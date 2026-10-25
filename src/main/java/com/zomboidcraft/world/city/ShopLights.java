package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * The glowing box lamps over shop doors and under canopies. Most are a cold purple that the wet
 * sidewalk picks up in the rain; now and then one is green or amber, and some are long dead.
 */
public final class ShopLights {
    /** A lamp block: dead (an unlit white box) with chance dead, otherwise mostly purple. */
    public static String lamp(SplittableRandom rng, double dead) {
        if (rng.nextDouble() < dead) {
            return "minecraft:smooth_quartz";
        }
        double r = rng.nextDouble();
        return r < 0.72 ? "minecraft:pearlescent_froglight" : r < 0.86 ? "minecraft:verdant_froglight" : "minecraft:ochre_froglight";
    }

    private ShopLights() {
    }
}
