package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A house, drawn in its lot's own coordinates through a {@link Placer}: the front faces north
 * (local z = 0 is the street side) and x runs along the front. Styles cover Kentucky's housing
 * stock: one-storey ranches with a garage, two-storey colonials, bungalows with a deep porch,
 * farmhouses with a wrap-around porch and tin roof, cabins and mobile homes. Each is weathered
 * to a condition (shut up, looted, boarded up by survivors, burnt out, or falling in), and
 * laid out and furnished inside by {@link Interior}: a hall, stairs, rooms with their doors,
 * wallpaper and searchable cupboards.
 *
 * <p>Outside, everything comes from the exterior kit (the ext_ blocks): sloped shingle, slate or
 * tin courses with gutters on the eaves and barge boards on the rakes, double-hung windows with
 * casings, sills and shutters, painted doors under a lamp and the house number, porches on posts
 * with railings, swings and rockers, chimneys at the fireplaces inside, downspouts, a foundation
 * course with crawlspace vents, and the marks of what happened here.
 */
public final class House {
    public enum Style { RANCH, COLONIAL, BUNGALOW, FARMHOUSE, CABIN, TRAILER, SHOTGUN, MANSION }

    public enum Condition { SHUT, LOOTED, BOARDED, BURNT, RUIN }

    /**
     * Materials for one house: wall, corner trim, roof (an exterior-kit roof kind such as
     * "charcoal" or "tin_red"), floor, foundation, window frames (white, brown or alu), shutter
     * paint (null for none) and the front door's paint.
     */
    public record Palette(String wall, String trim, String roof, String floor, String foundation, String frame,
                          String shutter, String door) {}

    private static final String[] SIDING = {"siding_white", "siding_blue", "siding_sage", "siding_yellow", "siding_grey",
            "siding_brown", "siding_red"};
    private static final String[] BRICK = {"brick_red", "brick_dark", "brick_buff", "brick_orange"};
    private static final String[] PLASTER = {"plaster_cream", "plaster_sage", "plaster_blue", "plaster_rose"};
    private static final String[] SHINGLES = {"charcoal", "charcoal", "grey", "grey", "brown", "brown", "green", "red"};
    private static final String[] TIN = {"tin_galv", "tin_galv", "tin_red", "tin_green"};
    private static final String[] SHUTTERS = {"black", "black", "green", "maroon", "blue"};
    private static final String[] DOORS = {"red", "green", "navy", "white", "black", "oak"};
    private static final String[] FLOORS = {"minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
            "floor_boards"};
    private static final String[] SIDES = {"north", "east", "south", "west"};
    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

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
    private int porch;
    /** Share of windows still lit, for the few houses with a generator running. */
    private double lit;
    /** The rooms, stairs and furniture; null for a house burnt out to its shell. */
    private Interior interior;

    // the roof, in its own frame: u runs along the ridge, v across it
    private boolean alongZ;
    private int ru0, ru1, rv0, rv1, base, half, span;
    /** Per eave (front, back): the u span where the gutter has come away, or {1, 0}. */
    private final int[][] lost = {{1, 0}, {1, 0}};
    /** Clusters of moss and holes on the roof: side, u, row, radius squared. */
    private final List<int[]> moss = new ArrayList<>(), holes = new ArrayList<>();
    /** A tarp over a hole: side, u0, u1, row0, row1; or null. */
    private int[] tarp;

    public House(Placer p, SplittableRandom rng, Style style, Condition cond, Palette pal, int w, int d, int floors) {
        this.p = p;
        this.rng = rng;
        this.style = style;
        this.cond = cond;
        this.pal = pal;
        this.w = w;
        this.d = d;
        this.floors = floors;
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
        String roof = switch (style) {
            case FARMHOUSE -> TIN[rng.nextInt(TIN.length)];
            case TRAILER -> "tin_galv";
            case CABIN -> rng.nextBoolean() ? TIN[rng.nextInt(TIN.length)] : rng.nextBoolean() ? "brown" : "green";
            case MANSION -> rng.nextDouble() < 0.7 ? "slate" : "charcoal";
            default -> SHINGLES[rng.nextInt(SHINGLES.length)];
        };
        String floor = FLOORS[rng.nextInt(FLOORS.length)];
        String foundation = wall.startsWith("brick") ? "concrete_dark" : rng.nextBoolean() ? "minecraft:stone_bricks" : "concrete_panel";
        String frame = style == Style.TRAILER ? "alu" : style == Style.CABIN || rng.nextDouble() < 0.22 ? "brown" : "white";
        double shutters = switch (style) {
            case COLONIAL -> 0.9;
            case MANSION -> 0.85;
            case FARMHOUSE -> 0.5;
            case SHOTGUN -> 0.45;
            case RANCH -> 0.35;
            case BUNGALOW -> 0.3;
            case CABIN -> 0.15;
            case TRAILER -> 0;
        };
        String shutter = rng.nextDouble() < shutters ? SHUTTERS[rng.nextInt(SHUTTERS.length)] : null;
        String door = style == Style.CABIN ? "oak" : style == Style.TRAILER ? "white" : DOORS[rng.nextInt(DOORS.length)];
        return new Palette(wall, trim, roof, floor, foundation, frame, shutter, door);
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

    /** Six-over-six sashes on the older, grander houses; one-over-one on the rest. */
    private String grille() {
        return !pal.frame.equals("alu") && (style == Style.COLONIAL || style == Style.FARMHOUSE || style == Style.MANSION
                || style == Style.SHOTGUN) ? "six" : "one";
    }

    private static String face(String facing) {
        return "[facing=" + facing + "]";
    }

    // ------------------------------------------------------------------ build

    public void build() {
        porch = switch (style) {
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
        entry();
        if (style != Style.TRAILER) {
            chimneys();
            trim();
        }
        weather(height);
        story();
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

    // ------------------------------------------------------------------ windows and doors

    /** The windows, with their shutters and window boxes, and the doors. */
    private void windowsAndDoors(int height) {
        if (interior != null) {
            // centred on the rooms behind them
            doorX = interior.doorX();
            for (int[] w : interior.windows()) {
                window(w[0], w[2] * STOREY + 2, w[1], w[3]);
            }
            door(doorX, z0, "south", pal.door);
            if (interior.backX() >= 0) {
                door(interior.backX(), z1, "north", rng.nextBoolean() ? "white" : "oak");
            }
            if (interior.garageZ() >= 0) {
                door(x1, interior.garageZ(), "west", "oak");
            }
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
                window(x, y, z0, 0);
                if (rng.nextDouble() < 0.8) {
                    window(x, y, z1, 2);
                }
            }
            // sides
            for (int z = z0 + 2; z <= z1 - 2; z += 3) {
                if (rng.nextDouble() < 0.75) {
                    window(x0, y, z, 3);
                }
                if (garageX0 < 0 && rng.nextDouble() < 0.75) {
                    window(x1, y, z, 1);
                }
            }
        }
        door(doorX, z0, "south", pal.door);
        // a back door to the yard
        door((x0 + x1) / 2 + 2 <= x1 - 1 ? (x0 + x1) / 2 + 2 : (x0 + x1) / 2, z1, "north", "white");
    }

    /** What's left in a window, by how the house has fared. */
    private String pane(int y) {
        double r = rng.nextDouble();
        return switch (cond) {
            case SHUT -> r < 0.05 ? "boarded" : r < 0.22 ? "broken" : "glass";
            case LOOTED -> r < 0.06 ? "boarded" : r < 0.58 ? "broken" : r < 0.68 ? "empty" : "glass";
            case BOARDED -> r < (y < STOREY ? 0.92 : 0.6) ? "boarded" : r < 0.95 ? "broken" : "glass";
            case BURNT -> r < 0.5 ? "broken" : "empty";
            case RUIN -> r < 0.45 ? "broken" : r < 0.75 ? "empty" : r < 0.85 ? "boarded" : "glass";
        };
    }

    /**
     * A double-hung window in the wall at (x, y..y+1, z) on wall side s: casing, sill and head
     * outside, shutters either side, perhaps a window box under it and glass on the ground below.
     */
    private void window(int x, int y, int z, int s) {
        String out = SIDES[s];
        String state = pane(y);
        String lo = state, hi = state;
        if (state.equals("broken") && rng.nextDouble() < 0.4) {
            if (rng.nextBoolean()) {
                lo = "glass";
            } else {
                hi = "glass";
            }
        }
        if (state.equals("glass") && rng.nextDouble() < lit) {
            lo = hi = "lit";
        }
        String name = "ext_win_" + pal.frame + "_" + grille() + "_";
        p.set(x, y, z, name + lo + "_lo" + face(out));
        p.set(x, y + 1, z, name + hi + "_hi" + face(out));
        int[] o = STEP[s];
        int[] sv = s == 0 || s == 2 ? new int[] {1, 0} : new int[] {0, 1};
        if (pal.shutter != null && !(cond == Condition.BURNT && rng.nextDouble() < 0.7)) {
            for (int k = -1; k <= 1; k += 2) {
                int sx = x + sv[0] * k + o[0], sz = z + sv[1] * k + o[1];
                if (rng.nextDouble() < (cond == Condition.RUIN ? 0.3 : 0.06)) {
                    continue;  // torn off
                }
                if (p.get(sx, y, sz) == null && p.get(sx, y + 1, sz) == null) {
                    p.set(sx, y, sz, "ext_shutter_" + pal.shutter + "_lo" + face(out));
                    p.set(sx, y + 1, sz, "ext_shutter_" + pal.shutter + "_hi" + face(out));
                }
            }
        }
        double box = switch (style) {
            case SHOTGUN -> 0.35;
            case COLONIAL, BUNGALOW -> 0.3;
            case CABIN -> 0.25;
            case FARMHOUSE, RANCH -> 0.2;
            case MANSION -> 0.15;
            case TRAILER -> 0;
        };
        if (cond != Condition.BURNT && s != 2 && y < floors * STOREY && rng.nextDouble() < (s == 0 ? box : box / 2)
                && p.get(x + o[0], y - 1, z + o[1]) == null) {
            p.set(x + o[0], y - 1, z + o[1], "ext_flowerbox" + face(out));
        }
        if (y == 2 && (lo.equals("broken") || lo.equals("empty")) && rng.nextDouble() < 0.5) {
            p.setIfAir(x + 2 * o[0], 1, z + 2 * o[1], "glass_shards");
        }
    }

    /** A painted door; facing is the side it sits flush with (see city.Doors). */
    private void door(int x, int z, String facing, String paint) {
        String door = "ext_door_" + paint;
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
        p.set(x, 1, z, door + props + ",half=lower]");
        p.set(x, 2, z, door + props + ",half=upper]");
    }

    /** True when the wall at (x, y, z) is plain wall a fitting can hang on (no window, door or gap). */
    private boolean plainWall(int x, int y, int z) {
        String b = p.get(x, y, z);
        return b != null && !b.equals("air") && !b.startsWith("ext_win") && !b.startsWith("ext_door")
                && !b.startsWith("boarded") && !b.startsWith("garage_door");
    }

    /** The front door's lamp, number, mat, and a hood or awning over it when there's no porch. */
    private void entry() {
        int z = z0 - 1;
        boolean on = lit > 0 && rng.nextDouble() < 0.8;
        String lamp = (on ? "ext_sconce_lit" : "ext_sconce") + face("north");
        int first = rng.nextBoolean() ? 1 : -1;
        int lampSide = 0;
        for (int k : new int[] {first, -first}) {
            int x = doorX + k;
            if (x >= x0 && x <= x1 && p.get(x, 2, z) == null && plainWall(x, 2, z0)) {
                p.set(x, 2, z, lamp);
                lampSide = k;
                break;
            }
        }
        boolean pair = style == Style.COLONIAL || style == Style.MANSION;
        int other = doorX - (lampSide == 0 ? first : lampSide);
        boolean numbered = false;
        if (other >= x0 && other <= x1 && p.get(other, 2, z) == null && plainWall(other, 2, z0)) {
            if (pair && lampSide != 0) {
                p.set(other, 2, z, lamp);
            } else {
                p.set(other, 2, z, "ext_number_" + rng.nextInt(6) + face("north"));
                numbered = true;
            }
        }
        if (porch == 0 && style != Style.CABIN) {
            boolean hood = style == Style.COLONIAL || style == Style.MANSION || style == Style.FARMHOUSE
                    || style != Style.TRAILER && rng.nextBoolean();
            if (p.get(doorX, 3, z) == null && (cond != Condition.RUIN || rng.nextBoolean())) {
                p.set(doorX, 3, z, (hood ? "ext_hood" : "ext_awning") + face("north"));
            }
        } else if (!numbered && p.get(doorX, 3, z) == null && plainWall(doorX, 3, z0)) {
            p.set(doorX, 3, z, "ext_number_" + rng.nextInt(6) + face("north"));
        }
        if (rng.nextDouble() < 0.7 && p.get(doorX, 1, z) == null) {
            boolean blood = (cond == Condition.LOOTED || cond == Condition.BOARDED) && rng.nextDouble() < 0.35;
            p.set(doorX, 1, z, (blood ? "ext_doormat_blood" : "ext_doormat") + face(SIDES[rng.nextInt(4)]));
        }
        // a lamp by the back door too
        if (interior != null && interior.backX() >= 0 && rng.nextDouble() < 0.6) {
            int x = interior.backX() + (rng.nextBoolean() ? 1 : -1);
            if (x > x0 && x < x1 && p.get(x, 2, z1 + 1) == null && plainWall(x, 2, z1)) {
                p.set(x, 2, z1 + 1, (on && rng.nextBoolean() ? "ext_sconce_lit" : "ext_sconce") + face("south"));
            }
        }
    }

    // ------------------------------------------------------------------ roof

    /** Sets a block in the roof's frame: u along the ridge, v across it. */
    private void roofSet(int u, int y, int v, String block) {
        if (alongZ) {
            p.set(v, y, u, block);
        } else {
            p.set(u, y, v, block);
        }
    }

    private String roofGet(int u, int y, int v) {
        return alongZ ? p.get(v, y, u) : p.get(u, y, v);
    }

    /** The way out from the wall under eave side 0 (the front, or west) or 1. */
    private String eaveOut(int side) {
        return alongZ ? (side == 0 ? "west" : "east") : (side == 0 ? "north" : "south");
    }

    /** Scatters clusters (of moss, or of holes) over the two slopes. */
    private void clusters(List<int[]> into, int n, double r0, double r1, double frontBias) {
        for (int k = 0; k < n; k++) {
            int side = rng.nextDouble() < frontBias ? 0 : 1;
            int u = ru0 - 1 + rng.nextInt(ru1 - ru0 + 3);
            int row = rng.nextInt(Math.max(1, half));
            double r = r0 + rng.nextDouble() * (r1 - r0);
            into.add(new int[] {side, u, row, (int) Math.round(r * r)});
        }
    }

    private static boolean within(List<int[]> cs, int side, int u, int row) {
        for (int[] c : cs) {
            int du = u - c[1], dr = row - c[2];
            if (c[0] == side && du * du + dr * dr <= c[3]) {
                return true;
            }
        }
        return false;
    }

    /**
     * A gable roof overhanging a block all round: eave courses with their gutters, sloped courses
     * (some mossed over, patched or under a tarp), a capped ridge, decking underneath, and the
     * gable walls closed up to it. ridgeAlongZ turns it to face the street with a gable.
     */
    private void gable(int a0, int a1, int b0, int b1, int height, boolean ridgeAlongZ) {
        alongZ = ridgeAlongZ;
        ru0 = alongZ ? b0 : a0;
        ru1 = alongZ ? b1 : a1;
        rv0 = alongZ ? a0 : b0;
        rv1 = alongZ ? a1 : b1;
        base = height + 1;
        span = rv1 - rv0 + 3;
        half = span / 2;
        String kind = "ext_roof_" + pal.roof;
        // facings: each slope rises toward the ridge
        String[] rise = alongZ ? new String[] {"east", "west"} : new String[] {"south", "north"};
        switch (cond) {
            case SHUT -> clusters(moss, 2, 1.2, 2.6, 0.7);
            case LOOTED, BOARDED -> clusters(moss, 3, 1.4, 2.8, 0.7);
            case RUIN -> clusters(moss, 6, 1.6, 3.2, 0.6);
            default -> clusters(moss, 1, 1.0, 2.0, 0.5);
        }
        if (cond == Condition.BURNT) {
            clusters(holes, 3 + rng.nextInt(3), 1.5, 3.0, 0.5);
        } else if (cond == Condition.RUIN) {
            clusters(holes, 1 + rng.nextInt(2), 1.5, 2.5, 0.5);
        }
        double tarped = switch (cond) {
            case BOARDED -> 0.7;
            case RUIN -> 0.2;
            case LOOTED -> 0.15;
            default -> 0;
        };
        if (half >= 4 && rng.nextDouble() < tarped) {
            int u = ru0 + rng.nextInt(Math.max(1, ru1 - ru0 - 3)), row = 1 + rng.nextInt(half - 3);
            tarp = new int[] {rng.nextInt(2), u, u + 1 + rng.nextInt(3), row, row + 1 + rng.nextInt(2)};
        }
        double gutterGone = switch (cond) {
            case SHUT -> 0.15;
            case LOOTED, BOARDED -> 0.3;
            case BURNT -> 0.8;
            case RUIN -> 0.7;
        };
        for (int side = 0; side < 2; side++) {
            if (rng.nextDouble() < gutterGone) {
                int len = cond == Condition.BURNT ? 6 + rng.nextInt(12) : 2 + rng.nextInt(6);
                int start = ru0 - 1 + rng.nextInt(Math.max(1, ru1 - ru0 + 3));
                lost[side] = new int[] {start, start + len - 1};
            }
        }
        double scatter = switch (cond) {
            case SHUT -> 0.008;
            case LOOTED, BOARDED -> 0.015;
            case BURNT -> 0.08;
            case RUIN -> 0.05;
        };
        for (int i = 0; i < half; i++) {
            int y = base + i;
            boolean crest = i == half - 1 && span % 2 == 0;
            for (int side = 0; side < 2; side++) {
                int v = side == 0 ? rv0 - 1 + i : rv1 + 1 - i;
                String f = face(rise[side]);
                for (int u = ru0 - 1; u <= ru1 + 1; u++) {
                    boolean covered = tarp != null && tarp[0] == side && u >= tarp[1] && u <= tarp[2] && i >= tarp[3]
                            && i <= tarp[4];
                    boolean hole = i > 0 && !covered && (within(holes, side, u, i) || rng.nextDouble() < scatter);
                    String piece;
                    if (i == 0) {
                        piece = kind + (u >= lost[side][0] && u <= lost[side][1] ? "_eave_bare" : "_eave");
                    } else if (crest) {
                        piece = kind + "_crest";
                    } else if (covered) {
                        piece = "ext_roof_tarp";
                    } else if (within(moss, side, u, i)) {
                        piece = kind + "_moss";
                    } else {
                        piece = kind + (rng.nextDouble() < 0.04 ? "_patch" : "");
                    }
                    if (!hole) {
                        roofSet(u, y, v, piece + f);
                    }
                    // the decking under each course, inside the gable walls; burnt through with it
                    if (i > 0 && u > ru0 && u < ru1 && !(hole && cond == Condition.BURNT)) {
                        roofSet(u, y - 1, v, "ext_roof_under" + f);
                    }
                }
            }
            // the gable walls, up under the courses
            for (int v = rv0 + i; v <= rv1 - i; v++) {
                roofSet(ru0, y, v, alongZ ? wall(v, y, ru0) : wall(ru0, y, v));
                roofSet(ru1, y, v, alongZ ? wall(v, y, ru1) : wall(ru1, y, v));
            }
        }
        if (span % 2 == 1) {
            int v = rv0 - 1 + half;
            String beam = "minecraft:stripped_spruce_log[axis=" + (alongZ ? "z" : "x") + "]";
            for (int u = ru0 - 1; u <= ru1 + 1; u++) {
                if (cond == Condition.BURNT && within(holes, 0, u, half - 1)) {
                    continue;
                }
                roofSet(u, base + half, v, kind + "_ridge" + face(alongZ ? "east" : "north"));
                if (u < ru0 || u > ru1) {
                    roofSet(u, base + half - 1, v, "ext_apex");
                } else if (u > ru0 && u < ru1) {
                    roofSet(u, base + half - 1, v, beam);
                }
            }
        }
        // where the gutter has come away, a length of it hangs off one end
        for (int side = 0; side < 2; side++) {
            if (lost[side][0] > lost[side][1] || cond == Condition.BURNT) {
                continue;
            }
            int u = rng.nextBoolean() ? lost[side][0] : lost[side][1];
            int v = side == 0 ? rv0 - 1 : rv1 + 1;
            if (u >= ru0 - 1 && u <= ru1 + 1 && roofGet(u, base - 1, v) == null) {
                roofSet(u, base - 1, v, "ext_gutter_hanging" + face(rise[side]));
            }
        }
    }

    /** The mobile home's flat tin roof, its furnace flue standing up through it. */
    private void flatRoof(int height) {
        String kind = "ext_porch_roof_" + pal.roof;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = rng.nextDouble();
                if (cond == Condition.BURNT && r < 0.35 || cond == Condition.RUIN && r < 0.2) {
                    continue;
                }
                p.set(x, height + 1, z, kind + face("north"));
            }
        }
        if (cond != Condition.BURNT) {
            int fx = x1 - 3, fz = z0 + 2;
            p.set(fx, height + 2, fz, "ext_flue_low");
            p.set(fx, height + 3, fz, "ext_flue_cap");
        }
    }

    // ------------------------------------------------------------------ porch

    /**
     * Boards on the ground, posts with a beam along their tops, railings either side of the steps,
     * a flat roof with a gutter at its edge and a haint-blue ceiling, a light over the door, and
     * whatever was left out: a swing, rockers, a cooler, a pot.
     */
    private void porch(int depth) {
        int a0 = x0, a1 = x1, zf = z0 - depth;
        String roof = "ext_porch_roof_" + pal.roof;
        for (int x = a0; x <= a1; x++) {
            for (int z = zf; z < z0; z++) {
                double r = rng.nextDouble();
                boolean gone = cond == Condition.RUIN && r < 0.1 || cond == Condition.LOOTED && r < 0.02;
                p.set(x, 0, z, gone ? "air" : "ext_porch_boards");
                double q = rng.nextDouble();
                if (!(cond == Condition.BURNT && q < 0.35 || cond == Condition.RUIN && q < 0.15)) {
                    p.set(x, STOREY, z, (z == zf ? roof + "_edge" : roof) + face("north"));
                }
            }
        }
        // posts evenly along the front, none in front of the steps
        int n = Math.max(1, (a1 - a0 + 3) / 4);
        List<Integer> posts = new ArrayList<>();
        for (int k = 0; k <= n; k++) {
            int x = a0 + Math.round((float) k * (a1 - a0) / n);
            if (Math.abs(x - doorX) <= 1 && x != a0 && x != a1) {
                x = x <= doorX ? doorX - 2 : doorX + 2;
            }
            if (x >= a0 && x <= a1 && !posts.contains(x)) {
                posts.add(x);
            }
        }
        for (int x = a0; x <= a1; x++) {
            if (posts.contains(x)) {
                for (int y = 1; y < STOREY; y++) {
                    p.set(x, y, zf, y == 1 ? "ext_post_base" : y == 2 ? "ext_post" : "ext_post_cap[axis=x]");
                }
                continue;
            }
            p.set(x, 3, zf, "ext_beam[axis=x]");
            if (Math.abs(x - doorX) > 1) {
                rail(x, zf, "x");
            }
        }
        // the sides, back to the wall
        for (int z = zf + 1; z < z0; z++) {
            for (int x : new int[] {a0, a1}) {
                p.set(x, 3, z, "ext_beam[axis=z]");
                rail(x, z, "z");
            }
        }
        boolean on = lit > 0 && rng.nextDouble() < 0.8;
        int lz = depth >= 3 ? z0 - 2 : z0 - 1;
        if (p.get(doorX, 3, lz) == null) {
            p.set(doorX, 3, lz, on ? "ext_porch_light_lit" : "ext_porch_light");
        }
        if (cond == Condition.BURNT) {
            return;
        }
        // a swing hung at the far end from the door, a rocker or two at the near end
        boolean right = doorX < (a0 + a1) / 2;
        int far = right ? a1 - 2 : a0 + 1, near = right ? a0 + 1 : a1 - 1;
        if (depth >= 3 && Math.abs(far - doorX) > 2 && Math.abs(far + 1 - doorX) > 2 && rng.nextDouble() < 0.45
                && cond != Condition.RUIN) {
            p.set(far, 1, z0 - 2, "ext_swing_l" + face("north"));
            p.set(far + 1, 1, z0 - 2, "ext_swing_r" + face("north"));
            for (int y = 2; y < STOREY; y++) {
                p.set(far, y, z0 - 2, "ext_swing_chain" + face("north"));
                p.set(far + 1, y, z0 - 2, "ext_swing_chain" + face("north"));
            }
        } else if (rng.nextDouble() < 0.5 && Math.abs(far - doorX) > 1) {
            p.setIfAir(far, 1, zf + 1, "ext_rocker" + face(rng.nextBoolean() ? "north" : right ? "west" : "east"));
        }
        if (Math.abs(near - doorX) > 1 && rng.nextDouble() < 0.6) {
            p.setIfAir(near, 1, zf + 1, "ext_rocker" + face(SIDES[rng.nextInt(4)]));
        }
        if (rng.nextDouble() < 0.25 && doorX + 2 < a1) {
            p.setIfAir(doorX + 2, 1, z0 - 1, "ext_cooler" + face(SIDES[rng.nextInt(4)]));
        }
        if (rng.nextDouble() < 0.5) {
            p.setIfAir(right ? a0 + 1 : a1 - 1, 1, z0 - 1, "minecraft:flower_pot");
        }
    }

    /** A length of porch railing, sometimes kicked in or gone. */
    private void rail(int x, int z, String axis) {
        double r = rng.nextDouble();
        double gone = cond == Condition.RUIN ? 0.25 : cond == Condition.BURNT ? 0.5 : 0.04;
        double broken = cond == Condition.SHUT ? 0.06 : 0.18;
        if (r < gone) {
            return;
        }
        p.set(x, 1, z, (r < gone + broken ? "ext_rail_broken" : "ext_rail") + "[axis=" + axis + "]");
    }

    // ------------------------------------------------------------------ chimneys

    /**
     * A chimney up the outside of the wall behind each fireplace inside, through the eave or the
     * rake and on above the roof to a crown and flue pot; a burnt-out house keeps its chimney
     * standing. Some carry a TV aerial on the ridge.
     */
    private void chimneys() {
        List<int[]> spots = new ArrayList<>();
        if (interior != null) {
            for (int[] h : interior.hearths()) {
                int s = h[3];
                if (s == 0 && porch > 0 || s == 1 && garageX0 >= 0) {
                    continue;
                }
                int cx = s == 1 ? x1 + 1 : s == 3 ? x0 - 1 : h[0];
                int cz = s == 0 ? z0 - 1 : s == 2 ? z1 + 1 : h[1];
                spots.add(new int[] {cx, cz, s, h[2]});
            }
        } else if (rng.nextDouble() < 0.75) {
            // the gable end, the chimney all that's left standing
            int s = alongZ ? 2 : garageX0 >= 0 || rng.nextBoolean() ? 3 : 1;
            int cx = s == 1 ? x1 + 1 : s == 3 ? x0 - 1 : (x0 + x1) / 2;
            int cz = s == 2 ? z1 + 1 : (z0 + z1) / 2;
            spots.add(new int[] {cx, cz, s, 0});
        }
        boolean stone = style == Style.CABIN || style == Style.FARMHOUSE && rng.nextBoolean();
        String brick = pal.wall.startsWith("brick") ? pal.wall : rng.nextDouble() < 0.6 ? "brick_red" : "brick_dark";
        for (int[] c : spots) {
            chimney(c[0], c[1], c[2], c[3], stone, brick);
        }
        if (cond != Condition.BURNT && rng.nextDouble() < (cond == Condition.RUIN ? 0.1 : 0.3)) {
            aerial();
        }
    }

    private void chimney(int cx, int cz, int s, int floor, boolean stone, String brick) {
        int top = base;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = base + half + 1; y > top; y--) {
                    String b = p.get(cx + dx, y, cz + dz);
                    if (b != null && b.startsWith("ext_roof") && !b.startsWith("ext_roof_under")) {
                        top = y;
                        break;
                    }
                }
            }
        }
        top += 1;
        for (int y = 0; y <= top; y++) {
            p.set(cx, y, cz, masonry(y, top, stone, brick));
        }
        p.set(cx, top + 1, cz, "ext_crown");
        // the breast stands three wide up to the firebox's top
        int[] sv = s == 0 || s == 2 ? new int[] {1, 0} : new int[] {0, 1};
        int shoulder = floor * STOREY + 2;
        boolean free = true;
        for (int k = -1; k <= 1; k += 2) {
            for (int y = 1; y <= shoulder; y++) {
                free &= p.get(cx + sv[0] * k, y, cz + sv[1] * k) == null;
            }
        }
        if (free) {
            for (int k = -1; k <= 1; k += 2) {
                for (int y = 0; y <= shoulder; y++) {
                    p.set(cx + sv[0] * k, y, cz + sv[1] * k, masonry(y, top, stone, brick));
                }
            }
        }
    }

    private String masonry(int y, int top, boolean stone, String brick) {
        double r = rng.nextDouble();
        if (stone) {
            return y <= 2 && r < 0.5 || r < 0.25 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone";
        }
        if (y >= top - 1 && r < 0.5 || cond == Condition.BURNT && r < 0.4) {
            return brick + "_soot";
        }
        return y <= 1 && r < 0.3 ? brick + "_moss" : r < 0.15 ? brick + "_stain" : brick;
    }

    /** A TV aerial on a mast stood on the ridge near one end. */
    private void aerial() {
        int u = rng.nextBoolean() ? ru0 + 1 : ru1 - 1;
        int v = rv0 - 1 + half;
        int y = span % 2 == 1 ? base + half + 1 : base + half;
        String below = roofGet(u, y - 1, v);
        if (below == null || !below.startsWith("ext_roof") || roofGet(u, y, v) != null) {
            return;
        }
        roofSet(u, y, v, "ext_mast_foot");
        roofSet(u, y + 1, v, "ext_aerial" + face(rng.nextBoolean() ? "north" : "east"));
    }

    // ------------------------------------------------------------------ trim

    /**
     * Downspouts at the corners under the gutters, a frieze board under the eaves, louvred vents
     * in the gables, the electric meter and the hose reel, and a foundation course all round
     * with crawlspace vents.
     */
    private void trim() {
        int top = base - 1;
        for (int side = 0; side < 2; side++) {
            int v = side == 0 ? rv0 - 1 : rv1 + 1;
            String out = face(eaveOut(side));
            boolean overPorch = !alongZ && side == 0 && porch > 0;
            if (!overPorch) {
                for (int u : new int[] {ru0, ru1}) {
                    if (u >= lost[side][0] && u <= lost[side][1] || rng.nextDouble() < 0.12) {
                        continue;
                    }
                    boolean free = true;
                    for (int y = 1; y <= top; y++) {
                        free &= roofGet(u, y, v) == null;
                    }
                    if (!free) {
                        continue;
                    }
                    // a ruin's pipe has come apart: only the top length still hangs from the gutter
                    int from = cond == Condition.RUIN && rng.nextBoolean() ? top - 1 : 1;
                    for (int y = from; y <= top; y++) {
                        roofSet(u, y, v, (y == top ? "ext_downspout_top" : y == 1 ? "ext_downspout_shoe" : "ext_downspout") + out);
                    }
                }
            }
            for (int u = ru0; u <= ru1; u++) {
                if (roofGet(u, top, v) == null && roofGet(u, top + 1, v) != null) {
                    roofSet(u, top, v, "ext_frieze" + out);
                }
            }
        }
        // the gable vents, or a little attic window in a tall gable
        int vy = base + Math.max(0, half - 3), vc = rv0 - 1 + half;
        for (int end = 0; end < 2; end++) {
            int u = end == 0 ? ru0 - 1 : ru1 + 1, wallU = end == 0 ? ru0 : ru1;
            int s = alongZ ? (end == 0 ? 0 : 2) : (end == 0 ? 3 : 1);
            if (half < 3 || roofGet(u, vy, vc) != null || roofGet(wallU, vy, vc) == null) {
                continue;
            }
            if (half >= 5 && rng.nextDouble() < 0.5 && roofGet(u, vy + 1, vc) == null) {
                int[] at = alongZ ? new int[] {vc, wallU} : new int[] {wallU, vc};
                window(at[0], vy, at[1], s);
            } else {
                roofSet(u, vy, vc, "ext_gable_vent" + face(SIDES[s]));
            }
        }
        // the meter and the hose on a side wall, toward the back
        int ms = garageX0 >= 0 || rng.nextBoolean() ? 3 : 1;
        int mx = ms == 3 ? x0 - 1 : x1 + 1, wx = ms == 3 ? x0 : x1;
        if (rng.nextDouble() < 0.75 && p.get(mx, 2, z1 - 2) == null && plainWall(wx, 2, z1 - 2)) {
            p.set(mx, 2, z1 - 2, "ext_meter" + face(SIDES[ms]));
        }
        if (rng.nextDouble() < 0.45 && p.get(mx, 1, z1 - 4) == null && plainWall(wx, 1, z1 - 4)) {
            p.set(mx, 1, z1 - 4, "ext_hose" + face(SIDES[ms]));
        }
        // the foundation course, broken by the doors and the porch
        int fx1 = garageX1 >= 0 ? garageX1 : x1;
        for (int s = 0; s < 4; s++) {
            if (s == 0 && porch > 0) {
                continue;
            }
            int n = s == 0 || s == 2 ? fx1 - x0 + 1 : z1 - z0 + 1;
            for (int k = 0; k < n; k++) {
                int x = s == 0 || s == 2 ? x0 + k : s == 1 ? fx1 : x0;
                int z = s == 1 || s == 3 ? z0 + k : s == 0 ? z0 : z1;
                int ox = x + STEP[s][0], oz = z + STEP[s][1];
                if (!plainWall(x, 1, z) || p.get(ox, 1, oz) != null) {
                    continue;
                }
                p.set(ox, 1, oz, (k % 4 == 2 && style != Style.CABIN ? "ext_plinth_vent" : "ext_plinth") + face(SIDES[s]));
            }
        }
    }

    // ------------------------------------------------------------------ weathering

    /** Ivy up the walls, a wall come down, dead shrubs along the front. */
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
        // the foundation planting, long dead
        if (style != Style.TRAILER) {
            int z = porch > 0 ? z0 - porch - 1 : z0 - 2;
            for (int x = x0; x <= x1; x++) {
                if (Math.abs(x - doorX) > 1 && rng.nextDouble() < 0.3) {
                    p.setIfAir(x, 1, z, "ext_shrub_dead");
                }
            }
        }
        if (rng.nextDouble() < 0.25) {
            p.setIfAir(doorX, 1, z0 - 1, "blood_splat");
        }
    }

    /**
     * What happened here, on the front wall: the Guard's quarantine notice on a shut house, the
     * search team's X by the door of a looted or burnt one, HELP sprayed across a boarded-up
     * one, a bloody hand by the door.
     */
    private void story() {
        int z = z0 - 1;
        double notice = switch (cond) {
            case SHUT -> 0.35;
            case BOARDED -> 0.3;
            default -> 0.05;
        };
        double mark = switch (cond) {
            case LOOTED -> 0.35;
            case BURNT -> 0.45;
            case RUIN -> 0.25;
            default -> 0.05;
        };
        if (rng.nextDouble() < notice) {
            hangByDoor(2, "ext_notice" + face("north"));
        }
        if (rng.nextDouble() < mark) {
            hangByDoor(3, "home_x_code" + face("south"));
        }
        if ((cond == Condition.LOOTED || cond == Condition.BOARDED) && rng.nextDouble() < 0.25) {
            hangByDoor(2, "home_blood_hand" + face("south"));
        }
        String word = cond == Condition.BOARDED ? "help" : cond == Condition.RUIN || cond == Condition.LOOTED ? "dead" : null;
        if (word != null && rng.nextDouble() < (cond == Condition.BOARDED ? 0.55 : 0.2)) {
            // read left to right from the street: the decal faces the wall, its parts run west
            int n = Math.max(1, x1 - x0 - 2), off = rng.nextInt(n);
            for (int k = 0; k < n; k++) {
                int a = x0 + 2 + (off + k) % n, b = a - 1;
                if (a < x1 && Math.abs(a - doorX) > 1 && Math.abs(b - doorX) > 1 && plainWall(a, 2, z0) && plainWall(b, 2, z0)
                        && p.get(a, 2, z) == null && p.get(b, 2, z) == null) {
                    p.set(a, 2, z, "home_spray_" + word + "_0" + face("south"));
                    p.set(b, 2, z, "home_spray_" + word + "_1" + face("south"));
                    break;
                }
            }
        }
    }

    /** Hangs a decal or sign on the front wall near the door, at the given height. */
    private void hangByDoor(int y, String block) {
        int z = z0 - 1;
        for (int k : new int[] {1, -1, 2, -2, 3, -3}) {
            int x = doorX + k;
            if (x > x0 && x < x1 && p.get(x, y, z) == null && plainWall(x, y, z0)) {
                p.set(x, y, z, block);
                return;
            }
        }
    }
}
