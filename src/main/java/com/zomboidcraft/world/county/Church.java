package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The county's churches, after the photos in refs/town/church-exterior and church-interior.
 *
 * <ul>
 * <li>The white chapel (the white church with the red doors, Grahamsville): clapboard on a rubble
 * footing, a gabled porch with red doors and a round window over them, the square tower rising
 * from the front of the nave through the roof to a louvred belfry and a white needle spire with a
 * gilt cross; inside beadboard and plaster under the open roof and its collar ties, pale pews,
 * the communion table, pulpit and lectern on the oak platform, the cross on the east wall under
 * a lettered band, schoolhouse globes on rods.</li>
 * <li>The brick church (Marion Christian Church): red brick on a rock-faced stone base, white-framed
 * pointed windows four lights high between buttresses, a standing-seam roof, the square corner
 * tower with its datestone, round-headed doors, the quilt square, the round window and the louvred
 * belfry under a parapet (or a slate spire); inside the same plan grown larger, the piano and the
 * choir, and a basement fellowship hall.</li>
 * <li>The stone church (Sacred Heart, Bellevue; inside St. Stanislaus Kostka, Bay City): buff
 * rock-faced ashlar, the tall front tower with a rose window over the doors, clock dials on three
 * faces, round-headed louvred belfry and a green copper spire; inside white columns and a plaster
 * vault, dark pews with kneelers down a red carpet, the marble communion rail and gates, the high
 * altar before a white and gold reredos with its pinnacles, statues with racks of votive lights
 * either side, the red sanctuary lamp, the confessional, the stations, the font, the organ loft
 * over the doors, the sacristy behind, a parish hall below.</li>
 * </ul>
 *
 * Every one tells what happened: most were left, their glass broken and pews thrown about; some
 * were a last refuge, cots in the aisles, sandbags at the doors, and in some of those the candles
 * still burn, so the windows glow red and gold at night.
 *
 * Drawn in its lot's frame, front along z = 0 facing north. Widths are made even so the aisle,
 * the doors and the tower centre on a block line (cx is the first block east of it).
 */
final class Church {
    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    /** A congregation held out here (cots, sandbags, stores); vigil: their candles still burn. */
    private final boolean refuge, vigil;

    // the frame of the church being built
    private int floor, base, x0, x1, z0, z1, cx, eave, xi0, xi1;
    private String wall, wk, pal;

    Church(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.4 + rng.nextDouble() * 0.45;
        b.boarded = 0.0;
        this.refuge = rng.nextDouble() < 0.45;
        this.vigil = refuge && rng.nextDouble() < 0.6;
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    /** A church of the kind on a lot w wide and d deep, the board out front (church, methodist, stmary) or null. */
    void build(Civic.Church kind, int w, int d, String sign) {
        w -= w & 1;
        int[] busy = switch (kind) {
            case CHAPEL -> chapel(w, d);
            case BRICK -> brick(w, d);
            default -> stone(w, d);
        };
        if (sign != null) {
            // the board on its posts along z = 2, clear of the porch or the tower steps: west of them, else east
            int k = Boards.WIDTH.getOrDefault(sign, 4);
            int signX = busy[0] - 1 - k >= 1 ? busy[0] - 1 - k : busy[1] + 2 + k <= w - 1 ? busy[1] + 2 : -1;
            if (signX >= 0) {
                Boards.board(p, rng, sign, signX, 2, 2);
            }
        }
    }

    // ------------------------------------------------------------------ the white chapel

    /** The chapel; returns the x range its porch takes along the front. */
    private int[] chapel(int w, int d) {
        floor = 1;
        base = floor;
        int nw = w >= 16 ? 14 : w >= 14 ? 12 : 10;
        x0 = (w - nw) / 2;
        x1 = x0 + nw - 1;
        cx = x0 + nw / 2;
        z0 = 5;
        z1 = Math.min(d - 2, z0 + 22);
        eave = floor + 6;
        wall = "siding_white";
        wk = "white";
        pal = rng.nextDouble() < 0.65 ? "amber" : "jewel";
        String roof = pick("grey", "charcoal", "green", "brown");
        shell("ch_rubble", "floor_boards", "ch_wainscot");
        b.roofGable(x0, x1, z0, z1, eave + 1, roof, true, wall, 1);
        for (int y = floor; y <= eave; y++) {
            for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                p.set(c[0], y, c[1], "front_trim_white");
            }
        }
        for (int z = z0 + 5; z <= z1 - 3; z += 3) {
            lancet(x0, floor + 2, z, "west", 3, false);
            lancet(x1, floor + 2, z, "east", 3, false);
        }
        lancet(cx - 3, floor + 2, z1, "south", 3, false);
        lancet(cx + 2, floor + 2, z1, "south", 3, false);
        if (nw >= 12) {
            // either side of the porch, clear of its eaves
            lancet(cx - 5, floor + 2, z0, "north", 3, false);
            lancet(cx + 4, floor + 2, z0, "north", 3, false);
        }
        porch(roof);
        int ridge = eave + (nw + 2) / 2;
        tower(cx - 2, z0, 4, 0, ridge + 3, "white", 3, false, "white");
        // the tower's walls plastered where they stand in the nave
        for (int y = floor + 1; y <= eave; y++) {
            for (int k = 1; k <= 3; k++) {
                p.set(cx - 2, y, z0 + k, "cv_plaster");
                p.set(cx + 1, y, z0 + k, "cv_plaster");
            }
            p.set(cx - 1, y, z0 + 3, "cv_plaster");
            p.set(cx, y, z0 + 3, "cv_plaster");
        }
        // the vestibule under the tower: its ceiling, the doorway from the porch, the doors into the nave
        b.clear(cx - 1, floor + 1, z0 + 1, cx, floor + 4, z0 + 2);
        b.floor(cx - 1, z0 + 1, cx, z0 + 2, floor + 5, "floor_boards");
        b.clear(cx - 1, floor + 1, z0, cx, floor + 2, z0);
        b.doors(cx - 1, floor + 1, z0 + 3, "home_door_oak", "south");
        for (int x = cx - 1; x <= cx; x++) {
            p.set(x, floor + 3, z0 + 3, "home_wall_wainscot");
        }
        chancel(z1 - 3, false);
        pews("ch_pew_pale", z0 + 4, z1 - 7, -1);
        trusses();
        pendants(cx - 3, cx + 2, z0 + 6, z1 - 4);
        // beside the tower: the coat rack and the visitors' book on one side, the stored hymnals on the other
        p.set(x0 + 2, floor + 1, z0 + 1, "home_coat_rack[facing=south]");
        p.set(x0 + 2, floor + 1, z0 + 3, "cv_po_desk[facing=east]");
        p.set(x0 + 1 + 1, floor + 2, z0 + 2, "cv_wanted[facing=east]");
        p.set(x1 - 2, floor + 1, z0 + 1, rng.nextBoolean() ? "cardboard_box" : "home_books_stack[facing=north]");
        refuge(z0 + 3);
        b.mess(x0 + 2, z0 + 1, x1 - 2, z1 - 4, floor + 1, 0.035);
        path(z0 - 4);
        fence(w, cx);
        return new int[] {cx - 4, cx + 3};
    }

    /** The chapel's porch: a little gable on the front with the red doors, a round window over them, a step. */
    private void porch(String roof) {
        int a = cx - 3, c = cx + 2, zf = z0 - 3;
        for (int y = 0; y <= floor + 3; y++) {
            for (int z = zf; z < z0; z++) {
                p.set(a, y, z, y < floor ? "ch_rubble" : b.wall(wall, y));
                p.set(c, y, z, y < floor ? "ch_rubble" : b.wall(wall, y));
            }
            for (int x = a; x <= c; x++) {
                p.set(x, y, zf, y < floor ? "ch_rubble" : b.wall(wall, y));
            }
        }
        for (int x = a + 1; x < c; x++) {
            for (int z = zf + 1; z < z0; z++) {
                p.set(x, floor, z, "floor_boards");
                for (int y = 0; y < floor; y++) {
                    p.set(x, y, z, "ch_rubble");
                }
                for (int y = floor + 1; y <= floor + 3; y++) {
                    p.set(x, y, z, "air");
                }
            }
        }
        b.roofGable(a, c, zf, z0 - 1, floor + 4, roof, true, wall, 1);
        // where the porch roof runs into the front wall, the wall again
        for (int x = a - 1; x <= c + 1; x++) {
            for (int y = floor + 4; y <= floor + 8; y++) {
                String s = p.get(x, y, z0);
                if (s != null && s.startsWith("ext_roof")) {
                    p.set(x, y, z0, b.wall(wall, y));
                }
            }
        }
        b.doors(cx - 1, floor + 1, zf, "ext_door_red", "north");
        p.set(cx, floor + 4, zf, "cv_oculus_white_l[facing=north]");
        p.set(cx - 1, floor + 4, zf, "cv_oculus_white_r[facing=north]");
        p.set(cx + 1, floor + 3, zf - 1, (vigil ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        for (int x = a + 1; x < c; x++) {
            p.set(x, floor, zf - 1, "cv_step_stone[facing=south]");
        }
    }

    // ------------------------------------------------------------------ the brick church

    /** The brick church; returns the x range its tower steps take along the front. */
    private int[] brick(int w, int d) {
        floor = 2;
        base = -3;
        int nw = w >= 18 ? 16 : 14;
        x0 = (w - nw) / 2;
        x1 = x0 + nw - 1;
        cx = x0 + nw / 2;
        z0 = 4;
        z1 = Math.min(d - 3, z0 + 28);
        eave = floor + 7;
        wall = "brick_red";
        wk = "red";
        pal = rng.nextDouble() < 0.5 ? "amber" : "jewel";
        // the tower stands in the front corner of the nave, flush with the gable
        boolean east = rng.nextBoolean();
        int tx0 = east ? x1 - 3 : x0, tz0 = z0;
        shell("ch_rubble", "floor_boards", "ch_wainscot");
        cellar();
        b.roofGable(x0, x1, z0, z1, eave + 1, pick("tin_galv", "tin_galv", "slate", "charcoal"), true, wall, 1);
        // buttresses between the bays, windows four lights high in the bays (behind the tower on its side)
        for (int z = z0 + 5; z <= z1 - 1; z += 4) {
            buttress(x0 - 1, z, "west", floor + 4, "brick_red");
            buttress(x1 + 1, z, "east", floor + 4, "brick_red");
        }
        for (int z = z0 + 3; z <= z1 - 3; z += 4) {
            if (z > z0 + 4 || east) {
                lancet(x0, floor + 2, z, "west", 4, false);
                cellarLight(x0, z, "west");
            }
            if (z > z0 + 4 || !east) {
                lancet(x1, floor + 2, z, "east", 4, false);
                cellarLight(x1, z, "east");
            }
        }
        // the gable front: twin lancets five lights high, the round window up in the peak
        lancet(cx - 1, floor + 2, z0, "north", 5, false);
        lancet(cx, floor + 2, z0, "north", 5, false);
        p.set(cx, eave + 2, z0, "ch_oculus_red_l[facing=north]");
        p.set(cx - 1, eave + 2, z0, "ch_oculus_red_r[facing=north]");
        int ridge = eave + (nw + 2) / 2;
        boolean spire = rng.nextDouble() < 0.35;
        tower(tx0, tz0, 4, 0, ridge + 1, "red", 3, false, spire ? "slate" : null);
        // inside the nave the tower's two walls are plastered
        int side = east ? tx0 : tx0 + 3;
        for (int y = floor + 1; y <= eave; y++) {
            for (int k = 1; k <= 3; k++) {
                p.set(side, y, tz0 + k, "cv_plaster");
                p.set(tx0 + k - (east ? 1 : 0), y, tz0 + 3, "cv_plaster");
            }
        }
        // the tower's foot: round-headed doors up two steps, the datestone, the quilt, the round window
        b.clear(tx0 + 1, floor + 1, tz0 + 1, tx0 + 2, floor + 4, tz0 + 2);
        b.floor(tx0 + 1, tz0 + 1, tx0 + 2, tz0 + 2, floor + 5, "floor_boards");
        b.doors(tx0 + 1, floor + 1, tz0, "ext_door_white", "north");
        p.set(tx0 + 2, floor + 3, tz0, "cv_arch_red_l[facing=north]");
        p.set(tx0 + 1, floor + 3, tz0, "cv_arch_red_r[facing=north]");
        p.set(east ? tx0 + 3 : tx0, floor + 1, tz0, "ch_datestone_" + (rng.nextBoolean() ? "a" : "b") + "[facing=north]");
        steps(tx0, tx0 + 3, tz0);
        p.set(tx0 + 2, floor + 5, tz0 - 1, "ch_quilt_0[facing=north]");
        p.set(tx0 + 1, floor + 5, tz0 - 1, "ch_quilt_1[facing=north]");
        p.set(tx0 + 2, floor + 4, tz0 - 1, "ch_quilt_2[facing=north]");
        p.set(tx0 + 1, floor + 4, tz0 - 1, "ch_quilt_3[facing=north]");
        p.set(tx0 + 2, floor + 8, tz0, "ch_oculus_red_l[facing=north]");
        p.set(tx0 + 1, floor + 8, tz0, "ch_oculus_red_r[facing=north]");
        // the doors on through the tower's back into the nave
        b.doors(tx0 + 1, floor + 1, tz0 + 3, "home_door_oak", "south");
        int sx = east ? x0 + 2 : x1 - 2;
        stairDown(sx, z0 + 2, east ? 1 : -1);
        chancel(z1 - 4, false);
        pews("ch_pew_" + (rng.nextBoolean() ? "pale" : "dark"), z0 + 5, z1 - 8, sx);
        trusses();
        pendants(cx - 4, cx + 3, z0 + 5, z1 - 5);
        refuge(z0 + 3);
        b.mess(x0 + 2, z0 + 1, x1 - 2, z1 - 5, floor + 1, 0.035);
        hall(sx);
        for (int z = 0; z < tz0 - 2; z++) {
            p.set(tx0 + 1, 0, z, "sidewalk");
            p.set(tx0 + 2, 0, z, "sidewalk");
        }
        return new int[] {tx0 - 1, tx0 + 4};
    }

    // ------------------------------------------------------------------ the stone church

    /** The stone church; returns the x range its tower steps take along the front. */
    private int[] stone(int w, int d) {
        floor = 2;
        base = -4;
        int nw = Math.min(20, w - 2);
        nw -= nw & 1;
        x0 = (w - nw) / 2;
        x1 = x0 + nw - 1;
        cx = x0 + nw / 2;
        z0 = 9;
        z1 = Math.min(d - 3, z0 + 34);
        eave = floor + 9;
        wall = "ch_ashlar";
        wk = "ashlar";
        pal = "jewel";
        int tx0 = cx - 3, tz0 = z0 - 6;
        shell("ch_rubble", rng.nextBoolean() ? "cv_terrazzo" : "marble_white", "cv_wainscot");
        cellar();
        b.roofGable(x0, x1, z0, z1, eave + 1, "slate", true, wall, 1);
        ring(floor, "cv_water_table");
        for (int z = z0 + 4; z <= z1 - 2; z += 4) {
            buttress(x0 - 1, z, "west", floor + 6, "ch_ashlar");
            buttress(x1 + 1, z, "east", floor + 6, "ch_ashlar");
        }
        for (int z = z0 + 6; z <= z1 - 3; z += 4) {
            lancet(x0, floor + 2, z, "west", 5, true);
            lancet(x1, floor + 2, z, "east", 5, true);
            cellarLight(x0, z, "west");
            cellarLight(x1, z, "east");
        }
        lancet(x0 + 3, floor + 3, z0, "north", 3, true);
        lancet(x1 - 3, floor + 3, z0, "north", 3, true);
        lancet(cx - 1, floor + 4, z1, "south", 4, true);
        lancet(cx, floor + 4, z1, "south", 4, true);
        // the tower: doors under a round arch, the rose over them, the clock, the belfry, the copper
        int yb = Math.max(floor + 18, eave + (nw + 2) / 2 + 1);
        tower(tx0, tz0, 6, 0, yb, "ashlar", 4, true, "copper");
        // the vestibule up to a ceiling over the rose window
        b.floor(tx0 + 1, tz0 + 1, tx0 + 4, tz0 + 4, floor, "cv_terrazzo");
        b.floor(tx0 + 1, tz0 + 1, tx0 + 4, tz0 + 4, floor + 10, "floor_boards");
        b.clear(tx0 + 1, floor + 1, tz0 + 1, tx0 + 4, floor + 9, tz0 + 4);
        b.doors(cx - 1, floor + 1, tz0, "ext_door_oak", "north");
        p.set(cx, floor + 3, tz0, "cv_arch_stone_l[facing=north]");
        p.set(cx - 1, floor + 3, tz0, "cv_arch_stone_r[facing=north]");
        steps(tx0, tx0 + 5, tz0);
        p.set(tx0, floor + 1, tz0 - 1, vigil ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        p.set(tx0 + 5, floor + 1, tz0 - 1, vigil ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        rose(cx - 2, floor + 8, tz0, "north");
        boolean lit = vigil && rng.nextBoolean();
        dial(cx - 2, yb - 2, tz0 - 1, "north", lit);
        dial(tz0 + 1, yb - 2, tx0 - 1, "west", lit);
        dial(tz0 + 1, yb - 2, tx0 + 6, "east", lit);
        // the vestibule's stoups and the doors on into the nave, under the organ loft
        b.clear(cx - 1, floor + 1, tz0 + 5, cx, floor + 2, z0 + 1);
        b.doors(cx - 1, floor + 1, z0, "home_door_oak", "south");
        p.set(cx - 1, floor, z0 - 1, "cv_terrazzo");
        p.set(cx, floor, z0 - 1, "cv_terrazzo");
        p.set(cx - 2, floor + 2, z0 + 1, "ch_stoup[facing=south]");
        p.set(cx + 1, floor + 2, z0 + 1, "ch_stoup[facing=south]");
        columns();
        vault();
        loft();
        sanctuary();
        pews("ch_pew_dark", z0 + 6, z1 - 11, xi1);
        for (int z = z0 + 1; z <= z1 - 10; z++) {
            for (int x = cx - 1; x <= cx; x++) {
                if (p.get(x, floor + 1, z) == null) {
                    p.set(x, floor + 1, z, "cv_carpet_red");
                }
            }
        }
        p.set(xi0 + 1, floor + 1, z0 + 6, "ch_font");
        confessional(xi0, z0 + 15, "east");
        for (int z = z0 + 10; z <= z1 - 11; z += 4) {
            p.set(xi0, floor + 3, z, "ch_station_" + (rng.nextBoolean() ? "a" : "b") + "[facing=east]");
            p.set(xi1, floor + 3, z, "ch_station_" + (rng.nextBoolean() ? "a" : "b") + "[facing=west]");
        }
        for (int k = 0; k < 2; k++) {
            int bz = z0 + 10 + k * 8, bx = k == 0 ? xi1 : xi0;
            String f = k == 0 ? "west" : "east";
            p.set(bx, floor + 6, bz, "ch_banner_" + k + "_top[facing=" + f + "]");
            p.set(bx, floor + 5, bz, "ch_banner_" + k + "[facing=" + f + "]");
        }
        for (int z = z0 + 8; z <= z1 - 10; z += 6) {
            hang(cx - 3, z, "ch_chandelier", 0.06, eave - 1);
            hang(cx + 2, z, "ch_chandelier", 0.06, eave - 1);
        }
        // its head a step in from the front wall's lining, so you come onto it from the nave
        stairDown(xi1, z0 + 2, -1);
        refuge(z0 + 1);
        b.mess(xi0, z0 + 1, xi1, z1 - 4, floor + 1, 0.03);
        hall(xi1);
        path(tz0 - 2);
        return new int[] {tx0 - 1, tx0 + 6};
    }

    // ------------------------------------------------------------------ the shell

    /** Walls from the ground to the eaves (the footing below the floor), the floor, the lining inside. */
    private void shell(String footing, String floorBlock, String dado) {
        for (int y = 0; y <= eave; y++) {
            String m = y < floor ? footing : null;
            for (int x = x0; x <= x1; x++) {
                p.set(x, y, z0, m != null ? m : b.wall(wall, y));
                p.set(x, y, z1, m != null ? m : b.wall(wall, y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x0, y, z, m != null ? m : b.wall(wall, y));
                p.set(x1, y, z, m != null ? m : b.wall(wall, y));
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                for (int y = 0; y < floor; y++) {
                    p.set(x, y, z, "concrete_dark");
                }
                p.set(x, floor, z, floorBlock);
            }
        }
        b.clear(x0 + 1, floor + 1, z0 + 1, x1 - 1, eave, z1 - 1);
        // plaster over a dado inside the walls (the windows open through it)
        xi0 = x0 + 2;
        xi1 = x1 - 2;
        for (int y = floor + 1; y <= eave; y++) {
            String s = y == floor + 1 ? dado : "cv_plaster";
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x0 + 1, y, z, s);
                p.set(x1 - 1, y, z, s);
            }
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                p.set(x, y, z0 + 1, s);
                p.set(x, y, z1 - 1, s);
            }
        }
    }

    /** A basement under the nave from base up to the floor: rubble walls, lino, cleared. */
    private void cellar() {
        for (int y = base; y < floor; y++) {
            for (int x = x0; x <= x1; x++) {
                p.set(x, y, z0, "cellar_wall");
                p.set(x, y, z1, "cellar_wall");
            }
            for (int z = z0; z <= z1; z++) {
                p.set(x0, y, z, "cellar_wall");
                p.set(x1, y, z, "cellar_wall");
            }
        }
        b.floor(x0 + 1, z0 + 1, x1 - 1, z1 - 1, base, "floor_lino");
        b.clear(x0 + 1, base + 1, z0 + 1, x1 - 1, floor - 1, z1 - 1);
    }

    /** One piece in front of every wall cell at y, all round, facing out. */
    private void ring(int y, String piece) {
        for (int x = x0; x <= x1; x++) {
            p.set(x, y, z0 - 1, piece + "[facing=north]");
            p.set(x, y, z1 + 1, piece + "[facing=south]");
        }
        for (int z = z0; z <= z1; z++) {
            p.set(x0 - 1, y, z, piece + "[facing=west]");
            p.set(x1 + 1, y, z, piece + "[facing=east]");
        }
    }

    /** A buttress against the wall: a pier of the wall's stuff up to top, a weathering on it. */
    private void buttress(int x, int z, String out, int top, String m) {
        for (int y = 0; y <= top; y++) {
            p.set(x, y, z, y < floor ? "ch_rubble" : b.wall(m, y));
        }
        p.set(x, top + 1, z, "cv_water_table[facing=" + out + "]");
    }

    /** A grille over a little window into the basement, low in the wall. */
    private void cellarLight(int x, int z, String out) {
        p.set(x, floor - 1, z, rng.nextDouble() < 0.3 ? "window_broken[axis=z]" : "window[axis=z]");
        p.set(x + (out.equals("west") ? -1 : 1), floor - 1, z, "cv_grille[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ windows

    /** How a window was left: lit from inside by the candles, broken, or whole. */
    private String state() {
        if (vigil && rng.nextDouble() < 0.7) {
            return "_lit";
        }
        return rng.nextDouble() < b.damage * 0.45 ? "_broken" : "";
    }

    /**
     * A lancet h lights high from y in the wall cell (x, z), facing out: the bottom light with its
     * sill, the middle ones, the pointed (or round) head; the lining inside opened back to it.
     */
    private void lancet(int x, int y, int z, String out, int h, boolean round) {
        lancetOf(x, y, z, out, h, round, pal, state());
        int[] in = inward(out);
        for (int k = 0; k < h; k++) {
            if (y + k > floor + 1) {
                p.set(x + in[0], y + k, z + in[1], "air");
            }
        }
    }

    private void lancetOf(int x, int y, int z, String out, int h, boolean round, String glass, String st) {
        for (int k = 0; k < h; k++) {
            String part = k == 0 ? "lo" : k == h - 1 ? (round ? "hi_round" : "hi") : "mid";
            p.set(x, y + k, z, "ch_win_" + wk + "_" + glass + "_" + part + st + "[facing=" + out + "]");
        }
    }

    private static int[] inward(String out) {
        return switch (out) {
            case "west" -> new int[] {1, 0};
            case "east" -> new int[] {-1, 0};
            case "north" -> new int[] {0, 1};
            default -> new int[] {0, -1};
        };
    }

    /** A four-by-four rose window with its top row at ytop in a north (or south) wall from xr. */
    private void rose(int xr, int ytop, int z, String out) {
        String st = state();
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int x = out.equals("north") ? xr + 3 - c : xr + c;
                p.set(x, ytop - r, z, "ch_rose_" + wk + st + "_" + (r * 4 + c) + "[facing=" + out + "]");
            }
        }
    }

    /**
     * A clock dial four by four in the cells in front of a wall, its top at ytop; a is the lowest
     * x (north and south faces) or z (east and west faces) it covers, f the cell's other coordinate.
     */
    private void dial(int a, int ytop, int f, String out, boolean lit) {
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int u = switch (out) {
                    case "north", "east" -> a + 3 - c;
                    default -> a + c;
                };
                String piece = "cv_dial" + (lit ? "_lit" : "") + "_" + (r * 4 + c) + "[facing=" + out + "]";
                if (out.equals("north") || out.equals("south")) {
                    p.set(u, ytop - r, f, piece);
                } else {
                    p.set(f, ytop - r, out.equals("west") ? a + c : a + 3 - c, piece);
                }
            }
        }
    }

    // ------------------------------------------------------------------ towers and spires

    /**
     * A square tower size across from (tx0, tz0), its walls from y0 up; the belfry's louvred
     * openings (h high, round-headed or pointed) from yb, the bell, a cornice, then a spire of the
     * material (white, slate, copper) or, with none, a parapet round a flat top.
     */
    private void tower(int tx0, int tz0, int size, int y0, int yb, String key, int h, boolean round, String spire) {
        int tx1 = tx0 + size - 1, tz1 = tz0 + size - 1, top = yb + h;
        for (int y = y0; y <= top; y++) {
            for (int x = tx0; x <= tx1; x++) {
                for (int z = tz0; z <= tz1; z++) {
                    boolean edge = x == tx0 || x == tx1 || z == tz0 || z == tz1;
                    if (edge) {
                        p.set(x, y, z, y < floor ? "ch_rubble" : wall.equals("siding_white") && (x == tx0 || x == tx1) && (z == tz0 || z == tz1)
                                ? "front_trim_white" : b.wall(wall, y));
                    } else if (y > floor + 6) {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
        // where a roof's overhang ran across a face of the tower, cut it back
        for (int y = y0; y <= top; y++) {
            for (int x = tx0 - 1; x <= tx1 + 1; x++) {
                for (int z : new int[] {tz0 - 1, tz1 + 1}) {
                    unroof(x, y, z);
                }
            }
            for (int z = tz0; z <= tz1; z++) {
                unroof(tx0 - 1, y, z);
                unroof(tx1 + 1, y, z);
            }
        }
        b.floor(tx0 + 1, tz0 + 1, tx1 - 1, tz1 - 1, yb - 1, "floor_boards");
        // louvred openings in the middle of each face
        String saved = wk;
        wk = key;
        int m0 = tx0 + size / 2 - 1, mz = tz0 + size / 2 - 1;
        for (int i = 0; i < 2; i++) {
            lancetOf(m0 + i, yb, tz0, "north", h, round, "louvre", "");
            lancetOf(m0 + i, yb, tz1, "south", h, round, "louvre", "");
            lancetOf(tx0, yb, mz + i, "west", h, round, "louvre", "");
            lancetOf(tx1, yb, mz + i, "east", h, round, "louvre", "");
        }
        wk = saved;
        p.set(m0, yb + h - 1, mz, "cv_bell_c");
        // the cornice round the top
        for (int x = tx0; x <= tx1; x++) {
            p.set(x, top, tz0 - 1, "cv_cornice_stone[facing=north]");
            p.set(x, top, tz1 + 1, "cv_cornice_stone[facing=south]");
        }
        for (int z = tz0; z <= tz1; z++) {
            p.set(tx0 - 1, top, z, "cv_cornice_stone[facing=west]");
            p.set(tx1 + 1, top, z, "cv_cornice_stone[facing=east]");
        }
        if (wall.equals("siding_white")) {
            for (int x = tx0; x <= tx1; x++) {
                for (int z = tz0; z <= tz1; z++) {
                    if (x == tx0 || x == tx1 || z == tz0 || z == tz1) {
                        p.set(x, top, z, "front_trim_white");
                    }
                }
            }
            for (int x = tx0 - 1; x <= tx1 + 1; x++) {
                p.set(x, top, tz0 - 1, "air");
                p.set(x, top, tz1 + 1, "air");
            }
            for (int z = tz0; z <= tz1; z++) {
                p.set(tx0 - 1, top, z, "air");
                p.set(tx1 + 1, top, z, "air");
            }
        }
        b.floor(tx0 + 1, tz0 + 1, tx1 - 1, tz1 - 1, top, "roof");
        if (spire != null) {
            pyramid(tx0, tz0, size, top + 1, spire);
        } else {
            // a battlemented parapet: merlons capped in stone at the corners and between
            for (int x = tx0; x <= tx1; x++) {
                for (int z = tz0; z <= tz1; z++) {
                    if (x == tx0 || x == tx1 || z == tz0 || z == tz1) {
                        boolean merlon = (x - tx0 + z - tz0) % 2 == 0;
                        p.set(x, top + 1, z, b.wall(wall, top + 1));
                        if (merlon) {
                            p.set(x, top + 2, z, b.wall(wall, top + 2));
                            p.set(x, top + 3, z, "stone_trim");
                        } else {
                            p.set(x, top + 2, z, "stone_trim");
                        }
                    }
                }
            }
        }
    }

    private void unroof(int x, int y, int z) {
        String s = p.get(x, y, z);
        if (s != null && s.startsWith("ext_roof")) {
            p.set(x, y, z, "air");
        }
    }

    /** A spire over a square size across: the broach stepping in a ring at a time, the tapering octagon, the cross. */
    private void pyramid(int tx0, int tz0, int size, int y, String m) {
        for (int i = 0; ; i++) {
            int lo = i, hi = size - 1 - i, yy = y + i;
            if (hi - lo == 1) {
                p.set(tx0 + lo, yy, tz0 + lo, "ch_broach_" + m + "_corner[facing=north]");
                p.set(tx0 + hi, yy, tz0 + lo, "ch_broach_" + m + "_corner[facing=east]");
                p.set(tx0 + hi, yy, tz0 + hi, "ch_broach_" + m + "_corner[facing=south]");
                p.set(tx0 + lo, yy, tz0 + hi, "ch_broach_" + m + "_corner[facing=west]");
                for (int k = 0; k < 4; k++) {
                    p.set(tx0 + lo, yy + 1 + k, tz0 + lo, "ch_spire_" + m + "_" + k + "_c");
                }
                p.set(tx0 + lo, yy + 5, tz0 + lo, "ch_cross_finial_c");
                return;
            }
            if (hi == lo) {
                for (int k = 0; k < 4; k++) {
                    p.set(tx0 + lo, yy + k, tz0 + lo, "ch_spire_" + m + "_" + k);
                }
                p.set(tx0 + lo, yy + 4, tz0 + lo, "ch_cross_finial[facing=north]");
                return;
            }
            for (int a = lo + 1; a < hi; a++) {
                p.set(tx0 + a, yy, tz0 + lo, "ch_broach_" + m + "[facing=north]");
                p.set(tx0 + a, yy, tz0 + hi, "ch_broach_" + m + "[facing=south]");
                p.set(tx0 + lo, yy, tz0 + a, "ch_broach_" + m + "[facing=west]");
                p.set(tx0 + hi, yy, tz0 + a, "ch_broach_" + m + "[facing=east]");
            }
            p.set(tx0 + lo, yy, tz0 + lo, "ch_broach_" + m + "_corner[facing=north]");
            p.set(tx0 + hi, yy, tz0 + lo, "ch_broach_" + m + "_corner[facing=east]");
            p.set(tx0 + hi, yy, tz0 + hi, "ch_broach_" + m + "_corner[facing=south]");
            p.set(tx0 + lo, yy, tz0 + hi, "ch_broach_" + m + "_corner[facing=west]");
        }
    }

    // ------------------------------------------------------------------ inside: the plain churches

    /**
     * The east end of a chapel or the brick church from zc: the oak platform up a step with the
     * pulpit and lectern on it, the communion table below, the cross on the wall under the band
     * of lettering, flags, the hymn board, the piano; the brick church's choir chairs.
     */
    private void chancel(int zc, boolean choir) {
        int y = floor + 1;
        for (int x = xi0; x <= xi1; x++) {
            for (int z = zc; z < z1 - 1; z++) {
                p.set(x, y, z, "ch_floor_oak");
            }
            p.set(x, y, zc - 1, "ch_step_oak[facing=north]");
        }
        int ta = zc - 2;
        p.set(cx - 1, y, ta, "ch_altar[facing=north]");
        p.set(cx, y, ta, "ch_altar[facing=north]");
        p.set(cx - 1, y + 1, ta, "ch_altar_cross[facing=north]");
        p.set(cx, y + 1, ta, (vigil ? "ch_candles_lit" : "ch_candles") + "[facing=north]");
        p.set(cx + 2, y + 1, zc, "ch_pulpit");
        p.set(cx + 2, y + 2, zc, "ch_pulpit_top[facing=north]");
        p.set(cx - 3, y + 1, zc, "ch_lectern[facing=north]");
        p.set(cx - 1, y + 3, z1 - 2, "ch_cross_lo[facing=north]");
        p.set(cx - 1, y + 4, z1 - 2, "ch_cross_mid[facing=north]");
        p.set(cx - 1, y + 5, z1 - 2, "ch_cross_hi[facing=north]");
        for (int i = 0; i < 6; i++) {
            p.set(cx + 2 - i, eave, z1 - 2, "ch_text_6_" + i + "[facing=north]");
        }
        p.set(xi0, y + 1, z1 - 2, "cv_flag_us[facing=east]");
        p.set(xi0, y + 2, z1 - 2, "cv_flag_us_top[facing=east]");
        p.set(xi1, y + 2, zc - 2, "ch_hymn_board[facing=west]");
        p.set(xi1, y + 1, zc + 1, "home_piano_l[facing=west]");
        p.set(xi1, y + 1, zc + 2, "home_piano_r[facing=west]");
        for (int x = xi0 + 1; x <= cx - 3; x++) {
            p.set(x, y + 1, z1 - 2, rng.nextDouble() < 0.15 ? "home_chair_tipped[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]" : "pk_chair_wooden[facing=north]");
        }
        if (vigil) {
            p.set(cx - 2, y + 1, zc, "ch_candles_lit[facing=north]");
            p.set(cx + 1, y + 1, zc, "ch_candles_lit[facing=north]");
        }
    }

    /**
     * Pews from za to zb every other row either side of the aisle (cx - 1 and cx), with their
     * ends; hymnals in some racks, some thrown down, some gone. skipX: a column kept clear (a stair).
     */
    private void pews(String kindOf, int za, int zb, int skipX) {
        boolean kneel = kindOf.equals("ch_pew_dark") && wk.equals("ashlar");
        for (int z = za; z <= zb; z += 2) {
            for (int side = 0; side < 2; side++) {
                int a = side == 0 ? xi0 : cx + 1, c = side == 0 ? cx - 2 : xi1;
                if (skipX >= a && skipX <= c) {
                    if (skipX == a) {
                        a++;
                    } else {
                        c--;
                    }
                }
                for (int x = a; x <= c; x++) {
                    if (p.get(x, floor + 1, z) != null) {
                        continue;
                    }
                    double r = rng.nextDouble();
                    if (r < 0.06 + b.damage * 0.05) {
                        continue;  // dragged off to the doors
                    }
                    if (r < 0.12 + b.damage * 0.08) {
                        p.set(x, floor + 1, z, kindOf + "_broken[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
                        continue;
                    }
                    String name = kindOf + (kneel ? "_kneel" : "");
                    if (x == a || p.get(x - 1, floor + 1, z) == null && x > a) {
                        name += "_end";
                    } else if (x == c) {
                        name += "_end_l";
                    } else if (!kneel && rng.nextDouble() < 0.4) {
                        name += "_books";
                    }
                    p.set(x, floor + 1, z, name + "[facing=south]");
                    if (rng.nextDouble() < 0.1) {
                        p.setIfAir(x, floor + 1, z + 1, rng.nextBoolean() ? "paper" : "home_books_stack[facing=north]");
                    }
                }
            }
        }
    }

    /** Collar ties across the open roof every four blocks, a post up to the ridge from each. */
    private void trusses() {
        for (int z = z0 + 3; z < z1 - 1; z += 4) {
            int y = eave + 3;
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                if (p.get(x, y, z) == null) {
                    p.set(x, y, z, "ch_beam");
                }
            }
            for (int x = cx - 1; x <= cx; x += 1) {
                for (int yy = y + 1; yy < y + 12 && p.get(x, yy, z) == null; yy++) {
                    p.set(x, yy, z, "ch_beam");
                }
            }
        }
    }

    /** Lights hung on rods down the nave from the roof: two rows from za to zb. */
    private void pendants(int xa, int xb, int za, int zb) {
        for (int z = za; z <= zb; z += 4) {
            hang(xa, z, "ch_pendant", 0.05, eave);
            hang(xb, z, "ch_pendant", 0.05, eave);
        }
    }

    /** A light hung at y with its chain up to whatever is above it. */
    private void hang(int x, int z, String light, double lit, int y) {
        if (p.get(x, y, z) != null) {
            return;
        }
        p.set(x, y, z, light + (rng.nextDouble() < lit ? "_lit" : ""));
        for (int yy = y + 1; yy < y + 14 && p.get(x, yy, z) == null; yy++) {
            p.set(x, yy, z, "cv_chain");
        }
    }

    /**
     * What the last people here left: sandbags behind the doors at zd, cots down the aisle and
     * across the front, stores, blood; candles where some still burn.
     */
    private void refuge(int zd) {
        if (!refuge) {
            for (int i = 0; i < 4; i++) {
                p.setIfAir(xi0 + rng.nextInt(xi1 - xi0 + 1), floor + 1, z0 + 2 + rng.nextInt(z1 - z0 - 8), "blood_splat");
            }
            return;
        }
        for (int x = cx - 2; x <= cx + 1; x++) {
            if (rng.nextDouble() < 0.85) {
                p.setIfAir(x, floor + 1, zd + 1, "sandbags[facing=north]");
            }
        }
        for (int z = zd + 3; z < z1 - 8; z += 3) {
            if (rng.nextDouble() < 0.6) {
                int x = rng.nextBoolean() ? cx - 1 : cx;
                if (p.get(x, floor + 1, z) == null && p.get(x, floor + 1, z + 1) == null) {
                    p.set(x, floor + 1, z, "ch_cot_head[facing=north]");
                    p.set(x, floor + 1, z + 1, "ch_cot_foot[facing=north]");
                }
            }
        }
        for (int i = 0; i < 8; i++) {
            int x = xi0 + rng.nextInt(xi1 - xi0 + 1), z = z0 + 2 + rng.nextInt(Math.max(1, z1 - z0 - 8));
            p.setIfAir(x, floor + 1, z, pick("crate", "cardboard_box", "home_candles", "blood_splat", "paper", "trade_folding_chair_tipped[facing=north]"));
        }
        if (vigil) {
            for (int i = 0; i < 5; i++) {
                int x = xi0 + rng.nextInt(xi1 - xi0 + 1), z = z0 + 3 + rng.nextInt(Math.max(1, z1 - z0 - 8));
                p.setIfAir(x, floor + 1, z, "ch_candles_lit[facing=north]");
            }
        }
    }

    /**
     * The stair down to the basement along x from z (its top in the floor), a rail on the side
     * toward dir. You step onto its head from that side, kept clear two high.
     */
    private void stairDown(int sx, int z, int dir) {
        for (int yy = floor + 1; yy <= floor + 2; yy++) {
            p.set(sx + dir, yy, z, "air");
        }
        for (int k = 0; k <= floor - base - 1; k++) {
            int zz = z + k;
            p.set(sx, floor - k, zz, "stairs_concrete[facing=north]");
            for (int yy = floor - k + 1; yy <= floor + 2; yy++) {
                p.set(sx, yy, zz, "air");
            }
            if (k > 0) {
                p.set(sx + dir, floor + 1, zz, "cv_bar_rail[axis=z]");
            }
        }
        p.set(sx, floor + 1, z + floor - base, "cv_bar_rail[axis=x]");
    }

    // ------------------------------------------------------------------ inside: the stone church

    /** Two rows of white columns from the organ loft to the sanctuary, an arcade over them up into the vault. */
    private void columns() {
        int za = z0 + 8, zb = z1 - 11;
        for (int cxl : new int[] {cx - 5, cx + 4}) {
            for (int z = za; z <= zb; z += 4) {
                p.set(cxl, floor + 1, z, "cv_column_base_white");
                for (int y = floor + 2; y < eave; y++) {
                    p.set(cxl, y, z, "cv_column_white");
                }
                p.set(cxl, eave, z, "cv_column_cap_white[facing=" + (cxl < cx ? "east" : "west") + "]");
            }
            // the arcade: arches springing from the capitals, the wall over them up to the vault
            for (int z = za - 2; z <= zb + 2; z++) {
                int dz = Math.floorMod(z - za, 4);
                if (dz == 1 || dz == 3) {
                    p.set(cxl, eave, z, "cv_plaster");
                }
                fillUp(cxl, eave + 1, z, vaultY(cxl));
            }
        }
    }

    /** A plaster barrel vault from wall to wall, springing at the eaves, filled solid up to the roof. */
    private void vault() {
        for (int x = xi0 - 1; x <= xi1 + 1; x++) {
            int yv = vaultY(x);
            for (int z = z0 + 1; z < z1; z++) {
                fillUp(x, yv, z, yv + 16);
            }
        }
    }

    /**
     * Plaster from y up to below the roof (or below yEnd), only where the roof is found above: a
     * column clear to the sky is left alone.
     */
    private void fillUp(int x, int y, int z, int yEnd) {
        int top = y;
        while (top < y + 20 && p.get(x, top, z) == null) {
            top++;
        }
        if (top >= y + 20) {
            return;
        }
        for (int yy = y; yy < Math.min(top, yEnd); yy++) {
            p.set(x, yy, z, "cv_plaster");
        }
    }

    /** The underside of the vault over column x. */
    private int vaultY(int x) {
        double hw = (xi1 - xi0 + 1) / 2.0, dd = Math.abs(x + 0.5 - cx) / (hw + 1);
        return eave + 1 + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - dd * dd)));
    }

    /** The organ loft over the doors: an oak floor on the front five rows, a white balustrade, the pipes and console, a stair up. */
    private void loft() {
        int y = floor + 5;
        for (int x = xi0; x <= xi1; x++) {
            for (int z = z0 + 1; z <= z0 + 4; z++) {
                p.set(x, y, z, "ch_floor_oak");
            }
            p.set(x, y + 1, z0 + 4, "cv_balustrade_white[axis=x]");
        }
        for (int x = cx - 3; x <= cx + 2; x++) {
            p.set(x, y + 1, z0 + 1, "ch_pipes[facing=south]");
            p.set(x, y + 2, z0 + 1, "ch_pipes_top[facing=south]");
            if (x == cx - 3 || x == cx + 2) {
                p.set(x, y + 3, z0 + 1, "ch_pipes_top[facing=south]");
            }
        }
        p.set(cx - 1, y + 1, z0 + 3, "ch_organ_console[facing=south]");
        p.set(cx, y + 1, z0 + 3, "pk_chair_wooden[facing=north]");
        // the stair up the side wall, arriving level with the loft through a gap in the balustrade
        for (int k = 0; k < 5; k++) {
            p.set(xi0, floor + 1 + k, z0 + 9 - k, "home_stairs_oak[facing=north]");
        }
        p.set(xi0, y + 1, z0 + 4, "air");
    }

    /**
     * The sanctuary: the marble rail and its gates across the church, steps up to the marble floor,
     * the high altar before the reredos and its pinnacles, statues and votive stands at the ends,
     * the red lamp hanging, the sacristy behind a wall.
     */
    private void sanctuary() {
        int y = floor + 1, zr = z1 - 9, zp = z1 - 7, zs = z1 - 3;
        for (int x = xi0; x <= xi1; x++) {
            p.set(x, y, zr, x == cx - 1 || x == cx ? "ch_rail_gate[axis=x]" : "ch_rail[axis=x]");
            p.set(x, y, zp - 1, "cv_step_stone[facing=south]");
            for (int z = zp; z < z1 - 1; z++) {
                p.set(x, y, z, "marble_white");
            }
        }
        // the sacristy wall behind the reredos, a door at its end
        for (int yy = y + 1; yy <= eave; yy++) {
            for (int x = xi0; x <= xi1; x++) {
                p.set(x, yy, zs, "cv_plaster");
            }
        }
        Doors.abandoned(p, rng, xi1 - 1, y + 1, zs, "home_door_oak", "south", Doors.hinge(rng));
        p.set(xi1 - 2, y + 1, zs + 1, "cellar_safe[facing=north]");
        p.set(xi1 - 2, y + 2, zs + 1, "cellar_safe_top[facing=north]");
        p.set(xi0, y + 1, zs + 1, "home_sink_white[facing=east]");
        for (int x = xi0 + 1; x <= xi0 + 4; x++) {
            p.set(x, y + 1, z1 - 2, "pk_sideboard_100[facing=north]");
        }
        // the reredos, pinnacles on it, the high altar in front
        for (int x = cx - 3; x <= cx + 2; x++) {
            for (int yy = y + 1; yy <= y + 3; yy++) {
                p.set(x, yy, zs - 1, "ch_reredos[facing=north]");
            }
            p.set(x, y + 4, zs - 1, "ch_pinnacle[facing=north]");
        }
        p.set(cx - 1, y + 5, zs - 1, "ch_pinnacle[facing=north]");
        p.set(cx, y + 5, zs - 1, "ch_pinnacle[facing=north]");
        int za = zs - 3;
        for (int x = cx - 2; x <= cx + 1; x++) {
            p.set(x, y + 1, za, "ch_altar_marble[facing=north]");
        }
        String c = vigil ? "ch_candles_lit" : "ch_candles";
        p.set(cx - 2, y + 2, za, c + "[facing=north]");
        p.set(cx + 1, y + 2, za, c + "[facing=north]");
        p.set(cx - 1, y + 2, za, "ch_altar_cross[facing=north]");
        p.set(cx, y + 2, za, c + "[facing=north]");
        p.set(cx - 4, y + 1, zs - 1, "ch_statue[facing=north]");
        p.set(cx - 4, y + 2, zs - 1, "ch_statue_top[facing=north]");
        p.set(cx + 3, y + 1, zs - 1, "ch_statue[facing=north]");
        p.set(cx + 3, y + 2, zs - 1, "ch_statue_top[facing=north]");
        hang(cx, za - 2, "ch_sanct_lamp", vigil ? 1.0 : 0.25, floor + 6);
        // the side altars' statues and their votive stands at the ends of the aisles
        for (int x : new int[] {xi0 + 1, xi1 - 1}) {
            p.set(x, y + 1, zp + 1, "ch_statue[facing=north]");
            p.set(x, y + 2, zp + 1, "ch_statue_top[facing=north]");
            p.set(x, y + 1, zp, (vigil || rng.nextDouble() < 0.1 ? "ch_votive_lit" : "ch_votive") + "[facing=north]");
        }
        p.set(cx + 3, y + 1, zp, "ch_lectern[facing=north]");
        for (int x = xi0; x <= xi1; x++) {
            for (int z = zp; z < zs; z++) {
                if (p.get(x, y + 1, z) == null && rng.nextDouble() < 0.04) {
                    p.set(x, y + 1, z, pick("paper", "glass_shards", "blood_splat"));
                }
            }
        }
    }

    /** The confessional against a side wall: a curtained bay either side of the priest's door, the cornice over all three. */
    private void confessional(int x, int z, String face) {
        p.set(x, floor + 1, z, "ch_conf_bay[facing=" + face + "]");
        p.set(x, floor + 1, z + 1, "ch_conf_door[facing=" + face + "]");
        p.set(x, floor + 1, z + 2, "ch_conf_bay[facing=" + face + "]");
        for (int k = 0; k < 3; k++) {
            p.set(x, floor + 2, z + k, "ch_conf_top[facing=" + face + "]");
        }
    }

    // ------------------------------------------------------------------ the basement hall

    /**
     * Below: the fellowship hall at the front (long tables, folding chairs, the coffee urn, the
     * notice board and the old piano), a wall across with the serving hatch, the kitchen behind it,
     * the restrooms and the boiler; cots down here too if they sheltered.
     */
    private void hall(int sx) {
        int y = base + 1, zm = z1 - 7, ya = floor - 1;
        for (int yy = y; yy <= ya; yy++) {
            for (int x = x0 + 1; x < x1; x++) {
                p.set(x, yy, zm, "cv_plaster");
            }
            for (int z = zm + 1; z < z1; z++) {
                p.set(cx, yy, z, "cv_plaster");
                p.set(cx + 4, yy, z, "cellar_wall");
            }
        }
        for (int x = x0 + 3; x <= x0 + 5; x++) {
            p.set(x, y, zm, "home_counter_oak[facing=north]");
            p.set(x, y + 1, zm, "air");
        }
        Doors.abandoned(p, rng, cx - 2, y, zm, "home_door_oak", "north", Doors.hinge(rng));
        Doors.abandoned(p, rng, cx + 2, y, zm, "home_door_oak", "north", Doors.hinge(rng));
        p.set(cx + 2, y + 2, zm - 1, "cv_plaque_restroom[facing=north]");
        Doors.abandoned(p, rng, cx + 4, y, z1 - 3, "bunk_door_steel", "east", Doors.hinge(rng));
        // the kitchen
        for (int x = x0 + 1; x < cx; x++) {
            String s = x == x0 + 2 ? "home_sink_white" : x == x0 + 4 ? "home_stove" : x == cx - 1 ? "home_fridge" : "home_counter_white";
            p.set(x, y, z1 - 1, s + "[facing=north]");
            if (s.equals("home_fridge")) {
                p.set(x, y + 1, z1 - 1, "home_fridge_top[facing=north]");
            }
        }
        p.set(x0 + 3, y + 1, z1 - 1, "home_coffee_maker[facing=north]");
        p.set(x0 + 1, y, zm + 2, "fit_urn[facing=east]");
        p.set(x0 + 1, y, zm + 1, "home_counter_white[facing=east]");
        // the restrooms and the boiler
        p.set(cx + 1, y, z1 - 1, "fit_toilet_stall[facing=north]");
        p.set(cx + 3, y, z1 - 1, "fit_toilet_stall[facing=north]");
        p.set(cx + 1, y, zm + 1, "home_sink_white[facing=east]");
        p.set(x1 - 2, y, z1 - 2, "cellar_furnace[facing=west]");
        p.set(x1 - 2, y + 1, z1 - 2, "cellar_furnace_top[facing=west]");
        // the hall: tables down its length, chairs either side, the urn and the board
        for (int tx = x0 + 3; tx <= x1 - 3; tx += 4) {
            if (Math.abs(tx - sx) <= 1) {
                continue;
            }
            for (int z = z0 + 3; z <= zm - 3; z++) {
                p.set(tx, y, z, "trade_banquet_table[facing=east]");
                if (rng.nextDouble() < 0.7) {
                    p.set(tx - 1, y, z, rng.nextDouble() < 0.12 ? "trade_folding_chair_tipped[facing=east]" : "trade_folding_chair[facing=east]");
                }
                if (rng.nextDouble() < 0.7) {
                    p.set(tx + 1, y, z, rng.nextDouble() < 0.12 ? "trade_folding_chair_tipped[facing=west]" : "trade_folding_chair[facing=west]");
                }
            }
        }
        p.set(x0 + 1, y + 1, z0 + 3, "cv_wanted[facing=east]");
        p.set(x0 + 1, y, z0 + 5, "home_piano_l[facing=east]");
        p.set(x0 + 1, y, z0 + 6, "home_piano_r[facing=east]");
        p.set(x0 + 1, y, z0 + 1, "cv_flag_us[facing=east]");
        p.set(x0 + 1, y + 1, z0 + 1, "cv_flag_us_top[facing=east]");
        if (refuge) {
            for (int i = 0; i < 6; i++) {
                int x = x0 + 2 + rng.nextInt(x1 - x0 - 3), z = z0 + 2 + rng.nextInt(Math.max(1, zm - z0 - 4));
                if (p.get(x, y, z) == null && p.get(x, y, z + 1) == null) {
                    p.set(x, y, z, "ch_cot_head[facing=north]");
                    p.set(x, y, z + 1, "ch_cot_foot[facing=north]");
                }
            }
        }
        b.lights(x0, z0, x1, z1, ya, 0.03);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y, 0.04);
    }

    // ------------------------------------------------------------------ outside

    /** The walk up from the street to the steps at zs. */
    private void path(int zs) {
        for (int z = 0; z < zs; z++) {
            p.set(cx - 1, 0, z, "sidewalk");
            p.set(cx, 0, z, "sidewalk");
        }
    }

    /** Two stone steps up from the ground to the tower doors in the wall at tz, x from a to c. */
    private void steps(int a, int c, int tz) {
        for (int x = a; x <= c; x++) {
            p.set(x, floor, tz - 1, "cv_step_stone[facing=south]");
            p.set(x, floor - 1, tz - 2, "cv_step_stone[facing=south]");
            for (int y = 0; y < floor - 1; y++) {
                p.set(x, y, tz - 1, "ch_rubble");
                p.set(x, y, tz - 2, "ch_rubble");
            }
            p.set(x, floor - 1, tz - 1, "ch_rubble");
        }
    }

    /** Iron railings along the front, a gap for the walk. */
    private void fence(int w, int gap) {
        for (int x = 0; x < w; x++) {
            if (Math.abs(x - gap + 0.5) < 2) {
                continue;
            }
            p.set(x, 1, 0, x == 0 || x == w - 1 || Math.abs(x - gap + 0.5) < 3 ? "cv_iron_post" : "cv_iron_fence[axis=x]");
        }
    }
}
