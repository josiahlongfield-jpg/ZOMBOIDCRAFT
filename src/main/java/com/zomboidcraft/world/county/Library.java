package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The town's Carnegie library, after the photos in refs/town/library: most often the Gilroy one,
 * a block of buff brick (or red, or white render) on a raised limestone basement, a portico of
 * four columns up a flight of steps carrying PVBLIC LIBRARY in the frieze and a pediment over
 * it, tall windows with stone sills and iron grilles over the basement lights, the roof hipped up
 * to a flat with a glass lantern; or, now and then, one like Bayliss: cream render, a red tile
 * roof, the entrance under its own gable with the name over the arch, a chimney at either end.
 *
 * Inside, the plan every Carnegie library had: the delivery hall straight ahead under a leaded
 * laylight, the card catalogue either side of the door and the loan desk at the back; the adult
 * reading room behind a colonnade on one side (long tables with green-shaded lamps, the walls
 * lined with bookcases under the windows), the children's room on the other round its
 * fireplace; behind the desk the closed stacks of the Providence and Library of Congress photos,
 * iron ranges two tiers high on a grated deck with a steel stair, and the librarian's office; the
 * stair down to the basement's lecture room, the periodicals store, the restrooms and the boiler.
 *
 * Drawn in its lot's frame, front along z = 0 facing north, an even number of blocks wide so the
 * doors and the portico centre on a block line. Ground at y = 0; the basement floor's slab is at
 * BASE, the main floor's at F1, the ceiling's at TOP.
 */
final class Library {
    static final int BASE = -3, F1 = 2, TOP = 9;
    private static final String WHITE = "front_trim_white";

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final boolean cottage;
    private final String wall, arch, columns, roof;

    Library(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.4 + rng.nextDouble() * 0.45;
        b.boarded = rng.nextDouble() < 0.35 ? 0.5 : 0.12;
        this.cottage = rng.nextDouble() < 0.3;
        if (cottage) {
            wall = "plaster_cream";
            arch = "red";
            columns = "white";
            roof = "red";
        } else {
            double r = rng.nextDouble();
            wall = r < 0.45 ? "brick_buff" : r < 0.75 ? "brick_red" : "plaster_cream";
            arch = wall.equals("brick_red") ? "red" : wall.equals("brick_buff") ? "buff" : "stone";
            columns = rng.nextDouble() < 0.75 ? "white" : "stone";
            double k = rng.nextDouble();
            roof = k < 0.45 ? "slate" : k < 0.75 ? "red" : "green";
        }
    }

    /** The basement inside the walls of a library built w by d, as {x0, z0, x1, z1}; its floor is at BASE. */
    static int[] cellar(int w, int d) {
        return new int[] {1, 7, w - (w & 1) - 2, d - 2};
    }

    /** A library w wide and d deep, its steps and portico in the first six rows; d at least 18. */
    void build(int w, int d) {
        w -= w & 1;
        int z0 = 6, z1 = d - 1, cx = w / 2;
        int zb = z1 - 1 - Math.max(5, Math.min(6, (z1 - z0 - 1) * 2 / 5));   // the wall behind the desk
        int px0 = cx - 4, px1 = cx + 3;
        shell(w, z0, z1);
        windows(w, z0, z1, zb, cx);
        if (cottage) {
            porch(cx, z0, px0, px1);
            hipDeck(-1, w, z0 - 1, z1 + 1, TOP + 1, 5, -1, -1, -1, -1);
            chimney(0, z0 + 4);
            chimney(w - 1, z0 + 4);
        } else {
            portico(cx, z0, px0, px1);
            hipDeck(-1, w, z0 - 1, z1 + 1, TOP + 1, 4, cx - 2, z0 + 2, cx + 1, zb - 2);
            chimney(w - 1, z0 + 4);
        }
        basement(w, z0, z1, zb, cx);
        backWall(w, zb);
        hall(w, z0, zb, cx);
        reading(z0, zb, cx);
        children(w, z0, zb, cx);
        office(z0, z1, zb, cx);
        stacks(w, z1, zb, cx);
        grounds(cx, z0, px0, px1);
    }

    // ------------------------------------------------------------------ the shell

    private void shell(int w, int z0, int z1) {
        int x1 = w - 1;
        boolean brick = wall.startsWith("brick");
        for (int y = BASE; y <= TOP; y++) {
            for (int x = 0; x <= x1; x++) {
                p.set(x, y, z0, outer(y));
                p.set(x, y, z1, outer(y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(0, y, z, outer(y));
                p.set(x1, y, z, outer(y));
            }
            if (y <= F1 || !brick) {
                continue;
            }
            // stone quoins up the corners of a brick one, long and short by turns
            int q = (y - F1) % 2 == 0 ? 2 : 1;
            for (int i = 0; i < q; i++) {
                for (int[] c : new int[][] {{i, z0}, {x1 - i, z0}, {i, z1}, {x1 - i, z1}, {0, z0 + i}, {x1, z0 + i}, {0, z1 - i}, {x1, z1 - i}}) {
                    p.set(c[0], y, c[1], "stone_trim");
                }
            }
        }
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, BASE, "floor_concrete");
        b.clear(1, BASE + 1, z0 + 1, x1 - 1, F1 - 1, z1 - 1);
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, F1, "floor_boards");
        b.clear(1, F1 + 1, z0 + 1, x1 - 1, TOP - 1, z1 - 1);
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, TOP, "cv_plaster");
        // the water table where the basement's stone meets the walls, the cornice under the eaves
        ring(w, z0, z1, F1, "cv_water_table");
        ring(w, z0, z1, TOP, cottage ? "cv_cornice_white" : "cv_cornice_" + columns);
    }

    private String outer(int y) {
        if (y < 0) {
            return y == BASE + 1 && rng.nextDouble() < 0.5 ? "cellar_wall_damp" : "cellar_wall";
        }
        return b.wall(y <= F1 ? "stone_trim" : wall, y);
    }

    /** One piece in front of every wall cell at y, all round, facing out from its wall. */
    private void ring(int w, int z0, int z1, int y, String piece) {
        int x1 = w - 1;
        for (int x = 0; x <= x1; x++) {
            p.set(x, y, z0 - 1, piece + "[facing=north]");
            p.set(x, y, z1 + 1, piece + "[facing=south]");
        }
        for (int z = z0; z <= z1; z++) {
            p.set(-1, y, z, piece + "[facing=west]");
            p.set(x1 + 1, y, z, piece + "[facing=east]");
        }
    }

    /** Which rows of the side walls have windows: every third from the front, never on a cross wall or the fireplace. */
    private static boolean sideWindow(int z, int z0, int z1, int zb) {
        return z >= z0 + 2 && z <= z1 - 2 && (z - z0 - 2) % 3 == 0 && z != zb && z != z0 + 4;
    }

    /**
     * Tall windows on the main floor (high enough for bookcases under them inside), the narrow
     * windows of the stacks two tiers high at the back, and the basement's lights at the foot of
     * the walls behind iron grilles.
     */
    private void windows(int w, int z0, int z1, int zb, int cx) {
        int x1 = w - 1;
        for (int x = 2; x <= cx - 6; x += 2) {
            for (int xx : new int[] {x, x1 - x}) {
                tall(xx, F1 + 1, z0, "north");
                cellarLight(xx, z0, "north");
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            if (!sideWindow(z, z0, z1, zb)) {
                continue;
            }
            tall(0, F1 + 1, z, "west");
            if (z < zb && !(z >= z0 + 3 && z <= z0 + 5)) {
                tall(x1, F1 + 1, z, "east");
            }
            cellarLight(0, z, "west");
            cellarLight(x1, z, "east");
        }
        // the office's window at the back, then a slit for every aisle of the stacks on both tiers
        tall(3, F1 + 1, z1, "south");
        cellarLight(3, z1, "south");
        for (int x = cx - 1; x <= x1 - 2; x += 2) {
            for (int y : new int[] {F1 + 1, F1 + 2, F1 + 4, F1 + 5}) {
                p.set(x, y, z1, b.glass("x"));
            }
            p.set(x, F1 + 3, z1, "stone_trim");
            p.set(x, F1 + 6, z1, "stone_trim");
            if ((x - cx) % 4 == 1) {
                cellarLight(x, z1, "south");
            }
        }
    }

    /** A window three panes tall under an arch (or a lintel and keystone), a stone sill under it outside. */
    private void tall(int x, int base, int z, String out) {
        String a = Bldg.along(out);
        String g = b.glass(a);
        boolean boarded = g.startsWith("boarded");
        for (int k = 1; k <= 3; k++) {
            p.set(x, base + k, z, boarded || k == 1 ? g : b.glass(a).replace("boarded_window", "window"));
        }
        int[] o = Bldg.step(out);
        if (arch.equals("stone")) {
            p.set(x, base + 4, z, "stone_trim");
            p.set(x + o[0], base + 4, z + o[1], "cv_keystone[facing=" + out + "]");
        } else {
            p.set(x, base + 4, z, "cv_arch_" + arch + "_1[facing=" + out + "]");
        }
        p.set(x + o[0], base, z + o[1], "sill[facing=" + out + "]");
    }

    /** A basement light just above the ground, an iron grille over it outside. */
    private void cellarLight(int x, int z, String out) {
        int[] o = Bldg.step(out);
        p.set(x, 1, z, b.glass(Bldg.along(out)).replace("boarded_window", "window_empty"));
        p.set(x + o[0], 1, z + o[1], rng.nextDouble() < 0.1 ? "air" : "cv_grille[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the roof

    /**
     * A roof hipped on all four sides over x0..x1, z0..z1 from y, rising `courses` courses to a
     * flat deck; the deck's cells over gx0..gx1, gz0..gz1 glazed (the lantern over the laylight).
     */
    private void hipDeck(int x0, int x1, int z0, int z1, int y, int courses, int gx0, int gz0, int gx1, int gz1) {
        String k = "ext_roof_" + roof;
        for (int i = 0; i < courses; i++) {
            int lx = x0 + i, hx = x1 - i, lz = z0 + i, hz = z1 - i;
            String piece = i == 0 ? k + "_eave" : k;
            for (int x = lx + 1; x < hx; x++) {
                p.set(x, y + i, lz, course(piece) + "[facing=south]");
                p.set(x, y + i, hz, course(piece) + "[facing=north]");
            }
            for (int z = lz + 1; z < hz; z++) {
                p.set(lx, y + i, z, course(piece) + "[facing=east]");
                p.set(hx, y + i, z, course(piece) + "[facing=west]");
            }
            p.set(lx, y + i, hz, "cv_hip_" + roof + "[facing=north]");
            p.set(lx, y + i, lz, "cv_hip_" + roof + "[facing=east]");
            p.set(hx, y + i, lz, "cv_hip_" + roof + "[facing=south]");
            p.set(hx, y + i, hz, "cv_hip_" + roof + "[facing=west]");
        }
        int top = y + courses - 1;
        for (int x = x0 + courses; x <= x1 - courses; x++) {
            for (int z = z0 + courses; z <= z1 - courses; z++) {
                boolean glass = x >= gx0 && x <= gx1 && z >= gz0 && z <= gz1;
                p.set(x, top, z, glass ? (rng.nextDouble() < b.damage * 0.25 ? "air" : "cv_skylight")
                        : rng.nextDouble() < 0.12 ? "roof_moss" : "roof");
            }
        }
        // the lantern's curb: copper round the glass where it comes up through the deck
        for (int x = gx0 - 1; x <= gx1 + 1; x++) {
            for (int z = gz0 - 1; z <= gz1 + 1; z++) {
                boolean edge = x == gx0 - 1 || x == gx1 + 1 || z == gz0 - 1 || z == gz1 + 1;
                if (edge && gx0 >= 0 && x >= x0 + courses && x <= x1 - courses && z >= z0 + courses && z <= z1 - courses) {
                    p.set(x, top + 1, z, "cv_copper_slab");
                }
            }
        }
    }

    private String course(String piece) {
        double r = rng.nextDouble();
        if (piece.endsWith("_eave")) {
            return r < 0.12 ? piece + "_bare" : piece;
        }
        return r < 0.1 ? piece + "_moss" : r < 0.15 ? piece + "_patch" : piece;
    }

    /** A chimney in the wall at x, two blocks long from z, up through the roof with a stone cap. */
    private void chimney(int x, int z) {
        for (int zz = z; zz <= z + 1; zz++) {
            for (int y = TOP + 1; y <= TOP + 5; y++) {
                p.set(x, y, zz, b.wall("brick_red", y));
            }
            p.set(x, TOP + 6, zz, "stone_trim");
        }
    }

    // ------------------------------------------------------------------ the entrance

    /** The deck at the main floor's level on its solid base, cheek walls either side, the steps down to the street. */
    private void deck(int z0, int px0, int px1) {
        int pz = z0 - 4;
        for (int x = px0 - 1; x <= px1 + 1; x++) {
            boolean cheek = x < px0 || x > px1;
            for (int z = pz - 2; z < z0; z++) {
                int top = z == pz - 2 ? F1 - 1 : F1;
                for (int y = 0; y <= top; y++) {
                    p.set(x, y, z, "stone_trim");
                }
                if (!cheek && z >= pz) {
                    p.set(x, F1, z, rng.nextDouble() < 0.1 ? "stone_trim_crack" : "pt_stone_flags");
                }
            }
        }
        for (int x = px0; x <= px1; x++) {
            p.set(x, F1, pz - 1, "cv_step_stone[facing=south]");
            p.set(x, F1 - 1, pz - 2, "cv_step_stone[facing=south]");
        }
    }

    /** The doors under a fanlight in the front wall, an EXIT sign inside over them. */
    private void doors(int cx, int z0) {
        Doors.pair(p, rng, cx - 1, F1 + 1, z0, "ext_door_oak", "south");
        p.set(cx - 1, F1 + 3, z0, b.glass("x"));
        p.set(cx, F1 + 3, z0, b.glass("x"));
        p.set(cx, F1 + 4, z0, "cv_arch_" + arch + "_l[facing=north]");
        p.set(cx - 1, F1 + 4, z0, "cv_arch_" + arch + "_r[facing=north]");
        p.set(cx, F1 + 4, z0 + 1, "cv_exit_red[facing=south]");
    }

    /** The Gilroy portico: four columns, PVBLIC LIBRARY in the frieze, the pediment with its oculus. */
    private void portico(int cx, int z0, int px0, int px1) {
        int pz = z0 - 4;
        deck(z0, px0, px1);
        for (int z = pz + 1; z < z0; z++) {
            p.set(px0, F1 + 1, z, "cv_balustrade_" + columns + "[axis=z]");
            p.set(px1, F1 + 1, z, "cv_balustrade_" + columns + "[axis=z]");
        }
        for (int j = 0; j < 2; j++) {
            for (int x : new int[] {cx - 2 - 2 * j, cx + 1 + 2 * j}) {
                p.set(x, F1 + 1, pz, "cv_column_base_" + columns);
                for (int y = F1 + 2; y < TOP - 1; y++) {
                    p.set(x, y, pz, "cv_column_" + columns);
                }
                p.set(x, TOP - 1, pz, "cv_column_cap_" + columns + "[facing=north]");
            }
        }
        // the frieze (its first tile at the viewer's left, the east end), the coffered ceiling behind it, the cornice
        int width = px1 - px0 + 1;
        String trim = columns.equals("white") ? WHITE : "stone_trim";
        for (int x = px0; x <= px1; x++) {
            p.set(x, TOP, pz, "cv_lfrieze_" + width + "_" + (px1 - x) + "[facing=north]");
            for (int z = pz + 1; z < z0; z++) {
                p.set(x, TOP, z, (x + z) % 2 == 0 ? trim : "stone_trim");
            }
            p.set(x, TOP + 1, pz - 1, "cv_cornice_" + columns + "[facing=north]");
        }
        for (int z = pz; z < z0; z++) {
            p.set(px0 - 1, TOP, z, "cv_cornice_" + columns + "[facing=west]");
            p.set(px1 + 1, TOP, z, "cv_cornice_" + columns + "[facing=east]");
        }
        int rows = width / 2;
        b.roofGable(px0, px1, pz, z0 - 2 + rows, TOP + 1, roof, true, trim, 0);
        for (int i = 0; i < rows; i++) {
            int y = TOP + 1 + i;
            for (int x = px0 + i; x <= px1 - i; x++) {
                p.set(x, y, pz, x == px0 + i ? "cv_rake_" + columns + "_l[facing=north]"
                        : x == px1 - i ? "cv_rake_" + columns + "_r[facing=north]" : trim);
            }
        }
        p.set(cx, TOP + 2, pz, "cv_oculus_white_l[facing=north]");
        p.set(cx - 1, TOP + 2, pz, "cv_oculus_white_r[facing=north]");
        doors(cx, z0);
        // someone held the door: a barricade of the reading tables, casings and blood on the flags
        for (int x = px0 + 1; x < px1; x++) {
            if (rng.nextDouble() < 0.4) {
                p.set(x, F1 + 1, pz + 2, rng.nextBoolean() ? "cv_table[axis=x]" : "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 6; i++) {
            p.setIfAir(px0 + rng.nextInt(width), F1 + 1, pz + rng.nextInt(4), rng.nextBoolean() ? "blood_splat" : "bunk_casings");
        }
    }

    /**
     * The Bayliss entrance: a bay out from the front under its own gable, open at the front
     * under the lettered band, the doors at the back of it, steps up to it.
     */
    private void porch(int cx, int z0, int px0, int px1) {
        int pz = z0 - 4;
        deck(z0, px0, px1);
        for (int z = pz; z < z0; z++) {
            for (int y = F1 + 1; y <= TOP; y++) {
                p.set(px0 - 1, y, z, b.wall(wall, y));
                p.set(px1 + 1, y, z, b.wall(wall, y));
            }
        }
        for (int x = px0; x <= px1; x++) {
            p.set(x, TOP - 1, pz, WHITE);
            p.set(x, TOP, pz, "cv_lfrieze_8_" + (px1 - x) + "[facing=north]");
            for (int z = pz + 1; z < z0; z++) {
                p.set(x, TOP, z, "floor_boards");
            }
            p.set(x, TOP - 2, pz, x == px0 || x == px1 ? WHITE : "air");
        }
        // the arch: its haunches stepped in from the piers
        p.set(px0 + 1, TOP - 2, pz, WHITE);
        p.set(px1 - 1, TOP - 2, pz, WHITE);
        for (int z = pz; z < z0; z++) {
            p.set(px0 - 2, TOP, z, "cv_cornice_white[facing=west]");
            p.set(px1 + 2, TOP, z, "cv_cornice_white[facing=east]");
        }
        for (int x = px0 - 1; x <= px1 + 1; x++) {
            p.set(x, TOP + 1, pz - 1, "cv_cornice_white[facing=north]");
        }
        b.roofGable(px0 - 1, px1 + 1, pz, z0 + 2, TOP + 1, roof, true, wall, 0);
        // white bargeboards up the gable's edges
        for (int i = 0; i < (px1 - px0 + 3) / 2; i++) {
            p.set(px0 - 1 + i, TOP + 1 + i, pz, "cv_rake_white_l[facing=north]");
            p.set(px1 + 1 - i, TOP + 1 + i, pz, "cv_rake_white_r[facing=north]");
        }
        p.set(cx, TOP + 2, pz, "cv_oculus_white_l[facing=north]");
        p.set(cx - 1, TOP + 2, pz, "cv_oculus_white_r[facing=north]");
        doors(cx, z0);
        // a bench either side inside the porch, the notice board
        p.set(px0, F1 + 1, z0 - 2, "cv_hall_bench[facing=east]");
        p.set(px1, F1 + 1, z0 - 2, "cv_hall_bench[facing=west]");
        p.set(px0, F1 + 2, z0 - 1, "ext_notice[facing=east]");
        for (int i = 0; i < 4; i++) {
            p.setIfAir(px0 + rng.nextInt(px1 - px0 + 1), F1 + 1, pz + rng.nextInt(4), rng.nextBoolean() ? "paper" : "blood_splat");
        }
    }

    /** Globe lamps on the cheeks of the steps, the flagpole, the book return by the path. */
    private void grounds(int cx, int z0, int px0, int px1) {
        int pz = z0 - 4;
        for (int x : new int[] {px0 - 1, px1 + 1}) {
            p.set(x, F1, pz - 2, "cv_lamp_post");
            p.set(x, F1 + 1, pz - 2, "cv_lamp_post");
            p.set(x, F1 + 2, pz - 2, rng.nextDouble() < 0.4 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        int fx = rng.nextBoolean() ? px0 - 4 : px1 + 4;
        p.set(fx, 1, pz - 1, "cv_flagpole_base");
        for (int y = 2; y < 13; y++) {
            p.set(fx, y, pz - 1, "cv_flagpole");
        }
        p.set(fx, 13, pz - 1, "cv_flagpole_top[facing=north]");
        p.set(fx == px0 - 4 ? px1 + 3 : px0 - 3, 1, pz - 1, rng.nextDouble() < 0.2 ? "cv_book_drop_tipped[facing=north]" : "cv_book_drop[facing=north]");
    }

    // ------------------------------------------------------------------ the main floor

    /**
     * The delivery hall: terrazzo inside a mosaic border, colonnades either side onto the reading
     * rooms, the card catalogues by the door, the loan desk at the back in front of the stacks'
     * door, the laylight overhead and globes on chains.
     */
    private void hall(int w, int z0, int zb, int cx) {
        int y = F1, hx0 = cx - 3, hx1 = cx + 2;
        for (int z = z0 + 1; z < zb; z++) {
            for (int x = hx0; x <= hx1; x++) {
                p.set(x, y, z, x == hx0 || x == hx1 || z == z0 + 1 || z == zb - 1 ? "cv_hex_border" : "cv_hex_tile");
            }
        }
        // the colonnades: a wall either side of the vestibule, then columns every third row under a beam
        for (int x : new int[] {hx0 - 1, hx1 + 1}) {
            String in = x < cx ? "east" : "west";
            for (int z = z0 + 1; z < zb; z++) {
                p.set(x, TOP - 1, z, "cv_plaster");
                if (z <= z0 + 2 || z == zb - 1) {
                    p.set(x, y + 1, z, "cv_panel_oak");
                    for (int yy = y + 2; yy < TOP - 1; yy++) {
                        p.set(x, yy, z, "cv_plaster");
                    }
                } else if ((z - z0 - 1) % 3 == 0) {
                    p.set(x, y + 1, z, "cv_column_base_white");
                    for (int yy = y + 2; yy < TOP - 2; yy++) {
                        p.set(x, yy, z, "cv_column_white");
                    }
                    p.set(x, TOP - 2, z, "cv_column_cap_white[facing=" + in + "]");
                }
            }
            p.set(x + (x < cx ? 1 : -1), y + 1, z0 + 1, "cv_catalog[facing=" + in + "]");
            p.set(x + (x < cx ? 1 : -1), y + 1, z0 + 2, "cv_catalog[facing=" + in + "]");
            if (rng.nextDouble() < 0.5) {
                p.set(x + (x < cx ? 1 : -1), y + 2, z0 + 1, "home_books_stack[facing=" + in + "]");
            }
            p.set(x + (x < cx ? 1 : -1), y + 3, z0 + 1, (x < cx ? "cv_portrait" : "cv_portrait_b") + "[facing=" + in + "]");
            p.set(x + (x < cx ? 1 : -1), y + 4, z0 + 2, (x < cx ? "cv_plaque_library" : "cv_plaque_children") + "[facing=" + in + "]");
        }
        // the laylight: leaded glass in the ceiling over the middle of the hall, oak round it
        for (int x = cx - 2; x <= cx + 1; x++) {
            for (int z = z0 + 2; z <= zb - 2; z++) {
                p.set(x, TOP, z, cottage ? "cv_panel_oak" : "cv_skylight");
            }
        }
        // the loan desk, a U open toward the stacks' door; the librarian's chair; the silence plaque
        for (int x = cx - 2; x <= cx + 1; x++) {
            p.set(x, y + 1, zb - 3, "cv_circ_desk[facing=north]");
        }
        p.set(cx - 2, y + 1, zb - 2, "cv_circ_desk[facing=west]");
        p.set(cx + 1, y + 1, zb - 2, "cv_circ_desk[facing=east]");
        p.set(cx, y + 1, zb - 2, rng.nextDouble() < 0.3 ? "home_chair_tipped[facing=north]" : "pk_chair_wooden[facing=north]");
        Doors.pair(p, rng, cx - 1, y + 1, zb, "home_door_oak", "south");
        p.set(cx - 1, y + 4, zb - 1, "cv_plaque_stacks[facing=north]");
        p.set(cx, y + 4, zb - 1, "cv_plaque_silence[facing=north]");
        p.set(hx1, y + 2, zb - 1, "bunk_clock[facing=north]");
        // globes hung on chains down either side of the laylight
        for (int z = z0 + 3; z < zb - 3; z += 4) {
            for (int x : new int[] {hx0, hx1}) {
                p.set(x, TOP - 1, z, "cv_chain");
                p.set(x, TOP - 2, z, rng.nextDouble() < 0.15 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            }
        }
        // a radiator by the door, then the floor: books dropped, glass, someone's blood
        p.set(hx0, y + 1, z0 + 3, "home_radiator[facing=east]");
        p.set(hx1, y + 1, z0 + 3, "home_radiator[facing=west]");
        books(hx0, z0 + 3, hx1, zb - 4, y + 1, 0.06);
        b.mess(hx0, z0 + 3, hx1, zb - 4, y + 1, 0.05);
    }

    /**
     * The adult reading room behind the west colonnade: parquet, bookcases round the walls (low
     * under the windows), tables across the room with a green banker's lamp in the middle of
     * each and chairs either side, a globe hung over each table.
     */
    private void reading(int z0, int zb, int cx) {
        int y = F1, x0 = 1, x1 = cx - 5;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0 + 1; z < zb; z++) {
                p.set(x, y, z, "home_floor_parquet");
            }
        }
        // round the walls: the west wall, the front wall, the back wall (but the office door)
        for (int z = z0 + 1; z < zb; z++) {
            shelf(x0, y + 1, z, "east", isGlass(x0 - 1, y + 2, z));
        }
        for (int x = x0 + 1; x <= x1; x++) {
            shelf(x, y + 1, z0 + 1, "south", isGlass(x, y + 2, z0));
            if (x != 3) {
                shelf(x, y + 1, zb - 1, "north", false);
            }
        }
        for (int z = z0 + 3; z + 1 <= zb - 2; z += 3) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                boolean mid = x == (x0 + x1) / 2;
                p.set(x, y + 1, z, mid ? (rng.nextDouble() < 0.3 ? "cv_table_lamp_lit" : "cv_table_lamp") + "[axis=x]" : "cv_table[axis=x]");
                chair(x, y + 1, z - 1, "south");
                chair(x, y + 1, z + 1, "north");
            }
            p.set((x0 + x1) / 2, TOP - 1, z, "cv_chain");
            p.set((x0 + x1) / 2, TOP - 2, z, rng.nextDouble() < 0.1 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        books(x0 + 1, z0 + 2, x1, zb - 2, y + 1, 0.08);
        b.mess(x0 + 1, z0 + 2, x1, zb - 2, y + 1, 0.04);
    }

    private boolean isGlass(int x, int y, int z) {
        String s = p.get(x, y, z);
        return s != null && (s.startsWith("window") || s.startsWith("boarded"));
    }

    /** A bookcase against a wall, one high under a window, two high otherwise; now and then pulled over. */
    private void shelf(int x, int y, int z, String facing, boolean low) {
        if (rng.nextDouble() < 0.06) {
            p.set(x, y, z, "home_books_stack[facing=" + facing + "]");
            return;
        }
        p.set(x, y, z, "home_bookcase[facing=" + facing + "]");
        if (!low) {
            p.set(x, y + 1, z, "home_bookcase_top[facing=" + facing + "]");
        }
    }

    private void chair(int x, int y, int z, String facing) {
        double r = rng.nextDouble();
        if (r < 0.12) {
            return;
        }
        p.set(x, y, z, r < 0.22 ? "home_chair_tipped[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]" : "pk_chair_wooden[facing=" + facing + "]");
    }

    /** Books pulled off the shelves onto the floor. */
    private void books(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (rng.nextDouble() < density) {
                    p.setIfAir(x, y, z, rng.nextDouble() < 0.6 ? "home_books_stack_low[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]" : "paper");
                }
            }
        }
    }

    /**
     * The children's room behind the east colonnade: low bookcases, the fireplace on the outside
     * wall with the story-hour armchair and a rug in front of it, little tables and painted chairs,
     * the globe on its stand.
     */
    private void children(int w, int z0, int zb, int cx) {
        int y = F1, x0 = cx + 4, x1 = w - 2, fz = z0 + 4;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0 + 1; z < zb; z++) {
                p.set(x, y, z, "home_floor_oak");
            }
        }
        for (int z = z0 + 1; z < zb; z++) {
            if (Math.abs(z - fz) > 1) {
                shelf(x1, y + 1, z, "west", true);
            }
        }
        for (int x = x0; x < x1; x++) {
            shelf(x, y + 1, z0 + 1, "south", true);
        }
        p.set(x1, y + 1, fz, "home_fireplace[facing=west]");
        for (int yy = y + 2; yy < TOP; yy++) {
            p.set(x1, yy, fz, "home_chimney[facing=west]");
        }
        p.set(x1, y + 2, fz - 1, "cv_portrait[facing=west]");
        for (int x = x1 - 3; x <= x1 - 1; x++) {
            for (int z = fz - 1; z <= fz + 1; z++) {
                p.set(x, y + 1, z, "cv_carpet_red");
            }
        }
        p.set(x1 - 4, y + 1, fz, rng.nextDouble() < 0.2 ? "home_chair_tipped[facing=east]" : "pk_armchair_green[facing=east]");
        p.set(x0, y + 1, z0 + 2, "cv_globe_stand");
        // the little tables toward the back, painted chairs round them
        for (int tz = fz + 3; tz + 1 < zb; tz += 3) {
            for (int x = x0 + 1; x <= x0 + 2; x++) {
                p.set(x, y + 1, tz, "cv_table[axis=x]");
                for (int s : new int[] {-1, 1}) {
                    if (rng.nextDouble() < 0.8) {
                        p.set(x, y + 1, tz + s, (rng.nextBoolean() ? "pk_chair_painted" : "pk_chair_painted_2") + "[facing=" + (s < 0 ? "south" : "north") + "]");
                    }
                }
            }
            p.set(x0 + 1, TOP - 1, tz, "cv_chain");
            p.set(x0 + 1, TOP - 2, tz, rng.nextDouble() < 0.1 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        books(x0, z0 + 2, x1 - 1, zb - 1, y + 1, 0.1);
        b.mess(x0, z0 + 2, x1 - 1, zb - 1, y + 1, 0.04);
    }

    /** The wall across behind the desk, oak panelled to the dado, plaster above. */
    private void backWall(int w, int zb) {
        for (int x = 1; x < w - 1; x++) {
            p.set(x, F1 + 1, zb, "cv_panel_oak");
            for (int yy = F1 + 2; yy < TOP; yy++) {
                p.set(x, yy, zb, "cv_plaster");
            }
        }
    }

    /** The librarian's office behind the reading room: the desk under the window, cabinets, the safe, the wall of the stairwell. */
    private void office(int z0, int z1, int zb, int cx) {
        int y = F1, x0 = 1, x1 = cx - 5, sx = cx - 3;
        for (int z = zb + 1; z < z1; z++) {
            for (int yy = y + 1; yy < TOP; yy++) {
                p.set(sx - 1, yy, z, "cv_plaster");
            }
        }
        for (int x = x0; x <= x1 + 1; x++) {
            for (int z = zb + 1; z < z1; z++) {
                p.set(x, y, z, "home_floor_carpet_green");
            }
        }
        Doors.abandoned(p, rng, 3, y + 1, zb, "home_door_oak", "south", Doors.hinge(rng));
        p.set(3, y + 4, zb - 1, "cv_plaque_librarian[facing=north]");
        p.set(3, y + 1, z1 - 2, "home_desk[facing=north]");
        p.set(3, y + 1, z1 - 1, rng.nextDouble() < 0.3 ? "office_chair_fallen[facing=west]" : "pk_chair_wooden[facing=north]");
        p.set(x0, y + 1, zb + 1, "pk_file_tall[facing=east]");
        p.set(x0, y + 2, zb + 1, "pk_file_tall_010[facing=east]");
        p.set(x0, y + 1, zb + 2, "pk_cabinet_oak[facing=east]");
        p.set(x0, y + 1, zb + 3, "cellar_safe[facing=east]");
        p.set(x0, y + 2, zb + 3, "cellar_safe_top[facing=east]");
        for (int z = zb + 1; z < z1; z++) {
            shelf(sx - 2, y + 1, z, "west", false);
        }
        p.set(x0 + 1, TOP - 1, zb + 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, zb + 1, x1, z1 - 3, y + 1, 0.08);
    }

    /**
     * The closed stacks behind the desk: iron ranges two tiers high (the upper tier on a grated
     * deck reached by a steel stair up the end aisle to the cross aisle), slit windows down each aisle, bulbs hung
     * in the aisles; the stair down to the basement along the west side behind a rail.
     */
    private void stacks(int w, int z1, int zb, int cx) {
        int y = F1, x1 = w - 2, sx = cx - 3, deck = F1 + 3;
        for (int x = sx; x <= x1; x++) {
            for (int z = zb + 1; z < z1; z++) {
                p.set(x, y, z, "cv_terrazzo");
            }
        }
        // the ranges, double-faced, running back from the cross aisle to an aisle along the back
        // wall (the way round to the stair's foot); uprights through both tiers
        for (int x = cx; x <= x1; x += 2) {
            for (int z = zb + 2; z < z1 - 1; z++) {
                for (int yy = y + 1; yy <= y + 5; yy++) {
                    p.set(x, yy, z, "cv_stack[axis=z]");
                }
            }
        }
        // the deck over the aisles and the cross aisle, the hole for the steel stair in the end aisle
        int ax = x1 - 1;
        for (int x = sx; x <= x1; x++) {
            for (int z = zb + 1; z < z1; z++) {
                if (p.get(x, deck, z) == null) {
                    p.set(x, deck, z, rng.nextDouble() < 0.04 * b.damage ? "air" : "grate");
                }
            }
        }
        // it climbs north to come out on the cross aisle's deck, where every upper aisle starts
        // (the deck open over its foot too: a head stepping onto the first tread from under the
        // deck's edge met it)
        for (int k = zb + 5 < z1 ? -1 : 0; k < 3; k++) {
            if (k >= 0) {
                p.set(ax, y + 1 + k, zb + 4 - k, "steel_stairs[facing=north]");
            }
            if (k < 2) {
                p.set(ax, deck, zb + 4 - k, "air");
            }
        }
        if (zb + 6 < z1) {
            p.set(ax, deck + 1, zb + 6, "railing[facing=north]");
        }
        // the stair down to the basement: its top in the floor at the cross aisle, the well behind a rail
        for (int k = 0; k <= F1 - BASE - 1; k++) {
            int z = zb + 1 + k;
            p.set(sx, F1 - k, z, "stairs_concrete[facing=north]");
            for (int yy = F1 - k + 1; yy <= F1 && k > 0; yy++) {
                p.set(sx, yy, z, "air");
            }
            for (int yy = BASE + 1; yy < F1 - k; yy++) {
                p.set(sx, yy, z, "cellar_wall");
            }
            if (k > 0) {
                p.set(sx + 1, y + 1, z, "cv_bar_rail[axis=z]");
            }
        }
        if (zb + F1 - BASE + 1 < z1) {
            p.set(sx, y + 1, zb + F1 - BASE + 1, "cv_bar_rail[axis=x]");
        }
        // bulbs hung in the aisles under the deck and from the ceiling, a few still burning
        for (int x = cx + 1; x <= x1; x += 4) {
            for (int z = zb + 2; z < z1; z += 2) {
                p.set(x, deck - 1, z, (rng.nextDouble() < 0.15 ? "cellar_bulb_lit" : "cellar_bulb") + "[axis=x]");
                p.set(x, TOP - 1, z, (rng.nextDouble() < 0.15 ? "cellar_bulb_lit" : "cellar_bulb") + "[axis=x]");
            }
        }
        books(sx + 1, zb + 1, x1, z1 - 1, y + 1, 0.12);
        books(sx, zb + 1, x1, z1 - 1, deck + 1, 0.08);
        if (zb + 5 < z1) {
            p.set(ax, y + 1, zb + 5, "air");
        }
        // the rolling steps left at the cross aisle's far end, clear of the doors in
        p.setIfAir(x1, y + 1, zb + 1, rng.nextDouble() < 0.5 ? "trade_stepladder[facing=south]" : "cardboard_box[facing=east]");
    }

    // ------------------------------------------------------------------ the basement

    /**
     * Down the stair: a passage at its foot; the lecture room across the front (rows of folding
     * chairs, the speaker's table and flag), the periodicals store east of it, the restrooms and
     * the boiler room at the back.
     */
    private void basement(int w, int z0, int z1, int zb, int cx) {
        int y = BASE + 1, x1 = w - 2, sx = cx - 3, px = cx + 2;
        // the cross wall under the desk's wall, the restrooms' and the boiler room's walls either side of the stair's passage
        for (int yy = y; yy < F1; yy++) {
            for (int x = 1; x <= x1; x++) {
                p.set(x, yy, zb, "cv_plaster");
            }
            for (int z = zb + 1; z < z1; z++) {
                p.set(sx - 1, yy, z, "cellar_wall");
                p.set(sx + 2, yy, z, "cellar_wall");
            }
            for (int z = z0 + 1; z < zb; z++) {
                p.set(px, yy, z, "cv_plaster");
            }
        }
        for (int z = zb + 1; z < z1; z++) {
            p.set(sx + 1, BASE, z, "floor_lino");
        }
        Doors.abandoned(p, rng, sx + 1, y, zb, "home_door_oak", "north", Doors.hinge(rng));
        p.set(sx + 1, y + 2, zb - 1, "cv_plaque_lecture[facing=north]");
        Doors.abandoned(p, rng, sx + 2, y, z1 - 2, "bunk_door_steel", "east", Doors.hinge(rng));
        Doors.abandoned(p, rng, 3, y, zb, "home_door_oak", "north", Doors.hinge(rng));
        p.set(3, y + 2, zb - 1, "cv_plaque_restroom[facing=north]");
        int dz = (z0 + zb) / 2;
        Doors.abandoned(p, rng, px, y, dz, "home_door_oak", "east", Doors.hinge(rng));
        p.set(px - 1, y + 2, dz, "cv_plaque_periodicals[facing=west]");
        lecture(1, z0 + 1, px - 1, zb - 1, y);
        periodicals(px + 1, z0 + 1, x1, zb - 1, y);
        restrooms(1, zb + 1, sx - 2, z1 - 1, y);
        boiler(sx + 3, zb + 1, x1, z1 - 1, y);
        p.set(sx + 1, F1 - 1, zb + 3, (rng.nextDouble() < 0.2 ? "bunk_tube_lit" : "bunk_tube") + "[axis=z]");
    }

    /** The lecture room: rows of folding chairs facing the speaker's table at the west end, the flag, the piano. */
    private void lecture(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, BASE, z, "floor_lino");
            }
        }
        int mz = (z0 + z1) / 2;
        for (int z = mz - 1; z <= mz + 1; z++) {
            p.set(x0 + 1, y, z, "trade_banquet_table[facing=east]");
        }
        p.set(x0, y, z0, "cv_flag_us[facing=east]");
        p.set(x0, y + 1, z0, "cv_flag_us_top[facing=east]");
        p.set(x0, y + 1, mz, "cv_portrait_b[facing=east]");
        for (int x = x0 + 3; x <= x1 - 1; x += 2) {
            for (int z = z0 + 1; z <= z1; z++) {
                if (z == mz || rng.nextDouble() < 0.15) {
                    continue;
                }
                p.set(x, y, z, rng.nextDouble() < 0.12 ? "trade_folding_chair_tipped[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]"
                        : "trade_folding_chair[facing=west]");
            }
        }
        b.lights(x0, z0, x1, z1, F1 - 1, 0.05);
        b.mess(x0 + 2, z0, x1, z1, y, 0.05);
    }

    /** The periodicals store: back numbers in ranges, bundles of newspapers tied with string, boxes. */
    private void periodicals(int x0, int z0, int x1, int z1, int y) {
        for (int z = z0 + 1; z <= z1; z += 2) {
            for (int x = x0 + 1; x <= x1; x++) {
                p.set(x, y, z, "cv_stack[axis=x]");
                p.set(x, y + 1, z, rng.nextDouble() < 0.8 ? "cv_stack[axis=x]" : "home_books_stack[facing=north]");
            }
        }
        books(x0, z0, x1, z1, y, 0.15);
        b.mess(x0, z0, x1, z1, y, 0.08);
        p.set((x0 + x1) / 2, F1 - 1, z0, rng.nextDouble() < 0.1 ? "ceiling_light" : "ceiling_light_off");
    }

    private void restrooms(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, BASE, z, "cv_hex_tile");
            }
        }
        for (int x = x0; x <= x1; x += 2) {
            p.set(x, y, z1, "fit_toilet_stall[facing=north]");
        }
        p.set(x0, y, z0, "home_sink_white[facing=east]");
        p.set(x1, y, z0, "home_sink_white[facing=west]");
        p.set((x0 + x1) / 2, F1 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0 + 1, x1, z1 - 1, y, 0.06);
    }

    /** The boiler room: the furnace and its ducts, the water heater, the fuse boxes, the janitor's bench. */
    private void boiler(int x0, int z0, int x1, int z1, int y) {
        p.set(x1, y, z0 + 1, "cellar_furnace[facing=west]");
        p.set(x1, y + 1, z0 + 1, "cellar_furnace_top[facing=west]");
        p.set(x1, y + 2, z0 + 1, "cellar_duct_up");
        for (int x = x0; x < x1; x++) {
            p.set(x, y + 2, z0 + 1, "cellar_duct[axis=x]");
        }
        p.set(x1, y, z1, "home_water_heater");
        p.set(x1, y + 1, z1, "home_water_heater_top");
        p.set(x0, y + 1, z0, "bunk_fusebox[facing=south]");
        p.set(x0 + 1, y + 1, z0, "bunk_fusebox[facing=south]");
        p.set(x0 + 2, y, z1, "home_workbench[facing=north]");
        p.set(x0 + 4, y, z1, "barrel");
        p.set(x0 + 5, y, z1, "cardboard_box[facing=north]");
        p.set((x0 + x1) / 2, F1 - 1, (z0 + z1) / 2, (rng.nextDouble() < 0.3 ? "bunk_tube_lit" : "bunk_tube") + "[axis=x]");
        b.mess(x0, z0 + 2, x1 - 1, z1 - 1, y, 0.05);
    }
}
