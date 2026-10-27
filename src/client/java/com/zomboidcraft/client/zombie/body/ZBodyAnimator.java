package com.zomboidcraft.client.zombie.body;

import com.zomboidcraft.combat.Deaths;
import com.zomboidcraft.combat.Dismember;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * Picks what each zombie is doing from what the game knows about it (moving, after someone,
 * swinging, on the ground, crawling) and blends from one movement into the next, so a zombie
 * turns, lurches into a chase or topples over instead of snapping between poses.
 *
 * It also watches for limbs coming off: losing a leg plays the stumble into a hop, losing the
 * last one plays the fall into a crawl. Shots make the body jerk where they land (ZHit), and a
 * dead zombie falls the way the server says it went down (ZDeath).
 */
public final class ZBodyAnimator {
    /** How fast one movement hands over to the next (weight per second). */
    private static final float BLEND = 6f;
    /** Seconds a swing plays for: wind up, lunge, grab, bite, back off (as in the preview). */
    private static final float ATTACK_TIME = 2f;
    /** How long the stumble and the fall play for, and how long a fallen zombie takes to settle into its crawl. */
    private static final float STUMBLE_TIME = 1.7f, FALL_TIME = 1f, SETTLE_TIME = 1.6f;
    private static final int STUMBLE = 1, FALL = 2;
    /**
     * Walk speed over which a zombie on one leg hops instead of standing: it goes at about half speed, under a
     * walker's threshold.
     */
    private static final float HOP_MOVING = 0.02f;
    /** Preview units in a block, for moving the body along the ground. */
    private static final float BLOCK = 16f / ZBodyLayer.K;

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
        /** The pose drawn last frame, and the one it was in when a leg came off. */
        final ZMove.Pose last = new ZMove.Pose();
        final ZMove.Pose from = new ZMove.Pose();
        /** Limbs gone as of last frame (-1 before the first). */
        int cuts = -1;
        int event;
        float eventStart;
        /** On one leg: how far through the hop (0 to 1), and how much ground a hop covers, in blocks. */
        float hop;
        float hopStride = 0.4f;
        /** Shots that landed on it lately, the newest last. */
        final List<Hit> hits = new ArrayList<>();
        /** Dead: the pose it died in, the way it faced then, and how it goes down. */
        boolean dying;
        final ZMove.Pose deathFrom = new ZMove.Pose();
        float deathYaw;
        final ZDeath.Fall fall = new ZDeath.Fall();
    }

    /** Shots landing on one zombie (Deaths.Hit): the parts struck, the shot's yaw, how hard, and when (nanoseconds). */
    private record Hit(int parts, float yaw, int force, long at) {
    }

    /** Shots told by the server, waiting for their zombie's next frame. Render thread only. */
    private static final Int2ObjectOpenHashMap<List<Hit>> PENDING = new Int2ObjectOpenHashMap<>();

    /** Hears the server's shots on living walkers (Deaths.Hit). */
    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Deaths.Hit.TYPE, (payload, context) ->
                context.client().execute(() -> hit(payload.entity(), payload.parts(), payload.heading(), payload.force())));
    }

    /** Shots struck zombie {@code entity} (from the server, on the render thread). */
    public static void hit(int entity, int parts, int heading, int force) {
        List<Hit> list = PENDING.computeIfAbsent(entity, id -> new ArrayList<>());
        if (list.size() < 8) {
            list.add(new Hit(parts, heading / 256f * (float) Math.PI * 2f, force, System.nanoTime()));
        }
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
        /** Limbs shot off (Dismember's cut bits). */
        public int cuts;
        /** A limb lost before it rose (ZombieLook.missingLimb). */
        public int missingLimb;
        /** Which way its body faces (degrees of yaw). */
        public float bodyYaw;
        /** Ticks since it died (with the frame's part of a tick), 0 alive; and how it died (Deaths), 0 if not told yet. */
        public float deathTime;
        public long death;
        /** Its body, for laying it on the ground when it's dead. */
        public ZBodyMesh mesh;
    }

    /** Dead: the way its body faces while it falls (degrees of yaw), for the renderer. */
    public float dyingYaw;

    /** The pose a zombie was in the frame before it last lost a limb: the limb flies off frozen in it. */
    public final ZMove.Pose before = new ZMove.Pose();

    private final Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
    private final ZMove move = new ZMove();
    private final ZDeath death = new ZDeath();
    private final ZMove.Input input = new ZMove.Input();
    private final float[] target = new float[ZMove.MODES];
    private long frames;

    /**
     * Works out this frame's pose for one zombie into {@code out}.
     *
     * @return the limbs that came off since last frame (cut bits), with the pose they came off in left in
     *     {@link #before}
     */
    public int animate(Zombie zombie, Facts f, float partialTick, ZMove.Pose out) {
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

        // limbs coming off: a leg lost sets off the stumble, the last leg the fall
        int newly = 0;
        int legsGone = Dismember.legsGone(f.cuts);
        boolean snap = false;
        if (tr.cuts < 0) {
            tr.cuts = f.cuts;
        } else if (f.cuts != tr.cuts) {
            newly = f.cuts & ~tr.cuts;
            int legsBefore = Dismember.legsGone(tr.cuts);
            before.copy(tr.last);
            if (legsGone == 2 && legsBefore < 2) {
                tr.event = FALL;
            } else if (legsGone == 1 && legsBefore == 0) {
                tr.event = STUMBLE;
            } else {
                tr.event = 0;
            }
            if (tr.event != 0) {
                tr.from.copy(tr.last);
                tr.eventStart = t;
                snap = true;
            }
            tr.cuts = f.cuts;
        }

        List<Hit> fresh = PENDING.remove(zombie.getId());
        if (fresh != null) {
            tr.hits.addAll(fresh);
        }
        tr.hits.removeIf(h -> now - h.at > 600_000_000L);
        while (tr.hits.size() > 4) {
            tr.hits.remove(0);
        }

        if (f.deathTime > 0f) {
            dead(zombie, tr, f, out, legsGone);
            tr.last.copy(out);
            return newly;
        }
        tr.dying = false;

        boolean moving = f.walkSpeed > (legsGone == 1 ? HOP_MOVING : 0.08f);
        boolean looking = Math.abs(f.headYaw) > 10f;
        boolean noLegs = (f.legless || legsGone == 2) && (f.low || tr.event == FALL);
        boolean crawler = (f.crawler || noLegs) && (f.low || tr.event == FALL);
        int mode;
        if (crawler) {
            mode = attacking ? ZMove.CRAWL_GRAB : moving ? (f.aggressive ? ZMove.CRAWL_FAST : ZMove.CRAWL_SLOW)
                    : f.aggressive || looking ? ZMove.CRAWL_ALERT : ZMove.CRAWL_REST;
        } else if (f.low) {
            mode = ZMove.DOWN;
        } else if (legsGone == 1) {
            mode = moving && !attacking ? ZMove.HOP : ZMove.STAND1;
        } else if (attacking) {
            mode = ZMove.ATTACK;
        } else if (moving) {
            mode = f.aggressive ? ZMove.CHASE : ZMove.SHAMBLE;
        } else {
            mode = f.aggressive || looking ? ZMove.ALERT : ZMove.IDLE;
        }
        java.util.Arrays.fill(target, 0f);
        target[mode] = 1f;
        float k = tr.started && !snap ? Math.min(1f, dt * BLEND) : 1f;
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
        if (legsGone == 1) {
            // a hop covers the ground it would walk in about 0.78 s, a slow zombie's at least 0.3 of a block (the
            // walk animation counts about four to a block, and moves at four times the blocks a tick)
            tr.hopStride = Math.clamp(f.walkSpeed * 3.9f, 0.3f, 0.9f);
            tr.hop = (tr.hop + step / 4f / tr.hopStride) % 1f;
        }

        input.t = t;
        input.gait = tr.gait;
        input.phase = (f.variant & 0xFFFF) / 65535f * (float) Math.PI * 2f;
        input.look = Math.clamp(-(float) Math.toRadians(f.headYaw) / 1.1f, -1.5f, 1.5f);
        input.attack = Math.max(0f, attack);
        input.legless = noLegs;
        int stumps = ZBodyLayer.stumps(f.cuts, f.missingLimb);
        input.armless = (stumps & Dismember.RIGHT_ARM) != 0 && (stumps & Dismember.LEFT_ARM) != 0;
        input.goneRight = (f.cuts & Dismember.RIGHT_LEG) != 0;
        input.reach = f.aggressive || attacking;
        input.hop = tr.hop;
        move.pose(out, w, input);
        if (w[ZMove.HOP] > 0.01f) {
            // the zombie glides along at an even pace; its foot stays planted between hops, so hold the body back while
            // it's down and let it catch up in the air
            out.hz += w[ZMove.HOP] * (ZMove.hopAdvance(tr.hop) - tr.hop) * tr.hopStride * BLOCK;
        }

        float tau = t - tr.eventStart;
        if (tr.event == STUMBLE && tau < STUMBLE_TIME && legsGone == 1 && !f.low) {
            move.stumble(out, tr.from, tau, input.goneRight);
        } else if (tr.event == FALL && tau < SETTLE_TIME && legsGone == 2) {
            // it falls forward from where it stood, then drags itself back into a crawler's usual place
            float off = tr.from.hz + 7f;
            out.hz += tau < 0.6f ? off : off * (1 - smooth((tau - 0.6f) / (SETTLE_TIME - 0.6f)));
            if (tau < FALL_TIME) {
                move.fall(out, tr.from, tau);
            }
        } else {
            tr.event = 0;
        }
        // shots jerk it where they land
        float yaw = (float) Math.toRadians(f.bodyYaw);
        for (Hit h : tr.hits) {
            float age = (now - h.at) / 1e9f, scale = 1f / (float) Math.sqrt(Integer.bitCount(h.parts));
            for (int part = 0; part < 12; part++) {
                if ((h.parts >> part & 1) != 0) {
                    ZHit.apply(out, part, yaw - h.yaw, h.force, age, f.variant + part, out.lying, scale);
                }
            }
        }
        tr.last.copy(out);

        if (++frames % 1200 == 0) {
            tracks.values().removeIf(old -> now - old.seen > 10_000_000_000L);
            // shots on zombies that never came on screen
            PENDING.values().removeIf(list -> now - list.get(list.size() - 1).at > 1_000_000_000L);
        }
        return newly;
    }

    /**
     * Dead: it falls from the pose it died in, the way the server says it went down, facing the way it faced then; until
     * the server's word comes, it starts to drop where it stood.
     */
    private void dead(Zombie zombie, Track tr, Facts f, ZMove.Pose out, int legsGone) {
        float tau = Math.max(0f, f.deathTime - 1f) / 20f;
        if (!tr.dying) {
            tr.dying = true;
            tr.deathFrom.copy(tr.last);
            tr.deathYaw = f.bodyYaw;
        }
        long d = f.death;
        int stumps = ZBodyLayer.stumps(f.cuts, f.missingLimb);
        boolean goneRight = (f.cuts & Dismember.RIGHT_LEG) != 0;
        if (d != 0L) {
            tr.fall.set(Deaths.kind(d), Deaths.heading(d), Deaths.force(d), Deaths.part(d), Deaths.seed(d), goneRight,
                    f.legless, stumps);
        } else {
            int kind = f.low ? (f.crawler || f.legless ? ZDeath.CRAWL : ZDeath.DOWN) : legsGone == 1 ? ZDeath.HOP : ZDeath.STILL;
            tr.fall.set(kind, 0f, -1, -1, f.variant & 0xFF, goneRight, f.legless, stumps);
        }
        if (f.mesh == null) {
            out.copy(tr.deathFrom);
            dyingYaw = tr.deathYaw;
            return;
        }
        death.pose(out, tr.deathFrom, tr.fall, f.mesh, tau);
        float told = d != 0L ? Deaths.yaw(d) : tr.deathYaw;
        float turn = ((told - tr.deathYaw) % 360f + 540f) % 360f - 180f;
        dyingYaw = tr.deathYaw + turn * smooth(tau / 0.15f);
    }

    private static float smooth(float x) {
        x = Math.clamp(x, 0f, 1f);
        return x * x * (3 - 2 * x);
    }
}
