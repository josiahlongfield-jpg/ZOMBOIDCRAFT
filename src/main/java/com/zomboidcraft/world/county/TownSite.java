package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Decay;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A county town, in four kinds:
 * <ul>
 * <li>hamlet: a crossroads of country lanes with a general store and its old pumps, a white
 * chapel and its graveyard, a grain elevator, a post office, a volunteer fire house and a
 * scatter of farmhouses and trailers;</li>
 * <li>main_street: a street of two- and three-storey brick shops with a GROCERY, a bank, the
 * picture house and a hotel, ending at a church on its green where the road forks round it;
 * the water tower over the back alleys, the fire station, the sheriff, a school and its buses,
 * and streets of old houses behind;</li>
 * <li>county_seat: the courthouse on its square with the war memorial and a bandstand, shops
 * facing it on all four sides, churches on the corners, a library, the high school and its
 * football field, and a grid of streets beyond;</li>
 * <li>mill_town: the textile mill and its smokestack beside the railroad, the depot, the
 * company store and a short main street facing the mill, rows of identical mill houses.</li>
 * </ul>
 * Every main street was where people made their stand: wrecks burning in the road, a school
 * bus slewed across it, sandbags at the crossings. Its buildings are ruined by the city's Decay
 * pass: smashed glass, shutters down, blown-out walls, ivy and graffiti.
 */
final class TownSite extends Site {
    private Kit kit;
    private Homes homes;
    /** Boxes {x0, z0, x1, z1} the Decay pass runs over: the commercial streets. */
    private final List<int[]> core = new ArrayList<>();
    /** The public buildings a civil-defence shelter could be dug under. */
    private final List<Host> hosts = new ArrayList<>();

    /** A building's frame, the room inside its walls on the ground floor {x0, z0, x1, z1}, and its front wall's z. */
    private record Host(Placer p, int[] interior, int front, int floor) {}

    TownSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        homes = new Homes(kit);
        switch (p.variant) {
            case "hamlet" -> hamlet();
            case "county_seat" -> countySeat();
            case "mill_town" -> millTown();
            default -> mainStreet();
        }
        for (int[] c : core) {
            new Decay(out, rng.nextLong(), Math.max(0, c[0]), Math.min(w - 1, c[2]), Math.max(0, c[1]), Math.min(d - 1, c[3]), 30).run();
        }
        kit.roadWeeds(0.2);
        shelter();
    }

    /** A town hall or post office (Civic.hall) as a shelter host: its walls at x 0..w - 1, z 3..d - 1. */
    private void hall(Placer pl, int hw, int hd) {
        hosts.add(new Host(pl, new int[] {1, 4, hw - 2, hd - 2}, 3, 0));
    }

    /**
     * About one town in three dug a fallout shelter under its courthouse, town hall or post
     * office; the yellow-and-black plate on the front wall still says so. Rolls its own dice, so
     * the town above comes out as it always did.
     */
    private void shelter() {
        SplittableRandom g = new SplittableRandom(Noise.hash(p.seed, 0x5E17, 0x7E12));
        if (hosts.isEmpty() || g.nextDouble() > (p.variant.equals("county_seat") ? 0.55 : 0.3)) {
            return;
        }
        double r = g.nextDouble();
        Bunker.State state = r < 0.45 ? Bunker.State.SEALED : r < 0.7 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int first = g.nextInt(hosts.size());
        for (int i = 0; i < hosts.size(); i++) {
            Host h = hosts.get((first + i) % hosts.size());
            if (Shelter.dig(h.p(), g, state, h.interior(), h.floor(), -18 + h.floor(), new int[] {2, 2, w - 3, d - 3})) {
                Shelter.plate(h.p(), h.interior()[0], h.interior()[2], h.front());
                return;
            }
        }
    }

    private double jitter(double amount) {
        return (rng.nextDouble() * 2 - 1) * amount;
    }

    // ------------------------------------------------------------------ shared pieces

    /** How far along a lane its nearest point to (x, z) lies. */
    private static double along(Kit.Lane l, double x, double z) {
        double best = 0, bd = 1e18;
        for (double s = 0; s <= l.length(); s += 1) {
            double[] q = l.point(s);
            double dd = Math.hypot(q[0] - x, q[1] - z);
            if (dd < bd) {
                bd = dd;
                best = s;
            }
        }
        return best;
    }

    /** A lot lw wide and depth deep fronting a lane at s, on one side (+1 left, -1 right); claimed, or null. */
    private Kit.Lot civicLot(Kit.Lane l, double s, int side, int lw, int depth) {
        double[] q = l.point(s);
        double nx = -q[3] * side, nz = q[2] * side;
        Kit.Lot lot = kit.fit(q[0], q[1], nx, nz, lw, depth);
        if (lot != null) {
            kit.lots.add(lot);
        }
        return lot;
    }

    /** Lawn and weeds over whatever part of a lot nothing was built on. */
    private void yard(Kit.Lot lot, double wild) {
        Kit.lawn(lot.placer(out), rng, 0, 0, lot.w() - 1, lot.d() - 1, wild);
    }

    /**
     * Shopfronts shoulder to shoulder along a lane from s0 to s1 on one side (0: both), each its
     * own building; the trades listed go in first (where the lot is wide enough), then a mix.
     */
    private List<Kit.Lot> shops(Kit.Lane l, double s0, double s1, int side, int depth, int maxFloors, List<Storefront.Trade> wanted) {
        List<Kit.Lot> lots = kit.lotsAlong(l, 8, 13, depth, 0, s0, s1, side);
        for (Kit.Lot lot : lots) {
            Storefront.Trade t = Storefront.trade(rng);
            if (!wanted.isEmpty() && lot.w() >= 10) {
                t = wanted.remove(0);
            }
            int bd = Math.min(20, lot.d() - 4);
            Placer pl = lot.placer(out);
            int floors = Math.min(maxFloors, rng.nextDouble() < 0.25 ? 1 : rng.nextDouble() < 0.65 ? 2 : 3);
            if (t == Storefront.Trade.HOTEL) {
                floors = maxFloors;
            }
            Storefront.Style style = Storefront.style(rng);
            if (t == Storefront.Trade.HOTEL && (style == Storefront.Style.MODERN || style == Storefront.Style.FALSE_FRONT)) {
                style = Storefront.Style.VICTORIAN;
            }
            Storefront s = new Storefront(pl, rng, lot.w(), bd, floors, style, t);
            s.yard = lot.d() - bd;
            int[] west = pl.world(-1, 3), east = pl.world(lot.w(), 3);
            s.openWest = kit.use(west[0], west[1]) != Kit.LOT;
            s.openEast = kit.use(east[0], east[1]) != Kit.LOT;
            s.build();
        }
        return lots;
    }

    /** An iron lamp post with a lantern head, the small-town kind. */
    private void townLamp(int x, int z, boolean lit) {
        out.set(x, 1, z, "lamp_base");
        for (int y = 2; y <= 4; y++) {
            out.set(x, y, z, "lamp_post");
        }
        out.set(x, 5, z, lit ? "lamp_head" : "lamp_head_off");
    }

    /** Lamp posts down both sidewalks of a lane, alternating sides. */
    private void townLamps(Kit.Lane l, int spacing, double lit) {
        int side = 1;
        for (double s = spacing / 2.0; s < l.length() - 3; s += spacing / 2.0) {
            double[] q = l.point(s);
            double nx = -q[3] * side, nz = q[2] * side;
            double o = l.style.half + 1.7;
            int x = (int) Math.floor(q[0] + nx * o), z = (int) Math.floor(q[1] + nz * o);
            side = -side;
            if (kit.use(x, z) == Kit.WALK && out.get(x, 1, z) == null) {
                townLamp(x, z, rng.nextDouble() < lit);
            }
        }
    }

    /** The facing (north, east...) nearest to a direction. */
    private static String facing(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    /** Hydrants, benches, bins, planters and dumped rubbish along a street's sidewalks. */
    private void furniture(Kit.Lane l, double s0, double s1) {
        for (int side = -1; side <= 1; side += 2) {
            for (double s = s0 + rng.nextInt(5); s < s1; s += 5 + rng.nextInt(6)) {
                double[] q = l.point(s);
                double nx = -q[3] * side, nz = q[2] * side;
                double o = l.style.half + 1.7;
                int x = (int) Math.floor(q[0] + nx * o), z = (int) Math.floor(q[1] + nz * o);
                if (kit.use(x, z) != Kit.WALK || out.get(x, 1, z) != null) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < 0.18) {
                    out.set(x, 1, z, "minecraft:red_nether_brick_wall");  // a hydrant
                } else if (r < 0.4) {
                    out.set(x, 1, z, "minecraft:dark_oak_stairs[facing=" + facing(-nx, -nz) + "]");  // a bench
                } else if (r < 0.55) {
                    out.set(x, 1, z, "minecraft:cauldron");  // a bin
                } else if (r < 0.68) {
                    out.set(x, 1, z, "planter");
                } else if (r < 0.8) {
                    out.set(x, 1, z, "trash_bag[facing=" + facing(nx, nz) + "]");
                } else if (r < 0.88) {
                    out.set(x, 1, z, "fence_post");  // a parking meter
                    out.set(x, 2, z, "minecraft:polished_blackstone_button[face=floor,facing=north]");
                }
            }
        }
    }

    /** Cars nosed in to the curb at an angle, the small-town way, some spaces empty. */
    private void diagonalParking(Kit.Lane l, double s0, double s1, double fill) {
        for (int side = -1; side <= 1; side += 2) {
            for (double s = s0; s < s1; s += 3.4) {
                if (rng.nextDouble() > fill) {
                    continue;
                }
                double[] q = l.point(s);
                double nx = -q[3] * side, nz = q[2] * side;
                double o = l.style.half - 2.3;
                double x = q[0] + nx * o, z = q[1] + nz * o;
                double hx = nx * 0.72 + q[2] * 0.7 * side, hz = nz * 0.72 + q[3] * 0.7 * side;
                kit.car(x, z, Kit.yaw(hx, hz) + (float) jitter(6), 0, 5.6);
            }
        }
    }

    /** A wreck burning in the road: the car, fire, scorched asphalt, rubble thrown about. */
    private void wreck(double x, double z, boolean school) {
        if (school) {
            out.vehicle(x, 1, z, (float) (rng.nextDouble() * 360), "school_bus,city_bus", rng.nextLong());
        } else {
            kit.car(x, z, (float) (rng.nextDouble() * 360), 0, 5.6);
        }
        int cx = (int) x, cz = (int) z;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double r = Math.hypot(dx, dz) + rng.nextDouble() * 1.5;
                if (!inside(cx + dx, cz + dz) || r > 4.5 || out.get(cx + dx, 1, cz + dz) != null) {
                    continue;
                }
                double k = rng.nextDouble();
                if (k < 0.45) {
                    out.set(cx + dx, 1, cz + dz, "scorch");
                } else if (k < 0.52) {
                    out.set(cx + dx, 1, cz + dz, "rubble_pile");
                } else if (k < 0.58) {
                    out.set(cx + dx, 1, cz + dz, "glass_shards");
                }
            }
        }
        int fires = 1 + rng.nextInt(2);
        for (int i = 0; i < fires; i++) {
            int fx = cx + (rng.nextBoolean() ? 3 : -3) + rng.nextInt(2), fz = cz + rng.nextInt(5) - 2;
            if (inside(fx, fz)) {
                out.set(fx, 1, fz, "minecraft:campfire[lit=true]");
            }
        }
    }

    /** Sandbags and barriers across a street at s, with the gap they left to drive through. */
    private void barricade(Kit.Lane l, double s) {
        double[] q = l.point(s);
        double reach = l.style.half + 1;
        int gap = rng.nextInt((int) (reach * 2));
        for (int i = 0; i <= reach * 2; i++) {
            double o = -reach + i;
            int x = (int) Math.floor(q[0] - q[3] * o), z = (int) Math.floor(q[1] + q[2] * o);
            if (Math.abs(i - gap) < 2 || !inside(x, z)) {
                continue;
            }
            String f = facing(q[2], q[3]);
            out.set(x, 1, z, i % 3 == 0 ? "jersey_barrier[facing=" + f + "]" : "sandbags[facing=" + f + "]");
            if (i % 3 == 1 && rng.nextBoolean()) {
                out.set(x, 2, z, "sandbags[facing=" + f + "]");
            }
        }
        for (int i = 0; i < 6; i++) {
            int x = (int) (q[0] + jitter(reach)), z = (int) (q[1] + jitter(reach));
            if (inside(x, z) && out.get(x, 1, z) == null) {
                out.set(x, 1, z, rng.nextBoolean() ? "blood_splat" : "debris_chunk[facing=north]");
            }
        }
    }

    /** A stop sign at a minor street's mouth, facing the traffic coming down it. */
    private void stopSign(Kit.Lane l, boolean atEnd) {
        double s = atEnd ? l.length() - l.style.reach() - 3 : l.style.reach() + 3;
        double[] q = l.point(Math.max(0, Math.min(l.length(), s)));
        double o = l.style.half + 1.6;
        int x = (int) Math.floor(q[0] - q[3] * o), z = (int) Math.floor(q[1] + q[2] * o);
        if (!inside(x, z) || out.get(x, 1, z) != null) {
            return;
        }
        double tx = atEnd ? -q[2] : q[2], tz = atEnd ? -q[3] : q[3];
        out.set(x, 1, z, "sign_post");
        out.set(x, 2, z, rng.nextDouble() < 0.1 ? "sign_post" : "stop_sign[facing=" + facing(tx, tz) + "]");
    }

    /** Quiet residential streets of older houses. */
    private void houses(List<Kit.Lane> streets, Homes.Mix mix, int minW, int maxW, int depth) {
        for (Kit.Lane l : streets) {
            for (Kit.Lot lot : kit.lotsAlong(l, minW, maxW, depth, 1, 4, l.length() - 4, 0)) {
                homes.home(lot, mix);
            }
        }
        for (Kit.Lane l : streets) {
            kit.streetLights(l, 32, 6, 0.4);
            kit.streetTrees(l, 9, 0.8);
            kit.abandonedCars(l, 2);
        }
    }

    /** A lane-side fire house for the volunteers: tin walls, a red door, the siren on a pole. */
    private void fireShed(Kit.Lot lot) {
        Placer pl = lot.placer(out);
        Bldg b = new Bldg(pl, rng);
        int sw = Math.min(lot.w(), 11), sd = Math.min(lot.d() - 4, 15);
        b.shell(0, 4, sw - 1, sd + 3, 1, 6, "corrugated_red", "floor_concrete", "floor_concrete");
        for (int x = 2; x <= 6; x++) {
            for (int y = 1; y <= 4; y++) {
                pl.set(x, y, 4, "garage_door_red[facing=north]");
            }
        }
        b.door(sw - 2, 1, 4, "fire_door", "north");
        b.gable(0, sw - 1, 4, sd + 3, 7, "minecraft:waxed_weathered_cut_copper", true, "corrugated_red", 1);
        Boards.fascia(pl, rng, "firedept", 1, 5, 3, sw - 2);
        pl.vehicle(4.5, 1, 10, 0, "fire_truck,pickup,tow_truck", rng.nextLong());
        for (int y = 1; y <= 9; y++) {
            pl.set(sw + 1, y, 4, "pole_wood");
        }
        pl.set(sw + 1, 10, 4, "hvac");
        Kit.lawn(pl, rng, 0, 0, lot.w() - 1, lot.d() - 1, 0.8);
        for (int x = 0; x < sw; x++) {
            for (int z = 0; z < 4; z++) {
                pl.set(x, 0, z, "minecraft:gravel");
            }
        }
    }

    /** Old gas pumps on a concrete island in front of a country store. */
    private void pumps(Placer pl, int x, int z) {
        for (int i = -1; i <= 3; i++) {
            pl.set(x + i, 0, z, "concrete_dark");
            pl.set(x + i, 0, z + 1, "concrete_dark");
        }
        pl.set(x, 1, z, "gas_pump[facing=north]");
        pl.set(x + 2, 1, z, "gas_pump[facing=north]");
        pl.set(x + 1, 1, z + 1, "minecraft:barrel[facing=up,loot=hardware]");
    }

    // ------------------------------------------------------------------ the hamlet

    private void hamlet() {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        double cx = N[0], cz = W[1];
        Kit.Lane we = kit.curve(Kit.Style.LANE, W[0], W[1], w * 0.25, cz + jitter(6), cx - 12, cz, cx + 12, cz,
                w * 0.75, cz + jitter(6), E[0], E[1]);
        Kit.Lane ns = kit.curve(Kit.Style.LANE, N[0], N[1], cx + jitter(6), d * 0.25, cx, cz - 12, cx, cz + 12,
                cx + jitter(6), d * 0.75, S[0], S[1]);
        kit.pave();
        double sWe = along(we, cx, cz), sNs = along(ns, cx, cz);
        // the four corners of the crossroads, dealt out: the store, the chapel, the elevator, the post office
        // (quadrants 0 NW, 1 NE, 2 SW, 3 SE; on the west-east lane +1 is south, on the north-south lane +1 is west)
        List<Integer> quads = new ArrayList<>(List.of(0, 1, 2, 3));
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            quads.set(i, quads.set(j, quads.get(i)));
        }
        int qStore = quads.get(0), qChapel = quads.get(1), qElevator = quads.get(2), qPost = quads.get(3);
        // the general store on its corner, its pumps out front
        Kit.Lot store = civicLot(we, sWe + ((qStore & 1) == 0 ? -16 : 16), qStore >= 2 ? 1 : -1, 13, 30);
        if (store != null) {
            Placer pl = store.placer(out);
            Storefront s = new Storefront(pl.shifted(0, 0, 8), rng, store.w(), 16, 1 + rng.nextInt(2),
                    rng.nextBoolean() ? Storefront.Style.FALSE_FRONT : Storefront.Style.ITALIANATE, Storefront.Trade.GENERAL);
            s.yard = store.d() - 24;
            s.openWest = s.openEast = true;
            s.build();
            for (int x = 0; x < store.w(); x++) {
                for (int z = 0; z < 8; z++) {
                    if (pl.get(x, 0, z) == null) {
                        pl.set(x, 0, z, rng.nextDouble() < 0.7 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                    }
                }
            }
            pumps(pl, store.w() / 2 - 1, 2);
            yard(store, 0.9);
        }
        // the white chapel and its graveyard on the north-south lane
        Kit.Lot chapel = civicLot(ns, sNs + (qChapel < 2 ? -23 : 23), (qChapel & 1) == 0 ? 1 : -1, 28, 32);
        if (chapel != null) {
            Placer pl = chapel.placer(out);
            new Civic(pl, rng).church(Civic.Church.CHAPEL, 13, chapel.d(), "church");
            new Civic(pl.shifted(14, 0, 0), rng).graveyard(0, 6, 12, chapel.d() - 4);
            yard(chapel, 0.7);
        }
        // the post office on its corner, the volunteer fire house down the lane from it
        Kit.Lot post = civicLot(we, sWe + ((qPost & 1) == 0 ? -17 : 17), qPost >= 2 ? 1 : -1, 16, 22);
        if (post != null) {
            new Civic(post.placer(out), rng).hall(16, 20, "postoffice", false);
            hall(post.placer(out), 16, 20);
            yard(post, 0.8);
        }
        Kit.Lot fire = civicLot(ns, sNs + (qPost < 2 ? -36 : 36), (qPost & 1) == 0 ? 1 : -1, 14, 22);
        if (fire != null) {
            fireShed(fire);
        }
        // the grain elevator out along the lane, the feed store past it
        int east = (qElevator & 1) == 0 ? -1 : 1, south = qElevator >= 2 ? 1 : -1;
        Kit.Lot elevator = civicLot(we, sWe + east * 32, south, 44, 26);
        if (elevator != null) {
            Placer pl = elevator.placer(out);
            new Landmarks(pl, rng).grainElevator(1, 6);
            for (int x = 0; x < elevator.w(); x++) {
                for (int z = 0; z < elevator.d(); z++) {
                    if (pl.get(x, 0, z) == null) {
                        pl.set(x, 0, z, rng.nextDouble() < 0.6 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                    }
                }
            }
            pl.vehicle(36, 1, 3, 90, "pickup,box_truck", rng.nextLong());
        }
        Kit.Lot feed = civicLot(we, sWe + east * 63, south, 12, 24);
        if (feed != null) {
            Storefront s = new Storefront(feed.placer(out), rng, 12, 16, 1, Storefront.Style.FALSE_FRONT, Storefront.Trade.FEED);
            s.yard = feed.d() - 16;
            s.openWest = s.openEast = true;
            s.build();
        }
        // farmhouses and trailers strung along the lanes, out past the crossroads
        Homes.Mix rural = new Homes.Mix(Homes.Mix.weights(0.1, 0.04, 0.24, 0.32, 0.1, 0.14, 0.06, 0), 7, 0.35, 0.05,
                0.05, 0.3, 0.6, 0.12, 0.9, false, false);
        for (Kit.Lane l : new Kit.Lane[] {we, ns}) {
            for (Kit.Lot lot : kit.lotsAlong(l, 18, 26, 30, 6, 8, l.length() - 8, 0)) {
                homes.home(lot, rural);
            }
        }
        // a wooden water tank on its legs, the power lines, stop signs, a car in the ditch
        if (rng.nextDouble() < 0.5) {
            int[] sq = kit.freeSquare(13, 15);
            if (sq != null) {
                new Landmarks(new Placer(out, 0, 0, 0, 0), rng).waterTower(sq[0] + sq[2] / 2, sq[1] + sq[2] / 2, false);
                kit.claim(sq[0], sq[1], sq[0] + sq[2] - 1, sq[1] + sq[2] - 1, Kit.BUILT);
            }
        }
        kit.powerLine(0, (int) cz - 6, w - 1, (int) cz - 6, 8, 24);
        kit.powerLine((int) cx + 6, 0, (int) cx + 6, d - 1, 8, 24);
        stopSign(ns, false);
        stopSign(ns, true);
        kit.abandonedCars(we, 1.2);
        kit.abandonedCars(ns, 1.2);
        if (rng.nextBoolean()) {
            wreck(cx + jitter(3), cz + jitter(3), false);
        }
        kit.woods(0.55);
        kit.overgrow(0.9);
    }

    // ------------------------------------------------------------------ main street

    private void mainStreet() {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        double gz = W[1], gx = N[0];
        double xa = Math.max(18, w * 0.08);
        double xc = w - 50;
        int green = 28;
        double mainEnd = xc - green - 14;
        Kit.Lane west = kit.lane(Kit.Style.ROAD, W[0], W[1], xa - 8, gz);
        Kit.Lane main = kit.lane(Kit.Style.MAIN, xa - 10, gz, mainEnd + 2, gz);
        Kit.Lane forkN = kit.curve(Kit.Style.ROAD, mainEnd, gz, xc - green + 2, gz - green + 4, xc, gz - green - 2,
                xc + green - 2, gz - green + 4, xc + green + 16, gz);
        Kit.Lane forkS = kit.curve(Kit.Style.ROAD, mainEnd, gz, xc - green + 2, gz + green - 4, xc, gz + green + 2,
                xc + green - 2, gz + green - 4, xc + green + 16, gz);
        Kit.Lane east = kit.lane(Kit.Style.ROAD, xc + green + 14, gz, E[0], E[1]);
        Kit.Lane cross = kit.lane(Kit.Style.STREET, N[0], N[1], S[0], S[1]);
        double x2 = xa + (gx - xa) * 0.5;
        int back = 40;
        // a side street each way from the alleys out to the houses, not crossing main street
        Kit.Lane cross2n = kit.lane(Kit.Style.STREET, x2, gz - back - 30, x2, gz - back);
        Kit.Lane cross2s = kit.lane(Kit.Style.STREET, x2, gz + back, x2, gz + back + 30);
        Kit.Lane alleyN = kit.lane(Kit.Style.GRAVEL, xa - 8, gz - back, mainEnd, gz - back);
        Kit.Lane alleyS = kit.lane(Kit.Style.GRAVEL, xa - 8, gz + back, mainEnd, gz + back);
        // residential streets out past the alleys
        double rn = gz - back - 30, rs = gz + back + 30;
        List<Kit.Lane> res = new ArrayList<>();
        if (rn > 14) {
            res.add(kit.lane(Kit.Style.STREET, 10, rn, gx, rn));
        }
        if (rs < d - 14) {
            res.add(kit.lane(Kit.Style.STREET, 10, rs, gx, rs));
        }
        kit.pave();

        // the church on its green where the road forks
        Kit.Lot church = kit.fit(xc - 19, gz, 1, 0, 19, 36);
        if (church != null) {
            kit.lots.add(church);
            Civic.Church kind = rng.nextBoolean() ? Civic.Church.BRICK : Civic.Church.CHAPEL;
            new Civic(church.placer(out), rng).church(kind, 19, 36, kind == Civic.Church.BRICK ? "methodist" : "church");
            yard(church, 0.5);
        }
        greenAround((int) xc, (int) gz, green);

        // the shops: both sides of main street between the cross streets, the special ones first
        double sCross = along(main, gx, gz);
        double clear = Kit.Style.STREET.reach() + 1;
        int depth = back - (int) Kit.Style.MAIN.reach() - 4;
        List<Storefront.Trade> wanted = new ArrayList<>(List.of(Storefront.Trade.GROCERY, Storefront.Trade.HOTEL,
                Storefront.Trade.DRUGSTORE, Storefront.Trade.HARDWARE, Storefront.Trade.CAFE, Storefront.Trade.TAVERN));
        // the bank on the corner, the picture house and the post office mid-block
        Kit.Lot bank = civicLot(main, sCross - clear - 13, 1, 13, depth);
        if (bank != null) {
            new Civic(bank.placer(out), rng).bank(13, Math.min(20, depth - 2));
        }
        Kit.Lot theatre = civicLot(main, sCross * (0.45 + rng.nextDouble() * 0.2), -1, 15, depth);
        if (theatre != null) {
            new Civic(theatre.placer(out), rng).theatre(15, depth - 2);
        }
        Kit.Lot post = civicLot(main, sCross - clear - 9, -1, 17, depth);
        if (post != null) {
            new Civic(post.placer(out), rng).hall(17, depth - 2, "postoffice", false);
            hall(post.placer(out), 17, depth - 2);
        }
        shops(main, 2, sCross - clear, 0, depth, 3, wanted);
        shops(main, sCross + clear, main.length() - 4, 0, depth, 2, wanted);
        core.add(new int[] {(int) xa - 14, (int) gz - back + 1, (int) mainEnd + 4, (int) gz + back - 1});

        // past main street on the cross street's east side: the fire station and the sheriff to the
        // north, the elementary school and its buses to the south
        Kit.Lot fire = civicLot(cross, along(cross, gx, 17), -1, 18, 26);
        if (fire != null) {
            new Civic(fire.placer(out), rng).fireStation(18, 24);
        }
        Kit.Lot sheriff = civicLot(cross, along(cross, gx, 40), -1, 20, 30);
        if (sheriff != null) {
            new Civic(sheriff.placer(out), rng).sheriff(20, 30);
        }
        Kit.Lot school = civicLot(cross, along(cross, gx, d - 30), -1, 50, 44);
        if (school != null) {
            new Civic(school.placer(out), rng).school(50, 36, false);
            yard(school, 0.6);
            if (school.d() >= 43) {
                // the playground behind the classrooms
                int[] a = school.placer(out).world(4, 36), b = school.placer(out).world(10, 42);
                kit.playground(Math.min(a[0], b[0]), Math.min(a[1], b[1]));
            }
        }
        // the water tower over the back alleys
        towerNear((int) (xa + (gx - xa) * 0.3), (int) (gz - back - 17), true);
        Homes.Mix town = new Homes.Mix(Homes.Mix.weights(0.08, 0.3, 0.3, 0.14, 0, 0.02, 0.14, 0.02), 5, 0.4, 0.2, 0.2,
                0.25, 0.4, 0.06, 0.75, false, false);
        List<Kit.Lane> homesOn = new ArrayList<>(res);
        homesOn.add(cross);
        homesOn.add(cross2n);
        homesOn.add(cross2s);
        homesOn.add(west);
        homesOn.add(east);
        houses(homesOn, town, 16, 21, 28);

        // dressing the street the way it was left
        townLamps(main, 14, 0.25);
        furniture(main, 4, main.length() - 4);
        diagonalParking(main, 6, main.length() - 6, 0.45);
        kit.abandonedCars(main, 3);
        for (Kit.Lane l : new Kit.Lane[] {forkN, forkS, west, east}) {
            kit.streetLights(l, 30, 6, 0.4);
            kit.abandonedCars(l, 2);
        }
        int wrecks = 2 + rng.nextInt(3);
        for (int i = 0; i < wrecks; i++) {
            double[] q = main.point(10 + rng.nextDouble() * (main.length() - 20));
            wreck(q[0] + jitter(3), q[1] + jitter(3), false);
        }
        // the school bus that tried to get the children out, across the road at the fork
        double[] fq = forkN.point(rng.nextDouble() * 6);
        wreck(fq[0], fq[1] + jitter(2), true);
        barricade(cross, along(cross, gx, gz - Kit.Style.MAIN.reach() - 5));
        barricade(cross, along(cross, gx, gz + Kit.Style.MAIN.reach() + 5));
        stopSign(cross2n, false);
        stopSign(cross2s, true);
        for (Kit.Lane a : new Kit.Lane[] {alleyN, alleyS}) {
            kit.powerLine((int) a.xs[0], (int) a.zs[0] + 3, (int) a.xs[a.xs.length - 1], (int) a.zs[0] + 3, 8, 22);
        }
        kit.woods(0.35);
        kit.overgrow(0.75);
    }

    /** The green inside the fork: grass, old trees, the war memorial, a path to the church door. */
    private void greenAround(int cx, int cz, int r) {
        for (int x = cx - r - 4; x <= cx + r + 4; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                if (inside(x, z) && kit.use(x, z) == Kit.FREE) {
                    kit.claim(x, z, x, z, Kit.PARK);
                    out.set(x, 0, z, "minecraft:grass_block");
                }
            }
        }
        Placer world = new Placer(out, 0, 0, 0, 0);
        int mx = cx - r + 2;
        if (kit.use(mx, cz) == Kit.PARK) {
            new Landmarks(world, rng).memorial(mx, cz);
        }
        for (int i = 0; i < 6; i++) {
            int tx = cx - r + rng.nextInt(2 * r), tz = cz + (rng.nextBoolean() ? 1 : -1) * (10 + rng.nextInt(Math.max(1, r - 14)));
            if (kit.use(tx, tz) == Kit.PARK && out.get(tx, 1, tz) == null) {
                kit.tree(rng.nextBoolean() ? Trees.Kind.OAK : Trees.Kind.SYCAMORE, tx, tz);
            }
        }
        for (int z = cz - 1; z <= cz; z++) {
            for (int x = mx + 3; x < cx; x++) {
                if (kit.use(x, z) == Kit.PARK) {
                    out.set(x, 0, z, "sidewalk");
                }
            }
        }
    }

    /** A water tower on the nearest free ground to (x, z). */
    private void towerNear(int x, int z, boolean steel) {
        for (int k = 0; k < 40; k++) {
            int tx = x + (k % 7 - 3) * 6, tz = z + (k / 7 - 3) * 6;
            if (kit.free(tx - 7, tz - 7, tx + 7, tz + 7)) {
                new Landmarks(new Placer(out, 0, 0, 0, 0), rng).waterTower(tx, tz, steel);
                kit.claim(tx - 6, tz - 6, tx + 6, tz + 6, Kit.BUILT);
                for (int dx = -6; dx <= 6; dx++) {
                    for (int dz = -6; dz <= 6; dz++) {
                        if (out.get(tx + dx, 0, tz + dz) == null) {
                            out.set(tx + dx, 0, tz + dz, rng.nextDouble() < 0.5 ? "minecraft:gravel" : "minecraft:grass_block");
                        }
                    }
                }
                Kit.chainlink(new Placer(out, tx - 6, 0, tz - 6, 0), rng, 0, 0, 12, 0, 3);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ the county seat

    private void countySeat() {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        double cx = N[0], cz = W[1];
        int half = Math.max(30, Math.min(40, Math.min(w, d) / 8));
        double ring = half + Kit.Style.MAIN.reach() - 3;
        // the four streets round the square, run on past its corners out to a ring road round the
        // business district; the roads in from the gates stop at the ring
        int back = 38;
        double outer = ring + Kit.Style.MAIN.reach() + back;
        Kit.Lane top = kit.lane(Kit.Style.MAIN, cx - outer, cz - ring, cx + outer, cz - ring);
        Kit.Lane bottom = kit.lane(Kit.Style.MAIN, cx - outer, cz + ring, cx + outer, cz + ring);
        Kit.Lane left = kit.lane(Kit.Style.MAIN, cx - ring, cz - outer, cx - ring, cz + outer);
        Kit.Lane right = kit.lane(Kit.Style.MAIN, cx + ring, cz - outer, cx + ring, cz + outer);
        List<Kit.Lane> ringRoad = new ArrayList<>();
        ringRoad.add(kit.lane(Kit.Style.STREET, cx - outer, cz - outer, cx + outer, cz - outer));
        ringRoad.add(kit.lane(Kit.Style.STREET, cx - outer, cz + outer, cx + outer, cz + outer));
        ringRoad.add(kit.lane(Kit.Style.STREET, cx - outer, cz - outer, cx - outer, cz + outer));
        ringRoad.add(kit.lane(Kit.Style.STREET, cx + outer, cz - outer, cx + outer, cz + outer));
        Kit.Lane inN = kit.lane(Kit.Style.ROAD, N[0], N[1], cx, cz - outer);
        Kit.Lane inS = kit.lane(Kit.Style.ROAD, S[0], S[1], cx, cz + outer);
        Kit.Lane inW = kit.lane(Kit.Style.ROAD, W[0], W[1], cx - outer, cz);
        Kit.Lane inE = kit.lane(Kit.Style.ROAD, E[0], E[1], cx + outer, cz);
        kit.pave();

        // the square: lawn, paths in from each side, the courthouse, the memorial and the bandstand
        int sq0x = (int) (cx - ring + Kit.Style.MAIN.reach()), sq1x = (int) (cx + ring - Kit.Style.MAIN.reach());
        int sq0z = (int) (cz - ring + Kit.Style.MAIN.reach()), sq1z = (int) (cz + ring - Kit.Style.MAIN.reach());
        for (int x = sq0x; x <= sq1x; x++) {
            for (int z = sq0z; z <= sq1z; z++) {
                if (kit.use(x, z) == Kit.FREE) {
                    kit.claim(x, z, x, z, Kit.PARK);
                    boolean path = Math.abs(x - (int) cx) <= 1 || Math.abs(z - (int) cz) <= 1;
                    out.set(x, 0, z, path ? (rng.nextDouble() < 0.1 ? "sidewalk_cracked" : "sidewalk") : "minecraft:grass_block");
                }
            }
        }
        int cw = Math.min(34, sq1x - sq0x - 14), cd = Math.min(30, sq1z - sq0z - 22);
        int hx = (int) cx - cw / 2, hz = (int) cz - cd / 2 + 2;
        new Civic(new Placer(out, hx, 0, hz - 4, 0), rng).courthouse(cw, cd + 4);
        hosts.add(new Host(new Placer(out, hx, 0, hz - 4, 0), Courthouse.cellar(cw, cd + 4), 6, Courthouse.BASE));
        kit.claim(hx - 2, hz - 10, hx + cw + 1, hz + cd + 1, Kit.BUILT);
        Placer world = new Placer(out, 0, 0, 0, 0);
        new Landmarks(world, rng).memorial((int) cx + 10, sq1z - 6);
        kit.gazebo((int) cx - 14, sq1z - 9);
        for (int[] c : new int[][] {{sq0x + 4, sq0z + 4}, {sq1x - 4, sq0z + 4}, {sq0x + 4, sq1z - 4}, {sq1x - 4, sq1z - 4}}) {
            kit.tree(rng.nextBoolean() ? Trees.Kind.OAK : Trees.Kind.MAPLE, c[0], c[1]);
        }
        for (int x = sq0x + 6; x <= sq1x - 6; x += 18) {
            townLamp(x, sq0z + 1, rng.nextDouble() < 0.3);
            townLamp(x, sq1z - 1, rng.nextDouble() < 0.3);
        }
        // a FEMA aid station that didn't last: tents, crates, a barricade of buses
        for (int i = 0; i < 4; i++) {
            int tx = sq0x + 6 + i * 6, tz = (int) cz + 6;
            if (kit.use(tx, tz) == Kit.PARK) {
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        out.set(tx + dx, 1 + (dz == 1 ? 1 : 0), tz + dz, "tarp_green");
                    }
                }
                out.set(tx + 1, 1, tz + 1, "crate");
            }
        }

        // the shops facing the square on all four sides, the bank on a corner, the theatre, a hotel
        List<Storefront.Trade> wanted = new ArrayList<>(List.of(Storefront.Trade.GROCERY, Storefront.Trade.HOTEL,
                Storefront.Trade.DRUGSTORE, Storefront.Trade.HARDWARE, Storefront.Trade.CAFE, Storefront.Trade.REALTY,
                Storefront.Trade.TAVERN, Storefront.Trade.BARBER, Storefront.Trade.ANTIQUES, Storefront.Trade.GUNS));
        int depth = back - (int) Kit.Style.STREET.reach() - 1;
        Kit.Lane[] sides = {top, bottom, left, right};
        int[] outward = {-1, 1, 1, -1};
        // which side of each lane faces away from the square: top's left (+1) is north... computed from geometry
        for (int i = 0; i < 4; i++) {
            Kit.Lane l = sides[i];
            double[] mid = l.point(l.length() / 2);
            int side = (-mid[3] * 1) * (mid[0] - cx) + (mid[2] * 1) * (mid[1] - cz) > 0 ? 1 : -1;
            outward[i] = side;
        }
        // along each side: from the alley to the corner, the corner to the road in, the road in to
        // the next corner, then on to the alley; the bank, the theatre and the town hall go in first
        double corner = outer - ring, mainClear = Kit.Style.MAIN.reach() + 1, ringClear = Kit.Style.STREET.reach() + 1;
        Kit.Lot bank = civicLot(sides[0], corner + mainClear + 7, outward[0], 13, depth);
        if (bank != null) {
            new Civic(bank.placer(out), rng).bank(13, depth - 2);
        }
        Kit.Lot theatre = civicLot(sides[1], corner + ring * 0.5, outward[1], 15, depth);
        if (theatre != null) {
            new Civic(theatre.placer(out), rng).theatre(15, depth - 2);
        }
        Kit.Lot townHall = civicLot(sides[2], corner + ring * 0.5, outward[2], 20, depth);
        if (townHall != null) {
            new Civic(townHall.placer(out), rng).hall(20, depth - 2, "townhall", true);
            hall(townHall.placer(out), 20, depth - 2);
        }
        Kit.Lot lib = civicLot(sides[3], corner + ring * 0.5, outward[3], 21, depth);
        if (lib != null) {
            yard(lib, 0.5);   // first: the library digs its basement out of it
            new Civic(lib.placer(out), rng).library(21, Math.min(22, depth - 2));
        }
        // the churches face the square from two of its corners
        Kit.Lot stone = civicLot(sides[0], sides[0].length() - corner - mainClear - 13, outward[0], 23, depth);
        if (stone != null) {
            new Civic(stone.placer(out), rng).church(Civic.Church.STONE, 23, stone.d(), "stmary");
            yard(stone, 0.5);
        }
        Kit.Lot brick = civicLot(sides[1], sides[1].length() - corner - mainClear - 11, outward[1], 19, depth);
        if (brick != null) {
            new Civic(brick.placer(out), rng).church(Civic.Church.BRICK, 19, brick.d(), "church");
            yard(brick, 0.5);
        }
        for (int i = 0; i < 4; i++) {
            Kit.Lane l = sides[i];
            shops(l, ringClear, corner - mainClear, outward[i], depth, 2, wanted);
            shops(l, corner + mainClear, l.length() - corner - mainClear, outward[i], depth, 3, wanted);
            shops(l, l.length() - corner + mainClear, l.length() - ringClear, outward[i], depth, 2, wanted);
            townLamps(l, 14, 0.3);
            furniture(l, 6, l.length() - 6);
            diagonalParking(l, corner + mainClear, l.length() - corner - mainClear, 0.4);
            kit.abandonedCars(l, 2.5);
        }
        core.add(new int[] {(int) (cx - outer), (int) (cz - outer), (int) (cx + outer), (int) (cz + outer)});

        // out past the ring road on the roads in: the sheriff and jail, the fire station
        double pastRing = Kit.Style.STREET.reach() + 12;
        Kit.Lot sheriff = civicLot(inE, along(inE, cx + outer + pastRing, cz), rng.nextBoolean() ? 1 : -1, 20, 32);
        if (sheriff != null) {
            new Civic(sheriff.placer(out), rng).sheriff(20, 32);
        }
        Kit.Lot fire = civicLot(inW, along(inW, cx - outer - pastRing, cz), rng.nextBoolean() ? 1 : -1, 19, 26);
        if (fire != null) {
            new Civic(fire.placer(out), rng).fireStation(19, 24);
        }
        towerNear((int) (cx - outer - 20), (int) (cz - outer - 20), true);
        Homes.Mix town = new Homes.Mix(Homes.Mix.weights(0.1, 0.34, 0.26, 0.1, 0, 0, 0.1, 0.1), 6, 0.4, 0.25, 0.25,
                0.2, 0.45, 0.06, 0.7, false, false);
        List<Kit.Lane> homesOn = new ArrayList<>(ringRoad);
        homesOn.addAll(List.of(inN, inS, inW, inE));
        houses(homesOn, town, 17, 22, 30);
        int wrecks = 3 + rng.nextInt(3);
        for (int i = 0; i < wrecks; i++) {
            Kit.Lane l = sides[rng.nextInt(4)];
            double[] q = l.point(8 + rng.nextDouble() * (l.length() - 16));
            wreck(q[0], q[1], i == 0);
        }
        for (Kit.Lane l : new Kit.Lane[] {inN, inS, inW, inE}) {
            barricade(l, l.length() - Kit.Style.STREET.reach() - 6);
        }
        for (Kit.Lane l : sides) {
            if (rng.nextBoolean()) {
                barricade(l, rng.nextBoolean() ? ringClear + 3 : l.length() - ringClear - 3);
            }
        }
        // the power lines on the ring road's outer verge
        int o = (int) Kit.Style.STREET.half + 2;
        int r0 = (int) (cx - outer), r1 = (int) (cx + outer), q0 = (int) (cz - outer), q1 = (int) (cz + outer);
        kit.powerLine(r0, q0 - o, r1, q0 - o, 8, 24);
        kit.powerLine(r0, q1 + o, r1, q1 + o, 8, 24);
        kit.powerLine(r0 - o, q0, r0 - o, q1, 8, 24);
        kit.powerLine(r1 + o, q0, r1 + o, q1, 8, 24);
        kit.woods(0.3);
        kit.overgrow(0.7);
    }

    /** The high school's football field: chalk lines in the long grass, goalposts, floodlights, the bleachers. */
    private void football(Kit.Lot lot) {
        Placer pl = lot.placer(out);
        int x0 = 7, x1 = lot.w() - 8, z0 = 7, z1 = lot.d() - 12;
        if (x1 - x0 < 40 || z1 - z0 < 16) {
            yard(lot, 0.8);
            return;
        }
        int mz = (z0 + z1) / 2;
        // the cinder track round it, the field inside with its yard lines half grown over
        for (int x = x0 - 3; x <= x1 + 3; x++) {
            for (int z = z0 - 3; z <= z1 + 3; z++) {
                boolean field = x >= x0 && x <= x1 && z >= z0 && z <= z1;
                if (!field) {
                    pl.set(x, 0, z, rng.nextDouble() < 0.2 ? "minecraft:coarse_dirt" : "minecraft:red_terracotta");
                } else {
                    boolean line = x == x0 || x == x1 || z == z0 || z == z1 || (x - x0) % 5 == 0;
                    boolean endZone = x < x0 + 5 || x > x1 - 5;
                    pl.set(x, 0, z, line && rng.nextDouble() < 0.75 ? "minecraft:white_concrete"
                            : endZone && rng.nextDouble() < 0.6 ? "minecraft:moss_block" : "minecraft:grass_block");
                }
            }
        }
        // goalposts at both ends
        for (int gx : new int[] {x0 - 1, x1 + 1}) {
            for (int y = 1; y <= 4; y++) {
                pl.set(gx, y, mz, "minecraft:iron_bars");
            }
            for (int z = mz - 3; z <= mz + 3; z++) {
                pl.set(gx, 5, z, "minecraft:iron_bars");
            }
            for (int y = 6; y <= 10; y++) {
                pl.set(gx, y, mz - 3, "minecraft:iron_bars");
                pl.set(gx, y, mz + 3, "minecraft:iron_bars");
            }
        }
        // the home bleachers along the far side, the press box on top
        int bz = z1 + 4;
        for (int i = 0; i < 5; i++) {
            for (int x = x0 + 6; x <= x1 - 6; x++) {
                if (rng.nextDouble() < 0.04) {
                    continue;
                }
                pl.set(x, 1 + i, bz + i, "minecraft:spruce_stairs[facing=south]");
                if ((x - x0) % 6 == 0) {
                    for (int y = 1; y <= i; y++) {
                        pl.set(x, y, bz + i, "minecraft:iron_bars");
                    }
                }
            }
        }
        int px = (x0 + x1) / 2 - 4;
        Bldg box = new Bldg(pl, rng);
        box.walls(px, bz + 4, px + 8, bz + 7, 6, 8, "corrugated_grey");
        box.clear(px + 1, 6, bz + 5, px + 7, 8, bz + 6);
        box.floor(px, bz + 4, px + 8, bz + 7, 9, "minecraft:smooth_stone_slab[type=bottom]");
        for (int x = px + 1; x <= px + 7; x++) {
            pl.set(x, 7, bz + 4, box.glass("x"));
        }
        // the scoreboard past the end zone, the floodlights on the corners
        int sx = x1 + 3;
        for (int y = 1; y <= 5; y++) {
            pl.set(sx + 1, y, mz - 3, "pole_wood");
            pl.set(sx + 1, y, mz + 3, "pole_wood");
        }
        for (int z = mz - 4; z <= mz + 4; z++) {
            for (int y = 6; y <= 8; y++) {
                pl.set(sx + 1, y, z, "concrete_dark");
            }
        }
        for (int[] c : new int[][] {{x0 - 5, z0 - 5}, {x1 + 5, z0 - 5}, {x0 - 5, z1 + 2}, {x1 + 5, z1 + 2}}) {
            for (int y = 1; y <= 13; y++) {
                pl.set(c[0], y, c[1], "streetlight_pole");
            }
            for (int dx = -1; dx <= 1; dx++) {
                pl.set(c[0] + dx, 14, c[1], "streetlight_head_off");
            }
        }
        // the fence round it all, the rest gone to grass
        Kit.chainlink(pl, rng, x0 - 6, z0 - 6, x1 + 6, z0 - 6, 2);
        Kit.chainlink(pl, rng, x0 - 6, z0 - 5, x0 - 6, lot.d() - 2, 2);
        Kit.chainlink(pl, rng, x1 + 6, z0 - 5, x1 + 6, lot.d() - 2, 2);
        for (int x = (x0 + x1) / 2 - 2; x <= (x0 + x1) / 2 + 2; x++) {
            pl.set(x, 1, z0 - 6, "air");
            pl.set(x, 2, z0 - 6, "air");
        }
        yard(lot, 0.9);
    }

    // ------------------------------------------------------------------ the mill town

    private void millTown() {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        double gz = W[1], gx = N[0];
        // main street along the gate line, the mill north of it, the railroad south
        Kit.Lane main = kit.lane(Kit.Style.MAIN, W[0], W[1], E[0], E[1]);
        Kit.Lane cross = kit.curve(Kit.Style.STREET, N[0], N[1], gx, gz - 20, gx, gz + 20, S[0], S[1]);
        int track = (int) gz + 44;
        int back = 36;
        Kit.Lane alley = kit.lane(Kit.Style.GRAVEL, 8, gz + back, w - 8, gz + back);
        // the mill village: streets of identical houses north-east of the mill and south of the tracks,
        // spaced for a row of house lots back to back between each pair
        List<Kit.Lane> village = new ArrayList<>();
        for (double z = track + 30; z < d - 22; z += 52) {
            village.add(kit.lane(Kit.Style.STREET, 8, z, w - 8, z));
        }
        double shopsBack = gz - Kit.Style.MAIN.reach() - (back - Kit.Style.MAIN.reach() + 3);
        if (shopsBack > 50) {
            village.add(kit.lane(Kit.Style.STREET, gx, 12, w - 8, 12));
            for (double x = gx + 40; x < w - 24; x += 52) {
                village.add(kit.lane(Kit.Style.STREET, x, 12, x, shopsBack - 14));
            }
        }
        kit.pave();
        railroad(track);

        // the mill: most of the north side of main street, its tower facing the street
        int millW = (int) Math.min(96, w * 0.42), millD = (int) Math.min(gz - Kit.Style.MAIN.reach() - 6, 70);
        double clear = Kit.Style.STREET.reach() + 1;
        double sCross = along(main, gx, gz);
        Kit.Lot mill = civicLot(main, sCross - clear - 4 - millW / 2.0, -1, millW, millD);
        if (mill != null) {
            Placer pl = mill.placer(out);
            new Landmarks(pl, rng).mill(millW, millD);
            for (int x = 0; x < millW; x++) {
                for (int z = 0; z < millD; z++) {
                    if (pl.get(x, 0, z) == null) {
                        pl.set(x, 0, z, rng.nextDouble() < 0.55 ? "asphalt_crack" : rng.nextDouble() < 0.5 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                    }
                }
            }
            Kit.chainlink(pl, rng, -1, -1, millW, -1, 3);
        }
        // the main street facing it: the company store first, then the rest
        int depth = back - (int) Kit.Style.MAIN.reach() - 3;
        List<Storefront.Trade> wanted = new ArrayList<>(List.of(Storefront.Trade.COMPANY, Storefront.Trade.CAFE,
                Storefront.Trade.TAVERN, Storefront.Trade.BARBER, Storefront.Trade.DIME, Storefront.Trade.PAWN));
        Kit.Lot post = civicLot(main, sCross + clear + 9, 1, 16, depth);
        if (post != null) {
            new Civic(post.placer(out), rng).hall(16, depth - 2, "postoffice", false);
            hall(post.placer(out), 16, depth - 2);
        }
        shops(main, Math.max(4, sCross - 110), sCross - clear, 1, depth, 2, wanted);
        shops(main, sCross + clear, Math.min(main.length() - 4, sCross + 90), 1, depth, 2, wanted);
        shops(main, sCross + clear, Math.min(main.length() - 4, sCross + 50), -1, depth + 6, 2, wanted);
        core.add(new int[] {0, (int) gz - 30, w - 1, (int) gz + back});
        // the church, the water tower with the mill's name, the depot by the tracks
        Kit.Lane vl = village.isEmpty() ? cross : village.get(0);
        Kit.Lot church = civicLot(vl, vl.length() * (0.6 + rng.nextDouble() * 0.2), 1, 19, 34);
        if (church != null) {
            new Civic(church.placer(out), rng).church(rng.nextBoolean() ? Civic.Church.BRICK : Civic.Church.CHAPEL, 19, 34, "methodist");
            yard(church, 0.6);
        }
        towerNear((int) gx - 60, 16, true);
        depot((int) gx + 11, track);
        // rows of mill houses: the same house over and over, a few painted since
        House.Style millHouse = rng.nextBoolean() ? House.Style.SHOTGUN : House.Style.BUNGALOW;
        double[] weights = new double[House.Style.values().length];
        weights[millHouse.ordinal()] = 0.85;
        weights[House.Style.BUNGALOW.ordinal()] += 0.1;
        weights[House.Style.TRAILER.ordinal()] = 0.05;
        Homes.Mix rows = new Homes.Mix(weights, 4, 0.2, 0.05, 0.1, 0.3, 0.3, 0.05, 0.85, false, false);
        houses(village, rows, 13, 16, 26);
        Homes.Mix older = new Homes.Mix(Homes.Mix.weights(0.1, 0.3, 0.3, 0.2, 0, 0, 0.1, 0), 6, 0.3, 0.2, 0.2, 0.3, 0.4,
                0.06, 0.8, false, false);
        houses(List.of(cross), older, 17, 22, 30);
        townLamps(main, 16, 0.2);
        furniture(main, 4, main.length() - 4);
        kit.abandonedCars(main, 3);
        kit.abandonedCars(alley, 1);
        for (int i = 0; i < 2 + rng.nextInt(2); i++) {
            double[] q = main.point(20 + rng.nextDouble() * (main.length() - 40));
            wreck(q[0], q[1] + jitter(3), i == 0 && rng.nextBoolean());
        }
        barricade(cross, along(cross, gx, track - 6));
        stopSign(cross, false);
        kit.powerLine(4, (int) gz + back + 3, w - 4, (int) gz + back + 3, 8, 22);
        kit.woods(0.4);
        kit.overgrow(0.8);
    }

    /** A railroad across the place at z: ballast, ties, two rails, a boxcar or three left on it. */
    private void railroad(int z) {
        for (int x = 0; x < w; x++) {
            for (int dz = -3; dz <= 3; dz++) {
                int zz = z + dz;
                if (!inside(x, zz)) {
                    continue;
                }
                byte u = kit.use(x, zz);
                if (u == Kit.ROAD) {
                    // a level crossing: planks between the rails
                    if (Math.abs(dz) <= 1) {
                        out.set(x, 0, zz, "minecraft:spruce_planks");
                    }
                    continue;
                }
                kit.claim(x, zz, x, zz, Kit.BUILT);
                out.set(x, 0, zz, Math.abs(dz) <= 1 && x % 2 == 0 ? "minecraft:stripped_dark_oak_log[axis=z]" : "minecraft:gravel");
                out.set(x, -1, zz, "minecraft:gravel");
            }
            for (int dz : new int[] {-1, 1}) {
                if (inside(x, z + dz)) {
                    out.set(x, 1, z + dz, "rail[axis=x]");
                }
            }
        }
        // boxcars standing on the line, one off the rails on its side
        int cars = 2 + rng.nextInt(3);
        int x = 10 + rng.nextInt(20);
        String[] bodies = {"barn_red", "corrugated_red", "container_orange", "corrugated_rust", "container_blue"};
        for (int i = 0; i < cars && x + 12 < w; i++) {
            if (kit.use(x, z) == Kit.ROAD || kit.use(x + 11, z) == Kit.ROAD) {
                x += 16;
                continue;
            }
            String body = bodies[rng.nextInt(bodies.length)];
            for (int bx = x; bx < x + 12; bx++) {
                out.set(bx, 1, z, "minecraft:polished_blackstone_slab[type=top]");
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = 2; y <= 5; y++) {
                        out.set(bx, y, z + dz, y == 5 ? "minecraft:smooth_stone_slab[type=bottom]" : body);
                    }
                }
            }
            for (int bx : new int[] {x + 1, x + 2, x + 9, x + 10}) {
                out.set(bx, 1, z - 1, "tire[axis=x]");
                out.set(bx, 1, z + 1, "tire[axis=x]");
            }
            // the sliding door open, the inside empty but for straw
            if (rng.nextBoolean()) {
                for (int y = 2; y <= 4; y++) {
                    out.set(x + 5, y, z - 1, "air");
                    out.set(x + 6, y, z - 1, "air");
                }
                out.set(x + 5, 2, z, "minecraft:hay_block");
            }
            x += 13 + rng.nextInt(10);
        }
    }

    /** The depot: a brick station on the platform, its canopy on posts, the bench and the timetable. */
    private void depot(int x0, int track) {
        int z0 = track + 4, z1 = track + 15;
        if (!kit.free(x0 - 2, z0, x0 + 24, z1 + 1)) {
            return;
        }
        kit.claim(x0 - 2, z0, x0 + 24, z1 + 1, Kit.BUILT);
        // drawn with the platform on its south side, then turned so the platform meets the tracks
        Placer pl = new Placer(out, x0 + 22, 0, z1, 2);
        Bldg b = new Bldg(pl, rng);
        b.shell(0, 0, 13, 6, 1, 5, "brick_red", "floor_boards", "floor_boards");
        b.windowRow(2, 11, 3, 6, 2, "south", 2, null);
        b.windowRow(2, 11, 3, 0, 2, "north", 2, null);
        b.door(6, 1, 6, "minecraft:dark_oak_door", "south");
        b.door(6, 1, 0, "minecraft:dark_oak_door", "north");
        b.hip(0, 13, 0, 6, 6, "minecraft:deepslate_tile", 1);
        // the platform and its canopy
        for (int x = -2; x <= 24; x++) {
            for (int z = 7; z <= 10; z++) {
                pl.set(x, 0, z, z == 10 ? "curb" : "sidewalk");
                if (x > 13 && z <= 9) {
                    pl.set(x, 4, z, "minecraft:spruce_slab[type=top]");
                }
            }
            if (x > 13 && x % 4 == 0) {
                for (int y = 1; y <= 3; y++) {
                    pl.set(x, y, 9, "minecraft:spruce_fence");
                }
            }
        }
        pl.set(16, 1, 7, "minecraft:spruce_stairs[facing=north]");
        pl.set(17, 1, 7, "minecraft:spruce_stairs[facing=north]");
        pl.set(20, 1, 7, "minecraft:cauldron");
        for (int x = 1; x <= 12; x++) {
            pl.set(x, 1, 2, x % 4 == 0 ? "reception_desk[facing=south]" : "air");
        }
        for (int x = 2; x <= 11; x += 3) {
            pl.set(x, 1, 4, "minecraft:dark_oak_stairs[facing=north]");
        }
        Kit.lawn(pl, rng, -2, -1, 24, -1, 0.8);
    }
}
