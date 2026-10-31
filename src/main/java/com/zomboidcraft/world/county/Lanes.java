package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A roadway a highway place draws for itself (the interstate through the rest area and the toll
 * plaza, the interchange's mainlines and ramps): its centre line in the place's own coordinates,
 * the height of its surface along it (0 is the place's ground), its half width and how it is
 * marked, and the stretches it crosses on a bridge. {@link #paint} draws a set together, so where
 * a ramp runs into the highway they pave as one and where one passes over another each is drawn
 * at its own height: on an embankment with grass slopes and a guardrail, or on a deck with its
 * parapets, girders and piers.
 *
 * The county's own roads (CountyGen.road) stop at the place's gates; these carry on from there
 * the same: asphalt, the double yellow down the middle of the highway and its dashed lanes.
 */
final class Lanes {
    enum Style {
        /** The undivided four-lane highway: double yellow, a dashed line down each side. */
        HIGHWAY,
        /** A ramp, one way: yellow edge line on the left, white on the right. */
        RAMP,
        /** Paved and nothing painted: the builder marks it (the toll plaza's lanes). */
        PLAIN
    }

    /** Points every block along the centre line: x, z, surface y, and the distance along. */
    final double[] xs, zs, ys, at;
    final double half;
    final Style style;
    /** Stretches on a bridge, as pairs of distances along. */
    private final List<double[]> spans = new ArrayList<>();
    /** Bents under a bridge every this far (along), from its start. */
    int bentEvery = 24;
    /** Guardrail along the top of an embankment higher than this. */
    double railOver = 1.5;
    private final int minX, minZ, maxX, maxZ;

    private Lanes(Style style, double half, List<double[]> pts) {
        this.style = style;
        this.half = half;
        int n = pts.size();
        xs = new double[n];
        zs = new double[n];
        ys = new double[n];
        at = new double[n];
        double a = 1e9, b = 1e9, c = -1e9, d = -1e9;
        for (int i = 0; i < n; i++) {
            double[] q = pts.get(i);
            xs[i] = q[0];
            zs[i] = q[1];
            ys[i] = q[2];
            at[i] = i == 0 ? 0 : at[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            a = Math.min(a, q[0]);
            b = Math.min(b, q[1]);
            c = Math.max(c, q[0]);
            d = Math.max(d, q[1]);
        }
        int m = (int) Math.ceil(half + 4 + Math.max(0, max(ys)));
        minX = (int) Math.floor(a) - m;
        minZ = (int) Math.floor(b) - m;
        maxX = (int) Math.ceil(c) + m;
        maxZ = (int) Math.ceil(d) + m;
    }

    private static double max(double[] v) {
        double m = -1e9;
        for (double x : v) {
            m = Math.max(m, x);
        }
        return m;
    }

    double length() {
        return at[at.length - 1];
    }

    /** Carried on a bridge from a to b along. */
    Lanes bridge(double a, double b) {
        spans.add(new double[] {a, b});
        return this;
    }

    boolean onBridge(double along) {
        for (double[] s : spans) {
            if (along >= s[0] && along <= s[1]) {
                return true;
            }
        }
        return false;
    }

    /** Just off a bridge's end: the abutment, its fill faced in retaining wall. */
    boolean abutment(double along) {
        for (double[] s : spans) {
            if (along > s[0] - 2.5 && along < s[0] || along > s[1] && along < s[1] + 2.5) {
                return true;
            }
        }
        return false;
    }

    /** The point d along: {x, z, y, dx, dz} (dx, dz the unit direction of travel). */
    double[] point(double d) {
        int i = 0;
        while (i < at.length - 2 && at[i + 1] < d) {
            i++;
        }
        double len = Math.max(1e-9, at[i + 1] - at[i]), t = Math.max(0, Math.min(1, (d - at[i]) / len));
        double dx = (xs[i + 1] - xs[i]) / len, dz = (zs[i + 1] - zs[i]) / len;
        return new double[] {xs[i] + (xs[i + 1] - xs[i]) * t, zs[i] + (zs[i + 1] - zs[i]) * t, ys[i] + (ys[i + 1] - ys[i]) * t, dx, dz};
    }

    /** The column s to the right of the centre line (negative: left) at d along: {x, z}. */
    int[] beside(double d, double s) {
        double[] q = point(d);
        return new int[] {(int) Math.floor(q[0] - q[4] * s), (int) Math.floor(q[1] + q[3] * s)};
    }

    /** The nearest point to (x, z): {distance, offset (+ right of travel), along, y, dx, dz}; null if far. */
    double[] nearest(double x, double z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return null;
        }
        double best = Double.MAX_VALUE;
        double[] hit = null;
        for (int i = 0; i + 1 < xs.length; i++) {
            double ax = xs[i], az = zs[i], vx = xs[i + 1] - ax, vz = zs[i + 1] - az;
            double len2 = vx * vx + vz * vz;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
            double px = ax + vx * t, pz = az + vz * t;
            double dd = (x - px) * (x - px) + (z - pz) * (z - pz);
            if (dd < best) {
                best = dd;
                double len = Math.sqrt(len2), dx = len == 0 ? 1 : vx / len, dz = len == 0 ? 0 : vz / len;
                hit = new double[] {Math.sqrt(dd), (x - px) * -dz + (z - pz) * dx, at[i] + t * len, ys[i] + (ys[i + 1] - ys[i]) * t, dx, dz};
            }
        }
        return hit;
    }

    /** Builds a centre line from straights and arcs, every block, the height eased between the points given. */
    static final class Path {
        private final List<double[]> pts = new ArrayList<>();
        /** Indices of the points whose heights were given; the rest are eased between them. */
        private final List<Integer> keys = new ArrayList<>();

        Path(double x, double z, double y) {
            pts.add(new double[] {x, z, y});
            keys.add(0);
        }

        private double[] last() {
            return pts.get(pts.size() - 1);
        }

        Path to(double x, double z, double y) {
            double[] p = last();
            int n = Math.max(1, (int) Math.ceil(Math.hypot(x - p[0], z - p[1])));
            for (int k = 1; k <= n; k++) {
                pts.add(new double[] {p[0] + (x - p[0]) * k / n, p[1] + (z - p[1]) * k / n, Double.NaN});
            }
            last()[2] = y;
            keys.add(pts.size() - 1);
            return this;
        }

        /**
         * Round (cx, cz) at radius r from angle a0 to a1 in degrees (0 east, 90 south: clockwise as
         * seen from above), ending at height y; joined to the path so far with a straight.
         */
        Path arc(double cx, double cz, double r, double a0, double a1, double y) {
            double r0 = Math.toRadians(a0), r1 = Math.toRadians(a1);
            to(cx + Math.cos(r0) * r, cz + Math.sin(r0) * r, Double.NaN);
            keys.remove(keys.size() - 1);
            int n = Math.max(2, (int) Math.ceil(Math.abs(r1 - r0) * r));
            for (int k = 1; k <= n; k++) {
                double a = r0 + (r1 - r0) * k / n;
                pts.add(new double[] {cx + Math.cos(a) * r, cz + Math.sin(a) * r, Double.NaN});
            }
            last()[2] = y;
            keys.add(pts.size() - 1);
            return this;
        }

        /** A smooth bend to (x, z) along the cubic through the two control points, ending at height y. */
        Path curve(double ax, double az, double bx, double bz, double x, double z, double y) {
            double[] p = last();
            double len = Math.hypot(ax - p[0], az - p[1]) + Math.hypot(bx - ax, bz - az) + Math.hypot(x - bx, z - bz);
            int n = Math.max(2, (int) Math.ceil(len));
            for (int k = 1; k <= n; k++) {
                double t = k / (double) n, u = 1 - t;
                double cx = u * u * u * p[0] + 3 * u * u * t * ax + 3 * u * t * t * bx + t * t * t * x;
                double cz = u * u * u * p[1] + 3 * u * u * t * az + 3 * u * t * t * bz + t * t * t * z;
                pts.add(new double[] {cx, cz, Double.NaN});
            }
            last()[2] = y;
            keys.add(pts.size() - 1);
            return this;
        }

        Lanes lanes(Style style, double half) {
            // heights: eased (smoothstep) between the given ones by distance along
            double[] along = new double[pts.size()];
            for (int i = 1; i < pts.size(); i++) {
                along[i] = along[i - 1] + Math.hypot(pts.get(i)[0] - pts.get(i - 1)[0], pts.get(i)[1] - pts.get(i - 1)[1]);
            }
            for (int k = 0; k + 1 < keys.size(); k++) {
                int i0 = keys.get(k), i1 = keys.get(k + 1);
                double y0 = pts.get(i0)[2], y1 = pts.get(i1)[2];
                for (int i = i0 + 1; i < i1; i++) {
                    double t = (along[i] - along[i0]) / Math.max(1e-9, along[i1] - along[i0]);
                    pts.get(i)[2] = y0 + (y1 - y0) * t * t * (3 - 2 * t);
                }
            }
            return new Lanes(style, half, pts);
        }
    }

    // ------------------------------------------------------------------ painting

    private record Hit(Lanes lane, double dist, double off, double along, double y, double dx, double dz) {}

    /**
     * Draws the roadways over the place's w x d footprint: surface, markings, shoulders, the
     * embankments and their slopes, guardrails, and the bridges with their decks, girders, parapets
     * and bents. Run after the ground is laid and before the props go on the road.
     */
    static void paint(BlockSink out, List<Lanes> all, int w, int d, SplittableRandom rng) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                List<Hit> hits = new ArrayList<>();
                for (Lanes l : all) {
                    double[] h = l.nearest(x + 0.5, z + 0.5);
                    if (h != null && h[0] <= l.half + 3 + Math.max(0, h[3])) {
                        hits.add(new Hit(l, h[0], h[1], h[2], h[3], h[4], h[5]));
                    }
                }
                if (hits.isEmpty()) {
                    continue;
                }
                hits.sort((a, b) -> Double.compare(a.y, b.y));
                // levels: roadways within 3 of each other's height pave as one
                List<List<Hit>> levels = new ArrayList<>();
                for (Hit h : hits) {
                    if (levels.isEmpty() || h.y - levels.get(levels.size() - 1).get(0).y >= 3) {
                        levels.add(new ArrayList<>());
                    }
                    levels.get(levels.size() - 1).add(h);
                }
                boolean below = false;
                for (int k = 0; k < levels.size(); k++) {
                    double above = k + 1 < levels.size() ? levels.get(k + 1).get(0).y : 1e9;
                    below |= column(out, levels.get(k), x, z, above, below, rng);
                }
            }
        }
    }

    /** One level's share of a column; true if it paved it. */
    private static boolean column(BlockSink out, List<Hit> level, int x, int z, double above, boolean below, SplittableRandom rng) {
        Hit c = null, edge = null;
        int paving = 0, near = 0;
        for (Hit h : level) {
            if (h.dist <= h.lane.half + 3) {
                near++;
            }
            if (h.dist <= h.lane.half + 0.5) {
                paving++;
                if (c == null || h.dist - h.lane.half < c.dist - c.lane.half) {
                    c = h;
                }
            } else if (edge == null || h.dist - h.lane.half < edge.dist - edge.lane.half) {
                edge = h;
            }
        }
        if (c != null) {
            pave(out, c, paving > 1, x, z, above, rng);
            return true;
        }
        if (edge != null && !(below && !edge.lane.onBridge(edge.along))) {
            // (an embankment's slope never buries the road under it)
            verge(out, edge, x, z, above, near > 1, rng);
        }
        return false;
    }

    private static void pave(BlockSink out, Hit c, boolean shared, int x, int z, double above, SplittableRandom rng) {
        int top = (int) Math.floor(c.y);
        double frac = c.y - top;
        boolean bridge = c.lane.onBridge(c.along);
        out.set(x, top, z, marking(c, shared, rng));
        if (frac >= 0.5) {
            out.set(x, top + 1, z, "asphalt_low");
        }
        int clear = (int) Math.min(top + 5, Math.floor(above) - 2);
        for (int y = top + (frac >= 0.5 ? 2 : 1); y <= clear; y++) {
            out.set(x, y, z, "air");
        }
        if (bridge) {
            deckUnder(out, c, x, z, top);
        } else {
            boolean wall = c.lane.abutment(c.along);
            for (int y = Math.min(top - 1, 0); y <= top - 1; y++) {
                out.set(x, y, z, wall && y > 0 ? "hw_mse" : y == top - 1 ? "minecraft:gravel" : "minecraft:dirt");
            }
            // a cutting: the surface below the ground, the bank cleared above it
            for (int y = top + 1; y <= 0; y++) {
                out.set(x, y, z, "air");
            }
        }
    }

    private static String marking(Hit c, boolean shared, SplittableRandom rng) {
        String axis = Math.abs(c.dx) > Math.abs(c.dz) ? "x" : "z";
        double s = Math.abs(c.off);
        String base = weathered(rng, c.lane.onBridge(c.along) ? 0.3 : 0.14);
        switch (c.lane.style) {
            case HIGHWAY -> {
                if (s < 0.5) {
                    return rng.nextDouble() < 0.85 ? "road_line_yellow[axis=" + axis + "]" : base;
                }
                if (Math.abs(s - 3.5) < 0.5 && Math.floorMod((int) c.along, 12) < 5) {
                    return rng.nextDouble() < 0.85 ? "road_line_white[axis=" + axis + "]" : base;
                }
            }
            case RAMP -> {
                if (!shared && s > c.lane.half - 1 && s <= c.lane.half - 0.0) {
                    return rng.nextDouble() < 0.8 ? (c.off < 0 ? "road_line_yellow" : "road_line_white") + "[axis=" + axis + "]" : base;
                }
            }
            default -> {
            }
        }
        return base;
    }

    static String weathered(SplittableRandom rng, double wear) {
        double r = rng.nextDouble();
        if (r > wear) {
            return "asphalt";
        }
        r /= wear;
        return r < 0.3 ? "asphalt_crack" : r < 0.55 ? "asphalt_b" : r < 0.8 ? "asphalt_patch" : "asphalt_moss";
    }

    /** Facing toward the centre line from a column at offset off. */
    static String toward(double off, double dx, double dz) {
        // the right of travel is (-dz, dx); the road lies the other way from a column on the right
        double vx = off > 0 ? dz : -dz, vz = off > 0 ? -dx : dx;
        return Math.abs(vx) > Math.abs(vz) ? (vx > 0 ? "east" : "west") : (vz > 0 ? "south" : "north");
    }

    /** Beyond the pavement: the shoulder, the guardrail or parapet, the slope down the embankment. */
    /** A road's edge; gore: between two roadways that meet (a ramp joining), so no guardrail there. */
    private static void verge(BlockSink out, Hit e, int x, int z, double above, boolean gore, SplittableRandom rng) {
        double out0 = e.dist - e.lane.half;
        int top = (int) Math.floor(e.y);
        boolean bridge = e.lane.onBridge(e.along);
        String face = toward(e.off, e.dx, e.dz);
        if (bridge) {
            if (out0 <= 2.5) {
                out.set(x, top, z, "hw_deck");
                deckUnder(out, e, x, z, top);
                if (out0 > 1.5) {
                    out.set(x, top + 1, z, "hw_parapet[facing=" + face + "]");
                } else {
                    out.set(x, top + 1, z, "air");
                }
            }
            return;
        }
        if (out0 <= 2.5) {
            // the shoulder: gravel at the road's height, the guardrail on its outer edge up high
            for (int y = Math.min(top - 1, 0); y < top; y++) {
                out.set(x, y, z, y > 0 && e.lane.abutment(e.along) ? "hw_mse" : "minecraft:dirt");
            }
            out.set(x, top, z, rng.nextDouble() < 0.8 ? "minecraft:gravel" : "minecraft:coarse_dirt");
            for (int y = top + 1; y <= Math.min(top + 4, Math.floor(above) - 2); y++) {
                out.set(x, y, z, "air");
            }
            if (out0 > 1.5 && e.y > e.lane.railOver && !gore) {
                int n = (int) Math.floor(e.along);
                out.set(x, top + 1, z, (n % 2 == 0 ? "hw_rail" : "hw_rail_span") + "[facing=" + face + "]");
            }
            return;
        }
        // the embankment's side, one down for one out, grassed (or the cutting's bank, up)
        double fall = out0 - 2.5;
        if (e.y > 0.5) {
            int g = (int) Math.floor(e.y - fall);
            if (g <= 0) {
                return;
            }
            for (int y = 1; y < g; y++) {
                out.set(x, y, z, e.lane.abutment(e.along) ? "hw_mse" : "minecraft:dirt");
            }
            out.set(x, g, z, rng.nextDouble() < 0.1 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
        } else if (e.y < -0.5) {
            int g = (int) Math.floor(e.y + fall);
            for (int y = Math.max(g + 1, top + 1); y <= 0; y++) {
                out.set(x, y, z, "air");
            }
            if (g < 0) {
                out.set(x, g, z, "minecraft:grass_block");
            }
        }
    }

    /** Under a bridge: the slab, a girder every few blocks across, the bent every so often. */
    private static void deckUnder(BlockSink out, Hit h, int x, int z, int top) {
        out.set(x, top - 1, z, "hw_deck");
        String axis = Math.abs(h.dx) > Math.abs(h.dz) ? "x" : "z";
        int across = (int) Math.floor(h.off + 64);
        double s = Math.abs(h.off);
        if (across % 4 == 1 && s < h.lane.half + 1.5) {
            out.set(x, top - 2, z, "hw_girder[axis=" + axis + "]");
        }
        // the bents: a cap beam across, columns under it
        double from = 0;
        for (double[] sp : h.lane.spans) {
            if (h.along >= sp[0] && h.along <= sp[1]) {
                from = h.along - sp[0];
                double len = sp[1] - sp[0];
                if (from < 3 || len - from < 3) {
                    return;  // the abutments carry the ends
                }
            }
        }
        int b = h.lane.bentEvery;
        double m = from % b;
        if (Math.min(m, b - m) < 1.0 && s < h.lane.half + 1) {
            out.set(x, top - 2, z, "hw_conc");
            out.set(x, top - 3, z, "hw_conc");
            if (Math.floorMod((int) Math.floor(h.off + 64) - 64 + 2, 6) == 0 || s > h.lane.half - 0.5 && s < h.lane.half + 0.5) {
                // never a column standing in the road underneath
                for (int y = -1; y <= 1; y++) {
                    String there = out.get(x, y, z);
                    if (there != null && (there.startsWith("asphalt") || there.startsWith("road_line"))) {
                        return;
                    }
                }
                for (int y = top - 4; y >= -2; y--) {
                    String there = out.get(x, y, z);
                    if (y >= 1 && there != null && !there.equals("air")) {
                        break;
                    }
                    out.set(x, y, z, "hw_pier");
                }
            }
        }
    }
}
