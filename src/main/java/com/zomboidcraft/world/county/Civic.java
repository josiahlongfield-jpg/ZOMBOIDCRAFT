package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.ShopLights;
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
        int storey = 5;
        String wall = pick("brick_buff", "brick_red", "concrete_panel");
        int z0 = 8;
        b.shell(0, z0, w - 1, d - 1, 1, storey, wall, "floor_lino", "roof");
        b.windowRow(2, w - 3, 3, z0, 2, "north", 2, "sill");
        b.windowRow(z0 + 2, d - 3, 4, 0, 2, "west", 1, null);
        b.windowRow(z0 + 2, d - 3, 4, w - 1, 2, "east", 1, null);
        int cx = w / 2;
        b.doors(cx - 1, 1, z0, "fire_door", "north");
        Boards.fascia(p, rng, "sheriff", 1, storey - 1, z0 - 1, w - 2);
        p.set(cx - 2, 3, z0 - 1, ShopLights.lamp(rng, 0.2));
        p.set(cx + 1, 3, z0 - 1, ShopLights.lamp(rng, 0.2));
        b.flat(0, z0, w - 1, d - 1, storey, wall, 1, "stone_trim");
        // front desk, then a corridor of cells at the back
        p.set(cx, 1, z0 + 3, "reception_desk[facing=north]");
        p.set(cx + 1, 1, z0 + 3, "reception_desk[facing=north]");
        p.set(1, 1, z0 + 2, "store_gun_rack[facing=east,loot=guns]");
        p.set(1, 1, z0 + 3, "store_case_guns[facing=east,loot=guns]");
        p.set(w - 2, 1, z0 + 2, "filing_cabinet[facing=west]");
        int cells = d - 5;
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, cells, (x - 1) % 4 == 2 && y <= 2 ? (rng.nextBoolean() ? "air" : "minecraft:iron_door[facing=south,half=" + (y == 1 ? "lower" : "upper") + ",hinge=left,open=false]")
                        : "minecraft:iron_bars");
            }
            if ((x - 1) % 4 == 0) {
                for (int z = cells + 1; z < d - 1; z++) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(x, y, z, "concrete_panel");
                    }
                }
            } else if ((x - 1) % 4 == 1) {
                p.set(x, 1, d - 2, "minecraft:gray_bed[facing=south,part=head]");
                p.set(x, 1, d - 3, "minecraft:gray_bed[facing=south,part=foot]");
            }
        }
        b.lights(0, z0, w - 1, d - 1, storey - 1, 0.05);
        b.mess(1, z0 + 1, w - 2, d - 2, 1, 0.08);
        // the lot: cruisers nose in, one burnt, sandbags and barriers where they held out
        for (int i = 0; i < 3; i++) {
            p.vehicle(4 + i * 5, 1, 3.5, 180, "police_sedan,police_suv", rng.nextLong());
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < z0; z++) {
                p.set(x, 0, z, rng.nextDouble() < 0.3 ? "asphalt_crack" : "asphalt");
            }
        }
        lastStand(cx - 3, cx + 2, z0 - 1, 1);
        flagpole(w - 2, z0 - 2, 9);
    }

    // ------------------------------------------------------------------ schools

    /**
     * A school: a long brick classroom wing (one storey for the elementary, two for the high
     * school) with a gym block, the entrance under a canopy, the bus loop out front.
     */
    void school(int w, int d, boolean high) {
        int floors = high ? 2 : 1, storey = 5;
        String wall = pick("brick_red", "brick_buff", "brick_orange", "brick_red");
        int z0 = 14, wingD = 14;
        int wx1 = w - 1 - (high ? 22 : 18);
        b.shell(0, z0, wx1, z0 + wingD - 1, floors, storey, wall, "floor_lino", "floor_lino");
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 2;
            for (int x = 2; x < wx1 - 1; x++) {
                if ((x - 2) % 6 < 4) {
                    b.window(x, y, z0, "north", 2, null);
                    b.window(x, y, z0 + wingD - 1, "south", 2, null);
                }
            }
            for (int x = 1; x < wx1; x++) {
                p.set(x, y - 1, z0 - 1, k == 0 ? "air" : "minecraft:smooth_stone_slab[type=top]");
            }
        }
        // classrooms off a corridor: desks in rows, the teacher's desk, the chalkboard
        int corridor = z0 + 5;
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 1;
            for (int x = 1; x < wx1; x++) {
                for (int h = 0; h < 3; h++) {
                    p.set(x, y + h, corridor, (x - 1) % 9 == 4 && h < 2 ? "air" : "drywall");
                }
            }
            for (int x = 9; x < wx1; x += 9) {
                for (int z = corridor + 1; z < z0 + wingD - 1; z++) {
                    for (int h = 0; h < 3; h++) {
                        p.set(x, y + h, z, "drywall");
                    }
                }
            }
            for (int x = 2; x < wx1 - 1; x++) {
                for (int z = corridor + 2; z < z0 + wingD - 2; z += 2) {
                    if ((x - 1) % 9 != 8 && (x % 2 == 0) && rng.nextDouble() < 0.85) {
                        p.set(x, y, z, rng.nextDouble() < 0.15 ? "office_chair_fallen[facing=east]" : "store_table");
                    }
                }
            }
            b.lights(0, z0, wx1, z0 + wingD - 1, y + storey - 2, 0.02);
            b.mess(1, z0 + 1, wx1 - 1, z0 + wingD - 2, y, 0.05);
        }
        if (high) {
            b.stair(1, z0 + 4, 1, storey, "north", "minecraft:spruce");
        }
        b.flat(0, z0, wx1, z0 + wingD - 1, floors * storey, wall, 1, "stone_trim");
        // the entrance: double doors under a flat canopy, the name in letters over them
        int ex = wx1 / 2;
        b.doors(ex - 1, 1, z0, "fire_door", "north");
        for (int x = ex - 3; x <= ex + 2; x++) {
            for (int z = z0 - 4; z < z0; z++) {
                p.set(x, 4, z, "minecraft:smooth_stone_slab[type=top]");
            }
        }
        p.set(ex - 3, 1, z0 - 4, "steel_mullion");
        p.set(ex - 3, 2, z0 - 4, "steel_mullion");
        p.set(ex - 3, 3, z0 - 4, "steel_mullion");
        p.set(ex + 2, 1, z0 - 4, "steel_mullion");
        p.set(ex + 2, 2, z0 - 4, "steel_mullion");
        p.set(ex + 2, 3, z0 - 4, "steel_mullion");
        Boards.fascia(p, rng, high ? "school" : "elementary", 1, floors * storey - 1, z0 - 1, wx1 - 1);
        // the gym: a tall block with a high strip of windows and a barrel roof of tin
        int gx0 = wx1 + 1, gx1 = w - 1, gz0 = z0 - 2, gz1 = Math.min(d - 1, z0 + wingD + 6);
        int gh = 9;
        b.walls(gx0, gz0, gx1, gz1, 1, gh, wall);
        b.floor(gx0 + 1, gz0 + 1, gx1 - 1, gz1 - 1, 0, "floor_boards");
        b.clear(gx0 + 1, 1, gz0 + 1, gx1 - 1, gh, gz1 - 1);
        for (int x = gx0 + 2; x < gx1 - 1; x++) {
            p.set(x, gh - 2, gz0, b.glass("x"));
            p.set(x, gh - 2, gz1, b.glass("x"));
        }
        b.gable(gx0, gx1, gz0, gz1, gh + 1, "minecraft:waxed_weathered_cut_copper", false, wall, 0);
        for (int y = 1; y <= 2; y++) {
            p.set(gx0, y, z0 + 3, "air");
        }
        // bleachers down one side, a hoop at each end, cots where it became a shelter
        for (int i = 0; i < 3; i++) {
            for (int z = gz0 + 2; z < gz1 - 1; z++) {
                p.set(gx1 - 3 + i, 1 + i, z, "minecraft:spruce_stairs[facing=east]");
            }
        }
        int hx = (gx0 + gx1) / 2;
        p.set(hx, 4, gz0 + 1, "minecraft:iron_bars");
        p.set(hx, 4, gz1 - 1, "minecraft:iron_bars");
        if (rng.nextDouble() < 0.6) {
            for (int i = 0; i < 10; i++) {
                int x = gx0 + 2 + rng.nextInt(Math.max(1, gx1 - gx0 - 6)), z = gz0 + 2 + rng.nextInt(Math.max(1, gz1 - gz0 - 3));
                p.set(x, 1, z, i % 3 == 0 ? "crate" : "minecraft:green_bed[facing=north,part=foot]");
            }
            lastStand(gx0 + 1, gx0 + 1, z0 + 3, 1);
        }
        // the bus loop and the buses still in it
        for (int x = 0; x < w; x++) {
            for (int z = 2; z <= 9; z++) {
                p.set(x, 0, z, rng.nextDouble() < 0.25 ? "asphalt_crack" : "asphalt");
            }
        }
        int buses = 1 + rng.nextInt(3);
        for (int i = 0; i < buses; i++) {
            p.vehicle(8 + i * 14 + rng.nextDouble() * 2, 1, 5.5, 270 + (float) (rng.nextDouble() * 8 - 4), "school_bus", rng.nextLong());
        }
        flagpole(ex + 5, z0 - 3, 11);
    }

    // ------------------------------------------------------------------ the picture house

    /** A small-town theatre: the marquee over the sidewalk, a neon blade, seats facing the screen. */
    void theatre(int w, int d) {
        int h = 10;
        String wall = pick("brick_buff", "brick_red", "plaster_cream", "plaster_rose");
        b.walls(0, 0, w - 1, d - 1, 1, h, wall);
        b.floor(0, 0, w - 1, d - 1, 0, "concrete_dark");
        b.floor(1, 1, w - 2, d - 2, 0, "floor_checker");
        b.clear(1, 1, 1, w - 2, h - 1, d - 2);
        b.floor(1, 1, w - 2, d - 2, h, "roof");
        int cx = w / 2;
        // the lobby front: glass doors, the ticket booth, poster cases
        for (int x = 1; x < w - 1; x++) {
            p.set(x, 1, 0, x == cx || x == cx - 1 || x == cx + 1 ? "air" : "stone_trim");
            p.set(x, 2, 0, x == cx || x == cx - 1 || x == cx + 1 ? "air" : b.glass("x"));
            p.set(x, 3, 0, b.glass("x"));
        }
        b.doors(cx - 1, 1, 0, "shop_door", "north");
        b.door(cx + 1, 1, 0, "shop_door", "north");
        p.set(cx, 1, -2, "store_teller_glass[axis=z]");
        p.set(cx, 2, -2, "store_teller_glass[axis=z]");
        p.set(cx, 3, -2, "minecraft:smooth_stone_slab[type=bottom]");
        // the marquee: a deep box over the sidewalk ringed with bulbs, the name on its face
        for (int x = 0; x < w; x++) {
            for (int z = -3; z <= -1; z++) {
                p.set(x, 4, z, "concrete_dark");
                p.set(x, 5, z, x == 0 || x == w - 1 || z == -3 ? "minecraft:smooth_quartz" : "concrete_dark");
            }
            if (x % 2 == 0) {
                p.set(x, 3, -3, rng.nextDouble() < 0.5 ? ShopLights.lamp(rng, 0.2) : "minecraft:iron_chain[axis=y]");
            }
        }
        Boards.fascia(p, rng, "rialto", 1, 5, -4, w - 2);
        Boards.blade(p, rng, "rialto", 6, cx, h + 1, -1, rng.nextDouble() < 0.5);
        // the screen at the back, rows of seats raked down toward it
        for (int x = 2; x < w - 2; x++) {
            for (int y = 2; y < h - 1; y++) {
                p.set(x, y, d - 2, "minecraft:white_concrete");
            }
        }
        for (int z = 6; z < d - 5; z += 2) {
            for (int x = 2; x < w - 2; x++) {
                if (x == cx) {
                    continue;
                }
                p.set(x, 1, z, rng.nextDouble() < 0.1 ? "air" : "minecraft:red_terracotta");
                p.set(x, 2, z, rng.nextDouble() < 0.15 ? "air" : "minecraft:dark_oak_stairs[facing=north]");
            }
        }
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, 4, x == cx || x == cx - 1 ? "air" : "drywall");
            }
        }
        p.set(2, 1, 2, "store_counter[facing=east]");
        p.set(2, 1, 3, "store_counter[facing=east]");
        p.set(1, 1, 2, "store_shelf_grocery[facing=east]");
        b.lights(0, 0, w - 1, d - 1, h - 1, 0.0);
        b.mess(1, 1, w - 2, d - 2, 1, 0.05);
        b.flat(0, 0, w - 1, d - 1, h, wall, 1, null);
        for (int y = h + 1; y <= h + 3; y++) {
            for (int x = 0; x < w; x++) {
                p.set(x, y, 0, b.wall(wall, y));
            }
        }
    }
}
