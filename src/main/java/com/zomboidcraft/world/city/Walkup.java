package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * A brick mid-rise of the old downtown: a shop on the ground floor behind big windows and an
 * awning, three to eight floors of flats above with paired windows, a stone cornice, and an
 * iron fire escape down the front. Some windows are boarded up, the way survivors left them.
 *
 * Same conventions as {@link Tower}: front along local z = 0 facing north, floors 4 blocks.
 */
public final class Walkup {
    public static final int FLOOR = 4;

    private static final String[] WALLS = {"brick_red", "brick_red", "brick_dark", "concrete_panel"};

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d, floors;
    private final boolean furnished;
    private final String wall;

    public Walkup(Placer p, SplittableRandom rng, int w, int d, int floors, boolean furnished) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.floors = floors;
        this.furnished = furnished;
        this.wall = WALLS[rng.nextInt(WALLS.length)];
    }

    public void build() {
        int roofY = floors * FLOOR;
        shell(roofY);
        shopfront();
        for (int k = 0; k < floors; k++) {
            interior(k * FLOOR);
        }
        roof(roofY);
        if (w >= 9 && floors >= 3) {
            fireEscape(roofY);
        }
    }

    private boolean windowColumn(int along, int length) {
        // pairs of windows between brick piers: pier, window, window, pier ...
        int i = along % 3;
        return along > 0 && along < length - 1 && i != 0;
    }

    private void shell(int roofY) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                for (int y = 0; y <= roofY; y++) {
                    if (y % FLOOR == 0) {
                        p.set(x, y, z, edge ? (y == 0 ? "concrete_dark" : wall) : y == 0 ? "floor_concrete" : "office_floor");
                        continue;
                    }
                    if (!edge) {
                        continue;
                    }
                    boolean front = z == 0, back = z == d - 1;
                    int along = front || back ? x : z;
                    int length = front || back ? w : d;
                    int inFloor = y % FLOOR;
                    String block = wall;
                    if (inFloor <= 2 && windowColumn(along, length) && y > FLOOR) {
                        block = upperWindow();
                    } else if (inFloor <= 2 && windowColumn(along, length) && y < FLOOR && !front) {
                        block = upperWindow();
                    } else if (inFloor == 3 && windowColumn(along, length) && y > FLOOR) {
                        block = "stone_trim";  // lintel
                    }
                    p.set(x, y, z, block);
                }
                // parapet
                if (edge) {
                    p.set(x, roofY + 1, z, wall);
                }
            }
        }
        // stone cornice sticking out over the street
        for (int x = -1; x <= w; x++) {
            p.set(x, roofY, -1, "stone_trim");
            p.set(x, roofY + 1, -1, "stone_trim");
        }
        // sills under every upper-floor window on the front
        for (int k = 1; k < floors; k++) {
            for (int x = 0; x < w; x++) {
                if (windowColumn(x, w) && rng.nextDouble() < 0.9) {
                    p.set(x, k * FLOOR, -1, "sill[facing=north]");
                }
            }
        }
    }

    private String upperWindow() {
        double r = rng.nextDouble();
        if (r < 0.08) {
            return "boarded_window";
        }
        if (r < 0.15) {
            return "window_broken";
        }
        if (r < 0.18) {
            return "window_lit";
        }
        return "window";
    }

    private void shopfront() {
        // pilasters at the ends, glass between, a door in the middle, sign band above
        int door = w / 2;
        for (int x = 0; x < w; x++) {
            boolean pier = x == 0 || x == w - 1;
            for (int y = 1; y <= 2; y++) {
                String glass = rng.nextDouble() < 0.25 ? "window_broken" : rng.nextDouble() < 0.12 ? "boarded_window" : "window";
                p.set(x, y, 0, pier ? "stone_trim" : (x == door ? "air" : glass));
            }
            p.set(x, 3, 0, "stone_trim");
            if (!pier) {
                p.set(x, 3, -1, "awning[facing=north]");
            }
        }
        if (rng.nextDouble() < 0.5) {
            p.set(door, 1, 0, "fire_door[facing=north]");
        }
        // a lantern by the door, still burning in some of them
        if (rng.nextDouble() < 0.45 && door + 1 < w - 1) {
            p.set(door + 1, 2, -1, rng.nextDouble() < 0.7 ? "wall_lantern" : "wall_lantern_off");
        }
    }

    private void interior(int y) {
        // ceiling lights off (one in a while still on), a back partition on the upper floors
        for (int x = 2; x < w - 1; x += 4) {
            for (int z = 2; z < d - 1; z += 4) {
                p.set(x, y + 3, z, rng.nextDouble() < 0.05 ? "ceiling_light" : "ceiling_light_off");
            }
        }
        if (!furnished) {
            return;
        }
        if (y > 0) {
            int back = d / 2;
            for (int x = 1; x < w - 1; x++) {
                for (int yy = y + 1; yy < y + FLOOR; yy++) {
                    p.set(x, yy, back, x == w / 2 && yy < y + 3 ? "air" : "drywall");
                }
            }
        } else {
            // shop counter and shelves
            for (int x = 2; x < w - 2; x++) {
                p.set(x, 1, d - 4, "reception_desk");
            }
            for (int z = 2; z < d - 5; z += 2) {
                p.set(1, 1, z, "filing_cabinet[facing=east]");
            }
        }
        for (int i = 0; i < 2; i++) {
            int x = 1 + rng.nextInt(w - 2), z = 1 + rng.nextInt(d - 2);
            p.set(x, y + 1, z, rng.nextBoolean() ? "rubble_pile" : "moss_carpet");
        }
    }

    private void roof(int roofY) {
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                p.set(x, roofY, z, "roof");
            }
        }
        if (rng.nextDouble() < 0.5) {
            int x = 1 + rng.nextInt(Math.max(1, w - 4)), z = 2 + rng.nextInt(Math.max(1, d - 5));
            p.fill(x, roofY + 1, z, x + 1, roofY + 2, z + 1, "hvac");
        }
        if (rng.nextDouble() < 0.4 && w >= 8) {
            // wooden water tower on a steel frame, the old-city silhouette
            int x = w - 5, z = d - 5;
            for (int[] leg : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
                p.fill(x + leg[0], roofY + 1, z + leg[1], x + leg[0], roofY + 2, z + leg[1], "steel_mullion");
            }
            p.fill(x, roofY + 3, z, x + 2, roofY + 5, z + 2, "water_tank");
            p.set(x + 1, roofY + 6, z + 1, "water_tank_top");
        }
    }

    /** Grated landings with railings at each floor, joined by steel stairs, down to a drop ladder. */
    private void fireEscape(int roofY) {
        int x0 = 1 + rng.nextInt(Math.max(1, w - 7));
        int x1 = x0 + 4;
        for (int k = 1; k < floors; k++) {
            int y = k * FLOOR;
            for (int x = x0; x <= x1; x++) {
                p.set(x, y, -1, "grate");
                p.set(x, y, -2, "grate");
                p.set(x, y + 1, -2, "railing[facing=south]");
            }
            p.set(x0 - 1, y + 1, -1, "railing[facing=east]");
            p.set(x0 - 1, y + 1, -2, "railing[facing=east]");
            p.set(x1 + 1, y + 1, -1, "railing[facing=west]");
            p.set(x1 + 1, y + 1, -2, "railing[facing=west]");
            // a window opens onto each landing
            p.set(x0 + 1, y + 1, 0, "air");
            p.set(x0 + 1, y + 2, 0, "air");
            // stairs from this landing down to the one below, alternating direction
            if (k > 1) {
                boolean east = k % 2 == 0;
                for (int s = 1; s <= 3; s++) {
                    int x = east ? x0 + s : x1 - s;
                    p.set(x, y - FLOOR + s, -2, "steel_stairs[facing=" + (east ? "west" : "east") + "]");
                }
            }
        }
        // the drop ladder stops a floor above the sidewalk
        p.fill(x1, FLOOR - 2, -2, x1, FLOOR - 1, -2, "ladder[facing=south]");
    }
}
