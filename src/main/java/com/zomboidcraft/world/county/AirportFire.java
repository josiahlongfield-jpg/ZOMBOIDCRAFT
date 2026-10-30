package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven International's fire station, the crash crews' house west of the terminal on the
 * taxiway, in the airport's frame ({@link Airport}), after East Midlands' and Frankfurt's stations
 * (refs/airport/arff-fire-station): a long apparatus hall of five bays opening south onto its own
 * apron and the taxiway, white sectional doors in a red steel front, GRAYHAVEN AIRPORT FIRE RESCUE
 * along the fascia; the crew block at its east end, two storeys, with the watch room glazed on its
 * roof looking down the field under its red cap; the hose tower at the back corner, the foam tank,
 * the crews' car park behind.
 *
 * <p>Inside the hall the engines that never got out, backed in nose to the doors, one bay emptied
 * when they ran, the gear racks and the air bottles down the back wall. The crew block: the watch
 * room with its radio desk and the alarm board, the locker room with the turnouts, the plant room,
 * the gym, the chief's office, the showers, the corridor between; upstairs the day room and the
 * kitchen, the bunk rooms, the classroom. The stair goes on up to the watch room on the roof.
 */
final class AirportFire extends Grounds {
    /** The hall from X0 to HX1 (its east wall the crew block's west), the crew block on to X1; the back wall Z0, the front Z1. */
    static final int X0 = -640, HX1 = X0 + 52, X1 = HX1 + 26, Z0 = -205, Z1 = -178;
    /** The roof over both, the crew block's upper floor, the bay doors' height, the number of bays and each one's door width. */
    static final int ROOF = 10, S2 = 5, DOOR = 6, BAYS = 5, BAY_W = 8;
    /** The watch room on the crew block's roof, in front of the stair's head; its cap. */
    static final int WX0 = HX1, WX1 = HX1 + 13, WZ0 = Z0 + 9, WZ1 = Z0 + 18, WTOP = ROOF + 5;
    /** The corridor down the crew block, two wide. */
    static final int CX = HX1 + 13;
    /** Everything this builds lies within these (in the frame): the car park behind, the apron down to the taxiway. */
    static final int BX0 = X0 - 16, BX1 = X1 + 3, BZ0 = Z0 - 32, BZ1 = Airport.TWY_Z - Airport.TWY_SHOULDER - 1;

    AirportFire(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.3, 0.1, 0.3);
    }

    void build() {
        apron();
        shell();
        b.flat(X0, Z0, X1, Z1, ROOF, "ml_precast", 1, "ml_precast");
        hall();
        crewGround();
        crewUpper();
        watchRoom();
        hoseTower();
        foamTank();
        carPark();
    }

    private String clad(int y) {
        return y <= 2 ? "concrete_panel_b" : chance() < 0.06 ? "corrugated_red_stain" : chance() < 0.3 ? "corrugated_red_b" : "corrugated_red";
    }

    // ------------------------------------------------------------------ the shell

    /** The walls and the slabs: the hall one tall room, the crew block two floors, the windows in bands. */
    private void shell() {
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean wall = x == X0 || x == X1 || z == Z0 || z == Z1 || x == HX1;
                boolean hall = x < HX1;
                set(x, 0, z, wall ? "concrete_dark" : hall ? "fs_floor_bay" : "un_slab_vct");
                for (int y = 1; y < ROOF; y++) {
                    set(x, y, z, wall ? clad(y) : "air");
                }
                if (!hall && !wall) {
                    set(x, S2, z, "un_slab_vct");
                }
                set(x, ROOF, z, "un_roof");
            }
        }
        // the white band along the top of every wall, the plinth's dark course
        for (int x = X0; x <= X1; x++) {
            for (int z : new int[] {Z0, Z1}) {
                set(x, ROOF - 1, z, "ml_precast_band");
            }
        }
        for (int z = Z0; z <= Z1; z++) {
            for (int x : new int[] {X0, X1}) {
                set(x, ROOF - 1, z, "ml_precast_band");
            }
        }
        // the crew block's windows: a band on each floor, front, back and east
        for (int y0 : new int[] {0, S2}) {
            for (int x = HX1 + 1; x < X1; x++) {
                if (Math.floorMod(x - HX1, 4) != 0 && x != CX && x != CX + 1) {
                    for (int z : new int[] {Z0, Z1}) {
                        win(x, y0 + 2, z, z == Z1 ? "south" : "north");
                        win(x, y0 + 3, z, z == Z1 ? "south" : "north");
                    }
                }
            }
            for (int z = Z0 + 1; z < Z1; z++) {
                if (Math.floorMod(z - Z0, 4) != 0) {
                    win(X1, y0 + 2, z, "east");
                    win(X1, y0 + 3, z, "east");
                }
            }
        }
        // the hall's clerestory along the back, the high windows in the west end
        for (int x = X0 + 2; x < HX1 - 1; x++) {
            if (Math.floorMod(x - X0, 5) != 0) {
                win(x, 7, Z0, "north");
            }
        }
        for (int z = Z0 + 2; z < Z1 - 1; z++) {
            if (Math.floorMod(z - Z0, 5) != 0) {
                win(X0, 7, z, "west");
            }
        }
    }

    private void win(int x, int y, int z, String out) {
        set(x, y, z, (chance() < 0.12 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]");
    }

    /** The bay doors in the hall's front, their numbers over them, the name along the fascia. */
    private int bayX(int i) {
        return X0 + 2 + i * (BAY_W + 2);
    }

    // ------------------------------------------------------------------ the apparatus hall

    private void hall() {
        String[] state = new String[BAYS];
        for (int i = 0; i < BAYS; i++) {
            int bx = bayX(i);
            double r = chance();
            state[i] = r < 0.4 ? "open" : r < 0.5 ? "rammed" : "shut";
            for (int x = bx; x < bx + BAY_W; x++) {
                set(x, 0, Z1, "fs_floor_bay");
                for (int y = 1; y <= DOOR; y++) {
                    String s;
                    if (state[i].equals("open")) {
                        s = y == DOOR ? "ah_sect_head[facing=south]" : "air";
                    } else if (state[i].equals("rammed") && y <= 2 && x > bx + 1 && x < bx + 6) {
                        s = y == 1 ? "air" : "ah_sect_white_dent[facing=south]";
                    } else {
                        s = "ah_sect_white" + (y == 4 ? "_glass" : "") + "[facing=south]";
                    }
                    set(x, y, Z1, s);
                }
                set(x, DOOR + 1, Z1, "ml_precast_band");
            }
            // the bay's number over its door, the stop line and the drain inside, the yellow lead-out lines on the apron
            set(bx + BAY_W / 2 - 1, DOOR + 2, Z1 + 1, "ah_bay_" + (i + 1) + "[facing=south]");
            for (int x = bx; x < bx + BAY_W; x++) {
                set(x, 0, Z0 + 4, "fs_floor_stop[facing=south]");
            }
            set(bx + 3 + (chance() < 0.5 ? 1 : 0), 0, Z1 - 10, "bunk_floor_drain");
            for (int z = Z1 + 1; z < Z1 + 26; z++) {
                set(bx + BAY_W / 2 - 1, 0, z, "af_paint_y");
            }
            // an engine in most bays, backed in nose to the doors; the open ones mostly gone out
            boolean gone = state[i].equals("open") ? chance() < 0.6 : chance() < 0.12;
            if (!gone) {
                p.vehicle(bx + BAY_W / 2.0, 1, Z1 - 8.5, 0f, i == BAYS - 1 && chance() < 0.6 ? "ambulance" : "fire_truck", rng.nextLong());
            } else if (chance() < 0.6) {
                set(bx + 2, 1, Z1 - 6, "st_cone");
                set(bx + 5, 1, Z1 - 9, "fs_hose_hung_end");
                set(bx + 3, 0 + 1, Z1 - 12, "home_blood_pool");
            }
        }
        // the name along the fascia, across the middle of the hall's front
        int nx = (X0 + HX1) / 2 - 6;
        for (int i = 0; i < 12; i++) {
            if (chance() < 0.06) {
                continue;
            }
            set(nx + i, ROOF - 1, Z1 + 1, "ah_arff_" + i + "[facing=south]");
        }
        // the gear racks down the back wall, some left empty by the crews that went; the air bottles, the hose, the tools
        for (int x = X0 + 2; x < HX1 - 1; x++) {
            int k = Math.floorMod(x - X0, 12);
            if (k < 6) {
                boolean empty = chance() < 0.35;
                set(x, 1, Z0 + 1, empty ? "fs_gear_hi_empty[facing=south]" : "fs_gear_lo[facing=south]");
                set(x, 2, Z0 + 1, empty ? "air" : "fs_gear_hi_" + (chance() < 0.6 ? "black" : "tan") + "[facing=south]");
            } else if (k < 8) {
                set(x, 1, Z0 + 1, "fs_scba_rack[facing=south]");
            } else if (k < 10) {
                set(x, 1, Z0 + 1, "fs_hose_rack[facing=south]");
                set(x, 2, Z0 + 1, "fs_hose_rack[facing=south]");
            } else if (k == 10) {
                set(x, 1, Z0 + 1, "home_workbench[facing=south]");
                set(x, 2, Z0 + 1, "fs_tools_" + (chance() < 0.5 ? "a" : "b") + "[facing=south]");
            } else {
                set(x, 1, Z0 + 1, any("pw_drum", "barrel", "pk_tool_chest[facing=south]"));
            }
            if (k == 3) {
                set(x, 4, Z0 + 1, "bunk_lamp_red" + (power ? "_lit" : "") + "[facing=south]");
            }
        }
        for (int z = Z0 + 6; z < Z1 - 2; z += 6) {
            set(X0 + 1, 2, z, "bunk_extinguisher[facing=east]");
        }
        set(X0 + 1, 1, Z0 + 3, "gs_compressor_lo[facing=east]");
        set(X0 + 1, 2, Z0 + 3, "gs_compressor_hi[facing=east]");
        // the doors through to the crew block: the locker room and the watch room
        set(HX1, 1, Z0 + 13, "air");
        set(HX1, 2, Z0 + 13, "air");
        door(HX1, 0, Z0 + 13, Doors.STEEL, "west");
        set(HX1, 1, Z1 - 4, "air");
        set(HX1, 2, Z1 - 4, "air");
        door(HX1, 0, Z1 - 4, Doors.STEEL, "west");
        // the watch room's window onto the floor
        for (int z = Z1 - 7; z <= Z1 - 6; z++) {
            set(HX1, 2, z, "wf_glass[facing=west]");
            set(HX1, 3, z, "wf_glass[facing=west]");
        }
        // the roof trusses across the hall every five, the lights hung between them over the bays
        for (int x = X0 + 1; x < HX1; x += 5) {
            for (int z = Z0 + 1; z < Z1; z++) {
                set(x, ROOF - 1, z, "tx_truss[axis=z]");
            }
        }
        for (int i = 0; i < BAYS; i++) {
            for (int z = Z0 + 4; z < Z1 - 1; z += 6) {
                set(bayX(i) + BAY_W / 2 - 1, ROOF - 1, z, power && chance() < 0.4 ? "fit_downlight_on" : "fit_downlight");
            }
        }
        mess(X0 + 1, Z0 + 3, HX1 - 1, Z1 - 1, 0, 0.012);
    }

    // ------------------------------------------------------------------ the crew block

    /**
     * The ground floor: the corridor from the front door to the back, the watch room and the locker
     * room on the hall's side with the stair and the plant room behind them, the office, the showers
     * and the gym on the other.
     */
    private void crewGround() {
        int y = 0, h = S2, zf = Z1 - 8, zm = Z0 + 10;
        // the corridor's walls, the cross walls each side
        partZ(CX - 1, Z0 + 1, Z1 - 1, y, h, "drywall", "drywall");
        partZ(CX + 2, Z0 + 1, Z1 - 1, y, h, "drywall", "drywall");
        partX(HX1 + 1, CX - 2, zf, y, h, "drywall", "drywall");
        partX(CX + 3, X1 - 1, zf, y, h, "drywall", "drywall");
        partX(HX1 + 6, CX - 2, zm, y, h, "drywall", "drywall");
        partX(CX + 3, X1 - 1, zm, y, h, "drywall", "drywall");
        // the front door, the back door
        for (int x = CX; x <= CX + 1; x++) {
            for (int yy = 1; yy <= 3; yy++) {
                set(x, yy, Z1, "air");
                set(x, yy, Z0, "air");
            }
        }
        doors(CX, y, Z1, Doors.SHOP, "south");
        doors(CX, y, Z0, Doors.STEEL, "north");
        for (int x = CX - 1; x <= CX + 2; x++) {
            for (int z = Z1 + 1; z <= Z1 + 3; z++) {
                set(x, 4, z, "tx_canopy");
            }
        }
        // the rooms' doors off the corridor
        int[][] ds = {{CX - 1, Z1 - 4, 0}, {CX - 1, Z0 + 14, 0}, {CX + 2, Z1 - 4, 1}, {CX + 2, Z0 + 14, 1}, {CX + 2, Z0 + 4, 1}, {CX - 1, Z0 + 4, 0}};
        for (int[] d : ds) {
            set(d[0], 1, d[1], "air");
            set(d[0], 2, d[1], "air");
            door(d[0], y, d[1], Doors.FLAT, d[2] == 0 ? "west" : "east");
        }
        // the stair from the ground to the roof, its door into the locker room
        stair(HX1 + 1, Z0, true, new int[] {0, S2, ROOF}, "ml_precast", "un_roof");
        // the watch room: the radio desk at the window onto the hall, the alarm board, the map, the board of the last calls
        int wx0 = HX1 + 1, wx1 = CX - 2;
        set(wx0, 1, Z1 - 7, (power ? "fs_watch_desk_a_lit" : "fs_watch_desk_a") + "[facing=west]");
        set(wx0, 1, Z1 - 6, "fs_watch_desk_b[facing=west]");
        set(wx0 + 1, 1, Z1 - 7, chance() < 0.7 ? "office_chair[facing=west]" : "office_chair_fallen[facing=" + dir() + "]");
        set(wx0 + 1, 1, Z1 - 5, "bunk_radio_desk[facing=south]");
        set(wx0 + 3, 3, zf + 1, (power ? "fs_alarm_board_lit" : "fs_alarm_board") + "[facing=south]");
        set(wx0 + 5, 3, zf + 1, "fs_alarm_board[facing=south]");
        set(wx0 + 7, 3, zf + 1, "bunk_map_wall[facing=south]");
        String board = chance() < 0.6 ? "b" : "a";
        set(wx1, 3, Z1 - 6, "fs_board_" + board + "_0[facing=west]");
        set(wx1, 3, Z1 - 5, "fs_board_" + board + "_1[facing=west]");
        set(wx1, 2, Z1 - 6, "fs_board_" + board + "_2[facing=west]");
        set(wx1, 2, Z1 - 5, "fs_board_" + board + "_3[facing=west]");
        set(wx1, 1, Z1 - 2, "filing_cabinet[facing=west]");
        set(wx1, 1, Z1 - 1, "home_coat_rack[facing=west]");
        set(wx0 + 4, 1, Z1 - 3, "bunk_map_table");
        set(HX1 + 2, 2, Z1, "fs_plaque_watch[facing=south]");
        // the locker room: the turnouts on their hooks, the lockers, the bench
        for (int x = HX1 + 1; x <= CX - 3; x++) {
            if (x <= HX1 + 5) {
                continue;
            }
            set(x, 1, zf - 1, "fs_locker[facing=north]");
            set(x, 2, zf - 1, "fs_locker_top[facing=north]");
            boolean gone = chance() < 0.35;
            set(x, 1, zm + 1, gone ? "fs_gear_hi_empty[facing=south]" : "fs_gear_lo[facing=south]");
            set(x, 2, zm + 1, gone ? "air" : "fs_gear_hi_" + (chance() < 0.6 ? "black" : "tan") + "[facing=south]");
        }
        for (int x = HX1 + 7; x <= CX - 4; x++) {
            set(x, 1, (zf + zm) / 2, "st_bench[facing=north]");
        }
        set(HX1 + 1, 1, zf - 2, "fs_scba_rack[facing=east]");
        set(HX1 + 1, 1, zf - 3, "fs_scba_rack[facing=east]");
        // the plant room behind it, east of the stair
        set(HX1 + 6, 1, Z0 + 1, "cellar_furnace[facing=south]");
        set(HX1 + 6, 2, Z0 + 1, "cellar_furnace_top[facing=south]");
        set(HX1 + 8, 1, Z0 + 1, "pw_breaker[facing=south]");
        set(HX1 + 9, 1, Z0 + 1, "pw_breaker[facing=south]");
        set(HX1 + 11, 1, Z0 + 1, "bunk_switchboard[facing=south]");
        set(HX1 + 11, 2, Z0 + 1, "bunk_switchboard_top[facing=south]");
        set(CX - 2, 1, Z0 + 7, "water_tank");
        // the chief's office: the desk, the cabinets, the whiteboard
        int ox0 = CX + 3, ox1 = X1 - 1;
        set(ox0 + 4, 1, Z1 - 4, "home_desk_pc[facing=north]");
        set(ox0 + 4, 1, Z1 - 3, (chance() < 0.2 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=north]");
        set(ox0 + 7, 1, Z1 - 4, "store_chair[facing=south]");
        set(ox1, 1, zf + 1, "filing_cabinet[facing=west]");
        set(ox1, 1, zf + 2, "filing_cabinet[facing=west]");
        set(ox0 + 2, 2, zf + 1, "of_whiteboard[facing=south]");
        set(ox1, 1, Z1 - 1, "home_coat_rack[facing=west]");
        // the showers and the toilets
        for (int x = ox0 + 1; x <= ox1 - 1; x += 2) {
            set(x, 1, zm + 1, "bunk_shower[facing=south]");
            set(x, 1, zf - 1, x < ox0 + 6 ? "fit_toilet_stall[facing=north]" : "fit_sink_cabin[facing=north]");
        }
        // the gym at the back
        set(ox0 + 2, 1, Z0 + 3, "of_treadmill[facing=north]");
        set(ox0 + 4, 1, Z0 + 3, "of_treadmill[facing=north]");
        set(ox0 + 7, 1, Z0 + 2, "of_dumbbells[facing=south]");
        set(ox0 + 9, 1, Z0 + 5, "of_dumbbells[facing=west]");
        lamps(HX1 + 1, Z0 + 1, X1 - 1, Z1 - 1, S2, 0.35);
        mess(HX1 + 1, Z0 + 1, X1 - 1, Z1 - 1, 0, 0.03);
    }

    /** Upstairs: the day room and the kitchen at the front, the bunk rooms behind, the classroom at the back. */
    private void crewUpper() {
        int y = S2, h = ROOF - S2, zf = Z1 - 8, zm = Z0 + 10;
        partZ(CX - 1, Z0 + 1, Z1 - 1, y, h, "drywall", "drywall");
        partZ(CX + 2, Z0 + 1, Z1 - 1, y, h, "drywall", "drywall");
        partX(HX1 + 1, CX - 2, zf, y, h, "drywall", "drywall");
        partX(CX + 3, X1 - 1, zf, y, h, "drywall", "drywall");
        partX(HX1 + 6, CX - 2, zm, y, h, "drywall", "drywall");
        partX(CX + 3, X1 - 1, zm, y, h, "drywall", "drywall");
        int[][] ds = {{CX - 1, Z1 - 4, 0}, {CX - 1, Z0 + 14, 0}, {CX + 2, Z1 - 4, 1}, {CX + 2, Z0 + 14, 1}, {CX + 2, Z0 + 4, 1}, {CX - 1, Z0 + 4, 0}};
        for (int[] d : ds) {
            set(d[0], y + 1, d[1], "air");
            set(d[0], y + 2, d[1], "air");
            door(d[0], y, d[1], Doors.FLAT, d[2] == 0 ? "west" : "east");
        }
        int yy = y + 1;
        // the day room: the sofas round the TV, the long table; the kitchen along the hall wall
        int dx0 = HX1 + 1;
        for (int z = Z1 - 6; z <= Z1 - 2; z++) {
            set(dx0, yy, z, z == Z1 - 6 ? "store_oven[facing=east]" : z == Z1 - 5 ? "home_fridge[facing=east]" : "store_counter[facing=east]");
        }
        set(dx0, yy, Z1 - 1, "store_coffee[facing=east]");
        for (int x = dx0 + 3; x <= dx0 + 7; x++) {
            set(x, yy, Z1 - 4, "fit_mess_table[axis=x]");
            set(x, yy, Z1 - 5, "store_chair[facing=south]");
            set(x, yy, Z1 - 3, "store_chair[facing=north]");
        }
        set(CX - 3, yy, zf + 2, "home_tv[facing=west]");
        set(CX - 6, yy, zf + 1, "home_sofa_brown_l[facing=east]");
        set(CX - 6, yy, zf + 2, "home_sofa_brown_m[facing=east]");
        set(CX - 6, yy, zf + 3, "home_sofa_brown_r[facing=east]");
        // the bunk rooms behind, each side of the corridor
        for (int x = HX1 + 7; x <= CX - 3; x += 2) {
            bunk(x, zf - 1, "north");
        }
        for (int x = CX + 4; x <= X1 - 2; x += 2) {
            bunk(x, zf - 1, "north");
            bunk(x, zm + 1, "south");
        }
        // the store over the plant room: the bedding, the boxes of spares
        for (int x = HX1 + 6; x <= CX - 3; x++) {
            set(x, yy, Z0 + 1, chance() < 0.5 ? "st_pallet_cots" : "cardboard_box[facing=south]");
            if (chance() < 0.4) {
                set(x, yy + 1, Z0 + 1, "cardboard_box[facing=south]");
            }
        }
        // the front room over the office: the captain's room
        set(X1 - 3, yy, Z1 - 3, "home_bed_blue_head[facing=south]");
        set(X1 - 3, yy, Z1 - 2, "home_bed_blue_foot[facing=south]");
        set(CX + 4, yy, Z1 - 1, "home_desk_pc[facing=south]");
        // the classroom at the back: the tables facing the whiteboard
        for (int x = CX + 4; x <= X1 - 2; x += 3) {
            for (int z = Z0 + 3; z <= Z0 + 7; z += 2) {
                set(x, yy, z, "store_table");
                set(x, yy, z + 1, "store_chair[facing=north]");
            }
        }
        set(CX + 8, yy + 1, Z0 + 1, "of_whiteboard[facing=south]");
        lamps(HX1 + 1, Z0 + 1, X1 - 1, Z1 - 1, ROOF, 0.3);
        mess(HX1 + 1, Z0 + 1, X1 - 1, Z1 - 1, y, 0.03);
    }

    /** A bunk with its head at (x, z) against a wall, its foot the way it faces, the footlocker past that. */
    private void bunk(int x, int z, String facing) {
        int s = facing.equals("south") ? 1 : -1;
        set(x, S2 + 1, z, "fs_bed_head[facing=" + facing + "]");
        set(x, S2 + 1, z + s, "fs_bed_foot[facing=" + facing + "]");
        if (chance() < 0.6) {
            set(x, S2 + 1, z + 2 * s, "bunk_footlocker[facing=" + facing + "]");
        }
    }

    // ------------------------------------------------------------------ the watch room on the roof

    /** Glazed on three sides over the parapet, the stair's head behind it, the red cap; the desk along the front glass. */
    private void watchRoom() {
        for (int x = WX0; x <= WX1; x++) {
            for (int z = WZ0; z <= WZ1; z++) {
                boolean wall = x == WX0 || x == WX1 || z == WZ0 || z == WZ1;
                set(x, ROOF, z, wall ? "un_roof" : "un_slab_vct");
                for (int y = ROOF + 1; y < WTOP; y++) {
                    String s = "air";
                    if (wall) {
                        String out = z == WZ1 ? "south" : z == WZ0 ? "north" : x == WX0 ? "west" : "east";
                        boolean corner = (x == WX0 || x == WX1) && (z == WZ0 || z == WZ1);
                        boolean glass = !corner && z != WZ0 && y >= ROOF + 2;
                        s = y == WTOP - 1 ? "ml_precast_band" : glass ? (chance() < 0.15 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]" : "ml_precast";
                    }
                    set(x, y, z, s);
                }
                set(x, WTOP, z, wall ? "corrugated_red" : "un_roof");
            }
        }
        // the red cap's lip all round
        for (int x = WX0 - 1; x <= WX1 + 1; x++) {
            set(x, WTOP, WZ0 - 1, "corrugated_red");
            set(x, WTOP, WZ1 + 1, "corrugated_red");
        }
        for (int z = WZ0; z <= WZ1; z++) {
            set(WX0 - 1, WTOP, z, "corrugated_red");
            set(WX1 + 1, WTOP, z, "corrugated_red");
        }
        // the stair's head opens into it from the back (the stair shaft's south side at WZ0)
        for (int x = HX1 + 2; x <= HX1 + 3; x++) {
            set(x, ROOF + 1, WZ0, "air");
            set(x, ROOF + 2, WZ0, "air");
        }
        // the desk along the front glass with its radios and screens, the chairs; the binoculars' stand, the crash phone
        int y = ROOF + 1;
        for (int x = WX0 + 2; x <= WX1 - 2; x++) {
            set(x, y, WZ1 - 1, (x % 3 == 0 ? (power ? "fs_watch_desk_a_lit" : "fs_watch_desk_a") : "fs_watch_desk_b") + "[facing=south]");
            if (x % 3 == 1) {
                set(x, y, WZ1 - 2, chance() < 0.75 ? "office_chair[facing=south]" : "office_chair_fallen[facing=" + dir() + "]");
            }
        }
        set(WX1 - 1, y, WZ0 + 2, "bunk_radio_desk[facing=east]");
        set(WX0 + 5, y + 1, WZ0, "fs_alarm_board" + (power ? "_lit" : "") + "[facing=south]");
        set(WX0 + 8, y + 1, WZ0, "bunk_map_wall[facing=south]");
        set(WX1 - 2, WTOP - 1, (WZ0 + WZ1) / 2, power ? "fit_downlight_on" : "fit_downlight");
        set(WX0 + 4, WTOP - 1, (WZ0 + WZ1) / 2, power ? "fit_downlight_on" : "fit_downlight");
        // the mast and the beacon on the cap
        for (int yy = WTOP + 1; yy <= WTOP + 6; yy++) {
            set(WX1 - 1, yy, WZ0 + 1, yy == WTOP + 1 ? "ext_mast_foot" : "ext_mast");
        }
        set(WX1 - 1, WTOP + 7, WZ0 + 1, "vessel_light_red");
        mess(WX0 + 1, WZ0 + 1, WX1 - 1, WZ1 - 1, ROOF, 0.04);
    }

    // ------------------------------------------------------------------ the yard

    /** The hose tower at the hall's back west corner: four square, the hose hung down inside to dry, louvres at the top, the siren. */
    private void hoseTower() {
        int tx0 = X0 - 5, tz0 = Z0 + 1, top = ROOF + 9;
        for (int x = tx0; x <= tx0 + 4; x++) {
            for (int z = tz0; z <= tz0 + 4; z++) {
                boolean wall = x == tx0 || x == tx0 + 4 || z == tz0 || z == tz0 + 4;
                set(x, 0, z, "concrete_dark");
                for (int y = 1; y < top; y++) {
                    set(x, y, z, !wall ? "air" : y >= top - 3 && y < top - 1 && (x == tx0 + 2 || z == tz0 + 2) ? "fs_louvre[facing="
                            + (x == tx0 ? "west" : x == tx0 + 4 ? "east" : z == tz0 ? "north" : "south") + "]" : y == top - 1 ? "ml_precast_band" : "ml_precast");
                }
                set(x, top, z, "un_roof");
            }
        }
        for (int y = 2; y < top - 1; y++) {
            for (int[] c : new int[][] {{tx0 + 1, tz0 + 1}, {tx0 + 3, tz0 + 2}, {tx0 + 2, tz0 + 3}}) {
                set(c[0], y, c[1], y == 2 ? "fs_hose_hung_end" : "fs_hose_hung");
            }
        }
        // its door from the hall, the siren and the mast on its roof
        set(X0, 1, tz0 + 2, "air");
        set(X0, 2, tz0 + 2, "air");
        set(tx0 + 4, 1, tz0 + 2, "air");
        set(tx0 + 4, 2, tz0 + 2, "air");
        door(X0, 0, tz0 + 2, Doors.STEEL, "west");
        set(tx0 + 2, top + 1, tz0 + 2, "fs_siren");
        for (int y = top + 1; y <= top + 5; y++) {
            set(tx0 + 4, y, tz0 + 4, y == top + 1 ? "ext_mast_foot" : "ext_mast");
        }
    }

    /** The foam tank behind the hall, white, on its pad, the pipe to the hall. */
    private void foamTank() {
        double cx = X0 + 12.5, cz = Z0 - 7.5;
        for (int x = X0 + 6; x <= X0 + 19; x++) {
            for (int z = Z0 - 14; z <= Z0 - 1; z++) {
                set(x, 0, z, "concrete_dark");
            }
        }
        Round.wall(p, cx, cz, 5, 1, 9, "wtw", 0);
        Round.roof(p, cx, cz, 5, "s", 10, "wtw");
        for (int z = Z0 - 2; z < Z0; z++) {
            set(X0 + 12, 1, z, "fm_pipe");
        }
        set(X0 + 16, 1, Z0 - 2, "fit_valve");
    }

    /** The crews' car park behind the station, the cars they came in still in their bays. */
    private void carPark() {
        int z0 = Z0 - 30, z1 = Z0 - 2;
        for (int x = HX1 - 20; x <= X1 + 2; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                String g = r < 0.1 ? "asphalt_crack" : r < 0.14 ? "asphalt_patch" : "asphalt";
                int u = x - (HX1 - 20);
                if ((z == z0 + 6 || z == z1 - 6) && u % 3 == 0) {
                    g = "road_line_white[axis=z]";
                }
                set(x, 0, z, g);
            }
        }
        for (int x = HX1 - 19; x <= X1 + 1; x += 3) {
            if (chance() < 0.45) {
                p.vehicle(x + 1.0, 1, z0 + 3.0, 180f, "sedan,suv,pickup,hatchback,minivan", rng.nextLong());
            }
            if (chance() < 0.4) {
                p.vehicle(x + 1.0, 1, z1 - 3.0, 0f, "sedan,suv,pickup,hatchback,wagon", rng.nextLong());
            }
        }
        // the flag by the front door, at half mast's worth of rag
        set(X1 + 2, 1, Z1 + 2, "cv_flagpole_base");
        for (int y = 2; y <= 9; y++) {
            set(X1 + 2, y, Z1 + 2, "cv_flagpole");
        }
        set(X1 + 2, 10, Z1 + 2, "cv_flagpole_top[facing=south]");
    }

    /** The station's own apron from its doors to the taxiway, the concrete in five-metre slabs. */
    private void apron() {
        for (int x = X0 - 4; x <= HX1 + 4; x++) {
            for (int z = Z1 + 1; z <= BZ1; z++) {
                boolean jx = Math.floorMod(x, 5) == 0, jz = Math.floorMod(z, 5) == 0;
                set(x, 0, z, jx && jz ? "af_apron_jj[facing=north]" : jx ? "af_apron_j[facing=north]" : jz ? "af_apron_j[facing=east]" : "af_apron");
            }
        }
        // the walk to the front door and the crew block's forecourt
        for (int x = HX1 + 1; x <= X1 + 3; x++) {
            for (int z = Z1 + 1; z <= Z1 + 6; z++) {
                set(x, 0, z, "floor_concrete");
            }
        }
    }
}
