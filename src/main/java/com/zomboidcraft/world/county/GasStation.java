package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The gas station on the county road. There are no photos of one in the library yet, so this
 * follows what the early-nineties stations of the rural upper South are known for. Three builds:
 * <ul>
 * <li>the canopy station of one of two made-up brands (CARDINAL, BLUEGRASS): the canopy banded in
 * the brand's colours over two curbed islands of dispensers, the price sign on its pylon by the
 * road, the food mart behind with its own little canopy over the walk, the ice merchandiser, the
 * propane cage, the newspaper boxes, the payphone round the side, air and vacuum by the lot; inside
 * the counter with the tobacco wall behind it, the lottery case, the food counter (coffee, roller
 * grill, fountain, slush), the cooler wall across the back, gondolas of snacks, the restroom and
 * the stockroom;</li>
 * <li>the old VALLEY service station: white enamel, the office and two bays (a car still up on the
 * lift in one), the old pumps with their globes on one island, the round sign on its pole;</li>
 * <li>the country store with its pumps out front (see RoadsideSite).</li>
 * </ul>
 * The power is still on at some (a generator out back): the canopy lit, the prices glowing, the
 * coolers humming, the beer neon in the window. Every one tells how it went: looted bare,
 * barricaded by someone who meant to hold it, or simply walked away from; the harder the stop was
 * hit, the more glass is out and the more of the canopy came down.
 *
 * Drawn in its lot's frame: x along the road, z back from it (the road is to the north, -z).
 */
final class GasStation {
    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final String CARS = "sedan,hatchback,pickup,suv,minivan,wagon,coupe,van";

    private enum Story { WALKED, LOOTED, HOLDOUT }

    private final Placer q;
    private final SplittableRandom rng;
    private final double hit;
    private final boolean power;
    private final Story story;
    /** The brand, "c" (CARDINAL) or "g" (BLUEGRASS). */
    private final String br;

    GasStation(Placer q, SplittableRandom rng, double hit) {
        this.q = q;
        this.rng = rng;
        this.hit = hit;
        power = rng.nextDouble() < 0.35;
        double r = rng.nextDouble();
        story = r < 0.45 ? Story.WALKED : r < 0.8 ? Story.LOOTED : Story.HOLDOUT;
        br = rng.nextBoolean() ? "c" : "g";
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

    private boolean empty(Placer p, int x, int y, int z) {
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    /** Split-face block, now and then pocked, stained, sooty or mossy. */
    private String cmu(int y) {
        double r = chance();
        return r < 0.08 ? "sh_cmu_tan_pocks" : r < 0.14 ? "sh_cmu_tan_stain" : r < 0.18 && hit > 0.5 ? "sh_cmu_tan_soot"
                : r < 0.22 && y <= 1 ? "sh_cmu_tan_moss" : r < 0.25 ? "sh_cmu_tan_crack" : "sh_cmu_tan";
    }

    /** Forecourt concrete over a box of the lot: saw cuts every five blocks, oil where cars stood. */
    private void slab(Placer p, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                String s;
                if (z % 5 == 0) {
                    s = "gs_slab_j[facing=north]";
                } else if (x % 5 == 0) {
                    s = "gs_slab_j[facing=east]";
                } else {
                    s = r < 0.1 ? "gs_slab_oil" : r < 0.2 ? "gs_slab_crack" : "gs_slab";
                }
                p.set(x, 0, z, s);
                p.set(x, 1, z, "air");
                if (chance() < 0.03) {
                    p.set(x, 1, z, chance() < 0.6 ? "weeds" : "paper");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the canopy station

    /** The brand's canopy station on a lot lw by ld (at least about 36 by 31). */
    void modern(int lw, int ld) {
        int cx = lw / 2;
        int cw = Math.max(14, Math.min(22, lw - 10)) & ~1;
        int x0 = cx - cw / 2, x1 = x0 + cw - 1;
        int sd = Math.max(9, Math.min(12, ld - 20));
        int storeZ = ld - sd - 1;
        int z0 = 3, z1 = Math.min(z0 + 11, storeZ - 7);
        slab(q, 1, 0, lw - 2, storeZ - 1);
        canopy(x0, x1, z0, z1);
        islands(x0, x1, z0, z1);
        int sw = Math.max(14, Math.min(22, lw - 12));
        int sx = cx - sw / 2;
        mart(q.shifted(sx, 0, storeZ), sw, sd);
        // parking nose-in along the mart's walk, wheel stops at the curb
        for (int x = sx; x < sx + sw; x++) {
            int z = storeZ - 3;
            if ((x - sx) % 3 == 0) {
                for (int k = 1; k <= 4; k++) {
                    if (chance() < 0.85) {
                        q.set(x, 0, z - k, "road_line_white[axis=z]");
                    }
                }
            } else if ((x - sx) % 3 == 1) {
                q.set(x, 1, z, "park_wheelstop[facing=north]");
                q.set(x + 1, 1, z, "park_wheelstop[facing=north]");
                if (chance() < 0.3) {
                    q.vehicle(x + 1, 1, z - 2.5, chance() < 0.5 ? 0 : 180, CARS, rng.nextLong());
                }
            }
        }
        // the tanks' fill caps and vents to one side, air and vacuum at the other
        int fx = x1 + 3 < lw - 2 ? x1 + 3 : x0 - 4;
        q.set(fx, 0, z0 + 2, "gs_slab_fill");
        q.set(fx, 0, z0 + 3, "gs_slab_fill");
        int ax = sx > 6 ? sx - 3 : sx + sw + 2;
        q.set(ax, 1, storeZ - 2, "gs_air[facing=" + (ax < sx ? "east" : "west") + "]");
        q.set(ax, 1, storeZ - 5, "gs_vac[facing=" + (ax < sx ? "east" : "west") + "]");
        q.set(ax, 1, storeZ - 4, "gs_bollard");
        priceSign(cx < lw / 2 ? lw - 4 : 2, 1);
        if (hit > 0.65) {
            int bx = x0 + 3 + rng.nextInt(Math.max(1, cw - 6));
            for (int x = bx - 3; x <= bx + 3; x++) {
                for (int z = z0 + 2; z <= z1 - 2; z++) {
                    if (chance() < 0.6) {
                        q.set(x, 0, z, chance() < 0.5 ? "burnt_ground" : "scorch");
                    }
                }
            }
            q.vehicle(bx + 0.5, 1, (z0 + z1) / 2.0 + 0.5, 90, "sedan,pickup,suv", rng.nextLong());
        }
    }

    /** The canopy: the deck at y = 6, its edge the fascia (the name front and back), lamps let into it. */
    private void canopy(int x0, int x1, int z0, int z1) {
        int xc = (x0 + x1) / 2;
        boolean burnt = hit > 0.65;
        int hx = burnt ? x0 + 2 + rng.nextInt(Math.max(1, x1 - x0 - 6)) : -99, hz = z0 + 2 + rng.nextInt(Math.max(1, z1 - z0 - 4));
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                boolean hole = burnt && Math.abs(x - hx) <= 2 && Math.abs(z - hz) <= 2 && chance() < 0.75;
                String s;
                if (edge) {
                    s = "gs_fascia_" + br;
                    int i = z == z0 ? xc + 3 - x : z == z1 ? x - (xc - 2) : -1;
                    if (i >= 0 && i < 6) {
                        s = "gs_fascia_" + br + "_name_" + i + (power && chance() > 0.15 ? "_on" : "") + "[facing=" + (z == z0 ? "north" : "south") + "]";
                    }
                    if (hole) {
                        s = "air";
                    }
                } else if (hole) {
                    s = chance() < 0.5 ? "air" : "gs_deck_hole";
                    if (s.equals("air") && chance() < 0.5) {
                        q.set(x, 1, z, pick("debris_chunk", "gs_slab_crack"));
                    }
                } else if ((x - x0) % 3 == 2 && (z - z0) % 3 == 2) {
                    double r = chance();
                    s = r < hit * 0.35 ? "gs_deck_light_smash" : power && r > 0.12 ? "gs_deck_light_on" : "gs_deck_light";
                } else {
                    s = chance() < hit * 0.05 ? "gs_deck_hole" : "gs_deck";
                }
                q.set(x, 6, z, s);
            }
        }
        for (int i = 0; i < 4; i++) {
            q.setIfAir(x0 + 1 + rng.nextInt(x1 - x0 - 1), 7, z0 + 1 + rng.nextInt(z1 - z0 - 1), pick("moss_carpet", "weeds", "paper"));
        }
    }

    /**
     * Two islands along x under the canopy, each with its noses, the two columns standing on it, the
     * dispensers between (and the squeegee bucket and the bin), cars left at some of the pumps.
     */
    private void islands(int x0, int x1, int z0, int z1) {
        int ia = x0 + 2, ib = x1 - 2;
        int[] zs = {z0 + 3, z1 - 3};
        for (int zi : zs) {
            q.set(ia, 1, zi, "gs_island_nose[facing=south]");
            q.set(ib, 1, zi, "gs_island_nose[facing=north]");
            int c0 = ia + 2, c1 = ib - 2;
            for (int x = ia + 1; x < ib; x++) {
                q.set(x, 0, zi, "concrete_dark");
                String s;
                if (x == c0 || x == c1) {
                    s = "gs_column_base_" + br;
                    for (int y = 2; y <= 5; y++) {
                        q.set(x, y, zi, "gs_column_" + br + (y == 4 ? "_band" : ""));
                    }
                } else if (x > c0 && x < c1 && (x - c0) % 3 == 2) {
                    pump(x, zi);
                    continue;
                } else if (x == ia + 1) {
                    s = "gs_squeegee[facing=" + (chance() < 0.5 ? "north" : "south") + "]";
                } else if (x == ib - 1) {
                    s = chance() < 0.7 ? "gs_island_bin" : "gs_island";
                } else {
                    s = "gs_island";
                }
                q.set(x, 1, zi, s);
            }
            // cars left at the pumps on either side
            for (int side : new int[] {-2, 2}) {
                if (chance() < 0.35 + hit * 0.2) {
                    q.vehicle(c0 + 2 + rng.nextInt(Math.max(1, c1 - c0 - 3)) + 0.5, 1, zi + side + 0.5, chance() < 0.5 ? 90 : 270, CARS, rng.nextLong());
                }
            }
            for (int x = ia; x <= ib; x++) {
                for (int dz : new int[] {-1, 1}) {
                    if (chance() < 0.25) {
                        q.set(x, 0, zi + dz, "gs_slab_oil");
                    }
                }
            }
        }
    }

    /** One dispenser on an island at (x, zi), its faces north and south: whole, smashed or lit. */
    private void pump(int x, int zi) {
        boolean smash = chance() < hit * 0.45;
        if (chance() < hit * 0.12) {
            // knocked off its base by a car
            q.set(x, 1, zi, "gs_island");
            q.setIfAir(x, 1, zi + (chance() < 0.5 ? 1 : -1), "debris_chunk");
            return;
        }
        String f = chance() < 0.5 ? "north" : "south";
        q.set(x, 1, zi, "gs_pump_" + br + "_lo" + (smash ? "_smash" : "") + "[facing=" + f + "]");
        q.set(x, 2, zi, "gs_pump_" + br + "_hi" + (smash ? "_smash" : power && chance() > 0.2 ? "_on" : "") + "[facing=" + f + "]");
    }

    /** The price sign at (px, pz): three blocks along z, five high on two posts, its faces up and down the road. */
    private void priceSign(int px, int pz) {
        for (int y = 1; y <= 4; y++) {
            q.set(px, y, pz, "gs_pylon_" + br);
            q.set(px, y, pz + 2, "gs_pylon_" + br);
        }
        q.set(px, 0, pz, "concrete_dark");
        q.set(px, 0, pz + 2, "concrete_dark");
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 3; c++) {
                int i = r * 3 + c;
                String s = "gs_price_" + br + "_" + i + (power ? "_on" : "");
                if (!power && r >= 2 && chance() < hit * 0.3) {
                    continue;  // a panel gone
                }
                q.set(px, 9 - r, pz + 2 - c, s + "[facing=east]");
            }
        }
    }

    // ------------------------------------------------------------------ the food mart

    /** The mart, w wide and d deep, its front at z = 0 facing the forecourt, its walk out in front. */
    private void mart(Placer s, int w, int d) {
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        int dx = w / 2 - 1;
        // the floor, the walls (split-face outside, panel or drywall inside), the roof
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean wall = x == 0 || x == w - 1 || z == 0 || z == d - 1;
                s.set(x, 0, z, wall ? "concrete_dark" : vct(x, z, dx));
                for (int y = 1; y <= 4; y++) {
                    s.set(x, y, z, "air");
                }
                if (wall) {
                    String in = x == 0 ? "east" : x == w - 1 ? "west" : z == d - 1 ? "north" : "south";
                    boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
                    for (int y = 1; y <= 4; y++) {
                        s.set(x, y, z, corner || y == 4 || z == 0 ? cmu(y)
                                : (y == 1 ? "gs_wall_" + br : "gs_wall_dry") + "[facing=" + in + "]");
                    }
                    s.set(x, 5, z, "stone_trim");
                } else {
                    s.set(x, 4, z, b.wall("roof", 2));
                }
            }
        }
        // the front: glass between the end piers, the doors in the middle, the walk canopy over it
        for (int x = 1; x < w - 1; x++) {
            if (x == dx || x == dx + 1) {
                continue;
            }
            boolean broken = chance() < hit * 0.6;
            boolean boarded = story == Story.HOLDOUT && chance() < 0.75;
            String lo = boarded ? "boarded_window[axis=x]" : broken ? "gs_front_broken_lo[facing=north]"
                    : chance() < 0.3 ? "gs_front_poster_" + rng.nextInt(6) + "[facing=north]" : "gs_front_lo[facing=north]";
            String hi = boarded ? "boarded_window[axis=x]" : broken && chance() < 0.7 ? "gs_front_broken_hi[facing=north]" : "gs_front_hi[facing=north]";
            s.set(x, 1, 0, lo);
            s.set(x, 2, 0, hi);
            if (broken && !boarded) {
                s.setIfAir(x, 1, -1, "glass_shards");
                s.setIfAir(x, 1, 1, "glass_shards");
            }
        }
        b.doors(dx, 1, 0, "shop_door", "north");
        walkCanopy(s, w);
        // outside along the walk: the ice, the propane, the papers, a bin, bollards at the curb
        for (int i = 0; i < 4; i++) {
            int r = i / 2, c = i % 2;
            s.set(2 - c, 2 - r, -1, "gs_ice_" + i + "[facing=north]");
        }
        s.set(w - 3, 1, -1, "gs_propane_l[facing=north]");
        s.set(w - 2, 1, -1, "gs_propane_r[facing=north]");
        for (int k = 0; k < 2 + rng.nextInt(2); k++) {
            s.set(dx - 2 - k, 1, -2, chance() < hit * 0.3 ? "gs_news_" + rng.nextInt(3) + "[facing=" + dir() + "]" : "gs_news_" + k % 3 + "[facing=north]");
        }
        s.set(dx + 3, 1, -2, chance() < hit * 0.4 ? "ext_trash_can_tipped[facing=" + dir() + "]" : "ext_trash_can");
        for (int x = 1; x < w - 1; x += 3) {
            if (x != dx && x != dx + 1 && x != dx + 2) {
                s.set(x, 1, -3, "gs_bollard");
            }
        }
        s.set(-1, 2, 3, "gs_payphone[facing=west]");
        s.set(-1, 1, 3, chance() < 0.3 ? "paper" : "air");
        for (int z = 1; z < d - 1; z += 4) {
            if (z != 3) {
                s.set(-1, 3, z, "ext_porch_light" + (power ? "_lit" : ""));
            }
        }
        // the back: the vent pipes, the dumpster, the generator if the lights are on
        s.set(w - 3, 1, d, "gs_vent_lo");
        s.set(w - 3, 2, d, "gs_vent_hi");
        s.set(2, 1, d + 1, "dumpster[facing=north]");
        s.setIfAir(3, 1, d + 1, "trash_bag[facing=" + dir() + "]");
        if (power) {
            s.set(5, 1, d + 1, "bunk_gen_engine[facing=north]");
        }
        s.set(w / 2, 5, d / 2, "pk_aircon[facing=south]");
        s.set(w / 2 + 3, 5, d / 2 + 1, "pk_aircon[facing=south]");
        inside(s, b, w, d, dx);
    }

    /** The floor tile: vinyl, now and then the accent, mud tracked in from the door. */
    private String vct(int x, int z, int dx) {
        if (z <= 3 && x >= dx - 1 && x <= dx + 2 && chance() < 0.6) {
            return "gs_vct_mud";
        }
        return (x + z * 3) % 7 == 0 ? "gs_vct_b" : "gs_vct";
    }

    /** The walk's canopy at y = 4: soffit lamps over the door, the store's name on its fascia. */
    private void walkCanopy(Placer s, int w) {
        int xc = w / 2;
        for (int x = -1; x <= w; x++) {
            s.set(x, 4, -1, x % 3 == 1 && x > 0 && x < w - 1 ? (power && chance() > 0.2 ? "gs_deck_light_on" : "gs_deck_light") : "gs_deck");
            int i = xc + 3 - x;
            String f = "gs_fascia_" + br;
            if (i >= 0 && i < 8) {
                f = "gs_store_" + br + "_" + i + (power && chance() > 0.15 ? "_on" : "") + "[facing=north]";
            }
            s.set(x, 4, -2, f);
            s.set(x, 0, -1, "gs_slab");
            s.set(x, 0, -2, "gs_slab");
        }
    }

    /** The mart's fit-out between the walls (x 1..w-2, z 1..d-2). */
    private void inside(Placer s, Bldg b, int w, int d, int dx) {
        boolean looted = story != Story.WALKED;
        int cxr = w - 5;  // the counter's line, the clerk between it and the tobacco wall
        // the stockroom and the restroom across the back right, behind a partition
        int px = w - 7, pz = d - 5;
        for (int y = 1; y <= 3; y++) {
            for (int x = px; x <= w - 2; x++) {
                s.set(x, y, pz, "gs_wall_dry[facing=north]");
            }
            for (int z = pz + 1; z <= d - 2; z++) {
                s.set(px, y, z, "gs_wall_dry[facing=west]");
                s.set(w - 4, y, z, "gs_wall_dry[facing=east]");
            }
        }
        b.door(w - 6, 1, pz, "home_door_white", "north");
        b.door(w - 3, 1, pz, "home_door_white", "north");
        s.set(w - 2, 2, pz - 1, "air");
        s.set(w - 3, 3, pz, "gs_sign_restrooms[facing=north]");
        s.set(w - 6, 3, pz, "gs_sign_office[facing=north]");
        s.set(w - 2, 1, d - 2, "home_toilet[facing=north]");
        s.set(w - 3, 1, d - 2, "home_sink_white[facing=north]");
        s.set(w - 3, 2, d - 2, "home_mirror[facing=south]");
        s.set(w - 6, 1, d - 2, "pk_metal_rack[facing=north]");
        s.set(w - 5, 1, d - 2, "store_safe[facing=north]");
        s.set(w - 5, 1, d - 3, "cardboard_box[facing=" + dir() + "]");
        s.set(w - 6, 1, d - 3, chance() < 0.5 ? "cardboard_box[facing=" + dir() + "]" : "air");
        // the counter facing the door, the tills and the lottery case on it, the tobacco wall behind
        for (int z = 1; z <= 4; z++) {
            s.set(cxr, 1, z, (z == 3 ? "gs_counter_" + br + "_candy" : "gs_counter_" + br) + "[facing=west]");
            s.set(w - 2, 1, z, "gs_cig_lo[facing=west]");
            s.set(w - 2, 2, z, "gs_cig_hi[facing=west]");
        }
        s.set(cxr, 2, 1, "gs_impulse[facing=west]");
        s.set(cxr, 2, 2, "store_register[facing=west]");
        s.set(cxr, 2, 3, "gs_lotto[facing=west]");
        if (chance() < 0.5) {
            s.set(cxr, 2, 4, "store_register[facing=west]");
        }
        s.set(cxr, 1, 5, "gs_counter_" + br + "[facing=south]");
        s.set(w - 2, 3, 2, "gs_sign_card[facing=west]");
        s.set(w - 3, 1, 4, chance() < 0.5 ? "home_chair[facing=west]" : "pk_stool_1[facing=west]");
        s.set(cxr - 2, 1, 1, "bk_atm[facing=south]");
        s.set(cxr - 1, 1, 6, "gs_freezer[facing=west]");
        // the food counter down the left wall, the menu boards over it
        int fz0 = 2, fz1 = Math.min(6, d - 5);
        String[] food = {"gs_coffee", "gs_cups", "gs_roller", "gs_fountain", "gs_slush"};
        for (int z = fz0; z <= fz1; z++) {
            s.set(1, 1, z, "gs_fs_cab[facing=east]");
            String f = food[(z - fz0) % food.length];
            if (!f.equals("gs_cups") && !f.equals("gs_fountain")) {
                f = on(f, 0.2);
            }
            if (!(looted && chance() < 0.25)) {
                s.set(1, 2, z, f + "[facing=east]");
            }
            if (z - fz0 < 3) {
                s.set(1, 3, z, "gs_menu_" + (fz1 - fz0 >= 2 ? 2 - (z - fz0) : 0) + "[facing=east]");
            }
        }
        s.set(1, 1, d - 3, chance() < 0.5 ? "gs_bread[facing=east]" : "gs_mags[facing=east]");
        // magazines and the beer neon in the front window
        s.set(2, 1, 1, "gs_mags[facing=south]");
        s.set(3, 1, 1, looted && chance() < 0.5 ? "gs_mags_low[facing=south]" : "gs_mags[facing=south]");
        s.set(2, 2, 1, (power && chance() > 0.2 ? "gs_neon_beer_on" : "gs_neon_beer") + "[facing=north]");
        s.set(w - 3, 2, 1, (power && chance() > 0.3 ? "gs_neon_lotto_on" : "gs_neon_lotto") + "[facing=north]");
        boolean open = power && chance() > 0.25;
        s.set(dx - 2, 2, 1, "neon_open_1" + (open ? "" : "_off") + "[facing=north]");
        s.set(dx - 1, 2, 1, "neon_open_0" + (open ? "" : "_off") + "[facing=north]");
        // the cooler wall across the back, the sign over it
        for (int x = 1; x < px; x++) {
            String st = coolerState(looted);
            String lit = power && chance() > 0.1 ? "_on" : "";
            s.set(x, 1, d - 2, "gs_cool_" + st + "_lo" + lit + "[facing=north]");
            s.set(x, 2, d - 2, "gs_cool_" + (st.equals("smash") ? "smash" : coolerState(looted)) + "_hi" + lit + "[facing=north]");
            s.set(x, 3, d - 2, "gs_cool_head_" + (x - 1) % 4 + "[facing=north]");
        }
        // gondolas down the middle, chips at the front ends, oil at the back
        for (int gx = 4; gx + 1 <= cxr - 3; gx += 4) {
            for (int z = 4; z <= d - 6; z++) {
                s.set(gx, 1, z, shelf("west", looted));
                s.set(gx + 1, 1, z, shelf("east", looted));
            }
            s.set(gx, 1, 3, (looted ? "gs_chips_low" : "gs_chips") + "[facing=north]");
            s.set(gx + 1, 1, 3, (looted && chance() < 0.6 ? "gs_chips_low" : "gs_chips") + "[facing=north]");
            s.set(gx, 1, d - 5, (looted ? "gs_oil_low" : "gs_oil") + "[facing=south]");
            s.set(gx + 1, 1, d - 5, "gs_oil[facing=south]");
        }
        s.set(1, 2, d - 3, "gs_mirror[facing=east]");
        // the drop ceiling, its troffers in rows, tiles come down where the roof leaks
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                if (!empty(s, x, 3, z)) {
                    continue;
                }
                String c = "fit_ceiling";
                if (x % 3 == 1 && z % 3 == 2) {
                    double r = chance();
                    c = power ? (r < 0.75 ? "fit_troffer_on" : r < 0.9 ? "fit_troffer_flicker" : "fit_troffer") : "fit_troffer";
                } else if (chance() < hit * 0.12) {
                    c = "fit_ceiling_open";
                    s.setIfAir(x, 1, z, "fit_ceiling_fallen[facing=" + dir() + "]");
                }
                s.set(x, 3, z, c + (c.equals("fit_ceiling") || c.startsWith("fit_troffer") ? "" : "[facing=" + dir() + "]"));
            }
        }
        story(s, w, d, dx, cxr);
    }

    private String coolerState(boolean looted) {
        double r = chance();
        if (r < hit * 0.25) {
            return "smash";
        }
        r = chance();
        if (looted) {
            return r < 0.5 ? "empty" : r < 0.85 ? "half" : "full";
        }
        return r < 0.75 ? "full" : "half";
    }

    /** A gondola block facing that way: stocked, picked over, bare, or pulled over. */
    private String shelf(String facing, boolean looted) {
        double r = chance();
        if (looted) {
            return (r < 0.45 ? "store_shelf_empty" : r < 0.55 ? "store_shelf_fallen" : "store_shelf_grocery") + "[facing=" + facing + "]";
        }
        return (r < 0.9 ? "store_shelf_grocery" : "store_shelf_empty") + "[facing=" + facing + "]";
    }

    /** What happened here: the floor after the looters, the holdout's barricade, the dead. */
    private void story(Placer s, int w, int d, int dx, int cxr) {
        int n = story == Story.WALKED ? 3 : 10;
        for (int i = 0; i < n; i++) {
            int x = 2 + rng.nextInt(Math.max(1, w - 4)), z = 1 + rng.nextInt(Math.max(1, d - 3));
            s.setIfAir(x, 1, z, pick("paper", "paper", "trash_bag[facing=" + dir() + "]", "cardboard_box[facing=" + dir() + "]", "glass_shards",
                    "pk_trash_bag[facing=" + dir() + "]"));
        }
        if (story == Story.HOLDOUT) {
            // the doors held with what was to hand; a bed behind the counter, water, the lantern
            s.set(dx, 1, 1, "store_shelf_fallen[facing=north]");
            s.set(dx + 1, 1, 1, "cardboard_box[facing=" + dir() + "]");
            s.set(dx, 1, 2, "cardboard_box[facing=" + dir() + "]");
            s.set(w - 3, 1, 2, "sc_cot_head[facing=south]");
            s.set(w - 3, 1, 1, "sc_cot_foot[facing=south]");
            s.set(w - 4, 1, 3, power || chance() < 0.5 ? "fit_lantern_on" : "fit_lantern");
            s.set(w - 4, 1, 1, "bunk_drum_water");
            s.set(1 + rng.nextInt(3), 2, d - 2, "air");
            s.set(w - 4, 2, 4, "graffiti_" + rng.nextInt(8) + "[facing=east]");
        }
        if (hit > 0.5 || story == Story.HOLDOUT && chance() < 0.5) {
            for (int i = 0, tries = 0; i < 2 && tries < 20; tries++) {
                int x = 2 + rng.nextInt(Math.max(1, w - 6)), z = 2 + rng.nextInt(Math.max(1, d - 5));
                if (empty(s, x, 1, z) && empty(s, x + 1, 1, z)) {
                    s.set(x, 1, z, "home_corpse_head[facing=east]");
                    s.set(x + 1, 1, z, "home_corpse_foot[facing=east]");
                    s.setIfAir(x, 1, z + 1, pick("home_blood_pool", "home_blood_drag", "blood_splat"));
                    i++;
                }
            }
        }
    }

    // ------------------------------------------------------------------ the old service station

    /** The VALLEY service station on a lot lw by ld (at least about 32 by 28). */
    void valley(int lw, int ld) {
        int bw = 18, bd = 11, bx = (lw - bw) / 2, bz = Math.min(14, ld - bd - 2);
        slab(q, 1, 0, lw - 2, bz - 1);
        Placer s = q.shifted(bx, 0, bz);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        for (int x = 0; x < bw; x++) {
            for (int z = 0; z < bd; z++) {
                boolean wall = x == 0 || x == bw - 1 || z == 0 || z == bd - 1;
                s.set(x, 0, z, wall ? "concrete_dark" : x < 6 ? (chance() < 0.15 ? "gs_vct_mud" : "gs_vct") : "gs_bay_floor");
                for (int y = 1; y <= 6; y++) {
                    s.set(x, y, z, "air");
                }
                // five high inside, the headroom a car up on the hoist needs (VEHICLES' car_hoist)
                if (wall) {
                    String in = x == 0 ? "east" : x == bw - 1 ? "west" : z == bd - 1 ? "north" : "south";
                    for (int y = 1; y <= 5; y++) {
                        boolean corner = (x == 0 || x == bw - 1) && (z == 0 || z == bd - 1);
                        s.set(x, y, z, corner || z == 0 || x >= 6 ? (chance() < 0.25 + hit * 0.3 ? "gs_enamel_rust" : "gs_enamel")
                                : "gs_wall_enamel[facing=" + in + "]");
                    }
                    s.set(x, 6, z, "gs_enamel_band");
                    s.set(x, 7, z, "stone_trim");
                } else {
                    s.set(x, 6, z, b.wall("roof", 2));
                }
            }
        }
        // the office front: the big windows, the door; the partition into the bays
        for (int x = 1; x <= 4; x++) {
            boolean broken = chance() < hit * 0.6;
            s.set(x, 1, 0, broken ? "gs_front_broken_lo[facing=north]" : "gs_front_lo[facing=north]");
            s.set(x, 2, 0, broken && chance() < 0.6 ? "gs_front_broken_hi[facing=north]" : "gs_front_hi[facing=north]");
        }
        b.door(5, 1, 0, "shop_door", "north");
        for (int z = 1; z < bd - 1; z++) {
            for (int y = 1; y <= 5; y++) {
                s.set(6, y, z, z == 3 && y <= 2 ? "air" : "gs_wall_enamel[facing=west]");
            }
        }
        b.door(6, 1, 3, "home_door_white", "east");
        // two bays, 4 wide: the first open with a car up on the lift, the second shut or half up
        for (int bay = 0; bay < 2; bay++) {
            int x0 = 8 + bay * 5;
            int rolled = bay == 0 ? 3 : chance() < 0.4 ? 1 : 0;  // door sections rolled up out of the way
            for (int i = 0; i < 4; i++) {
                for (int y = 1; y <= 4; y++) {
                    int row = y == 4 ? 0 : y == 3 ? 2 : y == 2 ? 1 : 3;
                    s.set(x0 + i, y, 0, y <= rolled ? "air" : "gs_bay_" + row + "[facing=north]");
                }
            }
        }
        // the name over the bays, the band elsewhere
        int xc = 12;
        for (int i = 0; i < 6; i++) {
            s.set(xc + 3 - i, 6, 0, "gs_valley_" + i + "[facing=north]");
        }
        s.set(15, 7, 4, "pk_aircon[facing=south]");
        // the office: the counter and till, the soda machine, the oil display, the tyre rack, a desk
        s.set(1, 1, 3, "store_counter[facing=north]");
        s.set(2, 1, 3, "store_register[facing=north]");
        s.set(3, 1, 3, "store_counter[facing=north]");
        s.set(1, 1, bd - 2, "pk_vending[facing=north]");
        s.set(2, 1, bd - 2, "gs_oilcans[facing=north]");
        s.set(3, 1, bd - 2, "pk_desk_metal[facing=north]");
        s.set(3, 1, bd - 3, "home_chair[facing=south]");
        s.set(4, 1, bd - 2, "pk_file_cabinet[facing=north]");
        s.set(5, 1, bd - 2, "gs_tirerack[facing=north]");
        s.set(5, 2, bd - 2, "air");
        s.set(2, 2, bd - 1, "air");
        s.set(4, 2, bd - 2, "home_calendar[facing=south]");
        s.set(1, 1, 1, "store_vending[facing=south]");
        s.set(1, 2, 1, "store_vending_top[facing=south]");
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z < bd - 1; z++) {
                if (empty(s, x, 4, z)) {
                    s.set(x, 4, z, x % 2 == 1 && z % 3 == 1 ? (power ? "fit_troffer_on" : "fit_troffer") : "fit_ceiling");
                }
            }
        }
        // the bays: the lift with its car, the tyre machines, the compressor, the bench, the drums
        // the two-post hoist (VEHICLES' car_hoist) in the first bay, a customer's car up on it, its key on the board in the office
        s.vehicle(10.0, 1, 4.5, 0, "!hoist:" + rng.nextInt(4) + ";" + CARS + (chance() < 0.5 ? "@usable" : "@abandoned") + "#customer", rng.nextLong());
        s.set(5, 2, 5, "as_keyboard[facing=west]");
        s.set(14, 1, 6, "gs_tirechanger[facing=west]");
        s.set(15, 1, 8, "gs_balancer[facing=west]");
        s.set(16, 1, bd - 2, "gs_compressor_lo");
        s.set(16, 2, bd - 2, "gs_compressor_hi[facing=west]");
        s.set(14, 2, bd - 2, "gs_hosereel[facing=north]");
        s.set(7, 1, bd - 2, "pk_tool_chest[facing=north]");
        s.set(8, 1, bd - 2, "store_pegboard[facing=north]");
        s.set(9, 1, bd - 2, "store_table");
        s.set(11, 1, bd - 2, "barrel");
        s.set(12, 1, bd - 2, chance() < 0.5 ? "barrel" : "tire[axis=x]");
        s.set(7, 1, 2, "tire[axis=z]");
        s.set(7, 2, 2, "tire[axis=z]");
        for (int x = 7; x < bw - 1; x++) {
            for (int z = 1; z < bd - 1; z++) {
                if (x % 4 == 1 && z % 4 == 2 && empty(s, x, 5, z)) {
                    s.set(x, 5, z, power && chance() > 0.25 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
                }
            }
        }
        // the island out front under its little canopy, the old pumps with their globes
        int iz = bz - 6;
        q.set(bx + 3, 1, iz, "gs_island_nose[facing=south]");
        q.set(bx + 13, 1, iz, "gs_island_nose[facing=north]");
        for (int x = bx + 4; x <= bx + 12; x++) {
            q.set(x, 1, iz, x == bx + 8 ? "gs_island_bin" : "gs_island");
        }
        for (int x : new int[] {bx + 5, bx + 11}) {
            boolean rust = chance() < 0.5 + hit * 0.3;
            if (chance() < hit * 0.25) {
                q.set(x, 1, iz, "gs_oldpump_lo_rust[facing=north]");
                q.set(x, 2, iz, "gs_oldpump_hi_smash[facing=north]");
            } else {
                q.set(x, 1, iz, "gs_oldpump_lo" + (rust ? "_rust" : "") + "[facing=north]");
                q.set(x, 2, iz, "gs_oldpump_hi" + (rust ? "_rust" : "") + "[facing=north]");
            }
        }
        if (chance() < 0.7) {
            for (int x = bx + 1; x <= bx + 15; x++) {
                for (int z = iz - 2; z < bz; z++) {
                    boolean edge = x == bx + 1 || x == bx + 15 || z == iz - 2;
                    q.set(x, 5, z, edge ? "gs_enamel_band" : (x - bx) % 4 == 1 && z == iz ? on("gs_deck_light", 0.2) : "gs_deck");
                }
            }
            for (int y = 1; y <= 4; y++) {
                q.set(bx + 1, y, iz - 2, "gs_pylon_c");
                q.set(bx + 15, y, iz - 2, "gs_pylon_c");
            }
        }
        // the round sign on its pole by the road, its faces up and down the road
        int sx = bx > 8 ? 2 : lw - 3;
        for (int y = 1; y <= 5; y++) {
            q.set(sx, y, 2, "gs_pylon_c");
        }
        for (int i = 0; i < 4; i++) {
            int r = i / 2, c = i % 2;
            q.set(sx, 7 - r, 2 + 1 - c, "gs_valley_sign_" + i + "[facing=east]");
        }
        // tyres stacked by the bays, drums, the tow truck that never went back out, the junk pile
        for (int i = 0; i < 6; i++) {
            q.set(bx + bw + 1 + i / 3, 1 + i % 3 / 2, bz + 1 + i % 3, "tire[axis=" + (chance() < 0.5 ? "x" : "z") + "]");
        }
        q.set(bx - 2, 1, bz + 1, "barrel");
        q.set(bx - 2, 1, bz + 2, "barrel");
        q.set(bx - 1, 2, bz + 4, "gs_payphone[facing=west]");
        q.vehicle(bx + bw + 5, 1, bz + 5, 0, "tow_truck,pickup", rng.nextLong());
        q.set(bx + bw - 3, 1, bz + bd + 1, "dumpster[facing=north]");
        q.setIfAir(bx + bw - 2, 1, bz + bd + 1, "trash_bag[facing=" + dir() + "]");
        for (int i = 0; i < 5; i++) {
            s.setIfAir(1 + rng.nextInt(bw - 2), 1, 1 + rng.nextInt(bd - 2), pick("paper", "glass_shards", "trash_bag[facing=" + dir() + "]", "cardboard_box[facing=" + dir() + "]"));
        }
        if (hit > 0.7) {
            for (int x = bx + 6; x <= bx + 10; x++) {
                for (int z = iz - 1; z <= iz + 1; z++) {
                    if (chance() < 0.6) {
                        q.set(x, 0, z, chance() < 0.5 ? "burnt_ground" : "scorch");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the country store's pumps

    /** A canopy over its two islands on their slab, x0..x1 by z0..z1 (the truck stop's diesel). */
    void forecourt(int x0, int x1, int z0, int z1) {
        slab(q, x0 - 2, z0 - 2, x1 + 2, z1 + 2);
        canopy(x0, x1, z0, z1);
        islands(x0, x1, z0, z1);
    }

    /** Two pumps on an island at (x, z) along x in front of a country store: old ones, or the brand's. */
    void storePumps(int x, int z) {
        boolean old = chance() < 0.65;
        q.set(x - 1, 1, z, "gs_island_nose[facing=south]");
        q.set(x + 3, 1, z, "gs_island_nose[facing=north]");
        q.set(x + 1, 1, z, "gs_island");
        for (int px : new int[] {x, x + 2}) {
            q.set(px, 0, z, "concrete_dark");
            if (old) {
                boolean rust = chance() < 0.6;
                q.set(px, 1, z, "gs_oldpump_lo" + (rust ? "_rust" : "") + "[facing=north]");
                q.set(px, 2, z, (chance() < hit * 0.3 ? "gs_oldpump_hi_smash" : "gs_oldpump_hi" + (rust ? "_rust" : "")) + "[facing=north]");
            } else {
                pump(px, z);
            }
        }
        q.set(x + 1, 0, z, "concrete_dark");
    }
}
