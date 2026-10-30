package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven International's hangars east of the apron, facing south onto their ramp and the
 * taxiway, in the airport's frame ({@link Airport}), after the steel-truss hangars of
 * refs/airport/hangars: grey ribbed walls on a concrete plinth, the roof carried on deep trusses
 * front to back, the doors a row of sliding leaves on two tracks with the hangar's number
 * stencilled across them.
 *
 * <ul>
 * <li>Hangar 1, the maintenance hangar: a second of the line's trijets in for its check when the
 * flights stopped, nose in, the stair truck at its front door, the tail in its scaffold dock, a
 * spare engine on its stand, the tug and the power cart; the workshop and the stores down the
 * lean-to on its east side with the offices upstairs looking down on the floor through their
 * windows.</li>
 * <li>Hangar 2, taken over as the evacuation's relief depot: the supplies on their pallets in rows,
 * the triage and shelter tents, the cots.</li>
 * <li>The T-hangars for the light planes along the east end, a row of eight with their sectional
 * doors, most shut, one or two up on what the owners kept in them.</li>
 * <li>The private jet centre at the east end, by the A5 connector to the runway (the Air Guard's
 * ramp until Josiah, 2026-10-30, sent the fighters and helicopters to Caldwell Army Airfield):
 * hangars 3 and 4 with their doors run right back into the pockets either side and a business
 * jet in each (tools/city/fleet.py), two more on the line facing the taxiway with their stairs
 * down, the helipads, the lounge, the fuel bowser's stand, the chocks, the cones.</li>
 * </ul>
 */
final class AirportHangars extends Grounds {
    /** Hangar 1: its walls, its door's clear height and its roof; the lean-to on its east side out to AX1. */
    static final int X0 = 440, X1 = 519, Z0 = -252, Z1 = -171, DOOR = 25, ROOF = 30, AX1 = X1 + 14, AS2 = 5, AROOF = 10;
    /** Hangar 2. */
    static final int H2X0 = 546, H2X1 = 601, H2Z0 = -238, H2Z1 = -177, H2DOOR = 17, H2ROOF = 21;
    /** The T-hangars: eight units from TX0, each TW wide, the back at TZ0 and the doors at TZ1. */
    static final int TX0 = 622, TW = 12, UNITS = 8, TZ0 = -214, TZ1 = -201, TROOF = 6;
    /** The jet centre's hangars 3 and 4 (x from G3, G4, GW wide, back at GZ0, door wall at GZ1), the door's height, the roof. */
    static final int G3 = 744, G4 = 800, GW = 44, GZ0 = -232, GZ1 = -193, GDOOR = 13, GROOF = 17;
    /** The jet centre's lounge building, the line of stands on the apron, the helipads' middles. */
    static final int OX0 = 848, OX1 = 879, OZ0 = -232, OZ1 = -214, LINE_X = 858, LINE_Z = -158;
    static final int[][] PADS = {{906, -200}, {936, -200}, {966, -200}};
    /** The ramp's edge on the taxiway's shoulder; everything this builds lies within these. */
    static final int BX0 = X0 - 4, BX1 = 990, BZ0 = Z0 - 4, BZ1 = Airport.TWY_Z - Airport.TWY_SHOULDER - 1;

    AirportHangars(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.3, 0.0, 0.25);
    }

    void build() {
        ramp();
        shell(X0, X1, Z0, Z1, DOOR, ROOF);
        leanTo();
        shell(H2X0, H2X1, H2Z0, H2Z1, H2DOOR, H2ROOF);
        doors(X0 + 4, 9, Z1, DOOR, 1, 3);
        doors(H2X0 + 4, 6, H2Z1, H2DOOR, 2, 2);
        maintenance();
        depot();
        tHangars();
        guard();
    }

    private String clad(int y) {
        double r = chance();
        return y <= 2 ? "concrete_panel_b" : r < 0.05 ? "corrugated_grey_stain" : r < 0.3 ? "corrugated_grey_b" : "corrugated_grey";
    }

    // ------------------------------------------------------------------ the shells

    /**
     * A hangar from x0 to x1 and z0 (the back) to z1 (the door's wall), the door's opening h high
     * across all but four each side, the roof at top: the walls, the trusses and the purlins, the
     * high-bays, the clerestory, the roof deck with its skylight strips.
     */
    private void shell(int x0, int x1, int z0, int z1, int h, int top) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, wall ? "concrete_dark" : chance() < 0.03 ? "navy_hangar_oil" : "navy_hangar_floor");
                for (int y = 1; y < top; y++) {
                    set(x, y, z, wall ? clad(y) : "air");
                }
                set(x, top, z, "un_roof");
            }
        }
        b.flat(x0, z0, x1, z1, top, "corrugated_grey", 0, null);
        parapet(x0, z0, x1, z1, top);
        // the blue band round the top of the walls, the floods over the door, a door in each side
        for (int x = x0; x <= x1; x++) {
            for (int z : new int[] {z0, z1}) {
                set(x, top - 1, z, "corrugated_blue_b");
                set(x, top - 2, z, "corrugated_blue_b");
            }
        }
        for (int z = z0; z <= z1; z++) {
            for (int x : new int[] {x0, x1}) {
                set(x, top - 1, z, "corrugated_blue_b");
                set(x, top - 2, z, "corrugated_blue_b");
            }
        }
        for (int x = x0 + 6; x <= x1 - 6; x += 12) {
            set(x, h + 3, z1 + 1, (power && chance() < 0.4 ? "cp_flood_lit" : "cp_flood") + "[facing=south]");
        }
        // the skylight strips along the roof, every eight, between the trusses
        for (int x = x0 + 8; x < x1 - 4; x += 8) {
            for (int z = z0 + 4; z < z1 - 3; z++) {
                set(x, top, z, chance() < 0.12 ? "us_skylight_broken" : "us_skylight");
                set(x, top + 1, z, "air");
            }
        }
        // the clerestory down both sides and across the back
        for (int z = z0 + 3; z < z1 - 2; z++) {
            if (Math.floorMod(z - z0, 6) < 4) {
                win(x0, top - 5, z, "west");
                win(x1, top - 5, z, "east");
            }
        }
        for (int x = x0 + 3; x < x1 - 2; x++) {
            if (Math.floorMod(x - x0, 6) < 4) {
                win(x, top - 5, z0, "north");
            }
        }
        // the trusses front to back every eight: the chords, the posts between, the purlins across
        for (int x = x0 + 4; x < x1; x += 8) {
            for (int z = z0 + 1; z < z1; z++) {
                set(x, top - 1, z, "sf_beam[axis=z]");
                set(x, top - 4, z, "sf_beam[axis=z]");
                boolean post = Math.floorMod(z - z0, 4) == 0;
                for (int y = top - 3; y <= top - 2; y++) {
                    set(x, y, z, post ? "sf_beam[axis=y]" : "tx_truss[axis=z]");
                }
            }
        }
        for (int z = z0 + 4; z < z1; z += 16) {
            for (int x = x0 + 1; x < x1; x++) {
                if (empty(x, top - 1, z)) {
                    set(x, top - 1, z, "sf_beam[axis=x]");
                }
            }
        }
        // the high-bays hung under the bottom chords, half of them dead
        for (int x = x0 + 4; x < x1; x += 8) {
            for (int z = z0 + 6; z < z1 - 2; z += 9) {
                set(x, top - 5, z, power && chance() < 0.35 ? "fit_highbay_on" : chance() < 0.15 ? "fit_highbay_flicker" : "fit_highbay");
            }
        }
        // the door's opening and the head girder over it
        for (int x = x0 + 4; x <= x1 - 4; x++) {
            set(x, 0, z1, "navy_hangar_floor");
            for (int y = 1; y <= h; y++) {
                set(x, y, z1, "air");
            }
            set(x, h + 1, z1, "fit_girder[axis=x]");
        }
    }

    /** A low parapet round a roof at y, its precast cap; the hangars' roofs carry no plant. */
    private void parapet(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    set(x, y + 1, z, "corrugated_grey");
                    String f = z == z0 ? "north" : z == z1 ? "south" : x == x0 ? "west" : "east";
                    boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                    set(x, y + 2, z, corner ? "bd_coping_pc_corner" : "bd_coping_pc[facing=" + f + "]");
                }
            }
        }
    }

    private void win(int x, int y, int z, String out) {
        for (int yy = y; yy <= y + 1; yy++) {
            set(x, yy, z, (chance() < 0.15 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]");
        }
    }

    /**
     * The sliding door across a hangar's opening from x0: n leaves eight wide, h high, on two tracks
     * (the wall's line and one outside it), the leaves alternating between them so that each can
     * slide over its neighbour; the pair at open (a leaf on the inner track and the one after it)
     * pushed aside to leave a gap of sixteen. The hangar's number across leaf 1, the band along the
     * bottom, the rubber at each leaf's end, the rails in the floor.
     */
    private void doors(int x0, int n, int z, int h, int num, int open) {
        for (int x = x0 - 2; x < x0 + n * 8 + 2; x++) {
            set(x, 0, z, "ah_track[axis=x]");
            set(x, 0, z + 1, "ah_track[axis=x]");
        }
        for (int i = 0; i < n; i++) {
            int track = i % 2, lx = x0 + i * 8;
            if (i == open) {
                lx -= 8;
            } else if (i == open + 1) {
                lx += 8;
            }
            int lz = z + track;
            for (int x = lx; x < lx + 8; x++) {
                for (int y = 1; y <= h; y++) {
                    set(x, y, lz, (y == 1 ? "ah_leaf_band" : x == lx + 7 ? "ah_leaf_edge" : "ah_leaf") + "[facing=south]");
                }
            }
            if (i == 1 || i == n - 2) {
                picture("ah_leaf_num_" + num + "_", 3, 3, lx + 2, h - 8, lz, "south");
            }
        }
        // the outer track's head beam carried out over the leaves on its brackets
        for (int x = x0 - 2; x < x0 + n * 8 + 2; x++) {
            set(x, h + 1, z + 1, "fit_girder[axis=x]");
        }
    }

    // ------------------------------------------------------------------ hangar 1

    /** The lean-to down hangar 1's east side: the workshop and the stores below, the offices above with their windows on the floor. */
    private void leanTo() {
        int ax0 = X1, az0 = Z0 + 10, az1 = Z1;
        for (int x = ax0; x <= AX1; x++) {
            for (int z = az0; z <= az1; z++) {
                boolean wall = x == ax0 || x == AX1 || z == az0 || z == az1;
                if (x == ax0) {
                    continue;
                }
                set(x, 0, z, wall ? "concrete_dark" : "un_slab_vct");
                for (int y = 1; y < AROOF; y++) {
                    set(x, y, z, wall ? clad(y) : "air");
                }
                if (!wall) {
                    set(x, AS2, z, "un_slab_vct");
                }
                set(x, AROOF, z, "un_roof");
            }
        }
        b.flat(ax0, az0, AX1, az1, AROOF, "corrugated_grey", 0, null);
        parapet(ax0, az0, AX1, az1, AROOF);
        for (int y0 : new int[] {0, AS2}) {
            for (int z = az0 + 2; z < az1 - 1; z++) {
                if (Math.floorMod(z - az0, 5) < 3) {
                    win(AX1, y0 + 2, z, "east");
                }
            }
        }
        // the stair at the front, its door north into the workshop
        int sx = AX1 - 5, sz = az1 - 10;
        stair(sx, sz, false, new int[] {0, AS2, AROOF}, "ml_precast", "un_roof");
        // the doors: from the hangar floor (two, wide) and the front door onto the ramp
        for (int z : new int[] {az0 + 6, az0 + 30}) {
            for (int dz = 0; dz <= 1; dz++) {
                for (int y = 1; y <= 3; y++) {
                    set(X1, y, z + dz, "air");
                }
            }
            doors(X1, 0, z, Doors.STEEL, "west");
        }
        for (int x = X1 + 3; x <= X1 + 4; x++) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, az1, "air");
            }
        }
        doors(X1 + 3, 0, az1, Doors.SHOP, "south");
        // the workshop: benches down the outer wall, the tool chests, the tyre racks, the spare parts
        for (int z = az0 + 2; z < sz - 1; z++) {
            int k = Math.floorMod(z - az0, 7);
            String s = k < 3 ? "home_workbench[facing=west]" : k == 3 ? "pk_tool_chest[facing=west]" : k == 4 ? "gs_tirerack[facing=west]"
                    : k == 5 ? "fit_tyres" : "navy_toolbox[facing=west]";
            set(AX1 - 1, 1, z, s);
            if (k < 3 && chance() < 0.5) {
                set(AX1 - 1, 2, z, "fs_tools_" + (chance() < 0.5 ? "a" : "b") + "[facing=west]");
            }
        }
        for (int z = az0 + 3; z < sz - 3; z += 9) {
            set(X1 + 5, 1, z, "ah_engine_0[facing=north]");
            set(X1 + 5, 1, z + 1, "ah_engine_1[facing=north]");
        }
        for (int z = az0 + 2; z < sz - 1; z += 4) {
            set(X1 + 9, 1, z, any("pw_drum", "barrel", "pk_crate_1", "pk_crate_2"));
        }
        set(X1 + 2, 1, az0 + 1, "gs_compressor_lo[facing=south]");
        set(X1 + 2, 2, az0 + 1, "gs_compressor_hi[facing=south]");
        // upstairs: the offices, the windows onto the hangar floor
        int y = AS2;
        for (int z = az0 + 2; z < sz - 1; z++) {
            if (Math.floorMod(z - az0, 4) != 0) {
                set(X1, y + 2, z, "wf_glass[facing=west]");
                set(X1, y + 3, z, "wf_glass[facing=west]");
            }
        }
        for (int z = az0 + 9; z < sz - 1; z += 9) {
            partX(X1 + 1, AX1 - 1, z, y, AROOF - AS2, "drywall", "drywall");
            set(X1 + 6, y + 1, z, "air");
            set(X1 + 6, y + 2, z, "air");
            door(X1 + 6, y, z, Doors.FLAT, "north");
        }
        for (int z = az0 + 3; z < sz - 2; z += 9) {
            set(X1 + 2, y + 1, z, "home_desk_pc[facing=west]");
            set(X1 + 3, y + 1, z, (chance() < 0.2 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=west]");
            set(AX1 - 1, y + 1, z + 1, "filing_cabinet[facing=west]");
            set(AX1 - 1, y + 1, z + 2, "filing_cabinet[facing=west]");
            set(X1 + 8, y + 2, z + 5, "of_whiteboard[facing=north]");
            set(X1 + 4, y + 1, z + 4, "store_table");
        }
        lamps(X1 + 1, az0 + 1, AX1 - 1, az1 - 1, AS2, 0.3);
        lamps(X1 + 1, az0 + 1, AX1 - 1, az1 - 1, AROOF, 0.3);
        mess(X1 + 1, az0 + 1, AX1 - 1, az1 - 1, 0, 0.03);
        mess(X1 + 1, az0 + 1, AX1 - 1, az1 - 1, AS2, 0.03);
    }

    /**
     * The trijet in for its check, nose in, the line down the floor it was towed in on; the stair
     * truck at L1, the tail in its scaffold dock, the tug, the power cart, the chocks and the cones,
     * the spare engine, the benches down the west wall.
     */
    private void maintenance() {
        int cx = (X0 + X1) / 2, nz = Z0 + 8;
        for (int z = nz - 3; z < Z1; z++) {
            set(cx, 0, z, "af_paint_y");
        }
        Airliner.place(p, cx, 0, nz);
        // the stair truck up to L1 on the west side, rising east to its platform at the sill
        int lz = nz + Airliner.L1, px = cx + Airliner.WEST - 1, fy = Airliner.FLOOR - 1;
        for (int dz = 0; dz <= 1; dz++) {
            set(px, fy, lz + dz, "rp_as_top[facing=east]");
            set(px - 1, fy, lz + dz, "rp_as_top[facing=east]");
            set(px - 1, fy + 1, lz + dz, "railing");
            for (int yy = 1; yy < fy; yy++) {
                set(px, yy, lz + dz, yy == 1 ? "rp_as_cab_lo[facing=west]" : yy == 2 ? "rp_as_cab_hi[facing=west]" : "rp_as_lift[facing=east]");
                set(px - 1, yy, lz + dz, yy == 1 ? "rp_as_bed_wheels[facing=east]" : "rp_as_lift[facing=east]");
            }
            for (int k = 1; k < fy; k++) {
                int x = px - 1 - k;
                set(x, fy - k, lz + dz, "rp_as_step[facing=east]");
                for (int yy = 1; yy < fy - k; yy++) {
                    set(x, yy, lz + dz, yy == 1 ? "rp_as_bed[facing=east]" : "rp_as_lift[facing=east]");
                }
            }
        }
        // the tail dock: scaffold round the tail cone and up the fin, decks every six, the rails on top
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = nz + Airliner.LENGTH - 3; z <= nz + Airliner.LENGTH + 3; z++) {
                boolean edge = x == cx - 6 || x == cx + 6 || z == nz + Airliner.LENGTH + 3;
                for (int y = 1; y <= 19; y++) {
                    if (!empty(x, y, z)) {
                        continue;
                    }
                    boolean deck = y % 6 == 0;
                    if (edge || deck && Math.abs(x - cx) >= 2) {
                        set(x, y, z, deck ? "ah_scaf_deck" : "ah_scaf");
                        if (deck && edge && empty(x, y + 1, z)) {
                            set(x, y + 1, z, "ah_scaf_rail[facing=" + (z == nz + Airliner.LENGTH + 3 ? "south" : x < cx ? "west" : "east") + "]");
                        }
                    }
                }
            }
        }
        for (int y = 1; y <= 18; y++) {
            set(cx - 7, y, nz + Airliner.LENGTH, "ladder[facing=east]");
        }
        // the spare engine on its stand off the right wing, the tug at the nose, the power cart, the cones
        set(cx + 18, 1, nz + 46, "ah_engine_0[facing=north]");
        set(cx + 18, 1, nz + 47, "ah_engine_1[facing=north]");
        set(cx + 16, 1, nz + 45, "trade_stepladder[facing=east]");
        set(cx, 1, nz - 3, "rp_tug_front[facing=north]");
        set(cx, 1, nz - 2, "rp_tug_rear[facing=north]");
        set(cx + 4, 1, nz + 2, "rp_gpu[facing=west]");
        set(cx - 1, 1, nz + 9, "rp_chocks[facing=east]");
        for (int[] c : new int[][] {{-28, 30}, {28, 30}, {0, 62}, {-10, 2}, {10, 2}}) {
            if (empty(cx + c[0], 1, nz + c[1])) {
                set(cx + c[0], 1, nz + c[1], "st_cone");
            }
        }
        // the benches and the cabinets down the west wall, the drums, the parts
        for (int z = Z0 + 4; z < Z1 - 4; z++) {
            int k = Math.floorMod(z - Z0, 10);
            String s = k < 3 ? "home_workbench[facing=east]" : k == 3 ? "pk_tool_chest[facing=east]" : k == 4 ? "navy_toolbox[facing=east]"
                    : k < 7 ? "pw_drum" : k == 7 ? "fit_tyres" : null;
            if (s != null) {
                set(X0 + 1, 1, z, s);
            }
        }
        for (int x = X0 + 6; x < X1 - 6; x += 7) {
            set(x, 1, Z0 + 1, any("pk_crate_1", "pk_crate_2", "crate", "pw_drum"));
            set(x + 1, 1, Z0 + 1, any("pk_crate_1", "crate"));
            if (chance() < 0.5) {
                set(x, 2, Z0 + 1, "pk_crate_2");
            }
        }
        mess(X0 + 1, Z0 + 1, X1 - 1, Z1 - 1, 0, 0.006);
    }

    // ------------------------------------------------------------------ hangar 2

    /** The relief depot: the pallets in rows, the tents, the cots, the forklift's work half done. */
    private void depot() {
        int x0 = H2X0 + 3, x1 = H2X1 - 3;
        String[] loads = {"st_pallet_water", "st_pallet_water_half", "st_pallet_meals", "st_pallet_cots"};
        for (int z = H2Z0 + 3; z < H2Z0 + 24; z += 4) {
            for (int x = x0; x <= x1; x++) {
                if (Math.floorMod(x - x0, 9) == 8 || chance() < 0.12) {
                    continue;
                }
                String l = loads[Math.floorMod((x - x0) / 9 + z, loads.length)];
                set(x, 1, z, l);
                set(x, 1, z + 1, l);
                if (chance() < 0.45) {
                    set(x, 2, z, "cardboard_box[facing=" + dir() + "]");
                }
            }
        }
        tent(H2X0 + 6, H2Z0 + 28, 10, "triage");
        tent(H2X0 + 20, H2Z0 + 28, 10, "shelter");
        tent(H2X0 + 34, H2Z0 + 28, 8, "supply");
        for (int x = H2X0 + 46; x <= H2X1 - 3; x += 2) {
            for (int z = H2Z0 + 28; z <= H2Z0 + 44; z += 3) {
                if (chance() < 0.7) {
                    set(x, 1, z, "ch_cot_head[facing=north]");
                    set(x, 1, z + 1, "ch_cot_foot[facing=north]");
                }
            }
        }
        for (int x = H2X0 + 4; x < H2X1 - 3; x += 5) {
            set(x, 1, H2Z1 - 3, any("st_cone", "trash_bag[facing=" + dir() + "]", "cardboard_box[facing=north]"));
        }
        p.vehicle(H2X0 + 12.5, 1, H2Z1 + 8, 90f, "box_truck,moving_truck", rng.nextLong());
        mess(H2X0 + 1, H2Z0 + 1, H2X1 - 1, H2Z1 - 1, 0, 0.01);
    }

    // ------------------------------------------------------------------ the T-hangars

    /** Eight units in a row under one roof, each with its sectional door; the light planes long flown out. */
    private void tHangars() {
        int x1 = TX0 + UNITS * TW;
        for (int x = TX0; x <= x1; x++) {
            for (int z = TZ0; z <= TZ1; z++) {
                boolean wall = x == TX0 || x == x1 || z == TZ0 || z == TZ1 || Math.floorMod(x - TX0, TW) == 0;
                set(x, 0, z, wall ? "concrete_dark" : "floor_concrete");
                for (int y = 1; y < TROOF; y++) {
                    set(x, y, z, wall ? clad(y) : "air");
                }
                set(x, TROOF, z, "un_roof");
            }
        }
        b.flat(TX0, TZ0, x1, TZ1, TROOF, "corrugated_grey", 0, null);
        parapet(TX0, TZ0, x1, TZ1, TROOF);
        for (int u = 0; u < UNITS; u++) {
            int ux = TX0 + u * TW + 1;
            boolean up = chance() < 0.3;
            for (int x = ux; x < ux + TW - 1; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, TZ1, up ? (y == 4 ? "ah_sect_head[facing=south]" : "air") : "ah_sect_white" + (y == 3 ? "_glass" : "") + "[facing=south]");
                }
                set(x, 0, TZ1, "floor_concrete");
            }
            // inside: a bench and the owner's things, a car or a boat left in some
            set(ux, 1, TZ0 + 1, "home_workbench[facing=south]");
            set(ux + 1, 1, TZ0 + 1, any("pk_tool_chest[facing=south]", "gs_tirerack[facing=south]", "pw_drum"));
            set(ux + TW - 2, 1, TZ0 + 1, any("barrel", "pk_crate_1", "fit_tyres"));
            if (up && chance() < 0.6) {
                p.vehicle(ux + TW / 2.0 - 0.5, 1, TZ0 + 6.5, 180f, "pickup,sedan,convertible,boat_skiff", rng.nextLong());
            }
            set(ux + TW / 2, TROOF, (TZ0 + TZ1) / 2, power && chance() < 0.3 ? "un_roof_lamp_on" : "un_roof_lamp");
        }
    }

    /** The ramp: concrete in five-metre slabs from the doors down to the taxiway, the lines the tugs followed. */
    private void ramp() {
        for (int x = BX0; x <= BX1; x++) {
            for (int z = Z0 - 4; z <= BZ1; z++) {
                boolean jx = Math.floorMod(x, 5) == 0, jz = Math.floorMod(z, 5) == 0;
                set(x, 0, z, jx && jz ? "af_apron_jj[facing=north]" : jx ? "af_apron_j[facing=north]" : jz ? "af_apron_j[facing=east]" : "af_apron");
            }
        }
        for (int z = Z1 + 2; z <= BZ1; z++) {
            set((X0 + X1) / 2, 0, z, "af_paint_y");
            set((H2X0 + H2X1) / 2, 0, z, "af_paint_y");
        }
        for (int x = (X0 + X1) / 2; x <= BX1; x++) {
            set(x, 0, BZ1 - 6, "af_paint_y");
        }
    }

    // ------------------------------------------------------------------ the jet centre

    /**
     * The private jet centre (the Air Guard's ramp until its fighters went to Caldwell Army Airfield):
     * the two hangars, the lounge, the line on the apron, the helipads.
     */
    private void guard() {
        for (int k = 0; k < 2; k++) {
            int x0 = k == 0 ? G3 : G4, x1 = x0 + GW - 1;
            shell(x0, x1, GZ0, GZ1, GDOOR, GROOF);
            pocketDoors(x0 + 4, GZ1, GDOOR, 3 + k);
            // a business jet nose out to the door, its stair let down, the power cart behind its tail
            Airliner.Plane jet = Airliner.plane(k == 0 ? "bizjet_private" : "bizjet_charter");
            Placer q = p.turned(x0 + GW / 2, 0, GZ1 - 4, 2);
            jet.place(q, 0, 0, 0);
            AirportGates.stairTruck(q, jet.skinW() - 1, jet.floor() - 1, jet.west()[0]);
            q.set(0, 1, 4, "rp_chocks[facing=east]");
            set(x0 + GW / 2 + 3, 1, GZ0 + 2, "rp_gpu[facing=south]");
            for (int z = GZ0 + 3; z < GZ1 - 3; z += 3) {
                set(x0 + 1, 1, z, any("pk_tool_chest[facing=east]", "navy_toolbox[facing=east]", "pw_drum", "tire_stack"));
                set(x1 - 1, 1, z, any("pk_tool_chest[facing=west]", "tire_stack", "pw_drum", "home_workbench[facing=west]"));
            }
            set(x0 + GW / 2, 1, GZ0 + 2, "trade_stepladder[facing=south]");
            for (int x = x0 + 4; x <= x1 - 4; x++) {
                set(x, 0, GZ1 - 2, "af_paint_y");
            }
            // the lead-out line down to the taxiway
            for (int z = GZ1 + 3; z <= BZ1; z++) {
                set(x0 + GW / 2, 0, z, "af_paint_y");
            }
        }
        ops();
        // the line on the apron, east of the hangars' way out: two jets nose to the taxiway, their stairs
        // down, chocks and cones left about them
        for (int j = 0; j < 2; j++) {
            int jx = LINE_X + 20 + j * 50;
            Airliner.Plane jet = Airliner.plane(j == 0 ? "bizjet_charter" : "bizjet_private");
            Placer q = p.turned(jx, 0, LINE_Z + 18, 2);
            jet.place(q, 0, 0, 0);
            AirportGates.stairTruck(q, jet.skinW() - 1, jet.floor() - 1, jet.west()[0]);
            set(jx, 1, LINE_Z + 13, "rp_chocks[facing=east]");
            // the crew ladder hooked on by the cockpit, the power cart off the nose
            set(jx + 5, 1, LINE_Z + 21, "rp_gpu[facing=west]");
            for (int s = -1; s <= 1; s += 2) {
                if (chance() < 0.7) {
                    set(jx + s * 19, 1, LINE_Z + 4, "st_cone");
                }
            }
            // the parking box painted round each
            for (int x = jx - 22; x <= jx + 22; x++) {
                set(x, 0, LINE_Z - 18, "af_paint_y");
            }
            for (int z = LINE_Z - 18; z <= LINE_Z + 20; z++) {
                set(jx - 22, 0, z, "af_paint_y");
                if (j == 1) {
                    set(jx + 22, 0, z, "af_paint_y");
                }
            }
        }
        // the helipads: the yellow ring, the white H
        for (int i = 0; i < PADS.length; i++) {
            int cx = PADS[i][0], cz = PADS[i][1];
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    double r = Math.hypot(dx, dz);
                    if (r >= 6.5 && r < 7.5) {
                        set(cx + dx, 0, cz + dz, "af_paint_y");
                    }
                    boolean h = (Math.abs(dx) == 3 && Math.abs(dz) <= 3) || (dz == 0 && Math.abs(dx) <= 3);
                    if (h) {
                        set(cx + dx, 0, cz + dz, "af_paint");
                    }
                }
            }
        }
        // the bowser's stand by the ops building: the hose reels, the drums, the extinguisher trolleys
        for (int x = OX1 + 4; x <= OX1 + 10; x += 3) {
            set(x, 1, OZ1 + 3, "navy_hose_reel[facing=south]");
            set(x + 1, 1, OZ1 + 3, "pw_drum");
        }
        p.vehicle(OX1 + 8.0, 1, OZ1 + 10.0, 90f, "limousine,suv,sedan_ltd", rng.nextLong());
        p.vehicle(OX0 - 4.0, 1, OZ1 + 8.0, 180f, "suv,sedan_ltd,minivan", rng.nextLong());
    }

    /**
     * A hangar door from x0 (the opening GW - 8 wide): four leaves run right back into the pockets
     * either side, on the two tracks outside the wall, so the whole opening is clear for a jet; the
     * hangar's number on the outer leaf each side, the rails out to the pockets.
     */
    private void pocketDoors(int x0, int z, int h, int num) {
        int w = GW - 8;
        for (int x = x0 - 8; x < x0 + w + 8; x++) {
            set(x, 0, z + 1, "ah_track[axis=x]");
            set(x, 0, z + 2, "ah_track[axis=x]");
            // the hood over the top tracks, in the civil hangars' blue
            set(x, h + 1, z + 1, "corrugated_blue_b");
            set(x, h + 1, z + 2, "corrugated_blue_b");
        }
        for (int side = 0; side < 2; side++) {
            int lx = side == 0 ? x0 - 8 : x0 + w;
            for (int track = 0; track < 2; track++) {
                int lz = z + 1 + track;
                for (int x = lx; x < lx + 8; x++) {
                    for (int y = 1; y <= h; y++) {
                        set(x, y, lz, (y == 1 ? "ah_leaf_band" : x == lx + 7 ? "ah_leaf_edge" : "ah_leaf") + "[facing=south]");
                    }
                }
                if (track == 1) {
                    picture("ah_leaf_num_" + num + "_", 3, 3, lx + 2, h - 6, lz, "south");
                }
            }
        }
    }

    /** The jet centre's building: the passengers' lounge with its sofas and coffee bar, the crew room, the front desk. */
    private void ops() {
        for (int x = OX0; x <= OX1; x++) {
            for (int z = OZ0; z <= OZ1; z++) {
                boolean wall = x == OX0 || x == OX1 || z == OZ0 || z == OZ1;
                set(x, 0, z, wall ? "concrete_dark" : x < OX0 + 18 ? "home_floor_carpet_blue" : "un_slab_vct");
                for (int y = 1; y < 5; y++) {
                    String out = z == OZ1 ? "south" : z == OZ0 ? "north" : x == OX0 ? "west" : "east";
                    boolean win = wall && (y == 2 || y == 3) && Math.floorMod(z == OZ0 || z == OZ1 ? x : z, 4) == 2;
                    set(x, y, z, !wall ? "air" : win ? (chance() < 0.15 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]"
                            : y == 4 ? "ml_precast_band" : "ml_precast");
                }
                set(x, 5, z, "un_roof");
            }
        }
        b.flat(OX0, OZ0, OX1, OZ1, 5, "ml_precast", 1, "ml_precast");
        partZ(OX0 + 18, OZ0 + 1, OZ1 - 1, 0, 5, "drywall", "drywall");
        set(OX0 + 18, 1, OZ1 - 4, "air");
        set(OX0 + 18, 2, OZ1 - 4, "air");
        door(OX0 + 18, 0, OZ1 - 4, Doors.FLAT, "east");
        for (int x = OX0 + 23; x <= OX0 + 24; x++) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, OZ1, "air");
            }
        }
        doors(OX0 + 23, 0, OZ1, Doors.SHOP, "south");
        // the lounge: sofas round low tables, the coffee bar along the back, the screen with the departures
        for (int gx : new int[] {OX0 + 3, OX0 + 10}) {
            for (int gz : new int[] {OZ0 + 5, OZ0 + 11}) {
                String c = any("brown", "blue");
                set(gx, 1, gz - 1, "home_sofa_" + c + "_l[facing=south]");
                set(gx + 1, 1, gz - 1, "home_sofa_" + c + "_m[facing=south]");
                set(gx + 2, 1, gz - 1, "home_sofa_" + c + "_r[facing=south]");
                set(gx + 1, 1, gz, any("home_coffee_table", "pk_coffee_table", "pk_coffee_table_round"));
                set(gx, 1, gz + 1, "home_armchair_" + c + "[facing=north]");
                set(gx + 2, 1, gz + 1, chance() < 0.15 ? "air" : "home_armchair_" + c + "[facing=north]");
            }
        }
        for (int x = OX0 + 2; x <= OX0 + 14; x++) {
            set(x, 1, OZ0 + 1, x % 4 == 0 ? "home_coffee_maker[facing=south]" : "store_counter[facing=south]");
        }
        set(OX0 + 16, 1, OZ0 + 1, "trade_water_cooler[facing=south]");
        set(OX0 + 1, 1, OZ0 + 2, "store_vending[facing=east]");
        set(OX0 + 1, 2, OZ0 + 2, "store_vending_top[facing=east]");
        set(OX0 + 8, 3, OZ1 - 1, "home_tv[facing=north]");
        set(OX0 + 14, 1, OZ1 - 2, "reception_desk[facing=north]");
        set(OX0 + 14, 1, OZ1 - 3, "office_chair[facing=north]");
        // the crew room: the pilots' lockers, the flight planning desk, the radio
        for (int x = OX0 + 20; x <= OX1 - 2; x++) {
            set(x, 1, OZ0 + 1, "fs_locker[facing=south]");
            set(x, 2, OZ0 + 1, "fs_locker_top[facing=south]");
        }
        set(OX1 - 2, 1, OZ1 - 3, "home_desk_pc[facing=west]");
        set(OX1 - 3, 1, OZ1 - 3, "office_chair[facing=west]");
        set(OX1 - 1, 1, OZ1 - 6, "bunk_radio_desk[facing=west]");
        set(OX0 + 20, 1, OZ1 - 2, "bunk_map_table");
        lamps(OX0, OZ0, OX1, OZ1, 5, 0.3);
        mess(OX0 + 1, OZ0 + 1, OX1 - 1, OZ1 - 1, 0, 0.03);
        // the flag at the door
        set(OX0 + 21, 1, OZ1 + 3, "cv_flagpole_base");
        for (int y = 2; y <= 9; y++) {
            set(OX0 + 21, y, OZ1 + 3, "cv_flagpole");
        }
        set(OX0 + 21, 10, OZ1 + 3, "cv_flagpole_top[facing=south]");
    }
}
