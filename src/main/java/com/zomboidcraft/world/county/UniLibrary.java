package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.University.LIB_X0;
import static com.zomboidcraft.world.county.University.LIB_X1;
import static com.zomboidcraft.world.county.University.LIB_Z0;
import static com.zomboidcraft.world.county.University.LIB_Z1;
import static com.zomboidcraft.world.county.University.S;

/**
 * The Halloran Library, down the quad's west side: five floors of board-marked concrete, the four
 * upper ones run out two blocks over the glazed ground floor on their columns, slot windows in
 * rows, HALLORAN LIBRARY along the fascia. The ground floor has the circulation desk facing the
 * doors, the card catalogue, the periodicals and the microfiche; the three floors over it are the
 * stacks with the study carrels along the windows and the reading tables down the middle aisle;
 * the top floor the reading room, special collections and the librarians' offices. The fire
 * stairs at either end, the lift by the south one.
 *
 * <p>Students hid in the stacks on the third floor and pushed the shelves over across the stairs;
 * something got in anyway. THEY'RE IN THE STACKS is sprayed by the north stair's door.
 */
final class UniLibrary {
    private final University u;
    /** The ground floor's glazed east wall, set back under the upper floors' overhang. */
    private static final int GX1 = LIB_X1 - 3;
    private static final int[] YS = {0, S, 2 * S, 3 * S, 4 * S, 5 * S};
    private static final int ROOF = 5 * S;
    /** The stairs' shafts (x0, z0) at the north and south ends, the lift by the south one. */
    private static final int SN_Z = LIB_Z0 + 2, SS_Z = LIB_Z1 - 11, ST_X = LIB_X0 + 2;
    private static final int LIFT_X = LIB_X0 + 9, LIFT_Z = LIB_Z1 - 9;

    UniLibrary(University u) {
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
        ground();
        for (int f = 1; f <= 3; f++) {
            stacks(f * S, f == 2);
        }
        top(4 * S);
        u.stair(ST_X, SN_Z, true, YS, "un_brut", "un_roof");
        u.stair(ST_X, SS_Z, false, YS, "un_brut", "un_roof");
        u.lift(LIFT_X, LIFT_Z, 3, 4, YS, "un_brut", ROOF + 2);
        for (int y = 0; y < ROOF; y += S) {
            u.sign("stacks", LIFT_X + 1, y + 4, LIFT_Z - 2, "north");
        }
        roof();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        // the floors, the roof and its parapet
        for (int x = LIB_X0; x <= LIB_X1; x++) {
            for (int z = LIB_Z0; z <= LIB_Z1; z++) {
                if (x <= GX1) {
                    set(x, 0, z, chance() < 0.2 ? "un_carpet_b" : "un_carpet");
                }
                for (int f = 1; f < 5; f++) {
                    set(x, f * S, z, chance() < 0.15 ? "un_slab_carpet_b" : "un_slab_carpet");
                }
                set(x, ROOF, z, "un_roof");
            }
        }
        // the ground floor: concrete to the north, west and south, glass to the east under the overhang
        for (int y = 1; y < S; y++) {
            for (int z = LIB_Z0; z <= LIB_Z1; z++) {
                set(LIB_X0, y, z, wallAt(LIB_X0, y, z, "west"));
                boolean pier = (z - LIB_Z0) % 6 == 0;
                set(GX1, y, z, pier || y == S - 1 ? "un_brut" : y == 1 ? "glass_spandrel" : u.b.glass("z"));
            }
            for (int x = LIB_X0; x <= GX1; x++) {
                set(x, y, LIB_Z0, wallAt(x, y, LIB_Z0, "north"));
                set(x, y, LIB_Z1, wallAt(x, y, LIB_Z1, "south"));
            }
        }
        // the columns under the overhang
        for (int z = LIB_Z0 + 2; z < LIB_Z1; z += 8) {
            for (int y = 1; y < S; y++) {
                set(LIB_X1 - 1, y, z, "un_brut_rib");
            }
        }
        // the upper floors: slot windows in the concrete, a ribbed fascia over the top
        for (int y = S + 1; y <= ROOF + 1; y++) {
            for (int z = LIB_Z0; z <= LIB_Z1; z++) {
                set(LIB_X0, y, z, wallAt(LIB_X0, y, z, "west"));
                set(LIB_X1, y, z, wallAt(LIB_X1, y, z, "east"));
            }
            for (int x = LIB_X0; x <= LIB_X1; x++) {
                set(x, y, LIB_Z0, wallAt(x, y, LIB_Z0, "north"));
                set(x, y, LIB_Z1, wallAt(x, y, LIB_Z1, "south"));
            }
        }
        // the main doors: two pairs in the glass, a canopy of the overhang over them
        for (int z : new int[] {87, 89}) {
            u.doors(GX1, 0, z, "shop_door", "east");
            set(GX1, 3, z, "un_brut");
            set(GX1, 3, z + 1, "un_brut");
        }
        // the name along the fascia over the doors, and the book return by them
        u.row("un_name_library_", 12, LIB_X1 + 1, ROOF - 1, 83, "east", 0.05);
        set(LIB_X1 - 1, 1, 84, "cv_mailbox[facing=east]");
        u.set(GX1 + 1, 1, 93, "un_notice[facing=east]");
    }

    /** The outer wall at (x, y, z): concrete, a slot window every third block on the upper floors' middle rows. */
    private String wallAt(int x, int y, int z, String out) {
        int k = y % S;
        int a = out.equals("north") || out.equals("south") ? x : z;
        boolean corner = (x == LIB_X0 || x == LIB_X1 || x == GX1) && (z == LIB_Z0 || z == LIB_Z1);
        if (y > ROOF - 2) {
            return "un_brut_rib";
        }
        if (!corner && k >= 1 && k <= 3 && a % 3 == 0 && y < ROOF) {
            if (y < S && !out.equals("west")) {
                return "un_brut";
            }
            return (chance() < u.b.damage * 0.25 ? "un_slot_broken" : "un_slot") + "[facing=" + out + "]";
        }
        return k == 0 ? "un_brut_rib" : chance() < 0.3 ? "un_brut_b" : "un_brut";
    }

    // ------------------------------------------------------------------ the floors

    /** Circulation, the catalogue, periodicals, microfiche, the restrooms; the reference desk. */
    private void ground() {
        int y = 0;
        // the circulation desk, a U open to the stacks behind it, facing the doors
        for (int z = 80; z <= 97; z++) {
            set(46, y + 1, z, (z == 80 || z == 97 ? "un_circ_end" : "un_circ") + "[facing=east]");
        }
        for (int x = 42; x <= 45; x++) {
            set(x, y + 1, 80, "un_circ[facing=north]");
            set(x, y + 1, 97, "un_circ[facing=south]");
        }
        set(44, y + 1, 85, "office_chair[facing=east]");
        set(44, y + 1, 92, "office_chair_fallen[facing=" + u.dir() + "]");
        set(41, y + 1, 88, "pk_metal_rack[facing=east]");
        set(41, y + 1, 89, "cardboard_box[facing=east]");
        u.sign("circ", 48, y + 4, 88, "east");
        u.sign("quiet", 48, y + 4, 99, "east");
        // the card catalogue in rows north of the desk
        for (int z = 60; z <= 76; z += 4) {
            for (int x = 24; x <= 40; x++) {
                if (x == 32) {
                    continue;
                }
                set(x, y + 1, z, "un_catalog[facing=" + (z % 8 == 0 ? "north" : "south") + "]");
                if (chance() < 0.15) {
                    set(x, y + 1, z + (z % 8 == 0 ? -1 : 1), "paper");
                }
            }
        }
        // periodicals: the racks along the north wall, tables before them
        for (int x = 18; x <= 52; x++) {
            if (x < 22 && x > 16) {
                continue;
            }
            set(x, y + 1, LIB_Z0 + 1, "un_magrack[facing=south]");
        }
        u.sign("periodicals", 34, y + 4, LIB_Z0 + 1, "south");
        // the microfiche readers and the reference desk to the south
        for (int z = 102; z <= 108; z += 3) {
            for (int x = 24; x <= 38; x += 2) {
                set(x, y + 1, z, "un_microfiche[facing=north]");
                set(x, y + 1, z - 1, chance() < 0.2 ? "office_chair_fallen[facing=" + u.dir() + "]" : "office_chair[facing=south]");
            }
        }
        for (int x = 40; x <= 46; x++) {
            set(x, y + 1, 112, "un_circ[facing=north]");
        }
        u.sign("reference", 42, y + 4, 113, "north");
        set(43, y + 1, 113, "office_chair[facing=north]");
        // reading tables between
        for (int x = 26; x <= 38; x += 4) {
            for (int z = 82; z <= 94; z += 4) {
                set(x, y + 1, z, "un_table[facing=north]");
                set(x + 1, y + 1, z, chance() < 0.4 ? "un_table_lamp" + (u.power && chance() < 0.3 ? "_on" : "") + "[facing=north]" : "un_table[facing=north]");
                set(x, y + 1, z - 1, chance() < 0.2 ? "home_chair_tipped[facing=" + u.dir() + "]" : "home_chair[facing=south]");
                set(x + 1, y + 1, z + 1, "home_chair[facing=north]");
            }
        }
        restrooms(y);
        u.lamps(LIB_X0, LIB_Z0, GX1, LIB_Z1, S, 0.35);
        u.mess(LIB_X0 + 1, LIB_Z0 + 1, GX1 - 1, LIB_Z1 - 1, y, 0.03);
    }

    /** The restrooms against the west wall between the stairs: two rooms, their doors to the floor. */
    private void restrooms(int y) {
        int x0 = LIB_X0 + 1, x1 = LIB_X0 + 8;
        u.partX(x0, x1, 84, y, S, "sc_cmu_dado_a", "sc_cmu");
        u.partX(x0, x1, 90, y, S, "sc_cmu_dado_a", "sc_cmu");
        u.partX(x0, x1, 96, y, S, "sc_cmu_dado_a", "sc_cmu");
        u.partZ(x1, 84, 96, y, S, "sc_cmu_dado_a", "sc_cmu");
        u.door(x1, y, 87, "flat_door", "east");
        u.door(x1, y, 93, "flat_door", "east");
        for (int[] r : new int[][] {{85, 89}, {91, 95}}) {
            for (int z = r[0]; z <= r[1]; z++) {
                set(x0, y, z, "bunk_tile");
                set(x0 + 1, y, z, "bunk_tile");
                if (z % 2 == 0) {
                    set(x0, y + 1, z, "home_toilet[facing=east]");
                } else {
                    set(x0 + 4, y + 1, z, "hs_sink[facing=west]");
                }
            }
        }
    }

    /**
     * A floor of stacks: the shelves in rows across the building with a walk down the middle, the
     * carrels along the windows, reading tables by the doors' end. fallen: the third floor, the
     * shelves pushed over, the barricade on the stairs, the dead.
     */
    private void stacks(int y, boolean fallen) {
        for (int z = LIB_Z0 + 14; z <= LIB_Z1 - 14; z += 3) {
            if (z >= 84 && z <= 94) {
                continue;
            }
            for (int x = 22; x <= 50; x++) {
                if (x == 36 || x == 37) {
                    continue;
                }
                if (fallen && chance() < 0.35) {
                    set(x, y + 1, z, chance() < 0.5 ? "home_books_stack_low" : "paper");
                    if (chance() < 0.4) {
                        set(x, y + 1, z + 1, "home_books_stack_low");
                    }
                    continue;
                }
                set(x, y + 1, z, "cv_stack[axis=x]");
                set(x, y + 2, z, "cv_stack[axis=x]");
            }
        }
        // carrels along the east windows and the west wall's middle
        for (int z = LIB_Z0 + 3; z <= LIB_Z1 - 3; z++) {
            if (z % 2 == 0 && u.empty(LIB_X1 - 1, y + 1, z)) {
                set(LIB_X1 - 1, y + 1, z, "un_carrel[facing=west]");
            }
            if (z >= 72 && z <= 108 && z % 2 == 0 && u.empty(LIB_X0 + 1, y + 1, z)) {
                set(LIB_X0 + 1, y + 1, z, "un_carrel[facing=east]");
            }
        }
        // the tables down the middle walk
        for (int x = 24; x <= 48; x += 6) {
            set(x, y + 1, 88, "un_table[facing=north]");
            set(x + 1, y + 1, 88, "un_table_lamp" + (u.power && chance() < 0.2 ? "_on" : "") + "[facing=north]");
            set(x, y + 1, 87, "home_chair[facing=south]");
            set(x + 1, y + 1, 89, chance() < 0.2 ? "home_chair_tipped[facing=" + u.dir() + "]" : "home_chair[facing=north]");
        }
        u.lamps(LIB_X0, LIB_Z0, LIB_X1, LIB_Z1, y + S, fallen ? 0.1 : 0.3);
        if (fallen) {
            // the barricade inside the north stair's door, the spray beside it, the ones who didn't make it
            int dz = SN_Z + 10;
            for (int x = ST_X; x <= ST_X + 6; x++) {
                if (x != ST_X + 2 && chance() < 0.8) {
                    set(x, y + 1, dz + 1, u.any("un_table[facing=north]", "home_chair_tipped[facing=east]", "cv_stack[axis=x]"));
                }
            }
            u.row("un_spray_stacks_", 3, ST_X + 5, y + 2, dz, "south", 0);
            for (int i = 0; i < 6; i++) {
                int x = 22 + u.rng.nextInt(28), z = LIB_Z0 + 14 + u.rng.nextInt(60);
                if (u.empty(x, y + 1, z)) {
                    set(x, y + 1, z, chance() < 0.5 ? "home_blood_pool" : "st_stretcher_bag[facing=" + u.dir() + "]");
                }
            }
        }
        u.mess(LIB_X0 + 1, LIB_Z0 + 1, LIB_X1 - 1, LIB_Z1 - 1, y, fallen ? 0.08 : 0.03);
    }

    /**
     * The top floor: the reading room down the east side under its lamps, special collections
     * behind glass to the north, the librarians' offices along the west, group study rooms south.
     */
    private void top(int y) {
        // the reading room: long tables, a lamp at every other seat
        for (int z = 64; z <= 112; z += 4) {
            for (int x = 40; x <= 52; x++) {
                set(x, y + 1, z, x % 2 == 0 ? "un_table_lamp" + (u.power && chance() < 0.15 ? "_on" : "") + "[facing=north]" : "un_table[facing=north]");
                if (chance() < 0.7) {
                    set(x, y + 1, z - 1, chance() < 0.15 ? "home_chair_tipped[facing=" + u.dir() + "]" : "home_chair[facing=south]");
                }
            }
        }
        // special collections: an oak-panelled room behind a glass wall, its cases and its globe
        int scz = LIB_Z0 + 12;
        for (int x = 18; x <= 34; x++) {
            set(x, y + 1, scz, "glass_spandrel");
            for (int yy = y + 2; yy < y + S; yy++) {
                set(x, yy, scz, "tx_glass[axis=x]");
            }
        }
        u.partZ(18, LIB_Z0 + 1, scz, y, S, "cv_panel_oak", "cv_panel_oak");
        u.partZ(34, LIB_Z0 + 1, scz, y, S, "cv_panel_oak", "cv_panel_oak");
        u.door(26, y, scz, "shop_door", "south");
        u.opening(26, 26, scz, scz, y, false);
        u.door(26, y, scz, "shop_door", "south");
        for (int x = 19; x <= 33; x++) {
            set(x, y + 1, LIB_Z0 + 1, "cv_stack[axis=x]");
            set(x, y + 2, LIB_Z0 + 1, "cv_stack[axis=x]");
        }
        set(22, y + 1, LIB_Z0 + 6, "cv_globe_stand");
        set(28, y + 1, LIB_Z0 + 6, "un_table_lamp[facing=north]");
        set(29, y + 1, LIB_Z0 + 6, "un_table[facing=north]");
        // the offices along the west wall between the stairs
        for (int z0 = 70; z0 <= 100; z0 += 8) {
            u.partX(LIB_X0 + 1, LIB_X0 + 10, z0, y, S, "drywall", "drywall");
            u.partZ(LIB_X0 + 10, z0, z0 + 8, y, S, "drywall", "drywall");
            u.door(LIB_X0 + 10, y, z0 + 4, "flat_door", "east");
            set(LIB_X0 + 3, y + 1, z0 + 4, "home_desk[facing=east]");
            set(LIB_X0 + 2, y + 1, z0 + 4, "office_chair[facing=east]");
            set(LIB_X0 + 1, y + 1, z0 + 2, "filing_cabinet[facing=east]");
            set(LIB_X0 + 1, y + 1, z0 + 6, "home_bookcase[facing=east]");
            set(LIB_X0 + 1, y + 2, z0 + 6, "home_bookcase_top[facing=east]");
        }
        u.partX(LIB_X0 + 1, LIB_X0 + 10, 108, y, S, "drywall", "drywall");
        // group study rooms in the south-east corner
        for (int x0 = 24; x0 <= 32; x0 += 8) {
            u.partZ(x0, 114, LIB_Z1 - 1, y, S, "drywall", "drywall");
            u.partX(x0, x0 + 8, 114, y, S, "drywall", "drywall");
            u.door(x0 + 4, y, 114, "flat_door", "north");
            set(x0 + 3, y + 1, 118, "un_table[facing=north]");
            set(x0 + 4, y + 1, 118, "un_table[facing=north]");
            set(x0 + 5, y + 1, 118, "un_table[facing=north]");
            set(x0 + 4, y + 2, LIB_Z1 - 1, "un_board_calc_" + (2 + u.rng.nextInt(2)) + "[facing=north]");
        }
        u.partZ(40, 114, LIB_Z1 - 1, y, S, "drywall", "drywall");
        u.lamps(LIB_X0, LIB_Z0, LIB_X1, LIB_Z1, ROOF, 0.3);
        u.mess(LIB_X0 + 1, LIB_Z0 + 1, LIB_X1 - 1, LIB_Z1 - 1, y, 0.025);
    }

    /** The roof: its gravel, the air handlers, a satellite dish no one used. */
    private void roof() {
        for (int x = 26; x <= 46; x += 5) {
            for (int z = 70; z <= 100; z += 10) {
                set(x, ROOF + 1, z, "hvac");
                set(x + 1, ROOF + 1, z, "hvac");
            }
        }
        for (int i = 0; i < 12; i++) {
            int x = LIB_X0 + 2 + u.rng.nextInt(LIB_X1 - LIB_X0 - 4), z = LIB_Z0 + 2 + u.rng.nextInt(LIB_Z1 - LIB_Z0 - 4);
            if (u.empty(x, ROOF + 1, z)) {
                set(x, ROOF + 1, z, u.any("paper", "trash_bag[facing=" + u.dir() + "]", "debris_chunk"));
            }
        }
    }
}
