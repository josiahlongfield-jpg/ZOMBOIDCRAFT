package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The things you see a town by from miles off: the water tower on its legs, the concrete grain
 * elevator by the tracks, the mill and its smokestack. And the war memorial in the square.
 * Drawn through a {@link Placer} like the buildings, centred where noted.
 */
final class Landmarks {
    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;

    Landmarks(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.6;
    }

    private static boolean ring(int dx, int dz, double r) {
        double dd = Math.sqrt(dx * dx + dz * dz);
        return dd <= r && dd > r - 1.2;
    }

    private static boolean disc(int dx, int dz, double r) {
        return dx * dx + dz * dz <= r * r;
    }

    // ------------------------------------------------------------------ the water tower

    /**
     * A water tower centred on (cx, cz): a steel tank on four lattice legs with a catwalk round
     * it (steel), or an old wooden tank on timber legs. Its footprint is 11 blocks across.
     */
    void waterTower(int cx, int cz, boolean steel) {
        int legTop = steel ? 22 + rng.nextInt(5) : 14 + rng.nextInt(3);
        int r = steel ? 5 : 3;
        int spread = steel ? 4 : 3;
        String leg = steel ? "truss_grey" : "minecraft:spruce_log";
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                for (int y = 1; y <= legTop; y++) {
                    // the legs batter in toward the top
                    int in = steel && y > legTop / 2 ? 1 : 0;
                    p.set(cx + sx * (spread - in), y, cz + sz * (spread - in), leg);
                }
                p.set(cx + sx * spread, 0, cz + sz * spread, "concrete_dark");
            }
        }
        // braces between the legs at two heights, and the riser up the middle
        for (int level : new int[] {legTop / 3, legTop * 2 / 3}) {
            int s = steel && level > legTop / 2 ? spread - 1 : spread;
            for (int i = -s + 1; i < s; i++) {
                p.set(cx + i, level, cz - s, steel ? "streetlight_arm[facing=east]" : "minecraft:spruce_fence");
                p.set(cx + i, level, cz + s, steel ? "streetlight_arm[facing=east]" : "minecraft:spruce_fence");
                p.set(cx - s, level, cz + i, steel ? "streetlight_arm[facing=north]" : "minecraft:spruce_fence");
                p.set(cx + s, level, cz + i, steel ? "streetlight_arm[facing=north]" : "minecraft:spruce_fence");
            }
        }
        for (int y = 1; y <= legTop; y++) {
            p.set(cx, y, cz, steel ? "steel_girder" : "minecraft:iron_chain[axis=y]");
        }
        // a ladder up the north-west leg
        for (int y = 1; y <= legTop; y++) {
            int in = steel && y > legTop / 2 ? 1 : 0;
            p.set(cx - spread + in, y, cz - spread + in - 1, "ladder[facing=south]");
        }
        int t0 = legTop + 1;
        int tankH = steel ? 7 : 6;
        String skin = steel ? (rng.nextBoolean() ? "tank_white" : "tank_blue") : "water_tank";
        // the bowl underneath
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (steel && disc(dx, dz, r - 2)) {
                    p.set(cx + dx, t0 - 1, cz + dz, skin);
                }
                if (disc(dx, dz, r + 0.5)) {
                    p.set(cx + dx, t0, cz + dz, skin);
                }
            }
        }
        for (int y = t0 + 1; y <= t0 + tankH; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (ring(dx, dz, r + 0.5)) {
                        p.set(cx + dx, y, cz + dz, b.wall(skin, y));
                    }
                }
            }
        }
        // the roof cone and its finial
        int y = t0 + tankH + 1;
        for (double rr = r + 0.5; rr > 0; rr -= 1.5) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (disc(dx, dz, rr)) {
                        p.set(cx + dx, y, cz + dz, steel ? skin : "water_tank_top");
                    }
                }
            }
            y++;
        }
        p.set(cx, y, cz, "minecraft:lightning_rod");
        if (steel) {
            // the catwalk round the tank's waist with a rail, a red light on top for aircraft
            for (int dx = -r - 1; dx <= r + 1; dx++) {
                for (int dz = -r - 1; dz <= r + 1; dz++) {
                    if (ring(dx, dz, r + 1.5)) {
                        p.set(cx + dx, t0, cz + dz, "grate");
                        p.set(cx + dx, t0 + 1, cz + dz, rng.nextDouble() < 0.08 ? "air" : "minecraft:iron_bars");
                    }
                }
            }
            p.set(cx, y + 1, cz, "aviation_light");
            // the town's name in the paint, and someone's later message
            int face = -r - 1;
            p.set(cx - 1, t0 + 4, cz + face, "graffiti_" + rng.nextInt(8) + "[facing=south]");
        }
    }

    // ------------------------------------------------------------------ the grain elevator

    /**
     * A concrete grain elevator from (x0, z0): two rows of silos, the headhouse straddling them,
     * a steel bin and the truck dump. About 26 by 18 blocks; the front (z0) faces the road or tracks.
     */
    void grainElevator(int x0, int z0) {
        int cols = 3, rad = 3, pitch = 7, rows = 2;
        int height = 26 + rng.nextInt(6);
        for (int i = 0; i < cols; i++) {
            for (int j = 0; j < rows; j++) {
                int cx = x0 + rad + i * pitch, cz = z0 + rad + j * pitch;
                for (int yy = 1; yy <= height; yy++) {
                    for (int dx = -rad; dx <= rad; dx++) {
                        for (int dz = -rad; dz <= rad; dz++) {
                            if (ring(dx, dz, rad + 0.5)) {
                                p.set(cx + dx, yy, cz + dz, b.wall("concrete_panel", yy));
                            }
                        }
                    }
                }
                for (int dx = -rad; dx <= rad; dx++) {
                    for (int dz = -rad; dz <= rad; dz++) {
                        if (disc(dx, dz, rad + 0.5)) {
                            p.set(cx + dx, 0, cz + dz, "concrete_dark");
                            p.set(cx + dx, height + 1, cz + dz, "minecraft:smooth_stone_slab[type=bottom]");
                        }
                    }
                }
            }
        }
        // the headhouse: corrugated steel on top of the bins, taller over the leg
        int hx0 = x0 + 2, hx1 = x0 + cols * pitch - 3, hz0 = z0 + 2, hz1 = z0 + rows * pitch - 3;
        int hy = height + 1, hh = 6;
        String clad = rng.nextBoolean() ? "corrugated_grey" : "corrugated_rust";
        b.walls(hx0, hz0, hx1, hz1, hy, hy + hh, clad);
        b.floor(hx0, hz0, hx1, hz1, hy, "floor_concrete");
        b.gable(hx0, hx1, hz0, hz1, hy + hh + 1, "minecraft:waxed_weathered_cut_copper", false, clad, 0);
        int lx = x0 + rad + pitch / 2;
        b.walls(lx - 1, hz0 - 1, lx + 2, hz0 + 2, hy + hh, hy + hh + 6, clad);
        b.gable(lx - 1, lx + 2, hz0 - 1, hz0 + 2, hy + hh + 7, "minecraft:waxed_weathered_cut_copper", true, clad, 0);
        for (int x = hx0 + 2; x < hx1 - 1; x += 3) {
            b.window(x, hy + 3, hz0, "north", 1, null);
        }
        Boards.fascia(p, rng, "grain", hx0 + 1, hy + 2, hz0 - 1, hx1 - hx0 - 1);
        // the spout down to the loading track, the stair up the side, the truck dump
        for (int k = 0; k < 8; k++) {
            p.set(x0 + cols * pitch + k / 2, hy - 2 - k, z0 + 3, "rust");
        }
        for (int yy = 1; yy <= height; yy++) {
            p.set(x0 - 1, yy, z0 + rad, "ladder[facing=west]");
        }
        int dx0 = x0 + cols * pitch, dz0 = z0 + rows * pitch - 6;
        b.walls(dx0, dz0, dx0 + 5, dz0 + 6, 1, 5, clad);
        b.clear(dx0, 1, dz0 + 1, dx0, 4, dz0 + 5);
        b.clear(dx0 + 5, 1, dz0 + 1, dx0 + 5, 4, dz0 + 5);
        b.floor(dx0 + 1, dz0, dx0 + 4, dz0 + 6, 0, "grate");
        b.gable(dx0, dx0 + 5, dz0, dz0 + 6, 6, "minecraft:waxed_weathered_cut_copper", true, clad, 0);
        // a steel bin alongside
        int bx = x0 + cols * pitch + 10, bz = z0 + rad + 1, br = 4, bh = 12;
        for (int yy = 1; yy <= bh; yy++) {
            for (int ddx = -br; ddx <= br; ddx++) {
                for (int ddz = -br; ddz <= br; ddz++) {
                    if (ring(ddx, ddz, br + 0.5)) {
                        p.set(bx + ddx, yy, bz + ddz, yy % 3 == 0 ? "corrugated_grey_b" : "corrugated_grey");
                    }
                }
            }
        }
        for (double rr = br + 0.5, yy = bh + 1; rr > 0; rr -= 1.6, yy++) {
            for (int ddx = -br; ddx <= br; ddx++) {
                for (int ddz = -br; ddz <= br; ddz++) {
                    if (disc(ddx, ddz, rr)) {
                        p.set(bx + ddx, (int) yy, bz + ddz, "minecraft:waxed_weathered_cut_copper_slab[type=bottom]");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the mill

    /** A round brick smokestack at (cx, cz), tapering, soot-black at the lip. */
    void smokestack(int cx, int cz, int height) {
        for (int y = 0; y <= height; y++) {
            double r = 2.6 - 1.1 * y / height;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (ring(dx, dz, r)) {
                        String brick = y > height - 3 ? "brick_dark_soot" : y < 3 ? "concrete_dark" : b.wall("brick_red", y);
                        p.set(cx + dx, y, cz + dz, brick);
                    }
                }
            }
        }
        for (int y = 2; y < height; y++) {
            p.set(cx - 3, y, cz, "ladder[facing=west]");
        }
        p.set(cx, height + 1, cz, "minecraft:campfire[lit=false]");
    }

    /**
     * A textile mill on a lot w by d: a long brick block of three or four storeys pierced with
     * rows of big windows, a stair tower in the middle of the front with its pyramid roof and
     * the mill's name, a one-storey weave shed with a sawtooth roof behind, the boiler house and
     * its smokestack, loading docks. Returns the stair tower's west and east x, so a fence
     * along the front can stop either side of it.
     */
    int[] mill(int w, int d) {
        int floors = 3 + rng.nextInt(2), storey = 5;
        String wall = rng.nextBoolean() ? "brick_red" : "brick_dark";
        int bd = 16;
        int bx1 = w - 1;
        b.damage = 0.75;
        b.boarded = 0.15;
        b.shell(0, 0, bx1, bd - 1, floors, storey, wall, "floor_concrete", "floor_boards");
        int top = floors * storey;
        // bays of big multi-pane windows between brick piers
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 2;
            for (int x = 2; x < bx1 - 1; x++) {
                if ((x - 2) % 4 == 3) {
                    continue;
                }
                b.window(x, y, 0, "north", 3, null);
                b.window(x, y, bd - 1, "south", 3, null);
                p.set(x, y + 3, 0, b.wall("stone_trim", y + 3));
            }
            for (int z = 3; z < bd - 3; z += 4) {
                b.window(0, y, z, "west", 3, null);
                b.window(bx1, y, z, "east", 3, null);
            }
        }
        // the corbelled brick band at the top
        for (int x = -1; x <= bx1 + 1; x++) {
            p.set(x, top, -1, "minecraft:brick_stairs[facing=south,half=top]");
            p.set(x, top + 1, -1, b.wall(wall, top + 1));
        }
        b.flat(0, 0, bx1, bd - 1, top, wall, 2, "stone_trim");
        // the stair tower, rising two floors past the roof, with the name and a pyramid roof; a
        // two-wide switchback inside it climbs to every floor and out onto the roof
        int tw = Stairwell.length(storey) + 2, tx = bx1 / 2 - tw / 2, tz = -3;
        int tTop = top + 9;
        b.walls(tx, tz, tx + tw - 1, tz + 5, 1, tTop, wall);
        b.clear(tx + 1, 1, tz + 1, tx + tw - 2, tTop - 1, tz + 4);
        for (int y = 3; y < tTop - 2; y += storey) {
            b.window(tx + tw / 2, y, tz, "north", 3, null);
        }
        Boards.fascia(p, rng, "mill", tx - 1, tTop - 2, tz - 1, tw + 2);
        b.hip(tx, tx + tw - 1, tz, tz + 5, tTop + 1, "minecraft:deepslate_tile", 1);
        Stairwell stair = new Stairwell(p, rng, tx + 1, tz + 1, 0, storey);
        stair.wall = wall;
        stair.landing = "floor_boards";
        String[] ways = new String[floors];
        java.util.Arrays.fill(ways, "south");
        stair.build(floors, ways, true, "south");
        b.doors(tx + 1, 1, tz, "ext_door_oak", "north");
        // the floors: looms in long rows, bobbins and bales strewn about
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 1;
            for (int z = 3; z < bd - 3; z += 3) {
                for (int x = 3; x < bx1 - 2; x++) {
                    if ((x - 3) % 6 < 4 && rng.nextDouble() < 0.85) {
                        p.set(x, y, z, x % 2 == 0 ? "store_washer" : "minecraft:smooth_stone_slab[type=top]");
                    }
                }
            }
            b.lights(0, 0, bx1, bd - 1, y + storey - 2, 0.01);
            b.mess(1, 1, bx1 - 1, bd - 2, y, 0.05);
            for (int i = 0; i < bx1 / 6; i++) {
                p.setIfAir(2 + rng.nextInt(bx1 - 3), y, 2 + rng.nextInt(bd - 4),
                        rng.nextBoolean() ? "minecraft:white_wool" : "crate");
            }
        }
        // the weave shed behind: sawtooth bays, glass facing north
        int sz0 = bd + 1, sz1 = Math.min(d - 12, sz0 + 26);
        int sx1 = Math.min(bx1, 50);
        if (sz1 - sz0 >= 10) {
            int sh = 6;
            b.walls(4, sz0, sx1, sz1, 1, sh, wall);
            b.floor(5, sz0 + 1, sx1 - 1, sz1 - 1, 0, "floor_concrete");
            b.clear(5, 1, sz0 + 1, sx1 - 1, sh, sz1 - 1);
            for (int z = sz0; z + 4 <= sz1; z += 5) {
                for (int x = 4; x <= sx1; x++) {
                    p.set(x, sh + 1, z, b.glass("x"));
                    p.set(x, sh + 2, z, b.glass("x"));
                    p.set(x, sh + 3, z, "corrugated_grey");
                    for (int k = 1; k <= 4; k++) {
                        int yy = sh + 3 - (k * 3) / 4;
                        p.set(x, Math.max(sh + 1, yy), z + k, "corrugated_grey");
                    }
                }
            }
            for (int x = 6; x < sx1 - 1; x += 3) {
                for (int z = sz0 + 2; z < sz1 - 1; z += 3) {
                    if (rng.nextDouble() < 0.7) {
                        p.set(x, 1, z, "store_washer");
                    }
                }
            }
            b.clear(4, 1, bd - 1 + 2, 6, 3, sz0);
            for (int y = 1; y <= 3; y++) {
                p.set(5, y, bd - 1, "air");
                p.set(6, y, bd - 1, "air");
            }
        }
        // the boiler house and its stack, a coal heap
        int hx = bx1 - 14, hz = bd + 2;
        b.walls(hx, hz, hx + 10, hz + 9, 1, 8, wall);
        b.clear(hx + 1, 1, hz + 1, hx + 9, 8, hz + 8);
        b.floor(hx + 1, hz + 1, hx + 9, hz + 8, 0, "floor_concrete");
        b.gable(hx, hx + 10, hz, hz + 9, 9, "minecraft:waxed_exposed_cut_copper", false, wall, 0);
        p.set(hx + 3, 1, hz + 4, "propane_tank[axis=x]");
        p.set(hx + 6, 1, hz + 4, "propane_tank[axis=x]");
        smokestack(hx + 5, hz + 13, 34 + rng.nextInt(10));
        for (int i = 0; i < 12; i++) {
            p.setIfAir(hx - 3 + rng.nextInt(5), 1, hz + 2 + rng.nextInt(6), "minecraft:coal_block");
        }
        // loading docks down the east end, roll-up doors, a box truck backed up to one
        for (int z = 2; z < bd - 2; z += 5) {
            for (int zz = z; zz < z + 3 && zz < bd - 1; zz++) {
                for (int y = 1; y <= 3; y++) {
                    p.set(bx1, y, zz, rng.nextDouble() < 0.3 ? "air" : "garage_door[facing=east]");
                }
            }
            p.set(bx1 + 1, 0, z + 1, "concrete_dark");
        }
        p.vehicle(bx1 + 6, 1, 4.5, 90, "box_truck,moving_truck,step_van", rng.nextLong());
        return new int[] {tx, tx + tw - 1};
    }

    // ------------------------------------------------------------------ the memorial

    /** The war memorial at (cx, cz): an obelisk on stepped stone, wreaths long dead, two old cannon. */
    void memorial(int cx, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.set(cx + dx, 0, cz + dz, "minecraft:stone_bricks");
                p.set(cx + dx, 1, cz + dz, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? "minecraft:stone_brick_slab[type=bottom]"
                        : "minecraft:stone_bricks");
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                p.set(cx + dx, 2, cz + dz, "minecraft:polished_andesite");
            }
        }
        for (int y = 3; y <= 9; y++) {
            p.set(cx, y, cz, y == 3 ? "minecraft:chiseled_stone_bricks" : "minecraft:polished_andesite");
        }
        p.set(cx, 10, cz, "minecraft:polished_andesite_slab[type=bottom]");
        p.set(cx, 2, cz - 2, "minecraft:dead_bush");
        p.set(cx - 1, 3, cz, "minecraft:polished_andesite_stairs[facing=east]");
        p.set(cx + 1, 3, cz, "minecraft:polished_andesite_stairs[facing=west]");
        p.set(cx, 3, cz - 1, "minecraft:polished_andesite_stairs[facing=south]");
        p.set(cx, 3, cz + 1, "minecraft:polished_andesite_stairs[facing=north]");
        for (int s = -1; s <= 1; s += 2) {
            int x = cx + s * 4;
            p.set(x, 1, cz + 1, "minecraft:polished_blackstone_wall");
            p.set(x, 1, cz, "minecraft:anvil[facing=north]");
            p.set(x, 1, cz - 1, "minecraft:polished_blackstone_wall");
        }
    }
}
