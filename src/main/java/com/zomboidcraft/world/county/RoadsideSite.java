package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.ShopLights;

/**
 * A stop on the county road, where a side lane comes in off to one end. Every one comes in
 * several builds, picked from the place's seed, and every one was hit when it all came down:
 * <ul>
 * <li>gas_station: a modern canopy of pumps over a purple glow with its store behind; an old
 * service station with its bays and one pump island; or a country store with two pumps out
 * front and bait for sale.</li>
 * <li>motel: a single-storey L of rooms, a two-storey bar with a walkway and stairs, or a U round
 * a courtyard; the office, the tall neon sign, the drained pool, a lamp over every door.</li>
 * <li>diner: a stainless railcar diner, a brick roadside one, or a truck stop with its rigs; a
 * counter and stools, booths, the grill, the pie case.</li>
 * <li>church: a chapel, a brick church or a stone one, its graveyard and its gravel lot.</li>
 * <li>school: an elementary with its playground and court, or a high school with a football
 * field, bleachers and lights; the bus lot, and on some the evacuation centre it became.</li>
 * <li>warehouse: a storage depot, a feed and farm supply, or a lumber yard; docks, trailers
 * backed in, racks inside, a fenced yard.</li>
 * <li>trailer_lot: a few single-wides on gravel pads round a lane, junk and propane tanks.</li>
 * </ul>
 */
final class RoadsideSite extends Site {
    private static final String CARS = "sedan,hatchback,pickup,suv,minivan,wagon,coupe,van";
    private static final String TRUCKS = "box_truck,moving_truck,step_van,tow_truck";

    private Kit kit;
    private Kit.Lane road;
    /** Where the side lane meets the county road, and the stretch of road clear of it. */
    private double sideX, freeA, freeB;
    /** Which side of the road the stop took: 1 south, -1 north, 0 none. */
    private int bizSide;
    /** A trailer park's drive. */
    private Kit.Lane spur;
    /** How hard the outbreak hit this stop, 0 to 1. */
    private double hit;

    RoadsideSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        hit = chance() * 0.8 + (chance() < 0.2 ? 0.2 : 0);
        roads();
        kit.pave();
        switch (p.variant) {
            case "motel" -> motel();
            case "diner" -> diner();
            case "church" -> church();
            case "school" -> school();
            case "warehouse" -> warehouse();
            case "trailer_lot" -> trailerLot();
            default -> gasStation();
        }
        roadside();
        across();
        kit.woods(0.45);
        kit.overgrow(0.85);
        kit.roadWeeds(0.35);
        kit.tidyPlants();
    }

    private double jitter(double amount) {
        return (chance() * 2 - 1) * amount;
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ the roads

    private void roads() {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        road = kit.curve(Kit.Style.COUNTY, W[0], W[1], 8, W[1], w * 0.5, W[1] + jitter(2), w - 9, E[1], E[0], E[1]);
        // the side lane comes in near one end, so the long stretch is left for the stop itself
        boolean west = chance() < 0.5;
        sideX = w * (west ? 0.16 : 0.84);
        freeA = west ? sideX + 8 : 6;
        freeB = west ? w - 6 : sideX - 8;
        double zr = zAt(road, sideX);
        Kit.Style lane = chance() < 0.6 ? Kit.Style.LANE : Kit.Style.GRAVEL;
        // it keeps to the back edges between the gates and the corner, leaving the frontage deep
        double toward = west ? 1 : -1;
        kit.curve(lane, N[0], N[1], N[0], 4, sideX + toward * 8, 4, sideX, 12, sideX, zr);
        kit.curve(lane, sideX, zr, sideX, d - 13, sideX + toward * 8, d - 5, S[0], d - 5, S[0], S[1]);
        if ("trailer_lot".equals(p.variant)) {
            // the park's own gravel drive, off the road into the trees, a turning loop at the end
            bizSide = chance() < 0.5 ? 1 : -1;
            double mx = (freeA + freeB) / 2 + jitter(4), z0 = zAt(road, mx), end = bizSide > 0 ? d - 18 : 18;
            spur = kit.lane(Kit.Style.GRAVEL, mx, z0, mx, end);
            kit.arc(Kit.Style.GRAVEL, mx, end + bizSide * 5, 5, 0, Math.PI * 2);
        }
    }

    private static double zAt(Kit.Lane l, double x) {
        return l.point(at(l, x))[1];
    }

    /** How far along a lane it passes nearest x. */
    private static double at(Kit.Lane l, double x) {
        double best = 1e9, s = 0;
        for (double t = 0; t <= l.length(); t += 1) {
            double dx = Math.abs(l.point(t)[0] - x);
            if (dx < best) {
                best = dx;
                s = t;
            }
        }
        return s;
    }

    /**
     * The stop's own lot, lw along the road and depth back from it, centred on the clear stretch,
     * on whichever side has room (shrinking until it fits); null when nothing fits.
     */
    private Kit.Lot business(int lw, int depth) {
        double mid = (freeA + freeB) / 2;
        int room = (int) (freeB - freeA);
        lw = Math.min(lw, room);
        int first = chance() < 0.5 ? 1 : -1;
        for (int shrink = 0; shrink < 4; shrink++) {
            int bw = lw - shrink * 4, bd = depth - shrink * 3;
            for (int side : new int[] {first, -first}) {
                double[] q = road.point(at(road, mid));
                Kit.Lot lot = kit.fit(q[0], q[1], -q[3] * side, q[2] * side, bw, bd);
                if (lot != null) {
                    kit.lots.add(lot);
                    bizSide = side;
                    // grass first; floors, slabs and pools laid over it clear what grew there
                    Kit.lawn(lot.placer(out), rng, 0, 0, lot.w() - 1, lot.d() - 1, 0.85);
                    return lot;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ shared pieces

    /** A cracked, stained concrete or asphalt floor over a box of the lot. */
    private void slab(Placer q, int x0, int z0, int x1, int z1, boolean asphalt) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                String m = asphalt
                        ? (r < 0.62 ? "asphalt" : r < 0.78 ? "asphalt_crack" : r < 0.88 ? "asphalt_patch" : r < 0.95 ? "asphalt_b" : "asphalt_moss")
                        : (r < 0.55 ? "floor_concrete" : r < 0.72 ? "floor_concrete_crack" : r < 0.84 ? "floor_concrete_b"
                        : r < 0.93 ? "concrete_dark_stain" : "floor_concrete_moss");
                q.set(x, 0, z, m);
                clearPlants(q, x, z);
                if (chance() < 0.04) {
                    q.set(x, 1, z, chance() < 0.7 ? "minecraft:short_grass" : "weeds");  // through the cracks
                }
            }
        }
    }

    /** Takes off whatever the lawn grew at (x, z), both halves of tall grass. */
    private static void clearPlants(Placer q, int x, int z) {
        String top = q.get(x, 2, z);
        if (top != null && top.contains("[half=upper]")) {
            q.set(x, 2, z, "air");
        }
        q.set(x, 1, z, "air");
    }

    /** A gravel lot with ruts of dirt and grass coming through. */
    private void gravel(Placer q, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                q.set(x, 0, z, r < 0.6 ? "minecraft:gravel" : r < 0.8 ? "minecraft:coarse_dirt" : r < 0.9 ? "minecraft:dirt_path" : "minecraft:grass_block");
                clearPlants(q, x, z);
                if (r > 0.93) {
                    q.set(x, 1, z, "minecraft:short_grass");
                }
            }
        }
    }

    /**
     * Parking spaces three wide down a row from x0 to x1, nose-in from z (front) to z + 5, with
     * painted lines; some still taken by what was left behind.
     */
    private void parking(Placer q, int x0, int x1, int z, double taken, String models) {
        for (int x = x0; x <= x1; x++) {
            if ((x - x0) % 3 == 0) {
                for (int k = 1; k <= 4; k++) {
                    if (chance() < 0.85) {
                        q.set(x, 0, z + k, "road_line_white[axis=z]");
                    }
                }
            } else if ((x - x0) % 3 == 1 && x + 1 <= x1 && chance() < taken) {
                q.vehicle(x + 0.5, 1, z + 3, chance() < 0.5 ? 0 : 180, models, rng.nextLong());
            }
        }
    }

    /** A tall sign on two steel poles: the board high up, a lamp box under it, facing the road. */
    private void pylon(Placer q, int x0, int z, String sign, int height) {
        int k = Boards.WIDTH.getOrDefault(sign, 4);
        for (int y = 1; y < height; y++) {
            q.set(x0, y, z, "streetlight_pole");
            q.set(x0 + k - 1, y, z, "streetlight_pole");
        }
        for (int i = 0; i < k; i++) {
            if (chance() > 0.08 + hit * 0.15) {
                q.set(x0 + k - 1 - i, height, z - 1, "sign_" + sign + "_" + i + "[facing=south]");
            }
            q.set(x0 + i, height + 1, z - 1, "steel_red");
        }
        q.set(x0 + k / 2, height - 1, z - 1, ShopLights.lamp(rng, 0.2 + hit * 0.2));
    }

    /** Junk round the back: a dumpster, bags, tyres, a drum. */
    private void junk(Placer q, int x, int z) {
        q.set(x, 1, z, "dumpster[facing=" + any("north", "east", "south", "west") + "]");
        q.set(x + 1, 1, z, chance() < 0.5 ? "trash_bag[facing=north]" : "air");
        q.set(x, 1, z + 1, chance() < 0.6 ? "trash_bag[facing=east]" : "air");
        if (chance() < 0.6) {
            q.set(x + 2, 1, z + 1, "tire[axis=x]");
            q.set(x + 2, 2, z + 1, chance() < 0.5 ? "tire[axis=x]" : "air");
        }
        if (chance() < 0.4) {
            q.set(x - 1, 1, z + 1, "minecraft:barrel[facing=up,loot=scraps]");
        }
    }

    /** Burn marks and a wreck where it went badly: scorch on the ground, the car burnt out. */
    private void scorch(Placer q, int cx, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if ((x - cx) * (x - cx) + (z - cz) * (z - cz) <= r * r && chance() < 0.6) {
                    q.set(x, 0, z, chance() < 0.5 ? "burnt_ground" : "scorch");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the gas station

    private void gasStation() {
        int style = rng.nextInt(3);
        Kit.Lot lot = switch (style) {
            case 1 -> business(48, 40);
            case 0 -> business(40, 32);
            default -> business(32, 30);
        };
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        switch (style) {
            case 1 -> modernStation(q, lot.w(), lot.d());
            case 0 -> serviceStation(q, lot.w(), lot.d());
            default -> countryStore(q, lot.w(), lot.d());
        }
    }

    /** The canopy station: islands of pumps under a lit canopy, the store behind, a car wash. */
    private void modernStation(Placer q, int lw, int ld) {
        int cx = lw / 2;
        int storeZ = Math.min(20, ld - 14);
        slab(q, 1, 0, lw - 2, storeZ - 1, false);
        // the canopy: white deck on four columns, a red band round it, lamp boxes underneath
        int x0 = cx - 9, x1 = cx + 8, z0 = 3, z1 = Math.min(15, storeZ - 3);
        boolean burnt = hit > 0.65;
        int hole = burnt ? x0 + rng.nextInt(x1 - x0 - 4) : -99;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (burnt && x >= hole && x <= hole + 4 && chance() < 0.7) {
                    if (chance() < 0.3) {
                        q.set(x, 1, z, "debris_chunk");
                    }
                    continue;  // burnt through: the deck fell in
                }
                q.set(x, 6, z, edge ? "steel_red" : "minecraft:smooth_quartz");
                q.set(x, 7, z, edge ? "minecraft:smooth_quartz_slab[type=bottom]" : "minecraft:smooth_stone_slab[type=bottom]");
            }
        }
        for (int[] c : new int[][] {{x0 + 3, z0 + 2}, {x1 - 3, z0 + 2}, {x0 + 3, z1 - 2}, {x1 - 3, z1 - 2}}) {
            for (int y = 1; y <= 5; y++) {
                q.set(c[0], y, c[1], "minecraft:smooth_quartz");
            }
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 4) {
            for (int z = z0 + 2; z <= z1 - 1; z += 4) {
                if (q.get(x, 6, z) != null && !"air".equals(q.get(x, 6, z))) {
                    q.set(x, 5, z, ShopLights.lamp(rng, 0.15 + hit * 0.25));
                } else if (chance() < 0.5) {
                    q.set(x, 1, z, "minecraft:smooth_quartz");  // a fitting that came down with the deck
                }
            }
        }
        // two islands of pumps, a bin and a bucket on each
        for (int iz : new int[] {z0 + 4, z1 - 4}) {
            for (int x = x0 + 4; x <= x1 - 4; x++) {
                q.set(x, 0, iz, "concrete_dark");
            }
            for (int x = x0 + 5; x <= x1 - 5; x += 4) {
                if (chance() > hit * 0.3) {
                    q.set(x, 1, iz, "gas_pump[facing=" + (chance() < 0.5 ? "north" : "south") + "]");
                }
            }
            q.set(x0 + 4, 1, iz, "minecraft:cauldron");
            q.set(x1 - 4, 1, iz, chance() < 0.5 ? "trash_bag[facing=north]" : "store_bin");
        }
        // what was left at the pumps
        for (int iz : new int[] {z0 + 2, z1 - 2}) {
            if (chance() < 0.55) {
                q.vehicle(x0 + 6 + rng.nextInt(Math.max(1, x1 - x0 - 12)), 1, iz, 90, CARS, rng.nextLong());
            }
        }
        if (burnt) {
            scorch(q, hole + 2, (z0 + z1) / 2, 4);
            q.vehicle(hole + 2.5, 1, (z0 + z1) / 2.0, 90, "sedan,pickup,suv", rng.nextLong());
        }
        // the store
        int sw = Math.min(24, lw - 10), sd = Math.min(14, ld - storeZ - 1);
        Storefront store = new Storefront(q.shifted(cx - sw / 2, 0, storeZ), rng, sw, sd, 1, Storefront.Style.MODERN, Storefront.Trade.FUEL);
        store.build();
        parking(q, cx - sw / 2, cx + sw / 2 - 1, storeZ - 6, 0.3, CARS);
        // ice chest and propane cage by the door, the air pump, junk round the back
        q.set(cx - sw / 2 - 2, 1, storeZ + 1, "store_cooler[facing=north]");
        q.set(cx - sw / 2 - 2, 2, storeZ + 1, "store_cooler_top[facing=north]");
        propaneCage(q, cx + sw / 2 + 1, storeZ);
        q.set(x1 + 2, 1, z1, "store_vending[facing=west]");
        q.set(x1 + 2, 2, z1, "store_vending_top[facing=west]");
        junk(q, cx + sw / 2 + 2, storeZ + sd - 2);
        // the car wash bay off the end, if there's room
        if (lw - (cx + sw / 2) > 12 && chance() < 0.6) {
            carWash(q, lw - 9, storeZ - 2, Math.min(12, ld - storeZ));
        }
        pylon(q, 1, 1, "fuel", 7 + rng.nextInt(3));
        q.set(1, 0, 1, "concrete_dark");
    }

    /** Propane bottles in a chain-link cage. */
    private void propaneCage(Placer q, int x, int z) {
        for (int i = 0; i < 3; i++) {
            q.set(x + i, 1, z, "chainlink[axis=x]");
            q.set(x + i, 1, z + 2, "chainlink[axis=x]");
        }
        q.set(x, 1, z + 1, "chainlink[axis=z]");
        q.set(x + 2, 1, z + 1, "chainlink[axis=z]");
        q.set(x + 1, 1, z + 1, "propane_tank");
    }

    /** A drive-through wash bay: tin sides, the brushes long gone, a drain grate down the middle. */
    private void carWash(Placer q, int x, int z, int len) {
        for (int k = 0; k < len; k++) {
            for (int y = 1; y <= 4; y++) {
                q.set(x, y, z + k, y == 4 || chance() > 0.08 ? "corrugated_blue" : "air");
                q.set(x + 6, y, z + k, y == 4 || chance() > 0.08 ? "corrugated_blue" : "air");
            }
            for (int i = 0; i <= 6; i++) {
                q.set(x + i, 0, z + k, i == 3 ? "grate" : "floor_concrete_b");
                q.set(x + i, 5, z + k, "corrugated_grey");
            }
        }
        q.set(x + 3, 4, z, "hazard_stripes");
        if (chance() < 0.4) {
            q.vehicle(x + 3.5, 1, z + len / 2.0, 0, CARS, rng.nextLong());
        }
    }

    /** The old service station: the office and two bays, one island out front, tyres stacked by the door. */
    private void serviceStation(Placer q, int lw, int ld) {
        int bw = 18, bd = 11, bx = (lw - bw) / 2, bz = Math.min(14, ld - bd - 2);
        slab(q, 1, 0, lw - 2, bz + 1, chance() < 0.4);
        Bldg b = new Bldg(q.shifted(bx, 0, bz), rng);
        String wall = any("plaster_cream", "siding_white", "brick_buff", "plaster_sage");
        b.shell(0, 0, bw - 1, bd - 1, 1, 5, wall, "floor_concrete", "floor_concrete");
        Placer s = q.shifted(bx, 0, bz);
        // the office: glass front, the door, counter, register, a rack of oil
        for (int x = 1; x <= 5; x++) {
            for (int y = 1; y <= 3; y++) {
                s.set(x, y, 0, x == 3 && y < 3 ? "air" : chance() < 0.4 + hit * 0.4 ? "window_broken[axis=x]" : "window[axis=x]");
            }
        }
        b.door(3, 1, 0, "shop_door", "north");
        s.set(6, 1, 1, "store_counter[facing=west]");
        s.set(6, 2, 1, "store_register[facing=west]");
        s.set(1, 1, bd - 2, "store_shelf_hardware[facing=north]");
        s.set(2, 1, bd - 2, "store_shelf_hardware[facing=north]");
        s.set(4, 1, bd - 2, "store_vending[facing=north]");
        s.set(4, 2, bd - 2, "store_vending_top[facing=north]");
        for (int z = 1; z < bd - 1; z++) {
            s.set(7, 1, z, z == 3 ? "air" : wall);
            s.set(7, 2, z, z == 3 ? "air" : wall);
            s.set(7, 3, z, wall);
        }
        // two bays: one shut, one open with a car still up in it
        for (int bay = 0; bay < 2; bay++) {
            int x = 8 + bay * 5;
            for (int i = 0; i < 4; i++) {
                for (int y = 1; y <= 4; y++) {
                    s.set(x + i, y, 0, bay == 1 && y < 4 ? "air" : "garage_door[facing=north]");
                }
                for (int z = 1; z < bd - 1; z++) {
                    s.set(x + i, 0, z, i == 1 && z > 2 && z < 7 ? "grate" : "floor_concrete_b");
                }
            }
            s.set(x, 1, bd - 2, "store_pegboard[facing=north]");
            s.set(x + 1, 1, bd - 2, "minecraft:barrel[facing=up,loot=hardware]");
        }
        s.vehicle(14.5, 1, 5, 0, CARS, rng.nextLong());
        // the band round the top with the name on it, the roof
        for (int x = 0; x < bw; x++) {
            s.set(x, 5, 0, "steel_red");
        }
        Boards.fascia(s, rng, "fuel", 1, 5, -1, 6);
        b.flat(0, 0, bw - 1, bd - 1, 5, wall, 1, "stone_trim");
        s.set(bw - 3, 7, 3, "hvac");
        // the island, a cantilevered canopy over it in some
        int iz = bz - 6;
        for (int x = bx + 4; x <= bx + 12; x++) {
            q.set(x, 0, iz, "concrete_dark");
        }
        q.set(bx + 5, 1, iz, "gas_pump[facing=north]");
        q.set(bx + 11, 1, iz, chance() < 0.75 ? "gas_pump[facing=north]" : "debris_chunk");
        q.set(bx + 8, 1, iz, "minecraft:cauldron");
        if (chance() < 0.6) {
            for (int x = bx + 2; x <= bx + 14; x++) {
                for (int z = iz - 2; z <= bz - 1; z++) {
                    q.set(x, 5, z, z == iz - 2 ? "steel_red" : "minecraft:smooth_quartz_slab[type=top]");
                }
            }
            q.set(bx + 4, 4, iz, ShopLights.lamp(rng, 0.2 + hit * 0.2));
            q.set(bx + 12, 4, iz, ShopLights.lamp(rng, 0.2 + hit * 0.2));
            for (int y = 1; y <= 4; y++) {
                q.set(bx + 2, y, iz - 2, "streetlight_pole");
                q.set(bx + 14, y, iz - 2, "streetlight_pole");
            }
        }
        // tyres by the bay door, oil drums, the tow truck that never went back out
        for (int i = 0; i < 4; i++) {
            q.set(bx + bw + 1, 1 + i % 2, bz + 1 + i / 2, "tire[axis=z]");
        }
        q.set(bx - 2, 1, bz + 1, "minecraft:barrel[facing=up,loot=hardware]");
        q.set(bx - 2, 1, bz + 2, "minecraft:barrel[facing=up]");
        q.vehicle(bx + bw + 5, 1, bz + 5, 0, "tow_truck,pickup", rng.nextLong());
        junk(q, bx + bw - 3, bz + bd + 1);
        if (hit > 0.7) {
            scorch(q, bx + 8, iz, 3);
        }
        pylon(q, 2, 1, "fuel", 6);
    }

    /** The country store with two pumps on the gravel out front, a bench on the porch, bait for sale. */
    private void countryStore(Placer q, int lw, int ld) {
        int sw = 14 + rng.nextInt(3), sd = Math.min(16, ld - 11);
        int sx = (lw - sw) / 2, sz = 9;
        gravel(q, 1, 0, lw - 2, sz - 1);
        Storefront.Style st = chance() < 0.6 ? Storefront.Style.FALSE_FRONT : Storefront.Style.VICTORIAN;
        new Storefront(q.shifted(sx, 0, sz), rng, sw, sd, 1 + (chance() < 0.3 ? 1 : 0), st, Storefront.Trade.GENERAL).build();
        int px = sx + sw / 2 - 2;
        for (int i = -1; i <= 3; i++) {
            q.set(px + i, 0, 4, "concrete_dark");
            q.set(px + i, 0, 5, "concrete_dark");
        }
        q.set(px, 1, 4, "gas_pump[facing=north]");
        q.set(px + 2, 1, 4, "gas_pump[facing=north]");
        q.set(px + 1, 1, 5, "minecraft:barrel[facing=up,loot=hardware]");
        q.set(sx + 1, 1, sz - 2, "minecraft:spruce_stairs[facing=south]");
        q.set(sx + 2, 1, sz - 2, "minecraft:spruce_stairs[facing=south]");
        q.set(sx + sw + 1, 1, sz + 1, "propane_tank");
        q.set(sx + sw + 1, 1, sz + 3, "store_cooler[facing=west]");
        q.set(sx + sw + 1, 2, sz + 3, "store_cooler_top[facing=west]");
        Boards.board(q, rng, "bait", sx - 6, 3, 2);
        if (chance() < 0.6) {
            q.vehicle(px + 1.5, 1, 1.5, 90, "pickup,pickup,suv,wagon", rng.nextLong());
        }
        junk(q, sx + sw - 2, sz + sd + 1);
    }

    // ------------------------------------------------------------------ the motel

    private void motel() {
        int style = rng.nextInt(3);
        Kit.Lot lot = business(style == 2 ? 60 : 56, style == 1 ? 36 : 42);
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        int lw = lot.w(), ld = lot.d();
        String wall = any("plaster_cream", "plaster_rose", "plaster_sage", "plaster_blue", "brick_buff", "siding_white");
        String roof = any("minecraft:deepslate_tile", "minecraft:mud_brick", "minecraft:waxed_exposed_cut_copper", "minecraft:dark_oak");
        int rooms;
        if (style == 1) {
            // a two-storey bar of rooms across the back, the walkway along the upper floor
            int n = Math.max(5, (lw - 16) / 5);
            int bz = ld - 12;
            slab(q, 1, 0, lw - 2, bz - 1, true);
            for (int i = 0; i < n; i++) {
                motelRoom(q.shifted(2 + i * 5, 0, bz), wall, 0, i);
                motelRoom(q.shifted(2 + i * 5, 4, bz), wall, 4, i + n);
            }
            int bx1 = 2 + n * 5;
            walkway(q, 1, bx1, bz - 2, 4);
            for (int x = 1; x <= bx1; x++) {
                q.set(x, 8, bz - 1, "minecraft:smooth_stone_slab[type=bottom]");
                for (int z = bz; z < bz + 10; z++) {
                    q.set(x, 8, z, Bldg.full(roof));
                }
            }
            // the stair up at the end
            for (int k = 0; k < 4; k++) {
                q.set(bx1 + 1, 1 + k, bz - 2 - 4 + k, "steel_stairs[facing=south]");
            }
            parking(q, 2, bx1 - 1, bz - 8, 0.35, CARS);
            rooms = 2 * n;
        } else {
            // single storey: a row across the back, and a wing down one side (or both, round a courtyard)
            int n = Math.max(4, (lw - 22) / 5);
            int bz = ld - 11;
            slab(q, 1, 0, lw - 2, bz - 1, true);
            int x0 = style == 2 ? 11 : 2;
            for (int i = 0; i < n; i++) {
                motelRoom(q.shifted(x0 + i * 5, 0, bz), wall, 0, i);
            }
            walkway(q, x0 - 1, x0 + n * 5, bz - 2, 0);
            gableRow(q, x0 - 1, x0 + n * 5, bz - 2, bz + 10, 4, roof, wall);
            int wing = Math.max(2, (bz - 12) / 5);
            Placer side = q.turned(x0 - 1, 0, bz - 3, 1);
            for (int i = 0; i < wing; i++) {
                motelRoom(side.shifted(i * 5, 0, 0), wall, 0, n + i);
            }
            rooms = n + wing;
            if (style == 2) {
                Placer other = q.turned(x0 + n * 5, 0, bz - 3 - wing * 5, 3);
                for (int i = 0; i < wing; i++) {
                    motelRoom(other.shifted(i * 5, 0, 0), wall, 0, n + wing + i);
                }
                rooms += wing;
                pool(q, x0 + n * 5 / 2 - 6, bz - 16);
            } else {
                parking(q, x0 + 2, x0 + n * 5 - 2, bz - 8, 0.35, CARS);
                pool(q, lw - 16, 4);
            }
        }
        // the office by the road, the tall sign, the ice machine
        int ox = style == 2 ? 2 : lw - 12;
        office(q, ox, 3, wall, roof);
        pylonBlade(q, style == 2 ? lw - 4 : 3, 2);
        junk(q, lw - 5, ld - 2);
        if (hit > 0.6) {
            scorch(q, lw / 2, ld / 2, 3);
        }
        if (rooms > 0 && chance() < 0.5) {
            q.vehicle(lw / 2.0, 1, 3, 90, "police_sedan,police_suv,ambulance", rng.nextLong());
        }
    }

    /**
     * One motel room, 5 wide and 10 deep in its own frame (front at z = 0): the door and a window,
     * a bed, a nightstand and the TV, the bathroom at the back, a lamp box over the door.
     */
    private void motelRoom(Placer r, String wall, int y0, int number) {
        Bldg b = new Bldg(r, rng);
        b.shell(0, 0, 4, 9, 1, 4, wall, "floor_lino", "floor_lino");
        if (chance() < 0.2 + hit * 0.3) {
            r.set(1, 1, 0, "air");  // kicked in, frame and all
            r.set(1, 2, 0, "air");
        } else {
            b.door(1, 1, 0, "flat_door", "north");
        }
        r.set(3, 2, 0, chance() < 0.3 + hit * 0.4 ? "window_broken[axis=x]" : chance() < 0.2 ? "boarded_window" : "window[axis=x]");
        String bed = any("red", "blue", "white", "brown", "green");
        r.set(2, 1, 4, "minecraft:" + bed + "_bed[facing=north,part=head]");
        r.set(2, 1, 5, "minecraft:" + bed + "_bed[facing=north,part=foot]");
        r.set(3, 1, 3, "minecraft:spruce_slab[type=top]");
        r.set(1, 1, 6, "minecraft:dark_oak_planks");
        r.set(1, 2, 6, chance() < 0.5 ? "minecraft:black_carpet" : "air");
        for (int x = 1; x <= 3; x++) {
            r.set(x, 1, 7, x == 2 ? "air" : "drywall");
            r.set(x, 2, 7, x == 2 ? "air" : "drywall");
            r.set(x, 3, 7, "drywall");
        }
        r.set(1, 1, 8, "minecraft:cauldron");
        r.set(3, 1, 8, "store_spill");
        if (chance() < 0.35) {
            r.set(2, 0, 4, "blood_splat");
        }
        if (chance() < 0.55) {
            r.set(2, 3, -1, ShopLights.lamp(rng, 0.25 + hit * 0.2));
        }
        if (chance() < 0.3) {
            r.set(1 + rng.nextInt(3), 1, 1 + rng.nextInt(5), "minecraft:barrel[facing=up,loot=scraps]");
        }
    }

    /** The covered walkway along the rooms: a slab roof on posts, at floor height y. */
    private void walkway(Placer q, int x0, int x1, int z, int y) {
        for (int x = x0; x <= x1; x++) {
            q.set(x, y, z, y == 0 ? "sidewalk" : "deck_steel");
            q.set(x, y, z + 1, y == 0 ? "sidewalk" : "deck_steel");
            if (y > 0) {
                q.set(x, y + 1, z, "railing[axis=x]");
            }
            if ((x - x0) % 5 == 0) {
                for (int k = 1; k <= 3; k++) {
                    q.set(x, y + k, z, "streetlight_pole");
                }
            }
            q.set(x, y + 4, z, "minecraft:smooth_stone_slab[type=bottom]");
            q.set(x, y + 4, z + 1, "minecraft:smooth_stone_slab[type=bottom]");
        }
    }

    /** A low gable over a row of rooms, running along x from x0 to x1 and z0 to z1, eaves at y. */
    private void gableRow(Placer q, int x0, int x1, int z0, int z1, int y, String roof, String wall) {
        new Bldg(q, rng).gable(x0, x1, z0, z1, y, roof, false, wall, 0);
    }

    /** The office: reception desk, key board, the ice machine and vending outside, MOTEL over the door. */
    private void office(Placer q, int x, int z, String wall, String roof) {
        Placer o = q.shifted(x, 0, z);
        Bldg b = new Bldg(o, rng);
        b.shell(0, 0, 9, 7, 1, 4, wall, "floor_lino", "floor_lino");
        for (int i = 1; i <= 8; i++) {
            if (i != 4) {
                o.set(i, 2, 0, chance() < 0.4 + hit * 0.4 ? "window_broken[axis=x]" : "window[axis=x]");
            }
        }
        b.door(4, 1, 0, "shop_door", "north");
        for (int i = 2; i <= 7; i++) {
            o.set(i, 1, 3, i == 7 ? "air" : "reception_desk[facing=north]");
        }
        o.set(2, 1, 6, "filing_cabinet[facing=north]");
        o.set(5, 1, 6, "store_pegboard[facing=north]");
        o.set(1, 1, 1, "planter");
        b.gable(0, 9, 0, 7, 4, roof, false, wall, 1);
        Boards.fascia(o, rng, "motel", 0, 4, -1, 10);
        o.set(4, 3, -1, ShopLights.lamp(rng, 0.15));
        o.set(10, 1, 1, "store_vending[facing=west]");
        o.set(10, 2, 1, "store_vending_top[facing=west]");
        o.set(10, 1, 3, "store_cooler[facing=west]");
        o.set(10, 2, 3, "store_cooler_top[facing=west]");
    }

    /** The road sign: a tall steel mast with MOTEL down it in neon, a lamp box and VACANCY board below. */
    private void pylonBlade(Placer q, int x, int z) {
        int top = 11 + rng.nextInt(3);
        for (int y = 1; y <= top; y++) {
            q.set(x, y, z + 1, "streetlight_pole");
        }
        Boards.blade(q, rng, "motel", 5, x, top, z, hit < 0.75 && chance() < 0.7);
        q.set(x, top + 1, z, "steel_red");
        q.set(x, top - 6, z, ShopLights.lamp(rng, 0.2));
        q.set(x, 0, z + 1, "concrete_dark");
    }

    /** The pool, drained: tiled walls, leaves and trash at the deep end, a chain-link fence round it. */
    private void pool(Placer q, int x, int z) {
        int pw = 12, pd = 8;
        for (int i = -1; i <= pw; i++) {
            for (int j = -1; j <= pd; j++) {
                boolean fence = i == -1 || j == -1 || i == pw || j == pd;
                if (fence) {
                    q.set(x + i, 0, z + j, "sidewalk");
                    if (chance() > 0.12 && !(j == pd && i == pw / 2)) {
                        q.set(x + i, 1, z + j, "chainlink[axis=" + (j == -1 || j == pd ? "x" : "z") + "]");
                    }
                    continue;
                }
                boolean rim = i == 0 || j == 0 || i == pw - 1 || j == pd - 1;
                if (rim) {
                    q.set(x + i, 0, z + j, "minecraft:smooth_quartz");
                    continue;
                }
                int depth = i < pw / 2 ? 2 : 3;
                for (int k = 0; k < depth; k++) {
                    q.set(x + i, -k, z + j, "air");
                }
                q.set(x + i, -depth, z + j, "minecraft:light_blue_terracotta");
                double r = chance();
                if (depth == 3 && r < 0.35) {
                    q.set(x + i, -2, z + j, r < 0.15 ? "leaf_litter" : r < 0.25 ? "trash_bag[facing=north]" : "minecraft:water");
                }
            }
        }
        for (int j = 1; j < pd - 1; j++) {
            for (int k = 1; k <= 3; k++) {
                q.set(x, -k, z + j, "minecraft:cyan_terracotta");
                q.set(x + pw - 1, -k, z + j, "minecraft:cyan_terracotta");
            }
        }
        q.set(x + 1, -1, z + 1, "ladder[facing=east]");
        q.set(x + 1, 1, z - 2, "store_chair[facing=south]");
        q.set(x + 4, 1, z - 2, "store_chair[facing=south]");
    }

    // ------------------------------------------------------------------ the diner

    private void diner() {
        int style = rng.nextInt(3);
        Kit.Lot lot = business(style == 2 ? 56 : 40, style == 2 ? 40 : 32);
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        int lw = lot.w(), ld = lot.d();
        int dw = style == 0 ? 22 : 18, dd = style == 0 ? 9 : 14;
        int dx = (lw - dw) / 2, dz = Math.min(12, ld - dd - 4);
        if (style == 2) {
            gravel(q, 1, 0, lw - 2, ld - 2);
            dx = 3;
        } else {
            slab(q, 1, 0, lw - 2, dz - 1, true);
            parking(q, 2, lw - 3, 1, 0.3, CARS);
        }
        Placer s = q.shifted(dx, 0, dz);
        if (style == 0) {
            railcar(s, dw, dd);
        } else {
            brickDiner(s, dw, dd);
        }
        if (style == 2) {
            // the rigs out back, nose to tail, and a fuel island for them
            for (int i = 0; i < 3; i++) {
                if (chance() < 0.75) {
                    q.vehicle(dx + dw + 6 + i * 5, 1, ld - 10, 0, TRUCKS, rng.nextLong());
                }
            }
            for (int x = dx + dw + 4; x <= dx + dw + 14 && x < lw - 2; x++) {
                q.set(x, 0, 6, "concrete_dark");
            }
            q.set(dx + dw + 6, 1, 6, "gas_pump[facing=north]");
            q.set(dx + dw + 11, 1, 6, "gas_pump[facing=north]");
            pylon(q, lw - 7, 1, "route9", 8);
        } else {
            pylon(q, 2, 1, style == 0 ? "joes" : any("joes", "cafe"), 6 + rng.nextInt(3));
        }
        junk(q, dx + dw + 1, dz + dd - 1);
        q.set(dx + dw + 1, 1, dz + 2, "minecraft:barrel[facing=up]");
        q.set(dx - 2, 1, dz, "lamp_post");
        if (hit > 0.65) {
            scorch(q, dx + dw / 2, dz - 3, 3);
            q.vehicle(dx + dw / 2.0, 1, dz - 3, 90, "police_sedan,sedan", rng.nextLong());
        }
    }

    /** The stainless railcar diner: a long low barrel-roofed car, a band of windows, the counter inside. */
    private void railcar(Placer s, int dw, int dd) {
        Bldg b = new Bldg(s, rng);
        b.shell(0, 0, dw - 1, dd - 1, 1, 4, "deck_steel", "floor_checker", "floor_checker");
        for (int x = 1; x < dw - 1; x++) {
            s.set(x, 2, 0, x == dw / 2 ? "air" : chance() < 0.35 + hit * 0.4 ? "window_broken[axis=x]" : "window[axis=x]");
            s.set(x, 2, dd - 1, chance() < 0.3 ? "window_broken[axis=x]" : "window[axis=x]");
            s.set(x, 1, 0, "steel_red");
            s.set(x, 3, 0, "minecraft:smooth_quartz");
        }
        b.door(dw / 2, 1, 0, "shop_door", "north");
        // the barrel roof: two slab steps up to a flat crown
        for (int x = 0; x < dw; x++) {
            for (int z = 0; z < dd; z++) {
                boolean outer = z == 0 || z == dd - 1;
                s.set(x, 4, z, outer ? "minecraft:smooth_quartz_slab[type=bottom]" : "minecraft:smooth_quartz");
                if (!outer && z > 1 && z < dd - 2) {
                    s.set(x, 5, z, "minecraft:smooth_quartz_slab[type=bottom]");
                }
            }
        }
        // the counter down the middle with its stools, booths at the windows, the grill behind
        for (int x = 2; x < dw - 2; x++) {
            s.set(x, 1, dd / 2 + 1, "store_counter[facing=north]");
            if (x % 2 == 0) {
                s.set(x, 1, dd / 2, "store_stool");
            }
            s.set(x, 1, dd - 2, x % 4 == 0 ? "store_grill[facing=north]" : x % 4 == 2 ? "store_coffee[facing=north]" : "store_counter[facing=north]");
        }
        for (int x = 1; x < dw - 1; x += 3) {
            if (Math.abs(x - dw / 2) > 1) {
                s.set(x, 1, 1, "store_booth[facing=east]");
                s.set(x + 1, 1, 1, "store_table");
            }
        }
        s.set(dw - 3, 2, dd / 2 + 1, "store_case_pies[facing=north]");
        s.set(2, 2, dd / 2 + 1, "store_register[facing=north]");
        s.set(dw / 2 + 2, 3, -1, ShopLights.lamp(rng, 0.15));
        Boards.blade(s, rng, "diner", 5, dw - 1, 5, -1, hit < 0.7 && chance() < 0.7);
        Boards.fascia(s, rng, "joes", 1, 4, -1, dw - 2);
    }

    /** A brick roadside diner: big windows, the sign on the roof edge, EAT down the corner in neon. */
    private void brickDiner(Placer s, int dw, int dd) {
        Bldg b = new Bldg(s, rng);
        String wall = any("brick_red", "brick_buff", "plaster_cream", "brick_orange");
        b.shell(0, 0, dw - 1, dd - 1, 1, 5, wall, "floor_checker", "floor_checker");
        for (int x = 1; x < dw - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                if (x != dw / 2 || y == 3) {
                    s.set(x, y, 0, y == 1 ? wall : chance() < 0.3 + hit * 0.4 ? "window_broken[axis=x]" : "window[axis=x]");
                }
            }
        }
        b.door(dw / 2, 1, 0, "shop_door", "north");
        for (int z = 2; z < dd - 4; z += 3) {
            s.set(1, 1, z, "store_booth[facing=east]");
            s.set(2, 1, z, "store_table");
            s.set(dw - 2, 1, z, "store_booth[facing=west]");
            s.set(dw - 3, 1, z, "store_table");
        }
        for (int x = 3; x < dw - 3; x++) {
            s.set(x, 1, dd - 4, x == dw - 4 ? "air" : "store_counter[facing=north]");
            if (x % 2 == 1) {
                s.set(x, 1, dd - 5, "store_stool");
            }
            s.set(x, 1, dd - 2, x % 3 == 0 ? "store_grill[facing=north]" : x % 3 == 1 ? "store_oven[facing=north]" : "store_counter[facing=north]");
        }
        s.set(4, 2, dd - 4, "store_register[facing=north]");
        s.set(6, 2, dd - 4, "store_case_pies[facing=north]");
        s.set(dw - 5, 3, dd - 1, "store_menu[facing=north]");
        b.flat(0, 0, dw - 1, dd - 1, 5, wall, 1, "stone_trim");
        Boards.fascia(s, rng, "joes", 1, 5, -1, dw - 2);
        Boards.blade(s, rng, "eat", 3, dw, 5, -1, hit < 0.7 && chance() < 0.75);
        s.set(dw / 2 - 2, 4, -1, ShopLights.lamp(rng, 0.15));
        s.set(dw / 2 + 2, 4, -1, ShopLights.lamp(rng, 0.15));
        s.set(dw - 3, 7, 4, "hvac");
    }

    // ------------------------------------------------------------------ the church

    private void church() {
        Civic.Church kind = Civic.Church.values()[rng.nextInt(3)];
        int[] size = Civic.churchSize(kind);
        Kit.Lot lot = business(size[0] + 26, size[1] + 6);
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        new Civic(q, rng).church(kind, size[0], Math.min(size[1], lot.d()), any("church", "methodist", "stmary"));
        // the graveyard beside it, a gravel lot on the other side or out front
        new Civic(q.shifted(size[0] + 2, 0, 0), rng).graveyard(0, 8, Math.min(14, lot.w() - size[0] - 4), lot.d() - 3);
        int gx = Math.min(lot.w() - 2, size[0] + 17);
        if (gx + 6 < lot.w() - 1) {
            gravel(q, gx, 2, lot.w() - 2, 16);
            for (int i = 0; i < 3; i++) {
                if (chance() < 0.4) {
                    q.vehicle(gx + 2 + i * 3, 1, 9, 0, CARS, rng.nextLong());
                }
            }
        }
        if (hit > 0.5) {
            // where they made their stand: pews dragged to the doors, the last notes on the board
            q.set(size[0] / 2 - 1, 1, 4, "sandbags");
            q.set(size[0] / 2 + 1, 1, 4, "sandbags");
            q.set(size[0] / 2, 1, 3, "blood_splat");
        }
    }

    // ------------------------------------------------------------------ the school

    private void school() {
        boolean high = chance() < 0.5;
        Kit.Lot lot = business(high ? 100 : 76, high ? 80 : 62);
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        int lw = lot.w(), ld = lot.d();
        int sw = Math.min(high ? 66 : 54, lw - 6), sd = 36;
        new Civic(q.shifted(2, 0, 0), rng).school(sw, sd, high);
        new Civic(q, rng).flagpole(sw + 5, 6, 9);
        // the bus lot beside the school, the yellow buses that never left
        int bx = sw + 4;
        if (lw - bx > 14) {
            slab(q, bx, 10, lw - 3, Math.min(ld - 3, 34), true);
            for (int i = 0; bx + 3 + i * 5 < lw - 4; i++) {
                if (chance() < 0.7) {
                    q.vehicle(bx + 3.5 + i * 5, 1, 22, 0, "school_bus", rng.nextLong());
                }
            }
        }
        int backZ = sd + 2;
        if (high && ld - backZ > 30) {
            football(q, 4, backZ, Math.min(lw - 8, 60), ld - backZ - 2);
        } else if (ld - backZ > 12) {
            int[] a = q.world(6, backZ + 2), c = q.world(18, backZ + 2);
            kit.playground(Math.min(a[0], a[0]), Math.min(a[1], a[1]));
            kit.court(c[0], c[1]);
        }
        if (hit > 0.4) {
            evacuation(q, sw / 2 - 10, 2);
        }
    }

    /** The football field: lines, goalposts, bleachers down one side, light towers at the corners. */
    private void football(Placer q, int x0, int z0, int fw, int fd) {
        for (int x = x0; x < x0 + fw; x++) {
            for (int z = z0; z < z0 + fd; z++) {
                boolean line = x == x0 || x == x0 + fw - 1 || z == z0 || z == z0 + fd - 1 || (x - x0) % 6 == 0;
                q.set(x, 0, z, line && chance() < 0.85 ? "minecraft:white_concrete_powder" : chance() < 0.15 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                q.set(x, 1, z, chance() < 0.25 ? "minecraft:short_grass" : "air");
            }
        }
        int mid = z0 + fd / 2;
        for (int x : new int[] {x0 + 1, x0 + fw - 2}) {
            for (int y = 1; y <= 5; y++) {
                q.set(x, y, mid, "minecraft:yellow_concrete");
            }
            for (int k = -2; k <= 2; k++) {
                q.set(x, 3, mid + k, "minecraft:yellow_concrete");
                q.set(x, 4, mid + (k < 0 ? -2 : 2), "minecraft:yellow_concrete");
                q.set(x, 5, mid + (k < 0 ? -2 : 2), "minecraft:yellow_concrete");
            }
        }
        // bleachers: aluminium benches stepping up, on the side nearest the school
        for (int x = x0 + 6; x < x0 + fw - 6; x++) {
            for (int k = 0; k < 4; k++) {
                q.set(x, 1 + k, z0 - 2 - k, chance() < 0.08 ? "air" : "minecraft:polished_andesite_slab[type=top]");
                for (int y = 1; y <= k; y++) {
                    q.set(x, y, z0 - 2 - k, "minecraft:iron_bars");
                }
            }
        }
        for (int[] c : new int[][] {{x0 - 1, z0 - 1}, {x0 + fw, z0 - 1}, {x0 - 1, z0 + fd}, {x0 + fw, z0 + fd}}) {
            for (int y = 1; y <= 14; y++) {
                q.set(c[0], y, c[1], "truss_grey");
            }
            q.set(c[0], 15, c[1], "streetlight_head[facing=" + (c[1] < z0 ? "south" : "north") + "]");
            q.set(c[0], 16, c[1], "deck_steel");
        }
    }

    /** What the school became: an aid station out front, sandbags, a fence line, the tents. */
    private void evacuation(Placer q, int x, int z) {
        for (int i = 0; i < 20; i++) {
            if (chance() > 0.15) {
                q.set(x + i, 1, z, i % 7 == 3 ? "air" : "hesco");
            }
        }
        for (int t = 0; t < 2; t++) {
            int tx = x + 2 + t * 9;
            for (int i = 0; i < 6; i++) {
                for (int j = 0; j < 4; j++) {
                    q.set(tx + i, 2, z + 2 + j, "tarp_green");
                }
                q.set(tx + i, 1, z + 2, i == 0 || i == 5 ? "pole_wood" : "air");
            }
            q.set(tx + 2, 1, z + 3, "minecraft:white_bed[facing=north,part=foot]");
            q.set(tx + 2, 1, z + 2, "minecraft:white_bed[facing=north,part=head]");
        }
        q.set(x + 19, 1, z + 3, "sign_quarantine_0[facing=south]");
        q.vehicle(x + 10, 1, z + 8, 90, "ambulance,ambulance_van,police_suv", rng.nextLong());
    }

    // ------------------------------------------------------------------ the warehouse

    private void warehouse() {
        int style = rng.nextInt(3);
        Kit.Lot lot = business(64, 54);
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        int lw = lot.w(), ld = lot.d();
        slab(q, 1, 0, lw - 2, ld - 2, true);
        // the fence round the yard, the gate rolled open
        Kit.chainlink(q, rng, 0, 2, lw - 1, 2, 2);
        Kit.chainlink(q, rng, 0, ld - 1, lw - 1, ld - 1, 2);
        Kit.chainlink(q, rng, 0, 3, 0, ld - 2, 2);
        Kit.chainlink(q, rng, lw - 1, 3, lw - 1, ld - 2, 2);
        for (int x = 8; x < 16; x++) {
            q.set(x, 1, 2, "air");
            q.set(x, 2, 2, "air");
        }
        String clad = style == 1 ? "corrugated_green" : any("corrugated_grey", "corrugated_blue", "corrugated_rust");
        int bw = Math.min(40, lw - 16), bd = Math.min(24, ld - 22), bx = lw - bw - 3, bz = ld - bd - 3;
        if (style == 2) {
            bw = Math.min(24, lw - 20);
            bx = lw - bw - 3;
        }
        Bldg b = new Bldg(q.shifted(bx, 0, bz), rng);
        Placer s = q.shifted(bx, 0, bz);
        b.shell(0, 0, bw - 1, bd - 1, 1, 8, clad, "floor_concrete", "floor_concrete");
        // dock doors along the front, a raised dock with tyre bumpers, trailers backed up to it
        int docks = Math.max(2, (bw - 8) / 6);
        for (int i = 0; i < docks; i++) {
            int x = 4 + i * 6;
            for (int k = 0; k < 4; k++) {
                for (int y = 2; y <= 5; y++) {
                    s.set(x + k, y, 0, i % 2 == 1 && y < 5 && chance() < 0.7 ? "air" : "garage_door[facing=north]");
                }
            }
            for (int k = 0; k < 4; k++) {
                s.set(x + k, 1, -1, "concrete_dark");
            }
            s.set(x - 1, 1, -2, "tire[axis=x]");
            s.set(x + 4, 1, -2, "tire[axis=x]");
            if (chance() < 0.6) {
                s.vehicle(x + 2, 1, -8, 0, "box_truck,moving_truck", rng.nextLong());
            }
            s.set(x + 2, 6, -1, ShopLights.lamp(rng, 0.3));
        }
        for (int x = 0; x < bw; x++) {
            for (int z = -1; z >= -2; z--) {
                if (s.get(x, 1, z) == null || "air".equals(s.get(x, 1, z))) {
                    s.set(x, 0, z, "floor_concrete");
                }
            }
        }
        if (style == 1) {
            b.gable(0, bw - 1, 0, bd - 1, 8, "minecraft:polished_andesite", false, clad, 1);  // the feed store's barn roof
        } else {
            b.flat(0, 0, bw - 1, bd - 1, 8, clad, 1, "minecraft:smooth_stone_slab[type=bottom]");
        }
        // racks of crates and boxes inside, aisles between
        for (int x = 3; x < bw - 3; x += 4) {
            for (int z = 4; z < bd - 2; z++) {
                for (int y = 1; y <= 3; y++) {
                    double r = chance();
                    s.set(x, y, z, r < 0.35 ? "crate" : r < 0.6 ? "cardboard_box" : r < 0.7 ? "minecraft:barrel[facing=up,loot=storage]" : "air");
                }
                s.set(x, 4, z, "steel_yellow");
            }
        }
        // the office on the end, its windows onto the yard
        Placer o = q.shifted(bx - 10, 0, bz);
        Bldg ob = new Bldg(o, rng);
        ob.shell(0, 0, 9, 8, 1, 4, "concrete_panel", "office_floor", "office_floor");
        for (int x = 1; x < 9; x++) {
            o.set(x, 2, 0, x == 5 ? "air" : chance() < 0.4 ? "window_broken[axis=x]" : "window[axis=x]");
        }
        ob.door(5, 1, 0, "fire_door", "north");
        o.set(2, 1, 3, "desk[facing=north]");
        o.set(2, 1, 4, "office_chair[facing=north]");
        o.set(7, 1, 6, "filing_cabinet[facing=west]");
        ob.flat(0, 0, 9, 8, 4, "concrete_panel", 1, "stone_trim");
        String sign = switch (style) {
            case 1 -> "feed";
            case 2 -> "kessler";
            default -> "storage";
        };
        Boards.fascia(o, rng, sign, 0, 4, -1, 10);
        o.set(5, 3, -1, ShopLights.lamp(rng, 0.2));
        if (style == 1) {
            // feed and farm supply: a blue silo and a stack of bagged feed on pallets
            for (int y = 1; y <= 14; y++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        if (dx * dx + dz * dz <= 5) {
                            q.set(6 + dx, y, ld - 8 + dz, "silo_blue");
                        }
                    }
                }
            }
            for (int i = 0; i < 4; i++) {
                q.set(14 + i, 1, ld - 5, "minecraft:hay_block");
                q.set(14 + i, 2, ld - 5, chance() < 0.6 ? "minecraft:hay_block" : "air");
            }
        } else if (style == 2) {
            // the lumber yard: stacks of boards under a lean-to, a tarp blown half off
            for (int x = 4; x < bx - 12; x += 3) {
                for (int z = 8; z < ld - 6; z++) {
                    for (int y = 1; y <= 1 + rng.nextInt(3); y++) {
                        q.set(x, y, z, "store_lumber[facing=north]");
                    }
                }
            }
        }
        q.vehicle(10, 1, 12, 90, "pickup,box_truck,step_van", rng.nextLong());
        junk(q, 3, ld - 4);
        if (hit > 0.6) {
            scorch(q, bx + bw / 2, bz - 6, 4);
        }
    }

    // ------------------------------------------------------------------ the trailer lot

    private void trailerLot() {
        if (spur == null) {
            return;
        }
        // single-wides nose to tail down both sides of the drive, the odd double-wide
        Homes homes = new Homes(kit);
        Homes.Mix mix = new Homes.Mix(Homes.Mix.weights(0, 0, 0, 0, 0.1, 0.9, 0, 0), 2, 0.05, 0.05, 0.1, 0.3,
                0.45, 0.12, 0.9, false, false);
        for (Kit.Lot lot : kit.lotsAlong(spur, 15, 18, 19, 1, 9, spur.length() + 4, 0)) {
            homes.home(lot, mix);
        }
        // the sign and the mailboxes at the mouth of the drive, junk and a dumpster at the loop
        double[] mouth = spur.point(5);
        int mx = (int) Math.round(mouth[0]), mz = (int) Math.round(mouth[1]);
        int k = Boards.WIDTH.get("shadyacres");
        Placer sign = bizSide > 0 ? new Placer(out, mx + 4, 0, mz, 0) : new Placer(out, mx + 4 + k - 1, 0, mz, 2);
        Boards.board(sign, rng, "shadyacres", 0, 3, 0);
        for (int i = 0; i < 4; i++) {
            int bx = mx - 4, bz = mz + bizSide * (1 + i);
            if (kit.use(bx, bz) != Kit.ROAD && out.get(bx, 1, bz) == null) {
                out.set(bx, 1, bz, "mailbox[facing=east]");
            }
        }
        double[] end = spur.point(spur.length());
        int ex = (int) Math.round(end[0]) + 9, ez = (int) Math.round(end[1]) + bizSide * 5;
        if (inside(ex, ez) && kit.use(ex, ez) == Kit.FREE) {
            out.set(ex, 1, ez, "dumpster[facing=west]");
            out.set(ex, 1, ez + 2, "tire[axis=x]");
            out.set(ex + 1, 1, ez + 2, "tire[axis=z]");
            out.vehicle(ex - 1.5, 1, ez - 3, 90, "pickup,suv,wagon", rng.nextLong());
        }
    }

    // ------------------------------------------------------------------ along the road

    private void roadside() {
        // power down the road, the odd wreck, a fallen pole across the shoulder now and then
        double zr = zAt(road, w / 2.0);
        int side = chance() < 0.5 ? -9 : 9;
        int pz = (int) Math.round(zr + side);
        if (pz > 2 && pz < d - 3) {
            kit.powerLine(4, pz, w - 5, pz, 8, 20);
        }
        kit.abandonedCars(road, 1.6);
        if (chance() < 0.35) {
            int x = 6 + rng.nextInt(Math.max(1, w - 12));
            int z = (int) Math.round(zAt(road, x)) + (chance() < 0.5 ? -5 : 5);
            if (inside(x + 6, z) && kit.use(x, z) != Kit.ROAD) {
                for (int i = 0; i < 6; i++) {
                    setIfAir(x + i, 1, z, "fallen_pole[axis=x]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ across the road

    /**
     * The other side of the road from the stop: an old pasture gone to seed or a field of corn
     * nobody cut, fenced off from the road, and often a billboard over the weeds facing the
     * traffic that stopped coming.
     */
    private void across() {
        if (bizSide == 0) {
            return;
        }
        int side = -bizSide;
        double mid = (freeA + freeB) / 2;
        double zr = zAt(road, mid);
        int depth = (int) (side > 0 ? d - 12 - zr - 7 : zr - 12 - 7);
        int lw = (int) (freeB - freeA) - 4;
        if (depth < 14 || lw < 20) {
            return;
        }
        double[] q = road.point(at(road, mid));
        Kit.Lot lot = kit.fit(q[0], q[1], -q[3] * side, q[2] * side, lw, depth);
        if (lot == null) {
            return;
        }
        Placer f = lot.placer(out);
        int fw = lot.w(), fd = lot.d();
        boolean corn = chance() < 0.35;
        if (corn) {
            cornField(f, fw, fd);
        } else {
            Kit.lawn(f, rng, 0, 0, fw - 1, fd - 1, 1.0);
            seedHeads(f, fw, fd);
        }
        fieldFence(f, fw, fd, corn ? "minecraft:spruce_fence" : "barbed_wire");
        if (chance() < 0.6 && fw >= 22) {
            billboard(f, 4 + rng.nextInt(fw - 21), 3 + rng.nextInt(3));
        }
        // a lone tree or two left standing in the field, a rotting bale, an abandoned pickup in the weeds
        for (int i = 0, n = rng.nextInt(3); i < n; i++) {
            int x = 4 + rng.nextInt(fw - 8), z = fd / 2 + rng.nextInt(Math.max(1, fd / 2 - 4));
            int[] at = f.world(x, z);
            kit.tree(chance() < 0.25 ? Trees.Kind.DEAD : chance() < 0.5 ? Trees.Kind.OAK : Trees.Kind.MAPLE, at[0], at[1]);
        }
        if (!corn) {
            for (int i = 0, n = rng.nextInt(4); i < n; i++) {
                int x = 3 + rng.nextInt(fw - 7), z = 8 + rng.nextInt(Math.max(1, fd - 12));
                f.set(x, 1, z, "bale_wrap");
                if (chance() < 0.5) {
                    f.set(x + 1, 1, z, "bale_wrap");
                }
            }
        }
        if (chance() < 0.3) {
            f.vehicle(6 + rng.nextInt(Math.max(1, fw - 12)), 1, fd / 2.0 + rng.nextInt(Math.max(1, fd / 3)),
                    rng.nextInt(360), "pickup,sedan_ltd,wagon,jeep", rng.nextLong());
        }
    }

    /** Seed heads and goldenrod-yellow through the grass of a field nobody mows. */
    private void seedHeads(Placer f, int fw, int fd) {
        for (int x = 1; x < fw - 1; x++) {
            for (int z = 2; z < fd - 1; z++) {
                if (f.get(x, 1, z) != null || !"minecraft:grass_block".equals(f.get(x, 0, z))) {
                    continue;
                }
                double r = chance();
                if (r < 0.3) {
                    f.set(x, 1, z, "minecraft:tall_grass[half=lower]");
                    f.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                } else if (r < 0.36) {
                    f.set(x, 1, z, "minecraft:tall_dry_grass");
                } else if (r < 0.4) {
                    f.set(x, 1, z, any("minecraft:dandelion", "minecraft:oxeye_daisy", "minecraft:wildflowers[flower_amount=3]"));
                } else if (r < 0.42) {
                    f.set(x, 1, z, "minecraft:sweet_berry_bush[age=1]");
                }
            }
        }
    }

    /** Corn left standing dry since the fall, rows gone ragged, a patch flattened where something lay down in it. */
    private void cornField(Placer f, int fw, int fd) {
        int fx = 3 + rng.nextInt(Math.max(1, fw - 10)), fz = 6 + rng.nextInt(Math.max(1, fd - 14)), fr = 2 + rng.nextInt(3);
        for (int x = 1; x < fw - 1; x++) {
            for (int z = 2; z < fd - 1; z++) {
                f.set(x, 0, z, chance() < 0.75 ? "minecraft:dirt" : "minecraft:coarse_dirt");
                boolean flat = Math.abs(x - fx) + Math.abs(z - fz) <= fr;
                if ((x - 1) % 3 == 2 || flat) {
                    if (chance() < 0.2) {
                        f.set(x, 1, z, flat ? "minecraft:short_dry_grass" : "minecraft:short_grass");
                    }
                    continue;
                }
                double r = chance();
                if (r < 0.78) {
                    f.set(x, 1, z, "corn_dry_lower");
                    f.set(x, 2, z, "corn_dry_upper");
                } else if (r < 0.88) {
                    f.set(x, 1, z, "minecraft:short_dry_grass");
                }
            }
        }
    }

    /** Posts every four along the front and sides of a field, rails or wire between, a gate onto the road. */
    private void fieldFence(Placer f, int fw, int fd, String rail) {
        int gate = 2 + rng.nextInt(Math.max(1, fw - 6));
        for (int x = 0; x < fw; x++) {
            fieldPost(f, x, 0, rail, "x", x % 4 == 0, x == gate || x == gate + 1);
        }
        for (int z = 1; z < fd - 1; z++) {
            fieldPost(f, 0, z, rail, "z", z % 4 == 0, false);
            fieldPost(f, fw - 1, z, rail, "z", z % 4 == 0, false);
        }
    }

    private void fieldPost(Placer f, int x, int z, String rail, String axis, boolean post, boolean gate) {
        if (gate) {
            if (chance() < 0.5) {
                f.set(x, 1, z, "minecraft:spruce_fence_gate[facing=north,open=" + (chance() < 0.6) + "]");
            }
            return;
        }
        double r = chance();
        if (r < 0.06) {
            return;
        }
        if (post || r < 0.1) {
            f.set(x, 1, z, "minecraft:spruce_fence");
        } else {
            f.set(x, 1, z, rail.equals("barbed_wire") ? rail + "[axis=" + axis + "]" : rail);
        }
    }

    /**
     * A billboard on three timber legs facing the road: a frame of weathered boards, the paper
     * peeling off in sheets, the old ad still across it, a catwalk and two lamps that hang dead.
     */
    private void billboard(Placer f, int x0, int z) {
        int bw = 15, base = 6, top = base + 5;
        String id = any("motel", "joes", "route9", "hollowcreek", "riverside", "kessler", "bait", "produce");
        boolean torn = chance() < 0.4;
        for (int x = x0; x < x0 + bw; x++) {
            for (int y = base; y <= top; y++) {
                boolean frame = x == x0 || x == x0 + bw - 1 || y == base || y == top;
                if (frame) {
                    f.set(x, y, z + 1, chance() < 0.85 ? "minecraft:stripped_dark_oak_wood" : "minecraft:dark_oak_planks");
                    continue;
                }
                if (torn && x > x0 + bw / 2 && chance() < 0.55) {
                    f.set(x, y, z + 1, chance() < 0.5 ? "minecraft:spruce_planks" : "minecraft:air");
                    continue;  // the face blew off: boards and sky behind
                }
                double r = chance();
                f.set(x, y, z + 1, r < 0.42 ? "minecraft:white_concrete" : r < 0.62 ? "minecraft:light_gray_concrete"
                        : r < 0.74 ? "minecraft:white_terracotta" : r < 0.84 ? "minecraft:light_blue_terracotta"
                        : r < 0.92 ? "minecraft:red_terracotta" : "minecraft:smooth_quartz");
            }
            // the back: battens and the timber that holds it up
            if ((x - x0) % 2 == 0) {
                for (int y = base; y <= top; y++) {
                    f.set(x, y, z + 2, "minecraft:spruce_fence");
                }
            }
        }
        int k = Boards.WIDTH.getOrDefault(id, 4);
        int sx = x0 + (bw - k) / 2;
        for (int i = 0; i < k; i++) {
            f.set(sx + k - 1 - i, base + 3, z, "sign_" + id + "_" + i + "[facing=south]");
        }
        for (int leg : new int[] {x0 + 1, x0 + bw / 2, x0 + bw - 2}) {
            for (int y = 1; y < base; y++) {
                f.set(leg, y, z + 2, "pole_wood");
            }
            f.set(leg, 1, z + 3, "minecraft:spruce_fence");
        }
        // the catwalk along the foot, and its lamps on arms over the face
        for (int x = x0; x < x0 + bw; x++) {
            if (chance() < 0.85) {
                f.set(x, base - 1, z, "minecraft:spruce_trapdoor[half=top,facing=south]");
            }
        }
        for (int lx : new int[] {x0 + 3, x0 + bw - 4}) {
            f.set(lx, top + 1, z + 1, "minecraft:iron_chain[axis=y]");
            f.set(lx, top + 1, z, "minecraft:iron_bars");
            f.set(lx, top + 1, z - 1, "minecraft:iron_bars");
            f.set(lx, top, z - 1, chance() < 0.25 ? ShopLights.lamp(rng, 0) : "minecraft:smooth_quartz");
        }
        // weeds grown up round the legs
        for (int x = x0; x < x0 + bw; x++) {
            if (chance() < 0.3) {
                f.setIfAir(x, 1, z + 1, "minecraft:tall_dry_grass");
            }
        }
    }
}
