package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven Union Station, where the county's railway and the island's subway meet: the city's
 * Beaux-Arts terminal of the old kind, drawn from what is known of Kansas City's, Chicago's and
 * Washington's Union Stations (no photos of them were in the library when it was made). Built
 * with the us_ kit (tools/city/unionstation.py) and the civic kit's stone.
 *
 * <p>Outside, a limestone front: three great arched windows between paired columns, double doors
 * under each, the name cut in the frieze over the colonnade and the clock in the attic above it,
 * a wing of offices either side. Inside, the Grand Hall under its coffered ceiling, brass
 * chandeliers on long chains, the ticket windows down one side, the information booth with its
 * clock in the middle and the departures board over the arch to the concourse, every train on it
 * cancelled. Behind the hall the long concourse under its skylights and steel, where the stair
 * climbs to the mezzanine and its door out onto platform 1 of the train hall; in the wing on the el's side
 * the stair down to the subway's hall, its turnstiles and the passage out to the subway platform
 * under the street; at the back the baggage hall with its left-luggage lockers.
 *
 * <p>When the trains stopped it was the city's evacuation point and it shows: a checkpoint of
 * sandbags across the forecourt, cots in the hall, the concourse full of shelter tents and the
 * field hospital under the mezzanine, the notices pasted on every wall.
 *
 * <p>Drawn in its lot's frame (front along z = 0 facing north, x along the front), the lot turned
 * so the front faces the street the subway runs under; the train hall ({@link UnionHall}) stands
 * against its wall at x = W - 1, the mezzanine's door at z = BRIDGE_Z0 .. BRIDGE_Z1 opening onto
 * the hall's platform 1, level with it. The subway's passage (RailGen) comes in through the lot's
 * front at x = PASS_X0 .. PASS_X1 on the subway hall's level, fixed by where the plan puts the station.
 */
final class UnionStation {
    /** The building's width: x 0 .. W - 1. */
    static final int W = 52;
    /** The front wall, the Grand Hall's back wall, the concourse's end, the back wall. */
    static final int ZF = 10, ZH = 49, ZC = 117, ZB = 130;
    /** The Grand Hall's side walls; the wings are outside them. */
    static final int HX0 = 10, HX1 = 41;
    /** Floors: the wings' upper storeys, their roof, the hall's ceiling, the concourse's, the baggage hall's roof. */
    static final int F2 = 8, F3 = 16, WING_TOP = 24, HALL_TOP = 27, CON_TOP = 18, BACK_TOP = 15;
    /** The mezzanine's floor, level with the el's platform; the subway hall's floor and ceiling. */
    static final int MEZZ = 11, SUB = -10, SUB_TOP = -4;
    /** The mezzanine's door out onto the train hall's platform 1, and the subway's passage in. */
    static final int BRIDGE_Z0 = 95, BRIDGE_Z1 = 98, PASS_X0 = 44, PASS_X1 = 47;
    /** The mezzanine, and the stair up to it. */
    private static final int MX0 = 42, MZ0 = 86, MZ1 = 106, SX0 = 44, SX1 = 48, SZ0 = 75;
    /** The subway stair's foot (it climbs toward +z to the ground floor at its head). */
    private static final int DZ0 = 32;

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final boolean clockLit, power;

    UnionStation(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.5;
        b.boarded = 0.15;
        this.clockLit = rng.nextDouble() < 0.6;
        // the army's generator still runs a few of the lights
        this.power = rng.nextDouble() < 0.7;
    }

    /** Whether the army's generator still runs (the train hall's lamps are on the same supply). */
    boolean power() {
        return power;
    }

    void build() {
        shell();
        front();
        hall();
        wing(true);
        wing(false);
        concourse();
        mezzanine();
        subway();
        baggage();
        camp();
        forecourt();
    }

    private void set(int x, int y, int z, String block) {
        p.set(x, y, z, block);
    }

    private double chance() {
        return rng.nextDouble();
    }

    /** The front's limestone: rusticated for the first storey (green at the foot here and there), smooth over it. */
    private String stone(int y) {
        if (y <= 3) {
            double r = chance();
            return y <= 1 && r < 0.25 ? "us_rustic_moss" : r < 0.35 ? "us_rustic_b" : "us_rustic";
        }
        return b.wall("stone_trim", y);
    }

    /** The concourse's and baggage hall's walls: buff brick on a stone base. */
    private String brick(int y) {
        return b.wall(y <= 2 ? "stone_trim" : "brick_buff", y);
    }

    private String on(String lit, String dead, double share) {
        return power && chance() < share ? lit : dead;
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        // the front block: the hall up to its roof, the wings lower either side
        for (int y = 0; y <= HALL_TOP + 1; y++) {
            for (int x = 0; x < W; x++) {
                boolean hall = x >= HX0 && x <= HX1;
                if (y > (hall ? HALL_TOP + 1 : WING_TOP + 1)) {
                    continue;
                }
                set(x, y, ZF, stone(y));
                // the hall's back wall: marble and plaster facing the hall (a stone skin outside it over the concourse's roof)
                set(x, y, ZH, !hall || y > HALL_TOP ? stone(y) : y <= 3 ? "us_marble_wall" : "cv_plaster");
                if (hall && y > CON_TOP + 1) {
                    set(x, y, ZH + 1, stone(y));
                }
            }
            for (int z = ZF + 1; z < ZH; z++) {
                if (y <= WING_TOP + 1) {
                    set(0, y, z, stone(y));
                    set(W - 1, y, z, stone(y));
                }
                // between the hall and the wings: marble low down, plaster over it; a stone skin outside over the wings' roofs
                String inner = y > HALL_TOP ? stone(y) : y <= 3 ? "us_marble_wall" : "cv_plaster";
                set(HX0, y, z, inner);
                set(HX1, y, z, inner);
                if (y > WING_TOP) {
                    set(HX0 - 1, y, z, stone(y));
                    set(HX1 + 1, y, z, stone(y));
                }
            }
        }
        // the wings' storeys
        for (int[] wx : new int[][] {{1, HX0 - 1}, {HX1 + 1, W - 2}}) {
            b.floor(wx[0], ZF + 1, wx[1], ZH - 1, 0, "cv_terrazzo");
            for (int[] st : new int[][] {{1, F2 - 1}, {F2 + 1, F3 - 1}, {F3 + 1, WING_TOP - 1}}) {
                b.clear(wx[0], st[0], ZF + 1, wx[1], st[1], ZH - 1);
            }
            b.floor(wx[0], ZF + 1, wx[1], ZH - 1, F2, "office_floor");
            b.floor(wx[0], ZF + 1, wx[1], ZH - 1, F3, "office_floor");
        }
        b.flat(0, ZF, HX0, ZH, WING_TOP, "stone_trim", 1, "stone_trim");
        b.flat(HX1, ZF, W - 1, ZH, WING_TOP, "stone_trim", 1, "stone_trim");
        // the hall: open to its coffered ceiling, the roof over it
        b.clear(HX0 + 1, 1, ZF + 1, HX1 - 1, HALL_TOP - 1, ZH - 1);
        b.floor(HX0 + 1, ZF + 1, HX1 - 1, ZH - 1, HALL_TOP, "us_coffer");
        b.flat(HX0, ZF, HX1, ZH, HALL_TOP + 1, "stone_trim", 1, "stone_trim");
        // the concourse
        for (int y = 0; y <= CON_TOP + 1; y++) {
            for (int z = ZH + 1; z < ZC; z++) {
                set(0, y, z, brick(y));
                set(W - 1, y, z, brick(y));
            }
        }
        b.floor(1, ZH + 1, W - 2, ZC - 1, 0, "cv_terrazzo");
        b.clear(1, 1, ZH + 1, W - 2, CON_TOP - 1, ZC - 1);
        b.flat(0, ZH, W - 1, ZC, CON_TOP + 1, "brick_buff", 1, "stone_trim");
        // the parapets' coping ran through the hall's walls: plaster again inside
        for (int x = HX0 + 1; x < HX1; x++) {
            set(x, CON_TOP + 2, ZH, "cv_plaster");
        }
        for (int z = ZF + 1; z < ZH; z++) {
            set(HX0, WING_TOP + 1, z, "cv_plaster");
            set(HX1, WING_TOP + 1, z, "cv_plaster");
        }
        for (int x = 1; x < W - 1; x++) {
            for (int z = ZH + 1; z < ZC; z++) {
                // skylight wells between the trusses down the middle (wired glass, panes gone), plaster either side
                boolean sky = x >= 14 && x <= 37 && Math.floorMod(z - ZH - 1, 8) >= 2 && Math.floorMod(z - ZH - 1, 8) <= 5;
                set(x, CON_TOP, z, sky ? "air" : "cv_plaster");
                if (sky) {
                    set(x, CON_TOP + 1, z, chance() < 0.15 ? "us_skylight_broken" : "us_skylight");
                }
            }
        }
        // the baggage hall, one tall storey
        for (int y = 0; y <= BACK_TOP + 1; y++) {
            for (int x = 0; x < W; x++) {
                set(x, y, ZB, brick(y));
                set(x, y, ZC, y <= CON_TOP + 1 ? brick(y) : stone(y));
            }
            for (int z = ZC + 1; z < ZB; z++) {
                set(0, y, z, brick(y));
                set(W - 1, y, z, brick(y));
            }
        }
        b.floor(1, ZC + 1, W - 2, ZB - 1, 0, "floor_concrete");
        b.clear(1, 1, ZC + 1, W - 2, BACK_TOP - 1, ZB - 1);
        b.flat(0, ZC, W - 1, ZB, BACK_TOP, "brick_buff", 1, "stone_trim");
        // the concourse's wall above the baggage hall's roof
        for (int y = BACK_TOP + 1; y <= CON_TOP + 1; y++) {
            for (int x = 0; x < W; x++) {
                set(x, y, ZC, brick(y));
            }
        }
        // the moulding round the outside: water table, belts between the storeys, cornices under the parapets
        for (int y : new int[] {1, F2, F3, WING_TOP}) {
            ring(0, HX0 - 1, ZF, ZH, y, y == 1 ? "cv_water_table" : y == WING_TOP ? "cv_cornice_stone" : "cv_belt", true);
            ring(HX1 + 1, W - 1, ZF, ZH, y, y == 1 ? "cv_water_table" : y == WING_TOP ? "cv_cornice_stone" : "cv_belt", false);
        }
        for (int z = ZF - 1; z <= ZB + 1; z++) {
            if (z >= ZH + 1) {
                for (int y : new int[] {1, z < ZC ? CON_TOP + 1 : BACK_TOP}) {
                    String piece = y == 1 ? "cv_water_table" : "cv_cornice_stone";
                    if (z <= ZB) {
                        set(-1, y, z, piece + "[facing=west]");
                        set(W, y, z, piece + "[facing=east]");
                    }
                }
            }
        }
        for (int x = -1; x <= W; x++) {
            set(x, 1, ZB + 1, "cv_water_table[facing=south]");
            set(x, BACK_TOP, ZB + 1, "cv_cornice_stone[facing=south]");
        }
        // the roofmen's ladders: from the wing's roof (its stair's hut) down to the concourse's, and
        // from that down to the baggage hall's, so every roof can be reached
        roofLadder(W - 6, ZH, CON_TOP + 2, WING_TOP + 1);
        roofLadder(8, ZC, BACK_TOP + 1, CON_TOP + 2);
    }

    /**
     * A steel ladder up the face of the wall at z = wall from the lower roof (standing at y0) to
     * the higher one (standing at y1), on the lower roof's side, its head flush with the wall's
     * top and a gap cut in the coping over it to step through.
     */
    private void roofLadder(int x, int wall, int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            set(x, y, wall + 1, "ladder[facing=south]");
        }
        set(x, y1, wall, "air");
        set(x, y1 + 1, wall, "air");
    }

    /** A wing's moulding at y: along its front and its outer side (lo: the side at x = 0). */
    private void ring(int x0, int x1, int z0, int z1, int y, String piece, boolean lo) {
        for (int x = x0 - (lo ? 1 : 0); x <= x1 + (lo ? 0 : 1); x++) {
            set(x, y, z0 - 1, piece + "[facing=north]");
        }
        for (int z = z0; z <= z1; z++) {
            if (lo) {
                set(x0 - 1, y, z, piece + "[facing=west]");
            } else {
                set(x1 + 1, y, z, piece + "[facing=east]");
            }
        }
    }

    // ------------------------------------------------------------------ the front

    private void front() {
        // the colonnade's floor, a step of stone flags in front of the doors
        for (int x = HX0 - 2; x <= HX1 + 2; x++) {
            for (int z = 6; z < ZF; z++) {
                set(x, 0, z, chance() < 0.12 ? "stone_trim_crack" : "pt_stone_flags");
            }
        }
        // the columns in pairs, between the three great windows
        for (int c : new int[] {HX0, 20, 30, HX1 - 1}) {
            for (int x = c; x <= c + 1; x++) {
                set(x, 1, 7, "cv_column_base_stone");
                for (int y = 2; y <= 17; y++) {
                    set(x, y, 7, "cv_column_stone");
                }
                set(x, 18, 7, "cv_column_cap_stone[facing=north]");
            }
        }
        // the entablature: UNION STATION in the frieze (its first letter at the viewer's left, the
        // high x), the colonnade's ceiling behind, the cornice over it returned down the ends
        for (int x = HX0 - 1; x <= HX1 + 1; x++) {
            set(x, 19, 7, x >= 19 && x <= 32 ? "us_frieze_14_" + (32 - x) + "[facing=north]" : "stone_trim");
            set(x, 20, 7, "stone_trim");
            for (int z = 8; z < ZF; z++) {
                set(x, 19, z, "stone_trim");
                set(x, 20, z, "stone_trim");
            }
        }
        for (int x = HX0 - 2; x <= HX1 + 2; x++) {
            set(x, 20, 6, "cv_cornice_stone[facing=north]");
        }
        for (int z = 7; z < ZF; z++) {
            set(HX0 - 2, 20, z, "cv_cornice_stone[facing=west]");
            set(HX1 + 2, 20, z, "cv_cornice_stone[facing=east]");
        }
        // the attic over it: pilasters, the clock, a cornice and a balustrade, piers at the ends
        for (int x = 13; x <= 38; x++) {
            for (int y = 21; y <= 27; y++) {
                set(x, y, 9, b.wall("stone_trim", y));
            }
            set(x, 28, 9, "stone_trim");
            set(x, 28, ZF, "stone_trim");
            set(x, 29, 9, x == 13 || x == 38 ? "cv_pier_stone" : "cv_balustrade_stone[axis=x]");
        }
        for (int x = 12; x <= 39; x++) {
            set(x, 28, 8, "cv_cornice_stone[facing=north]");
        }
        set(12, 28, 9, "cv_cornice_stone[facing=west]");
        set(39, 28, 9, "cv_cornice_stone[facing=east]");
        for (int x : new int[] {13, 18, 33, 38}) {
            for (int y = 21; y <= 27; y++) {
                set(x, y, 8, "cv_pier_stone");
            }
        }
        // the clock, four by four, tile 0 at the top left as you face it; lamps behind its middle
        String dial = clockLit ? "cv_dial_lit_" : "cv_dial_";
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                set(27 - i, 25 - j, 8, dial + (j * 4 + i) + "[facing=north]");
            }
        }
        if (clockLit) {
            for (int x = 25; x <= 26; x++) {
                for (int y = 23; y <= 24; y++) {
                    set(x, y, 9, "minecraft:ochre_froglight");
                }
            }
        }
        // the three great windows: bronze glazing in a round-headed opening, the doors in its foot
        for (int a : new int[] {12, 22, 32}) {
            for (int k = 0; k < 8; k++) {
                int x = a + k;
                int head = k == 0 || k == 7 ? 15 : k == 1 || k == 6 ? 16 : k == 2 || k == 5 ? 17 : 18;
                for (int y = 1; y <= head; y++) {
                    boolean doors = k == 3 || k == 4;
                    if (doors && y <= 2) {
                        continue;
                    }
                    set(x, y, ZF, y <= 3 && !doors ? stone(y) : pane(y, "x"));
                }
            }
            Doors.pair(p, rng, a + 3, 1, ZF, "ext_door_oak", "south");
        }
        // the wings' fronts: tall windows on every storey (the ground floor's boarded more often), the
        // subway's own door in the wing on the el's side with its globe lamps
        for (int[] row : new int[][] {{2, 4}, {F2 + 2, 4}, {F3 + 2, 4}}) {
            int y = row[0];
            for (int x : new int[] {2, 3, 6, 7, 43, 44, 48, 49}) {
                if (y == 2 && x >= 43 && x <= 49 && (x == 44 || x == 48)) {
                    continue;
                }
                b.boarded = y == 2 ? 0.45 : 0.15;
                b.window(x, y, ZF, "north", row[1], y == 2 ? null : "sill");
            }
        }
        b.boarded = 0.15;
        Doors.pair(p, rng, 45, 1, ZF, "ext_door_oak", "south");
        set(45, 3, ZF, pane(3, "x"));
        set(46, 3, ZF, pane(3, "x"));
        for (int x : new int[] {44, 47}) {
            set(x, 1, ZF - 1, "rw_globe_post");
            set(x, 2, ZF - 1, chance() < 0.3 ? "rw_globe" : "rw_globe_post");
        }
        set(44, 2, ZF - 1, "rw_globe");
        set(47, 2, ZF - 1, "rw_globe");
        // the wings' outer sides: windows down them on every storey
        for (int z = ZF + 3; z < ZH - 1; z += 4) {
            for (int y : new int[] {2, F2 + 2, F3 + 2}) {
                b.window(0, y, z, "west", 4, y == 2 ? null : "sill");
                b.window(W - 1, y, z, "east", 4, y == 2 ? null : "sill");
            }
        }
        // windows high in the hall's sides, over the wings' roofs: the glass in the skin, a deep reveal inside
        for (int z = ZF + 4; z < ZH - 2; z += 6) {
            for (int x : new int[] {HX0, HX1}) {
                for (int y = WING_TOP + 1; y <= WING_TOP + 2; y++) {
                    for (int zz = z; zz <= z + 1; zz++) {
                        set(x, y, zz, "air");
                        set(x == HX0 ? x - 1 : x + 1, y, zz, pane(y, "z"));
                    }
                }
            }
        }
    }

    /** One of the station's bronze-barred panes: whole, broken, or boarded low down. */
    private String pane(int y, String axis) {
        double r = chance();
        if (y <= 6 && r < 0.3) {
            return "boarded_window[axis=" + axis + "]";
        }
        return (chance() < 0.35 ? "us_window_broken" : "us_window") + "[axis=" + axis + "]";
    }

    // ------------------------------------------------------------------ the Grand Hall

    private void hall() {
        int x0 = HX0 + 1, x1 = HX1 - 1, z0 = ZF + 1, z1 = ZH - 1;
        // the floor: two marbles in squares inside a border, worn and cracked where the crowds stood
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, border || ((x - x0) / 2 + (z - z0) / 2) % 2 == 0 ? "us_marble_b" : "us_marble_a");
            }
        }
        // pilasters down the sides, between the bays of ticket windows
        for (int z = 13; z <= 43; z += 6) {
            for (int y = 1; y <= 24; y++) {
                set(x0, y, z, "cv_pier_stone");
                set(x1, y, z, "cv_pier_stone");
            }
            set(x0, 25, z, "cv_column_cap_stone[facing=east]");
            set(x1, 25, z, "cv_column_cap_stone[facing=west]");
        }
        // the ticket windows in the wall to the ticket office, two to a bay, half shuttered
        for (int z = 15; z <= 35; z += 6) {
            for (int zz = z; zz <= z + 2; zz += 2) {
                double r = chance();
                set(HX0, 2, zz, (r < 0.3 ? "st_slats" : r < 0.45 ? "st_ticket_broken" : "st_ticket_" + (1 + rng.nextInt(4))) + "[facing=east]");
                set(HX0 + 1, 1, zz, "st_ticket_ledge[facing=east]");
                set(HX0 + 1, 3, zz, "cv_grille[facing=east]");
            }
        }
        hungSign("tickets", 13, 7, 24, "east", HALL_TOP - 1);
        // the arches through to the concourse, and the departures board over the middle one
        for (int a : new int[] {13, 23, 33}) {
            for (int k = 0; k < 6; k++) {
                int top = k == 0 || k == 5 ? 7 : k == 1 || k == 4 ? 8 : 9;
                b.clear(a + k, 1, ZH, a + k, top, ZH);
            }
            set(a + 2, 10, ZH - 1, "cv_keystone[facing=north]");
            set(a + 3, 10, ZH - 1, "cv_keystone[facing=north]");
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 8; col++) {
                set(29 - col, 14 - row, ZH - 1, "us_board_" + (row * 8 + col) + "[facing=north]");
            }
        }
        set(25, 17, ZH - 1, "bk_wall_clock[facing=north]");
        // the information booth in the middle under its clock
        int bx = 23, bz = 27;
        for (int x = bx; x <= bx + 5; x++) {
            for (int z = bz; z <= bz + 5; z++) {
                boolean edge = x == bx || x == bx + 5 || z == bz || z == bz + 5;
                boolean gap = z == bz + 5 && (x == bx + 2 || x == bx + 3);
                if (edge && !gap) {
                    set(x, 1, z, "reception_desk");
                    if (chance() < 0.3) {
                        set(x, 2, z, chance() < 0.5 ? "paper" : "sc_desk_things[facing=north]");
                    }
                }
            }
        }
        for (int y = 1; y <= 3; y++) {
            set(bx + 2, y, bz + 2, "cv_lamp_post");
            set(bx + 3, y, bz + 3, "cv_lamp_post");
        }
        set(bx + 2, 4, bz + 2, (clockLit ? "bk_post_clock_lit" : "bk_post_clock") + "[facing=north]");
        set(bx + 3, 4, bz + 3, (clockLit ? "bk_post_clock_lit" : "bk_post_clock") + "[facing=south]");
        set(bx + 1, 1, bz + 3, "office_chair[facing=west]");
        set(bx + 4, 1, bz + 2, "office_chair_fallen[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
        // the chandeliers on their long chains, a few still burning on the army's generator
        for (int[] c : new int[][] {{17, 19}, {34, 19}, {17, 39}, {34, 39}, {25, 30}}) {
            for (int y = 20; y < HALL_TOP; y++) {
                set(c[0], y, c[1], "cv_chain");
            }
            set(c[0], 19, c[1], on("tr_chandelier_on", "tr_chandelier", 0.4));
        }
        // the waiting benches back to back in the hall's far half, some dragged off for barricades
        for (int z = 38; z <= 46; z += 4) {
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                if (x >= 22 && x <= 29 || chance() < 0.12) {
                    continue;
                }
                set(x, 1, z, "cv_hall_bench[facing=north]");
                set(x, 1, z + 1, "cv_hall_bench[facing=south]");
            }
        }
        // notices pasted on the walls at eye height
        for (int z = 14; z <= 46; z += 3) {
            if (chance() < 0.4) {
                set(x1, 2, z, "us_notice_" + rng.nextInt(3) + "[facing=west]");
            }
            if (chance() < 0.25 && (z - 15) % 6 != 0 && (z - 17) % 6 != 0) {
                set(x0, 3, z, "us_notice_" + rng.nextInt(3) + "[facing=east]");
            }
        }
        // through to the subway's wing and to the lunch room on the other side
        b.clear(HX1, 1, 43, HX1, 4, 46);
        b.clear(HX0, 1, 42, HX0, 3, 43);
        hungSign("subway", HX1 - 2, 6, 44, "west", HALL_TOP - 1);
    }

    /**
     * One of the station's enamel signs, two blocks long, hung from a rod up to `top` and facing
     * that way: its first half at the reader's left.
     */
    private void hungSign(String key, int x, int y, int z, String facing, int top) {
        boolean alongX = facing.equals("north") || facing.equals("south");
        // a reader facing a sign that faces north or east has the higher x or z on their left
        boolean flip = facing.equals("north") || facing.equals("east");
        for (int t = 0; t < 2; t++) {
            int at = flip ? 1 - t : t;
            int sx = alongX ? x + at : x, sz = alongX ? z : z + at;
            set(sx, y, sz, "us_sign_" + key + "_" + t + "[facing=" + facing + "]");
            for (int yy = y + 1; yy <= top; yy++) {
                set(sx, yy, sz, "cv_chain");
            }
        }
    }

    // ------------------------------------------------------------------ the wings

    /**
     * A wing of offices: lo the one away from the el (the ticket office and the lunch room on the
     * ground floor), else the one on the el's side (the subway's hall). Offices upstairs, the stair
     * up through them to a hut on the roof.
     */
    private void wing(boolean lo) {
        int x0 = lo ? 1 : HX1 + 1, x1 = lo ? HX0 - 1 : W - 2, z0 = ZF + 1, z1 = ZH - 1;
        int sx = lo ? x0 : x1 - 1;    // the stair, two wide against the outer wall
        if (lo) {
            // the ticket office: the clerks' counter behind the windows, desks, the files, the safe's room
            for (int z = 14; z <= 37; z++) {
                set(HX0 - 1, 1, z, "store_counter[facing=west,loot=till]");
            }
            for (int z = 15; z <= 35; z += 2) {
                if (chance() < 0.7) {
                    set(HX0 - 2, 1, z, (chance() < 0.3 ? "office_chair_fallen" : "office_chair") + "[facing=east]");
                }
            }
            for (int z = 16; z <= 34; z += 6) {
                set(4, 1, z, "desk[facing=east]");
                set(4, 1, z + 1, "desk[facing=east]");
                set(5, 1, z, "office_chair[facing=west]");
                if (chance() < 0.6) {
                    set(4, 2, z, chance() < 0.5 ? "sh_typewriter[facing=east]" : "paper");
                }
            }
            for (int z = 22; z <= 36; z += 2) {
                set(x0 + 2, 1, z, "filing_cabinet[facing=east]");
            }
            // the partition to the lunch room, a door through it
            for (int x = x0; x <= x1; x++) {
                for (int y = 1; y < F2; y++) {
                    set(x, y, 38, x == 5 && y <= 2 ? "air" : "cv_plaster");
                }
            }
            Doors.abandoned(p, rng, 5, 1, 38, "ext_door_oak", "north", Doors.hinge(rng));
            // the lunch room: the counter and its stools, the shelf of mugs behind it, the till
            for (int x = 3; x <= 8; x++) {
                set(x, 1, 45, "dn_counter_q" + (x == 8 ? "_end" : "") + "[facing=north]");
                set(x, 1, 44, chance() < 0.25 ? "dn_stool_stub" : "dn_stool_r");
                set(x, 2, ZH - 1, "dn_mugshelf[facing=north]");
            }
            set(3, 2, 45, "dn_ct_register[facing=north]");
            set(1, 1, 47, "fit_dc_locker[facing=east]");
            set(1, 1, 40, "pk_vending[facing=east]");
        } else {
            // the subway's hall: the stair down in its middle, railed round, the way in from the street
            subwayHead();
            b.clear(x0, 1, ZH, x1 - 1, 5, ZH);
            for (int z = 22; z <= 34; z += 4) {
                set(x1, 1, z, "gs_payphone[facing=west]");
            }
            set(x0, 1, 20, "pk_vending[facing=east]");
            set(x0, 1, 21, "pk_vending[facing=east]");
        }
        // three straight flights up the outer wall to the roof, a landing between each
        int[] flights = {ZF + 2, ZF + 11, ZF + 20};
        int[] bases = {1, F2 + 1, F3 + 1};
        for (int f = 0; f < 3; f++) {
            int rise = f == 0 ? F2 : f == 1 ? F3 - F2 : WING_TOP - F3;
            for (int i = 0; i < rise; i++) {
                for (int x = sx; x <= sx + 1; x++) {
                    int z = flights[f] + i, y = bases[f] + i;
                    set(x, y, z, "stairs_concrete[facing=south]");
                    for (int yy = y + 1; yy <= y + 4; yy++) {
                        set(x, yy, z, "air");
                    }
                }
            }
            // a stone stringer along the flight's open side with a balustrade on it
            int rx = lo ? sx + 2 : sx - 1;
            for (int i = 0; i < rise; i++) {
                int y = bases[f] + i, z = flights[f] + i;
                for (int yy = bases[f]; yy <= y; yy++) {
                    set(rx, yy, z, "stone_trim");
                }
                set(rx, y + 1, z, "cv_balustrade_stone[axis=z]");
            }
        }
        // the hut over the last flight's head, its doors out onto the roof
        RoofHut.build(p, rng, sx - 1, flights[2] + 3, sx + 2, flights[2] + 9, WING_TOP, "stone_trim", sx, "south");
        // the offices upstairs: desks, files and the mess the last of the staff left
        for (int y : new int[] {F2 + 1, F3 + 1}) {
            for (int z = z0 + 2; z <= z1 - 2; z += 5) {
                int dx = lo ? x1 - 3 : x0 + 2;
                if (z >= flights[0] - 1 && z <= flights[2] + 10 && Math.abs(dx - sx) < 3) {
                    continue;
                }
                set(dx, y, z, "desk[facing=" + (lo ? "west" : "east") + "]");
                set(dx + (lo ? -1 : 1), y, z, "office_chair[facing=" + (lo ? "east" : "west") + "]");
                set(lo ? x1 : x0, y, z + 2, "filing_cabinet[facing=" + (lo ? "west" : "east") + "]");
                if (chance() < 0.5) {
                    set(dx, y + 1, z, chance() < 0.5 ? "paper" : "sh_typewriter[facing=" + (lo ? "west" : "east") + "]");
                }
            }
            b.lights(x0, z0, x1, z1, y + 6, power ? 0.15 : 0);
            b.mess(x0, z0, x1, z1, y, 0.05);
        }
        b.lights(x0, z0, x1, z1, F2 - 1, power ? 0.2 : 0);
        b.mess(x0, z0, x1, z1, 1, 0.04);
    }

    // ------------------------------------------------------------------ the concourse

    private void concourse() {
        int z0 = ZH + 1, z1 = ZC - 1;
        // the steel: columns in two rows, a truss across under the roof over each pair
        for (int z = z0 + 4; z < z1; z += 8) {
            for (int x : new int[] {13, 38}) {
                set(x, 1, z, "rw_column_base");
                for (int y = 2; y < CON_TOP - 1; y++) {
                    set(x, y, z, "rw_column");
                }
                set(x, CON_TOP - 1, z, "rw_column_cap");
            }
            for (int x = 1; x < W - 1; x++) {
                if (x != 13 && x != 38) {
                    set(x, CON_TOP - 1, z, "rw_girder");
                }
            }
        }
        // lamps on chains between the trusses, a few still lit
        for (int z = z0 + 8; z < z1; z += 8) {
            for (int x : new int[] {7, 25, 44}) {
                set(x, CON_TOP - 1, z, "cv_chain");
                set(x, CON_TOP - 2, z, "cv_chain");
                set(x, CON_TOP - 3, z, on("cv_lamp_globe_lit", "cv_lamp_globe", 0.25));
            }
        }
        // tall windows down both sides, a high row over them; none where the bridge comes in
        for (int z = z0 + 2; z < z1 - 1; z += 4) {
            b.window(0, 3, z, "west", 6, null);
            b.window(0, 3, z + 1, "west", 6, null);
            b.window(0, 12, z, "west", 4, null);
            if (z + 1 < MZ0 - 1 || z > MZ1 + 1) {
                b.window(W - 1, 3, z, "east", 6, null);
                b.window(W - 1, 3, z + 1, "east", 6, null);
            }
            if (z + 1 < BRIDGE_Z0 - 1 || z > BRIDGE_Z1 + 1) {
                b.window(W - 1, 12, z, "east", 4, null);
            }
        }
        // payphones and machines along the wall, the newsstand by the hall's arches
        for (int z = z0 + 4; z <= z0 + 10; z += 2) {
            set(1, 1, z, "gs_payphone[facing=east]");
        }
        set(1, 1, z0 + 13, "pk_vending[facing=east]");
        set(1, 1, z0 + 14, "pk_vending[facing=east]");
        for (int x = 16; x <= 20; x++) {
            set(x, 1, z0 + 3, "store_counter[facing=north,loot=theatre_candy]");
        }
        for (int x = 16; x <= 20; x++) {
            set(x, 1, z0 + 5, "store_shelf_thrift[facing=north]");
        }
        set(15, 1, z0 + 3, "store_counter[facing=west,loot=theatre_candy]");
        set(15, 1, z0 + 4, "store_counter[facing=west,loot=theatre_candy]");
        // the signs: back to the trains, to baggage, out to the street
        hungSign("trains", 45, 6, SZ0 - 2, "north", CON_TOP - 1);
        hungSign("baggage", 25, 7, ZC - 2, "north", CON_TOP - 1);
        hungSign("exit", 25, 11, z0 + 1, "south", CON_TOP - 1);
        // the arch through to the baggage hall
        b.clear(22, 1, ZC, 29, 5, ZC);
    }

    // ------------------------------------------------------------------ the mezzanine and the bridge

    private void mezzanine() {
        // the grand stair up from the concourse floor: stone treads on a solid stringer each side
        for (int k = 0; k <= MEZZ - 1; k++) {
            int z = SZ0 + k, y = 1 + k;
            for (int x = SX0 - 1; x <= SX1 + 1; x++) {
                for (int yy = 1; yy < y; yy++) {
                    set(x, yy, z, "stone_trim");
                }
                boolean side = x < SX0 || x > SX1;
                set(x, y, z, side ? "stone_trim" : "cv_step_stone[facing=south]");
                for (int yy = y + 1; yy <= y + 4 && yy < CON_TOP - 1; yy++) {
                    set(x, yy, z, side && yy == y + 1 ? "cv_balustrade_stone[axis=z]" : "air");
                }
            }
        }
        // the gallery: its floor on steel columns, a balustrade round its open sides
        for (int x = MX0; x <= W - 2; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                set(x, MEZZ, z, x == MX0 || z == MZ0 || z == MZ1 ? "us_marble_b" : "us_marble_a");
                set(x, MEZZ - 1, z, "stone_trim");
            }
        }
        for (int z = MZ0; z <= MZ1; z++) {
            set(MX0, MEZZ + 1, z, "cv_balustrade_stone[axis=z]");
        }
        for (int x = MX0; x <= W - 2; x++) {
            set(x, MEZZ + 1, MZ1, "cv_balustrade_stone[axis=x]");
            if (x < SX0 || x > SX1) {
                set(x, MEZZ + 1, MZ0, "cv_balustrade_stone[axis=x]");
            }
        }
        for (int z : new int[] {MZ0, MZ0 + 7, MZ0 + 13, MZ1}) {
            set(MX0, 1, z, "rw_column_base");
            for (int y = 2; y < MEZZ - 1; y++) {
                set(MX0, y, z, "rw_column");
            }
        }
        // the door out to the train hall's platform, the sign over it, the rope lines that kept the queue for the trains
        b.clear(W - 1, MEZZ + 1, BRIDGE_Z0, W - 1, MEZZ + 4, BRIDGE_Z1);
        for (int z = BRIDGE_Z0 - 1; z <= BRIDGE_Z1 + 1; z += BRIDGE_Z1 - BRIDGE_Z0 + 2) {
            for (int y = MEZZ + 1; y <= MEZZ + 4; y++) {
                set(W - 2, y, z, "cv_pier_stone");
            }
        }
        hungSign("trains", W - 2, MEZZ + 5, BRIDGE_Z0 + 1, "west", CON_TOP - 1);
        // (open at the gallery's rail end, the way into the queue)
        for (int x = W - 8; x <= W - 3; x++) {
            set(x, MEZZ + 1, BRIDGE_Z0 - 2, x == W - 8 ? "trade_rope_post_end[facing=east]" : "trade_rope_post[axis=x]");
            set(x, MEZZ + 1, BRIDGE_Z1 + 2, x == W - 8 ? "trade_rope_post_end[facing=east]" : "trade_rope_post[axis=x]");
        }
        // the waiting benches along the gallery
        for (int z : new int[] {MZ0 + 2, MZ0 + 4, MZ1 - 4, MZ1 - 2}) {
            for (int x = MX0 + 2; x <= MX0 + 6; x++) {
                if (chance() < 0.85) {
                    set(x, MEZZ + 1, z, "cv_hall_bench[facing=north]");
                }
            }
        }
        hungSign("waiting", 46, MEZZ + 5, MZ1 - 2, "north", CON_TOP - 1);
        b.mess(MX0 + 1, MZ0 + 1, W - 2, MZ1 - 1, MEZZ + 1, 0.05);
    }

    // ------------------------------------------------------------------ the subway

    /** The head of the stair down to the subway, in the wing on the el's side: the opening railed round. */
    private void subwayHead() {
        int top = DZ0 + 9;   // the top tread, in the ground floor's plane
        for (int z = DZ0 + 5; z < top; z++) {
            set(PASS_X0 - 1, 1, z, "cv_balustrade_stone[axis=z]");
            set(PASS_X1 + 1, 1, z, "cv_balustrade_stone[axis=z]");
        }
        for (int x = PASS_X0; x <= PASS_X1; x++) {
            set(x, 1, DZ0 + 4, "cv_balustrade_stone[axis=x]");
        }
        hungSign("subway", 45, 6, top + 1, "south", F2 - 1);
    }

    private void subway() {
        int fx0 = 36, fx1 = 49, fz0 = 8, fz1 = 30;
        // the subway's hall under the wing: white tile, a hexagon floor with its dark border, the concrete ceiling
        for (int x = fx0 - 1; x <= fx1 + 1; x++) {
            for (int z = fz0 - 1; z <= fz1 + 1; z++) {
                boolean wall = x < fx0 || x > fx1 || z < fz0 || z > fz1;
                boolean way = x >= PASS_X0 && x <= PASS_X1;   // the passage in at the front, the stair at the back
                boolean rim = x == fx0 || x == fx1 || z == fz0 || z == fz1;
                set(x, SUB, z, wall && !way ? "rw_tunnel" : rim && !wall ? "cv_hex_border" : "cv_hex_tile");
                for (int y = SUB + 1; y < SUB_TOP; y++) {
                    set(x, y, z, wall && !way ? tile(y) : "air");
                }
                set(x, SUB_TOP, z, "rw_tunnel");
            }
        }
        // the passage out under the forecourt to the lot's edge (RailGen lays the rest to the platform)
        for (int z = 0; z < fz0 - 1; z++) {
            for (int x = PASS_X0 - 1; x <= PASS_X1 + 1; x++) {
                boolean wall = x < PASS_X0 || x > PASS_X1;
                set(x, SUB, z, wall ? "rw_tunnel" : "cv_hex_tile");
                for (int y = SUB + 1; y < SUB_TOP; y++) {
                    set(x, y, z, wall ? tile(y) : "air");
                }
                set(x, SUB_TOP, z, "rw_tunnel");
            }
        }
        // the stair up to the wing: ten steps, tile walls each side, headroom cut through the slab
        for (int k = 0; k <= 9; k++) {
            int z = DZ0 + k, y = SUB + 1 + k;
            for (int x = PASS_X0 - 1; x <= PASS_X1 + 1; x++) {
                boolean wall = x < PASS_X0 || x > PASS_X1;
                for (int yy = SUB; yy < y; yy++) {
                    set(x, yy, z, wall ? tile(yy) : "rw_tunnel");
                }
                if (wall) {
                    for (int yy = y; yy <= Math.min(y + 4, -1); yy++) {
                        set(x, yy, z, tile(yy));
                    }
                    continue;
                }
                set(x, y, z, "stairs_concrete[facing=south]");
                for (int yy = y + 1; yy <= y + 4; yy++) {
                    set(x, yy, z, "air");
                }
            }
        }
        // the turnstiles across the hall, every other one gone; the token booth beside them
        for (int x = fx0; x <= fx1; x++) {
            if (x >= PASS_X0 && x <= PASS_X1) {
                continue;
            }
            if (x % 2 == 0) {
                set(x, SUB + 1, 14, "st_turnstile[facing=north]");
            }
        }
        for (int x = fx0; x <= fx0 + 3; x++) {
            for (int z = 16; z <= 19; z++) {
                boolean edge = x == fx0 + 3 || z == 16 || z == 19;
                for (int y = SUB + 1; y < SUB_TOP; y++) {
                    if (edge) {
                        set(x, y, z, y == SUB + 2 && x == fx0 + 3 && (z == 17 || z == 18) ? "st_ticket_broken[facing=east]" : tile(y));
                    }
                }
            }
        }
        set(fx0 + 1, SUB + 1, 17, "office_chair_fallen[facing=east]");
        // the steel columns down the hall each side of the way through, riveted, bases rusted
        for (int z : new int[] {10, 21, 28}) {
            for (int x : new int[] {PASS_X0 - 4, PASS_X0 - 1, PASS_X1 + 1}) {
                set(x, SUB + 1, z, "rw_column_base");
                for (int y = SUB + 2; y < SUB_TOP - 1; y++) {
                    set(x, y, z, "rw_column");
                }
                set(x, SUB_TOP - 1, z, "rw_column_cap");
            }
        }
        // the station's name set in the tile both sides, the timetable by the stair
        for (int k = 0; k < 4; k++) {
            set(fx0 - 1, SUB + 3, 27 - k, "rw_mosaic_union_" + k + "[facing=east]");
            set(fx1 + 1, SUB + 3, 20 + k, "rw_mosaic_union_" + k + "[facing=west]");
        }
        set(fx1 + 1, SUB + 2, 28, "rw_timetable[facing=west]");
        set(fx0 - 1, SUB + 2, 22, "rw_timetable[facing=east]");
        for (int z : new int[] {9, 17, 25}) {
            set(fx1, SUB + 1, z, "rw_bench[facing=west]");
        }
        // lamps down the ceiling, the signs out
        for (int z = fz0 + 2; z <= fz1; z += 4) {
            for (int x : new int[] {39, 45}) {
                set(x, SUB_TOP - 1, z, "rw_subway_lamp[axis=z]");
            }
        }
        hungSign("subway", 45, SUB_TOP - 2, fz0 + 1, "south", SUB_TOP - 1);
        hungSign("exit", 45, SUB_TOP - 2, fz1 - 1, "north", SUB_TOP - 1);
        // the last stand down here: sandbags across the passage, casings, the dead
        for (int x = PASS_X0; x <= PASS_X1; x++) {
            if (x != PASS_X0 + 1) {
                set(x, SUB + 1, fz0 + 3, "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 14; i++) {
            int x = fx0 + rng.nextInt(fx1 - fx0 + 1), z = fz0 + rng.nextInt(fz1 - fz0 + 1);
            p.setIfAir(x, SUB + 1, z, chance() < 0.4 ? "bunk_casings" : chance() < 0.5 ? "blood_splat" : "paper");
        }
        set(fx1 - 2, SUB + 1, 24, "home_corpse_head[facing=north]");
        set(fx1 - 2, SUB + 1, 25, "home_corpse_foot[facing=north]");
        for (int z = fz0; z <= fz1; z += 3) {
            if (chance() < 0.4) {
                p.setIfAir(fx1, SUB + 2, z, "graffiti_" + rng.nextInt(8) + "[facing=east]");
            }
        }
    }

    /** The subway's white tile: the darker dado low down, the band at the platform's height, a block in six with tiles gone. */
    private String tile(int y) {
        if (y == SUB + 3) {
            return "rw_tile_band";
        }
        String t = y <= SUB + 2 ? "rw_tile_dado" : "rw_tile";
        return chance() < 0.17 ? t + "_broken" : t;
    }

    // ------------------------------------------------------------------ the baggage hall

    private void baggage() {
        int z0 = ZC + 1, z1 = ZB - 1;
        // the counter across the hall, the left-luggage lockers along the back, carts and cases everywhere
        for (int x = 4; x < W - 4; x++) {
            if (x < 22 || x > 29) {
                set(x, 1, z0 + 3, "store_counter[facing=north,loot=clothes]");
            }
        }
        for (int x = 2; x < W - 2; x++) {
            if (x >= 23 && x <= 28) {
                continue;
            }
            String k = rng.nextDouble() < 0.4 ? "open_" : "";
            String c = "abc".charAt(rng.nextInt(3)) + "";
            set(x, 1, z1, "sc_locker_" + k + c + "[facing=north,loot=clothes]");
            set(x, 2, z1, "sc_locker_top_" + k + c + "[facing=north,loot=clothes]");
        }
        for (int i = 0; i < 16; i++) {
            int x = 2 + rng.nextInt(W - 4), z = z0 + 5 + rng.nextInt(z1 - z0 - 6);
            if (x < 22 || x > 29) {
                p.setIfAir(x, 1, z, i % 3 == 0 ? "trade_luggage_cart[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]"
                        : "cardboard_box[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
            }
        }
        // the doors out the back to the street behind
        Doors.pair(p, rng, 24, 1, ZB, Doors.STEEL, "north");
        Doors.pair(p, rng, 26, 1, ZB, Doors.STEEL, "north");
        for (int x = 4; x < W - 4; x += 6) {
            b.window(x, 4, ZB, "south", 5, null);
        }
        b.lights(1, z0, W - 2, z1, BACK_TOP - 1, power ? 0.1 : 0);
        b.mess(1, z0, W - 2, z1, 1, 0.05);
    }

    // ------------------------------------------------------------------ the evacuation

    private void camp() {
        // the hall: cots in rows for the ones waiting for a train out, the army's supplies under guard
        for (int z = ZF + 4; z <= ZF + 22; z += 3) {
            for (int x = HX0 + 4; x <= 21; x += 2) {
                if (chance() < 0.8) {
                    set(x, 1, z, (chance() < 0.5 ? "sc_cot_head_b" : "sc_cot_head") + "[facing=north]");
                    set(x, 1, z + 1, "sc_cot_foot[facing=north]");
                }
            }
        }
        for (int x = 30; x <= 38; x++) {
            for (int z = ZF + 4; z <= ZF + 14; z++) {
                boolean ring = x == 30 || x == 38 || z == ZF + 4 || z == ZF + 14;
                if (ring) {
                    if (!(z == ZF + 14 && (x == 33 || x == 34))) {
                        set(x, 1, z, "sandbags[facing=" + (x == 30 ? "west" : x == 38 ? "east" : z == ZF + 4 ? "north" : "south") + "]");
                    }
                } else if ((x + z) % 3 != 0 && chance() < 0.7) {
                    double r = chance();
                    set(x, 1, z, (r < 0.3 ? "st_pallet_water" : r < 0.5 ? "bunk_crate_ration" : r < 0.7 ? "pk_military_crate"
                            : r < 0.85 ? "st_pallet_cots" : "cellar_shelf_medical") + "[facing=north]");
                }
            }
        }
        // the concourse: the tent lines, the field hospital under the mezzanine, the check-in by the arches
        int[][] tents = {
                {2, 56, 0}, {2, 68, 0}, {2, 80, 0}, {2, 92, 0}, {2, 104, 0},
                {15, 56, 0}, {15, 68, 0}, {15, 80, 0}, {15, 92, 2}, {15, 104, 0},
                {31, 56, 1}, {31, 68, 1}, {31, 80, 0}, {31, 92, 0}, {31, 104, 3}};
        String[] labels = {"shelter", "triage", "supply"};
        for (int[] t : tents) {
            tent(t[0], t[1], 10, t[2] == 3 ? "shelter" : labels[t[2]], t[2] == 3 || chance() < 0.12);
        }
        for (int z = MZ0 + 1; z < MZ1; z += 2) {
            for (int x : new int[] {44, 47, 50}) {
                if (x == W - 2 && z >= BRIDGE_Z0 - 1 && z <= BRIDGE_Z1 + 1) {
                    continue;
                }
                double r = chance();
                if (r < 0.45) {
                    set(x, 1, z, "st_stretcher" + (chance() < 0.3 ? "_bag" : "") + "[facing=north]");
                    if (chance() < 0.4) {
                        set(x + (x == 50 ? -1 : 1), 1, z, "bunk_iv_stand");
                    }
                } else if (r < 0.6) {
                    set(x, 1, z, "bunk_bodybag[facing=north]");
                }
            }
        }
        for (int z = MZ0 + 2; z < MZ1; z += 5) {
            set(W - 2, 1, z, "cellar_shelf_medical[facing=west]");
        }
        for (int z = ZH + 2; z <= ZH + 5; z++) {
            set(22, 1, z, "trade_rope_post[axis=z]");
            set(29, 1, z, "trade_rope_post[axis=z]");
        }
        for (int x : new int[] {23, 27}) {
            set(x, 1, ZH + 7, "sc_desk_a[facing=north]");
            set(x, 2, ZH + 7, "sc_desk_things[facing=north]");
        }
        for (int z = ZH + 1; z < ZC; z += 7) {
            set(W - 2, 1, z, chance() < 0.5 ? "st_pallet_water[facing=west]" : "bunk_crate_ration[facing=west]");
        }
        // what was left: litter, blood, the dead, the notices
        for (int i = 0; i < 10; i++) {
            int x = 2 + rng.nextInt(W - 4), z = ZH + 2 + rng.nextInt(ZC - ZH - 4);
            if (p.get(x, 1, z) == null || "air".equals(p.get(x, 1, z))) {
                String f = Bldg.DIRS[rng.nextInt(4)];
                int[] s = Bldg.step(f);
                if (p.get(x + s[0], 1, z + s[1]) == null || "air".equals(p.get(x + s[0], 1, z + s[1]))) {
                    set(x, 1, z, "home_corpse_head[facing=" + f + "]");
                    set(x + s[0], 1, z + s[1], "home_corpse_foot[facing=" + f + "]");
                }
            }
        }
        for (int z = ZH + 3; z < ZC - 2; z += 5) {
            if (chance() < 0.5) {
                set(1, 2, z, "us_notice_" + rng.nextInt(3) + "[facing=east]");
            }
        }
        b.mess(1, ZH + 1, W - 2, ZC - 1, 1, 0.05);
        b.mess(HX0 + 1, ZF + 1, HX1 - 1, ZH - 1, 1, 0.04);
    }

    /** A shelter tent six across from lx and len along from lz, its door at the north end ({@link Tents}). */
    private void tent(int lx, int lz, int len, String label, boolean collapsed) {
        Tents t = new Tents(p, rng);
        t.hit = 0.6;
        if (collapsed) {
            t.fallen(lx, lz, 6, len, Tents.canvasFor(label), label);
        } else {
            t.pitch(lx, lz, 6, len, Tents.canvasFor(label), label);
        }
    }

    // ------------------------------------------------------------------ the forecourt

    private void forecourt() {
        // the sidewalk along the el's side and behind the back
        for (int z = 0; z <= ZB + 2; z++) {
            set(W, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
        }
        for (int x = 0; x < W; x++) {
            for (int z = ZB + 1; z <= ZB + 2; z++) {
                set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
            }
        }
        for (int x = 0; x <= W; x++) {
            for (int z = 0; z < ZF; z++) {
                if (p.get(x, 0, z) == null || !p.get(x, 0, z).equals("pt_stone_flags") && !p.get(x, 0, z).startsWith("stone_trim")) {
                    set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : chance() < 0.5 ? "pt_stone_flags" : "sidewalk");
                }
            }
        }
        // nothing underfoot in the subway's doorway
        set(45, 1, ZF - 1, "air");
        set(46, 1, ZF - 1, "air");
        // the lamp standards
        for (int x : new int[] {4, 16, 35, 47}) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, 2, "cv_lamp_post");
            }
            set(x, 4, 2, on("cv_lamp_globe_lit", "cv_lamp_globe", 0.3));
        }
        // the checkpoint: a sandbag wall across the front, gaps at the doors, the queue's rope lines
        for (int x = 0; x < W; x++) {
            boolean gap = x >= 14 && x <= 17 || x >= 24 && x <= 27 || x >= 34 && x <= 37 || x >= 44 && x <= 47;
            if (!gap && x != 4 && x != 16 && x != 35 && x != 47) {
                set(x, 1, 4, chance() < 0.08 ? "air" : "sandbags[facing=north]");
            }
        }
        for (int z = 0; z <= 3; z++) {
            set(23, 1, z, "trade_rope_post[axis=z]");
            set(28, 1, z, "trade_rope_post[axis=z]");
        }
        set(20, 1, 5, "pk_military_crate[facing=north]");
        set(31, 1, 5, "pk_military_crate_old[facing=north]");
        // the cars that brought people in and the ones that never left
        p.car(8, 1, 1.5, 90 + (float) (chance() * 20 - 10), 4, 6.5, rng.nextLong());
        p.car(41, 1, 1.5, 270 + (float) (chance() * 20 - 10), 4, 6.5, rng.nextLong());
        for (int i = 0; i < 10; i++) {
            int x = rng.nextInt(W), z = rng.nextInt(ZF - 1);
            p.setIfAir(x, 1, z, chance() < 0.3 ? "bunk_casings" : chance() < 0.5 ? "blood_splat" : chance() < 0.5 ? "paper" : "trash_bag[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
        }
    }
}
