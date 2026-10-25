package com.zomboidcraft.world.county;

/**
 * Seeded 2D gradient noise (simplex) with fractal and ridged sums. Pure Java so the preview tool
 * can run the same terrain outside the game. Values are roughly in [-1, 1].
 */
public final class Noise {
    private static final double F2 = 0.5 * (Math.sqrt(3) - 1), G2 = (3 - Math.sqrt(3)) / 6;
    private static final double[][] GRAD = new double[16][];

    static {
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            GRAD[i] = new double[] {Math.cos(a), Math.sin(a)};
        }
    }

    private final short[] perm = new short[512];

    public Noise(long seed) {
        short[] p = new short[256];
        for (short i = 0; i < 256; i++) {
            p[i] = i;
        }
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = mix(s + i);
            int j = (int) Math.floorMod(s, i + 1);
            short t = p[i];
            p[i] = p[j];
            p[j] = t;
        }
        for (int i = 0; i < 512; i++) {
            perm[i] = p[i & 255];
        }
    }

    /** A well-mixed 64-bit hash (splitmix64 finaliser). */
    public static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /** A hash of a seed and two coordinates. */
    public static long hash(long seed, long a, long b) {
        return mix(seed ^ mix(a * 0x9E3779B97F4A7C15L ^ mix(b + 0x632BE59BD9B4E019L)));
    }

    /** A uniform double in [0, 1) from a hash. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public double get(double x, double y) {
        double s = (x + y) * F2;
        int i = (int) Math.floor(x + s), j = (int) Math.floor(y + s);
        double t = (i + j) * G2;
        double x0 = x - (i - t), y0 = y - (j - t);
        int i1 = x0 > y0 ? 1 : 0, j1 = 1 - i1;
        double x1 = x0 - i1 + G2, y1 = y0 - j1 + G2;
        double x2 = x0 - 1 + 2 * G2, y2 = y0 - 1 + 2 * G2;
        int ii = i & 255, jj = j & 255;
        return 70 * (corner(perm[ii + perm[jj]], x0, y0)
                + corner(perm[ii + i1 + perm[jj + j1]], x1, y1)
                + corner(perm[ii + 1 + perm[jj + 1]], x2, y2));
    }

    private static double corner(int g, double x, double y) {
        double t = 0.5 - x * x - y * y;
        if (t < 0) {
            return 0;
        }
        double[] d = GRAD[g & 15];
        t *= t;
        return t * t * (d[0] * x + d[1] * y);
    }

    /** Fractal sum over octaves; scale is the size of the largest features in blocks. */
    public double fbm(double x, double z, double scale, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1 / scale;
        for (int o = 0; o < octaves; o++) {
            sum += amp * get(x * f + o * 17.3, z * f - o * 31.7);
            norm += amp;
            amp *= 0.5;
            f *= 2.03;
        }
        return sum / norm;
    }

    /** 1 on the ridge lines of the noise, falling to 0 away from them. */
    public double ridge(double x, double z, double scale, int octaves) {
        return 1 - Math.abs(fbm(x, z, scale, octaves));
    }
}
