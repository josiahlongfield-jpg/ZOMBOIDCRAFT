package com.zomboidcraft.world.county;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/**
 * The trees of the county. Most come from templates (data/zomboidcraft/trees.bin, written by
 * tools/nature/trees.py from the trees pack Josiah bought): oaks, maples, beeches, birches, elms,
 * spruces, firs, red cedars, willows and dead snags, each in three sizes, built of chunky tree
 * blocks (bark, half-round trunk edges, limbs that join their neighbours, dense and sparse
 * leaves), turned and mirrored at random, a share of them turning red or gold or sick and grey.
 * Bushes are still grown block by block. Blocks go into a {@link Canopy} that keeps trunks over
 * leaves where trees overlap.
 */
public final class Trees {
    public interface Canopy {
        /** Wood always wins over leaves and moss. */
        void wood(int x, int y, int z, String block);

        void leaves(int x, int y, int z, String block);
    }

    public enum Kind { OAK, MAPLE, SYCAMORE, PINE, DEAD, SICK, BUSH, YOUNG }

    /** The furthest any template's blocks reach from its trunk, sideways. */
    public static final int REACH = 11;

    private static final String LEAF = "[persistent=true]";

    /**
     * One template: its blocks relative to the foot of its trunk. {@code shell} marks the crown's
     * outside: a leaf block's open sides (bits east, west, up, down, south, north), or {@link #INNER}
     * for the leaves one block in.
     */
    private record Template(String name, int[] x, int[] y, int[] z, String[] state, byte[] shell) {}

    private static final int[][] SIDES = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final byte INNER = 0x40;

    private static final Map<String, List<Template>> SPECIES = load();
    /** Templates turned and mirrored, by name and turn. */
    private static final Map<String, Template> TURNED = new ConcurrentHashMap<>();

    private static Map<String, List<Template>> load() {
        Map<String, List<Template>> out = new HashMap<>();
        try (InputStream raw = Trees.class.getResourceAsStream("/data/zomboidcraft/trees.bin")) {
            if (raw == null) {
                return out;
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            int n = in.readInt();
            for (int t = 0; t < n; t++) {
                String name = in.readUTF();
                String[] pal = new String[in.readUnsignedShort()];
                for (int i = 0; i < pal.length; i++) {
                    pal[i] = in.readUTF().intern();
                }
                int cells = in.readInt();
                int[] x = new int[cells], y = new int[cells], z = new int[cells];
                String[] state = new String[cells];
                for (int i = 0; i < cells; i++) {
                    x[i] = in.readByte();
                    y[i] = in.readShort();
                    z[i] = in.readByte();
                    state[i] = pal[in.readUnsignedShort()];
                }
                // "oak_2_large" files under "oak" and "oak/large"
                String sp = name.substring(0, name.indexOf('_'));
                String size = name.substring(name.lastIndexOf('_') + 1);
                Template tp = new Template(name, x, y, z, state, shell(x, y, z, state));
                out.computeIfAbsent(sp, k -> new ArrayList<>()).add(tp);
                out.computeIfAbsent(sp + "/" + size, k -> new ArrayList<>()).add(tp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't read the tree templates", e);
        }
        return out;
    }

    private static long cell(int x, int y, int z) {
        return ((long) x & 0xFFFF) << 32 | ((long) y & 0xFFFF) << 16 | ((long) z & 0xFFFF);
    }

    /** Which leaves are on the crown's outside, and which way they face out; which are just inside them. */
    private static byte[] shell(int[] x, int[] y, int[] z, String[] state) {
        Set<Long> all = new HashSet<>();
        for (int i = 0; i < x.length; i++) {
            all.add(cell(x[i], y[i], z[i]));
        }
        byte[] out = new byte[x.length];
        Set<Long> outer = new HashSet<>();
        for (int i = 0; i < x.length; i++) {
            if (!state[i].contains("_leaves")) {
                continue;
            }
            for (int d = 0; d < 6; d++) {
                if (!all.contains(cell(x[i] + SIDES[d][0], y[i] + SIDES[d][1], z[i] + SIDES[d][2]))) {
                    out[i] |= (byte) (1 << d);
                }
            }
            if (out[i] != 0) {
                outer.add(cell(x[i], y[i], z[i]));
            }
        }
        for (int i = 0; i < x.length; i++) {
            if (out[i] == 0 && state[i].contains("_leaves")) {
                for (int[] d : SIDES) {
                    if (outer.contains(cell(x[i] + d[0], y[i] + d[1], z[i] + d[2]))) {
                        out[i] = INNER;
                        break;
                    }
                }
            }
        }
        return out;
    }

    /** Whether the templates are there (they are in the mod; previews may run without them). */
    static boolean templates() {
        return !SPECIES.isEmpty();
    }

    /** Picks a tree for the spot from how wooded, damp and high it is. */
    public static Kind pick(SplittableRandom rng, double forest, double moisture, double height, boolean nearWater) {
        double r = rng.nextDouble();
        if (nearWater && r < 0.45) {
            return Kind.SYCAMORE;
        }
        if (height > 88 && r < 0.6) {
            return Kind.PINE;
        }
        if (r < 0.05) {
            return Kind.DEAD;
        }
        if (r < 0.10) {
            return Kind.SICK;
        }
        if (forest < 0.3 && r < 0.35) {
            return Kind.BUSH;
        }
        if (r < 0.22) {
            return Kind.YOUNG;
        }
        if (r < 0.30) {
            return Kind.PINE;
        }
        return moisture > 0.1 ? (r < 0.6 ? Kind.MAPLE : Kind.OAK) : (r < 0.55 ? Kind.OAK : Kind.MAPLE);
    }

    /** Grows a tree standing on the block at (x, y - 1, z). */
    public static void grow(Canopy c, Kind kind, int x, int y, int z, SplittableRandom rng) {
        if (templates() && kind != Kind.BUSH) {
            double r = rng.nextDouble();
            String size = r < 0.3 ? "small" : r < 0.72 ? "medium" : "large";
            String sp = switch (kind) {
                case OAK -> "oak";
                case MAPLE -> rng.nextDouble() < 0.72 ? "maple" : "elm";
                case SYCAMORE -> new String[] {"willow", "willow", "beech", "birch", "beech"}[rng.nextInt(5)];
                case PINE -> new String[] {"spruce", "spruce", "fir", "cedar", "cedar"}[rng.nextInt(5)];
                case DEAD -> "dead";
                case SICK -> rng.nextBoolean() ? "elm" : "oak";
                case YOUNG -> new String[] {"oak", "maple", "birch", "beech", "elm"}[rng.nextInt(5)];
                default -> "oak";
            };
            if (kind == Kind.YOUNG) {
                size = "small";
            }
            List<Template> list = SPECIES.get(sp + "/" + size);
            if (list != null && !list.isEmpty()) {
                place(c, list.get(rng.nextInt(list.size())), kind, sp, x, y, z, rng);
                return;
            }
        }
        switch (kind) {
            case OAK -> broadleaf(c, x, y, z, rng, "minecraft:oak_log", "minecraft:oak_leaves", 7 + rng.nextInt(5), 4 + rng.nextInt(2));
            case MAPLE -> broadleaf(c, x, y, z, rng, "minecraft:dark_oak_log", "minecraft:dark_oak_leaves", 8 + rng.nextInt(6), 3 + rng.nextInt(2));
            case SYCAMORE -> broadleaf(c, x, y, z, rng, "minecraft:birch_log", "minecraft:oak_leaves", 9 + rng.nextInt(6), 4);
            case PINE -> pine(c, x, y, z, rng);
            case DEAD -> dead(c, x, y, z, rng);
            case SICK -> sick(c, x, y, z, rng);
            case BUSH -> bush(c, x, y, z, rng, !templates() ? (rng.nextBoolean() ? "minecraft:oak_leaves" : "minecraft:dark_oak_leaves")
                    : "tree_" + new String[] {"oak", "beech", "elm", "maple"}[rng.nextInt(4)] + "_leaves" + (rng.nextDouble() < 0.4 ? "_sparse" : ""));
            case YOUNG -> young(c, x, y, z, rng);
        }
    }

    /** Autumn colours each species can turn, and how often. */
    private static final Map<String, String[]> AUTUMN = Map.of(
            "maple", new String[] {"red", "gold"}, "oak", new String[] {"brown"}, "beech", new String[] {"gold"},
            "birch", new String[] {"gold"}, "elm", new String[] {"gold"}, "willow", new String[] {"gold"});

    private static void place(Canopy c, Template tp, Kind kind, String sp, int x, int y, int z, SplittableRandom rng) {
        Template t = turned(tp, rng.nextInt(8));
        String[] colours = AUTUMN.get(sp);
        String colour = null;
        if (kind == Kind.SICK) {
            colour = "sick";
        } else if (colours != null && rng.nextDouble() < (sp.equals("maple") ? 0.3 : 0.14)) {
            colour = colours[rng.nextInt(colours.length)];
        }
        // the crown's edge is this tree's own: bitten into in clumps, thinned, sprigs poking out
        SplittableRandom r = new SplittableRandom(rng.nextLong());
        long seed = r.nextLong();
        boolean ragged = !sp.equals("dead");
        List<int[]> sprigs = new ArrayList<>();
        List<String> sprigLeaves = new ArrayList<>();
        for (int i = 0; i < t.x.length; i++) {
            String s = t.state[i];
            int wx = x + t.x[i], wy = y + t.y[i], wz = z + t.z[i];
            if (!s.contains("_leaves")) {
                c.wood(wx, wy, wz, s);
                continue;
            }
            int sh = t.shell[i];
            if (ragged && sh != 0) {
                double n = wobble(seed, wx / 2.3, wy / 2.0, wz / 2.3);
                if (sh == INNER) {
                    // the deepest bites reach a block in
                    if (n < -0.5) {
                        continue;
                    }
                    if (n < -0.32) {
                        s = sparse(s);
                    }
                } else {
                    int open = Integer.bitCount(sh);
                    if (n < -0.24 + 0.08 * open) {
                        if (r.nextDouble() < 0.35) {
                            sprigs.add(new int[] {wx, wy, wz});
                            sprigLeaves.add(s);
                        }
                        continue;
                    }
                    if (n < 0.12 + 0.07 * open || r.nextDouble() < 0.3) {
                        s = sparse(s);
                    }
                    if ((sh & 0b110111) != 0 && r.nextDouble() < 0.07 * open) {
                        // a sprig out of one of its open sides, not hanging straight down
                        int d;
                        do {
                            d = r.nextInt(6);
                        } while ((sh >> d & 1) == 0 || d == 3);
                        sprigs.add(new int[] {wx + SIDES[d][0], wy + SIDES[d][1], wz + SIDES[d][2]});
                        sprigLeaves.add(s);
                    }
                }
            }
            if (colour != null) {
                s = recolour(s, colour);
                // a sick tree has lost half its leaves
                if (kind == Kind.SICK && r.nextDouble() < 0.45) {
                    continue;
                }
            }
            c.leaves(wx, wy, wz, s);
        }
        for (int i = 0; i < sprigs.size(); i++) {
            String s = tuft(sprigLeaves.get(i));
            if (colour != null) {
                s = recolour(s, colour);
            }
            int[] p = sprigs.get(i);
            c.leaves(p[0], p[1], p[2], s);
        }
    }

    private static String sparse(String leaves) {
        return leaves.endsWith("_sparse") ? leaves : leaves + "_sparse";
    }

    /** A crossed sprig of the same leaves, for the crown's ragged edge. */
    private static String tuft(String leaves) {
        return (leaves.endsWith("_sparse") ? leaves.substring(0, leaves.length() - "_sparse".length()) : leaves) + "_tuft";
    }

    /** Smooth value noise, -1 to 1, wobbling about once a unit. */
    private static double wobble(long seed, double x, double y, double z) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        fz = fz * fz * (3 - 2 * fz);
        double v = 0;
        for (int i = 0; i < 8; i++) {
            int dx = i & 1, dy = i >> 1 & 1, dz = i >> 2;
            long h = Noise.mix(seed ^ (x0 + dx) * 0x9E3779B97F4A7C15L ^ (y0 + dy) * 0xC2B2AE3D27D4EB4FL ^ (z0 + dz) * 0x165667B19E3779F9L);
            v += (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz) * ((h >>> 11) * 0x1.0p-52 - 1);
        }
        return v;
    }

    private static final Map<String, String> RECOLOURED = new ConcurrentHashMap<>();

    /** "tree_maple_leaves_sparse" in red is "tree_maple_leaves_red_sparse"; sick ones are all one grey. */
    private static String recolour(String leaves, String colour) {
        return RECOLOURED.computeIfAbsent(leaves + "|" + colour, k -> {
            String suffix = leaves.endsWith("_sparse") ? "_sparse" : leaves.endsWith("_tuft") ? "_tuft" : "";
            if (colour.equals("sick")) {
                return "tree_leaves_sick" + suffix;
            }
            return leaves.substring(0, leaves.length() - suffix.length()) + "_" + colour + suffix;
        });
    }

    private static final String[] TURN = {"north", "east", "south", "west"};

    /**
     * A template turned a quarter turn clockwise (seen from above) t % 4 times, mirrored east-west
     * first when t >= 4: the cells, a trunk edge's facing and a limb's arms all turned with it.
     */
    private static Template turned(Template tp, int t) {
        if (t == 0) {
            return tp;
        }
        return TURNED.computeIfAbsent(tp.name + "/" + t, k -> {
            int n = tp.x.length;
            int[] x = new int[n], z = new int[n];
            String[] state = new String[n];
            byte[] shell = new byte[n];
            Map<String, String> states = new HashMap<>();
            for (int i = 0; i < n; i++) {
                int a = t >= 4 ? -tp.x[i] : tp.x[i], b = tp.z[i];
                for (int q = 0; q < t % 4; q++) {
                    int na = -b;
                    b = a;
                    a = na;
                }
                x[i] = a;
                z[i] = b;
                state[i] = states.computeIfAbsent(tp.state[i], s -> turnState(s, t));
                shell[i] = turnShell(tp.shell[i], t);
            }
            return new Template(tp.name, x, tp.y.clone(), z, state, shell);
        });
    }

    /** A leaf's open sides turned with its template. */
    private static byte turnShell(byte sh, int t) {
        if (sh == 0 || sh == INNER) {
            return sh;
        }
        int out = sh & 0b1100;
        for (int d : new int[] {0, 1, 4, 5}) {
            if ((sh >> d & 1) == 0) {
                continue;
            }
            int a = t >= 4 ? -SIDES[d][0] : SIDES[d][0], b = SIDES[d][2];
            for (int q = 0; q < t % 4; q++) {
                int na = -b;
                b = a;
                a = na;
            }
            out |= 1 << (a == 1 ? 0 : a == -1 ? 1 : b == 1 ? 4 : 5);
        }
        return (byte) out;
    }

    private static String turnDir(String d, int t) {
        if (t >= 4) {
            d = d.equals("east") ? "west" : d.equals("west") ? "east" : d;
        }
        for (int i = 0; i < 4; i++) {
            if (TURN[i].equals(d)) {
                return TURN[(i + t % 4) % 4];
            }
        }
        return d;
    }

    private static String turnState(String s, int t) {
        int open = s.indexOf('[');
        if (open < 0) {
            return s;
        }
        StringBuilder out = new StringBuilder(s.substring(0, open + 1));
        String[] pairs = s.substring(open + 1, s.length() - 1).split(",");
        for (int i = 0; i < pairs.length; i++) {
            String[] kv = pairs[i].split("=");
            String key = kv[0], value = kv[1];
            if (key.equals("facing")) {
                value = turnDir(value, t);
            } else if (!key.equals("up") && !key.equals("down")) {
                key = turnDir(key, t);
            }
            out.append(i > 0 ? "," : "").append(key).append('=').append(value);
        }
        return out.append(']').toString();
    }

    private static String axis(String log, int dx, int dz) {
        return log + (dx != 0 ? "[axis=x]" : dz != 0 ? "[axis=z]" : "[axis=y]");
    }

    /** A trunk that forks into two or three limbs, each carrying a lumpy crown. */
    private static void broadleaf(Canopy c, int x, int y, int z, SplittableRandom rng, String log, String leaves, int height, int radius) {
        int fork = height / 2 + rng.nextInt(2);
        // roots flaring at the base
        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (rng.nextDouble() < 0.5) {
                c.wood(x + d[0], y, z + d[1], axis(log, d[0], d[1]));
            }
        }
        for (int i = 0; i < fork; i++) {
            c.wood(x, y + i, z, log);
        }
        int limbs = 2 + rng.nextInt(2);
        double a0 = rng.nextDouble() * Math.PI * 2;
        for (int l = 0; l < limbs; l++) {
            double a = a0 + l * Math.PI * 2 / limbs + rng.nextDouble() * 0.6;
            int len = height - fork;
            double bx = x, bz = z;
            int by = y + fork;
            for (int s = 0; s < len; s++) {
                bx += Math.cos(a) * 0.55;
                bz += Math.sin(a) * 0.55;
                by++;
                c.wood((int) Math.round(bx), by, (int) Math.round(bz), log);
            }
            blob(c, (int) Math.round(bx), by, (int) Math.round(bz), radius, radius - 1, leaves, rng);
        }
        blob(c, x, y + height - 1, z, radius, radius - 1, leaves, rng);
    }

    /** A ragged ball of leaves, flattened and with holes, a little drooping at the edges. */
    private static void blob(Canopy c, int cx, int cy, int cz, int r, int ry, String leaves, SplittableRandom rng) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    double d = (dx * dx + dz * dz) / (double) (r * r) + dy * dy / (double) (ry * ry + 0.5);
                    if (d <= 1 + rng.nextDouble() * 0.3 - 0.15 && rng.nextDouble() > 0.08) {
                        c.leaves(cx + dx, cy + dy, cz + dz, leaf(leaves));
                    }
                }
                if (dx * dx + dz * dz >= (r - 1) * (r - 1) && dx * dx + dz * dz <= r * r && rng.nextDouble() < 0.18) {
                    c.leaves(cx + dx, cy - ry - 1, cz + dz, leaf(leaves));
                }
            }
        }
    }

    /** A tall pine: bare lower trunk, then layered skirts of needles to a spire. */
    private static void pine(Canopy c, int x, int y, int z, SplittableRandom rng) {
        int h = 13 + rng.nextInt(9);
        int bare = 4 + rng.nextInt(3);
        for (int i = 0; i < h; i++) {
            c.wood(x, y + i, z, "minecraft:spruce_log");
        }
        for (int i = bare; i <= h; i++) {
            int fromTop = h - i;
            int r = Math.min(4, 1 + fromTop / 3) - ((fromTop % 3 == 2) ? 1 : 0);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) <= r + (r > 2 ? 1 : 0) && (dx != 0 || dz != 0 || i >= h - 1) && rng.nextDouble() > 0.1) {
                        c.leaves(x + dx, y + i, z + dz, "minecraft:spruce_leaves" + LEAF);
                    }
                }
            }
        }
        c.leaves(x, y + h + 1, z, "minecraft:spruce_leaves" + LEAF);
    }

    /** A dead snag: grey trunk, a couple of bare broken limbs. */
    private static void dead(Canopy c, int x, int y, int z, SplittableRandom rng) {
        String log = rng.nextBoolean() ? "minecraft:stripped_oak_log" : "minecraft:pale_oak_log";
        int h = 5 + rng.nextInt(7);
        for (int i = 0; i < h; i++) {
            c.wood(x, y + i, z, log);
        }
        for (int l = 0; l < 2 + rng.nextInt(2); l++) {
            int at = y + 2 + rng.nextInt(Math.max(1, h - 2));
            int[] d = new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}[rng.nextInt(4)];
            int len = 1 + rng.nextInt(3);
            for (int s = 1; s <= len; s++) {
                c.wood(x + d[0] * s, at + s / 2, z + d[1] * s, axis(log, d[0], d[1]));
            }
        }
    }

    /** A sickly grey tree, its pale canopy trailing to the ground. */
    private static void sick(Canopy c, int x, int y, int z, SplittableRandom rng) {
        broadleaf(c, x, y, z, rng, "minecraft:pale_oak_log", "minecraft:pale_oak_leaves", 6 + rng.nextInt(4), 3);
        // grey leaves trailing down from the crown like moss
        for (int i = 0; i < 8; i++) {
            int dx = rng.nextInt(5) - 2, dz = rng.nextInt(5) - 2;
            int len = 1 + rng.nextInt(3);
            for (int s = 0; s < len; s++) {
                c.leaves(x + dx, y + 4 - s, z + dz, "minecraft:pale_oak_leaves" + LEAF);
            }
        }
    }

    /** Vanilla leaves are kept from decaying; the tree blocks never decay. */
    private static String leaf(String leaves) {
        return leaves.startsWith("minecraft:") ? leaves + LEAF : leaves;
    }

    private static void bush(Canopy c, int x, int y, int z, SplittableRandom rng, String leaves) {
        c.wood(x, y, z, templates() ? "tree_oak_limb_8[up=true]" : "minecraft:oak_log");
        blob(c, x, y + 1, z, 2, 1, leaves, rng);
    }

    private static void young(Canopy c, int x, int y, int z, SplittableRandom rng) {
        int h = 4 + rng.nextInt(3);
        for (int i = 0; i < h; i++) {
            c.wood(x, y + i, z, "minecraft:oak_log");
        }
        blob(c, x, y + h, z, 2, 2, rng.nextBoolean() ? "minecraft:oak_leaves" : "minecraft:birch_leaves", rng);
    }

    private Trees() {
    }
}
