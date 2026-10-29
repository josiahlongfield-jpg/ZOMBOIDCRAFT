package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Hospital.CN0;
import static com.zomboidcraft.world.county.Hospital.CN1;
import static com.zomboidcraft.world.county.Hospital.CORE0;
import static com.zomboidcraft.world.county.Hospital.CORE1;
import static com.zomboidcraft.world.county.Hospital.CS0;
import static com.zomboidcraft.world.county.Hospital.CS1;
import static com.zomboidcraft.world.county.Hospital.EX0;
import static com.zomboidcraft.world.county.Hospital.PX0;
import static com.zomboidcraft.world.county.Hospital.PX1;
import static com.zomboidcraft.world.county.Hospital.PZ0;
import static com.zomboidcraft.world.county.Hospital.PZ1;
import static com.zomboidcraft.world.county.Hospital.S;
import static com.zomboidcraft.world.county.Hospital.WX0;
import static com.zomboidcraft.world.county.Hospital.WX1;
import static com.zomboidcraft.world.county.Hospital.WZ1;

import com.zomboidcraft.world.city.Doors;

/**
 * {@link Hospital}'s podium, floor by floor, and its service wing.
 *
 * <p>Every podium floor has the same bones: a north band of rooms (z 17 to 34) behind the north
 * corridor (36 to 38), the core's band (39 to 48: the stairs and lifts, rooms between them), the
 * south corridor (49 to 51), the south band (53 to 87); the emergency department (or what's over
 * it) east of x = 100, the corridors running into it.
 * <ul>
 * <li>ground: the chapel, the gift shop, the lobby and its desk, admitting; washrooms, the
 * outpatient pharmacy, security; the cafeteria, the lab and the pharmacy behind it, radiology;
 * the emergency department with its waiting room, bays, station and trauma rooms.</li>
 * <li>second: pre-op, the surgical waiting room, offices; the staff's lockers, anaesthesia's
 * workroom, sterile supply; five theatres off their own corridor, recovery behind them; intensive
 * care in glass-fronted rooms round its station.</li>
 * <li>third: administration, the board room; records, the mail room, a staff lounge; the clinics'
 * exam rooms either side of their corridor; physical therapy's gym.</li>
 * </ul>
 */
final class HospitalDepts {
    private final Hospital h;
    private static final int NZ0 = PZ0 + 1, NZ1 = 34, NW = 35, SZ0 = 53, SZ1 = PZ1 - 1, SW = 52;

    HospitalDepts(Hospital h) {
        this.h = h;
    }

    private void set(int x, int y, int z, String b) {
        h.set(x, y, z, b);
    }

    private double chance() {
        return h.chance();
    }

    void build() {
        for (int f = 0; f < 3; f++) {
            bones(f * S);
        }
        ground();
        second();
        third();
        er();
        icu();
        gym();
        wing();
        for (int f = 0; f < 3; f++) {
            clutter(f * S);
        }
    }

    /** The walls every floor shares: the corridors' sides, the core band's, the emergency side's. */
    private void bones(int y) {
        // north corridor's north wall (the lobby open on the ground floor), the core band's walls
        for (int x = PX0 + 1; x < EX0 - 1; x++) {
            if (!(y == 0 && x >= 44 && x <= 88)) {
                h.wallX(x, x, NW, y);
            }
        }
        h.wallX(PX0 + 1, EX0 - 2, CORE0, y);
        h.wallX(PX0 + 1, EX0 - 2, CORE1, y);
        h.wallX(PX0 + 1, EX0 - 2, SW, y);
        // the emergency side's wall, open to the corridors
        h.wallZ(EX0 - 1, NZ0, SZ1, y);
        h.opening(EX0 - 1, EX0 - 1, CN0, CN1, y);
        h.opening(EX0 - 1, EX0 - 1, CS0, CS1, y);
        // the core band's rooms' ends
        for (int x : new int[] {30, 36, 55, 69, 94}) {
            h.wallZ(x, CORE0, CORE1, y);
        }
        h.lamps(PX0, CN0 - 1, EX0 - 1, CN1 + 1, y + S, 0.4);
        h.lamps(PX0, CS0 - 1, EX0 - 1, CS1 + 1, y + S, 0.4);
    }

    /** The corridors' clutter once the rooms are in: gurneys pushed to the walls (never across a door), a body bag on one. */
    private void clutter(int y) {
        for (int x = PX0 + 3; x < EX0 - 3; x += 7) {
            for (int z : new int[] {CN0, CS1}) {
                if (chance() > 0.35 || !h.solid(x - 2, x + 3, y, z == CN0 ? NW : SW)) {
                    continue;
                }
                String f = z == CN0 ? "east" : "west";
                int x2 = x + (f.equals("east") ? 1 : -1);
                if (h.empty(x, y + 1, z) && h.empty(x2, y + 1, z)) {
                    boolean bag = chance() < 0.4;
                    set(x, y + 1, z, (bag ? "hs_gurney_bag_a" : "hs_gurney_a") + "[facing=" + f + "]");
                    set(x2, y + 1, z, (bag ? "hs_gurney_bag_b" : "hs_gurney_b") + "[facing=" + f + "]");
                }
            }
        }
        h.mess(PX0 + 1, CN0, EX0 - 2, CN1, y, 0.05);
        h.mess(PX0 + 1, CS0, EX0 - 2, CS1, y, 0.05);
    }

    // ------------------------------------------------------------------ ground

    private void ground() {
        int y = 0;
        // the chapel: pews to the altar on the north wall, the votives
        h.wallZ(30, NZ0, NZ1, y);
        h.door(18, y, NW, "flat_door", "south");
        h.door(19, y, NW, "flat_door", "south");
        h.sign("chapel", 21, y + 3, NW + 1, "south");
        for (int x = 15; x <= 23; x++) {
            set(x, y + 1, NZ0 + 1, x == 19 ? "ch_altar_cross[facing=south]" : x == 15 || x == 23 ? "ch_votive" + (h.power ? "_lit" : "") : "air");
        }
        for (int z = NZ0 + 5; z <= NZ1 - 2; z += 2) {
            for (int x = 11; x <= 27; x++) {
                if (x >= 18 && x <= 20) {
                    continue;
                }
                set(x, y + 1, z, (chance() < 0.08 ? "ch_pew_pale_broken" : "ch_pew_pale") + "[facing=north]");
            }
        }
        h.lamps(PX0, NZ0 - 1, 30, NZ1 + 1, y + S, 0.3);
        // the gift shop: cards and candy, the cooler, the till, its glass door to the lobby
        h.wallZ(43, NZ0, NZ1, y);
        h.door(43, y, 26, "shop_door", "east");
        for (int z = NZ0 + 2; z <= NZ1 - 2; z++) {
            set(31, y + 1, z, "store_shelf_thrift[facing=east]");
            if (z % 4 != 0) {
                set(36, y + 1, z, "store_shelf_grocery[facing=east]");
                set(37, y + 1, z, "store_shelf_grocery[facing=west]");
            }
        }
        set(42, y + 1, NZ0 + 1, "store_cooler[facing=west]");
        set(42, y + 2, NZ0 + 1, "store_cooler_top[facing=west]");
        set(41, y + 1, 30, "store_counter[facing=north]");
        set(40, y + 1, 30, "store_register[facing=north]");
        h.lamps(30, NZ0 - 1, 43, NZ1 + 1, y + S, 0.3);
        // the lobby: the desk, the waiting chairs facing each other, the vending, the planters
        for (int x = 62; x <= 69; x++) {
            set(x, y + 1, 27, (x == 62 || x == 69 ? "hs_station_end" : "hs_station") + "[facing=north]");
        }
        set(65, y + 1, 29, "office_chair[facing=north]");
        set(66, y + 1, 29, "office_chair_fallen[facing=" + h.dir() + "]");
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 46 : 74;
            for (int z = 20; z <= 32; z += 4) {
                for (int x = x0; x <= x0 + 10; x += 2) {
                    if (chance() < 0.1) {
                        set(x, y + 1, z, "hs_chairs_tipped[facing=" + h.dir() + "]");
                        continue;
                    }
                    set(x, y + 1, z, "hs_chairs" + (side == 0 ? "" : "_b") + "[facing=north]");
                    set(x, y + 1, z + 1, "hs_chairs" + (side == 0 ? "" : "_b") + "[facing=south]");
                }
            }
        }
        for (int x : new int[] {45, 87}) {
            set(x, y + 1, NZ0 + 1, "store_vending[facing=" + (x == 45 ? "east" : "west") + "]");
            set(x, y + 2, NZ0 + 1, "store_vending_top[facing=" + (x == 45 ? "east" : "west") + "]");
            set(x, y + 1, NZ1 - 1, "planter");
        }
        for (int x = 50; x <= 82; x += 8) {
            for (int k = 1; k < S; k++) {
                set(x, y + k, 24, "tx_column");
            }
        }
        h.lamps(43, NZ0 - 1, 89, NZ1 + 4, y + S, 0.45);
        h.mess(44, NZ0, 88, NZ1, y, 0.06);
        // admitting: the counter's windows to the lobby, the desks behind
        h.wallZ(89, NZ0, NZ1, y);
        for (int z = 19; z <= 27; z++) {
            set(89, y + 1, z, "store_counter[facing=west]");
            set(89, y + 2, z, z % 3 == 0 ? "hs_wall" : "store_teller_glass[axis=z]");
        }
        h.door(89, y, 31, "flat_door", "west");
        h.sign("admitting", 88, y + 4, 22, "west");
        for (int z = 19; z <= 27; z += 4) {
            set(92, y + 1, z, "home_desk[facing=west]");
            set(93, y + 1, z, "office_chair[facing=west]");
        }
        for (int z = NZ0; z <= NZ1; z += 2) {
            set(99, y + 1, z, "filing_cabinet[facing=west]");
        }
        h.lamps(89, NZ0 - 1, EX0 - 1, NZ1 + 1, y + S, 0.3);
        // washrooms off the north corridor, the outpatient pharmacy's window, security
        washroom(9, 19, y);
        washroom(20, 29, y);
        for (int x = 39; x <= 50; x++) {
            set(x, y + 1, CORE0, "store_counter[facing=north]");
            set(x, y + 2, CORE0, x % 4 == 0 ? "hs_wall" : "store_teller_glass[axis=x]");
        }
        h.sign("pharmacy", 44, y + 4, CORE0 - 1, "north");
        h.door(53, y, CORE0, "flat_door", "north");
        for (int x = 38; x <= 53; x += 3) {
            for (int z = CORE0 + 3; z <= CORE1 - 2; z++) {
                set(x, y + 1, z, "store_shelf_pharmacy[facing=east]");
                set(x, y + 2, z, "store_shelf_pharmacy_top[facing=east]");
            }
        }
        set(54, y + 1, CORE1 - 1, "store_safe[facing=north]");
        h.lamps(36, CORE0, 55, CORE1, y + S, 0.3);
        office(69, 81, CORE0, CORE1, y, "security");
        storeroom(82, 94, CORE0, CORE1, y, "hs_cart_linen");
        // the cafeteria: tables in rows, the servery along the back, the kitchen through it
        h.wallZ(46, SZ0, SZ1, y);
        h.door(20, y, SW, "flat_door", "south");
        h.door(21, y, SW, "flat_door", "south");
        h.door(38, y, SW, "flat_door", "south");
        h.sign("cafeteria", 23, y + 3, SW - 1, "north");
        for (int x = 12; x <= 42; x += 5) {
            for (int z = SZ0 + 3; z <= 79; z += 4) {
                set(x, y + 1, z, chance() < 0.12 ? "sc_cafe_folded[facing=north]" : "sc_cafe_table[axis=x]");
                set(x + 1, y + 1, z, chance() < 0.12 ? "air" : "sc_cafe_table[axis=x]");
            }
        }
        for (int x = 10; x <= 44; x++) {
            set(x, y + 1, 84, x % 6 == 0 ? "fit_urn[facing=north]" : "fit_serving[facing=north]");
        }
        h.door(25, y, PZ1, "fire_door", "south");
        h.lamps(PX0, SZ0 - 1, 46, SZ1 + 1, y + S, 0.3);
        h.mess(9, SZ0, 45, SZ1, y, 0.08);
        // the lab: benches in rows, the blood fridge, the pharmacy behind
        h.wallZ(68, SZ0, SZ1, y);
        h.wallX(47, 67, 71, y);
        h.door(57, y, SW, "flat_door", "south");
        h.door(57, y, 71, "flat_door", "south");
        h.sign("lab", 59, y + 3, SW - 1, "north");
        for (int z = SZ0 + 3; z <= 68; z += 4) {
            for (int x = 49; x <= 66; x++) {
                if (x == 57 || x == 58) {
                    continue;
                }
                set(x, y + 1, z, "hs_lab_bench[facing=north]");
            }
        }
        set(67, y + 1, 55, "hs_bloodfridge" + (h.power ? "_on" : "") + "[facing=west]");
        set(67, y + 2, 55, "hs_cabinet[facing=west]");
        for (int x = 48; x <= 66; x += 3) {
            for (int z = 73; z <= 85; z++) {
                if (z == 79) {
                    continue;
                }
                set(x, y + 1, z, "store_shelf_pharmacy[facing=east]");
                set(x, y + 2, z, "store_shelf_pharmacy_top[facing=east]");
            }
        }
        h.lamps(46, SZ0 - 1, 68, SZ1 + 1, y + S, 0.3);
        // radiology: the waiting room, the reading room's viewers, three x-ray rooms
        h.door(84, y, SW, "flat_door", "south");
        h.door(85, y, SW, "flat_door", "south");
        h.sign("radiology", 86, y + 3, SW - 1, "north");
        for (int x = 70; x <= 98; x += 2) {
            set(x, y + 1, 56, "hs_chairs_b[facing=south]");
        }
        h.wallX(69, EX0 - 2, 61, y);
        h.wallX(69, EX0 - 2, 69, y);
        h.door(75, y, 61, "flat_door", "south");
        for (int x = 70; x <= 80; x++) {
            set(x, y + 2, 68, "hs_lightbox" + (h.power && chance() < 0.4 ? "_on" : "") + "[facing=north]");
        }
        for (int x = 71; x <= 79; x += 4) {
            set(x, y + 1, 66, "home_desk[facing=south]");
            set(x, y + 1, 65, "office_chair[facing=south]");
        }
        h.wallZ(81, 62, 68, y);
        for (int x0 : new int[] {69, 79, 89}) {
            int x1 = x0 + 10;
            h.wallZ(x1, 70, SZ1, y);
            h.door(x0 + 4, y, 69, "fire_door", "south");
            set(x0 + 5, y + 1, 78, "hs_xray_table[facing=north]");
            set(x0 + 5, y + 1, 79, "hs_xray_table[facing=north]");
            set(x0 + 5, y + 4, 78, "hs_xray_arm[facing=east]");
            set(x0 + 2, y + 2, SZ1, "hs_lightbox" + (h.power && chance() < 0.3 ? "_on" : "") + "[facing=north]");
            set(x0 + 8, y + 1, SZ1, "hs_cabinet_lo[facing=north]");
            set(x0 + 1, y + 1, 72, "hs_wheelchair[facing=" + h.dir() + "]");
        }
        h.lamps(68, SZ0 - 1, EX0 - 1, SZ1 + 1, y + S, 0.3);
    }

    /** Washrooms between x0 and x1 in the core band: the stalls along the back, the basins by the door. */
    private void washroom(int x0, int x1, int y) {
        h.tileX(x0, x1, CORE1 - 1, y);
        h.door(x0 + 2, y, CORE0, "flat_door", "north");
        for (int x = x0 + 1; x < x1; x += 2) {
            set(x, y + 1, CORE1 - 2, "home_toilet[facing=north]");
        }
        for (int x = x0 + 4; x < x1; x += 2) {
            set(x, y + 1, CORE0 + 1, "hs_sink[facing=south]");
            set(x, y + 2, CORE0 + 1, "home_mirror[facing=south]");
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = CORE0 + 1; z < CORE1; z++) {
                set(x, y, z, "bunk_tile" + (chance() < 0.3 ? "_dirty" : ""));
            }
        }
        h.lamps(x0, CORE0, x1, CORE1, y + S, 0.25);
    }

    /** An office in the core band: desks, the cabinets, the screens (security's, the cameras). */
    private void office(int x0, int x1, int z0, int z1, int y, String kind) {
        h.door(x0 + 2, y, z0, "flat_door", "north");
        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            set(x, y + 1, z0 + 3, "home_desk[facing=south]");
            set(x, y + 1, z0 + 2, "office_chair[facing=south]");
            if (kind.equals("security")) {
                set(x, y + 2, z0 + 3, "hs_monitor" + (h.power && chance() < 0.5 ? "_on" : "") + "[facing=north]");
            }
        }
        for (int x = x0 + 1; x < x1; x += 2) {
            set(x, y + 1, z1 - 1, kind.equals("security") ? "hs_locker[facing=north]" : "filing_cabinet[facing=north]");
            if (kind.equals("security")) {
                set(x, y + 2, z1 - 1, "hs_locker_top[facing=north]");
            }
        }
        h.lamps(x0, z0, x1, z1, y + S, 0.3);
    }

    /** A store room: shelves of boxes, a cart or two. */
    private void storeroom(int x0, int x1, int z0, int z1, int y, String cart) {
        h.door(x1 - 2, y, z1, "fire_door", "south");
        for (int x = x0 + 1; x < x1; x++) {
            set(x, y + 1, z0 + 1, "hs_cabinet_lo[facing=south]");
            set(x, y + 2, z0 + 1, "hs_cabinet[facing=south]");
        }
        for (int x = x0 + 2; x < x1 - 2; x += 3) {
            set(x, y + 1, z0 + 4, cart + "[facing=" + h.dir() + "]");
            if (chance() < 0.5) {
                set(x + 1, y + 1, z0 + 5, "cardboard_box[facing=" + h.dir() + "]");
            }
        }
        h.lamps(x0, z0, x1, z1, y + S, 0.25);
    }

    // ------------------------------------------------------------------ second: theatres

    private void second() {
        int y = S;
        // pre-op: curtained bays along the north wall, gurneys in them
        h.sign("surgery", 30, y + 3, NW + 1, "south");
        h.door(26, y, NW, "flat_door", "south");
        h.door(27, y, NW, "flat_door", "south");
        bays(10, 42, NZ0, y, "north", false);
        h.wallZ(43, NZ0, NZ1, y);
        h.lamps(PX0, NZ0 - 1, 43, NZ1 + 1, y + S, 0.3);
        // the surgical waiting room: sofas, chairs, the television, the coffee
        h.door(43, y, 30, "flat_door", "west");
        for (int x = 46; x <= 86; x += 6) {
            set(x, y + 1, 20, "home_sofa_blue_l[facing=south]");
            set(x + 1, y + 1, 20, "home_sofa_blue_m[facing=south]");
            set(x + 2, y + 1, 20, "home_sofa_blue_r[facing=south]");
            set(x + 1, y + 1, 22, "home_coffee_table[facing=north]");
        }
        for (int x = 46; x <= 86; x += 2) {
            set(x, y + 1, 30, "hs_chairs[facing=north]");
        }
        set(66, y + 1, NZ0, "home_tv" + (chance() < 0.4 ? "_smashed" : "") + "[facing=south]");
        set(70, y + 1, NZ0, "store_coffee[facing=south]");
        h.opening(60, 63, NW, NW, y);
        h.opening(70, 73, NW, NW, y);
        h.lamps(43, NZ0 - 1, 89, NZ1 + 1, y + S, 0.35);
        h.mess(44, NZ0, 88, NZ1, y, 0.05);
        // offices
        h.wallZ(89, NZ0, NZ1, y);
        h.wallX(89, EX0 - 2, 25, y);
        h.door(92, y, NW, "flat_door", "south");
        h.door(92, y, 25, "flat_door", "south");
        for (int z : new int[] {19, 28}) {
            set(94, y + 1, z, "home_desk[facing=west]");
            set(95, y + 1, z, "office_chair[facing=west]");
            set(98, y + 1, z + 2, "filing_cabinet[facing=west]");
        }
        h.lamps(89, NZ0 - 1, EX0 - 1, NZ1 + 1, y + S, 0.3);
        // the staff's lockers, anaesthesia's workroom, sterile supply
        lockers(9, 29, y);
        h.door(45, y, CORE0, "flat_door", "north");
        for (int x = 37; x <= 54; x++) {
            set(x, y + 1, CORE1 - 1, x % 5 == 0 ? "hs_oxygen[facing=north]" : "hs_cabinet_lo[facing=north]");
            set(x, y + 2, CORE1 - 1, "hs_cabinet[facing=north]");
        }
        for (int x = 38; x <= 52; x += 4) {
            set(x, y + 1, CORE0 + 3, "hs_anesthesia[facing=" + h.dir() + "]");
        }
        h.lamps(36, CORE0, 55, CORE1, y + S, 0.3);
        storeroom(69, 94, CORE0, CORE1, y, "hs_cart_med");
        // the theatres off their own corridor (z 53 to 56), two to a bay, the sterile core behind
        // them (z 68 to 78), recovery behind that (z 80 to 87)
        int cz = 57, sz = 67, rz = 79;
        h.wallX(PX0 + 1, EX0 - 2, cz, y);
        h.wallX(PX0 + 1, EX0 - 2, rz, y);
        h.door(50, y, SW, "fire_door", "south");
        h.door(51, y, SW, "fire_door", "south");
        h.sign("staff", 53, y + 3, SW - 1, "north");
        for (int x = 12; x <= 96; x += 6) {
            set(x, y + 1, SW + 1, "hs_scrub_sink[facing=south]");
        }
        int[] ors = {12, 29, 46, 63, 80};
        for (int k = 0; k < ors.length; k++) {
            theatre(ors[k], ors[k] + 8, cz, sz, y, 2 * k);
            theatre(ors[k] + 8, ors[k] + 16, cz, sz, y, 2 * k + 1);
        }
        sterileCore(12, 96, sz, rz, y);
        // the passages at either end down to recovery
        h.wallZ(11, cz, rz, y);
        h.wallZ(97, cz, rz, y);
        h.opening(9, 10, cz, cz, y);
        h.opening(98, 99, cz, cz, y);
        h.opening(9, 10, rz, rz, y);
        h.opening(98, 99, rz, rz, y);
        h.door(11, y, 73, "fire_door", "west");
        h.door(97, y, 73, "fire_door", "east");
        for (int x = 11; x <= 96; x += 3) {
            if (chance() < 0.8) {
                bed(x, y, SZ1, "south", chance() < 0.5);
                if (chance() < 0.6) {
                    set(x + 1, y + 1, SZ1, "hs_monitor" + (h.power && chance() < 0.4 ? "_on" : "") + "[facing=north]");
                }
            }
            set(x + 2, y + 1, SZ1 - 1, "hs_curtain" + (chance() < 0.2 ? "_blood" : "") + "[facing=east]");
            set(x + 2, y + 2, SZ1 - 1, "hs_curtain_top[facing=east]");
        }
        for (int x = 46; x <= 62; x++) {
            set(x, y + 1, 82, (x == 46 || x == 62 ? "hs_station_end" : "hs_station") + "[facing=south]");
        }
        h.lamps(PX0, SZ0 - 1, EX0 - 1, cz + 1, y + S, 0.4);
        h.lamps(PX0, rz - 1, EX0 - 1, SZ1 + 1, y + S, 0.3);
        h.mess(9, rz + 1, 99, SZ1, y, 0.05);
    }

    /**
     * A theatre from x0 to x1 (its walls) between its corridor's wall at z0 and the sterile core's at
     * z1: glazed tile all round, the table under its lamp, the anaesthesia machine at the head, the
     * door to the core in the back corner.
     */
    private void theatre(int x0, int x1, int z0, int z1, int y, int k) {
        h.tileZ(x1, z0 + 1, z1 - 1, y);
        for (int x = x0; x <= x1; x++) {
            for (int kk = 1; kk < S; kk++) {
                set(x, y + kk, z1, chance() < 0.15 ? "hs_tile_b" : "hs_tile");
            }
        }
        for (int x = x0; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                set(x, y, z, "hs_slab_green");
            }
        }
        int cx = x0 + 4, cz = (z0 + z1) / 2;
        h.door(cx - 1, y, z0, "fire_door", "north");
        h.door(cx, y, z0, "fire_door", "north");
        int bx = x0 + 2;
        h.door(bx, y, z1, "fire_door", "south");
        set(cx, y + 1, cz, "hs_or_table[facing=north]");
        set(cx, y + 1, cz + 1, "hs_or_table[facing=north]");
        set(cx, y + 4, cz, "hs_or_light" + (h.power && chance() < 0.4 ? "_on" : "") + "[facing=north]");
        set(cx, y + 4, cz + 1, "hs_or_light[facing=north]");
        set(cx, y + 1, cz - 2, "hs_anesthesia[facing=south]");
        set(cx - 1, y + 1, cz - 1, "hs_monitor" + (h.power && chance() < 0.5 ? "_on" : "") + "[facing=east]");
        set(cx + 2, y + 1, cz + 1, "hs_mayo[facing=west]");
        set(cx - 2, y + 1, cz + 2, "hs_mayo[facing=east]");
        set(x1 - 1, y + 1, z0 + 1, "hs_cart_crash[facing=west]");
        set(x1 - 1, y + 1, z1 - 1, "hs_biohazard[facing=north]");
        set(x0 + 1, y + 1, z0 + 2, "hs_sharps[facing=east]");
        for (int x = bx + 2; x < x1 - 1; x++) {
            set(x, y + 2, z1 - 1, "hs_cabinet[facing=north]");
        }
        set(x0 + 1, y + 2, z0 + 3, "hs_lightbox" + (h.power && chance() < 0.3 ? "_on" : "") + "[facing=east]");
        if (k == 4 || chance() < 0.25) {
            // the one they didn't finish: the drapes on the floor, the blood down the table
            set(cx, y + 1, cz + 3, "home_blood_pool");
            set(cx + 1, y + 1, cz + 2, "blood_splat");
            set(cx - 1, y + 1, cz + 3, "hs_tray[facing=north]");
        }
        h.lamps(x0, z0, x1, z1, y + S, 0.3);
    }

    /**
     * The sterile core behind the theatres, x0 to x1 between z0 and z1 (the walls): the instrument
     * racks down both sides, the linen and supply carts in the aisle, the autoclaves at the ends.
     */
    private void sterileCore(int x0, int x1, int z0, int z1, int y) {
        h.sign("staff", 14, y + 3, z0 + 1, "south");
        for (int x = x0 + 1; x < x1; x++) {
            if (h.solid(x - 1, x + 1, y, z0)) {
                set(x, y + 1, z0 + 1, "hs_cabinet_lo[facing=south]");
                set(x, y + 2, z0 + 1, "hs_cabinet[facing=south]");
            }
            if (x % 9 != 0) {
                set(x, y + 1, z1 - 1, x % 7 == 0 ? "hs_oxygen[facing=north]" : "hs_cabinet_lo[facing=north]");
                set(x, y + 2, z1 - 1, "hs_cabinet[facing=north]");
            }
        }
        for (int x = x0 + 3; x < x1 - 3; x += 5) {
            if (chance() < 0.6) {
                set(x, y + 1, (z0 + z1) / 2, h.any("hs_cart_linen", "hs_cart_med", "hs_cart_meals") + "[facing=" + h.dir() + "]");
            }
            if (chance() < 0.3) {
                set(x + 2, y + 1, (z0 + z1) / 2 + 1, "cardboard_box[facing=" + h.dir() + "]");
            }
        }
        h.lamps(x0, z0, x1, z1, y + S, 0.3);
        h.mess(x0 + 1, z0 + 2, x1 - 1, z1 - 2, y, 0.04);
    }

    /** The staff's lockers between x0 and x1 in the core band: rows of them, the benches between. */
    private void lockers(int x0, int x1, int y) {
        h.door(x0 + 3, y, CORE0, "flat_door", "north");
        h.door(x1 - 3, y, CORE0, "flat_door", "north");
        for (int x = x0 + 1; x < x1; x++) {
            set(x, y + 1, CORE1 - 1, "hs_locker[facing=north]");
            set(x, y + 2, CORE1 - 1, "hs_locker_top[facing=north]");
            if (x % 2 == 0) {
                set(x, y + 1, CORE0 + 4, "th_bench_plain[facing=north]");
            }
        }
        h.lamps(x0, CORE0, x1, CORE1, y + S, 0.3);
    }

    /** A bed with its head at (x, z) to the wall on its side head, the foot out from it. */
    private void bed(int x, int y, int z, String head, boolean bloody) {
        int[] o = Bldg.step(Bldg.opposite(head));
        String suffix = bloody ? "_blood" : "";
        set(x, y + 1, z, "hs_bed_head" + suffix + "[facing=" + head + "]");
        set(x + o[0], y + 1, z + o[1], (chance() < 0.12 ? "hs_bed_foot_body" : "hs_bed_foot" + suffix) + "[facing=" + head + "]");
    }

    /** Curtained bays along a wall (heads to side head) from x0 to x1 at z (the wall's row inside). */
    private void bays(int x0, int x1, int z, int y, String head, boolean gurneys) {
        for (int x = x0; x <= x1; x += 3) {
            if (chance() < 0.85) {
                bed(x, y, z, head, chance() < 0.3);
            }
            set(x + 1, y + 1, z, "hs_iv");
            set(x + 2, y + 1, z + 1, "hs_curtain[facing=east]");
            set(x + 2, y + 2, z + 1, "hs_curtain_top[facing=east]");
            set(x + 2, y + 1, z, "hs_curtain_open[facing=east]");
            set(x + 2, y + 2, z, "hs_curtain_open_top[facing=east]");
        }
    }

    // ------------------------------------------------------------------ third: offices, records, clinics

    private void third() {
        int y = 2 * S;
        // administration: desks in rows, the board room at its east end
        h.door(20, y, NW, "flat_door", "south");
        h.door(50, y, NW, "flat_door", "south");
        h.sign("admin", 22, y + 3, NW + 1, "south");
        for (int z = NZ0 + 2; z <= NZ1 - 2; z += 4) {
            for (int x = 11; x <= 58; x += 3) {
                set(x, y + 1, z, chance() < 0.5 ? "home_desk[facing=south]" : "home_desk_pc[facing=south]");
                set(x, y + 1, z + 1, chance() < 0.15 ? "office_chair_fallen[facing=" + h.dir() + "]" : "office_chair[facing=north]");
            }
        }
        h.wallZ(60, NZ0, NZ1, y);
        for (int x0 : new int[] {60, 67, 74, 81}) {
            h.wallZ(x0 + 7, NZ0, NZ1, y);
            h.door(x0 + 3, y, NW, "flat_door", "south");
            set(x0 + 3, y + 1, NZ0 + 3, "home_desk[facing=south]");
            set(x0 + 3, y + 1, NZ0 + 4, "office_chair[facing=north]");
            set(x0 + 1, y + 1, NZ0, "home_bookcase[facing=south]");
            set(x0 + 1, y + 2, NZ0, "home_bookcase_top[facing=south]");
            set(x0 + 5, y + 1, NZ0, "filing_cabinet[facing=south]");
            set(x0 + 5, y + 1, NZ1 - 1, "home_plant" + (chance() < 0.7 ? "_dead" : ""));
        }
        h.door(93, y, NW, "flat_door", "south");
        for (int x = 91; x <= 97; x++) {
            for (int z = 22; z <= 28; z += 6) {
                set(x, y + 1, z, "of_conf_table[facing=north]");
            }
        }
        set(94, y + 2, NZ0, "of_whiteboard[facing=south]");
        h.lamps(PX0, NZ0 - 1, EX0 - 1, NZ1 + 1, y + S, 0.3);
        h.mess(9, NZ0, 99, NZ1, y, 0.06);
        // medical records: row on row of files; the mail room; a staff lounge
        h.door(20, y, CORE0, "fire_door", "north");
        h.sign("records", 16, y + 4, CORE0 - 1, "north");
        for (int x = 10; x <= 28; x += 2) {
            for (int z = CORE0 + 2; z <= CORE1 - 2; z++) {
                set(x, y + 1, z, "filing_cabinet[facing=" + (x % 4 == 0 ? "east" : "west") + "]");
            }
        }
        h.lamps(PX0, CORE0, 30, CORE1, y + S, 0.3);
        h.door(45, y, CORE0, "flat_door", "north");
        set(40, y + 1, CORE0 + 2, "of_copier[facing=north]");
        for (int x = 42; x <= 53; x++) {
            set(x, y + 1, CORE1 - 1, "cv_po_desk[facing=north]");
        }
        h.lamps(36, CORE0, 55, CORE1, y + S, 0.3);
        h.door(80, y, CORE0, "flat_door", "north");
        for (int x = 71; x <= 92; x += 5) {
            set(x, y + 1, 43, "home_table[facing=north]");
            set(x, y + 1, 42, "home_chair[facing=south]");
            set(x, y + 1, 44, chance() < 0.2 ? "home_chair_tipped[facing=" + h.dir() + "]" : "home_chair[facing=north]");
        }
        set(70, y + 1, CORE1 - 1, "home_fridge[facing=north]");
        set(70, y + 2, CORE1 - 1, "home_fridge_top[facing=north]");
        set(72, y + 1, CORE1 - 1, "home_microwave[facing=north]");
        set(73, y + 1, CORE1 - 1, "home_coffee_maker[facing=north]");
        set(75, y + 1, CORE1 - 1, "trade_water_cooler[facing=north]");
        h.lamps(69, CORE0, 94, CORE1, y + S, 0.3);
        // the clinics: exam rooms both sides of their corridor (z 69 to 71)
        h.wallX(PX0 + 1, EX0 - 2, 68, y);
        h.wallX(PX0 + 1, EX0 - 2, 72, y);
        h.opening(9, 10, SW, SW, y);
        h.opening(98, 99, SW, SW, y);
        h.opening(9, 10, 68, 68, y);
        h.opening(98, 99, 68, 68, y);
        h.wallZ(11, SZ0, 67, y);
        h.wallZ(97, SZ0, 67, y);
        for (int x0 = 11; x0 + 6 <= 97; x0 += 6) {
            h.wallZ(x0 + 6, SZ0, 67, y);
            h.wallZ(x0 + 6, 73, SZ1, y);
            exam(x0, SZ0, 67, y, "north");
            exam(x0, 73, SZ1, y, "south");
        }
        h.lamps(PX0, 68, EX0 - 1, 72, y + S, 0.35);
        h.mess(9, 69, 99, 71, y, 0.06);
    }

    /** An exam room x0 to x0 + 6, its door to the clinics' corridor; the table, the desk, the basin. */
    private void exam(int x0, int z0, int z1, int y, String side) {
        boolean north = side.equals("north");
        int dz = north ? z1 + 1 : z0 - 1, back = north ? z0 : z1;
        h.door(x0 + 2, y, dz, "flat_door", north ? "north" : "south");
        String in = north ? "south" : "north";
        set(x0 + 3, y + 1, back, "of_exam_table[facing=" + in + "]");
        set(x0 + 1, y + 1, back, "hs_sink[facing=" + in + "]");
        set(x0 + 1, y + 2, back, "hs_cabinet[facing=" + in + "]");
        set(x0 + 5, y + 1, (z0 + z1) / 2, "home_desk[facing=west]");
        set(x0 + 4, y + 1, (z0 + z1) / 2, "office_chair[facing=west]");
        set(x0 + 5, y + 2, back, "hs_lightbox" + (h.power && chance() < 0.2 ? "_on" : "") + "[facing=" + in + "]");
        set(x0 + 4, y + 1, back, "hs_sharps[facing=" + in + "]");
        h.lamps(x0, z0 - 1, x0 + 6, z1 + 1, y + S, 0.2);
    }

    // ------------------------------------------------------------------ the emergency department

    private void er() {
        int y = 0;
        // the walk-in waiting room, its triage window
        h.wallZ(110, NZ0, 30, y);
        h.wallX(EX0, 109, 31, y);
        for (int x = 103; x <= 108; x++) {
            set(x, y + 1, 31, "store_counter[facing=north]");
            set(x, y + 2, 31, "store_teller_glass[axis=x]");
        }
        h.door(EX0 + 1, y, 31, "flat_door", "north");
        for (int z = 19; z <= 28; z += 3) {
            for (int x = 102; x <= 108; x += 2) {
                set(x, y + 1, z, chance() < 0.1 ? "hs_chairs_tipped[facing=" + h.dir() + "]" : "hs_chairs[facing=north]");
            }
        }
        set(109, y + 1, NZ0, "store_vending[facing=west]");
        set(109, y + 2, NZ0, "store_vending_top[facing=west]");
        h.sign("emergency", 102, y + 4, 30, "north");
        // the ambulance doors' vestibule and the main floor; the station in the middle
        h.wallX(111, PX1 - 1, 21, y);
        h.door(116, y, 21, "fire_door", "north");
        h.door(117, y, 21, "fire_door", "north");
        h.wallX(111, PX1 - 1, 21, y);
        for (int x = 109; x <= 116; x++) {
            set(x, y + 1, 44, (x == 109 || x == 116 ? "hs_station_end" : "hs_station") + "[facing=north]");
            set(x, y + 1, 49, (x == 109 || x == 116 ? "hs_station_end" : "hs_station") + "[facing=south]");
        }
        for (int z = 45; z <= 48; z++) {
            set(109, y + 1, z, "hs_station[facing=west]");
            set(116, y + 1, z, "hs_station[facing=east]");
        }
        set(112, y + 1, 46, "office_chair[facing=north]");
        set(113, y + 1, 47, "office_chair_fallen[facing=" + h.dir() + "]");
        set(111, y + 1, 47, "hs_monitor" + (h.power ? "_on" : "") + "[facing=north]");
        // bays down the east wall, heads to it; down the west wall south of the corridors
        for (int z = 23; z <= 84; z += 3) {
            erBay(PX1 - 1, z, y, "east");
        }
        for (int z = 54; z <= 66; z += 3) {
            erBay(EX0, z, y, "west");
        }
        // the trauma rooms at the back
        h.wallX(EX0, 116, 69, y);
        h.wallZ(108, 70, PZ1 - 1, y);
        h.wallZ(116, 70, PZ1 - 1, y);
        for (int x0 : new int[] {EX0, 109}) {
            int cx = x0 + 3;
            h.door(cx, y, 69, "fire_door", "north");
            h.door(cx + 1, y, 69, "fire_door", "north");
            set(cx, y + 1, 78, "hs_or_table[facing=north]");
            set(cx, y + 1, 79, "hs_or_table[facing=north]");
            set(cx, y + 4, 78, "hs_or_light" + (h.power ? "_on" : "") + "[facing=north]");
            set(cx - 2, y + 1, 77, "hs_monitor" + (h.power && chance() < 0.6 ? "_on" : "") + "[facing=east]");
            set(cx + 2, y + 1, 77, "hs_cart_crash[facing=west]");
            set(cx + 2, y + 1, 80, "hs_mayo[facing=west]");
            set(cx - 1, y + 1, PZ1 - 1, "hs_oxygen[facing=north]");
            set(cx, y + 1, 81, "home_blood_pool");
            for (int x = x0 + 1; x < x0 + 6; x++) {
                set(x, y + 2, PZ1 - 1, "hs_cabinet[facing=north]");
            }
            h.lamps(x0, 69, x0 + 7, PZ1, y + S, 0.4);
        }
        h.lamps(EX0 - 1, 21, PX1, 69, y + S, 0.45);
        h.mess(EX0, 22, PX1 - 1, 68, y, 0.08);
        // where they made their stand: the station's barricade of gurneys and beds
        for (int x = 107; x <= 118; x++) {
            if (chance() < 0.45) {
                set(x, y + 1, 42, any("hs_gurney_b[facing=east]", "hs_wheelchair_tipped[facing=north]", "hs_chairs_tipped[facing=north]", "store_shelf_fallen[facing=north]"));
            }
        }
    }

    private String any(String... options) {
        return h.any(options);
    }

    /** A bay in the emergency department: the bed (or gurney) to the wall, its curtains, the monitor. */
    private void erBay(int x, int z, int y, String head) {
        if (chance() < 0.8) {
            bed(x, y, z, head, chance() < 0.4);
        }
        int dx = head.equals("east") ? -1 : 1;
        set(x, y + 1, z + 1, "hs_monitor" + (h.power && chance() < 0.4 ? "_on" : "") + "[facing=" + Bldg.opposite(head) + "]");
        set(x, y + 1, z + 2, "hs_curtain" + (chance() < 0.25 ? "_blood" : "") + "[facing=north]");
        set(x, y + 2, z + 2, "hs_curtain_top[facing=north]");
        set(x + dx, y + 1, z + 2, "hs_curtain[facing=north]");
        set(x + dx, y + 2, z + 2, "hs_curtain_top[facing=north]");
        set(x + 2 * dx, y + 2, z + 2, "hs_track[facing=north]");
        set(x + 2 * dx, y + 2, z, "hs_curtain_open_top[facing=east]");
        set(x + 2 * dx, y + 1, z, "hs_curtain_open[facing=east]");
    }

    // ------------------------------------------------------------------ intensive care (over the emergency department)

    private void icu() {
        int y = S;
        h.sign("icu", 102, y + 4, CN0 - 1, "north");
        // glass-fronted rooms along the east wall and the west, the station between
        for (int z0 = NZ0; z0 + 5 <= SZ1; z0 += 6) {
            icuRoom(117, PX1 - 1, z0, z0 + 5, y, "east");
            if (z0 + 5 < CN0 - 1 || z0 > CS1 + 1) {
                icuRoom(EX0, 106, z0, z0 + 5, y, "west");
            }
        }
        for (int z = 44; z <= 56; z++) {
            set(110, y + 1, z, (z == 44 || z == 56 ? "hs_station_end" : "hs_station") + "[facing=west]");
            set(113, y + 1, z, (z == 44 || z == 56 ? "hs_station_end" : "hs_station") + "[facing=east]");
        }
        for (int z = 46; z <= 54; z += 4) {
            set(110, y + 2, z, "hs_monitor" + (h.power && chance() < 0.6 ? "_on" : "") + "[facing=east]");
            set(112, y + 1, z, "office_chair[facing=" + h.dir() + "]");
        }
        h.lamps(106, NZ0, 117, SZ1, y + S, 0.45);
        h.mess(107, NZ0, 116, SZ1, y, 0.06);
    }

    /** An intensive care room from x0 to x1, z0 to z1; its glass front on the station's side, the bed's head to the outer wall. */
    private void icuRoom(int x0, int x1, int z0, int z1, int y, String head) {
        boolean east = head.equals("east");
        int front = east ? x0 - 1 : x1 + 1, wall = east ? x1 : x0;
        h.wallX(x0, x1, z1 + 1 > PZ1 - 1 ? z1 : z1 + 1, y);
        for (int z = z0; z <= z1; z++) {
            for (int k = 1; k < S; k++) {
                set(front, y + k, z, k == 4 ? "hs_wall" : chance() < 0.15 ? "tx_glass_cracked[axis=z]" : "tx_glass[axis=z]");
            }
        }
        h.door(front, y, z0 + 1, "shop_door", east ? "west" : "east");
        int bz = (z0 + z1) / 2;
        bed(wall, y, bz, head, chance() < 0.35);
        set(wall, y + 1, bz - 1, "hs_monitor" + (h.power && chance() < 0.5 ? "_on" : "") + "[facing=" + (east ? "west" : "east") + "]");
        set(wall, y + 1, bz + 1, "hs_iv");
        set(wall, y + 1, z1, "hs_oxygen[facing=" + (east ? "west" : "east") + "]");
        set(wall, y + 2, z0, "hs_cabinet[facing=" + (east ? "west" : "east") + "]");
        if (chance() < 0.4) {
            set(front, y + 1, z0 + 3, "hs_tape[facing=east]");
        }
        h.lamps(x0 - 1, z0 - 1, x1 + 1, z1 + 1, y + S, 0.3);
    }

    // ------------------------------------------------------------------ physical therapy (over intensive care)

    private void gym() {
        int y = 2 * S;
        h.sign("visitors", 102, y + 4, CN0 - 1, "north");
        String[] kit = {"of_treadmill[facing=north]", "of_bike[facing=north]", "of_mat", "of_exam_table[facing=north]"};
        for (int z = NZ0 + 2; z <= SZ1 - 2; z += 4) {
            for (int x = 103; x <= 121; x += 3) {
                if (z >= CN0 - 1 && z <= CS1 + 1) {
                    continue;
                }
                set(x, y + 1, z, kit[rng(kit.length)]);
            }
        }
        // the parallel bars, the mirror wall
        for (int z = 60; z <= 68; z++) {
            set(108, y + 2, z, "railing[facing=east]");
            set(111, y + 2, z, "railing[facing=west]");
        }
        for (int z = NZ0; z <= SZ1; z += 1) {
            if (z % 3 != 0) {
                set(PX1 - 1, y + 2, z, "of_mirror[facing=west]");
            }
        }
        set(118, y + 1, 70, "hs_wheelchair[facing=" + h.dir() + "]");
        set(104, y + 1, 76, "hs_wheelchair_tipped[facing=" + h.dir() + "]");
        h.lamps(EX0 - 1, NZ0, PX1, SZ1, y + S, 0.35);
        h.mess(EX0, NZ0, PX1 - 1, SZ1, y, 0.04);
    }

    private int rng(int n) {
        return (int) (chance() * n);
    }

    // ------------------------------------------------------------------ the service wing

    /**
     * Behind the podium: the kitchen (behind the cafeteria), the laundry, central stores, the
     * morgue at the east end with its own door on the dock; the boiler house at the west end.
     */
    private void wing() {
        int y = 0, z0 = PZ1 + 1, z1 = WZ1 - 1;
        // the kitchen's back way through to the cafeteria's door, then the boiler house
        h.wallZ(46, z0, z1, y);
        h.wallZ(66, z0, z1, y);
        h.wallZ(84, z0, z1, y);
        h.wallZ(100, z0, z1, y);
        // boiler house (west of 46): the boilers, the generator, the stack's foot
        for (int x = WX0 + 6; x <= 42; x += 6) {
            for (int z : new int[] {94, 104}) {
                set(x, y + 1, z, "fit_riser");
                set(x + 1, y + 1, z, "fit_burner[facing=north]");
                set(x, y + 2, z, "fit_pipes_a[facing=north]");
            }
        }
        set(36, y + 1, 110, h.power ? "fit_gauges[facing=north]" : "fit_gauges[facing=north]");
        h.door(46, y, 100, "fire_door", "east");
        // kitchen 47-65: steel counters, ovens, the walk-in's door
        for (int x = 48; x <= 64; x++) {
            set(x, y + 1, z0 + 2, x % 4 == 0 ? "fit_oven[facing=south]" : "fit_counter_steel[facing=south]");
            if (x % 3 != 0) {
                set(x, y + 1, 100, "fit_counter_steel[facing=north]");
                set(x, y + 1, 101, "fit_counter_steel[facing=south]");
            }
        }
        for (int x = 48; x <= 64; x += 4) {
            set(x, y + 1, z1, "hs_cart_meals[facing=north]");
        }
        set(26, y + 1, z0, "air");
        h.door(46, y, 92, "fire_door", "west");
        // the laundry 67-83: washers and dryers, carts of linen
        for (int x = 68; x <= 82; x++) {
            set(x, y + 1, z0 + 1, (x % 2 == 0 ? "store_washer" : "store_dryer") + "[facing=south]");
        }
        for (int x = 69; x <= 81; x += 3) {
            set(x, y + 1, 104, "hs_cart_linen[facing=" + h.dir() + "]");
        }
        h.door(66, y, 96, "fire_door", "west");
        // central stores 85-99: shelving, boxes, the oxygen
        for (int z = z0 + 2; z < z1 - 2; z += 3) {
            for (int x = 86; x <= 98; x++) {
                if (x == 92) {
                    continue;
                }
                set(x, y + 1, z, "hs_cabinet_lo[facing=" + (z % 2 == 0 ? "north" : "south") + "]");
                if (chance() < 0.4) {
                    set(x, y + 2, z, "cardboard_box[facing=north]");
                }
            }
        }
        h.door(84, y, 98, "fire_door", "west");
        // the morgue 101-123: the wall of doors, the tables, the drain; its own door on the dock
        h.tileZ(100, z0, z1, y);
        h.tileX(101, WX1 - 1, z0, y);
        for (int x = 102; x <= 122; x++) {
            boolean open = chance() < 0.15;
            set(x, y + 1, z0 + 1, (open ? "hs_morgue_open" : "hs_morgue_lo") + "[facing=south]");
            set(x, y + 2, z0 + 1, "hs_morgue_hi[facing=south]");
        }
        for (int x = 104; x <= 120; x += 5) {
            set(x, y + 1, 104, "hs_autopsy[facing=north]");
            set(x, y + 1, 105, "hs_autopsy[facing=north]");
            set(x + 1, y + 1, 106, "hs_mayo[facing=west]");
        }
        for (int x = 102; x <= 122; x += 3) {
            if (chance() < 0.6) {
                set(x, y + 1, z1 - 1, "hs_gurney_bag_a[facing=east]");
                set(x + 1, y + 1, z1 - 1, "hs_gurney_bag_b[facing=east]");
            }
        }
        for (int x = 101; x < WX1; x++) {
            for (int z = z0 + 1; z < z1 + 1; z++) {
                set(x, y, z, "bunk_tile" + (chance() < 0.4 ? "_dirty" : ""));
            }
        }
        h.sign("morgue", 103, y + 4, 100, "west");
        Doors.abandoned(h.p, h.rng, 110, y + 1, WZ1, Doors.STEEL, "north", Doors.hinge(h.rng));
        h.door(100, y, 112, "fire_door", "west");
        // the wing's corridor door into the podium's south corridor? The podium's back doors
        h.door(75, y, PZ1, "fire_door", "south");
        h.door(92, y, PZ1, "fire_door", "south");
        h.lamps(WX0, z0 - 1, WX1, z1 + 1, y + S, 0.3);
        h.mess(WX0 + 1, z0, WX1 - 1, z1, y, 0.04);
    }
}
