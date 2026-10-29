package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven International's terminal, in the airport's frame ({@link Airport}: x along the runway,
 * z across it, the landside north): a 1980s regional terminal of precast and tinted glass, the
 * kerb and its canopy along the north face, two piers out over the apron to the south.
 *
 * <ul>
 * <li>The main hall, x {@link #X0}..{@link #X1}, z {@link #Z0}..{@link #Z1}. On the ground floor the
 * ticketing hall (west) with four airlines' counters, their belts and their backlit headers, the
 * queue lines and the kiosks, the shelter's cots in rows where the evacuees slept; the lobby (the
 * middle) with the escalators and stair up to security, the lifts, the departures screens and
 * the restrooms; the baggage claim (east) with its three carousels, the rental desks and the
 * baggage office. Both halls run up to the roof; behind them, under the upper floor, the bag room
 * where the belts end.</li>
 * <li>Upstairs, over the lobby, the security checkpoint: the queue maze, eight lanes of x-ray,
 * arches and tables, the glass line they hold; and the airside concourse along the back, over the
 * bag room: the food court, the newsstand and the bar, the shops, seats along the windows looking
 * out over the apron.</li>
 * <li>Pier B (gates 5 to 10) out from the concourse: the corridor down the middle, the gate
 * lounges either side with their seats facing the windows, the podiums and the jet bridge doors,
 * the gate numbers hung over them, the end lounge; the ramp rooms under them.</li>
 * <li>Pier A (gates 1 to 4), after Modern Warfare 2's "Terminal" (Josiah, 2026-10-29: "Main
 * concourse + one pier", the layout and the feel, our own art and names): wider, glass to its
 * roof, a mezzanine down its east side with the food court on it (Grilltown's burgers, the Sakana
 * sushi bar), Airside Books to walk through and the Hangar Coffee bar under it, the escalators up;
 * the gate lounges along the west windows, and at the end gate 1, Gray Eagle 214 parked nose in
 * with the jet bridge at its front door and the stair truck at its back, the ramp's gear about it
 * ({@link #homagePier}).</li>
 * </ul>
 *
 * <p>What happened here: the airport was the county's evacuation point. The flights were suspended,
 * the evacuees slept on cots in the ticketing hall, the Guard held the security line with sandbags
 * and it fell; the luggage lies where it was dropped.
 */
final class AirportTerminal extends Grounds {
    /** The main hall's outer walls. */
    static final int X0 = -140, X1 = 140, Z0 = -396, Z1 = -331;
    /** The ground floor's back wall (the halls north of it, the bag room south), under the upper floor's edge. */
    static final int BACK = -365;
    /** The upper floor's slab, the main hall's roof, the piers' roof. */
    static final int UP = 6, ROOF = 16, PROOF = 13;
    /** The security mezzanine's half width (x -MEZ..MEZ over the lobby). */
    static final int MEZ = 40;
    /** Each pier's west wall, pier B's width (pier A runs to HX1), and where the piers end. */
    static final int[] PIER_X = {-140, 108};
    static final int PIER_W = 24, PIER_Z1 = -215;
    /** Pier A's east wall; its mezzanine's floor, west edge and south edge; its roof. */
    static final int HX1 = -100, MZ = 14, MZ_X0 = -120, MZ_Z1 = -263, HROOF = 23;
    /** A gate with no sign over the corridor. */
    private static final int NO_SIGN = Integer.MIN_VALUE;
    /** Gray Eagle 214 at gate 1: its middle and its nose (the plane's frame origin). */
    static final int PLANE_X = -116, PLANE_Z = -212;
    /** The gates down each pier, the jet bridge doors' middles. */
    static final int[] GATE_Z = {-312, -274, -236};
    /** The kerb's canopy, out from the north face. */
    static final int CANOPY_Z = -404;
    /** Everything the terminal builds lies within these (in the frame). */
    static final int BX0 = -150, BX1 = 150, BZ0 = -420, BZ1 = -150;
    /** The four airlines' counter runs, west to east, from x = -137 every 23. */
    static final String[] AIRLINES = {"eagle", "coastal", "north", "skyline"};

    AirportTerminal(Placer p, SplittableRandom rng) {
        super(p, rng, X1 - X0 + 1, Z1 - Z0 + 1, 0.35, 0.0, 0.35);
    }

    void build() {
        shell();
        canopy();
        ticketing();
        lobby();
        claim();
        bagRoom();
        security();
        concourse();
        homagePier();
        pier(PIER_X[1], 5);
        lights();
        roof();
        aftermath();
    }

    // ------------------------------------------------------------------ the shell

    private String precast() {
        return chance() < 0.25 ? "ml_precast_b" : "ml_precast";
    }

    private String glass(String facing, double broken) {
        double r = chance();
        return (r < broken ? "wf_glass_broken" : power && r < broken + 0.12 ? "wf_glass_lit" : "wf_glass") + "[facing=" + facing + "]";
    }

    /** Floors, the outer walls, the upper floor's slabs, the columns. */
    private void shell() {
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                boolean wall = x == X0 || x == X1 || z == Z0 || z == Z1;
                set(x, 0, z, wall ? "concrete_dark" : z > BACK ? "floor_concrete" : chance() < 0.1 ? "tx_terrazzo_b" : "tx_terrazzo");
                for (int y = 1; y < ROOF; y++) {
                    set(x, y, z, wall ? outer(x, y, z) : "air");
                }
                boolean upper = z >= BACK || Math.abs(x) <= MEZ;
                if (upper && !wall) {
                    set(x, UP, z, Math.abs(x) <= MEZ && z < BACK ? "un_slab_terrazzo" : chance() < 0.1 ? "tx_terrazzo_b" : "un_slab_terrazzo");
                }
            }
        }
        // the ground floor's back wall, the halls' side of it plaster; the doors through it later
        for (int x = X0 + 1; x < X1; x++) {
            if (Math.abs(x) > MEZ) {
                wallX(x, x, BACK, 0, UP - 1, "tx_wall");
            }
        }
        // the columns: precast drums every twelve across the bag room and the halls
        for (int x = X0 + 12; x < X1; x += 12) {
            for (int z : new int[] {-385, -348}) {
                if (Math.abs(x) <= MEZ + 2 && z < BACK || Math.abs(x) < 6) {
                    continue;
                }
                int top = z < BACK && Math.abs(x) > MEZ ? ROOF - 1 : UP - 1;
                for (int y = 1; y <= top; y++) {
                    set(x, y, z, y == 1 ? "tx_column_base" : y == top ? "tx_column_cap" : "tx_column");
                }
                if (z > BACK) {
                    for (int y = UP + 1; y < ROOF; y++) {
                        set(x, y, z, y == UP + 1 ? "tx_column_base" : y == ROOF - 1 ? "tx_column_cap" : "tx_column");
                    }
                }
            }
        }
        // the upper floor's edge over the halls: a glass rail
        for (int x = X0 + 1; x < X1; x++) {
            if (Math.abs(x) > MEZ) {
                set(x, UP + 1, BACK, "tx_glass_rail[axis=x]");
            }
        }
        for (int z = Z0 + 1; z < BACK; z++) {
            set(-MEZ, UP + 1, z, "tx_glass_rail[axis=z]");
            set(MEZ, UP + 1, z, "tx_glass_rail[axis=z]");
        }
        for (int x = -MEZ; x <= MEZ; x++) {
            set(x, UP + 1, Z0 + 1, "air");
        }
        // the kerb's doors: four pairs into ticketing, four into claim, the vestibule's glass round them
        for (int x : new int[] {-118, -98, -78, -58, 56, 76, 96, 116}) {
            vestibule(x);
        }
        // the airside face's ground floor: the bag room's roller doors to the apron
        for (int x = X0 + 6; x < X1 - 6; x += 16) {
            if (inPier(x) || inPier(x + 4)) {
                continue;
            }
            for (int dx = 0; dx < 5; dx++) {
                for (int y = 1; y <= 4; y++) {
                    set(x + dx, y, Z1, y <= (chance() < 0.3 ? 1 : 4) ? (y == 4 || chance() < 0.85 ? "pt_shutter_steel" : "air") : "air");
                }
                set(x + dx, 5, Z1, "ml_precast_band");
            }
        }
    }

    private static boolean inPier(int x) {
        for (int px : PIER_X) {
            if (x >= px && x <= px + pierW(px)) {
                return true;
            }
        }
        return false;
    }

    /** A pier's width from its west wall to its east. */
    private static int pierW(int px) {
        return px == PIER_X[0] ? HX1 - PIER_X[0] : PIER_W;
    }

    /** What the outer wall is at (x, y, z): the kerb face glass from end to end, the rest precast with glass bands. */
    private String outer(int x, int y, int z) {
        boolean corner = (x == X0 || x == X1) && (z == Z0 || z == Z1);
        if (corner || y == ROOF - 1) {
            return "ml_precast_band";
        }
        if (z == Z0) {
            // the kerb face: a mullion every six, glass between, a band at the upper floor's level
            if (Math.floorMod(x - X0, 6) == 0) {
                return "wf_steel";
            }
            return y == UP ? "ml_precast_band" : glass("north", 0.1);
        }
        if (z == Z1) {
            // the airside face: precast under the upper floor, glass over it looking out at the planes
            if (y <= UP) {
                return y == UP ? "ml_precast_band" : precast();
            }
            return Math.floorMod(x - X0, 6) == 0 ? "wf_steel" : glass("south", 0.12);
        }
        // the ends: precast, a clerestory band of glass high up
        String out = x == X0 ? "west" : "east";
        if (y >= ROOF - 4 && y <= ROOF - 2 && Math.floorMod(z - Z0, 4) != 0) {
            return glass(out, 0.15);
        }
        return y == UP ? "ml_precast_band" : precast();
    }

    /** Two glass doors in the kerb face at x, x + 1, their vestibule's glass box inside. */
    private void vestibule(int x) {
        for (int dx = -1; dx <= 2; dx++) {
            for (int y = 1; y <= 3; y++) {
                set(x + dx, y, Z0, "air");
                set(x + dx, y, Z0 + 3, dx == -1 || dx == 2 ? "tx_glass[axis=z]" : "air");
            }
            set(x + dx, 4, Z0 + 3, "wf_steel");
            set(x + dx, 4, Z0, "wf_steel");
        }
        for (int z = Z0 + 1; z <= Z0 + 2; z++) {
            for (int y = 1; y <= 3; y++) {
                set(x - 1, y, z, "tx_glass[axis=z]");
                set(x + 2, y, z, "tx_glass[axis=z]");
            }
            set(x - 1, 4, z, "wf_steel");
            set(x + 2, 4, z, "wf_steel");
            for (int dx = -1; dx <= 2; dx++) {
                set(x + dx, 4, z, "wf_steel");
                set(x + dx, 0, z, "tx_terrazzo_d");
            }
        }
        Doors.pair(p, rng, x, 1, Z0, Doors.SHOP, "north");
        Doors.pair(p, rng, x, 1, Z0 + 3, Doors.SHOP, "north");
    }

    /**
     * The kerb: a sidewalk under a steel canopy the length of the face, its columns along the kerb
     * line, the name in letters on its fascia over the middle doors.
     */
    private void canopy() {
        for (int x = X0 - 2; x <= X1 + 2; x++) {
            for (int z = CANOPY_Z; z < Z0; z++) {
                set(x, 0, z, chance() < 0.15 ? "sidewalk_cracked" : "sidewalk");
                set(x, UP + 1, z, z == CANOPY_Z ? "wf_panel" : "un_roof");
            }
            set(x, UP + 2, CANOPY_Z, "wf_panel");
            set(x, UP, CANOPY_Z, "ml_precast_band");
        }
        for (int x = X0; x <= X1; x += 12) {
            for (int y = 1; y <= UP; y++) {
                set(x, y, CANOPY_Z + 1, y == 1 ? "tx_column_base" : "tx_column");
            }
        }
        // the name, its sixteen tiles read from the kerb: tile 0 at the higher x
        for (int i = 0; i < 16; i++) {
            if (chance() < 0.06) {
                continue;
            }
            set(7 - i, UP + 1, CANOPY_Z - 1, "ap_name_" + i + "[facing=north]");
        }
        // the lights under it and the benches, the bins along the kerb
        for (int x = X0 + 6; x < X1; x += 12) {
            set(x, UP, CANOPY_Z + 3, power && chance() < 0.4 ? "ceiling_light" : "ceiling_light_off");
            if (chance() < 0.6) {
                set(x + 3, 1, CANOPY_Z + 6, "tx_bench[facing=north]");
            }
            if (chance() < 0.5) {
                set(x - 3, 1, CANOPY_Z + 1, chance() < 0.2 ? "ext_trash_can_tipped" : "ext_trash_can");
            }
        }
    }

    // ------------------------------------------------------------------ ground floor

    /** The airline's counters, x from xs over 20: counter, counter, scale; the belt behind; its header and queue. */
    private void airline(String k, int xs) {
        int zc = BACK - 4;
        for (int x = xs; x < xs + 20; x++) {
            int i = x - xs;
            set(x, 1, zc, (i % 3 == 2 ? "ap_counter_scale" : "ap_counter") + "[facing=north]");
            set(x, 1, zc + 2, (chance() < 0.12 ? "ap_belt_bags" : "ap_belt") + "[axis=x]");
            if (i % 3 == 1 && chance() < 0.5) {
                set(x, 1, zc + 1, "office_chair[facing=" + any("north", "east", "west") + "]");
            }
        }
        // the belt goes through the wall at the run's east end, on into the bag room
        set(xs + 19, 1, BACK, "ap_belt_hole[facing=north]");
        for (int z = BACK + 1; z <= BACK + 6; z++) {
            set(xs + 19, 1, z, "ap_belt[axis=z]");
        }
        // the agents' door through to the bag room at the gap
        set(xs + 21, 1, BACK, "air");
        set(xs + 21, 2, BACK, "air");
        door(xs + 21, 0, BACK, Doors.FLAT, "south");
        // the header, three tiles over the middle, tile 0 at the viewer's left (the higher x facing north)
        for (int i = 0; i < 3; i++) {
            set(xs + 11 - i, 4, zc + 3, "ap_airline_" + k + "_" + i + "[facing=north]");
        }
        // the queue: three lines of barrier, open at alternate ends, the posts at the ends
        for (int r = 0; r < 3; r++) {
            int z = zc - 3 - r * 2;
            int from = r % 2 == 0 ? xs + 1 : xs + 4, to = r % 2 == 0 ? xs + 16 : xs + 19;
            set(from - 1, 1, z, "wf_queue_post");
            set(to + 1, 1, z, "wf_queue_post");
            for (int x = from; x <= to; x++) {
                if (chance() < 0.08) {
                    continue;
                }
                set(x, 1, z, chance() < 0.04 ? "air" : "wf_queue[axis=x]");
            }
        }
        // the kiosks out in front
        for (int x = xs + 4; x < xs + 18; x += 5) {
            set(x, 1, zc - 12, "ap_kiosk[facing=north]");
        }
    }

    /**
     * The ticketing hall, x X0 + 1 .. -MEZ - 1: the four airlines' counters along the back wall,
     * the shelter's cots in rows in front of them, the FEMA table by the doors.
     */
    private void ticketing() {
        for (int i = 0; i < AIRLINES.length; i++) {
            airline(AIRLINES[i], -137 + 23 * i);
        }
        // the cots: rows along x in the hall's north half, a gangway every fourth
        for (int x = X0 + 3; x < -MEZ - 3; x += 2) {
            if (Math.floorMod(x, 8) == 1) {
                continue;
            }
            for (int z = Z0 + 6; z <= Z0 + 14; z += 3) {
                if (chance() < 0.18) {
                    continue;
                }
                String b = chance() < 0.3 ? "_b" : "";
                set(x, 1, z, "sc_cot_head" + b + "[facing=north]");
                set(x, 1, z + 1, "sc_cot_foot" + b + "[facing=north]");
                if (chance() < 0.25) {
                    set(x + 1, 1, z, bag());
                }
            }
        }
        // the shelter's table by the west doors: the water, the notices
        for (int x = -124; x <= -120; x++) {
            set(x, 1, Z0 + 4, "trade_banquet_table[facing=north]");
        }
        set(-122, 2, Z0 + 4, "ap_notice_fema[facing=north]");
        set(-126, 1, Z0 + 4, "st_pallet_cots[facing=north]");
        set(-118, 1, Z0 + 5, "trade_water_cooler[facing=north]");
        // the wayfinding: TICKETING over the hall, pointing back to it from the lobby
        hungSign("ticketing", -46, 10, Z0 + 12, "north");
    }

    /** A wayfinding sign three tiles long hung at y, its middle tile at x, read from `facing`. */
    private void hungSign(String key, int x, int y, int z, String facing) {
        boolean ns = facing.equals("north") || facing.equals("south");
        for (int i = 0; i < 3; i++) {
            // facing north the viewer's left is the higher x; facing south the lower; east the lower z, west the higher
            int off = 1 - i;
            int sx = ns ? x + (facing.equals("north") ? off : -off) : x, sz = ns ? z : z + (facing.equals("east") ? -off : off);
            set(sx, y, sz, "ap_way_" + key + "_" + i + "[facing=" + facing + "]");
        }
    }

    private String bag() {
        double r = chance();
        String f = dir();
        return (r < 0.2 ? "ap_bag_up" : r < 0.35 ? "ap_bag_up_b" : r < 0.55 ? "ap_bag_flat" : r < 0.7 ? "ap_bag_flat_b"
                : r < 0.85 ? "ap_bag_pile" : "ap_bag_open") + "[facing=" + f + "]";
    }

    /**
     * The lobby under the mezzanine: the escalators and stair up the middle rising north, the lifts,
     * the information desk, the departures screens, the restrooms either side.
     */
    private void lobby() {
        // the escalator pair (x -3, -2) and the stair (x 0, 1), rising north from z0 to the mezzanine
        int z0 = BACK - 6;
        for (int k = 1; k <= UP; k++) {
            int z = z0 - k;
            for (int x = -3; x <= 1; x++) {
                for (int y = 1; y < k; y++) {
                    set(x, y, z, "stone_trim");
                }
                if (x == -1) {
                    set(x, k, z, "stone_trim");
                    set(x, k + 1, z, "tx_glass_rail[axis=z]");
                    continue;
                }
                boolean esc = x <= -2;
                set(x, k, z, esc ? "zomboidcraft:escalator[facing=north,run=" + (x == -3 ? "down" : "up") + "]" : "stairs_concrete[facing=north]");
                if (esc) {
                    set(x, k + 1, z, "tx_esc_side_" + (x == -2 ? "r" : "l") + "[facing=north]");
                }
                for (int y = k + (esc ? 2 : 1); y <= k + Builders.HEADROOM; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int x = -3; x <= -2; x++) {
            String hand = x == -2 ? "r" : "l";
            set(x, 0, z0, "tx_esc_plate[facing=north]");
            set(x, 1, z0, "tx_esc_newel_" + hand + "[facing=south]");
            set(x, UP, z0 - UP - 1, "tx_esc_plate[facing=south]");
            set(x, UP + 1, z0 - UP - 1, "tx_esc_newel_" + hand + "[facing=north]");
        }
        // the slot in the mezzanine over them, railed round
        for (int z = z0 - UP; z <= z0 - 1; z++) {
            set(-4, UP + 1, z, "tx_glass_rail[axis=z]");
            set(2, UP + 1, z, "tx_glass_rail[axis=z]");
        }
        for (int x = -4; x <= 2; x++) {
            set(x, UP + 1, z0, "tx_glass_rail[axis=x]");
        }
        // the lifts east of them, their doors to the north on both floors
        lift(6, -386, 3, 3, new int[] {0, UP, ROOF - 4}, "ml_precast", ROOF - 4);
        // the information desk: a ring of counters, the screens on it
        for (int x = -12; x <= -8; x++) {
            set(x, 1, -390, "ap_gate_desk[facing=north]");
            set(x, 1, -386, "ap_gate_desk[facing=south]");
        }
        set(-13, 1, -388, "ap_gate_desk[facing=west]");
        set(-7, 1, -388, "ap_gate_desk[facing=east]");
        // the departures screens either side of the doors' axis
        for (int x : new int[] {-24, 18}) {
            boolean dead = !power || chance() < 0.5;
            set(x, 1, Z0 + 8, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=north]");
            set(x, 2, Z0 + 8, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=north]");
        }
        // the restrooms: men west, women east, against the back wall
        restroom(-38, -22, true);
        restroom(22, 38, false);
        hungSign("security", -1, 4, -392, "north");
        hungSign("rest", -24, 4, -380, "north");
    }

    /** A restroom x0..x1 against the back wall on the ground floor: stalls along the back, basins, its door north. */
    private void restroom(int x0, int x1, boolean men) {
        int z0 = BACK - 9;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z < BACK; z++) {
                set(x, 0, z, "home_floor_tile");
                boolean wall = x == x0 || x == x1 || z == z0;
                for (int y = 1; y < UP; y++) {
                    set(x, y, z, wall ? "home_wall_tile_white" : "air");
                }
            }
        }
        for (int x = x0 + 1; x < x1; x += 2) {
            set(x, 1, BACK - 1, "fit_toilet_stall[facing=north]");
            set(x + 1, 1, BACK - 1, "fit_stall_top[facing=north]");
        }
        for (int x = x0 + 2; x < x1 - 1; x += 2) {
            set(x, 1, z0 + 1, "home_sink_white[facing=south]");
            set(x, 2, z0 + 1, "home_mirror[facing=south]");
        }
        int dx = men ? x1 - 2 : x0 + 2;
        set(dx, 1, z0, "air");
        set(dx, 2, z0, "air");
        door(dx, 0, z0, Doors.FLAT, "north");
        set(dx + (men ? -1 : 1), 3, z0 - 1, (men ? "tr_sign_men" : "tr_sign_ladies") + "[facing=north]");
        lamps(x0, z0, x1, BACK, UP, 0.3);
    }

    /**
     * Baggage claim, x MEZ + 1 .. X1 - 1: three carousels fed down chutes from the bag room, their
     * numbers hung over them; the rental desks along the east wall, the baggage office in the corner.
     */
    private void claim() {
        int n = 1;
        for (int cx : new int[] {64, 92, 120}) {
            carousel(cx, n++);
        }
        // the rental desks along the east wall, the backs of their offices behind
        for (int z = Z0 + 4; z <= Z0 + 18; z++) {
            set(X1 - 3, 1, z, Math.floorMod(z, 5) == 0 ? "ap_counter_scale[facing=west]" : "ap_counter[facing=west]");
        }
        for (int z = Z0 + 3; z <= Z0 + 19; z++) {
            wallZ(X1 - 2, z, z, 0, 3, z == Z0 + 11 ? "air" : "tx_wall");
        }
        hungSign("ground", X1 - 6, 6, Z0 + 11, "west");
        // the baggage office: a glass box in the south-east corner, unclaimed bags racked inside
        int ox0 = X1 - 14, oz0 = BACK - 7;
        for (int x = ox0; x < X1; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, oz0, y <= 1 || y == 4 ? "tx_wall" : "tx_glass[axis=x]");
            }
        }
        for (int z = oz0; z < BACK; z++) {
            for (int y = 1; y <= 4; y++) {
                set(ox0, y, z, y <= 1 || y == 4 ? "tx_wall" : "tx_glass[axis=z]");
            }
        }
        set(ox0, 1, oz0 + 3, "air");
        set(ox0, 2, oz0 + 3, "air");
        door(ox0, 0, oz0 + 3, Doors.FLAT, "west");
        for (int x = ox0 + 2; x < X1 - 1; x += 2) {
            set(x, 1, BACK - 1, "store_shelf_empty[facing=north]");
            for (int y = 2; y <= 3; y++) {
                set(x, y, BACK - 1, y == 2 ? "ap_bag_pile[facing=north]" : "air");
            }
            set(x, 1, oz0 + 2, chance() < 0.5 ? bag() : "air");
        }
        set(ox0 + 3, 1, oz0 + 4, "home_desk[facing=north]");
        set(ox0 + 3, 1, oz0 + 5, "office_chair[facing=north]");
        hungSign("claim", MEZ + 4, 10, Z0 + 12, "north");
    }

    /**
     * A carousel about x cx: two runs down the hall from the chute at the back, joined round at the
     * north, the steel island between, its number hung over the north end.
     */
    private void carousel(int cx, int n) {
        int zs = BACK - 3, zn = Z0 + 11;
        for (int z = zn + 1; z <= zs; z++) {
            for (int x : new int[] {cx - 2, cx + 2}) {
                set(x, 1, z, (chance() < 0.25 ? "ap_carousel_bags" : "ap_carousel") + "[axis=z]");
            }
            for (int x = cx - 1; x <= cx + 1; x++) {
                set(x, 1, z, "wf_steel");
            }
        }
        set(cx - 2, 1, zn, "ap_carousel_end[facing=north]");
        set(cx + 2, 1, zn, "ap_carousel_end[facing=north]");
        for (int x = cx - 1; x <= cx + 1; x++) {
            set(x, 1, zn, "ap_carousel[axis=x]");
        }
        // the chute down from the hole in the back wall
        set(cx, 1, zs + 1, "ap_carousel_feed[facing=north]");
        set(cx, 1, BACK, "ap_belt_hole[facing=north]");
        set(cx, 2, BACK, "tx_wall");
        for (int z = BACK + 1; z <= BACK + 4; z++) {
            set(cx, 1, z, "ap_belt[axis=z]");
        }
        set(cx, 9, zn + 2, "ap_claim_" + n + "[facing=north]");
        set(cx, 9, zn + 3, "ap_claim_" + n + "[facing=south]");
        // the bags nobody came for, round its foot
        for (int k = 0; k < 6; k++) {
            int x = cx + (chance() < 0.5 ? -4 : 4) + rng.nextInt(3) - 1, z = zn + 1 + rng.nextInt(zs - zn);
            if (empty(x, 1, z)) {
                set(x, 1, z, chance() < 0.3 ? "ap_cart[facing=" + dir() + "]" : bag());
            }
        }
    }

    /**
     * The bag room under the upper floor: the belts in from the counters running to the make-up
     * belt, the carts (the dollies stand outside), lockers and the ramp office, the doors out.
     */
    private void bagRoom() {
        // the make-up belt along x behind the counters, the claim feeds' stubs
        for (int x = -137; x <= -46; x++) {
            set(x, 1, BACK + 7, (chance() < 0.15 ? "ap_belt_bags" : "ap_belt") + "[axis=x]");
        }
        // the ramp office in the middle: lockers, a table, the radio desk
        int z0 = BACK + 10, z1 = Z1 - 2;
        for (int x = -30; x <= 30; x++) {
            set(x, 1, z0, Math.abs(x) % 6 == 3 ? "air" : "tx_wall");
            set(x, 2, z0, Math.abs(x) % 6 == 3 ? "air" : "tx_wall");
            for (int y = 3; y < UP; y++) {
                set(x, y, z0, "tx_wall");
            }
        }
        for (int x = -28; x <= 28; x += 2) {
            set(x, 1, z1, "fit_locker_lo[facing=north]");
            set(x, 2, z1, "fit_locker_hi[facing=north]");
        }
        for (int x = -12; x <= 12; x += 6) {
            set(x, 1, z0 + 6, "fit_mess_table[axis=x]");
            set(x - 1, 1, z0 + 5, "store_chair[facing=south]");
            set(x + 1, 1, z0 + 7, "store_chair[facing=north]");
        }
        set(20, 1, z0 + 3, "home_desk[facing=north]");
        set(20, 1, z0 + 4, "office_chair[facing=north]");
        set(-20, 1, z0 + 3, "store_vending[facing=south]");
        set(-20, 2, z0 + 3, "store_vending_top[facing=south]");
        lamps(-30, z0, 30, z1, UP, 0.3);
        // the fire stairs up to the concourse at both ends of the office
        stair(-45, BACK + 1, true, new int[] {0, UP, ROOF}, "ml_precast", "un_roof");
        stair(41, BACK + 1, true, new int[] {0, UP, ROOF}, "ml_precast", "un_roof");
    }

    // ------------------------------------------------------------------ upstairs

    /**
     * The checkpoint on the mezzanine: up the escalators into the queue maze to the north, back
     * south through eight lanes (podium, bins, the x-ray's belt, the arch beside it, the benches),
     * the glass line across through the arches; the Guard's sandbags where they held it.
     */
    private void security() {
        int y = UP + 1;
        // the maze: barrier lines along x, open at alternate ends
        for (int r = 0; r < 5; r++) {
            int z = Z0 + 4 + r * 3;
            int from = r % 2 == 0 ? -36 : -33, to = r % 2 == 0 ? 33 : 36;
            for (int x = from; x <= to; x++) {
                set(x, y, z, chance() < 0.06 ? "air" : "wf_queue[axis=x]");
            }
            set(from - 1, y, z, "wf_queue_post");
            set(to + 1, y, z, "wf_queue_post");
        }
        // the escalators' well kept apart from the lanes by glass
        for (int z = BACK - 14; z < BACK; z++) {
            for (int yy = y; yy <= y + 2; yy++) {
                set(-5, yy, z, "tx_glass[axis=z]");
                set(3, yy, z, "tx_glass[axis=z]");
            }
        }
        // the lanes
        for (int lane : new int[] {-36, -28, -20, -12, 7, 15, 23, 31}) {
            lane(lane, y);
        }
        // the line: glass across at the arches, the posts between
        int zl = BACK - 10;
        for (int x = -MEZ + 1; x < MEZ; x++) {
            if (x >= -5 && x <= 3) {
                continue;
            }
            String s = get(x, y, zl);
            if (s == null || s.equals("air")) {
                for (int yy = y; yy <= y + 2; yy++) {
                    set(x, yy, zl, Math.floorMod(x, 4) == 0 ? "wf_steel" : "tx_glass[axis=x]");
                }
            }
        }
        // the Guard's line: sandbags across the exits, the table, the boxes they left
        for (int x = -30; x <= 30; x++) {
            if ((x >= -5 && x <= 3) || chance() < 0.25) {
                continue;
            }
            set(x, y, BACK - 2, "sandbags[facing=north]");
        }
        set(-6, y, BACK - 1, "trade_banquet_table[facing=north]");
        set(-7, y, BACK - 1, "crate");
        set(0, y + 2, Z0 + 2, "ap_notice_evac[facing=south]");
    }

    /** One lane, its belt down x = lx and the arch at lx + 2, from the podium in the north to the benches. */
    private void lane(int lx, int y) {
        int zl = BACK - 10;
        set(lx + 2, y, zl - 6, "ap_podium[facing=north]");
        set(lx - 1, y, zl - 4, "ap_bins");
        if (chance() < 0.6) {
            set(lx - 1, y + 1, zl - 4, "air");
        }
        set(lx, y, zl - 4, "ap_rollers[facing=east]");
        set(lx, y, zl - 3, "ap_rollers[facing=east]");
        set(lx, y, zl - 2, "ap_xray_in[facing=east]");
        set(lx, y, zl - 1, "ap_xray[facing=east]");
        set(lx, y, zl, "ap_xray[facing=east]");
        set(lx, y, zl + 1, "ap_xray_out[facing=east]");
        set(lx, y, zl + 2, "ap_rollers[facing=east]");
        set(lx, y, zl + 3, "ap_rollers[facing=east]");
        set(lx + 2, y, zl, "ap_wtmd[facing=north]");
        set(lx + 2, y + 1, zl, "ap_wtmd_top[facing=north]");
        for (int yy = y; yy <= y + 2; yy++) {
            set(lx + 1, yy, zl, "wf_steel");
            set(lx + 3, yy, zl, "wf_steel");
        }
        set(lx + 3, y, zl + 5, "tx_bench[facing=west]");
        // what the last ones through left: tubs on the floor, a bag on the belt
        for (int k = 0; k < 3; k++) {
            int x = lx + rng.nextInt(4) - 1, z = zl - 3 + rng.nextInt(9);
            if (empty(x, y, z) && chance() < 0.5) {
                set(x, y, z, chance() < 0.5 ? "ap_bins" : bag());
            }
        }
    }

    /**
     * The airside concourse over the bag room: the food court in the west half (counters along the
     * hall side, tables in front), the newsstand and the bar either side of the checkpoint's exits,
     * shops in the east half, rows of seats along the windows, the restrooms by the piers.
     */
    private void concourse() {
        int y = UP + 1;
        // food: six counters along the hall edge facing the windows
        int n = 0;
        for (int x = -100; x + 8 <= -50; x += 9) {
            unit(x, x + 8, n++);
        }
        for (int x = -96; x <= -54; x += 4) {
            for (int z = BACK + 10; z <= BACK + 20; z += 4) {
                if (chance() < 0.15) {
                    continue;
                }
                set(x, y, z, "store_table");
                for (int[] c : new int[][] {{1, 0, 3}, {-1, 0, 1}, {0, 1, 2}, {0, -1, 0}}) {
                    if (chance() < 0.7) {
                        set(x + c[0], y, z + c[1], (chance() < 0.15 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=" + Bldg.DIRS[c[2]] + "]");
                    }
                }
            }
        }
        // the newsstand west of the exits, the bar east
        shop(-34, -18, "news");
        shop(18, 34, "bar");
        // the shops in the east half: gifts, books
        shop(52, 70, "gifts");
        shop(74, 92, "books");
        // seats in rows along the windows, facing out
        for (int x = X0 + 4; x < X1 - 4; x++) {
            if (inPier(x) || Math.abs(x) < 6 || Math.floorMod(x, 10) == 0) {
                continue;
            }
            for (int z : new int[] {Z1 - 3, Z1 - 7}) {
                double r = chance();
                set(x, y, z, (r < 0.08 ? "ap_seats_bag" : r < 0.15 ? "ap_seats_torn" : "ap_seats") + "[facing=south]");
            }
        }
        // the restrooms at the ends by the piers' mouths, the screens
        upstairsRestroom(-106, -100, true);
        upstairsRestroom(100, 106, false);
        for (int x : new int[] {-10, 8}) {
            boolean dead = !power || chance() < 0.5;
            set(x, y, BACK + 12, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=north]");
            set(x, y + 1, BACK + 12, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=north]");
        }
        hungSign("gates_a", -60, y + 5, BACK + 16, "east");
        hungSign("gates_b", 60, y + 5, BACK + 16, "west");
    }

    /** A food counter x0..x1 against the hall edge: the counter line facing south, the kitchen behind. */
    private void unit(int x0, int x1, int n) {
        int y = UP + 1, z0 = BACK + 1, line = BACK + 6;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= line; z++) {
                set(x, UP, z, "home_floor_tile");
            }
            for (int yy = y; yy < ROOF - 4; yy++) {
                set(x, yy, z0, "home_wall_tile_white");
            }
        }
        for (int z = z0; z <= line; z++) {
            for (int yy = y; yy < ROOF - 4; yy++) {
                set(x0, yy, z, "home_wall_tile_white");
                set(x1, yy, z, "home_wall_tile_white");
            }
        }
        String[] kit = {"store_grill", "store_oven", "home_fridge", "st_fountain", "store_shelf_grocery"};
        for (int x = x0 + 1; x < x1; x++) {
            set(x, y, line, x == x0 + 2 ? "store_register[facing=south]" : "store_counter[facing=south]");
            set(x, y, z0 + 1, kit[(x - x0 + n) % kit.length] + "[facing=south]");
            set(x, y + 3, line, "stone_trim");
            set(x, y + 4, line, power && chance() < 0.4 ? "dn_glassblock_lit" : "dn_glassblock");
            if (x == x0 + 1) {
                for (int yy = y + 1; yy <= y + 2; yy++) {
                    set(x, yy, line, "st_grille_half[facing=south]");
                }
            }
        }
        set(x1 - 1, y, line, "air");
        set(x1 - 1, y + 1, line, "air");
    }

    /** A shop x0..x1 off the concourse along the hall edge, its open front south, fitted out by kind. */
    private void shop(int x0, int x1, String kind) {
        int y = UP + 1, z0 = BACK + 1, z1 = BACK + 8;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, UP, z, kind.equals("bar") ? "un_slab_carpet" : "un_slab_vct");
            }
            for (int yy = y; yy < ROOF - 4; yy++) {
                set(x, yy, z0, "drywall");
                set(x, yy, z1, yy <= y + 2 && x > x0 + 1 && x < x1 - 1 ? "air" : yy == y + 3 ? "wf_panel" : "drywall");
            }
        }
        for (int z = z0; z <= z1; z++) {
            for (int yy = y; yy < ROOF - 4; yy++) {
                set(x0, yy, z, "drywall");
                set(x1, yy, z, "drywall");
            }
        }
        switch (kind) {
            case "bar" -> {
                for (int x = x0 + 2; x < x1 - 1; x++) {
                    set(x, y, z0 + 3, x == x0 + 3 ? "store_register[facing=south]" : "store_counter[facing=south]");
                    set(x, y, z0 + 1, x % 3 == 0 ? "trade_bar_taps[facing=south]" : "store_shelf_liquor[facing=south]");
                    if (chance() < 0.6) {
                        set(x, y, z0 + 4, chance() < 0.2 ? "dn_stool_torn" : "dn_stool_r");
                    }
                }
                set(x0 + 2, y + 1, z0, "trade_bar_mirror[facing=south]");
            }
            case "news" -> {
                for (int x = x0 + 2; x < x1 - 1; x++) {
                    set(x, y, z0 + 1, (chance() < 0.4 ? "store_shelf_empty" : "store_shelf_grocery") + "[facing=south]");
                    set(x, y + 1, z0 + 1, "store_shelf_grocery_top[facing=south]");
                    if (x % 3 == 0) {
                        set(x, y, z0 + 4, "store_cooler[facing=south]");
                        set(x, y + 1, z0 + 4, "store_cooler_top[facing=south]");
                    }
                }
                set(x1 - 2, y, z1 - 2, "store_register[facing=west]");
            }
            case "books" -> {
                for (int x = x0 + 2; x < x1 - 1; x++) {
                    set(x, y, z0 + 1, "home_bookcase[facing=south]");
                    set(x, y + 1, z0 + 1, "home_bookcase_top[facing=south]");
                    if (x % 4 == 0) {
                        set(x, y, z0 + 4, "home_books_stack[facing=" + dir() + "]");
                    }
                }
                set(x0 + 2, y, z1 - 2, "store_register[facing=east]");
            }
            default -> {
                for (int x = x0 + 2; x < x1 - 1; x++) {
                    set(x, y, z0 + 1, "store_shelf_thrift[facing=south]");
                    set(x, y + 1, z0 + 1, "store_shelf_thrift_top[facing=south]");
                    if (x % 4 == 1) {
                        set(x, y, z0 + 4, "store_clothes_rack[facing=south]");
                    }
                }
                set(x1 - 2, y, z1 - 2, "store_register[facing=west]");
            }
        }
        lamps(x0, z0, x1, z1, ROOF - 4, 0.3);
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                set(x, ROOF - 4, z, chance() < 0.08 ? "un_slab_vct_lamp" : "un_slab_vct");
            }
        }
    }

    /** A restroom upstairs, x0..x1 against the hall edge, its door south to the concourse. */
    private void upstairsRestroom(int x0, int x1, boolean men) {
        int y = UP + 1, z0 = BACK + 1, z1 = BACK + 9;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, UP, z, "home_floor_tile");
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                for (int yy = y; yy < ROOF - 4; yy++) {
                    set(x, yy, z, wall ? "home_wall_tile_white" : "air");
                }
                set(x, ROOF - 4, z, "un_slab_vct");
            }
        }
        for (int z = z0 + 1; z < z1 - 2; z += 2) {
            set(x0 + 1, y, z, "fit_toilet_stall[facing=east]");
            set(x1 - 1, y, z, "home_sink_white[facing=west]");
            set(x1 - 1, y + 1, z, "home_mirror[facing=west]");
        }
        int dx = (x0 + x1) / 2;
        set(dx, y, z1, "air");
        set(dx, y + 1, z1, "air");
        door(dx, UP, z1, Doors.FLAT, "south");
        set(dx + 1, y + 2, z1 + 1, (men ? "tr_sign_men" : "tr_sign_ladies") + "[facing=south]");
    }

    // ------------------------------------------------------------------ pier A, the Terminal homage

    /**
     * Pier A after Modern Warfare 2's "Terminal": x PIER_X[0]..HX1, glass from the concourse's
     * floor to its roof at HROOF down both sides and across the end; the mezzanine at MZ down the
     * east side (x MZ_X0..HX1, from the concourse to MZ_Z1) with Grilltown and the Sakana sushi bar
     * on it, Hangar Coffee and Airside Books under it; the escalators and stair up to it off its
     * south edge; the gate lounges for gates 2 to 4 along the west windows; gate 1's lounge at the
     * end looking out on Gray Eagle 214; the ramp rooms on the ground floor, as pier B's.
     */
    private void homagePier() {
        int x0 = PIER_X[0], x1 = HX1, z0 = Z1, z1 = PIER_Z1, y = UP + 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0 + 1; z <= z1; z++) {
                boolean side = x == x0 || x == x1, end = z == z1;
                set(x, 0, z, side || end ? "concrete_dark" : "floor_concrete");
                for (int yy = 1; yy < HROOF; yy++) {
                    String s = "air";
                    if (side || end) {
                        String out = side ? (x == x0 ? "west" : "east") : "south";
                        boolean mull = Math.floorMod(side ? z : x, 6) == 0;
                        boolean band = yy == UP || yy == HROOF - 1 || yy == MZ && x == x1 && z <= MZ_Z1;
                        s = band ? "ml_precast_band" : yy < UP ? precast() : mull ? "wf_steel" : glass(out, 0.15);
                    }
                    set(x, yy, z, s);
                }
                if (!side && !end) {
                    set(x, UP, z, x < MZ_X0 - 6 || z > MZ_Z1 + 8 ? "un_slab_carpet" : "un_slab_terrazzo");
                }
            }
        }
        // the mouth: the concourse's south wall opened upstairs, the pier's own wall over the hall's roof
        for (int x = x0 + 1; x < x1; x++) {
            for (int yy = y; yy < ROOF; yy++) {
                set(x, yy, Z1, "air");
            }
            set(x, UP, Z1, "un_slab_terrazzo");
        }
        for (int x = x0; x <= x1; x++) {
            for (int yy = ROOF + 1; yy < HROOF; yy++) {
                set(x, yy, Z1, yy == HROOF - 1 ? "ml_precast_band" : Math.floorMod(x, 6) == 0 ? "wf_steel" : glass("north", 0.1));
            }
        }
        pierGround(x0, x1, -125, -121);
        // the gates down the west windows, the signs hung from the roof
        for (int g = 0; g < GATE_Z.length; g++) {
            gate(x0, GATE_Z[g], 2 + g, true, NO_SIGN, HROOF);
        }
        mezzanine();
        escalators(-119, MZ_Z1 + 9);
        coffee();
        bookshop();
        grilltown();
        sakana();
        stair(-105, Z1 + 1, true, new int[] {0, UP, MZ, HROOF}, "ml_precast", "un_roof");
        gateOne();
        // the lights: pendants from the roof over the open lounges, troffers under the mezzanine
        for (int x = x0 + 4; x < x1; x += 8) {
            for (int z = z0 + 4; z < z1; z += 8) {
                if (empty(x, HROOF - 1, z)) {
                    set(x, HROOF - 1, z, power && chance() < 0.3 ? "tx_pendant" : "tx_pendant_off");
                }
                if (x >= MZ_X0 && z <= MZ_Z1 && empty(x, MZ - 1, z)) {
                    set(x, MZ - 1, z, power && chance() < 0.3 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        hungSign("gates_a", (x0 + x1) / 2 - 8, MZ - 2, z0 + 4, "north");
        rods((x0 + x1) / 2 - 8, MZ - 1, z0 + 4, HROOF);
    }

    /** The mezzanine's floor, its columns and its glass rail along the open sides. */
    private void mezzanine() {
        for (int x = MZ_X0; x < HX1; x++) {
            for (int z = Z1 + 1; z <= MZ_Z1; z++) {
                set(x, MZ, z, "un_slab_terrazzo");
            }
        }
        for (int z = Z1 + 1; z <= MZ_Z1; z++) {
            set(MZ_X0, MZ + 1, z, "tx_glass_rail[axis=z]");
        }
        for (int x = MZ_X0; x < HX1; x++) {
            if (x < -119 || x > -115) {
                set(x, MZ + 1, MZ_Z1, "tx_glass_rail[axis=x]");
            }
        }
        for (int z = Z1 + 7; z <= MZ_Z1; z += 12) {
            for (int yy = UP + 1; yy < MZ; yy++) {
                set(MZ_X0, yy, z, yy == UP + 1 ? "tx_column_base" : yy == MZ - 1 ? "tx_column_cap" : "tx_column");
            }
        }
    }

    /**
     * The escalator pair (x ex, ex + 1: down, up) and the stair (ex + 3, ex + 4) rising north from
     * the lounge's floor at z0 to land on the mezzanine's south edge, the balustrade between them.
     */
    private void escalators(int ex, int z0) {
        int rise = MZ - UP;
        for (int k = 1; k <= rise; k++) {
            int z = z0 - k, yk = UP + k;
            for (int x = ex - 1; x <= ex + 5; x++) {
                for (int yy = UP + 1; yy < yk; yy++) {
                    set(x, yy, z, "stone_trim");
                }
                if (x == ex - 1 || x == ex + 2 || x == ex + 5) {
                    set(x, yk, z, "stone_trim");
                    set(x, yk + 1, z, "tx_glass_rail[axis=z]");
                    continue;
                }
                boolean esc = x <= ex + 1;
                set(x, yk, z, esc ? "zomboidcraft:escalator[facing=north,run=" + (x == ex ? "down" : "up") + "]" : "stairs_concrete[facing=north]");
                if (esc) {
                    set(x, yk + 1, z, "tx_esc_side_" + (x == ex + 1 ? "r" : "l") + "[facing=north]");
                }
                for (int yy = yk + (esc ? 2 : 1); yy <= yk + Builders.HEADROOM && yy < MZ; yy++) {
                    set(x, yy, z, "air");
                }
            }
        }
        for (int x = ex; x <= ex + 1; x++) {
            String hand = x == ex + 1 ? "r" : "l";
            set(x, UP, z0, "tx_esc_plate[facing=north]");
            set(x, UP + 1, z0, "tx_esc_newel_" + hand + "[facing=south]");
            set(x, MZ, z0 - rise - 1, "tx_esc_plate[facing=south]");
            set(x, MZ + 1, z0 - rise - 1, "tx_esc_newel_" + hand + "[facing=north]");
        }
    }

    /** Tables with their chairs every four over the box at floor y, a few gone. */
    private void cafeTables(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x += 4) {
            for (int z = z0; z <= z1; z += 4) {
                if (chance() < 0.15 || !empty(x, y + 1, z)) {
                    continue;
                }
                set(x, y + 1, z, chance() < 0.1 ? "trade_folding_chair_tipped[facing=" + dir() + "]" : "store_table");
                for (int[] c : new int[][] {{1, 0, 3}, {-1, 0, 1}, {0, 1, 2}, {0, -1, 0}}) {
                    if (chance() < 0.7 && empty(x + c[0], y + 1, z + c[1])) {
                        set(x + c[0], y + 1, z + c[1], (chance() < 0.15 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=" + DIRS[c[2]] + "]");
                    }
                }
            }
        }
    }

    /** A back-lit board of a picture three wide over a counter, the bulkhead it hangs on behind it. */
    private void boards(String prefix, int x, int y, int zc, String facing) {
        row(prefix, 3, x, y, zc - 1, facing, 0.0);
    }

    /** Hangar Coffee under the mezzanine's north end: the counter facing west, the machines behind, the boards over it, tables out front. */
    private void coffee() {
        int y = UP + 1, cx = -109, za = Z1 + 11, zb = Z1 + 19;
        for (int x = cx; x < HX1; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, "home_floor_tile");
            }
        }
        for (int z = za; z <= zb; z++) {
            set(cx, y, z, z == za + 4 ? "store_register[facing=west]" : "store_counter[facing=west]");
            set(cx + 2, y, z, (z % 3 == 0 ? "store_coffee" : z % 3 == 1 ? "home_fridge" : "store_shelf_grocery") + "[facing=west]");
            for (int yy = MZ - 3; yy < MZ; yy++) {
                set(cx, yy, z, "drywall");
            }
        }
        set(cx, y, zb, "air");
        boards("ap_menu_coffee_", cx - 1, MZ - 3, za + 4, "west");
        boards("ap_store_coffee_", cx - 1, MZ - 2, za + 4, "west");
        cafeTables(-118, Z1 + 3, -112, Z1 + 21, UP);
    }

    /**
     * Airside Books under the mezzanine, x MZ_X0..HX1, z -306..-278: glass to the lounge with two
     * ways in, doors in both ends, so the way runs through it; shelf runs down it, tables, the
     * magazines, the till by the south door.
     */
    private void bookshop() {
        int y = UP + 1, za = -306, zb = -278;
        for (int x = MZ_X0; x < HX1; x++) {
            for (int z = za + 1; z < zb; z++) {
                set(x, UP, z, "un_slab_vct");
            }
            for (int yy = y; yy < MZ; yy++) {
                set(x, yy, za, "drywall");
                set(x, yy, zb, "drywall");
            }
        }
        for (int z = za + 1; z < zb; z++) {
            for (int yy = y; yy < MZ; yy++) {
                boolean way = (z >= za + 5 && z <= za + 7 || z >= zb - 7 && z <= zb - 5) && yy <= y + 3;
                if (get(MZ_X0, yy, z) != null && get(MZ_X0, yy, z).startsWith("tx_column")) {
                    continue;
                }
                set(MZ_X0, yy, z, way ? "air" : yy <= y + 3 ? "tx_glass[axis=z]" : "wf_panel");
            }
        }
        for (int x = -112; x <= -110; x++) {
            for (int yy = y; yy <= y + 2; yy++) {
                set(x, yy, za, "air");
                set(x, yy, zb, "air");
            }
        }
        row("ap_store_books_", 3, MZ_X0 - 1, MZ - 3, (za + zb) / 2 - 1, "west", 0.0);
        // the shelf runs: two lengths each with the cross aisle between, the end caps
        for (int gx : new int[] {-117, -113, -108}) {
            for (int[] run : new int[][] {{za + 3, za + 10}, {zb - 12, zb - 4}}) {
                for (int z = run[0]; z <= run[1]; z++) {
                    set(gx, y, z, chance() < 0.08 ? "home_books_stack_low" : "ap_gondola[axis=z]");
                    set(gx, y + 1, z, "ap_gondola_top[axis=z]");
                }
                set(gx, y, run[0] - 1, "ap_gondola_end[facing=north]");
                set(gx, y, run[1] + 1, "ap_gondola_end[facing=south]");
            }
        }
        for (int z = za + 2; z <= zb - 2; z++) {
            set(HX1 - 2, y, z, "home_bookcase[facing=west]");
            set(HX1 - 2, y + 1, z, "home_bookcase_top[facing=west]");
        }
        set(-110, y, za + 8, "ap_book_table[facing=north]");
        set(-110, y, zb - 8, "ap_book_table[facing=north]");
        set(-119, y, za + 13, "ap_book_table[facing=north]");
        for (int x = MZ_X0 + 1; x <= MZ_X0 + 5; x++) {
            set(x, y, zb - 1, "ap_mag_rack[facing=north]");
        }
        for (int z = zb - 6; z <= zb - 3; z++) {
            set(-105, y, z, z == zb - 4 ? "store_register[facing=west]" : "store_counter[facing=west]");
        }
        for (int x = MZ_X0 + 2; x < HX1 - 1; x += 4) {
            for (int z = za + 3; z < zb; z += 4) {
                if (empty(x, MZ - 1, z)) {
                    set(x, MZ - 1, z, power && chance() < 0.3 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
    }

    /** Grilltown up on the mezzanine against the east windows: the kitchen, the counter facing west, the boards over it, the tables. */
    private void grilltown() {
        int y = MZ + 1, za = -318, zb = -302, cx = -107;
        String[] kit = {"store_grill", "dn_fryer", "store_oven", "home_fridge", "st_fountain", "dn_fryer"};
        for (int x = cx; x < HX1; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, MZ, z, "home_floor_tile");
            }
            for (int yy = y; yy < y + 5; yy++) {
                set(x, yy, za - 1, "home_wall_tile_white");
                set(x, yy, zb + 1, "home_wall_tile_white");
            }
        }
        for (int z = za; z <= zb; z++) {
            for (int yy = y; yy < y + 5; yy++) {
                set(HX1 - 1, yy, z, "home_wall_tile_white");
            }
            set(cx, y, z, z == za + 5 || z == zb - 5 ? "store_register[facing=west]" : "store_counter[facing=west]");
            set(HX1 - 2, y, z, kit[Math.floorMod(z, kit.length)] + "[facing=west]");
            set(cx + 2, y, z, z % 4 == 0 ? "store_counter[facing=east]" : "air");
            for (int yy = y + 3; yy < HROOF - 1; yy++) {
                set(cx, yy, z, "drywall");
            }
        }
        set(cx, y, zb, "air");
        set(cx, y + 1, zb, "air");
        boards("ap_menu_grilltown_", cx - 1, y + 3, za + 5, "west");
        boards("ap_menu_grilltown_", cx - 1, y + 3, zb - 5, "west");
        row("ap_store_grilltown_", 3, cx - 1, y + 5, (za + zb) / 2 - 1, "west", 0.0);
        set(cx - 1, y, za - 2, "ap_tray_rack[facing=west]");
        set(cx - 1, y, zb + 2, "home_trash_can");
        cafeTables(-119, Z1 + 3, -111, za - 4, MZ);
        cafeTables(-119, za, -111, zb, MZ);
        cafeTables(-119, -282, -105, MZ_Z1 - 2, MZ);
    }

    /** The Sakana sushi bar: an island on the mezzanine, the belt round it with stools outside, the chefs' cases inside. */
    private void sakana() {
        int y = MZ + 1, xa = -116, xb = -110, za = -299, zb = -285;
        for (int z = za + 1; z < zb; z++) {
            set(xa, y, z, "ap_sushi_belt[axis=z]");
            set(xb, y, z, "ap_sushi_belt[axis=z]");
            set(xa - 1, y, z, chance() < 0.15 ? "dn_stool_torn" : "dn_stool_b");
            set(xb + 1, y, z, chance() < 0.15 ? "dn_stool_torn" : "dn_stool_b");
            if (z % 3 == 0) {
                set(xa + 1, y, z, "ap_sushi_case[facing=west]");
                set(xb - 1, y, z, "ap_sushi_case[facing=east]");
            }
        }
        for (int x = xa; x <= xb; x++) {
            set(x, y, za, "ap_sushi_belt[axis=x]");
            set(x, y, zb, x == (xa + xb) / 2 ? "air" : "ap_sushi_belt[axis=x]");
            set(x, y, za - 1, chance() < 0.15 ? "dn_stool_torn" : "dn_stool_b");
            for (int z = za + 1; z < zb; z++) {
                set(x, MZ, z, x > xa && x < xb ? "home_floor_tile" : get(x, MZ, z));
            }
        }
        // the name and the menu hung over each end of the island
        for (int[] e : new int[][] {{za, 0}, {zb, 1}}) {
            String f = e[1] == 0 ? "north" : "south";
            row("ap_menu_sakana_", 3, xa + 2, y + 3, e[0], f, 0.0);
            row("ap_store_sakana_", 3, xa + 2, y + 4, e[0], f, 0.0);
            for (int x = xa + 2; x <= xa + 4; x++) {
                rods(x, y + 5, e[0], HROOF);
            }
        }
    }

    // ---- gate 1 and its plane

    /** Gate 1 at the pier's end: its lounge, Gray Eagle 214 nose in outside, the jet bridge, the stair truck, the gear. */
    private void gateOne() {
        int y = UP + 1, z1 = PIER_Z1;
        // the lounge: rows facing the end windows and the plane, the podium by the bridge's door
        for (int z = z1 - 4; z >= z1 - 16; z -= 3) {
            for (int x = -123; x <= -104; x++) {
                if (x == -114 || !empty(x, y, z)) {
                    continue;
                }
                double r = chance();
                if (r > 0.04) {
                    set(x, y, z, (r < 0.12 ? "ap_seats_bag" : r < 0.2 ? "ap_seats_torn" : "ap_seats") + "[facing=south]");
                }
            }
        }
        set(-130, y, z1 - 2, "ap_gate_desk[facing=east]");
        set(-127, y + 4, z1 - 1, "ap_gate_1[facing=north]");
        rods(-127, y + 5, z1 - 1, HROOF);
        boolean dead = !power || chance() < 0.5;
        set(-118, y, z1 - 20, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=south]");
        set(-118, y + 1, z1 - 20, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=south]");
        Airliner.place(p, PLANE_X, 0, PLANE_Z);
        jetBridge();
        stairTruck(PLANE_X + Airliner.WEST - 1, PLANE_Z + Airliner.L2);
        rampGear();
    }

    /** True inside the jet bridge: down from the pier's end at x -128..-126, then east to the plane's front door. */
    private static boolean inBridge(int x, int z) {
        int door = PLANE_Z + Airliner.L1, ex = PLANE_X + Airliner.WEST - 1;
        return x >= -128 && x <= -126 && z > PIER_Z1 && z <= door + 2
                || x > -126 && x <= ex && z >= door && z <= door + 2;
    }

    /**
     * The jet bridge from the pier's end to Gray Eagle 214's front door: the walk on its floor at
     * UP, the sides with their window band, the roof with its lights, the rotunda's column by the
     * pier, the drive leg and its wheels under the cab, the canopy against the fuselage.
     */
    private void jetBridge() {
        int y = UP, ex = PLANE_X + Airliner.WEST - 1;
        String[] faces = {"north", "east", "south", "west"};
        int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        // the door through the pier's end
        for (int x = -128; x <= -126; x++) {
            for (int yy = y + 1; yy <= y + 4; yy++) {
                set(x, yy, PIER_Z1, "air");
            }
        }
        for (int x = -130; x <= ex + 1; x++) {
            for (int z = PIER_Z1 + 1; z <= PLANE_Z + Airliner.L1 + 3; z++) {
                if (inBridge(x, z)) {
                    boolean ns = x <= -126;
                    set(x, y, z, "rp_jb_floor[facing=" + (ns ? "south" : "east") + "]");
                    for (int yy = y + 1; yy <= y + 4; yy++) {
                        set(x, yy, z, "air");
                    }
                    boolean lamp = (ns ? z : x) % 3 == 0;
                    set(x, y + 5, z, x == ex ? "rp_jb_bellows_top[facing=east]"
                            : lamp ? "rp_jb_roof_lamp" + (power && chance() < 0.4 ? "_lit" : "") + "[facing=east]" : "rp_jb_roof[facing=east]");
                    continue;
                }
                if (x > ex) {
                    continue;
                }
                // a side: which ways the inside lies
                int sides = 0, n = 0;
                for (int d = 0; d < 4; d++) {
                    if (inBridge(x - step[d][0], z - step[d][1])) {
                        sides |= 1 << d;
                        n++;
                    }
                }
                String wall = null;
                int f = -1;
                if (n == 1) {
                    f = Integer.numberOfTrailingZeros(sides);
                    wall = "wall";
                } else if (n == 2) {
                    // an inside corner: the sides face away from both; the corner piece's two are f and the one anticlockwise of it
                    for (int d = 0; d < 4; d++) {
                        if ((sides >> d & 1) == 1 && (sides >> ((d + 3) % 4) & 1) == 1) {
                            f = d;
                        }
                    }
                    wall = "corner";
                } else {
                    for (int d = 0; d < 4; d++) {
                        int dd = (d + 3) % 4;
                        if (inBridge(x - step[d][0] - step[dd][0], z - step[d][1] - step[dd][1])) {
                            f = d;
                            wall = "corner";
                        }
                    }
                }
                if (wall == null || !empty(x, y + 1, z)) {
                    continue;
                }
                set(x, y, z, "rp_jb_floor[facing=east]");
                for (int yy = y + 1; yy <= y + 4; yy++) {
                    String piece = wall.equals("corner") ? "rp_jb_corner" : yy == y + 2 ? "rp_jb_window" : yy == y + 3 ? "rp_jb_wall_band" : "rp_jb_wall";
                    set(x, yy, z, piece + "[facing=" + faces[f] + "]");
                }
                set(x, y + 5, z, "rp_jb_eave[facing=" + faces[f] + "]");
            }
        }
        set(ex, y + 1, PLANE_Z + Airliner.L1 + 2, "rp_jb_console[facing=east]");
        // the rotunda's column by the pier, the drive leg under the cab
        for (int yy = 1; yy < y; yy++) {
            set(-127, yy, PIER_Z1 + 2, "rp_jb_column[facing=north]");
        }
        int lx = ex - 2;
        for (int z : new int[] {PLANE_Z + Airliner.L1, PLANE_Z + Airliner.L1 + 2}) {
            set(lx, 1, z, "rp_jb_bogie[facing=east]");
            for (int yy = 2; yy < y; yy++) {
                set(lx, yy, z, yy < 4 ? "rp_jb_leg_lo[facing=north]" : "rp_jb_leg[facing=north]");
            }
        }
    }

    /**
     * The stair truck at the plane's back door, its platform in the cell x by the door (two wide,
     * z and z + 1), the stairs coming down to the west, the cab under the platform.
     */
    private void stairTruck(int x, int z) {
        for (int dz = 0; dz <= 1; dz++) {
            int zz = z + dz;
            set(x, UP, zz, "rp_as_top[facing=east]");
            set(x, UP + 1, zz, "rp_as_rail[facing=east]");
            for (int yy = UP + 2; yy <= UP + 4; yy++) {
                set(x, yy, zz, "rp_as_post[facing=east]");
            }
            set(x, UP + 5, zz, "rp_as_roof[facing=east]");
            set(x, 1, zz, "rp_as_cab_lo[facing=east]");
            set(x, 2, zz, "rp_as_cab_hi[facing=east]");
            for (int yy = 3; yy < UP; yy++) {
                set(x, yy, zz, "rp_as_lift[facing=east]");
            }
            for (int k = 1; k < UP; k++) {
                int sx = x - k, sy = UP - k;
                set(sx, sy, zz, "rp_as_step[facing=east]");
                for (int yy = 1; yy < sy; yy++) {
                    set(sx, yy, zz, yy == 1 ? (k == 2 ? "rp_as_bed_wheels[facing=east]" : "rp_as_bed[facing=east]") : "rp_as_lift[facing=east]");
                }
            }
        }
    }

    /** The ground crew's gear about the plane: the belt loader at the front hold, the baggage train, containers, the power cart, chocks and cones. */
    private void rampGear() {
        int ex = PLANE_X - Airliner.WEST + 1, hz = PLANE_Z + 15;
        // the belt loader up to the front hold on the right side, rising west
        set(ex + 3, 1, hz, "rp_bl_0[facing=west]");
        set(ex + 2, 1, hz, "rp_bl_1[facing=west]");
        set(ex + 1, 2, hz, "rp_bl_2[facing=west]");
        set(ex, 2, hz, "rp_bl_3[facing=west]");
        // the train: the tug and three carts, stopped under the wing's tip
        int tx = PLANE_X + 13, tz = PLANE_Z + 22;
        set(tx, 1, tz, "rp_tug_front[facing=north]");
        set(tx, 1, tz + 1, "rp_tug_rear[facing=north]");
        for (int k = 0; k < 3; k++) {
            boolean bags = chance() < 0.7;
            set(tx, 1, tz + 3 + k * 3, "rp_dolly_front" + (bags ? "_bags" : "") + "[facing=north]");
            set(tx, 1, tz + 4 + k * 3, "rp_dolly_rear" + (bags ? "_bags" : "") + "[facing=north]");
        }
        set(tx + 3, 1, tz - 4, "rp_dolly_tipped[facing=" + dir() + "]");
        set(tx + 2, 1, tz - 6, "ap_bag_open[facing=" + dir() + "]");
        set(tx + 4, 1, tz - 3, "ap_bag_flat[facing=" + dir() + "]");
        set(tx + 1, 1, tz - 2, "ap_bag_up_b[facing=" + dir() + "]");
        // containers by the loader, one burst
        set(ex + 6, 1, hz - 2, "rp_uld[facing=west]");
        set(ex + 7, 1, hz - 2, "rp_uld[facing=west]");
        set(ex + 6, 1, hz + 2, "rp_uld_open[facing=west]");
        set(ex + 8, 1, hz + 1, "rp_uld_spill[facing=south]");
        // the power cart at the nose, the chocks at the wheels, cones round the wingtips and tail
        set(PLANE_X + 3, 1, PLANE_Z + 3, "rp_gpu[facing=west]");
        set(PLANE_X, 1, PLANE_Z + 8, "rp_chocks[facing=north]");
        set(PLANE_X - 5, 1, PLANE_Z + 31, "rp_chocks[facing=north]");
        set(PLANE_X + 4, 1, PLANE_Z + 31, "rp_chocks[facing=north]");
        set(PLANE_X - 7, 1, PLANE_Z + 4, "rp_wand[facing=" + dir() + "]");
        for (int[] c : new int[][] {{-28, 31}, {28, 31}, {0, 58}, {-2, -2}, {2, -2}}) {
            if (empty(PLANE_X + c[0], 1, PLANE_Z + c[1])) {
                set(PLANE_X + c[0], 1, PLANE_Z + c[1], "st_cone");
            }
        }
    }

    // ------------------------------------------------------------------ the piers

    /**
     * A pier from the concourse's south wall to PIER_Z1, x px..px + PIER_W: the ramp rooms on the
     * ground, the gate lounges upstairs either side of the corridor, three gates a side numbered
     * from first (west side odd, east side even, from the concourse out), the end lounge, a fire stair.
     */
    private void pier(int px, int first) {
        int x0 = px, x1 = px + PIER_W, z0 = Z1, z1 = PIER_Z1;
        int y = UP + 1, cx0 = px + 10, cx1 = px + 14;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0 + 1; z <= z1; z++) {
                boolean side = x == x0 || x == x1, end = z == z1;
                set(x, 0, z, side || end ? "concrete_dark" : "floor_concrete");
                for (int yy = 1; yy < PROOF; yy++) {
                    String s;
                    if (side || end) {
                        String out = side ? (x == x0 ? "west" : "east") : "south";
                        boolean mull = Math.floorMod(side ? z : x, 6) == 0;
                        s = yy == UP || yy == PROOF - 1 ? "ml_precast_band" : yy < UP ? precast()
                                : mull ? "wf_steel" : glass(out, 0.15);
                    } else {
                        s = "air";
                    }
                    set(x, yy, z, s);
                }
                if (!side && !end) {
                    set(x, UP, z, x >= cx0 && x <= cx1 ? "un_slab_terrazzo" : "un_slab_carpet");
                }
            }
        }
        // the mouth: the concourse's south wall opened the pier's width upstairs
        for (int x = x0 + 1; x < x1; x++) {
            for (int yy = y; yy < PROOF; yy++) {
                set(x, yy, Z1, "air");
            }
            set(x, UP, Z1, "un_slab_terrazzo");
        }
        pierGround(x0, x1, cx0, cx1);
        // the gates: doors in the side walls for the jet bridges, podiums, numbers, seats facing out
        for (int g = 0; g < GATE_Z.length; g++) {
            int gz = GATE_Z[g];
            gate(x0, gz, first + g * 2, true, cx0);
            gate(x1, gz, first + g * 2 + 1, false, cx1);
        }
        // the end lounge: seats across, facing the end windows
        for (int z = z1 - 4; z >= z1 - 10; z -= 3) {
            for (int x = x0 + 2; x < x1 - 1; x++) {
                if (x >= cx0 && x <= cx1 && z == z1 - 4) {
                    continue;
                }
                double r = chance();
                set(x, y, z, (r < 0.1 ? "ap_seats_bag" : r < 0.18 ? "ap_seats_torn" : "ap_seats") + "[facing=south]");
            }
        }
        // the restroom between the first two gates on the east side, the fire stair by the third west
        int rz = (GATE_Z[0] + GATE_Z[1]) / 2;
        for (int x = cx1 + 1; x < x1; x++) {
            for (int z = rz - 4; z <= rz + 4; z++) {
                boolean wall = x == cx1 + 1 || z == rz - 4 || z == rz + 4;
                set(x, UP, z, "home_floor_tile");
                for (int yy = y; yy < PROOF; yy++) {
                    set(x, yy, z, wall ? "home_wall_tile_white" : "air");
                }
            }
        }
        for (int z = rz - 3; z <= rz + 3; z += 2) {
            set(x1 - 1, y, z, "fit_toilet_stall[facing=west]");
        }
        set(cx1 + 2, y, rz - 2, "home_sink_white[facing=east]");
        set(cx1 + 1, y, rz, "air");
        set(cx1 + 1, y + 1, rz, "air");
        door(cx1 + 1, UP, rz, Doors.FLAT, "west");
        int sz = (GATE_Z[1] + GATE_Z[2]) / 2 - 4;
        stair(x0 + 1, sz, false, new int[] {0, UP, PROOF}, "ml_precast", "un_roof");
        // the corridor's signs: the gate numbers this way
        hungSign(first == 1 ? "gates_a" : "gates_b", (cx0 + cx1) / 2, PROOF - 3, z0 + 6, "north");
    }

    /** A pier's ground floor: a corridor down the middle x cx0..cx1, rooms off it, doors out to the ramp. */
    private void pierGround(int x0, int x1, int cx0, int cx1) {
        int z0 = Z1, z1 = PIER_Z1;
        for (int z = z0 + 1; z < z1; z++) {
            wallZ(cx0 - 1, z, z, 0, UP - 1, "tx_wall");
            wallZ(cx1 + 1, z, z, 0, UP - 1, "tx_wall");
        }
        for (int z = z0 + 20; z < z1; z += 22) {
            wallX(x0 + 1, cx0 - 2, z, 0, UP - 1, "tx_wall");
            wallX(cx1 + 2, x1 - 1, z, 0, UP - 1, "tx_wall");
            rampRoom(x0 + 1, z - 21, cx0 - 2, z - 1, true);
            rampRoom(cx1 + 2, z - 21, x1 - 1, z - 1, false);
        }
        for (int z = z0 + 10; z < z1; z += 22) {
            for (int x : new int[] {cx0 - 1, cx1 + 1}) {
                set(x, 1, z, "air");
                set(x, 2, z, "air");
                door(x, 0, z, Doors.FLAT, x == cx0 - 1 ? "west" : "east");
            }
        }
        for (int x = cx0; x <= cx1; x += 2) {
            set(x, 1, z1, "air");
            set(x, 2, z1, "air");
        }
        Doors.pair(p, rng, cx0 + 1, 1, z1, Doors.STEEL, "south");
        lamps(x0, z0, x1, z1, UP, 0.25);
    }

    /**
     * A gate at gz on the side wall at x (west when west): the jet bridge's door, the podium by it,
     * the number hung over the lounge, rows of seats facing the window, a screen.
     */
    private void gate(int x, int gz, int n, boolean west, int corridor) {
        gate(x, gz, n, west, corridor, PROOF);
    }

    /** As above, under a roof (or ceiling) at top: the signs hung from it on rods; no corridor sign when corridor is NO_SIGN. */
    private void gate(int x, int gz, int n, boolean west, int corridor, int top) {
        int y = UP + 1, in = west ? 1 : -1;
        String out = west ? "west" : "east", back = west ? "east" : "west";
        // the door: three wide, four high, a steel frame; the bridge meets it outside
        for (int z = gz - 1; z <= gz + 1; z++) {
            for (int yy = y; yy < y + 4; yy++) {
                set(x, yy, z, "air");
            }
            set(x, y + 4, z, "wf_steel");
        }
        set(x, y, gz - 2, "wf_steel");
        set(x, y, gz + 2, "wf_steel");
        set(x + in * 2, y, gz + 3, "ap_gate_desk[facing=" + back + "]");
        set(x + in * 2, y + 4, gz, "ap_gate_" + n + "[facing=" + back + "]");
        rods(x + in * 2, y + 5, gz, top);
        if (corridor != NO_SIGN) {
            set(corridor, top - 2, gz, "ap_gate_" + n + "[facing=north]");
        }
        // the rows: back to back pairs from the window in, along z, gaps for the walk to the door
        for (int r = 0; r < 2; r++) {
            int rx = x + in * (3 + r * 3);
            for (int z = gz - 15; z <= gz + 15; z++) {
                if (Math.abs(z - gz) <= 4 || Math.abs(z - gz) == 10) {
                    continue;
                }
                for (int k = 0; k < 2; k++) {
                    double rr = chance();
                    String f = k == 0 ? out : back;
                    if (rr < 0.04) {
                        continue;
                    }
                    set(rx + in * k, y, z, (rr < 0.12 ? "ap_seats_bag" : rr < 0.2 ? "ap_seats_torn" : "ap_seats") + "[facing=" + f + "]");
                }
            }
        }
        boolean dead = !power || chance() < 0.6;
        set(x + in * 2, y, gz - 4, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=" + back + "]");
        set(x + in * 2, y + 1, gz - 4, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=" + back + "]");
    }

    /** A ramp room under a pier: the crew's lockers along the wall, a table, the radio and the boots. */
    private void rampRoom(int x0, int z0, int x1, int z1, boolean west) {
        int wx = west ? x0 : x1;
        for (int z = z0 + 1; z < z1; z += 2) {
            set(wx, 1, z, "fit_locker_lo[facing=" + (west ? "east" : "west") + "]");
            set(wx, 2, z, "fit_locker_hi[facing=" + (west ? "east" : "west") + "]");
        }
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        set(mx, 1, mz, "fit_mess_table[axis=z]");
        set(mx - 1, 1, mz, "store_chair[facing=east]");
        set(mx + 1, 1, mz + 1, chance() < 0.3 ? "trade_folding_chair_tipped[facing=west]" : "store_chair[facing=west]");
        // a door out to the apron in the outer wall
        int ox = west ? x0 - 1 : x1 + 1;
        set(ox, 1, mz + 4, "air");
        set(ox, 2, mz + 4, "air");
        door(ox, 0, mz + 4, Doors.STEEL, west ? "west" : "east");
    }

    /** The rods a hung sign or lamp hangs on, from y up to the ceiling at top. */
    private void rods(int x, int y, int z, int top) {
        for (int yy = y; yy < top; yy++) {
            if (empty(x, yy, z)) {
                set(x, yy, z, "tx_rod");
            }
        }
    }

    // ------------------------------------------------------------------ lights, roof, the end of it

    /** Pendants down from the halls' roof, troffers in the ceilings under the upper floor. */
    private void lights() {
        for (int x = X0 + 4; x < X1; x += 8) {
            for (int z = Z0 + 4; z < Z1; z += 8) {
                boolean hall = z < BACK && Math.abs(x) > MEZ;
                int yl = hall || z >= BACK || Math.abs(x) <= MEZ ? ROOF - 1 : UP - 1;
                if (empty(x, yl, z)) {
                    set(x, yl, z, power && chance() < 0.3 ? "tx_pendant" : "tx_pendant_off");
                }
                if (!hall && empty(x, UP - 1, z)) {
                    set(x, UP - 1, z, power && chance() < 0.3 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        for (int px : new int[] {PIER_X[1]}) {
            for (int z = Z1 + 4; z < PIER_Z1; z += 6) {
                for (int x = px + 4; x < px + PIER_W; x += 8) {
                    if (empty(x, PROOF - 1, z)) {
                        set(x, PROOF - 1, z, power && chance() < 0.25 ? "ceiling_light" : "ceiling_light_off");
                    }
                }
            }
        }
    }

    private void roof() {
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                set(x, ROOF, z, "un_roof");
            }
        }
        b.flat(X0, Z0, X1, Z1, ROOF, "ml_precast", 1, "ml_precast");
        for (int px : PIER_X) {
            int top = px == PIER_X[0] ? HROOF : PROOF;
            for (int x = px; x <= px + pierW(px); x++) {
                for (int z = Z1 + 1; z <= PIER_Z1; z++) {
                    set(x, top, z, "un_roof");
                }
            }
            b.flat(px, Z1 + 1, px + pierW(px), PIER_Z1, top, "ml_precast", 1, "ml_precast");
        }
    }

    /**
     * What the evacuation left: luggage everywhere it was dropped, the mess on the floors, the
     * glass from the broken panes, the dead where the line broke.
     */
    private void aftermath() {
        scatter(X0 + 1, Z0 + 1, X1 - 1, BACK - 1, 0, 0.012);
        scatter(X0 + 1, BACK + 1, X1 - 1, Z1 - 1, UP, 0.008);
        for (int px : PIER_X) {
            scatter(px + 1, Z1 + 1, px + pierW(px) - 1, PIER_Z1 - 1, UP, 0.01);
        }
        scatter(MZ_X0, Z1 + 1, HX1 - 1, MZ_Z1, MZ, 0.01);
        mess(PIER_X[0] + 1, Z1 + 1, HX1 - 1, PIER_Z1 - 1, UP, 0.015);
        mess(MZ_X0, Z1 + 1, HX1 - 1, MZ_Z1, MZ, 0.015);
        mess(X0 + 1, Z0 + 1, X1 - 1, BACK - 1, 0, 0.02);
        mess(-MEZ, Z0 + 1, MEZ, BACK - 1, UP, 0.03);
        mess(X0 + 1, BACK + 1, X1 - 1, Z1 - 1, UP, 0.015);
        // the dead at the security line
        for (int k = 0; k < 6; k++) {
            int x = rng.nextInt(60) - 30, z = BACK - 1 - rng.nextInt(6);
            if (empty(x, UP + 1, z) && empty(x + 1, UP + 1, z)) {
                set(x, UP + 1, z, "home_corpse_head[facing=west]");
                set(x + 1, UP + 1, z, "home_corpse_foot[facing=west]");
                set(x, UP + 1, z - 1, empty(x, UP + 1, z - 1) ? "blood_splat" : get(x, UP + 1, z - 1));
            }
        }
    }

    /** Luggage dropped about the floor at y over the box, density per cell. */
    private void scatter(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (chance() < density && empty(x, y + 1, z)) {
                    String s = get(x, y, z);
                    if (s != null && !s.equals("air")) {
                        set(x, y + 1, z, chance() < 0.15 ? "ap_cart[facing=" + dir() + "]" : bag());
                    }
                }
            }
        }
    }
}
