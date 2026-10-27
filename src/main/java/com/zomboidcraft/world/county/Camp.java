package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The pieces the army put up, drawn through a {@link Placer} in their own frame (front along
 * local z = 0 facing north, x along the front) like {@link Bldg}'s:
 * <ul>
 * <li>walls and wire: double chain-link with barbed wire on top, hesco lines, shipping
 * containers stacked into a wall, jersey barriers, sandbag nests;</li>
 * <li>the watchtowers on their legs with a floodlight and a sandbagged platform, the guard
 * booth and its barrier at the gate, floodlight masts;</li>
 * <li>canvas: the GP tents in their rows with cots inside, the big white field hospital with its
 * red cross, a tent fallen in;</li>
 * <li>buildings: barracks with their bunks, the headquarters, the mess hall, a quonset hangar,
 * an open vehicle shed;</li>
 * <li>a helipad and the helicopter on it, or what's left of it;</li>
 * <li>the yard: crates under camo nets, fuel tanks in their berm, the radio mast, a row of
 * portable toilets;</li>
 * <li>what the outbreak left: the burn pit, the long trench of body bags, graves.</li>
 * </ul>
 */
final class Camp {
    /** Floodlights: mostly a cold white, some dead. */
    static String flood(SplittableRandom rng, double dead) {
        return rng.nextDouble() < dead ? "minecraft:smooth_quartz" : "minecraft:sea_lantern";
    }

    private final Placer p;
    private final SplittableRandom rng;
    /** How hard the camp was overrun, 0 to 1. */
    final double hit;

    Camp(Placer p, SplittableRandom rng, double hit) {
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

    // ------------------------------------------------------------------ wire and walls

    /**
     * A straight run of fence along x (z0 == z1) or z: chain-link 3 high on posts every 4, a
     * strand of barbed wire along the top, and here and there a panel cut or pushed in.
     */
    void fence(int x0, int z0, int x1, int z1, double breach) {
        boolean alongX = z0 == z1;
        String axis = alongX ? "x" : "z";
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean post = n++ % 4 == 0;
                if (!post && chance() < breach) {
                    if (chance() < 0.5) {
                        p.set(x, 1, z, "barbed_wire[axis=" + axis + "]");  // the mesh down, the wire left lying
                    }
                    continue;
                }
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, post ? "fence_post" : "chainlink[axis=" + axis + "]");
                }
                p.set(x, 4, z, post ? "fence_post" : "barbed_wire[axis=" + axis + "]");
            }
        }
    }

    /** A line of hesco baskets h high along x or z, a gap blown in now and then. */
    void hesco(int x0, int z0, int x1, int z1, int h, double breach) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (chance() < breach) {
                    p.set(x, 1, z, chance() < 0.5 ? "minecraft:coarse_dirt" : "debris_chunk");
                    continue;
                }
                for (int y = 1; y <= h; y++) {
                    p.set(x, y, z, "hesco");
                }
                if (chance() < 0.25) {
                    p.set(x, h + 1, z, chance() < 0.6 ? "minecraft:short_grass" : "minecraft:short_dry_grass");
                }
            }
        }
    }

    private static final String[] BOXES = {"container_red", "container_blue", "container_green", "container_orange",
            "container_white", "container_grey"};

    /**
     * A wall of shipping containers along x (or z), end to end, one or two high (a few three):
     * each box 3 high, its doors at one end, the odd one rusted through.
     */
    void containerWall(int x0, int z0, int x1, int z1, int levels, double gaps) {
        boolean alongX = z0 == z1;
        int len = Math.abs(alongX ? x1 - x0 : z1 - z0) + 1;
        int a = alongX ? Math.min(x0, x1) : Math.min(z0, z1);
        for (int s = 0; s < len; s += 12) {
            int boxLen = Math.min(12, len - s);
            int tiers = levels + (chance() < 0.15 ? 1 : 0);
            for (int t = 0; t < tiers; t++) {
                if (t > 0 && chance() < gaps) {
                    break;
                }
                if (t == 0 && chance() < gaps * 0.5) {
                    break;  // a box missing from the bottom row: the gap they came through
                }
                String box = BOXES[rng.nextInt(BOXES.length)];
                for (int i = 0; i < boxLen; i++) {
                    for (int k = 0; k < 3; k++) {
                        for (int y = 1 + t * 3; y <= 3 + t * 3; y++) {
                            int x = alongX ? a + s + i : x0 + k - 1, z = alongX ? z0 + k - 1 : a + s + i;
                            boolean end = i == 0 || i == boxLen - 1;
                            p.set(x, y, z, end && k == 1 && y < 3 + t * 3 ? box + "_door[facing=" + (alongX ? (i == 0 ? "west" : "east")
                                    : (i == 0 ? "north" : "south")) + "]" : box);
                        }
                    }
                }
            }
        }
    }

    /**
     * Jersey barriers along x or z, every one in line with the row (never one turned across it),
     * the odd one dragged away to leave a gap.
     */
    void jersey(int x0, int z0, int x1, int z1) {
        boolean alongX = z0 == z1;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (chance() < 0.12) {
                    continue;
                }
                p.set(x, 1, z, "jersey_barrier[facing=" + (alongX ? "north" : "east") + "]");
            }
        }
    }

    /** A U of sandbags two high round (x, z), open at the back (+z), a gun's worth of mess inside. */
    void nest(int x, int z) {
        for (int i = -2; i <= 2; i++) {
            for (int y = 1; y <= 2; y++) {
                p.set(x + i, y, z - 2, "sandbags[facing=north]");
            }
        }
        for (int k = -1; k <= 1; k++) {
            for (int y = 1; y <= 2; y++) {
                p.set(x - 2, y, z + k, "sandbags[facing=east]");
                p.set(x + 2, y, z + k, "sandbags[facing=east]");
            }
        }
        if (chance() < 0.6) {
            p.setIfAir(x, 1, z, any("crate", "trash_bag[facing=north]", "minecraft:barrel[facing=up,loot=storage]"));
        }
        if (chance() < 0.3 + hit * 0.4) {
            p.setIfAir(x - 1, 1, z + 1, "blood_splat");
        }
    }

    // ------------------------------------------------------------------ towers and lights

    /**
     * A watchtower over (x, z) to (x + 4, z + 4): four timber legs braced, a ladder, a floor at 7
     * with sandbags round it, a tin roof, and a floodlight on the corner facing out (-z).
     */
    void watchtower(int x, int z) {
        int deck = 7;
        for (int[] c : new int[][] {{0, 0}, {4, 0}, {0, 4}, {4, 4}}) {
            for (int y = 1; y < deck; y++) {
                p.set(x + c[0], y, z + c[1], "minecraft:stripped_spruce_log[axis=y]");
            }
            for (int y = deck + 1; y <= deck + 3; y++) {
                p.set(x + c[0], y, z + c[1], "minecraft:spruce_fence");
            }
        }
        for (int i = 1; i < 4; i++) {
            p.set(x + i, 3, z, "minecraft:spruce_fence");
            p.set(x + i, 3, z + 4, "minecraft:spruce_fence");
            p.set(x, 3, z + i, "minecraft:spruce_fence");
            p.set(x + 4, 3, z + i, "minecraft:spruce_fence");
        }
        for (int i = 0; i <= 4; i++) {
            for (int k = 0; k <= 4; k++) {
                p.set(x + i, deck, z + k, "minecraft:spruce_planks");
                p.set(x + i, deck + 4, z + k, "corrugated_grey");
                boolean edge = i == 0 || k == 0 || i == 4 || k == 4;
                boolean corner = (i == 0 || i == 4) && (k == 0 || k == 4);
                if (edge && !corner && !(i == 2 && k == 4)) {
                    p.set(x + i, deck + 1, z + k, "sandbags[facing=" + (k == 0 || k == 4 ? "north" : "east") + "]");
                }
            }
        }
        // the ladder up the back on its own post, onto the deck through the gap in the sandbags
        for (int y = 1; y < deck; y++) {
            p.set(x + 2, y, z + 4, "minecraft:stripped_spruce_log[axis=y]");
        }
        for (int y = 1; y <= deck; y++) {
            p.set(x + 2, y, z + 5, "minecraft:ladder[facing=south]");
        }
        // the floodlight on its bracket, looking out
        p.set(x + 1, deck + 3, z - 1, flood(rng, 0.25 + hit * 0.3));
        p.set(x + 1, deck + 3, z, "minecraft:iron_bars");
        if (chance() < 0.4) {
            p.set(x + 3, deck + 1, z + 2, "minecraft:barrel[facing=up,loot=storage]");
        }
        if (chance() < hit * 0.5) {
            p.set(x + 1, deck + 1, z + 1, "blood_splat");
        }
    }

    /** A floodlight mast: a steel pole h high with a bar of two lamps facing -z. */
    void floodMast(int x, int z, int h) {
        for (int y = 1; y < h; y++) {
            p.set(x, y, z, "streetlight_pole");
        }
        p.set(x, 0, z, "concrete_dark");
        p.set(x - 1, h, z, flood(rng, 0.35 + hit * 0.3));
        p.set(x, h, z, "steel_olive");
        p.set(x + 1, h, z, flood(rng, 0.35 + hit * 0.3));
    }

    /**
     * A guard booth 4 x 4 at (x, z): windows all round, a door at the back, a flat roof with a
     * lamp under the eave, a stool and the log book on the desk.
     */
    void booth(int x, int z) {
        Bldg b = new Bldg(p.shifted(x, 0, z), rng);
        b.damage = 0.4 + hit * 0.5;
        b.shell(0, 0, 3, 3, 1, 3, "concrete_panel", "floor_concrete", "floor_concrete");
        for (int i = 1; i <= 2; i++) {
            b.window(i, 2, 0, "north", 1, null);
            b.window(0, 2, i, "west", 1, null);
            b.window(3, 2, i, "east", 1, null);
        }
        b.door(1, 1, 3, "flat_door", "south");
        b.flat(0, 0, 3, 3, 3, "concrete_panel", 0, "minecraft:smooth_stone_slab[type=bottom]");
        Placer s = p.shifted(x, 0, z);
        s.set(2, 1, 1, "desk[facing=north]");
        s.set(2, 2, -1, flood(rng, 0.4));
    }

    /**
     * A barrier arm at (x, z) across the lane of length len along x: the striped arm on its
     * post, raised (standing up) or down across the road.
     */
    void boom(int x, int z, int len, boolean down) {
        p.set(x, 1, z, "steel_yellow");
        if (down) {
            for (int i = 1; i <= len; i++) {
                p.set(x + i, 1, z, i % 2 == 0 ? "steel_red" : "minecraft:white_concrete");
            }
        } else {
            for (int y = 2; y <= Math.min(len, 6); y++) {
                p.set(x, y, z, y % 2 == 0 ? "steel_red" : "minecraft:white_concrete");
            }
        }
    }

    // ------------------------------------------------------------------ canvas

    /**
     * A GP tent 6 wide (x) and len deep from (x, z): canvas walls 2 high, a pitched roof, the
     * door flap open at the front, cots down both sides. Fallen in, it's a heap of canvas.
     */
    void tent(int x, int z, int len, String canvas, boolean cots) {
        if (chance() < 0.15 + hit * 0.35) {
            // blown down or pulled down: canvas over whatever was inside
            for (int i = 0; i < 6; i++) {
                for (int k = 0; k < len; k++) {
                    if (chance() < 0.85) {
                        p.set(x + i, 1, z + k, chance() < 0.2 ? "debris_chunk" : canvas);
                    }
                }
            }
            p.set(x + 2 + rng.nextInt(2), 2, z + rng.nextInt(len), canvas);
            return;
        }
        for (int k = 0; k < len; k++) {
            boolean end = k == 0 || k == len - 1;
            for (int i = 0; i < 6; i++) {
                p.set(x + i, 0, z + k, chance() < 0.7 ? "minecraft:spruce_planks" : "minecraft:coarse_dirt");
                boolean side = i == 0 || i == 5;
                if (side) {
                    p.set(x + i, 1, z + k, canvas);
                    p.set(x + i, 2, z + k, canvas);
                } else if (end && !(k == 0 && (i == 2 || i == 3))) {
                    for (int y = 1; y <= (i == 1 || i == 4 ? 3 : 4); y++) {
                        p.set(x + i, y, z + k, canvas);
                    }
                } else {
                    p.set(x + i, 1, z + k, "air");
                    p.set(x + i, 2, z + k, "air");
                }
            }
            p.set(x, 3, z + k, canvas);
            p.set(x + 5, 3, z + k, canvas);
            p.set(x + 1, 4, z + k, canvas);
            p.set(x + 4, 4, z + k, canvas);
            p.set(x + 2, 5, z + k, canvas);
            p.set(x + 3, 5, z + k, canvas);
            if (!end) {
                p.set(x + 1, 3, z + k, "air");
                p.set(x + 4, 3, z + k, "air");
                p.set(x + 2, 3, z + k, "air");
                p.set(x + 3, 3, z + k, "air");
                p.set(x + 2, 4, z + k, "air");
                p.set(x + 3, 4, z + k, "air");
            }
        }
        // guy ropes out to pegs
        for (int k = 1; k < len - 1; k += 3) {
            p.setIfAir(x - 1, 1, z + k, "minecraft:tripwire");
            p.setIfAir(x + 6, 1, z + k, "minecraft:tripwire");
        }
        if (cots) {
            for (int k = 2; k + 1 < len - 1; k += 3) {
                for (int i : new int[] {1, 4}) {
                    if (chance() < 0.8) {
                        p.set(x + i, 1, z + k, "minecraft:green_bed[part=head,facing=north]");
                        p.set(x + i, 1, z + k + 1, "minecraft:green_bed[part=foot,facing=north]");
                    }
                }
            }
            p.set(x + 2, 1, z + len - 2, any("crate", "minecraft:barrel[facing=up,loot=storage]", "cardboard_box"));
        }
        if (chance() < 0.3 + hit * 0.4) {
            p.setIfAir(x + 2, 1, z + 1 + rng.nextInt(Math.max(1, len - 2)), chance() < 0.5 ? "blood_splat" : "trash_bag[facing=north]");
        }
    }

    /**
     * The field hospital: a long white tent bw wide (x) and len deep, walls 3 high, a pitched
     * roof with a red cross on it and on the front, rows of beds and the screens between them.
     */
    void hospital(int x, int z, int bw, int len) {
        String c = "minecraft:white_wool", c2 = "minecraft:light_gray_wool";
        int mid = bw / 2;
        for (int k = 0; k < len; k++) {
            boolean end = k == 0 || k == len - 1;
            for (int i = 0; i < bw; i++) {
                p.set(x + i, 0, z + k, "floor_lino");
                boolean side = i == 0 || i == bw - 1;
                int rise = Math.min(i, bw - 1 - i);
                int top = 3 + rise / 2;
                for (int y = 1; y <= top; y++) {
                    boolean wall = side || end && !(k == 0 && Math.abs(i - mid) <= 1 && y <= 3);
                    if (wall || y == top) {
                        p.set(x + i, y, z + k, chance() < 0.8 ? c : c2);
                    } else {
                        p.set(x + i, y, z + k, "air");
                    }
                }
            }
        }
        // the red cross on the front and on the roof
        int fy = 3 + mid / 2 - 1;
        for (int d = -1; d <= 1; d++) {
            p.set(x + mid + d, fy, z - 1 + 1, "minecraft:red_wool");
            p.set(x + mid, fy + d, z, "minecraft:red_wool");
        }
        int ry = 3 + mid / 2, rz = z + len / 2;
        for (int d = -2; d <= 2; d++) {
            p.set(x + mid + d, ry, rz, "minecraft:red_wool");
            p.set(x + mid, ry, rz + d, "minecraft:red_wool");
        }
        // beds down both walls, screens between, a crash cart, the mess
        for (int k = 2; k < len - 2; k += 3) {
            for (int side = 0; side < 2; side++) {
                int bx = side == 0 ? x + 1 : x + bw - 2;
                if (chance() < 0.85) {
                    p.set(bx, 1, z + k, "minecraft:white_bed[part=head,facing=" + (side == 0 ? "west" : "east") + "]");
                    p.set(side == 0 ? bx + 1 : bx - 1, 1, z + k, "minecraft:white_bed[part=foot,facing=" + (side == 0 ? "west" : "east") + "]");
                }
                if (chance() < 0.5) {
                    p.set(bx, 1, z + k + 1, "minecraft:white_stained_glass_pane");
                }
                if (chance() < 0.25 + hit * 0.4) {
                    p.setIfAir(side == 0 ? bx + 2 : bx - 2, 1, z + k, "blood_splat");
                }
            }
        }
        for (int k = 3; k < len - 3; k += 6) {
            p.setIfAir(x + mid, 1, z + k, any("crate", "minecraft:barrel[facing=up,loot=storage]", "cardboard_box", "trash_bag[facing=east]"));
        }
    }

    // ------------------------------------------------------------------ buildings

    /**
     * A barracks bw x bd: one storey of clapboard or block under a gable roof, doors at both ends
     * and the middle of the front, bunks down both walls, lockers between.
     */
    void barracks(int bw, int bd, String wall, String roof) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.3 + hit * 0.6;
        b.soot = hit > 0.6 && chance() < 0.4 ? 0.5 : 0;
        b.boarded = 0.15 + hit * 0.2;
        b.shell(0, 0, bw - 1, bd - 1, 1, 4, wall, "floor_lino", "floor_lino");
        b.windowRow(3, bw - 4, 3, 0, 2, "north", 1, null);
        b.windowRow(3, bw - 4, 3, bd - 1, 2, "south", 1, null);
        b.door(bw / 2, 1, 0, "flat_door", "north");
        b.door(0, 1, bd / 2, "flat_door", "west");
        b.door(bw - 1, 1, bd / 2, "flat_door", "east");
        b.gable(0, bw - 1, 0, bd - 1, 4, roof, false, wall, 1);
        for (int x = 2; x < bw - 2; x += 3) {
            if (Math.abs(x - bw / 2) <= 1) {
                continue;
            }
            for (int[] side : new int[][] {{1, 2}, {bd - 2, bd - 3}}) {
                if (chance() < 0.85) {
                    String f = side[0] == 1 ? "south" : "north";
                    p.set(x, 1, side[0], "minecraft:green_bed[part=head,facing=" + (side[0] == 1 ? "north" : "south") + "]");
                    p.set(x, 1, side[1], "minecraft:green_bed[part=foot,facing=" + (side[0] == 1 ? "north" : "south") + "]");
                    if (chance() < 0.4) {
                        p.set(x + 1, 1, side[0], "filing_cabinet[facing=" + f + "]");
                    }
                }
            }
        }
        b.lights(1, 1, bw - 2, bd - 2, 3, 0);
        b.mess(1, 1, bw - 2, bd - 2, 1, 0.04 + hit * 0.06);
        // sandbags across a door where they held it
        if (chance() < hit * 0.6) {
            for (int i = -1; i <= 1; i++) {
                p.set(bw / 2 + i, 1, -2, "sandbags[facing=north]");
            }
        }
    }

    /**
     * The headquarters bw x bd: two storeys of concrete with rows of windows, an entrance under
     * a slab canopy, a flat roof with the antennas, offices inside, flagpoles out front.
     */
    void headquarters(int bw, int bd) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.3 + hit * 0.5;
        b.boarded = 0.2;
        b.shell(0, 0, bw - 1, bd - 1, 2, 4, "concrete_panel", "office_floor", "office_floor");
        for (int y : new int[] {2, 6}) {
            b.windowRow(2, bw - 3, 2, 0, y, "north", 2, null);
            b.windowRow(2, bw - 3, 3, bd - 1, y, "south", 2, null);
            b.windowRow(2, bd - 3, 3, 0, y, "west", 2, null);
            b.windowRow(2, bd - 3, 3, bw - 1, y, "east", 2, null);
        }
        int cx = bw / 2;
        b.doors(cx - 1, 1, 0, "shop_door", "north");
        for (int x = cx - 3; x <= cx + 2; x++) {
            for (int z = -3; z <= -1; z++) {
                p.set(x, 4, z, "minecraft:smooth_stone_slab[type=top]");
                p.set(x, 0, z, "floor_concrete");
            }
        }
        for (int x : new int[] {cx - 3, cx + 2}) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, -3, "concrete_panel");
            }
        }
        b.flat(0, 0, bw - 1, bd - 1, 8, "concrete_panel", 1, "minecraft:smooth_stone_slab[type=bottom]");
        // antennas and a dish on the roof
        for (int i = 0; i < 3; i++) {
            int ax = 2 + rng.nextInt(Math.max(1, bw - 4)), az = 2 + rng.nextInt(Math.max(1, bd - 4));
            for (int y = 9; y < 9 + 3 + rng.nextInt(5); y++) {
                p.set(ax, y, az, "minecraft:iron_bars");
            }
        }
        p.set(bw - 4, 9, bd - 4, "minecraft:smooth_quartz");
        p.set(bw - 4, 10, bd - 4, "minecraft:smooth_quartz_slab[type=bottom]");
        // desks and cabinets in rows behind the windows, a stair up at the back
        for (int y : new int[] {1, 5}) {
            for (int x = 2; x < bw - 2; x += 4) {
                for (int z = 2; z < bd - 3; z += 4) {
                    if (chance() < 0.75) {
                        p.set(x, y, z, "desk[facing=north]");
                        p.set(x, y, z + 1, "office_chair[facing=north]");
                    }
                    if (chance() < 0.3) {
                        p.set(x + 2, y, z, "filing_cabinet[facing=north]");
                    }
                }
            }
        }
        b.stair(bw - 3, bd - 3, 1, 4, "north", "stairs_concrete");
        b.lights(1, 1, bw - 2, bd - 2, 3, 0);
        b.lights(1, 1, bw - 2, bd - 2, 7, 0);
        b.mess(1, 1, bw - 2, bd - 2, 1, 0.05 + hit * 0.05);
        b.mess(1, 1, bw - 2, bd - 2, 5, 0.04);
        new Civic(p, rng).flagpole(cx - 6, -6, 11);
        new Civic(p, rng).flagpole(cx + 5, -6, 11);
    }

    /** The mess hall bw x bd: long tables in rows, the serving line at the back, a gable roof. */
    void messHall(int bw, int bd) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.4 + hit * 0.5;
        b.shell(0, 0, bw - 1, bd - 1, 1, 5, "brick_buff", "floor_lino", "floor_lino");
        b.windowRow(2, bw - 3, 3, 0, 2, "north", 2, null);
        b.windowRow(2, bw - 3, 3, bd - 1, 2, "south", 2, null);
        b.doors(bw / 2 - 1, 1, 0, "shop_door", "north");
        b.gable(0, bw - 1, 0, bd - 1, 5, "minecraft:deepslate_tile", false, "brick_buff", 1);
        for (int z = 3; z < bd - 5; z += 3) {
            for (int x = 2; x < bw - 2; x++) {
                if (x % 7 == 0) {
                    continue;
                }
                if (chance() < 0.9) {
                    p.set(x, 1, z, "minecraft:spruce_slab[type=top]");
                }
                if (chance() < 0.3) {
                    p.set(x, 1, z - 1, "office_chair[facing=south]");
                }
            }
        }
        for (int x = 2; x < bw - 2; x++) {
            p.set(x, 1, bd - 3, x % 5 == 0 ? "minecraft:smoker[facing=north]" : "minecraft:smooth_stone_slab[type=top]");
        }
        b.lights(1, 1, bw - 2, bd - 2, 4, 0);
        b.mess(1, 1, bw - 2, bd - 2, 1, 0.08 + hit * 0.06);
    }

    /**
     * A quonset hangar r in radius and len deep: corrugated arches from (x, z), the back closed,
     * the front open with its doors half drawn, a concrete floor.
     */
    void quonset(int x, int z, int r, int len, String skin) {
        for (int k = 0; k < len; k++) {
            for (int dx = -r; dx <= r; dx++) {
                p.set(x + r + dx, 0, z + k, "floor_concrete");
                for (int y = 1; y <= r + 1; y++) {
                    double dist = Math.sqrt(dx * dx + (y - 0.5) * (y - 0.5));
                    boolean shell = dist >= r - 0.5 && dist < r + 0.6;
                    boolean inside = dist < r - 0.5;
                    if (shell) {
                        p.set(x + r + dx, y, z + k, chance() < 0.05 + hit * 0.08 ? "air" : skin);
                    } else if (inside && k == len - 1) {
                        p.set(x + r + dx, y, z + k, skin);
                    } else if (inside) {
                        p.set(x + r + dx, y, z + k, "air");
                    }
                }
            }
        }
        // the doors, half across the front opening
        for (int dx = -r + 1; dx <= r - 1; dx++) {
            if (Math.abs(dx) < r / 2) {
                continue;
            }
            for (int y = 1; y <= r; y++) {
                if (Math.sqrt(dx * dx + (y - 0.5) * (y - 0.5)) < r - 0.5) {
                    p.set(x + r + dx, y, z, "garage_door[facing=north]");
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            p.setIfAir(x + 2 + rng.nextInt(2 * r - 3), 1, z + 3 + rng.nextInt(Math.max(1, len - 6)),
                    any("crate", "minecraft:barrel[facing=up,loot=storage]", "tire[axis=x]", "minecraft:anvil[facing=north]"));
        }
        p.set(x + r, r + 1, z - 1, flood(rng, 0.4));
    }

    /** An open vehicle shed len along x and 9 deep: steel posts, a tin roof at 5, an oil-stained floor. */
    void shed(int x, int z, int len) {
        for (int i = 0; i < len; i++) {
            for (int k = 0; k < 9; k++) {
                p.set(x + i, 0, z + k, chance() < 0.15 ? "concrete_dark_stain" : "floor_concrete");
                p.set(x + i, 5, z + k, chance() < 0.04 + hit * 0.1 ? "air" : "corrugated_grey");
            }
            if (i % 6 == 0 || i == len - 1) {
                for (int y = 1; y < 5; y++) {
                    p.set(x + i, y, z, "steel_olive");
                    p.set(x + i, y, z + 8, "steel_olive");
                }
            }
        }
        for (int i = 2; i + 3 < len; i += 6) {
            if (chance() < 0.75) {
                p.vehicle(x + i + 1.5, 1, z + 4.5, 0, any("jeep", "jeep_open", "jeep", "box_truck", "ambulance"), rng.nextLong());
            }
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(x + rng.nextInt(len), 1, z + 8, any("tire[axis=x]", "minecraft:barrel[facing=up,loot=storage]", "crate"));
        }
    }

    // ------------------------------------------------------------------ air

    /** A helipad 15 x 15 from (x, z): concrete, a yellow ring, the H. */
    void helipad(int x, int z) {
        for (int i = 0; i < 15; i++) {
            for (int k = 0; k < 15; k++) {
                double dist = Math.hypot(i - 7, k - 7);
                String m = chance() < 0.12 ? "floor_concrete_crack" : "floor_concrete";
                if (dist >= 6 && dist < 7) {
                    m = "minecraft:yellow_concrete";
                }
                boolean h = (i == 4 || i == 10) && k >= 4 && k <= 10 || k == 7 && i > 4 && i < 10;
                if (h) {
                    m = "minecraft:white_concrete";
                }
                p.set(x + i, 0, z + k, m);
                p.set(x + i, 1, z + k, "air");
            }
        }
        for (int[] c : new int[][] {{0, 0}, {14, 0}, {0, 14}, {14, 14}}) {
            p.set(x + c[0], 1, z + c[1], flood(rng, 0.6));
        }
    }

    /**
     * A transport helicopter with its nose at -z over (cx, cz): the olive body with its cockpit
     * glass and open doors, the tail boom and fin, the rotor head and four blades. A wreck is
     * burnt black on its side of the pad, blades snapped, the tail broken off.
     */
    void helicopter(int cx, int cz, boolean wreck) {
        String hull = wreck ? any("minecraft:blackstone", "minecraft:polished_blackstone") : "steel_olive";
        String dark = wreck ? "minecraft:black_concrete" : "minecraft:gray_concrete";
        // cabin: 3 wide, 3 high, 7 long, doors open amidships
        for (int k = 0; k < 7; k++) {
            for (int i = -1; i <= 1; i++) {
                for (int y = 1; y <= 3; y++) {
                    boolean side = i != 0;
                    boolean door = side && y <= 2 && k >= 2 && k <= 4;
                    boolean inside = !side && y <= 2 && k > 0;
                    if (door || inside) {
                        p.set(cx + i, y, cz + k, "air");
                        continue;
                    }
                    p.set(cx + i, y, cz + k, wreck && chance() < 0.25 ? "debris_chunk" : hull);
                }
            }
        }
        // the cockpit: a sloped glass nose
        for (int i = -1; i <= 1; i++) {
            p.set(cx + i, 1, cz - 1, hull);
            p.set(cx + i, 2, cz - 1, wreck ? "air" : "minecraft:black_stained_glass");
            p.set(cx + i, 1, cz - 2, dark);
        }
        p.set(cx, 3, cz - 1, hull);
        // tail boom, fin and the little rotor
        int tail = wreck ? 4 : 8;
        for (int k = 7; k < 7 + tail; k++) {
            p.set(cx, 2, cz + k, hull);
            if (k < 9) {
                p.set(cx, 3, cz + k, hull);
            }
        }
        if (!wreck) {
            p.set(cx, 3, cz + 14, hull);
            p.set(cx, 4, cz + 14, hull);
            p.set(cx, 5, cz + 14, hull);
            p.set(cx + 1, 4, cz + 14, "minecraft:iron_bars");
            p.set(cx + 1, 3, cz + 15, "minecraft:iron_bars");
            p.set(cx + 1, 5, cz + 13, "minecraft:iron_bars");
        } else {
            // the broken tail lying off to one side
            for (int k = 0; k < 5; k++) {
                p.set(cx + 3 + k / 3, 1, cz + 12 + k, hull);
            }
        }
        // rotor head and blades
        p.set(cx, 4, cz + 3, dark);
        p.set(cx, 5, cz + 3, "minecraft:iron_bars");
        int blade = wreck ? 3 : 8;
        for (int s = 1; s <= blade; s++) {
            String b = "minecraft:polished_blackstone_slab[type=bottom]";
            p.set(cx + s, 6, cz + 3, b);
            p.set(cx - s, 6, cz + 3, b);
            if (!wreck || s < 2) {
                p.set(cx, 6, cz + 3 + s, b);
                p.set(cx, 6, cz + 3 - s, b);
            }
        }
        if (wreck) {
            for (int i = 0; i < 12; i++) {
                int x = cx - 5 + rng.nextInt(11), z = cz - 4 + rng.nextInt(20);
                p.setIfAir(x, 1, z, chance() < 0.5 ? "debris_chunk" : "minecraft:polished_blackstone_slab[type=bottom]");
                if (chance() < 0.6) {
                    p.set(x, 0, z, chance() < 0.5 ? "minecraft:black_concrete_powder" : "minecraft:coal_block");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the yard

    /** Pallets of crates from (x, z), w x d, under a camo net on posts at 4. */
    void crates(int x, int z, int w, int d) {
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                boolean aisle = i % 4 == 3;
                if (!aisle && chance() < 0.8) {
                    int h = 1 + rng.nextInt(3);
                    for (int y = 1; y <= h; y++) {
                        p.set(x + i, y, z + k, chance() < 0.65 ? "crate" : chance() < 0.5 ? "cardboard_box"
                                : "minecraft:barrel[facing=up,loot=storage]");
                    }
                }
                if (chance() > hit * 0.3) {
                    p.set(x + i, 4, z + k, "camo_net");
                }
            }
        }
        for (int[] c : new int[][] {{-1, -1}, {w, -1}, {-1, d}, {w, d}, {w / 2, -1}, {w / 2, d}}) {
            for (int y = 1; y <= 4; y++) {
                p.set(x + c[0], y, z + c[1], "minecraft:spruce_fence");
            }
        }
    }

    /** Fuel tanks in a row along x from (x, z), round, white, in an earth berm with a pump. */
    void fuelTanks(int x, int z, int n) {
        int r = 3;
        for (int t = 0; t < n; t++) {
            int cx = x + r + 1 + t * (2 * r + 3), cz = z + r + 1;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.hypot(dx, dz);
                    if (dist <= r + 0.3) {
                        for (int y = 1; y <= 5; y++) {
                            p.set(cx + dx, y, cz + dz, dist > r - 0.8 || y == 5 ? (y == 5 ? "minecraft:smooth_quartz_slab[type=bottom]"
                                    : chance() < 0.15 ? "minecraft:light_gray_concrete" : "minecraft:white_concrete") : "air");
                        }
                    }
                }
            }
            for (int y = 1; y <= 6; y++) {
                p.set(cx + r + 1, y, cz, "minecraft:ladder[facing=east]");
            }
        }
        int bw = n * (2 * r + 3) + 1, bd = 2 * r + 3;
        for (int i = -1; i <= bw; i++) {
            p.set(x + i, 1, z - 1, "minecraft:coarse_dirt");
            p.set(x + i, 1, z + bd, "minecraft:coarse_dirt");
        }
        for (int k = 0; k < bd; k++) {
            p.set(x - 1, 1, z + k, "minecraft:coarse_dirt");
            p.set(x + bw, 1, z + k, "minecraft:coarse_dirt");
        }
        p.set(x + bw / 2, 1, z - 3, "gas_pump[facing=north]");
        p.set(x + 1, 1, z - 2, "sign_post");
    }

    /** A lattice radio mast h high at (x, z): two-by-two of bars, braced, a dead red lamp on top. */
    void mast(int x, int z, int h) {
        for (int y = 1; y <= h; y++) {
            for (int[] c : new int[][] {{0, 0}, {1, 0}, {0, 1}, {1, 1}}) {
                p.set(x + c[0], y, z + c[1], y % 4 == 0 ? "steel_girder" : "minecraft:iron_bars");
            }
        }
        p.set(x, h + 1, z, "minecraft:iron_bars");
        p.set(x, h + 2, z, "minecraft:iron_bars");
        p.set(x, h + 3, z, "minecraft:red_concrete");
        p.set(x + 1, 0, z + 1, "concrete_dark");
        p.set(x - 1, 1, z + 3, "minecraft:smooth_stone");
    }

    /** A row of n portable toilets along x from (x, z), doors facing -z, one on its side. */
    void toilets(int x, int z, int n) {
        for (int t = 0; t < n; t++) {
            int tx = x + t * 2;
            if (chance() < 0.12 + hit * 0.2) {
                p.set(tx, 1, z, "minecraft:blue_concrete");
                p.set(tx, 1, z + 1, "minecraft:blue_concrete");
                continue;  // knocked over
            }
            p.set(tx, 1, z, "minecraft:dark_oak_door[facing=south,half=lower,hinge=left,open=" + (chance() < 0.4) + "]");
            p.set(tx, 2, z, "minecraft:dark_oak_door[facing=south,half=upper,hinge=left,open=false]");
            p.set(tx, 3, z, "minecraft:blue_concrete");
            p.set(tx, 1, z + 1, "minecraft:blue_concrete");
            p.set(tx, 2, z + 1, "minecraft:blue_concrete");
            p.set(tx, 3, z + 1, "minecraft:white_concrete");
        }
    }

    /**
     * The burn pit centred on (cx, cz), radius r: scorched ground and ash, a ring of blackened
     * earth thrown up, bone and the half-burnt bags, the jerrycans that fed it.
     */
    void burnPit(int cx, int cz, int r) {
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double dist = Math.hypot(dx, dz) + chance() * 0.8;
                int x = cx + dx, z = cz + dz;
                if (dist < r) {
                    p.set(x, 0, z, any("minecraft:soul_soil", "minecraft:black_concrete_powder", "minecraft:gray_concrete_powder",
                            "minecraft:coal_block"));
                    p.set(x, 1, z, "air");
                    double q = chance();
                    if (q < 0.18) {
                        p.set(x, 1, z, "minecraft:bone_block[axis=" + (chance() < 0.5 ? "x" : "z") + "]");
                    } else if (q < 0.3) {
                        p.set(x, 1, z, "trash_bag[facing=" + any("north", "east") + "]");
                    } else if (q < 0.36) {
                        p.set(x, 1, z, "debris_chunk");
                    }
                } else if (dist < r + 1.5) {
                    p.set(x, 0, z, "minecraft:coarse_dirt");
                    p.set(x, 1, z, chance() < 0.6 ? "minecraft:coarse_dirt" : "air");
                }
            }
        }
        p.set(cx, 1, cz, "minecraft:campfire[lit=" + (chance() < 0.2) + "]");
        for (int i = 0; i < 3; i++) {
            p.setIfAir(cx + r + 2, 1, cz - 1 + i, "minecraft:barrel[facing=up]");
        }
    }

    /**
     * The trench: a pit len long along x and 4 wide from (x, z), body bags laid in rows, lime
     * thrown over, the spoil heaped along one side and the excavator's tracks.
     */
    void trench(int x, int z, int len) {
        for (int i = 0; i < len; i++) {
            for (int k = 0; k < 4; k++) {
                p.set(x + i, 0, z + k, "air");
                p.set(x + i, -1, z + k, chance() < 0.3 ? "minecraft:white_concrete_powder" : "minecraft:coarse_dirt");
                if (i < len * 0.7 && chance() < 0.75) {
                    p.set(x + i, -1, z + k, "trash_bag[facing=" + (k % 2 == 0 ? "east" : "north") + "]");
                    p.set(x + i, -2, z + k, "minecraft:coarse_dirt");
                }
            }
            p.set(x + i, 1, z - 1, "minecraft:coarse_dirt");
            p.set(x + i, 1, z - 2, chance() < 0.6 ? "minecraft:coarse_dirt" : "minecraft:dirt");
            if (chance() < 0.5) {
                p.set(x + i, 2, z - 2, "minecraft:coarse_dirt");
            }
        }
        // the finished end: filled in, a row of markers
        for (int i = len; i < len + 10; i++) {
            for (int k = 0; k < 4; k++) {
                p.set(x + i, 0, z + k, "minecraft:rooted_dirt");
            }
            if (i % 2 == 0) {
                p.set(x + i, 1, z + 1, "minecraft:birch_fence");
            }
        }
    }

    /** A grave: a mound and a cross of two sticks, a helmet on some. */
    void grave(int x, int z) {
        p.set(x, 0, z, "minecraft:rooted_dirt");
        p.set(x, 0, z + 1, "minecraft:rooted_dirt");
        p.set(x, 1, z - 1, "minecraft:birch_fence");
        if (chance() < 0.4) {
            p.set(x, 2, z - 1, "minecraft:birch_button[face=floor,facing=north]");
        }
        if (chance() < 0.3) {
            p.setIfAir(x, 1, z + 1, chance() < 0.5 ? "minecraft:poppy" : "minecraft:dandelion");
        }
    }

    // ------------------------------------------------------------------ ground, training, stores

    /** Yard ground from (x0, z0) to (x1, z1): gravel churned to dirt and mud in the ruts, a weed or two. */
    void yard(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                double r = chance();
                p.set(x, 0, z, r < 0.5 ? "minecraft:gravel" : r < 0.74 ? "minecraft:coarse_dirt" : r < 0.86 ? "minecraft:dirt_path"
                        : r < 0.94 ? "minecraft:mud" : "minecraft:packed_mud");
                if (r > 0.975) {
                    p.setIfAir(x, 1, z, chance() < 0.6 ? "minecraft:short_grass" : "weeds");
                }
            }
        }
    }

    /**
     * The assault course, a churned track len along x and 11 deep from (x, z): log hurdles, the
     * plank wall, the tyre run, monkey bars, the wire crawl and the rope frame, over and over.
     */
    void assaultCourse(int x, int z, int len) {
        for (int i = 0; i < len; i++) {
            for (int k = 0; k < 11; k++) {
                double r = chance();
                p.set(x + i, 0, z + k, k == 0 || k == 10 ? "minecraft:grass_block"
                        : r < 0.6 ? "minecraft:coarse_dirt" : r < 0.8 ? "minecraft:dirt_path" : "minecraft:mud");
            }
        }
        int kind = rng.nextInt(6);
        for (int i = 3; i + 5 < len; i += 8, kind = (kind + 1) % 6) {
            int a = x + i;
            switch (kind) {
                case 0 -> {
                    // hurdles: logs across on posts, two of them
                    for (int h = 0; h < 2; h++) {
                        for (int k : new int[] {1, 5, 9}) {
                            p.set(a + h * 3, 1, z + k, "minecraft:spruce_fence");
                        }
                        for (int k = 1; k <= 9; k++) {
                            p.set(a + h * 3, 2, z + k, "minecraft:stripped_spruce_log[axis=z]");
                        }
                    }
                }
                case 1 -> {
                    // the wall, a platform behind to drop off
                    for (int k = 1; k <= 9; k++) {
                        for (int y = 1; y <= 3; y++) {
                            p.set(a + 1, y, z + k, chance() < 0.06 * (1 + hit) ? "air" : "minecraft:spruce_planks");
                        }
                        p.set(a + 2, 1, z + k, "minecraft:spruce_slab[type=bottom]");
                    }
                }
                case 2 -> {
                    // the tyre run
                    for (int i2 = 0; i2 < 5; i2++) {
                        for (int k = 1; k <= 9; k += 2) {
                            p.set(a + i2, 1, z + k + (i2 % 2), "tire[axis=z]");
                        }
                    }
                }
                case 3 -> {
                    // monkey bars on four posts
                    for (int[] c : new int[][] {{0, 3}, {5, 3}, {0, 7}, {5, 7}}) {
                        for (int y = 1; y <= 4; y++) {
                            p.set(a + c[0], y, z + c[1], "minecraft:stripped_spruce_log[axis=y]");
                        }
                    }
                    for (int i2 = 1; i2 < 5; i2++) {
                        p.set(a + i2, 4, z + 3, "minecraft:iron_bars");
                        p.set(a + i2, 4, z + 7, "minecraft:iron_bars");
                    }
                    for (int k = 4; k < 7; k += 2) {
                        p.set(a, 4, z + k, "minecraft:iron_bars");
                    }
                }
                case 4 -> {
                    // the crawl: barbed wire strung low over the mud
                    for (int i2 = 0; i2 < 6; i2++) {
                        for (int k = 1; k <= 9; k++) {
                            p.set(a + i2, 0, z + k, "minecraft:mud");
                            if (i2 % 3 == 0 && (k == 1 || k == 9)) {
                                p.set(a + i2, 1, z + k, "fence_post");
                            } else if (chance() < 0.85) {
                                p.set(a + i2, 2, z + k, "barbed_wire[axis=x]");
                            }
                        }
                        p.set(a + i2, 2, z + 1, "barbed_wire[axis=x]");
                    }
                }
                default -> {
                    // the rope frame: a log gantry six high, ropes hanging
                    for (int k : new int[] {1, 9}) {
                        for (int y = 1; y <= 6; y++) {
                            p.set(a + 2, y, z + k, "minecraft:stripped_spruce_log[axis=y]");
                        }
                    }
                    for (int k = 2; k <= 8; k++) {
                        p.set(a + 2, 6, z + k, "minecraft:stripped_spruce_log[axis=z]");
                    }
                    for (int k = 3; k <= 7; k += 2) {
                        int drop = 2 + rng.nextInt(3);
                        for (int y = 6 - drop; y < 6; y++) {
                            p.set(a + 2, y, z + k, "minecraft:iron_chain[axis=y]");
                        }
                    }
                }
            }
        }
    }

    /**
     * A firing range w wide (x) and d deep from (x, z): the firing line of sandbag rests at the
     * front with the brass where it fell, numbered lanes, target frames, and the earth berm.
     */
    void range(int x, int z, int w, int d) {
        for (int i = 0; i < w; i++) {
            for (int k = 0; k < d; k++) {
                if (k < 4) {
                    p.set(x + i, 0, z + k, chance() < 0.7 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                }
            }
            // the berm: earth heaped four high at the back
            for (int k = d - 5; k < d; k++) {
                int h = Math.min(k - (d - 6), d - k + 1);
                for (int y = 1; y <= Math.min(4, h); y++) {
                    p.set(x + i, y, z + k, y == Math.min(4, h) && chance() < 0.6 ? "minecraft:grass_block" : "minecraft:coarse_dirt");
                }
            }
        }
        for (int lane = 0; 2 + lane * 4 < w - 1; lane++) {
            int lx = x + 2 + lane * 4;
            p.set(lx - 1, 1, z + 1, "sandbags[facing=north]");
            p.set(lx, 1, z + 1, "sandbags[facing=north]");
            p.set(lx + 1, 1, z + 1, "sandbags[facing=north]");
            if (chance() < 0.5) {
                p.set(lx, 1, z + 2, chance() < 0.5 ? "debris_chunk" : "crate");
            }
            p.set(lx - 2, 1, z, "minecraft:spruce_fence");
            // the target frame down range, some shot to bits
            int tz = z + d - 7;
            if (chance() < 0.8) {
                p.set(lx - 1, 1, tz, "minecraft:spruce_fence");
                p.set(lx + 1, 1, tz, "minecraft:spruce_fence");
                p.set(lx - 1, 2, tz, "minecraft:spruce_fence");
                p.set(lx + 1, 2, tz, "minecraft:spruce_fence");
                p.set(lx, 2, tz, "minecraft:target");
                p.set(lx, 3, tz, chance() < 0.7 ? "minecraft:target" : "air");
            } else {
                p.set(lx, 1, tz, "minecraft:target");
            }
        }
        // the red flag at the end of the line, the control hut
        for (int y = 1; y <= 6; y++) {
            p.set(x - 2, y, z + 1, "minecraft:spruce_fence");
        }
        p.set(x - 1, 6, z + 1, "minecraft:red_wool");
        p.set(x - 1, 5, z + 1, chance() < 0.5 ? "minecraft:red_wool" : "air");
        booth(x - 6, z - 2);
    }

    /**
     * The armoury bw x bd: a squat block building, one steel door, bars on its slits of windows,
     * gun racks and ammunition shelves inside; broken into, more often than not.
     */
    void armoury(int bw, int bd) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.15 + hit * 0.3;
        b.shell(0, 0, bw - 1, bd - 1, 1, 4, "concrete_panel", "floor_concrete", "floor_concrete");
        for (int x = 2; x < bw - 2; x += 4) {
            p.set(x, 3, 0, "minecraft:iron_bars");
            p.set(x, 3, bd - 1, "minecraft:iron_bars");
        }
        boolean forced = chance() < 0.4 + hit * 0.5;
        if (forced) {
            p.set(bw / 2, 1, 0, "air");  // the door's gone, blown or cut out
            p.set(bw / 2, 2, 0, "air");
        } else {
            b.door(bw / 2, 1, 0, "minecraft:iron_door", "north");
        }
        b.flat(0, 0, bw - 1, bd - 1, 4, "concrete_panel", 1, "minecraft:smooth_stone_slab[type=bottom]");
        for (int x = 1; x < bw - 1; x++) {
            if (Math.abs(x - bw / 2) <= 1) {
                continue;
            }
            // looted racks are left as empty shelving
            boolean taken = forced && chance() < 0.6;
            p.set(x, 1, bd - 2, x % 3 == 0 ? (taken ? "store_shelf_empty[facing=north]" : "store_shelf_ammo[facing=north]")
                    : (taken ? "store_shelf_empty[facing=north]" : "store_gun_rack[facing=north]"));
            if (x % 4 == 1 && chance() < 0.6) {
                p.set(x, 1, 1, chance() < 0.5 ? "crate" : "store_case_guns[facing=south]");
            }
        }
        p.set(bw / 2, 3, -1, flood(rng, 0.5));
        b.lights(1, 1, bw - 2, bd - 2, 3, 0);
        if (forced) {
            b.mess(1, 1, bw - 2, bd - 2, 1, 0.08);
            p.setIfAir(bw / 2, 1, -1, "debris_chunk");
        }
        for (int i = -2; i <= 2; i++) {
            if (Math.abs(i) > 0) {
                p.set(bw / 2 + i, 1, -3, "sandbags[facing=north]");
            }
        }
    }

    /**
     * A vehicle bay 7 wide and 11 deep from (x, z): hesco two high down the sides and across the
     * back, open at the front (-z), a vehicle backed in, or its oil stain.
     */
    void bay(int x, int z, String models) {
        for (int k = 0; k < 11; k++) {
            for (int y = 1; y <= 2; y++) {
                p.set(x, y, z + k, "hesco");
                p.set(x + 6, y, z + k, "hesco");
            }
        }
        for (int i = 1; i < 6; i++) {
            for (int y = 1; y <= 2; y++) {
                p.set(x + i, y, z + 10, "hesco");
            }
            for (int k = 0; k < 10; k++) {
                p.set(x + i, 0, z + k, chance() < 0.6 ? "minecraft:gravel" : chance() < 0.5 ? "minecraft:coarse_dirt" : "concrete_dark_stain");
            }
        }
        if (chance() < 0.65) {
            p.vehicle(x + 3.5, 1, z + 5, 180, models, rng.nextLong());
        } else if (chance() < 0.5) {
            p.set(x + 3, 1, z + 4, any("tire[axis=x]", "minecraft:barrel[facing=up,loot=storage]", "crate"));
        }
        if (chance() < 0.25) {
            p.set(x + 3, 3, z + 10, "camo_net");
            for (int i = 1; i < 6; i++) {
                for (int k = 6; k < 11; k++) {
                    p.set(x + i, 3, z + k, "camo_net");
                }
            }
        }
    }

    /**
     * The processing line, len deep from (x, z) and 10 wide: three chain-link chutes the queue
     * was fed down, what people dropped in them, and the screening desks at the far end.
     */
    void processing(int x, int z, int len) {
        for (int i = 0; i < 10; i++) {
            for (int k = 0; k < len + 4; k++) {
                p.set(x + i, 0, z + k, chance() < 0.65 ? "minecraft:gravel" : chance() < 0.6 ? "minecraft:coarse_dirt" : "minecraft:mud");
            }
        }
        for (int c = 0; c <= 3; c++) {
            int cx = x + c * 3;
            for (int k = 0; k < len; k++) {
                boolean post = k % 4 == 0;
                if (!post && chance() < 0.04 + hit * 0.1) {
                    continue;
                }
                p.set(cx, 1, z + k, post ? "fence_post" : "chainlink[axis=z]");
                p.set(cx, 2, z + k, post ? "fence_post" : "chainlink[axis=z]");
            }
        }
        for (int c = 0; c < 3; c++) {
            int cx = x + 1 + c * 3;
            for (int k = 1; k < len; k += 2) {
                if (chance() < 0.18 + hit * 0.15) {
                    p.set(cx + rng.nextInt(2), 1, z + k, any("cardboard_box", "trash_bag[facing=north]", "blood_splat", "debris_chunk",
                            "minecraft:white_carpet"));
                }
            }
            // the desk at the end of the chute, the chair knocked over
            p.set(cx, 1, z + len + 1, "desk[facing=north]");
            p.set(cx + 1, 1, z + len + 1, chance() < 0.5 ? "filing_cabinet[facing=north]" : "office_chair_fallen");
        }
        // a floodlight over the head of the line
        floodMast(x + 5, z - 2, 7);
    }

    /** Clutter dropped at (x, z): a sandbag heap, jerrycans, pallets, a stretcher, the dead. */
    void litter(int x, int z) {
        switch (rng.nextInt(6)) {
            case 0 -> {
                for (int i = 0; i < 3; i++) {
                    p.setIfAir(x + i, 1, z, "sandbags[facing=north]");
                    if (i == 1) {
                        p.setIfAir(x + i, 2, z, "sandbags[facing=north]");
                    }
                }
            }
            case 1 -> {
                p.setIfAir(x, 1, z, "minecraft:barrel[facing=up,loot=storage]");
                p.setIfAir(x + 1, 1, z, "minecraft:barrel[facing=up]");
                p.setIfAir(x, 1, z + 1, "minecraft:barrel[facing=north]");
            }
            case 2 -> {
                p.setIfAir(x, 1, z, "minecraft:spruce_slab[type=bottom]");
                p.setIfAir(x + 1, 1, z, "minecraft:spruce_slab[type=bottom]");
                p.setIfAir(x, 2, z, chance() < 0.5 ? "crate" : "air");
            }
            case 3 -> {
                p.setIfAir(x, 1, z, "minecraft:white_bed[part=head,facing=north]");
                p.setIfAir(x, 1, z + 1, "minecraft:white_bed[part=foot,facing=north]");
                p.setIfAir(x + 1, 1, z, "blood_splat");
            }
            case 4 -> {
                p.setIfAir(x, 1, z, "trash_bag[facing=east]");
                p.setIfAir(x + 1, 1, z, "trash_bag[facing=north]");
                p.setIfAir(x, 1, z + 1, "blood_splat");
            }
            default -> {
                p.setIfAir(x, 1, z, "tire[axis=x]");
                p.setIfAir(x, 2, z, "tire[axis=z]");
                p.setIfAir(x + 1, 1, z, "debris_chunk");
            }
        }
    }
}
