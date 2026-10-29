package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Broadcast.AX0;
import static com.zomboidcraft.world.county.Broadcast.AX1;
import static com.zomboidcraft.world.county.Broadcast.AZ0;
import static com.zomboidcraft.world.county.Broadcast.AZ1;
import static com.zomboidcraft.world.county.Broadcast.BX1;
import static com.zomboidcraft.world.county.Broadcast.BZ0;
import static com.zomboidcraft.world.county.Broadcast.HZ0;
import static com.zomboidcraft.world.county.Broadcast.HZ1;
import static com.zomboidcraft.world.county.Broadcast.ROOF;
import static com.zomboidcraft.world.county.Broadcast.ROOF_B;
import static com.zomboidcraft.world.county.Broadcast.S;

/**
 * Broadcast House: the brown-brick block of three floors along the street and the windowless
 * studio wing east of it.
 *
 * <p>The ground floor: the lobby behind the glass with reception, the monitor wall and the awards,
 * the green room and the conference room beside it; the corridor through; behind it the newsroom
 * (the assignment desk, the desks in pods, the wire machines, the news director's glass box),
 * the washrooms and the tape library, the control room looking at its monitor wall with the studio
 * through the door, master control behind it in its rows of racks. The second floor: the offices
 * along the front, production and the weather center behind, the edit suites off their hall. The
 * third: the engineers' shop and the break room, the FM station (on-air, production, the record
 * library), and along the front the room the staff lived in at the end, the emergency desk at the
 * windows. The studio wing: the scene dock with its roll-up door, Studio A under its lighting grid
 * with the NewsCenter 6 set, the cameras where they stopped, the weather wall.
 */
final class BroadcastHouse {
    /** The block's partitions: the room columns and rows. */
    static final int NEWS_X1 = 37, COL_X0 = 39, COL_X1 = 44, CTL_X0 = 46, MC_Z0 = 48;
    /** The stairs: one off the corridor in the column, one in the newsroom's corner. */
    static final int ST1_X = 40, ST1_Z = 28, ST2_X = 7, ST2_Z = 56;
    /** Studio A and the scene dock in the wing. */
    static final int SA_Z0 = 35, DOCK_Z1 = 33;

    private final Broadcast c;

    BroadcastHouse(Broadcast c) {
        this.c = c;
    }

    void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        c.fill(x0, y0, z0, x1, y1, z1, b);
    }

    boolean empty(int x, int y, int z) {
        return c.empty(x, y, z);
    }

    double chance() {
        return c.chance();
    }

    String any(String... o) {
        return c.any(o);
    }

    void build() {
        shell();
        stairs();
        for (int k = 0; k < 3; k++) {
            partitions(k * S);
        }
        lobby();
        frontRooms();
        newsroom();
        column(0);
        control();
        master();
        floor2();
        floor3();
        for (int k = 0; k < 3; k++) {
            c.ceiling(k * S + S, AX0 + 1, AZ0 + 1, AX1 - 1, AZ1 - 1, 0.3);
        }
        wing();
        dock();
        studio();
        roofs();
        for (int k = 0; k < 3; k++) {
            c.mess(AX0 + 1, AZ0 + 1, AX1 - 1, AZ1 - 1, k * S, 0.025);
        }
    }

    // ------------------------------------------------------------------ the shell

    /** The brick walls, the precast bands at the floors, ribbon windows, slabs and the roof. */
    private void shell() {
        for (int y = 0; y <= ROOF + 1; y++) {
            for (int x = AX0; x <= AX1; x++) {
                outer(x, y, AZ0, "north");
                outer(x, y, AZ1, "south");
            }
            for (int z = AZ0 + 1; z < AZ1; z++) {
                outer(AX0, y, z, "west");
                if (z < BZ0 || y > ROOF_B) {
                    outer(AX1, y, z, "east");
                } else {
                    set(AX1, y, z, y == 0 ? "floor_concrete" : c.brick(AX1, y, z));
                }
            }
        }
        for (int x = AX0 + 1; x < AX1; x++) {
            for (int z = AZ0 + 1; z < AZ1; z++) {
                set(x, 0, z, "sc_vct");
                set(x, S, z, "office_floor");
                set(x, 2 * S, z, "office_floor");
                set(x, ROOF, z, "un_roof");
            }
        }
        // the coping along the parapet
        for (int x = AX0; x <= AX1; x++) {
            set(x, ROOF + 2, AZ0, "cc_coping");
            set(x, ROOF + 2, AZ1, "cc_coping");
        }
        for (int z = AZ0; z <= AZ1; z++) {
            set(AX0, ROOF + 2, z, "cc_coping");
            set(AX1, ROOF + 2, z, "cc_coping");
        }
        // the call letters on the parapet over the front
        c.row("tv_name_", 14, 18, ROOF + 1, AZ0 - 1, "north", 0.04);
    }

    /** One cell of the outside wall: brick, the bands at the slabs, the windows. */
    private void outer(int x, int y, int z, String out) {
        if (y == 0) {
            set(x, y, z, "floor_concrete");
            return;
        }
        if (y > ROOF) {
            set(x, y, z, c.brick(x, y, z));
            return;
        }
        if (y == S || y == 2 * S || y == ROOF) {
            set(x, y, z, "cc_precast");
            return;
        }
        int k = y / S, dy = y % S;
        boolean front = out.equals("north");
        int along = out.equals("north") || out.equals("south") ? x : z;
        boolean corner = x == AX0 || x == AX1 || z == AZ0 || z == AZ1;
        boolean end = (out.equals("north") || out.equals("south")) ? (x == AX0 || x == AX1) : (z == AZ0 || z == AZ1);
        boolean win;
        if (k == 0) {
            if (front) {
                // the lobby's glass between brick piers; the green room and the conference room windows
                win = x <= Broadcast.AX0 + 38 ? dy >= 1 && dy <= 3 && (x - AX0) % 6 != 0 : dy >= 2 && dy <= 3 && x % 3 != 0;
                if (x == 24 || x == 25) {
                    win = false;
                }
            } else if (out.equals("west")) {
                win = dy >= 2 && dy <= 3 && z > 29 && z < 54 && z % 3 != 0;
            } else if (out.equals("south")) {
                win = dy >= 2 && dy <= 3 && x > 12 && x < 37 && x % 3 != 0;
            } else {
                win = false;
            }
        } else {
            boolean stair = out.equals("west") && z >= 55;
            win = dy >= 2 && dy <= 3 && !stair && along % 4 != 0;
        }
        if (end || !win) {
            set(x, y, z, corner && dy == 4 && k > 0 ? "cc_precast_b" : c.brick(x, y, z));
            return;
        }
        double r = chance();
        String wb = r < 0.12 ? "cc_win_broken" : c.power && r < 0.2 ? "cc_win_lit" : "cc_win";
        if (k == 2 && front && r > 0.85) {
            wb = "boarded_window";
        }
        set(x, y, z, wb + (wb.equals("boarded_window") ? "[axis=x]" : "[facing=" + out + "]"));
    }

    /** The two fire stairs, carried up to huts on the roof. */
    private void stairs() {
        int[] ys = {0, S, 2 * S, ROOF};
        c.stair(ST1_X, ST1_Z, false, ys, "concrete_panel", "un_roof");
        c.stair(ST2_X, ST2_Z, false, ys, "concrete_panel", "un_roof");
        // the dead strip between the newsroom's wall and the first stair, filled
        for (int y = 1; y <= ROOF; y++) {
            for (int z = ST1_Z + 1; z <= ST1_Z + 9; z++) {
                set(COL_X0, y, z, "concrete_panel");
            }
        }
    }

    /** Every floor's walls: the corridor's, the column's, the control rooms'. */
    private void partitions(int y) {
        String lo = "sc_cmu_dado_a", hi = "sc_cmu";
        for (int x = AX0 + 1; x < AX1; x++) {
            if (x < ST1_X || x > ST1_X + 4) {
                c.partX(x, x, HZ0 - 1, y, S, lo, hi);
                c.partX(x, x, HZ1 + 1, y, S, lo, hi);
            }
        }
        // the column: the newsroom's east wall, the control rooms' west wall
        c.partZ(COL_X0 - 1, HZ1 + 2, AZ1 - 1, y, S, lo, hi);
        c.partZ(CTL_X0 - 1, HZ1 + 2, AZ1 - 1, y, S, lo, hi);
        for (int z : new int[] {38, 46, 54}) {
            c.partX(COL_X0, COL_X1, z, y, S, lo, hi);
        }
        // the front: the lobby's east wall and the rooms beside it (offices upstairs)
        c.partZ(45, AZ0 + 1, HZ0 - 2, y, S, lo, hi);
        c.partZ(56, AZ0 + 1, HZ0 - 2, y, S, lo, hi);
        // the corridor's doors at either end and into the column's rooms
        c.doors(ST1_X - 20, y, HZ1 + 1, "fire_door", "south");
        for (int z : new int[] {42, 50, 60}) {
            c.door(COL_X0 - 1, y, z, "flat_door", "west");
        }
        for (int x = AX0 + 1; x < AX1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                if (y > 0) {
                    set(x, y, z, "sc_vct");
                }
            }
        }
        c.sign("staff", 30, y + 3, HZ1 + 1, "north");
    }

    // ------------------------------------------------------------------ the ground floor

    /** The lobby: the doors, the travertine, reception, the logo, the monitor wall, the chairs; the sandbags. */
    private void lobby() {
        for (int x = AX0 + 1; x < 45; x++) {
            for (int z = AZ0 + 1; z < HZ0 - 1; z++) {
                set(x, 0, z, "cc_travertine");
            }
        }
        c.doors(24, 0, AZ0, "shop_door", "north");
        c.doors(12, 0, HZ0 - 1, "shop_door", "north");
        c.doors(36, 0, HZ0 - 1, "flat_door", "north");
        // reception: the desk's curve, the logo on the wall behind
        for (int x = 18; x <= 29; x++) {
            set(x, 1, 19, x == 18 || x == 29 ? "reception_desk" : chance() < 0.1 ? "air" : "reception_desk");
        }
        set(18, 1, 20, "reception_desk");
        set(29, 1, 20, "reception_desk");
        for (int x = 20; x <= 27; x += 3) {
            set(x, 1, 21, "office_chair[facing=north]");
        }
        set(22, 2, 19, "fit_phone[facing=north]");
        c.picture("tv_logo_", 2, 2, 23, 2, HZ0 - 2, "north");
        // the monitor wall, six by three
        c.picture(c.power ? "tv_mwall_on_" : "tv_mwall_", 6, 3, 30, 1, HZ0 - 2, "north");
        // the chairs along the west wall, the awards, the plants
        for (int z = 14; z <= 21; z++) {
            set(AX0 + 1, 1, z, z % 4 == 1 ? "planter" : "hs_chairs[facing=east]");
        }
        for (int x = 40; x <= 43; x++) {
            set(x, 1, HZ0 - 2, "fit_shelves[facing=north]");
            set(x, 2, HZ0 - 2, "fit_shelves[facing=north]");
        }
        c.promo(16, 2, HZ0 - 2, "north");
        c.promo(43, 3, AZ0 + 2, "west");
        // the seats facing the monitor wall, the runner from the doors, the old camera on show
        set(31, 1, 18, "home_sofa_brown_l[facing=south]");
        set(32, 1, 18, "home_sofa_brown_m[facing=south]");
        set(33, 1, 18, "home_sofa_brown_m[facing=south]");
        set(34, 1, 18, "home_sofa_brown_r[facing=south]");
        set(36, 1, 18, "planter");
        set(32, 1, 20, "fit_round_table");
        set(33, 1, 20, "home_books_stack_low[facing=north]");
        for (int z = AZ0 + 1; z <= 18; z++) {
            set(24, 0, z, "office_floor");
            set(25, 0, z, "office_floor");
        }
        set(40, 0, 15, "cc_plaza_band");
        set(40, 1, 15, "tv_cam_lo[facing=north]");
        set(40, 2, 15, "tv_cam_hi[facing=north]");
        // the sandbags inside the doors, the gap at the doors
        for (int x = 19; x <= 30; x++) {
            if (x < 23 || x > 26) {
                set(x, 1, AZ0 + 2, "sandbags[facing=north]");
                if (x % 2 == 0) {
                    set(x, 2, AZ0 + 2, "sandbags[facing=north]");
                }
            }
        }
        set(33, 1, 14, "trade_folding_chair[facing=north]");
        set(34, 1, 14, "crate");
    }

    /** The green room and the conference room beside the lobby. */
    private void frontRooms() {
        // green room: the sofa, the makeup counter and its mirror, the coffee
        for (int x = 46; x <= 55; x++) {
            for (int z = AZ0 + 1; z < HZ0 - 1; z++) {
                set(x, 0, z, "office_floor");
            }
        }
        c.door(50, 0, HZ0 - 1, "flat_door", "north");
        c.sign("green", 51, 3, HZ0, "south");
        set(47, 1, 17, "home_sofa_brown_l[facing=east]");
        set(47, 1, 18, "home_sofa_brown_m[facing=east]");
        set(47, 1, 19, "home_sofa_brown_r[facing=east]");
        for (int x = 49; x <= 54; x++) {
            set(x, 1, AZ0 + 1, "home_counter_white[facing=south]");
            set(x, 2, AZ0 + 1, x % 2 == 0 ? "fit_mirror_long[facing=south]" : "air");
        }
        set(55, 1, 22, "fit_urn[facing=west]");
        set(49, 1, 16, "office_chair[facing=north]");
        set(52, 1, 16, "office_chair[facing=north]");
        // conference: the long table, the chairs, the monitor on its stand, the whiteboard
        for (int x = 57; x <= 67; x++) {
            for (int z = AZ0 + 1; z < HZ0 - 1; z++) {
                set(x, 0, z, "office_floor");
            }
        }
        c.door(62, 0, HZ0 - 1, "flat_door", "north");
        c.sign("conf", 63, 3, HZ0, "south");
        for (int x = 59; x <= 65; x++) {
            set(x, 1, 17, "of_conf_table");
            set(x, 1, 18, "of_conf_table");
            if (x % 2 == 1) {
                set(x, 1, 16, chance() < 0.15 ? "office_chair_fallen[facing=south]" : "office_chair[facing=south]");
                set(x, 1, 19, "office_chair[facing=north]");
            }
        }
        set(66, 1, 17, c.power ? "tv_monitor_on[facing=west]" : "tv_monitor[facing=west]");
        set(57, 2, 17, "of_whiteboard[facing=east]");
    }

    /**
     * The newsroom: the assignment desk at the door with its scanners, the desks in rows, the wire
     * machines along the west wall, the monitors high on the corridor wall, the news director's box.
     */
    private void newsroom() {
        int z0 = HZ1 + 2;
        for (int x = AX0 + 1; x <= NEWS_X1; x++) {
            for (int z = z0; z < AZ1; z++) {
                set(x, 0, z, "office_floor");
            }
        }
        c.sign("news", 22, 3, HZ1 + 1, "north");
        // the assignment desk: desks and scanners in a row, the editors behind them
        for (int x = 12; x <= 26; x++) {
            if (x == 20 || x == 21) {
                continue;
            }
            set(x, 1, z0 + 2, x % 3 == 0 ? "tv_scanner[facing=north]" : x % 3 == 1 ? "home_desk_pc[facing=north]" : "desk[facing=north]");
            if (x % 2 == 0) {
                set(x, 1, z0 + 3, "office_chair[facing=north]");
            }
        }
        // the desks back to back in rows, the aisles between
        for (int row = 0; row < 3; row++) {
            int dz = z0 + 7 + row * 6;
            for (int x = 13; x <= NEWS_X1 - 2; x++) {
                if (x == 20 || x == 21 || x == 28) {
                    continue;
                }
                double r = chance();
                set(x, 1, dz, (r < 0.45 ? "home_desk_pc" : "desk") + "[facing=north]");
                set(x, 1, dz + 1, (chance() < 0.4 ? "home_desk_pc" : "desk") + "[facing=south]");
                if (x % 2 == 1) {
                    set(x, 1, dz - 1, chance() < 0.12 ? "office_chair_fallen[facing=east]" : "office_chair[facing=south]");
                    set(x, 1, dz + 2, "office_chair[facing=north]");
                }
                if (chance() < 0.12) {
                    set(x, 2, dz, chance() < 0.5 ? "home_books_stack_low" : "paper");
                }
            }
        }
        // the wire machines along the west wall, their paper on the floor
        for (int z = z0 + 1; z <= 52; z += 3) {
            set(AX0 + 1, 1, z, "tv_wire[facing=east]");
            if (chance() < 0.6) {
                set(AX0 + 2, 1, z, "paper");
            }
        }
        // the monitors high on the corridor wall, the map and the posters on the back wall
        for (int x = 10; x <= 34; x += 4) {
            if (x >= 18 && x <= 22) {
                continue;
            }
            set(x, 3, z0, (c.power ? (chance() < 0.5 ? "tv_ebs" : "tv_monitor_on") : "tv_monitor") + "[facing=south]");
        }
        c.picture("tv_wx_", 3, 2, 18, 2, AZ1 - 1, "north");
        c.promo(14, 2, AZ1 - 1, "north");
        c.promo(24, 2, AZ1 - 1, "north");
        // the news director's office in the north-east corner, glass over the wainscot
        int ox0 = 28, oz0 = ST2_Z;
        for (int x = ox0; x <= NEWS_X1; x++) {
            set(x, 1, oz0, "cv_wainscot");
            set(x, 2, oz0, "tx_glass[axis=x]");
            set(x, 3, oz0, "tx_glass[axis=x]");
        }
        for (int z = oz0; z < AZ1; z++) {
            set(ox0, 1, z, "cv_wainscot");
            set(ox0, 2, z, "tx_glass[axis=z]");
            set(ox0, 3, z, "tx_glass[axis=z]");
        }
        c.door(32, 0, oz0, "flat_door", "north");
        set(32, 1, 62, "home_desk_pc[facing=north]");
        set(32, 1, 63, "office_chair[facing=north]");
        set(NEWS_X1, 1, 60, "filing_cabinet[facing=west]");
        set(NEWS_X1, 1, 61, "filing_cabinet[facing=west]");
        set(30, 1, AZ1 - 1, c.power ? "tv_monitor_on[facing=north]" : "tv_monitor[facing=north]");
        set(29, 1, 58, "home_sofa_brown_m[facing=east]");
        // a cot where somebody slept at their desk the last nights
        set(16, 1, 60, "sc_cot_head[facing=north]");
        set(16, 1, 61, "sc_cot_foot[facing=north]");
    }

    /** The column between the newsroom and the control rooms: washrooms and the tape library. */
    private void column(int y) {
        for (int x = COL_X0; x <= COL_X1; x++) {
            for (int z = 39; z <= 45; z++) {
                set(x, y, z, "fit_tile_cream");
            }
            for (int z = 47; z <= 53; z++) {
                set(x, y, z, "fit_tile_cream");
            }
        }
        c.sign("men", COL_X0 - 2, y + 3, 41, "west");
        c.sign("women", COL_X0 - 2, y + 3, 49, "west");
        for (int z : new int[] {40, 44, 48, 52}) {
            set(COL_X1, y + 1, z, "fit_toilet_stall[facing=west]");
            set(COL_X1, y + 1, z + 1, "home_sink_white[facing=west]");
        }
        for (int z = 55; z < AZ1; z++) {
            set(COL_X0, y + 1, z, "tv_tapes[facing=east]");
            set(COL_X0, y + 2, z, "tv_tapes[facing=east]");
            set(COL_X1, y + 1, z, "tv_tapes[facing=west]");
            set(COL_X1, y + 2, z, "tv_tapes[facing=west]");
        }
        c.sign("tapes", COL_X0 - 2, y + 3, 59, "west");
    }

    /**
     * The control room: the monitor wall on the east wall, the switcher's row facing it, the audio
     * row behind, the tape decks along the west wall; the door through to the studio under its light.
     */
    private void control() {
        int z0 = HZ1 + 2, z1 = MC_Z0 - 2;
        for (int x = CTL_X0; x < AX1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, "office_floor");
            }
        }
        c.partX(CTL_X0, AX1 - 1, MC_Z0 - 1, 0, S, "tv_acoustic", "tv_acoustic");
        c.doors(50, 0, HZ1 + 1, "fire_door", "south");
        c.sign("control", 52, 3, HZ1 + 1, "north");
        // the monitor wall, six by three, the odd monitors stacked beside it
        c.picture(c.power ? "tv_mwall_on_" : "tv_mwall_", 6, 3, AX1 - 1, 1, z0 + 2, "west");
        for (int z = z0 + 8; z <= z0 + 12; z++) {
            for (int y = 1; y <= 2; y++) {
                set(AX1 - 1, y, z, (c.power && chance() < 0.6 ? "tv_monitor_on" : "tv_monitor") + "[facing=west]");
            }
        }
        // the switcher's row, the director's chair, the audio row behind
        for (int z = z0 + 2; z <= z0 + 11; z++) {
            set(62, 1, z, (c.power ? "tv_switcher_on" : "tv_switcher") + "[facing=west]");
            if (z % 2 == 0) {
                set(61, 1, z, chance() < 0.15 ? "office_chair_fallen[facing=north]" : "office_chair[facing=east]");
            }
            set(57, 1, z, (z % 3 == 0 ? "tv_audio" : z % 3 == 1 ? "desk" : "tv_audio") + "[facing=west]");
            if (z % 3 == 2) {
                set(56, 1, z, "office_chair[facing=east]");
            }
        }
        set(57, 2, z0 + 4, (c.power ? "tv_monitor_on" : "tv_monitor") + "[facing=west]");
        for (int z = z0 + 2; z <= z0 + 11; z += 3) {
            if (empty(57, 2, z)) {
                set(57, 2, z, any("paper", "home_books_stack_low[facing=west]", "fit_phone[facing=west]"));
            }
        }
        // the producer's desk at the back, the clock over the monitor wall, the scripts on the floor
        set(50, 1, z0 + 8, "home_desk_pc[facing=east]");
        set(50, 1, z0 + 9, "desk[facing=east]");
        set(51, 1, z0 + 8, "office_chair[facing=west]");
        set(50, 2, z0 + 9, "fit_phone[facing=east]");
        c.mess(52, z0 + 1, 60, z1, 0, 0.08);
        // the tape decks and racks along the west wall
        for (int z = z0 + 1; z <= z1; z++) {
            set(CTL_X0, 1, z, (z % 2 == 0 ? "tv_vtr" : "tv_rack") + (c.power ? "_on" : "") + "[facing=east]");
            set(CTL_X0, 2, z, (c.power ? "tv_rack_on" : "tv_rack") + "[facing=east]");
        }
        // through to the studio: the doors, the ON AIR light over them on both sides
        c.doors(AX1, 0, 43, "fire_door", "east");
        set(AX1 - 1, 3, 42, (c.power ? "tv_onair_on" : "tv_onair") + "[facing=west]");
        set(AX1 + 1, 3, 42, (c.power ? "tv_onair_on" : "tv_onair") + "[facing=east]");
        set(AX1 + 1, 3, 45, "tv_sgn_quiet_0[facing=east]");
    }

    /** Master control: the desk with its monitors and the EBS encoder, the racks in rows behind. */
    private void master() {
        for (int x = CTL_X0; x < AX1; x++) {
            for (int z = MC_Z0; z < AZ1; z++) {
                set(x, 0, z, "sc_vct");
            }
        }
        c.door(56, 0, MC_Z0 - 1, "fire_door", "north");
        c.sign("master", 58, 3, MC_Z0, "south");
        c.sign("ebs", 62, 3, MC_Z0, "south");
        for (int x = 49; x <= 61; x++) {
            set(x, 1, MC_Z0 + 1, (c.power ? "tv_switcher_on" : "tv_switcher") + "[facing=north]");
            set(x, 1, MC_Z0 + 2, (x == 60 ? (c.power ? "tv_ebs" : "tv_monitor") : c.power && chance() < 0.5 ? "tv_monitor_on" : "tv_monitor") + "[facing=north]");
            if (x % 3 == 0) {
                set(x, 1, MC_Z0, "office_chair[facing=south]");
            }
        }
        for (int row = 0; row < 4; row++) {
            int z = MC_Z0 + 5 + row * 3;
            if (z >= AZ1 - 1) {
                break;
            }
            for (int x = CTL_X0 + 2; x <= 62; x++) {
                if (x == 55) {
                    continue;
                }
                String f = row % 2 == 0 ? "north" : "south";
                String a = chance() < 0.4 ? "tv_vtr" : "tv_rack", on = c.power && chance() < 0.7 ? "_on" : "";
                set(x, 1, z, a + on + "[facing=" + f + "]");
                set(x, 2, z, "tv_rack" + on + "[facing=" + f + "]");
            }
        }
        for (int z = MC_Z0 + 4; z < AZ1; z += 2) {
            set(AX1 - 1, 1, z, "tv_cable");
        }
    }

    // ------------------------------------------------------------------ the second floor

    /** Offices along the front, production and the weather center behind, the edit suites. */
    private void floor2() {
        int y = S;
        // the front: the general manager's corner, sales, promotions, accounts
        for (int x = AX0 + 1; x < AX1; x++) {
            for (int z = AZ0 + 1; z < HZ0 - 1; z++) {
                set(x, y, z, "office_floor");
            }
        }
        c.partZ(17, AZ0 + 1, HZ0 - 2, y, S, "drywall", "drywall");
        c.door(12, y, HZ0 - 1, "flat_door", "north");
        c.door(30, y, HZ0 - 1, "flat_door", "north");
        c.door(50, y, HZ0 - 1, "flat_door", "north");
        c.door(62, y, HZ0 - 1, "flat_door", "north");
        c.sign("gm", 12, y + 3, HZ0, "south");
        c.sign("sales", 31, y + 3, HZ0, "south");
        set(11, y + 1, 16, "home_desk_pc[facing=north]");
        set(11, y + 1, 17, "office_chair[facing=north]");
        set(8, y + 1, 21, "home_sofa_brown_l[facing=east]");
        set(8, y + 1, 22, "home_sofa_brown_r[facing=east]");
        set(15, y + 1, 13, "fit_shelves[facing=south]");
        set(16, y + 1, 13, "filing_cabinet[facing=south]");
        for (int x = 19; x <= 43; x += 3) {
            for (int z : new int[] {15, 19}) {
                set(x, y + 1, z, (chance() < 0.4 ? "home_desk_pc" : "desk") + "[facing=north]");
                set(x, y + 1, z + 1, "office_chair[facing=north]");
            }
        }
        for (int x = 46; x <= 55; x += 3) {
            set(x, y + 1, 16, "desk[facing=north]");
            set(x, y + 1, 17, "office_chair[facing=north]");
        }
        for (int z = 14; z <= 21; z += 2) {
            set(66, y + 1, z, "filing_cabinet[facing=west]");
        }
        set(60, y + 1, 17, "home_desk_pc[facing=north]");
        set(60, y + 1, 18, "office_chair[facing=north]");
        c.promo(20, y + 2, HZ0 - 2, "north");
        c.promo(47, y + 2, AZ0 + 1, "south");
        // production and the weather center behind the corridor
        int z0 = HZ1 + 2;
        for (int x = AX0 + 1; x <= NEWS_X1; x++) {
            for (int z = z0; z < AZ1; z++) {
                set(x, y, z, "office_floor");
            }
        }
        c.sign("weather", 22, y + 3, HZ1 + 1, "north");
        c.picture("tv_wx_", 3, 2, 14, y + 2, z0, "south");
        for (int x = 12; x <= 26; x++) {
            if (x == 20 || x == 21) {
                continue;
            }
            set(x, y + 1, z0 + 3, (x % 4 == 0 ? "tv_monitor" + (c.power ? "_on" : "") : x % 4 == 1 ? "home_desk_pc" : "desk") + "[facing=south]");
            if (x % 2 == 1) {
                set(x, y + 1, z0 + 4, "office_chair[facing=north]");
            }
        }
        for (int row = 0; row < 3; row++) {
            int dz = z0 + 9 + row * 6;
            for (int x = 13; x <= 26; x++) {
                if (x == 20) {
                    continue;
                }
                set(x, y + 1, dz, "desk[facing=north]");
                if (x % 3 == 0) {
                    set(x, y + 1, dz + 1, "office_chair[facing=north]");
                }
            }
        }
        c.promo(AX0 + 1, y + 2, 40, "east");
        c.promo(AX0 + 1, y + 2, 46, "east");
        column(y);
        // the edit suites off their hall
        for (int x = CTL_X0; x < AX1; x++) {
            for (int z = z0; z < AZ1; z++) {
                set(x, y, z, x <= 48 ? "sc_vct" : "office_floor");
            }
        }
        c.door(47, y, HZ1 + 1, "flat_door", "south");
        c.sign("edit", 49, y + 3, HZ1 + 1, "north");
        c.partZ(49, z0, AZ1 - 1, y, S, "tv_acoustic", "tv_acoustic");
        for (int z : new int[] {38, 47, 56}) {
            c.partX(50, AX1 - 1, z, y, S, "tv_acoustic", "tv_acoustic");
        }
        int[] starts = {z0, 39, 48, 57};
        for (int s : starts) {
            c.door(49, y, s + 2, "flat_door", "west");
            int dz = s + 4;
            for (int x = 58; x <= 62; x++) {
                set(x, y + 1, dz, x == 60 ? "tv_audio[facing=west]" : (x % 2 == 0 ? "tv_vtr" : "tv_monitor") + (c.power ? "_on" : "") + "[facing=north]");
            }
            set(66, y + 1, dz, "tv_tapes[facing=west]");
            set(66, y + 2, dz, "tv_tapes[facing=west]");
            set(60, y + 1, dz - 1, "office_chair[facing=south]");
            set(51, y + 1, s + 1, "tv_case[facing=east]");
        }
        for (int z = z0; z < AZ1; z += 9) {
            c.promo(CTL_X0, y + 2, z + 4, "east");
        }
    }

    // ------------------------------------------------------------------ the third floor

    /**
     * The engineers' shop and the break room, the FM station, and the front room where the staff
     * lived at the end: cots and water, the emergency desk at the windows still wired to master control.
     */
    private void floor3() {
        int y = 2 * S;
        int z0 = HZ1 + 2;
        for (int x = AX0 + 1; x < AX1; x++) {
            for (int z = z0; z < AZ1; z++) {
                set(x, y, z, x < COL_X0 ? (z < 47 ? "sc_vct" : "fit_tile_cream") : x >= CTL_X0 && x <= 48 ? "sc_vct" : "office_floor");
            }
        }
        // the shop: benches round the walls, the parts shelves, the test gear on its carts
        c.partX(AX0 + 1, NEWS_X1, 46, y, S, "drywall", "drywall");
        c.door(20, y, 46, "flat_door", "north");
        c.sign("eng", 22, y + 3, HZ1 + 1, "north");
        for (int x = 12; x <= 34; x++) {
            set(x, y + 1, z0 + 1, x % 5 == 0 ? "tv_rack[facing=south]" : "home_workbench[facing=south]");
            if (x % 4 == 0) {
                set(x, y + 2, z0 + 1, any("tv_monitor[facing=south]", "pk_toolbox[facing=south]", "air"));
            }
        }
        for (int z = z0 + 4; z <= 44; z++) {
            set(AX0 + 1, y + 1, z, "home_garage_shelf[facing=east]");
            set(NEWS_X1, y + 1, z, z % 3 == 0 ? "tv_case[facing=west]" : "home_garage_shelf[facing=west]");
        }
        for (int x = 14; x <= 30; x += 8) {
            set(x, y + 1, 38, "home_workbench[facing=north]");
            set(x + 1, y + 1, 38, "home_workbench[facing=north]");
            set(x, y + 2, 38, "tv_monitor[facing=north]");
        }
        // the break room: the counter and the fridge, the tables, the vending machine
        for (int x = 13; x <= 24; x++) {
            set(x, y + 1, AZ1 - 1, x == 13 ? "home_fridge[facing=north]" : x == 18 ? "home_sink_white[facing=north]" : "home_counter_white[facing=north]");
        }
        set(13, y + 2, AZ1 - 1, "home_fridge_top[facing=north]");
        set(NEWS_X1, y + 1, 50, "store_vending[facing=west]");
        set(NEWS_X1, y + 2, 50, "store_vending_top[facing=west]");
        for (int x = 16; x <= 30; x += 6) {
            set(x, y + 1, 54, "fit_round_table");
            set(x - 1, y + 1, 54, "pk_chair_dining[facing=east]");
            set(x + 1, y + 1, 54, "pk_chair_dining[facing=west]");
        }
        c.sign("break", 30, y + 3, 47, "south");
        column(y);
        // the FM station: on-air, production, the record library
        c.partZ(49, z0, AZ1 - 1, y, S, "tv_acoustic", "tv_acoustic");
        c.partX(50, AX1 - 1, 42, y, S, "tv_acoustic", "tv_acoustic");
        c.partX(50, AX1 - 1, 54, y, S, "tv_acoustic", "tv_acoustic");
        for (int x = 56; x <= 62; x++) {
            set(x, y + 2, 42, "tx_glass[axis=x]");
        }
        c.door(47, y, HZ1 + 1, "flat_door", "south");
        c.sign("radio", 49, y + 3, HZ1 + 1, "north");
        c.door(49, y, 35, "flat_door", "west");
        c.door(49, y, 48, "flat_door", "west");
        c.door(49, y, 60, "flat_door", "west");
        set(48, y + 3, 35, (c.power ? "tv_onair_on" : "tv_onair") + "[facing=west]");
        // on-air: the board, the turntables either side, the carts, the mics
        for (int x = 56; x <= 62; x++) {
            set(x, y + 1, 36, x == 56 || x == 62 ? "tv_turntables[facing=north]" : x == 59 ? "tv_console[facing=north]" : "tv_mic[facing=north]");
        }
        set(59, y + 1, 37, "office_chair[facing=north]");
        for (int z = z0 + 1; z <= 40; z += 2) {
            set(AX1 - 1, y + 1, z, "tv_carts[facing=west]");
            set(AX1 - 1, y + 2, z, "tv_records[facing=west]");
        }
        c.promo(55, y + 2, z0, "south");
        // production: the board and the tape machines, the window through to on-air
        for (int x = 54; x <= 64; x++) {
            set(x, y + 1, 44, x == 59 ? "tv_console[facing=south]" : (x % 2 == 0 ? "tv_vtr" : "tv_carts") + "[facing=south]");
        }
        set(59, y + 1, 45, "office_chair[facing=north]");
        // the record library in rows
        c.sign("records", 52, y + 3, 55, "south");
        for (int x = 52; x <= 66; x += 3) {
            for (int z = 57; z <= 64; z++) {
                set(x, y + 1, z, "tv_records[facing=" + (x % 2 == 0 ? "east" : "west") + "]");
                set(x, y + 2, z, chance() < 0.85 ? "tv_records[facing=" + (x % 2 == 0 ? "east" : "west") + "]" : "air");
            }
        }
        // the front: where the staff lived, the emergency desk at the windows
        for (int x = AX0 + 1; x < AX1; x++) {
            for (int z = AZ0 + 1; z < HZ0 - 1; z++) {
                set(x, y, z, "office_floor");
            }
        }
        c.doors(30, y, HZ0 - 1, "flat_door", "north");
        c.sign("ebs", 26, y + 3, HZ0 - 2, "north");
        for (int x = 24; x <= 32; x++) {
            set(x, y + 1, AZ0 + 2, x == 28 ? "tv_console[facing=south]" : x == 27 ? "tv_mic[facing=south]" : x % 2 == 0 ? (c.power ? "tv_ebs" : "tv_monitor") + "[facing=south]" : "tv_carts[facing=south]");
        }
        set(28, y + 1, AZ0 + 3, "office_chair[facing=north]");
        set(29, y + 1, AZ0 + 4, "home_blood_pool");
        for (int x = AX0 + 2; x <= 20; x += 2) {
            set(x, y + 1, 15, "sc_cot_head[facing=south]");
            set(x, y + 1, 16, "sc_cot_foot[facing=south]");
            set(x, y + 1, 20, "sc_cot_head[facing=north]");
            set(x, y + 1, 19, "sc_cot_foot[facing=north]");
        }
        for (int x = 36; x <= 44; x++) {
            set(x, y + 1, 21, x % 3 == 0 ? "sc_cases_water" : "crate");
            if (x % 2 == 0) {
                set(x, y + 2, 21, "sc_cases_water");
            }
        }
        for (int x = 48; x <= 64; x += 3) {
            set(x, y + 1, 17, "sc_cot_head[facing=east]");
            set(x + 1, y + 1, 17, "sc_cot_foot[facing=east]");
        }
        set(66, y + 1, 14, "bunk_st_generator[facing=west]");
        for (int x = 46; x <= 66; x += 4) {
            set(x, y + 1, 22, "trash_bag[facing=north]");
        }
        c.mess(AX0 + 1, AZ0 + 1, AX1 - 1, HZ0 - 2, y, 0.06);
        // the sandbags across the corridor at both stairs' doors
        for (int x = ST1_X; x <= ST1_X + 4; x++) {
            set(x, y + 1, HZ0, x == ST1_X + 2 ? "air" : "sandbags[facing=north]");
        }
        c.set(20, y + 3, HZ0 - 1, "tv_spray_third_0[facing=north]");
        c.set(21, y + 3, HZ0 - 1, "tv_spray_third_1[facing=north]");
        c.set(22, y + 3, HZ0 - 1, "tv_spray_third_2[facing=north]");
    }

    // ------------------------------------------------------------------ the studio wing

    /** The wing's walls, its floor and roof, the mural on its front. */
    private void wing() {
        for (int y = 0; y <= ROOF_B + 1; y++) {
            for (int x = AX1; x <= BX1; x++) {
                set(x, y, BZ0, y == 0 ? "floor_concrete" : c.brick(x, y, BZ0));
                set(x, y, AZ1, y == 0 ? "floor_concrete" : c.brick(x, y, AZ1));
            }
            for (int z = BZ0; z <= AZ1; z++) {
                set(BX1, y, z, y == 0 ? "floor_concrete" : c.brick(BX1, y, z));
            }
        }
        for (int x = AX1 + 1; x < BX1; x++) {
            for (int z = BZ0 + 1; z < AZ1; z++) {
                set(x, 0, z, z <= DOCK_Z1 ? "floor_concrete" : "tv_floor");
                set(x, ROOF_B, z, "un_roof");
            }
            set(x, ROOF_B + 2, BZ0, "cc_coping");
            set(x, ROOF_B + 2, AZ1, "cc_coping");
        }
        for (int z = BZ0; z <= AZ1; z++) {
            set(BX1, ROOF_B + 2, z, "cc_coping");
        }
        for (int x = AX1 + 1; x < BX1; x++) {
            set(x, 5, BZ0, "cc_precast");
        }
        // the air handlers' louvres high in the wing's walls
        for (int x = AX1 + 6; x < BX1 - 2; x += 9) {
            set(x, 9, BZ0, "hvac");
            set(x + 1, 9, BZ0, "hvac");
        }
        for (int z = BZ0 + 6; z < AZ1 - 2; z += 9) {
            set(BX1, 9, z, "hvac");
        }
        c.picture("tv_mural_", 12, 2, 76, 7, BZ0 - 1, "north");
        c.picture("tv_logo_", 2, 2, BX1 + 1, 7, 50, "east");
        // the partition between the dock and the studio, the elephant doors through it
        for (int x = AX1 + 1; x < BX1; x++) {
            for (int y = 1; y < ROOF_B; y++) {
                set(x, y, SA_Z0 - 1, x >= 89 && x <= 92 && y <= 4 ? "air" : "concrete_panel");
            }
        }
        c.doors(AX1, 0, HZ0, "fire_door", "east");
        c.sign("dock", AX1 + 2, 3, HZ0 - 1, "east");
    }

    /** The scene dock: the roll-up door, the flats stacked, the road cases, the spare set pieces. */
    private void dock() {
        for (int z = 26; z <= 29; z++) {
            for (int y = 1; y <= 4; y++) {
                set(BX1, y, z, "garage_door[facing=east]");
            }
        }
        c.door(BX1, 0, 23, "fire_door", "east");
        for (int x = 71; x <= 104; x++) {
            if (x % 6 == 2) {
                continue;
            }
            set(x, 1, BZ0 + 1, "tv_flat[facing=south]");
            if (chance() < 0.6) {
                set(x, 2, BZ0 + 1, "tv_flat[facing=south]");
            }
        }
        for (int k = 0; k < 18; k++) {
            int x = 72 + c.rng.nextInt(36), z = 24 + c.rng.nextInt(9);
            if ((x >= 88 && x <= 93) || z == 26 || z == 27 || x > 108) {
                continue;
            }
            set(x, 1, z, any("tv_case[facing=north]", "tv_case[facing=east]", "crate", "tv_cable", "trade_stepladder[facing=north]", "tv_desk_end[facing=north]"));
        }
        for (int x = 98; x <= 104; x++) {
            set(x, 1, DOCK_Z1, "tv_desk[facing=north]");
        }
        set(110, 1, 22, "tv_case[facing=west]");
        set(110, 2, 22, "tv_case[facing=west]");
    }

    /**
     * Studio A: the acoustic panels round the walls, the NewsCenter 6 set on its riser under the
     * backdrop, the three cameras where they stopped, the weather wall, the floor monitors, and the
     * lighting grid overhead with its fresnels.
     */
    private void studio() {
        int x0 = AX1 + 1, x1 = BX1 - 1, z0 = SA_Z0, z1 = AZ1 - 1;
        for (int y = 1; y <= 7; y++) {
            for (int z = z0; z <= z1; z++) {
                if (empty(x0, y, z)) {
                    set(x0, y, z, "tv_acoustic");
                }
                set(x1, y, z, z >= 40 && z <= 52 ? "tv_chroma" : "tv_acoustic");
            }
            for (int x = x0; x <= x1; x++) {
                set(x, y, z1, "tv_acoustic");
            }
        }
        // the doorway from the control room kept open through the panels
        for (int z = 43; z <= 44; z++) {
            for (int y = 1; y <= 2; y++) {
                if (c.get(x0, y, z) != null && c.get(x0, y, z).equals("tv_acoustic")) {
                    set(x0, y, z, "air");
                }
            }
        }
        // the set: the riser, the backdrop, the desk, the chairs
        for (int x = 84; x <= 97; x++) {
            for (int z = 54; z <= z1; z++) {
                set(x, 0, z, "office_floor");
            }
        }
        c.picture("tv_set_", 8, 4, 87, 2, z1 - 1, "north");
        for (int x = 87; x <= 94; x++) {
            set(x, 1, z1 - 1, "tv_cyc");
        }
        for (int x = 87; x <= 94; x++) {
            set(x, 1, 58, x == 87 || x == 94 ? "tv_desk_end[facing=north]" : "tv_desk[facing=north]");
            if (x % 2 == 0 && x != 94) {
                set(x, 1, 59, chance() < 0.2 ? "office_chair_fallen[facing=east]" : "office_chair[facing=north]");
            }
        }
        set(90, 1, 57, "home_blood_pool");
        set(91, 1, 56, "paper");
        set(89, 1, 56, "paper");
        // the cameras, aimed at the desk, the floor monitors facing the anchors
        for (int x : new int[] {83, 90, 98}) {
            int z = 46 + c.rng.nextInt(3);
            set(x, 1, z, "tv_cam_lo[facing=south]");
            set(x, 2, z, "tv_cam_hi[facing=" + (x == 83 ? "east" : x == 98 ? "west" : "south") + "]");
            set(x, 1, z - 1, "tv_cable");
        }
        set(84, 1, 53, (c.power ? "tv_monitor_on" : "tv_monitor") + "[facing=south]");
        set(96, 1, 53, (c.power ? "tv_ebs" : "tv_monitor") + "[facing=south]");
        // the weather wall: the chroma panels, the monitor the forecaster watched
        set(x1 - 3, 1, 44, (c.power ? "tv_monitor_on" : "tv_monitor") + "[facing=east]");
        c.picture("tv_wx_", 3, 2, x1, 2, 55, "west");
        // road cases and cable along the walls
        for (int k = 0; k < 10; k++) {
            int x = x0 + 2 + c.rng.nextInt(x1 - x0 - 4), z = z0 + 1 + c.rng.nextInt(8);
            if ((x >= 88 && x <= 93) || !empty(x, 1, z)) {
                continue;
            }
            set(x, 1, z, any("tv_case[facing=north]", "tv_cable", "trade_stepladder[facing=east]"));
        }
        // the grid: pipes along z every three, the cross pipes, the fresnels hung off them
        int gy = 8;
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                double r = chance();
                String f = z < 54 ? "south" : "north";
                set(x, gy, z, r < 0.22 ? (c.power && r < 0.05 ? "tv_fresnel_on" : "tv_fresnel") + "[facing=" + f + "]" : "tv_pipe[axis=z]");
            }
        }
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if (empty(x, gy, z0 + 2)) {
                set(x, gy, z0 + 2, "tv_pipe[axis=x]");
            }
            if (empty(x, gy, z1 - 2)) {
                set(x, gy, z1 - 2, "tv_pipe[axis=x]");
            }
        }
        for (int x = x0 + 1; x <= x1 - 1; x += 4) {
            for (int z = z0 + 1; z <= z1 - 1; z += 4) {
                if (c.power && chance() < 0.3) {
                    set(x, ROOF_B - 1, z, "fit_highbay_on");
                } else if (chance() < 0.5) {
                    set(x, ROOF_B - 1, z, "fit_highbay");
                }
            }
        }
        // the interview set by the west wall: flats behind, two chairs, the table, a plant
        for (int z = 53; z <= 61; z++) {
            set(x0 + 1, 1, z, "tv_flat[facing=east]");
            set(x0 + 1, 2, z, "tv_flat[facing=east]");
            set(x0 + 1, 3, z, z % 2 == 0 ? "tv_flat[facing=east]" : "air");
            for (int x = x0 + 2; x <= x0 + 8; x++) {
                set(x, 0, z, "office_floor");
            }
        }
        set(x0 + 3, 1, 55, "home_armchair_brown[facing=east]");
        set(x0 + 3, 1, 59, "home_armchair_brown[facing=east]");
        set(x0 + 4, 1, 57, "fit_round_table");
        set(x0 + 2, 1, 61, "planter");
        set(x0 + 7, 1, 57, "tv_cam_lo[facing=west]");
        set(x0 + 7, 2, 57, "tv_cam_hi[facing=west]");
        // the dimmer racks and the lighting desk along the dock wall
        for (int x = 100; x <= 110; x++) {
            set(x, 1, z0, (c.power ? "tv_rack_on" : "tv_rack") + "[facing=south]");
            set(x, 2, z0, "tv_rack[facing=south]");
        }
        set(76, 1, z0 + 1, "tv_audio[facing=south]");
        set(77, 1, z0 + 1, "tv_audio[facing=south]");
        set(76, 1, z0 + 2, "office_chair[facing=north]");
        // tape marks on the floor where the cameras stood for the six o'clock
        for (int k = 0; k < 14; k++) {
            int x = x0 + 4 + c.rng.nextInt(x1 - x0 - 8), z = z0 + 6 + c.rng.nextInt(16);
            if (c.get(x, 0, z) != null && c.get(x, 0, z).equals("tv_floor")) {
                set(x, 0, z, "tv_floor_tape");
            }
        }
        c.sign("studio", 94, 3, SA_Z0 - 2, "north");
        set(88, 5, SA_Z0 - 2, (c.power ? "tv_onair_on" : "tv_onair") + "[facing=north]");
    }

    // ------------------------------------------------------------------ the roofs

    /**
     * The block's roof: the HVAC units, the small dishes, the microwave mast; the steel steps down
     * to the studio wing's roof and the news helicopter's pad on it.
     */
    private void roofs() {
        int y = ROOF + 1;
        for (int x = 14; x <= 34; x += 10) {
            for (int z = 34; z <= 44; z += 10) {
                c.fill(x, y, z, x + 2, y + 1, z + 1, "hvac");
            }
        }
        for (int x = 50; x <= 62; x += 4) {
            set(x, y, 16, "tv_dish_small[facing=" + (x % 8 == 2 ? "north" : "east") + "]");
        }
        // a short mast with a drum on it
        for (int yy = y; yy <= y + 6; yy++) {
            set(60, yy, 40, "st_lt_mast");
        }
        set(60, y + 7, 40, "tv_beacon" + (c.power ? "_lit" : ""));
        set(60, y + 5, 39, "tv_mw[facing=north]");
        set(61, y + 4, 40, "tv_mw[facing=east]");
        // the steps down to the wing's roof, through a gap in the parapet
        for (int z = 58; z <= 59; z++) {
            set(AX1, ROOF + 1, z, "air");
            set(AX1, ROOF + 2, z, "air");
            set(AX1 + 1, ROOF, z, "steel_stairs[facing=west]");
            set(AX1 + 2, ROOF - 1, z, "steel_stairs[facing=west]");
            set(AX1 + 3, ROOF - 2, z, "steel_stairs[facing=west]");
            c.fill(AX1 + 1, ROOF_B + 1, z, AX1 + 1, ROOF - 1, z, "steel_girder");
            c.fill(AX1 + 2, ROOF_B + 1, z, AX1 + 2, ROOF - 2, z, "steel_girder");
        }
        for (int x = AX1 + 1; x <= AX1 + 3; x++) {
            set(x, ROOF + 2 - (x - AX1), 57, "tv_rail[facing=north]");
            set(x, ROOF + 2 - (x - AX1), 60, "tv_rail[facing=south]");
        }
        // the pad: seven by seven tiles of the hospital's H, the helicopter on it most days
        int hx = 87, hz = 37;
        for (int i = 0; i < 49; i++) {
            set(hx + i % 7, ROOF_B, hz + i / 7, "hs_pad_h_" + i);
        }
        if (chance() < 0.55) {
            p().vehicle(hx + 3.5, ROOF_B + 1, hz + 3.5, 0f, "heli_hornbill", c.rng.nextLong());
        }
        for (int x = 74; x <= 108; x += 8) {
            set(x, ROOF_B + 1, 62, "hvac");
            set(x + 1, ROOF_B + 1, 62, "hvac");
        }
        c.sign("climb", 104, ROOF_B + 1, 30, "north");
    }

    private com.zomboidcraft.world.city.Placer p() {
        return c.p;
    }
}
