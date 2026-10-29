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
    /** STRAFE, BOMB and HUNT are the air strikes ({@link AirStrikes}): flybys on a path of their own, at a crowd of zombies. */
    public enum Kind { JET, HELI, SIREN, EXPLOSION, STRAFE, BOMB, HUNT }

    /** Sound travels this many blocks a second. */
    public static final double SOUND_SPEED = 343;

    public static final SoundEvent SIREN = register("world.siren", 480f);
    public static final SoundEvent EXPLOSION = register("world.explosion", 192f);
    public static final SoundEvent EXPLOSION_FAR = register("world.explosion.far", 640f);
    /** Josiah's whole flyby recordings (tools/audio/build_flyby_sounds.py), one event each so everyone hears the same. */
    public static final SoundEvent[] FLYBY_JET = {register("world.flyby.jet.1", 800f), register("world.flyby.jet.2", 800f),
            register("world.flyby.jet.3", 800f), register("world.flyby.jet.4", 800f), register("world.flyby.jet.5", 800f)};
    public static final SoundEvent[] FLYBY_HELI = {register("world.flyby.heli.1", 800f)};

    /**
     * One event: where it starts, how it moves (blocks a second; still for a siren or explosion), the server tick
     * it starts on and how long it lasts. The aircraft's model and sound are only for a flyby: sound is which of
     * the whole flyby recordings everyone hears it as (world.flyby.jet.N / heli.N, from 1), or 0 for the far-off
     * loop bent by the Doppler shift in game until the aircraft is near and its own engines take over.
     * An air strike flies a path instead of the line: where it is and how it's flying every {@link #STEP}
     * seconds (x, y, z, yaw, nose, bank, over and over; the yaw carried on round rather than wrapped, the nose NaN
     * where it's left to the way it's going), smoothed between; a line's path is empty.
     */
    public record Event(Kind kind, double x, double y, double z, double vx, double vy, double vz, long start, int ticks,
            String model, int sound, float[] path) implements CustomPacketPayload {
        /** Seconds between a path's points. */
        public static final double STEP = 0.25;
        /** Numbers a path's point takes. */
        public static final int STRIDE = 6;
        public static final float[] LINE = new float[0];
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
            buf.writeVarInt(e.path.length);
            for (float f : e.path) {
                buf.writeFloat(f);
            }
        }, buf -> {
            Kind kind = Kind.values()[buf.readByte()];
            double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble(), vx = buf.readDouble(), vy = buf.readDouble(),
                    vz = buf.readDouble();
            long start = buf.readLong();
            int ticks = buf.readVarInt();
            String model = buf.readUtf();
            int sound = buf.readVarInt();
            float[] path = new float[buf.readVarInt()];
            for (int i = 0; i < path.length; i++) {
                path[i] = buf.readFloat();
            }
            return new Event(kind, x, y, z, vx, vy, vz, start, ticks, model, sound, path);
        });

        /** Where it is t seconds after it started. */
        public Vec3 at(double t) {
            if (path.length == 0) {
                return new Vec3(x + vx * t, y + vy * t, z + vz * t);
            }
            return new Vec3(spline(t, 0, false), spline(t, 1, false), spline(t, 2, false));
        }

        /** How it's moving t seconds in, blocks a second. */
        public Vec3 velocity(double t) {
            if (path.length == 0) {
                return new Vec3(vx, vy, vz);
            }
            return new Vec3(spline(t, 0, true), spline(t, 1, true), spline(t, 2, true));
        }

        /** The path's value c (x y z yaw nose bank) at t, through its points (Catmull-Rom), or how fast it's changing. */
        private double spline(double t, int c, boolean rate) {
            int n = path.length / STRIDE;
            double s = Math.clamp(t / STEP, 0, n - 1);
            int i = Math.min((int) s, n - 2);
            double f = s - i;
            double a = path[Math.max(i - 1, 0) * STRIDE + c], b = path[i * STRIDE + c], d = path[(i + 1) * STRIDE + c],
                    e = path[Math.min(i + 2, n - 1) * STRIDE + c];
            if (rate) {
                return 0.5 * ((d - a) + 2 * (2 * a - 5 * b + 4 * d - e) * f + 3 * (-a + 3 * b - 3 * d + e) * f * f) / STEP;
            }
            return 0.5 * (2 * b + (d - a) * f + (2 * a - 5 * b + 4 * d - e) * f * f + (-a + 3 * b - 3 * d + e) * f * f * f);
        }

        /** Which way its nose points t seconds in (Minecraft's yaw): along the line, or as its path says. */
        public float yaw(double t) {
            if (path.length == 0) {
                return (float) Math.toDegrees(Math.atan2(-vx, vz));
            }
            return (float) spline(t, 3, false);
        }

        public boolean flyby() {
            return kind != Kind.SIREN && kind != Kind.EXPLOSION;
        }

        /** A jet's flyby or strike (otherwise a helicopter's, for a flyby). */
        public boolean jet() {
            return kind == Kind.JET || kind == Kind.STRAFE || kind == Kind.BOMB;
        }

        public boolean afterburner() {
            return jet() && (kind == Kind.STRAFE || (start & 3) == 0);
        }

        /**
         * Puts a flyby's aircraft where it is t seconds in, flying: nose along the way it's going, banked into its
         * turns; the helicopter's nose dips as it speeds up and lifts as it hovers, turned the way its path says.
         */
        public void place(CarProp car, double t) {
            Vec3 at = at(t), v = velocity(t);
            if (path.length == 0) {
                car.flyby(at, v, afterburner());
                return;
            }
            float yaw = yaw(t);
            if (!Float.isNaN(path[4])) {
                // a jet's strike: flown, so how it's flying is in the path
                car.flyby(at, v, yaw, (float) spline(t, 4, false), (float) spline(t, 5, false), afterburner());
                return;
            }
            Vec3 a = velocity(t + 0.25).subtract(velocity(t - 0.25)).scale(2);
            double rad = Math.toRadians(yaw), h = Math.sqrt(v.x * v.x + v.z * v.z);
            Vec3 ahead = new Vec3(-Math.sin(rad), 0, Math.cos(rad)), right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
            float bank = (float) Math.clamp(Math.toDegrees(Math.atan2(a.dot(right), 9.81)), -70, 70);
            float nose;
            if (jet()) {
                nose = (float) Math.toDegrees(Math.atan2(v.y, Math.max(h, 1)));
            } else {
                // a helicopter tips its nose down to speed up, up to slow, and leans into a sidestep
                nose = (float) Math.clamp(-3 - 0.1 * v.dot(ahead) - 0.6 * a.dot(ahead), -16, 9);
                bank = Math.clamp(bank * 0.7f, -25, 25);
            }
            car.flyby(at, v, yaw, nose, bank, afterburner());
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
        /** An air strike's targets and guns, server side. */
        AirStrikes.Run strike;

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
    /** When the siren last went off; it waits at least SIREN_GAP ticks (an hour) before it can again. */
    private static long lastSiren = Long.MIN_VALUE / 2;
    private static final long SIREN_GAP = 60 * 60 * 20;

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Event.TYPE, Event.CODEC);
        AirStrikes.register();
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
            Kind kind = pick(random, cfg);
            if (kind == Kind.SIREN && now - lastSiren < SIREN_GAP) {
                kind = Kind.EXPLOSION;
            }
            if (kind == Kind.SIREN) {
                lastSiren = now;
            }
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
                if (r.strike != null) {
                    r.strike.tick(level, t);
                }
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

    private static Kind pick(RandomSource random, ZcConfig.World cfg) {
        int r = random.nextInt(100);
        // Josiah 09:27 2026-10-28: now and then the jets and the helicopter come in to strafe, bomb or hunt the dead
        if (cfg.airStrikes && random.nextDouble() < cfg.airStrikeShare) {
            return r < 35 ? Kind.STRAFE : r < 65 ? Kind.BOMB : Kind.HUNT;
        }
        // Josiah: the siren is rare (and an hour apart at least, see tick)
        return r < 40 ? Kind.JET : r < 70 ? Kind.HELI : r < 94 ? Kind.EXPLOSION : Kind.SIREN;
    }

    /**
     * Starts an event of this kind somewhere around this player and tells everyone. A flyby crosses the map on a
     * line that passes within a couple of hundred blocks of them; a siren or explosion goes off a few hundred
     * blocks away.
     */
    public static Event start(ServerLevel level, Kind kind, ServerPlayer near) {
        return start(level, kind, near, false);
    }

    /**
     * The same; an air strike with no crowd of zombies near enough to hit is a plain flyby instead, unless forced
     * (the test command), when it makes its run at the ground ahead of the player.
     */
    public static Event start(ServerLevel level, Kind kind, ServerPlayer near, boolean force) {
        RandomSource random = level.getRandom();
        if (kind == Kind.STRAFE || kind == Kind.BOMB || kind == Kind.HUNT) {
            List<AirStrikes.Run> runs = AirStrikes.plan(level, kind, near, force);
            if (runs.isEmpty()) {
                kind = kind == Kind.HUNT ? Kind.HELI : Kind.JET;
            } else {
                for (AirStrikes.Run run : runs) {
                    Running r = new Running(run.event);
                    r.strike = run;
                    RUNNING.add(r);
                    for (ServerPlayer player : level.players()) {
                        ServerPlayNetworking.send(player, run.event);
                    }
                }
                Event e = runs.get(0).event;
                ZomboidCraft.LOGGER.info("World event {} at {} {} {}", kind.name().toLowerCase(Locale.ROOT), (int) runs.get(0).target.x,
                        (int) runs.get(0).target.y, (int) runs.get(0).target.z);
                return e;
            }
        }
        Vec3 p = near.position();
        double ground = surface(level, p.x, p.z, p.y);
        Event e;
        List<Event> wingmen = new ArrayList<>();
        if (kind == Kind.JET || kind == Kind.HELI) {
            boolean jet = kind == Kind.JET;
            double speed = jet ? 75 + random.nextDouble() * 30 : 32 + random.nextDouble() * 12;   // jets 270-380 km/h, helicopters 115-160 (Josiah: slower)
            double reach = jet ? 1600 : 900;     // half the path: start and end well out of earshot
            double heading = random.nextDouble() * Math.PI * 2;
            double side = (random.nextDouble() * 2 - 1) * (jet ? 90 : 120);   // Josiah 11:13: jets closer and lower, to show through the fog
            double dx = Math.cos(heading), dz = Math.sin(heading);
            // closest point to the player, then back along the line to the start
            double cx = p.x - dz * side, cz = p.z + dx * side;
            double y = ground + (jet ? 35 + random.nextDouble() * 25 : 35 + random.nextDouble() * 30);
            // ...but always over the tallest thing along the line near the player (towers downtown)
            for (int k = -240; k <= 240; k += 12) {
                y = Math.max(y, surface(level, cx + dx * k, cz + dz * k, 0) + 18);
            }
            y = Math.min(level.getMaxY() - 8, y);
            String model = jet ? JETS[random.nextInt(JETS.length)] : HELI;
            e = new Event(kind, cx - dx * reach, y, cz - dz * reach, dx * speed, 0, dz * speed, level.getGameTime(),
                    (int) (2 * reach / speed * 20), model, sound(kind, random), Event.LINE);
            // often a few together, same type, each 3 to 5 seconds behind the one before (Josiah: far enough
            // back that every pass is its own Doppler sweep), a little out to one side and higher
            // Josiah 11:37: 2 or 3 jets in a row most of the time, and a couple of helicopters together often too
            int pack = random.nextInt(10) < (jet ? 7 : 6) ? (jet ? 1 + random.nextInt(2) : 1) : 0;
            double back = 0;
            for (int i = 1; i <= pack; i++) {
                back += speed * (3 + random.nextDouble() * 2);
                double out = (i % 2 == 1 ? 1 : -1) * 22 * ((i + 1) / 2);
                wingmen.add(new Event(kind, e.x() - dx * back - dz * out, y + 4 * i, e.z() - dz * back + dx * out, e.vx(), 0, e.vz(),
                        e.start(), e.ticks() + (int) (back / speed * 20) + 1, model, sound(kind, random), Event.LINE));
            }
        } else {
            double angle = random.nextDouble() * Math.PI * 2;
            double d = kind == Kind.SIREN ? 120 + random.nextDouble() * 220 : 100 + random.nextDouble() * 350;
            double x = p.x + Math.cos(angle) * d, z = p.z + Math.sin(angle) * d;
            double y = surface(level, x, z, ground) + (kind == Kind.SIREN ? 14 : 1);
            // a siren winds up and down for 40 seconds; an explosion's smoke hangs for a minute and a half
            e = new Event(kind, x, y, z, 0, 0, 0, level.getGameTime(), kind == Kind.SIREN ? 44 * 20 : 90 * 20, "", 0, Event.LINE);
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

    /**
     * A flyby's sound: one of the whole recordings, and nothing else (Josiah: one sound for each aircraft, so the
     * far-off loop and the aircraft's own engines stay quiet for flybys).
     */
    private static int sound(Kind kind, RandomSource random) {
        if (kind == Kind.JET) {
            // Josiah: the two F-16 passes most often, then the F/A-18, F-22 and MiG-15
            return random.nextInt(10) < 7 ? 1 + random.nextInt(2) : 3 + random.nextInt(FLYBY_JET.length - 2);
        }
        return 1 + random.nextInt(FLYBY_HELI.length);
    }

    static String jet(RandomSource random) {
        return JETS[random.nextInt(JETS.length)];
    }

    /** Ground height here if the chunk is loaded, otherwise the fallback. */
    static double surface(ServerLevel level, double x, double z, double fallback) {
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
            r.event.place(car, t);
            level.addFreshEntity(car);
            r.aircraft = car;
            return;
        }
        r.event.place(r.aircraft, t);
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

    static double sqr(double v) {
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

    static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    private WorldEvents() {
    }
}
