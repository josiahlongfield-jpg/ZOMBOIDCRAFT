package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;

/**
 * The Glass Line (Josiah 2026-10-27: "an underwater subway tube that's see through", picked as its
 * own shuttle): in 1986 the harbour board sank a pair of glass tubes across the South Arm, from
 * under the subway's Mercy General station on the island to the Eastside quays, instead of a
 * fourth bridge the ships would have to pass under. The line is one closed loop: out along one
 * track, round a turning loop under Eastside, back along the other twelve blocks to its left and
 * round a turning loop under the island. Both stations are island platforms between the two
 * tracks, so the shuttle stops at each going both ways.
 *
 * On land the line is a subway tunnel a flight under the subway's ({@link #Y}); under the water it
 * runs in the glass tubes (RailGen.tube, the gl_ blocks of tools/city/glassline.py), dipping at the
 * grade to {@link #DEEP} mid-channel, in a channel dredged for it. The route was drawn on the
 * route map Josiah was sent (project files previews/rail/48-glass-line-route-map.jpg).
 */
final class GlassLine {
    static final String ID = "glass";
    /** The bed top on land and at the stations: one flight under the subway's. */
    static final int Y = Railway.SUBWAY_Y - 10;
    /** The bed top mid-channel, as deep as the grade takes it. */
    static final int DEEP = 40;
    private static final double GRADE = 0.03;
    /** The two tracks' centrelines are this far apart. */
    static final int GAP = 12;
    /** Half a platform (the subway's 38, and room to stop). */
    private static final int HALF = 44;

    private GlassLine() {
    }

    /** Lays out the line (the plan is fixed: the planned county's island and Eastside). */
    static Track build(Terrain terrain) {
        // the loop, as drawn on the route map: track A first, east from the island's turning loop
        Turtle a = new Turtle(2790, -400, 0);
        a.toX(2908);                                       // under the subway's Mercy General platform
        a.turn(40, 90);                                    // right, south toward the water
        a.toZ(-80);
        a.turn(100, -90);                                  // left, east under the el's bridge
        a.toX(3230);
        double r2 = 113 / (2 * (1 - Math.cos(Math.toRadians(50))));   // the S-bend over to the south bank, 113 across
        a.turn(r2, 50);
        a.turn(r2, -50);
        a.fwd(150);                                        // along the quay: Eastside Quay
        a.turn(64, 90);                                    // right, under Eastside
        a.fwd(40);
        a.turn(40, 60);                                    // the mainland's turning loop
        a.turn(52, -300);
        a.turn(40, 60);
        a.fwd(40);
        a.turn(64 + GAP, -90);                             // track B: left, west along the quay
        a.fwd(150);
        a.turn(r2 - GAP, 50);
        a.turn(r2 + GAP, -50);
        a.toX(3048);
        a.turn(100 - GAP, 90);
        a.toZ(-360);
        a.turn(40 + GAP, -90);
        a.toX(2790);                                       // under Mercy General again
        a.turn(40, 60);                                    // the island's turning loop
        a.turn(52, -300);
        a.turn(40, 60);
        List<double[]> pts = a.pts;
        pts.remove(pts.size() - 1);                        // back where it started

        // a block apart
        int dn = pts.size();
        double[] cum = new double[dn + 1];
        for (int i = 1; i <= dn; i++) {
            double[] p = pts.get(i % dn), q = pts.get(i - 1);
            cum[i] = cum[i - 1] + Math.hypot(p[0] - q[0], p[1] - q[1]);
        }
        int n = (int) Math.round(cum[dn] / Track.STEP);
        double step = cum[dn] / n;
        float[] xs = new float[n], zs = new float[n], ys = new float[n];
        boolean[] arc = new boolean[n];
        int seg = 0;
        for (int i = 0; i < n; i++) {
            double at = i * step;
            while (seg < dn - 1 && cum[seg + 1] < at) {
                seg++;
            }
            double[] p = pts.get(seg), q = pts.get((seg + 1) % dn);
            double span = cum[seg + 1] - cum[seg], t = span <= 1e-9 ? 0 : (at - cum[seg]) / span;
            xs[i] = (float) (p[0] + (q[0] - p[0]) * t);
            zs[i] = (float) (p[1] + (q[1] - p[1]) * t);
            arc[i] = q[2] > 0;
        }

        // the stations: an island platform between the tracks, each track's stop on its left
        double[][] at = {{2850, -400, 2850, -400 - GAP}, {3547, 133, 3547, 133 - GAP}};
        String[] key = {"mercy", "quay"}, name = {"Mercy General", "Eastside Quay"};
        List<int[]> platforms = new ArrayList<>();   // {station, from, to}
        int[][] mids = new int[2][2];
        for (int s = 0; s < 2; s++) {
            for (int side = 0; side < 2; side++) {
                int m = nearest(xs, zs, at[s][side * 2], at[s][side * 2 + 1]);
                mids[s][side] = m;
                platforms.add(new int[] {s, m - HALF, m + HALF});
            }
        }

        // in the water: the tubes; on land a tunnel
        byte[] mode = new byte[n];
        boolean[] level = new boolean[n];
        for (int i = 0; i < n; i++) {
            int p = Math.floorMod(i - 1, n), q = (i + 1) % n;
            double tx = xs[q] - xs[p], tz = zs[q] - zs[p], len = Math.max(1e-9, Math.hypot(tx, tz));
            boolean wet = false;
            for (int o = -4; o <= 4; o += 4) {
                wet |= Terrain.isWater(terrain.height(xs[i] - tz / len * o, zs[i] + tx / len * o));
            }
            mode[i] = wet ? Track.TUBE : Track.TUNNEL;
        }
        settle(mode, Track.TUBE, 16);
        settle(mode, Track.TUNNEL, 16);
        for (int i = 0; i < n; i++) {
            level[i] = mode[i] != Track.TUBE;
        }
        for (int[] pl : platforms) {
            for (int i = pl[1] - 12; i <= pl[2] + 12; i++) {
                level[Math.floorMod(i, n)] = true;
            }
        }
        // the bed: level on land and at the stations, dipping at the grade under the water
        int far = 1 << 20;
        int[] d = new int[n];
        java.util.Arrays.fill(d, far);
        for (int pass = 0; pass < 2; pass++) {
            for (int k = 0; k < 2 * n; k++) {
                int i = k % n, p = Math.floorMod(i - 1, n);
                d[i] = level[i] ? 0 : Math.min(d[i], d[p] + 1);
            }
            for (int k = 2 * n - 1; k >= 0; k--) {
                int i = k % n, p = (i + 1) % n;
                d[i] = level[i] ? 0 : Math.min(d[i], d[p] + 1);
            }
        }
        for (int i = 0; i < n; i++) {
            ys[i] = (float) Math.max(DEEP, Y - GRADE * Math.min(d[i], 1000));
        }

        Track track = new Track(ID, "Glass Line", Track.Kind.SUBWAY, xs, zs, ys, mode, new byte[n], 32);
        platforms.sort((p, q) -> Integer.compare(p[1], q[1]));
        for (int[] pl : platforms) {
            short idx = (short) track.stops.size();
            track.stops.add(new Track.Stop(name[pl[0]], key[pl[0]], pl[1], pl[2], -1));
            for (int i = pl[1]; i <= pl[2]; i++) {
                track.stopAt[Math.floorMod(i, n)] = idx;
            }
        }
        for (int s = 0; s < 2; s++) {
            // both stations stand on straights running east and west: the island squared to the
            // blocks, its middle on a block's corner, so what's built round it lines up column by column
            int ma = mids[s][0], mb = mids[s][1];
            double ax = Math.round(track.tx[ma]), az = Math.round(track.tz[ma]);
            track.islands.add(new Track.Island(key[s], Math.round((xs[ma] + xs[mb]) / 2.0), Math.round((zs[ma] + zs[mb]) / 2.0), ax, az,
                    HALF, Y));
        }
        // the points the trains run on: the curves every few blocks, the slopes every eight, the rest every couple of dozen
        int last = -100;
        for (int i = 0; i < n; i++) {
            boolean corner = arc[i] != arc[Math.floorMod(i - 1, n)];
            boolean slope = ys[i] != ys[Math.floorMod(i - 1, n)] || ys[i] != ys[(i + 1) % n];
            if (corner || arc[i] && i - last >= 3 || slope && i - last >= 8 || i - last >= 24) {
                track.points.add(new double[] {round3(xs[i]), round3(ys[i]), round3(zs[i])});
                last = i;
            }
        }
        System.out.printf("Laid the Glass Line: %d blocks round, %d stops, %d points%n", n, track.stops.size(), track.points.size());
        return track;
    }

    private static int nearest(float[] xs, float[] zs, double x, double z) {
        int at = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            double d = (xs[i] - x) * (xs[i] - x) + (zs[i] - z) * (zs[i] - z);
            if (d < best) {
                best = d;
                at = i;
            }
        }
        return at;
    }

    /** Runs of the given mode shorter than len between runs of the other take the other's. */
    private static void settle(byte[] mode, byte which, int len) {
        int n = mode.length;
        for (int i = 0; i < n; i++) {
            if (mode[i] != which || mode[Math.floorMod(i - 1, n)] == which) {
                continue;
            }
            int k = 0;
            while (k < n && mode[(i + k) % n] == which) {
                k++;
            }
            if (k < len && k < n) {
                byte other = mode[Math.floorMod(i - 1, n)];
                for (int q = 0; q < k; q++) {
                    mode[(i + q) % n] = other;
                }
            }
        }
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** Draws the loop: straights and arcs, a point about a block apart ({x, z, 1 on an arc}). */
    private static final class Turtle {
        double x, z, t;
        final List<double[]> pts = new ArrayList<>();

        Turtle(double x, double z, double deg) {
            this.x = x;
            this.z = z;
            t = Math.toRadians(deg);
            pts.add(new double[] {x, z, 0});
        }

        void fwd(double d) {
            int n = Math.max(1, (int) Math.round(d));
            for (int i = 0; i < n; i++) {
                x += Math.cos(t) * d / n;
                z += Math.sin(t) * d / n;
                pts.add(new double[] {x, z, 0});
            }
        }

        /** Turns through deg on a curve of radius r: positive to the right (z is south). */
        void turn(double r, double deg) {
            double a = Math.toRadians(deg);
            int n = Math.max(2, (int) (Math.abs(a) * r));
            for (int i = 0; i < n; i++) {
                t += a / n;
                double step = Math.abs(a) / n * r;
                x += Math.cos(t - a / n / 2) * step;
                z += Math.sin(t - a / n / 2) * step;
                pts.add(new double[] {x, z, 1});
            }
        }

        void toX(double to) {
            fwd(Math.abs(to - x) / Math.max(1e-9, Math.abs(Math.cos(t))));
        }

        void toZ(double to) {
            fwd(Math.abs(to - z) / Math.max(1e-9, Math.abs(Math.sin(t))));
        }
    }
}
