package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;
import java.util.function.IntFunction;

/**
 * The roadside diner, drawn from the photos in refs/diners: the Worcester lunch cars (Miss
 * Worcester, the Bolton Landing car), the Grubstake, the Americana's Kullman car, the Sylva
 * Coffee Shop and the Roundabout's dining room. There is no photo of a diner kitchen yet, so the
 * kitchens follow what is written about short-order kitchens of the time. Three builds, every
 * name made up:
 * <ul>
 * <li>the lunch car: a barrel-roofed car on its brick foundation, its name painted under the band
 * of sash windows (MISS GRAYHAVEN in cream and navy, NIGHT OWL LUNCH in navy and gold, WHISTLE
 * STOP in red), or the stainless SILVER MOON with its fluted sides, the neon along its eaves and
 * the sign tower over its glass-block vestibule. Inside the booths along the windows, the stools,
 * the counter with the register, the pies and the cake stand, the coffee urns and the mixer on
 * the back bar, the griddle under its hood, the specials over the counter; the block kitchen and
 * the restroom built on behind, the walk-in and the dish pit in it;</li>
 * <li>the HILLTOP CAFE in brick: the storefront with its lettered windows under the mansard
 * canopy, the signboard over it, the hanging sign on its mast and the letterboard by the road.
 * Inside booths, the restrooms in the corner, the counter, the menu board and the pass to the
 * kitchen: the line under the hoods, the dish pit, the dry stores and the walk-in;</li>
 * <li>the ROUTE 9 TRUCK STOP: the cafe with a store and the drivers' showers on its east end, the
 * diesel canopy, the scale, the trucks left in the gravel.</li>
 * </ul>
 * Some still have power (a generator out back): the neon lit, the globes on, the coffee hot. Each
 * tells how it went: walked away from with the plates still out, picked over, or held by someone
 * who boarded it up and stayed.
 *
 * Drawn in its lot's frame: x along the road, z back from it (the road is to the north, -z).
 */
final class Diner {
    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final String CARS = "sedan,hatchback,pickup,suv,minivan,wagon,coupe,van";
    private static final String TRUCKS = "box_truck,box_truck,moving_truck,step_van,tow_truck";

    private enum Story { WALKED, LOOTED, HOLDOUT }

    private final Placer q;
    private final SplittableRandom rng;
    private final double hit;
    private final boolean power;
    private final Story story;

    Diner(Placer q, SplittableRandom rng, double hit) {
        this.q = q;
        this.rng = rng;
        this.hit = hit;
        power = rng.nextDouble() < 0.35;
        double r = rng.nextDouble();
        story = r < 0.4 ? Story.WALKED : r < 0.8 ? Story.LOOTED : Story.HOLDOUT;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private String dir() {
        return DIRS[rng.nextInt(4)];
    }

    /** The lit version of a light when the power is on (now and then dead anyway). */
    private String on(String block, double dead) {
        return power && chance() >= dead ? block + "_on" : block;
    }

    private static String f(String block, String facing) {
        return block + "[facing=" + facing + "]";
    }

    private static boolean empty(Placer p, int x, int y, int z) {
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    /** Takes off whatever grew at (x, z), both halves of tall grass. */
    private static void clear(Placer p, int x, int z) {
        String top = p.get(x, 2, z);
        if (top != null && top.contains("half=upper")) {
            p.set(x, 2, z, "air");
        }
        p.set(x, 1, z, "air");
    }

    /** Split-face block, now and then pocked, stained or cracked. */
    private String cmu(int y) {
        double r = chance();
        return r < 0.08 ? "sh_cmu_tan_pocks" : r < 0.14 ? "sh_cmu_tan_stain" : r < 0.18 && y <= 2 ? "sh_cmu_tan_moss"
                : r < 0.22 ? "sh_cmu_tan_crack" : "sh_cmu_tan";
    }

    /**
     * A sign cols by rows tiles on blocks facing out, tile 0 (the picture's top left as its reader
     * sees it) at (x, y, z); tile(i) names each block, null leaving it out (fallen off).
     */
    private static void sign(Placer p, int x, int y, int z, String facing, int cols, int rows, IntFunction<String> tile) {
        int rx = facing.equals("north") ? -1 : facing.equals("south") ? 1 : 0;
        int rz = facing.equals("east") ? -1 : facing.equals("west") ? 1 : 0;
        for (int i = 0; i < cols * rows; i++) {
            String t = tile.apply(i);
            if (t != null) {
                int c = i % cols;
                p.set(x + rx * c, y - i / cols, z + rz * c, f(t, facing));
            }
        }
    }

    /** A sash of the given frame along axis: whole, broken, or boarded over by the holdout. */
    private String sash(String fr, String part, String axis) {
        if (story == Story.HOLDOUT && chance() < 0.55) {
            return "boarded_window[axis=" + axis + "]";
        }
        return "dn_win_" + fr + "_" + part + (chance() < 0.1 + hit * 0.45 ? "_broken" : "") + "[axis=" + axis + "]";
    }

    private String droptile() {
        double r = chance();
        return r < hit * 0.1 ? "dn_droptile_gone" : r < 0.12 ? "dn_droptile_stain" : r < 0.16 ? "dn_droptile_sprinkler"
                : r < 0.18 ? "dn_droptile_alarm" : "dn_droptile";
    }

    /** A troffer set into the drop ceiling where the room is too low for a light under it. */
    private String troffer() {
        double r = chance();
        String t = !power ? "fit_troffer" : r < 0.75 ? "fit_troffer_on" : r < 0.9 ? "fit_troffer_flicker" : "fit_troffer";
        return t + "[axis=x]";
    }

    /** A flush light under a ceiling: lit if the generator still runs. */
    private String light() {
        return power && chance() > 0.15 ? "ceiling_light" : "ceiling_light_off";
    }

    // ------------------------------------------------------------------ the lot

    /** Asphalt over a box of the lot, cracked and patched, what grew there cleared. */
    private void asphalt(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                q.set(x, 0, z, r < 0.62 ? "asphalt" : r < 0.78 ? "asphalt_crack" : r < 0.88 ? "asphalt_patch" : r < 0.95 ? "asphalt_b" : "asphalt_moss");
                clear(q, x, z);
                if (chance() < 0.03) {
                    q.set(x, 1, z, "weeds");
                }
            }
        }
    }

    /** The concrete walk over a box of the lot. */
    private void walk(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                q.set(x, 0, z, r < 0.6 ? "floor_concrete" : r < 0.78 ? "floor_concrete_crack" : r < 0.9 ? "floor_concrete_b" : "concrete_dark_stain");
                clear(q, x, z);
            }
        }
    }

    /**
     * A gravel lot: grey gravel with a few broad patches of packed dirt where the rigs turned, and
     * grass creeping in only round the edges.
     */
    private void gravel(int x0, int z0, int x1, int z1) {
        double ox = rng.nextDouble() * 40, oz = rng.nextDouble() * 40;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double n = Math.sin((x + ox) * 0.31) * Math.cos((z + oz) * 0.27) + Math.sin((x + z + ox) * 0.17) * 0.5 + chance() * 0.3;
                int edge = Math.min(Math.min(x - x0, x1 - x), Math.min(z - z0, z1 - z));
                String g = edge < 2 && chance() < 0.45 - edge * 0.2 ? "minecraft:grass_block"
                        : n > 1.15 ? "minecraft:coarse_dirt" : n > 1.05 && chance() < 0.5 ? "minecraft:dirt_path" : "minecraft:gravel";
                q.set(x, 0, z, g);
                clear(q, x, z);
            }
        }
    }

    /**
     * Nose-in spaces three wide from x0 to x1, their wheel stops along z, the lines back toward the
     * road; some still taken by what was left behind.
     */
    private void spaces(int x0, int x1, int z, double taken) {
        for (int x = x0; x <= x1; x++) {
            int i = (x - x0) % 3;
            if (i == 0) {
                for (int k = 1; k <= 5; k++) {
                    if (chance() < 0.85) {
                        q.set(x, 0, z - k, "road_line_white[axis=z]");
                    }
                }
            } else if (x < x1) {
                q.set(x, 1, z, chance() < hit * 0.2 ? "air" : "dn_wheelstop[axis=x]");
                if (i == 1 && chance() < taken) {
                    q.vehicle(x + 1, 1, z - 2.5, chance() < 0.5 ? 0 : 180, CARS, rng.nextLong());
                }
            }
        }
    }

    /** Out back: the dumpster in its lattice pen, bags, the grease bin, the generator if the lights are on. */
    private void yard(Placer s, int x0, int z) {
        for (int x = x0 - 1; x <= x0 + 6; x++) {
            for (int dz = 0; dz <= 1; dz++) {
                s.set(x, 0, z + dz, "floor_concrete");
                clear(s, x, z + dz);
            }
        }
        for (int y = 1; y <= 2; y++) {
            for (int x = x0 - 1; x <= x0 + 2; x++) {
                s.set(x, y, z + 1, "dn_lattice[axis=x]");
            }
            s.set(x0 - 1, y, z, "dn_lattice[axis=z]");
            s.set(x0 + 2, y, z, "dn_lattice[axis=z]");
        }
        s.set(x0, 1, z, f("dumpster", pick("north", "south")));
        s.set(x0 + 1, 1, z, chance() < 0.7 ? f("trash_bag", dir()) : "air");
        s.set(x0 + 4, 1, z, f("dn_grease", "north"));
        if (power) {
            s.set(x0 + 6, 1, z, f("bunk_gen_engine", "north"));
        }
        for (int i = 0; i < 3; i++) {
            s.setIfAir(x0 + 3 + rng.nextInt(4), 1, z + rng.nextInt(2), chance() < 0.5 ? f("trash_bag", dir()) : f("pk_trash_bag", dir()));
        }
    }

    // ------------------------------------------------------------------ the dining room

    private String booth(String vin) {
        return "dn_booth_" + vin + (chance() < 0.1 + hit * 0.3 ? "_torn" : "");
    }

    private String table() {
        double r = chance();
        return switch (story) {
            case WALKED -> r < 0.6 ? "dn_table_set" : "dn_table_mess";
            case LOOTED -> r < 0.4 ? "dn_table" : r < 0.85 ? "dn_table_mess" : "dn_table_blood";
            default -> r < 0.5 ? "dn_table" : "dn_table_blood";
        };
    }

    private String stool(String vin) {
        double r = chance();
        return r < hit * 0.15 ? "dn_stool_stub" : r < hit * 0.35 ? "dn_stool_torn" : "dn_stool_" + vin;
    }

    /**
     * Booths along a front wall from x0 to x1 at z: two facing each other over each table (one over
     * a table for two where there's no room), a pendant over each table at ly (0 for none).
     */
    private void booths(Placer s, int x0, int x1, int y, int z, String vin, int ly) {
        for (int x = x0; x1 - x >= 1; ) {
            boolean pair = x1 - x >= 2;
            s.set(x, y, z, f(booth(vin), "east"));
            s.set(x + 1, y, z, f(table(), "north"));
            if (pair) {
                s.set(x + 2, y, z, f(booth(vin), "west"));
            }
            if (ly > 0) {
                s.set(x + 1, ly, z, on("dn_orb", 0.25));
            }
            x += pair ? 3 : 2;
        }
    }

    /**
     * The counter from x0 to its rounded end at x1 along z, its front to the stools (north); on it
     * the register at reg, the pie case beside it, the cake stand, napkins and menus, dishes left.
     */
    private void counter(Placer s, int x0, int x1, int y, int z, String ck, int reg) {
        int mid = (x0 + x1) / 2;
        for (int x = x0; x <= x1; x++) {
            s.set(x, y, z, f("dn_counter_" + ck + (x == x1 ? "_end" : ""), "north"));
            String it = null;
            if (x == reg) {
                it = story == Story.LOOTED && chance() < 0.6 ? null : "dn_ct_register";
            } else if (x == reg + (reg == x0 ? 1 : -1)) {
                it = story != Story.WALKED && chance() < 0.5 ? null : "dn_ct_pies";
            } else if (x == mid) {
                it = "dn_ct_cake";
            } else if (x == mid + 3 && x < x1) {
                it = story == Story.WALKED ? "dn_ct_donuts" : null;
            } else if ((x - x0) % 3 == 0) {
                it = pick("dn_ct_napkins", "dn_ct_napkins", "dn_ct_menus");
            } else if (chance() < (story == Story.WALKED ? 0.3 : 0.15)) {
                it = "dn_ct_mess";
            }
            if (it != null) {
                s.set(x, y + 1, z, f(it, "north"));
            }
        }
    }

    /** What's left on the floor of x0..x1, z0..z1 at y: menus, plates, papers, bags; the dead. */
    private void aftermath(Placer s, int x0, int z0, int x1, int z1, int y) {
        int n = story == Story.WALKED ? 3 : 10;
        for (int i = 0; i < n; i++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1), z = z0 + rng.nextInt(z1 - z0 + 1);
            s.setIfAir(x, y, z, pick("paper", "paper", f("dn_menus_floor", dir()), "dn_plates_broken", "glass_shards", f("trash_bag", dir()),
                    "blood_splat"));
        }
        if (hit > 0.5 || story == Story.HOLDOUT && chance() < 0.5) {
            for (int i = 0, tries = 0; i < 2 && tries < 20; tries++) {
                int x = x0 + rng.nextInt(Math.max(1, x1 - x0)), z = z0 + rng.nextInt(z1 - z0 + 1);
                if (empty(s, x, y, z) && empty(s, x + 1, y, z)) {
                    s.set(x, y, z, f("home_corpse_head", "east"));
                    s.set(x + 1, y, z, f("home_corpse_foot", "east"));
                    s.setIfAir(x, y, z + (z < z1 ? 1 : -1), pick("home_blood_pool", "home_blood_drag", "blood_splat"));
                    i++;
                }
            }
        }
    }

    /** The holdout's bed, light and water at (x, y, z), its head toward +z. */
    private void camp(Placer s, int x, int y, int z) {
        s.set(x, y, z + 1, f("sc_cot_head", "south"));
        s.set(x, y, z, f("sc_cot_foot", "south"));
        s.setIfAir(x + 1, y, z + 1, power || chance() < 0.5 ? f("fit_lantern_on", "north") : f("fit_lantern", "north"));
        s.setIfAir(x + 1, y, z, "bunk_drum_water");
    }

    private String barricade(String facing) {
        return "barricade[facing=" + facing + ",planks=" + (2 + rng.nextInt(3)) + ",strength=" + rng.nextInt(3) + "]";
    }

    // ------------------------------------------------------------------ the lunch car

    /** The lunch car and its kitchen on a lot lw by ld (at least about 28 by 25). */
    void railcar(int lw, int ld) {
        String k = pick("w", "b", "r", "s");
        int len = Math.max(16, Math.min(26, lw - 12)) & ~1;
        int cx = (lw - len) / 2, cz = Math.max(5, Math.min(12, ld - 20));
        asphalt(1, 0, lw - 2, cz - 4);
        if (cz >= 10) {
            spaces(2, lw - 3, cz - 4, 0.3);
        }
        walk(cx - 2, cz - 3, cx + len + 1, cz - 1);
        Placer s = q.shifted(cx, 0, cz);
        car(s, k, len, chance() < 0.5 ? 3 : len / 2 - 1);
        yard(s, 2, 17);
    }

    /**
     * The car at s: its front wall at z 0 along x 0..len-1, ten deep, raised a block on its
     * foundation (the floor's top at y 2); the vestibule and its steps out front at ex..ex+1, the
     * block kitchen and the restroom built on behind (z 10..15).
     */
    private void car(Placer s, String k, int len, int ex) {
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        boolean st = k.equals("s");
        String fr = st ? "s" : k.equals("r") ? "g" : "c";
        String cr = st ? "s" : k.equals("r") ? "r" : "d";
        String ck = st || k.equals("b") ? "q" : k.equals("r") ? "r" : "t";
        String body = st ? "dn_flute" : "dn_body_" + k, corner = "dn_corner_" + k, back = "dn_backq_" + k;
        String vin = pick("r", "b", "g");
        int bk = 9, an = 15, last = len - 1;
        for (int x = -1; x <= len; x++) {
            for (int z = -3; z <= an + 1; z++) {
                clear(s, x, z);
                for (int y = 2; y <= 8; y++) {
                    s.set(x, y, z, "air");
                }
            }
        }
        // the foundation, brick under the car and block under the kitchen; the floors inside
        for (int x = 0; x <= last; x++) {
            for (int z = 0; z <= an; z++) {
                s.set(x, 0, z, "floor_concrete");
                boolean rim = x == 0 || x == last || z == 0 || z == bk || z == an;
                String fl;
                if (rim) {
                    fl = z <= bk ? b.wall("brick_red", 1) : cmu(1);
                } else if (z < bk) {
                    fl = chance() < (z >= 6 ? 0.5 : 0.15) ? "dn_checker_worn" : "dn_checker";
                } else if (x <= 3) {
                    fl = "dn_mosaic";
                } else {
                    fl = chance() < 0.2 ? "dn_quarry_grease" : "dn_quarry";
                }
                s.set(x, 1, z, fl);
            }
        }
        s.set(ex, 1, 0, "dn_mosaic");
        s.set(ex + 1, 1, 0, "dn_mosaic");
        s.set(1, 1, bk, "dn_mosaic");
        s.set(last - 1, 1, bk, "dn_quarry");

        // the front: the name under the sashes in the longer stretch, BOOTH SERVICE in the other
        int wb = ex - 1, ea = ex + 2, eb = last - 1;
        boolean east = eb - ea >= wb - 1;
        int na = east ? ea : 1, nb = east ? eb : wb;
        int n0 = na + (nb - na + 1 - 6) / 2;
        int sa = east ? 1 : ea, sb = east ? wb : eb;
        int s0 = !st && sb - sa >= 1 ? sa + (sb - sa - 1) / 2 : -99;
        for (int x = 0; x <= last; x++) {
            if (x == 0 || x == last) {
                for (int y = 2; y <= 4; y++) {
                    s.set(x, y, 0, corner);
                    s.set(x, y, bk, corner);
                }
                continue;
            }
            boolean door = x == ex || x == ex + 1;
            String lo = x >= n0 && x < n0 + 6 ? "dn_name_" + k + "_" + (n0 + 5 - x)
                    : x >= s0 && x < s0 + 2 ? "dn_svc_" + k + "_" + (s0 + 1 - x)
                    : st && chance() < hit * 0.4 ? "dn_flute_dent" : body;
            s.set(x, 2, 0, door ? "air" : f(lo, "north"));
            s.set(x, 3, 0, door ? "air" : sash(fr, "lo", "x"));
            s.set(x, 4, 0, sash(fr, "hi", "x"));
            for (int y = 2; y <= 4; y++) {
                s.set(x, y, bk, f(back, "south"));
            }
        }
        // the ends: plain at the west where the menu board hangs, sashes at the east
        for (int z = 1; z < bk; z++) {
            for (int y = 2; y <= 4; y++) {
                s.set(0, y, z, f(body, "west"));
                s.set(last, y, z, y >= 3 && z >= 2 && z <= bk - 2 ? sash(fr, y == 3 ? "lo" : "hi", "z") : f(body, "east"));
            }
        }
        // the barrel roof: the eaves over the walls, the deck, the crown stepping up its middle
        boolean neon = st && power && chance() > 0.2;
        for (int x = 0; x <= last; x++) {
            s.set(x, 5, 0, f("dn_eave_" + k + (neon && chance() > 0.1 ? "_on" : ""), "north"));
            s.set(x, 5, bk, f("dn_eave_" + k, "south"));
            for (int z = 1; z < bk; z++) {
                s.set(x, 5, z, "dn_roof_" + k);
            }
            s.set(x, 6, 1, f("dn_crown_a_" + cr, "north"));
            s.set(x, 6, 2, f("dn_crown_b_" + cr, "north"));
            for (int z = 3; z <= bk - 3; z++) {
                s.set(x, 6, z, "dn_crown_c_" + cr);
            }
            s.set(x, 6, bk - 2, f("dn_crown_b_" + cr, "south"));
            s.set(x, 6, bk - 1, f("dn_crown_a_" + cr, "south"));
        }

        // the vestibule out front: its walls (glass block on the stainless car), its roof, the doors, the steps
        for (int x = ex - 1; x <= ex + 2; x++) {
            boolean side = x == ex - 1 || x == ex + 2;
            for (int z = -2; z <= -1; z++) {
                s.set(x, 0, z, "floor_concrete");
                s.set(x, 1, z, side ? b.wall("brick_red", 1) : "dn_mosaic");
                for (int y = 2; y <= 3 && side; y++) {
                    s.set(x, y, z, st ? (power && chance() < 0.8 ? "dn_glassblock_lit" : "dn_glassblock") : f(body, x < ex ? "west" : "east"));
                }
                s.set(x, 4, z, "dn_roof_" + k);
            }
        }
        b.doors(ex, 2, -2, "dn_door", "north");
        for (int x = ex; x <= ex + 1; x++) {
            s.set(x, 1, -3, "stairs_concrete[facing=south]");
        }
        s.set(ex + 2, 1, -3, f("dn_rail_slope", "north"));
        boolean lit = power && chance() > 0.2;
        if (st) {
            sign(s, ex + 1, 6, -2, "north", 2, 2, i -> "dn_tower_" + i + (lit ? "_on" : ""));
        } else {
            sign(s, ex + 1, 4, -3, "north", 2, 1, i -> "dn_neon_diner_" + i + (lit ? "_on" : ""));
        }
        // the striped awnings over the windows, the menu case and the hours by the door, notes on the glass
        if (k.equals("w") || k.equals("b")) {
            for (int x = 1; x < last; x++) {
                if (x < ex - 1 || x > ex + 2) {
                    s.set(x, 4, -1, f(chance() < hit * 0.4 ? "dn_awning_torn" : "dn_awning", "north"));
                }
            }
        }
        s.set(ex + 3, 2, -1, f("dn_menucase", "north"));
        s.set(ex - 2, 2, -1, f("dn_hours", "north"));
        int notes = story == Story.WALKED ? 1 : story == Story.HOLDOUT ? 2 : rng.nextInt(2);
        for (int i = 0; i < notes; i++) {
            int x = 1 + rng.nextInt(last - 1);
            if (x < ex - 1 || x > ex + 2) {
                String n = story == Story.WALKED ? pick("0", "2") : story == Story.HOLDOUT ? "1" : pick("0", "1", "2");
                s.set(x, 3, -1, f("dn_note_" + n, "north"));
            }
        }

        // inside: booths along the windows, the gumballs and cigarettes by the door, the OPEN sign
        booths(s, 1, ex - 2, 2, 1, vin, 0);
        booths(s, ex + 3, last - 1, 2, 1, vin, 0);
        s.set(ex - 1, 2, 1, "dn_gumball");
        s.set(ex + 2, 2, 1, f("dn_cig_lo", "south"));
        s.set(ex + 2, 3, 1, f("dn_cig_hi", "south"));
        boolean open = power && chance() > 0.3;
        s.set(ex + 4, 4, 1, f("neon_open_0" + (open ? "" : "_off"), "north"));
        s.set(ex + 3, 4, 1, f("neon_open_1" + (open ? "" : "_off"), "north"));
        // the stools, the counter, the specials hung over it, the globes over the aisle
        int c0 = 2, c1 = last - 2, mid = (c0 + c1) / 2;
        for (int x = c0; x <= c1; x += 2) {
            s.set(x, 2, 4, stool(vin));
        }
        counter(s, c0, c1, 2, 5, ck, Math.abs(c0 - ex) < Math.abs(c1 - ex) ? c0 : c1);
        sign(s, mid + 3, 4, 5, "north", 6, 1, i -> "dn_specials_" + i);
        for (int x = 2; x < last; x += 3) {
            s.set(x, 4, 3, on("dn_globe", 0.2));
        }
        // the back bar: urns, brewer, mixer, juice, toaster and mugs; the line under its hood; the pass to the kitchen
        int la = mid - 2, lb = mid + 1, pa = lb + 2;
        String[] tops = {"dn_urn", "dn_brewer", "dn_mugs", "dn_mixer", "dn_juice", "dn_toaster", "dn_mugs"};
        int t = rng.nextInt(tops.length);
        for (int x = c0; x <= c1; x++) {
            boolean line = x >= la && x <= lb;
            s.set(x, 2, bk - 1, f(line ? (x - la < 2 ? "dn_griddle" : x == lb - 1 ? "dn_fryer" : "dn_range") : "dn_backbar", "north"));
            if (line) {
                s.set(x, 4, bk - 1, f(on("dn_hood", 0.15), "north"));
            } else if (x != pa && x != pa + 1) {
                String top = tops[t++ % tops.length];
                if (!(story != Story.WALKED && chance() < 0.4)) {
                    s.set(x, 3, bk - 1, f(top.equals("dn_brewer") ? on(top, 0.3) : top, "north"));
                }
                if (chance() < 0.5) {
                    s.set(x, 4, bk - 1, f("dn_mugshelf", "north"));
                }
            }
        }
        for (int x = pa; x <= pa + 1; x++) {
            s.set(x, 3, bk, f(on("dn_pass", 0.3), "north"));
        }
        s.set(c1, 4, bk - 1, f(on("dn_clock", 0.5), "north"));
        // the menu board on the west end, posters, the restroom sign over its door
        sign(s, 1, 4, 4, "east", 3, 2, i -> "dn_menuboard_" + i);
        s.set(1, 3, bk - 1, f("dn_poster_" + rng.nextInt(4), "east"));
        s.set(last - 1, 3, bk - 1, f("dn_poster_" + rng.nextInt(4), "west"));
        s.set(1, 4, bk - 1, f("gs_sign_restrooms", "north"));
        b.door(1, 2, bk, "home_door_white", "south");
        b.door(last - 1, 2, bk, "home_door_white", "south");

        // the kitchen built on behind: block walls, white tile in the restroom, panel in the kitchen
        for (int z = bk + 1; z <= an; z++) {
            for (int y = 2; y <= 4; y++) {
                s.set(0, y, z, z == an || y == 4 ? cmu(y) : f("dn_wall_cmu_tile_white", "east"));
                s.set(last, y, z, z == an || y == 4 ? cmu(y) : f("dn_wall_cmu_frp", "west"));
            }
        }
        for (int x = 1; x < last; x++) {
            for (int y = 2; y <= 4; y++) {
                s.set(x, y, an, y == 4 ? cmu(y) : f(x <= 3 ? "dn_wall_cmu_tile_white" : "dn_wall_cmu_frp", "north"));
            }
        }
        for (int z = bk + 1; z < an; z++) {
            for (int y = 2; y <= 3; y++) {
                s.set(4, y, z, "dn_part_tile");
            }
        }
        // its flat roof, the coping, the air handler; the ceiling (the walk-in's own over it)
        for (int x = 0; x <= last; x++) {
            for (int z = bk + 1; z <= an; z++) {
                s.set(x, 5, z, b.wall("roof", 2));
                if (x == 0 || x == last || z == an) {
                    s.set(x, 6, z, "stone_trim");
                }
                if (x > 0 && x < last && z < an) {
                    s.set(x, 4, z, x >= last - 5 && z >= bk + 3 ? "dn_walkin" : droptile());
                }
            }
        }
        s.set(len / 2, 6, bk + 3, f("pk_aircon", "south"));
        // the restroom
        s.set(2, 2, an - 1, f("home_toilet", "north"));
        s.set(3, 2, bk + 2, f("home_sink_white", "west"));
        s.set(3, 3, bk + 2, f("home_mirror", "east"));
        s.set(2, 4, bk + 3, troffer());
        // the walk-in in the east corner: its box, the door, the shelves inside
        for (int x = last - 5; x < last; x++) {
            for (int y = 2; y <= 3; y++) {
                s.set(x, y, bk + 3, "dn_walkin");
                if (x == last - 5) {
                    s.set(x, y, an - 1, "dn_walkin");
                }
            }
        }
        b.door(last - 3, 2, bk + 3, "dn_walkin_door", "north");
        for (int x = last - 4; x < last; x++) {
            s.set(x, 2, an - 1, f(story == Story.LOOTED && chance() < 0.5 ? "dn_dry_low" : "dn_cold", "north"));
            s.set(x, 3, an - 1, f("dn_cold", "north"));
        }
        s.setIfAir(last - 1, 2, bk + 4, f("cardboard_box", dir()));
        s.set(last - 2, 4, bk + 4, troffer());
        // the prep counters under the pass, the ice machine, the dry stores against the restroom
        s.set(5, 2, bk + 1, f("dn_ice_lo", "south"));
        s.set(5, 3, bk + 1, f("dn_ice_hi", "south"));
        for (int x = 6; x <= last - 3; x++) {
            s.set(x, 2, bk + 1, f(x == pa || x == pa + 1 ? "dn_prep" : x % 3 == 0 ? "dn_lowboy" : "dn_prep_bare", "south"));
        }
        for (int z = bk + 2; z <= bk + 3; z++) {
            s.set(5, 2, z, f(story == Story.LOOTED ? "dn_dry_low" : "dn_dry", "east"));
            s.set(5, 3, z, f("dn_dry", "east"));
        }
        // the dish pit along the back, the back door and its step
        s.set(5, 2, an - 1, "dn_mop");
        s.set(6, 2, an - 1, f("dn_sink", "north"));
        s.set(7, 2, an - 1, f("dn_drain", "north"));
        s.set(8, 2, an - 1, f("dn_dish_lo", "north"));
        s.set(8, 3, an - 1, f("dn_dish_hi", "north"));
        b.door(9, 2, an, "fire_door", "south");
        s.set(9, 1, an + 1, "stairs_concrete[facing=north]");
        for (int x = 6; x < last - 5; x += 3) {
            s.set(x, 4, bk + 3, troffer());
        }

        // how it went
        aftermath(s, 1, 2, last - 1, 3, 2);
        aftermath(s, 5, bk + 2, last - 6, an - 2, 2);
        if (story == Story.HOLDOUT) {
            for (int x = ex; x <= ex + 1; x++) {
                for (int y = 2; y <= 3; y++) {
                    s.set(x, y, -1, barricade("north"));
                }
            }
            camp(s, 7, 2, bk + 2);
        }
    }

    // ------------------------------------------------------------------ the brick cafe

    /** The HILLTOP CAFE on a lot lw by ld (at least about 28 by 27). */
    void cafe(int lw, int ld) {
        int w = Math.max(20, Math.min(24, lw - 8)) & ~1;
        int cx = (lw - w) / 2, cz = Math.max(5, Math.min(11, ld - 22));
        asphalt(1, 0, lw - 2, cz - 5);
        if (cz >= 11) {
            spaces(7, lw - 3, cz - 5, 0.3);
        }
        walk(cx - 3, cz - 4, cx + w + 2, cz - 1);
        // the letterboard on its legs by the road
        String mv = story == Story.WALKED ? "a" : "b";
        q.set(2, 1, 1, f("dn_marquee_leg", "north"));
        q.set(4, 1, 1, f("dn_marquee_leg", "north"));
        sign(q, 4, 3, 1, "north", 3, 2, i -> "dn_marquee_" + mv + "_" + i);
        Placer s = q.shifted(cx, 0, cz);
        brick(s, w, 0, "c");
        // the hanging sign on its mast at the corner
        boolean lit = power && chance() > 0.2;
        for (int y = 1; y <= 6; y++) {
            s.set(w + 1, y, -5, "dn_mast");
        }
        for (int z = -4; z <= -2; z++) {
            s.set(w + 1, 6, z, "dn_mast_arm[axis=z]");
        }
        sign(s, w + 1, 5, -2, "east", 3, 2, i -> chance() < hit * 0.15 ? null : "dn_hang_" + i + (lit ? "_on" : ""));
        yard(s, 2, 19);
    }

    /** The ROUTE 9 TRUCK STOP on a lot lw by ld (at least about 40 by 34). */
    void truckStop(int lw, int ld) {
        int w = 22, ww = lw >= 56 ? 10 : 0, bx = 3, cz = Math.max(6, Math.min(12, ld - 24));
        gravel(1, 0, lw - 2, ld - 2);
        walk(bx - 3, cz - 4, bx + w + ww + 2, cz - 1);
        // the diesel canopy and its islands to the east, the scale beside it
        int fx0 = bx + w + ww + 6, fx1 = lw - 4, fz1 = Math.min(ld - 16, 17);
        if (fx1 - fx0 >= 13 && fz1 >= 12) {
            new GasStation(q, rng, hit).forecourt(fx0, fx1, 3, fz1);
            for (int x = fx0; x < fx0 + 12 && x <= fx1; x++) {
                for (int z = fz1 + 4; z <= fz1 + 5; z++) {
                    q.set(x, 0, z, "dn_scale");
                    clear(q, x, z);
                }
            }
        } else if (lw - (bx + w + ww) > 10) {
            new GasStation(q, rng, hit).storePumps(bx + w + ww + 4, 6);
        }
        // the trucks left round the back
        for (int i = 0; i < 3 && ld - 6 >= cz + 23; i++) {
            if (chance() < 0.75) {
                q.vehicle(bx + 6 + i * 10, 1, ld - 6, 90, TRUCKS, rng.nextLong());
            }
        }
        Placer s = q.shifted(bx, 0, cz);
        brick(s, w, ww, "t");
        if (cz >= 11) {
            for (int x = 0; x < w + ww; x += 3) {
                s.set(x + 1, 1, -5, "dn_wheelstop[axis=x]");
                s.set(x + 2, 1, -5, "dn_wheelstop[axis=x]");
                if (chance() < 0.3) {
                    s.vehicle(x + 2, 1, -7.5, chance() < 0.5 ? 0 : 180, CARS, rng.nextLong());
                }
            }
        }
        yard(s, 2, 19);
    }

    /**
     * The brick building at s, w wide and 18 deep, its floor at y 0 and its storefront on z 0
     * under the canopy; ww more blocks east (0 for none) hold the truck stop's store and showers.
     * v picks the signboard: "c" the cafe's, "t" the truck stop's.
     */
    private void brick(Placer s, int w, int ww, String v) {
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        int d = 18, pz = 9, lastZ = d - 1, W = w + ww, dx = 7, dx2 = ww > 0 ? w + 4 : -1;
        String inner = pick("pine", "teal", "tile");
        String part = inner.equals("pine") ? "dn_part_frp_pine" : inner.equals("teal") ? "dn_part_frp_teal" : "dn_part_quilt_tile";
        String ck = v.equals("t") ? "r" : "t";
        String vin = pick("r", "b", "g");
        for (int x = -2; x <= W + 1; x++) {
            for (int z = -4; z <= d; z++) {
                clear(s, x, z);
                for (int y = 2; y <= 8; y++) {
                    s.set(x, y, z, "air");
                }
            }
        }
        // the slab: checker in the dining room, quarry tile in the kitchen, mosaic in the restrooms
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < d; z++) {
                String fl;
                if (x >= w) {
                    fl = z < pz ? "gs_vct" : "dn_mosaic";
                } else if (z < pz) {
                    fl = x <= 6 && z >= 4 ? "dn_mosaic" : chance() < (z >= 6 ? 0.4 : 0.12) ? "dn_checker_worn" : "dn_checker";
                } else {
                    fl = chance() < 0.2 ? "dn_quarry_grease" : "dn_quarry";
                }
                s.set(x, 0, z, fl);
            }
        }
        // the walls: brick outside; pine, teal or tile in the dining room, white panel in the kitchen
        for (int z = 0; z < d; z++) {
            for (int y = 1; y <= 5; y++) {
                boolean shell = y >= 4 || z == 0 || z == lastZ;
                String west = z < pz ? (z >= 5 ? "tile_white" : inner) : "frp";
                String east = ww > 0 ? (z < pz ? "frp" : "tile_white") : z < pz ? inner : "frp";
                s.set(0, y, z, shell ? b.wall("brick_red", y) : f("dn_wall_brick_" + west, "east"));
                s.set(W - 1, y, z, shell ? b.wall("brick_red", y) : f("dn_wall_brick_" + east, "west"));
            }
            s.set(0, 6, z, "stone_trim");
            s.set(W - 1, 6, z, "stone_trim");
        }
        for (int x = 1; x < W - 1; x++) {
            for (int y = 1; y <= 5; y++) {
                s.set(x, y, lastZ, y >= 4 ? b.wall("brick_red", y) : f("dn_wall_brick_" + (x >= w ? "tile_white" : "frp"), "north"));
            }
            s.set(x, 6, lastZ, "stone_trim");
            s.set(x, 1, 0, b.wall("brick_red", 1));
            s.set(x, 4, 0, b.wall("brick_red", 4));
            s.set(x, 5, 0, b.wall("brick_red", 5));
            s.set(x, 6, 0, "stone_trim");
        }
        // the storefront: the lettered windows, plain sashes, the doors with transoms over them
        int p0 = dx + 2;
        boolean painted = !(story == Story.HOLDOUT);
        for (int x = 1; x < W - 1; x++) {
            if (x == w - 1) {
                s.set(x, 2, 0, b.wall("brick_red", 2));
                s.set(x, 3, 0, b.wall("brick_red", 3));
                continue;
            }
            if (x == dx || x == dx2) {
                s.set(x, 3, 0, sash("m", "hi", "x"));
                continue;
            }
            int c = p0 + 5 - x;
            if (painted && c >= 0 && c < 6 && chance() > hit * 0.4) {
                s.set(x, 3, 0, f("dn_paintwin_" + c, "north"));
                s.set(x, 2, 0, f("dn_paintwin_" + (6 + c), "north"));
            } else {
                s.set(x, 2, 0, sash("m", "lo", "x"));
                s.set(x, 3, 0, sash("m", "hi", "x"));
            }
        }
        b.door(dx, 1, 0, "dn_door", "north");
        if (dx2 > 0) {
            b.door(dx2, 1, 0, "shop_door", "north");
            b.door(w - 1, 1, 2, "home_door_white", "east");
        }
        for (int z = 2; z <= 3 && ww == 0; z++) {
            for (int y = 2; y <= 3; y++) {
                s.set(W - 1, y, z, sash("m", y == 2 ? "lo" : "hi", "z"));
            }
        }
        if (ww > 0) {
            for (int z = 1; z < lastZ; z++) {
                for (int y = 1; y <= 4; y++) {
                    if (z != 2 || y > 2) {
                        s.set(w - 1, y, z, b.wall("brick_red", y));
                    }
                }
            }
        }
        // the canopy over the walk: the soffit and its tubes, the mansard round its edge, its posts
        boolean tubes = power && chance() > 0.2;
        for (int x = -1; x <= W; x++) {
            for (int z = -3; z <= -1; z++) {
                String c;
                if (z == -3) {
                    c = x == -1 ? f("dn_mansard_corner", "north") : x == W ? f("dn_mansard_corner", "east") : f("dn_mansard", "north");
                } else if (x == -1 || x == W) {
                    c = f("dn_mansard", x == -1 ? "west" : "east");
                } else {
                    c = z == -2 && Math.floorMod(x, 4) == 1 ? "dn_soffit_tube" + (tubes && chance() > 0.15 ? "_on" : "") + "[axis=x]" : "dn_soffit";
                }
                s.set(x, 4, z, c);
            }
        }
        for (int x : new int[] {-1, W / 2, W}) {
            for (int y = 1; y <= 3; y++) {
                s.set(x, y, -3, "dn_mast");
            }
        }
        // the signboard on the parapet over the canopy, the cola sign round the side
        int b0 = (W - 8) / 2;
        sign(s, b0 + 7, 5, -1, "north", 8, 1, i -> chance() < hit * 0.12 ? null : "dn_board_" + v + "_" + i);
        sign(s, -1, 3, 5, "west", 2, 1, i -> "dn_cola_" + i);
        // the walk: the chalkboard easel, the paper boxes, a bin
        String ch = pick("a", "b");
        s.set(dx - 2, 1, -2, f("dn_chalk_" + ch + "_lo", "north"));
        s.set(dx - 2, 2, -2, f("dn_chalk_" + ch + "_hi", "north"));
        s.set(dx + 2, 1, -2, chance() < hit * 0.4 ? f("ext_trash_can_tipped", dir()) : "ext_trash_can");
        for (int i = 0; i < 2; i++) {
            s.set(dx - 4 - i, 1, -2, f("gs_news_" + rng.nextInt(3), "north"));
        }

        // the dining room: booths with pendants over them, the restrooms, stools, the counter, globes over it
        booths(s, 1, dx - 1, 1, 1, vin, 3);
        booths(s, dx + 1, w - 2, 1, 1, vin, 3);
        for (int x = 1; x <= 6; x++) {
            for (int y = 1; y <= 3; y++) {
                s.set(x, y, 4, "dn_part_tile");
            }
        }
        for (int z = 5; z < pz; z++) {
            for (int y = 1; y <= 3; y++) {
                s.set(3, y, z, "dn_part_tile");
                s.set(6, y, z, "dn_part_tile");
            }
        }
        b.door(2, 1, 4, "home_door_white", "north");
        b.door(4, 1, 4, "home_door_white", "north");
        s.set(3, 2, 3, f("gs_sign_restrooms", "north"));
        for (int x : new int[] {1, 5}) {
            int sx = x == 1 ? 2 : 4;
            s.set(x, 1, pz - 1, f("home_toilet", "north"));
            s.set(sx, 1, pz - 1, f("home_sink_white", "north"));
            s.set(sx, 2, pz - 1, f("home_mirror", "south"));
            s.set(sx, 3, 6, light());
        }
        int c1 = w - 5;
        for (int x = 8; x <= c1; x += 2) {
            s.set(x, 1, 4, stool(vin));
        }
        counter(s, 8, c1, 1, 5, ck, 8);
        for (int x = 9; x <= c1; x += 3) {
            s.set(x, 3, 5, on("dn_globe", 0.2));
        }
        for (int x = 2; x < w - 1; x += 4) {
            s.set(x, 3, 2, light());
        }
        // the back bar, the menu board over it, the partition with the pass and the kitchen door
        int pa = w - 8, mb = 9;
        String[] tops = {"dn_urn", "dn_brewer", "dn_juice", "dn_mixer", "dn_toaster"};
        int t = rng.nextInt(tops.length);
        for (int x = 8; x <= c1; x++) {
            s.set(x, 1, pz - 1, f("dn_backbar", "north"));
            if (x >= mb && x < mb + 3 || x >= pa && x < pa + 3) {
                continue;
            }
            String top = tops[t++ % tops.length];
            if (!(story != Story.WALKED && chance() < 0.4)) {
                s.set(x, 2, pz - 1, f(top.equals("dn_brewer") ? on(top, 0.3) : top, "north"));
            }
        }
        sign(s, mb + 2, 3, pz - 1, "north", 3, 2, i -> "dn_menuboard_" + i);
        s.set(w - 2, 1, pz - 1, "dn_mop");
        for (int x = 1; x < w - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                s.set(x, y, pz, x <= 6 ? "dn_part_tile" : f(part, "south"));
            }
        }
        for (int x = pa; x < pa + 3; x++) {
            s.set(x, 2, pz, f(on("dn_pass", 0.3), "south"));
        }
        b.door(w - 3, 1, pz, "home_door_white", "south");
        s.set(w - 3, 3, pz - 1, f(on("dn_clock", 0.5), "north"));
        s.set(1, 2, 2, f("dn_poster_" + rng.nextInt(4), "east"));

        // the kitchen: prep under the pass, the line facing it under the hoods, the walk-in behind
        for (int x = pa; x < pa + 3; x++) {
            s.set(x, 1, pz + 1, f("dn_prep", "south"));
        }
        s.set(pa - 2, 1, pz + 1, f("dn_lowboy", "south"));
        s.set(pa - 1, 1, pz + 1, f("dn_lowboy", "south"));
        String[] line = {"dn_griddle", "dn_griddle", "dn_fryer", "dn_fryer", "dn_range", "dn_range"};
        for (int x = pa - 3; x <= pa + 2; x++) {
            s.set(x, 1, pz + 3, f(line[x - pa + 3], "north"));
            s.set(x, 3, pz + 3, f(on("dn_hood", 0.2), "north"));
        }
        for (int x = w - 6; x <= w - 2; x++) {
            for (int y = 1; y <= 3; y++) {
                s.set(x, y, pz + 4, "dn_walkin");
                if (x == w - 6) {
                    for (int z = pz + 5; z < lastZ; z++) {
                        s.set(x, y, z, "dn_walkin");
                    }
                }
            }
        }
        b.door(w - 4, 1, pz + 4, "dn_walkin_door", "north");
        for (int x = w - 5; x <= w - 2; x++) {
            s.set(x, 1, lastZ - 1, f(story == Story.LOOTED && chance() < 0.5 ? "dn_dry_low" : "dn_cold", "north"));
            s.set(x, 2, lastZ - 1, f("dn_cold", "north"));
        }
        s.setIfAir(w - 2, 1, pz + 5, f("cardboard_box", dir()));
        s.set(w - 3, 3, pz + 6, light());
        // the dish pit down the west wall, the dry stores along the back with the back door among them
        s.set(1, 1, pz + 1, f("dn_ice_lo", "east"));
        s.set(1, 2, pz + 1, f("dn_ice_hi", "east"));
        s.set(1, 1, pz + 2, f("dn_sink", "east"));
        s.set(1, 1, pz + 3, f("dn_drain", "east"));
        s.set(1, 1, pz + 4, f("dn_dish_lo", "east"));
        s.set(1, 2, pz + 4, f("dn_dish_hi", "east"));
        s.set(1, 1, pz + 5, f("dn_drain", "east"));
        s.set(1, 1, lastZ - 1, "dn_mop");
        int bd = 10;
        for (int x = 2; x <= w - 7; x++) {
            if (x != bd) {
                s.set(x, 1, lastZ - 1, f(story == Story.LOOTED && chance() < 0.5 ? "dn_dry_low" : "dn_dry", "north"));
                s.set(x, 2, lastZ - 1, f("dn_dry", "north"));
            }
        }
        if (story != Story.HOLDOUT) {
            for (int x = 4; x <= 6; x++) {
                s.set(x, 1, pz + 4, f(chance() < 0.5 ? "dn_prep" : "dn_prep_bare", "north"));
            }
        }
        b.door(bd, 1, lastZ, "fire_door", "south");
        // the ceilings: tiles over everything, tubes over the kitchen, the walk-in's own
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z < lastZ; z++) {
                boolean walkin = x >= w - 6 && x < w - 1 && z >= pz + 4;
                s.set(x, 4, z, walkin ? "dn_walkin" : droptile());
            }
        }
        for (int x = 3; x < pa - 3; x += 4) {
            for (int z : new int[] {pz + 2, pz + 6}) {
                if (empty(s, x, 3, z)) {
                    s.set(x, 3, z, (power && chance() > 0.2 ? "fit_tube_on" : "fit_tube") + "[axis=x]");
                }
            }
        }
        // the roof: the deck, the fans over the line and the dining room
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z < lastZ; z++) {
                s.set(x, 5, z, b.wall("roof", 2));
            }
        }
        s.set(pa, 6, pz + 3, f("pk_aircon", "south"));
        s.set(w / 2, 6, 4, f("pk_aircon", "south"));
        if (ww > 0) {
            wing(s, b, w, W - 2, pz, lastZ, dx2);
        }

        // how it went
        aftermath(s, 1, 2, w - 2, 3, 1);
        aftermath(s, 2, pz + 2, w - 7, lastZ - 2, 1);
        if (story == Story.HOLDOUT) {
            for (int y = 1; y <= 2; y++) {
                s.set(dx, y, -1, barricade("south"));
                if (dx2 > 0) {
                    s.set(dx2, y, -1, barricade("south"));
                }
            }
            camp(s, 5, 1, pz + 3);
        }
    }

    /** The truck stop's store (front) and the drivers' showers (back) between x0 and x1. */
    private void wing(Placer s, Bldg b, int x0, int x1, int pz, int lastZ, int door) {
        boolean looted = story != Story.WALKED;
        // the till counter by the door, the tobacco wall behind it, the CB shop's radios along the wall
        for (int z = 1; z <= 3; z++) {
            s.set(x1 - 2, 1, z, f("gs_counter_c", "west"));
            s.set(x1, 1, z, f("gs_cig_lo", "west"));
            s.set(x1, 2, z, f("gs_cig_hi", "west"));
        }
        s.set(x1 - 2, 1, 4, f("gs_counter_c", "south"));
        if (!(story == Story.LOOTED && chance() < 0.5)) {
            s.set(x1 - 2, 2, 2, f("store_register", "west"));
        }
        s.set(x1, 1, 5, f("dn_cb", "west"));
        s.set(x1, 1, 6, f("dn_cb", "west"));
        // a gondola down the middle, the coolers across the back
        for (int z = 3; z <= 6; z++) {
            s.set(x0 + 2, 1, z, shelf("west", looted));
            s.set(x0 + 3, 1, z, shelf("east", looted));
        }
        for (int x = x0; x <= x0 + 4 && x < door; x++) {
            String st = looted ? pick("empty", "half", "full") : "full";
            String lit = power && chance() > 0.1 ? "_on" : "";
            s.set(x, 1, pz - 1, f("gs_cool_" + st + "_lo" + lit, "north"));
            s.set(x, 2, pz - 1, f("gs_cool_" + (looted ? pick("empty", "half") : "full") + "_hi" + lit, "north"));
            s.set(x, 3, pz - 1, f("gs_cool_head_" + (x - x0) % 4, "north"));
        }
        for (int x = x0 + 1; x < x1; x += 4) {
            s.set(x, 3, 2, light());
            s.set(x, 3, 6, light());
        }
        // the showers behind: the partition, the sign by its door, the stalls along the back, the sinks
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y <= 3; y++) {
                s.set(x, y, pz, "dn_part_tile");
            }
        }
        b.door(x1 - 1, 1, pz, "home_door_white", "south");
        s.set(x1 - 2, 2, pz - 1, f("dn_sign_showers", "north"));
        for (int x = x0 + 1; x < x1; x++) {
            s.set(x, 1, lastZ - 1, f("dn_shower", "north"));
        }
        for (int x = x0; x <= x0 + 2; x++) {
            s.set(x, 1, pz + 1, f("home_sink_white", "south"));
            s.set(x, 2, pz + 1, f("home_mirror", "north"));
        }
        // the drivers' lockers down the far wall, a chair or two to dress on, the laundry and the mop
        for (int z = pz + 2; z <= lastZ - 3; z++) {
            boolean forced = chance() < (looted ? 0.4 : 0.1);
            s.set(x1, 1, z, f("fit_locker_lo", "west"));
            s.set(x1, 2, z, f("fit_locker_hi", "west"));
            if (forced) {
                s.set(x1 - 1, 1, z, chance() < 0.5 ? "home_clothes_pile" : "fit_laundry_bag");
            }
        }
        s.set(x0 + 4, 1, pz + 3, f(chance() < hit * 0.5 ? "trade_folding_chair_tipped" : "trade_folding_chair", "east"));
        if (chance() < 0.6) {
            s.set(x0 + 5, 1, pz + 4, f("trade_folding_chair", pick("north", "west")));
        }
        s.set(x0, 1, lastZ - 2, "fit_laundry_bag");
        s.set(x0, 1, lastZ - 3, chance() < 0.5 ? "fit_laundry_bag" : "home_clothes_pile");
        s.set(x0 + 3, 1, pz + 1, "sc_bucket");
        s.set(x0 + 2, 3, pz + 4, light());
        s.set(x1 - 2, 3, pz + 4, light());
        aftermath(s, x0, 1, x1 - 3, 7, 1);
    }

    /** A gondola block facing that way: stocked, picked over, bare, or pulled over. */
    private String shelf(String facing, boolean looted) {
        double r = chance();
        if (looted) {
            return f(r < 0.45 ? "store_shelf_empty" : r < 0.55 ? "store_shelf_fallen" : "store_shelf_grocery", facing);
        }
        return f(r < 0.9 ? "store_shelf_grocery" : "store_shelf_empty", facing);
    }
}
