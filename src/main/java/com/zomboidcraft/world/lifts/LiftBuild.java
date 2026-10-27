package com.zomboidcraft.world.lifts;

/**
 * Lays a working lift into a builder's blocks, in the builder's own frame with the doors facing
 * north (local -z), as the towers have them; the builder's placer turns it with the building.
 * The builder walls the shaft; this clears it, sets the landing doors, the call buttons and the
 * machine on top (see {@link LiftShaft} for how the game reads it back).
 */
public final class LiftBuild {
    /** Where the blocks go: a builder's placer (set(x, y, z, block)). */
    public interface Sink {
        void set(int x, int y, int z, String block);
    }

    /**
     * A shaft x0 .. x0 + w - 1 wide and z0 .. z0 + d - 1 deep (z0 the row behind the doors; the
     * doors in the wall at z0 - 1), stopping at the floors whose slabs are at floorYs (the car
     * stands at slab + 1), bottom up. lobby: which of them is the street floor (numbered 1), or -1
     * to number from base + 1 up (a stair core that starts higher up). The machine goes at top,
     * which must be at least 4 over the highest floor's slab.
     */
    public static void shaft(Sink s, int x0, int z0, int w, int d, int[] floorYs, int lobby, int base, int top) {
        int lo = floorYs[0] + 1;
        for (int y = lo; y < top; y++) {
            for (int x = x0; x < x0 + w; x++) {
                for (int z = z0; z < z0 + d; z++) {
                    s.set(x, y, z, "air");
                }
            }
        }
        for (int i = 0; i < floorYs.length; i++) {
            int y = floorYs[i] + 1;
            for (int a = 0; a < w; a++) {
                // standing in front of the doors (north of them, looking south) local +x is on your left
                String side = a < w / 2 ? "right" : "left";
                for (int k = 0; k < 2; k++) {
                    s.set(x0 + a, y + k, z0 - 1, "zomboidcraft:lift_door[facing=north,part=" + (k == 0 ? "lower" : "upper")
                            + ",side=" + side + ",open=false,lobby=" + (i == lobby) + "]");
                }
            }
            s.set(x0 + w, y + 1, z0 - 2, "zomboidcraft:lift_button[facing=north,lit=false]");
        }
        s.set(x0, top, z0, "zomboidcraft:lift_machine[facing=north,base=" + Math.max(0, Math.min(63, base)) + "]");
    }

    private LiftBuild() {
    }
}
