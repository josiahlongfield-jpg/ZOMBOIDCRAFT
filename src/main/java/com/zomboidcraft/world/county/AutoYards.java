package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The two big yards of the auto trade (Josiah 2026-10-31: the scrapyard by Foundry Row, the truck
 * depot off I-88), in {@link AutoShop}'s kit and with its helpers, after refs/industry
 * (scrap-yard-magnets) and refs/roadside/truck-stop. Drawn in the lot's frame, x along the street,
 * z back from it (the street to the north).
 * <ul>
 * <li>Calder Salvage, across the street from the Row: the office and pulling shed at the front
 * (AutoShop's SALVAGE: the counter, the used-parts shelves, two bays where cars come apart, one on
 * a hoist), behind the tin fence the yard: the gate and the truck scale with its scale house, the
 * gravel lanes, the pick rows of wrecks, the fluids pad where a car is drained before it's
 * crushed, the crusher with the next car waiting and the stacks of flats, the magnet gantry over
 * them, the parts shed with its bins, the tyre heap, the scrap mounds at the back.</li>
 * <li>I-88 Truck &amp; Diesel: the steel truck hall, three bays seven wide with roll-up doors (a pit
 * under the first, mobile column lifts round the truck in the second, a hoist for vans and light
 * trucks in the third), the heavy parts and big tyres, the office with its key board; the diesel
 * canopy out front, the trucks waiting their turn out back.</li>
 * </ul>
 */
final class AutoYards {
    private static final String CARS = "sedan,hatchback,coupe,wagon,suv,pickup,minivan,van";
    private static final String TRUCKS = "box_truck,moving_truck,step_van,garbage_truck,school_bus,city_bus,tow_truck";
    private static final String[] FLATS = {"red", "blue", "white", "green", "rust", "tan"};

    private final Placer q;
    private final SplittableRandom rng;
    private final double hit;
    private final AutoShop kit;

    AutoYards(Placer q, SplittableRandom rng, double hit) {
        this.q = q;
        this.rng = rng;
        this.hit = hit;
        this.kit = new AutoShop(q, rng, hit);
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ Calder Salvage

    /** The yard on a lot w by d (about 139 square: two city blocks). */
    void scrapyard(int w, int d) {
        int fz = 8, gx0 = 40, gx1 = 47;
        ground(w, d, fz);
        // the office and the pulling shed on the street, the frontage for the yard's own trucks
        new AutoShop(q.shifted(3, 0, 0), rng, hit).build(AutoShop.Kind.SALVAGE, 26, 20, "salvage");
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < fz; z++) {
                if (q.get(x, 0, z) == null) {
                    q.set(x, 0, z, chance() < 0.12 ? "gs_slab_crack" : "gs_slab");
                }
            }
        }
        q.vehicle(gx1 + 6.5, 1, 3.5, 90, "tow_truck", rng.nextLong());
        fence(w, d, fz, 4, 25, gx0, gx1);
        scale(gx0, gx1, fz);
        lanes(w, d, fz, gx0, gx1);
        int px0 = 58, px1 = 84;
        crusherPad(px0, px1, fz + 4, fz + 26);
        partsShed(4, 25, fz + 14, fz + 22);
        rows(4, 36, fz + 34, d - 28);
        rows(52, w - 24, fz + 36, d - 34);
        tyreHeap(14, d - 20, 5);
        mound(w - 26, d - 20, 12);
        mound(w - 56, d - 24, 6);
        mound(w - 14, fz + 44, 6);
        // the yard's name over the gate between its two steel posts
        for (int y = 1; y <= 7; y++) {
            q.set(gx0, y, fz, y == 1 ? "as_column_base" : "as_column");
            q.set(gx1, y, fz, y == 1 ? "as_column_base" : "as_column");
        }
        for (int c = 0; c < 6; c++) {
            q.set(gx0 + 6 - c, 6, fz, "as_s_salvage_" + c + "0[facing=north]");
        }
        q.set(gx0 - 2, 2, fz - 1, "as_sign_keepout[facing=north]");
    }

    /** The yard floor: gravel and dirt, worn to mud and oil where the loaders turn. */
    private void ground(int w, int d, int fz) {
        for (int x = 2; x < w - 2; x++) {
            for (int z = fz + 1; z < d - 2; z++) {
                double r = chance();
                q.set(x, 0, z, r < 0.45 ? "mu_gravel" : r < 0.7 ? "mu_gravel_b" : r < 0.9 ? "minecraft:coarse_dirt" : "minecraft:dirt");
                q.set(x, 1, z, "air");
            }
        }
    }

    /**
     * The tin fence round the yard, two high on rusted posts every four: along the front from the
     * shed's ends to the gate, down the sides, along the back; a panel down here and there.
     */
    private void fence(int w, int d, int fz, int shed0, int shed1, int gx0, int gx1) {
        for (int x = 1; x <= w - 2; x++) {
            boolean front = x < shed0 || x > shed1 && (x < gx0 || x > gx1);
            if (front) {
                tin(x, fz, "north", x % 4 == 0);
            }
            tin(x, d - 2, "north", x % 4 == 0);
        }
        for (int z = fz; z <= d - 2; z++) {
            tin(1, z, "east", z % 4 == 0);
            tin(w - 2, z, "west", z % 4 == 0);
        }
        // the gate's leaves swung open inside
        for (int z = fz + 1; z <= fz + 4; z++) {
            for (int y = 1; y <= 2; y++) {
                q.set(gx0 + 1, y, z, "as_tin_gate[facing=east]");
                q.set(gx1 - 1, y, z, "as_tin_gate[facing=west]");
            }
        }
    }

    private void tin(int x, int z, String facing, boolean post) {
        boolean down = chance() < 0.03 + hit * 0.04;
        for (int y = 1; y <= 2; y++) {
            q.set(x, y, z, down && y == 2 ? "air" : (post ? "as_tin_post" : "as_tin") + "[facing=" + facing + "]");
        }
        if (down) {
            q.set(x + (facing.equals("north") ? 0 : facing.equals("east") ? 1 : -1), 1, z + (facing.equals("north") ? 1 : 0), "as_scrap");
        }
    }

    /** The truck scale just in the gate, its deck flush, the scale house beside it with the window on it. */
    private void scale(int gx0, int gx1, int fz) {
        int sx0 = gx0 + 2, sz0 = fz + 4, sz1 = fz + 15;
        for (int x = sx0; x <= sx0 + 3; x++) {
            for (int z = sz0; z <= sz1; z++) {
                q.set(x, 0, z, "as_scale");
            }
        }
        Placer s = q.shifted(gx1 + 1, 0, sz0 + 2);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        b.walls(0, 0, 4, 4, 1, 4, "sh_cmu_tan");
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                s.set(x, 0, z, chance() < 0.2 ? "gs_vct_mud" : "gs_vct");
                for (int y = 1; y <= 3; y++) {
                    s.set(x, y, z, "air");
                }
                s.set(x, 3, z, "fit_ceiling");
            }
        }
        for (int z = 1; z <= 3; z++) {
            boolean broken = chance() < hit * 0.6;
            s.set(0, 2, z, broken ? "gs_front_broken_hi[facing=west]" : "gs_front_hi[facing=west]");
        }
        b.door(2, 1, 4, "home_door_white", "south");
        s.set(1, 1, 2, "as_desk[facing=west]");
        s.set(2, 1, 2, "home_chair[facing=west]");
        s.set(1, 1, 1, "pk_file_cabinet[facing=east]");
        s.set(3, 1, 1, "as_coffee[facing=south]");
        s.set(-1, 3, 2, "as_sign_scale[facing=west]");
        b.flat(0, 0, 4, 4, 4, "sh_cmu_tan", 0, "stone_trim");
    }

    /** The gravel lanes: in from the gate to the back, across the yard, along the back to the mounds. */
    private void lanes(int w, int d, int fz, int gx0, int gx1) {
        for (int x = gx0; x <= gx1; x++) {
            for (int z = fz; z < d - 8; z++) {
                lane(x, z);
            }
        }
        for (int z = fz + 28; z <= fz + 33; z++) {
            for (int x = 4; x < w - 4; x++) {
                lane(x, z);
            }
        }
        for (int z = d - 14; z <= d - 9; z++) {
            for (int x = 4; x < w - 4; x++) {
                lane(x, z);
            }
        }
    }

    private void lane(int x, int z) {
        String at = q.get(x, 0, z);
        if (at != null && at.equals("as_scale")) {
            return;
        }
        double r = chance();
        q.set(x, 0, z, r < 0.6 ? "mu_gravel" : r < 0.8 ? "minecraft:dirt_path" : r < 0.9 ? "mu_gravel_b" : "minecraft:coarse_dirt");
    }

    /**
     * The concrete pad by the gantry: the fluids station where a car is drained before it goes in,
     * the crusher with the next one waiting, the stacks of flats it makes, and the magnet gantry
     * over the lot.
     */
    private void crusherPad(int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                q.set(x, 0, z, r < 0.15 ? "gs_slab_oil" : r < 0.25 ? "gs_slab_crack" : (x - x0) % 5 == 0 || (z - z0) % 5 == 0 ? "gs_slab_j[facing=north]" : "gs_slab");
            }
        }
        // the crusher: its bed along z, the posts at its corners, the lid raised, the cab at the end
        int cx = x0 + 8, cz0 = z0 + 4, cz1 = z0 + 11;
        for (int x = cx; x <= cx + 2; x++) {
            for (int z = cz0; z <= cz1; z++) {
                q.set(x, 1, z, "as_crusher_bed[axis=z]");
                q.set(x, 5, z, "as_crusher_lid[axis=z]");
            }
        }
        for (int[] c : new int[][] {{cx - 1, cz0}, {cx + 3, cz0}, {cx - 1, cz1}, {cx + 3, cz1}}) {
            for (int y = 1; y <= 5; y++) {
                q.set(c[0], y, c[1], "as_crusher_post[facing=" + (c[0] < cx ? "east" : "west") + "]");
            }
        }
        q.set(cx + 1, 1, cz1 + 1, "as_crusher_cab[facing=south]");
        q.vehicle(cx + 1.5, 1, cz1 + 6.5, 180, CARS + "@wrecked", rng.nextLong());
        // the fluids station: drained into the drums and the tote before the crusher, the oil on the pad
        for (int x = x0 + 1; x <= x0 + 5; x++) {
            q.set(x, 1, z1 - 1, x == x0 + 3 ? "as_tote[facing=north]" : "as_drum_" + pick("oil", "oil", "atf", "coolant"));
        }
        q.set(x0 + 2, 1, z1 - 3, "as_drain[facing=north]");
        q.set(x0 + 4, 1, z1 - 3, "as_drain[facing=north]");
        q.vehicle(x0 + 3.5, 1, z1 - 7.5, 0, CARS + "@abandoned", rng.nextLong());
        q.set(x0 + 1, 2, z1, "as_sign_wasteoil[facing=north]");
        // the flats in their stacks east of the crusher, each car a 4 by 2 slab, three to six high
        for (int sz = z0 + 1; sz + 1 <= z1 - 2; sz += 3) {
            for (int sx = cx + 6; sx + 3 <= x1 - 1; sx += 6) {
                if (chance() < 0.2) {
                    continue;
                }
                int h = 3 + rng.nextInt(4);
                for (int y = 1; y <= h; y++) {
                    String flat = "as_flat_" + FLATS[rng.nextInt(FLATS.length)] + "[axis=x]";
                    int skew = y > 1 && chance() < 0.3 ? (chance() < 0.5 ? -1 : 1) : 0;
                    for (int x = sx; x <= sx + 3; x++) {
                        for (int z = sz; z <= sz + 1; z++) {
                            q.set(x + skew, y, z, flat);
                        }
                    }
                }
            }
        }
        gantry(cx - 3, x1 + 1, (z0 + z1) / 2);
    }

    /**
     * The magnet gantry: two legs a side, the end trucks on them, the double girder across, the
     * trolley on top over a stack, the rope down through the girders to the magnet.
     */
    private void gantry(int xa, int xb, int zc) {
        int g = 12;
        for (int x : new int[] {xa, xb}) {
            for (int z : new int[] {zc - 1, zc + 1}) {
                for (int y = 1; y < g; y++) {
                    q.set(x, y, z, y == 1 ? "as_column_base" : "as_column");
                }
            }
            for (int z = zc - 1; z <= zc + 1; z++) {
                q.set(x, g, z, "pw_crane_end[facing=" + (x == xa ? "west" : "east") + "]");
            }
        }
        for (int x = xa + 1; x < xb; x++) {
            q.set(x, g, zc - 1, "pw_crane[axis=x]");
            q.set(x, g, zc + 1, "pw_crane[axis=x]");
        }
        int tx = xa + 3 + rng.nextInt(Math.max(1, xb - xa - 6));
        for (int x = tx - 1; x <= tx + 1; x++) {
            for (int z = zc - 1; z <= zc + 1; z++) {
                q.set(x, g + 1, z, "pw_trolley[facing=north]");
            }
        }
        int low = 8;
        for (int y = low + 1; y <= g; y++) {
            q.set(tx, y, zc, "pw_rope");
        }
        q.set(tx, low, zc, "as_chain");
        q.set(tx, low - 1, zc, "as_magnet");
        q.set(xb - 1, g - 1, zc + 1, "pw_crane_cab[facing=west]");
    }

    /**
     * The pick rows, x0..x1 by z0..z1: the wrecks side by side, nose to the aisle, the aisles
     * between the rows with what was pulled off them left lying.
     */
    private void rows(int x0, int x1, int z0, int z1) {
        for (int z = z0; z + 5 <= z1; z += 11) {
            boolean north = (z / 11) % 2 == 0;
            for (double x = x0 + 1.5; x <= x1 - 1.5; x += 3.2) {
                if (chance() < 0.42) {
                    double r = chance();
                    String state = r < 0.6 ? "@wrecked" : r < 0.8 ? "@burnt" : "@abandoned";
                    q.vehicle(x, 1, z + 2.5, north ? 0 : 180, CARS + state, rng.nextLong());
                } else if (chance() < 0.35) {
                    // where a car was: the engine it gave up, its wheels, a door
                    int bx = (int) x, bz = z + 1 + rng.nextInt(3);
                    q.setIfAir(bx, 1, bz, pick("as_engine[facing=north]", "as_trans[facing=east]", "tire[axis=x]", "as_seat[facing=south]",
                            "as_cardoor[facing=north]", "as_radiator[facing=north]"));
                }
            }
            // the aisle: a hood off one, a bumper, tyres rolled out of the way
            for (int i = 0; i < 4; i++) {
                int ax = x0 + rng.nextInt(Math.max(1, x1 - x0)), az = z + 7 + rng.nextInt(2);
                if (az < z1) {
                    q.setIfAir(ax, 1, az, pick("as_hood[facing=north]", "as_bumper[facing=north]", "tire[axis=z]", "as_wheels", "as_cores[facing=" + kit.dir() + "]"));
                }
            }
        }
    }

    /**
     * The parts shed behind the office: tin back and sides, posts along its open front, rows of
     * bins of pulled parts, the engines on pallets, doors and bumpers stood against the back.
     */
    private void partsShed(int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                q.set(x, 0, z, chance() < 0.15 ? "gs_slab_oil" : "gs_slab");
                for (int y = 1; y <= 4; y++) {
                    q.set(x, y, z, "air");
                }
                q.set(x, 5, z, "as_metal_rust");
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int y = 1; y <= 4; y++) {
                q.set(x, y, z1, "as_metal_tan");
            }
            if ((x - x0) % 5 == 0 || x == x1) {
                for (int y = 1; y <= 4; y++) {
                    q.set(x, y, z0, y == 1 ? "as_column_base" : "as_column");
                }
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            for (int y = 1; y <= 4; y++) {
                q.set(x0, y, z, "as_metal_tan");
                q.set(x1, y, z, "as_metal_tan");
            }
        }
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            if ((x - x0) % 5 == 0) {
                continue;
            }
            q.set(x, 1, z0 + 2, "as_partsbin[facing=" + kit.dir() + "]");
            q.set(x, 1, z0 + 4, chance() < 0.7 ? "as_partsbin[facing=" + kit.dir() + "]" : pick("as_engine[facing=north]", "as_trans[facing=north]"));
            q.set(x, 1, z1 - 1, pick("as_cardoor[facing=north]", "as_bumper[facing=north]", "as_hood[facing=north]", "as_radiator[facing=north]",
                    "as_seat[facing=north]", "as_wheels"));
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            q.set(x, 4, (z0 + z1) / 2, kit.on("as_strip", 0.3) + "[axis=x]");
        }
        q.set(x0 + 1, 3, z0, "as_sign_parts[facing=north]");
    }

    /** The heap of old tyres in the back corner, three high in the middle, the cages beside it. */
    private void tyreHeap(int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double t = Math.hypot(x - cx, z - cz) / r;
                int h = (int) Math.round(3.4 * (1 - t) + chance() * 0.6);
                for (int y = 1; y <= h; y++) {
                    q.set(x, y, z, "tire[axis=" + pick("x", "z") + "]");
                }
            }
        }
        q.set(cx + r + 2, 1, cz, "as_tyre_cage");
        q.set(cx + r + 2, 1, cz - 1, "as_tyre_cage");
    }

    /** A mound of scrap: rusted panel heaped up, the loose scrap over it, bins and drums round its foot. */
    private void mound(int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                double t = Math.hypot(x - cx, z - cz) / r;
                if (t > 1) {
                    continue;
                }
                int h = (int) Math.round(5.5 * (1 - t * t) + chance() * 0.8);
                for (int y = 1; y < h; y++) {
                    q.set(x, y, z, "as_metal_rust");
                }
                if (h >= 1) {
                    q.set(x, h, z, chance() < 0.85 ? "as_scrap" : pick("as_engine[facing=north]", "as_radiator[facing=east]", "tire[axis=x]"));
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * (r + 2)), z = cz + (int) Math.round(Math.sin(a) * (r + 2));
            q.setIfAir(x, 1, z, pick("as_partsbin[facing=" + kit.dir() + "]", "as_drum_oil", "as_cores[facing=" + kit.dir() + "]"));
        }
    }

    // ------------------------------------------------------------------ I-88 Truck & Diesel

    /** The truck hall's bays: 7 wide inside, 18 deep, on a 9 pitch (2 between); walls to y 8, the roof at 9. */
    static final int TBAY_W = 7, TBAY_D = 18, TPITCH = 9, TTOP = 8;

    /** The depot on a lot lw by ld (at least about 66 by 56), its front on the road. */
    void truckDepot(int lw, int ld) {
        kit.classes = new String[] {"heavy"};
        int ow = 12, bx0 = ow + 2, n = 3, xe = bx0 + n * TPITCH, dd = TBAY_D + 5, sz = 12;
        Placer s = q.shifted(1, 0, sz);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        // the hall: ribbed steel on a block base, the office inside its west end
        b.walls(0, 0, xe, dd - 1, 1, TTOP + 1, "as_metal");
        for (int x = 0; x <= xe; x++) {
            for (int z = 0; z < dd; z++) {
                boolean edge = x == 0 || x == xe || z == 0 || z == dd - 1;
                s.set(x, 0, z, edge ? "concrete_dark" : kit.floor(false));
                for (int y = 1; y <= TTOP; y++) {
                    if (!edge) {
                        s.set(x, y, z, "air");
                    } else if (y <= 2) {
                        s.set(x, y, z, b.wall("concrete_panel", y));
                    }
                }
            }
        }
        for (int z = 1; z < dd - 1; z++) {
            for (int y = 1; y <= TTOP; y++) {
                s.set(ow + 1, y, z, "as_metal");
            }
        }
        b.door(ow + 1, 1, 2, "home_door_white", "east");
        b.door(ow + 1, 1, dd - 3, "home_door_white", "east");
        s.set(ow, 3, 2, "as_sign_keepout[facing=west]");
        kit.office(s, b, 1, ow, dd, "diesel", true);
        // the parts store behind the counter: rows of heavy stock three high, the aisle down the middle
        for (int z = 10; z <= dd - 6; z += 3) {
            for (int x = 4; x <= ow; x++) {
                if (x == 8) {
                    continue;
                }
                String[] kinds = {"filters", "brakes", "boxes", "batteries"};
                String[] tables = {"auto_suspension", "auto_brakes", "auto_exhaust", null};
                int k = rng.nextInt(kinds.length);
                for (int y = 1; y <= 3; y++) {
                    String part = "as_shelf_" + kinds[k] + (y == 1 ? "_lo" : "_hi");
                    s.set(x, y, z, tables[k] == null ? part + "[facing=north]" : kit.stock(part, "north", tables[k]));
                }
            }
        }
        for (int i = 0; i < n; i++) {
            truckBay(s, bx0 + i * TPITCH, i);
        }
        kit.backOfShop(s, bx0, xe - 1, TBAY_D + 1, dd - 2, false);
        // the big tyres stood along the back between the benches, the grease carts
        for (int x = bx0 + 1; x < xe - 1; x += 5) {
            s.set(x, 1, dd - 2, "as_bigtyre[facing=north]");
            s.set(x, 2, dd - 2, "as_bigtyre[facing=north]");
            s.setIfAir(x + 1, 1, TBAY_D + 1, "as_grease[facing=north]");
        }
        b.flat(0, 0, xe, dd - 1, TTOP + 1, "as_metal", 1, "stone_trim");
        s.set(4, TTOP + 2, dd / 2, "pk_aircon[facing=south]");
        kit.fascia(s, "diesel", ow + 1);
        // the apron for the trucks to swing in, the diesel canopy out front to the east
        kit.apron(q, 1, 1 + xe, 0, sz);
        int fx0 = xe + 6, fx1 = lw - 4, fz1 = Math.min(sz + 6, 17);
        if (fx1 - fx0 >= 13) {
            new GasStation(q, rng, hit).forecourt(fx0, fx1, 3, fz1);
        }
        kit.pylon(q, "diesel", 2, 0);
        // the lot out back: the trucks and buses waiting their turn, their keys on the office board
        int bz0 = sz + dd + 1;
        kit.apron(q, 1, lw - 2, bz0, ld - 1);
        for (double x = 4.5; x < lw - 4; x += 4.2) {
            if (chance() < 0.6) {
                q.vehicle(x, 1, bz0 + 7.5, chance() < 0.5 ? 0 : 180, TRUCKS + (chance() < 0.4 ? "@usable" : "@abandoned") + "#customer", rng.nextLong());
            }
        }
        // the side lot by the canopy: a tractor's worth of tyres, the scrap tyres, the tote, the dumpster
        int yx = xe + 4;
        if (yx + 6 < lw - 2) {
            for (int z = fz1 + 6; z < bz0 - 2; z += 2) {
                q.set(yx, 1, z, "as_bigtyre[facing=east]");
                q.set(yx + 1, 1, z, chance() < 0.5 ? "as_bigtyre[facing=east]" : "as_tyre_cage");
            }
            q.set(yx + 4, 1, bz0 - 3, "dumpster[facing=west]");
            q.set(yx + 4, 1, bz0 - 5, "as_tote[facing=west]");
        }
    }

    /**
     * One truck bay at x0 (inside x0..x0+6, z 1..18), its roll-up door in the front wall: the first
     * with the pit under the truck, the second with the column lifts round it, the third a light
     * bay with a hoist for vans and pickups. The strip east of it (x0+7, x0+8): the cabinet, the
     * grease cart, the reels on the wall.
     */
    private void truckBay(Placer s, int x0, int i) {
        // the door: rolled up all the way, part way or shut, its drum over the opening inside
        int up = chance() < 0.45 ? 6 : chance() < 0.5 ? 2 + rng.nextInt(4) : 0;
        for (int x = x0; x < x0 + TBAY_W; x++) {
            for (int y = 1; y <= 6; y++) {
                s.set(x, y, 0, y <= up ? "air" : y == up + 1 ? "as_slat_bar[facing=north]" : "as_slat[facing=north]");
            }
            s.set(x, 7, 1, "as_coil[axis=x]");
        }
        s.set(x0 + 3, 7, -1, "as_sign_bay" + (i + 1) + "[facing=north]");
        for (int z = 3; z <= TBAY_D - 2; z += 5) {
            s.set(x0 + 3, TTOP, z, kit.on("as_highbay", 0.3));
        }
        String truck = TRUCKS + (chance() < 0.5 ? "@usable" : "@abandoned") + "#customer";
        if (i == 0) {
            pit(s, x0 + 3, 3, 15);
            if (chance() < 0.8) {
                s.vehicle(x0 + 3.5, 1, 9.0, 0, truck, rng.nextLong());
            }
            s.set(x0 + 1, 1, 16, "as_drain[facing=north]");
        } else if (i == 1) {
            // a truck on the floor, the four column lifts wheeled up to its wheels
            boolean there = chance() < 0.75;
            if (there) {
                s.vehicle(x0 + 3.5, 1, 9.0, 0, truck, rng.nextLong());
            }
            int[] zs = there ? new int[] {5, 13} : new int[] {2, 4};
            for (int z : zs) {
                s.set(x0, 1, z, "as_column_lift_lo[facing=east]");
                s.set(x0, 2, z, "as_column_lift_hi[facing=east]");
                s.set(x0 + 6, 1, z, "as_column_lift_lo[facing=west]");
                s.set(x0 + 6, 2, z, "as_column_lift_hi[facing=west]");
            }
        } else {
            // VEHICLES' hoist for vans and light trucks, 8 deep at the front: posts in x0+1 and x0+4
            String van = chance() < 0.85 ? "van,step_van,pickup,suv,minivan" + (chance() < 0.5 ? "@usable" : "@abandoned") + "#customer" : null;
            s.vehicle(x0 + 3.0, 1, 1 + 4.0, 0, "!hoist:" + rng.nextInt(4) + (van == null ? "" : ";" + van), rng.nextLong());
            s.set(x0 + 2, 1, 12, "as_engine_stand[facing=west]");
            s.set(x0 + 4, 1, 14, "as_trans[facing=north]");
            s.set(x0, 1, 11, "as_stands[facing=east]");
            s.set(x0 + 6, 1, 16, "as_creeper[facing=west]");
        }
        // the strip to the east
        int g = x0 + TBAY_W;
        s.set(g + 1, 1, 1, "as_chest_" + pick("red", "black", "blue") + "_lo[facing=south]");
        s.set(g + 1, 2, 1, "as_chest_" + pick("red", "black", "blue") + "_hi[facing=south]");
        s.set(g + 1, 4, 1, "as_reels[facing=south]");
        if (chance() < 0.7) {
            s.set(g + 1, 1, 8, "as_grease[facing=west]");
        }
        if (chance() < 0.6) {
            s.set(g + 1, 1, 12, "as_bigtyre[facing=west]");
        }
        s.set(g + 1, 1, 15, kit.stock("as_shelf_filters_lo", "west", "auto_suspension"));
        s.set(g + 1, 2, 15, kit.stock("as_shelf_filters_hi", "west", "auto_suspension"));
    }

    /**
     * The inspection pit at x along z0..z1, two deep: its yellow curb at the floor, the walls with
     * the lamps let into them, the steps up at the back end.
     */
    private void pit(Placer s, int x, int z0, int z1) {
        for (int z = z0 - 1; z <= z1 + 3; z++) {
            boolean in = z >= z0 && z <= z1;
            if (z == z0 - 1) {
                s.set(x, 0, z, "as_pit_curb[facing=south]");
                s.set(x, -1, z, "as_pit_wall[facing=south]");
                continue;
            }
            if (in) {
                s.set(x, 0, z, "air");
                s.set(x, -1, z, "air");
                s.set(x, -2, z, "as_floor");
            } else if (z == z1 + 1) {
                s.set(x, 0, z, "air");
                s.set(x, -1, z, "as_pit_steps[facing=north]");
                s.set(x, -2, z, "as_floor");
            } else if (z == z1 + 2) {
                s.set(x, 0, z, "as_pit_steps[facing=north]");
                s.set(x, -1, z, "as_floor");
            } else {
                continue;
            }
            boolean lamp = in && (z - z0) % 4 == 2;
            String l = lamp ? kit.on("as_pit_lamp", 0.2) : "as_pit_wall";
            s.set(x - 1, 0, z, z <= z1 + 1 ? "as_pit_curb[facing=east]" : "as_floor");
            s.set(x + 1, 0, z, z <= z1 + 1 ? "as_pit_curb[facing=west]" : "as_floor");
            s.set(x - 1, -1, z, (lamp ? l : "as_pit_wall") + "[facing=east]");
            s.set(x + 1, -1, z, (lamp ? l : "as_pit_wall") + "[facing=west]");
            s.set(x - 1, -2, z, "as_floor");
            s.set(x + 1, -2, z, "as_floor");
        }
    }
}
