package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Broadcast.DX0;
import static com.zomboidcraft.world.county.Broadcast.DX1;
import static com.zomboidcraft.world.county.Broadcast.DZ0;
import static com.zomboidcraft.world.county.Broadcast.DZ1;
import static com.zomboidcraft.world.county.Broadcast.KX0;
import static com.zomboidcraft.world.county.Broadcast.KX1;
import static com.zomboidcraft.world.county.Broadcast.KZ0;
import static com.zomboidcraft.world.county.Broadcast.KZ1;
import static com.zomboidcraft.world.county.Broadcast.TCX;
import static com.zomboidcraft.world.county.Broadcast.TCZ;
import static com.zomboidcraft.world.county.Broadcast.XX0;
import static com.zomboidcraft.world.county.Broadcast.XX1;
import static com.zomboidcraft.world.county.Broadcast.XZ0;
import static com.zomboidcraft.world.county.Broadcast.XZ1;

/**
 * Channel 6's tower and everything round its foot: the self-supporting lattice, square, its legs
 * stepping in a block at every panel from twelve out at the footings to three at the top, X-braced
 * on every face and painted in the seven aviation bands; the caged ladder up the spine with the
 * lines beside it, the platforms with their rails and lights, the microwave drums and the dishes on
 * them; the antenna mast on top with the FM bays round it and the beacon. The fenced compound at its
 * foot, the ice bridge over to the transmitter building (the transmitters along its back wall, the
 * racks, the generators next door), and the farm of satellite dishes east of it in its own fence.
 */
final class BroadcastTower {
    /** The lattice's first panel (its girts clear of a walker's head), the top platform, the mast over it. */
    static final int BASE = 3, LT = 200, MAST = 30, BANDS = 7;
    /** The bridge's height, its deck one under. */
    static final int ICE_Y = 4;

    private final Broadcast c;
    private final String lit;

    BroadcastTower(Broadcast c) {
        this.c = c;
        this.lit = c.power ? "_lit" : "";
    }

    void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    boolean empty(int x, int y, int z) {
        return c.empty(x, y, z);
    }

    void build() {
        tower();
        compound();
        transmitter();
        bridge();
        dishes();
    }

    // ------------------------------------------------------------------ the tower

    /** The paint at a height: seven bands, red at the foot and the top. */
    static String paint(int y) {
        int band = (y - BASE) * BANDS / (LT - BASE + 1);
        return band % 2 == 0 ? "r" : "w";
    }

    private void tower() {
        int hw = 12;
        // the footings
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int x = TCX + sx * hw, z = TCZ + sz * hw;
                c.fill(x - 1, 0, z - 1, x + 1, 0, z + 1, "floor_concrete");
                set(x, 1, z, "tv_pier");
                set(x, 2, z, "tv_leg_r");
            }
        }
        int y0 = BASE, seg = 0;
        while (y0 < LT) {
            int h = Math.min(2 * hw, LT - y0);
            boolean plat = seg == 2 || seg == 5 || seg == 8 || seg == 12;
            panel(y0, hw, h, plat);
            if (plat) {
                platform(y0, hw);
            }
            y0 += h;
            seg++;
            if (hw > 3) {
                // the legs step in a block; the old leg carried up to meet the new
                for (int sx = -1; sx <= 1; sx += 2) {
                    for (int sz = -1; sz <= 1; sz += 2) {
                        set(TCX + sx * hw, y0, TCZ + sz * hw, "tv_leg_" + paint(y0));
                    }
                }
                hw--;
            }
        }
        platform(LT, hw);
        // the spine and the ladder up it, from the ground to the top platform
        for (int y = 1; y <= LT; y++) {
            set(TCX, y, TCZ, "tv_spine");
            set(TCX, y, TCZ + 1, "tv_ladder[facing=south]");
        }
        // the mast, its FM bays, the beacon
        for (int y = LT + 1; y <= LT + MAST; y++) {
            set(TCX, y, TCZ, "tv_ant");
            if (y >= LT + 4 && y <= LT + MAST - 4 && (y - LT) % 4 == 0) {
                set(TCX, y, TCZ - 1, "tv_ant_bay[facing=north]");
                set(TCX + 1, y, TCZ, "tv_ant_bay[facing=east]");
                set(TCX, y, TCZ + 1, "tv_ant_bay[facing=south]");
                set(TCX - 1, y, TCZ, "tv_ant_bay[facing=west]");
            }
        }
        set(TCX, LT + MAST + 1, TCZ, "tv_beacon_top" + lit);
        set(TCX, LT + MAST / 2, TCZ - 1, "tv_beacon" + lit);
    }

    /**
     * One panel of the lattice from y0, h high, the legs hw out from the centre: the legs at the
     * corners, a girt along every face at its foot (grating there if it's a platform's), the X of
     * braces across every face.
     */
    private void panel(int y0, int hw, int h, boolean plat) {
        for (int t = 0; t < h; t++) {
            int y = y0 + t;
            String col = paint(y);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    set(TCX + sx * hw, y, TCZ + sz * hw, "tv_leg_" + col);
                }
            }
            for (int a = -hw + 1; a <= hw - 1; a++) {
                boolean d1 = a == -hw + t, d2 = a == hw - t;
                String along, across;
                if (t == 0) {
                    if (plat) {
                        continue;
                    }
                    along = "tv_girt_" + col + "[facing=north]";
                    across = "tv_girt_" + col + "[facing=east]";
                } else if (d1 && d2) {
                    along = "tv_diagx_" + col + "[facing=north]";
                    across = "tv_diagx_" + col + "[facing=east]";
                } else if (d1 || d2) {
                    along = "tv_diag_" + col + "[facing=" + (d1 ? "north" : "south") + "]";
                    across = "tv_diag_" + col + "[facing=" + (d1 ? "east" : "west") + "]";
                } else {
                    continue;
                }
                set(TCX + a, y, TCZ - hw, along);
                set(TCX + a, y, TCZ + hw, along);
                set(TCX - hw, y, TCZ + a, across);
                set(TCX + hw, y, TCZ + a, across);
            }
        }
    }

    /**
     * A platform at y round the tower: grating over the inside and two out past the legs, the rail
     * round its edge, the obstruction lights at its corners, a microwave drum and a dish or two.
     */
    private void platform(int y, int hw) {
        int r = hw + 2;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                int x = TCX + dx, z = TCZ + dz;
                boolean leg = Math.abs(dx) == hw && Math.abs(dz) == hw;
                boolean core = dx == 0 && (dz == 0 || dz == 1);
                if (!leg && !core) {
                    set(x, y, z, "tv_grate");
                }
                boolean edgeX = Math.abs(dx) == r, edgeZ = Math.abs(dz) == r;
                if (edgeX && edgeZ) {
                    set(x, y + 1, z, "tv_beacon" + lit);
                } else if (edgeZ) {
                    set(x, y + 1, z, "tv_rail[facing=" + (dz < 0 ? "south" : "north") + "]");
                } else if (edgeX) {
                    set(x, y + 1, z, "tv_rail[facing=" + (dx < 0 ? "east" : "west") + "]");
                }
            }
        }
        if (y < LT) {
            set(TCX + 2, y + 2, TCZ - r, "tv_mw[facing=north]");
            set(TCX + r, y + 2, TCZ - 3, "tv_mw[facing=east]");
            set(TCX - r + 1, y + 1, TCZ + 3, "tv_dish_small[facing=west]");
        }
    }

    // ------------------------------------------------------------------ round its foot

    /** The compound's chain link and wire, the gate, the signs. */
    private void compound() {
        for (int x = KX0; x <= KX1; x++) {
            for (int z : new int[] {KZ0, KZ1}) {
                boolean gate = z == KZ0 && x >= TCX - 1 && x <= TCX + 2;
                if (gate) {
                    continue;
                }
                fence(x, z, "x");
            }
        }
        for (int z = KZ0; z <= KZ1; z++) {
            for (int x : new int[] {KX0, KX1}) {
                if (z == TCZ && x == KX1) {
                    fence(x, z, "z");
                    continue;
                }
                fence(x, z, "z");
            }
        }
        c.sign("rf", TCX - 4, 2, KZ0 - 1, "north");
        c.sign("climb", TCX + 4, 2, KZ0 - 1, "north");
        // the gate lying open, the lock cut
        set(TCX + 3, 1, KZ0 + 1, "chainlink[axis=z]");
        set(TCX + 3, 2, KZ0 + 1, "chainlink[axis=z]");
        // a tool cage and the cable drums inside
        set(KX0 + 2, 1, KZ1 - 2, "bunk_tank");
        set(KX0 + 3, 1, KZ1 - 2, "tv_case[facing=north]");
        set(KX0 + 2, 1, KZ1 - 4, "crate");
    }

    private void fence(int x, int z, String axis) {
        if (!empty(x, 1, z) && !c.get(x, 1, z).startsWith("chainlink")) {
            return;
        }
        set(x, 1, z, "chainlink[axis=" + axis + "]");
        set(x, 2, z, "chainlink[axis=" + axis + "]");
        if (c.chance() < 0.9) {
            set(x, 3, z, "st_razor");
        }
    }

    /**
     * The transmitter building: brick, one storey; the transmitter hall with the cabinets along the
     * back wall and the lines overhead going out to the bridge, the control desk, the racks; the
     * generator room next door with its switchboard; the fuel tank outside.
     */
    private void transmitter() {
        int top = 6, gx = 76;
        for (int y = 0; y <= top + 1; y++) {
            for (int x = XX0; x <= XX1; x++) {
                for (int z = XZ0; z <= XZ1; z++) {
                    boolean wall = x == XX0 || x == XX1 || z == XZ0 || z == XZ1;
                    if (y == 0) {
                        set(x, 0, z, wall ? "floor_concrete" : "sc_vct");
                    } else if (y == top) {
                        set(x, y, z, wall ? "cc_precast" : "un_roof");
                    } else if (y == top + 1) {
                        if (wall) {
                            set(x, y, z, "cc_coping");
                        }
                    } else if (wall) {
                        boolean win = y == 3 && z == XZ0 && x % 3 == 0 && x != 70 && x != 71;
                        set(x, y, z, win ? (c.chance() < 0.2 ? "cc_win_broken" : "cc_win") + "[facing=north]" : c.brick(x, y, z));
                    } else if (x == gx) {
                        set(x, y, z, "concrete_panel");
                    }
                }
            }
        }
        c.doors(70, 0, XZ0, "fire_door", "north");
        c.sign("tx", 72, 3, XZ0 - 1, "north");
        c.sign("rf", 66, 3, XZ0 - 1, "north");
        c.door(gx, 0, 100, "fire_door", "east");
        c.door(XX1, 0, 110, "fire_door", "east");
        c.sign("gen", XX1 + 1, 3, 108, "east");
        // the transmitters along the back wall, two blocks high, lit if the generator's running
        String on = c.power ? "_on" : "";
        for (int x = XX0 + 2; x <= gx - 2; x++) {
            if (x == XX0 + 8) {
                set(x, 1, XZ1 - 1, "tv_rack" + on + "[facing=north]");
                set(x, 2, XZ1 - 1, "tv_rack" + on + "[facing=north]");
                continue;
            }
            set(x, 1, XZ1 - 1, "tv_tx" + on + "[facing=north]");
            set(x, 2, XZ1 - 1, "tv_tx_top" + on + "[facing=north]");
        }
        // the lines: up off the cabinets, along under the roof, out through the west wall
        for (int z = TCZ; z <= XZ1 - 2; z++) {
            set(67, ICE_Y, z, "tv_coax[axis=z]");
        }
        for (int x = XX0; x <= 66; x++) {
            set(x, ICE_Y, TCZ, "tv_coax[axis=x]");
        }
        // the racks down the west wall, the control desk, the dummy load
        for (int z = XZ0 + 2; z <= XZ1 - 4; z++) {
            if (z == TCZ) {
                continue;
            }
            set(XX0 + 1, 1, z, (z % 3 == 0 ? "tv_vtr" : "tv_rack") + on + "[facing=east]");
            set(XX0 + 1, 2, z, "tv_rack" + on + "[facing=east]");
        }
        for (int x = 64; x <= 70; x++) {
            set(x, 1, 100, x == 67 ? "tv_audio[facing=north]" : x % 2 == 0 ? "tv_monitor" + on + "[facing=north]" : "desk[facing=north]");
        }
        set(67, 1, 99, "office_chair[facing=south]");
        set(72, 1, 108, "bunk_tank");
        set(72, 2, 108, "bunk_tank_top");
        set(73, 1, 104, "filing_cabinet[facing=west]");
        set(73, 1, 103, "home_workbench[facing=west]");
        c.ceiling(top, XX0 + 1, XZ0 + 1, gx - 1, XZ1 - 1, 0.4);
        // the generator room
        set(gx + 3, 1, 96, "bunk_st_generator[facing=south]");
        set(gx + 3, 1, 104, "bunk_st_generator[facing=south]");
        set(XX1 - 1, 1, 114, "bunk_switchboard[facing=west]");
        set(XX1 - 1, 2, 114, "bunk_switchboard_top[facing=west]");
        set(gx + 1, 1, 116, "bunk_drum_water");
        set(gx + 2, 1, 116, "bunk_drum_water");
        c.ceiling(top, gx + 1, XZ0 + 1, XX1 - 1, XZ1 - 1, 0.4);
        c.mess(XX0 + 1, XZ0 + 1, XX1 - 1, XZ1 - 1, 0, 0.03);
        // outside: the fuel tank on its saddles, the bollards
        for (int z = 96; z <= 102; z++) {
            set(XX1 + 3, 1, z, "tank_white");
            set(XX1 + 4, 1, z, "tank_white");
        }
        for (int z = 94; z <= 104; z += 2) {
            set(XX1 + 6, 1, z, "fence_post");
        }
        c.lampPost(XX0 + 2, XZ0 - 3);
    }

    /** The ice bridge from the transmitter building to the tower, on its posts, the lines along it. */
    private void bridge() {
        for (int x = TCX + 1; x < XX0; x++) {
            set(x, ICE_Y, TCZ, "tv_ice[axis=x]");
        }
        for (int x = 48; x < XX0; x += 3) {
            if (x == KX1) {
                continue;
            }
            for (int y = 1; y < ICE_Y; y++) {
                set(x, y, TCZ, "tv_post");
            }
        }
        set(XX0, ICE_Y, TCZ, "tv_coax[axis=x]");
    }

    // ------------------------------------------------------------------ the dish farm

    /**
     * The satellite dishes east of the transmitter building in their own fence: two big ones, two
     * middling, a row of small ones on their footings, the equipment hut.
     */
    private void dishes() {
        for (int x = DX0; x <= DX1; x++) {
            for (int z : new int[] {DZ0, DZ1}) {
                if (z == DZ0 && x >= 108 && x <= 111) {
                    continue;
                }
                fence(x, z, "x");
            }
        }
        for (int z = DZ0; z <= DZ1; z++) {
            fence(DX0, z, "z");
            fence(DX1, z, "z");
        }
        dish(102, 92, 4);
        dish(118, 110, 4);
        dish(104, 114, 2);
        dish(120, 90, 2);
        for (int x = 96; x <= 124; x += 4) {
            set(x, 1, 123, "tv_pier");
            set(x, 2, 123, "tv_dish_small[facing=north]");
        }
        // the hut
        int hx0 = 94, hz0 = 78, hx1 = 100, hz1 = 84;
        for (int y = 0; y <= 4; y++) {
            for (int x = hx0; x <= hx1; x++) {
                for (int z = hz0; z <= hz1; z++) {
                    boolean wall = x == hx0 || x == hx1 || z == hz0 || z == hz1;
                    if (y == 0) {
                        set(x, 0, z, "floor_concrete");
                    } else if (y == 4) {
                        set(x, y, z, "un_roof");
                    } else if (wall) {
                        set(x, y, z, c.brick(x, y, z));
                    }
                }
            }
        }
        c.door(97, 0, hz1, "fire_door", "south");
        for (int x = hx0 + 1; x < hx1; x++) {
            set(x, 1, hz0 + 1, "tv_rack" + (c.power ? "_on" : "") + "[facing=south]");
            set(x, 2, hz0 + 1, "tv_rack" + (c.power ? "_on" : "") + "[facing=south]");
        }
    }

    /**
     * A dish of radius r: the reflector a disc of 45-degree plates stepped up toward the south so it
     * looks up into the northern sky, the feed out in front of it on its strut, the pedestal behind.
     */
    private void dish(int cx, int cz, int r) {
        int cy = r + 3;
        c.fill(cx - 1, 0, cz - 1, cx + 1, 0, cz + 2, "floor_concrete");
        for (int y = 1; y < cy; y++) {
            set(cx, y, cz, "steel_girder");
        }
        for (int u = -r; u <= r; u++) {
            for (int v = -r; v <= r; v++) {
                if (u * u + v * v <= r * r + r / 2) {
                    set(cx + u, cy + v, cz + v, "tv_dish[facing=south]");
                }
            }
        }
        int f = r / 2 + 1;
        for (int k = 1; k < f; k++) {
            set(cx, cy + k, cz - k, "tv_strut[facing=south]");
        }
        set(cx, cy + f, cz - f, "tv_feed[facing=south]");
    }
}
