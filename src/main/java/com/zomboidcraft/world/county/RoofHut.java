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

    /**
     * A straight flight two wide from a top floor's slab at y up through the flat roof at roofY,
     * with the hut over its head. Drawn climbing east from its foot at x0, in lanes z0 and z0 + 1:
     * the first tread at x0 + 1, the last in the roof's plane at x0 + h (h = roofY - y), then a
     * landing two long at roof level; draw it in a turned frame to climb another way. The hut
     * stands over the last treads and the landing (x0 + h - 4 .. x0 + h + 3 by z0 - 1 .. z0 + 2,
     * walls on that edge), its doors in its north wall (doorSide "north") or south over the
     * landing. Needs the top floor clear over the lanes from x0 to x0 + h + 2. A rail, when
     * given, runs up the flight's south side (an edge piece, as ext_rail_edge_dark).
     */
    public static void flight(Placer p, SplittableRandom rng, int x0, int z0, int y, int roofY, String wall,
                              String tread, String doorSide, String rail) {
        int h = roofY - y;
        for (int i = 1; i <= h; i++) {
            for (int z = z0; z <= z0 + 1; z++) {
                p.set(x0 + i, y + i, z, tread + "[facing=east]");
                for (int r = 1; r <= 3; r++) {
                    p.set(x0 + i, y + i + r, z, "air");
                }
            }
            if (rail != null && y + i + 1 < roofY) {
                p.set(x0 + i, y + i + 1, z0 + 1, rail + "[facing=south]");
            }
        }
        for (int x = x0 + h + 1; x <= x0 + h + 2; x++) {
            for (int z = z0; z <= z0 + 1; z++) {
                p.set(x, roofY, z, "roof");
            }
        }
        build(p, rng, x0 + h - 4, z0 - 1, x0 + h + 3, z0 + 2, roofY, wall, x0 + h + 1, doorSide);
    }

    private static boolean stair(String b) {
        return b != null && (b.contains("stairs") || b.contains("ladder"));
    }

    private RoofHut() {
    }
}
