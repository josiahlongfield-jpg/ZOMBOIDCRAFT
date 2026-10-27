package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The town hall facing the square. There are no town hall photos in the library yet, so this
 * follows what the small Midwestern city halls of the 1890s are known for: Romanesque, rock-faced
 * stone up to the floor, smooth stone for the ground storey with CITY HALL cut in a band over the
 * round-arched door (the doors set back in the arch, lamps either side), red (or buff, or dark)
 * brick above with stone quoins, round-headed windows, a little brick gable over the entrance bay
 * with the date stone in it, a steep slate hip with iron cresting round its deck and two chimneys;
 * and at the front corner the clock tower standing a block proud of the front: a dial on each
 * face over the roof (lit amber from behind in half the towns), the louvred belfry under round
 * arches with the bell in it, a slate pyramid and a copper finial.
 *
 * Inside, the hall of terrazzo up the middle to the stair, the offices off it behind counter
 * windows with their grilles (city clerk, tax collector, water department), the mayor's office in
 * the tower corner with its fireplace and flags, the records vault behind the clerk, restrooms;
 * notices, the honour roll, past mayors on the walls. Upstairs the council chamber: the bench on
 * its dais under the town seal, the clerk's and the press tables, the podium for the public, the
 * bar, the town map with the red line round the quarantine; on the last election day the
 * chamber was the polling place (booths, the ballot box), or in more than half the towns it
 * became the emergency centre (map table, radios, cots, red lamps, sandbags at the door). Behind
 * it the committee room and the civil-defence office. Pressed-tin ceilings throughout.
 *
 * Drawn in its lot's frame, front along z = 0 facing north, an even number of blocks wide so the
 * door centres on a block line; at least 20 wide and 24 deep. Ground at y = 0, the main floor's
 * slab at F1, the chamber floor's at F2, the ceiling at TOP.
 */
final class TownHall {
    static final int F1 = 2, F2 = 8, TOP = 14, Z0 = 4;
    /** The clock tower's width, at the front west corner. */
    private static final int T = 6;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final String brick, arch, roof;
    private final boolean lit, eoc;

    TownHall(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.3 + rng.nextDouble() * 0.35;
        b.boarded = rng.nextDouble() < 0.35 ? 0.5 : 0.12;
        double r = rng.nextDouble();
        this.brick = r < 0.55 ? "brick_red" : r < 0.8 ? "brick_dark" : "brick_buff";
        this.arch = brick.equals("brick_buff") ? "buff" : "red";
        this.roof = rng.nextDouble() < 0.7 ? "slate" : "charcoal";
        this.lit = rng.nextDouble() < 0.5;
        this.eoc = rng.nextDouble() < 0.55;
    }

    /** Where a fallout shelter may be dug under a town hall w by d: inside its walls, as {x0, z0, x1, z1}; its floor is at F1. */
    static int[] host(int w, int d) {
        return new int[] {1, Z0 + 1, w - (w & 1) - 2, d - 2};
    }

    /** A town hall w wide and d deep (its steps in the first four rows); w at least 20, d at least 24. */
    void build(int w, int d) {
        w -= w & 1;
        int z0 = Z0, z1 = d - 1, cx = w / 2;
        shell(w, z0, z1);
        windows(w, z0, z1, cx);
        roof(w, z0, z1, cx);
        tower(z0 - 1);
        entrance(cx, z0);
        groundFloor(w, z0, z1, cx);
        upperFloor(w, z0, z1, cx);
        grounds(w, cx, z0);
    }

    // ------------------------------------------------------------------ the shell

    /** The first row inside the front wall at x: the tower's corner reaches a row further forward. */
    private static int front(int x, int z0) {
        return x >= 1 && x <= T - 2 ? z0 : z0 + 1;
    }

    private void shell(int w, int z0, int z1) {
        int x1 = w - 1, tz = z0 - 1;
        for (int y = 0; y <= TOP; y++) {
            for (int x = 0; x <= x1; x++) {
                p.set(x, y, x < T ? tz : z0, outer(y));
                p.set(x, y, z1, outer(y));
            }
            p.set(T - 1, y, z0, outer(y));
            for (int z = tz + 1; z < z1; z++) {
                p.set(0, y, z, outer(y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x1, y, z, outer(y));
            }
            if (y > F2) {
                // stone quoins at the brick's corners, long and short by turns
                int q = (y - F2) % 2 == 0 ? 2 : 1;
                for (int i = 0; i < q; i++) {
                    p.set(i, y, tz, "stone_trim");
                    p.set(0, y, tz + i, "stone_trim");
                    p.set(T - 1 - i, y, tz, "stone_trim");
                    p.set(x1 - i, y, z0, "stone_trim");
                    p.set(x1, y, z0 + i, "stone_trim");
                    p.set(i, y, z1, "stone_trim");
                    p.set(0, y, z1 - i, "stone_trim");
                    p.set(x1 - i, y, z1, "stone_trim");
                    p.set(x1, y, z1 - i, "stone_trim");
                }
                p.set(T - 1, y, z0, q == 2 ? "stone_trim" : b.wall(brick, y));
            }
        }
        // solid under the floor; oak boards on the main floor; boards on the chamber floor and in
        // the attic, each with the pressed tin of the ceiling under it
        for (int x = 1; x < x1; x++) {
            for (int z = front(x, z0); z < z1; z++) {
                p.set(x, 0, z, "cellar_wall");
                p.set(x, 1, z, "cellar_wall");
                p.set(x, F1, z, "home_floor_oak");
                p.set(x, F2, z, "th_floor_tin");
                p.set(x, TOP, z, "th_floor_tin");
                for (int y = F1 + 1; y < TOP; y++) {
                    if (y != F2) {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
        // the water table round the foot of the stone, the belt under the brick, the cornice under the eaves
        ring(w, z0, z1, F1, "cv_water_table");
        ring(w, z0, z1, F2, "cv_belt");
        ring(w, z0, z1, TOP, "cv_cornice_stone");
    }

    /** Rock-faced stone to the floor, smooth stone for the ground storey, brick above. */
    private String outer(int y) {
        if (y <= F1) {
            return rng.nextDouble() < 0.08 ? "ch_ashlar_moss" : "ch_rubble";
        }
        return b.wall(y <= F2 ? "ch_ashlar" : brick, y);
    }

    /** One piece in front of every wall cell at y, all round, facing out from its wall. */
    private void ring(int w, int z0, int z1, int y, String piece) {
        int x1 = w - 1, tz = z0 - 1;
        for (int x = 0; x <= x1; x++) {
            p.set(x, y, (x < T ? tz : z0) - 1, piece + "[facing=north]");
            p.set(x, y, z1 + 1, piece + "[facing=south]");
        }
        for (int z = tz; z <= z1; z++) {
            p.set(-1, y, z, piece + "[facing=west]");
        }
        for (int z = z0; z <= z1; z++) {
            p.set(x1 + 1, y, z, piece + "[facing=east]");
        }
    }

    /** Where the cross walls are in the side ranges: the front offices end before za, the back rooms start after zs. */
    private static int za(int z0) {
        return z0 + 7;
    }

    private static int zs(int z1) {
        return z1 - 7;
    }

    private void windows(int w, int z0, int z1, int cx) {
        int x1 = w - 1, tz = z0 - 1, za = za(z0), zs = zs(z1);
        int wx1 = cx + 3, em = (wx1 + 1 + x1 - 1) / 2, wm = (cx - 4 + 1) / 2;
        for (int base : new int[] {F1, F2}) {
            String a = base == F1 ? "stone" : arch;
            // the tower's pair, front and side
            for (int i = 2; i <= 3; i++) {
                tall(i, base, tz, "north", a);
                tall(0, base, tz + i, "west", a);
            }
            // the front: lights either side of the door below, pairs above; the east offices' pair
            if (base == F1) {
                tall(cx - 3, base, z0, "north", a);
                tall(cx + 2, base, z0, "north", a);
            } else {
                for (int x : new int[] {cx - 3, cx - 2, cx + 1, cx + 2}) {
                    tall(x, base, z0, "north", a);
                }
            }
            tall(em - 1, base, z0, "north", a);
            tall(em, base, z0, "north", a);
            // the sides every three, never on a cross wall
            for (int z = tz + T + 1; z <= z1 - 2; z += 3) {
                int zz = z == za || z == zs || z == zs - 1 ? z + 1 : z;
                tall(0, base, zz, "west", a);
            }
            for (int z = z0 + 3; z <= z1 - 2; z += 3) {
                int zz = z == za || z == zs || z == zs - 1 ? z + 1 : z;
                tall(x1, base, zz, "east", a);
            }
            // the back: the stair hall's two, the restrooms' high frosted ones; upstairs a pair to each back room
            if (base == F1) {
                tall(cx - 2, base, z1, "south", a);
                tall(cx + 1, base, z1, "south", a);
                p.set(em - 1, F1 + 3, z1, b.glass("x"));
                p.set(em + 1, F1 + 3, z1, b.glass("x"));
            } else {
                for (int x : new int[] {wm - 1, wm + 1, cx - 2, cx + 1, em - 1, em + 1}) {
                    tall(x, base, z1, "south", a);
                }
            }
        }
    }

    /** A window three panes tall under a round arch of `a`, a stone sill under it outside. */
    private void tall(int x, int base, int z, String out, String a) {
        String ax = Bldg.along(out);
        String g = b.glass(ax);
        boolean boarded = g.startsWith("boarded");
        for (int k = 1; k <= 3; k++) {
            p.set(x, base + k, z, boarded || k == 1 ? g : b.glass(ax).replace("boarded_window", "window"));
        }
        p.set(x, base + 4, z, "cv_arch_" + a + "_1[facing=" + out + "]");
        int[] o = Bldg.step(out);
        p.set(x + o[0], base, z + o[1], "sill[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the roof

    private void roof(int w, int z0, int z1, int cx) {
        int courses = 5;
        hip(-1, w, z0 - 1, z1 + 1, TOP + 1, courses, true);
        // the gable over the entrance bay, breaking the eave: brick between raking stone copings,
        // the date stone in a stone band across its foot, a round window over it
        int gx0 = cx - 4, gx1 = cx + 3, rows = (gx1 - gx0 + 1) / 2;
        b.roofGable(gx0, gx1, z0, z0 + rows + 2, TOP + 1, roof, true, brick, 0);
        for (int x = gx0; x <= gx1; x++) {
            p.set(x, TOP + 1, z0 - 1, "air");
        }
        for (int i = 0; i < rows; i++) {
            int y = TOP + 1 + i;
            for (int x = gx0 + i; x <= gx1 - i; x++) {
                p.set(x, y, z0, x == gx0 + i ? "cv_rake_stone_l[facing=north]" : x == gx1 - i ? "cv_rake_stone_r[facing=north]"
                        : i == 0 ? "stone_trim" : b.wall(brick, y));
            }
        }
        p.set(cx, TOP + 1, z0, "th_datestone_0[facing=north]");
        p.set(cx - 1, TOP + 1, z0, "th_datestone_1[facing=north]");
        p.set(cx, TOP + 2, z0, "cv_oculus_white_l[facing=north]");
        p.set(cx - 1, TOP + 2, z0, "cv_oculus_white_r[facing=north]");
        // the chimneys: the mayor's fireplace flue up the west slope, the boiler's up the east
        int zc = z0 + 6;
        for (int x : new int[] {1, w - 3}) {
            for (int y = TOP + 1; y <= TOP + 8; y++) {
                for (int i = 0; i < 2; i++) {
                    p.set(x + i, y, zc, b.wall(brick, y));
                }
            }
            for (int i = 0; i < 2; i++) {
                p.set(x + i, TOP + 9, zc, "stone_trim");
            }
        }
    }

    /**
     * A roof hipped on all four sides over x0..x1, z0..z1 from y, rising `courses` courses: to a
     * flat deck with iron cresting round it, or (deck false) to the 2 by 2 point of a pyramid.
     */
    private void hip(int x0, int x1, int z0, int z1, int y, int courses, boolean deck) {
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
        if (!deck) {
            return;
        }
        int dx0 = x0 + courses, dx1 = x1 - courses, dz0 = z0 + courses, dz1 = z1 - courses, top = y + courses - 1;
        for (int x = dx0; x <= dx1; x++) {
            for (int z = dz0; z <= dz1; z++) {
                p.set(x, top, z, rng.nextDouble() < 0.12 ? "roof_moss" : "roof");
            }
        }
        // the cresting: iron railing round the deck, a post at the corners and every few blocks, bent away here and there
        for (int x = dx0; x <= dx1; x++) {
            for (int z : new int[] {dz0, dz1}) {
                boolean post = x == dx0 || x == dx1 || (x - dx0) % 4 == 0;
                if (post || rng.nextDouble() > 0.08 + b.damage * 0.12) {
                    p.set(x, top + 1, z, post ? "cv_iron_post" : "cv_iron_fence[axis=x]");
                }
            }
        }
        for (int z = dz0 + 1; z < dz1; z++) {
            for (int x : new int[] {dx0, dx1}) {
                boolean post = (z - dz0) % 4 == 0;
                if (post || rng.nextDouble() > 0.08 + b.damage * 0.12) {
                    p.set(x, top + 1, z, post ? "cv_iron_post" : "cv_iron_fence[axis=z]");
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

    // ------------------------------------------------------------------ the clock tower

    /**
     * The tower T square at the front west corner, its front at tz: the shaft up through the roof,
     * the dials over the roof's deck, the belfry, the pyramid. Below the cornice it is the walls of
     * the mayor's office and the chamber's corner; above, it stands on the attic floor.
     */
    private void tower(int tz) {
        int t = T, tx1 = t - 1, tz1 = tz + t - 1;
        int dial0 = TOP + 7, bel = dial0 + 6, cap = bel + 4;
        // the main roof's overhang ran along the tower's west face: cut it back
        for (int z = tz - 1; z <= tz1 + 1; z++) {
            for (int y = TOP + 1; y <= TOP + 2; y++) {
                String s = p.get(-1, y, z);
                if (s != null && (s.startsWith("ext_roof") || s.startsWith("cv_hip"))) {
                    p.set(-1, y, z, "air");
                }
            }
        }
        for (int y = TOP + 1; y < cap; y++) {
            for (int x = 0; x <= tx1; x++) {
                for (int z = tz; z <= tz1; z++) {
                    boolean edge = x == 0 || x == tx1 || z == tz || z == tz1;
                    String s;
                    if (!edge) {
                        s = "air";
                    } else if (y == dial0 - 1 || y == bel - 1) {
                        s = "stone_trim";
                    } else if (y >= bel) {
                        continue;
                    } else {
                        int q = (y - F2) % 2 == 0 ? 2 : 1;
                        boolean quoin = (x < q || x > tx1 - q) && (z == tz || z == tz1) || (z < tz + q || z > tz1 - q) && (x == 0 || x == tx1);
                        s = quoin ? "stone_trim" : b.wall(brick, y);
                    }
                    p.set(x, y, z, s);
                }
            }
        }
        // a slit window in the shaft on the two faces you see from the street
        for (int i = 2; i <= 3; i++) {
            p.set(i, TOP + 3, tz, b.glass("x"));
            p.set(i, TOP + 4, tz, "cv_arch_" + arch + "_1[facing=north]");
            p.set(0, TOP + 3, tz + i, b.glass("z"));
            p.set(0, TOP + 4, tz + i, "cv_arch_" + arch + "_1[facing=west]");
        }
        // the dials, four by four on each face, tile 0 at the top left as you face it
        String dial = lit ? "cv_dial_lit_" : "cv_dial_";
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                int y = dial0 + 3 - j;
                String tile = dial + (j * 4 + i);
                p.set(4 - i, y, tz - 1, tile + "[facing=north]");
                p.set(1 + i, y, tz1 + 1, tile + "[facing=south]");
                p.set(-1, y, tz + 1 + i, tile + "[facing=west]");
                p.set(t, y, tz + 4 - i, tile + "[facing=east]");
            }
        }
        if (lit) {
            for (int y = dial0 + 1; y <= dial0 + 2; y++) {
                for (int i = 2; i <= 3; i++) {
                    p.set(i, y, tz, "minecraft:ochre_froglight");
                    p.set(i, y, tz1, "minecraft:ochre_froglight");
                    p.set(0, y, tz + i, "minecraft:ochre_froglight");
                    p.set(tx1, y, tz + i, "minecraft:ochre_froglight");
                }
            }
        }
        // the belfry: brick piers at the corners, louvres under round arches between, the bell on its boards
        for (int x = 1; x < tx1; x++) {
            for (int z = tz + 1; z < tz1; z++) {
                p.set(x, bel - 1, z, "floor_boards");
            }
        }
        for (int y = bel; y < cap; y++) {
            for (int x = 0; x <= tx1; x++) {
                for (int z = tz; z <= tz1; z++) {
                    boolean corner = (x == 0 || x == tx1) && (z == tz || z == tz1);
                    boolean edge = x == 0 || x == tx1 || z == tz || z == tz1;
                    if (corner) {
                        p.set(x, y, z, y == cap - 1 ? "stone_trim" : b.wall(brick, y));
                    } else if (edge) {
                        String out = z == tz ? "north" : z == tz1 ? "south" : x == 0 ? "west" : "east";
                        p.set(x, y, z, y == cap - 1 ? "cv_arch_" + arch + "_1[facing=" + out + "]"
                                : "cv_louvre[axis=" + (z == tz || z == tz1 ? "x" : "z") + "]");
                    } else {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
        p.set(2, bel, tz + 2, "cv_bell_c");
        for (int i = 0; i < t; i++) {
            p.set(i, cap, tz - 1, "cv_cornice_stone[facing=north]");
            p.set(i, cap, tz1 + 1, "cv_cornice_stone[facing=south]");
            p.set(-1, cap, tz + i, "cv_cornice_stone[facing=west]");
            p.set(t, cap, tz + i, "cv_cornice_stone[facing=east]");
            for (int z = tz; z <= tz1; z++) {
                p.set(i, cap, z, "stone_trim");
            }
        }
        // the slate pyramid from over the cornice to its point, the copper finial
        hip(-1, t, tz - 1, tz1 + 1, cap + 1, (t + 2) / 2, false);
        p.set(2, cap + 1 + (t + 2) / 2, tz + 2, "cv_finial_c");
    }

    // ------------------------------------------------------------------ the entrance

    private void entrance(int cx, int z0) {
        int y = F1;
        // the portal: a round arch in the stone, the doors a block back under it, stone reveals
        for (int x = cx - 1; x <= cx; x++) {
            for (int yy = y + 1; yy <= y + 3; yy++) {
                p.set(x, yy, z0, "air");
            }
            p.set(x, y, z0, "pt_stone_flags");
            p.set(x, y + 4, z0 + 1, "ch_ashlar");
        }
        p.set(cx, y + 4, z0, "cv_arch_stone_l[facing=north]");
        p.set(cx - 1, y + 4, z0, "cv_arch_stone_r[facing=north]");
        for (int yy = y + 1; yy <= y + 4; yy++) {
            p.set(cx - 2, yy, z0 + 1, b.wall("ch_ashlar", yy));
            p.set(cx + 1, yy, z0 + 1, b.wall("ch_ashlar", yy));
        }
        Doors.pair(p, rng, cx - 1, y + 1, z0 + 1, "ext_door_oak", "south");
        p.set(cx - 1, y + 3, z0 + 1, b.glass("x"));
        p.set(cx, y + 3, z0 + 1, b.glass("x"));
        p.set(cx, y + 3, z0 + 2, "cv_exit_red[facing=south]");
        // CITY HALL in the band over it, its first tile at the viewer's left
        for (int i = 0; i < 8; i++) {
            p.set(cx + 3 - i, F2 - 1, z0, "th_frieze_8_" + i + "[facing=north]");
        }
        // the lamps either side
        p.set(cx - 2, y + 3, z0 - 1, (rng.nextDouble() < 0.45 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        p.set(cx + 1, y + 3, z0 - 1, (rng.nextDouble() < 0.45 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        // the steps down to the walk between stone cheeks, a lamp standard on each
        for (int x = cx - 4; x <= cx + 3; x++) {
            boolean cheek = x == cx - 4 || x == cx + 3;
            for (int z = z0 - 2; z <= z0 - 1; z++) {
                for (int yy = 0; yy < (z == z0 - 1 ? F1 : F1 - 1); yy++) {
                    p.set(x, yy, z, "stone_trim");
                }
            }
            if (cheek) {
                for (int yy = 0; yy <= F1; yy++) {
                    p.set(x, yy, z0 - 2, "stone_trim");
                    p.set(x, yy, z0 - 1, "stone_trim");
                }
                p.set(x, F1 + 1, z0 - 2, "cv_lamp_post");
                p.set(x, F1 + 2, z0 - 2, rng.nextDouble() < 0.5 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            } else {
                p.set(x, F1, z0 - 1, "cv_step_stone[facing=south]");
                p.set(x, F1 - 1, z0 - 2, "cv_step_stone[facing=south]");
                p.set(x, 0, z0 - 3, rng.nextDouble() < 0.15 ? "sidewalk_cracked" : "sidewalk");
            }
        }
        // what was posted on the door, and (where it held out) the sandbags either side of it
        if (eoc || rng.nextDouble() < 0.5) {
            p.set(cx - 2, y + 2, z0 - 1, "th_notice_" + rng.nextInt(3) + "[facing=north]");
            p.set(cx + 1, y + 2, z0 - 1, "th_notice_" + rng.nextInt(3) + "[facing=north]");
        }
        if (eoc) {
            for (int x : new int[] {cx - 3, cx - 2, cx + 1, cx + 2}) {
                if (rng.nextDouble() < 0.85) {
                    p.set(x, F1 + 1, z0 - 1, "sandbags[facing=north]");
                }
            }
            for (int x = cx - 3; x <= cx + 2; x++) {
                if (rng.nextDouble() < 0.6) {
                    p.set(x, F1 + 2, z0 - 1, x == cx - 1 || x == cx ? "air" : "sandbags[facing=north]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the main floor

    private void groundFloor(int w, int z0, int z1, int cx) {
        int x1 = w - 1, y = F1;
        int hx0 = cx - 3, hx1 = cx + 2, wx0 = hx0 - 1, wx1 = hx1 + 1;
        int za = za(z0), zs = zs(z1);
        // the hall: terrazzo inside a border, from the doors back past the stair
        for (int z = z0 + 1; z < z1; z++) {
            for (int x = hx0; x <= hx1; x++) {
                p.set(x, y, z, x == hx0 || x == hx1 || z == z0 + 2 || z == z1 - 1 ? "cv_hex_border" : "cv_terrazzo");
            }
        }
        // its walls wainscoted, plaster above; the cross walls in the side ranges
        for (int z = z0 + 1; z < z1; z++) {
            for (int x : new int[] {wx0, wx1}) {
                p.set(x, y + 1, z, "cv_wainscot");
                for (int yy = y + 2; yy < F2; yy++) {
                    p.set(x, yy, z, "cv_plaster");
                }
            }
        }
        for (int x = 1; x < x1; x++) {
            if (x < wx0 || x > wx1) {
                for (int zz : new int[] {za, zs}) {
                    for (int yy = y + 1; yy < F2; yy++) {
                        p.set(x, yy, zz, "cv_plaster");
                    }
                }
            }
        }
        mayor(1, z0, wx0 - 1, za - 1, wx0, z0 + 4);
        clerk(1, za + 1, wx0 - 1, zs - 1, wx0);
        vault(1, zs + 1, wx0 - 1, z1 - 1, zs);
        office("tax", wx1 + 1, z0 + 1, x1 - 1, za - 1, wx1);
        office("water", wx1 + 1, za + 1, x1 - 1, zs - 1, wx1);
        restrooms(wx1 + 1, zs + 1, x1 - 1, z1 - 1, wx1);
        // the stair up the middle of the hall to the chamber floor: oak treads, panelled stringers, rails
        for (int k = 0; k <= F2 - F1 - 1; k++) {
            int z = zs + k;
            for (int x = cx - 1; x <= cx; x++) {
                p.set(x, y + 1 + k, z, "home_stairs_oak[facing=south]");
                for (int yy = y + 2 + k; yy <= y + 4 + k && yy < TOP; yy++) {
                    p.set(x, yy, z, "air");
                }
            }
            for (int x : new int[] {cx - 2, cx + 1}) {
                for (int yy = y + 1; yy <= y + k; yy++) {
                    p.set(x, yy, z, "cv_panel_oak");
                }
                p.set(x, y + 1 + k, z, "cv_bar_rail[axis=z]");
            }
        }
        // the hall's furniture: benches and radiators along the walls, the directory, the notices,
        // the honour roll, past mayors, the water cooler, the flags at the stair foot
        p.set(hx1, y + 2, z0 + 3, "cv_directory[facing=west]");
        for (int z = z0 + 4; z < zs - 1; z += 5) {
            p.set(hx0, y + 1, z, "cv_hall_bench[facing=east]");
            p.set(hx0, y + 1, z + 1, "cv_hall_bench[facing=east]");
            p.set(hx1, y + 1, z + 2, "cv_hall_bench[facing=west]");
            p.set(hx0, y + 3, z + 1, rng.nextBoolean() ? "cv_portrait[facing=east]" : "cv_portrait_b[facing=east]");
        }
        p.set(hx0, y + 1, za, "home_radiator[facing=east]");
        p.set(hx1, y + 1, za, "home_radiator[facing=west]");
        p.set(hx1, y + 2, za + 2, "th_honor[facing=west]");
        p.set(hx1, y + 2, z0 + 6, "th_notice_" + rng.nextInt(3) + "[facing=west]");
        p.set(hx0, y + 2, zs - 2, "th_notice_" + rng.nextInt(3) + "[facing=east]");
        p.set(hx1, y + 1, zs - 1, "trade_water_cooler[facing=west]");
        p.set(hx0, y + 1, zs - 1, "home_plant_dead");
        p.set(cx - 2, y + 1, zs - 1, "cv_flag_us[facing=north]");
        p.set(cx - 2, y + 2, zs - 1, "cv_flag_us_top[facing=north]");
        p.set(cx + 1, y + 1, zs - 1, "cv_flag_state[facing=north]");
        p.set(cx + 1, y + 2, zs - 1, "cv_flag_state_top[facing=north]");
        // the globes hung on chains down the hall, mostly dark; past the stair a plain light
        for (int z = z0 + 4; z < zs - 1; z += 6) {
            p.set(cx - 1, F2 - 1, z, "cv_chain");
            p.set(cx - 1, F2 - 2, z, rng.nextDouble() < 0.15 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        p.set(hx0, F2 - 1, z1 - 2, rng.nextDouble() < 0.1 ? "ceiling_light" : "ceiling_light_off");
        p.set(hx1, F2 - 1, z1 - 2, rng.nextDouble() < 0.1 ? "ceiling_light" : "ceiling_light_off");
        b.mess(hx0, z0 + 2, hx1, z1 - 1, y + 1, 0.07);
    }

    /** The mayor's office in the tower corner: the desk before the bay window between the flags, the fireplace, the armchairs. */
    private void mayor(int x0, int z0, int x1, int z1, int wallX, int dz) {
        int y = F1;
        Doors.abandoned(p, rng, wallX, y + 1, dz, "home_door_oak", "west", Doors.hinge(rng));
        p.set(wallX + 1, y + 3, dz, "th_plaque_mayor[facing=east]");
        int mx = x0 + 1;
        p.set(mx, y + 1, z0 + 2, "desk[facing=south]");
        p.set(mx + 1, y + 1, z0 + 2, "desk[facing=south]");
        p.set(mx + 1, y + 1, z0 + 1, rng.nextDouble() < 0.7 ? "office_chair[facing=south]" : "air");
        p.set(mx, y + 2, z0 + 2, (rng.nextDouble() < 0.3 ? "cv_table_lamp_lit" : "cv_table_lamp") + "[axis=x]");
        p.set(x0, y + 1, z0 + 1, "cv_flag_us[facing=south]");
        p.set(x0, y + 2, z0 + 1, "cv_flag_us_top[facing=south]");
        p.set(mx + 3, y + 1, z0, "cv_flag_state[facing=south]");
        p.set(mx + 3, y + 2, z0, "cv_flag_state_top[facing=south]");
        // the fireplace on the outer wall, its flue up through the chamber to the stack
        int fz = z0 + 6;
        p.set(x0, y + 1, fz, "home_fireplace[facing=east]");
        for (int yy = y + 2; yy < TOP; yy++) {
            if (yy != F2) {
                p.set(x0, yy, fz, "home_chimney[facing=east]");
            }
        }
        p.set(x0, y + 3, fz - 2, "cv_portrait[facing=east]");
        for (int x = x0 + 1; x <= x0 + 3; x++) {
            for (int z = fz - 1; z <= fz + 1 && z <= z1; z++) {
                p.set(x, y + 1, z, "cv_carpet_red");
            }
        }
        p.set(x0 + 2, y + 1, fz - 1, "pk_armchair[facing=west]");
        if (fz + 1 <= z1) {
            p.set(x0 + 2, y + 1, fz + 1, "pk_armchair[facing=west]");
        }
        for (int x = x0 + 1; x <= x1; x++) {
            if (rng.nextDouble() < 0.8) {
                p.set(x, y + 1, z1, "home_bookcase[facing=north]");
                p.set(x, y + 2, z1, "home_bookcase_top[facing=north]");
            }
        }
        p.set(x1, y + 1, z0 + 1, "home_coat_rack[facing=west]");
        p.set(x1, y + 3, z0 + 2, "home_calendar[facing=west]");
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0, x1, z1, y + 1, 0.05);
    }

    /** The city clerk's office: the counter window onto the hall, desks behind it, cabinets, the vault door in the back wall. */
    private void clerk(int x0, int z0, int x1, int z1, int wallX) {
        int y = F1, wz = z0 + 2;
        counterWindow(wallX, wz, "east", "th_plaque_clerk");
        Doors.abandoned(p, rng, wallX, y + 1, z1, "home_door_oak", "west", Doors.hinge(rng));
        for (int z = z0; z <= z1; z += 2) {
            p.set(x1 - 2, y + 1, z, "desk[facing=east]");
            p.set(x1 - 3, y + 1, z, rng.nextDouble() < 0.75 ? "office_chair[facing=east]" : "air");
        }
        for (int z = z0; z <= z1; z++) {
            if (rng.nextDouble() < 0.75) {
                p.set(x0, y + 1, z, (rng.nextBoolean() ? "filing_cabinet" : "pk_file_cabinet") + "[facing=east]");
            }
        }
        p.set(x1, y + 1, z0, "store_safe[facing=west]");
        p.set(x0, y + 3, z0 + 3, "bunk_clock[facing=east]");
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0, x1, z1, y + 1, 0.06);
    }

    /** A counter window in the hall wall at wallX over z and z + 1, facing the public side, its plaque over it. */
    private void counterWindow(int wallX, int z, String pub, String plaque) {
        int y = F1, o = pub.equals("east") ? 1 : -1;
        for (int zz = z; zz <= z + 1; zz++) {
            p.set(wallX, y + 1, zz, "cv_po_counter[facing=" + pub + "]");
            p.set(wallX, y + 2, zz, rng.nextDouble() < b.damage * 0.3 ? "air" : "cv_po_grille[facing=" + pub + "]");
            p.set(wallX, y + 3, zz, "cv_panel_oak");
        }
        p.set(wallX + o, y + 4, z, plaque + "[facing=" + pub + "]");
    }

    /** The records vault behind the clerk: the steel door, the old safe, shelves of minute books and ledgers. */
    private void vault(int x0, int z0, int x1, int z1, int wallZ) {
        int y = F1, dx = (x0 + x1) / 2;
        p.set(dx, y + 1, wallZ, "trade_vault_door[facing=north]");
        p.set(dx, y + 2, wallZ, "trade_vault_door_top[facing=north]");
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, y, z, "floor_concrete");
            }
        }
        p.set(x0, y + 1, z1, "cellar_safe[facing=east]");
        p.set(x0, y + 2, z1, "cellar_safe_top[facing=east]");
        for (int z = z0 + 1; z < z1; z++) {
            p.set(x0, y + 1, z, "home_bookcase[facing=east]");
            p.set(x0, y + 2, z, "home_bookcase_top[facing=east]");
            p.set(x1, y + 1, z, "home_bookcase[facing=west]");
            p.set(x1, y + 2, z, "home_bookcase_top[facing=west]");
        }
        for (int x = x0 + 1; x < x1; x++) {
            p.set(x, y + 1, z1, rng.nextBoolean() ? "filing_cabinet[facing=north]" : "pk_file_cabinet[facing=north]");
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(x0 + 1 + rng.nextInt(Math.max(1, x1 - x0 - 1)), y + 1, z0 + rng.nextInt(z1 - z0), "cv_stack[axis=" + (rng.nextBoolean() ? "x" : "z") + "]");
        }
        p.set(dx, F2 - 1, (z0 + z1) / 2, "bunk_tube[axis=z]");
        b.mess(x0 + 1, z0, x1 - 1, z1 - 1, y + 1, 0.08);
    }

    /** The tax collector's or the water department's office: its counter window, desks, cabinets, the safe or the map of the mains. */
    private void office(String kind, int x0, int z0, int x1, int z1, int wallX) {
        int y = F1, wz = (z0 + z1) / 2 - 1;
        counterWindow(wallX, wz, "west", "th_plaque_" + kind);
        Doors.abandoned(p, rng, wallX, y + 1, kind.equals("tax") ? z1 : z0, "home_door_oak", "east", Doors.hinge(rng));
        for (int z = z0; z <= z1; z += 2) {
            p.set(x0 + 2, y + 1, z, "desk[facing=west]");
            p.set(x0 + 3, y + 1, z, rng.nextDouble() < 0.75 ? "office_chair[facing=west]" : "air");
        }
        for (int z = z0; z <= z1; z++) {
            if (rng.nextDouble() < 0.7) {
                p.set(x1, y + 1, z, (rng.nextBoolean() ? "filing_cabinet" : "pk_file_cabinet") + "[facing=west]");
            }
        }
        if (kind.equals("tax")) {
            p.set(x1, y + 1, z0, "cellar_safe[facing=west]");
            p.set(x1, y + 2, z0, "cellar_safe_top[facing=west]");
        } else {
            p.set(x1, y + 2, wz, "bunk_map_wall[facing=west]");
            p.set(x1, y + 2, wz + 1, "bunk_map_wall[facing=west]");
        }
        p.set(x1, y + 3, z1, "home_calendar[facing=west]");
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0, x1, z1, y + 1, 0.06);
    }

    /** The restrooms: stalls along the back wall, basins and mirrors along the side, mosaic on the floor. */
    private void restrooms(int x0, int z0, int x1, int z1, int wallX) {
        int y = F1, dz = (z0 + z1) / 2;
        Doors.abandoned(p, rng, wallX, y + 1, dz, "home_door_oak", "east", Doors.hinge(rng));
        p.set(wallX - 1, y + 3, dz, "cv_plaque_restroom[facing=west]");
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, y, z, "cv_hex_tile");
            }
        }
        for (int x = x0 + 1; x <= x1; x += 2) {
            p.set(x, y + 1, z1, "fit_toilet_stall[facing=north]");
        }
        for (int z = z0; z <= z1 - 2; z += 2) {
            p.set(x1, y + 1, z, "home_sink_white[facing=west]");
            p.set(x1, y + 2, z, "home_mirror[facing=west]");
        }
        p.set((x0 + x1) / 2, F2 - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0, x1 - 1, z1 - 1, y + 1, 0.06);
    }

    // ------------------------------------------------------------------ the chamber floor

    private void upperFloor(int w, int z0, int z1, int cx) {
        int x1 = w - 1, y = F2, zs = zs(z1), zb = zs - 1;
        int hx0 = cx - 3, hx1 = cx + 2, wx0 = hx0 - 1, wx1 = hx1 + 1;
        // the chamber's back wall, panelled to waist height, two doors in from the landing
        for (int x = 1; x < x1; x++) {
            for (int yy = y + 1; yy < TOP; yy++) {
                p.set(x, yy, zb, yy == y + 1 ? "cv_panel_oak" : "cv_plaster");
            }
        }
        for (int x : new int[] {hx0, hx1}) {
            Doors.abandoned(p, rng, x, y + 1, zb, "home_door_oak", "north", Doors.hinge(rng));
            p.set(x, y + 3, zb - 1, "cv_exit_red[facing=north]");
        }
        p.set(hx0, y + 3, zb + 1, "th_plaque_council[facing=south]");
        // the dais across the front, a step up; the bench on it facing the room, the chairs behind
        int dx0 = cx - 6, dx1 = cx + 5, bx0 = cx - 5, bx1 = cx + 4;
        for (int x = dx0; x <= dx1; x++) {
            for (int z = z0 + 1; z <= z0 + 3; z++) {
                p.set(x, y + 1, z, "ch_floor_oak");
            }
            p.set(x, y + 1, z0 + 4, "ch_step_oak[facing=south]");
        }
        for (int x = bx0; x <= bx1; x++) {
            String piece = x == bx0 ? "th_bench_end_l" : x == bx1 ? "th_bench_end" : rng.nextDouble() < 0.7 ? "th_bench" : "th_bench_plain";
            p.set(x, y + 2, z0 + 2, piece + "[facing=south]");
            double r = rng.nextDouble();
            p.set(x, y + 2, z0 + 1, r < 0.75 ? "office_chair[facing=south]" : r < 0.85 ? "office_chair[facing=" + DIRS[rng.nextInt(4)] + "]" : "air");
        }
        // the town seal over the mayor's seat between the windows, the flags at the dais ends, past mayors on the side walls
        p.set(cx, y + 4, z0 + 1, "cv_seal_c[facing=south]");
        p.set(dx0, y + 2, z0 + 1, "cv_flag_us[facing=south]");
        p.set(dx0, y + 3, z0 + 1, "cv_flag_us_top[facing=south]");
        p.set(dx1, y + 2, z0 + 1, "cv_flag_state[facing=south]");
        p.set(dx1, y + 3, z0 + 1, "cv_flag_state_top[facing=south]");
        p.set(1, y + 3, z0 + 4, "cv_portrait[facing=east]");
        p.set(x1 - 1, y + 3, z0 + 5, "cv_portrait_b[facing=west]");
        p.set(x1 - 1, y + 3, z0 + 8, "cv_portrait[facing=west]");
        // the town map on the west wall with the red line round the quarantine
        p.set(1, y + 3, z0 + 8, "th_map_0[facing=east]");
        p.set(1, y + 3, z0 + 7, "th_map_1[facing=east]");
        // the clerk's table and the press table below the dais, the podium facing the bench
        p.set(dx0 - 1, y + 1, z0 + 6, "desk[facing=east]");
        p.set(dx0 - 2, y + 1, z0 + 6, "office_chair[facing=east]");
        p.set(dx1 + 1, y + 1, z0 + 6, "desk[facing=west]");
        p.set(dx1 + 2, y + 1, z0 + 6, "office_chair[facing=west]");
        p.set(cx - 1, y + 1, z0 + 7, "th_podium[facing=north]");
        // the bar across the room, its gate open in the middle
        int bar = z0 + 8;
        for (int x = 1; x < x1; x++) {
            if (x != cx - 1 && x != cx) {
                p.set(x, y + 1, bar, x == cx - 2 || x == cx + 1 ? "cv_bar_post" : "cv_bar_rail[axis=x]");
            }
        }
        for (int z = bar + 1; z < zb; z++) {
            for (int x = cx - 1; x <= cx; x++) {
                p.set(x, y + 1, z, "cv_carpet_red");
            }
        }
        if (eoc) {
            emergency(x1, bar + 1, zb - 1, cx);
        } else {
            polling(x1, bar + 1, zb - 1, cx);
        }
        // the lights hung from the tin ceiling on chains
        for (int x : new int[] {cx - 5, cx - 1, cx + 4}) {
            for (int z = z0 + 4; z < zb - 1; z += 6) {
                p.set(x, TOP - 1, z, "cv_chain");
                p.set(x, TOP - 2, z, rng.nextDouble() < 0.12 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            }
        }
        b.mess(1, z0 + 5, x1 - 1, zb - 1, y + 1, 0.04);
        // the landing round the stairwell, the committee room west of it, the civil-defence office east
        for (int x = hx0; x <= hx1; x++) {
            for (int z = zb + 1; z < z1; z++) {
                p.set(x, y, z, "cv_terrazzo");
            }
        }
        for (int x = cx - 1; x <= cx; x++) {
            for (int z = zs; z < zs + F2 - F1 - 1; z++) {
                p.set(x, y, z, "air");
            }
        }
        for (int z = zs; z < zs + F2 - F1 - 1; z++) {
            p.set(cx - 2, y + 1, z, "cv_bar_rail[axis=z]");
            p.set(cx + 1, y + 1, z, "cv_bar_rail[axis=z]");
        }
        for (int z = zb + 1; z < z1; z++) {
            for (int yy = y + 1; yy < TOP; yy++) {
                p.set(wx0, yy, z, "cv_plaster");
                p.set(wx1, yy, z, "cv_plaster");
            }
        }
        int dz = (zb + z1) / 2;
        Doors.abandoned(p, rng, wx0, y + 1, dz, "home_door_oak", "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, wx1, y + 1, dz, "home_door_oak", "east", Doors.hinge(rng));
        p.set(wx0 + 1, y + 3, dz, "cv_plaque_office[facing=east]");
        p.set(wx1 - 1, y + 3, dz, "cv_plaque_office[facing=west]");
        p.set(hx0, y + 3, z1 - 1, "cv_portrait_b[facing=north]");
        p.set(hx0, F2 + 4, z1 - 2, rng.nextDouble() < 0.1 ? "ceiling_light" : "ceiling_light_off");
        committee(1, zb + 1, wx0 - 1, z1 - 1);
        defence(wx1 + 1, zb + 1, x1 - 1, z1 - 1, wx1);
    }

    /** Election day, the last one: the booths along the back, the ballot box on its table, the chairs left in rows. */
    private void polling(int x1, int z0, int z1, int cx) {
        int y = F2;
        for (int x = 2; x <= 5; x++) {
            booth(x, z1, "north");
            booth(x1 - x, z1, "north");
        }
        p.set(cx - 1, y + 1, z0 + 1, "cv_table[axis=x]");
        p.set(cx, y + 1, z0 + 1, "cv_table[axis=x]");
        p.set(cx - 1, y + 2, z0 + 1, "th_ballot_box[facing=north]");
        p.set(cx, y + 2, z0 + 1, rng.nextDouble() < 0.5 ? "paper" : "air");
        for (int x = 2; x <= x1 - 2; x++) {
            if (Math.abs(x - cx + 0.5) > 2 && rng.nextDouble() < 0.8) {
                p.set(x, y + 1, z0, rng.nextDouble() < 0.85 ? "trade_folding_chair[facing=north]" : "trade_folding_chair_tipped[facing=north]");
            }
        }
        p.set(1, y + 3, z1 - 2, "th_notice_" + rng.nextInt(3) + "[facing=east]");
    }

    /** One voting booth, its open front toward `open`, the VOTE card on its rail. */
    private void booth(int x, int z, String open) {
        p.set(x, F2 + 1, z, "th_booth[facing=" + open + "]");
        p.set(x, F2 + 2, z, "th_booth_top[facing=" + open + "]");
    }

    /**
     * The emergency centre the council ran from the chamber at the end: the map table where the
     * public sat, the radios along the east wall, cots along the west, stores, the red lamps.
     */
    private void emergency(int x1, int z0, int z1, int cx) {
        int y = F2;
        for (int x = cx - 2; x <= cx + 1; x++) {
            for (int z = z0; z <= z0 + 1; z++) {
                p.set(x, y + 1, z, "bunk_map_table");
            }
        }
        for (int x = cx - 2; x <= cx + 1; x++) {
            if (rng.nextDouble() < 0.7) {
                p.set(x, y + 1, z0 + 2, "trade_folding_chair[facing=north]");
            }
        }
        for (int z = z0; z <= z1; z++) {
            p.set(x1 - 1, y + 1, z, z == z1 ? "bunk_rack_boxes[facing=west]" : "bunk_radio_desk[facing=west]");
        }
        p.set(x1 - 2, y + 1, z0, "bunk_chair[facing=east]");
        p.set(x1 - 2, y + 1, z0 + 2, "bunk_chair[facing=east]");
        for (int x = 2; x <= 6; x += 2) {
            p.set(x, y + 1, z0, "ch_cot_head[facing=north]");
            p.set(x, y + 1, z0 + 1, "ch_cot_foot[facing=north]");
        }
        booth(2, z1, "north");
        p.set(3, y + 1, z1, "bunk_crate_ration[facing=north]");
        p.set(4, y + 1, z1, "bunk_drum_water");
        p.set(5, y + 1, z1, "cardboard_box[facing=east]");
        p.set(cx - 1, y + 2, z1, "bunk_map_wall[facing=north]");
        p.set(cx, y + 2, z1, "bunk_map_wall[facing=north]");
        p.set(1, y + 3, z0 + 1, "bunk_lamp_red_lit[facing=east]");
        p.set(x1 - 1, y + 3, z0 - 3, "bunk_lamp_red_lit[facing=west]");
        p.set(cx + 2, y + 3, z1, rng.nextDouble() < 0.6 ? "bunk_lamp_red_lit[facing=north]" : "bunk_lamp_red[facing=north]");
        for (int i = 0; i < 6; i++) {
            p.setIfAir(2 + rng.nextInt(x1 - 3), y + 1, z0 + rng.nextInt(z1 - z0 + 1),
                    rng.nextDouble() < 0.5 ? "paper" : rng.nextDouble() < 0.5 ? "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]" : "blood_splat");
        }
    }

    /** The committee room: the long table and its chairs, the bookcase, a portrait. */
    private void committee(int x0, int z0, int x1, int z1) {
        int y = F2, tx = (x0 + x1) / 2;
        for (int z = z0 + 1; z <= z1 - 1; z++) {
            p.set(tx, y + 1, z, "cv_table[axis=z]");
            if (rng.nextDouble() < 0.8) {
                p.set(tx - 1, y + 1, z, "pk_chair_wooden[facing=east]");
            }
            if (rng.nextDouble() < 0.8) {
                p.set(tx + 1, y + 1, z, "pk_chair_wooden[facing=west]");
            }
        }
        for (int x = x0; x <= x1; x++) {
            if (x != tx) {
                p.set(x, y + 1, z1, "home_bookcase[facing=north]");
                p.set(x, y + 2, z1, "home_bookcase_top[facing=north]");
            }
        }
        p.set(tx, y + 3, z1, "cv_portrait[facing=north]");
        p.set(tx, TOP - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0, x1, z1 - 1, y + 1, 0.05);
    }

    /** The civil-defence office from the sixties: the radio desk, the wall map, the shelter stencil, boxes of pamphlets. */
    private void defence(int x0, int z0, int x1, int z1, int wallX) {
        int y = F2;
        p.set(x1, y + 1, z0 + 1, "bunk_radio_desk[facing=west]");
        p.set(x1 - 1, y + 1, z0 + 1, "bunk_chair[facing=east]");
        p.set(x1, y + 1, z0 + 2, "bunk_desk[facing=west]");
        p.set(x0 + 1, y + 2, z1, "bunk_map_wall[facing=north]");
        p.set(x0 + 2, y + 2, z1, "bunk_map_wall[facing=north]");
        p.set(wallX + 1, y + 2, z0 + 2, "bunk_st_shelter_l[facing=east]");
        p.set(wallX + 1, y + 2, z0 + 1, "bunk_st_shelter_r[facing=east]");
        for (int x = x0; x <= x1; x++) {
            if (rng.nextDouble() < 0.6) {
                p.set(x, y + 1, z1, rng.nextBoolean() ? "bunk_rack_boxes[facing=north]" : "filing_cabinet[facing=north]");
            }
        }
        p.set(x1, y + 3, z1 - 2, (eoc || rng.nextDouble() < 0.3 ? "bunk_lamp_red_lit" : "bunk_lamp_red") + "[facing=west]");
        p.set((x0 + x1) / 2, TOP - 1, (z0 + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(x0, z0, x1 - 1, z1 - 1, y + 1, 0.06);
    }

    // ------------------------------------------------------------------ the grounds

    /** The flagpole on the lawn east of the steps. */
    private void grounds(int w, int cx, int z0) {
        int fx = Math.min(w - 2, cx + 6), fz = Math.max(0, z0 - 3);
        p.set(fx, 1, fz, "cv_flagpole_base");
        for (int y = 2; y < 15; y++) {
            p.set(fx, y, fz, "cv_flagpole");
        }
        p.set(fx, 15, fz, "cv_flagpole_top[facing=north]");
    }
}
