package com.zomboidcraft.client.zombie.body;

import com.zomboidcraft.zombie.ClimbPath;

/**
 * Breaking in (breakin.js in the character preview, joint for joint): beating on a door or a window, and climbing in
 * through a window whose glass is gone. A door it leans into and beats, one blow a second like the game, arms in turn
 * and every fourth both at once, its free hand pawing at it. Through a window it rakes the teeth out of the frame,
 * hauls itself onto the sill, slides through on its belly, spills out head first onto the floor, lies there a moment
 * and gets up.
 *
 * <p>Hands and feet that touch something reach for it (two bones solved to a point), so they stay put while the body
 * moves. Spaces and signs are ZMove's. The bash is drawn in the zombie's own frame with the door's face 8.6 units ahead
 * and a window's wall face 2.4 ahead (the renderer and the server's hit shapes move it there); the climb is keyed in
 * the window's frame, glass at z 0, and given back with the hips over the zombie, which the server walks through the
 * opening under them (ClimbPath).
 */
final class ZBreak {
    private static final float PI2 = (float) Math.PI / 2;
    /** Body A: the arm's two bones as the reach sees them (upper less the shoulder, the forearm to the wrist), and the leg's. */
    private static final float UPPER = 4.5f, FORE = 4.9f, THIGH = 7.2f, SHIN = 6.6f;
    /** The door's face, and a window's glass, ahead of the zombie while it beats on them. */
    static final float DOOR = 8.6f, WIN_Z = 11.2f;
    private static final float NONE = Float.NaN;

    /**
     * One limb's key: reach the wrist (or ankle) for a point with the hand at pitch hw in the world (a foot: flat, or
     * tipped by a), or set it by hand: the upper bone's pitch in the world p (0 hangs down, -PI/2 points ahead, PI/2
     * behind), spread out by o, the elbow folded by e (below 0) or the knee by k (above 0), the hand turned by w, the
     * foot by a.
     */
    record Spec(float[] t, float hw, float p, float o, float e, float k, float w, float a) {
        boolean reach() {
            return t != null;
        }
    }

    static Spec hand(float x, float y, float z, float hw) {
        return new Spec(new float[] {x, y, z}, hw, 0, 0, 0, 0, 0, 0);
    }

    static Spec foot(float x, float y, float z) {
        return new Spec(new float[] {x, y, z}, NONE, 0, 0, 0, 0, 0, 0);
    }

    static Spec foot(float x, float y, float z, float a) {
        return new Spec(new float[] {x, y, z}, NONE, 0, 0, 0, 0, 0, a);
    }

    static Spec arm(float p, float o, float e, float w) {
        return new Spec(null, NONE, p, o, e, 0, w, 0);
    }

    static Spec leg(float p, float o, float k, float a) {
        return new Spec(null, NONE, p, o, 0, k, 0, a);
    }

    /** A limb's angles: the upper joint (x, y, z), the elbow or knee, and the wrist or ankle. */
    static final class Limb {
        float ux, uy, uz, l, w;
    }

    /** Where the joints are for a pose (hips turned Y-X-Z, the spine X-Y-Z), in the zombie's frame. */
    static final class Frames {
        final float[] mh = new float[9], ms = new float[9], tmp = new float[9];
        float hx, sx;
        float px, py, pz;

        void set(float[] pos, float[] hips, float[] spine) {
            rot(hips[0], hips[1], hips[2], true, mh);
            rot(spine[0], spine[1], spine[2], false, tmp);
            mul(mh, tmp, ms);
            hx = hips[0];
            sx = hips[0] + spine[0];
            px = pos[0];
            py = pos[1];
            pz = pos[2];
        }

        /** The shoulder joint (s -1 right, 1 left). */
        void shoulder(float s, float[] out) {
            float sox = mh[1] * 1.5f + px, soy = mh[4] * 1.5f + py, soz = mh[7] * 1.5f + pz;
            ap(ms, s * 5.05f, 7.9f, 0f, out);
            out[0] += sox;
            out[1] += soy;
            out[2] += soz;
        }

        /** The hip joint. */
        void hip(float s, float[] out) {
            ap(mh, s * 1.82f, -1.1f, 0f, out);
            out[0] += px;
            out[1] += py;
            out[2] += pz;
        }
    }

    // ---- rotations as three.js builds them (X-Y-Z is Rx Ry Rz, Y-X-Z is Ry Rx Rz), row-major 3x3 ----

    static void rot(float x, float y, float z, boolean yxz, float[] out) {
        float cx = cos(x), sx = sin(x), cy = cos(y), sy = sin(y), cz = cos(z), sz = sin(z);
        float[] X = {1, 0, 0, 0, cx, -sx, 0, sx, cx}, Y = {cy, 0, sy, 0, 1, 0, -sy, 0, cy}, Z = {cz, -sz, 0, sz, cz, 0, 0, 0, 1};
        float[] m = new float[9];
        if (yxz) {
            mul(Y, X, m);
        } else {
            mul(X, Y, m);
        }
        mul(m, Z, out);
    }

    static void mul(float[] a, float[] b, float[] out) {
        float[] r = new float[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i * 3 + j] = a[i * 3] * b[j] + a[i * 3 + 1] * b[3 + j] + a[i * 3 + 2] * b[6 + j];
            }
        }
        System.arraycopy(r, 0, out, 0, 9);
    }

    static void ap(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[1] * y + m[2] * z;
        out[1] = m[3] * x + m[4] * y + m[5] * z;
        out[2] = m[6] * x + m[7] * y + m[8] * z;
    }

    static void apT(float[] m, float x, float y, float z, float[] out) {
        out[0] = m[0] * x + m[3] * y + m[6] * z;
        out[1] = m[1] * x + m[4] * y + m[7] * z;
        out[2] = m[2] * x + m[5] * y + m[8] * z;
    }

    /**
     * Two bones reaching for d (in the upper joint's parent frame; the bones hang down -y at rest). An elbow folds the
     * lower bone forward, a knee back. Gives the upper joint's x and z and the fold, exact for a joint turned x then z
     * with no y, and x kept within a half turn so easing between poses never spins a limb.
     */
    static void solve(float[] d, float l1, float l2, boolean elbow, float[] out) {
        float n = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        if (n == 0f) {
            n = 1e-6f;
        }
        float len = Math.min(l1 + l2 - 1e-3f, Math.max(Math.abs(l1 - l2) + 1e-3f, n));
        float ex = d[0] * len / n, ey = d[1] * len / n, ez = d[2] * len / n;
        float k = (float) Math.acos(clamp((len * len - l1 * l1 - l2 * l2) / (2 * l1 * l2), -1f, 1f));
        float r = l1 + l2 * cos(k), c = (float) Math.asin(clamp(ex / r, -1f, 1f));
        float q = (elbow ? 1 : -1) * l2 * sin(k);
        float x = (float) (Math.atan2(ez, ey) - Math.atan2(q, -r * cos(c)));
        out[0] = (float) Math.atan2(sin(x), cos(x));
        out[1] = c;
        out[2] = k;
    }

    private final float[] at = new float[3], d = new float[3], sol = new float[3];

    /** One limb's angles from its key. */
    void limb(Spec spec, Frames f, boolean right, boolean isArm, Limb out) {
        float s = right ? -1f : 1f, base = isArm ? f.sx : f.hx;
        if (spec.reach()) {
            if (isArm) {
                f.shoulder(s, at);
            } else {
                f.hip(s, at);
            }
            apT(isArm ? f.ms : f.mh, spec.t[0] - at[0], spec.t[1] - at[1], spec.t[2] - at[2], d);
            if (isArm) {
                solve(d, UPPER, FORE, true, sol);
            } else {
                solve(d, THIGH, SHIN, false, sol);
            }
            float l = isArm ? -sol[2] : sol[2], end = base + sol[0] + l;
            out.ux = sol[0];
            out.uy = 0f;
            out.uz = sol[1];
            out.l = l;
            out.w = !Float.isNaN(spec.hw) ? spec.hw - end : isArm ? spec.w : -end + spec.a;
            return;
        }
        out.ux = spec.p - base;
        out.uy = 0f;
        out.uz = spec.o * s;
        out.l = isArm ? spec.e : spec.k;
        out.w = isArm ? spec.w : spec.a;
    }

    /** The same angle a whole number of turns over, nearest to a (so easing goes the short way round). */
    static float near(float a, float b) {
        float turn = (float) Math.PI * 2;
        return b + Math.round((a - b) / turn) * turn;
    }

    private final Limb la = new Limb(), lb = new Limb();

    /** A limb's track at time t: reaching for a point that slides between two reaching keys, or easing key to key. */
    void track(float[] times, Spec[] keys, float t, Frames f, boolean right, boolean isArm, Limb out) {
        int i = 0;
        while (i + 1 < times.length && times[i + 1] <= t) {
            i++;
        }
        if (i + 1 >= times.length || t <= times[0]) {
            limb(keys[t <= times[0] ? 0 : i], f, right, isArm, out);
            return;
        }
        Spec a = keys[i], b = keys[i + 1];
        float s = ClimbPath.smooth((t - times[i]) / (times[i + 1] - times[i]));
        if (a.reach() && b.reach()) {
            float[] p = {a.t[0] + (b.t[0] - a.t[0]) * s, a.t[1] + (b.t[1] - a.t[1]) * s, a.t[2] + (b.t[2] - a.t[2]) * s};
            boolean hw = !Float.isNaN(a.hw) && !Float.isNaN(b.hw);
            limb(new Spec(p, hw ? a.hw + (b.hw - a.hw) * s : NONE, 0, 0, 0, 0, a.w + (b.w - a.w) * s,
                    a.a + (b.a - a.a) * s), f, right, isArm, out);
            return;
        }
        limb(a, f, right, isArm, la);
        limb(b, f, right, isArm, lb);
        out.ux = la.ux + (near(la.ux, lb.ux) - la.ux) * s;
        out.uy = la.uy + (near(la.uy, lb.uy) - la.uy) * s;
        out.uz = la.uz + (near(la.uz, lb.uz) - la.uz) * s;
        out.l = la.l + (near(la.l, lb.l) - la.l) * s;
        out.w = la.w + (near(la.w, lb.w) - la.w) * s;
    }

    /** A limb track: its key times and keys. */
    record Track(float[] times, Spec[] keys) {
        static Track of(Object... pairs) {
            float[] times = new float[pairs.length / 2];
            Spec[] keys = new Spec[pairs.length / 2];
            for (int i = 0; i < times.length; i++) {
                times[i] = ((Number) pairs[i * 2]).floatValue();
                keys[i] = (Spec) pairs[i * 2 + 1];
            }
            return new Track(times, keys);
        }
    }

    // ---- the climb, keyed by hand: the spine, the neck and head, then each limb (the hips are ClimbPath's) ----

    private static final float[][] SPINE = {
        {0.00f, 0.25f, 0.1f, 0}, {0.25f, 0.12f, 0.35f, 0.05f}, {0.45f, 0.3f, -0.15f, 0}, {0.60f, 0.26f, -0.05f, 0},
        {0.78f, 0.12f, -0.35f, -0.05f}, {0.95f, 0.32f, 0.15f, 0}, {1.10f, 0.22f, 0.1f, 0}, {1.18f, 0.3f, 0.25f, 0},
        {1.38f, 0.45f, -0.25f, 0}, {1.60f, 0.55f, 0, 0}, {1.90f, 0.7f, 0, 0}, {2.25f, 0.7f, 0, 0}, {2.60f, 0.15f, 0, 0.05f},
        {2.95f, 0.05f, 0.08f, 0}, {3.30f, 0, -0.1f, 0.05f}, {3.70f, 0, 0.1f, -0.05f}, {4.10f, 0.35f, 0, 0}, {4.45f, 0.3f, 0, 0},
        {4.70f, 0, 0, 0}, {4.90f, -0.25f, 0, 0}, {5.05f, -0.05f, 0, 0}, {6.10f, -0.1f, 0, 0}, {6.50f, -0.45f, 0, 0},
        {6.90f, 0.2f, 0, 0}, {7.25f, 0.35f, 0.1f, 0}, {7.65f, 0.25f, 0, 0}, {8.00f, 0.18f, 0.2f, 0}, {9.20f, 0.18f, 0.2f, 0},
    };
    /** Neck x, y, z, then head x, y, z. */
    private static final float[][] HEAD = {
        {0.00f, 0, 0, 0, 0.1f, 0, 0.15f}, {0.25f, -0.1f, 0, 0, 0, 0.1f, 0.1f}, {0.45f, 0.05f, 0, 0, 0.1f, 0, 0.1f},
        {0.78f, -0.1f, 0, 0, 0, -0.1f, -0.1f}, {0.95f, 0.05f, 0, 0, 0.12f, 0, -0.1f}, {1.10f, -0.1f, 0, 0, -0.1f, 0, 0},
        {1.60f, -0.2f, 0, 0, -0.1f, 0, 0}, {2.25f, -0.4f, 0, 0, -0.3f, 0, 0}, {2.60f, -0.6f, 0, 0, -0.5f, 0, 0.1f},
        {3.30f, -0.5f, 0, 0, -0.45f, 0, -0.1f}, {4.10f, -0.4f, 0, 0, -0.3f, 0, 0}, {4.45f, -0.6f, 0, 0, -0.5f, 0, 0},
        {4.90f, -0.2f, 0, 0, 0, 0, 0}, {5.05f, -0.35f, 0.35f, 0, -0.2f, 0.5f, 0}, {5.60f, -0.3f, 0.2f, 0, -0.3f, 0.3f, 0.1f},
        {6.10f, -0.4f, 0, 0, -0.4f, -0.2f, 0}, {6.50f, -0.5f, 0, 0, -0.4f, 0, 0}, {7.00f, -0.2f, 0, 0, -0.2f, 0, 0},
        {7.70f, 0, 0, 0, 0.1f, 0, 0.2f}, {8.10f, 0, 0.3f, 0, 0, 0.5f, 0.1f}, {9.20f, 0, 0.3f, 0, 0, 0.5f, 0.1f},
    };
    private static final Track A_R = Track.of(
            0.00f, arm(-1.6f, 0.1f, -1.2f, 0.2f), 0.25f, arm(-3.0f, 0.25f, -1.7f, 0.3f),
            0.45f, hand(-2.2f, 30.0f, -3.0f, -1.4f), 0.60f, hand(-2.4f, 28.5f, -4.6f, -1.1f),
            0.78f, hand(-3.6f, 20.6f, -6.5f, -PI2), 0.95f, hand(-3.6f, 20.6f, -6.5f, -PI2), 1.05f, hand(-3.8f, 24.0f, -6.0f, -1.2f),
            1.18f, hand(-5.0f, 27.0f, -2.6f, -PI2), 1.38f, hand(4.0f, 22.6f, -2.4f, -PI2), 1.48f, hand(0f, 25.0f, -3.0f, -1.2f),
            1.60f, hand(-3.6f, 20.6f, -2.0f, -PI2), 2.45f, hand(-3.6f, 20.6f, -2.0f, -PI2),
            2.70f, hand(-3.6f, 25.0f, 3.0f, -1.2f), 2.85f, hand(-3.4f, 20.6f, 5.0f, -PI2), 3.55f, hand(-3.4f, 20.6f, 5.0f, -PI2),
            3.75f, arm(-0.8f, 0.15f, -0.2f, 0.2f), 4.45f, arm(-0.3f, 0.2f, -0.15f, 0.1f),
            4.72f, hand(-4.5f, 1.4f, 27.0f, -PI2), 6.20f, hand(-4.5f, 1.4f, 27.0f, -PI2),
            6.40f, hand(-4.8f, 1.4f, 28.0f, -PI2), 6.70f, hand(-4.8f, 1.4f, 28.0f, -PI2), 6.90f, hand(-4.8f, 1.4f, 26.6f, -PI2),
            7.10f, hand(-4.8f, 1.4f, 26.6f, -PI2), 7.35f, arm(-0.4f, 0.15f, -0.5f, 0.2f), 7.70f, arm(-0.15f, 0.08f, -0.3f, 0.15f),
            9.20f, arm(-0.15f, 0.08f, -0.3f, 0.15f));
    private static final Track A_L = Track.of(
            0.00f, hand(3.5f, 20.6f, -6.5f, -PI2), 0.60f, hand(3.5f, 20.6f, -6.5f, -PI2), 0.78f, arm(-3.0f, 0.25f, -1.7f, 0.3f),
            0.95f, hand(1.8f, 31.0f, -3.0f, -1.6f), 1.10f, hand(1.8f, 29.0f, -4.8f, -1.1f), 1.30f, hand(3.6f, 20.6f, -6.5f, -PI2),
            1.50f, hand(3.6f, 20.6f, -6.5f, -PI2), 1.62f, hand(3.6f, 20.6f, -2.0f, -PI2), 2.55f, hand(3.6f, 20.6f, -2.0f, -PI2),
            2.78f, hand(3.6f, 25.0f, 3.5f, -1.2f), 2.95f, hand(3.4f, 20.6f, 5.0f, -PI2), 3.45f, hand(3.4f, 20.6f, 5.0f, -PI2),
            3.65f, arm(-0.8f, 0.15f, -0.2f, 0.2f), 4.45f, arm(-0.35f, 0.2f, -0.2f, 0.1f),
            4.76f, hand(4.5f, 1.4f, 26.0f, -PI2), 6.30f, hand(4.5f, 1.4f, 26.0f, -PI2),
            6.50f, hand(4.8f, 1.4f, 28.0f, -PI2), 6.75f, hand(4.8f, 1.4f, 28.0f, -PI2), 6.95f, hand(4.8f, 1.4f, 26.6f, -PI2),
            7.15f, hand(4.8f, 1.4f, 26.6f, -PI2), 7.40f, arm(-0.5f, 0.15f, -0.6f, 0.2f), 7.70f, arm(-0.3f, 0.1f, -0.4f, 0.2f),
            8.20f, arm(-0.6f, 0.1f, -0.5f, 0.1f), 9.20f, arm(-0.6f, 0.1f, -0.5f, 0.1f));
    private static final Track L_R = Track.of(
            0.00f, foot(-1.9f, 1.8f, -12.6f), 1.90f, foot(-1.9f, 1.8f, -12.6f), 2.10f, foot(-1.9f, 2.4f, -12.4f, 0.6f),
            2.25f, leg(0.12f, 0.05f, 0.4f, 0.6f), 2.60f, leg(-0.05f, 0.05f, 0.5f, 0.5f), 2.80f, leg(-0.4f, 0.08f, 1.5f, 0.3f),
            2.95f, leg(-1.45f, 0.1f, 2.9f, 0.4f), 3.30f, leg(1.6f, 0.1f, 0.35f, 0.4f), 3.70f, leg(1.62f, 0.06f, 0.25f, 0.5f),
            4.10f, leg(1.62f, 0.08f, 0.3f, 0.5f), 4.45f, leg(1.75f, 0.1f, 0.3f, 0.4f), 4.70f, leg(2.05f, 0.12f, 0.45f, 0.4f),
            4.90f, leg(1.9f, 0.12f, 1.4f, 0.3f), 5.05f, leg(1.62f, 0.1f, 1.8f, 0.4f), 5.25f, leg(1.6f, 0.1f, 1.0f, 0.5f),
            6.50f, leg(1.58f, 0.1f, 1.0f, 0.6f), 6.90f, leg(-0.52f, 0.06f, 2.09f, 0.9f), 7.25f, leg(0.0f, 0.05f, 1.57f, 0.9f),
            7.65f, foot(-1.9f, 1.8f, 19.2f), 9.20f, foot(-1.9f, 1.8f, 19.2f));
    private static final Track L_L = Track.of(
            0.00f, foot(1.9f, 1.8f, -14.4f), 1.90f, foot(1.9f, 1.8f, -14.4f), 2.15f, foot(1.9f, 2.4f, -14.2f, 0.6f),
            2.30f, leg(0.2f, 0.08f, 0.5f, 0.6f), 2.65f, leg(0.05f, 0.06f, 0.7f, 0.5f), 3.00f, leg(0.5f, 0.08f, 1.0f, 0.4f),
            3.30f, leg(1.5f, 0.1f, 0.5f, 0.4f), 3.70f, leg(1.6f, 0.08f, 0.3f, 0.5f), 4.10f, leg(1.62f, 0.1f, 0.35f, 0.5f),
            4.45f, leg(1.78f, 0.12f, 0.35f, 0.4f), 4.70f, leg(2.1f, 0.14f, 0.5f, 0.4f), 4.90f, leg(2.0f, 0.14f, 1.2f, 0.3f),
            5.08f, leg(1.65f, 0.12f, 1.7f, 0.4f), 5.30f, leg(1.62f, 0.12f, 1.05f, 0.5f), 6.50f, leg(1.6f, 0.12f, 1.0f, 0.6f),
            6.90f, leg(-0.45f, 0.08f, 2.0f, 0.9f), 7.25f, foot(1.9f, 1.8f, 24.2f), 7.65f, foot(1.9f, 1.8f, 22.0f),
            9.20f, foot(1.9f, 1.8f, 22.0f));

    private final Frames f = new Frames();
    private final float[] root = new float[6], pos = new float[3], hips = new float[3], spine = new float[3], hn = new float[6];
    private final Limb ar = new Limb(), al = new Limb(), lr = new Limb(), ll = new Limb();

    /**
     * The climb at time u (seconds into the preview's climb), the hips given back over the zombie: the server keeps it
     * under them (ClimbPath.place), so only their height and turn are left.
     */
    void climb(ZMove.Pose p, float u, float ph) {
        ClimbPath.root(u, root);
        pos[0] = root[0];
        pos[1] = root[1];
        pos[2] = root[2];
        hips[0] = root[3];
        hips[1] = root[4];
        hips[2] = root[5];
        ClimbPath.track(SPINE, u, spine);
        ClimbPath.track(HEAD, u, hn);
        f.set(pos, hips, spine);
        track(A_R.times, A_R.keys, u, f, true, true, ar);
        track(A_L.times, A_L.keys, u, f, false, true, al);
        track(L_R.times, L_R.keys, u, f, true, false, lr);
        track(L_L.times, L_L.keys, u, f, false, false, ll);
        // the feet scrabble at the wall while it hauls itself up; it twitches where it lies
        float scr = ClimbPath.smooth((u - 2.2f) / 0.15f) * (1 - ClimbPath.smooth((u - 2.95f) / 0.15f));
        lr.ux += scr * sin(u * 31) * 0.22f;
        ll.ux += scr * sin(u * 29 + 1.3f) * 0.22f;
        lr.l += scr * Math.max(0f, sin(u * 31)) * 0.3f;
        ll.l += scr * Math.max(0f, sin(u * 29 + 1.3f)) * 0.3f;
        float lie = ClimbPath.smooth((u - 5.3f) / 0.2f) * (1 - ClimbPath.smooth((u - 6.1f) / 0.2f));
        lr.l += lie * Math.max(0f, sin(u * 7.3f)) * 0.25f;
        al.w += lie * sin(u * 13) * 0.15f;
        ar.w += lie * sin(u * 11 + 2) * 0.15f;
        hn[5] += sin(u * 9 + ph) * 0.03f;
        put(p, pos[0], pos[1], 0f, hips, spine, hn);
        // face down on the sill and the floor: a shot only jolts it, as one lying down
        p.lying = hips[0] > 1.2f;
    }

    private final Spec[] bangKeys = new Spec[5];
    private static final float[] BANG_TIMES = {0f, 0.4f, 0.55f, 0.7f, 1f};

    /**
     * Beating on a door or a window's glass, tt seconds into it: a blow lands at every whole second plus 0.55, arms in
     * turn and every fourth both at once, leaning in with the body and the head thrown into it, the free hand pawing.
     */
    void bang(ZMove.Pose p, float tt, float ph, boolean win) {
        int n = (int) Math.floor(tt);
        float b = tt - n;
        boolean right = Math.floorMod(n, 2) == 0, both = Math.floorMod(n, 4) == 3;
        float wind = ClimbPath.smooth((b - 0.05f) / 0.35f) * (1 - ClimbPath.smooth((b - 0.42f) / 0.13f));
        float hit = ClimbPath.smooth((b - 0.5f) / 0.06f) * (1 - ClimbPath.smooth((b - 0.6f) / 0.25f));
        float zAt = win ? WIN_Z - 2.8f : DOOR - 2.9f;
        float turn = both ? 0f : (right ? 1 : -1) * (win ? 0.22f : 0.25f) * wind;
        if (win) {
            pos[0] = 0f;
            pos[1] = 16.0f - 0.3f * hit;
            pos[2] = 0.3f * hit + (both ? 0.4f * hit : 0f);
            hips[0] = 0.1f + 0.03f * hit;
            spine[0] = 0.32f - 0.12f * wind + 0.1f * hit + (both ? 0.06f * hit : 0f);
        } else {
            pos[0] = 0f;
            pos[1] = 16.0f - 0.3f * hit;
            pos[2] = -1.6f + 0.3f * hit + (both ? 0.5f * hit : 0f);
            hips[0] = 0.06f + 0.02f * hit;
            spine[0] = 0.3f - 0.12f * wind + 0.08f * hit + (both ? 0.06f * hit : 0f);
        }
        hips[1] = hips[2] = 0f;
        spine[1] = turn;
        spine[2] = 0f;
        f.set(pos, hips, spine);
        // the swing speeds up into the hit
        float u = b < 0.4f || b > 0.55f ? b : 0.4f + 0.15f * (float) Math.pow((b - 0.4f) / 0.15f, 1.8f);
        if (right || both) {
            blow(true, both, win, tt, zAt, u, ar);
        } else {
            limb(rest(true, win, tt), f, true, true, ar);
        }
        if (!right || both) {
            blow(false, both, win, tt, zAt, u, al);
        } else {
            limb(rest(false, win, tt), f, false, true, al);
        }
        limb(win ? foot(-1.9f, 1.8f, -1.4f) : foot(-1.9f, 1.8f, -0.2f), f, true, false, lr);
        limb(win ? foot(1.9f, 1.8f, -3.4f) : foot(1.9f, 1.8f, -4.2f), f, false, false, ll);
        float butt = both ? hit : 0f, tilt = right ? -1f : 1f;
        hn[0] = 0.05f - 0.15f * wind + 0.08f * hit + 0.15f * butt;
        hn[1] = hn[2] = 0f;
        hn[3] = (win ? -0.05f : 0.1f) - 0.1f * wind + 0.1f * hit + 0.2f * butt;
        hn[4] = sin(tt * 0.7f) * 0.1f;
        hn[5] = 0.12f * tilt * wind + sin(tt * 9 + ph) * 0.02f;
        put(p, pos[0], pos[1], pos[2], hips, spine, hn);
    }

    /** The free hand leans on it and paws: knuckles on the door, the palm smearing down the glass. */
    private static Spec rest(boolean right, boolean win, float tt) {
        float s = right ? -1f : 1f, paw = sin(tt * 3.1f + (right ? 0f : 1.7f));
        return win ? hand(s * 3.2f, 27.5f + 1.4f * paw, WIN_Z - 2.7f, -1.5f)
                : hand(s * 3.0f, 25.5f + 0.8f * paw, DOOR - 2.75f, -1.25f + 0.1f * paw);
    }

    /** One arm's blow: from resting on it, up behind the head, down onto it, a recoil, and back to rest. */
    private void blow(boolean right, boolean both, boolean win, float tt, float zAt, float u, Limb out) {
        float s = right ? -1f : 1f;
        bangKeys[0] = rest(right, win, tt);
        bangKeys[1] = both ? arm(-3.0f, 0.15f, -1.3f, 0.3f) : arm(-2.9f, 0.3f, -1.9f, 0.4f);
        bangKeys[2] = hand(s * (both ? 2.8f : 2.4f), (win ? 30.5f : 30.0f) + (both ? 1f : 0f), zAt, win ? -1.5f : -1.25f);
        bangKeys[3] = hand(s * 2.8f, win ? 28.5f : 27.0f, zAt - 0.9f, win ? -1.3f : -0.9f);
        bangKeys[4] = bangKeys[0];
        track(BANG_TIMES, bangKeys, u, f, right, true, out);
    }

    /** Sets the pose: the hips at (x, y, z) turned Y-X-Z, the spine, the neck and head (six), and the four limbs. */
    private void put(ZMove.Pose p, float x, float y, float z, float[] hips, float[] spine, float[] hn) {
        p.hx = x;
        p.hy = y;
        p.hz = z;
        p.lying = false;
        p.set(ZMove.HIPS, hips[0], hips[1], hips[2]);
        p.set(ZMove.SPINE, spine[0], spine[1], spine[2]);
        p.set(ZMove.NECK, hn[0], hn[1], hn[2]);
        p.set(ZMove.HEAD, hn[3], hn[4], hn[5]);
        set(p, ZMove.SH_R, ZMove.EL_R, ZMove.WR_R, ar);
        set(p, ZMove.SH_L, ZMove.EL_L, ZMove.WR_L, al);
        set(p, ZMove.HIP_R, ZMove.KN_R, ZMove.AN_R, lr);
        set(p, ZMove.HIP_L, ZMove.KN_L, ZMove.AN_L, ll);
    }

    private static void set(ZMove.Pose p, int upper, int mid, int end, Limb l) {
        p.set(upper, l.ux, l.uy, l.uz);
        p.set(mid, l.l, 0f, 0f);
        p.set(end, l.w, 0f, 0f);
    }

    private static float sin(float a) {
        return (float) Math.sin(a);
    }

    private static float cos(float a) {
        return (float) Math.cos(a);
    }

    private static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }
}
