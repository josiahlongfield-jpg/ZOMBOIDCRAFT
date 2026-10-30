package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Harbor Arena, on its three-by-two lot by the island's east shore: the home of the Grayhaven
 * Gulls, a precast box with glass entrance halls at both ends. The floor is sunk ten below the
 * street so the concourse is at the doors: the rink at the bottom with its boards and glass, the
 * lower bowl rising from it to the cross-aisle at street level, the portal wall with the ribbon
 * board, the upper deck over the concourse to the walkway at the top, the trusses and the
 * scoreboard hung over centre ice. Under the bowl the event level: the corridor round it, the
 * tunnels to the ice at each end, the rooms (see {@link ArenaRooms}).
 *
 * <p>Everything round the rink is in bands b, the distance out from the rink's rounded edge:
 * the boards at 0, the rows of the lower bowl 4 .. 22, the cross-aisle 23 .. 26, the portal wall
 * 27, the upper rows 28 .. 41 over the concourse (28 .. 39, its outer wall at 40), the walkway at
 * 13 from 42 out to the box. No photos of it yet: redo it when city/arena-* lands.
 */
final class Arena extends Grounds {
    /** The rink's centre (between cells), half length, half width and corner radius. */
    static final int X = 106, Z = 66, RW = 30, RD = 13, RC = 8;
    /** The event floor, the concourse (the street), the upper walkway, the roof. */
    static final int EV = -10, CONC = 0, UP = 13, ROOF = 30;
    /** The box's walls. */
    static final int BX0 = 28, BX1 = 183, BZ0 = 5, BZ1 = 126;
    /** The bands. */
    static final int FRONT = 4, LOW_LAST = 22, PORTAL = 27, UP_FIRST = 28, CONC_WALL = 40, UP_LAST = 41;
    /** The event corridor round under the rows and its walls. */
    static final int COR0 = 16, COR1 = 20;
    /** The tunnels to the ice at each end: z 64 .. 67, walls at 63 and 68. */
    static final int TZ0 = 64, TZ1 = 67;
    /** The ramp from the west plaza down to the dock, z 20 .. 23. */
    static final int DZ0 = 20, DZ1 = 23;

    /** The fire stairs in the box's corners (shaft x0, z0; the doors face the bowl). */
    static final int[][] CORES = {{36, 8}, {171, 8}, {36, 114}, {171, 114}};

    static final String FILL = "concrete_dark", WALL = "concrete_panel", TREAD = "st_tread", STEP = "st_step", RAIL = "st_rail",
            FLOOR = "floor_concrete";

    final Kit kit;
    final boolean[][] ice;

    Arena(Placer p, SplittableRandom rng, int w, int d, Kit kit) {
        super(p, rng, w, d, 0.45, 0.15, 0.35);
        this.kit = kit;
        this.ice = new boolean[w][d];
    }

    @Override
    String signs() {
        return "ar_sgn_";
    }

    // ------------------------------------------------------------------ the bands

    static double px(int x) {
        return x + 0.5 - X;
    }

    static double pz(int z) {
        return z + 0.5 - Z;
    }

    /** How far out from the rink's rounded edge (negative inside it). */
    static double dist(int x, int z) {
        double dx = Math.max(Math.abs(px(x)) - (RW - RC), 0), dz = Math.max(Math.abs(pz(z)) - (RD - RC), 0);
        return Math.hypot(dx, dz) - RC;
    }

    static int band(int x, int z) {
        return (int) Math.floor(dist(x, z));
    }

    static boolean inBox(int x, int z) {
        return x > BX0 && x < BX1 && z > BZ0 && z < BZ1;
    }

    /** The top of the tread (or floor) at band b. */
    static int tread(int b) {
        if (b < FRONT) {
            return EV;
        }
        if (b == FRONT) {
            return EV + 1;
        }
        if (b <= LOW_LAST) {
            return EV + 1 + (b - 5) / 2;
        }
        if (b < PORTAL) {
            return CONC;
        }
        if (b == PORTAL) {
            return 7;
        }
        if (b <= UP_LAST) {
            return 7 + (b - UP_FIRST) / 2;
        }
        return UP;
    }

    /** The way toward the rink from a cell: north, east, south or west. */
    static String in(int x, int z) {
        double sx = Math.signum(px(x)) * Math.max(Math.abs(px(x)) - (RW - RC), 0);
        double sz = Math.signum(pz(z)) * Math.max(Math.abs(pz(z)) - (RD - RC), 0);
        if (sx == 0 && sz == 0) {
            return "north";
        }
        return Math.abs(sx) >= Math.abs(sz) ? (sx > 0 ? "west" : "east") : (sz > 0 ? "north" : "south");
    }

    static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** On a straight long side (north or south). */
    static boolean longSide(int x) {
        return Math.abs(px(x)) <= RW - RC;
    }

    static boolean endSide(int z) {
        return Math.abs(pz(z)) <= RD - RC;
    }

    /** Across a corner ray at deg (0 the end, 90 the side) from the corner's centre: lateral and along. */
    static double[] corner(int x, int z, double deg) {
        double cxr = Math.signum(px(x)) * (RW - RC), czr = Math.signum(pz(z)) * (RD - RC);
        double a = Math.toRadians(deg), dx = Math.signum(px(x)) * Math.cos(a), dz = Math.signum(pz(z)) * Math.sin(a);
        double rx = px(x) - cxr, rz = pz(z) - czr;
        return new double[] {Math.abs(rx * dz - rz * dx), rx * dx + rz * dz};
    }

    static boolean inCorner(int x, int z) {
        return !longSide(x) && !endSide(z);
    }

    private static final double[] LONG_AISLES = {-17, -5, 5, 17}, CORNER_AISLES = {28, 62};
    private static final double[] LONG_PORTALS = {-11, 11};

    /** An aisle down the bowl (two wide); the upper deck has one down each end too. */
    static boolean aisle(int x, int z, boolean upper) {
        if (longSide(x)) {
            for (double a : LONG_AISLES) {
                if (Math.abs(px(x) - a) < 1) {
                    return true;
                }
            }
            return false;
        }
        if (endSide(z)) {
            return upper && Math.abs(pz(z)) < 1;
        }
        for (double deg : CORNER_AISLES) {
            if (corner(x, z, deg)[0] < 1) {
                return true;
            }
        }
        return false;
    }

    /** A portal through the wall between the cross-aisle and the concourse (four wide). */
    static boolean portal(int x, int z) {
        if (longSide(x)) {
            for (double a : LONG_PORTALS) {
                if (Math.abs(px(x) - a) < 2) {
                    return true;
                }
            }
            return false;
        }
        if (endSide(z)) {
            return Math.abs(pz(z)) < 2;
        }
        return corner(x, z, 45)[0] < 2;
    }

    /** In one of the end tunnels' lanes (inside its walls), and on the walls. */
    static boolean tunnel(int x, int z) {
        return !longSide(x) && z >= TZ0 && z <= TZ1;
    }

    static boolean tunnelWall(int x, int z) {
        return !longSide(x) && (z == TZ0 - 1 || z == TZ1 + 1);
    }

    // ------------------------------------------------------------------ building

    void build() {
        ground();
        for (int x = BX0 + 1; x < BX1; x++) {
            for (int z = BZ0 + 1; z < BZ1; z++) {
                column(x, z);
            }
        }
        rink();
        benches();
        roof();
        scoreboard();
        facade();
        int[] ys = {EV, CONC, UP, ROOF};
        for (int[] c : CORES) {
            fill(c[0] + 1, EV + 1, c[1] + 1, c[0] + 3, ROOF + 4, c[1] + 8, "air");
            stair(c[0], c[1], c[1] < Z, ys, WALL, "un_roof");
        }
        new ArenaRooms(this).build();
        plazas();
        ramp();
    }

    /** The lot: paved to the street, the box dug out to the event level. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                set(x, CONC, z, chance() < 0.12 ? "sidewalk_cracked" : "sidewalk");
            }
        }
        for (int x = BX0; x <= BX1; x++) {
            for (int z = BZ0; z <= BZ1; z++) {
                set(x, EV - 2, z, FILL);
                set(x, EV - 1, z, FILL);
                boolean wall = x == BX0 || x == BX1 || z == BZ0 || z == BZ1;
                set(x, EV, z, wall ? FILL : FLOOR);
                for (int y = EV + 1; y < ROOF; y++) {
                    set(x, y, z, wall && y <= CONC ? FILL : "air");
                }
            }
        }
    }

    /** One column of the bowl, the concourse, the event level under it. */
    private void column(int x, int z) {
        int b = band(x, z);
        if (b < 0) {
            return;
        }
        String in = in(x, z), out = opposite(in);
        boolean tun = tunnel(x, z) && b <= 21, twall = tunnelWall(x, z) && b <= 21;
        if (b <= 3) {
            set(x, EV, z, b == 1 || b == 2 ? "ar_rubber" : FLOOR);
            if (b == 0) {
                boards(x, z, in, tun);
            } else if (b == 3 && !tun && aisle(x, z, false)) {
                set(x, EV + 1, z, STEP + "[facing=" + out + "]");
            }
            return;
        }
        int t = tread(b);
        if (b <= LOW_LAST) {
            lower(x, z, b, t, in, out, tun, twall);
            return;
        }
        // the cross-aisle, the portal wall, the concourse and the outer zone over the event level
        set(x, CONC, z, b < PORTAL ? TREAD : b == PORTAL ? WALL : b < CONC_WALL ? "st_floor" : FLOOR);
        if (b < PORTAL) {
            return;
        }
        if (b == PORTAL) {
            boolean open = portal(x, z);
            for (int y = CONC + 1; y <= 7; y++) {
                set(x, y, z, open && y <= 3 ? "air" : WALL);
            }
            set(x, 7, z, "st_wallcap");
            set(x, 8, z, RAIL + "[facing=" + in + "]");
            ribbon(x, z, in);
            return;
        }
        if (b <= UP_LAST) {
            upper(x, z, b, t, in, out);
            if (b == CONC_WALL) {
                for (int y = CONC + 1; y < t; y++) {
                    set(x, y, z, WALL);
                }
            }
            return;
        }
        set(x, UP, z, b <= 45 ? TREAD : "st_floor");
    }

    /** The boards at band 0: the dashers, the glass over them, the gates at the tunnels. */
    private void boards(int x, int z, String in, boolean gate) {
        if (gate) {
            return;
        }
        set(x, EV + 1, z, dasher(x, z, in) + "[facing=" + in + "]");
        set(x, EV + 2, z, "ar_shield");
        set(x, EV + 3, z, longSide(x) ? "air" : "ar_shield");
    }

    private static final String[] ADS = {"savings", "cola", "radio", "motors"};

    private String dasher(int x, int z, String in) {
        if (!longSide(x)) {
            return "ar_dasher";
        }
        int a = in.equals("north") ? -x : x;
        int slot = Math.floorDiv(a, 6), col = Math.floorMod(a, 6);
        return col >= 4 ? "ar_dasher" : "ar_ad_" + ADS[Math.floorMod(slot, 4)] + "_" + col;
    }

    /** The lower bowl at band b: the tread, the fill or the corridor under it, the seats, the steps. */
    private void lower(int x, int z, int b, int t, String in, String out, boolean tun, boolean twall) {
        if (tun && t < EV + 5) {
            return;                                  // the tunnel's open trench
        }
        if (twall && t < EV + 5) {
            for (int y = EV + 1; y <= t; y++) {
                set(x, y, z, WALL);
            }
            set(x, t + 1, z, RAIL + "[facing=" + (z < Z ? "south" : "north") + "]");
            return;
        }
        set(x, t, z, chance() < 0.15 ? "st_tread_gum" : TREAD);
        if (!tun && b <= 14) {
            for (int y = EV + 1; y < t; y++) {
                set(x, y, z, FILL);
            }
        } else if (b == 15 || b == 21) {
            for (int y = EV + 1; y < t; y++) {
                set(x, y, z, tun ? "air" : WALL);
            }
        }
        if (aisle(x, z, false)) {
            if (tread(b + 1) > t) {
                set(x, t + 1, z, STEP + "[facing=" + out + "]");
            }
            return;
        }
        if (b == FRONT) {
            set(x, t + 1, z, RAIL + "[facing=" + in + "]");
            return;
        }
        if ((b - 5) % 2 == 1) {
            set(x, t + 1, z, seat(true, b) + "[facing=" + in + "]");
        }
    }

    /** The upper deck at band b over the concourse. */
    private void upper(int x, int z, int b, int t, String in, String out) {
        set(x, t, z, chance() < 0.1 ? "st_tread_gum" : TREAD);
        if (aisle(x, z, true)) {
            if (tread(b + 1) > t) {
                set(x, t + 1, z, STEP + "[facing=" + out + "]");
            }
            return;
        }
        if ((b - UP_FIRST) % 2 == 1) {
            set(x, t + 1, z, seat(false, b) + "[facing=" + in + "]");
        }
    }

    /** The lower bowl is where the shelter's people slept; the upper mostly folded up, the high corners camped in. */
    private String seat(boolean low, int b) {
        double r = chance();
        String c = low ? "b" : b > 36 && chance() < 0.4 ? "f" : "s";
        String s = low ? (r < 0.45 ? "" : r < 0.58 ? "_up" : r < 0.68 ? "_mix" : r < 0.84 ? "_blanket" : r < 0.95 ? "_trash" : "_broken")
                : (r < 0.35 ? "" : r < 0.75 ? "_up" : r < 0.86 ? "_mix" : r < 0.93 ? "_trash" : r < 0.97 ? "_blanket" : "_broken");
        return "st_seat_" + c + s;
    }

    /** The ribbon board along the portal wall's face on the long sides, dark round the ends. */
    private void ribbon(int x, int z, String in) {
        if (portal(x, z)) {
            return;
        }
        if (!longSide(x)) {
            set(x, 5, z, "st_ribbon_off[facing=" + in + "]");
            set(x, 6, z, "st_ribbon_off[facing=" + in + "]");
            return;
        }
        int a = in.equals("north") ? -x : x;
        int col = Math.floorMod(a, 16);
        String k = Math.floorMod(Math.floorDiv(a, 16), 2) == 1 ? "b" : "a";
        set(x, 6, z, "ar_ribbon_" + k + "_" + col + "[facing=" + in + "]");
        set(x, 5, z, "ar_ribbon_" + k + "_" + (col + 16) + "[facing=" + in + "]");
    }

    // ------------------------------------------------------------------ the rink

    /**
     * The ice: the lines and circles under it, the crest at centre ice; over the middle the
     * shelter's plywood deck (pulled up round the crest), the ice rotting at the ends.
     */
    private void rink() {
        for (int x = BX0 + 1; x < BX1; x++) {
            for (int z = BZ0 + 1; z < BZ1; z++) {
                if (dist(x, z) >= 0) {
                    continue;
                }
                ice[x][z] = true;
                double ax = Math.abs(px(x)), az = Math.abs(pz(z));
                String s = "ar_ice";
                if (ax < 1) {
                    s = "ar_ice_red";
                } else if (ax >= 9 && ax < 10) {
                    s = "ar_ice_blue";
                } else if (ax >= 26 && ax < 27 && az < 11) {
                    s = "ar_ice_red";
                } else if (ax >= 24 && ax < 26 && az < 2) {
                    s = "ar_ice_crease";
                }
                for (double cx : new double[] {-20.5, 20.5}) {
                    for (double cz : new double[] {-7, 7}) {
                        double r = Math.hypot(px(x) - cx, pz(z) - cz);
                        if (Math.abs(r - 4.5) < 0.5 || r < 0.8) {
                            s = "ar_ice_red";
                        }
                    }
                }
                if (Math.abs(Math.hypot(px(x), pz(z)) - 4.5) < 0.5) {
                    s = "ar_ice_blue";
                }
                int lx = x - (X - 3), lz = z - (Z - 3);
                if (lx >= 0 && lx < 5 && lz >= 0 && lz < 5) {
                    s = "ar_ice_logo_" + (lz * 5 + lx);
                }
                boolean crest = lx >= -1 && lx <= 5 && lz >= -1 && lz <= 5;
                if (ax <= 18 && !crest && chance() > 0.05) {
                    double r = chance();
                    s = r < 0.75 ? "ar_deck" : r < 0.95 ? "ar_deck_b" : "ar_deck_blood";
                } else if (s.equals("ar_ice") && (ax > 18 && ax < 21 || ax > 25 && az > 6 || crest) && chance() < 0.35) {
                    s = "ar_ice_melt";
                }
                set(x, EV, z, s);
            }
        }
        // the goals: the west one in its crease, the east one knocked over
        for (int c = 0; c < 2; c++) {
            int[] a = MallSigns.at(X - 28, Z - 1, 2, c, "east");
            set(a[0], EV + 1, a[1], "ar_goal_" + c + "[facing=east]");
        }
        set(X + 27, EV + 1, Z - 1, "ar_goal_down[facing=south]");
        set(X + 27, EV + 1, Z, "ar_goal_down[facing=south]");
    }

    /** The benches cut into the north side, the penalty boxes and the scorers' table on the south. */
    private void benches() {
        int zn = Z - RD - 1, zs = Z + RD;
        for (int[] run : new int[][] {{X - 14, X - 5}, {X + 4, X + 13}}) {
            for (int x = run[0]; x <= run[1]; x++) {
                set(x, EV + 2, zn - 0, "air");
                if (x == run[0] || x == run[1]) {
                    set(x, EV + 1, zn, "air");      // the gates at each end of the bench
                    continue;
                }
                set(x, EV + 1, zn - 1, chance() < 0.85 ? "st_bench[facing=south]" : "air");
                if (chance() < 0.2) {
                    set(x, EV + 1, zn - 2, any("st_cooler", "ar_sticks[facing=south]", "trash_bag[facing=north]", "paper"));
                }
            }
        }
        for (int[] run : new int[][] {{X - 9, X - 6}, {X + 5, X + 8}}) {
            for (int x = run[0]; x <= run[1]; x++) {
                set(x, EV + 1, zs + 1, "st_bench[facing=north]");
            }
            set(run[0] - 1, EV + 1, zs + 1, "ar_shield");
            set(run[1] + 1, EV + 1, zs + 1, "ar_shield");
            set(run[0] - 1, EV + 2, zs + 1, "ar_shield");
            set(run[1] + 1, EV + 2, zs + 1, "ar_shield");
        }
        for (int x = X - 4; x <= X + 3; x++) {
            set(x, EV + 1, zs + 1, "store_counter[facing=north]");
            set(x, EV + 1, zs + 2, chance() < 0.6 ? "office_chair[facing=north]" : "office_chair_fallen[facing=" + dir() + "]");
        }
        set(X - 1, EV + 2, zs + 1, "pk_tv_crt[facing=north]");
    }

    // ------------------------------------------------------------------ the roof

    /** The roof deck on its trusses, the catwalk down the middle, the lamps, the banners. */
    private void roof() {
        for (int x = BX0; x <= BX1; x++) {
            for (int z = BZ0; z <= BZ1; z++) {
                set(x, ROOF, z, "st_roof");
                if (x == BX0 || x == BX1 || z == BZ0 || z == BZ1) {
                    set(x, ROOF + 1, z, "st_wallcap");
                }
            }
        }
        // the air handlers on the roof, their ducts down through it, skylights over the walkway
        for (int[] u : new int[][] {{X - 50, Z - 30}, {X - 10, Z - 30}, {X + 30, Z - 30}, {X - 30, Z + 26}, {X + 10, Z + 26}, {X + 50, Z + 26}}) {
            for (int x = u[0]; x < u[0] + 6; x++) {
                for (int z = u[1]; z < u[1] + 3; z++) {
                    set(x, ROOF + 1, z, "hvac");
                    set(x, ROOF + 2, z, x % 2 == 0 ? "hvac" : "air");
                }
            }
            for (int z = u[1] + 3; z < u[1] + 9; z++) {
                set(u[0] + 2, ROOF + 1, z, "st_duct[axis=z]");
            }
        }
        for (int x = BX0 + 4; x < BX1 - 3; x += 8) {
            for (int z : new int[] {BZ0 + 4, BZ1 - 4}) {
                set(x, ROOF, z, chance() < 0.2 ? "us_skylight_broken" : "us_skylight");
                set(x + 1, ROOF, z, chance() < 0.2 ? "us_skylight_broken" : "us_skylight");
            }
        }
        for (int x = BX0 + 8; x < BX1; x += 8) {
            for (int z = BZ0 + 1; z < BZ1; z++) {
                set(x, ROOF - 1, z, "st_chord[axis=z]");
                set(x, ROOF - 5, z, "st_chord[axis=z]");
                for (int y = ROOF - 4; y <= ROOF - 2; y++) {
                    set(x, y, z, "st_truss");
                }
            }
        }
        for (int z = BZ0 + 8; z < BZ1; z += 8) {
            for (int x = BX0 + 1; x < BX1; x++) {
                if (x % 8 != BX0 % 8) {
                    set(x, ROOF - 1, z, "st_chord[axis=x]");
                }
            }
        }
        // the catwalk hung under the bottom chords down the long axis, the lamp banks along it
        for (int x = BX0 + 12; x <= BX1 - 12; x++) {
            for (int z = Z - 1; z <= Z; z++) {
                set(x, ROOF - 6, z, "grate");
            }
            set(x, ROOF - 5, Z - 2, RAIL + "[facing=south]");
            set(x, ROOF - 5, Z + 1, RAIL + "[facing=north]");
        }
        for (int x = X - 32; x <= X + 32; x += 8) {
            for (int z : new int[] {Z - 14, Z - 4, Z + 3, Z + 13}) {
                set(x + 4, ROOF - 6, z, (power && chance() < 0.5 ? "st_floods_on" : "st_floods") + "[facing=" + (z < Z ? "south" : "north") + "]");
            }
        }
        // the banners over the west end: the 1981 division title and the three numbers retired
        String[] keys = {"champ", "nine", "sixteen", "four"};
        int[] zs = {Z - 20, Z - 9, Z + 8, Z + 19};
        for (int k = 0; k < 4; k++) {
            int x = BX0 + 16;
            for (int i = 0; i < 3; i++) {
                set(x, ROOF - 6 - i, zs[k], "ar_vban_" + keys[k] + "_" + i + "[facing=east]");
            }
        }
    }

    /** The scoreboard hung over centre ice: four faces six by four, the rings over and under, the cables. */
    private void scoreboard() {
        int x0 = X - 3, x1 = X + 2, z0 = Z - 3, z1 = Z + 2, y0 = 16;
        fill(x0, y0 - 1, z0, x1, y0 + 5, z1, "ar_sb_house");
        for (int c = 0; c < 6; c++) {
            for (int r = 0; r < 4; r++) {
                int y = y0 + 3 - r, i = r * 6 + c;
                int[] n = MallSigns.at(x0, z0, 6, c, "north"), s = MallSigns.at(x0, z1, 6, c, "south");
                int[] e = MallSigns.at(x1, z0, 6, c, "east"), wv = MallSigns.at(x0, z0, 6, c, "west");
                set(n[0], y, n[1], "ar_sb_" + i + "[facing=north]");
                set(s[0], y, s[1], "ar_sb_" + i + "[facing=south]");
                set(e[0], y, e[1], "ar_sb_" + i + "[facing=east]");
                set(wv[0], y, wv[1], "ar_sb_" + i + "[facing=west]");
            }
            int[] n = MallSigns.at(x0, z0, 6, c, "north"), s = MallSigns.at(x0, z1, 6, c, "south");
            int[] e = MallSigns.at(x1, z0, 6, c, "east"), wv = MallSigns.at(x0, z0, 6, c, "west");
            set(n[0], y0 + 4, n[1], "ar_sb_ring_" + c + "[facing=north]");
            set(s[0], y0 + 4, s[1], "ar_sb_ring_" + c + "[facing=south]");
            set(e[0], y0 + 4, e[1], "ar_sb_ring_" + c + "[facing=east]");
            set(wv[0], y0 + 4, wv[1], "ar_sb_ring_" + c + "[facing=west]");
        }
        for (int x = x0; x <= x1; x++) {
            set(x, y0 - 1, z0, "st_ribbon_off[facing=north]");
            set(x, y0 - 1, z1, "st_ribbon_off[facing=south]");
        }
        for (int z = z0; z <= z1; z++) {
            set(x0, y0 - 1, z, "st_ribbon_off[facing=west]");
            set(x1, y0 - 1, z, "st_ribbon_off[facing=east]");
        }
        for (int[] c : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}}) {
            for (int y = y0 + 6; y < ROOF - 5; y++) {
                set(c[0], y, c[1], "mu_cable");
            }
        }
    }

    // ------------------------------------------------------------------ the outside

    private final long wear = rng.nextLong();

    /**
     * A precast panel's block: one panel between two fins and five courses high weathers as one,
     * clean or stained down from its top, sooted, damp at the foot, cracked, mossy where it stays wet.
     */
    private String precast(int col, int y, int side) {
        long h = (col * 0x9E3779B97F4A7C15L) ^ ((y / 5) * 0xC2B2AE3D27D4EB4FL) ^ (side * 0x165667B19E3779F9L) ^ wear;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        double r = (h & 0xFFFF) / 65536.0;
        String k = r < 0.74 ? "" : r < 0.84 ? "_stain" : r < 0.89 ? "_damp" : r < 0.94 ? "_crack" : r < 0.97 ? (y > 18 ? "_soot" : "_stain")
                : (y < 10 ? "_moss" : "_damp");
        if (k.isEmpty() && y % 5 == 4 && chance() < 0.3) {
            k = "_stain";
        }
        return "st_precast_b" + k;
    }

    /** True at the ends' glass halls. */
    static boolean hall(int z) {
        return z >= Z - 22 && z <= Z + 21;
    }

    /**
     * The box's walls: precast panels between fins, a dark base, a band of clerestory glass along
     * the sides, the glass halls at the ends with their doors and canopies, the name along both
     * sides, the fire exits.
     */
    private void facade() {
        for (int y = CONC + 1; y < ROOF; y++) {
            for (int x = BX0; x <= BX1; x++) {
                for (int z : new int[] {BZ0, BZ1}) {
                    boolean fin = (x - BX0) % 8 == 0;
                    String s = y <= 3 ? FILL : fin ? "st_precast_plain" : y >= 16 && y <= 18 ? "st_glazing" + (chance() < 0.12 ? "_broken" : "")
                            + "[facing=" + (z == BZ0 ? "north" : "south") + "]" : precast((x - BX0) / 8, y, z == BZ0 ? 0 : 1);
                    set(x, y, z, s);
                    int oz = z == BZ0 ? z - 1 : z + 1;
                    if (fin && y > 3) {
                        set(x, y, oz, "st_fin[axis=z]");
                    } else if (y == 4 || y == 13 || y == 19 || y == ROOF - 1) {
                        set(x, y, oz, "st_precast_plain");
                    }
                }
            }
            for (int z = BZ0; z <= BZ1; z++) {
                for (int x : new int[] {BX0, BX1}) {
                    String f = x == BX0 ? "west" : "east";
                    boolean fin = (z - BZ0) % 8 == 0;
                    String s;
                    if (hall(z) && y <= 24) {
                        s = (z - (Z - 22)) % 4 == 0 || y == 12 ? "st_precast_plain" : "st_glazing" + (chance() < 0.08 ? "_broken" : "") + "[facing=" + f + "]";
                    } else {
                        s = y <= 3 ? FILL : fin ? "st_precast_plain" : precast((z - BZ0) / 8, y, x == BX0 ? 2 : 3);
                        int ox = x == BX0 ? x - 1 : x + 1;
                        if (fin && y > 3) {
                            set(ox, y, z, "st_fin[axis=x]");
                        } else if (y == 4 || y == 13 || y == 19 || y == ROOF - 1) {
                            set(ox, y, z, "st_precast_plain");
                        }
                    }
                    set(x, y, z, s);
                }
            }
        }
        // the doors into the halls, the canopy over them
        for (int x : new int[] {BX0, BX1}) {
            String f = x == BX0 ? "west" : "east";
            for (int z0 = Z - 17; z0 <= Z + 15; z0 += 6) {
                fill(x, CONC + 1, z0, x, CONC + 3, z0 + 1, "air");
                doors(x, CONC, z0, Doors.SHOP, f);
            }
            for (int k = 0; k < 4; k++) {
                int xx = x == BX0 ? x - 1 - k : x + 1 + k;
                for (int z = Z - 20; z <= Z + 19; z++) {
                    set(xx, 5, z, "st_canopy");
                }
            }
            for (int z = Z - 20; z <= Z + 19; z += 13) {
                for (int y = CONC + 1; y < 5; y++) {
                    set(x == BX0 ? x - 4 : x + 4, y, z, "st_column");
                }
            }
        }
        // the name along both sides between the fins
        for (int z : new int[] {BZ0, BZ1}) {
            String f = z == BZ0 ? "north" : "south";
            int len = WORD.length() * 4 - 1, x0 = X - len / 2;
            for (int k = 0; k < WORD.length(); k++) {
                char c = WORD.charAt(k);
                if (c == ' ') {
                    continue;
                }
                // seen from the north the word runs east to west, so its letters go down x there
                int lx = x0 + (z == BZ0 ? WORD.length() - 1 - k : k) * 4;
                int zz = z == BZ0 ? z - 2 : z + 2;
                for (int i = 0; i < 9; i++) {
                    int[] a = MallSigns.at(lx, zz, 3, i % 3, f);
                    set(a[0], 22 - i / 3, a[1], "ar_big_" + Character.toLowerCase(c) + "_" + i + "[facing=" + f + "]");
                }
            }
        }
        // the fire exits along the sides
        for (int x : new int[] {X - 44, X - 3, X + 40}) {
            for (int z : new int[] {BZ0, BZ1}) {
                fill(x, CONC + 1, z, x + 1, CONC + 3, z, "air");
                doors(x, CONC, z, Doors.STEEL, z == BZ0 ? "north" : "south");
                set(x, CONC + 3, z, FILL);
                set(x + 1, CONC + 3, z, FILL);
            }
        }
    }

    static final String WORD = "HARBOR ARENA";

    /**
     * The plazas: the east one the main gate (the shelter's checkpoint still across it, the
     * marquee on its pylon, the triage tent), the west one the box office, the ramp down to the
     * dock; lamps, litter, the cars left where the last of them stopped.
     */
    private void plazas() {
        // the checkpoint across the east plaza: sandbags and barriers with a gap, the tent behind
        int cx = BX1 + 10;
        for (int z = Z - 26; z <= Z + 25; z++) {
            if (Math.abs(z - Z) <= 2) {
                set(cx, CONC + 1, z, chance() < 0.5 ? "st_barrier_down[facing=east]" : "air");
                continue;
            }
            set(cx, CONC + 1, z, (z % 5 == 0 ? "st_barrier" : "sandbags") + "[facing=east]");
            if (chance() < 0.25) {
                set(cx, CONC + 2, z, "barbed_wire");
            }
        }
        tent(cx + 4, Z + 6, 10, "triage");
        tent(cx + 4, Z - 18, 8, "supply");
        for (int k = 0; k < 6; k++) {
            set(cx + 2 + rng.nextInt(12), CONC + 1, Z - 30 + rng.nextInt(60), any("st_stretcher_bag[facing=north]", "st_pallet_water",
                    "st_pallet_meals", "trash_bag[facing=east]", "home_blood_pool", "st_cone"));
        }
        // the marquee on its pylon at the plaza's south corner, read from the street and the doors
        int mx = w - 6, mz0 = d - 18;
        for (int y = CONC + 1; y <= 9; y++) {
            set(mx, y, mz0 - 1, "st_column");
            set(mx, y, mz0 + 8, "st_column");
            set(mx - 1, y, mz0 - 1, "st_column");
            set(mx - 1, y, mz0 + 8, "st_column");
        }
        for (int c = 0; c < 8; c++) {
            for (int r = 0; r < 3; r++) {
                int[] e = MallSigns.at(mx, mz0, 8, c, "east"), wv = MallSigns.at(mx - 1, mz0, 8, c, "west");
                set(e[0], 9 - r, e[1], "ar_marq_" + (r * 8 + c) + "[facing=east]");
                set(wv[0], 9 - r, wv[1], "ar_marq_" + (r * 8 + c) + "[facing=west]");
            }
        }
        // the box office windows in the west wall, south of the hall
        for (int z = Z + 26; z <= Z + 32; z++) {
            boolean win = (z - Z) % 2 == 0;
            if (win) {
                set(BX0, CONC + 2, z, (chance() < 0.3 ? "st_ticket_broken" : "st_ticket_" + (1 + rng.nextInt(4))) + "[facing=west]");
                set(BX0 - 1, CONC + 1, z, "st_ticket_ledge[facing=west]");
            }
        }
        sign("boxoffice", BX0 - 1, CONC + 4, Z + 28, "west");
        // lamps round the plazas
        for (int[] l : new int[][] {{BX1 + 4, Z - 28}, {BX1 + 4, Z + 28}, {w - 4, Z - 28}, {w - 4, 10}, {BX0 - 4, Z - 28},
                {BX0 - 4, Z + 28}, {4, Z + 40}, {4, 10}, {BX1 + 16, d - 4}}) {
            for (int y = CONC + 1; y <= 5; y++) {
                set(l[0], y, l[1], "st_lot_pole");
            }
            set(l[0], 6, l[1], power && chance() < 0.4 ? "st_lot_head_on" : "st_lot_head");
        }
        for (int i = 0; i < 160; i++) {
            int x = rng.nextInt(w), z = rng.nextInt(d);
            if (!inBox(x, z) && x != BX0 && x != BX1 && z != BZ0 && z != BZ1 && empty(x, CONC + 1, z) && get(x, CONC, z) != null
                    && get(x, CONC, z).startsWith("sidewalk")) {
                set(x, CONC + 1, z, any("paper", "paper", "trash_bag[facing=" + dir() + "]", "blood_splat", "glass_shards", "debris_chunk[facing=" + dir() + "]"));
            }
        }
        kit.car(BX1 + 22.5, Z - 34.5, 95, 0, 5.6);
        kit.car(BX1 + 6.5, Z + 36.5, 172, 0, 5.6);
        kit.car(12.5, DZ0 - 7.5, 270, 7, 9.5);
        kit.car(16.5, Z + 30.5, 15, 0, 5.6);
    }

    /** The ramp from the west plaza down to the dock door under the box's north-west corner. */
    private void ramp() {
        int top = 3, bottom = BX0 - 1;
        for (int x = top; x <= bottom; x++) {
            int f = rampY(x);
            for (int z = DZ0 - 1; z <= DZ1 + 1; z++) {
                boolean wall = z == DZ0 - 1 || z == DZ1 + 1;
                for (int y = f - 2; y <= CONC; y++) {
                    set(x, y, z, wall ? FILL : y < f ? FILL : y == f ? (chance() < 0.1 ? "asphalt_crack" : "floor_concrete") : "air");
                }
                if (wall) {
                    set(x, CONC + 1, z, RAIL + "[facing=" + (z < DZ0 ? "south" : "north") + "]");
                } else if (rampY(x - 1) > f) {
                    set(x, f + 1, z, "stairs_concrete[facing=west]");
                }
            }
        }
        for (int z = DZ0 - 1; z <= DZ1 + 1; z++) {
            set(top - 1, CONC + 1, z, z == DZ0 - 1 || z == DZ1 + 1 ? RAIL + "[facing=east]" : "air");
        }
        fill(BX0, EV + 1, DZ0, BX0, EV + 4, DZ1, "air");
        for (int z = DZ0; z <= DZ1; z++) {
            set(BX0, EV + 4, z, "st_grille_half[facing=west]");
        }
    }

    /** The ramp's floor at x: the event level at the door, the street at the top, a step in every two and a half. */
    private int rampY(int x) {
        if (x >= BX0) {
            return EV;
        }
        return Math.min(CONC, EV + (int) Math.floor((BX0 - 1 - x) / 2.5));
    }
}
