import java.util.*;

/**
 * Fits the 12 hit capsules of one pose to the drawn body: shoots a dense grid of rays at many sampled frames of the
 * pose (gait cycle, attack, look) from many directions and nudges each capsule's ends and radius to catch as many
 * rays that touch the body (on the right part) as it can, without catching many that miss. Scored incrementally:
 * moving one capsule only rescans the rays that pass near it.
 */
public class Fit {
    final float[] ray;
    final int[] meshPart;
    final int n;
    final int gone;
    float[][] t;
    final byte[] win;
    final float[] val;

    static final double MARGIN = 0.3, PAD = 0.12;
    int M;
    int[] partOf;

    Fit(List<HitFit.Body> bodies, int gone) {
        this.gone = gone;
        List<float[]> rs = new ArrayList<>();
        List<Integer> ps = new ArrayList<>();
        int G = HitFit.G;
        int m = (int) Math.ceil(MARGIN / HitFit.PIX);
        for (HitFit.Body b : bodies) {
            double[] c = HitFit.centre(b);
            for (double[] view : HitFit.VIEWS) {
                double[][] B = HitFit.basis(view[0], view[1]);
                int[] id = HitFit.render(b, B, c);
                boolean[] near = new boolean[G * G];
                for (int y = 0; y < G; y++) for (int x = 0; x < G; x++) {
                    if (id[y * G + x] == -1) continue;
                    for (int yy = Math.max(0, y - m); yy <= Math.min(G - 1, y + m); yy += 2)
                        for (int xx = Math.max(0, x - m); xx <= Math.min(G - 1, x + m); xx += 2) near[yy * G + xx] = true;
                }
                double[] d = B[0], u = B[1], v = B[2];
                for (int y = 0; y < G; y++) for (int x = 0; x < G; x++) {
                    int i = y * G + x;
                    if (!near[i] && id[i] == -1) continue;
                    double su = (x + 0.5 - G / 2.0) * HitFit.PIX, sv = (y + 0.5 - G / 2.0) * HitFit.PIX;
                    rs.add(new float[] {(float) (c[0] + su * u[0] + sv * v[0] - 4 * d[0]), (float) (c[1] + su * u[1] + sv * v[1] - 4 * d[1]),
                            (float) (c[2] + su * u[2] + sv * v[2] - 4 * d[2]), (float) d[0], (float) d[1], (float) d[2]});
                    ps.add(id[i]);
                }
            }
        }
        n = rs.size();
        ray = new float[n * 6];
        meshPart = new int[n];
        for (int i = 0; i < n; i++) {
            System.arraycopy(rs.get(i), 0, ray, i * 6, 6);
            meshPart[i] = ps.get(i);
        }
        t = null;
        win = new byte[n];
        val = new float[n];
    }

    /** What a ray is worth: mesh part mp (-1 nothing there), caught by capsule w (-1 none). */
    double value(int mp, int w) {
        int p = w < 0 ? -1 : partOf[w];
        if (mp == -1) return p < 0 ? 0 : p == 0 ? -2 : -1;
        if (p < 0) return 0;
        if (mp == 0) return p == 0 ? 2 : 0;
        if (HitFit.agree(mp, p)) return 1;
        return p == 0 ? -1.5 : 0.3;
    }

    double enterRay(float[] caps, int k, int i) {
        int o = i * 6;
        return HitFit.enter(ray[o], ray[o + 1], ray[o + 2], ray[o + 3], ray[o + 4], ray[o + 5], caps, k * 8 + 1);
    }

    /** Rays passing within the capsule's radius plus PAD of its core. */
    int[] near(float[] caps, int k) {
        float[] fat = caps.clone();
        fat[k * 8 + 7] += (float) PAD;
        int[] out = new int[n];
        int c = 0;
        for (int i = 0; i < n; i++) if (enterRay(fat, k, i) >= 0) out[c++] = i;
        return Arrays.copyOf(out, c);
    }

    double total;

    void init(float[] caps) {
        M = caps.length / 8;
        partOf = new int[M];
        t = new float[M][n];
        for (int k = 0; k < M; k++) {
            partOf[k] = (int) caps[k * 8];
            for (int i = 0; i < n; i++) t[k][i] = (gone >> partOf[k] & 1) != 0 ? -1f : (float) enterRay(caps, k, i);
        }
        total = 0;
        for (int i = 0; i < n; i++) {
            int w = winner(i, -1, 0);
            win[i] = (byte) w;
            val[i] = (float) value(meshPart[i], w);
            total += val[i];
        }
    }

    /** The nearest capsule along ray i, with capsule k's entry replaced by tk (k = -1: none replaced). */
    int winner(int i, int k, double tk) {
        int w = -1;
        double best = Double.MAX_VALUE;
        for (int j = 0; j < M; j++) {
            double tj = j == k ? tk : t[j][i];
            if (tj >= 0 && tj < best) { best = tj; w = j; }
        }
        return w;
    }

    float[] scratch = new float[0];

    /** Score change if capsule k took its current params in caps (rays in idx only). */
    double delta(float[] caps, int k, int[] idx) {
        if (scratch.length < idx.length) scratch = new float[idx.length];
        double d = 0;
        for (int a = 0; a < idx.length; a++) {
            int i = idx[a];
            double tk = enterRay(caps, k, i);
            scratch[a] = (float) tk;
            int w = winner(i, k, tk);
            d += value(meshPart[i], w) - val[i];
        }
        return d;
    }

    void accept(int k, int[] idx, double d) {
        for (int a = 0; a < idx.length; a++) {
            int i = idx[a];
            t[k][i] = scratch[a];
            int w = winner(i, -1, 0);
            win[i] = (byte) w;
            val[i] = (float) value(meshPart[i], w);
        }
        total += d;
    }

    float[] fit(float[] start) {
        float[] caps = start.clone();
        init(caps);
        double step = 0.04;
        int[][] idx = new int[M][];
        float[][] built = new float[M][];
        while (step > 0.004) {
            boolean improved = false;
            for (int k = 0; k < M; k++) {
                if ((gone >> partOf[k] & 1) != 0) continue;
                for (int p = 0; p < 7; p++) {
                    double unit = step * (p == 6 ? 0.5 : 1);
                    for (int dir : new int[] {1, -1}) {
                        int id = k * 8 + 1 + p;
                        boolean moved = false;
                        while (true) {
                            if (idx[k] == null || drift(caps, built[k], k) > PAD * 0.5 - unit) {
                                idx[k] = near(caps, k);
                                built[k] = caps.clone();
                            }
                            float old = caps[id];
                            caps[id] = (float) (old + dir * unit);
                            if (p == 6 && caps[id] < 0.03f) { caps[id] = old; break; }
                            double d = delta(caps, k, idx[k]);
                            if (d > 1e-9) {
                                accept(k, idx[k], d);
                                moved = true;
                                improved = true;
                            } else {
                                caps[id] = old;
                                break;
                            }
                        }
                        if (moved) break;
                    }
                }
            }
            if (!improved) step /= 2;
        }
        return caps;
    }

    static double drift(float[] now, float[] then, int k) {
        int o = k * 8 + 1;
        double a = Math.sqrt(sq(now[o] - then[o]) + sq(now[o + 1] - then[o + 1]) + sq(now[o + 2] - then[o + 2]));
        double b = Math.sqrt(sq(now[o + 3] - then[o + 3]) + sq(now[o + 4] - then[o + 4]) + sq(now[o + 5] - then[o + 5]));
        return Math.max(a, b) + Math.max(0, now[o + 6] - then[o + 6]);
    }

    static double sq(double x) { return x * x; }
}
