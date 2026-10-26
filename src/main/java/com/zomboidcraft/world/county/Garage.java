package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Parking levels under a downtown tower, in the tower's own frame (front at z = 0, x along it,
 * the 14 x 10 core at cx0, cz0 as {@link com.zomboidcraft.world.city.Tower} lays it out). Each
 * level is four blocks: its floor slab, then three of room; B1's ceiling is the lobby floor.
 *
 * Cars come in through a portal at the east end of the front and down a ramp along the east
 * wall (lane A), then the ramps take turns down two lanes side by side, each falling a level over
 * eight blocks in half-block steps: B1 to B2 in lane B, heading back toward the front, B2 to B3 in
 * lane A again. A wall runs between the lanes. Bays are painted along the walls and round the core,
 * a wheel stop at the head of each, columns on the bay lines, a few cars nobody came back for,
 * strip lights mostly dead. The fire stairs and the lifts carry on down from the lobby.
 *
 * The lowest level keeps the service corner at the back on the west side: the freight lift and the
 * electrical room, with a mesh cage in its corner. Under one tower in a city the cage floor has a
 * hatch, and a ladder drops from it to the blast door of a bunker ({@link #bunker}).
 */
public final class Garage {
    static final int H = 4;
    private static final int CORE_W = 14, CORE_D = 10;
    private static final String[] LEVEL = {"blue", "green", "orange"};
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d, levels, cx0, cz0;
    /** The lanes: A by the east wall, then the wall between them, then B, then B's outer wall. */
    private final int laneA, mid, laneB, outer;
    /** The hatch in the cage, on the lowest level's floor. */
    private final int hx, hz;
    /** Spreads grit, cracks and worn patches over the floors in drifts rather than block by block. */
    private final Noise grime;

    public Garage(Placer p, SplittableRandom rng, int w, int d, int levels, int cx0, int cz0) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.levels = levels;
        this.cx0 = cx0;
        this.cz0 = cz0;
        this.laneA = w - 4;
        this.mid = w - 5;
        this.laneB = w - 8;
        this.outer = w - 9;
        this.hx = 6;
        this.hz = d - 2;
        this.grime = new Noise(rng.nextLong());
    }

    /** Whether a tower w x d has the room: the ramps clear of the core, aisles round it. */
    public static boolean fits(int w, int d) {
        int cx0 = (w - CORE_W) / 2, cz0 = (d - CORE_D) / 2;
        return w >= 30 && d >= 28 && cx0 + CORE_W + 1 <= w - 9 && cz0 >= 8;
    }

    static int floorY(int k) {
        return -H * k;
    }

    int bottom() {
        return floorY(levels);
    }

    public void build() {
        for (int k = 1; k <= levels; k++) {
            level(k);
        }
        lobbyFloor();
        core();
        stairs(cx0 + 1);
        stairs(cx0 + 10);
        for (int k = 0; k < levels; k++) {
            ramp(k);
        }
        walls();
        portal();
        for (int k = 1; k <= levels; k++) {
            int y = floorY(k);
            boolean last = k == levels;
            bays(y, 1, 1, w - 11, 5, 0);
            bays(y, 1, 6, 5, d - (last ? 10 : 8), 3);
            bays(y, last ? 13 : 1, d - 6, w - 2, d - 2, 2);
            bays(y, w - 6, 15, w - 2, d - 8, 1);
            if (cx0 >= 15) {
                bays(y, cx0 - 5, cz0, cx0 - 1, cz0 + CORE_D - 1, 1);
            }
            if (d - 7 - (cz0 + CORE_D + 4) >= 4) {
                bays(y, cx0, cz0 + CORE_D, cx0 + CORE_W - 1, cz0 + CORE_D + 4, 0);
            }
            lights(y);
            signs(k, y);
            litter(y);
        }
        service(floorY(levels));
    }

    // ------------------------------------------------------------------ the levels

    /** The slab, the room over it, the walls round it in the level's colour. */
    private void level(int k) {
        int y = floorY(k);
        String band = "park_band_" + LEVEL[(k - 1) % LEVEL.length];
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                if (edge) {
                    p.set(x, y, z, "bunk_concrete");
                    p.set(x, y + 1, z, band);
                    double r = rng.nextDouble();
                    p.set(x, y + 2, z, r < 0.2 ? "park_wall_b" : "park_wall");
                    p.set(x, y + 3, z, r < 0.12 ? "park_wall_soot" : r < 0.3 ? "park_wall_b" : "park_wall");
                    continue;
                }
                p.set(x, y, z, floor(x, z, k));
                for (int yy = y + 1; yy < y + H; yy++) {
                    p.set(x, yy, z, "air");
                }
            }
        }
    }

    private String floor(int x, int z, int k) {
        double g = grime.fbm(x, z + k * 37.0, 6, 2) * 0.5 + 0.5;
        if (g > 0.74 || rng.nextDouble() < 0.02) {
            return rng.nextDouble() < 0.7 ? "park_floor_grit" : "park_floor_crack";
        }
        // tyre tracks down the aisles, along whichever way the aisle runs there
        boolean aisleX = z > 5 && z < cz0 || z > cz0 + CORE_D - 1 && z < d - 6;
        boolean aisleZ = x > 5 && x < cx0 || x > cx0 + CORE_W - 1 && x < outer;
        if (aisleX ^ aisleZ && g > 0.3) {
            return "park_floor_tyre[axis=" + (aisleX ? "x" : "z") + "]";
        }
        return g > 0.6 ? "park_floor_b" : "park_floor";
    }

    /** Lobby tiles over the garage get a concrete underside: they are B1's ceiling. */
    private void lobbyFloor() {
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                String b = p.get(x, 0, z);
                if ("lobby_floor_dark".equals(b) || "lobby_floor_white".equals(b)) {
                    p.set(x, 0, z, b.equals("lobby_floor_dark") ? "park_lobby_dark" : "park_lobby_white");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the core, carried down

    /** The core's walls, its lift shafts down to a pit, lift doors on every level. */
    private void core() {
        int sa = cx0 + 4, lift = cx0 + 5, sb = cx0 + 9, back = cz0 + 4;
        int pit = bottom();
        for (int y = pit; y < 0; y++) {
            for (int x = cx0; x < cx0 + CORE_W; x++) {
                for (int z = cz0; z < cz0 + CORE_D; z++) {
                    boolean edge = x == cx0 || x == cx0 + CORE_W - 1 || z == cz0 || z == cz0 + CORE_D - 1
                            || x == sa || x == sb || (x >= lift && x <= lift + 3 && z == back);
                    boolean shaft = x == lift + 1 && z < back;
                    boolean plant = x >= lift && x <= lift + 3 && z > back;
                    boolean ring = x == cx0 || x == cx0 + CORE_W - 1 || z == cz0 || z == cz0 + CORE_D - 1;
                    if (ring && y > pit) {
                        // the outside of the core, painted like the walls round the level
                        int k = Math.floorDiv(-y + H - 1, H), row = y - floorY(k);
                        p.set(x, y, z, row == 0 ? "bunk_concrete" : row == 1 ? "park_band_" + LEVEL[(k - 1) % LEVEL.length] : "park_wall");
                    } else if (edge || shaft || plant) {
                        p.set(x, y, z, "concrete_dark");
                    } else {
                        p.set(x, y, z, y == pit ? "floor_concrete" : "air");
                    }
                }
            }
        }
        for (int k = 1; k <= levels; k++) {
            int y = floorY(k);
            for (int yy = y + 1; yy <= y + 2; yy++) {
                p.set(lift, yy, cz0, "elevator_door[facing=north]");
                p.set(lift + 2, yy, cz0, "elevator_door[facing=north]");
            }
            p.set(lift + 1, y + 2, cz0 - 1, "park_st_lifts[facing=north]");
        }
    }

    /**
     * The fire stair carried down below the lobby, as the tower builds it above: switchback
     * flights in the 3-wide well whose inner x runs x0..x0 + 2, a landing by the door each level.
     */
    private void stairs(int x0) {
        int land = cz0 + 1, flight = cz0 + 3, far = cz0 + 5;
        for (int k = levels; k >= 1; k--) {
            int y = floorY(k), next = y + H;
            p.fill(x0, y, land, x0 + 2, y, land + 1, "floor_concrete");
            Doors.abandoned(p, rng, x0 + 1, y + 1, cz0, Doors.STEEL, "south", Doors.hinge(rng));
            p.set(x0 + 1, y + 3, land + 1, "ceiling_light_off");
            p.set(x0, y + 2, cz0 - 1, "bunk_st_b" + k + "[facing=north]");
            int h = y;
            int side = 0;
            while (h < next) {
                if (side == 0) {
                    for (int z = flight; z <= flight + 1 && h < next; z++) {
                        h++;
                        p.set(x0, h, z, "stairs_concrete[facing=south]");
                    }
                    if (h < next) {
                        p.fill(x0, h, far, x0 + 2, h, far + 3, "floor_concrete");
                    }
                } else {
                    for (int z = flight + 1; z >= flight && h < next; z--) {
                        h++;
                        p.set(x0 + 2, h, z, "stairs_concrete[facing=north]");
                    }
                    if (h < next) {
                        p.fill(x0, h, land, x0 + 2, h, land + 1, "floor_concrete");
                    }
                }
                side ^= 1;
            }
            for (int yy = y + 2; yy < next; yy++) {
                p.set(x0 + 1, yy, flight, "railing[facing=west]");
            }
        }
        // headroom where the last flight comes up under the lobby's first one
        for (int z = flight + 1; z <= far + 3; z++) {
            for (int x = x0; x <= x0 + 2; x++) {
                p.set(x, 0, z, "air");
            }
        }
    }

    // ------------------------------------------------------------------ ramps

    /**
     * The ramp from level k down to k + 1 (k = 0 is the street): eight cells of three lanes
     * wide, each half a block lower than the last, solid down to the level below.
     */
    private void ramp(int k) {
        int top = k == 0 ? 0 : floorY(k), low = floorY(k + 1);
        boolean a = k == 0 || k % 2 == 0;
        int lx = a ? laneA : laneB;
        int roof = k == 0 ? 3 : top + H - 1;
        for (int t = 0; t < 8; t++) {
            int z = k == 0 ? 1 + t : k % 2 == 1 ? 11 - t : 4 + t;
            int s2 = 2 * (top + 1) - (t + 1);  // twice the surface's height
            int whole = Math.floorDiv(s2, 2);
            boolean half = (s2 & 1) == 1;
            for (int x = lx; x < lx + 3; x++) {
                for (int y = low + 1; y < whole; y++) {
                    p.set(x, y, z, "bunk_concrete");
                }
                if (half) {
                    p.set(x, whole, z, "garage_ramp_half");
                } else {
                    p.set(x, whole - 1, z, t % 3 == 1 ? "park_floor" : "park_floor_tyre[axis=z]");
                }
                for (int y = half ? whole + 1 : whole; y <= roof; y++) {
                    p.set(x, y, z, "air");
                }
            }
        }
    }

    /** The wall between the lanes, B's outer wall, and the box round the street ramp in the lobby. */
    private void walls() {
        for (int k = 1; k <= levels; k++) {
            int y = floorY(k);
            for (int z = 1; z <= 11; z++) {
                for (int yy = y + 1; yy < y + H; yy++) {
                    if (z >= 4 || k == 1) {
                        p.set(mid, yy, z, yy == y + 1 ? "park_column" : "park_wall");
                    }
                    if (z >= 4) {
                        p.set(outer, yy, z, yy == y + 1 ? "park_band_" + LEVEL[(k - 1) % LEVEL.length] : "park_wall");
                    }
                }
            }
        }
        for (int z = 1; z <= 8; z++) {
            for (int y = 0; y <= 3; y++) {
                p.set(mid, y, z, "concrete_panel");
            }
            for (int x = mid; x <= w - 2; x++) {
                p.set(x, 4, z, "concrete_panel");
            }
        }
        for (int x = mid; x <= w - 2; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, 9, "concrete_panel");
            }
        }
    }

    /** The way in through the front: the facade opened over the ramp, a sign, the height bar. */
    private void portal() {
        for (int x = laneA; x < laneA + 3; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, 0, "air");
            }
            p.set(x, 0, 0, "park_floor_tyre[axis=z]");
        }
        // a concrete lintel over the opening, striped jambs where the bumpers caught them
        for (int x = laneA - 1; x <= laneA + 3; x++) {
            p.set(x, 4, 0, "concrete_dark");
            p.set(x, 5, 0, "concrete_dark");
        }
        p.set(laneA - 1, 1, 0, "park_column");
        p.set(laneA + 3, 1, 0, "park_column");
        p.set(laneA + 1, 4, -1, "bunk_st_clearance[facing=north]");
        if (!Boards.fascia(p, rng, "parking", laneA - 6, 5, -1, 10)) {
            p.set(laneA, 5, -1, "park_st_exit[facing=north]");
        }
        // inside the portal: the ticket barrier's post, its arm long since snapped off
        p.set(mid, 1, 1, "steel_yellow");
        p.set(laneA, floorY(1) + 2, 10, "park_st_exit[facing=east]");
    }

    // ------------------------------------------------------------------ bays

    /**
     * A row of bays filling the box (corners included), their heads against side head (0 north,
     * 1 east, 2 south, 3 west): lines between them, a wheel stop at each head, a column on every
     * third line at the open end, and now and then a car still parked.
     */
    private void bays(int y, int x0, int z0, int x1, int z1, int head) {
        if (x1 - x0 < 2 || z1 - z0 < 2) {
            return;
        }
        boolean alongX = head == 0 || head == 2;  // bays side by side along x, depth along z
        int a0 = alongX ? x0 : z0, a1 = alongX ? x1 : z1;
        int b0 = alongX ? z0 : x0, b1 = alongX ? z1 : x1;
        int headB = head == 0 || head == 3 ? b0 : b1, openB = headB == b0 ? b1 : b0;
        String stop = "park_wheelstop[facing=" + DIRS[(head + 2) & 3] + "]";
        String lineAxis = alongX ? "z" : "x";
        int lines = 0;
        for (int a = a0; a <= a1; a += 3, lines++) {
            for (int b = b0; b <= b1; b++) {
                if (clear(alongX ? a : b, y, alongX ? b : a)) {
                    p.set(alongX ? a : b, y, alongX ? b : a, rng.nextDouble() < 0.85 ? "park_line[axis=" + lineAxis + "]" : "park_floor_b");
                }
            }
            if (lines % 3 == 0 && clear(alongX ? a : openB, y + 1, alongX ? openB : a)) {
                int cx = alongX ? a : openB, cz = alongX ? openB : a;
                p.set(cx, y + 1, cz, "park_column");
                p.set(cx, y + 2, cz, "park_wall");
                p.set(cx, y + 3, cz, "park_wall");
            }
            if (a + 2 > a1) {
                break;
            }
            // the bay between this line and the next
            for (int i = 1; i <= 2; i++) {
                int sx = alongX ? a + i : headB, sz = alongX ? headB : a + i;
                if (clear(sx, y + 1, sz) && rng.nextDouble() < 0.9) {
                    p.set(sx, y + 1, sz, stop);
                }
                if (rng.nextDouble() < 0.2) {
                    int ox = alongX ? a + i : (b0 + b1) / 2, oz = alongX ? (b0 + b1) / 2 : a + i;
                    if (clear(ox, y, oz)) {
                        p.set(ox, y, oz, "park_floor_oil");
                    }
                }
            }
            int c1x = alongX ? a + 1 : (b0 + b1) / 2, c1z = alongX ? (b0 + b1) / 2 : a + 1;
            if (rng.nextDouble() < 0.14 && clear(c1x, y + 1, c1z) && clear(alongX ? c1x + 1 : c1x, y + 1, alongX ? c1z : c1z + 1)) {
                double c = a + 2.0, m = (b0 + b1 + 1) / 2.0;
                float yaw = switch (head) {
                    case 0 -> 180f;
                    case 1 -> 270f;
                    case 2 -> 0f;
                    default -> 90f;
                };
                if (rng.nextDouble() < 0.3) {
                    yaw += 180f;  // backed in
                }
                p.car(alongX ? c : m, y + 1, alongX ? m : c, yaw, 0, 5.2, rng.nextLong());
            }
        }
    }

    /** Free to paint or stand on: not in the core, the ramps or the service corner. */
    private boolean clear(int x, int y, int z) {
        if (x < 1 || z < 1 || x > w - 2 || z > d - 2) {
            return false;
        }
        if (x >= cx0 && x < cx0 + CORE_W && z >= cz0 && z < cz0 + CORE_D) {
            return false;
        }
        if (x >= outer && z <= 14) {
            return false;
        }
        String b = p.get(x, y, z);
        return b == null || b.startsWith("park_floor") || b.equals("air");
    }

    // ------------------------------------------------------------------ dressing

    /** Strip lights down the aisles: most dead, a few still lit or stuttering, some fallen. */
    private void lights(int y) {
        for (int x = 3; x < w - 1; x += 4) {
            for (int z = 3; z < d - 1; z += 4) {
                if (!clear(x, y + 3, z)) {
                    continue;
                }
                String axis = z > cz0 && z < cz0 + CORE_D ? "z" : "x";
                double r = rng.nextDouble();
                if (r < 0.06) {
                    p.set(x, y + 3, z, "bunk_tube_lit[axis=" + axis + "]");
                } else if (r < 0.16) {
                    p.set(x, y + 3, z, "bunk_tube_flicker[axis=" + axis + "]");
                } else if (r < 0.2) {
                    p.set(x, y + 3, z, "bunk_tube_dangle[axis=" + axis + "]");
                } else if (r < 0.25 && clear(x, y + 1, z)) {
                    p.set(x, y + 1, z, "bunk_tube_fallen[axis=" + axis + "]");
                    p.set(x, y + 3, z, "bunk_cable_loose");
                } else {
                    p.set(x, y + 3, z, "bunk_tube[axis=" + axis + "]");
                }
            }
        }
        // pipes along the back wall under the slab
        for (int x = 1; x < w - 1; x++) {
            if (clear(x, y + 3, d - 2)) {
                p.set(x, y + 3, d - 2, x % 9 == 4 ? "bunk_pipes_valve[facing=north]" : "bunk_pipes[facing=north]");
            }
        }
    }

    /** The level's name on the walls and by the lifts, the way out, an extinguisher or two. */
    private void signs(int k, int y) {
        String st = "bunk_st_b" + k;
        for (int z = 8; z < d - 8; z += 12) {
            if (clear(1, y + 2, z)) {
                p.set(1, y + 2, z, st + "[facing=east]");
            }
        }
        if (clear(cx0 + 4, y + 2, cz0 - 1)) {
            p.set(cx0 + 4, y + 2, cz0 - 1, "park_st_stairs[facing=north]");
        }
        for (int x = 8; x < w - 10; x += 14) {
            if (clear(x, y + 2, d - 2)) {
                p.set(x, y + 2, d - 2, "bunk_extinguisher[facing=north]");
            }
        }
        // arrows painted round the one-way loop: south past the ramps, west along the back, north
        // up the west side, east along the front
        int front = (6 + cz0 - 1) / 2, west = (6 + cx0 - 1) / 2;
        int back = (cz0 + CORE_D + d - 7) / 2, east = (cx0 + CORE_W + w - 7) / 2;
        for (int x = 9; x < w - 11; x += 10) {
            arrow(x, y, front, "east");
            arrow(w - 1 - x, y, back, "west");
        }
        for (int z = cz0; z < d - 9; z += 10) {
            arrow(west, y, z, "north");
            arrow(east, y, d - 1 - z, "south");
        }
    }

    private void arrow(int x, int y, int z, String facing) {
        if (clear(x, y, z) && p.get(x, y, z) != null && p.get(x, y, z).startsWith("park_floor")) {
            p.set(x, y, z, "park_arrow[facing=" + facing + "]");
        }
    }

    private void litter(int y) {
        int n = w * d / 70;
        for (int i = 0; i < n; i++) {
            int x = 1 + rng.nextInt(w - 2), z = 1 + rng.nextInt(d - 2);
            if (!clear(x, y + 1, z) || p.get(x, y + 1, z) != null) {
                continue;
            }
            double r = rng.nextDouble();
            String b = r < 0.22 ? "paper" : r < 0.36 ? "trash_bag[facing=" + DIRS[rng.nextInt(4)] + "]" : r < 0.46 ? "bunk_puddle"
                    : r < 0.58 ? "bunk_grit" : r < 0.66 ? "bunk_drum_water"
                    : r < 0.74 ? "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]" : r < 0.84 ? "glass_shards"
                    : r < 0.92 ? "rubble_pile" : "debris_chunk[facing=" + DIRS[rng.nextInt(4)] + "]";
            p.set(x, y + 1, z, b);
        }
        // water down the walls from the slab, cobwebs in the corners, now and then a drag of blood
        for (int i = 0; i < n / 2; i++) {
            int side = rng.nextInt(4);
            int along = 1 + rng.nextInt((side & 1) == 0 ? w - 2 : d - 2);
            int x = side == 1 ? w - 2 : side == 3 ? 1 : along, z = side == 0 ? 1 : side == 2 ? d - 2 : along;
            String in = DIRS[(side + 2) & 3];
            int yy = y + 2 + rng.nextInt(2);
            if (clear(x, yy, z) && p.get(x, yy, z) == null) {
                p.set(x, yy, z, (rng.nextDouble() < 0.7 ? "bunk_leak" : "bunk_cobweb") + "[facing=" + in + "]");
            }
        }
        for (int i = 0; i < 2; i++) {
            int x = 1 + rng.nextInt(w - 2), z = 1 + rng.nextInt(d - 2);
            if (rng.nextDouble() < 0.4 && clear(x, y + 1, z) && p.get(x, y + 1, z) == null) {
                p.set(x, y + 1, z, "home_blood_drag[facing=" + DIRS[rng.nextInt(4)] + "]");
            }
        }
    }

    // ------------------------------------------------------------------ the service corner

    /** The electrical room and the freight lift at the back of the lowest level, on the west. */
    private void service(int y) {
        int zw = d - 8;
        // the electrical room: x 1..6, z zw + 1 .. d - 2, its walls on x = 7 and z = zw
        for (int yy = y + 1; yy < y + H; yy++) {
            for (int x = 1; x <= 7; x++) {
                p.set(x, yy, zw, "park_wall");
            }
            for (int z = zw; z <= d - 2; z++) {
                p.set(7, yy, z, "park_wall");
            }
            for (int x = 1; x <= 6; x++) {
                for (int z = zw + 1; z <= d - 2; z++) {
                    p.set(x, yy, z, "air");
                }
            }
        }
        for (int x = 1; x <= 6; x++) {
            for (int z = zw + 1; z <= d - 2; z++) {
                p.set(x, y, z, rng.nextDouble() < 0.3 ? "park_floor_grit" : "park_floor");
            }
        }
        for (int x = 2; x <= 6; x++) {
            p.set(x, y, zw - 1, "park_line_yellow[axis=x]");
        }
        Doors.place(p, 4, y + 1, zw, "bunk_door_elec", "north", "left", false);
        p.set(2, y + 2, zw - 1, "park_st_authorised[facing=north]");
        p.set(6, y + 2, zw - 1, "bunk_fusebox[facing=north]");
        // switchboards down the west wall, cable trays over them
        for (int z = zw + 1; z <= d - 3; z++) {
            p.set(1, y + 1, z, "bunk_switchboard[facing=east]");
            p.set(1, y + 2, z, "bunk_switchboard_top[facing=east]");
            p.set(2, y + 3, z, "bunk_cable_tray[axis=z]");
        }
        p.set(3, y + 3, zw + 3, "bunk_lamp_hang");
        p.set(6, y + 2, zw + 1, "bunk_fusebox[facing=west]");
        // the cage in the back corner: x 5..6, z d - 4 .. d - 2 behind mesh on x = 4 and z = d - 5
        for (int yy = y + 1; yy < y + H; yy++) {
            for (int z = d - 4; z <= d - 2; z++) {
                p.set(4, yy, z, "bunk_cage[axis=z]");
            }
            p.set(4, yy, d - 5, "bunk_cage[axis=x]");
            p.set(6, yy, d - 5, "bunk_cage[axis=x]");
        }
        Doors.place(p, 5, y + 1, d - 5, "bunk_door_cage", "north", "left", false);
        // the freight lift: a shaft x 9..11 behind three doors in the wall at z = d - 6
        int fz = d - 6;
        for (int yy = y + 1; yy < y + H; yy++) {
            for (int x = 8; x <= 12; x++) {
                p.set(x, yy, fz, "park_wall");
            }
            for (int z = fz; z <= d - 2; z++) {
                p.set(8, yy, z, "park_wall");
                p.set(12, yy, z, "park_wall");
            }
        }
        for (int x = 9; x <= 11; x++) {
            p.set(x, y + 1, fz, "elevator_door[facing=north]");
            p.set(x, y + 2, fz, "elevator_door[facing=north]");
            p.set(x, y, fz - 1, "park_line_yellow[axis=x]");
        }
        p.set(10, y + 3, fz - 1, "park_st_freight[facing=north]");
    }

    /** Junk in the cage of a garage with nothing under it: drums, a crate, a dead transformer. */
    public void cageJunk() {
        int y = bottom();
        p.set(5, y + 1, d - 2, "bunk_rack_drums[facing=north]");
        p.set(6, y + 1, d - 2, "bunk_crate_ration[facing=north]");
        p.set(6, y + 1, d - 3, "cardboard_box[facing=west]");
    }

    // ------------------------------------------------------------------ the way down

    /**
     * The hatch in the cage and the ladder below it, down to a vestibule in front of a bunker's
     * blast door, the bunker built beyond it. The bunker goes the first way round (of four, in a
     * random order) that keeps it inside the place: area {x0, z0, x1, z1} in place coordinates.
     * Returns the bunker's frame, or null when no way fits.
     */
    public Placer bunker(Bunker.State state, int[] area) {
        int y = bottom();
        int yB = Math.min(y - 10, -22);
        int[] order = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        long seed = rng.nextLong();
        for (int q : order) {
            // the shaft sits at (0, -4) in the bunker's frame: put that on the hatch
            int[] r = turn(q, 0, -4);
            Placer bp = p.turned(hx - r[0], yB, hz - r[1], q);
            Bunker b = new Bunker(bp, new SplittableRandom(seed), Bunker.Kind.TOWER, state);
            int[] box = b.bounds();
            if (!inside(bp, box[0] - 1, -6, box[2] + 1, box[3] + 1, area)) {
                continue;
            }
            vestibule(bp, y - yB, state);
            b.build();
            p.set(hx, y, hz, "bunk_hatch[facing=north,half=top,open=false]");
            p.set(hx - 1, y + 2, hz, "bunk_keyswitch[facing=north]");
            p.mark("bunker " + state.name().toLowerCase(java.util.Locale.ROOT) + " tower", hx, y + 1, hz);
            bp.mark("bunker door", 0, 1, -2);
            return bp;
        }
        cageJunk();
        return null;
    }

    private static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static boolean inside(Placer bp, int x0, int z0, int x1, int z1, int[] area) {
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(c[0], c[1]);
            if (at[0] < area[0] || at[1] < area[1] || at[0] > area[2] || at[1] > area[3]) {
                return false;
            }
        }
        return true;
    }

    /**
     * In the bunker's frame: the narrow concrete passage in front of the blast door (x 0..1, z -4..-1)
     * and the lined shaft over its far end, a ladder up the shaft's north side to the hatch rise rows up.
     */
    private void vestibule(Placer bp, int rise, Bunker.State state) {
        for (int x = -1; x <= 2; x++) {
            for (int z = -5; z <= -1; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean room = x >= 0 && x <= 1 && z >= -4 && y >= 1 && y <= 3;
                    bp.set(x, y, z, room ? "air" : y == 0 ? "bunk_floor_dirty" : "bunk_concrete");
                }
            }
        }
        for (int y = 5; y < rise; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -5; z <= -3; z++) {
                    bp.set(x, y, z, x == 0 && z == -4 ? "air" : "bunk_concrete");
                }
            }
        }
        for (int y = 1; y < rise; y++) {
            bp.set(0, y, -4, "bunk_ladder[facing=south]");
        }
        bp.set(1, 3, -4, state == Bunker.State.SEALED ? "bunk_lamp_red_lit[facing=south]" : "bunk_lamp_red[facing=south]");
        bp.set(0, 2, -2, "bunk_st_b4[facing=east]");
        bp.set(1, 2, -1, "bunk_keyswitch[facing=west]");
        bp.set(1, 1, -3, state == Bunker.State.BREACHED ? "home_blood_drag[facing=north]" : "bunk_grit");
        if (state != Bunker.State.SEALED) {
            bp.set(0, 1, -2, "paper");
            bp.set(1, 1, -2, "glass_shards");
        }
    }
}
