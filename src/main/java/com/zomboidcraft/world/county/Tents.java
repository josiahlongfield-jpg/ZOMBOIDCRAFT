package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Every tent in the world, from tools/city/tents.py's tn_ kit: the relief shelters, the triage
 * tents, the army's GP tents, the white field hospitals. A tent is a frame of aluminium poles under
 * a canvas fly pitched at 22.5 degrees, drawn through a {@link Placer} in its own frame: w across
 * from lx (6, or 10 and 18 for the white wards), len along from lz, the floor at y 0, its door in
 * the middle of its north end.
 * <ul>
 * <li>walls two high in the outermost cells; the fly a panel per cell from the eave up to the
 * ridge, panel k in row 3 + floor(k tan 22.5);</li>
 * <li>every other row a frame row: a rafter under each panel, a pole in each wall, a tie bar under
 * the eaves with its lamp, a guy line out to a stake each side;</li>
 * <li>the ends: corner posts, end walls up to the roof line, the door rolled up or hung with a
 * clear strip curtain; a banner on each side of a labelled tent, a red cross on a ward's;</li>
 * <li>the inside by what it was for, and the yard round it: the sign by the door, the genset at
 * the back, the mats out front of an aid station.</li>
 * </ul>
 * Fallen, the fly lies over what was inside, maybe one end still standing and the canvas slumped
 * down from it.
 */
final class Tents {
    private static final double TAN = Math.tan(Math.toRadians(22.5));
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    /** How hard the place was hit, 0 to 1: cut guy lines, torn walls, the mess inside. */
    double hit = 0.3;
    /** The chance a lamp still burns. */
    double lit = 0.1;
    /** A door at the back as well (the long wards). */
    boolean backDoor;
    /** Leave the floor under it as it is. */
    boolean bare;

    Tents(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String dir() {
        return DIRS[rng.nextInt(DIRS.length)];
    }

    /** The canvas a use comes in when the caller doesn't say. */
    static String canvasFor(String use) {
        return switch (use) {
            case "triage", "ward" -> "white";
            case "gp" -> "olive";
            default -> "tan";
        };
    }

    /** The widths the kit has a fly for in each canvas (tents.py's CANVAS). */
    static int width(String canvas, int w) {
        int[] ok = switch (canvas) {
            case "white" -> new int[] {6, 10, 18};
            case "tan" -> new int[] {6, 10};
            default -> new int[] {6};
        };
        int best = ok[0];
        for (int v : ok) {
            if (v <= w) {
                best = v;
            }
        }
        return best;
    }

    /** The row over the eave that panel k sits in. */
    static int rise(int k) {
        return (int) Math.floor(k * TAN + 1e-9);
    }

    /** A tent standing, or fallen in when the dice say so (chance grows with how hard it was hit). */
    void tent(int lx, int lz, int w, int len, String canvas, String use, double fallChance) {
        if (chance() < fallChance) {
            fallen(lx, lz, w, len, canvas, use);
        } else {
            pitch(lx, lz, w, len, canvas, use);
        }
    }

    // ------------------------------------------------------------------ standing

    void pitch(int lx, int lz, int w, int len, String canvas, String use) {
        w = width(canvas, w);
        // the army's frame tents mostly have an entry vestibule: the first two rows of the plot
        int v = len >= 8 && (use.equals("gp") || use.equals("ward")) && chance() < 0.7 ? 2 : 0;
        frame(lx, lz + v, w, len - v, len - v, canvas, use);
        if (v > 0) {
            vestibule(lx, lz, w, canvas, use);
        }
        fitOut(lx, lz + v, w, len - v, use);
        yard(lx, lz, w, len, canvas, use);
    }

    /**
     * The entry vestibule: a little frame tent four wide and two deep out in front of the door,
     * its own door at the front, open through to the tent behind; the ground either side of it bare.
     */
    private void vestibule(int lx, int lz, int w, String canvas, String use) {
        String t = "tn_" + canvas + "_";
        int x0 = lx + w / 2 - 2, x1 = x0 + 3;
        boolean curtain = use.equals("ward");
        String floor = bare ? null : use.equals("ward") ? "tn_mat" : "tn_ply";
        for (int i = 0; i < 2; i++) {
            int z = lz + i;
            boolean front = i == 0;
            if (floor != null) {
                for (int x = x0; x <= x1; x++) {
                    p.set(x, 0, z, floor);
                }
            }
            for (int s = 0; s < 2; s++) {
                int x = s == 0 ? x0 : x1;
                if (front) {
                    String f = s == 0 ? "north" : "east";
                    p.set(x, 1, z, t + "corner_lo[facing=" + f + "]");
                    p.set(x, 2, z, t + "corner[facing=" + f + "]");
                } else {
                    String out = s == 0 ? "west" : "east";
                    p.set(x, 1, z, t + "wall_lo[facing=" + out + "]");
                    p.set(x, 2, z, t + (chance() < 0.5 ? "win" : "wall") + "[facing=" + out + "]");
                }
            }
            for (int k = 0; k < 2; k++) {
                for (int s = 0; s < 2; s++) {
                    int x = s == 0 ? x0 + k : x1 - k;
                    String f = s == 0 ? "west" : "east";
                    String r = k == 1 ? "r" : "";
                    if (!front) {
                        p.set(x, 3, z, t + "roof_" + k + r + "[facing=" + f + "]");
                        continue;
                    }
                    p.set(x, 3, z, t + "roofend_" + k + r + "_" + (s == 0 ? "a" : "b") + "[facing=" + f + "]");
                    if (k == 1) {
                        String lr = s == 0 ? "l" : "r";
                        if (curtain) {
                            p.set(x, 1, z, "tn_curtain_lo[facing=north]");
                            p.set(x, 2, z, t + "curtain_hi[facing=north]");
                        } else {
                            p.set(x, 1, z, t + "door_lo_" + lr + "[facing=north]");
                            p.set(x, 2, z, t + "door_hi_" + lr + "[facing=north]");
                        }
                    }
                }
            }
        }
        p.setIfAir(x0 - 1, 1, lz + 1, "tn_guy[facing=west]");
        p.setIfAir(x1 + 1, 1, lz + 1, "tn_guy[facing=east]");
    }

    /** The canvas and its frame: rows 0..upTo-1 of the tent (the rest is down). */
    private void frame(int lx, int lz, int w, int len, int upTo, String canvas, String use) {
        String t = "tn_" + canvas + "_";
        int kk = w / 2;
        int door0 = lx + kk - 1, door1 = lx + kk;
        boolean curtain = use.equals("triage") || use.equals("ward");
        boolean label = use.equals("triage") || use.equals("shelter") || use.equals("supply");
        boolean cross = canvas.equals("white") && (use.equals("ward") || use.equals("triage"));
        String floor = bare ? null : switch (use) {
            case "triage", "ward" -> "tn_mat";
            case "gp", "supply" -> "tn_ply";
            default -> "tn_groundsheet";
        };
        for (int i = 0; i < upTo; i++) {
            int z = lz + i;
            boolean front = i == 0, back = i == len - 1, end = front || back;
            boolean frameRow = !end && i % 2 == 0;
            if (floor != null) {
                for (int x = lx; x < lx + w; x++) {
                    p.set(x, 0, z, floor);
                }
            }
            // the side walls
            for (int s = 0; s < 2; s++) {
                int x = s == 0 ? lx : lx + w - 1;
                String out = s == 0 ? "west" : "east";
                if (end) {
                    String f = front ? (s == 0 ? "north" : "east") : (s == 0 ? "west" : "south");
                    p.set(x, 1, z, t + "corner_lo[facing=" + f + "]");
                    p.set(x, 2, z, t + "corner[facing=" + f + "]");
                    continue;
                }
                boolean torn = chance() < hit * 0.05;
                p.set(x, 1, z, torn && chance() < 0.5 ? "air" : t + (frameRow ? "wall_lo_p" : "wall_lo") + "[facing=" + out + "]");
                String upper;
                if (label && len >= 6 && (i == 1 || i == 2)) {
                    // the banner's tile 0 at the reader's left: north on the west wall, south on the east
                    upper = "lbl_" + use + "_" + ((s == 0) == (i == 1) ? 0 : 1);
                } else if (cross && i == len / 2) {
                    upper = "cross";
                } else if (!frameRow && i % 4 == 3) {
                    upper = chance() < 0.4 ? "win_open" : "win";
                } else {
                    upper = frameRow ? "wall_p" : "wall";
                }
                p.set(x, 2, z, torn ? "air" : t + upper + "[facing=" + out + "]");
            }
            // the fly, and at the ends the gables and the doors under it
            for (int k = 0; k < kk; k++) {
                int y = 3 + rise(k);
                boolean ridge = k == kk - 1;
                for (int s = 0; s < 2; s++) {
                    int x = s == 0 ? lx + k : lx + w - 1 - k;
                    String f = s == 0 ? "west" : "east";
                    if (!end) {
                        p.set(x, y, z, t + "roof_" + k + (frameRow ? "f" : "") + (ridge ? "r" : "") + "[facing=" + f + "]");
                        continue;
                    }
                    // the gable panel on the model's east (a) or west (b), turned to the tent's end
                    boolean a = (s == 0) == front;
                    p.set(x, y, z, t + "roofend_" + k + (ridge ? "r" : "") + "_" + (a ? "a" : "b") + "[facing=" + f + "]");
                    if (k == 0) {
                        continue;  // the corner post's cell
                    }
                    String ef = front ? "north" : "south";
                    boolean door = (x == door0 || x == door1) && (front || backDoor);
                    for (int yy = door ? 3 : 1; yy < y; yy++) {
                        // a window either side of the door, plain canvas up into the gable
                        String piece = yy == 1 ? "wall_lo" : yy == 2 && k == 1 && w > 4 ? (chance() < 0.3 ? "win_open" : "win") : "wall";
                        p.set(x, yy, z, t + piece + "[facing=" + ef + "]");
                    }
                    if (door) {
                        // the jamb on the door's outer side: west on the front (the model as drawn), mirrored at the back
                        String lr = (x == door0) == front ? "l" : "r";
                        if (curtain) {
                            p.set(x, 1, z, "tn_curtain_lo[facing=" + ef + "]");
                            p.set(x, 2, z, t + "curtain_hi[facing=" + ef + "]");
                        } else {
                            p.set(x, 1, z, t + "door_lo_" + lr + "[facing=" + ef + "]");
                            p.set(x, 2, z, t + "door_hi_" + lr + "[facing=" + ef + "]");
                        }
                    }
                }
            }
            // a frame row's tie bar and lamp, its guy lines out each side
            if (frameRow) {
                for (int x = lx + 1; x < lx + w - 1; x++) {
                    boolean lamp = x == door0 || w > 6 && (x - lx) % 4 == 1;
                    p.set(x, 2, z, !lamp ? "tn_tie[axis=x]" : (chance() < lit ? "tn_tie_lamp_lit" : "tn_tie_lamp") + "[axis=x]");
                }
                p.setIfAir(lx - 1, 1, z, (chance() < hit * 0.4 ? "tn_guy_cut" : "tn_guy") + "[facing=west]");
                p.setIfAir(lx + w, 1, z, (chance() < hit * 0.4 ? "tn_guy_cut" : "tn_guy") + "[facing=east]");
            }
        }
    }

    // ------------------------------------------------------------------ fallen

    /**
     * Blown down or pulled down: maybe the front still up for a row or two and the fly slumped
     * from it to the ground, the rest canvas over whatever was inside, poles sticking out of it,
     * the guy lines cut.
     */
    void fallen(int lx, int lz, int w, int len, String canvas, String use) {
        w = width(canvas, w);
        String t = "tn_" + canvas + "_";
        int up = len >= 6 && chance() < 0.45 ? 1 + rng.nextInt(2) : 0;
        if (up > 0) {
            frame(lx, lz, w, len, up, canvas, use);
            // the fly slumped from the last frame still up: down in one go here, caught on a cot there
            for (int x = lx; x < lx + w; x++) {
                double r = chance();
                if (r < 0.7) {
                    p.set(x, 2, lz + up, t + "sag[facing=south]");
                    p.set(x, 1, lz + up + 1, t + "sag[facing=south]");
                } else if (r < 0.85) {
                    p.set(x, 1, lz + up, t + "sag[facing=south]");
                    p.set(x, 1, lz + up + 1, t + "heap[facing=" + dir() + "]");
                } else {
                    p.set(x, 1, lz + up, t + "heap[facing=" + dir() + "]");
                    p.set(x, 1, lz + up + 1, t + "sheet[facing=" + dir() + "]");
                }
            }
        }
        int from = up > 0 ? up + 2 : 0;
        boolean bed = !use.equals("supply");
        for (int i = from; i < len; i++) {
            int z = lz + i;
            for (int x = lx; x < lx + w; x++) {
                boolean edge = x == lx || x == lx + w - 1;
                if (!bare && !use.equals("shelter")) {
                    p.set(x, 0, z, use.equals("gp") || use.equals("supply") ? "tn_ply" : "tn_mat");
                }
                double r = chance();
                // a hump where a cot or a pallet is under it, the inner cells mostly
                String b = r < (edge ? 0.06 : bed ? 0.22 : 0.4) ? t + "heap[facing=" + dir() + "]"
                        : r < 0.9 ? t + "sheet[facing=" + dir() + "]"
                        : r < 0.95 ? "tn_pole_down[axis=" + (chance() < 0.5 ? "x" : "z") + "]" : "air";
                p.set(x, 1, z, b);
            }
            if (i % 2 == 0 && i > 0 && i < len - 1) {
                p.setIfAir(lx - 1, 1, z, "tn_guy_cut[facing=west]");
                p.setIfAir(lx + w, 1, z, "tn_guy_cut[facing=east]");
            }
        }
        // the corner posts that didn't go, a frame pole out of the heap
        int zb = lz + len - 1;
        for (int[] c : new int[][] {{lx, zb}, {lx + w - 1, zb}, {lx, lz}, {lx + w - 1, lz}}) {
            if (c[1] - lz >= from && chance() < 0.4) {
                p.set(c[0], 1, c[1], "tn_pole");
                if (chance() < 0.4) {
                    p.set(c[0], 2, c[1], "tn_pole");
                }
            }
        }
        // what was dragged out of it
        for (int n = 0; n < 2 + rng.nextInt(3); n++) {
            int x = chance() < 0.5 ? lx - 1 - rng.nextInt(2) : lx + w + rng.nextInt(2);
            int z = lz + rng.nextInt(len);
            String what = switch (use) {
                case "triage", "ward" -> chance() < 0.5 ? "st_stretcher[facing=" + dir() + "]" : "bunk_iv_stand";
                case "supply" -> chance() < 0.5 ? "st_pallet_water_half[facing=" + dir() + "]" : "cardboard_box";
                case "gp" -> chance() < 0.5 ? "tn_ruck[facing=" + dir() + "]" : "bunk_footlocker[facing=" + dir() + "]";
                default -> chance() < 0.5 ? "sc_cot_foot[facing=" + dir() + "]" : "wf_bags_" + "abc".charAt(rng.nextInt(3)) + "[facing=" + dir() + "]";
            };
            p.setIfAir(x, 1, z, what);
        }
    }

    // ------------------------------------------------------------------ the inside

    private void put(int x, int z, String block) {
        p.set(x, 1, z, block);
    }

    private void fitOut(int lx, int lz, int w, int len, String use) {
        int x0 = lx + 1, x1 = lx + w - 2, z0 = lz + 1, z1 = lz + len - 2;
        switch (use) {
            case "shelter" -> shelter(x0, x1, z0, z1, w);
            case "triage" -> triage(x0, x1, z0, z1);
            case "ward" -> ward(x0, x1, z0, z1, w);
            case "supply" -> supply(x0, x1, z0, z1);
            case "gp" -> gp(x0, x1, z0, z1);
            default -> { }
        }
        // the last of it: blood where someone was treated or turned, rubbish, a chair kicked over
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                if (chance() < 0.03 + hit * 0.05) {
                    p.setIfAir(x, 1, z, chance() < 0.5 ? "blood_splat" : chance() < 0.6 ? "trash_bag[facing=" + dir() + "]" : "trade_folding_chair_tipped[facing=" + dir() + "]");
                }
            }
        }
    }

    /** Cots down both walls in threes (head, foot, the family's things), more rows in a wide one. */
    private void shelter(int x0, int x1, int z0, int z1, int w) {
        int[] cols = w > 6 ? new int[] {x0, x0 + 2, x1 - 2, x1} : new int[] {x0, x1};
        for (int x : cols) {
            for (int z = z0; z + 1 <= z1; z += 3) {
                if (chance() < 0.85) {
                    String b = chance() < 0.5 ? "_b" : "";
                    put(x, z, "sc_cot_head" + b + "[facing=north]");
                    put(x, z + 1, "sc_cot_foot" + b + "[facing=north]");
                }
                if (z + 2 <= z1) {
                    double r = chance();
                    String f = x <= x0 + 2 ? "east" : "west";
                    put(x, z + 2, r < 0.3 ? "trade_folding_chair[facing=" + f + "]" : r < 0.55 ? "wf_bags_" + "abc".charAt(rng.nextInt(3)) + "[facing=" + f + "]"
                            : r < 0.75 ? "sc_cases_blankets[facing=" + f + "]" : r < 0.85 ? "sc_cases_water[facing=" + f + "]" : "air");
                }
            }
        }
    }

    /** Stretchers on their stands down the walls, a drip or the oxygen between, the medics' table by the door. */
    private void triage(int x0, int x1, int z0, int z1) {
        put(x0, z0, "tn_table_med[facing=east]");
        put(x0 + 1, z0, "trade_folding_chair[facing=west]");
        put(x1, z0, "tn_screen[facing=east]");
        for (int x : new int[] {x0, x1}) {
            String in = x == x0 ? "east" : "west";
            for (int z = z0 + 1; z <= z1; z += 2) {
                if (chance() < 0.85) {
                    put(x, z, (chance() < 0.1 + hit * 0.25 ? "st_stretcher_bag" : "st_stretcher") + "[facing=north]");
                }
                if (z + 1 <= z1) {
                    double r = chance();
                    put(x, z + 1, r < 0.35 ? "bunk_iv_stand" : r < 0.55 ? "hs_oxygen[facing=" + in + "]" : r < 0.8 ? "tn_screen[facing=north]" : "air");
                }
            }
        }
        put(x1, z1, "cellar_shelf_medical[facing=west]");
    }

    /** The field hospital: beds down the walls between screens, drips and oxygen by them, the tables up the middle. */
    private void ward(int x0, int x1, int z0, int z1, int w) {
        int[] beds = w >= 18 ? new int[] {x0, x0 + 6, x1 - 6, x1} : new int[] {x0, x1};
        for (int x : beds) {
            String in = x < x0 + (x1 - x0) / 2 ? "east" : "west";
            int side = in.equals("east") ? 1 : -1;
            for (int z = z0 + 1; z <= z1; z += 2) {
                if (chance() < 0.85) {
                    put(x, z, (chance() < 0.5 ? "hs_gurney_a" : chance() < 0.2 + hit * 0.3 ? "hs_gurney_bag_a" : "hs_gurney_b") + "[facing=north]");
                    if (chance() < 0.6) {
                        put(x + side, z, chance() < 0.7 ? "bunk_iv_stand" : "hs_oxygen[facing=" + in + "]");
                    }
                }
                if (z + 1 <= z1) {
                    put(x, z + 1, chance() < 0.75 ? "tn_screen[facing=north]" : "air");
                }
            }
        }
        if (w > 6) {
            int mid = x0 + (x1 - x0) / 2;
            for (int z = z0 + 2; z <= z1 - 1; z += 6) {
                put(mid, z, "tn_table_med[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
                put(mid + 1, z, "trade_folding_chair[facing=west]");
            }
        }
        put(x0, z0, "tn_table_med[facing=east]");
        put(x1, z1, "cellar_shelf_medical[facing=west]");
    }

    /** The stores on pallets down both sides, the desk by the door with the list on it. */
    private void supply(int x0, int x1, int z0, int z1) {
        put(x0, z0, "tn_table_supply[facing=east]");
        put(x0 + 1, z0, "trade_folding_chair[facing=west]");
        String[] what = {"st_pallet_water", "st_pallet_water_half", "st_pallet_meals", "st_pallet_cots", "bunk_crate_ration", "pk_military_crate"};
        for (int x : new int[] {x0, x1}) {
            for (int z = z0 + 1; z <= z1; z++) {
                if (chance() < 0.8 - hit * 0.3) {
                    put(x, z, what[rng.nextInt(what.length)] + "[facing=" + (x == x0 ? "east" : "west") + "]");
                } else if (chance() < 0.3) {
                    put(x, z, "cardboard_box");
                }
            }
        }
    }

    /** Soldiers' cots down both walls, a footlocker at each foot, rucks by the heads, the cans down the middle. */
    private void gp(int x0, int x1, int z0, int z1) {
        for (int x : new int[] {x0, x1}) {
            String in = x == x0 ? "east" : "west";
            for (int z = z0; z + 1 <= z1; z += 3) {
                if (chance() < 0.85) {
                    put(x, z, "sc_cot_head" + (chance() < 0.4 ? "_b" : "") + "[facing=north]");
                    put(x, z + 1, "sc_cot_foot[facing=north]");
                    if (chance() < 0.4) {
                        put(x + (x == x0 ? 1 : -1), z, "tn_ruck[facing=" + in + "]");
                    }
                }
                if (z + 2 <= z1) {
                    put(x, z + 2, "bunk_footlocker[facing=" + in + "]");
                }
            }
        }
        int mid = z0 + (z1 - z0) / 2;
        if (chance() < 0.5) {
            put(x0 + 1, mid, chance() < 0.5 ? "bunk_jerry_cans[facing=east]" : "bunk_crate_ammo[facing=east]");
        }
    }

    // ------------------------------------------------------------------ the yard

    private void yard(int lx, int lz, int w, int len, String canvas, String use) {
        int kk = w / 2, door0 = lx + kk - 1, door1 = lx + kk;
        boolean med = use.equals("triage") || use.equals("ward");
        if (med && !bare) {
            // the mats run out of the door onto the ground
            for (int x = door0 - 1; x <= door1 + 1; x++) {
                for (int z = lz - 2; z < lz; z++) {
                    if (p.get(x, 1, z) == null || "air".equals(p.get(x, 1, z))) {
                        p.set(x, 0, z, "tn_mat");
                    }
                }
            }
        }
        if (use.equals("triage") || use.equals("shelter") || use.equals("supply")) {
            p.setIfAir(door1 + 2, 1, lz - 1, "tn_stand_" + use + "[facing=north]");
        }
        if (med && chance() < 0.6) {
            p.setIfAir(door0 - 2, 1, lz - 2, "st_cone");
        }
        if (use.equals("gp") && chance() < 0.3 + hit * 0.5) {
            p.setIfAir(door0 - 1, 1, lz - 1, "sandbags[facing=north]");
            p.setIfAir(door1 + 1, 1, lz - 1, "sandbags[facing=north]");
        }
        // the genset that ran the lights, out behind, its cable in under the back wall
        if ((med || use.equals("supply") || chance() < 0.3) && chance() < 0.65) {
            int z = lz + len;
            p.setIfAir(door1 + 1, 1, z + 1, "tn_genset[facing=north]");
            p.setIfAir(door1 + 1, 1, z, "tn_cable[axis=z]");
            if (chance() < 0.6) {
                p.setIfAir(door1 + 2, 1, z + 1, "bunk_jerry_cans[facing=north]");
            }
        }
    }
}
