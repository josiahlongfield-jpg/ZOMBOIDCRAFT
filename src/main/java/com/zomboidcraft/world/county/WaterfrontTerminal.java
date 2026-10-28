package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Waterfront.GZ;
import static com.zomboidcraft.world.county.Waterfront.SEA;
import static com.zomboidcraft.world.county.Waterfront.SEAWALL;
import static com.zomboidcraft.world.county.Waterfront.TX0;
import static com.zomboidcraft.world.county.Waterfront.TX1;
import static com.zomboidcraft.world.county.Waterfront.TZ0;
import static com.zomboidcraft.world.county.Waterfront.TZ1;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;

/**
 * Harbor Landing, the ferry terminal at the Waterfront's north end: a two-storey shed of blue steel
 * and glass, the ticket windows and the departures board, the waiting hall's rows of seats, the
 * coffee bar, the line's offices and the glass lounge upstairs, and the clock tower at its north-east
 * corner; the Calder Queen tied up alongside, her gangway from the terminal's gate.
 *
 * <p>The evacuation: queue barriers and turnstiles in front of the gate, the Guard's sandbags and
 * table by it, the board still showing the last sailings, the crowd's luggage and cots left where
 * they waited.
 */
final class WaterfrontTerminal {
    /** The upper floor's slab and the roof's. */
    static final int UP = 6, ROOF = 12;
    /** The clock tower (inclusive) and its top. */
    static final int KX0 = 22, KX1 = 28, KZ0 = 1, KZ1 = 7, KTOP = 24;
    /** The stair's shaft (5 by 10, its door north). */
    static final int SX = 7, SZ = 53;

    private final Waterfront c;

    WaterfrontTerminal(Waterfront c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    void build() {
        shell();
        tower();
        ground();
        upstairs();
        roof();
        outside();
        ferry();
    }

    // ------------------------------------------------------------------ the shell

    /** Steel posts every six, cladding to sill height, glass above; the name band at the upper floor's slab. */
    private void shell() {
        for (int x = TX0; x <= TX1; x++) {
            for (int z = TZ0; z <= TZ1; z++) {
                boolean wall = x == TX0 || x == TX1 || z == TZ0 || z == TZ1;
                c.set(x, 0, z, c.chance() < 0.12 ? "tx_terrazzo_b" : "tx_terrazzo");
                for (int y = 1; y < ROOF; y++) {
                    set(x, y, z, wall ? wallAt(x, y, z) : y == UP ? "un_slab_vct" : "air");
                }
                set(x, ROOF, z, "un_roof");
                if (wall) {
                    set(x, ROOF + 1, z, "wf_panel");
                }
            }
        }
        c.lamps(TX0 + 1, TZ0 + 1, TX1 - 1, TZ1 - 1, UP, 0.3);
        c.lamps(TX0 + 1, TZ0 + 1, TX1 - 1, TZ1 - 1, ROOF, 0.3);
        // the doors: two pairs from the avenue, the gate's pair to the promenade and the ferry
        for (int z : new int[] {33, 39}) {
            set(TX0, 2, z, "wf_steel");
            set(TX0, 2, z + 1, "wf_steel");
            c.doors(TX0, 0, z, "shop_door", "west");
        }
        set(TX1, 2, GZ, "wf_steel");
        set(TX1, 2, GZ + 1, "wf_steel");
        c.doors(TX1, 0, GZ, "shop_door", "east");
        // the name along both long sides, the line's board over the gate
        int nz = (TZ0 + TZ1) / 2 - 7;
        c.row("wf_name_", 14, TX0 - 1, UP, nz, "west", 0.05);
        c.row("wf_name_", 14, TX1 + 1, UP, nz, "east", 0.05);
        c.row("wf_ferry_", 3, TX1 + 1, 4, GZ - 1, "east", 0);
        c.row("wf_evac_", 6, TX1 + 1, UP + 2, GZ - 2, "east", 0.1);
    }

    /** What a wall cell is: the posts and the corners steel, cladding at the foot and the slab, glass between. */
    private String wallAt(int x, int y, int z) {
        boolean longWall = x == TX0 || x == TX1;
        int along = longWall ? z - TZ0 : x - TX0;
        boolean post = along % 6 == 0 || (x == TX0 || x == TX1) && (z == TZ0 || z == TZ1);
        if (post) {
            return "wf_steel";
        }
        if (y == 1 || y == UP || y == ROOF - 1 && x == TX0) {
            return c.chance() < 0.3 ? "wf_panel_b" : "wf_panel";
        }
        // the water side's upper floor all glass; the avenue side's a ribbon of windows
        String out = x == TX0 ? "west" : x == TX1 ? "east" : z == TZ0 ? "north" : "south";
        if (x == TX0 && y > UP && y != UP + 2 && y != UP + 3) {
            return "wf_panel";
        }
        double r = c.chance();
        return (r < 0.12 + c.b.damage * 0.2 ? "wf_glass_broken" : c.power && r < 0.32 && y > UP ? "wf_glass_lit" : "wf_glass") + "[facing=" + out + "]";
    }

    /**
     * The clock tower at the north-east corner: a shaft of cladding on steel corners, the clocks on
     * three faces under its cornice, the open lantern above, the beacon on its mast.
     */
    private void tower() {
        for (int y = 1; y <= KTOP; y++) {
            for (int x = KX0; x <= KX1; x++) {
                for (int z = KZ0; z <= KZ1; z++) {
                    boolean edge = x == KX0 || x == KX1 || z == KZ0 || z == KZ1;
                    boolean corner = (x == KX0 || x == KX1) && (z == KZ0 || z == KZ1);
                    if (y > KTOP - 3) {
                        // the lantern: corners only, open between
                        set(x, y, z, corner ? "wf_steel" : "air");
                    } else if (edge) {
                        set(x, y, z, corner || y % 6 == 0 ? "wf_steel" : y > 16 ? "wf_panel" : c.chance() < 0.3 ? "wf_panel_b" : "wf_panel");
                    } else {
                        set(x, y, z, "air");
                    }
                }
            }
        }
        for (int x = KX0; x <= KX1; x++) {
            for (int z = KZ0; z <= KZ1; z++) {
                set(x, KTOP - 3, z, "wf_steel");
                set(x, KTOP + 1, z, "cv_copper");
            }
        }
        for (int x = KX0 + 1; x <= KX1 - 1; x++) {
            for (int z = KZ0 + 1; z <= KZ1 - 1; z++) {
                set(x, KTOP + 2, z, "cv_copper_slab");
            }
        }
        for (int y = KTOP + 3; y <= KTOP + 7; y++) {
            set((KX0 + KX1) / 2, y, (KZ0 + KZ1) / 2, "vessel_mast");
        }
        set((KX0 + KX1) / 2, KTOP + 8, (KZ0 + KZ1) / 2, "vessel_light_red");
        // the clocks: north, east and west, their tops under the cornice
        boolean lit = c.power && c.chance() < 0.5;
        String dial = lit ? "cv_dial_lit_" : "cv_dial_";
        int top = KTOP - 5;
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                String tile = dial + (j * 4 + i);
                int y = top - j;
                set(KX1 - 1 - i, y, KZ0 - 1, tile + "[facing=north]");
                set(KX1 + 1, y, KZ1 - 1 - i, tile + "[facing=east]");
                set(KX0 - 1, y, KZ0 + 1 + i, tile + "[facing=west]");
            }
        }
    }

    // ------------------------------------------------------------------ the ground floor

    /**
     * The ticket office behind its windows across the north end, the board over them; the waiting
     * hall's seats either side of the way through from the avenue doors to the gate; the queue,
     * the turnstiles and the Guard's post at the gate; the coffee bar and the restrooms at the south end.
     */
    private void ground() {
        // the ticket office
        int tz = TZ0 + 12;
        c.partX(TX0 + 1, TX1 - 1, tz, 0, UP, "wf_panel", "plaster_cream");
        for (int x = TX0 + 3; x <= TX0 + 11; x++) {
            set(x, 1, tz, "wf_ticket[facing=south]");
        }
        c.picture("wf_dep_", 4, 2, TX0 + 5, 3, tz + 1, "south");
        c.sign("tickets", TX0 + 13, 3, tz + 1, "south");
        c.door(TX1 - 3, 0, tz, "flat_door", "south");
        for (int x = TX0 + 3; x <= TX0 + 11; x += 2) {
            set(x, 1, tz - 2, "office_chair[facing=south]");
        }
        set(TX0 + 1, 1, TZ0 + 2, "store_safe[facing=east]");
        set(TX0 + 1, 1, TZ0 + 4, "filing_cabinet[facing=east]");
        set(TX0 + 1, 1, TZ0 + 5, "filing_cabinet[facing=east]");
        set(TX0 + 6, 1, TZ0 + 4, "desk[facing=north]");
        set(TX0 + 9, 1, TZ0 + 4, "desk[facing=north]");
        set(TX1 - 5, 1, TZ0 + 3, "fit_chart_table");
        c.mess(TX0 + 1, TZ0 + 1, TX1 - 1, tz - 1, 0, 0.1);
        // the waiting hall's seats: two blocks of back-to-back rows, the way through between them
        for (int[] blk : new int[][] {{tz + 4, GZ - 4}, {GZ + 6, SZ - 3}}) {
            for (int z = blk[0]; z + 1 <= blk[1]; z += 4) {
                for (int x = TX0 + 4; x <= TX1 - 6; x++) {
                    if (x == TX0 + 10 || x == TX0 + 11) {
                        continue;  // the aisle
                    }
                    set(x, 1, z, c.chance() < 0.04 ? "air" : "wf_seats[facing=north]");
                    set(x, 1, z + 1, c.chance() < 0.04 ? "air" : "wf_seats[facing=south]");
                }
            }
        }
        // where they slept: cots and bags along the walls
        for (int z = tz + 3; z < SZ - 2; z += 3) {
            if (c.chance() < 0.5 && Math.abs(z - GZ) > 5) {
                set(TX0 + 1, 1, z, "sc_cot_head[facing=north]");
                set(TX0 + 1, 1, z + 1, "sc_cot_foot[facing=north]");
            }
            if (c.chance() < 0.4 && Math.abs(z - GZ) > 5) {
                set(TX1 - 1, 1, z, "wf_bags_" + c.any("a", "b", "c"));
            }
        }
        // the queue to the gate: barriers down both sides of the way, turnstiles across it
        for (int x = TX0 + 8; x <= TX1 - 4; x++) {
            set(x, 1, GZ - 2, x % 4 == 0 ? "wf_queue_post" : "wf_queue[axis=x]");
            set(x, 1, GZ + 3, x % 4 == 0 ? "wf_queue_post" : "wf_queue[axis=x]");
        }
        for (int z = GZ - 1; z <= GZ + 2; z += 2) {
            set(TX1 - 4, 1, z, "wf_turnstile[facing=east]");
        }
        c.sign("line", TX0 + 12, 3, GZ - 3, "north");
        set(TX1 - 2, 1, GZ - 3, "trade_banquet_table");
        set(TX1 - 2, 1, GZ - 4, "trade_folding_chair[facing=south]");
        set(TX1 - 1, 1, GZ - 2, "sandbags");
        set(TX1 - 1, 1, GZ + 3, "sandbags");
        set(TX1 - 1, 2, GZ + 3, "sandbags");
        c.sign("gate", TX1 - 1 - 0, 4, GZ + 0, "west");
        // the stair to the upper floor and the roof, the restrooms beside it, the coffee bar
        c.stair(SX, SZ, false, new int[] {0, UP, ROOF}, "wf_panel", "un_roof");
        int rx = SX + 6;
        c.partX(rx, rx + 6, SZ + 1, 0, UP, "wf_panel", "plaster_cream");
        c.partZ(rx, SZ + 1, TZ1 - 1, 0, UP, "wf_panel", "plaster_cream");
        c.door(rx + 3, 0, SZ + 1, "flat_door", "north");
        c.sign("rest", rx + 1, 3, SZ, "north");
        for (int z = SZ + 3; z <= TZ1 - 2; z += 2) {
            set(rx + 1, 1, z, "fit_toilet_stall[facing=east]");
            set(rx + 6, 1, z, "hs_sink[facing=west]");
        }
        int kx = rx + 8;
        for (int x = kx; x <= TX1 - 1; x++) {
            set(x, 1, SZ + 2, x == kx + 2 ? "store_register[facing=north]" : x % 3 == 0 ? "om_coffee[facing=north]" : "store_counter[facing=north]");
            set(x, 1, TZ1 - 1, x % 2 == 0 ? "store_cooler[facing=north]" : "dn_prep[facing=north]");
            if (x % 2 == 0) {
                set(x, 2, TZ1 - 1, "store_cooler_top[facing=north]");
            }
        }
        set(kx, 1, SZ + 5, "air");
        c.mess(TX0 + 1, tz + 1, TX1 - 1, TZ1 - 1, 0, 0.07);
    }

    // ------------------------------------------------------------------ upstairs

    /** The line's offices along the avenue side, the glass lounge along the water, vending and a snack counter. */
    private void upstairs() {
        int ox = TX0 + 9;
        c.partZ(ox, TZ0 + 1, SZ - 2, UP, ROOF - UP, "wf_panel", "plaster_cream");
        for (int z = TZ0 + 10; z < SZ - 2; z += 10) {
            c.partX(TX0 + 1, ox - 1, z, UP, ROOF - UP, "wf_panel", "plaster_cream");
        }
        for (int z = TZ0 + 4; z < SZ - 2; z += 10) {
            c.door(ox, UP, z, "flat_door", "east");
            set(TX0 + 3, UP + 1, z, "desk[facing=east]");
            set(TX0 + 2, UP + 1, z, "office_chair[facing=east]");
            set(TX0 + 1, UP + 1, z + 3, "filing_cabinet[facing=east]");
            if (c.chance() < 0.5) {
                set(TX0 + 5, UP + 1, z + 2, "home_desk_pc[facing=west]");
            }
            c.mess(TX0 + 1, z - 3, ox - 1, z + 5, UP, 0.12);
        }
        // the lounge: seats facing the water
        for (int x = ox + 3; x <= TX1 - 3; x += 3) {
            for (int z = TZ0 + 3; z <= SZ - 6; z++) {
                if (z % 8 == 0) {
                    continue;
                }
                set(x, UP + 1, z, c.chance() < 0.05 ? "air" : "wf_seats[facing=east]");
            }
        }
        for (int z = SZ - 4; z <= SZ - 2; z++) {
            set(TX1 - 1, UP + 1, z, "store_counter[facing=west]");
        }
        set(ox + 1, UP + 1, SZ - 3, "store_vending[facing=east]");
        set(ox + 1, UP + 2, SZ - 3, "store_vending_top[facing=east]");
        c.sign("wait", ox + 1, UP + 4, SZ - 1, "north");
        c.mess(ox + 1, TZ0 + 1, TX1 - 1, SZ - 1, UP, 0.06);
    }

    /** The roof: its parapet, the air handlers, the stair's hut. */
    private void roof() {
        for (int z = TZ0 + 6; z < TZ1 - 6; z += 9) {
            set(TX0 + 14, ROOF + 1, z, "hvac");
            set(TX0 + 15, ROOF + 1, z, "hvac");
        }
    }

    /** The avenue side: a canopy over the drop-off, cars left at the kerb, the aid post's barriers. */
    private void outside() {
        for (int z = 28; z <= 46; z++) {
            set(1, 5, z, "un_roof");
            for (int x = 2; x < TX0; x++) {
                set(x, 5, z, "un_roof");
            }
            if ((z - 28) % 6 == 0) {
                for (int y = 1; y <= 4; y++) {
                    set(1, y, z, "wf_steel");
                }
            }
        }
        for (int z = 6; z < Waterfront.PZ0; z += 7) {
            if (c.chance() < 0.6) {
                c.p.car(3.0, 1, z + 0.5, c.chance() < 0.5 ? 0 : 180, 4.0, 5.2, c.rng.nextLong());
            }
        }
        for (int z = TZ0 + 4; z <= TZ1 - 4; z += 3) {
            if (c.chance() < 0.5) {
                set(TX1 + 3, 1, z, "jersey_barrier[facing=east]");
            }
        }
        // the queue outside the gate: a switchback of barriers on the promenade
        for (int k = 0; k < 4; k++) {
            int z = GZ - 8 + k * 2;
            for (int x = TX1 + 2; x <= SEAWALL - 3; x++) {
                if (k % 2 == 0 ? x == SEAWALL - 3 : x == TX1 + 2) {
                    continue;
                }
                set(x, 1, z, x % 3 == 0 ? "wf_queue_post" : "wf_queue[axis=x]");
            }
        }
        for (int x = TX1 + 2; x < SEAWALL; x++) {
            for (int z = GZ - 9; z <= GZ + 10; z++) {
                if (c.empty(x, 1, z) && c.chance() < 0.08) {
                    set(x, 1, z, "wf_bags_" + c.any("a", "b", "c"));
                }
            }
        }
        c.row("wf_spray_gone_", 4, TX1 + 1, 2, GZ + 6, "east", 0);
        set(SEAWALL - 1, 1, GZ - 3, "vessel_bollard");
        set(SEAWALL - 1, 1, GZ + 6, "vessel_bollard");
    }

    // ------------------------------------------------------------------ the ferry

    /**
     * The Calder Queen alongside the seawall, her bow north: a steel hull in the line's navy, the
     * passenger cabin the length of her main deck, the open upper deck on its roof with the
     * wheelhouse forward and the funnels' exhausts, her name down both sides; the gangway from the
     * terminal's gate to her side door.
     */
    private void ferry() {
        int len = 46, beam = 12;
        // the frame: her x along the lot's -z from the stern at z = 62, her z the lot's x about x = 45
        Placer q = c.p.turned(45, 0, 62, 3);
        int keel = SEA - 4, deck = -2;
        Ships.Hull h = new Ships.Hull(len, beam, keel, deck, 0.22, 0.08);
        int hb = beam / 2 + 1;
        for (int x = 0; x < len; x++) {
            for (int y = keel; y <= deck; y++) {
                for (int z = -hb; z < hb; z++) {
                    if (!h.in(x, y, z)) {
                        continue;
                    }
                    boolean edge = y == keel || !h.in(x + 1, y, z) || !h.in(x - 1, y, z) || !h.in(x, y, z + 1) || !h.in(x, y, z - 1);
                    if (y == deck && !edge) {
                        q.set(x, y, z, "vessel_deck_walk");
                    } else if (edge) {
                        double r = c.chance();
                        q.set(x, y, z, y <= SEA ? (r < 0.2 ? "vessel_bottom_weed" : "vessel_bottom")
                                : r < 0.06 + c.b.damage * 0.1 ? "vessel_hull_navy_rust" : r < 0.15 ? "vessel_hull_navy_seam" : "vessel_hull_navy");
                    } else {
                        q.set(x, y, z, "air");
                    }
                }
            }
        }
        // the rail round her deck's edge, a gap at the side doors
        int door = 62 - GZ - 1;
        for (int x = 0; x < len; x++) {
            for (int z = -hb; z < hb; z++) {
                if (!h.in(x, deck, z)) {
                    continue;
                }
                boolean in = h.in(x, deck, z + 1) && h.in(x, deck, z - 1);
                boolean end = !h.in(x + 1, deck, z) || !h.in(x - 1, deck, z);
                if (!in || end) {
                    if (x >= door && x <= door + 1) {
                        continue;
                    }
                    q.set(x, deck + 1, z, in ? "vessel_rail[axis=z]" : "vessel_rail[axis=x]");
                }
            }
        }
        // the cabin: x 6 .. 36, z -4 .. 3, its roof the upper deck at deck + 4
        int c0 = 6, c1 = 36, top = deck + 4;
        for (int x = c0; x <= c1; x++) {
            for (int z = -4; z <= 3; z++) {
                boolean wall = x == c0 || x == c1 || z == -4 || z == 3;
                for (int y = deck + 1; y < top; y++) {
                    if (!wall) {
                        q.set(x, y, z, "air");
                        continue;
                    }
                    boolean pane = y == deck + 2 && (z == -4 || z == 3) && x % 2 == 0 && x > c0 && x < c1;
                    boolean front = y == deck + 2 && x == c1 && z > -4 && z < 3;
                    String f = z == -4 ? "north" : z == 3 ? "south" : "east";
                    q.set(x, y, z, pane || front ? (c.chance() < 0.15 + c.b.damage * 0.2 ? "vessel_window_broken" : "vessel_window") + "[facing=" + f + "]"
                            : c.chance() < 0.05 ? "vessel_house_rust" : "vessel_house");
                }
                q.set(x, deck, z, "floor_lino");
                q.set(x, top, z, "vessel_deck_walk");
            }
        }
        // the side doors, both sides, by the gangway
        for (int z : new int[] {-4, 3}) {
            for (int x = door; x <= door + 1; x++) {
                q.set(x, deck + 2, z, "vessel_house");
                q.set(x, deck + 3, z, "vessel_house");
            }
            Doors.pair(q, c.rng, door, deck + 1, z, "ship_door", z < 0 ? "north" : "south");
        }
        q.set(c0, deck + 1, 0, "air");
        q.set(c0, deck + 2, 0, "air");
        // inside: rows of seats facing forward, the aisle down the middle, the snack bar aft
        for (int x = c0 + 3; x < c1 - 1; x += 2) {
            if (x >= door - 1 && x <= door + 2) {
                continue;
            }
            for (int z = -3; z <= 2; z++) {
                if (z == 0) {
                    continue;
                }
                q.set(x, deck + 1, z, c.chance() < 0.06 ? "wf_bags_" + c.any("a", "b", "c") : "wf_seats[facing=east]");
            }
        }
        for (int z = -3; z <= 2; z++) {
            if (z != 0) {
                q.set(c0 + 1, deck + 1, z, z < 0 ? "store_counter[facing=east]" : "store_cooler[facing=east]");
            }
        }
        for (int x = c0 + 2; x < c1; x += 4) {
            q.set(x, top - 1, 0, c.power && c.chance() < 0.3 ? "fit_troffer_on" : "fit_troffer");
        }
        // the stair aft up to the upper deck
        for (int k = 0; k < 4; k++) {
            q.set(2 + k, deck + 1 + k, 0, "wf_gangway[facing=east]");
            q.set(2 + k, deck + 1 + k, -1, "wf_gangway[facing=east]");
        }
        // the upper deck: rail round it, benches aft, the life rafts, the exhausts, the wheelhouse forward
        for (int x = c0; x <= c1; x++) {
            for (int z = -4; z <= 3; z++) {
                boolean edge = x == c0 || x == c1 || z == -4 || z == 3;
                if (edge && !(x == c0 && (z == 0 || z == -1))) {
                    q.set(x, top + 1, z, x == c0 || x == c1 ? "vessel_rail[axis=z]" : "vessel_rail[axis=x]");
                }
            }
        }
        for (int x = c0 + 2; x <= c0 + 12; x += 2) {
            q.set(x, top + 1, -2, "st_bench[facing=south]");
            q.set(x, top + 1, 1, "st_bench[facing=north]");
        }
        for (int x = c0 + 15; x <= c1 - 10; x += 3) {
            q.set(x, top + 1, -3, "vessel_liferaft[axis=x]");
            q.set(x, top + 1, 2, "vessel_liferaft[axis=x]");
        }
        q.set(c0 + 14, top + 1, -1, "vessel_exhaust");
        q.set(c0 + 14, top + 2, -1, "vessel_exhaust");
        q.set(c0 + 14, top + 1, 0, "vessel_exhaust");
        q.set(c0 + 14, top + 2, 0, "vessel_exhaust");
        int w0 = c1 - 6;
        for (int x = w0; x <= c1 - 1; x++) {
            for (int z = -3; z <= 2; z++) {
                boolean wall = x == w0 || x == c1 - 1 || z == -3 || z == 2;
                for (int y = top + 1; y <= top + 3; y++) {
                    if (!wall) {
                        q.set(x, y, z, "air");
                        continue;
                    }
                    String f = x == c1 - 1 ? "east" : z == -3 ? "north" : z == 2 ? "south" : "west";
                    q.set(x, y, z, y == top + 2 && x > w0 ? (c.chance() < 0.2 ? "vessel_bridge_broken" : "vessel_bridge") + "[facing=" + f + "]"
                            : "vessel_house");
                }
                q.set(x, top + 4, z, "vessel_roof");
            }
        }
        Doors.abandoned(q, c.rng, w0, top + 1, 0, "ship_door", "west", "left");
        q.set(c1 - 2, top + 1, -1, "ship_console[facing=east]");
        q.set(c1 - 2, top + 1, 0, "ship_console[facing=east]");
        q.set(c1 - 2, top + 1, 1, "ship_telegraph[facing=east]");
        for (int y = top + 5; y <= top + 9; y++) {
            q.set(c1 - 3, y, 0, "vessel_mast");
        }
        q.set(c1 - 3, top + 7, -1, "vessel_radar[axis=z]");
        q.set(c1 - 3, top + 10, 0, "vessel_light_white");
        q.set(c1 - 2, top + 5, 0, "vessel_horn[facing=east]");
        q.set(c1 - 2, top + 5, -3, "vessel_light_red");
        q.set(c1 - 2, top + 5, 2, "vessel_light_green");
        // her name down both sides amidships, two blocks high
        String name = "CALDER QUEEN";
        for (int i = 0; i < name.length(); i++) {
            char ch = Character.toLowerCase(name.charAt(i));
            if (ch == ' ') {
                continue;
            }
            int xn = 20 - i, xs = 9 + i;
            q.set(xn, deck - 1, -hb, "vessel_name_" + ch + "_top[facing=north]");
            q.set(xn, deck - 2, -hb, "vessel_name_" + ch + "_bot[facing=north]");
            q.set(xs, deck - 1, hb - 1, "vessel_name_" + ch + "_top[facing=south]");
            q.set(xs, deck - 2, hb - 1, "vessel_name_" + ch + "_bot[facing=south]");
        }
        // the gangway from the gate down to her side door
        for (int z = GZ; z <= GZ + 1; z++) {
            set(SEAWALL + 1, 0, z, "wf_gangway[facing=west]");
            set(SEAWALL + 2, -1, z, "wf_gangway[facing=west]");
            set(SEAWALL + 1, 1, z, "air");
        }
        c.mess(TX1 + 1, GZ - 1, SEAWALL - 1, GZ + 2, 0, 0.05);
    }
}
