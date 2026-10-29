package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The airside of Grayhaven International after Modern Warfare 2's "Terminal" (Josiah, 2026-10-29:
 * the screenshots and the map's plan, 22:54 "You can prioritise building this now"; the layout and
 * the feel, our own art and names), south of the main hall's south wall ({@link AirportTerminal#Z1})
 * in the airport's frame, its main floor level with the concourse's ({@link AirportTerminal#UP}).
 *
 * <ul>
 * <li>The west block, x {@link #X0}..{@link #XH}, z Z1..{@link #ZS}: down its west side the lounge
 * behind the glass, the security hall with its lanes, the security office, the restrooms and the
 * stair; up the middle the food court on its checkered floor with Grilltown's corner kiosk, the
 * waiting area with the wood bar and the big departures board, the atrium under its round glass
 * lantern with the fir in its granite bed, the security lobby with the reservations desk, check-in,
 * Airside Books to walk through, the Bag &amp; Go luggage shop; down its east side the hallway along
 * the tarmac windows with its stair down, and gate 1's room at the south end ("TO PLANE").</li>
 * <li>The lobby, x XH..{@link #XE}, z Z1..{@link #ZL}: two storeys, the escalators and stair up
 * the middle, the upper lobby looking out over the tarmac, the baggage hall with its carousels.</li>
 * <li>The tarmac south of the lobby: the control-room building with its outside stair, its roller
 * door, the tank and the windsock on its roof; the cargo containers in stacks, the painted gate
 * number, the police line along its east edge with the cruisers inside it.</li>
 * <li>Gray Eagle 214 alongside it, nose west to the building (Josiah 23:12, the map's "PLANE" inset
 * slotted in so its bridge meets the building): the jet bridge from gate 1's room to the front
 * door, the slide down from the back door on the right onto the tarmac, the ramp's gear about it.</li>
 * </ul>
 *
 * <p>Under the main floor, the bag hall and the stores, doors out to the tarmac.
 */
final class TerminalHomage extends Grounds {
    /** The main hall's south wall, the floor, the west block's roof. */
    static final int Z1 = AirportTerminal.Z1, UP = AirportTerminal.UP, ROOF = AirportTerminal.ROOF;
    /** The west block's west wall, its east wall (the hallway's windows), its south wall. */
    static final int X0 = -140, XH = -41, ZS = -236;
    /** The lobby's east wall and south wall (the tarmac's north edge), its upper floor and roof. */
    static final int XE = 31, ZL = -299, LUP = 12, LROOF = 18;
    /** The lobby's upper floor runs east to here; the baggage hall starts at XB. */
    static final int LX1 = -12, XB = 17;
    /** The atrium's middle and its light well's radius. */
    static final int AX = -62, AZ = -290, AR = 10;
    /** Gray Eagle 214: its nose's x and its middle's z (nose west, the doors used south, R2 north). */
    static final int NX = -38, PZ = -250;
    /** Everything this builds lies within these (in the frame). */
    static final int BX0 = -150, BX1 = 50, BZ0 = Z1, BZ1 = -215;

    TerminalHomage(Placer p, SplittableRandom rng) {
        super(p, rng, XE - X0 + 1, BZ1 - Z1 + 1, 0.35, 0.0, 0.35);
    }

    void build() {
        shell();
        westLounge();
        security();
        restrooms();
        foodCourt();
        waitingArea();
        atrium();
        securityLobby();
        checkIn();
        bookshop();
        luggage();
        hallway();
        gateOne();
        lobby();
        baggage();
        under();
        tarmac();
        controlRoom();
        plane();
        columns();
        lights();
        roof();
        aftermath();
    }

    // ------------------------------------------------------------------ helpers

    private String precast() {
        return chance() < 0.25 ? "ml_precast_b" : "ml_precast";
    }

    private String glass(String facing, double broken) {
        double r = chance();
        return (r < broken ? "wf_glass_broken" : power && r < broken + 0.12 ? "wf_glass_lit" : "wf_glass") + "[facing=" + facing + "]";
    }

    private String bag() {
        double r = chance();
        return (r < 0.2 ? "ap_bag_up" : r < 0.35 ? "ap_bag_up_b" : r < 0.55 ? "ap_bag_flat" : r < 0.7 ? "ap_bag_flat_b"
                : r < 0.85 ? "ap_bag_pile" : "ap_bag_open") + "[facing=" + dir() + "]";
    }

    private String seat() {
        double r = chance();
        return r < 0.1 ? "ap_seats_bag" : r < 0.18 ? "ap_seats_torn" : "ap_seats";
    }

    /** The rods a hung sign or lamp hangs on, from y up to the ceiling at top. */
    private void rods(int x, int y, int z, int top) {
        for (int yy = y; yy < top; yy++) {
            if (empty(x, yy, z)) {
                set(x, yy, z, "tx_rod");
            }
        }
    }

    /** A wayfinding sign three wide hung at (x, y, z) facing that way, on rods to top. */
    private void hungSign(String key, int x, int y, int z, String facing, int top) {
        boolean ns = facing.equals("north") || facing.equals("south");
        for (int i = 0; i < 3; i++) {
            int off = 1 - i;
            int sx = ns ? x + (facing.equals("north") ? off : -off) : x, sz = ns ? z : z + (facing.equals("east") ? -off : off);
            set(sx, y, sz, "ap_way_" + key + "_" + i + "[facing=" + facing + "]");
            if (i != 1) {
                rods(sx, y + 1, sz, top);
            }
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

    /** A row of gate seating along x at z from x0 to x1, facing that way, gaps every gap. */
    private void seatsX(int x0, int x1, int z, String facing, int gap) {
        for (int x = x0; x <= x1; x++) {
            if (Math.floorMod(x - x0, gap) == gap - 1 || !empty(x, UP + 1, z) || chance() < 0.04) {
                continue;
            }
            set(x, UP + 1, z, seat() + "[facing=" + facing + "]");
        }
    }

    private void seatsZ(int x, int z0, int z1, String facing, int gap) {
        for (int z = z0; z <= z1; z++) {
            if (Math.floorMod(z - z0, gap) == gap - 1 || !empty(x, UP + 1, z) || chance() < 0.04) {
                continue;
            }
            set(x, UP + 1, z, seat() + "[facing=" + facing + "]");
        }
    }

    /** A partition along x on the main floor up to the roof's underside, x0..x1 at z. */
    private void wallAlongX(int x0, int x1, int z, String block, int top) {
        fill(x0, UP + 1, z, x1, top, z, block);
    }

    private void wallAlongZ(int x, int z0, int z1, String block, int top) {
        fill(x, UP + 1, z0, x, top, z1, block);
    }

    /** A shop front of glass along x: the panes to y + 3, steel every four, a doorway three wide at dx..dx + 2, the fascia over it. */
    private void frontX(int x0, int x1, int z, int dx) {
        for (int x = x0; x <= x1; x++) {
            boolean way = x >= dx && x <= dx + 2;
            for (int yy = UP + 1; yy <= UP + 3; yy++) {
                set(x, yy, z, way ? "air" : Math.floorMod(x - x0, 4) == 0 ? "wf_steel" : "tx_glass[axis=x]");
            }
            set(x, UP + 4, z, "wf_panel");
        }
    }

    private void frontZ(int x, int z0, int z1, int dz) {
        for (int z = z0; z <= z1; z++) {
            boolean way = z >= dz && z <= dz + 2;
            for (int yy = UP + 1; yy <= UP + 3; yy++) {
                set(x, yy, z, way ? "air" : Math.floorMod(z - z0, 4) == 0 ? "wf_steel" : "tx_glass[axis=z]");
            }
            set(x, UP + 4, z, "wf_panel");
        }
    }

    /** A potted palm on the main floor at (x, z): the granite planter, the trunk, the crown. */
    private void palm(int x, int z, int h) {
        if (!empty(x, UP + 1, z)) {
            return;
        }
        set(x, UP + 1, z, "om_planter");
        for (int y = UP + 2; y < UP + 2 + h; y++) {
            set(x, y, z, "mu_palm_trunk");
        }
        set(x, UP + 2 + h, z, chance() < 0.85 ? "mu_palm_crown" : "air");
    }

    // ------------------------------------------------------------------ the shell

    private static boolean inWest(int x, int z) {
        return x >= X0 && x <= XH && z >= Z1 && z <= ZS;
    }

    private static boolean inLobby(int x, int z) {
        return x >= XH && x <= XE && z >= Z1 && z <= ZL;
    }

    /** Floors, the outer walls, the slab, the openings from the concourse. */
    private void shell() {
        for (int x = X0; x <= XE; x++) {
            for (int z = Z1; z <= ZS; z++) {
                boolean west = inWest(x, z), lob = inLobby(x, z);
                if (!west && !lob) {
                    continue;
                }
                int top = lob && x > XH ? LROOF : ROOF;
                boolean edge = x == X0 || z == ZS && west || x == XE || z == ZL && x > XH || x == XH && z >= ZL;
                set(x, 0, z, edge ? "concrete_dark" : "floor_concrete");
                for (int y = 1; y < top; y++) {
                    set(x, y, z, edge ? outer(x, y, z) : "air");
                }
                if (!edge && z > Z1) {
                    set(x, UP, z, "un_slab_terrazzo");
                }
            }
        }
        // the concourse's south wall opened from end to end over the floor, a post every twelve
        for (int x = X0 + 1; x < XE; x++) {
            for (int y = UP + 1; y < ROOF; y++) {
                set(x, y, Z1, Math.floorMod(x - X0, 12) == 0 ? (y == UP + 1 ? "tx_column_base" : y == ROOF - 1 ? "tx_column_cap" : "tx_column") : "air");
            }
            set(x, UP, Z1, "un_slab_terrazzo");
            if (x > XH) {
                for (int y = ROOF; y < LROOF; y++) {
                    set(x, y, Z1, y == LROOF - 1 ? "ml_precast_band" : precast());
                }
            }
        }
        // the west block's wall over the hallway runs on up the lobby's side over the west roof
        for (int z = Z1; z <= ZL; z++) {
            for (int y = ROOF; y < LROOF; y++) {
                set(XH, y, z, y == LROOF - 1 ? "ml_precast_band" : precast());
            }
        }
        // between the waiting area and the lobby: open but for the posts
        for (int z = Z1 + 1; z < ZL; z++) {
            for (int y = UP + 1; y < ROOF; y++) {
                set(XH, y, z, Math.floorMod(z - Z1, 6) == 0 ? "tx_panel_mid_s" : y >= ROOF - 2 ? "wf_panel" : "air");
            }
            set(XH, UP, z, "un_slab_terrazzo");
            set(XH, 0, z, "floor_concrete");
            for (int y = 1; y < UP; y++) {
                set(XH, y, z, "tx_wall");
            }
        }
    }

    /** What the outer wall is at (x, y, z). */
    private String outer(int x, int y, int z) {
        if (y == UP || y == ROOF - 1 && !(inLobby(x, z) && x > XH) || y == LROOF - 1 || y == LUP && inLobby(x, z) && x > XH) {
            return "ml_precast_band";
        }
        if (y < UP) {
            return precast();
        }
        if (x == X0) {
            // the lounge's glass to the west, the security hall's high band, the rest precast
            if (z <= -307) {
                return Math.floorMod(z, 4) == 0 ? "wf_steel" : glass("west", 0.12);
            }
            return y >= ROOF - 4 && y <= ROOF - 2 && Math.floorMod(z, 4) != 0 ? glass("west", 0.1) : precast();
        }
        if (z == ZS) {
            return y >= UP + 2 && y <= UP + 4 && Math.floorMod(x, 6) != 0 && x > -104 ? glass("south", 0.1) : precast();
        }
        if (x == XH) {
            // the hallway's windows on the tarmac, floor to ceiling
            return Math.floorMod(z, 4) == 0 || y > ROOF - 3 ? "wf_steel" : glass("east", 0.15);
        }
        if (z == ZL || x == XE) {
            String out = z == ZL ? "south" : "east";
            return Math.floorMod(z == ZL ? x : z, 5) == 0 ? "wf_steel" : glass(out, 0.12);
        }
        return precast();
    }

    // ------------------------------------------------------------------ the west side

    /** The lounge at the west end behind its glass: rows facing out, palms, gate 4's podium at the bus door. */
    private void westLounge() {
        int y = UP + 1;
        for (int x = X0 + 1; x <= -104; x++) {
            for (int z = Z1 + 1; z <= -307; z++) {
                set(x, UP, z, chance() < 0.08 ? "un_slab_carpet_b" : "un_slab_carpet");
            }
        }
        for (int x = X0 + 4; x <= -112; x += 4) {
            seatsZ(x, Z1 + 3, -309, "west", 7);
            seatsZ(x + 1, Z1 + 3, -309, "east", 7);
        }
        palm(X0 + 1, Z1 + 1, 4);
        palm(X0 + 1, -307, 3);
        palm(-105, -309, 4);
        // gate 4: the bus gate's podium, its number hung, the stair down behind the glass door
        set(-108, y, -318, "ap_gate_desk[facing=west]");
        set(-108, y + 4, -321, "ap_gate_4[facing=west]");
        rods(-108, y + 5, -321, ROOF);
        boolean dead = !power || chance() < 0.5;
        set(-106, y, -312, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=west]");
        set(-106, y + 1, -312, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=west]");
        // the wall to the security hall: glass over a dado, a way through by the window
        for (int x = X0 + 1; x <= -104; x++) {
            for (int yy = y; yy < ROOF; yy++) {
                boolean way = x >= X0 + 4 && x <= X0 + 7 && yy <= y + 3;
                set(x, yy, -307, way ? "air" : yy == y ? "wf_panel" : yy <= y + 3 ? (Math.floorMod(x, 4) == 0 ? "wf_steel" : "tx_glass[axis=x]") : "drywall");
            }
        }
    }

    /**
     * The security hall: the queue's maze off the lobby, four lanes of x-ray and arches running east
     * to west, the glass line they hold, the Guard's sandbags; the SECURITY sign hung over the way in;
     * the office to the south with its screens.
     */
    private void security() {
        int y = UP + 1, xa = X0 + 1, xb = -104, za = -306, zb = -263;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, chance() < 0.1 ? "tx_terrazzo_b" : "un_slab_terrazzo");
            }
        }
        // the maze: barrier lines along z, open at alternate ends, east of the lanes
        for (int r = 0; r < 4; r++) {
            int x = -110 + r * 2;
            int from = r % 2 == 0 ? za + 3 : za + 6, to = r % 2 == 0 ? zb - 6 : zb - 3;
            for (int z = from; z <= to; z++) {
                set(x, y, z, chance() < 0.06 ? "air" : "wf_queue[axis=z]");
            }
            set(x, y, from - 1, "wf_queue_post");
            set(x, y, to + 1, "wf_queue_post");
        }
        // the lanes: the belt along x, the arch beside it, the podium at the head
        for (int lz : new int[] {-302, -294, -286, -278, -270}) {
            lane(-122, lz);
        }
        // the glass line between the lanes, the posts
        for (int z = za; z <= zb; z++) {
            if (empty(-122, y, z)) {
                for (int yy = y; yy <= y + 2; yy++) {
                    set(-122, yy, z, Math.floorMod(z, 4) == 0 ? "wf_steel" : "tx_glass[axis=z]");
                }
            }
        }
        // the Guard's line: sandbags across the exits, the table, the boxes
        for (int z = za + 1; z < zb; z++) {
            if (chance() < 0.3 || !empty(-130, y, z)) {
                continue;
            }
            set(-130, y, z, "sandbags[facing=west]");
        }
        set(-132, y, -290, "trade_banquet_table");
        set(-133, y, -289, "crate");
        set(-133, y, -291, "pk_military_crate_old");
        set(-104, y + 2, -290, "ap_notice_evac[facing=east]");
        // the security office: drywall round it, a door off the hall, the desks and the screens
        int ox1 = -117, oz0 = -262, oz1 = -249;
        for (int x = xa; x <= ox1; x++) {
            wallAlongX(x, x, oz0, "drywall", ROOF - 1);
            wallAlongX(x, x, oz1, "drywall", ROOF - 1);
            for (int z = oz0 + 1; z < oz1; z++) {
                set(x, UP, z, "un_slab_vct");
                set(x, UP + 5, z, "un_slab_vct");
            }
        }
        wallAlongZ(ox1, oz0, oz1, "drywall", ROOF - 1);
        lamps(xa, oz0, ox1, oz1, UP + 5, 0.4);
        set(-121, y, oz0, "air");
        set(-121, y + 1, oz0, "air");
        door(-121, UP, oz0, Doors.FLAT, "north");
        for (int x = xa + 1; x <= ox1 - 2; x += 3) {
            set(x, y, oz1 - 2, "home_desk_pc[facing=north]");
            set(x, y, oz1 - 3, (chance() < 0.25 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=south]");
        }
        for (int x = xa + 1; x <= xa + 8; x++) {
            set(x, y, oz0 + 1, "fit_console" + (power && chance() < 0.5 ? "_on" : "") + "[facing=south]");
            set(x, y + 1, oz0 + 1, (power && chance() < 0.6 ? "hs_monitor_on" : "hs_monitor") + "[facing=south]");
        }
        for (int z = oz0 + 2; z < oz1 - 1; z += 2) {
            set(ox1 - 1, y, z, "fit_locker_lo[facing=west]");
            set(ox1 - 1, y + 1, z, "fit_locker_hi[facing=west]");
        }
        set(xa + 10, y, oz0 + 4, "fit_mess_table[axis=z]");
        set(xa + 9, y, oz0 + 4, "store_chair[facing=east]");
        set(xa + 2, y + 2, oz1 - 1, "ap_notice_missing[facing=north]");
    }

    /** One lane along x at z: the rollers, the x-ray, the arch beside it, the podium, the bins. */
    private void lane(int lx, int lz) {
        int y = UP + 1;
        set(lx + 7, y, lz + 2, "ap_podium[facing=east]");
        set(lx + 5, y, lz, "ap_bins");
        set(lx + 4, y, lz, "ap_rollers[facing=north]");
        set(lx + 3, y, lz, "ap_rollers[facing=north]");
        set(lx + 2, y, lz, "ap_xray_in[facing=north]");
        set(lx + 1, y, lz, "ap_xray[facing=north]");
        set(lx, y, lz, "ap_xray[facing=north]");
        set(lx - 1, y, lz, "ap_xray_out[facing=north]");
        set(lx - 2, y, lz, "ap_rollers[facing=north]");
        set(lx - 3, y, lz, "ap_rollers[facing=north]");
        set(lx, y, lz + 2, "ap_wtmd[facing=east]");
        set(lx, y + 1, lz + 2, "ap_wtmd_top[facing=east]");
        for (int yy = y; yy <= y + 2; yy++) {
            set(lx, yy, lz + 1, "wf_steel");
            set(lx, yy, lz + 3, "wf_steel");
        }
        set(lx - 5, y, lz + 3, "tx_bench[facing=north]");
        for (int k = 0; k < 3; k++) {
            int x = lx - 4 + rng.nextInt(9), z = lz - 1 + rng.nextInt(4);
            if (empty(x, y, z) && chance() < 0.5) {
                set(x, y, z, chance() < 0.5 ? "ap_bins" : bag());
            }
        }
    }

    /** The restrooms in the south-west corner off the hall, the stair beside them down and up. */
    private void restrooms() {
        int y = UP + 1, za = -248, zb = ZS - 1;
        for (int[] r : new int[][] {{X0 + 1, -131, 1}, {-130, -122, 0}}) {
            for (int x = r[0]; x <= r[1]; x++) {
                for (int z = za; z <= zb; z++) {
                    set(x, UP, z, "home_floor_tile");
                    boolean wall = x == r[0] && r[0] != X0 + 1 || x == r[1] || z == za;
                    for (int yy = y; yy < ROOF - 4; yy++) {
                        set(x, yy, z, wall ? "home_wall_tile_white" : "air");
                    }
                    set(x, ROOF - 4, z, "un_slab_vct");
                }
            }
            for (int z = za + 2; z < zb; z += 2) {
                set(r[0] + (r[0] == X0 + 1 ? 0 : 1), y, z, "fit_toilet_stall[facing=east]");
                set(r[1] - 1, y, z, "home_sink_white[facing=west]");
                set(r[1] - 1, y + 1, z, "home_mirror[facing=west]");
            }
            int dx = (r[0] + r[1]) / 2;
            set(dx, y, za, "air");
            set(dx, y + 1, za, "air");
            door(dx, UP, za, Doors.FLAT, "north");
            set(dx + 1, y + 2, za - 1, (r[2] == 1 ? "tr_sign_men" : "tr_sign_ladies") + "[facing=north]");
            lamps(r[0], za, r[1], zb, ROOF - 4, 0.3);
        }
        stair(-116, za, false, new int[] {0, UP, ROOF}, "ml_precast", "un_roof");
        set(-108, y, za + 2, "st_fountain[facing=west]");
        set(-108, y, za + 4, "home_trash_can");
    }

    // ------------------------------------------------------------------ the food court and the waiting area

    /** True inside Grilltown's kiosk. */
    private static boolean inKiosk(int x, int z) {
        return x >= -95 && x <= -84 && z >= -326 && z <= -316;
    }

    /**
     * The food court: the checkered floor, Grilltown's kiosk standing in its north-west corner open
     * to the south and the east with its signs over both sides, the tables and the sofas, the
     * vending machines and the camera advert on the wood wall, the palms, strip skylights over it.
     */
    private void foodCourt() {
        int y = UP + 1, xa = -103, xb = -70, za = Z1 + 1, zb = -300;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, chance() < 0.12 ? "dn_checker_worn" : "dn_checker");
            }
        }
        kiosk();
        cafeTables(-80, -326, -74, -304, UP);
        cafeTables(-100, -312, -84, -304, UP);
        // the sofas in pairs back to back down the middle
        for (int z = -313; z >= -324; z -= 4) {
            for (int[] s : new int[][] {{-80, 0}, {-79, 1}, {-78, 2}}) {
                String k = s[1] == 0 ? "l" : s[1] == 1 ? "m" : "r";
                if (empty(s[0], y, z)) {
                    set(s[0], y, z, "home_sofa_brown_" + k + "[facing=north]");
                }
            }
        }
        // the wood wall to the waiting area: the machines and the advert on its food court side
        for (int z = Z1 + 1; z <= -309; z++) {
            for (int yy = y; yy < ROOF; yy++) {
                set(-70, yy, z, yy >= ROOF - 2 ? "wf_panel" : "wood_panel");
            }
        }
        for (int z : new int[] {-328, -327, -326, -308 - 4}) {
            set(-71, y, z, (chance() < 0.2 ? "pk_vending" : "store_vending") + "[facing=west]");
            set(-71, y + 1, z, "store_vending_top[facing=west]");
        }
        picture("ap_ad_vista_", 3, 2, -71, y + 2, -320, "west");
        palm(-103, -301, 4);
        palm(-71, -302, 4);
        set(-72, y, -324, "pk_houseplant[facing=west]");
        set(-98, y, -301, "ext_trash_can");
        set(-74, y, -310, "ext_trash_can_tipped[facing=" + dir() + "]");
        hungSign("rest", -88, y + 5, -301, "south", ROOF);
    }

    /** Grilltown's corner kiosk: a dark box open to the south and east, the counters, the kitchen, the boards, the signs on its fascia. */
    private void kiosk() {
        int y = UP + 1, x0 = -95, x1 = -84, z0 = -326, z1 = -316;
        String[] kit = {"store_grill", "dn_fryer", "store_oven", "home_fridge", "st_fountain", "dn_fryer", "store_grill"};
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, UP, z, "home_floor_tile");
                boolean back = x == x0 || z == z0, front = x == x1 || z == z1;
                for (int yy = y; yy <= y + 4; yy++) {
                    String s = "air";
                    if (back) {
                        s = yy <= y + 2 ? "home_wall_tile_white" : "tx_panel";
                    } else if (front) {
                        boolean post = x == x1 && z == z1 || x == x1 && z == z0 + 4 || z == z1 && x == x0 + 4;
                        s = post ? "tx_panel" : yy == y ? "store_counter[facing=" + (x == x1 ? "east" : "south") + "]"
                                : yy <= y + 2 ? "air" : "tx_panel";
                    }
                    set(x, yy, z, s);
                }
                set(x, y + 5, z, "tx_panel");
                if (!back && !front && (x + z) % 3 == 0) {
                    set(x, y + 4, z, power && chance() < 0.4 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        // the registers, a way in for the staff at the corner, the kitchen along the back
        set(x1, y, z0 + 7, "store_register[facing=east]");
        set(x0 + 7, y, z1, "store_register[facing=south]");
        set(x1, y, z0 + 1, "air");
        set(x1, y + 1, z0 + 1, "air");
        for (int x = x0 + 1; x < x1; x++) {
            set(x, y, z0 + 1, kit[Math.floorMod(x, kit.length)] + "[facing=south]");
        }
        for (int z = z0 + 2; z < z1; z++) {
            set(x0 + 1, y, z, (z % 2 == 0 ? "store_counter" : "home_fridge") + "[facing=east]");
        }
        set(x0 + 4, y, z0 + 4, "ap_tray_rack[facing=south]");
        // the boards on the back walls, facing the counters
        boards("ap_menu_grilltown_", x0 + 5, y + 2, z0 + 1, "south");
        boards("ap_menu_grilltown_", x0 + 1, y + 2, z0 + 5, "east");
        // the name on the fascia, both open sides, standing out over the counters
        row("ap_store_grilltown_", 3, x0 + 6, y + 3, z1 + 1, "south", 0.0);
        row("ap_store_grilltown_", 3, x1 + 1, y + 3, z0 + 6, "east", 0.0);
        set(x1 + 1, y, z1 + 2, "ap_tray_rack[facing=east]");
        set(x1 + 2, y, z1 + 1, "home_trash_can");
    }

    /** A board of a picture three wide at (x, y, z) on a wall facing that way. */
    private void boards(String prefix, int x, int y, int z, String facing) {
        row(prefix, 3, x, y, z, facing, 0.0);
    }

    /**
     * The waiting area between the food court and the lobby: the wood bar along the food court's
     * wall with its stools, the shelves and the mirror behind it; rows of seats facing the big
     * departures board hung over the way to the atrium; gate 3's podium.
     */
    private void waitingArea() {
        int y = UP + 1, xa = -69, xb = XH - 1, za = Z1 + 1, zb = -308;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, chance() < 0.06 ? "un_slab_carpet_b" : "un_slab_carpet");
            }
        }
        // the bar: the back bar against the wood wall, the counter two out, stools in front
        for (int z = za + 1; z <= zb - 3; z++) {
            set(xa, y, z, (z % 4 == 0 ? "trade_bar_taps" : "store_shelf_liquor") + "[facing=east]");
            set(xa + 2, y, z, z == za + 5 ? "store_register[facing=east]" : "trade_bar[facing=east]");
            if (chance() < 0.75) {
                set(xa + 3, y, z, chance() < 0.15 ? "dn_stool_torn" : "dn_stool_r");
            }
        }
        set(xa + 2, y, zb - 2, "air");
        set(xa - 0, y + 1, za + 3, "trade_bar_mirror_logo[facing=east]");
        set(xa, y + 1, za + 8, "trade_bar_mirror[facing=east]");
        set(xa, y + 1, za + 13, "trade_bar_mirror[facing=east]");
        for (int z = za + 1; z <= zb - 3; z++) {
            set(xa + 2, y + 4, z, "tx_panel");
            if (z % 3 == 0) {
                set(xa + 2, y + 3, z, power && chance() < 0.5 ? "tx_pendant" : "tx_pendant_off");
            }
        }
        // the rows facing south to the board
        for (int z = za + 3; z <= zb - 4; z += 3) {
            seatsX(-62, xb - 1, z, "south", 7);
        }
        // the big board over the way south, its back to the waiting area
        picture("tx_board_", 4, 2, -57, ROOF - 5, zb, "north");
        for (int x = -59; x <= -55; x += 4) {
            rods(x, ROOF - 3, zb, ROOF);
        }
        set(-47, y, zb - 1, "ap_gate_desk[facing=north]");
        set(-47, y + 4, zb - 1, "ap_gate_3[facing=south]");
        rods(-47, y + 5, zb - 1, ROOF);
        palm(xb, za, 4);
    }

    // ------------------------------------------------------------------ the atrium

    private static double fromAtrium(int x, int z) {
        return Math.hypot(x + 0.5 - (AX + 0.5), z + 0.5 - (AZ + 0.5));
    }

    /**
     * The atrium under the round lantern: marble inside a black granite ring, the fir in its granite
     * bed in the middle, the flower planters round it, four clad columns with the departures screens
     * on their faces, paper planes hung from the lantern's ribs.
     */
    private void atrium() {
        int y = UP + 1;
        for (int x = AX - AR - 1; x <= AX + AR + 1; x++) {
            for (int z = AZ - AR - 1; z <= AZ + AR + 1; z++) {
                double d = fromAtrium(x, z);
                if (d <= AR + 0.5) {
                    set(x, UP, z, d > AR - 1 ? "pt_granite_black" : d > AR - 2.5 && (x + z) % 2 == 0 ? "us_marble_b" : "us_marble_a");
                }
            }
        }
        // the bed: granite walls a block high, the soil, the fir
        for (int x = AX - 3; x <= AX + 3; x++) {
            for (int z = AZ - 3; z <= AZ + 3; z++) {
                boolean rim = Math.abs(x - AX) == 3 || Math.abs(z - AZ) == 3;
                set(x, y, z, rim ? "tx_granite" : "bed_soil");
                if (!rim && chance() < 0.3 && (x != AX || z != AZ)) {
                    set(x, y + 1, z, chance() < 0.6 ? "mu_fern" : "home_plant_dead");
                }
            }
        }
        fir(AX, y + 1, AZ);
        // the flower planters round it on the diagonals, the benches between
        for (int[] c : new int[][] {{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    int x = AX + c[0] + (c[0] < 0 ? -dx : dx), z = AZ + c[1] + (c[1] < 0 ? -dz : dz);
                    set(x, y, z, "om_planter");
                    set(x, y + 1, z, chance() < 0.8 ? "om_flowers" : "air");
                }
            }
        }
        for (int[] c : new int[][] {{0, -6, 2}, {0, 6, 0}, {-6, 0, 1}, {6, 0, 3}}) {
            set(AX + c[0], y, AZ + c[1], "tx_bench[facing=" + DIRS[c[2]] + "]");
        }
        // the columns with their screens, on the ring
        for (int[] c : new int[][] {{-8, 0}, {8, 0}, {0, -8}, {0, 8}}) {
            int cx = AX + c[0], cz = AZ + c[1];
            for (int yy = y; yy < ROOF; yy++) {
                set(cx, yy, cz, yy == y ? "tx_panel_lo_s" : yy == ROOF - 1 ? "tx_panel_hi_s" : "tx_panel_mid_s");
            }
            boolean dead = !power || chance() < 0.5;
            for (int d = 0; d < 4; d++) {
                int fx = cx + new int[] {0, 1, 0, -1}[d], fz = cz + new int[] {-1, 0, 1, 0}[d];
                if (fromAtrium(fx, fz) < fromAtrium(cx, cz)) {
                    continue;
                }
                set(fx, y + 2, fz, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=" + DIRS[d] + "]");
            }
        }
        // the paper planes, hung from the lantern on rods, noses every which way
        for (int[] c : new int[][] {{-5, -3}, {4, 5}, {3, -6}, {-4, 6}}) {
            int x = AX + c[0], z = AZ + c[1], py = ROOF - 3 + rng.nextInt(2);
            set(x, py, z, "ap_paper_plane[facing=" + dir() + "]");
            rods(x, py + 1, z, ROOF + 4);
        }
    }

    /** A fir in the atrium's bed, built by hand to fit under the lantern (and of its own blocks, which the trees' tidy-up leaves be): the trunk, the tiers closing in. */
    private void fir(int x, int y, int z) {
        int h = 10;
        for (int k = 0; k < h; k++) {
            set(x, y + k, z, "ap_fir_trunk");
        }
        double[] r = {0, 3.2, 3.0, 2.6, 2.4, 2.0, 1.8, 1.4, 1.2, 0.8, 0.6, 0.0};
        for (int k = 1; k <= h; k++) {
            int ri = (int) Math.ceil(r[k]);
            for (int dx = -ri; dx <= ri; dx++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.hypot(dx, dz);
                    if ((dx != 0 || dz != 0) && d <= r[k] + 0.2 && empty(x + dx, y + k, z + dz)) {
                        set(x + dx, y + k, z + dz, d > r[k] - 0.6 && chance() < 0.4 ? "ap_fir_needles_sparse" : "ap_fir_needles");
                    }
                }
            }
        }
        set(x, y + h, z, "ap_fir_needles");
        set(x, y + h + 1, z, "ap_fir_needles_sparse");
    }

    // ------------------------------------------------------------------ the lobby side

    /**
     * The security lobby between the food court and check-in: the checkered floor, the golf cart
     * left by the way to security, the reservations desk standing in the middle with its sign hung
     * over it, the self check-in kiosks, a column of screens.
     */
    private void securityLobby() {
        int y = UP + 1, xa = -103, xb = -76, za = -299, zb = -263;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                if (fromAtrium(x, z) > AR + 0.5) {
                    set(x, UP, z, chance() < 0.1 ? "dn_checker_worn" : "floor_checker");
                }
            }
        }
        // the reservations desk: the counter facing north, the agents' side, the sign over it
        for (int x = -96; x <= -84; x++) {
            set(x, y, -275, x == -92 || x == -87 ? "store_register[facing=north]" : "ap_counter[facing=north]");
            set(x, y, -273, Math.floorMod(x, 3) == 0 ? "home_desk_pc[facing=north]" : "air");
        }
        set(-97, y, -274, "ap_counter[facing=west]");
        set(-83, y, -274, "ap_counter[facing=east]");
        boards("ap_store_reserve_", -89, y + 4, -276, "north");
        for (int x = -91; x <= -87; x += 4) {
            rods(x, y + 5, -276, ROOF);
        }
        set(-90, y, -272, (chance() < 0.3 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=south]");
        set(-86, y, -272, "store_chair[facing=south]");
        // the cart, the kiosks, the bags
        set(-100, y, -292, "ap_golf_front[facing=north]");
        set(-100, y, -291, "ap_golf_rear[facing=north]");
        for (int x = -86; x <= -80; x += 2) {
            set(x, y, -296, "ap_kiosk[facing=north]");
        }
        for (int k = 0; k < 10; k++) {
            int x = xa + rng.nextInt(xb - xa), z = za + rng.nextInt(zb - za);
            if (empty(x, y, z) && fromAtrium(x, z) > AR + 1) {
                set(x, y, z, chance() < 0.2 ? "ap_cart[facing=" + dir() + "]" : bag());
            }
        }
        palm(xa, zb, 4);
        hungSign("security", -101, y + 5, -285, "east", ROOF);
        hungSign("gates_a", -86, y + 5, -300, "north", ROOF);
    }

    /** Check-in along the south wall: the counters with their scales, the belt behind, the airlines' headers on the wall, the queue lines, the kiosks. */
    private void checkIn() {
        int y = UP + 1, xa = -103, xb = -76, za = -262, zb = ZS - 1;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, chance() < 0.1 ? "tx_terrazzo_b" : "un_slab_terrazzo");
            }
        }
        for (int x = xa + 1; x <= xb - 1; x++) {
            set(x, y, zb - 2, Math.floorMod(x, 3) == 0 ? "ap_counter_scale[facing=north]" : "ap_counter[facing=north]");
            set(x, y, zb - 1, (chance() < 0.25 ? "ap_belt_bags" : "ap_belt") + "[axis=x]");
        }
        set(xa, y, zb - 1, "ap_belt_hole[facing=west]");
        int k = 0;
        for (String a : new String[] {"eagle", "coastal", "north"}) {
            boards("ap_airline_" + a + "_", xa + 4 + k * 9, y + 3, zb, "north");
            k++;
        }
        // the queue lines along x, open at alternate ends
        for (int r = 0; r < 4; r++) {
            int z = zb - 5 - r * 2;
            int from = r % 2 == 0 ? xa + 2 : xa + 5, to = r % 2 == 0 ? xb - 5 : xb - 2;
            for (int x = from; x <= to; x++) {
                set(x, y, z, chance() < 0.07 ? "air" : "wf_queue[axis=x]");
            }
            set(from - 1, y, z, "wf_queue_post");
            set(to + 1, y, z, "wf_queue_post");
        }
        for (int x = xa + 3; x <= xb - 3; x += 4) {
            set(x, y, za + 1, "ap_kiosk[facing=north]");
        }
        for (int j = 0; j < 8; j++) {
            int x = xa + rng.nextInt(xb - xa), z = zb - 13 + rng.nextInt(10);
            if (empty(x, y, z)) {
                set(x, y, z, bag());
            }
        }
        set(xb - 1, y + 2, zb, "ap_notice_fema[facing=north]");
        hungSign("ticketing", (xa + xb) / 2, y + 5, za + 3, "north", ROOF);
    }

    /**
     * Airside Books between the atrium and the luggage shop, x -74..-56, z -278..-264: glass on its
     * east and north, ways in at both ends so the way runs through; the shelf runs, the tables, the
     * magazines, the till.
     */
    private void bookshop() {
        int y = UP + 1, xa = -75, xb = -55, za = -279, zb = -263;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z <= zb; z++) {
                boolean wall = x == xa || x == xb || z == za || z == zb;
                set(x, UP, z, "un_slab_vct");
                if (wall) {
                    for (int yy = y; yy < ROOF - 4; yy++) {
                        set(x, yy, z, "drywall");
                    }
                }
                if (!wall) {
                    set(x, ROOF - 4, z, (x + z) % 4 == 0 ? "un_slab_vct_lamp" + (power && chance() < 0.4 ? "_on" : "") : "un_slab_vct");
                }
            }
        }
        frontX(xa + 1, xb - 1, za, -66);
        frontX(xa + 1, xb - 1, zb, -68);
        frontZ(xb, za + 1, zb - 1, -272);
        row("ap_store_books_", 3, -64, UP + 5, za - 1, "north", 0.0);
        row("ap_store_books_", 3, xb + 1, UP + 5, -270, "east", 0.0);
        for (int gx : new int[] {-72, -68, -61}) {
            for (int[] run : new int[][] {{za + 2, za + 6}, {zb - 6, zb - 2}}) {
                for (int z = run[0]; z <= run[1]; z++) {
                    set(gx, y, z, chance() < 0.08 ? "home_books_stack_low" : "ap_gondola[axis=z]");
                    set(gx, y + 1, z, "ap_gondola_top[axis=z]");
                }
                set(gx, y, run[0] - 1, "ap_gondola_end[facing=north]");
                set(gx, y, run[1] + 1, "ap_gondola_end[facing=south]");
            }
        }
        for (int z = za + 1; z <= zb - 1; z++) {
            set(xa + 1, y, z, "home_bookcase[facing=east]");
            set(xa + 1, y + 1, z, "home_bookcase_top[facing=east]");
        }
        set(-70, y, -271, "ap_book_table[facing=north]");
        set(-58, y, -275, "ap_book_table[facing=north]");
        for (int x = -60; x <= -57; x++) {
            set(x, y, zb - 1, "ap_mag_rack[facing=north]");
        }
        for (int z = -269; z <= -266; z++) {
            set(-58, y, z, z == -267 ? "store_register[facing=west]" : "store_counter[facing=west]");
        }
    }

    /** Bag &amp; Go, the luggage shop by check-in: its glass front north, the cases on the floor in rows, the shelves, the till. */
    private void luggage() {
        int y = UP + 1, xa = -75, xb = -60, za = -259, zb = ZS;
        for (int x = xa; x <= xb; x++) {
            for (int z = za; z < zb; z++) {
                boolean wall = x == xa || x == xb;
                set(x, UP, z, "un_slab_vct");
                if (wall) {
                    for (int yy = y; yy < ROOF - 4; yy++) {
                        set(x, yy, z, "drywall");
                    }
                } else if (z > za) {
                    set(x, ROOF - 4, z, (x + z) % 4 == 0 ? "un_slab_vct_lamp" + (power && chance() < 0.4 ? "_on" : "") : "un_slab_vct");
                }
            }
        }
        frontX(xa + 1, xb - 1, za, -69);
        for (int yy = UP + 5; yy < ROOF - 3; yy++) {
            fill(xa + 1, yy, za, xb - 1, yy, za, "drywall");
        }
        row("ap_store_luggage_", 3, -68, UP + 5, za - 1, "north", 0.0);
        for (int z = za + 2; z < zb - 1; z++) {
            set(xa + 1, y, z, "store_shelf_thrift[facing=east]");
            set(xa + 1, y + 1, z, "store_shelf_thrift_top[facing=east]");
            set(xb - 1, y, z, "store_shelf_empty[facing=west]");
        }
        for (int x = xa + 3; x <= xb - 3; x += 3) {
            for (int z = za + 3; z < zb - 3; z += 2) {
                if (chance() < 0.75) {
                    set(x, y, z, (chance() < 0.5 ? "ap_bag_up" : "ap_bag_up_b") + "[facing=north]");
                }
            }
        }
        for (int x = xa + 2; x <= xa + 5; x++) {
            set(x, y, zb - 2, x == xa + 3 ? "store_register[facing=north]" : "store_counter[facing=north]");
        }
    }

    /**
     * The hallway down the tarmac windows from the waiting area to gate 1's room: the wall to its
     * west with ways through, the screens, the door out to the outside stair down to the tarmac.
     */
    private void hallway() {
        int y = UP + 1, xa = -48, za = -307, zb = -251;
        for (int x = xa; x < XH; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, chance() < 0.1 ? "tx_terrazzo_b" : "tx_terrazzo_d");
            }
        }
        for (int z = -300; z <= zb; z++) {
            boolean way = z >= -297 && z <= -294 || z >= -284 && z <= -281 || z >= -262 && z <= -260;
            for (int yy = y; yy < ROOF; yy++) {
                set(xa - 1, yy, z, way && yy <= y + 3 ? "air" : yy == y ? "wf_panel" : yy <= y + 3 && Math.floorMod(z, 4) != 0 ? "tx_glass[axis=z]" : "drywall");
            }
        }
        for (int z = za + 4; z <= zb - 2; z += 9) {
            boolean dead = !power || chance() < 0.5;
            set(XH - 1, y, z, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=west]");
            set(XH - 1, y + 1, z, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=west]");
        }
        seatsZ(xa, -293, -287, "east", 8);
        seatsZ(xa, -278, -266, "east", 7);
        // the door out over the tarmac, the landing, the stair down to the south
        for (int z = -270; z <= -268; z++) {
            for (int yy = y; yy <= y + 3; yy++) {
                set(XH, yy, z, "air");
            }
            set(XH, y + 4, z, "wf_steel");
        }
        doors(XH, UP, -270, Doors.STEEL, "east");
        for (int x = XH + 1; x <= XH + 3; x++) {
            for (int z = -271; z <= -268; z++) {
                set(x, UP, z, "fit_deckplate");
            }
            set(x, y, -272, "railing");
        }
        set(XH + 3, y, -271, "railing");
        set(XH + 3, y, -270, "railing");
        for (int k = 1; k <= UP; k++) {
            int z = -268 + k, yk = UP - k + 1;
            for (int x = XH + 1; x <= XH + 2; x++) {
                set(x, yk, z, "stairs_concrete[facing=north]");
                for (int yy = 1; yy < yk; yy++) {
                    set(x, yy, z, "stone_trim");
                }
            }
            set(XH + 3, yk + 1, z, "railing");
            for (int yy = 1; yy <= yk; yy++) {
                set(XH + 3, yy, z, "stone_trim");
            }
        }
        hungSign("gates_a", -45, y + 5, za + 3, "north", ROOF);
    }

    // ------------------------------------------------------------------ gate 1 and the plane

    /**
     * Gate 1 ("TO PLANE") at the hallway's south end: the room behind the glass, the podium by the
     * jet bridge's door in the east wall, the rows facing the plane, the stair down to the bag hall
     * and the door out to the tarmac under the bridge.
     */
    private void gateOne() {
        int y = UP + 1, xa = -59, xb = XH - 1, za = -250, zb = ZS - 1;
        for (int x = xa - 1; x <= xb; x++) {
            for (int z = za - 1; z <= zb; z++) {
                set(x, UP, z, x >= xa && z >= za ? (chance() < 0.08 ? "un_slab_carpet_b" : "un_slab_carpet") : get(x, UP, z));
            }
        }
        for (int z = za - 1; z <= zb; z++) {
            for (int yy = y; yy < ROOF; yy++) {
                set(xa - 1, yy, z, yy <= y + 3 ? "drywall" : "wf_panel");
            }
        }
        for (int x = xa; x <= -49; x++) {
            boolean way = x >= -58 && x <= -55;
            for (int yy = y; yy < ROOF; yy++) {
                set(x, yy, za - 1, way && yy <= y + 3 ? "air" : yy <= y + 3 ? (Math.floorMod(x, 4) == 0 ? "wf_steel" : "tx_glass[axis=x]") : "wf_panel");
            }
        }
        hungSign("gates_a", -56, y + 4, za - 2, "north", ROOF);
        // the bridge's door in the east wall
        for (int z = -244; z <= -242; z++) {
            for (int yy = y; yy <= y + 3; yy++) {
                set(XH, yy, z, "air");
            }
            set(XH, y + 4, z, "wf_steel");
        }
        set(XH - 2, y, -246, "ap_gate_desk[facing=west]");
        set(XH - 2, y + 4, -243, "ap_gate_1[facing=west]");
        rods(XH - 2, y + 5, -243, ROOF);
        boolean dead = !power || chance() < 0.4;
        set(XH - 1, y, -248, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=west]");
        set(XH - 1, y + 1, -248, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=west]");
        for (int x = -52; x <= -46; x += 3) {
            seatsZ(x, za + 1, zb - 1, "east", 5);
        }
        // the stair down along the west wall: two wide, falling south to the bag hall's floor
        for (int k = 0; k < UP; k++) {
            int z = za + k, yk = UP - k;
            for (int x = xa; x <= xa + 1; x++) {
                set(x, yk, z, "stairs_concrete[facing=north]");
                for (int yy = yk + 1; yy <= yk + 4 && yy < ROOF; yy++) {
                    set(x, yy, z, "air");
                }
            }
            set(xa + 2, UP + 1, z, "tx_glass_rail[axis=z]");
        }
        set(xa + 2, UP + 1, za + UP, "tx_glass_rail[axis=z]");
        set(xa + 3, y + 2, zb, "ap_notice_missing[facing=north]");
    }

    /** True inside the jet bridge: east from gate 1's door along the plane's side, then the cab at the front door. */
    private static boolean inBridge(int x, int z) {
        int door = NX + Airliner.L1;
        return x > XH && x <= door + 1 && z >= -244 && z <= -242
                || x >= door && x <= door + 1 && z == PZ - Airliner.WEST + 1;
    }

    /**
     * Gray Eagle 214 nose west to the building, the doors used on its south side; the jet bridge to
     * its front door, the slide down from the door on its right onto the tarmac, the ramp's gear.
     */
    private void plane() {
        Airliner.place(p.turned(NX, 0, PZ, 3), 0, 0, 0);
        jetBridge();
        slide(NX + Airliner.R2, PZ - Airliner.EAST - 1);
        rampGear();
    }

    /**
     * The jet bridge from gate 1's door to the front door: the walk on its floor at UP, the sides
     * with their window band, the roof with its lights; the rotunda's column by the building, the
     * drive leg and its wheels under the cab, the canopy against the fuselage; the service stair.
     */
    private void jetBridge() {
        int y = UP, door = NX + Airliner.L1, ez = PZ - Airliner.WEST + 1;
        String[] faces = {"north", "east", "south", "west"};
        int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        // the canopy's sides against the skin either side of the door
        for (int yy = y + 1; yy <= y + 4; yy++) {
            if (empty(door - 1, yy, ez)) {
                set(door - 1, yy, ez, "rp_jb_bellows[facing=east]");
            }
            if (empty(door + 2, yy, ez)) {
                set(door + 2, yy, ez, "rp_jb_bellows[facing=west]");
            }
        }
        for (int x = XH + 1; x <= door + 3; x++) {
            for (int z = ez - 1; z <= -241; z++) {
                if (inBridge(x, z)) {
                    boolean cab = z == ez;
                    set(x, y, z, "rp_jb_floor[facing=east]");
                    for (int yy = y + 1; yy <= y + 4; yy++) {
                        set(x, yy, z, "air");
                    }
                    boolean lamp = x % 3 == 0;
                    set(x, y + 5, z, cab ? "rp_jb_bellows_top[facing=north]"
                            : lamp ? "rp_jb_roof_lamp" + (power && chance() < 0.4 ? "_lit" : "") + "[facing=east]" : "rp_jb_roof[facing=east]");
                    continue;
                }
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
                    for (int d = 0; d < 4; d++) {
                        if ((sides >> d & 1) == 1 && (sides >> ((d + 3) % 4) & 1) == 1) {
                            f = d;
                        }
                    }
                    wall = f < 0 ? null : "corner";
                } else if (n == 0) {
                    for (int d = 0; d < 4; d++) {
                        int dd = (d + 3) % 4;
                        if (inBridge(x - step[d][0] - step[dd][0], z - step[d][1] - step[dd][1])) {
                            f = d;
                            wall = "corner";
                        }
                    }
                }
                if (wall == null || !empty(x, y + 1, z) || f == 0 && z == ez - 1) {
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
        set(door + 1, y + 1, -242, "rp_jb_console[facing=north]");
        // the rotunda's column by the building, the drive leg under the cab
        for (int yy = 1; yy < y; yy++) {
            set(XH + 3, yy, -243, "rp_jb_column[facing=north]");
        }
        for (int z : new int[] {-244, -242}) {
            set(door - 1, 1, z, "rp_jb_bogie[facing=east]");
            for (int yy = 2; yy < y; yy++) {
                set(door - 1, yy, z, yy < 4 ? "rp_jb_leg_lo[facing=north]" : "rp_jb_leg[facing=north]");
            }
        }
        // the service stair down from the rotunda's door to the apron, falling east along the bridge's south side
        set(XH + 5, y + 1, -241, "air");
        for (int k = 1; k <= UP; k++) {
            int x = XH + 5 + k, yk = UP - k + 1;
            set(x, yk, -240, "rp_as_step[facing=west]");
            for (int yy = 1; yy < yk; yy++) {
                set(x, yy, -240, "rp_as_lift[facing=west]");
            }
        }
        for (int x = XH + 4; x <= XH + 5; x++) {
            set(x, y, -240, "rp_as_top[facing=west]");
            set(x, y + 1, -239, "railing");
        }
        set(XH + 5, y, -241, "rp_jb_floor[facing=east]");
        set(XH + 5, y + 1, -241, "air");
        set(XH + 5, y + 2, -241, "air");
    }

    /**
     * The slide from the back door on the right: its head on the sill (x, x + 1 at z), the lengths
     * falling north a block for a block, the run-out on the apron.
     */
    private void slide(int x, int z) {
        for (int dx = 0; dx <= 1; dx++) {
            set(x + dx, Airliner.FLOOR, z, "rp_slide_top[facing=south]");
            for (int k = 1; k < Airliner.FLOOR; k++) {
                set(x + dx, Airliner.FLOOR - k, z - k, "rp_slide[facing=south]");
            }
            set(x + dx, 1, z - Airliner.FLOOR, "rp_slide_foot[facing=south]");
        }
        set(x - 1, 1, z - Airliner.FLOOR - 2, bag());
        set(x + 2, 1, z - Airliner.FLOOR - 1, bag());
        set(x + 1, 1, z - Airliner.FLOOR - 3, "ap_bag_open[facing=" + dir() + "]");
    }

    /** The ground crew's gear about the plane: the belt loader at the front hold, the train, the power cart, chocks, cones. */
    private void rampGear() {
        int hx = NX + 15, nz = PZ - Airliner.EAST - 1;
        set(hx, 1, nz - 4, "rp_bl_0[facing=south]");
        set(hx, 1, nz - 3, "rp_bl_1[facing=south]");
        set(hx, 2, nz - 2, "rp_bl_2[facing=south]");
        set(hx, 2, nz - 1, "rp_bl_3[facing=south]");
        // the train: the tug and three carts stopped under the wing, the tipped one, the spill
        int tx = NX + 20, tz = nz - 9;
        set(tx, 1, tz, "rp_tug_front[facing=west]");
        set(tx + 1, 1, tz, "rp_tug_rear[facing=west]");
        for (int k = 0; k < 3; k++) {
            boolean bags = chance() < 0.7;
            set(tx + 3 + k * 3, 1, tz, "rp_dolly_front" + (bags ? "_bags" : "") + "[facing=west]");
            set(tx + 4 + k * 3, 1, tz, "rp_dolly_rear" + (bags ? "_bags" : "") + "[facing=west]");
        }
        set(tx + 6, 1, tz - 3, "rp_dolly_tipped[facing=" + dir() + "]");
        set(tx + 5, 1, tz - 2, "ap_bag_open[facing=" + dir() + "]");
        set(tx + 8, 1, tz - 2, "ap_bag_flat[facing=" + dir() + "]");
        // the power cart at the nose, chocks at the wheels, cones at the tips and tail
        set(NX + 2, 1, nz - 2, "rp_gpu[facing=east]");
        set(NX + 8, 1, PZ, "rp_chocks[facing=east]");
        for (int[] c : new int[][] {{30, -5}, {30, 5}}) {
            if (empty(NX + c[0], 1, PZ + c[1])) {
                set(NX + c[0], 1, PZ + c[1], "rp_chocks[facing=east]");
            }
        }
        set(NX + 4, 1, nz - 6, "rp_wand[facing=" + dir() + "]");
        for (int[] c : new int[][] {{31, -28}, {31, 28}, {58, 0}, {-2, -2}, {-2, 2}}) {
            if (empty(NX + c[0], 1, PZ + c[1])) {
                set(NX + c[0], 1, PZ + c[1], "st_cone");
            }
        }
    }

    // ------------------------------------------------------------------ the lobby and the baggage hall

    /**
     * The lobby: the upper floor over its west part with the glass rail along its edge, the
     * escalators and the stair rising west up the middle to it, the lounge upstairs looking out over
     * the tarmac, the information desk, the stair shaft in the south-west corner.
     */
    private void lobby() {
        int y = UP + 1, xa = XH + 1, za = Z1 + 1, zb = ZL - 1;
        for (int x = xa; x <= LX1; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, LUP, z, chance() < 0.08 ? "un_slab_carpet_b" : "un_slab_carpet");
            }
        }
        for (int z = za; z <= zb; z++) {
            set(LX1, LUP + 1, z, "tx_glass_rail[axis=z]");
        }
        for (int z = za + 5; z <= zb; z += 8) {
            for (int yy = y; yy < LUP; yy++) {
                set(LX1, yy, z, yy == y ? "tx_column_base" : yy == LUP - 1 ? "tx_column_cap" : "tx_column");
            }
        }
        escalators(LX1 + UP + 1, -318);
        stair(xa, -311, true, new int[] {0, UP, LUP, LROOF}, "ml_precast", "un_roof");
        // downstairs: the information desk, the seats, the screens
        for (int z = -312; z <= -308; z++) {
            set(-24, y, z, z == -310 ? "store_register[facing=east]" : "ap_counter[facing=east]");
        }
        set(-26, y, -310, "store_chair[facing=east]");
        set(-26, y + 2, -313, "ap_notice_evac[facing=east]");
        for (int z = za + 3; z <= za + 12; z += 3) {
            seatsX(xa + 4, LX1 - 3, z, z % 2 == 0 ? "north" : "south", 6);
        }
        for (int x : new int[] {-8, 10}) {
            boolean dead = !power || chance() < 0.5;
            set(x, y, -303, "ap_fids_lo" + (dead ? "_dead" : "") + "[facing=north]");
            set(x, y + 1, -303, "ap_fids_hi" + (dead ? "_dead" : "") + "[facing=north]");
        }
        palm(xa + 6, zb, 4);
        palm(XB - 2, za, 5);
        // upstairs: sofas facing the windows, the plants, a bar cart, the board
        int u = LUP + 1;
        for (int x = xa + 3; x <= LX1 - 4; x += 5) {
            for (int[] s : new int[][] {{0, 0}, {1, 1}, {2, 2}}) {
                set(x + s[0], u, zb - 3, "home_sofa_blue_" + (s[1] == 0 ? "r" : s[1] == 1 ? "m" : "l") + "[facing=south]");
            }
            set(x + 1, u, zb - 1, "store_table");
        }
        for (int x = xa + 2; x <= LX1 - 2; x += 6) {
            set(x, u, za + 1, "pk_houseplant[facing=south]");
        }
        cafeTables(xa + 3, za + 3, LX1 - 3, zb - 7, LUP);
        hungSign("claim", 2, ROOF - 2, za + 3, "west", LROOF);
        hungSign("exit", -4, ROOF - 2, zb - 2, "north", LROOF);
        lamps(xa, za, LX1, zb, LUP, 0.3);
    }

    /**
     * The escalator pair (z ez, ez + 1: down, up) and the stair (ez + 3, ez + 4) rising west from the
     * floor at x0 to land on the upper floor's edge, the balustrades between them.
     */
    private void escalators(int x0, int ez) {
        int rise = LUP - UP;
        for (int k = 1; k <= rise; k++) {
            int x = x0 - k, yk = UP + k;
            for (int z = ez - 1; z <= ez + 5; z++) {
                for (int yy = UP + 1; yy < yk; yy++) {
                    set(x, yy, z, "tx_panel");
                }
                if (z == ez - 1 || z == ez + 2 || z == ez + 5) {
                    set(x, yk, z, "tx_panel");
                    set(x, yk + 1, z, "tx_glass_rail[axis=x]");
                    continue;
                }
                boolean esc = z <= ez + 1;
                set(x, yk, z, esc ? "zomboidcraft:escalator[facing=west,run=" + (z == ez ? "down" : "up") + "]" : "stairs_concrete[facing=west]");
                if (esc) {
                    set(x, yk + 1, z, "tx_esc_side_" + (z == ez + 1 ? "l" : "r") + "[facing=west]");
                }
                for (int yy = yk + (esc ? 2 : 1); yy <= yk + Builders.HEADROOM && yy < LROOF; yy++) {
                    set(x, yy, z, "air");
                }
            }
        }
        for (int z = ez; z <= ez + 1; z++) {
            String hand = z == ez + 1 ? "l" : "r";
            set(x0, UP, z, "tx_esc_plate[facing=west]");
            set(x0, UP + 1, z, "tx_esc_newel_" + hand + "[facing=east]");
            set(x0 - rise - 1, LUP, z, "tx_esc_plate[facing=east]");
            set(x0 - rise - 1, LUP + 1, z, "tx_esc_newel_" + hand + "[facing=west]");
        }
        for (int z = ez - 1; z <= ez + 5; z++) {
            set(x0 - rise - 1, LUP + 1, z, z == ez || z == ez + 1 ? get(x0 - rise - 1, LUP + 1, z) : "air");
        }
    }

    /** The baggage hall east of the lobby: two carousels with their feeds through the back wall, the claim signs, the carts, the bags left. */
    private void baggage() {
        int y = UP + 1, za = Z1 + 1, zb = ZL - 1;
        for (int x = XB; x < XE; x++) {
            for (int z = za; z <= zb; z++) {
                set(x, UP, z, chance() < 0.1 ? "tx_terrazzo_b" : "tx_terrazzo");
            }
        }
        for (int z = za; z <= zb; z++) {
            boolean way = z >= -326 && z <= -322 || z >= -310 && z <= -305;
            for (int yy = y; yy < LROOF; yy++) {
                set(XB - 1, yy, z, way && yy <= y + 3 ? "air" : yy <= y + 3 ? (Math.floorMod(z, 4) == 0 ? "wf_steel" : "tx_glass[axis=z]") : "wf_panel");
            }
        }
        int n = 1;
        for (int cx : new int[] {21, 27}) {
            for (int z = za + 4; z <= zb - 4; z++) {
                boolean end = z == za + 4 || z == zb - 4;
                for (int x = cx - 1; x <= cx + 1; x += 2) {
                    set(x, y, z, end ? "ap_carousel_end[facing=" + (z == za + 4 ? "north" : "south") + "]"
                            : (chance() < 0.3 ? "ap_carousel_bags" : "ap_carousel") + "[axis=z]");
                }
                set(cx, y, z, end ? "ap_carousel_end[facing=" + (z == za + 4 ? "north" : "south") + "]" : "stone_trim");
            }
            set(cx, y, za + 3, "ap_carousel_feed[facing=north]");
            set(cx, y + 3, za + 8, "ap_claim_" + n + "[facing=south]");
            rods(cx, y + 4, za + 8, LROOF);
            n++;
        }
        for (int k = 0; k < 6; k++) {
            set(XB + 1 + rng.nextInt(3), y, za + 2 + rng.nextInt(28), "trade_luggage_cart[facing=" + dir() + "]");
        }
        for (int k = 0; k < 12; k++) {
            int x = XB + rng.nextInt(XE - XB), z = za + rng.nextInt(zb - za);
            if (empty(x, y, z)) {
                set(x, y, z, bag());
            }
        }
    }

    // ------------------------------------------------------------------ under the floor

    /**
     * The bag hall under the main floor: the belts down its middle from the main hall's bag room,
     * the carts, the crew's rooms in the west, the roller doors to the tarmac and the apron, the
     * door out under the jet bridge.
     */
    private void under() {
        int y = 1;
        // the main hall's bag room opened through to it in two places
        for (int x : new int[] {-98, -60}) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int yy = 1; yy <= 3; yy++) {
                    set(x + dx, yy, Z1, "air");
                }
            }
            Doors.pair(p, rng, x, 1, Z1, Doors.STEEL, "south");
        }
        // the belts down the hall, the carts, the lights in the slab's underside
        for (int bz : new int[] {-316, -296}) {
            for (int x = -100; x <= -46; x++) {
                set(x, y, bz, (chance() < 0.2 ? "ap_belt_bags" : "ap_belt") + "[axis=x]");
            }
        }
        for (int k = 0; k < 6; k++) {
            int x = -100 + rng.nextInt(50), z = -312 + k * 3;
            set(x, y, z, "rp_dolly_front" + (chance() < 0.6 ? "_bags" : "") + "[facing=west]");
            set(x + 1, y, z, "rp_dolly_rear" + (chance() < 0.6 ? "_bags" : "") + "[facing=west]");
        }
        set(-80, y, -284, "rp_tug_front[facing=north]");
        set(-80, y, -283, "rp_tug_rear[facing=north]");
        // the crew's rooms in the west part
        for (int x = -139; x <= -104; x++) {
            set(x, y, -300, "tx_wall");
            for (int yy = 2; yy < UP; yy++) {
                set(x, yy, -300, "tx_wall");
            }
        }
        for (int z = -330; z <= -237; z++) {
            for (int yy = 1; yy < UP; yy++) {
                if (z != -300 && (z < -290 || z > -287)) {
                    set(-104, yy, z, "tx_wall");
                }
            }
        }
        for (int z = -328; z <= -302; z += 2) {
            set(-139, y, z, "fit_locker_lo[facing=east]");
            set(-139, y + 1, z, "fit_locker_hi[facing=east]");
        }
        set(-125, y, -315, "fit_mess_table[axis=z]");
        set(-126, y, -315, "store_chair[facing=east]");
        set(-124, y, -314, "trade_folding_chair_tipped[facing=west]");
        set(-125, y, -320, "fit_mess_table[axis=z]");
        set(-110, y, -302, "home_fridge[facing=north]");
        // the doors out: roller doors in the south wall, the steel door under the jet bridge
        for (int x = -100; x <= -64; x += 12) {
            for (int dx = 0; dx < 5; dx++) {
                for (int yy = 1; yy <= 4; yy++) {
                    set(x + dx, yy, ZS, yy <= (chance() < 0.3 ? 1 : 4) ? (yy == 4 || chance() < 0.85 ? "pt_shutter_steel" : "air") : "air");
                }
            }
        }
        for (int yy = 1; yy <= 2; yy++) {
            set(XH, yy, -239, "air");
            set(XH, yy, -264, "air");
        }
        door(XH, 0, -239, Doors.STEEL, "east");
        door(XH, 0, -264, Doors.STEEL, "east");
        // the ceiling's lights
        for (int x = X0 + 4; x < XH; x += 6) {
            for (int z = Z1 + 4; z < ZS; z += 6) {
                if (empty(x, UP - 1, z)) {
                    set(x, UP - 1, z, power && chance() < 0.25 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        for (int x = XH + 3; x < XE; x += 6) {
            for (int z = Z1 + 4; z < ZL; z += 6) {
                if (empty(x, UP - 1, z)) {
                    set(x, UP - 1, z, power && chance() < 0.25 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        // the lobby's ground floor: the roller doors to the tarmac
        for (int x = -30; x <= 20; x += 14) {
            for (int dx = 0; dx < 5; dx++) {
                for (int yy = 1; yy <= 4; yy++) {
                    set(x + dx, yy, ZL, yy <= (chance() < 0.4 ? 1 : 4) ? "pt_shutter_steel" : "air");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the tarmac

    /**
     * The tarmac between the lobby and the plane: the apron's surface, the lead-in line and the stop
     * bar, the gate number painted in its box, the containers in stacks, the light masts, the police
     * line along the east edge with the cruisers inside it.
     */
    private void tarmac() {
        for (int x = XH + 1; x <= XE + 12; x++) {
            for (int z = ZL + 1; z <= BZ1; z++) {
                set(x, 0, z, chance() < 0.04 ? "af_apron_j" : "af_apron");
            }
        }
        // the lead-in line from the east to the nose wheel, the stop bar across it
        for (int x = NX + 6; x <= XE + 12; x++) {
            set(x, 0, PZ, "af_paint_y");
        }
        for (int z = PZ - 2; z <= PZ + 2; z++) {
            set(NX + 5, 0, z, "af_paint_y");
        }
        // the gate's number in its box on the apron north of the plane, read from the plane: a 1 seven high
        int gx = NX + 40, gz = -280;
        for (int x = gx; x <= gx + 8; x++) {
            set(x, 0, gz, "af_paint_y");
            set(x, 0, gz + 12, "af_paint_y");
        }
        for (int z = gz; z <= gz + 12; z++) {
            set(gx, 0, z, "af_paint_y");
            set(gx + 8, 0, z, "af_paint_y");
        }
        for (int z = gz + 3; z <= gz + 9; z++) {
            set(gx + 4, 0, z, "af_paint");
        }
        set(gx + 3, 0, gz + 4, "af_paint");
        set(gx + 2, 0, gz + 5, "af_paint");
        for (int x = gx + 3; x <= gx + 5; x++) {
            set(x, 0, gz + 9, "af_paint");
        }
        // the service road's dashed edge across the tarmac, the hatching under the bridge's cab
        for (int x = XH + 1; x <= XE + 10; x++) {
            if (Math.floorMod(x, 4) < 2) {
                set(x, 0, -266, "af_paint");
            }
        }
        for (int x = NX + 6; x <= NX + 14; x++) {
            for (int z = -241; z <= -236; z++) {
                if (Math.floorMod(x + z, 3) == 0) {
                    set(x, 0, z, "af_paint");
                }
            }
        }
        // the containers: two stacks two high, a row along the control room, a fallen one
        for (int[] s : new int[][] {{-12, -292, 3, 2}, {16, -284, 3, 3}, {-6, -270, 2, 2}, {8, -265, 4, 3}, {22, -270, 2, 2}}) {
            for (int dx = 0; dx < s[2]; dx++) {
                for (int dz = 0; dz < s[3]; dz++) {
                    int h = chance() < 0.6 ? 2 : 1;
                    for (int yy = 1; yy <= h; yy++) {
                        set(s[0] + dx, yy, s[1] + dz, (yy == 1 && chance() < 0.15 ? "rp_uld_open" : "rp_uld") + "[facing=" + (chance() < 0.7 ? "south" : "east") + "]");
                    }
                }
            }
        }
        set(-3, 1, -268, "rp_uld_spill[facing=south]");
        for (int x = -18; x <= -12; x++) {
            if (chance() < 0.6) {
                set(x, 1, -278, "rp_uld[facing=south]");
            }
        }
        for (int x = -8; x <= 12; x++) {
            if (chance() < 0.55) {
                set(x, 1, ZL + 2, "rp_uld[facing=north]");
            }
        }
        // a train parked along the control room, a belt loader waiting
        set(-16, 1, -297, "rp_tug_front[facing=west]");
        set(-15, 1, -297, "rp_tug_rear[facing=west]");
        for (int k = 0; k < 2; k++) {
            set(-13 + k * 3, 1, -297, "rp_dolly_front_bags[facing=west]");
            set(-12 + k * 3, 1, -297, "rp_dolly_rear_bags[facing=west]");
        }
        set(24, 1, -293, "rp_bl_0[facing=north]");
        set(24, 1, -294, "rp_bl_1[facing=north]");
        set(24, 2, -295, "rp_bl_2[facing=north]");
        set(24, 2, -296, "rp_bl_3[facing=north]");
        for (int k = 0; k < 28; k++) {
            int x = -16 + rng.nextInt(44), z = -296 + rng.nextInt(38);
            if (empty(x, 1, z) && empty(x, 2, z)) {
                set(x, 1, z, bag());
            }
        }
        // the light masts at the corners
        for (int[] m : new int[][] {{XH + 2, ZL + 2}, {XE - 1, ZL + 2}}) {
            for (int yy = 1; yy <= 14; yy++) {
                set(m[0], yy, m[1], "streetlight_pole");
            }
            set(m[0], 15, m[1], power ? "cp_flood_lit[facing=south]" : "cp_flood[facing=south]");
        }
        // the police line: barriers along the east edge, the tape between, the cruisers inside
        for (int z = ZL + 2; z <= -228; z++) {
            int x = XE + 4;
            if (Math.floorMod(z, 3) == 0) {
                set(x, 1, z, chance() < 0.15 ? "st_barrier_down[facing=east]" : "st_barrier[facing=east]");
            } else if (chance() < 0.8) {
                set(x, 1, z, "hs_tape[facing=east]");
            }
        }
        p.vehicle(XE + 8, 1, -290, 20f, "police_sedan,police_suv", rng.nextLong());
        p.vehicle(XE + 9, 1, -272, -10f, "police_sedan,police_suv", rng.nextLong());
        p.vehicle(XE + 7, 1, -240, 180f, "police_sedan,police_suv", rng.nextLong());
        for (int z = ZL + 4; z <= -232; z += 9) {
            set(XE + 2, 1, z, "st_cone");
        }
    }

    /**
     * The control-room building on the tarmac, x -36..-20, z -294..-280: the workshop downstairs
     * behind its roller door, the office upstairs behind its windows looking over the tarmac, the
     * outside stair up its east side, the units, the tank and the windsock on its roof.
     */
    private void controlRoom() {
        int x0 = -36, x1 = -20, z0 = -294, z1 = -280, up = 6, top = 12;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, "floor_concrete");
                for (int y = 1; y < top; y++) {
                    String s = "air";
                    if (wall) {
                        String out = z == z1 ? "south" : x == x1 ? "east" : x == x0 ? "west" : "north";
                        boolean win = y >= up + 1 && y <= up + 3 && (z == z1 || x == x1 || x == x0) && Math.floorMod(z == z1 ? x : z, 4) != 0;
                        s = y == up || y == top - 1 ? "ml_precast_band" : win ? glass(out, 0.2) : "ml_precast_b";
                    }
                    set(x, y, z, s);
                }
                if (!wall) {
                    set(x, up, z, "un_slab_vct");
                }
                set(x, top, z, "un_roof");
            }
        }
        b.flat(x0, z0, x1, z1, top, "ml_precast", 1, "ml_precast");
        // the roller door south, the steel door beside it
        for (int x = x0 + 3; x <= x0 + 8; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, z1, y <= (chance() < 0.4 ? 2 : 4) ? "pt_shutter_grey" : "air");
            }
        }
        set(x1 - 3, 1, z1, "air");
        set(x1 - 3, 2, z1, "air");
        door(x1 - 3, 0, z1, Doors.STEEL, "south");
        // downstairs: the workshop, a tug in, the shelves, the bench
        set(x0 + 5, 1, z0 + 5, "rp_tug_front[facing=south]");
        set(x0 + 5, 1, z0 + 4, "rp_tug_rear[facing=south]");
        for (int x = x0 + 1; x < x1; x += 2) {
            set(x, 1, z0 + 1, "store_shelf_empty[facing=south]");
        }
        set(x1 - 2, 1, z0 + 4, "pw_bench[facing=west]");
        set(x1 - 2, 1, z0 + 6, "rp_gpu[facing=west]");
        for (int x = x0 + 2; x < x1; x += 5) {
            for (int z = z0 + 3; z < z1; z += 5) {
                set(x, up - 1, z, power && chance() < 0.3 ? "ceiling_light" : "ceiling_light_off");
            }
        }
        // the inside stair from the workshop to the office, along the north wall
        for (int k = 1; k <= up - 1; k++) {
            set(x0 + 1 + k, k, z0 + 2, "stairs_concrete[facing=east]");
            if (k >= 2) {
                set(x0 + 1 + k, up, z0 + 2, "air");
            }
        }
        // upstairs: the consoles along the windows, the desks, the radio, the board
        int u = up + 1;
        for (int x = x0 + 2; x < x1 - 1; x++) {
            set(x, u, z1 - 1, (power && chance() < 0.5 ? "fit_console_on" : "fit_console") + "[facing=south]");
        }
        for (int z = z0 + 6; z < z1 - 2; z++) {
            set(x0 + 1, u, z, (power && chance() < 0.4 ? "hs_monitor_on" : "hs_monitor") + "[facing=east]");
        }
        set(x0 + 6, u, z0 + 6, "home_desk_pc[facing=south]");
        set(x0 + 10, u, z0 + 6, "home_desk_pc[facing=south]");
        set(x0 + 6, u, z0 + 7, "store_chair[facing=north]");
        set(x0 + 10, u, z0 + 8, "trade_folding_chair_tipped[facing=west]");
        set(x0 + 8, u, z0 + 1, "of_whiteboard[facing=south]");
        set(x0 + 12, u + 2, z0, "ap_notice_evac[facing=south]");
        lamps(x0, z0, x1, z1, top, 0.3);
        // the outside stair up the east side, rising north to the office's door
        for (int k = 1; k <= up; k++) {
            int z = z1 - 1 - k, yk = k;
            for (int x = x1 + 1; x <= x1 + 2; x++) {
                set(x, yk, z, "stairs_concrete[facing=north]");
                for (int yy = 1; yy < yk; yy++) {
                    set(x, yy, z, "ml_precast");
                }
            }
            set(x1 + 3, yk + 1, z, "railing");
        }
        for (int x = x1 + 1; x <= x1 + 3; x++) {
            for (int z = z1 - up - 3; z <= z1 - up - 2; z++) {
                set(x, up, z, "fit_deckplate");
                for (int yy = 1; yy < up; yy++) {
                    set(x, yy, z, x == x1 + 3 ? "streetlight_pole" : "air");
                }
            }
            set(x, up + 1, z1 - up - 4, "railing");
        }
        set(x1 + 3, up + 1, z1 - up - 3, "railing");
        set(x1, up + 1, z1 - up - 3, "air");
        set(x1, up + 2, z1 - up - 3, "air");
        door(x1, up, z1 - up - 3, Doors.STEEL, "east");
        // the roof: the units, the tank on its stand, the windsock's mast
        set(x0 + 3, top + 1, z0 + 3, "bd_hvac_a[facing=south]");
        set(x0 + 6, top + 1, z0 + 3, "bd_hvac_b[facing=south]");
        set(x1 - 4, top + 1, z0 + 4, "water_tank");
        set(x1 - 4, top + 2, z0 + 4, "water_tank_top");
        for (int y = top + 1; y <= top + 5; y++) {
            set(x0 + 2, y, z1 - 2, "af_windsock_mast");
        }
        set(x0 + 2, top + 6, z1 - 2, "af_windsock[facing=" + dir() + "]");
    }

    // ------------------------------------------------------------------ columns, lights, roof

    /** The clad columns standing through the main floor where nothing else is. */
    private void columns() {
        int[][] at = {{-104, -324}, {-104, -312}, {-104, -276}, {-104, -264}, {-76, -318}, {-76, -306}, {-90, -288},
                      {-82, -252}, {-96, -252}, {-52, -318}, {-52, -260}};
        for (int[] c : at) {
            if (!empty(c[0], UP + 1, c[1])) {
                continue;
            }
            for (int y = UP + 1; y < ROOF; y++) {
                set(c[0], y, c[1], y == UP + 1 ? "tx_panel_lo_s" : y == ROOF - 1 ? "tx_panel_hi_s" : "tx_panel_mid_s");
            }
        }
        for (int x : new int[] {-28, -16}) {
            for (int z : new int[] {-318, -306}) {
                for (int y = UP + 1; y < LUP; y++) {
                    if (empty(x, y, z)) {
                        set(x, y, z, y == UP + 1 ? "tx_panel_lo_s" : y == LUP - 1 ? "tx_panel_hi_s" : "tx_panel_mid_s");
                    }
                }
                for (int y = LUP + 1; y < LROOF; y++) {
                    if (empty(x, y, z)) {
                        set(x, y, z, y == LUP + 1 ? "tx_panel_lo_s" : y == LROOF - 1 ? "tx_panel_hi_s" : "tx_panel_mid_s");
                    }
                }
            }
        }
    }

    /** Pendants down from the roof over the open floor. */
    private void lights() {
        for (int x = X0 + 4; x < XE; x += 8) {
            for (int z = Z1 + 4; z < ZS; z += 8) {
                int top = inLobby(x, z) && x > XH ? LROOF : ROOF;
                if (!inWest(x, z) && !inLobby(x, z) || fromAtrium(x, z) < AR + 1 || inKiosk(x, z)) {
                    continue;
                }
                if (empty(x, top - 1, z) && empty(x, top - 2, z) && empty(x, ROOF - 4, z)) {
                    set(x, top - 1, z, power && chance() < 0.3 ? "tx_pendant" : "tx_pendant_off");
                }
            }
        }
    }

    /**
     * The roofs: the west block's flat roof with the food court's strip lights and the atrium's
     * round lantern (a glass drum, its ribs, the glass over it), the lobby's higher roof.
     */
    private void roof() {
        for (int x = X0; x <= XH; x++) {
            for (int z = Z1 + 1; z <= ZS; z++) {
                set(x, ROOF, z, "un_roof");
            }
        }
        b.flat(X0, Z1 + 1, XH, ZS, ROOF, "ml_precast", 1, "ml_precast");
        for (int x = XH; x <= XE; x++) {
            for (int z = Z1; z <= ZL; z++) {
                set(x, LROOF, z, "un_roof");
            }
        }
        b.flat(XH, Z1, XE, ZL, LROOF, "ml_precast", 1, "ml_precast");
        // the food court's strip lights, the roof's kit cleared off them
        for (int x = -100; x <= -73; x++) {
            for (int z = Z1 + 3; z < -303; z += 4) {
                if (inKiosk(x, z)) {
                    continue;
                }
                set(x, ROOF, z, chance() < 0.08 ? "us_skylight_broken" : "us_skylight");
                for (int y = ROOF + 1; y <= ROOF + 3; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        // the lantern: the drum of glass round the well, its posts, the ribs over it to the middle, the glass between
        int drum = 4;
        for (int x = AX - AR - 3; x <= AX + AR + 3; x++) {
            for (int z = AZ - AR - 3; z <= AZ + AR + 3; z++) {
                double d = fromAtrium(x, z);
                if (d > AR + 2) {
                    continue;
                }
                for (int y = ROOF + 1; y <= ROOF + drum + 2; y++) {
                    set(x, y, z, "air");
                }
                if (d > AR + 0.6) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(z - AZ, x - AX));
                double off = Math.toRadians(ang - Math.round(ang / 30) * 30.0);
                boolean rib = d * Math.abs(Math.sin(off)) < 0.5 || d < 1.5;
                if (d > AR - 0.4) {
                    for (int y = ROOF; y < ROOF + drum; y++) {
                        set(x, y, z, d * Math.abs(Math.sin(off)) < 0.6 ? "tx_concrete" : "tx_roof_glass");
                    }
                    set(x, ROOF + drum, z, "ml_precast_band");
                } else {
                    set(x, ROOF, z, "air");
                    set(x, ROOF + drum, z, rib ? "tx_concrete" : "tx_roof_glass");
                }
            }
        }
    }

    /** What the evacuation left: luggage dropped, the mess on the floors, the dead at the gate. */
    private void aftermath() {
        scatter(X0 + 1, Z1 + 1, XH - 1, ZS - 1, UP, 0.008);
        scatter(XH + 1, Z1 + 1, XE - 1, ZL - 1, UP, 0.008);
        mess(X0 + 1, Z1 + 1, XH - 1, ZS - 1, UP, 0.018);
        mess(XH + 1, Z1 + 1, XE - 1, ZL - 1, UP, 0.015);
        mess(XH + 1, Z1 + 1, LX1, ZL - 1, LUP, 0.012);
        mess(X0 + 1, Z1 + 1, XH - 1, ZS - 1, 0, 0.01);
        for (int k = 0; k < 4; k++) {
            int x = -56 + rng.nextInt(12), z = -249 + rng.nextInt(10);
            if (empty(x, UP + 1, z) && empty(x + 1, UP + 1, z)) {
                set(x, UP + 1, z, "home_corpse_head[facing=west]");
                set(x + 1, UP + 1, z, "home_corpse_foot[facing=west]");
            }
        }
    }

    private void scatter(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (chance() < density && empty(x, y + 1, z)) {
                    String s = get(x, y, z);
                    if (s != null && !s.equals("air") && !s.startsWith("stairs") && !s.startsWith("zomboidcraft:")) {
                        set(x, y + 1, z, chance() < 0.15 ? "ap_cart[facing=" + dir() + "]" : bag());
                    }
                }
            }
        }
    }
}
