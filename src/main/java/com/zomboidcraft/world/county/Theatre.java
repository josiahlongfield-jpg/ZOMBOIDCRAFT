package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The picture house on main street. There are no photos of one in the library yet, so this follows
 * what the small-town theatres of the thirties and forties are known for: a cream terracotta front
 * with fluted pilasters and a crest, the marquee hung out over the sidewalk with its letter board
 * and its rows of bulbs, the neon blade standing on top of it with the name, the open entry under a
 * soffit of bulbs with the ticket booth in the middle, the name set in the terrazzo and the poster
 * cases either side.
 *
 * Through the brass doors a shallow lobby: red carpet, wainscot and plaster, the deco frieze, the
 * chandelier, the usher's podium, the candy counter down one side (the popcorn machine and the soda
 * fountain on the back bar, the stockroom behind), the restroom in the front corner, the steel
 * ladder up to the projection booth over the lobby with the manager's office in front of it. The
 * auditorium behind: a cross aisle under the projection ports, rows of velvet seats on iron
 * standards raked down toward the stage in three sections (two aisles, the seat ends lit), sconces
 * down the walls, a chandelier, the coffered ceiling, the gilt proscenium with its masks, the
 * curtains, the screen, the footlights in the apron.
 *
 * Every theatre tells its own end: the last show (the power still on, the board lit, the film
 * burned through on the screen, coats and popcorn left in the seats), closed by order of the county
 * (seats up, curtains drawn, the notice on the front), or the holdout (survivors barricaded in,
 * camped on the stage, the board asking for help) and maybe overrun.
 *
 * Drawn in its lot's frame, 15 wide: the front wall at z = 0 facing north with the sidewalk beyond
 * (the marquee hangs over it, z = -3..-1), the lobby between the walls at ZL and ZA, the
 * auditorium behind to the back wall at d - 1, its floor dug down below the street toward the
 * stage; the proscenium at zp, the screen at d - 2.
 */
final class Theatre {
    /** The lobby's front wall, the wall between the lobby and the auditorium, the upstairs floor. */
    private static final int ZL = 3, ZA = 7, UF = 5;
    /** The ceiling of the auditorium and the top of the front. */
    private static final int CEIL = 11, TOP = 14;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private enum Story { LAST, CLOSED, HOLDOUT }

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final Story story;
    private final boolean lit, overrun;
    private final String brick;
    /** The proscenium, the last row, the lowest floor, the stage block and the stage's standing level. */
    private int d, zp, last, yLow, sy, ys;
    /** The auditorium's floor block y at each z (rows, aisles, the stage). */
    private int[] fy;

    Theatre(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        double r = rng.nextDouble();
        story = r < 0.35 ? Story.LAST : r < 0.68 ? Story.CLOSED : Story.HOLDOUT;
        lit = story == Story.LAST ? rng.nextDouble() < 0.85 : story == Story.HOLDOUT && rng.nextDouble() < 0.45;
        overrun = story != Story.CLOSED && rng.nextDouble() < 0.45;
        brick = pick("brick_red", "brick_buff", "brick_dark", "brick_red");
        b.damage = story == Story.CLOSED ? 0.2 : 0.55;
        b.boarded = story == Story.HOLDOUT ? 0.7 : story == Story.CLOSED ? 0.4 : 0.08;
        b.lit = lit ? 0.4 : 0;
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private double chance() {
        return rng.nextDouble();
    }

    /** The lit version of a light when the power is on (now and then dead anyway). */
    private String on(String block, double dead) {
        return lit && chance() >= dead ? block + "_on" : block;
    }

    /** A lit soffit cell: every other one carries the light, the rest only glow. */
    private String soffit(int x, int z, double dead) {
        String s = on("tr_mq_box", dead);
        return s.endsWith("_on") && (x + z) % 2 != 0 ? "tr_mq_box_glow" : s;
    }

    private boolean empty(int x, int y, int z) {
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    /** A theatre 15 wide and d deep (at least 20). */
    void build(int w, int d) {
        this.d = d;
        zp = d - (d >= 30 ? 6 : d >= 25 ? 5 : 4);
        last = zp - 3;
        int rows = Math.max(1, last - (ZA + 2) + 1);
        yLow = -Math.min(3, Math.max(1, rows / 3));
        sy = yLow + 1;
        ys = sy + 1;
        fy = new int[d];
        for (int i = 0; i < rows; i++) {
            fy[ZA + 2 + i] = -Math.min(-yLow, i * (1 - yLow) / rows);
        }
        for (int z = zp - 2; z < d; z++) {
            fy[z] = yLow;
        }
        shell();
        front();
        marquee();
        entry();
        backRooms();
        lobby();
        upstairs();
        auditorium();
        stage();
        roof();
        switch (story) {
            case LAST -> lastShow();
            case CLOSED -> closed();
            case HOLDOUT -> holdout();
        }
    }

    // ------------------------------------------------------------------ the shell

    private String outer(int y) {
        return b.wall(brick, y);
    }

    /** The plaster lining of a room's side, from the floor at f up to the frieze at top. */
    private String lining(int y, int f, int top) {
        return y == f + 1 ? "tr_wainscot" : y == top ? "tr_frieze" : plaster();
    }

    /** The oxblood damask, now and then stained or blown. */
    private String plaster() {
        double r = chance();
        return r < 0.14 ? "tr_plaster_stain" : r < 0.22 ? "tr_plaster_peel" : "tr_plaster";
    }

    /** The ochre of the house's upper walls. */
    private String upper() {
        return chance() < 0.2 ? "tr_upper_stain" : "tr_upper";
    }

    private void shell() {
        // the side walls: the front part to the parapet over the upstairs, the auditorium taller and dug down
        for (int z = 1; z < d; z++) {
            boolean aud = z >= ZA;
            int y0 = aud ? yLow - 1 : 0, y1 = aud ? CEIL + 2 : 11;
            for (int y = y0; y <= y1; y++) {
                p.set(0, y, z, y == y1 ? "stone_trim" : outer(y));
                p.set(14, y, z, y == y1 ? "stone_trim" : outer(y));
            }
        }
        for (int x = 1; x < 14; x++) {
            for (int y = yLow - 1; y <= CEIL + 2; y++) {
                p.set(x, y, d - 1, y == CEIL + 2 ? "stone_trim" : outer(y));
            }
        }
        // the front part: the floor, the rooms cleared, the upstairs floor, the roof deck
        p.fill(1, 1, 1, 13, 9, ZA - 1, "air");
        p.fill(1, 0, 1, 13, 0, ZA - 1, "floor_concrete");
        for (int x = 1; x <= 13; x++) {
            for (int z = 1; z < ZA; z++) {
                p.set(x, UF, z, z < ZL ? "tr_loft_carpet" : "tr_loft");
                p.set(x, 10, z, b.wall("roof", 2));
            }
        }
        // the lining of the lobby and the upstairs (the restroom tiled)
        for (int z = 1; z < ZA; z++) {
            for (int y = 1; y <= 9; y++) {
                String s = y == UF ? "tr_plaster" : y > UF ? (y == UF + 1 ? "tr_wainscot" : "tr_plaster")
                        : z < ZL ? "fit_tile_cream" : z == ZL ? "tr_plaster" : lining(y, 0, 4);
                p.set(1, y, z, s);
                p.set(13, y, z, s.equals("fit_tile_cream") ? "tr_plaster" : s);
            }
        }
        // the wall between the lobby (and the booth) and the auditorium
        for (int x = 1; x <= 13; x++) {
            for (int y = 1; y <= CEIL + 2; y++) {
                String s = y <= 4 ? lining(y, 0, 4) : y == 10 ? "tr_frieze" : y <= 10 ? upper()
                        : y == CEIL + 2 ? "stone_trim" : outer(y);
                p.set(x, y, ZA, s);
            }
        }
        // the auditorium: cleared down to its raked floor
        for (int z = ZA + 1; z < d - 1; z++) {
            for (int x = 1; x <= 13; x++) {
                for (int y = yLow - 1; y <= CEIL; y++) {
                    p.set(x, y, z, y <= fy[z] ? "floor_concrete" : "air");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the front

    /** A plain block of the terracotta front, weathered: rain-stained, cracked, chipped, bled on by rust, sooted under the coping, or gone to the brick. */
    private String tc(int y) {
        double r = chance();
        if (y == TOP && r < 0.8 || y == TOP - 2 && r < 0.3) {
            return "tr_tc_soot";
        }
        r = chance();
        return r < 0.22 ? "tr_tc_b" : r < 0.3 ? "tr_tc_crack" : r < 0.37 ? "tr_tc_chip" : r < 0.43 ? "tr_tc_rust" : r < 0.47 ? outer(y) : "tr_tc";
    }

    private void front() {
        for (int x = 0; x <= 14; x++) {
            for (int y = 0; y <= TOP; y++) {
                String s;
                boolean recess = x >= 5 && x <= 9;
                if (y == 0) {
                    s = recess ? "tr_terrazzo_name_" + (9 - x) : "stone_trim";
                } else if (recess && y <= 3) {
                    s = "air";
                } else if (recess && y == 4) {
                    s = soffit(x, 0, 0);
                } else if (x == 0 || x == 14) {
                    s = "tr_tc_pilaster";
                } else if (y == 1) {
                    s = b.wall("stone_trim", 1);
                } else if (y == 6 || y == TOP - 1) {
                    s = "tr_tc_cornice";
                } else if (y >= 7 && y <= 8 && (x == 3 || x == 4 || x == 10 || x == 11)) {
                    s = b.glass("x");
                } else if (y >= 7 && y <= TOP - 2 && (x == 5 || x == 9)) {
                    s = "tr_tc_pilaster";
                } else if (y >= 10 && y <= 11 && (x == 2 || x == 3 || x == 4 || x == 10 || x == 11 || x == 12)) {
                    s = "tr_tc_panel";
                } else {
                    s = tc(y);
                }
                p.set(x, y, 0, s);
            }
        }
        // ivy down from the coping at a corner
        for (int x : new int[] {1, 13}) {
            if (chance() < 0.3) {
                for (int y = TOP - 1; y >= TOP - 2 - rng.nextInt(3); y--) {
                    p.setIfAir(x, y, -1, "vines[facing=south]");
                }
            }
        }
        // the crest on the parapet
        p.set(7, TOP + 1, 0, "tr_crest[facing=north]");
        for (int x : new int[] {5, 6, 8, 9}) {
            p.set(x, TOP + 1, 0, "tr_crest_side[facing=north]");
        }
        // the poster cases on the piers, a notice or the graffiti beside them
        int[] cases = {2, 3, 11, 12};
        for (int i = 0; i < cases.length; i++) {
            p.set(cases[i], 1, -1, "tr_poster_" + i + "_lo[facing=north]");
            p.set(cases[i], 2, -1, "tr_poster_" + i + "_hi[facing=north]");
        }
        if (story == Story.CLOSED) {
            p.set(1, 2, -1, "th_notice_" + rng.nextInt(3) + "[facing=north]");
            p.set(13, 2, -1, "th_notice_" + rng.nextInt(3) + "[facing=north]");
        } else if (chance() < 0.7) {
            p.set(chance() < 0.5 ? 1 : 13, 2, -1, "graffiti_" + rng.nextInt(8) + "[facing=north]");
        }
    }

    /**
     * The marquee: the letter board across the front (tile 0 at the viewer's left, high x), the name
     * on both ends, the soffit of bulbs under it, the bulb rows round its top, the blade standing on it.
     */
    private void marquee() {
        String key = switch (story) {
            case LAST -> lit ? "a_on" : "a";
            case CLOSED -> "b";
            case HOLDOUT -> "c";
        };
        for (int i = 0; i < 30; i++) {
            p.set(14 - i % 15, 5 - i / 15, -3, "tr_mq_" + key + "_" + i + "[facing=north]");
        }
        String suffix = lit ? "_on" : "";
        for (int i = 0; i < 4; i++) {
            int col = i % 2, y = 5 - i / 2;
            p.set(0, y, -2 + col, "tr_mq_side_" + i + suffix + "[facing=west]");
            p.set(14, y, -1 - col, "tr_mq_side_" + i + suffix + "[facing=east]");
        }
        for (int x = 1; x <= 13; x++) {
            for (int z = -2; z <= -1; z++) {
                p.set(x, 4, z, soffit(x, z, 0.08));
                p.set(x, 5, z, "tr_mq_box");
            }
        }
        for (int x = 0; x <= 14; x++) {
            p.set(x, 6, -3, on("tr_bulbs", 0.15) + "[axis=x]");
        }
        for (int z = -2; z <= -1; z++) {
            p.set(0, 6, z, on("tr_bulbs", 0.15) + "[axis=z]");
            p.set(14, 6, z, on("tr_bulbs", 0.15) + "[axis=z]");
        }
        // the blade: the cap, RIALTO top to bottom, the foot on the marquee
        p.set(7, 13, -1, on("tr_blade_cap", 0.05) + "[facing=north]");
        for (int i = 0; i < 6; i++) {
            p.set(7, 12 - i, -1, on("tr_blade_" + i, 0.12) + "[facing=north]");
        }
        p.set(7, 6, -1, "tr_blade_foot[facing=north]");
    }

    /** The open entry: terrazzo, the ticket booth, the posters on its side walls, the doors into the lobby. */
    private void entry() {
        for (int z = 1; z < ZL; z++) {
            for (int x = 5; x <= 9; x++) {
                p.set(x, 0, z, "tr_terrazzo");
                p.set(x, 4, z, soffit(x, z, 0.1));
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, "air");
                }
            }
            for (int y = 1; y <= 4; y++) {
                p.set(4, y, z, tc(y));
                p.set(10, y, z, tc(y));
            }
        }
        p.set(7, 1, 1, "tr_booth_lo[facing=north]");
        p.set(7, 2, 1, "tr_booth_hi[facing=north]");
        p.set(5, 1, 1, "tr_poster_4_lo[facing=east]");
        p.set(5, 2, 1, "tr_poster_4_hi[facing=east]");
        p.set(9, 1, 1, "tr_poster_5_lo[facing=west]");
        p.set(9, 2, 1, "tr_poster_5_hi[facing=west]");
        // the lobby's front wall with its two pairs of doors
        for (int x = 2; x <= 12; x++) {
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, ZL, y == 4 ? "tr_frieze" : y == 1 ? "tr_wainscot" : plaster());
            }
        }
        b.doors(5, 1, ZL, "shop_door", "north");
        b.doors(8, 1, ZL, "shop_door", "north");
        if (chance() < 0.4) {
            p.set(5 + rng.nextInt(5), 1, 1 + rng.nextInt(2), chance() < 0.5 ? "glass_shards" : "paper");
        }
    }

    /**
     * The front corners either side of the entry: the restroom on the west (two stalls, the basin
     * and its mirror), the candy stockroom on the east, opening behind the counter.
     */
    private void backRooms() {
        for (int x = 2; x <= 3; x++) {
            for (int z = 1; z < ZL; z++) {
                p.set(x, 0, z, "home_floor_lino_check");
            }
            p.set(x, 1, 1, "fit_toilet_stall[facing=south]");
            p.set(x, 2, 1, "fit_stall_top[facing=south]");
        }
        p.set(2, 1, 2, "fit_vanity_black[facing=east]");
        p.set(2, 2, 2, "fit_mirror_long[facing=east]");
        p.fill(3, 1, ZL, 3, 2, ZL, "air");
        b.door(3, 1, ZL, "home_door_white", "south");
        p.set(3, 3, ZL + 1, "tr_sign_ladies[facing=south]");
        if (chance() < 0.4) {
            p.set(3, 1, 2, chance() < 0.5 ? "home_blood_pool" : "paper");
        }
        // the stockroom: racks of boxes, the cups and the syrup
        p.set(12, 1, 1, "pk_metal_rack[facing=south]");
        p.set(12, 1, 2, "pk_metal_rack[facing=west]");
        p.set(11, 1, 1, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
        if (chance() < 0.6) {
            p.set(11, 2, 1, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        p.fill(11, 1, ZL, 11, 2, ZL, "air");
        b.door(11, 1, ZL, "home_door_white", "south");
    }

    // ------------------------------------------------------------------ the lobby

    private void lobby() {
        for (int x = 2; x <= 12; x++) {
            for (int z = ZL + 1; z < ZA; z++) {
                boolean lane = x >= 3 && x <= 9;
                p.set(x, 0, z, lane && chance() < 0.5 ? "tr_carpet_b" : "tr_carpet");
            }
        }
        // the candy counter down the east side facing the lobby, the staff aisle, the back bar on the wall
        boolean stocked = story != Story.HOLDOUT && chance() < 0.75;
        for (int z = ZL + 1; z < ZA - 1; z++) {
            p.set(10, 1, z, (stocked && chance() < 0.85 ? "tr_candy" : "tr_candy_empty") + "[facing=west]");
        }
        p.set(12, 1, ZL + 1, "tr_popcorn_lo[facing=west]");
        p.set(12, 2, ZL + 1, "tr_popcorn_hi[facing=west]");
        p.set(12, 1, ZL + 2, "tr_backbar[facing=west]");
        p.set(12, 1, ZL + 3, "tr_soda[facing=west]");
        p.set(12, 3, ZL + 2, "tr_sign_food[facing=west]");
        p.set(12, 3, ZL + 3, "tr_lobbycard_" + rng.nextInt(3) + "[facing=west]");
        if (chance() < 0.6) {
            p.set(11, 1, ZL + 2, chance() < 0.5 ? "store_stool" : "paper");
        }
        // the ladder up through the hatch to the booth in the west corner, its sign
        for (int y = 1; y <= UF; y++) {
            p.set(2, y, ZA - 1, "ladder[facing=east]");
        }
        p.set(2, 3, ZA - 2, "tr_sign_booth[facing=east]");
        // the usher's podium on the pier between the doors, the chandelier, sconces, the cards, a seat
        p.set(7, 1, ZL + 1, "tr_podium[facing=north]");
        p.set(7, 4, ZL + 2, on("tr_chandelier", 0.1));
        p.set(4, 3, ZL + 1, on("tr_sconce", 0.2) + "[facing=south]");
        p.set(10, 3, ZL + 1, on("tr_sconce", 0.2) + "[facing=south]");
        p.set(6, 3, ZA - 1, "tr_lobbycard_" + rng.nextInt(3) + "[facing=north]");
        p.set(7, 3, ZA - 1, "tr_lobbycard_" + rng.nextInt(3) + "[facing=north]");
        p.set(6, 1, ZA - 1, "pk_armchair_green[facing=north]");
        p.set(5, 1, ZA - 1, "pk_trash_can[facing=north]");
        // the doors into the auditorium off the back corners
        for (int x : new int[] {3, 8}) {
            p.fill(x, 1, ZA, x + 1, 2, ZA, "air");
            b.doors(x, 1, ZA, "home_door_oak", "north");
        }
        b.mess(3, ZL + 1, 9, ZA - 1, 1, story == Story.CLOSED ? 0.02 : 0.06);
    }

    // ------------------------------------------------------------------ upstairs

    /** The projection booth over the lobby (the ladder comes up in its corner), the manager's office in front. */
    private void upstairs() {
        int y = UF + 1;
        p.set(2, UF, ZA - 1, "ladder[facing=east]");
        for (int yy = y; yy <= 9; yy++) {
            for (int x = 2; x <= 12; x++) {
                p.set(x, yy, ZL, yy == y ? "tr_wainscot" : "tr_plaster");
            }
        }
        p.fill(4, y, ZL, 4, y + 1, ZL, "air");
        b.door(4, y, ZL, "home_door_white", "north");
        // the booth: two projectors at their ports, the rewind bench, cans, reels and film on the floor
        for (int x : new int[] {5, 9}) {
            p.set(x, y, ZA - 1, "tr_projector_lo[facing=south]");
            p.set(x, y + 1, ZA - 1, "tr_projector_hi[facing=south]");
            p.set(x, y, ZA, "tr_port[facing=south]");
        }
        p.set(12, y, ZL + 2, "tr_rewind[facing=west]");
        p.set(12, y, ZL + 1, "tr_cans[facing=west]");
        p.set(11, y, ZA - 1, "tr_cans[facing=north]");
        p.set(12, y, ZA - 1, "store_stool");
        p.set(7, y, ZL + 2, "tr_reel");
        p.set(8, y, ZL + 1, "tr_film[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.set(6, y, ZL + 2, "tr_film[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.set(2, y + 1, ZL + 1, "bunk_fusebox[facing=east]");
        p.set(12, y + 1, ZA - 1, "bunk_extinguisher[facing=west]");
        p.set(7, 9, ZL + 2, lit ? "ceiling_light" : "ceiling_light_off");
        // the office over the entry, its windows on the street
        p.set(10, y, 2, "desk[facing=south]");
        p.set(10, y, 1, "office_chair[facing=south]");
        p.set(12, y, 1, "store_safe[facing=west]");
        p.set(12, y, 2, "pk_file_cabinet[facing=west]");
        p.set(11, y, 1, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.set(2, y, 1, "pk_armchair_green[facing=east]");
        p.set(2, y, 2, "home_coat_rack");
        p.set(7, 8, 2, "bk_wall_clock[facing=north]");
        p.set(8, 7, 2, "tr_lobbycard_" + rng.nextInt(3) + "[facing=north]");
        p.set(7, 9, 1, lit ? "ceiling_light" : "ceiling_light_off");
        b.mess(3, 1, 11, 2, y, 0.08);
    }

    // ------------------------------------------------------------------ the auditorium

    /** The seat block at x in a row: the aisle ends lit, the rest left as the story left them. */
    private String seat(int x) {
        String aisle = x == 3 || x == 9 ? "tr_seat_aisle_l" : x == 5 || x == 11 ? "tr_seat_aisle" : null;
        if (aisle != null) {
            return on(aisle, 0.2) + "[facing=south]";
        }
        double r = chance();
        String s = switch (story) {
            case LAST -> r < 0.5 ? "tr_seat" : r < 0.66 ? "tr_seat_coat" : r < 0.8 ? "tr_seat_pop" : r < 0.9 ? "tr_seat_mix" : r < 0.96 ? "tr_seat_broken" : "tr_seat_up";
            case CLOSED -> r < 0.68 ? "tr_seat_up" : r < 0.84 ? "tr_seat_mix" : r < 0.96 ? "tr_seat" : "tr_seat_broken";
            case HOLDOUT -> r < 0.35 ? "tr_seat" : r < 0.55 ? "tr_seat_mix" : r < 0.75 ? "tr_seat_up" : r < 0.9 ? "tr_seat_broken" : "air";
        };
        return s.equals("air") ? s : s + "[facing=south]";
    }

    private void auditorium() {
        for (int z = ZA + 1; z < zp; z++) {
            int f = fy[z];
            boolean row = z >= ZA + 2 && z <= last;
            for (int x = 2; x <= 12; x++) {
                boolean aisle = !row || x == 4 || x == 10;
                p.set(x, f, z, aisle ? (chance() < 0.3 ? "tr_carpet_b" : "tr_carpet") : b.wall("concrete_dark", 3));
                if (!aisle) {
                    p.set(x, f + 1, z, seat(x));
                } else if ((x == 4 || x == 10) && f < fy[z - 1]) {
                    // a step down the aisle where the floor drops
                    p.set(x, f + 1, z, "tr_stair[facing=north]");
                }
            }
            // the side walls: wainscot, plaster, a band at the sconces, gilt pilasters every fourth block
            boolean pilaster = (z - ZA - 3) % 4 == 0 && z <= zp - 2;
            for (int y = f + 1; y <= 10; y++) {
                boolean col = pilaster && y > f + 1 && y < 10;
                String s = y == 7 ? "tr_frieze" : y > 7 && y < 10 ? upper() : lining(y, f, 10);
                p.set(1, y, z, col ? "tr_prosc_col[facing=east]" : s);
                p.set(13, y, z, col ? "tr_prosc_col[facing=west]" : s);
            }
            if (z >= ZA + 3 && pilaster) {
                p.set(2, 5, z, on("tr_sconce", 0.2) + "[facing=east]");
                p.set(12, 5, z, on("tr_sconce", 0.2) + "[facing=west]");
            }
        }
        // the exits off the cross aisle, the signs over them and over the lobby doors
        for (int side = 0; side < 2; side++) {
            int xl = side == 0 ? 1 : 13, xo = side == 0 ? 0 : 14;
            p.fill(xl, 1, ZA + 1, xl, 2, ZA + 1, "air");
            b.door(xo, 1, ZA + 1, "fire_door", side == 0 ? "west" : "east");
            p.set(side == 0 ? 2 : 12, 3, ZA + 1, "cv_exit_red[facing=" + (side == 0 ? "east" : "west") + "]");
        }
        p.set(4, 3, ZA + 1, "cv_exit_red[facing=south]");
        p.set(8, 3, ZA + 1, "cv_exit_red[facing=south]");
        // the coffered ceiling with the chandelier at its middle
        for (int x = 1; x <= 13; x++) {
            for (int z = ZA + 1; z < d - 1; z++) {
                p.set(x, CEIL, z, "tr_ceiling");
            }
        }
        p.set(7, CEIL - 1, (ZA + 2 + last) / 2, on("tr_chandelier", 0.1));
        // the mess down the aisles
        for (int z = ZA + 1; z < zp - 1; z++) {
            for (int x : new int[] {4, 10}) {
                if (chance() < (story == Story.CLOSED ? 0.03 : 0.12)) {
                    p.setIfAir(x, fy[z] + 1, z, pick("paper", "paper", "blood_splat", "trash_bag[facing=north]", "glass_shards"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the stage

    private void stage() {
        // the stage floor from the apron to the back wall, the steps up at its ends
        for (int x = 1; x <= 13; x++) {
            for (int z = zp - 1; z < d - 1; z++) {
                p.set(x, yLow, z, "floor_concrete");
                if (z >= zp) {
                    p.set(x, sy, z, "tr_stage");
                } else if (x >= 2 && x <= 12) {
                    p.set(x, sy, z, on("tr_apron", 0.15) + "[facing=north]");
                }
            }
        }
        p.set(2, sy, zp - 2, "tr_stair[facing=south]");
        p.set(12, sy, zp - 2, "tr_stair[facing=south]");
        // the proscenium: gilt columns, the band with the masks over the opening, plaster above
        for (int y = sy; y <= 10; y++) {
            p.set(1, y, zp, "tr_prosc_col[facing=north]");
            p.set(13, y, zp, "tr_prosc_col[facing=north]");
        }
        for (int x = 2; x <= 12; x++) {
            for (int y = ys + 9; y <= 10; y++) {
                p.set(x, y, zp, upper());
            }
        }
        for (int i = 0; i < 22; i++) {
            int y = ys + 8 - i / 11;
            if (y <= 10) {
                p.set(12 - i % 11, y, zp, "tr_prosc_" + i + "[facing=north]");
            }
        }
        // the curtains hung just behind the arch: drawn back and tied, or closed across
        boolean shut = story == Story.CLOSED;
        for (int x = 2; x <= 12; x++) {
            boolean edge = x <= 3 || x >= 11;
            p.set(x, ys + 6, zp, "tr_valance[facing=north]");
            if (!shut && !edge) {
                continue;
            }
            for (int y = ys; y <= ys + 5; y++) {
                String c = y == ys ? "tr_curtain_hem" : !shut && y == ys + 2 ? "tr_curtain_tie" : "tr_curtain";
                if (story == Story.HOLDOUT && chance() < 0.35) {
                    c = y == ys ? "air" : "tr_curtain_torn";
                }
                p.set(x, y, zp, c.equals("air") ? c : c + "[facing=north]");
            }
        }
        // backstage: the lining to the ceiling, the screen on the back wall
        for (int z = zp + 1; z < d - 1; z++) {
            for (int y = sy + 1; y <= 10; y++) {
                p.set(1, y, z, "tr_plaster");
                p.set(13, y, z, "tr_plaster");
            }
        }
        // the black masking round the screen
        for (int y = ys; y <= 10; y++) {
            for (int x = 2; x <= 12; x++) {
                if (x == 2 || x == 12 || y == ys || y > ys + 4) {
                    p.set(x, y, d - 2, "tr_masking");
                }
            }
        }
        boolean film = story == Story.LAST && lit;
        for (int i = 0; i < 36; i++) {
            p.set(11 - i % 9, ys + 4 - i / 9, d - 2, "tr_screen_" + (film ? "lit_" : "") + i + "[facing=north]");
        }
    }

    // ------------------------------------------------------------------ the roof

    private void roof() {
        for (int x = 1; x <= 13; x++) {
            for (int z = ZA + 1; z < d - 1; z++) {
                p.set(x, CEIL + 1, z, b.wall("roof", 2));
            }
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(2 + rng.nextInt(11), CEIL + 2, ZA + 2 + rng.nextInt(Math.max(1, d - ZA - 4)), pick("moss_carpet", "weeds", "paper", "moss_carpet"));
        }
        for (int i = 0; i < 2; i++) {
            p.setIfAir(2 + rng.nextInt(11), 11, 1 + rng.nextInt(ZA - 1), pick("moss_carpet", "weeds"));
        }
        p.set(11, 11, 4, "pk_aircon[facing=south]");
        // the ceiling come down where the roof let the rain in
        int holes = story == Story.CLOSED ? rng.nextInt(2) : 1 + rng.nextInt(3);
        for (int i = 0; i < holes; i++) {
            int x = 2 + rng.nextInt(11), z = ZA + 3 + rng.nextInt(Math.max(1, last - ZA - 2));
            p.set(x, CEIL, z, "air");
            if (chance() < 0.6) {
                p.set(x, CEIL + 1, z, "air");
            }
            p.set(x, fy[z] + 1, z, "tr_plaster_chunk[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
    }

    // ------------------------------------------------------------------ the stories

    /** The last show: people ran mid-reel; blood in the lobby, coats left, someone didn't get out. */
    private void lastShow() {
        for (int i = 0; i < 4; i++) {
            p.setIfAir(3 + rng.nextInt(7), 1, ZL + 1 + rng.nextInt(ZA - ZL - 1), pick("home_blood_drag", "blood_splat", "paper", "glass_shards"));
        }
        if (overrun) {
            corpses(3);
        }
    }

    /** Closed by order: shut up tidy, boxes of stock left in the lobby. */
    private void closed() {
        p.setIfAir(9, 1, ZA - 1, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.setIfAir(3, 1, ZL + 2, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
    }

    /**
     * The holdout: the lobby doors barricaded from inside with the seats they pulled out, the exits
     * blocked, a camp on the stage (cots, water, the lantern, candles), the graffiti; maybe overrun.
     */
    private void holdout() {
        for (int x : new int[] {5, 6, 8, 9}) {
            if (!overrun || chance() < 0.5) {
                p.set(x, 1, ZL + 1, pick("tr_seat_broken[facing=north]", "sandbags", "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]",
                        "home_chair_tipped[facing=" + DIRS[rng.nextInt(4)] + "]"));
                if (chance() < 0.6) {
                    p.set(x, 2, ZL + 1, pick("tr_seat_broken[facing=south]", "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]"));
                }
            }
        }
        p.set(1, 1, ZA + 1, "sandbags");
        p.set(13, 1, ZA + 1, "sandbags");
        p.set(1, 2, ZA + 1, "cardboard_box[facing=east]");
        p.set(13, 2, ZA + 1, "cardboard_box[facing=west]");
        // the camp on the stage behind the arch: cots end to end along the screen, the stores at the ends
        int zh = d - 3;
        for (int x : new int[] {4, 7, 10}) {
            p.set(x, ys, zh, "sc_cot_head" + (chance() < 0.4 ? "_b" : "") + "[facing=east]");
            p.set(x - 1, ys, zh, "sc_cot_foot" + (chance() < 0.4 ? "_b" : "") + "[facing=east]");
        }
        p.set(2, ys, zh, "bunk_drum_water");
        p.set(12, ys, zh, "cardboard_box[facing=west]");
        p.setIfAir(11, ys, zh, "trash_bag[facing=north]");
        for (int z = Math.min(zp + 1, zh - 1); z < zh; z++) {
            p.set(7, ys, z, lit || chance() < 0.5 ? "fit_lantern_on" : "fit_lantern");
            p.setIfAir(5, ys, z, "home_candles");
            p.setIfAir(9, ys, z, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        p.set(2, ys + 1, zh, "graffiti_" + rng.nextInt(8) + "[facing=east]");
        if (overrun) {
            corpses(4);
            for (int i = 0; i < 4; i++) {
                p.setIfAir(2 + rng.nextInt(11), ys, zp + 1 + rng.nextInt(Math.max(1, d - zp - 2)), pick("home_blood_pool", "blood_splat"));
            }
        }
    }

    /** Bodies down the aisles and across the cross aisle (the foot the way the body faces). */
    private void corpses(int n) {
        for (int i = 0, tries = 0; i < n && tries < 30; tries++) {
            int x, z, fx, fz;
            String face;
            if (chance() < 0.3) {
                x = 3 + rng.nextInt(9);
                z = ZA + 1;
                fx = x + 1;
                fz = z;
                face = "east";
            } else {
                x = chance() < 0.5 ? 4 : 10;
                z = ZA + 3 + rng.nextInt(Math.max(1, last - ZA - 2));
                fx = x;
                fz = z - 1;
                face = "north";
            }
            if (fy[z] != fy[fz] || !empty(x, fy[z] + 1, z) || !empty(fx, fy[fz] + 1, fz)) {
                continue;
            }
            p.set(x, fy[z] + 1, z, "home_corpse_head[facing=" + face + "]");
            p.set(fx, fy[fz] + 1, fz, "home_corpse_foot[facing=" + face + "]");
            i++;
        }
    }
}
