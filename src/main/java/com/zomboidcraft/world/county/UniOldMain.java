package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.University.OM_X0;
import static com.zomboidcraft.world.county.University.OM_X1;
import static com.zomboidcraft.world.county.University.OM_Z0;
import static com.zomboidcraft.world.county.University.OM_Z1;

/**
 * Old Main, closing the quad's south end: the university's first building, three tall floors of
 * red brick on a stone base, arched windows with stone keystones, a cornice and balustrade round
 * the flat roof, the portico of six stone columns two floors high with OLD MAIN in the frieze and
 * the seal in the pediment, and the clock tower over the middle with its copper dome.
 *
 * <p>Inside, a corridor runs end to end on every floor between the fire stairs; off it on the
 * ground floor the entrance hall, admissions, the registrar, the bursar and the Great Hall; over
 * them the president's suite, the board room and the deans; at the top the old classrooms and
 * the history department. The Guard ran the campus from the top floor: the map table, the radios,
 * the cots, the sandbags in the windows over the quad.
 */
final class UniOldMain {
    private final University u;
    /** The ground floor stands a step up on the stone base; the floors six apart. */
    static final int F0 = 1, OS = 6, ROOF = F0 + 3 * OS;
    private static final int[] YS = {F0, F0 + OS, F0 + 2 * OS, ROOF};
    /** The corridor's walls; north rooms between the front wall and CN, south rooms between CS and the back. */
    private static final int CN = 184, CS = 190;
    /** The stairs at the corridor's ends, their doors on it. */
    private static final int SW_X = 68, SE_X = OM_X1 - 6, ST_Z = CS;
    /** The portico in front of the middle: its columns, its steps. */
    private static final int PX0 = 94, PX1 = 118, PZ = 164;
    /** The clock tower's corner on the roof (a block corner, as the courthouse's). */
    private static final int TCX = 106, TCZ = 187;

    private enum Room { HALL, ADMISSIONS, REGISTRAR, BURSAR, GREAT, RECORDS, PRESIDENT, BOARD, OFFICES, LOUNGE, CLASS, COMMAND }

    /** Each floor's rooms, west to east, the north side's then the south side's, with their x ranges (walls between). */
    private static final int[] NX = {66, 81, 95, 117, 131, 146};
    private static final int[] SXS = {74, 95, 117, 139};
    private static final Room[][] NORTH = {
            {Room.ADMISSIONS, Room.REGISTRAR, Room.HALL, Room.BURSAR, Room.OFFICES},
            {Room.OFFICES, Room.BOARD, Room.PRESIDENT, Room.OFFICES, Room.OFFICES},
            {Room.CLASS, Room.CLASS, Room.COMMAND, Room.OFFICES, Room.CLASS},
    };
    private static final Room[][] SOUTH = {
            {Room.RECORDS, Room.GREAT, Room.LOUNGE},
            {Room.RECORDS, Room.OFFICES, Room.LOUNGE},
            {Room.OFFICES, Room.CLASS, Room.RECORDS},
    };

    UniOldMain(University u) {
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
        for (int f = 0; f < 3; f++) {
            floor(f);
        }
        // the corners behind the stairs built solid
        u.fill(OM_X0 + 1, F0 + 1, CS + 1, SXS[0] - 1, ROOF - 1, OM_Z1 - 1, "brick_red");
        u.fill(SXS[SXS.length - 1] + 1, F0 + 1, CS + 1, OM_X1 - 1, ROOF - 1, OM_Z1 - 1, "brick_red");
        u.stair(SW_X, ST_Z, false, YS, "brick_red", "un_roof");
        u.stair(SE_X, ST_Z, false, YS, "brick_red", "un_roof");
        portico();
        tower();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int x = OM_X0; x <= OM_X1; x++) {
            for (int z = OM_Z0; z <= OM_Z1; z++) {
                set(x, 0, z, "stone_trim");
                set(x, F0, z, chance() < 0.1 ? "ch_floor_oak" : "floor_boards");
                set(x, F0 + OS, z, "un_slab_boards");
                set(x, F0 + 2 * OS, z, "un_slab_boards");
                set(x, ROOF, z, "un_roof");
            }
        }
        for (int y = 1; y <= ROOF; y++) {
            for (int z = OM_Z0; z <= OM_Z1; z++) {
                set(OM_X0, y, z, wallAt(y));
                set(OM_X1, y, z, wallAt(y));
            }
            for (int x = OM_X0; x <= OM_X1; x++) {
                set(x, y, OM_Z0, wallAt(y));
                set(x, y, OM_Z1, wallAt(y));
            }
        }
        // the water table round the base, the arched windows, the cornice and the balustrade
        for (int x = OM_X0 - 1; x <= OM_X1 + 1; x++) {
            set(x, 1, OM_Z0 - 1, "cv_water_table[facing=north]");
            set(x, 1, OM_Z1 + 1, "cv_water_table[facing=south]");
            set(x, ROOF, OM_Z0 - 1, "cv_cornice_stone[facing=north]");
            set(x, ROOF, OM_Z1 + 1, "cv_cornice_stone[facing=south]");
        }
        for (int z = OM_Z0; z <= OM_Z1; z++) {
            set(OM_X0 - 1, 1, z, "cv_water_table[facing=west]");
            set(OM_X1 + 1, 1, z, "cv_water_table[facing=east]");
            set(OM_X0 - 1, ROOF, z, "cv_cornice_stone[facing=west]");
            set(OM_X1 + 1, ROOF, z, "cv_cornice_stone[facing=east]");
        }
        for (int f = 0; f < 3; f++) {
            int y = F0 + f * OS;
            for (int x = OM_X0 + 3; x <= OM_X1 - 3; x += 3) {
                boolean door = f == 0 && (Math.abs(x - 98.5) < 2 || Math.abs(x - 105.5) < 2 || Math.abs(x - 113.5) < 2);
                boolean pediment = f == 2 && x >= PX0 - 1 && x <= PX1 + 1;
                if (!door && !pediment) {
                    window(x, y, OM_Z0, "north");
                }
                if (Math.abs(x - 106) > 1 && !(x >= SW_X - 1 && x <= SW_X + 5) && !(x >= SE_X - 1 && x <= SE_X + 5)) {
                    window(x, y, OM_Z1, "south");
                }
            }
            for (int z = OM_Z0 + 3; z < CS; z += 4) {
                window(OM_X0, y, z, "west");
                window(OM_X1, y, z, "east");
            }
        }
        for (int x = OM_X0; x <= OM_X1; x++) {
            set(x, ROOF + 1, OM_Z0, "cv_balustrade_stone[axis=x]");
            set(x, ROOF + 1, OM_Z1, "cv_balustrade_stone[axis=x]");
        }
        for (int z = OM_Z0; z <= OM_Z1; z++) {
            set(OM_X0, ROOF + 1, z, "cv_balustrade_stone[axis=z]");
            set(OM_X1, ROOF + 1, z, "cv_balustrade_stone[axis=z]");
        }
        for (int x = OM_X0; x <= OM_X1; x += 8) {
            set(x, ROOF + 1, OM_Z0, "cv_pier_stone");
            set(x, ROOF + 1, OM_Z1, "cv_pier_stone");
        }
        // the front doors under the portico, the back doors on the service walk with a step down
        u.doors(105, F0, OM_Z0, "shop_door", "north");
        u.doors(98, F0, OM_Z0, "shop_door", "north");
        u.doors(113, F0, OM_Z0, "shop_door", "north");
        u.doors(105, F0, OM_Z1, "flat_door", "south");
        set(105, 1, OM_Z1 + 1, "cv_step_stone[facing=north]");
        set(106, 1, OM_Z1 + 1, "cv_step_stone[facing=north]");
        for (int x : new int[] {104, 107}) {
            set(x, F0 + 3, OM_Z1 + 1, "fit_globe" + (u.power ? "_on" : "") + "[facing=south]");
        }
    }

    private String wallAt(int y) {
        int k = (y - F0) % OS;
        if (y <= F0 || y == ROOF) {
            return chance() < 0.2 ? "stone_trim_b" : "stone_trim";
        }
        if (k == 0) {
            return "stone_trim";
        }
        return u.b.wall("brick_red", y);
    }

    /** A tall arched window in a wall facing out: three panes, the brick arch with its keystone, the sill. */
    private void window(int x, int y, int z, String out) {
        int[] o = Bldg.step(out);
        String a = Bldg.along(out);
        String g = u.b.glass(a);
        for (int k = 2; k <= 4; k++) {
            set(x, y + k, z, g.startsWith("boarded") ? g : u.b.glass(a).replace("boarded_window", "window"));
        }
        set(x, y + 5, z, "cv_arch_red_1[facing=" + out + "]");
        set(x + o[0], y + 5, z + o[1], "cv_keystone[facing=" + out + "]");
        set(x + o[0], y + 1, z + o[1], "sill[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the floors

    private void floor(int f) {
        int y = F0 + f * OS;
        // the corridor walls, the oak wainscot and the plaster; the runner down the middle
        u.partX(OM_X0 + 1, OM_X1 - 1, CN, y, OS, "cv_wainscot", "cv_plaster");
        u.partX(OM_X0 + 1, OM_X1 - 1, CS, y, OS, "cv_wainscot", "cv_plaster");
        for (int x = OM_X0 + 1; x < OM_X1; x++) {
            for (int z = CN + 1; z < CS; z++) {
                set(x, y, z, f == 0 ? "cv_terrazzo" : "un_slab_terrazzo");
            }
            if (chance() < 0.85) {
                set(x, y + 1, CN + 3, "cv_carpet_red");
            }
        }
        for (int i = 0; i + 1 < NX.length; i++) {
            if (i > 0) {
                u.partZ(NX[i], OM_Z0 + 1, CN - 1, y, OS, "cv_wainscot", "cv_plaster");
            }
            Room r = NORTH[f][i];
            int x0 = NX[i] + 1, x1 = NX[i + 1] - 1, dx = (x0 + x1) / 2;
            if (r == Room.HALL) {
                u.opening(x0 + 4, x1 - 4, CN, CN, y, true);
            } else {
                u.door(dx, y, CN, "flat_door", "south");
            }
            room(r, x0, OM_Z0 + 1, x1, CN - 1, y, true, f);
        }
        for (int i = 0; i + 1 < SXS.length; i++) {
            u.partZ(SXS[i], CS + 1, OM_Z1 - 1, y, OS, "cv_wainscot", "cv_plaster");
            u.partZ(SXS[i + 1], CS + 1, OM_Z1 - 1, y, OS, "cv_wainscot", "cv_plaster");
            Room r = SOUTH[f][i];
            int x0 = SXS[i] + 1, x1 = SXS[i + 1] - 1;
            u.door((x0 + x1) / 2 + (r == Room.GREAT ? -3 : 0), y, CS, "flat_door", "north");
            if (r == Room.GREAT) {
                u.door((x0 + x1) / 2 + 4, y, CS, "flat_door", "north");
            }
            room(r, x0, CS + 1, x1, OM_Z1 - 1, y, false, f);
        }
        // the back hall down to the back doors between the Great Hall's wall and the stairs
        if (f == 0) {
            u.opening(104, 107, CS, CS, y, true);
        }
        // the corridor's furniture: portraits, benches, a case of trophies, the clock
        for (int x = OM_X0 + 8; x < OM_X1 - 8; x += 12) {
            set(x, y + 3, CN + 1, (chance() < 0.5 ? "cv_portrait" : "cv_portrait_b") + "[facing=south]");
            set(x + 5, y + 1, CS - 1, "cv_hall_bench[facing=north]");
        }
        set(106, y + 4, CS - 1, "bk_wall_clock[facing=north]");
        u.lamps(OM_X0, OM_Z0, OM_X1, OM_Z1, y + OS, 0.25);
        for (int x = OM_X0 + 6; x < OM_X1; x += 10) {
            set(x, y + OS - 1, CN + 3, "ch_pendant" + (u.power && chance() < 0.4 ? "_lit" : ""));
        }
        u.mess(OM_X0 + 1, CN + 1, OM_X1 - 1, CS - 1, y, f == 2 ? 0.07 : 0.04);
    }

    private void room(Room r, int x0, int z0, int x1, int z1, int y, boolean north, int f) {
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        String toDoor = north ? "south" : "north", away = north ? "north" : "south";
        int wallZ = north ? z0 : z1, doorZ = north ? z1 : z0, signZ = north ? CN + 1 : CS - 1;
        switch (r) {
            case HALL -> {
                // the entrance hall: the mosaic floor with the seal, the directory, the founders' portraits, the flags
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, "cv_hex_tile");
                    }
                }
                u.picture("un_seal_", 3, 3, cx - 1, y + 1, cz - 1, "north");
                for (int x = cx - 1; x <= cx + 1; x++) {
                    for (int yy = y + 1; yy <= y + 3; yy++) {
                        // the seal's three by three lies on a plinth's face, set back on the floor
                        set(x, yy, cz, "stone_trim");
                    }
                }
                set(x0, y + 1, z1 - 2, "cv_directory[facing=east]");
                set(x0, y + 3, cz, "cv_portrait[facing=east]");
                set(x1, y + 3, cz, "cv_portrait_b[facing=west]");
                set(x0 + 1, y + 1, z0 + 2, "cv_flag_us[facing=east]");
                set(x0 + 1, y + 2, z0 + 2, "cv_flag_us_top[facing=east]");
                set(x1 - 1, y + 1, z0 + 2, "cv_flag_state[facing=west]");
                set(x1 - 1, y + 2, z0 + 2, "cv_flag_state_top[facing=west]");
                u.sign("admissions", x0, y + 4, z0 + 5, "east");
                u.sign("bursar", x1, y + 4, z0 + 5, "west");
                set(cx, y + OS - 1, cz + 3, "ch_chandelier" + (u.power ? "_lit" : ""));
                // the Guard's checkpoint inside the doors: the table, the sandbags, the sign-out sheets
                set(cx - 4, y + 1, z0 + 3, "sc_cafe_table[facing=north]");
                set(cx - 3, y + 1, z0 + 3, "sc_cafe_table[facing=north]");
                set(cx - 3, y + 2, z0 + 3, "paper");
                for (int x = cx + 3; x <= cx + 6; x++) {
                    set(x, y + 1, z0 + 2, "sandbags[facing=north]");
                }
            }
            case ADMISSIONS, REGISTRAR, BURSAR -> {
                // the counter across the room, the waiting chairs before it, the clerks' desks behind
                int counterZ = north ? cz - 1 : cz + 1;
                for (int x = x0; x <= x1; x++) {
                    if (x == x1 - 1) {
                        continue;
                    }
                    set(x, y + 1, counterZ, r == Room.BURSAR ? "store_teller_glass[axis=x]" : "un_circ[facing=" + toDoor + "]");
                }
                set(x1 - 1, y + 1, counterZ, "air");
                for (int x = x0 + 1; x <= x1 - 2; x += 3) {
                    set(x, y + 1, north ? z0 + 2 : z1 - 2, "home_desk[facing=" + toDoor + "]");
                    set(x, y + 1, north ? z0 + 1 : z1 - 1, "office_chair[facing=" + toDoor + "]");
                    set(x, y + 1, doorZ + (north ? -3 : 3), "hs_chairs[facing=" + away + "]");
                }
                for (int z = z0; z <= z1; z += 2) {
                    if (Math.abs(z - counterZ) > 1) {
                        set(x0, y + 1, z, "filing_cabinet[facing=east]");
                    }
                }
                if (r == Room.BURSAR) {
                    set(x1, y + 1, wallZ, "store_safe[facing=" + toDoor + "]");
                }
                u.sign(r == Room.ADMISSIONS ? "admissions" : r == Room.REGISTRAR ? "registrar" : "bursar", cx, y + 4, signZ, toDoor);
            }
            case GREAT -> {
                // the Great Hall: the long tables, the portraits of the presidents, the chandeliers, the dais
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, "ch_floor_oak");
                    }
                }
                for (int x = x0 + 3; x <= x1 - 3; x++) {
                    if (x >= 103 && x <= 108) {
                        continue;
                    }
                    for (int z : new int[] {cz - 3, cz + 2}) {
                        set(x, y + 1, z, "cv_table[axis=x]");
                        set(x, y + 1, z - 1, chance() < 0.15 ? "home_chair_tipped[facing=" + u.dir() + "]" : "home_chair[facing=south]");
                        set(x, y + 1, z + 1, "home_chair[facing=north]");
                    }
                }
                for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                    set(x0, y + 3, z, "cv_portrait[facing=east]");
                    set(x1, y + 3, z, "cv_portrait_b[facing=west]");
                }
                set(cx - 4, y + OS - 1, cz, "ch_chandelier" + (u.power ? "_lit" : ""));
                set(cx + 4, y + OS - 1, cz, "ch_chandelier");
                u.sign("great", cx - 1, y + 4, CS - 1, "north");
            }
            case RECORDS -> {
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    if ((x - x0) % 4 == 0) {
                        continue;
                    }
                    for (int z = z0 + 2; z <= z1 - 2; z += 3) {
                        set(x, y + 1, z, "filing_cabinet[facing=" + (z % 2 == 0 ? "north" : "south") + "]");
                    }
                }
                for (int i = 0; i < 6; i++) {
                    int x = x0 + u.rng.nextInt(x1 - x0 + 1), z = z0 + u.rng.nextInt(z1 - z0 + 1);
                    if (u.empty(x, y + 1, z)) {
                        set(x, y + 1, z, "paper");
                    }
                }
            }
            case PRESIDENT -> {
                // the panelled office, the desk before the window, the flags, the sofa, the Guard's map on the desk
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        set(x, y, z, "un_slab_carpet");
                    }
                    set(x, y + 1, z0, "cv_panel_oak");
                }
                set(cx, y + 1, z0 + 3, "home_desk[facing=south]");
                set(cx + 1, y + 1, z0 + 3, "home_desk[facing=south]");
                set(cx, y + 1, z0 + 2, "office_chair[facing=south]");
                set(cx - 3, y + 1, z0 + 1, "cv_flag_us[facing=south]");
                set(cx - 3, y + 2, z0 + 1, "cv_flag_us_top[facing=south]");
                set(cx + 4, y + 1, z0 + 1, "cv_flag_state[facing=south]");
                set(cx + 4, y + 2, z0 + 1, "cv_flag_state_top[facing=south]");
                set(x0 + 1, y + 1, cz, "home_sofa_brown_l[facing=east]");
                set(x0 + 1, y + 1, cz + 1, "home_sofa_brown_m[facing=east]");
                set(x0 + 1, y + 1, cz + 2, "home_sofa_brown_r[facing=east]");
                set(x1, y + 3, cz, "cv_portrait[facing=west]");
                set(x1 - 1, y + 1, z1, "home_bookcase[facing=north]");
                set(x1 - 1, y + 2, z1, "home_bookcase_top[facing=north]");
                set(x1 - 3, y + 1, z1, "home_bookcase[facing=north]");
                set(x1 - 3, y + 2, z1, "home_bookcase_top[facing=north]");
                u.sign("president", cx, y + 4, signZ, toDoor);
            }
            case BOARD -> {
                for (int x = x0 + 3; x <= x1 - 3; x++) {
                    set(x, y + 1, cz, "cv_table[axis=x]");
                    set(x, y + 1, cz - 1, "office_chair[facing=south]");
                    set(x, y + 1, cz + 1, chance() < 0.15 ? "office_chair_fallen[facing=" + u.dir() + "]" : "office_chair[facing=north]");
                }
                set(x0, y + 3, cz, "cv_portrait_b[facing=east]");
                set(x1, y + 3, cz, "cv_portrait[facing=west]");
            }
            case OFFICES -> {
                // small offices off the corridor's side of the room, a hall along it
                int span = Math.max(5, (x1 - x0 + 1) / 3);
                int hz = north ? z1 - 2 : z0 + 2;
                u.partX(x0, x1, north ? hz - 1 : hz + 1, y, OS, "cv_wainscot", "cv_plaster");
                for (int x = x0 + span; x < x1 - 2; x += span) {
                    u.partZ(x, north ? z0 : hz + 2, north ? hz - 2 : z1, y, OS, "cv_wainscot", "cv_plaster");
                }
                for (int ox = x0; ox < x1 - 2; ox += span) {
                    int ox0 = ox == x0 ? ox : ox + 1, ox1 = ox + span >= x1 - 2 ? x1 : ox + span - 1;
                    if (ox1 - ox0 < 2) {
                        continue;
                    }
                    int mx = (ox0 + ox1) / 2;
                    u.door(mx, y, north ? hz - 1 : hz + 1, "flat_door", away);
                    int dz = north ? z0 + 2 : z1 - 2;
                    set(mx, y + 1, dz, "home_desk[facing=" + toDoor + "]");
                    set(mx, y + 1, dz + (north ? -1 : 1), "office_chair[facing=" + toDoor + "]");
                    set(ox0, y + 1, wallZ, "filing_cabinet[facing=" + toDoor + "]");
                    if (ox1 - 1 > mx) {
                        set(ox1, y + 1, wallZ, "home_bookcase[facing=" + toDoor + "]");
                        set(ox1, y + 2, wallZ, "home_bookcase_top[facing=" + toDoor + "]");
                    }
                }
            }
            case LOUNGE -> {
                for (int x = x0 + 2; x <= x1 - 4; x += 6) {
                    set(x, y + 1, cz, "home_sofa_brown_l[facing=" + toDoor + "]");
                    set(x + 1, y + 1, cz, "home_sofa_brown_m[facing=" + toDoor + "]");
                    set(x + 2, y + 1, cz, "home_sofa_brown_r[facing=" + toDoor + "]");
                    set(x + 1, y + 1, cz + (north ? 2 : -2), "sc_cafe_table[facing=north]");
                }
                set(x1, y + 1, wallZ, "dn_brewer[facing=west]");
                set(x1, y + 1, wallZ + (north ? 1 : -1), "pk_fridge[facing=west]");
                set(x0, y + 1, wallZ, "home_piano_l[facing=east]");
                u.sign("faculty", cx, y + 4, signZ, toDoor);
            }
            case CLASS -> {
                for (int x = x0 + 1; x <= x1 - 4; x += 2) {
                    for (int z = z0 + 2; z <= z1 - 2; z += 2) {
                        set(x, y + 1, z, chance() < 0.1 ? "sc_desk_b_tipped[facing=" + u.dir() + "]" : "sc_desk_b[facing=east]");
                    }
                }
                u.row("sc_chalk_lesson_", 3, x1, y + 2, cz - 1, "west", 0);
                set(x1 - 2, y + 1, cz + 3, "sc_tdesk_ped[facing=west]");
            }
            case COMMAND -> {
                // the Guard's command post: the map table, the radios, the cots, sandbags in the front windows
                set(cx, y + 1, cz, "bunk_map_table");
                set(cx + 1, y + 1, cz, "bunk_map_table");
                set(cx - 1, y + 1, cz, "bunk_chair[facing=east]");
                for (int x = x0 + 1; x <= x0 + 4; x++) {
                    set(x, y + 1, z1 - 1, "bunk_radio_desk[facing=north]");
                }
                set(x0 + 2, y + 1, z1 - 2, "bunk_chair[facing=south]");
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    if (chance() < 0.7) {
                        set(x, y + 1, z0, "sandbags[facing=north]");
                    }
                }
                for (int z = z0 + 3; z <= z1 - 3; z += 2) {
                    set(x1 - 1, y + 1, z, "sc_cot_head[facing=east]");
                    set(x1 - 2, y + 1, z, "sc_cot_foot[facing=east]");
                }
                set(x0, y + 2, cz, "bunk_map_wall[facing=east]");
                set(x0 + 1, y + 1, z0 + 2, "bunk_crate_ration[facing=north]");
                set(x0 + 2, y + 1, z0 + 2, "bunk_jerry_cans");
                set(cx + 3, y + 1, cz, "bunk_casings");
            }
        }
        // the pendant lamps hung from the ceiling
        for (int x = x0 + 3; x <= x1 - 2; x += 7) {
            set(x, y + OS - 1, cz, "ch_pendant" + (u.power && chance() < 0.3 ? "_lit" : ""));
        }
        u.mess(x0, z0, x1, z1, y, f == 2 ? 0.05 : 0.03);
    }

    // ------------------------------------------------------------------ the portico

    private void portico() {
        int top = F0 + 2 * OS + 1;
        // the stone deck a step up, the steps across its front
        for (int x = PX0; x <= PX1; x++) {
            for (int z = PZ + 1; z < OM_Z0; z++) {
                set(x, 0, z, "stone_trim");
                set(x, F0, z, chance() < 0.2 ? "stone_trim_b" : "stone_trim");
            }
            set(x, 0, PZ, "stone_trim");
            set(x, F0, PZ, "cv_step_stone[facing=south]");
        }
        // six columns, two floors high
        for (int x : new int[] {PX0 + 1, PX0 + 5, PX0 + 9, PX1 - 9, PX1 - 5, PX1 - 1}) {
            set(x, F0 + 1, PZ + 1, "cv_column_base_stone");
            for (int y = F0 + 2; y < top - 1; y++) {
                set(x, y, PZ + 1, "cv_column_stone");
            }
            set(x, top - 1, PZ + 1, "cv_column_cap_stone");
        }
        // the entablature with OLD MAIN in the frieze, the coffers behind, the cornice over
        for (int x = PX0; x <= PX1; x++) {
            for (int z = PZ; z < OM_Z0; z++) {
                set(x, top, z, (x + z) % 2 == 0 ? "stone_trim" : "stone_trim_b");
            }
            set(x, top + 1, PZ - 1, "cv_cornice_stone[facing=north]");
        }
        u.row("un_name_oldmain_", 8, 102, top, PZ - 1, "north", 0.03);
        for (int z = PZ; z < OM_Z0; z++) {
            set(PX0 - 1, top, z, "cv_cornice_stone[facing=west]");
            set(PX1 + 1, top, z, "cv_cornice_stone[facing=east]");
        }
        // the pediment: a low stone wedge stepping in two a course, the seal in the tympanum
        for (int i = 0; i < 5; i++) {
            int y = top + 1 + i;
            for (int x = PX0 + 1 + 2 * i; x <= PX1 - 1 - 2 * i; x++) {
                for (int z = PZ; z < OM_Z0; z++) {
                    set(x, y, z, z == PZ || i == 4 ? "stone_trim" : "stone_trim_b");
                }
            }
        }
        u.picture("un_seal_", 3, 3, 105, top + 2, PZ - 1, "north");
        // the lamps either side of the doors, the litter, the Guard's wire
        set(PX0 + 3, F0 + 3, OM_Z0 - 1, "fit_globe" + (u.power ? "_on" : "") + "[facing=north]");
        set(PX1 - 3, F0 + 3, OM_Z0 - 1, "fit_globe" + (u.power ? "_on" : "") + "[facing=north]");
        for (int x = PX0 + 2; x <= PX1 - 2; x++) {
            if (x < 104 || x > 108) {
                if (chance() < 0.3) {
                    set(x, F0 + 1, PZ + 3, "barbed_wire");
                }
            } else if (chance() < 0.2) {
                set(x, F0 + 1, PZ + 4, "blood_splat");
            }
        }
    }

    // ------------------------------------------------------------------ the clock tower

    /** The tower six square on the roof over the middle: the dials, the belfry, the copper dome (after the courthouse's). */
    private void tower() {
        int t = 6, tx = TCX - 3, tz = TCZ - 3, base = ROOF + 1;
        for (int y = base; y <= base + 6; y++) {
            for (int x = tx; x < tx + t; x++) {
                for (int z = tz; z < tz + t; z++) {
                    boolean edge = x == tx || x == tx + t - 1 || z == tz || z == tz + t - 1;
                    set(x, y, z, !edge ? "air" : y == base || y == base + 6 ? "stone_trim" : u.b.wall("brick_red", y));
                }
            }
        }
        // the door into the tower's foot from the roof
        u.door(TCX, ROOF, tz + t - 1, "flat_door", "south");
        String dial = u.power ? "cv_dial_lit_" : "cv_dial_";
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                int y = base + 5 - j;
                String tile = dial + (j * 4 + i);
                set(tx + 4 - i, y, tz - 1, tile + "[facing=north]");
                set(tx + 1 + i, y, tz + t, tile + "[facing=south]");
                set(tx - 1, y, tz + 1 + i, tile + "[facing=west]");
                set(tx + t, y, tz + 4 - i, tile + "[facing=east]");
            }
        }
        if (u.power) {
            for (int y = base + 3; y <= base + 4; y++) {
                for (int i = 2; i <= 3; i++) {
                    set(tx + i, y, tz, "dn_glassblock_lit");
                    set(tx + i, y, tz + t - 1, "dn_glassblock_lit");
                    set(tx, y, tz + i, "dn_glassblock_lit");
                    set(tx + t - 1, y, tz + i, "dn_glassblock_lit");
                }
            }
        }
        int bel = base + 7;
        for (int x = tx + 1; x < tx + t - 1; x++) {
            for (int z = tz + 1; z < tz + t - 1; z++) {
                set(x, bel - 1, z, "floor_boards");
            }
        }
        for (int y = bel; y < bel + 4; y++) {
            for (int x = tx; x < tx + t; x++) {
                for (int z = tz; z < tz + t; z++) {
                    boolean corner = (x == tx || x == tx + t - 1) && (z == tz || z == tz + t - 1);
                    boolean edge = x == tx || x == tx + t - 1 || z == tz || z == tz + t - 1;
                    if (edge && y == bel + 3) {
                        set(x, y, z, "stone_trim");
                    } else if (corner) {
                        set(x, y, z, "cv_column_stone");
                    } else if (edge) {
                        set(x, y, z, "cv_louvre[axis=" + (z == tz || z == tz + t - 1 ? "x" : "z") + "]");
                    } else {
                        set(x, y, z, "air");
                    }
                }
            }
        }
        set(TCX - 1, bel, TCZ - 1, "cv_bell_c");
        for (int i = 0; i < t; i++) {
            set(tx + i, bel + 3, tz - 1, "cv_cornice_stone[facing=north]");
            set(tx + i, bel + 3, tz + t, "cv_cornice_stone[facing=south]");
            set(tx - 1, bel + 3, tz + i, "cv_cornice_stone[facing=west]");
            set(tx + t, bel + 3, tz + i, "cv_cornice_stone[facing=east]");
        }
        int top = bel + 4;
        for (int s = 0; s < 2; s++) {
            int a0 = tx + s, a1 = tx + t - 1 - s, c0 = tz + s, c1 = tz + t - 1 - s;
            for (int x = a0; x <= a1; x++) {
                for (int z = c0; z <= c1; z++) {
                    boolean corner = (x == a0 || x == a1) && (z == c0 || z == c1);
                    boolean edge = x == a0 || x == a1 || z == c0 || z == c1;
                    String f = z == c0 ? "south" : z == c1 ? "north" : x == a0 ? "east" : "west";
                    set(x, top + s, z, corner ? "cv_copper_slab" : edge ? "cv_copper_step[facing=" + f + "]" : "cv_copper");
                }
            }
        }
        for (int x = TCX - 1; x <= TCX; x++) {
            for (int z = TCZ - 1; z <= TCZ; z++) {
                set(x, top + 2, z, "cv_copper");
            }
        }
        set(TCX - 1, top + 3, TCZ - 1, "cv_finial_c");
    }
}
