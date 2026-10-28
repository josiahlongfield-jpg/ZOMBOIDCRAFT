package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.LiftBuild;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Stairwell;
import java.util.SplittableRandom;

/**
 * What a landmark drawn block by block on its own lot shares with its buildings: the lot's
 * placer and random, the weathering ({@link Bldg}), whether anything still has power, and the
 * helpers for walls, partitions, doors, troffers, the mess on the floors, signs and pictures,
 * fire stairs in their shafts and lifts. {@link University} and {@link CivicCentre} build on it.
 */
abstract class Grounds {
    final Placer p;
    final SplittableRandom rng;
    final Bldg b;
    final int w, d;
    /** The generator still runs: some lights on, the clock lit. */
    final boolean power;

    Grounds(Placer p, SplittableRandom rng, int w, int d, double damage, double boarded, double powered) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.b = new Bldg(p, rng);
        b.damage = damage;
        b.boarded = boarded;
        this.power = rng.nextDouble() < powered;
    }

    void set(int x, int y, int z, String block) {
        p.set(x, y, z, block);
    }

    String get(int x, int y, int z) {
        return p.get(x, y, z);
    }

    boolean empty(int x, int y, int z) {
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    p.set(x, y, z, block);
                }
            }
        }
    }

    double chance() {
        return rng.nextDouble();
    }

    String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    static final String[] DIRS = {"north", "east", "south", "west"};

    String dir() {
        return DIRS[rng.nextInt(4)];
    }

    /** A wall along x at z from y0 + 1 up to y1 (the floor at y0 left alone). */
    void wallX(int x0, int x1, int z, int y0, int y1, String block) {
        fill(x0, y0 + 1, z, x1, y1, z, block);
    }

    void wallZ(int x, int z0, int z1, int y0, int y1, String block) {
        fill(x, y0 + 1, z0, x, y1, z1, block);
    }

    /** An inside partition along x on the floor at y, h storey high: the painted block over its dado. */
    void partX(int x0, int x1, int z, int y, int h, String lo, String hi) {
        fill(x0, y + 1, z, x1, y + 1, z, lo);
        fill(x0, y + 2, z, x1, y + h - 1, z, hi);
    }

    void partZ(int x, int z0, int z1, int y, int h, String lo, String hi) {
        fill(x, y + 1, z0, x, y + 1, z1, lo);
        fill(x, y + 2, z0, x, y + h - 1, z1, hi);
    }

    /** A door in a wall, the floor at y; the wall over it kept up to the next floor. */
    void door(int x, int y, int z, String type, String facing) {
        Doors.abandoned(p, rng, x, y + 1, z, type, facing, Doors.hinge(rng));
    }

    /** Double doors in a wall along x (at x and x + 1) or along z (at z and z + 1). */
    void doors(int x, int y, int z, String type, String facing) {
        if (facing.equals("north") || facing.equals("south")) {
            Doors.pair(p, rng, x, y + 1, z, type, facing);
        } else {
            boolean east = facing.equals("east");
            Doors.abandoned(p, rng, x, y + 1, z, type, facing, east ? "left" : "right");
            Doors.abandoned(p, rng, x, y + 1, z + 1, type, facing, east ? "right" : "left");
        }
    }

    /** A doorway with nothing in it, two blocks high (three if tall). */
    void opening(int x0, int x1, int z0, int z1, int y, boolean tall) {
        fill(x0, y + 1, z0, x1, y + (tall ? 3 : 2), z1, "air");
    }

    /** Troffers in the slab at ys over a room every three blocks: slab blocks swapped for their lamp kind. */
    void lamps(int x0, int z0, int x1, int z1, int ys, double on) {
        for (int x = x0 + 1; x <= x1 - 1; x += 3) {
            for (int z = z0 + 1; z <= z1 - 1; z += 3) {
                String s = get(x, ys, z);
                if (s != null && (s.startsWith("un_slab_carpet") || s.equals("un_slab_vct") || s.equals("un_slab_dorm") || s.equals("un_roof"))) {
                    String base = s.equals("un_slab_carpet_b") ? "un_slab_carpet" : s;
                    set(x, ys, z, base + "_lamp" + (power && chance() < on ? "_on" : ""));
                }
            }
        }
    }

    /** What's left on a floor: paper, blood, glass, books, a bag. */
    void mess(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (!empty(x, y + 1, z) || get(x, y, z) == null || chance() > density) {
                    continue;
                }
                double r = chance();
                set(x, y + 1, z, r < 0.45 ? "paper" : r < 0.6 ? "blood_splat" : r < 0.68 ? "glass_shards"
                        : r < 0.8 ? "home_books_stack_low" : r < 0.9 ? "trash_bag[facing=" + dir() + "]" : "home_blood_pool");
            }
        }
    }

    /** The kit the signs come from. */
    String signs() {
        return "un_sgn_";
    }

    /** A sign two wide on a wall: tile 0 at the viewer's left. */
    void sign(String key, int x, int y, int z, String facing) {
        int[] a = MallSigns.at(x, z, 2, 0, facing), c = MallSigns.at(x, z, 2, 1, facing);
        set(a[0], y, a[1], signs() + key + "_0[facing=" + facing + "]");
        set(c[0], y, c[1], signs() + key + "_1[facing=" + facing + "]");
    }

    /** A row of tiles from a picture cols wide (prefix + i), from x0/z0 along the wall, read facing. */
    void row(String prefix, int cols, int x0, int y, int z0, String facing, double missing) {
        for (int c = 0; c < cols; c++) {
            if (chance() < missing) {
                continue;
            }
            int[] a = MallSigns.at(x0, z0, cols, c, facing);
            set(a[0], y, a[1], prefix + c + "[facing=" + facing + "]");
        }
    }

    /** A picture cols by rows (tile i = row i / cols, column i % cols; row 0 at the top) from x0/z0, its bottom row at y. */
    void picture(String prefix, int cols, int rows, int x0, int y, int z0, String facing) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int[] a = MallSigns.at(x0, z0, cols, c, facing);
                set(a[0], y + rows - 1 - r, a[1], prefix + (r * cols + c) + "[facing=" + facing + "]");
            }
        }
    }

    /** One of the posters students put up, on a wall facing that way. */
    void poster(int x, int y, int z, String facing) {
        set(x, y, z, "un_poster_" + rng.nextInt(6) + "[facing=" + facing + "]");
    }

    /**
     * A fire stair in its own shaft: walls x0 .. x0 + 4 by z0 .. z0 + 9, the door in the north wall
     * (z0) at x0 + 2, or with doorSouth in the south wall (z0 + 9); every level in ys a landing,
     * the last the roof's, the shaft carried up into a hut five over it.
     */
    void stair(int x0, int z0, boolean doorSouth, int[] ys, String wall, String roof) {
        int top = ys[ys.length - 1] + 5;
        for (int y = ys[0]; y <= top; y++) {
            for (int z = z0; z <= z0 + 9; z++) {
                set(x0, y, z, wall);
                set(x0 + 4, y, z, wall);
            }
            for (int x = x0; x <= x0 + 4; x++) {
                set(x, y, z0, wall);
                set(x, y, z0 + 9, wall);
            }
        }
        fill(x0, top, z0, x0 + 4, top, z0 + 9, roof);
        Placer q = doorSouth ? p.turned(x0 + 4, 0, z0 + 9, 2) : p.turned(x0, 0, z0, 0);
        int[] last = Stairwell.build(q, rng, 1, 0, ys, false, 0, power ? 0.4 : 0.0);
        Stairwell.capTop(q, last);
        for (int y : ys) {
            int zs = doorSouth ? z0 + 10 : z0 - 1;
            if (y < ys[ys.length - 1]) {
                set(x0 + 1, y + 3, zs, "fit_st_escape[facing=" + (doorSouth ? "south" : "north") + "]");
            }
        }
    }

    /**
     * A lift w wide and dd deep with its car doors in the wall at z0 - 1 (facing north), the shaft
     * walled round in block, stopping at every level in ys; the machine room on the roof at top.
     */
    void lift(int x0, int z0, int lw, int dd, int[] ys, String block, int top) {
        int roofY = ys[ys.length - 1];
        for (int y = ys[0]; y <= top + 3; y++) {
            for (int z = z0 - 1; z <= z0 + dd; z++) {
                set(x0 - 1, y, z, block);
                set(x0 + lw, y, z, block);
            }
            for (int x = x0 - 1; x <= x0 + lw; x++) {
                set(x, y, z0 + dd, block);
                int k = -1;
                for (int fy : ys) {
                    if (y > fy && y <= fy + 2) {
                        k = fy;
                    }
                }
                if (k < 0 || y > roofY) {
                    set(x, y, z0 - 1, block);
                }
            }
        }
        fill(x0 - 1, top + 4, z0 - 1, x0 + lw, top + 4, z0 + dd, "un_roof");
        int[] floors = new int[ys.length - 1];
        System.arraycopy(ys, 0, floors, 0, floors.length);
        LiftBuild.shaft(p::set, x0, z0, lw, dd, floors, 0, 0, top);
    }

    /** A lamp standard on the walks: three of the fluted post, the globe on top (lit if there's power). */
    void lampPost(int x, int z) {
        for (int y = 1; y <= 3; y++) {
            set(x, y, z, "cv_lamp_post");
        }
        set(x, 4, z, power && chance() < 0.5 ? "cv_lamp_globe_lit" : chance() < 0.25 ? "air" : "cv_lamp_globe");
    }

    /** A shelter tent six across from x and len along from z, its door at its north end (the stadium's tent kit). */
    void tent(int lx, int lz, int len, String label) {
        for (int z = 0; z < len; z++) {
            for (int y = 1; y <= 2; y++) {
                set(lx, y, lz + z, "st_tent_wall[facing=west]");
                set(lx + 5, y, lz + z, "st_tent_wall[facing=east]");
            }
            set(lx, 3, lz + z, "st_tent_roof_lo[facing=west]");
            set(lx + 1, 3, lz + z, "st_tent_roof_hi[facing=west]");
            set(lx + 2, 4, lz + z, "st_tent_roof_lo[facing=west]");
            set(lx + 5, 3, lz + z, "st_tent_roof_lo[facing=east]");
            set(lx + 4, 3, lz + z, "st_tent_roof_hi[facing=east]");
            set(lx + 3, 4, lz + z, "st_tent_roof_lo[facing=east]");
        }
        for (int end = 0; end < 2; end++) {
            int z = end == 0 ? lz : lz + len - 1;
            String f = end == 0 ? "north" : "south";
            for (int x = 1; x <= 4; x++) {
                boolean door = end == 0 && (x == 2 || x == 3);
                for (int y = 1; y <= 2; y++) {
                    set(lx + x, y, z, door ? "air" : "st_tent_wall[facing=" + f + "]");
                }
            }
            set(lx + (end == 0 ? 3 : 2), 3, z, "st_tent_" + label + "_0[facing=" + f + "]");
            set(lx + (end == 0 ? 2 : 3), 3, z, "st_tent_" + label + "_1[facing=" + f + "]");
        }
        for (int z = lz + 2; z < lz + len - 2; z += 2) {
            for (int x : new int[] {lx + 1, lx + 4}) {
                if (chance() < 0.8) {
                    set(x, 1, z, (label.equals("supply") ? "sc_cases_water" : "sc_cot_head") + "[facing=north]");
                }
            }
        }
    }
}
