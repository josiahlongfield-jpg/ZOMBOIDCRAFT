package com.zomboidcraft.client.npc;

import org.joml.Matrix4f;

/**
 * Poses a rigged NPC (NpcAssets.Bone): a hand port of tools/npc/pose.py, which renders the same poses offline; keep the
 * two in step. Angles in degrees; each bone turns Ry * Rx * Rz about its pivot (Rz first) in its parent's frame. Model
 * space: +z forward, +y up, +x her left; a downward limb swings forward with negative x.
 */
public final class NpcPose {
    /** The bought model stands in an A-pose; the arms come down to the sides by this much. */
    private static final float ARM_DOWN = 40f;

    /** What drives a pose this frame. */
    public static final class Input {
        /** Walking (0 standing to 1 walking) and running (0 walking to 1 running) blends. */
        public float move, run;
        /** The stride's phase (radians) and the clock (ticks). */
        public float phase, t;
        /** Holding a long gun up (0..1), and a shot's kick fading out (1 just fired). */
        public float aim, kick;
        /** Where the head turns, relative to the body (degrees; pitch down positive). */
        public float headYaw, headPitch;
    }

    /** Each bone's matrix from rest to posed, in model space, into out (one per bone). */
    public static void pose(NpcAssets.Bone[] bones, Input in, Matrix4f[] out) {
        int n = bones.length;
        float[][] r = new float[n][3];
        int hips = find(bones, "hips"), chest = find(bones, "chest"), head = find(bones, "head");
        int lArm = find(bones, "l_arm"), lFore = find(bones, "l_forearm"), rArm = find(bones, "r_arm"), rFore = find(bones, "r_forearm");
        int lThigh = find(bones, "l_thigh"), lShin = find(bones, "l_shin"), rThigh = find(bones, "r_thigh"), rShin = find(bones, "r_shin");
        float move = in.move, run = in.run, t = in.t;
        float s = (float) Math.sin(in.phase);
        float a = 24 + 16 * run, k = 30 + 45 * run, b = 16 + 22 * run;
        set(r, lThigh, 0, -a * s * move);
        set(r, rThigh, 0, a * s * move);
        set(r, lShin, 0, k * Math.max(0f, s) * move + 4);
        set(r, rShin, 0, k * Math.max(0f, -s) * move + 4);
        float lift = -0.03f * (1 + run) * Math.abs(s) * move;
        set(r, hips, 1, 6 * s * move);
        set(r, chest, 1, -9 * s * move);
        set(r, chest, 0, 3 * move + 9 * run + 1.2f * (float) Math.sin(t * 0.09f));
        set(r, hips, 2, 1.5f * (float) Math.sin(t * 0.031f) * (1 - move));
        for (int side = 0; side < 2; side++) {
            int arm = side == 0 ? lArm : rArm, fore = side == 0 ? lFore : rFore;
            float sign = side == 0 ? 1 : -1;
            float swing = b * s * move * sign;
            set(r, arm, 2, -sign * (ARM_DOWN - 2 * (float) Math.sin(t * 0.07f) * (1 - move)));
            set(r, arm, 0, swing);
            set(r, fore, 0, -(12 + 55 * run));
        }
        if (in.aim > 0 && rArm >= 0 && lArm >= 0) {
            float aim = in.aim;
            blend(r, rArm, -78, 24, ARM_DOWN + 4, aim);
            blend(r, rFore, -78, 0, 0, aim);
            blend(r, lArm, -96, -36, -ARM_DOWN - 2, aim);
            blend(r, lFore, -16, 0, 0, aim);
            add(r, rArm, 0, 6 * in.kick);
            add(r, lArm, 0, 6 * in.kick);
            add(r, chest, 0, -3 * in.kick);
            add(r, chest, 1, -10 * aim);
            add(r, head, 1, 10 * aim);
        }
        add(r, head, 1, in.headYaw);
        add(r, head, 0, in.headPitch);
        for (int i = 0; i < n; i++) {
            NpcAssets.Bone bone = bones[i];
            Matrix4f m = new Matrix4f().translate(bone.px(), bone.py(), bone.pz())
                    .rotateY((float) Math.toRadians(r[i][1])).rotateX((float) Math.toRadians(r[i][0])).rotateZ((float) Math.toRadians(r[i][2]))
                    .translate(-bone.px(), -bone.py(), -bone.pz());
            if (bone.parent() < 0) {
                out[i].translation(0, lift, 0).mul(m);
            } else {
                out[i].set(out[bone.parent()]).mul(m);
            }
        }
    }

    public static int find(NpcAssets.Bone[] bones, String name) {
        for (int i = 0; i < bones.length; i++) {
            if (bones[i].name().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void set(float[][] r, int bone, int axis, float degrees) {
        if (bone >= 0) {
            r[bone][axis] = degrees;
        }
    }

    private static void add(float[][] r, int bone, int axis, float degrees) {
        if (bone >= 0) {
            r[bone][axis] += degrees;
        }
    }

    private static void blend(float[][] r, int bone, float x, float y, float z, float amount) {
        if (bone >= 0) {
            r[bone][0] += (x - r[bone][0]) * amount;
            r[bone][1] += (y - r[bone][1]) * amount;
            r[bone][2] += (z - r[bone][2]) * amount;
        }
    }

    private NpcPose() {
    }
}
