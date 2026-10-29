package com.zomboidcraft.world.events;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * The air strikes among the world events (Josiah 2026-10-28 09:27): a jet diving on a crowd of the dead and raking
 * it with its cannon, one or two jets crossing a crowd low and dropping a stick of bombs on it, and the Apache
 * coming in to circle a horde and hover over it, picking zombies off with its chain gun for most of a minute
 * before it leaves. They're flybys on paths of their own (WorldEvents.Event's path), so everyone's game draws
 * them coming in from far off and hears them as any flyby; the guns and bombs are worked out here.
 *
 * What they hit (Josiah's pick, 09:28): zombies die, and a player standing right where the shells or bombs land
 * is hurt or killed; nothing else is touched (no blocks broken, buildings, roads and cars all stay as they were).
 * A strike always picks a crowd at least a little way from every player, so only someone who walks in under
 * it is caught.
 */
public final class AirStrikes {
    public static final SoundEvent CANNON = WorldEvents.register("world.strike.cannon", 640f);
    public static final SoundEvent CHAIN_GUN = WorldEvents.register("world.strike.chaingun", 480f);
    private static final double G = 9.81, STEP = WorldEvents.Event.STEP;
    /** How far players are told about a round's tracer or a bomb (blocks). */
    private static final double TELL = 640;
    /** A crowd's reach: zombies within this of its middle count toward it. */
    private static final double CROWD = 12;

    /** One shell's tracer: from where it left the gun to where it struck. */
    public record Tracer(float x, float y, float z, float tx, float ty, float tz) implements CustomPacketPayload {
        public static final Type<Tracer> TYPE = new Type<>(ZomboidCraft.id("strike_tracer"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tracer> CODEC = StreamCodec.of((buf, t) -> {
            buf.writeFloat(t.x);
            buf.writeFloat(t.y);
            buf.writeFloat(t.z);
            buf.writeFloat(t.tx);
            buf.writeFloat(t.ty);
            buf.writeFloat(t.tz);
        }, buf -> new Tracer(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

        public Vec3 from() {
            return new Vec3(x, y, z);
        }

        public Vec3 to() {
            return new Vec3(tx, ty, tz);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A bomb let go: where and how it was moving; each game lets it fall the same way (and stops it where it lands). */
    public record Bomb(float x, float y, float z, float vx, float vy, float vz) implements CustomPacketPayload {
        public static final Type<Bomb> TYPE = new Type<>(ZomboidCraft.id("strike_bomb"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Bomb> CODEC = StreamCodec.of((buf, b) -> {
            buf.writeFloat(b.x);
            buf.writeFloat(b.y);
            buf.writeFloat(b.z);
            buf.writeFloat(b.vx);
            buf.writeFloat(b.vy);
            buf.writeFloat(b.vz);
        }, buf -> new Bomb(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Tracer.TYPE, Tracer.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Bomb.TYPE, Bomb.CODEC);
    }

    private AirStrikes() {
    }

    // ------------------------------------------------------------------ planning

    /**
     * Plans a strike of this kind on the biggest crowd of zombies near this player (one far enough from every
     * player), and the aircraft that fly it: its event (the path everyone's told) and what it does on the way.
     * Empty if there's no crowd to hit, unless forced, when it hits the ground ahead of the player instead.
     */
    static List<Run> plan(ServerLevel level, WorldEvents.Kind kind, ServerPlayer near, boolean force) {
        ZcConfig.World cfg = ZcConfig.get().world;
        RandomSource random = level.getRandom();
        double clear = kind == WorldEvents.Kind.BOMB ? 22 : kind == WorldEvents.Kind.STRAFE ? 14 : 6;
        Vec3 target = crowd(level, near, clear, Math.max(1, cfg.airStrikeCrowd));
        if (target == null) {
            if (!force) {
                return List.of();
            }
            Vec3 look = near.getLookAngle().multiply(1, 0, 1);
            look = look.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : look.normalize();
            Vec3 p = near.position().add(look.scale(clear + 40));
            target = new Vec3(p.x, WorldEvents.surface(level, p.x, p.z, near.getY()), p.z);
        }
        // Josiah 09:46: low over a city they line up with the street and never fly into anything, so the run comes
        // in along whichever way has the least in its way (down the street, in town), raised to clear what's left
        // (for a jet, how high its low pass has to be over the target's stretch; for the Apache, over its way in)
        boolean jet = kind != WorldEvents.Kind.HUNT;
        double from = jet ? -450 : -600, to = jet ? 250 : -30;
        Vec3 dir = null;
        double lift = Double.MAX_VALUE, offset = random.nextDouble() * Math.PI * 2;
        for (int i = 0; i < HEADINGS; i++) {
            double a = offset + i * Math.PI * 2 / HEADINGS;
            Vec3 d = new Vec3(Math.cos(a), 0, Math.sin(a));
            double l = lift(level, target, d, FLAT, LEVEL, from, to, 8) + random.nextDouble() * 2;
            if (l < lift) {
                lift = l;
                dir = d;
            }
        }
        if (lift > MAX_LIFT && jet && !force) {
            return List.of();       // walled in by towers: the shells and bombs would only hit roofs
        }
        List<Run> runs = new ArrayList<>();
        long now = level.getGameTime();
        switch (kind) {
            case STRAFE -> {
                String model = WorldEvents.jet(random);
                // each breaks away one way or the other after its run, a pair splitting (Josiah 20:19)
                int side = random.nextBoolean() ? 1 : -1;
                runs.add(strafe(level, target, dir, model, now, 0, side, random));
                if (random.nextInt(10) < 4) {
                    // a wingman a few seconds behind down the same street, its shells walked a little to one side
                    runs.add(strafe(level, target, dir, model, now, 4 + random.nextDouble() * 3, -side, random));
                }
            }
            case BOMB -> {
                String model = WorldEvents.jet(random);
                int side = random.nextBoolean() ? 1 : -1;
                runs.add(bomb(level, target, dir, model, now, 0, 0, side, random));
                if (random.nextInt(10) < 5) {
                    // the wingman's stick beside the leader's, and it breaks the other way
                    runs.add(bomb(level, target, dir, model, now, 2.5 + random.nextDouble() * 1.5, -side * 5, -side, random));
                }
            }
            default -> runs.add(hunt(level, target, dir, now, random));
        }
        return runs;
    }

    /**
     * The middle of the biggest crowd of zombies within a few hundred blocks of the player that's at least clear
     * blocks from every player, out under the sky by preference (bombs and shells stop on a roof); null if none
     * has at least least zombies in it.
     */
    private static Vec3 crowd(ServerLevel level, ServerPlayer near, double clear, int least) {
        List<Zombie> dead = level.getEntitiesOfClass(Zombie.class, near.getBoundingBox().inflate(180, 64, 180),
                z -> z.isAlive() && ZombieTypes.isWalker(z));
        if (dead.isEmpty()) {
            return null;
        }
        RandomSource random = level.getRandom();
        Zombie best = null;
        double bestScore = 0;
        for (int k = 0; k < Math.min(160, dead.size()); k++) {
            Zombie c = dead.size() <= 160 ? dead.get(k) : dead.get(random.nextInt(dead.size()));
            boolean nearPlayer = false;
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && p.position().distanceToSqr(c.position()) < clear * clear) {
                    nearPlayer = true;
                    break;
                }
            }
            if (nearPlayer) {
                continue;
            }
            int count = 0;
            for (Zombie z : dead) {
                if (z.position().distanceToSqr(c.position()) < CROWD * CROWD) {
                    count++;
                }
            }
            double score = count * (level.canSeeSky(c.blockPosition().above()) ? 1 : 0.3);
            if (count >= least && score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        if (best == null) {
            return null;
        }
        double x = 0, z = 0;
        int n = 0;
        for (Zombie each : dead) {
            if (each.position().distanceToSqr(best.position()) < CROWD * CROWD) {
                x += each.getX();
                z += each.getZ();
                n++;
            }
        }
        return new Vec3(x / n, best.getY(), z / n);
    }

    /**
     * How far above the target a path along dir has to be raised to clear everything under it by margin, given
     * how high it already is at each point along (the key points' heights, straight between).
     */
    private static double lift(ServerLevel level, Vec3 target, Vec3 dir, double[] along, double[] height, double from, double to,
            double margin) {
        double lift = 0;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        for (double u = from; u <= to; u += 6) {
            Vec3 p = target.add(dir.scale(u));
            // under the middle and out to both wing tips
            for (double w = -WING; w <= WING; w += WING) {
                double ground = top(level, p.x + side.x * w, p.z + side.z * w, target.y) - target.y;
                lift = Math.max(lift, ground + margin - heightAt(along, height, u));
            }
        }
        return lift;
    }

    /** The highest thing here that an aircraft could hit, trees included, if the chunk is loaded (else the fallback). */
    private static double top(ServerLevel level, double x, double z, double fallback) {
        BlockPos pos = BlockPos.containing(x, level.getSeaLevel(), z);
        if (!level.isLoaded(pos)) {
            return fallback;
        }
        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
    }

    /**
     * The last check on a path: anywhere it would pass within margin of anything under it or beside it (half
     * blocks out each way, a wing or the rotor), it climbs to clear it, starting the climb ease seconds before
     * and easing back down after, so it never flies into a building.
     */
    private static void clear(ServerLevel level, float[] path, double half, double margin, double ease) {
        int k = WorldEvents.Event.STRIDE, n = path.length / k;
        double[] need = new double[n];
        for (int i = 0; i < n; i++) {
            double x = path[i * k], y = path[i * k + 1], z = path[i * k + 2];
            double highest = Double.NEGATIVE_INFINITY;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    highest = Math.max(highest, top(level, x + dx * half, z + dz * half, Double.NEGATIVE_INFINITY));
                }
            }
            need[i] = Math.max(0, highest + margin - y);
        }
        int window = (int) Math.round(ease / STEP);
        for (int i = 0; i < n; i++) {
            double raise = 0;
            for (int j = Math.max(0, i - window); j <= Math.min(n - 1, i + window); j++) {
                // full height over the obstacle itself, eased in and out either side of it
                double f = 1 - Math.abs(j - i) / (double) (window + 1);
                raise = Math.max(raise, need[j] * Math.min(1, f * 1.6));
            }
            path[i * k + 1] = (float) Math.min(path[i * k + 1] + raise, level.getMaxY() - 4);
        }
    }

    private static double heightAt(double[] along, double[] height, double u) {
        for (int i = 1; i < along.length; i++) {
            if (u <= along[i]) {
                double f = (u - along[i - 1]) / (along[i] - along[i - 1]);
                return height[i - 1] + (height[i] - height[i - 1]) * Math.clamp(f, 0, 1);
            }
        }
        return height[height.length - 1];
    }

    /** How many ways in a strike tries, half a jet's span and the rotor's reach (blocks), and the highest a low pass goes. */
    private static final int HEADINGS = 36;
    private static final double WING = 8, ROTOR = 7, MAX_LIFT = 70;
    /** A run's whole length either side of the target, and a level line along it for the way in (see plan). */
    private static final double REACH = 1800;
    private static final double[] FLAT = {-REACH, REACH}, LEVEL = {0, 0};

    /** The event for a path flown by model, starting delay seconds from now (a wingman's, behind the leader). */
    private static WorldEvents.Event event(WorldEvents.Kind kind, float[] path, String model, long now, double delay) {
        // a few still points at the start hold it where it comes in until its time
        int k = WorldEvents.Event.STRIDE, wait = (int) Math.round(delay / STEP);
        float[] held = new float[path.length + wait * k];
        for (int i = 0; i < wait; i++) {
            System.arraycopy(path, 0, held, i * k, k);
        }
        System.arraycopy(path, 0, held, wait * k, path.length);
        int ticks = (int) Math.ceil((held.length / k - 1) * STEP * 20);
        return new WorldEvents.Event(kind, held[0], held[1], held[2], 0, 0, 0, now, ticks, model, 0, held);
    }

    /**
     * A strafing run (StrikeFlight.strafe): in a little higher than the old low pass on a mild dive at the crowd
     * down the street, the nose eased up to walk the shells along it, bottoming out just over the rooftops, then
     * the pull up and the break to side.
     */
    private static Run strafe(ServerLevel level, Vec3 target, Vec3 dir, String model, long now, double delay, int side,
            RandomSource random) {
        double speed = 85 + random.nextDouble() * 12;
        double low = Math.max(16 + random.nextDouble() * 6, lift(level, target, dir, FLAT, LEVEL, -300, 250, 8));
        StrikeFlight.Plan plan = StrikeFlight.strafe(level, target, dir, speed, Math.toRadians(14 + random.nextDouble() * 3), low,
                low + 92 + random.nextDouble() * 15, side, Math.toRadians(65 + random.nextDouble() * 13),
                Math.toRadians(60 + random.nextDouble() * 35));
        float[] path = plan.path();
        clear(level, path, WING, 8, 2);
        WorldEvents.Event e = event(WorldEvents.Kind.STRAFE, path, model, now, delay);
        Run run = new Run(e, target, dir);
        double held = Math.round(delay / STEP) * STEP;
        run.fireFrom = held + plan.fireFrom();
        run.fireTo = held + plan.fireTo();
        double offset = (random.nextDouble() * 2 - 1) * 3;
        Vec3 across = new Vec3(-dir.z, 0, dir.x);
        run.lineFrom = target.add(dir.scale(-StrikeFlight.LINE / 2)).add(across.scale(offset)).add(0, 0.5, 0);
        run.lineTo = target.add(dir.scale(StrikeFlight.LINE / 2)).add(across.scale(-offset)).add(0, 0.5, 0);
        return run;
    }

    /**
     * A bombing run (StrikeFlight.bomb): in on a gentle dive, rounded out to level just over the roofs, a stick of
     * four to six bombs let go so they fall on a line through the crowd a few blocks apart, then the pull up and
     * the break to side; a wingman's lands beside the first's.
     */
    private static Run bomb(ServerLevel level, Vec3 target, Vec3 dir, String model, long now, double delay, double offset,
            int side, RandomSource random) {
        double speed = 78 + random.nextDouble() * 12;
        Vec3 aim = target.add(new Vec3(-dir.z, 0, dir.x).scale(offset));
        double low = Math.max(24 + random.nextDouble() * 6, lift(level, aim, dir, FLAT, LEVEL, -450, 250, 9));
        int count = 4 + random.nextInt(3);
        StrikeFlight.Plan plan = StrikeFlight.bomb(level, aim, dir, speed, Math.toRadians(8 + random.nextDouble() * 2), low,
                low + 75 + random.nextDouble() * 15, side, Math.toRadians(62 + random.nextDouble() * 14),
                Math.toRadians(60 + random.nextDouble() * 35), count);
        float[] path = plan.path();
        clear(level, path, WING, 9, 2);
        WorldEvents.Event e = event(WorldEvents.Kind.BOMB, path, model, now, delay);
        Run run = new Run(e, aim, dir);
        double held = Math.round(delay / STEP) * STEP;
        run.release = new double[count];
        for (int i = 0; i < count; i++) {
            run.release[i] = held + plan.release()[i];
        }
        return run;
    }

    /**
     * The Apache's hunt: in from far off, slowing and dropping low as it nears the horde, then round it for most
     * of a minute nose in, down between the buildings wherever there's room (the circle picked to have the most
     * open ground on it) and up over them where there isn't, drifting nearer and further and nearly stopping now
     * and then, bursts of its chain gun picking the dead off; then away, nose down to gather speed, and climbing.
     */
    private static Run hunt(ServerLevel level, Vec3 target, Vec3 dir, long now, RandomSource random) {
        // the circle with the least in the way: mostly over the street, between the buildings
        double radius = 34, best = Double.MAX_VALUE;
        for (double r = 20; r <= 50; r += 6) {
            double sum = 0;
            for (int a = 0; a < 24; a++) {
                double x = target.x + Math.cos(a * Math.PI / 12) * r, z = target.z + Math.sin(a * Math.PI / 12) * r;
                double high = target.y;
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        high = Math.max(high, top(level, x + dx * ROTOR, z + dz * ROTOR, target.y));
                    }
                }
                sum += high - target.y;
            }
            double score = sum / 24 + random.nextDouble() * 2;
            if (score < best) {
                best = score;
                radius = r;
            }
        }
        double low = 11 + random.nextDouble() * 6;
        List<Vec3> points = new ArrayList<>();
        List<Float> yaws = new ArrayList<>();
        // in: from 900 out, at 40 blocks a second until it has to slow to reach the circle at orbit speed, coming down
        double orbit = 9;
        Vec3 entry = target.add(dir.scale(-radius)).add(0, low, 0);
        Vec3 start = target.add(dir.scale(-900)).add(0, low + 45, 0);
        double total = start.distanceTo(entry), gone = 0;
        float inYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        while (gone < total) {
            double f = gone / total;
            Vec3 p = start.add(entry.subtract(start).scale(f));
            // it holds its height until the last 250 blocks
            double left = total - gone, drop = Math.clamp(1 - left / 250, 0, 1);
            points.add(new Vec3(p.x, entry.y + 45 * (1 - drop), p.z));
            yaws.add(inYaw);
            double v = Math.min(40, orbit + Math.sqrt(2 * 2.5 * left));
            gone += v * STEP;
        }
        // round the horde, facing it
        double seconds = 40 + random.nextDouble() * 15, spin = random.nextBoolean() ? 1 : -1;
        double angle = Math.atan2(entry.z - target.z, entry.x - target.x);
        double huntFrom = points.size() * STEP + 3;
        for (double t = 0; t < seconds; t += STEP) {
            double r = radius + 4 * Math.sin(t * 0.21);
            // nearly stops to hover over it every 18 seconds or so
            double rate = orbit / r * (0.45 + 0.55 * (0.5 - 0.5 * Math.cos(t * Math.PI * 2 / 18)));
            angle += spin * rate * STEP;
            Vec3 p = new Vec3(target.x + Math.cos(angle) * r, target.y + low + 2 * Math.sin(t * 0.37), target.z + Math.sin(angle) * r);
            float face = (float) Math.toDegrees(Math.atan2(-(target.x - p.x), target.z - p.z));
            float blend = (float) Math.min(1, t / 3);
            yaws.add(inYaw + net.minecraft.util.Mth.wrapDegrees(face - inYaw) * blend);
            points.add(p);
        }
        double huntTo = points.size() * STEP - 2;
        // away: out along a new heading, speeding up and climbing
        double away = Math.atan2(dir.z, dir.x) + (random.nextDouble() - 0.5) * Math.PI;
        Vec3 out = new Vec3(Math.cos(away), 0, Math.sin(away));
        float outYaw = (float) Math.toDegrees(Math.atan2(-out.x, out.z));
        float lastYaw = yaws.get(yaws.size() - 1);
        Vec3 p = points.get(points.size() - 1);
        double v = orbit, t = 0;
        while (p.distanceTo(target) < 950) {
            v = Math.min(44, v + 3 * STEP);
            t += STEP;
            p = p.add(out.scale(v * STEP)).add(0, Math.min(3, v * 0.08) * STEP, 0);
            points.add(p);
            yaws.add(lastYaw + net.minecraft.util.Mth.wrapDegrees(outYaw - lastYaw) * (float) Math.min(1, t / 4));
        }
        int k = WorldEvents.Event.STRIDE;
        float[] path = new float[points.size() * k];
        float yaw = yaws.getFirst();
        for (int i = 0; i < points.size(); i++) {
            // carried on round rather than wrapped, so it turns smoothly between points
            yaw += net.minecraft.util.Mth.wrapDegrees(yaws.get(i) - yaw);
            path[i * k] = (float) points.get(i).x;
            path[i * k + 1] = (float) Math.min(points.get(i).y, level.getMaxY() - 8);
            path[i * k + 2] = (float) points.get(i).z;
            path[i * k + 3] = yaw;
            // its nose and lean are left to how it's flying (WorldEvents.Event.place)
            path[i * k + 4] = Float.NaN;
            path[i * k + 5] = Float.NaN;
        }
        // up over anything in the way of its rotor, and back down between the buildings after
        clear(level, path, ROTOR, 5, 2.5);
        Run hunt = new Run(event(WorldEvents.Kind.HUNT, path, "heli_hornbill", now, 0), target, dir);
        hunt.huntFrom = huntFrom;
        hunt.huntTo = huntTo;
        hunt.nextBurst = huntFrom;
        return hunt;
    }

    // ------------------------------------------------------------------ flying it

    /** One aircraft's strike under way: its path, where it's hitting, and its guns or bombs. */
    static final class Run {
        final WorldEvents.Event event;
        final Vec3 target, dir;
        /** A strafing run's firing: from when to when (seconds in), and the line its shells walk along. */
        double fireFrom = -1, fireTo = -1;
        Vec3 lineFrom, lineTo;
        double rounds;
        boolean fired;
        /** A bombing run's releases (seconds in), and the bombs falling. */
        double[] release = new double[0];
        int dropped;
        final List<Falling> bombs = new ArrayList<>();
        /** The hunt: from when to when it's over the horde, its next burst and the one it's firing at now. */
        double huntFrom, huntTo, nextBurst, burstEnd;
        Zombie mark;

        Run(WorldEvents.Event event, Vec3 target, Vec3 dir) {
            this.event = event;
            this.target = target;
            this.dir = dir;
        }

        void tick(ServerLevel level, double t) {
            if (fireFrom >= 0 && t >= fireFrom && t <= fireTo) {
                strafe(level, t);
            }
            while (dropped < release.length && release[dropped] >= 0 && t >= release[dropped]) {
                drop(level, t);
                dropped++;
            }
            for (var it = bombs.iterator(); it.hasNext(); ) {
                if (it.next().fall(level)) {
                    it.remove();
                }
            }
            if (event.kind() == WorldEvents.Kind.HUNT && t >= huntFrom && t <= huntTo) {
                hunt(level, t);
            }
        }

        /** The nose cannon: three or so shells a tick, walked along the line through the crowd. */
        private void strafe(ServerLevel level, double t) {
            Vec3 at = event.at(t), v = event.velocity(t).normalize();
            Vec3 muzzle = at.add(v.scale(7));
            if (!fired) {
                fired = true;
                sound(level, muzzle, CANNON, 4f);
            }
            double f = (t - fireFrom) / Math.max(0.05, fireTo - fireFrom);
            Vec3 aim = lineFrom.add(lineTo.subtract(lineFrom).scale(f));
            rounds += ZcConfig.get().world.strikeCannonRate / 20.0;
            int shots = (int) rounds;
            rounds -= shots;
            for (int i = 0; i < shots; i++) {
                round(level, muzzle, aim, 3.2, 1.9, 3.2, 14, i == 0);
            }
        }

        /** Lets the next bomb go from under the jet. */
        private void drop(ServerLevel level, double t) {
            Vec3 at = event.at(t).add(0, -1.5, 0), v = event.velocity(t);
            RandomSource random = level.getRandom();
            Vec3 vel = v.add(random.nextGaussian() * 0.6, 0, random.nextGaussian() * 0.6);
            bombs.add(new Falling(at, vel));
            Bomb packet = new Bomb((float) at.x, (float) at.y, (float) at.z, (float) vel.x, (float) vel.y, (float) vel.z);
            for (ServerPlayer player : PlayerLookup.around(level, at, TELL)) {
                ServerPlayNetworking.send(player, packet);
            }
        }

        /** Over the horde: bursts of the chain gun at one zombie after another, a breather between them. */
        private void hunt(ServerLevel level, double t) {
            Vec3 at = event.at(t);
            float yaw = event.yaw(t);
            double rad = Math.toRadians(yaw);
            Vec3 ahead = new Vec3(-Math.sin(rad), 0, Math.cos(rad));
            Vec3 muzzle = at.add(ahead.scale(3.6)).add(0, -0.6, 0);
            RandomSource random = level.getRandom();
            if (t >= nextBurst && t > burstEnd) {
                mark = pick(level, muzzle);
                if (mark == null) {
                    nextBurst = t + 1;
                    return;
                }
                burstEnd = t + 1.2 + random.nextDouble() * 1.2;
                nextBurst = burstEnd + 1.8 + random.nextDouble() * 2.4;
                sound(level, muzzle, CHAIN_GUN, 3f);
            }
            if (t > burstEnd || mark == null) {
                return;
            }
            if (!mark.isAlive()) {
                // on to the next one in the same burst, if one's close by
                mark = pick(level, muzzle);
                if (mark == null) {
                    burstEnd = t;
                    return;
                }
            }
            rounds += ZcConfig.get().world.strikeChainGunRate / 20.0;
            int shots = (int) rounds;
            rounds -= shots;
            for (int i = 0; i < shots; i++) {
                round(level, muzzle, mark.position().add(0, 1, 0), 1.4, 1.5, 2.6, 10, true);
            }
        }

        /** The zombie nearest the horde's middle the gunner can see from up here, not right next to a player. */
        private Zombie pick(ServerLevel level, Vec3 muzzle) {
            Zombie best = null;
            double bestD = Double.MAX_VALUE;
            for (Zombie z : level.getEntitiesOfClass(Zombie.class, new AABB(target, target).inflate(50, 30, 50),
                    z -> z.isAlive() && ZombieTypes.isWalker(z))) {
                double d = z.position().distanceToSqr(target) * (0.6 + 0.8 * level.getRandom().nextDouble());
                if (d >= bestD || nextToPlayer(level, z.position(), 2.5)) {
                    continue;
                }
                Vec3 chest = z.position().add(0, 1, 0);
                if (level.clip(new ClipContext(muzzle, chest, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        CollisionContext.empty())).getType() != HitResult.Type.MISS) {
                    continue;
                }
                best = z;
                bestD = d;
            }
            return best;
        }
    }

    private static boolean nextToPlayer(ServerLevel level, Vec3 p, double reach) {
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(p) < reach * reach) {
                return true;
            }
        }
        return false;
    }

    /**
     * One shell from the gun at muzzle toward aim (scattered up to spread blocks round it): it bursts where it
     * strikes, killing the zombies within kill blocks and hurting a player within hurt blocks (damage at its
     * heart, less further off); the ground there throws up dust and a flash. A tracer if told.
     */
    private static void round(ServerLevel level, Vec3 muzzle, Vec3 aim, double spread, double kill, double hurt, float damage,
            boolean tracer) {
        RandomSource random = level.getRandom();
        Vec3 to = aim.add((random.nextDouble() * 2 - 1) * spread, (random.nextDouble() * 2 - 1) * spread * 0.3,
                (random.nextDouble() * 2 - 1) * spread);
        Vec3 dir = to.subtract(muzzle).normalize();
        Vec3 end = to.add(dir.scale(40));
        BlockHitResult hit = level.clip(new ClipContext(muzzle, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS || !level.isLoaded(hit.getBlockPos())) {
            return;
        }
        Vec3 p = hit.getLocation();
        if (tracer) {
            Tracer packet = new Tracer((float) muzzle.x, (float) muzzle.y, (float) muzzle.z, (float) p.x, (float) p.y, (float) p.z);
            for (ServerPlayer player : PlayerLookup.around(level, p, TELL)) {
                ServerPlayNetworking.send(player, packet);
            }
        }
        BlockState state = level.getBlockState(hit.getBlockPos());
        Vec3 up = p.add(normal(hit).scale(0.3));
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, up.x, up.y, up.z, 1, 0.2, 0.2, 0.2, 0);
        if (!state.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, false, up.x, up.y, up.z, 14, 0.4, 0.4, 0.4, 0.25);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, false, false, up.x, up.y, up.z, 2, 0.3, 0.3, 0.3, 0.03);
        burst(level, up, kill, hurt, damage, 0.55);
    }

    /**
     * Something going off at centre: every zombie within kill blocks is torn apart and dies (those further in the
     * open the worse), and a player within hurt blocks takes up to damage, less further off and a lot less
     * behind cover. Nothing else is touched.
     */
    private static void burst(ServerLevel level, Vec3 centre, double kill, double hurt, float damage, double tear) {
        DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE)
                .getOrThrow(ModDamageTypes.AIR_STRIKE), centre);
        double reach = Math.max(kill, hurt);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(centre, centre).inflate(reach),
                e -> e.isAlive() && !e.isSpectator())) {
            double d = e.position().add(0, e.getBbHeight() * 0.4, 0).distanceTo(centre);
            if (e instanceof Zombie zombie && ZombieTypes.isWalker(zombie)) {
                if (d < kill) {
                    double strength = 1 - d / kill;
                    Dismember.blast(zombie, centre, tear + (1 - tear) * strength);
                    zombie.hurtServer(level, source, zombie.getMaxHealth() * 50);
                }
            } else if (e instanceof ServerPlayer player && d < hurt) {
                boolean covered = level.clip(new ClipContext(centre, player.getEyePosition(), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, CollisionContext.empty())).getType() != HitResult.Type.MISS;
                float dealt = (float) (damage * Math.pow(1 - d / hurt, 1.2) * (covered ? 0.25 : 1));
                if (dealt > 0.5f) {
                    player.hurtServer(level, source, dealt);
                }
            }
        }
    }

    /** A bomb on its way down, server side: it falls and goes off where it first strikes anything solid. */
    private static final class Falling {
        Vec3 pos, vel;
        int age;

        Falling(Vec3 pos, Vec3 vel) {
            this.pos = pos;
            this.vel = vel;
        }

        /** One tick of its fall; true once it's gone off (or fallen out of the world, or taken too long). */
        boolean fall(ServerLevel level) {
            Vec3 next = pos.add(vel.scale(0.05));
            vel = vel.add(0, -G * 0.05, 0);
            if (++age > 400 || next.y < level.getMinY()) {
                return true;
            }
            if (!level.isLoaded(BlockPos.containing(next))) {
                pos = next;
                return false;
            }
            BlockHitResult hit = level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY,
                    CollisionContext.empty()));
            if (hit.getType() == HitResult.Type.MISS) {
                pos = next;
                level.sendParticles(ParticleTypes.SMOKE, false, false, pos.x, pos.y, pos.z, 1, 0.05, 0.05, 0.05, 0);
                return false;
            }
            explode(level, hit.getLocation().add(normal(hit).scale(0.5)));
            return true;
        }
    }

    /** A bomb going off: fire and smoke, the blast heard near and far, and everything caught in it (no blocks). */
    private static void explode(ServerLevel level, Vec3 at) {
        ZcConfig.World cfg = ZcConfig.get().world;
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, true, true, at.x, at.y + 1, at.z, 2, 1.5, 0.8, 1.5, 0);
        level.sendParticles(ParticleTypes.EXPLOSION, true, true, at.x, at.y + 1, at.z, 16, 3, 1.5, 3, 0);
        level.sendParticles(ParticleTypes.LAVA, true, true, at.x, at.y + 0.5, at.z, 24, 2, 0.6, 2, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true, true, at.x, at.y + 1, at.z, 14, 2.5, 1, 2.5, 0.03);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, true, true, at.x, at.y + 1, at.z, 30, 3, 1.2, 3, 0.08);
        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), true, true, at.x, at.y + 0.5, at.z, 60, 2.5, 1, 2.5, 0.6);
        }
        for (ServerPlayer player : PlayerLookup.around(level, at, 640)) {
            double d = player.getEyePosition().distanceTo(at);
            SoundEvent sound = d > 150 ? WorldEvents.EXPLOSION_FAR : WorldEvents.EXPLOSION;
            player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.NEUTRAL,
                    at.x, at.y, at.z, 2.5f, 0.9f + level.getRandom().nextFloat() * 0.2f, level.getRandom().nextLong()));
        }
        burst(level, at, cfg.strikeBombKill, cfg.strikeBombHurt, (float) cfg.strikeBombDamage, 0.3);
    }

    private static Vec3 normal(BlockHitResult hit) {
        return new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
    }

    /** A gun's whole burst, heard from where it opened up. */
    private static void sound(ServerLevel level, Vec3 at, SoundEvent sound, float volume) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, volume, 0.95f + level.getRandom().nextFloat() * 0.1f);
    }
}
