package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.PowerStation.AX0;
import static com.zomboidcraft.world.county.PowerStation.AX1;
import static com.zomboidcraft.world.county.PowerStation.AZ0;
import static com.zomboidcraft.world.county.PowerStation.AZ1;
import static com.zomboidcraft.world.county.PowerStation.BROOF;
import static com.zomboidcraft.world.county.PowerStation.BZ1;
import static com.zomboidcraft.world.county.PowerStation.MROOF;
import static com.zomboidcraft.world.county.PowerStation.MZ0;
import static com.zomboidcraft.world.county.PowerStation.ROOF_A;
import static com.zomboidcraft.world.county.PowerStation.S;
import static com.zomboidcraft.world.county.PowerStation.TRIP;
import static com.zomboidcraft.world.county.PowerStation.TROOF;
import static com.zomboidcraft.world.county.PowerStation.TX1;

/**
 * The plant's buildings along the street and behind it.
 *
 * <p>The plant office: the lobby with the time clock, the safety board and the model of the
 * station in its case; the control room beside it looking into the turbine hall through its
 * glass, the benchboards and the vertical boards, the one-line diagram on the wall; behind the
 * corridor first aid, the locker room and the lunch room. Upstairs the superintendent, the
 * engineers, records and the conference room; on the top floor the chemical lab, the dispatcher's
 * radio room, the stores, and the room the night shift slept in at the end.
 *
 * <p>The turbine hall: three turbine-generators in a row down its length under the 50-ton crane,
 * Unit 2 opened for an overhaul that never finished, its top casing on the cribbing beside it and
 * the rotor showing; the steam mains coming through the back wall from the boiler house. The
 * boiler house: four boilers with their burners along the firing aisle, the drums on top, the
 * flues behind going up into the four stacks; grating levels round them, the tripper floor with
 * the coal belt over the bunkers. The machine shop and the stores behind the office.
 */
final class PowerHalls {
    /** The office's corridor (between its walls at z 20 and 24), the lobby's east wall, the stair. */
    static final int CZ0 = 21, CZ1 = 23, LX1 = 20, ST_X = 22, ST_Z = 24;
    /** The units' casings across the hall, each unit's west end (24 long), the crane. */
    static final int UZ0 = 23, UZ1 = 29, CRANE_X = 84, CRANE_Y = 21;
    static final int[] UNIT_X = {46, 72, 98};
    /** The boiler house: the firing aisle's end, the boilers (14 wide), the stacks' row, the levels. */
    static final int BZ0 = 54, BZB = 75, STACK_Z = 84, LEV1 = 11, LEV2 = 22;
    static final int[] BOIL_X = {45, 64, 83, 102};
    /** The boiler house stair, the steam header in the hall, where the coal belt comes in. */
    static final int BST_X = 119, BST_Z = 56, HEAD_Z = 34, HEAD_Y = 25, BELT_X = 52, BELT_Z = 70;

    private final PowerStation c;

    PowerHalls(PowerStation c) {
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

    void build() {
        office();
        hall();
        boilerHouse();
        stacks();
        shop();
    }

    // ================================================================== the plant office

    private void office() {
        officeShell();
        c.stair(ST_X, ST_Z, false, new int[] {0, S, 2 * S, ROOF_A}, "concrete_panel", "un_roof");
        for (int k = 0; k < 3; k++) {
            officeWalls(k * S);
        }
        lobby();
        control();
        backRooms();
        floor2();
        floor3();
        officeRoof();
    }

    /** Brick with a limestone belt at each floor, steel sash in pairs, the portal and the carved panel. */
    private void officeShell() {
        for (int y = 0; y <= ROOF_A + 2; y++) {
            for (int x = AX0; x <= AX1; x++) {
                officeOuter(x, y, AZ0, "north");
                officeOuter(x, y, AZ1, "south");
            }
            for (int z = AZ0 + 1; z < AZ1; z++) {
                officeOuter(AX0, y, z, "west");
                // the east wall is the turbine hall's: brick, with the control room's and the offices' glass into the hall
                boolean glass = y % S >= 2 && y % S <= 3 && y < ROOF_A && z >= AZ0 + 2 && z <= 19;
                set(AX1, y, z, y == 0 ? "floor_concrete" : glass ? "tx_glass[axis=z]" : c.brick(AX1, y, z));
            }
        }
        for (int x = AX0 + 1; x < AX1; x++) {
            for (int z = AZ0 + 1; z < AZ1; z++) {
                set(x, 0, z, "sc_vct");
                set(x, S, z, "office_floor");
                set(x, 2 * S, z, "office_floor");
                set(x, ROOF_A, z, "un_roof");
            }
        }
        for (int x = AX0; x <= AX1; x++) {
            set(x, ROOF_A + 2, AZ0, "cc_coping");
            set(x, ROOF_A + 2, AZ1, "cc_coping");
        }
        for (int z = AZ0; z <= AZ1; z++) {
            set(AX0, ROOF_A + 2, z, "cc_coping");
        }
        // the portal: stone round the doors, the carved panel over them, the seal above that
        int dx = 13;
        for (int y = 1; y <= 4; y++) {
            set(dx - 1, y, AZ0, "stone_trim");
            set(dx + 2, y, AZ0, "stone_trim");
        }
        for (int x = dx - 1; x <= dx + 2; x++) {
            set(x, 4, AZ0, "stone_trim");
        }
        c.doors(dx, 0, AZ0, "shop_door", "north");
        c.picture("pw_glp_", 8, 2, dx - 3, 5, AZ0, "north");
        for (int x = dx - 3; x <= dx + 4; x++) {
            for (int y = 7; y <= 9; y++) {
                set(x, y, AZ0, y == 9 ? "stone_trim" : c.brick(x, y, AZ0));
            }
        }
        c.picture("pw_logo_", 2, 2, dx, 7, AZ0 - 1, "north");
        // the back door out to the alley, from the locker room
        c.door(17, 0, AZ1, "flat_door", "south");
    }

    private void officeOuter(int x, int y, int z, String out) {
        if (y == 0) {
            set(x, y, z, "floor_concrete");
            return;
        }
        if (y == 1) {
            set(x, y, z, "brick_dark");
            return;
        }
        if (y == ROOF_A + 1) {
            set(x, y, z, out.equals("east") ? c.brick(x, y, z) : "cv_cornice_stone[facing=" + out + "]");
            return;
        }
        if (y > ROOF_A) {
            set(x, y, z, c.brick(x, y, z));
            return;
        }
        if (y == S || y == 2 * S) {
            set(x, y, z, "cv_belt[facing=" + out + "]");
            return;
        }
        int dy = y % S;
        int along = out.equals("north") || out.equals("south") ? x : z;
        boolean end = out.equals("north") || out.equals("south") ? x == AX0 || x == AX1 : z == AZ0 || z == AZ1;
        boolean win = dy >= 2 && dy <= 3 && Math.floorMod(along - 1, 3) != 0;
        if (out.equals("south") && along >= ST_X && along <= ST_X + 4) {
            win = false;
        }
        if (end || !win) {
            set(x, y, z, c.brick(x, y, z));
            return;
        }
        double r = chance();
        String s = r < 0.2 ? "pw_sash_broken" : c.power && r < 0.3 ? "pw_sash_lit" : "pw_sash";
        if (y < S && out.equals("north") && x > LX1) {
            // the control room's windows, boarded by the crew
            s = chance() < 0.75 ? "boarded_window" : s;
        }
        set(x, y, z, s.equals("boarded_window") ? s + "[axis=" + (out.equals("north") || out.equals("south") ? "x" : "z") + "]"
                : s + "[facing=" + out + "]");
    }

    /** One floor's partitions: the corridor's walls, the rooms either side, their doors. */
    private void officeWalls(int y) {
        String lo = "sc_cmu_dado_a", hi = "sc_cmu";
        for (int x = AX0 + 1; x < AX1; x++) {
            c.partX(x, x, CZ0 - 1, y, S, lo, hi);
            if (x < ST_X || x > ST_X + 4) {
                c.partX(x, x, CZ1 + 1, y, S, lo, hi);
            }
            for (int z = CZ0; z <= CZ1; z++) {
                set(x, y, z, y == 0 ? "sc_vct" : "office_floor");
            }
        }
        c.partZ(LX1 + 1, AZ0 + 1, CZ0 - 2, y, S, lo, hi);
        c.partZ(13, CZ1 + 2, AZ1 - 1, y, S, lo, hi);
        c.partZ(ST_X + 5, CZ1 + 2, ST_Z + 9, y, S, lo, hi);
        c.partZ(LX1 + 1, CZ1 + 2, AZ1 - 1, y, S, lo, hi);
        c.door(16, y, CZ1 + 1, "flat_door", "north");
        c.door(9, y, CZ1 + 1, "flat_door", "north");
        c.door(33, y, CZ1 + 1, "flat_door", "north");
        if (y > 0) {
            c.door(14, y, CZ0 - 1, "flat_door", "south");
            c.door(30, y, CZ0 - 1, "flat_door", "south");
        }
        // the corridor's end at the hall: on the ground floor the doors through, upstairs a window
        if (y == 0) {
            c.doors(AX1, y, CZ0, "fire_door", "east");
        }
        c.ceiling(y + S, AX0 + 1, CZ0, AX1 - 1, CZ1, 0.3);
    }

    // ------------------------------------------------------------------ the ground floor

    /** The lobby: terrazzo, the guard's desk, the model, the time clock and the hard hats, the board; the sandbags. */
    private void lobby() {
        for (int x = AX0 + 1; x <= LX1; x++) {
            for (int z = AZ0 + 1; z < CZ0 - 1; z++) {
                set(x, 0, z, (x + z) % 7 == 0 ? "cv_hex_border" : "cv_terrazzo");
            }
        }
        c.doors(13, 0, CZ0 - 1, "fire_door", "south");
        set(8, 1, 13, "reception_desk[facing=north]");
        set(9, 1, 13, "reception_desk[facing=north]");
        set(8, 1, 15, "office_chair[facing=north]");
        set(18, 1, 12, "pw_model");
        set(AX0 + 1, 3, 11, "cv_portrait[facing=east]");
        set(AX0 + 1, 3, 15, "cv_portrait_b[facing=east]");
        set(8, 2, CZ0 - 2, "pw_timeclock[facing=north]");
        set(9, 2, CZ0 - 2, "pw_hardhats[facing=north]");
        set(10, 2, CZ0 - 2, "pw_hardhats[facing=north]");
        c.picture("pw_safety_", 3, 2, 16, 2, CZ0 - 2, "north");
        c.sign("visitors", 16, 4, CZ0 - 2, "north");
        for (int z = 14; z <= 18; z += 2) {
            set(LX1, 1, z, "cv_hall_bench[facing=west]");
        }
        // the crew's sandbags inside the doors, a gap to come in by
        for (int x = 10; x <= 17; x++) {
            if (x == 13 || x == 14) {
                continue;
            }
            set(x, 1, AZ0 + 2, "sandbags[facing=north]");
            if (x != 12 && x != 15) {
                set(x, 2, AZ0 + 2, "sandbags[facing=north]");
            }
        }
        c.ceiling(S, AX0 + 1, AZ0 + 1, LX1, CZ0 - 2, 0.2);
        c.mess(AX0 + 1, AZ0 + 1, LX1, CZ0 - 2, 0, 0.04);
    }

    /**
     * The control room: the vertical boards along the back wall with the annunciators over them,
     * the benchboard in front, the supervisor's desk by the boarded windows, the one-line diagram
     * on the west wall; the crew's cots and their water, the radio, the desks against the door.
     */
    private void control() {
        int x0 = LX1 + 2, x1 = AX1 - 1, z0 = AZ0 + 1, z1 = CZ0 - 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, "sc_vct");
            }
        }
        boolean on = c.power;
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            if (x >= 29 && x <= 32) {
                continue;
            }
            boolean lit = on && x >= x1 - 6;
            set(x, 1, z1, (lit ? "pw_vb_lo_on" : "pw_vb_lo") + "[facing=north]");
            set(x, 2, z1, (lit ? "pw_vb_hi_on" : "pw_vb_hi") + "[facing=north]");
            set(x, 3, z1, (lit && chance() < 0.5 ? "pw_annun_on" : "pw_annun") + "[facing=north]");
        }
        c.row("pw_freq_", 2, x1 - 5, 4, z1, "north", 0.0);
        // the benchboard: a U open toward the windows
        for (int x = x0 + 3; x <= x1 - 3; x++) {
            boolean lit = on && x >= x1 - 7;
            set(x, 1, z1 - 4, (lit ? "pw_bench_on" : "pw_bench") + "[facing=north]");
        }
        set(x0 + 2, 1, z1 - 3, (on ? "pw_bench_on" : "pw_bench") + "[facing=east]");
        set(x1 - 2, 1, z1 - 3, (on ? "pw_bench_on" : "pw_bench") + "[facing=west]");
        for (int x = x0 + 5; x <= x1 - 5; x += 4) {
            set(x, 1, z1 - 5, "office_chair[facing=south]");
        }
        c.picture("pw_mimic_", 6, 2, x0, 2, z0 + 2, "east");
        set(x1 - 3, 1, z0 + 2, "pw_desk[facing=south]");
        set(x1 - 3, 1, z0 + 3, "office_chair[facing=north]");
        set(x1 - 1, 1, z0 + 1, "filing_cabinet[facing=west]");
        // the night shift: cots along the windows, water, the radio, coffee, the crew's word on the wall
        for (int x = x0 + 1; x <= x0 + 9; x += 3) {
            set(x, 1, z0, "sc_cot_head[facing=east]");
            set(x + 1, 1, z0, "sc_cot_foot[facing=east]");
        }
        set(x0, 1, z1 - 1, "sc_cases_water[facing=east]");
        set(x0, 2, z1 - 1, "sc_cases_water[facing=east]");
        set(x0, 1, z1 - 2, "sc_cases_water[facing=east]");
        set(x0 + 1, 1, z1 - 1, "bunk_radio_desk[facing=east]");
        set(x1 - 1, 1, z0 + 5, "home_counter_white[facing=west]");
        set(x1 - 1, 2, z0 + 5, "pk_coffee[facing=west]");
        c.row("pw_spray_lights_", 4, x0 + 4, 4, z0, "south", 0.0);
        c.doors(30, 0, CZ0 - 1, "fire_door", "south");
        // the desk they shoved against the doors from inside, pulled back to let the last man in
        set(33, 1, z1 - 1, "pw_desk[facing=west]");
        c.row("pw_spray_here_", 4, 28, 3, CZ0, "north", 0.0);
        c.sign("control", 32, 4, CZ0, "north");
        c.ceiling(S, x0, z0, x1, z1, 0.4);
        c.mess(x0, z0, x1, z1, 0, 0.03);
    }

    /** Behind the corridor: first aid, the locker room with its showers, the lunch room. */
    private void backRooms() {
        // first aid, x 7 .. 12
        set(8, 1, CZ1 + 3, "bunk_medbed_head[facing=south]");
        set(8, 1, CZ1 + 4, "bunk_medbed_foot[facing=south]");
        set(11, 1, CZ1 + 3, "bunk_medbed_head[facing=south]");
        set(11, 1, CZ1 + 4, "bunk_medbed_foot[facing=south]");
        set(AX0 + 1, 1, 36, "bunk_medcab[facing=east]");
        set(AX0 + 1, 1, 38, "bunk_medcab[facing=east]");
        set(12, 1, 40, "bunk_iv_stand");
        set(8, 1, AZ1 - 1, "bunk_bodybag_head[facing=east]");
        set(9, 1, AZ1 - 1, "bunk_bodybag[facing=east]");
        c.sign("aid", 9, 3, CZ1, "north");
        // the locker room, x 14 .. 20: lockers down both walls, the bench, the showers at the back
        for (int z = CZ1 + 3; z <= 37; z++) {
            set(14, 1, z, "bunk_locker[facing=east]");
            set(LX1, 1, z, (chance() < 0.2 ? "bunk_locker_open" : "bunk_locker") + "[facing=west]");
        }
        for (int z = CZ1 + 4; z <= 35; z += 1) {
            set(17, 1, z, "cv_hall_bench[facing=east]");
        }
        for (int x = 14; x <= LX1; x++) {
            set(x, 1, AZ1 - 1, (x % 2 == 0 ? "bunk_shower" : "fit_toilet_stall") + "[facing=north]");
            set(x, 0, AZ1 - 1, "bunk_tile");
            set(x, 0, AZ1 - 2, "bunk_tile");
        }
        c.sign("lockers", 16, 3, CZ1, "north");
        // the lunch room, x 27 .. 39 and behind the stair
        for (int x = ST_X + 7; x <= AX1 - 3; x += 5) {
            for (int z = 28; z <= 40; z += 6) {
                set(x, 1, z, "fit_round_table");
                set(x - 1, 1, z, "pk_chair_dining[facing=east]");
                set(x + 1, 1, z, "pk_chair_dining[facing=west]");
                if (chance() < 0.5) {
                    set(x, 1, z + 1, "pk_chair_dining[facing=" + c.dir() + "]");
                }
            }
        }
        for (int z = CZ1 + 3; z <= CZ1 + 7; z++) {
            set(AX1 - 1, 1, z, "home_counter_white[facing=west]");
        }
        set(AX1 - 1, 2, CZ1 + 4, "pk_coffee[facing=west]");
        set(AX1 - 1, 2, CZ1 + 6, "pk_microwave[facing=west]");
        set(AX1 - 1, 1, CZ1 + 9, "pk_fridge[facing=west]");
        set(AX1 - 1, 1, AZ1 - 3, "store_vending[facing=west]");
        set(AX1 - 1, 2, AZ1 - 3, "store_vending_top[facing=west]");
        c.sign("lunch", 33, 3, CZ1, "north");
        c.ceiling(S, AX0 + 1, CZ1 + 2, AX1 - 1, AZ1 - 1, 0.25);
        c.mess(AX0 + 1, CZ1 + 2, AX1 - 1, AZ1 - 1, 0, 0.03);
    }

    // ------------------------------------------------------------------ upstairs

    /** The superintendent and the engineers along the front; records and the conference room behind. */
    private void floor2() {
        int y = S;
        // the superintendent, x 7 .. 20
        set(12, y + 1, 13, "desk[facing=north]");
        set(13, y + 1, 13, "desk[facing=north]");
        set(12, y + 1, 15, "office_chair[facing=north]");
        set(AX0 + 1, y + 1, 17, "filing_cabinet[facing=east]");
        set(AX0 + 1, y + 1, 18, "filing_cabinet[facing=east]");
        set(AX0 + 1, y + 3, 12, "cv_portrait[facing=east]");
        set(18, y + 1, 10, "home_sofa_brown_m[facing=south]");
        set(17, y + 1, 10, "home_sofa_brown_l[facing=south]");
        c.sign("super", 14, y + 3, CZ0 - 1, "south");
        // engineering: the drafting tables in rows, the plan files, the window on the hall
        for (int x = 24; x <= 37; x += 3) {
            for (int z = 11; z <= 17; z += 3) {
                set(x, y + 1, z, "desk[facing=north]");
                set(x, y + 1, z + 1, chance() < 0.2 ? "office_chair_fallen[facing=" + c.dir() + "]" : "office_chair[facing=north]");
            }
        }
        for (int x = 23; x <= 27; x++) {
            set(x, y + 1, CZ0 - 2, "filing_cabinet[facing=north]");
        }
        c.sign("eng", 30, y + 3, CZ0 - 1, "south");
        // records: shelves of logbooks in rows
        for (int z = CZ1 + 3; z <= AZ1 - 2; z += 3) {
            for (int x = 15; x <= LX1; x++) {
                set(x, y + 1, z, "fit_shelves[facing=north]");
                set(x, y + 2, z, "fit_shelves[facing=north]");
            }
        }
        for (int z = CZ1 + 3; z <= AZ1 - 2; z += 2) {
            set(AX0 + 1, y + 1, z, "filing_cabinet[facing=east]");
        }
        c.sign("records", 16, y + 3, CZ1 + 1, "north");
        // the conference room
        for (int z = 30; z <= 38; z++) {
            set(33, y + 1, z, "of_conf_table");
            set(32, y + 1, z, chance() < 0.15 ? "air" : "pk_chair_dining[facing=east]");
            set(34, y + 1, z, chance() < 0.15 ? "air" : "pk_chair_dining[facing=west]");
        }
        set(AX1 - 1, y + 2, 34, "of_whiteboard[facing=west]");
        c.ceiling(2 * S, AX0 + 1, AZ0 + 1, AX1 - 1, CZ0 - 2, 0.2);
        c.ceiling(2 * S, AX0 + 1, CZ1 + 2, AX1 - 1, AZ1 - 1, 0.2);
        c.mess(AX0 + 1, AZ0 + 1, AX1 - 1, AZ1 - 1, y, 0.03);
    }

    /** The lab, the room the shift slept in, the radio room, the stores. */
    private void floor3() {
        int y = 2 * S;
        // the chemical lab: the benches, the fume hoods along the west wall
        for (int z = 12; z <= 18; z += 3) {
            for (int x = 10; x <= 18; x++) {
                set(x, y + 1, z, "pw_labbench[facing=" + (z == 15 ? "south" : "north") + "]");
            }
        }
        for (int z = AZ0 + 2; z <= AZ0 + 6; z++) {
            set(AX0 + 1, y + 1, z, "pw_fumehood[facing=east]");
        }
        c.sign("lab", 14, y + 3, CZ0 - 1, "south");
        // where the crew slept: cots in rows, their kit, the stove, the water
        for (int x = 24; x <= 37; x += 3) {
            for (int z = 10; z <= 17; z += 4) {
                if (chance() < 0.85) {
                    set(x, y + 1, z, "sc_cot_head[facing=south]");
                    set(x, y + 1, z + 1, "sc_cot_foot[facing=south]");
                }
            }
        }
        for (int x = 23; x <= 27; x++) {
            set(x, y + 1, CZ0 - 2, chance() < 0.6 ? "sc_cases_water[facing=north]" : "bunk_crate_ration[facing=north]");
        }
        set(AX1 - 1, y + 1, CZ0 - 2, "bunk_drum_water");
        set(AX1 - 1, y + 1, CZ0 - 3, "bunk_jerry_cans[facing=west]");
        // the dispatcher's radio room
        set(8, y + 1, 28, "bunk_radio_desk[facing=south]");
        set(9, y + 1, 28, "bunk_radio_desk[facing=south]");
        set(8, y + 1, 30, "office_chair[facing=north]");
        set(AX0 + 1, y + 2, 34, "bunk_map_wall[facing=east]");
        set(16, y + 1, 32, "desk[facing=north]");
        set(16, y + 1, 33, "office_chair[facing=north]");
        // the stores: crates and shelving
        for (int z = CZ1 + 3; z <= AZ1 - 2; z += 3) {
            for (int x = 29; x <= 37; x += 2) {
                set(x, y + 1, z, chance() < 0.5 ? "crate" : "pk_garage_shelves[facing=north]");
            }
        }
        c.ceiling(ROOF_A, AX0 + 1, AZ0 + 1, AX1 - 1, CZ0 - 2, 0.2);
        c.ceiling(ROOF_A, AX0 + 1, CZ1 + 2, AX1 - 1, AZ1 - 1, 0.2);
        c.mess(AX0 + 1, AZ0 + 1, AX1 - 1, AZ1 - 1, y, 0.03);
    }

    /** The roof: the stair hut, the vents, a lookout of sandbags over the street; the ladder up the hall's end wall to its roof. */
    private void officeRoof() {
        int y = ROOF_A;
        for (int yy = y + 1; yy <= TROOF + 1; yy++) {
            set(AX1 - 1, yy, 30, "ladder[facing=west]");
        }
        set(AX1, TROOF + 1, 30, "air");
        set(AX1, TROOF + 2, 30, "air");
        for (int x = 10; x <= 16; x += 3) {
            set(x, y + 1, 36, "hvac");
        }
        for (int x = 9; x <= 14; x++) {
            set(x, y + 1, AZ0 + 2, "sandbags[facing=north]");
        }
        set(8, y + 1, AZ0 + 3, "sandbags[facing=west]");
        set(15, y + 1, AZ0 + 3, "sandbags[facing=east]");
        set(11, y + 1, AZ0 + 4, "trade_folding_chair[facing=north]");
        set(13, y + 1, AZ0 + 4, "sc_cases_water[facing=north]");
    }

    // ================================================================== the turbine hall

    private void hall() {
        hallShell();
        units();
        crane();
        steam();
        hallFittings();
    }

    /** Pilasters and tall arched windows on the street and the east end, the roof on its trusses, the skylights. */
    private void hallShell() {
        for (int y = 0; y <= TROOF + 2; y++) {
            for (int x = AX1; x <= TX1; x++) {
                hallFace(x, y, AZ0, x - AX1, "north");
                if (y <= TROOF + 2) {
                    set(x, y, AZ1, y == 0 ? "floor_concrete" : c.brick(x, y, AZ1));
                }
            }
            for (int z = AZ0 + 1; z < AZ1; z++) {
                hallFace(TX1, y, z, z - AZ0, "east");
                if (y > ROOF_A + 2) {
                    set(AX1, y, z, c.brick(AX1, y, z));
                }
            }
        }
        // the arched heads over every bay
        for (int x = AX1 + 1; x + 4 < TX1; x += 6) {
            c.picture("pw_arch_", 5, 2, x, 20, AZ0, "north");
        }
        for (int z = AZ0 + 1; z + 4 < AZ1; z += 6) {
            if (z != 21) {
                c.picture("pw_arch_", 5, 2, TX1, 20, z, "east");
            }
        }
        // the floor, the roof, the coping
        for (int x = AX1 + 1; x < TX1; x++) {
            for (int z = AZ0 + 1; z < AZ1; z++) {
                set(x, 0, z, z == 20 || z == 32 ? "pw_tile_line" : "pw_tile");
                boolean bay = Math.floorMod(x - AX1, 6) != 0;
                set(x, TROOF, z, bay && z >= 24 && z <= 28 ? "cv_skylight" : "un_roof");
            }
        }
        for (int x = AX1; x <= TX1; x++) {
            set(x, TROOF + 2, AZ0, "cc_coping");
        }
        for (int z = AZ0; z <= AZ1; z++) {
            set(TX1, TROOF + 2, z, "cc_coping");
        }
        // the name between the windows and the cornice, the company's seal at either end
        c.row("pw_name_", 20, 74, 24, AZ0 - 1, "north", 0.03);
        c.picture("pw_logo_", 2, 2, 52, 23, AZ0 - 1, "north");
        c.picture("pw_logo_", 2, 2, 112, 23, AZ0 - 1, "north");
        // the equipment door at the east end, its apron
        for (int z = 21; z <= 25; z++) {
            for (int y = 1; y <= 9; y++) {
                set(TX1, y, z, "garage_door[facing=east]");
            }
            // a lintel over the door, the sashes standing on it like on a sill
            set(TX1, 10, z, "stone_trim");
            for (int y = 11; y <= 19; y++) {
                set(TX1, y, z, sash(z, "east"));
            }
        }
        c.picture("pw_arch_", 5, 2, TX1, 20, 21, "east");
        c.sign("turbine", TX1 + 1, 3, 28, "east");
        // the doors through to the boiler house
        c.doors(60, 0, AZ1, "fire_door", "north");
        c.doors(100, 0, AZ1, "fire_door", "north");
        c.sign("boiler", 60, 4, AZ1 - 1, "north");
        c.sign("boiler", 100, 4, AZ1 - 1, "north");
    }

    /** One cell of the hall's street or east front: plinth, pilaster, sill, sash, brick, cornice, parapet. */
    private void hallFace(int x, int y, int z, int along, String out) {
        if (y == 0) {
            set(x, y, z, "floor_concrete");
            return;
        }
        if (y <= ROOF_A + 2 && x == AX1 && out.equals("north")) {
            set(x, y, z, c.brick(x, y, z));
            return;
        }
        boolean pilaster = Math.floorMod(along, 6) == 0 || out.equals("north") && x > TX1 - 3;
        if (y == 27) {
            set(x, y, z, "cv_cornice_stone[facing=" + out + "]");
            return;
        }
        if (pilaster || y == 1 || y >= 22) {
            String b = pilaster && y < 27 ? (y == 22 ? "stone_trim" : y == 1 ? "brick_dark" : "brick_dark_b") : y == 1 ? "brick_dark" : c.brick(x, y, z);
            set(x, y, z, b);
            return;
        }
        if (y == 2) {
            set(x, y, z, "brick_dark");
            return;
        }
        if (y == 3) {
            set(x, y, z, "stone_trim");
            return;
        }
        if (y <= 19) {
            set(x, y, z, sash(along, out));
            return;
        }
        set(x, y, z, c.brick(x, y, z));
    }

    private String sash(int along, String out) {
        double r = chance();
        String s = r < 0.25 ? "pw_sash_broken" : c.power && r < 0.33 ? "pw_sash_lit" : "pw_sash";
        return s + "[facing=" + out + "]";
    }

    /**
     * The three turbine-generators, each from its west end: the exciter, the generator, a bearing,
     * the low-pressure turbine, a bearing, the high-pressure turbine, the front standard with its
     * gauges. Unit 2's turbines stand open, the rotor in its lower casing.
     */
    private void units() {
        for (int k = 0; k < 3; k++) {
            int x0 = UNIT_X[k];
            boolean open = k == 1;
            set(x0, 1, 26, "pw_exciter[facing=west]");
            set(x0 + 1, 1, 26, "pw_pedestal");
            casing(x0 + 2, x0 + 8, UZ0, UZ1, 5, "pw_gen", "pw_gen_round", false);
            set(x0 + 9, 1, 26, "pw_pedestal");
            set(x0 + 9, 2, 26, "pw_tg");
            casing(x0 + 10, x0 + 15, UZ0, UZ1, 5, "pw_tg", "pw_tg_round", open);
            set(x0 + 16, 1, 26, "pw_pedestal");
            set(x0 + 16, 2, 26, "pw_tg");
            casing(x0 + 17, x0 + 21, UZ0 + 1, UZ1 - 1, 4, "pw_tg", "pw_tg_round", open);
            set(x0 + 22, 1, 26, "pw_pedestal");
            set(x0 + 22, 2, 26, "pw_tg");
            set(x0 + 23, 1, 25, "pw_gauge[facing=east]");
            set(x0 + 23, 1, 27, "pw_gauge[facing=east]");
            if (open) {
                for (int x = x0 + 10; x <= x0 + 21; x++) {
                    if (x != x0 + 16) {
                        set(x, 3, 26, "pw_rotor[axis=x]");
                    }
                }
            }
            set(x0 + 5, 3, UZ0 - 1, "pw_tg_plate[facing=north]");
            c.sign("unit" + (k + 1), x0 + 4, 5, UZ0 - 1, "north");
            // the deck plates round the unit, worn to the tile where the men walked
            for (int x = x0 - 1; x <= x0 + 23; x++) {
                for (int z : new int[] {UZ0 - 1, UZ1 + 1}) {
                    if (c.get(x, 0, z) != null && c.get(x, 0, z).startsWith("pw_tile")) {
                        set(x, 0, z, chance() < 0.8 ? "fit_deckplate" : "pw_tile");
                    }
                }
            }
        }
        // Unit 2's top casing set down on its cribbing in front, where the crane left it
        int cx0 = 82, cx1 = 87, cz0 = 12, cz1 = 18;
        for (int x = cx0; x <= cx1; x += cx1 - cx0) {
            for (int z = cz0; z <= cz1; z += 3) {
                set(x, 1, z, "wood_panel");
            }
        }
        for (int x = cx0; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                boolean edge = z == cz0 || z == cz1;
                set(x, 2, z, "pw_tg");
                set(x, 3, z, edge ? "pw_tg_round[facing=" + (z == cz0 ? "north" : "south") + "]" : "pw_tg_joint");
            }
        }
        // bolts and parts laid out on the floor, the rigging
        for (int x = cx0 - 4; x <= cx0 - 1; x++) {
            set(x, 1, 13, chance() < 0.5 ? "navy_toolbox[facing=north]" : "crate");
        }
        set(cx1 + 2, 1, 16, "cv_chain");
        set(cx1 + 3, 1, 14, "pk_tool_chest[facing=west]");
    }

    /**
     * A casing x0 .. x1 by z0 .. z1, h high: its top row's long edges rounded, the split flange at
     * mid height; open, only the lower half, the rotor's trough down its middle.
     */
    private void casing(int x0, int x1, int z0, int z1, int h, String body, String round, boolean open) {
        int split = (h + 1) / 2;
        int top = open ? split : h;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 1; y <= top; y++) {
                    boolean side = z == z0 || z == z1;
                    String b = body;
                    if (!open && y == h && side) {
                        b = round + "[facing=" + (z == z0 ? "north" : "south") + "]";
                    } else if (y == split && (side || x == x0 || x == x1) && body.equals("pw_tg")) {
                        b = "pw_tg_joint";
                    }
                    if (open && y == top && !side) {
                        b = "air";
                    }
                    set(x, y, z, b);
                }
            }
        }
    }

    /** The 50-ton crane: its runway along both walls, the bridge across over Unit 2, the trolley, the hook let down. */
    private void crane() {
        int y = CRANE_Y;
        for (int x = AX1 + 1; x < TX1; x++) {
            set(x, y - 1, AZ0 + 1, "pw_crane_rail[facing=south]");
            set(x, y - 1, AZ1 - 1, "pw_crane_rail[facing=north]");
        }
        for (int z = AZ0 + 1; z <= AZ1 - 1; z++) {
            set(CRANE_X, y, z, "pw_crane[axis=z]");
            set(CRANE_X + 2, y, z, "pw_crane[axis=z]");
        }
        for (int x = CRANE_X; x <= CRANE_X + 2; x++) {
            set(x, y, AZ0 + 1, "pw_crane_end[facing=north]");
            set(x, y, AZ1 - 1, "pw_crane_end[facing=south]");
        }
        set(CRANE_X + 2, y - 1, AZ1 - 3, "pw_crane_cab[facing=west]");
        c.row("pw_tons_", 2, CRANE_X - 1, y, 25, "west", 0.0);
        c.row("pw_tons_", 2, CRANE_X + 3, y, 25, "east", 0.0);
        int tz = 15;
        for (int x = CRANE_X; x <= CRANE_X + 2; x++) {
            for (int z = tz - 1; z <= tz + 1; z++) {
                set(x, y + 1, z, "pw_trolley[facing=north]");
            }
        }
        for (int yy = 6; yy < y; yy++) {
            set(CRANE_X + 1, yy, tz, "pw_rope");
        }
        set(CRANE_X + 1, 5, tz, "pw_hook");
        c.sign("crane", AX1 + 4, 12, AZ1 - 1, "north");
    }

    /**
     * The steam mains: from each boiler's drum over the firing aisle, through the back wall into the
     * hall to the header along it under the roof, down to each unit's high-pressure turbine.
     */
    private void steam() {
        for (int k = 0; k < 4; k++) {
            int xc = BOIL_X[k] + 6;
            for (int z = AZ1 + 1; z <= BZ0 + 9; z++) {
                set(xc, 31, z, "pw_steam[axis=z]");
            }
            for (int yy = HEAD_Y + 1; yy <= 30; yy++) {
                set(xc, yy, AZ1 + 1, "pw_steam_v");
            }
            for (int z = HEAD_Z + 1; z <= AZ1 + 1; z++) {
                set(xc, HEAD_Y, z, "pw_steam[axis=z]");
            }
        }
        for (int x = BOIL_X[0] + 6; x <= UNIT_X[2] + 19; x++) {
            set(x, HEAD_Y, HEAD_Z, "pw_steam[axis=x]");
        }
        for (int k = 0; k < 3; k++) {
            int hx = UNIT_X[k] + 19;
            for (int yy = 5; yy < HEAD_Y; yy++) {
                set(hx, yy, HEAD_Z, "pw_steam_v");
            }
            for (int z = UZ1; z < HEAD_Z; z++) {
                set(hx, 4, z, "pw_steam[axis=z]");
            }
        }
    }

    /** Trusses and high-bay lamps, the gauge boards, oil drums and crates along the walls. */
    private void hallFittings() {
        for (int x = AX1 + 6; x < TX1; x += 6) {
            for (int z = AZ0 + 1; z < AZ1; z++) {
                if (empty(x, TROOF - 1, z)) {
                    set(x, TROOF - 1, z, "fit_girder[axis=z]");
                }
            }
            for (int z : new int[] {14, 38}) {
                set(x - 3, TROOF - 2, z, c.power && chance() < 0.4 ? "fit_highbay_on" : chance() < 0.1 ? "fit_highbay_flicker" : "fit_highbay");
            }
        }
        for (int x = AX1 + 3; x < TX1 - 2; x += 4) {
            if (x >= 58 && x <= 63 || x >= 98 && x <= 103) {
                continue;
            }
            if (empty(x, 1, AZ1 - 1) && empty(x, 2, AZ1 - 1)) {
                double r = chance();
                set(x, 1, AZ1 - 1, r < 0.3 ? "pk_barrel_1" : r < 0.5 ? "pk_barrel_2" : r < 0.65 ? "crate" : r < 0.8 ? "pk_tool_chest[facing=north]" : "air");
            }
        }
        for (int x = AX1 + 2; x < TX1 - 1; x += 7) {
            if (empty(x, 1, AZ0 + 1)) {
                set(x, 1, AZ0 + 1, chance() < 0.5 ? "pw_vise_bench[facing=south]" : "pk_barrel_3");
            }
        }
        set(TX1 - 2, 1, 36, "pw_desk[facing=west]");
        set(TX1 - 3, 1, 36, "office_chair[facing=east]");
        c.sign("ears", AX1 + 1, 4, 30, "east");
        c.sign("hats", AX1 + 1, 4, 36, "east");
        c.mess(AX1 + 1, AZ0 + 1, TX1 - 1, AZ1 - 1, 0, 0.012);
    }

    // ================================================================== the boiler house

    private void boilerHouse() {
        boilerShell();
        c.stair(BST_X, BST_Z, false, new int[] {0, LEV1, LEV2, TRIP, BROOF}, "concrete_panel", "un_roof");
        for (int k = 0; k < 4; k++) {
            boiler(k);
        }
        trippers();
        levels();
        firingAisle();
    }

    /** Brick to the roof with tall narrow windows, the painted name over the hall's roof; doors out to the yard. */
    private void boilerShell() {
        for (int y = 0; y <= BROOF + 2; y++) {
            for (int z = AZ1; z <= BZ1; z++) {
                boilerWall(AX1, y, z, z - AZ1, "west", y > MROOF + 2);
                boilerWall(TX1, y, z, z - AZ1, "east", true);
            }
            for (int x = AX1; x <= TX1; x++) {
                boilerWall(x, y, BZ1, x - AX1, "south", x < BELT_X - 3 || x > BELT_X + 3);
                if (y > TROOF + 2) {
                    boilerWall(x, y, AZ1, x - AX1, "north", false);
                }
            }
        }
        for (int x = AX1 + 1; x < TX1; x++) {
            for (int z = AZ1 + 1; z < BZ1; z++) {
                set(x, 0, z, chance() < 0.15 ? "bunk_floor_dirty" : "floor_concrete");
                set(x, BROOF, z, "un_roof");
            }
        }
        for (int x = AX1; x <= TX1; x++) {
            set(x, BROOF + 2, AZ1, "cc_coping");
            set(x, BROOF + 2, BZ1, "cc_coping");
        }
        for (int z = AZ1; z <= BZ1; z++) {
            set(AX1, BROOF + 2, z, "cc_coping");
            set(TX1, BROOF + 2, z, "cc_coping");
        }
        // the name painted across the face over the turbine hall's roof
        c.picture("pw_mural_", 16, 2, 75, 38, AZ1 - 1, "north");
        // doors: the alley, the shop, the yard road (two), the east side street
        c.door(AX1, 0, 47, "flat_door", "west");
        c.door(AX1, 0, 70, "flat_door", "west");
        // out onto the shop's roof from the first grating level
        c.door(AX1, LEV1, 60, "fire_door", "west");
        c.doors(80, 0, BZ1, "fire_door", "south");
        c.doors(112, 0, BZ1, "fire_door", "south");
        c.doors(TX1, 0, 84, "fire_door", "east");
        c.sign("boiler", 80, 4, BZ1 + 1, "south");
        c.sign("smoke", 112, 4, BZ1 + 1, "south");
        c.sign("hats", TX1 + 1, 4, 81, "east");
    }

    private void boilerWall(int x, int y, int z, int along, String out, boolean windows) {
        if (y == 0) {
            set(x, y, z, "floor_concrete");
            return;
        }
        boolean corner = (x == AX1 || x == TX1) && (z == AZ1 || z == BZ1);
        int m = Math.floorMod(along, 8);
        boolean win = windows && !corner && (m == 3 || m == 4) && (y >= 4 && y <= 30 || y >= 37 && y <= 43);
        if (win) {
            set(x, y, z, sash(along, out));
            return;
        }
        set(x, y, z, y == 1 ? "brick_dark" : y == BROOF + 1 ? "cv_cornice_stone[facing=" + out + "]" : c.brick(x, y, z));
    }

    /**
     * One boiler: its casing 14 wide by 22 deep and 30 high, the burners in rows on its front, the
     * drum along its top with the safety valves, the water gauge; behind it the fans, the mills
     * and the flue up into its stack.
     */
    private void boiler(int k) {
        int x0 = BOIL_X[k], x1 = x0 + 13;
        boolean lit = c.power && k == 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = BZ0; z <= BZB; z++) {
                String f = z == BZ0 ? "north" : z == BZB ? "south" : x == x0 ? "west" : x == x1 ? "east" : "north";
                for (int y = 1; y <= 30; y++) {
                    set(x, y, z, "pw_boiler[facing=" + f + "]");
                }
            }
        }
        for (int x : new int[] {x0 + 3, x0 + 6, x0 + 7, x0 + 10}) {
            for (int y : new int[] {3, 6}) {
                set(x, y, BZ0, (lit && chance() < 0.6 ? "pw_burner_on" : "pw_burner") + "[facing=north]");
            }
        }
        set(x0 + 6, 24, BZ0 - 1, "pw_gauge_glass[facing=north]");
        set(x0 + 7, 24, BZ0 - 1, "pw_gauge_glass[facing=north]");
        set(x0 + 6, 13, BZ0 - 1, "fit_gauges[facing=north]");
        // the drum along the top, its ends, the safety valves
        int dz = 64;
        for (int x = x0 + 1; x < x1; x++) {
            set(x, 31, dz, "pw_drum[axis=x]");
        }
        set(x0, 31, dz, "pw_drum_end[facing=west]");
        set(x1, 31, dz, "pw_drum_end[facing=east]");
        set(x0 + 3, 32, dz, "pw_safety_valve");
        set(x1 - 3, 32, dz, "pw_safety_valve");
        // behind: the forced draft fans, the pulverizers, the flue's breeching up into the stack
        int xc = x0 + 6;
        set(x0 + 1, 1, BZB + 3, "pw_fan[facing=south]");
        set(x0 + 2, 1, BZB + 3, "pw_fan[facing=south]");
        set(x1 - 2, 1, BZB + 3, "pw_mill");
        set(x1 - 1, 1, BZB + 5, "pw_mill");
        for (int yy = 2; yy <= 30; yy++) {
            set(x1 - 2, yy, BZB + 3, yy == 2 ? "pw_mill" : "pw_chute");
        }
        for (int x = xc - 2; x <= xc + 2; x++) {
            for (int y = 24; y <= 28; y++) {
                for (int z = BZB + 1; z < STACK_Z - 2; z++) {
                    set(x, y, z, "pw_duct");
                }
            }
            for (int z = STACK_Z - 2; z <= STACK_Z + 2; z++) {
                for (int y = 18; y < BROOF; y++) {
                    set(x, y, z, "pw_duct");
                }
            }
        }
        for (int y = 1; y <= 17; y++) {
            set(xc, y, STACK_Z, "steel_girder");
        }
    }

    /** The tripper floor under the roof: the belt in from the gallery, along over the boilers, the chutes down. */
    private void trippers() {
        for (int x = AX1 + 1; x < TX1; x++) {
            for (int z = AZ1 + 1; z < BZ1; z++) {
                if (empty(x, TRIP, z) && !c.shaft(x, z)) {
                    set(x, TRIP, z, "floor_concrete");
                }
            }
        }
        for (int z = BELT_Z + 1; z < BZ1; z++) {
            set(BELT_X, TRIP + 1, z, "pw_belt_coal[axis=z]");
        }
        for (int x = AX1 + 2; x < TX1 - 8; x++) {
            set(x, TRIP + 1, BELT_Z, "pw_belt_coal[axis=x]");
        }
        for (int k = 0; k < 4; k++) {
            for (int x : new int[] {BOIL_X[k] + 3, BOIL_X[k] + 10}) {
                for (int y = 31; y < TRIP; y++) {
                    set(x, y, BELT_Z, "pw_chute");
                }
                set(x, TRIP, BELT_Z, "pw_chute");
            }
        }
        // the opening in the back wall where the gallery comes in
        for (int x = BELT_X - 1; x <= BELT_X + 2; x++) {
            for (int y = TRIP + 1; y <= TRIP + 4; y++) {
                set(x, y, BZ1, "air");
            }
        }
        for (int x = AX1 + 4; x < TX1; x += 8) {
            for (int z = AZ1 + 6; z < BZ1; z += 12) {
                set(x, BROOF - 1, z, c.power && chance() < 0.3 ? "fit_highbay_on" : "fit_highbay");
            }
        }
        c.sign("coal", BELT_X + 2, TRIP + 3, BZ1 - 1, "north");
    }

    /** Grating levels at 11 and 22 wherever there's floor to walk, a lamp hung under each. */
    private void levels() {
        for (int lev : new int[] {LEV1, LEV2}) {
            for (int x = AX1 + 1; x < TX1; x++) {
                for (int z = AZ1 + 1; z < BZ1; z++) {
                    if (empty(x, lev, z) && !c.shaft(x, z)) {
                        set(x, lev, z, "bunk_walkway");
                    }
                }
            }
        }
        for (int lev : new int[] {LEV1, LEV2, TRIP}) {
            for (int x = AX1 + 4; x < TX1; x += 9) {
                for (int z = AZ1 + 4; z < BZ1; z += 10) {
                    if (empty(x, lev - 1, z) && !c.shaft(x, z)) {
                        set(x, lev - 1, z, c.power && chance() < 0.3 ? "fit_highbay_on" : chance() < 0.1 ? "fit_highbay_flicker" : "fit_highbay");
                    }
                }
            }
        }
    }

    /** The firing aisle: the boards along the hall's wall, the tool carts, the ash on the floor. */
    private void firingAisle() {
        for (int k = 0; k < 4; k++) {
            boolean lit = c.power && k == 2;
            for (int x = BOIL_X[k] + 2; x <= BOIL_X[k] + 11; x++) {
                if (x >= 59 && x <= 62 || x >= 99 && x <= 102) {
                    continue;
                }
                set(x, 1, AZ1 + 1, (lit ? "pw_vb_lo_on" : "pw_vb_lo") + "[facing=south]");
                set(x, 2, AZ1 + 1, (lit ? "pw_vb_hi_on" : "pw_vb_hi") + "[facing=south]");
            }
        }
        for (int k = 0; k < 14; k++) {
            int x = AX1 + 2 + rng(80), z = AZ1 + 3 + rng(6);
            if (empty(x, 1, z)) {
                set(x, 1, z, chance() < 0.5 ? "bunk_grit" : chance() < 0.5 ? "pk_barrel_1" : "navy_toolbox[facing=north]");
            }
        }
        c.mess(AX1 + 1, AZ1 + 1, TX1 - 1, BZ1 - 1, 0, 0.01);
    }

    private int rng(int n) {
        return c.rng.nextInt(n);
    }

    /**
     * The four stacks on the boiler house roof over the flues, round the way the photos have them:
     * smooth brick drums tapering as they rise, a sooted band every twelve, the top sooted black,
     * the obstruction lights round the rim.
     */
    private void stacks() {
        int top = BROOF + 66;
        for (int k = 0; k < 4; k++) {
            int xc = BOIL_X[k] + 6;
            double ox = xc + 0.5, oz = STACK_Z + 0.5;
            for (int dx = -6; dx <= 6; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    if (Math.hypot(dx, dz) <= 5.6) {
                        set(xc + dx, BROOF + 1, STACK_Z + dz, "concrete_dark");
                    }
                }
            }
            for (int y = BROOF + 2; y <= top; y++) {
                double r = 4.9 - (y - BROOF - 2) * 1.1 / (top - BROOF - 2);
                java.util.Set<Long> rim = new java.util.HashSet<>();
                String m = y >= top - 2 || (y - BROOF) % 12 == 0 ? "soot" : "brick";
                for (Round.Cell cell : Round.ring(ox, oz, r)) {
                    set(cell.x(), y, cell.z(), "rd_" + m + "_" + cell.suffix() + "[facing=" + cell.facing() + "]");
                    rim.add(((long) cell.x() << 32) ^ (cell.z() & 0xffffffffL));
                }
                for (int dx = -5; dx <= 5; dx++) {
                    for (int dz = -5; dz <= 5; dz++) {
                        int x = xc + dx, z = STACK_Z + dz;
                        if (!rim.contains(((long) x << 32) ^ (z & 0xffffffffL)) && Math.hypot(x + 0.5 - ox, z + 0.5 - oz) < r - 0.4) {
                            set(x, y, z, y >= top - 1 ? "air" : "pw_soot");
                        }
                    }
                }
            }
            for (int[] o : new int[][] {{0, -4}, {0, 4}, {-4, 0}, {4, 0}}) {
                set(xc + o[0], top + 1, STACK_Z + o[1], c.power && chance() < 0.7 ? "tv_beacon_lit" : "tv_beacon");
            }
        }
    }

    // ================================================================== the machine shop

    /** The machine shop and the stores: one high storey behind the office, the roll-up door to the side street. */
    private void shop() {
        int x0 = AX0, x1 = AX1, z0 = MZ0, z1 = BZ1;
        for (int y = 0; y <= MROOF + 2; y++) {
            for (int x = x0; x < x1; x++) {
                shopWall(x, y, z0, x - x0, "north");
                shopWall(x, y, z1, x - x0, "south");
            }
            for (int z = z0 + 1; z < z1; z++) {
                shopWall(x0, y, z, z - z0, "west");
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                set(x, 0, z, chance() < 0.2 ? "bunk_floor_stain" : "floor_concrete");
                set(x, MROOF, z, (x - x0) % 6 == 3 && (z - z0) % 4 != 0 ? "cv_skylight" : "un_roof");
            }
        }
        for (int x = x0; x < x1; x++) {
            set(x, MROOF + 2, z0, "cc_coping");
            set(x, MROOF + 2, z1, "cc_coping");
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, MROOF + 2, z, "cc_coping");
        }
        for (int z = 58; z <= 61; z++) {
            for (int y = 1; y <= 4; y++) {
                set(x0, y, z, "garage_door[facing=west]");
            }
            set(x0, 5, z, "stone_trim");
        }
        c.door(20, 0, z0, "flat_door", "north");
        c.sign("shop", 10, 6, z0 - 1, "north");
        c.sign("stores", 30, 6, z0 - 1, "north");
        // the wall between: the stores' counter in the opening
        int px = 24;
        c.partZ(px, z0 + 1, z1 - 1, 0, MROOF, "sc_cmu_dado_a", "sc_cmu");
        for (int z = 70; z <= 72; z++) {
            set(px, 1, z, "home_counter_white[facing=west]");
            set(px, 2, z, "air");
            set(px, 3, z, "air");
        }
        c.door(px, 0, 66, "flat_door", "west");
        // the shop: lathes in rows, drill presses, grinders, the benches along the wall, the welder
        for (int z : new int[] {56, 62}) {
            for (int x = 9; x <= 19; x += 5) {
                set(x, 1, z, "pw_lathe_head[facing=north]");
                set(x + 1, 1, z, "pw_lathe_tail[facing=north]");
            }
        }
        for (int x = 9; x <= 15; x += 3) {
            set(x, 1, 68, "pw_drill[facing=north]");
        }
        set(19, 1, 68, "pw_grinder[facing=north]");
        set(21, 1, 68, "pw_grinder[facing=north]");
        for (int z = 74; z <= 88; z += 2) {
            set(x0 + 1, 1, z, "pw_vise_bench[facing=east]");
        }
        set(13, 1, 78, "pw_welder[facing=north]");
        for (int z = 80; z <= 88; z++) {
            set(19, 1, z, "pw_pipe_rack[axis=z]");
            set(21, 1, z, "pw_pipe_rack[axis=z]");
        }
        set(px - 1, 2, 56, "pw_hardhats[facing=west]");
        // the stores: shelving rows, the cable reels, the racks
        for (int z = 55; z <= 67; z += 4) {
            for (int x = 27; x <= 37; x++) {
                set(x, 1, z, "pw_parts[facing=north]");
                set(x, 2, z, "pw_parts[facing=north]");
                set(x, 1, z + 1, "pw_parts[facing=south]");
            }
        }
        for (int x = 27; x <= 37; x += 2) {
            for (int z = 78; z <= 88; z += 3) {
                if (chance() < 0.8) {
                    set(x, 1, z, "pw_reel[axis=x]");
                }
            }
        }
        for (int z = 75; z <= 89; z += 2) {
            set(x1 - 1, 1, z, "pk_metal_rack[facing=west]");
        }
        for (int x = x0 + 4; x < x1; x += 6) {
            for (int z = z0 + 5; z < z1; z += 10) {
                set(x, MROOF - 1, z, c.power && chance() < 0.3 ? "fit_highbay_on" : "fit_highbay");
            }
        }
        c.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.02);
    }

    private void shopWall(int x, int y, int z, int along, String out) {
        if (y == 0) {
            set(x, y, z, "floor_concrete");
            return;
        }
        boolean end = along == 0;
        int m = Math.floorMod(along, 4);
        boolean win = !end && y >= 4 && y <= 7 && (m == 1 || m == 2);
        set(x, y, z, win ? sash(along, out) : y == 1 ? "brick_dark" : c.brick(x, y, z));
    }
}
