package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Putting up {@link Mall}'s signs from the ml_ kit (tools/city/mall.py): the shops' names in
 * channel letters over their fronts, the big names on the outside, the pylon by the street, the
 * directories and the banners. Every sign's tile 0 is its viewer's left.
 */
final class MallSigns {
    private MallSigns() {
    }

    /**
     * Where column c of a sign cols wide starting at (x0, z0) stands when it faces facing: along
     * the wall, tile 0 at its viewer's left (high x facing north, low x facing south, high z
     * facing east, low z facing west).
     */
    static int[] at(int x0, int z0, int cols, int c, String facing) {
        return switch (facing) {
            case "north" -> new int[] {x0 + cols - 1 - c, z0};
            case "south" -> new int[] {x0 + c, z0};
            case "east" -> new int[] {x0, z0 + cols - 1 - c};
            default -> new int[] {x0, z0 + c};
        };
    }

    /**
     * A shop's name over its front, four by one from x0 along the band facing north (the unit's
     * frame); some letters lit on the generator and some dark, now and then one fallen.
     */
    static void brand(Placer q, SplittableRandom rng, String key, int x0, int y, int z, boolean lit) {
        boolean flicker = lit && rng.nextDouble() < 0.3;
        for (int c = 0; c < 4; c++) {
            if (rng.nextDouble() < 0.06) {
                continue;
            }
            boolean on = lit && !(flicker && rng.nextDouble() < 0.4);
            q.set(x0 + 3 - c, y, z, "ml_sign_" + key + "_" + c + (on ? "_on" : "") + "[facing=north]");
        }
    }

    /**
     * One of the big names (mall, harlans, bellmont, cinema, food) in channel letters, cols by
     * rows, its lower left corner (seen from in front) at the wall's run from (x0, z0), bottom row
     * at y0; a letter dark or gone here and there.
     */
    static void name(Placer q, SplittableRandom rng, String key, int cols, int rows, int x0, int y0, int z0, String facing, boolean lit) {
        for (int i = 0; i < cols * rows; i++) {
            if (rng.nextDouble() < 0.04) {
                continue;
            }
            int[] xz = at(x0, z0, cols, i % cols, facing);
            boolean on = lit && rng.nextDouble() > 0.12;
            q.set(xz[0], y0 + rows - 1 - i / cols, xz[1], "ml_name_" + key + "_" + i + (on ? "_on" : "") + "[facing=" + facing + "]");
        }
    }

    /**
     * The pylon by the street: the six-by-four face toward the street to the north at z0, the same
     * panel to the lot behind it at z0 + 1, the two legs under it, the cap; lit while the mall
     * still has its generator.
     */
    static void pylon(Placer q, SplittableRandom rng, int x0, int z0, boolean lit) {
        for (int y = 1; y <= 7; y++) {
            for (int x : new int[] {x0, x0 + 5}) {
                q.set(x, y, z0, "ml_precast");
                q.set(x, y, z0 + 1, "ml_precast");
            }
        }
        for (int i = 0; i < 24; i++) {
            int c = i % 6, y = 11 - i / 6;
            boolean on = lit && rng.nextDouble() > 0.15;
            q.set(x0 + 5 - c, y, z0, "ml_pylon_" + i + (on ? "_on" : "") + "[facing=north]");
            q.set(x0 + c, y, z0 + 1, "ml_pylon_" + i + (on ? "_on" : "") + "[facing=south]");
        }
        for (int x = x0 - 1; x <= x0 + 6; x++) {
            q.set(x, 12, z0, "stone_trim");
            q.set(x, 12, z0 + 1, "stone_trim");
        }
    }

    /** A directory on its stand, two by two, facing facing from (x0, z0) along its run. */
    static void directory(Placer q, SplittableRandom rng, int x0, int z0, String facing, boolean lit) {
        for (int i = 0; i < 4; i++) {
            int[] xz = at(x0, z0, 2, i % 2, facing);
            q.set(xz[0], 2 - i / 2, xz[1], "ml_dir_" + i + (lit && rng.nextDouble() > 0.2 ? "_on" : "") + "[facing=" + facing + "]");
        }
    }

    /** A banner three long hung on its rods from the roof at top + 1 down to the banner's head at y. */
    static void banner(Placer q, SplittableRandom rng, int x, int y, int z, int top, String facing) {
        String kind = rng.nextDouble() < 0.5 ? "a" : "b";
        for (int yy = y + 1; yy <= top; yy++) {
            q.set(x, yy, z, "tx_rod");
        }
        for (int i = 0; i < 3; i++) {
            if (i == 2 && rng.nextDouble() < 0.25) {
                continue;  // torn off at the hem
            }
            q.set(x, y - i, z, "ml_banner_" + kind + "_" + i + "[facing=" + facing + "]");
        }
    }
}
