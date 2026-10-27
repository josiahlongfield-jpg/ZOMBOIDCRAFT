package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Supplier;

/**
 * What the Glass Line's riders see through the glass (the sights on the route map Josiah picked,
 * project files previews/rail/48-glass-line-route-map.jpg), lying on the South Arm's bed beside
 * the tubes:
 * <ul>
 * <li>a feeder ship that went down at her anchorage beside the reach, settled deep in the mud,
 * heeled over toward the channel and down by the stern: her deck awash, her house and its bridge
 * standing out of the water, the hole that sank her torn in her side;</li>
 * <li>a harbour tug and the coal barge it had on its hip, gone down together at the foot of the
 * island's bank, the coal spilled from the barge's hold across the bed toward the channel;</li>
 * <li>the cars and the city bus that went off the highway bridge when it jammed, on the bottom
 * either side of it.</li>
 * </ul>
 * Each is drawn once into its own blocks in world coordinates (settled on the bed, sheared to its
 * heel and trim, everything left open under the water flooded) and the chunks copy their share
 * out, as they do a place's.
 */
final class Wrecks {
    /** The water's top block. */
    private static final int SURFACE = Terrain.WATER - 1;
    /** The freighter: her stern, the line of her keel, her length and beam. */
    private static final int SHIP_X = 3075, SHIP_Z = -28, SHIP_LEN = 150, SHIP_BEAM = 26;
    /** The barge (its west end, the line of its keel) and the tug on its hip, to its north. */
    private static final int BARGE_X = 2900, BARGE_Z = -58, BARGE_LEN = 32, BARGE_BEAM = 12;
    private static final int TUG_X = 2907, TUG_Z = -70, TUG_LEN = 22;
    /**
     * The cars off the highway bridge: {world z, blocks to the right of the road's middle (it runs
     * south: the right is the west), yaw from the road's heading}, the bus first. Either side of
     * the tubes' crossing, clear of the bank the road raises round its piers and of the channel.
     */
    private static final int[][] OFF_BRIDGE = {{56, -21, 65}, {40, -19, 20}, {70, -18, -110}, {30, -22, 80}, {64, -25, 140},
            {47, -27, 160}, {30, 19, 75}, {120, 18, 35}, {125, 24, -60}};

    private final County county;
    private volatile List<Wreck> list;

    Wrecks(County county) {
        this.county = county;
    }

    /** Copies the wrecks' blocks and vehicles inside the chunk at (bx, bz) into out. */
    void copy(int bx, int bz, ChunkSink out) {
        for (Wreck w : list()) {
            if (w.x1 < bx || w.x0 > bx + 15 || w.z1 < bz || w.z0 > bz + 15) {
                continue;
            }
            Voxels v = w.voxels();
            List<String> palette = v.palette();
            v.forColumn(bx >> 4, bz >> 4, (x, y, z, id) -> out.set(x, y, z, palette.get(id)));
            for (Voxels.Car car : v.cars) {
                if (car.x() >= bx && car.x() < bx + 16 && car.z() >= bz && car.z() < bz + 16) {
                    // wrecked, whatever the shares of the county's cars say: they've lain in the harbour since
                    out.vehicle(car.x(), car.y(), car.z(), car.yaw(), car.models() + "@wrecked", car.seed());
                }
            }
        }
    }

    private List<Wreck> list() {
        List<Wreck> l = list;
        if (l == null) {
            synchronized (this) {
                if (list == null) {
                    list = plan();
                }
                l = list;
            }
        }
        return l;
    }

    /** Where they lie: only in the planned county, under the Glass Line. */
    private List<Wreck> plan() {
        List<Wreck> l = new ArrayList<>();
        if (county.tracks().stream().noneMatch(t -> t.id.equals(GlassLine.ID))) {
            return l;
        }
        int hb = SHIP_BEAM / 2 + 3;
        l.add(new Wreck(SHIP_X - 6, SHIP_Z - hb - 8, SHIP_X + SHIP_LEN + 6, SHIP_Z + hb + 8, this::freighter));
        l.add(new Wreck(BARGE_X - 4, BARGE_Z - BARGE_BEAM / 2 - 4, BARGE_X + BARGE_LEN + 18, BARGE_Z + 24, this::barge));
        l.add(new Wreck(TUG_X - 4, TUG_Z - 10, TUG_X + TUG_LEN + 4, TUG_Z + 8, this::tug));
        List<double[]> cars = offBridge();
        if (!cars.isEmpty()) {
            double x0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
            for (double[] c : cars) {
                x0 = Math.min(x0, c[0]);
                z0 = Math.min(z0, c[1]);
                x1 = Math.max(x1, c[0]);
                z1 = Math.max(z1, c[1]);
            }
            l.add(new Wreck((int) x0 - 8, (int) z0 - 8, (int) x1 + 8, (int) z1 + 8, () -> cars(cars)));
        }
        return l;
    }

    /** One wreck: the box it lies in, and how it's drawn (once, the first time a chunk wants it). */
    private static final class Wreck {
        final int x0, z0, x1, z1;
        private final Supplier<Voxels> draw;
        private volatile Voxels voxels;

        Wreck(int x0, int z0, int x1, int z1, Supplier<Voxels> draw) {
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.draw = draw;
        }

        Voxels voxels() {
            Voxels v = voxels;
            if (v == null) {
                synchronized (this) {
                    if (voxels == null) {
                        voxels = draw.get();
                    }
                    v = voxels;
                }
            }
            return v;
        }
    }

    // ------------------------------------------------------------------ the bed

    /**
     * The top of the bed at (x, z) as the chunk lays it: the land with the places levelled in, raised
     * toward a bridge's road round its piers, and the Glass Line's channel dredged through it.
     */
    private int bed(int x, int z) {
        List<Place> places = county.placesNear(x, z, x, z, County.SKIRT);
        double natural = county.ground(x, z, places), h = natural;
        Road.Hit hit = new Road.Hit();
        for (Road r : county.roadsNear(x - 16, z - 16, x + 16, z + 16)) {
            r.nearest(x + 0.5, z + 0.5, hit);
        }
        boolean bridge = false;
        if (hit.road != null) {
            int half = hit.road.kind.half;
            if (hit.distance <= half + 1.5) {
                if (Terrain.isWater(natural) || natural < hit.y - 4) {
                    bridge = true;
                } else {
                    h = hit.y;
                }
            } else if (hit.distance <= half + 9) {
                h = Terrain.lerp(h, hit.y, Terrain.smooth(half + 9, half + 1.5, hit.distance));
            }
        }
        RailGen rails = RailGen.near(county, x & ~15, z & ~15);
        if (rails != null) {
            h = bridge ? rails.trench(x, z, h) : rails.ground(x, z, h, new boolean[1]);
        }
        return (int) Math.floor(h);
    }

    // ------------------------------------------------------------------ the freighter

    /**
     * A feeder ship 150 long, bow to the east: settled till her keel is nine blocks down in the
     * mud and her deck awash amidships, heeled toward the channel and down by the stern, the hole
     * that sank her torn in her side under the water on the channel side.
     */
    private Voxels freighter() {
        SplittableRandom rng = new SplittableRandom(county.seed ^ 0xF4E16E7L);
        // the frame's 0 is a quay's top, her deck 9 over it, her keel 13 under
        Sunk s = new Sunk(SHIP_X + SHIP_LEN / 2.0, SHIP_Z, 0.35, 0.035);
        new BigShip(new Placer(s, SHIP_X, SURFACE - 13, SHIP_Z, 0), rng, 0.9, 0).container(SHIP_LEN, SHIP_BEAM, null);
        int gx = SHIP_X + SHIP_LEN * 2 / 5;
        gash(s.v, rng, gx, bed(gx, SHIP_Z + SHIP_BEAM / 2 + 1) + 5, SHIP_Z + SHIP_BEAM / 2 + 2, -1, 8, 3.5);
        return s.flood();
    }

    /**
     * Tears a hole through a side's plating round (cx, cy): scanning in from z0 the way dir runs,
     * the plate and the lining behind it gone inside the ragged edge, rusted round it.
     */
    private static void gash(Voxels v, SplittableRandom rng, int cx, int cy, int z0, int dir, double rx, double ry) {
        for (int x = (int) (cx - rx - 2); x <= cx + rx + 2; x++) {
            for (int y = (int) (cy - ry - 2); y <= cy + ry + 2; y++) {
                double r = Math.hypot((x - cx) / rx, (y - cy) / ry) + (rng.nextDouble() - 0.5) * 0.35;
                if (r >= 1.4) {
                    continue;
                }
                int z = z0;
                for (int k = 0; k < 12 && plain(v.get(x, y, z)); k++) {
                    z += dir;
                }
                if (plain(v.get(x, y, z))) {
                    continue;
                }
                if (r < 1) {
                    for (int k = 0; k < 3; k++) {
                        String b = v.get(x, y, z + k * dir);
                        if (b != null && (b.startsWith("vessel_") || b.equals("ship_hold") || b.equals("rust"))) {
                            v.set(x, y, z + k * dir, "air");
                        }
                    }
                } else if (rng.nextDouble() < 0.7) {
                    v.set(x, y, z, "rust");
                }
            }
        }
    }

    private static boolean plain(String b) {
        return b == null || b.equals("air") || b.equals("minecraft:water");
    }

    // ------------------------------------------------------------------ the tug and the barge

    /**
     * The coal barge, an open box of a hull heaped with coal, listing toward the channel, and the
     * coal that slid off it fanned out over the bed to the south-east.
     */
    private Voxels barge() {
        SplittableRandom rng = new SplittableRandom(county.seed ^ 0xBA26EL);
        int bed = bed(BARGE_X + BARGE_LEN / 2, BARGE_Z);
        Sunk s = new Sunk(BARGE_X + BARGE_LEN / 2.0, BARGE_Z, 0.12, -0.03);
        // its keel a block into the mud
        Placer p = new Placer(s, BARGE_X, bed, BARGE_Z, 0);
        int hb = BARGE_BEAM / 2, deck = 6;
        Ships.Hull h = new Ships.Hull(BARGE_LEN, BARGE_BEAM, 0, deck, 0.05, 0.04);
        new Ships(p, rng, 0.9).hull(h, "vessel_hull_black", "vessel_bottom", "vessel_deck_grey", 2, false);
        for (int x = 2; x < BARGE_LEN - 2; x++) {
            for (int z = -hb + 1; z < hb - 1; z++) {
                boolean rim = x == 2 || x == BARGE_LEN - 3 || z == -hb + 1 || z == hb - 2;
                if (rim) {
                    p.set(x, deck + 1, z, rng.nextDouble() < 0.1 ? "rust" : "vessel_coaming_grey");
                    continue;
                }
                p.set(x, deck, z, "air");
                double heap = 4 - Math.abs(x - BARGE_LEN / 2.0) / (BARGE_LEN / 2.0) * 2 - Math.abs(z + 0.5) / hb * 1.5 + rng.nextDouble() * 0.8;
                for (int y = 1; y <= deck + 1; y++) {
                    p.set(x, y, z, y <= Math.max(1, (int) heap + 3) ? "gl_coal" : "air");
                }
            }
        }
        // the bitts the tug's lines are made fast to, and the lines across to it
        for (int x : new int[] {8, 22}) {
            p.set(x, deck + 1, -hb, "vessel_bollard[axis=x]");
            for (int z = BARGE_Z - hb - 1; z >= TUG_Z + 4; z--) {
                s.v.set(BARGE_X + x, bed + deck, z, "vessel_rope[axis=z]");
            }
        }
        // the coal it shed as it went over, fanned out down the bed
        double fx = BARGE_X + BARGE_LEN * 0.6, fz = BARGE_Z + hb + 3;
        for (int x = (int) fx - 16; x <= fx + 18; x++) {
            for (int z = (int) fz - 4; z <= fz + 18; z++) {
                double u = (x - fx) / 15.0, w = (z - fz) / 13.0, r = Math.hypot(u, w);
                double hgt = 2.6 * (1 - r) + rng.nextDouble() * 0.9 - 0.3;
                if (r >= 1 || hgt < 0.5 || s.v.get(x, bed + 1, z) != null) {
                    continue;
                }
                int b = bed(x, z);
                for (int y = b + 1; y <= b + (int) Math.round(hgt); y++) {
                    s.v.set(x, y, z, "gl_coal");
                }
            }
        }
        return s.flood();
    }

    /**
     * The harbour tug that had the barge on its hip: heeled hard over away from it, the top of its
     * wheelhouse just under the surface and its mast standing out; tyre fenders round its sheer,
     * the funnel aft of the wheelhouse, the towing winch on its after deck.
     */
    private Voxels tug() {
        SplittableRandom rng = new SplittableRandom(county.seed ^ 0x7A6L);
        int bed = bed(TUG_X + TUG_LEN / 2, TUG_Z), len = TUG_LEN, hb = 4, deck = 8;
        Sunk s = new Sunk(TUG_X + len / 2.0, TUG_Z, -0.3, 0.02);
        Placer p = new Placer(s, TUG_X, bed - 1, TUG_Z, 0);
        Ships.Hull h = new Ships.Hull(len, 2 * hb, 0, deck, 0.32, 0.3);
        String skin = rng.nextDouble() < 0.5 ? "vessel_hull_black" : "vessel_hull_maroon";
        new Ships(p, rng, 0.9).hull(h, skin, "vessel_bottom", "vessel_deck_red", 4, false);
        for (int x = 0; x < len; x++) {
            for (int z = -hb - 1; z <= hb; z++) {
                if (!h.in(x, deck, z)) {
                    continue;
                }
                boolean side = !h.in(x, deck, z + 1) || !h.in(x, deck, z - 1), end = !h.in(x + 1, deck, z) || !h.in(x - 1, deck, z);
                if (side || end) {
                    p.set(x, deck + 1, z, skin);
                    p.set(x, deck - 1, z, "tire[axis=" + (side && !end ? "x" : "z") + "]");
                }
            }
        }
        // the wheelhouse: a deck of cabin, the wheelhouse over it with windows all round
        int wx0 = len / 2 - 2, wx1 = wx0 + 5, roof = deck + 7;
        for (int y = deck + 1; y < roof; y++) {
            int r = (y - deck - 1) % 3;
            boolean top = y > deck + 3;
            for (int x = wx0; x <= wx1; x++) {
                for (int z = -2; z <= 1; z++) {
                    boolean wall = x == wx0 || x == wx1 || z == -2 || z == 1;
                    boolean corner = (x == wx0 || x == wx1) && (z == -2 || z == 1);
                    if (!wall) {
                        p.set(x, y, z, r == 0 ? "floor_lino" : "air");
                        continue;
                    }
                    String facing = x == wx1 ? "east" : x == wx0 ? "west" : z < 0 ? "north" : "south";
                    boolean pane = !corner && r == 1 && (top || (x - wx0) % 2 == 1 && x != wx0);
                    String glass = top && x == wx1 ? "vessel_bridge" : "vessel_window";
                    p.set(x, y, z, pane ? glass + (rng.nextDouble() < 0.6 ? "_broken" : "") + "[facing=" + facing + "]"
                            : rng.nextDouble() < 0.25 ? "vessel_house_rust" : "vessel_house");
                }
            }
        }
        for (int x = wx0; x <= wx1; x++) {
            for (int z = -2; z <= 1; z++) {
                p.set(x, roof, z, "vessel_roof");
            }
        }
        Doors.abandoned(p, rng, wx0, deck + 1, 0, "ship_door", "west", Doors.hinge(rng));
        p.set(wx0 + 2, deck + 2, 2, "ship_lifebuoy[facing=south]");
        for (int y = roof + 1; y <= roof + 5; y++) {
            p.set(wx0 + 3, y, -1, "vessel_mast");
        }
        p.set(wx0 + 3, roof + 6, -1, "vessel_light_white[facing=east]");
        p.set(wx0 + 2, roof + 4, -1, "vessel_yard[axis=z]");
        // the funnel aft of it, sooted at the top
        String funnel = rng.nextDouble() < 0.5 ? "vessel_funnel_red" : "vessel_funnel_black";
        for (int y = deck + 1; y <= deck + 7; y++) {
            for (int x = wx0 - 3; x <= wx0 - 2; x++) {
                for (int z = -1; z <= 0; z++) {
                    p.set(x, y, z, y > deck + 5 ? "vessel_funnel_soot" : funnel);
                }
            }
        }
        // the towing winch and the bitts aft, the bitts on the side the barge was made fast to
        p.set(3, deck + 1, -1, "vessel_winch[axis=z]");
        p.set(3, deck + 1, 0, "vessel_winch[axis=z]");
        p.set(1, deck + 1, 0, "vessel_bollard[axis=x]");
        p.set(len - 4, deck + 1, 0, "vessel_bollard[axis=x]");
        for (int x : new int[] {BARGE_X + 8 - TUG_X, BARGE_X + 22 - TUG_X}) {
            p.set(x, deck + 1, hb - 1, "vessel_bollard[axis=x]");
        }
        return s.flood();
    }

    // ------------------------------------------------------------------ off the bridge

    /** Where the cars off the highway bridge lie: {x, z, yaw, index}, none without the bridge. */
    private List<double[]> offBridge() {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < OFF_BRIDGE.length; i++) {
            int[] c = OFF_BRIDGE[i];
            Road.Hit hit = new Road.Hit();
            for (Road r : county.roadsNear(3340, c[0] - 20, 3440, c[0] + 20)) {
                if (r.kind == Road.Kind.HIGHWAY) {
                    r.nearest(3390.5, c[0] + 0.5, hit);
                }
            }
            if (hit.road == null || hit.distance > 40) {
                continue;
            }
            // the road's middle level with the bridge, then out to the side
            double mx = 3390.5 + hit.offset * hit.dz, mz = c[0] + 0.5 - hit.offset * hit.dx;
            double x = mx - hit.dz * c[1], z = mz + hit.dx * c[1];
            float yaw = (float) Math.toDegrees(Math.atan2(-hit.dx, hit.dz)) + c[2];
            out.add(new double[] {x, z, yaw, i});
        }
        return out;
    }

    /** The bus and the cars on the bed, where it's level enough to take them. */
    private Voxels cars(List<double[]> at) {
        Voxels v = new Voxels();
        for (double[] c : at) {
            int x = (int) Math.floor(c[0]), z = (int) Math.floor(c[1]);
            int b = bed(x, z);
            if (!Terrain.isWater(b) || b < Terrain.WATER - 18 || Math.abs(bed(x + 3, z) - b) > 1 || Math.abs(bed(x, z + 3) - b) > 1) {
                continue;
            }
            boolean bus = c[3] == 0;
            v.vehicle(c[0], b + 1, c[1], (float) c[2], bus ? "city_bus" : "car", county.seed ^ (long) c[3] * 0x9E3779B97F4A7C15L);
        }
        return v;
    }

    // ------------------------------------------------------------------ drawing a wreck

    /**
     * Draws a wreck in world coordinates into its own blocks, sheared to lie heeled over (down on
     * the +z side heel blocks a block) and trimmed (down toward -x trim blocks a block) about (cx, cz).
     */
    private static final class Sunk implements BlockSink {
        final Voxels v = new Voxels();
        private final double cx, cz, heel, trim;

        Sunk(double cx, double cz, double heel, double trim) {
            this.cx = cx;
            this.cz = cz;
            this.heel = heel;
            this.trim = trim;
        }

        private int drop(int x, int z) {
            return (int) Math.floor(trim * (x + 0.5 - cx) - heel * (z + 0.5 - cz) + 0.5);
        }

        @Override
        public void set(int x, int y, int z, String block) {
            v.set(x, y + drop(x, z), z, block);
        }

        @Override
        public String get(int x, int y, int z) {
            return v.get(x, y + drop(x, z), z);
        }

        /** Floods everything left open under the water, and hands the blocks over. */
        Voxels flood() {
            List<int[]> open = new ArrayList<>();
            v.forEach((x, y, z, id) -> {
                if (id == 0 && y <= SURFACE) {
                    open.add(new int[] {x, y, z});
                }
            });
            for (int[] c : open) {
                v.set(c[0], c[1], c[2], "minecraft:water");
            }
            return v;
        }
    }
}
