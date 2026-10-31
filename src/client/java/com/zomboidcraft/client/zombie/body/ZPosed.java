package com.zomboidcraft.client.zombie.body;

import com.zomboidcraft.zombie.Corpse;

/**
 * The bodies a Harvestman sets out (zombie.Corpse's poses): sat up against a wall with its legs out and its head
 * fallen to one side, slumped in a doorway leaning on the frame, or hung from a cable with its neck broken over and its
 * toes pointing at the floor. Angles as in ZMove (preview space); the body's number picks which side it lolls to and
 * where its hands fell.
 */
final class ZPosed {
    /** How high the neck sits over the feet of a body standing straight, in blocks at size 1 (where a hung one turns). */
    static final float NECK = 1.68f;

    static void pose(ZMove.Pose p, int pose, int variant) {
        p.clear();
        boolean flip = (variant & 1) != 0, knee = (variant & 2) != 0, lap = (variant & 4) != 0;
        float side = flip ? -1f : 1f;
        if (pose == Corpse.HUNG) {
            hung(p, side, variant);
        } else {
            sat(p, side, knee, lap, pose == Corpse.SLUMPED);
        }
    }

    private static void sat(ZMove.Pose p, float side, boolean knee, boolean lap, boolean slumped) {
        // the pelvis on the floor, tipped back against the wall; the legs out along the floor, one maybe drawn up
        float back = -0.15f;
        p.hy = ZMove.PELVIS_H / 2 + 0.3f;
        p.hz = -0.4f;
        p.set(ZMove.HIPS, back, 0f, slumped ? 0.12f * side : 0f);
        float aOut = -1.6f - back, kOut = 0.1f;
        float aUp = -2.0f - back, kUp = 0.83f;
        boolean rightUp = knee && side > 0, leftUp = knee && side < 0;
        ZMove.leg(p, true, rightUp ? aUp : aOut, rightUp ? kUp : kOut, rightUp ? 1.17f : 0.45f, 0.22f, rightUp ? 0.1f : 0.45f);
        ZMove.leg(p, false, leftUp ? aUp : aOut, leftUp ? kUp : kOut, leftUp ? 1.17f : 0.4f, 0.26f, leftUp ? 0.1f : 0.5f);
        // the spine curled forward off the wall, the head fallen onto a shoulder
        float lean = slumped ? 0.4f : 0.12f;
        p.set(ZMove.SPINE, 0.32f, 0.06f * side, lean * side);
        p.set(ZMove.NECK, 0.3f, 0f, 0.25f * side);
        p.set(ZMove.HEAD, 0.45f, 0.15f * side, 0.38f * side);
        // one arm dead at its side with the hand on the floor, the other in its lap or flung out
        boolean rightLap = lap && side < 0, leftLap = lap && side > 0;
        ZMove.arm(p, true, rightLap ? -0.75f : 0.15f, rightLap ? -0.15f : 0.28f, 0f, rightLap ? -1.0f : -0.25f, 0.2f, 0.1f);
        ZMove.arm(p, false, leftLap ? -0.75f : 0.1f, leftLap ? -0.15f : slumped ? 0.6f : 0.3f, 0f, leftLap ? -1.0f : -0.2f, 0.25f, 0.1f);
    }

    private static void hung(ZMove.Pose p, float side, int variant) {
        // feet off the floor, toes down, knees a little bent; everything hangs from the broken neck
        ZMove.leg(p, true, 0.04f, 0.14f, 0.75f, 0.03f, 0.05f);
        ZMove.leg(p, false, -0.02f, 0.22f, 0.7f, 0.04f, 0.1f);
        p.set(ZMove.SPINE, 0.08f, 0f, 0.04f * side);
        p.set(ZMove.NECK, 0.35f, 0f, 0.55f * side);
        p.set(ZMove.HEAD, 0.25f, 0.1f * side, 0.25f * side);
        float r = (variant >> 3 & 3) * 0.04f;
        ZMove.arm(p, true, 0.03f + r, 0.07f, 0f, -0.12f, 0.1f, 0f);
        ZMove.arm(p, false, 0.05f - r, 0.09f, 0f, -0.18f, 0.12f, 0f);
    }

    private ZPosed() {
    }
}
