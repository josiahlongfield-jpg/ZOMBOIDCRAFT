package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The Grayhaven Civic Center, on a lot of two blocks by two: City Hall's tower
 * ({@link CivicTower}) at the back of its plaza, the council chamber ({@link CivicChamber}) in its
 * drum beside it, the glass concourse between them; the plaza of granite in front, the long
 * reflecting pool on the tower's axis with THE MARINER rusting on its plinth at the end of it, the
 * three flags at the tower's door, trees in their pits, the veterans' obelisk; behind, the
 * service yard and the staff lot.
 *
 * <p>In the last weeks the state put its relief station up on the west half of the plaza: the
 * hoarding along the street with the banner on it and the missing on the back, the queue through
 * the gap to the registration tables, the rows of tents, triage and stores, the water pallets, the
 * portable toilets, the light towers, the ambulance that stayed. City Hall ran the quarantine from
 * the top floor.
 *
 * <p>Drawn in the lot's frame: x along the front (west to east), z from the front (north) back,
 * the ground at y = 0.
 */
final class CivicCentre extends Grounds {
    /** City Hall: storeys, floors, the roof's slab, its walls (inclusive), the lobby's glass. */
    static final int S = 5, FLOORS = 12, ROOF = FLOORS * S;
    static final int TX0 = 72, TX1 = 120, TZ0 = 70, TZ1 = 100, GLASS_Z = 73;
    /** The core of stairs and lifts in the middle of the tower. */
    static final int CORE_X0 = 84, CORE_X1 = 108, CORE_Z0 = 80, CORE_Z1 = 89;
    /** The council chamber's drum, and the concourse from it to the tower's lobby. */
    static final int CHX = 36, CHZ = 84, CHR = 15;
    static final int CON_X0 = 46, CON_X1 = 71, CON_Z0 = 74, CON_Z1 = 80;
    /** The reflecting pool, on the tower's axis. */
    static final int PX0 = 84, PX1 = 108, PZ0 = 20, PZ1 = 54;
    /** The relief station on the west half of the plaza, and the hoarding along its street. */
    static final int RX0 = 6, RX1 = 58, RZ0 = 6, RZ1 = 62, HOARD_Z = 10;

    CivicCentre(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.4, 0.15, 0.5);
    }

    @Override
    String signs() {
        return "cc_sgn_";
    }

    void build() {
        ground();
        new CivicTower(this).build();
        new CivicChamber(this).build();
        pool();
        flags();
        sculpture();
        planting();
        memorial();
        nameWall();
        relief();
        yard();
        litter();
    }

    // ------------------------------------------------------------------ the ground

    /** The sidewalks round the lot, granite over the plaza and round the buildings, asphalt behind. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                String g;
                if (edge) {
                    g = chance() < 0.1 ? "sidewalk_cracked" : "sidewalk";
                } else if (z >= TZ1 + 4) {
                    g = chance() < 0.15 ? "asphalt_crack" : "asphalt";
                } else if ((x - 4) % 8 == 0 || (z - 4) % 8 == 0) {
                    g = "cc_plaza_band";
                } else {
                    g = chance() < 0.12 ? "cc_plaza_b" : "cc_plaza";
                }
                set(x, 0, z, g);
            }
        }
        // the drive in from the back street
        for (int x = 60; x <= 66; x++) {
            for (int z = d - 4; z < d; z++) {
                set(x, 0, z, "asphalt");
            }
        }
    }

    /** The reflecting pool: a granite coping round still water a block deep, leaves and paper on it. */
    private void pool() {
        for (int x = PX0; x <= PX1; x++) {
            for (int z = PZ0; z <= PZ1; z++) {
                boolean rim = x == PX0 || x == PX1 || z == PZ0 || z == PZ1;
                if (rim) {
                    set(x, 0, z, "cc_plaza_band");
                    set(x, 1, z, chance() < 0.06 ? "air" : "cc_coping");
                } else {
                    set(x, -1, z, "cc_plaza_band");
                    set(x, 0, z, "minecraft:water");
                }
            }
        }
        // the plaque at the north end, on its granite post
        set(96, 1, PZ0 - 2, "tx_granite");
        set(96, 1, PZ0 - 3, "cc_plaque[facing=north]");
    }

    /** THE MARINER on its plinth in the pool: weathering-steel plates, a mast, a sail leaning out. */
    private void sculpture() {
        int cx = 96, cz = PZ0 + 6;
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                set(x, -1, z, "cc_plaza_band");
                set(x, 0, z, "cc_plaza_band");
                set(x, 1, z, "cc_coping");
            }
        }
        for (int y = 2; y <= 11; y++) {
            set(cx, y, cz, "cc_corten");
        }
        for (int x = cx - 2; x <= cx + 1; x++) {
            set(x, 2, cz, "cc_corten_plate[facing=north]");
            set(x, 3, cz, "cc_corten_plate[facing=north]");
        }
        for (int k = 0; k < 5; k++) {
            set(cx + 1 + (k + 1) / 2, 4 + k, cz, "cc_corten_plate[facing=north]");
            set(cx + 1 + (k + 1) / 2, 4 + k, cz - 1, k % 2 == 0 ? "cc_corten_plate[facing=east]" : "air");
        }
        for (int z = cz - 2; z <= cz + 2; z++) {
            set(cx - 1, 6, z, "cc_corten_plate[facing=east]");
            set(cx - 1, 7, z, Math.abs(z - cz) < 2 ? "cc_corten_plate[facing=east]" : "air");
        }
        set(cx, 12, cz, "cc_corten_plate[facing=east]");
    }

    /** The three flagpoles before the tower's doors: the nation's, the state's and the city's. */
    private void flags() {
        String[] tops = {"cv_flagpole_top", "cc_pole_state_top", "cc_pole_city_top"};
        for (int i = 0; i < 3; i++) {
            int x = 90 + i * 6, z = TZ0 - 6;
            set(x, 0, z, "cc_plaza_band");
            set(x, 1, z, "cv_flagpole_base");
            for (int y = 2; y < 15; y++) {
                set(x, y, z, "cv_flagpole");
            }
            set(x, 15, z, chance() < 0.75 ? tops[i] + "[facing=east]" : "cv_flagpole");
        }
    }

    /** Trees in their pits down the walks, benches and lamps between, the hedges along the drum. */
    private void planting() {
        int[][] pits = {{70, 16}, {70, 32}, {70, 48}, {76, 62}, {116, 16}, {126, 24}, {126, 52}, {114, 58}, {60, 66}, {126, 66}};
        for (int[] t : pits) {
            pit(t[0], t[1]);
        }
        for (int z = 12; z <= 60; z += 8) {
            lampPost(PX0 - 4, z);
            lampPost(PX1 + 4, z + 4);
            bench(PX0 - 2, z + 2, "east");
            bench(PX1 + 2, z + 6, "west");
        }
        for (int x = 112; x <= 126; x += 7) {
            lampPost(x, 10);
            bench(x + 2, 66, "north");
        }
        for (int x = 62; x <= 80; x += 9) {
            lampPost(x, 10);
        }
        // the raised beds down the pool's long sides, box gone wild in them
        for (int z = PZ0 + 2; z <= PZ1 - 2; z++) {
            if ((z - PZ0) % 8 == 6 || (z - PZ0) % 8 == 7) {
                continue;
            }
            for (int x : new int[] {PX0 - 1, PX1 + 1}) {
                set(x, 1, z, chance() < 0.25 ? "un_hedge_dead" : "un_hedge_low");
            }
        }
        // along the street: news boxes, the payphone, bins, hydrants, bollards at the plaza's mouth
        for (int k = 0; k < 3; k++) {
            set(84 + k, 1, 5, chance() < 0.3 ? "gs_news_" + rng.nextInt(3) + "[facing=" + dir() + "]" : "gs_news_" + k + "[facing=north]");
        }
        set(110, 1, 5, "gs_payphone[facing=north]");
        for (int x : new int[] {64, 100, 122}) {
            set(x, 1, 5, chance() < 0.3 ? "ext_trash_can_tipped[facing=" + dir() + "]" : "ext_trash_can");
        }
        set(58, 1, 4, "fs_hydrant[facing=north]");
        set(w - 5, 1, 70, "fs_hydrant[facing=east]");
        for (int x = 88; x <= 104; x += 2) {
            set(x, 1, 4, "gs_bollard");
        }
    }

    /** A tree in a pit three square, a low granite curb round it. */
    private void pit(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(x + dx, 0, z + dz, "minecraft:grass_block");
                if (dx != 0 || dz != 0) {
                    set(x + dx, 1, z + dz, (dx + dz) % 2 == 0 ? "cc_coping" : "air");
                }
            }
        }
        double r = chance();
        Trees.grow(Kit.canopy(p), r < 0.5 ? Trees.Kind.YOUNG : r < 0.8 ? Trees.Kind.MAPLE : r < 0.9 ? Trees.Kind.OAK : Trees.Kind.SICK, x, 1, z, rng);
    }

    private void bench(int x, int z, String facing) {
        if (empty(x, 1, z)) {
            set(x, 1, z, (chance() < 0.85 ? "cv_hall_bench" : "debris_chunk") + "[facing=" + facing + "]");
        }
    }

    /** The veterans' obelisk on its three steps at the plaza's east corner, the wreath long dead. */
    private void memorial() {
        int x = 120, z = 40;
        for (int s = 0; s < 3; s++) {
            int e = 3 - s;
            for (int dx = -e; dx <= e; dx++) {
                for (int dz = -e; dz <= e; dz++) {
                    boolean ring = s < 2 && (Math.abs(dx) == e || Math.abs(dz) == e);
                    set(x + dx, 1 + s, z + dz, !ring ? "tx_granite"
                            : "cv_step_stone[facing=" + (dz == -e ? "south" : dz == e ? "north" : dx < 0 ? "east" : "west") + "]");
                }
            }
        }
        set(x, 4, z, "ch_obelisk[facing=north]");
        set(x, 5, z, "ch_obelisk_top[facing=north]");
        set(x, 4, z - 1, "home_plant_dead");
    }

    /** CIVIC CENTER PLAZA cut in a low granite wall by the street, west of the pool. */
    private void nameWall() {
        for (int x = 62; x <= 75; x++) {
            set(x, 1, 8, "tx_granite");
            set(x, 2, 8, x == 62 || x == 75 ? "tx_granite" : "air");
        }
        row("cc_name_plaza_", 12, 63, 2, 8, "north", 0.02);
        for (int x = 63; x <= 74; x++) {
            set(x, 2, 9, "tx_granite");
        }
    }

    // ------------------------------------------------------------------ the relief station

    private void relief() {
        Camp camp = new Camp(p, rng, 0.55);
        // the hoarding along the street, the gap for the queue; the banner on the front, the missing on the back
        for (int x = RX0; x <= RX1; x++) {
            if (x >= 41 && x <= 47) {
                continue;
            }
            for (int y = 1; y <= 3; y++) {
                set(x, y, HOARD_Z, y == 3 && chance() < 0.1 ? "air" : "wood_panel");
            }
        }
        row("cc_banner_", 6, 14, 3, HOARD_Z - 1, "north", 0.05);
        sign("relief", 49, 3, HOARD_Z - 1, "north");
        picture("cc_missing_", 4, 2, 22, 1, HOARD_Z + 1, "south");
        picture("cc_missing_", 4, 2, 30, 1, HOARD_Z + 1, "south");
        for (int x = RX0 + 1; x <= RX1 - 1; x += 3) {
            if (x < 20 || x > 35) {
                if (x < 41 || x > 47) {
                    set(x, 2, HOARD_Z + 1, chance() < 0.5 ? "st_poster_missing[facing=south]" : "st_poster_missing_b[facing=south]");
                }
            }
        }
        sign("water", 52, 2, HOARD_Z + 1, "south");
        sign("aid", 36, 3, HOARD_Z + 1, "south");
        set(40, 2, HOARD_Z - 1, "cc_proclamation[facing=north]");
        // the fence down the side street and the jersey barriers at the queue
        camp.fence(RX0 - 2, HOARD_Z, RX0 - 2, RZ1, 0.2);
        camp.jersey(39, HOARD_Z - 3, 40, HOARD_Z - 3);
        camp.jersey(48, HOARD_Z - 3, 49, HOARD_Z - 3);
        // the queue: barriers down both sides to the registration tables
        for (int z = HOARD_Z + 1; z <= HOARD_Z + 7; z++) {
            for (int x : new int[] {40, 48}) {
                if (chance() < 0.85) {
                    set(x, 1, z, chance() < 0.85 ? "st_barrier[facing=east]" : "st_barrier_down[facing=east]");
                }
            }
        }
        for (int x = 41; x <= 47; x++) {
            if (x != 44) {
                set(x, 1, HOARD_Z + 9, "cv_table[axis=x]");
                if (chance() < 0.6) {
                    set(x, 1, HOARD_Z + 10, "trade_folding_chair[facing=north]");
                }
                if (chance() < 0.5) {
                    set(x, 2, HOARD_Z + 9, any("paper", "home_books_stack_low", "cardboard_box[facing=north]"));
                }
            }
        }
        // the tents: shelters in two rows, triage and stores on the east
        for (int i = 0; i < 4; i++) {
            for (int z : new int[] {22, 38}) {
                if (chance() < 0.88) {
                    tent(RX0 + 2 + i * 8, z, 12, "shelter");
                }
            }
        }
        tent(42, 24, 12, "triage");
        tent(50, 24, 12, "supply");
        // the water pallets, the body bags behind triage, the toilets, the light towers
        for (int x = 50; x <= 56; x += 2) {
            for (int z = 16; z <= 18; z += 2) {
                set(x, 1, z, chance() < 0.4 ? "st_pallet_water_half" : chance() < 0.7 ? "st_pallet_water" : "st_pallet_meals");
            }
        }
        for (int x = 42; x <= 48; x++) {
            if (chance() < 0.65) {
                set(x, 1, 40, "st_stretcher_bag[facing=east]");
            }
            if (chance() < 0.4) {
                set(x, 1, 42, "st_stretcher_bag[facing=east]");
            }
        }
        for (int z = 52; z <= 60; z += 2) {
            set(RX0, 1, z, "st_porta_lo[facing=east]");
            set(RX0, 2, z, "st_porta_hi[facing=east]");
        }
        for (int[] m : new int[][] {{38, 34}, {20, 54}, {56, 46}}) {
            set(m[0], 1, m[1], "st_lt_base[facing=north]");
            for (int y = 2; y <= 8; y++) {
                set(m[0], y, m[1], "st_lt_mast");
            }
            set(m[0], 9, m[1], (power && chance() < 0.6 ? "st_lt_head_on" : "st_lt_head") + "[facing=north]");
        }
        // the ambulance that stayed, the guard's jeep, sandbags round the triage door
        p.vehicle(46.5, 1, 52, 175 + (float) (chance() * 10), "ambulance,ambulance_van", rng.nextLong());
        if (chance() < 0.7) {
            p.vehicle(30, 1, 56, 80 + (float) (chance() * 20), "jeep,jeep_open", rng.nextLong());
        }
        for (int x = 41; x <= 48; x++) {
            if (x != 44 && x != 45 && chance() < 0.7) {
                set(x, 1, 22, "sandbags[facing=north]");
            }
        }
        // cots and bags where people slept out, the litter of it all
        for (int i = 0; i < 70; i++) {
            int x = RX0 + 1 + rng.nextInt(RX1 - RX0 - 1), z = HOARD_Z + 2 + rng.nextInt(RZ1 - HOARD_Z - 2);
            if (empty(x, 1, z) && empty(x, 2, z) && get(x, 0, z) != null && get(x, 0, z).startsWith("cc_plaza")) {
                double r = chance();
                set(x, 1, z, r < 0.35 ? "paper" : r < 0.5 ? "trash_bag[facing=" + dir() + "]" : r < 0.62 ? "blood_splat"
                        : r < 0.75 ? "cardboard_box[facing=" + dir() + "]" : r < 0.85 ? "sc_cases_water[facing=" + dir() + "]" : "glass_shards");
            }
        }
    }

    // ------------------------------------------------------------------ behind

    /**
     * The service yard behind the tower (the dock, the dumpsters, the city's trucks) and the
     * staff lot behind the chamber, its rows of stalls and light poles.
     */
    private void yard() {
        int z0 = TZ1 + 6;
        for (int row = 0; row < 2; row++) {
            int sz = z0 + row * 12;
            for (int x = 6; x <= 58; x++) {
                boolean line = (x - 6) % 3 == 0;
                for (int z = sz; z < sz + 5; z++) {
                    if (line && chance() < 0.85) {
                        set(x, 0, z, "road_line_white[axis=z]");
                    }
                }
                if (line && x + 3 <= 58 && chance() < 0.2) {
                    p.vehicle(x + 1.5, 1, sz + 2.5, 180 + (float) (chance() * 10 - 5),
                            x < 20 ? "police_sedan,police_suv" : "sedan,suv,pickup,hatchback,wagon,van,sedan_ltd", rng.nextLong());
                }
            }
        }
        for (int x = 18; x <= 58; x += 20) {
            int z = z0 + 8;
            set(x, 1, z, "st_lot_base");
            for (int y = 2; y <= 9; y++) {
                set(x, y, z, "st_lot_pole");
            }
            set(x, 10, z, (power && chance() < 0.35 ? "st_lot_head_on" : "st_lot_head") + "[facing=north]");
        }
        // the east half: a row of stalls for the city's cars along the back fence, the guard's booth at the drive
        for (int x = 72; x <= 126; x++) {
            boolean line = (x - 72) % 3 == 0;
            for (int z = d - 10; z < d - 5; z++) {
                if (line && chance() < 0.85) {
                    set(x, 0, z, "road_line_white[axis=z]");
                }
            }
            if (line && x + 3 <= 126 && chance() < 0.22) {
                p.vehicle(x + 1.5, 1, d - 7.5, (float) (chance() * 10 - 5), x < 96 ? "police_sedan,police_suv,sedan_ltd" : "pickup,van,sedan", rng.nextLong());
            }
        }
        new Camp(p, rng, 0.4).booth(67, d - 12);
        for (int x = 66; x <= 72; x++) {
            for (int z = d - 13; z <= d - 7; z++) {
                for (int y = 1; y <= 6; y++) {
                    String g = get(x, y, z);
                    if (g != null && (g.startsWith("minecraft:sea_lantern") || g.startsWith("minecraft:smooth_quartz"))) {
                        set(x, y, z, "air");
                    }
                }
            }
        }
        set(66, 1, d - 7, "st_barrier_down[facing=east]");
        // the dock behind City Hall: the dumpsters, the garbage truck backed up, a box truck
        for (int x = 98; x <= 102; x += 2) {
            set(x, 1, TZ1 + 2, "dumpster[facing=north]");
        }
        p.vehicle(112, 1, TZ1 + 10, 5 + (float) (chance() * 10), "garbage_truck,box_truck,step_van", rng.nextLong());
        if (chance() < 0.6) {
            p.vehicle(84, 1, TZ1 + 14, 90 + (float) (chance() * 30), "pickup,van", rng.nextLong());
        }
        for (int x = 72; x <= 128; x += 4) {
            if (chance() < 0.25) {
                set(x, 1, w - 5, "trash_bag[facing=" + dir() + "]");
            }
        }
    }

    private void litter() {
        for (int i = 0; i < 140; i++) {
            int x = 5 + rng.nextInt(w - 10), z = 5 + rng.nextInt(d - 10);
            String g = get(x, 0, z);
            if (g != null && g.startsWith("cc_plaza") && empty(x, 1, z) && x > RX1) {
                set(x, 1, z, any("paper", "paper", "trash_bag[facing=" + dir() + "]", "blood_splat", "glass_shards", "debris_chunk[facing=" + dir() + "]"));
            }
        }
    }
}
