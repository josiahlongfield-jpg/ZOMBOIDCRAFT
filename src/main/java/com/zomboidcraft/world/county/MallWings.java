package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The parts of {@link Mall} off its centre court: the cinema wing to the north (the arcade, the
 * mall's security office and the restrooms under a twin cinema, the lobby between with the box
 * office and the escalator up to the concession stand), and the food court to the south under
 * its skylight (counters round three walls, the tables in the middle, the doors out to the
 * parking decks), with the first-aid room and the management office either side of the passage
 * to it. Drawn in the mall's frame.
 */
final class MallWings {
    /** The cinema wing's walls (the court's sides carried north), its front, its roof. */
    static final int NX0 = Mall.CX0 - 1, NX1 = Mall.CX1 + 1, NZ0 = 6, NTOP = 22;
    /** The lobby down the middle between the auditoriums. */
    private static final int LX0 = 140, LX1 = 152;
    /** The wall between the front and back auditoriums of the quad upstairs. */
    private static final int MID = 28;
    /** The food court's walls, the passage to it from the court, the roof and its lantern. */
    static final int FX0 = 118, FX1 = 174, FZ1 = 142, PX0 = 136, PX1 = 156, FTOP = 12, LANTERN = 16;

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final boolean power;

    MallWings(Placer p, SplittableRandom rng, Bldg b, boolean power) {
        this.p = p;
        this.rng = rng;
        this.b = b;
        this.power = power;
    }

    private void set(int x, int y, int z, String block) {
        p.set(x, y, z, block);
    }

    private String get(int x, int y, int z) {
        return p.get(x, y, z);
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String lamp(double share) {
        return power && chance() < share ? "ceiling_light" : "ceiling_light_off";
    }

    private String outer(int y) {
        return y <= 1 ? (chance() < 0.25 ? "ml_brick_b" : "ml_brick") : y % Mall.UP == 0 ? "ml_precast_band"
                : chance() < 0.3 ? "ml_precast_b" : "ml_precast";
    }

    void build() {
        cinemaShell();
        arcade();
        lobby();
        eastRooms();
        for (int[] a : new int[][] {{NX0 + 2, LX0 - 2}, {LX1 + 2, NX1 - 2}}) {
            auditorium(a[0], a[1], NZ0 + 1, MID - 1);
            auditorium(a[0], a[1], MID + 1, Mall.CZ0 - 2);
        }
        foodCourt();
        southRooms();
    }

    // ------------------------------------------------------------------ the cinema wing

    private void cinemaShell() {
        int up = Mall.UP;
        for (int y = 0; y <= NTOP + 2; y++) {
            for (int z = NZ0; z < Mall.CZ0; z++) {
                set(NX0, y, z, y == NTOP + 2 ? "stone_trim" : outer(y));
                set(NX1, y, z, y == NTOP + 2 ? "stone_trim" : outer(y));
            }
            for (int x = NX0; x <= NX1; x++) {
                set(x, y, NZ0, y == NTOP + 2 ? "stone_trim" : outer(y));
            }
        }
        for (int x = NX0 + 1; x < NX1; x++) {
            for (int z = NZ0 + 1; z < Mall.CZ0 - 1; z++) {
                set(x, 0, z, "tx_terrazzo");
                set(x, up, z, "tr_carpet");
                set(x, NTOP, z, b.wall("roof", 2));
                for (int y = 1; y < NTOP; y++) {
                    if (y != up) {
                        set(x, y, z, "air");
                    }
                }
            }
        }
        // its walls inside: the arcade's and the rooms' sides of the lobby, the auditoriums' walls
        for (int z = NZ0 + 1; z < Mall.CZ0 - 1; z++) {
            for (int y = 1; y < NTOP; y++) {
                if (y == up) {
                    continue;
                }
                set(LX0 - 1, y, z, y < up ? "drywall" : "tr_plaster");
                set(LX1 + 1, y, z, y < up ? "drywall" : "tr_plaster");
            }
        }
        // the wall to the court carried up past its glass to the roof, the auditoriums lined inside, the wall between them
        for (int x = NX0; x <= NX1; x++) {
            for (int y = Mall.ROOF; y <= NTOP + 2; y++) {
                set(x, y, Mall.CZ0 - 1, y == NTOP + 2 ? "stone_trim" : outer(y));
            }
        }
        for (int z = NZ0 + 1; z < Mall.CZ0 - 1; z++) {
            for (int y = up + 1; y < NTOP; y++) {
                set(NX0 + 1, y, z, "tr_plaster");
                set(NX1 - 1, y, z, "tr_plaster");
            }
        }
        for (int x = NX0 + 2; x <= NX1 - 2; x++) {
            if (x >= LX0 - 1 && x <= LX1 + 1) {
                continue;
            }
            for (int y = up + 1; y < NTOP; y++) {
                set(x, y, MID, "tr_plaster");
            }
        }
        // the court's north wall opens into the lobby on both floors
        for (int x = LX0; x <= LX1; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, Mall.CZ0 - 1, "air");
                set(x, up + y, Mall.CZ0 - 1, "air");
            }
            set(x, up, Mall.CZ0 - 1, "tr_carpet");
        }
        // the front: glass doors to the car park under the marquee's canopy, the posters in their cases
        for (int x = LX0 + 1; x <= LX1 - 1; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, NZ0, y <= 2 ? "air" : "tx_glass[axis=x]");
            }
        }
        for (int x = LX0 + 2; x <= LX1 - 3; x += 2) {
            Doors.pair(p, rng, x, 1, NZ0, Doors.SHOP, "south");
        }
        for (int x = LX0 - 6; x <= LX1 + 6; x++) {
            for (int z = NZ0 - 4; z < NZ0; z++) {
                set(x, 5, z, z == NZ0 - 4 ? (power && chance() < 0.5 ? "tr_bulbs_on" : "tr_bulbs") : "stone_trim");
            }
        }
        for (int[] c : new int[][] {{LX0 - 6, NZ0 - 4}, {LX1 + 6, NZ0 - 4}}) {
            for (int y = 1; y <= 4; y++) {
                set(c[0], y, c[1], "tx_column");
            }
        }
        // the letter board along the canopy's front, the name high on the wall over it
        String key = new String[] {"a", "b", "c"}[rng.nextInt(3)];
        if (key.equals("a") && power && chance() < 0.6) {
            key = "a_on";
        }
        for (int i = 0; i < 30; i++) {
            set(LX0 - 1 + 14 - i % 15, 7 - i / 15, NZ0 - 4, "tr_mq_" + key + "_" + i + "[facing=north]");
        }
        for (int x = LX0 - 2; x <= LX1 + 2; x++) {
            if (x < LX0 - 1 || x > LX0 + 13) {
                set(x, 6, NZ0 - 4, "tr_mq_box");
                set(x, 7, NZ0 - 4, "tr_mq_box");
            }
        }
        MallSigns.name(p, rng, "cinema", 6, 2, (LX0 + LX1) / 2 - 3, 12, NZ0 - 1, "north", power && chance() < 0.8);
        for (int i = 0; i < 4; i++) {
            int x = i < 2 ? LX0 - 5 + i * 3 : LX1 + 2 + (i - 2) * 3;
            set(x, 2, NZ0 - 1, "tr_poster_" + rng.nextInt(6) + "_lo[facing=north]");
            set(x, 3, NZ0 - 1, "tr_poster_" + rng.nextInt(6) + "_hi[facing=north]");
        }
        for (int x = NX0 + 2; x < NX1 - 1; x += 4) {
            for (int z = NZ0 + 2; z < Mall.CZ0 - 2; z += 4) {
                if (get(x, up - 1, z) == null) {
                    set(x, up - 1, z, lamp(0.1));
                }
            }
        }
    }

    /** The arcade under the west auditorium: cabinets in rows on the patterned carpet, the change machine. */
    private void arcade() {
        int x0 = NX0 + 1, x1 = LX0 - 2;
        p.fill(x0, 0, NZ0 + 1, x1, 0, Mall.CZ0 - 2, "home_floor_carpet_blue");
        for (int z = NZ0 + 3; z < Mall.CZ0 - 3; z += 4) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                if (x == (x0 + x1) / 2) {
                    continue;
                }
                set(x, 1, z, chance() < 0.15 ? "store_vending[facing=north]" : "pk_vending[facing=" + (chance() < 0.5 ? "north" : "south") + "]");
            }
        }
        // the way in from the lobby
        for (int z = Mall.CZ0 - 8; z <= Mall.CZ0 - 6; z++) {
            for (int y = 1; y <= 3; y++) {
                set(LX0 - 1, y, z, "air");
            }
        }
        b.mess(x0, NZ0 + 1, x1, Mall.CZ0 - 2, 1, 0.03);
    }

    /**
     * The lobby: the box office by the doors, the escalator and the stair up the middle to the
     * cinema's lobby over it, the concession stand upstairs, posters all along.
     */
    private void lobby() {
        int up = Mall.UP;
        // the box office: a booth just in from the doors, its window to them
        for (int x = LX0 + 3; x <= LX1 - 3; x++) {
            set(x, 1, NZ0 + 4, "tr_booth_lo[facing=north]");
            set(x, 2, NZ0 + 4, "tr_booth_hi[facing=north]");
        }
        // the escalator pair (x 144, 145) and the stair (x 147, 148), rising south from z0 to the floor upstairs
        int z0 = 22;
        for (int k = 1; k <= up; k++) {
            int z = z0 + k;
            for (int x = 144; x <= 148; x++) {
                for (int y = 1; y < k; y++) {
                    set(x, y, z, "stone_trim");
                }
                if (x == 146) {
                    set(x, k, z, "stone_trim");
                    set(x, k + 1, z, "tx_glass_rail[axis=z]");
                    continue;
                }
                boolean esc = x <= 145;
                set(x, k, z, esc ? "zomboidcraft:escalator[facing=south,run=" + (x == 144 ? "down" : "up") + "]" : "stairs_concrete[facing=south]");
                if (esc) {
                    set(x, k + 1, z, "tx_esc_side_" + (x == 145 ? "r" : "l") + "[facing=south]");
                }
                for (int y = k + (esc ? 2 : 1); y <= k + Builders.HEADROOM; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int x = 144; x <= 145; x++) {
            String hand = x == 145 ? "r" : "l";
            set(x, 0, z0, "tx_esc_plate[facing=south]");
            set(x, 1, z0, "tx_esc_newel_" + hand + "[facing=north]");
            set(x, up, z0 + up + 1, "tx_esc_plate[facing=north]");
            set(x, up + 1, z0 + up + 1, "tx_esc_newel_" + hand + "[facing=south]");
        }
        // the slot upstairs over them, railed round
        for (int z = z0 + 1; z <= z0 + up; z++) {
            set(143, up + 1, z, "tx_glass_rail[axis=z]");
            set(149, up + 1, z, "tx_glass_rail[axis=z]");
        }
        for (int x = 144; x <= 148; x++) {
            set(x, up + 1, z0, "tx_glass_rail[axis=x]");
        }
        // the concession stand upstairs against the east wall, the popcorn machine, the drinks
        for (int z = 34; z <= 42; z++) {
            set(LX1 - 2, up + 1, z, z == 38 ? "tr_soda[facing=west]" : "tr_candy" + (chance() < 0.5 ? "_empty" : "") + "[facing=west]");
        }
        set(LX1, up + 1, 36, "tr_popcorn_lo[facing=west]");
        set(LX1, up + 2, 36, "tr_popcorn_hi[facing=west]");
        set(LX1, up + 1, 40, "store_cooler[facing=west]");
        set(LX1, up + 2, 40, "store_cooler_top[facing=west]");
        // the posters in their cases, clear of the doors and the stand
        for (int z = 10; z < Mall.CZ0 - 2; z += 6) {
            set(LX0, 2, z, "tr_poster_" + rng.nextInt(6) + "_lo[facing=east]");
            set(LX0, 3, z, "tr_poster_" + rng.nextInt(6) + "_hi[facing=east]");
        }
        for (int z : new int[] {14, 20, 25, 36, 42, 46}) {
            set(LX0, up + 2, z, "tr_poster_" + rng.nextInt(6) + "_lo[facing=east]");
            set(LX0, up + 3, z, "tr_poster_" + rng.nextInt(6) + "_hi[facing=east]");
            if (z < 34) {
                set(LX1, up + 2, z, "tr_poster_" + rng.nextInt(6) + "_lo[facing=west]");
                set(LX1, up + 3, z, "tr_poster_" + rng.nextInt(6) + "_hi[facing=west]");
            }
        }
        // the lobby's ceiling upstairs, a storey and a half up, the chandeliers hung from it
        p.fill(LX0, up + 8, NZ0 + 1, LX1, up + 8, Mall.CZ0 - 2, "tr_plaster");
        for (int x = LX0 + 1; x < LX1; x += 4) {
            for (int z = NZ0 + 3; z < Mall.CZ0 - 1; z += 5) {
                if (get(x, up + 7, z) == null) {
                    set(x, up + 7, z, power && chance() < 0.2 ? "tr_chandelier_on" : "tr_chandelier");
                }
            }
        }
        b.mess(LX0, NZ0 + 1, LX1, Mall.CZ0 - 2, 1, 0.03);
        b.mess(LX0, NZ0 + 1, LX1, Mall.CZ0 - 2, up + 1, 0.03);
        // the stair on up to the roof: north along the lobby's west side through its ceiling, its hut on the roof
        for (int z = NZ0 + 1; z < Mall.CZ0 - 1; z++) {
            for (int x = LX0 + 1; x <= LX0 + 2; x++) {
                if (get(x, up + 7, z) != null && get(x, up + 7, z).startsWith("tr_chandelier")) {
                    set(x, up + 7, z, "air");
                }
            }
        }
        Placer q = p.turned(LX0 + 1, 0, Mall.CZ0 - 3, 3);
        RoofHut.flight(q, rng, 0, 0, up, NTOP, "ml_precast", "stairs_concrete", "south", "ext_rail_edge_dark");
    }

    /**
     * Under the east auditorium: the mall's security office (the desk and its monitors, lockers,
     * the gun cabinet, a cot) with its door to the lobby, and the restrooms off the corridor.
     */
    private void eastRooms() {
        int x0 = LX1 + 2, x1 = NX1 - 1, zm = 24;
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y < Mall.UP; y++) {
                set(x, y, zm, "drywall");
            }
        }
        p.fill(x0, 0, NZ0 + 1, x1, 0, zm - 1, "floor_lino");
        // security, north: the desk under the monitors, lockers, the gun cabinet
        for (int x = x0 + 2; x <= x0 + 5; x++) {
            set(x, 1, NZ0 + 2, "home_desk[facing=south]");
            set(x, 2, NZ0 + 1, "pk_tv_crt[facing=south]");
        }
        set(x0 + 3, 1, NZ0 + 3, "office_chair[facing=north]");
        for (int z = NZ0 + 5; z < zm - 2; z++) {
            set(x1, 1, z, "filing_cabinet[facing=west]");
        }
        set(x1, 1, zm - 2, "home_gun_cabinet[facing=west]");
        set(x1, 2, zm - 2, "home_gun_cabinet_top[facing=west]");
        set(x0, 1, zm - 3, "trade_folding_chair[facing=east]");
        for (int y = 1; y <= 2; y++) {
            set(LX1 + 1, y, 14, "air");
            set(LX1 + 1, y, 15, "air");
        }
        Doors.abandoned(p, rng, LX1 + 1, 1, 14, Doors.FLAT, "west", "left");
        // the restrooms, south: two rooms of stalls and basins, doors to the lobby
        p.fill(x0, 0, zm + 1, x1, 0, Mall.CZ0 - 2, "home_floor_tile");
        int zr = (zm + Mall.CZ0 - 1) / 2;
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y < Mall.UP; y++) {
                set(x, y, zr, "drywall");
            }
        }
        for (int[] room : new int[][] {{zm + 1, zr - 1}, {zr + 1, Mall.CZ0 - 2}}) {
            for (int x = x0 + 1; x <= x1 - 1; x += 2) {
                set(x, 1, room[1], "home_toilet[facing=north]");
            }
            for (int z = room[0] + 1; z < room[1]; z += 2) {
                set(x1, 1, z, "home_sink_white[facing=west]");
                set(x1, 2, z, "home_mirror[facing=west]");
            }
            int dz = room[0] + 1;
            set(LX1 + 1, 1, dz, "air");
            set(LX1 + 1, 2, dz, "air");
            Doors.abandoned(p, rng, LX1 + 1, 1, dz, Doors.FLAT, "west", "right");
            set(LX1, 3, dz, room[0] == zm + 1 ? "tr_sign_men[facing=east]" : "tr_sign_ladies[facing=east]");
        }
    }

    /**
     * One of the quad's auditoriums upstairs, x0 to x1 and z0 to z1: the screen at its north wall
     * behind the masking, the rows raked up toward the back (an aisle down each side stepping up
     * with them), the cross aisle and the projection port at the back, the doors in from the lobby
     * at the front by the screen.
     */
    private void auditorium(int x0, int x1, int z0, int z1) {
        int up = Mall.UP, top = up + 11;
        boolean west = x1 < LX0;
        // the screen: nine across, four high, in the middle of the north wall, tile 0 at the audience's left
        int cx = (x0 + x1) / 2;
        for (int i = 0; i < 36; i++) {
            set(cx - 4 + i % 9, up + 7 - i / 9, z0, "tr_screen_" + i + "[facing=south]");
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = up + 1; y < top; y++) {
                if (get(x, y, z0) == null) {
                    set(x, y, z0, "tr_masking");
                }
            }
        }
        // the rake: rows two deep from z0 + 5, each a block over the one in front after the first two
        int h = up;
        for (int r = 0; ; r++) {
            int z = z0 + 5 + 2 * r;
            if (z + 1 > z1 - 3) {
                break;
            }
            int was = h;
            h = up + Math.max(0, r - 1);
            for (int dz = 0; dz < 2; dz++) {
                for (int x = x0; x <= x1; x++) {
                    for (int y = up + 1; y <= h; y++) {
                        set(x, y, z + dz, "tr_carpet");
                    }
                    boolean aisle = x == x0 || x == x1;
                    if (aisle) {
                        if (dz == 0 && h > was) {
                            set(x, h, z, "tr_stair[facing=south]");
                        }
                    } else if (dz == 1) {
                        double k = chance();
                        set(x, h + 1, z + dz, k < 0.08 ? "tr_seat_broken[facing=north]" : k < 0.2 ? "tr_seat_up[facing=north]"
                                : k < 0.3 ? "tr_seat_pop[facing=north]" : "tr_seat[facing=north]");
                    }
                }
            }
        }
        // the back: a cross aisle level with the last row, the projection port in the wall over it
        for (int z = z1 - 2; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                for (int y = up + 1; y <= h; y++) {
                    set(x, y, z, "tr_carpet");
                }
            }
        }
        set(cx, h + 4, z1 + 1, "tr_port[facing=north]");
        // the ceiling under the roof, the sconces down the walls
        p.fill(x0, top, z0, x1, top, z1, "tr_ceiling");
        for (int z = z0 + 4; z < z1; z += 4) {
            set(x0, top - 2, z, (power && chance() < 0.2 ? "tr_sconce_on" : "tr_sconce") + "[facing=east]");
            set(x1, top - 2, z, (power && chance() < 0.2 ? "tr_sconce_on" : "tr_sconce") + "[facing=west]");
        }
        // the doors in from the lobby, at the front by the screen
        int door = west ? LX0 - 1 : LX1 + 1;
        for (int z = z0 + 2; z <= z0 + 3; z++) {
            for (int y = up + 1; y <= up + 2; y++) {
                set(door, y, z, "air");
            }
        }
        Doors.abandoned(p, rng, door, up + 1, z0 + 2, Doors.FLAT, west ? "east" : "west", "left");
        Doors.abandoned(p, rng, door, up + 1, z0 + 3, Doors.FLAT, west ? "east" : "west", "right");
        b.mess(x0, z0 + 1, x1, z0 + 4, up + 1, 0.04);
    }

    // ------------------------------------------------------------------ the food court

    /**
     * The food court: a hall under a raised lantern of glass off the court's south side, counters
     * round three walls (each a kitchen behind its counter, its menu over it), tables and chairs
     * in the middle among the planters, doors out to the decks.
     */
    private void foodCourt() {
        int z0 = Mall.Z1;
        for (int y = 0; y <= FTOP + 2; y++) {
            for (int z = z0; z <= FZ1; z++) {
                set(FX0, y, z, y == FTOP + 2 ? "stone_trim" : outer(y));
                set(FX1, y, z, y == FTOP + 2 ? "stone_trim" : outer(y));
            }
            for (int x = FX0; x <= FX1; x++) {
                set(x, y, FZ1, y == FTOP + 2 ? "stone_trim" : outer(y));
            }
        }
        for (int x = FX0 + 1; x < FX1; x++) {
            for (int z = z0 + 1; z < FZ1; z++) {
                double r = chance();
                set(x, 0, z, r < 0.1 ? "floor_checker" : "floor_lino");
                for (int y = 1; y < FTOP; y++) {
                    set(x, y, z, "air");
                }
                set(x, FTOP, z, b.wall("roof", 2));
            }
        }
        // the bar's parapet opened where the food court's roof meets it, so the one roof walks on to the other
        for (int x = FX0 + 1; x < FX1; x++) {
            for (int y = Mall.ROOF + 1; y <= Mall.ROOF + 2; y++) {
                set(x, y, z0, "air");
            }
        }
        // the lantern: a glass box over the middle, its clerestory, the flat glass on top
        int lx0 = FX0 + 14, lx1 = FX1 - 14, lz0 = z0 + 8, lz1 = FZ1 - 10;
        for (int x = lx0; x <= lx1; x++) {
            for (int z = lz0; z <= lz1; z++) {
                boolean edge = x == lx0 || x == lx1 || z == lz0 || z == lz1;
                set(x, FTOP, z, "air");
                for (int y = FTOP + 1; y < LANTERN; y++) {
                    set(x, y, z, edge ? (x % 4 == 0 || z % 4 == 0 ? "tx_column" : "tx_glass[axis=" + (z == lz0 || z == lz1 ? "x" : "z") + "]") : "air");
                }
                set(x, LANTERN, z, edge ? "stone_trim" : chance() < 0.12 ? "us_skylight_broken" : "us_skylight");
            }
        }
        // the passage in from the court through the bar's south row and corridor
        for (int x = PX0; x <= PX1; x++) {
            for (int z = Mall.CZ1 + 1; z <= z0; z++) {
                set(x, 0, z, "tx_terrazzo");
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int z = Mall.CZ1 + 1; z < z0; z++) {
            for (int y = 1; y < Mall.UP; y++) {
                set(PX0 - 1, y, z, "drywall");
                set(PX1 + 1, y, z, "drywall");
            }
        }
        // the corridor's doors either side of the passage
        Doors.abandoned(p, rng, PX0 - 1, 1, Mall.Z1 - 2, Doors.STEEL, "east", "left");
        Doors.abandoned(p, rng, PX1 + 1, 1, Mall.Z1 - 2, Doors.STEEL, "west", "right");
        counters();
        // the tables in the middle, chairs round them, the trays and cups left on them
        for (int x = FX0 + 12; x < FX1 - 12; x += 4) {
            for (int z = z0 + 6; z < FZ1 - 10; z += 4) {
                if (chance() < 0.12) {
                    continue;
                }
                set(x, 1, z, "store_table");
                for (int[] c : new int[][] {{1, 0, 3}, {-1, 0, 1}, {0, 1, 2}, {0, -1, 0}}) {
                    if (chance() < 0.75) {
                        set(x + c[0], 1, z + c[1], (chance() < 0.15 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=" + Bldg.DIRS[c[2]] + "]");
                    }
                }
            }
        }
        for (int[] c : new int[][] {{lx0 + 2, lz0 + 2}, {lx1 - 2, lz0 + 2}, {lx0 + 2, lz1 - 2}, {lx1 - 2, lz1 - 2}}) {
            for (int x = c[0] - 1; x <= c[0] + 1; x++) {
                for (int z = c[1] - 1; z <= c[1] + 1; z++) {
                    set(x, 1, z, x != c[0] || z != c[1] ? "stone_trim" : "planter");
                }
            }
            if (chance() < 0.8) {
                Trees.grow(Kit.canopy(p), Trees.Kind.YOUNG, c[0], 2, c[1], rng);
            }
        }
        // the doors out to the service road and the decks, in the middle of the south wall
        int dx0 = (FX0 + FX1) / 2 - 4;
        for (int x = dx0; x < dx0 + 8; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, FZ1, y <= 2 ? "air" : "tx_glass[axis=x]");
            }
        }
        for (int x = dx0; x < dx0 + 8; x += 2) {
            Doors.pair(p, rng, x, 1, FZ1, Doors.SHOP, "north");
        }
        for (int x = FX0 + 3; x < FX1 - 2; x += 5) {
            for (int z = z0 + 3; z < FZ1 - 2; z += 5) {
                if (get(x, FTOP - 1, z) == null && (x < lx0 || x > lx1 || z < lz0 || z > lz1)) {
                    set(x, FTOP - 1, z, lamp(0.2));
                }
            }
        }
        b.mess(FX0 + 1, z0 + 1, FX1 - 1, FZ1 - 1, 1, 0.04);
    }

    private static final String[] FOODS = {"burger", "pizza", "chinese", "pretzel", "taco", "yogurt", "hotdog", "cookie"};

    /**
     * The counters: eight units along the south wall either side of the doors and four down each
     * side wall, each seven wide and six deep: its counter and till across the front, the grill,
     * fryers and fridge behind, the drinks machine.
     */
    private void counters() {
        int dx0 = (FX0 + FX1) / 2 - 4;
        int n = 0;
        for (int x = FX0 + 9; x + 6 < FX1 - 8; x += 7) {
            if (x + 6 >= dx0 - 1 && x <= dx0 + 8) {
                continue;
            }
            unit(x, FZ1 - 7, x + 6, FZ1 - 1, "north", n++);
        }
        for (int z = Mall.Z1 + 4; z + 6 < FZ1 - 8; z += 7) {
            unit(FX0 + 1, z, FX0 + 6, z + 6, "east", n++);
            unit(FX1 - 6, z, FX1 - 1, z + 6, "west", n++);
        }
    }

    /** One counter unit in x0..x1, z0..z1, its front to `front`. */
    private void unit(int x0, int z0, int x1, int z1, String front, int n) {
        boolean alongX = front.equals("north") || front.equals("south");
        // the walls between units, the counter line, the shutter box over it
        for (int y = 1; y < FTOP; y++) {
            if (alongX) {
                for (int z = z0; z <= z1; z++) {
                    set(x0, y, z, "home_wall_tile_white");
                    set(x1, y, z, "home_wall_tile_white");
                }
            } else {
                for (int x = x0; x <= x1; x++) {
                    set(x, y, z0, "home_wall_tile_white");
                    set(x, y, z1, "home_wall_tile_white");
                }
            }
        }
        p.fill(x0, 0, z0, x1, 0, z1, "home_floor_tile");
        int line = switch (front) {
            case "north" -> z0;
            case "south" -> z1;
            case "east" -> x1;
            default -> x0;
        };
        int a0 = alongX ? x0 + 1 : z0 + 1, a1 = alongX ? x1 - 1 : z1 - 1;
        for (int a = a0; a <= a1; a++) {
            int x = alongX ? a : line, z = alongX ? line : a;
            set(x, 1, z, a == a0 + 1 ? "store_register[facing=" + front + "]" : "store_counter[facing=" + front + "]");
            set(x, 4, z, "stone_trim");
            set(x, 5, z, power && chance() < 0.4 ? "dn_glassblock_lit" : "dn_glassblock");
            if (a == a0) {
                for (int y = 2; y <= 3; y++) {
                    set(x, y, z, "st_grille_half[facing=" + front + "]");
                }
            }
        }
        // the kitchen behind: grill, fryer, fridge, drinks along the back wall
        String back = Bldg.opposite(front);
        String[] kit = {"store_grill", "store_oven", "home_fridge", "st_fountain", "store_shelf_grocery"};
        for (int a = a0; a <= a1; a++) {
            int x = alongX ? a : (front.equals("east") ? x0 + 1 : x1 - 1), z = alongX ? (front.equals("north") ? z1 - 1 : z0 + 1) : a;
            set(x, 1, z, kit[(a - a0 + n) % kit.length] + "[facing=" + front + "]");
        }
        // the way in behind the counter: a gap at its end
        int ex = alongX ? a1 : line, ez = alongX ? line : a1;
        set(ex, 1, ez, "air");
        set(ex, 2, ez, "air");
    }

    /**
     * Either side of the passage to the food court: the first-aid room (beds, the cabinet, what
     * the last nurse left) and the management office.
     */
    private void southRooms() {
        int z0 = Mall.CZ1 + 2, z1 = Mall.BS - 1;
        // first aid, west of the passage
        p.fill(Mall.CX0, 0, z0, PX0 - 2, 0, z1, "floor_lino");
        for (int x = Mall.CX0 + 1; x < PX0 - 2; x += 3) {
            set(x, 1, z1, "home_bed_bloody_head[facing=north]");
            set(x, 1, z1 - 1, "home_bed_bloody_foot[facing=north]");
        }
        set(PX0 - 2, 1, z0, "home_medicine[facing=west]");
        set(PX0 - 2, 1, z0 + 1, "store_shelf_pharmacy[facing=west]");
        for (int y = 1; y <= 2; y++) {
            set(PX0 - 1, y, z0 + 2, "air");
        }
        Doors.abandoned(p, rng, PX0 - 1, 1, z0 + 2, Doors.FLAT, "east", "left");
        // the management office, east of it
        p.fill(PX1 + 2, 0, z0, Mall.CX1, 0, z1, "home_floor_carpet_brown");
        for (int x = PX1 + 3; x < Mall.CX1; x += 3) {
            set(x, 1, z0 + 2, "home_desk[facing=south]");
            set(x, 1, z0 + 3, "office_chair[facing=north]");
            set(x, 1, z1, "filing_cabinet[facing=north]");
        }
        set(Mall.CX1, 1, z1, "store_safe[facing=west]");
        for (int y = 1; y <= 2; y++) {
            set(PX1 + 1, y, z0 + 2, "air");
        }
        Doors.abandoned(p, rng, PX1 + 1, 1, z0 + 2, Doors.FLAT, "west", "right");
    }
}
