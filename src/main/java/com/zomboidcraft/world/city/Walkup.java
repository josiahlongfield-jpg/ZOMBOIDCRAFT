package com.zomboidcraft.world.city;

import com.zomboidcraft.world.county.Flats;
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

    private static final String[] WALLS = {"brick_red", "brick_red", "brick_dark", "concrete_panel", "brick_buff",
            "brick_orange", "plaster_sage", "plaster_cream", "plaster_blue", "plaster_rose"};
    /** Painted shopfronts and faded awnings: the streets stay grey, the shops break them up. */
    private static final String[] SHOP_PAINT = {"shop_paint_red", "shop_paint_navy", "shop_paint_teal",
            "shop_paint_black", "shop_paint_mustard", "shop_paint_green"};
    private static final String[] AWNINGS = {"awning", "awning_red", "awning_blue", "awning_maroon", "awning_yellow",
            "awning_teal"};

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d, floors;
    private final boolean furnished;
    private final String wall;
    /** Flats upstairs, reached by a stairwell across the back of the building. */
    private boolean flats;
    /** What the ground-floor shop sells; its sign and fit-out follow it. */
    private final Shops.Type shop;

    public Walkup(Placer p, SplittableRandom rng, int w, int d, int floors, boolean furnished) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.floors = floors;
        this.furnished = furnished;
        this.wall = WALLS[rng.nextInt(WALLS.length)];
        this.shop = Shops.pick(rng);
    }

    public void build() {
        int roofY = floors * FLOOR;
        shell(roofY);
        shopfront();
        flats = furnished && w >= 9 && d >= 14;
        for (int k = 0; k < (flats ? 1 : floors); k++) {
            interior(k * FLOOR);
        }
        if (flats) {
            // a stairwell up the back and a flat on every floor, furnished room by room
            Flats.stairwell(p, rng, w, d, floors, FLOOR, 0.03);
        }
        roof(roofY);
        glowingRooms();
        if (w >= 9 && floors >= 3) {
            fireEscape(roofY);
        }
        if (floors >= 3 && rng.nextDouble() < 0.4) {
            neonBlade(roofY);
        }
    }

    private void neonBlade(int roofY) {
        // the shop's own word, hung off the left-hand corner pier (as you face it), above the
        // shop sign: the fire escape never reaches that corner, and next door's blade never
        // hangs beside it
        String blade = Signs.bladeFor(shop);
        if (blade == null) {
            return;
        }
        int n = Signs.bladeLength(blade);
        int bottom = FLOOR + 1;
        if (bottom + n > roofY) {
            return;
        }
        Signs.blade(p, rng, blade, w - 1, bottom, -1);
    }

    /** Now and then an upstairs room still has its lamp on: purple, up by the front window. */
    private void glowingRooms() {
        for (int k = 1; k < floors; k++) {
            int y = k * FLOOR + 2;
            for (int x = 1; x < w - 1; x++) {
                String lamp = windowColumn(x, w) ? ShopLights.window(ShopLights.spot(p, x, y, 0), 0.02) : null;
                if (lamp != null) {
                    p.setIfAir(x, y + 1, 1, lamp);
                }
            }
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
        // pilasters at the ends, glass between, a glass door in the middle (double doors in a
        // wide shop), a sign or an awning over the windows, and a steel back door onto the yard
        int door = w / 2;
        boolean wide = w >= 12;
        boolean sign = rng.nextDouble() < 0.65;
        String trim = rng.nextDouble() < 0.7 ? SHOP_PAINT[rng.nextInt(SHOP_PAINT.length)] : "stone_trim";
        String awning = AWNINGS[rng.nextInt(AWNINGS.length)];
        if (sign) {
            sign = Signs.fascia(p, rng, shop.brand, 1, 3, -1, w - 2);
        }
        for (int x = 0; x < w; x++) {
            boolean pier = x == 0 || x == w - 1;
            for (int y = 1; y <= 2; y++) {
                String glass = rng.nextDouble() < 0.25 ? "window_broken" : rng.nextDouble() < 0.12 ? "boarded_window" : "window";
                p.set(x, y, 0, pier ? trim : glass);
            }
            p.set(x, 3, 0, trim);
            if (!pier && !sign) {
                p.set(x, 3, -1, awning + "[facing=north]");
            }
        }
        if (wide) {
            Doors.pair(p, rng, door - 1, 1, 0, Doors.SHOP, "south");
        } else {
            Doors.abandoned(p, rng, door, 1, 0, Doors.SHOP, "south", Doors.hinge(rng));
        }
        Doors.abandoned(p, rng, w - 2, 1, d - 1, Doors.STEEL, "north", Doors.hinge(rng));
        // a lantern by the door, still burning in some of them
        if (rng.nextDouble() < 0.45 && door + 1 < w - 1) {
            p.set(door + 1, 2, -1, rng.nextDouble() < 0.7 ? "wall_lantern" : "wall_lantern_off");
        }
        // a lamp box over the door: set into the awning, or lighting the sign from above
        if (rng.nextDouble() < 0.6) {
            p.set(door, sign ? 4 : 3, -1, ShopLights.lamp(rng, 0.2));
        }
    }

    private void interior(int y) {
        // ceiling lights off (one in a while still on); the shop fitted out, a back partition upstairs
        for (int x = 2; x < w - 1; x += 4) {
            for (int z = 2; z < d - 1; z += 4) {
                p.set(x, y + 3, z, rng.nextDouble() < 0.05 ? "ceiling_light" : "ceiling_light_off");
            }
        }
        if (y == 0) {
            // the shop is always fitted out (see Shops), stocked or not
            new Shops(p, rng, shop, w, d).backTo(flats ? w - 9 : w - 2).fitOut();
            return;
        }
        if (!furnished) {
            return;
        }
        int back = d / 2;
        for (int x = 1; x < w - 1; x++) {
            for (int yy = y + 1; yy < y + FLOOR; yy++) {
                p.set(x, yy, back, "drywall");
            }
        }
        Doors.abandoned(p, rng, w / 2, y + 1, back, Doors.FLAT, "south", Doors.hinge(rng));
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
