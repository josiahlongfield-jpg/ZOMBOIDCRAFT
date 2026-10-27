package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * A stair in a shaft of its own that players and zombies climb without ducking or jumping: two
 * wide all the way up, two flights to a storey switching back across a landing, a whole storey
 * between a flight and the one over it (a body's headroom over every step), a landing the full
 * width of the shaft at every floor with a two-wide way out onto the floor. On a flat roof it
 * rises one storey more into a hut with a door out onto the roof.
 *
 * The shaft's inside is x0 .. x0 + length(h) - 1 by z0 .. z0 + 3 (its walls one block round
 * that): the floors' landings at the west end (x0 and x0 + 1), the half landings at the east
 * end, the flight up from each floor in lanes z0 and z0 + 1 climbing east, the flight on up in
 * z0 + 2 and z0 + 3 climbing west. Storeys must be at least four blocks.
 */
final class Stairwell {
    static final int DEPTH = 4;

    /** How long the shaft's inside is for storeys h high: the two landings and the longer flight. */
    static int length(int h) {
        return 4 + (h + 1) / 2;
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final int x0, z0, y0, h, len, a;
    String wall = "concrete_panel", tread = "stairs_concrete", landing = "floor_concrete", roof = "roof",
            door = Doors.STEEL, lamp = "ceiling_light_off";

    Stairwell(Placer p, SplittableRandom rng, int x0, int z0, int y0, int h) {
        if (h < 4) {
            throw new IllegalArgumentException("storeys under four blocks leave no headroom: " + h);
        }
        this.p = p;
        this.rng = rng;
        this.x0 = x0;
        this.z0 = z0;
        this.y0 = y0;
        this.h = h;
        len = length(h);
        a = (h + 1) / 2;
    }

    /**
     * Builds the stair for floors 0 .. floors - 1 (floor k at y0 + k * h). ways[k] is the wall
     * floor k's way out goes through ("west", "north" or "south"; null for none). With hut the
     * stair climbs on to the roof at y0 + floors * h and the hut goes up over it, its door
     * through hutWay.
     */
    void build(int floors, String[] ways, boolean hut, String hutWay) {
        int levels = hut ? floors : floors - 1;     // storeys climbed
        int topY = y0 + levels * h;                  // the highest landing
        int x1 = x0 + len - 1, z1 = z0 + DEPTH - 1;
        // empty the shaft, then its walls where nothing stands already (the building's own walls stay)
        for (int y = y0 + 1; y <= topY + 3; y++) {
            boolean above = hut && y > topY;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    boolean edge = x < x0 || x > x1 || z < z0 || z > z1;
                    if (!edge) {
                        p.set(x, y, z, "air");
                    } else if (above) {
                        p.set(x, y, z, wall);
                    } else {
                        p.setIfAir(x, y, z, wall);
                    }
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, y0, z, landing);
            }
        }
        for (int k = 0; k <= levels; k++) {
            int y = y0 + k * h;
            // the floor's landing (the ground one is the shaft's floor, laid above)
            if (k > 0) {
                for (int z = z0; z <= z1; z++) {
                    int reach = z >= z0 + 2 ? x0 + 1 + (a - (h - a)) : x0 + 1;
                    for (int x = x0; x <= reach; x++) {
                        p.set(x, y, z, landing);
                    }
                }
            }
            if (k < levels) {
                flights(y);
            }
            if (k < floors && ways != null && k < ways.length && ways[k] != null) {
                way(y, ways[k], false);
            }
        }
        if (hut) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    boolean edge = x < x0 || x > x1 || z < z0 || z > z1;
                    p.set(x, topY + 4, z, edge ? wall : roof);
                }
            }
            p.set(x1, topY + 3, z1, lamp);
            way(topY, hutWay, true);
        }
    }

    /** The two flights from the floor at y to the one a storey up. */
    private void flights(int y) {
        for (int i = 1; i <= a; i++) {
            for (int z = z0; z <= z0 + 1; z++) {
                p.set(x0 + 1 + i, y + i, z, tread + "[facing=east]");
            }
        }
        for (int x = x0 + 2 + a; x <= x0 + 3 + a; x++) {
            for (int z = z0; z < z0 + DEPTH; z++) {
                p.set(x, y + a, z, landing);
            }
        }
        for (int j = 1; j <= h - a; j++) {
            for (int z = z0 + 2; z <= z0 + 3; z++) {
                p.set(x0 + 2 + a - j, y + a + j, z, tread + "[facing=west]");
            }
        }
    }

    /**
     * The way out of the landing at y: two wide through the given wall, the floor beyond it
     * cleared of whatever stood in the way; the roof hut's gets a pair of doors.
     */
    private void way(int y, String side, boolean doors) {
        int[][] cells;
        int dx, dz;
        switch (side) {
            case "north" -> {
                cells = new int[][] {{x0, z0 - 1}, {x0 + 1, z0 - 1}};
                dx = 0;
                dz = -1;
            }
            case "south" -> {
                cells = new int[][] {{x0, z0 + DEPTH}, {x0 + 1, z0 + DEPTH}};
                dx = 0;
                dz = 1;
            }
            default -> {
                cells = new int[][] {{x0 - 1, z0 + 1}, {x0 - 1, z0 + 2}};
                dx = -1;
                dz = 0;
            }
        }
        int high = Math.min(3, h - 1);
        for (int[] c : cells) {
            for (int r = 1; r <= high; r++) {
                p.set(c[0], y + r, c[1], "air");
                p.set(c[0] + dx, y + r, c[1] + dz, "air");
            }
            if (p.get(c[0], y, c[1]) == null) {
                p.set(c[0], y, c[1], landing);
            }
        }
        if (doors) {
            String facing = switch (side) {
                case "north" -> "south";
                case "south" -> "north";
                default -> "east";
            };
            if (side.equals("west")) {
                Doors.abandoned(p, rng, cells[0][0], y + 1, cells[0][1], door, facing, "right");
                Doors.abandoned(p, rng, cells[1][0], y + 1, cells[1][1], door, facing, "left");
            } else {
                Doors.pair(p, rng, cells[0][0], y + 1, cells[0][1], door, facing);
            }
            for (int[] c : cells) {
                p.set(c[0], y + 3, c[1], wall);
            }
        }
    }
}
