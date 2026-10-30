package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Everything standing on Caldwell Army Airfield's ramp and behind it ({@link ArmyField}), after
 * refs/airfield-mil and refs/military, built once in the field's frame and copied into its tiles:
 * <ul>
 * <li>the fighter revetments, a row of bays along the ramp's front walled in earth-filled steel bins
 * four high (the mf_ kit), a jet nosed out in most with its power cart, chocks and tool chest;</li>
 * <li>the helicopter pads east of them, each a tan membrane pad on pierced steel planking with low
 * timber cribs round its back and side, a gunship on some;</li>
 * <li>two round-roofed hangars, the corrugated vault on low walls, the doors run back to the sides, a
 * jet inside each under maintenance (the scaffold, the engine out on its stand, the benches), the
 * shops between them;</li>
 * <li>the lattice control tower with its glass cab and catwalk, the ops building beside it with the
 * briefing room and the crew room, the flag;</li>
 * <li>the fuel bladders in their berm, the vehicle sheds with the jeeps and the crash truck, the
 * Hesco wall along the back, the floodlight masts, a watchtower at the back corners.</li>
 * </ul>
 */
final class ArmyFieldBase extends Grounds {
    /** The fighter revetments: the first bay's west wall at REV_X, BAYS bays each BAY wide inside, the back wall's front face at REV_Z0, the bays open at REV_Z1. */
    static final int REV_X = 40, BAYS = 8, BAY = 22, REV_Z0 = -152, REV_Z1 = -125, REV_H = 4;
    /** The helicopter pads: three a row from HELI_X, the rows' backs at these az. */
    static final int HELI_X = 270, HELI_PITCH = 26;
    static final int[] HELI_ROWS = {-196, -150};
    /** The hangars: west walls at these ax, VW wide, from VZ0 back to the doors at VZ1; walls VWALL high, the vault rising VRISE over them. */
    static final int[] VX = {-340, -270};
    static final int VW = 40, VZ0 = -272, VZ1 = -221, VWALL = 8, VRISE = 12, VDOOR = 14;
    /** The tower's lattice and the ops building. */
    static final int TX = -160, TZ = -262, TDECK = 15;
    static final int OX0 = -140, OX1 = -101, OZ0 = -250, OZ1 = -229;
    /** Everything lies within these. */
    static final int BX0 = -560, BX1 = 560, BZ0 = ArmyField.LAND_Z0 + ArmyField.PERIM + 3, BZ1 = ArmyField.RAMP_Z1;

    private final Camp camp;

    ArmyFieldBase(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.3, 0.0, 0.2);
        camp = new Camp(p, rng, 0.3);
    }

    void build() {
        backLot();
        revetments();
        heliPads();
        for (int k = 0; k < VX.length; k++) {
            hangar(VX[k], k);
        }
        shops(VX[0] + VW, VX[1] - 1);
        tower(TX, TZ);
        ops();
        support();
    }

    // ------------------------------------------------------------------ the ground behind the ramp

    /** North of the ramp to the perimeter road: packed gravel and grass worn through, the roads between the buildings. */
    private void backLot() {
        for (int x = BX0; x <= BX1; x++) {
            for (int z = BZ0; z < ArmyField.RAMP_Z0; z++) {
                if (x >= -530 && x <= ArmyField.RAMP_X1 + 30) {
                    // packed gravel, worn to dirt in patches, the grass coming back at the edges
                    double n = Noise.unit(Noise.hash(0x6BA7L, x >> 3, z >> 3)) * 0.6 + chance() * 0.4;
                    boolean edge = x < -500 || x > ArmyField.RAMP_X1 || z < BZ0 + 3;
                    set(x, 0, z, n < (edge ? 0.45 : 0.68) ? "minecraft:gravel" : n < (edge ? 0.6 : 0.86) ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the fighter revetments

    private void revetments() {
        String[] jets = {"jet_corvid", "jet_gyrfalcon"};
        int pitch = BAY + 2;
        for (int i = 0; i <= BAYS; i++) {
            // the walls between the bays, two bins thick, the back wall behind
            int wx = REV_X + i * pitch;
            binWall(wx, REV_Z0, wx + 1, REV_Z1, REV_H);
        }
        binWall(REV_X, REV_Z0 - 2, REV_X + BAYS * pitch + 1, REV_Z0 - 1, REV_H);
        for (int i = 0; i < BAYS; i++) {
            int x0 = REV_X + 2 + i * pitch, cx = x0 + BAY / 2;
            // the bay's painted number on the back wall, the lead-in line out to the ramp
            for (int z = REV_Z0 + 1; z <= ArmyField.RAMP_Z1 - 1; z++) {
                set(cx, 0, z, "af_paint_y");
            }
            if (chance() < 0.8) {
                p.vehicle(cx + 0.5, 1, REV_Z0 + 13.5, 0f, "=" + jets[i % 2] + (i % 3 == 0 ? "@usable" : ""), rng.nextLong());
                set(cx, 1, REV_Z0 + 18, "rp_chocks[facing=east]");
                set(x0 + 2, 1, REV_Z0 + 2, "rp_gpu[facing=east]");
                set(x0 + BAY - 2, 1, REV_Z0 + 2, any("pk_tool_chest[facing=west]", "navy_toolbox[facing=west]", "pw_drum"));
                set(x0 + 1, 1, REV_Z0 + 18, "trade_stepladder[facing=east]");
            } else {
                // an empty bay: the oil where one stood, the cart and the drums left
                set(cx, 0, REV_Z0 + 12, "navy_hangar_oil");
                set(cx - 3, 1, REV_Z0 + 6, any("rp_gpu[facing=south]", "pw_drum", "pk_military_crate"));
            }
            if (chance() < 0.5) {
                set(x0 + 1, 1, REV_Z1 - 1, "st_cone");
            }
        }
    }

    /** A wall of revetment bins from (x0, z0) to (x1, z1) h high: the earth showing in the top course, the closed ends. */
    private void binWall(int x0, int z0, int x1, int z1, int h) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean end = z == Math.max(z0, z1) && x1 - x0 < z1 - z0;
                set(x, 0, z, "minecraft:coarse_dirt");
                for (int y = 1; y <= h; y++) {
                    set(x, y, z, y == h ? "mf_bin_top" : end ? "mf_bin_end" : "mf_bin");
                }
                if (chance() < 0.25) {
                    set(x, h + 1, z, any("grass_tuft", "weeds", "minecraft:short_dry_grass"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the helicopter pads

    private void heliPads() {
        int n = 0;
        for (int row : HELI_ROWS) {
            for (int k = 0; k < 3; k++, n++) {
                int x0 = HELI_X + k * HELI_PITCH, z0 = row;
                // the pad: planking 20 square, the membrane 12 square in its middle
                for (int x = x0; x < x0 + 20; x++) {
                    for (int z = z0 + 2; z < z0 + 22; z++) {
                        boolean mem = x >= x0 + 4 && x < x0 + 16 && z >= z0 + 6 && z < z0 + 18;
                        set(x, 0, z, mem ? "mf_membrane" : "mf_psp");
                    }
                }
                // the cribs: two high, along the back and down the west side
                for (int x = x0 - 1; x < x0 + 20; x++) {
                    crib(x, z0);
                    crib(x, z0 + 1);
                }
                for (int z = z0 + 2; z < z0 + 16; z++) {
                    crib(x0 - 1, z);
                }
                if (n % 2 == 0) {
                    p.vehicle(x0 + 10.0, 1, z0 + 12.0 + 1.84, 180f, "=heli_hornbill" + (n == 0 ? "@usable" : ""), rng.nextLong());
                } else {
                    set(x0 + 4, 1, z0 + 3, any("bunk_jerry_cans[facing=south]", "pw_drum", "pk_military_crate"));
                }
                set(x0 + 18, 1, z0 + 3, "navy_extinguisher[facing=south]");
            }
        }
    }

    private void crib(int x, int z) {
        set(x, 0, z, "minecraft:coarse_dirt");
        set(x, 1, z, "mf_crib");
        set(x, 2, z, chance() < 0.08 ? "minecraft:coarse_dirt" : "mf_crib");
    }

    // ------------------------------------------------------------------ the hangars

    /**
     * A round-roofed hangar from x0, VZ0..VZ1: the concrete floor, the walls VWALL high, the vault
     * over them in the mf_vault pieces (a segment of a circle rising VRISE over the span), the back
     * filled in under it, the front open through its door with the leaves run back to the sides.
     */
    private void hangar(int x0, int k) {
        int x1 = x0 + VW - 1;
        double half = VW / 2.0, cx = x0 + half;
        double r = (half * half + VRISE * VRISE) / (2.0 * VRISE);
        // the arc's centre: the springing at the top of the walls, its foot at y VWALL + 1
        double cy = VWALL + 1 + VRISE - r;
        for (int x = x0; x <= x1; x++) {
            for (int z = VZ0; z <= VZ1; z++) {
                boolean wall = x == x0 || x == x1;
                set(x, 0, z, wall ? "concrete_dark" : chance() < 0.03 ? "navy_hangar_oil" : "navy_hangar_floor");
                for (int y = 1; y <= VWALL; y++) {
                    set(x, y, z, wall ? (y <= 2 ? "concrete_dark" : "corrugated_grey") : "air");
                }
            }
        }
        Map<Long, double[]> cells = arc(cx, cy, r, x0, x0 + VW);
        for (double[] c : cells.values()) {
            for (int z = VZ0; z <= VZ1; z++) {
                // at the ends the piece with the gable's cladding filled in under it
                set((int) c[0], (int) c[1], z, "mf_vault_" + (z == VZ0 || z == VZ1 ? "end_" : "") + (int) c[2] + "_" + (int) c[3]);
            }
        }
        // the gable ends filled in under the arc: the back whole, the front over the door
        for (int x = x0; x <= x1; x++) {
            double top = cy + Math.sqrt(Math.max(0, r * r - (x + 0.5 - cx) * (x + 0.5 - cx)));
            for (int y = 1; y < top - 0.6; y++) {
                if (cells.containsKey(key(x, y))) {
                    continue;
                }
                set(x, y, VZ0, y <= 2 ? "concrete_dark" : "corrugated_grey");
                boolean door = x >= x0 + 4 && x <= x1 - 4 && y <= VDOOR;
                set(x, y, VZ1, door ? "air" : y == VDOOR + 1 ? "corrugated_green_b" : "corrugated_grey");
            }
        }
        // the leaves run back over the wall ends, two a side on the two tracks
        for (int side = 0; side < 2; side++) {
            int lx = side == 0 ? x0 - 4 : x1 - 3;
            for (int t = 0; t < 2; t++) {
                for (int x = lx; x < lx + 8; x++) {
                    set(x, 0, VZ1 + 1 + t, "ah_track[axis=x]");
                    for (int y = 1; y <= VDOOR; y++) {
                        set(x, y, VZ1 + 1 + t, (y == 1 ? "ah_leaf_band" : x == lx + (side == 0 ? 7 : 0) ? "ah_leaf_edge" : "ah_leaf") + "[facing=south]");
                    }
                    set(x, VDOOR + 1, VZ1 + 1 + t, "corrugated_green_b");
                }
            }
        }
        for (int x = x0 + 4; x <= x1 - 4; x++) {
            set(x, 0, VZ1 + 1, "ah_track[axis=x]");
            set(x, 0, VZ1 + 2, "ah_track[axis=x]");
        }
        // the floods over the door, the lamps hung down the middle
        for (int x = x0 + 8; x <= x1 - 8; x += 12) {
            set(x, VDOOR + 2, VZ1 + 1, (power && chance() < 0.4 ? "cp_flood_lit" : "cp_flood") + "[facing=south]");
        }
        for (int z = VZ0 + 6; z < VZ1; z += 10) {
            set((int) cx, (int) (cy + r) - 2, z, Camp.lamp(rng, 0.5, "cp_flood_hang", "south"));
        }
        // the side doors and windows along the walls
        for (int z = VZ0 + 5; z < VZ1 - 3; z += 9) {
            for (int x : new int[] {x0, x1}) {
                set(x, 4, z, (chance() < 0.2 ? "wf_glass_broken" : "wf_glass") + "[facing=" + (x == x0 ? "west" : "east") + "]");
                set(x, 5, z, (chance() < 0.2 ? "wf_glass_broken" : "wf_glass") + "[facing=" + (x == x0 ? "west" : "east") + "]");
            }
        }
        set(x1, 1, VZ0 + 4, "air");
        set(x1, 2, VZ0 + 4, "air");
        door(x1, 0, VZ0 + 4, Doors.FLAT, "east");
        // inside: a jet in for work, the scaffold along it, the engine out on its stand, the benches
        String jet = k == 0 ? "jet_gyrfalcon" : "jet_corvid";
        p.vehicle(cx, 1, VZ0 + 24.5, 0f, "=" + jet + "@usable", rng.nextLong());
        for (int z = VZ0 + 18; z <= VZ0 + 30; z++) {
            set(x0 + 10, 1, z, "ah_scaf");
            set(x0 + 10, 2, z, "ah_scaf_deck");
            set(x0 + 10, 3, z, "ah_scaf_rail[facing=east]");
        }
        set(x1 - 8, 1, VZ0 + 8, "ah_engine_0[facing=south]");
        set(x1 - 8, 1, VZ0 + 9, "ah_engine_1[facing=south]");
        for (int z = VZ0 + 2; z < VZ1 - 4; z += 3) {
            set(x0 + 1, 1, z, any("home_workbench[facing=east]", "pk_tool_chest[facing=east]", "navy_toolbox[facing=east]", "pw_drum"));
            set(x1 - 1, 1, z, any("pk_military_crate", "pk_military_crate_old", "pw_drum", "bunk_crate_ammo[facing=west]"));
        }
        set((int) cx + 6, 1, VZ1 - 6, "rp_gpu[facing=north]");
        set((int) cx - 6, 1, VZ0 + 3, "trade_stepladder[facing=south]");
        for (int x = x0 + 3; x <= x1 - 3; x++) {
            set(x, 0, VZ1 - 2, "af_paint_y");
        }
        mess(x0 + 1, VZ0 + 1, x1 - 1, VZ1 - 1, 0, 0.02);
    }

    private static long key(int x, int y) {
        return ((long) x << 32) ^ (y & 0xffffffffL);
    }

    /**
     * The blocks an arc of radius r round (cx, cy) passes between xLo and xHi, each with its mf_vault
     * piece: {x, y, slope index, depth index}, the slope the arc's normal there in steps of 22.5
     * from -90, the depth the arc's distance from the block's middle along it in steps of 2 px.
     */
    static Map<Long, double[]> arc(double cx, double cy, double r, double xLo, double xHi) {
        Map<Long, double[]> acc = new LinkedHashMap<>();
        double lim = Math.asin(Math.min(1, (xHi - cx) / r));
        int n = (int) Math.max(64, 2 * lim * r / 0.02);
        for (int i = 0; i <= n; i++) {
            double th = -lim + 2 * lim * i / n, x = cx + r * Math.sin(th), y = cy + r * Math.cos(th);
            int bx = (int) Math.floor(Math.min(x, xHi - 1e-6)), by = (int) Math.floor(y);
            double[] a = acc.computeIfAbsent(key(bx, by), q -> new double[] {bx, by, 0, 0, 0, 0, 0});
            a[2] += Math.sin(th);
            a[3] += Math.cos(th);
            a[4] += x;
            a[5] += y;
            a[6]++;
        }
        Map<Long, double[]> out = new LinkedHashMap<>();
        for (Map.Entry<Long, double[]> e : acc.entrySet()) {
            double[] a = e.getValue();
            double len = Math.hypot(a[2], a[3]), nx = a[2] / len, ny = a[3] / len;
            double slope = Math.toDegrees(Math.atan2(nx, ny));
            int si = (int) Math.max(0, Math.min(8, Math.round((slope + 90) / 22.5)));
            double px = a[4] / a[6] - (a[0] + 0.5), py = a[5] / a[6] - (a[1] + 0.5);
            double depth = (px * nx + py * ny) * 16;
            int di = (int) Math.max(0, Math.min(8, Math.round((depth + 8) / 2)));
            out.put(e.getKey(), new double[] {a[0], a[1], si, di});
        }
        return out;
    }

    /** The shops between the hangars: a flat-roofed block of workshops and stores, its roller doors to the ramp. */
    private void shops(int x0, int x1) {
        int z0 = VZ0 + 10, z1 = VZ1 - 2, top = 6;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, wall ? "concrete_dark" : "floor_concrete");
                for (int y = 1; y < top; y++) {
                    set(x, y, z, wall ? (y == top - 1 ? "ml_precast_band" : "ml_precast") : "air");
                }
                set(x, top, z, "un_roof");
            }
        }
        b.flat(x0, z0, x1, z1, top, "ml_precast", 0, "ml_precast");
        for (int x = x0 + 3; x <= x0 + 7; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, z1, y == 4 ? "ml_precast" : "air");
            }
            set(x, 4, z1, "garage_door[facing=south]");
        }
        partZ(x0 + 10, z0 + 1, z1 - 1, 0, top, "drywall", "drywall");
        set(x0 + 10, 1, z1 - 3, "air");
        set(x0 + 10, 2, z1 - 3, "air");
        door(x0 + 10, 0, z1 - 3, Doors.FLAT, "east");
        for (int z = z0 + 2; z < z1 - 1; z += 2) {
            set(x0 + 1, 1, z, any("home_workbench[facing=east]", "pk_tool_chest[facing=east]", "pw_drill[facing=east]"));
            set(x1 - 1, 1, z, any("store_shelf_hardware[facing=west]", "store_shelf_ammo[facing=west]", "pk_military_crate"));
        }
        set(x1 - 3, 1, z1, "air");
        set(x1 - 3, 2, z1, "air");
        door(x1 - 3, 0, z1, Doors.FLAT, "south");
        lamps(x0, z0, x1, z1, top, 0.3);
        mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.04);
    }

    // ------------------------------------------------------------------ the tower and the ops building

    /**
     * The control tower: a steel lattice of four legs 6 apart braced in tiers to the deck at TDECK,
     * the glass cab on it with the consoles, the catwalk round with its rail, the stair up inside the
     * lattice, the beacon and the anemometer on the roof.
     */
    private void tower(int x, int z) {
        int s = 6;
        String[] turn = {"north", "east", "south", "west"};
        int[][] corner = {{0, 0}, {s, 0}, {s, s}, {0, s}};
        for (int c = 0; c < 4; c++) {
            for (int y = 1; y < TDECK; y++) {
                set(x + corner[c][0], y, z + corner[c][1], "cp_tw_leg[facing=" + turn[c] + "]");
            }
            set(x + corner[c][0], 0, z + corner[c][1], "concrete_dark");
        }
        for (int c = 0; c < 4; c++) {
            for (int u = 1; u < s; u++) {
                int[] at = switch (c) {
                    case 0 -> new int[] {x + u, z};
                    case 1 -> new int[] {x + s, z + u};
                    case 2 -> new int[] {x + s - u, z + s};
                    default -> new int[] {x, z + s - u};
                };
                for (int t = 1; t < TDECK; t++) {
                    if ((t % 3 == 0) && chance() > 0.05) {
                        set(at[0], t, at[1], "cp_tw_girt[facing=" + turn[c] + "]");
                    } else if ((t + u) % 3 == 1 && chance() > 0.08) {
                        set(at[0], t, at[1], "cp_tw_brace_a[facing=" + turn[c] + "]");
                    }
                }
            }
        }
        // the stair: a ladder up the middle of the lattice, a landing every 5
        for (int y = 1; y < TDECK; y++) {
            set(x + 3, y, z + 3, "ladder[facing=north]");
            if (y % 5 == 0) {
                for (int i = 2; i <= 4; i++) {
                    for (int k = 4; k <= 4; k++) {
                        set(x + i, y - 1, z + k, "cp_tw_deck");
                    }
                }
            }
        }
        // the deck, a block out round the cab for the catwalk
        for (int i = -1; i <= s + 1; i++) {
            for (int k = -1; k <= s + 1; k++) {
                boolean edge = i == -1 || k == -1 || i == s + 1 || k == s + 1;
                if (!(i == 3 && k == 3)) {
                    set(x + i, TDECK, z + k, "cp_tw_deck");
                }
                if (edge) {
                    set(x + i, TDECK + 1, z + k, "ext_rail[axis=" + (k == -1 || k == s + 1 ? "x" : "z") + "]");
                }
            }
        }
        // the cab: windows all round from waist height, the roof over
        int y0 = TDECK + 1, top = TDECK + 5;
        for (int i = 0; i <= s; i++) {
            for (int k = 0; k <= s; k++) {
                boolean wall = i == 0 || k == 0 || i == s || k == s;
                boolean post = (i == 0 || i == s) && (k == 0 || k == s);
                String out = k == 0 ? "north" : k == s ? "south" : i == 0 ? "west" : "east";
                for (int y = y0; y < top; y++) {
                    if (!wall) {
                        set(x + i, y, z + k, "air");
                    } else if (post || y == y0) {
                        set(x + i, y, z + k, "ml_precast");
                    } else {
                        set(x + i, y, z + k, (chance() < 0.25 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]");
                    }
                }
                set(x + i, top, z + k, "un_roof");
            }
        }
        b.flat(x, z, x + s, z + s, top, "ml_precast", 0, "ml_precast");
        set(x + 3, y0, z + s, "air");
        set(x + 3, y0 + 1, z + s, "air");
        door(x + 3, TDECK, z + s, Doors.FLAT, "south");
        set(x + 3, TDECK, z + 3, "cp_tw_deck");
        set(x + 3, y0, z + 3, "air");
        // the hatch the ladder comes up through, beside the consoles
        set(x + 3, TDECK, z + 4, "air");
        set(x + 3, y0 - 1, z + 4, "ladder[facing=north]");
        for (int i = 1; i < s; i++) {
            set(x + i, y0, z + 1, any("bunk_radio_desk[facing=north]", "home_desk_pc[facing=north]"));
        }
        set(x + 2, y0, z + 2, "office_chair[facing=north]");
        set(x + 4, y0, z + 2, "office_chair[facing=north]");
        set(x + 1, y0, z + 5, "bunk_map_table");
        set(x + 3, top + 1, z + 3, chance() < 0.3 ? "tv_beacon_lit" : "tv_beacon");
        set(x + 1, top + 1, z + 1, "cp_mast_top[facing=north]");
        set(x + 3, top - 1, z, Camp.lamp(rng, 0.4, "cp_flood_hang", "north"));
    }

    /** The ops building by the tower: the briefing room with its map and rows of chairs, the crew room, the duty desk. */
    private void ops() {
        for (int x = OX0; x <= OX1; x++) {
            for (int z = OZ0; z <= OZ1; z++) {
                boolean wall = x == OX0 || x == OX1 || z == OZ0 || z == OZ1;
                set(x, 0, z, wall ? "concrete_dark" : "un_slab_vct");
                for (int y = 1; y < 5; y++) {
                    String out = z == OZ1 ? "south" : z == OZ0 ? "north" : x == OX0 ? "west" : "east";
                    boolean win = wall && (y == 2 || y == 3) && Math.floorMod(z == OZ0 || z == OZ1 ? x : z, 4) == 2;
                    set(x, y, z, !wall ? "air" : win ? (chance() < 0.2 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]"
                            : y == 4 ? "ml_precast_band" : "ml_precast");
                }
                set(x, 5, z, "un_roof");
            }
        }
        b.flat(OX0, OZ0, OX1, OZ1, 5, "ml_precast", 1, "ml_precast");
        partZ(OX0 + 20, OZ0 + 1, OZ1 - 1, 0, 5, "drywall", "drywall");
        set(OX0 + 20, 1, OZ1 - 4, "air");
        set(OX0 + 20, 2, OZ1 - 4, "air");
        door(OX0 + 20, 0, OZ1 - 4, Doors.FLAT, "east");
        for (int x = OX0 + 25; x <= OX0 + 26; x++) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, OZ1, "air");
            }
        }
        doors(OX0 + 25, 0, OZ1, Doors.SHOP, "south");
        set(OX0 + 9, 2, OZ0 + 1, "bunk_map_wall[facing=south]");
        set(OX0 + 11, 2, OZ0 + 1, "of_whiteboard[facing=south]");
        set(OX0 + 6, 1, OZ0 + 2, "bunk_map_table");
        for (int z = OZ0 + 5; z <= OZ1 - 3; z += 2) {
            for (int x = OX0 + 3; x <= OX0 + 17; x++) {
                if (x != OX0 + 10 && chance() < 0.85) {
                    set(x, 1, z, chance() < 0.12 ? "trade_folding_chair_tipped[facing=" + dir() + "]" : "trade_folding_chair[facing=north]");
                }
            }
        }
        for (int x = OX0 + 22; x <= OX1 - 2; x++) {
            set(x, 1, OZ0 + 1, "fs_locker[facing=south]");
            set(x, 2, OZ0 + 1, "fs_locker_top[facing=south]");
        }
        set(OX1 - 2, 1, OZ1 - 3, "home_desk_pc[facing=west]");
        set(OX1 - 3, 1, OZ1 - 3, "office_chair[facing=west]");
        set(OX1 - 1, 1, OZ1 - 7, "bunk_radio_desk[facing=west]");
        set(OX0 + 22, 1, OZ1 - 2, "fs_scba_rack[facing=east]");
        lamps(OX0, OZ0, OX1, OZ1, 5, 0.3);
        mess(OX0 + 1, OZ0 + 1, OX1 - 1, OZ1 - 1, 0, 0.04);
        set(OX0 + 23, 1, OZ1 + 3, "cv_flagpole_base");
        for (int y = 2; y <= 10; y++) {
            set(OX0 + 23, y, OZ1 + 3, "cv_flagpole");
        }
        set(OX0 + 23, 11, OZ1 + 3, "cv_flagpole_top[facing=south]");
    }

    // ------------------------------------------------------------------ the rest

    /** The fuel bladders, the vehicle sheds, the Hesco wall, the masts and the watchtowers. */
    private void support() {
        camp.fuelTanks(-500, -290, 4);
        camp.shed(-470, -250, 30);
        p.vehicle(-430.5, 1, -232.5, 180f, "fire_truck", rng.nextLong());
        camp.shed(-60, -262, 24);
        for (int i = 0; i < 6; i++) {
            p.vehicle(-55.5 + i * 7, 1, -238.5, 180f, any("jeep", "jeep_open", "box_truck"), rng.nextLong());
        }
        camp.mast(-20, -300, 18);
        camp.hesco(BX0 + 40, BZ0 + 4, BX1 - 40, BZ0 + 5, 3, 0.04);
        camp.watchtower(BX0 + 40, BZ0 + 8);
        camp.watchtower(BX1 - 44, BZ0 + 8);
        for (int x = ArmyField.RAMP_X0 + 30; x <= ArmyField.RAMP_X1 - 30; x += 90) {
            camp.floodMast(x, ArmyField.RAMP_Z0 + 2, 9);
        }
        for (int i = 0; i < 5; i++) {
            camp.lightTower(200 + i * 30, -290, chance() < 0.6);
        }
        camp.crates(-200, -300, 10, 8);
    }
}
