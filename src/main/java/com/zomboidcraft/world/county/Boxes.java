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
     * The cell a box's skin faces: if another box's skin is there, it goes a plain cube and so does
     * this one (returned); otherwise this one's piece stands.
     */
    private static String hide(Placer p, int x, int y, int z, String piece, String c) {
        String n = p.get(x, y, z);
        if (n == null || !(n.startsWith("ct_") || n.startsWith("container_"))) {
            return piece;
        }
        if (n.startsWith("ct_") && n.indexOf("_end_") < 0 && n.indexOf("_door_") < 0) {
            p.set(x, y, z, "container_" + n.substring(3, n.indexOf('_', 3)));
        }
        return piece.startsWith("ct_") ? "container_" + c : piece;
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
                    int px = alongX ? x + i : x + k, pz = alongX ? z + k : z + i, py = y + j;
                    // a side or roof against another box's skin is never seen: both go plain cubes,
                    // so a stack doesn't draw thousands of hidden ribs
                    if (!low && !high && k != 1) {
                        int out = k == 0 ? -1 : 1;
                        b = hide(p, alongX ? px : px + out, py, alongX ? pz + out : pz, b, c);
                    } else if (!low && !high && j == 2) {
                        b = hide(p, px, py + 1, pz, b, c);
                    } else if (!low && !high && j == 0) {
                        String under = p.get(px, py - 1, pz);
                        if (under != null && under.startsWith("ct_") && under.contains("_roof")) {
                            p.set(px, py - 1, pz, "container_" + under.substring(3, under.indexOf('_', 3)));
                        }
                    }
                    p.set(px, py, pz, b);
                }
            }
        }
    }
}
