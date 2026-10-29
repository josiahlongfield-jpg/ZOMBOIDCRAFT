package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The buildings and gear of a farm, drawn through a {@link Placer} in their own frame like
 * {@link Bldg}'s (front along local z = 0 facing north, x along the front):
 * <ul>
 * <li>barns: the red gambrel barn with its white X doors, loft door and hay hood, stalls below
 * and a hay loft above; the long gable horse barn with its aisle of stalls; the dairy's free-stall
 * barn, open-sided under a low roof, and its milk house; the pole barn full of machinery;</li>
 * <li>silos (concrete stave, old brick, the blue glass-lined kind) with their domes and caged
 * ladders, corrugated grain bins with cone roofs, the leg that fills them, a wire corn crib;</li>
 * <li>the farm windmill on its lattice tower over a stock tank;</li>
 * <li>the small things: a tractor and hay wagon, a chicken coop and its run, a well, an
 * outhouse, a woodpile, beehives, a scarecrow, the fuel tank, a roadside produce stand;</li>
 * <li>what the outbreak left: graves dug in the yard, a burnt pyre, a barricade of hay bales,
 * someone's last camp up in the loft.</li>
 * </ul>
 */
final class Farmstead {
    /** White-painted trim: corner boards, door frames, the edge of a gable. */
    static final String TRIM = "minecraft:pale_oak_planks";
    /** Tin roofs: galvanized, gone dull, and rusted through. */
    static final String[] TIN = {"minecraft:polished_andesite", "minecraft:polished_tuff",
            "minecraft:waxed_exposed_cut_copper"};
    /** Galvanized steel: domes, cones, tanks. */
    static final String GALV = "minecraft:polished_andesite";
    static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    /** How hard the farm was hit, 0 (left as it was) to 1 (overrun, burnt). */
    private final double hit;

    Farmstead(Placer p, SplittableRandom rng, double hit) {
        this.p = p;
        this.rng = rng;
        this.hit = hit;
        this.b = new Bldg(p, rng);
        b.damage = 0.3 + hit * 0.6;
        b.boarded = 0.1 + hit * 0.2;
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private double chance() {
        return rng.nextDouble();
    }

    private static boolean ring(int dx, int dz, double r) {
        double dd = Math.sqrt(dx * dx + dz * dz);
        return dd <= r && dd > r - 1.2;
    }

    private static boolean disc(int dx, int dz, double r) {
        return dx * dx + dz * dz <= r * r;
    }

    private static String stairs(String family, String facing, boolean top) {
        return family + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    /** A roof block, unless the weather or a fire took it. */
    private void tile(int x, int y, int z, String block) {
        if (chance() < 0.012 + hit * 0.05) {
            return;
        }
        p.set(x, y, z, block);
    }

    /** The cardinal a stair on a ring round (0, 0) faces to rise toward the middle. */
    private static String inward(int dx, int dz) {
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx > 0 ? "west" : "east";
        }
        return dz > 0 ? "north" : "south";
    }

    // ------------------------------------------------------------------ roofs

    /**
     * A gambrel roof over walls x0..x1 (an even width), its ridge running along z from z0 to z1,
     * sitting on y, overhanging a block all round: a steep lower slope, then a shallow upper one.
     * The gable ends are filled with `wall` and trimmed white under the roof line. Returns the
     * ridge's top y.
     */
    int gambrelRoof(int x0, int x1, int z0, int z1, int y, String family, String wall) {
        int a0 = x0 - 1, a1 = x1 + 1, span = a1 - a0 + 1, half = span / 2;
        int[] hh = new int[half];
        int steep = Math.max(2, (int) Math.round(half * 0.4));
        for (int i = 0; i < half; i++) {
            hh[i] = i == 0 ? 2 : i < steep ? hh[i - 1] + 4 : hh[i - 1] + (i % 2 == 0 ? 2 : 1);
        }
        int top = y;
        for (int i = 0; i < half; i++) {
            int t = hh[i], tp = i == 0 ? 0 : hh[i - 1];
            roofColumn(a0 + i, x0, x1, z0, z1, y, t, tp, family, "east", wall);
            roofColumn(a1 - i, x0, x1, z0, z1, y, t, tp, family, "west", wall);
            top = Math.max(top, y + (t + 1) / 2 - 1);
        }
        if (span % 2 == 1) {
            int t = hh[half - 1] + 1;
            roofColumn(a0 + half, x0, x1, z0, z1, y, t, hh[half - 1], family, null, wall);
            top = Math.max(top, y + (t + 1) / 2 - 1);
        }
        return top;
    }

    /** One column of a profiled roof: t half-blocks high over the eave, tp its outer neighbour's. */
    private void roofColumn(int x, int x0, int x1, int z0, int z1, int y, int t, int tp, String family, String face, String wall) {
        int full = t / 2;
        boolean slab = (t & 1) == 1;
        int from = Math.max(0, tp / 2 - 1);
        if (x >= x0 && x <= x1) {
            for (int z : new int[] {z0, z1}) {
                for (int yy = y; yy < y + from; yy++) {
                    p.set(x, yy, z, yy == y + from - 1 ? TRIM : b.wall(wall, yy));
                }
            }
        }
        String block = Bldg.full(family);
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int k = from; k < full; k++) {
                boolean cap = k == full - 1 && !slab && face != null;
                tile(x, y + k, z, cap ? stairs(family, face, false) : block);
            }
            if (slab) {
                tile(x, y + full, z, family + "_slab[type=bottom]");
            }
        }
    }

    /** A shallow lean-to roof of slabs from x0 (against a wall, at y) falling a half block a column to x1. */
    private void leanRoof(int x0, int x1, int z0, int z1, int y, String family) {
        int dir = Integer.signum(x1 - x0);
        for (int i = 0; i <= Math.abs(x1 - x0); i++) {
            int h = 2 * y + 1 - i;  // in half blocks
            String s = (h & 1) == 1 ? family + "_slab[type=bottom]" : family + "_slab[type=top]";
            int yy = (h & 1) == 1 ? (h - 1) / 2 : h / 2 - 1;
            for (int z = z0; z <= z1; z++) {
                tile(x0 + dir * i, yy, z, s);
            }
        }
    }

    /** A small cupola on a ridge: a louvred box, a cap, the weathervane. */
    private void cupola(int x, int z, int y, int size, String family) {
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                p.set(x + i, y, z + j, TRIM);
                p.set(x + i, y + 1, z + j, "minecraft:pale_oak_fence");
                p.set(x + i, y + 2, z + j, family + "_slab[type=bottom]");
            }
        }
        p.set(x + size / 2, y + 3, z + size / 2, "minecraft:lightning_rod");
    }

    // ------------------------------------------------------------------ barns

    /** What a barn was built for: its ground floor. */
    enum Use { STALLS, HORSES, HAY }

    /** Sliding barn doors filling x c-2..c+1 of a gable end (in q's frame): closed, one open, both open, or gone. */
    private void bigDoors(Placer q, int c, int state) {
        for (int y = 1; y <= 4; y++) {
            q.set(c - 3, y, 0, TRIM);
            q.set(c + 2, y, 0, TRIM);
        }
        for (int x = c - 2; x <= c + 1; x++) {
            q.set(x, 4, 0, TRIM);
            for (int y = 1; y <= 3; y++) {
                q.set(x, y, 0, "air");
            }
            q.set(x, 0, 0, "floor_concrete");
        }
        // the left leaf (seen from outside) is the high-x one; each slides out over the wall beside it
        for (int leaf = 0; leaf < 2; leaf++) {
            boolean open = state == 2 || state == 1 && leaf == 0;
            if (state == 3 && (leaf == 0 || chance() < 0.5)) {
                continue;
            }
            for (int i = 0; i < 2; i++) {
                int x = leaf == 0 ? c + 1 - i : c - 1 - i;
                int z = 0;
                if (open) {
                    x += leaf == 0 ? 2 : -2;
                    z = -1;
                }
                for (int j = 0; j < 3; j++) {
                    q.set(x, 3 - j, z, "barn_door_" + i + j + "[facing=north]");
                }
            }
        }
        if (state == 3) {
            for (int i = 0; i < 3; i++) {
                q.setIfAir(c - 3 + rng.nextInt(6), 1, -1 - rng.nextInt(3), pick("debris_chunk[facing=east]", "minecraft:spruce_slab[type=bottom]"));
            }
        }
    }

    /** The loft door high in a gable end, x c-1..c, from y; a hay hood over it with its hook. */
    private void loftDoor(Placer q, int c, int y, boolean open) {
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                q.set(c - i, y + 1 - j, 0, open ? "air" : "barn_loft_" + i + j + "[facing=north]");
            }
        }
        if (open) {
            // swung out flat against the wall beside the opening
            for (int j = 0; j < 2; j++) {
                q.set(c + 1, y + 1 - j, -1, "barn_loft_0" + j + "[facing=north]");
                q.set(c - 2, y + 1 - j, -1, "barn_loft_1" + j + "[facing=north]");
            }
            q.set(c - 1, y - 1, 1, "minecraft:hay_block");
        }
        for (int x = c - 2; x <= c + 1; x++) {
            q.set(x, y + 2, 0, TRIM);
        }
    }

    /**
     * A barn w wide (even) and d deep, its gable end with the big doors to the front: a fieldstone
     * footing, painted board walls with white corner boards, small windows down the sides, a man
     * door, a gambrel (or a plain gable) roof with cupolas on the ridge and a hay hood over the
     * loft door. Inside, a drive down the middle, the stalls or stanchions either side under a hay
     * loft, the feed room by the doors. Returns the ridge's top y.
     */
    int barn(int w, int d, String wall, String roof, boolean gambrel, Use use) {
        int top = w >= 16 ? 6 : 5;
        int c = w / 2;
        int loft = 4;
        b.soot = hit > 0.75 ? 0.45 : 0;
        // floor: packed earth and old straw, concrete down the drive
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean drive = x >= c - 2 && x <= c + 1;
                p.set(x, 0, z, drive ? b.wall("floor_concrete", 1) : pick("minecraft:packed_mud", "minecraft:coarse_dirt", "minecraft:dirt"));
            }
        }
        // the footing, walls, corner boards
        for (int x = 0; x < w; x++) {
            p.set(x, 1, 0, pick("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks"));
            p.set(x, 1, d - 1, pick("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks"));
        }
        for (int z = 0; z < d; z++) {
            p.set(0, 1, z, pick("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks"));
            p.set(w - 1, 1, z, pick("minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:stone_bricks"));
        }
        b.walls(0, 0, w - 1, d - 1, 2, top, wall);
        for (int y = 2; y <= top; y++) {
            p.set(0, y, 0, TRIM);
            p.set(w - 1, y, 0, TRIM);
            p.set(0, y, d - 1, TRIM);
            p.set(w - 1, y, d - 1, TRIM);
        }
        int ridge;
        if (gambrel) {
            ridge = gambrelRoof(0, w - 1, 0, d - 1, top + 1, roof, wall);
        } else {
            b.gable(0, w - 1, 0, d - 1, top + 1, roof, true, wall, 1);
            ridge = top + 1 + (w + 2) / 2;
        }
        // the front and back gable ends
        Placer back = p.turned(w - 1, 0, d - 1, 2);
        double r = chance();
        bigDoors(p, c, r < 0.25 ? 0 : r < 0.6 ? 1 : r < 0.85 ? 2 : 3);
        bigDoors(back, c, chance() < 0.6 ? 0 : 1);
        loftDoor(p, c, top + 2, chance() < 0.4);
        if (gambrel) {
            loftDoor(back, c, top + 2, false);
            // the hay hood: the ridge carried out over the loft door, a hook hanging from it
            for (int x = c - 2; x <= c + 1; x++) {
                String face = x < c ? "east" : "west";
                int yy = x == c - 2 || x == c + 1 ? ridge - 1 : ridge;
                for (int z = -3; z <= -2; z++) {
                    p.set(x, yy, z, stairs(roof, face, false));
                }
            }
            p.set(c, ridge - 1, -3, "minecraft:iron_chain[axis=y]");
            p.set(c, ridge - 2, -3, "minecraft:iron_chain[axis=y]");
            p.set(c, ridge - 3, -3, "minecraft:lightning_rod[facing=down]");
        }
        // windows down both sides, a man door near the front
        for (int z = 3; z < d - 2; z += 4) {
            b.window(0, 3, z, "west", 1, null);
            b.window(w - 1, 3, z, "east", 1, null);
        }
        int side = chance() < 0.5 ? 0 : w - 1;
        b.door(side, 1, 2, "minecraft:spruce_door", side == 0 ? "west" : "east");
        p.set(side, 3, 2, TRIM);
        // cupolas on the ridge
        int cups = d >= 28 ? 2 : 1;
        for (int k = 1; k <= cups; k++) {
            cupola(c - 1, d * k / (cups + 1) - 1, ridge, 2, roof);
        }
        interior(w, d, c, top, loft, use);
        return ridge;
    }

    /** Stalls or stanchions either side of the drive under a hay loft, the feed room by the doors. */
    private void interior(int w, int d, int c, int top, int loft, Use use) {
        int lx0 = 1, lx1 = c - 3, hx0 = c + 2, hx1 = w - 2;
        int z0 = 3, z1 = d - 4;
        if (use != Use.HAY) {
            int pitch = use == Use.HORSES ? 4 : 3;
            for (int z = z0; z <= z1; z += pitch) {
                for (int[] bay : new int[][] {{lx0, lx1, lx1}, {hx0, hx1, hx0}}) {
                    for (int x = bay[0]; x <= bay[1]; x++) {
                        p.set(x, 1, z, use == Use.HORSES ? "minecraft:spruce_planks" : "minecraft:spruce_fence");
                        if (use == Use.HORSES) {
                            p.set(x, 2, z, "minecraft:iron_bars");
                        }
                    }
                    // the stall's front onto the drive, its gate
                    for (int zz = z + 1; zz < Math.min(z + pitch, z1 + 1); zz++) {
                        boolean gate = zz == z + 1;
                        String facing = bay[2] == lx1 ? "east" : "west";
                        p.set(bay[2], 1, zz, gate ? "minecraft:spruce_fence_gate[facing=" + facing + ",open=" + (chance() < 0.3 + hit * 0.4) + "]"
                                : use == Use.HORSES ? "minecraft:spruce_planks" : "minecraft:spruce_fence");
                        if (use == Use.HORSES && !gate) {
                            p.set(bay[2], 2, zz, "minecraft:iron_bars");
                        }
                        // straw down, the manger at the wall, a water bucket
                        for (int x = bay[0]; x <= bay[1]; x++) {
                            if (x != bay[2] && chance() < 0.7) {
                                p.setIfAir(x, 1, zz, chance() < 0.08 ? "blood_splat" : "straw_floor");
                            }
                        }
                    }
                    int wallX = bay[2] == lx1 ? lx0 : hx1;
                    p.set(wallX, 1, z + 1, chance() < 0.6 ? "minecraft:hay_block" : "minecraft:composter[level=" + rng.nextInt(8) + "]");
                    if (chance() < 0.4) {
                        p.set(wallX, 1, z + 2, "minecraft:cauldron");
                    }
                }
            }
        } else {
            // a hay barn: bales stacked floor to loft either side
            for (int[] bay : new int[][] {{lx0, lx1}, {hx0, hx1}}) {
                for (int x = bay[0]; x <= bay[1]; x++) {
                    for (int z = z0; z <= z1; z++) {
                        int hgt = 1 + rng.nextInt(3);
                        for (int y = 1; y <= hgt && y < loft; y++) {
                            p.set(x, y, z, "minecraft:hay_block[axis=" + (chance() < 0.5 ? "x" : "z") + "]");
                        }
                    }
                }
            }
        }
        // the loft floor over both bays, open over the drive, a ladder up from the feed room
        for (int[] bay : new int[][] {{lx0, lx1}, {hx0, hx1}}) {
            for (int x = bay[0]; x <= bay[1]; x++) {
                for (int z = 1; z <= d - 2; z++) {
                    p.set(x, loft, z, chance() < 0.04 + hit * 0.06 ? "air" : "minecraft:spruce_planks");
                }
            }
        }
        for (int z = 1; z <= d - 2; z += 2) {
            p.set(lx1, loft + 1, z, "minecraft:spruce_fence");
            p.set(hx0, loft + 1, z, "minecraft:spruce_fence");
        }
        for (int y = 1; y <= loft; y++) {
            p.set(lx1, y, 1, "ladder[facing=south]");
        }
        // hay in the loft, in heaps up under the roof
        for (int[] bay : new int[][] {{lx0, lx1}, {hx0, hx1}}) {
            for (int x = bay[0]; x <= bay[1]; x++) {
                for (int z = 3; z <= d - 3; z++) {
                    double hh = 2.5 * Math.sin(z * 0.35 + x) + 1.2 + chance();
                    for (int y = 1; y <= hh; y++) {
                        p.set(x, loft + y, z, chance() < 0.85 ? "minecraft:hay_block[axis=" + (chance() < 0.5 ? "x" : "z") + "]" : "straw_floor");
                    }
                }
            }
        }
        // the feed room by the doors: feed, tools, the grain barrels; a lantern hanging over the drive
        p.set(lx0, 1, 1, "minecraft:barrel[facing=up,loot=farm]");
        p.set(lx0 + 1, 1, 1, "crate");
        p.set(hx1, 1, 1, "minecraft:barrel[facing=up,loot=farm]");
        p.set(hx1, 2, 1, "store_pegboard[facing=north,loot=hardware]");
        p.set(hx1 - 1, 1, 1, chance() < 0.5 ? "minecraft:grindstone[face=floor,facing=east]" : "minecraft:chipped_anvil[facing=east]");
        p.set(hx1, 1, d - 2, "minecraft:barrel[facing=up,loot=farm]");
        for (int z = d / 3; z < d - 2; z += d / 3) {
            p.set(c, top + 3, z, "minecraft:iron_chain[axis=y]");
            p.set(c, top + 2, z, chance() < 0.5 ? "minecraft:lantern[hanging=true]" : "minecraft:iron_chain[axis=y]");
        }
        // what happened here
        double r = chance();
        if (r < 0.35) {
            // someone held out in the loft: the ladder pulled up, a bed in the hay, cans and a lantern
            for (int y = 1; y <= loft; y++) {
                p.set(lx1, y, 1, "air");
            }
            p.set(hx0 + 1, loft + 1, 2, "minecraft:brown_bed[facing=east,part=foot]");
            p.set(hx0 + 2, loft + 1, 2, "minecraft:brown_bed[facing=east,part=head]");
            p.set(hx0 + 1, loft + 1, 3, "crate");
            p.set(hx0 + 2, loft + 1, 3, "minecraft:barrel[facing=up,loot=grocery]");
            p.set(hx0, loft + 1, 4, "minecraft:lantern");
            for (int i = 0; i < 6; i++) {
                p.setIfAir(hx0 + rng.nextInt(Math.max(1, hx1 - hx0)), loft + 1, 1 + rng.nextInt(4), pick("paper", "glass_shards", "blood_splat"));
            }
        } else if (r < 0.6) {
            // bales stacked across the doors, pushed in
            for (int x = c - 2; x <= c + 1; x++) {
                for (int y = 1; y <= 1 + rng.nextInt(2); y++) {
                    p.set(x, y, 2 + rng.nextInt(2), "minecraft:hay_block[axis=x]");
                }
            }
        }
        for (int i = 0; i < d / 3; i++) {
            p.setIfAir(c - 2 + rng.nextInt(4), 1, 1 + rng.nextInt(d - 2), pick("straw_floor", "straw_floor", "blood_splat", "paper", "minecraft:hay_block"));
        }
    }

    /**
     * A lean-to along a barn's side wall at x = wallX (going out toward +x when dir is 1): posts
     * on the outside, a tin roof falling from the eave at y, open at the front; firewood and junk.
     */
    void leanTo(int wallX, int dir, int z0, int z1, int lw, int y, String tin) {
        int outer = wallX + dir * lw;
        for (int z = z0; z <= z1; z++) {
            for (int i = 1; i <= lw; i++) {
                p.set(wallX + dir * i, 0, z, pick("minecraft:gravel", "minecraft:coarse_dirt", "minecraft:packed_mud"));
            }
            if ((z - z0) % 4 == 0 || z == z1) {
                for (int yy = 1; yy < y - lw / 2; yy++) {
                    p.set(outer, yy, z, "minecraft:stripped_spruce_log[axis=y]");
                }
            }
        }
        leanRoof(wallX + dir, outer + dir, z0 - 1, z1 + 1, y, tin);
        for (int z = z0 + 1; z <= z1; z += 2) {
            double r = chance();
            if (r < 0.3) {
                for (int yy = 1; yy <= 2; yy++) {
                    p.set(wallX + dir, yy, z, "minecraft:oak_log[axis=x]");
                    p.set(wallX + dir, yy, z + 1, "minecraft:birch_log[axis=x]");
                }
            } else if (r < 0.45) {
                p.set(wallX + dir * 2, 1, z, "tire[axis=z]");
            } else if (r < 0.55) {
                p.set(wallX + dir, 1, z, "minecraft:barrel[facing=up,loot=hardware]");
            }
        }
    }

    // ------------------------------------------------------------------ the other barns

    /**
     * A pole barn for the machinery: steel-clad back and sides, open bays to the front between
     * posts, a low tin gable; a tractor, the hay wagon, the fuel drums and the workbench inside.
     */
    void machineShed(int w, int d, String clad, String tin) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                p.set(x, 0, z, chance() < 0.7 ? "minecraft:gravel" : b.wall("floor_concrete", 1));
            }
        }
        for (int y = 1; y <= 4; y++) {
            for (int x = 0; x < w; x++) {
                p.set(x, y, d - 1, b.wall(clad, y));
            }
            for (int z = 0; z < d; z++) {
                p.set(0, y, z, b.wall(clad, y));
                p.set(w - 1, y, z, b.wall(clad, y));
            }
            for (int x = 0; x < w; x += 5) {
                p.set(x, y, 0, "steel_girder");
            }
            p.set(w - 1, y, 0, "steel_girder");
        }
        for (int x = 0; x < w; x++) {
            p.set(x, 5, 0, clad);
        }
        b.gable(0, w - 1, 0, d - 1, 5, tin, false, clad, 1);
        // what's parked in the bays
        int bay = 1;
        while (bay + 4 < w) {
            double r = chance();
            if (r < 0.4) {
                tractor(bay, 2, pick("red", "green", "green", "yellow"));
            } else if (r < 0.6) {
                hayWagon(bay, 1, 2 + rng.nextInt(3));
            } else if (r < 0.75) {
                p.vehicle(bay + 2, 1, d / 2.0, 180, "pickup", rng.nextLong());
            }
            bay += 5;
        }
        for (int z = 1; z < d - 1; z += 3) {
            p.set(w - 2, 1, z, pick("minecraft:barrel[facing=up,loot=hardware]", "tire[axis=x]", "minecraft:barrel[facing=up]", "crate"));
        }
        p.set(1, 1, d - 2, "store_lumber[facing=south,loot=hardware]");
        p.set(2, 2, d - 2, "store_pegboard[facing=south,loot=hardware]");
        p.set(3, 1, d - 2, "minecraft:smithing_table");
        p.set(4, 1, d - 2, "propane_tank[axis=x]");
        b.mess(1, 1, w - 2, d - 2, 1, 0.03);
    }

    /**
     * The dairy's free-stall barn, d long: a low gable roof on posts, concrete curtain walls with the
     * sides open above them, the feed alley down the middle with headlocks along it, rows of stalls
     * out to the walls, fans at the ends.
     */
    void dairyBarn(int w, int d, String tin) {
        int c = w / 2;
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                p.set(x, 0, z, b.wall("floor_concrete", 1));
            }
        }
        for (int z = 0; z < d; z++) {
            p.set(0, 1, z, "concrete_dark");
            p.set(w - 1, 1, z, "concrete_dark");
            if (z % 4 == 0 || z == d - 1) {
                for (int y = 2; y <= 4; y++) {
                    p.set(0, y, z, "minecraft:stripped_spruce_log[axis=y]");
                    p.set(w - 1, y, z, "minecraft:stripped_spruce_log[axis=y]");
                }
            } else if (chance() < 0.6) {
                // the curtain, rolled up or torn
                p.set(0, 4, z, "tarp_green");
                p.set(w - 1, 4, z, "tarp_green");
            }
        }
        for (int x = 0; x < w; x++) {
            p.set(x, 5, 0, "siding_white");
            p.set(x, 5, d - 1, "siding_white");
        }
        b.gable(0, w - 1, 0, d - 1, 5, tin, true, "siding_white", 1);
        // the feed alley, open at both ends for the mixer wagon, headlocks along it
        for (int z = 0; z < d; z++) {
            for (int x = c - 2; x <= c + 1; x++) {
                p.set(x, 0, z, b.wall("floor_concrete", 1));
                if (chance() < 0.25) {
                    p.set(x, 1, z, chance() < 0.6 ? "straw_floor" : "minecraft:hay_block");
                }
            }
            if (z > 0 && z < d - 1) {
                p.set(c - 3, 1, z, "minecraft:iron_bars");
                p.set(c + 2, 1, z, "minecraft:iron_bars");
                p.set(c - 3, 2, z, "minecraft:iron_bars");
                p.set(c + 2, 2, z, "minecraft:iron_bars");
            }
        }
        // stall loops out to the walls, with a back alley behind them
        for (int z = 2; z < d - 2; z += 2) {
            for (int x : new int[] {1, 2, w - 3, w - 2}) {
                p.set(x, 1, z, "minecraft:iron_bars");
            }
            for (int x : new int[] {1, w - 2}) {
                if (chance() < 0.6) {
                    p.setIfAir(x, 1, z + 1, "straw_floor");
                }
            }
        }
        for (int x : new int[] {3, w - 4}) {
            for (int z = 3; z < d - 3; z += 9) {
                p.set(x, 1, z, "minecraft:water_cauldron[level=" + (1 + rng.nextInt(3)) + "]");
            }
        }
        for (int x : new int[] {2, w - 3}) {
            p.set(x, 4, 1, "hvac");
            p.set(x, 4, d - 2, "hvac");
        }
        for (int i = 0; i < d / 4; i++) {
            p.setIfAir(1 + rng.nextInt(w - 2), 1, 1 + rng.nextInt(d - 2), pick("blood_splat", "minecraft:bone_block[axis=x]", "straw_floor", "paper"));
        }
    }

    /** The milk house: a white block room, the steel bulk tank, the sink and the hoses. */
    void milkHouse(int w, int d) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                p.set(x, 0, z, "floor_lino");
            }
        }
        b.walls(0, 0, w - 1, d - 1, 1, 3, "concrete_panel");
        b.flat(0, 0, w - 1, d - 1, 4, "concrete_panel", 1, "stone_trim");
        b.door(w / 2, 1, 0, "minecraft:iron_door", "north");
        b.window(1, 2, 0, "north", 1, null);
        b.window(w - 1, 2, d / 2, "east", 1, null);
        for (int z = 1; z < d - 1; z++) {
            for (int x = 1; x <= Math.min(w - 2, 3); x++) {
                p.set(x, 1, z, "tank_white");
                p.set(x, 2, z, z == 1 || z == d - 2 ? "air" : "tank_white");
            }
        }
        p.set(w - 2, 1, 1, "minecraft:water_cauldron[level=1]");
        p.set(w - 2, 1, d - 2, "minecraft:barrel[facing=up,loot=kitchen]");
        b.lights(0, 0, w - 1, d - 1, 3, 0.1);
    }

    /** A horse barn's tack and wash: done by barn(..., Use.HORSES); this adds the paddock gate sign and saddle racks. */
    void tackWall(int x, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            p.set(x, 2, z, z % 2 == 0 ? "minecraft:spruce_fence" : "minecraft:brown_wall_banner[facing=east]");
            p.set(x, 1, z, z % 3 == 0 ? "minecraft:barrel[facing=up,loot=farm]" : "minecraft:spruce_trapdoor[facing=east,half=top,open=false]");
        }
    }

    // ------------------------------------------------------------------ silos and bins

    enum Silo { STAVE, BRICK, BLUE }

    /**
     * A tower silo centred on (cx, cz), radius r, h high: concrete staves, old brick, or blue
     * glass-lined steel; a dome (or a tin cone on the brick one), the ladder up the north side in
     * its cage, the unloader's shed at the foot.
     */
    void silo(int cx, int cz, int r, int h, Silo kind) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                if (disc(dx, dz, r + 0.5)) {
                    p.set(cx + dx, 0, cz + dz, "concrete_dark");
                }
            }
        }
        // the drum: concrete staves with a steel hoop every other block, brick, or glass-lined steel,
        // smooth round (Round); then the dome (the brick one's tin cone) and its cap
        double ox = cx + 0.5, oz = cz + 0.5, R = r + 0.3;
        switch (kind) {
            case STAVE -> Round.wall(p, ox, oz, R, 1, h, "stave", 2);
            case BRICK -> Round.wall(p, ox, oz, R, 1, h, "brick", 0);
            case BLUE -> Round.wall(p, ox, oz, R, 1, h, "blue", 0);
        }
        int top = kind == Silo.BRICK
                ? Round.roof(p, ox, oz, R + 0.4, Round.cone(R + 0.4), h + 1, "tinred")
                : Round.roof(p, ox, oz, R + 0.1, Round.dome(R), h + 1, kind == Silo.BLUE ? "blue" : "galv");
        p.set(cx, top, cz, "rd_finial");
        // the ladder up the north side, caged from head height, onto the dome
        int lz = cz - r - 1;
        for (int y = 1; y <= h + 1; y++) {
            p.set(cx, y, lz, y >= 4 && y <= h ? "rd_cage[facing=north]" : "ladder[facing=north]");
        }
        // the unloader shed at the foot
        for (int y = 1; y <= 3; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -1; dz >= -3; dz--) {
                    boolean edge = dx == -2 || dx == 2 || dz == -3;
                    if (edge && !(dx == 0 && dz == -3 && y <= 2)) {
                        p.set(cx + dx + r + 3, y, cz + dz + 2, b.wall("corrugated_grey", y));
                    }
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -4; dz <= 0; dz++) {
                p.set(cx + dx + r + 3, 4, cz + dz + 2, dz == -4 ? "ext_porch_roof_tin_red_edge[facing=north]" : "ext_porch_roof_tin_red");
            }
        }
        if (chance() < 0.4) {
            // a pile of old silage spilling out
            for (int i = 0; i < 5; i++) {
                p.setIfAir(cx + r + 1 + rng.nextInt(4), 1, cz - 3 + rng.nextInt(6), pick("minecraft:moss_carpet", "straw_floor", "straw_floor"));
            }
        }
    }

    /**
     * A corrugated grain bin centred on (cx, cz): ribbed walls, a shallow cone roof with its cap,
     * the ladder, the aeration fan and the door at the foot. Returns the top of the roof.
     */
    int grainBin(int cx, int cz, int r, int h) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                if (disc(dx, dz, r + 1.5)) {
                    p.set(cx + dx, 0, cz + dz, "concrete_dark");
                }
            }
        }
        double ox = cx + 0.5, oz = cz + 0.5, R = r + 0.3;
        Round.wall(p, ox, oz, R, 1, h, "bin", 0);
        // what's left of last year's corn, a slope of it inside
        for (int dx = -r + 1; dx <= r - 1; dx++) {
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                if (disc(dx, dz, r - 0.5)) {
                    int fill = (int) (h * 0.4 - Math.hypot(dx, dz) * 0.4);
                    for (int y = 1; y <= fill; y++) {
                        p.set(cx + dx, y, cz + dz, "straw_floor");
                    }
                }
            }
        }
        // the shallow cone, its eave a little out over the wall, the cap and vent at the peak
        int capY = Round.roof(p, ox, oz, R + 0.4, Round.cone(R + 0.4), h + 1, "galv");
        p.set(cx, capY, cz, "rd_bin_cap");
        for (int yy = 1; yy <= h; yy++) {
            p.set(cx, yy, cz - r - 1, "ladder[facing=north]");
        }
        p.set(cx + r + 1, 1, cz, "rd_fan[facing=east]");
        p.set(cx, 1, cz + r, chance() < 0.5 ? "air" : "flat_door[facing=north,half=lower,hinge=left,open=false]");
        if (p.get(cx, 1, cz + r) == null) {
            p.set(cx, 2, cz + r, "air");
        }
        return capY;
    }

    /** The bucket elevator ("leg") at (x, z) that fills the bins: a lattice tower, its head, spouts down to each bin top. */
    void grainLeg(int x, int z, int height, int[][] binTops) {
        for (int y = 1; y <= height; y++) {
            p.set(x, y, z, "truss_grey");
            p.set(x, y, z + 1, y % 3 == 0 ? "corrugated_grey" : "truss_grey");
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                p.set(x + dx, height + 1, z + dz, "corrugated_rust");
                p.set(x + dx, height + 2, z + dz, TIN[2] + "_slab[type=bottom]");
                p.set(x + dx, 0, z + dz, "grate");
            }
        }
        for (int[] t : binTops) {
            line(x, height, z, t[0], t[1] + 1, t[2], "minecraft:andesite_wall");
        }
        // the truck dump at its foot and a catwalk out to the first bin
        for (int y = 1; y <= height; y++) {
            p.set(x - 1, y, z + 1, "ladder[facing=west]");
        }
    }

    /** A stepped line of blocks from one point to another (spouts, augers, fallen poles). */
    void line(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        int n = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : (double) i / n;
            p.set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), (int) Math.round(z0 + (z1 - z0) * t), block);
        }
    }

    /** A round wire corn crib centred on (cx, cz): mesh walls with last year's ears showing through, a tin cone. */
    void cornCrib(int cx, int cz) {
        int r = 2, h = 5;
        for (int y = 1; y <= h; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (ring(dx, dz, r + 0.5)) {
                        String axis = Math.abs(dz) >= Math.abs(dx) ? "x" : "z";
                        p.set(cx + dx, y, cz + dz, "chainlink[axis=" + axis + "]");
                    } else if (disc(dx, dz, r - 0.5) && y <= 3) {
                        p.set(cx + dx, y, cz + dz, "minecraft:honeycomb_block");
                    }
                }
            }
        }
        String tin = pick(TIN);
        int y = h + 1;
        for (double rr = r + 1.5; rr > 0.4; rr -= 1, y++) {
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (ring(dx, dz, rr)) {
                        tile(cx + dx, y, cz + dz, stairs(tin, inward(dx, dz), false));
                    }
                }
            }
        }
        p.set(cx, y, cz, tin);
    }

    // ------------------------------------------------------------------ the windmill

    /**
     * A farm windmill over (cx, cz), h to its platform: a steel lattice tower on four legs tapering
     * in, the wheel facing north into the wind with its tail vane behind, the pump rod down the
     * middle to a galvanized stock tank on the east side.
     */
    void windmill(int cx, int cz, int h, boolean tank) {
        for (int y = 1; y <= h; y++) {
            int off = y < h * 0.55 ? 2 : 1;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    p.set(cx + sx * off, y, cz + sz * off, "fence_post");
                }
            }
            if (y % 4 == 0) {
                for (int i = -off + 1; i < off; i++) {
                    p.set(cx + i, y, cz - off, "minecraft:iron_bars");
                    p.set(cx + i, y, cz + off, "minecraft:iron_bars");
                    p.set(cx - off, y, cz + i, "minecraft:iron_bars");
                    p.set(cx + off, y, cz + i, "minecraft:iron_bars");
                }
            }
        }
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                p.set(cx + sx * 2, 0, cz + sz * 2, "concrete_dark");
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.set(cx + dx, h, cz + dz, "grate");
            }
        }
        // the head: gearbox, the wheel in front of it, the tail vane behind
        int hc = h + 3;
        p.set(cx, h + 1, cz, "fence_post");
        p.set(cx, h + 2, cz, "fence_post");
        p.set(cx, hc, cz, "rust");
        boolean wrecked = chance() < 0.15 + hit * 0.2;
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                if (wrecked && (i + j) % 3 == 0) {
                    continue;
                }
                p.set(cx + 2 - i, hc + 2 - j, cz - 1, "windmill_" + i + j + "[facing=north]");
            }
        }
        for (int z = cz + 1; z <= cz + 5; z++) {
            p.set(cx, hc, z, "windmill_tail[facing=north]");
            if (z >= cz + 4) {
                p.set(cx, hc + 1, z, "windmill_tail[facing=north]");
                p.set(cx, hc - 1, z, "windmill_tail[facing=north]");
            }
        }
        // the pump rod down the middle, the pump, the pipe to the tank
        for (int y = 2; y < h; y++) {
            p.set(cx, y, cz, "minecraft:iron_chain[axis=y]");
        }
        p.set(cx, 1, cz, "minecraft:lightning_rod[facing=up]");
        p.set(cx, 0, cz, "concrete_dark");
        if (tank) {
            int tx = cx + 5;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (disc(dx, dz, 2.5)) {
                        p.set(tx + dx, 0, cz + dz, "minecraft:stone");
                        p.set(tx + dx, 1, cz + dz, ring(dx, dz, 2.5) ? GALV : "minecraft:water");
                    } else if (disc(dx, dz, 3.5)) {
                        p.set(tx + dx, 0, cz + dz, chance() < 0.6 ? "minecraft:mud" : "minecraft:coarse_dirt");
                    }
                }
            }
            p.set(cx + 1, 2, cz, "minecraft:lightning_rod[facing=east]");
            p.set(cx + 2, 2, cz, "minecraft:lightning_rod[facing=east]");
            p.set(cx + 3, 2, cz, "minecraft:lightning_rod[facing=east]");
        }
    }

    // ------------------------------------------------------------------ machinery

    /** Paint for a tractor: body block and the carpet its fenders are cut from. */
    private static String[] paint(String colour) {
        return switch (colour) {
            case "green" -> new String[] {"minecraft:green_concrete", "minecraft:green_carpet", "minecraft:yellow_concrete"};
            case "yellow" -> new String[] {"steel_yellow", "minecraft:yellow_carpet", "minecraft:black_concrete"};
            case "orange" -> new String[] {"minecraft:orange_terracotta", "minecraft:orange_carpet", "minecraft:light_gray_concrete"};
            default -> new String[] {"steel_red", "minecraft:red_carpet", "minecraft:light_gray_concrete"};
        };
    }

    /** A tractor 3 wide and 5 long from (x, z), nose toward -z: big rear tyres, hood, exhaust stack, seat. */
    void tractor(int x, int z, String colour) {
        String[] c = paint(colour);
        String body = c[0];
        p.set(x, 1, z, "tire[axis=x]");
        p.set(x + 2, 1, z, "tire[axis=x]");
        for (int dz = 3; dz <= 4; dz++) {
            for (int y = 1; y <= 2; y++) {
                p.set(x, y, z + dz, "tire[axis=x]");
                p.set(x + 2, y, z + dz, "tire[axis=x]");
            }
            p.set(x, 3, z + dz, c[1]);
            p.set(x + 2, 3, z + dz, c[1]);
        }
        for (int dz = 0; dz <= 4; dz++) {
            p.set(x + 1, 1, z + dz, chance() < 0.12 ? "rust" : dz == 0 ? c[2] : body);
        }
        for (int dz = 0; dz <= 2; dz++) {
            p.set(x + 1, 2, z + dz, chance() < 0.1 ? "rust" : body);
        }
        p.set(x + 1, 2, z - 1, "minecraft:iron_trapdoor[facing=south,half=bottom,open=true]");
        p.set(x + 1, 3, z + 1, "minecraft:iron_chain[axis=y]");
        p.set(x + 1, 4, z + 1, "minecraft:iron_chain[axis=y]");
        p.set(x + 1, 2, z + 3, body);
        p.set(x + 1, 3, z + 3, "minecraft:lever[face=floor,facing=south,powered=false]");
        p.set(x + 1, 2, z + 4, "minecraft:polished_blackstone_stairs[facing=south,half=bottom]");
    }

    /** A flat hay wagon 3 wide and 6 long from (x, z), loaded with bales `load` high in places. */
    void hayWagon(int x, int z, int load) {
        for (int dz = 0; dz < 6; dz++) {
            for (int dx = 0; dx < 3; dx++) {
                boolean wheel = (dz == 1 || dz == 4) && dx != 1;
                p.set(x + dx, 1, z + dz, wheel ? "tire[axis=x]" : "minecraft:spruce_slab[type=top]");
                for (int y = 2; y < 2 + load; y++) {
                    if (chance() < 0.75 - (y - 2) * 0.15) {
                        p.set(x + dx, y, z + dz, "minecraft:hay_block[axis=z]");
                    }
                }
            }
        }
        p.set(x + 1, 1, z - 1, "minecraft:iron_chain[axis=z]");
    }

    // ------------------------------------------------------------------ the small buildings

    /** A chicken coop w x d: board walls, a tin lean roof, nest boxes and roosts, the wire run in front. */
    void coop(int w, int d, String wall) {
        String tin = pick(TIN);
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                p.set(x, 0, z, "minecraft:spruce_planks");
            }
        }
        b.walls(0, 0, w - 1, d - 1, 1, 2, wall);
        for (int x = 0; x < w; x++) {
            p.set(x, 3, d - 1, b.wall(wall, 3));
        }
        for (int z = 0; z < d; z++) {
            p.set(0, 3, z, z >= d / 2 ? b.wall(wall, 3) : "air");
            p.set(w - 1, 3, z, z >= d / 2 ? b.wall(wall, 3) : "air");
        }
        // the roof falls toward the front
        for (int x = -1; x <= w; x++) {
            for (int z = -1; z <= d; z++) {
                String s = z >= d / 2 ? tin + "_slab[type=top]" : tin + "_slab[type=bottom]";
                tile(x, z >= d / 2 ? 3 : 3, z, s);
            }
        }
        b.door(1, 1, 0, "minecraft:spruce_door", "north");
        p.set(w - 2, 2, 0, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
        for (int x = 1; x < w - 1; x++) {
            p.set(x, 1, d - 2, x % 2 == 0 ? "minecraft:hay_block" : "minecraft:barrel[facing=north,loot=farm]");
            if (chance() < 0.5) {
                p.setIfAir(x, 1, 1 + rng.nextInt(Math.max(1, d - 3)), pick("straw_floor", "minecraft:white_carpet", "blood_splat"));
            }
        }
        p.set(w - 2, 2, 1, "minecraft:spruce_fence");
        // the run: chicken wire on posts, the gate hanging open
        int rz = -6;
        for (int x = -1; x <= w; x++) {
            for (int z = rz; z <= -1; z++) {
                p.set(x, 0, z, chance() < 0.7 ? "minecraft:coarse_dirt" : "minecraft:packed_mud");
                if (chance() < 0.08) {
                    p.set(x, 1, z, pick("minecraft:white_carpet", "blood_splat", "straw_floor"));
                }
            }
        }
        Kit.chainlink(p, rng, -1, rz, w, rz, 1);
        Kit.chainlink(p, rng, -1, rz + 1, -1, -1, 1);
        Kit.chainlink(p, rng, w, rz + 1, w, -1, 1);
        p.set(w / 2, 1, rz, "minecraft:spruce_fence_gate[facing=north,open=true]");
        p.set(1, 1, rz + 2, "minecraft:cauldron");
    }

    /** A dug well: a stone curb, a little shingled roof on two posts, the windlass and its chain. */
    void well(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean mid = dx == 0 && dz == 0;
                p.set(x + dx, 0, z + dz, mid ? "minecraft:water" : "minecraft:cobblestone");
                p.set(x + dx, 1, z + dz, mid ? "air" : chance() < 0.3 ? "minecraft:mossy_cobblestone_wall" : "minecraft:cobblestone_wall");
            }
        }
        for (int y = -3; y < 0; y++) {
            p.set(x, y, z, "minecraft:water");
        }
        for (int y = 2; y <= 3; y++) {
            p.set(x - 1, y, z, "minecraft:spruce_fence");
            p.set(x + 1, y, z, "minecraft:spruce_fence");
        }
        p.set(x, 3, z, "minecraft:stripped_spruce_log[axis=x]");
        p.set(x, 2, z, "minecraft:iron_chain[axis=y]");
        for (int dz = -1; dz <= 1; dz++) {
            p.set(x - 1, 4, z + dz, "minecraft:spruce_stairs[facing=east,half=bottom]");
            p.set(x, 4, z + dz, "minecraft:spruce_slab[type=bottom]");
            p.set(x + 1, 4, z + dz, "minecraft:spruce_stairs[facing=west,half=bottom]");
        }
    }

    /** An outhouse: two by two of boards, a door with the moon cut in it, a slab roof. */
    void outhouse(int x, int z) {
        String wall = pick("minecraft:spruce_planks", "barn_grey", "minecraft:oak_planks");
        for (int dx = 0; dx < 2; dx++) {
            for (int dz = 0; dz < 2; dz++) {
                p.set(x + dx, 0, z + dz, "minecraft:spruce_planks");
                for (int y = 1; y <= 2; y++) {
                    p.set(x + dx, y, z + dz, dz == 0 && dx == 0 ? "air" : wall);
                }
                p.set(x + dx, 3, z + dz, "minecraft:spruce_slab[type=bottom]");
            }
        }
        p.set(x + 1, 1, z + 1, "minecraft:spruce_trapdoor[facing=north,half=top,open=false]");
        b.door(x, 1, z, "minecraft:spruce_door", "north");
    }

    /** Split firewood stacked between posts, a chopping block with the axe still in it. */
    void woodpile(int x, int z, int len) {
        for (int i = 0; i < len; i++) {
            int hgt = 1 + rng.nextInt(3);
            for (int y = 1; y <= hgt; y++) {
                p.set(x + i, y, z, pick("minecraft:oak_log[axis=z]", "minecraft:birch_log[axis=z]", "minecraft:spruce_log[axis=z]"));
            }
        }
        p.set(x - 1, 1, z, "minecraft:spruce_fence");
        p.set(x + len, 1, z, "minecraft:spruce_fence");
        p.set(x + len + 1, 1, z + 2, "minecraft:stripped_oak_log[axis=y]");
        p.set(x + len, 1, z + 2, "minecraft:oak_slab[type=bottom]");
    }

    /** A row of beehives on cinder blocks, a couple knocked over. */
    void hives(int x, int z, int n) {
        for (int i = 0; i < n; i++) {
            p.set(x + i * 2, 1, z, chance() < 0.2 ? "minecraft:beehive[facing=" + DIRS[rng.nextInt(4)] + ",honey_level=0]"
                    : "minecraft:beehive[facing=north,honey_level=" + rng.nextInt(6) + "]");
            p.set(x + i * 2, 0, z, "minecraft:cobblestone");
        }
    }

    /** A scarecrow in a field: a post, a bale for a body, a pumpkin head, its arms out. */
    void scarecrow(int x, int z, String facing) {
        p.set(x, 1, z, "minecraft:spruce_fence");
        p.set(x, 2, z, "minecraft:hay_block");
        p.set(x, 3, z, "minecraft:carved_pumpkin[facing=" + facing + "]");
        boolean alongX = facing.equals("north") || facing.equals("south");
        p.set(x + (alongX ? 1 : 0), 2, z + (alongX ? 0 : 1), "minecraft:spruce_fence");
        p.set(x - (alongX ? 1 : 0), 2, z - (alongX ? 0 : 1), "minecraft:spruce_fence");
    }

    /** The farm's diesel tank up on its stand, the hose hanging, a stain under it. */
    void fuelTank(int x, int z) {
        for (int dz = 0; dz < 3; dz++) {
            p.set(x, 1, z + dz, dz == 1 ? "air" : "steel_girder");
            p.set(x, 2, z + dz, "propane_tank[axis=z]");
            p.set(x, 0, z + dz, "minecraft:coarse_dirt");
        }
        p.set(x, 0, z + 1, "scorch");
        p.set(x + 1, 1, z + 1, "minecraft:iron_chain[axis=y]");
        p.set(x + 1, 2, z + 1, "minecraft:lightning_rod[facing=east]");
    }

    /** Graves dug in a row in the yard: mounds, crosses of two sticks, one hole still open. */
    void graves(int x, int z, int n) {
        for (int i = 0; i < n; i++) {
            int gx = x + i * 2;
            boolean open = i == n - 1 && chance() < 0.5;
            for (int dz = 0; dz < 3; dz++) {
                p.set(gx, 0, z + dz, open ? "air" : "minecraft:coarse_dirt");
                if (open) {
                    p.set(gx, -1, z + dz, "minecraft:dirt");
                } else if (dz > 0) {
                    p.set(gx, 1, z + dz, "minecraft:rooted_dirt");
                }
            }
            if (open) {
                p.set(gx + 1, 1, z + 1, "minecraft:coarse_dirt");
                p.set(gx + 1, 1, z + 2, "minecraft:dirt");
            }
            p.set(gx, 1, z, "minecraft:spruce_fence");
            p.set(gx, 2, z, "minecraft:spruce_trapdoor[facing=south,half=top,open=false]");
        }
    }

    /** Where they burnt the dead: a black scar, charred logs, bones. */
    void pyre(int x, int z, int r) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double dd = Math.hypot(dx, dz) + chance() * 0.8;
                if (dd > r + 1) {
                    continue;
                }
                p.set(x + dx, 0, z + dz, dd < r ? "burnt_ground" : "minecraft:coarse_dirt");
                if (dd < r - 0.5 && chance() < 0.5) {
                    p.set(x + dx, 1, z + dz, pick("minecraft:bone_block[axis=x]", "minecraft:bone_block[axis=z]",
                            "minecraft:stripped_dark_oak_log[axis=x]", "minecraft:blackstone_slab[type=bottom]", "scorch"));
                }
            }
        }
        p.set(x, 1, z, "minecraft:campfire[lit=false,facing=north]");
    }

    /** A roadside produce stand facing the road (-z): a tin roof on posts, a counter, crates, the sign. */
    void produceStand(int x, int z, String sign) {
        String tin = pick(TIN);
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                p.set(x + dx, 0, z + dz, "minecraft:spruce_planks");
                p.set(x + dx, 4, z + dz, tin + "_slab[type=bottom]");
            }
            p.set(x + dx, 1, z, dx == 2 ? "store_register[facing=north,loot=till]" : "store_produce[facing=north,loot=grocery]");
            p.set(x + dx, 1, z + 2, dx % 2 == 0 ? "minecraft:pumpkin" : "crate");
        }
        for (int y = 1; y <= 3; y++) {
            p.set(x, y, z + 2, "minecraft:spruce_fence");
            p.set(x + 4, y, z + 2, "minecraft:spruce_fence");
        }
        p.set(x - 1, 1, z - 1, "minecraft:carved_pumpkin[facing=north]");
        p.set(x + 5, 1, z - 1, "minecraft:jack_o_lantern[facing=north]");
        p.set(x + 5, 1, z, "minecraft:hay_block");
        Boards.board(p, rng, sign, x, 2, z - 4);
    }
}
