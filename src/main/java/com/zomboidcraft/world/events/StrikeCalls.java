package com.zomboidcraft.world.events;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.gear.RadioItem;
import com.zomboidcraft.npc.NpcEntity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Air strikes a radio operator calls in (Josiah 2026-11-06, W17 and calls A1-A8 in "Calling in air strikes"): the
 * operator marks the target with a laser, a coloured smoke signal or a grid square on the map, picks the strike
 * (a jet's gun run, a jet's stick of bombs, or the Apache), and the aircraft fly the same runs as the strikes nobody
 * called (AirStrikes), aimed at the mark instead of the nearest crowd.
 *
 * <p>How close it lands goes with the effort the mark took: a laser held on the spot about a block (read the moment
 * the guns open or the bombs go, so the operator can walk it), smoke about three blocks, a grid square about ten. A
 * laser that drops before release leaves the last spot it held at smoke accuracy; a mark that has moved more than
 * {@link #DRIFT} blocks from where the strike was called calls it off rather than guessing.
 *
 * <p>Who calls: a player carrying a field radio (A2). The menu and the voice on the command net are AUDIO's: it
 * calls {@link #call}, which answers at once (checked in, or a reason it's refused), then hears every moment of the
 * strike through {@link #listen}. Danger close (friendlies within 25 blocks of a gun run's line, 60 of a bomb stick,
 * 15 of the Apache's mark) is refused until the operator confirms it (A4). The operator can wave it off until
 * {@link #ABORT_BY} seconds before release, which spends only half the cooldown.
 *
 * <p>Cooldown (A3): one strike an in-game day for each base, a third less with a comms post at the base; a bonus
 * strike from a hunt task skips the cooldown once (two at most). The hunt board reads {@link #status}. A base is
 * whatever {@link #BASE} says a player belongs to and {@link #COMMS_POST} says has a comms post: until base
 * building fills those in, everyone is one base.
 */
public final class StrikeCalls {
    /** What the operator can call. */
    public enum StrikeType {
        GUN_RUN(WorldEvents.Kind.STRAFE, 25),
        BOMBS(WorldEvents.Kind.BOMB, 60),
        APACHE(WorldEvents.Kind.HUNT, 15);

        final WorldEvents.Kind kind;
        /** How close a friendly can be before it's danger close (blocks). */
        public final double dangerClose;

        StrikeType(WorldEvents.Kind kind, double dangerClose) {
            this.kind = kind;
            this.dangerClose = dangerClose;
        }
    }

    /** Why a call is turned down (or, for MARK_LOST, called off after it was accepted). */
    public enum Refusal { NO_RADIO, COOLDOWN, MARK_LOST, NO_CLEAR_RUN, DANGER_CLOSE_UNCONFIRMED }

    /** The moments of a strike, as they happen: for the voice on the command net. */
    public enum Moment { CHECKED_IN, IN_HOT, RELEASED, ABORTED, RESULT }

    /** Where to hit. */
    public sealed interface Mark permits Fixed, Laser {
    }

    /** A mark that stays put: a smoke cloud ("smoke:green") or a grid square ("grid"), and how far off it can land. */
    public record Fixed(Vec3 at, double spread, String how) implements Mark {
    }

    /** A laser the operator holds on the target: read again at release. */
    public record Laser(UUID operator) implements Mark {
    }

    /** The answer to a call: accepted with its id and the seconds to impact (in ticks), or refused and why. */
    public record CallResult(boolean accepted, Refusal reason, int id, int etaTicks, List<String> dangerClose) {
        static CallResult no(Refusal why) {
            return new CallResult(false, why, -1, 0, List.of());
        }
    }

    /**
     * One moment of a strike. mark is where it's aimed; kills and hurt (the friendlies it hit) are filled in on
     * RESULT; why says what called it off on an ABORTED that the operator didn't ask for.
     */
    public record Update(int id, UUID caller, String base, StrikeType type, Moment moment, Vec3 mark, int kills,
                         List<String> hurt, Refusal why) {
    }

    /** The last strike a base had, for the hunt board. day counts from 1. */
    public record Last(StrikeType type, BlockPos where, long day, int kills, int friendlies) {
    }

    /** A base's air support now: ready or cooling (ticks left of cycleTicks), bonus strikes, and the last strike. */
    public record Status(boolean ready, int ticksLeft, int cycleTicks, int bonus, Last lastResult) {
    }

    /** A smoke cloud coloured to mark a target, sent to everyone near so their game draws it. */
    public record SmokeCloud(float x, float y, float z, int colour, int ticks) implements CustomPacketPayload {
        public static final Type<SmokeCloud> TYPE = new Type<>(ZomboidCraft.id("strike_smoke"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SmokeCloud> CODEC = StreamCodec.of((buf, c) -> {
            buf.writeFloat(c.x);
            buf.writeFloat(c.y);
            buf.writeFloat(c.z);
            buf.writeInt(c.colour);
            buf.writeVarInt(c.ticks);
        }, buf -> new SmokeCloud(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A puff of a coloured smoke signal (its colour travels in the velocity slots): drawn by the client. */
    public static final net.minecraft.core.particles.SimpleParticleType SIGNAL_SMOKE = net.minecraft.core.Registry.register(
            BuiltInRegistries.PARTICLE_TYPE, ZomboidCraft.id("signal_smoke"), net.fabricmc.fabric.api.particle.v1.FabricParticleTypes.simple(true));

    /** A mark that's moved further than this from where the strike was called (blocks) calls it off. */
    public static final double DRIFT = 30;
    /** How far each kind of mark can be off: laser, laser lost (the last spot), smoke, grid square (blocks). */
    public static final double LASER = 1, LOST = 3, SMOKE = 3, GRID = 10;
    /** The operator can wave a strike off until this many seconds before release. */
    public static final double ABORT_BY = 10;
    /** The IN_HOT call comes this long before release (seconds). */
    private static final double IN_HOT = 6;
    /** The longest a strike can be held for its time on target (ticks): two minutes. */
    public static final int MAX_DELAY = 2 * 60 * 20;
    private static final int MAX_BONUS = 2;
    /** How far a laser reaches (blocks), and how long a dropped laser's last spot is still good at call time (ticks). */
    private static final double LASER_REACH = 800;
    private static final long LASER_GRACE = 40;

    /** The base a player belongs to: base building sets this; until then everyone is one base. */
    public static Function<ServerPlayer, String> BASE = p -> "main";
    /** Whether a base has a comms post: base building sets this. */
    public static BiPredicate<ServerLevel, String> COMMS_POST = (level, base) -> false;
    /** The item ids that are lasers: held with the use key down, they lase. */
    private static final java.util.Set<String> LASERS = java.util.Set.of("laser_designator", "laser_rangefinder");

    private static final List<Consumer<Update>> LISTENERS = new CopyOnWriteArrayList<>();

    private StrikeCalls() {
    }

    // ------------------------------------------------------------------ the calls under way

    /** A strike called in and not yet over. */
    public static final class Call {
        final int id;
        final UUID caller;
        final String base;
        final StrikeType type;
        final Mark mark;
        /** Where it was aimed when called, and where it's aiming now. */
        final Vec3 called;
        Vec3 aim;
        final List<AirStrikes.Run> runs = new ArrayList<>();
        final long start;
        final boolean usedBonus;
        final long spentFrom;
        boolean hot, released, over, aborted;

        Call(int id, UUID caller, String base, StrikeType type, Mark mark, Vec3 called, long start, boolean usedBonus, long spentFrom) {
            this.id = id;
            this.caller = caller;
            this.base = base;
            this.type = type;
            this.mark = mark;
            this.called = called;
            this.aim = called;
            this.start = start;
            this.usedBonus = usedBonus;
            this.spentFrom = spentFrom;
        }

        /** The seconds from the start to the first run's release, and to the last run being done. */
        double release() {
            double r = Double.MAX_VALUE;
            for (AirStrikes.Run run : runs) {
                r = Math.min(r, run.release());
            }
            return r;
        }

        double done() {
            double d = 0;
            for (AirStrikes.Run run : runs) {
                d = Math.max(d, run.done());
            }
            return d;
        }

        /**
         * A run letting go: where it should hit now. A laser is read again (the last spot it held, if it's been
         * let go, a little less precisely); a fixed mark is where it was. Null calls the whole strike off (the mark
         * is lost or has moved too far).
         */
        Vec3 release(AirStrikes.Run run) {
            if (aborted) {
                return null;
            }
            if (mark instanceof Laser laser && server != null) {
                ServerPlayer op = server.getPlayerList().getPlayer(laser.operator);
                Vec3 spot = op == null ? null : spot(op);
                double spread = LASER;
                if (spot == null) {
                    Seen last = SEEN.get(laser.operator);
                    spot = last == null ? null : last.at;
                    spread = LOST;
                }
                if (spot == null || horizontal(spot, called) > DRIFT) {
                    abort(Refusal.MARK_LOST);
                    return null;
                }
                aim = scatter(spot, spread, run.event.kind() == WorldEvents.Kind.HUNT ? 0 : 1);
            }
            if (!released) {
                released = true;
                tell(Moment.RELEASED, 0, List.of(), null);
            }
            return aim;
        }

        /** A laser held on the target now, or null: the Apache moves its fire round with it. */
        Vec3 liveSpot() {
            if (!(mark instanceof Laser laser) || server == null) {
                return null;
            }
            ServerPlayer op = server.getPlayerList().getPlayer(laser.operator);
            return op == null ? null : spot(op);
        }

        void abort(Refusal why) {
            if (aborted || over) {
                return;
            }
            aborted = true;
            for (AirStrikes.Run run : runs) {
                run.aborted = true;
            }
            // only half the cooldown is spent (a bonus strike comes back)
            Base b = base(this.base);
            if (usedBonus) {
                b.bonus = Math.min(MAX_BONUS, b.bonus + 1);
            } else if (server != null) {
                b.readyAt = spentFrom + cycle(server.overworld(), this.base) / 2;
            }
            save();
            tell(Moment.ABORTED, 0, List.of(), why);
        }

        void tell(Moment moment, int kills, List<String> hurt, Refusal why) {
            Update u = new Update(id, caller, base, type, moment, aim, kills, hurt, why);
            for (Consumer<Update> l : LISTENERS) {
                try {
                    l.accept(u);
                } catch (RuntimeException e) {
                    ZomboidCraft.LOGGER.error("Strike call listener failed", e);
                }
            }
        }
    }

    private static final List<Call> LIVE = new ArrayList<>();
    private static int nextId = 1;
    private static MinecraftServer server;

    // ------------------------------------------------------------------ what AUDIO and UI call

    /**
     * A radio operator calls a strike: of this type, on this mark, coming in along direction (null: down the
     * street through the mark), delayTicks from now (at most two minutes). Refused if they have no field radio,
     * the base's strike is cooling (and it has no bonus), the mark can't be found, every way in is walled in, or
     * friendlies are danger close and not confirmed; otherwise the aircraft are on their way.
     */
    public static CallResult call(ServerLevel level, ServerPlayer caller, StrikeType type, Mark mark, Vec3 direction, int delayTicks,
            boolean confirmedDangerClose) {
        if (!hasRadio(caller)) {
            return CallResult.no(Refusal.NO_RADIO);
        }
        String baseId = BASE.apply(caller);
        Base b = base(baseId);
        long now = level.getGameTime();
        boolean cooling = now < b.readyAt;
        if (cooling && b.bonus <= 0 || live(baseId)) {
            return CallResult.no(Refusal.COOLDOWN);
        }
        Vec3 at = where(level, mark);
        if (at == null) {
            return CallResult.no(Refusal.MARK_LOST);
        }
        if (mark instanceof Fixed fixed) {
            at = scatter(at, fixed.spread, 1);
            at = new Vec3(at.x, WorldEvents.surface(level, at.x, at.z, at.y), at.z);
        }
        Vec3 dir = AirStrikes.heading(level, type.kind, at, direction, false);
        if (dir == null) {
            return CallResult.no(Refusal.NO_CLEAR_RUN);
        }
        List<String> close = dangerClose(level, type, at, dir);
        if (!close.isEmpty() && !confirmedDangerClose) {
            return new CallResult(false, Refusal.DANGER_CLOSE_UNCONFIRMED, -1, 0, close);
        }
        int delay = Math.clamp(delayTicks, 0, MAX_DELAY);
        Call c = new Call(nextId++, caller.getUUID(), baseId, type, mark, at, now, cooling, now);
        c.runs.addAll(AirStrikes.runs(level, type.kind, at, dir, delay / 20.0, c));
        if (c.runs.isEmpty()) {
            return CallResult.no(Refusal.NO_CLEAR_RUN);
        }
        if (cooling) {
            b.bonus--;
        } else {
            b.readyAt = now + cycle(level, baseId);
        }
        save();
        LIVE.add(c);
        WorldEvents.launch(level, c.runs);
        ZomboidCraft.LOGGER.info("Strike call {} by {}: {} at {} {} {}, in along {} {}", c.id, caller.getName().getString(),
                type.name().toLowerCase(Locale.ROOT), (int) at.x, (int) at.y, (int) at.z, String.format(Locale.ROOT, "%.2f", dir.x),
                String.format(Locale.ROOT, "%.2f", dir.z));
        c.tell(Moment.CHECKED_IN, 0, List.of(), null);
        return new CallResult(true, null, c.id, (int) Math.round(c.release() * 20), close);
    }

    /** The operator waves their strike off: true if it was still early enough (ABORT_BY seconds before release). */
    public static boolean abort(ServerPlayer caller) {
        for (Call c : LIVE) {
            if (c.caller.equals(caller.getUUID()) && !c.aborted && !c.over) {
                double t = (caller.level().getGameTime() - c.start) / 20.0;
                if (t > c.release() - ABORT_BY) {
                    return false;
                }
                c.abort(null);
                return true;
            }
        }
        return false;
    }

    /** Hears every moment of every called strike. */
    public static void listen(Consumer<Update> listener) {
        LISTENERS.add(listener);
    }

    /** A base's air support now (the hunt board). */
    public static Status status(ServerLevel level, String baseId) {
        Base b = base(baseId);
        int left = (int) Math.max(0, b.readyAt - level.getGameTime());
        return new Status(left == 0, left, cycle(level, baseId), b.bonus, b.last);
    }

    /** A hunt task paid out a strike: it skips the cooldown once (two saved at most). */
    public static void addBonus(String baseId) {
        Base b = base(baseId);
        b.bonus = Math.min(MAX_BONUS, b.bonus + 1);
        save();
    }

    /** Whether any called strike is under way (the strikes nobody called stay away meanwhile). */
    public static boolean live() {
        return !LIVE.isEmpty();
    }

    private static boolean live(String baseId) {
        for (Call c : LIVE) {
            if (c.base.equals(baseId)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ marks

    /** The operator's laser (held on the target with the use key down). */
    public static Mark laser(ServerPlayer operator) {
        return new Laser(operator.getUUID());
    }

    /** A grid square on the map: its middle, on the ground. */
    public static Mark grid(ServerLevel level, double x, double z) {
        return new Fixed(new Vec3(x, WorldEvents.surface(level, x, z, level.getSeaLevel()), z), GRID, "grid");
    }

    /**
     * A coloured smoke cloud burning within 200 blocks of the operator ("from my green smoke": offset added, e.g.
     * 20 blocks north is (0, 0, -20)); colour null takes the nearest of any colour. Null if there's none.
     */
    public static Mark smoke(ServerLevel level, ServerPlayer operator, String colour, Vec3 offset) {
        Smoke best = null;
        double bestD = 200 * 200;
        long now = level.getGameTime();
        for (Smoke s : SMOKES) {
            double d = s.at.distanceToSqr(operator.position());
            if (s.level == level && now <= s.until && d < bestD && (colour == null || colour.equalsIgnoreCase(s.colour))) {
                best = s;
                bestD = d;
            }
        }
        if (best == null) {
            return null;
        }
        Vec3 at = best.at.add(offset == null ? Vec3.ZERO : offset);
        return new Fixed(at, SMOKE, "smoke:" + best.colour);
    }

    /**
     * A coloured smoke signal has gone off here and burns for ticks (LOOT's smoke canister calls this when it
     * pops): everyone near sees the cloud in its colour, and it's a mark a strike can be called on.
     */
    public static void smoke(ServerLevel level, Vec3 at, String colour, int ticks) {
        long now = level.getGameTime();
        SMOKES.removeIf(s -> now > s.until);
        SMOKES.add(new Smoke(level, at, colour.toLowerCase(Locale.ROOT), now + ticks));
        SmokeCloud packet = new SmokeCloud((float) at.x, (float) at.y, (float) at.z, rgb(colour), ticks);
        for (ServerPlayer p : PlayerLookup.around(level, at, 512)) {
            ServerPlayNetworking.send(p, packet);
        }
    }

    /** The colour each smoke burns, as the cloud is drawn: an M18's red, green, yellow or violet. */
    public static int rgb(String colour) {
        return switch (colour.toLowerCase(Locale.ROOT)) {
            case "red" -> 0xC8382C;
            case "green" -> 0x5C9A4C;
            case "yellow" -> 0xE6C84A;
            default -> 0x8C62B4;
        };
    }

    private record Smoke(ServerLevel level, Vec3 at, String colour, long until) {
    }

    private static final List<Smoke> SMOKES = new ArrayList<>();

    /** Whether this is a laser (the designator, or the rangefinder) . */
    public static boolean isLaser(ItemStack stack) {
        return !stack.isEmpty() && LASERS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
    }

    /** Whether someone is lasing: holding a laser with the use key down. */
    public static boolean lasing(LivingEntity e) {
        return e.isUsingItem() && isLaser(e.getUseItem());
    }

    /** Where someone's laser is on now (the first thing it hits), or null if they aren't lasing or it hits nothing. */
    public static Vec3 spot(Player p) {
        if (!lasing(p)) {
            return null;
        }
        Vec3 eye = p.getEyePosition(), to = eye.add(p.getViewVector(1f).scale(LASER_REACH));
        BlockHitResult hit = p.level().clip(new ClipContext(eye, to, ClipContext.Block.VISUAL, ClipContext.Fluid.ANY, p));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    /** Where each operator's laser was last on, and when. */
    private record Seen(Vec3 at, long when) {
    }

    private static final Map<UUID, Seen> SEEN = new HashMap<>();

    private static Vec3 where(ServerLevel level, Mark mark) {
        return switch (mark) {
            case Fixed f -> f.at;
            case Laser l -> {
                ServerPlayer op = level.getServer().getPlayerList().getPlayer(l.operator);
                Vec3 spot = op == null ? null : spot(op);
                if (spot == null) {
                    Seen last = SEEN.get(l.operator);
                    spot = last != null && level.getGameTime() - last.when <= LASER_GRACE ? last.at : null;
                }
                yield spot;
            }
        };
    }

    /** A point up to spread blocks off this one, anywhere in the circle round it (flat, or squashed up and down). */
    private static Vec3 scatter(Vec3 at, double spread, double flat) {
        RandomSource r = RandomSource.create();
        double a = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * spread;
        return at.add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }

    // ------------------------------------------------------------------ safety

    /**
     * The friendlies (players and companions) danger close to a strike on at coming in along dir: within the
     * type's distance of a gun run's whole line, a bomb stick's whole length, or the Apache's mark.
     */
    static List<String> dangerClose(ServerLevel level, StrikeType type, Vec3 at, Vec3 dir) {
        double half = switch (type) {
            case GUN_RUN -> 24;      // StrikeFlight.LINE / 2
            case BOMBS -> 20;        // up to six bombs seven blocks apart, a wingman's beside
            case APACHE -> 0;
        };
        Vec3 a = at.add(dir.scale(-half)), b = at.add(dir.scale(half));
        double reach = type.dangerClose + half;
        List<String> close = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(reach, 64, reach),
                e -> e.isAlive() && (e instanceof ServerPlayer p && !p.isSpectator() || e instanceof NpcEntity))) {
            if (toSegment(e.position(), a, b) < type.dangerClose) {
                close.add(e.getName().getString());
            }
        }
        return close;
    }

    private static double toSegment(Vec3 p, Vec3 a, Vec3 b) {
        double dx = b.x - a.x, dz = b.z - a.z, len = dx * dx + dz * dz;
        double t = len < 1e-6 ? 0 : Math.clamp(((p.x - a.x) * dx + (p.z - a.z) * dz) / len, 0, 1);
        return Math.hypot(p.x - (a.x + dx * t), p.z - (a.z + dz * t));
    }

    private static boolean hasRadio(ServerPlayer p) {
        if (p.isCreative()) {
            return true;
        }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof RadioItem radio && radio.type() == RadioItem.Type.FIELD) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ cooldowns, kept with the world

    private static final class Base {
        long readyAt;
        int bonus;
        Last last;
    }

    private static final Map<String, Base> BASES = new HashMap<>();

    private static Base base(String id) {
        return BASES.computeIfAbsent(id, k -> new Base());
    }

    /** A base's cooldown (ticks): an in-game day, a third less with a comms post. */
    static int cycle(ServerLevel level, String baseId) {
        double minutes = Math.max(0.5, ZcConfig.get().world.strikeCallMinutes);
        double cut = COMMS_POST.test(level, baseId) ? Math.clamp(ZcConfig.get().world.strikeCallCommsCut, 0, 0.9) : 0;
        return (int) Math.round(minutes * 60 * 20 * (1 - cut));
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(SmokeCloud.TYPE, SmokeCloud.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            server = s;
            load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            LIVE.clear();
            SMOKES.clear();
            SEEN.clear();
            BASES.clear();
            server = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(StrikeCalls::tick);
    }

    private static void tick(MinecraftServer s) {
        ServerLevel level = s.overworld();
        long now = level.getGameTime();
        for (ServerPlayer p : s.getPlayerList().getPlayers()) {
            Vec3 spot = spot(p);
            if (spot != null) {
                SEEN.put(p.getUUID(), new Seen(spot, now));
            }
        }
        for (var it = LIVE.iterator(); it.hasNext(); ) {
            Call c = it.next();
            double t = (now - c.start) / 20.0;
            if (!c.hot && !c.aborted && t >= c.release() - IN_HOT) {
                c.hot = true;
                c.tell(Moment.IN_HOT, 0, List.of(), null);
            }
            if (t >= c.done() + 1) {
                c.over = true;
                it.remove();
                if (c.aborted) {
                    continue;
                }
                int kills = 0;
                java.util.LinkedHashSet<String> hurt = new java.util.LinkedHashSet<>();
                for (AirStrikes.Run run : c.runs) {
                    kills += run.kills;
                    hurt.addAll(run.hurt);
                }
                base(c.base).last = new Last(c.type, BlockPos.containing(c.aim), Math.floorDiv(level.getDefaultClockTime(), 24000L) + 1, kills, hurt.size());
                save();
                c.tell(Moment.RESULT, kills, List.copyOf(hurt), null);
            }
        }
    }

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("zomboidcraft_strike_calls.json");
    }

    private static void load(MinecraftServer s) {
        BASES.clear();
        try {
            Path f = file(s);
            if (!Files.exists(f)) {
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            for (String id : root.keySet()) {
                JsonObject o = root.getAsJsonObject(id);
                Base b = base(id);
                b.readyAt = o.has("readyAt") ? o.get("readyAt").getAsLong() : 0;
                b.bonus = o.has("bonus") ? o.get("bonus").getAsInt() : 0;
                if (o.has("last")) {
                    JsonObject l = o.getAsJsonObject("last");
                    b.last = new Last(StrikeType.valueOf(l.get("type").getAsString()),
                            new BlockPos(l.get("x").getAsInt(), l.get("y").getAsInt(), l.get("z").getAsInt()),
                            l.get("day").getAsLong(), l.get("kills").getAsInt(), l.get("friendlies").getAsInt());
                }
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the strike calls", e);
        }
    }

    private static void save() {
        if (server == null) {
            return;
        }
        JsonObject root = new JsonObject();
        for (Map.Entry<String, Base> e : BASES.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("readyAt", e.getValue().readyAt);
            o.addProperty("bonus", e.getValue().bonus);
            Last l = e.getValue().last;
            if (l != null) {
                JsonObject j = new JsonObject();
                j.addProperty("type", l.type().name());
                j.addProperty("x", l.where().getX());
                j.addProperty("y", l.where().getY());
                j.addProperty("z", l.where().getZ());
                j.addProperty("day", l.day());
                j.addProperty("kills", l.kills());
                j.addProperty("friendlies", l.friendlies());
                o.add("last", j);
            }
            root.add(e.getKey(), o);
        }
        try {
            Files.writeString(file(server), root.toString());
        } catch (Exception ex) {
            ZomboidCraft.LOGGER.error("Couldn't save the strike calls", ex);
        }
    }
}
