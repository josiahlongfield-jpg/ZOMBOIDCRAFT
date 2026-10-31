package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The auto shops (Josiah 2026-10-31: one in every town, sized to the town, more in the city, full
 * of tools and car parts, with working hoists), after Zomboid's mechanic shops and their
 * containers (MechanicShelfTools, Brakes, Suspension, Mufflers, Electric, Wheels, Outfit, Books)
 * and refs/autoshop. Every part is where a job gets done or where a part or a tool turns up:
 * <ul>
 * <li>the bays: 4 wide and 7 deep inside with 6 of headroom and a sectional door 4 by 4 (VEHICLES'
 * spec), a two-post hoist in some (VEHICLES' car_hoist, a customer's car up on it at some notch),
 * a car on stands or the floor clear in the rest, a 2-wide strip between bays for the tool cabinet
 * and the drain cart, the hoist's controls kept clear beside its +x post;</li>
 * <li>the back of the shop: benches with the vise and grinder, the tool wall, the cabinets, the
 * hose reels, the drums and the waste-oil tote, the compressor;</li>
 * <li>the office: the counter and till, the key board with the customer cars' keys (filled by
 * CarKeys.board the first time it's opened, so the cars parked outside must stay within 48 blocks
 * of it), the waiting chairs and coffee, the service desk, the manuals; the parts room behind it
 * stocked by class (standard at a village garage, sport added at a tyre centre, all three at the
 * dealer);</li>
 * <li>outside: the apron, the customers' cars nose-in along the side, the sign, the tyre cage and
 * the dumpster out back.</li>
 * </ul>
 * Five builds: the village garage (one bay, no lift), the repair shop on main street (three bays,
 * one hoist), the tyre and exhaust centre in a county seat (four bays, two hoists, the tyre
 * machines and the tyre store), the city's corner garage (two bays, one hoist) and the dealer's
 * service centre (six bays in two rows, four hoists, the parts store and warehouse). The bigger gas
 * stations get a service bay of their own ({@link #sideBay}).
 *
 * Drawn in its lot's frame: x along the road, z back from it (the road is to the north, -z).
 */
final class AutoShop {
    enum Kind { VILLAGE, REPAIR, TYRE, CORNER, DEALER, SALVAGE }

    /** The bay's inside: 4 wide, 7 deep; bays sit 6 apart (2 between them); walls rise to y 6, the roof at 7. */
    static final int BAY_W = 4, BAY_D = 7, PITCH = 6, TOP = 6;
    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final String CARS = "sedan,hatchback,coupe,wagon,suv,pickup,minivan,van";

    private final Placer q;
    private final SplittableRandom rng;
    private final double hit;
    private final boolean power;
    /** The parts classes this shop stocks: "standard", "sport", "heavy". */
    String[] classes = {"standard"};
    /** A salvage yard's shelves hold pulled parts, worn, whatever they were (LOOT's auto_scrap), not new stock by class. */
    boolean used;

    AutoShop(Placer q, SplittableRandom rng, double hit) {
        this.q = q;
        this.rng = rng;
        this.hit = hit;
        this.power = rng.nextDouble() < 0.25;
    }

    /** The lot each build needs, {w, d}: the building, its apron and the customers' row. */
    static int[] lot(Kind k) {
        return switch (k) {
            case VILLAGE -> new int[] {24, 26};
            case REPAIR -> new int[] {40, 28};
            case TYRE -> new int[] {48, 30};
            case CORNER -> new int[] {30, 26};
            case DEALER -> new int[] {46, 40};
            case SALVAGE -> new int[] {26, 20};
        };
    }

    double chance() {
        return rng.nextDouble();
    }

    String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    String dir() {
        return DIRS[rng.nextInt(4)];
    }

    String on(String block, double dead) {
        return power && chance() >= dead ? block + "_lit" : block;
    }

    /** A fitting stocked from one of the shop's parts classes: "as_shelf_brakes_lo[facing=south,loot=auto_brakes_sport]". */
    String stock(String block, String facing, String table) {
        if (used) {
            return block + "[facing=" + facing + ",loot=auto_scrap]";
        }
        return block + "[facing=" + facing + ",loot=" + table + "_" + classes[rng.nextInt(classes.length)] + "]";
    }

    String floor(boolean epoxy) {
        double r = chance();
        return epoxy ? (r < 0.08 ? "as_floor_oil" : "as_floor_epoxy") : r < 0.22 ? "as_floor_oil" : "as_floor";
    }

    /** A customer's car: the key is on the office board, never in it (a salvage yard's: a wreck, no key). */
    String customer(boolean runs) {
        if (used) {
            return CARS + (chance() < 0.7 ? "@wrecked" : "@abandoned");   // a yard car being pulled for parts
        }
        return CARS + (runs && chance() < 0.5 ? "@usable" : "@abandoned") + "#customer";
    }

    // ------------------------------------------------------------------ the builds

    void build(Kind k, int lw, int ld, String sign) {
        switch (k) {
            case VILLAGE -> village(lw, ld, sign);
            case REPAIR -> {
                classes = new String[] {"standard"};
                single(lw, ld, 7, 3, new int[] {0}, false, "sh_cmu_tan", sign, 12);
            }
            case TYRE -> {
                classes = new String[] {"standard", "sport"};
                single(lw, ld, 9, 4, new int[] {0, 1}, true, "brick_buff", sign, 14);
            }
            case CORNER -> {
                classes = new String[] {"standard"};
                single(lw, ld, 5, 2, new int[] {0}, false, chance() < 0.5 ? "brick_red" : "sh_cmu_tan", sign, 12);
            }
            case DEALER -> dealer(lw, ld);
            case SALVAGE -> {
                // the salvage yard's office and its pulling shed: the parts off the cars out back, two bays
                // where they come apart, one with a hoist; no customers' cars, the yard is behind the fence
                classes = new String[] {"standard", "sport", "heavy"};
                used = true;
                yardOnly = true;
                single(lw, ld, 7, 2, new int[] {0}, false, "as_metal_tan", sign, 12);
            }
        }
    }

    /**
     * A one-row shop: the office ow wide at the west end, n bays east of it facing the road, the
     * back of the shop behind them; dd deep overall, set back sz from the lot's front.
     */
    /** Set for the salvage yard's front: the yard round it does the customers, the sign and the back. */
    private boolean yardOnly;

    private void single(int lw, int ld, int ow, int n, int[] hoists, boolean tyres, String wall, String sign, int dd) {
        int bx0 = ow + 2, xe = bx0 + n * PITCH, sz = 8;
        int sx = Math.max(1, Math.min(4, lw - xe - 10));
        Placer s = q.shifted(sx, 0, sz);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        shell(s, b, xe, dd, wall);
        // the partition between the office and the shop, the door through it, the warning on it
        for (int z = 1; z < dd - 1; z++) {
            for (int y = 1; y <= TOP; y++) {
                s.set(ow + 1, y, z, b.wall(wall, y));
            }
        }
        b.door(ow + 1, 1, 2, "home_door_white", "east");
        s.set(ow, 3, 2, "as_sign_keepout[facing=west]");
        office(s, b, 1, ow, dd, sign, true);
        for (int i = 0; i < n; i++) {
            boolean lift = false;
            for (int h : hoists) {
                lift |= h == i;
            }
            bay(s, bx0 + i * PITCH, i + 1, lift, tyres && i == n - 1);
        }
        backOfShop(s, bx0, xe - 1, 8, dd - 2, tyres);
        b.flat(0, 0, xe, dd - 1, TOP + 1, wall, 1, "stone_trim");
        roofKit(s, xe, dd);
        fascia(s, sign, ow + 1);
        apron(q, sx, sx + xe, 0, sz);
        if (yardOnly) {
            return;
        }
        customers(q, sx + xe + 2, lw - 1, 3, ld - 3, sz + dd);
        yard(q, sx, sx + xe, sz + dd, ld - 1, tyres);
        if (tyres) {
            for (int i = 0; i < 3; i++) {
                q.set(sx + 1 + i * 2, 1, sz - 2, i == 1 ? "as_tyre_sale[facing=north]" : "as_tyre_stack");
            }
        }
        pylon(q, sign, sx + xe + 3, 0);
    }

    /** The walls, floor and ceiling of a shop xe wide (x 0..xe) and dd deep. */
    void shell(Placer s, Bldg b, int xe, int dd, String wall) {
        b.walls(0, 0, xe, dd - 1, 1, TOP + 1, wall);   // up to the roof slab: flat() rings only its parapet
        for (int x = 0; x <= xe; x++) {
            for (int z = 0; z < dd; z++) {
                boolean edge = x == 0 || x == xe || z == 0 || z == dd - 1;
                s.set(x, 0, z, edge ? "concrete_dark" : floor(false));
                if (!edge) {
                    for (int y = 1; y <= TOP; y++) {
                        s.set(x, y, z, "air");
                    }
                }
            }
        }
    }

    /**
     * One bay at x0 (inside x0..x0+3, z 1..7), its door in the front wall, number over it: a hoist
     * with a customer's car up on it at some notch, or a car on the floor, on stands, or the floor
     * clear; the tyre bay has the changer and balancer instead. The strip east of it (x0+4, x0+5):
     * the cabinet against the front pier, the drain cart, the creeper; x0+4 stays clear beside the
     * hoist's control post.
     */
    void bay(Placer s, int x0, int number, boolean lift, boolean tyreBay) {
        int rolled = chance() < 0.45 ? 4 : chance() < 0.5 ? rng.nextInt(1, 4) : 0;
        for (int i = 0; i < BAY_W; i++) {
            for (int y = 1; y <= 4; y++) {
                int row = y == 4 ? 0 : y == 3 ? 2 : y == 2 ? 1 : 3;
                s.set(x0 + i, y, 0, y <= rolled ? "air" : "gs_bay_" + row + "[facing=north]");
            }
        }
        s.set(x0 + 1 + rng.nextInt(2), 5, -1, "as_sign_bay" + Math.min(6, number) + "[facing=north]");
        // the opener on its rail over the door, the lights
        s.set(x0 + 1, TOP, 3, "as_opener[facing=north]");
        s.set(x0 + 1, TOP, 1, "as_rail[axis=z]");
        s.set(x0 + 1, TOP, 2, "as_rail[axis=z]");
        // the light at the bay's back, clear of a hoist's beam over its middle (up to 5.15 over the floor)
        s.set(x0 + 2, TOP, 7, on("as_highbay", 0.3));
        // a drain down the bay's middle, oil where the cars stood
        for (int z = 2; z <= 6; z++) {
            if (chance() < 0.5) {
                s.set(x0 + 1 + rng.nextInt(2), 0, z, "as_floor_oil");
            }
        }
        s.set(x0 + 1, 0, 6, "as_floor_drain");
        s.set(x0 + 2, 0, 6, "as_floor_drain");
        if (lift) {
            // VEHICLES' two-post hoist: posts in x0 and x0+3, the car nose-in
            String car = chance() < 0.85 ? customer(false) : null;
            s.vehicle(x0 + 2.0, 1, 1 + BAY_D / 2.0, 0, "!hoist:" + rng.nextInt(4) + (car == null ? "" : ";" + car), rng.nextLong());
        } else if (tyreBay) {
            s.set(x0, 1, 3, "gs_tirechanger[facing=east]");
            s.set(x0, 1, 6, "gs_balancer[facing=east]");
            s.set(x0 + 3, 1, 5, "as_tyre_stack");
            s.set(x0 + 3, 1, 6, "as_wheels");
            s.set(x0 + 2, 1, 2, "tire[axis=" + pick("x", "z") + "]");
        } else {
            double r = chance();
            if (r < 0.5) {
                s.vehicle(x0 + 2.0, 1, 1 + BAY_D / 2.0, 0, customer(true), rng.nextLong());
                if (chance() < 0.5) {
                    s.set(x0, 1, 2, "as_stands[facing=east]");
                }
            } else if (r < 0.75) {
                // a car up on stands, an engine out on the floor beside it
                s.set(x0 + 1, 1, 6, "as_engine_stand[facing=west]");
                s.set(x0, 1, 1, "as_stands[facing=east]");
                s.set(x0 + 3, 1, 2, "as_creeper[facing=" + dir() + "]");
            }
        }
        // the strip to the east
        int g = x0 + 5;
        s.set(g, 1, 1, "as_chest_" + pick("red", "red", "blue", "black") + "_lo[facing=south]");
        s.set(g, 2, 1, "as_chest_" + pick("red", "red", "blue", "black") + "_hi[facing=south]");
        if (chance() < 0.7) {
            s.set(g, 1, 4, "as_drain[facing=" + dir() + "]");
        }
        if (chance() < 0.6) {
            s.set(g, 1, 6, chance() < 0.5 ? "as_cart[facing=west]" : "as_creeper[facing=north]");
        }
        s.set(g, 4, 1, "as_reels[facing=south]");
    }

    /**
     * The back of the shop, x0..x1 by z0..z1: along the back wall the benches under the tool walls,
     * the cabinets, the compressor and the reels, the drums and the tote, the press and the welder;
     * a tyre centre's back is its tyre store, the racks two high.
     */
    void backOfShop(Placer s, int x0, int x1, int z0, int z1, boolean tyres) {
        String[] row = tyres
                ? new String[] {"rack", "rack", "rack", "bench_vise", "chests", "rack", "rack", "rack", "press", "rack", "rack", "compressor",
                        "drums", "bench", "rack", "rack", "pipes", "pipes", "tote", "rack", "rack", "welder", "rack", "rack"}
                : new String[] {"bench_vise", "chests", "drums", "compressor", "bench_grinder", "chests", "press", "tote", "bench", "welder",
                        "oilrack", "chests", "analyzer", "drums", "torch", "charger", "bench", "chests", "pipes", "tote"};
        int off = rng.nextInt(row.length);
        for (int x = x0; x <= x1; x++) {
            String what = row[(x - x0 + off) % row.length];
            switch (what) {
                case "bench_vise", "bench_grinder", "bench" -> {
                    s.set(x, 1, z1, "as_" + what + "[facing=north]");
                    s.set(x, 2, z1, "as_pegwall_" + pick("a", "b") + "[facing=north]");
                }
                case "chests" -> {
                    String paint = pick("red", "red", "blue", "black");
                    s.set(x, 1, z1, "as_chest_" + paint + "_lo[facing=north]");
                    s.set(x, 2, z1, "as_chest_" + paint + "_hi[facing=north]");
                }
                case "drums" -> s.set(x, 1, z1, "as_drum_" + pick("oil", "oil", "atf", "coolant"));
                case "compressor" -> {
                    s.set(x, 1, z1, "gs_compressor_lo[facing=north]");
                    s.set(x, 2, z1, "gs_compressor_hi[facing=north]");
                }
                case "press" -> {
                    s.set(x, 1, z1, "as_press_lo[facing=north]");
                    s.set(x, 2, z1, "as_press_hi[facing=north]");
                }
                case "tote" -> s.set(x, 1, z1, "as_tote[facing=north]");
                case "welder" -> s.set(x, 1, z1, "as_welder[facing=north]");
                case "torch" -> s.set(x, 1, z1, "as_torch[facing=north]");
                case "charger" -> s.set(x, 1, z1, "as_charger[facing=north]");
                case "analyzer" -> {
                    s.set(x, 1, z1, "as_analyzer_lo[facing=north]");
                    s.set(x, 2, z1, "as_analyzer_hi[facing=north]");
                }
                case "oilrack" -> s.set(x, 1, z1, "as_oilrack[facing=north]");
                case "pipes" -> s.set(x, 1, z1, stock("as_pipes", "north", "auto_exhaust"));
                case "rack" -> {
                    s.set(x, 1, z1, stock("as_tyre_rack_lo", "north", "auto_wheels"));
                    s.set(x, 2, z1, stock(chance() < 0.3 ? "as_tyre_rack_low" : "as_tyre_rack_hi", "north", "auto_wheels"));
                    if (z1 - 2 >= z0 && chance() < 0.5) {
                        s.set(x, 1, z1 - 2, "as_tyre_stack");
                    }
                }
                default -> {
                }
            }
            if (!what.equals("rack") && !what.startsWith("bench") && x % 3 == 0) {
                s.set(x, 3, z1, "as_reels[facing=north]");
            }
            if (x % 4 == 2) {
                s.set(x, TOP, z0 + 1, on("as_highbay", 0.3));
            }
        }
        // the clutter across the floor: cores, a pulled engine, a transmission, a hood leaning on the wall
        for (int i = 0; i < 3; i++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1);
            s.setIfAir(x, 1, z0, pick("as_cores[facing=" + dir() + "]", "as_engine[facing=" + dir() + "]", "as_trans[facing=" + dir() + "]",
                    "as_radiator[facing=north]", "tire[axis=x]", "as_bumper[facing=north]"));
        }
        if (chance() < 0.6) {
            s.setIfAir(x1, 1, z0, "as_hood[facing=west]");
        }
        s.set(x1, 3, z0, "as_sign_noflame[facing=west]");
    }

    /**
     * The office from x0 to x1, its front on the road: the windows and door, the counter and till,
     * the waiting chairs and coffee, the key board on the partition behind the counter, the service
     * desk, the manuals; behind, the parts room's shelves and the restroom.
     */
    void office(Placer s, Bldg b, int x0, int x1, int dd, String sign, boolean parts) {
        // the front: windows either side of the door
        int door = x0 + 1;
        for (int x = x0; x <= x1; x++) {
            if (x == door) {
                b.door(x, 1, 0, "shop_door", "north");
                continue;
            }
            boolean broken = chance() < hit * 0.6;
            s.set(x, 1, 0, broken ? "gs_front_broken_lo[facing=north]" : "gs_front_lo[facing=north]");
            s.set(x, 2, 0, broken && chance() < 0.6 ? "gs_front_broken_hi[facing=north]" : "gs_front_hi[facing=north]");
        }
        // the drop ceiling with its troffers, the counter across, the till, the board behind
        for (int x = x0; x <= x1; x++) {
            for (int z = 1; z < dd - 1; z++) {
                s.set(x, 0, z, chance() < 0.15 ? "gs_vct_mud" : "gs_vct");
                s.set(x, 4, z, x % 2 == 1 && z % 3 == 1 ? (power ? "fit_troffer_on[axis=x]" : "fit_troffer[axis=x]") : "fit_ceiling");
            }
        }
        int cz = 4;
        for (int x = x0 + 2; x <= x1; x++) {
            s.set(x, 1, cz, x == x0 + 3 ? "store_register[facing=north]" : x == x1 ? "as_counter_end[facing=north]" : "store_counter[facing=north]");
        }
        s.set(x1, 2, cz + 2, "as_keyboard[facing=west]");
        s.set(x1, 3, cz + 2, "as_sign_cashier[facing=west]");
        s.set(x0 + 2, 1, cz + 2, "as_desk[facing=north]");
        s.set(x0 + 2, 1, cz + 3, "home_chair[facing=north]");
        s.set(x0, 2, cz + 1, "as_sign_rate[facing=east]");
        // the waiting room inside the windows
        s.set(x0, 1, 1, "as_chairs_l[facing=east]");
        s.set(x0, 1, 2, "as_chairs_r[facing=east]");
        s.set(x0, 1, 3, "as_coffee[facing=east]");
        s.set(x0, 3, 2, "as_clock[facing=east]");
        s.set(x0 + 4 <= x1 ? x0 + 4 : x1, 1, 1, "pk_vending[facing=south]");
        if (x1 - x0 >= 6) {
            s.set(x0 + 5, 1, 1, "gs_oilcans[facing=south]");
            s.set(x0 + 6, 1, 1, "as_oilrack[facing=south]");
        }
        // the parts room behind the counter, its shelves in rows two high
        int pz0 = cz + 4, pz1 = dd - 2;
        if (parts && pz1 - pz0 >= 1) {
            String[] kinds = {"filters", "brakes", "boxes", "batteries"};
            String[] tables = {"auto_suspension", "auto_brakes", "auto_exhaust", null};
            for (int x = x0 + 3; x <= x1; x++) {
                int k = rng.nextInt(kinds.length);
                String block = "as_shelf_" + kinds[k];
                s.set(x, 1, pz1, tables[k] == null ? block + "_lo[facing=north]" : stock(block + "_lo", "north", tables[k]));
                s.set(x, 2, pz1, tables[k] == null ? block + "_hi[facing=north]" : stock(block + "_hi", "north", tables[k]));
                s.set(x, 3, pz1, "air");
            }
            s.set(x0 + 3, 2, pz0 + 1, "as_belts[facing=east]");
            s.set(x1, 1, pz0, "as_counter_parts[facing=west]");
        }
        // the restroom in the back corner, x0..x0+1 behind its own walls, the door off the parts room
        int rz0 = Math.max(pz0, dd - 5);
        if (dd - 2 - rz0 >= 1 && x1 - x0 >= 3) {
            for (int y = 1; y <= 3; y++) {
                for (int z = rz0; z <= dd - 2; z++) {
                    s.set(x0 + 2, y, z, b.wall("drywall", y));
                }
                s.set(x0, y, rz0 - 1, b.wall("drywall", y));
                s.set(x0 + 1, y, rz0 - 1, b.wall("drywall", y));
                s.set(x0 + 2, y, rz0 - 1, b.wall("drywall", y));
            }
            b.door(x0 + 2, 1, rz0, "home_door_white", "east");
            s.set(x0, 1, dd - 2, "home_toilet[facing=east]");
            s.set(x0 + 1, 1, dd - 2, "home_sink_white[facing=north]");
        }
        // what happened here: the papers off the counter, glass in from the windows
        b.mess(x0, 1, x1, cz - 1, 1, 0.1 + hit * 0.15);
    }

    /** The rooftop units and a vent stack over the shop. */
    void roofKit(Placer s, int xe, int dd) {
        s.set(3, TOP + 2, dd / 2, "pk_aircon[facing=south]");
        for (int x = 8; x < xe - 2; x += 9) {
            s.set(x, TOP + 2, 2 + rng.nextInt(dd - 4), "hvac");
        }
    }

    /** The shop's name on the wall's face at y 5, centred over the office front (x 0..x1), clear of the bay numbers. */
    void fascia(Placer s, String sign, int x1) {
        int cols = SIGN_COLS.getOrDefault(sign, 5);
        int a = Math.max(0, (x1 + 1 - cols) / 2);
        for (int c = 0; c < cols; c++) {
            s.set(a + cols - 1 - c, 5, -1, "as_s_" + sign + "_" + c + "0[facing=north]");
        }
    }

    private static final java.util.Map<String, Integer> SIGN_COLS = java.util.Map.ofEntries(
            java.util.Map.entry("holloway", 5), java.util.Map.entry("pruitt", 5), java.util.Map.entry("dean", 5),
            java.util.Map.entry("seabright", 6), java.util.Map.entry("millbrook", 6), java.util.Map.entry("kessler", 6),
            java.util.Map.entry("roadking", 6), java.util.Map.entry("motors", 7), java.util.Map.entry("service", 3),
            java.util.Map.entry("parts", 3), java.util.Map.entry("repair", 4), java.util.Map.entry("quikfix", 4),
            java.util.Map.entry("diesel", 6), java.util.Map.entry("salvage", 6), java.util.Map.entry("motorpool", 4));

    static int signCols(String sign) {
        return SIGN_COLS.getOrDefault(sign, 5);
    }

    /** The sign by the road on its two posts (x, z the west post), its face to the road. */
    void pylon(Placer p, String sign, int x, int z) {
        int cols = SIGN_COLS.getOrDefault(sign, 5);
        if (cols > 6) {
            return;
        }
        for (int y = 1; y <= 5; y++) {
            p.set(x, y, z + 1, "gs_pylon_c");
            p.set(x + cols - 1, y, z + 1, "gs_pylon_c");
        }
        for (int c = 0; c < cols; c++) {
            p.set(x + cols - 1 - c, 5, z, "as_s_" + sign + "_" + c + "0[facing=north]");
        }
    }

    /** Concrete, x0..x1 by z0..z1-1: saw cut, cracked, oil where cars waited. */
    void apron(Placer p, int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z < z1; z++) {
                double r = chance();
                p.set(x, 0, z, r < 0.12 ? "gs_slab_oil" : r < 0.2 ? "gs_slab_crack" : (x - x0) % 5 == 0 || z % 5 == 0 ? "gs_slab_j[facing=north]" : "gs_slab");
            }
        }
    }

    /** The customers' cars nose-in down the side lot, x0..x1 by z0..z1, the waiting ones and the done ones. */
    void customers(Placer p, int x0, int x1, int z0, int z1, int zBack) {
        if (x1 - x0 < 5) {
            return;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= Math.min(z1, zBack); z++) {
                p.set(x, 0, z, chance() < 0.1 ? "gs_slab_oil" : "gs_slab");
            }
        }
        for (double z = z0 + 1.5; z + 2 <= Math.min(z1, zBack); z += 3) {
            if (chance() < 0.7) {
                p.vehicle(x0 + 2.5, 1, z, 90, customer(true), rng.nextLong());
            }
        }
    }

    /** Out back: the tyre cage, the dumpster, the waste-oil tote, a heap of old parts. */
    void yard(Placer p, int x0, int x1, int z0, int z1, boolean tyres) {
        if (z1 - z0 < 2) {
            return;
        }
        p.set(x0 + 1, 1, z0 + 1, "as_tyre_cage");
        if (tyres) {
            p.set(x0 + 2, 1, z0 + 1, "as_tyre_cage");
        }
        p.set(x0 + 4, 1, z0 + 1, "dumpster[facing=north]");
        p.set(x0 + 6, 1, z0 + 1, "as_tote[facing=north]");
        p.set(x0 + 7, 1, z0 + 1, "as_drum_oil");
        p.set(x1 - 2, 1, z0 + 1, "as_scrap");
        p.setIfAir(x1 - 3, 1, z0 + 1, pick("as_bumper[facing=south]", "as_cardoor[facing=south]", "as_radiator[facing=south]"));
    }

    // ------------------------------------------------------------------ the village garage

    /**
     * The village garage beside the store: a gabled shed of corrugated tin over CMU, one bay with
     * no lift (the car up on stands, the trolley jack's place), the owner's name on a board over the
     * door, a tiny office with the till and the key board, gravel out front, the junk out back.
     */
    private void village(int lw, int ld, String sign) {
        classes = new String[] {"standard"};
        int ow = 4, bx0 = ow + 2, xe = bx0 + PITCH, dd = 12, sz = 7;
        Placer s = q.shifted(2, 0, sz);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        shell(s, b, xe, dd, "sh_cmu_tan");
        for (int z = 1; z < dd - 1; z++) {
            for (int y = 1; y <= TOP; y++) {
                s.set(ow + 1, y, z, b.wall("sh_cmu_tan", y));
            }
        }
        b.door(ow + 1, 1, 2, "home_door_white", "east");
        office(s, b, 1, ow, dd, sign, false);
        bay(s, bx0, 1, false, false);
        // the back of the one bay: the bench, the cabinet, the drums, the tote
        backOfShop(s, bx0, xe - 1, 8, dd - 2, false);
        b.gable(0, xe, 0, dd - 1, TOP + 1, "minecraft:tuff", false, "sh_cmu_tan", 1);   // the kit's red tin
        fascia(s, sign, ow + 1);
        for (int x = 0; x < lw; x++) {
            for (int z = 0; z < sz; z++) {
                if (q.get(x, 0, z) == null) {
                    q.set(x, 0, z, chance() < 0.7 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                }
            }
        }
        customers(q, xe + 4, lw - 1, 6, ld - 3, sz + dd);
        yard(q, 2, xe + 2, sz + dd, ld - 1, false);
        q.vehicle(xe + 7.5, 1, 3.0, 90, "tow_truck,pickup", rng.nextLong());
    }

    // ------------------------------------------------------------------ the dealer's service centre

    /**
     * The dealer's service centre: a steel building of white ribbed panels on a block base, the
     * service drive under its canopy out front where the cars were written up, the lounge and the
     * advisors' desks, the parts store with its counter and the warehouse racks behind it; the
     * shop is two rows of three bays back to back with the benches down the middle, four of the six
     * with hoists; the epoxy floor; the lot of customers' cars and the ones that were for sale.
     */
    private void dealer(int lw, int ld) {
        classes = new String[] {"standard", "sport", "heavy"};
        int ow = 12, bx0 = ow + 2, xe = bx0 + 3 * PITCH, dd = 21, sz = 9;
        Placer s = q.shifted(2, 0, sz);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        shell(s, b, xe, dd, "as_metal");
        for (int x = 0; x <= xe; x++) {
            for (int z = 0; z < dd; z++) {
                if (s.get(x, 0, z) != null && s.get(x, 0, z).startsWith("as_floor")) {
                    s.set(x, 0, z, floor(true));
                }
                for (int y = 1; y <= 2; y++) {
                    if (x == 0 || x == xe || z == 0 || z == dd - 1) {
                        s.set(x, y, z, b.wall("concrete_panel", y));
                    }
                }
            }
        }
        for (int z = 1; z < dd - 1; z++) {
            for (int y = 1; y <= TOP; y++) {
                s.set(ow + 1, y, z, "as_metal");
            }
        }
        b.door(ow + 1, 1, 3, "home_door_white", "east");
        b.door(ow + 1, 1, dd - 4, "home_door_white", "east");
        s.set(ow, 3, 3, "as_sign_keepout[facing=west]");
        // the lounge and the advisors at the front, the parts store behind
        int split = 9;
        for (int x = 1; x <= ow; x++) {
            for (int y = 1; y <= 4; y++) {
                s.set(x, y, split, x == 6 && y <= 2 ? "air" : "as_metal");
            }
        }
        office(s, b, 1, ow, split + 1, "motors", false);
        // the parts store: the counter facing the lounge door, the warehouse racks in rows
        for (int x = 2; x <= ow - 1; x++) {
            s.set(x, 1, split + 2, x == 6 ? "air" : "as_counter_parts[facing=north]");
        }
        s.set(1, 3, split + 1, "as_sign_parts[facing=east]");
        for (int z = split + 4; z <= dd - 2; z += 2) {
            for (int x = 2; x <= ow - 1; x++) {
                if (x == 6) {
                    continue;
                }
                String[] kinds = {"filters", "brakes", "boxes", "batteries"};
                String[] tables = {"auto_suspension", "auto_brakes", "auto_exhaust", null};
                int k = rng.nextInt(kinds.length);
                String block = "as_shelf_" + kinds[k];
                for (int y = 1; y <= 3; y++) {
                    String part = block + (y == 1 ? "_lo" : "_hi");
                    s.set(x, y, z, tables[k] == null ? part + "[facing=north]" : stock(part, "north", tables[k]));
                }
            }
        }
        for (int x = 1; x <= ow; x++) {
            for (int z = split + 1; z < dd - 1; z++) {
                s.set(x, 0, z, floor(true));
                s.set(x, TOP, z, x % 3 == 0 && z % 3 == 0 ? on("as_strip", 0.3) + "[axis=z]" : "air");
            }
        }
        // the shop: three bays opening north, three opening south onto the back lot, benches between
        for (int i = 0; i < 3; i++) {
            int x0 = bx0 + i * PITCH;
            bay(s, x0, i + 1, i != 2, false);
            backBay(s, x0, dd, i + 4, i < 2);
        }
        int mz0 = 1 + BAY_D, mz1 = dd - 2 - BAY_D;
        for (int x = bx0; x < xe; x++) {
            for (int z = mz0; z <= mz1; z++) {
                s.set(x, 0, z, "as_floor_stripe");
            }
            if ((x - bx0) % PITCH == 4) {
                s.set(x, 1, mz0, "as_bench_vise[facing=north]");
                s.set(x + 1, 1, mz0, "as_chest_" + pick("red", "black") + "_lo[facing=north]");
                s.set(x + 1, 2, mz0, "as_chest_" + pick("red", "black") + "_hi[facing=north]");
                s.set(x, 1, mz1, "as_analyzer_lo[facing=south]");
                s.set(x, 2, mz1, "as_analyzer_hi[facing=south]");
                s.set(x + 1, 1, mz1, "as_drum_" + pick("oil", "atf", "coolant"));
            }
        }
        s.set(xe - 1, 1, mz0, "as_tote[facing=west]");
        s.set(xe - 1, 1, mz1, "gs_compressor_lo[facing=north]");
        s.set(xe - 1, 2, mz1, "gs_compressor_hi[facing=west]");
        b.flat(0, 0, xe, dd - 1, TOP + 1, "as_metal", 1, "stone_trim");
        roofKit(s, xe, dd);
        // the name on the front, the service drive's canopy over the write-up lane
        for (int c = 0; c < 7; c++) {
            s.set(9 - c + 2, 5, -1, "as_s_motors_" + c + "0[facing=north]");
        }
        for (int c = 0; c < 3; c++) {
            s.set(bx0 + 9 - c, 6, -1, "as_s_service_" + c + "0[facing=north]");
        }
        for (int x = 1; x <= 11; x++) {
            for (int z = -6; z <= -1; z++) {
                boolean edge = x == 1 || x == 11 || z == -6;
                s.set(x, 4, z, edge ? "gs_enamel_band" : (x % 3 == 0 && z % 3 == 0 ? (power && chance() >= 0.2 ? "gs_deck_light_on" : "gs_deck_light") : "gs_deck"));
            }
        }
        for (int y = 1; y <= 3; y++) {
            s.set(1, y, -6, "as_column");
            s.set(11, y, -6, "as_column");
        }
        apron(q, 0, lw - 1, 0, sz);
        apron(q, 0, lw - 1, sz + dd, ld);
        customers(q, xe + 4, lw - 1, 2, ld - 3, ld - 3);
        // the back lot: the cars waiting their turn behind the south doors
        for (double x = bx0 + 4; x < xe + 2; x += 3.2) {
            if (chance() < 0.6) {
                q.vehicle(x, 1, sz + dd + 4.5, 0, customer(true), rng.nextLong());
            }
        }
        yard(q, 2, 12, sz + dd, ld - 1, false);
    }

    /** A bay of the dealer's back row: opening south, its door in the back wall. */
    private void backBay(Placer s, int x0, int dd, int number, boolean lift) {
        int z0 = dd - 1 - BAY_D;
        int rolled = chance() < 0.4 ? 4 : 0;
        for (int i = 0; i < BAY_W; i++) {
            for (int y = 1; y <= 4; y++) {
                int row = y == 4 ? 0 : y == 3 ? 2 : y == 2 ? 1 : 3;
                s.set(x0 + i, y, dd - 1, y <= rolled ? "air" : "gs_bay_" + row + "[facing=south]");
            }
        }
        s.set(x0 + 1, 5, dd, "as_sign_bay" + number + "[facing=south]");
        s.set(x0 + 2, TOP, z0 + 6, on("as_highbay", 0.3));
        if (lift) {
            String car = chance() < 0.85 ? customer(false) : null;
            s.vehicle(x0 + 2.0, 1, z0 + BAY_D / 2.0, 180, "!hoist:" + rng.nextInt(4) + (car == null ? "" : ";" + car), rng.nextLong());
        } else if (chance() < 0.6) {
            s.vehicle(x0 + 2.0, 1, z0 + BAY_D / 2.0, 180, customer(true), rng.nextLong());
        }
        s.set(x0 + 5, 1, dd - 2, "as_chest_" + pick("red", "black") + "_lo[facing=north]");
        s.set(x0 + 5, 2, dd - 2, "as_chest_" + pick("red", "black") + "_hi[facing=north]");
    }

    // ------------------------------------------------------------------ the gas station's bay

    /**
     * A service bay on the side of a bigger gas station, x0 its west wall, z0 its front, facing the
     * forecourt: one bay with a hoist, the bench and cabinet at the back, the drums and the tote.
     */
    void sideBay(int x0, int z0, String wall) {
        classes = new String[] {"standard"};
        Placer s = q.shifted(x0, 0, z0);
        Bldg b = new Bldg(s, rng);
        b.damage = hit;
        int xe = PITCH + 1, dd = 12;
        shell(s, b, xe, dd, wall);
        bay(s, 1, 1, true, false);
        backOfShop(s, 1, xe - 1, 8, dd - 2, false);
        // the customer's key on the board on the far wall, a side door out toward the mart
        s.set(xe - 1, 2, 3, "as_keyboard[facing=west]");
        b.door(0, 1, 9, "home_door_white", "west");
        b.flat(0, 0, xe, dd - 1, TOP + 1, wall, 1, "stone_trim");
    }
}
