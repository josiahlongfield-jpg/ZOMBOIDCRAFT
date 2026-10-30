package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven's Waterfront on the island's east shore, a lot two blocks out from the avenue the
 * subway runs under by three along the water: the land to a straight granite seawall, the rest the
 * harbour. Along the seawall the promenade of brick pavers, its iron rail, lamps and benches; at
 * the north end the Harbor Landing ferry terminal and its ferry tied up alongside
 * ({@link WaterfrontTerminal}); Anchor Plaza at the root of Pier 9, a timber pier of shingled shops
 * with the carousel at its end ({@link WaterfrontPier}); to the south the marina, its floats and
 * boats, the harbormaster's office, the bait shop and the dry stack shed.
 *
 * <p>The terminal was the island's evacuation point. The Guard held the crowd back with barriers
 * and turnstiles while the ferries ran; when the last one went the rest took what boats they could
 * from the marina, and sank half of them doing it.
 *
 * <p>Drawn in the lot's frame: x from the avenue (west) out over the water (east), z along the
 * shore from the north street south, the promenade's paving at y = 0; the water's top block at
 * {@link #SEA}, the dredged bed at {@link #BED}.
 */
final class Waterfront extends Grounds {
    /** The seawall's coping, the land's last column (x); everything east of it is water. */
    static final int SEAWALL = 36;
    /** The water's top block and the basin's bed, under the paving. */
    static final int SEA = -5, BED = -13;
    /** The terminal's walls (inclusive). */
    static final int TX0 = 6, TX1 = 28, TZ0 = 8, TZ1 = 64;
    /** Pier 9: its deck from the seawall out to PX1, z PZ0 .. PZ1; the boardwalk between the rows BZ0 .. BZ1. */
    static final int PX1 = 120, PZ0 = 92, PZ1 = 120, BZ0 = 101, BZ1 = 111;
    /** The marina's two floats: each one's gangway and main walk at z (two wide), its fingers every eight. */
    static final int MA = 136, MB = 176, MX1 = 110;
    /** The gangway down to the ferry. */
    static final int GZ = 36;
    /** The prison slip: its jetty from the seawall out to JX1, z JZ0 .. JZ0 + 2; the prison ferry's middle at FZ. */
    static final int JZ0 = 66, JX1 = 90, FX = 52, FZ = 76;
    /** The ferry bell at the jetty's root, and where the ferry puts people ashore (y on the paving). */
    static final int[] BELL = {38, 1, 66}, LANDING = {41, 1, 67};

    Waterfront(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.3, 0.3, 0.25);
    }

    @Override
    String signs() {
        return "wf_sgn_";
    }

    void build() {
        ground();
        seawall();
        new WaterfrontTerminal(this).build();
        new WaterfrontPier(this).build();
        plaza();
        slip();
        marinaLand();
        floats(MA);
        floats(MB);
        boats();
        promenade();
        story();
    }

    // ------------------------------------------------------------------ the ground and the seawall

    /** Brick pavers to the seawall, sunk and mossy here and there; the harbour's top. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (x < SEAWALL) {
                    double r = chance();
                    boolean edge = x < 2 || z < 2 || z > d - 3;
                    set(x, 0, z, edge && r < 0.3 ? "wf_pavers_moss" : r < 0.07 ? "wf_pavers_b" : r < 0.1 ? "wf_pavers_moss" : "wf_pavers");
                } else if (x > SEAWALL) {
                    set(x, SEA, z, "minecraft:water");
                }
            }
        }
    }

    /**
     * The seawall down the lot: granite from the bed to the coping, the weed on it below the tide
     * line, fenders where the ferry lies, a ladder every thirty.
     */
    private void seawall() {
        for (int z = 0; z < d; z++) {
            set(SEAWALL, 0, z, "wf_cap");
            for (int y = BED; y < 0; y++) {
                set(SEAWALL, y, z, y < SEA + 2 ? "wf_wall_slime" : "wf_wall");
            }
            if (z % 30 == 17 && (z < PZ0 - 2 || z > PZ1 + 2) && Math.abs(z - MA) > 3 && Math.abs(z - MB) > 3) {
                for (int y = SEA - 1; y <= 0; y++) {
                    set(SEAWALL + 1, y, z, "bunk_ladder[facing=east]");
                }
            }
        }
        // the ferry's berth: rubber fenders on the face where her side lies against it
        for (int z = 16; z <= 60; z += 4) {
            for (int y = SEA + 1; y <= -1; y++) {
                set(SEAWALL + 1, y, z, "wf_fender[facing=east]");
            }
        }
    }

    /** Whether the seawall's rail has a gap at z: the ferry's gangway, the pier's root, the floats'. */
    private boolean gap(int z) {
        return z >= GZ - 1 && z <= GZ + 2 || z >= JZ0 - 3 && z <= JZ0 + 4 || z >= PZ0 && z <= PZ1 || z >= MA - 1 && z <= MA + 2 || z >= MB - 1 && z <= MB + 2
                || z % 30 == 17;
    }

    /** The rail on the coping, lamps and benches along the promenade, telescopes and life rings by the water. */
    private void promenade() {
        for (int z = 0; z < d; z++) {
            if (!gap(z) && empty(SEAWALL, 1, z)) {
                set(SEAWALL, 1, z, chance() < 0.03 ? "air" : "wf_rail_iron[facing=east]");
            }
        }
        for (int z = 6; z < d - 4; z += 12) {
            if (gap(z) || z >= PZ0 - 2 && z <= PZ1 + 2) {
                continue;
            }
            if (empty(SEAWALL - 2, 1, z)) {
                lampPost(SEAWALL - 2, z);
            }
            int bz = z + 4;
            if (!gap(bz) && empty(SEAWALL - 1, 1, bz) && empty(SEAWALL - 1, 1, bz + 1)) {
                set(SEAWALL - 1, 1, bz, "st_bench[facing=west]");
                set(SEAWALL - 1, 1, bz + 1, "st_bench[facing=west]");
            }
            int tz = z + 8;
            if (!gap(tz) && empty(SEAWALL - 1, 1, tz)) {
                set(SEAWALL - 1, 1, tz, chance() < 0.5 ? "wf_telescope[facing=east]" : "wf_ring_post[facing=east]");
            }
        }
    }

    // ------------------------------------------------------------------ Anchor Plaza

    /**
     * Between the terminal and the pier: the anchor from the old Calder Queen set up on a granite
     * plinth, the flagpoles, benches round planters; the Guard's aid tents along the avenue side.
     */
    private void plaza() {
        int ax = 20, az = (TZ1 + PZ0) / 2;
        for (int x = ax - 1; x <= ax + 1; x++) {
            for (int z = az - 1; z <= az + 1; z++) {
                set(x, 1, z, "wf_cap");
            }
        }
        for (int y = 2; y <= 4; y++) {
            set(ax, y, az, "wf_wall");
        }
        set(ax, 5, az, "wf_cap");
        // the anchor stood against the plinth's water side, its crown at the foot
        set(ax + 1, 2, az, "vessel_anchor_crown[facing=east]");
        set(ax + 1, 3, az, "vessel_anchor_shank[facing=east]");
        set(ax + 1, 4, az, "vessel_anchor_shank[facing=east]");
        for (int k = -1; k <= 1; k += 2) {
            int fz = az + 6 * k;
            set(ax, 1, fz, "cv_flagpole_base");
            for (int y = 2; y <= 9; y++) {
                set(ax, y, fz, "cv_flagpole");
            }
            set(ax, 10, fz, chance() < 0.3 ? "cv_flagpole" : "cv_flagpole_top[facing=east]");
            set(ax + 5, 1, az + 3 * k, "om_planter");
            set(ax + 5, 1, az + 3 * k + k, "st_bench[facing=" + (k < 0 ? "north" : "south") + "]");
        }
        // the Guard's aid post: a triage tent and a supply tent off the avenue, barriers round them
        tent(2, TZ1 + 3, 10, "triage");
        tent(2, TZ1 + 14, 10, "supply");
        for (int z = TZ1 + 2; z <= PZ0 - 3; z += 3) {
            if (chance() < 0.75) {
                set(9, 1, z, "jersey_barrier[facing=east]");
            }
        }
        sign("family", 12, 3, TZ1 + 1, "south");
        mess(10, TZ1 + 1, SEAWALL - 2, PZ0 - 1, 0, 0.03);
    }

    // ------------------------------------------------------------------ the prison slip

    /**
     * Ravenrock's mainland landing, between the terminal and the plaza: a concrete jetty on piles out
     * from the seawall, the prison ferry tied up along its south side, the bell at its root to call
     * her across; across the promenade the corrections officers' pen, chain link and razor wire,
     * the booth, the boom, the signs, the Guard's sandbags funnelling the line to the gate.
     */
    private void slip() {
        int z0 = JZ0, z1 = JZ0 + 2;
        for (int x = SEAWALL + 1; x <= JX1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, chance() < 0.08 ? "floor_concrete_crack" : "floor_concrete");
                set(x, -1, z, "concrete_dark");
            }
            if ((x - SEAWALL) % 6 == 3 || x == JX1) {
                for (int z : new int[] {z0, z1}) {
                    for (int y = BED; y <= -2; y++) {
                        set(x, y, z, y < SEA + 2 ? "wf_wall_slime" : "wf_wall");
                    }
                }
            }
            boolean gangway = x >= FX + PrisonFerry.DOOR - 1 && x <= FX + PrisonFerry.DOOR + 2;
            if (x != BELL[0]) {
                set(x, 1, z0, "wf_rail_iron[facing=north]");
            }
            if (!gangway) {
                set(x, 1, z1, x % 8 == 4 ? "vessel_bollard" : "wf_rail_iron[facing=south]");
            }
            if (x % 4 == 1 && !gangway && x > FX - 2) {
                for (int y = SEA + 1; y <= -1; y++) {
                    set(x, y, z1 + 1, "wf_fender[facing=south]");
                }
            }
        }
        for (int z = z0; z <= z1; z++) {
            set(JX1, 1, z, "wf_rail_iron[facing=east]");
        }
        lampPost(JX1 - 1, z0 + 1);
        lampPost(FX + 4, z0 + 1);
        // the ferry alongside, her bow out east; the gangway down to her side door
        PrisonFerry ferry = new PrisonFerry(this, p.turned(FX, 0, FZ, 0), SEA);
        ferry.build("RAVENROCK I");
        for (int x = FX + PrisonFerry.DOOR; x <= FX + PrisonFerry.DOOR + 1; x++) {
            set(x, -1, z1 + 1, "wf_gangway[facing=north]");
            set(x, -2, z1 + 2, "wf_gangway[facing=north]");
            set(x, 0, z1 + 1, "air");
            set(x, -1, z1 + 2, "air");
        }
        // the bell on its post at the root
        set(BELL[0], BELL[1], BELL[2], "zomboidcraft:ferry_bell[facing=south]");
        // the pen across the promenade: chain link on posts, razor on top, the gate on the plaza side
        int px0 = 29, px1 = SEAWALL - 1, pz0 = z0 - 3, pz1 = z1 + 3;
        for (int x = px0; x <= px1; x++) {
            for (int z = pz0; z <= pz1; z++) {
                boolean edgeX = z == pz0 || z == pz1, edgeZ = x == px0;
                set(x, 0, z, chance() < 0.1 ? "floor_concrete_crack" : "floor_concrete");
                if (!edgeX && !edgeZ) {
                    continue;
                }
                if (edgeZ && z >= z0 && z <= z1) {
                    continue;  // the gate, swung back
                }
                boolean post = edgeX ? (x - px0) % 3 == 0 || x == px1 : (z - pz0) % 3 == 0 || z == z0 - 1 || z == z1 + 1;
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, post ? "pn_post" : "chainlink[axis=" + (edgeX ? "x" : "z") + "]");
                }
                set(x, 4, z, post ? "pn_post" : "st_razor");
            }
        }
        for (int y = 1; y <= 2; y++) {
            set(px0 + 1, y, z0 - 1, "pn_gate[axis=x]");
            set(px0 + 1, y, z1 + 1, "pn_gate[axis=x]");
        }
        set(px0 + 1, 3, z0 - 1, "pn_gate_top[axis=x]");
        set(px0 + 1, 3, z1 + 1, "pn_gate_top[axis=x]");
        Camp camp = new Camp(p, rng, 0.5);
        camp.booth(px0 + 2, pz0 + 1);
        camp.boom(px0 - 1, z0 - 1, 4, false);
        camp.floodMast(px1, pz0 + 1, 6);
        for (int c = 0; c < 2; c++) {
            int[] a = MallSigns.at(px0 - 1, z0 - 3, 2, c, "west"), b2 = MallSigns.at(px0 - 1, z1 + 2, 2, c, "west");
            set(a[0], 3, a[1], "pn_sgn_ferry_" + c + "[facing=west]");
            set(b2[0], 3, b2[1], "pn_sgn_warn_" + c + "[facing=west]");
            int[] bs = MallSigns.at(px1 - 2, pz1 - 1, 2, c, "north");
            set(bs[0], 2, bs[1], "pn_sgn_bell_" + c + "[facing=north]");
        }
        // the line they kept: sandbags and barriers in a funnel to the gate, what was dropped in it
        for (int k = 2; k <= 7; k++) {
            if (chance() < 0.85) {
                set(px0 - k, 1, z0 - 1 - k / 2, k % 2 == 0 ? "sandbags[facing=south]" : "jersey_barrier[facing=north]");
            }
            if (chance() < 0.85) {
                set(px0 - k, 1, z1 + 1 + k / 2, k % 2 == 0 ? "sandbags[facing=north]" : "jersey_barrier[facing=north]");
            }
        }
        mess(px0 - 6, z0 - 2, px1, z1 + 2, 0, 0.05);
    }

    // ------------------------------------------------------------------ the marina, ashore

    /**
     * The harbormaster's office and the bait shop off the promenade, and the dry stack shed: boats
     * three high on steel racks in a tall open shed, the lift truck left halfway up an aisle.
     */
    private void marinaLand() {
        // the harbormaster: a shingled office, the counter, the radio and the charts, the slip board
        hut(6, MA - 10, 15, 14, "wf_shingle", "master", "east");
        set(9, 1, MA - 6, "desk[facing=east]");
        set(8, 1, MA - 6, "office_chair[facing=east]");
        set(7, 1, MA - 4, "bunk_radio_desk[facing=east]");
        set(7, 1, MA - 2, "filing_cabinet[facing=east]");
        set(7, 1, MA, "filing_cabinet[facing=east]");
        set(12, 1, MA - 8, "fit_chart_table");
        set(16, 1, MA - 7, "store_counter[facing=east]");
        set(16, 1, MA - 6, "store_counter[facing=east]");
        set(16, 1, MA - 5, "store_register[facing=east]");
        set(7, 2, MA - 8, "store_safe[facing=east]");
        // the bait shop: tanks and freezers, rods on the wall, the pots stacked outside
        hut(6, MA + 8, 11, 11, "wf_clap", "bait", "east");
        for (int z = MA + 9; z <= MA + 17; z += 2) {
            set(7, 1, z, "store_shelf_hardware[facing=east]");
        }
        set(12, 1, MA + 10, "wf_freezer[facing=east]");
        set(12, 1, MA + 11, "wf_freezer[facing=east]");
        set(14, 1, MA + 15, "store_counter[facing=east]");
        set(14, 1, MA + 16, "store_register[facing=east]");
        for (int z = MA + 8; z <= MA + 18; z += 3) {
            set(18, 1, z, "wf_pots");
            if (chance() < 0.5) {
                set(18, 2, z, "wf_pots");
            }
        }
        set(18, 1, MA + 13, "wf_coil");
        drystack(4, MB - 14, 23, MB + 30 - (MB - 14));
    }

    /**
     * A one-storey shop or office: walls of wall from (x0, z0), w by dd, a flat roof, its door and
     * windows and sign in the face toward facing (east: the promenade).
     */
    private void hut(int x0, int z0, int hw, int dd, String wall, String sign, String facing) {
        int x1 = x0 + hw - 1, z1 = z0 + dd - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, edge ? "wf_cap" : "floor_boards");
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z, edge ? (y == 4 ? "wf_trim" : wall) : "air");
                }
                set(x, 5, z, edge ? "wf_trim" : "un_roof");
            }
        }
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            if (z % 3 == 0) {
                set(x1, 2, z, chance() < b.damage ? "window_broken[axis=z]" : "wf_window[facing=east]");
                set(x1, 3, z, "wf_window[facing=east]");
            }
        }
        int dz = (z0 + z1) / 2;
        set(x1, 2, dz, wall);
        set(x1, 3, dz, wall);
        door(x1, 0, dz, "shop_door", facing);
        row("wf_shop_" + sign + "_", 4, x1 + 1, 4, dz - 1, facing, 0.08);
        ceilingLights(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 4);
        mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.06);
    }

    /** Lamps hung under a ceiling at y, about every four. */
    void ceilingLights(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0 + 1; x <= x1; x += 4) {
            for (int z = z0 + 1; z <= z1; z += 4) {
                if (empty(x, y, z)) {
                    set(x, y, z, power && chance() < 0.3 ? "fit_troffer_on" : "fit_troffer");
                }
            }
        }
    }

    /** The dry stack shed: a steel frame clad in cladding on three sides, open to the promenade, racks inside. */
    private void drystack(int x0, int z0, int sw, int sd) {
        int x1 = x0 + sw - 1, z1 = z0 + sd - 1, top = 13;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean side = z == z0 || z == z1 || x == x0;
                set(x, 0, z, chance() < 0.1 ? "floor_concrete_crack" : "floor_concrete");
                for (int y = 1; y < top; y++) {
                    set(x, y, z, side ? ((x - x0) % 6 == 0 || (z - z0) % 6 == 0 ? "wf_steel" : y > top - 3 ? "wf_panel" : "wf_panel_b") : "air");
                }
                set(x, top, z, "un_roof");
            }
        }
        // the open front: the steel posts and the beam over it
        for (int z = z0; z <= z1; z += 6) {
            for (int y = 1; y < top; y++) {
                set(x1, y, z, "wf_steel");
            }
        }
        for (int z = z0; z <= z1; z++) {
            set(x1, top - 1, z, "wf_steel");
        }
        row("wf_sgn_storage_", 2, x1 + 1, top - 2, (z0 + z1) / 2, "east", 0);
        // the racks: three levels down the back wall and along the middle, a boat in most bays
        for (int level = 0; level < 3; level++) {
            int y = 1 + level * 4;
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                for (int x : new int[] {x0 + 1, x0 + 12}) {
                    if (level > 0) {
                        set(x, y - 1, z, "wf_rack[axis=z]");
                        set(x + 7, y - 1, z, "wf_rack[axis=z]");
                    }
                }
            }
            for (int z = z0 + 3; z + 6 < z1; z += 9) {
                for (int x : new int[] {x0 + 2, x0 + 13}) {
                    if (level > 0) {
                        // the arms across the bay the hull's bunks sit on
                        for (int xx = x - 1; xx <= x + 6; xx++) {
                            set(xx, y - 1, z + 1, "wf_rack[axis=x]");
                            set(xx, y - 1, z + 6, "wf_rack[axis=x]");
                        }
                        for (int zz = z + 2; zz <= z + 5; zz++) {
                            set(x + 3, y - 1, zz, "wf_rack[axis=z]");   // the keel's rest, which she's stood on
                        }
                    }
                    if (chance() < 0.75) {
                        int k = new int[] {0, 0, 1, 4}[rng.nextInt(4)];
                        String st = state(false);
                        p.vehicle(x + 3.5, y, z + 4, 0, "=" + BOATS[k] + "@" + (st.equals("burnt") ? "abandoned" : st), rng.nextLong());
                    }
                }
            }
        }
        // the posts holding the racks
        for (int z = z0 + 2; z <= z1 - 2; z += 9) {
            for (int x : new int[] {x0 + 1, x0 + 8, x0 + 12, x0 + 19}) {
                for (int y = 1; y < 12; y++) {
                    if (empty(x, y, z)) {
                        set(x, y, z, "wf_steel");
                    }
                }
            }
        }
        mess(x0 + 9, z0 + 1, x0 + 11, z1 - 1, 0, 0.05);
    }

    // ------------------------------------------------------------------ the floats

    /**
     * A float system: aluminium stairs down from the coping at z (two wide), the main walk out
     * along it to MX1, a finger off it either side every eight; a fuel dock at the end of the first.
     */
    private void floats(int z) {
        int y = SEA + 1;
        for (int k = 0; k <= 4; k++) {
            for (int i = 0; i <= 1; i++) {
                set(SEAWALL + 1 + k, -k, z + i, "wf_gangway[facing=west]");
                set(SEAWALL + 1 + k, -k + 1, z + i, "air");
            }
        }
        for (int x = SEAWALL + 6; x <= MX1; x++) {
            for (int i = 0; i <= 1; i++) {
                set(x, y, z + i, "wf_float");
            }
            if (x % 8 == 6) {
                set(x, y + 1, z, "wf_cleat[axis=x]");
            }
        }
        for (int x = SEAWALL + 10; x <= MX1 - 2; x += 8) {
            for (int k = 1; k <= 8; k++) {
                set(x, y, z - k, "wf_float");
                set(x, y, z + 1 + k, "wf_float");
            }
            set(x, y + 1, z - 8, "wf_cleat[axis=x]");
            set(x, y + 1, z + 9, "wf_cleat[axis=x]");
        }
        for (int x = SEAWALL + 6; x <= MX1; x += 2) {
            for (int i = 0; i <= 1; i++) {
                if (chance() < 0.03 + b.damage * 0.05) {
                    set(x, y, z + i, "air");  // a float gone under
                }
            }
        }
        if (z == MA) {
            // the fuel dock at the end
            for (int x = MX1 + 1; x <= MX1 + 6; x++) {
                for (int zz = z - 4; zz <= z + 5; zz++) {
                    set(x, y, zz, "wf_float");
                }
            }
            set(MX1 + 3, y + 1, z - 3, "wf_pump[facing=north]");
            set(MX1 + 3, y + 1, z + 4, "wf_pump[facing=south]");
            set(MX1 + 6, y + 1, z, "wf_ring_post[facing=east]");
            for (int x = MX1 + 4; x <= MX1 + 5; x++) {
                set(x, y + 1, z - 4, "wf_pile");
                set(x, y + 2, z - 4, "wf_pile");
            }
            sign("fuel", MX1 + 4, y + 2, z - 5, "north");
        }
    }

    // ------------------------------------------------------------------ the boats

    /**
     * The boats' models (tools/cars/boats.py) and their lengths overall in metres, so each lies with her
     * bow a step short of the walk; boats are vehicles, afloat in the top water block at their lines.
     */
    private static final String[] BOATS = {"boat_bowrider", "boat_console", "boat_cruiser", "boat_sloop", "boat_skiff",
            "boat_lobster"};
    private static final double[] BOAT_LENGTH = {7.1, 7.7, 10.6, 9.7, 5.1, 10.7};

    /** How a boat's been left: most of them abandoned at their lines, some smashed, a few burnt out. */
    private String state(boolean sunk) {
        double r = chance() - b.damage * 0.25;
        return sunk ? "wrecked" : r < 0.1 ? "burnt" : r < 0.25 ? "wrecked" : r < 0.75 ? "abandoned" : "usable";
    }

    /**
     * In the slips between the fingers: runabouts, centre consoles, cruisers, sailboats, a lobster boat
     * or two in from the grounds, a few sunk at their lines.
     */
    private void boats() {
        int[] pick = {0, 0, 1, 1, 2, 3, 3, 4, 5};
        for (int z : new int[] {MA, MB}) {
            for (int x = SEAWALL + 14; x <= MX1 - 4; x += 8) {
                for (int side = 0; side < 2; side++) {
                    if (chance() < 0.3) {
                        continue;  // the slip's boat taken
                    }
                    boolean north = side == 0;
                    int k = pick[rng.nextInt(pick.length)];
                    // the bow toward the main walk, the stern out in the slip
                    double zc = north ? z - 0.6 - BOAT_LENGTH[k] / 2 : z + 2.6 + BOAT_LENGTH[k] / 2;
                    boolean sunk = chance() < 0.12 + b.damage * 0.15;
                    p.vehicle(x + 0.5, sunk ? SEA - 1 - rng.nextInt(2) : SEA, zc, north ? 0 : 180,
                            "=" + BOATS[k] + "@" + state(sunk), rng.nextLong());
                }
            }
        }
        // the channel's buoys out past the floats and the pier
        for (int z = 10; z < d - 6; z += 26) {
            set(w - 6, SEA + 1, z, (z / 26) % 2 == 0 ? "wf_buoy_red" : "wf_buoy_green");
        }
        // one that got away, run aground on the pier's piles and half sunk, listing
        p.vehicle(PX1 - 8.7, SEA - 2, PZ1 + 6.5, 270, "=boat_cruiser@wrecked", rng.nextLong());
    }

    // ------------------------------------------------------------------ what happened

    /** The crowd's leavings along the front: bags, litter, a body or two, the words on the walls. */
    private void story() {
        for (int z = 2; z < d - 2; z++) {
            for (int x = TX1 + 1; x < SEAWALL; x++) {
                if (empty(x, 1, z) && get(x, 0, z) != null && get(x, 0, z).startsWith("wf_pavers") && chance() < 0.035) {
                    if (x == TX1 + 1) {
                        continue;  // the row along the terminal, where the gate's doors open
                    }
                    double r = chance();
                    set(x, 1, z, r < 0.35 ? "paper" : r < 0.55 ? "wf_bags_" + any("a", "b", "c") : r < 0.7 ? "blood_splat"
                            : r < 0.8 ? "trash_bag[facing=" + dir() + "]" : r < 0.9 ? "glass_shards" : "home_blood_pool");
                }
            }
        }
        row("wf_spray_nomore_", 4, 10, 2, MA - 11, "north", 0);
        row("wf_spray_swim_", 3, 8, 2, MA + 7, "north", 0);
    }
}
