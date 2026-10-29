package com.zomboidcraft.creature;

import net.minecraft.world.phys.Vec3;

/**
 * Where a shot strikes the Harvestman now that it's drawn as the Spider 7 creature: its shape as it stands at rest (the
 * model's first idle frame), in the model's frame (+x the frame's x, +y off the surface it holds, +z ahead, the surface
 * at y=0), set on its body frame lift below the centre. Its skull is where its eyes are (a shot there throws it back);
 * the glowing sac under its tail is its core; its body and arms are plate; each walking leg is three tubes, hip to knee
 * to ankle to point (only the first while it's lost below the knee). The legs' numbering is HarvestmanBody's (0 and 1
 * front and rear on +x, 2 and 3 on -x), matching the client's.
 */
public final class SpiderShape {
    private static final double[] SKULL = {0, 1.99, 0.50, 0.24};
    private static final double[] CORE = {0, 0.68, -0.27, 0.27};
    /** Plate: segments (two points) and radii. */
    private static final double[][] PLATE = {
            {0, 1.09, 0.04, 0, 1.61, -0.04, 0.32},      // pelvis to chest
            {0, 1.61, -0.04, 0, 2.13, 0.23, 0.27},      // chest to neck
            {0, 1.09, 0.04, 0, 0.72, -0.22, 0.30},      // over the sac
            {0.25, 1.85, 0.25, 0.25, 1.45, 0.85, 0.12}, // arms
            {-0.25, 1.85, 0.25, -0.25, 1.45, 0.85, 0.12}};
    /** Each leg: hip, knee, ankle, point. */
    private static final double[][][] LEGS = {
            {{0.16, 0.93, 0.21}, {0.86, 1.50, 0.92}, {1.32, 0.80, 1.72}, {1.20, 0, 1.78}},
            {{0.19, 0.97, 0.22}, {0.92, 1.21, -0.41}, {1.20, 0.71, -1.14}, {1.10, 0, -1.26}},
            {{-0.16, 0.93, 0.21}, {-0.86, 1.50, 0.92}, {-1.32, 0.80, 1.72}, {-1.20, 0, 1.78}},
            {{-0.19, 0.97, 0.22}, {-0.92, 1.21, -0.41}, {-1.20, 0.71, -1.14}, {-1.10, 0, -1.26}}};
    private static final double[] LEG_R = {0.15, 0.12, 0.09};

    private static Vec3 at(HarvestmanBody.Frame body, double lift, double x, double y, double z) {
        return body.at(x, y - lift, z);
    }

    /** Where a shot from eye along dir (unit) first strikes it within range, or null. */
    public static HarvestmanBody.Strike strike(HarvestmanBody.Frame body, double lift, int lost, Vec3 eye, Vec3 dir, double range) {
        HarvestmanBody.Strike best = null;
        Vec3 skull = at(body, lift, SKULL[0], SKULL[1], SKULL[2]);
        double t = HarvestmanBody.capsule(eye, dir, skull, skull, SKULL[3]);
        if (t <= range) {
            best = new HarvestmanBody.Strike(t, HarvestmanBody.EYES_ZONE, -1, eye.add(dir.scale(t)));
        }
        Vec3 core = at(body, lift, CORE[0], CORE[1], CORE[2]);
        t = HarvestmanBody.capsule(eye, dir, core, core, CORE[3]);
        if (t <= range && (best == null || t < best.distance())) {
            best = new HarvestmanBody.Strike(t, HarvestmanBody.CORE, -1, eye.add(dir.scale(t)));
        }
        for (double[] s : PLATE) {
            t = HarvestmanBody.capsule(eye, dir, at(body, lift, s[0], s[1], s[2]), at(body, lift, s[3], s[4], s[5]), s[6]);
            // the core and skull sit in the plate: a shot that reaches them about as soon goes in there
            if (t <= range && (best == null || t < best.distance() - 0.12)) {
                best = new HarvestmanBody.Strike(t, HarvestmanBody.HULL, -1, eye.add(dir.scale(t)));
            }
        }
        for (int li = 0; li < 4; li++) {
            double[][] leg = LEGS[li];
            int parts = (lost >> li & 1) != 0 ? 1 : 3;
            for (int k = 0; k < parts; k++) {
                t = HarvestmanBody.capsule(eye, dir, at(body, lift, leg[k][0], leg[k][1], leg[k][2]),
                        at(body, lift, leg[k + 1][0], leg[k + 1][1], leg[k + 1][2]), LEG_R[k]);
                if (t <= range && (best == null || t < best.distance())) {
                    best = new HarvestmanBody.Strike(t, HarvestmanBody.LEG, li, eye.add(dir.scale(t)));
                }
            }
        }
        return best;
    }

    private SpiderShape() {
    }
}
