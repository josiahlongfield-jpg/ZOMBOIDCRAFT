package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The pieces the army put up, drawn through a {@link Placer} in their own frame (front along
 * local z = 0 facing north, x along the front) like {@link Bldg}'s:
 * <ul>
 * <li>walls and wire: double chain-link with barbed wire on top, hesco lines, shipping
 * containers stacked into a wall, jersey barriers, sandbag nests;</li>
 * <li>the watchtowers on their legs with a floodlight and a sandbagged platform, the guard
 * booth and its barrier at the gate, floodlight masts and light tower trailers;</li>
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
    /** A floodlight on its bracket, looking -z: stands on a beam or bolts to the wall behind it; some dead. */
    static String flood(SplittableRandom rng, double dead) {
        return lamp(rng, dead, "cp_flood", "north");
    }

    /** One of the camp kit's lamps (cp_flood, cp_flood_hang, cp_flood_bar) facing that way, lit or dead. */
    static String lamp(SplittableRandom rng, double dead, String kind, String facing) {
        return kind + (rng.nextDouble() < dead ? "" : "_lit") + "[facing=" + facing + "]";
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
            p.setIfAir(x, 1, z, any("crate", "trash_bag[facing=north]", "bunk_crate_ammo[facing=north]"));
        }
        if (chance() < 0.3 + hit * 0.4) {
            p.setIfAir(x - 1, 1, z + 1, "blood_splat");
        }
    }

    // ------------------------------------------------------------------ towers and lights

    /**
     * A watchtower over (x, z) to (x + 4, z + 4): four timber legs X-braced in two tiers, a plank
     * deck at 7 with sandbags round it, the ladder up the back through a hatch, a tin roof on its
     * corner posts, a floodlight hung under the eave looking out (-z).
     */
    void watchtower(int x, int z) {
        int deck = 7;
        int[][] corner = {{0, 0}, {4, 0}, {4, 4}, {0, 4}};
        String[] turn = {"north", "east", "south", "west"};
        for (int c = 0; c < 4; c++) {
            String f = "[facing=" + turn[c] + "]";
            int cx = x + corner[c][0], cz = z + corner[c][1];
            for (int y = 1; y <= deck + 3; y++) {
                p.set(cx, y, cz, (y == deck ? "cp_tw_deck_post" : "cp_tw_leg") + f);
            }
        }
        // the X braces on each face, in tiers three high, the girt along the top of each tier
        for (int c = 0; c < 4; c++) {
            for (int u = 1; u <= 3; u++) {
                int[] at = switch (c) {
                    case 0 -> new int[] {x + u, z};
                    case 1 -> new int[] {x + 4, z + u};
                    case 2 -> new int[] {x + 4 - u, z + 4};
                    default -> new int[] {x, z + 4 - u};
                };
                if (c == 2 && u == 2) {
                    continue;  // the ladder's side
                }
                for (int t = 1; t < deck; t += 3) {
                    for (int v = 0; v < 3; v++) {
                        boolean up = u - 1 == v, down = 3 - u == v;
                        String b = up && down ? "cp_tw_brace_x" : up ? "cp_tw_brace_a" : down ? "cp_tw_brace_b" : null;
                        if (v == 2 && !(up && down)) {
                            b = b == null ? "cp_tw_girt" : b + "_g";
                        }
                        if (b != null && chance() > hit * 0.08) {
                            p.set(at[0], t + v, at[1], b + "[facing=" + turn[c] + "]");
                        }
                    }
                }
            }
        }
        for (int i = 0; i <= 4; i++) {
            for (int k = 0; k <= 4; k++) {
                boolean edge = i == 0 || k == 0 || i == 4 || k == 4;
                boolean corner4 = (i == 0 || i == 4) && (k == 0 || k == 4);
                if (!corner4 && !(i == 2 && k == 4)) {
                    p.set(x + i, deck, z + k, "cp_tw_deck");
                }
                if (edge && !corner4 && !(i == 2 && k == 4)) {
                    p.set(x + i, deck + 1, z + k, "sandbags[facing=" + (k == 0 || k == 4 ? "north" : "east") + "]");
                }
                String sides = (k == 0 ? "n" : k == 4 ? "s" : "") + (i == 0 ? "w" : i == 4 ? "e" : "");
                String roof = "cp_tw_roof" + (sides.isEmpty() ? "" : "_" + sides);
                if (chance() > hit * 0.12) {
                    p.set(x + i, deck + 4, z + k, roof);
                }
            }
        }
        // the ladder up the back face, through the hatch in the deck and the gap in the sandbags
        for (int y = 1; y <= deck; y++) {
            p.set(x + 2, y, z + 4, "ladder[facing=north]");
        }
        // the floodlight under the eave, looking out; on some a second one over the side
        p.set(x + 2, deck + 3, z, lamp(rng, 0.25 + hit * 0.3, "cp_flood_hang", "north"));
        if (chance() < 0.4) {
            p.set(x + 4, deck + 3, z + 2, lamp(rng, 0.25 + hit * 0.3, "cp_flood_hang", "east"));
        }
        if (chance() < 0.5) {
            p.set(x + 3, deck + 1, z + 2, "bunk_crate_ammo[facing=west]");
        }
        if (chance() < hit * 0.5) {
            p.set(x + 1, deck + 1, z + 1, "blood_splat");
        }
    }

    /** A floodlight mast: a steel pole h high, the crossbar of two lamps on top facing -z. */
    void floodMast(int x, int z, int h) {
        for (int y = 1; y < h; y++) {
            p.set(x, y, z, "streetlight_pole");
        }
        p.set(x, 0, z, "concrete_dark");
        p.set(x, h, z, lamp(rng, 0.35 + hit * 0.3, "cp_flood_bar", "north"));
    }

    /**
     * A light tower trailer at (x, z) to (x, z + 1), its lamps looking out (-z) and the tow bar back
     * toward +z: olive army issue, or one of the hire fleet's white ones commandeered; the mast run up
     * 3 to 5 sections, or wound down as it came off the truck.
     */
    void lightTower(int x, int z, boolean raised) {
        String c = chance() < 0.75 ? "olive" : "white";
        p.set(x, 1, z, "cp_lt_rear_" + c + "[facing=north]");
        p.set(x, 1, z + 1, "cp_lt_front_" + c + "[facing=north]");
        p.set(x, 2, z, "cp_lt_cap_rear[facing=north]");
        p.set(x, 2, z + 1, "cp_lt_cap_front[facing=north]");
        int h = raised ? 3 + rng.nextInt(3) : 0;
        for (int y = 3; y < 3 + h; y++) {
            p.set(x, y, z, "cp_lt_mast[facing=north]");
        }
        p.set(x, 3 + h, z, lamp(rng, 0.35 + hit * 0.3, "cp_lt_head", "north"));
    }

    /**
     * A guard booth on the 4 x 4 at (x, z): the prefab three by three in the corner, white ribbed
     * steel to the waist and glazed all round above it (some panes out), a glazed door at the back
     * (+z), the flat roof reaching out over every side with the beacon on it; inside the ledge under
     * the windows, the stool, the radio; bollards along the open side.
     */
    void booth(int x, int z) {
        double damage = 0.4 + hit * 0.5;
        String[] faces = {"north", "east", "south", "west"};
        // the ring of cells round the middle, each with the side it looks out of; corners take the
        // side clockwise after them as well (their piece's west)
        int[][] ring = {{0, 0, 0, 1}, {1, 0, 0, 0}, {2, 0, 1, 1}, {2, 1, 1, 0}, {2, 2, 2, 1}, {1, 2, 2, 0}, {0, 2, 3, 1},
            {0, 1, 3, 0}};
        for (int[] c : ring) {
            int cx = x + c[0], cz = z + c[1];
            String f = "[facing=" + faces[c[2]] + "]", k = c[3] == 1 ? "_c" : "";
            p.set(cx, 0, cz, "floor_concrete");
            p.set(cx, 3, cz, "cp_gb_head" + k + f);
            if (c[0] == 1 && c[1] == 2) {
                continue; // the door's cell
            }
            p.set(cx, 1, cz, "cp_gb_wall" + k + f);
            p.set(cx, 2, cz, "cp_gb_win" + k + (chance() < 0.12 + damage * 0.35 ? "_broken" : "") + f);
        }
        p.set(x + 1, 0, z + 1, "floor_concrete");
        p.set(x + 1, 3, z + 1, "cp_gb_roof[facing=north]");
        Doors.abandoned(p, rng, x + 1, 1, z + 2, Doors.SHOP, "north", Doors.hinge(rng));
        p.set(x + 1, 0, z + 3, "curb");
        // inside: the stool knocked over or not, the radio on the floor where it fell
        p.set(x + 1, 1, z + 1, chance() < 0.3 + damage * 0.3 ? "un_stool" : "air");
        if (chance() < 0.4) {
            p.set(x + 1, 1, z + 1, "home_radio_low[facing=" + any("north", "east", "west") + "]");
        }
        // the bollards down the open side, the sandbags someone started
        for (int i = 0; i < 4; i += 2) {
            p.set(x + 3, 1, z + i, "gs_bollard");
        }
        if (chance() < 0.5 + hit * 0.3) {
            p.set(x + 3, 1, z + 1, "sandbags[facing=north]");
        }
    }

    /**
     * A barrier at (x, z) across the lane of length len along x: the yellow housing, the striped
     * arm on its rest post, or raised standing up over the housing.
     */
    void boom(int x, int z, int len, boolean down) {
        p.set(x, 1, z, "cp_boom_post[facing=north]");
        if (down) {
            for (int i = 1; i <= len; i++) {
                p.set(x + i, 1, z, (i == len ? "cp_boom_tip" : "cp_boom_arm") + "[facing=north]");
            }
        } else {
            int top = Math.min(len, 6);
            for (int y = 2; y <= top; y++) {
                p.set(x, y, z, (y == top ? "cp_boom_cap" : "cp_boom_up") + "[facing=north]");
            }
        }
    }

    // ------------------------------------------------------------------ canvas

    /**
     * A GP tent 6 wide (x) and len deep from (x, z), its door at the front ({@link Tents}): olive
     * for green canvas with soldiers' cots, white an aid tent, the rest a relief shelter; the
     * stores without cots. Fallen in, it's a heap of canvas.
     */
    void tent(int x, int z, int len, String canvas, boolean cots) {
        Tents t = new Tents(p, rng);
        t.hit = hit;
        String c = canvas.contains("green") ? "olive" : canvas.contains("white") ? "white" : "tan";
        String use = !cots ? "supply" : c.equals("olive") ? "gp" : c.equals("white") ? "triage" : "shelter";
        t.tent(x, z, 6, len, c, use, 0.15 + hit * 0.35);
    }

    /**
     * The field hospital: a long white frame tent bw wide (x; 6, 10 or 18) and len deep, doors at
     * both ends of a long one, red crosses on its walls, beds between screens ({@link Tents}).
     */
    void hospital(int x, int z, int bw, int len) {
        Tents t = new Tents(p, rng);
        t.hit = hit;
        t.lit = 0.25;
        t.backDoor = len >= 12;
        t.tent(x, z, bw, len, "white", "ward", 0.05 + hit * 0.15);
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
                    String g = side[0] == 1 ? "south" : "north";
                    p.set(x, 1, side[0], "bunk_bed_head[facing=" + g + "]");
                    p.set(x, 1, side[1], "bunk_bed_foot[facing=" + g + "]");
                    if (chance() < 0.7) {
                        p.set(x, 2, side[0], "bunk_bed_head_top[facing=" + g + "]");
                        p.set(x, 2, side[1], "bunk_bed_foot_top[facing=" + g + "]");
                    }
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
                p.set(x, 4, z, "stone_trim");
                p.set(x, 0, z, "floor_concrete");
            }
        }
        for (int x : new int[] {cx - 3, cx + 2}) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, -3, "concrete_panel");
            }
        }
        b.flat(0, 0, bw - 1, bd - 1, 8, "concrete_panel", 1, "stone_trim");
        // antennas and a dish on the roof
        for (int i = 0; i < 3; i++) {
            int ax = 2 + rng.nextInt(Math.max(1, bw - 4)), az = 2 + rng.nextInt(Math.max(1, bd - 4));
            for (int y = 9; y < 9 + 3 + rng.nextInt(5); y++) {
                p.set(ax, y, az, "ext_mast");
            }
        }
        p.set(bw - 4, 9, bd - 4, "tv_dish_small[facing=" + any("north", "east", "south", "west") + "]");
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
                    p.set(x, 1, z, "bunk_mess_table[axis=x]");
                }
                if (chance() < 0.3) {
                    p.set(x, 1, z - 1, "office_chair[facing=south]");
                }
            }
        }
        for (int x = 2; x < bw - 2; x++) {
            p.set(x, 1, bd - 3, x % 5 == 0 ? "bunk_range[facing=north]" : "store_counter[facing=north]");
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
                    any("crate", "bunk_crate_ammo[facing=north]", "tire_stack", "bunk_jerry_cans[facing=north]"));
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
            p.setIfAir(x + rng.nextInt(len), 1, z + 8, any("tire_stack", "barrel", "crate"));
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
                    m = "cp_pad_yellow";
                }
                boolean h = (i == 4 || i == 10) && k >= 4 && k <= 10 || k == 7 && i > 4 && i < 10;
                if (h) {
                    m = "cp_pad_white";
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
     * The helicopter on the pad with its nose at -z over (cx, cz): the attack helicopter left
     * where it landed, or what's left of it, burnt out with its wreckage thrown round the pad.
     */
    void helicopter(int cx, int cz, boolean wreck) {
        String state = wreck ? (chance() < 0.5 ? "burnt" : "wrecked") : (chance() < 0.75 ? "abandoned" : "usable");
        p.vehicle(cx + 0.5, 1, cz + 4, 180, "=heli_hornbill@" + state, rng.nextLong());
        if (wreck) {
            for (int i = 0; i < 14; i++) {
                int x = cx - 6 + rng.nextInt(13), z = cz - 5 + rng.nextInt(20);
                p.setIfAir(x, 1, z, chance() < 0.55 ? "debris_chunk[facing=" + any("north", "east", "south", "west") + "]" : "scorch");
                if (chance() < 0.4) {
                    p.set(x, 0, z, "burnt_ground");
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
                                : "bunk_crate_ammo[facing=north]");
                    }
                }
                if (chance() > hit * 0.3) {
                    p.set(x + i, 4, z + k, "camo_net");
                }
            }
        }
        for (int[] c : new int[][] {{-1, -1}, {w, -1}, {-1, d}, {w, d}, {w / 2, -1}, {w / 2, d}}) {
            for (int y = 1; y <= 4; y++) {
                p.set(x + c[0], y, z + c[1], "tn_pole");
            }
        }
    }

    /**
     * Fuel bladders in a row along x from (x, z), seven blocks square each, black pillows in an
     * earth berm with the hose run to the pump.
     */
    void fuelTanks(int x, int z, int n) {
        int r = 3;
        String[] turn = {"north", "east", "south", "west"};
        for (int t = 0; t < n; t++) {
            int cx = x + r + 1 + t * (2 * r + 3), cz = z + r + 1;
            boolean slack = chance() < 0.3 + hit * 0.3;  // drained, or holed and run dry
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean ex = Math.abs(dx) == r, ez = Math.abs(dz) == r;
                    String v = slack ? "_slack" : "", b;
                    if (ex && ez) {
                        b = "cp_bladder_c" + v + "[facing=" + turn[dz < 0 ? (dx < 0 ? 0 : 1) : (dx > 0 ? 2 : 3)] + "]";
                    } else if (ez || ex) {
                        b = "cp_bladder_e" + v + "[facing=" + (dz < 0 && ez ? "north" : dz > 0 && ez ? "south" : dx > 0 ? "east" : "west") + "]";
                    } else if (dx == 0 && dz == 0) {
                        b = "cp_bladder_valve" + v + "[facing=north]";
                    } else {
                        b = (Math.abs(dx) == r - 1 || Math.abs(dz) == r - 1 ? "cp_bladder_ring" : "cp_bladder") + v;
                    }
                    p.set(cx + dx, 1, cz + dz, b);
                }
            }
            // the hose from the valve, over the berm to the pump
            p.setIfAir(cx, 1, z - 2, "tn_cable[axis=z]");
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
        p.set(x + bw / 2 + 1, 1, z - 3, "tn_genset[facing=north]");
        p.set(x + 1, 1, z - 2, "sign_post");
    }

    /**
     * A lattice radio mast h high at (x, z): sections in the red and white aviation bands, the head
     * with its whips and yagi, the beacon on top (mostly dead), the equipment cabinet at its foot.
     */
    void mast(int x, int z, int h) {
        for (int y = 1; y <= h; y++) {
            p.set(x, y, z, ((y - 1) / 3) % 2 == 0 ? "cp_mast_r" : "cp_mast_w");
        }
        p.set(x, h + 1, z, "cp_mast_top[facing=" + any("north", "east", "south", "west") + "]");
        p.set(x, h + 2, z, chance() < 0.25 ? "tv_beacon_lit" : "tv_beacon");
        p.set(x, 0, z, "concrete_dark");
        p.set(x - 1, 1, z + 2, "cp_cabinet[facing=north]");
        for (int k = 1; k <= 2; k++) {
            p.setIfAir(x, 1, z + k, "tn_cable[axis=z]");
        }
    }

    /** A row of n portable toilets along x from (x, z), doors facing -z, one blown over on its back. */
    void toilets(int x, int z, int n) {
        for (int t = 0; t < n; t++) {
            int tx = x + t * 2;
            if (chance() < 0.12 + hit * 0.2) {
                p.set(tx, 1, z, "cp_porta_down[facing=north]");
                continue;
            }
            p.set(tx, 1, z, "st_porta_lo[facing=north]");
            p.set(tx, 2, z, "st_porta_hi[facing=north]");
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
                    p.set(x, 0, z, any("burnt_ground", "burnt_ground", "minecraft:coarse_dirt"));
                    p.set(x, 1, z, "air");
                    double q = chance();
                    if (q < 0.18) {
                        p.set(x, 1, z, "bunk_bodybag[facing=" + any("north", "east", "south", "west") + "]");
                    } else if (q < 0.3) {
                        p.set(x, 1, z, "trash_bag[facing=" + any("north", "east") + "]");
                    } else if (q < 0.36) {
                        p.set(x, 1, z, "debris_chunk");
                    } else if (q < 0.6) {
                        p.set(x, 1, z, "scorch");
                    }
                } else if (dist < r + 1.5) {
                    p.set(x, 0, z, "minecraft:coarse_dirt");
                    p.set(x, 1, z, chance() < 0.6 ? "minecraft:coarse_dirt" : "air");
                }
            }
        }
        p.set(cx, 1, cz, "minecraft:campfire[lit=" + (chance() < 0.2) + "]");
        for (int i = 0; i < 3; i++) {
            p.setIfAir(cx + r + 2, 1, cz - 1 + i, i == 1 ? "bunk_jerry_cans[facing=west]" : "barrel");
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
                    p.set(x + i, -1, z + k, "bunk_bodybag[facing=" + (k % 2 == 0 ? "east" : "west") + "]");
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
                p.set(x + i, 1, z + 1, "cp_stake");
            }
        }
    }

    /** A grave: a mound and a cross of two battens lashed together, a helmet hung on some. */
    void grave(int x, int z) {
        p.set(x, 0, z, "minecraft:rooted_dirt");
        p.set(x, 0, z + 1, "minecraft:rooted_dirt");
        p.set(x, 1, z, "ch_grave_mound");
        p.set(x, 1, z - 1, (chance() < 0.4 ? "cp_grave_helmet" : "cp_grave_cross") + "[facing=north]");
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
                            p.set(a + h * 3, 1, z + k, "cp_post");
                        }
                        for (int k = 1; k <= 9; k++) {
                            p.set(a + h * 3, 2, z + k, "cp_log[facing=east]");
                        }
                    }
                }
                case 1 -> {
                    // the wall, a platform behind to drop off
                    for (int k = 1; k <= 9; k++) {
                        for (int y = 1; y <= 3; y++) {
                            p.set(a + 1, y, z + k, chance() < 0.06 * (1 + hit) ? "air" : (y == 3 ? "cp_wall_top" : "cp_wall") + "[facing=west]");
                        }
                        p.set(a + 2, 1, z + k, "cp_step[facing=east]");
                    }
                }
                case 2 -> {
                    // the tyre run
                    for (int i2 = 0; i2 < 5; i2++) {
                        for (int k = 1; k <= 9; k += 2) {
                            p.set(a + i2, 1, z + k + (i2 % 2), "tire_flat");
                        }
                    }
                }
                case 3 -> {
                    // monkey bars, two runs, each between its pairs of posts
                    for (int run : new int[] {3, 7}) {
                        for (int[] c : new int[][] {{0, run - 1}, {5, run - 1}, {0, run + 1}, {5, run + 1}}) {
                            for (int y = 1; y <= 4; y++) {
                                p.set(a + c[0], y, z + c[1], "cp_post");
                            }
                        }
                        p.set(a, 4, z + run, "cp_bars_end[facing=north]");
                        p.set(a + 5, 4, z + run, "cp_bars_end[facing=south]");
                        for (int i2 = 1; i2 < 5; i2++) {
                            p.set(a + i2, 4, z + run, "cp_bars[facing=north]");
                        }
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
                            p.set(a + 2, y, z + k, "cp_post");
                        }
                    }
                    for (int k = 2; k <= 8; k++) {
                        p.set(a + 2, 6, z + k, "cp_log[facing=east]");
                    }
                    for (int k = 3; k <= 7; k += 2) {
                        int drop = 2 + rng.nextInt(3);
                        for (int y = 6 - drop; y < 6; y++) {
                            p.set(a + 2, y, z + k, y == 6 - drop ? "cp_rope_end" : "cp_rope");
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
            p.set(lx - 2, 1, z, "cp_lane_post[facing=north]");
            // the target frame down range, some shot to bits
            int tz = z + d - 7;
            if (chance() < 0.8) {
                p.set(lx, 1, tz, "cp_target_lo[facing=north]");
                p.set(lx, 2, tz, (chance() < 0.4 + hit * 0.3 ? "cp_target_hi_shot" : "cp_target_hi") + "[facing=north]");
            } else {
                p.set(lx, 1, tz, "cp_target_down[facing=" + any("north", "south") + "]");
            }
        }
        // the red flag at the end of the line, the control hut
        for (int y = 1; y < 6; y++) {
            p.set(x - 2, y, z + 1, "cp_flagpole");
        }
        p.set(x - 2, 6, z + 1, "cp_flag[facing=north]");
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
            p.set(x, 3, 0, "cv_cell_bars[facing=north]");
            p.set(x, 3, bd - 1, "cv_cell_bars[facing=south]");
        }
        boolean forced = chance() < 0.4 + hit * 0.5;
        if (forced) {
            p.set(bw / 2, 1, 0, "air");  // the door's gone, blown or cut out
            p.set(bw / 2, 2, 0, "air");
        } else {
            b.door(bw / 2, 1, 0, "bunk_door_steel", "north");
        }
        b.flat(0, 0, bw - 1, bd - 1, 4, "concrete_panel", 1, "stone_trim");
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
            p.set(x + 3, 1, z + 4, any("tire_stack", "barrel", "crate"));
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
                            "tn_groundsheet"));
                }
            }
            // the desk at the end of the chute, the chair knocked over
            p.set(cx, 1, z + len + 1, "desk[facing=north]");
            p.set(cx + 1, 1, z + len + 1, chance() < 0.5 ? "filing_cabinet[facing=north]" : "office_chair_fallen");
        }
        // a light tower trailer lighting the head of the line
        lightTower(x + 5, z - 3, true);
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
                p.setIfAir(x, 1, z, "barrel");
                p.setIfAir(x + 1, 1, z, "barrel");
                p.setIfAir(x, 1, z + 1, "bunk_jerry_cans[facing=north]");
            }
            case 2 -> {
                p.setIfAir(x, 1, z, "st_pallet[facing=north]");
                p.setIfAir(x + 1, 1, z, "st_pallet[facing=east]");
                p.setIfAir(x, 2, z, chance() < 0.5 ? "crate" : "air");
            }
            case 3 -> {
                p.setIfAir(x, 1, z, "st_stretcher[facing=north]");
                p.setIfAir(x + 1, 1, z, "blood_splat");
            }
            case 4 -> {
                p.setIfAir(x, 1, z, "trash_bag[facing=east]");
                p.setIfAir(x + 1, 1, z, "trash_bag[facing=north]");
                p.setIfAir(x, 1, z + 1, "blood_splat");
            }
            default -> {
                p.setIfAir(x, 1, z, "tire_stack");
                p.setIfAir(x + 1, 1, z + 1, "tire_flat");
                p.setIfAir(x + 1, 1, z, "debris_chunk");
            }
        }
    }
}
