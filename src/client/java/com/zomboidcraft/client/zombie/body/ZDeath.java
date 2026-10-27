package com.zomboidcraft.client.zombie.body;

import org.joml.Matrix4f;

/**
 * Dying: the zombie drops as a dead weight out of whatever it was doing, the way the killing shot and its own
 * momentum carry it, and ends up lying where it fell (deaths.js in the preview).
 *
 * <p>Everything comes from what the server tells every client about the death (combat.Deaths): what it was doing
 * (standing, shambling, running, lunging, hopping on one leg, crawling, or knocked flat), which way the shot went
 * through it, how hard, where it struck, and a seed for the little differences. So everyone sees the same fall, and
 * the body it leaves (zombie.Corpse) lies in exactly the pose the fall ends in.
 *
 * <p>On its feet the knees go first and it starts to tip, slowly and then faster, toward where the shot and its
 * momentum take it: forward for a runner, which slides on along the ground; back for one shot hard from the front;
 * toward the missing side for a hopper. On the way down it turns to land on its face or its back, whichever is
 * nearer. Nothing braces: the head and arms trail behind the body, hit the ground just after it and flop still. On
 * the ground already, a crawler or a zombie knocked flat just goes slack where it lies, jolted along by the shot.
 *
 * <p>The resting pose is laid on the ground with the body's own mesh, so every outfit lies flat on it, limbs and
 * head down on the floor and nothing through it.
 */
public final class ZDeath {
    /** What it was doing when it died. */
    public static final int STILL = 0, SHAMBLE = 1, RUN = 2, LUNGE = 3, HOP = 4, CRAWL = 5, DOWN = 6;
    /** Seconds from the death until the body lies exactly as it will stay. */
    public static final float SETTLED = 1.6f;

    private static final float PI = (float) Math.PI;
    /** How high the ground is in the preview's space (the soles of a zombie standing). */
    static final float GROUND = 0.4f;
    /** How high off the ground the body rests (it lies on its clothes), and a limb's lowest point. */
    private static final float REST = 0.15f, LIMB_REST = 0.25f;

    /** One death: what the server said about it, and the fall worked out from that. */
    public static final class Fall {
        public int kind;
        /** Which way the killing shot went through it, from its front round toward its left (radians). */
        public float heading;
        /** 0 handgun, 1 rifle, 2 hunting rifle, 3 buckshot; -1 for a death with no shot or blow to push it. */
        public int force = -1;
        /** Where the shot struck, as ZHit's parts (-1 nowhere). */
        public int part = -1;
        public int seed;
        /** On one leg: the right one is the one gone. */
        public boolean goneRight;
        /** Lost its legs: drawn with stumps for thighs. */
        public boolean legless;
        /** Limbs it hasn't got (cut bits, the head as HEAD_GONE), so the drape leaves them out. */
        public int stumps;

        boolean planned;
        ZBodyMesh mesh;
        /** Which way it goes over (radians from its front, toward its left), face down or not. */
        float dir;
        boolean prone;
        /** When it hits the ground, how long the knees give before it tips, how fast it is already tipping. */
        float impact, delay, start;
        /** How much of its height the knees give before it tips, how far the hips end up, and the slide after. */
        float give, slide;
        final ZMove.Pose rest = new ZMove.Pose(), loose = new ZMove.Pose();

        /** Sets what the server said; the fall is worked out again when next drawn. */
        public Fall set(int kind, float heading, int force, int part, int seed, boolean goneRight, boolean legless,
                        int stumps) {
            if (planned && (this.kind != kind || this.heading != heading || this.force != force || this.part != part
                    || this.seed != seed || this.goneRight != goneRight || this.legless != legless
                    || this.stumps != stumps)) {
                planned = false;
            }
            this.kind = kind;
            this.heading = heading;
            this.force = force;
            this.part = part;
            this.seed = seed;
            this.goneRight = goneRight;
            this.legless = legless;
            this.stumps = stumps;
            return this;
        }

        /** Whether it was on the ground when it died. */
        public boolean onGround() {
            return kind == CRAWL || kind == DOWN;
        }
    }

    private final ZMove.Pose tmp = new ZMove.Pose();
    private final Matrix4f[] world = new Matrix4f[ZMove.BONES];

    public ZDeath() {
        for (int i = 0; i < world.length; i++) {
            world[i] = new Matrix4f();
        }
    }

    /** The pose it ends in, lying where it fell. */
    public void rest(ZMove.Pose out, Fall f, ZBodyMesh mesh) {
        plan(f, mesh);
        out.copy(f.rest);
    }

    /**
     * The pose tau seconds after it died, from the pose it was in at that moment.
     */
    public void pose(ZMove.Pose out, ZMove.Pose from, Fall f, ZBodyMesh mesh, float tau) {
        plan(f, mesh);
        if (tau >= SETTLED) {
            out.copy(f.rest);
            return;
        }
        if (f.onGround()) {
            slump(out, from, f, tau);
        } else {
            topple(out, from, f, tau);
        }
        ZHit.apply(out, f.part, f.heading, Math.max(0, f.force), tau, f.seed, f.onGround());
        // the last moment eases onto exactly the pose the body keeps
        float s = ZMove.seg(tau, SETTLED - 0.3f, SETTLED);
        if (s > 0f) {
            ZMove.mix(out, out, f.rest, s);
            out.lying = f.rest.lying;
        }
    }

    // ---- going over from its feet

    private void topple(ZMove.Pose out, ZMove.Pose from, Fall f, float tau) {
        ZMove.Pose rest = f.rest;
        float T = f.impact;
        float tilt = PI / 2 * tipped(f, tau);
        float knees = f.give * ZMove.seg(tau, 0f, f.delay + 0.18f);
        // the knees give, then it swings down round its feet: slow at first, then fast
        float drop = 1f - (1f - knees) * ZMove.cos(tilt);
        float along = ZMove.sin(tilt);
        float slide = f.slide * ZMove.seg(tau, 0f, T + 0.35f);
        float sx = ZMove.sin(f.dir), sz = ZMove.cos(f.dir);
        float rx = rest.hx - sx * f.slide, rz = rest.hz - sz * f.slide;
        out.hx = from.hx + (rx - from.hx) * along + sx * slide;
        out.hz = from.hz + (rz - from.hz) * along + sz * slide;
        out.hy = from.hy + (rest.hy - from.hy) * drop + bounce(tau - T, 0.9f + 0.25f * Math.max(0, f.force));
        out.lying = false;
        out.fallDir = f.dir;
        out.tilt = tilt;
        // it turns on the way down to land on its face or its back
        float turn = ZMove.seg(tilt / (PI / 2), 0.1f, 0.95f);
        int h = ZMove.HIPS * 3;
        for (int c = 0; c < 3; c++) {
            out.rot[h + c] = from.rot[h + c] + (rest.rot[h + c] - from.rot[h + c]) * turn;
        }
        limbs(out, from, f.loose, rest, tau, T, f);
        // the head and arms hang back as the body goes over, and catch up as it nears the ground (where they'd go
        // through it)
        float near = 1f - ZMove.seg(tipped(f, tau), 0.5f, 0.85f);
        float lag = PI / 2 * (tipped(f, tau - 0.07f) - tipped(f, tau)) * near;
        float lagArms = PI / 2 * (tipped(f, tau - 0.12f) - tipped(f, tau)) * near * 0.7f;
        float rel = f.dir - out.rot[h + 1];
        float cx = ZMove.cos(rel), cz = -ZMove.sin(rel);
        add(out, ZMove.NECK, lag * cx * 0.6f, 0f, lag * cz * 0.6f);
        add(out, ZMove.HEAD, lag * cx, 0f, lag * cz);
        for (int side = 0; side < 2; side++) {
            add(out, side == 0 ? ZMove.SH_R : ZMove.SH_L, lagArms * cx * 1.1f, 0f, lagArms * cz * 0.9f);
        }
    }

    /** How far over it has gone (0 upright to 1 down), tau seconds in. */
    private static float tipped(Fall f, float tau) {
        if (tau <= f.delay) {
            return 0f;
        }
        float u = Math.min(1f, (tau - f.delay) / (f.impact - f.delay));
        return f.start * u + (1f - f.start) * u * u;
    }

    /** The thump on landing: up a little and down again, once more smaller (preview units). */
    private static float bounce(float t, float size) {
        if (t <= 0f) {
            return 0f;
        }
        if (t < 0.16f) {
            return size * ZMove.sin(PI * t / 0.16f);
        }
        if (t < 0.27f) {
            return size * 0.3f * ZMove.sin(PI * (t - 0.16f) / 0.11f);
        }
        return 0f;
    }

    // ---- going slack where it lies

    private void slump(ZMove.Pose out, ZMove.Pose from, Fall f, float tau) {
        ZMove.Pose rest = f.rest;
        float s = ZMove.seg(tau, 0f, 0.45f);
        float jolt = ZMove.seg(tau, 0f, 0.18f);
        out.lying = true;
        out.tilt = 0f;
        int h = ZMove.HIPS * 3;
        for (int c = 0; c < 3; c++) {
            out.rot[h + c] = from.rot[h + c] + (rest.rot[h + c] - from.rot[h + c]) * s;
        }
        // shoved along the ground by the shot at once, then the chest drops
        out.hx = from.hx + (rest.hx - from.hx) * jolt;
        out.hz = from.hz + (rest.hz - from.hz) * jolt;
        out.hy = from.hy + (rest.hy - from.hy) * s + bounce(tau - 0.3f, 0.35f);
        limbs(out, from, f.loose, rest, tau, 0.3f, f);
    }

    // ---- the limbs: each part swings on toward where it's going, overshoots a little and settles, the loose ones
    // looser

    /** Spring stiffness (radians a second) and damping for spine, neck and head, arms, legs; and how early each
     * starts for where it lies, before the body lands. */
    private static final float[][] SPRINGS = {{11f, 0.7f, 0.08f}, {13f, 0.45f, 0f}, {10f, 0.6f, 0.12f},
            {11f, 0.65f, 0.2f}};

    private static int group(int bone) {
        return switch (bone) {
            case ZMove.SPINE -> 0;
            case ZMove.NECK, ZMove.HEAD -> 1;
            case ZMove.SH_R, ZMove.EL_R, ZMove.WR_R, ZMove.SH_L, ZMove.EL_L, ZMove.WR_L -> 2;
            default -> 3;
        };
    }

    private static void limbs(ZMove.Pose out, ZMove.Pose from, ZMove.Pose loose, ZMove.Pose rest, float tau,
                              float land, Fall f) {
        for (int b = 1; b < ZMove.BONES; b++) {
            float[] g = SPRINGS[group(b)];
            float a = step(tau, g[0], g[1]), c = step(tau - (land - g[2]), g[0], g[1]);
            for (int k = 0; k < 3; k++) {
                int i = b * 3 + k;
                out.rot[i] = from.rot[i] + (loose.rot[i] - from.rot[i]) * a + (rest.rot[i] - loose.rot[i]) * c;
            }
        }
    }

    /** How far a spring has gone toward a new place t seconds after it was given it (overshooting past 1). */
    private static float step(float t, float w, float zeta) {
        if (t <= 0f) {
            return 0f;
        }
        float wd = w * (float) Math.sqrt(1 - zeta * zeta);
        return 1f - (float) Math.exp(-zeta * w * t) * (ZMove.cos(wd * t) + zeta * w / wd * ZMove.sin(wd * t));
    }

    private static void add(ZMove.Pose p, int bone, float x, float y, float z) {
        p.rot[bone * 3] += x;
        p.rot[bone * 3 + 1] += y;
        p.rot[bone * 3 + 2] += z;
    }

    // ---- the plan: which way it goes, how, and how it ends up

    /** A number from 0 to 1, the same for the same seed and key on every client. */
    static float rnd(int seed, int key) {
        int h = seed * 0x9E3779B9 + key * 0x85EBCA6B;
        h ^= h >>> 16;
        h *= 0x7FEB352D;
        h ^= h >>> 15;
        h *= 0x846CA68B;
        h ^= h >>> 16;
        return (h >>> 8) / 16777216f;
    }

    private void plan(Fall f, ZBodyMesh mesh) {
        if (f.planned && f.mesh == mesh) {
            return;
        }
        f.planned = true;
        f.mesh = mesh;
        float push = f.force < 0 ? 0f : 0.45f + 0.35f * f.force;
        float vx = push * ZMove.sin(f.heading), vz = push * ZMove.cos(f.heading);
        if (!f.onGround()) {
            vz += switch (f.kind) {
                case SHAMBLE -> 0.35f;
                case RUN -> 1.3f;
                case LUNGE -> 0.9f;
                case HOP -> 0.2f;
                default -> 0f;
            };
            if (f.kind == HOP) {
                vx += f.goneRight ? -0.6f : 0.6f;
            }
            vx += (rnd(f.seed, 1) - 0.5f) * 0.3f;
            vz += (rnd(f.seed, 2) - 0.5f) * 0.2f;
            if (vx * vx + vz * vz < 0.05f) {
                // nothing pushing it: it drops and goes over any way
                float a = rnd(f.seed, 3) * 2 * PI;
                vx = 0.3f * ZMove.sin(a);
                vz = 0.3f * ZMove.cos(a);
            }
        }
        f.dir = (float) Math.atan2(vx, vz);
        float force = Math.max(0, f.force);
        switch (f.kind) {
            case RUN -> timing(f, 0.6f, 0f, 0.4f, 0.3f, 5f);
            case LUNGE -> timing(f, 0.64f, 0.04f, 0.32f, 0.3f, 3f);
            case HOP -> timing(f, 0.66f, 0.08f, 0.2f, 0.22f, 0f);
            case SHAMBLE -> timing(f, 0.8f, 0.16f, 0.12f, 0.28f, 1f);
            default -> timing(f, 0.85f, 0.2f, 0.05f, 0.32f, 0f);
        }
        if (!f.onGround()) {
            f.impact -= 0.035f * force;
            f.start = Math.min(0.7f, f.start + 0.06f * force);
            f.slide += force;
        }
        if (f.onGround()) {
            slack(f);
        } else {
            lieDown(f, push);
        }
        drape(f);
        loose(f);
    }

    private static void timing(Fall f, float impact, float delay, float start, float give, float slide) {
        f.impact = impact;
        f.delay = delay;
        f.start = start;
        f.give = give;
        f.slide = slide;
    }

    /** Where a body that went over from its feet ends up: flat on its face or its back, limbs flung. */
    private void lieDown(Fall f, float push) {
        ZMove.Pose p = f.rest;
        p.clear();
        // face down if it went over forward-ish, on its back if backward; sideways it rolls whichever way is nearer
        f.prone = Math.abs(f.dir) < PI / 2 + (rnd(f.seed, 4) - 0.5f) * 0.5f;
        float spin = f.prone ? 0f : f.dir > 0f ? -PI : PI;
        float roll = (rnd(f.seed, 5) - 0.5f) * 0.5f;
        p.fallDir = f.dir;
        p.tilt = PI / 2;
        p.set(ZMove.HIPS, 0f, f.dir + spin + roll, 0f);
        float reach = switch (f.kind) {
            case RUN -> 13f;
            case LUNGE -> 12f;
            case HOP -> 10f;
            case SHAMBLE -> 9.5f;
            default -> 8.5f;
        } + 0.6f * Math.max(0, f.force) + f.slide;
        p.hx = ZMove.sin(f.dir) * reach;
        p.hz = ZMove.cos(f.dir) * reach;
        p.hy = ZMove.PELVIS_D / 2;
        float bend = (rnd(f.seed, 6) - 0.5f) * 0.3f;
        float turn = rnd(f.seed, 7) < 0.5f ? -1f : 1f;
        p.set(ZMove.SPINE, -0.05f, 0f, bend);
        int arms = (int) (rnd(f.seed, 8) * 4), legs = (int) (rnd(f.seed, 9) * 3);
        // arms flung up lie that way by swinging out to the side (along the floor), not forward (through it)
        if (f.prone) {
            // the cheek on the floor
            p.set(ZMove.NECK, 0f, turn * 0.45f, 0f);
            p.set(ZMove.HEAD, 0f, turn * 0.95f, 0f);
            boolean faceRight = turn < 0;
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0;
                switch (arms) {
                    case 1 -> {
                        if (right == faceRight) {
                            ZMove.arm(p, right, -0.15f, 2.5f, 0f, -0.75f, 0.2f, 0f);   // up by its face
                        } else {
                            ZMove.arm(p, right, -0.15f, 0.2f, 0f, -0.3f, 0.2f, 0f);
                        }
                    }
                    case 2 -> ZMove.arm(p, right, -0.15f, 2.3f, 0f, -1.0f, 0.3f, 0f);   // both up
                    case 3 -> ZMove.arm(p, right, -0.2f, right == faceRight ? 1.25f : 0.3f, 0f, -0.55f, 0.2f, 0f);
                    default -> ZMove.arm(p, right, -0.15f, 0.18f, 0f, -0.25f, 0.2f, 0f);   // by its sides
                }
                boolean bent = legs == 2 && right != faceRight;
                ZMove.leg(p, right, -0.1f, bent ? 0.9f : 0.12f, 1.0f, bent ? 0.75f : right ? 0.12f : 0.18f, 0f);
            }
        } else {
            p.set(ZMove.NECK, 0.05f, turn * 0.3f, 0f);
            p.set(ZMove.HEAD, 0f, turn * 0.65f, 0f);
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0;
                switch (arms) {
                    case 1 -> ZMove.arm(p, right, 0.15f, 1.3f, 0f, -0.5f, 0.2f, 0f);   // flung wide
                    case 2 -> ZMove.arm(p, right, 0.15f, 2.5f, 0f, -0.8f, 0.2f, 0f);  // over its head
                    case 3 -> ZMove.arm(p, right, 0.15f, right ? 1.2f : 0.35f, 0f, -0.45f, 0.2f, 0f);
                    default -> ZMove.arm(p, right, 0.15f, 0.3f, 0f, -0.3f, 0.2f, 0f);
                }
                boolean bent = legs == 2 && right;
                ZMove.leg(p, right, bent ? -0.45f : 0.08f, bent ? 0.95f : 0.08f, -0.35f,
                        bent ? 0.65f : right ? 0.16f : 0.2f, 0.5f);
            }
        }
    }

    /** Where a body already on the ground ends up: slack, face down (a crawler) or on its back, shoved along. */
    private void slack(Fall f) {
        ZMove.Pose p = f.rest;
        p.clear();
        p.lying = true;
        f.prone = f.kind == CRAWL;
        float push = f.force < 0 ? 0f : 0.45f + 0.35f * f.force;
        float sx = ZMove.sin(f.heading) * push, sz = ZMove.cos(f.heading) * push;
        float turn = rnd(f.seed, 7) < 0.5f ? -1f : 1f;
        float roll = (rnd(f.seed, 5) - 0.5f) * 0.3f + sx * 0.15f;
        float yaw = (rnd(f.seed, 6) - 0.5f) * 0.3f;
        int arms = (int) (rnd(f.seed, 8) * 3);
        if (f.prone) {
            p.set(ZMove.HIPS, PI / 2 - (f.legless ? 0.05f : 0f), roll, yaw);
            p.hz = -7f;
            p.set(ZMove.NECK, -0.1f, turn * 0.45f, 0f);
            p.set(ZMove.HEAD, -0.15f, turn * 0.95f, 0f);
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0;
                if (arms == 0 || arms == 1 && right) {
                    ZMove.arm(p, right, -2.55f, 0.5f, 0.2f, -0.9f, 0.3f, 0f);   // still reaching ahead
                } else {
                    ZMove.arm(p, right, -0.15f, 0.2f, 0f, -0.3f, 0.2f, 0f);
                }
                ZMove.leg(p, right, -0.08f, 0.15f, 1.0f, right ? 0.1f : 0.14f, 0.1f);
            }
        } else {
            p.set(ZMove.HIPS, -PI / 2, roll, yaw);
            p.hz = 6f;
            p.set(ZMove.NECK, 0.05f, turn * 0.3f, 0f);
            p.set(ZMove.HEAD, 0f, turn * 0.65f, 0f);
            for (int side = 0; side < 2; side++) {
                boolean right = side == 0;
                ZMove.arm(p, right, 0.15f, arms == 0 || arms == 1 && right ? 1.0f : 0.35f, 0f, -0.4f, 0.2f, 0f);
                ZMove.leg(p, right, 0.05f, 0.12f, -0.3f, right ? 0.15f : 0.2f, 0.4f);
            }
        }
        p.hx += sx * 3f;
        p.hz += sz * 3f;
        p.hy = ZMove.PELVIS_D / 2;
    }

    /**
     * The limbs as it goes down, before it lands: the legs buckled, the spine folding the way it falls, the head
     * lolling and the arms hanging.
     */
    private static void loose(Fall f) {
        ZMove.Pose p = f.loose;
        p.copy(f.rest);
        if (f.onGround()) {
            return;
        }
        float c = ZMove.cos(f.dir), s = ZMove.sin(f.dir);
        p.set(ZMove.SPINE, 0.3f * c, 0f, -0.25f * s);
        p.set(ZMove.NECK, 0.2f, 0f, -0.1f * s);
        p.set(ZMove.HEAD, 0.25f, 0f, -0.15f * s);
        for (int side = 0; side < 2; side++) {
            boolean right = side == 0;
            ZMove.arm(p, right, 0.1f, 0.2f, 0.1f, -0.3f, 0.2f, 0f);
        }
        // a runner's legs fold under it mid-stride; standing, both knees go
        boolean run = f.kind == RUN || f.kind == LUNGE;
        float a = run ? -0.75f : -0.65f, k = run ? 1.5f : 1.45f;
        ZMove.leg(p, true, a, k, -(a + k) * 0.6f, 0.08f, 0f);
        ZMove.leg(p, false, run ? -0.1f : a + 0.1f, run ? 1.1f : k - 0.15f, -(a + k) * 0.6f, 0.1f, 0.1f);
    }

    // ---- laying it on the ground

    /**
     * Sets the resting body down onto the ground with its mesh: the hips so the trunk lies on it, then the head and
     * each limb swung down until it touches.
     */
    private void drape(Fall f) {
        ZBodyMesh mesh = f.mesh;
        if (mesh == null) {
            return;
        }
        ZMove.Pose p = f.rest;
        int hidden = ZBodyLayer.hiddenBones(f.stumps, f.legless);
        int trunk = bit(ZMove.HIPS) | bit(ZMove.SPINE);
        p.hy += GROUND + REST - lowest(mesh, p, trunk & ~hidden, f.legless);
        if ((hidden & bit(ZMove.HEAD)) == 0) {
            swing(mesh, p, ZMove.NECK * 3, ZMove.HEAD * 3, 1.2f, bit(ZMove.HEAD) | bit(ZMove.NECK), f.legless,
                    GROUND + REST, 0.55f);
        }
        int[][] chains = {{ZMove.SH_R, ZMove.EL_R, ZMove.WR_R}, {ZMove.SH_L, ZMove.EL_L, ZMove.WR_L},
                {ZMove.HIP_R, ZMove.KN_R, ZMove.AN_R}, {ZMove.HIP_L, ZMove.KN_L, ZMove.AN_L}};
        for (int[] chain : chains) {
            int mask = bit(chain[0]) | bit(chain[1]) | bit(chain[2]);
            if ((hidden & bit(chain[0])) != 0) {
                continue;
            }
            swing(mesh, p, chain[0] * 3, -1, 0f, mask & ~hidden, f.legless, GROUND + LIMB_REST, 1.0f);
            // then the forearm or shin down too, bending only the way the joint bends
            int lower = (bit(chain[1]) | bit(chain[2])) & ~hidden;
            if (lower != 0) {
                boolean arm = chain[0] == ZMove.SH_R || chain[0] == ZMove.SH_L;
                swing(mesh, p, chain[1] * 3, -1, 0f, lower, f.legless, GROUND + LIMB_REST, 1.2f,
                        arm ? -2.3f : 0f, arm ? 0f : 2.3f);
            }
        }
        // whatever still couldn't turn clear of the floor props the body up on it
        float low = lowest(mesh, p, ~hidden & 0xFFFF, f.legless);
        if (low < GROUND) {
            p.hy += GROUND - low;
        }
    }

    /**
     * Swings one joint's x (index i in the pose, and with it a share of the next joint's, index j, or -1) until the
     * bones in mask just touch the ground at height target: the nearest such turn within max radians either way. If
     * none touches, the turn that leaves them nearest the ground without going through it.
     */
    private void swing(ZBodyMesh mesh, ZMove.Pose p, int i, int j, float share, int mask, boolean legless, float target,
                       float max) {
        swing(mesh, p, i, j, share, mask, legless, target, max, -10f, 10f);
    }

    /** As above, keeping the joint's x between lo and hi (a knee or elbow only bends one way). */
    private void swing(ZBodyMesh mesh, ZMove.Pose p, int i, int j, float share, int mask, boolean legless, float target,
                       float max, float lo, float hi) {
        float a0 = p.rot[i], b0 = j < 0 ? 0f : p.rot[j];
        float from = Math.max(-max, lo - a0), to = Math.min(max, hi - a0);
        if (from >= to) {
            return;
        }
        int n = 36;
        float best = 0f, bestH = Float.NaN, cross = Float.NaN;
        float prevA = 0f, prevH = 0f;
        for (int k = 0; k <= n; k++) {
            float d = from + (to - from) * k / n;
            float h = height(mesh, p, i, j, share, a0, b0, d, mask, legless) - target;
            if (k > 0 && (h > 0f) != (prevH > 0f)) {
                // a crossing between the last two: the nearest one to where it was meant to lie
                float c = bisect(mesh, p, i, j, share, a0, b0, prevA, prevH, d, mask, legless, target);
                if (Float.isNaN(cross) || Math.abs(c) < Math.abs(cross)) {
                    cross = c;
                }
            }
            boolean better = Float.isNaN(bestH) || (h >= 0f) != (bestH >= 0f) ? h >= 0f || Float.isNaN(bestH)
                    : h >= 0f ? h < bestH || h == bestH && Math.abs(d) < Math.abs(best) : h > bestH;
            if (better) {
                best = d;
                bestH = h;
            }
            prevA = d;
            prevH = h;
        }
        float d = Float.isNaN(cross) ? best : cross;
        p.rot[i] = a0 + d;
        if (j >= 0) {
            p.rot[j] = b0 + d * share;
        }
    }

    private float height(ZBodyMesh mesh, ZMove.Pose p, int i, int j, float share, float a0, float b0, float d,
                         int mask, boolean legless) {
        p.rot[i] = a0 + d;
        if (j >= 0) {
            p.rot[j] = b0 + d * share;
        }
        return lowest(mesh, p, mask, legless);
    }

    private float bisect(ZBodyMesh mesh, ZMove.Pose p, int i, int j, float share, float a0, float b0, float lo,
                         float hLo, float hi, int mask, boolean legless, float target) {
        for (int k = 0; k < 10; k++) {
            float mid = (lo + hi) / 2f;
            float h = height(mesh, p, i, j, share, a0, b0, mid, mask, legless) - target;
            if ((h > 0f) == (hLo > 0f)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2f;
    }

    private static int bit(int bone) {
        return 1 << bone;
    }

    /** The lowest point of the bones in mask (their main shapes), in the preview's space. */
    float lowest(ZBodyMesh mesh, ZMove.Pose p, int mask, boolean legless) {
        float[] mats = chain(mesh, p, legless);
        float low = Float.MAX_VALUE;
        for (int pass = 0; pass < 2; pass++) {
            for (int q : mesh.quads(pass == 0, ZDetail.SIMPLE, legless)) {
                int b = mesh.bone[q];
                if ((mask >> b & 1) == 0) {
                    continue;
                }
                int o = b * 16;
                for (int c = 0; c < 4; c++) {
                    int v = q * 12 + c * 3;
                    float y = mats[o + 1] * mesh.pos[v] + mats[o + 5] * mesh.pos[v + 1] + mats[o + 9] * mesh.pos[v + 2]
                            + mats[o + 13];
                    low = Math.min(low, y);
                }
            }
        }
        return low;
    }

    /** Each bone's matrix in the preview's own space (y up, front +z), as ZBodyLayer.matrices builds the model's. */
    private float[] chain(ZBodyMesh mesh, ZMove.Pose p, boolean legless) {
        float[] mats = new float[ZMove.BONES * 16];
        for (int b = 0; b < ZMove.BONES; b++) {
            Matrix4f m = world[b];
            float rx = p.rot[b * 3], ry = p.rot[b * 3 + 1], rz = p.rot[b * 3 + 2];
            if (b == ZMove.HIPS) {
                m.translation(p.hx, p.hy, p.hz);
                if (p.lying) {
                    m.rotateXYZ(rx, ry, rz);
                } else {
                    if (p.tilt != 0f) {
                        m.rotateY(p.fallDir).rotateX(p.tilt).rotateY(-p.fallDir);
                    }
                    m.rotateYXZ(ry, rx, rz);
                }
            } else {
                m.set(world[mesh.parent[b]]);
                m.translate(mesh.rest[b * 3], mesh.rest[b * 3 + 1], mesh.rest[b * 3 + 2]);
                m.rotateXYZ(rx, ry, rz);
                if (legless && (b == ZMove.HIP_R || b == ZMove.HIP_L)) {
                    m.scale(1f, 0.34f, 1f);
                }
            }
            m.get(mats, b * 16);
        }
        return mats;
    }
}
