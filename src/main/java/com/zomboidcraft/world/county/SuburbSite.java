package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Walkup;
import java.util.ArrayList;
import java.util.List;

/**
 * The suburbs, in four layouts:
 * <ul>
 * <li>cul_de_sac: a newer subdivision. Two curving collector roads cross at a planted
 * roundabout, and short streets branch off them to end in turning circles ringed with ranch
 * houses and colonials. A park where the land was left over, woods between the streets.</li>
 * <li>old_grid: the pre-war streets near town. A tight grid of tree-lined streets, narrow lots
 * of bungalows, shotgun houses and colonials, gravel alleys behind with detached garages and
 * the power lines, a corner shop or two.</li>
 * <li>estates: the money. A gated boulevard, then private drives winding between big lots:
 * mansions behind iron railings, turning circles with fountains, pools and tennis courts.</li>
 * <li>trailer_park: a gravel loop of single-wides on small pads, decks and junk and propane
 * tanks, the office trailer and its sign at the gate, the laundry shed, power lines overhead.</li>
 * </ul>
 * Every layout runs a street out to each side's gate, where the county roads arrive.
 */
final class SuburbSite extends Site {
    private Kit kit;
    private Homes homes;

    SuburbSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        homes = new Homes(kit);
        switch (p.variant) {
            case "old_grid" -> oldGrid();
            case "estates" -> estates();
            case "trailer_park" -> trailerPark();
            default -> culDeSac();
        }
        kit.roadWeeds(0.25);
    }

    private double jitter(double amount) {
        return (rng.nextDouble() * 2 - 1) * amount;
    }

    // ------------------------------------------------------------------ cul-de-sacs

    private void culDeSac() {
        double cx = w / 2.0 + jitter(w * 0.08), cz = d / 2.0 + jitter(d * 0.08);
        double[] west = kit.beyond(3), east = kit.beyond(1), north = kit.beyond(0), south = kit.beyond(2);
        // two collectors, each wandering between its gates through the roundabout
        Kit.Lane we = kit.curve(Kit.Style.ROAD, west[0], west[1], 10, west[1], w * 0.25, cz + jitter(d * 0.12), cx - 14, cz,
                cx + 14, cz, w * 0.75, cz + jitter(d * 0.12), w - 11, east[1], east[0], east[1]);
        Kit.Lane ns = kit.curve(Kit.Style.ROAD, north[0], north[1], north[0], 10, cx + jitter(w * 0.12), d * 0.25, cx, cz - 14,
                cx, cz + 14, cx + jitter(w * 0.12), d * 0.75, south[0], d - 11, south[0], south[1]);
        kit.bulb(Kit.Style.ROAD, cx, cz, 10);
        // the closes: short streets off the collectors, each ending in a turning circle
        List<Kit.Lane> closes = new ArrayList<>();
        List<Kit.Bulb> circles = new ArrayList<>();
        int want = 3 + (w * d) / 14000;
        for (int tries = 0; tries < 40 && closes.size() < want; tries++) {
            Kit.Lane from = rng.nextBoolean() ? we : ns;
            double s = 30 + rng.nextDouble() * (from.length() - 60);
            double[] at = from.point(s);
            if (Math.hypot(at[0] - cx, at[1] - cz) < 34) {
                continue;
            }
            int side = rng.nextBoolean() ? 1 : -1;
            double nx = -at[3] * side, nz = at[2] * side;
            double len = 38 + rng.nextDouble() * 34;
            double bend = jitter(0.5);
            double mx = at[0] + nx * len * 0.5 + at[2] * bend * len * 0.3, mz = at[1] + nz * len * 0.5 + at[3] * bend * len * 0.3;
            double ex = at[0] + nx * len + at[2] * bend * len * 0.5, ez = at[1] + nz * len + at[3] * bend * len * 0.5;
            if (ex < 26 || ez < 26 || ex > w - 26 || ez > d - 26 || clashes(ex, ez, mx, mz, closes, circles, we, ns)) {
                continue;
            }
            Kit.Lane close = kit.curve(Kit.Style.STREET, at[0], at[1], at[0] + nx * 8, at[1] + nz * 8, mx, mz, ex, ez);
            closes.add(close);
            circles.add(kit.bulb(Kit.Style.STREET, ex, ez, 8));
        }
        kit.pave();
        kit.island(cx, cz, 5);
        Homes.Mix mix = new Homes.Mix(Homes.Mix.weights(0.42, 0.3, 0.1, 0.06, 0, 0, 0, 0.04), 7, 0.25, 0.2, 0.45, 0.2,
                0.5, 0.1, 0.6, false, false);
        for (int i = 0; i < closes.size(); i++) {
            Kit.Lane c = closes.get(i);
            Kit.Bulb b = circles.get(i);
            double[] end = c.point(c.length());
            double entry = Math.atan2(-end[3], -end[2]);
            for (Kit.Lot lot : kit.lotsAround(b, 5, 20, 30, entry + 0.9, entry + Math.PI * 2 - 0.9)) {
                homes.home(lot, mix);
            }
            for (Kit.Lot lot : kit.lotsAlong(c, 20, 25, 30, 1, 14, c.length() - 10, 0)) {
                homes.home(lot, mix);
            }
        }
        for (Kit.Lane l : new Kit.Lane[] {we, ns}) {
            for (Kit.Lot lot : kit.lotsAlong(l, 21, 27, 32, 1, 0, l.length(), 0)) {
                homes.home(lot, mix);
            }
        }
        int[] sq = kit.freeSquare(22, 40);
        if (sq != null) {
            kit.park(sq[0], sq[1], sq[2]);
        }
        for (Kit.Lane l : kit.lanes) {
            kit.streetLights(l, l.style == Kit.Style.ROAD ? 34 : 28, 6, 0.55);
            kit.streetTrees(l, 11, 0.55);
        }
        kit.abandonedCars(we, 1.6);
        kit.abandonedCars(ns, 1.6);
        for (Kit.Lane c : closes) {
            kit.abandonedCars(c, 1.0);
        }
        kit.woods(0.5);
        kit.overgrow(0.7);
    }

    /** True when a close's end or middle would come too near another street. */
    private boolean clashes(double ex, double ez, double mx, double mz, List<Kit.Lane> closes, List<Kit.Bulb> circles, Kit.Lane... mains) {
        for (Kit.Bulb b : circles) {
            if (Math.hypot(b.x() - ex, b.z() - ez) < 64 || Math.hypot(b.x() - mx, b.z() - mz) < 40) {
                return true;
            }
        }
        List<Kit.Lane> all = new ArrayList<>(closes);
        all.addAll(List.of(mains));
        for (Kit.Lane l : all) {
            for (double s = 0; s < l.length(); s += 4) {
                double[] q = l.point(s);
                if (Math.hypot(q[0] - ex, q[1] - ez) < 40 || (closes.contains(l) && Math.hypot(q[0] - mx, q[1] - mz) < 34)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the old grid

    private void oldGrid() {
        int sz = 76, sx = 80 + rng.nextInt(24);
        List<Integer> rows = new ArrayList<>(), cols = new ArrayList<>();
        for (int z = d / 2; z >= 14; z -= sz) {
            rows.add(0, z);
        }
        for (int z = d / 2 + sz; z <= d - 14; z += sz) {
            rows.add(z);
        }
        for (int x = w / 2; x >= 14; x -= sx) {
            cols.add(0, x);
        }
        for (int x = w / 2 + sx; x <= w - 14; x += sx) {
            cols.add(x);
        }
        // streets run between the outermost cross streets, or on toward the edge where there are none
        int xa = Math.min(cols.get(0), 12), xb = Math.max(cols.get(cols.size() - 1), w - 13);
        int za = Math.min(rows.get(0), 12), zb = Math.max(rows.get(rows.size() - 1), d - 13);
        List<Kit.Lane> streets = new ArrayList<>();
        for (int z : rows) {
            boolean gate = z == d / 2;
            streets.add(kit.lane(Kit.Style.STREET, gate ? -3 : xa, z + 0.5, gate ? w + 2 : xb, z + 0.5));
        }
        List<Kit.Lane> avenues = new ArrayList<>();
        for (int x : cols) {
            boolean gate = x == w / 2;
            avenues.add(kit.lane(Kit.Style.STREET, x + 0.5, gate ? -3 : za, x + 0.5, gate ? d + 2 : zb));
        }
        // alleys halfway between the streets, and past the outer streets where there's room
        List<Integer> alleys = new ArrayList<>();
        for (int i = 0; i + 1 < rows.size(); i++) {
            alleys.add((rows.get(i) + rows.get(i + 1)) / 2);
        }
        int first = rows.get(0), last = rows.get(rows.size() - 1);
        if (first - 38 >= 3) {
            alleys.add(first - 38);
        }
        if (last + 38 <= d - 4) {
            alleys.add(last + 38);
        }
        List<Kit.Lane> alleyLanes = new ArrayList<>();
        for (int z : alleys) {
            alleyLanes.add(kit.lane(Kit.Style.GRAVEL, xa, z + 0.5, xb, z + 0.5));
        }
        kit.pave();
        Homes.Mix mix = new Homes.Mix(Homes.Mix.weights(0.08, 0.25, 0.35, 0.12, 0, 0, 0.15, 0), 5, 0.3, 0.2, 0.3, 0.35,
                0.35, 0.08, 0.7, true, false);
        int shops = 0;
        for (Kit.Lane st : streets) {
            List<Kit.Lot> lots = kit.lotsAlong(st, 14, 18, 32, 0, 12, st.length() - 12, 0);
            for (Kit.Lot lot : lots) {
                double r = rng.nextDouble();
                if (shops < 3 && r < 0.06 && lot.w() >= 12) {
                    // a corner shop with a flat or two over it
                    new Walkup(lot.placer(out), rng, lot.w(), Math.min(lot.d() - 4, 20), 2 + rng.nextInt(2), true).build();
                    shops++;
                } else {
                    homes.home(lot, mix);
                }
            }
        }
        for (Kit.Lane av : avenues) {
            for (Kit.Lot lot : kit.lotsAlong(av, 15, 18, 26, 0, 0, av.length(), 0)) {
                homes.home(lot, new Homes.Mix(mix.styles(), 5, 0.3, 0.2, 0.3, 0.35, 0.4, 0.08, 0.7, false, false));
            }
        }
        for (Kit.Lane l : streets) {
            kit.streetLights(l, 30, 6, 0.5);
            kit.streetTrees(l, 8, 0.85);
            kit.abandonedCars(l, 2.5);
        }
        for (Kit.Lane l : avenues) {
            kit.streetLights(l, 30, 6, 0.5);
            kit.streetTrees(l, 8, 0.85);
            kit.abandonedCars(l, 2);
        }
        for (int z : alleys) {
            kit.powerLine(xa, z + 3, xb, z + 3, 8, 22);
        }
        int[] sq = kit.freeSquare(20, 34);
        if (sq != null) {
            kit.park(sq[0], sq[1], sq[2]);
        }
        kit.woods(0.35);
        kit.overgrow(0.8);
    }

    // ------------------------------------------------------------------ the estates

    private void estates() {
        double cx = w / 2.0 + jitter(w * 0.06), cz = d / 2.0 + jitter(d * 0.06);
        double[] west = kit.beyond(3), east = kit.beyond(1), north = kit.beyond(0), south = kit.beyond(2);
        // the boulevard in from the south gate, then drives winding off to the other gates
        Kit.Lane blvd = kit.lane(Kit.Style.MAIN, south[0], south[1], south[0], d - 46);
        Kit.Lane spine = kit.curve(Kit.Style.ESTATE, south[0], d - 44, cx + jitter(30), d * 0.6, cx + jitter(40), d * 0.35,
                north[0], 12, north[0], north[1]);
        Kit.Lane loopW = kit.curve(Kit.Style.ESTATE, west[0], west[1], 12, west[1], w * 0.22, west[1] + jitter(30), w * 0.32,
                d * 0.7, south[0] - 8, d - 44);
        Kit.Lane loopE = kit.curve(Kit.Style.ESTATE, east[0], east[1], w - 13, east[1], w * 0.78, east[1] + jitter(30), w * 0.68,
                d * 0.3, cx, d * 0.32);
        kit.bulb(Kit.Style.ESTATE, south[0], d - 45, 7);
        kit.pave();
        kit.island(south[0], d - 45, 3.5);
        // a planted median down the boulevard, and the gatehouse at its mouth
        for (int z = d - 44; z < d; z++) {
            for (int x = (int) south[0] - 1; x <= (int) south[0] + 1; x++) {
                out.set(x, 0, z, Math.abs(x - (int) south[0]) == 1 ? "curb" : "minecraft:grass_block");
                kit.claim(x, z, x, z, Kit.PARK);
            }
            if (z % 8 == 0) {
                kit.tree(Trees.Kind.YOUNG, (int) south[0], z);
            } else {
                out.set((int) south[0], 1, z, "minecraft:dark_oak_leaves[persistent=true]");
            }
        }
        gatehouse((int) south[0], d - 6);
        Homes.Mix mix = new Homes.Mix(Homes.Mix.weights(0.1, 0.3, 0, 0.1, 0, 0, 0, 0.5), 14, 0, 0, 0, 0,
                0.55, 0.15, 0.5, false, true);
        for (Kit.Lane l : new Kit.Lane[] {spine, loopW, loopE}) {
            for (Kit.Lot lot : kit.lotsAlong(l, 32, 42, 46, 3, 6, l.length() - 6, 0)) {
                homes.home(lot, mix);
            }
        }
        for (Kit.Lane l : new Kit.Lane[] {spine, loopW, loopE}) {
            for (double s = 10; s < l.length() - 6; s += 22) {
                double[] q = l.point(s);
                int side = ((int) s / 22) % 2 == 0 ? 1 : -1;
                int x = (int) Math.floor(q[0] - q[3] * side * 5.5), z = (int) Math.floor(q[1] + q[2] * side * 5.5);
                if (kit.use(x, z) == Kit.VERGE && out.get(x, 1, z) == null) {
                    kit.postLamp(x, z, rng.nextDouble() < 0.5);
                }
            }
            kit.streetTrees(l, 9, 0.7);
            kit.abandonedCars(l, 0.8);
        }
        int[] sq = kit.freeSquare(26, 44);
        if (sq != null) {
            kit.park(sq[0], sq[1], sq[2]);
        }
        kit.woods(0.65);
        kit.overgrow(0.55);
    }

    /** Brick gate piers with lamps, iron gates pushed open, a guard booth with its window smashed. */
    private void gatehouse(int x, int z) {
        for (int side = -1; side <= 1; side += 2) {
            int px = x + side * 9;
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    for (int y = 1; y <= 4; y++) {
                        out.set(px + dx * side, y, z + dz, "minecraft:bricks");
                    }
                    out.set(px + dx * side, 5, z + dz, "minecraft:stone_brick_slab[type=bottom]");
                }
            }
            out.set(px, 6, z, "minecraft:lantern");
            // the gate leaf, swung back against the wall
            for (int k = 1; k <= 3; k++) {
                out.set(px + side * 2 + (side > 0 ? k - 1 : 1 - k), 1, z + 2 + k, "minecraft:iron_bars");
                out.set(px + side * 2 + (side > 0 ? k - 1 : 1 - k), 2, z + 2 + k, "minecraft:iron_bars");
            }
            // brick walls running off along the boundary
            for (int k = 2; k < 22; k++) {
                int wx = px + side * k;
                if (wx < 1 || wx > w - 2 || kit.use(wx, z) == Kit.ROAD) {
                    continue;
                }
                out.set(wx, 1, z, "minecraft:bricks");
                out.set(wx, 2, z, rng.nextDouble() < 0.9 ? "minecraft:brick_wall" : "air");
            }
        }
        // the guard booth beside the in lane
        int bx = x + 4, bz = z - 10;
        Placer b = new Placer(out, bx, 0, bz, 0);
        b.fill(0, 0, 0, 2, 0, 2, "floor_concrete");
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                boolean edge = i != 1 || j != 1;
                if (!edge) {
                    continue;
                }
                b.set(i, 1, j, "siding_white");
                b.set(i, 2, j, i == 1 || j == 1 ? (rng.nextBoolean() ? "window_broken[axis=" + (i == 1 ? "x" : "z") + "]" : "window_empty[axis=" + (i == 1 ? "x" : "z") + "]") : "siding_white");
                b.set(i, 3, j, "minecraft:smooth_stone_slab[type=bottom]");
            }
        }
        b.set(1, 3, 1, "minecraft:smooth_stone_slab[type=bottom]");
        b.set(0, 1, 1, "air");
        b.set(1, 1, 1, "minecraft:barrel[facing=up,loot=police]");
    }

    // ------------------------------------------------------------------ the trailer park

    private void trailerPark() {
        int inset = Math.max(20, Math.min(w, d) / 5);
        double x0 = inset, z0 = inset, x1 = w - inset, z1 = d - inset;
        double r = 10;
        // the loop: a rounded rectangle
        Kit.Lane loop = kit.curve(Kit.Style.LANE,
                x0 + r, z0, w / 2.0, z0 + jitter(2), x1 - r, z0, x1, z0 + r, x1, d / 2.0, x1, z1 - r, x1 - r, z1,
                w / 2.0, z1, x0 + r, z1, x0, z1 - r, x0, d / 2.0, x0, z0 + r, x0 + r, z0);
        // spurs out to the gates, and a gravel lane up the middle
        double[] west = kit.beyond(3), east = kit.beyond(1), north = kit.beyond(0), south = kit.beyond(2);
        kit.lane(Kit.Style.LANE, west[0], west[1], x0, west[1]);
        kit.lane(Kit.Style.LANE, east[0], east[1], x1, east[1]);
        kit.lane(Kit.Style.LANE, north[0], north[1], north[0], z0);
        kit.lane(Kit.Style.LANE, south[0], south[1], south[0], z1);
        Kit.Lane middle = kit.lane(Kit.Style.GRAVEL, w / 2.0, z0, w / 2.0, z1);
        kit.pave();
        // the office trailer and the sign by the south gate
        int sx = (int) south[0] + 6, szz = d - 8;
        sign(sx, szz);
        mailboxes((int) south[0] - 12, d - 6);
        laundry((int) south[0] - 16, (int) z1 + 5);
        Homes.Mix mix = new Homes.Mix(Homes.Mix.weights(0, 0, 0, 0, 0.08, 0.86, 0.06, 0), 3, 0.05, 0.05, 0.1, 0.45,
                0.45, 0.12, 0.85, false, false);
        for (Kit.Lot lot : kit.lotsAlong(loop, 23, 26, 17, 1, 0, loop.length(), 0)) {
            homes.home(lot, mix);
        }
        for (Kit.Lot lot : kit.lotsAlong(middle, 23, 26, 16, 1, 8, middle.length() - 8, 0)) {
            homes.home(lot, mix);
        }
        // overhead power down the straight sides
        kit.powerLine((int) x0 + 14, (int) z0 - 5, (int) x1 - 14, (int) z0 - 5, 8, 18);
        kit.powerLine((int) x0 + 14, (int) z1 + 5, (int) x1 - 14, (int) z1 + 5, 8, 18);
        // junk everywhere: dumpsters, tyres, a burnt-out car, a couch someone dragged out
        for (int i = 0; i < 4; i++) {
            int jx = 4 + rng.nextInt(w - 8), jz = 4 + rng.nextInt(d - 8);
            if (kit.use(jx, jz) == Kit.FREE && kit.free(jx - 1, jz - 1, jx + 3, jz + 1)) {
                out.set(jx, 1, jz, "dumpster[facing=" + FACINGS[rng.nextInt(4)] + "]");
                out.set(jx + 2, 1, jz, "tire[axis=x]");
                out.set(jx + 2, 2, jz, rng.nextBoolean() ? "tire[axis=z]" : "air");
                if (rng.nextBoolean()) {
                    out.set(jx, 1, jz + 1, "trash_bag[facing=north]");
                }
            }
        }
        kit.abandonedCars(loop, 1.5);
        for (Kit.Lane l : kit.lanes) {
            kit.streetTrees(l, 13, 0.3);
        }
        kit.woods(0.4);
        kit.overgrow(0.9);
    }

    private void sign(int x, int z) {
        // two posts and the six panels of SHADY ACRES MOBILE HOME PARK, facing the gate road
        if (!kit.inside(x + 7, z)) {
            return;
        }
        for (int y = 1; y <= 3; y++) {
            out.set(x, y, z, "pole_wood");
            out.set(x + 7, y, z, "pole_wood");
        }
        // read from the south, so slice 0 (the left end) is the west end
        for (int i = 0; i < 6; i++) {
            if (rng.nextDouble() > 0.1) {
                out.set(x + 1 + i, 3, z, "sign_shadyacres_" + i + "[facing=north]");
            }
        }
        out.set(x + 3, 1, z, "minecraft:tall_grass[half=lower]");
        out.set(x + 3, 2, z, "minecraft:tall_grass[half=upper]");
        kit.claim(x, z, x + 7, z, Kit.BUILT);
    }

    /** A bank of mailboxes on a post rail by the gate. */
    private void mailboxes(int x, int z) {
        for (int i = 0; i < 6; i++) {
            if (!kit.inside(x + i, z) || kit.use(x + i, z) == Kit.ROAD) {
                continue;
            }
            out.set(x + i, 1, z, rng.nextDouble() < 0.15 ? "minecraft:spruce_fence" : "mailbox[facing=south]");
            kit.claim(x + i, z, x + i, z, Kit.BUILT);
        }
    }

    /** The laundry shed: corrugated walls, washers and dryers, a soda machine. */
    private void laundry(int x, int z) {
        int lw = 9, ld = 6;
        if (!kit.free(x - 1, z - 1, x + lw, z + ld)) {
            return;
        }
        kit.claim(x, z, x + lw - 1, z + ld - 1, Kit.BUILT);
        Placer p = new Placer(out, x, 0, z, 0);
        for (int i = 0; i < lw; i++) {
            for (int j = 0; j < ld; j++) {
                boolean edge = i == 0 || j == 0 || i == lw - 1 || j == ld - 1;
                p.set(i, 0, j, "floor_concrete");
                for (int y = 1; y <= 3; y++) {
                    p.set(i, y, j, edge ? (y == 2 && j == 0 && i % 3 == 1 ? "window_broken[axis=x]" : "corrugated_grey") : "air");
                }
                p.set(i, 4, j, "corrugated_rust");
            }
        }
        p.set(4, 1, 0, "air");
        p.set(4, 2, 0, "air");
        for (int i = 1; i < lw - 1; i++) {
            if (i != 4) {
                p.set(i, 1, ld - 2, (i % 2 == 0 ? "store_washer" : "store_dryer") + "[facing=north]");
            }
        }
        p.set(1, 1, 1, "store_vending[facing=east]");
        p.set(1, 2, 1, "store_vending_top[facing=east]");
        p.set(lw - 2, 1, 1, "store_table");
    }
}
