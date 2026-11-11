package com.zomboidcraft.client.zombie.body;

/** The deaths the JS port is checked on: mode, kind, heading, force, part, seed, cuts, legless, gait. */
final class DeathCases {
    static final float PI = (float) Math.PI;
    static final Object[][] CASES = {
        {ZMove.IDLE, ZDeath.STILL, PI, 0, 2, 11, 0, false, 0f},
        {ZMove.SHAMBLE, ZDeath.SHAMBLE, 0f, 1, 1, 12, 0, false, 1.2f},
        {ZMove.CHASE, ZDeath.RUN, PI, 0, 2, 13, 0, false, 0.7f},
        {ZMove.CHASE, ZDeath.RUN, PI, 3, 1, 14, 0, false, 2.2f},
        {ZMove.ALERT, ZDeath.STILL, -PI / 2, 3, 1, 15, 0, false, 0f},
        {ZMove.HOP, ZDeath.HOP, 0.3f, 0, 2, 16, 16 | 64, false, 0f},
        {ZMove.CRAWL_FAST, ZDeath.CRAWL, PI / 2, 1, 2, 17, 64 | 128, true, 2f},
        {ZMove.DOWN, ZDeath.DOWN, 2.5f, 0, 1, 18, 0, false, 0f},
        {ZMove.ATTACK, ZDeath.LUNGE, PI, 2, 0, 19, 0, false, 0f},
        {ZMove.IDLE, ZDeath.STILL, PI - 0.3f, 2, 0, 20, 1 << 18, false, 0f},
        {ZMove.IDLE, ZDeath.STILL, 0f, -1, -1, 77, 0, false, 0f},
        {ZMove.SHAMBLE, ZDeath.SHAMBLE, 1.2f, 3, 8, 201, 0, false, 1.0f},
    };

    static ZMove.Pose from(int mode, int cuts, boolean legless, float gait) {
        ZMove move = new ZMove();
        ZMove.Input in = new ZMove.Input();
        in.t = 1.3f; in.gait = gait; in.attack = 0.75f; in.reach = true; in.goneRight = (cuts & 80) != 0; in.hop = 0.3f;
        in.legless = legless; in.phase = 0.4f;
        float[] w = new float[ZMove.MODES]; w[mode] = 1f;
        ZMove.Pose p = new ZMove.Pose();
        move.pose(p, w, in);
        return p;
    }
}
