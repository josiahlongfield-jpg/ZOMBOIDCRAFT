package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * The glowing box lamps over shop doors and under canopies. Most are a cold purple that the wet
 * sidewalk picks up in the rain; now and then one is green or amber, and some are long dead.
 *
 * They're froglights because Photon colours a light by its block: the mod's own emitters all
 * come out amber, so wherever a colour should spill (a neon blade, a window) a froglight throws it.
 */
public final class ShopLights {
    /** A lamp block: dead (a light box gone dark, its glass yellowed) with chance dead, otherwise mostly purple. */
    public static String lamp(SplittableRandom rng, double dead) {
        if (rng.nextDouble() < dead) {
            return "front_lampbox_dead";
        }
        double r = rng.nextDouble();
        return r < 0.72 ? "minecraft:pearlescent_froglight" : r < 0.86 ? "minecraft:verdant_froglight" : "minecraft:ochre_froglight";
    }

    /** The lamp box under a lit neon blade, in its tubes' colour (see tools/city/signs.py BLADES). */
    public static String bladeGlow(String blade) {
        return switch (blade) {
            case "drugs", "wash", "cash" -> "minecraft:verdant_froglight";
            case "pawn", "eat", "rialto", "cafe", "guns", "pizza" -> "minecraft:ochre_froglight";
            default -> "minecraft:pearlescent_froglight";
        };
    }

    /**
     * Now and then a room high up still glows: a lamp just inside the glass, nearly always purple.
     * Picked by a hash of the spot, so it costs the builder's random numbers nothing and a building
     * comes out the same whichever chunk builds it.
     */
    public static String window(long spot, double chance) {
        double r = unit(spot);
        if (r >= chance) {
            return null;
        }
        return r < chance * 0.85 ? "minecraft:pearlescent_froglight" : "minecraft:verdant_froglight";
    }

    /** A block's hash for {@link #window}, from where it lands in the world. */
    public static long spot(Placer p, int x, int y, int z) {
        int[] at = p.world(x, z);
        return ((long) at[0] * 73856093L) ^ ((long) y * 19349663L) ^ ((long) at[1] * 83492791L);
    }

    private static double unit(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private ShopLights() {
    }
}
