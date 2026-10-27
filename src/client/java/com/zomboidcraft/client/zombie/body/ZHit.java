package com.zomboidcraft.client.zombie.body;

/**
 * A body jerking where a shot strikes it (hits.js in the preview): the part that's hit snaps along the shot and swings
 * back, and the rest of the body rocks with it a moment later, harder for heavier rounds. A head hit whips the head
 * back or round, a chest hit knocks the shoulders back and twists the trunk, an arm hit flings the arm, and a leg hit
 * buckles the knee so the body dips. Laid on top of whatever the zombie is doing, living or falling dead. A shot
 * doesn't throw the zombie back: the body rocks back on its feet a touch and the server checks its advance for a
 * split second (Downed.flinch).
 *
 * <p>The parts are Dismember's: head 0, chest 1 (left) and 2 (right), pelvis 3, right upper arm 4, forearm 5, left
 * upper arm 6, forearm 7, right thigh 8, shin 9, left thigh 10, shin 11.
 */
public final class ZHit {
    /** How long a jerk takes to peak (seconds), and the follow-through behind it. */
    private static final float RISE = 0.07f, FOLLOW = 0.09f, FOLLOW_LAG = 0.05f;

    /** A kick that rises to 1 at rise seconds and dies away over about five times that. */
    static float pulse(float t, float rise) {
        if (t <= 0f) {
            return 0f;
        }
        float u = t / rise;
        return u * (float) Math.exp(1f - u);
    }

    /** Whether a hit that landed t seconds ago still moves the body. */
    public static boolean live(float t) {
        return t < 0.6f;
    }

    /**
     * Adds one shot's jerk t seconds after it struck part, going heading (radians from the body's front round toward
     * its left) with force 0 (handgun) to 3 (buckshot); scale shares it out among the parts one blast struck.
     */
    public static void apply(ZMove.Pose p, int part, float heading, float force, float t, int seed, boolean lying) {
        apply(p, part, heading, force, t, seed, lying, 1f);
    }

    public static void apply(ZMove.Pose p, int part, float heading, float force, float t, int seed, boolean lying,
                             float scale) {
        if (part < 0 || !live(t)) {
            return;
        }
        float r = pulse(t, RISE), r2 = pulse(t - FOLLOW_LAG, FOLLOW);
        if (r == 0f) {
            return;
        }
        float a = (0.22f + 0.13f * force) * scale;
        float bx = ZMove.sin(heading), bz = ZMove.cos(heading);
        float twist = ZDeath.rnd(seed, 11) < 0.5f ? -1f : 1f;
        if (lying) {
            // flat on the ground it can only jolt: the trunk slams, the head knocks, the arms jump
            add(p, ZMove.SPINE, a * 0.45f * r, 0f, -a * 0.3f * bx * r);
            add(p, ZMove.NECK, a * 0.4f * r2, 0f, 0f);
            add(p, ZMove.HEAD, a * 0.6f * r2, a * 0.4f * twist * r2, 0f);
            add(p, ZMove.EL_R, -a * 0.5f * r2, 0f, 0f);
            add(p, ZMove.EL_L, -a * 0.5f * r2, 0f, 0f);
            p.hx += bx * a * 2f * r;
            p.hz += bz * a * 2f * r;
            return;
        }
        switch (part) {
            case 0 -> {
                add(p, ZMove.HEAD, a * 1.3f * bz * r, a * 0.5f * twist * r, -a * 1.3f * bx * r);
                add(p, ZMove.NECK, a * 0.5f * bz * r, 0f, -a * 0.5f * bx * r);
                add(p, ZMove.SPINE, a * 0.25f * bz * r2, 0f, -a * 0.2f * bx * r2);
            }
            case 1, 2 -> {
                float side = part == 2 ? 1f : -1f;    // a push on the right side turns it toward its left
                add(p, ZMove.SPINE, a * bz * r, a * 0.6f * bz * side * r, -a * 0.7f * bx * r);
                // the head and arms hang back a moment, then whip after
                add(p, ZMove.HEAD, -a * 0.6f * bz * r2, 0f, a * 0.4f * bx * r2);
                add(p, ZMove.SH_R, a * 0.5f * bz * r2, 0f, a * 0.4f * bx * r2);
                add(p, ZMove.SH_L, a * 0.5f * bz * r2, 0f, a * 0.4f * bx * r2);
            }
            case 3 -> {
                add(p, ZMove.HIPS, a * 0.5f * bz * r, a * 0.3f * twist * r, -a * 0.3f * bx * r);
                add(p, ZMove.SPINE, -a * 0.4f * bz * r2, 0f, a * 0.3f * bx * r2);
                p.hx += bx * a * 2.5f * r;
                p.hz += bz * a * 2.5f * r;
            }
            case 4, 5, 6, 7 -> {
                boolean right = part < 6, fore = part == 5 || part == 7;
                int sh = right ? ZMove.SH_R : ZMove.SH_L, el = right ? ZMove.EL_R : ZMove.EL_L;
                add(p, sh, -a * (fore ? 0.8f : 1.6f) * bz * r, 0f, a * bx * r);
                add(p, el, -a * (fore ? 0.9f : 0.4f) * r, 0f, 0f);
                add(p, ZMove.SPINE, a * 0.25f * bz * r2, a * 0.3f * bz * (right ? 1f : -1f) * r2, -a * 0.15f * bx * r2);
            }
            default -> {
                boolean right = part < 10;
                int hip = right ? ZMove.HIP_R : ZMove.HIP_L, knee = right ? ZMove.KN_R : ZMove.KN_L;
                add(p, hip, -a * 0.6f * bz * r, 0f, a * 0.3f * bx * r);
                add(p, knee, a * 1.2f * r, 0f, 0f);
                // the knee gives: it dips toward that side and hunches
                p.hy -= a * 2.5f * r2;
                add(p, ZMove.SPINE, a * 0.4f * r2, 0f, (right ? 1f : -1f) * a * 0.25f * r2);
            }
        }
        if (part > 3) {
            // the whole body rocks back a little from the push
            add(p, ZMove.SPINE, a * 0.3f * bz * r2, 0f, -a * 0.25f * bx * r2);
        }
        if (part != 3) {
            // and gives a little ground on its feet before it comes on again
            p.hx += bx * a * 1.4f * r2;
            p.hz += bz * a * 1.4f * r2;
        }
    }

    private static void add(ZMove.Pose p, int bone, float x, float y, float z) {
        p.rot[bone * 3] += x;
        p.rot[bone * 3 + 1] += y;
        p.rot[bone * 3 + 2] += z;
    }

    private ZHit() {
    }
}
