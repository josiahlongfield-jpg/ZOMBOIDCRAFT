package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * A house, drawn in its lot's own coordinates through a {@link Placer}: the front faces north
 * (local z = 0 is the street side) and x runs along the front. Styles cover Kentucky's housing
 * stock: one-storey ranches with a garage, two-storey colonials, bungalows with a deep porch,
 * farmhouses with a wrap-around porch and tin roof, cabins and mobile homes. Each is weathered
 * to a condition (shut up, looted, boarded up by survivors, burnt out, or falling in), and
 * furnished room by room with searchable cupboards.
 */
public final class House {
    public enum Style { RANCH, COLONIAL, BUNGALOW, FARMHOUSE, CABIN, TRAILER, SHOTGUN, MANSION }

    public enum Condition { SHUT, LOOTED, BOARDED, BURNT, RUIN }

    /** Wall, trim, roof (a stairs family) and floor materials for one house. */
    public record Palette(String wall, String trim, String roof, String floor, String foundation) {}

    private static final String[] SIDING = {"siding_white", "siding_blue", "siding_sage", "siding_yellow", "siding_grey",
            "siding_brown", "siding_red"};
    private static final String[] BRICK = {"brick_red", "brick_dark", "brick_buff", "brick_orange"};
    private static final String[] PLASTER = {"plaster_cream", "plaster_sage", "plaster_blue", "plaster_rose"};
    private static final String[] ROOFS = {"minecraft:deepslate_tile", "minecraft:dark_oak", "minecraft:spruce",
            "minecraft:cobbled_deepslate", "minecraft:mud_brick", "minecraft:tuff_brick", "minecraft:blackstone"};
    private static final String[] TIN = {"minecraft:waxed_weathered_cut_copper", "minecraft:waxed_exposed_cut_copper",
            "minecraft:waxed_oxidized_cut_copper"};
    private static final String[] FLOORS = {"minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
            "floor_boards"};
    private static final String[] BEDS = {"red", "blue", "white", "gray", "brown", "green", "light_blue", "yellow"};
    private static final String[] CARPETS = {"brown", "gray", "red", "blue", "green", "light_gray", "cyan"};

    public static final int STOREY = 4;

    private final Placer p;
    private final SplittableRandom rng;
    private final Style style;
    private final Condition cond;
    private final Palette pal;
    /** Lot size; the body sits behind the porch, from z = front. */
    private final int w, d;
    private final int floors;
    private int x0, x1, z0, z1;
    private int garageX0 = -1, garageX1 = -1;
    private final String shutterWood;

    public House(Placer p, SplittableRandom rng, Style style, Condition cond, Palette pal, int w, int d, int floors) {
        this.p = p;
        this.rng = rng;
        this.style = style;
        this.cond = cond;
        this.pal = pal;
        this.w = w;
        this.d = d;
        this.floors = floors;
        this.shutterWood = rng.nextBoolean() ? "minecraft:dark_oak" : rng.nextBoolean() ? "minecraft:spruce" : "minecraft:pale_oak";
    }

    /** A random palette suited to the style. */
    public static Palette palette(Style style, SplittableRandom rng) {
        String wall;
        double r = rng.nextDouble();
        if (style == Style.CABIN) {
            wall = rng.nextBoolean() ? "minecraft:spruce_log" : "minecraft:dark_oak_log";
        } else if (style == Style.TRAILER) {
            wall = r < 0.5 ? "siding_white" : r < 0.75 ? "siding_grey" : "siding_yellow";
        } else if (style == Style.MANSION) {
            wall = r < 0.6 ? BRICK[rng.nextInt(BRICK.length)] : "siding_white";
        } else if (r < 0.62) {
            wall = SIDING[rng.nextInt(SIDING.length)];
        } else if (r < 0.88) {
            wall = BRICK[rng.nextInt(BRICK.length)];
        } else {
            wall = PLASTER[rng.nextInt(PLASTER.length)];
        }
        String trim = rng.nextDouble() < 0.7 ? "minecraft:pale_oak_planks" : "minecraft:dark_oak_planks";
        String roof = style == Style.FARMHOUSE || style == Style.CABIN && rng.nextBoolean() || style == Style.TRAILER
                ? TIN[rng.nextInt(TIN.length)] : ROOFS[rng.nextInt(ROOFS.length)];
        String floor = FLOORS[rng.nextInt(FLOORS.length)];
        String foundation = wall.startsWith("brick") ? "concrete_dark" : rng.nextBoolean() ? "minecraft:stone_bricks" : "concrete_panel";
        return new Palette(wall, trim, roof, floor, foundation);
    }

    public static Condition condition(SplittableRandom rng) {
        double r = rng.nextDouble();
        return r < 0.38 ? Condition.SHUT : r < 0.70 ? Condition.LOOTED : r < 0.84 ? Condition.BOARDED
                : r < 0.93 ? Condition.BURNT : Condition.RUIN;
    }

    // ------------------------------------------------------------------ materials

    /** The wall block at a spot, with weathered variants scattered over it. */
    private String wall(int x, int y, int z) {
        String base = pal.wall;
        if (base.startsWith("minecraft:")) {
            return base.endsWith("_log") ? base + "[axis=" + (z == z0 || z == z1 ? "x" : "z") + "]" : base;
        }
        double r = rng.nextDouble();
        if (cond == Condition.BURNT && r < 0.45) {
            return base + "_soot";
        }
        if (y <= 2 && r < 0.18) {
            return base + "_moss";
        }
        if (r < 0.12) {
            return base + "_stain";
        }
        if (r < 0.20) {
            return base + "_b";
        }
        if (r < 0.24) {
            return base + "_crack";
        }
        return base;
    }

    private String stairs(String facing, boolean top) {
        return pal.roof + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    private String slab(boolean top) {
        return pal.roof + "_slab[type=" + (top ? "top" : "bottom") + "]";
    }

    // ------------------------------------------------------------------ build

    public void build() {
        int porch = switch (style) {
            case BUNGALOW, FARMHOUSE -> 4;
            case COLONIAL, MANSION -> 3;
            case SHOTGUN, CABIN -> 3;
            default -> 0;
        };
        x0 = 0;
        x1 = w - 1;
        z0 = porch;
        z1 = d - 1;
        if (style == Style.RANCH && w >= 16) {
            garageX0 = w - 7;
            garageX1 = w - 1;
            x1 = garageX0 - 1;
        }
        int height = floors * STOREY;
        shell(x0, x1, z0, z1, height);
        if (garageX0 >= 0) {
            garage();
        }
        windowsAndDoors(height);
        if (cond != Condition.BURNT) {
            for (int f = 0; f < floors; f++) {
                rooms(f);
            }
        }
        if (style == Style.TRAILER) {
            flatRoof(height);
        } else if (style == Style.RANCH || style == Style.SHOTGUN) {
            gable(x0, x1 + (garageX1 > 0 ? garageX1 - x1 : 0), z0, z1, height, style == Style.SHOTGUN);
        } else {
            gable(x0, x1, z0, z1, height, style == Style.BUNGALOW);
        }
        if (porch > 0) {
            porch(porch);
        }
        if (style != Style.TRAILER && rng.nextDouble() < 0.55) {
            chimney(height);
        }
        weather(height);
    }

    /** Floors, walls with trimmed corners, a foundation course. */
    private void shell(int a0, int a1, int b0, int b1, int height) {
        for (int x = a0; x <= a1; x++) {
            for (int z = b0; z <= b1; z++) {
                p.set(x, 0, z, (x == a0 || x == a1 || z == b0 || z == b1) ? pal.foundation : pal.floor);
                for (int y = 1; y <= height; y++) {
                    boolean edge = x == a0 || x == a1 || z == b0 || z == b1;
                    if (!edge) {
                        p.set(x, y, z, y % STOREY == 0 ? pal.floor : "air");
                        continue;
                    }
                    boolean corner = (x == a0 || x == a1) && (z == b0 || z == b1);
                    p.set(x, y, z, corner && !pal.wall.endsWith("_log") ? pal.trim : wall(x, y, z));
                }
            }
        }
        if (style == Style.TRAILER) {
            // skirting and the hitch
            for (int x = a0; x <= a1; x++) {
                p.set(x, 0, b0, "chainlink[facing=north]");
            }
            p.set(-1, 0, (b0 + b1) / 2, "rust");
        }
    }

    private void garage() {
        int h = STOREY;
        for (int x = garageX0; x <= garageX1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == garageX1 || z == z0 || z == z1;
                p.set(x, 0, z, "floor_concrete");
                for (int y = 1; y <= h; y++) {
                    p.set(x, y, z, edge ? (y == h ? pal.trim : wall(x, y, z)) : y == h ? pal.floor : "air");
                }
            }
        }
        // a roll-up door at the front, often forced
        boolean open = rng.nextDouble() < 0.45;
        for (int x = garageX0 + 1; x <= garageX1 - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, z0, open && y < 3 ? "air" : "garage_door[facing=north]");
            }
        }
        // shelves of junk, a workbench, maybe a car
        for (int z = z0 + 2; z < z1; z += 2) {
            p.set(garageX1 - 1, 1, z, "minecraft:barrel[facing=up,loot=" + (rng.nextBoolean() ? "hardware" : "storage") + "]");
        }
        p.set(garageX0 + 1, 1, z1 - 1, "minecraft:crafting_table");
        if (rng.nextDouble() < 0.35) {
            p.set(garageX0 + 2, 1, z0 + 4, "store_paint_cans");
        }
    }

    /** Window openings with sills and shutters, and the doors. */
    private void windowsAndDoors(int height) {
        int doorX = style == Style.SHOTGUN ? x0 + 2 : (x0 + x1) / 2 + (rng.nextBoolean() ? 0 : -1);
        for (int f = 0; f < floors; f++) {
            int y = f * STOREY + 2;
            // front and back
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                if (f == 0 && Math.abs(x - doorX) <= 1) {
                    continue;
                }
                window(x, y, z0, "north", "x");
                if (rng.nextDouble() < 0.8) {
                    window(x, y, z1, "south", "x");
                }
            }
            // sides
            for (int z = z0 + 2; z <= z1 - 2; z += 3) {
                if (rng.nextDouble() < 0.75) {
                    window(x0, y, z, "west", "z");
                }
                if (garageX0 < 0 && rng.nextDouble() < 0.75) {
                    window(x1, y, z, "east", "z");
                }
            }
        }
        door(doorX, z0, "south");
        // a back door to the yard
        door((x0 + x1) / 2 + 2 <= x1 - 1 ? (x0 + x1) / 2 + 2 : (x0 + x1) / 2, z1, "north");
    }

    private void window(int x, int y, int z, String out, String axis) {
        String glass;
        double r = rng.nextDouble();
        if (cond == Condition.BOARDED && r < 0.85 || r < 0.12) {
            glass = "boarded_window[axis=" + axis + "]";
        } else if (cond == Condition.BURNT || cond == Condition.RUIN || r < 0.55) {
            glass = r < 0.75 ? "window_broken[axis=" + axis + "]" : "window_empty[axis=" + axis + "]";
        } else {
            glass = "window[axis=" + axis + "]";
        }
        p.set(x, y, z, glass);
        p.set(x, y + 1, z, glass);
        // the sill sits outside the wall, one block out
        int[] o = outward(out);
        p.set(x + o[0], y - 1, z + o[1], "sill[facing=" + out + "]");
        if (style == Style.COLONIAL || style == Style.MANSION || style == Style.FARMHOUSE && rng.nextBoolean()) {
            int[] side = axis.equals("x") ? new int[] {1, 0} : new int[] {0, 1};
            for (int s = -1; s <= 1; s += 2) {
                int sx = x + side[0] * s + o[0], sz = z + side[1] * s + o[1];
                if (rng.nextDouble() < 0.85) {
                    // open trapdoors lie flat against the wall either side of the window
                    String shutter = shutterWood + "_trapdoor[facing=" + out + ",half=bottom,open=true]";
                    p.set(sx, y, sz, shutter);
                    p.set(sx, y + 1, sz, rng.nextDouble() < 0.1 ? "air" : shutter);
                }
            }
        }
    }

    private static int[] outward(String facing) {
        return switch (facing) {
            case "north" -> new int[] {0, -1};
            case "south" -> new int[] {0, 1};
            case "east" -> new int[] {1, 0};
            default -> new int[] {-1, 0};
        };
    }

    /** A wooden door; facing is the side it sits flush with (see city.Doors). */
    private void door(int x, int z, String facing) {
        String wood = rng.nextBoolean() ? "minecraft:spruce_door" : rng.nextBoolean() ? "minecraft:dark_oak_door" : "minecraft:oak_door";
        double r = rng.nextDouble();
        if (cond == Condition.BOARDED && r < 0.7) {
            p.set(x, 1, z, "minecraft:spruce_planks");
            p.set(x, 2, z, "boarded_window[axis=" + (facing.equals("north") || facing.equals("south") ? "x" : "z") + "]");
            return;
        }
        if (cond == Condition.LOOTED && r < 0.35 || cond == Condition.RUIN || cond == Condition.BURNT) {
            p.set(x, 1, z, "air");
            p.set(x, 2, z, "air");
            return;
        }
        boolean open = cond == Condition.LOOTED || r < 0.3;
        String props = "[facing=" + facing + ",hinge=" + (rng.nextBoolean() ? "left" : "right") + ",open=" + open;
        p.set(x, 1, z, wood + props + ",half=lower]");
        p.set(x, 2, z, wood + props + ",half=upper]");
    }

    /** A gable roof whose ridge runs along x, overhanging a block all round. */
    private void gable(int a0, int a1, int b0, int b1, int height, boolean ridgeAlongZ) {
        int base = height + 1;
        if (!ridgeAlongZ) {
            int span = b1 - b0 + 3;
            int half = span / 2;
            for (int i = 0; i < half; i++) {
                int y = base + i;
                for (int x = a0 - 1; x <= a1 + 1; x++) {
                    roofTile(x, y, b0 - 1 + i, stairs("south", false));
                    roofTile(x, y, b1 + 1 - i, stairs("north", false));
                    if (i > 0) {
                        roofTile(x, y - 1, b0 - 1 + i, stairs("north", true));
                        roofTile(x, y - 1, b1 + 1 - i, stairs("south", true));
                    }
                }
                // gable ends
                for (int z = b0 + i; z <= b1 - i; z++) {
                    if (z > b0 - 1 + i && z < b1 + 1 - i) {
                        p.set(a0, y, z, wall(a0, y, z));
                        p.set(a1, y, z, wall(a1, y, z));
                    }
                }
                if (i == 1 && b1 - b0 > 6) {
                    // a round attic vent in each gable
                    p.set(a0, y + 1, (b0 + b1) / 2, "minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]");
                }
            }
            if (span % 2 == 1) {
                for (int x = a0 - 1; x <= a1 + 1; x++) {
                    roofTile(x, base + half, b0 - 1 + half, slab(false));
                }
            }
        } else {
            int span = a1 - a0 + 3;
            int half = span / 2;
            for (int i = 0; i < half; i++) {
                int y = base + i;
                for (int z = b0 - 1; z <= b1 + 1; z++) {
                    roofTile(a0 - 1 + i, y, z, stairs("east", false));
                    roofTile(a1 + 1 - i, y, z, stairs("west", false));
                    if (i > 0) {
                        roofTile(a0 - 1 + i, y - 1, z, stairs("west", true));
                        roofTile(a1 + 1 - i, y - 1, z, stairs("east", true));
                    }
                }
                for (int x = a0 + i; x <= a1 - i; x++) {
                    if (x > a0 - 1 + i && x < a1 + 1 - i) {
                        p.set(x, y, b0, wall(x, y, b0));
                        p.set(x, y, b1, wall(x, y, b1));
                    }
                }
            }
            if (span % 2 == 1) {
                for (int z = b0 - 1; z <= b1 + 1; z++) {
                    roofTile(a0 - 1 + half, base + half, z, slab(false));
                }
            }
        }
    }

    /** A roof block, unless the roof has burnt or fallen through here. */
    private void roofTile(int x, int y, int z, String block) {
        double r = rng.nextDouble();
        if ((cond == Condition.BURNT && r < 0.35) || (cond == Condition.RUIN && r < 0.25)) {
            return;
        }
        if (cond == Condition.SHUT && r < 0.01 || cond == Condition.LOOTED && r < 0.02) {
            return;  // a few tiles blown off
        }
        p.set(x, y, z, block);
    }

    private void flatRoof(int height) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0; z <= z1; z++) {
                roofTile(x, height + 1, z, pal.roof + "_slab[type=bottom]");
            }
        }
    }

    private void porch(int depth) {
        int a0 = x0, a1 = style == Style.SHOTGUN ? x1 : x1;
        int roofY = STOREY;
        for (int x = a0; x <= a1; x++) {
            for (int z = z0 - depth; z < z0; z++) {
                p.set(x, 0, z, rng.nextDouble() < 0.06 ? "air" : "minecraft:spruce_planks");
                boolean post = (x == a0 || x == a1 || (x - a0) % 4 == 0) && z == z0 - depth;
                if (post) {
                    for (int y = 1; y < roofY; y++) {
                        p.set(x, y, z, style == Style.CABIN ? "minecraft:spruce_log" : "minecraft:pale_oak_fence");
                    }
                } else if (z == z0 - depth && Math.abs(x - (a0 + a1) / 2) > 1) {
                    p.set(x, 1, z, rng.nextDouble() < 0.15 ? "air" : "minecraft:pale_oak_fence");
                }
                roofTile(x, roofY, z, slab(false));
            }
        }
        // steps down to the path
        for (int x = (a0 + a1) / 2 - 1; x <= (a0 + a1) / 2 + 1; x++) {
            p.set(x, 0, z0 - depth, "minecraft:spruce_planks");
        }
        // a rocker or a swing, a forgotten plant pot
        if (rng.nextDouble() < 0.6) {
            p.set(a0 + 1, 1, z0 - depth + 1, "minecraft:spruce_stairs[facing=south]");
        }
        if (rng.nextDouble() < 0.5) {
            p.set(a1 - 1, 1, z0 - 1, "minecraft:flower_pot");
        }
    }

    private void chimney(int height) {
        int x = rng.nextBoolean() ? x0 - 1 : (garageX0 > 0 ? x0 - 1 : x1 + 1);
        int z = (z0 + z1) / 2;
        int top = height + (z1 - z0) / 2 + 3;
        for (int y = 0; y <= top; y++) {
            p.set(x, y, z, y == top ? "minecraft:brick_wall" : "minecraft:bricks");
        }
    }

    // ------------------------------------------------------------------ rooms

    /** Splits a floor into rooms with drywall partitions and furnishes them. */
    private void rooms(int f) {
        int y = f * STOREY + 1;
        int ix0 = x0 + 1, ix1 = x1 - 1, iz0 = z0 + 1, iz1 = z1 - 1;
        if (ix1 - ix0 < 3 || iz1 - iz0 < 3) {
            return;
        }
        int midZ = (iz0 + iz1) / 2 + (rng.nextBoolean() ? 0 : 1);
        int midX = (ix0 + ix1) / 2;
        boolean big = ix1 - ix0 >= 8 && iz1 - iz0 >= 6 && style != Style.TRAILER;
        if (big) {
            for (int x = ix0; x <= ix1; x++) {
                partition(x, y, midZ);
            }
            for (int z = midZ + 1; z <= iz1; z++) {
                partition(midX, y, z);
            }
            gap(midX - 1, y, midZ, true);
            gap(midX + 2, y, midZ, true);
            gap(midX, y, midZ + 2, false);
        }
        String[] kinds = f == 0 ? new String[] {"living", "kitchen", "bath"} : new String[] {"bed", "bed", "bath"};
        if (!big) {
            furnish(f == 0 ? "living" : "bed", ix0, iz0, ix1, iz1, y);
            if (f == 0) {
                furnish("kitchen", ix0, (iz0 + iz1) / 2 + 1, ix1, iz1, y);
            }
            return;
        }
        furnish(kinds[0], ix0, iz0, ix1, midZ - 1, y);
        furnish(kinds[1], ix0, midZ + 1, midX - 1, iz1, y);
        furnish(kinds[2], midX + 1, midZ + 1, ix1, iz1, y);
        if (f == 0 && floors > 1) {
            stairsUp(ix1, iz0 + 1, y);
        }
    }

    private void partition(int x, int y, int z) {
        for (int h = 0; h < 3; h++) {
            String wall = rng.nextDouble() < 0.15 ? "wallpaper_rotten" : rng.nextDouble() < 0.1 ? "drywall_peel" : "drywall";
            p.set(x, y + h, z, wall);
        }
    }

    private void gap(int x, int y, int z, boolean alongX) {
        p.set(x, y, z, "air");
        p.set(x, y + 1, z, "air");
    }

    /** A straight flight up the side wall to the next floor. */
    private void stairsUp(int x, int z, int y) {
        for (int i = 0; i < STOREY; i++) {
            p.set(x, y + i, z + i, "minecraft:spruce_stairs[facing=south]");
            p.set(x, y + STOREY - 1, z + i, "air");
            for (int h = y + i + 1; h < y + STOREY; h++) {
                p.set(x, h, z + i, "air");
            }
        }
    }

    private String loot(String block, String table) {
        int open = block.indexOf('[');
        return open < 0 ? block + "[loot=" + table + "]" : block.substring(0, block.length() - 1) + ",loot=" + table + "]";
    }

    private void furnish(String kind, int a0, int b0, int a1, int b1, int y) {
        if (a1 - a0 < 1 || b1 - b0 < 1) {
            return;
        }
        boolean messy = cond == Condition.LOOTED || cond == Condition.RUIN;
        switch (kind) {
            case "kitchen" -> {
                // counters and cupboards along the back wall, a stove, a fridge, a table
                for (int x = a0; x <= a1; x++) {
                    String block = x == a0 ? "store_cooler[facing=north]" : x == a0 + 2 ? "store_oven[facing=north]"
                            : loot("minecraft:barrel[facing=up]", "kitchen");
                    p.set(x, y, b1, block);
                    if (x == a0) {
                        p.set(x, y + 1, b1, "store_cooler_top[facing=north]");
                    } else if (rng.nextDouble() < 0.5) {
                        p.set(x, y + 2, b1, loot("minecraft:barrel[facing=north]", "kitchen"));
                    }
                }
                if (b1 - b0 >= 3 && a1 - a0 >= 3) {
                    p.set((a0 + a1) / 2, y, b0 + 1, "store_table");
                    p.set((a0 + a1) / 2 - 1, y, b0 + 1, messy && rng.nextBoolean() ? "air" : "store_chair[facing=east]");
                    p.set((a0 + a1) / 2 + 1, y, b0 + 1, "store_chair[facing=west]");
                }
                floor(a0, b0, a1, b1, y - 1, rng.nextBoolean() ? "floor_lino" : "floor_checker");
            }
            case "living" -> {
                String carpet = "minecraft:" + CARPETS[rng.nextInt(CARPETS.length)] + "_carpet";
                for (int x = a0 + 1; x < a1; x++) {
                    for (int z = b0 + 1; z < b1; z++) {
                        if (rng.nextDouble() < 0.85) {
                            p.set(x, y, z, carpet);
                        }
                    }
                }
                // a sofa facing the television, a bookcase, a lamp
                for (int x = a0 + 1; x <= Math.min(a1 - 1, a0 + 3); x++) {
                    p.set(x, y, b1, "minecraft:dark_oak_stairs[facing=south]");
                }
                p.set(a0 + 2, y, b0, "minecraft:black_terracotta");
                p.set(a1, y, b0, "minecraft:bookshelf");
                p.set(a1, y + 1, b0, rng.nextBoolean() ? "minecraft:bookshelf" : "minecraft:flower_pot");
                if (rng.nextDouble() < 0.4) {
                    p.set(a1, y, b1, loot("minecraft:chest[facing=west]", rng.nextDouble() < 0.15 ? "guns" : "storage"));
                }
            }
            case "bed" -> {
                String colour = BEDS[rng.nextInt(BEDS.length)];
                int bx = a0 + 1;
                p.set(bx, y, b0, "minecraft:" + colour + "_bed[facing=north,part=head]");
                p.set(bx, y, b0 + 1, "minecraft:" + colour + "_bed[facing=north,part=foot]");
                p.set(bx + 1, y, b0, loot("minecraft:barrel[facing=up]", "clothes"));
                p.set(a1, y, b0, loot("minecraft:barrel[facing=up]", "clothes"));
                p.set(a1, y + 1, b0, loot("minecraft:barrel[facing=west]", "clothes"));
                if (rng.nextBoolean()) {
                    p.set(a1, y, b1, "minecraft:" + CARPETS[rng.nextInt(CARPETS.length)] + "_carpet");
                }
            }
            case "bath" -> {
                p.set(a0, y, b1, "minecraft:cauldron");
                p.set(a1, y, b1, "minecraft:white_glazed_terracotta");
                p.set(a1, y + 1, b1, loot("minecraft:barrel[facing=north]", "pharmacy"));
                floor(a0, b0, a1, b1, y - 1, "floor_checker");
            }
            default -> {
            }
        }
        if (messy) {
            for (int i = 0; i < 3; i++) {
                int x = a0 + rng.nextInt(a1 - a0 + 1), z = b0 + rng.nextInt(b1 - b0 + 1);
                if (p.get(x, y, z) == null) {
                    p.set(x, y, z, pickOne("paper", "glass_shards", "cardboard_box[facing=north]", "trash_bag[facing=east]", "blood_splat"));
                }
            }
        }
    }

    private void floor(int a0, int b0, int a1, int b1, int y, String block) {
        for (int x = a0; x <= a1; x++) {
            for (int z = b0; z <= b1; z++) {
                p.set(x, y, z, block);
            }
        }
    }

    private String pickOne(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ weathering

    /** Ivy up the walls, moss on the roof, gutters hanging, blood at the door. */
    private void weather(int height) {
        int ivy = cond == Condition.RUIN ? 8 : 3;
        for (int i = 0; i < ivy; i++) {
            boolean front = rng.nextBoolean();
            int x = x0 + rng.nextInt(Math.max(1, x1 - x0 + 1));
            int z = front ? z0 - 1 : z1 + 1;
            int len = 2 + rng.nextInt(height);
            for (int y = 1; y <= len; y++) {
                if (p.get(x, y, z) == null) {
                    p.set(x, y, z, "vines[facing=" + (front ? "south" : "north") + "]");
                }
            }
        }
        if (cond == Condition.BURNT) {
            for (int i = 0; i < 6; i++) {
                int x = x0 + 1 + rng.nextInt(Math.max(1, x1 - x0 - 1)), z = z0 + 1 + rng.nextInt(Math.max(1, z1 - z0 - 1));
                p.set(x, 1, z, rng.nextBoolean() ? "scorch" : "rubble_pile");
            }
        }
        if (cond == Condition.RUIN) {
            // a wall section has come down
            int x = x0 + 1 + rng.nextInt(Math.max(1, x1 - x0 - 2));
            for (int y = 2; y <= height; y++) {
                for (int dx = 0; dx < 2 + rng.nextInt(2); dx++) {
                    if (rng.nextDouble() < 0.8) {
                        p.set(x + dx, y + rng.nextInt(2), z1, "air");
                    }
                }
            }
            for (int dx = -1; dx < 3; dx++) {
                p.set(x + dx, 1, z1 + 1, "rubble");
            }
        }
        if (rng.nextDouble() < 0.25) {
            p.set((x0 + x1) / 2, 0, z0 - 1, "blood_splat");
        }
    }
}
