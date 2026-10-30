package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Foundry Row (Josiah, 2026-10-30: the coast and industry places, this one last): the Calder Steel
 * Company's Foundry Row Works on its six blocks by five in the old mill quarter, the Glass Line
 * running past its east side, after refs/industry (Carrie Furnaces, Sloss, Duisburg-Nord, Bethlehem,
 * the Homestead and Braddock mill towns):
 * <ul>
 * <li>Nos. 1 and 2 blast furnaces down the middle, each with its three hot-blast stoves behind it,
 * the dust catcher and the downcomer, the skip incline up from the stockhouse bins, the cast house
 * out to one side over the hot metal line ({@link FoundryFurnaces});</li>
 * <li>the coke ovens along the north fence with the coal tower and the quench tower, the blowing
 * engine house and its stacks, the gas holder; the ore yard and its ore bridge, the slag dump; the
 * open hearth shop, the 28 inch bar mill and its cooling bed with the company's name down its
 * side for the trains to read, the machine shop, the water tower ({@link FoundryWorks});</li>
 * <li>outside the gate the Row itself: the company's brick row houses, the Calder Mercantile, the
 * union hall, the Cast House Tavern, the time office, the general office, the wash house, the car
 * park ({@link FoundryRow}).</li>
 * </ul>
 * When it went No. 1 was banked with a cast still in her, and the night turn held her cast house
 * with ingot moulds across the doors; the hall fed the Row until the food ran out. No. 1's notch
 * still glows.
 *
 * <p>The lot's frame: x east along the Row, z south toward it, the ground at y 0.
 */
final class Foundry extends Grounds {
    /** The works' fence along the Row, the gate in it (x), the road across the works (z). */
    static final int FENCE = 316, GATE = 200, SPINE0 = 294, SPINE1 = 301;
    /** The rail spur along the spine, the hot metal line under the cast houses, the slag line. */
    static final int SPUR = 306, HOT = 147, SLAG = 99;
    /** The north-south roads either side of the furnaces. */
    static final int WEST_RD = 141, EAST_RD = 332;
    /** The furnaces' middles. */
    static final int[] FX = {190, 280};
    static final int FZ = 120;

    final Camp camp;
    final Harbour hb;
    final Noise noise;

    Foundry(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.45, 0.3, 0.15);
        this.camp = new Camp(p, rng, 0.5);
        this.hb = new Harbour(p, rng, 0.5);
        this.noise = new Noise(rng.nextLong());
    }

    @Override
    String signs() {
        return "fr_sgn_";
    }

    void build() {
        ground();
        roads();
        rails();
        new FoundryFurnaces(this).build();
        new FoundryWorks(this).build();
        new FoundryRow(this).build();
        fence();
        lamps();
        story();
    }

    // ------------------------------------------------------------------ helpers

    /** A pipe of plate r round from one point to another, stepped along the line. */
    void pipe(int x0, int y0, int z0, int x1, int y1, int z1, double r, String block) {
        int n = Math.max(1, Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0))));
        int k = (int) Math.ceil(r);
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            double cx = x0 + (x1 - x0) * t, cy = y0 + (y1 - y0) * t, cz = z0 + (z1 - z0) * t;
            for (int dx = -k; dx <= k; dx++) {
                for (int dy = -k; dy <= k; dy++) {
                    for (int dz = -k; dz <= k; dz++) {
                        if (dx * dx + dy * dy + dz * dz <= r * r + 0.3) {
                            set((int) Math.round(cx) + dx, (int) Math.round(cy) + dy, (int) Math.round(cz) + dz, block);
                        }
                    }
                }
            }
        }
    }

    /** A smooth drum (Round) of plate on (cx, cz), radius r, from y0 to y1, hooped every few courses. */
    void drum(int cx, int cz, double r, int y0, int y1, String mat, int hoop) {
        Round.wall(p, cx + 0.5, cz + 0.5, r, y0, y1, mat, hoop);
    }

    /** A column of a heap of fr_ stuff (ore, coke, cinder), its top in quarter layers. */
    void pile(int x, int z, double h, String stuff) {
        if (h <= 0) {
            return;
        }
        int whole = (int) Math.floor(h), part = (int) Math.round((h - whole) * 4);
        if (part == 4) {
            whole++;
            part = 0;
        }
        for (int y = 1; y <= whole; y++) {
            set(x, y, z, "fr_" + stuff);
        }
        if (part > 0) {
            set(x, whole + 1, z, "fr_" + stuff + "_" + part);
        }
    }

    /** A heap of stuff on (cx, cz), rx by rz round and h high, its sides lumpy. */
    void heap(int cx, int cz, int rx, int rz, double h, String stuff) {
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dz = -rz; dz <= rz; dz++) {
                double e = Math.hypot(dx / (double) rx, dz / (double) rz);
                if (e < 1) {
                    pile(cx + dx, cz + dz, h * (1 - e) * (1 + noise.get((cx + dx) * 0.3, (cz + dz) * 0.3) * 0.15) + (chance() - 0.5) * 0.4, stuff);
                }
            }
        }
    }

    /** Four steel legs and a deck at y round x0..x1, z0..z1 with its rail. */
    void platform(int x0, int z0, int x1, int z1, int y, boolean legs) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (empty(x, y, z)) {
                    set(x, y, z, "deck_steel");
                }
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge && empty(x, y + 1, z)) {
                    set(x, y + 1, z, "ext_rail[axis=" + (z == z0 || z == z1 ? "x" : "z") + "]");
                }
            }
        }
        if (legs) {
            for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                for (int yy = 1; yy < y; yy++) {
                    if (empty(c[0], yy, c[1])) {
                        set(c[0], yy, c[1], "steel_girder");
                    }
                }
            }
        }
    }

    /** A caged ladder up the outside of something from y0 to y1 at (x, z), facing out. */
    void ladder(int x, int z, int y0, int y1, String facing) {
        for (int y = y0; y <= y1; y++) {
            set(x, y, z, "rd_cage[facing=" + facing + "]");
        }
    }

    /** A steel stair from (x, y0, z) climbing toward dir to y1, a rail beside it. */
    void steelStair(int x, int z, int y0, int y1, String dir) {
        int[] s = Bldg.step(dir);
        for (int i = 0; y0 + i < y1; i++) {
            int sx = x + s[0] * i, sz = z + s[1] * i;
            set(sx, y0 + i + 1, sz, "steel_stairs[facing=" + dir + "]");
            for (int yy = y0 + i + 2; yy <= y0 + i + 4; yy++) {
                if (get(sx, yy, sz) != null && !get(sx, yy, sz).startsWith("ext_rail")) {
                    set(sx, yy, sz, "air");
                }
            }
            for (int yy = 1; yy <= y0 + i; yy++) {
                if (empty(sx, yy, sz) && (i % 4 == 0)) {
                    set(sx, yy, sz, "steel_girder");
                }
            }
        }
    }

    /** Ground for a yard cell: cinder, slag, dirt and gravel, the odd tuft. */
    String yard(int x, int z) {
        double n = noise.get(x * 0.05, z * 0.05) * 0.5 + 0.5, r = chance();
        if (n > 0.62) {
            return r < 0.7 ? "fr_cinder" : "fr_slag";
        }
        if (n < 0.3) {
            return r < 0.5 ? "minecraft:coarse_dirt" : r < 0.8 ? "minecraft:gravel" : "fr_cinder";
        }
        return r < 0.45 ? "fr_cinder" : r < 0.75 ? "minecraft:gravel" : r < 0.9 ? "minecraft:coarse_dirt" : "fr_slag";
    }

    // ------------------------------------------------------------------ the ground

    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (z < FENCE) {
                    set(x, 0, z, yard(x, z));
                    double r = chance();
                    if (r < 0.05) {
                        set(x, 1, z, "gc_grass_" + (1 + rng.nextInt(12)));
                    } else if (r < 0.065) {
                        set(x, 1, z, any("minecraft:dead_bush", "debris_chunk[facing=" + dir() + "]", "paper"));
                    }
                } else if (z >= d - 4) {
                    set(x, 0, z, chance() < 0.12 ? "sidewalk_cracked" : "sidewalk");
                } else {
                    set(x, 0, z, chance() < 0.85 ? "minecraft:grass_block" : "minecraft:coarse_dirt");
                }
            }
        }
    }

    private void road(int x0, int z0, int x1, int z1, boolean alongX) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean mid = alongX ? z == (z0 + z1) / 2 : x == (x0 + x1) / 2;
                set(x, 0, z, mid && Math.floorMod(alongX ? x : z, 6) < 3 ? "road_line_white[axis=" + (alongX ? "x" : "z") + "]"
                        : chance() < 0.3 ? "asphalt_crack" : "asphalt");
                set(x, 1, z, chance() < 0.01 ? "debris_chunk[facing=" + dir() + "]" : "air");
            }
        }
    }

    private void roads() {
        road(4, SPINE0, w - 5, SPINE1, true);
        // in from the Row through the gate, and the roads either side of the furnaces up to the coke works
        road(GATE - 4, SPINE1 + 1, GATE + 4, d - 5, false);
        road(WEST_RD - 3, 40, WEST_RD + 3, SPINE0 - 1, false);
        road(EAST_RD - 3, 40, EAST_RD + 3, SPINE0 - 1, false);
        road(WEST_RD + 4, 40, EAST_RD - 4, 46, true);
    }

    private void rails() {
        hb.railSpur(6, w - 7, SPUR);
        hb.railSpur(WEST_RD + 5, w - 7, HOT);
        hb.railSpur(6, EAST_RD - 4, SLAG);
        // the crossings: planks across the roads
        for (int x : new int[] {WEST_RD, EAST_RD}) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int z : new int[] {HOT, SLAG}) {
                    if (x == EAST_RD && z == SLAG) {
                        continue;
                    }
                    for (int k = -1; k <= 1; k++) {
                        set(x + dx, 0, z + k, "asphalt");
                        set(x + dx, 1, z + k, k == 0 ? "air" : "tb_rail[axis=x]");
                    }
                }
            }
            sign("rail", x + 4, 2, HOT - 3, "west");
            sign("hotmetal", x - 5, 2, HOT + 3, "east");
        }
        for (int dx = -4; dx <= 4; dx++) {
            for (int k = -1; k <= 1; k++) {
                set(GATE + dx, 0, SPUR + k, "asphalt");
                set(GATE + dx, 1, SPUR + k, k == 0 ? "air" : "tb_rail[axis=x]");
            }
        }
    }

    // ------------------------------------------------------------------ the fence, the lights

    private void fence() {
        camp.fence(2, FENCE, GATE - 6, FENCE, 0.04);
        camp.fence(GATE + 6, FENCE, w - 3, FENCE, 0.04);
        camp.fence(2, 2, w - 3, 2, 0.06);
        camp.fence(2, 3, 2, FENCE - 1, 0.05);
        camp.fence(w - 3, 3, w - 3, FENCE - 1, 0.05);
        // the gate: its posts, one leaf swung in and the other gone, the guard's hut
        for (int y = 1; y <= 5; y++) {
            set(GATE - 5, y, FENCE, "fence_post");
            set(GATE + 5, y, FENCE, "fence_post");
        }
        for (int x = GATE - 4; x <= GATE - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                set(GATE - 5, y, FENCE - (x - (GATE - 5)), "chainlink[axis=z]");
            }
        }
        camp.booth(GATE + 7, FENCE + 2);
        sign("gate", GATE - 1, 5, FENCE + 1, "south");
        sign("hats", GATE - 8, 2, FENCE + 1, "south");
        sign("safety", GATE + 13, 2, FENCE - 1, "north");
        sign("days", GATE + 13, 4, FENCE - 1, "north");
    }

    private void lamps() {
        for (int x = 10; x < w - 10; x += 24) {
            camp.lightTower(x, SPINE0 - 3, false);
        }
        for (int z = 60; z < SPINE0 - 10; z += 40) {
            camp.floodMast(WEST_RD - 5, z, 9);
            camp.floodMast(EAST_RD + 5, z, 9);
        }
    }

    // ------------------------------------------------------------------ what happened

    private void story() {
        // the ones who didn't make the cast house or the hall: down along the spine and the hot metal line
        for (int i = 0; i < 80; i++) {
            int x = 6 + rng.nextInt(w - 12), z = 6 + rng.nextInt(FENCE - 12);
            if (!empty(x, 1, z) || get(x, 0, z) == null) {
                continue;
            }
            double r = chance();
            if (r < 0.25) {
                set(x, 1, z, "home_corpse_head[facing=" + dir() + "]");
            } else if (r < 0.45) {
                set(x, 1, z, "blood_splat");
            } else if (r < 0.6) {
                set(x, 1, z, "pw_hardhats[facing=" + dir() + "]");
            } else if (r < 0.75) {
                set(x, 1, z, "paper");
            } else if (r < 0.88) {
                set(x, 1, z, "trash_bag[facing=" + dir() + "]");
            } else {
                set(x, 1, z, "barrel");
            }
        }
        // the cars on the spine where the night turn left them, a fire truck that came for No. 1
        p.vehicle(70.5, 1, SPINE0 + 2.5, 92, "pickup,sedan,suv", rng.nextLong());
        p.vehicle(124.5, 1, SPINE1 - 1.5, 268, "box_truck", rng.nextLong());
        p.vehicle(236.5, 1, SPINE0 + 2.5, 85, "sedan,pickup,hatchback", rng.nextLong());
        p.vehicle(EAST_RD + 0.5, 1, 220.5, 4, "fire_truck,box_truck", rng.nextLong());
        p.vehicle(WEST_RD + 0.5, 1, 250.5, 182, "pickup", rng.nextLong());
    }
}
