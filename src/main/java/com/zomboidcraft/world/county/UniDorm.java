package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.University.DORM_X0;
import static com.zomboidcraft.world.county.University.DORM_X1;
import static com.zomboidcraft.world.county.University.DORM_Z0;
import static com.zomboidcraft.world.county.University.DORM_Z1;

/**
 * Hawthorne Hall, the dormitory tower in the campus's north-east corner by the gate: ten floors
 * of precast concrete and brown brick, a window to every room, HAWTHORNE HALL down its end. A
 * corridor runs the length of every floor between the fire stairs; the rooms either side, two to
 * a room; the bathroom and the lounge by the lift in the middle. On the ground floor the lobby
 * with the front desk, the mail boxes, the laundry and the television lounge.
 *
 * <p>The kids on the top floors stayed: they pushed the beds against the stair doors, ran
 * buckets up from the showers and hung bedsheets out of the windows with HELP on them. GO HOME
 * is sprayed down the corridor on the sixth.
 */
final class UniDorm {
    private final University u;
    static final int DS = 4, FLOORS = 10, ROOF = DS * FLOORS;
    private static final int[] YS = new int[FLOORS + 1];

    static {
        for (int i = 0; i <= FLOORS; i++) {
            YS[i] = i * DS;
        }
    }

    /** The corridor's walls; the rooms north and south of it. */
    private static final int CN = 21, CS = 25;
    /** The stairs at the corridor's ends on its north side, their doors on it. */
    private static final int SW_X = DORM_X0 + 1, SE_X = DORM_X1 - 6;
    /** The lift on the corridor's south side in the middle; the bathroom west of it, the lounge east. */
    private static final int LIFT_X = 176, LIFT_Z = CS + 1;
    private static final int BATH_X0 = 168, LOUNGE_X1 = 186;
    /** The entrance in the south face, the lobby behind it. */
    private static final int DOOR_X = 158;

    UniDorm(University u) {
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
        for (int f = 0; f < FLOORS; f++) {
            floor(f);
        }
        // the cell east of the east stair is built solid, so nothing's shut in there
        u.fill(SE_X + 5, 1, DORM_Z0 + 1, DORM_X1 - 1, ROOF - 1, CN, "un_brut");
        u.stair(SW_X, DORM_Z0, true, YS, "un_brut", "un_roof");
        u.stair(SE_X, DORM_Z0, true, YS, "un_brut", "un_roof");
        u.lift(LIFT_X, LIFT_Z, 3, 3, YS, "un_brut", ROOF + 2);
        roof();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int x = DORM_X0; x <= DORM_X1; x++) {
            for (int z = DORM_Z0; z <= DORM_Z1; z++) {
                set(x, 0, z, "concrete_dark");
                for (int f = 1; f < FLOORS; f++) {
                    set(x, f * DS, z, "un_slab_dorm");
                }
                set(x, ROOF, z, "un_roof");
            }
        }
        for (int y = 1; y <= ROOF + 1; y++) {
            for (int x = DORM_X0; x <= DORM_X1; x++) {
                set(x, y, DORM_Z0, wallAt(y, x - DORM_X0, "north"));
                set(x, y, DORM_Z1, wallAt(y, x - DORM_X0, "south"));
            }
            for (int z = DORM_Z0; z <= DORM_Z1; z++) {
                set(DORM_X0, y, z, end(y));
                set(DORM_X1, y, z, end(y));
            }
        }
        // the name down the west end, read from the gate
        u.row("un_name_dorm_", 10, DORM_X0 - 1, ROOF - 2, 18, "west", 0.04);
        // the entrance: the canopy on its posts, the doors, the steps' rail
        u.doors(DOOR_X, 0, DORM_Z1, "shop_door", "south");
        u.doors(DOOR_X + 2, 0, DORM_Z1, "shop_door", "south");
        for (int x = DOOR_X - 2; x <= DOOR_X + 5; x++) {
            for (int z = DORM_Z1 + 1; z <= DORM_Z1 + 4; z++) {
                set(x, 4, z, "un_brut");
            }
            set(x, 3, DORM_Z1, "glass_spandrel");
        }
        for (int x : new int[] {DOOR_X - 2, DOOR_X + 5}) {
            for (int y = 1; y < 4; y++) {
                set(x, y, DORM_Z1 + 4, "steel_mullion");
            }
        }
        // a door at the east end to the gate's lot
        u.door(DORM_X1, 0, 30, "fire_door", "east");
    }

    /** The long faces: precast bands at the floors, brown brick piers, a window to each room. */
    private String wallAt(int y, int a, String out) {
        int k = y % DS;
        if (y > ROOF || k == 0) {
            return chance() < 0.25 ? "concrete_panel_b" : "concrete_panel";
        }
        if (y < DS) {
            return u.b.wall("brick_dark", y);
        }
        int m = (a - 6) % 5;
        if (a > 5 && a < DORM_X1 - DORM_X0 - 5 && (m == 2 || m == 3) && (k == 1 || k == 2)) {
            String state = chance() < u.b.boarded ? "boarded" : chance() < u.b.damage * 0.5 ? (chance() < 0.7 ? "broken" : "empty")
                    : u.power && chance() < 0.06 ? "lit" : "glass";
            return "ext_win_alu_one_" + state + "_" + (k == 1 ? "lo" : "hi") + "[facing=" + out + "]";
        }
        return u.b.wall("brick_dark", y);
    }

    /** The blind ends: brick between the bands. */
    private String end(int y) {
        return y % DS == 0 || y > ROOF ? "concrete_panel" : u.b.wall("brick_dark", y);
    }

    // ------------------------------------------------------------------ the floors

    private void floor(int f) {
        int y = f * DS;
        boolean holdout = f >= 7, sprayed = f == 5;
        // the corridor
        u.partX(DORM_X0 + 1, DORM_X1 - 1, CN, y, DS, "drywall", "drywall");
        u.partX(DORM_X0 + 1, DORM_X1 - 1, CS, y, DS, "drywall", "drywall");
        for (int x = DORM_X0 + 1; x < DORM_X1; x++) {
            for (int z = CN + 1; z < CS; z++) {
                set(x, y, z, f == 0 ? "sc_vct" : "un_slab_vct");
            }
        }
        // the north rooms between the stairs
        for (int x0 = SW_X + 5; x0 + 3 < SE_X; x0 += 5) {
            if (x0 > SW_X + 5) {
                u.partZ(x0 - 1, DORM_Z0 + 1, CN - 1, y, DS, "drywall", "drywall");
            }
            if (f == 0 && x0 < 178) {
                continue;
            }
            room(x0, DORM_Z0 + 1, x0 + 3, CN - 1, y, true, holdout);
        }
        if (f == 0) {
            groundNorth();
        }
        // the south rooms: west of the bathroom, east of the lounge
        for (int x0 = DORM_X0 + 1; x0 + 3 < BATH_X0; x0 += 5) {
            u.partZ(x0 + 4, CS + 1, DORM_Z1 - 1, y, DS, "drywall", "drywall");
            if (f > 0) {
                room(x0, CS + 1, x0 + 3, DORM_Z1 - 1, y, false, holdout);
            }
        }
        for (int x0 = LOUNGE_X1 + 2; x0 < DORM_X1; x0 += 5) {
            int x1 = Math.min(DORM_X1 - 1, x0 + 3);
            if (DORM_X1 - 1 - x1 < 3) {
                x1 = DORM_X1 - 1;
            }
            u.partZ(x0 - 1, CS + 1, DORM_Z1 - 1, y, DS, "drywall", "drywall");
            room(x0, CS + 1, x1, DORM_Z1 - 1, y, false, holdout);
            if (x1 == DORM_X1 - 1) {
                break;
            }
        }
        if (f == 0) {
            lobby();
        }
        bathroom(y);
        lounge(y, f);
        // the corridor's things: the RA's board, the fire extinguishers, the floor's number by the lift
        u.picture(f == 0 ? "un_bulletin_rooms_" : holdout ? "un_bulletin_vigil_" : "un_bulletin_rooms_", 2, 1, 170, y + 2, CN + 1, "south");
        set(DORM_X0 + 12, y + 2, CS - 1, "bunk_extinguisher[facing=north]");
        set(DORM_X1 - 12, y + 2, CS - 1, "bunk_extinguisher[facing=north]");
        u.poster(185, y + 2, CN + 1, "south");
        if (sprayed) {
            u.row("un_spray_gohome_", 3, 188, y + 2, CN + 1, "south", 0);
        }
        if (holdout) {
            // the beds pushed against the stair doors' sides, the buckets, the bags
            for (int sx : new int[] {SW_X + 2, SE_X + 2}) {
                set(sx - 1, y + 1, CN + 1, "home_bed_bloody_foot[facing=" + u.dir() + "]");
                set(sx + 1, y + 1, CN + 1, "cardboard_box[facing=north]");
            }
            for (int i = 0; i < 4; i++) {
                int x = DORM_X0 + 8 + u.rng.nextInt(DORM_X1 - DORM_X0 - 16);
                if (u.empty(x, y + 1, CN + 2)) {
                    set(x, y + 1, CN + 2, u.any("sc_bucket", "trash_bag[facing=north]", "sc_cases_water[facing=north]"));
                }
            }
        }
        u.lamps(DORM_X0, CN, DORM_X1, CS, y + DS, holdout ? 0.05 : 0.3);
        u.mess(DORM_X0 + 7, CN + 1, DORM_X1 - 7, CS - 1, y, holdout ? 0.08 : 0.04);
    }

    /**
     * A room, x0..x1 by z0..z1 inside: two beds along the side walls, their heads to the window,
     * the desks between, the closets by the door, the fridge, the posters; its door off the corridor.
     */
    private void room(int x0, int z0, int x1, int z1, int y, boolean north, boolean holdout) {
        int doorZ = north ? CN : CS;
        int dx = x0 + 1;
        u.door(dx, y, doorZ, "flat_door", north ? "south" : "north");
        int head = north ? z0 : z1, step = north ? 1 : -1;
        String along = north ? "south" : "north";
        String in = north ? "south" : "north";
        String[] covers = {"blue", "quilt", "rose", "bloody"};
        for (int bx : new int[] {x0, x1}) {
            if (holdout && bx == x0 && chance() < 0.5) {
                continue;  // carried to the stair doors
            }
            String c = covers[chance() < 0.1 ? 3 : u.rng.nextInt(3)];
            set(bx, y + 1, head, "home_bed_" + c + "_head[facing=" + along + "]");
            set(bx, y + 1, head + step, "home_bed_" + c + "_foot[facing=" + along + "]");
        }
        // the desks by the beds' feet, the closets by the door
        set(x0, y + 1, head + 3 * step, "un_desk[facing=east]");
        set(x1, y + 1, head + 3 * step, "un_desk[facing=west]");
        int cz = north ? z1 : z0;
        set(x1, y + 1, cz, "un_wardrobe_lo[facing=" + (north ? "north" : "south") + "]");
        set(x1, y + 2, cz, "un_wardrobe_hi[facing=" + (north ? "north" : "south") + "]");
        set(x1 - 1, y + 1, head + 4 * step, chance() < 0.5 ? "un_minifridge[facing=west]" : "un_beanbag[facing=" + u.dir() + "]");
        u.poster(x0, y + 2, head + 2 * step, "east");
        u.poster(x1, y + 2, head + step, "west");
        if (chance() < 0.4) {
            set(x0 + 1, y + 1, head + 2 * step, u.any("un_records[facing=north]", "sc_pack_blue[facing=north]", "trash_bag[facing=north]", "un_textbooks[facing=north]"));
        }
        if (holdout && chance() < 0.3) {
            set(x0 + 2, y + 1, head + 3 * step, chance() < 0.5 ? "home_blood_pool" : "bunk_bodybag[facing=" + u.dir() + "]");
        }
        u.mess(x0, z0, x1, z1, y, 0.04);
    }

    /** The ground floor's north side: the laundry and the television lounge west, the RA's rooms east. */
    private void groundNorth() {
        int z0 = DORM_Z0 + 1, z1 = CN - 1;
        // two rooms' widths each: the partitions between the pairs come down
        u.fill(162, 1, z0, 162, DS - 1, z1, "air");
        u.fill(172, 1, z0, 172, DS - 1, z1, "air");
        // the laundry: washers and dryers along the north wall, the folding tables
        for (int x = 158; x <= 166; x++) {
            set(x, 1, z0, (x % 2 == 0 ? "store_washer" : "store_dryer") + "[facing=south]");
            if (x >= 160 && x <= 164) {
                set(x, 1, 17, "sc_cafe_table[facing=north]");
            }
        }
        set(166, 1, z1, "sc_bucket");
        u.door(163, 0, CN, "flat_door", "south");
        u.sign("laundry", 163, 3, CN + 1, "south");
        // the television lounge
        u.door(171, 0, CN, "flat_door", "south");
        set(169, 1, z0 + 1, "home_sofa_floral_l[facing=south]");
        set(170, 1, z0 + 1, "home_sofa_floral_m[facing=south]");
        set(171, 1, z0 + 1, "home_sofa_floral_r[facing=south]");
        set(170, 1, z0 + 5, "pk_tv_crt[facing=north]");
        set(174, 1, z0 + 3, "un_beanbag[facing=west]");
        set(175, 1, z0 + 1, "un_records[facing=south]");
        u.sign("lounge", 171, 3, CN + 1, "south");
    }

    /** The lobby inside the doors: the front desk, the mail boxes, the notice boards, the couches. */
    private void lobby() {
        int x0 = DORM_X0 + 1, x1 = BATH_X0 - 1;
        // no rooms here: the partitions come down
        for (int x = x0; x <= x1; x++) {
            for (int z = CS; z < DORM_Z1; z++) {
                for (int y = 1; y < DS; y++) {
                    set(x, y, z, "air");
                }
                set(x, 0, z, chance() < 0.15 ? "cv_terrazzo" : "tr_terrazzo");
            }
        }
        for (int z = CS + 1; z <= CS + 4; z++) {
            set(x1 - 3, 1, z, "un_circ[facing=west]");
        }
        set(x1 - 2, 1, CS + 2, "office_chair[facing=west]");
        set(x1, 1, CS + 1, "sc_mailboxes[facing=west]");
        set(x1, 1, CS + 3, "sc_mailboxes[facing=west]");
        u.sign("desk", x1 - 3, 3, DORM_Z1 - 1, "north");
        set(x0, 1, CS + 2, "home_sofa_brown_l[facing=east]");
        set(x0, 1, CS + 3, "home_sofa_brown_m[facing=east]");
        set(x0, 1, CS + 4, "home_sofa_brown_r[facing=east]");
        u.picture("un_bulletin_vigil_", 2, 1, x0, 2, DORM_Z1 - 3, "east");
        set(DOOR_X + 1, 3, DORM_Z1 - 1, "bk_wall_clock[facing=north]");
        u.mess(x0, CS, x1, DORM_Z1 - 1, 0, 0.05);
    }

    /** The bathroom west of the lift: the showers along the back, the stalls, the basins on the corridor wall. */
    private void bathroom(int y) {
        int x0 = BATH_X0, x1 = LIFT_X - 2;
        u.partZ(x0 - 1, CS + 1, DORM_Z1 - 1, y, DS, "sc_cmu_dado_a", "sc_cmu");
        u.partZ(LIFT_X - 1, LIFT_Z + 4, DORM_Z1 - 1, y, DS, "sc_cmu_dado_a", "sc_cmu");
        u.door(x0 + 1, y, CS, "flat_door", "north");
        for (int x = x0; x <= x1; x++) {
            for (int z = CS + 1; z < DORM_Z1; z++) {
                if (y == 0) {
                    set(x, y, z, "bunk_tile");
                }
            }
            if (x > x0) {
                set(x, y + 1, DORM_Z1 - 1, (x % 2 == 0 ? "dn_shower" : "home_shower_curtain") + "[facing=north]");
            }
        }
        set(x1, y + 1, CS + 2, "fit_toilet_stall[facing=west]");
        set(x1, y + 2, CS + 2, "fit_stall_top[facing=west]");
        set(x1, y + 1, CS + 4, "fit_toilet_stall[facing=west]");
        set(x1, y + 2, CS + 4, "fit_stall_top[facing=west]");
        set(x0, y + 1, CS + 3, "hs_sink[facing=east]");
        set(x0, y + 1, CS + 5, "hs_sink[facing=east]");
        set(x0, y + 2, CS + 4, "home_mirror[facing=east]");
        set(x0 + 2, y + 3, CS - 1, "tx_sign_toilets_0[facing=north]");
        set(x0 + 3, y + 3, CS - 1, "tx_sign_toilets_1[facing=north]");
    }

    /** The floor lounge east of the lift, round behind it: couches, the study tables, the vending machine. */
    private void lounge(int y, int f) {
        int x0 = LIFT_X + 4, x1 = LOUNGE_X1;
        u.partZ(x1 + 1, CS + 1, DORM_Z1 - 1, y, DS, "drywall", "drywall");
        u.opening(x0 + 1, x0 + 3, CS, CS, y, false);
        set(x0 + 2, y + 1, DORM_Z1 - 1, "home_sofa_blue_l[facing=north]");
        set(x0 + 3, y + 1, DORM_Z1 - 1, "home_sofa_blue_m[facing=north]");
        set(x0 + 4, y + 1, DORM_Z1 - 1, "home_sofa_blue_r[facing=north]");
        set(x0 + 3, y + 1, DORM_Z1 - 4, f % 2 == 0 ? "pk_tv_crt[facing=south]" : "sc_cafe_table[facing=north]");
        set(x1, y + 1, CS + 2, "store_vending[facing=west]");
        set(x1, y + 2, CS + 2, "store_vending_top[facing=west]");
        set(LIFT_X, y + 1, DORM_Z1 - 1, "un_beanbag[facing=" + u.dir() + "]");
        u.sign("lounge", x0 + 1, y + 3, CS - 1, "north");
        u.mess(LIFT_X - 1, CS + 1, x1, DORM_Z1 - 1, y, 0.04);
    }

    /** The roof: the water tank, the vents, the sheets with HELP on them hung over the parapet. */
    private void roof() {
        for (int x = 166; x <= 168; x++) {
            for (int z = 16; z <= 18; z++) {
                set(x, ROOF + 1, z, "steel_girder");
                set(x, ROOF + 2, z, x == 167 && z == 17 ? "water_tank" : "air");
            }
        }
        set(167, ROOF + 3, 17, "water_tank_top");
        for (int x = 190; x <= 194; x += 2) {
            set(x, ROOF + 1, 28, "hvac");
        }
        for (int x = 160; x <= 196; x += 9) {
            for (int y = ROOF - 3; y < ROOF; y++) {
                set(x, y, DORM_Z0 - 1, "gl_wall_tarp[facing=north]");
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = DORM_X0 + 3 + u.rng.nextInt(DORM_X1 - DORM_X0 - 6), z = DORM_Z0 + 2 + u.rng.nextInt(DORM_Z1 - DORM_Z0 - 4);
            if (u.empty(x, ROOF + 1, z)) {
                set(x, ROOF + 1, z, u.any("trash_bag[facing=north]", "sc_bucket", "home_books_stack_low", "sc_cases_water[facing=north]"));
            }
        }
    }
}
