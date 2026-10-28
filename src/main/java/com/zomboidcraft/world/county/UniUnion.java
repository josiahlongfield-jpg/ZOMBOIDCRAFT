package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.University.UNI_X0;
import static com.zomboidcraft.world.county.University.UNI_X1;
import static com.zomboidcraft.world.county.University.UNI_Z0;
import static com.zomboidcraft.world.county.University.UNI_Z1;

/**
 * The Garvey Student Union, down the quad's east side south of the science hall: two tall floors
 * of orange brick, glass along the ground floor's front, GARVEY STUDENT UNION over the doors. A
 * concourse runs down the front inside the glass between the stairs; off it the bookstore, the
 * cafeteria with its kitchen behind the serving line, the game room; upstairs the ballroom, the
 * lounge over the concourse, the student offices and the campus radio station at the south end.
 *
 * <p>When the buses stopped coming the ones left on campus moved into the ballroom: the cots in
 * rows, the cafeteria's food carried up, the doors barricaded. WE ARE STILL HERE on the board.
 */
final class UniUnion {
    private final University u;
    /** The union's storeys are six tall; the ballroom takes the height. */
    static final int US = 6, ROOF = 2 * US;
    private static final int[] YS = {0, US, ROOF};
    /** The concourse inside the front glass, the rooms' wall east of it. */
    private static final int RW = 167;
    /** The stairs in the concourse's ends. */
    private static final int ST_X = 162, SN_Z = UNI_Z0 + 1, SS_Z = UNI_Z1 - 10;
    /** The ground floor's rooms east of the concourse: bookstore, cafeteria, game room. */
    private static final int BOOK_Z1 = 152, CAFE_Z1 = 184;
    /** The serving line and the kitchen behind it. */
    private static final int LINE_X = 189;

    UniUnion(University u) {
        this.u = u;
    }

    private void set(int x, int y, int z, String b) {
        u.set(x, y, z, b);
    }

    private double chance() {
        return u.chance();
    }

    void build() {
        shell();
        concourse();
        bookstore();
        cafeteria();
        games();
        upstairs();
        u.stair(ST_X, SN_Z, true, YS, "brick_orange", "un_roof");
        u.stair(ST_X, SS_Z, false, YS, "brick_orange", "un_roof");
        roof();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int x = UNI_X0; x <= UNI_X1; x++) {
            for (int z = UNI_Z0; z <= UNI_Z1; z++) {
                set(x, 0, z, chance() < 0.15 ? "cv_terrazzo" : "tr_terrazzo");
                set(x, US, z, chance() < 0.15 ? "un_slab_carpet_b" : "un_slab_carpet");
                set(x, ROOF, z, "un_roof");
            }
        }
        for (int y = 1; y <= ROOF + 1; y++) {
            for (int z = UNI_Z0; z <= UNI_Z1; z++) {
                set(UNI_X0, y, z, front(y, z));
                set(UNI_X1, y, z, side(y, z - UNI_Z0, "east"));
            }
            for (int x = UNI_X0; x <= UNI_X1; x++) {
                set(x, y, UNI_Z0, side(y, x - UNI_X0, "north"));
                set(x, y, UNI_Z1, side(y, x - UNI_X0, "south"));
            }
        }
        u.doors(UNI_X0, 0, 141, "shop_door", "west");
        u.doors(UNI_X0, 0, 143, "shop_door", "west");
        u.doors(UNI_X0, 0, 160, "shop_door", "west");
        u.doors(UNI_X0, 0, 186, "shop_door", "west");
        // the deep canopy over the front doors, the name on its fascia
        for (int z = 137; z <= 148; z++) {
            for (int x = UNI_X0 - 4; x < UNI_X0; x++) {
                set(x, 5, z, "un_brut");
            }
            if (z == 137 || z == 148) {
                for (int y = 1; y < 5; y++) {
                    set(UNI_X0 - 4, y, z, "un_brut_rib");
                }
            }
        }
        u.row("un_name_union_", 10, UNI_X0 - 5, 5, 138, "west", 0.05);
        // the kitchen's back door and the loading step
        u.door(UNI_X1, 0, 170, "fire_door", "east");
        set(UNI_X1 + 1, 1, 172, "trash_bag[facing=east]");
        set(UNI_X1 + 2, 1, 168, "dumpster[facing=east]");
    }

    /** The front: glass between brick piers on the ground floor, a brick band, tall windows upstairs. */
    private String front(int y, int z) {
        int a = z - UNI_Z0;
        boolean pier = a % 6 == 0 || a <= 1 || z >= UNI_Z1 - 1;
        if (y == US || y > ROOF) {
            return "stone_trim";
        }
        if (y < US) {
            if (pier || y == US - 1) {
                return u.b.wall("brick_orange", y);
            }
            return y == 1 ? "glass_spandrel" : u.b.glass("z");
        }
        return window(y - US, a, "west");
    }

    private String side(int y, int a, String out) {
        if (y == US || y > ROOF) {
            return "stone_trim";
        }
        return window(y % US, a, out);
    }

    private String window(int k, int a, String out) {
        if ((k == 2 || k == 3 || k == 4) && a % 6 >= 2 && a % 6 <= 4 && a > 2) {
            String state = chance() < u.b.boarded ? "boarded" : chance() < u.b.damage * 0.5 ? (chance() < 0.7 ? "broken" : "empty") : "glass";
            return k == 4 ? u.b.wall("brick_orange", k) : "ext_win_brown_one_" + state + "_" + (k == 2 ? "lo" : "hi") + "[facing=" + out + "]";
        }
        return u.b.wall("brick_orange", k);
    }

    // ------------------------------------------------------------------ the ground floor

    /** The concourse: the info desk, the mail boxes, notice boards, benches, the walls down to the rooms. */
    private void concourse() {
        u.partZ(RW, UNI_Z0 + 1, UNI_Z1 - 1, 0, US, "brick_orange", "drywall");
        u.lamps(UNI_X0, UNI_Z0, RW, UNI_Z1, US, 0.3);
        // the information desk facing the doors
        for (int z = 150; z <= 155; z++) {
            set(161, 1, z, "un_circ[facing=west]");
        }
        set(162, 1, 152, "office_chair[facing=west]");
        u.sign("desk", RW - 1, 4, 152, "west");
        // the mail boxes and the notice boards on the rooms' wall
        for (int z = 164; z <= 169; z++) {
            set(RW - 1, 1, z, "sc_mailboxes[facing=west]");
        }
        u.sign("mail", RW - 1, 4, 166, "west");
        u.picture("un_bulletin_rooms_", 2, 1, RW - 1, 2, 174, "west");
        u.picture("un_bulletin_vigil_", 2, 1, RW - 1, 2, 182, "west");
        for (int z = 172; z <= 184; z += 3) {
            u.poster(RW - 1, 3, z, "west");
        }
        for (int z : new int[] {150, 172, 178}) {
            set(158, 1, z, "cv_hall_bench[facing=" + (z == 150 ? "east" : "west") + "]");
        }
        set(156, 1, 190, "store_vending[facing=east]");
        set(156, 2, 190, "store_vending_top[facing=east]");
        set(156, 1, 191, "pk_vending[facing=east]");
        set(160, 1, 158, "bk_post_clock" + (u.power ? "_lit" : ""));
        u.mess(UNI_X0 + 1, UNI_Z0 + 11, RW - 1, UNI_Z1 - 11, 0, 0.05);
    }

    /** The bookstore: glass front on the concourse, shelves of textbooks, the sweatshirts, the tills. */
    private void bookstore() {
        int z0 = UNI_Z0 + 1, z1 = BOOK_Z1 - 1;
        u.partX(RW + 1, UNI_X1 - 1, BOOK_Z1, 0, US, "drywall", "drywall");
        for (int z = 144; z <= 150; z++) {
            for (int y = 2; y < US - 1; y++) {
                set(RW, y, z, "tx_glass[axis=z]");
            }
            set(RW, 1, z, "glass_spandrel");
        }
        u.doors(RW, 0, 146, "shop_door", "west");
        u.sign("bookstore", RW - 1, 5, 148, "west");
        for (int x = RW + 4; x <= UNI_X1 - 4; x += 3) {
            for (int z = z0 + 2; z <= z1 - 6; z++) {
                boolean gap = (z - z0) % 8 == 7;
                if (gap) {
                    continue;
                }
                boolean emptied = chance() < 0.35;
                set(x, 1, z, emptied ? "store_shelf_empty[facing=east]" : "un_textbooks[facing=east]");
                set(x + 1, 1, z, emptied ? "store_shelf_empty[facing=west]" : "un_textbooks[facing=west]");
                set(x, 2, z, "store_shelf_empty_top[facing=east]");
                set(x + 1, 2, z, "store_shelf_empty_top[facing=west]");
            }
        }
        for (int z = z0; z <= z1; z++) {
            if (z % 3 != 0) {
                set(UNI_X1 - 1, 1, z, "store_shelf_thrift[facing=west]");
                set(UNI_X1 - 1, 2, z, "store_shelf_thrift_top[facing=west]");
            }
        }
        for (int x = RW + 3; x <= RW + 8; x++) {
            set(x, 1, z1 - 2, "store_counter[facing=south]");
        }
        set(RW + 4, 2, z1 - 2, "store_register[facing=south]");
        set(RW + 7, 2, z1 - 2, "store_register[facing=south]");
        set(RW + 10, 4, z1, "un_banner_" + u.rng.nextInt(4) + "[facing=north]");
        u.mess(RW + 1, z0, UNI_X1 - 1, z1, 0, 0.06);
    }

    /** The cafeteria: tables in rows, the serving line, the kitchen behind it. */
    private void cafeteria() {
        int z0 = BOOK_Z1 + 1, z1 = CAFE_Z1 - 1;
        u.partX(RW + 1, UNI_X1 - 1, CAFE_Z1, 0, US, "drywall", "drywall");
        u.opening(RW, RW, 157, 160, 0, false);
        u.opening(RW, RW, 175, 178, 0, false);
        u.sign("cafeteria", RW - 1, 4, 158, "west");
        for (int x = RW + 1; x < LINE_X; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, (x + z) % 2 == 0 ? "sc_vct" : "sc_vct_b");
            }
        }
        for (int x = RW + 3; x <= LINE_X - 5; x += 4) {
            for (int z = z0 + 2; z <= z1 - 2; z += 3) {
                if (chance() < 0.15) {
                    set(x, 1, z, "sc_cafe_folded[facing=north]");
                    set(x, 2, z, "sc_cafe_folded_top[facing=north]");
                    continue;
                }
                set(x, 1, z, "sc_cafe_table[facing=north]");
                set(x + 1, 1, z, "sc_cafe_table[facing=north]");
                if (chance() < 0.3) {
                    set(x, 2, z, u.any("sc_trays[facing=north]", "sc_milk", "sc_lunchbox[facing=east]", "paper"));
                }
            }
        }
        // the serving line: the counter with the trays and the rail, the kitchen behind
        u.partZ(LINE_X, z0, z1, 0, US, "dn_wall_cmu_tile_white", "dn_wall_cmu_tile_white");
        for (int z = z0 + 3; z <= z1 - 3; z++) {
            set(LINE_X, 1, z, "sc_counter[facing=west]");
            set(LINE_X, 2, z, z % 4 == 0 ? "sc_trays[facing=west]" : "air");
            set(LINE_X, 3, z, "air");
        }
        set(LINE_X - 1, 1, z0 + 2, "sc_register[facing=west]");
        u.door(LINE_X, 0, z0 + 1, "flat_door", "east");
        for (int x = LINE_X + 1; x < UNI_X1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, chance() < 0.1 ? "dn_quarry_grease" : "dn_quarry");
            }
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            String k = z % 6 == 0 ? "dn_range" : z % 6 == 1 ? "dn_griddle" : z % 6 == 2 ? "dn_fryer" : z % 6 == 3 ? "dn_prep" : z % 6 == 4 ? "dn_sink" : "dn_prep_bare";
            set(UNI_X1 - 1, 1, z, k + "[facing=west]");
            if (z % 6 <= 2) {
                set(UNI_X1 - 1, 3, z, "dn_hood[facing=west]");
            }
        }
        for (int z = z0 + 3; z <= z1 - 3; z += 5) {
            set(LINE_X + 5, 1, z, "dn_prep[facing=north]");
            set(LINE_X + 6, 1, z, "dn_prep[facing=north]");
        }
        set(LINE_X + 2, 1, z1, "pk_fridge[facing=north]");
        set(LINE_X + 4, 1, z1, "pk_fridge[facing=north]");
        set(LINE_X + 2, 1, z0, "dn_dish_lo[facing=south]");
        set(LINE_X + 2, 2, z0, "dn_dish_hi[facing=south]");
        u.mess(RW + 1, z0, UNI_X1 - 1, z1, 0, 0.05);
    }

    /** The game room: the arcade cabinets, the pool tables, the lounge chairs, the jukebox long dead. */
    private void games() {
        int z0 = CAFE_Z1 + 1, z1 = SS_Z - 1;
        u.doors(RW, 0, 188, "flat_door", "west");
        u.sign("games", RW - 1, 4, 190, "west");
        for (int x = RW + 2; x <= UNI_X1 - 2; x++) {
            if (x % 2 == 0) {
                set(x, 1, UNI_Z1 - 1, "un_arcade_lo[facing=north]");
                set(x, 2, UNI_Z1 - 1, "un_arcade_hi" + (u.power && chance() < 0.5 ? "_on" : "") + "[facing=north]");
            }
        }
        for (int x = RW + 6; x <= UNI_X1 - 6; x += 6) {
            for (int z = z0 + 3; z <= UNI_Z1 - 6; z += 5) {
                set(x, 1, z, "un_pool_a");
                set(x + 1, 1, z, "un_pool_b");
            }
        }
        set(UNI_X1 - 1, 1, z0 + 1, "home_sofa_brown_l[facing=west]");
        set(UNI_X1 - 1, 1, z0 + 2, "home_sofa_brown_m[facing=west]");
        set(UNI_X1 - 1, 1, z0 + 3, "home_sofa_brown_r[facing=west]");
        set(UNI_X1 - 4, 1, z0 + 2, "pk_tv_crt[facing=east]");
        set(RW + 1, 1, UNI_Z1 - 3, "un_records[facing=east]");
        u.mess(RW + 1, z0, UNI_X1 - 1, UNI_Z1 - 1, 0, 0.04);
    }

    // ------------------------------------------------------------------ upstairs

    /**
     * The ballroom over the bookstore and the cafeteria, the shelter it became: cots in rows, the
     * food and water stacked on the stage, the doors barricaded from inside; the lounge over the
     * concourse; the offices and the radio station over the game room.
     */
    private void upstairs() {
        int y = US, bz1 = CAFE_Z1;
        u.partZ(RW, UNI_Z0 + 1, UNI_Z1 - 1, y, US, "cv_panel_oak", "drywall");
        u.partX(RW + 1, UNI_X1 - 1, bz1, y, US, "drywall", "drywall");
        for (int x = RW + 1; x < UNI_X1; x++) {
            for (int z = UNI_Z0 + 1; z < bz1; z++) {
                set(x, y, z, "un_slab_boards");
            }
        }
        // the ballroom's doors, the barricade, the signs
        for (int z : new int[] {146, 170}) {
            u.doors(RW, y, z, "flat_door", "west");
            for (int dz : new int[] {-2, -1, 2, 3}) {
                if (chance() < 0.75) {
                    set(RW + 1, y + 1, z + dz, u.any("sc_cafe_folded[facing=east]", "home_chair_tipped[facing=east]", "sandbags[facing=west]", "cardboard_box[facing=east]"));
                }
            }
        }
        u.sign("ballroom", RW - 1, y + 4, 148, "west");
        u.sign("shelter", RW - 1, y + 4, 172, "west");
        // the stage at the east end, the supplies on it, the chandeliers
        for (int x = UNI_X1 - 6; x < UNI_X1; x++) {
            for (int z = 145; z <= 172; z++) {
                set(x, y + 1, z, "tr_stage");
            }
        }
        set(UNI_X1 - 7, y + 1, 146, "tr_stair[facing=east]");
        set(UNI_X1 - 7, y + 1, 171, "tr_stair[facing=east]");
        for (int z = 147; z <= 170; z += 2) {
            for (int x = UNI_X1 - 5; x < UNI_X1 - 1; x++) {
                if (chance() < 0.6) {
                    set(x, y + 2, z, u.any("sc_cases_water[facing=west]", "sc_cases_blankets[facing=west]", "st_pallet_meals", "cardboard_box[facing=west]"));
                }
            }
        }
        u.picture("un_board_stay_", 4, 2, UNI_X1 - 1, y + 3, 156, "west");
        for (int x = 176; x <= 196; x += 10) {
            for (int z = 142; z <= 176; z += 12) {
                set(x, ROOF - 1, z, "ch_chandelier" + (u.power && chance() < 0.4 ? "_lit" : ""));
            }
        }
        // the cots in rows, some of them still made, blankets, the bags, the ones they couldn't save
        for (int x = RW + 3; x <= UNI_X1 - 10; x += 3) {
            for (int z = UNI_Z0 + 3; z <= bz1 - 4; z += 3) {
                if (chance() < 0.15) {
                    continue;
                }
                boolean b = chance() < 0.3;
                set(x, y + 1, z, (b ? "sc_cot_head_b" : "sc_cot_head") + "[facing=north]");
                set(x, y + 1, z + 1, (b ? "sc_cot_foot_b" : "sc_cot_foot") + "[facing=north]");
                if (chance() < 0.25) {
                    set(x + 1, y + 1, z, u.any("sc_pack_blue[facing=north]", "sc_pack_red[facing=north]", "sc_pack_green[facing=north]", "sc_lunchbox[facing=north]"));
                }
            }
        }
        for (int i = 0; i < 5; i++) {
            int x = RW + 3 + u.rng.nextInt(UNI_X1 - RW - 14), z = UNI_Z0 + 3 + u.rng.nextInt(bz1 - UNI_Z0 - 6);
            if (u.empty(x, y + 1, z)) {
                set(x, y + 1, z, chance() < 0.6 ? "bunk_bodybag[facing=" + u.dir() + "]" : "home_blood_pool");
            }
        }
        u.lamps(RW, UNI_Z0, UNI_X1, bz1, ROOF, 0.15);
        // the lounge over the concourse: sofas round a television, the beanbags, a piano
        for (int z = 150; z <= 180; z += 10) {
            set(UNI_X0 + 2, y + 1, z, "home_sofa_blue_l[facing=east]");
            set(UNI_X0 + 2, y + 1, z + 1, "home_sofa_blue_m[facing=east]");
            set(UNI_X0 + 2, y + 1, z + 2, "home_sofa_blue_r[facing=east]");
            set(UNI_X0 + 6, y + 1, z + 1, chance() < 0.5 ? "pk_tv_crt[facing=west]" : "un_beanbag[facing=west]");
            set(UNI_X0 + 5, y + 1, z + 4, "un_beanbag[facing=" + u.dir() + "]");
        }
        set(RW - 1, y + 1, 186, "home_piano_l[facing=west]");
        set(RW - 1, y + 1, 187, "home_piano_r[facing=west]");
        u.sign("lounge", RW - 1, y + 4, 160, "west");
        u.lamps(UNI_X0, UNI_Z0, RW, UNI_Z1, ROOF, 0.25);
        u.mess(UNI_X0 + 1, UNI_Z0 + 11, RW - 1, UNI_Z1 - 11, y, 0.04);
        // the offices and the radio station over the game room
        int oz0 = bz1 + 1, oz1 = UNI_Z1 - 1, mx = 185;
        u.partZ(mx, oz0, oz1, y, US, "drywall", "drywall");
        u.door(RW, y, oz0 + 3, "flat_door", "west");
        u.door(mx, y, oz0 + 6, "flat_door", "east");
        for (int z = oz0 + 2; z <= oz1 - 2; z += 4) {
            set(RW + 3, y + 1, z, "home_desk[facing=north]");
            set(RW + 3, y + 1, z + 1, "office_chair[facing=north]");
            set(RW + 9, y + 1, z, "home_desk[facing=north]");
            set(RW + 9, y + 1, z + 1, chance() < 0.3 ? "office_chair_fallen[facing=" + u.dir() + "]" : "office_chair[facing=north]");
            set(mx - 1, y + 1, z, "filing_cabinet[facing=west]");
        }
        set(RW + 6, y + 1, oz1, "sc_mimeo[facing=north]");
        // the radio station: the desk and its decks, the records on every wall, the booth's window
        for (int x = mx + 3; x <= mx + 8; x++) {
            set(x, y + 1, oz0 + 6, "bunk_radio_desk[facing=south]");
        }
        set(mx + 5, y + 1, oz0 + 7, "office_chair[facing=north]");
        for (int x = mx + 1; x < UNI_X1; x++) {
            if (x % 2 == 0) {
                set(x, y + 1, oz1, "un_records[facing=north]");
                set(x, y + 2, oz1, "un_records[facing=north]");
            }
        }
        for (int z = oz0 + 1; z <= oz0 + 4; z++) {
            set(UNI_X1 - 1, y + 1, z, "home_garage_shelf[facing=west]");
        }
        u.poster(mx + 1, y + 2, oz1 - 3, "east");
        u.lamps(RW, bz1, UNI_X1, UNI_Z1, ROOF, 0.2);
        u.mess(RW + 1, oz0, UNI_X1 - 1, oz1, y, 0.04);
    }

    /** The roof: the radio mast, the air handlers, what the shelter kept up there. */
    private void roof() {
        int mx = UNI_X1 - 6, mz = UNI_Z1 - 8;
        set(mx, ROOF + 1, mz, "concrete_dark");
        for (int y = ROOF + 2; y <= ROOF + 16; y++) {
            set(mx, y, mz, "dn_mast");
        }
        set(mx, ROOF + 17, mz, "fit_red" + (u.power ? "_on" : ""));
        for (int x = 172; x <= 196; x += 8) {
            set(x, ROOF + 1, 150, "hvac");
            set(x + 1, ROOF + 1, 150, "hvac");
        }
        for (int x = 172; x <= 186; x += 3) {
            set(x, ROOF + 1, 176, u.any("bunk_drum_water", "tarp_green", "sc_bucket"));
        }
    }
}
