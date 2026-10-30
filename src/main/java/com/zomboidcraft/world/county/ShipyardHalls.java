package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.Map;

/**
 * The shipyard's shops ({@link Shipyard}): the panel line and the block assembly hall under their
 * vaulted roofs with their travelling cranes, the pipe shop; the blocks on their stools in the
 * staging yard between the halls and the berths, the transporter with one up on it, the crawler
 * crane; the plate yard and its magnet gantry, the blast and prime shop, the profile racks; the
 * outfit store, the keel blocks and the cabins out west.
 */
final class ShipyardHalls {
    /** The panel line, the block assembly hall: their sides (x), ends (z), eaves and the vault's rise. */
    static final int AX0 = 104, AX1 = 153, BX0 = 166, BX1 = 233, HZ0 = 30, HZ1 = 229;
    /** The plate yard's gantry rails, the yard's ends. */
    static final int PX0 = 104, PX1 = 176, PZ0 = 272, PZ1 = 344;

    private final Shipyard.Yard y;
    private final Placer p;

    ShipyardHalls(Shipyard.Yard y) {
        this.y = y;
        this.p = y.p;
    }

    private void set(int x, int yy, int z, String b) {
        y.set(x, yy, z, b);
    }

    private double chance() {
        return y.chance();
    }

    void build() {
        hall(AX0, AX1, 18, 6, "a", 8, false);
        panelLine();
        hall(BX0, BX1, 24, 8, "b", 10, true);
        assembly();
        pipeShop();
        staging();
        plateYard();
        blastShop();
        westYard();
    }

    // ------------------------------------------------------------------ the halls

    /**
     * A fabrication hall from x0 to x1, the length of the shops (z HZ0 to HZ1): corrugated walls
     * over a concrete dado to the eaves at `eave`, a band of translucent sheeting under them, the
     * primed steel columns inside every ten blocks carrying the crane rails, the roof a shallow
     * vault rising `rise` (the airfield hangars' panels), the big sliding doors in the north end,
     * (drawn back if open) the name over them, the travelling crane on its rails, the lamps hung down the middle.
     */
    private void hall(int x0, int x1, int eave, int rise, String name, int cols, boolean open) {
        int craneY = eave - 3;
        for (int x = x0; x <= x1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                boolean wall = x == x0 || x == x1 || z == HZ0 || z == HZ1;
                boolean walk = !wall && (x == x0 + 3 || x == x1 - 3) && z % 2 == 0;
                set(x, 0, z, wall ? "concrete_dark" : walk ? "af_paint_y" : chance() < 0.03 ? "navy_hangar_oil" : "navy_hangar_floor");
                for (int yy = 1; yy <= eave; yy++) {
                    set(x, yy, z, wall ? clad(yy, eave, x == x0 ? "west" : x == x1 ? "east" : z == HZ0 ? "north" : "south") : "air");
                }
            }
        }
        // the columns and the crane rails along both sides
        for (int z = HZ0 + 1; z < HZ1; z++) {
            boolean col = (z - HZ0) % 10 == 0;
            for (int yy = 1; yy <= eave; yy++) {
                if (col) {
                    set(x0 + 1, yy, z, yy == 1 ? "sf_col_base" : "sf_col");
                    set(x1 - 1, yy, z, yy == 1 ? "sf_col_base" : "sf_col");
                }
            }
            set(x0 + 1, craneY - 1, z, "pw_crane_rail[facing=east]");
            set(x1 - 1, craneY - 1, z, "pw_crane_rail[facing=west]");
        }
        // the vault
        double half = (x1 - x0 + 1) / 2.0, cx = x0 + half;
        double r = (half * half + rise * rise) / (2.0 * rise), cy = eave + 1 + rise - r;
        Map<Long, double[]> cells = ArmyFieldBase.arc(cx, cy, r, x0, x1 + 1);
        for (double[] c : cells.values()) {
            for (int z = HZ0; z <= HZ1; z++) {
                boolean end = z == HZ0 || z == HZ1;
                if (!end && chance() < 0.012) {
                    continue;  // a sheet gone, the rain coming in
                }
                set((int) c[0], (int) c[1], z, "mf_vault_" + (end ? "end_" : "") + (int) c[2] + "_" + (int) c[3]);
            }
        }
        // the exhausts down the crown that take the welding fume out, two to a bay
        int crownX = (int) cx, crownY = 0;
        for (double[] c : cells.values()) {
            if ((int) c[0] == crownX) {
                crownY = Math.max(crownY, (int) c[1]);
            }
        }
        for (int z = HZ0 + 5; z < HZ1 - 3; z += 10) {
            set(crownX - 1, crownY + 1, z, "bd_vent");
            set(crownX + 1, crownY + 1, z, chance() < 0.15 ? "bunk_vent_cap" : "bd_vent");
        }
        // the gables under the vault, the doors in the north one
        int doorTop = eave - 4, dx0 = x0 + 6, dx1 = x1 - 6;
        for (int x = x0; x <= x1; x++) {
            double top = cy + Math.sqrt(Math.max(0, r * r - (x + 0.5 - cx) * (x + 0.5 - cx)));
            for (int yy = 1; yy < top - 0.6; yy++) {
                if (cells.containsKey(key(x, yy))) {
                    continue;
                }
                boolean door = x >= dx0 && x <= dx1 && yy <= doorTop;
                set(x, yy, HZ0, door ? "air" : yy == doorTop + 1 && x >= dx0 - 1 && x <= dx1 + 1 ? "ml_precast_band" : (x == x0 || x == x1) && yy > 3 ? "corrugated_grey" : clad(yy, eave, "north"));
                if (yy > eave) {
                    set(x, yy, HZ1, clad(yy, eave, "south"));
                }
            }
        }
        // the leaves on their two tracks: the west half drawn across, the east half run back over the wall
        int mid = (dx0 + dx1) / 2;
        for (int x = dx0 - 2; x <= x1 + 2; x++) {
            set(x, 0, HZ0 - 1, "ah_track[axis=x]");
            set(x, 0, HZ0 - 2, "ah_track[axis=x]");
        }
        if (open) {
            for (int x = dx0 - 8; x < dx0; x++) {
                leaf(x, HZ0 - 1, doorTop, x == dx0 - 1);
            }
        } else {
            for (int x = dx0; x <= mid; x++) {
                leaf(x, HZ0 - 1 - (x - dx0) / ((mid - dx0 + 2) / 2) % 2, doorTop, x == dx0 || x == mid);
            }
        }
        int run = dx1 - mid;
        for (int x = dx1 + 1; x <= dx1 + run && x <= x1 + 2; x++) {
            leaf(x, HZ0 - 2, doorTop, x == dx1 + 1);
        }
        y.picture("sy_hall_" + name + "_", cols, 2, (x0 + x1) / 2 - cols / 2, doorTop + 3, HZ0 - 1, "north");
        // the south end: a roller door to the yard behind, a man door
        for (int x = (x0 + x1) / 2 - 4; x <= (x0 + x1) / 2 + 3; x++) {
            for (int yy = 1; yy <= 6; yy++) {
                set(x, yy, HZ1, yy == 6 ? "pt_shutter_steel" : chance() < 0.5 ? "pt_shutter_steel" : "air");
            }
        }
        y.door(x1, 0, HZ1 - 6, com.zomboidcraft.world.city.Doors.FLAT, "east");
        // the travelling crane: its two girders across the span, the end carriages, the trolley, the hook hung down
        int cz = HZ0 + 30 + y.rng.nextInt(HZ1 - HZ0 - 70);
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            set(x, craneY, cz, "pw_crane[axis=x]");
            set(x, craneY, cz + 2, "pw_crane[axis=x]");
        }
        for (int z = cz; z <= cz + 2; z++) {
            set(x0 + 1, craneY, z, "pw_crane_end[facing=west]");
            set(x1 - 1, craneY, z, "pw_crane_end[facing=east]");
        }
        int tx = x0 + 6 + y.rng.nextInt(x1 - x0 - 12);
        set(tx, craneY, cz + 1, "pw_trolley[facing=north]");
        int hookY = 3 + y.rng.nextInt(Math.max(1, craneY - 8));
        for (int yy = hookY + 1; yy < craneY; yy++) {
            set(tx, yy, cz + 1, "vessel_chain_v");
        }
        set(tx, hookY, cz + 1, "pw_hook");
        // the floods hung down the middle, the windows' band lit grey
        for (int z = HZ0 + 6; z < HZ1; z += 12) {
            set((int) cx, (int) (cy + r) - 2, z, Camp.lamp(y.rng, 0.6, "cp_flood_hang", "south"));
        }
        y.mess(x0 + 2, HZ0 + 2, x1 - 2, HZ1 - 2, 0, 0.012);
    }

    /** A hall's wall at height yy: the dado, the corrugated sheeting, the band of translucent sheets under the eaves. */
    private String clad(int yy, int eave, String out) {
        if (yy <= 3) {
            return chance() < 0.1 ? "concrete_panel_stain" : "concrete_panel_b";
        }
        if (yy >= eave - 3 && yy <= eave - 2 && yy <= eave) {
            return (chance() < 0.12 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]";
        }
        double r = chance();
        return r < 0.06 ? "corrugated_grey_stain" : r < 0.3 ? "corrugated_grey_b" : r < 0.33 ? "corrugated_rust" : "corrugated_grey";
    }

    /** A column of a door leaf on its track at z, the band at its foot, its edge stile where edge. */
    private void leaf(int x, int z, int top, boolean edge) {
        for (int yy = 1; yy <= top; yy++) {
            set(x, yy, z, (yy == 1 ? "ah_leaf_band" : edge ? "ah_leaf_edge" : "ah_leaf") + "[facing=north]");
        }
        set(x, top + 1, z, "corrugated_blue_b");
    }

    private static long key(int x, int yy) {
        return ((long) x << 32) ^ (yy & 0xffffffffL);
    }

    /**
     * The panel line: the plasma tables at the south end with the plates waiting by them, the line
     * of welding gantries down the middle with a panel at each station, the finished panels stacked
     * at the north end for the crane; the welding sets and the bottle cages along the walls.
     */
    private void panelLine() {
        // the plasma tables and their gantries
        for (int t = 0; t < 2; t++) {
            int x = AX0 + 8 + t * 14;
            for (int z = HZ1 - 50; z <= HZ1 - 34; z++) {
                for (int dx = 0; dx < 5; dx++) {
                    set(x + dx, 1, z, "sy_cnc[axis=z]");
                }
            }
            int gz = HZ1 - 48 + y.rng.nextInt(12);
            for (int dx = -1; dx <= 5; dx++) {
                set(x + dx, 2, gz, "sy_cnc_beam[axis=x]");
            }
            set(x - 1, 1, gz, "steel_yellow");
            set(x + 5, 1, gz, "steel_yellow");
        }
        for (int z = HZ1 - 26; z < HZ1 - 6; z += 6) {
            for (int x = AX0 + 30; x <= AX0 + 42; x += 5) {
                stack(x, z, 4, 4, 1 + y.rng.nextInt(3));
            }
        }
        // the line: four stations, a gantry over each with its panel under it
        int lx0 = AX0 + 13, lx1 = AX1 - 13;
        for (int k = 0; k < 4; k++) {
            int z0 = HZ0 + 46 + k * 26;
            for (int x = lx0; x <= lx1; x++) {
                for (int z = z0; z < z0 + 14; z++) {
                    if (chance() < 0.92) {
                        set(x, 1, z, "sy_panel[axis=z]");
                    }
                }
            }
            int gz = z0 + 3 + y.rng.nextInt(8);
            for (int yy = 1; yy <= 6; yy++) {
                set(lx0 - 2, yy, gz, "steel_yellow");
                set(lx1 + 2, yy, gz, "steel_yellow");
            }
            for (int x = lx0 - 2; x <= lx1 + 2; x++) {
                set(x, 7, gz, "sy_cnc_beam[axis=x]");
            }
            for (int x = lx0; x <= lx1; x++) {
                set(x, 0, z0 - 1, "ah_track[axis=x]");
            }
        }
        // the finished panels stacked for the crane, one hung on its hook
        for (int z = HZ0 + 6; z < HZ0 + 36; z += 10) {
            for (int x = AX0 + 6; x < AX1 - 8; x += 14) {
                for (int yy = 1; yy <= 1 + y.rng.nextInt(4); yy++) {
                    for (int dx = 0; dx < 10; dx++) {
                        for (int dz = 0; dz < 7; dz++) {
                            set(x + dx, yy, z + dz, "sy_panel[axis=z]");
                        }
                    }
                }
            }
        }
        walls(AX0, AX1);
    }

    /** The welding sets, bottle cages, tool chests and hose coils along a hall's walls. */
    private void walls(int x0, int x1) {
        for (int z = HZ0 + 4; z < HZ1 - 4; z += 3) {
            for (int x : new int[] {x0 + 2, x1 - 2}) {
                if (!y.empty(x, 1, z)) {
                    continue;
                }
                String f = x == x0 + 2 ? "east" : "west";
                double r = chance();
                set(x, 1, z, r < 0.3 ? "pw_welder[facing=" + f + "]" : r < 0.45 ? "sy_cyls[facing=" + f + "]" : r < 0.55 ? "pk_tool_chest[facing=" + f + "]"
                        : r < 0.7 ? "sy_hose" : r < 0.75 ? "pw_drum" : "air");
            }
        }
    }

    /** A stack of plates from (x, z), w across and d along, n high, the top one a thin stack. */
    private void stack(int x, int z, int w, int d, int n) {
        for (int dx = 0; dx < w; dx++) {
            for (int dz = 0; dz < d; dz++) {
                for (int yy = 1; yy <= n; yy++) {
                    set(x + dx, yy, z + dz, yy == n ? "sy_plates_low[axis=z]" : "sy_plates[axis=z]");
                }
            }
        }
    }

    /**
     * The block assembly hall: three blocks building on their stools with the staging round them,
     * the pin jig with a curved plate on it, a block on the transporter halfway out of the door, the
     * glass office up on its legs at the south end.
     */
    private void assembly() {
        shipBlock(BX0 + 8, HZ0 + 60, 22, 18, 9, false, 0);
        shipBlock(BX0 + 36, HZ0 + 60, 20, 18, 8, true, 1);
        shipBlock(BX0 + 10, HZ0 + 100, 24, 20, 10, true, 2);
        // the pin jig and the curved shell plate on it
        for (int x = BX0 + 40; x < BX0 + 56; x++) {
            for (int z = HZ0 + 102; z < HZ0 + 118; z++) {
                set(x, 1, z, "sy_jig");
                int h = (int) Math.round(Math.pow((x - (BX0 + 48)) / 4.0, 2));
                if ((x + z) % 3 != 0) {
                    set(x, 2 + Math.min(2, h), z, "sy_primer");
                }
            }
        }
        // the block rolling out of the north door on its transporter, left where it stopped
        int bx = (BX0 + BX1) / 2 - 8;
        for (int x = bx; x < bx + 16; x++) {
            for (int z = HZ0 - 10; z < HZ0 + 10; z++) {
                set(x, 1, z, "sy_spmt[facing=north]");
            }
        }
        blockShell(bx, 2, HZ0 - 10, 16, 20, 8, false, 3);
        // the glass office on its legs at the south end, the stair up to it
        int ox0 = BX1 - 16, oz0 = HZ1 - 16, oy = 6;
        for (int x = ox0; x <= BX1 - 2; x++) {
            for (int z = oz0; z <= HZ1 - 2; z++) {
                set(x, oy, z, "un_slab_vct");
                boolean wall = x == ox0 || z == oz0;
                for (int yy = oy + 1; yy <= oy + 3; yy++) {
                    set(x, yy, z, wall ? (yy == oy + 3 ? "steel_mullion" : chance() < 0.25 ? "wf_glass_broken[facing=north]" : "wf_glass[facing=north]") : "air");
                }
                set(x, oy + 4, z, "un_roof");
                if ((x - ox0) % 6 == 0 && (z - oz0) % 6 == 0) {
                    for (int yy = 1; yy < oy; yy++) {
                        set(x, yy, z, "sf_col");
                    }
                }
            }
        }
        for (int i = 0; i < oy; i++) {
            set(ox0 - 1 - i, 1 + i, oz0 + 2, "steel_stairs[facing=east]");
        }
        set(ox0 + 4, oy + 1, oz0 + 4, "home_desk[facing=north]");
        set(ox0 + 5, oy + 1, oz0 + 4, "paper");
        set(ox0 + 8, oy + 1, oz0 + 8, "filing_cabinet[facing=north]");
        walls(BX0, BX1);
    }

    /** The pipe shop: a flat-roofed shed east of the halls, the racks of pipe, the bending bench, the store. */
    private void pipeShop() {
        int x0 = 250, x1 = 292, z0 = 40, z1 = 150;
        Bldg ps = new Bldg(p, y.rng);
        ps.damage = 0.45;
        ps.shell(x0, z0, x1, z1, 1, 10, "corrugated_blue", "floor_concrete", "floor_concrete");
        ps.flat(x0, z0, x1, z1, 10, "corrugated_blue", 0, null);
        for (int x = x0 + 8; x < x1 - 6; x++) {
            for (int yy = 1; yy <= 6; yy++) {
                set(x, yy, z0, yy == 6 ? "pt_shutter_steel" : chance() < 0.4 ? "air" : "pt_shutter_steel");
            }
        }
        y.sign("pipe", x0 + 18, 8, z0 - 1, "north");
        for (int z = z0 + 6; z < z1 - 6; z += 8) {
            for (int x = x0 + 3; x < x1 - 3; x += 9) {
                for (int yy = 1; yy <= 4; yy++) {
                    set(x, yy, z, "steel_yellow");
                    set(x, yy, z + 4, "steel_yellow");
                }
                for (int yy = 2; yy <= 4; yy += 2) {
                    for (int zz = z; zz <= z + 4; zz++) {
                        set(x + 1, yy, zz, chance() < 0.75 ? "dm_penstock[axis=z]" : "air");
                        set(x + 2, yy, zz, chance() < 0.6 ? "dm_penstock[axis=z]" : "air");
                    }
                }
            }
        }
        y.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.02);
        ps.lights(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 9, 0);
    }

    // ------------------------------------------------------------------ the blocks

    /**
     * A ship's block on its stools at (x0, z0), bw across and bd along, bh high: up on the stools
     * two blocks off the ground ({@link #blockShell}), the staging round it if it's still being
     * built.
     */
    private void shipBlock(int x0, int z0, int bw, int bd, int bh, boolean staged, int number) {
        for (int x = x0 + 1; x < x0 + bw - 1; x += 4) {
            for (int z = z0 + 1; z < z0 + bd - 1; z += 4) {
                set(x, 1, z, "sy_keel[axis=z]");
                set(x, 2, z, "sy_keel_cap[axis=z]");
            }
        }
        blockShell(x0, 3, z0, bw, bd, bh, !staged, number);
        if (staged) {
            for (int x = x0 - 2; x <= x0 + bw + 1; x++) {
                for (int z = z0 - 2; z <= z0 + bd + 1; z++) {
                    boolean ring = x == x0 - 2 || x == x0 + bw + 1 || z == z0 - 2 || z == z0 + bd + 1;
                    if (!ring) {
                        continue;
                    }
                    String f = z == z0 - 2 ? "north" : z == z0 + bd + 1 ? "south" : x == x0 - 2 ? "west" : "east";
                    boolean pole = (x + z) % 3 == 0;
                    for (int yy = 1; yy <= bh + 4; yy++) {
                        String piece = yy % 4 == 0 ? (pole ? "sf_deck_std" : "sf_deck") : pole ? "sf_std" : "sf_bay";
                        set(x, yy, z, piece + "[facing=" + f + "]");
                    }
                }
            }
        }
    }

    /**
     * A block's steel from y0: the bottom shell and its stiffened plating over it, the side shells
     * round it with the number stencilled on two of them, the web frames across it every four, and
     * the deck over it if it's closed (a manhole left in it) or its webs open to the sky if not.
     */
    void blockShell(int x0, int y0, int z0, int bw, int bd, int bh, boolean closed, int number) {
        boolean zinc = number % 2 == 1;
        String plate = zinc ? "sy_zinc" : "sy_primer", seam = zinc ? "sy_zinc_seam" : "sy_primer_seam";
        // the butts it'll be joined at stay open to show the webs: the north always, the south on most
        boolean openN = closed, openS = closed && number % 3 != 0;
        for (int x = x0; x < x0 + bw; x++) {
            for (int z = z0; z < z0 + bd; z++) {
                boolean shell = x == x0 || x == x0 + bw - 1;
                boolean butt = !shell && (z == z0 && openN || z == z0 + bd - 1 && openS);
                boolean side = shell || z == z0 || z == z0 + bd - 1;
                set(x, y0, z, plate);
                for (int yy = y0 + 1; yy <= y0 + bh; yy++) {
                    if (butt && yy < y0 + bh) {
                        set(x, yy, z, yy == y0 + 1 ? "sy_stiff[axis=z]" : "sy_web[axis=x]");
                    } else if (side) {
                        set(x, yy, z, (yy - y0) % 3 == 0 ? seam : chance() < 0.04 ? "sy_primer_chalk" : plate);
                    } else if (yy == y0 + 1) {
                        set(x, yy, z, "sy_stiff[axis=z]");
                    } else if (yy == y0 + bh && closed) {
                        set(x, yy, z, seam);
                    } else if ((z - z0) % 4 == 0) {
                        set(x, yy, z, "sy_web[axis=x]");
                    } else {
                        set(x, yy, z, "air");
                    }
                }
            }
        }
        int k = number % 8;
        if (!openN) {
            set(x0 + bw / 2, y0 + bh - 2, z0 - 1, "sy_blk_" + k + "[facing=north]");
        }
        set(x0 - 1, y0 + bh - 2, z0 + bd / 2, "sy_blk_" + k + "[facing=west]");
        set(x0 + bw, y0 + bh - 2, z0 + bd / 2, "sy_blk_" + k + "[facing=east]");
        if (closed) {
            blockDeck(x0, y0 + bh + 1, z0, bw, bd, number);
        }
    }

    /**
     * What's on a closed block's deck at y: the four lifting lugs it's flown by, the manholes with
     * their covers off, the temporary handrail round the edge with the gaps the fitters left, and
     * what the last shift left up there.
     */
    private void blockDeck(int x0, int top, int z0, int bw, int bd, int number) {
        int x1 = x0 + bw - 1, z1 = z0 + bd - 1;
        for (int[] c : new int[][] {{x0 + 2, z0 + 2}, {x1 - 2, z0 + 2}, {x0 + 2, z1 - 2}, {x1 - 2, z1 - 2}}) {
            set(c[0], top, c[1], "sy_lug[facing=" + (number % 2 == 0 ? "north" : "east") + "]");
        }
        for (int x = x0 + 3; x < x1 - 2; x += 7) {
            set(x, top, z0 + bd / 2 + (x % 2 == 0 ? -1 : 1), "sy_manhole[facing=" + y.dir() + "]");
        }
        if (number % 4 != 1) {
            for (int x = x0; x <= x1; x++) {
                boolean keep = x <= x0 + 1 || x >= x1 - 1;  // the corners stay whole, so no rail stands turned on its own
                if (keep || chance() > 0.12) {
                    set(x, top, z0, "cr_rail[facing=north]");
                }
                if (keep || chance() > 0.12) {
                    set(x, top, z1, "cr_rail[facing=south]");
                }
            }
            for (int z = z0 + 1; z < z1; z++) {
                boolean keep = z <= z0 + 1 || z >= z1 - 1;
                if (keep || chance() > 0.12) {
                    set(x0, top, z, "cr_rail[facing=west]");
                }
                if (keep || chance() > 0.12) {
                    set(x1, top, z, "cr_rail[facing=east]");
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int x = x0 + 3 + y.rng.nextInt(Math.max(1, bw - 6)), z = z0 + 3 + y.rng.nextInt(Math.max(1, bd - 6));
            if (y.empty(x, top, z)) {
                set(x, top, z, y.any("sy_hose", "sy_cyls[facing=" + y.dir() + "]", "pw_welder[facing=" + y.dir() + "]"));
            }
        }
    }

    /**
     * The staging yard between the berths and the halls: the blocks on their stools in rows, some
     * finished and some not, one up on the transporter; the crawler crane; the bottle cages, the
     * welding sets, the cabins and the toilets.
     */
    private void staging() {
        int n = 4;
        int[][] free = {{-96, 40}, {-60, 30}, {-20, 34}, {24, 30}, {58, 40}, {-96, 86}, {-50, 80}, {-6, 86}, {40, 84},
            {-90, 132}, {-46, 130}, {6, 136}, {-40, 182}, {4, 180}, {44, 186}, {-8, 226}};
        for (int[] c : free) {
            if (chance() < 0.18) {
                continue;
            }
            int bw = 12 + y.rng.nextInt(10), bd = 12 + y.rng.nextInt(8), bh = 6 + y.rng.nextInt(5);
            shipBlock(c[0], c[1], bw, bd, bh, chance() < 0.35, n++);
        }
        // the crawler crane by the jetty's root, its boom up at sixty degrees toward the berths
        crawler(70, 140);
        // the bottle cages, the welding sets, the hose coils, between the rows
        for (int i = 0; i < 40; i++) {
            int x = -96 + y.rng.nextInt(180), z = 20 + y.rng.nextInt(230);
            if (y.empty(x, 1, z) && y.empty(x + 1, 1, z) && y.get(x, 0, z) != null && y.get(x, 0, z).startsWith("floor")) {
                set(x, 1, z, y.any("sy_cyls[facing=" + y.dir() + "]", "pw_welder[facing=" + y.dir() + "]", "sy_hose", "sy_hose",
                        "pw_drum", "st_cone"));
            }
        }
        Harbour hb = new Harbour(p, y.rng, y.hit);
        hb.box(-96, 1, 244, "container_white", true);
        hb.box(-96, 4, 244, "container_blue", true);
        hb.box(-80, 1, 244, "container_white", true);
        new Camp(p, y.rng, y.hit).toilets(-62, 244, 3);
        y.sign("hats", -92, 4, 243, "north");
        y.sign("days", -76, 4, 243, "north");
    }

    /**
     * A crawler crane at (x, z): the tracks, the upper works and its counterweight, the cab, the
     * lattice boom raised north-west, its pendants to the A-frame, the hook block hanging at the tip.
     */
    private void crawler(int x, int z) {
        for (int dz = -5; dz <= 5; dz++) {
            for (int dx : new int[] {-4, -3, 3, 4}) {
                set(x + dx, 1, z + dz, "rg_track_rust[facing=" + (dx < 0 ? "west" : "east") + "]");
            }
            for (int dx = -2; dx <= 2; dx++) {
                set(x + dx, 1, z + dz, Math.abs(dz) < 3 ? "cr_plate_grey" : "air");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 5; dz++) {
                for (int yy = 2; yy <= 5; yy++) {
                    boolean shell = Math.abs(dx) == 3 || dz == -3 || dz == 5 || yy == 5;
                    set(x + dx, yy, z + dz, shell ? (dz >= 3 && yy <= 4 ? "concrete_dark" : "cr_plate_yellow") : "air");
                }
            }
        }
        for (int yy = 3; yy <= 4; yy++) {
            set(x - 3, yy, z - 2, (chance() < 0.3 ? "wf_glass_broken" : "wf_glass") + "[facing=west]");
        }
        // the boom: two lattice chords up from the foot at the front, sixty degrees toward -z
        int len = 44;
        double ang = Math.toRadians(60);
        int tipY = 5 + (int) Math.round(len * Math.sin(ang)), tipZ = z - 3 - (int) Math.round(len * Math.cos(ang));
        Harbour boom = new Harbour(p, y.rng, y.hit);
        for (int dx : new int[] {-1, 1}) {
            boom.member(x + dx, 5, z - 3, tipY, tipZ, "cr_lattice_yellow");
        }
        boom.member(x, 9, z + 2, tipY, tipZ, "cr_stay");
        for (int yy = 6; yy <= 9; yy++) {
            set(x, yy, z + 2, "steel_yellow");
        }
        set(x, tipY + 1, tipZ, "cr_headblock[axis=x]");
        int hookY = 8 + y.rng.nextInt(10);
        for (int yy = hookY + 1; yy <= tipY; yy++) {
            set(x, yy, tipZ - 1, "vessel_chain_v");
        }
        set(x, hookY, tipZ - 1, "cr_headblock[axis=x]");
        set(x, hookY - 1, tipZ - 1, "pw_hook");
    }

    // ------------------------------------------------------------------ the plate yard

    /**
     * The plate yard: the stacks in their lanes under the magnet gantry on its rails, the gantry with
     * its trolley and the magnet beam hung with a plate on it, the stacks' bearers; the profile racks.
     */
    private void plateYard() {
        for (int z = PZ0; z <= PZ1; z++) {
            set(PX0, 0, z, "ah_track[axis=z]");
            set(PX1, 0, z, "ah_track[axis=z]");
        }
        for (int x = PX0 + 5; x < PX1 - 5; x += 7) {
            for (int z = PZ0 + 4; z < PZ1 - 10; z += 13) {
                if (chance() < 0.12) {
                    continue;
                }
                for (int dz = 0; dz < 10; dz++) {
                    for (int dx = 0; dx < 4; dx++) {
                        set(x + dx, 0, z + dz, dz % 3 == 0 ? "sy_timber" : y.get(x + dx, 0, z + dz));
                    }
                }
                stack(x, z, 4, 10, 1 + y.rng.nextInt(3));
            }
        }
        // the gantry: a leg each side on its bogies, the girder across, the trolley and the magnets
        int gz = PZ0 + 10 + y.rng.nextInt(PZ1 - PZ0 - 20), top = 15;
        for (int lx : new int[] {PX0, PX1}) {
            for (int z = gz - 3; z <= gz + 4; z++) {
                set(lx, 1, z, "cr_bogie[axis=z]");
                set(lx, 2, z, "cr_plate_yellow");
            }
            for (int yy = 3; yy < top; yy++) {
                for (int z = gz; z <= gz + 1; z++) {
                    set(lx, yy, z, yy % 5 == 0 ? "cr_plate_yellow_seam" : "cr_plate_yellow");
                }
            }
            set(lx, 8, gz - 1, "cr_cage_ladder[facing=north]");
        }
        for (int x = PX0 - 2; x <= PX1 + 2; x++) {
            set(x, top, gz, "cr_plate_yellow");
            set(x, top, gz + 1, "cr_plate_yellow");
            set(x, top + 1, gz, "cr_rail[facing=north]");
        }
        y.row("sy_sgn_load_", 2, (PX0 + PX1) / 2, top - 1, gz - 1, "north", 0);
        int tx = PX0 + 12 + y.rng.nextInt(PX1 - PX0 - 24);
        for (int dx = -1; dx <= 1; dx++) {
            set(tx + dx, top - 1, gz, "cr_plate_grey");
            set(tx + dx, top - 1, gz + 1, "cr_plate_grey");
        }
        int my = 6;
        for (int yy = my + 1; yy < top - 1; yy++) {
            set(tx, yy, gz, "vessel_chain_v");
        }
        for (int dx = -4; dx <= 4; dx++) {
            set(tx + dx, my, gz, "sy_magnet_beam[axis=x]");
            set(tx + dx, my - 1, gz, dx % 2 == 0 ? "sy_magnet" : "air");
            set(tx + dx, my - 2, gz, "sy_plates_low[axis=x]");
        }
        y.sign("plate", PX0 - 1, 3, PZ0 - 1, "north");
    }

    /** The blast and prime shop: a long shed the plates run through on the rollers, its booths, its stack. */
    private void blastShop() {
        int x0 = 190, x1 = 290, z0 = 274, z1 = 306;
        Bldg bs = new Bldg(p, y.rng);
        bs.damage = 0.5;
        bs.shell(x0, z0, x1, z1, 1, 11, "corrugated_grey", "floor_concrete", "floor_concrete");
        bs.flat(x0, z0, x1, z1, 11, "corrugated_grey", 0, null);
        int rz = (z0 + z1) / 2;
        for (int x = x0 - 12; x <= x1 + 6; x++) {
            for (int z = rz - 2; z <= rz + 2; z++) {
                set(x, 1, z, "navy_roller[axis=x]");
            }
            if (x == x0 || x == x1) {
                for (int z = rz - 2; z <= rz + 2; z++) {
                    for (int yy = 2; yy <= 3; yy++) {
                        set(x, yy, z, "air");
                    }
                }
            }
            if (x % 9 == 0 && x < x0) {
                for (int z = rz - 2; z <= rz + 2; z++) {
                    set(x, 2, z, "sy_plates_low[axis=x]");
                }
            }
        }
        // the blast cabinet and the paint booth over the rollers, the shot hopper's stack through the roof
        for (int[] booth : new int[][] {{x0 + 20, x0 + 36}, {x0 + 56, x0 + 78}}) {
            for (int x = booth[0]; x <= booth[1]; x++) {
                for (int z = rz - 5; z <= rz + 5; z++) {
                    for (int yy = 1; yy <= 8; yy++) {
                        boolean shell = x == booth[0] || x == booth[1] || Math.abs(z - rz) == 5 || yy == 8;
                        boolean tunnel = Math.abs(z - rz) <= 2 && yy <= 3 && (x == booth[0] || x == booth[1]);
                        if (shell && !tunnel) {
                            set(x, yy, z, yy == 8 ? "cr_plate_grey" : "pw_tg_plate");
                        }
                    }
                }
            }
        }
        for (int yy = 9; yy <= 20; yy++) {
            Round.wall(p, x0 + 28.5, rz + 0.5, 1.4, yy, yy, "galv", 0);
        }
        y.sign("blast", x0 + 10, 7, z0 - 1, "north");
        // the profile racks south of it
        for (int x = x0 + 2; x < x1 - 2; x += 6) {
            for (int z = z1 + 8; z <= PZ1; z++) {
                boolean upright = (z - z1 - 8) % 6 == 0;
                for (int yy = 1; yy <= 5; yy++) {
                    if (upright) {
                        set(x, yy, z, "steel_yellow");
                    } else if (yy % 2 == 1) {
                        set(x + 1, yy, z, chance() < 0.8 ? "sy_profiles[axis=z]" : "air");
                        set(x + 2, yy, z, chance() < 0.7 ? "sy_profiles[axis=z]" : "air");
                    }
                }
            }
        }
    }

    /** West of the spine: the outfit store, the keel blocks and shores piled up, the scrap, the cabins. */
    private void westYard() {
        Harbour store = new Harbour(y.frame(-96, 280, 80, 44, 0), y.rng, y.hit);
        store.warehouse(80, 44);
        y.sign("hats", -60, 6, 279, "north");
        // the keel blocks and the shores piled by the dock's head
        for (int x = -290; x < -230; x += 4) {
            for (int z = 296; z < 340; z += 6) {
                int n = 1 + y.rng.nextInt(3);
                for (int yy = 1; yy <= n; yy++) {
                    set(x, yy, z, "sy_keel[axis=" + (yy % 2 == 0 ? "x" : "z") + "]");
                    set(x + 1, yy, z, "sy_keel[axis=" + (yy % 2 == 0 ? "x" : "z") + "]");
                }
            }
        }
        for (int z = 296; z < 340; z += 3) {
            for (int x = -222; x < -200; x++) {
                set(x, 1, z, "sy_shore[axis=x]");
                if (z % 2 == 0) {
                    set(x, 2, z, "sy_shore[axis=x]");
                }
            }
        }
        // the scrap bins, the cabins in a row
        Harbour hb = new Harbour(p, y.rng, y.hit);
        for (int i = 0; i < 4; i++) {
            hb.box(-190 + i * 15, 1, 300, i % 2 == 0 ? "container_white" : "container_blue", true);
        }
        for (int x = -190; x < -120; x += 9) {
            for (int dx = 0; dx < 6; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    set(x + dx, 1, 320 + dz, dx == 0 || dx == 5 || dz == 0 || dz == 3 ? "cr_plate_red" : "rubble_pile");
                }
            }
        }
    }
}
