package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Union Station's train hall, put up across the avenue the el ran down when the railway brought
 * its trains in under cover in the late eighties: the el comes in through a portal in each end
 * wall and runs straight down the middle of the station's top floor between its two platforms,
 * under a flat glass roof held up on two rows of white steel trees, each a trunk branching into
 * eight arms that land on the roof's I-beams. Drawn from what is known of the steel-tree halls
 * (Lisbon's Oriente, Stuttgart's and Berlin's new stations); no photos of them were in the library
 * when it was made. Built with the tx_ kit (tools/city/transit.py), the town stations' own.
 *
 * <p>Under the platforms the ticket hall: long and low under a metal ceiling with its strip
 * lights, glass to the street at both ends and on the bus side, the ticket office and the ticket
 * machines, a kiosk at each end, the gates across it either side of the paid middle where the four
 * banks of stairs climb to the platforms, an escalator beside each, still running (one lane up, one down).
 * Doors through to the head house (the subway's wing, the concourse) and out to the bus and taxi
 * terminal under its glass canopies on the hall's far side, the buses that were the evacuation's
 * last way out still in their bays.
 *
 * <p>Drawn after {@link UnionStation} in the same lot frame (its front along z = 0, x = 0 the head
 * house's far side, y = 0 the street), from the head house's wall at x = {@link #HE} to the lot's
 * far side. The el's centre line runs between x = 66 and 67; RailGen lays its bed through the hall
 * (x 65 .. 68, the deck under it, the trains' room over it) and nothing else, everything round it
 * is drawn here. Trains stop at platform 1, on the head house's side; the old bridge door from the
 * mezzanine (UnionStation's BRIDGE_Z0 .. BRIDGE_Z1) opens onto it.
 */
final class UnionHall {
    /** The head house's wall (its x = W - 1), the hall's west wall. */
    static final int HE = UnionStation.W - 1, HW = 82;
    /** The platforms' edges either side of the trains' room. */
    static final int E1 = 64, E2 = 69;
    /** The end walls, where the el comes in. */
    static final int Z0 = 1, Z1 = 131;
    /** The concourse's ceiling, the deck, the platforms' slab (standing a block over it), the trees' forks, the roof. */
    static final int CEIL = 9, DECK = 10, PLAT = 11, FORK = 20, ROOF = 26;
    /** The steel trees, one row down each platform, a wide gap either side of the middle for the stairs. */
    private static final int[] TREE_X = {58, 75}, TREE_Z = {10, 26, 42, 66, 90, 106, 122};
    /** The roof's long beams, over the trees and the tips of their arms. */
    private static final int[] BEAM_X = {53, 58, 63, 70, 75, 80};
    /** The gate lines across the concourse, the paid middle between them. */
    private static final int GATE_S = 36, GATE_N = 96;
    /** The banks' feet: A climbs toward +z, B toward -z, both to the platforms' middle. */
    private static final int FOOT_A = 43, FOOT_B = 89;
    /** The posts of the end walls (the portal between 64 and 69), and their doors' left leaves. */
    private static final int[] POSTS = {51, 55, 59, 64, 69, 74, 78, 82}, END_DOORS = {56, 61, 66, 71, 76};
    /** The doors out of the west wall to the terminal, all in the unpaid ends. */
    private static final int[] WEST_DOORS = {6, 20, 104, 120};
    /** The benches down each platform, in threes between the trees, none by the stairs. */
    private static final int[] BENCH_Z = {14, 15, 16, 20, 21, 22, 30, 31, 32, 36, 37, 38, 58, 59, 60, 72, 73, 74, 94, 95, 96,
            100, 101, 102, 110, 111, 112, 116, 117, 118};
    /** The terminal's bands across x: the taxi lane, the taxi island, a bus lane, the bus island, a bus lane, the plaza. */
    private static final int TAXI = 87, ISLE1 = 93, BUS1 = 100, ISLE2 = 108, BUS2 = 115, PLAZA = 123;

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d;
    private final boolean power;
    /** The roof's cross beams: through each tree and the tips of its arms, and across the long gaps. */
    private final boolean[] cross;

    UnionHall(Placer p, SplittableRandom rng, int w, int d, boolean power) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.power = power;
        this.cross = new boolean[d];
        for (int tz : TREE_Z) {
            cross[tz - 5] = cross[tz] = cross[tz + 5] = true;
        }
        cross[54] = cross[78] = true;
    }

    void build() {
        shell();
        ends();
        sides();
        roof();
        trees();
        for (boolean two : new boolean[] {false, true}) {
            bank(two, FOOT_A, 1);
            bank(two, FOOT_B, -1);
        }
        concourse();
        platform(false);
        platform(true);
        terminal();
        wear();
    }

    private void set(int x, int y, int z, String block) {
        p.set(x, y, z, block);
    }

    private double chance() {
        return rng.nextDouble();
    }

    private boolean empty(int x, int y, int z) {
        String b = p.get(x, y, z);
        return b == null || b.equals("air");
    }

    /** x on platform 2's side: the hall is the same either side of the track, mirrored. */
    private static int m(int x) {
        return HE + HW - x;
    }

    private static int side(boolean two, int x) {
        return two ? m(x) : x;
    }

    private static boolean in(int[] xs, int x) {
        for (int v : xs) {
            if (v == x) {
                return true;
            }
        }
        return false;
    }

    /** The concourse's strip lights: most of them dead, a few still stuttering on the army's generator. */
    private String strip() {
        return power && chance() < 0.18 ? "tx_ceiling_lamp[axis=z]" : "tx_ceiling_lamp_off[axis=z]";
    }

    private String pendant() {
        return power && chance() < 0.25 ? "tx_pendant" : "tx_pendant_off";
    }

    /** A pane of the hall's glass along the axis: whole mostly; low down the looters' work shows. */
    private String pane(String axis, boolean transom, boolean low) {
        double r = chance(), broken = low ? 0.12 : 0.05, frame = low ? 0.06 : 0.03;
        String g = r < broken ? "tx_glass_broken" : r < broken + 0.1 ? "tx_glass_cracked" : r < broken + 0.1 + frame ? "tx_glass_frame"
                : transom ? "tx_glass_t" : "tx_glass";
        return g + "[axis=" + axis + "]";
    }

    /** A post of the glass walls: its foot on the floor or the slab, a cap under the roof. */
    private void post(int x, int z, int y0, int y1) {
        set(x, y0, z, "tx_column_base");
        for (int y = y0 + 1; y < y1; y++) {
            set(x, y, z, "tx_column");
        }
        set(x, y1, z, "tx_column_cap");
    }

    /** A pendant hung on a rod from the roof's steel at y + 1 .. the roof. */
    private void hang(int x, int y, int z, String block) {
        set(x, y, z, block);
        for (int yy = y + 1; yy < ROOF; yy++) {
            set(x, yy, z, "tx_rod");
        }
    }

    /** A two-tile hung sign facing the given way, tile 0 at the reader's left; top: rods up to it (0: none). */
    private void sign(String what, int x, int y, int z, String facing, int top) {
        boolean alongX = facing.equals("north") || facing.equals("south");
        // a reader facing a sign that faces north or east has the higher x or z on their left
        boolean flip = facing.equals("north") || facing.equals("east");
        for (int t = 0; t < 2; t++) {
            int at = flip ? 1 - t : t;
            int sx = alongX ? x + at : x, sz = alongX ? z : z + at;
            set(sx, y, sz, "tx_sign_" + what + "_" + t + "[facing=" + facing + "]");
            for (int yy = y + 1; yy < top; yy++) {
                set(sx, yy, sz, "tx_rod");
            }
        }
    }

    /** The departures board, four by two, facing north or south, hung back to back with its twin under the ceiling. */
    private void boards(int z) {
        for (int i = 0; i < 8; i++) {
            int col = i % 4, row = i / 4;
            set(68 - col, CEIL - 1 - row, z, "tx_board_" + i + "[facing=north]");
            set(65 + col, CEIL - 1 - row, z + 1, "tx_board_" + i + "[facing=south]");
        }
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int x = HE + 1; x < HW; x++) {
            boolean track = x > E1 && x < E2;
            for (int z = Z0 + 1; z < Z1; z++) {
                boolean band = x == HE + 1 || x == HW - 1 || z == Z0 + 1 || z == Z1 - 1 || z == GATE_S || z == GATE_N || x == E1 || x == E2;
                set(x, 0, z, band ? "tx_terrazzo_d" : chance() < 0.12 ? "tx_terrazzo_b" : "tx_terrazzo");
                for (int y = 1; y < CEIL; y++) {
                    set(x, y, z, "air");
                }
                // the deck under the platforms (RailGen's under the bed); the platforms on it, their edges to the track
                if (!track) {
                    set(x, CEIL, z, "tx_ceiling");
                    set(x, DECK, z, "tx_concrete");
                    set(x, PLAT, z, x == E1 ? "tx_edge[facing=east]" : x == E2 ? "tx_edge[facing=west]"
                            : chance() < 0.1 ? "tx_paver_b" : "tx_paver");
                }
                for (int y = track ? PLAT + 7 : PLAT + 1; y < ROOF; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        // the strip lights down the concourse's ceiling under each platform, in threes
        for (int x : new int[] {54, 59, m(59), m(54)}) {
            for (int z = Z0 + 2; z < Z1 - 1; z++) {
                if (z % 4 != 0) {
                    set(x, CEIL, z, strip());
                }
            }
        }
    }

    /**
     * The end walls: glass to the street on the concourse's floor with five pairs of doors, the
     * deck's edge, glass again up to the roof with the station's name over the portal the el comes
     * in by. z = Z0 faces the front (north in the lot's frame), Z1 the back.
     */
    private void ends() {
        for (int z : new int[] {Z0, Z1}) {
            String out = z == Z0 ? "north" : "south";
            for (int x = HE; x <= HW; x++) {
                set(x, 0, z, "tx_granite");
                boolean post = in(POSTS, x);
                boolean portal = x > E1 && x < E2;
                for (int y = 1; y < ROOF; y++) {
                    if (portal && y >= CEIL && y <= FORK - 2) {
                        continue;    // RailGen's: the deck, the bed, the trains' room
                    }
                    String b;
                    if (y == CEIL || y == DECK) {
                        b = "tx_panel";
                    } else if (y == PLAT) {
                        b = "tx_concrete";
                    } else if (post) {
                        b = y == 1 || y == PLAT + 1 ? "tx_column_base" : y == CEIL - 1 || y == ROOF - 1 ? "tx_column_cap" : "tx_column";
                    } else if (y < CEIL) {
                        b = pane("x", y == 1 || y == 5, true);
                    } else {
                        b = pane("x", (y - PLAT - 1) % 4 == 0, false);
                    }
                    set(x, y, z, b);
                }
            }
            for (int dx : END_DOORS) {
                Doors.pair(p, rng, dx, 1, z, "tx_door", z == Z0 ? "south" : "north");
                set(dx, 3, z, pane("x", true, true));
                set(dx + 1, 3, z, pane("x", true, true));
            }
            // the portal: posts either side (the el's guard beams), a lintel, the name over it
            for (int x = E1; x <= E2; x++) {
                set(x, FORK - 1, z, "tx_beam[axis=x]");
            }
            for (int i = 0; i < 8; i++) {
                set(z == Z0 ? E2 + 1 - i : E1 - 1 + i, FORK + 1, z, "tx_name_union_" + i + "[facing=" + out + "]");
            }
            // the el's deck carried on out over the sidewalk to RailGen's viaduct, its guard rails each side
            int zo = z == Z0 ? z - 1 : z + 1;
            for (int x = HE; x <= HW; x++) {
                set(x, 0, zo, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
            }
            for (int zz : new int[] {z, zo}) {
                for (int x : new int[] {E1, E2}) {
                    set(x, DECK, zz, "rw_ballast");
                    set(x, CEIL, zz, "rw_deck");
                }
                for (int x : new int[] {E1 - 1, E2 + 1}) {
                    if (zz == zo) {
                        set(x, DECK, zz, "rw_grate");
                        set(x, CEIL, zz, "rw_girder");
                        set(x, CEIL - 1, zz, "rw_girder");
                        set(x, PLAT, zz, "rw_guard[facing=" + (x < E1 ? "west" : "east") + "]");
                    }
                }
            }
            for (int x = E1; x <= E2; x += E2 - E1) {
                for (int y = PLAT; y < FORK - 1; y++) {
                    set(x, y, z, y == PLAT ? "tx_concrete" : y == PLAT + 1 ? "tx_column_base" : "tx_column");
                }
            }
        }
    }

    /**
     * The long sides. On the head house's side the hall stands against its wall, glass from the
     * wall's top to the roof (and the whole height in front of the head house's forecourt). On the
     * terminal's side: glass on the concourse's floor with doors in the unpaid ends, the deck's edge,
     * glass over it to the roof; posts every eight.
     */
    private void sides() {
        for (int z = Z0; z <= Z1; z++) {
            int top = 0;
            for (int y = ROOF - 1; y > 0; y--) {
                if (!empty(HE, y, z)) {
                    top = y;
                    break;
                }
            }
            boolean open = z < UnionStation.ZF;   // the head house's forecourt: no wall of its own
            for (int y = open ? 0 : top + 1; y < ROOF; y++) {
                if (z == Z0 || z == Z1) {
                    break;
                }
                String b;
                if (y == 0) {
                    b = "tx_granite";
                } else if (open && z == 5) {
                    b = y == 1 || y == PLAT + 1 ? "tx_column_base" : "tx_column";
                } else if (y == CEIL || y == DECK) {
                    b = "tx_panel";
                } else if (y == PLAT) {
                    b = "tx_concrete";
                } else {
                    b = pane("z", y == 1 || y == 5 || y > PLAT && (y - PLAT - 1) % 4 == 0, y < CEIL);
                }
                set(HE, y, z, b);
            }
        }
        for (int z = Z0 + 1; z < Z1; z++) {
            boolean post = Math.floorMod(z - 2, 8) == 0;
            set(HW, 0, z, "tx_granite");
            for (int y = 1; y < ROOF; y++) {
                String b;
                if (post) {
                    b = y == 1 ? "tx_column_base" : y == ROOF - 1 ? "tx_column_cap" : "tx_column";
                } else if (y == CEIL || y == DECK) {
                    b = "tx_panel";
                } else if (y == PLAT) {
                    b = "tx_concrete";
                } else {
                    b = pane("z", y == 1 || y == 5 || y > PLAT && (y - PLAT - 1) % 4 == 0, y < CEIL);
                }
                set(HW, y, z, b);
            }
        }
        for (int z : WEST_DOORS) {
            Doors.abandoned(p, rng, HW, 1, z, "tx_door", "west", "right");
            Doors.abandoned(p, rng, HW, 1, z + 1, "tx_door", "west", "left");
        }
        // through to the head house: the subway's wing, and its concourse by the field hospital
        for (int z : new int[] {22, 100}) {
            for (int zz = z; zz <= z + 1; zz++) {
                for (int y = 1; y <= 3; y++) {
                    set(HE - 1, y, zz, "air");
                }
            }
            Doors.abandoned(p, rng, HE, 1, z, "tx_door", "west", "right");
            Doors.abandoned(p, rng, HE, 1, z + 1, "tx_door", "west", "left");
        }
        // the corners
        for (int z : new int[] {Z0, Z1}) {
            for (int x : new int[] {HE, HW}) {
                post(x, z, 1, ROOF - 1);
            }
        }
    }

    // ------------------------------------------------------------------ the roof

    private void roof() {
        for (int x = HE + 1; x < HW; x++) {
            boolean beam = in(BEAM_X, x);
            for (int z = Z0 + 1; z < Z1; z++) {
                String b;
                if (beam && cross[z]) {
                    b = "tx_vault_node";
                } else if (beam || cross[z]) {
                    b = "tx_vault_beam[axis=" + (beam ? "z" : "x") + "]";
                } else {
                    double r = chance();
                    b = r < 0.04 ? "tx_vault_frame" : r < 0.13 ? "tx_vault_cracked" : "tx_vault";
                }
                set(x, ROOF, z, b);
            }
        }
        for (int x = HE; x <= HW; x++) {
            for (int z : new int[] {Z0, Z1}) {
                set(x, ROOF, z, "tx_fascia");
                set(x, ROOF + 1, z, "tx_parapet");
            }
        }
        for (int z = Z0; z <= Z1; z++) {
            for (int x : new int[] {HE, HW}) {
                set(x, ROOF, z, "tx_fascia");
                set(x, ROOF + 1, z, "tx_parapet");
            }
        }
        // the roofmen's ladder up from the head house wing's roof, through a gap in the parapet
        for (int y = UnionStation.WING_TOP + 1; y <= ROOF; y++) {
            set(HE - 1, y, 30, "ladder[facing=west]");
        }
        set(HE, ROOF + 1, 30, "air");
    }

    /** The steel trees: a foot on the platform, the trunk, the fork, and eight arms rising a block a block to the roof's beams. */
    private void trees() {
        int[][] orth = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        // the way a diagonal arm facing north .. west runs (drawn toward north-east)
        int[][] diag = {{1, -1}, {1, 1}, {-1, 1}, {-1, -1}};
        for (int tx : TREE_X) {
            for (int tz : TREE_Z) {
                set(tx, PLAT + 1, tz, "tx_tree_base");
                for (int y = PLAT + 2; y < FORK; y++) {
                    set(tx, y, tz, "tx_tree");
                }
                set(tx, FORK, tz, "tx_tree_fork");
                for (int f = 0; f < 4; f++) {
                    for (int k = 1; k < ROOF - FORK; k++) {
                        set(tx + orth[f][0] * k, FORK + k, tz + orth[f][1] * k, "tx_branch[facing=" + Bldg.DIRS[f] + "]");
                        set(tx + diag[f][0] * k, FORK + k, tz + diag[f][1] * k, "tx_branch_d[facing=" + Bldg.DIRS[f] + "]");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the stairs

    /**
     * A bank of stairs up to a platform from the concourse: a solid stringer each side, the escalator
     * on the wall side (two wide), the granite stairs (three) on the track side, the slab opened over
     * them and railed round at the platform's level. foot: the floor's block before the first step;
     * s: the way up along z.
     */
    private void bank(boolean two, int foot, int s) {
        String up = s > 0 ? "south" : "north", down = s > 0 ? "north" : "south";
        int lo = two ? m(61) : 55, e0 = two ? m(57) : 56, top = foot + 12 * s;
        for (int k = 1; k <= PLAT; k++) {
            int z = foot + s * k;
            for (int x = lo; x <= lo + 6; x++) {
                for (int y = 1; y < k; y++) {
                    set(x, y, z, clad(y));
                }
                if (x == lo || x == lo + 6) {
                    set(x, k, z, clad(k));
                    boolean byEscalator = x == e0 - 1 || x == e0 + 2;
                    if (!byEscalator && k + 1 < CEIL) {
                        set(x, k + 1, z, "tx_glass_rail[axis=z]");
                    }
                    // the slab carried on over the stringer, railed along the opening
                    set(x, PLAT + 1, z, "tx_glass_rail[axis=z]");
                    continue;
                }
                boolean esc = x == e0 || x == e0 + 1;
                // the escalator's two lanes: up on the right as you climb, down on the left
                boolean upLane = (x == e0) == (s > 0);
                set(x, k, z, esc ? "zomboidcraft:escalator[facing=" + up + ",run=" + (upLane ? "up" : "down") + "]"
                        : "tx_step[facing=" + up + "]");
                for (int y = Math.max(k + 1, CEIL); y <= PLAT; y++) {
                    set(x, y, z, "air");
                }
                if (esc) {
                    // the balustrade over the step, on the escalator's left or right as you ride up
                    boolean left = (x == e0 + 1) == (s > 0);
                    set(x, k + 1, z, "tx_esc_side_" + (left ? "l" : "r") + "[facing=" + up + "]");
                }
            }
        }
        for (int x = lo + 1; x <= lo + 5; x++) {
            // the closed end at the platform's level, over the foot
            set(x, PLAT + 1, foot, "tx_glass_rail[axis=x]");
            if (x == e0 || x == e0 + 1) {
                String hand = (x == e0 + 1) == (s > 0) ? "l" : "r";
                set(x, 0, foot, "tx_esc_plate[facing=" + up + "]");
                set(x, 1, foot, "tx_esc_newel_" + hand + "[facing=" + down + "]");
                set(x, PLAT, top, "tx_esc_plate[facing=" + down + "]");
                set(x, PLAT + 1, top, "tx_esc_newel_" + hand + "[facing=" + up + "]");
            }
        }
        // under the bank: the network map on its outer side and on its high end, the notices, the platform's number over its foot
        int face = two ? lo + 7 : lo - 1;
        String out = two ? "east" : "west";
        for (int i = 0; i < 6; i++) {
            int col = i % 3, row = i / 3, k0 = 5;
            int z = foot + s * (k0 + (two == s > 0 ? 2 - col : col));
            set(face, 3 - row, z, "tx_map_union_" + i + "[facing=" + out + "]");
            set(s > 0 ? lo + 2 + col : lo + 4 - col, 4 - row, top, "tx_map_union_" + i + "[facing=" + up + "]");
        }
        for (int k = 3; k <= 9; k += 6) {
            if (chance() < 0.7) {
                set(face, 2, foot + s * k, "us_notice_" + rng.nextInt(3) + "[facing=" + out + "]");
            }
        }
        if (chance() < 0.5) {
            set(face, 3 + rng.nextInt(3), foot + s * 10, "graffiti_" + rng.nextInt(8) + "[facing=" + (two ? "west" : "east") + "]");
        }
        set(lo + 3, CEIL - 1, foot - s, "tx_platnum_" + (two ? 2 : 1) + "[axis=x]");
    }

    /** The banks' cladding: a granite plinth, tall panels three courses high over it, a third of them streaked. */
    private String clad(int y) {
        if (y == 1) {
            return "tx_granite";
        }
        String part = (y - 2) % 3 == 0 ? "lo" : (y - 2) % 3 == 1 ? "mid" : "hi";
        return "tx_panel_" + part + (chance() < 0.3 ? "_s" : "");
    }

    // ------------------------------------------------------------------ the concourse

    private void concourse() {
        // the columns under the deck, in line with the trees
        for (int x : new int[] {53, 63, m(63), m(53)}) {
            for (int z : TREE_Z) {
                set(x, 1, z, "tx_column_base");
                for (int y = 2; y < CEIL - 1; y++) {
                    set(x, y, z, "tx_column");
                }
                set(x, CEIL - 1, z, "tx_column_cap");
            }
        }
        // lamps hung under the el's deck down the middle
        for (int z = 6; z < Z1; z += 8) {
            set(66, CEIL - 1, z, pendant());
            set(67, CEIL - 1, z, pendant());
        }
        gates(GATE_S);
        gates(GATE_N);
        ticketOffice();
        kiosks();
        // the ticket machines on the terminal side of each unpaid end, a third of them kicked in for the coins
        for (int z0 : new int[] {12, 108}) {
            for (int z = z0; z < z0 + 6; z++) {
                boolean smashed = chance() < 0.35;
                set(HW - 1, 1, z, (smashed ? "tx_tvm_smashed" : "tx_tvm") + "[facing=west]");
                set(HW - 1, 2, z, (smashed ? "tx_tvm_top_smashed" : "tx_tvm_top") + "[facing=west]");
            }
        }
        // the departures boards under the el, back to back: in each unpaid end and in the paid middle
        boards(31);
        boards(66);
        boards(100);
        set(66, CEIL - 3, 66, "air");
        // the signs, under the ceiling
        sign("platforms", 66, CEIL - 1, GATE_S - 1, "north", 0);
        sign("platforms", 66, CEIL - 1, GATE_N + 1, "south", 0);
        sign("subway", HE + 2, CEIL - 1, 22, "east", 0);
        for (int z : WEST_DOORS) {
            sign("buses", HW - 2, CEIL - 1, z, "west", 0);
        }
        for (int x : new int[] {60, m(61)}) {
            sign("exit", x, CEIL - 1, GATE_S + 1, "south", 0);
            sign("exit", x, CEIL - 1, GATE_N - 1, "north", 0);
        }
        // the paid middle: benches back to back between the banks, the machines by the columns
        for (int z : new int[] {58, 74}) {
            for (int x = 54; x <= 61; x++) {
                for (int xx : new int[] {x, m(x)}) {
                    if (chance() < 0.85) {
                        set(xx, 1, z, "tx_bench[facing=north]");
                    }
                    if (chance() < 0.85) {
                        set(xx, 1, z + 1, "tx_bench[facing=south]");
                    }
                }
            }
        }
        for (int z : new int[] {64, 68}) {
            set(54, 1, z, "store_vending[facing=east,loot=drinks]");
            set(54, 2, z, "store_vending_top[facing=east]");
            set(m(54), 1, z, "store_vending[facing=west,loot=drinks]");
            set(m(54), 2, z, "store_vending_top[facing=west]");
        }
        set(66, 1, 61, "tx_bin");
        set(67, 1, 71, "tx_bin");
        // the last days: a check-in at the south gates, the rope lines of the queue for the trains,
        // the army's water and its crates, what people dropped
        for (int z = 23; z < GATE_S; z++) {
            set(64, 1, z, "trade_rope_post[axis=z]");
            set(69, 1, z, "trade_rope_post[axis=z]");
        }
        for (int x : new int[] {61, 72}) {
            set(x, 1, GATE_S - 2, "sc_desk_a[facing=north]");
            set(x, 2, GATE_S - 2, "sc_desk_things[facing=north]");
            set(x, 1, GATE_S - 1, chance() < 0.5 ? "office_chair_fallen[facing=west]" : "office_chair[facing=south]");
        }
        for (int z = 26; z <= 30; z++) {
            for (int x = 72; x <= 75; x++) {
                if (chance() < 0.6) {
                    double r = chance();
                    set(x, 1, z, (r < 0.5 ? "st_pallet_water" : r < 0.8 ? "bunk_crate_ration" : "pk_military_crate") + "[facing=north]");
                }
            }
        }
        for (int i = 0; i < 14; i++) {
            int x = 54 + rng.nextInt(26), z = 23 + rng.nextInt(12);
            if (empty(x, 1, z)) {
                set(x, 1, z, i % 3 == 0 ? "trade_luggage_cart[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]"
                        : "cardboard_box[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
            }
        }
        // sandbags piled inside the front's glass, clear of its doors
        for (int x = HE + 2; x < HW - 1; x++) {
            boolean door = false;
            for (int dx : END_DOORS) {
                door |= x >= dx - 1 && x <= dx + 2;
            }
            if (!door && chance() < 0.5) {
                set(x, 1, Z0 + 1, "sandbags[facing=north]");
            }
        }
    }

    /** A line of fare gates across the concourse: a cabinet every other block, the wide one under the el, glass at the ends. */
    private void gates(int z) {
        for (int x = HE + 1; x < HW; x++) {
            if (x <= 53 || x >= m(53)) {
                set(x, 1, z, "tx_glass_rail[axis=x]");
            } else if (x % 2 == 1 && x != 67) {
                set(x, 1, z, "tx_gate[axis=z]");
            }
        }
    }

    /** The ticket office against the head house in the front end: windows to the concourse, the departures over them. */
    private void ticketOffice() {
        int x0 = HE + 1, x1 = 57, z0 = 12, z1 = 19;
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y < CEIL; y++) {
                set(x, y, z0, y == 1 ? "tx_wall_base" : "tx_wall");
                set(x, y, z1, y == 1 ? "tx_wall_base" : "tx_wall");
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            set(x1, 1, z, "tx_ticket_counter[facing=east]");
            set(x1, 2, z, "tx_ticket_window[facing=east]");
            for (int y = 3; y < CEIL; y++) {
                set(x1, y, z, "tx_wall");
            }
            set(x1 - 1, 1, z, z == 15 ? "store_register[facing=east,loot=till]" : "store_counter[facing=east,loot=office]");
            if (chance() < 0.6) {
                set(x1 - 2, 1, z, chance() < 0.3 ? "office_chair_fallen[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]"
                        : "office_chair[facing=east]");
            }
        }
        Doors.abandoned(p, rng, 53, 1, z0, Doors.FLAT, "south", Doors.hinge(rng));
        set(x0, 1, z1 - 1, "store_safe[facing=east,loot=cash]");
        set(x0, 1, z0 + 2, "filing_cabinet[facing=east,loot=office]");
        set(x0, 1, z0 + 3, "filing_cabinet[facing=east,loot=office]");
        set(x0, 1, z0 + 4, "fit_locker_lo[facing=east]");
        set(x0, 2, z0 + 4, "fit_locker_hi[facing=east]");
        for (int i = 0; i < 8; i++) {
            int col = i % 4, row = i / 4;
            set(x1 + 1, 5 - row, 17 - col, "tx_board_" + i + "[facing=east]");
        }
        set(x1 + 1, 6, 15, "bk_wall_clock[facing=east]");
        sign("tickets", 60, CEIL - 1, 15, "east", 0);
    }

    private void kiosks() {
        // the newsstand under the el in the front end: a ring of counters, the papers, the till
        for (int x = 64; x <= 69; x++) {
            for (int z = 15; z <= 21; z++) {
                boolean ring = x == 64 || x == 69 || z == 15 || z == 21;
                if (ring && !(z == 21 && x == 66)) {
                    String f = x == 64 ? "west" : x == 69 ? "east" : z == 15 ? "north" : "south";
                    set(x, 1, z, (x == 67 && z == 15 ? "store_register" : "store_counter") + "[facing=" + f + ",loot="
                            + (x == 67 && z == 15 ? "till" : "grocery") + "]");
                    if (chance() < 0.3) {
                        set(x, 2, z, "gs_news_" + rng.nextInt(3) + "[facing=" + f + "]");
                    }
                } else if (!ring && (x == 65 || x == 68) && z >= 17 && z <= 19) {
                    set(x, 1, z, "store_shelf_grocery[facing=" + (x == 65 ? "east" : "west") + ",loot=grocery]");
                }
            }
        }
        sign("kiosk", 66, CEIL - 1, 15, "north", 0);
        // the kiosk in the back end against the head house: its counter open to the concourse, shelves and coolers behind
        int x0 = HE + 1, x1 = 57, z0 = 112, z1 = 120;
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y < CEIL; y++) {
                set(x, y, z0, y == 1 ? "tx_wall_base" : "tx_wall");
                set(x, y, z1, y == 1 ? "tx_wall_base" : "tx_wall");
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            if (z < z1 - 1) {
                set(x1, 1, z, z == 115 ? "store_register[facing=east,loot=till]" : "store_counter[facing=east,loot=grocery]");
            }
            for (int y = 4; y < CEIL; y++) {
                set(x1, y, z, "tx_wall");
            }
            set(x0, 1, z, "store_shelf_grocery[facing=east,loot=grocery]");
        }
        set(x0 + 1, 1, z0 + 1, "store_cooler[facing=south,loot=drinks]");
        set(x0 + 1, 2, z0 + 1, "store_cooler_top[facing=south]");
        set(x0 + 2, 1, z0 + 1, "store_cooler[facing=south,loot=drinks]");
        set(x0 + 2, 2, z0 + 1, "store_cooler_top[facing=south]");
        set(x0 + 3, 1, z1 - 1, "store_coffee[facing=north,loot=kitchen]");
        sign("kiosk", 60, CEIL - 1, 115, "east", 0);
    }

    // ------------------------------------------------------------------ the platforms

    private void platform(boolean two) {
        String face = two ? "west" : "east";
        int wall = side(two, HE + 1);
        // benches facing the track, bins between them
        for (int z : BENCH_Z) {
            double r = chance();
            if (r < 0.86) {
                set(side(two, 54), PLAT + 1, z, "tx_bench[facing=" + face + "]");
            } else if (r < 0.92) {
                set(side(two, 54), PLAT + 1, z, "trash_bag[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
            }
        }
        for (int z : new int[] {18, 34, 63, 69, 98, 114}) {
            set(side(two, 54), PLAT + 1, z, "tx_bin");
        }
        // on the back wall: the departures, the call buttons for the trains with a help point by each
        for (int z0 : new int[] {18, 62, 110}) {
            for (int i = 0; i < 8; i++) {
                int col = i % 4, row = i / 4;
                set(wall, PLAT + 5 - row, two ? z0 + col : z0 + 3 - col, "tx_board_" + i + "[facing=" + face + "]");
            }
        }
        for (int z : new int[] {30, 66, 102}) {
            set(wall, PLAT + 2, z, "zomboidcraft:train_call_button[facing=" + face + "]");
            set(wall, PLAT + 1, z + 1, "tx_help[facing=" + face + "]");
        }
        // the name boards hung over the platform's edge side, facing the track
        for (int z0 : new int[] {16, 54, 75, 113}) {
            int x = side(two, E1 - 1);
            for (int k = 0; k < 4; k++) {
                set(x, PLAT + 5, two ? z0 + k : z0 + 3 - k, "rw_name_union_" + k + "[facing=" + face + "]");
            }
            for (int y = PLAT + 6; y < ROOF; y++) {
                set(x, y, z0, "tx_rod");
                set(x, y, z0 + 3, "tx_rod");
            }
        }
        // the clocks and the platform's number, hung from the cross beams
        for (int z : new int[] {61, 71}) {
            hang(side(two, 61), PLAT + 7, z, "tx_clock[axis=x]");
        }
        for (int z : new int[] {37, 95}) {
            hang(side(two, 61), PLAT + 6, z, "tx_platnum_" + (two ? 2 : 1) + "[axis=x]");
        }
        // the lamps on rods from the long beams
        for (int z : new int[] {18, 34, 51, 81, 98, 114}) {
            hang(side(two, 53), PLAT + 8, z, pendant());
            if (z != 18 && z != 114) {
                hang(side(two, 63), PLAT + 8, z, pendant());
            }
        }
        // the way out over the head of each bank, facing along the platform
        int a = side(two, 58), b = side(two, 59), lo = Math.min(a, b);
        sign("exit", lo, PLAT + 5, FOOT_A + 11, "south", ROOF);
        sign("exit", lo, PLAT + 5, FOOT_B - 11, "north", ROOF);
    }

    // ------------------------------------------------------------------ the terminal

    private boolean lane(int x) {
        return x >= TAXI && x < ISLE1 || x >= BUS1 && x < ISLE2 || x >= BUS2 && x < PLAZA;
    }

    /**
     * The bus and taxi terminal on the hall's far side, the lanes running the lot's length from the
     * front street to the back: the sidewalk under the hall's glass, the taxi lane and the taxis'
     * island, the buses' two lanes either side of their island (both lanes' doors open onto it), the
     * plaza along the lot's edge. Glass canopies on steel over both islands.
     */
    private void terminal() {
        for (int x = HW + 1; x < w; x++) {
            boolean lane = lane(x);
            boolean curb = x == TAXI - 1 || x == ISLE1 || x == BUS1 - 1 || x == ISLE2 || x == BUS2 - 1 || x == PLAZA;
            boolean dash = x == 103 || x == 104 || x == 118 || x == 119;
            for (int z = 0; z < d; z++) {
                boolean end = z <= Z0 || z >= Z1;
                String g;
                if (lane) {
                    g = dash && x % 2 == 0 && Math.floorMod(z, 6) < 3 && !end ? "road_line_white[axis=z]" : asphalt(0.4);
                } else if (curb && !end) {
                    g = "curb";
                } else if (x >= PLAZA + 1 && !end) {
                    g = chance() < 0.1 ? "stone_trim_crack" : "pt_stone_flags";
                } else {
                    g = chance() < 0.1 ? "sidewalk_cracked" : "sidewalk";
                }
                set(x, 0, z, g);
            }
        }
        canopy(ISLE1, ISLE1 + 6);
        canopy(ISLE2, ISLE2 + 6);
        // the taxis' island: benches facing the taxis, the rank's sign
        for (int z = 14; z < 120; z += 10) {
            for (int dz = 0; dz < 3; dz++) {
                if (chance() < 0.85) {
                    set(ISLE1 + 1, 1, z + dz, "tx_bench[facing=west]");
                }
            }
            set(ISLE1 + 5, 1, z + 1, "tx_bin");
        }
        // the buses' island: benches both ways under the canopy, the queue's rope lines, the army's desks at the head
        for (int z = 12; z < 124; z += 12) {
            for (int dz = 0; dz < 3; dz++) {
                if (chance() < 0.85) {
                    set(ISLE2 + 1, 1, z + dz, "tx_bench[facing=west]");
                }
                if (chance() < 0.85) {
                    set(ISLE2 + 5, 1, z + dz, "tx_bench[facing=east]");
                }
            }
        }
        for (int z = 50; z <= 90; z++) {
            if (Math.floorMod(z, 12) > 2) {
                set(ISLE2 + 2, 1, z, "trade_rope_post[axis=z]");
                set(ISLE2 + 4, 1, z, "trade_rope_post[axis=z]");
            }
        }
        set(ISLE2 + 3, 1, 48, "sc_desk_a[facing=north]");
        set(ISLE2 + 3, 2, 48, "sc_desk_things[facing=north]");
        set(ISLE2 + 3, 1, 46, "jersey_barrier[facing=north]");
        for (int i = 0; i < 18; i++) {
            int z = 50 + rng.nextInt(41);
            int x = ISLE2 + 3;
            if (empty(x, 1, z)) {
                set(x, 1, z, i % 4 == 0 ? "trade_luggage_cart[facing=north]" : "cardboard_box[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
            }
        }
        // the buses in their bays, heading so their doors open on the island; taxis waiting at the rank
        for (int z : new int[] {28, 62, 100}) {
            p.vehicle(104, 1, z + rng.nextInt(5), 180 + (float) (chance() * 6 - 3), "city_bus", rng.nextLong());
        }
        for (int z : new int[] {22, 58, 96}) {
            p.vehicle(119, 1, z + rng.nextInt(5), (float) (chance() * 6 - 3), "city_bus", rng.nextLong());
        }
        for (int z = 16; z < 60; z += 9) {
            if (chance() < 0.75) {
                p.vehicle(90, 1, z + rng.nextInt(3), 180 + (float) (chance() * 10 - 5), "taxi", rng.nextLong());
            }
        }
        p.vehicle(90, 1, 6, 170, "police_suv", rng.nextLong());
        p.vehicle(127, 1, 70, 90 + (float) (chance() * 20 - 10), "ambulance", rng.nextLong());
        p.vehicle(127, 1, 30, (float) (chance() * 20 - 10), "jeep", rng.nextLong());
        p.vehicle(119, 1, 124, 20, "box_truck", rng.nextLong());
        // the plaza: lamp standards, planters gone to weed, bike racks; the field dressing station by the ambulance
        for (int z = 10; z < Z1; z += 20) {
            for (int y = 1; y <= 3; y++) {
                set(PLAZA + 4, y, z, "cv_lamp_post");
            }
            boolean roll = power && chance() < 0.3;  // the old dead-bulb roll, kept so everything after it lands where it did: every lamp works now
            set(PLAZA + 4, 4, z, "cv_lamp_globe_lit");
            set(PLAZA + 4, 1, z + 5, "planter");
            set(PLAZA + 4, 2, z + 5, chance() < 0.6 ? "weeds_tall" : "weeds");
            set(PLAZA + 5, 1, z + 5, "planter");
            set(PLAZA + 5, 2, z + 5, "weeds");
        }
        for (int z = 112; z <= 116; z++) {
            set(PLAZA + 2, 1, z, "sc_bike_rack[axis=x]");
        }
        for (int z = 62; z <= 78; z += 2) {
            for (int x = PLAZA + 6; x <= PLAZA + 8; x += 2) {
                double r = chance();
                if (r < 0.45) {
                    set(x, 1, z, "st_stretcher" + (chance() < 0.3 ? "_bag" : "") + "[facing=north]");
                } else if (r < 0.65) {
                    set(x, 1, z, "bunk_bodybag[facing=north]");
                }
            }
        }
        for (int x = PLAZA + 5; x <= PLAZA + 9; x++) {
            set(x, 1, 60, "sandbags[facing=north]");
            set(x, 1, 80, "sandbags[facing=south]");
        }
        // the lanes closed at the front street when the last bus left: barriers, cones, a sandbag wall
        for (int x = TAXI; x < PLAZA; x++) {
            if (lane(x)) {
                double r = chance();
                set(x, 1, Z0, r < 0.4 ? "jersey_barrier[facing=north]" : r < 0.6 ? "st_barrier[facing=north]" : r < 0.75 ? "st_cone" : "air");
            }
        }
        for (int i = 0; i < 12; i++) {
            int x = BUS1 + rng.nextInt(PLAZA - BUS1), z = 4 + rng.nextInt(124);
            if (lane(x) && empty(x, 1, z)) {
                set(x, 1, z, "st_cone");
            }
        }
    }

    /** A glass canopy on steel over an island x0 .. x1: posts down its middle, tapered arms across, the gutters over the lanes. */
    private void canopy(int x0, int x1) {
        int cx = (x0 + x1) / 2, z0 = 8, z1 = 124;
        for (int z = z0; z <= z1; z++) {
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                String b = x == x0 - 1 ? "tx_canopy_edge[facing=west]" : x == x1 + 1 ? "tx_canopy_edge[facing=east]"
                        : z == z0 ? "tx_canopy_edge[facing=north]" : z == z1 ? "tx_canopy_edge[facing=south]"
                        : chance() < 0.07 ? "air" : "tx_canopy";
                set(x, 6, z, b);
            }
            if (Math.floorMod(z - z0, 12) == 4) {
                // a post, and an arm out from it each way, tapering to the gutter
                post(cx, z, 1, 5);
                for (int i = 1; i <= cx - x0; i++) {
                    String k = Integer.toString((i - 1) * 6 / Math.max(1, cx - x0 - 1));
                    set(cx - i, 6, z, "tx_canopy_arm_" + k + "[facing=west]");
                    set(cx + i, 6, z, "tx_canopy_arm_" + k + "[facing=east]");
                }
            } else if (Math.floorMod(z - z0, 12) == 10) {
                set(cx, 5, z, pendant());
            }
        }
    }

    private String asphalt(double wear) {
        double r = chance();
        if (r > wear) {
            return "asphalt";
        }
        r /= wear;
        return r < 0.3 ? "asphalt_crack" : r < 0.55 ? "asphalt_b" : r < 0.8 ? "asphalt_patch" : "asphalt_moss";
    }

    // ------------------------------------------------------------------ what the years left

    private void wear() {
        // the platforms: rain in through the holes in the roof, puddles and weeds under them; litter, blood, the dead
        for (boolean two : new boolean[] {false, true}) {
            for (int x = side(two, HE + 1), n = 0; n < 12; n++, x += two ? -1 : 1) {
                for (int z = Z0 + 1; z < Z1; z++) {
                    if (!empty(x, PLAT + 1, z) || empty(x, PLAT, z)) {
                        continue;
                    }
                    String roof = p.get(x, ROOF, z);
                    boolean wet = roof != null && roof.startsWith("tx_vault_frame");
                    boolean end = z < 8 || z > Z1 - 8;
                    double r = chance();
                    if (wet && r < 0.7) {
                        set(x, PLAT + 1, z, r < 0.4 ? "bunk_puddle" : r < 0.55 ? "moss_carpet" : "weeds");
                    } else if (end && r < 0.12) {
                        set(x, PLAT + 1, z, r < 0.06 ? "leaf_litter" : "weeds");
                    } else if (r < 0.035) {
                        set(x, PLAT + 1, z, "paper");
                    } else if (r < 0.045) {
                        set(x, PLAT + 1, z, "blood_splat");
                    } else if (r < 0.052) {
                        set(x, PLAT + 1, z, "trash_bag[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                corpse(side(two, 53 + rng.nextInt(8)), PLAT + 1, 4 + rng.nextInt(124));
            }
        }
        // vines down from the roof's steel where the rain gets in
        for (int x = HE + 1; x < HW; x++) {
            for (int z = Z0 + 1; z < Z1; z++) {
                String roof = p.get(x, ROOF, z);
                if (roof != null && roof.startsWith("tx_vault_frame") && chance() < 0.25 && empty(x, ROOF - 1, z)) {
                    set(x, ROOF - 1, z, "vines_hanging");
                }
            }
        }
        // the concourse: shards under the broken glass, papers, blood, the dead, spray on the walls
        for (int x = HE + 1; x < HW; x++) {
            for (int z = Z0 + 1; z < Z1; z++) {
                if (!empty(x, 1, z) || empty(x, 0, z)) {
                    continue;
                }
                boolean byGlass = z == Z0 + 1 && broken(x, 3, Z0) || z == Z1 - 1 && broken(x, 3, Z1) || x == HW - 1 && broken(HW, 3, z);
                double r = chance();
                if (byGlass && r < 0.6) {
                    set(x, 1, z, "glass_shards");
                } else if (r < 0.03) {
                    set(x, 1, z, "paper");
                } else if (r < 0.04) {
                    set(x, 1, z, "blood_splat");
                } else if (r < 0.045) {
                    set(x, 1, z, "rubble_pile");
                } else if (r < 0.05) {
                    set(x, 1, z, "trash_bag[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            corpse(HE + 2 + rng.nextInt(HW - HE - 3), 1, Z0 + 2 + rng.nextInt(Z1 - Z0 - 4));
        }
        // the terminal
        for (int i = 0; i < 40; i++) {
            int x = HW + 1 + rng.nextInt(w - HW - 1), z = rng.nextInt(d);
            if (empty(x, 1, z) && !empty(x, 0, z)) {
                double r = chance();
                set(x, 1, z, r < 0.3 ? "paper" : r < 0.5 ? "blood_splat" : r < 0.65 ? "bunk_casings" : r < 0.8 ? "weeds"
                        : "trash_bag[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
            }
        }
        for (int i = 0; i < 6; i++) {
            corpse(HW + 2 + rng.nextInt(w - HW - 4), 1, 4 + rng.nextInt(d - 8));
        }
        for (int i = 0; i < 6; i++) {
            int z = 4 + rng.nextInt(Z1 - 8);
            if (Math.floorMod(z - 2, 8) != 0 && chance() < 0.7) {
                set(HW + 1, 2, z, "graffiti_" + rng.nextInt(8) + "[facing=west]");
            }
        }
    }

    private boolean broken(int x, int y, int z) {
        String b = p.get(x, y, z);
        return b != null && (b.startsWith("tx_glass_broken") || b.startsWith("tx_glass_frame"));
    }

    /** One of the dead, laid any way where there's room for them. */
    private void corpse(int x, int y, int z) {
        String f = Bldg.DIRS[rng.nextInt(4)];
        int[] s = Bldg.step(f);
        if (empty(x, y, z) && empty(x + s[0], y, z + s[1]) && !empty(x, y - 1, z) && !empty(x + s[0], y - 1, z + s[1])) {
            set(x, y, z, "home_corpse_head[facing=" + f + "]");
            set(x + s[0], y, z + s[1], "home_corpse_foot[facing=" + f + "]");
        }
    }
}
