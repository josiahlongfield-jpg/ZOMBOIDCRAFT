package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * A house, drawn in its lot's own coordinates through a {@link Placer}: the front faces north
 * (local z = 0 is the street side) and x runs along the front. Styles cover Kentucky's housing
 * stock: one-storey ranches with a garage, two-storey colonials, bungalows with a deep porch,
 * farmhouses with a wrap-around porch and tin roof, cabins and mobile homes. Each is weathered
 * to a condition (shut up, looted, boarded up by survivors, burnt out, or falling in), and
 * laid out and furnished inside by {@link Interior}: a hall, stairs, rooms with their doors,
 * wallpaper and searchable cupboards.
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
    private static final String[] SIDES = {"north", "east", "south", "west"};

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
    private int doorX;
    private final String shutterWood;
    /** Share of windows still lit, for the few houses with a generator running. */
    private double lit;
    /** The rooms, stairs and furniture; null for a house burnt out to its shell. */
    private Interior interior;

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

    /** Lights some of the windows and the porch lamp: someone, or a generator, is still here. */
    public House lit(double share) {
        this.lit = share;
        return this;
    }

    /** After {@link #build()}: the front door's x, in the house's frame. */
    public int doorX() {
        return doorX;
    }

    /** After {@link #build()}: the z of the front wall (behind any porch). */
    public int frontZ() {
        return z0;
    }

    /** After {@link #build()}: the garage's x span, or -1 when there is none. */
    public int garageX0() {
        return garageX0;
    }

    public int garageX1() {
        return garageX1;
    }

    /** After {@link #build()}: the rooms in the house's frame, one per line (for previews). */
    public String describe() {
        return interior == null ? "" : interior.describe();
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
        porch = Math.min(porch, Math.max(0, d - 11));  // a shallow lot loses the porch before the rooms
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
        if (cond != Condition.BURNT) {
            interior = new Interior(p, rng, style, cond, x0, x1, z0, z1, floors, style != Style.CABIN, garageX0 >= 0, lit);
            interior.plan();
        }
        shell(x0, x1, z0, z1, height);
        if (garageX0 >= 0) {
            garage();
        }
        windowsAndDoors(height);
        if (interior != null) {
            interior.build();
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
                p.set(x, 0, b0, "chainlink[axis=x]");
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
        // a workbench under a pegboard along the back, shelves and the freezer down the side,
        // boxes nobody unpacked; the middle left clear for the car
        int gx0 = garageX0, gx1 = garageX1 - 1, gz0 = z0 + 1, gz1 = z1 - 1;
        int into = interior != null ? interior.garageZ() : -1;
        for (int x = gx0; x <= gx1; x++) {
            for (int z = gz0; z <= gz1; z++) {
                p.set(x, h, z, "home_floor_oak");
            }
        }
        java.util.function.BiPredicate<Integer, Integer> clear = (x, z) -> x <= gx0 + 1 && Math.abs(z - into) <= 0;
        String[] back = {"home_workbench", "home_workbench", "home_tool_chest"};
        for (int i = 0; i < back.length && gx0 + 1 + i < gx1; i++) {
            int x = gx0 + 1 + i;
            if (!clear.test(x, gz1)) {
                p.set(x, 1, gz1, back[i] + "[facing=north]");
                if (i < 2) {
                    p.set(x, 2, gz1, "store_pegboard[facing=south]");
                }
            }
        }
        p.set(gx1, 1, gz1, "home_boxes");
        p.set(gx1, 2, gz1, "home_boxes");
        String[] side = {"home_garage_shelf", "home_garage_shelf", "home_freezer",
                rng.nextDouble() < (style == Style.RANCH ? 0.2 : 0.1) ? "home_gun_cabinet" : "home_water_heater"};
        for (int i = 0; i < side.length && gz1 - 1 - i > gz0 + 1; i++) {
            int z = gz1 - 1 - i;
            String b = side[i];
            boolean tall = b.equals("home_gun_cabinet") || b.equals("home_water_heater");
            p.set(gx1, 1, z, b + (b.equals("home_water_heater") ? "" : "[facing=west]"));
            if (tall) {
                p.set(gx1, 2, z, b + "_top" + (b.equals("home_water_heater") ? "" : "[facing=west]"));
            }
        }
        if (rng.nextDouble() < 0.6) {
            p.set(gx0 + 1, 1, gz0 + 1, "home_mower[facing=" + SIDES[rng.nextInt(4)] + "]");
        }
        p.set((gx0 + gx1) / 2, 1, (gz0 + gz1) / 2 + 1, "home_stain_dark");
        p.set((gx0 + gx1) / 2, 3, (gz0 + gz1) / 2, "ceiling_light_off");
        if (rng.nextDouble() < 0.35) {
            p.set(gx0 + 2, 1, gz0 + 3, "store_paint_cans");
        }
    }

    /** Window openings with sills and shutters, and the doors. */
    private void windowsAndDoors(int height) {
        if (interior != null) {
            // centred on the rooms behind them
            doorX = interior.doorX();
            for (int[] w : interior.windows()) {
                window(w[0], w[2] * STOREY + 2, w[1], SIDES[w[3]], w[3] == 0 || w[3] == 2 ? "x" : "z");
            }
            door(doorX, z0, "south");
            if (interior.backX() >= 0) {
                door(interior.backX(), z1, "north");
            }
            if (interior.garageZ() >= 0) {
                door(x1, interior.garageZ(), "west");
            }
            porchLamp();
            return;
        }
        doorX = style == Style.SHOTGUN ? x0 + 2 : (x0 + x1) / 2 + (rng.nextBoolean() ? 0 : -1);
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
        porchLamp();
        // a back door to the yard
        door((x0 + x1) / 2 + 2 <= x1 - 1 ? (x0 + x1) / 2 + 2 : (x0 + x1) / 2, z1, "north");
    }

    private void porchLamp() {
        boolean on = lit > 0 && rng.nextDouble() < 0.8;
        p.set(doorX + 1, 3, z0 - 1, on ? "wall_lantern" : "wall_lantern_off");
    }

    private void window(int x, int y, int z, String out, String axis) {
        String glass;
        double r = rng.nextDouble();
        if (cond == Condition.BOARDED && r < 0.85 || r < 0.12) {
            glass = "boarded_window[axis=" + axis + "]";
        } else if (cond == Condition.BURNT || cond == Condition.RUIN || r < 0.55) {
            glass = r < 0.75 ? "window_broken[axis=" + axis + "]" : "window_empty[axis=" + axis + "]";
        } else {
            glass = (rng.nextDouble() < lit ? "window_lit" : "window") + "[axis=" + axis + "]";
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
        if (cond == Condition.BOARDED && r < 0.7 && !facing.equals("west")) {
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
