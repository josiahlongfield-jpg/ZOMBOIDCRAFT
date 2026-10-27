package com.zomboidcraft.zombie;

/**
 * Where a zombie climbing in through a window is, moment by moment: its hips' way through the opening (the climb in the
 * character preview, breakin.js), and where the server keeps the zombie itself meanwhile. Plain numbers, shared by the
 * server (WindowClimbGoal), the client's movement (ZBreak) and the hit-shape fitter (tools/zombies/hitcaps).
 *
 * <p>A house window is a one-block sill with an opening over it 0.69 of a block wide and 1.75 tall, through a wall a
 * block thick with the glass in the middle. Times are seconds into the preview's climb; distances are preview units
 * (17.6 to a block) from the glass along the way it climbs, and up from the floor it starts on.
 */
public final class ClimbPath {
    public static final float BLOCK = 17.6f;
    /**
     * Where the game takes up the preview's climb (the glass already beaten in: it rakes the frame clear), when the
     * shards in the top half are gone, when it hauls itself onto the sill, when it hits the floor inside, and when it
     * stands there again.
     */
    public static final float START = 1.1f, RAKED = 1.33f, HAUL = 2.25f, LANDED = 5.05f, END = 7.9f;
    /** Seconds it takes to go from where it stood into the climb. */
    public static final float SETTLE = 0.5f;
    /** Where the hit shapes change while it climbs: at the wall, onto the sill, sliding through, falling, down, getting up, up. */
    public static final float[] STEPS = {START, 2.25f, 3.0f, 4.1f, 4.9f, 6.5f, 7.3f, END};

    private static final float PI2 = (float) Math.PI / 2;
    /** The hips: time, then where they are (x, y, z) and their turn (x, y, z). */
    private static final float[][] ROOT = {
        {0.00f, 0, 16.3f, -11.0f, 0.06f, 0, 0}, {0.25f, 0, 16.1f, -11.3f, 0.02f, 0, 0}, {0.45f, 0, 16.0f, -10.8f, 0.1f, 0, 0},
        {0.60f, 0, 16.1f, -11.0f, 0.08f, 0, 0}, {0.78f, 0, 16.1f, -11.3f, 0.02f, 0, 0}, {0.95f, 0, 16.0f, -10.8f, 0.1f, 0, 0},
        {1.10f, 0, 16.2f, -11.2f, 0.04f, 0, 0}, {1.38f, 0, 16.0f, -10.9f, 0.12f, 0, 0.04f}, {1.60f, 0, 15.8f, -11.1f, 0.15f, 0, 0},
        {1.90f, 0, 14.8f, -11.5f, 0.25f, 0, 0},
        // it springs and hauls its chest onto the sill, gets a knee up, then slides through on its belly
        {2.25f, 0, 19.0f, -11.0f, 0.7f, 0, 0}, {2.60f, 0, 21.6f, -10.6f, 1.35f, 0, 0.05f}, {2.95f, 0, 21.9f, -9.6f, 1.5f, 0, -0.08f},
        {3.30f, 0, 21.7f, -6.0f, 1.55f, 0, 0.06f}, {3.70f, 0, 21.6f, -1.0f, 1.6f, 0, -0.05f}, {4.10f, 0, 21.5f, 4.0f, 1.75f, 0, 0.04f},
        // over the inner edge head first: the hands hit the floor, the arms fold and it lands on its face
        {4.45f, 0, 20.8f, 8.0f, 2.15f, 0, 0}, {4.70f, 0, 15.0f, 13.5f, 2.25f, 0, 0.1f}, {4.90f, 0, 5.5f, 18.8f, 1.75f, 0, 0.06f},
        {5.05f, 0, 2.6f, 21.0f, PI2, 0, 0}, {5.20f, 0, 3.3f, 21.0f, 1.55f, 0, 0}, {5.35f, 0, 2.6f, 21.1f, PI2, 0, 0},
        {6.10f, 0, 2.6f, 21.1f, PI2, 0, 0},
        // pushes up, gets onto its hands and knees, one knee and one foot, and stands
        {6.50f, 0, 3.6f, 20.8f, 1.45f, 0, 0}, {6.90f, 0, 8.6f, 17.7f, 1.25f, 0, 0.04f}, {7.25f, 0, 10.5f, 18.7f, 0.35f, 0, -0.05f},
        {7.65f, 0, 16.2f, 19.7f, 0.1f, 0, 0}, {8.00f, 0, 16.5f, 19.8f, 0.06f, 0, 0}, {9.20f, 0, 16.5f, 19.8f, 0.06f, 0, 0},
    };

    /** The hips at time u: where they are (x, y, z) and their turn (x, y, z), into out. */
    public static void root(float u, float[] out) {
        track(ROOT, u, out);
    }

    /** How far past the glass the hips are at time u, in blocks. */
    public static float hips(float u) {
        float[] r = new float[6];
        track(ROOT, u, r);
        return r[2] / BLOCK;
    }

    /**
     * Where the server keeps the zombie at time u, having started the climb from along0 blocks past the glass and side0
     * to its left of the window's middle: under its hips, stepping there over the first half second. Blocks: {along,
     * side}.
     */
    public static void place(float u, float along0, float side0, float[] out) {
        float s = smooth((u - START) / SETTLE), path = hips(u), from = hips(START);
        out[0] = path + (along0 - from) * (1 - s);
        out[1] = side0 * (1 - s);
    }

    /** Which hit-shape step time u falls in (0 at the wall to STEPS.length - 2 standing inside). */
    public static int step(float u) {
        int i = 0;
        while (i + 2 < STEPS.length && u >= STEPS[i + 1]) {
            i++;
        }
        return i;
    }

    /**
     * A keyed track ([time, values...] rows) at time t, gliding through its keys (Catmull-Rom, with tangents over
     * the keys' own spacing, as breakin.js's brkArr).
     */
    public static void track(float[][] track, float t, float[] out) {
        int n = track[0].length - 1, i = key(track, t);
        if (i + 1 >= track.length || t <= track[0][0]) {
            System.arraycopy(track[t <= track[0][0] ? 0 : i], 1, out, 0, n);
            return;
        }
        float[] p1 = track[i], p2 = track[i + 1], p0 = track[Math.max(0, i - 1)], p3 = track[Math.min(track.length - 1, i + 2)];
        float t1 = p1[0], t2 = p2[0], t0 = p0[0], t3 = p3[0];
        float h = t2 - t1, s = (t - t1) / h, s2 = s * s, s3 = s2 * s;
        for (int k = 1; k <= n; k++) {
            float m1 = (p2[k] - p0[k]) / Math.max(1e-6f, t2 - t0) * h, m2 = (p3[k] - p1[k]) / Math.max(1e-6f, t3 - t1) * h;
            out[k - 1] = (2 * s3 - 3 * s2 + 1) * p1[k] + (s3 - 2 * s2 + s) * m1 + (-2 * s3 + 3 * s2) * p2[k] + (s3 - s2) * m2;
        }
    }

    /** The last key at or before t. */
    public static int key(float[][] track, float t) {
        int i = 0;
        while (i + 1 < track.length && track[i + 1][0] <= t) {
            i++;
        }
        return i;
    }

    public static float smooth(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3 - 2 * x);
    }

    private ClimbPath() {
    }
}
