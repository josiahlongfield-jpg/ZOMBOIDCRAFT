package com.zomboidcraft.world.events;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's search helicopter, its most memorable event (the Helicopter setting, Once by default: one day from the
 * 6th to the 9th). It flies in from far off toward one survivor, sweeps its searchlight round in widening circles,
 * and once it sees someone in the open it locks on: it hovers over them and follows wherever they go for up to four
 * in-game hours, and every second the noise of it reaches the ground under it, so the dead from all round, the ones
 * nobody can see as well, turn and follow it, and the crowd grows behind it as it goes. Get under a roof or a
 * bridge and it loses you: it circles where it saw you last, widens the search, and in the end flies off. The dead
 * it brought stay.
 *
 * <p>The real heli_hornbill flies it, driven here a tick at a time (a flyby aircraft, CarProp.flyby), with its own
 * engines' sound and its searchlight turned onto what it's looking at.
 */
public final class SearchHeli {
    /** When the next one is due (game ticks, -1 not yet planned) and how many have flown. Saved with the world. */
    public record Schedule(long next, int flown) {
        static final Codec<Schedule> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("next").forGetter(Schedule::next),
                Codec.INT.fieldOf("flown").forGetter(Schedule::flown)).apply(i, Schedule::new));
    }

    public static final AttachmentType<Schedule> SCHEDULE = AttachmentRegistry.create(ZomboidCraft.id("search_heli"),
            builder -> builder.persistent(Schedule.CODEC).initializer(() -> new Schedule(-1, 0)));

    public enum Phase { APPROACH, SEARCH, LOCKED, LOST, LEAVING }

    private static final long DAY = 24000, HOUR = 1000;
    private static final String MODEL = "heli_hornbill";
    /** Where it comes in from and leaves to, out of earshot. */
    private static final double FAR = 900;
    /** Blocks a second: in and out, searching, and the most it puts on to keep up with someone. */
    private static final double CRUISE = 30, SEARCH_SPEED = 9, CHASE = 26, ACCEL = 5;
    /** How high it flies over the ground, and how far its light reaches. */
    private static final double HEIGHT = 30, REACH = 140;
    /** How near the lit patch someone must be to be seen in it, or under it. */
    private static final double SPOTTED = 10, UNDER = 18;
    /** The aircraft is only in the world while a player is this near it. */
    private static final double SHOW = 256;

    /** The one flying, if any. Server thread only. */
    private static Flight current;

    private static final class Flight {
        Phase phase = Phase.APPROACH;
        ServerPlayer target;
        Vec3 pos, vel = Vec3.ZERO;
        float yaw;
        /** Centre of the search, and when the search (or the circling after losing someone) began. */
        Vec3 centre;
        long since;
        /** When it locked on, and when it last saw them. */
        long lockedAt, seenAt;
        Vec3 lastSeen;
        Vec3 leaveTo;
        CarProp aircraft;
        long started;
        /** The sweep's angle round the centre (radians). */
        double sweep;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SearchHeli::tick);
    }

    /** What it's doing now, for tests and /zc perf; null when there's none. */
    public static Phase phase() {
        return current == null ? null : current.phase;
    }

    public static Vec3 position() {
        return current == null ? null : current.pos;
    }

    private static long day(ServerLevel level) {
        return level.getGameTime() / DAY;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (current != null) {
            fly(level, current);
            return;
        }
        ZcConfig.SearchHeli cfg = ZcConfig.get().searchHeli;
        if (cfg.frequency == ZcConfig.SearchHeli.Frequency.NEVER || level.getGameTime() % 100 != 0 || level.players().isEmpty()) {
            return;
        }
        Schedule s = level.getAttachedOrCreate(SCHEDULE);
        RandomSource random = level.getRandom();
        if (s.next() < 0) {
            int lo = Math.max(0, cfg.firstDay), hi = Math.max(lo, cfg.lastDay);
            long next = (lo + random.nextInt(hi - lo + 1)) * DAY + random.nextInt((int) DAY);
            if (next < level.getGameTime()) {
                // a world already past those days: its one came and went before there was a helicopter to send
                next = cfg.frequency == ZcConfig.SearchHeli.Frequency.ONCE ? Long.MAX_VALUE / 4
                        : level.getGameTime() + (2 + random.nextInt(5)) * DAY;
            }
            level.setAttached(SCHEDULE, s = new Schedule(next, s.flown()));
        }
        if (level.getGameTime() < s.next()) {
            return;
        }
        long again = switch (cfg.frequency) {
            case SOMETIMES -> (14 + random.nextInt(15)) * DAY + random.nextInt((int) DAY);
            case OFTEN -> (2 + random.nextInt(4)) * DAY + random.nextInt((int) DAY);
            default -> Long.MAX_VALUE / 4;   // once
        };
        level.setAttached(SCHEDULE, new Schedule(level.getGameTime() + again, s.flown() + 1));
        start(level, pick(level, random));
    }

    /** Someone outdoors if anyone is, otherwise anyone (it comes looking either way). */
    private static ServerPlayer pick(ServerLevel level, RandomSource random) {
        List<ServerPlayer> out = new ArrayList<>(), all = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isAlive() && !p.isSpectator()) {
                all.add(p);
                if (inTheOpen(level, p)) {
                    out.add(p);
                }
            }
        }
        List<ServerPlayer> from = out.isEmpty() ? all : out;
        return from.isEmpty() ? null : from.get(random.nextInt(from.size()));
    }

    /** Sends one in now, toward this player (the test hook; the schedule above otherwise). */
    public static void start(ServerLevel level, ServerPlayer target) {
        start(level, target, FAR);
    }

    public static void start(ServerLevel level, ServerPlayer target, double from) {
        if (target == null) {
            return;
        }
        end();
        Flight f = new Flight();
        double a = level.getRandom().nextDouble() * Math.PI * 2;
        f.target = target;
        f.pos = target.position().add(Math.cos(a) * from, 0, Math.sin(a) * from);
        f.pos = new Vec3(f.pos.x, Math.max(target.getY(), level.getSeaLevel()) + HEIGHT + 20, f.pos.z);
        f.yaw = (float) Math.toDegrees(Math.atan2(-(target.getX() - f.pos.x), target.getZ() - f.pos.z));
        f.started = level.getGameTime();
        f.leaveTo = f.pos;
        current = f;
        ZomboidCraft.LOGGER.info("Search helicopter coming in from {} {} toward {}", (int) f.pos.x, (int) f.pos.z,
                target.getName().getString());
    }

    /** Takes it out of the sky (a new one replacing it, or a test). */
    public static void end() {
        if (current != null && current.aircraft != null) {
            current.aircraft.discard();
        }
        current = null;
    }

    // ---- flying it

    private static void fly(ServerLevel level, Flight f) {
        long now = level.getGameTime();
        if (f.target == null || f.target.isRemoved() || f.target.level() != level || !f.target.isAlive()) {
            if (f.phase != Phase.LEAVING) {
                leave(f, level);
            }
        }
        ZcConfig.SearchHeli cfg = ZcConfig.get().searchHeli;
        Vec3 goal;
        double speed;
        Vec3 look = null;   // where the light is turned
        switch (f.phase) {
            case APPROACH -> {
                goal = above(level, f.target.position(), f);
                speed = CRUISE;
                if (horizontal(f.pos, f.target.position()) < 110) {
                    f.phase = Phase.SEARCH;
                    // it knows roughly where to look, not exactly
                    RandomSource r = level.getRandom();
                    f.centre = f.target.position().add(r.nextDouble() * 30 - 15, 0, r.nextDouble() * 30 - 15);
                    f.since = now;
                }
            }
            case SEARCH, LOST -> {
                // widening circles round where to look, the light swept round inside them
                double t = (now - f.since) / 20.0;
                double radius = Math.min(f.phase == Phase.LOST ? 60 : 70, (f.phase == Phase.LOST ? 12 : 20) + t * 0.9);
                f.sweep += SEARCH_SPEED / Math.max(radius, 10) / 20;
                goal = above(level, f.centre.add(Math.cos(f.sweep) * radius, 0, Math.sin(f.sweep) * radius), f);
                speed = SEARCH_SPEED + 4;
                double inner = radius * 0.55;
                look = ground(level, f.centre.add(Math.cos(f.sweep * 2.3) * inner, 0, Math.sin(f.sweep * 2.3) * inner));
                if (t > (f.phase == Phase.LOST ? 75 : 120)) {
                    leave(f, level);
                }
            }
            case LOCKED -> {
                // over them, a little behind, the light on them
                Vec3 at = f.target.position();
                Vec3 trail = f.vel.horizontalDistanceSqr() > 1 ? f.vel.multiply(1, 0, 1).normalize().scale(-6) : Vec3.ZERO;
                goal = above(level, at.add(trail), f);
                speed = CHASE;
                look = at;
                if (now % 20 == 0) {
                    // the noise of it reaches the ground under it: the dead from all round come
                    Vec3 under = ground(level, f.pos);
                    Noise.emit(level, under, cfg.noise);
                }
                if (now - f.lockedAt > cfg.lockHours * HOUR) {
                    leave(f, level);
                }
            }
            default -> {
                goal = f.leaveTo;
                speed = CRUISE;
                if (horizontal(f.pos, f.leaveTo) < 40) {
                    end();
                    return;
                }
            }
        }
        if (now % 5 == 0) {
            watch(level, f, look, now);
        }
        steer(f, goal, speed);
        Vec3 aim = look == null ? null : look.subtract(lamp(f)).normalize();
        show(level, f, aim);
    }

    /** Looks for whoever it's after: in the light or right under it, out in the open. */
    private static void watch(ServerLevel level, Flight f, Vec3 lit, long now) {
        switch (f.phase) {
            case SEARCH, LOST -> {
                for (ServerPlayer p : level.players()) {
                    if (!p.isAlive() || p.isSpectator() || !inTheOpen(level, p)) {
                        continue;
                    }
                    boolean inLight = lit != null && horizontal(p.position(), lit) < SPOTTED && clear(level, lamp(f), p.getEyePosition());
                    boolean under = horizontal(p.position(), f.pos) < UNDER;
                    if (inLight || under) {
                        f.target = p;
                        f.phase = Phase.LOCKED;
                        if (f.lockedAt == 0) {
                            f.lockedAt = now;
                        }
                        f.seenAt = now;
                        ZomboidCraft.LOGGER.info("Search helicopter has {} in its light", p.getName().getString());
                        return;
                    }
                }
            }
            case LOCKED -> {
                if (inTheOpen(level, f.target) && clear(level, lamp(f), f.target.getEyePosition())) {
                    f.seenAt = now;
                    f.lastSeen = f.target.position();
                } else if (now - f.seenAt > 60) {
                    // under a roof or a bridge: it's lost them, and circles where they were
                    f.phase = Phase.LOST;
                    f.centre = f.lastSeen != null ? f.lastSeen : f.target.position();
                    f.since = now;
                    ZomboidCraft.LOGGER.info("Search helicopter has lost sight of {}", f.target.getName().getString());
                }
            }
            default -> {
            }
        }
    }

    private static void leave(Flight f, ServerLevel level) {
        f.phase = Phase.LEAVING;
        Vec3 away = f.pos.subtract(f.target == null ? f.pos.add(1, 0, 0) : f.target.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1) {
            away = new Vec3(1, 0, 0);
        }
        f.leaveTo = f.pos.add(away.normalize().scale(FAR));
        f.leaveTo = new Vec3(f.leaveTo.x, f.pos.y + 25, f.leaveTo.z);
    }

    /** A point HEIGHT over the ground at pos, and over anything tall between it and the helicopter. */
    private static Vec3 above(ServerLevel level, Vec3 pos, Flight f) {
        double top = surface(level, pos.x, pos.z, f.pos.y - HEIGHT);
        Vec3 ahead = pos.subtract(f.pos).multiply(1, 0, 1);
        double len = ahead.length();
        for (double s = 0; s < Math.min(len, 60); s += 10) {
            Vec3 p = f.pos.add(ahead.scale(s / Math.max(len, 1)));
            top = Math.max(top, surface(level, p.x, p.z, top));
        }
        return new Vec3(pos.x, top + HEIGHT, pos.z);
    }

    /** Flies toward goal no faster than speed, easing in and out, so it never jumps. */
    private static void steer(Flight f, Vec3 goal, double speed) {
        double dt = 0.05;
        Vec3 to = goal.subtract(f.pos);
        double dist = to.length();
        // slows as it gets there: no faster than it can stop
        double want = Math.min(speed, Math.sqrt(2 * ACCEL * Math.max(dist - 1, 0)));
        Vec3 desired = dist < 1e-3 ? Vec3.ZERO : to.scale(want / dist);
        Vec3 dv = desired.subtract(f.vel);
        double max = ACCEL * dt;
        if (dv.length() > max) {
            dv = dv.normalize().scale(max);
        }
        f.vel = f.vel.add(dv);
        f.pos = f.pos.add(f.vel.scale(dt));
        // nose the way it's going when it's going somewhere, else round toward who it's watching
        Vec3 face = f.vel.horizontalDistanceSqr() > 9 ? f.vel
                : f.target != null ? f.target.position().subtract(f.pos) : f.vel;
        if (face.horizontalDistanceSqr() > 1e-4) {
            float wantYaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
            float d = net.minecraft.util.Mth.wrapDegrees(wantYaw - f.yaw);
            f.yaw += Math.clamp(d, -1.5f, 1.5f);   // 30 degrees a second
        }
    }

    /** Puts the aircraft where it is while anyone's near enough to see it, its light turned where it's looking. */
    private static void show(ServerLevel level, Flight f, Vec3 aim) {
        boolean seen = level.isLoaded(BlockPos.containing(f.pos)) && level.players().stream()
                .anyMatch(p -> horizontal(p.position(), f.pos) < SHOW);
        if (!seen) {
            if (f.aircraft != null) {
                f.aircraft.discard();
                f.aircraft = null;
            }
            return;
        }
        if (f.aircraft == null || f.aircraft.isRemoved()) {
            CarProp car = Cars.CAR.create(level, EntitySpawnReason.EVENT);
            if (car == null) {
                return;
            }
            car.setLook(MODEL, "usable");
            place(car, f);
            level.addFreshEntity(car);
            f.aircraft = car;
        }
        place(f.aircraft, f);
        f.aircraft.aimSearchlight(aim == null ? Vec3.ZERO : aim);
    }

    /** Nose down to speed up, up to slow, leaning into its turns (as the flyby helicopter does). */
    private static void place(CarProp car, Flight f) {
        double rad = Math.toRadians(f.yaw);
        Vec3 ahead = new Vec3(-Math.sin(rad), 0, Math.cos(rad)), right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        float nose = (float) Math.clamp(-3 - 0.1 * f.vel.dot(ahead), -16, 9);
        float bank = (float) Math.clamp(f.vel.dot(right) * 0.6, -18, 18);
        car.flyby(f.pos, f.vel, f.yaw, nose, bank, false);
    }

    /** The searchlight under the nose, near enough. */
    private static Vec3 lamp(Flight f) {
        return f.pos.add(0, 0.5, 0);
    }

    // ---- what it can see

    /** Out in the open: nothing over their head between them and the sky. */
    static boolean inTheOpen(ServerLevel level, ServerPlayer p) {
        return level.canSeeSky(BlockPos.containing(p.getEyePosition()));
    }

    private static boolean clear(ServerLevel level, Vec3 from, Vec3 to) {
        if (from.distanceTo(to) > REACH) {
            return false;
        }
        return level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    private static Vec3 ground(ServerLevel level, Vec3 pos) {
        return new Vec3(pos.x, surface(level, pos.x, pos.z, pos.y - HEIGHT), pos.z);
    }

    private static double surface(ServerLevel level, double x, double z, double fallback) {
        BlockPos pos = BlockPos.containing(x, fallback, z);
        if (!level.isLoaded(pos)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    private static double horizontal(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private SearchHeli() {
    }
}
