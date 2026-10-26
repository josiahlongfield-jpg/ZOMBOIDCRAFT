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
        if (FASCIA_WIDTH[pick] <= span) {
            RECENT[recentAt] = pick;
            recentAt = (recentAt + 1) % RECENT.length;
            hang(p, rng, pick, x0, y, z, span);
        }
    }

    /** The sign for one brand, centred over the span; false (nothing placed) when it doesn't fit. */
    public static boolean fascia(Placer p, SplittableRandom rng, String brand, int x0, int y, int z, int span) {
        for (int pick = 0; pick < FASCIA.length; pick++) {
            if (FASCIA[pick].equals(brand)) {
                if (FASCIA_WIDTH[pick] > span) {
                    return false;
                }
                hang(p, rng, pick, x0, y, z, span);
                return true;
            }
        }
        return false;
    }

    private static void hang(Placer p, SplittableRandom rng, int pick, int x0, int y, int z, int span) {
        int k = FASCIA_WIDTH[pick];
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

    /** The neon blade a shop hangs out, spelling what it sells; null for the ones that never had one. */
    public static String bladeFor(Shops.Type shop) {
        return switch (shop) {
            case PHARMACY -> "drugs";
            case LAUNDROMAT -> "wash";
            case GUNS -> "guns";
            case CASH -> "cash";
            case DINER -> "diner";
            case PAWN -> "pawn";
            case PIZZA -> "pizza";
            case LIQUOR -> "liquor";
            default -> null;
        };
    }

    /** How many letters (blocks) a blade has. */
    public static int bladeLength(String blade) {
        return switch (blade) {
            case "bar", "eat" -> 3;
            case "pawn", "cafe", "guns", "wash", "cash" -> 4;
            case "liquor", "rialto" -> 6;
            default -> 5;
        };
    }

    /**
     * A neon blade hung off the front at x, a letter a block from the top down to bottom. Lit in
     * some places, with a letter or two flickered out; dead in the rest.
     */
    public static void blade(Placer p, SplittableRandom rng, String blade, int x, int bottom, int z) {
        boolean lit = rng.nextDouble() < 0.45;
        int n = bladeLength(blade);
        for (int i = 0; i < n; i++) {
            boolean on = lit && rng.nextDouble() > 0.15;
            p.set(x, bottom + n - 1 - i, z, "blade_" + blade + "_" + i + (on ? "" : "_off") + "[facing=south]");
        }
        if (lit) {
            glow(p, blade, x, bottom - 1, z);
        }
    }

    /**
     * The lamp box under a lit blade, throwing its colour down the wall and across the wet
     * street (the blade's own tubes would light amber under Photon).
     */
    public static void glow(Placer p, String blade, int x, int y, int z) {
        p.setIfAir(x, y, z, ShopLights.bladeGlow(blade));
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
