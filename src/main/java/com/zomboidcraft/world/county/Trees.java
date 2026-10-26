package com.zomboidcraft.world.county;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** One template: its blocks relative to the foot of its trunk. */
    private record Template(String name, int[] x, int[] y, int[] z, String[] state) {}

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
                Template tp = new Template(name, x, y, z, state);
                out.computeIfAbsent(sp, k -> new ArrayList<>()).add(tp);
                out.computeIfAbsent(sp + "/" + size, k -> new ArrayList<>()).add(tp);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't read the tree templates", e);
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
        for (int i = 0; i < t.x.length; i++) {
            String s = t.state[i];
            if (s.contains("_leaves")) {
                if (colour != null) {
                    s = recolour(s, colour);
                    // a sick tree has lost half its leaves
                    if (kind == Kind.SICK && rng.nextDouble() < 0.45) {
                        continue;
                    }
                }
                c.leaves(x + t.x[i], y + t.y[i], z + t.z[i], s);
            } else {
                c.wood(x + t.x[i], y + t.y[i], z + t.z[i], s);
            }
        }
    }

    private static final Map<String, String> RECOLOURED = new ConcurrentHashMap<>();

    /** "tree_maple_leaves_sparse" in red is "tree_maple_leaves_red_sparse"; sick ones are all one grey. */
    private static String recolour(String leaves, String colour) {
        return RECOLOURED.computeIfAbsent(leaves + "|" + colour, k -> {
            boolean sparse = leaves.endsWith("_sparse");
            if (colour.equals("sick")) {
                return sparse ? "tree_leaves_sick_sparse" : "tree_leaves_sick";
            }
            String base = sparse ? leaves.substring(0, leaves.length() - "_sparse".length()) : leaves;
            return base + "_" + colour + (sparse ? "_sparse" : "");
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
            }
            return new Template(tp.name, x, tp.y.clone(), z, state);
        });
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
