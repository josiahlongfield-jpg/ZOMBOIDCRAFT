package com.zomboidcraft.world.county;

import java.util.SplittableRandom;

/**
 * The trees of the county, grown block by block: broad oaks and maples, pale sycamores along
 * the water, tall pines on the knobs, dead snags, and sick grey trees. Blocks go
 * into a {@link Canopy} that keeps trunks over leaves where trees overlap.
 */
public final class Trees {
    public interface Canopy {
        /** Wood always wins over leaves and moss. */
        void wood(int x, int y, int z, String block);

        void leaves(int x, int y, int z, String block);
    }

    public enum Kind { OAK, MAPLE, SYCAMORE, PINE, DEAD, SICK, BUSH, YOUNG }

    private static final String LEAF = "[persistent=true]";

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
        switch (kind) {
            case OAK -> broadleaf(c, x, y, z, rng, "minecraft:oak_log", "minecraft:oak_leaves", 7 + rng.nextInt(5), 4 + rng.nextInt(2));
            case MAPLE -> broadleaf(c, x, y, z, rng, "minecraft:dark_oak_log", "minecraft:dark_oak_leaves", 8 + rng.nextInt(6), 3 + rng.nextInt(2));
            case SYCAMORE -> broadleaf(c, x, y, z, rng, "minecraft:birch_log", "minecraft:oak_leaves", 9 + rng.nextInt(6), 4);
            case PINE -> pine(c, x, y, z, rng);
            case DEAD -> dead(c, x, y, z, rng);
            case SICK -> sick(c, x, y, z, rng);
            case BUSH -> bush(c, x, y, z, rng, rng.nextBoolean() ? "minecraft:oak_leaves" : "minecraft:dark_oak_leaves");
            case YOUNG -> young(c, x, y, z, rng);
        }
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
                        c.leaves(cx + dx, cy + dy, cz + dz, leaves + LEAF);
                    }
                }
                if (dx * dx + dz * dz >= (r - 1) * (r - 1) && dx * dx + dz * dz <= r * r && rng.nextDouble() < 0.18) {
                    c.leaves(cx + dx, cy - ry - 1, cz + dz, leaves + LEAF);
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

    private static void bush(Canopy c, int x, int y, int z, SplittableRandom rng, String leaves) {
        c.wood(x, y, z, "minecraft:oak_log");
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
