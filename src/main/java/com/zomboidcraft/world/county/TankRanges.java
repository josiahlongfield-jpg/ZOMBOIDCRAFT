package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Caldwell Tank Ranges (Josiah, 2026-10-30: the northwest military zone), after refs/ranges and
 * refs/military, in its own frame round the plan's spot (x = RX + fx, z = RZ + fz) and built in tiles
 * ({@link FrameSite}):
 * <ul>
 * <li>the trail round inside the boundary, churned to mud and ruts, the gravel range road across
 * behind the firing line, the spurs in from wherever the tank trail meets it, the red flags and the
 * DANGER signs round the edge;</li>
 * <li>the firing line facing north: six firing points, each a hull-down berm of earth with its lane
 * post and the ammunition crates, a sand-coloured tank left at two of them;</li>
 * <li>downrange the lanes with their pop-up targets behind small berms, the rusting target hulks
 * shot full of holes, the tank tracks wandering between; the impact area at the far end, cratered,
 * burnt, strewn with fragments and the odd dud, blackened hulks;</li>
 * <li>behind the line the range control compound: the concrete control tower with its glass room, the
 * covered bleachers, the jeeps, the toilets, the flag; the ammunition igloos in their fence to the
 * west, earth-covered barrel vaults behind concrete headwalls; the watchtowers on the flanks.</li>
 * </ul>
 */
final class TankRanges extends FrameSite {
    /** The frame's extent round the spot; the trail's middle this far in from the edge. */
    static final int X0 = -400, X1 = 399, Z0 = -300, Z1 = 299, TILE_W = 240, RING = 14;
    /** The firing line, the range road behind it, the six firing points. */
    static final int LINE_Z = 196, ROAD_Z = 228;
    static final int[] POINTS = {-250, -150, -50, 50, 150, 250};
    /** The range control compound and the igloos. */
    static final int CX0 = -70, CX1 = 70, CZ0 = 238, CZ1 = 276, IX0 = -360, IZ0 = 240;

    private final Noise noise;

    TankRanges(Place p, BlockSink out) {
        super(p, out, 0x7A2C5L);
        noise = new Noise(seed);
    }

    static List<Place> places(Plan.Spot s, County county, Plan plan, long h) {
        return FrameSite.tiles("ranges", s, county, plan, h, X0, Z0, X1, Z1, TILE_W);
    }

    @Override
    void build() {
        for (int x = fx0; x <= fx1; x++) {
            for (int z = fz0; z <= fz1; z++) {
                ground(x, z);
            }
        }
        boundary();
        if (overlaps(X0, Z0, X1, Z1)) {
            copy(piece("range", 0x12A6EL, (q, r) -> new Range(q, r).build()));
        }
    }

    // ------------------------------------------------------------------ the ground

    private static int in(int x, int z) {
        return Math.min(Math.min(x - X0, X1 - x), Math.min(z - Z0, Z1 - z));
    }

    /** The ground: the trail round the edge, the range road and the spurs in gravel, the rest grass worn to dirt and churned by tracks. */
    private void ground(int x, int z) {
        double r = rnd(x, z, 0x6A55), n = noise.fbm(x, z, 28, 3) * 0.5 + 0.5;
        String g;
        int i = in(x, z);
        if (Math.abs(i - RING) <= 3) {
            g = Math.abs(i - RING) == 2 ? (r < 0.6 ? "minecraft:mud" : "minecraft:packed_mud") : r < 0.5 ? "minecraft:coarse_dirt" : r < 0.8 ? "minecraft:mud" : "minecraft:grass_block";
        } else if (Math.abs(z - ROAD_Z) <= 2 && i > RING || spur(x, z)) {
            g = r < 0.75 ? "minecraft:gravel" : "minecraft:coarse_dirt";
        } else if (rut(x, z)) {
            g = r < 0.55 ? "minecraft:mud" : r < 0.85 ? "minecraft:packed_mud" : "minecraft:coarse_dirt";
        } else if (z < -200) {
            // the impact area, the grass burnt off it
            g = n + r * 0.15 < 0.6 ? "burnt_ground" : n + r * 0.15 < 0.75 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
        } else {
            g = n + r * 0.12 < 0.36 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
        }
        a.set(x, 0, z, g);
        if (g.equals("minecraft:grass_block") && r > 0.9) {
            a.set(x, 1, z, r > 0.985 ? "weeds_tall" : r > 0.95 ? "grass_tuft" : "weeds");
        } else if (g.equals("burnt_ground") && r < 0.02) {
            a.set(x, 1, z, "rg_frag");
        }
    }

    /** The tank tracks wandering across the ground, pairs of ruts three apart. */
    private static boolean rut(int x, int z) {
        for (int k = 0; k < 5; k++) {
            double c = -260 + k * 110 + 40 * Math.sin(z / 37.0 + k * 1.7) + 18 * Math.sin(z / 13.0 + k);
            double d = Math.abs(x - c);
            if (d > 0.6 && d < 2.6 && z > -290 && z < LINE_Z - 8) {
                return true;
            }
        }
        return false;
    }

    /** The gravel in from each gate to the trail. */
    private boolean spur(int x, int z) {
        for (int[] g : gates) {
            boolean alongZ = (g[2] & 1) == 0;
            int across = alongZ ? x - g[0] : z - g[1], along = alongZ ? Math.abs(z - g[1]) : Math.abs(x - g[0]);
            if (Math.abs(across) <= 2 && along <= RING + 2) {
                return true;
            }
        }
        return false;
    }

    /** The red flags on their poles and the DANGER signs round the edge, outside the trail. */
    private void boundary() {
        for (int x = X0 + 4; x <= X1 - 4; x += 40) {
            flag(x, Z0 + 4, "north");
            flag(x, Z1 - 4, "south");
        }
        for (int z = Z0 + 44; z <= Z1 - 44; z += 40) {
            flag(X0 + 4, z, "west");
            flag(X1 - 4, z, "east");
        }
    }

    private void flag(int x, int z, String out) {
        boolean sign = Math.floorMod(x + z, 80) < 40;
        if (sign) {
            boolean alongX = out.equals("north") || out.equals("south");
            for (int i = 0; i < 2; i++) {
                int sx = alongX ? x + (out.equals("north") ? 1 - i : i) : x, sz = alongX ? z : z + (out.equals("east") ? 1 - i : i);
                a.set(sx, 1, sz, "rg_danger_" + i + "[facing=" + out + "]");
            }
            return;
        }
        for (int y = 1; y < 5; y++) {
            a.set(x, y, z, "cp_flagpole");
        }
        a.set(x, 5, z, "cp_flag[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the range itself

    /** Everything standing on the range, built once in the frame. */
    static final class Range extends Grounds {
        private final Camp camp;

        Range(Placer p, SplittableRandom rng) {
            super(p, rng, X1 - X0 + 1, Z1 - Z0 + 1, 0.4, 0.0, 0.1);
            camp = new Camp(p, rng, 0.4);
        }

        void build() {
            firingLine();
            downrange();
            impactArea();
            control();
            igloos();
            camp.watchtower(X0 + 40, LINE_Z - 6);
            camp.watchtower(X1 - 44, LINE_Z - 6);
            for (int z = LINE_Z - 60; z >= Z0 + 60; z -= 120) {
                set(X0 + 20, 1, z, "rg_tanks_0[facing=west]");
                set(X0 + 20, 1, z - 1, "rg_tanks_1[facing=west]");
            }
        }

        /** The six firing points: the berm in front, the lane post, the crates; a tank left at two. */
        private void firingLine() {
            for (int i = 0; i < POINTS.length; i++) {
                int x = POINTS[i];
                // the berm: a bank of earth 3 high and 14 long in front, a ramp up its back for the tank to sit hull-down
                for (int dx = -7; dx <= 7; dx++) {
                    for (int dz = 0; dz < 6; dz++) {
                        int h = dz < 2 ? 3 : dz < 4 ? 2 : 1;
                        if (Math.abs(dx) == 7) {
                            h = Math.max(1, h - 1);
                        }
                        for (int y = 1; y <= h; y++) {
                            set(x + dx, y, LINE_Z - 6 + dz, y == h ? "minecraft:grass_block" : "minecraft:dirt");
                        }
                        if (h == 3 && chance() < 0.3) {
                            set(x + dx, 4, LINE_Z - 6 + dz, any("weeds", "grass_tuft", "minecraft:short_dry_grass"));
                        }
                    }
                }
                set(x - 9, 1, LINE_Z - 2, "cp_lane_post[facing=south]");
                for (int k = 0; k < 3; k++) {
                    if (chance() < 0.7) {
                        set(x + 9, 1, LINE_Z + k, any("bunk_crate_ammo[facing=west]", "pk_military_crate", "pk_military_crate_old"));
                    }
                }
                if (i == 1 || i == 4) {
                    Tanks.tank(p, x - 2, LINE_Z, 0, "sand", 0.1, rng);
                }
                for (int dx = -6; dx <= 6; dx++) {
                    set(x + dx, 0, LINE_Z + 10, "minecraft:gravel");
                }
            }
        }

        /** The lanes' targets behind their berms, the target hulks, the lane posts down the lines. */
        private void downrange() {
            int[] rows = {140, 90, 40, -10, -60, -110, -160};
            for (int i = 0; i < POINTS.length; i++) {
                int x = POINTS[i];
                for (int k = 0; k < rows.length; k++) {
                    int z = rows[k] + (int) (chance() * 14) - 7, tx = x + (int) (chance() * 50) - 25;
                    if ((i * 3 + k) % 4 == 1) {
                        // a hulk: a turned-out tank, rusted and holed, set side-on or head-on to the line
                        Tanks.tank(p, tx - 2, z - 4, rng.nextInt(4), chance() < 0.75 ? "rust" : "burnt", 0.6, rng);
                    } else {
                        for (int dx = -2; dx <= 2; dx++) {
                            set(tx + dx, 1, z + 2, "minecraft:dirt");
                            set(tx + dx, 2, z + 2, dx == 0 ? "minecraft:dirt" : "minecraft:grass_block");
                        }
                        // a pair of pop-ups behind the bank, a burnt patch where one caught fire
                        for (int t = -1; t <= 1; t += 2) {
                            set(tx + t, 1, z, chance() < 0.25 ? "cp_target_down[facing=south]" : "cp_target_lo[facing=south]");
                            if (!at(tx + t, 1, z).startsWith("cp_target_down")) {
                                set(tx + t, 2, z, (chance() < 0.5 ? "cp_target_hi_shot" : "cp_target_hi") + "[facing=south]");
                            }
                        }
                        if (chance() < 0.3) {
                            set(tx + 3, 1, z - 1, "scorch");
                        }
                        set(tx - 3, 1, z + 3, "tn_cable[axis=z]");
                    }
                }
                for (int z = LINE_Z - 20; z > -200; z -= 50) {
                    set(x + 50, 1, z, "cp_lane_post[facing=south]");
                }
            }
        }

        private String at(int x, int y, int z) {
            String s = p.get(x, y, z);
            return s == null ? "" : s;
        }

        /** The impact area: craters blown in it, scorched, a dud here and there, the burnt-out hulks. */
        private void impactArea() {
            for (int k = 0; k < 70; k++) {
                int cx = X0 + 40 + rng.nextInt(X1 - X0 - 80), cz = Z0 + 24 + rng.nextInt(80), r = 2 + rng.nextInt(5);
                crater(cx, cz, r);
            }
            for (int k = 0; k < 4; k++) {
                Tanks.tank(p, -300 + k * 170 + rng.nextInt(40), -260 + rng.nextInt(40), rng.nextInt(4), "burnt", 0.8, rng);
            }
            for (int k = 0; k < 30; k++) {
                int x = X0 + 30 + rng.nextInt(X1 - X0 - 60), z = Z0 + 20 + rng.nextInt(110);
                if (p.get(x, 1, z) == null) {
                    set(x, 1, z, chance() < 0.3 ? "rg_shell" : "rg_frag");
                }
            }
        }

        private void crater(int cx, int cz, int r) {
            for (int dx = -r - 2; dx <= r + 2; dx++) {
                for (int dz = -r - 2; dz <= r + 2; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= r) {
                        int depth = (int) Math.round((r - d) * 0.6) + 1;
                        set(cx + dx, 0, cz + dz, "air");
                        for (int y = -1; y >= -depth; y--) {
                            set(cx + dx, y, cz + dz, y == -depth ? (chance() < 0.3 ? "minecraft:mud" : "minecraft:coarse_dirt") : "air");
                        }
                        if (chance() < 0.08) {
                            set(cx + dx, -depth + 1, cz + dz, "rg_frag");
                        }
                    } else if (d <= r + 2) {
                        // the lip thrown up round it, scorched
                        set(cx + dx, 1, cz + dz, d <= r + 1 ? "minecraft:coarse_dirt" : "air");
                        if (d > r + 1 && chance() < 0.3) {
                            set(cx + dx, 1, cz + dz, "scorch");
                        }
                    }
                }
            }
        }

        /** The range control compound: the tower, the bleachers, the parking, the flag, the fence. */
        private void control() {
            camp.fence(CX0, CZ0, CX1, CZ0, 0.05);
            camp.fence(CX0, CZ1, CX1, CZ1, 0.05);
            camp.fence(CX0, CZ0, CX0, CZ1, 0.05);
            camp.fence(CX1, CZ0, CX1, CZ1, 0.05);
            for (int x = -3; x <= 3; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, CZ0, "air");
                }
            }
            for (int x = CX0 + 1; x < CX1; x++) {
                for (int z = CZ0 + 1; z < CZ1; z++) {
                    set(x, 0, z, chance() < 0.9 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                }
            }
            tower(-6, CZ0 + 6);
            // the covered bleachers facing the range
            for (int x = CX0 + 6; x <= CX0 + 26; x++) {
                for (int r = 0; r < 4; r++) {
                    set(x, 1 + r, CZ0 + 6 + r, "sc_bleacher[facing=north]");
                    for (int y = 1; y <= r; y++) {
                        set(x, y, CZ0 + 6 + r, "sc_bleacher_frame");
                    }
                }
                set(x, 7, CZ0 + 5, "corrugated_green_b");
                for (int z = CZ0 + 6; z <= CZ0 + 10; z++) {
                    set(x, 7, z, "corrugated_green_b");
                }
            }
            for (int x = CX0 + 6; x <= CX0 + 26; x += 10) {
                for (int y = 1; y < 7; y++) {
                    set(x, y, CZ0 + 10, "steel_olive");
                }
            }
            for (int k = 0; k < 5; k++) {
                p.vehicle(CX1 - 30 + k * 6 + 0.5, 1, CZ1 - 8.5, 180f, any("jeep", "jeep_open", "jeep", "box_truck"), rng.nextLong());
            }
            for (int k = 0; k < 3; k++) {
                set(CX1 - 4, 1, CZ0 + 6 + k * 2, chance() < 0.15 ? "cp_porta_down[facing=west]" : "st_porta_lo[facing=west]");
                if (!at(CX1 - 4, 1, CZ0 + 6 + k * 2).startsWith("cp_porta_down")) {
                    set(CX1 - 4, 2, CZ0 + 6 + k * 2, "st_porta_hi[facing=west]");
                }
            }
            for (int y = 1; y < 9; y++) {
                set(12, y, CZ0 + 4, "cp_flagpole");
            }
            set(12, 9, CZ0 + 4, "cp_flag[facing=north]");
            camp.floodMast(CX0 + 2, CZ0 + 2, 8);
            camp.floodMast(CX1 - 2, CZ0 + 2, 8);
        }

        /**
         * The range control tower: a concrete block 9 square, two storeys of offices with slits of
         * windows, the stair up the back, the control room on top glazed all round and wider than the
         * shaft, its roof with the masts.
         */
        private void tower(int x, int z) {
            int s = 9, room = 9;
            for (int i = 0; i < s; i++) {
                for (int k = 0; k < s; k++) {
                    boolean wall = i == 0 || k == 0 || i == s - 1 || k == s - 1;
                    set(x + i, 0, z + k, wall ? "concrete_dark" : "floor_concrete");
                    for (int y = 1; y < room; y++) {
                        String out = k == 0 ? "north" : k == s - 1 ? "south" : i == 0 ? "west" : "east";
                        boolean slit = wall && (y == 3 || y == 6) && (i == 4 || k == 4);
                        set(x + i, y, z + k, !wall ? "air" : slit ? "wf_glass[facing=" + out + "]" : "ml_precast");
                    }
                    if (!wall) {
                        set(x + i, 4, z + k, "floor_concrete");
                    }
                }
            }
            // the stair: a ladder up the back corner through each floor
            for (int y = 1; y < room + 1; y++) {
                set(x + 1, y, z + s - 2, "ladder[facing=north]");
            }
            set(x + 1, 4, z + s - 2, "ladder[facing=north]");
            set(x + 4, 1, z + s - 1, "air");
            set(x + 4, 2, z + s - 1, "air");
            door(x + 4, 0, z + s - 1, Doors.FLAT, "south");
            set(x + 6, 5, z + 2, "home_desk_pc[facing=north]");
            set(x + 2, 1, z + 2, any("pk_military_crate", "bunk_radio_desk[facing=north]"));
            // the control room: a block out over the shaft all round, windows from the sill up
            int y0 = room, top = room + 4;
            for (int i = -1; i <= s; i++) {
                for (int k = -1; k <= s; k++) {
                    boolean wall = i == -1 || k == -1 || i == s || k == s;
                    boolean post = (i == -1 || i == s) && (k == -1 || k == s);
                    String out = k == -1 ? "north" : k == s ? "south" : i == -1 ? "west" : "east";
                    set(x + i, y0, z + k, "floor_concrete");
                    for (int y = y0 + 1; y < top; y++) {
                        set(x + i, y, z + k, !wall ? "air" : post || y == y0 + 1 ? "ml_precast" : (chance() < 0.3 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]");
                    }
                    set(x + i, top, z + k, "un_roof");
                }
            }
            b.flat(x - 1, z - 1, x + s, z + s, top, "ml_precast", 0, "ml_precast");
            set(x + 1, y0, z + s - 2, "ladder[facing=north]");
            for (int i = 1; i < s - 1; i++) {
                set(x + i, y0 + 1, z, any("bunk_radio_desk[facing=north]", "home_desk_pc[facing=north]", "bunk_map_table"));
            }
            set(x + 3, y0 + 1, z + 2, "office_chair[facing=north]");
            set(x + 5, y0 + 1, z + 2, "office_chair[facing=north]");
            set(x + 7, y0 + 1, z + 6, "bunk_map_wall[facing=west]");
            for (int y = top + 1; y <= top + 5; y++) {
                set(x + 1, y, z + 1, y % 2 == 0 ? "cp_mast_r" : "cp_mast_w");
            }
            set(x + 1, top + 6, z + 1, "cp_mast_top[facing=north]");
            set(x + 7, top + 1, z + 7, "tv_beacon");
        }

        /**
         * The ammunition igloos: four earth-covered barrel vaults, each behind its concrete headwall
         * with the steel doors to the north, the mound grassed over; the fence round them.
         */
        private void igloos() {
            int w = 11, len = 18, rise = 6;
            for (int k = 0; k < 4; k++) {
                int x0 = IX0 + k * 26, x1 = x0 + w - 1, z0 = IZ0, z1 = z0 + len - 1;
                double cx = x0 + w / 2.0, r = rise, cy = 1;
                Map<Long, double[]> cells = ArmyFieldBase.arc(cx, cy, r, x0, x0 + w);
                for (int x = x0 - 4; x <= x1 + 4; x++) {
                    double dx = x + 0.5 - cx;
                    double inner = Math.sqrt(Math.max(0, r * r - dx * dx)), mound = Math.sqrt(Math.max(0, (r + 3) * (r + 3) - dx * dx));
                    for (int z = z0; z <= z1 + 3; z++) {
                        set(x, 0, z, x >= x0 && x <= x1 && z <= z1 ? "floor_concrete" : "minecraft:dirt");
                        int mz = z > z1 ? z1 + 3 - z : 3;  // the mound slopes off at the back
                        double top = Math.min(mound, mound - (3 - mz)) + 1;
                        for (int y = 1; y < top; y++) {
                            boolean under = Math.abs(dx) < r && y + 0.5 < cy + inner && z <= z1;
                            if (under) {
                                set(x, y, z, "air");
                                continue;
                            }
                            set(x, y, z, y + 1 >= top ? "minecraft:grass_block" : "minecraft:dirt");
                        }
                    }
                }
                for (double[] c : cells.values()) {
                    for (int z = z0; z <= z1; z++) {
                        set((int) c[0], (int) c[1], z, "mf_vault_" + (z == z0 || z == z1 ? "end_" : "") + (int) c[2] + "_" + (int) c[3]);
                    }
                }
                for (int x = x0; x <= x1; x++) {
                    for (int y = 1; y <= rise; y++) {
                        double dx = x + 0.5 - cx;
                        if (y + 0.5 < cy + Math.sqrt(Math.max(0, r * r - dx * dx)) && !cells.containsKey(((long) x << 32) ^ (y & 0xffffffffL))) {
                            set(x, y, z1, "concrete_dark");
                        }
                    }
                }
                // the headwall: a slab of concrete wider and taller than the vault, the steel doors in it
                for (int x = x0 - 2; x <= x1 + 2; x++) {
                    // the headwall follows the mound down to wing walls at the sides
                    double dx = x + 0.5 - cx;
                    int h = (int) Math.round(Math.min(rise + 1, Math.sqrt(Math.max(0, (r + 2) * (r + 2) - dx * dx)) + 1));
                    for (int y = 1; y <= Math.max(2, h); y++) {
                        boolean door = x >= (int) cx - 2 && x <= (int) cx + 1 && y <= 3;
                        set(x, y, z0 - 1, door ? "air" : y == Math.max(2, h) ? "concrete_dark" : "tx_concrete");
                    }
                    set(x, 0, z0 - 1, "concrete_dark");
                }
                for (int x = (int) cx - 2; x <= (int) cx + 1; x++) {
                    for (int y = 1; y <= 3; y++) {
                        set(x, y, z0 - 1, chance() < 0.25 && y < 3 ? "air" : "garage_door[facing=north]");
                    }
                }
                set((int) cx - 3, 4, z0 - 2, Camp.lamp(rng, 0.6, "cp_flood", "north"));
                for (int x = x0 + 1; x < x1; x += 2) {
                    if (chance() < 0.5) {
                        set(x, 1, z0 + 2 + rng.nextInt(len - 4), any("bunk_crate_ammo[facing=north]", "pk_military_crate", "pk_military_crate_old"));
                    }
                }
            }
            camp.fence(IX0 - 8, IZ0 - 8, IX0 + 4 * 26, IZ0 - 8, 0.08);
            camp.fence(IX0 - 8, IZ0 - 8, IX0 - 8, IZ0 + 24, 0.08);
            camp.fence(IX0 + 4 * 26, IZ0 - 8, IX0 + 4 * 26, IZ0 + 24, 0.08);
        }
    }
}
