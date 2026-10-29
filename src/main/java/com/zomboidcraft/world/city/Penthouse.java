package com.zomboidcraft.world.city;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A tall hotel's penthouse: the top three storeys of the tower rebuilt as one millionaire's
 * home, a monument on the skyline. Each hotel rolls one of seven layouts (mirrored at random),
 * each its own shape: glass boxes jutting out over the street, cut and rounded corners, stepped
 * tiers with terraces on every level, rooms two and three storeys tall with glass bridges
 * across them, a grand stair in its own hall under a glass lantern, and an Art Deco crown of
 * lit stone tiers and a spire on top of the core.
 *
 * Layouts are drawn on a 40 x 32 plan with the core at x 16..24, z 12..22 and the street at
 * z = 0; the plan stretches round the core to fit the tower. A level's spec lists, separated by
 * ';': its shape ("inset N", "chamfer C MASK", "round R MASK" with corners NW 1, NE 2, SW 4,
 * SE 8; "bay X0 X1 DEPTH" a glass box out over the street; "pav X0 Z0 X1 Z1" a small top
 * pavilion), what fills its open floor ("open KIND"), double-height voids ("void ..."), its
 * rooms ("room X0 Z0 X1 Z1 KIND", plan edges) and set pieces ("feat KIND X0 Z0 X1 Z1").
 */
final class Penthouse {
    static final int OUT = 3, FLOOR = 4;
    static final String[] NAMES = {"Sky Mansion", "Gallery Loft", "Entertainer", "Winter Garden", "Collector", "Spa Retreat", "Velvet Room"};
    static final String[][] LAYOUTS = {
        // the sky mansion: a three-storey great room behind the glass, the master wing on a cut-cornered top
        {"chamfer 4 12; bay 12 28 3; stair 2 2; open great; room 1 11 13 21 ph_dining; room 1 21 13 31 library; room 27 11 39 21 ph_kitchen;"
                + " room 27 21 39 31 ph_staff; room 13 23 27 31 ph_music",
         "chamfer 4 12; open lounge; void 1 0 39 10; room 1 12 13 22 penthouse_bed; room 1 22 13 31 penthouse_bed; room 13 23 27 31 ph_cinema;"
                + " room 27 12 39 22 ph_gym; room 27 22 39 31 penthouse_bed",
         "inset 3; chamfer 4 15; open lounge; void 8 3 32 9; room 4 12 15 28 ph_master; room 15 23 25 28 ph_bath; room 25 12 36 20 ph_closet;"
                + " room 25 20 36 28 ph_study"},
        // the gallery loft: a long art hall with a glass front, the lap pool behind, a sculpture terrace on top
        {"chamfer 3 3; bay 4 36 2; stair 33 2; open gallery; room 1 10 14 22 ph_dining; room 26 10 39 22 ph_kitchen; room 1 22 7 31 ph_sauna;"
                + " feat lappool 8 23 39 31",
         "inset 2; open lounge; void 2 0 30 8; room 2 12 14 21 penthouse_bed; room 26 12 38 21 penthouse_bed; room 2 23 16 30 ph_master;"
                + " room 16 23 24 30 ph_bath; room 24 23 38 30 ph_closet",
         "pav 12 4 28 16; open lounge; feat olives 3 3 11 11; feat olives 29 18 37 29"},
        // the entertainer: a private nightclub under a double-height dance floor, a sky bar and hot tub up top
        {"chamfer 5 15; stair 34 2; open bar; feat dance 13 2 27 9; room 1 12 13 22 ph_billiards; room 1 22 13 31 ph_cigar;"
                + " room 13 23 27 31 ph_wine; room 27 12 39 22 ph_cards; room 27 22 39 31 ph_kitchen",
         "chamfer 5 15; open great; void 13 2 27 9; room 1 12 13 31 ph_master; room 13 23 20 31 ph_bath; room 20 23 27 31 ph_closet;"
                + " room 27 12 39 22 penthouse_bed; room 27 22 39 31 ph_cinema",
         "pav 4 4 20 12; open bar; feat tub 27 20 34 26"},
        // the winter garden: a two-storey glasshouse jungle with a koi pond, rounded glass corners
        {"round 6 3; stair 2 2; open great; room 1 11 13 20 ph_dining; room 27 11 39 20 ph_kitchen; room 1 22 8 31 ph_tea;"
                + " feat garden 8 22 39 31; feat pond 18 24 28 29",
         "inset 2; round 6 3; open lounge; void 8 23 38 30; room 8 2 16 10 ph_bath; room 16 2 38 10 ph_master; room 2 12 14 21 penthouse_bed;"
                + " room 26 12 38 21 penthouse_bed",
         "pav 12 3 28 16; open lounge; feat glassroof 8 23 38 30; feat olives 3 18 10 29"},
        // the collector: cars behind a jutting glass showroom, the gun room, trophies, the vault, an observatory
        {"chamfer 4 3; bay 6 34 3; stair 2 2; open cars; feat cars 8 -3 32 10; feat carlift 33 1 39 8; room 1 11 13 21 ph_guns;"
                + " room 1 21 13 31 ph_trophy; room 13 23 20 31 ph_study; room 20 23 27 31 ph_vault; room 27 11 39 31 ph_bar",
         "chamfer 4 3; open great; room 1 11 13 31 ph_master; room 13 23 20 31 ph_bath; room 20 23 27 31 ph_closet;"
                + " room 27 11 39 21 penthouse_bed; room 27 21 39 31 ph_staff",
         "pav 10 2 30 12; open lounge; feat dome 12 2 28 11"},
        // the spa: a pool hall two storeys tall between white columns, a glass-bottomed plunge pool on the roof over it
        {"chamfer 3 12; bay 4 36 2; stair 2 2; open pool; feat pool 9 0 33 9; room 1 12 13 22 ph_sauna; room 1 22 13 31 ph_treat;"
                + " room 13 23 27 31 ph_gym; room 27 12 39 22 ph_juice; room 27 22 39 31 ph_kitchen",
         "chamfer 3 12; open lounge; void 8 0 34 9; room 1 13 13 31 ph_master; room 13 23 27 31 ph_bath; room 27 13 39 22 penthouse_bed;"
                + " room 27 22 39 31 penthouse_bed",
         "pav 22 3 37 12; open lounge; feat plunge 9 2 19 8"},
        // the velvet room: a private gentlemen's club, the stage out in a glass box over the street
        {"chamfer 4 3; bay 14 26 3; stair 2 2; open velvet; feat stage 14 -3 26 4; feat runway 18 4 22 9; feat neon 0 0 40 32;"
                + " room 1 12 13 22 ph_bar; room 1 22 13 31 ph_champagne; room 13 23 20 31 ph_dressing; room 20 23 27 31 ph_cash;"
                + " room 27 12 39 22 ph_lounge; room 27 22 39 31 ph_kitchen",
         "chamfer 4 3; open lounge; void 10 0 30 9; feat neon 0 0 40 32; room 1 12 13 31 ph_master; room 13 23 27 31 ph_bath;"
                + " room 27 12 39 22 ph_cards; room 27 22 39 31 ph_study",
         "pav 4 4 20 12; open velvet; feat neon 0 0 40 32; feat tub 25 20 33 26"},
    };

    /** Whether a tower is big enough round its core for the three-storey penthouse. */
    static boolean fits(int w, int d) {
        return w >= 26 && d >= 22;
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d, base, kx, cz0, n;
    private final String trim;
    private final boolean furnished;
    private final double lit;
    final int layout;
    private final boolean mirror;
    private final Level[] lv = new Level[3];
    private final boolean[][] foot = new boolean[3][], per = new boolean[3][], hole = new boolean[3][], rim = new boolean[3][],
            res = new boolean[4][], keep = new boolean[3][], item = new boolean[4][], front = new boolean[4][];
    private int hx, hz;
    private final List<int[]> columns = new ArrayList<>();
    private final String clad;

    /** One storey's spec, parsed. */
    private static final class Level {
        int inset, chamfer, cmask, round, rmask;
        int[] bay, pav;
        String open = "great";
        int[] stair;
        final List<int[]> voids = new ArrayList<>();
        final List<int[]> rooms = new ArrayList<>();
        final List<String> kinds = new ArrayList<>();
        final List<int[]> feats = new ArrayList<>();
        final List<String> featKinds = new ArrayList<>();
    }

    Penthouse(Placer p, SplittableRandom rng, int w, int d, int base, int kx, int cz0, String trim, boolean furnished, double lit) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.base = base;
        this.kx = kx;
        this.cz0 = cz0;
        this.trim = trim;
        this.furnished = furnished;
        this.lit = lit;
        this.n = w * (d + OUT);
        int forced = Integer.getInteger("zc.penthouse", -1);
        this.layout = forced >= 0 ? forced % LAYOUTS.length : rng.nextInt(LAYOUTS.length);
        this.mirror = rng.nextBoolean();
        // the crown's cladding: stainless like the Chrysler's, brass for the showier homes, or lit stone
        this.clad = switch (layout) {
            case 3, 6 -> "of_deco_brass";
            case 1, 4 -> "stone_trim";
            default -> "of_deco_steel";
        };
        for (int k = 0; k < 3; k++) {
            lv[k] = parse(LAYOUTS[layout][k]);
        }
    }

    private int y(int k) {
        return base + k * FLOOR;
    }

    // ------------------------------------------------------------------ the plan

    private Level parse(String spec) {
        Level l = new Level();
        for (String part : spec.split(";")) {
            String[] t = part.trim().split("\\s+");
            switch (t[0]) {
                case "inset" -> l.inset = i(t[1]);
                case "chamfer" -> {
                    l.chamfer = i(t[1]);
                    l.cmask = flip(i(t[2]));
                }
                case "round" -> {
                    l.round = i(t[1]);
                    l.rmask = flip(i(t[2]));
                }
                case "bay" -> l.bay = new int[] {i(t[1]), i(t[2]), i(t[3])};
                case "pav" -> l.pav = new int[] {i(t[1]), i(t[2]), i(t[3]), i(t[4])};
                case "stair" -> l.stair = new int[] {i(t[1]), i(t[2])};
                case "open" -> l.open = t[1];
                case "void" -> l.voids.add(new int[] {i(t[1]), i(t[2]), i(t[3]), i(t[4])});
                case "room" -> {
                    l.rooms.add(new int[] {i(t[1]), i(t[2]), i(t[3]), i(t[4])});
                    l.kinds.add(t[5]);
                }
                case "feat" -> {
                    l.featKinds.add(t[1]);
                    l.feats.add(new int[] {i(t[2]), i(t[3]), i(t[4]), i(t[5])});
                }
                default -> { }
            }
        }
        return l;
    }

    private static int i(String s) {
        return Integer.parseInt(s);
    }

    /** Corner bits swapped west for east when the plan is mirrored. */
    private int flip(int mask) {
        return mirror ? (mask & 1) << 1 | (mask & 2) >> 1 | (mask & 4) << 1 | (mask & 8) >> 1 : mask;
    }

    /** A plan x edge (0..40) on the tower: stretched either side of the core, the core itself to scale. */
    private int mx(int px) {
        int right = w - kx - 8;
        if (px <= 16) {
            return Math.round(px * kx / 16f);
        }
        if (px <= 24) {
            return kx + px - 16;
        }
        return kx + 8 + Math.round((px - 24) * right / 16f);
    }

    private int mz(int pz) {
        if (pz <= 0) {
            return pz;
        }
        if (pz <= 12) {
            return Math.round(pz * cz0 / 12f);
        }
        if (pz <= 22) {
            return cz0 + pz - 12;
        }
        return cz0 + 10 + Math.round(Math.min(10, pz - 22) * (d - cz0 - 10) / 10f);
    }

    /** A plan rectangle (edges) as tower cells x0, z0, x1, z1 inclusive, mirrored as the layout is. */
    private int[] cells(int[] q) {
        int px0 = mirror ? 40 - q[2] : q[0], px1 = mirror ? 40 - q[0] : q[2];
        return new int[] {mx(px0), mz(q[1]), mx(px1) - 1, mz(q[3]) - 1};
    }

    private boolean ok(int x, int z) {
        return x >= 0 && x < w && z >= -OUT && z < d;
    }

    private int ix(int x, int z) {
        return (z + OUT) * w + x;
    }

    private boolean at(boolean[] a, int x, int z) {
        return a != null && ok(x, z) && a[ix(x, z)];
    }

    private boolean inCore(int x, int z) {
        return x >= kx && x < kx + 8 && z >= cz0 && z < cz0 + 10;
    }

    private boolean inTower(int x, int z) {
        return x >= 0 && x < w && z >= 0 && z < d;
    }

    /** The grand stair's hall: the lanes, the mid wall, the landings (5 x 8 from hx, hz). */
    private boolean inHall(int x, int z) {
        return x >= hx && x <= hx + 4 && z >= hz && z <= hz + 7;
    }

    /** The hall's side walls beside the flights. */
    private boolean hallSide(int x, int z) {
        return (x == hx - 1 || x == hx + 5) && z >= hz + 2 && z <= hz + 5;
    }

    private boolean nearHall(int x, int z, int m) {
        return x >= hx - m && x <= hx + 4 + m && z >= hz - m && z <= hz + 7 + m;
    }

    /** Where the hall goes: near the plan's spot, inside the tower with room round it, clear of the core. */
    private boolean placeHall() {
        int[] st = lv[0].stair == null ? new int[] {2, 2} : lv[0].stair;
        int tx = mx(st[0]), tz = mz(st[1]);
        if (mirror) {
            tx = w - 5 - mx(40 - st[0] - 6);
            tx = mx(40 - st[0]) - 5;
        }
        int best = Integer.MAX_VALUE;
        boolean found = false;
        for (int x0 = 2; x0 + 6 < w; x0++) {
            for (int z0 = 2; z0 + 9 < d; z0++) {
                // clear of the core, its landing and the closet door behind it
                boolean clash = x0 + 5 >= kx - 1 && x0 - 1 <= kx + 8 && z0 + 8 >= cz0 - 3 && z0 - 1 <= cz0 + 10;
                if (clash) {
                    continue;
                }
                int score = Math.abs(x0 - tx) + Math.abs(z0 - tz) * 2;
                if (score < best) {
                    best = score;
                    hx = x0;
                    hz = z0;
                    found = true;
                }
            }
        }
        return found;
    }

    // ------------------------------------------------------------------ shapes

    private boolean[] shape(int k) {
        Level l = lv[k];
        boolean[] f = new boolean[n];
        if (l.pav != null) {
            int[] r = cells(l.pav);
            fillRect(f, r[0], r[1], r[2], r[3]);
            // the pavilion joins the core and the stair hall by a wing between them
            int za = Math.max(cz0 - 3, hz - 2), zb = Math.min(cz0 + 12, hz + 9);
            if (zb - za >= 5) {
                zb = Math.min(zb, za + 6);
                fillRect(f, Math.min(kx - 3, hx - 2), za, Math.max(kx + 10, hx + 6), zb);
            } else {
                fillRect(f, Math.min(kx - 3, hx - 2), Math.min(cz0 - 3, hz - 2), Math.max(kx + 10, hx + 6), Math.max(cz0 + 12, hz + 9));
            }
        } else {
            int in = l.inset;
            for (int x = in; x < w - in; x++) {
                for (int z = in; z < d - in; z++) {
                    if (!cut(l, x - in, z - in, w - 2 * in, d - 2 * in)) {
                        f[ix(x, z)] = true;
                    }
                }
            }
            if (l.bay != null) {
                int[] r = cells(new int[] {l.bay[0], 0, l.bay[1], 1});
                fillRect(f, r[0], -l.bay[2], r[2], -1);
            }
        }
        // always round the core and the stair hall
        fillRect(f, Math.max(0, kx - 3), Math.max(0, cz0 - 3), Math.min(w - 1, kx + 10), Math.min(d - 1, cz0 + 12));
        fillRect(f, hx - 2, hz - 2, hx + 6, hz + 9);
        if (k > 0) {
            for (int i = 0; i < n; i++) {
                f[i] &= foot[k - 1][i];
            }
        }
        return f;
    }

    /** Whether a corner cut (chamfer or rounding) takes cell x, z of a rectangle ww x dd. */
    private static boolean cut(Level l, int x, int z, int ww, int dd) {
        int[][] corners = {{0, 0, 1}, {ww - 1, 0, 2}, {0, dd - 1, 4}, {ww - 1, dd - 1, 8}};
        for (int[] c : corners) {
            int ax = Math.abs(x - c[0]), az = Math.abs(z - c[1]);
            if ((l.cmask & c[2]) != 0 && ax + az < l.chamfer) {
                return true;
            }
            if ((l.rmask & c[2]) != 0 && ax < l.round && az < l.round) {
                double dx = l.round - 0.5 - ax, dz = l.round - 0.5 - az;
                if (dx * dx + dz * dz > l.round * l.round) {
                    return true;
                }
            }
        }
        return false;
    }

    private void fillRect(boolean[] f, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (ok(x, z)) {
                    f[ix(x, z)] = true;
                }
            }
        }
    }

    /** The outside wall: cells of the shape with any of the eight round them outside it. */
    private boolean[] edge(boolean[] f) {
        boolean[] e = new boolean[n];
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (!f[ix(x, z)] || inCore(x, z)) {
                    continue;
                }
                for (int dx = -1; dx <= 1 && !e[ix(x, z)]; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (!at(f, x + dx, z + dz)) {
                            e[ix(x, z)] = true;
                            break;
                        }
                    }
                }
            }
        }
        return e;
    }

    /** Floor at level k: inside, not the wall, not open to below, not the core or the hall. */
    private boolean floor(int k, int x, int z) {
        return at(foot[k], x, z) && !at(per[k], x, z) && !at(hole[k], x, z) && !inCore(x, z) && !inHall(x, z) && !hallSide(x, z);
    }

    /** The outdoor deck of level k (k = 3 the roof): what the level below has that this one doesn't. */
    private boolean deck(int k, int x, int z) {
        if (k == 3) {
            return at(foot[2], x, z) && !inCore(x, z) && !nearHall(x, z, 1);
        }
        boolean below = k == 0 ? inTower(x, z) : at(foot[k - 1], x, z);
        return below && !at(foot[k], x, z);
    }

    /** The deck's outer edge, which carries the rail. */
    private boolean deckRim(int k, int x, int z) {
        if (!deck(k, x, z)) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int xx = x + dx, zz = z + dz;
                boolean in = k == 0 ? inTower(xx, zz) : at(foot[Math.min(2, k - 1 + (k == 3 ? 1 : 0))], xx, zz);
                if (!in) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ building

    void build() {
        if (!placeHall()) {
            return;
        }
        for (int k = 0; k < 3; k++) {
            foot[k] = shape(k);
            per[k] = edge(foot[k]);
            keep[k] = new boolean[n];
            hole[k] = new boolean[n];
            rim[k] = new boolean[n];
        }
        for (int k = 0; k < 4; k++) {
            res[k] = new boolean[n];
            item[k] = new boolean[n];
            front[k] = new boolean[n];
        }
        for (int k = 0; k < 3; k++) {
            // the landing in front of the core's doors, the closet door behind, the ways round the stair hall
            for (int x = kx; x < kx + 8; x++) {
                for (int z = cz0 - 2; z < cz0; z++) {
                    keep[k][ix(x, z)] = true;
                }
            }
            keep[k][ix(kx + 5, cz0 + 10)] = true;
            for (int x = hx - 1; x <= hx + 5; x++) {
                for (int z = hz - 1; z <= hz + 8; z++) {
                    if (!inHall(x, z) && !hallSide(x, z) && at(foot[k], x, z) && !at(per[k], x, z)) {
                        keep[k][ix(x, z)] = true;
                    }
                }
            }
        }
        voids();
        reserve();
        for (int k = 0; k < 3; k++) {
            shell(k);
        }
        roof();
        hall();
        for (int[] c : columns) {
            int yb = y(c[2]) + 1, yt = y(c[3]) - 1;
            p.set(c[0], yb, c[1], "cv_column_base_white");
            p.fill(c[0], yb + 1, c[1], c[0], yt - 1, c[1], "cv_column_white");
            p.set(c[0], yt, c[1], "cv_column_cap_white");
        }
        for (int k = 0; k < 3; k++) {
            Level l = lv[k];
            for (int i = 0; i < l.feats.size(); i++) {
                feature(k, l.featKinds.get(i), cells(l.feats.get(i)));
            }
        }
        List<List<int[]>> doors = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            doors.add(terraceDoors(k));
        }
        for (int k = 0; k < 3; k++) {
            List<int[]> made = doors.get(k);
            if (furnished) {
                fitOut(k, made);
            }
            for (int[] dr : made) {
                Doors.abandoned(p, rng, dr[0], y(k) + 1, dr[1], Doors.SHOP, Floorplan.face(dr[2]), Doors.hinge(rng));
                p.set(dr[0], y(k) + 3, dr[1], Placer.inWall("window", dr[2] % 2 == 0));
            }
        }
        chandeliers();
        if (furnished) {
            for (int k = 0; k <= 3; k++) {
                furnishDeck(k);
            }
        }
        crown();
    }

    /** The rooms two and three storeys tall: no slab, a stone edge and a glass rail round them, bridges where needed. */
    private void voids() {
        for (int k = 1; k < 3; k++) {
            for (int[] q : lv[k].voids) {
                int[] r = cells(q);
                for (int x = r[0]; x <= r[2]; x++) {
                    for (int z = r[1]; z <= r[3]; z++) {
                        if (ok(x, z) && at(foot[k], x, z) && !at(per[k], x, z) && !inCore(x, z) && !keep[k][ix(x, z)] && !nearHall(x, z, 2)
                                && at(foot[k - 1], x, z) && !at(per[k - 1], x, z)) {
                            hole[k][ix(x, z)] = true;
                        }
                    }
                }
            }
            bridges(k);
            for (int z = -OUT; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (!hole[k][ix(x, z)]) {
                        continue;
                    }
                    for (int[] s : Floorplan.DIRS) {
                        if (floor(k, x + s[0], z + s[1]) || inHall(x + s[0], z + s[1])) {
                            rim[k][ix(x, z)] = true;
                        }
                    }
                }
            }
            // the rim is a stone edge with a rail: floor again for the slab, not for walking
            for (int i = 0; i < n; i++) {
                if (rim[k][i]) {
                    hole[k][i] = false;
                }
            }
        }
    }

    /** Glass bridges over a void where the stair's landing can't otherwise reach the core. */
    private void bridges(int k) {
        for (int guard = 0; guard < 4; guard++) {
            boolean[] seen = flood(k, false);
            int[] miss = null;
            for (int z = -OUT; z < d && miss == null; z++) {
                for (int x = 0; x < w; x++) {
                    if (keep[k][ix(x, z)] && floor(k, x, z) && !seen[ix(x, z)]) {
                        miss = new int[] {x, z};
                        break;
                    }
                }
            }
            if (miss == null) {
                return;
            }
            // cheapest way from the core's landing to it, counting each void cell crossed
            int[] cost = new int[n], prev = new int[n];
            java.util.Arrays.fill(cost, Integer.MAX_VALUE);
            java.util.Arrays.fill(prev, -1);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            for (int i = 0; i < n; i++) {
                if (seen[i]) {
                    cost[i] = 0;
                    q.add(i);
                }
            }
            while (!q.isEmpty()) {
                int i = q.pollFirst();
                int x = i % w, z = i / w - OUT;
                for (int[] s : Floorplan.DIRS) {
                    int xx = x + s[0], zz = z + s[1];
                    if (!ok(xx, zz) || !at(foot[k], xx, zz) || at(per[k], xx, zz) || inCore(xx, zz) || inHall(xx, zz) || hallSide(xx, zz)) {
                        continue;
                    }
                    int j = ix(xx, zz), c = cost[i] + (hole[k][j] ? 1 : 0);
                    if (c < cost[j]) {
                        cost[j] = c;
                        prev[j] = i;
                        if (hole[k][j]) {
                            q.addLast(j);
                        } else {
                            q.addFirst(j);
                        }
                    }
                }
            }
            int j = ix(miss[0], miss[1]);
            if (cost[j] == Integer.MAX_VALUE) {
                return;
            }
            for (int c = j; c >= 0; c = prev[c]) {
                if (hole[k][c]) {
                    hole[k][c] = false;
                    // two wide
                    int x = c % w, z = c / w - OUT;
                    int pc = prev[c] >= 0 ? prev[c] : c;
                    boolean alongX = pc % w != x;
                    int sx = x + (alongX ? 0 : 1), sz = z + (alongX ? 1 : 0);
                    if (ok(sx, sz)) {
                        hole[k][ix(sx, sz)] = false;
                    }
                }
            }
        }
    }

    /** The floor reachable from the core's landing at level k. */
    private boolean[] flood(int k, boolean deckToo) {
        boolean[] seen = new boolean[n];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = kx; x < kx + 8; x++) {
            int i = ix(x, cz0 - 1);
            if (floor(k, x, cz0 - 1)) {
                seen[i] = true;
                q.add(i);
            }
        }
        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % w, z = i / w - OUT;
            for (int[] s : Floorplan.DIRS) {
                int xx = x + s[0], zz = z + s[1];
                if (ok(xx, zz) && !seen[ix(xx, zz)] && floor(k, xx, zz)) {
                    seen[ix(xx, zz)] = true;
                    q.add(ix(xx, zz));
                }
            }
        }
        return seen;
    }

    /** The set pieces' cells: nothing else is put there. White columns carry each void's edge down to the floor under it. */
    private void reserve() {
        for (int k = 1; k < 3; k++) {
            for (int z = -OUT; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (!at(rim[k], x, z)) {
                        continue;
                    }
                    boolean rx = at(rim[k], x - 1, z) || at(rim[k], x + 1, z), rz = at(rim[k], x, z - 1) || at(rim[k], x, z + 1);
                    boolean spot = rx && rz || rx && Math.floorMod(x, 4) == 0 || rz && !rx && Math.floorMod(z, 4) == 0;
                    int b = k - 1;
                    if (!spot || !floor(b, x, z) || keep[b][ix(x, z)] || at(rim[b], x, z) || nearHall(x, z, 1)) {
                        continue;
                    }
                    res[b][ix(x, z)] = true;
                    columns.add(new int[] {x, z, b, k});
                }
            }
        }
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < lv[k].feats.size(); i++) {
                String kind = lv[k].featKinds.get(i);
                if (kind.equals("neon") || kind.equals("olives") || kind.equals("glassroof")) {
                    continue;
                }
                int[] r = cells(lv[k].feats.get(i));
                for (int x = r[0]; x <= r[2]; x++) {
                    for (int z = r[1]; z <= r[3]; z++) {
                        if (ok(x, z) && !keep[k][ix(x, z)] && !inCore(x, z) && !nearHall(x, z, 1)) {
                            res[k][ix(x, z)] = true;
                        }
                    }
                }
            }
        }
    }

    /** Level k's slab, its terrace, its glass walls. */
    private void shell(int k) {
        int y = y(k);
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (inCore(x, z)) {
                    continue;
                }
                int i = ix(x, z);
                if (foot[k][i]) {
                    if (hole[k][i]) {
                        p.set(x, y, z, "air");
                    } else if (per[k][i]) {
                        // a band of polished steel at every slab: the penthouse shines over the stone floors under it
                        p.set(x, y, z, clad);
                    } else if (rim[k][i]) {
                        p.set(x, y, z, trim);
                    } else {
                        p.set(x, y, z, z < 0 ? "of_pool_glass" : "marble_white");
                    }
                    if (rim[k][i]) {
                        rail(x, y + 1, z, k);
                    }
                    if (per[k][i]) {
                        glassWall(x, y, z, k);
                    }
                } else if (deck(k, x, z)) {
                    p.set(x, y, z, deckRim(k, x, z) ? trim : "of_deck");
                    if (deckRim(k, x, z)) {
                        deckRail(x, y + 1, z, k);
                    }
                }
            }
        }
        // a jutting glass box hangs on steel beams back into the slab
        if (k == 0 && lv[0].bay != null) {
            for (int x = 0; x < w; x++) {
                if (at(foot[0], x, -1) && x % 4 == 1) {
                    for (int z = -OUT; z <= 1; z++) {
                        if (at(foot[0], x, z)) {
                            p.set(x, y - 1, z, "steel_mullion");
                        }
                    }
                }
            }
        }
    }

    /** A void's rail on its stone edge, run along the edge; a post where the edge turns. */
    private void rail(int x, int y, int z, int k) {
        boolean fx = floor(k, x - 1, z) || floor(k, x + 1, z), fz = floor(k, x, z - 1) || floor(k, x, z + 1);
        boolean hx2 = at(hole[k], x - 1, z) || at(hole[k], x + 1, z) || at(rim[k], x - 1, z) || at(rim[k], x + 1, z);
        if (fx && fz) {
            p.set(x, y, z, trim);
        } else if (fz || hx2 && !fx) {
            p.set(x, y, z, "tx_glass_rail[axis=x]");
        } else {
            p.set(x, y, z, "tx_glass_rail[axis=z]");
        }
    }

    /** A terrace's rail along its outer edge. */
    private void deckRail(int x, int y, int z, int k) {
        boolean ex = deckRim(k, x - 1, z) || deckRim(k, x + 1, z) || at(per[Math.max(0, Math.min(2, k - 1))], x - 1, z) && k > 0
                || at(per[Math.max(0, Math.min(2, k - 1))], x + 1, z) && k > 0;
        boolean ez = deckRim(k, x, z - 1) || deckRim(k, x, z + 1);
        if (ex == ez) {
            p.set(x, y, z, trim);
        } else {
            p.set(x, y, z, "tx_glass_rail[axis=" + (ex ? "x" : "z") + "]");
        }
    }

    /** Glass floor to ceiling between slim piers; stone at the corners. */
    private void glassWall(int x, int y, int z, int k) {
        boolean ox = !at(foot[k], x - 1, z) || !at(foot[k], x + 1, z), oz = !at(foot[k], x, z - 1) || !at(foot[k], x, z + 1);
        for (int r = 1; r <= 3; r++) {
            String b;
            if (ox && oz) {
                b = Math.floorMod(x + z, 2) == 0 ? trim : "glass_spandrel";
            } else if (!ox && !oz) {
                b = trim;
            } else {
                boolean alongX = oz;
                int along = alongX ? x : z;
                b = Math.floorMod(along, 4) == 0 ? "steel_mullion" : Placer.inWall(rng.nextDouble() < 0.05 ? "window_broken"
                        : rng.nextDouble() < lit + 0.25 ? "window_lit" : "window", alongX);
            }
            p.set(x, y + r, z, b);
        }
    }

    /** The roof over the top level: a deck with a rail all round, the lantern over the stair hall. */
    private void roof() {
        int y = y(3);
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (!at(foot[2], x, z) || inCore(x, z)) {
                    continue;
                }
                boolean edge = at(per[2], x, z);
                p.set(x, y, z, edge ? trim : "of_deck");
                if (edge) {
                    boolean ox = !at(foot[2], x - 1, z) || !at(foot[2], x + 1, z), oz = !at(foot[2], x, z - 1) || !at(foot[2], x, z + 1);
                    p.set(x, y + 1, z, ox == oz ? trim : "tx_glass_rail[axis=" + (oz ? "x" : "z") + "]");
                }
            }
        }
        // stone finials on the parapet's corners
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (at(per[2], x, z)) {
                    boolean ox = !at(foot[2], x - 1, z) || !at(foot[2], x + 1, z), oz = !at(foot[2], x, z - 1) || !at(foot[2], x, z + 1);
                    if (ox && oz) {
                        p.set(x, y + 2, z, "of_finial");
                    }
                }
            }
        }
        // the core's plant room in stone, the same as the crown on it
        for (int yy = y + 1; yy <= y + 5; yy++) {
            for (int x = kx; x < kx + 8; x++) {
                for (int z = cz0; z < cz0 + 10; z++) {
                    if ("concrete_dark".equals(p.get(x, yy, z))) {
                        p.set(x, yy, z, (x + z + yy) % 5 == 0 && yy == y + 3 && (x == kx || x == kx + 7) ? Placer.inWall("window_lit", false) : "stone_trim");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the grand stair

    /**
     * Two flights in the hall: up lane A (x hx..hx+1) from the near landing to the far landing on
     * level 2, back up lane B (hx+3..hx+4) to the near landing on level 3, a marble wall between.
     * No slab over a flight within four of a tread; a glass lantern over the hall on the roof.
     */
    private void hall() {
        int y0 = y(0), y1 = y(1), y2 = y(2), y3 = y(3);
        String stone = "marble_white";
        for (int x = hx; x <= hx + 4; x++) {
            for (int z = hz; z <= hz + 7; z++) {
                boolean laneA = x <= hx + 1, laneB = x >= hx + 3, mid = x == hx + 2, flight = z >= hz + 2 && z <= hz + 5;
                p.set(x, y0, z, stone);
                // level 2: open over lane A, solid under lane B and the landings
                p.set(x, y1, z, laneA && flight ? "air" : stone);
                // level 3: open over both flights
                p.set(x, y2, z, flight && !mid ? "air" : stone);
                for (int yy = y0 + 1; yy < y3; yy++) {
                    if (yy != y1 && yy != y2) {
                        p.set(x, yy, z, "air");
                    }
                }
                if (mid && flight) {
                    p.fill(x, y0 + 1, z, x, y2, z, stone);
                    p.set(x, y2 + 1, z, "tx_glass_rail[axis=z]");
                }
            }
        }
        for (int z = hz + 2; z <= hz + 5; z++) {
            int s = z - hz - 2;
            for (int x = hx; x <= hx + 1; x++) {
                p.fill(x, y0 + 1, z, x, y0 + s, z, stone);
                p.set(x, y0 + 1 + s, z, "home_stairs_carpet[facing=south]");
            }
            int t = hz + 5 - z;
            for (int x = hx + 3; x <= hx + 4; x++) {
                p.fill(x, y1 + 1, z, x, y1 + t, z, stone);
                p.set(x, y1 + 1 + t, z, "home_stairs_carpet[facing=north]");
            }
        }
        // the side walls beside the flights, panelled, up to the lantern
        for (int z = hz + 2; z <= hz + 5; z++) {
            for (int x : new int[] {hx - 1, hx + 5}) {
                p.fill(x, y0 + 1, z, x, y3 - 1, z, "home_wall_panelling");
                p.set(x, y1, z, stone);
                p.set(x, y2, z, stone);
            }
        }
        // rails where a landing looks down a flight's void
        for (int x = hx; x <= hx + 1; x++) {
            p.set(x, y1 + 1, hz + 1, "tx_glass_rail[axis=x]");
            p.set(x, y2 + 1, hz + 1, "tx_glass_rail[axis=x]");
            p.set(x, y2 + 1, hz + 6, "tx_glass_rail[axis=x]");
        }
        for (int x = hx + 3; x <= hx + 4; x++) {
            p.set(x, y2 + 1, hz + 6, "tx_glass_rail[axis=x]");
        }
        // under lane B on the ground floor: a bust in its alcove
        p.set(hx + 4, y0 + 1, hz + 3, "mu_bust[facing=west]");
        p.set(hx + 3, y0 + 1, hz + 5, "pk_planter[facing=north]");
        // the lantern: glass walls two high round the hall, a glass roof, a chandelier down the middle
        for (int x = hx - 1; x <= hx + 5; x++) {
            for (int z = hz - 1; z <= hz + 8; z++) {
                boolean ring = x == hx - 1 || x == hx + 5 || z == hz - 1 || z == hz + 8;
                boolean corner = (x == hx - 1 || x == hx + 5) && (z == hz - 1 || z == hz + 8);
                for (int yy = y3; yy <= y3 + 1; yy++) {
                    p.set(x, yy, z, !ring ? "air" : corner ? trim : Placer.inWall("window_lit", z == hz - 1 || z == hz + 8));
                }
                p.set(x, y3 + 2, z, ring ? trim : "of_pool_glass");
            }
        }
        p.set(hx + 2, y3 + 1, hz + 3, "tr_chandelier");
        p.set(hx + 2, y3 + 1, hz + 4, "tr_chandelier");
    }

    // ------------------------------------------------------------------ set pieces

    private void feature(int k, String kind, int[] r) {
        int y = y(k);
        boolean tall = k < 2 && lv[k + 1] != null && hole[k + 1] != null && anyHole(k + 1, r);
        switch (kind) {
            case "stage" -> stage(k, y, r, tall);
            case "runway" -> {
                for (int x = r[0]; x <= r[2]; x++) {
                    for (int z = r[1]; z <= r[3]; z++) {
                        if (resAt(k, x, z)) {
                            p.set(x, y, z, "of_dance_" + "abcd".charAt(Math.floorMod(x + 2 * z, 4)));
                        }
                    }
                }
            }
            case "dance" -> dance(k, y, r, tall);
            case "pool", "lappool" -> pool(k, y, r, kind.equals("pool"), tall);
            case "garden" -> garden(k, y, r, tall);
            case "pond" -> pond(k, y, r);
            case "cars" -> cars(k, y, r);
            case "carlift" -> carLift(k, y, r);
            case "tub", "plunge" -> tub(k, y, r, kind.equals("plunge"));
            case "glassroof" -> {
                for (int x = r[0]; x <= r[2]; x++) {
                    for (int z = r[1]; z <= r[3]; z++) {
                        if (ok(x, z) && deck(k, x, z) && !deckRim(k, x, z)) {
                            p.set(x, y, z, "of_pool_glass");
                        }
                    }
                }
            }
            case "olives" -> {
                for (int x = r[0] + 1; x < r[2]; x += 2) {
                    for (int z = r[1] + 1; z < r[3]; z += 2) {
                        deckItem(k, x, z, "olive");
                    }
                }
            }
            case "dome" -> dome(r);
            default -> { }
        }
    }

    private boolean anyHole(int k, int[] r) {
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                if (at(hole[k], x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean resAt(int k, int x, int z) {
        return ok(x, z) && res[k][ix(x, z)];
    }

    /** The club's stage: a lit floor, three brass poles, speakers at the back, neon on the glass behind. */
    private void stage(int k, int y, int[] r, boolean tall) {
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                if (resAt(k, x, z) && !at(per[k], x, z)) {
                    p.set(x, y, z, "of_dance_" + "abcd".charAt(Math.floorMod(x * 3 + z, 4)) + (rng.nextDouble() < 0.2 ? "_off" : ""));
                }
            }
        }
        int cz = (r[1] + r[3]) / 2 + 1, top = tall ? y + FLOOR + 3 : y + 3;
        for (int i = 1; i <= 3; i++) {
            int x = r[0] + (r[2] - r[0]) * i / 4;
            if (resAt(k, x, cz) && !at(per[k], x, cz)) {
                p.fill(x, y + 1, cz, x, top - 1, cz, "of_dance_pole");
                p.set(x, top, cz, "of_pole_top");
            }
        }
        for (int x : new int[] {r[0] + 1, r[2] - 1}) {
            int z = r[1] + 1;
            while (z < r[3] && (at(per[k], x, z) || !resAt(k, x, z))) {
                z++;
            }
            if (resAt(k, x, z)) {
                p.set(x, y + 1, z, "of_speaker[facing=south]");
            }
        }
        p.set((r[0] + r[2]) / 2, top, cz + 1, "of_mirrorball");
    }

    /** The sunken club's dance floor under the mirror ball, the DJ booth at its head, speaker stacks. */
    private void dance(int k, int y, int[] r, boolean tall) {
        for (int x = r[0] + 1; x < r[2]; x++) {
            for (int z = r[1] + 1; z <= r[3]; z++) {
                if (resAt(k, x, z)) {
                    p.set(x, y, z, "of_dance_" + "abcd".charAt(Math.floorMod(x * 3 + z * 5, 4)) + (rng.nextDouble() < 0.3 ? "_off" : ""));
                }
            }
        }
        int cx = (r[0] + r[2]) / 2;
        for (int x = cx - 1; x <= cx + 1; x++) {
            if (resAt(k, x, r[1])) {
                p.set(x, y + 1, r[1], "of_dj_booth[facing=south]");
            }
        }
        for (int[] c : new int[][] {{r[0], r[1] + 1}, {r[2], r[1] + 1}, {r[0], r[3]}, {r[2], r[3]}}) {
            if (resAt(k, c[0], c[1])) {
                p.set(c[0], y + 1, c[1], "of_speaker[facing=" + (c[1] == r[1] + 1 ? "south" : "north") + "]");
            }
        }
        int top = tall ? y + FLOOR + 3 : y + 3;
        p.set(cx, top, (r[1] + r[3]) / 2, "of_mirrorball");
        if (tall) {
            p.set(cx - 3, top, (r[1] + r[3]) / 2, "of_mirrorball");
            p.set(cx + 3, top, (r[1] + r[3]) / 2, "of_mirrorball");
        }
    }

    /**
     * A pool set in the floor: glass under it (the floor below looks up through the water), the
     * coping a step up, white columns round the hall when it's two storeys tall.
     */
    private void pool(int k, int y, int[] r, boolean columns, boolean tall) {
        int x0 = r[0] + 1, z0 = r[1] + 1, x1 = r[2] - 1, z1 = r[3] - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!resAt(k, x, z)) {
                    continue;
                }
                boolean ring = x == x0 || x == x1 || z == z0 || z == z1 || !resAt(k, x - 1, z) || !resAt(k, x + 1, z) || !resAt(k, x, z - 1)
                        || !resAt(k, x, z + 1);
                p.set(x, y - 1, z, "of_pool_glass");
                p.set(x, y, z, ring ? "of_pool_tile" : "minecraft:water");
                p.set(x, y + 1, z, ring ? "of_coping" : "minecraft:water");
            }
        }
        if (!columns) {
            // loungers along the pool's side, on the walk round it
            for (int x = x0 + 1; x < x1; x += 3) {
                if (resAt(k, x, r[1]) && !at(per[k], x, r[1])) {
                    p.set(x, y + 1, r[1], "of_lounger[facing=south]");
                }
            }
            return;
        }
        int top = tall ? y + FLOOR + 3 : y + 3;
        for (int x = r[0]; x <= r[2]; x += 4) {
            for (int z : new int[] {r[1], r[3]}) {
                if (resAt(k, x, z) && !at(per[k], x, z)) {
                    p.set(x, y + 1, z, "cv_column_base_white");
                    p.fill(x, y + 2, z, x, top - 1, z, "cv_column_white");
                    p.set(x, top, z, "cv_column_cap_white");
                }
            }
        }
    }

    /** The winter garden: grass and stone paths, palms up into the void, ferns, benches, a fountain. */
    private void garden(int k, int y, int[] r, boolean tall) {
        int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                if (!resAt(k, x, z) || at(per[k], x, z)) {
                    continue;
                }
                boolean path = x == r[0] || x == r[2] || z == r[1] || z == r[3] || x == cx || z == cz;
                p.set(x, y, z, path ? "pt_marble" : "bp_grass_a");
                if (path) {
                    continue;
                }
                if (Math.floorMod(x - r[0], 4) == 2 && Math.floorMod(z - r[1], 3) == 2) {
                    int h = tall ? 5 : 2;
                    p.fill(x, y + 1, z, x, y + h, z, "mu_palm_trunk");
                    p.set(x, y + h + 1, z, "mu_palm_crown");
                } else if (rng.nextDouble() < 0.45) {
                    p.set(x, y + 1, z, "mu_fern");
                } else if (rng.nextDouble() < 0.12 && (x == cx - 1 || x == cx + 1)) {
                    p.set(x, y + 1, z, "mu_bench[facing=" + (x < cx ? "east" : "west") + "]");
                }
            }
        }
        if (tall) {
            // a glass roof over it on the terrace above, the garden's sky
            for (int x = r[0]; x <= r[2]; x++) {
                for (int z = r[1]; z <= r[3]; z++) {
                    if (at(hole[k + 1], x, z) && !at(foot[2], x, z)) {
                        p.set(x, y(2), z, "of_pool_glass");
                    }
                }
            }
        }
    }

    /** The koi pond: water sunk into the floor, a stone rim flush with it, the fountain on its island. */
    private void pond(int k, int y, int[] r) {
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                if (!ok(x, z)) {
                    continue;
                }
                boolean ring = x == r[0] || x == r[2] || z == r[1] || z == r[3];
                for (int yy = y + 1; yy <= y + 2; yy++) {
                    p.set(x, yy, z, "air");
                }
                p.set(x, y, z, ring ? "of_coping" : "minecraft:water");
                if (!ring) {
                    p.set(x, y - 1, z, "of_pool_tile");
                }
            }
        }
        int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
        p.set(cx, y, cz, "of_coping");
        p.set(cx, y + 1, cz, "gs_fountain[facing=south]");
    }

    /** The car gallery: dust-sheeted classics in rows, a few real ones parked between. */
    private void cars(int k, int y, int[] r) {
        int slot = 0;
        for (int z = r[1] + 1; z + 1 <= r[3]; z += 4) {
            for (int x = r[0] + 1; x + 4 <= r[2]; x += 6) {
                boolean clear = true;
                for (int xx = x; xx <= x + 4 && clear; xx++) {
                    for (int zz = z; zz <= z + 1; zz++) {
                        clear &= resAt(k, xx, zz) && !at(per[k], xx, zz) && !at(hole[k], xx, zz);
                    }
                }
                if (!clear) {
                    continue;
                }
                // a plinth of polished floor under each
                for (int xx = x; xx <= x + 4; xx++) {
                    for (int zz = z; zz <= z + 1; zz++) {
                        p.set(xx, y, zz, "lobby_floor_dark");
                    }
                }
                if (slot++ % 2 == 0) {
                    p.vehicle(x + 2.5, y + 1, z + 1.0, 90 + (float) (rng.nextDouble() * 6 - 3), "sports_coupe,convertible,coupe,limousine@abandoned",
                            rng.nextLong());
                } else {
                    p.set(x + 1, y + 1, z, "of_car_sheet_b[axis=x]");
                    p.set(x + 2, y + 1, z, "of_car_sheet_m[axis=x]");
                    p.set(x + 3, y + 1, z, "of_car_sheet_f[axis=x]");
                }
            }
        }
    }

    /** The car lift from the street: a steel platform, a car on it under its sheet, the posts. */
    private void carLift(int k, int y, int[] r) {
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                if (resAt(k, x, z) && !at(per[k], x, z)) {
                    p.set(x, y, z, "of_car_lift");
                }
            }
        }
        int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
        if (resAt(k, cx, cz - 1) && resAt(k, cx, cz + 1)) {
            p.set(cx, y + 1, cz - 1, "of_car_sheet_f[axis=z]");
            p.set(cx, y + 1, cz, "of_car_sheet_m[axis=z]");
            p.set(cx, y + 1, cz + 1, "of_car_sheet_b[axis=z]");
        }
    }

    /** A hot tub raised on the terrace (or a plunge pool with a glass floor over the rooms below). */
    private void tub(int k, int y, int[] r, boolean glass) {
        int dk = k + 1;  // the terrace of the level above this spec's
        int ty = y(Math.min(3, dk));
        // a tub belongs on the deck round the top level, or on the roof
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = r[1]; z <= r[3]; z++) {
                if (!ok(x, z)) {
                    continue;
                }
                int deckK = deck(k, x, z) && !deckRim(k, x, z) ? k : -1;
                if (deckK < 0) {
                    continue;
                }
                boolean ring = x == r[0] || x == r[2] || z == r[1] || z == r[3];
                p.set(x, y, z, glass ? "of_pool_glass" : "of_pool_tile");
                p.set(x, y + 1, z, ring ? glass ? "of_pool_glass" : "of_coping" : "minecraft:water");
                item[k][ix(x, z)] = true;
            }
        }
        if (ty < 0) {
            return;
        }
    }

    /** The observatory: a brass-ribbed glass dome on the roof over the top room, the telescope under it. */
    private void dome(int[] r) {
        int cx = (r[0] + r[2]) / 2, cz = (r[1] + r[3]) / 2;
        int rad = Math.min(5, Math.min(r[2] - r[0], r[3] - r[1]) / 2 - 1);
        if (rad < 3) {
            return;
        }
        int y = y(3);
        for (int x = cx - rad; x <= cx + rad; x++) {
            for (int z = cz - rad; z <= cz + rad; z++) {
                double rr = Math.hypot(x - cx, z - cz);
                if (rr <= rad + 0.5 && (!at(foot[2], x, z) || at(per[2], x, z) || inCore(x, z) || nearHall(x, z, 1))) {
                    return;
                }
            }
        }
        for (int x = cx - rad; x <= cx + rad; x++) {
            for (int z = cz - rad; z <= cz + rad; z++) {
                double rr = Math.hypot(x - cx, z - cz);
                if (rr > rad + 0.5) {
                    continue;
                }
                boolean rib = x == cx || z == cz || Math.abs(x - cx) == Math.abs(z - cz);
                p.set(x, y, z, rr > rad - 0.5 ? trim : "air");
                for (int h = 1; h <= rad + 1; h++) {
                    double sr = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz) + (double) h * h);
                    if (Math.abs(sr - rad) < 0.55) {
                        p.set(x, y + h, z, rib ? "steel_mullion" : "of_pool_glass");
                    }
                }
            }
        }
        p.set(cx, y + rad + 1, cz, "aviation_light");
        p.set(cx, y(2) + 1, cz, "of_telescope[facing=north]");
    }

    // ------------------------------------------------------------------ terraces

    /** Glass doors out to the terrace on each run of wall, the cells either side kept clear. Each: x, z, facing in. */
    private List<int[]> terraceDoors(int k) {
        List<int[]> out = new ArrayList<>();
        List<int[]> cand = new ArrayList<>();
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (!at(per[k], x, z)) {
                    continue;
                }
                for (int s = 0; s < 4; s++) {
                    int[] o = Floorplan.step(x, z, s), in = Floorplan.step(x, z, s + 2);
                    int[] a = Floorplan.step(x, z, s + 1), b = Floorplan.step(x, z, s + 3);
                    if (deck(k, o[0], o[1]) && !deckRim(k, o[0], o[1]) && !resAt(Math.min(k, 2), o[0], o[1]) && floor(k, in[0], in[1])
                            && !resAt(k, in[0], in[1]) && !at(rim[k], in[0], in[1]) && jamb(k, a) && jamb(k, b)
                            && deck(k, Floorplan.step(o[0], o[1], s)[0], Floorplan.step(o[0], o[1], s)[1])) {
                        boolean straight = at(per[k], a[0], a[1]) && at(per[k], b[0], b[1]);
                        cand.add(new int[] {x, z, s + 2 & 3, in[0], in[1], o[0], o[1], straight ? 1 : 0});
                    }
                }
            }
        }
        java.util.Collections.shuffle(cand, new java.util.Random(rng.nextLong()));
        // every terrace reachable: at least one door to each piece, then more along the runs
        int[] comp = deckPieces(k);
        boolean[] served = new boolean[n + 1];
        for (int pass = 0; pass < 3; pass++) {
            for (int[] c : cand) {
                int piece = comp[ix(c[5], c[6])];
                // first the straight runs of wall, then a cut corner's slant for a piece still without a way out
                if (pass < 2 && served[piece] || pass != 1 && c[7] == 0) {
                    continue;
                }
                boolean far = true;
                for (int[] dd : out) {
                    far &= Math.abs(dd[0] - c[0]) + Math.abs(dd[1] - c[1]) >= 7;
                }
                if (!far || pass == 2 && out.size() > 10) {
                    continue;
                }
                out.add(c);
                served[piece] = true;
                for (int[] s : Floorplan.DIRS) {
                    if (ok(c[5] + s[0], c[6] + s[1])) {
                        front[k][ix(c[5] + s[0], c[6] + s[1])] = true;
                    }
                }
                front[k][ix(c[5], c[6])] = true;
            }
        }
        // a corner too small for a door: a planted bed behind the glass instead
        for (int i = 0; i < n; i++) {
            int x = i % w, z = i / w - OUT;
            if (comp[i] != 0 && !served[comp[i]]) {
                p.set(x, y(k), z, "bp_grass_a");
                p.set(x, y(k) + 1, z, Math.floorMod(x * 7 + z * 3, 5) == 0 ? "of_tree_pot" : "mu_fern");
                if (Math.floorMod(x * 7 + z * 3, 5) == 0) {
                    p.set(x, y(k) + 2, z, "of_tree_crown");
                }
                item[k][i] = true;
            }
        }
        return out;
    }

    /** A door's side: the wall going on, or the outside where the wall turns a corner. */
    private boolean jamb(int k, int[] c) {
        return at(per[k], c[0], c[1]) || !at(foot[k], c[0], c[1]) && !inCore(c[0], c[1]);
    }

    /** Which piece of terrace each deck cell is on. */
    private int[] deckPieces(int k) {
        int[] comp = new int[n];
        int next = 1;
        for (int i = 0; i < n; i++) {
            int x = i % w, z = i / w - OUT;
            if (comp[i] != 0 || !deck(k, x, z) || deckRim(k, x, z)) {
                continue;
            }
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(i);
            comp[i] = next;
            while (!q.isEmpty()) {
                int c = q.poll();
                int cx = c % w, cz = c / w - OUT;
                for (int[] s : Floorplan.DIRS) {
                    int xx = cx + s[0], zz = cz + s[1];
                    if (ok(xx, zz) && comp[ix(xx, zz)] == 0 && deck(k, xx, zz) && !deckRim(k, xx, zz)) {
                        comp[ix(xx, zz)] = next;
                        q.add(ix(xx, zz));
                    }
                }
            }
            next++;
        }
        return comp;
    }

    /** Furniture on a terrace (k = 3 the roof): along the rail, clear of the glass, the doors and each other. */
    private void furnishDeck(int k) {
        int y = y(k);
        int i = rng.nextInt(6);
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (!deck(k, x, z) || deckRim(k, x, z)) {
                    continue;
                }
                boolean byRail = false;
                for (int s = 0; s < 4; s++) {
                    int[] o = Floorplan.step(x, z, s);
                    byRail |= deckRim(k, o[0], o[1]);
                }
                boolean grid = k == 3 && Math.floorMod(x, 5) == 2 && Math.floorMod(z, 5) == 2;
                if (!byRail && !grid) {
                    continue;
                }
                String[] kinds = {"olive", "lounger", "parasol", "sofa", "olive", "daybed", "fire"};
                if (deckItem(k, x, z, kinds[i % kinds.length])) {
                    i += 1 + rng.nextInt(2);
                }
            }
        }
    }

    /** One piece on the deck if it leaves the deck joined up; true if placed. */
    private boolean deckItem(int k, int x, int z, String kind) {
        int y = y(k);
        if (!ok(x, z) || !deck(k, x, z) || deckRim(k, x, z) || item[k][ix(x, z)] || front[k][ix(x, z)] || resAt(Math.min(k, 2), x, z)) {
            return false;
        }
        // the lane by the glass and round the core stays clear; nothing beside another piece
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int xx = x + dx, zz = z + dz;
                if (!ok(xx, zz)) {
                    continue;
                }
                if (k < 3 && at(per[k], xx, zz) || inCore(xx, zz) || nearHall(xx, zz, 1) && k == 3 || item[k][ix(xx, zz)]) {
                    return false;
                }
            }
        }
        if (!"air".equals(String.valueOf(p.get(x, y + 1, z))) && p.get(x, y + 1, z) != null) {
            return false;
        }
        item[k][ix(x, z)] = true;
        if (!deckJoined(k)) {
            item[k][ix(x, z)] = false;
            return false;
        }
        int look = 0;
        for (int s = 0; s < 4; s++) {
            int[] o = Floorplan.step(x, z, s);
            if (deckRim(k, o[0], o[1])) {
                look = s;
            }
        }
        String face = Floorplan.face(look);
        switch (kind) {
            case "olive" -> {
                p.set(x, y + 1, z, "of_tree_pot");
                p.set(x, y + 2, z, "of_tree_crown");
            }
            case "lounger" -> p.set(x, y + 1, z, "of_lounger[facing=" + face + "]");
            case "parasol" -> {
                p.set(x, y + 1, z, "of_parasol_pole");
                p.set(x, y + 2, z, "of_parasol_pole");
                p.set(x, y + 3, z, "of_parasol_top");
            }
            case "sofa" -> p.set(x, y + 1, z, "of_out_sofa[facing=" + face + "]");
            case "daybed" -> p.set(x, y + 1, z, "of_daybed[facing=" + face + "]");
            default -> p.set(x, y + 1, z, "of_fire_pit");
        }
        return true;
    }

    /** Whether the deck's free cells still make the same pieces they did with nothing on them. */
    private boolean deckJoined(int k) {
        int[] comp = deckPieces(k);
        int pieces = 0;
        for (int c : comp) {
            pieces = Math.max(pieces, c);
        }
        boolean[] seen = new boolean[n];
        int found = 0;
        for (int i = 0; i < n; i++) {
            if (comp[i] == 0 || item[k][i] || seen[i]) {
                continue;
            }
            found++;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(i);
            seen[i] = true;
            while (!q.isEmpty()) {
                int c = q.poll();
                int cx = c % w, cz = c / w - OUT;
                for (int[] s : Floorplan.DIRS) {
                    int xx = cx + s[0], zz = cz + s[1];
                    if (ok(xx, zz) && !seen[ix(xx, zz)] && comp[ix(xx, zz)] != 0 && !item[k][ix(xx, zz)]) {
                        seen[ix(xx, zz)] = true;
                        q.add(ix(xx, zz));
                    }
                }
            }
        }
        return found <= pieces;
    }

    // ------------------------------------------------------------------ the rooms

    /** Level k's rooms and open floor, through the floor planner; the doors out to the terraces kept clear. */
    private void fitOut(int k, List<int[]> doors) {
        int D = d + OUT;
        boolean[] open = new boolean[n];
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                open[ix(x, z)] = floor(k, x, z) && !at(rim[k], x, z) && !res[k][ix(x, z)] && !keep[k][ix(x, z)];
            }
        }
        Floorplan f = new Floorplan(p.shifted(0, 0, -OUT), rng, w, D, y(k), FLOOR - 1, new Floorplan.Site() {
            @Override
            public boolean open(int x, int zz) {
                return open[zz * w + x];
            }

            @Override
            public boolean core(int x, int zz) {
                return inCore(x, zz - OUT);
            }

            @Override
            public boolean keep(int x, int zz) {
                return keep[k][zz * w + x] && floor(k, x, zz - OUT);
            }
        }, lit);
        boolean[] inner = new boolean[n];
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                inner[ix(x, z)] = at(foot[k], x, z) && !at(per[k], x, z) && !inCore(x, z) && !open[ix(x, z)] && !keep[k][ix(x, z)];
            }
        }
        f.inner = inner;
        List<int[]> rects = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        Level l = lv[k];
        for (int i = 0; i < l.rooms.size(); i++) {
            int[] r = trimRoom(open, cells(l.rooms.get(i)));
            if (r != null) {
                rects.add(new int[] {r[0], r[1] + OUT, r[2], r[3] + OUT});
                kinds.add(l.kinds.get(i));
            }
        }
        List<int[]> clear = new ArrayList<>();
        for (int[] dr : doors) {
            clear.add(new int[] {dr[3], dr[4] + OUT});
        }
        Floors.penthouseLevel(f, rng, rects, kinds, l.open, clear);
        // a door only where the planner left the way in clear
        doors.removeIf(dr -> !Floorplan.walkable(f.at(dr[3], dr[4] + OUT)) && open[ix(dr[3], dr[4])]);
        if (layout == 6) {
            neon(k, f);
        }
    }

    /** A room rectangle shrunk until all of it is open floor; null if too little is left. */
    private int[] trimRoom(boolean[] open, int[] r) {
        int x0 = r[0], z0 = r[1], x1 = r[2], z1 = r[3];
        while (x1 - x0 >= 2 && z1 - z0 >= 2) {
            int[] bad = {badLine(open, x0, z0, x1, z0), badLine(open, x1, z0, x1, z1), badLine(open, x0, z1, x1, z1), badLine(open, x0, z0, x0, z1)};
            int worst = 0;
            for (int s = 1; s < 4; s++) {
                if (bad[s] > bad[worst]) {
                    worst = s;
                }
            }
            if (bad[worst] == 0) {
                return new int[] {x0, z0, x1, z1};
            }
            switch (worst) {
                case 0 -> z0++;
                case 1 -> x1--;
                case 2 -> z1--;
                default -> x0++;
            }
        }
        return null;
    }

    private int badLine(boolean[] open, int x0, int z0, int x1, int z1) {
        int b = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                b += ok(x, z) && open[ix(x, z)] ? 0 : 1;
            }
        }
        return b;
    }

    /** Pink and blue neon tubes high on the glass of the club. */
    private void neon(int k, Floorplan f) {
        int y = y(k) + 3, i = 0;
        for (int z = -OUT; z < d; z++) {
            for (int x = 0; x < w; x++) {
                if (!floor(k, x, z)) {
                    continue;
                }
                for (int s = 0; s < 4; s++) {
                    int[] o = Floorplan.step(x, z, s);
                    if (at(per[k], o[0], o[1]) && (i++ % 3 == 0) && "air".equals(String.valueOf(p.get(x, y, z)))) {
                        p.set(x, y, z, (i % 2 == 0 ? "of_neon_pink" : "of_neon_blue") + "[facing=" + Floorplan.face(s + 2) + "]");
                        break;
                    }
                }
            }
        }
    }

    /** Chandeliers hung at the top of the tall rooms. */
    private void chandeliers() {
        for (int k = 1; k < 3; k++) {
            for (int z = -OUT; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (!at(hole[k], x, z) || k == 1 && at(hole[2], x, z) || Math.floorMod(x, 5) != 2 || Math.floorMod(z, 5) != 2) {
                        continue;
                    }
                    int yy = y(k + 1) - 1;
                    String b = p.get(x, yy, z);
                    if (b == null || b.equals("air")) {
                        p.set(x, yy, z, rng.nextDouble() < lit + 0.2 ? "tr_chandelier_on" : "tr_chandelier");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the crown

    /**
     * The crown, after the Chrysler's: stainless tiers stepping in over the core's plant room, each
     * face a sunburst of steel rays with lit glass between, eagles at the first ledge, a needle
     * spire with its warning light. The first tier overhangs the roof deck on four stone piers.
     */
    private void crown() {
        int y3 = y(3);
        int x0 = kx - 2, z0 = cz0 - 2, x1 = kx + 9, z1 = cz0 + 11;
        // the piers carrying the overhang down to the deck
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            if (at(foot[2], c[0], c[1]) && !nearHall(c[0], c[1], 1)) {
                p.fill(c[0], y3 + 1, c[1], c[0], y3 + 5, c[1], "stone_trim");
            }
        }
        int y = y3 + 6;
        int[] heights = {6, 6, 5, 5, 4};
        for (int t = 0; t < heights.length && x1 - x0 >= 3 && z1 - z0 >= 3; t++) {
            int h = heights[t];
            for (int yy = y; yy < y + h; yy++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean ex = x == x0 || x == x1, ez = z == z0 || z == z1;
                        if (yy == y) {
                            // the tier's floor: a steel soffit under the overhang, stone over the core
                            p.set(x, yy, z, ex || ez ? "stone_trim" : t == 0 && !inCore(x, z) ? clad : "air");
                            continue;
                        }
                        if (!ex && !ez) {
                            p.set(x, yy, z, yy == y + h - 1 ? clad : "air");
                            continue;
                        }
                        if (ex && ez) {
                            p.set(x, yy, z, clad);
                            continue;
                        }
                        boolean alongX = ez;
                        int len = alongX ? x1 - x0 + 1 : z1 - z0 + 1, a = alongX ? x - x0 : z - z0;
                        p.set(x, yy, z, sunburst(a, len, yy - y, h, alongX));
                    }
                }
            }
            if (t == 0) {
                // the eagles on the first ledge's corners, looking out over the city
                int ey = y + h;
                p.set(x0, ey, z0, "mu_eagle[facing=north]");
                p.set(x1, ey, z0, "mu_eagle[facing=east]");
                p.set(x0, ey, z1, "mu_eagle[facing=west]");
                p.set(x1, ey, z1, "mu_eagle[facing=south]");
            }
            y += h;
            x0++;
            z0++;
            x1--;
            z1--;
            // a stone finial on each corner of the ledge left as the next tier steps in
            if (t > 0) {
                for (int[] c : new int[][] {{x0 - 1, z0 - 1}, {x1 + 1, z0 - 1}, {x0 - 1, z1 + 1}, {x1 + 1, z1 + 1}}) {
                    p.set(c[0], y, c[1], "of_finial");
                }
            }
        }
        // the needle
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        p.fill(cx, y, cz, cx, y + 5, cz, "of_spire_a");
        p.fill(cx, y + 6, cz, cx, y + 11, cz, "of_spire_b");
        p.fill(cx, y + 12, cz, cx, y + 16, cz, "of_spire_c");
        p.fill(cx, y + 17, cz, cx, y + 19, cz, "of_spire_tip");
        p.set(cx, y + 20, cz, "aviation_light");
    }

    /**
     * One cell of a tier's face: a half-round arch on the face's base, its rim in steel, inside it
     * rays fanning out, steel and lit glass by turns; steel above the arch's shoulders.
     */
    private String sunburst(int a, int len, int row, int h, boolean alongX) {
        double cx = (len - 1) / 2.0, dx = a - cx, dy = row - 0.5;
        double r = Math.hypot(dx, dy), outer = Math.min(len / 2.0, h - 0.5);
        if (r > outer || row == h - 1) {
            return clad;
        }
        if (r > outer - 1.0) {
            return "stone_trim";
        }
        double ang = Math.atan2(dy, dx);
        int ray = (int) Math.floor(ang / (Math.PI / 9));
        return ray % 2 == 0 ? Placer.inWall("window_lit", alongX) : clad;
    }
}
