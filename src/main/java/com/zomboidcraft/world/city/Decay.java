package com.zomboidcraft.world.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Ruins a freshly built city. Runs over everything the generator placed and:
 * smashes, boards up and empties most windows (and turns them to sit in the wall properly);
 * blows holes in some walls, with rebar and rubble below; mixes weathered versions of every wall
 * and ground block (cracked, mossy, sooty, stained, missing bricks) by patchy noise, with soot
 * above burnt-out windows; hangs ivy curtains down the facades; puts moss and weeds on ledges,
 * litter and weeds along the streets, trees in the sidewalks, papers and blood inside, and the
 * odd raider banner on a wall.
 *
 * Works on any {@link BlockSink} that can read back what it holds.
 */
public final class Decay {
    private static final int[][] SIDES = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] FACING = {"north", "east", "south", "west"};

    /** Wall and ground blocks with weathered versions, and which versions each has. */
    private static final Map<String, String[]> VARIANTS = Map.ofEntries(
            Map.entry("brick_red", new String[] {"b", "crack", "moss", "soot", "stain", "holes"}),
            Map.entry("brick_dark", new String[] {"b", "crack", "moss", "soot", "stain", "holes"}),
            Map.entry("concrete_panel", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("concrete_dark", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("stone_trim", new String[] {"b", "crack", "moss", "soot"}),
            Map.entry("asphalt", new String[] {"b", "crack", "patch", "moss"}),
            Map.entry("sidewalk", new String[] {"b", "moss", "stain"}),
            Map.entry("drywall", new String[] {"b", "stain", "soot", "peel", "brick"}),
            Map.entry("plaster_sage", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("plaster_cream", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("plaster_blue", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("plaster_rose", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("brick_buff", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("brick_orange", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("floor_concrete", new String[] {"b", "crack", "moss"}),
            Map.entry("roof", new String[] {"b", "moss"}),
            Map.entry("lobby_floor_white", new String[] {"b", "crack"}),
            Map.entry("lobby_floor_dark", new String[] {"b", "crack"}));
    private static final Set<String> WALLS = Set.of("brick_red", "brick_dark", "concrete_panel", "concrete_dark",
            "stone_trim", "steel_mullion", "glass_spandrel", "drywall", "plaster_sage", "plaster_cream", "plaster_blue",
            "plaster_rose", "brick_buff", "brick_orange", "shop_paint_red", "shop_paint_navy", "shop_paint_teal",
            "shop_paint_black", "shop_paint_mustard", "shop_paint_green");
    private static final Set<String> WINDOWS = Set.of("window", "window_lit", "window_broken", "window_empty",
            "boarded_window");
    private static final Set<String> FLOORS = Set.of("office_floor", "lobby_floor_white", "lobby_floor_dark",
            "floor_concrete");
    private static final Set<String> LEDGES = Set.of("stone_trim", "sill", "roof", "awning", "concrete_dark",
            "awning_red", "awning_blue", "awning_maroon", "awning_yellow", "awning_teal");

    private final BlockSink sink;
    private final SplittableRandom rng;
    private final long seed;
    private final int x0, x1, z0, z1, height;
    private final List<int[]> scars = new ArrayList<>();

    public Decay(BlockSink sink, long seed, int x0, int x1, int z0, int z1, int height) {
        this.sink = sink;
        this.seed = seed;
        this.rng = new SplittableRandom(seed ^ 0x5DEECE66DL);
        this.x0 = x0;
        this.x1 = x1;
        this.z0 = z0;
        this.z1 = z1;
        this.height = height;
    }

    public void run() {
        List<int[]> windows = new ArrayList<>(), walls = new ArrayList<>(), grounds = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    String b = base(sink.get(x, y, z));
                    if (b == null) {
                        continue;
                    }
                    if (WINDOWS.contains(b)) {
                        windows.add(new int[] {x, y, z});
                    } else if (WALLS.contains(b)) {
                        walls.add(new int[] {x, y, z});
                    } else if (air(x, y + 1, z)) {
                        grounds.add(new int[] {x, y, z});
                    }
                }
            }
        }
        for (int[] p : windows) {
            window(p[0], p[1], p[2]);
        }
        breaches(walls);
        for (int[] p : walls) {
            weather(p[0], p[1], p[2]);
        }
        for (int[] p : grounds) {
            weather(p[0], p[1], p[2]);
            dress(p[0], p[1], p[2]);
        }
        for (int[] p : walls) {
            ivy(p[0], p[1], p[2]);
        }
    }

    // ---------------------------------------------------------------- reading the world

    private static String base(String block) {
        if (block == null) {
            return null;
        }
        int open = block.indexOf('[');
        return open < 0 ? block : block.substring(0, open);
    }

    /** The block's name without any weathering suffix ("brick_red_moss" -> "brick_red"). */
    private static String material(String block) {
        String b = base(block);
        if (b == null) {
            return null;
        }
        for (String m : VARIANTS.keySet()) {
            if (b.startsWith(m + "_") && !b.equals("sidewalk_cracked")) {
                return m;
            }
        }
        return b;
    }

    private boolean air(int x, int y, int z) {
        String b = sink.get(x, y, z);
        return b == null || b.equals("air");
    }

    private boolean wallish(int x, int y, int z) {
        String m = material(sink.get(x, y, z));
        return m != null && (WALLS.contains(m) || WINDOWS.contains(m));
    }

    /** Open to the sky: air here and for six blocks up (so not inside a room). */
    private boolean outdoor(int x, int y, int z) {
        for (int k = 0; k < 7; k++) {
            if (!air(x, y + k, z)) {
                return false;
            }
        }
        return true;
    }

    /** The side of this block that faces the open street, or -1. */
    private int outside(int x, int y, int z) {
        for (int d = 0; d < 4; d++) {
            if (outdoor(x + SIDES[d][0], y, z + SIDES[d][1])) {
                return d;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- noise

    private double hash(int x, int y, int z, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0x632BE59BD9B4E019L + y * 0x85157AF5L + z * 0x94D049BB133111EBL + salt;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise in 0..1, stretched by the given cell sizes. */
    private double noise(double x, double y, double z, double sx, double sy, double sz, int salt) {
        x /= sx;
        y /= sy;
        z /= sz;
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y), iz = (int) Math.floor(z);
        double fx = smooth(x - ix), fy = smooth(y - iy), fz = smooth(z - iz);
        double v = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = c >> 1 & 1, dz = c >> 2 & 1;
            double w = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            v += w * hash(ix + dx, iy + dy, iz + dz, salt);
        }
        return v;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    // ---------------------------------------------------------------- windows

    private void window(int x, int y, int z) {
        String b = base(sink.get(x, y, z));
        double r = rng.nextDouble();
        if (y <= 3 && !b.equals("window_lit") && r < 0.3) {
            b = "shutter";  // shops rolled their shutters down when it started
        } else if (b.equals("window")) {
            b = r < 0.42 ? "window_broken" : r < 0.56 ? "boarded_window" : r < 0.65 ? "window_empty" : "window";
        } else if (b.equals("window_lit") && r < 0.25) {
            b = "window_broken";
        }
        boolean alongX = wallish(x - 1, y, z) || wallish(x + 1, y, z);
        boolean alongZ = wallish(x, y, z - 1) || wallish(x, y, z + 1);
        String axis = alongX && !alongZ ? "x" : alongZ && !alongX ? "z" : air(x, y, z - 1) || air(x, y, z + 1) ? "x" : "z";
        sink.set(x, y, z, b + "[axis=" + axis + "]");
    }

    // ---------------------------------------------------------------- blown-out walls

    private void breaches(List<int[]> walls) {
        int n = walls.size() / 2500;
        for (int i = 0; i < n * 4 && n > 0; i++) {
            int[] p = walls.get(rng.nextInt(walls.size()));
            int d = outside(p[0], p[1], p[2]);
            if (d < 0 || p[1] < 2) {
                continue;
            }
            breach(p[0], p[1], p[2], d, 1.2 + rng.nextDouble() * 1.6);
            if (--n <= 0) {
                break;
            }
        }
    }

    private void breach(int cx, int cy, int cz, int d, double r) {
        int tx = SIDES[d][1] != 0 ? 1 : 0, tz = 1 - tx;  // along the wall
        String centre = material(sink.get(cx, cy, cz));
        if (centre == null) {
            return;  // an earlier breach already took this wall
        }
        boolean concrete = centre.startsWith("concrete");
        int reach = (int) Math.ceil(r) + 1;
        for (int a = -reach; a <= reach; a++) {
            for (int v = -reach; v <= reach; v++) {
                int x = cx + a * tx, y = cy + v, z = cz + a * tz;
                double dist = Math.sqrt(a * a + v * v * 1.4) + (hash(x, y, z, 7) - 0.5) * 0.9;
                if (!wallish(x, y, z)) {
                    continue;
                }
                if (dist < r) {
                    boolean lowEdge = v < 0 && dist > r - 1;
                    sink.set(x, y, z, concrete && lowEdge && rng.nextDouble() < 0.5 ? "rebar" : "air");
                } else if (dist < r + 1.3) {
                    String m = material(sink.get(x, y, z));
                    String[] kinds = VARIANTS.get(m);
                    if (kinds != null) {
                        String kind = has(kinds, "holes") && rng.nextDouble() < 0.5 ? "holes" : rng.nextBoolean() ? "soot" : "crack";
                        if (has(kinds, kind)) {
                            sink.set(x, y, z, m + "_" + kind);
                        }
                    }
                }
            }
        }
        scars.add(new int[] {cx, cy, cz, (int) Math.ceil(r + 3)});
        // the wall that fell out lies in a heap on the ground below
        int ox = cx + SIDES[d][0], oz = cz + SIDES[d][1];
        int gy = cy;
        while (gy > 0 && air(ox, gy - 1, oz)) {
            gy--;
        }
        for (int k = 0; k < 8 + rng.nextInt(10); k++) {
            int x = ox + SIDES[d][0] * rng.nextInt(3) + (rng.nextInt(5) - 2) * tx;
            int z = oz + SIDES[d][1] * rng.nextInt(3) + (rng.nextInt(5) - 2) * tz;
            if (air(x, gy, z) && !air(x, gy - 1, z)) {
                double q = rng.nextDouble();
                sink.set(x, gy, z, q < 0.4 ? "rubble_pile" : q < 0.75 ? "debris_chunk[facing=" + FACING[rng.nextInt(4)] + "]"
                        : q < 0.9 ? "rubble" : "rebar");
            }
        }
    }

    private static boolean has(String[] kinds, String k) {
        for (String s : kinds) {
            if (s.equals(k)) {
                return true;
            }
        }
        return false;
    }

    private boolean nearScar(int x, int y, int z) {
        for (int[] s : scars) {
            int dx = x - s[0], dy = y - s[1], dz = z - s[2];
            if (dx * dx + dy * dy + dz * dz < s[3] * s[3]) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- weathering

    private void weather(int x, int y, int z) {
        String block = sink.get(x, y, z);
        String b = base(block);
        if (b == null || !b.equals(material(block))) {
            return;  // already weathered (or not a block with versions)
        }
        String[] kinds = VARIANTS.get(b);
        if (kinds == null) {
            return;
        }
        String below1 = base(sink.get(x, y - 1, z)), below2 = base(sink.get(x, y - 2, z));
        boolean burnt = "window_broken".equals(below1) || "window_empty".equals(below1)
                || "window_broken".equals(below2) || "window_empty".equals(below2);
        double moss = noise(x, y, z, 5, 7, 5, 11) + (y < 5 ? 0.15 : 0) + (b.equals("roof") ? 0.1 : 0);
        double r = hash(x, y, z, 3);
        String kind = null;
        if (has(kinds, "soot") && ((burnt && r < 0.55) || (nearScar(x, y, z) && r < 0.4))) {
            kind = "soot";
        } else if (has(kinds, "moss") && moss > 0.66) {
            kind = "moss";
        } else if (has(kinds, "holes") && r < 0.05) {
            kind = "holes";
        } else if (has(kinds, "crack") && r < 0.15) {
            kind = "crack";
        } else if (has(kinds, "patch") && r < 0.2) {
            kind = "patch";
        } else if (has(kinds, "stain") && r < 0.24) {
            kind = "stain";
        } else if (has(kinds, "peel") && r < 0.36) {
            kind = r < 0.31 ? "peel" : "brick";
        } else if (r < 0.62) {
            kind = "b";
        }
        if (kind != null) {
            sink.set(x, y, z, b + "_" + kind);
        }
    }

    // ---------------------------------------------------------------- plants and litter

    private void dress(int x, int y, int z) {
        String m = material(sink.get(x, y, z));
        if (m == null) {
            return;  // a breach took it
        }
        int ay = y + 1;
        if (m.equals("asphalt") && nearBurntCar(x, ay, z)) {
            sink.set(x, y, z, "burnt_ground");
        }
        if (m.equals("concrete_dark") && y == 0 && noise(x, 0, z, 9, 1, 9, 31) > 0.58) {
            sink.set(x, y, z, "dirt_withered");  // weeds have broken up the old lots
        }
        if (!air(x, ay, z)) {
            return;
        }
        // floors are indoors even under a tall lobby ceiling
        boolean floor = FLOORS.contains(m) || m.startsWith("office_floor") || m.startsWith("lobby_floor");
        double r = rng.nextDouble();
        if (floor || !outdoor(x, ay, z)) {
            if (floor) {
                put(x, ay, z, r, new Object[][] {{0.11, "paper"}, {0.02, "blood_splat"}, {0.02, "rubble_pile"},
                        {0.02, "debris_chunk[facing=" + FACING[rng.nextInt(4)] + "]"}, {0.025, "leaf_litter"},
                        {0.008, "office_chair_fallen[facing=" + FACING[rng.nextInt(4)] + "]"}, {0.015, "glass_shards"},
                        {0.006, "trash_bag[facing=" + FACING[rng.nextInt(4)] + "]"}, {0.006, "cardboard_box[facing=" + FACING[rng.nextInt(4)] + "]"}});
            }
            return;
        }
        if (LEDGES.contains(m) && y > 2) {
            put(x, ay, z, r, new Object[][] {{0.3, "moss_carpet"}, {0.07, "grass_tuft"}, {0.02, "weeds_tall"}});
            return;
        }
        boolean street = m.equals("asphalt") || m.equals("road_line_yellow") || m.equals("road_line_white") || m.equals("crosswalk");
        boolean walk = m.equals("sidewalk") || m.equals("sidewalk_cracked") || m.equals("concrete_dark");
        if (!street && !walk) {
            return;
        }
        boolean base = false;
        for (int[] s : SIDES) {
            base |= wallish(x + s[0], ay, z + s[1]);
        }
        if (base) {
            put(x, ay, z, r, new Object[][] {{0.12, "weeds"}, {0.1, "grass_tuft"}, {0.05, "trash_bag[facing=" + FACING[rng.nextInt(4)] + "]"},
                    {0.06, "paper"}, {0.07, "glass_shards"}, {0.03, "rubble_pile"}, {0.03, "weeds_tall"}, {0.015, "crate"},
                    {0.025, "cardboard_box[facing=" + FACING[rng.nextInt(4)] + "]"}, {0.04, "leaf_litter"},
                    {0.006, "dumpster[facing=" + FACING[rng.nextInt(4)] + "]"}, {0.008, "barrel"}});
        } else if (walk) {
            if (r < 0.004 && treeRoom(x, ay, z)) {
                tree(x, ay, z);
                return;
            }
            put(x, ay, z, r, new Object[][] {{0.05, "grass_tuft"}, {0.035, "paper"}, {0.012, "glass_shards"}, {0.03, "moss_carpet"},
                    {0.05, "leaf_litter"},
                    {0.012, "weeds"}, {0.004, "blood_splat"}, {0.006, "trash_bag[facing=" + FACING[rng.nextInt(4)] + "]"},
                    {0.003, "barrel"}});
        } else {
            boolean cracked = base(sink.get(x, y, z)).endsWith("_crack");
            put(x, ay, z, r, new Object[][] {{cracked ? 0.12 : 0.015, "grass_tuft"}, {0.012, "paper"}, {0.015, "leaf_litter"}, {0.006, "debris_chunk[facing=" + FACING[rng.nextInt(4)] + "]"},
                    {0.004, "scorch"}, {0.003, "blood_splat"}, {0.003, "glass_shards"}});
        }
    }

    private boolean nearBurntCar(int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                String b = base(sink.get(x + dx, y, z + dz));
                if (b != null && b.startsWith("car_") && b.contains("burnt")) {
                    return hash(x, y, z, 12) < 0.8;
                }
            }
        }
        return false;
    }

    /** Places the first choice whose share the roll falls in (shares are added up in order). */
    private void put(int x, int y, int z, double roll, Object[][] choices) {
        double acc = 0;
        for (Object[] c : choices) {
            acc += (double) c[0];
            if (roll < acc) {
                sink.set(x, y, z, (String) c[1]);
                return;
            }
        }
    }

    private boolean treeRoom(int x, int y, int z) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (!outdoor(x + dx, y, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A scraggly street tree that has gone wild: a crooked trunk and a ragged crown. */
    private void tree(int x, int y, int z) {
        int h = 3 + rng.nextInt(3);
        int tx = x, tz = z;
        for (int k = 0; k < h; k++) {
            sink.set(tx, y + k, tz, "tree_log");
            if (k == h - 2 && rng.nextBoolean()) {
                int[] s = SIDES[rng.nextInt(4)];
                tx += s[0];
                tz += s[1];
                sink.set(tx, y + k, tz, "tree_log");
            }
        }
        int cy = y + h;
        double rad = 1.8 + rng.nextDouble() * 0.9;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy * 2.2 + dz * dz) + (hash(tx + dx, cy + dy, tz + dz, 9) - 0.5) * 1.4;
                    if (dist < rad && air(tx + dx, cy + dy, tz + dz)) {
                        sink.set(tx + dx, cy + dy, tz + dz, "leaves_dark");
                    }
                }
            }
        }
        for (int[] s : SIDES) {
            if (rng.nextDouble() < 0.5 && air(x + s[0], y, z + s[1])) {
                sink.set(x + s[0], y, z + s[1], rng.nextBoolean() ? "weeds_tall" : "grass_tuft");
            }
        }
    }

    /** Ivy curtains: long vertical streaks of vines on the street side of walls. */
    private void ivy(int x, int y, int z) {
        String m = material(sink.get(x, y, z));
        if (m == null || !WALLS.contains(m) || m.equals("drywall") || m.equals("glass_spandrel")) {
            return;
        }
        for (int d = 0; d < 4; d++) {
            int ax = x + SIDES[d][0], az = z + SIDES[d][1];
            if (!air(ax, y, az) || !outdoor(ax, y, az) && !air(ax, y - 1, az)) {
                continue;
            }
            double curtain = noise(ax, y, az, 3.5, 22, 3.5, 21) + (y < 6 ? 0.12 : 0) + (m.startsWith("brick") ? 0.06 : 0);
            if (y >= 1 && y <= 3 && hash(ax, y, az, 6) < 0.07 && outdoor(ax, y, az)) {
                sink.set(ax, y, az, "graffiti_" + (int) (hash(ax, 0, az, 8) * 8) + "[facing=" + FACING[(d + 2) & 3] + "]");
            } else if (curtain > 0.7 && hash(ax, y, az, 4) < 0.9) {
                sink.set(ax, y, az, "vines[facing=" + FACING[(d + 2) & 3] + "]");
            } else if (y > 6 && y < 60 && hash(ax, y, az, 5) < 0.0006 && air(ax, y - 1, az) && wallish(x, y - 1, z)) {
                sink.set(ax, y, az, "banner_skull[facing=" + FACING[(d + 2) & 3] + "]");
                sink.set(ax, y - 1, az, "banner_skull[facing=" + FACING[(d + 2) & 3] + "]");
            }
            return;
        }
    }
}
