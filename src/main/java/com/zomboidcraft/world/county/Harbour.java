package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The pieces of a port, drawn through a {@link Placer} in their own frame (the water toward -z,
 * the quay's top at y = 0, the water's top block at {@link Ships#SEA}):
 * <ul>
 * <li>the quay's edge with its bollards, fender tyres and ladders; wooden jetties on their piles;
 * a rubble breakwater and the lighthouse at its end;</li>
 * <li>cranes: the ship-to-shore gantries with their booms out over the water (or raised), the
 * rubber-tyred gantries over the stacks, a mobile harbour crane;</li>
 * <li>the yard: stacks of containers, chassis with and without a box, the high lighting masts;</li>
 * <li>the industry behind: oil tanks in their bund, a grain elevator and its gallery, heaps of
 * coal and gravel, a saw-tooth factory and its chimney, warehouses, open sheds.</li>
 * </ul>
 */
final class Harbour {
    private static final String[] BOXES = {"container_red", "container_blue", "container_green", "container_orange",
            "container_white", "container_grey"};

    private final Placer p;
    private final SplittableRandom rng;
    /** How far gone the port is, 0 to 1. */
    private final double hit;

    Harbour(Placer p, SplittableRandom rng, double hit) {
        this.p = p;
        this.rng = rng;
        this.hit = hit;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    /** A port lamp: amber sodium or the purple and green the cities have, some dead. */
    String lamp(double dead) {
        if (chance() < dead) {
            return "minecraft:smooth_quartz";
        }
        double r = chance();
        return r < 0.45 ? "minecraft:ochre_froglight" : r < 0.85 ? "minecraft:pearlescent_froglight" : "minecraft:verdant_froglight";
    }

    /** A straight line of blocks between two points (both included). */
    void line(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        int n = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            p.set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), (int) Math.round(z0 + (z1 - z0) * t), block);
        }
    }

    /**
     * A straight member in the plane x from (y0, z0) to (y1, z1): cr_ member blocks (tools/city/ports.py)
     * turned to the nearest 11.25 degrees and set off their cells' centres (3 px steps) to follow the true line,
     * instead of a staircase of whole blocks. family is cr_stay, cr_strut_red, cr_boom_yellow...
     */
    void member(int x, double y0, double z0, double y1, double z1, String family) {
        double dz = z1 - z0, dy = y1 - y0;
        int k = Math.floorMod((int) Math.round(Math.atan2(dy, dz) / (Math.PI / 16)), 16);
        double n = k * Math.PI / 16 + Math.PI / 2, nz = Math.cos(n), ny = Math.sin(n);
        int steps = (int) Math.ceil(Math.max(Math.abs(dz), Math.abs(dy)));
        boolean fine = family.equals("cr_stay"); // a thin stay steps by 1.5 px, the rest by 3
        double step = fine ? 1.5 : 3;
        int lim = fine ? 6 : 3;
        for (int i = 0; i <= steps; i++) {
            double pz = z0 + dz * i / steps, py = y0 + dy * i / steps;
            int cz = (int) Math.round(pz), cy = (int) Math.round(py);
            int o = (int) Math.round(((pz - cz) * nz + (py - cy) * ny) * 16 / step);
            o = Math.max(-lim, Math.min(lim, o));
            p.set(x, cy, cz, family + "_" + k + (o > 0 ? "_p" + o : o < 0 ? "_m" + (-o) : ""));
        }
    }

    /** The member family for a crane's steel colour: steel_red -> cr_strut_red. */
    static String strut(String steel) {
        return "cr_strut_" + (steel.endsWith("red") ? "red" : steel.endsWith("white") ? "white" : "yellow");
    }

    /** The crane plating for a steel colour: steel_red -> cr_plate_red, its welds and splice straps. */
    static String plate(String steel) {
        return "cr_plate_" + (steel.endsWith("red") ? "red" : steel.endsWith("white") ? "white" : "yellow");
    }

    /** A shipping container 12 long along x (or z) from (x, y, z), 3 x 3, doors at the low end. */
    void box(int x, int y, int z, String colour, boolean alongX) {
        Boxes.place(p, x, y, z, 12, colour, alongX, true, false);
    }

    String boxColour() {
        return BOXES[rng.nextInt(BOXES.length)];
    }

    // ------------------------------------------------------------------ the waterfront

    /**
     * The quay's edge along x from x0 to x1 on the row z (the first row of land): a concrete
     * capping beam, its face down to the bed with weed at the waterline, bollards, tyres hung as
     * fenders, a ladder down now and then.
     */
    void quay(int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) {
            p.set(x, 0, z, (x - x0) % 9 == 4 ? "concrete_dark" : chance() < 0.15 ? "concrete_dark_crack" : "concrete_panel");
            for (int y = Ships.BED; y < 0; y++) {
                p.set(x, y, z, y <= Ships.SEA && y >= Ships.SEA - 1 ? "concrete_panel_moss" : y < Ships.SEA ? "concrete_dark" : "concrete_panel_stain");
            }
            if ((x - x0) % 9 == 4) {
                p.set(x, 1, z, "vessel_bollard[axis=x]");
            }
            if ((x - x0) % 6 == 1 && chance() > hit * 0.3) {
                p.set(x, -1, z - 1, "tire[axis=x]");
                p.set(x, -2, z - 1, "tire[axis=x]");
            }
            if ((x - x0) % 27 == 13) {
                // up beside the capping beam, so a climber can step off it onto the quay
                for (int y = Ships.SEA - 1; y <= 0; y++) {
                    p.set(x, y, z - 1, "bunk_ladder[facing=north]");
                }
            }
        }
    }

    /**
     * A timber jetty from (x, z) out along -z for len, w wide, its deck at y: the pier's decking on
     * stringers along its edges and middle, a cross beam and a bent of creosoted piles down to the bed
     * every four, a planks gone here and there; cleats along the edges, a pile standing up through the
     * deck at the corners of every eighth bent, the light on its pile at the end, the gear left there.
     */
    void jetty(int x, int z, int len, int w, int y) {
        for (int k = 0; k < len; k++) {
            for (int i = 0; i < w; i++) {
                if (chance() < 0.03 + hit * 0.06 && k > 2) {
                    continue;  // a rotten plank gone
                }
                p.set(x + i, y, z - k, chance() < 0.08 ? "wf_deck_b" : "wf_deck");
            }
            for (int i : new int[] {0, w / 2, w - 1}) {
                p.set(x + i, y - 1, z - k, "wf_stringer[axis=z]");
            }
            if (k % 4 == 0 || k == len - 1) {
                for (int i = 0; i < w; i++) {
                    p.set(x + i, y - 2, z - k, "wf_stringer[axis=x]");
                }
                for (int i : new int[] {0, w - 1}) {
                    for (int yy = Ships.BED; yy < y - 2; yy++) {
                        p.set(x + i, yy, z - k, "wf_pile");
                    }
                    if (k % 8 == 0 && k > 0) {
                        p.set(x + i, y + 1, z - k, "wf_pile_top");
                    }
                }
            } else if (k % 4 == 2) {
                for (int i : new int[] {0, w - 1}) {
                    p.set(x + i, y + 1, z - k, "wf_cleat[axis=z]");
                }
            }
        }
        int ex = x + w / 2, ez = z - len + 1;
        p.set(x, y + 1, ez, "wf_pile");
        p.set(x, y + 2, ez, "wf_pile");
        p.set(x, y + 3, ez, "wf_pile_top");
        p.set(x, y + 4, ez, Camp.lamp(rng, 0.5, "cp_flood", "north"));
        p.set(ex, y + 1, ez, chance() < 0.5 ? "wf_pots[facing=" + any("north", "east", "south", "west") + "]" : "wf_coil");
    }

    /** A rubble breakwater along x from x0 to x1, centred on z, wide at the bed and up to y = top. */
    void breakwater(int x0, int x1, int z, int width, int top) {
        for (int x = x0; x <= x1; x++) {
            for (int dz = -width; dz <= width; dz++) {
                double crest = top - Math.max(0, Math.abs(dz) - 2) * 1.6 + (chance() - 0.5) * 1.2;
                for (int y = Ships.BED; y <= Math.min(top + 1, (int) Math.round(crest)); y++) {
                    p.set(x, y, z + dz, any("minecraft:stone", "minecraft:andesite", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
                            "minecraft:polished_andesite"));
                }
            }
        }
    }

    /**
     * The lighthouse on (cx, cz) from y up: a round tower striped white and red, its gallery
     * railed round, the lantern room glazed with its light (or dead), a dark cap.
     */
    void lighthouse(int cx, int cz, int y, int h) {
        int r = 3;
        for (int yy = y; yy < y + h; yy++) {
            boolean red = ((yy - y) / 4) % 2 == 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= r + 0.3) {
                        boolean wall = d > r - 0.8;
                        p.set(cx + dx, yy, cz + dz, wall ? (red ? "minecraft:red_concrete" : "minecraft:white_concrete") : "air");
                    }
                }
            }
        }
        for (int yy = y; yy < y + h; yy++) {
            p.set(cx, yy, cz, "minecraft:spruce_planks");
        }
        p.set(cx, y, cz - r, "flat_door[facing=north,half=lower,hinge=left,open=false]");
        p.set(cx, y + 1, cz - r, "flat_door[facing=north,half=upper,hinge=left,open=false]");
        int g = y + h;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= r + 1.3) {
                    p.set(cx + dx, g, cz + dz, "deck_steel");
                    if (d > r + 0.4) {
                        p.set(cx + dx, g + 1, cz + dz, "minecraft:iron_bars");
                    }
                }
            }
        }
        for (int yy = g + 1; yy <= g + 3; yy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                        boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                        p.set(cx + dx, yy, cz + dz, corner ? "minecraft:black_concrete" : "minecraft:glass_pane");
                    }
                }
            }
        }
        p.set(cx, g + 2, cz, chance() < 0.4 + hit * 0.3 ? "minecraft:redstone_lamp" : "minecraft:ochre_froglight");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                p.set(cx + dx, g + 4, cz + dz, Math.abs(dx) + Math.abs(dz) > 3 ? "air" : "minecraft:black_concrete");
            }
        }
        p.set(cx, g + 5, cz, "minecraft:lightning_rod");
    }

    // ------------------------------------------------------------------ cranes

    /**
     * A ship-to-shore gantry crane on the rails at z = 0 (the waterside) and z = gauge, its
     * portal pw wide along x from 0: the legs on their bogies, sill and portal beams, the boom out
     * over the water to -reach and back over the land (or raised, standing up over the water),
     * the A-frame and its stays, the machinery house, the trolley with a box hanging from it.
     */
    void gantry(int pw, int gauge, int reach, boolean raised, String steel, int top) {
        int apex = top + 18, back = 14;
        String strut = strut(steel);
        steel = plate(steel);
        for (int[] c : new int[][] {{0, 0}, {pw - 1, 0}, {0, gauge - 1}, {pw - 1, gauge - 1}}) {
            for (int y = 1; y <= top; y++) {
                for (int i = 0; i < 2; i++) {
                    for (int k = 0; k < 2; k++) {
                        p.set(c[0] + i, y, c[1] + k, y <= 3 ? (y == 1 ? "deck_steel" : "hazard_stripes") : y % 5 == 0 ? steel + "_seam" : steel);
                    }
                }
            }
            // the wheel trucks along the rail
            for (int x = c[0] - 2; x <= c[0] + 3; x++) {
                p.set(x, 1, c[1], "cr_bogie[axis=x]");
                p.set(x, 1, c[1] + 1, "cr_bogie[axis=x]");
            }
        }
        // the portal braces down each side, leg to leg
        for (int x : new int[] {0, pw - 1}) {
            member(x, 9, 2, top - 1, gauge - 3, strut);
        }
        for (int x = 0; x <= pw; x++) {
            for (int z : new int[] {0, 1, gauge - 2, gauge - 1}) {
                p.set(x, top, z, steel);
            }
        }
        for (int z = 0; z < gauge; z++) {
            for (int x : new int[] {0, 1, pw - 2, pw - 1}) {
                p.set(x, top, z, steel);
                p.set(x, 8, z, steel);
            }
        }
        // the boom: two girders with ties across, the back reach over the house
        int b0 = 2, b1 = pw - 3;
        for (int z = gauge - 1; z <= gauge + back; z++) {
            for (int x : new int[] {b0, b1}) {
                p.set(x, top + 1, z, steel);
                p.set(x, top + 2, z, steel);
            }
        }
        int tipY = top + 2, tipZ = -reach;
        for (int z = -1; z < gauge; z++) {
            for (int x : new int[] {b0, b1}) {
                p.set(x, top + 1, z, steel);
                p.set(x, top + 2, z, steel);
            }
        }
        if (raised) {
            tipY = top + 1 + reach;
            tipZ = -1 - reach / 6;
            for (int x : new int[] {b0, b1}) {
                member(x, top + 1, -1.5, tipY, tipZ - 0.5, steel.endsWith("red") ? "cr_boom_red" : "cr_boom_yellow");
            }
            for (int y = top + 6; y < tipY; y += 6) {
                int z = -1 - (y - top - 1) / 6;
                for (int x = b0 + 1; x < b1; x++) {
                    p.set(x, y, z, steel);
                }
            }
        } else {
            for (int z = -reach; z < -1; z++) {
                for (int x : new int[] {b0, b1}) {
                    p.set(x, top + 1, z, steel);
                    p.set(x, top + 2, z, steel);
                }
                if (z % 6 == 0) {
                    for (int x = b0; x <= b1; x++) {
                        p.set(x, top + 2, z, steel);
                    }
                }
                // the festoon cables looped under the boom, the trolley's power
                if (z % 3 == 0) {
                    p.set(b1 - 1, top, z, "cr_festoon[axis=z]");
                }
            }
            // the trolley out over the ship, the cab under it, a box on the spreader
            int tz = -reach + 6 + rng.nextInt(Math.max(1, reach - 4));
            for (int x = b0; x <= b1; x++) {
                for (int z = tz; z < tz + 3; z++) {
                    p.set(x, top + 3, z, "cr_plate_yellow");
                }
            }
            for (int y = top - 2; y <= top; y++) {
                p.set(b0 + 1, y, tz, "window[axis=z]");
                p.set(b0 + 1, y, tz + 1, y == top - 2 ? "deck_steel" : "window[axis=x]");
                p.set(b0 + 2, y, tz + 1, y == top - 2 ? "deck_steel" : "window[axis=x]");
            }
            int drop = 6 + rng.nextInt(10);
            for (int y = top - drop; y <= top; y++) {
                p.set(b0 + 3, y, tz + 1, "vessel_rope_v");
                p.set(b1 - 1, y, tz + 1, "vessel_rope_v");
            }
            int sy = top - drop - 1;
            for (int x = pw / 2 - 6; x < pw / 2 + 6; x++) {
                p.set(x, sy, tz + 1, x == pw / 2 - 6 ? "cr_spreader_end[facing=west]" : x == pw / 2 + 5 ? "cr_spreader_end[facing=east]"
                        : "cr_spreader[axis=x]");
            }
            if (chance() < 0.6) {
                box(Math.max(0, pw / 2 - 6), sy - 3, tz, boxColour(), true);
            }
        }
        // the A-frame and its stays
        for (int x : new int[] {0, pw - 1}) {
            member(x, top, 0, apex, 3, strut);
            member(x, top, gauge - 1, apex, 3, strut);
        }
        for (int x = 0; x < pw; x++) {
            p.set(x, apex, 3, steel);
        }
        for (int x : new int[] {b0, b1}) {
            member(x, apex, 3, tipY, tipZ, "cr_stay");
            member(x, apex, 3, top + 2, gauge + back, "cr_stay");
        }
        // the machinery house on the back reach: ribbed cladding between steel corner posts, a door
        // off the A-frame's side, louvres over the hoist motors, a handrail round its roof
        int z0 = gauge + 3, z1 = gauge + back - 1, y0 = top + 3, y1 = top + 7;
        for (int x = b0; x <= b1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean ex = x == b0 || x == b1, ez = z == z0 || z == z1;
                for (int y = y0; y <= y1; y++) {
                    String face = x == b0 ? "west" : x == b1 ? "east" : z == z0 ? "north" : "south";
                    String row = y == y0 ? "side_bot" : y == y1 - 1 ? "side_top" : "side";
                    p.set(x, y, z, y == y1 ? "ct_white_roof[facing=north]" : ex && ez ? "cr_plate_white"
                            : ex || ez ? "ct_white_" + row + "[facing=" + face + "]" : "air");
                }
                if (ex || ez) {
                    String out = ez ? (z == z0 ? "north" : "south") : x == b0 ? "west" : "east";
                    p.set(x, y1 + 1, z, "cr_rail[facing=" + out + "]");
                }
            }
        }
        p.set(b0 + 2, y0, z0, "bunk_door_steel[facing=south,half=lower,hinge=left,open=false]");
        p.set(b0 + 2, y0 + 1, z0, "bunk_door_steel[facing=south,half=upper,hinge=left,open=false]");
        for (int z = z0 + 2; z < z1 - 1; z += 3) {
            p.set(b0 - 1, y0 + 1, z, "fit_louvre[facing=west]");
            p.set(b1 + 1, y0 + 1, z, "fit_louvre[facing=east]");
        }
        p.set(0, apex + 1, 3, "aviation_light");
        p.set(pw - 1, apex + 1, 3, "aviation_light");
        p.set(pw / 2, top - 1, gauge / 2, lamp(0.3));
        // a caged ladder up the landside leg, the walkways down both sides of the boom
        for (int y = 2; y < top; y++) {
            p.set(0, y, gauge, "cr_cage_ladder[facing=south]");
        }
        for (int z = raised ? -1 : -reach; z <= gauge + back; z++) {
            p.set(b0 - 1, top + 1, z, "cr_walk[facing=west]");
            p.set(b1 + 1, top + 1, z, "cr_walk[facing=east]");
        }
    }

    /** A rubber-tyred gantry len along x and span across z, h high, over a block of stacks. */
    void rtg(int len, int span, int h, String steel) {
        steel = plate(steel);
        for (int[] c : new int[][] {{0, 0}, {len, 0}, {0, span}, {len, span}}) {
            for (int y = 2; y <= h; y++) {
                p.set(c[0], y, c[1], y % 5 == 0 ? steel + "_seam" : steel);
            }
            p.set(c[0], 1, c[1], "tire[axis=x]");
            p.set(c[0] - 1, 1, c[1], "tire[axis=x]");
            p.set(c[0] + 1, 1, c[1], "tire[axis=x]");
            p.set(c[0], 2, c[1], "deck_steel");
        }
        for (int z = 0; z <= span; z++) {
            p.set(0, h + 1, z, steel);
            p.set(len, h + 1, z, steel);
        }
        for (int x = 0; x <= len; x++) {
            p.set(x, h + 1, 0, steel);
            p.set(x, h + 1, span, steel);
            p.set(x, 3, 0, steel);
            p.set(x, 3, span, steel);
        }
        int tz = 2 + rng.nextInt(Math.max(1, span - 5));
        for (int x = 1; x < len; x++) {
            p.set(x, h + 2, tz, "deck_steel");
            p.set(x, h + 2, tz + 1, "deck_steel");
        }
        p.set(len / 2, h, tz, "window[axis=x]");
        p.set(len / 2, h - 1, tz, "window[axis=x]");
        p.set(1, h + 2, 0, lamp(0.4));
        // the engine house on the leg, a caged ladder up the far one
        for (int y = 4; y <= 6; y++) {
            for (int x = 1; x <= 3; x++) {
                p.set(x, y, -1, "corrugated_grey");
            }
        }
        for (int y = 4; y <= h; y++) {
            p.set(len, y, span + 1, "cr_cage_ladder[facing=south]");
        }
    }

    /**
     * A mobile harbour crane: the wheeled base on its outriggers, the tower, the cab, the lattice
     * boom out over the water toward -z, the hook hanging.
     */
    void harbourCrane(int boom, String steel) {
        String strut = strut(steel);
        steel = plate(steel);
        // the carrier on its tyres, the outriggers out to their jacks
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                p.set(x, 1, z, Math.abs(z) == 4 && x != 0 ? "tire[axis=x]" : Math.abs(z) < 3 ? "cr_plate_grey" : "air");
                p.set(x, 2, z, Math.abs(x) == 4 || Math.abs(z) == 4 ? "hazard_stripes" : "deck_steel");
            }
        }
        for (int sx : new int[] {-1, 1}) {
            for (int z : new int[] {-3, 3}) {
                for (int x = 5; x <= 7; x++) {
                    p.set(sx * x, 2, z, "cr_outrigger[axis=x]");
                }
                p.set(sx * 8, 1, z, "cr_pad[facing=north]");
                p.set(sx * 8, 2, z, "cr_plate_white");
            }
        }
        // the tower, plated, a caged ladder up its back
        for (int y = 3; y <= 16; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    p.set(x, y, z, y % 5 == 0 ? steel + "_seam" : steel);
                }
            }
            p.set(0, y, 2, "cr_cage_ladder[facing=south]");
        }
        for (int x = -2; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                for (int y = 17; y <= 19; y++) {
                    boolean side = x == -2 || x == 3 || z == -2 || z == 2;
                    p.set(x, y, z, !side ? "air" : y == 18 && z == -2 && x > -2 && x < 3 ? "window[axis=x]" : steel);
                }
                p.set(x, 20, z, "deck_steel");
            }
        }
        int tipY = 20 + boom * 3 / 5, tipZ = -boom * 4 / 5;
        member(-1, 18, -3, tipY, tipZ, "cr_lattice_yellow");
        member(1, 18, -3, tipY, tipZ, "cr_lattice_yellow");
        for (int y = tipY - 1; y > tipY - 10; y--) {
            p.set(0, y, tipZ, "vessel_rope_v");
        }
        p.set(0, tipY - 10, tipZ, "cr_headblock[axis=z]");
        p.set(0, tipY + 1, tipZ, "aviation_light");
        member(0, 20, 3, 26, 0, strut);
        member(0, 26, 0, tipY, tipZ, "cr_stay");
    }

    /** A high lighting mast at (x, z), h tall, its head of lamps. */
    void lightMast(int x, int z, int h) {
        for (int y = 1; y < h; y++) {
            p.set(x, y, z, "streetlight_pole");
        }
        p.set(x, 0, z, "concrete_dark");
        p.set(x, h, z, "steel_girder");
        String l = lamp(0.25 + hit * 0.25);
        p.set(x - 1, h, z, l);
        p.set(x + 1, h, z, chance() < 0.3 ? lamp(0.5) : l);
        p.set(x, h + 1, z, "aviation_light");
    }

    // ------------------------------------------------------------------ the yard

    /**
     * A block of stacks from (x, z): cols containers end to end along x (a step between), rows
     * side by side along z, each pile one to tiers high, gaps where boxes were taken.
     */
    void stacks(int x, int z, int cols, int rows, int tiers) {
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int t = 1 + rng.nextInt(tiers);
                if (chance() < 0.08) {
                    continue;
                }
                for (int j = 0; j < t; j++) {
                    box(x + c * 13, 1 + j * 3, z + r * 3, boxColour(), true);
                }
            }
        }
    }

    /** A chassis 12 long along x from (x, z): the frame on its wheels, a box on it, or not. */
    void chassis(int x, int z, boolean loaded) {
        for (int i = 0; i < 12; i++) {
            p.set(x + i, 2, z, "steel_girder");
            p.set(x + i, 2, z + 2, "steel_girder");
            if (i % 4 == 0) {
                p.set(x + i, 2, z + 1, "steel_girder");
            }
        }
        for (int i : new int[] {1, 2, 9, 10}) {
            p.set(x + i, 1, z, "tire[axis=x]");
            p.set(x + i, 1, z + 2, "tire[axis=x]");
        }
        p.set(x + 7, 1, z, "minecraft:iron_bars");
        p.set(x + 7, 1, z + 2, "minecraft:iron_bars");
        if (loaded) {
            box(x, 3, z, boxColour(), true);
        }
    }

    // ------------------------------------------------------------------ industry

    /** An oil tank on (cx, cz), radius r and h high: white or blue, a shallow roof, the ladder and the rail round its top. */
    void tank(int cx, int cz, int r, int h, String skin) {
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 0.4) {
                    continue;
                }
                boolean wall = d > r - 0.7;
                for (int y = 1; y <= h; y++) {
                    p.set(cx + dx, y, cz + dz, wall ? (chance() < 0.04 + hit * 0.08 ? "rust" : skin) : "air");
                }
                int roofY = h + 1 + (int) ((r - d) / 3);
                p.set(cx + dx, roofY, cz + dz, "minecraft:smooth_quartz_slab[type=bottom]");
                if (wall) {
                    p.set(cx + dx, h + 1, cz + dz, "minecraft:iron_bars");
                }
            }
        }
        for (int y = 1; y <= h; y++) {
            p.set(cx, y, cz - r - 1, "minecraft:ladder[facing=north]");
        }
        p.set(cx, 1, cz - r - 2, "minecraft:iron_bars");
    }

    /** A bund wall from (x0, z0) to (x1, z1): low concrete round a tank farm. */
    void bund(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    p.set(x, 1, z, "concrete_panel");
                    p.set(x, 2, z, chance() < 0.1 ? "air" : "minecraft:smooth_stone_slab[type=bottom]");
                } else {
                    p.set(x, 0, z, chance() < 0.7 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                }
            }
        }
    }

    /** A pipe run along x from x0 to x1 at z, on a rack h high: three pipes, posts every 6. */
    void pipeRack(int x0, int x1, int z, int h) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int k = 0; k < 3; k++) {
                p.set(x, h, z + k, "minecraft:iron_bars");
            }
            if (x % 6 == 0) {
                for (int y = 1; y < h; y++) {
                    p.set(x, y, z, "steel_girder");
                    p.set(x, y, z + 2, "steel_girder");
                }
                p.set(x, h - 1, z + 1, "steel_girder");
            }
        }
    }

    /**
     * A grain elevator: n silos of radius r in a row along x from x, h high, the head house on
     * top of them; returns the head house's middle x, for the gallery to the quay.
     */
    int elevator(int x, int z, int n, int r, int h) {
        String c = any("silo_stave", "concrete_panel", "minecraft:light_gray_concrete");
        for (int s = 0; s < n; s++) {
            int cx = x + r + s * 2 * r, cz = z + r;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d > r + 0.3) {
                        continue;
                    }
                    for (int y = 1; y <= h; y++) {
                        p.set(cx + dx, y, cz + dz, d > r - 0.8 ? (chance() < 0.05 + hit * 0.05 ? "concrete_panel_stain" : c) : "air");
                    }
                    p.set(cx + dx, h + 1, cz + dz, "concrete_panel");
                }
            }
        }
        int hx0 = x + 2, hx1 = x + n * 2 * r - 3, hz0 = z + r - 3, hz1 = z + r + 3;
        for (int xx = hx0; xx <= hx1; xx++) {
            for (int zz = hz0; zz <= hz1; zz++) {
                for (int y = h + 2; y <= h + 8; y++) {
                    boolean side = xx == hx0 || xx == hx1 || zz == hz0 || zz == hz1;
                    p.set(xx, y, zz, !side ? "air" : y == h + 5 && (xx - hx0) % 3 == 1 ? "window_broken[axis=" + (zz == hz0 || zz == hz1 ? "x" : "z") + "]"
                            : "corrugated_grey");
                }
                p.set(xx, h + 9, zz, "corrugated_rust");
            }
        }
        p.set((hx0 + hx1) / 2, h + 10, z + r, "aviation_light");
        return (hx0 + hx1) / 2;
    }

    /** The covered gallery from (x, y0, z0) along -z to z1, dropping to y1: the conveyor in its tin box, a spout at the end. */
    void gallery(int x, int y0, int z0, int z1, int y1) {
        int n = Math.abs(z0 - z1);
        for (int i = 0; i <= n; i++) {
            int z = z0 - i;
            int y = y0 + (int) Math.round((y1 - y0) * i / (double) n);
            for (int dx = -1; dx <= 1; dx++) {
                p.set(x + dx, y, z, "deck_steel");
                p.set(x + dx, y + 3, z, "corrugated_grey");
            }
            p.set(x - 1, y + 1, z, "corrugated_grey");
            p.set(x + 1, y + 1, z, "corrugated_grey");
            p.set(x - 1, y + 2, z, i % 4 == 0 ? "window_broken[axis=z]" : "corrugated_grey");
            p.set(x + 1, y + 2, z, i % 4 == 2 ? "window[axis=z]" : "corrugated_grey");
            p.set(x, y + 1, z, "rail[axis=z]");
            if (i % 12 == 6) {
                for (int yy = 1; yy < y; yy++) {
                    p.set(x - 1, yy, z, "steel_girder");
                    p.set(x + 1, yy, z, "steel_girder");
                }
            }
        }
        // the loading tower and its spout over the water
        for (int y = 1; y <= y1 + 3; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                p.set(x + dx, y, z1 - 1, "steel_girder");
            }
        }
        member(x, y1, z1 - 2, y1 - 6, z1 - 7, "cr_strut_yellow");
    }

    /** A siding along x from x0 to x1 on row z: ballast three wide, the rails, a buffer stop at each end. */
    void railSpur(int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) {
            for (int k = -1; k <= 1; k++) {
                p.set(x, 0, z + k, chance() < 0.75 ? "minecraft:gravel" : chance() < 0.5 ? "minecraft:coarse_dirt" : "minecraft:andesite");
            }
            if (chance() > 0.02 + hit * 0.04) {
                p.set(x, 1, z, "rail[axis=x]");
            } else if (chance() < 0.5) {
                p.set(x, 1, z, "minecraft:short_grass");
            }
        }
        for (int x : new int[] {x0, x1}) {
            p.set(x, 1, z, "steel_yellow");
            p.set(x, 2, z, "hazard_stripes");
        }
    }

    /**
     * A freight wagon 14 long along x from x on the rails at row z: a boxcar you can climb into,
     * an ore hopper, a tank car or a flat with a box on it.
     */
    void wagon(int x, int z) {
        for (int i = 0; i < 14; i++) {
            for (int k = -1; k <= 1; k++) {
                p.set(x + i, 2, z + k, "deck_steel");
            }
        }
        for (int i : new int[] {1, 2, 11, 12}) {
            p.set(x + i, 1, z - 1, "steel_girder");
            p.set(x + i, 1, z + 1, "steel_girder");
        }
        p.set(x - 1, 2, z, "steel_girder");
        p.set(x + 14, 2, z, "steel_girder");
        switch (rng.nextInt(4)) {
            case 0 -> {
                String clad = any("corrugated_rust", "corrugated_red", "corrugated_green", "corrugated_grey_stain");
                boolean open = chance() < 0.6;
                for (int i = 0; i < 14; i++) {
                    for (int y = 3; y <= 5; y++) {
                        for (int k = -1; k <= 1; k++) {
                            boolean wall = k != 0 || i == 0 || i == 13 || y == 5;
                            boolean door = open && k == 1 && y < 5 && (i == 6 || i == 7);
                            p.set(x + i, y, z + k, wall && !door ? (y == 5 && chance() < hit * 0.15 ? "rust" : clad) : "air");
                        }
                    }
                }
                if (open && chance() < 0.5) {
                    p.set(x + 4, 3, z, chance() < 0.5 ? "crate" : "minecraft:barrel[facing=up,loot=storage]");
                }
            }
            case 1 -> {
                String stuff = any("minecraft:coal_block", "minecraft:gravel", "minecraft:sand", "rubble_pile");
                for (int i = 0; i < 14; i++) {
                    for (int k = -1; k <= 1; k++) {
                        boolean end = i == 0 || i == 13;
                        p.set(x + i, 3, z + k, i % 4 == 2 ? "air" : "rust");
                        p.set(x + i, 4, z + k, k == 0 && !end ? stuff : chance() < 0.3 ? "rust" : "hull_black");
                        if (k == 0 && !end && chance() < 0.6) {
                            p.set(x + i, 5, z + k, stuff);
                        }
                    }
                }
            }
            case 2 -> {
                String skin = any("tank_white", "hull_black", "hull_black", "tank_blue");
                for (int i = 1; i < 13; i++) {
                    p.set(x + i, 3, z, skin);
                    p.set(x + i, 5, z, skin);
                    for (int k = -1; k <= 1; k++) {
                        p.set(x + i, 4, z + k, skin);
                    }
                }
                p.set(x + 7, 6, z, "steel_girder");
                p.set(x + 7, 5, z - 1, "minecraft:iron_bars");
            }
            default -> {
                if (chance() < 0.75) {
                    box(x + 1, 3, z - 1, boxColour(), true);
                }
            }
        }
    }

    /** A heap of bulk (coal, gravel, sand) on (cx, cz), r round and h high. */
    void heap(int cx, int cz, int r, int h, String stuff) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.hypot(dx, dz * 1.3) / r;
                int top = (int) Math.round(h * (1 - d) + (chance() - 0.5));
                for (int y = 1; y <= top; y++) {
                    p.set(cx + dx, y, cz + dz, stuff);
                }
            }
        }
    }

    /** A brick chimney on (cx, cz), radius r and h high, a rust band near the top. */
    void chimney(int cx, int cz, int r, int h) {
        for (int y = 1; y <= h; y++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double d = Math.hypot(dx, dz);
                    if (d <= r + 0.3) {
                        p.set(cx + dx, y, cz + dz, d > r - 0.8 ? (y > h - 3 ? "brick_dark_soot" : y == h - 6 ? "rust" : "brick_red") : "air");
                    }
                }
            }
        }
        p.set(cx, h + 1, cz, "aviation_light");
    }

    /**
     * A saw-tooth factory bw x bd in its frame: brick walls, a row of north lights in the roof,
     * loading doors on the front, machines rusting inside.
     */
    void factory(int bw, int bd) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.4 + hit * 0.4;
        b.soot = chance() < 0.4 ? 0.4 : 0;
        String wall = any("brick_red", "brick_dark", "brick_orange");
        b.shell(0, 0, bw - 1, bd - 1, 1, 7, wall, "floor_concrete", "floor_concrete");
        b.windowRow(2, bw - 3, 3, 0, 3, "north", 2, null);
        b.windowRow(2, bd - 3, 4, 0, 3, "west", 2, null);
        b.windowRow(2, bd - 3, 4, bw - 1, 3, "east", 2, null);
        for (int x = 4; x + 4 < bw; x += 12) {
            for (int k = 0; k < 4; k++) {
                for (int y = 1; y <= 4; y++) {
                    p.set(x + k, y, 0, chance() < 0.3 ? "air" : "garage_door[facing=north]");
                }
            }
        }
        // the saw teeth: each a slope up to the south and a glazed face to the north
        for (int z0 = 0; z0 < bd; z0 += 6) {
            for (int k = 0; k < 6 && z0 + k < bd; k++) {
                int y = 8 + (k * 4) / 6;
                for (int x = 0; x < bw; x++) {
                    p.set(x, y, z0 + k, k == 0 ? "corrugated_grey" : "roof");
                    if (k == 0) {
                        for (int yy = 8; yy <= 11; yy++) {
                            boolean edge = x == 0 || x == bw - 1;
                            p.set(x, yy, z0, edge ? wall : chance() < 0.15 + hit * 0.25 ? "window_broken[axis=x]" : "window[axis=x]");
                        }
                    }
                }
            }
        }
        for (int x = 3; x < bw - 3; x += 5) {
            for (int z = 4; z < bd - 3; z += 6) {
                p.set(x, 1, z, any("minecraft:anvil[facing=north]", "steel_olive", "minecraft:smithing_table", "crate"));
                p.set(x + 1, 1, z, chance() < 0.5 ? "steel_olive" : "air");
            }
        }
        b.lights(1, 1, bw - 2, bd - 2, 6, 0);
        b.mess(1, 1, bw - 2, bd - 2, 1, 0.06);
    }

    /** A warehouse bw x bd in its frame: corrugated walls 8 high, dock doors, racks inside, the trucks left at its doors. */
    void warehouse(int bw, int bd) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.4 + hit * 0.5;
        String clad = any("corrugated_grey", "corrugated_blue", "corrugated_rust", "corrugated_red");
        b.shell(0, 0, bw - 1, bd - 1, 1, 8, clad, "floor_concrete", "floor_concrete");
        for (int i = 0; i * 7 + 8 < bw; i++) {
            int x = 3 + i * 7;
            for (int k = 0; k < 4; k++) {
                for (int y = 1; y <= 4; y++) {
                    p.set(x + k, y, 0, i % 2 == 1 && chance() < 0.6 ? "air" : "garage_door[facing=north]");
                }
            }
            p.set(x + 1, 6, -1, lamp(0.5));
            if (chance() < 0.25) {
                p.vehicle(x + 2, 1, -7, 0, "box_truck,moving_truck,step_van", rng.nextLong());
            }
        }
        b.flat(0, 0, bw - 1, bd - 1, 8, clad, 1, "minecraft:smooth_stone_slab[type=bottom]");
        for (int x = 3; x < bw - 3; x += 4) {
            for (int z = 4; z < bd - 2; z++) {
                for (int y = 1; y <= 3; y++) {
                    double r = chance();
                    p.set(x, y, z, r < 0.35 ? "crate" : r < 0.55 ? "cardboard_box" : r < 0.62 ? "minecraft:barrel[facing=up,loot=storage]" : "air");
                }
                p.set(x, 4, z, "steel_yellow");
            }
        }
        b.lights(1, 1, bw - 2, bd - 2, 7, 0);
    }

    /**
     * An open shed len along x and dep deep: posts, a tin roof at 5, the tables and boxes of a fish
     * market under it, or nets and pots hung up to dry.
     */
    void openShed(int len, int dep, boolean market) {
        for (int i = 0; i < len; i++) {
            for (int k = 0; k < dep; k++) {
                p.set(i, 0, k, chance() < 0.15 ? "concrete_dark_stain" : "floor_concrete");
                p.set(i, 5, k, chance() < 0.04 + hit * 0.1 ? "air" : any("corrugated_grey", "corrugated_rust"));
            }
            if (i % 5 == 0 || i == len - 1) {
                for (int y = 1; y < 5; y++) {
                    p.set(i, y, 0, "steel_girder");
                    p.set(i, y, dep - 1, "steel_girder");
                }
            }
        }
        for (int i = 2; i < len - 2; i += 3) {
            for (int k = 2; k < dep - 2; k += 4) {
                if (market) {
                    p.set(i, 1, k, "store_table");
                    p.setIfAir(i + 1, 1, k, any("minecraft:barrel[facing=up,loot=storage]", "minecraft:white_concrete", "crate", "air"));
                } else {
                    p.set(i, 1, k, any("minecraft:cobweb", "minecraft:scaffolding", "crate", "minecraft:orange_concrete"));
                    p.set(i, 4, k, chance() < 0.5 ? "minecraft:cobweb" : "air");
                }
            }
        }
        p.set(len / 2, 4, 1, lamp(0.5));
    }
}
