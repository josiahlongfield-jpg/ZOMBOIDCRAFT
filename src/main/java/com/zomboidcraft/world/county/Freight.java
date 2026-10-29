package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The freight wagons left standing on the sidings, from the fw_ kit (tools/city/freight.py): a
 * boxcar 13 long along +x from x, three wide about row z, its trucks on the track at y (two
 * axles each, under the second and third blocks from each end), the floor at y + 1, the body two
 * blocks high and the roof row over it, a coupler out of each end. The track itself, the tb_
 * pieces under the rest of the car, is the caller's. Drawn in the placer's own coordinates.
 */
final class Freight {
    static final int LEN = 13;
    static final String[] PAINTS = {"red", "brown", "green"};

    private Freight() {
    }

    /** A paint for a car, red and brown the commonest. */
    static String paint(SplittableRandom rng) {
        double r = rng.nextDouble();
        return r < 0.45 ? "red" : r < 0.8 ? "brown" : "green";
    }

    /** The running gear and the floor alone: under a boxcar, or a flat with whatever is on it. */
    static void chassis(Placer p, int x, int y, int z) {
        for (int i = 0; i < LEN; i++) {
            boolean truck = i == 1 || i == 2 || i == LEN - 3 || i == LEN - 2;
            for (int k = -1; k <= 1; k++) {
                String out = k < 0 ? "north" : "south";
                if (truck) {
                    p.set(x + i, y, z + k, k == 0 ? "fw_truck_mid[facing=east]" : "fw_truck[facing=" + out + "]");
                }
                p.set(x + i, y + 1, z + k, k == 0 ? "fw_floor_mid[axis=x]" : "fw_floor[facing=" + out + "]");
            }
        }
        p.set(x - 1, y + 1, z, "fw_coupler[facing=west]");
        p.set(x + LEN, y + 1, z, "fw_coupler[facing=east]");
    }

    /**
     * A boxcar: the chassis, the sides with the reporting marks left of the door, the doors (slid
     * open over the side beside the opening when open), the ends with their ladders up the corners
     * and the hand brake on the east end, the roof with its running board.
     */
    static void boxcar(Placer p, SplittableRandom rng, int x, int y, int z, String c, boolean open) {
        chassis(p, x, y, z);
        int d0 = LEN / 2 - 1;  // the door, two wide
        for (int i = 0; i < LEN; i++) {
            boolean end = i == 0 || i == LEN - 1;
            String endFace = i == 0 ? "west" : "east";
            for (int k = -1; k <= 1; k++) {
                String out = k < 0 ? "north" : "south";
                // left as seen from outside the end: north at the west end, south at the east
                String lr = (i == 0) == (k < 0) ? "l" : "r";
                for (int r = 0; r <= 2; r++) {
                    int yy = y + 2 + r;
                    String b;
                    if (r == 2) {
                        b = end ? (k == 0 ? "fw_end_top_" + c + "[facing=" + endFace + "]" : "fw_corner_top_" + c + "_" + lr + "[facing=" + endFace + "]")
                                : k == 0 ? "fw_ridge_" + c + "[axis=x]" : "fw_roof_" + c + "[facing=" + out + "]";
                    } else if (end) {
                        b = k != 0 ? "fw_corner_" + c + "_" + lr + "[facing=" + endFace + "]"
                                : (i == LEN - 1 && r == 1 ? "fw_brake_" : "fw_end_") + c + "[facing=" + endFace + "]";
                    } else if (k == 0) {
                        b = "air";
                    } else {
                        boolean doorway = i == d0 || i == d0 + 1, slid = i == d0 + 2 || i == d0 + 3;
                        // the door's quarters: column 0 its left as seen from outside, the lower x on the south
                        int col = (i - d0) % 2 == 0 ? (k > 0 ? 0 : 1) : (k > 0 ? 1 : 0);
                        String quarter = "fw_door_" + c + "_" + (r * 2 + col) + "[facing=" + out + "]";
                        if (doorway) {
                            b = open ? "air" : quarter;
                        } else if (slid && open) {
                            b = quarter;
                        } else if (r == 1 && i >= 1 && i <= 3) {
                            // the marks read left to right from outside: west to east on the south side
                            int m = k > 0 ? i - 1 : 3 - i;
                            b = "fw_mark_" + c + "_" + m + "[facing=" + out + "]";
                        } else {
                            b = "fw_side_" + c + "[facing=" + out + "]";
                        }
                    }
                    p.set(x + i, yy, z + k, b);
                }
            }
        }
    }
}
