package com.zomboidcraft.client.zombie.body;

/**
 * The zombie movements from the character preview (move.js), joint for joint: a swaying idle with
 * the feet planted, a shamble the whole body rides, an alert head turn, a lurching chase with the
 * arms straight out, a lunge-grab-and-bite attack, a hand-over-hand crawl, knocked down, and all
 * of these again for a zombie with no legs.
 *
 * Angles are in the preview's space (three.js: y up, front +z, the zombie's right at -x). A
 * shoulder or hip x below zero swings the limb forward, an elbow x below zero bends the forearm
 * forward, a knee x above zero bends the shin back, and spine, neck and head x above zero lean
 * forward. The hips use Y-X-Z Euler order standing and X-Y-Z lying down, like the preview.
 *
 * A zombie with a leg shot off hops on the one it has (limbs.js in the preview), stumbling onto its knee
 * first; with both gone it falls on its face and crawls.
 */
public final class ZMove {
    public static final int HIPS = 0, SPINE = 1, NECK = 2, HEAD = 3, SH_R = 4, EL_R = 5, WR_R = 6, SH_L = 7, EL_L = 8,
            WR_L = 9, HIP_R = 10, KN_R = 11, AN_R = 12, HIP_L = 13, KN_L = 14, AN_L = 15, BONES = 16;
    /**
     * Standing modes, knocked flat on its back, the crawler's own (face down, also every legless zombie), and
     * hopping or standing on one leg.
     */
    public static final int IDLE = 0, SHAMBLE = 1, ALERT = 2, CHASE = 3, ATTACK = 4, DOWN = 5, CRAWL_REST = 6,
            CRAWL_SLOW = 7, CRAWL_FAST = 8, CRAWL_ALERT = 9, CRAWL_GRAB = 10, HOP = 11, STAND1 = 12, MODES = 13;

    private static final float PI = (float) Math.PI;
    // body A sizes (STYLES.A in the preview)
    private static final float THIGH = 7.2f, SHIN = 6.6f, FOOT = 1.4f, PELVIS_H = 3f, PELVIS_D = 3.8f;
    private static final float BASE = THIGH + SHIN + FOOT + PELVIS_H / 2;

    /** One frame's joint angles plus where the hips sit. */
    public static final class Pose {
        public final float[] rot = new float[BONES * 3];
        public float hx, hy = BASE, hz;
        /** Hips turn X-Y-Z (lying down) rather than Y-X-Z. */
        public boolean lying;

        void clear() {
            java.util.Arrays.fill(rot, 0f);
            hx = 0f;
            hy = BASE;
            hz = 0f;
            lying = false;
        }

        void add(Pose p, float w) {
            for (int i = 0; i < rot.length; i++) {
                rot[i] += p.rot[i] * w;
            }
            hx += p.hx * w;
            hy += p.hy * w;
            hz += p.hz * w;
        }

        void set(int bone, float x, float y, float z) {
            rot[bone * 3] = x;
            rot[bone * 3 + 1] = y;
            rot[bone * 3 + 2] = z;
        }

        public void copy(Pose p) {
            System.arraycopy(p.rot, 0, rot, 0, rot.length);
            hx = p.hx;
            hy = p.hy;
            hz = p.hz;
            lying = p.lying;
        }
    }

    /** What the animation needs to know about one zombie this frame. */
    public static final class Input {
        /** Seconds, for the sways and twitches. */
        public float t;
        /** Gait angle, from how far it has walked, so the feet keep pace with the ground. */
        public float gait;
        /** Per-zombie offset, so a crowd doesn't move in step. */
        public float phase;
        /** Head turn toward what it is looking at, radians (preview sign: positive turns to its left). */
        public float look;
        /** Seconds into the current attack. */
        public float attack;
        public boolean legless;
        /** On one leg: the right one is the one gone (it hops on the left). */
        public boolean goneRight;
        /** On one leg: arms thrust ahead at what it is after, rather than out for balance. */
        public boolean reach;
        /**
         * On one leg and moving: how far through its hop it is, from the ground covered (0 to about 0.38 crouching to
         * spring, off the ground to about 0.74, then landing).
         */
        public float hop;
    }

    private final Pose tmp = new Pose();
    private final Pose live = new Pose();
    private final One one = new One();
    /** The stumble's two holds for each missing leg: buckling toward the gone side, then down on its one knee. */
    private static final Pose[] BUCKLE = {new Pose(), new Pose()}, KNEEL = {new Pose(), new Pose()};
    /** What comes up fast in a fall: the arms to break it, and the head. */
    private static final int[] FAST_BONES = {SH_R, EL_R, WR_R, SH_L, EL_L, WR_L, NECK, HEAD};

    static {
        for (int i = 0; i < 2; i++) {
            One o = new One();
            o.reset(i == 0);
            o.a = -0.5f;
            o.k = 1.2f;
            o.ga = -0.8f;
            o.gout = 0.35f;
            o.shift = 0.9f;
            o.z = 0.8f;
            o.hx = 0.15f;
            o.roll = 0.3f;
            o.sx = 0.55f;
            o.lean = -0.15f;
            o.nx = -0.2f;
            o.hd = -0.3f;
            o.arms(-0.9f, 1.3f, -0.3f, 0.2f, -1.1f, 1.4f, -0.4f, 0.2f);
            o.pose(BUCKLE[i]);
            o.reset(i == 0);
            o.a = -1.5f;
            o.k = 2.0f;
            o.ga = -0.4f;
            o.gout = 0.25f;
            o.shift = 0.6f;
            o.z = 1.5f;
            o.hx = 0.35f;
            o.roll = 0.15f;
            o.sx = 0.85f;
            o.lean = 0.1f;
            o.nx = -0.3f;
            o.hd = -0.25f;
            o.arms(-1.5f, 0.25f, -0.15f, 0.6f, -2.2f, 0.9f, -0.8f, 0.2f);
            o.pose(KNEEL[i]);
        }
    }

    /** Blends every mode with a weight above zero into {@code out}. */
    public void pose(Pose out, float[] weights, Input in) {
        out.clear();
        out.hy = 0f;
        float total = 0f, lying = 0f;
        for (int m = 0; m < MODES; m++) {
            if (weights[m] < 0.01f) {
                continue;
            }
            tmp.clear();
            mode(tmp, m, in);
            out.add(tmp, weights[m]);
            total += weights[m];
            if (tmp.lying) {
                lying += weights[m];
            }
        }
        if (total <= 0f) {
            out.clear();
            return;
        }
        if (Math.abs(total - 1f) > 1e-4f) {
            float k = 1f / total;
            for (int i = 0; i < out.rot.length; i++) {
                out.rot[i] *= k;
            }
            out.hx *= k;
            out.hy *= k;
            out.hz *= k;
        }
        out.lying = lying > total * 0.5f;
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

    private static float seg(float x, float a, float b) {
        float s = clamp((x - a) / (b - a), 0f, 1f);
        return s * s * (3 - 2 * s);
    }

    private static void arm(Pose p, boolean right, float sx, float out, float sy, float el, float wr, float wz) {
        float s = right ? -1f : 1f;
        p.set(right ? SH_R : SH_L, sx, sy * s, out * s);
        p.set(right ? EL_R : EL_L, el, 0f, 0f);
        p.set(right ? WR_R : WR_L, wr, 0f, wz * s);
    }

    private static void leg(Pose p, boolean right, float a, float k, float an, float out, float tw) {
        float s = right ? -1f : 1f;
        p.set(right ? HIP_R : HIP_L, a, tw * s, out * s);
        p.set(right ? KN_R : KN_L, k, 0f, 0f);
        p.set(right ? AN_R : AN_L, an, 0f, 0f);
    }

    /** How far below the hip the ankle hangs. */
    private static float drop(float a, float k) {
        return THIGH * cos(a) + SHIN * cos(a + k);
    }

    /** Hip height that keeps the lower foot on the ground. */
    private static float plant(float aR, float kR, float aL, float kL) {
        return BASE - (THIGH + SHIN - Math.max(drop(aR, kR), drop(aL, kL)));
    }

    private void mode(Pose p, int mode, Input in) {
        switch (mode) {
            case IDLE, ALERT -> idle(p, mode == ALERT, in);
            case SHAMBLE, CHASE -> walk(p, mode == CHASE, in);
            case ATTACK -> attack(p, in);
            case DOWN -> crawl(p, Crawl.DOWN, in.legless, in);
            case CRAWL_SLOW -> crawl(p, Crawl.SLOW, in.legless, in);
            case CRAWL_FAST -> crawl(p, Crawl.FAST, in.legless, in);
            case CRAWL_ALERT -> crawl(p, Crawl.ALERT, in.legless, in);
            case CRAWL_GRAB -> crawl(p, Crawl.GRAB, in.legless, in);
            case HOP, STAND1 -> hop(p, mode == HOP, in);
            default -> crawl(p, Crawl.REST, in.legless, in);
        }
    }

    /** The feet stay planted and the legs still; only the body above the hips sways, and the arms hang behind it. */
    private static void idle(Pose p, boolean alert, Input in) {
        float t = in.t, ph = in.phase, w = t * 0.9f + ph, sw = sin(w), amp = alert ? 0.5f : 1f;
        float kR = 0.16f, kL = 0.2f;
        leg(p, true, -kR * 0.45f, kR, -kR * 0.55f, 0.02f, 0f);
        leg(p, false, -kL * 0.45f, kL, -kL * 0.55f, 0.04f, 0.12f);
        p.hy = plant(-kR * 0.45f, kR, -kL * 0.45f, kL);
        p.set(SPINE, 0.16f + sin(w * 0.5f + 0.8f) * 0.07f * amp, sin(w * 0.5f) * 0.08f * amp, -sw * 0.13f * amp);
        p.set(NECK, 0.04f, 0f, -sin(w - 1.1f) * 0.08f * amp);
        p.set(HEAD, 0.1f + sin(t * 0.7f + ph) * 0.06f, 0f, 0.22f + sin(w - 1.6f) * 0.1f * amp);
        float lag = sin(w - 1.3f);
        arm(p, true, 0.04f + sin(w * 0.5f - 1f) * 0.05f, 0.06f - lag * 0.08f * amp, 0.1f, -0.28f - sin(t * 0.6f + ph) * 0.05f, 0.15f, 0.1f);
        arm(p, false, 0.08f + sin(w * 0.5f + 1f) * 0.05f, 0.08f + lag * 0.08f * amp, 0.1f, -0.35f - sin(t * 0.5f + ph) * 0.05f, 0.2f, 0.1f);
        if (alert) {
            // the head whips round to what it heard or saw; the neck and then the spine follow, the arms start to lift
            float hd = clamp(in.look, -1.5f, 1.5f);
            p.set(HEAD, 0.05f, hd * 0.45f, -0.1f * Math.signum(hd) + sin(t * 9f) * 0.015f);
            p.set(NECK, -0.1f, hd * 0.3f, p.rot[NECK * 3 + 2]);
            p.rot[SPINE * 3] = 0.2f;
            p.rot[SPINE * 3 + 1] += hd * 0.35f;
            float lift = 0.55f + sin(t * 1.3f + ph) * 0.08f;
            arm(p, true, -lift, 0.12f, 0.25f, -0.55f, -0.25f, 0f);
            arm(p, false, -lift * 0.8f, 0.12f, 0.25f, -0.65f, -0.3f, 0f);
        }
    }

    /** Shamble (one leg dragging, the whole body riding the gait) or chase (lurching, arms thrust straight out). */
    private static void walk(Pose p, boolean chase, Input in) {
        float t = in.t, ph = in.phase, q = in.gait + ph;
        float AR = chase ? 0.72f : 0.42f, AL = chase ? 0.62f : 0.26f;
        float aR = -AR * sin(q) + (chase ? -0.1f : 0.02f), aL = AL * sin(q) + (chase ? -0.1f : 0.06f);
        float swR = Math.max(0f, cos(q)), swL = Math.max(0f, -cos(q));
        float kR = 0.12f + swR * (chase ? 1.25f : 0.7f), kL = 0.12f + swL * (chase ? 1.0f : 0.22f);
        float anR = -(aR + kR) * (1 - swR) + swR * 0.35f, anL = -(aL + kL) * (1 - swL) + swL * (chase ? 0.3f : -0.15f);
        leg(p, true, aR, kR, anR, 0.02f, 0f);
        leg(p, false, aL, kL, anL, 0.05f, 0.18f);
        float roll = sin(q), twist = sin(q);
        p.hx = roll * (chase ? 0.5f : 0.85f);
        float hipsZ = roll * (chase ? 0.06f : 0.11f);
        p.set(HIPS, 0f, twist * (chase ? 0.14f : 0.2f), hipsZ);
        p.rot[HIP_R * 3 + 2] -= hipsZ;
        p.rot[HIP_L * 3 + 2] -= hipsZ;
        p.hy = plant(aR, kR, aL, kL) - (chase ? 0.4f : 0.15f);
        p.hz = chase ? 0.8f : 0f;
        float lean = chase ? 0.5f : 0.32f;
        p.set(SPINE, lean + sin(2 * q) * (chase ? 0.06f : 0.04f), -twist * (chase ? 0.18f : 0.25f), -roll * (chase ? 0.05f : 0.09f));
        p.set(NECK, chase ? -0.25f : 0.1f, twist * 0.08f, -roll * 0.05f);
        p.set(HEAD, (chase ? -0.2f : 0.16f) + sin(2 * q - 0.9f) * (chase ? 0.08f : 0.06f), twist * 0.06f,
                (chase ? 0.06f : 0.2f) + sin(q - 1.2f) * 0.07f);
        if (chase) {
            // the spine's lean tips the shoulders forward, so the arms rise by the same angle to stay level
            float bob = sin(2 * q) * 0.08f, claw = sin(t * 7f + ph);
            arm(p, true, -1.5f - lean * 0.95f - bob + sin(q) * 0.1f, -0.04f, 0.12f, -0.12f - Math.max(0f, claw) * 0.2f, -0.15f + claw * 0.25f, 0.1f);
            arm(p, false, -1.44f - lean * 0.95f - bob - sin(q) * 0.1f, -0.02f, 0.12f, -0.16f - Math.max(0f, -claw) * 0.2f, -0.15f - claw * 0.25f, 0.1f);
        } else {
            // dead arms swing from the twist with a lag; one hangs low and loose, the other half-reaches
            float sR = sin(q + PI - 0.7f), sL = sin(q - 0.7f);
            arm(p, true, -0.12f + sR * 0.22f, 0.05f + Math.max(0f, -roll) * 0.08f, 0.15f, -0.3f - Math.max(0f, sR) * 0.25f, 0.25f + sR * 0.12f, 0.1f);
            arm(p, false, -0.85f + sL * 0.15f, 0.1f, 0.2f, -0.55f + sL * 0.1f, -0.1f + sL * 0.1f, 0.05f);
        }
    }

    /** Wind up, lunge onto the front foot grabbing with both hands, pull in and bite, then reset. */
    private static void attack(Pose p, Input in) {
        float t = in.t, c = Math.min(in.attack, 1.99f);
        float wind = seg(c, 0f, 0.35f) * (1 - seg(c, 0.35f, 0.5f)), lunge = seg(c, 0.35f, 0.55f) * (1 - seg(c, 1.5f, 2.0f));
        float bite = c > 0.6f && c < 1.45f ? Math.max(0f, sin((c - 0.6f) / 0.85f * 3 * 2 * PI)) : 0f;
        float pull = seg(c, 0.55f, 0.8f) * (1 - seg(c, 1.45f, 1.8f));
        float aR = -0.62f * lunge + 0.08f * wind, kR = 0.25f + 0.45f * lunge, aL = 0.38f * lunge - 0.05f * wind, kL = 0.18f + 0.25f * lunge;
        leg(p, true, aR, kR, -(aR + kR), 0.04f, 0f);
        leg(p, false, aL, kL, -(aL + kL) + 0.25f * lunge, 0.06f, 0.15f);
        p.hy = plant(aR, kR, aL, kL);
        p.hz = 2.2f * lunge - 0.6f * wind;
        p.set(HIPS, 0f, -0.15f * lunge + 0.1f * wind, 0f);
        p.set(SPINE, 0.12f - 0.2f * wind + 0.5f * lunge + 0.12f * bite, 0.1f * lunge, 0f);
        p.set(NECK, -0.1f * wind - 0.15f * lunge + 0.25f * bite, 0f, 0f);
        p.set(HEAD, 0.1f - 0.35f * wind - 0.15f * lunge + 0.45f * bite, 0f, 0.12f * (1 - lunge) + sin(t * 20f) * 0.03f * bite);
        float sx = -1.1f - 1.0f * wind * (1 - lunge) - 0.9f * lunge + 0.35f * pull;
        float out = 0.55f * wind * (1 - lunge) + 0.25f * lunge * (1 - pull) - 0.1f * pull;
        arm(p, true, sx, out, 0.3f * pull, -0.25f - 0.15f * lunge - 0.95f * pull, -0.4f * lunge + 0.3f * pull, 0.2f * pull);
        arm(p, false, sx + 0.08f, out, 0.3f * pull, -0.3f - 0.15f * lunge - 0.95f * pull, -0.4f * lunge + 0.3f * pull, 0.2f * pull);
    }

    private enum Crawl { SLOW, FAST, REST, ALERT, GRAB, DOWN }

    /** Face down (or on its back when knocked down). */
    private static void crawl(Pose p, Crawl kind, boolean legless, Input in) {
        float t = in.t, ph = in.phase;
        p.lying = true;
        if (kind == Crawl.DOWN) {
            float tt = t + ph;
            p.set(HIPS, -PI / 2, 0f, 0f);
            p.hx = 0f;
            p.hy = PELVIS_D / 2 + 0.2f;
            p.hz = 6f;
            p.set(SPINE, -0.08f, 0f, 0f);
            p.set(NECK, 0.35f + sin(tt * 0.9f) * 0.1f, 0f, 0f);
            p.set(HEAD, 0.25f + sin(tt * 1.7f) * 0.15f, sin(tt * 0.8f) * 0.5f, sin(tt * 1.1f) * 0.12f);
            arm(p, true, -1.2f + sin(tt * 1.9f) * 0.35f, 0.25f, 0.2f, -0.6f + sin(tt * 2.3f) * 0.4f, sin(tt * 3f) * 0.3f, 0f);
            arm(p, false, -1.0f + cos(tt * 1.7f) * 0.35f, 0.3f, 0.2f, -0.5f + cos(tt * 2.1f) * 0.4f, cos(tt * 2.7f) * 0.3f, 0f);
            if (!legless) {
                leg(p, true, -0.5f + sin(tt * 1.3f) * 0.25f, 0.9f + sin(tt * 1.3f) * 0.3f, 0.3f, 0.1f, 0f);
                leg(p, false, -0.1f, 0.25f, 0.1f, 0.12f, 0f);
            }
            return;
        }
        boolean pulling = kind == Crawl.SLOW || kind == Crawl.FAST;
        float q = in.gait * (kind == Crawl.FAST ? 1f : 0.8f) + ph;
        float r = sin(q), pullR = Math.max(0f, r), pullL = Math.max(0f, -r);
        p.set(HIPS, PI / 2 - (legless ? 0.05f : 0f), r * (pulling ? 0.14f : 0.03f), 0f);
        p.hx = 0f;
        p.hy = PELVIS_D / 2 + (legless ? 0.2f : 0.5f);
        p.hz = -7f + (pulling ? (sin(2 * q) * 0.5f + 0.5f) * (legless ? 1.2f : 0.8f) : 0f);
        // the chest heaves up on each pull, the neck cranes so the face stays on what it is after
        float heave = pulling ? Math.abs(r) : 0f;
        p.set(SPINE, -0.08f - heave * (legless ? 0.28f : 0.16f), -r * 0.12f, 0f);
        p.set(NECK, -0.55f - heave * 0.1f, 0f, 0f);
        p.set(HEAD, -0.55f + sin(2 * q) * 0.06f, r * 0.12f, 0.08f + sin(t * 0.8f + ph) * 0.06f);
        if (pulling) {
            // one arm reaches far ahead and plants, the other drags the body up to it, elbow out
            arm(p, true, -(2.0f + 0.85f * (1 - pullR)), 0.25f + 0.3f * pullR, 0.2f, -0.15f - 1.15f * pullR, 0.9f * (1 - pullR) + 0.4f * pullR, 0f);
            arm(p, false, -(2.0f + 0.85f * (1 - pullL)), 0.25f + 0.3f * pullL, 0.2f, -0.15f - 1.15f * pullL, 0.9f * (1 - pullL) + 0.4f * pullL, 0f);
        } else if (kind == Crawl.ALERT) {
            // pushes up on both arms, chest off the ground, and the head turns to find the player
            float hd = clamp(in.look, -1.4f, 1.4f);
            p.rot[SPINE * 3] = -0.45f;
            p.set(NECK, -0.5f, hd * 0.35f, 0f);
            p.set(HEAD, -0.35f, hd * 0.5f, -0.12f * Math.signum(hd));
            arm(p, true, -1.75f, 0.2f, 0.25f, -0.5f, 1.2f, 0f);
            arm(p, false, -1.75f, 0.2f, 0.25f, -0.5f, 1.2f, 0f);
        } else if (kind == Crawl.GRAB) {
            // lunges forward off its elbows and swipes both hands at an ankle, then drags it in
            float c = Math.min(in.attack, 1.79f), l = seg(c, 0.2f, 0.45f) * (1 - seg(c, 1.2f, 1.7f));
            float pull = seg(c, 0.5f, 0.9f) * (1 - seg(c, 1.2f, 1.7f));
            p.rot[SPINE * 3] = -0.25f - 0.35f * l;
            p.rot[NECK * 3] = -0.5f - 0.2f * l;
            p.rot[HEAD * 3] = -0.4f - 0.2f * l + 0.3f * pull * Math.max(0f, sin(c * 18f));
            p.hz += 2.5f * l;
            arm(p, true, -2.3f - 0.6f * l + 0.8f * pull, 0.35f - 0.2f * l, 0.2f, -0.4f + 0.2f * l - 1.0f * pull, 0.3f + 0.5f * pull, 0f);
            arm(p, false, -2.2f - 0.6f * l + 0.8f * pull, 0.35f - 0.2f * l, 0.2f, -0.5f + 0.2f * l - 1.0f * pull, 0.3f + 0.5f * pull, 0f);
        } else {
            // resting on its forearms, swaying its head, fingers twitching
            p.rot[SPINE * 3] = -0.2f;
            p.rot[NECK * 3] = -0.45f;
            p.set(HEAD, -0.4f, sin(t * 0.6f + ph) * 0.35f, sin(t * 0.9f + ph) * 0.1f);
            arm(p, true, -2.1f + sin(t * 0.7f) * 0.05f, 0.35f, 0.3f, -1.3f, 0.5f + sin(t * 5f) * 0.08f, 0f);
            arm(p, false, -2.0f, 0.4f, 0.3f, -1.4f + sin(t * 0.8f) * 0.05f, 0.6f, 0f);
        }
        if (!legless) {
            // legs trail behind: knees bend in turn as the feet push, toes dragging
            float kR = pulling ? 0.25f + pullL * 0.7f : 0.2f, kL = pulling ? 0.25f + pullR * 0.7f : 0.35f;
            leg(p, true, -0.05f - (pulling ? pullL * 0.3f : 0f), kR, 0.6f, 0.12f, 0.1f);
            leg(p, false, -0.05f - (pulling ? pullR * 0.3f : 0f), kL, 0.6f, 0.12f, 0.1f);
        }
    }

    /**
     * A pose on one leg, set by hand: the standing leg (thigh a, knee k) with the hips planted over its foot and
     * shifted so that foot is under the body, the stump of the other leg, the spine, neck and head, and both arms.
     */
    private static final class One {
        boolean goneRight;
        float a, k, toe, ga, gout, shift, z, hx, lift, roll, sx, lean, nx, hd;
        final float[] armS = new float[4], armG = new float[4];

        One reset(boolean goneRight) {
            this.goneRight = goneRight;
            a = k = toe = ga = z = hx = lift = roll = sx = lean = nx = hd = 0f;
            gout = 0.2f;
            shift = 1.3f;
            return this;
        }

        /** The standing side's arm (shoulder x, out, elbow, wrist), then the gone side's. */
        void arms(float s0, float s1, float s2, float s3, float g0, float g1, float g2, float g3) {
            armS[0] = s0;
            armS[1] = s1;
            armS[2] = s2;
            armS[3] = s3;
            armG[0] = g0;
            armG[1] = g1;
            armG[2] = g2;
            armG[3] = g3;
        }

        void pose(Pose p) {
            boolean stR = !goneRight;
            float ss = stR ? -1f : 1f, gs = -ss;
            p.clear();
            leg(p, stR, a, k, -(a + k + hx) + toe, -0.08f, 0f);
            leg(p, goneRight, ga, 0.2f, 0f, gout, 0f);
            p.hx = gs * shift;
            p.hy = BASE - (THIGH + SHIN - drop(a + hx, k)) + lift;
            p.hz = z;
            p.set(HIPS, hx, 0f, -gs * roll);
            p.set(SPINE, sx, 0f, ss * lean);
            p.set(NECK, nx, 0f, 0f);
            p.set(HEAD, hd, 0f, 0f);
            arm(p, stR, armS[0], armS[1], 0.15f, armS[2], armS[3], 0f);
            arm(p, goneRight, armG[0], armG[1], 0.15f, armG[2], armG[3], 0f);
        }
    }

    /**
     * On one leg: crouch, spring and land again, arms out for balance (or reaching for what it is after), the
     * stump swinging; standing still it sways on its bent leg.
     */
    private void hop(Pose p, boolean moving, Input in) {
        float t = in.t, crouch, air, land;
        if (moving) {
            float q = in.hop;
            crouch = seg(q, 0f, 0.35f) * (1 - seg(q, 0.35f, 0.5f));
            air = seg(q, 0.38f, 0.52f) * (1 - seg(q, 0.6f, 0.74f));
            land = seg(q, 0.6f, 0.74f) * (1 - seg(q, 0.74f, 1f));
        } else {
            crouch = 0.3f + 0.08f * sin(t * 1.4f + in.phase);
            air = 0f;
            land = 0.15f + 0.15f * sin(t * 0.9f + in.phase);
        }
        float flail = sin(t * 9f) * land;
        One o = one.reset(in.goneRight);
        o.a = -0.25f - 0.45f * crouch - 0.2f * land + 0.12f * air;
        o.k = 0.3f + 0.95f * crouch + 0.45f * land - 0.25f * air;
        o.toe = 0.4f * air;
        o.ga = -0.3f - 0.25f * air + 0.2f * crouch;
        o.lift = 2f * air;
        o.roll = 0.06f;
        o.sx = 0.35f + 0.15f * crouch - 0.08f * air;
        o.lean = 0.1f;
        o.nx = -0.05f;
        o.hd = 0.1f + 0.2f * land - 0.1f * air;
        if (in.reach) {
            o.arms(-1.6f - 0.2f * air, 0.2f, -0.2f, -0.1f, -1.5f - 0.2f * air, 0.3f, -0.25f, -0.1f);
        } else {
            o.arms(-0.35f - 0.3f * air, 0.7f + 0.3f * air + 0.15f * flail, -0.6f, 0.2f,
                    -0.3f - 0.3f * air, 0.85f + 0.35f * air - 0.15f * flail, -0.5f, 0.2f);
        }
        o.pose(p);
        p.rot[HEAD * 3 + 1] = clamp(in.look, -1.5f, 1.5f) * 0.4f;
    }

    private static float smooth(float x) {
        return seg(x, 0f, 1f);
    }

    /**
     * How far along one hop's ground the body is at phase q (0 to 1): it stays put while it crouches and lands, and
     * covers the ground in the air.
     */
    public static float hopAdvance(float q) {
        return seg(q, 0.4f, 0.72f);
    }

    /** Straight-line blend of two poses; the hips turn in the order of whichever is nearer. */
    public static void mix(Pose out, Pose a, Pose b, float s) {
        for (int i = 0; i < out.rot.length; i++) {
            out.rot[i] = a.rot[i] + (b.rot[i] - a.rot[i]) * s;
        }
        out.hx = a.hx + (b.hx - a.hx) * s;
        out.hy = a.hy + (b.hy - a.hy) * s;
        out.hz = a.hz + (b.hz - a.hz) * s;
        out.lying = s < 0.5f ? a.lying : b.lying;
    }

    /**
     * Just lost a leg, tau seconds ago: it lurches toward the missing side, buckles onto its one knee with a hand
     * reaching for the ground, pushes itself back up and goes into its hop ({@code out} holds the hop on the way in).
     */
    public void stumble(Pose out, Pose from, float tau, boolean goneRight) {
        int i = goneRight ? 0 : 1;
        live.copy(out);
        if (tau < 0.3f) {
            mix(out, from, BUCKLE[i], smooth(tau / 0.3f));
        } else if (tau < 0.8f) {
            mix(out, BUCKLE[i], KNEEL[i], smooth((tau - 0.3f) / 0.5f));
        } else {
            mix(out, KNEEL[i], live, smooth((tau - 0.8f) / 0.9f));
        }
        if (tau > 0.3f) {
            // blending the angles bends the leg on a curve, so set the hips back down onto the standing foot
            int hip = goneRight ? HIP_L : HIP_R, knee = goneRight ? KN_L : KN_R;
            out.hy = BASE - (THIGH + SHIN - drop(out.rot[hip * 3] + out.rot[HIPS * 3], out.rot[knee * 3]));
        }
    }

    /**
     * Just lost its last leg, tau seconds ago (under one): the hips drop like a dead weight as it pitches forward,
     * the arms come up fast to break the fall, then it lands with a bounce ({@code out} holds the legless crawl).
     */
    public void fall(Pose out, Pose from, float tau) {
        if (tau < 0.6f) {
            live.copy(out);
            float e = (tau / 0.6f) * (tau / 0.6f), fast = smooth(tau / 0.3f);
            mix(out, from, live, e);
            for (int b : FAST_BONES) {
                for (int c = 0; c < 3; c++) {
                    int j = b * 3 + c;
                    out.rot[j] = from.rot[j] + (live.rot[j] - from.rot[j]) * fast;
                }
            }
            out.hz = from.hz;
            out.rot[HEAD * 3] -= 0.35f * fast * (1 - e);
        } else {
            float u = Math.min(1f, (tau - 0.6f) / 0.4f), bump = sin(PI * u) * (1 - u);
            out.hy += 1.1f * bump;
            out.rot[SPINE * 3] -= 0.2f * bump;
            out.rot[EL_R * 3] -= 0.6f * bump;
            out.rot[EL_L * 3] -= 0.6f * bump;
            out.rot[HEAD * 3] += 0.25f * bump;
        }
    }
}
