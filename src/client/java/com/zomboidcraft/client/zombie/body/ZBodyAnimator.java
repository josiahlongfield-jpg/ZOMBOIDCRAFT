package com.zomboidcraft.client.zombie.body;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Picks what each zombie is doing from what the game knows about it (moving, after someone,
 * swinging, on the ground, crawling) and blends from one movement into the next, so a zombie
 * turns, lurches into a chase or topples over instead of snapping between poses.
 */
public final class ZBodyAnimator {
    /** How fast one movement hands over to the next (weight per second). */
    private static final float BLEND = 6f;
    /** Seconds a swing plays for: wind up, lunge, grab, bite, back off (as in the preview). */
    private static final float ATTACK_TIME = 2f;

    /** What one zombie was doing last frame. */
    private static final class Track {
        final float[] weights = new float[ZMove.MODES];
        float time;
        float walked;
        float gait;
        float attackStart = -100f;
        float swing;
        long seen;
        boolean started;
    }

    /** What the renderer reads off a zombie each frame. */
    public static final class Facts {
        public float age;
        public float walkPos;
        public float walkSpeed;
        public float headYaw;
        public int variant;
        public boolean aggressive;
        public boolean low;
        public boolean crawler;
        public boolean legless;
    }

    private final Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
    private final ZMove move = new ZMove();
    private final ZMove.Input input = new ZMove.Input();
    private final float[] target = new float[ZMove.MODES];
    private long frames;

    /** Works out this frame's pose for one zombie into {@code out}. */
    public void animate(Zombie zombie, Facts f, float partialTick, ZMove.Pose out) {
        long now = System.nanoTime();
        Track tr = tracks.computeIfAbsent(zombie.getId(), id -> new Track());
        float t = f.age / 20f;
        float dt = tr.started ? Math.clamp(t - tr.time, 0f, 0.25f) : 0f;
        tr.time = t;
        tr.seen = now;

        // a swing starts the attack; another one late in the bite goes straight back into the lunge
        float swing = zombie.getSwingAnimation(partialTick);
        if (tr.started && swing > 0f && (tr.swing == 0f || swing < tr.swing - 0.5f)) {
            float into = t - tr.attackStart;
            if (into >= ATTACK_TIME) {
                tr.attackStart = t;
            } else if (into > 1f) {
                tr.attackStart = t - 0.45f;
            }
        }
        tr.swing = swing;
        float attack = t - tr.attackStart;
        boolean attacking = attack < ATTACK_TIME;

        boolean moving = f.walkSpeed > 0.08f;
        boolean looking = Math.abs(f.headYaw) > 10f;
        boolean legless = f.legless && f.low;
        boolean crawler = (f.crawler || legless) && f.low;
        int mode;
        if (crawler) {
            mode = attacking ? ZMove.CRAWL_GRAB : moving ? (f.aggressive ? ZMove.CRAWL_FAST : ZMove.CRAWL_SLOW)
                    : f.aggressive || looking ? ZMove.CRAWL_ALERT : ZMove.CRAWL_REST;
        } else if (f.low) {
            mode = ZMove.DOWN;
        } else if (attacking) {
            mode = ZMove.ATTACK;
        } else if (moving) {
            mode = f.aggressive ? ZMove.CHASE : ZMove.SHAMBLE;
        } else {
            mode = f.aggressive || looking ? ZMove.ALERT : ZMove.IDLE;
        }
        java.util.Arrays.fill(target, 0f);
        target[mode] = 1f;
        float k = tr.started ? Math.min(1f, dt * BLEND) : 1f;
        float[] w = tr.weights;
        for (int m = 0; m < ZMove.MODES; m++) {
            w[m] += (target[m] - w[m]) * k;
        }

        // the gait follows the ground covered, so the feet keep pace: long chase strides, short shuffles,
        // and a crawler's arms pull a little faster than its slow drag along the ground
        float step = tr.started ? Math.clamp(f.walkPos - tr.walked, 0f, 2f) : 0f;
        tr.walked = f.walkPos;
        float crawl = w[ZMove.CRAWL_SLOW] + w[ZMove.CRAWL_FAST] + w[ZMove.CRAWL_REST] + w[ZMove.CRAWL_ALERT] + w[ZMove.CRAWL_GRAB];
        float rate = 1.2f + (0.8f - 1.2f) * w[ZMove.CHASE] + (1.5f - 1.2f) * crawl;
        tr.gait += step * rate;
        tr.started = true;

        input.t = t;
        input.gait = tr.gait;
        input.phase = (f.variant & 0xFFFF) / 65535f * (float) Math.PI * 2f;
        input.look = Math.clamp(-(float) Math.toRadians(f.headYaw) / 1.1f, -1.5f, 1.5f);
        input.attack = Math.max(0f, attack);
        input.legless = legless;
        move.pose(out, w, input);

        if (++frames % 1200 == 0) {
            tracks.values().removeIf(old -> now - old.seen > 10_000_000_000L);
        }
    }
}
