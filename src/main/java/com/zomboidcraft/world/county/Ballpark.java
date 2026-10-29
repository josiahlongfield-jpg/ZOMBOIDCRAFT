package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Stairwell;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Grayhaven Ballpark, on its three-by-three lot by the island's west shore: the home of the
 * Skippers, an old-style park of red brick and green steel with home plate in the north-east
 * corner and the outfield running down toward the water. The field is sunk ten below the street
 * so the concourse is at the gates. Two decks wrap round home and down the lines to the poles
 * (the grandstand), the open concourse between them looking onto the field, the units and the
 * fire stairs behind it, the passage inside the brick wall; across the outfield the bleachers
 * under the ivy, the batter's eye in centre, the hand-turned scoreboard over right. Under the
 * grandstand the event level: the corridor from dugout to dugout, the clubhouses and the rooms
 * (see {@link BallparkRooms}). The Guard used the field as an evacuation landing zone.
 *
 * <p>Everything round the field is in bands b, the distance out from the field's edge (the foul
 * ground sixteen out from the lines, the outfield wall): the wall at 0, the lower rows 1 .. 17,
 * the concourse 18 .. 23, the units 24 .. 35 under the upper deck (its fascia at 14, its rows
 * 15 .. 33 up to the walkway at 17 from 34 to 37), the passage 36 .. 37, the brick wall at 38. In
 * the outfield the bleachers' rows 1 .. 17, the walk behind them, their back wall at 22, the plaza
 * past it. No photos of it yet: redo it when city/ballpark-* lands.
 */
final class Ballpark extends Grounds {
    /** Home plate's cell; the first-base line runs west from it, the third-base line south. */
    static final int HX = 150, HZ = 62;
    /** Foul ground from the lines to the stands, the lines' length to the poles. */
    static final int FOUL = 16, POLE = 96;
    /** The field, the concourse (the street), the upper walkway, the roof. */
    static final int EV = -10, CONC = 0, UPW = 17, ROOF = 24;
    /** The grandstand's bands. */
    static final int LOW_LAST = 17, CONC_LAST = 23, UNIT0 = 24, UNIT1 = 35, PASS0 = 36, PASS1 = 37, FACADE = 38;
    static final int FASCIA = 14, UP_FIRST = 15, UP_LAST = 33;
    /** The event corridor under the rows. */
    static final int CORR0 = 9, CORR1 = 12;
    /** The outfield: the walk behind the bleachers, their back wall. */
    static final int BACK = 22;
    /** The fire stairs, four along from each of these on both lines; the gates, four wide. */
    static final int[] CORES = {20, 50, 80};
    static final int[] GATES = {33, 63};
    /** The dugouts along each line. */
    static final int DUG0 = 8, DUG1 = 26, TUNNEL = 17;

    static final String FILL = "concrete_dark", WALL = "concrete_panel", TREAD = "st_tread", STEP = "st_step", RAIL = "st_rail",
            FLOOR = "floor_concrete";

    final Kit kit;
    /** The band of every cell (-1 on the field). */
    final int[][] band;
    final boolean[][] field;

    Ballpark(Placer p, SplittableRandom rng, int w, int d, Kit kit) {
        super(p, rng, w, d, 0.4, 0.15, 0.3);
        this.kit = kit;
        this.band = new int[w][d];
        this.field = new boolean[w][d];
    }

    @Override
    String signs() {
        return "bp_sgn_";
    }

    // ------------------------------------------------------------------ the geometry

    static int a(int x) {
        return HX - x;
    }

    static int c(int z) {
        return z - HZ;
    }

    /** The outfield wall's distance from home at an angle (0 down the first-base line, 90 the third). */
    static double fence(double deg) {
        return POLE + 24 * Math.pow(Math.max(0, Math.sin(Math.toRadians(2 * deg))), 1.3);
    }

    /** The angle from home of a fair cell, 0 .. 90. */
    static double theta(int x, int z) {
        return Math.toDegrees(Math.atan2(c(z), Math.max(a(x), 0)));
    }

    /** 0 the first-base stand (north of its line), 1 the third-base stand (east), 2 behind home, 3 fair. */
    static int side(int x, int z) {
        int a = a(x), c = c(z);
        if (a >= 0 && c >= 0) {
            return 3;
        }
        return a >= 0 ? 0 : c >= 0 ? 1 : 2;
    }

    /** How far along its line from home a foul cell is (0 behind home). */
    static int along(int x, int z) {
        return switch (side(x, z)) {
            case 0 -> a(x);
            case 1 -> c(z);
            default -> 0;
        };
    }

    /** In the grandstand (or its foul ground): the foul side short of the poles. */
    static boolean grand(int x, int z) {
        return side(x, z) != 3 && along(x, z) <= POLE;
    }

    /** On the field: fair short of the wall, or the foul ground in front of the grandstand. */
    static boolean onField(int x, int z) {
        int a = a(x), c = c(z);
        if (a >= 0 && c >= 0) {
            return Math.hypot(a, c) < fence(theta(x, z));
        }
        if (!grand(x, z)) {
            return false;
        }
        double wedge = a >= 0 ? -c : c >= 0 ? -a : Math.hypot(a, c);
        return wedge < FOUL;
    }

    /** The cell at a band out from the field, along a line from home: side 0 the first-base stand, 1 the third. */
    static int[] at(int side, int along, int b) {
        return side == 0 ? new int[] {HX - along, HZ - FOUL - b} : new int[] {HX + FOUL + b, HZ + along};
    }

    /** Toward the field from a cell (the stands face it). */
    static String in(int x, int z) {
        int s = side(x, z);
        if (s == 0 && grand(x, z)) {
            return "south";
        }
        if (s == 1 && grand(x, z)) {
            return "west";
        }
        int a = a(x), c = c(z);
        return Math.abs(a) >= Math.abs(c) ? (a > 0 ? "east" : "west") : (c > 0 ? "north" : "south");
    }

    static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** The lower rows' tread at band b (the wall's top at 0). */
    static int tread(int b) {
        return b <= 0 ? EV : EV + 2 + (b - 1) / 2;
    }

    /** The upper deck's tread at band b (15 .. 33), the walkway's behind. */
    static int upper(int b) {
        return b > UP_LAST ? UPW : 8 + (b - UP_FIRST) / 2;
    }

    /** In the batter's eye: the band of centre field the hitters look into, kept dark. */
    static boolean eye(int x, int z) {
        return side(x, z) == 3 && Math.abs(theta(x, z) - 45) < 5;
    }

    /** An aisle down the rows (two wide): every sixteen along the lines, three behind home, every nine degrees in the bleachers. */
    boolean aisle(int x, int z) {
        int s = side(x, z);
        if (grand(x, z) && s < 2) {
            int m = Math.floorMod(along(x, z), 16);
            return m == 7 || m == 8;
        }
        int a = a(x), c = c(z);
        double r = Math.hypot(a, c);
        if (grand(x, z)) {
            double psi = Math.toDegrees(Math.atan2(-c, -a));
            for (double k : new double[] {22.5, 45, 67.5}) {
                if (Math.abs(psi - k) * Math.PI / 180 * r < 1) {
                    return true;
                }
            }
            return false;
        }
        double t = Math.toDegrees(Math.atan2(c, a));
        for (int k = -20; k <= 110; k += 9) {
            if (Math.abs(t - k) * Math.PI / 180 * r < 1) {
                return true;
            }
        }
        return false;
    }

    /** Dug down to the event level: the field, the grandstand inside its wall, the bleachers. */
    boolean dug(int x, int z) {
        int b = band[x][z];
        return b < 0 || (grand(x, z) ? b <= FACADE : b <= LOW_LAST);
    }

    // ------------------------------------------------------------------ building

    void build() {
        measure();
        ground();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (band[x][z] < 0) {
                    turf(x, z);
                } else if (grand(x, z)) {
                    stand(x, z);
                } else {
                    bleacher(x, z);
                }
            }
        }
        lines();
        dugouts();
        ends();
        facade();
        int[] ys = {EV, CONC, UPW, ROOF};
        for (int s = 0; s < 2; s++) {
            for (int c0 : CORES) {
                int[] o = at(s, s == 0 ? c0 + 4 : c0, UNIT1);
                core(o[0], o[1], s == 0 ? 0 : 1, ys);
            }
        }
        new BallparkRooms(this).build();
        scoreboard();
        lights();
        new BallparkLz(this).build();
    }

    /** Every cell's band: the distance out to the nearest field cell. */
    private void measure() {
        List<int[]> edge = new ArrayList<>();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                field[x][z] = onField(x, z);
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (field[x][z] && (x == 0 || z == 0 || x == w - 1 || z == d - 1 || !field[x - 1][z] || !field[x + 1][z] || !field[x][z - 1]
                        || !field[x][z + 1])) {
                    edge.add(new int[] {x, z});
                }
            }
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (field[x][z]) {
                    band[x][z] = -1;
                    continue;
                }
                double best = 1e9;
                for (int[] e : edge) {
                    double dx = e[0] - x, dz = e[1] - z;
                    double q = dx * dx + dz * dz;
                    if (q < best) {
                        best = q;
                    }
                }
                band[x][z] = Math.max(0, (int) Math.floor(Math.sqrt(best)) - 1);
            }
        }
    }

    /** The lot paved to the street; the bowl dug out, a retaining wall where it stops. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (!dug(x, z)) {
                    set(x, CONC, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
                    continue;
                }
                set(x, EV - 2, z, FILL);
                set(x, EV - 1, z, FILL);
                set(x, EV, z, FLOOR);
                boolean rim = x == 0 || z == 0 || x == w - 1 || z == d - 1 || !dug(x - 1, z) || !dug(x + 1, z) || !dug(x, z - 1) || !dug(x, z + 1);
                for (int y = EV + 1; y <= ROOF + 2; y++) {
                    set(x, y, z, rim && y <= CONC ? FILL : "air");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the field

    /** The playing surface: the clay round home and the infield, the grass mown in squares, the warning track. */
    private void turf(int x, int z) {
        int a = a(x), c = c(z);
        String s;
        double r = Math.hypot(a, c), m = Math.hypot(a - 13, c - 13);
        boolean fair = a >= 0 && c >= 0;
        if (r < 4.5) {
            s = "bp_clay";
        } else if (fair && m < 3) {
            s = "bp_clay";
        } else if (fair && a >= 2 && c >= 2 && a <= 25 && c <= 25) {
            s = grass(x, z);
        } else if (m < 28.5 && a >= -2 && c >= -2) {
            s = "bp_clay";
        } else if (fair ? r >= fence(theta(x, z)) - 5 : band(x, z, 4)) {
            s = "bp_track";
        } else {
            s = grass(x, z);
        }
        set(x, EV, z, s);
        set(x, EV - 1, z, "bp_clay");
    }

    /** Within n of the wall on foul ground. */
    private boolean band(int x, int z, int n) {
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                int xx = x + i, zz = z + j;
                if (xx >= 0 && zz >= 0 && xx < w && zz < d && !field[xx][zz] && i * i + j * j <= n * n) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The grass in its mown squares, a season long, patches gone to weed. */
    String grass(int x, int z) {
        if (chance() < 0.08) {
            return "bp_grass_rough";
        }
        return Math.floorMod(Math.floorDiv(x + z, 8) + Math.floorDiv(x - z, 8), 2) == 0 ? "bp_grass_a" : "bp_grass_b";
    }

    /** The chalk down the lines to the poles, the bags, the plate, the rubber. */
    private void lines() {
        for (int k = 0; k < POLE + 2; k++) {
            for (int[] q : new int[][] {{HX - k, HZ, 0}, {HX, HZ + k, 1}}) {
                if (q[0] < 0 || q[1] >= d || !field[q[0]][q[1]]) {
                    continue;
                }
                String g = get(q[0], EV, q[1]);
                String axis = q[2] == 0 ? "x" : "z";
                set(q[0], EV, q[1], (g != null && g.startsWith("bp_clay") ? "bp_line_clay" : "bp_line_grass") + "[axis=" + axis + "]");
            }
        }
        set(HX, EV, HZ, "bp_plate[facing=north]");
        set(HX - 13, EV, HZ + 13, "bp_rubber[axis=x]");
        set(HX - 27, EV + 1, HZ, "bp_base");
        set(HX - 27, EV + 1, HZ + 27, "bp_base");
        set(HX, EV + 1, HZ + 27, "bp_base");
        // the on-deck circles in front of each dugout
        for (int[] q : new int[][] {{HX - 14, HZ - 9}, {HX + 9, HZ + 14}}) {
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    set(q[0] + i, EV, q[1] + j, "bp_clay");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the grandstand

    private void stand(int x, int z) {
        int b = band[x][z];
        String in = in(x, z), out = opposite(in);
        int s = side(x, z), al = along(x, z);
        if (b == 0) {
            for (int y = EV + 1; y <= EV + 2; y++) {
                set(x, y, z, "bp_pad[facing=" + in + "]");
            }
            if (s == 2 || al <= 30) {
                // the backstop's netting on its posts
                for (int y = EV + 3; y <= EV + 9; y++) {
                    set(x, y, z, Math.floorMod(x + z, 8) == 0 ? "bp_column" : "bp_net");
                }
            } else {
                set(x, EV + 3, z, RAIL + "[facing=" + in + "]");
            }
            return;
        }
        if (b <= LOW_LAST) {
            int t = tread(b);
            set(x, t, z, chance() < 0.12 ? "st_tread_gum" : TREAD);
            int from = b < CORR0 ? EV + 1 : EV + 6;
            for (int y = from; y < t; y++) {
                set(x, y, z, FILL);
            }
            if (aisle(x, z)) {
                if (tread(b + 1) > t) {
                    set(x, t + 1, z, STEP + "[facing=" + out + "]");
                }
            } else if ((b - 1) % 2 == 1) {
                set(x, t + 1, z, seat(true, b) + "[facing=" + in + "]");
            }
        } else if (b <= CONC_LAST) {
            set(x, CONC, z, chance() < 0.1 ? "st_floor_b" : "st_floor");
            fill(x, EV + 6, z, x, CONC - 1, z, FILL);
            if (b == 19 && post(x, z)) {
                for (int y = CONC + 1; y < upper(19); y++) {
                    set(x, y, z, "bp_column");
                }
            }
        } else if (b <= FACADE) {
            set(x, CONC, z, FLOOR);
            fill(x, EV + 6, z, x, CONC - 1, z, FILL);
            if (b <= UNIT1) {
                // the units' ceiling, solid up to the rows over them
                fill(x, CONC + 5, z, x, upper(b) - 1, z, FILL);
            }
        }
        if (b >= FACADE) {
            return;
        }
        // the upper deck over the concourse and the units
        if (b == FASCIA) {
            for (int y = 5; y <= 8; y++) {
                set(x, y, z, "bp_green");
            }
            set(x, 9, z, RAIL + "[facing=" + in + "]");
        } else if (b >= UP_FIRST) {
            int t = upper(b);
            set(x, t, z, b > UP_LAST ? "st_floor" : chance() < 0.08 ? "st_tread_gum" : TREAD);
            if (b <= UP_LAST) {
                if (aisle(x, z)) {
                    if (upper(b + 1) > t) {
                        set(x, t + 1, z, STEP + "[facing=" + out + "]");
                    }
                } else if ((b - UP_FIRST) % 2 == 1) {
                    set(x, t + 1, z, seat(false, b) + "[facing=" + in + "]");
                }
            }
            if (b == 26 && post(x, z)) {
                for (int y = t + 1; y < ROOF; y++) {
                    set(x, y, z, "bp_column");
                }
            }
        }
        // the roof over the upper deck
        if (b >= 22) {
            set(x, ROOF, z, "st_roof");
            if (b == 22) {
                set(x, ROOF - 1, z, "bp_green");
            }
        }
    }

    /** Where the steel columns stand: every sixteen along the lines, every fifteen degrees behind home. */
    private boolean post(int x, int z) {
        int s = side(x, z);
        if (s < 2) {
            return Math.floorMod(along(x, z), 16) == 0 && along(x, z) > 0;
        }
        double psi = Math.toDegrees(Math.atan2(-c(z), -a(x))), r = Math.hypot(a(x), c(z));
        for (double k = 15; k < 90; k += 15) {
            if (Math.abs(psi - k) * Math.PI / 180 * r < 0.5) {
                return true;
            }
        }
        return false;
    }

    /** The lower rows are where the shelter's people sat out the waits; the upper mostly folded up. */
    private String seat(boolean low, int b) {
        double r = chance();
        String s = low ? (r < 0.5 ? "" : r < 0.66 ? "_up" : r < 0.76 ? "_mix" : r < 0.86 ? "_blanket" : r < 0.95 ? "_trash" : "_broken")
                : (r < 0.35 ? "" : r < 0.78 ? "_up" : r < 0.88 ? "_mix" : r < 0.94 ? "_trash" : r < 0.97 ? "_blanket" : "_broken");
        return "bp_seat" + s;
    }

    /** The dugouts sunk into the stands along each line, the tunnel back to the corridor. */
    private void dugouts() {
        for (int s = 0; s < 2; s++) {
            String in = s == 0 ? "south" : "west";
            for (int al = DUG0; al <= DUG1; al++) {
                for (int b = 0; b <= 5; b++) {
                    int[] q = at(s, al, b);
                    boolean end = al == DUG0 || al == DUG1;
                    set(q[0], EV, q[1], b == 0 ? "bp_clay" : FLOOR);
                    for (int y = EV + 1; y <= EV + 3; y++) {
                        set(q[0], y, q[1], end || b == 5 ? WALL : "air");
                    }
                    set(q[0], EV + 4, q[1], b == 0 ? "bp_green" : FILL);
                    if (b <= 1) {
                        set(q[0], EV + 5, q[1], b == 0 ? RAIL + "[facing=" + in + "]" : "air");
                    }
                    if (b == 0 && !end && (al < DUG0 + 3 || al > DUG1 - 3)) {
                        set(q[0], EV + 1, q[1], RAIL + "[facing=" + in + "]");
                    }
                }
                int[] bench = at(s, al, 4), rail = at(s, al, 1);
                if (al > DUG0 + 1 && al < DUG1 - 1 && al != TUNNEL && al != TUNNEL + 1) {
                    set(bench[0], EV + 1, bench[1], "st_bench[facing=" + in + "]");
                    int[] back = at(s, al, 5);
                    if (al % 3 == 0) {
                        set(back[0] + (s == 1 ? -1 : 0), EV + 2, back[1] + (s == 0 ? 1 : 0), (al % 6 == 0 ? "bp_bats" : "bp_helmets") + "[facing=" + in + "]");
                    }
                }
                if (al % 5 == 0 && chance() < 0.5) {
                    set(rail[0], EV + 1, rail[1], any("bp_balls", "trash_bag[facing=" + dir() + "]", "paper", "st_cooler[facing=" + in + "]"));
                }
            }
            // the tunnel back to the corridor
            for (int al = TUNNEL; al <= TUNNEL + 1; al++) {
                for (int b = 5; b < CORR0; b++) {
                    int[] q = at(s, al, b);
                    set(q[0], EV, q[1], FLOOR);
                    for (int y = EV + 1; y <= EV + 3; y++) {
                        set(q[0], y, q[1], "air");
                    }
                }
                for (int b = 5; b < CORR0; b++) {
                    for (int side2 : new int[] {TUNNEL - 1, TUNNEL + 2}) {
                        int[] q = at(s, side2, b);
                        for (int y = EV + 1; y <= EV + 3; y++) {
                            set(q[0], y, q[1], WALL);
                        }
                    }
                }
            }
            for (int al = DUG0 + 1; al < DUG1; al += 6) {
                int[] q = at(s, al, 5);
                set(q[0] + (s == 1 ? -1 : 0), EV + 3, q[1] + (s == 0 ? 1 : 0), (power && chance() < 0.4 ? "fit_troffer_on" : "fit_troffer") + "[axis=" + (s == 0 ? "x" : "z") + "]");
            }
            // the bullpen down the line: two mounds by the wall, the bench
            for (int k = 0; k < 2; k++) {
                int al = 70 + k * 8;
                int[] q = at(s, al, -5);
                for (int i = -1; i <= 1; i++) {
                    for (int j = -1; j <= 1; j++) {
                        set(q[0] + i, EV, q[1] + j, "bp_clay");
                    }
                }
                set(q[0], EV, q[1], "bp_rubber[axis=" + (s == 0 ? "z" : "x") + "]");
                int[] bn = at(s, al, -1);
                set(bn[0], EV + 1, bn[1], "st_bench[facing=" + in + "]");
            }
            // the infield tarp rolled up along the wall
            if (s == 0) {
                for (int al = 32; al < 62; al++) {
                    int[] q = at(s, al, -1);
                    set(q[0], EV + 1, q[1], "bp_tarp[axis=x]");
                }
            }
        }
    }

    /** Where the grandstand stops at the poles: its brick end walls, the corridor shut. */
    private void ends() {
        for (int s = 0; s < 2; s++) {
            for (int b = 1; b <= FACADE; b++) {
                int[] q = at(s, POLE, b);
                if (b >= CORR0) {
                    fill(q[0], EV + 1, q[1], q[0], EV + 5, q[1], WALL);
                }
                if (b >= FASCIA && b < UNIT0) {
                    fill(q[0], 6, q[1], q[0], Math.min(upper(Math.max(b, UP_FIRST)) + 2, ROOF), q[1], brick(q[0], 6, q[1]));
                } else if (b >= UNIT0) {
                    for (int y = CONC + 1; y <= ROOF + 1; y++) {
                        boolean pass = b >= PASS0 && b <= PASS1 && y <= 4;
                        set(q[0], y, q[1], pass ? "air" : brick(q[0], y, q[1]));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the outfield

    private void bleacher(int x, int z) {
        int b = band[x][z];
        String in = in(x, z), out = opposite(in);
        boolean eye = eye(x, z);
        if (b == 0) {
            for (int y = EV + 1; y <= EV + 2; y++) {
                set(x, y, z, (chance() < 0.25 ? "bp_ivy_bare" : "bp_ivy") + "[facing=" + in + "]");
            }
            set(x, EV + 3, z, eye ? "bp_eye" : RAIL + "[facing=" + in + "]");
            return;
        }
        if (b <= LOW_LAST) {
            int t = tread(b);
            for (int y = EV + 1; y < t; y++) {
                set(x, y, z, FILL);
            }
            if (eye) {
                set(x, t, z, "bp_eye");
                for (int y = t + 1; y <= (b <= 2 ? CONC + 2 : t); y++) {
                    set(x, y, z, "bp_eye");
                }
                return;
            }
            set(x, t, z, TREAD);
            if (aisle(x, z)) {
                if (tread(b + 1) > t) {
                    set(x, t + 1, z, STEP + "[facing=" + out + "]");
                }
            } else if ((b - 1) % 2 == 1) {
                set(x, t + 1, z, (chance() < 0.08 ? "sc_bleacher_frame" : "sc_bleacher_alu") + "[facing=" + in + "]");
            }
            return;
        }
        if (b < BACK) {
            set(x, CONC, z, "st_floor");
            return;
        }
        if (b == BACK) {
            boolean gap = aisle(x, z);
            int a = a(x), c = c(z);
            double t = Math.toDegrees(Math.atan2(c, a));
            int col = (int) Math.floor(Math.toRadians(t + 30) * Math.hypot(a, c));
            String[] ads = {"cola", "savings", "motors", "brew", "radio"};
            if (gap || eye) {
                return;
            }
            set(x, CONC + 1, z, brick(x, 1, z));
            set(x, CONC + 2, z, "bp_ad_" + ads[Math.floorMod(Math.floorDiv(col, 5), ads.length)] + "_" + Math.min(Math.floorMod(col, 5), 3)
                    + "[facing=" + in + "]");
            set(x, CONC + 3, z, "bp_green");
        }
    }

    // ------------------------------------------------------------------ the brick wall

    /** A brick, weathered by hash so whole patches go together: soot high, moss low, the old stains. */
    String brick(int x, int y, int z) {
        long h = (x / 3) * 73856093L ^ (y / 2) * 19349663L ^ (z / 3) * 83492791L;
        int k = (int) Math.floorMod(h, 100);
        if (k < 60) {
            return Math.floorMod(x + z + y, 3) == 0 ? "brick_red_b" : "brick_red";
        }
        if (k < 72) {
            return y > 14 ? "brick_red_soot" : "brick_red_stain";
        }
        if (k < 82) {
            return y < 5 ? "brick_red_moss" : "brick_red";
        }
        if (k < 90) {
            return "brick_red_crack";
        }
        return y < 10 && chance() < 0.3 ? "brick_red_holes" : "brick_red_b";
    }

    /** The outer wall at band 38 round the grandstand: the arcade at the street, the arched windows above, the gates. */
    private void facade() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (band[x][z] != FACADE || !grand(x, z)) {
                    continue;
                }
                String out = opposite(in(x, z));
                int s = side(x, z);
                int k = s < 2 ? along(x, z) : (int) Math.round(Math.toRadians(Math.toDegrees(Math.atan2(-c(z), -a(x)))) * Math.hypot(a(x), c(z)));
                boolean gate = false;
                for (int g : GATES) {
                    gate |= s < 2 && k >= g && k < g + 4;
                }
                int m = Math.floorMod(k, 4);
                boolean flat = s < 2;
                for (int y = CONC + 1; y <= ROOF + 1; y++) {
                    String b = brick(x, y, z);
                    if (y == 5 || y == 11 || y == 17) {
                        b = "brick_dark";
                    } else if (y == ROOF) {
                        b = "bp_green";
                    } else if (gate && y <= 4) {
                        b = "air";
                    } else if (gate && y == 5) {
                        b = "bp_green";
                    } else if (m >= 2 && y <= 3) {
                        b = flat ? "st_grille[facing=" + out + "]" : "st_grille[facing=" + out + "]";
                    } else if (m >= 2 && y == 4 && flat) {
                        b = archTile(x, z, out, k);
                    } else if (m >= 2 && y >= 7 && y <= 9) {
                        b = (chance() < 0.12 ? "st_glazing_broken" : "st_glazing") + "[facing=" + out + "]";
                    } else if (m >= 2 && y == 10 && flat) {
                        b = archTile(x, z, out, k);
                    } else if (Math.floorMod(k, 8) >= 5 && Math.floorMod(k, 8) <= 6 && y >= 19 && y <= 21) {
                        b = "st_glazing[facing=" + out + "]";
                    }
                    set(x, y, z, b);
                }
            }
        }
        // the name over the main gate on the first-base side, the tickets either side
        int g = GATES[0];
        for (int i = 0; i < 12; i++) {
            for (int r = 0; r < 2; r++) {
                int al = g - 4 + i;                   // the viewer outside (north) has the east, the near end along, on the left
                int[] q = at(0, al, FACADE + 1);
                set(q[0], 7 - r, q[1], "bp_gate_" + (r * 12 + i) + "[facing=north]");
            }
        }
        for (int s = 0; s < 2; s++) {
            for (int gg : GATES) {
                for (int al : new int[] {gg - 2, gg + 5}) {
                    int[] q = at(s, al, FACADE);
                    String out = s == 0 ? "north" : "east";
                    set(q[0], 2, q[1], "st_ticket_" + (1 + rng.nextInt(4)) + "[facing=" + out + "]");
                    int[] o = at(s, al, FACADE + 1);
                    set(o[0], 1, o[1], "st_ticket_ledge[facing=" + out + "]");
                }
                int[] sg = at(s, gg + 1, FACADE + 1);
                if (!(s == 0 && gg == GATES[0])) {
                    sign("tickets", Math.min(sg[0], at(s, gg + 2, FACADE + 1)[0]), 6, Math.min(sg[1], at(s, gg + 2, FACADE + 1)[1]), s == 0 ? "north" : "east");
                }
            }
        }
    }

    /** The two halves of an arch's head over the opening at k (its first column at k mod 4 = 2). */
    private String archTile(int x, int z, String out, int k) {
        // tile 0 is the viewer's left: outside the north wall that's the east (the lower along, as along
        // runs west there), outside the east wall the south (the higher along)
        boolean first = Math.floorMod(k, 4) == 2;
        String side = out.equals("north") ? (first ? "0" : "1") : (first ? "1" : "0");
        return "bp_arch_" + side + "[facing=" + out + "]";
    }

    // ------------------------------------------------------------------ the fire stairs

    /**
     * A fire stair in its own shaft, five along by ten deep, turned k quarters: its door in the wall
     * at its local z = 0 (outward), a landing at every level in ys, a hut on the roof.
     */
    void core(int ox, int oz, int k, int[] ys) {
        Placer q = p.turned(ox, 0, oz, k);
        int top = ys[ys.length - 1] + 5;
        for (int y = ys[0] + 1; y < top; y++) {
            for (int lx = 1; lx <= 3; lx++) {
                for (int lz = 1; lz <= 8; lz++) {
                    q.set(lx, y, lz, "air");
                }
            }
        }
        for (int y = ys[0]; y <= top; y++) {
            for (int lz = 0; lz <= 9; lz++) {
                q.set(0, y, lz, WALL);
                q.set(4, y, lz, WALL);
            }
            for (int lx = 0; lx <= 4; lx++) {
                q.set(lx, y, 0, WALL);
                q.set(lx, y, 9, WALL);
            }
        }
        for (int lx = 0; lx <= 4; lx++) {
            for (int lz = 0; lz <= 9; lz++) {
                q.set(lx, top, lz, "un_roof");
            }
        }
        int[] last = Stairwell.build(q, rng, 1, 0, ys, false, 0, power ? 0.4 : 0.0);
        Stairwell.capTop(q, last);
        for (int y : ys) {
            if (y < ys[ys.length - 1]) {
                q.set(1, y + 3, -1, "fit_st_escape[facing=north]");
            }
        }
    }

    // ------------------------------------------------------------------ the scoreboard and the lights

    /** The hand-turned board over the right-field bleachers, on its steel legs, the pennants over it. */
    private void scoreboard() {
        int x0 = 20, z0 = 96, y0 = 7;           // its front faces east, home's way: tile 0 (the viewer's left) at the south end
        for (int i = 0; i < 8; i++) {
            for (int r = 0; r < 6; r++) {
                set(x0, y0 + 5 - r, z0 + 7 - i, "bp_sb_" + (r * 8 + i) + "[facing=east]");
            }
            set(x0 - 1, y0 + 6, z0 + i, "bp_sb_house");
            set(x0, y0 + 6, z0 + i, "bp_sb_house");
            for (int y = y0; y <= y0 + 5; y++) {
                set(x0 - 1, y, z0 + i, "bp_sb_house");
            }
        }
        for (int i : new int[] {0, 7}) {
            for (int y = 1; y < y0; y++) {
                set(x0 - 1, y, z0 + i, "bp_column");
            }
        }
        // the ladder up the back and the pennants on their poles
        for (int y = 1; y <= y0 + 6; y++) {
            set(x0 - 2, y, z0 + 3, "ladder[facing=west]");
        }
        String[] flags = {"a", "b", "c"};
        for (int f = 0; f < 3; f++) {
            int z = z0 + 1 + f * 3;
            for (int y = y0 + 7; y <= y0 + 11; y++) {
                set(x0, y, z, "cv_flagpole");
            }
            set(x0, y0 + 10, z - 1, "bp_flag_" + flags[f] + "_0[facing=east]");
            set(x0, y0 + 10, z - 2, "bp_flag_" + flags[f] + "_1[facing=east]");
        }
    }

    /** The light towers: on the roof's front over each line, and two behind the bleachers. */
    private void lights() {
        List<int[]> spots = new ArrayList<>();
        for (int s = 0; s < 2; s++) {
            for (int al : new int[] {28, 68}) {
                int[] q = at(s, al, 23);
                spots.add(new int[] {q[0], q[1], ROOF + 1});
            }
        }
        spots.add(new int[] {24, 70, CONC + 1});
        spots.add(new int[] {100, 190, CONC + 1});
        for (int[] q : spots) {
            String in = in(q[0], q[1]);
            int top = q[2] + 22;
            for (int y = q[2]; y < top; y++) {
                set(q[0], y, q[1], "st_lt_mast");
            }
            boolean ns = in.equals("north") || in.equals("south");
            for (int i = -2; i <= 2; i++) {
                for (int j = 0; j < 3; j++) {
                    int x = ns ? q[0] + i : q[0], z = ns ? q[1] : q[1] + i;
                    set(x, top + j, z, (power && chance() < 0.3 ? "st_floods_on" : "st_floods") + "[facing=" + in + "]");
                }
            }
        }
    }
}
