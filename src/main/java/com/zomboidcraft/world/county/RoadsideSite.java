package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.ShopLights;

/**
 * A stop on the county road, where a side lane comes in off to one end. Every one comes in
 * several builds, picked from the place's seed, and every one was hit when it all came down:
 * <ul>
 * <li>gas_station: a brand's canopy station with its food mart behind and a service bay beside it, an old VALLEY service
 * station with its bays and lift, or a country store with two pumps out front and bait for
 * sale (the stations and the pumps are {@link GasStation}'s).</li>
 * <li>motel: a single-storey L of rooms, a two-storey bar with a walkway and stairs, or a U round
 * a courtyard; the office, the tall neon sign, the drained pool, a lamp over every door.</li>
 * <li>diner: a lunch car with its kitchen built on behind, the brick cafe, or the truck stop
 * with its store, showers and diesel canopy ({@link Diner}'s).</li>
 * <li>church: a chapel, a brick church or a stone one, its graveyard and its gravel lot.</li>
 * <li>school: an elementary with its playground and court, or a high school with a football
 * field, bleachers and lights; the bus lot, and on some the evacuation centre it became.</li>
 * <li>warehouse: a storage depot, a feed and farm supply, or a lumber yard; docks, trailers
 * backed in, racks inside, a fenced yard.</li>
 * <li>trailer_lot: a few single-wides on gravel pads round a lane, junk and propane tanks.</li>
 * <li>truck_depot: I-88 Truck &amp; Diesel ({@link AutoYards}), the truck hall with its pit, column
 * lifts and van hoist, the diesel canopy, the trucks waiting out back.</li>
 * </ul>
 */
final class RoadsideSite extends Site {
    private static final String CARS = "sedan,hatchback,pickup,suv,minivan,wagon,coupe,van";

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
            case "truck_depot" -> truckDepot();
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
            q.set(x - 1, 1, z + 1, "fm_drum");
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
        GasStation gs = new GasStation(q, rng, hit);
        switch (style) {
            case 1 -> {
                gs.modern(lot.w(), lot.d());
                // the bigger stations' service bay, east of the mart on the forecourt (GasStation.modern's layout)
                int sw = Math.max(14, Math.min(22, lot.w() - 12)), sx = lot.w() / 2 - sw / 2 + sw + 1;
                int sd = Math.max(9, Math.min(12, lot.d() - 20)), storeZ = lot.d() - sd - 1;
                if (sx + 8 <= lot.w() - 2 && storeZ + 12 <= lot.d()) {
                    new AutoShop(q, rng, hit).sideBay(sx, storeZ, "sh_cmu_tan");
                }
            }
            case 0 -> gs.valley(lot.w(), lot.d());
            default -> countryStore(q, gs, lot.w(), lot.d());
        }
    }

    /** I-88 Truck &amp; Diesel ({@link AutoYards#truckDepot}): the truck hall, the diesel canopy, the lot out back. */
    private void truckDepot() {
        Kit.Lot lot = business(70, 60);
        if (lot != null) {
            new AutoYards(lot.placer(out), rng, hit).truckDepot(lot.w(), lot.d());
        }
    }

    /** The country store with two pumps on the gravel out front, ice and propane by the porch, bait for sale. */
    private void countryStore(Placer q, GasStation gs, int lw, int ld) {
        int sw = 14 + rng.nextInt(3), sd = Math.min(16, ld - 11);
        int sx = (lw - sw) / 2, sz = 9;
        gravel(q, 1, 0, lw - 2, sz - 1);
        Storefront.Style st = chance() < 0.6 ? Storefront.Style.FALSE_FRONT : Storefront.Style.VICTORIAN;
        new Storefront(q.shifted(sx, 0, sz), rng, sw, sd, 1 + (chance() < 0.3 ? 1 : 0), st, Storefront.Trade.GENERAL).build();
        int px = sx + sw / 2 - 2;
        gs.storePumps(px, 4);
        // the ice box past the porch's end, the propane cage at the other, a paper box by the steps
        for (int i = 0; i < 4; i++) {
            q.set(sx + sw + 2 - i % 2, 2 - i / 2, sz - 1, "gs_ice_" + i + "[facing=north]");
        }
        q.set(sx - 3, 1, sz - 1, "gs_propane_l[facing=north]");
        q.set(sx - 2, 1, sz - 1, "gs_propane_r[facing=north]");
        q.set(px + 5, 1, sz - 3, "gs_news_" + rng.nextInt(3) + "[facing=north]");
        q.set(sx + sw + 1, 2, sz + 3, "gs_payphone[facing=east]");
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
                q.set(x, 8, bz - 1, "front_coping_metal");
                for (int z = bz; z < bz + 10; z++) {
                    q.set(x, 8, z, Bldg.full(roof));
                }
            }
            // the stair up at the end, two wide in line with the walkway, climbing onto a landing at its end
            for (int k = 0; k < 4; k++) {
                for (int z = bz - 2; z <= bz - 1; z++) {
                    q.set(bx1 + 6 - k, 1 + k, z, "steel_stairs[facing=west]");
                    for (int r = 1; r <= 3; r++) {
                        q.set(bx1 + 6 - k, 1 + k + r, z, "air");
                    }
                }
                q.set(bx1 + 6 - k, 2 + k, bz - 2, "ext_rail_edge_dark[facing=north]");
                q.set(bx1 + 6 - k, 2 + k, bz - 1, "ext_rail_edge_dark[facing=south]");
            }
            for (int x = bx1 + 1; x <= bx1 + 2; x++) {
                q.set(x, 4, bz - 2, "deck_steel");
                q.set(x, 4, bz - 1, "deck_steel");
                q.set(x, 5, bz - 2, "railing[facing=north]");
            }
            // and from the landing on up beside the rooms to a hut on the roof, its doors out onto it
            RoofHut.flight(q.turned(bx1 + 2, 0, bz - 1, 1), rng, 0, 0, 4, 8, wall, "steel_stairs", "south",
                    "ext_rail_edge_dark");
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
            // the wing runs from the walkway's end toward the road, its doors to the courtyard
            // (drawn from bz - 3 back it stood beside the row, its doors against the end room's wall)
            Placer side = q.turned(x0 - 1, 0, bz - 2 - wing * 5, 1);
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
        r.set(3, 2, 0, chance() < 0.3 + hit * 0.4 ? "window_broken[axis=x]" : chance() < 0.2 ? "boarded_window[axis=x]" : "window[axis=x]");
        String bed = any("home_bed_quilt", "home_bed_blue", "home_bed_rose", "home_bed_quilt", "home_bed_bloody");
        r.set(2, 1, 4, bed + "_head[facing=south]");
        r.set(2, 1, 5, bed + "_foot[facing=south]");
        r.set(3, 1, 4, "pk_nightstand[facing=west]");
        r.set(1, 1, 6, "home_dresser[facing=east]");
        r.set(1, 2, 6, chance() < 0.7 ? "pk_tv_crt[facing=east]" : "air");
        for (int x = 1; x <= 3; x++) {
            r.set(x, 1, 7, x == 2 ? "air" : "drywall");
            r.set(x, 2, 7, x == 2 ? "air" : "drywall");
            r.set(x, 3, 7, "drywall");
        }
        r.set(1, 1, 8, "home_toilet[facing=east]");
        r.set(3, 1, 8, "store_spill");
        if (chance() < 0.35) {
            r.set(2, 0, 4, "blood_splat");
        }
        if (chance() < 0.55) {
            r.set(2, 3, -1, ShopLights.lamp(rng, 0.25 + hit * 0.2));
        }
        if (chance() < 0.3) {
            r.setIfAir(1 + rng.nextInt(3), 1, 1 + rng.nextInt(3), "cardboard_box");
        }
    }

    /** The covered walkway along the rooms: a slab roof on posts, at floor height y. */
    private void walkway(Placer q, int x0, int x1, int z, int y) {
        for (int x = x0; x <= x1; x++) {
            q.set(x, y, z, y == 0 ? "sidewalk" : "deck_steel");
            q.set(x, y, z + 1, y == 0 ? "sidewalk" : "deck_steel");
            if (y > 0) {
                q.set(x, y + 1, z, "railing[facing=north]");
            }
            if ((x - x0) % 5 == 0) {
                for (int k = 1; k <= 3; k++) {
                    q.set(x, y + k, z, "streetlight_pole");
                }
            }
            q.set(x, y + 4, z, "front_coping_metal");
            q.set(x, y + 4, z + 1, "front_coping_metal");
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
                    q.set(x + i, 0, z + j, "of_coping");
                    continue;
                }
                int depth = i < pw / 2 ? 2 : 3;
                for (int k = 0; k < depth; k++) {
                    q.set(x + i, -k, z + j, "air");
                }
                q.set(x + i, -depth, z + j, "of_pool_tile");
                double r = chance();
                if (depth == 3 && r < 0.35) {
                    q.set(x + i, -2, z + j, r < 0.15 ? "leaf_litter" : r < 0.25 ? "trash_bag[facing=north]" : "minecraft:water");
                }
            }
        }
        for (int j = 1; j < pd - 1; j++) {
            for (int k = 1; k <= 3; k++) {
                q.set(x, -k, z + j, "of_pool_tile");
                q.set(x + pw - 1, -k, z + j, "of_pool_tile");
            }
        }
        q.set(x + 1, -1, z + 1, "ladder[facing=east]");
        q.set(x + 1, 1, z - 2, "store_chair[facing=south]");
        q.set(x + 4, 1, z - 2, "store_chair[facing=south]");
    }

    // ------------------------------------------------------------------ the diner

    /** A lunch car, the brick cafe or the truck stop (see {@link Diner}). */
    private void diner() {
        int style = rng.nextInt(3);
        Kit.Lot lot = switch (style) {
            case 2 -> business(64, 46);
            case 1 -> business(40, 36);
            default -> business(40, 34);
        };
        if (lot == null) {
            return;
        }
        Placer q = lot.placer(out);
        Diner dn = new Diner(q, rng, hit);
        switch (style) {
            case 2 -> {
                dn.truckStop(lot.w(), lot.d());
                pylon(q, lot.w() - 7, 1, "route9", 9);
            }
            case 1 -> dn.cafe(lot.w(), lot.d());
            default -> dn.railcar(lot.w(), lot.d());
        }
        if (hit > 0.65) {
            scorch(q, lot.w() / 2, 3, 2);
            q.vehicle(lot.w() / 2.0, 1, 3, 90, "police_sedan,sedan", rng.nextLong());
        }
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
        new Civic(q, rng).church(kind, size[0], Math.min(size[1], lot.d()),
                kind == Civic.Church.CHAPEL ? "church" : kind == Civic.Church.BRICK ? "methodist" : "stmary");
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
        new Civic(q.shifted(2, 0, 0), rng).school(sw, sd, high, hit > 0.4);
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
        int backZ = sd + 7;  // the bleachers clear of the back wall
        if (high && ld - backZ > 30) {
            football(q, 4, backZ, Math.min(lw - 8, 60), ld - backZ - 2);
        } else if (ld - backZ > 12) {
            int[] a = q.world(6, backZ + 2), b = q.world(12, backZ + 8), c = q.world(18, backZ + 2), e = q.world(26, backZ + 8);
            kit.playground(Math.min(a[0], b[0]), Math.min(a[1], b[1]));
            kit.court(Math.min(c[0], e[0]), Math.min(c[1], e[1]));
        }
        if (hit > 0.4) {
            evacuation(q, sw / 2 - 10, 2);
        }
    }

    /** The football field: lines, goalposts, bleachers down one side, light towers at the corners. */
    private void football(Placer q, int x0, int z0, int fw, int fd) {
        for (int x = x0; x < x0 + fw; x++) {
            for (int z = z0; z < z0 + fd; z++) {
                boolean side = z == z0 || z == z0 + fd - 1, yard = x == x0 || x == x0 + fw - 1 || (x - x0) % 6 == 0;
                // the mown bands growing out: rough in drifts, mud worn bare in the middle, weeds coming up
                double wild = Math.sin(x * 0.31 + z * 0.17) + Math.sin(z * 0.43 - x * 0.12);
                boolean rough = wild + chance() * 0.8 > 1.1;
                String turf = rough ? "sc_turf_rough" : chance() < 0.1 ? "sc_turf_mud" : (x - x0) / 3 % 2 == 0 ? "sc_turf" : "sc_turf_d";
                q.set(x, 0, z, rough && chance() < 0.5 ? turf : side ? "sc_turf_line[axis=x]" : yard ? "sc_turf_line[axis=z]" : turf);
                double r = chance();
                if (rough && r < 0.25) {
                    int k = 1 + rng.nextInt(3);
                    String dry = chance() < 0.5 ? "dry_" : "";
                    q.set(x, 1, z, "gc_tall_" + dry + k + "_lower");
                    q.set(x, 2, z, "gc_tall_" + dry + k + "_upper");
                } else if (rough && r < 0.8 || r < 0.08) {
                    q.set(x, 1, z, chance() < 0.4 ? "gc_grass_dry_" + (1 + rng.nextInt(6)) : "gc_grass_" + (1 + rng.nextInt(12)));
                } else {
                    q.set(x, 1, z, "air");
                }
            }
        }
        int mid = z0 + fd / 2;
        // the goalposts: the padded post, the crossbar, the uprights with their ribbons
        for (int x : new int[] {x0 + 1, x0 + fw - 2}) {
            q.set(x, 1, mid, "sc_goal_pad");
            q.set(x, 2, mid, "sc_goal_post");
            q.set(x, 3, mid, "sc_goal_mid[axis=z]");
            q.set(x, 3, mid - 1, "sc_goal_bar[axis=z]");
            q.set(x, 3, mid + 1, "sc_goal_bar[axis=z]");
            q.set(x, 3, mid - 2, "sc_goal_end[facing=north]");
            q.set(x, 3, mid + 2, "sc_goal_end[facing=south]");
            for (int z : new int[] {mid - 2, mid + 2}) {
                for (int y = 4; y <= 6; y++) {
                    q.set(x, y, z, "sc_goal_post");
                }
                q.set(x, 7, z, "sc_goal_top");
            }
        }
        // bleachers: aluminium benches stepping up, on the side nearest the school
        for (int x = x0 + 6; x < x0 + fw - 6; x++) {
            for (int k = 0; k < 4; k++) {
                q.set(x, 1 + k, z0 - 2 - k, chance() < 0.06 ? "air" : "sc_bleacher_alu[facing=south]");
                for (int y = 1; y <= k; y++) {
                    q.set(x, y, z0 - 2 - k, "sc_bleacher_frame[facing=south]");
                }
            }
        }
        // the scoreboard on its legs past the east end zone
        int sx = x0 + fw + 2;
        for (int y = 1; y <= 3; y++) {
            q.set(sx, y, mid - 1, "truss_grey");
            q.set(sx, y, mid + 1, "truss_grey");
        }
        for (int i = 0; i < 3; i++) {
            q.set(sx, 4, mid - 1 + i, "sc_score_" + i + "[facing=west]");
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
        // two shelter tents off the Tents kit, the cots inside, the lawn trodden round them
        Tents t = new Tents(q, rng);
        t.hit = hit;
        t.lit = 0.05;
        for (int k = 0; k < 2; k++) {
            t.tent(x + 2 + k * 9, z + 2, 6, 7, Tents.canvasFor("shelter"), "shelter", 0.2);
        }
        q.set(x + 19, 1, z + 3, "sign_quarantine_0[facing=south]");
        // parked along the loop west of the tents, clear of them and the barrier
        q.vehicle(x - 4, 1, z + 5, 270, "ambulance,ambulance_van,police_suv", rng.nextLong());
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
            // a step up onto the dock beside the bumper, and down off the door's sill inside
            s.set(x - 1, 1, -1, "steel_stairs[facing=east]");
            if ("air".equals(s.get(x + 1, 2, 0))) {
                s.set(x + 1, 1, 1, "steel_stairs[facing=north]");
            }
        }
        // the walk-in door beside the first dock, at the floor's own level
        b.door(1, 1, 0, "fire_door", "north");
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
            b.flat(0, 0, bw - 1, bd - 1, 8, clad, 1, "front_coping_metal");
        }
        // racks of crates and boxes inside, aisles between
        for (int x = 3; x < bw - 3; x += 4) {
            for (int z = 4; z < bd - 2; z++) {
                for (int y = 1; y <= 3; y++) {
                    double r = chance();
                    s.set(x, y, z, r < 0.35 ? "crate" : r < 0.6 ? "cardboard_box" : r < 0.7 ? "fm_drum" : "air");
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
        // and through to the warehouse floor
        o.set(9, 1, 2, "air");
        o.set(9, 2, 2, "air");
        b.door(0, 1, 2, "fire_door", "west");
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
                q.set(14 + i, 1, ld - 5, "fm_bales[axis=x]");
                q.set(14 + i, 2, ld - 5, chance() < 0.6 ? "fm_bale_one[facing=" + any("north", "east", "south", "west") + "]" : "air");
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
        for (Kit.Lot lot : kit.lotsAlong(spur, 21, 24, 19, 1, 9, spur.length() + 4, 0)) {
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
        if (bizSide != 0) {
            side = -9 * bizSide;    // down the far verge, so no pole stands in the forecourt or a bay's way
        }
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
        fieldFence(f, fw, fd, corn ? "fm_rail" : "barbed_wire");
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
                Doors.place(f, x, 1, z, "fm_gate", "north", x % 2 == 0 ? "left" : "right", chance() < 0.6);
            }
            return;
        }
        double r = chance();
        if (r < 0.06) {
            return;
        }
        boolean wire = rail.equals("barbed_wire");
        if (post || r < 0.1) {
            f.set(x, 1, z, wire ? "fm_post" : "fm_rail_post[axis=" + axis + "]");
        } else {
            f.set(x, 1, z, (wire || r > 0.16 ? rail : "fm_rail_broken") + "[axis=" + axis + "]");
        }
    }

    /**
     * A billboard on three timber legs facing the road (rs_ kit, tools/city/roadside.py): a timber
     * frame, the poster sheets peeling off the ply, the old ad still across it, battens and kickers
     * behind, a grating catwalk and gooseneck lamps, most of them dead.
     */
    private void billboard(Placer f, int x0, int z) {
        int bw = 15, base = 6, top = base + 5;
        String id = any("motel", "joes", "route9", "hollowcreek", "riverside", "kessler", "bait", "produce");
        boolean torn = chance() < 0.4;
        String ground = any("a", "b", "c", "d", "e", "e");
        int[] legX = {x0 + 1, x0 + bw / 2, x0 + bw - 2};
        for (int x = x0; x < x0 + bw; x++) {
            for (int y = base; y <= top; y++) {
                boolean frame = x == x0 || x == x0 + bw - 1 || y == base || y == top;
                if (frame) {
                    f.set(x, y, z + 1, "rs_frame[facing=north]");
                    continue;
                }
                if (torn && x > x0 + bw / 2 && chance() < 0.55) {
                    f.set(x, y, z + 1, chance() < 0.5 ? "rs_ply" : "air");
                    continue;  // the face blew off: boards and sky behind
                }
                // one ad's sheets over the whole face, the odd one gone to the ad pasted under it
                String sheet = (chance() < 0.9 ? ground : any("a", "b", "c", "d", "e")) + rng.nextInt(3);
                f.set(x, y, z + 1, chance() < 0.08 ? "rs_paper_curl_" + (chance() < 0.5 ? "a" : "b") + "[facing=north]"
                        : "rs_paper_" + sheet + "[facing=north]");
            }
            // the back: an upright every other block, the rails between, the legs running up to the top
            boolean leg = x == legX[0] || x == legX[1] || x == legX[2];
            for (int y = base; y <= top; y++) {
                f.set(x, y, z + 2, leg ? "pole_wood" : (x - x0) % 2 == 0 ? "rs_batten[facing=north]" : "rs_stringer[facing=north]");
            }
        }
        int k = Boards.WIDTH.getOrDefault(id, 4);
        int sx = x0 + (bw - k) / 2;
        for (int i = 0; i < k; i++) {
            f.set(sx + k - 1 - i, base + 3, z, "sign_" + id + "_" + i + "[facing=south]");
        }
        for (int leg : legX) {
            for (int y = 1; y < base; y++) {
                f.set(leg, y, z + 2, "pole_wood");
            }
            f.set(leg, 1, z + 3, "rs_brace[facing=north]");
        }
        // the catwalk along the foot, and its gooseneck lamps out over the road turned back on the face
        for (int x = x0; x < x0 + bw; x++) {
            if (chance() < 0.92) {
                f.set(x, base - 1, z, "rs_walk[facing=north]");
            }
        }
        for (int lx : new int[] {x0 + 3, x0 + bw / 2 + 1, x0 + bw - 4}) {
            if (chance() < 0.85) {
                f.set(lx, base - 1, z - 1, chance() < 0.25 ? "rs_lamp_lit[facing=north]" : "rs_lamp[facing=north]");
            }
        }
        // weeds grown up round the legs
        for (int x = x0; x < x0 + bw; x++) {
            if (chance() < 0.3) {
                f.setIfAir(x, 1, z + 1, "minecraft:tall_dry_grass");
            }
        }
    }
}
