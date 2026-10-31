package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;

/**
 * Ravenrock's ferry (refs/prison and the old island-prison boats: a black hull, a white house, a
 * caged hold for the men going over): drawn in its own frame, x from the stern (0) to the bow,
 * z across her about 0 (her port side at -z), sea the water's top block. On the main deck the
 * saloon forward for the staff and visitors with its benches, aft of it the secure compartment
 * behind bars with the steel bench bolted along it; the wheelhouse on the saloon's roof with the
 * open upper deck behind it, the mast, the horn and the radar; the vehicle deck open at the bow
 * under the loading ramp, the name down both sides. The side doors at {@link #DOOR} (and DOOR + 1)
 * take the gangway.
 */
final class PrisonFerry {
    static final int LEN = 34, BEAM = 10;
    /** Where the side doors are along her. */
    static final int DOOR = 14;

    private final Grounds g;
    private final Placer q;
    private final int sea, keel, deck;

    PrisonFerry(Grounds g, Placer q, int sea) {
        this.g = g;
        this.q = q;
        this.sea = sea;
        this.keel = sea - 3;
        this.deck = sea + 2;
    }

    /** Her deck's level in the frame. */
    int deck() {
        return deck;
    }

    void build(String name) {
        Ships.Hull h = new Ships.Hull(LEN, BEAM, keel, deck, 0.2, 0.1);
        int hb = BEAM / 2 + 1;
        for (int x = 0; x < LEN; x++) {
            for (int y = keel; y <= deck; y++) {
                for (int z = -hb; z < hb; z++) {
                    if (!h.in(x, y, z)) {
                        continue;
                    }
                    boolean edge = y == keel || !h.in(x + 1, y, z) || !h.in(x - 1, y, z) || !h.in(x, y, z + 1) || !h.in(x, y, z - 1);
                    if (y == deck && !edge) {
                        q.set(x, y, z, x >= 24 ? "vessel_deck_grey" : "vessel_deck_walk");
                    } else if (edge) {
                        double r = g.chance();
                        q.set(x, y, z, y <= sea ? (r < 0.25 ? "vessel_bottom_weed" : "vessel_bottom")
                                : y == deck - 1 && r < 0.5 ? "vessel_hull_black_seam"
                                : r < 0.08 + g.b.damage * 0.1 ? "vessel_hull_black_rust" : r < 0.14 ? "vessel_hull_black_scrape" : "vessel_hull_black");
                    } else {
                        q.set(x, y, z, "air");
                    }
                }
            }
        }
        // the rail round her deck's edge, gaps at the side doors and the bow ramp
        for (int x = 0; x < LEN; x++) {
            for (int z = -hb; z < hb; z++) {
                if (!h.in(x, deck, z)) {
                    continue;
                }
                boolean in = h.in(x, deck, z + 1) && h.in(x, deck, z - 1);
                boolean end = !h.in(x + 1, deck, z) || !h.in(x - 1, deck, z);
                if ((!in || end) && !(x >= DOOR && x <= DOOR + 1) && !(x >= LEN - 3 && Math.abs(z + 0.5) < 2.5)) {
                    q.set(x, deck + 1, z, in ? "vessel_rail[axis=z]" : "vessel_rail[axis=x]");
                }
            }
        }
        house();
        upper();
        foredeck();
        lettering(name, hb);
        // fenders slung over both sides, the mooring bitts fore and aft
        for (int x = 6; x < LEN - 6; x += 5) {
            q.set(x, deck - 1, -hb, "wf_fender[facing=north]");
            q.set(x, deck - 1, hb - 1, "wf_fender[facing=south]");
        }
        for (int x : new int[] {2, LEN - 5}) {
            q.set(x, deck + 1, -2, "vessel_bollard");
            q.set(x, deck + 1, 1, "vessel_bollard");
        }
        q.set(1, deck + 1, 0, "vessel_liferaft[axis=z]");
    }

    /** The main deck's house, x 4 .. 23, z -4 .. 3: the cage aft, the saloon forward. */
    private void house() {
        int c0 = 4, c1 = 23, top = deck + 4;
        for (int x = c0; x <= c1; x++) {
            for (int z = -4; z <= 3; z++) {
                boolean wall = x == c0 || x == c1 || z == -4 || z == 3;
                for (int y = deck + 1; y < top; y++) {
                    if (!wall) {
                        q.set(x, y, z, "air");
                        continue;
                    }
                    boolean pane = y == deck + 2 && (z == -4 || z == 3) && x % 2 == 1 && x > c0 + 1 && x < c1;
                    boolean front = y == deck + 2 && x == c1 && z > -4 && z < 3;
                    String f = z == -4 ? "north" : z == 3 ? "south" : "east";
                    if (pane && x < 12) {
                        // the cage's ports: small, barred
                        q.set(x, y, z, "pn_window[facing=" + f + "]");
                    } else {
                        q.set(x, y, z, pane || front ? (g.chance() < 0.12 + g.b.damage * 0.2 ? "vessel_window_broken" : "vessel_window") + "[facing=" + f + "]"
                                : g.chance() < 0.06 ? "vessel_house_rust" : "vessel_house");
                    }
                }
                q.set(x, deck, z, x < 12 ? "pn_floor" : "floor_lino");
                q.set(x, top, z, "vessel_deck_walk");
            }
        }
        for (int z : new int[] {-4, 3}) {
            for (int x = DOOR; x <= DOOR + 1; x++) {
                q.set(x, deck + 2, z, "vessel_house");
                q.set(x, deck + 3, z, "vessel_house");
            }
            Doors.pair(q, g.rng, DOOR, deck + 1, z, "ship_door", z < 0 ? "north" : "south");
        }
        // the cage: bars across at x = 12 with its gate, the bench along the hull side, the rings
        for (int z = -3; z <= 2; z++) {
            for (int y = deck + 1; y < top; y++) {
                q.set(12, y, z, z == 0 && y <= deck + 2 ? "air" : "pn_bars[axis=z]");
            }
        }
        Doors.abandoned(q, g.rng, 12, deck + 1, 0, "pn_cell_door", "east", "left");
        for (int x = c0 + 1; x < 12; x++) {
            q.set(x, deck + 1, -3, "th_bench_plain[facing=south]");
            q.set(x, deck + 1, 2, x == 7 ? "home_blood_pool" : "th_bench_plain[facing=north]");
        }
        q.set(c0 + 1, deck + 3, 0, "sh_camera[facing=east]");
        q.set(c0 + 1, deck + 1, 0, "pn_toilet_sink[facing=east]");
        // the saloon: benches facing forward either side of the aisle, the officer's desk by the door
        for (int x = 16; x < c1 - 1; x += 2) {
            for (int z = -3; z <= 2; z++) {
                if (z != 0 && z != -1) {
                    q.set(x, deck + 1, z, g.chance() < 0.08 ? "wf_bags_" + g.any("a", "b", "c") : "wf_seats[facing=east]");
                }
            }
        }
        q.set(13, deck + 1, -3, "desk[facing=east]");
        q.set(13, deck + 1, 2, "filing_cabinet[facing=north]");
        q.set(c1 - 1, deck + 1, -3, "bunk_first_aid[facing=west]");
        for (int x = c0 + 2; x < c1; x += 4) {
            q.set(x, top - 1, 0, g.power && g.chance() < 0.3 ? "fit_troffer_on" : "fit_troffer");
        }
        // the ladder aft up to the upper deck
        for (int k = 0; k < 4; k++) {
            q.set(2 + k, deck + 1 + k, 0, "wf_gangway[facing=east]");
            q.set(2 + k, deck + 1 + k, -1, "wf_gangway[facing=east]");
        }
    }

    /** The upper deck on the house: the rail, the benches, the funnel, the wheelhouse forward. */
    private void upper() {
        int c0 = 4, c1 = 23, top = deck + 4;
        for (int x = c0; x <= c1; x++) {
            for (int z = -4; z <= 3; z++) {
                boolean edge = x == c0 || x == c1 || z == -4 || z == 3;
                if (edge && !(x == c0 && (z == 0 || z == -1))) {
                    q.set(x, top + 1, z, x == c0 || x == c1 ? "vessel_rail[axis=z]" : "vessel_rail[axis=x]");
                }
            }
        }
        for (int x = c0 + 2; x <= c0 + 6; x += 2) {
            q.set(x, top + 1, -2, "st_bench[facing=south]");
            q.set(x, top + 1, 1, "st_bench[facing=north]");
        }
        q.set(c0 + 9, top + 1, -3, "vessel_liferaft[axis=x]");
        q.set(c0 + 9, top + 1, 2, "vessel_liferaft[axis=x]");
        for (int y = top + 1; y <= top + 3; y++) {
            q.set(c0 + 11, y, -1, y == top + 3 ? "vessel_funnel_soot" : "vessel_funnel_black");
            q.set(c0 + 11, y, 0, y == top + 3 ? "vessel_funnel_soot" : "vessel_funnel_black");
        }
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
                    q.set(x, y, z, y == top + 2 && x > w0 ? (g.chance() < 0.2 ? "vessel_bridge_broken" : "vessel_bridge") + "[facing=" + f + "]"
                            : "vessel_house");
                }
                q.set(x, top + 4, z, "vessel_roof");
            }
        }
        Doors.abandoned(q, g.rng, w0, top + 1, 0, "ship_door", "west", "left");
        q.set(c1 - 2, top + 1, -1, "ship_console[facing=east]");
        q.set(c1 - 2, top + 1, 0, "ship_telegraph[facing=east]");
        q.set(c1 - 2, top + 1, 1, "ship_console[facing=east]");
        q.set(c1 - 4, top + 1, -2, "bunk_radio_desk[facing=south]");
        q.set(c1 - 4, top + 1, 1, "fit_chart_table");
        for (int y = top + 5; y <= top + 8; y++) {
            q.set(c1 - 3, y, 0, "vessel_mast");
        }
        q.set(c1 - 3, top + 6, -1, "vessel_radar[axis=z]");
        q.set(c1 - 3, top + 9, 0, "vessel_light_white");
        q.set(c1 - 2, top + 5, 0, "vessel_horn[facing=east]");
        q.set(c1 - 2, top + 5, -3, "vessel_light_red");
        q.set(c1 - 2, top + 5, 2, "vessel_light_green");
        q.set(c1 - 1, top + 5, 0, "fit_searchlight[facing=east]");
    }

    /** The vehicle deck forward of the house: the supply pallets lashed down, the bitts, the bow ramp raised. */
    private void foredeck() {
        int y = deck + 1;
        q.set(26, y, -2, "st_pallet_water");
        q.set(26, y, 1, "st_pallet_meals");
        q.set(27, y, 1, g.chance() < 0.5 ? "st_pallet_water_half" : "st_pallet");
        q.set(29, y, -1, "bunk_crate_ration[facing=east]");
        for (int x = 25; x <= 31; x += 3) {
            q.set(x, y, -4, "vessel_bollard");
            q.set(x, y, 3, "vessel_bollard");
        }
        for (int z = -2; z <= 1; z++) {
            q.set(LEN - 2, y, z, "vessel_rail_cross[axis=z]");
            q.set(LEN - 2, y + 1, z, "vessel_hull_black_seam");
        }
    }

    /** Her name and port of registry down both sides amidships. */
    private void lettering(String name, int hb) {
        for (int i = 0; i < name.length(); i++) {
            char ch = Character.toLowerCase(name.charAt(i));
            if (ch == ' ') {
                continue;
            }
            int xn = 6 + name.length() - 1 - i, xs = 6 + i;
            q.set(xn, deck - 1, -hb, "vessel_name_" + ch + "_top[facing=north]");
            q.set(xn, deck - 2, -hb, "vessel_name_" + ch + "_bot[facing=north]");
            q.set(xs, deck - 1, hb - 1, "vessel_name_" + ch + "_top[facing=south]");
            q.set(xs, deck - 2, hb - 1, "vessel_name_" + ch + "_bot[facing=south]");
        }
    }
}
