package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven International's buildings round the terminal, in the airport's frame ({@link Airport}):
 * built whole once, like the terminal, and copied tile by tile.
 *
 * <ul>
 * <li>The control tower east of the terminal ({@link #TX}, {@link #TZ}), after the round towers of
 * Dulles and of the Terminal map's skyline (refs/airport/control-tower): a pale concrete shaft with
 * the stair winding up inside it round its core, the bell flaring out under the cab, the dark band,
 * the cab glazed all round with its consoles facing out over the field, the catwalk round it, the
 * roof's radar dome and masts; the base building at its foot with the lobby and the offices.</li>
 * </ul>
 */
final class AirportGrounds extends Grounds {
    /** The tower shaft's middle (a block's centre is TX + 0.5). */
    static final int TX = 200, TZ = -372;
    /** The shaft's outside radius; the cab's floor, its glazing's radius, its roof. */
    static final double SHAFT_R = 5.5, CAB_R = 9;
    static final int CAB = 38, CAB_ROOF = 43;
    /** The base building's half width round the shaft and its roof. */
    static final int BASE = 11, BASE_ROOF = 6;
    /** Everything this builds lies within these (in the frame). */
    static final int BX0 = TX - BASE - 2, BX1 = TX + BASE + 2, BZ0 = TZ - BASE - 6, BZ1 = TZ + BASE + 2;

    AirportGrounds(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.3, 0.0, 0.35);
    }

    void build() {
        towerBase();
        tower();
    }

    private static double dist(int x, int z) {
        return Math.hypot(x - TX, z - TZ);
    }

    /** The way out from the tower's middle toward (x, z), as a facing. */
    private static String outward(int x, int z) {
        int dx = x - TX, dz = z - TZ;
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    // ------------------------------------------------------------------ the control tower

    /**
     * The shaft from the ground to the bell, the stair up inside it round the core: a lane two wide
     * round a square, three steps a side and a landing at each corner, twelve up a turn, from the
     * door on the west at the ground to the cab's floor.
     */
    private void tower() {
        double cx = TX + 0.5, cz = TZ + 0.5;
        // the shaft's floor, the core, the wall
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                set(TX + dx, 0, TZ + dz, "tx_terrazzo");
            }
        }
        for (int y = 1; y < CAB; y++) {
            fill(TX - 1, y, TZ - 1, TX + 1, y, TZ + 1, "tx_concrete");
        }
        Round.wall(p, cx, cz, SHAFT_R, 1, CAB - 5, "twr", 0);
        // slit windows up the shaft every twelve, on the corners' landings, the glass from the round kit
        for (Round.Cell c : Round.ring(cx, cz, SHAFT_R)) {
            int dx = c.x() - TX, dz = c.z() - TZ;
            if (Math.abs(Math.abs(dx) - Math.abs(dz)) <= 1 && Math.abs(dx) >= 3) {
                for (int y = 4; y < CAB - 6; y += 12) {
                    for (int k = 0; k < 3; k++) {
                        set(c.x(), y + k, c.z(), "rd_tglaze_" + c.suffix() + "[facing=" + c.facing() + "]");
                    }
                }
            }
        }
        stair();
        // the door at the ground on the west, onto the first landing
        for (Round.Cell c : Round.ring(cx, cz, SHAFT_R)) {
            int dz = c.z() - TZ;
            if (c.x() < TX - 3 && dz >= -3 && dz <= -2) {
                for (int y = 1; y <= 3; y++) {
                    set(c.x(), y, c.z(), y == 3 ? "rd_twr_" + c.suffix() + "[facing=" + c.facing() + "]" : "air");
                }
                door(c.x(), 0, c.z(), Doors.STEEL, "west");
            }
        }
        // the bell flaring out under the cab, its soffit closed ring by ring
        double[] bell = {6.5, 7.5, 8.5};
        for (int k = 0; k < bell.length; k++) {
            int y = CAB - 4 + k;
            double inner = k == 0 ? SHAFT_R - 0.5 : bell[k - 1] - 0.5;
            annulus(y, inner, bell[k], "tx_concrete");
            Round.wall(p, cx, cz, bell[k], y, y, "twr", 0);
        }
        annulus(CAB - 1, 8.0, CAB_R, "fit_deckplate");
        Round.wall(p, cx, cz, CAB_R, CAB - 1, CAB - 1, "twd", 0);
        cab(cx, cz);
    }

    /** Fills the ring between radii r0 and r1 at y with block (inside the circle's own panels). */
    private void annulus(int y, double r0, double r1, String block) {
        int ri = (int) Math.ceil(r1) + 1;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.hypot(dx, dz);
                if (d >= r0 && d < r1 - 0.6) {
                    set(TX + dx, y, TZ + dz, block);
                }
            }
        }
    }

    /** The stair winding up the shaft, from the ground's landing in the north-west corner. */
    private void stair() {
        // the four sides in turn: the lane's cells across (two) and the three steps along, the way up
        int[][] sides = {{0, -1, 1, 0}, {1, 0, 0, 1}, {0, 1, -1, 0}, {-1, 0, 0, -1}};
        String[] up = {"east", "south", "west", "north"};
        int s = 1;
        int side = 0;
        while (s < CAB + 1) {
            int[] sd = sides[side];
            // the corner before this side: a landing
            int cxo = side == 0 ? -1 : side == 1 ? 1 : side == 2 ? 1 : -1;
            int czo = side == 0 ? -1 : side == 1 ? -1 : side == 2 ? 1 : 1;
            for (int a = 2; a <= 3; a++) {
                for (int b = 2; b <= 3; b++) {
                    if (s > 1) {
                        set(TX + cxo * a, s - 1, TZ + czo * b, "tx_concrete");
                    }
                }
            }
            // three steps along the side
            for (int k = -1; k <= 1 && s < CAB + 1; k++) {
                for (int w = 2; w <= 3; w++) {
                    int x = TX + (sd[0] != 0 ? sd[0] * w : sd[2] * k);
                    int z = TZ + (sd[1] != 0 ? sd[1] * w : sd[3] * k);
                    set(x, s, z, s == CAB ? "tx_concrete" : "stairs_concrete[facing=" + up[side] + "]");
                }
                s++;
            }
            side = (side + 1) % 4;
        }
    }

    /**
     * The cab: the floor, the glazing all round, the consoles in a ring facing out and the chairs
     * behind them, the supervisor's desk by the stair, the roof with its overhang, the catwalk round
     * the band below, the radar dome and the masts on top.
     */
    private void cab(double cx, double cz) {
        int y = CAB + 1;
        int ri = (int) Math.ceil(CAB_R) + 2;
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dz = -ri; dz <= ri; dz++) {
                double d = Math.hypot(dx, dz);
                boolean well = dx >= -3 && dx <= -2 && dz >= -3 && dz <= 1;
                if (d < CAB_R - 0.6 && !well) {
                    set(TX + dx, CAB, TZ + dz, chance() < 0.08 ? "un_slab_carpet_b" : "un_slab_carpet");
                }
                // the catwalk's deck round the band
                if (d >= CAB_R - 0.6 && d < CAB_R + 1.2) {
                    set(TX + dx, CAB - 1, TZ + dz, "fit_deckplate");
                }
            }
        }
        // the stair's well: the rail round it on the cab's floor
        for (int dz = -1; dz <= 1; dz++) {
            set(TX - 1, y, TZ + dz, "tx_glass_rail[axis=z]");
        }
        for (int dx = -3; dx <= -2; dx++) {
            set(TX + dx, y, TZ + 2, "tx_glass_rail[axis=x]");
        }
        Round.wall(p, cx, cz, CAB_R, y, CAB_ROOF - 1, "tglaze", 0);
        Round.wall(p, cx, cz, CAB_R + 1.5, CAB, CAB, "rail", 0);
        // the consoles in a ring facing out, the screens on them, the chairs behind
        for (Round.Cell c : Round.ring(cx, cz, CAB_R - 1.5)) {
            String f = outward(c.x(), c.z());
            if (!empty(c.x(), y, c.z())) {
                continue;
            }
            set(c.x(), y, c.z(), (power && chance() < 0.5 ? "fit_console_on" : "fit_console") + "[facing=" + f + "]");
            if (chance() < 0.3) {
                set(c.x(), y + 1, c.z(), (power && chance() < 0.5 ? "hs_monitor_on" : "hs_monitor") + "[facing=" + f + "]");
            }
        }
        for (Round.Cell c : Round.ring(cx, cz, CAB_R - 3)) {
            if (chance() < 0.4 && empty(c.x(), y, c.z())) {
                set(c.x(), y, c.z(), (chance() < 0.25 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=" + outward(c.x(), c.z()) + "]");
            }
        }
        set(TX + 2, y, TZ + 3, "home_desk_pc[facing=south]");
        set(TX + 3, y, TZ + 3, "home_desk[facing=south]");
        set(TX + 2, y, TZ + 2, "store_chair[facing=north]");
        set(TX + 2, y + 2, TZ - 1, "ap_notice_evac[facing=east]");
        for (int[] l : new int[][] {{0, -5}, {5, 0}, {0, 5}, {-5, 0}, {4, 4}, {-4, -4}}) {
            set(TX + l[0], CAB_ROOF - 1, TZ + l[1], power && chance() < 0.5 ? "fit_downlight_on" : "fit_downlight");
        }
        // the roof: the overhang ring, the flat over the cab, the dome, the masts and the light
        int top = Round.roof(p, cx, cz, CAB_R + 0.5, "sm", CAB_ROOF, "twr");
        Round.wall(p, cx, cz, 2.0, top, top + 1, "wtw", 0);
        Round.roof(p, cx, cz, 2.0, "sm", top + 2, "wtw");
        for (int[] m : new int[][] {{5, -3}, {-4, 4}}) {
            for (int yy = top; yy < top + 6; yy++) {
                set(TX + m[0], yy, TZ + m[1], yy % 2 == 0 ? "cp_mast_r" : "cp_mast_w");
            }
            set(TX + m[0], top + 6, TZ + m[1], "cp_mast_top");
        }
        set(TX - 5, top, TZ - 3, "bd_hvac_a[facing=north]");
    }

    // ------------------------------------------------------------------ the base building

    /**
     * The base building round the shaft's foot, a storey of precast with a band of windows: the
     * doors in its north face to the landside, the lobby round the shaft's door, the offices and the
     * break room in its east half, the plant room in the south-west; the shaft through its roof.
     */
    private void towerBase() {
        int x0 = TX - BASE, x1 = TX + BASE, z0 = TZ - BASE, z1 = TZ + BASE, y = 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean wall = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, wall ? "concrete_dark" : "un_slab_vct");
                for (int yy = 1; yy < BASE_ROOF; yy++) {
                    String s = "air";
                    if (wall) {
                        String out = z == z0 ? "north" : z == z1 ? "south" : x == x0 ? "west" : "east";
                        boolean win = yy >= 2 && yy <= 3 && Math.floorMod(z == z0 || z == z1 ? x : z, 3) != 0;
                        s = yy == BASE_ROOF - 1 ? "ml_precast_band" : win ? (chance() < 0.12 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]" : "ml_precast";
                    }
                    set(x, yy, z, s);
                }
                set(x, BASE_ROOF, z, "un_roof");
            }
        }
        b.flat(x0, z0, x1, z1, BASE_ROOF, "ml_precast", 1, "ml_precast");
        // the shaft's footprint cleared through the roof
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                if (Math.hypot(dx, dz) < SHAFT_R + 0.3) {
                    for (int yy = BASE_ROOF; yy <= BASE_ROOF + 3; yy++) {
                        set(TX + dx, yy, TZ + dz, "air");
                    }
                }
            }
        }
        // the doors north, the canopy and the name over them
        for (int x = TX - 1; x <= TX + 2; x++) {
            for (int yy = 1; yy <= 3; yy++) {
                set(x, yy, z0, "air");
            }
        }
        Doors.pair(p, rng, TX, 1, z0, Doors.SHOP, "north");
        for (int x = TX - 3; x <= TX + 4; x++) {
            for (int z = z0 - 3; z < z0; z++) {
                set(x, 4, z, "tx_canopy");
                set(x, 0, z, "floor_concrete");
            }
        }
        // the partitions: offices east of the lobby, the plant room south-west
        partZ(TX + 6, z0 + 1, z1 - 1, 0, BASE_ROOF, "drywall", "drywall");
        partX(TX + 7, x1 - 1, TZ, 0, BASE_ROOF, "drywall", "drywall");
        partX(TX + 7, x1 - 1, TZ + 6, 0, BASE_ROOF, "drywall", "drywall");
        partX(x0 + 1, TX - 7, TZ + 5, 0, BASE_ROOF, "drywall", "drywall");
        for (int z : new int[] {TZ - 6, TZ + 3, TZ + 8}) {
            set(TX + 6, 1, z, "air");
            set(TX + 6, 2, z, "air");
            door(TX + 6, 0, z, Doors.FLAT, "east");
        }
        set(TX - 9, 1, TZ + 5, "air");
        set(TX - 9, 2, TZ + 5, "air");
        door(TX - 9, 0, TZ + 5, Doors.STEEL, "south");
        // the offices: desks with their screens, the cabinets, a map of the field
        for (int x = TX + 8; x <= x1 - 2; x += 3) {
            for (int z : new int[] {z0 + 3, TZ + 3}) {
                set(x, y, z, "home_desk_pc[facing=north]");
                set(x, y, z + 1, (chance() < 0.2 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=north]");
            }
        }
        set(x1 - 1, y, z0 + 1, "fit_cabinet[facing=west]");
        set(x1 - 1, y, z0 + 2, "fit_cabinet[facing=west]");
        set(TX + 8, y + 1, TZ - 1, "of_whiteboard[facing=north]");
        // the break room: the table, the counter, the fridge
        set(TX + 9, y, z1 - 2, "fit_mess_table[axis=x]");
        set(TX + 9, y, z1 - 3, "store_chair[facing=south]");
        set(x1 - 1, y, z1 - 1, "home_fridge[facing=west]");
        set(x1 - 1, y, z1 - 2, "store_counter[facing=west]");
        set(x1 - 1, y, z1 - 3, "store_coffee[facing=west]");
        // the plant room: the boilers and the switchgear
        for (int x = x0 + 1; x <= TX - 8; x += 2) {
            set(x, y, z1 - 1, "filing_cabinet[facing=north]");
        }
        set(x0 + 2, y, TZ + 7, "water_tank");
        // the lobby: the desk facing the doors, the seats, the notices, the lights
        for (int x = TX - 3; x <= TX + 3; x++) {
            set(x, y, z0 + 4, x == TX ? "store_register[facing=north]" : "ap_counter[facing=north]");
        }
        set(TX, y, z0 + 5, "store_chair[facing=north]");
        for (int z = z0 + 2; z <= z0 + 6; z++) {
            set(x0 + 1, y, z, "ap_seats[facing=east]");
        }
        set(x0 + 3, y + 1, z0, "ap_notice_fema[facing=south]");
        lamps(x0, z0, x1, z1, BASE_ROOF, 0.35);
        mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.03);
    }
}
