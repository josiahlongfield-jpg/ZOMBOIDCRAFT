package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One train line as the county lays it: a closed loop sampled a block apart, each sample's
 * position, the top of its track bed (the level the wheels run at, as the trains' own route has
 * it) and what carries it there: the ground, a viaduct or bridge, or a tunnel. Chunks ask it what
 * runs past each column ({@link #nearest}); the trains are given {@link #points}, the loop's
 * corners and the points along its curves, from which they lay the same line.
 */
public final class Track {
    public enum Kind { RAIL, SUBWAY }

    /** On the ground (an embankment or cutting where the land isn't level). */
    static final byte GROUND = 0;
    /** On a viaduct or a bridge: the island's el, the river crossings, the ramps up to them. */
    static final byte VIADUCT = 1;
    /** Under the streets. */
    static final byte TUNNEL = 2;

    /** Samples are this far apart along the loop. */
    static final double STEP = 1;
    private static final int CELL = 16;

    /** A stop: its name, the place on the plan it serves, the samples its platform runs along, and which side it's on. */
    public record Stop(String name, String spot, int from, int to, int side) {
        public int middle() {
            return (from + to) >>> 1;
        }
    }

    /** What runs past a column: the nearest sample, and how far to the side (positive to the right, going the way trains run). */
    static final class Hit {
        int i = -1;
        double offset, distance = Double.MAX_VALUE;
    }

    public final String id, name;
    public final Kind kind;
    final float[] xs, zs, ys;
    /** Direction of travel at each sample, as a unit vector. */
    final float[] tx, tz;
    final byte[] mode;
    /** Over a city street: half its road's width (the el's columns stand on its curbs); 0 off the grid. */
    final byte[] street;
    /** Index of the stop whose platform a sample is beside, or -1. */
    final short[] stopAt;
    final int n;
    public final List<Stop> stops = new ArrayList<>();
    /** What the trains run on: {x, bed top y, z}, a few blocks apart along curves, further on the straights. */
    public final List<double[]> points = new ArrayList<>();
    /**
     * Where roads cross on the level: {x, bed top y, z, the road's direction x and z (times 1000),
     * its half width, 0 for a county road or 1 for a city street}.
     */
    public final List<int[]> crossings = new ArrayList<>();
    private final Map<Long, int[]> cells = new HashMap<>();
    private final int reach;

    Track(String id, String name, Kind kind, float[] xs, float[] zs, float[] ys, byte[] mode, byte[] street, int reach) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.xs = xs;
        this.zs = zs;
        this.ys = ys;
        this.mode = mode;
        this.street = street;
        this.reach = reach;
        n = xs.length;
        tx = new float[n];
        tz = new float[n];
        for (int i = 0; i < n; i++) {
            int a = (i + n - 1) % n, b = (i + 1) % n;
            double dx = xs[b] - xs[a], dz = zs[b] - zs[a], len = Math.max(1e-9, Math.hypot(dx, dz));
            tx[i] = (float) (dx / len);
            tz[i] = (float) (dz / len);
        }
        stopAt = new short[n];
        java.util.Arrays.fill(stopAt, (short) -1);
        Map<Long, List<Integer>> build = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (int cx = Math.floorDiv((int) Math.floor(xs[i]) - reach, CELL); cx <= Math.floorDiv((int) Math.floor(xs[i]) + reach, CELL); cx++) {
                for (int cz = Math.floorDiv((int) Math.floor(zs[i]) - reach, CELL); cz <= Math.floorDiv((int) Math.floor(zs[i]) + reach, CELL); cz++) {
                    build.computeIfAbsent(County.key(cx, cz), k -> new ArrayList<>()).add(i);
                }
            }
        }
        build.forEach((k, v) -> cells.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
    }

    int wrap(int i) {
        return Math.floorMod(i, n);
    }

    /** True if the line passes within its reach of the 16-block cell holding (x, z). */
    boolean near(int x, int z) {
        return cells.containsKey(County.key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
    }

    /** The samples within reach of the cell holding (x, z), or null. */
    int[] candidates(int x, int z) {
        return cells.get(County.key(Math.floorDiv(x, CELL), Math.floorDiv(z, CELL)));
    }

    /** Finds the nearest sample among the candidates to the column's middle. */
    Hit nearest(int[] cand, double x, double z, Hit hit) {
        hit.i = -1;
        hit.distance = Double.MAX_VALUE;
        if (cand == null) {
            return hit;
        }
        double best = Double.MAX_VALUE;
        int at = -1;
        for (int i : cand) {
            double dx = x - xs[i], dz = z - zs[i], d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                at = i;
            }
        }
        if (at < 0) {
            return hit;
        }
        hit.i = at;
        double ox = x - xs[at], oz = z - zs[at];
        double along = ox * tx[at] + oz * tz[at];
        hit.offset = -ox * tz[at] + oz * tx[at];
        hit.distance = Math.abs(hit.offset);
        if (Math.abs(along) > 0.75) {
            // past the end of what the samples cover here (a gap at a cell's edge): measure straight
            hit.distance = Math.sqrt(best);
        }
        return hit;
    }

    double length() {
        return n * STEP;
    }

    /** The samples, a block apart round the loop: how many, and each one's x, bed top and z. */
    public int samples() {
        return n;
    }

    public double x(int i) {
        return xs[i];
    }

    public double y(int i) {
        return ys[i];
    }

    public double z(int i) {
        return zs[i];
    }

    /** The point on the platform of a stop the trains are given: its middle, a few blocks out on its side. */
    public double[] stopPoint(Stop st) {
        int m = st.middle();
        double o = 3.5 * st.side();
        return new double[] {xs[m] - tz[m] * o, zs[m] + tx[m] * o};
    }

    /** The top of the bed beside a stop's platform (the platform's own top is a block higher). */
    public double bedTop(Stop st) {
        return ys[st.middle()];
    }

    /** Index of the sample nearest (x, z) anywhere along the loop. */
    int project(double x, double z) {
        double best = Double.MAX_VALUE;
        int at = 0;
        for (int i = 0; i < n; i++) {
            double d = (xs[i] - x) * (xs[i] - x) + (zs[i] - z) * (zs[i] - z);
            if (d < best) {
                best = d;
                at = i;
            }
        }
        return at;
    }

    int reach() {
        return reach;
    }
}
