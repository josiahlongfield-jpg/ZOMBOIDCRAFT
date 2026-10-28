package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.PowerStation.BZ1;
import static com.zomboidcraft.world.county.PowerStation.CX1;
import static com.zomboidcraft.world.county.PowerStation.RZ1;
import static com.zomboidcraft.world.county.PowerStation.SX0;
import static com.zomboidcraft.world.county.PowerStation.SZ0;
import static com.zomboidcraft.world.county.PowerStation.TRIP;
import static com.zomboidcraft.world.county.PowerStation.YZ1;

/**
 * The yards behind the yard road. The coal yard: the pile the dozer pushed up to the reclaim
 * hopper, the belt along the ground into the crusher tower, the bucket elevator up its side and
 * the enclosed gallery carrying the belt over the road into the boiler house; the two fuel oil
 * tanks in their berm. The switchyard over the service road: the three main transformers behind
 * their firewalls, the breakers and switches, the lattice gantries with the lines strung between,
 * the two line towers at the back, the relay house.
 */
final class PowerYards {
    /** The crusher tower (walls inclusive), the floor it shares with the gallery, its roof; its stair. */
    static final int KX0 = 46, KX1 = 60, KZ0 = 103, KZ1 = 118, KROOF = 40;
    static final int TST_X = 54, TST_Z = 107;
    /** The gallery's walls either side of the belt. */
    static final int GX0 = 51, GX1 = 55;
    /** The transformers' centres, the gantry rows, the line towers. */
    static final int[] XF_X = {82, 99, 116};
    static final int[] GANTRY_Z = {130, 156, 182};
    static final int[] TOWER_X = {98, 120};
    static final int TOWER_Z = 198;

    private final PowerStation c;

    PowerYards(PowerStation c) {
        this.c = c;
    }

    void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    boolean empty(int x, int y, int z) {
        return c.empty(x, y, z);
    }

    double chance() {
        return c.chance();
    }

    void build() {
        tower();
        gallery();
        pile();
        tanks();
        switchyard();
        relayHouse();
        lineTowers();
        perimeter();
    }

    // ------------------------------------------------------------------ the coal yard

    /**
     * The crusher tower: corrugated siding on its steel frame, the stair up inside to the gallery's
     * floor and the roof, the bucket elevator beside it, the crusher at its foot where the yard belt
     * comes in.
     */
    private void tower() {
        for (int y = 0; y <= KROOF + 1; y++) {
            for (int x = KX0; x <= KX1; x++) {
                for (int z = KZ0; z <= KZ1; z++) {
                    boolean wall = x == KX0 || x == KX1 || z == KZ0 || z == KZ1;
                    boolean corner = (x == KX0 || x == KX1) && (z == KZ0 || z == KZ1);
                    if (y == 0) {
                        set(x, y, z, "floor_concrete");
                    } else if (y == KROOF) {
                        set(x, y, z, "un_roof");
                    } else if (y == KROOF + 1) {
                        if (wall) {
                            set(x, y, z, "corrugated_grey_b");
                        }
                    } else if (corner) {
                        set(x, y, z, "steel_girder");
                    } else if (wall) {
                        int along = x == KX0 || x == KX1 ? z - KZ0 : x - KX0;
                        boolean win = (y % 10 == 6 || y % 10 == 7) && along % 4 == 2;
                        String side = x == KX0 ? "west" : x == KX1 ? "east" : z == KZ0 ? "north" : "south";
                        set(x, y, z, win ? (chance() < 0.4 ? "pw_sash_broken" : "pw_sash") + "[facing=" + side + "]"
                                : y % 10 == 0 ? "steel_girder" : along % 7 == 3 && y < 12 ? "corrugated_grey_stain" : "corrugated_grey");
                    }
                }
            }
        }
        // the floor at the gallery's level, the stair
        for (int x = KX0 + 1; x < KX1; x++) {
            for (int z = KZ0 + 1; z < KZ1; z++) {
                if (!c.shaft(x, z)) {
                    set(x, TRIP, z, "fit_deckplate");
                }
            }
        }
        c.stair(TST_X, TST_Z, false, new int[] {0, TRIP, KROOF}, "concrete_panel", "un_roof");
        c.doors(50, 0, KZ0, "fire_door", "north");
        c.sign("coal", 52, 4, KZ0 - 1, "north");
        // the bucket elevator up from the boot to the head over the belt
        for (int y = 1; y <= KROOF - 2; y++) {
            for (int x = 50; x <= 51; x++) {
                for (int z = 110; z <= 111; z++) {
                    set(x, y, z, y == KROOF - 2 ? "pw_crusher[facing=north]" : "pw_elev[facing=" + (x == 50 ? "west" : "east") + "]");
                }
            }
        }
        // the crusher at the foot, the yard belt in through the west wall
        for (int z = 113; z <= 115; z++) {
            set(50, 1, z, "pw_crusher[facing=north]");
            set(51, 1, z, "pw_crusher[facing=north]");
        }
        for (int x = 30; x < 50; x++) {
            set(x, 1, 112, "pw_belt_coal[axis=x]");
        }
        // the belt up top from the elevator's head north to the gallery
        for (int z = KZ0; z <= 111; z++) {
            set(PowerHalls.BELT_X, TRIP + 1, z, "pw_belt_coal[axis=z]");
        }
        for (int x = GX0; x <= GX1; x++) {
            for (int y = TRIP + 1; y <= TRIP + 4; y++) {
                set(x, y, KZ0, x == GX0 || x == GX1 ? "steel_girder" : "air");
            }
        }
        // the reclaim hopper the dozer pushed into
        for (int x = 25; x <= 29; x++) {
            for (int z = 110; z <= 114; z++) {
                set(x, 0, z, "pw_hopper");
            }
        }
        c.row("pw_spray_coal_", 3, KX0 - 1, 3, 104, "west", 0.0);
        c.sign("hats", KX0 - 1, 3, 116, "west");
        p().vehicle(36.5, 1, 104.5, 90f, "box_truck", c.rng.nextLong());
    }

    private com.zomboidcraft.world.city.Placer p() {
        return c.p;
    }

    /** The gallery: the belt in its corrugated box over the yard road, from the tower into the boiler house. */
    private void gallery() {
        for (int z = BZ1 + 1; z < KZ0; z++) {
            for (int x = GX0; x <= GX1; x++) {
                set(x, TRIP, z, "fit_deckplate");
                set(x, TRIP + 5, z, "corrugated_grey_b");
                if (x == GX0 || x == GX1) {
                    for (int y = TRIP + 1; y <= TRIP + 4; y++) {
                        boolean win = (y == TRIP + 2 || y == TRIP + 3) && z % 3 == 1;
                        set(x, y, z, win ? "pw_sash[facing=" + (x == GX0 ? "west" : "east") + "]" : "corrugated_grey");
                    }
                } else if (x == PowerHalls.BELT_X) {
                    set(x, TRIP + 1, z, "pw_belt_coal[axis=z]");
                } else if (z % 6 == 0) {
                    set(x, TRIP + 4, z, c.power && chance() < 0.3 ? "fit_tube_on" : "fit_tube");
                }
            }
        }
        // its trestle south of the road
        for (int x : new int[] {GX0, GX1}) {
            for (int y = 1; y < TRIP; y++) {
                set(x, y, RZ1 + 1, "pw_trestle");
            }
        }
        for (int x = GX0 + 1; x < GX1; x++) {
            set(x, TRIP - 1, RZ1 + 1, "pw_girt[axis=x]");
        }
    }

    /** The coal pile: a long mound, the dozer at its flank where the last load went. */
    private void pile() {
        int cx = 26, cz = 147;
        double ax = 19, bz = 24, hmax = 10;
        for (int x = cx - 20; x <= cx + 20; x++) {
            for (int z = cz - 25; z <= cz + 25; z++) {
                double u = (x - cx) / ax, v = (z - cz) / bz;
                double r2 = u * u + v * v;
                if (r2 >= 1 || x < 5 || x > CX1 - 2) {
                    continue;
                }
                double h = hmax * Math.pow(1 - r2, 0.8) * (0.9 + 0.2 * Math.sin(x * 0.7 + z * 0.3));
                int top = (int) Math.floor(h);
                for (int y = 1; y <= top; y++) {
                    set(x, y, z, "gl_coal");
                }
                if (h - top >= 0.5) {
                    set(x, top + 1, z, "pw_coal_slab");
                }
                set(x, 0, z, "gl_coal");
            }
        }
        // the dozer, nosed into the pile
        int dx = 46, dz = 150;
        for (int x = dx; x <= dx + 2; x++) {
            set(x, 1, dz - 1, "pw_dozer_blade[facing=north]");
        }
        for (int z = dz; z <= dz + 3; z++) {
            set(dx, 1, z, "pw_track[axis=z]");
            set(dx + 2, 1, z, "pw_track[axis=z]");
            set(dx + 1, 1, z, "pw_dozer_body[facing=north]");
        }
        set(dx + 1, 2, dz, "pw_dozer_body[facing=north]");
        set(dx + 1, 2, dz + 1, "pw_dozer_body[facing=north]");
        set(dx + 1, 2, dz + 2, "pw_dozer_cab[facing=north]");
        set(dx + 1, 2, dz + 3, "pw_dozer_body[facing=south]");
        floodlight(8, 118);
        floodlight(CX1 - 4, 172);
    }

    /** The two fuel oil tanks in their berm, the labels, the ladders up their sides. */
    private void tanks() {
        int[][] at = {{17, 190}, {45, 190}};
        int r = 9;
        for (int x = 5; x <= 57; x++) {
            for (int z = 177; z <= 203; z++) {
                boolean edge = x == 5 || x == 57 || z == 177 || z == 203;
                if (edge && !(z == 177 && x >= 29 && x <= 32)) {
                    set(x, 1, z, "concrete_dark");
                    set(x, 2, z, "cc_coping");
                }
            }
        }
        for (int[] t : at) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    int x = t[0] + dx, z = t[1] + dz;
                    if (d <= r + 0.5) {
                        set(x, 0, z, "concrete_dark");
                        for (int y = 1; y <= 12; y++) {
                            set(x, y, z, "pw_tank");
                        }
                        set(x, 13, z, "pw_tank_roof");
                        if (d <= 5) {
                            set(x, 13, z, "pw_tank");
                            set(x, 14, z, "pw_tank_roof");
                        }
                    }
                }
            }
            c.picture("pw_tanklabel_", 2, 2, t[0] - 1, 7, t[1] - r - 1, "north");
            for (int y = 1; y <= 13; y++) {
                set(t[0] - r - 1, y, t[1], "ladder[facing=west]");
            }
            set(t[0] + r + 1, 1, t[1] - 2, "fit_valve[facing=east]");
            set(t[0] + r + 1, 1, t[1] + 2, "fit_fuel_valves[facing=east]");
        }
        c.sign("oil", 30, 3, 176, "north");
        c.sign("smoke", 36, 3, 176, "north");
    }

    private void floodlight(int x, int z) {
        for (int y = 1; y <= 10; y++) {
            set(x, y, z, "rw_pole");
        }
        set(x, 11, z, c.power && chance() < 0.6 ? "fit_flood_on" : "fit_flood");
    }

    // ------------------------------------------------------------------ the switchyard

    /** The transformers on their pads, the firewalls, the breakers and switches, the gantries and lines, the fence. */
    private void switchyard() {
        int x0 = SX0, x1 = c.w - 6, z0 = SZ0, z1 = YZ1 - 2;
        c.fence(x0, z0, x1, z0, 0.0);
        for (int z = z0; z <= YZ1; z++) {
            if (z >= z0 + 6 && z <= z0 + 10) {
                continue;
            }
            if (empty(x0, 1, z)) {
                for (int y = 1; y <= 2; y++) {
                    set(x0, y, z, (z - z0) % 8 == 0 ? "fence_post" : "chainlink[axis=z]");
                }
                set(x0, 3, z, "st_razor");
            }
        }
        for (int z = z0 + 2; z < z1; z += 16) {
            c.sign("danger", x0 - 1, 2, z, "west");
        }
        for (int x = x0 + 4; x < x1; x += 18) {
            c.sign("danger", x, 2, z0 - 1, "north");
        }
        c.sign("yard", x0 - 1, 3, z0 + 12, "west");
        // the main transformers: each on its pad, the radiators down its sides, the bushings on top
        for (int xc : XF_X) {
            for (int x = xc - 5; x <= xc + 5; x++) {
                for (int z = z0 + 2; z <= z0 + 12; z++) {
                    set(x, 0, z, "concrete_dark");
                }
            }
            for (int x = xc - 3; x <= xc + 3; x++) {
                for (int z = z0 + 4; z <= z0 + 10; z++) {
                    for (int y = 1; y <= 5; y++) {
                        set(x, y, z, "pw_xfmr");
                    }
                }
            }
            for (int z = z0 + 5; z <= z0 + 9; z++) {
                for (int y = 1; y <= 5; y++) {
                    set(xc - 4, y, z, "pw_fins[facing=west]");
                    set(xc + 4, y, z, "pw_fins[facing=east]");
                }
            }
            for (int dx = -2; dx <= 2; dx += 2) {
                set(xc + dx, 6, z0 + 5, "pw_bushing");
                set(xc + dx, 7, z0 + 5, "pw_bushing");
                set(xc + dx, 6, z0 + 9, "pw_bushing_lo");
            }
            for (int x = xc - 2; x <= xc + 2; x++) {
                set(x, 6, z0 + 7, "pw_conservator[axis=x]");
            }
            c.sign("danger", xc - 1, 3, z0 + 3, "north");
        }
        for (int fx : new int[] {(XF_X[0] + XF_X[1]) / 2, (XF_X[1] + XF_X[2]) / 2}) {
            for (int z = z0 + 2; z <= z0 + 12; z++) {
                for (int y = 1; y <= 8; y++) {
                    set(fx, y, z, "concrete_panel");
                }
            }
        }
        // the gantries: lattice columns, the beam across, the strings under it, the lines strung between
        int[] cols = {74, 86, 98, 110, 122};
        int gy = 15;
        for (int gz : GANTRY_Z) {
            for (int cx : cols) {
                for (int y = 1; y < gy; y++) {
                    set(cx, y, gz, "pw_leg");
                }
                set(cx, 0, gz, "concrete_dark");
            }
            for (int x = cols[0]; x <= cols[cols.length - 1]; x++) {
                set(x, gy, gz, "pw_girt[axis=x]");
                if (Math.floorMod(x - cols[0], 4) == 2) {
                    set(x, gy - 1, gz, "pw_ins_string");
                }
            }
        }
        for (int x = cols[0] + 2; x < cols[cols.length - 1]; x += 4) {
            for (int z = GANTRY_Z[0] + 1; z < GANTRY_Z[GANTRY_Z.length - 1]; z++) {
                boolean at = false;
                for (int gz : GANTRY_Z) {
                    at |= z == gz;
                }
                if (!at) {
                    set(x, gy - 2, z, "pw_wire[axis=z]");
                }
            }
        }
        // the breakers and the disconnect switches between the gantries, the bus on its posts
        for (int bx = 78; bx <= 118; bx += 10) {
            for (int bz : new int[] {143, 169}) {
                for (int x = bx - 1; x <= bx + 1; x++) {
                    for (int z = bz - 1; z <= bz + 1; z++) {
                        set(x, 0, z, "concrete_dark");
                    }
                }
                set(bx, 1, bz, "pw_breaker[facing=north]");
                for (int sz : new int[] {bz - 5, bz + 5}) {
                    for (int y = 1; y <= 5; y++) {
                        set(bx, y, sz, "pw_leg");
                    }
                    set(bx, 6, sz, "pw_disconnect[axis=z]");
                }
            }
        }
        for (int x = 74; x <= 122; x++) {
            if (Math.floorMod(x - 74, 6) == 0) {
                for (int y = 1; y <= 6; y++) {
                    set(x, y, 150, "pw_leg");
                }
                set(x, 7, 150, "pw_post_ins");
            }
            set(x, 8, 150, "pw_bus[axis=x]");
        }
        floodlight(x0 + 3, z0 + 20);
        floodlight(x1 - 3, 168);
    }

    /** The relay house: brick, the relay panels in rows, the battery racks, the desk. */
    private void relayHouse() {
        int x0 = SX0 + 2, x1 = SX0 + 16, z0 = 186, z1 = 198, top = 5;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, "floor_concrete");
                for (int y = 1; y <= top + 1; y++) {
                    if (y == top) {
                        set(x, y, z, wall ? c.brick(x, y, z) : "un_roof");
                    } else if (wall) {
                        String f = z == z0 ? "north" : z == z1 ? "south" : x == x0 ? "west" : "east";
                        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                        boolean win = !corner && (y == 2 || y == 3) && (x + z) % 3 == 0 && y <= top - 1;
                        set(x, y, z, y == top + 1 ? "cc_coping" : win ? (chance() < 0.3 ? "pw_sash_broken" : "pw_sash") + "[facing=" + f + "]"
                                : y == 1 ? "brick_dark" : c.brick(x, y, z));
                    }
                }
            }
        }
        c.door(x0 + 6, 0, z0, "flat_door", "north");
        c.sign("relay", x0 + 3, 3, z0 - 1, "north");
        for (int z : new int[] {z0 + 4, z0 + 8}) {
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                if (x == x0 + 6) {
                    continue;
                }
                set(x, 1, z, "pw_vb_lo" + (c.power ? "_on" : "") + "[facing=north]");
                set(x, 2, z, "pw_vb_hi[facing=north]");
            }
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 2) {
            set(x, 1, z1 - 1, "bunk_rack_boxes[facing=north]");
        }
        set(x1 - 1, 1, z0 + 2, "pw_desk[facing=west]");
        for (int x = x0 + 1; x < x1; x += 3) {
            for (int z = z0 + 2; z < z1; z += 4) {
                if (empty(x, top - 1, z)) {
                    set(x, top - 1, z, c.power && chance() < 0.5 ? "fit_tube_on" : "fit_tube");
                }
            }
        }
    }

    /** The two line towers at the back where the circuits leave: four legs braced on every face, the arms with their strings. */
    private void lineTowers() {
        int h = 28;
        for (int tx : TOWER_X) {
            int tz = TOWER_Z;
            for (int y = 0; y <= h; y++) {
                for (int dx = -2; dx <= 2; dx += 4) {
                    for (int dz = -2; dz <= 2; dz += 4) {
                        set(tx + dx, y, tz + dz, y == 0 ? "concrete_dark" : "pw_leg");
                    }
                }
                if (y > 0 && y % 3 == 0) {
                    for (int d = -1; d <= 1; d++) {
                        String br = y % 6 == 0 ? "pw_girt" : "pw_diagx";
                        set(tx + d, y, tz - 2, br.equals("pw_girt") ? "pw_girt[axis=x]" : "pw_diagx[facing=north]");
                        set(tx + d, y, tz + 2, br.equals("pw_girt") ? "pw_girt[axis=x]" : "pw_diagx[facing=south]");
                        set(tx - 2, y, tz + d, br.equals("pw_girt") ? "pw_girt[axis=z]" : "pw_diagx[facing=west]");
                        set(tx + 2, y, tz + d, br.equals("pw_girt") ? "pw_girt[axis=z]" : "pw_diagx[facing=east]");
                    }
                }
            }
            for (int ay : new int[] {h - 8, h}) {
                for (int x = tx - 8; x <= tx + 8; x++) {
                    if (Math.abs(x - tx) > 2) {
                        set(x, ay, tz, "pw_girt[axis=x]");
                    }
                }
                for (int x : new int[] {tx - 7, tx - 4, tx + 4, tx + 7}) {
                    set(x, ay - 1, tz, "pw_ins_string");
                }
            }
            c.sign("danger", tx - 1, 3, tz - 3, "north");
        }
    }

    /** The plant's outer fence round the yards: the west side, the back, the east side. */
    private void perimeter() {
        c.fence(4, RZ1 + 1, 4, YZ1, 0.03);
        c.fence(4, YZ1, c.w - 6, YZ1, 0.03);
        c.fence(c.w - 6, RZ1 + 1, c.w - 6, YZ1, 0.02);
    }
}
