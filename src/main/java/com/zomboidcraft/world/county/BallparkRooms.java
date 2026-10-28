package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;

/**
 * Inside {@link Ballpark}'s grandstand: on the event level the corridor from dugout to dugout
 * under the rows, the rooms off it (the clubhouses, the cages, the grounds crew, the Guard's
 * armory and its morgue) and the passage behind; the landing zone's operations hall under home;
 * on the concourse the units under the upper deck (the stands, the restrooms, the shop, first aid)
 * and the processing lines in the rotunda behind home.
 *
 * <p>Everything along a line is placed by (along, band): side 0 the first-base stand, 1 the
 * third-base stand; "in" faces the field, "out" the street, "fwd" further along the line.
 */
final class BallparkRooms {
    private static final int EV = Ballpark.EV, CONC = Ballpark.CONC, ROOM0 = 14, ROOM1 = 34, CEIL = 5;
    private final Ballpark a;

    BallparkRooms(Ballpark a) {
        this.a = a;
    }

    private record Room(String kind, int al0, int al1) {
    }

    /** The rooms on the event level, their walls at al0 and al1; the cores stand in the backs of some. */
    private static final Room[][] ROOMS = {
            {new Room("umpires", 0, 12), new Room("home", 12, 30), new Room("cages", 30, 46), new Room("grounds", 46, 62),
                    new Room("laundry", 62, 78), new Room("armory", 78, Ballpark.POLE)},
            {new Room("press", 0, 12), new Room("visitors", 12, 30), new Room("kitchen", 30, 46), new Room("office", 46, 62),
                    new Room("aid", 62, 78), new Room("morgue", 78, Ballpark.POLE)}};
    /** The units on the concourse, walls at al0 and al1. */
    private static final Room[][] UNITS = {
            {new Room("dogs", 1, 10), new Room("men", 10, 19), new Room("store", 25, 32), new Room("crab", 37, 49), new Room("women", 55, 62),
                    new Room("brew", 67, 79), new Room("aid", 85, 95)},
            {new Room("crab", 1, 10), new Room("women", 10, 19), new Room("store", 25, 32), new Room("dogs", 37, 49), new Room("men", 55, 62),
                    new Room("brew", 67, 79), new Room("aid", 85, 95)}};

    // ------------------------------------------------------------------ local frame

    private static String face(int s, String local) {
        return switch (local) {
            case "in" -> s == 0 ? "south" : "west";
            case "out" -> s == 0 ? "north" : "east";
            case "fwd" -> s == 0 ? "west" : "south";
            default -> s == 0 ? "east" : "north";
        };
    }

    private void put(int s, int al, int b, int y, String block) {
        int[] q = Ballpark.at(s, al, b);
        a.set(q[0], y, q[1], block);
    }

    private String get(int s, int al, int b, int y) {
        int[] q = Ballpark.at(s, al, b);
        return a.get(q[0], y, q[1]);
    }

    private boolean free(int s, int al, int b, int y) {
        int[] q = Ballpark.at(s, al, b);
        return a.empty(q[0], y, q[1]) && a.empty(q[0], y + 1, q[1]);
    }

    /** Puts a thing on the floor at y if the cell and the one over it are clear and no door is beside it. */
    private void place(int s, int al, int b, int y, String block) {
        if (!free(s, al, b, y + 1)) {
            return;
        }
        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            String n = get(s, al + o[0], b + o[1], y + 1);
            if (n != null && n.contains("door")) {
                return;
            }
        }
        put(s, al, b, y + 1, block);
    }

    private static boolean core(int al, int b) {
        for (int c : Ballpark.CORES) {
            if (al >= c && al <= c + 4 && b >= Ballpark.UNIT1 - 9) {
                return true;
            }
        }
        return false;
    }

    void build() {
        for (int s = 0; s < 2; s++) {
            eventSide(s);
            for (Room r : ROOMS[s]) {
                room(s, r);
            }
            for (Room u : UNITS[s]) {
                unit(s, u);
            }
            gates(s);
            concourse(s);
        }
        round();
        strew();
    }

    // ------------------------------------------------------------------ the event level

    /** One side's corridor under the rows, the rooms' front and back walls, the passage behind, the ceilings. */
    private void eventSide(int s) {
        for (int al = 0; al < Ballpark.POLE; al++) {
            for (int b = Ballpark.CORR0; b <= Ballpark.PASS1; b++) {
                put(s, al, b, EV, b >= ROOM0 && b <= ROOM1 ? "floor_concrete" : "floor_lino");
                if (b == ROOM0 - 1 || b == ROOM1 + 1) {
                    for (int y = EV + 1; y < EV + CEIL; y++) {
                        put(s, al, b, y, Ballpark.WALL);
                    }
                }
                if (b != ROOM0 - 1 && b != ROOM1 + 1 && !core(al, b)) {
                    boolean lamp = Math.floorMod(al, 5) == 2 && (b == 10 || b == 36 || b >= ROOM0 && b <= ROOM1 && Math.floorMod(b, 5) == 1);
                    put(s, al, b, EV + CEIL, lamp ? (a.power && a.chance() < 0.3 ? "fit_troffer_on" : "fit_troffer") + "[axis=" + (s == 0 ? "x" : "z") + "]"
                            : "fit_ceiling");
                }
            }
            if (al % 12 == 6) {
                put(s, al, Ballpark.CORR0, EV + 3, (a.chance() < 0.5 ? "st_poster_missing" : "st_poster_missing_b") + "[facing=" + face(s, "out") + "]");
            }
        }
        // the corridor's end at the round, the walls of the rooms
        for (Room r : ROOMS[s]) {
            for (int al : new int[] {r.al0(), r.al1()}) {
                if (al >= Ballpark.POLE) {
                    continue;
                }
                for (int b = ROOM0; b <= ROOM1; b++) {
                    if (!core(al, b) || al == 0) {
                        for (int y = EV + 1; y < EV + CEIL; y++) {
                            put(s, al, b, y, Ballpark.WALL);
                        }
                    }
                }
            }
        }
    }

    private void room(int s, Room r) {
        int mid = (r.al0() + r.al1()) / 2;
        String in = face(s, "in"), out = face(s, "out");
        // the door from the corridor, the sign beside it; the back door to the passage
        door(s, mid, ROOM0 - 1, r.kind().equals("armory") || r.kind().equals("morgue") ? Doors.STEEL : Doors.SHOP, in);
        sign(s, mid - 3, ROOM0 - 2, EV + 3, sign(r.kind()), in);
        if (!core(r.al0() + 2, ROOM1)) {
            door(s, r.al0() + 2, ROOM1 + 1, Doors.STEEL, out);
        }
        int lo = r.al0() + 1, hi = r.al1() - 1;
        switch (r.kind()) {
            case "home", "visitors" -> clubhouse(s, lo, hi, r.kind().equals("home"));
            case "cages" -> cages(s, lo, hi);
            case "grounds" -> {
                for (int al = lo + 1; al <= hi - 1; al += 2) {
                    place(s, al, ROOM1, EV, a.any("bp_mower", "bp_chalker", "st_pallet", "bp_bases", "home_workbench") + "[facing=" + in + "]");
                    place(s, al, ROOM0 + 1, EV, a.any("bp_rake", "store_shelf_hardware", "water_tank", "st_pallet", "propane_tank") + "[facing=" + out + "]");
                }
                for (int b = ROOM0 + 4; b <= ROOM1 - 4; b += 4) {
                    place(s, mid, b, EV, "bp_tarp[axis=" + (s == 0 ? "x" : "z") + "]");
                    place(s, mid + 1, b, EV, "bp_tarp[axis=" + (s == 0 ? "x" : "z") + "]");
                }
            }
            case "laundry" -> {
                for (int al = lo; al <= hi; al++) {
                    place(s, al, ROOM0, EV, (al % 2 == 0 ? "store_washer" : "store_dryer") + "[facing=" + out + "]");
                }
                for (int b = ROOM0 + 4; b <= ROOM1 - 2; b += 3) {
                    place(s, lo, b, EV, "store_shelf_thrift[facing=" + face(s, "fwd") + "]");
                    place(s, mid, b, EV, "trade_banquet_table[facing=" + in + "]");
                }
            }
            case "armory" -> armory(s, lo, hi);
            case "umpires", "press", "office" -> {
                for (int al = lo + 1; al <= hi - 1; al += 3) {
                    for (int b = ROOM0 + 2; b <= ROOM1 - 3; b += 4) {
                        place(s, al, b, EV, (r.kind().equals("umpires") && b < ROOM0 + 6 ? "st_bench" : a.chance() < 0.4 ? "home_desk_pc" : "desk") + "[facing=" + in + "]");
                        place(s, al, b + 1, EV, (a.chance() < 0.2 ? "office_chair_fallen" : "office_chair") + "[facing=" + in + "]");
                    }
                }
                for (int al = lo; al <= hi; al += 2) {
                    place(s, al, ROOM0, EV, (r.kind().equals("umpires") ? "fs_locker" : "filing_cabinet") + "[facing=" + out + "]");
                }
                place(s, hi, ROOM0 + 4, EV, "of_whiteboard[facing=" + face(s, "back") + "]");
            }
            case "kitchen" -> {
                String[] line = {"dn_griddle", "dn_fryer", "dn_prep", "store_oven", "dn_prep", "store_cooler", "store_cooler", "dn_lowboy"};
                for (int al = lo, k = 0; al <= hi; al++, k++) {
                    place(s, al, ROOM0, EV, line[k % line.length] + "[facing=" + out + "]");
                    if (line[k % line.length].equals("store_cooler")) {
                        put(s, al, ROOM0, EV + 2, "store_cooler_top[facing=" + out + "]");
                    }
                }
                for (int b = ROOM0 + 4; b <= ROOM1 - 2; b += 4) {
                    for (int al = lo + 2; al <= hi - 2; al++) {
                        place(s, al, b, EV, "fit_counter_steel[facing=" + in + "]");
                    }
                }
            }
            case "aid" -> {
                for (int al = lo + 1; al <= hi - 1; al += 2) {
                    for (int b = ROOM0 + 2; b <= ROOM1 - 3; b += 4) {
                        if (free(s, al, b, EV + 1) && free(s, al, b + 1, EV + 1)) {
                            put(s, al, b, EV + 1, "sc_cot_head[facing=" + in + "]");
                            put(s, al, b + 1, EV + 1, "sc_cot_foot[facing=" + in + "]");
                        }
                    }
                }
                place(s, hi, ROOM0, EV, "hs_cabinet[facing=" + out + "]");
                place(s, hi - 1, ROOM0, EV, "hs_cart_med[facing=" + out + "]");
                place(s, lo, ROOM0, EV, "hs_gurney_a[facing=" + out + "]");
            }
            case "morgue" -> {
                for (int al = lo + 1; al <= hi - 1; al += 2) {
                    for (int b = ROOM0 + 1; b <= ROOM1 - 3; b += 3) {
                        if (a.chance() < 0.8 && free(s, al, b, EV + 1) && free(s, al, b + 1, EV + 1)) {
                            put(s, al, b, EV + 1, "bunk_bodybag_head[facing=" + in + "]");
                            put(s, al, b + 1, EV + 1, "bunk_bodybag[facing=" + in + "]");
                        }
                    }
                }
            }
            default -> {
            }
        }
        litter(s, lo, hi, 0.02);
    }

    /** The clubhouse: the stalls round the walls, the benches, the trainer's tables, the showers at the back. */
    private void clubhouse(int s, int lo, int hi, boolean home) {
        String out = face(s, "out"), in = face(s, "in");
        for (int al = lo; al <= hi; al++) {
            for (int b = ROOM0; b <= ROOM1 - 10; b++) {
                put(s, al, b, EV, home ? "st_carpet" : "floor_lino");
            }
        }
        String stall = home ? "bp_stall" : "bp_stall_away";
        for (int al = lo; al <= hi; al++) {
            place(s, al, ROOM0, EV, stall + "[facing=" + out + "]");
        }
        for (int b = ROOM0 + 2; b <= ROOM1 - 12; b++) {
            place(s, lo, b, EV, stall + "[facing=" + face(s, "fwd") + "]");
            place(s, hi, b, EV, stall + "[facing=" + face(s, "back") + "]");
        }
        for (int al = lo + 3; al <= hi - 3; al++) {
            place(s, al, ROOM0 + 5, EV, "st_bench[facing=" + in + "]");
        }
        place(s, hi - 2, ROOM0 + 2, EV, "bp_bats[facing=" + out + "]");
        place(s, lo + 2, ROOM0 + 9, EV, "of_whiteboard[facing=" + in + "]");
        // the wall to the back: the trainer's room and the showers behind it
        int wb = ROOM1 - 9;
        for (int al = lo; al <= hi; al++) {
            if (!core(al, wb)) {
                for (int y = EV + 1; y < EV + CEIL; y++) {
                    put(s, al, wb, y, Ballpark.WALL);
                }
            }
        }
        door(s, lo + 2, wb, Doors.SHOP, out);
        for (int al = lo; al <= hi; al++) {
            for (int b = wb + 1; b <= ROOM1; b++) {
                if (!core(al, b)) {
                    put(s, al, b, EV, "dn_quarry");
                }
            }
        }
        for (int al = lo + 1; al <= hi - 1; al += 2) {
            if (!core(al, ROOM1)) {
                place(s, al, ROOM1, EV, (a.chance() < 0.5 ? "bunk_shower" : "fit_toilet_stall") + "[facing=" + in + "]");
            }
        }
        place(s, lo + 5, wb + 3, EV, "hs_gurney_a[facing=" + in + "]");
        place(s, lo + 7, wb + 3, EV, "hs_gurney_a[facing=" + in + "]");
        place(s, lo + 9, wb + 3, EV, "tank_blue");
        for (int k = 0; k < 6; k++) {
            place(s, lo + 2 + a.rng.nextInt(Math.max(1, hi - lo - 3)), ROOM0 + 2 + a.rng.nextInt(6), EV,
                    a.any("trash_bag[facing=" + a.dir() + "]", "home_blood_pool", "paper", "bp_balls"));
        }
    }

    /** The batting cages: two lanes of netting the length of the room, the machines at the far ends. */
    private void cages(int s, int lo, int hi) {
        for (int al = lo; al <= hi; al++) {
            for (int b = ROOM0; b <= ROOM1; b++) {
                if (!core(al, b)) {
                    put(s, al, b, EV, "bp_turf");
                }
            }
        }
        for (int lane = 0; lane < 2; lane++) {
            int l0 = lo + 1 + lane * 7, l1 = l0 + 5;
            for (int b = ROOM0 + 2; b <= ROOM1 - 3; b++) {
                for (int y = EV + 1; y <= EV + 4; y++) {
                    boolean door = b == ROOM0 + 2 && y <= EV + 2;
                    if (!door) {
                        put(s, l0, b, y, "bp_cage");
                        put(s, l1, b, y, "bp_cage");
                    }
                }
            }
            for (int al = l0 + 1; al < l1; al++) {
                for (int y = EV + 1; y <= EV + 4; y++) {
                    put(s, al, ROOM1 - 3, y, "bp_cage");
                }
            }
            put(s, l0 + 2, ROOM1 - 5, EV + 1, "bp_machine[facing=" + face(s, "in") + "]");
            put(s, l0 + 3, ROOM1 - 5, EV + 1, "bp_balls");
            put(s, l0 + 2, ROOM0 + 4, EV, "bp_plate[facing=" + face(s, "out") + "]");
        }
        place(s, hi - 1, ROOM0 + 1, EV, "bp_bats[facing=" + face(s, "out") + "]");
        place(s, hi - 1, ROOM0 + 3, EV, "bp_helmets[facing=" + face(s, "back") + "]");
    }

    /** The Guard's armory in the end room: the racks, the crates, the sandbags at the door. */
    private void armory(int s, int lo, int hi) {
        String out = face(s, "out"), in = face(s, "in");
        for (int al = lo; al <= hi; al++) {
            place(s, al, ROOM0, EV, (al % 3 == 0 ? "bunk_gun_rack" : al % 3 == 1 ? "bunk_crate_ammo" : "pk_military_crate") + "[facing=" + out + "]");
        }
        for (int b = ROOM0 + 3; b <= ROOM1 - 2; b += 3) {
            place(s, lo, b, EV, "store_gun_rack[facing=" + face(s, "fwd") + "]");
            place(s, (lo + hi) / 2, b, EV, a.any("bunk_crate_gun", "bunk_crate_ammo", "bunk_crate_ration", "pk_military_crate_old") + "[facing=" + in + "]");
        }
        // the stacks down the room in rows, the soldiers' cots along the far wall
        for (int b = ROOM0 + 4; b <= ROOM1 - 4; b += 3) {
            for (int al = lo + 3; al <= hi - 3; al += 2) {
                if (a.chance() < 0.7) {
                    place(s, al, b, EV, a.any("bunk_crate_ammo", "pk_military_crate", "bunk_crate_ration", "pk_military_crate_old_b", "st_pallet_meals")
                            + "[facing=" + in + "]");
                }
            }
        }
        for (int b = ROOM0 + 3; b <= ROOM1 - 3; b += 2) {
            if (free(s, hi, b, EV + 1) && free(s, hi, b + 1, EV + 1)) {
                put(s, hi, b, EV + 1, "sc_cot_head[facing=" + in + "]");
                put(s, hi, b + 1, EV + 1, "sc_cot_foot[facing=" + in + "]");
            }
        }
        int mid = (lo + hi) / 2;
        for (int al = mid - 3; al <= mid + 3; al++) {
            if (Math.abs(al - mid) > 1) {
                place(s, al, ROOM0 + 1, EV, "sandbags");
            }
        }
        place(s, hi - 1, ROOM0 + 2, EV, "bunk_radio_desk[facing=" + face(s, "back") + "]");
        sign(s, mid - 1, ROOM0 + 1, EV + 3, "armory", out);
    }

    // ------------------------------------------------------------------ the concourse

    private static final java.util.Map<String, String[]> LINE = java.util.Map.of(
            "dogs", new String[] {"dn_griddle", "st_warmer", "st_warmer", "dn_prep", "store_cooler", "st_fountain"},
            "crab", new String[] {"dn_fryer", "dn_fryer", "dn_prep", "st_warmer", "dn_lowboy", "st_fountain"},
            "brew", new String[] {"store_cooler", "store_cooler", "trade_back_bar", "trade_back_bar", "dn_ice_lo", "st_fountain"});

    /** A unit behind the concourse: its walls, and a stand, a restroom, the shop or first aid in it. */
    private void unit(int s, Room u) {
        String in = face(s, "in"), out = face(s, "out");
        int f = Ballpark.UNIT0, bk = Ballpark.UNIT1;
        for (int b = f; b <= bk; b++) {
            for (int y = CONC + 1; y < CEIL; y++) {
                put(s, u.al0(), b, y, Ballpark.WALL);
                put(s, u.al1(), b, y, Ballpark.WALL);
            }
        }
        for (int al = u.al0(); al <= u.al1(); al++) {
            for (int y = CONC + 1; y < CEIL; y++) {
                put(s, al, f, y, Ballpark.WALL);
                put(s, al, bk, y, Ballpark.WALL);
            }
        }
        int mid = (u.al0() + u.al1()) / 2;
        switch (u.kind()) {
            case "men", "women" -> {
                door(s, mid, f, Doors.SHOP, in);
                put(s, mid - 1, f - 1, CONC + 3, "st_plaque_" + u.kind() + "[facing=" + in + "]");
                for (int al = u.al0() + 1; al < u.al1(); al++) {
                    for (int b = f + 1; b < bk; b++) {
                        put(s, al, b, CONC, "dn_quarry");
                    }
                    put(s, al, bk - 1, CONC + 1, "fit_toilet_stall[facing=" + in + "]");
                }
                for (int b = f + 2; b < bk - 2; b++) {
                    put(s, u.al0() + 1, b, CONC + 1, "home_sink_white[facing=" + face(s, "fwd") + "]");
                    put(s, u.al0() + 1, b, CONC + 2, "fit_mirror_long[facing=" + face(s, "fwd") + "]");
                }
            }
            case "store" -> {
                header(s, u, "store");
                door(s, u.al0() + 1, f, Doors.SHOP, in);
                for (int al = u.al0() + 1; al < u.al1(); al++) {
                    put(s, al, bk - 1, CONC + 1, "store_shelf_thrift[facing=" + in + "]");
                    put(s, al, bk - 1, CONC + 2, "store_shelf_thrift_top[facing=" + in + "]");
                    if (al > u.al0() + 1) {
                        put(s, al, f, CONC + 1, "store_counter[facing=" + in + "]");
                    }
                }
            }
            case "aid" -> {
                door(s, mid, f, Doors.SHOP, in);
                put(s, mid - 1, f - 1, CONC + 3, "st_plaque_aid[facing=" + in + "]");
                for (int al = u.al0() + 1; al < u.al1() - 1; al += 2) {
                    put(s, al, bk - 1, CONC + 1, "sc_cot_head[facing=" + in + "]");
                    put(s, al, bk - 2, CONC + 1, "sc_cot_foot[facing=" + in + "]");
                }
                put(s, u.al1() - 1, bk - 1, CONC + 1, "hs_cabinet[facing=" + in + "]");
            }
            default -> {
                header(s, u, u.kind());
                door(s, u.al0() + 1, f, Doors.STEEL, in);
                double g = a.chance();
                for (int al = u.al0() + 2; al < u.al1(); al++) {
                    put(s, al, f, CONC + 1, (al % 3 == 1 ? "store_register" : "store_counter") + "[facing=" + in + "]");
                    put(s, al, f, CONC + 2, g < 0.3 ? "st_grille[facing=" + in + "]" : "air");
                    put(s, al, f, CONC + 3, g < 0.3 ? "st_grille[facing=" + in + "]" : g < 0.6 ? "st_grille_half[facing=" + in + "]" : "air");
                }
                String[] line = LINE.get(u.kind());
                for (int al = u.al0() + 1, k = 0; al < u.al1() && k < line.length; al++, k++) {
                    put(s, al, bk - 1, CONC + 1, line[k] + "[facing=" + in + "]");
                    if (line[k].equals("store_cooler")) {
                        put(s, al, bk - 1, CONC + 2, "store_cooler_top[facing=" + in + "]");
                    }
                    if (line[k].startsWith("dn_griddle") || line[k].startsWith("dn_fryer")) {
                        put(s, al, bk - 1, CONC + 3, "dn_hood[facing=" + in + "]");
                    }
                }
                // the menus over the back counter, tile 0 at the viewer's left
                int m0 = u.al1() - 3;
                boolean leftHigh = s == 0;
                put(s, m0, bk - 1, CONC + 3, "bp_menu_" + u.kind() + "_" + (leftHigh ? 1 : 0) + "[facing=" + in + "]");
                put(s, m0 + 1, bk - 1, CONC + 3, "bp_menu_" + u.kind() + "_" + (leftHigh ? 0 : 1) + "[facing=" + in + "]");
            }
        }
        for (int al = u.al0() + 1; al < u.al1(); al++) {
            if (Math.floorMod(al, 4) == 2) {
                put(s, al, (f + bk) / 2, CEIL - 1, (a.power && a.chance() < 0.3 ? "fit_troffer_on" : "fit_troffer") + "[axis=" + (s == 0 ? "x" : "z") + "]");
            }
        }
    }

    /** The lit header over a stand's counter, eight wide in the front wall's soffit: tile 0 at the viewer's left. */
    private void header(int s, Room u, String kind) {
        for (int i = 0; i < 8; i++) {
            // side 0 faces south, its viewer's left is west (the far end along); side 1 faces west, its left the north (the near end)
            int al = s == 0 ? u.al1() - 1 - i : u.al0() + 1 + i;
            if (al <= u.al0() || al >= u.al1()) {
                continue;
            }
            put(s, al, Ballpark.UNIT0, CEIL, "bp_stand_" + kind + "_" + i + "[facing=" + face(s, "in") + "]");
        }
    }

    /** The gate passages through to the concourse: the turnstiles, the Guard's barriers and its line. */
    private void gates(int s) {
        String in = face(s, "in");
        for (int g : Ballpark.GATES) {
            for (int al = g; al < g + 4; al++) {
                for (int b = Ballpark.UNIT0; b <= Ballpark.PASS1; b++) {
                    for (int y = CONC + 1; y < CEIL; y++) {
                        put(s, al, b, y, "air");
                    }
                }
                put(s, al, Ballpark.UNIT0 + 6, CONC + 1, al == g + 1 || al == g + 2 ? "st_barrier[facing=" + in + "]" : "st_turnstile[facing=" + in + "]");
            }
            for (int b = Ballpark.UNIT0; b <= Ballpark.UNIT1; b++) {
                for (int y = CONC + 1; y < CEIL; y++) {
                    put(s, g - 1, b, y, Ballpark.WALL);
                    put(s, g + 4, b, y, Ballpark.WALL);
                }
            }
            sign(s, g + 1, Ballpark.UNIT0 + 9, CONC + 3, "evac", in);
            put(s, g + 3, Ballpark.UNIT0 + 3, CONC + 1, "trade_banquet_table[facing=" + in + "]");
            put(s, g + 3, Ballpark.UNIT0 + 2, CONC + 1, "trade_folding_chair[facing=" + in + "]");
        }
    }

    /** Along the concourse's back: the machines and the bins in the gaps between the units, the posters. */
    private void concourse(int s) {
        String in = face(s, "in");
        for (int al = 2; al < Ballpark.POLE - 1; al += 3) {
            int b = Ballpark.UNIT0 - 1;
            boolean door = false;
            for (int k = -1; k <= 1; k++) {
                String g = get(s, al + k, Ballpark.UNIT0, CONC + 1);
                door |= g == null || g.equals("air") || g.contains("door") || g.contains("counter") || g.contains("register");
            }
            if (door || !free(s, al, b, CONC + 1)) {
                continue;
            }
            double q = a.chance();
            if (q < 0.2) {
                put(s, al, b, CONC + 1, "pk_vending[facing=" + in + "]");
            } else if (q < 0.4) {
                put(s, al, b, CONC + 1, "bin_ribbed");
            } else if (q < 0.55) {
                put(s, al, b, CONC + 2, (a.chance() < 0.5 ? "st_poster_missing" : "st_poster_missing_b") + "[facing=" + in + "]");
            }
        }
    }

    // ------------------------------------------------------------------ behind home

    /**
     * The round behind home: on the event level the corridor carried round, the landing zone's
     * operations hall behind it; on the concourse the rotunda where the lines were processed.
     */
    private void round() {
        for (int x = 0; x < a.w; x++) {
            for (int z = 0; z < a.d; z++) {
                if (Ballpark.side(x, z) != 2 || !Ballpark.grand(x, z)) {
                    continue;
                }
                int b = a.band[x][z];
                if (b < Ballpark.CORR0 || b > Ballpark.PASS1) {
                    continue;
                }
                a.set(x, EV, z, b >= ROOM0 && b <= ROOM1 ? "floor_concrete" : "floor_lino");
                boolean wall = b == ROOM0 - 1 || b == ROOM1 + 1;
                double psi = Math.toDegrees(Math.atan2(-Ballpark.c(z), -Ballpark.a(x)));
                boolean doorway = Math.abs(psi - 45) < 4 || Math.abs(psi - 20) < 3 || Math.abs(psi - 70) < 3;
                for (int y = EV + 1; y < EV + CEIL; y++) {
                    a.set(x, y, z, wall && !(doorway && y <= EV + 3) ? Ballpark.WALL : "air");
                }
                boolean lamp = Math.floorMod(x * 3 + z * 7, 11) == 0;
                a.set(x, EV + CEIL, z, lamp ? (a.power && a.chance() < 0.3 ? "fit_troffer_on" : "fit_troffer") + "[axis=x]" : "fit_ceiling");
                // the concourse's rotunda: the processing tables and the barriers
                if (b >= Ballpark.UNIT0 && b <= Ballpark.UNIT1) {
                    a.set(x, CONC, z, a.chance() < 0.15 ? "st_floor_b" : "st_floor");
                    a.set(x, CEIL, z, Math.floorMod(x + z, 9) == 0 ? "fit_troffer[axis=x]" : "fit_ceiling");
                }
            }
        }
        // the operations hall's furniture and the rotunda's lines, on a coarse grid
        for (int x = 0; x < a.w; x += 2) {
            for (int z = 0; z < a.d; z += 2) {
                if (Ballpark.side(x, z) != 2 || !Ballpark.grand(x, z)) {
                    continue;
                }
                int b = a.band[x][z];
                String in = Ballpark.in(x, z);
                if (b >= ROOM0 + 2 && b <= ROOM1 - 2 && a.empty(x, EV + 1, z) && a.empty(x, EV + 2, z)) {
                    double q = a.chance();
                    a.set(x, EV + 1, z, (q < 0.25 ? "trade_banquet_table" : q < 0.4 ? "bunk_map_table" : q < 0.52 ? "bunk_radio_desk"
                            : q < 0.62 ? "sc_cot_head" : q < 0.7 ? "st_pallet_water" : q < 0.78 ? "bunk_crate_ration" : "trade_folding_chair") + "[facing=" + in + "]");
                }
                if (b >= Ballpark.UNIT0 + 2 && b <= Ballpark.UNIT1 - 2 && a.empty(x, CONC + 1, z) && a.chance() < 0.5) {
                    a.set(x, CONC + 1, z, a.any("st_barrier", "trade_banquet_table", "trade_folding_chair", "st_barrier_down") + "[facing=" + in + "]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** A door in the wall at (al, b): the unit fronts' on the concourse, every other on the event level. */
    private void door(int s, int al, int b, String type, String facing) {
        int[] q = Ballpark.at(s, al, b);
        a.door(q[0], b == Ballpark.UNIT0 ? CONC : EV, q[1], type, facing);
    }

    /** A two-wide sign on the wall at al .. al + 1 (on the wall's near side, cell b), facing the reader. */
    private void sign(int s, int al, int b, int y, String key, String facing) {
        int[] p0 = Ballpark.at(s, al, b), p1 = Ballpark.at(s, al + 1, b);
        a.sign(key, Math.min(p0[0], p1[0]), y, Math.min(p0[1], p1[1]), facing);
    }

    private static String sign(String kind) {
        return switch (kind) {
            case "home" -> "home";
            case "visitors" -> "visitors";
            case "umpires" -> "umpires";
            case "cages" -> "cages";
            case "grounds" -> "grounds";
            case "laundry" -> "laundry";
            case "armory" -> "armory";
            case "press" -> "press";
            case "kitchen" -> "kitchen";
            case "office" -> "office";
            case "aid" -> "aid";
            default -> "morgue";
        };
    }

    private void litter(int s, int lo, int hi, double density) {
        for (int al = lo; al <= hi; al++) {
            for (int b = ROOM0; b <= ROOM1; b++) {
                if (a.chance() < density && free(s, al, b, EV + 1)) {
                    double q = a.chance();
                    put(s, al, b, EV + 1, q < 0.4 ? "paper" : q < 0.6 ? "blood_splat" : q < 0.75 ? "trash_bag[facing=" + a.dir() + "]" : q < 0.85 ? "glass_shards"
                            : "home_blood_pool");
                }
            }
        }
    }

    private static final java.util.Set<String> FLOORS = java.util.Set.of("st_floor", "st_floor_b", "floor_concrete", "st_tread", "st_tread_gum",
            "floor_lino");

    /** What's left on the concourse and the walkways: on the floor only, never on a step or a seat. */
    private void strew() {
        for (int x = 0; x < a.w; x++) {
            for (int z = 0; z < a.d; z++) {
                for (int y : new int[] {CONC, Ballpark.UPW, EV}) {
                    String g = a.get(x, y, z);
                    if (g == null || !FLOORS.contains(g) || !a.empty(x, y + 1, z) || a.chance() > 0.02) {
                        continue;
                    }
                    double r = a.chance();
                    a.set(x, y + 1, z, r < 0.5 ? "paper" : r < 0.62 ? "blood_splat" : r < 0.7 ? "glass_shards"
                            : r < 0.85 ? "trash_bag[facing=" + a.dir() + "]" : "home_blood_pool");
                }
            }
        }
    }
}
