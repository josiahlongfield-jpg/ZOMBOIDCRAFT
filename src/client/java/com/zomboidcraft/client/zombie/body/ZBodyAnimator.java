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
 * dead zombie falls the way the server says it went down (ZDeath). Beating on a door or a window
 * and climbing in through one (Acts) turn the body square to it and draw it where it reaches.
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
        /** Breaking in, as last told: which way it faces, how far into it, and how far off the zombie its body is drawn. */
        float actYaw, actTime, shiftX, shiftY, shiftZ;
        /** The way its body was drawn facing last frame. */
        float drawYaw;
        /** Dead: the pose it died in, the way it faced then, and how it goes down. */
        boolean dying;
        final ZMove.Pose deathFrom = new ZMove.Pose();
        float deathYaw;
        final ZDeath.Fall fall = new ZDeath.Fall();
        /** Follow-through (see flow): its springs (surge, sway, head, arms), where each is and how fast, and what drives them. */
        final float[] spring = new float[4], springV = new float[4];
        float speed = Float.NaN, accel, yawWas = Float.NaN, turning, yWas = Float.NaN, fallVy;
        boolean wasGround = true;
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
        /**
         * Breaking in (Acts): ZMove's BASH_DOOR, BASH_WIN or CLIMB, or 0; seconds into it (ZMove.Input.act); which
         * way it faces (degrees of yaw); and how far off the zombie its body is drawn (blocks, in the world: east, up,
         * south), square in front of what it's beating on or where the climb has it.
         */
        public int act;
        public float actTime, actYaw, shiftX, shiftY, shiftZ;
    }

    /** The way to draw its body facing (degrees of yaw): its own, turned square to what it's breaking in through. */
    public float drawYaw;

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
        if (f.act != 0) {
            tr.actYaw = f.actYaw;
            tr.actTime = f.actTime;
            tr.shiftX = f.shiftX;
            tr.shiftY = f.shiftY;
            tr.shiftZ = f.shiftZ;
        }
        int mode;
        if (f.act == ZMove.CLIMB || f.act != 0 && !f.low && !crawler) {
            // beating on it standing (a crawler claws at it as it is), or climbing through
            mode = f.act;
        } else if (crawler) {
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
        input.act = tr.actTime;
        move.pose(out, w, input);
        // square to what it's breaking in through, and drawn where it reaches it, as far as it's into it
        float acting = Math.min(1f, w[ZMove.BASH_DOOR] + w[ZMove.BASH_WIN] + w[ZMove.CLIMB]);
        drawYaw = f.bodyYaw;
        if (acting > 0.001f) {
            drawYaw += (((tr.actYaw - f.bodyYaw) % 360f + 540f) % 360f - 180f) * acting;
            float r = (float) Math.toRadians(drawYaw), c = (float) Math.cos(r), sn = (float) Math.sin(r);
            out.hx += acting * (tr.shiftX * c + tr.shiftZ * sn) * BLOCK;
            out.hy += acting * tr.shiftY * BLOCK;
            out.hz += acting * (-tr.shiftX * sn + tr.shiftZ * c) * BLOCK;
        }
        tr.drawYaw = drawYaw;
        if (w[ZMove.HOP] > 0.01f) {
            // the zombie glides along at an even pace; its foot stays planted between hops, so hold the body back while
            // it's down and let it catch up in the air
            out.hz += w[ZMove.HOP] * (ZMove.hopAdvance(tr.hop) - tr.hop) * tr.hopStride * BLOCK;
        }

        float tau = t - tr.eventStart;
        if (acting > 0.5f) {
            tr.event = 0;    // half through a window it carries on, whatever comes off
        } else if (tr.event == STUMBLE && tau < STUMBLE_TIME && legsGone == 1 && !f.low) {
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
        if (acting < 0.5f && crawl < 0.5f && !out.lying) {
            flow(zombie, tr, f.bodyYaw, partialTick, dt, out);
        }
        // shots jerk it where they land
        float yaw = (float) Math.toRadians(drawYaw);
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

    /** The springs' stiffness (Hz) and damping: surge and sway of the body, the head, the arms. */
    private static final float[] SPRING_HZ = {1.4f, 1.2f, 1.8f, 1.2f}, SPRING_DAMP = {0.3f, 0.35f, 0.45f, 0.22f};

    /**
     * Follow-through over its movement, the same as the companions': it pitches forward and rocks back when it pulls up,
     * leans into a turn with its head swinging round first, its arms carry on past a stop, and a drop sinks through it.
     * Springs, so each settles on its own; a few degrees, a little looser than a living body.
     */
    private static void flow(Zombie zombie, Track tr, float yaw, float partialTick, float dt, ZMove.Pose out) {
        if (dt <= 0f) {
            return;
        }
        double vx = zombie.getX() - zombie.xo, vz = zombie.getZ() - zombie.zo;
        float speed = (float) Math.sqrt(vx * vx + vz * vz) * 20f;
        if (Float.isNaN(tr.speed)) {
            tr.speed = speed;
        }
        float was = tr.speed;
        tr.speed += (speed - tr.speed) * Math.min(1f, dt * 8f);
        tr.accel += ((tr.speed - was) / dt - tr.accel) * Math.min(1f, dt * 10f);
        if (!Float.isNaN(tr.yawWas)) {
            float rate = (((yaw - tr.yawWas) % 360f + 540f) % 360f - 180f) / dt;
            tr.turning += (rate - tr.turning) * Math.min(1f, dt * 6f);
        }
        tr.yawWas = yaw;
        float y = (float) net.minecraft.util.Mth.lerp(partialTick, zombie.yo, zombie.getY());
        boolean ground = zombie.onGround();
        if (!Float.isNaN(tr.yWas)) {
            tr.fallVy = ground ? tr.fallVy * Math.max(0f, 1f - dt * 10f) : Math.min(tr.fallVy, (y - tr.yWas) / dt);
            if (ground && !tr.wasGround && tr.fallVy < -3f) {
                float hard = Math.min(1f, (-tr.fallVy - 3f) / 9f) + 0.35f;
                tr.springV[0] += 1.2f * hard;
                tr.springV[3] -= 1.0f * hard;
            }
        }
        tr.yWas = y;
        tr.wasGround = ground;

        // targets in radians: a stop (accel below zero) tips it forward and swings the arms on forward
        float[] target = {
                Math.clamp(-tr.accel * 0.02f, -0.14f, 0.14f),
                Math.clamp(tr.turning * 0.0006f, -0.12f, 0.12f),
                Math.clamp(-tr.turning * 0.0008f, -0.2f, 0.2f),
                Math.clamp(tr.accel * 0.035f, -0.25f, 0.25f)};
        int n = Math.max(1, (int) Math.ceil(dt / 0.01f));
        float h = dt / n;
        for (int s = 0; s < 4; s++) {
            float w = (float) (2 * Math.PI * SPRING_HZ[s]), k = w * w, c = 2 * SPRING_DAMP[s] * w;
            for (int i = 0; i < n; i++) {
                tr.springV[s] += (k * (target[s] - tr.spring[s]) - c * tr.springV[s]) * h;
                tr.spring[s] += tr.springV[s] * h;
            }
        }
        float bow = tr.spring[0], lean = tr.spring[1], lead = tr.spring[2], arms = tr.spring[3];
        // spine x above zero leans forward, z above zero toward its right; head y below zero turns it right;
        // a shoulder x below zero swings the arm forward
        out.rot[ZMove.SPINE * 3] += bow;
        out.rot[ZMove.SPINE * 3 + 2] += lean;
        out.rot[ZMove.HEAD * 3] -= bow * 0.5f;
        out.rot[ZMove.HEAD * 3 + 1] += lead;
        out.rot[ZMove.SH_R * 3] += arms;
        out.rot[ZMove.SH_L * 3] += arms;
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
            tr.deathYaw = tr.started ? tr.drawYaw : f.bodyYaw;
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
