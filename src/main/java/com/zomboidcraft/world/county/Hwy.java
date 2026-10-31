package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import java.util.SplittableRandom;

/**
 * What the three highway places ({@link RestArea}, {@link TollPlaza}, {@link Interchange}) put up
 * along their roads, from the hw_ kit (tools/city/highway.py): the signs, the overhead gantries
 * they hang from, the high lamps over the lanes, the wrecks and debris of the day the roads jammed.
 */
final class Hwy {
    static final String[] FACING = {"north", "east", "south", "west"};
    static final int[][] DIR = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    private Hwy() {
    }

    static int side(String facing) {
        for (int i = 0; i < 4; i++) {
            if (FACING[i].equals(facing)) {
                return i;
            }
        }
        return 0;
    }

    /**
     * A sign cols x rows tiles (hw_g_KEY_CR) facing out, its bottom row at y, (x, z) the tile at the
     * reader's left; legs: the columns with a post down to the ground under them (y0 the ground).
     */
    static void sign(BlockSink out, String key, int cols, int rows, int x, int y, int z, String facing, int[] legs, String leg) {
        int f = side(facing);
        // the reader faces the sign, so their right is the facing turned a quarter anticlockwise
        int[] right = DIR[(f + 3) & 3];
        for (int c = 0; c < cols; c++) {
            int cx = x + right[0] * c, cz = z + right[1] * c;
            for (int r = 0; r < rows; r++) {
                out.set(cx, y + r, cz, "hw_g_" + key + "_" + c + r + "[facing=" + facing + "]");
            }
            if (legs != null) {
                for (int l : legs) {
                    if (l == c) {
                        for (int yy = 1; yy < y; yy++) {
                            out.set(cx, yy, cz, leg + "[facing=" + facing + "]");
                        }
                    }
                }
            }
        }
    }

    /**
     * An overhead sign gantry across from (x0, z0) to (x1, z1) (one axis), the truss at height y
     * on its two legs and their footings.
     */
    static void gantry(BlockSink out, int x0, int z0, int x1, int z1, int y) {
        boolean alongX = z0 == z1;
        out.set(x0, 0, z0, "hw_conc");
        out.set(x1, 0, z1, "hw_conc");
        out.set(x0, 1, z0, "hw_gantry_foot");
        out.set(x1, 1, z1, "hw_gantry_foot");
        for (int yy = 2; yy <= y; yy++) {
            out.set(x0, yy, z0, "hw_gantry_post");
            out.set(x1, yy, z1, "hw_gantry_post");
        }
        if (alongX) {
            for (int x = Math.min(x0, x1) + 1; x < Math.max(x0, x1); x++) {
                out.set(x, y, z0, "hw_gantry_beam[axis=x]");
            }
        } else {
            for (int z = Math.min(z0, z1) + 1; z < Math.max(z0, z1); z++) {
                out.set(x0, y, z, "hw_gantry_beam[axis=z]");
            }
        }
    }

    /**
     * A high lamp on the verge at (x, z): the pole height tall, the arm reaching out toward the
     * road (toward: 0 north .. 3 west) and the head on its end. They all burn now.
     */
    static void lamp(BlockSink out, int x, int z, int toward, int height) {
        for (int y = 1; y <= height; y++) {
            out.set(x, y, z, "streetlight_pole");
        }
        int ax = x + DIR[toward][0], az = z + DIR[toward][1];
        out.set(ax, height, az, "streetlight_arm[facing=" + ((toward & 1) == 1 ? "east" : "south") + "]");
        int hx = ax + DIR[toward][0], hz = az + DIR[toward][1];
        out.set(hx, height, hz, "streetlight_head[facing=" + FACING[toward] + "]");
    }

    /** Lamps every spacing along a roadway on its right verge (or left), clear of what's there. */
    static void lamps(BlockSink out, Lanes l, double from, double to, int spacing, boolean right, int w, int d) {
        for (double s = from; s <= Math.min(to, l.length() - 2); s += spacing) {
            double[] q = l.point(s);
            double o = (l.half + 2.2) * (right ? 1 : -1);
            int[] c = l.beside(s, o);
            if (c[0] < 1 || c[1] < 1 || c[0] >= w - 1 || c[1] >= d - 1) {
                continue;
            }
            int top = (int) Math.floor(q[2]);
            // the arm points back across the road
            double vx = right ? q[4] : -q[4], vz = right ? -q[3] : q[3];
            int toward = Math.abs(vx) > Math.abs(vz) ? (vx > 0 ? 1 : 3) : (vz > 0 ? 2 : 0);
            BlockSink up = shifted(out, top);
            if (up.get(c[0], 1, c[1]) != null) {
                continue;
            }
            lamp(up, c[0], c[1], toward, 9);
        }
    }

    /** The sink raised by dy (to put a prop on a road at its own height). */
    static BlockSink shifted(BlockSink out, int dy) {
        if (dy == 0) {
            return out;
        }
        return new BlockSink() {
            @Override
            public void set(int x, int y, int z, String block) {
                out.set(x, y + dy, z, block);
            }

            @Override
            public String get(int x, int y, int z) {
                return out.get(x, y + dy, z);
            }

            @Override
            public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
                out.car(x, y + dy, z, yaw, minLength, maxLength, seed);
            }

            @Override
            public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
                out.vehicle(x, y + dy, z, yaw, models, seed);
            }
        };
    }

    /** Minecraft's yaw for a direction of travel (dx, dz): 0 faces south, 90 west. */
    static float yaw(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * Abandoned cars along a roadway from a to b along, in the lane s to the right of its centre:
     * nose to tail like the jam they were in, a gap here and there, a few turned where they were
     * shoved, doors open; wrecked: the share burnt out or crushed.
     */
    static void jam(BlockSink out, Lanes l, double a, double b, double s, double gap, double wrecked, SplittableRandom rng) {
        double at = a + rng.nextDouble() * 3;
        while (at < b) {
            double[] q = l.point(at);
            double px = q[0] - q[4] * s, pz = q[1] + q[3] * s;
            float yaw = yaw(q[3], q[4]) + (float) rng.nextGaussian() * 6f;
            if (rng.nextDouble() < 0.12) {
                yaw += (float) (rng.nextBoolean() ? 25 : -25);
            }
            int top = (int) Math.floor(q[2]);
            double r = rng.nextDouble();
            if (r < wrecked * 0.4) {
                out.vehicle(px, top + 1, pz, yaw, "burnt", rng.nextLong());
            } else if (r < wrecked) {
                out.vehicle(px, top + 1, pz, yaw, "abandoned", rng.nextLong());
            } else {
                out.car(px, top + 1, pz, yaw, 3.5, 6.5, rng.nextLong());
            }
            at += 6.5 + rng.nextDouble() * 2 + (rng.nextDouble() < gap ? 8 + rng.nextDouble() * 20 : 0);
        }
    }

    /** Litter on a road: glass, papers, a lost shoe, a suitcase burst open. */
    static void litter(BlockSink out, Lanes l, double a, double b, double density, SplittableRandom rng) {
        String[] bits = {"glass_shards", "paper", "trash_bag[facing=north]", "cardboard_box[facing=north]", "debris_chunk[facing=east]", "tire[axis=x]"};
        for (double s = a; s < b; s += 1) {
            if (rng.nextDouble() > density) {
                continue;
            }
            double o = (rng.nextDouble() * 2 - 1) * (l.half + 1);
            int[] c = l.beside(s, o);
            int top = (int) Math.floor(l.point(s)[2]);
            if (out.get(c[0], top + 1, c[1]) == null) {
                out.set(c[0], top + 1, c[1], bits[rng.nextInt(bits.length)]);
            }
        }
    }
}
