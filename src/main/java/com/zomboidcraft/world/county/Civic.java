package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The public buildings of a county town, each drawn in its lot's frame (front along z = 0
 * facing the street): churches (a white clapboard chapel, a brick church with a corner tower, a
 * stone one with a spire), the courthouse with its portico and clock tower, the town hall, the
 * post office, a Carnegie library, the bank, the fire station, the sheriff's office and jail,
 * schools, and the picture house.
 *
 * Every one is a little different each time (materials, sizes, which way the tower stands) and
 * every one shows what happened here: the church barricaded as a last refuge, the sheriff's
 * sandbagged, the school's buses abandoned in the loop.
 */
final class Civic {
    private static final String[] GLASS = {"minecraft:red_stained_glass_pane", "minecraft:blue_stained_glass_pane",
            "minecraft:yellow_stained_glass_pane", "minecraft:purple_stained_glass_pane", "minecraft:green_stained_glass_pane"};
    private static final String[] ROOFS = {"minecraft:deepslate_tile", "minecraft:cobbled_deepslate", "minecraft:blackstone",
            "minecraft:dark_oak", "minecraft:tuff_brick"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;

    Civic(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.5 + rng.nextDouble() * 0.4;
        b.boarded = rng.nextDouble() < 0.3 ? 0.45 : 0.1;
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ shared bits

    /** Steps up to a door: a flight of `rise` stairs out from the front, w wide, centred on cx. */
    private void steps(int cx, int half, int rise, String family) {
        for (int i = 0; i < rise; i++) {
            for (int x = cx - half - i; x <= cx + half + i; x++) {
                p.set(x, rise - 1 - i, -1 - i, family + "_stairs[facing=south]");
                for (int y = 0; y < rise - 1 - i; y++) {
                    p.set(x, y, -1 - i, Bldg.full(family));
                }
            }
        }
    }

    /** A flagpole h high on its concrete pad: the white pole, the flag off the gilt ball at the top (or torn down). */
    void flagpole(int x, int z, int h) {
        p.set(x, 0, z, "concrete_dark");
        p.set(x, 1, z, "cv_flagpole_base");
        for (int y = 2; y < h; y++) {
            p.set(x, y, z, "cv_flagpole");
        }
        p.set(x, h, z, rng.nextDouble() < 0.75 ? "cv_flagpole_top[facing=north]" : "cv_flagpole");
    }

    /** A last stand: sandbags across the door, a barricade, the casings and blood. */
    private void lastStand(int x0, int x1, int z, int y) {
        for (int x = x0; x <= x1; x++) {
            if (rng.nextDouble() < 0.8) {
                p.set(x, y, z, "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(x0 + rng.nextInt(Math.max(1, x1 - x0 + 1)), y, z - 1 - rng.nextInt(3), "blood_splat");
        }
    }

    // ------------------------------------------------------------------ churches

    enum Church { CHAPEL, BRICK, STONE }

    /** Lot size a church of the kind wants: {w, d}. */
    static int[] churchSize(Church kind) {
        return switch (kind) {
            case CHAPEL -> new int[] {14, 30};
            case BRICK -> new int[] {19, 36};
            case STONE -> new int[] {23, 44};
        };
    }

    /** A church of the kind on a lot w wide and d deep (see {@link com.zomboidcraft.world.county.Church}); sign: the board out front or null. */
    void church(Church kind, int w, int d, String sign) {
        new com.zomboidcraft.world.county.Church(p, rng).build(kind, w, d, sign);
    }

    /**
     * A churchyard over x0..x1, z0..z1: iron railings round it on posts, the gate in the front;
     * rows of headstones in granite and marble (each with its grave behind it), stone crosses and
     * the odd obelisk, some sunk and leaning, some graves dug up from below and left open.
     */
    void graveyard(int x0, int z0, int x1, int z1) {
        int gate = (x0 + x1) / 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean ex = x == x0 || x == x1, ez = z == z0 || z == z1;
                if (!ex && !ez) {
                    continue;
                }
                if (z == z0 && (x == gate || x == gate + 1)) {
                    continue;
                }
                boolean post = ex && ez || (ez ? (x - x0) % 4 == 0 || x == gate - 1 || x == gate + 2 : (z - z0) % 4 == 0);
                if (rng.nextDouble() < 0.08) {
                    continue;  // a length of railing down
                }
                p.set(x, 1, z, post ? "cv_iron_post" : "cv_iron_fence[axis=" + (ez ? "x" : "z") + "]");
            }
        }
        for (int z = z0 + 2; z + 1 < z1; z += 3) {
            for (int x = x0 + 1; x < x1; x += 2) {
                if (x == gate || x == gate + 1 || rng.nextDouble() < 0.12) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < 0.06 && z + 2 < z1) {
                    p.set(x, 1, z, "ch_obelisk[facing=north]");
                    p.set(x, 2, z, "ch_obelisk_top");
                } else if (r < 0.16) {
                    p.set(x, 1, z, "ch_grave_cross[facing=north]");
                } else {
                    p.set(x, 1, z, "ch_headstone_" + rng.nextInt(6) + (r > 0.84 ? "_sunk" : "") + "[facing=north]");
                }
                // the grave behind it, or the hole where it came up
                if (rng.nextDouble() < 0.14) {
                    p.set(x, 0, z + 1, "ch_grave_open");
                } else {
                    p.set(x, 1, z + 1, "ch_grave_mound");
                }
            }
        }
        // the path from the gate, worn through the grass
        for (int z = z0; z < z1; z++) {
            p.set(gate, 0, z, "dirt_withered");
            p.set(gate + 1, 0, z, "dirt_withered");
        }
    }

    // ------------------------------------------------------------------ the courthouse

    /** The county courthouse (see {@link Courthouse}): w wide, d deep, the portico and steps in its first six rows. */
    void courthouse(int w, int d) {
        new Courthouse(p, rng).build(w, d);
    }

    // ------------------------------------------------------------------ the smaller civic buildings

    /** The town hall (see {@link TownHall}): w wide, d deep, its steps in the first four rows. */
    void townHall(int w, int d) {
        new TownHall(p, rng).build(w, d);
    }

    /** The Carnegie library (see {@link Library}): w wide, d deep, the portico and steps in its first six rows. */
    void library(int w, int d) { new Library(p, rng).build(w, d); }

    /** The bank on its corner (see {@link Bank}): w wide, d deep, the portico and steps in its first four rows. */
    void bank(int w, int d) { new Bank(p, rng).build(w, d); }

    /** The fire station (see {@link FireStation}): w wide, d deep, its apron in the first six rows. */
    void fireStation(int w, int d) { new FireStation(p, rng).build(w, d); }

    /** The sheriff's office and county jail: front desk, cells, a gun rack, cruisers out front. */
    void sheriff(int w, int d) {
        new Sheriff(p, rng).build(w, d);
    }

    // ------------------------------------------------------------------ schools

    /** A school, built by {@link School}: classrooms off a corridor, the office, library and gym. */
    void school(int w, int d, boolean high) {
        new School(p, rng).build(w, d, high);
    }

    // ------------------------------------------------------------------ the picture house

    /** The picture house: see {@link Theatre}. */
    void theatre(int w, int d) {
        new Theatre(p, rng).build(w, d);
    }
}
