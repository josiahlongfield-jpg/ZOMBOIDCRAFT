package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;

/**
 * Shipping containers laid out from the ct_ kit (tools/city/ports.py): three blocks square and len
 * long, every outside cell a piece with its corrugations, rails, posts, castings and doors, the
 * middle the plain container cube nobody sees.
 */
final class Boxes {
    private Boxes() {
    }

    /** The world direction a facing's model +x runs along, as {dx, dz}. */
    private static int[] modelX(String facing) {
        return switch (facing) {
            case "east" -> new int[] {0, 1};
            case "south" -> new int[] {-1, 0};
            case "west" -> new int[] {0, -1};
            default -> new int[] {1, 0};
        };
    }

    /**
     * A container from (x, y, z), len long along x (or z), its colour given as container_red and
     * the like; the doors at the low end, the high end or both.
     */
    static void place(Placer p, int x, int y, int z, int len, String colour, boolean alongX, boolean doorsLow, boolean doorsHigh) {
        String c = colour.startsWith("container_") ? colour.substring("container_".length()) : colour;
        String kit = "ct_" + c + "_";
        String[] rows = {"b", "m", "t"};
        int[] width = alongX ? new int[] {0, 1} : new int[] {1, 0};
        for (int i = 0; i < len; i++) {
            boolean low = i == 0, high = i == len - 1 && !low;
            for (int k = 0; k < 3; k++) {
                for (int j = 0; j < 3; j++) {
                    String b;
                    if (low || high) {
                        String facing = low ? (alongX ? "west" : "north") : (alongX ? "east" : "south");
                        int[] mx = modelX(facing);
                        int col = mx[0] == width[0] && mx[1] == width[1] ? k : 2 - k;
                        boolean door = low ? doorsLow : doorsHigh;
                        b = kit + (door ? "door_" : "end_") + rows[j] + col + "[facing=" + facing + "]";
                    } else if (k != 1) {
                        String facing = alongX ? (k == 0 ? "north" : "south") : (k == 0 ? "west" : "east");
                        b = kit + (j == 0 ? "side_bot" : j == 2 ? "side_top" : "side") + "[facing=" + facing + "]";
                    } else if (j == 2) {
                        b = kit + "roof[facing=" + (alongX ? "north" : "west") + "]";
                    } else {
                        b = "container_" + c;
                    }
                    if (alongX) {
                        p.set(x + i, y + j, z + k, b);
                    } else {
                        p.set(x + k, y + j, z + i, b);
                    }
                }
            }
        }
    }
}
