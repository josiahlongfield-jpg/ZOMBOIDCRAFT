package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * A fully enterable office tower: a double-height lobby, stacked office floors around a core
 * with two fire stairwells and two lift shafts, an optional setback terrace, and a crown.
 *
 * Local layout (front at z = 0): the facade is a ring one block thick; each floor is 4 blocks
 * (slab plus 3 of room). The 14 x 10 core sits in the middle of the footprint: stairwell A,
 * the two lifts (plant room behind), stairwell B. Stairs switch back inside each 3-wide
 * stairwell, so every floor connects, up to a plant room on the roof.
 *
 * Styles change the skin, not the plan: a concrete grid, a glass curtain wall, stone piers in
 * the 1930s manner, or old brick with stone bands. Crowns: a radio mast, a stepped stone crown
 * with a spire, a glowing band, or a rooftop water tank.
 */
public final class Tower {
    public static final int FLOOR = 4;
    public static final int LOBBY = 8;
    public static final int GRID = 0, GLASS = 1, DECO = 2, BRICK = 3;
    private static final int CORE_W = 14, CORE_D = 10;

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d, floors;
    private final boolean furnished;
    private final boolean[] powered;
    private final int style, crown, setbackFloor;
    private final int cx0, cz0;  // core corner

    /** furnished = false leaves the office floors empty (distant towers nobody will reach soon). */
    public Tower(Placer p, SplittableRandom rng, int w, int d, int floors, boolean furnished) {
        this(p, rng, w, d, floors, furnished, rng.nextInt(4), rng.nextInt(4));
    }

    public Tower(Placer p, SplittableRandom rng, int w, int d, int floors, boolean furnished, int style, int crown) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.floors = floors;
        this.furnished = furnished;
        this.style = style;
        this.crown = crown;
        this.cx0 = (w - CORE_W) / 2;
        this.cz0 = (d - CORE_D) / 2;
        // tall towers wide enough to keep the core step in by 4 blocks part way up
        boolean canStep = floors >= 16 && cx0 >= 6 && cz0 >= 6;
        this.setbackFloor = canStep && rng.nextDouble() < 0.7 ? floors / 2 + rng.nextInt(floors / 4) : Integer.MAX_VALUE;
        // a few floors still have power from a generator somewhere: their lights and windows glow
        this.powered = new boolean[floors + 2];
        for (int k = 1; k <= floors; k++) {
            powered[k] = rng.nextDouble() < 0.12;
        }
    }

    public static int height(int floors) {
        return LOBBY + floors * FLOOR + 3;
    }

    public void build() {
        lobby();
        for (int k = 1; k <= floors; k++) {
            officeFloor(k, floorY(k));
        }
        int roofY = floorY(floors + 1);
        facade(roofY);
        core(roofY);
        roof(roofY);
    }

    private static int floorY(int k) {
        return k == 0 ? 0 : LOBBY + (k - 1) * FLOOR;
    }

    /** Which floor a height belongs to: 0 for the lobby. */
    private static int floorOf(int y) {
        return y < LOBBY ? 0 : (y - LOBBY) / FLOOR + 1;
    }

    private int inset(int floor) {
        return floor >= setbackFloor ? 4 : 0;
    }

    private boolean inCore(int x, int z) {
        return x >= cx0 && x < cx0 + CORE_W && z >= cz0 && z < cz0 + CORE_D;
    }

    // ------------------------------------------------------------------ outside

    private void facade(int roofY) {
        for (int y = 0; y <= roofY; y++) {
            int k = floorOf(y);
            int in = inset(Math.min(k, floors));
            boolean slab = y >= LOBBY && (y - LOBBY) % FLOOR == 0;
            for (int x = in; x < w - in; x++) {
                for (int z = in; z < d - in; z++) {
                    boolean edge = x == in || z == in || x == w - 1 - in || z == d - 1 - in;
                    if (!edge) {
                        continue;
                    }
                    boolean frontBack = z == in || z == d - 1 - in;
                    int along = frontBack ? x - in : z - in;
                    int length = (frontBack ? w : d) - 2 * in;
                    p.set(x, y, z, skin(y, k, slab, along, length));
                }
            }
            // the terrace where the tower steps in: roof and parapet on the lower part
            if (k == setbackFloor && y == floorY(k)) {
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        boolean outer = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                        if (x < in || z < in || x >= w - in || z >= d - in) {
                            p.set(x, y, z, outer ? trim() : "roof");
                            if (outer) {
                                p.set(x, y + 1, z, trim());
                            }
                        }
                    }
                }
            }
        }
        int in = inset(floors);
        for (int x = in; x < w - in; x++) {
            for (int z = in; z < d - in; z++) {
                if (x == in || z == in || x == w - 1 - in || z == d - 1 - in) {
                    p.set(x, roofY + 1, z, wallMaterial());
                    p.set(x, roofY + 2, z, trim());
                }
            }
        }
        // the entrance: two pairs of glass doors under a transom, a steel canopy over the sidewalk
        int mid = w / 2;
        p.fill(mid - 2, 3, 0, mid + 1, 3, 0, "window");
        Doors.pair(p, rng, mid - 2, 1, 0, Doors.SHOP, "south");
        Doors.pair(p, rng, mid, 1, 0, Doors.SHOP, "south");
        p.fill(mid - 3, 4, -3, mid + 2, 4, -1, "steel_mullion");
        p.set(mid - 3, 3, -3, "wall_lantern");
        p.set(mid + 2, 3, -3, "wall_lantern");
    }

    private String wallMaterial() {
        return switch (style) {
            case GLASS -> "steel_mullion";
            case DECO -> "stone_trim";
            case BRICK -> "brick_dark";
            default -> "concrete_dark";
        };
    }

    private String trim() {
        return style == GLASS ? "steel_mullion" : style == GRID ? "concrete_panel" : "stone_trim";
    }

    /** The facade block at one spot: columns, spandrels and windows depend on the style. */
    private String skin(int y, int floor, boolean slab, int along, int length) {
        boolean corner = along == 0 || along == length - 1;
        if (y < LOBBY) {
            // the lobby: tall glass between piers, a stone band over it
            if (corner || along % 4 == 0) {
                return style == GLASS ? "steel_mullion" : style == BRICK ? "stone_trim" : "concrete_dark";
            }
            return y == 0 ? "concrete_dark" : y == LOBBY - 1 ? "stone_trim" : "window";
        }
        if (corner) {
            return wallMaterial();
        }
        switch (style) {
            case GLASS:
                if (along % 4 == 0) {
                    return "steel_mullion";
                }
                return slab ? "glass_spandrel" : windowFor(floor);
            case DECO:
                // continuous stone piers every other block, dark brick spandrels between
                if (along % 2 == 0) {
                    return "stone_trim";
                }
                return slab ? "brick_dark" : windowFor(floor);
            case BRICK:
                if (slab) {
                    return floor % 5 == 0 ? "stone_trim" : "brick_dark";
                }
                return along % 3 == 0 ? "brick_dark" : windowFor(floor);
            default:
                if (along % 4 == 0) {
                    return "concrete_dark";
                }
                return slab ? "concrete_panel" : windowFor(floor);
        }
    }

    private String windowFor(int floor) {
        double r = rng.nextDouble();
        if (floor <= floors && (powered[floor] ? r < 0.75 : r < 0.035)) {
            return "window_lit";
        }
        return rng.nextDouble() < 0.07 ? "window_broken" : "window";
    }

    // ------------------------------------------------------------------ lobby

    private void lobby() {
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                boolean dark = ((x / 2) + (z / 2)) % 2 == 0;
                p.set(x, 0, z, dark ? "lobby_floor_dark" : "lobby_floor_white");
                p.set(x, LOBBY, z, "office_floor");
                if (x % 4 == 2 && z % 4 == 2 && !inCore(x, z)) {
                    p.set(x, LOBBY - 1, z, rng.nextDouble() < 0.4 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        if (!furnished) {
            return;
        }
        // reception desk facing the doors, with a chair behind
        int mid = w / 2;
        int rz = Math.max(3, cz0 - 4);
        for (int x = mid - 4; x <= mid + 3; x++) {
            p.set(x, 1, rz, "reception_desk");
        }
        p.set(mid - 4, 1, rz + 1, "reception_desk");
        p.set(mid + 3, 1, rz + 1, "reception_desk");
        p.set(mid - 2, 1, rz + 1, "office_chair[facing=north]");
        p.set(mid + 1, 1, rz + 2, "office_chair[facing=west]");
        // planters in the corners
        for (int[] c : new int[][] {{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}}) {
            p.set(c[0], 1, c[1], "planter");
            p.set(c[0], 2, c[1], "weeds");
        }
        // security desk by the side wall
        if (cx0 > 4) {
            p.fill(2, 1, cz0, 2, 1, cz0 + 3, "reception_desk");
            p.set(3, 1, cz0 + 1, "office_chair[facing=west]");
        }
        scatter(1, 1, 1, w - 2, d - 2, 4);
    }

    // ------------------------------------------------------------------ office floors

    private void officeFloor(int k, int y) {
        int in = inset(k);
        boolean lit = powered[k];
        for (int x = 1 + in; x < w - 1 - in; x++) {
            for (int z = 1 + in; z < d - 1 - in; z++) {
                if (!inCore(x, z)) {
                    p.set(x, y, z, "office_floor");
                    if (x % 4 == 2 && z % 4 == 2) {
                        p.set(x, y + 3, z, lit ? "ceiling_light" : "ceiling_light_off");
                    }
                }
            }
        }
        if (!furnished) {
            return;
        }
        int fy = y + 1;
        // desk pods in front of and behind the core
        for (int start = 2 + in; start + 4 < w - 1 - in; start += 6) {
            int front = in + 4, back = d - in - 6;
            if (front + 3 < cz0) {
                pod(start, fy, front);
            }
            if (back - 2 > cz0 + CORE_D - 1) {
                pod(start, fy, back);
            }
        }
        // a meeting room on the west side behind glass, filing cabinets on the east
        int wallX = cx0 - 2;
        if (wallX - 4 > in) {
            for (int z = cz0; z < cz0 + CORE_D; z++) {
                String wall = z % 3 == 0 ? "drywall" : "window";
                for (int yy = fy; yy < fy + 3; yy++) {
                    p.set(wallX, yy, z, wall);
                }
            }
            Doors.abandoned(p, rng, wallX, fy, cz0 + 4, Doors.SHOP, "west", "right");
            Doors.abandoned(p, rng, wallX, fy, cz0 + 5, Doors.SHOP, "west", "left");
            for (int z = cz0 + 2; z <= cz0 + 7; z++) {
                p.set(wallX - 3, fy, z, "desk[facing=east]");
                p.set(wallX - 2, fy, z, "desk[facing=west]");
                if (rng.nextDouble() < 0.7) {
                    p.set(wallX - 4, fy, z, chair("east"));
                }
                if (rng.nextDouble() < 0.7) {
                    p.set(wallX - 1, fy, z, chair("west"));
                }
            }
        }
        for (int z = cz0; z < cz0 + CORE_D; z += 2) {
            p.set(w - 2 - in, fy, z, "filing_cabinet[facing=west]");
        }
        scatter(fy, 1 + in, 1 + in, w - 2 - in, d - 2 - in, 3);
    }

    /** Two rows of desks back to back, four wide, with chairs and end panels. */
    private void pod(int x0, int y, int z) {
        for (int x = x0; x < x0 + 4; x++) {
            p.set(x, y, z, "desk[facing=south]");
            p.set(x, y, z + 1, "desk[facing=north]");
            if (rng.nextDouble() < 0.85) {
                p.set(x, y, z - 1, chair("south"));
            }
            if (rng.nextDouble() < 0.85) {
                p.set(x, y, z + 2, chair("north"));
            }
        }
        p.set(x0 - 1, y, z, "cubicle_wall[facing=east]");
        p.set(x0 - 1, y, z + 1, "cubicle_wall[facing=east]");
        p.set(x0 + 4, y, z, "cubicle_wall[facing=east]");
        p.set(x0 + 4, y, z + 1, "cubicle_wall[facing=east]");
    }

    private String chair(String facing) {
        // the dead left in a hurry: some chairs are turned away
        String[] dirs = {"north", "east", "south", "west"};
        return "office_chair[facing=" + (rng.nextDouble() < 0.3 ? dirs[rng.nextInt(4)] : facing) + "]";
    }

    private void scatter(int y, int x0, int z0, int x1, int z1, int n) {
        for (int i = 0; i < n; i++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1);
            int z = z0 + rng.nextInt(z1 - z0 + 1);
            if (!inCore(x, z)) {
                p.set(x, y, z, rng.nextBoolean() ? "rubble_pile" : "moss_carpet");
            }
        }
    }

    // ------------------------------------------------------------------ core

    private void core(int roofY) {
        int top = roofY + FLOOR;  // stairs and lifts rise into a plant room on the roof
        int sa = cx0 + 4, lift = cx0 + 5, sb = cx0 + 9, back = cz0 + 4;
        for (int y = 0; y <= top; y++) {
            boolean lobby = y < LOBBY;
            String wall = lobby ? "wood_panel" : "drywall";
            for (int x = cx0; x < cx0 + CORE_W; x++) {
                for (int z = cz0; z < cz0 + CORE_D; z++) {
                    boolean edge = x == cx0 || x == cx0 + CORE_W - 1 || z == cz0 || z == cz0 + CORE_D - 1
                            || x == sa || x == sb || (x >= lift && x <= lift + 3 && z == back);
                    boolean shaft = x == lift + 1 && z < back;  // wall between the two lifts
                    if (edge || shaft) {
                        p.set(x, y, z, y > roofY ? "concrete_dark" : (x == sa || x == sb) && !lobby ? "concrete_dark" : wall);
                    } else {
                        p.set(x, y, z, y == 0 ? "floor_concrete" : "air");
                    }
                }
            }
        }
        // lift doors on every floor, plant rooms behind the lifts get a floor
        for (int level = 0; level <= floors; level++) {
            int y = floorY(level);
            for (int yy = y + 1; yy <= y + 2; yy++) {
                p.set(lift, yy, cz0, "elevator_door[facing=north]");
                p.set(lift + 2, yy, cz0, "elevator_door[facing=north]");
            }
            p.fill(lift, y, back + 1, lift + 3, y, cz0 + CORE_D - 2, "floor_concrete");
        }
        stairwell(cx0 + 1, roofY);
        stairwell(cx0 + 10, roofY);
        p.fill(cx0, top, cz0, cx0 + CORE_W - 1, top, cz0 + CORE_D - 1, "roof");
    }

    /** A 3-wide switchback stairwell whose inner x runs x0..x0+2. */
    private void stairwell(int x0, int roofY) {
        int land = cz0 + 1, flight = cz0 + 3, far = cz0 + 5;
        int levels = floors + 1;  // lobby, offices, then up to the roof
        for (int level = 0; level <= levels; level++) {
            int y = level == levels ? roofY : floorY(level);
            // landing at floor level, by the fire door
            p.fill(x0, y, land, x0 + 2, y, land + 1, "floor_concrete");
            Doors.abandoned(p, rng, x0 + 1, y + 1, cz0, Doors.STEEL, "south", Doors.hinge(rng));
            p.set(x0 + 1, y + 3, land + 1, "ceiling_light_off");
            if (level == levels) {
                break;
            }
            int next = y + (level == 0 ? LOBBY : FLOOR);
            // flights up each side with landings at the far end, until the next floor
            int h = y;
            int side = 0;
            while (h < next) {
                int x = side == 0 ? x0 : x0 + 2;
                if (side == 0) {
                    for (int z = flight; z <= flight + 1 && h < next; z++) {
                        h++;
                        p.set(x, h, z, "stairs_concrete[facing=south]");
                    }
                    if (h < next) {
                        p.fill(x0, h, far, x0 + 2, h, far + 3, "floor_concrete");
                    }
                } else {
                    for (int z = flight + 1; z >= flight && h < next; z--) {
                        h++;
                        p.set(x, h, z, "stairs_concrete[facing=north]");
                    }
                    if (h < next) {
                        p.fill(x0, h, land, x0 + 2, h, land + 1, "floor_concrete");
                    }
                }
                side ^= 1;
            }
            for (int yy = y + 1; yy < next; yy++) {
                if (rng.nextDouble() < 0.9) {
                    p.set(x0 + 1, yy + 1, flight, "railing[facing=west]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ roof

    private void roof(int roofY) {
        int in = inset(floors);
        for (int x = 1 + in; x < w - 1 - in; x++) {
            for (int z = 1 + in; z < d - 1 - in; z++) {
                if (!inCore(x, z)) {
                    p.set(x, roofY, z, "roof");
                }
            }
        }
        int y = roofY + 1;
        for (int i = 0; i < 3; i++) {
            int x = 2 + in + rng.nextInt(Math.max(1, w - 8 - 2 * in));
            int z = rng.nextBoolean() ? 2 + in + rng.nextInt(3) : d - 5 - in - rng.nextInt(3);
            if (!inCore(x, z) && !inCore(x + 2, z + 1)) {
                p.fill(x, y, z, x + 2, y + 1, z + 1, "hvac");
            }
        }
        switch (crown) {
            case 1 -> steppedCrown(roofY, in);
            case 2 -> glowingBand(roofY, in);
            case 3 -> waterTank(y, w - 7 - in, d - 7 - in);
            default -> mast(y, 3 + in, d - 4 - in);
        }
    }

    private void mast(int y, int x, int z) {
        int h = 14 + rng.nextInt(10);
        p.fill(x, y, z, x, y + h, z, "steel_mullion");
        p.set(x, y + h + 1, z, "aviation_light");
    }

    private void waterTank(int y, int tx, int tz) {
        for (int[] leg : new int[][] {{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            p.fill(tx + leg[0], y, tz + leg[1], tx + leg[0], y + 1, tz + leg[1], "steel_mullion");
        }
        for (int yy = y + 2; yy < y + 6; yy++) {
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    boolean corner = (dx == 0 || dx == 3) && (dz == 0 || dz == 3);
                    if (!corner) {
                        p.set(tx + dx, yy, tz + dz, "water_tank");
                    }
                }
            }
        }
        p.set(tx + 1, y + 6, tz + 1, "water_tank_top");
        p.set(tx + 2, y + 6, tz + 2, "water_tank_top");
    }

    /** Stone tiers stepping in above the roof, with a spire and a warning light on top. */
    private void steppedCrown(int roofY, int in) {
        int y = roofY + 3;
        int x0 = in + 2, z0 = in + 2, x1 = w - 3 - in, z1 = d - 3 - in;
        while (x1 - x0 >= 4 && z1 - z0 >= 4) {
            int tier = 3 + rng.nextInt(3);
            for (int yy = y; yy < y + tier; yy++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean edge = x == x0 || z == z0 || x == x1 || z == z1;
                        boolean slit = edge && (x + z) % 2 == 1 && yy == y + 1;
                        p.set(x, yy, z, slit ? "window_lit" : edge ? "stone_trim" : "concrete_dark");
                    }
                }
            }
            y += tier;
            x0 += 2;
            z0 += 2;
            x1 -= 2;
            z1 -= 2;
        }
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int h = 10 + rng.nextInt(14);
        p.fill(cx, y, cz, cx, y + h, cz, "steel_mullion");
        p.set(cx, y + h + 1, cz, "aviation_light");
    }

    /** A ring of light around the top floor, the last thing still powered. */
    private void glowingBand(int roofY, int in) {
        for (int x = in; x < w - in; x++) {
            for (int z = in; z < d - in; z++) {
                if (x == in || z == in || x == w - 1 - in || z == d - 1 - in) {
                    p.set(x, roofY + 1, z, "window_lit");
                }
            }
        }
        mast(roofY + 1, w - 4 - in, 3 + in);
    }
}
