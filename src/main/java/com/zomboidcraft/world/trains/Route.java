package com.zomboidcraft.world.trains;

import java.util.ArrayList;
import java.util.List;

/**
 * A line's track: a closed loop through its control points, corners rounded to a radius, sampled
 * every {@link #STEP} blocks along its length. Distance along it ({@code s}) runs the way trains go
 * and wraps at {@link #length()}. y is the top of the track bed, the same as the control points;
 * it changes evenly along the path between them.
 */
public final class Route {
    public static final double STEP = 0.25;

    private final float[] xs, ys, zs;
    /** Fastest a train may take each sample, in blocks a second, from how tight the curve is there. */
    private final float[] limit;
    private final double length;
    private final int n;

    /** points: {x, y, z} corners of the loop in order; radius: how far corners are rounded. */
    public Route(List<double[]> points, double radius, double lateral) {
        List<double[]> dense = dense(points, radius);
        // resample at an even step
        double total = 0;
        double[] run = new double[dense.size() + 1];
        for (int i = 0; i < dense.size(); i++) {
            double[] a = dense.get(i), b = dense.get((i + 1) % dense.size());
            run[i] = total;
            total += Math.hypot(b[0] - a[0], b[2] - a[2]);
        }
        run[dense.size()] = total;
        n = Math.max(8, (int) Math.round(total / STEP));
        length = n * STEP;
        double scale = total / length;
        xs = new float[n];
        ys = new float[n];
        zs = new float[n];
        int seg = 0;
        for (int i = 0; i < n; i++) {
            double at = i * STEP * scale;
            while (seg < dense.size() - 1 && run[seg + 1] < at) {
                seg++;
            }
            double[] a = dense.get(seg), b = dense.get((seg + 1) % dense.size());
            double span = run[seg + 1] - run[seg];
            double t = span <= 1e-9 ? 0 : (at - run[seg]) / span;
            xs[i] = (float) (a[0] + (b[0] - a[0]) * t);
            ys[i] = (float) (a[1] + (b[1] - a[1]) * t);
            zs[i] = (float) (a[2] + (b[2] - a[2]) * t);
        }
        // how fast each part may be taken: a = v^2 / r, so v = sqrt(a * r), from the turn over 4 blocks
        limit = new float[n];
        int k = (int) Math.round(2 / STEP);
        for (int i = 0; i < n; i++) {
            double h0 = heading(i - k), h1 = heading(i + k - 1);
            double turn = Math.abs(wrap(h1 - h0));
            double r = turn < 1e-4 ? 1e9 : 4 / turn;
            limit[i] = (float) Math.min(1000, Math.sqrt(lateral * r));
        }
    }

    public double length() {
        return length;
    }

    public double wrapS(double s) {
        s %= length;
        return s < 0 ? s + length : s;
    }

    /** How far ahead {@code to} is from {@code from}, going the way trains run (0 to length). */
    public double ahead(double from, double to) {
        return wrapS(to - from);
    }

    /** Signed shortest difference to - from, either way round the loop. */
    public double diff(double from, double to) {
        double d = wrapS(to - from);
        return d > length / 2 ? d - length : d;
    }

    public double x(double s) {
        return lerp(xs, s);
    }

    public double y(double s) {
        return lerp(ys, s);
    }

    public double z(double s) {
        return lerp(zs, s);
    }

    /** The speed limit (blocks a second) at s. */
    public double limit(double s) {
        return limit[(int) (wrapS(s) / STEP) % n];
    }

    /** The slowest limit over [from, from + span]. */
    public double slowest(double from, double span) {
        double min = 1000;
        for (double d = 0; d <= span; d += 1) {
            min = Math.min(min, limit(from + d));
        }
        return min;
    }

    /** The point on the line nearest (x, z), as distance along it. */
    public double project(double x, double z) {
        double best = Double.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < n; i++) {
            double d = (xs[i] - x) * (xs[i] - x) + (zs[i] - z) * (zs[i] - z);
            if (d < best) {
                best = d;
                at = i;
            }
        }
        return at * STEP;
    }

    /** Horizontal distance from (x, z) to the line at s. */
    public double distance(double s, double x, double z) {
        return Math.hypot(x(s) - x, z(s) - z);
    }

    /** Which side of the line (x, z) is, looking the way trains go: +1 the car's +x side, -1 the other. */
    public int side(double s, double x, double z) {
        double dx = x(s + 1) - x(s - 1), dz = z(s + 1) - z(s - 1);
        double ox = x - x(s), oz = z - z(s);
        // local +x of a car facing (dx, dz) is (dz, -dx) turned: see Pose.toWorld
        double c = dz * ox - dx * oz;
        return c >= 0 ? 1 : -1;
    }

    private double heading(int i) {
        int a = Math.floorMod(i, n), b = Math.floorMod(i + 1, n);
        return Math.atan2(zs[b] - zs[a], xs[b] - xs[a]);
    }

    private double lerp(float[] v, double s) {
        double f = wrapS(s) / STEP;
        int i = (int) f;
        double t = f - i;
        i %= n;
        return v[i] + (v[(i + 1) % n] - v[i]) * t;
    }

    private static double wrap(double a) {
        while (a > Math.PI) {
            a -= Math.PI * 2;
        }
        while (a < -Math.PI) {
            a += Math.PI * 2;
        }
        return a;
    }

    /** The loop as a fine polyline: straights between corners, each corner an arc. */
    private static List<double[]> dense(List<double[]> pts, double radius) {
        int m = pts.size();
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            double[] p = pts.get(i), prev = pts.get((i + m - 1) % m), next = pts.get((i + 1) % m);
            double inX = p[0] - prev[0], inZ = p[2] - prev[2], outX = next[0] - p[0], outZ = next[2] - p[2];
            double inL = Math.hypot(inX, inZ), outL = Math.hypot(outX, outZ);
            if (inL < 1e-6 || outL < 1e-6) {
                continue;
            }
            inX /= inL;
            inZ /= inL;
            outX /= outL;
            outZ /= outL;
            double cross = inX * outZ - inZ * outX, dot = inX * outX + inZ * outZ;
            double theta = Math.atan2(Math.abs(cross), dot);    // how far it turns
            if (theta < 1e-3) {
                out.add(p.clone());
                continue;
            }
            double tan = Math.tan(theta / 2);
            double t = Math.min(radius * tan, Math.min(inL, outL) * 0.48);
            double r = t / tan;
            double ax = p[0] - inX * t, az = p[2] - inZ * t, bx = p[0] + outX * t, bz = p[2] + outZ * t;
            double ay = p[1] + (prev[1] - p[1]) * t / inL, by = p[1] + (next[1] - p[1]) * t / outL;
            // the centre is r to the inside of the incoming line at A
            double sign = Math.signum(cross);
            double cx = ax - inZ * r * sign, cz = az + inX * r * sign;
            double a0 = Math.atan2(az - cz, ax - cx), a1 = Math.atan2(bz - cz, bx - cx);
            double sweep = wrap(a1 - a0);
            int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) * r / 0.5));
            for (int k = 0; k <= steps; k++) {
                double f = (double) k / steps, a = a0 + sweep * f;
                out.add(new double[] {cx + Math.cos(a) * r, ay + (by - ay) * f, cz + Math.sin(a) * r});
            }
        }
        return out;
    }
}
