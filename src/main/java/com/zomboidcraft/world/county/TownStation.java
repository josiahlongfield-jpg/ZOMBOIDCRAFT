package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The railway's station beside a street stop in a town: the steel-and-glass ticket hall the
 * railway put up in the late eighties (the tx_ kit, tools/city/transit.py), across the sidewalk
 * from the middle of the platform, its glass canopy reaching out over the sidewalk to meet the
 * platform's own so nobody waits in the rain.
 *
 * <p>Drawn in its lot's frame: x along the street (0 .. {@link #L} - 1, the platform's middle
 * halfway), z = 0 the glass front on the sidewalk, growing back into the block; y = 0 the floor,
 * y = 1 standing on it. The canopy hangs out over z = -1 .. -canopy at y = 6, level with the
 * platform's canopy (the trains' bed top is a block over the street, its canopy five over that).
 *
 * <p>Inside, front to back: the hall (three pairs of glass doors, validators by them, benches
 * facing the platform, the vending machine and the payphone, the map on each end wall), then the
 * service wall at z = 10 with the ticket machines, the ticket office's windows under the
 * departures board, the kiosk's counter and the doors to the toilets and the staff office; the
 * rooms behind it to the back wall at z = 15, a fire door out the back.
 */
final class TownStation {
    static final int L = 28, D = 17;
    /** The front's columns, the bays between them nine wide with a pair of doors in the middle of each. */
    private static final int[] COLS = {0, 9, 18, 27};
    private static final int[] DOORS = {4, 13, 22};
    private static final int WALL_Z = 10, BACK_Z = 15, TOP = 7, ROOF = 8;

    private final Placer q;
    private final SplittableRandom rng;
    private final String key;
    private final int canopy;

    /** key: the stop's name key (the kit has its lettering and map); canopy: how far out over the sidewalk. */
    TownStation(Placer q, SplittableRandom rng, String key, int canopy) {
        this.q = q;
        this.rng = rng;
        this.key = key;
        this.canopy = Math.max(2, canopy);
    }

    private double chance() {
        return rng.nextDouble();
    }

    void build() {
        floor();
        front();
        walls();
        roof();
        canopy();
        hall();
        rooms();
        wear();
    }

    // ------------------------------------------------------------------ the shell

    private void floor() {
        for (int x = 0; x < L; x++) {
            for (int z = 0; z <= BACK_Z; z++) {
                boolean border = z == 1 || z == WALL_Z - 1 || x == 1 || x == L - 2;
                String f = border ? "tx_terrazzo_d" : chance() < 0.14 ? "tx_terrazzo_b" : "tx_terrazzo";
                if (z > WALL_Z) {
                    f = x <= 6 ? "tx_terrazzo_d" : chance() < 0.1 ? "tx_terrazzo_b" : "tx_terrazzo";
                }
                q.set(x, 0, z, f);
                for (int y = 1; y < ROOF; y++) {
                    q.set(x, y, z, "air");
                }
            }
            // the back strip: the block's yard ground, cleared
            q.set(x, 0, D - 1, chance() < 0.6 ? "floor_concrete_crack" : "minecraft:gravel");
            for (int y = 1; y <= ROOF + 2; y++) {
                q.set(x, y, D - 1, "air");
            }
        }
    }

    /** The glass front: white columns at the bay lines, glass between with a transom at the foot and at four, three pairs of doors. */
    private void front() {
        for (int x = 0; x < L; x++) {
            boolean col = false;
            for (int c : COLS) {
                col |= c == x;
            }
            if (col) {
                q.set(x, 1, 0, "tx_column_base");
                for (int y = 2; y < TOP; y++) {
                    q.set(x, y, 0, "tx_column");
                }
                q.set(x, TOP, 0, "tx_column_cap");
                continue;
            }
            boolean door = false;
            for (int dx : DOORS) {
                door |= x == dx || x == dx + 1;
            }
            for (int y = door ? 3 : 1; y <= TOP; y++) {
                q.set(x, y, 0, pane(y == 1 || y == 4 || door && y == 3));
            }
        }
        for (int dx : DOORS) {
            Doors.pair(q, rng, dx, 1, 0, "tx_door", "south");
            // a dark mat inside and out
            q.set(dx, 0, 1, "tx_terrazzo_d");
            q.set(dx + 1, 0, 1, "tx_terrazzo_d");
        }
    }

    /** A pane of the front: whole, cracked, smashed through or gone from its frame, as the years took them. */
    private String pane(boolean transom) {
        double r = chance();
        String b = r < 0.08 ? "tx_glass_broken" : r < 0.18 ? "tx_glass_cracked" : r < 0.23 ? "tx_glass_frame"
                : transom ? "tx_glass_t" : "tx_glass";
        if (transom && !b.equals("tx_glass_t") && !b.equals("tx_glass_frame")) {
            b = r < 0.08 ? "tx_glass_broken" : "tx_glass_t";
        }
        return b + "[axis=x]";
    }

    /** The end walls and the back in aluminium panels over a granite plinth; the service wall and the rooms' walls in plaster. */
    private void walls() {
        for (int z = 1; z <= BACK_Z; z++) {
            for (int x : new int[] {0, L - 1}) {
                q.set(x, 1, z, "tx_granite");
                for (int y = 2; y <= TOP; y++) {
                    q.set(x, y, z, cladding(y));
                }
            }
        }
        for (int x = 1; x < L - 1; x++) {
            q.set(x, 1, BACK_Z, "tx_granite");
            for (int y = 2; y <= TOP; y++) {
                q.set(x, y, BACK_Z, cladding(y));
            }
            for (int y = 1; y <= TOP; y++) {
                q.set(x, y, WALL_Z, y == 1 ? "tx_wall_base" : "tx_wall");
            }
        }
        for (int x : new int[] {7, 13, 20}) {
            for (int z = WALL_Z + 1; z < BACK_Z; z++) {
                for (int y = 1; y <= TOP; y++) {
                    q.set(x, y, z, y == 1 ? "tx_wall_base" : "tx_wall");
                }
            }
        }
    }

    /** The panel for a course of the cladding: tall panels three high from the plinth up, a third of them streaked. */
    private String cladding(int y) {
        String part = (y - 2) % 3 == 0 ? "lo" : (y - 2) % 3 == 1 ? "mid" : "hi";
        return "tx_panel_" + part + (chance() < 0.3 ? "_s" : "");
    }

    /** The flat roof on its trusses, the rooflight down the hall's middle, the parapet with the town's name along the front. */
    private void roof() {
        for (int x = 0; x < L; x++) {
            for (int z = 0; z <= BACK_Z; z++) {
                boolean light = x >= 2 && x <= L - 3 && z >= 3 && z <= 6 && x != 9 && x != 18;
                q.set(x, ROOF, z, light ? "tx_roof_glass" : "tx_roof");
                boolean edge = x == 0 || x == L - 1 || z == 0 || z == BACK_Z;
                if (edge) {
                    q.set(x, ROOF + 1, z, "tx_parapet");
                }
            }
        }
        int n = 8, x0 = (L - n) / 2;
        for (int i = 0; i < n; i++) {
            // tile 0 is the reader's left: from the street, looking at the north face, that's the high x
            q.set(x0 + n - 1 - i, ROOF + 1, 0, "tx_name_" + key + "_" + i + "[facing=north]");
        }
        // the steel ladder up the back for the roof's upkeep, through a gap in the parapet
        for (int y = 1; y <= ROOF + 1; y++) {
            q.set(L - 4, y, D - 1, "ladder[facing=south]");
        }
        q.set(L - 4, ROOF + 1, BACK_Z, "air");
        // the trusses across the hall at the column lines
        for (int x : new int[] {9, 18}) {
            for (int z = 1; z < WALL_Z; z++) {
                q.set(x, TOP, z, "tx_truss[axis=z]");
            }
        }
    }

    /** The glass canopy out over the sidewalk on a tapered arm from each column, the gutter at its tip. */
    private void canopy() {
        for (int x = 0; x < L; x++) {
            boolean arm = false;
            for (int c : COLS) {
                arm |= c == x;
            }
            for (int i = 1; i <= canopy; i++) {
                int z = -i;
                if (i == canopy) {
                    q.set(x, 6, z, "tx_canopy_edge[facing=north]");
                } else if (arm) {
                    int k = (int) Math.round((i - 1) * 6.0 / Math.max(1, canopy - 2));
                    q.set(x, 6, z, "tx_canopy_arm_" + Math.min(6, k) + "[facing=north]");
                } else {
                    q.set(x, 6, z, "tx_canopy");
                }
                for (int y = 1; y < 6; y++) {
                    q.set(x, y, z, "air");
                }
                q.set(x, 7, z, "air");
            }
        }
        // under it: a bin by each end column, the news boxes, a bike rack at the far end
        q.set(1, 1, -1, "tx_bin");
        q.set(L - 2, 1, -1, "tx_bin");
        if (chance() < 0.7) {
            q.set(7, 1, -1, "gs_news_" + rng.nextInt(3) + "[facing=north]");
            q.set(8, 1, -1, "gs_news_" + rng.nextInt(3) + "[facing=north]");
        }
        if (chance() < 0.6) {
            q.set(19, 1, -1, "sc_bike_rack[axis=x]");
            q.set(20, 1, -1, "sc_bike_rack[axis=x]");
        }
    }

    // ------------------------------------------------------------------ inside

    private void hall() {
        // validators either side of each pair of doors, facing in (you stamp your ticket on the way out to the train)
        for (int dx : DOORS) {
            q.set(dx - 1, 1, 1, "tx_validator[facing=south]");
            q.set(dx + 2, 1, 1, "tx_validator[facing=south]");
        }
        // benches in rows facing the platform, either side of the way through from the doors
        for (int z : new int[] {5, 7}) {
            for (int x = 2; x <= 6; x++) {
                bench(x, z);
            }
            for (int x = 21; x <= 25; x++) {
                bench(x, z);
            }
        }
        q.set(8, 1, 2, "tx_bin");
        q.set(19, 1, 2, "tx_bin");
        // the network map on each end wall, three by two
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3;
            q.set(1, 3 - row, 5 - col, "tx_map_" + key + "_" + i + "[facing=east]");
            q.set(L - 2, 3 - row, 3 + col, "tx_map_" + key + "_" + i + "[facing=west]");
        }
        // the drinks machine and the payphone on the east end
        q.set(L - 2, 1, 7, "store_vending[facing=west,loot=drinks]");
        q.set(L - 2, 2, 7, "store_vending_top[facing=west]");
        q.set(1, 2, 7, "gs_payphone[facing=east]");
        // the ticket machines on the service wall, one kicked in for the coins
        for (int x = 9; x <= 11; x++) {
            boolean smashed = chance() < 0.4;
            q.set(x, 1, WALL_Z - 1, (smashed ? "tx_tvm_smashed" : "tx_tvm") + "[facing=north]");
            q.set(x, 2, WALL_Z - 1, (smashed ? "tx_tvm_top_smashed" : "tx_tvm_top") + "[facing=north]");
        }
        // the ticket office's four windows, the departures board over them
        for (int x = 14; x <= 17; x++) {
            q.set(x, 1, WALL_Z, "tx_ticket_counter[facing=north]");
            q.set(x, 2, WALL_Z, "tx_ticket_window[facing=north]");
        }
        for (int i = 0; i < 8; i++) {
            int col = i % 4, row = i / 4;
            q.set(17 - col, 5 - row, WALL_Z - 1, "tx_board_" + i + "[facing=north]");
        }
        q.set(20, 4, WALL_Z - 1, "bk_wall_clock[facing=north]");
        // the hung signs, under the ceiling
        sign("trains", 13, 2, "south");
        sign("tickets", 15, WALL_Z - 2, "north");
        sign("toilets", 3, WALL_Z - 2, "north");
        sign("kiosk", 23, WALL_Z - 2, "north");
        // the lamps hung from the slats either side of the rooflight, clear of the signs and the trusses
        for (int x : new int[] {5, 11, 16, 22}) {
            for (int z : new int[] {2, 7}) {
                q.set(x, TOP, z, "tx_pendant");
            }
        }
    }

    private void bench(int x, int z) {
        double r = chance();
        if (r < 0.85) {
            q.set(x, 1, z, "tx_bench[facing=north]");
        } else if (r < 0.92) {
            q.set(x, 1, z, "trash_bag[facing=" + Site.FACINGS[rng.nextInt(4)] + "]");
        }
    }

    /** A two-block hung sign facing the given way, tile 0 to the reader's left. */
    private void sign(String what, int x, int z, String facing) {
        boolean north = facing.equals("north");
        q.set(north ? x + 1 : x, TOP, z, "tx_sign_" + what + "_0[facing=" + facing + "]");
        q.set(north ? x : x + 1, TOP, z, "tx_sign_" + what + "_1[facing=" + facing + "]");
    }

    private void rooms() {
        // the toilets: two pans along the back, the basins and their mirror on the end wall
        Doors.abandoned(q, rng, 4, 1, WALL_Z, "home_door_white", "north", Doors.hinge(rng));
        q.set(2, 1, BACK_Z - 1, "fit_toilet_stall[facing=north]");
        q.set(5, 1, BACK_Z - 1, "fit_toilet_stall[facing=north]");
        q.set(1, 1, 12, "home_sink_white[facing=east]");
        q.set(1, 1, 13, "home_sink_white[facing=east]");
        q.set(1, 2, 12, "sh_mirror[facing=east]");
        // the staff office: lockers, the desk, the files, a fire door out the back, the way through to the ticket office
        Doors.abandoned(q, rng, 12, 1, WALL_Z, Doors.FLAT, "north", Doors.hinge(rng));
        Doors.abandoned(q, rng, 13, 1, 13, Doors.FLAT, "east", Doors.hinge(rng));
        Doors.abandoned(q, rng, 10, 1, BACK_Z, Doors.STEEL, "south", Doors.hinge(rng));
        q.set(8, 1, BACK_Z - 1, "fit_locker_lo[facing=north]");
        q.set(8, 2, BACK_Z - 1, "fit_locker_hi[facing=north]");
        q.set(9, 1, BACK_Z - 1, "fit_locker_lo[facing=north]");
        q.set(9, 2, BACK_Z - 1, "fit_locker_hi[facing=north]");
        q.set(11, 1, BACK_Z - 1, "filing_cabinet[facing=north,loot=office]");
        q.set(12, 1, BACK_Z - 1, "filing_cabinet[facing=north,loot=office]");
        q.set(10, 1, 12, "desk[facing=north]");
        q.set(10, 1, 11, chance() < 0.4 ? "office_chair_fallen[facing=west]" : "office_chair[facing=south]");
        // the ticket office: the counter inside the windows with the till, the safe, the chairs
        for (int x = 14; x <= 17; x++) {
            q.set(x, 1, WALL_Z + 1, x == 15 ? "store_register[facing=north,loot=till]" : "store_counter[facing=north,loot=office]");
            if (chance() < 0.6) {
                q.set(x, 1, WALL_Z + 2, chance() < 0.3 ? "office_chair_fallen[facing=" + Site.FACINGS[rng.nextInt(4)] + "]"
                        : "office_chair[facing=south]");
            }
        }
        q.set(19, 1, BACK_Z - 1, "store_safe[facing=north,loot=cash]");
        q.set(18, 1, BACK_Z - 1, "filing_cabinet[facing=north,loot=office]");
        // the kiosk: its counter open to the hall, shelves behind, the cooler and the coffee machine
        for (int x = 21; x <= 26; x++) {
            for (int y = 1; y <= 3; y++) {
                q.set(x, y, WALL_Z, "air");
            }
        }
        for (int x = 21; x <= 24; x++) {
            q.set(x, 1, WALL_Z, x == 22 ? "store_register[facing=north,loot=till]" : "store_counter[facing=north,loot=grocery]");
            q.set(x, 1, BACK_Z - 1, "store_shelf_grocery[facing=north,loot=grocery]");
        }
        q.set(26, 1, 12, "store_cooler[facing=west,loot=drinks]");
        q.set(26, 2, 12, "store_cooler_top[facing=west]");
        q.set(26, 1, 13, "store_cooler[facing=west,loot=drinks]");
        q.set(26, 2, 13, "store_cooler_top[facing=west]");
        q.set(25, 1, BACK_Z - 1, "store_coffee[facing=north,loot=kitchen]");
        q.set(21, 1, 8, "gs_news_" + rng.nextInt(3) + "[facing=north]");
    }

    /** What the years and the looters left: shards under the broken panes, papers, leaves blown in, blood. */
    private void wear() {
        for (int x = 1; x < L - 1; x++) {
            String g = q.get(x, 2, 0);
            boolean open = g != null && (g.startsWith("tx_glass_broken") || g.startsWith("tx_glass_frame"));
            for (int z = 1; z < WALL_Z; z++) {
                if (q.get(x, 1, z) != null && !"air".equals(q.get(x, 1, z))) {
                    continue;
                }
                double r = chance();
                if (open && z <= 2 && r < 0.5) {
                    q.set(x, 1, z, "glass_shards");
                } else if (r < 0.05) {
                    q.set(x, 1, z, "paper");
                } else if (r < 0.08 && z <= 4) {
                    q.set(x, 1, z, "leaf_litter");
                } else if (r < 0.084) {
                    q.set(x, 1, z, "blood_splat");
                }
            }
        }
        // spray on the back
        for (int k = 0; k < 2; k++) {
            int x = 2 + rng.nextInt(L - 8);
            if (chance() < 0.6) {
                q.set(x, 2, D - 1, "graffiti_" + rng.nextInt(8) + "[facing=south]");
            }
        }
    }
}
