package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Hospital.CN0;
import static com.zomboidcraft.world.county.Hospital.CN1;
import static com.zomboidcraft.world.county.Hospital.CORE0;
import static com.zomboidcraft.world.county.Hospital.CORE1;
import static com.zomboidcraft.world.county.Hospital.CS0;
import static com.zomboidcraft.world.county.Hospital.CS1;
import static com.zomboidcraft.world.county.Hospital.CX0;
import static com.zomboidcraft.world.county.Hospital.CX1;
import static com.zomboidcraft.world.county.Hospital.NR0;
import static com.zomboidcraft.world.county.Hospital.PROOF;
import static com.zomboidcraft.world.county.Hospital.S;
import static com.zomboidcraft.world.county.Hospital.SR1;
import static com.zomboidcraft.world.county.Hospital.TX0;
import static com.zomboidcraft.world.county.Hospital.TX1;
import static com.zomboidcraft.world.county.Hospital.WARDS;

import com.zomboidcraft.world.city.Placer;

/**
 * {@link Hospital}'s ward tower, a floor to each ward, by what it was for (from the podium's roof
 * up): maternity and its nursery, children's, two general wards (medical and surgical), cardiac
 * care, oncology, isolation (where they made the last stand: the polythene, the tape, the doors
 * sprayed shut), the locked psychiatric ward, and the overflow at the top with cots down its
 * corridors.
 *
 * <p>Each is a racetrack: patient rooms along both fronts (thirteen a side, five wide, two beds
 * each by the window, the curtain between, the washroom by the door), the corridors inside them
 * joined round the core's ends, and in the core between the stairs and the lifts the nurses'
 * station with the medication room behind it, the lounge, the utility rooms; the floor's own room
 * in the core's east block (the nursery, the playroom, the day room).
 */
final class HospitalWard {
    enum Kind { MATERNITY, PEDIATRICS, MEDICAL, SURGICAL, CARDIAC, ONCOLOGY, ISOLATION, PSYCH, OVERFLOW }

    private static final String[] SIGN = {"maternity", "pediatrics", "ward3", "ward5", "cardiac", "oncology", "isolation", "psych", "wards"};
    private final Hospital h;

    HospitalWard(Hospital h) {
        this.h = h;
    }

    private void set(int x, int y, int z, String b) {
        h.set(x, y, z, b);
    }

    private double chance() {
        return h.chance();
    }

    void build() {
        Kind[] kinds = Kind.values();
        for (int i = 0; i < WARDS; i++) {
            floor(PROOF + i * S, kinds[i], SIGN[i]);
        }
    }

    private void floor(int y, Kind kind, String sign) {
        // the rooms' corridor walls, the core's walls
        h.wallX(TX0 + 1, TX1 - 1, CN0 - 1, y);
        h.wallX(TX0 + 1, TX1 - 1, CS1 + 1, y);
        h.wallX(CX0, CX1, CORE0, y);
        h.wallX(CX0, CX1, CORE1, y);
        h.wallZ(CX0, CORE0, CORE1, y);
        h.wallZ(CX1, CORE0, CORE1, y);
        // the rooms, thirteen a side
        for (int k = 0; k < 13; k++) {
            int r0 = TX0 + 1 + 6 * k;
            if (k > 0) {
                h.wallZ(r0 - 1, NR0, CN0 - 2, y);
                h.wallZ(r0 - 1, CS1 + 2, SR1, y);
            }
            room(h.p.turned(r0, y, NR0, 0), r0, y, CN0 - 1, "north", kind, k);
            room(h.p.turned(r0 + 4, y, SR1, 2), r0, y, CS1 + 1, "south", kind, k);
        }
        station(y, kind, sign);
        lounge(y, kind);
        // the corridors: lights, the carts and the gurneys against the walls, what's on the floor
        h.lamps(TX0, CN0 - 1, TX1, CN1 + 1, y + S, kind == Kind.ISOLATION ? 0.15 : 0.4);
        h.lamps(TX0, CS0 - 1, TX1, CS1 + 1, y + S, kind == Kind.ISOLATION ? 0.15 : 0.4);
        h.lamps(TX0, CORE0, CX0, CORE1, y + S, 0.4);
        h.lamps(CX1, CORE0, TX1, CORE1, y + S, 0.4);
        for (int x = TX0 + 4; x < TX1 - 4; x += 6) {
            for (int z : new int[] {CN1, CS0}) {
                if (chance() > (kind == Kind.OVERFLOW ? 0.8 : 0.3) || !h.empty(x, y + 1, z) || !h.empty(x + 1, y + 1, z)
                        || !h.empty(x - 1, y + 1, z) || !h.solid(x - 2, x + 3, y, z == CN1 ? CORE0 : CORE1)) {
                    continue;
                }
                if (kind == Kind.OVERFLOW) {
                    set(x, y + 1, z, (chance() < 0.4 ? "sc_cot_head_b" : "sc_cot_head") + "[facing=west]");
                    set(x + 1, y + 1, z, "sc_cot_foot[facing=west]");
                } else {
                    double r = chance();
                    String f = z == CN1 ? "east" : "west";
                    if (r < 0.4) {
                        boolean bag = kind == Kind.ISOLATION || chance() < 0.3;
                        set(x, y + 1, z, (bag ? "hs_gurney_bag_a" : "hs_gurney_a") + "[facing=" + f + "]");
                        set(x + (f.equals("east") ? 1 : -1), y + 1, z, (bag ? "hs_gurney_bag_b" : "hs_gurney_b") + "[facing=" + f + "]");
                    } else if (r < 0.6) {
                        set(x, y + 1, z, (kind == Kind.CARDIAC ? "hs_cart_crash" : "hs_cart_med") + "[facing=" + (z == CN1 ? "south" : "north") + "]");
                    } else if (r < 0.8) {
                        set(x, y + 1, z, "hs_cart_linen[facing=north]");
                    } else {
                        set(x, y + 1, z, "hs_wheelchair" + (chance() < 0.3 ? "_tipped" : "") + "[facing=" + h.dir() + "]");
                    }
                }
            }
        }
        h.mess(TX0 + 1, CN0, TX1 - 1, CN1, y, kind == Kind.ISOLATION ? 0.14 : 0.05);
        h.mess(TX0 + 1, CS0, TX1 - 1, CS1, y, kind == Kind.ISOLATION ? 0.14 : 0.05);
        if (kind == Kind.ISOLATION) {
            lastStand(y);
        }
        if (kind == Kind.OVERFLOW) {
            for (int x = TX0 + 3; x < TX1 - 3; x += 9) {
                set(x, y + 2, CN0 - 1 + 1, "hs_notice[facing=south]");
            }
            for (int z = CORE0 + 1; z <= CORE1 - 1; z += 2) {
                set(TX0 + 1, y + 1, z, h.any("sc_cases_water[facing=east]", "sc_cases_blankets[facing=east]", "cardboard_box[facing=east]"));
            }
        }
    }

    /**
     * A patient room in its own frame: x 0 to 4 along the front, z 0 the window wall's row inside
     * to 5 by the corridor, whose wall is at world z door (the door at local x 3).
     */
    private void room(Placer q, int r0, int y, int doorZ, String side, Kind kind, int k) {
        boolean north = side.equals("north");
        int dx = north ? r0 + 3 : r0 + 1;
        boolean shut = kind == Kind.PSYCH || kind == Kind.ISOLATION && chance() < 0.6;
        h.door(dx, y, doorZ, shut ? "fire_door" : "flat_door", north ? "north" : "south");
        if (kind == Kind.ISOLATION && chance() < 0.45) {
            // polythene taped over the door, the tape across it
            int out = north ? doorZ + 1 : doorZ - 1;
            set(dx, y + 1, out, "hs_plastic[facing=north]");
            set(dx, y + 2, out, "hs_plastic[facing=north]");
            if (chance() < 0.5) {
                sprayBeside(dx, y, out, north);
            }
        }
        // the washroom by the door
        for (int x = 0; x <= 2; x++) {
            wall(q, x, 3, y);
        }
        wall(q, 2, 4, y);
        wall(q, 2, 5, y);
        q.set(1, 1, 3, "flat_door[facing=south,hinge=left,open=" + (chance() < 0.5) + ",half=lower]");
        q.set(1, 2, 3, "flat_door[facing=south,hinge=left,open=false,half=upper]");
        q.set(0, 1, 5, "home_toilet[facing=east]");
        q.set(0, 1, 4, "hs_sink[facing=east]");
        q.set(0, 2, 4, "home_mirror[facing=east]");
        for (int x = 0; x <= 1; x++) {
            for (int z = 4; z <= 5; z++) {
                q.set(x, 0, z, "bunk_tile" + (chance() < 0.3 ? "_dirty" : ""));
            }
        }
        if (kind == Kind.PSYCH) {
            // a quiet room: padded all round, one bolted bed, nothing else
            for (int x = 0; x <= 2; x++) {
                for (int yy = 1; yy < S; yy++) {
                    if (x != 1 || yy > 2) {
                        q.set(x, yy, 3, "hs_pad");
                    }
                }
            }
            bed(q, 2, 0, chance() < 0.25);
            if (chance() < 0.3) {
                q.set(3, 1, 2, "home_blood_pool");
            }
            return;
        }
        boolean single = kind == Kind.ISOLATION || kind == Kind.MATERNITY;
        if (kind == Kind.PEDIATRICS) {
            q.set(0, 1, 0, "sc_cot_head" + (chance() < 0.5 ? "_b" : "") + "[facing=north]");
            q.set(0, 1, 1, "sc_cot_foot" + (chance() < 0.5 ? "_b" : "") + "[facing=north]");
            q.set(3, 1, 0, "sc_cot_head" + (chance() < 0.5 ? "_b" : "") + "[facing=north]");
            q.set(3, 1, 1, "sc_cot_foot[facing=north]");
        } else if (single) {
            bed(q, 1, 0, chance() < (kind == Kind.ISOLATION ? 0.6 : 0.2));
        } else {
            bed(q, 0, 0, chance() < 0.2);
            if (chance() < 0.85) {
                bed(q, 3, 0, chance() < 0.2);
            }
        }
        if (!single) {
            q.set(1, 1, 0, "hs_nightstand[facing=south]");
            q.set(2, 1, 0, "hs_curtain[facing=east]");
            q.set(2, 2, 0, "hs_curtain_top[facing=east]");
            q.set(2, 1, 1, chance() < 0.5 ? "hs_curtain[facing=east]" : "hs_curtain_open[facing=east]");
            q.set(2, 2, 1, "hs_curtain_top[facing=east]");
        }
        boolean live = kind == Kind.CARDIAC || chance() < 0.3;
        q.set(4, 1, 0, (live ? "hs_monitor" + (h.power && chance() < 0.6 ? "_on" : "") : "hs_iv") + "[facing=south]");
        q.set(4, 1, 2, kind == Kind.ONCOLOGY ? "home_armchair_blue[facing=west]" : "fit_chair_ward[facing=west]");
        if (kind == Kind.MATERNITY) {
            q.set(3, 1, 0, "hs_bassinet[facing=south]");
            q.set(0, 1, 0, "hs_nightstand[facing=south]");
        }
        if (kind == Kind.ONCOLOGY || chance() < 0.3) {
            q.set(single ? 2 : 1, 1, 1, "hs_iv");
        }
        if (chance() < 0.25) {
            q.set(0, 1, 2, "hs_overbed[facing=north]");
        }
        if (chance() < 0.15) {
            q.set(4, 1, 1, "hs_tray[facing=north]");
        }
        if (chance() < 0.12) {
            q.set(1, 1, 2, "home_blood_pool");
        }
        // the troffer over the beds
        q.set(2, S, 1, h.power && chance() < 0.25 ? "hs_slab_lamp_on" : "hs_slab_lamp");
    }

    private void wall(Placer q, int x, int z, int y) {
        q.set(x, 1, z, chance() < 0.1 ? "hs_wall_lo_b" : "hs_wall_lo");
        for (int k = 2; k < S; k++) {
            q.set(x, k, z, "hs_wall");
        }
    }

    private void bed(Placer q, int x, int z, boolean bloody) {
        String s = bloody ? "_blood" : "";
        q.set(x, 1, z, "hs_bed_head" + s + "[facing=north]");
        q.set(x, 1, z + 1, (chance() < 0.12 ? "hs_bed_foot_body" : "hs_bed_foot" + s) + "[facing=north]");
    }

    /** DEAD INSIDE (or the like) sprayed on the corridor wall beside a door, reading from the corridor. */
    private void sprayBeside(int dx, int y, int z, boolean northRoom) {
        String key = h.any("dead", "dont", "nohelp");
        String f = northRoom ? "south" : "north";
        for (int i = 0; i < 3; i++) {
            // facing south: tile 0 at the low x; facing north: at the high x
            int x = northRoom ? dx - 3 + i : dx + 3 - i;
            if (h.empty(x, y + 2, z)) {
                set(x, y + 2, z, "hs_spray_" + key + "_" + i + "[facing=" + f + "]");
            }
        }
    }

    // ------------------------------------------------------------------ the core's rooms

    /** The nurses' station open to the north corridor, its charts and screens; the medication room behind it. */
    private void station(int y, Kind kind, String sign) {
        for (int x = 38; x <= 53; x++) {
            set(x, y + 1, CORE0, (x == 38 || x == 53 ? "hs_station_end" : "hs_station") + "[facing=north]");
            set(x, y + 2, CORE0, "air");
            set(x, y + 3, CORE0, "air");
        }
        h.sign(sign, 44, y + 4, CORE0 - 1, "north");
        for (int x = 39; x <= 52; x += 3) {
            set(x, y + 1, CORE0 + 2, "office_chair" + (chance() < 0.2 ? "_fallen" : "") + "[facing=" + h.dir() + "]");
        }
        for (int x = 37; x <= 54; x++) {
            set(x, y + 1, CORE0 + 4, x % 4 == 0 ? "hs_monitor" + (h.power && (kind == Kind.CARDIAC || chance() < 0.3) ? "_on" : "") + "[facing=north]"
                    : "hs_cabinet_lo[facing=north]");
            set(x, y + 2, CORE0 + 4, x % 3 == 0 ? "hs_cabinet[facing=north]" : "air");
        }
        h.wallX(36, 55, CORE0 + 5, y);
        h.door(45, y, CORE0 + 5, "flat_door", "north");
        // the medication room
        for (int x = 37; x <= 54; x++) {
            set(x, y + 1, CORE1 - 1, x % 5 == 0 ? "hs_cart_med[facing=north]" : "store_shelf_pharmacy[facing=north]");
            set(x, y + 2, CORE1 - 1, x % 5 == 0 ? "air" : "store_shelf_pharmacy_top[facing=north]");
        }
        set(37, y + 1, CORE0 + 6, "store_safe[facing=east]");
        h.lamps(36, CORE0, 55, CORE1, y + S, 0.35);
        // the equipment room behind the lifts
        h.door(62, y, CORE1, "fire_door", "south");
        for (int x = 57; x <= 67; x += 2) {
            set(x, y + 1, CORE1 - 1, h.any("hs_oxygen[facing=north]", "hs_wheelchair[facing=north]", "hs_iv", "hs_cart_linen[facing=north]"));
        }
    }

    /** The core's east block: the floor's own room (nursery, playroom, day room) and the utility room past it. */
    private void lounge(int y, Kind kind) {
        h.wallZ(81, CORE0, CORE1, y);
        switch (kind) {
            case MATERNITY -> {
                // the nursery behind its window to the corridor
                for (int x = 70; x <= 80; x++) {
                    set(x, y + 2, CORE0, x % 4 == 0 ? "hs_wall" : "tx_glass[axis=x]");
                }
                for (int x = 71; x <= 79; x += 2) {
                    for (int z = CORE0 + 2; z <= CORE1 - 2; z += 2) {
                        set(x, y + 1, z, (chance() < 0.25 ? "hs_incubator" : "hs_bassinet") + "[facing=north]");
                    }
                }
            }
            case PEDIATRICS -> {
                for (int x = 71; x <= 79; x += 3) {
                    set(x, y + 1, CORE0 + 3, "store_table[facing=north]");
                    set(x, y + 1, CORE0 + 2, "store_chair[facing=south]");
                }
                set(70, y + 1, CORE1 - 1, "home_tv[facing=north]");
                set(72, y + 1, CORE1 - 1, "home_bookcase[facing=north]");
                set(76, y + 1, CORE1 - 1, "sc_cot_head_b[facing=east]");
            }
            case PSYCH -> {
                for (int x = 71; x <= 79; x += 4) {
                    set(x, y + 1, 43, "home_table[facing=north]");
                    set(x, y + 1, 42, "home_chair[facing=south]");
                    set(x, y + 1, 44, "home_chair_tipped[facing=" + h.dir() + "]");
                }
                set(70, y + 1, CORE1 - 1, "home_tv_smashed[facing=north]");
            }
            default -> {
                for (int x = 71; x <= 79; x += 4) {
                    set(x, y + 1, 43, "home_table[facing=north]");
                    set(x, y + 1, 42, "home_chair[facing=south]");
                    set(x, y + 1, 44, chance() < 0.3 ? "home_chair_tipped[facing=" + h.dir() + "]" : "home_chair[facing=north]");
                }
                set(70, y + 1, CORE1 - 1, "home_fridge[facing=north]");
                set(70, y + 2, CORE1 - 1, "home_fridge_top[facing=north]");
                set(72, y + 1, CORE1 - 1, "home_coffee_maker[facing=north]");
                set(74, y + 1, CORE1 - 1, "home_microwave[facing=north]");
            }
        }
        h.door(75, y, CORE0, kind == Kind.MATERNITY || kind == Kind.PSYCH ? "fire_door" : "flat_door", "north");
        h.lamps(69, CORE0, 81, CORE1, y + S, 0.3);
        // the utility room: the linen, the soiled hoppers, the bins
        h.door(88, y, CORE1, "fire_door", "south");
        for (int x = 82; x <= 93; x++) {
            set(x, y + 1, CORE0 + 1, x % 3 == 0 ? "hs_biohazard[facing=south]" : "hs_cabinet_lo[facing=south]");
        }
        for (int x = 83; x <= 92; x += 3) {
            set(x, y + 1, CORE1 - 2, "hs_cart_linen[facing=" + h.dir() + "]");
        }
        h.lamps(81, CORE0, 94, CORE1, y + S, 0.25);
    }

    /** The isolation ward's end: tape across the corridors, the barricade at the stairs, the hazard bins and the bags. */
    private void lastStand(int y) {
        for (int z = CN0; z <= CN1; z++) {
            set(TX0 + 8, y + 1, z, "hs_tape[facing=east]");
            set(TX1 - 8, y + 1, z, "hs_tape[facing=east]");
        }
        for (int z = CS0; z <= CS1; z++) {
            set(TX0 + 8, y + 1, z, "hs_tape[facing=east]");
            set(TX1 - 8, y + 1, z, "hs_tape[facing=east]");
        }
        for (int x = TX0 + 1; x <= TX0 + 3; x++) {
            for (int z = CORE0 + 1; z < CORE1; z += 2) {
                if (chance() < 0.6) {
                    set(x, y + 1, z, h.any("bunk_bodybag[facing=north]", "hs_biohazard[facing=east]", "hs_gurney_bag_a[facing=north]"));
                }
            }
        }
        for (int x = TX0 + 4; x < TX1 - 4; x += 11) {
            set(x, y + 2, CN0 - 1 + 1, "hs_notice[facing=south]");
        }
    }
}
