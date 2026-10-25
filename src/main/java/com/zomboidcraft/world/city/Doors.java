package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * Doors as people left them when the city fell: most pulled shut, plenty hanging open, a few
 * torn off. Zombies wander in through the open ones and bash the shut ones down.
 *
 * Facing works like vanilla doors: a door facing south sits on the north side of its block, so
 * a door set into a wall at local z = 0 faces south to sit flush with the street.
 */
public final class Doors {
    public static final String SHOP = "shop_door", STEEL = "fire_door", FLAT = "flat_door";

    public static void place(Placer p, int x, int y, int z, String type, String facing, String hinge, boolean open) {
        String props = "[facing=" + facing + ",hinge=" + hinge + ",open=" + open;
        p.set(x, y, z, type + props + ",half=lower]");
        p.set(x, y + 1, z, type + props + ",half=upper]");
    }

    /** A door left shut, open or gone; a missing glass door leaves its shards behind. */
    public static void abandoned(Placer p, SplittableRandom rng, int x, int y, int z, String type, String facing, String hinge) {
        double r = rng.nextDouble();
        if (r < 0.12) {
            p.set(x, y, z, type.equals(SHOP) ? "glass_shards" : "air");
            p.set(x, y + 1, z, "air");
            return;
        }
        place(p, x, y, z, type, facing, hinge, r < 0.5);
    }

    /** Double doors at x and x + 1 of a wall along x, hinged on their outer edges. */
    public static void pair(Placer p, SplittableRandom rng, int x, int y, int z, String type, String facing) {
        boolean south = facing.equals("south");
        abandoned(p, rng, x, y, z, type, facing, south ? "right" : "left");
        abandoned(p, rng, x + 1, y, z, type, facing, south ? "left" : "right");
    }

    public static String hinge(SplittableRandom rng) {
        return rng.nextBoolean() ? "left" : "right";
    }

    private Doors() {
    }
}
