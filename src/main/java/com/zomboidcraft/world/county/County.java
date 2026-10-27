package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Plans the county: which places stand where, and the roads between them.
 *
 * A county world follows the master plan Josiah approved ({@link Plan}, laid out by
 * {@link Layout}): Grayhaven Island and the walled city round it, the towns, ports and bases where
 * the map puts them, suburbs, farms and woods between, the highways and bridges. The seed only
 * decides how each place is built.
 *
 * {@link #endless(long)} is the county before the plan, still used to try builders out: the world is cut into {@link #CELL}-block cells. Each cell holds at most one place, picked by
 * how built-up the district is (towns and suburbs near cities, farms and woods in the country),
 * jittered inside the cell and levelled to the land around it. Every few cells a big city takes
 * over a whole block of cells. Each place is joined to its nearest neighbours by highways, county
 * roads or farm tracks. All of it is a pure function of the seed, cached as chunks ask for it,
 * so any chunk can be generated on its own in any order.
 */
public final class County {
    public static final int CELL = 512;
    /** A downtown is planned once per MACRO x MACRO cells. */
    public static final int MACRO = 6;
    /** How far a place's levelling blends into the land around it. */
    public static final int SKIRT = 40;
    private static final int LINK_RANGE = 1500;

    public final long seed;
    public final Terrain terrain;
    /** The planned county's layout, made the first time anything asks where things are. */
    private volatile Layout layout;
    private volatile boolean laidOut;
    private final Map<Long, Optional<Place>> candidates = new ConcurrentHashMap<>();
    private final Map<Long, Optional<Place>> places = new ConcurrentHashMap<>();
    private final Map<Long, Optional<Place>> cities = new ConcurrentHashMap<>();
    private Optional<Place> stadium;
    private final Map<Long, List<Road>> roads = new ConcurrentHashMap<>();
    private final Map<Long, List<Road>> joins = new ConcurrentHashMap<>();
    private final Map<Long, Optional<Place>> hideouts = new ConcurrentHashMap<>();

    /** The planned county. */
    public County(long seed) {
        this(seed, true);
    }

    /** The endless county the seed alone lays out, one place to a cell. */
    public static County endless(long seed) {
        return new County(seed, false);
    }

    private County(long seed, boolean planned) {
        this.seed = seed;
        this.terrain = new Terrain(seed, planned ? Plan.get() : null);
    }

    public boolean planned() {
        return terrain.plan != null;
    }

    /**
     * The layout, made once (the thread making it gets the half-made one back while it works; any
     * other waits for it to finish).
     */
    Layout layout() {
        if (laidOut) {
            return layout;
        }
        synchronized (this) {
            if (layout == null) {
                new Layout(this);
                laidOut = true;
            }
            return layout;
        }
    }

    /** Called by the layout as it starts, so the roads it builds can already find its places. */
    void layingOut(Layout l) {
        layout = l;
    }

    /** The planned county's train lines, the railway and the island's subway (none in the endless one). */
    public List<Track> tracks() {
        if (!planned()) {
            return List.of();
        }
        Railway r = layout().railway;
        return r == null ? List.of() : r.tracks;
    }

    /** Every place in the planned county (none in the endless one). */
    public List<Place> allPlaces() {
        return planned() ? List.copyOf(layout().places) : List.of();
    }

    /**
     * True on Grayhaven Island, the county's biggest landmark: between the river's two arms, west
     * of the sea. Only the planned county has it; ask {@link #island} of a world's county when it
     * may be an older, endless one.
     */
    public static boolean onIsland(int x, int z) {
        return Plan.onIsland(x + 0.5, z + 0.5);
    }

    /** {@link #onIsland} in this county: never in the endless one. */
    public boolean island(int x, int z) {
        return planned() && onIsland(x, z);
    }

    static long key(int a, int b) {
        return (long) a << 32 | (b & 0xFFFFFFFFL);
    }

    double rand(long salt, int a, int b) {
        return Noise.unit(Noise.hash(seed ^ salt, a, b));
    }

    // ------------------------------------------------------------------ places

    /** The place a cell holds, if any (its centre lies in the cell). */
    public Optional<Place> place(int cx, int cz) {
        return places.computeIfAbsent(key(cx, cz), k -> Optional.ofNullable(resolve(cx, cz)));
    }

    /** Every place whose footprint plus margin touches the box. */
    public List<Place> placesNear(int x0, int z0, int x1, int z1, int margin) {
        if (planned()) {
            return layout().placesNear(x0, z0, x1, z1, margin);
        }
        List<Place> out = new ArrayList<>();
        int reach = 360 + margin;
        for (int cx = Math.floorDiv(x0 - reach, CELL); cx <= Math.floorDiv(x1 + reach, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - reach, CELL); cz <= Math.floorDiv(z1 + reach, CELL); cz++) {
                place(cx, cz).ifPresent(p -> {
                    if (p.x0 - margin <= x1 && p.x1 + margin >= x0 && p.z0 - margin <= z1 && p.z1 + margin >= z0) {
                        out.add(p);
                    }
                });
            }
        }
        return out;
    }

    /** A cell's place, unless it overlaps a neighbour's place that outranks it. */
    private Place resolve(int cx, int cz) {
        Place p = candidate(cx, cz).orElse(null);
        if (p == null) {
            return null;
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Place o = candidate(cx + dx, cz + dz).orElse(null);
                if (o != null && o != p && p.overlaps(o, 24) && outranks(o, cx + dx, cz + dz, p, cx, cz)) {
                    return null;
                }
            }
        }
        return p;
    }

    private static int rank(PlaceType t) {
        return switch (t) {
            case DOWNTOWN -> 6;
            case LANDMARK, PORT -> 5;
            case MILITARY -> 4;
            case TOWN -> 3;
            case SUBURB -> 2;
            case FARM -> 1;
            case ROADSIDE, HIDEOUT -> 0;
        };
    }

    private boolean outranks(Place a, int ax, int az, Place b, int bx, int bz) {
        if (rank(a.type) != rank(b.type)) {
            return rank(a.type) > rank(b.type);
        }
        return Noise.hash(seed, ax, az) > Noise.hash(seed, bx, bz);
    }

    private Optional<Place> candidate(int cx, int cz) {
        return candidates.computeIfAbsent(key(cx, cz), k -> Optional.ofNullable(plan(cx, cz)));
    }

    /** The downtown planned for a macro region, if it has one. */
    private Optional<Place> city(int mx, int mz) {
        return cities.computeIfAbsent(key(mx, mz), k -> Optional.ofNullable(planCity(mx, mz)));
    }

    private Place planCity(int mx, int mz) {
        if (rand(0xC171L, mx, mz) > 0.8) {
            return null;
        }
        // try the macro's inner cells in a seeded order until one has dry land for the city;
        // never the spawn town's cell or its neighbours
        String[] variants = {"metro", "city", "old_city"};
        int[] sizes = {560, 456, 384};
        int v = (int) (rand(0xC174L, mx, mz) * 3);
        int inner = MACRO - 2;
        int start = (int) (rand(0xC172L, mx, mz) * inner * inner);
        for (int tryV = v; tryV < 3; tryV++) {
            for (int i = 0; i < inner * inner; i++) {
                int k = (start + i * 7) % (inner * inner);
                int cx = mx * MACRO + 1 + k % inner, cz = mz * MACRO + 1 + k / inner;
                if (Math.abs(cx) <= 1 && Math.abs(cz) <= 1) {
                    continue;
                }
                int px = cx * CELL + CELL / 2, pz = cz * CELL + CELL / 2;
                Place p = fit(PlaceType.DOWNTOWN, variants[tryV], px, pz, sizes[tryV], sizes[tryV], Noise.hash(seed, cx, cz));
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }

    /**
     * The county's one stadium, on a lot of its own a cell east or west of the downtown nearest
     * the spawn (whichever side is dry), pushed a little further out so the city's levelling and
     * roads keep clear of it. Until the county plan puts it on the island.
     */
    private synchronized Optional<Place> stadium() {
        if (stadium == null) {
            stadium = Optional.empty();
            List<Place> downtowns = new ArrayList<>();
            int reach = Math.floorDiv(8192, CELL * MACRO) + 1;
            for (int mx = -reach; mx <= reach; mx++) {
                for (int mz = -reach; mz <= reach; mz++) {
                    city(mx, mz).ifPresent(downtowns::add);
                }
            }
            downtowns.sort(Comparator.comparingDouble(c -> Math.hypot(c.centreX(), c.centreZ())));
            for (Place c : downtowns) {
                int dcx = Math.floorDiv(c.centreX(), CELL), dcz = Math.floorDiv(c.centreZ(), CELL);
                int side = rand(0x57AD1L, dcx, dcz) < 0.5 ? -1 : 1;
                for (int sd : new int[] {side, -side}) {
                    int px = (dcx + sd) * CELL + CELL / 2 + sd * 40, pz = dcz * CELL + CELL / 2;
                    Place p = fit(PlaceType.LANDMARK, "stadium", px, pz, 400, 450, Noise.hash(seed, dcx + sd, dcz));
                    if (p != null) {
                        stadium = Optional.of(p);
                        return stadium;
                    }
                }
            }
        }
        return stadium;
    }

    /** The downtown owning this cell, or the nearest downtown's distance (for suburbs). */
    private double cityDistance(int px, int pz) {
        double best = Double.MAX_VALUE;
        int mx = Math.floorDiv(Math.floorDiv(px, CELL), MACRO), mz = Math.floorDiv(Math.floorDiv(pz, CELL), MACRO);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Optional<Place> c = city(mx + dx, mz + dz);
                if (c.isPresent()) {
                    best = Math.min(best, c.get().outside(px, pz));
                }
            }
        }
        return best;
    }

    private Place plan(int cx, int cz) {
        // a downtown claims its cell outright
        int mx = Math.floorDiv(cx, MACRO), mz = Math.floorDiv(cz, MACRO);
        Optional<Place> c = city(mx, mz);
        if (c.isPresent() && Math.floorDiv(c.get().centreX(), CELL) == cx && Math.floorDiv(c.get().centreZ(), CELL) == cz) {
            return c.get();
        }
        Optional<Place> st = stadium();
        if (st.isPresent() && Math.floorDiv(st.get().centreX(), CELL) == cx && Math.floorDiv(st.get().centreZ(), CELL) == cz) {
            return st.get();
        }
        long h = Noise.hash(seed, cx, cz);
        int px = cx * CELL + 100 + (int) (rand(0xA1L, cx, cz) * (CELL - 200));
        int pz = cz * CELL + 100 + (int) (rand(0xA2L, cx, cz) * (CELL - 200));
        if (cx == 0 && cz == 0) {
            // the spawn: always a town, centred on the world origin
            Place p = fit(PlaceType.TOWN, "main_street", 0, 0, 240, 200, h);
            return p != null ? p : fit(PlaceType.TOWN, "hamlet", 0, 0, 150, 150, h);
        }
        double u = terrain.urban(px, pz);
        double city = cityDistance(px, pz);
        double nearCity = city < 900 ? 1 - city / 900 : 0;
        double shore = shoreDistance(px, pz);

        double[] w = new double[8];
        w[0] = 0.30 - 0.12 * u - nearCity * 0.3;          // wild woods, nothing built
        w[1] = 0.10 + 0.14 * Math.max(u, 0);               // town
        w[2] = 0.05 + 0.18 * Math.max(u, 0) + 1.2 * nearCity; // suburb
        w[3] = Math.max(0.05, 0.32 - 0.2 * u - nearCity);  // farm
        w[4] = shore < 200 && rand(0xA5L, cx, cz) < 0.45 ? 0.3 + 0.4 * nearCity : 0;    // port
        w[5] = u < 0.4 && nearCity == 0 ? 0.045 : 0;       // military
        w[6] = 0.12;                                       // roadside stop
        double total = 0;
        for (double v : w) {
            total += Math.max(0, v);
        }
        double r = rand(0xA3L, cx, cz) * total;
        int pick = 0;
        for (; pick < w.length - 1; pick++) {
            r -= Math.max(0, w[pick]);
            if (r < 0) {
                break;
            }
        }
        double v = rand(0xA4L, cx, cz);
        return switch (pick) {
            case 1 -> town(px, pz, v, h);
            case 2 -> suburb(px, pz, v, h);
            case 3 -> farm(px, pz, v, h);
            case 4 -> port(px, pz, v, h);
            case 5 -> military(px, pz, v, h);
            case 6 -> roadside(px, pz, v, h);
            default -> null;
        };
    }

    private Place town(int px, int pz, double v, long h) {
        if (v < 0.30) {
            return fitOrShrink(PlaceType.TOWN, "hamlet", px, pz, 150, 150, h);
        } else if (v < 0.62) {
            return fitOrShrink(PlaceType.TOWN, "main_street", px, pz, 240, 200, h);
        } else if (v < 0.84) {
            return fitOrShrink(PlaceType.TOWN, "county_seat", px, pz, 300, 300, h);
        }
        return fitOrShrink(PlaceType.TOWN, "mill_town", px, pz, 260, 230, h);
    }

    private Place suburb(int px, int pz, double v, long h) {
        if (v < 0.35) {
            return fitOrShrink(PlaceType.SUBURB, "cul_de_sac", px, pz, 260, 240, h);
        } else if (v < 0.65) {
            return fitOrShrink(PlaceType.SUBURB, "old_grid", px, pz, 240, 220, h);
        } else if (v < 0.85) {
            return fitOrShrink(PlaceType.SUBURB, "estates", px, pz, 300, 260, h);
        }
        return fitOrShrink(PlaceType.SUBURB, "trailer_park", px, pz, 150, 130, h);
    }

    private Place farm(int px, int pz, double v, long h) {
        if (v < 0.30) {
            return fitOrShrink(PlaceType.FARM, "crop", px, pz, 220, 200, h);
        } else if (v < 0.52) {
            return fitOrShrink(PlaceType.FARM, "dairy", px, pz, 200, 180, h);
        } else if (v < 0.70) {
            return fitOrShrink(PlaceType.FARM, "horse_ranch", px, pz, 220, 210, h);
        } else if (v < 0.85) {
            return fitOrShrink(PlaceType.FARM, "orchard", px, pz, 180, 170, h);
        }
        return fit(PlaceType.FARM, "homestead", px, pz, 130, 120, h);  // any smaller and the crossroads leave no room for the yard
    }

    private Place military(int px, int pz, double v, long h) {
        // each one its own size, a tenth either way
        double s = 0.9 + ((h >>> 24) & 31) / 31.0 * 0.2;
        if (v < 0.4) {
            return fitOrShrink(PlaceType.MILITARY, "quarantine", px, pz, (int) (210 * s), (int) (190 * s), h);
        } else if (v < 0.75) {
            return fitOrShrink(PlaceType.MILITARY, "army_base", px, pz, (int) (280 * s), (int) (260 * s), h);
        }
        return fitOrShrink(PlaceType.MILITARY, "depot", px, pz, (int) (190 * s), (int) (170 * s), h);
    }

    private Place roadside(int px, int pz, double v, long h) {
        String[] kinds = {"gas_station", "motel", "diner", "church", "school", "warehouse", "trailer_lot"};
        int[][] sizes = {{90, 108}, {100, 112}, {90, 108}, {96, 128}, {150, 190}, {104, 136}, {90, 116}};
        int k = (int) (v * kinds.length);
        return fitOrShrink(PlaceType.ROADSIDE, kinds[k], px, pz, sizes[k][0], sizes[k][1], h);
    }

    Place fitOrShrink(PlaceType type, String variant, int px, int pz, int w, int d, long h) {
        Place p = fit(type, variant, px, pz, w, d, h);
        return p != null ? p : fit(type, variant, px, pz, w * 3 / 4, d * 3 / 4, h);
    }

    /** A footprint centred on (px, pz) if the land there is dry, levelled to its average height. */
    Place fit(PlaceType type, String variant, int px, int pz, int w, int d, long h) {
        int x0 = px - w / 2, z0 = pz - d / 2, x1 = x0 + w - 1, z1 = z0 + d - 1;
        double sum = 0;
        int n = 0;
        for (int i = 0; i <= 6; i++) {
            for (int j = 0; j <= 6; j++) {
                double x = x0 + (x1 - x0) * i / 6.0, z = z0 + (z1 - z0) * j / 6.0;
                double g = terrain.height(x, z);
                if (Terrain.isWater(g - 1) || terrain.riverChannel(x, z)) {
                    return null;
                }
                sum += g;
                n++;
            }
        }
        int level = (int) Math.round(sum / n);
        return new Place(type, variant, x0, z0, x1, z1, Math.max(level, Terrain.WATER + 2), h, -1, -1);
    }

    /** Distance from (px, pz) to open lake water, up to 320 (checked on eight rays). */
    private double shoreDistance(int px, int pz) {
        double best = 1e9;
        for (int a = 0; a < 8; a++) {
            double dx = Math.cos(a * Math.PI / 4), dz = Math.sin(a * Math.PI / 4);
            for (int s = 0; s <= 320; s += 16) {
                if (terrain.lake(px + dx * s, pz + dz * s) < -0.36) {
                    best = Math.min(best, s);
                    break;
                }
            }
        }
        return best;
    }

    /**
     * A port with its quay on the shore: the footprint runs from the land side out over the
     * water, which is dredged into a deep basin.
     */
    private Place port(int px, int pz, double v, long h) {
        String variant;
        int width, land, basin;
        if (v < 0.3) {
            variant = "container";
            width = 340;
            land = 190;
            basin = 110;
        } else if (v < 0.42) {
            // the naval wharf: long enough for the carrier, the basin wide enough for her overhang
            variant = "naval";
            width = 430;
            land = 170;
            basin = 130;
        } else if (v < 0.7) {
            variant = "industrial";
            width = 280;
            land = 150;
            basin = 90;
        } else {
            variant = "fishing";
            width = 170;
            land = 90;
            basin = 70;
        }
        // which way is the water, and where's the shoreline
        int side = -1;
        int shore = 0;
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int best = Integer.MAX_VALUE;
        for (int s = 0; s < 4; s++) {
            for (int dist = 0; dist <= 340; dist += 8) {
                if (terrain.lake(px + dirs[s][0] * dist, pz + dirs[s][1] * dist) < -0.33) {
                    if (dist < best) {
                        best = dist;
                        side = s;
                        shore = dist;
                    }
                    break;
                }
            }
        }
        if (side < 0) {
            return null;
        }
        // centre the footprint so the quay line sits on the shore
        int sx = px + dirs[side][0] * shore, sz = pz + dirs[side][1] * shore;
        int along = land + basin;
        int x0, z0, x1, z1;
        if (side == 0) {
            x0 = sx - width / 2; x1 = x0 + width - 1; z0 = sz - basin; z1 = z0 + along - 1;
        } else if (side == 2) {
            x0 = sx - width / 2; x1 = x0 + width - 1; z1 = sz + basin; z0 = z1 - along + 1;
        } else if (side == 1) {
            z0 = sz - width / 2; z1 = z0 + width - 1; x1 = sx + basin; x0 = x1 - along + 1;
        } else {
            z0 = sz - width / 2; z1 = z0 + width - 1; x0 = sx - basin; x1 = x0 + along - 1;
        }
        return new Place(PlaceType.PORT, variant, x0, z0, x1, z1, Terrain.WATER + 3, h, side, basin);
    }

    // ------------------------------------------------------------------ hideouts

    /** How far a hideout's footprint reaches from its hatch: room for its bunker turned any way. */
    public static final int HIDE_REACH = 56;
    /** Ground cover keeps this far from a hatch and trees a little further, leaving its clues room. */
    public static final int CLEARING = 7;

    /**
     * The hideout a cell hides, if any: a hatch in thick woods on gentle ground, at least 120 blocks
     * from any place and 90 from any road, with dry land all round for the bunker under it. It is
     * a place of its own kind on a layer of its own: nothing is levelled for it, no road leads to it.
     */
    public Optional<Place> hideout(int cx, int cz) {
        return hideouts.computeIfAbsent(key(cx, cz), k -> Optional.ofNullable(planHideout(cx, cz)));
    }

    /** Every hideout whose footprint plus margin touches the box. */
    public List<Place> hideoutsNear(int x0, int z0, int x1, int z1, int margin) {
        List<Place> out = new ArrayList<>();
        int reach = HIDE_REACH + margin;
        for (int cx = Math.floorDiv(x0 - reach, CELL); cx <= Math.floorDiv(x1 + reach, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - reach, CELL); cz <= Math.floorDiv(z1 + reach, CELL); cz++) {
                hideout(cx, cz).ifPresent(p -> {
                    if (p.x0 - margin <= x1 && p.x1 + margin >= x0 && p.z0 - margin <= z1 && p.z1 + margin >= z0) {
                        out.add(p);
                    }
                });
            }
        }
        return out;
    }

    private Place planHideout(int cx, int cz) {
        if (rand(0x41DEL, cx, cz) > 0.05 || Math.abs(cx) <= 1 && Math.abs(cz) <= 1) {
            return null;
        }
        long h = Noise.hash(seed ^ 0x41DE0L, cx, cz);
        for (int i = 0; i < 24; i++) {
            int x = cx * CELL + 100 + (int) (Noise.unit(Noise.mix(h + i * 2L)) * (CELL - 200));
            int z = cz * CELL + 100 + (int) (Noise.unit(Noise.mix(h + i * 2L + 1)) * (CELL - 200));
            if (hatchFits(x, z)) {
                int level = (int) Math.floor(terrain.height(x, z));
                return new Place(PlaceType.HIDEOUT, "forest_hatch", x - HIDE_REACH, z - HIDE_REACH, x + HIDE_REACH,
                        z + HIDE_REACH, level, Noise.mix(h ^ i), -1, -1);
            }
        }
        return null;
    }

    private boolean hatchFits(int x, int z) {
        if (terrain.forest(x, z) < 0.62) {
            return false;
        }
        double g = terrain.height(x, z);
        if (g < Terrain.WATER + 4) {
            return false;
        }
        for (int[] d : new int[][] {{-3, 0}, {3, 0}, {0, -3}, {0, 3}, {-3, -3}, {3, 3}, {-3, 3}, {3, -3}}) {
            if (Math.abs(terrain.height(x + d[0], z + d[1]) - g) > 1.6) {
                return false;
            }
        }
        // dry land over wherever the bunker may run
        for (int dx = -48; dx <= 48; dx += 8) {
            for (int dz = -48; dz <= 48; dz += 8) {
                if (terrain.height(x + dx, z + dz) < Terrain.WATER + 3 || terrain.riverChannel(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        if (!placesNear(x - 120, z - 120, x + 120, z + 120, 0).isEmpty()) {
            return false;
        }
        Road.Hit hit = new Road.Hit();
        for (Road r : roadsNear(x - 100, z - 100, x + 100, z + 100)) {
            r.nearest(x + 0.5, z + 0.5, hit);
        }
        return hit.distance > 90;
    }

    // ------------------------------------------------------------------ ground

    /** Ground height after places are levelled in (roads not yet cut), as a double. */
    public double ground(double x, double z, List<Place> near) {
        double h = terrain.height(x, z);
        double bestW = 0, target = h;
        for (Place p : near) {
            double d = p.outside(x, z);
            if (d >= SKIRT) {
                continue;
            }
            double w = Terrain.smooth(SKIRT, 0, d);
            double t;
            if (p.waterSide >= 0) {
                int cx = (int) Math.max(p.x0, Math.min(p.x1, Math.floor(x)));
                int cz = (int) Math.max(p.z0, Math.min(p.z1, Math.floor(z)));
                t = p.inBasin(cx, cz) ? Terrain.WATER - 9 : p.level;
                if (p.inBasin(cx, cz) && d > 0) {
                    continue;  // the open water beyond the basin stays as it is
                }
            } else {
                t = p.level;
            }
            if (w > bestW) {
                bestW = w;
                target = t;
            }
        }
        return Terrain.lerp(h, target, bestW);
    }

    // ------------------------------------------------------------------ roads

    /** Every road passing within reach of the box. */
    public List<Road> roadsNear(int x0, int z0, int x1, int z1) {
        if (planned()) {
            return layout().roadsNear(x0, z0, x1, z1);
        }
        List<Road> out = new ArrayList<>();
        int reach = LINK_RANGE / 2 + 400;
        for (int cx = Math.floorDiv(x0 - reach, CELL); cx <= Math.floorDiv(x1 + reach, CELL); cx++) {
            for (int cz = Math.floorDiv(z0 - reach, CELL); cz <= Math.floorDiv(z1 + reach, CELL); cz++) {
                for (Road r : roads(cx, cz)) {
                    if (r.near(x0, z0, x1, z1)) {
                        out.add(r);
                    }
                }
            }
        }
        int span = CELL * MACRO;
        for (int mx = Math.floorDiv(x0, span) - 1; mx <= Math.floorDiv(x1, span) + 1; mx++) {
            for (int mz = Math.floorDiv(z0, span) - 1; mz <= Math.floorDiv(z1, span) + 1; mz++) {
                for (Road r : joins(mx, mz)) {
                    if (r.near(x0, z0, x1, z1)) {
                        out.add(r);
                    }
                }
            }
        }
        return out;
    }

    /**
     * The extra roads that make a macro region's places one network, and tie it to the regions east
     * and south of it, so every town can be driven to from every other. The nearest-neighbour roads
     * leave clusters that never meet: each cluster is joined to the nearest other one, longest bridges
     * allowed, until one network is left.
     */
    private List<Road> joins(int mx, int mz) {
        return joins.computeIfAbsent(key(mx, mz), k -> {
            List<Road> out = new ArrayList<>();
            List<Place> mine = macroPlaces(mx, mz);
            if (mine.isEmpty()) {
                return out;
            }
            Map<Place, Place> parent = new java.util.HashMap<>();
            for (Place p : mine) {
                parent.put(p, p);
            }
            for (int cx = mx * MACRO - 3; cx < mx * MACRO + MACRO + 3; cx++) {
                for (int cz = mz * MACRO - 3; cz < mz * MACRO + MACRO + 3; cz++) {
                    for (Road r : roads(cx, cz)) {
                        if (parent.containsKey(r.from) && parent.containsKey(r.to)) {
                            parent.put(root(parent, r.from), root(parent, r.to));
                        }
                    }
                }
            }
            // grow one network from the first place: always add the shortest road that reaches a new cluster
            while (true) {
                Place r0 = root(parent, mine.get(0));
                List<Place[]> pairs = new ArrayList<>();
                for (Place a : mine) {
                    if (root(parent, a) != r0) {
                        continue;
                    }
                    for (Place b : mine) {
                        if (root(parent, b) != r0) {
                            pairs.add(new Place[] {a, b});
                        }
                    }
                }
                if (pairs.isEmpty()) {
                    break;
                }
                pairs.sort(Comparator.comparingDouble(p -> dist(p[0], p[1])));
                Road road = null;
                for (int i = 0; i < Math.min(12, pairs.size()) && road == null; i++) {
                    road = build(pairs.get(i)[0], pairs.get(i)[1], 900);
                }
                Place[] pick = pairs.get(0);
                if (road != null) {
                    out.add(road);
                    pick = new Place[] {road.from, road.to};
                }
                // a cluster no road can reach (out on an island) is let go rather than looped on
                parent.put(root(parent, pick[1]), r0);
            }
            // then one road to each of the regions east and south, unless one already crosses
            for (int[] d : new int[][] {{1, 0}, {0, 1}}) {
                List<Place> theirs = macroPlaces(mx + d[0], mz + d[1]);
                if (theirs.isEmpty() || crosses(mx, mz, mine, theirs)) {
                    continue;
                }
                List<Place[]> pairs = new ArrayList<>();
                for (Place a : mine) {
                    for (Place b : theirs) {
                        pairs.add(new Place[] {a, b});
                    }
                }
                pairs.sort(Comparator.comparingDouble(p -> dist(p[0], p[1])));
                Road road = null;
                for (int i = 0; i < Math.min(12, pairs.size()) && road == null; i++) {
                    road = build(pairs.get(i)[0], pairs.get(i)[1], 900);
                }
                if (road != null) {
                    out.add(road);
                }
            }
            return out;
        });
    }

    /** The places whose cells lie in a macro region. */
    private List<Place> macroPlaces(int mx, int mz) {
        List<Place> out = new ArrayList<>();
        for (int cx = mx * MACRO; cx < mx * MACRO + MACRO; cx++) {
            for (int cz = mz * MACRO; cz < mz * MACRO + MACRO; cz++) {
                place(cx, cz).ifPresent(out::add);
            }
        }
        return out;
    }

    /** Whether a nearest-neighbour road already runs between the two regions' places. */
    private boolean crosses(int mx, int mz, List<Place> mine, List<Place> theirs) {
        java.util.Set<Place> a = new java.util.HashSet<>(mine), b = new java.util.HashSet<>(theirs);
        for (int cx = mx * MACRO - 3; cx < mx * MACRO + 2 * MACRO + 3; cx++) {
            for (int cz = mz * MACRO - 3; cz < mz * MACRO + 2 * MACRO + 3; cz++) {
                for (Road r : roads(cx, cz)) {
                    if (a.contains(r.from) && b.contains(r.to) || a.contains(r.to) && b.contains(r.from)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static Place root(Map<Place, Place> parent, Place p) {
        while (parent.get(p) != p) {
            Place up = parent.get(parent.get(p));
            parent.put(p, up);
            p = up;
        }
        return p;
    }

    private static double dist(Place a, Place b) {
        return Math.hypot(a.centreX() - b.centreX(), a.centreZ() - b.centreZ());
    }

    private record Cand(Place p, int cx, int cz, double dist) {}

    private List<Cand> neighbours(Place a, int acx, int acz) {
        List<Cand> out = new ArrayList<>();
        int r = LINK_RANGE / CELL + 1;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Place b = place(acx + dx, acz + dz).orElse(null);
                if (b == null) {
                    continue;
                }
                double d = Math.hypot(a.centreX() - b.centreX(), a.centreZ() - b.centreZ());
                if (d <= LINK_RANGE) {
                    out.add(new Cand(b, acx + dx, acz + dz, d));
                }
            }
        }
        out.sort(Comparator.comparingDouble(Cand::dist));
        return out;
    }

    private static int links(PlaceType t) {
        return switch (t) {
            case DOWNTOWN -> 5;
            case TOWN, PORT, LANDMARK -> 3;
            case SUBURB, MILITARY, FARM -> 2;
            case ROADSIDE -> 1;
            case HIDEOUT -> 0;
        };
    }

    private boolean linked(Place a, int acx, int acz, Place b) {
        List<Cand> n = neighbours(a, acx, acz);
        for (int i = 0; i < Math.min(links(a.type), n.size()); i++) {
            if (n.get(i).p == b) {
                return true;
            }
        }
        return false;
    }

    /** The roads a cell's place starts (each road belongs to the end with the lower cell key). */
    private List<Road> roads(int cx, int cz) {
        return roads.computeIfAbsent(key(cx, cz), k -> {
            Place a = place(cx, cz).orElse(null);
            List<Road> out = new ArrayList<>();
            if (a == null) {
                return out;
            }
            long ka = key(cx, cz);
            for (Cand c : neighbours(a, cx, cz)) {
                if (key(c.cx, c.cz) < ka) {
                    continue;
                }
                if (linked(a, cx, cz, c.p) || linked(c.p, c.cx, c.cz, a)) {
                    Road road = build(a, c.p, 260);
                    if (road != null) {
                        out.add(road);
                    }
                }
            }
            return out;
        });
    }

    private static boolean major(PlaceType t) {
        return t == PlaceType.DOWNTOWN || t == PlaceType.TOWN || t == PlaceType.PORT || t == PlaceType.MILITARY;
    }

    Road build(Place a, Place b, int maxWater) {
        Road.Kind kind;
        double dist = Math.hypot(a.centreX() - b.centreX(), a.centreZ() - b.centreZ());
        if (major(a.type) && major(b.type) && dist > 600) {
            kind = Road.Kind.HIGHWAY;
        } else if (a.type == PlaceType.FARM && b.type == PlaceType.FARM) {
            kind = Road.Kind.TRACK;
        } else {
            kind = Road.Kind.COUNTY;
        }
        int sa = landSide(a, sideFacing(a, b), b), sb = landSide(b, sideFacing(b, a), a);
        int[] ga = a.gate(sa), gb = b.gate(sb);
        int[][] out = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int lead = 24;
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {ga[0], ga[1]});
        double ax = ga[0] + out[sa][0] * lead, az = ga[1] + out[sa][1] * lead;
        double bx = gb[0] + out[sb][0] * lead, bz = gb[1] + out[sb][1] * lead;
        pts.add(new double[] {ax, az});
        long rs = Noise.hash(seed ^ 0x40ADL, a.centreX() * 31L + b.centreX(), a.centreZ() * 31L + b.centreZ());
        wander(pts, ax, az, bx, bz, rs, 0, kind == Road.Kind.HIGHWAY ? 0.10 : 0.18);
        pts.add(new double[] {bx, bz});
        pts.add(new double[] {gb[0], gb[1]});
        // resample every STEP blocks
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p = pts.get(i), q = pts.get(i + 1);
            double len = Math.hypot(q[0] - p[0], q[1] - p[1]);
            int n = Math.max(1, (int) Math.round(len / Road.STEP));
            for (int s = 0; s < n; s++) {
                line.add(new double[] {p[0] + (q[0] - p[0]) * s / n, p[1] + (q[1] - p[1]) * s / n});
            }
        }
        line.add(pts.get(pts.size() - 1));
        // round the corners: a few passes of neighbour averaging, ends held in place
        int keep = lead / Road.STEP + 1;
        for (int pass = 0; pass < 6; pass++) {
            for (int i = keep; i < line.size() - keep; i++) {
                double[] p = line.get(i - 1), q = line.get(i + 1);
                line.set(i, new double[] {(p[0] + q[0] + line.get(i)[0] * 2) / 4, (p[1] + q[1] + line.get(i)[1] * 2) / 4});
            }
        }
        int n = line.size();
        double[] xs = new double[n], zs = new double[n], ys = new double[n];
        double water = 0;
        for (int i = 0; i < n; i++) {
            xs[i] = line.get(i)[0];
            zs[i] = line.get(i)[1];
            List<Place> near = placesNear((int) xs[i], (int) zs[i], (int) xs[i], (int) zs[i], SKIRT);
            double g = ground(xs[i], zs[i], near);
            if (Terrain.isWater(g)) {
                water += Road.STEP;
                g = Terrain.WATER + 4;  // a bridge
            }
            ys[i] = Math.max(g, Terrain.WATER + 2);
        }
        if (water > maxWater) {
            return null;  // no causeways across lakes
        }
        // smooth the profile so it climbs gently, then pin the ends to the places' ground
        for (int pass = 0; pass < 8; pass++) {
            double[] next = ys.clone();
            for (int i = 1; i < n - 1; i++) {
                double s = 0;
                int c = 0;
                for (int j = Math.max(0, i - 4); j <= Math.min(n - 1, i + 4); j++) {
                    s += ys[j];
                    c++;
                }
                next[i] = s / c;
            }
            ys = next;
        }
        for (int i = 0; i < n; i++) {
            double ta = Math.max(0, 1 - i / (double) keep), tb = Math.max(0, 1 - (n - 1 - i) / (double) keep);
            ys[i] = Terrain.lerp(ys[i], a.level, ta);
            ys[i] = Terrain.lerp(ys[i], b.level, tb);
        }
        return new Road(kind, xs, zs, ys, rs, a, b);
    }

    /** Midpoint displacement: a road that wanders a little instead of running dead straight. */
    void wander(List<double[]> pts, double ax, double az, double bx, double bz, long rs, int depth, double amp) {
        double len = Math.hypot(bx - ax, bz - az);
        if (len < 70 || depth > 6) {
            return;
        }
        double mx = (ax + bx) / 2, mz = (az + bz) / 2;
        double off = (Noise.unit(Noise.mix(rs + depth * 7919L)) * 2 - 1) * amp * len;
        mx += -(bz - az) / len * off;
        mz += (bx - ax) / len * off;
        wander(pts, ax, az, mx, mz, Noise.mix(rs ^ 0x1L), depth + 1, amp);
        pts.add(new double[] {mx, mz});
        wander(pts, mx, mz, bx, bz, Noise.mix(rs ^ 0x2L), depth + 1, amp);
    }

    /** A port's road never leaves from its water side: it turns to whichever land side is nearer b. */
    static int landSide(Place a, int side, Place b) {
        if (side != a.waterSide) {
            return side;
        }
        boolean alongX = (side & 1) == 0;
        double d = alongX ? b.centreX() - a.centreX() : b.centreZ() - a.centreZ();
        return alongX ? (d > 0 ? 1 : 3) : (d > 0 ? 2 : 0);
    }

    /** Which side of a faces b: 0 north, 1 east, 2 south, 3 west. */
    static int sideFacing(Place a, Place b) {
        double dx = b.centreX() - a.centreX(), dz = b.centreZ() - a.centreZ();
        if (Math.abs(dx) > Math.abs(dz)) {
            return dx > 0 ? 1 : 3;
        }
        return dz > 0 ? 2 : 0;
    }
}
