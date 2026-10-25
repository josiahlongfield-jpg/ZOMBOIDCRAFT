package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * Shop signs: a fascia over the shop window, neon hung off the wall, an OPEN sign behind the
 * glass. The blocks come from tools/city/signs.py, which draws each sign whole and cuts it into
 * one block per slice; the names and widths here follow its FASCIA and BLADES lists.
 *
 * Signs face the wall they hang on, so on a front (local z = 0, facing north) they face south.
 * Slice 0 is the left end as you read the sign from the street, which is the high-x end.
 */
public final class Signs {
    private static final String[] FASCIA = {"medline", "stopnsave", "suds", "patriot", "cash", "joes",
            "luckypawn", "tonys", "thrift", "riverside", "kessler"};
    private static final int[] FASCIA_WIDTH = {5, 4, 5, 4, 4, 5, 5, 4, 6, 5, 5};
    private static final String[] BLADES = {"hotel", "bar", "pawn", "diner"};
    private static final int[] BLADE_LENGTH = {5, 3, 4, 5};
    /** The last few shop signs put up, so neighbours don't repeat. Only steers variety. */
    private static final int[] RECENT = {-1, -1, -1, -1, -1};
    private static int recentAt;

    /**
     * A fascia centred in a band span blocks wide starting at x0, or nothing when no sign fits.
     * Now and then one panel has fallen out.
     */
    public static void fascia(Placer p, SplittableRandom rng, int x0, int y, int z, int span) {
        int pick = rng.nextInt(FASCIA.length);
        for (int tries = 0; tries < FASCIA.length && (FASCIA_WIDTH[pick] > span || recent(pick)); tries++) {
            pick = (pick + 1) % FASCIA.length;
        }
        int k = FASCIA_WIDTH[pick];
        if (k > span) {
            return;
        }
        RECENT[recentAt] = pick;
        recentAt = (recentAt + 1) % RECENT.length;
        int start = x0 + (span - k) / 2;
        int fallen = rng.nextDouble() < 0.12 ? rng.nextInt(k) : -1;
        for (int i = 0; i < k; i++) {
            if (i != fallen) {
                p.set(start + k - 1 - i, y, z, "sign_" + FASCIA[pick] + "_" + i + "[facing=south]");
            }
        }
    }

    private static boolean recent(int pick) {
        for (int r : RECENT) {
            if (r == pick) {
                return true;
            }
        }
        return false;
    }

    /** Which neon blade to hang: pass it to {@link #bladeLength} and {@link #blade}. */
    public static int pickBlade(SplittableRandom rng) {
        return rng.nextInt(BLADES.length);
    }

    public static int bladeLength(int pick) {
        return BLADE_LENGTH[pick];
    }

    /**
     * A neon blade hung off the front at x, a letter a block from the top down to bottom. Lit in
     * some places, with a letter or two flickered out; dead in the rest.
     */
    public static void blade(Placer p, SplittableRandom rng, int pick, int x, int bottom, int z) {
        boolean lit = rng.nextDouble() < 0.45;
        int n = BLADE_LENGTH[pick];
        for (int i = 0; i < n; i++) {
            boolean on = lit && rng.nextDouble() > 0.15;
            p.set(x, bottom + n - 1 - i, z, "blade_" + BLADES[pick] + "_" + i + (on ? "" : "_off") + "[facing=south]");
        }
    }

    /** A neon OPEN sign hung just behind the glass, two blocks wide, its left end at x. */
    public static void open(Placer p, SplittableRandom rng, int x, int y, int z) {
        String state = rng.nextDouble() < 0.3 ? "" : "_off";
        p.set(x, y, z, "neon_open_0" + state + "[facing=north]");
        p.set(x - 1, y, z, "neon_open_1" + state + "[facing=north]");
    }

    private Signs() {
    }
}
