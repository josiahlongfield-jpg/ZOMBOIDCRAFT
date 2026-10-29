package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.CivicCentre.CHX;
import static com.zomboidcraft.world.county.CivicCentre.CHZ;
import static com.zomboidcraft.world.county.CivicCentre.CHR;
import static com.zomboidcraft.world.county.CivicCentre.CON_X0;
import static com.zomboidcraft.world.county.CivicCentre.CON_X1;
import static com.zomboidcraft.world.county.CivicCentre.CON_Z0;
import static com.zomboidcraft.world.county.CivicCentre.CON_Z1;

/**
 * The council chamber: a drum of precast beside the tower under its stepped saucer roof, the
 * glass-block ring of clerestory windows round the top, the bronze name over its portal on the
 * plaza. Inside, the oak wainscot round the wall, the public's pews facing the dais behind the
 * bar, the podium for the public in the aisle; the council's bench across the dais under the
 * city seal, the vote boards either side still lit with the quarantine ordinance (carried seven
 * to two), the flags; the clerk's tables below, the press table at the side, past mayors round
 * the walls. In some, the relief station's overflow slept in the pews.
 *
 * <p>The glass concourse joins it to the tower's lobby, its own doors on the plaza.
 */
final class CivicChamber {
    /** The drum: wall out to CHR + 0.5, the wainscot inside it, the room inside that. */
    private static final double WALL = CHR - 0.5, OAK = CHR - 1.5;
    /** The wall's height, the dais's front row. */
    private static final int TOP = 10, DAIS = CHZ + 5;

    private final CivicCentre c;
    private final boolean shelter;

    CivicChamber(CivicCentre c) {
        this.c = c;
        this.shelter = c.chance() < 0.5;
    }

    void build() {
        drum();
        roof();
        portal();
        dais();
        seats();
        concourse();
    }

    private double r(int x, int z) {
        return Math.hypot(x - CHX, z - CHZ);
    }

    private void drum() {
        for (int x = CHX - CHR - 1; x <= CHX + CHR + 1; x++) {
            for (int z = CHZ - CHR - 1; z <= CHZ + CHR + 1; z++) {
                double r = r(x, z);
                if (r > CHR + 0.5) {
                    continue;
                }
                c.set(x, 0, z, r < OAK ? "ch_floor_oak" : "tx_terrazzo");
                for (int y = 1; y <= TOP; y++) {
                    c.set(x, y, z, y == 1 && r < OAK ? "cv_carpet_red" : "air");
                }
                if (r >= WALL) {
                    // the clerestory: glass block every third round the ring, under the cornice
                    double a = Math.atan2(z - CHZ, x - CHX);
                    boolean light = Math.floorMod((int) Math.round(a / (Math.PI * 2) * 48), 3) != 0;
                    for (int y = 1; y <= TOP; y++) {
                        String b = y >= 7 && y <= 8 && light ? "dn_glassblock" : y == TOP || y == 1 ? "cc_precast_b" : "cc_precast";
                        c.set(x, y, z, b);
                    }
                } else if (r >= OAK) {
                    for (int y = 1; y <= 3; y++) {
                        c.set(x, y, z, y == 3 ? "cv_wainscot" : "cv_panel_oak");
                    }
                }
            }
        }
        // the doors: north to the portal, east to the concourse, the fire exit west
        c.doors(CHX - 1, 0, CHZ - CHR, "ext_door_oak", "north");
        clear(CHX - 1, CHZ - CHR + 1, CHX, CHZ - CHR + 1);
        int ex = (int) Math.floor(CHX + Math.sqrt(CHR * CHR - 49.0));
        for (int z = 76; z <= 77; z++) {
            for (int x = ex - 3; x <= ex + 2; x++) {
                if (r(x, z) >= OAK && r(x, z) <= CHR + 0.5) {
                    for (int y = 1; y <= 3; y++) {
                        c.set(x, y, z, "air");
                    }
                    c.set(x, 0, z, "tx_terrazzo");
                }
            }
        }
        c.doors(outer(76, true), 0, 76, "ext_door_oak", "east");
        int wx = CHX - CHR;
        c.door(wx, 0, CHZ, "fire_door", "west");
        clear(wx + 1, CHZ, wx + 1, CHZ);
        c.set(wx + 2, 3, CHZ + 1, "cv_exit_red[facing=east]");
        // past mayors round the north half, over the wainscot
        for (int k = 0; k < 8; k++) {
            double a = Math.PI + Math.PI * (k + 0.5) / 8;
            int x = (int) Math.round(CHX + Math.cos(a) * (OAK + 0.4)), z = (int) Math.round(CHZ + Math.sin(a) * (OAK + 0.4));
            if (Math.abs(x - CHX) < 3 || !c.empty(x, 5, z)) {
                continue;
            }
            String f = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) < 0 ? "east" : "west") : "south";
            c.set(x, 5, z, (k % 2 == 0 ? "cv_portrait" : "cv_portrait_b") + "[facing=" + f + "]");
        }
    }

    /** The outermost wall cell east (or west) of the middle in row z. */
    private int outer(int z, boolean east) {
        int best = CHX;
        for (int x = CHX; east ? x <= CHX + CHR + 1 : x >= CHX - CHR - 1; x += east ? 1 : -1) {
            if (r(x, z) <= CHR + 0.5) {
                best = x;
            }
        }
        return best;
    }

    private void clear(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int y = 1; y <= 3; y++) {
                    c.set(x, y, z, "air");
                }
                c.set(x, 0, z, "tx_terrazzo");
            }
        }
    }

    /** The saucer: rings stepping up and in over the room, the oculus of glass in the middle; pendants on chains. */
    private void roof() {
        double[] rings = {CHR + 0.5, 11.5, 7.5, 3.5};
        for (int x = CHX - CHR - 1; x <= CHX + CHR + 1; x++) {
            for (int z = CHZ - CHR - 1; z <= CHZ + CHR + 1; z++) {
                double r = r(x, z);
                for (int i = 0; i < rings.length; i++) {
                    double outerR = rings[i], innerR = i + 1 < rings.length ? rings[i + 1] : -1;
                    int y = TOP + 1 + i;
                    if (r <= outerR && r > innerR) {
                        c.set(x, y, z, i == rings.length - 1 ? (c.chance() < 0.15 ? "us_skylight_broken" : "us_skylight")
                                : i == 0 && r >= WALL ? "cc_precast_b" : "cc_precast");
                    }
                }
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = (int) Math.round(CHX + Math.cos(a) * 9.5), z = (int) Math.round(CHZ + Math.sin(a) * 9.5);
            c.set(x, TOP + 1, z, c.power && c.chance() < 0.5 ? "ch_pendant_lit" : "ch_pendant");
        }
        // the drains and a rooftop vent
        c.set(CHX + 9, TOP + 3, CHZ + 4, "hvac");
    }

    /** The portal on the plaza: precast piers, the lintel with COUNCIL CHAMBER on it, the lamps. */
    private void portal() {
        int z0 = CHZ - CHR - 3, z1 = CHZ - CHR - 1, x0 = CHX - 5, x1 = CHX + 4;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                c.set(x, 0, z, "cc_plaza_band");
                boolean pierCell = x == x0 || x == x1;
                for (int y = 1; y <= 6; y++) {
                    if (pierCell || y == 6) {
                        if (r(x, z) > CHR + 0.5) {
                            c.set(x, y, z, y == 6 ? "cc_precast_b" : "cc_precast");
                        }
                    }
                }
            }
        }
        c.row("cc_name_council_", 10, x0, 6, z0 - 1, "north", 0.03);
        c.set(x0, 4, z0 - 1, (c.power ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        c.set(x1, 4, z0 - 1, (c.power ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
    }

    /**
     * The dais across the south of the room, one up, the steps in the middle; the bench on it facing
     * the room, the chairs behind; the chord wall behind them with the seal, the flags and the two
     * vote boards; the clerk's tables below the steps.
     */
    private void dais() {
        int y = 1;
        for (int x = CHX - CHR; x <= CHX + CHR; x++) {
            for (int z = DAIS - 1; z <= CHZ + CHR; z++) {
                if (r(x, z) >= OAK) {
                    continue;
                }
                if (z == DAIS - 1) {
                    if (Math.abs(x - CHX) <= 3) {
                        c.set(x, y, z, "ch_step_oak[facing=south]");
                    }
                } else {
                    c.set(x, y, z, "ch_floor_oak");
                }
            }
        }
        // the chord wall the bench backs onto, solid behind it to the drum
        int wz = CHZ + 12;
        for (int x = CHX - CHR; x <= CHX + CHR; x++) {
            for (int z = wz; z <= CHZ + CHR; z++) {
                if (r(x, z) < WALL) {
                    for (int yy = 2; yy <= TOP; yy++) {
                        c.set(x, yy, z, z == wz && yy <= 4 ? "cv_panel_oak" : "cc_travertine");
                    }
                }
            }
        }
        c.picture("cc_seal_", 3, 3, CHX - 1, 6, wz - 1, "north");
        c.picture("cc_vote_", 4, 2, CHX - 6, 5, wz - 1, "north");
        c.picture("cc_vote_", 4, 2, CHX + 3, 5, wz - 1, "north");
        c.set(CHX - 2, 2, wz - 1, "cv_flag_us[facing=north]");
        c.set(CHX - 2, 3, wz - 1, "cv_flag_us_top[facing=north]");
        c.set(CHX + 2, 2, wz - 1, "cv_flag_state[facing=north]");
        c.set(CHX + 2, 3, wz - 1, "cv_flag_state_top[facing=north]");
        c.set(CHX + 3, 2, wz - 1, "cc_flag_city[facing=north]");
        c.set(CHX + 3, 3, wz - 1, "cc_flag_city_top[facing=north]");
        // the council's bench in a row, the mayor's chair in the middle
        int bz = DAIS + 2, bx0 = CHX - 8, bx1 = CHX + 8;
        for (int x = bx0; x <= bx1; x++) {
            if (r(x, bz) >= OAK - 1) {
                continue;
            }
            String piece = x == bx0 ? "th_bench_end_l" : x == bx1 ? "th_bench_end" : c.chance() < 0.75 ? "th_bench" : "th_bench_plain";
            c.set(x, y + 1, bz, piece + "[facing=north]");
            double q = c.chance();
            c.set(x, y + 1, bz + 1, x == CHX ? "pk_armchair[facing=north]" : q < 0.75 ? "office_chair[facing=north]"
                    : q < 0.85 ? "office_chair_fallen[facing=" + c.dir() + "]" : "air");
        }
        // the clerk's and the attorney's tables below, either side of the steps; the press at the west side
        for (int x : new int[] {CHX - 7, CHX - 6, CHX - 5, CHX + 5, CHX + 6, CHX + 7}) {
            c.set(x, 1, DAIS - 3, "desk[facing=north]");
            if (c.chance() < 0.7) {
                c.set(x, 1, DAIS - 2, "office_chair[facing=north]");
            }
        }
        for (int z = CHZ - 2; z <= CHZ; z++) {
            c.set(CHX - 11, 1, z, "desk[facing=east]");
            if (c.chance() < 0.6) {
                c.set(CHX - 12, 1, z, "pk_chair_wooden[facing=east]");
            }
        }
        c.set(CHX, 1, CHZ - 4, "th_podium[facing=south]");
        for (int i = 0; i < 26; i++) {
            int x = CHX - 9 + c.rng.nextInt(19), z = DAIS + c.rng.nextInt(6);
            if (c.empty(x, 2, z) && "ch_floor_oak".equals(c.get(x, 1, z))) {
                c.set(x, 2, z, c.any("paper", "paper", "home_books_stack_low", "blood_splat"));
            }
        }
    }

    /**
     * The public's pews in rows facing the dais, the centre aisle, the bar across in front of them;
     * in a shelter the front rows gone for cots and the stores stacked by the west wall.
     */
    private void seats() {
        int bar = CHZ - 3;
        for (int x = CHX - CHR; x <= CHX + CHR; x++) {
            if (r(x, bar) >= OAK - 1 || Math.abs(x - CHX) <= 1) {
                continue;
            }
            boolean post = Math.abs(x - CHX) == 2 || r(x + 1, bar) >= OAK - 1 || r(x - 1, bar) >= OAK - 1;
            c.set(x, 1, bar, post ? "cv_bar_post" : "cv_bar_rail[axis=x]");
        }
        for (int z = bar - 2; z >= CHZ - CHR + 3; z -= 2) {
            boolean cots = shelter && z >= bar - 6;
            int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
            for (int x = CHX - CHR; x <= CHX + CHR; x++) {
                if (r(x, z) < OAK - 1.5 && Math.abs(x - CHX) > 1) {
                    lo = Math.min(lo, x);
                    hi = Math.max(hi, x);
                }
            }
            for (int x = lo; x <= hi; x++) {
                if (Math.abs(x - CHX) <= 1 || r(x, z) >= OAK - 1.5) {
                    continue;
                }
                if (cots) {
                    if ((x - lo) % 2 == 0 && c.chance() < 0.8) {
                        c.set(x, 1, z, "ch_cot_head[facing=south]");
                        c.set(x, 1, z + 1, "ch_cot_foot[facing=south]");
                    }
                    continue;
                }
                boolean left = x == lo || x == CHX + 2, right = x == hi || x == CHX - 2;
                c.set(x, 1, z, (left ? "cv_pew_end_l" : right ? "cv_pew_end" : "cv_pew") + "[facing=south]");
            }
        }
        if (shelter) {
            for (int z = CHZ - 8; z <= CHZ + 2; z += 2) {
                int x = (int) Math.ceil(CHX - Math.sqrt(OAK * OAK - (z - CHZ) * (z - CHZ))) + 1;
                c.set(x, 1, z, c.any("bunk_drum_water", "bunk_crate_ration[facing=east]", "st_pallet_water_half", "cardboard_box[facing=east]"));
            }
        }
        c.mess(CHX - CHR + 2, CHZ - CHR + 2, CHX + CHR - 2, CHZ + CHR - 2, 0, 0.05);
    }

    /** The glass concourse from the tower's lobby to the chamber's east door, its own doors on the plaza. */
    private void concourse() {
        for (int x = CON_X0; x <= CON_X1; x++) {
            for (int z = CON_Z0; z <= CON_Z1; z++) {
                if (r(x, z) <= CHR + 0.5) {
                    continue;
                }
                boolean wall = z == CON_Z0 || z == CON_Z1;
                c.set(x, 0, z, wall ? "cc_plaza_band" : "tx_terrazzo");
                for (int y = 1; y <= 4; y++) {
                    c.set(x, y, z, !wall ? "air" : y == 4 ? "cc_spandrel" : c.chance() < 0.1 ? "tx_glass_broken[axis=x]" : "tx_glass[axis=x]");
                }
                c.set(x, 5, z, wall ? "cc_precast" : (x % 3 == 0 ? (c.power && c.chance() < 0.4 ? "un_roof_lamp_on" : "un_roof_lamp") : "un_roof"));
            }
        }
        c.doors(60, 0, CON_Z0, "shop_door", "north");
        // benches along the south glass, the sign by the chamber's doors, the relief overflow's litter
        for (int x = 54; x <= 68; x += 4) {
            c.set(x, 1, CON_Z1 - 1, "cv_hall_bench[facing=north]");
        }
        c.sign("council", 52, 3, CON_Z1 - 1, "north");
        for (int x = 56; x <= 66; x += 5) {
            c.set(x, 2, CON_Z1 - 1, "st_poster_missing[facing=north]");
        }
        c.mess(CON_X0, CON_Z0 + 1, CON_X1, CON_Z1 - 1, 0, 0.12);
    }
}
