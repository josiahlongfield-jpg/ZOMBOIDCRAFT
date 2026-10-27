package com.zomboidcraft.world.events;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Big things that happen out in the world and that everyone hears together: an air-raid siren wailing over a
 * town, a far-off explosion, a jet or a helicopter crossing the map. The server picks each one, where it is and
 * when it starts, and tells every player; each player's game then plays it from where that player stands, so
 * someone close hears it first and loudest, someone a few hundred blocks off hears it later and fainter (sound
 * travels at 343 blocks a second), and a jet passing between two friends reaches each as it goes by them.
 * The aircraft are the real models: wherever a flyby passes near a player the jet or helicopter itself flies
 * over, and its engines are heard up close as any aircraft's are.
 */
public final class WorldEvents {
    public enum Kind { JET, HELI, SIREN, EXPLOSION }

    /** Sound travels this many blocks a second. */
    public static final double SOUND_SPEED = 343;

    public static final SoundEvent SIREN = register("world.siren", 480f);
    public static final SoundEvent EXPLOSION = register("world.explosion", 192f);
    public static final SoundEvent EXPLOSION_FAR = register("world.explosion.far", 640f);
    /** Josiah's whole flyby recordings (tools/audio/build_flyby_sounds.py), one event each so everyone hears the same. */
    public static final SoundEvent[] FLYBY_JET = {register("world.flyby.jet.1", 800f), register("world.flyby.jet.2", 800f),
            register("world.flyby.jet.3", 800f)};
    public static final SoundEvent[] FLYBY_HELI = {register("world.flyby.heli.1", 800f)};

    /**
     * One event: where it starts, how it moves (blocks a second; still for a siren or explosion), the server tick
     * it starts on and how long it lasts. The aircraft's model and sound are only for a flyby: sound is which of
     * the whole flyby recordings everyone hears it as (world.flyby.jet.N / heli.N, from 1), or 0 for the far-off
     * loop bent by the Doppler shift in game until the aircraft is near and its own engines take over.
     */
    public record Event(Kind kind, double x, double y, double z, double vx, double vy, double vz, long start, int ticks,
            String model, int sound) implements CustomPacketPayload {
        public static final Type<Event> TYPE = new Type<>(ZomboidCraft.id("world_event"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Event> CODEC = StreamCodec.of((buf, e) -> {
            buf.writeByte(e.kind.ordinal());
            buf.writeDouble(e.x);
            buf.writeDouble(e.y);
            buf.writeDouble(e.z);
            buf.writeDouble(e.vx);
            buf.writeDouble(e.vy);
            buf.writeDouble(e.vz);
            buf.writeLong(e.start);
            buf.writeVarInt(e.ticks);
            buf.writeUtf(e.model);
            buf.writeVarInt(e.sound);
        }, buf -> new Event(Kind.values()[buf.readByte()], buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readLong(), buf.readVarInt(), buf.readUtf(), buf.readVarInt()));

        /** Where it is t seconds after it started. */
        public Vec3 at(double t) {
            return new Vec3(x + vx * t, y + vy * t, z + vz * t);
        }

        public Vec3 velocity() {
            return new Vec3(vx, vy, vz);
        }

        public boolean flyby() {
            return kind == Kind.JET || kind == Kind.HELI;
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** An event under way on the server, and the aircraft flying it while one is near a player. */
    private static final class Running {
        final Event event;
        CarProp aircraft;

        Running(Event event) {
            this.event = event;
        }
    }

    private static final List<Running> RUNNING = new ArrayList<>();
    private static final String[] JETS = {"jet_corvid", "jet_gyrfalcon"};
    private static final String HELI = "heli_hornbill";
    /** A flyby's aircraft is only there while a player is within this many blocks of it (about the tracking range). */
    private static final double SHOW = 192;
    private static long next = -1;

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Event.TYPE, Event.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(WorldEvents::tick);
        // someone joining mid-event still hears the rest of it
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            for (Running r : RUNNING) {
                ServerPlayNetworking.send(handler.getPlayer(), r.event);
            }
        }));
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        ZcConfig.World cfg = ZcConfig.get().world;
        RandomSource random = level.getRandom();
        if (next < 0 || next > now + gap(cfg, random) * 2L) {
            next = now + gap(cfg, random);
        }
        if (cfg.worldEvents && now >= next && !level.players().isEmpty()) {
            next = now + gap(cfg, random);
            Kind kind = pick(random);
            start(level, kind, level.players().get(random.nextInt(level.players().size())));
        }
        for (var it = RUNNING.iterator(); it.hasNext(); ) {
            Running r = it.next();
            double t = (now - r.event.start) / 20.0;
            if (t > r.event.ticks / 20.0) {
                if (r.aircraft != null) {
                    r.aircraft.discard();
                }
                it.remove();
                continue;
            }
            if (r.event.flyby()) {
                fly(level, r, t);
            } else if (r.event.kind == Kind.EXPLOSION) {
                smoke(level, r.event, now - r.event.start);
            }
        }
    }

    /** Ticks until the next event: worldEventMinutes on average, anywhere from half to one and a half times it. */
    private static int gap(ZcConfig.World cfg, RandomSource random) {
        double minutes = Math.max(0.5, cfg.worldEventMinutes) * (0.5 + random.nextDouble());
        return (int) (minutes * 60 * 20);
    }

    private static Kind pick(RandomSource random) {
        int r = random.nextInt(100);
        return r < 35 ? Kind.JET : r < 60 ? Kind.HELI : r < 80 ? Kind.EXPLOSION : Kind.SIREN;
    }

    /**
     * Starts an event of this kind somewhere around this player and tells everyone. A flyby crosses the map on a
     * line that passes within a couple of hundred blocks of them; a siren or explosion goes off a few hundred
     * blocks away.
     */
    public static Event start(ServerLevel level, Kind kind, ServerPlayer near) {
        RandomSource random = level.getRandom();
        Vec3 p = near.position();
        double ground = surface(level, p.x, p.z, p.y);
        Event e;
        List<Event> wingmen = new ArrayList<>();
        if (kind == Kind.JET || kind == Kind.HELI) {
            boolean jet = kind == Kind.JET;
            double speed = jet ? 110 + random.nextDouble() * 50 : 50 + random.nextDouble() * 20;
            double reach = jet ? 1600 : 900;     // half the path: start and end well out of earshot
            double heading = random.nextDouble() * Math.PI * 2;
            double side = (random.nextDouble() * 2 - 1) * (jet ? 180 : 120);
            double dx = Math.cos(heading), dz = Math.sin(heading);
            // closest point to the player, then back along the line to the start
            double cx = p.x - dz * side, cz = p.z + dx * side;
            double y = Math.min(level.getMaxY() - 8, ground + (jet ? 70 + random.nextDouble() * 60 : 35 + random.nextDouble() * 30));
            String model = jet ? JETS[random.nextInt(JETS.length)] : HELI;
            e = new Event(kind, cx - dx * reach, y, cz - dz * reach, dx * speed, 0, dz * speed, level.getGameTime(),
                    (int) (2 * reach / speed * 20), model, sound(kind, random));
            // sometimes a few jets together: each further back and out to one side, a little higher, same type
            int pack = jet && random.nextInt(3) == 0 ? 1 + random.nextInt(3) : 0;
            for (int i = 1; i <= pack; i++) {
                double back = 28 * i, out = (i % 2 == 1 ? 1 : -1) * 22 * ((i + 1) / 2);
                wingmen.add(new Event(kind, e.x() - dx * back - dz * out, y + 4 * i, e.z() - dz * back + dx * out, e.vx(), 0, e.vz(),
                        e.start(), e.ticks() + (int) (back / speed * 20) + 1, model, sound(kind, random)));
            }
        } else {
            double angle = random.nextDouble() * Math.PI * 2;
            double d = kind == Kind.SIREN ? 120 + random.nextDouble() * 220 : 100 + random.nextDouble() * 350;
            double x = p.x + Math.cos(angle) * d, z = p.z + Math.sin(angle) * d;
            double y = surface(level, x, z, ground) + (kind == Kind.SIREN ? 14 : 1);
            // a siren winds up and down for 40 seconds; an explosion's smoke hangs for a minute and a half
            e = new Event(kind, x, y, z, 0, 0, 0, level.getGameTime(), kind == Kind.SIREN ? 44 * 20 : 90 * 20, "", 0);
            if (kind == Kind.EXPLOSION && level.isLoaded(BlockPos.containing(x, y, z))) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, x, y + 2, z, 2, 2, 1, 2, 0);
                level.sendParticles(ParticleTypes.LAVA, true, true, x, y + 1, z, 30, 2, 1, 2, 0);
            }
        }
        wingmen.add(0, e);
        for (Event each : wingmen) {
            RUNNING.add(new Running(each));
            for (ServerPlayer player : level.players()) {
                ServerPlayNetworking.send(player, each);
            }
        }
        ZomboidCraft.LOGGER.info("World event {} at {} {} {}", kind.name().toLowerCase(Locale.ROOT), (int) e.x(), (int) e.y(), (int) e.z());
        return e;
    }

    /** A flyby's sound: one of the whole recordings most of the time, otherwise the far-off loop. */
    private static int sound(Kind kind, RandomSource random) {
        int takes = kind == Kind.JET ? FLYBY_JET.length : FLYBY_HELI.length;
        int pick = random.nextInt(takes + 1);
        return pick == takes ? 0 : pick + 1;
    }

    /** Ground height here if the chunk is loaded, otherwise the fallback. */
    private static double surface(ServerLevel level, double x, double z, double fallback) {
        BlockPos pos = BlockPos.containing(x, fallback, z);
        if (!level.isLoaded(pos)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    /**
     * Keeps a flyby's aircraft on its line: there, flying, wherever a player is close enough to see it and the
     * world is loaded under it, and gone everywhere else (the far-off sound is each player's own game's job).
     */
    private static void fly(ServerLevel level, Running r, double t) {
        Vec3 at = r.event.at(t);
        boolean seen = level.isLoaded(BlockPos.containing(at)) && level.players().stream()
                .anyMatch(pl -> sqr(pl.getX() - at.x) + sqr(pl.getZ() - at.z) < SHOW * SHOW);
        if (!seen) {
            if (r.aircraft != null) {
                r.aircraft.discard();
                r.aircraft = null;
            }
            return;
        }
        if (r.aircraft == null || r.aircraft.isRemoved()) {
            CarProp car = Cars.CAR.create(level, EntitySpawnReason.EVENT);
            if (car == null) {
                return;
            }
            car.setLook(r.event.model(), "usable");
            car.flyby(at, r.event.velocity(), r.event.kind() == Kind.JET && (r.event.start() & 3) == 0);
            level.addFreshEntity(car);
            r.aircraft = car;
            return;
        }
        r.aircraft.flyby(at, r.event.velocity(), r.event.kind() == Kind.JET && (r.event.start() & 3) == 0);
    }

    /** Smoke climbing from where it went off, thinning out over its minute and a half. */
    private static void smoke(ServerLevel level, Event e, long age) {
        if (age % 5 != 0 || !level.isLoaded(BlockPos.containing(e.x(), e.y(), e.z()))) {
            return;
        }
        int count = age < 300 ? 4 : age < 1000 ? 2 : 1;
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, e.x(), e.y() + 1, e.z(), count, 1.5, 0.5, 1.5, 0.02);
        if (age < 400) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, e.x(), e.y() + 1, e.z(), 3, 1.5, 0.8, 1.5, 0.05);
        }
    }

    private static double sqr(double v) {
        return v * v;
    }

    /** /zc event: what kinds there are. */
    public static List<String> kinds() {
        List<String> out = new ArrayList<>();
        for (Kind k : Kind.values()) {
            out.add(k.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    private WorldEvents() {
    }
}
