package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Pieces shared by the county's bigger buildings (shops, churches, the courthouse, schools,
 * the mill, hangars and warehouses), drawn through a {@link Placer} in the building's own frame:
 * the front along local z = 0 facing north, x along the front.
 *
 * Walls come out weathered (cracked, mossy low down, stained, sooty where the building burnt),
 * windows broken or boarded in proportion to how hard the place was hit, roofs gable, hipped,
 * spired or flat behind a parapet, and every interior gets its ceiling lights, a stair to the
 * floor above and the mess left behind.
 */
final class Bldg {
    /** The weathered versions each wall material has (see tools/city/blocks.json). */
    private static final Map<String, String[]> WEATHER = Map.ofEntries(
            Map.entry("brick_red", new String[] {"b", "crack", "moss", "soot", "stain", "holes"}),
            Map.entry("brick_dark", new String[] {"b", "crack", "moss", "soot", "stain", "holes"}),
            Map.entry("brick_buff", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("brick_orange", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("concrete_panel", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("concrete_dark", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("stone_trim", new String[] {"b", "crack", "moss", "soot"}),
            Map.entry("ch_ashlar", new String[] {"b", "crack", "moss", "soot"}),
            Map.entry("plaster_sage", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("plaster_cream", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("plaster_blue", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("plaster_rose", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_white", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_blue", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_sage", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_yellow", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_grey", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_brown", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("siding_red", new String[] {"b", "crack", "moss", "soot", "stain"}),
            Map.entry("corrugated_grey", new String[] {"b", "stain"}),
            Map.entry("corrugated_rust", new String[] {"b", "stain"}),
            Map.entry("corrugated_green", new String[] {"b", "stain"}),
            Map.entry("corrugated_red", new String[] {"b", "stain"}),
            Map.entry("corrugated_blue", new String[] {"b", "stain"}),
            Map.entry("barn_red", new String[] {"b", "moss"}),
            Map.entry("barn_grey", new String[] {"b", "moss"}),
            Map.entry("floor_concrete", new String[] {"b", "crack", "moss"}),
            Map.entry("roof", new String[] {"b", "moss"}));

    static final String[] DIRS = {"north", "east", "south", "west"};

    final Placer p;
    final SplittableRandom rng;
    /** How hard the building was hit, 0 (shut up and left) to 1 (gutted): breaks glass, opens doors. */
    double damage = 0.4;
    /** Share of wall blackened by fire. */
    double soot = 0;
    /** Share of windows boarded up by survivors. */
    double boarded = 0.1;
    /** Share of windows still lit (a generator somewhere). */
    double lit = 0;

    Bldg(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
    }

    double chance() {
        return rng.nextDouble();
    }

    // ------------------------------------------------------------------ walls

    /** A wall block, now and then swapped for one of its weathered versions. */
    String wall(String base, int y) {
        String[] kinds = WEATHER.get(base);
        if (kinds == null) {
            return base;
        }
        double r = rng.nextDouble();
        if (soot > 0 && r < soot && has(kinds, "soot")) {
            return base + "_soot";
        }
        r = rng.nextDouble();
        if (y <= 2 && r < 0.2 && has(kinds, "moss")) {
            return base + "_moss";
        }
        if (r < 0.32) {
            String k = kinds[rng.nextInt(kinds.length)];
            return k.equals("holes") && rng.nextBoolean() ? base : base + "_" + k;
        }
        return base;
    }

    private static boolean has(String[] kinds, String k) {
        for (String s : kinds) {
            if (s.equals(k)) {
                return true;
            }
        }
        return false;
    }

    /** The four walls of a box from y0 to y1, corners included. */
    void walls(int x0, int z0, int x1, int z1, int y0, int y1, String material) {
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                p.set(x, y, z0, wall(material, y));
                p.set(x, y, z1, wall(material, y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x0, y, z, wall(material, y));
                p.set(x1, y, z, wall(material, y));
            }
        }
    }

    /** A floor (or ceiling) filling the inside of a box at y. */
    void floor(int x0, int z0, int x1, int z1, int y, String block) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, y, z, block.equals("floor_concrete") ? wall(block, 3) : block);
            }
        }
    }

    /** Air through a box (clears what an earlier step put there). */
    void clear(int x0, int y0, int z0, int x1, int y1, int z1) {
        p.fill(x0, y0, z0, x1, y1, z1, "air");
    }

    /**
     * A shell: walls round x0..x1, z0..z1 from the ground up, a floor at y = 0 and at every
     * storey, the roof deck at the top, the rooms cleared to air between.
     */
    void shell(int x0, int z0, int x1, int z1, int storeys, int storey, String material, String floor, String upper) {
        int top = storeys * storey;
        walls(x0, z0, x1, z1, 1, top, material);
        floor(x0, z0, x1, z1, 0, "concrete_dark");
        floor(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, floor);
        for (int s = 0; s < storeys; s++) {
            clear(x0 + 1, s * storey + 1, z0 + 1, x1 - 1, s * storey + storey - 1, z1 - 1);
            floor(x0 + 1, z0 + 1, x1 - 1, z1 - 1, (s + 1) * storey, s + 1 == storeys ? "roof" : upper);
        }
    }

    // ------------------------------------------------------------------ openings

    /** One pane of glass for a window running along axis: whole, lit, broken, gone or boarded. */
    String glass(String axis) {
        double r = rng.nextDouble();
        if (r < boarded) {
            return "boarded_window[axis=" + axis + "]";
        }
        r = rng.nextDouble();
        if (r < damage * 0.55) {
            return (rng.nextDouble() < 0.7 ? "window_broken" : "window_empty") + "[axis=" + axis + "]";
        }
        return (rng.nextDouble() < lit ? "window_lit" : "window") + "[axis=" + axis + "]";
    }

    /** The axis a wall facing out that way runs along. */
    static String along(String out) {
        return out.equals("north") || out.equals("south") ? "x" : "z";
    }

    static int[] step(String dir) {
        return switch (dir) {
            case "north" -> new int[] {0, -1};
            case "south" -> new int[] {0, 1};
            case "east" -> new int[] {1, 0};
            default -> new int[] {-1, 0};
        };
    }

    static String opposite(String dir) {
        return switch (dir) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /** A window h panes tall in a wall facing out, with a stone sill outside under it. */
    void window(int x, int y, int z, String out, int h, String sill) {
        String a = along(out);
        String g = glass(a);
        for (int k = 0; k < h; k++) {
            // a boarded window is boarded top to bottom; otherwise each pane breaks on its own
            p.set(x, y + k, z, g.startsWith("boarded") || k == 0 ? g : glass(a).startsWith("boarded") ? g : glass(a));
        }
        if (sill != null) {
            int[] o = step(out);
            p.set(x + o[0], y - 1, z + o[1], sill + "[facing=" + out + "]");
        }
    }

    /** Windows every `every` blocks along a wall, from a to b (x on a front or back, z on a side). */
    void windowRow(int a, int b, int every, int fixed, int y, String out, int h, String sill) {
        boolean front = out.equals("north") || out.equals("south");
        for (int i = a; i <= b; i += every) {
            if (front) {
                window(i, y, fixed, out, h, sill);
            } else {
                window(fixed, y, i, out, h, sill);
            }
        }
    }

    /** A door into a wall facing out (city door types: shop_door, fire_door, flat_door, or a vanilla door). */
    void door(int x, int y, int z, String type, String out) {
        String facing = opposite(out);
        if (type.startsWith("minecraft:")) {
            double r = rng.nextDouble();
            if (r < damage * 0.25) {
                p.set(x, y, z, "air");
                p.set(x, y + 1, z, "air");
                return;
            }
            String props = "[facing=" + facing + ",hinge=" + (rng.nextBoolean() ? "left" : "right") + ",open=" + (r < 0.3 + damage * 0.3);
            p.set(x, y, z, type + props + ",half=lower]");
            p.set(x, y + 1, z, type + props + ",half=upper]");
        } else {
            Doors.abandoned(p, rng, x, y, z, type, facing, Doors.hinge(rng));
        }
    }

    /** A pair of doors at x and x + 1 in a wall along x. */
    void doors(int x, int y, int z, String type, String out) {
        if (type.startsWith("minecraft:")) {
            door(x, y, z, type, out);
            door(x + 1, y, z, type, out);
        } else {
            Doors.pair(p, rng, x, y, z, type, opposite(out));
        }
    }

    // ------------------------------------------------------------------ roofs

    private String stairs(String family, String facing, boolean top) {
        return family + "_stairs[facing=" + facing + ",half=" + (top ? "top" : "bottom") + "]";
    }

    private void tile(int x, int y, int z, String block) {
        if (rng.nextDouble() < damage * 0.06) {
            return;  // blown off, or burnt through
        }
        p.set(x, y, z, block);
    }

    /**
     * A gable roof over x0..x1, z0..z1 sitting on y, its ridge along x (or along z), overhanging
     * by `over`, the gable ends filled with `gableWall`. family is a vanilla stairs family
     * ("minecraft:deepslate_tile").
     */
    void gable(int x0, int x1, int z0, int z1, int y, String family, boolean ridgeAlongZ, String gableWall, int over) {
        if (!ridgeAlongZ) {
            int b0 = z0 - over, b1 = z1 + over;
            int half = (b1 - b0 + 1) / 2;
            for (int i = 0; i < half; i++) {
                for (int x = x0 - over; x <= x1 + over; x++) {
                    tile(x, y + i, b0 + i, stairs(family, "south", false));
                    tile(x, y + i, b1 - i, stairs(family, "north", false));
                    if (i > 0) {
                        p.set(x, y + i - 1, b0 + i, stairs(family, "north", true));
                        p.set(x, y + i - 1, b1 - i, stairs(family, "south", true));
                    }
                }
                for (int z = Math.max(z0, b0 + i + 1); z <= Math.min(z1, b1 - i - 1); z++) {
                    p.set(x0, y + i, z, wall(gableWall, y + i));
                    p.set(x1, y + i, z, wall(gableWall, y + i));
                }
            }
            if ((b1 - b0 + 1) % 2 == 1) {
                for (int x = x0 - over; x <= x1 + over; x++) {
                    tile(x, y + half, b0 + half, family + "_slab[type=bottom]");
                }
            }
        } else {
            int a0 = x0 - over, a1 = x1 + over;
            int half = (a1 - a0 + 1) / 2;
            for (int i = 0; i < half; i++) {
                for (int z = z0 - over; z <= z1 + over; z++) {
                    tile(a0 + i, y + i, z, stairs(family, "east", false));
                    tile(a1 - i, y + i, z, stairs(family, "west", false));
                    if (i > 0) {
                        p.set(a0 + i, y + i - 1, z, stairs(family, "west", true));
                        p.set(a1 - i, y + i - 1, z, stairs(family, "east", true));
                    }
                }
                for (int x = Math.max(x0, a0 + i + 1); x <= Math.min(x1, a1 - i - 1); x++) {
                    p.set(x, y + i, z0, wall(gableWall, y + i));
                    p.set(x, y + i, z1, wall(gableWall, y + i));
                }
            }
            if ((a1 - a0 + 1) % 2 == 1) {
                for (int z = z0 - over; z <= z1 + over; z++) {
                    tile(a0 + half, y + half, z, family + "_slab[type=bottom]");
                }
            }
        }
    }

    /**
     * A gable roof in the house kit's courses (tools/city/exterior.py; kind is "tin_galv",
     * "brown" and the like) over x0..x1, z0..z1 from y, its ridge along z (or x), overhanging by
     * over: the eave course with its gutter (now and then gone), the decking under each course
     * inside the walls, the crest or ridge cap on top, moss and patches, the gable ends filled
     * with gableWall.
     */
    void roofGable(int x0, int x1, int z0, int z1, int y, String kind, boolean ridgeAlongZ, String gableWall, int over) {
        String k = "ext_roof_" + kind;
        // a runs across the slopes, u along the ridge
        int w0 = ridgeAlongZ ? x0 : z0, w1 = ridgeAlongZ ? x1 : z1, e0 = ridgeAlongZ ? z0 : x0, e1 = ridgeAlongZ ? z1 : x1;
        int a0 = w0 - over, a1 = w1 + over, span = a1 - a0 + 1, half = span / 2;
        String[] rise = ridgeAlongZ ? new String[] {"east", "west"} : new String[] {"south", "north"};
        for (int i = 0; i < half; i++) {
            boolean crest = i == half - 1 && span % 2 == 0;
            for (int side = 0; side < 2; side++) {
                int a = side == 0 ? a0 + i : a1 - i;
                String f = "[facing=" + rise[side] + "]";
                for (int u = e0 - over; u <= e1 + over; u++) {
                    double r = rng.nextDouble();
                    String piece = i == 0 ? k + (r < 0.1 + damage * 0.2 ? "_eave_bare" : "_eave")
                            : crest ? k + "_crest" : k + (r < 0.12 ? "_moss" : r < 0.17 ? "_patch" : "");
                    tile(ridgeAlongZ ? a : u, y + i, ridgeAlongZ ? u : a, piece + f);
                    if (i > 0 && u > e0 && u < e1) {
                        p.set(ridgeAlongZ ? a : u, y + i - 1, ridgeAlongZ ? u : a, "ext_roof_under" + f);
                    }
                }
            }
            for (int a = Math.max(w0, a0 + i + 1); a <= Math.min(w1, a1 - i - 1); a++) {
                for (int u : new int[] {e0, e1}) {
                    p.set(ridgeAlongZ ? a : u, y + i, ridgeAlongZ ? u : a, wall(gableWall, y + i));
                }
            }
        }
        if (span % 2 == 1) {
            for (int u = e0 - over; u <= e1 + over; u++) {
                tile(ridgeAlongZ ? a0 + half : u, y + half, ridgeAlongZ ? u : a0 + half, k + "_ridge[facing=" + (ridgeAlongZ ? "east" : "north") + "]");
            }
        }
    }

    /** A hipped roof (four slopes) over x0..x1, z0..z1 from y, rising one block per step in. */
    void hip(int x0, int x1, int z0, int z1, int y, String family, int over) {
        int a0 = x0 - over, a1 = x1 + over, b0 = z0 - over, b1 = z1 + over;
        for (int i = 0; a0 + i <= a1 - i && b0 + i <= b1 - i; i++) {
            int lx = a0 + i, hx = a1 - i, lz = b0 + i, hz = b1 - i;
            if (lx == hx || lz == hz) {
                for (int x = lx; x <= hx; x++) {
                    for (int z = lz; z <= hz; z++) {
                        tile(x, y + i, z, family + "_slab[type=bottom]");
                    }
                }
                break;
            }
            for (int x = lx; x <= hx; x++) {
                tile(x, y + i, lz, stairs(family, "south", false));
                tile(x, y + i, hz, stairs(family, "north", false));
            }
            for (int z = lz + 1; z < hz; z++) {
                tile(lx, y + i, z, stairs(family, "east", false));
                tile(hx, y + i, z, stairs(family, "west", false));
            }
        }
    }

    /** The full block of a stairs family ("minecraft:deepslate_tile" -> deepslate_tiles). */
    static String full(String family) {
        return switch (family) {
            case "minecraft:deepslate_tile", "minecraft:mud_brick", "minecraft:tuff_brick", "minecraft:stone_brick",
                 "minecraft:deepslate_brick", "minecraft:brick", "minecraft:nether_brick" -> family + "s";
            case "minecraft:dark_oak", "minecraft:spruce", "minecraft:oak", "minecraft:pale_oak", "minecraft:birch" -> family + "_planks";
            default -> family;
        };
    }

    /**
     * A steep spire over a square from (x0, z0), `size` blocks across, stepping in a block every
     * two up, with a finial on the point.
     */
    void spire(int x0, int z0, int size, int y, String family, String finial) {
        String full = full(family);
        int yy = y;
        for (int i = 0; i * 2 < size; i++) {
            int lo = i, hi = size - 1 - i;
            for (int a = lo; a <= hi; a++) {
                for (int b = lo; b <= hi; b++) {
                    if (a != lo && a != hi && b != lo && b != hi) {
                        continue;
                    }
                    p.set(x0 + a, yy, z0 + b, full);
                    String f = lo == hi ? null : b == lo ? "south" : b == hi ? "north" : a == lo ? "east" : "west";
                    p.set(x0 + a, yy + 1, z0 + b, f == null ? full : stairs(family, f, false));
                }
            }
            yy += 2;
        }
        int c = x0 + (size - 1) / 2, cz = z0 + (size - 1) / 2;
        p.set(c, yy, cz, finial);
    }

    /** A flat roof deck at y with a parapet h high round the edge of x0..x1, z0..z1. */
    void flat(int x0, int z0, int x1, int z1, int y, String parapet, int h, String coping) {
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x, y, z, wall("roof", 2));
            }
        }
        if (h > 0) {
            walls(x0, z0, x1, z1, y + 1, y + h - (coping != null ? 1 : 0), parapet);
            if (coping != null) {
                for (int x = x0; x <= x1; x++) {
                    p.set(x, y + h, z0, coping);
                    p.set(x, y + h, z1, coping);
                }
                for (int z = z0 + 1; z < z1; z++) {
                    p.set(x0, y + h, z, coping);
                    p.set(x1, y + h, z, coping);
                }
            }
        }
        // a puddle or a patch of moss where the drains blocked
        for (int i = 0; i < (x1 - x0) * (z1 - z0) / 60; i++) {
            int x = x0 + 1 + rng.nextInt(Math.max(1, x1 - x0 - 1)), z = z0 + 1 + rng.nextInt(Math.max(1, z1 - z0 - 1));
            p.setIfAir(x, y + 1, z, rng.nextBoolean() ? "moss_carpet" : rng.nextBoolean() ? "weeds" : "paper");
        }
    }

    /**
     * A cornice along the front at y: a band of `band` sticking out over the street, with
     * brackets (upside-down stairs) under it every other block.
     */
    void cornice(int x0, int x1, int y, String band, String bracketFamily) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            p.set(x, y, -1, band);
            if (bracketFamily != null && (x - x0) % 2 == 0 && x >= x0 && x <= x1) {
                p.set(x, y - 1, -1, bracketFamily + "_stairs[facing=south,half=top]");
            }
        }
    }

    // ------------------------------------------------------------------ inside

    /**
     * A straight stair from y up `rise` blocks, starting at (x, z) and climbing toward dir, with
     * the floor above opened over it.
     */
    void stair(int x, int z, int y, int rise, String dir, String family) {
        int[] s = step(dir);
        for (int i = 0; i < rise; i++) {
            int sx = x + s[0] * i, sz = z + s[1] * i;
            p.set(sx, y + i, sz, family + "_stairs[facing=" + dir + ",half=bottom]");
            for (int k = 1; k <= 3 && y + i + k <= y + rise; k++) {
                if (y + i + k == y + rise && i == rise - 1) {
                    break;
                }
                p.set(sx, y + i + k, sz, "air");
            }
        }
    }

    /** Ceiling lights in a grid under the ceiling at y (most dead). */
    void lights(int x0, int z0, int x1, int z1, int y, double on) {
        for (int x = x0 + 1; x <= x1 - 1; x += 4) {
            for (int z = z0 + 1; z <= z1 - 1; z += 4) {
                if (p.get(x, y, z) == null) {
                    p.set(x, y, z, rng.nextDouble() < on ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
    }

    /** What's left on a floor: papers, blood, glass, rubble, rubbish, a fallen chair. */
    void mess(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (p.get(x, y, z) != null || rng.nextDouble() > density) {
                    continue;
                }
                double r = rng.nextDouble();
                p.set(x, y, z, r < 0.35 ? "paper" : r < 0.5 ? "blood_splat" : r < 0.62 ? "glass_shards"
                        : r < 0.74 ? "rubble_pile" : r < 0.86 ? "trash_bag[facing=" + DIRS[rng.nextInt(4)] + "]"
                        : r < 0.94 ? "moss_carpet" : "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
            }
        }
    }
}
