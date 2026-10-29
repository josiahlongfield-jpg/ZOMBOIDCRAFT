package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Grayhaven Stadium, home of the Grayhaven Gales: an open-air football bowl at true scale, about
 * seventy thousand seats. The bowl and the seats were checked against the first photos in the
 * library (refs/city/stadium-bowl-wide, stadium-seats-aisles: the abandoned Aloha Stadium's faded
 * rows, numbered seat backs, the upper deck's soffit); the outside follows text research on the
 * concrete bowls of the seventies and eighties (spiral ramp towers at the corners, precast piers,
 * gate canopies, ticket windows) until refs/city/stadium-exterior-gates fills. It is drawn round a
 * field 90 x 140 (the playing field, the sidelines and the end lines) whose edge is a rounded
 * rectangle:
 * <ul>
 * <li>the lower bowl: a padded wall round the field, the front walkway behind its glass, then 21
 * rows of seats two deep (the seat and the leg room in front of it) rising one a row, cut by radial
 * aisles and by vomitories that come up out of the ground concourse to the front walkway;</li>
 * <li>the club level at the top of it: the cross-aisle, the suites behind their glass with their
 * balcony seats, the club corridor, the lounges and, out to the facade, the 200 concourse;</li>
 * <li>the upper bowl over it: the ribbon board on its fascia, 25 rows rising one, one, two (steeper
 * the higher you go), the 300 concourse under its back rows with vomitories up into it;</li>
 * <li>the ground concourse under the lower bowl, the open undercroft under the club and the upper
 * concourse out to the facade, eight stair cores and the escalators at the gates; the canopy over
 * the top rows with its floodlights; the team tunnel and the service tunnel trucks drive in by.</li>
 * </ul>
 * The section runs in bands b out from the field's edge: the lower rows at b 3..44, the club
 * cross-aisle at 45, the suites at 46..55, the upper rows at 49..98, the facade at 99..100 with its
 * piers and gate canopies standing out to 106, a spiral ramp tower outside each corner with its
 * bridges to the 200 and 300 concourses.
 *
 * It ended as the county's evacuation centre: FEMA's tents and cots on the field, triage under the
 * stands, the intake at gates A and C with the queues, the barriers and the buses, the overflow
 * camp out in the south-west lot, and then the night it was overrun.
 */
final class Stadium {
    /** The field's edge: a rounded rectangle 2HX x 2HZ about the centre, corners of radius RC. */
    static final int HX = 45, HZ = 70, RC = 24, AX = HX - RC, AZ = HZ - RC;
    /** The facade's two bands; the footprint reaches EX, EZ either side of the centre. */
    static final int FACADE = 99, EX = HX + FACADE + 2, EZ = HZ + FACADE + 2;

    // the section: bands out from the field's edge, and the levels
    static final int WALKWAY = 3, LOW_FIRST = 3;
    static final int XAISLE = 45, CLUB = 24, CLUB_CEIL = 31;
    static final int GLASS = 48, SUITE_BACK = 56, CORRIDOR_WALL = 62, LOUNGE_WALL = 73;
    static final int UP_WALK = 34, UP_FIRST = 49, UP_LAST = 98;
    static final int UPPER = 40, UPPER_CEIL = 49, UPPER_IN = 73;
    static final int CONC_IN = 15, CONC_WALL = 48, CONC_CEIL = 9;
    static final int ROOF = 76, ROOF_IN = 80, TOP = 77;

    /** The lower bowl's tread at band b (0..44): the front walkway, then rows two deep rising one. */
    static int lowTread(int b) {
        return b < LOW_FIRST ? WALKWAY : 4 + (b - LOW_FIRST) / 2;
    }

    /** The upper bowl's tread at band b (46..98): its front walkway, then rows rising one, one, two. */
    static int upTread(int b) {
        if (b < UP_FIRST) {
            return UP_WALK;
        }
        int r = (b - UP_FIRST) / 2;
        return UP_WALK + r + r / 3;
    }

    static int tread(int b) {
        return b <= 44 ? lowTread(b) : b == XAISLE ? CLUB : upTread(b);
    }

    /** A line straight out from the field's edge: its foot on the edge and its direction. */
    record Ray(double bx, double bz, double nx, double nz) {
        /** Signed distance across the ray. */
        double lat(double px, double pz) {
            return (px - bx) * nz - (pz - bz) * nx;
        }

        /** Distance out along the ray. */
        double along(double px, double pz) {
            return (px - bx) * nx + (pz - bz) * nz;
        }

        double x(double along) {
            return bx + nx * along;
        }

        double z(double along) {
            return bz + nz * along;
        }
    }

    /** The ray out of a straight side (0 north, 1 east, 2 south, 3 west) at t along it. */
    static Ray side(int side, double t) {
        return switch (side) {
            case 0 -> new Ray(t, -HZ, 0, -1);
            case 1 -> new Ray(HX, t, 1, 0);
            case 2 -> new Ray(t, HZ, 0, 1);
            default -> new Ray(-HX, t, -1, 0);
        };
    }

    /** The ray out of the corner (sx, sz = +-1) at deg degrees round from the x axis. */
    static Ray corner(int sx, int sz, double deg) {
        double a = Math.toRadians(deg), c = Math.cos(a), s = Math.sin(a);
        return new Ray(sx * (AX + RC * c), sz * (AZ + RC * s), sx * c, sz * s);
    }

    /** Distance out from the field's edge (negative on the field). */
    static double edge(double px, double pz) {
        double qx = Math.abs(px) - AX, qz = Math.abs(pz) - AZ;
        return Math.hypot(Math.max(qx, 0), Math.max(qz, 0)) + Math.min(Math.max(qx, qz), 0) - RC;
    }

    /** Which way is out, away from the field (0 north, 1 east, 2 south, 3 west). */
    static int outward(double px, double pz) {
        double qx = Math.abs(px) - AX, qz = Math.abs(pz) - AZ;
        double nx, nz;
        if (qx > 0 && qz > 0) {
            nx = qx * Math.signum(px);
            nz = qz * Math.signum(pz);
        } else if (qx > qz) {
            nx = Math.signum(px);
            nz = 0;
        } else {
            nx = 0;
            nz = Math.signum(pz);
        }
        return Math.abs(nx) >= Math.abs(nz) ? (nx > 0 ? 1 : 3) : (nz > 0 ? 2 : 0);
    }

    static final String[] DIRS = {"north", "east", "south", "west"};

    static final String FILL = "concrete_dark", TREAD = "st_tread", SLAB = "floor_concrete", CEIL = "concrete_panel",
            WALL = "concrete_panel", FACADE_WALL = "concrete_panel", STAIR = "st_step", RAIL = "st_rail",
            GLASS_RAIL = "st_rail_glass", PAD = "st_pad";

    private final BlockSink out;
    private final Placer p;
    private final SplittableRandom rng;
    /** The field's centre in the place's coordinates: the field runs cx - 45 .. cx + 44. */
    private final int cx, cz;
    private final List<Ray> lowAisles = new ArrayList<>(), upAisles = new ArrayList<>();
    private final List<Ray> lowVoms = new ArrayList<>(), upVoms = new ArrayList<>();
    private final List<Ray> gates = new ArrayList<>(), cornerGates = new ArrayList<>(), cores = new ArrayList<>();
    private final List<Ray> ramps = new ArrayList<>();
    private final Ray teamTunnel = side(3, 0), serviceTunnel = side(0, 0);

    Stadium(BlockSink out, SplittableRandom rng, int cx, int cz) {
        this.out = out;
        this.p = new Placer(out, 0, 0, 0, 0);
        this.rng = rng;
        this.cx = cx;
        this.cz = cz;
        this.wearSeed = rng.nextLong();
        for (int s = 1; s <= 3; s += 2) {
            for (double t : new double[] {-32, -19, -6, 6, 19, 32}) {
                lowAisles.add(side(s, t));
                upAisles.add(side(s, t));
            }
            for (double t : new double[] {-25.5, 25.5}) {
                lowVoms.add(side(s, t));
                upVoms.add(side(s, t));
            }
            upVoms.add(side(s, 0));
            gates.add(side(s, 0));
            cores.add(side(s, -18));
            cores.add(side(s, 18));
        }
        lowVoms.add(side(1, 0));
        for (int s = 0; s <= 2; s += 2) {
            for (double t : new double[] {-8, 8}) {
                lowAisles.add(side(s, t));
                upAisles.add(side(s, t));
            }
            upVoms.add(side(s, 0));
            gates.add(side(s, 0));
            cores.add(side(s, -14));
            cores.add(side(s, 14));
        }
        lowVoms.add(side(2, 0));
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int k = 0; k < 5; k++) {
                    lowAisles.add(corner(sx, sz, (k + 0.5) * 18));
                }
                for (int k = 0; k < 9; k++) {
                    upAisles.add(corner(sx, sz, (k + 0.5) * 10));
                }
                lowVoms.add(corner(sx, sz, 18));
                lowVoms.add(corner(sx, sz, 72));
                for (double a : new double[] {20, 50, 80}) {
                    upVoms.add(corner(sx, sz, a));
                }
                cornerGates.add(corner(sx, sz, 45));
                ramps.add(corner(sx, sz, RAMP_DEG));
            }
        }
    }

    private double chance() {
        return rng.nextDouble();
    }

    private void set(int x, int y, int z, String block) {
        out.set(x, y, z, block);
    }

    /** Within half of a ray (and out along it from the field's edge). */
    private static Ray near(List<Ray> rays, double px, double pz, double half) {
        for (Ray r : rays) {
            if (r.along(px, pz) > -1 && Math.abs(r.lat(px, pz)) <= half) {
                return r;
            }
        }
        return null;
    }

    private static boolean on(Ray r, double px, double pz, double half) {
        return r.along(px, pz) > -1 && Math.abs(r.lat(px, pz)) <= half;
    }

    /** The direction across a ray toward its middle from a column beside it. */
    private static String toward(Ray r, double px, double pz) {
        double l = r.lat(px, pz);
        // the lateral axis is (nz, -nx): step back across it
        double dx = -Math.signum(l) * r.nz(), dz = Math.signum(l) * r.nx();
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    void build() {
        shell();
        undercroft();
        concourse();
        club();
        facade();
        ramps();
        canopy();
        boards();
        checkpoints();
        overflowCamp();
        fieldShelter();
        cores();
        roofStairs();
    }

    // ------------------------------------------------------------------ the shell

    /** Every column of the bowl, the levels and the facade, then what is cut out of them. */
    private void shell() {
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5;
                double dd = edge(px, pz);
                if (dd < 0) {
                    set(x, 0, z, field(x - cx, z - cz));
                    continue;
                }
                int b = (int) dd;
                if (b > FACADE + 1) {
                    continue;
                }
                column(x, z, px, pz, b);
            }
        }
    }

    private void column(int x, int z, double px, double pz, int b) {
        int o = outward(px, pz);
        String in = DIRS[(o + 2) & 3], outw = DIRS[o];
        set(x, 0, z, SLAB);
        // the lower bowl: a solid wedge of rows
        if (b <= 44) {
            int t = lowTread(b);
            for (int y = 1; y < t; y++) {
                set(x, y, z, FILL);
            }
            set(x, t, z, tread());
            if (b == 0) {
                wallPad(x, z, px, pz, in);
                set(x, 4, z, GLASS_RAIL + "[facing=" + in + "]");
            }
        }
        if (b == XAISLE) {
            for (int y = CONC_CEIL + 1; y < CLUB; y++) {
                set(x, y, z, FILL);
            }
            set(x, CLUB, z, TREAD);
        }
        // the ground concourse under the bowl
        if (b >= CONC_IN && b <= CONC_WALL - 1) {
            for (int y = 1; y < CONC_CEIL; y++) {
                set(x, y, z, "air");
            }
            set(x, CONC_CEIL, z, CEIL);
        }
        if (b == CONC_WALL) {
            for (int y = 1; y <= CONC_CEIL; y++) {
                set(x, y, z, WALL);
            }
        }
        // the club level, its ceiling, the upper bowl's fascia and rows
        if (b > XAISLE && b < FACADE) {
            set(x, CLUB, z, SLAB);
            if (b <= LOUNGE_WALL) {
                set(x, CLUB_CEIL, z, CEIL);
            }
            int t = upTread(b);
            int from = b < UPPER_IN ? CLUB_CEIL + 1 : UPPER_CEIL;
            if (b >= UPPER_IN) {
                set(x, UPPER, z, SLAB);
            }
            for (int y = from; y < t; y++) {
                set(x, y, z, FILL);
            }
            set(x, t, z, tread());
            if (b == XAISLE + 1) {
                set(x, CLUB + 1, z, GLASS_RAIL + "[facing=" + in + "]");
                set(x, UP_WALK + 1, z, GLASS_RAIL + "[facing=" + in + "]");
                ribbon(x, z, px, pz, in);
            }
            if (b == LOUNGE_WALL) {
                for (int y = CLUB + 1; y < UPPER; y++) {
                    set(x, y, z, WALL);
                }
            }
            if (b > LOUNGE_WALL) {
                set(x, CLUB_CONC_CEIL, z, "concrete_dark");
                for (int y = CLUB_CONC_CEIL + 1; y < UPPER; y++) {
                    set(x, y, z, FILL);
                }
            }
        }
        // the facade, up past the top rows to carry the canopy
        if (b >= FACADE) {
            for (int y = 0; y <= TOP; y++) {
                set(x, y, z, FACADE_WALL);
            }
        }
        if (b >= ROOF_IN && b <= FACADE + 1) {
            set(x, ROOF, z, "st_roof");
        }
        cuts(x, z, px, pz, b, in, outw);
        if (b <= 44 || b > XAISLE && b < FACADE) {
            seats(x, z, px, pz, b, in, outw);
        }
    }

    /** The vomitories, the tunnels, the gates. */
    private void cuts(int x, int z, double px, double pz, int b, String in, String outw) {
        // the lower vomitories: level with the front walkway, three steps down into the concourse
        if (b >= LOW_FIRST && b < CONC_IN && near(lowVoms, px, pz, 2) != null) {
            int t = lowTread(b);
            if (b <= 11) {
                set(x, WALKWAY, z, TREAD);
                for (int y = WALKWAY + 1; y <= (b <= 8 ? t : 6); y++) {
                    set(x, y, z, "air");
                }
            } else {
                // each step's top half a block under the one before, the first flush with the walkway
                int f = WALKWAY + 1 - (b - 11);
                set(x, f, z, STAIR + "[facing=" + in + "]");
                for (int y = f + 1; y <= Math.min(t - 1, 8); y++) {
                    set(x, y, z, "air");
                }
            }
        }
        // the 300 vomitories: level with row 5, open over rows 6 and 7, then under the rows
        if (b >= 59 && b < UPPER_IN && near(upVoms, px, pz, 2) != null) {
            int t = upTread(b);
            set(x, UPPER, z, b <= 60 ? TREAD : SLAB);
            for (int y = UPPER + 1; y <= (b <= 64 ? t : UPPER + 3); y++) {
                set(x, y, z, "air");
            }
        }
        // the team tunnel off the west sideline, the service tunnel under the north end
        if (b < CONC_IN && on(teamTunnel, px, pz, 3)) {
            for (int y = 1; y <= Math.max(3, b < LOW_FIRST ? 4 : 3); y++) {
                set(x, y, z, "air");
            }
        }
        if (b < FACADE && on(serviceTunnel, px, pz, 4)) {
            for (int y = 1; y <= (b <= 6 ? 6 : 5); y++) {
                set(x, y, z, "air");
            }
        }
        // gates through the concourse wall and the facade
        if (b == CONC_WALL) {
            Ray g = near(gates, px, pz, 5);
            int h = g != null ? 5 : near(cornerGates, px, pz, 3) != null ? 4 : 0;
            if (on(serviceTunnel, px, pz, 4)) {
                h = 6;
            }
            for (int y = 1; y <= h; y++) {
                set(x, y, z, "air");
            }
        }
        if (b >= FACADE) {
            int h = near(gates, px, pz, 8) != null ? 10 : near(cornerGates, px, pz, 5) != null ? 8 : 0;
            for (int y = 1; y <= h; y++) {
                set(x, y, z, "air");
            }
        }
    }

    /** Seats on the rows, steps up the aisles, rails round the pits. */
    private void seats(int x, int z, double px, double pz, int b, String in, String outw) {
        boolean low = b <= 44;
        List<Ray> aisles = low ? lowAisles : upAisles;
        int t = tread(b);
        // nothing on the rows the cuts took
        if (low && b < CONC_IN && near(lowVoms, px, pz, 2) != null || !low && b >= 59 && b < UPPER_IN && near(upVoms, px, pz, 2) != null
                || low && b < CONC_IN && on(teamTunnel, px, pz, 3) || low && b <= 6 && on(serviceTunnel, px, pz, 4)) {
            return;
        }
        // rails beside the pits
        Ray pit = low ? (b <= 8 ? near(lowVoms, px, pz, 3) : null) : (b >= 61 && b <= 64 ? near(upVoms, px, pz, 3) : null);
        if (pit != null) {
            set(x, t + 1, z, RAIL + "[facing=" + toward(pit, px, pz) + "]");
            return;
        }
        if (low && b <= 2 && on(teamTunnel, px, pz, 4)) {
            set(x, t + 1, z, RAIL + "[facing=" + toward(teamTunnel, px, pz) + "]");
            return;
        }
        if (low && b <= 6 && on(serviceTunnel, px, pz, 5)) {
            set(x, t + 1, z, RAIL + "[facing=" + toward(serviceTunnel, px, pz) + "]");
            return;
        }
        // over the tunnel and vomitory mouths
        if (low && b == 9 && near(lowVoms, px, pz, 2.5) != null || low && b == 3 && on(teamTunnel, px, pz, 3.5)
                || low && b == 7 && on(serviceTunnel, px, pz, 4.5) || !low && b == 65 && near(upVoms, px, pz, 2.5) != null) {
            set(x, t + 1, z, RAIL + "[facing=" + in + "]");
            return;
        }
        int first = low ? LOW_FIRST : UP_FIRST, last = low ? 44 : UP_LAST;
        boolean aisle = near(aisles, px, pz, 1) != null;
        if (aisle && b == first - 1 && tread(first) > t) {
            // the step up off the front walkway onto the first row
            set(x, t + 1, z, STAIR + "[facing=" + outw + "]");
        }
        if (b < first) {
            return;
        }
        boolean seatCell = (b - first) % 2 == 1;
        if (aisle) {
            if (b < last && tread(b + 1) > t) {
                set(x, t + 1, z, STAIR + "[facing=" + outw + "]");
            }
            if (b > first && t - tread(b - 1) == 2) {
                set(x, t, z, STAIR + "[facing=" + outw + "]");
            }
            return;
        }
        if (seatCell) {
            set(x, t + 1, z, seat(low, b) + "[facing=" + in + "]");
        }
    }

    private String tread() {
        return chance() < 0.18 ? "st_tread_gum" : TREAD;
    }

    /**
     * A seat block for the bowl: the lower bowl is where the shelter's people slept (blankets,
     * the seats down, rubbish), the upper bowl mostly folded up and sun-bleached toward the top.
     */
    private String seat(boolean low, int b) {
        double r = chance();
        String c = !low && b > 86 && chance() < 0.6 ? "f" : "b";
        String s = low ? (r < 0.5 ? "" : r < 0.62 ? "_up" : r < 0.72 ? "_mix" : r < 0.86 ? "_blanket" : r < 0.96 ? "_trash" : "_broken")
                : (r < 0.3 ? "" : r < 0.72 ? "_up" : r < 0.86 ? "_mix" : r < 0.92 ? "_trash" : r < 0.96 ? "_blanket" : "_broken");
        return "st_seat_" + c + s;
    }

    // ------------------------------------------------------------------ pictures along the bowl

    private static final String[] ADS = {"a", "b", "c", "d"};

    /** Where along a straight side a column is, counted toward the right of someone facing it. */
    private static int along(int x, int z, String facing) {
        return switch (facing) {
            case "east" -> -z;   // the west wall, read facing west: south is the left
            case "west" -> z;
            case "north" -> -x;
            default -> x;
        };
    }

    private static boolean straight(double px, double pz) {
        return Math.abs(px) <= AX || Math.abs(pz) <= AZ;
    }

    /** The field wall: sponsors' panels four wide along the straight runs, padding between. */
    private void wallPad(int x, int z, double px, double pz, String in) {
        if (!straight(px, pz)) {
            set(x, 1, z, PAD + "[facing=" + in + "]");
            set(x, 2, z, PAD + "[facing=" + in + "]");
            return;
        }
        int a = along(x, z, in), slot = Math.floorDiv(a, 6), col = Math.floorMod(a, 6);
        if (col >= 4) {
            set(x, 1, z, PAD + "[facing=" + in + "]");
            set(x, 2, z, PAD + "[facing=" + in + "]");
            return;
        }
        String k = ADS[Math.floorMod(slot, 4)];
        set(x, 2, z, "st_ad_" + k + "_" + col + "[facing=" + in + "]");
        set(x, 1, z, "st_ad_" + k + "_" + (col + 4) + "[facing=" + in + "]");
    }

    /** The ribbon board along the upper deck's fascia: lit along the sides, dead round the ends. */
    private void ribbon(int x, int z, double px, double pz, String in) {
        boolean sides = Math.abs(pz) <= AZ;
        if (!sides) {
            set(x, UP_WALK - 1, z, "st_ribbon_off[facing=" + in + "]");
            set(x, UP_WALK - 2, z, "st_ribbon_off[facing=" + in + "]");
            return;
        }
        int col = Math.floorMod(along(x, z, in), 16);
        String k = Math.floorMod(Math.floorDiv(along(x, z, in), 16), 3) == 2 ? "b" : "a";
        set(x, UP_WALK - 1, z, "st_ribbon_" + k + "_" + col + "[facing=" + in + "]");
        set(x, UP_WALK - 2, z, "st_ribbon_" + k + "_" + (col + 16) + "[facing=" + in + "]");
    }

    // ------------------------------------------------------------------ the field

    /** Yards to blocks. */
    static final double YD = 0.9144;
    /** The field of play's half width (cells -24 .. 23), the goal lines and end lines from the centre. */
    static final int SIDE = 24;

    /** The cell a yard line falls in (from the centre, south positive) and the quarter of it. */
    private static int[] yardCell(double yards) {
        double z = yards * YD;
        int cell = (int) Math.floor(z);
        return new int[] {cell, Math.min(3, (int) ((z - cell) * 4))};
    }

    /**
     * The turf at a cell of the field (lx, lz from the centre): mown in five-yard bands, the
     * six-foot white border, the yard lines and hash marks, the numbers read from each sideline,
     * the end zones painted storm blue with GALES across them.
     */
    private String field(int lx, int lz) {
        boolean width = lx >= -SIDE && lx < SIDE;
        boolean north = lz >= -55 && lz <= -47, south = lz >= 46 && lz <= 54;
        if ((lx == -SIDE - 2 || lx == -SIDE - 1 || lx == SIDE || lx == SIDE + 1) && lz >= -57 && lz <= 56
                || width && (lz == -57 || lz == -56 || lz == 55 || lz == 56)) {
            return "st_turf_white";
        }
        if (!width || lz < -57 || lz > 56) {
            return "sc_turf";
        }
        if (north || south) {
            // GALES, read from the field: the north one facing north, the south one facing south
            int col = north ? lx + 15 : 14 - lx, row = north ? lz + 54 : 53 - lz;
            if (col >= 0 && col < 30 && row >= 0 && row < 6) {
                return "st_ez_" + (row * 30 + col) + "[facing=" + (north ? "north" : "south") + "]";
            }
            return "st_ez";
        }
        // the numbers: two rows nine and ten yards in from each sideline
        boolean west = lx == -15 || lx == -14, east = lx == 13 || lx == 14;
        for (int yards = -40; yards <= 40; yards += 10) {
            int[] c = yardCell(yards);
            if ((west || east) && Math.abs(lz - c[0]) <= 1) {
                int n = (50 - Math.abs(yards)) / 10;
                String a = n == 5 ? "x" : (yards < 0) == west ? "l" : "r";
                int col = west ? lz - c[0] + 1 : c[0] + 1 - lz, row = west ? (lx == -14 ? 0 : 1) : (lx == 13 ? 0 : 1);
                if (col == 1) {
                    int q = west ? c[1] : 3 - c[1];
                    return "st_num_" + n + "m" + q + "_" + (row * 3 + 1) + "[facing=" + (west ? "east" : "west") + "]";
                }
                return "st_num_" + n + a + "_" + (row * 3 + col) + "[facing=" + (west ? "east" : "west") + "]";
            }
        }
        for (int yards = -50; yards <= 50; yards += 5) {
            int[] c = yardCell(yards);
            if (lz == c[0]) {
                return "st_yard_" + c[1];
            }
        }
        // hash marks every yard: the inbounds pair in the middle and the ticks along each sideline
        if (lx == -3 || lx == 2 || lx == -SIDE || lx == SIDE - 1) {
            for (int yards = -49; yards <= 49; yards++) {
                int[] c = yardCell(yards);
                if (yards % 5 != 0 && lz == c[0]) {
                    return "st_hash_" + c[1];
                }
            }
        }
        int band = (int) Math.floor((lz + 0.5 + 45.72) / (5 * YD));
        return band % 2 == 0 ? "sc_turf" : "sc_turf_d";
    }

    // ------------------------------------------------------------------ the ground concourse

    /** The ground concourse's bands: the rooms under the rows, the walk, the rooms along its wall. */
    static final int IN_BACK = CONC_IN, IN_FRONT = 23, OUT_FRONT = 38, OUT_BACK = CONC_WALL - 1;
    /** The rooms' drop ceilings; solid block above them up to the concourse's own. */
    static final int ROOM_CEIL = 4;
    /** The 200 concourse's ceiling, out past the lounges; solid structure over it to the 300 concourse. */
    static final int CLUB_CONC_CEIL = 32;
    /** The undercroft hall's slab, under the structure that carries the club. */
    static final int HALL = 12;

    private static final String[] FOOD = {"grill", "pizza", "brew", "sweet", "nacho"};
    private static final String[] INNER = {"stand", "men", "stand", "stand", "women", "stand"};
    private static final String[] OUTER = {"women", "store", "men", "aid", "stand", "men", "stand", "women"};
    private static final String[] CLUB_ROOMS = {"stand", "women", "stand", "men", "stand"};
    private static final String[] UPPER_INNER = {"stand", "stand", "men", "stand", "women"};
    private static final String[] UPPER_OUTER = {"women", "men", "stand", "men", "women"};
    private static final Map<String, String[]> LINE = Map.of(
            "grill", new String[] {"dn_griddle", "dn_griddle", "dn_fryer", "dn_fryer", "st_warmer", "dn_prep", "st_fountain"},
            "pizza", new String[] {"store_oven", "store_oven", "dn_prep", "dn_prep", "st_warmer", "dn_lowboy", "st_fountain"},
            "brew", new String[] {"store_cooler", "store_cooler", "trade_back_bar", "trade_back_bar", "dn_ice_lo", "store_cooler", "st_fountain"},
            "sweet", new String[] {"tr_popcorn_lo", "tr_popcorn_lo", "gs_freezer", "gs_freezer", "dn_prep", "st_warmer", "st_fountain"},
            "nacho", new String[] {"st_warmer", "dn_griddle", "dn_prep", "dn_prep", "dn_fryer", "dn_lowboy", "st_fountain"});
    private static final String[] SUPPLY = {"st_pallet_water", "st_pallet_water", "st_pallet_water_half", "st_pallet_meals", "st_pallet_meals",
            "st_pallet_cots", "st_pallet", "bunk_rack_boxes", "sc_cases_water", "sc_cases_blankets", "cardboard_box"};

    /** A room along a straight side, kept so the signs over the walk can point to it. */
    private record Room(int s, int t0, int w, boolean facesOut, String kind, int y) {
    }

    private final List<Room> rooms = new ArrayList<>();
    private int food;

    /** A cell of straight side s, t across from the centre line and b out from the field: x, z. */
    private int[] cell(int s, int t, int b) {
        return switch (s) {
            case 0 -> new int[] {cx + t, cz - HZ - 1 - b};
            case 1 -> new int[] {cx + HX + b, cz + t};
            case 2 -> new int[] {cx + t, cz + HZ + b};
            default -> new int[] {cx - HX - 1 - b, cz + t};
        };
    }

    /**
     * A room on straight side s, w cells along it from t0 and d bands deep from b0, drawn in its own
     * frame: the front on local z = 0 (its outer band when it faces out, else its inner), local x
     * running to the left of someone looking at it.
     */
    private Placer room(int s, int t0, int w, int b0, int d, boolean facesOut, int y) {
        int minX, minZ;
        switch (s) {
            case 0 -> {
                minX = cx + t0;
                minZ = cz - HZ - b0 - d;
            }
            case 1 -> {
                minX = cx + HX + b0;
                minZ = cz + t0;
            }
            case 2 -> {
                minX = cx + t0;
                minZ = cz + HZ + b0;
            }
            default -> {
                minX = cx - HX - b0 - d;
                minZ = cz + t0;
            }
        }
        return Placer.forLot(out, minX, y, minZ, w, d, facesOut ? s : (s + 2) & 3);
    }

    private String concrete() {
        double r = chance();
        return r < 0.88 ? "st_floor" : "st_floor_b";
    }

    private String light(String base) {
        double r = chance();
        return base + (r < 0.62 ? "_on" : r < 0.72 ? "_flicker" : "");
    }

    /** Along the ring at a cell: across a straight side from its centre line, or round a corner, in blocks at band b. */
    private static int ringPos(double px, double pz, int b) {
        double qx = Math.abs(px) - AX, qz = Math.abs(pz) - AZ;
        if (qx > 0 && qz > 0) {
            return (int) Math.floor(Math.atan2(qz, qx) * (RC + b));
        }
        return (int) Math.floor(qx > qz ? pz : px);
    }

    /** A band of rooms round one level: its floor, its first and last bands, which way the fronts face, the rooms in order. */
    private record Band(int y, int b0, int b1, int ceil, boolean facesOut, String[] seq, int[] next, Through through) {
        int d() {
            return b1 - b0 + 1;
        }
    }

    /** How high the way through a band (or a wall) is at a cell; 0 where there is none. */
    @FunctionalInterface
    private interface Through {
        int height(double px, double pz);
    }

    private static final Through SOLID = (px, pz) -> 0;

    private final Through groundIn = (px, pz) -> on(serviceTunnel, px, pz, 4) || near(lowVoms, px, pz, 2) != null || on(teamTunnel, px, pz, 3) ? 5 : 0;
    private final Through groundOut = (px, pz) -> on(serviceTunnel, px, pz, 4) || near(gates, px, pz, 5) != null ? 5
            : near(cornerGates, px, pz, 3) != null ? 4 : 0;
    private final Through upperIn = (px, pz) -> near(upVoms, px, pz, 2) != null ? 3 : 0;
    /** The lounges' wall onto the 200 concourse: a doorway every 24 along the ring. */
    private final Through loungeWall = (px, pz) -> {
        int v = Math.floorMod(seg(px, pz)[1], 24);
        return v == 20 || v == 21 ? 3 : 0;
    };

    /**
     * The concourses: block walls either side of each walk with the rooms let into them, then the
     * walks. On the ground: food stands and restrooms under the rows, the pro shop, first aid and
     * more restrooms along the outer wall, the shelter's supply rooms round the corners. On the 200
     * level the club's own stands and restrooms against the facade; on the 300 level stands under
     * the back rows and restrooms along the facade.
     */
    private void concourse() {
        Band[] bands = {
                new Band(0, IN_BACK, IN_FRONT, CONC_CEIL, true, INNER, new int[1], groundIn),
                new Band(0, OUT_FRONT, OUT_BACK, CONC_CEIL, false, OUTER, new int[1], groundOut),
                new Band(CLUB, 87, 98, CLUB_CONC_CEIL, false, CLUB_ROOMS, new int[1], rampWay),
                new Band(UPPER, UPPER_IN, 78, UPPER_CEIL, true, UPPER_INNER, new int[1], upperIn),
                new Band(UPPER, 93, 98, UPPER_CEIL, false, UPPER_OUTER, new int[1], rampWay)};
        double[][][] splits = {
                {{0, 14.5}, {21.5, 44.5}, {45.5, 68.5}, {75.5, 90}},
                {{0, 20}, {21, 41.5}, {48.5, 69}, {70, 90}},
                {{0, 22}, {23, 44.5}, {45.5, 67}, {68, 90}},
                {{0, 18.5}, {21.5, 48.5}, {51.5, 78.5}, {81.5, 90}},
                {{0, 22}, {23, 44.5}, {45.5, 67}, {68, 90}}};
        for (Band band : bands) {
            fillBand(band);
        }
        for (int i = 0; i < bands.length; i++) {
            for (int s = 0; s < 4; s++) {
                sideRooms(s, bands[i]);
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    for (double[] a : splits[i]) {
                        depot(sx, sz, a[0], a[1], bands[i]);
                    }
                }
            }
        }
        walk(0, IN_FRONT + 1, OUT_FRONT - 1, CONC_CEIL, groundIn, groundOut);
        walk(CLUB, LOUNGE_WALL + 1, 86, CLUB_CONC_CEIL, loungeWall, SOLID);
        walk(UPPER, 79, 92, UPPER_CEIL, upperIn, SOLID);
        for (int s = 0; s < 4; s++) {
            checkIn(s);
        }
        sections();
        if (Boolean.getBoolean("zc.stadium.rooms")) {
            rooms.forEach(r -> System.err.println("room " + r));
        }
    }

    /** A band's cells to solid block over its floor, but for the ways through, which keep a lintel. */
    private void fillBand(Band band) {
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5, dd = edge(px, pz);
                if (dd < band.b0() || dd >= band.b1() + 1) {
                    continue;
                }
                int b = (int) dd, h = band.through().height(px, pz), front = band.facesOut() ? band.b1() : band.b0();
                set(x, band.y(), z, concrete());
                for (int y = band.y() + h + 1; y < band.ceil(); y++) {
                    set(x, y, z, y == band.y() + 2 && b == front ? "st_cmu_band" : "st_cmu");
                }
            }
        }
    }

    private void sideRooms(int s, Band band) {
        int half = (s & 1) == 1 ? AZ : AX;
        int t = -half;
        while (t < half) {
            if (blocked(s, t, band)) {
                t++;
                continue;
            }
            int e = t;
            while (e < half && !blocked(s, e, band)) {
                e++;
            }
            pack(s, t, e, band);
            t = e;
        }
    }

    private boolean blocked(int s, int t, Band band) {
        for (int b : new int[] {band.b0(), band.b1()}) {
            int[] c = cell(s, t, b);
            if (band.through().height(c[0] - cx + 0.5, c[1] - cz + 0.5) > 0) {
                return true;
            }
        }
        // the stair cores stand in the upper walk against the inner rooms' fronts: no room there
        if (band.y() == UPPER && band.facesOut()) {
            for (Ray r : cores) {
                if (coreSide(r) == s && t >= coreT(r) - 6 && t <= coreT(r) + 5) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int width(String k) {
        return switch (k) {
            case "stand" -> 10;
            case "aid" -> 14;
            case "family" -> 5;
            case "closet" -> 6;
            default -> 12;
        };
    }

    /**
     * Rooms along a run of wall from a to e, taking the band's order where they fit; a short run
     * left over makes a staff room, and less than four cells stays block.
     */
    private void pack(int s, int a, int e, Band band) {
        String[] seq = band.seq();
        int d = band.d();
        while (e - a >= 4) {
            int next = band.next()[0];
            String k = null;
            for (int j = 0; j < seq.length && k == null; j++) {
                String c = seq[(next + j) % seq.length];
                if (!c.equals("closet") && !c.equals("family") && width(c) <= e - a) {
                    k = c;
                    band.next()[0] = next + j + 1;
                }
            }
            if (k == null) {
                k = !band.facesOut() && e - a >= 5 && e - a <= 7 ? "family" : "closet";
            }
            int w = k.equals("closet") ? Math.min(e - a, 8) : width(k);
            Placer q = room(s, a, w, band.b0(), d, band.facesOut(), band.y());
            switch (k) {
                case "stand" -> stand(q, w, d, FOOD[food++ % FOOD.length]);
                case "men" -> restroom(q, w, d, true);
                case "women" -> restroom(q, w, d, false);
                case "store" -> store(q, w, d);
                case "aid" -> aid(q, w, d);
                case "family" -> family(q, w, d);
                default -> closet(q, w, d);
            }
            rooms.add(new Room(s, a, w, band.facesOut(), k, band.y()));
            a += w;
        }
    }

    /** Clears a room's inside up to its drop ceiling, lays its floor, lines its side and back walls. */
    private void hollow(Placer q, int w, int d, String floor, String lining) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean wall = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                if (!wall) {
                    q.set(x, 0, z, floor);
                }
                for (int y = 1; y <= ROOM_CEIL; y++) {
                    if (!wall) {
                        q.set(x, y, z, y == ROOM_CEIL ? "fit_ceiling" : "air");
                    } else if (lining != null && z > 0 && y < ROOM_CEIL) {
                        q.set(x, y, z, lining);
                    }
                }
            }
        }
        for (int x = 2; x < w - 1; x += 4) {
            for (int z = 2; z < d - 1; z += 3) {
                q.set(x, ROOM_CEIL, z, light("fit_troffer") + "[axis=x]");
            }
        }
    }

    /** What the shelter's last days left on a room's floor. */
    private void litter(Placer q, int x, int z, double amount) {
        double r = chance() / amount;
        if (r < 0.04) {
            q.set(x, 1, z, chance() < 0.5 ? "home_blood_pool" : "blood_splat");
        } else if (r < 0.12) {
            q.set(x, 1, z, "paper");
        } else if (r < 0.16) {
            q.set(x, 1, z, "trash_bag[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
    }

    /**
     * A food stand: the counter on the walk with its tills under the roll-down grille and the lit
     * header, the cooks' line and the menu boards behind, the stock room at the back.
     */
    private void stand(Placer q, int w, int d, String k) {
        hollow(q, w, d, "dn_quarry", null);
        double g = chance();
        for (int x = 1; x <= w - 2; x++) {
            int i = w - 2 - x;
            q.set(x, ROOM_CEIL + 1, 0, "st_stand_" + k + "_" + i + "[facing=north]");
            q.set(x, ROOM_CEIL, 0, "st_grille_box[facing=north]");
            if (x == 1) {
                Doors.abandoned(q, rng, x, 1, 0, Doors.STEEL, "south", Doors.hinge(rng));
                q.set(x, 3, 0, "st_cmu");
                continue;
            }
            q.set(x, 1, 0, (k.equals("brew") && i % 3 == 1 ? "trade_bar_taps" : i % 3 == 1 ? "store_register" : "store_counter") + "[facing=north]");
            String lo = "air", hi = "air";
            if (g < 0.25) {
                lo = "st_grille";
                hi = "st_grille";
            } else if (g < 0.5) {
                hi = "st_grille_half";
            } else if (g < 0.7) {
                double r = chance();
                hi = r < 0.4 ? "st_grille_bent" : r < 0.7 ? "st_grille_half" : "air";
            }
            q.set(x, 2, 0, lo.equals("air") ? lo : lo + "[facing=north]");
            q.set(x, 3, 0, hi.equals("air") ? hi : hi + "[facing=north]");
        }
        // the line, its back to the stock room's wall (a gap at the end for the door through), or
        // against the back wall where the band is too shallow for a stock room
        boolean stock = d >= 9;
        int lz = stock ? 3 : d - 2;
        String[] line = LINE.get(k);
        boolean[] high = new boolean[w];
        for (int x = 1; x <= (stock ? w - 3 : w - 2) && x - 1 < line.length; x++) {
            String e = line[x - 1];
            q.set(x, 1, lz, e + "[facing=north]");
            if (e.equals("store_cooler")) {
                q.set(x, 2, lz, "store_cooler_top[facing=north]");
            }
            if (e.startsWith("dn_griddle") || e.startsWith("dn_fryer") || e.equals("store_oven")) {
                q.set(x, 3, lz, (chance() < 0.6 ? "dn_hood_on" : "dn_hood") + "[facing=north]");
                high[x] = true;
            }
        }
        for (int x = w - 4; x >= 1; x--) {
            if (!high[x] && !high[x + 1]) {
                q.set(x + 1, 3, lz, "st_menu_" + k + "_0[facing=north]");
                q.set(x, 3, lz, "st_menu_" + k + "_1[facing=north]");
                break;
            }
        }
        if (!stock) {
            for (int x = 2; x <= w - 2; x++) {
                litter(q, x, 1 + rng.nextInt(2), 0.6);
            }
            return;
        }
        for (int x = 1; x <= w - 2; x++) {
            for (int y = 1; y < ROOM_CEIL; y++) {
                q.set(x, y, 4, "st_cmu");
            }
        }
        Doors.abandoned(q, rng, w - 2, 1, 4, Doors.STEEL, "south", Doors.hinge(rng));
        // the stock room: racks and pallets along the back, boxes down the side
        for (int x = 1; x <= w - 2; x++) {
            for (int z = 5; z <= d - 2; z++) {
                if (z == d - 2) {
                    double r = chance();
                    if (r < 0.3) {
                        q.set(x, 1, z, "bunk_rack_boxes[facing=north]");
                    } else if (r < 0.55) {
                        q.set(x, 1, z, "dn_dry[facing=north]");
                    } else if (r < 0.75) {
                        q.set(x, 1, z, "store_shelf_empty[facing=north]");
                        q.set(x, 2, z, "store_shelf_empty_top[facing=north]");
                    } else {
                        q.set(x, 1, z, SUPPLY[rng.nextInt(SUPPLY.length)] + "[facing=north]");
                    }
                } else if (x == 1 && chance() < 0.6) {
                    q.set(x, 1, z, "cardboard_box[facing=east]");
                } else if (!(x == w - 2 && z == 5)) {
                    litter(q, x, z, 1);
                }
            }
        }
        for (int x = 2; x <= w - 2; x++) {
            litter(q, x, 1 + rng.nextInt(2), 0.6);
        }
    }

    /** A restroom: in round a screen wall, the basins along the front, stalls (and the trough) along the back. */
    private void restroom(Placer q, int w, int d, boolean men) {
        hollow(q, w, d, "home_floor_tile", "st_tile");
        for (int y = 1; y <= 3; y++) {
            q.set(w - 3, y, 0, "air");
            q.set(w - 2, y, 0, "air");
            for (int x = w - 4; x <= w - 2; x++) {
                q.set(x, y, 2, "st_tile");
            }
        }
        q.set(w - 4, 3, -1, "st_plaque_" + (men ? "men" : "women") + "[facing=north]");
        for (int x = 1; x <= w - 6; x++) {
            q.set(x, 1, 1, "fit_vanity_black[facing=south]");
            q.set(x, 2, 1, "fit_mirror_long[facing=south]");
        }
        q.set(w - 5, 2, 1, "st_dryer[facing=south]");
        q.set(w - 2, 1, 3, chance() < 0.3 ? "ext_trash_can_tipped" : "ext_trash_can");
        int back = d - 2;
        for (int x = 1; x <= w - 2; x++) {
            if (men && x >= 4) {
                q.set(x, 1, back, "st_trough[facing=north]");
            } else {
                q.set(x, 1, back, "fit_toilet_stall[facing=north]");
                q.set(x, 2, back, "fit_stall_top[facing=north]");
            }
        }
        if (!men) {
            for (int z = 4; z < back - 1; z++) {
                q.set(1, 1, z, "fit_toilet_stall[facing=east]");
                q.set(1, 2, z, "fit_stall_top[facing=east]");
            }
        }
        for (int x = 2; x <= w - 3; x++) {
            for (int z = 3; z < back; z++) {
                litter(q, x, z, 0.8);
            }
        }
    }

    /** The pro shop: a glass front under its header, racks of jerseys, the shelves round the walls, looted. */
    private void store(Placer q, int w, int d) {
        hollow(q, w, d, "st_carpet", null);
        int dl = w / 2 - 1;
        for (int x = 1; x <= w - 2; x++) {
            boolean door = x == dl || x == dl + 1;
            for (int y = door ? 3 : 1; y <= 3; y++) {
                boolean broken = chance() < 0.3;
                q.set(x, y, 0, (broken ? "st_glazing_broken" : "st_glazing") + "[facing=north]");
                if (broken && y == 1) {
                    q.set(x, 1, -1, "glass_shards");
                }
            }
        }
        Doors.pair(q, rng, dl, 1, 0, Doors.SHOP, "south");
        for (int x = 2; x <= 9 && x <= w - 2; x++) {
            q.set(x, ROOM_CEIL + 1, 0, "st_stand_store_" + (9 - x) + "[facing=north]");
        }
        q.set(dl + 2, 3, -1, "st_plaque_store[facing=north]");
        for (int z = 1; z <= d - 2; z++) {
            q.set(1, 1, z, "store_shelf_thrift[facing=east]");
            q.set(1, 2, z, "store_shelf_thrift_top[facing=east]");
            q.set(w - 2, 1, z, "store_shelf_thrift[facing=west]");
            q.set(w - 2, 2, z, "store_shelf_thrift_top[facing=west]");
        }
        for (int z = 3; z <= d - 4; z += 2) {
            for (int x = 3; x <= w - 4; x++) {
                if (x != dl && x != dl + 1) {
                    q.set(x, 1, z, chance() < 0.25 ? "store_shelf_fallen[facing=" + DIRS[rng.nextInt(4)] + "]" : "store_clothes_rack[facing=north]");
                }
            }
        }
        for (int x = 2; x <= 5; x++) {
            q.set(x, 1, d - 2, (x == 3 ? "store_register" : "store_counter") + "[facing=north]");
        }
        for (int x = 2; x <= w - 3; x++) {
            litter(q, x, 2, 1);
        }
    }

    /** First aid, then the shelter's triage: cots head to the walls, drips, the stretchers, the dead. */
    private void aid(Placer q, int w, int d) {
        hollow(q, w, d, "floor_lino", null);
        int dl = w / 2 - 1;
        Doors.pair(q, rng, dl, 1, 0, Doors.STEEL, "south");
        for (int x : new int[] {2, 3, w - 4, w - 3}) {
            q.set(x, 2, 0, (chance() < 0.3 ? "st_glazing_broken" : "st_glazing") + "[facing=north]");
        }
        for (int x = dl - 3; x <= dl + 4; x++) {
            q.set(x, ROOM_CEIL + 1, 0, "st_stand_guest_" + (dl + 4 - x) + "[facing=north]");
        }
        q.set(dl - 1, 3, -1, "st_plaque_aid[facing=north]");
        q.set(dl + 2, 3, -1, "st_plaque_triage[facing=north]");
        for (int z = 2; z <= d - 3; z += 2) {
            for (int side = 0; side < 2; side++) {
                int hx = side == 0 ? 1 : w - 2, fx = side == 0 ? 2 : w - 3;
                String f = side == 0 ? "west" : "east";
                double r = chance();
                if (r < 0.7) {
                    q.set(hx, 1, z, (chance() < 0.5 ? "sc_cot_head_b" : "sc_cot_head") + "[facing=" + f + "]");
                    q.set(fx, 1, z, "sc_cot_foot[facing=" + f + "]");
                } else if (r < 0.85) {
                    q.set(fx, 1, z, "bunk_bodybag[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
                if (chance() < 0.4) {
                    q.set(side == 0 ? 1 : w - 2, 1, z + 1, "bunk_iv_stand");
                }
            }
        }
        for (int z = 3; z <= d - 3; z += 2) {
            q.set(dl - 2, 1, z, (chance() < 0.4 ? "st_stretcher_bag" : "st_stretcher") + "[facing=north]");
            q.set(dl + 3, 1, z, (chance() < 0.4 ? "st_stretcher_bag" : "st_stretcher") + "[facing=north]");
        }
        for (int x = 2; x <= w - 3; x++) {
            if (x != dl && x != dl + 1) {
                q.set(x, 1, d - 2, (chance() < 0.7 ? "cellar_shelf_medical" : "sc_cases_blankets") + "[facing=north]");
            }
        }
        for (int x : new int[] {3, dl, dl + 1, w - 4}) {
            for (int z = 1; z < d - 2; z++) {
                if (chance() < 0.12) {
                    q.set(x, 1, z, chance() < 0.6 ? "home_blood_pool" : "blood_splat");
                }
            }
        }
    }

    private void family(Placer q, int w, int d) {
        hollow(q, w, d, "home_floor_tile", "st_tile");
        Doors.abandoned(q, rng, 2, 1, 0, Doors.STEEL, "south", "left");
        q.set(3, 3, -1, "st_plaque_family[facing=north]");
        q.set(1, 1, d - 2, "fit_toilet_stall[facing=north]");
        q.set(1, 2, d - 2, "fit_stall_top[facing=north]");
        q.set(w - 2, 1, d - 2, "fit_vanity_black[facing=north]");
        q.set(w - 2, 2, d - 2, "fit_mirror_long[facing=north]");
        q.set(w - 2, 2, d - 4, "st_dryer[facing=west]");
        litter(q, 2, d - 3, 1);
    }

    /** A staff room the shelter stacked its supplies in. */
    private void closet(Placer q, int w, int d) {
        hollow(q, w, d, "floor_concrete", null);
        Doors.abandoned(q, rng, 1, 1, 0, Doors.STEEL, "south", Doors.hinge(rng));
        q.set(Math.min(2, w - 1), 3, -1, "st_plaque_staff[facing=north]");
        for (int x = 2; x <= w - 2; x++) {
            for (int z = 2; z <= d - 2; z++) {
                if (z == d - 2 || x == w - 2 || chance() < 0.5) {
                    q.set(x, 1, z, SUPPLY[rng.nextInt(SUPPLY.length)] + "[facing=north]");
                }
            }
        }
        q.set(1, 1, d - 2, "dn_mop[facing=east]");
    }

    /**
     * A room round a corner: the cells between two angles and two bands. Its edge stays block, a
     * doorway through the middle of its front; the shelter's pallets and racks fill it.
     */
    private void depot(int sx, int sz, double a0, double a1, Band band) {
        int b0 = band.b0(), b1 = band.b1(), y0 = band.y();
        boolean inner = band.facesOut();
        int r = RC + b1 + 2;
        int xa = sx > 0 ? cx + AX : cx - AX - r, za = sz > 0 ? cz + AZ : cz - AZ - r;
        java.util.function.BiPredicate<Integer, Integer> in = (x, z) -> {
            double px = x - cx + 0.5, pz = z - cz + 0.5, qx = Math.abs(px) - AX, qz = Math.abs(pz) - AZ;
            if (qx <= 0 || qz <= 0 || px * sx < 0 || pz * sz < 0) {
                return false;
            }
            double a = Math.toDegrees(Math.atan2(qz, qx));
            int b = (int) edge(px, pz);
            return a >= a0 && a < a1 && b >= b0 && b <= b1;
        };
        int front = inner ? b1 : b0;
        double best = 1e9;
        int dx = 0, dz = 0, doorDir = 0;
        List<int[]> inside = new ArrayList<>();
        for (int x = xa; x < xa + r; x++) {
            for (int z = za; z < za + r; z++) {
                if (!in.test(x, z)) {
                    continue;
                }
                boolean wall = !in.test(x + 1, z) || !in.test(x - 1, z) || !in.test(x, z + 1) || !in.test(x, z - 1);
                double px = x - cx + 0.5, pz = z - cz + 0.5;
                if (wall) {
                    if ((int) edge(px, pz) == front) {
                        double a = Math.toDegrees(Math.atan2(Math.abs(pz) - AZ, Math.abs(px) - AX));
                        int o = outward(px, pz), dir = inner ? o : (o + 2) & 3;
                        int ddx = dir == 1 ? 1 : dir == 3 ? -1 : 0, ddz = dir == 2 ? 1 : dir == 0 ? -1 : 0;
                        double score = Math.abs(a - (a0 + a1) / 2);
                        boolean through = in.test(x - ddx, z - ddz) && !in.test(x + ddx, z + ddz);
                        if (through && score < best) {
                            best = score;
                            dx = x;
                            dz = z;
                            doorDir = dir;
                        }
                    }
                    continue;
                }
                inside.add(new int[] {x, z});
            }
        }
        if (inside.size() < 6) {
            return;
        }
        for (int[] c : inside) {
            set(c[0], y0, c[1], "floor_concrete");
            for (int y = 1; y < ROOM_CEIL; y++) {
                set(c[0], y0 + y, c[1], "air");
            }
            set(c[0], y0 + ROOM_CEIL, c[1], Math.floorMod(c[0] * 3 + c[1] * 5, 11) == 0 ? light("fit_downlight") : "fit_ceiling");
        }
        if (best < 1e9) {
            // the door, facing in so it sits flush with the walk
            Doors.abandoned(p, rng, dx, y0 + 1, dz, Doors.STEEL, DIRS[(doorDir + 2) & 3], Doors.hinge(rng));
            set(dx + (doorDir == 1 ? 1 : doorDir == 3 ? -1 : 0), y0 + 3, dz + (doorDir == 2 ? 1 : doorDir == 0 ? -1 : 0),
                    "st_plaque_staff[facing=" + DIRS[doorDir] + "]");
        }
        int bx = dx - (doorDir == 1 ? 1 : doorDir == 3 ? -1 : 0), bz = dz - (doorDir == 2 ? 1 : doorDir == 0 ? -1 : 0);
        for (int[] c : inside) {
            int x = c[0], z = c[1];
            if (Math.abs(x - bx) + Math.abs(z - bz) <= 1 && best < 1e9) {
                continue;
            }
            int wallDir = !in.test(x, z - 1) ? 0 : !in.test(x + 1, z) ? 1 : !in.test(x, z + 1) ? 2 : !in.test(x - 1, z) ? 3 : -1;
            boolean edgeCell = wallDir < 0 && (!in.test(x, z - 2) || !in.test(x + 2, z) || !in.test(x, z + 2) || !in.test(x - 2, z));
            if (wallDir >= 0) {
                if (chance() < 0.85) {
                    set(x, y0 + 1, z, SUPPLY[rng.nextInt(SUPPLY.length)] + "[facing=" + DIRS[(wallDir + 2) & 3] + "]");
                }
            } else if (!edgeCell && Math.floorMod(x + z, 3) != 0 && chance() < 0.5) {
                set(x, y0 + 1, z, SUPPLY[rng.nextInt(SUPPLY.length)] + "[facing=" + DIRS[rng.nextInt(4)] + "]");
            } else if (chance() < 0.05) {
                set(x, y0 + 1, z, "paper");
            }
        }
    }

    // ------------------------------------------------------------------ the walks

    /**
     * A concourse walk on the level at y, bands bIn .. bOut, under its slab at ceil: the slab's
     * precast beams and the services along it, lamps in two rows, the signs hung over it, the
     * machines against its walls, the shelter's cots and leavings.
     */
    private void walk(int y, int bIn, int bOut, int ceil, Through inWall, Through outWall) {
        int duct = bIn + 2, pipes = bOut - 2, lampA = bIn + 5, lampB = bOut - 4;
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5, dd = edge(px, pz);
                if (dd < bIn || dd >= bOut + 1) {
                    continue;
                }
                int b = (int) dd, rp = ringPos(px, pz, b), o = outward(px, pz);
                String radial = (o & 1) == 1 ? "x" : "z", along = (o & 1) == 1 ? "z" : "x";
                set(x, y, z, concrete());
                // the slab's underside painted out dark, the precast beams across, the services along
                set(x, ceil, z, "concrete_dark");
                if (Math.floorMod(rp, 4) == 0) {
                    set(x, ceil - 1, z, "st_beam[axis=" + radial + "]");
                } else if (b == pipes) {
                    set(x, ceil - 1, z, "st_pipes[axis=" + along + "]");
                }
                if (b == duct) {
                    set(x, ceil - 2, z, (Math.floorMod(rp, 8) == 2 ? "st_duct_tee" : "st_duct") + "[axis=" + along + "]");
                }
                if ((b == lampB || b <= bOut - 3 && Math.floorMod(b - lampA, 8) == 0) && Math.floorMod(rp, 8) == 6) {
                    set(x, ceil - 2, z, light("fit_highbay"));
                }
            }
        }
        for (int s = 0; s < 4; s++) {
            int half = (s & 1) == 1 ? AZ : AX;
            for (int t = -half; t < half; t++) {
                if (Math.floorMod(t - 3, 14) == 0) {
                    hang(s, t, (bIn + bOut) / 2, ceil - 1, wayTo(s, t, y));
                }
                wallSide(s, t, y, bIn - 1, true, inWall);
                wallSide(s, t, y, bOut + 1, false, outWall);
                if (nearCore(s, t) || y == 0 && Math.abs(t) <= 8) {
                    continue;
                }
                // the shelter's people slept along the walk, heads to the wall
                if (Math.floorMod(t, 2) == 0 && chance() < 0.12 * (y == 0 ? 1 : 0.5)) {
                    int[] h = cell(s, t, bOut - 1), f = cell(s, t, bOut - 2);
                    set(h[0], y + 1, h[1], (chance() < 0.5 ? "sc_cot_head_b" : "sc_cot_head") + "[facing=" + DIRS[s] + "]");
                    set(f[0], y + 1, f[1], (chance() < 0.3 ? "sc_cot_foot_b" : "sc_cot_foot") + "[facing=" + DIRS[s] + "]");
                }
                if (chance() < 0.05) {
                    int[] c = cell(s, t, bIn + 1 + rng.nextInt(bOut - bIn - 2));
                    double r = chance();
                    String f = "[facing=" + DIRS[rng.nextInt(4)] + "]";
                    set(c[0], y + 1, c[1], r < 0.35 ? "paper" : r < 0.6 ? "trash_bag" + f : r < 0.75 ? "cardboard_box" + f
                            : r < 0.85 ? "blood_splat" : r < 0.93 ? "sc_cases_water" + f : "home_blood_drag" + f);
                }
            }
        }
    }

    private boolean nearCore(int s, int t) {
        for (Ray r : cores) {
            int cs = r.nx() > 0 ? 1 : r.nx() < 0 ? 3 : r.nz() > 0 ? 2 : 0;
            if (cs == s && Math.abs(((s & 1) == 1 ? r.bz() : r.bx()) - t) < 8) {
                return true;
            }
        }
        return false;
    }

    /** Which way a sign at t on side s points: the gate, the seats, the restrooms, first aid, the stairs. */
    private String wayTo(int s, int t, int y) {
        if (y == 0 && Math.abs(t) <= 8) {
            return chance() < 0.5 ? "gate" : "shelter";
        }
        if (nearCore(s, t)) {
            return y == 0 ? (chance() < 0.5 ? "stairs" : "club") : "stairs";
        }
        if (y != CLUB) {
            for (Ray v : y == 0 ? lowVoms : upVoms) {
                if (v.nx() == (s == 1 ? 1 : s == 3 ? -1 : 0) && v.nz() == (s == 2 ? 1 : s == 0 ? -1 : 0)
                        && Math.abs((s & 1) == 1 ? v.bz() - t : v.bx() - t) < 7) {
                    return "secs";
                }
            }
        }
        for (Room r : rooms) {
            if (r.s() == s && r.y() == y && t >= r.t0() - 4 && t < r.t0() + r.w() + 4) {
                if (r.kind().equals("men") || r.kind().equals("women") || r.kind().equals("family")) {
                    return "rest";
                }
                if (r.kind().equals("aid")) {
                    return "aid";
                }
            }
        }
        return y == CLUB ? "club" : Math.floorMod(t, 28) < 14 ? "stairs" : "shelter";
    }

    /** A sign hung across the walk at t on side s, two blocks wide from band b, read along the walk. */
    private void hang(int s, int t, int b, int y, String k) {
        int f = (s + 3) & 3;
        String face = DIRS[f];
        int[] a = cell(s, t, b), c = cell(s, t, b + 1);
        boolean aFirst = along(a[0], a[1], face) < along(c[0], c[1], face);
        String name = k.equals("secs") ? "st_way_secs_" : "st_way_" + k + "_";
        set(a[0], y, a[1], name + (aFirst ? 0 : 1) + "[facing=" + face + "]");
        set(c[0], y, c[1], name + (aFirst ? 1 : 0) + "[facing=" + face + "]");
    }

    /**
     * Against a plain stretch of a walk's wall (band wallB, rooms there facing out when inner):
     * drink machines, a cash machine, a fountain, bins, notices.
     */
    private void wallSide(int s, int t, int y, int wallB, boolean inner, Through through) {
        for (Room r : rooms) {
            if (r.s() == s && r.y() == y && r.facesOut() == inner && t >= r.t0() - 1 && t <= r.t0() + r.w()) {
                return;
            }
        }
        int[] w = cell(s, t, wallB), c = cell(s, t, inner ? wallB + 1 : wallB - 1);
        for (int dt = -1; dt <= 1; dt++) {
            int[] n = cell(s, t + dt, wallB);
            if (through.height(n[0] - cx + 0.5, n[1] - cz + 0.5) > 0) {
                return;
            }
        }
        String f = "[facing=" + DIRS[inner ? s : (s + 2) & 3] + "]";
        int k = Math.floorMod(t * 7 + s * 3 + (inner ? 1 : 0) + y, 11);
        switch (k) {
            case 0, 1 -> {
                set(c[0], y + 1, c[1], "store_vending" + f);
                set(c[0], y + 2, c[1], "store_vending_top" + f);
            }
            case 3 -> set(c[0], y + 1, c[1], (chance() < 0.7 ? "bk_atm_lit" : "bk_atm") + f);
            case 5 -> set(c[0], y + 1, c[1], "sc_fountain" + f);
            case 7 -> set(c[0], y + 1, c[1], chance() < 0.3 ? "ext_trash_can_tipped" : "ext_trash_can");
            case 9 -> set(c[0], y + 2, c[1], "bunk_extinguisher" + f);
            default -> {
            }
        }
        if (chance() < 0.3) {
            double r = chance();
            set(c[0], y + 3, c[1], (r < 0.4 ? "st_poster_missing" : r < 0.7 ? "st_poster_missing_b" : "st_poster_rules") + f);
        }
    }

    /**
     * The shelter's check-in inside each side's gate: tables across the walk, crowd barriers
     * making the lines, most of them knocked flat the night the gate went.
     */
    private void checkIn(int s) {
        String lat = DIRS[(s + 1) & 3];
        for (int t = -4; t <= 3; t++) {
            int[] c = cell(s, t, 31);
            if (t % 3 != 0) {
                set(c[0], 1, c[1], "cv_table[axis=" + ((s & 1) == 1 ? "z" : "x") + "]");
                if (chance() < 0.5) {
                    set(c[0], 2, c[1], "paper");
                }
            }
        }
        for (int t : new int[] {-5, -2, 1, 4}) {
            for (int b = 32; b <= 36; b++) {
                int[] c = cell(s, t, b);
                set(c[0], 1, c[1], (chance() < 0.55 ? "st_barrier_down" : "st_barrier") + "[facing=" + lat + "]");
            }
        }
    }

    /**
     * The section plates: SEC and the number over each vomitory's mouth on its concourse and over
     * its portal in the rows, the sections counted clockwise from the north (100s below, 300s up).
     */
    private void sections() {
        for (int level = 0; level < 2; level++) {
            List<Ray> voms = new ArrayList<>(level == 0 ? lowVoms : upVoms);
            voms.sort(Comparator.comparingDouble(r -> Math.floorMod((long) (Math.toDegrees(Math.atan2(r.nx(), -r.nz())) * 1000), 360000)));
            for (int j = 0; j < voms.size(); j++) {
                Ray r = voms.get(j);
                if (level == 0) {
                    int n = 101 + 3 * j;
                    plate(r, IN_FRONT + 1.5, 6, n, false);
                    plate(r, 8.5, 7, n, true);
                } else {
                    int n = 301 + 2 * j;
                    plate(r, 79.5, UPPER + 4, n, false);
                    plate(r, 64.5, UPPER + 4, n, true);
                }
            }
        }
    }

    /** SEC and the three digits across ray r at a distance out, on a wall facing out (or in). */
    private void plate(Ray r, double along, int y, int n, boolean facingIn) {
        double ax = r.x(along), az = r.z(along);
        int o = outward(ax, az), f = facingIn ? (o + 2) & 3 : o;
        String face = DIRS[f];
        String[] tiles = {"st_sec_0", "st_sec_" + (n / 100 + 1), "st_sec_" + (n / 10 % 10 + 1), "st_sec_" + (n % 10 + 1)};
        List<int[]> cells = new ArrayList<>();
        for (double k = -1.5; k <= 1.5; k += 1) {
            int x = (int) Math.floor(cx + ax + k * r.nz()), z = (int) Math.floor(cz + az - k * r.nx());
            cells.add(new int[] {x, z});
        }
        cells.sort(Comparator.comparingInt(c -> along(c[0], c[1], face)));
        for (int i = 0; i < 4; i++) {
            set(cells.get(i)[0], y, cells.get(i)[1], tiles[i] + "[facing=" + face + "]");
        }
    }

    // ------------------------------------------------------------------ the club level

    /** Which run of the ring a cell is on (0..3 the sides, 4..7 the corners) and how far along it, in blocks at the suites. */
    private static int[] seg(double px, double pz) {
        double qx = Math.abs(px) - AX, qz = Math.abs(pz) - AZ;
        if (qx > 0 && qz > 0) {
            return new int[] {4 + (px > 0 ? 1 : 0) + (pz > 0 ? 2 : 0), (int) Math.floor(Math.atan2(qz, qx) * (RC + 52))};
        }
        if (qx > qz) {
            return new int[] {px > 0 ? 1 : 3, (int) Math.floor(pz) + AZ};
        }
        return new int[] {pz > 0 ? 2 : 0, (int) Math.floor(px) + AX};
    }

    /** A number fixed for a suite or a lounge, the same for every cell of it. */
    private int roll(int segment, int unit, int salt) {
        long h = (segment * 1_000_003L + unit * 7919L + salt) * 0x9E3779B97F4A7C15L ^ clubSeed;
        h ^= h >>> 29;
        return (int) Math.floorMod(h, 100);
    }

    private long clubSeed;

    /**
     * The club level round the whole bowl at y 24: the suites, eight wide, each with its row of
     * padded seats out on the balcony behind the glass guard, the glass front with its slider, a
     * drink rail, chairs, the buffet and the fridge at the back; the club corridor behind them; the
     * lounges with their bars behind that, and their doorways out to the 200 concourse. The shelter
     * put families in some of the suites and ran itself from a lounge on each side.
     */
    private void club() {
        clubSeed = rng.nextLong();
        int y = CLUB;
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5, dd = edge(px, pz);
                if (dd < XAISLE + 2 || dd >= LOUNGE_WALL + 1) {
                    continue;
                }
                int b = (int) dd, o = outward(px, pz);
                int[] sg = seg(px, pz);
                int k = Math.floorMod(sg[1], 8), unit = Math.floorDiv(sg[1], 8), v = Math.floorMod(sg[1], 24), lounge = Math.floorDiv(sg[1], 24);
                String in = "[facing=" + DIRS[(o + 2) & 3] + "]", outw = "[facing=" + DIRS[o] + "]";
                int kind = roll(sg[0], unit, 1);
                if (b == LOUNGE_WALL) {
                    if (v == 20 || v == 21) {
                        for (int yy = y + 1; yy <= y + 3; yy++) {
                            set(x, yy, z, "air");
                        }
                    }
                    continue;
                }
                if (b == XAISLE + 2) {
                    // the suite's balcony seats (the one in front of its glass door goes after)
                    if (k != 0) {
                        set(x, y + 1, z, (kind < 30 ? "st_seat_g_up" : kind < 40 ? "st_seat_g_blanket" : chance() < 0.3 ? "st_seat_g_up" : "st_seat_g") + in);
                    }
                    continue;
                }
                if (b == GLASS) {
                    for (int yy = y + 1; yy <= y + 4; yy++) {
                        set(x, yy, z, k == 0 ? "drywall" : (roll(sg[0], unit * 8 + k, yy) < 12 ? "st_glazing_broken" : "st_glazing") + outw);
                    }
                    set(x, y + 5, z, "concrete_panel");
                    set(x, y + 6, z, "concrete_panel");
                    doorCell(glassDoors, sg[0], unit, x, z, o, GLASS, k, 3);
                    continue;
                }
                // floors, the drop ceilings, the air under them
                boolean wall = b == SUITE_BACK || b == CORRIDOR_WALL || k == 0 && b < SUITE_BACK;
                String floor = b < SUITE_BACK ? "home_floor_carpet_blue" : "st_carpet";
                set(x, y, z, floor);
                for (int yy = y + 1; yy <= y + 4; yy++) {
                    set(x, yy, z, wall ? (b == CORRIDOR_WALL && (v == 11 || v == 12) && yy < y + 4 ? "air" : "drywall") : "air");
                }
                set(x, y + 5, z, wall ? "concrete_panel" : "fit_ceiling");
                set(x, y + 6, z, "concrete_panel");
                if (wall) {
                    if (b == SUITE_BACK) {
                        doorCell(backDoors, sg[0], unit, x, z, o, SUITE_BACK, k, 6);
                    }
                    continue;
                }
                if (b < SUITE_BACK) {
                    suiteCell(x, z, b, k, kind, in, outw);
                } else if (b < CORRIDOR_WALL) {
                    // the corridor: pictures and sconces on its walls, downlights
                    if (b == SUITE_BACK + 1 && k == 2) {
                        set(x, y + 2, z, "fit_painting_" + "abc".charAt(roll(sg[0], unit, 3) % 3) + outw);
                    } else if (b == SUITE_BACK + 1 && k == 5) {
                        set(x, y + 3, z, light("fit_sconce") + outw);
                    } else if (b == CORRIDOR_WALL - 1 && k == 6 && v != 11 && v != 12 && v != 13 && v != 10) {
                        set(x, y + 2, z, "fit_painting_" + "abc".charAt(roll(sg[0], unit, 4) % 3) + in);
                    }
                    if (b == SUITE_BACK + 3 && k == 4) {
                        set(x, y + 5, z, light("fit_downlight"));
                    }
                    if (chance() < 0.02) {
                        set(x, y + 1, z, chance() < 0.5 ? "paper" : "blood_splat");
                    }
                } else {
                    loungeCell(x, z, b, v, roll(sg[0], lounge, 2), sg[0] < 4 && lounge == 1, in, outw);
                }
            }
        }
        // each suite's two doors, where its walls run straight enough for a door to open both ways
        for (int[] d : glassDoors.values()) {
            set(d[0] - STEP_X[d[2]], y + 1, d[1] - STEP_Z[d[2]], "air");
            Doors.abandoned(p, rng, d[0], y + 1, d[1], Doors.SHOP, DIRS[d[2]], Doors.hinge(rng));
        }
        for (int[] d : backDoors.values()) {
            Doors.abandoned(p, rng, d[0], y + 1, d[1], Doors.FLAT, DIRS[(d[2] + 2) & 3], Doors.hinge(rng));
        }
    }

    private static final int[] STEP_X = {0, 1, 0, -1}, STEP_Z = {-1, 0, 1, 0};
    private final Map<Long, int[]> glassDoors = new java.util.TreeMap<>(), backDoors = new java.util.TreeMap<>();

    /**
     * Offers the wall cell (x, z) of ring wallB as a suite's door: kept if the cells either side of
     * it, in and out along o, are open floor of the suite and of what lies beyond (not its own
     * wall, not a partition), and if it lies nearer the wanted place k along the suite than the
     * best so far. Round the corners the ring steps diagonally and most of its cells can't hold one.
     */
    private void doorCell(Map<Long, int[]> best, int segment, int unit, int x, int z, int o, int wallB, int k, int want) {
        for (int sgn = -1; sgn <= 1; sgn += 2) {
            double qx = x + sgn * STEP_X[o] - cx + 0.5, qz = z + sgn * STEP_Z[o] - cz + 0.5;
            int b = (int) edge(qx, qz);
            if (sgn < 0 ? b >= wallB : b <= wallB) {
                return;
            }
            int[] q = seg(qx, qz);
            if (Math.floorMod(q[1], 8) == 0 && b < SUITE_BACK && b > GLASS) {
                return;
            }
        }
        long key = segment * 100_000L + unit;
        int score = Math.abs(k - want);
        int[] cur = best.get(key);
        if (cur == null || score < cur[3]) {
            best.put(key, new int[] {x, z, o, score});
        }
    }

    /** One cell of a suite: band b out, k across it (0 its wall), kind its own roll. */
    private void suiteCell(int x, int z, int b, int k, int kind, String in, String outw) {
        int y = CLUB + 1;
        boolean family = kind >= 60 && kind < 80, looted = kind >= 80;
        if (b == GLASS + 1 && (k == 1 || k == 2 || k == 5 || k == 6)) {
            // the drink rail stands right against the glass; round the corners, where the glass
            // steps away from it, it would wall off a strip of floor, so it's left out there
            if (againstGlass(x, z, outw)) {
                set(x, y, z, "trade_bar" + outw);
            }
        } else if (b == GLASS + 2 && (k == 1 || k == 2 || k == 5 || k == 6)) {
            set(x, y, z, (looted && chance() < 0.5 ? "home_chair_tipped" : "fit_chair_high") + in);
        } else if (b == GLASS + 3 && k == 4) {
            set(x, y, z, "pk_coffee_table" + in);
        } else if (b == GLASS + 4 && k != 4 && k != 6 && k != 7) {
            set(x, y, z, "home_armchair_blue" + in);
        } else if (family && (b == GLASS + 5 || b == GLASS + 6) && (k == 1 || k == 3 || k == 5)) {
            set(x, y, z, (b == GLASS + 6 ? (chance() < 0.5 ? "sc_cot_head_b" : "sc_cot_head") : "sc_cot_foot") + outw);
        } else if (b == SUITE_BACK - 1) {
            switch (k) {
                case 1 -> set(x, y, z, "store_counter" + in);
                case 2 -> {
                    set(x, y, z, "store_counter" + in);
                    set(x, y + 1, z, "st_warmer" + in);
                }
                case 3 -> {
                    set(x, y, z, "store_counter" + in);
                    set(x, y + 1, z, (looted ? "home_tv_smashed" : "home_tv") + in);
                }
                case 4 -> {
                    set(x, y, z, "store_cooler" + in);
                    set(x, y + 1, z, "store_cooler_top" + in);
                }
                case 7 -> set(x, y + 1, z, "fit_painting_c" + in);
                default -> {
                }
            }
        }
        if (b == GLASS + 3 && (k == 2 || k == 6)) {
            set(x, CLUB + 5, z, light("fit_downlight"));
        }
        if ((looted || family) && b > GLASS + 4 && b < SUITE_BACK - 1 && chance() < 0.15) {
            set(x, y, z, looted ? (chance() < 0.5 ? "blood_splat" : "paper") : "sc_cases_water" + in);
        }
    }

    private boolean againstGlass(int x, int z, String outw) {
        int o = List.of(DIRS).indexOf(outw.substring(8, outw.length() - 1));
        return (int) edge(x - STEP_X[o] - cx + 0.5, z - STEP_Z[o] - cz + 0.5) == GLASS;
    }

    /**
     * One cell of a lounge (v along its 24, roll its kind): a bar along the back wall with stools,
     * high tables and sofas round the TVs; one lounge a side the shelter's command post.
     */
    private void loungeCell(int x, int z, int b, int v, int kind, boolean command, String in, String outw) {
        int y = CLUB + 1;
        if (b == LOUNGE_WALL - 4 && v % 4 == 2 && v > 1 && v < 23) {
            set(x, CLUB + 5, z, light("fit_troffer") + "[axis=" + (in.contains("east") || in.contains("west") ? "z" : "x") + "]");
        }
        if (command && v >= 2 && v <= 17) {
            // maps, radios and the paperwork of a shelter running out of everything
            if (b == CORRIDOR_WALL + 3 && v % 5 != 0) {
                set(x, y, z, "bunk_map_table" + in);
            } else if (b == LOUNGE_WALL - 1 && v % 3 != 0) {
                set(x, y, z, (v % 3 == 1 ? "bunk_radio_desk" + in : "cv_table[axis=" + (in.contains("east") || in.contains("west") ? "z" : "x") + "]"));
            } else if (b == LOUNGE_WALL - 3 && v % 2 == 0) {
                set(x, y, z, (chance() < 0.4 ? "office_chair_fallen" : "office_chair") + outw);
            } else if (b == CORRIDOR_WALL + 6 && v % 4 == 1) {
                set(x, y, z, "sc_cot_head" + outw);
            } else if (chance() < 0.08) {
                set(x, y, z, "paper");
            }
            return;
        }
        if (v >= 2 && v <= 9) {
            // the bar
            if (b == LOUNGE_WALL - 1) {
                set(x, y, z, "trade_back_bar" + in);
            } else if (b == LOUNGE_WALL - 3) {
                set(x, y, z, (v == 4 || v == 7 ? "trade_bar_taps" : "trade_bar") + in);
            } else if (b == LOUNGE_WALL - 4 && kind < 70) {
                set(x, y, z, (chance() < 0.2 ? "home_chair_tipped" : "fit_chair_high") + outw);
            }
            return;
        }
        if (v >= 13 && v <= 22) {
            if (b == LOUNGE_WALL - 1 && v % 3 == 1) {
                set(x, y, z, "store_counter" + in);
                set(x, y + 1, z, (kind > 70 ? "home_tv_smashed" : "home_tv") + in);
            } else if (b == LOUNGE_WALL - 4 && v % 4 == 2) {
                set(x, y, z, "fit_round_table");
            } else if ((b == LOUNGE_WALL - 5 || b == LOUNGE_WALL - 3) && v % 4 == 2) {
                set(x, y, z, "fit_chair_high" + (b == LOUNGE_WALL - 5 ? outw : in));
            } else if (b == CORRIDOR_WALL + 2 && v % 5 != 0) {
                set(x, y, z, "home_sofa_blue_m" + outw);
            } else if (chance() < 0.03) {
                set(x, y, z, chance() < 0.5 ? "paper" : "trash_bag" + in);
            }
        }
    }

    // ------------------------------------------------------------------ the undercroft

    /**
     * Under the club, between the concourse's wall and the facade: the tall open hall the gates
     * open into, its columns, the turnstiles in each gate, the shelter's overflow (cots, pallets,
     * the vehicles it drove in).
     */
    private void undercroft() {
        // a slab over the hall at y 12, the structure solid from there up to the club
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double dd = edge(x - cx + 0.5, z - cz + 0.5);
                if (dd < XAISLE + 1 || dd >= FACADE) {
                    continue;
                }
                for (int y = dd < CONC_WALL + 1 ? CONC_CEIL + 1 : HALL + 1; y < CLUB; y++) {
                    set(x, y, z, FILL);
                }
            }
        }
        walk(0, CONC_WALL + 1, FACADE - 1, HALL, groundOut, (px, pz) -> {
            int k = bayKind(px, pz);
            return facadeGates.height(px, pz) > 0 ? 10 : k == EXIT || k == BREACH ? 3 : 0;
        });
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5, dd = edge(px, pz);
                if (dd < CONC_WALL + 1 || dd >= FACADE) {
                    continue;
                }
                int b = (int) dd, o = outward(px, pz);
                int[] sg = seg(px, pz);
                if ((b == 61 || b == 73 || b == 90) && Math.floorMod(sg[1], 12) == 6) {
                    for (int y = 1; y < HALL; y++) {
                        set(x, y, z, "st_precast_plain");
                    }
                    continue;
                }
                Ray g = near(gates, px, pz, 8);
                Ray cg = near(cornerGates, px, pz, 5);
                if ((g != null || cg != null) && b == FACADE - 3) {
                    // the turnstiles, every other block, barriers between
                    boolean even = Math.floorMod(x + z, 2) == 0;
                    set(x, 1, z, (even ? "st_turnstile" : chance() < 0.5 ? "st_barrier_down" : "st_barrier") + "[facing=" + DIRS[o] + "]");
                    continue;
                }
                if (g == null && cg == null && b > 75 && b < 90 && chance() < 0.02) {
                    set(x, 1, z, SUPPLY[rng.nextInt(SUPPLY.length)] + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        // the shelter's vehicles inside each side gate, and its cots in rows along the wall
        for (int s = 0; s < 4; s++) {
            for (int side = -1; side <= 1; side += 2) {
                int[] c = cell(s, side * 14, 70);
                float yaw = (s & 1) == 1 ? 0 : 90;
                out.car(c[0] + 0.5, 1, c[1] + 0.5, yaw, side < 0 ? 5.5 : 7, side < 0 ? 7 : 10, rng.nextLong());
            }
            int half = (s & 1) == 1 ? AZ : AX;
            for (int t = -half + 2; t < half - 2; t += 2) {
                if (Math.abs(t) < 12 || nearCore(s, t) || chance() < 0.4) {
                    continue;
                }
                int[] h = cell(s, t, CONC_WALL + 1), f = cell(s, t, CONC_WALL + 2);
                set(h[0], 1, h[1], (chance() < 0.5 ? "sc_cot_head" : "sc_cot_head_b") + "[facing=" + DIRS[(s + 2) & 3] + "]");
                set(f[0], 1, f[1], "sc_cot_foot[facing=" + DIRS[(s + 2) & 3] + "]");
            }
        }
    }

    // ------------------------------------------------------------------ the facade, the canopy, the boards

    /** The facade's gates, the side gates lettered clockwise from the north. */
    private static final String GATE_LETTERS = "ABCD";

    private final Through facadeGates = (px, pz) -> near(gates, px, pz, 8) != null ? 10 : near(cornerGates, px, pz, 5) != null ? 8 : 0;

    /** How far a gate canopy reaches out from the facade's face, and its fascia's height. */
    private static final int CANOPY = 6, CANOPY_Y = 11;

    /** What a plinth bay between two piers holds. */
    private static final int PLAIN = 0, SHUTTERS = 1, WINDOWS = 2, EXIT = 3, BREACH = 4, FIRE = 5;

    /** The ledges the rain runs down from. */
    private static final int[] LEDGES = {12, CLUB, UPPER, 60, TOP};

    private final long wearSeed;
    private final java.util.Set<Integer> rammed = new java.util.HashSet<>();

    /** A number 0..1 fixed for a cell. */
    private double hash(int x, int y, int z, int salt) {
        long h = wearSeed ^ x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ z * 0x165667B19E3779F9L ^ salt * 0xD6E8FEB86659FD93L;
        h = (h ^ h >>> 31) * 0xBF58476D1CE4E5B9L;
        h = (h ^ h >>> 29) * 0x94D049BB133111EBL;
        return ((h ^ h >>> 32) >>> 11) * 0x1.0p-53;
    }

    /** Along the facade's face at a cell, about a block a step; it starts again round each corner. */
    private static int faceU(double px, double pz) {
        return ringPos(px, pz, FACADE);
    }

    /** The plinth bay a cell of the facade stands in. */
    private int bayKind(double px, double pz) {
        if (near(gates, px, pz, 20.5) != null || near(cornerGates, px, pz, 9) != null || rampLat(px, pz) < 7) {
            return PLAIN;
        }
        int sg = seg(px, pz)[0], bay = Math.floorDiv(faceU(px, pz), 12);
        double r = hash(sg, bay, 0, 31);
        if (r < 0.06) {
            return BREACH;
        }
        if (r < 0.14) {
            return FIRE;
        }
        if (r < 0.40) {
            return SHUTTERS;
        }
        if (r < 0.62) {
            return WINDOWS;
        }
        return r < 0.8 && sg < 4 ? EXIT : PLAIN;
    }

    /**
     * Precast as the years left it: rain and rust streaked down from each ledge in runs, moss
     * along the foot, settlement cracks, soot fanned up the wall over the bays that burned.
     */
    private String worn(String base, int x, int y, int z) {
        double px = x - cx + 0.5, pz = z - cz + 0.5, r = hash(x, y, z, 12);
        if (y < 10 && edge(px, pz) >= FACADE - 0.5 && bayKind(px, pz) == FIRE) {
            int pm = Math.floorMod(faceU(px, pz), 12);
            double spread = Math.abs(pm - 6.5);
            if (spread < 2.5 + y * 0.35 && r < 0.95 - y * 0.08) {
                return base + "_soot";
            }
        }
        int ledge = TOP;
        for (int l : LEDGES) {
            if (l > y) {
                ledge = l;
                break;
            }
        }
        if (hash(x, 0, z, 11) < 0.24 && ledge - y <= 2 + (int) (hash(x, ledge, z, 13) * 9) && r < 0.8) {
            return base + "_stain";
        }
        if (y == 1 && r < 0.35 || y == 2 && r < 0.1) {
            return base + "_damp";
        }
        if (r > 0.993) {
            return base + "_moss";
        }
        return r < 0.05 ? base + "_crack" : base;
    }

    /**
     * The facade: precast in storey bands two thick, piers every twelve standing out three at the
     * plinth, two to the club level and one to the top, fins between; cornices at each floor,
     * deep over the plinth and at the parapet; louvres where the 200 and 300 concourses breathe;
     * the main gates between their pylons, the corner gates, the ramp bridges' doorways. The
     * plinth's bays: roll-down shutters on the old concession windows, high windows into the hall,
     * exit doors, a bay burned, two broken through. Then the lettering, the banners on the piers,
     * the ivy and the paint.
     */
    private void facade() {
        int reach = FACADE + 7;
        for (int x = cx - HX - reach; x < cx + HX + reach; x++) {
            for (int z = cz - HZ - reach; z < cz + HZ + reach; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5, dd = edge(px, pz);
                if (dd < FACADE || dd >= FACADE + 6) {
                    continue;
                }
                int b = (int) dd, o = outward(px, pz), fu = faceU(px, pz), pm = Math.floorMod(fu, 12);
                int gate = facadeGates.height(px, pz);
                double lat = rampLat(px, pz);
                boolean open = lat < 1.4, pylon = gate == 0 && near(gates, px, pz, 10.4) != null;
                boolean named = (o & 1) == 1 ? near(gates, px, pz, 26.5) != null : near(gates, px, pz, 20.5) != null;
                boolean pier = pm <= 1 && !named && lat >= 2.6 && gate == 0, fin = (pm == 5 || pm == 9) && lat >= 2.6 && gate == 0 && !pylon;
                if (b <= FACADE + 1) {
                    for (int y = 0; y <= TOP; y++) {
                        if (y >= 1 && y <= gate) {
                            continue;
                        }
                        if (open && (y > CLUB && y <= CLUB + 3 || y > UPPER && y <= UPPER + 3)) {
                            set(x, y, z, "air");
                            continue;
                        }
                        if (b == FACADE + 1 && Math.floorMod(fu, 4) != 0 && !pylon && !open
                                && (y >= CLUB + 3 && y <= CLUB + 5 || y >= UPPER + 3 && y <= UPPER + 5)) {
                            set(x, y, z, (hash(x, y, z, 21) < 0.06 ? "st_grille_bent" : "st_slats") + "[facing=" + DIRS[o] + "]");
                            continue;
                        }
                        set(x, y, z, worn(y <= 12 ? "st_precast_b" : Math.floorMod(y - 12, 12) == 0 ? "st_precast_plain" : "st_precast",
                                x, y, z));
                    }
                    if (b == FACADE + 1 && gate == 0) {
                        bay(x, z, px, pz, o, pm);
                    }
                    continue;
                }
                int depth = b - FACADE - 1;
                if (pylon) {
                    if (depth <= 3) {
                        for (int y = 1; y <= TOP + 3; y++) {
                            set(x, y, z, worn("st_precast_plain", x, y, z));
                        }
                    }
                    continue;
                }
                if (pier) {
                    int top = depth == 1 ? TOP : depth == 2 ? CLUB : depth == 3 ? 12 : 0;
                    for (int y = 1; y <= top; y++) {
                        set(x, y, z, worn("st_precast_plain", x, y, z));
                    }
                    if (depth == 1 && pm == 0 && Math.floorMod(Math.floorDiv(fu, 12), 2) == 0) {
                        banner(x, z, o, UPPER - 2);
                    }
                    continue;
                }
                if (depth == 1) {
                    for (int y : new int[] {12, CLUB, UPPER, 60}) {
                        if (y > gate && !(open && (y == CLUB || y == UPPER))) {
                            set(x, y, z, worn("st_precast_plain", x, y, z));
                        }
                    }
                    if (fin) {
                        for (int y = 13; y < TOP - 1; y++) {
                            if (y != CLUB && y != UPPER && y != 60) {
                                set(x, y, z, "st_fin[axis=" + ((o & 1) == 1 ? "x" : "z") + "]");
                            }
                        }
                    }
                    // the storm-blue band round the parapet
                    set(x, TOP - 1, z, "st_fascia");
                    set(x, TOP, z, "st_fascia");
                    if (gate == 0 && lat >= 2.6) {
                        dress(x, z, px, pz, o, fin);
                    }
                } else if (depth == 2 && gate == 0 && !open && lat > 2.6) {
                    set(x, 12, z, worn("st_precast_plain", x, 12, z));
                    set(x, TOP, z, "st_fascia");
                }
            }
        }
        for (int s = 0; s < 4; s++) {
            gateFront(s);
            name(s, (s & 1) == 1 ? "GRAYHAVEN STADIUM" : "GALES", 33);
        }
    }

    /** The facade's normal at a cell, near enough square to the grid to hang flat things on it. */
    private static boolean square(double px, double pz) {
        double qx = Math.abs(px) - AX, qz = Math.abs(pz) - AZ;
        if (qx <= 0 || qz <= 0) {
            return true;
        }
        double l = Math.hypot(qx, qz);
        return Math.max(qx, qz) / l > 0.985;
    }

    /** One cell of a plinth bay's wall (the outer layer, b 100), by what the bay holds. */
    private void bay(int x, int z, double px, double pz, int o, int pm) {
        int kind = bayKind(px, pz);
        int ix = x - (o == 1 ? 1 : o == 3 ? -1 : 0), iz = z - (o == 2 ? 1 : o == 0 ? -1 : 0);
        int fx = x + (o == 1 ? 1 : o == 3 ? -1 : 0), fz = z + (o == 2 ? 1 : o == 0 ? -1 : 0);
        String f = "[facing=" + DIRS[o] + "]";
        switch (kind) {
            case SHUTTERS -> {
                if (pm >= 3 && pm <= 10) {
                    // the old concession windows along the plaza, their shutters down, one forced
                    boolean forced = hash(x, 0, z, 41) < 0.12;
                    for (int y = 1; y <= 3; y++) {
                        set(x, y, z, forced && y <= 2 ? "air" : "st_slats" + f);
                    }
                    if (forced) {
                        set(ix, 1, iz, "air");
                        set(ix, 2, iz, "air");
                    }
                    set(x, 4, z, "st_grille_box" + f);
                }
            }
            case WINDOWS -> {
                if (pm >= 3 && pm <= 10) {
                    for (int y = 8; y <= 10; y++) {
                        double r = hash(x, y, z, 43);
                        set(ix, y, iz, "air");
                        set(x, y, z, r < 0.5 ? "st_glazing" : r < 0.85 ? "st_glazing_broken" : "air");
                    }
                }
            }
            case EXIT -> {
                if (square(px, pz) && (pm == 6 || pm == 7)) {
                    set(ix, 1, iz, "air");
                    set(ix, 2, iz, "air");
                    Doors.abandoned(p, rng, x, 1, z, Doors.STEEL, DIRS[o], pm == 6 ? "left" : "right");
                    set(fx, 3, fz, "st_plaque_staff" + f);
                }
            }
            case BREACH -> {
                if (pm >= 4 && pm <= 8) {
                    int top = pm == 4 || pm == 8 ? 2 : 4;
                    for (int y = 1; y <= top; y++) {
                        set(x, y, z, "air");
                        set(ix, y, iz, "air");
                    }
                    set(fx, 1, fz, chance() < 0.5 ? "rubble_pile" : "debris_chunk[facing=" + DIRS[rng.nextInt(4)] + "]");
                    set(ix - (fx - x), 1, iz - (fz - z), chance() < 0.6 ? "rubble_pile" : "debris_chunk[facing=" + DIRS[rng.nextInt(4)] + "]");
                    if (pm == 6 && rammed.add(seg(px, pz)[0] * 1000 + Math.floorDiv(faceU(px, pz), 12))) {
                        // what broke it through: a car still nosed into the hole
                        out.car(fx + (fx - x) * 2.5 + 0.5, 1, fz + (fz - z) * 2.5 + 0.5, (float) yawToward(o), 4.5, 5.5, rng.nextLong());
                    }
                }
            }
            case FIRE -> {
                if (pm >= 3 && pm <= 10) {
                    set(fx, 0, fz, "burnt_ground");
                    if (pm >= 5 && pm <= 8) {
                        set(fx, 1, fz, chance() < 0.5 ? "scorch" : chance() < 0.5 ? "pk_barrel_1[facing=" + DIRS[rng.nextInt(4)] + "]" : "rubble_pile");
                    }
                    for (int y = 1; y <= 3; y++) {
                        set(x, y, z, pm >= 4 && pm <= 9 ? "st_slats" + f : worn("st_precast_b", x, y, z));
                    }
                }
            }
            default -> {
            }
        }
    }

    /** Minecraft's yaw for a car pointing back in at the facade from outward o. */
    private static double yawToward(int o) {
        return switch (o) {
            case 0 -> 0;     // from the north, nose south
            case 1 -> 90;    // from the east, nose west
            case 2 -> 180;
            default -> 270;
        };
    }

    /**
     * The face in front of the wall (b 101) between the piers: ivy hung in curtains down from the
     * cornices and climbing from the foot, paint and posters along the plinth, thickest near the
     * gates.
     */
    private void dress(int x, int z, double px, double pz, int o, boolean fin) {
        if (!square(px, pz)) {
            return;
        }
        String toWall = "[facing=" + DIRS[(o + 2) & 3] + "]", away = "[facing=" + DIRS[o] + "]";
        boolean nearGate = near(gates, px, pz, 26) != null;
        // the ivy grows in patches five or so along: up from the foot, or down in a curtain from a ledge
        int patch = seg(px, pz)[0] * 1000 + Math.floorDiv(faceU(px, pz), 5);
        double kind = hash(patch, 0, 0, 51), jitter = hash(x, 1, z, 52);
        int climb = kind < 0.16 ? 2 + (int) (hash(patch, 1, 0, 53) * 9) + (int) (jitter * 3) : 0;
        int ledge = LEDGES[(int) (hash(patch, 2, 0, 54) * 3)];
        int hang = kind > 0.88 && jitter < 0.85 ? 3 + (int) (hash(patch, 3, 0, 55) * 8) + (int) (jitter * 4) : 0;
        for (int y = 1; y < TOP - 1; y++) {
            if (y == 12 || y == CLUB || y == UPPER || y == 60 || fin && y > 12) {
                continue;
            }
            double r = hash(x, y, z, 56);
            if (y <= climb && r < 0.92 || hang > 0 && y < ledge && y >= ledge - hang && r < 0.9) {
                set(x, y, z, "vines" + toWall);
            } else if (y <= 3 && r < (nearGate ? 0.16 : 0.05)) {
                set(x, y, z, "graffiti_" + (int) (hash(x, 9, z, 57) * 8) + toWall);
            } else if (y == 2 && nearGate && r > 0.85) {
                set(x, y, z, (r > 0.95 ? "st_poster_rules" : r > 0.9 ? "st_poster_missing" : "st_poster_missing_b") + away);
            }
        }
    }

    /** A pole banner three blocks tall hung on a pier's face, top at y: the team's, the shelter's, torn, or gone. */
    private void banner(int x, int z, int o, int y) {
        int bx = x + (o == 1 ? 1 : o == 3 ? -1 : 0), bz = z + (o == 2 ? 1 : o == 0 ? -1 : 0);
        double r = hash(x, y, z, 61);
        if (r < 0.12) {
            return;
        }
        String k = r < 0.45 ? "fema" : r < 0.75 ? "gales" : r < 0.88 ? "season" : "torn";
        for (int i = 0; i < 3; i++) {
            set(bx, y - i, bz, "st_vban_" + k + "_" + i + "[facing=" + DIRS[o] + "]");
        }
    }

    /**
     * A main gate's front: its canopy out over the plaza on two steel columns, GATE and the
     * letter on the fascia, the shelter's banner tied up over it; the opening divided into three
     * bays by posts, the roll-down grilles in their boxes (one down, one jammed half way); the
     * ticket windows either side under their sign.
     */
    private void gateFront(int s) {
        String f = "[facing=" + DIRS[s] + "]", along = (s & 1) == 1 ? "z" : "x";
        int b0 = FACADE + 2, fas = FACADE + 1 + CANOPY;
        for (int t = -10; t <= 9; t++) {
            boolean beside = t < -8 || t > 7;
            for (int b = beside ? FACADE + 5 : b0; b < fas; b++) {
                int[] c = cell(s, t, b);
                set(c[0], CANOPY_Y, c[1], "st_canopy");
                if (b == FACADE + 3 && Math.floorMod(t, 4) == 1) {
                    set(c[0], CANOPY_Y - 1, c[1], light("fit_highbay"));
                }
            }
            int[] c = cell(s, t, fas);
            set(c[0], CANOPY_Y, c[1], "st_fascia");
            set(c[0], CANOPY_Y + 1, c[1], "st_fascia");
            if (t == -10 || t == 9) {
                set(c[0], 1, c[1], "st_column_base");
                for (int y = 2; y < CANOPY_Y; y++) {
                    set(c[0], y, c[1], "st_column");
                }
            }
        }
        letters(s, "GATE " + GATE_LETTERS.charAt(s), CANOPY_Y + 1, fas + 1);
        // the shelter's banner over the canopy
        List<int[]> row = new ArrayList<>();
        for (int t = -4; t <= 3; t++) {
            row.add(cell(s, t, b0));
        }
        row.sort(Comparator.comparingInt(c -> along(c[0], c[1], DIRS[s])));
        for (int i = 0; i < 8; i++) {
            int[] c = row.get(i);
            set(c[0], 14, c[1], "st_fema_" + i + f);
            set(c[0], 13, c[1], "st_fema_" + (8 + i) + f);
        }
        // the posts and the grilles across the opening
        for (int t = -8; t <= 7; t++) {
            int[] c = cell(s, t, FACADE + 1);
            if (t == -3 || t == 2) {
                set(c[0], 1, c[1], "st_column_base");
                for (int y = 2; y <= 10; y++) {
                    set(c[0], y, c[1], "st_column");
                }
                continue;
            }
            set(c[0], 10, c[1], "st_grille_box" + f);
            if (t < -3) {
                boolean bent = s == 1 || s == 3 ? t == -5 : false;
                for (int y = 1; y <= 9; y++) {
                    set(c[0], y, c[1], bent && y <= 3 ? "air" : (bent && y == 4 ? "st_grille_bent" : "st_grille") + f);
                }
            } else if (t > 2) {
                for (int y = 6; y <= 9; y++) {
                    set(c[0], y, c[1], "st_grille" + f);
                }
            }
        }
        // the ticket windows each side of the pylons, the sign over them
        for (int side = -1; side <= 1; side += 2) {
            List<int[]> win = new ArrayList<>();
            for (int k = 0; k < 8; k++) {
                win.add(cell(s, side < 0 ? -18 + k : 11 + k, FACADE + 1));
            }
            win.sort(Comparator.comparingInt(c -> along(c[0], c[1], DIRS[s])));
            for (int k = 0; k < 8; k++) {
                int[] c = win.get(k);
                int fx = c[0] + (s == 1 ? 1 : s == 3 ? -1 : 0), fz = c[1] + (s == 2 ? 1 : s == 0 ? -1 : 0);
                set(c[0], 4, c[1], "st_stand_tickets_" + k + f);
                if (k % 2 == 1) {
                    double r = chance();
                    set(c[0], 2, c[1], (r < 0.3 ? "st_slats" : r < 0.45 ? "st_ticket_broken" : "st_ticket_" + (k / 2 + 1)) + f);
                    set(fx, 1, fz, "st_ticket_ledge" + f);
                }
            }
        }
    }

    /** Two-block letters across side s at band b with their top row at y, centred on the side. */
    private void letters(int s, String text, int y, int b) {
        int w = text.length() * 3 - 1;
        String face = DIRS[s];
        int[] c0 = cell(s, 0, b), c1 = cell(s, 1, b);
        int dir = along(c1[0], c1[1], face) > along(c0[0], c0[1], face) ? 1 : -1;
        for (int p = 0; p < w; p++) {
            char ch = text.charAt(p / 3);
            int t = dir > 0 ? -w / 2 + p : w - 1 - w / 2 - p, col = p % 3;
            int[] c = cell(s, t, b);
            for (int y2 = y - 1; y2 <= y; y2++) {
                set(c[0], y2, c[1], "air");
            }
            if (ch == ' ' || col == 2) {
                continue;
            }
            char lc = Character.toLowerCase(ch);
            set(c[0], y, c[1], "st_let_" + lc + "_" + col + "[facing=" + face + "]");
            set(c[0], y - 1, c[1], "st_let_" + lc + "_" + (col + 2) + "[facing=" + face + "]");
        }
    }

    /**
     * The stadium's name in channel letters three blocks square across side s's face, top row at
     * y, centred: storm blue edged gold. The odd one has fallen; the first never.
     */
    private void name(int s, String text, int y) {
        int w = text.length() * 3, b = FACADE + 2;
        String face = DIRS[s];
        int[] c0 = cell(s, 0, b), c1 = cell(s, 1, b);
        int dir = along(c1[0], c1[1], face) > along(c0[0], c0[1], face) ? 1 : -1;
        for (int p = 0; p < w; p++) {
            char ch = text.charAt(p / 3);
            int t = dir > 0 ? -w / 2 + p : w - 1 - w / 2 - p, col = p % 3;
            int[] c = cell(s, t, b);
            for (int r = 0; r < 3; r++) {
                set(c[0], y - r, c[1], "air");
            }
            if (ch == ' ' || p / 3 > 0 && hash(s, p / 3, y, 71) < 0.07) {
                continue;
            }
            for (int r = 0; r < 3; r++) {
                set(c[0], y - r, c[1], "st_big_" + Character.toLowerCase(ch) + "_" + (r * 3 + col) + "[facing=" + face + "]");
            }
        }
    }

    // ------------------------------------------------------------------ the ramp towers

    /** Where the ramp towers stand round each corner (degrees from the x axis), the core's and
     *  the deck's radii, and the centre's distance out from the field's edge. */
    static final double RAMP_DEG = 67.5;
    static final int RAMP_CORE = 4, RAMP_OUT = 9, RAMP_AT = FACADE + 16;
    /** The deck climbs a lap every RAMP_PITCH: three laps from the 200 bridge to the 300's. */
    static final double RAMP_PITCH = (UPPER - CLUB) / 3.0;

    /** How far across the nearest ramp tower's line a cell lies (99 off all of them). */
    private double rampLat(double px, double pz) {
        double best = 99;
        for (Ray r : ramps) {
            if (r.along(px, pz) > 0) {
                best = Math.min(best, Math.abs(r.lat(px, pz)));
            }
        }
        return best;
    }

    /** The way in from a ramp's bridges through the rooms to the 200 and 300 walks. */
    private final Through rampWay = (px, pz) -> rampLat(px, pz) < 0.95 ? 3 : 0;

    /**
     * The ramp towers outside the corners, the way most of the crowd went up: a deck five wide
     * winding round a concrete core a lap every five and a third, its parapet striping the
     * tower, columns up the outside, lights under each lap; a bridge across to the facade where
     * the deck passes the 200 level and again three laps up at the 300. The foot is on the
     * plaza on the far side, the way in under the end of the first lap.
     */
    private void ramps() {
        for (Ray r : ramps) {
            double ox = r.x(RAMP_AT), oz = r.z(RAMP_AT), tb = Math.atan2(-r.nz(), -r.nx());
            double ux = Math.cos(tb), uz = Math.sin(tb);
            int x0 = (int) Math.floor(cx + ox) - RAMP_OUT - 8, z0 = (int) Math.floor(cz + oz) - RAMP_OUT - 8;
            for (int x = x0; x <= x0 + 2 * RAMP_OUT + 16; x++) {
                for (int z = z0; z <= z0 + 2 * RAMP_OUT + 16; z++) {
                    double dx = x - cx + 0.5 - ox, dz = z - cz + 0.5 - oz, rr = Math.hypot(dx, dz);
                    double lat = Math.abs(dx * uz - dz * ux), fwd = dx * ux + dz * uz;
                    if (rr >= RAMP_OUT + 1.25) {
                        // the bridges across to the facade
                        if (lat < 2.2 && fwd > 0 && edge(x - cx + 0.5, z - cz + 0.5) >= FACADE + 2) {
                            for (int y : new int[] {CLUB, UPPER}) {
                                set(x, y, z, "st_ramp_7");
                                set(x, y - 1, z, worn("st_precast_plain", x, y - 1, z));
                                if (lat >= 1.4) {
                                    set(x, y + 1, z, worn("st_precast_plain", x, y + 1, z));
                                }
                            }
                        }
                        continue;
                    }
                    if (rr < RAMP_CORE) {
                        for (int y = 1; y <= UPPER + 4; y++) {
                            set(x, y, z, worn("st_precast_plain", x, y, z));
                        }
                        continue;
                    }
                    double f = (Math.atan2(dz, dx) - tb) / (2 * Math.PI);
                    f -= Math.floor(f);
                    boolean rim = rr >= RAMP_OUT, bridge = lat < 1.4 && fwd > 0, mouth = f > 0.25 && f < 0.5;
                    double cf = f * 8 - 0.5;
                    if (rim && !bridge && !mouth && Math.abs(cf - Math.round(cf)) * Math.PI / 4 * rr < 0.6) {
                        for (int y = 1; y <= UPPER + 3; y++) {
                            set(x, y, z, worn("st_precast_plain", x, y, z));
                        }
                        continue;
                    }
                    for (int k = -5; k <= 3; k++) {
                        double s = CLUB + 1 + (f + k) * RAMP_PITCH;
                        if (s < 1 || s > UPPER + 1.0001) {
                            continue;
                        }
                        if (bridge && (Math.abs(s - CLUB - 1) < 0.5 || Math.abs(s - UPPER - 1) < 0.5)) {
                            s = Math.round(s);
                        } else if (rim) {
                            // the parapet: a band from the deck's underside to a block and a bit over it
                            span(x, z, s < 1 + RAMP_PITCH * 0.75 ? 1 : s - 1, s + 1.15);
                            continue;
                        }
                        int ys = (int) Math.ceil(s) - 1, n = (int) Math.round((s - ys) * 8) - 1;
                        if (n < 0) {
                            ys--;
                            n = 7;
                        }
                        set(x, ys, z, "st_ramp_" + n);
                        span(x, z, s < 1 + RAMP_PITCH * 0.75 ? 1 : s - 1, ys);
                        double sixth = f * 6;
                        if (!rim && s > 1 + RAMP_PITCH && rr > 6 && rr < 7.3 && Math.abs(sixth - Math.round(sixth)) * Math.PI / 3 * rr < 0.6) {
                            set(x, (int) Math.floor(s - 1) - 1, z, light("fit_downlight"));
                        }
                    }
                }
            }
        }
    }

    /**
     * Precast filling one column from lo to hi (in blocks, fractions allowed): whole blocks, and
     * at either end a piece standing up from the floor or hanging from the top of its block.
     */
    private void span(int x, int z, double lo, double hi) {
        lo = Math.max(lo, 1);
        for (int y = (int) Math.floor(lo); y < hi; y++) {
            double a = Math.max(lo, y), b = Math.min(hi, y + 1);
            int n = (int) Math.round((b - a) * 8) - 1;
            if (n < 0) {
                continue;
            }
            if (n >= 7) {
                set(x, y, z, worn("st_precast_plain", x, y, z));
            } else if (a > y + 0.01) {
                set(x, y, z, "st_soffit_" + n);
            } else {
                set(x, y, z, "st_parapet_" + n);
            }
        }
    }

    // ------------------------------------------------------------------ the shelter's checkpoints

    /**
     * Out in front of each main gate, what the shelter set up to take people in: at A and C the
     * intake (the queue snaking between crowd barriers, the jersey barriers and sandbags across
     * the approach with the gap the guard watched, light towers, cones, the registration tables),
     * at B and D the barriers closed and sandbagged. And what was left when it fell: bags,
     * boxes, the bodies, the blood, the cars that drove in as far as they could.
     */
    private void checkpoints() {
        for (int s = 0; s < 4; s++) {
            boolean intake = s == 0 || s == 2;
            String face = DIRS[s], across = "[facing=" + face + "]";
            int front = FACADE + 2 + CANOPY + 2;
            if (intake) {
                // the queue: five lanes between barriers, the ends left open turn and turn about
                for (int k = 0; k < 6; k++) {
                    int b = front + 2 + k * 3;
                    for (int t = -16; t <= 15; t++) {
                        boolean gap = k % 2 == 0 ? t >= 13 : t <= -14;
                        if (k == 0 && Math.abs(t + 0.5) < 3 || gap && k > 0) {
                            continue;
                        }
                        int[] c = cell(s, t, b);
                        set(c[0], 1, c[1], (chance() < 0.18 ? "st_barrier_down" : "st_barrier") + across);
                    }
                }
                // the registration tables just under the canopy's edge
                for (int t = -6; t <= 5; t++) {
                    if (Math.abs(t + 0.5) < 2) {
                        continue;
                    }
                    int[] c = cell(s, t, FACADE + 2 + CANOPY - 1);
                    set(c[0], 1, c[1], "cv_table[axis=" + ((s & 1) == 1 ? "z" : "x") + "]");
                    if (chance() < 0.5) {
                        set(c[0], 2, c[1], chance() < 0.6 ? "paper" : "sc_cases_water[facing=" + face + "]");
                    }
                }
            }
            // the line across the approach: jersey barriers, the gap, sandbags either side of it
            int line = front + (intake ? 22 : 4);
            for (int t = -24; t <= 23; t++) {
                int[] c = cell(s, t, line);
                double at = Math.abs(t + 0.5);
                if (intake && at < 3) {
                    continue;
                }
                set(c[0], 1, c[1], (at > 20 ? "hesco" : "jersey_barrier") + "[facing=" + face + "]");
                if (at > 20) {
                    set(c[0], 1, c[1], "hesco");
                    set(c[0], 2, c[1], "hesco");
                }
                if (at >= 3 && at < 7) {
                    int[] bag = cell(s, t, line + 1);
                    set(bag[0], 1, bag[1], "sandbags" + across);
                    set(bag[0], 2, bag[1], "sandbags" + across);
                } else if (!intake && chance() < 0.25) {
                    set(c[0], 2, c[1], "st_razor");
                }
            }
            // the light towers either side, cones down the approach
            for (int side = -1; side <= 1; side += 2) {
                int[] c = cell(s, side * 19, line - 3);
                String in = DIRS[(s + 2) & 3];
                set(c[0], 1, c[1], "st_lt_base[facing=" + in + "]");
                for (int y = 2; y <= 8; y++) {
                    set(c[0], y, c[1], "st_lt_mast");
                }
                set(c[0], 9, c[1], (chance() < 0.7 ? "st_lt_head_on" : "st_lt_head") + "[facing=" + in + "]");
                for (int k = 0; k < 6; k++) {
                    int[] q = cell(s, side * 4, line + 3 + k * 2);
                    if (chance() < 0.75) {
                        set(q[0], 1, q[1], "st_cone");
                    }
                }
            }
            // what was dropped in the rush, and who
            for (int t = -22; t <= 21; t++) {
                for (int b = FACADE + 2; b < line + 8; b++) {
                    int[] c = cell(s, t, b);
                    if (chance() > 0.035) {
                        continue;
                    }
                    String g = out.get(c[0], 1, c[1]);
                    if (g != null && !g.equals("air")) {
                        continue;
                    }
                    double r = chance();
                    String d = "[facing=" + DIRS[rng.nextInt(4)] + "]";
                    if (r < 0.12 && b > FACADE + 3) {
                        int[] h = cell(s, t, b + 1);
                        String hg = out.get(h[0], 1, h[1]);
                        if (hg == null || hg.equals("air")) {
                            set(c[0], 1, c[1], "home_corpse_foot[facing=" + DIRS[(s + 2) & 3] + "]");
                            set(h[0], 1, h[1], "home_corpse_head[facing=" + DIRS[(s + 2) & 3] + "]");
                        }
                    } else {
                        set(c[0], 1, c[1], r < 0.3 ? "blood_splat" : r < 0.45 ? "home_blood_drag" + d : r < 0.6 ? "paper"
                                : r < 0.72 ? "trash_bag" + d : r < 0.84 ? "cardboard_box" + d : r < 0.92 ? "sc_cases_blankets" + d
                                : "sc_cases_water" + d);
                    }
                }
            }
            // the buses that brought people in, parked along the plaza either side of the intake
            if (intake) {
                for (int side = -1; side <= 1; side += 2) {
                    for (int k = 0; k < 2; k++) {
                        int[] c = cell(s, side * (31 + k * 16), FACADE + 9);
                        out.vehicle(c[0] + 0.5, 1, c[1] + 0.5, side < 0 ? 90 : 270, "school_bus,city_bus", rng.nextLong());
                    }
                }
            }
            // the vehicles: the police at the line, an ambulance at C, cars abandoned short of it
            int[] pc = cell(s, intake ? -12 : 10, line + 5);
            float yaw = (float) yawToward(s);
            out.vehicle(pc[0] + 0.5, 1, pc[1] + 0.5, yaw + 90, "police_sedan,police_suv", rng.nextLong());
            if (s == 2) {
                int[] ac = cell(s, 12, line - 6);
                out.vehicle(ac[0] + 0.5, 1, ac[1] + 0.5, yaw, "ambulance,ambulance_van", rng.nextLong());
            }
            if (s == 1 || s == 3) {
                int[] jc = cell(s, -14, line + 6);
                out.vehicle(jc[0] + 0.5, 1, jc[1] + 0.5, yaw + 90, "jeep,jeep_open", rng.nextLong());
            }
        }
    }

    /**
     * The canopy round the top: its deck on trusses running out from the facade, the edge beam,
     * the floodlights along the leading edge, most still burning on the shelter's generators.
     */
    private void canopy() {
        for (int x = cx - EX; x < cx + EX; x++) {
            for (int z = cz - EZ; z < cz + EZ; z++) {
                double px = x - cx + 0.5, pz = z - cz + 0.5, dd = edge(px, pz);
                if (dd < ROOF_IN || dd >= FACADE + 2) {
                    continue;
                }
                int b = (int) dd, o = outward(px, pz), u = seg(px, pz)[1];
                String radial = (o & 1) == 1 ? "x" : "z", along = (o & 1) == 1 ? "z" : "x";
                set(x, ROOF, z, "st_roof");
                if (b >= FACADE) {
                    continue;
                }
                if (b == ROOF_IN) {
                    set(x, ROOF - 1, z, Math.floorMod(u, 3) == 0 ? (chance() < 0.8 ? "st_floods_on" : "st_floods") + "[facing=" + DIRS[(o + 2) & 3] + "]"
                            : "st_chord[axis=" + along + "]");
                } else if (b == ROOF_IN + 6 || b == ROOF_IN + 13) {
                    set(x, ROOF - 1, z, "st_chord[axis=" + along + "]");
                } else if (Math.floorMod(u, 8) == 0) {
                    set(x, ROOF - 1, z, "st_chord[axis=" + radial + "]");
                }
            }
        }
    }

    /**
     * The scoreboards hung under the canopy's leading edge over each end zone, twenty-four by ten:
     * GRAYHAVEN GALES across the top, the shelter's message on the screen, the last game's score
     * still in the bulbs under it; the truss over it, the catwalk behind.
     */
    private void boards() {
        for (int s = 0; s <= 2; s += 2) {
            String face = DIRS[(s + 2) & 3], along = "x";
            List<int[]> row = new ArrayList<>();
            for (int t = -12; t <= 11; t++) {
                row.add(cell(s, t, ROOF_IN));
            }
            row.sort(Comparator.comparingInt(c -> along(c[0], c[1], face)));
            int top = ROOF - 2;
            for (int col = 0; col < row.size(); col++) {
                int[] c = row.get(col);
                set(c[0], ROOF - 1, c[1], "st_chord[axis=" + along + "]");
                for (int r = 0; r < 10; r++) {
                    set(c[0], top - r, c[1], "st_board_" + (r * 24 + col) + "[facing=" + face + "]");
                }
                set(c[0], top - 10, c[1], "st_chord[axis=" + along + "]");
                int[] back = cell(s, c[0] - cx, ROOF_IN + 1);
                set(back[0], top - 10, back[1], "grate");
            }
        }
    }

    // ------------------------------------------------------------------ the field the shelter stood on

    private void fset(int lx, int y, int lz, String block) {
        set(cx + lx, y, cz + lz, block);
    }

    /**
     * What stood on the field when it fell: the goal posts and the benches still there, the
     * shelter's tents in rows over the north half, cots in the open over the south, triage on its
     * tarps in the south end zone, the stores on pallets in the north one, light towers at the
     * corners, and the mess of the last night.
     */
    private void fieldShelter() {
        for (int e = -1; e <= 1; e += 2) {
            goalPost(e);
        }
        for (int lz = -14; lz <= 14; lz++) {
            if (Math.floorMod(lz, 7) != 0) {
                fset(-30, 1, lz, "st_bench[facing=east]");
                fset(29, 1, lz, "st_bench[facing=west]");
            }
        }
        for (int lz : new int[] {-16, 16}) {
            fset(-31, 1, lz, "st_cooler");
            fset(30, 1, lz, "st_cooler");
        }
        // the tents: three across, two deep, over the north half
        for (int lx : new int[] {-21, -3, 15}) {
            for (int lz : new int[] {-44, -28}) {
                tent(lx, lz, 12, "shelter", chance() < 0.25);
            }
        }
        tent(-3, 34, 10, "triage", false);
        // cots in the open, rows across the south half
        for (int lz = 4; lz <= 30; lz += 3) {
            for (int lx = -22; lx <= 21; lx += 2) {
                if (chance() < 0.75) {
                    String f = chance() < 0.85 ? "north" : DIRS[rng.nextInt(4)];
                    fset(lx, 1, lz, (chance() < 0.5 ? "sc_cot_head_b" : "sc_cot_head") + "[facing=" + f + "]");
                    fset(lx, 1, lz + 1, (chance() < 0.3 ? "sc_cot_foot_b" : "sc_cot_foot") + "[facing=" + f + "]");
                } else if (chance() < 0.4) {
                    fset(lx, 1, lz, chance() < 0.5 ? "bunk_bodybag[facing=" + DIRS[rng.nextInt(4)] + "]" : "home_blood_pool");
                } else if (chance() < 0.4) {
                    fset(lx, 1, lz + 1, chance() < 0.5 ? "sc_cases_water[facing=north]" : "sc_cases_blankets[facing=south]");
                }
            }
        }
        // triage: the tarps by colour, stretchers and drips on red and yellow, the dead on black
        String[] tarp = {"red", "yellow", "green", "black"};
        for (int lx = -24; lx < 24; lx++) {
            for (int lz = 46; lz <= 54; lz++) {
                int k = (lx + 24) / 12;
                int ix = Math.floorMod(lx + 24, 12);
                if (ix == 0 || lz == 50) {
                    continue;
                }
                fset(lx, 1, lz, "st_tarp_" + tarp[k]);
                if (ix % 2 == 1 && lz % 2 == 1 && chance() < 0.7) {
                    fset(lx, 2, lz, switch (k) {
                        case 0, 1 -> (chance() < 0.3 ? "st_stretcher_bag" : "st_stretcher") + "[facing=north]";
                        case 2 -> "sc_cot_head[facing=north]";
                        default -> "bunk_bodybag[facing=" + (chance() < 0.5 ? "north" : "east") + "]";
                    });
                } else if (k < 2 && chance() < 0.08) {
                    fset(lx, 2, lz, "bunk_iv_stand");
                }
            }
        }
        // the stores: pallets in blocks in the north end zone
        for (int lx = -22; lx <= 21; lx++) {
            for (int lz = -55; lz <= -47; lz++) {
                if (Math.floorMod(lx + 22, 5) < 3 && Math.floorMod(lz + 55, 4) < 2 && chance() < 0.85) {
                    fset(lx, 1, lz, SUPPLY[rng.nextInt(7)] + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        // light towers at the corners of the field, burning on their gensets
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                int lx = sx < 0 ? -28 : 27, lz = sz < 0 ? -50 : 49;
                String in = Math.abs(lx) > Math.abs(lz) ? (sx < 0 ? "east" : "west") : (sz < 0 ? "south" : "north");
                fset(lx, 1, lz, "st_lt_base[facing=" + in + "]");
                for (int y = 2; y <= 8; y++) {
                    fset(lx, y, lz, "st_lt_mast");
                }
                fset(lx, 9, lz, (chance() < 0.75 ? "st_lt_head_on" : "st_lt_head") + "[facing=" + (sz < 0 ? "south" : "north") + "]");
            }
        }
        out.car(cx + 27.5, 1, cz + 42.5, 0, 5.5, 7, rng.nextLong());
        out.car(cx - 29.5, 1, cz - 50.5, 0, 7, 10, rng.nextLong());
        // the night it fell
        for (int lx = -40; lx < 40; lx++) {
            for (int lz = -60; lz < 60; lz++) {
                if (edge(lx + 0.5, lz + 0.5) < -2 && chance() < 0.006) {
                    double r = chance();
                    fset(lx, 1, lz, r < 0.4 ? "blood_splat" : r < 0.6 ? "home_blood_drag[facing=" + DIRS[rng.nextInt(4)] + "]" : r < 0.8 ? "paper"
                            : "trash_bag[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
    }

    /** The gooseneck goal post behind the end line (e: -1 north, 1 south). */
    private void goalPost(int e) {
        int line = e < 0 ? -57 : 56, base = line + 2 * e;
        fset(-1, 1, base, "sc_goal_pad");
        fset(-1, 2, base, "sc_goal_post");
        for (int lz = Math.min(base, line); lz <= Math.max(base, line); lz++) {
            fset(-1, 3, lz, "sc_goal_bar[axis=z]");
        }
        for (int lx = -3; lx <= 2; lx++) {
            fset(lx, 3, line, "sc_goal_bar[axis=x]");
        }
        for (int lx : new int[] {-3, 2}) {
            for (int y = 4; y <= 11; y++) {
                fset(lx, y, line, "sc_goal_post");
            }
            fset(lx, 12, line, "sc_goal_top");
        }
    }

    /**
     * A shelter tent on the field, six across from lx and len along from lz, a ridge down its
     * length, its door and its word on the north end; collapsed, its canvas lies where it fell.
     */
    private void tent(int lx, int lz, int len, String label, boolean collapsed) {
        if (collapsed) {
            for (int x = 0; x < 6; x++) {
                for (int z = 0; z < len; z++) {
                    if (chance() < 0.6) {
                        fset(lx + x, 1, lz + z, (chance() < 0.5 ? "st_tent_roof_lo" : "st_tent_roof_hi") + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                    } else if (chance() < 0.15) {
                        fset(lx + x, 1, lz + z, "sc_cot_foot[facing=" + DIRS[rng.nextInt(4)] + "]");
                    }
                }
            }
            return;
        }
        for (int z = 0; z < len; z++) {
            for (int y = 1; y <= 2; y++) {
                fset(lx, y, lz + z, "st_tent_wall[facing=west]");
                fset(lx + 5, y, lz + z, "st_tent_wall[facing=east]");
            }
            fset(lx, 3, lz + z, "st_tent_roof_lo[facing=west]");
            fset(lx + 1, 3, lz + z, "st_tent_roof_hi[facing=west]");
            fset(lx + 2, 4, lz + z, "st_tent_roof_lo[facing=west]");
            fset(lx + 5, 3, lz + z, "st_tent_roof_lo[facing=east]");
            fset(lx + 4, 3, lz + z, "st_tent_roof_hi[facing=east]");
            fset(lx + 3, 4, lz + z, "st_tent_roof_lo[facing=east]");
        }
        for (int end = 0; end < 2; end++) {
            int z = end == 0 ? lz : lz + len - 1;
            String f = end == 0 ? "north" : "south";
            for (int x = 1; x <= 4; x++) {
                boolean door = end == 0 && (x == 2 || x == 3);
                for (int y = 1; y <= 2; y++) {
                    fset(lx + x, y, z, door ? "air" : "st_tent_wall[facing=" + f + "]");
                }
                if (door) {
                    fset(lx + x, 2, z, "st_tent_flap[facing=" + f + "]");
                }
            }
            // the word on the gable: tile 0 at the reader's left
            fset(lx + (end == 0 ? 3 : 2), 3, z, "st_tent_" + label + "_0[facing=" + f + "]");
            fset(lx + (end == 0 ? 2 : 3), 3, z, "st_tent_" + label + "_1[facing=" + f + "]");
        }
        for (int z = lz + 2; z < lz + len - 2; z += 2) {
            for (int x : new int[] {lx + 1, lx + 4}) {
                if (chance() < 0.85) {
                    boolean triage = label.equals("triage");
                    fset(x, 1, z, (triage ? "st_stretcher" : chance() < 0.5 ? "sc_cot_head_b" : "sc_cot_head") + "[facing=north]");
                    if (!triage) {
                        fset(x, 1, z + 1, "sc_cot_foot[facing=north]");
                    } else if (chance() < 0.5) {
                        fset(x, 1, z + 1, "bunk_iv_stand");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the overflow camp

    private int[] campBox;

    /** Where the overflow camp goes: its rectangle in the lot's south-west corner relative to the centre (x0, z0, x1, z1), or null without the room. */
    static int[] camp(int cx, int cz, int w, int d) {
        int x0 = -cx + 6, z1 = d - cz - 6, x1 = x0 + 64, z0 = z1 - 70;
        for (int[] c : new int[][] {{x1, z0}, {x1, z1}, {x0, z0}}) {
            if (edge(c[0], c[1]) < FACADE + 22 || nearRamp(c[0], c[1], 14)) {
                return null;
            }
        }
        return new int[] {x0, z0, x1, z1};
    }

    static boolean inCamp(int[] box, int lx, int lz) {
        return box != null && lx >= box[0] - 2 && lx <= box[2] + 2 && lz >= box[1] - 2 && lz <= box[3] + 2;
    }

    /** Within r of a ramp tower's outside. */
    static boolean nearRamp(double px, double pz, double r) {
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                Ray ray = corner(sx, sz, RAMP_DEG);
                if (Math.hypot(px - ray.x(RAMP_AT), pz - ray.z(RAMP_AT)) < RAMP_OUT + 1 + r) {
                    return true;
                }
            }
        }
        return false;
    }

    Stadium withCamp(int[] box) {
        campBox = box;
        return this;
    }

    /**
     * The overflow camp FEMA put up in the south-west lot when the bowl was full: tents in rows
     * inside a chain-link fence topped with wire, the toilets down one side, a light tower, the
     * water on pallets, cots in the open; its gate toward the stadium torn open.
     */
    private void overflowCamp() {
        if (campBox == null) {
            return;
        }
        int x0 = campBox[0], z0 = campBox[1], x1 = campBox[2], z1 = campBox[3], gz = (z0 + z1) / 2;
        // chain-link on posts every eight, at the corners and either side of the gate
        for (int lx = x0; lx <= x1; lx++) {
            boolean post = (lx - x0) % 8 == 0 || lx == x1;
            fence(lx, z0, "x", post);
            fence(lx, z1, "x", post);
        }
        for (int lz = z0 + 1; lz < z1; lz++) {
            boolean post = (lz - z0) % 8 == 0;
            fence(x0, lz, "z", post);
            if (Math.abs(lz - gz) > 2) {
                fence(x1, lz, "z", post || Math.abs(lz - gz) == 3);
            }
        }
        // the tents, four rows of four
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                int lx = x0 + 4 + i * 11, lz = z0 + 4 + j * 16;
                if (lx + 6 <= x1 - 8 && lz + 12 <= z1 - 2) {
                    tent(lx, lz, 12, j == 3 && i == 0 ? "supply" : "shelter", chance() < 0.25);
                }
            }
        }
        // the toilets down the east side, doors west; the light tower and the water by the gate
        for (int lz = z0 + 6; lz < z0 + 22; lz += 2) {
            if (chance() < 0.9) {
                fset(x1 - 3, 1, lz, "st_porta_lo[facing=west]");
                fset(x1 - 3, 2, lz, "st_porta_hi[facing=west]");
            }
        }
        fset(x1 - 5, 1, gz - 6, "st_lt_base[facing=west]");
        for (int y = 2; y <= 8; y++) {
            fset(x1 - 5, y, gz - 6, "st_lt_mast");
        }
        fset(x1 - 5, 9, gz - 6, (chance() < 0.6 ? "st_lt_head_on" : "st_lt_head") + "[facing=west]");
        for (int lx = x1 - 7; lx <= x1 - 4; lx++) {
            for (int lz = gz + 4; lz <= gz + 8; lz++) {
                if (chance() < 0.7) {
                    fset(lx, 1, lz, SUPPLY[rng.nextInt(5)] + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        // what was left: cots in the open, the dead, the trash
        for (int lx = x0 + 2; lx < x1 - 1; lx++) {
            for (int lz = z0 + 2; lz < z1 - 1; lz++) {
                String g = out.get(cx + lx, 1, cz + lz);
                if (g != null && !g.equals("air") || chance() > 0.03) {
                    continue;
                }
                double r = chance();
                String f = "[facing=" + DIRS[rng.nextInt(4)] + "]";
                fset(lx, 1, lz, r < 0.25 ? "blood_splat" : r < 0.45 ? "trash_bag" + f : r < 0.6 ? "paper" : r < 0.75 ? "bunk_bodybag" + f
                        : r < 0.88 ? "sc_cases_water" + f : "cardboard_box" + f);
            }
        }
    }

    /** A run of the camp's fence: chain link two high, a panel down here and there, wire along the top. */
    private void fence(int lx, int lz, String axis, boolean post) {
        if (post) {
            for (int y = 1; y <= 3; y++) {
                fset(lx, y, lz, "fence_post");
            }
            return;
        }
        boolean down = chance() < 0.06;
        fset(lx, 1, lz, down ? "air" : "chainlink[axis=" + axis + "]");
        fset(lx, 2, lz, down || chance() < 0.1 ? "air" : "chainlink[axis=" + axis + "]");
        if (!down && chance() < 0.7) {
            fset(lx, 3, lz, "st_razor");
        }
    }


    // ------------------------------------------------------------------ the stair cores

    /**
     * Eight stairs from the undercroft to the 200 and 300 concourses, standing in the walks with
     * their length along the ring and the way out at the end.
     */
    /**
     * A way onto the roof at each end: a flight up the back row from the top of the aisle east of
     * the middle, two wide and climbing a block a cell, out through the roof into a block hut with
     * a steel door onto the roof deck.
     */
    private void roofStairs() {
        int top = upTread(UP_LAST), t0 = 9, deck = ROOF + 1;
        for (int s = 0; s <= 2; s += 2) {
            for (int i = 0; i <= 11; i++) {
                int t = t0 + i;
                for (int b = UP_LAST - 2; b <= FACADE; b++) {
                    int[] c = cell(s, t, b);
                    boolean flight = b >= UP_LAST - 1 && b <= UP_LAST;
                    if (flight && i <= 9) {
                        for (int y = top + 1; y < top + 1 + i; y++) {
                            set(c[0], y, c[1], worn("st_precast_plain", c[0], y, c[1]));
                        }
                        set(c[0], top + 1 + i, c[1], STAIR + "[facing=east]");
                        // four clear over each step, and up through the roof into the hut
                        for (int y = top + 2 + i; y <= (i > 5 ? deck + 3 : top + 5 + i); y++) {
                            set(c[0], y, c[1], "air");
                        }
                    }
                    if (i < 5) {
                        continue;
                    }
                    // the hut over the flight's head, cells 5..11
                    boolean inside = flight && i > 5 && i < 11;
                    if (inside && i == 10) {
                        set(c[0], ROOF, c[1], SLAB);
                        for (int y = deck; y < deck + 4; y++) {
                            set(c[0], y, c[1], "air");
                        }
                    }
                    if (!inside) {
                        for (int y = b == FACADE ? deck + 1 : deck; y < deck + 4; y++) {
                            set(c[0], y, c[1], "st_cmu");
                        }
                    }
                    set(c[0], deck + 4, c[1], "concrete_panel");
                }
            }
            int[] d = cell(s, t0 + 11, UP_LAST - 1);
            Doors.abandoned(p, rng, d[0], deck, d[1], Doors.STEEL, "east", Doors.hinge(rng));
            int[] l = cell(s, t0 + 10, UP_LAST);
            set(l[0], deck + 2, l[1], light("fit_sconce") + "[facing=" + (s == 0 ? "south" : "north") + "]");
        }
    }

    private static int coreSide(Ray r) {
        return r.nx() > 0 ? 1 : r.nx() < 0 ? 3 : r.nz() > 0 ? 2 : 0;
    }

    private static int coreT(Ray r) {
        return (int) Math.floor((coreSide(r) & 1) == 1 ? r.bz() : r.bx());
    }

    private void cores() {
        for (Ray r : cores) {
            int s = coreSide(r), t = coreT(r);
            Placer q = room(s, t - 5, 10, 79, 6, true, 0);
            Stairwell w = new Stairwell(q, rng, 1, 1, 0, 8);
            w.landing = SLAB;
            w.lamp = "fit_downlight_on";
            w.build(6, new String[] {"west", null, null, "west", null, "west"}, false, null);
        }
    }
}
