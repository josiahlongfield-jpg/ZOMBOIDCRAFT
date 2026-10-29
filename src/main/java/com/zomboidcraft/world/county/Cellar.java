package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The basement under the odd {@link House}: painted block walls going damp at the foot (old
 * fieldstone under a farmhouse, a cabin's root cellar dug out of the earth), the joists of the
 * house overhead with a bare bulb or two, the furnace and water heater, and whatever the family
 * kept down there. Most kept food, then first aid; some did the laundry or had a workshop, a rec
 * room or (under a mansion) a wine cellar; a very few stocked guns and ammunition against the
 * end of the world. Then the house's story reaches down here too: survivors who held out in the
 * dark, looters who turned it over, a basement standing in a foot of water.
 *
 * Works in the house's frame below its ground floor: the floor at y = -4, three rows of room up
 * to the joists under the house's floor at y = 0. The way down ({@link Interior#cellar}) is a
 * flight under the house's stairs or a ladder below a trapdoor; the room is laid out round it.
 * Furniture keeps to the walls, and a piece goes in only if every free cell can still be reached
 * from the foot of the way down.
 */
final class Cellar {
    enum Use { FOOD, MEDICINE, LAUNDRY, WORKSHOP, DEN, WINE, ARMOURY }

    private static final int FLOOR = -4;
    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    // what stands in each cell of the room
    private static final byte FREE = 0, TAKEN = 1, WAY = 2, FRONT = 3;

    private final Placer p;
    private final SplittableRandom rng;
    private final House.Style style;
    private final House.Condition cond;
    private final double lit;
    private final int[] way;
    /** The house's walls: the basement's own walls stand under them or inside. */
    private final int hx0, hx1, hz0, hz1;
    /** The room inside its walls. */
    private int bx0, bx1, bz0, bz1;
    private int size;
    private Use use;
    private String wall, damp, floor;
    private boolean wet, flooded;
    private byte[][] at;
    /** The piece standing on the floor in each cell (its name, no state), for what goes on top. */
    private String[][] what;
    /** Cells whose top row stays clear of joists (the stairwell, the ladder, ducts and the like). */
    private boolean[][] open;
    /** Where you arrive at the bottom of the way down. */
    private int ax, az;

    /** How often a house of this kind has a basement. */
    static double odds(House.Style style) {
        return switch (style) {
            case MANSION -> 0.4;
            case COLONIAL, FARMHOUSE -> 0.14;
            case RANCH, BUNGALOW -> 0.1;
            case SHOTGUN, CABIN -> 0.08;
            case TRAILER -> 0;
        };
    }

    /** way is {@link Interior#cellar}'s; x0..x1, z0..z1 the house's outer walls. */
    Cellar(Placer p, SplittableRandom rng, House.Style style, House.Condition cond, double lit, int[] way,
           int x0, int x1, int z0, int z1) {
        this.p = p;
        this.rng = rng;
        this.style = style;
        this.cond = cond;
        this.lit = lit;
        this.way = way;
        this.hx0 = x0;
        this.hx1 = x1;
        this.hz0 = z0;
        this.hz1 = z1;
    }

    String describe() {
        return "cellar " + use.name().toLowerCase() + " " + (bx1 - bx0 + 1) + "x" + (bz1 - bz0 + 1)
                + " " + new String[] {"small", "half", "full"}[size] + (flooded ? " flooded" : "") + " at " + bx0 + "," + bz0
                + " way " + way[1] + "," + way[2] + "\n";
    }

    void build() {
        materials();
        use = pickUse();
        rect();
        shell();
        wayDown();
        utilities();
        switch (use) {
            case FOOD -> food();
            case MEDICINE -> medicine();
            case LAUNDRY -> laundry();
            case WORKSHOP -> workshop();
            case DEN -> den();
            case WINE -> wine();
            case ARMOURY -> armoury();
        }
        story();
        islands();
        junk();
        surfaces();
        ceiling();
        if (flooded) {
            flood();
        }
    }

    // ------------------------------------------------------------------ the room

    private void materials() {
        double r = rng.nextDouble();
        boolean stone = style == House.Style.CABIN || (style == House.Style.FARMHOUSE || style == House.Style.COLONIAL) && r < 0.5;
        if (stone) {
            wall = "fm_fieldstone";
            damp = "fm_fieldstone_moss";
            floor = style == House.Style.CABIN || rng.nextBoolean() ? "minecraft:packed_mud" : "floor_concrete";
        } else if (style == House.Style.MANSION && r < 0.5) {
            wall = "ch_ashlar";
            damp = "ch_ashlar_moss";
            floor = "floor_concrete";
        } else {
            wall = "cellar_wall";
            damp = "cellar_wall_damp";
            floor = "floor_concrete";
        }
        wet = cond == House.Condition.RUIN || rng.nextDouble() < 0.3;
        flooded = cond == House.Condition.RUIN && rng.nextDouble() < 0.5;
    }

    private Use pickUse() {
        double r = rng.nextDouble();
        Use u = r < 0.30 ? Use.FOOD : r < 0.52 ? Use.MEDICINE : r < 0.70 ? Use.LAUNDRY : r < 0.84 ? Use.WORKSHOP
                : r < 0.94 ? Use.DEN : Use.ARMOURY;
        if (u == Use.DEN && style == House.Style.MANSION && rng.nextDouble() < 0.6) {
            u = Use.WINE;
        }
        if (style == House.Style.CABIN && (u == Use.LAUNDRY || u == Use.DEN)) {
            u = Use.FOOD;  // a root cellar
        }
        return u;
    }

    /** The room's rectangle: a small cellar round the way down, half the house, or all of it. */
    private void rect() {
        int lx0 = hx0 + 1, lx1 = hx1 - 1, lz0 = hz0 + 1, lz1 = hz1 - 1;
        double r = rng.nextDouble();
        size = style == House.Style.MANSION ? (r < 0.15 ? 0 : r < 0.55 ? 1 : 2)
                : style == House.Style.CABIN ? (r < 0.8 ? 0 : 1)
                : r < 0.45 ? 0 : r < 0.8 ? 1 : 2;
        // the cells the room must hold: the way down and the floor at its foot
        int[] need;
        boolean stairs = way[0] == 0;
        int wx = way[1], wz = way[2], s = way[3];
        if (stairs) {
            int in = -STEP[s][0];  // across the flight, toward the hall
            need = new int[] {Math.min(wx, wx + in), wz - 1, Math.max(wx, wx + in), wz + 3};
        } else {
            int ix = wx - STEP[s][0], iz = wz - STEP[s][1];
            need = new int[] {Math.min(wx, ix), Math.min(wz, iz), Math.max(wx, ix), Math.max(wz, iz)};
        }
        int x0, x1, z0, z1;
        if (size == 2) {
            x0 = lx0;
            x1 = lx1;
            z0 = lz0;
            z1 = lz1;
        } else if (size == 1) {
            x0 = lx0;
            x1 = lx1;
            z0 = lz0;
            z1 = lz1;
            if (lx1 - lx0 >= lz1 - lz0) {
                int mid = (lx0 + lx1) / 2;
                if ((need[0] + need[2]) / 2 <= mid) {
                    x1 = mid;
                } else {
                    x0 = mid + 1;
                }
            } else {
                int mid = (lz0 + lz1) / 2;
                if ((need[1] + need[3]) / 2 <= mid) {
                    z1 = mid;
                } else {
                    z0 = mid + 1;
                }
            }
        } else {
            int w = 3 + rng.nextInt(3), d = 4 + rng.nextInt(3);
            if (stairs) {
                // the flight along one wall, the room opening beside it
                int in = -STEP[s][0];
                x0 = in > 0 ? wx : wx - w;
                x1 = in > 0 ? wx + w : wx;
                z0 = wz - 1 - rng.nextInt(2);
                z1 = Math.max(wz + 3, z0 + d - 1);
            } else {
                // back to the wall the ladder goes down, out into the room from there
                int[] f = STEP[(s + 2) & 3];
                int ax0 = wx, az0 = wz, ax1 = wx + f[0] * (d - 1), az1 = wz + f[1] * (d - 1);
                int half = w / 2;
                if (f[0] == 0) {
                    ax0 -= half;
                    ax1 += w - 1 - half;
                } else {
                    az0 -= half;
                    az1 += w - 1 - half;
                }
                x0 = Math.min(ax0, ax1);
                x1 = Math.max(ax0, ax1);
                z0 = Math.min(az0, az1);
                z1 = Math.max(az0, az1);
            }
        }
        bx0 = Math.max(lx0, Math.min(x0, need[0]));
        bx1 = Math.min(lx1, Math.max(x1, need[2]));
        bz0 = Math.max(lz0, Math.min(z0, need[1]));
        bz1 = Math.min(lz1, Math.max(z1, need[3]));
        at = new byte[bx1 - bx0 + 1][bz1 - bz0 + 1];
        what = new String[bx1 - bx0 + 1][bz1 - bz0 + 1];
        open = new boolean[bx1 - bx0 + 1][bz1 - bz0 + 1];
    }

    /** The floor, the walls round the room, the room itself dug out. */
    private void shell() {
        for (int x = bx0 - 1; x <= bx1 + 1; x++) {
            for (int z = bz0 - 1; z <= bz1 + 1; z++) {
                p.set(x, FLOOR, z, floorBlock());
                boolean ring = !in(x, z);
                for (int y = FLOOR + 1; y < 0; y++) {
                    p.set(x, y, z, ring ? (wet && y == FLOOR + 1 || cond == House.Condition.RUIN && y == FLOOR + 2
                            && rng.nextDouble() < 0.4 ? damp : wall) : "air");
                }
            }
        }
    }

    private String floorBlock() {
        if (!floor.equals("floor_concrete")) {
            return floor;
        }
        double r = rng.nextDouble();
        if (wet && r < 0.25) {
            return "floor_concrete_moss";
        }
        return r < 0.05 ? "floor_concrete_crack" : "floor_concrete";
    }

    /** The flight down under the house's stairs, or the ladder under the hatch. */
    private void wayDown() {
        int wx = way[1], wz = way[2], s = way[3];
        if (way[0] == 0) {
            // steps down northward under the flight up; solid beneath each, like the flight above
            for (int i = 0; i < 4; i++) {
                int z = wz + i;
                if (i < 3) {
                    p.set(wx, -3 + i, z, "home_stairs_oak[facing=south]");
                }
                for (int y = FLOOR + 1; y < -3 + i; y++) {
                    p.set(wx, y, z, wall);
                }
                mark(wx, z, TAKEN);
                setOpen(wx, z);
            }
            ax = wx;
            az = wz - 1;
            mark(ax, az, WAY);
            mark(ax, az - 1, WAY);
            mark(ax - STEP[s][0], az, WAY);
        } else {
            String facing = DIRS[(s + 2) & 3];
            for (int y = -3; y <= -1; y++) {
                p.set(wx, y, wz, "fm_ladder[facing=" + facing + "]");
            }
            int bx = wx + STEP[s][0], bz = wz + STEP[s][1];
            if (in(bx, bz)) {
                // the ladder's foot is out in the room: a block pillar to fix it to
                for (int y = FLOOR + 1; y < 0; y++) {
                    p.set(bx, y, bz, wall);
                }
                mark(bx, bz, TAKEN);
                setOpen(bx, bz);
            }
            setOpen(wx, wz);
            mark(wx, wz, WAY);
            ax = wx - STEP[s][0];
            az = wz - STEP[s][1];
            mark(ax, az, WAY);
        }
    }

    /** The furnace with its ducts and the water heater beside it; not in a cabin's root cellar. */
    private void utilities() {
        if (style == House.Style.CABIN || use != Use.LAUNDRY && rng.nextDouble() < 0.2) {
            return;
        }
        int[] c = against("cellar_furnace", "cellar_furnace_top");
        if (c != null) {
            p.set(c[0], -1, c[1], "cellar_duct_up");
            setOpen(c[0], c[1]);
            // a supply duct out across the room under the joists
            int fs = c[2];
            String axis = fs == 0 || fs == 2 ? "z" : "x";
            for (int i = 1, n = 2 + rng.nextInt(4); i <= n; i++) {
                int x = c[0] + STEP[fs][0] * i, z = c[1] + STEP[fs][1] * i;
                if (!in(x, z) || open[x - bx0][z - bz0]) {
                    break;
                }
                p.set(x, -1, z, "cellar_duct[axis=" + axis + "]");
                setOpen(x, z);
            }
            int[] sv = side(fs);
            for (int k : new int[] {1, -1}) {
                int x = c[0] + sv[0] * k, z = c[1] + sv[1] * k;
                if (place(x, z, (fs + 2) & 3, "home_water_heater", "home_water_heater_top")) {
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ what it was for

    private void food() {
        int n = 2 + size * 2 + rng.nextInt(2);
        for (int i = 0; i < n; i++) {
            String shelf = rng.nextDouble() < 0.55 ? "cellar_shelf_preserves" : "cellar_shelf_cans";
            tallOrLow(shelf);
        }
        against("cellar_jugs");
        if (style != House.Style.CABIN && rng.nextDouble() < 0.5) {
            against("home_freezer");
        }
        for (int i = 0, k = rng.nextInt(2 + size); i < k; i++) {
            against("cellar_barrel[loot=basement_food]");
        }
    }

    private void medicine() {
        int n = 1 + size + rng.nextInt(2);
        for (int i = 0; i < n; i++) {
            tallOrLow("cellar_shelf_medical");
        }
        tallOrLow("cellar_shelf_cans");
        against("cellar_jugs");
        if (rng.nextBoolean()) {
            against("cellar_jugs");
        }
    }

    private void laundry() {
        row("store_washer", "store_dryer");
        against("home_laundry");
        against("home_ironing_board");
        against("home_garage_shelf");
        if (rng.nextBoolean()) {
            tallOrLow("cellar_shelf_cans");
        }
        scatter("home_clothes_pile", 1 + rng.nextInt(2));
        // the folding table out in the room, a basket by it
        middle("home_table");
        if (rng.nextBoolean()) {
            middle("home_laundry");
        }
    }

    private void workshop() {
        against("home_workbench");
        against("home_tool_chest");
        for (int i = 0, n = 1 + size; i < n; i++) {
            tallOrLow("home_garage_shelf");
        }
        if (rng.nextBoolean()) {
            against("home_freezer");
        }
    }

    private void den() {
        // the old sofa from upstairs, the TV, the chest freezer, the family's books
        String colour = new String[] {"floral", "brown", "blue"}[rng.nextInt(3)];
        int[] sofa = row("home_sofa_" + colour + "_l", "home_sofa_" + colour + "_m", "home_sofa_" + colour + "_r");
        if (sofa == null) {
            against("home_armchair_" + colour);
        }
        boolean smashed = (cond == House.Condition.LOOTED || cond == House.Condition.RUIN) && rng.nextBoolean();
        against(smashed ? "home_tv_smashed" : "home_tv");
        against("home_armchair_" + colour);
        against("home_bookcase", "home_bookcase_top");
        against("home_freezer");
        if (rng.nextBoolean()) {
            against("home_table");
        }
        // a rug out in the middle, the coffee table on it
        int[] m = middle("home_coffee_table");
        if (m != null) {
            rug(m[0] - 1, m[1] - 1, m[0] + 1, m[1] + 1, rng.nextBoolean() ? "red" : "blue");
        }
    }

    /** A rug over a rectangle, its border round the edge, under whatever already stands there. */
    private void rug(int ax, int az, int bx, int bz, String colour) {
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                if (!in(x, z) || at[x - bx0][z - bz0] == TAKEN || at[x - bx0][z - bz0] == WAY) {
                    continue;
                }
                boolean n = z == az, so = z == bz, w = x == ax, e = x == bx;
                String block;
                if ((n || so) && (w || e)) {
                    block = "home_rug_" + colour + "_corner[facing=" + (n ? (w ? "north" : "east") : (e ? "south" : "west")) + "]";
                } else if (n || so || w || e) {
                    block = "home_rug_" + colour + "_edge[facing=" + (n ? "north" : so ? "south" : w ? "west" : "east") + "]";
                } else {
                    block = "home_rug_" + colour;
                }
                p.set(x, -3, z, block);
                at[x - bx0][z - bz0] = FRONT;
            }
        }
    }

    private void wine() {
        for (int i = 0, n = 4 + size * 3 + rng.nextInt(3); i < n; i++) {
            if (rng.nextDouble() < 0.7) {
                against("cellar_wine_rack", "cellar_wine_rack");
            } else {
                against("cellar_wine_rack");
            }
        }
        for (int i = 0, n = 1 + rng.nextInt(2); i < n; i++) {
            against("cellar_barrel_side[loot=liquor]");  // on its side, the tap toward the room
        }
    }

    /** A prepper's armoury: the safe, the ammo shelves, the bench he loaded at, his supplies. */
    private void armoury() {
        against("cellar_safe", "cellar_safe_top");
        for (int i = 0, n = 2 + rng.nextInt(2); i < n; i++) {
            tallOrLow("cellar_shelf_ammo");
        }
        against("home_gun_cabinet", "home_gun_cabinet_top");
        against(withLoot("home_workbench", "basement_supplies"));
        for (int i = 0, n = 1 + rng.nextInt(2); i < n; i++) {
            against("cellar_barrel[loot=basement_supplies]");
        }
        tallOrLow("cellar_shelf_cans");
        against("cellar_jugs");
        against("cellar_jugs");
    }

    // ------------------------------------------------------------------ filling it up

    /** Free-standing shelving down the middle of a big room, aisles two wide round it. */
    private void islands() {
        if (use == Use.DEN || use == Use.LAUNDRY) {
            return;
        }
        int w = bx1 - bx0 + 1, d = bz1 - bz0 + 1;
        boolean alongZ = d >= w;
        int across = alongZ ? w : d, len = alongZ ? d : w;
        if (across < 6 || len < 6) {
            return;
        }
        int lines = across >= 8 ? 2 : 1;
        int c0 = (alongZ ? bx0 : bz0) + (across - lines) / 2;
        int a0 = (alongZ ? bz0 : bx0) + 2, a1 = (alongZ ? bz1 : bx1) - 2;
        int lone = rng.nextBoolean() ? 1 : -1;
        for (int li = 0; li < lines; li++) {
            // back to back down the middle; a single line backs onto one aisle
            int back = lines == 2 ? (li == 0 ? 1 : -1) : lone;
            int s = alongZ ? (back > 0 ? 1 : 3) : (back > 0 ? 2 : 0);
            for (int a = a0; a <= a1; a++) {
                if (len > 12 && (a - a0) % 6 == 5) {
                    continue;  // a gap to cut through
                }
                int c = c0 + li;
                place(alongZ ? c : a, alongZ ? a : c, s, islandPiece());
            }
        }
    }

    private String[] islandPiece() {
        boolean two = rng.nextDouble() < 0.6;
        String b = switch (use) {
            case FOOD -> rng.nextDouble() < 0.55 ? "cellar_shelf_preserves" : "cellar_shelf_cans";
            case MEDICINE -> rng.nextDouble() < 0.6 ? "cellar_shelf_medical" : "cellar_shelf_cans";
            case ARMOURY -> rng.nextDouble() < 0.5 ? "cellar_shelf_ammo" : "cellar_shelf_cans";
            case WINE -> "cellar_wine_rack";
            default -> "home_garage_shelf";
        };
        return two ? new String[] {loot(b), loot(b)} : new String[] {loot(b)};
    }

    /**
     * What every basement fills up with: boxes stacked against the walls, the old furniture from
     * upstairs, a broken fan, the kids' crib nobody threw out, bags of rubbish, jugs of water.
     */
    private void junk() {
        int wallCells = 0;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                if (at[x - bx0][z - bz0] == FREE && (x == bx0 || x == bx1 || z == bz0 || z == bz1)) {
                    wallCells++;
                }
            }
        }
        int want = (int) (wallCells * (size == 0 ? 0.35 : 0.45));
        for (int i = 0, tries = 0; i < want && tries < want * 3 + 4; tries++) {
            if (against(junkPiece()) != null) {
                i++;
            }
        }
    }

    private String[] junkPiece() {
        double r = rng.nextDouble();
        if (r < 0.32) {
            return rng.nextDouble() < 0.45 ? new String[] {loot("home_boxes"), loot("home_boxes")} : new String[] {loot("home_boxes")};
        }
        String[] one = {"home_bin_bags", "cellar_jugs", "cellar_barrel[loot=home_boxes]", "home_chair",
                "home_armchair_brown", "home_dresser", "home_table", "home_garage_shelf", "home_garage_shelf", "home_vacuum",
                "home_fan", "home_ironing_board", "home_tool_chest", "home_trash_can", "home_toy_chest", "home_crib",
                "home_freezer", "home_bookcase"};
        String b = one[rng.nextInt(one.length)];
        if (style == House.Style.CABIN && !b.startsWith("home_boxes") && rng.nextBoolean()) {
            b = rng.nextBoolean() ? "cellar_barrel[loot=basement_food]" : "cellar_jugs";
        }
        return new String[] {loot(b)};
    }

    /** Things left on the workbench, the tables, the chest freezer and the dressers. */
    private void surfaces() {
        boolean radio = false;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                String on = what[x - bx0][z - bz0];
                if (on == null || on.isEmpty()) {
                    continue;
                }
                String put = switch (on) {
                    case "home_workbench", "home_tool_chest" -> rng.nextDouble() < 0.6
                            ? pick("home_cans", "home_bottles", "home_jars", radio ? "home_jars" : "home_radio") : null;
                    case "home_table", "home_freezer" -> rng.nextDouble() < 0.5
                            ? pick("home_cans_low", "home_bottles_low", "home_books_stack_low", radio ? "home_cans_low" : "home_radio_low")
                            : null;
                    case "home_dresser" -> rng.nextDouble() < 0.4 ? pick("home_books_stack", "home_lamp_table", "home_bottles") : null;
                    default -> null;
                };
                if (put != null) {
                    p.set(x, -2, z, put + face(facingAt(x, z)));
                    radio |= put.startsWith("home_radio");
                }
            }
        }
    }

    /** The way a wall piece faces: away from the wall behind it. */
    private int facingAt(int x, int z) {
        for (int s = 0; s < 4; s++) {
            if (!in(x + STEP[s][0], z + STEP[s][1])) {
                return (s + 2) & 3;
            }
        }
        return rng.nextInt(4);
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ overhead and the story

    /** Joists under the house's floor, with a bulb or two; cobwebs in the corners. */
    private void ceiling() {
        String axis = bx1 - bx0 <= bz1 - bz0 ? "x" : "z";
        int cells = (bx1 - bx0 + 1) * (bz1 - bz0 + 1);
        List<int[]> bulbs = new ArrayList<>();
        int n = Math.max(1, cells / 30);
        for (int tries = 0; tries < 40 && bulbs.size() < n; tries++) {
            int x = bx0 + 1 + rng.nextInt(Math.max(1, bx1 - bx0 - 1)), z = bz0 + 1 + rng.nextInt(Math.max(1, bz1 - bz0 - 1));
            if (in(x, z) && !open[x - bx0][z - bz0] && at[x - bx0][z - bz0] != TAKEN) {
                bulbs.add(new int[] {x, z});
                setOpen(x, z);
            }
        }
        for (int[] b : bulbs) {
            boolean on = lit > 0 && rng.nextDouble() < lit + 0.3;
            p.set(b[0], -1, b[1], (on ? "cellar_bulb_lit" : "cellar_bulb") + "[axis=" + axis + "]");
        }
        double webs = cond == House.Condition.SHUT ? 0.7 : cond == House.Condition.BOARDED ? 0.2 : 0.5;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                if (open[x - bx0][z - bz0]) {
                    continue;
                }
                boolean corner = (x == bx0 || x == bx1) && (z == bz0 || z == bz1);
                p.set(x, -1, z, "cellar_joists[axis=" + axis + "]");
                if (corner && rng.nextDouble() < webs) {
                    // a web strung in the corner under the joists, on the north or south wall
                    p.setIfAir(x, -2, z, "bunk_cobweb[facing=" + (z == bz0 ? "south" : "north") + "]");
                }
            }
        }
    }

    private void story() {
        switch (cond) {
            case BOARDED -> {
                // someone held out down here: a mattress, candles, the bags of their rubbish, a note
                against("home_mattress_floor");
                scatter("home_candles", 1 + rng.nextInt(2));
                against("home_bin_bags");
                against("cellar_jugs");
                note("home_note_basement");
            }
            case LOOTED -> {
                scatter("paper", 2 + rng.nextInt(3));
                scatter("glass_shards", rng.nextInt(3));
                if (rng.nextDouble() < 0.3) {
                    scatter("blood_splat", 1);
                }
            }
            case RUIN -> {
                if (!flooded) {
                    scatter("home_stain_brown", 1 + rng.nextInt(3));
                    scatter("paper", 1 + rng.nextInt(2));
                }
            }
            default -> {
                if (rng.nextDouble() < 0.15) {
                    scatter(rng.nextBoolean() ? "home_stain_dark" : "home_stain_brown", 1);
                }
            }
        }
        if (cond != House.Condition.BOARDED && !flooded && rng.nextDouble() < 0.12) {
            body();
        }
    }

    /** A foot of standing water over the floor. */
    private void flood() {
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                boolean ladder = way[0] == 1 && x == way[1] && z == way[2];
                if (at[x - bx0][z - bz0] != TAKEN && !ladder) {
                    p.set(x, -3, z, "minecraft:water");
                }
            }
        }
    }

    /** Whoever hid down here and never came back up. */
    private void body() {
        for (int tries = 0; tries < 20; tries++) {
            int x = bx0 + rng.nextInt(bx1 - bx0 + 1), z = bz0 + rng.nextInt(bz1 - bz0 + 1);
            int fc = rng.nextInt(4);
            int fx = x + STEP[fc][0], fz = z + STEP[fc][1];
            if (in(x, z) && in(fx, fz) && at[x - bx0][z - bz0] == FREE && at[fx - bx0][fz - bz0] == FREE) {
                p.set(x, -3, z, "home_corpse_head" + face(fc));
                p.set(fx, -3, fz, "home_corpse_foot" + face(fc));
                mark(x, z, FRONT);
                mark(fx, fz, FRONT);
                return;
            }
        }
    }

    /** A note pinned to a wall where nothing stands in front of it. */
    private void note(String name) {
        for (int tries = 0; tries < 30; tries++) {
            int x = bx0 + rng.nextInt(bx1 - bx0 + 1), z = bz0 + rng.nextInt(bz1 - bz0 + 1);
            for (int s = 0; s < 4; s++) {
                byte a = at[x - bx0][z - bz0];
                if (!in(x + STEP[s][0], z + STEP[s][1]) && (a == FREE || a == FRONT)) {
                    p.set(x, -2, z, name + face(s));
                    return;
                }
            }
        }
    }

    /** Floor litter on free floor. */
    private void scatter(String name, int n) {
        for (int i = 0; i < n; i++) {
            for (int tries = 0; tries < 10; tries++) {
                int x = bx0 + rng.nextInt(bx1 - bx0 + 1), z = bz0 + rng.nextInt(bz1 - bz0 + 1);
                byte a = at[x - bx0][z - bz0];
                if (a == FREE || a == FRONT) {
                    p.set(x, -3, z, Interior.PLAIN.contains(name) ? name : name + face(rng.nextInt(4)));
                    at[x - bx0][z - bz0] = FRONT;
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------ placing

    /** A shelf unit two high, or now and then one. */
    private void tallOrLow(String shelf) {
        if (rng.nextDouble() < 0.6) {
            against(loot(shelf), loot(shelf));
        } else {
            against(loot(shelf));
        }
    }

    /** A looted basement's shelves were mostly stripped. */
    private String loot(String block) {
        if (cond == House.Condition.LOOTED && rng.nextDouble() < 0.6) {
            return withLoot(block, "scraps");
        }
        return block;
    }

    private static String withLoot(String block, String table) {
        int open = block.indexOf('[');
        return open < 0 ? block + "[loot=" + table + "]" : block.substring(0, block.length() - 1) + ",loot=" + table + "]";
    }

    /** A piece out in the room, two cells clear of the walls; {x, z} or null. */
    private int[] middle(String block) {
        List<int[]> spots = new ArrayList<>();
        for (int x = bx0 + 2; x <= bx1 - 2; x++) {
            for (int z = bz0 + 2; z <= bz1 - 2; z++) {
                if (at[x - bx0][z - bz0] == FREE) {
                    spots.add(new int[] {x, z});
                }
            }
        }
        while (!spots.isEmpty()) {
            int[] c = spots.remove(rng.nextInt(spots.size()));
            int s = rng.nextInt(4);
            if (place(c[0], c[1], s, block)) {
                return c;
            }
        }
        return null;
    }

    /** A piece (a stack of blocks from the floor up) against any free stretch of wall; {x, z, facing} or null. */
    private int[] against(String... stack) {
        List<int[]> spots = new ArrayList<>();
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                for (int s = 0; s < 4; s++) {
                    if (!in(x + STEP[s][0], z + STEP[s][1]) && at[x - bx0][z - bz0] == FREE) {
                        spots.add(new int[] {x, z, s});
                    }
                }
            }
        }
        while (!spots.isEmpty()) {
            int[] c = spots.remove(rng.nextInt(spots.size()));
            if (place(c[0], c[1], c[2], stack)) {
                return new int[] {c[0], c[1], (c[2] + 2) & 3};
            }
        }
        return null;
    }

    /** Pieces side by side along one wall, in the order a sofa's run (left to right as you face it). */
    private int[] row(String... pieces) {
        int s0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int s = (s0 + k) & 3, fs = (s + 2) & 3;
            int[] sv = side(fs);
            List<int[]> starts = new ArrayList<>();
            for (int x = bx0; x <= bx1; x++) {
                for (int z = bz0; z <= bz1; z++) {
                    boolean fits = true;
                    for (int i = 0; i < pieces.length && fits; i++) {
                        int cx = x + sv[0] * i, cz = z + sv[1] * i;
                        int fx = cx - STEP[s][0], fz = cz - STEP[s][1];
                        fits = in(cx, cz) && !in(cx + STEP[s][0], cz + STEP[s][1]) && at[cx - bx0][cz - bz0] == FREE
                                && in(fx, fz) && at[fx - bx0][fz - bz0] != TAKEN;
                    }
                    if (fits) {
                        starts.add(new int[] {x, z});
                    }
                }
            }
            while (!starts.isEmpty()) {
                int[] c = starts.remove(rng.nextInt(starts.size()));
                for (int i = 0; i < pieces.length; i++) {
                    at[c[0] + sv[0] * i - bx0][c[1] + sv[1] * i - bz0] = TAKEN;
                }
                if (connected()) {
                    for (int i = 0; i < pieces.length; i++) {
                        int x = c[0] + sv[0] * i, z = c[1] + sv[1] * i;
                        p.set(x, -3, z, faced(pieces[i], fs));
                        what[x - bx0][z - bz0] = base(pieces[i]);
                        mark(x - STEP[s][0], z - STEP[s][1], FRONT);
                    }
                    return new int[] {c[0], c[1], fs};
                }
                for (int i = 0; i < pieces.length; i++) {
                    at[c[0] + sv[0] * i - bx0][c[1] + sv[1] * i - bz0] = FREE;
                }
            }
        }
        return null;
    }

    /**
     * Stands a piece at (x, z) with its back to wall side s, if the cell in front of it is free
     * and every free cell can still be walked to from the foot of the way down.
     */
    private boolean place(int x, int z, int s, String... stack) {
        int fx = x - STEP[s][0], fz = z - STEP[s][1];
        if (!in(x, z) || at[x - bx0][z - bz0] != FREE || !in(fx, fz) || at[fx - bx0][fz - bz0] == TAKEN) {
            return false;
        }
        at[x - bx0][z - bz0] = TAKEN;
        if (!connected()) {
            at[x - bx0][z - bz0] = FREE;
            return false;
        }
        int fs = (s + 2) & 3;
        for (int i = 0; i < stack.length; i++) {
            p.set(x, -3 + i, z, faced(stack[i], fs));
        }
        what[x - bx0][z - bz0] = stack.length == 1 ? base(stack[0]) : "";
        if (stack.length >= 3) {
            setOpen(x, z);
        }
        mark(fx, fz, FRONT);
        return true;
    }

    /** True while every cell not taken can be reached from the foot of the way down. */
    private boolean connected() {
        int w = bx1 - bx0 + 1, d = bz1 - bz0 + 1;
        boolean[][] seen = new boolean[w][d];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        seen[ax - bx0][az - bz0] = true;
        q.add(new int[] {ax, az});
        int reached = 1;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] st : STEP) {
                int x = c[0] + st[0], z = c[1] + st[1];
                if (!in(x, z) || seen[x - bx0][z - bz0] || at[x - bx0][z - bz0] == TAKEN) {
                    continue;
                }
                seen[x - bx0][z - bz0] = true;
                reached++;
                q.add(new int[] {x, z});
            }
        }
        int free = 0;
        for (byte[] col : at) {
            for (byte a : col) {
                if (a != TAKEN) {
                    free++;
                }
            }
        }
        return reached == free;
    }

    private static String base(String block) {
        int open = block.indexOf('[');
        return open < 0 ? block : block.substring(0, open);
    }

    /** Facing for pieces that turn; plain ones (the water heater, boxes, jugs, a barrel) as they are. */
    private static String faced(String block, int fs) {
        int open = block.indexOf('[');
        boolean turns = !block.startsWith("minecraft:") || block.startsWith("minecraft:barrel") && !block.contains("facing=");
        if (!turns || Interior.PLAIN.contains(open < 0 ? block : block.substring(0, open))) {
            return block;
        }
        return open < 0 ? block + face(fs) : block.substring(0, open + 1) + "facing=" + DIRS[fs] + "," + block.substring(open + 1);
    }

    private void mark(int x, int z, byte what) {
        if (in(x, z) && at[x - bx0][z - bz0] != TAKEN && at[x - bx0][z - bz0] != WAY) {
            at[x - bx0][z - bz0] = what;
        }
    }

    private void setOpen(int x, int z) {
        if (in(x, z)) {
            open[x - bx0][z - bz0] = true;
        }
    }

    private boolean in(int x, int z) {
        return x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1;
    }

    /** The side vector of a piece facing fs: its parts run this way. */
    private static int[] side(int fs) {
        return switch (fs) {
            case 0 -> new int[] {1, 0};
            case 1 -> new int[] {0, 1};
            case 2 -> new int[] {-1, 0};
            default -> new int[] {0, -1};
        };
    }

    private static String face(int d) {
        return "[facing=" + DIRS[d & 3] + "]";
    }
}
