package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * One of {@link Mall}'s two department stores, the anchors at each end of the bar: three floors
 * under a flat roof, open to the concourse on the ground floor and to the walkways upstairs, its
 * own doors to the street. Inside, the escalators up through the middle, a floor of cosmetics,
 * jewellery, bags and shoes; one of clothes on racks in rows with the fitting rooms along a wall;
 * one of the home (furniture set out as rooms, televisions, kitchenware, toys) and the café. The
 * stock rooms run the length of the outer wall behind a partition, a fire stair at each end of
 * them up to the roof, the loading dock off the back.
 *
 * <p>Drawn in its own frame: x from the outer wall (0) to the wall onto the mall (L - 1), z the
 * length of it (0 .. D - 1) with the concourse meeting it at z = CZ0 .. CZ1 in the middle; floors
 * at 0, 6 and 12, the roof at 18. The east store is the west one turned half round.
 */
final class MallAnchor {
    static final int L = 57, D = 99;
    /** Where the concourse meets the mall-side wall, and where its walkways do upstairs. */
    static final int CZ0 = 41, CZ1 = 57, WALK = 4;
    private static final int F2 = Mall.UP, F3 = 2 * Mall.UP, TOP = 3 * Mall.UP;
    /** The stock rooms' partition. */
    private static final int PART = 9;
    /** The escalators: GF to 2F in lanes E1, E1 + 1 rising east from the plate at EX0; 2F to 3F in E2, E2 + 1 rising west. */
    private static final int EX0 = 24, E1 = 44, E2 = 52;

    private final Placer q;
    private final SplittableRandom rng;
    private final Bldg b;
    private final boolean power, brick;
    private final String name;

    MallAnchor(Placer q, SplittableRandom rng, boolean brick, boolean power) {
        this.q = q;
        this.rng = rng;
        this.b = new Bldg(q, rng);
        b.damage = 0.5;
        this.brick = brick;
        this.power = power;
        this.name = brick ? "harlans" : "bellmont";
    }

    private void set(int x, int y, int z, String block) {
        q.set(x, y, z, block);
    }

    private String get(int x, int y, int z) {
        return q.get(x, y, z);
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String lamp(double share) {
        return power && chance() < share ? "ceiling_light" : "ceiling_light_off";
    }

    void build() {
        shell();
        mallSide();
        street();
        stock();
        escalators();
        fireStair(true);
        fireStair(false);
        groundFloor();
        clothesFloor();
        homeFloor();
        roof();
    }

    // ------------------------------------------------------------------ the shell

    private String wall(int y) {
        if (brick) {
            return b.wall(y <= 1 || y % Mall.UP == 0 ? "stone_trim" : "brick_red", y);
        }
        return y <= 1 ? (chance() < 0.25 ? "ml_brick_b" : "ml_brick") : y % Mall.UP == 0 ? "ml_precast_band"
                : chance() < 0.3 ? "ml_precast_b" : "ml_precast";
    }

    private void shell() {
        for (int y = 0; y <= TOP + 2; y++) {
            for (int x = 0; x < L; x++) {
                set(x, y, 0, y == TOP + 2 ? "stone_trim" : wall(y));
                set(x, y, D - 1, y == TOP + 2 ? "stone_trim" : wall(y));
            }
            for (int z = 1; z < D - 1; z++) {
                set(0, y, z, y == TOP + 2 ? "stone_trim" : wall(y));
                set(L - 1, y, z, y == TOP + 2 ? "stone_trim" : wall(y));
            }
        }
        for (int x = 1; x < L - 1; x++) {
            for (int z = 1; z < D - 1; z++) {
                set(x, 0, z, x < PART ? b.wall("floor_concrete", 3) : "lobby_floor_white");
                set(x, F2, z, x < PART ? b.wall("floor_concrete", 3) : "home_floor_carpet_beige");
                set(x, F3, z, x < PART ? b.wall("floor_concrete", 3) : "home_floor_carpet_blue");
                set(x, TOP, z, b.wall("roof", 2));
                for (int y = 1; y < TOP; y++) {
                    if (y % Mall.UP != 0) {
                        set(x, y, z, "air");
                    }
                }
            }
        }
        // the columns on their grid, and the lamps between them
        for (int x = 18; x < L - 2; x += 14) {
            for (int z = 10; z < D - 2; z += 12) {
                if (z > CZ0 - 4 && z < CZ1 + 4 && x < 40) {
                    continue;
                }
                for (int y = 1; y < TOP; y++) {
                    if (y % Mall.UP != 0) {
                        set(x, y, z, "stone_trim");
                    }
                }
            }
        }
        for (int f = 0; f < 3; f++) {
            int y = f * Mall.UP + Mall.UP - 1;
            for (int x = PART + 3; x < L - 2; x += 4) {
                for (int z = 3; z < D - 2; z += 4) {
                    if (get(x, y, z) == null) {
                        set(x, y, z, lamp(0.12));
                    }
                }
            }
        }
    }

    /**
     * The wall onto the mall: the ground floor open across the concourse's width behind the
     * store's grille (half down), the two walkways' ends open upstairs with glass between them
     * over the well.
     */
    private void mallSide() {
        int x = L - 1;
        for (int z = CZ0; z <= CZ1; z++) {
            for (int y = 1; y < Mall.UP; y++) {
                set(x, y, z, y >= Mall.UP - 2 ? "stone_trim" : y == 3 && chance() < 0.5 ? "st_grille_half[facing=east]" : "air");
            }
            boolean walk = z < CZ0 + WALK || z > CZ1 - WALK;
            for (int y = F2 + 1; y < F3; y++) {
                set(x, y, z, y >= F3 - 2 ? "stone_trim" : walk ? "air" : chance() < 0.2 ? "tx_glass_broken[axis=z]" : "tx_glass[axis=z]");
            }
        }
        for (int z : new int[] {CZ0 - 1, CZ1 + 1}) {
            for (int y = 1; y < F3; y++) {
                if (y != F2) {
                    set(x, y, z, "stone_trim");
                }
            }
        }
    }

    /**
     * The street doors: a run of glass doors in each end wall and one in the outer wall, the
     * display windows either side of them, mannequins still in a few; the store's name over the
     * doors.
     */
    private void street() {
        for (int end : new int[] {0, D - 1}) {
            for (int x = 28; x <= 35; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, end, y <= 2 ? "air" : "tx_glass[axis=x]");
                }
            }
            for (int x = 28; x <= 34; x += 2) {
                Doors.pair(q, rng, x, 1, end, Doors.SHOP, end == 0 ? "south" : "north");
            }
            // display windows along the street, boarded where it went bad
            for (int x = 12; x < L - 3; x++) {
                if (x >= 26 && x <= 37 || x % 7 == 4) {
                    continue;
                }
                for (int y = 2; y <= 4; y++) {
                    double r = chance();
                    set(x, y, end, r < 0.15 ? "boarded_window[axis=x]" : r < 0.3 ? "window_broken[axis=x]" : "window[axis=x]");
                }
            }
        }
        for (int z = 46; z <= 51; z++) {
            for (int y = 1; y <= 4; y++) {
                set(0, y, z, y <= 2 ? "air" : "tx_glass[axis=z]");
            }
        }
        // the door in the outer wall goes through the stock band: a passage across it
        for (int z = 46; z <= 51; z++) {
            for (int x = 1; x <= PART; x++) {
                set(x, 0, z, "lobby_floor_white");
                for (int y = 1; y < F2; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int z = 46; z <= 50; z += 2) {
            doorsZ(0, 1, z, Doors.SHOP, "east");
        }
        // the loading dock: roller doors in the outer wall at the back, the dock plates inside
        for (int z = D - 24; z <= D - 8; z += 6) {
            for (int dz = 0; dz < 3; dz++) {
                for (int y = 1; y <= 4; y++) {
                    set(0, y, z + dz, chance() < 0.25 && y <= 2 ? "air" : "pt_shutter_steel[facing=west]");
                }
            }
        }
    }

    /** A pair of doors at z and z + 1 in a wall along z, facing as the city's doors do (into the room they open from). */
    private void doorsZ(int x, int y, int z, String type, String facing) {
        Doors.abandoned(q, rng, x, y, z, type, facing, "left");
        Doors.abandoned(q, rng, x, y, z + 1, type, facing, "right");
    }

    // ------------------------------------------------------------------ the stock rooms

    private void stock() {
        for (int f = 0; f < 3; f++) {
            int y0 = f * Mall.UP;
            for (int z = 1; z < D - 1; z++) {
                for (int y = y0 + 1; y < y0 + Mall.UP; y++) {
                    set(PART, y, z, f == 0 && z >= 46 && z <= 51 ? "air" : "drywall");
                }
            }
            for (int z : new int[] {20, 76}) {
                doorsZ(PART, y0 + 1, z, Doors.STEEL, "west");
            }
            // racking down the band, cartons on the floor, a cage trolley
            for (int z = 30; z < D - 30; z++) {
                if (z >= 44 && z <= 53) {
                    continue;
                }
                set(PART - 1, y0 + 1, z, "store_shelf_empty[facing=west]");
                if (chance() < 0.5) {
                    set(PART - 1, y0 + 2, z, chance() < 0.6 ? "cardboard_box[facing=west]" : "crate");
                }
                if (chance() < 0.08) {
                    set(5, y0 + 1, z, chance() < 0.6 ? "cardboard_box[facing=north]" : "store_cart[facing=north]");
                }
            }
            for (int z = 6; z < D - 6; z += 5) {
                set(5, y0 + Mall.UP - 1, z, lamp(0.08));
            }
        }
    }

    // ------------------------------------------------------------------ the escalators

    private void escalators() {
        run(EX0, 0, E1, 1);
        run(EX0 + Mall.UP + 1, F2, E2, -1);
        // the slots cut in the floors over them, railed round
        for (int x = EX0 + 1; x <= EX0 + Mall.UP; x++) {
            for (int dz = 0; dz < 2; dz++) {
                set(x, F2, E1 + dz, "air");
                set(x, F3, E2 + dz, "air");
            }
            set(x, F2 + 1, E1 - 1, "tx_glass_rail[axis=x]");
            set(x, F2 + 1, E1 + 2, "tx_glass_rail[axis=x]");
            set(x, F3 + 1, E2 - 1, "tx_glass_rail[axis=x]");
            set(x, F3 + 1, E2 + 2, "tx_glass_rail[axis=x]");
        }
        for (int dz = 0; dz < 2; dz++) {
            set(EX0, F2 + 1, E1 + dz, "tx_glass_rail[axis=z]");
            set(EX0 + Mall.UP + 1, F3 + 1, E2 + dz, "tx_glass_rail[axis=z]");
        }
    }

    /** A pair of escalators up one floor from the plate at x0 on the floor at y, rising toward s. */
    private void run(int x0, int y, int z0, int s) {
        String up = s > 0 ? "east" : "west", down = s > 0 ? "west" : "east";
        for (int k = 1; k <= Mall.UP; k++) {
            int x = x0 + s * k;
            for (int z = z0; z <= z0 + 1; z++) {
                boolean upLane = z == z0;
                for (int yy = y + 1; yy < y + k; yy++) {
                    set(x, yy, z, "stone_trim");
                }
                set(x, y + k, z, "zomboidcraft:escalator[facing=" + up + ",run=" + (upLane ? "up" : "down") + "]");
                set(x, y + k + 1, z, "tx_esc_side_" + (upLane == s > 0 ? "r" : "l") + "[facing=" + up + "]");
                for (int yy = y + k + 2; yy <= y + k + Builders.HEADROOM; yy++) {
                    set(x, yy, z, "air");
                }
            }
        }
        for (int z = z0; z <= z0 + 1; z++) {
            String hand = (z == z0) == s > 0 ? "r" : "l";
            set(x0, y, z, "tx_esc_plate[facing=" + up + "]");
            set(x0, y + 1, z, "tx_esc_newel_" + hand + "[facing=" + down + "]");
            set(x0 + s * (Mall.UP + 1), y + Mall.UP, z, "tx_esc_plate[facing=" + down + "]");
            set(x0 + s * (Mall.UP + 1), y + Mall.UP + 1, z, "tx_esc_newel_" + hand + "[facing=" + up + "]");
        }
    }

    // ------------------------------------------------------------------ the fire stairs

    /**
     * A fire stair in the stock band's lanes x 2..3: a flight up each floor along the band, a
     * landing between, the last flight out through the roof into its hut. The north one climbs
     * south from z = 3, the south one north from D - 4.
     */
    private void fireStair(boolean north) {
        int s = north ? 1 : -1, z0 = north ? 3 : D - 4;
        String up = north ? "south" : "north";
        for (int f = 0; f < 2; f++) {
            int y0 = f * Mall.UP, zs = z0 + s * f * (Mall.UP + 2);
            for (int k = 1; k <= Mall.UP; k++) {
                int z = zs + s * k;
                for (int x = 2; x <= 3; x++) {
                    for (int y = y0 + 1; y < y0 + k; y++) {
                        set(x, y, z, "concrete_panel");
                    }
                    set(x, y0 + k, z, "stairs_concrete[facing=" + up + "]");
                    for (int r = 1; r <= Builders.HEADROOM; r++) {
                        set(x, y0 + k + r, z, "air");
                    }
                }
                if (y0 + k + Builders.HEADROOM >= y0 + Mall.UP) {
                    set(4, y0 + Mall.UP + 1, z, "ext_rail_edge_dark[facing=west]");
                }
            }
            for (int x = 2; x <= 3; x++) {
                set(x, y0 + Mall.UP + 1, zs, "air");
                set(x, y0 + Mall.UP + 1, zs + s, "ext_rail_edge_dark[facing=" + (north ? "south" : "north") + "]");
            }
        }
        // the last flight, climbing on along the band through the roof into the hut
        Placer t = north ? q.turned(3, 0, 0, 1) : q.turned(2, 0, D - 1, 3);
        RoofHut.flight(t, rng, 2 * (Mall.UP + 2) + (north ? z0 : D - 1 - z0), 0, F3, TOP, brick ? "brick_red" : "concrete_panel",
                "stairs_concrete", north ? "north" : "south", null);
    }

    // ------------------------------------------------------------------ the floors

    /** Cosmetics and fragrance counters in islands, jewellery cases, bags on tables, shoes on the walls. */
    private void groundFloor() {
        for (int x = 14; x < L - 6; x += 9) {
            for (int z = 8; z < D - 8; z += 11) {
                if (clearOf(x - 1, z - 1, x + 4, z + 3, 0)) {
                    island(x, z, chance() < 0.5 ? "store_case_jewelry" : "store_counter", 0);
                }
            }
        }
        for (int x = PART + 2; x < L - 3; x++) {
            if (x >= 26 && x <= 37) {
                continue;
            }
            for (int z : new int[] {1, D - 2}) {
                if (get(x, 1, z) == null && x % 5 != 0) {
                    set(x, 1, z, "store_shelf_thrift[facing=" + (z == 1 ? "south" : "north") + "]");
                }
            }
        }
        b.mess(PART + 1, 1, L - 2, D - 2, 1, 0.03);
    }

    /** Clothes on racks in rows across the floor, the fitting rooms down the partition. */
    private void clothesFloor() {
        int y = F2;
        for (int z = 4; z < D - 4; z += 3) {
            for (int x = PART + 4; x < L - 3; x++) {
                // an aisle kept clear along the fitting rooms' curtains
                boolean fitting = z >= 58 && z <= 93 && x <= PART + 6;
                if (x % 8 == 0 || fitting || !clearOf(x, z, x, z, y)) {
                    continue;
                }
                if (chance() < 0.82) {
                    set(x, y + 1, z, (chance() < 0.1 ? "store_shelf_empty" : "store_clothes_rack") + "[facing=north]");
                }
            }
        }
        // fitting rooms: cubicles along the partition, curtains drawn or torn
        for (int z = 60; z < 92; z += 3) {
            for (int dz = 0; dz < 3; dz++) {
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    set(PART + 3, yy, z + dz, dz == 1 && yy <= y + 2 ? (chance() < 0.5 ? "air" : "home_curtains_rose") : "drywall");
                }
            }
            for (int yy = y + 1; yy <= y + 3; yy++) {
                set(PART + 1, yy, z, "drywall");
                set(PART + 2, yy, z, "drywall");
            }
            set(PART + 1, y + 2, z + 1, "home_mirror[facing=east]");
        }
        b.mess(PART + 1, y + 1, L - 2, D - 2, y + 1, 0.03);
    }

    /** The home floor: furniture set out as rooms, the televisions, kitchenware and toys, the café. */
    private void homeFloor() {
        int y = F3;
        String[] sofas = {"blue", "brown", "floral"};
        for (int x = PART + 4; x < L - 8; x += 8) {
            for (int z = 4; z < CZ0 - 4; z += 7) {
                if (!clearOf(x, z, x + 4, z + 3, y)) {
                    continue;
                }
                String c = sofas[rng.nextInt(3)];
                set(x, y + 1, z, "home_sofa_" + c + "_l[facing=south]");
                set(x + 1, y + 1, z, "home_sofa_" + c + "_m[facing=south]");
                set(x + 2, y + 1, z, "home_sofa_" + c + "_r[facing=south]");
                set(x + 1, y + 1, z + 2, "home_coffee_table");
                set(x + 3, y + 1, z, "home_lamp_table");
                if (chance() < 0.5) {
                    set(x + 1, y + 1, z + 3, "home_tv[facing=north]");
                }
            }
        }
        for (int x = PART + 4; x < L - 6; x += 6) {
            for (int z = CZ1 + 6; z < D - 6; z += 5) {
                if (!clearOf(x, z, x + 2, z + 1, y)) {
                    continue;
                }
                double r = chance();
                if (r < 0.35) {
                    set(x, y + 1, z, "home_bed_" + new String[] {"blue", "quilt", "rose"}[rng.nextInt(3)] + "_head[facing=south]");
                    set(x, y + 1, z + 1, "home_bed_blue_foot[facing=south]");
                    set(x + 1, y + 1, z, "home_nightstand");
                } else if (r < 0.65) {
                    set(x, y + 1, z, "store_table");
                    set(x + 1, y + 1, z, "store_table");
                    set(x, y + 2, z, chance() < 0.3 ? "home_tv_smashed[facing=north]" : "home_tv[facing=north]");
                    set(x + 1, y + 2, z, "pk_tv_crt[facing=north]");
                } else {
                    for (int dx = 0; dx < 3; dx++) {
                        set(x + dx, y + 1, z, "store_shelf_grocery[facing=north]");
                    }
                }
            }
        }
        b.mess(PART + 1, y + 1, L - 2, D - 2, y + 1, 0.03);
    }

    /** A counter island 4 by 3 on the floor at y: cases round a till in the middle. */
    private void island(int x0, int z0, String cases, int y) {
        for (int x = x0; x < x0 + 4; x++) {
            for (int z = z0; z < z0 + 3; z++) {
                boolean edge = x == x0 || x == x0 + 3 || z == z0 || z == z0 + 2;
                if (edge && !(x == x0 + 3 && z == z0 + 1)) {
                    String f = z == z0 ? "north" : z == z0 + 2 ? "south" : x == x0 ? "west" : "east";
                    set(x, y + 1, z, cases + "[facing=" + f + "]");
                }
            }
        }
        set(x0 + 1, y + 2, z0 + 1, "store_register[facing=north]");
    }

    /** Nothing built between the corners at the floor's walking level (y + 1). */
    private boolean clearOf(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (get(x, y + 1, z) != null || get(x, y, z) == null || x <= PART || x >= L - 1) {
                    return false;
                }
                if (x >= EX0 - 1 && x <= EX0 + Mall.UP + 2 && (z >= E1 - 2 && z <= E1 + 3 || z >= E2 - 2 && z <= E2 + 3)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The roof: the plant; the store's name high on both ends and over the outer door, lit on the generator. */
    private void roof() {
        boolean lit = power && chance() < 0.7;
        MallSigns.name(q, rng, name, 12, 3, 26, F3 + 1, -1, "north", lit);
        MallSigns.name(q, rng, name, 12, 3, 26, F3 + 1, D, "south", lit);
        MallSigns.name(q, rng, name, 12, 3, -1, F3 + 1, 43, "west", lit);
        for (int x = 14; x < L - 6; x += 16) {
            for (int z = 20; z < D - 20; z += 30) {
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        set(x + dx, TOP + 1, z + dz, "hvac");
                    }
                }
            }
        }
    }

    String name() {
        return name;
    }
}
