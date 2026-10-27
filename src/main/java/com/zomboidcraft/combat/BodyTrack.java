package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Each walker over the last second and a half, tick by tick: where it stood, which way its body faced and the pose
 * it was drawn in. A shot is checked against the zombie as the shooter saw it, and that is a few ticks old by the
 * time the shot reaches the server: the game sends a zombie's moves every few ticks and each client glides it there
 * over a few more, on top of the shooter's ping. So a running zombie is hit where it was drawn, not a step ahead.
 *
 * The pose is picked as the client's animator picks it ({@code ZBodyAnimator}): moving or not, after someone,
 * swinging, looking round, on the ground, crawling, on one leg. Server thread only.
 */
final class BodyTrack {
    static final int SIZE = 32;
    /** Ticks a pose takes to blend into the next on the client: until then a shot is checked against both. */
    private static final int BLEND = 4;
    private static final Map<Zombie, BodyTrack> TRACKS = new WeakHashMap<>();

    private final double[] xs = new double[SIZE], ys = new double[SIZE], zs = new double[SIZE];
    private final float[] yaws = new float[SIZE];
    private final byte[] poses = new byte[SIZE];
    private int count, newest;
    /** The walk animation's speed, worked out as the client does it: four times the blocks a tick, eased. */
    private float walk;
    private double lastX, lastZ;
    private float swing;
    private long attackStart = -1000;

    /** Where it was and how it stood some ticks back. */
    record Frame(double x, double y, double z, float yaw, int pose, int blendingFrom) {
    }

    /** Called each server tick: every walker's place and pose this tick. */
    static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            long now = level.getGameTime();
            for (Entity e : level.getAllEntities()) {
                if (e instanceof Zombie zombie && zombie.isAlive() && ZombieTypes.isWalker(zombie)) {
                    TRACKS.computeIfAbsent(zombie, z -> new BodyTrack()).record(zombie, now);
                }
            }
        }
    }

    /** How many ticks behind the server the shooter sees zombies: their ping, plus how long a zombie's moves take to show. */
    static int lag(Entity shooter) {
        int ping = shooter instanceof ServerPlayer player ? player.connection.latency() : 0;
        return Mth.clamp(Math.round(ping / 50f) + ZcConfig.get().limbs.lagTicks, 0, SIZE - BLEND - 2);
    }

    /** The zombie as it was {@code back} ticks ago (as far back as it's been seen), or as it is if it's not been tracked. */
    static Frame frame(Zombie zombie, int back) {
        BodyTrack t = TRACKS.get(zombie);
        if (t == null || t.count == 0) {
            return new Frame(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.yBodyRot,
                    BodyCaps.IDLE, -1);
        }
        int i = t.index(back), j = t.index(back + BLEND);
        int pose = t.poses[i], before = t.poses[j];
        return new Frame(t.xs[i], t.ys[i], t.zs[i], t.yaws[i], pose, before != pose ? before : -1);
    }

    /** The zombie as it is now. */
    static Frame now(Zombie zombie) {
        BodyTrack t = TRACKS.get(zombie);
        int pose = t == null || t.count == 0 ? BodyCaps.IDLE : t.poses[t.newest];
        return new Frame(zombie.getX(), zombie.getY(), zombie.getZ(), zombie.yBodyRot, pose, -1);
    }

    /** What it's doing now, as one of the ways it can die (Deaths' kinds). */
    static int deathKind(Zombie zombie) {
        int pose = now(zombie).pose();
        if (pose >= BodyCaps.CRAWL_REST && pose < BodyCaps.HOP) {
            return Deaths.CRAWL;
        }
        if (pose >= BodyCaps.ATTACK && pose < BodyCaps.DOWN) {
            return Deaths.LUNGE;
        }
        return switch (pose) {
            case BodyCaps.SHAMBLE -> Deaths.SHAMBLE;
            case BodyCaps.CHASE -> Deaths.RUN;
            case BodyCaps.DOWN -> Deaths.DOWN;
            case BodyCaps.HOP, BodyCaps.HOP_REACH, BodyCaps.STAND1, BodyCaps.STAND1_REACH -> Deaths.HOP;
            default -> Deaths.STILL;
        };
    }

    private int index(int back) {
        return Math.floorMod(newest - Math.min(back, count - 1), SIZE);
    }

    private void record(Zombie zombie, long now) {
        double dx = zombie.getX() - lastX, dz = zombie.getZ() - lastZ;
        float target = count == 0 ? 0f : (float) Math.min(Math.sqrt(dx * dx + dz * dz) * 4, 1);
        walk += (target - walk) * 0.4f;
        lastX = zombie.getX();
        lastZ = zombie.getZ();
        // a swing starts the attack; another one late in the bite goes straight back into the lunge
        float s = zombie.getSwingAnimation(1f);
        if (s > 0f && (swing == 0f || s < swing - 0.5f)) {
            long into = now - attackStart;
            if (into >= 40) {
                attackStart = now;
            } else if (into > 20) {
                attackStart = now - 9;
            }
        }
        swing = s;

        newest = (newest + 1) % SIZE;
        count = Math.min(count + 1, SIZE);
        xs[newest] = zombie.getX();
        ys[newest] = zombie.getY();
        zs[newest] = zombie.getZ();
        yaws[newest] = zombie.yBodyRot;
        poses[newest] = (byte) pose(zombie, (now - attackStart) / 20f);
    }

    /** The pose the client draws it in (as ZBodyAnimator picks the movement), as a BodyCaps table. */
    private int pose(Zombie zombie, float attack) {
        int cuts = Dismember.cuts(zombie), legs = Dismember.legsGone(cuts);
        boolean low = Downed.isLow(zombie);
        boolean attacking = attack >= 0f && attack < 2f;
        boolean moving = walk > (legs == 1 ? 0.02f : 0.08f);
        boolean looking = Math.abs(Mth.wrapDegrees(zombie.yHeadRot - zombie.yBodyRot)) > 10f;
        boolean aggressive = zombie.isAggressive();
        if (low && (Downed.isCrawler(zombie) || Downed.isLegless(zombie) || legs == 2)) {
            return attacking ? BodyCaps.CRAWL_GRAB + (attack < 1f ? 0 : 1)
                    : moving ? (aggressive ? BodyCaps.CRAWL_FAST : BodyCaps.CRAWL_SLOW)
                    : aggressive || looking ? BodyCaps.CRAWL_ALERT : BodyCaps.CRAWL_REST;
        }
        if (low) {
            return BodyCaps.DOWN;
        }
        if (legs == 1) {
            boolean reach = aggressive || attacking;
            return moving && !attacking ? (reach ? BodyCaps.HOP_REACH : BodyCaps.HOP)
                    : reach ? BodyCaps.STAND1_REACH : BodyCaps.STAND1;
        }
        if (attacking) {
            return BodyCaps.ATTACK + Math.min(3, (int) (attack * 2f));
        }
        if (moving) {
            return aggressive ? BodyCaps.CHASE : BodyCaps.SHAMBLE;
        }
        return aggressive || looking ? BodyCaps.ALERT : BodyCaps.IDLE;
    }

    private BodyTrack() {
    }
}
