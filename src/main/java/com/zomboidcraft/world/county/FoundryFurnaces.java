package com.zomboidcraft.world.county;

/**
 * Foundry Row's two blast furnaces in {@link Foundry}'s frame (refs/industry/blast-furnace,
 * hot-stoves-dust-catcher, skip-hoist-stockhouse, blast-furnace-cast-house, ladles-hot-metal-cars):
 * each one a riveted shell on its hearth, the bustle main round her waist feeding the tuyeres, her
 * four columns and the platforms up to the top with the bell house and the uptakes, the downcomer
 * off to the dust catcher on its legs; the three stoves behind her with the hot blast main and the
 * stoves' stack; the skip incline up from the stockhouse bins; the cast house out on her east side,
 * its floor up on fill, the iron runners from the notch to the spouts over the hot metal line where
 * the torpedo cars still wait, the slag runner to the pots on the slag line. No. 1 was banked with
 * a cast in her, her notch still glowing; the night turn holds her cast house.
 */
final class FoundryFurnaces {
    private static final int FLOOR = 4;
    /** The stockhouse bins along the furnaces' south side. */
    static final int BX0 = 150, BX1 = 330, BZ0 = 168, BZ1 = 182, BH = 8;

    private final Foundry c;

    FoundryFurnaces(Foundry c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String block) {
        c.set(x, y, z, block);
    }

    void build() {
        stockhouse();
        for (int i = 0; i < 2; i++) {
            furnace(i, Foundry.FX[i], Foundry.FZ);
        }
    }

    // ------------------------------------------------------------------ the furnace

    private void furnace(int i, int cx, int cz) {
        // the pad she stands on
        for (int dx = -10; dx <= 10; dx++) {
            for (int dz = -10; dz <= 10; dz++) {
                if (Math.hypot(dx, dz) < 10.5) {
                    set(cx + dx, 0, cz + dz, "concrete_dark");
                }
            }
        }
        // the hearth in its firebrick and hoops, the bosh, the stack tapering up, all riveted plate
        c.drum(cx, cz, 5.5, 1, 6, "oilrust", 2);
        c.drum(cx, cz, 6.5, 7, 12, "oilrust", 3);
        c.drum(cx, cz, 6, 14, 20, "oilrust", 3);
        c.drum(cx, cz, 5.5, 21, 27, "oilrust", 3);
        c.drum(cx, cz, 5, 28, 33, "oilrust", 3);
        c.drum(cx, cz, 4.5, 34, 38, "oilrust", 0);
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double r = Math.hypot(dx, dz);
                if (r < 5) {
                    set(cx + dx, 1, cz + dz, "fr_firebrick");
                }
                if (r >= 5.5 && r < 7.2) {
                    set(cx + dx, 13, cz + dz, "deck_steel");
                }
            }
        }
        // the bustle main round her waist, the tuyeres down off it
        c.drum(cx, cz, 9.5, 10, 10, "oilrust", 0);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int tx = cx + (int) Math.round(Math.cos(a) * 7.4), tz = cz + (int) Math.round(Math.sin(a) * 7.4);
            String f = Math.abs(Math.cos(a)) > 0.5 ? (Math.cos(a) > 0 ? "west" : "east") : (Math.sin(a) > 0 ? "north" : "south");
            set(tx, 8, tz, "fr_tuyere[facing=" + f + "]");
            set(tx, 9, tz, "fr_tuyere[facing=" + f + "]");
        }
        // the four columns, the platforms, the ladder up
        for (int[] q : new int[][] {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            for (int y = 1; y <= 39; y++) {
                set(cx + q[0], y, cz + q[1], "steel_girder");
            }
        }
        for (int y : new int[] {26}) {
            for (int dx = -8; dx <= 8; dx++) {
                for (int dz = -8; dz <= 8; dz++) {
                    double r = Math.hypot(dx, dz);
                    if (r > 6.2 && (Math.abs(dx) == 8 || Math.abs(dz) == 8 || r < 8.2)) {
                        set(cx + dx, y, cz + dz, "deck_steel");
                    }
                }
            }
        }
        c.platform(cx - 8, cz - 8, cx + 8, cz + 8, 39, false);
        c.ladder(cx - 9, cz - 8, 1, 40, "west");
        c.ladder(cx - 8, cz - 9, 14, 26, "north");
        // the bell house on top, the receiving hopper, the uptakes and the bleeders
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean edge = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                for (int y = 40; y <= 45; y++) {
                    set(cx + dx, y, cz + dz, edge ? (y == 43 && (dx == 0 || dz == 0) ? "window_broken[axis=" + (Math.abs(dz) == 3 ? "x" : "z") + "]"
                            : "fr_blue") : "air");
                }
                set(cx + dx, 46, cz + dz, "fr_rust");
            }
        }
        set(cx, 47, cz + 3, "fr_skip[facing=south]");
        for (int[] q : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            c.pipe(cx + q[0], 47, cz + q[1], cx + q[0], 55, cz + q[1], 0.9, "fr_rust");
            set(cx + q[0], 56, cz + q[1], "fr_blue");
            set(cx + q[0], 57, cz + q[1], "hull_black");
        }
        c.pipe(cx - 3, 54, cz - 3, cx + 3, 54, cz - 3, 0.9, "fr_rust");
        c.pipe(cx - 3, 54, cz + 3, cx + 3, 54, cz + 3, 0.9, "fr_rust");
        c.pipe(cx + 3, 54, cz - 3, cx + 3, 54, cz + 3, 0.9, "fr_rust");
        // the downcomer off to the dust catcher on its legs, its cone, the gas main away to the stoves
        int dcx = cx + 20, dcz = cz - 32;
        c.pipe(cx + 4, 53, cz - 4, dcx - 1, 34, dcz + 1, 1.3, "fr_rust_seam");
        for (int[] q : new int[][] {{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            for (int y = 1; y <= 15; y++) {
                set(dcx + q[0], y, dcz + q[1], "steel_girder");
            }
        }
        c.drum(dcx, dcz, 1.5, 7, 9, "oilrust", 0);
        c.drum(dcx, dcz, 2.5, 10, 11, "oilrust", 0);
        c.drum(dcx, dcz, 3.5, 12, 13, "oilrust", 0);
        c.drum(dcx, dcz, 4.5, 14, 29, "oilrust", 4);
        Round.roof(c.p, dcx + 0.5, dcz + 0.5, 4.5, Round.dome(4.5), 30, "oil");
        c.pipe(dcx, 5, dcz, dcx, 5, dcz - 3, 1.0, "fr_rust");
        c.pipe(dcx, 5, dcz - 3, cx - 12, 5, dcz - 3, 1.0, "fr_rust");
        c.ladder(dcx - 5, dcz, 1, 30, "west");
        // the stoves behind her, their hot blast main to the bustle, the stoves' stack
        int sz = cz - 46;
        for (int k = -1; k <= 1; k++) {
            int sx = cx + k * 12;
            stove(sx, sz);
            c.pipe(sx, 14, sz + 6, sx, 14, cz - 36, 1.0, "fr_rust");
            c.pipe(sx, 5, sz + 6, sx, 5, dcz - 3, 0.8, "fr_rust");
        }
        c.pipe(cx - 12, 14, cz - 36, cx, 14, cz - 36, 1.3, "fr_rust_seam");
        c.pipe(cx, 14, cz - 36, cx, 11, cz - 10, 1.3, "fr_rust_seam");
        int stx = cx - 22;
        c.drum(stx, sz, 2.5, 1, 62, "soot", 0);
        c.pipe(cx - 18, 3, sz, stx + 3, 3, sz, 0.9, "fr_rust");
        set(stx, 63, sz, "aviation_light");
        c.ladder(stx - 3, sz, 1, 62, "west");
        // her name on the column facing the yard
        c.sign(i == 0 ? "furnace1" : "furnace2", cx - 8, 7, cz + 9, "south");
        skip(cx, cz);
        castHouse(i, cx, cz);
    }

    /** A hot-blast stove: a riveted drum 36 high under its dome, the valves at the foot, the ladder. */
    private void stove(int sx, int sz) {
        c.drum(sx, sz, 5.5, 1, 36, "oilrust", 4);
        Round.roof(c.p, sx + 0.5, sz + 0.5, 5.5, Round.dome(5.5), 37, "oil");
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                if (Math.hypot(dx, dz) < 6.5) {
                    set(sx + dx, 0, sz + dz, "concrete_dark");
                }
            }
        }
        set(sx, 2, sz + 6, "fr_blue");
        set(sx, 3, sz + 6, "hull_black");
        set(sx - 1, 2, sz + 6, "pw_gauge[facing=south]");
        c.ladder(sx + 6, sz, 1, 37, "east");
        c.platform(sx + 5, sz - 2, sx + 8, sz + 2, 20, false);
    }

    // ------------------------------------------------------------------ the skip incline

    /**
     * The skip bridge from the pit at the bins up to the top: two trussed girders (crane members)
     * either side of the track, the skip half way up, the winding house at the foot.
     */
    private void skip(int cx, int cz) {
        int x0 = cx - 8, x1 = cx - 4, zb = BZ0 - 2, zt = cz + 7, yt = 46;
        for (int x : new int[] {x0, x1}) {
            c.hb.member(x, 1, zb, yt, zt, "cr_strut_red");
            c.hb.member(x, 4, zb - 1, yt + 3, zt - 1, "cr_strut_red");
        }
        int n = zb - zt;
        for (int i = 0; i <= n; i++) {
            int z = zb - i, y = 1 + (int) Math.round((yt - 1) * i / (double) n);
            for (int x = x0 + 1; x < x1; x++) {
                set(x, y, z, x == cx - 6 ? "rail[axis=z]" : "deck_steel");
            }
            if (i % 8 == 4 && y > 4) {
                for (int yy = 1; yy < y; yy++) {
                    set(x0, yy, z, "steel_girder");
                    set(x1, yy, z, "steel_girder");
                }
            }
        }
        int mz = (zb + zt) / 2, my = 1 + (int) Math.round((yt - 1) * (zb - mz) / (double) n);
        set(cx - 6, my + 1, mz, "fr_skip[facing=north]");
        // the winding house beside the pit
        int hx0 = cx - 18, hx1 = cx - 10, hz0 = BZ0 - 12, hz1 = BZ0 - 2;
        c.b.shell(hx0, hz0, hx1, hz1, 1, 6, "brick_dark", "floor_concrete", "floor_concrete");
        c.b.gable(hx0, hx1, hz0, hz1, 7, "minecraft:polished_tuff", true, "brick_dark", 1);
        c.b.window(hx0, 3, (hz0 + hz1) / 2, "west", 2, "stone_trim");
        c.b.door((hx0 + hx1) / 2, 1, hz0, "fire_door", "north");
        set(hx0 + 2, 1, hz0 + 3, "pw_drum[axis=z]");
        set(hx0 + 2, 1, hz0 + 4, "pw_drum[axis=z]");
        set(hx0 + 4, 1, hz0 + 3, "pw_gen");
        set(hx0 + 6, 1, hz1 - 2, "pw_bench[facing=north]");
        c.pipe(hx1, 4, hz0 + 4, x0, 4, hz0 + 4, 0.0, "pw_rope");
    }

    // ------------------------------------------------------------------ the cast house

    private void castHouse(int i, int cx, int cz) {
        int x0 = cx + 8, x1 = cx + 40, z0 = cz - 14, z1 = cz + 20;
        // the cast floor up on its fill, under the furnace's side too
        for (int x = cx + 5; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x < x0 && Math.abs(z - cz) > 4) {
                    continue;
                }
                for (int y = 1; y < FLOOR; y++) {
                    if (c.empty(x, y, z) || c.get(x, y, z).startsWith("gc_") || c.get(x, y, z).startsWith("minecraft:")) {
                        set(x, y, z, "concrete_dark");
                    }
                }
                set(x, FLOOR, z, c.chance() < 0.08 ? "fr_slag" : "fr_castsand");
            }
        }
        // the notch, the main trough with its skimmer, the iron runners to the spouts, the slag runner
        set(cx + 5, FLOOR + 1, cz, (i == 0 ? "fr_tap_lit" : "fr_tap") + "[facing=east]");
        for (int x = cx + 6; x <= cx + 16; x++) {
            set(x, FLOOR, cz, x == cx + 12 ? "fr_skull" : "fr_runner[axis=x]");
        }
        for (int z = cz; z <= z1 + 5; z++) {
            set(cx + 16, FLOOR, z, "fr_runner[axis=z]");
        }
        for (int x = cx + 17; x <= cx + 30; x++) {
            set(x, FLOOR, cz + 8, "fr_runner[axis=x]");
        }
        for (int z = cz + 8; z <= z1 + 5; z++) {
            set(cx + 30, FLOOR, z, "fr_runner[axis=z]");
        }
        for (int z = z0 - 5; z < cz; z++) {
            set(cx + 12, FLOOR, z, "fr_runner[axis=z]");
        }
        // the spouts out over the lines on their girders
        for (int[] s : new int[][] {{cx + 16, z1 + 1, z1 + 5}, {cx + 30, z1 + 1, z1 + 5}, {cx + 12, z0 - 5, z0 - 1}}) {
            for (int z = s[1]; z <= s[2]; z++) {
                set(s[0] - 1, FLOOR, z, "deck_steel");
                set(s[0] + 1, FLOOR, z, "deck_steel");
                if (z == s[1] || z == s[2]) {
                    for (int y = 1; y < FLOOR; y++) {
                        set(s[0] - 1, y, z, "steel_girder");
                        set(s[0] + 1, y, z, "steel_girder");
                    }
                }
            }
        }
        // the torpedo cars under the spouts, the slag pots under theirs
        torpedo(cx + 11, Foundry.HOT);
        torpedo(cx + 25, Foundry.HOT);
        for (int k = -2; k <= 2; k += 2) {
            set(cx + 12 + k, 1, Foundry.SLAG, "fw_truck_mid[facing=east]");
            set(cx + 12 + k, 2, Foundry.SLAG, "fr_slagpot");
        }
        // the shed: columns, the cladding on its upper half, the roof
        int top = 18;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (!edge) {
                    continue;
                }
                boolean col = (x == x0 || x == x1) && ((z - z0) % 7 == 0 || z == z1) || (z == z0 || z == z1) && ((x - x0) % 6 == 0 || x == x1);
                for (int y = FLOOR + 1; y <= top; y++) {
                    if (x == x0 && Math.abs(z - cz) <= 7 && y < 14) {
                        continue;  // open to the furnace
                    }
                    if (col) {
                        set(x, y, z, "steel_girder");
                    } else if (y >= 11 && c.chance() > 0.08 + c.b.damage * 0.12) {
                        String ax = z == z0 || z == z1 ? "x" : "z";
                        set(x, y, z, y == 14 && (x + z) % 4 == 0 ? "pw_sash_broken[facing=" + (z == z0 ? "north" : z == z1 ? "south" : x == x0 ? "west" : "east") + "]"
                                : c.chance() < 0.5 ? "corrugated_rust" : "corrugated_grey");
                    }
                }
            }
        }
        c.b.gable(x0, x1, z0, z1, top + 1, "minecraft:polished_tuff", false, "corrugated_rust", 1);
        // a monitor along the ridge to let the fume out
        int rz = (z0 + z1) / 2;
        for (int x = x0 + 3; x <= x1 - 3; x++) {
            for (int dz = -2; dz <= 2; dz++) {
                set(x, top + 19, rz + dz, Math.abs(dz) == 2 ? "corrugated_rust" : "air");
                set(x, top + 20, rz + dz, "corrugated_grey");
            }
        }
        // the stairs up from the yard, the crane rail, the gear
        c.steelStair(x1 + 4, cz + 4, 0, FLOOR, "west");
        c.steelStair(x0 + 3, z1 + 4, 0, FLOOR, "north");
        for (int x = x0 + 1; x < x1; x++) {
            set(x, 15, z0 + 1, "pw_crane_rail[facing=south]");
            set(x, 15, z1 - 1, "pw_crane_rail[facing=north]");
        }
        int kx = x0 + 10;
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            set(kx, 16, z, "pw_crane[axis=z]");
        }
        for (int y = FLOOR + 3; y < 16; y++) {
            set(kx, y, cz + 4, "pw_rope");
        }
        set(kx, FLOOR + 2, cz + 4, "pw_hook");
        set(cx + 7, FLOOR + 1, cz - 2, "pw_drill[facing=west]");
        set(cx + 7, FLOOR + 1, cz + 2, "pw_drum[axis=x]");
        set(x1 - 2, FLOOR + 1, z0 + 2, "pw_parts[facing=south]");
        set(x1 - 3, FLOOR + 1, z0 + 2, "barrel");
        c.sign("cast", x1 + 1, FLOOR + 3, cz + 6, "east");
        c.sign("hats", x0 + 5, FLOOR + 3, z1 + 1, "south");
        if (i == 0) {
            holdout(x0, x1, z0, z1);
        } else {
            for (int k = 0; k < 30; k++) {
                int x = x0 + 1 + c.rng.nextInt(x1 - x0 - 1), z = z0 + 1 + c.rng.nextInt(z1 - z0 - 1);
                if (c.empty(x, FLOOR + 1, z)) {
                    set(x, FLOOR + 1, z, c.any("paper", "debris_chunk[facing=" + c.dir() + "]", "pw_hardhats[facing=" + c.dir() + "]", "blood_splat"));
                }
            }
        }
    }

    /**
     * No. 1's cast house, where the night turn held out by the banked furnace: ingot moulds and
     * sandbags across the stairheads, cots along the warm side, the water and the food, a fire in a
     * drum, their words on the cladding.
     */
    private void holdout(int x0, int x1, int z0, int z1) {
        int y = FLOOR + 1;
        for (int z = z0 + 2; z <= z0 + 12; z += 2) {
            set(x0 + 4, y, z, "sc_cot_head[facing=west]");
            set(x0 + 5, y, z, "sc_cot_foot[facing=west]");
        }
        for (int x = x0 + 8; x <= x0 + 20; x += 2) {
            set(x, y, z0 + 2, "sc_cot_head[facing=north]");
            set(x, y, z0 + 3, "sc_cot_foot[facing=north]");
        }
        set(x0 + 12, y, z0 + 8, "rg_barrel_burnt");
        set(x0 + 13, y, z0 + 9, "trade_folding_chair[facing=west]");
        set(x0 + 11, y, z0 + 9, "trade_folding_chair[facing=east]");
        for (int k = 0; k < 4; k++) {
            set(x1 - 2, y, z0 + 4 + k, k % 2 == 0 ? "sc_cases_water[facing=west]" : "bunk_crate_ration[facing=west]");
        }
        // the barricades: moulds across the stairheads, sandbags on them
        int sz = (z0 + z1) / 2 + 1;
        for (int z = sz - 2; z <= sz + 2; z++) {
            set(x1 - 1, y, z, "fr_mould");
            set(x1 - 1, y + 1, z, z == sz ? "air" : "sandbags[facing=east]");
        }
        for (int x = x0 + 1; x <= x0 + 6; x++) {
            set(x, y, z1 - 1, "fr_mould");
            set(x, y + 1, z1 - 1, x % 2 == 0 ? "sandbags[facing=south]" : "air");
        }
        set(x0 + 9, y, z1 - 3, "home_corpse_head[facing=south]");
        set(x0 + 9, y, z1 - 2, "home_corpse_foot[facing=south]");
        set(x0 + 9, y, z1 - 4, "home_blood_pool");
        c.row("fr_spray_holding_", 5, x0 + 8, 12, z0, "south", 0.0);
        c.row("fr_spray_hot_", 3, x1, 12, z0 + 4, "west", 0.0);
    }

    /** A torpedo car 11 along x from x on the line at z: the round body on its trucks, the pouring mouth. */
    private void torpedo(int x, int z) {
        for (int i = 0; i <= 10; i++) {
            int xx = x + i;
            boolean end = i == 0 || i == 10;
            set(xx, 1, z, i == 1 || i == 2 || i == 8 || i == 9 ? "fw_truck_mid[facing=east]" : "tb_sleeper[axis=x]");
            if (end) {
                set(xx, 3, z, "fr_rust");
                continue;
            }
            set(xx, 2, z - 1, "fr_torp_bot[facing=north]");
            set(xx, 2, z + 1, "fr_torp_bot[facing=south]");
            set(xx, 2, z, "fr_rust");
            set(xx, 3, z - 1, "fr_rust_seam");
            set(xx, 3, z + 1, "fr_rust_seam");
            set(xx, 3, z, "fr_rust");
            set(xx, 4, z - 1, "fr_torp_top[facing=north]");
            set(xx, 4, z + 1, "fr_torp_top[facing=south]");
            set(xx, 4, z, i == 5 ? "fr_skull" : "fr_rust");
        }
        set(x + 5, 5, z, "hull_black");
    }

    // ------------------------------------------------------------------ the stockhouse

    /**
     * The bins along the furnaces' south side: a concrete bunker open on top with ore and coke in
     * its pockets, the trestle along it the ore cars came in on, the chutes on its north face where
     * the skips were filled.
     */
    private void stockhouse() {
        for (int x = BX0; x <= BX1; x++) {
            for (int z = BZ0; z <= BZ1; z++) {
                boolean edge = x == BX0 || x == BX1 || z == BZ0 || z == BZ1 || (x - BX0) % 15 == 0;
                for (int y = 1; y <= BH; y++) {
                    set(x, y, z, edge ? (c.chance() < 0.1 ? "concrete_panel_stain" : "concrete_panel") : "air");
                }
                if (!edge) {
                    int pocket = (x - BX0) / 15;
                    c.pile(x, z, 4 + c.chance() * 2 + (pocket % 2) * 1.5, pocket % 3 == 1 ? "coke" : "ore");
                }
            }
        }
        // the trestle along the middle and an ore car left on it
        int tz = (BZ0 + BZ1) / 2;
        for (int x = BX0 - 10; x <= BX1 + 10; x++) {
            set(x, BH + 2, tz - 1, "tb_rail[axis=x]");
            set(x, BH + 2, tz, "tb_sleeper[axis=x]");
            set(x, BH + 2, tz + 1, "tb_rail[axis=x]");
            set(x, BH + 1, tz, "steel_girder");
            if (x % 6 == 0) {
                for (int y = 1; y <= BH; y++) {
                    if (c.empty(x, y, tz)) {
                        set(x, y, tz, "steel_girder");
                    }
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int x = BX0 + 40 + i * 8;
            for (int k = 0; k < 6; k++) {
                for (int dz = -1; dz <= 1; dz++) {
                    set(x + k, BH + 3, tz + dz, k == 0 || k == 5 || dz != 0 ? "fr_rust" : "fr_ore");
                    set(x + k, BH + 4, tz + dz, dz == 0 && k > 0 && k < 5 ? "fr_ore_2" : k == 0 || k == 5 || dz != 0 ? "fr_rust" : "air");
                }
            }
        }
        // the chutes and gates on the north face
        for (int x = BX0 + 7; x < BX1; x += 15) {
            set(x, 4, BZ0 - 1, "pw_chute");
            set(x, 5, BZ0 - 1, "pw_hopper");
        }
        c.sign("ore", BX0 + 2, 6, BZ0 - 1, "north");
        c.sign("crane", BX1 - 6, 6, BZ0 - 1, "north");
    }
}
