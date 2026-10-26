package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The county courthouse on its square, after the Kentucky courthouses in the photo library
 * (refs/town/courthouse: Warren, Scott, Hancock, Woodford): a two-storey hall of red (or buff)
 * brick raised on a limestone basement, stone quoins and belt course, tall round-arched windows,
 * a white portico of six columns (four on a narrow one) carrying the frieze with the county's
 * name and a pediment with its round window, side doors up stoops of their own, a slate roof
 * hipped up to a deck with a balustrade round it, and on the deck the clock tower: a dial on
 * each face (half the towns still have them lit amber from behind), the belfry with its bell
 * behind louvres, a copper dome and finial.
 *
 * Inside, the hall of the Cleburne and Conway photos: hexagon mosaic with its black border, grey
 * marble wainscot, cream plaster, the offices off it (clerk, circuit clerk, valuation,
 * treasurer) with their plaques, a passage across to the side doors, the records vault and the
 * restrooms at the back, and the grand stair up to the courtroom of the Hopkins and Greenbrier
 * photos: the judge's bench on its dais under the county seal between the flags, the witness
 * box, the jury box, counsel tables, the bar rail and the public's pews; the judge's chambers
 * and the jury room either side of the landing. Down in the basement, the old county jail's
 * holding cells, the boiler room, the civil-defence stores and the deed archive.
 *
 * Drawn in its lot's frame, front along z = 0 facing north, an even number of blocks wide so
 * that the doors, columns and tower centre on the line between two blocks. Ground at y = 0; the
 * basement's floor is at y = BASE, the main floor's slab at y = F1, the courtroom floor's at F2.
 */
final class Courthouse {
    static final int BASE = -3, F1 = 2, F2 = 8, TOP = 14;
    private static final String WHITE = "front_trim_white";

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final String brick, arch, columns, roof;
    private final boolean lit;

    Courthouse(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.45 + rng.nextDouble() * 0.4;
        b.boarded = rng.nextDouble() < 0.35 ? 0.5 : 0.12;
        boolean buff = rng.nextDouble() < 0.3;
        this.brick = buff ? "brick_buff" : "brick_red";
        this.arch = buff ? "buff" : "red";
        this.columns = rng.nextDouble() < 0.7 ? "white" : "stone";
        this.roof = rng.nextDouble() < 0.65 ? "slate" : "charcoal";
        this.lit = rng.nextDouble() < 0.5;
    }

    /** The basement inside the walls of a courthouse built w by d, as {x0, z0, x1, z1}; its floor is at BASE. */
    static int[] cellar(int w, int d) {
        return new int[] {1, 7, w - (w & 1) - 2, d - 2};
    }

    /** A courthouse w wide and d deep (its portico and steps in the first six rows); d at least 24. */
    void build(int w, int d) {
        w -= w & 1;
        int z0 = 6, z1 = d - 1, cx = w / 2;
        int m = w >= 28 ? 3 : 2;                 // pairs of columns
        int px0 = cx - 2 * m, px1 = cx + 2 * m - 1;
        shell(w, z0, z1);
        windows(w, z0, z1, cx);
        hipDeck(-1, w, z0 - 1, z1 + 1, TOP + 1, 2 * m);
        portico(cx, z0, px0, px1, m);
        tower(cx, (z0 + z1 + 1) / 2, TOP + 2 * m);
        basement(w, z0, z1, cx);
        groundFloor(w, z0, z1, cx);
        upperFloor(w, z0, z1, cx);
        grounds(cx, z0, px0, px1);
    }

    // ------------------------------------------------------------------ the shell

    private void shell(int w, int z0, int z1) {
        int x1 = w - 1;
        // painted block below ground, limestone up to the water table, brick for the two storeys
        for (int y = BASE; y <= TOP; y++) {
            for (int x = 0; x <= x1; x++) {
                p.set(x, y, z0, outer(y));
                p.set(x, y, z1, outer(y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(0, y, z, outer(y));
                p.set(x1, y, z, outer(y));
            }
            if (y <= F1) {
                continue;
            }
            // stone quoins at the corners, long and short by turns
            int q = (y - F1) % 2 == 0 ? 2 : 1;
            for (int i = 0; i < q; i++) {
                p.set(i, y, z0, "stone_trim");
                p.set(x1 - i, y, z0, "stone_trim");
                p.set(i, y, z1, "stone_trim");
                p.set(x1 - i, y, z1, "stone_trim");
                p.set(0, y, z0 + i, "stone_trim");
                p.set(x1, y, z0 + i, "stone_trim");
                p.set(0, y, z1 - i, "stone_trim");
                p.set(x1, y, z1 - i, "stone_trim");
            }
        }
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, BASE, "floor_concrete");
        b.clear(1, BASE + 1, z0 + 1, x1 - 1, F1 - 1, z1 - 1);
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, F1, "office_floor");
        b.clear(1, F1 + 1, z0 + 1, x1 - 1, F2 - 1, z1 - 1);
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, F2, "office_floor");
        b.clear(1, F2 + 1, z0 + 1, x1 - 1, TOP - 1, z1 - 1);
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, TOP, "concrete_dark");
        // the water table round the foot of the brick, the belt course between the storeys, the cornice under the eaves
        ring(w, z0, z1, F1, "cv_water_table");
        ring(w, z0, z1, F2, "cv_belt");
        ring(w, z0, z1, TOP, "cv_cornice_white");
    }

    private String outer(int y) {
        if (y < 0) {
            return y == BASE + 1 && rng.nextDouble() < 0.5 ? "cellar_wall_damp" : "cellar_wall";
        }
        return b.wall(y <= F1 ? "stone_trim" : brick, y);
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

    /** Where the side doors are: the passage across the main floor, two rows wide. */
    private static int passage(int z1) {
        return z1 - 10;
    }

    /** Tall round-headed windows in pairs out from the middle, both storeys; small ones low in the basement. */
    private void windows(int w, int z0, int z1, int cx) {
        int x1 = w - 1, pa = passage(z1);
        for (int base : new int[] {F1, F2}) {
            for (int k = 0; cx - 2 - 3 * k >= 3; k++) {
                for (int x : new int[] {cx - 2 - 3 * k, cx + 1 + 3 * k}) {
                    if (base == F2 || k > 0) {
                        tall(x, base, z0, "north");
                    }
                    tall(x, base, z1, "south");
                    if (k > 0 && Math.abs(x - cx + 0.5) > 2 * (w >= 28 ? 3 : 2) + 2) {
                        p.set(x, 1, z0, b.glass("x"));
                    }
                    p.set(x, 1, z1, b.glass("x"));
                }
            }
            for (int z = z0 + 3; z <= z1 - 3; z += 3) {
                if (base == F1 && z >= pa - 1 && z <= pa + 2) {
                    continue;
                }
                tall(0, base, z, "west");
                tall(x1, base, z, "east");
                if (base == F1) {
                    p.set(0, 1, z, b.glass("z"));
                    p.set(x1, 1, z, b.glass("z"));
                }
            }
        }
    }

    /** A window three panes tall under a round arch, a stone sill under it outside. */
    private void tall(int x, int base, int z, String out) {
        String a = Bldg.along(out);
        String g = b.glass(a);
        boolean boarded = g.startsWith("boarded");
        for (int k = 1; k <= 3; k++) {
            p.set(x, base + k, z, boarded || k == 1 ? g : b.glass(a).replace("boarded_window", "window"));
        }
        p.set(x, base + 4, z, "cv_arch_" + arch + "_1[facing=" + out + "]");
        int[] o = Bldg.step(out);
        p.set(x + o[0], base, z + o[1], "sill[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the roof

    /**
     * A roof hipped on all four sides over x0..x1, z0..z1 from y, rising `courses` courses to a
     * flat deck: the house kit's slate courses, the civic kit's corners, the deck in roofing felt
     * with a white balustrade round it.
     */
    private void hipDeck(int x0, int x1, int z0, int z1, int y, int courses) {
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
            // the corners rise toward the middle both ways: south-west is the kit's own turn
            p.set(lx, y + i, hz, "cv_hip_" + roof + "[facing=north]");
            p.set(lx, y + i, lz, "cv_hip_" + roof + "[facing=east]");
            p.set(hx, y + i, lz, "cv_hip_" + roof + "[facing=south]");
            p.set(hx, y + i, hz, "cv_hip_" + roof + "[facing=west]");
        }
        int dx0 = x0 + courses, dx1 = x1 - courses, dz0 = z0 + courses, dz1 = z1 - courses, top = y + courses - 1;
        for (int x = dx0; x <= dx1; x++) {
            for (int z = dz0; z <= dz1; z++) {
                p.set(x, top, z, rng.nextDouble() < 0.12 ? "roof_moss" : "roof");
            }
        }
        // the balustrade round the deck, a pier at each corner and every few blocks, a gap where it fell
        for (int x = dx0; x <= dx1; x++) {
            for (int z : new int[] {dz0, dz1}) {
                boolean pier = x == dx0 || x == dx1 || (x - dx0) % 6 == 0;
                if (pier || rng.nextDouble() > 0.06 + b.damage * 0.1) {
                    p.set(x, top + 1, z, pier ? "cv_pier_white" : "cv_balustrade_white[axis=x]");
                }
            }
        }
        for (int z = dz0 + 1; z < dz1; z++) {
            for (int x : new int[] {dx0, dx1}) {
                boolean pier = (z - dz0) % 6 == 0;
                if (pier || rng.nextDouble() > 0.06 + b.damage * 0.1) {
                    p.set(x, top + 1, z, pier ? "cv_pier_white" : "cv_balustrade_white[axis=z]");
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

    // ------------------------------------------------------------------ the portico

    /** The columns two storeys tall, the frieze, the pediment and its oculus, the steps, the doors. */
    private void portico(int cx, int z0, int px0, int px1, int m) {
        int pz = z0 - 4;
        String mat = columns;
        // the deck at the main floor's level, a stone-flagged floor on a solid base, cheek walls either side
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
        // the steps down to the square: climb toward the doors
        for (int x = px0; x <= px1; x++) {
            p.set(x, F1, pz - 1, "cv_step_stone[facing=south]");
            p.set(x, F1 - 1, pz - 2, "cv_step_stone[facing=south]");
            p.set(x, 0, pz - 3, rng.nextDouble() < 0.15 ? "sidewalk_cracked" : "sidewalk");
        }
        // balustrades along the deck's open sides
        for (int z = pz + 1; z < z0; z++) {
            p.set(px0, F1 + 1, z, "cv_balustrade_" + mat + "[axis=z]");
            p.set(px1, F1 + 1, z, "cv_balustrade_" + mat + "[axis=z]");
        }
        // the columns in pairs out from the doors
        for (int j = 0; j < m; j++) {
            for (int x : new int[] {cx - 2 - 2 * j, cx + 1 + 2 * j}) {
                p.set(x, F1 + 1, pz, "cv_column_base_" + mat);
                for (int y = F1 + 2; y < TOP - 1; y++) {
                    p.set(x, y, pz, "cv_column_" + mat);
                }
                p.set(x, TOP - 1, pz, "cv_column_cap_" + mat + "[facing=north]");
            }
        }
        // the entablature: the lettered frieze across the front (its first tile at the viewer's left,
        // the east end), the ceiling behind it, the cornice over it and returned down the sides
        int width = px1 - px0 + 1;
        for (int x = px0; x <= px1; x++) {
            p.set(x, TOP, pz, "cv_frieze_" + width + "_" + (px1 - x) + "[facing=north]");
            for (int z = pz + 1; z < z0; z++) {
                p.set(x, TOP, z, "stone_trim");
            }
            p.set(x, TOP + 1, pz - 1, "cv_cornice_white[facing=north]");
        }
        for (int z = pz; z < z0; z++) {
            p.set(px0 - 1, TOP, z, "cv_cornice_white[facing=west]");
            p.set(px1 + 1, TOP, z, "cv_cornice_white[facing=east]");
        }
        // the pediment: a gable roof run back into the hip, its front the tympanum with raking cornices and the oculus
        int rows = width / 2;
        b.roofGable(px0, px1, pz, z0 - 2 + rows, TOP + 1, roof, true, WHITE, 0);
        for (int i = 0; i < rows; i++) {
            int y = TOP + 1 + i;
            for (int x = px0 + i; x <= px1 - i; x++) {
                p.set(x, y, pz, x == px0 + i ? "cv_rake_white_l[facing=north]" : x == px1 - i ? "cv_rake_white_r[facing=north]" : WHITE);
            }
        }
        p.set(cx, TOP + 2, pz, "cv_oculus_white_l[facing=north]");
        p.set(cx - 1, TOP + 2, pz, "cv_oculus_white_r[facing=north]");
        // the double doors under a fanlight, an EXIT sign inside over them
        Doors.pair(p, rng, cx - 1, F1 + 1, z0, "ext_door_oak", "south");
        p.set(cx - 1, F1 + 3, z0, b.glass("x"));
        p.set(cx, F1 + 3, z0, b.glass("x"));
        p.set(cx, F1 + 4, z0, "cv_arch_" + arch + "_l[facing=north]");
        p.set(cx - 1, F1 + 4, z0, "cv_arch_" + arch + "_r[facing=north]");
        p.set(cx, F1 + 4, z0 + 1, "cv_exit_red[facing=south]");
        // the last stand: sandbags across the portico, casings and blood on the flags
        for (int x = px0 + 1; x < px1; x++) {
            if (rng.nextDouble() < 0.75) {
                p.set(x, F1 + 1, pz + 1, "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = px0 + rng.nextInt(width), z = pz + rng.nextInt(4);
            p.setIfAir(x, F1 + 1, z, rng.nextBoolean() ? "blood_splat" : "bunk_casings");
        }
    }

    // ------------------------------------------------------------------ the clock tower

    /** The tower six blocks square centred on (cx, zc) (a block corner), standing on the deck at y = deck. */
    private void tower(int cx, int zc, int deck) {
        int t = 6, tx = cx - 3, tz = zc - 3, base = deck + 1;
        // the stage: a stone course, five of brick with the dials, a stone band
        for (int y = base; y <= base + 6; y++) {
            for (int x = tx; x < tx + t; x++) {
                for (int z = tz; z < tz + t; z++) {
                    boolean edge = x == tx || x == tx + t - 1 || z == tz || z == tz + t - 1;
                    p.set(x, y, z, !edge ? "air" : y == base || y == base + 6 ? "stone_trim" : b.wall(brick, y));
                }
            }
        }
        // the dials, four by four on each face, tile 0 at the top left as you face it
        String dial = lit ? "cv_dial_lit_" : "cv_dial_";
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                int y = base + 5 - j;
                String tile = dial + (j * 4 + i);
                p.set(tx + 4 - i, y, tz - 1, tile + "[facing=north]");
                p.set(tx + 1 + i, y, tz + t, tile + "[facing=south]");
                p.set(tx - 1, y, tz + 1 + i, tile + "[facing=west]");
                p.set(tx + t, y, tz + 4 - i, tile + "[facing=east]");
            }
        }
        if (lit) {
            // the lamps set in the wall behind each dial's middle, amber through the glass
            for (int y = base + 3; y <= base + 4; y++) {
                for (int i = 2; i <= 3; i++) {
                    p.set(tx + i, y, tz, "minecraft:ochre_froglight");
                    p.set(tx + i, y, tz + t - 1, "minecraft:ochre_froglight");
                    p.set(tx, y, tz + i, "minecraft:ochre_froglight");
                    p.set(tx + t - 1, y, tz + i, "minecraft:ochre_froglight");
                }
            }
        }
        // the belfry: white columns at the corners, louvres between, a white lintel; boards to stand on, the bell in the middle
        int bel = base + 7;
        for (int x = tx; x < tx + t; x++) {
            for (int z = tz; z < tz + t; z++) {
                boolean edge = x == tx || x == tx + t - 1 || z == tz || z == tz + t - 1;
                if (!edge) {
                    p.set(x, bel - 1, z, "floor_boards");
                }
            }
        }
        for (int y = bel; y < bel + 4; y++) {
            for (int x = tx; x < tx + t; x++) {
                for (int z = tz; z < tz + t; z++) {
                    boolean corner = (x == tx || x == tx + t - 1) && (z == tz || z == tz + t - 1);
                    boolean edge = x == tx || x == tx + t - 1 || z == tz || z == tz + t - 1;
                    if (edge && y == bel + 3) {
                        p.set(x, y, z, WHITE);
                    } else if (corner) {
                        p.set(x, y, z, "cv_column_white");
                    } else if (edge) {
                        p.set(x, y, z, "cv_louvre[axis=" + (z == tz || z == tz + t - 1 ? "x" : "z") + "]");
                    } else {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
        p.set(cx - 1, bel, zc - 1, "cv_bell_c");
        // the cornice round the belfry's top, then the copper dome stepping in to the finial
        for (int i = 0; i < t; i++) {
            p.set(tx + i, bel + 3, tz - 1, "cv_cornice_white[facing=north]");
            p.set(tx + i, bel + 3, tz + t, "cv_cornice_white[facing=south]");
            p.set(tx - 1, bel + 3, tz + i, "cv_cornice_white[facing=west]");
            p.set(tx + t, bel + 3, tz + i, "cv_cornice_white[facing=east]");
        }
        int top = bel + 4;
        for (int s = 0; s < 2; s++) {
            int a0 = tx + s * 1, a1 = tx + t - 1 - s, c0 = tz + s, c1 = tz + t - 1 - s;
            for (int x = a0; x <= a1; x++) {
                for (int z = c0; z <= c1; z++) {
                    boolean corner = (x == a0 || x == a1) && (z == c0 || z == c1);
                    boolean edge = x == a0 || x == a1 || z == c0 || z == c1;
                    String f = z == c0 ? "south" : z == c1 ? "north" : x == a0 ? "east" : "west";
                    p.set(x, top + s, z, corner ? "cv_copper_slab" : edge ? "cv_copper_step[facing=" + f + "]" : "cv_copper");
                }
            }
        }
        for (int x = cx - 1; x <= cx; x++) {
            for (int z = zc - 1; z <= zc; z++) {
                p.set(x, top + 2, z, "cv_copper");
            }
        }
        p.set(cx - 1, top + 3, zc - 1, "cv_finial_c");
    }

    // ------------------------------------------------------------------ the basement

    /**
     * A corridor under the hall; west of it the old county jail (three holding cells behind bars,
     * the booking desk) and the boiler room with the stair down from the main floor; east, the
     * civil-defence stores from the sixties and the deed archive.
     */
    private void basement(int w, int z0, int z1, int cx) {
        int x1 = w - 1, y = BASE + 1, hx0 = cx - 2, hx1 = cx + 1;
        int zj = Math.min(z0 + 11, z1 - 12);    // the jail's and the stores' back walls are one past this
        for (int z = z0 + 1; z < z1; z++) {
            for (int yy = y; yy < F1; yy++) {
                p.set(hx0 - 1, yy, z, cellarWall(yy));
                p.set(hx1 + 1, yy, z, cellarWall(yy));
            }
            for (int x = hx0; x <= hx1; x++) {
                p.set(x, BASE, z, rng.nextDouble() < 0.04 ? "bunk_floor_drain" : b.wall("floor_concrete", 3));
            }
            // pipes along the corridor's west wall under the ceiling, a strip light every few blocks
            p.set(hx0, F1 - 1, z, (z - z0) % 7 == 3 ? "bunk_pipes_valve[facing=east]" : "bunk_pipes[facing=east]");
            if ((z - z0) % 5 == 2) {
                p.set(hx1, F1 - 1, z, (rng.nextDouble() < 0.15 ? "bunk_tube_lit" : rng.nextDouble() < 0.3 ? "bunk_tube_dangle" : "bunk_tube")
                        + "[axis=z]");
            }
        }
        for (int x = 1; x <= x1 - 1; x++) {
            if (x < hx0 - 1 || x > hx1 + 1) {
                for (int yy = y; yy < F1; yy++) {
                    p.set(x, yy, zj + 1, cellarWall(yy));
                }
            }
        }
        jail(1, z0 + 1, hx0 - 2, zj, y);
        boiler(1, zj + 2, hx0 - 2, z1 - 1, y);
        stores(hx1 + 2, z0 + 1, x1 - 1, zj, y);
        archive(hx1 + 2, zj + 2, x1 - 1, z1 - 1, y);
        Doors.abandoned(p, rng, hx0 - 1, y, z0 + 3, "bunk_door_steel", "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, hx0 - 1, y, zj + 4, "bunk_door_steel", "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, hx1 + 1, y, z0 + 3, "bunk_door_steel", "east", Doors.hinge(rng));
        Doors.abandoned(p, rng, hx1 + 1, y, zj + 4, "bunk_door_steel", "east", Doors.hinge(rng));
        // the faded shelter stencil on the corridor wall by the stores' door
        p.set(hx1, y + 2, z0 + 5, "bunk_st_shelter_l[facing=west]");
        p.set(hx1, y + 2, z0 + 6, "bunk_st_shelter_r[facing=west]");
        b.mess(hx0, z0 + 1, hx1, z1 - 1, y, 0.06);
    }

    private String cellarWall(int y) {
        return y == BASE + 1 && rng.nextDouble() < 0.35 ? "cellar_wall_damp" : "cellar_wall";
    }

    /** The holding cells along the outer wall behind bars, each with its bunk and steel toilet; the booking desk and lockers. */
    private void jail(int x0, int z0, int x1, int z1, int y) {
        int bars = x0 + 4;
        for (int c = z0; c + 2 <= z1; c += 4) {
            // a cell three wide: bunk along the back wall, the toilet in the corner, bars and the cage door
            if (c + 3 <= z1) {
                for (int x = x0; x < bars; x++) {
                    for (int yy = y; yy < F1; yy++) {
                        p.set(x, yy, c + 3, cellarWall(yy));
                    }
                }
            }
            for (int z = c; z <= c + 2; z++) {
                for (int yy = y; yy < F1; yy++) {
                    p.set(bars, yy, z, "cv_cell_bars[axis=z]");
                }
            }
            String state = rng.nextDouble() < 0.5 ? "true" : "false";
            p.set(bars, y, c + 1, "bunk_door_cage[facing=east,half=lower,hinge=left,open=" + state + "]");
            p.set(bars, y + 1, c + 1, "bunk_door_cage[facing=east,half=upper,hinge=left,open=" + state + "]");
            p.set(x0, y, c + 2, "bunk_bed_head[facing=north]");
            p.set(x0, y, c + 1, "bunk_bed_foot[facing=north]");
            p.set(x0, y, c, "fit_toilet_steel[facing=east]");
            p.set(x0, y + 2, c + 1, rng.nextDouble() < 0.25 ? "bunk_lamp_lit[facing=east]" : "bunk_lamp[facing=east]");
            if (rng.nextDouble() < 0.5) {
                p.setIfAir(x0 + 2, y, c + rng.nextInt(3), rng.nextBoolean() ? "home_blood_pool" : "paper");
            }
        }
        // the booking side: the desk facing the door, lockers for the prisoners' things, the fuse box
        p.set(x1 - 3, y, z0 + 2, "bunk_desk[facing=east]");
        p.set(x1 - 4, y, z0 + 2, "bunk_chair[facing=east]");
        p.set(x1 - 3, y, z0 + 4, "filing_cabinet[facing=east]");
        for (int z = z1 - 3; z <= z1; z++) {
            p.set(x1, y, z, "bunk_locker[facing=west]");
            p.set(x1, y + 1, z, "bunk_locker_top[facing=west]");
        }
        p.set(x1, y + 2, z0 + 6, "bunk_fusebox[facing=west]");
        p.set(x1, y + 2, z0 + 1, rng.nextDouble() < 0.6 ? "bunk_lamp_red_lit[facing=west]" : "bunk_lamp_red[facing=west]");
        for (int z = z0 + 1; z < z1; z += 4) {
            p.set((bars + x1) / 2 + 1, F1 - 1, z, (rng.nextDouble() < 0.2 ? "bunk_tube_lit" : "bunk_tube") + "[axis=z]");
        }
        b.mess(bars + 1, z0, x1, z1, y, 0.06);
    }

    /** The boiler and water heater, ducts off them under the ceiling, the stair down from the records vault in the back corner. */
    private void boiler(int x0, int z0, int x1, int z1, int y) {
        int fz = z0 + 1;
        p.set(x0, y, fz, "cellar_furnace[facing=east]");
        p.set(x0, y + 1, fz, "cellar_furnace_top[facing=east]");
        p.set(x0, y + 2, fz, "cellar_duct_up");
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            p.set(x, y + 2, fz, "cellar_duct[axis=x]");
        }
        p.set(x0, y, fz + 2, "home_water_heater");
        p.set(x0, y + 1, fz + 2, "home_water_heater_top");
        p.set(x0 + 3, y, z0, "water_tank");
        p.set(x0 + 3, y + 1, z0, "water_tank_top");
        p.set(x1, y + 1, z0 + 1, "bunk_fusebox[facing=west]");
        p.set(x1, y + 1, z0 + 2, "bunk_fusebox[facing=west]");
        p.set(x1 - 1, y, z0, "home_workbench[facing=south]");
        p.set(x1, y, z0 + 4, "barrel");
        p.set(x1, y, z0 + 5, "pk_barrel_2");
        p.set((x0 + x1) / 2, F1 - 1, (z0 + z1) / 2, (rng.nextDouble() < 0.3 ? "bunk_tube_lit" : "bunk_tube") + "[axis=x]");
        // the stair from the vault comes down along the back wall, x0 + 1 (the foot) to x0 + 5
        for (int k = 0; k < 5; k++) {
            for (int z = z1 - 1; z <= z1; z++) {
                p.set(x0 + 1 + k, y + k, z, "stairs_concrete[facing=east]");
                for (int h = 1; h <= 3 && y + k + h < F1; h++) {
                    p.set(x0 + 1 + k, y + k + h, z, "air");
                }
                if (y + k + 1 <= F1) {
                    for (int yy = y; yy < y + k; yy++) {
                        p.set(x0 + 1 + k, yy, z, "cellar_wall");
                    }
                }
            }
        }
        b.mess(x0 + 1, z0, x1, z1 - 2, y, 0.05);
    }

    /** The civil-defence stores: racks of boxes, crates and drums from 1962, water drums against the wall. */
    private void stores(int x0, int z0, int x1, int z1, int y) {
        for (int z = z0; z <= z1; z++) {
            p.set(x1, y, z, (z % 3 == 0 ? "bunk_rack_drums" : z % 3 == 1 ? "bunk_rack_crates" : "bunk_rack_boxes") + "[facing=west]");
        }
        for (int x = x0 + 3; x <= x1 - 3; x += 3) {
            for (int z = z0 + 1; z <= z1 - 2; z++) {
                p.set(x, y, z, (rng.nextDouble() < 0.5 ? "bunk_rack_boxes" : "bunk_rack_crates") + "[facing=" + (x % 2 == 0 ? "east" : "west") + "]");
            }
        }
        for (int x = x0 + 1; x <= x1 - 1; x += 2) {
            p.set(x, y, z1, rng.nextDouble() < 0.7 ? "bunk_drum_water" : "bunk_crate_ration[facing=north]");
        }
        for (int i = 0; i < 6; i++) {
            p.setIfAir(x0 + 1 + rng.nextInt(Math.max(1, x1 - x0 - 1)), y, z0 + rng.nextInt(z1 - z0), "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        for (int z = z0 + 2; z < z1; z += 5) {
            p.set((x0 + x1) / 2, F1 - 1, z, (rng.nextDouble() < 0.15 ? "bunk_tube_lit" : "bunk_tube") + "[axis=z]");
        }
    }

    /** The deed archive: rows of deed books and plat cabinets with an aisle down the corridor side. */
    private void archive(int x0, int z0, int x1, int z1, int y) {
        for (int z = z0 + 1; z <= z1; z += 3) {
            for (int x = x0 + 2; x <= x1; x++) {
                String what = rng.nextDouble() < 0.55 ? "home_bookcase" : "filing_cabinet";
                if (rng.nextDouble() < 0.9) {
                    p.set(x, y, z, what + "[facing=north]");
                    if (what.equals("home_bookcase")) {
                        p.set(x, y + 1, z, "home_bookcase_top[facing=north]");
                    }
                }
            }
        }
        p.set(x0, y, z1, "bunk_desk[facing=north]");
        for (int z = z0; z <= z1; z += 5) {
            p.set((x0 + x1) / 2, F1 - 1, z, (rng.nextDouble() < 0.15 ? "bunk_tube_lit" : "bunk_tube") + "[axis=x]");
        }
        b.mess(x0, z0, x1, z1, y, 0.08);
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};

    // ------------------------------------------------------------------ the main floor

    private void groundFloor(int w, int z0, int z1, int cx) {
        int x1 = w - 1, y = F1;
        int hx0 = cx - 2, hx1 = cx + 1;        // the hall
        int zs = z1 - 7;                        // the foot of the grand stair, the back rooms' front wall one row before it
        int pa = passage(z1);                   // the passage across to the side doors: pa, pa + 1
        // the hall's mosaic with its border, the walls either side wainscoted in marble
        for (int z = z0 + 1; z < zs; z++) {
            for (int x = hx0; x <= hx1; x++) {
                p.set(x, y, z, x == hx0 || x == hx1 || z == z0 + 1 ? "cv_hex_border" : "cv_hex_tile");
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            if (z == pa || z == pa + 1) {
                continue;
            }
            for (int x : new int[] {hx0 - 1, hx1 + 1}) {
                p.set(x, y + 1, z, z < zs ? "cv_wainscot" : "cv_plaster");
                for (int yy = y + 2; yy < F2; yy++) {
                    p.set(x, yy, z, "cv_plaster");
                }
            }
        }
        // the passage across: terrazzo, the side doors at its ends up their stoops
        for (int x = 1; x < x1; x++) {
            if (x < hx0 || x > hx1) {
                p.set(x, y, pa, "cv_terrazzo");
                p.set(x, y, pa + 1, "cv_terrazzo");
            }
            for (int yy = y + 1; yy < F2; yy++) {
                if (x < hx0 - 1 || x > hx1 + 1) {
                    p.set(x, yy, pa - 1, "cv_plaster");
                    p.set(x, yy, pa + 2, "cv_plaster");
                }
            }
        }
        for (int s = 0; s < 2; s++) {
            int wx = s == 0 ? 0 : x1, o = s == 0 ? -1 : 1;
            String in = s == 0 ? "east" : "west";
            Doors.abandoned(p, rng, wx, y + 1, pa, "ext_door_oak", in, s == 0 ? "left" : "right");
            Doors.abandoned(p, rng, wx, y + 1, pa + 1, "ext_door_oak", in, s == 0 ? "right" : "left");
            p.set(wx, y + 3, pa, b.glass("z"));
            p.set(wx, y + 3, pa + 1, b.glass("z"));
            // the stoop: a landing at the floor's height, a step down, the ground
            for (int z = pa - 1; z <= pa + 2; z++) {
                boolean cheek = z == pa - 1 || z == pa + 2;
                for (int yy = 0; yy <= F1; yy++) {
                    p.set(wx + o, yy, z, "stone_trim");
                }
                p.set(wx + 2 * o, 0, z, "stone_trim");
                p.set(wx + 2 * o, 1, z, cheek ? "stone_trim" : "cv_step_stone[facing=" + in + "]");
                if (cheek) {
                    p.set(wx + o, F1 + 1, z, "cv_pier_" + columns);
                }
            }
            // a little pediment over the side doors
            for (int z = pa - 1; z <= pa + 2; z++) {
                p.set(wx + o, y + 4, z, "cv_cornice_white[facing=" + (s == 0 ? "west" : "east") + "]");
            }
        }
        // the offices either side of the hall, a cross wall between each
        String[][] sides = {{"clerk", "pva", "records"}, {"circuit", "treasurer", "sheriff"}};
        int za = z0 + 1, zb = pa - 2, rows = Math.max(1, Math.min(3, (zb - za + 2) / 8));
        int depth = (zb - za + 1 - (rows - 1)) / rows;
        for (int s = 0; s < 2; s++) {
            int ax = s == 0 ? 1 : hx1 + 2, bx = s == 0 ? hx0 - 2 : x1 - 1;
            int wallX = s == 0 ? hx0 - 1 : hx1 + 1;
            String into = s == 0 ? "west" : "east";
            for (int r = 0; r < rows; r++) {
                int ra = za + r * (depth + 1), rb = r == rows - 1 ? zb : ra + depth - 1;
                if (r < rows - 1) {
                    for (int x = ax; x <= bx; x++) {
                        for (int yy = y + 1; yy < F2; yy++) {
                            p.set(x, yy, rb + 1, "cv_plaster");
                        }
                    }
                }
                String kind = sides[s][r];
                int dz = ra + 1;
                Doors.abandoned(p, rng, wallX, y + 1, dz, "home_door_oak", into, Doors.hinge(rng));
                p.set(wallX + (s == 0 ? 1 : -1), y + 4, dz, "cv_plaque_" + kind + "[facing=" + (s == 0 ? "east" : "west") + "]");
                office(kind, ax, ra, bx, rb, y, s == 0);
            }
        }
        // the back rooms off the passage: the records vault (and the way down) west, the restrooms east
        for (int x = 1; x < x1; x++) {
            if (x < hx0 - 1 || x > hx1 + 1) {
                for (int yy = y + 1; yy < F2; yy++) {
                    p.set(x, yy, zs - 1, "cv_plaster");
                }
            }
        }
        int vd = hx0 - 4, rd = hx1 + 4;
        Doors.abandoned(p, rng, vd, y + 1, zs - 1, "home_door_oak", "north", Doors.hinge(rng));
        p.set(vd, y + 4, zs - 2, "cv_plaque_records[facing=north]");
        Doors.abandoned(p, rng, rd, y + 1, zs - 1, "home_door_oak", "north", Doors.hinge(rng));
        p.set(rd, y + 4, zs - 2, "cv_plaque_restroom[facing=north]");
        vault(1, zs, hx0 - 2, z1 - 1, y);
        restrooms(hx1 + 2, zs, x1 - 1, z1 - 1, y);
        // the grand stair: carpet up the middle of the hall to the courtroom floor, panelled stringers and a rail either side
        for (int k = 0; k <= F2 - F1 - 1; k++) {
            int z = zs + k;
            for (int x = cx - 1; x <= cx; x++) {
                p.set(x, y + 1 + k, z, "home_stairs_carpet[facing=south]");
                for (int yy = y + 2 + k; yy <= y + 4 + k && yy < TOP; yy++) {
                    p.set(x, yy, z, "air");
                }
            }
            for (int x : new int[] {hx0, hx1}) {
                for (int yy = y + 1; yy <= y + k; yy++) {
                    p.set(x, yy, z, "cv_panel_oak");
                }
                p.set(x, y + 1 + k, z, "cv_bar_rail[axis=z]");
            }
        }
        // the hall's lights hung on chains, the benches, the directory by the door, portraits, the flags at the stair foot
        for (int z = z0 + 3; z < zs - 1; z += 6) {
            for (int x = cx - 1; x <= cx; x++) {
                p.set(x, F2 - 1, z, "cv_chain");
                p.set(x, F2 - 2, z, rng.nextDouble() < 0.12 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            }
        }
        p.set(hx1, y + 2, z0 + 4, "cv_directory[facing=west]");
        for (int z = z0 + 5; z < pa - 1; z += 6) {
            p.set(hx0, y + 1, z, "cv_hall_bench[facing=east]");
            p.set(hx0, y + 1, z + 1, "cv_hall_bench[facing=east]");
            p.set(hx1, y + 1, z + 2, "cv_hall_bench[facing=west]");
            p.set(hx0, y + 2, z + 3, "cv_portrait[facing=east]");
            p.set(hx1, y + 2, z, "cv_portrait_b[facing=west]");
        }
        p.set(hx0, y + 1, zs - 1, "cv_flag_us[facing=east]");
        p.set(hx0, y + 2, zs - 1, "cv_flag_us_top[facing=east]");
        p.set(hx1, y + 1, zs - 1, "cv_flag_state[facing=west]");
        p.set(hx1, y + 2, zs - 1, "cv_flag_state_top[facing=west]");
        p.set(hx0 - 2, F2 - 1, pa, rng.nextDouble() < 0.08 ? "ceiling_light" : "ceiling_light_off");
        p.set(hx1 + 2, F2 - 1, pa, rng.nextDouble() < 0.08 ? "ceiling_light" : "ceiling_light_off");
        b.mess(hx0, z0 + 1, hx1, zs - 1, y + 1, 0.08);
        b.mess(1, pa, x1 - 1, pa + 1, y + 1, 0.06);
    }

    /** One office off the hall: counters, desks, cabinets and paper, by what the office was. */
    private void office(String kind, int x0, int z0, int x1, int z1, int y, boolean west) {
        int door = west ? x1 : x0;          // the side the hall door is on
        int far = west ? x0 : x1;
        String toHall = west ? "east" : "west", fromHall = west ? "west" : "east";
        int step = west ? -1 : 1;
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        switch (kind) {
            case "clerk", "circuit", "treasurer", "sheriff" -> {
                // the public counter two in from the door, a gap at one end; desks and cabinets behind
                int cxl = door + step * 2;
                for (int z = z0; z < z1; z++) {
                    p.set(cxl, y + 1, z, "store_counter[facing=" + toHall + "]");
                }
                for (int z = z0; z <= z1; z += 2) {
                    int dx = cxl + step * 2;
                    if (dx * step < far * step) {
                        p.set(dx, y + 1, z, "desk[facing=" + toHall + "]");
                        p.set(dx + step, y + 1, z, "office_chair[facing=" + toHall + "]");
                    }
                }
                for (int z = z0; z <= z1; z++) {
                    if (rng.nextDouble() < 0.7) {
                        p.set(far, y + 1, z, (kind.equals("clerk") && rng.nextBoolean() ? "home_bookcase" : "filing_cabinet")
                                + "[facing=" + toHall + "]");
                    }
                }
                if (kind.equals("treasurer")) {
                    p.set(far, y + 1, z1, "cellar_safe[facing=" + toHall + "]");
                    p.set(far, y + 2, z1, "cellar_safe_top[facing=" + toHall + "]");
                } else if (kind.equals("sheriff")) {
                    p.set(far, y + 1, z0, "home_gun_cabinet[facing=" + toHall + "]");
                    p.set(far, y + 2, z0, "home_gun_cabinet_top[facing=" + toHall + "]");
                }
            }
            default -> {
                // rows of deed books and plat cabinets, an aisle along the door wall
                for (int x = door + step * 2; x * step < far * step; x += step * 2) {
                    for (int z = z0; z < z1; z++) {
                        if (rng.nextDouble() < 0.85) {
                            p.set(x, y + 1, z, (kind.equals("records") ? "home_bookcase" : "filing_cabinet") + "[facing=" + fromHall + "]");
                        }
                    }
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = Math.min(x0, x1) + rng.nextInt(Math.abs(x1 - x0) + 1), z = z0 + rng.nextInt(z1 - z0 + 1);
            p.setIfAir(x, y + 1, z, rng.nextDouble() < 0.7 ? "paper" : "rubble_pile");
        }
    }

    /** The records vault: the old safe, deed books round the walls, the stair down to the boiler room against the back wall. */
    private void vault(int x0, int z0, int x1, int z1, int y) {
        p.set(x0, y + 1, z0, "cellar_safe[facing=east]");
        p.set(x0, y + 2, z0, "cellar_safe_top[facing=east]");
        for (int z = z0 + 1; z <= z1 - 3; z++) {
            p.set(x0, y + 1, z, "home_bookcase[facing=east]");
            p.set(x0, y + 2, z, "home_bookcase_top[facing=east]");
        }
        for (int x = x0 + 7; x <= x1; x++) {
            p.set(x, y + 1, z1, "filing_cabinet[facing=north]");
        }
        // the well of the stair down (its top step at x0 + 5 in the floor), a rail round it
        for (int x = x0 + 2; x <= x0 + 4; x++) {
            for (int z = z1 - 1; z <= z1; z++) {
                p.set(x, y, z, "air");
            }
            p.set(x, y + 1, z1 - 2, "railing[facing=north]");
        }
        p.set(x0 + 1, y + 1, z1 - 1, "railing[facing=west]");
        p.set(x0 + 1, y + 1, z1, "railing[facing=west]");
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0 + 1, z0, x1, z1 - 3, y + 1, 0.1);
    }

    /** The restrooms: toilets along the back wall, basins along the front, mosaic on the floor. */
    private void restrooms(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, y, z, "cv_hex_tile");
            }
        }
        for (int x = x0 + 1; x <= x1; x += 2) {
            p.set(x, y + 1, z1, "fit_toilet_stall[facing=north]");
        }
        for (int x = x0 + 4; x <= x1 - 1; x += 2) {
            p.set(x, y + 1, z0, "home_sink_white[facing=south]");
        }
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0 + 1, x1, z1 - 1, y + 1, 0.06);
    }

    // ------------------------------------------------------------------ the courtroom floor

    private void upperFloor(int w, int z0, int z1, int cx) {
        int x1 = w - 1, y = F2;
        int zs = z1 - 7, back = zs - 1;          // the courtroom's back wall, the landing behind it
        int hx0 = cx - 2, hx1 = cx + 1;
        // the courtroom: oak floor, the back wall panelled to waist height, plaster above
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 1; z < back; z++) {
                p.set(x, y, z, "home_floor_oak");
            }
            for (int yy = y + 1; yy < TOP; yy++) {
                p.set(x, yy, back, yy == y + 1 ? "cv_panel_oak" : "cv_plaster");
            }
        }
        // two doors in from the landing, either side of the stairwell
        for (int x : new int[] {hx0 - 1, hx1 + 1}) {
            Doors.abandoned(p, rng, x, y + 1, back, "home_door_oak", "north", Doors.hinge(rng));
            p.set(x, y + 3, back - 1, "cv_exit_red[facing=north]");
        }
        // the dais and the bench under the seal between the flags; the judge faces the room
        for (int x = cx - 4; x <= cx + 3; x++) {
            for (int z = z0 + 1; z <= z0 + 3; z++) {
                p.set(x, y + 1, z, "home_floor_oak");
            }
            boolean end = x == cx - 4 || x == cx + 3;
            p.set(x, y + 2, z0 + 3, end ? "cv_panel_oak" : "cv_judge_bench[facing=south]");
            p.set(x, y + 3, z0 + 3, end ? "air" : "cv_judge_bench_top[facing=south]");
        }
        p.set(cx - 1, y + 2, z0 + 2, "office_chair[facing=south]");
        p.set(cx, y + 5, z0 + 1, "cv_seal_c[facing=south]");
        p.set(cx - 3, y + 3, z0 + 1, "cv_portrait[facing=south]");
        p.set(cx + 2, y + 3, z0 + 1, "cv_portrait_b[facing=south]");
        p.set(cx - 4, y + 2, z0 + 1, "cv_flag_us[facing=south]");
        p.set(cx - 4, y + 3, z0 + 1, "cv_flag_us_top[facing=south]");
        p.set(cx + 3, y + 2, z0 + 1, "cv_flag_state[facing=south]");
        p.set(cx + 3, y + 3, z0 + 1, "cv_flag_state_top[facing=south]");
        // the witness box to the judge's right, raised a step behind its rail; the clerk's desk to the left
        for (int x = cx - 7; x <= cx - 5; x++) {
            for (int z = z0 + 2; z <= z0 + 4; z++) {
                p.set(x, y + 1, z, "home_floor_oak");
            }
            p.set(x, y + 2, z0 + 4, "cv_bar_rail[axis=x]");
        }
        p.set(cx - 7, y + 2, z0 + 3, "cv_bar_rail[axis=z]");
        p.set(cx - 6, y + 2, z0 + 3, "office_chair[facing=south]");
        p.set(cx + 5, y + 1, z0 + 4, "desk[facing=south]");
        p.set(cx + 5, y + 1, z0 + 3, "office_chair[facing=south]");
        // the jury box along the east wall: two rows of chairs, the back row raised, behind a rail
        int jx = x1 - 4;
        for (int z = z0 + 5; z <= z0 + 9; z++) {
            p.set(jx, y + 1, z, "cv_bar_rail[axis=z]");
            p.set(jx + 1, y + 1, z, "office_chair[facing=west]");
            p.set(jx + 2, y + 1, z, "home_floor_oak");
            p.set(jx + 2, y + 2, z, "office_chair[facing=west]");
            p.set(jx + 3, y + 1, z, "home_floor_oak");
        }
        for (int x = jx; x < x1; x++) {
            p.set(x, y + 1, z0 + 4, "cv_bar_rail[axis=x]");
            p.set(x, y + 1, z0 + 10, "cv_bar_rail[axis=x]");
        }
        // counsel tables facing the bench
        for (int x : new int[] {cx - 6, cx - 5, cx + 4, cx + 5}) {
            p.set(x, y + 1, z0 + 7, "desk[facing=north]");
            p.set(x, y + 1, z0 + 8, "office_chair[facing=north]");
        }
        // the bar across the room, its gate in the middle
        int bar = z0 + 10;
        for (int x = 1; x < jx; x++) {
            if (x != cx - 1 && x != cx) {
                p.set(x, y + 1, bar, x == cx - 2 || x == cx + 1 ? "cv_bar_post" : "cv_bar_rail[axis=x]");
            }
        }
        // the public's pews, an aisle of red carpet down the middle
        for (int z = bar + 2; z <= back - 2; z += 2) {
            for (int x = 2; x <= cx - 2; x++) {
                String pew = x == 2 ? "cv_pew_end_l" : x == cx - 2 ? "cv_pew_end" : "cv_pew";
                p.set(x, y + 1, z, rng.nextDouble() < 0.06 ? "rubble_pile" : pew + "[facing=north]");
            }
            for (int x = cx + 1; x <= x1 - 2; x++) {
                String pew = x == cx + 1 ? "cv_pew_end_l" : x == x1 - 2 ? "cv_pew_end" : "cv_pew";
                p.set(x, y + 1, z, rng.nextDouble() < 0.06 ? "rubble_pile" : pew + "[facing=north]");
            }
        }
        for (int z = bar + 1; z < back; z++) {
            for (int x = cx - 1; x <= cx; x++) {
                p.set(x, y + 1, z, "cv_carpet_red");
            }
        }
        // the lights hung down from the high ceiling on chains, globes mostly dark
        for (int x = cx - 8; x <= cx + 7; x += 5) {
            for (int z = z0 + 5; z < back - 2; z += 6) {
                p.set(x, TOP - 1, z, "cv_chain");
                p.set(x, TOP - 2, z, rng.nextDouble() < 0.1 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            }
        }
        // the landing at the stair head; the judge's chambers to the west of it, the jury room east
        for (int x = 1; x < x1; x++) {
            for (int z = back + 1; z < z1; z++) {
                if (p.get(x, y, z) != null) {
                    p.set(x, y, z, x >= hx0 - 1 && x <= hx1 + 1 ? "cv_terrazzo" : "home_floor_carpet_green");
                }
            }
        }
        for (int z = back + 1; z < z1; z++) {
            for (int yy = y + 1; yy < TOP; yy++) {
                p.set(hx0 - 2, yy, z, "cv_plaster");
                p.set(hx1 + 2, yy, z, "cv_plaster");
            }
        }
        for (int x = cx - 1; x <= cx; x++) {
            for (int z = zs; z < zs + F2 - F1 - 1; z++) {
                p.set(x, F2, z, "air");
            }
        }
        for (int z = zs; z < zs + F2 - F1 - 1; z++) {
            p.set(hx0, y + 1, z, "cv_bar_rail[axis=z]");
            p.set(hx1, y + 1, z, "cv_bar_rail[axis=z]");
        }
        Doors.abandoned(p, rng, hx0 - 2, y + 1, z1 - 2, "home_door_oak", "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, hx1 + 2, y + 1, z1 - 2, "home_door_oak", "east", Doors.hinge(rng));
        p.set(hx0 - 1, y + 4, z1 - 2, "cv_plaque_judge[facing=east]");
        p.set(hx1 + 1, y + 4, z1 - 2, "cv_plaque_jury[facing=west]");
        p.set(hx0 - 1, y + 4, back + 1, "cv_plaque_court[facing=south]");
        p.set(hx1 + 1, y + 4, back + 1, "cv_plaque_court[facing=south]");
        // chambers: the judge's desk, bookcases of reports, a leather armchair
        p.set(4, y + 1, z1 - 3, "desk[facing=east]");
        p.set(3, y + 1, z1 - 3, "office_chair[facing=east]");
        p.set(hx0 - 4, y + 1, back + 1, "pk_armchair[facing=south]");
        for (int z = back + 1; z < z1; z++) {
            p.set(1, y + 1, z, "home_bookcase[facing=east]");
            p.set(1, y + 2, z, "home_bookcase_top[facing=east]");
        }
        // the jury room: a long table and its chairs, the water cooler
        for (int x = hx1 + 4; x < x1 - 2; x++) {
            p.set(x, y + 1, z1 - 3, "desk[facing=north]");
            p.set(x, y + 1, z1 - 4, "office_chair[facing=south]");
            p.set(x, y + 1, z1 - 2, "office_chair[facing=north]");
        }
        p.set(x1 - 1, y + 1, back + 1, "trade_water_cooler[facing=west]");
        b.lights(1, back, x1 - 1, z1, TOP - 1, 0.03);
        b.mess(1, z0 + 1, x1 - 1, z1 - 1, y + 1, 0.05);
    }

    // ------------------------------------------------------------------ the grounds

    /** Globe lamps on the cheeks of the steps, the flagpole on the lawn. */
    private void grounds(int cx, int z0, int px0, int px1) {
        int pz = z0 - 4;
        for (int x : new int[] {px0 - 1, px1 + 1}) {
            p.set(x, F1, pz - 2, "cv_lamp_post");
            p.set(x, F1 + 1, pz - 2, "cv_lamp_post");
            p.set(x, F1 + 2, pz - 2, rng.nextDouble() < 0.4 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        int fx = px0 - 5;
        p.set(fx, 1, pz - 3, "cv_flagpole_base");
        for (int y = 2; y < 15; y++) {
            p.set(fx, y, pz - 3, "cv_flagpole");
        }
        p.set(fx, 15, pz - 3, "cv_flagpole_top[facing=north]");
    }
}
