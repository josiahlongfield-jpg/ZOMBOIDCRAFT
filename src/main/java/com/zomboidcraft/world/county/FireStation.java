package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The fire station. There are no fire station photos in the library yet, so this follows what the
 * small-town stations of 1915 to 1960 are known for: two storeys of brick, two apparatus bays under
 * a stone band with the station's name cut in it, ENGINE tablets over the doors, the Maltese cross
 * in a raised parapet; the house door between its red lamps; the hose tower at the back corner with
 * its louvres and the siren on top; the apron out front with KEEP CLEAR on it, the call box and the
 * hydrant at the curb, the old alarm bell on its plinth, the flag.
 *
 * Inside: the apparatus floor (an engine still in most bays, one bay emptied for cots when the
 * station became an aid post), the turnout gear hung ready, air bottles, hose in rolls, the tool
 * board, the open stair at the back and the brass pole coming down onto its mat; the watch room
 * with the radio, the alarm board and the chalkboard of the last calls; the chief's office; the
 * kitchen. Upstairs: the captain's room, the bunk room with the pole hole, the showers at the stair
 * head, the TV lounge, the bathroom, the laundry.
 *
 * Drawn in its lot's frame, the apron along z = 0..Z0-1, the front wall at Z0 facing north; the
 * bays and their partition fill x 0..10, the side wing x 11 to the east wall. The floor at y = 0
 * (level with the apron), the upper floor's slab at S2, the roof at TOP.
 */
final class FireStation {
    static final int S2 = 6, TOP = 11, Z0 = 6, WX = 10;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final String brick, name, colour;
    private final boolean lit;

    FireStation(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.25 + rng.nextDouble() * 0.3;
        b.boarded = rng.nextDouble() < 0.3 ? 0.4 : 0.1;
        double r = rng.nextDouble();
        this.brick = r < 0.55 ? "brick_red" : r < 0.8 ? "brick_dark" : "brick_buff";
        this.name = rng.nextBoolean() ? "a" : "b";
        this.colour = rng.nextDouble() < 0.75 ? "red" : "white";
        this.lit = rng.nextDouble() < 0.45;
    }

    /** A station w wide (at least 17) and d deep (at least 24, the apron in the first Z0 rows). */
    void build(int w, int d) {
        int x1 = w - 1, z0 = Z0, z1 = d - 1;
        int tx0 = x1 - 3, tz0 = z1 - 3, tt = TOP + 8;
        shell(x1, z0, z1);
        front(x1, z0);
        sides(x1, z0, z1, tz0);
        b.flat(0, z0, x1, z1, TOP, brick, 2, "stone_trim");
        parapet(z0);
        boolean[] empty = bays(z0, z1);
        apparatus(z0, z1, empty);
        watchRoom(x1, z0);
        office(x1, z0);
        kitchen(x1, z0, z1, tx0, tz0);
        upstairs(x1, z0, z1, tx0, tz0);
        tower(x1, z1, tx0, tz0, tt);
        apron(x1, z0);
        rear(x1, z1, tx0);
        roof(x1, z0, z1, tx0, tz0);
        roofStair(z0);
    }

    /**
     * The stair up to the roof: two wide up the bunk room's west wall from its front end, climbing
     * toward the back, into a hut on the roof with its doors out east.
     */
    private void roofStair(int z0) {
        RoofHut.flight(p.turned(2, 0, z0 + 6, 1), rng, 0, 0, S2, TOP, brick, "stairs_concrete", "north",
                "ext_rail_edge_dark");
    }

    // ------------------------------------------------------------------ the shell

    private void shell(int x1, int z0, int z1) {
        for (int y = 0; y <= TOP; y++) {
            for (int x = 0; x <= x1; x++) {
                p.set(x, y, z0, wall(y));
                p.set(x, y, z1, wall(y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(0, y, z, wall(y));
                p.set(x1, y, z, wall(y));
            }
        }
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x, 0, z, x < WX ? "fs_floor_bay" : x == WX ? wall(0) : "home_floor_lino");
                for (int y = 1; y < TOP; y++) {
                    p.set(x, y, z, "air");
                }
                p.set(x, S2, z, x < WX ? "floor_boards" : "home_floor_oak");
            }
        }
        // the fire wall between the bays and the side wing, plaster upstairs
        for (int z = z0 + 1; z < z1; z++) {
            for (int y = 1; y < TOP; y++) {
                p.set(WX, y, z, y < S2 ? b.wall(brick, y) : "cv_plaster");
            }
        }
    }

    private String wall(int y) {
        return y == 0 ? "stone_trim" : b.wall(brick, y);
    }

    /** The bays' doors and piers, the stone band with the name, the tablets, the windows, the house door. */
    private void front(int x1, int z0) {
        // the name band over the bays (its first tile at the viewer's left), stone on across the side wing
        for (int x = 0; x <= x1; x++) {
            p.set(x, 5, z0, x <= WX ? "fs_frieze_" + name + "_11_" + (WX - x) + "[facing=north]" : "stone_trim");
            p.set(x, TOP, z0 - 1, "cv_cornice_stone[facing=north]");
        }
        // over each bay its tablet, two windows above with their sills and lintels
        for (int i = 0; i < 2; i++) {
            int bx = 1 + 5 * i, n = 2 - i;
            p.set(bx + 2, 6, z0 - 1, "fs_company_" + n + "_0[facing=north]");
            p.set(bx + 1, 6, z0 - 1, "fs_company_" + n + "_1[facing=north]");
            for (int x = bx + 1; x <= bx + 2; x++) {
                window(x, 7, z0);
            }
        }
        for (int x : new int[] {0, 5, WX}) {
            p.set(x, 4, z0 - 1, (lit && rng.nextDouble() < 0.5 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        }
        // the side wing: the house door between its red lamps, a window by it, two pairs upstairs
        int sx0 = WX + 1;
        Doors.abandoned(p, rng, sx0 + 1, 1, z0, "ext_door_red", "south", Doors.hinge(rng));
        p.set(sx0 + 1, 3, z0, b.glass("x"));
        p.set(sx0 + 1, 4, z0, "stone_trim");
        for (int x : new int[] {sx0, sx0 + 2}) {
            p.set(x, 3, z0 - 1, (lit ? "fs_lamp_red_lit" : "fs_lamp_red") + "[facing=north]");
        }
        p.set(sx0 + 1, 3, z0 + 1, "cv_exit_red[facing=south]");
        for (int x = x1 - 2; x < x1; x++) {
            for (int y = 2; y <= 3; y++) {
                p.set(x, y, z0, b.glass("x"));
            }
            p.set(x, 1, z0 - 1, "sill[facing=north]");
            p.set(x, 4, z0, "stone_trim");
        }
        for (int x : new int[] {sx0 + 1, sx0 + 2, x1 - 2, x1 - 1}) {
            window(x, 7, z0);
        }
    }

    /** A window on the front three high from y, its sill under and the stone lintel over. */
    private void window(int x, int y, int z0) {
        for (int yy = y; yy < y + 3; yy++) {
            p.set(x, yy, z0, b.glass("x"));
        }
        p.set(x, y - 1, z0 - 1, "sill[facing=north]");
        p.set(x, y + 3, z0, "stone_trim");
    }

    /** High windows along the bays' side, the side wing's windows, the back door. */
    private void sides(int x1, int z0, int z1, int tz0) {
        for (int z = z0 + 10; z < z1 - 2; z += 4) {
            for (int y = 3; y <= 4; y++) {
                p.set(0, y, z, b.glass("z"));
            }
        }
        for (int z = z0 + 7; z < z1 - 4; z += 3) {
            for (int y = 8; y <= 9; y++) {
                p.set(0, y, z, b.glass("z"));
            }
        }
        for (int z : new int[] {z0 + 3, z0 + 9, z0 + 15}) {
            if (z < tz0) {
                for (int y = 2; y <= 3; y++) {
                    p.set(x1, y, z, b.glass("z"));
                }
                for (int y = 8; y <= 9; y++) {
                    p.set(x1, y, z, b.glass("z"));
                }
            }
        }
        Doors.abandoned(p, rng, 1, 1, z1, "fire_door", "north", Doors.hinge(rng));
        p.set(1, 3, z1 - 1, "cv_exit_red[facing=north]");
    }

    /** The parapet raised over the bays, the Maltese cross in it. */
    private void parapet(int z0) {
        for (int x = 2; x <= 8; x++) {
            p.set(x, TOP + 2, z0, b.wall(brick, TOP + 2));
            p.set(x, TOP + 3, z0, b.wall(brick, TOP + 3));
            p.set(x, TOP + 4, z0, "stone_trim");
        }
        p.set(1, TOP + 3, z0, "stone_trim");
        p.set(9, TOP + 3, z0, "stone_trim");
        p.set(5, TOP + 2, z0 - 1, "fs_cross[facing=north]");
    }

    // ------------------------------------------------------------------ the apparatus floor

    /** The two bays: a door shut, run up or rammed; an engine in most. Returns which bays stand empty. */
    private boolean[] bays(int z0, int z1) {
        boolean[] empty = new boolean[2];
        for (int i = 0; i < 2; i++) {
            int bx = 1 + 5 * i;
            double r = rng.nextDouble();
            boolean open = r < 0.45, rammed = !open && r < 0.6;
            for (int x = bx; x < bx + 4; x++) {
                p.set(x, 0, z0, "fs_floor_bay");
                for (int y = 1; y <= 4; y++) {
                    String row = open ? (y == 4 ? "fs_door_open_" + colour : "air")
                            : rammed && y == 1 && (x == bx + 1 || x == bx + 2) && colour.equals("red") ? "fs_door_red_dent"
                            : "fs_door_" + colour + "_" + (y - 1);
                    p.set(x, y, z0, row.equals("air") ? row : row + "[facing=north]");
                }
            }
            empty[i] = open ? rng.nextDouble() < 0.6 : rng.nextDouble() < 0.15;
            if (!empty[i]) {
                p.vehicle(bx + 2.0, 1, z0 + 6.5, 180, "fire_truck", rng.nextLong());  // backed in, nose to the door
            }
            // the stop line behind it, the drain down its middle
            for (int x = bx; x < bx + 4; x++) {
                p.set(x, 0, z1 - 4, "fs_floor_stop[facing=north]");
            }
            p.set(bx + 1 + rng.nextInt(2), 0, z1 - 7, "bunk_floor_drain");
        }
        return empty;
    }

    private void apparatus(int z0, int z1, boolean[] empty) {
        // the gear racks down the west wall behind the engine, ready to step into; some gone with their crews
        for (int z = z0 + 12; z <= z1 - 2; z++) {
            double r = rng.nextDouble();
            boolean gone = r < 0.35;
            p.set(1, 1, z, gone ? "fs_gear_hi_empty[facing=east]" : "fs_gear_lo[facing=east]");
            p.set(1, 2, z, gone ? "air" : "fs_gear_hi_" + (rng.nextDouble() < 0.6 ? "black" : "tan") + "[facing=east]");
        }
        p.set(1, 4, z0 + 12, "bunk_lamp_red" + (lit ? "_lit" : "") + "[facing=east]");
        // air bottles and the hose on the partition side behind the second engine; the extinguishers
        p.set(9, 1, z0 + 12, "fs_scba_rack[facing=west]");
        p.set(9, 1, z0 + 13, "fs_scba_rack[facing=west]");
        p.set(9, 1, z0 + 14, "fs_hose_rack[facing=west]");
        p.set(9, 2, z0 + 14, "fs_hose_rack[facing=west]");
        p.set(9, 1, z0 + 15, "fs_hose_rack[facing=west]");
        p.set(9, 4, z0 + 15, "bunk_lamp_red" + (lit ? "_lit" : "") + "[facing=west]");
        p.set(1, 2, z0 + 2, "bunk_extinguisher[facing=east]");
        p.set(9, 2, z0 + 2, "bunk_extinguisher[facing=west]");
        // the workbench and the tool boards on the back wall by the rear door
        p.set(2, 1, z1 - 1, "home_workbench[facing=north]");
        p.set(3, 1, z1 - 1, "pk_tool_chest[facing=north]");
        p.set(2, 2, z1 - 1, "fs_tools_" + (rng.nextBoolean() ? "a" : "b") + "[facing=north]");
        p.set(3, 2, z1 - 1, "fs_tools_" + (rng.nextBoolean() ? "a" : "b") + "[facing=north]");
        p.set(2, 3, z1 - 1, "fs_tools_b[facing=north]");
        // the open stair up the back wall, climbing west, closed in under
        for (int i = 0; i < S2; i++) {
            int x = 9 - i, y = 1 + i;
            p.set(x, y, z1 - 1, "home_stairs_oak[facing=west]");
            for (int yy = 1; yy < y; yy++) {
                p.set(x, yy, z1 - 1, "cv_panel_oak");
            }
            for (int yy = y + 1; yy <= y + 3 && yy < TOP; yy++) {
                p.set(x, yy, z1 - 1, "air");
            }
        }
        // the pole down from the bunk room onto its mat, between the engines
        int pz = z0 + 8;
        p.set(5, 1, pz, "fs_pole_mat");
        for (int y = 2; y < S2; y++) {
            p.set(5, y, pz, "fs_pole");
        }
        p.set(5, S2, pz, "fs_pole_hole");
        p.set(5, S2 + 1, pz, "fs_pole_cage[facing=north]");
        // an empty bay became the aid post: cots, a drip stand, the supply boxes
        for (int i = 0; i < 2; i++) {
            if (!empty[i]) {
                continue;
            }
            int bx = 1 + 5 * i;
            for (int z = z0 + 3; z < z0 + 12; z += 3) {
                if (rng.nextDouble() < 0.8) {
                    p.set(bx + 1, 1, z, "ch_cot_head[facing=north]");
                    p.set(bx + 1, 1, z + 1, "ch_cot_foot[facing=north]");
                }
                if (rng.nextDouble() < 0.5) {
                    p.set(bx + 2, 1, z, rng.nextBoolean() ? "bunk_iv_stand" : "cardboard_box[facing=north]");
                }
            }
            p.set(bx + 3, 1, z0 + 2, "cardboard_box[facing=north]");
            p.set(bx + 3, 2, z0 + 2, "cardboard_box[facing=north]");
            p.set(bx + 3, 1, z0 + 3, "bunk_first_aid[facing=west]");
            p.setIfAir(bx + 2, 1, z0 + 6, "home_blood_pool");
        }
        b.lights(0, z0, WX, z1, S2 - 1, 0.05);
        b.mess(2, z0 + 1, 8, z1 - 2, 1, 0.025);
    }

    // ------------------------------------------------------------------ the side wing

    private void partition(int xa, int xb, int z, int y0, int y1) {
        for (int x = xa; x <= xb; x++) {
            for (int y = y0; y <= y1; y++) {
                p.set(x, y, z, "cv_plaster");
            }
        }
    }

    /** The watch room inside the house door: the desk at the window onto the bays, the boards. */
    private void watchRoom(int x1, int z0) {
        int sx0 = WX + 1, sx1 = x1 - 1;
        partition(sx0, sx1, z0 + 6, 1, S2 - 1);
        for (int z = z0 + 2; z <= z0 + 3; z++) {
            for (int y = 2; y <= 3; y++) {
                p.set(WX, y, z, b.glass("z"));
            }
        }
        Doors.abandoned(p, rng, WX, 1, z0 + 4, "home_door_white", "west", Doors.hinge(rng));
        p.set(9, 4, z0 + 4, "fs_plaque_watch[facing=west]");
        p.set(sx0, 1, z0 + 2, (lit ? "fs_watch_desk_a_lit" : "fs_watch_desk_a") + "[facing=west]");
        p.set(sx0, 1, z0 + 3, "fs_watch_desk_b[facing=west]");
        p.set(sx0 + 1, 1, z0 + 2, rng.nextDouble() < 0.7 ? "office_chair[facing=west]" : "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.set(sx0, 3, z0 + 5, (lit ? "fs_alarm_board_lit" : "fs_alarm_board") + "[facing=north]");
        p.set(sx0 + 2, 3, z0 + 5, "fs_alarm_board[facing=north]");
        // the chalkboard, two by two on the east wall, its first tile at the viewer's left
        String board = rng.nextDouble() < 0.6 ? "b" : "a";
        p.set(sx1, 3, z0 + 2, "fs_board_" + board + "_0[facing=west]");
        p.set(sx1, 3, z0 + 3, "fs_board_" + board + "_1[facing=west]");
        p.set(sx1, 2, z0 + 2, "fs_board_" + board + "_2[facing=west]");
        p.set(sx1, 2, z0 + 3, "fs_board_" + board + "_3[facing=west]");
        p.set(sx1, 1, z0 + 5, "filing_cabinet[facing=west]");
        p.set(sx1, 1, z0 + 4, "home_coat_rack[facing=west]");
        b.mess(sx0, z0 + 1, sx1, z0 + 5, 1, 0.06);
    }

    /** The chief's office behind it. */
    private void office(int x1, int z0) {
        int sx0 = WX + 1, sx1 = x1 - 1;
        partition(sx0, sx1, z0 + 12, 1, S2 - 1);
        Doors.abandoned(p, rng, sx0 + 1, 1, z0 + 6, "home_door_oak", "north", Doors.hinge(rng));
        Doors.abandoned(p, rng, WX, 1, z0 + 9, "home_door_white", "west", Doors.hinge(rng));
        p.set(9, 4, z0 + 9, "fs_plaque_chief[facing=west]");
        for (int z = z0 + 7; z <= z0 + 11; z++) {
            for (int x = sx0; x <= sx1; x++) {
                p.set(x, 0, z, "home_floor_oak");
            }
        }
        int dx = sx0 + 2, dz = z0 + 9;
        p.set(dx, 1, dz, "desk[facing=north]");
        p.set(dx, 1, dz + 1, rng.nextDouble() < 0.8 ? "office_chair[facing=north]" : "air");
        p.set(dx, 2, dz, rng.nextBoolean() ? "home_phone[facing=south]" : "paper");
        p.set(dx + 1, 1, dz, "desk[facing=north]");
        p.set(dx + 1, 2, dz, (lit && rng.nextBoolean() ? "cv_table_lamp_lit" : "cv_table_lamp") + "[axis=x]");
        p.set(dx, 1, dz - 2, "pk_chair_wooden[facing=south]");
        p.set(sx1, 1, z0 + 7, "filing_cabinet[facing=west]");
        p.set(sx1, 1, z0 + 8, "pk_file_cabinet[facing=west]");
        p.set(sx0, 1, z0 + 11, "cv_flag_us[facing=east]");
        p.set(sx0, 2, z0 + 11, "cv_flag_us_top[facing=east]");
        p.set(sx0, 3, z0 + 8, "cv_portrait[facing=east]");
        p.set(sx0, 3, z0 + 10, "fit_painting_" + "abc".charAt(rng.nextInt(3)) + "[facing=east]");
        p.set(sx1, 3, z0 + 10, "th_map_0[facing=west]");
        p.set(sx1, 3, z0 + 11, "th_map_1[facing=west]");
        p.set(dx, S2 - 1, dz - 1, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(sx0, z0 + 7, sx1, z0 + 11, 1, 0.05);
    }

    /** The kitchen across the back, the hose tower's foot open off it. */
    private void kitchen(int x1, int z0, int z1, int tx0, int tz0) {
        int sx0 = WX + 1, sx1 = x1 - 1, kz = z0 + 13;
        Doors.abandoned(p, rng, sx0 + 1, 1, z0 + 12, "home_door_white", "north", Doors.hinge(rng));
        Doors.abandoned(p, rng, WX, 1, kz + 2, "home_door_white", "west", Doors.hinge(rng));
        for (int x = sx0; x <= sx1; x++) {
            if (x == sx0 + 1) {
                continue;
            }
            String unit = x == sx0 + 2 ? "home_sink_white" : x == sx0 + 3 ? "home_stove" : x == sx1 ? "home_fridge" : "home_counter_white";
            p.set(x, 1, kz, unit + "[facing=south]");
        }
        p.set(sx1, 2, kz, "home_fridge_top[facing=south]");
        p.set(sx0, 2, kz, "home_coffee_maker[facing=south]");
        // the long table where the shift ate
        for (int z = kz + 2; z <= Math.min(kz + 4, z1 - 2); z++) {
            p.set(sx0 + 2, 1, z, "home_table");
            p.set(sx0 + 3, 1, z, "home_table");
            p.set(sx0 + 1, 1, z, rng.nextDouble() < 0.8 ? "home_chair[facing=east]" : "home_chair_tipped[facing=" + DIRS[rng.nextInt(4)] + "]");
            if (sx0 + 4 < tx0 || z < tz0) {
                p.set(sx0 + 4, 1, z, rng.nextDouble() < 0.8 ? "home_chair[facing=west]" : "air");
            }
            if (rng.nextDouble() < 0.4) {
                p.set(sx0 + 2 + rng.nextInt(2), 2, z, rng.nextBoolean() ? "paper" : "cv_stack[axis=z]");
            }
        }
        p.set(sx0, 1, z1 - 1, "pk_tv_crt[facing=north]");
        p.set(sx0, 3, kz + 3, "home_calendar[facing=east]");
        p.set(sx0 + 2, S2 - 1, kz + 3, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(sx0, kz + 1, sx1, z1 - 1, 1, 0.05);
    }

    // ------------------------------------------------------------------ upstairs

    private void upstairs(int x1, int z0, int z1, int tx0, int tz0) {
        int y = S2 + 1, top = TOP - 1, zb = z1 - 5, sx0 = WX + 1, sx1 = x1 - 1;
        // the stairwell: its hole, the rail round it
        for (int x = 5; x <= 9; x++) {
            p.set(x, S2, z1 - 1, "air");
            p.set(x, y, z1 - 2, "cv_bar_rail[axis=x]");
        }
        // the captain's room in the front corner
        partition(1, 4, z0 + 5, y, top);
        for (int z = z0 + 1; z <= z0 + 4; z++) {
            for (int yy = y; yy <= top; yy++) {
                p.set(5, yy, z, "cv_plaster");
            }
        }
        Doors.abandoned(p, rng, 5, y, z0 + 3, "home_door_white", "east", Doors.hinge(rng));
        p.set(6, y + 2, z0 + 2, "fs_plaque_captain[facing=east]");
        p.set(1, y, z0 + 3, "fs_bed_head[facing=east]");
        p.set(2, y, z0 + 3, "fs_bed_foot[facing=east]");
        p.set(2, y, z0 + 1, "desk[facing=north]");
        p.set(2, y, z0 + 2, rng.nextDouble() < 0.7 ? "office_chair[facing=north]" : "air");
        p.set(3, y + 1, z0 + 1, rng.nextBoolean() ? "home_radio[facing=south]" : "paper");
        p.set(4, y, z0 + 4, "fs_locker[facing=north]");
        p.set(4, y + 1, z0 + 4, "fs_locker_top[facing=north]");
        p.set(1, y, z0 + 4, "fs_gear_lo[facing=east]");
        p.set(1, y + 1, z0 + 4, "fs_gear_hi_black[facing=east]");
        // the bunk room: beds head to the walls, a locker and a footlocker each
        partition(1, 9, zb, y, top);
        Doors.abandoned(p, rng, 7, y, zb, "home_door_white", "north", Doors.hinge(rng));
        // (the west wall of it carries the stair up to the roof, see roofStair)
        for (int z = z0 + 2; z < zb - 1; z += 3) {
            if (z == z0 + 2 || z == z0 + 8) {
                continue;     // clear of the lounge door, and of the pole
            }
            bunk(9, z, "west");
        }
        b.lights(0, z0, WX, zb, top, lit ? 0.12 : 0.03);
        p.set(5, top, z0 + 12, lit ? "bunk_lamp_red_lit[facing=north]" : "bunk_lamp_red[facing=north]");
        b.mess(2, z0 + 1, 8, zb - 1, y, 0.03);
        // the showers and the sinks at the stair head
        p.set(1, y, z1 - 4, "bunk_shower[facing=east]");
        p.set(1, y, z1 - 3, "bunk_shower[facing=east]");
        p.set(1, y, z1 - 2, "home_toilet[facing=east]");
        for (int x = 2; x <= 4; x++) {
            p.set(x, y, z1 - 4, "bunk_sink[facing=south]");
            p.set(x, y + 2, z1 - 4, "home_mirror[facing=south]");
        }
        p.set(4, y + 2, z1 - 1, "cv_exit_red[facing=north]");
        // the side wing: the TV lounge at the front, the bathroom, the laundry and stores
        partition(sx0, sx1, z0 + 7, y, top);
        partition(sx0, sx1, z0 + 13, y, top);
        Doors.abandoned(p, rng, WX, y, z0 + 3, "home_door_white", "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, WX, y, z0 + 10, "home_door_white", "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, WX, y, z1 - 3, "home_door_white", "west", Doors.hinge(rng));
        int lz = z0 + 5;
        p.set(sx0 + 1, y, lz, "home_sofa_brown_r[facing=north]");
        p.set(sx0 + 2, y, lz, "home_sofa_brown_m[facing=north]");
        p.set(sx0 + 3, y, lz, "home_sofa_brown_l[facing=north]");
        p.set(sx0 + 2, y, lz - 2, "pk_coffee_table[facing=north]");
        p.set(sx0 + 2, y, z0 + 1, "pk_sideboard[facing=south]");
        p.set(sx0 + 2, y + 1, z0 + 1, "pk_tv_crt[facing=south]");
        p.set(sx1, y, z0 + 6, "home_lamp_table[facing=west]");
        p.set(sx1, y + 2, z0 + 3, "home_picture_lake[facing=west]");
        for (int z = z0 + 9; z <= z0 + 10; z++) {
            p.set(sx1, y, z, "bunk_shower[facing=west]");
        }
        p.set(sx1, y, z0 + 12, "home_toilet[facing=west]");
        for (int x = sx0; x <= sx0 + 1; x++) {
            p.set(x, y, z0 + 8, "bunk_sink[facing=south]");
            p.set(x, y + 2, z0 + 8, "home_mirror[facing=south]");
        }
        for (int z = z0 + 14; z < tz0; z++) {
            p.set(sx1, y, z, "store_washer[facing=west]");
        }
        for (int z = z0 + 14; z < z1; z++) {
            p.set(sx0, y, z, (rng.nextBoolean() ? "pk_garage_shelves" : "pk_garage_shelves_2") + "[facing=east]");
        }
        for (int z = z0 + 1; z < z1; z += 6) {
            p.set(sx0 + 2, top, z + 2, rng.nextDouble() < 0.05 ? "ceiling_light" : "ceiling_light_off");
        }
        b.mess(sx0, z0 + 1, sx1, z1 - 1, y, 0.03);
    }

    /** A bunk head to the wall at x, its locker beside it and the footlocker at its foot. */
    private void bunk(int x, int z, String facing) {
        int s = facing.equals("east") ? 1 : -1, y = S2 + 1;
        p.set(x, y, z, "fs_bed_head[facing=" + facing + "]");
        p.set(x + s, y, z, "fs_bed_foot[facing=" + facing + "]");
        if (rng.nextDouble() < 0.7) {
            p.set(x + 2 * s, y, z, "bunk_footlocker[facing=" + facing + "]");
        }
        p.set(x, y, z + 1, "fs_locker[facing=" + facing + "]");
        p.set(x, y + 1, z + 1, "fs_locker_top[facing=" + facing + "]");
    }

    // ------------------------------------------------------------------ the hose tower

    private void tower(int x1, int z1, int tx0, int tz0, int tt) {
        for (int y = 1; y <= tt; y++) {
            for (int x = tx0; x <= x1; x++) {
                for (int z = tz0; z <= z1; z++) {
                    boolean edge = x == tx0 || x == x1 || z == tz0 || z == z1;
                    if (edge) {
                        if (y > TOP || x == tx0 || z == tz0) {
                            p.set(x, y, z, y == TOP + 1 || y == tt - 4 ? "stone_trim" : b.wall(brick, y));
                        }
                    } else {
                        p.set(x, y, z, y == tt ? "stone_trim" : "air");
                    }
                }
            }
        }
        for (int x = tx0; x <= x1; x++) {
            for (int z = tz0; z <= z1; z++) {
                p.set(x, tt, z, "stone_trim");
                if (x == tx0 || x == x1 || z == tz0 || z == z1) {
                    p.set(x, tt + 1, z, "stone_trim");
                }
            }
        }
        // its foot open off the kitchen
        for (int y = 1; y <= 2; y++) {
            p.set(tx0, y, tz0 + 1, "air");
            p.set(tx0, y, tz0 + 2, "air");
        }
        // the louvres near the top on every side, slit windows lower down on the street side
        for (int y = tt - 3; y < tt; y++) {
            for (int k = 1; k <= 2; k++) {
                p.set(tx0 + k, y, tz0, "fs_louvre[facing=north]");
                p.set(tx0 + k, y, z1, "fs_louvre[facing=south]");
                p.set(tx0, y, tz0 + k, "fs_louvre[facing=west]");
                p.set(x1, y, tz0 + k, "fs_louvre[facing=east]");
            }
        }
        for (int y = TOP + 2; y <= TOP + 3; y++) {
            p.set(tx0 + 1, y, tz0, b.glass("x"));
            p.set(tx0 + 2, y, tz0, b.glass("x"));
        }
        // the hose hung inside to dry, two lengths down from the top
        for (int k = 0; k < 2; k++) {
            int x = tx0 + 1 + k, z = tz0 + 1 + k, low = tt - 9 + k * 2;
            for (int y = tt - 1; y > low; y--) {
                p.set(x, y, z, "fs_hose_hung");
            }
            p.set(x, low, z, "fs_hose_hung_end");
        }
        // the siren on top, its light
        p.set(tx0 + 1, tt + 1, tz0 + 1, "ext_mast_foot");
        p.set(tx0 + 1, tt + 2, tz0 + 1, "fs_siren[facing=north]");
        p.set(tx0 + 2, tt + 1, tz0 + 2, "ext_mast");
        p.set(tx0 + 2, tt + 2, tz0 + 2, "vessel_light_red[facing=north]");
    }

    // ------------------------------------------------------------------ the back and the roof

    /** Windows in the back wall: over the stair, by the showers, the laundry and the kitchen; a lamp over the rear door. */
    private void rear(int x1, int z1, int tx0) {
        int sx0 = WX + 1;
        for (int y = S2 + 2; y <= S2 + 3; y++) {
            p.set(3, y, z1, b.glass("x"));
            p.set(7, y, z1, b.glass("x"));
            p.set(8, y, z1, b.glass("x"));
            if (sx0 + 1 < tx0) {
                p.set(sx0 + 1, y, z1, b.glass("x"));
            }
        }
        if (sx0 + 2 < tx0) {
            for (int y = 2; y <= 3; y++) {
                p.set(sx0 + 2, y, z1, b.glass("x"));
            }
        }
        p.set(1, 3, z1 + 1, (lit && rng.nextDouble() < 0.5 ? "ext_sconce_lit" : "ext_sconce") + "[facing=south]");
    }

    /** The flat roof: the radio aerial the watch room talked through, the plant, a vent or two. */
    private void roof(int x1, int z0, int z1, int tx0, int tz0) {
        int y = TOP + 1;
        p.set(3, y, z1 - 3, "hvac");
        // the aerial on a mast over the watch room, guyed back to the parapet
        int ax = WX + 3, az = z0 + 3;
        p.set(ax, y, az, "ext_mast_foot");
        for (int k = 1; k <= 3; k++) {
            p.set(ax, y + k, az, "ext_mast");
        }
        p.set(ax, y + 4, az, "ext_aerial[facing=" + (rng.nextBoolean() ? "north" : "east") + "]");
        // the soil stacks from the showers and the kitchen
        p.set(2, y, z1 - 3, "ext_flue_low");
        p.set(WX + 2, y, z1 - 2, "ext_flue");
        p.set(WX + 2, y + 1, z1 - 2, "ext_flue_cap");
        for (int i = 0; i < 3; i++) {
            p.setIfAir(1 + rng.nextInt(x1 - 1), y, z0 + 1 + rng.nextInt(z1 - z0 - 2), rng.nextBoolean() ? "rubble_pile" : "paper");
        }
    }

    // ------------------------------------------------------------------ out front

    private void apron(int x1, int z0) {
        for (int x = 0; x <= x1; x++) {
            for (int z = 0; z < z0; z++) {
                p.set(x, 0, z, "fs_apron");
                p.set(x, 1, z, "air");
            }
        }
        // KEEP CLEAR before each bay, read from the street: the first slab at the viewer's left
        for (int i = 0; i < 2; i++) {
            int bx = 1 + 5 * i;
            for (int x = bx; x < bx + 4; x++) {
                p.set(x, 0, 2, "fs_apron_text_" + (bx + 3 - x) + "[facing=south]");
            }
        }
        int sx0 = WX + 1;
        p.set(sx0, 1, 0, "fs_hydrant[facing=north]");
        p.set(x1, 1, 0, "fs_callbox_post[facing=north]");
        p.set(x1, 2, 0, (lit ? "fs_callbox_lit" : "fs_callbox") + "[facing=north]");
        p.set(x1 - 2, 0, 3, "stone_trim");
        p.set(x1 - 2, 1, 3, "fs_plinth[facing=north]");
        p.set(x1 - 2, 2, 3, "cv_bell[facing=north]");
        new Civic(p, rng).flagpole(x1, 4, 9);
        for (int i = 0; i < 4; i++) {
            p.setIfAir(rng.nextInt(x1 + 1), 1, rng.nextInt(z0), rng.nextBoolean() ? "paper" : "rubble_pile");
        }
    }
}
