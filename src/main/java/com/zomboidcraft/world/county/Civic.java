package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The public buildings of a county town, each drawn in its lot's frame (front along z = 0
 * facing the street): churches (a white clapboard chapel, a brick church with a corner tower, a
 * stone one with a spire), the courthouse with its portico and clock tower, a town hall, the
 * post office, a Carnegie library, the bank, the fire station, the sheriff's office and jail,
 * schools, and the picture house.
 *
 * Every one is a little different each time (materials, sizes, which way the tower stands) and
 * every one shows what happened here: the church barricaded as a last refuge, the sheriff's
 * sandbagged, the school's buses abandoned in the loop.
 */
final class Civic {
    private static final String[] GLASS = {"minecraft:red_stained_glass_pane", "minecraft:blue_stained_glass_pane",
            "minecraft:yellow_stained_glass_pane", "minecraft:purple_stained_glass_pane", "minecraft:green_stained_glass_pane"};
    private static final String[] ROOFS = {"minecraft:deepslate_tile", "minecraft:cobbled_deepslate", "minecraft:blackstone",
            "minecraft:dark_oak", "minecraft:tuff_brick"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;

    Civic(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.5 + rng.nextDouble() * 0.4;
        b.boarded = rng.nextDouble() < 0.3 ? 0.45 : 0.1;
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ shared bits

    /** Steps up to a door: a flight of `rise` stairs out from the front, w wide, centred on cx. */
    private void steps(int cx, int half, int rise, String family) {
        for (int i = 0; i < rise; i++) {
            for (int x = cx - half - i; x <= cx + half + i; x++) {
                p.set(x, rise - 1 - i, -1 - i, family + "_stairs[facing=south]");
                for (int y = 0; y < rise - 1 - i; y++) {
                    p.set(x, y, -1 - i, Bldg.full(family));
                }
            }
        }
    }

    /** Pews in two blocks either side of an aisle, facing the far end (+z). */
    private void pews(int x0, int x1, int z0, int z1, int y, int aisle) {
        for (int z = z0; z <= z1; z += 2) {
            for (int x = x0; x <= x1; x++) {
                if (Math.abs(x - aisle) <= 1) {
                    continue;
                }
                if (rng.nextDouble() < 0.12) {
                    p.set(x, y, z, rng.nextBoolean() ? "minecraft:spruce_slab[type=bottom]" : "air");  // smashed, or dragged off
                    continue;
                }
                p.set(x, y, z, "minecraft:spruce_stairs[facing=north]");
            }
        }
    }

    /** A flag pole: chain up to a banner of the faded colours. */
    void flagpole(int x, int z, int h) {
        p.set(x, 0, z, "concrete_dark");
        for (int y = 1; y < h; y++) {
            p.set(x, y, z, "minecraft:iron_chain[axis=y]");
        }
        p.set(x, h, z, "minecraft:lightning_rod");
        if (rng.nextDouble() < 0.7) {
            p.set(x + 1, h - 1, z, pick("minecraft:white_wall_banner[facing=east]", "minecraft:blue_wall_banner[facing=east]",
                    "minecraft:red_wall_banner[facing=east]"));
        }
    }

    /** A last stand: sandbags across the door, a barricade, the casings and blood. */
    private void lastStand(int x0, int x1, int z, int y) {
        for (int x = x0; x <= x1; x++) {
            if (rng.nextDouble() < 0.8) {
                p.set(x, y, z, "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(x0 + rng.nextInt(Math.max(1, x1 - x0 + 1)), y, z - 1 - rng.nextInt(3), "blood_splat");
        }
    }

    // ------------------------------------------------------------------ churches

    enum Church { CHAPEL, BRICK, STONE }

    /** Lot size a church of the kind wants: {w, d}. */
    static int[] churchSize(Church kind) {
        return switch (kind) {
            case CHAPEL -> new int[] {13, 30};
            case BRICK -> new int[] {19, 36};
            case STONE -> new int[] {23, 44};
        };
    }

    /**
     * A church on a lot w wide and d deep, set back behind its own lawn with the tower at the
     * front. sign: the board out front (church, methodist, stmary) or null.
     */
    void church(Church kind, int w, int d, String sign) {
        int nw, nd, tower, setback;
        String wall, roof, trimWall;
        switch (kind) {
            case CHAPEL -> {
                nw = 9 + (rng.nextBoolean() ? 2 : 0);
                nd = Math.min(d - 9, 16 + rng.nextInt(5));
                tower = 3;
                setback = 5;
                wall = "siding_white";
                trimWall = "siding_white";
                roof = pick("minecraft:deepslate_tile", "minecraft:dark_oak", "minecraft:waxed_weathered_cut_copper");
            }
            case BRICK -> {
                nw = 13 + (rng.nextBoolean() ? 2 : 0);
                nd = Math.min(d - 10, 22 + rng.nextInt(5));
                tower = 5;
                setback = 6;
                wall = pick("brick_red", "brick_red", "brick_dark", "brick_buff");
                trimWall = "stone_trim";
                roof = pick(ROOFS);
            }
            default -> {
                nw = 15 + (rng.nextBoolean() ? 2 : 0);
                nd = Math.min(d - 12, 28 + rng.nextInt(5));
                tower = 5;
                setback = 8;
                wall = pick("minecraft:stone_bricks", "minecraft:tuff_bricks", "minecraft:polished_andesite");
                trimWall = "stone_trim";
                roof = pick("minecraft:deepslate_tile", "minecraft:blackstone", "minecraft:cobbled_deepslate");
            }
        }
        int x0 = (w - nw) / 2, x1 = x0 + nw - 1, z0 = setback, z1 = z0 + nd - 1;
        int wallH = kind == Church.CHAPEL ? 6 : kind == Church.BRICK ? 8 : 10;
        b.walls(x0, z0, x1, z1, 1, wallH, wall);
        b.floor(x0, z0, x1, z1, 0, "concrete_dark");
        b.floor(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, kind == Church.STONE ? "lobby_floor_dark" : "floor_boards");
        b.clear(x0 + 1, 1, z0 + 1, x1 - 1, wallH, z1 - 1);
        // tall windows down both sides, stained glass in the brick and stone, clear in the chapel
        String g = GLASS[rng.nextInt(GLASS.length)];
        for (int z = z0 + 3; z <= z1 - 3; z += 3) {
            for (int side = 0; side < 2; side++) {
                int x = side == 0 ? x0 : x1;
                int top = wallH - 2;
                for (int y = 2; y <= top; y++) {
                    String pane = kind == Church.CHAPEL ? b.glass("z") : rng.nextDouble() < b.damage * 0.35 ? "air"
                            : rng.nextDouble() < 0.3 ? GLASS[rng.nextInt(GLASS.length)] : g;
                    p.set(x, y, z, pane);
                }
                if (kind != Church.CHAPEL) {
                    // a pointed head over the lancet, and a buttress between the bays
                    int o = side == 0 ? -1 : 1;
                    p.set(x, top + 1, z, b.wall(trimWall, top + 1));
                    if (z + 1 < z1) {
                        for (int y = 1; y <= wallH - 2; y++) {
                            p.set(x + o, y, z + 2, b.wall(wall, y));
                        }
                        p.set(x + o, wallH - 1, z + 2, (wall.startsWith("minecraft:") ? stairsFamily(wall) : "minecraft:stone_brick")
                                + "_stairs[facing=" + (side == 0 ? "east" : "west") + "]");
                    }
                }
            }
        }
        b.gable(x0, x1, z0, z1, wallH + 1, roof, true, kind == Church.CHAPEL ? wall : trimWall.equals("stone_trim") ? wall : wall, 1);
        // the far end: an apse window over the altar
        int cx = (x0 + x1) / 2;
        for (int y = 3; y <= wallH; y++) {
            p.set(cx, y, z1, kind == Church.CHAPEL ? b.glass("x") : g);
        }
        // the tower over the door
        int tx0 = kind == Church.BRICK ? (rng.nextBoolean() ? x0 - 1 : x1 - tower + 2) : cx - tower / 2;
        int tz0 = z0 - (kind == Church.CHAPEL ? 2 : 3);
        int towerTop = wallH + (kind == Church.CHAPEL ? 6 : kind == Church.BRICK ? 9 : 12);
        String towerWall = wall;
        b.walls(tx0, tz0, tx0 + tower - 1, tz0 + tower - 1, 1, towerTop, towerWall);
        b.floor(tx0 + 1, tz0 + 1, tx0 + tower - 2, tz0 + tower - 2, 0, "floor_boards");
        b.clear(tx0 + 1, 1, tz0 + 1, tx0 + tower - 2, towerTop - 1, tz0 + tower - 2);
        // the belfry: open louvres on all four sides, the bell hanging inside
        int bel = towerTop - 3;
        for (int i = 1; i < tower - 1; i++) {
            for (int y = bel; y <= bel + 1; y++) {
                p.set(tx0 + i, y, tz0, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
                p.set(tx0 + i, y, tz0 + tower - 1, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]");
                p.set(tx0, y, tz0 + i, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]");
                p.set(tx0 + tower - 1, y, tz0 + i, "minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]");
            }
        }
        p.set(tx0 + tower / 2, bel + 2, tz0 + tower / 2, "minecraft:bell[attachment=ceiling,facing=north]");
        for (int x = tx0; x < tx0 + tower; x++) {
            for (int z = tz0; z < tz0 + tower; z++) {
                p.set(x, towerTop, z, b.wall(towerWall, towerTop));
            }
        }
        // corner pinnacles on a stone tower, then the spire
        if (kind == Church.STONE) {
            for (int[] c : new int[][] {{tx0, tz0}, {tx0 + tower - 1, tz0}, {tx0, tz0 + tower - 1}, {tx0 + tower - 1, tz0 + tower - 1}}) {
                p.set(c[0], towerTop + 1, c[1], "minecraft:stone_brick_wall");
                p.set(c[0], towerTop + 2, c[1], "minecraft:stone_brick_wall");
            }
        }
        String spireRoof = kind == Church.CHAPEL ? roof : pick("minecraft:deepslate_tile", "minecraft:waxed_oxidized_cut_copper",
                "minecraft:blackstone");
        b.spire(tx0, tz0, tower, towerTop + 1, spireRoof, "minecraft:lightning_rod");
        // a clock on the brick and stone towers, stopped
        if (kind != Church.CHAPEL) {
            int cy = bel - 3;
            p.set(tx0 + tower / 2, cy, tz0 - 1, "clock_face[facing=south]");
        }
        // the door at the foot of the tower (or in the gable beside it), steps up to it
        boolean towerDoor = kind != Church.BRICK;
        int dx = towerDoor ? tx0 + tower / 2 : cx;
        int dz = towerDoor ? tz0 : z0;
        if (towerDoor) {
            b.clear(tx0 + 1, 1, tz0 + tower - 1, tx0 + tower - 2, 3, tz0 + tower - 1);  // into the nave
            b.clear(cx - 1, 1, z0, cx + 1, 3, z0);
        }
        b.door(dx, 1, dz, "minecraft:dark_oak_door", "north");
        if (kind != Church.CHAPEL) {
            b.door(dx - 1, 1, dz, "minecraft:dark_oak_door", "north");
        }
        p.set(dx, 3, dz, kind == Church.CHAPEL ? "minecraft:spruce_planks" : g);
        // a rose window in the gable of a brick church
        if (kind == Church.BRICK) {
            int ry = wallH + 3;
            p.set(cx, ry, z0, g);
            p.set(cx - 1, ry, z0, GLASS[rng.nextInt(GLASS.length)]);
            p.set(cx + 1, ry, z0, GLASS[rng.nextInt(GLASS.length)]);
            p.set(cx, ry + 1, z0, GLASS[rng.nextInt(GLASS.length)]);
            p.set(cx, ry - 1, z0, GLASS[rng.nextInt(GLASS.length)]);
            for (int y = 4; y <= wallH - 1; y++) {
                p.set(cx, y, z0, rng.nextDouble() < 0.5 ? g : GLASS[rng.nextInt(GLASS.length)]);
            }
        }
        // inside: pews, the altar on its step, a lectern, candles burnt down
        pews(x0 + 1, x1 - 1, z0 + 3, z1 - 6, 1, cx);
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            for (int z = z1 - 4; z <= z1 - 1; z++) {
                p.set(x, 1, z, "minecraft:spruce_slab[type=bottom]");
            }
        }
        p.set(cx, 1, z1 - 2, "minecraft:spruce_planks");
        p.set(cx - 1, 1, z1 - 2, "minecraft:spruce_planks");
        p.set(cx + 1, 1, z1 - 2, "minecraft:spruce_planks");
        p.set(cx, 2, z1 - 2, "minecraft:candle[candles=3,lit=false]");
        p.set(cx - 2, 2, z1 - 4, "minecraft:lectern[facing=north]");
        for (int y = 4; y <= 6; y++) {
            p.set(cx, y, z1 - 1, y == 5 ? "minecraft:dark_oak_fence" : "minecraft:dark_oak_fence");
        }
        p.set(cx - 1, 5, z1 - 1, "minecraft:dark_oak_fence");
        p.set(cx + 1, 5, z1 - 1, "minecraft:dark_oak_fence");
        // the refuge: cots between the pews, sandbags at the door, crates of tins
        if (rng.nextDouble() < 0.5) {
            lastStand(cx - 2, cx + 2, z0 + 1, 1);
            for (int i = 0; i < 4; i++) {
                int x = x0 + 1 + rng.nextInt(nw - 2), z = z0 + 4 + rng.nextInt(Math.max(1, nd - 10));
                p.set(x, 1, z, rng.nextBoolean() ? "minecraft:white_bed[facing=north,part=foot]" : "crate");
            }
        }
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.04);
        b.lights(x0, z0, x1, z1, wallH, 0.0);
        steps(dx, kind == Church.CHAPEL ? 1 : 2, 1, "minecraft:stone_brick");
        // the board out front by the walk
        if (sign != null) {
            Boards.board(p, rng, sign, 1, 2, 1);
        }
        // the path up from the street
        for (int z = 0; z < dz - 1; z++) {
            p.set(dx, 0, z, "sidewalk");
            p.set(dx - 1, 0, z, "sidewalk");
        }
    }

    private static String stairsFamily(String block) {
        return switch (block) {
            case "minecraft:stone_bricks" -> "minecraft:stone_brick";
            case "minecraft:tuff_bricks" -> "minecraft:tuff_brick";
            case "minecraft:polished_andesite" -> "minecraft:polished_andesite";
            default -> "minecraft:stone_brick";
        };
    }

    /** A graveyard over x0..x1, z0..z1: rows of stones gone over to moss, an iron fence round it. */
    void graveyard(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge) {
                    p.set(x, 1, z, rng.nextDouble() < 0.1 ? "air" : "minecraft:iron_bars");
                    continue;
                }
                if ((x - x0) % 2 == 0 && (z - z0) % 3 == 1 && rng.nextDouble() < 0.8) {
                    p.set(x, 1, z, pick("minecraft:stone_brick_wall", "minecraft:mossy_stone_brick_wall",
                            "minecraft:cobblestone_wall", "minecraft:mossy_cobblestone_wall", "minecraft:andesite_wall"));
                    if (rng.nextDouble() < 0.1) {
                        p.set(x, 2, z, "minecraft:stone_brick_wall");  // an obelisk
                    }
                } else if ((x - x0) % 2 == 0 && (z - z0) % 3 == 2 && rng.nextDouble() < 0.25) {
                    p.set(x, 0, z, "minecraft:coarse_dirt");  // a grave dug up from below
                } else if (rng.nextDouble() < 0.3) {
                    p.set(x, 1, z, rng.nextBoolean() ? "minecraft:short_grass" : "minecraft:tall_grass[half=lower]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the courthouse

    /** The county courthouse: two or three storeys of stone and brick, a columned portico, a clock tower. */
    void courthouse(int w, int d) {
        int floors = 2 + (rng.nextDouble() < 0.4 ? 1 : 0);
        int storey = 5;
        String wall = pick("brick_red", "brick_buff", "minecraft:stone_bricks", "brick_red");
        String trim = "stone_trim";
        int x0 = 0, x1 = w - 1, z0 = 4, z1 = d - 1;
        int top = floors * storey;
        b.shell(x0, z0, x1, z1, floors, storey, wall, "lobby_floor_white", "office_floor");
        // a raised basement storey course and stone quoins at the corners
        for (int x = x0; x <= x1; x++) {
            p.set(x, 1, z0, b.wall(trim, 1));
            p.set(x, 1, z1, b.wall(trim, 1));
        }
        for (int y = 1; y <= top; y++) {
            for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
                p.set(c[0], y, c[1], y % 2 == 0 ? trim : b.wall(wall, y));
            }
        }
        // tall windows, three to a bay
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 2;
            b.windowRow(x0 + 2, x1 - 2, 3, z0, y, "north", 2, "sill");
            b.windowRow(x0 + 2, x1 - 2, 3, z1, y, "south", 2, "sill");
            b.windowRow(z0 + 2, z1 - 2, 3, x0, y, "west", 2, "sill");
            b.windowRow(z0 + 2, z1 - 2, 3, x1, y, "east", 2, "sill");
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                p.set(x, y + 2, z0, b.wall(trim, y + 2));
            }
        }
        // the portico: columns two storeys tall carrying a pediment
        int cx = w / 2, half = Math.min(6, w / 4);
        int pz = z0 - 4;
        String column = pick("minecraft:quartz_pillar[axis=y]", "minecraft:smooth_quartz", "stone_trim");
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = pz; z < z0; z++) {
                p.set(x, 0, z, "minecraft:smooth_stone");
                p.set(x, storey * 2 + 1, z, b.wall(trim, storey * 2 + 1));
            }
            if ((x - (cx - half)) % 2 == 0) {
                for (int y = 1; y <= storey * 2; y++) {
                    p.set(x, y, pz, column);
                }
            }
        }
        // the pediment over the columns
        for (int i = 0; i <= half; i++) {
            for (int x = cx - half + i; x <= cx + half - i; x++) {
                boolean edge = x == cx - half + i || x == cx + half - i;
                p.set(x, storey * 2 + 2 + i, pz, edge ? "minecraft:stone_brick_stairs[facing=" + (x < cx ? "east" : "west") + "]"
                        : b.wall(trim, storey * 2 + 2 + i));
                if (x == cx && edge) {
                    p.set(x, storey * 2 + 2 + i, pz, "minecraft:stone_brick_slab[type=bottom]");
                }
            }
            for (int z = pz + 1; z < z0; z++) {
                p.set(cx - half + i, storey * 2 + 2 + i, z, "minecraft:stone_brick_stairs[facing=east]");
                p.set(cx + half - i, storey * 2 + 2 + i, z, "minecraft:stone_brick_stairs[facing=west]");
            }
        }
        Boards.fascia(p, rng, "courthouse", cx - half, storey * 2 + 1, pz - 1, half * 2 + 1);
        // the double doors, the steps down to the square
        b.doors(cx - 1, 1, z0, "minecraft:dark_oak_door", "north");
        p.set(cx - 1, 3, z0, b.glass("x"));
        p.set(cx, 3, z0, b.glass("x"));
        steps(cx, half, 1, "minecraft:stone_brick");
        // roof: a parapet with a balustrade, and the clock tower in the middle
        b.flat(x0, z0, x1, z1, top, wall, 1, "minecraft:stone_brick_slab[type=bottom]");
        int t = 7, tx = cx - t / 2, tz = (z0 + z1) / 2 - t / 2;
        int tTop = top + 9;
        b.walls(tx, tz, tx + t - 1, tz + t - 1, top + 1, tTop, trim);
        for (int i = 2; i < t - 2; i++) {
            for (int y = top + 6; y <= top + 7; y++) {
                p.set(tx + i, y, tz, "minecraft:iron_bars");
                p.set(tx + i, y, tz + t - 1, "minecraft:iron_bars");
                p.set(tx, y, tz + i, "minecraft:iron_bars");
                p.set(tx + t - 1, y, tz + i, "minecraft:iron_bars");
            }
        }
        int c = t / 2;
        p.set(tx + c, top + 3, tz - 1, "clock_face[facing=south]");
        p.set(tx + c, top + 3, tz + t, "clock_face[facing=north]");
        p.set(tx - 1, top + 3, tz + c, "clock_face[facing=east]");
        p.set(tx + t, top + 3, tz + c, "clock_face[facing=west]");
        b.hip(tx, tx + t - 1, tz, tz + t - 1, tTop + 1, "minecraft:waxed_oxidized_cut_copper", 0);
        p.set(tx + c, tTop + 1 + c, tz + c, "minecraft:lightning_rod");
        // inside: the hall, a courtroom at the back with the bench and the gallery
        int hall = (z0 + z1) / 2;
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = 1; y < storey; y++) {
                p.set(x, y, hall, x == cx || x == cx - 1 ? "air" : "drywall");
            }
        }
        for (int x = x0 + 2; x < x1 - 1; x++) {
            p.set(x, 1, z1 - 2, x == cx ? "reception_desk[facing=north]" : "minecraft:dark_oak_planks");
        }
        p.set(cx, 2, z1 - 1, "minecraft:dark_oak_stairs[facing=south]");
        pews(x0 + 2, x1 - 2, hall + 2, z1 - 5, 1, cx);
        for (int x = x0 + 2; x < x1 - 1; x += 4) {
            p.set(x, 1, z0 + 2, "desk[facing=south]");
            p.set(x, 1, z0 + 3, "filing_cabinet[facing=north]");
        }
        b.stair(x1 - 1, hall - 1, 1, storey, "north", "minecraft:dark_oak");
        if (floors == 3) {
            b.stair(x0 + 1, hall - 1, storey + 1, storey, "north", "minecraft:dark_oak");
        }
        for (int k = 0; k < floors; k++) {
            b.lights(x0, z0, x1, z1, k * storey + storey - 1, 0.03);
            b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, k * storey + 1, 0.05);
        }
        lastStand(cx - half, cx + half, pz - 1, 1);
        flagpole(cx - half - 3, pz - 1, 12);
    }

    // ------------------------------------------------------------------ the smaller civic buildings

    /** A town hall or post office: brick, a stone-dressed door under a small pediment, a cupola. */
    void hall(int w, int d, String sign, boolean cupola) {
        int floors = cupola ? 2 : 1, storey = 5;
        String wall = pick("brick_red", "brick_buff", "brick_dark", "brick_orange");
        int z0 = 3;
        b.shell(0, z0, w - 1, d - 1, floors, storey, wall, "lobby_floor_white", "office_floor");
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 2;
            b.windowRow(2, w - 3, 3, z0, y, "north", 2, "sill");
            b.windowRow(z0 + 2, d - 3, 3, 0, y, "west", 2, "sill");
            b.windowRow(z0 + 2, d - 3, 3, w - 1, y, "east", 2, "sill");
            b.windowRow(2, w - 3, 3, d - 1, y, "south", 2, null);
        }
        int cx = w / 2;
        for (int y = 1; y <= 4; y++) {
            for (int x = cx - 2; x <= cx + 1; x++) {
                p.set(x, y, z0, x == cx - 2 || x == cx + 1 ? "stone_trim" : y <= 2 ? "air" : "stone_trim");
            }
        }
        b.doors(cx - 1, 1, z0, "minecraft:dark_oak_door", "north");
        p.set(cx - 2, 5, z0 - 1, "minecraft:stone_brick_stairs[facing=east]");
        p.set(cx + 1, 5, z0 - 1, "minecraft:stone_brick_stairs[facing=west]");
        p.set(cx - 1, 5, z0 - 1, "stone_trim");
        p.set(cx, 5, z0 - 1, "stone_trim");
        p.set(cx - 1, 6, z0 - 1, "minecraft:stone_brick_slab[type=bottom]");
        p.set(cx, 6, z0 - 1, "minecraft:stone_brick_slab[type=bottom]");
        if (sign != null) {
            Boards.fascia(p, rng, sign, 1, floors * storey - 1, z0 - 1, w - 2);
        }
        steps(cx, 1, 1, "minecraft:stone_brick");
        String roof = pick(ROOFS);
        b.hip(0, w - 1, z0, d - 1, floors * storey + 1, roof, 1);
        if (cupola) {
            int cz = (z0 + d - 1) / 2;
            int ry = floors * storey + Math.min(w, d - z0) / 2;
            b.walls(cx - 1, cz - 1, cx + 1, cz + 1, ry - 2, ry + 2, "minecraft:pale_oak_planks");
            p.set(cx, ry, cz - 1, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
            p.set(cx, ry, cz + 1, "minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]");
            p.set(cx, ry + 1, cz, "minecraft:bell[attachment=ceiling,facing=north]");
            b.hip(cx - 1, cx + 1, cz - 1, cz + 1, ry + 3, "minecraft:waxed_oxidized_cut_copper", 0);
        }
        for (int x = 2; x < w - 2; x += 4) {
            p.set(x, 1, d - 3, "desk[facing=north]");
            p.set(x, 1, d - 4, "office_chair[facing=south]");
            p.set(x + 1, 1, d - 2, "filing_cabinet[facing=north]");
        }
        p.set(cx, 1, z0 + 4, "reception_desk[facing=north]");
        if (floors > 1) {
            b.stair(w - 2, d - 2, 1, storey, "north", "minecraft:dark_oak");
        }
        for (int k = 0; k < floors; k++) {
            b.lights(0, z0, w - 1, d - 1, k * storey + storey - 1, 0.03);
            b.mess(1, z0 + 1, w - 2, d - 2, k * storey + 1, 0.05);
        }
        flagpole(cx + 4, 0, 10);
    }

    /** A Carnegie library: buff brick on a raised basement, a portico of two columns, shelves of books. */
    void library(int w, int d) {
        int z0 = 4, storey = 6;
        String wall = pick("brick_buff", "brick_red", "minecraft:stone_bricks");
        b.shell(0, z0, w - 1, d - 1, 1, storey, wall, "floor_boards", "floor_boards");
        for (int x = 0; x < w; x++) {
            p.set(x, 1, z0, "stone_trim");
        }
        b.windowRow(2, w - 3, 3, z0, 2, "north", 3, "sill");
        b.windowRow(z0 + 2, d - 3, 3, 0, 2, "west", 3, "sill");
        b.windowRow(z0 + 2, d - 3, 3, w - 1, 2, "east", 3, "sill");
        int cx = w / 2;
        for (int x = cx - 2; x <= cx + 1; x++) {
            for (int z = z0 - 3; z < z0; z++) {
                p.set(x, 1, z, "minecraft:smooth_stone");
                p.set(x, storey + 1, z, "stone_trim");
            }
        }
        for (int x : new int[] {cx - 2, cx + 1}) {
            for (int y = 2; y <= storey; y++) {
                p.set(x, y, z0 - 3, "minecraft:quartz_pillar[axis=y]");
            }
        }
        for (int x = cx - 3; x <= cx + 2; x++) {
            p.set(x, storey + 2, z0 - 3, x == cx - 3 ? "minecraft:stone_brick_stairs[facing=east]"
                    : x == cx + 2 ? "minecraft:stone_brick_stairs[facing=west]" : "stone_trim");
        }
        p.set(cx - 1, storey + 3, z0 - 3, "minecraft:stone_brick_slab[type=bottom]");
        p.set(cx, storey + 3, z0 - 3, "minecraft:stone_brick_slab[type=bottom]");
        b.doors(cx - 1, 2, z0, "minecraft:spruce_door", "north");
        for (int x = cx - 2; x <= cx + 1; x++) {
            p.set(x, 1, z0 - 4, "minecraft:stone_brick_stairs[facing=south]");
        }
        Boards.fascia(p, rng, "library", 1, storey, z0 - 1, w - 2);
        b.hip(0, w - 1, z0, d - 1, storey + 1, pick(ROOFS), 1);
        // the stacks: shelves in rows, the issue desk, books all over the floor
        for (int z = z0 + 4; z < d - 2; z += 3) {
            for (int x = 2; x < w - 2; x++) {
                if (Math.abs(x - cx) <= 1) {
                    continue;
                }
                p.set(x, 2, z, "minecraft:bookshelf");
                if (rng.nextDouble() < 0.8) {
                    p.set(x, 3, z, "minecraft:bookshelf");
                }
            }
        }
        p.set(cx, 2, z0 + 2, "reception_desk[facing=north]");
        for (int i = 0; i < w; i++) {
            p.setIfAir(1 + rng.nextInt(w - 2), 2, z0 + 1 + rng.nextInt(d - z0 - 2), "paper");
        }
        b.lights(0, z0, w - 1, d - 1, storey - 1, 0.02);
    }

    /** The bank on its corner: a stone front with two columns, the vault behind the tellers. */
    void bank(int w, int d) {
        int storey = 7;
        String wall = pick("minecraft:stone_bricks", "stone_trim", "brick_buff");
        b.shell(0, 0, w - 1, d - 1, 1, storey, wall, "lobby_floor_white", "lobby_floor_white");
        int cx = w / 2;
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y < storey; y++) {
                p.set(x, y, 0, x == cx || x == cx - 1 ? (y <= 2 ? "air" : b.glass("x")) : x == 1 || x == w - 2 ? b.wall(wall, y)
                        : y >= 2 && y <= 5 ? b.glass("x") : b.wall(wall, y));
            }
        }
        for (int x : new int[] {cx - 2, cx + 1}) {
            for (int y = 1; y < storey; y++) {
                p.set(x, y, -1, "minecraft:quartz_pillar[axis=y]");
            }
        }
        for (int x = 0; x < w; x++) {
            p.set(x, storey, -1, "stone_trim");
            p.set(x, storey + 1, 0, b.wall(wall, storey + 1));
        }
        b.doors(cx - 1, 1, 0, "fire_door", "north");
        Boards.fascia(p, rng, "bank", 1, storey + 1, -1, w - 2);
        b.flat(0, 0, w - 1, d - 1, storey, wall, 2, "minecraft:stone_brick_slab[type=bottom]");
        int counter = d / 2;
        for (int x = 1; x < w - 1; x++) {
            if (x == w - 2) {
                b.door(x, 1, counter, "fire_door", "north");
                continue;
            }
            p.set(x, 1, counter, "store_counter[facing=north]");
            p.set(x, 2, counter, "store_teller_glass[axis=x]");
            p.set(x, 3, counter, "store_teller_glass[axis=x]");
        }
        // the vault: steel walls, the door hanging open, the safe deposit boxes gutted
        int vx0 = 1, vz0 = d - 5;
        for (int x = vx0; x <= vx0 + 4; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, vz0, x == vx0 + 2 && y <= 2 ? "air" : "steel_girder");
            }
        }
        p.set(vx0 + 1, 1, d - 2, "store_safe[facing=north]");
        p.set(vx0 + 3, 1, d - 2, "store_safe[facing=north]");
        p.set(vx0 + 1, 2, d - 2, "filing_cabinet[facing=north]");
        p.set(w - 3, 1, counter + 3, "desk[facing=north]");
        b.lights(0, 0, w - 1, d - 1, storey - 1, 0.03);
        b.mess(1, 1, w - 2, d - 2, 1, 0.08);
    }

    /** The fire station: a brick front of red bay doors, a hose tower, an engine still in its bay. */
    void fireStation(int w, int d) {
        int bays = Math.max(2, Math.min(3, (w - 4) / 5));
        int storey = 5, floors = 2;
        String wall = pick("brick_red", "brick_red", "brick_dark", "brick_buff");
        b.shell(0, 0, w - 1, d - 1, floors, storey, wall, "floor_concrete", "floor_boards");
        int bayW = 4;
        int x = 2;
        for (int i = 0; i < bays; i++) {
            boolean open = rng.nextDouble() < 0.5;
            for (int bx = x; bx < x + bayW; bx++) {
                for (int y = 1; y <= 4; y++) {
                    p.set(bx, y, 0, open && y < 4 ? "air" : "garage_door_red[facing=north]");
                }
            }
            if (i == 0 || rng.nextDouble() < 0.4) {
                p.vehicle(x + bayW / 2.0, 1, d / 2.0, 0, i == 0 ? "fire_truck" : "fire_truck,ambulance,ambulance_van", rng.nextLong());
            }
            x += bayW + 1;
        }
        b.door(w - 2, 1, 0, "fire_door", "north");
        b.windowRow(2, w - 3, 3, 0, storey + 2, "north", 2, "sill");
        Boards.fascia(p, rng, "firedept", 1, storey, -1, w - 2);
        b.flat(0, 0, w - 1, d - 1, floors * storey, wall, 2, "stone_trim");
        // the hose tower at the back corner, its louvres open
        int tx = w - 4, tz = d - 4, tTop = floors * storey + 8;
        b.walls(tx, tz, tx + 3, tz + 3, 1, tTop, wall);
        for (int y = tTop - 2; y < tTop; y++) {
            p.set(tx + 1, y, tz, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
            p.set(tx + 2, y, tz, "minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]");
        }
        b.hip(tx, tx + 3, tz, tz + 3, tTop + 1, "minecraft:deepslate_tile", 0);
        // the apparatus floor: lockers, hoses, boots; the brass pole down from the dorm
        for (int z = 2; z < d - 2; z += 2) {
            p.set(w - 2, 1, z, "minecraft:barrel[facing=west,loot=hardware]");
        }
        p.set(1, 1, d - 2, "store_shelf_hardware[facing=east]");
        p.set(w / 2, 1, d - 3, "minecraft:iron_chain[axis=y]");
        for (int y = 1; y <= storey; y++) {
            p.set(w / 2, y, d - 3, "minecraft:iron_chain[axis=y]");
        }
        b.stair(1, d - 2, 1, storey, "north", "minecraft:spruce");
        for (int i = 0; i < 4; i++) {
            p.set(2 + i * 2, storey + 1, d - 3, "minecraft:white_bed[facing=south,part=head]");
            p.set(2 + i * 2, storey + 1, d - 4, "minecraft:white_bed[facing=south,part=foot]");
        }
        b.lights(0, 0, w - 1, d - 1, storey - 1, 0.04);
        b.lights(0, 0, w - 1, d - 1, storey * 2 - 1, 0.02);
        // the apron out front
        for (int ax = 0; ax < w; ax++) {
            for (int z = -6; z < 0; z++) {
                p.set(ax, 0, z, rng.nextDouble() < 0.15 ? "sidewalk_cracked" : "floor_concrete");
            }
        }
    }

    /** The sheriff's office and county jail: front desk, cells, a gun rack, cruisers out front. */
    void sheriff(int w, int d) {
        int storey = 5;
        String wall = pick("brick_buff", "brick_red", "concrete_panel");
        int z0 = 8;
        b.shell(0, z0, w - 1, d - 1, 1, storey, wall, "floor_lino", "roof");
        b.windowRow(2, w - 3, 3, z0, 2, "north", 2, "sill");
        b.windowRow(z0 + 2, d - 3, 4, 0, 2, "west", 1, null);
        b.windowRow(z0 + 2, d - 3, 4, w - 1, 2, "east", 1, null);
        int cx = w / 2;
        b.doors(cx - 1, 1, z0, "fire_door", "north");
        Boards.fascia(p, rng, "sheriff", 1, storey - 1, z0 - 1, w - 2);
        b.flat(0, z0, w - 1, d - 1, storey, wall, 1, "stone_trim");
        // front desk, then a corridor of cells at the back
        p.set(cx, 1, z0 + 3, "reception_desk[facing=north]");
        p.set(cx + 1, 1, z0 + 3, "reception_desk[facing=north]");
        p.set(1, 1, z0 + 2, "store_gun_rack[facing=east,loot=guns]");
        p.set(1, 1, z0 + 3, "store_case_guns[facing=east,loot=guns]");
        p.set(w - 2, 1, z0 + 2, "filing_cabinet[facing=west]");
        int cells = d - 5;
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, cells, (x - 1) % 4 == 2 && y <= 2 ? (rng.nextBoolean() ? "air" : "minecraft:iron_door[facing=south,half=" + (y == 1 ? "lower" : "upper") + ",hinge=left,open=false]")
                        : "minecraft:iron_bars");
            }
            if ((x - 1) % 4 == 0) {
                for (int z = cells + 1; z < d - 1; z++) {
                    for (int y = 1; y <= 3; y++) {
                        p.set(x, y, z, "concrete_panel");
                    }
                }
            } else if ((x - 1) % 4 == 1) {
                p.set(x, 1, d - 2, "minecraft:gray_bed[facing=south,part=head]");
                p.set(x, 1, d - 3, "minecraft:gray_bed[facing=south,part=foot]");
            }
        }
        b.lights(0, z0, w - 1, d - 1, storey - 1, 0.05);
        b.mess(1, z0 + 1, w - 2, d - 2, 1, 0.08);
        // the lot: cruisers nose in, one burnt, sandbags and barriers where they held out
        for (int i = 0; i < 3; i++) {
            p.vehicle(4 + i * 5, 1, 3.5, 180, "police_sedan,police_suv", rng.nextLong());
        }
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < z0; z++) {
                p.set(x, 0, z, rng.nextDouble() < 0.3 ? "asphalt_crack" : "asphalt");
            }
        }
        lastStand(cx - 3, cx + 2, z0 - 1, 1);
        flagpole(w - 2, z0 - 2, 9);
    }

    // ------------------------------------------------------------------ schools

    /**
     * A school: a long brick classroom wing (one storey for the elementary, two for the high
     * school) with a gym block, the entrance under a canopy, the bus loop out front.
     */
    void school(int w, int d, boolean high) {
        int floors = high ? 2 : 1, storey = 5;
        String wall = pick("brick_red", "brick_buff", "brick_orange", "brick_red");
        int z0 = 14, wingD = 14;
        int wx1 = w - 1 - (high ? 22 : 18);
        b.shell(0, z0, wx1, z0 + wingD - 1, floors, storey, wall, "floor_lino", "floor_lino");
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 2;
            for (int x = 2; x < wx1 - 1; x++) {
                if ((x - 2) % 6 < 4) {
                    b.window(x, y, z0, "north", 2, null);
                    b.window(x, y, z0 + wingD - 1, "south", 2, null);
                }
            }
            for (int x = 1; x < wx1; x++) {
                p.set(x, y - 1, z0 - 1, k == 0 ? "air" : "minecraft:smooth_stone_slab[type=top]");
            }
        }
        // classrooms off a corridor: desks in rows, the teacher's desk, the chalkboard
        int corridor = z0 + 5;
        for (int k = 0; k < floors; k++) {
            int y = k * storey + 1;
            for (int x = 1; x < wx1; x++) {
                for (int h = 0; h < 3; h++) {
                    p.set(x, y + h, corridor, (x - 1) % 9 == 4 && h < 2 ? "air" : "drywall");
                }
            }
            for (int x = 9; x < wx1; x += 9) {
                for (int z = corridor + 1; z < z0 + wingD - 1; z++) {
                    for (int h = 0; h < 3; h++) {
                        p.set(x, y + h, z, "drywall");
                    }
                }
            }
            for (int x = 2; x < wx1 - 1; x++) {
                for (int z = corridor + 2; z < z0 + wingD - 2; z += 2) {
                    if ((x - 1) % 9 != 8 && (x % 2 == 0) && rng.nextDouble() < 0.85) {
                        p.set(x, y, z, rng.nextDouble() < 0.15 ? "office_chair_fallen[facing=east]" : "store_table");
                    }
                }
            }
            b.lights(0, z0, wx1, z0 + wingD - 1, y + storey - 2, 0.02);
            b.mess(1, z0 + 1, wx1 - 1, z0 + wingD - 2, y, 0.05);
        }
        if (high) {
            b.stair(1, z0 + 4, 1, storey, "north", "minecraft:spruce");
        }
        b.flat(0, z0, wx1, z0 + wingD - 1, floors * storey, wall, 1, "stone_trim");
        // the entrance: double doors under a flat canopy, the name in letters over them
        int ex = wx1 / 2;
        b.doors(ex - 1, 1, z0, "fire_door", "north");
        for (int x = ex - 3; x <= ex + 2; x++) {
            for (int z = z0 - 4; z < z0; z++) {
                p.set(x, 4, z, "minecraft:smooth_stone_slab[type=top]");
            }
        }
        p.set(ex - 3, 1, z0 - 4, "steel_mullion");
        p.set(ex - 3, 2, z0 - 4, "steel_mullion");
        p.set(ex - 3, 3, z0 - 4, "steel_mullion");
        p.set(ex + 2, 1, z0 - 4, "steel_mullion");
        p.set(ex + 2, 2, z0 - 4, "steel_mullion");
        p.set(ex + 2, 3, z0 - 4, "steel_mullion");
        Boards.fascia(p, rng, high ? "school" : "elementary", 1, floors * storey - 1, z0 - 1, wx1 - 1);
        // the gym: a tall block with a high strip of windows and a barrel roof of tin
        int gx0 = wx1 + 1, gx1 = w - 1, gz0 = z0 - 2, gz1 = Math.min(d - 1, z0 + wingD + 6);
        int gh = 9;
        b.walls(gx0, gz0, gx1, gz1, 1, gh, wall);
        b.floor(gx0 + 1, gz0 + 1, gx1 - 1, gz1 - 1, 0, "floor_boards");
        b.clear(gx0 + 1, 1, gz0 + 1, gx1 - 1, gh, gz1 - 1);
        for (int x = gx0 + 2; x < gx1 - 1; x++) {
            p.set(x, gh - 2, gz0, b.glass("x"));
            p.set(x, gh - 2, gz1, b.glass("x"));
        }
        b.gable(gx0, gx1, gz0, gz1, gh + 1, "minecraft:waxed_weathered_cut_copper", false, wall, 0);
        for (int y = 1; y <= 2; y++) {
            p.set(gx0, y, z0 + 3, "air");
        }
        // bleachers down one side, a hoop at each end, cots where it became a shelter
        for (int i = 0; i < 3; i++) {
            for (int z = gz0 + 2; z < gz1 - 1; z++) {
                p.set(gx1 - 3 + i, 1 + i, z, "minecraft:spruce_stairs[facing=east]");
            }
        }
        int hx = (gx0 + gx1) / 2;
        p.set(hx, 4, gz0 + 1, "minecraft:iron_bars");
        p.set(hx, 4, gz1 - 1, "minecraft:iron_bars");
        if (rng.nextDouble() < 0.6) {
            for (int i = 0; i < 10; i++) {
                int x = gx0 + 2 + rng.nextInt(Math.max(1, gx1 - gx0 - 6)), z = gz0 + 2 + rng.nextInt(Math.max(1, gz1 - gz0 - 3));
                p.set(x, 1, z, i % 3 == 0 ? "crate" : "minecraft:green_bed[facing=north,part=foot]");
            }
            lastStand(gx0 + 1, gx0 + 1, z0 + 3, 1);
        }
        // the bus loop and the buses still in it
        for (int x = 0; x < w; x++) {
            for (int z = 2; z <= 9; z++) {
                p.set(x, 0, z, rng.nextDouble() < 0.25 ? "asphalt_crack" : "asphalt");
            }
        }
        int buses = 1 + rng.nextInt(3);
        for (int i = 0; i < buses; i++) {
            p.vehicle(8 + i * 14 + rng.nextDouble() * 2, 1, 5.5, 270 + (float) (rng.nextDouble() * 8 - 4), "school_bus", rng.nextLong());
        }
        flagpole(ex + 5, z0 - 3, 11);
    }

    // ------------------------------------------------------------------ the picture house

    /** A small-town theatre: the marquee over the sidewalk, a neon blade, seats facing the screen. */
    void theatre(int w, int d) {
        int h = 10;
        String wall = pick("brick_buff", "brick_red", "plaster_cream", "plaster_rose");
        b.walls(0, 0, w - 1, d - 1, 1, h, wall);
        b.floor(0, 0, w - 1, d - 1, 0, "concrete_dark");
        b.floor(1, 1, w - 2, d - 2, 0, "floor_checker");
        b.clear(1, 1, 1, w - 2, h - 1, d - 2);
        b.floor(1, 1, w - 2, d - 2, h, "roof");
        int cx = w / 2;
        // the lobby front: glass doors, the ticket booth, poster cases
        for (int x = 1; x < w - 1; x++) {
            p.set(x, 1, 0, x == cx || x == cx - 1 || x == cx + 1 ? "air" : "stone_trim");
            p.set(x, 2, 0, x == cx || x == cx - 1 || x == cx + 1 ? "air" : b.glass("x"));
            p.set(x, 3, 0, b.glass("x"));
        }
        b.doors(cx - 1, 1, 0, "shop_door", "north");
        b.door(cx + 1, 1, 0, "shop_door", "north");
        p.set(cx, 1, -2, "store_teller_glass[axis=z]");
        p.set(cx, 2, -2, "store_teller_glass[axis=z]");
        p.set(cx, 3, -2, "minecraft:smooth_stone_slab[type=bottom]");
        // the marquee: a deep box over the sidewalk ringed with bulbs, the name on its face
        for (int x = 0; x < w; x++) {
            for (int z = -3; z <= -1; z++) {
                p.set(x, 4, z, "concrete_dark");
                p.set(x, 5, z, x == 0 || x == w - 1 || z == -3 ? "minecraft:smooth_quartz" : "concrete_dark");
            }
            if (x % 2 == 0) {
                p.set(x, 3, -3, rng.nextDouble() < 0.2 ? "minecraft:pearlescent_froglight" : "minecraft:iron_chain[axis=y]");
            }
        }
        Boards.fascia(p, rng, "rialto", 1, 5, -4, w - 2);
        Boards.blade(p, rng, "rialto", 6, cx, h + 1, -1, rng.nextDouble() < 0.5);
        // the screen at the back, rows of seats raked down toward it
        for (int x = 2; x < w - 2; x++) {
            for (int y = 2; y < h - 1; y++) {
                p.set(x, y, d - 2, "minecraft:white_concrete");
            }
        }
        for (int z = 6; z < d - 5; z += 2) {
            for (int x = 2; x < w - 2; x++) {
                if (x == cx) {
                    continue;
                }
                p.set(x, 1, z, rng.nextDouble() < 0.1 ? "air" : "minecraft:red_terracotta");
                p.set(x, 2, z, rng.nextDouble() < 0.15 ? "air" : "minecraft:dark_oak_stairs[facing=north]");
            }
        }
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y <= 4; y++) {
                p.set(x, y, 4, x == cx || x == cx - 1 ? "air" : "drywall");
            }
        }
        p.set(2, 1, 2, "store_counter[facing=east]");
        p.set(2, 1, 3, "store_counter[facing=east]");
        p.set(1, 1, 2, "store_shelf_grocery[facing=east]");
        b.lights(0, 0, w - 1, d - 1, h - 1, 0.0);
        b.mess(1, 1, w - 2, d - 2, 1, 0.05);
        b.flat(0, 0, w - 1, d - 1, h, wall, 1, null);
        for (int y = h + 1; y <= h + 3; y++) {
            for (int x = 0; x < w; x++) {
                p.set(x, y, 0, b.wall(wall, y));
            }
        }
    }
}
