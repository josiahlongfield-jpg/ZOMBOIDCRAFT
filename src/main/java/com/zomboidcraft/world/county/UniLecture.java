package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.University.LEC_X0;
import static com.zomboidcraft.world.county.University.LEC_X1;
import static com.zomboidcraft.world.county.University.LEC_Z0;
import static com.zomboidcraft.world.county.University.LEC_Z1;

/**
 * Wells Lecture Hall, down the quad's west side south of the library: two raked auditoriums side
 * by side behind a glass lobby the height of the building, the concrete fins of their blind walls
 * down the sides, WELLS LECTURE HALL along the lobby's fascia. Each hall has its stage at the
 * lobby end under the chalkboards and the lectern, the tiers of seats climbing away to the west to
 * the back aisle and the projection booth; a passage at ground level between them out to the west
 * walk, the restrooms at the lobby's south end.
 *
 * <p>Hall A was a final when the sirens went; the blue books are still on the seats. Hall B was
 * where the Red Cross gave out the shots, and where they laid out the ones who turned after.
 */
final class UniLecture {
    private final University u;
    /** The roof over the halls and the lobby. */
    static final int ROOF = 18;
    /** The halls' east wall behind the stages; the lobby from it to the glass. */
    private static final int AX1 = 46;
    /** The halls' insides along z: hall A to the north, hall B to the south, the passage between. */
    private static final int A0 = LEC_Z0 + 1, A1 = 164, B0 = 172, B1 = LEC_Z1 - 1;
    private static final int PZ0 = 166, PZ1 = 170;
    /** The pit in front of the stage, the first tier's east edge, the tiers' count, the back aisle. */
    private static final int PIT = 38, TIERS = 12, BACK = 13;

    UniLecture(University u) {
        this.u = u;
    }

    private void set(int x, int y, int z, String b) {
        u.set(x, y, z, b);
    }

    private double chance() {
        return u.chance();
    }

    /** The floor's height at x in a hall: the pit and the stage's ends at 0, a tier every two blocks up to the back aisle. */
    static int rake(int x) {
        if (x >= PIT) {
            return 0;
        }
        if (x <= BACK) {
            return TIERS;
        }
        return Math.min(TIERS, (PIT - 1 - x) / 2 + 1);
    }

    void build() {
        shell();
        hall(A0, A1, true);
        hall(B0, B1, false);
        passage();
        lobby();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        int x0 = LEC_X0, x1 = LEC_X1, z0 = LEC_Z0, z1 = LEC_Z1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, "concrete_dark");
                set(x, ROOF, z, "un_roof");
            }
        }
        // the blind walls: board-marked concrete, a fin every sixth block standing proud
        for (int y = 1; y < ROOF + 2; y++) {
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, blind(y, z - z0));
                if ((z - z0) % 6 == 0 && y < ROOF + 1) {
                    set(x0 - 1, y, z, "un_brut_rib");
                }
            }
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, blind(y, x - x0));
                set(x, y, z1, blind(y, x - x0));
                if ((x - x0) % 6 == 0 && y < ROOF + 1 && x < AX1) {
                    set(x, y, z0 - 1, "un_brut_rib");
                    set(x, y, z1 + 1, "un_brut_rib");
                }
            }
            // the halls' east wall behind the stages, the passage's walls between the halls
            for (int z = z0; z <= z1; z++) {
                set(AX1, y, z, y >= ROOF - 1 ? "un_brut_rib" : u.chance() < 0.3 ? "un_brut_b" : "un_brut");
            }
            for (int x = x0; x <= AX1; x++) {
                for (int z = A1 + 1; z <= B0 - 1; z++) {
                    if (y > 5 || z == A1 + 1 || z == B0 - 1) {
                        set(x, y, z, u.chance() < 0.3 ? "un_brut_b" : "un_brut");
                    }
                }
            }
        }
        // the lobby's front: a glass wall the height of the building between steel mullions
        for (int z = z0 + 1; z < z1; z++) {
            boolean mullion = (z - z0) % 4 == 0;
            for (int y = 1; y <= ROOF + 1; y++) {
                String s;
                if (y >= ROOF - 2) {
                    s = "un_brut_rib";
                } else if (mullion || y % 6 == 0) {
                    s = "steel_mullion";
                } else {
                    s = y == 1 ? "glass_spandrel" : u.b.glass("z");
                }
                set(x1, y, z, s);
            }
        }
        // the doors on the quad: a pair either side of the cross walk, a pair at the front walk; one to the side gate's walk
        u.doors(x1, 0, 141, "shop_door", "east");
        u.doors(x1, 0, 143, "shop_door", "east");
        u.doors(x1, 0, 160, "shop_door", "east");
        u.doors(52, 0, z0, "shop_door", "north");
        // the name along the fascia, the seal over the main doors
        u.row("un_name_lecture_", 12, x1 + 1, ROOF - 1, 136, "east", 0.05);
        u.set(x1 + 1, ROOF - 2, 142, "un_seal_4[facing=east]");
        for (int z = 140; z <= 145; z++) {
            u.set(x1 + 1, 4, z, "dn_awning[facing=east]");
        }
    }

    private String blind(int y, int a) {
        if (y >= ROOF) {
            return "un_brut_rib";
        }
        if (y % 6 == 0) {
            return "un_brut_rib";
        }
        return chance() < 0.3 ? "un_brut_b" : "un_brut";
    }

    // ------------------------------------------------------------------ a hall

    /**
     * A hall from z0 to z1 (its inside): the stage across its middle at the lobby end, aisles
     * either end of it to the lobby's doors, the tiers of seats climbing west with three aisles,
     * the back aisle and the booth over it all. finals: hall A, the exam left; else hall B, the
     * clinic and the dead.
     */
    private void hall(int z0, int z1, boolean finals) {
        int zc = (z0 + z1) / 2, s0 = z0 + 7, s1 = z1 - 7;
        // the rake: solid concrete under every tier, carpet on top
        for (int x = LEC_X0 + 1; x < AX1; x++) {
            int f = rake(x);
            for (int z = z0; z <= z1; z++) {
                for (int y = 1; y < f; y++) {
                    set(x, y, z, "concrete_dark");
                }
                set(x, f, z, chance() < 0.2 ? "un_carpet_b" : "un_carpet");
            }
        }
        // the seats: on each tier's back block, facing the stage; the aisles at the walls and in the middle
        for (int x = BACK + 1; x < PIT; x++) {
            int f = rake(x);
            boolean back = rake(x - 1) > f || x == BACK + 1;
            for (int z = z0; z <= z1; z++) {
                boolean aisle = z <= z0 + 1 || z >= z1 - 1 || z == zc || z == zc + 1;
                if (aisle) {
                    if (rake(x - 1) > f) {
                        set(x, f + 1, z, "tr_stair[facing=west]");
                    }
                    continue;
                }
                if (back) {
                    set(x, f + 1, z, seat(finals));
                } else if (finals && chance() < 0.05) {
                    set(x, f + 1, z, "paper");
                }
            }
        }
        // the step up out of the pit to the first tier in the aisles
        for (int z : new int[] {z0, z0 + 1, zc, zc + 1, z1 - 1, z1}) {
            set(PIT, 1, z, "tr_stair[facing=west]");
        }
        // the stage across the middle, its steps at the ends, the lectern and the bench
        for (int x = PIT + 3; x < AX1; x++) {
            for (int z = s0; z <= s1; z++) {
                set(x, 1, z, "tr_stage");
            }
        }
        for (int z = s0 + 1; z <= s1 - 1; z += s1 - s0 - 2) {
            set(PIT + 2, 1, z, "tr_stair[facing=east]");
        }
        set(PIT + 4, 2, zc, "ch_lectern[facing=west]");
        set(PIT + 5, 2, zc - 4, "un_lab_bench[facing=west]");
        set(PIT + 5, 2, zc - 3, "un_lab_bench[facing=west]");
        set(PIT + 5, 2, zc + 3, "sc_av_cart[facing=west]");
        set(PIT + 5, 2, zc + 4, chance() < 0.5 ? "sc_projector[facing=west]" : "cardboard_box[facing=west]");
        // the chalkboards on the wall behind, the screen pulled down between them, the clock over
        u.picture(finals ? "un_board_calc_" : "un_board_stay_", 4, 2, AX1 - 1, 3, s0, "west");
        u.picture("tr_screen_", 9, 4, AX1 - 1, 4, s0 + 4, "west");
        u.picture(finals ? "un_board_stay_" : "un_board_calc_", 4, 2, AX1 - 1, 3, s0 + 13, "west");
        set(AX1 - 1, 9, zc, "bk_wall_clock[facing=west]");
        // the exits each side of the stage to the lobby, lit signs over them
        for (int z : new int[] {z0 + 2, z1 - 3}) {
            u.opening(AX1, AX1, z, z + 1, 0, false);
            u.doors(AX1, 0, z, "fire_door", "east");
            set(AX1 - 1, 3, z, "cv_exit_red[facing=west]");
        }
        // the back aisle: the projector on its cart, the reels, the clock on the back wall
        set(BACK - 1, TIERS + 1, zc, "sc_av_cart[facing=east]");
        set(BACK - 1, TIERS + 2, zc, "sc_projector[facing=east]");
        set(BACK - 2, TIERS + 1, zc + 3, "tr_reel[facing=" + u.dir() + "]");
        set(LEC_X0 + 1, TIERS + 1, zc - 3, "bunk_rack_boxes[facing=east]");
        set(LEC_X0 + 1, TIERS + 4, zc, "bk_wall_clock[facing=east]");
        // oak acoustic panels up the side walls, following the rake
        for (int x = LEC_X0 + 1; x < AX1; x++) {
            int f = rake(x);
            for (int y = f + 1; y <= f + 3; y++) {
                for (int z : new int[] {z0 - 1, z1 + 1}) {
                    String w = u.get(x, y, z);
                    if (w != null && w.startsWith("un_brut")) {
                        set(x, y, z, "cv_panel_oak");
                    }
                }
            }
        }
        // the lights in the roof and on the side walls
        u.lamps(LEC_X0, z0 - 1, AX1, z1 + 1, ROOF, 0.25);
        for (int x = 20; x < AX1; x += 8) {
            int f = rake(x) + 5;
            set(x, f, z0, "tr_sconce" + (u.power && chance() < 0.3 ? "_on" : "") + "[facing=south]");
            set(x, f, z1, "tr_sconce" + (u.power && chance() < 0.3 ? "_on" : "") + "[facing=north]");
        }
        if (finals) {
            // the exam on the lectern, FINALS CANCELLED on the boards' end, the blue books
            u.row("un_spray_finals_", 3, PIT, 4, z0, "south", 0);
            for (int i = 0; i < 6; i++) {
                int x = BACK + 1 + u.rng.nextInt(PIT - BACK - 1), z = z0 + 2 + u.rng.nextInt(z1 - z0 - 3);
                int f = rake(x);
                if (u.empty(x, f + 1, z)) {
                    set(x, f + 1, z, chance() < 0.5 ? "trash_bag[facing=" + u.dir() + "]" : "blood_splat");
                }
            }
        } else {
            // the clinic: the tables in the pit, the stretchers in rows, the sheets over them
            for (int z = s0 - 6; z <= s1 + 6; z += 3) {
                if (z == zc || z == zc + 1) {
                    continue;
                }
                set(PIT + 1, 1, z, "st_stretcher_bag[facing=north]");
            }
            for (int z = s0; z <= s1; z += 4) {
                set(PIT + 3, 2, z, "hs_tray[facing=west]");
            }
            set(PIT + 4, 2, s0 + 1, "bunk_iv_stand");
            set(PIT + 4, 2, s1 - 1, "bunk_first_aid[facing=west]");
            set(PIT + 5, 2, s0 + 2, "sc_cases_water[facing=west]");
            u.picture("un_bulletin_vigil_", 2, 1, AX1 - 1, 9, zc - 6, "west");
        }
        u.mess(PIT, z0, AX1 - 1, z1, 0, 0.04);
    }

    private String seat(boolean finals) {
        double r = chance();
        String s = finals
                ? r < 0.45 ? "tr_seat" : r < 0.7 ? "tr_seat_mix" : r < 0.82 ? "tr_seat_coat" : r < 0.94 ? "tr_seat_up" : "tr_seat_broken"
                : r < 0.3 ? "tr_seat" : r < 0.6 ? "tr_seat_up" : r < 0.8 ? "tr_seat_mix" : r < 0.93 ? "tr_seat_broken" : "air";
        return s.equals("air") ? s : s + "[facing=east]";
    }

    // ------------------------------------------------------------------ the passage and the lobby

    /** The passage between the halls at ground level, from the lobby out to the west walk. */
    private void passage() {
        for (int x = LEC_X0 + 1; x < AX1; x++) {
            for (int z = PZ0; z <= PZ1; z++) {
                set(x, 0, z, "cv_terrazzo");
                set(x, 5, z, "un_slab_vct");
                for (int y = 1; y < 5; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        u.lamps(LEC_X0, PZ0 - 1, AX1, PZ1 + 1, 5, 0.3);
        u.opening(AX1, AX1, PZ0 + 1, PZ1 - 1, 0, false);
        u.doors(LEC_X0, 0, 167, "fire_door", "west");
        u.set(LEC_X0 + 1, 3, 168, "cv_exit_red[facing=east]");
        // lockers and the notice boards down its walls, the drinking fountain
        for (int x = LEC_X0 + 4; x < AX1 - 2; x++) {
            if (x % 9 == 0) {
                continue;
            }
            String l = "sc_locker_" + (chance() < 0.25 ? "open_" : "") + "abc".charAt(x % 3);
            set(x, 1, PZ0, l + "[facing=south]");
            set(x, 2, PZ0, l.replace("sc_locker_", "sc_locker_top_") + "[facing=south]");
        }
        u.picture("un_bulletin_rooms_", 2, 1, 20, 2, PZ1, "north");
        u.poster(30, 2, PZ1, "north");
        set(36, 1, PZ1, "sc_fountain[facing=north]");
        u.mess(LEC_X0 + 1, PZ0, AX1 - 1, PZ1, 0, 0.05);
    }

    /** The lobby: the benches, the notice boards between the halls' doors, vending machines, the restrooms at the south end. */
    private void lobby() {
        int x0 = AX1 + 1, x1 = LEC_X1 - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = LEC_Z0 + 1; z < LEC_Z1; z++) {
                set(x, 0, z, chance() < 0.15 ? "tr_terrazzo" : "cv_terrazzo");
            }
        }
        u.lamps(x0 - 1, LEC_Z0, x1 + 1, LEC_Z1, ROOF, 0.3);
        u.sign("hall_a", x0, 4, A0 + 2, "east");
        u.sign("hall_b", x0, 4, B1 - 3, "east");
        u.picture("un_bulletin_rooms_", 2, 1, x0, 2, 150, "east");
        u.poster(x0, 2, 156, "east");
        u.poster(x0, 2, 180, "east");
        for (int z : new int[] {148, 152, 178, 184}) {
            set(x0 + 4, 1, z, "cv_hall_bench[facing=" + (z % 8 == 0 ? "east" : "west") + "]");
        }
        set(x0, 1, PZ0 - 2, "store_vending[facing=east]");
        set(x0, 2, PZ0 - 2, "store_vending_top[facing=east]");
        set(x0, 1, PZ1 + 2, "pk_vending[facing=east]");
        set(x0 + 5, 1, 172, "bk_post_clock" + (u.power ? "_lit" : ""));
        // the restrooms in the lobby's south end
        int rz = B1 - 10;
        u.partX(x0 + 3, x1, rz, 0, 6, "sc_cmu_dado_a", "sc_cmu");
        u.partX(x0 + 3, x1, rz + 5, 0, 6, "sc_cmu_dado_a", "sc_cmu");
        u.partZ(x0 + 3, rz, B1, 0, 6, "sc_cmu_dado_a", "sc_cmu");
        fillSlab(x0 + 4, rz + 1, x1, B1, 6);
        u.door(x0 + 3, 0, rz + 2, "flat_door", "west");
        u.door(x0 + 3, 0, rz + 7, "flat_door", "west");
        for (int z = rz + 1; z <= B1; z++) {
            if (z == rz + 5) {
                continue;
            }
            for (int x = x0 + 4; x <= x1; x++) {
                set(x, 0, z, "bunk_tile");
            }
            if (z % 2 == 0) {
                set(x1, 1, z, "home_toilet[facing=west]");
            }
        }
        set(x0 + 4, 1, rz + 4, "hs_sink[facing=east]");
        set(x0 + 4, 1, rz + 9, "hs_sink[facing=east]");
        set(x0 + 2, 3, rz + 2, "tx_sign_toilets_0[facing=west]");
        set(x0 + 2, 3, rz + 3, "tx_sign_toilets_1[facing=west]");
        // the Guard's table by the doors, the leaflets, the line that went out the door
        set(x1 - 2, 1, 138, "sc_cafe_table[facing=east]");
        set(x1 - 2, 1, 139, "sc_cafe_table[facing=east]");
        set(x1 - 3, 1, 138, "bunk_chair[facing=east]");
        set(x1 - 2, 2, 139, "paper");
        u.mess(x0, LEC_Z0 + 1, x1, rz - 1, 0, 0.05);
    }

    private void fillSlab(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, y, z, "un_slab_vct");
            }
        }
    }
}
