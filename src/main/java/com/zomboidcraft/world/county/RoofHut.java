package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The little room a stair comes up into on a flat roof: walls round the stair's head, a roof
 * over it a storey up, a lamp, and a pair of doors out onto the roof, so players and zombies
 * can walk up onto any roof a building's stairs reach.
 */
public final class RoofHut {
    /**
     * The hut over x0 .. x1 by z0 .. z1 (its walls on that edge) standing on the roof slab at
     * roofY: clear inside up to roofY + 3, roofed at roofY + 4. The doors are two wide from
     * doorX in its north or south wall ("north"/"south" names the wall), the roof outside them
     * cleared of anything in the way.
     */
    public static void build(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1, int roofY, String wall,
                             int doorX, String side) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = roofY + 1; y <= roofY + 3; y++) {
                    if (edge) {
                        p.set(x, y, z, wall);
                    } else if (!stair(p.get(x, y, z))) {
                        p.set(x, y, z, "air");
                    }
                }
                p.set(x, roofY + 4, z, edge ? wall : "roof");
            }
        }
        // a lip round the hut's roof, stained where the rain runs off it
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if ((x == x0 || x == x1 || z == z0 || z == z1) && rng.nextDouble() < 0.7) {
                    p.set(x, roofY + 5, z, "stone_trim");
                }
            }
        }
        int dz = side.equals("north") ? z0 : z1, out = side.equals("north") ? -1 : 1;
        String facing = side.equals("north") ? "south" : "north";
        for (int x = doorX; x <= doorX + 1; x++) {
            for (int y = roofY + 1; y <= roofY + 3; y++) {
                p.set(x, y, dz + out, "air");
            }
            p.set(x, roofY + 3, dz, wall);
        }
        Doors.pair(p, rng, doorX, roofY + 1, dz, Doors.STEEL, facing);
        p.set((x0 + x1) / 2, roofY + 3, (z0 + z1) / 2 + (side.equals("north") ? 1 : -1),
                rng.nextDouble() < 0.15 ? "ceiling_light" : "ceiling_light_off");
        // a vent stack through the hut's roof, the way they all have one
        if (rng.nextBoolean()) {
            p.set(x1 - 1, roofY + 5, z1 - 1, "bunk_vent_stack");
            p.set(x1 - 1, roofY + 6, z1 - 1, "bunk_vent_cap");
        }
    }

    private static boolean stair(String b) {
        return b != null && (b.contains("stairs") || b.contains("ladder"));
    }

    private RoofHut() {
    }
}
