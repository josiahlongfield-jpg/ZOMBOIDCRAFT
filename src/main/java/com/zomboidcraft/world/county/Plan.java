package com.zomboidcraft.world.county;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Grayhaven County master plan, as Josiah approved it on the plan page (draft 3): the sea and
 * its coast, the Calder and the two arms that cut Grayhaven Island off from the mainland, the
 * walled city core and its sprawl, mountains, forests and farm country, every named place, the
 * highways, the railway loop and the subway. tools/county/plan_export.py writes it from the page
 * into data/zomboidcraft/county_plan.txt; this reads it and answers where things are.
 *
 * Shapes keep their segments in buckets, so asking how far a column is from the coast or a river
 * looks at a handful of segments, not every one.
 */
public final class Plan {
    /** A named place on the plan: its kind on the map, the builder it maps to, and where it is. */
    public record Spot(String id, String kind, PlaceType type, String variant, int x, int z, Set<String> flags, String name) {
        public boolean has(String flag) {
            return flags.contains(flag);
        }
    }

    /** A road, rail or river line: its id, class (i, us, ky, loc, rail, subway or river) and width. */
    public static final class Line {
        public final String id, cls;
        public final double width, blownAt;
        public final Shape shape;

        Line(String id, String cls, double width, double blownAt, Shape shape) {
            this.id = id;
            this.cls = cls;
            this.width = width;
            this.blownAt = blownAt;
            this.shape = shape;
        }
    }

    public record Oval(String id, double x, double z, double rx, double rz, boolean cliff) {
        /** Below 1 inside, 1 on the edge. */
        public double at(double px, double pz) {
            double a = (px - x) / rx, b = (pz - z) / rz;
            return Math.sqrt(a * a + b * b);
        }
    }

    public record Ship(double x, double z, double angle, double length) {}

    private static volatile Plan instance;

    public final List<Spot> spots = new ArrayList<>();
    public final Map<String, Spot> byId = new LinkedHashMap<>();
    public final List<Line> rivers = new ArrayList<>(), roads = new ArrayList<>(), rails = new ArrayList<>();
    public final Map<String, Shape> areas = new LinkedHashMap<>(), lines = new LinkedHashMap<>();
    public final Map<String, List<String>> stops = new LinkedHashMap<>();
    public final List<Oval> lakes = new ArrayList<>(), islands = new ArrayList<>();
    public final Map<String, Double> urban = new LinkedHashMap<>();
    public final List<Ship> ships = new ArrayList<>();
    /** The piers drawn off each place, relative to it: {ax, az, bx, bz, width}. */
    public final Map<String, List<double[]>> piers = new LinkedHashMap<>();
    public final Shape sea, isle, core, sprawl, mountains, marsh;
    public final List<Shape> forests = new ArrayList<>(), farms = new ArrayList<>(), military = new ArrayList<>();
    /** The widest river, for bucket reach. */
    private final double maxRiver;

    public static Plan get() {
        Plan p = instance;
        if (p == null) {
            synchronized (Plan.class) {
                p = instance;
                if (p == null) {
                    try (InputStream in = Plan.class.getResourceAsStream("/data/zomboidcraft/county_plan.txt")) {
                        if (in == null) {
                            throw new IllegalStateException("county_plan.txt is missing from the jar");
                        }
                        p = instance = new Plan(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
                    } catch (IOException e) {
                        throw new IllegalStateException("Couldn't read the county plan", e);
                    }
                }
            }
        }
        return p;
    }

    private Plan(BufferedReader in) throws IOException {
        double widest = 0;
        for (String raw; (raw = in.readLine()) != null; ) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split(" ");
            switch (f[0]) {
                case "place" -> {
                    PlaceType type = null;
                    String variant = "";
                    if (!f[3].equals("new")) {
                        String[] tv = f[3].split(":");
                        type = PlaceType.valueOf(tv[0]);
                        variant = tv[1];
                    }
                    Set<String> flags = f[6].equals("-") ? Set.of() : new HashSet<>(Arrays.asList(f[6].split(",")));
                    String name = String.join(" ", Arrays.copyOfRange(f, 7, f.length));
                    Spot s = new Spot(f[1], f[2], type, variant, (int) Double.parseDouble(f[4]), (int) Double.parseDouble(f[5]), flags, name);
                    spots.add(s);
                    byId.put(s.id, s);
                }
                case "river" -> {
                    double w = Double.parseDouble(f[2]);
                    widest = Math.max(widest, w);
                    rivers.add(new Line(f[1], "river", w, -1, new Shape(points(f, 3), false, w / 2 + 400)));
                }
                case "area" -> areas.put(f[1], new Shape(points(f, 2), true, 600));
                case "line" -> lines.put(f[1], new Shape(points(f, 2), false, 200));
                case "lake" -> lakes.add(new Oval(f[1], d(f[2]), d(f[3]), d(f[4]), d(f[5]), false));
                case "island" -> islands.add(new Oval(f[1], d(f[2]), d(f[3]), d(f[4]), d(f[5]), f[6].equals("cliff")));
                case "urban" -> urban.put(f[1], d(f[2]));
                case "road" -> roads.add(new Line(f[1], f[2], 0, f[3].equals("-") ? -1 : d(f[3]), new Shape(points(f, 4), false, 200)));
                case "rail", "subway" -> rails.add(new Line(f[1], f[0], 0, -1, new Shape(points(f, 2), true, 200)));
                case "stops" -> stops.put(f[1], Arrays.asList(Arrays.copyOfRange(f, 2, f.length)));
                case "ship" -> ships.add(new Ship(d(f[1]), d(f[2]), d(f[3]), d(f[4])));
                case "pier" -> {
                    double[] a = points(Arrays.copyOf(f, 4), 2);
                    piers.computeIfAbsent(f[1], k -> new ArrayList<>()).add(new double[] {a[0], a[1], a[2], a[3], d(f[4])});
                }
                default -> {
                    // anything newer is read by the builders that need it
                }
            }
        }
        maxRiver = widest;
        sea = areas.get("sea");
        isle = areas.get("isle");
        core = areas.get("core");
        sprawl = areas.get("sprawl");
        mountains = areas.get("mountains");
        marsh = areas.get("marsh");
        areas.forEach((k, v) -> {
            if (k.startsWith("forest")) {
                forests.add(v);
            } else if (k.startsWith("farm")) {
                farms.add(v);
            } else if (k.startsWith("mil-")) {
                military.add(v);
            }
        });
    }

    private static double d(String s) {
        return Double.parseDouble(s);
    }

    private static double[] points(String[] f, int from) {
        double[] out = new double[(f.length - from) * 2];
        for (int i = from; i < f.length; i++) {
            int c = f[i].indexOf(',');
            out[(i - from) * 2] = Double.parseDouble(f[i].substring(0, c));
            out[(i - from) * 2 + 1] = Double.parseDouble(f[i].substring(c + 1));
        }
        return out;
    }

    // ------------------------------------------------------------------ water

    /**
     * How far (x, z) is from the nearest river bank: negative in the channel, down to minus half
     * its width on the centre line. Up to 400 out; past that, 400. Fills in which river in at[0].
     */
    public double river(double x, double z, Line[] at) {
        double best = 400;
        for (Line r : rivers) {
            double dist = r.shape.distance(x, z, best + r.width / 2) - r.width / 2;
            if (dist < best) {
                best = dist;
                if (at != null) {
                    at[0] = r;
                }
            }
        }
        return best;
    }

    /** True on the open sea: east of the coast and not on one of its islands. */
    public boolean inSea(double x, double z) {
        return sea.contains(x, z) && island(x, z) == null;
    }

    /** The offshore island (x, z) stands on, if any. */
    public Oval island(double x, double z) {
        for (Oval o : islands) {
            if (o.at(x, z) < 1) {
                return o;
            }
        }
        return null;
    }

    /**
     * How far (x, z) is from the shore of the sea: negative out at sea (how far out), positive on
     * land. Accurate to 600 either way.
     */
    public double coast(double x, double z) {
        double dist = sea.distance(x, z, 600);
        double c = sea.contains(x, z) ? -dist : dist;
        for (Oval o : islands) {
            double e = o.at(x, z);
            if (e < 1.6) {
                // an island's shore, roughly in blocks
                double r = Math.min(o.rx, o.rz);
                c = Math.max(c, (1 - e) * r);
            }
        }
        return c;
    }

    /** How far inside a lake (negative) or outside its shore (positive). */
    public double lake(double x, double z) {
        double best = 1e9;
        for (Oval o : lakes) {
            best = Math.min(best, (o.at(x, z) - 1) * Math.min(o.rx, o.rz));
        }
        return best;
    }

    /** True where the plan has water: the sea, a river or a lake. */
    public boolean water(double x, double z) {
        return inSea(x, z) || river(x, z, null) < 0 || lake(x, z) < 0;
    }

    /** Distance to the nearest water of any kind, negative inside it; capped at 400. */
    public double toWater(double x, double z) {
        return Math.min(Math.min(river(x, z, null), coast(x, z)), Math.min(lake(x, z), 400));
    }

    // ------------------------------------------------------------------ land use

    /**
     * True on Grayhaven Island: between the North and South Arms, west of the sea. The island
     * city is the county's biggest landmark (its 20,000 zombies are budgeted by this).
     */
    public static boolean onIsland(double x, double z) {
        Plan p = get();
        if (!p.isle.contains(x, z) || p.inSea(x, z)) {
            return false;
        }
        for (Line r : p.rivers) {
            if (r.id.endsWith("-arm") && r.shape.distance(x, z, r.width / 2) < r.width / 2) {
                return false;
            }
        }
        return true;
    }

    /** Inside the wall: the city of Grayhaven, island and mainland. */
    public boolean inCore(double x, double z) {
        return core.contains(x, z);
    }

    /** Signed distance into an area (positive inside, negative outside), capped at its reach. */
    public static double depth(Shape s, double x, double z) {
        double dist = s.distance(x, z, s.reach);
        return s.contains(x, z) ? dist : -dist;
    }

    public boolean inAny(List<Shape> shapes, double x, double z) {
        for (Shape s : shapes) {
            if (s.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /** True in the suburbs round the wall or round one of the inland towns. */
    public boolean inSprawl(double x, double z) {
        if (sprawl.contains(x, z)) {
            return true;
        }
        for (Map.Entry<String, Double> u : urban.entrySet()) {
            Spot s = byId.get(u.getKey());
            if (Math.hypot(s.x - x, s.z - z) < u.getValue() * 1.5) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ shapes

    /**
     * A polyline or polygon. Its segments sit in square buckets, each holding the segments that
     * pass within {@link #reach} of it, and in rows for the inside test.
     */
    public static final class Shape {
        private static final int CELL = 128, ROW = 64;
        public final double[] pts;
        public final boolean closed;
        public final double reach;
        public final double minX, minZ, maxX, maxZ;
        // fastutil mixes the key; a HashMap's Long hash (x ^ z of County.key) put whole diagonals in one bin
        private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<int[]> buckets = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();
        private final Map<Integer, int[]> rows = new java.util.HashMap<>();

        Shape(double[] pts, boolean closed, double reach) {
            this.pts = pts;
            this.closed = closed;
            this.reach = reach;
            double a = 1e18, b = 1e18, c = -1e18, e = -1e18;
            for (int i = 0; i < pts.length; i += 2) {
                a = Math.min(a, pts[i]);
                b = Math.min(b, pts[i + 1]);
                c = Math.max(c, pts[i]);
                e = Math.max(e, pts[i + 1]);
            }
            minX = a;
            minZ = b;
            maxX = c;
            maxZ = e;
            Map<Long, List<Integer>> bk = new java.util.HashMap<>();
            Map<Integer, List<Integer>> rw = new java.util.HashMap<>();
            for (int s = 0; s < segments(); s++) {
                double ax = x(s), az = z(s), bx = x(s + 1), bz = z(s + 1);
                for (int cx = Math.floorDiv((int) Math.floor(Math.min(ax, bx) - reach), CELL);
                        cx <= Math.floorDiv((int) Math.ceil(Math.max(ax, bx) + reach), CELL); cx++) {
                    for (int cz = Math.floorDiv((int) Math.floor(Math.min(az, bz) - reach), CELL);
                            cz <= Math.floorDiv((int) Math.ceil(Math.max(az, bz) + reach), CELL); cz++) {
                        // only the cells the reach really touches (centre within reach plus half a diagonal)
                        if (segDist(cx * CELL + CELL / 2.0, cz * CELL + CELL / 2.0, ax, az, bx, bz) > reach + CELL * 0.71) {
                            continue;
                        }
                        bk.computeIfAbsent(County.key(cx, cz), k -> new ArrayList<>()).add(s);
                    }
                }
                for (int r = Math.floorDiv((int) Math.floor(Math.min(az, bz)), ROW); r <= Math.floorDiv((int) Math.ceil(Math.max(az, bz)), ROW); r++) {
                    rw.computeIfAbsent(r, k -> new ArrayList<>()).add(s);
                }
            }
            bk.forEach((k, v) -> buckets.put(k.longValue(), v.stream().mapToInt(Integer::intValue).toArray()));
            rw.forEach((k, v) -> rows.put(k, v.stream().mapToInt(Integer::intValue).toArray()));
        }

        public int count() {
            return pts.length / 2;
        }

        public int segments() {
            return closed ? count() : count() - 1;
        }

        public double x(int i) {
            return pts[(i % count()) * 2];
        }

        public double z(int i) {
            return pts[(i % count()) * 2 + 1];
        }

        /** Distance to the nearest segment, or cap if none is nearer (cap at most {@link #reach}). */
        public double distance(double x, double z, double cap) {
            int[] segs = buckets.get(County.key(Math.floorDiv((int) Math.floor(x), CELL), Math.floorDiv((int) Math.floor(z), CELL)));
            double best = cap;
            if (segs != null) {
                for (int s : segs) {
                    best = Math.min(best, segDist(x, z, x(s), z(s), x(s + 1), z(s + 1)));
                }
            }
            return best;
        }

        /** The nearest point on the line and the direction there: {x, z, dx, dz, along}, or null past reach. */
        public double[] nearest(double x, double z) {
            int[] segs = buckets.get(County.key(Math.floorDiv((int) Math.floor(x), CELL), Math.floorDiv((int) Math.floor(z), CELL)));
            if (segs == null) {
                return null;
            }
            double best = 1e18;
            double[] out = null;
            for (int s : segs) {
                double ax = x(s), az = z(s), bx = x(s + 1), bz = z(s + 1);
                double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz;
                double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
                double px = ax + vx * t, pz = az + vz * t, dist = Math.sqrt((x - px) * (x - px) + (z - pz) * (z - pz));
                if (dist < best) {
                    best = dist;
                    double len = Math.sqrt(len2);
                    out = new double[] {px, pz, len == 0 ? 1 : vx / len, len == 0 ? 0 : vz / len, s + t};
                }
            }
            return out;
        }

        /** Inside a closed shape (even-odd). */
        public boolean contains(double x, double z) {
            if (!closed || x < minX || x > maxX || z < minZ || z > maxZ) {
                return false;
            }
            int[] segs = rows.get(Math.floorDiv((int) Math.floor(z), ROW));
            if (segs == null) {
                return false;
            }
            boolean in = false;
            for (int s : segs) {
                double ax = x(s), az = z(s), bx = x(s + 1), bz = z(s + 1);
                if ((az > z) != (bz > z) && x < ax + (bx - ax) * (z - az) / (bz - az)) {
                    in = !in;
                }
            }
            return in;
        }

        /** Every point where the segment a-b crosses this line: {x, z, along this line in segments}. */
        public List<double[]> crossings(double ax, double az, double bx, double bz) {
            List<double[]> out = new ArrayList<>();
            for (int s = 0; s < segments(); s++) {
                double cx = x(s), cz = z(s), dx = x(s + 1), dz = z(s + 1);
                double r1 = bx - ax, r2 = bz - az, s1 = dx - cx, s2 = dz - cz;
                double den = r1 * s2 - r2 * s1;
                if (Math.abs(den) < 1e-9) {
                    continue;
                }
                double t = ((cx - ax) * s2 - (cz - az) * s1) / den, u = ((cx - ax) * r2 - (cz - az) * r1) / den;
                if (t >= 0 && t <= 1 && u >= 0 && u <= 1) {
                    out.add(new double[] {ax + r1 * t, az + r2 * t, s + u});
                }
            }
            return out;
        }

        static double segDist(double x, double z, double ax, double az, double bx, double bz) {
            double vx = bx - ax, vz = bz - az, len2 = vx * vx + vz * vz;
            double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - ax) * vx + (z - az) * vz) / len2));
            double ex = x - ax - vx * t, ez = z - az - vz * t;
            return Math.sqrt(ex * ex + ez * ez);
        }
    }
}
