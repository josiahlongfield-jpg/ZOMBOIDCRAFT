package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The county laid out from the {@link Plan}, once, when the world is made:
 * <ul>
 * <li>the plan's own places where the map puts them: towns, ports, bases, quarantine zones and
 * Grayhaven Stadium on the island;</li>
 * <li>the city inside the wall, the island and the inland cities, as one street grid cut into
 * pieces of up to six blocks square, each piece a downtown that grows taller toward its
 * district's heart (Downtown, the Financial District, Old Harbor, Midtown, Uptown, Eastside)
 * and turns to walk-ups toward Westgate, Northside and Southport;</li>
 * <li>suburbs through the sprawl round the wall and the towns, farms out in the farm country
 * and the odd stop along the state roads, as the plan's fill rule has them;</li>
 * <li>the plan's highways and roads, broken where they run into a place or the city (they meet
 * a gate or the city's edge streets) and bridged over the rivers, and a road from every
 * suburb, farm and stop to its neighbours and the nearest highway.</li>
 * </ul>
 * Places and roads sit in square buckets so a chunk finds the ones near it at once.
 */
final class Layout {
    /** The city's street grid: a street every PITCH blocks, east-west and north-south. */
    static final int PITCH = 80;
    /** A piece of the city is at most this many blocks a side. */
    private static final int SUPER = 6;
    private static final int EDGE = DowntownSite.TILE_EDGE;
    private static final int BUCKET = 256;
    private static final int FENCE = 7950;

    private final County county;
    private final Plan plan;
    private final Terrain terrain;
    private final long seed;
    final List<Place> places = new ArrayList<>();
    final List<Road> roads = new ArrayList<>();
    /** The place each named plan spot became. */
    final Map<String, Place> named = new LinkedHashMap<>();
    private final Map<Long, List<Place>> placeIndex = new HashMap<>();
    private final Map<Long, List<Road>> roadIndex = new HashMap<>();
    /** The city's blocks, by grid cell. */
    private final Set<Long> cityBlocks = new HashSet<>();
    /** Lots the city keeps clear: {x0, z0, x1, z1}. */
    private final List<int[]> reserved = new ArrayList<>();
    private final List<Place> filler = new ArrayList<>();
    private final List<Road> planRoads = new ArrayList<>();

    /** A city district: its heart, what it's built as, and how far its towers reach. */
    private record District(String id, int x, int z, String variant, int radius, double offset) {}

    private final List<District> districts = new ArrayList<>();
    /** The inland cities' circles of street grid: {x, z, radius}. */
    private final List<int[]> inland = new ArrayList<>();
    /** The railway and the subway, laid once the city's grid is known. */
    Railway railway;

    Layout(County county) {
        this.county = county;
        this.plan = county.terrain.plan;
        this.terrain = county.terrain;
        this.seed = county.seed;
        county.layingOut(this);
        long t0 = System.nanoTime();
        districts();
        fixed();
        city();
        railway = new Railway(this, plan, terrain);
        railway.markTiles(places);
        depots();
        suburbs();
        country();
        for (Place p : places) {
            index(p);
        }
        planRoads();
        links();
        airportRoad();
        railway.crossRoads(roads);
        for (Road r : roads) {
            index(r);
        }
        System.out.printf("County laid out from the plan: %d places (%d city pieces), %d roads in %d ms%n", places.size(),
                places.stream().filter(p -> p.tile != null).count(), roads.size(), (System.nanoTime() - t0) / 1_000_000);
    }

    // ------------------------------------------------------------------ lookups

    List<Place> placesNear(int x0, int z0, int x1, int z1, int margin) {
        Set<Place> out = new LinkedHashSet<>();
        for (int bx = Math.floorDiv(x0 - margin, BUCKET); bx <= Math.floorDiv(x1 + margin, BUCKET); bx++) {
            for (int bz = Math.floorDiv(z0 - margin, BUCKET); bz <= Math.floorDiv(z1 + margin, BUCKET); bz++) {
                List<Place> in = placeIndex.get(County.key(bx, bz));
                if (in == null) {
                    continue;
                }
                for (Place p : in) {
                    if (p.x0 - margin <= x1 && p.x1 + margin >= x0 && p.z0 - margin <= z1 && p.z1 + margin >= z0) {
                        out.add(p);
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    List<Road> roadsNear(int x0, int z0, int x1, int z1) {
        Set<Road> out = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (int bx = Math.floorDiv(x0, BUCKET); bx <= Math.floorDiv(x1, BUCKET); bx++) {
            for (int bz = Math.floorDiv(z0, BUCKET); bz <= Math.floorDiv(z1, BUCKET); bz++) {
                List<Road> in = roadIndex.get(County.key(bx, bz));
                if (in == null) {
                    continue;
                }
                for (Road r : in) {
                    if (r.near(x0, z0, x1, z1)) {
                        out.add(r);
                    }
                }
            }
        }
        return new ArrayList<>(out);
    }

    private void index(Place p) {
        for (int bx = Math.floorDiv(p.x0, BUCKET); bx <= Math.floorDiv(p.x1, BUCKET); bx++) {
            for (int bz = Math.floorDiv(p.z0, BUCKET); bz <= Math.floorDiv(p.z1, BUCKET); bz++) {
                placeIndex.computeIfAbsent(County.key(bx, bz), k -> new ArrayList<>()).add(p);
            }
        }
    }

    private void index(Road r) {
        // a road's box can be long and thin across the county: file it under the buckets its
        // points pass through, padded by its own reach
        Set<Long> keys = new HashSet<>();
        int pad = r.kind.half + 12;
        for (int i = 0; i < r.xs.length; i++) {
            for (int bx = Math.floorDiv((int) r.xs[i] - pad, BUCKET); bx <= Math.floorDiv((int) r.xs[i] + pad, BUCKET); bx++) {
                for (int bz = Math.floorDiv((int) r.zs[i] - pad, BUCKET); bz <= Math.floorDiv((int) r.zs[i] + pad, BUCKET); bz++) {
                    keys.add(County.key(bx, bz));
                }
            }
        }
        for (long k : keys) {
            roadIndex.computeIfAbsent(k, kk -> new ArrayList<>()).add(r);
        }
    }

    // ------------------------------------------------------------------ the plan's places

    private void districts() {
        for (Plan.Spot s : plan.spots) {
            switch (s.kind()) {
                case "district" -> districts.add(new District(s.id(), s.x(), s.z(), s.variant(),
                        s.variant().equals("metro") ? 950 : s.variant().equals("city") ? 900 : 800, 0));
                case "suburb" -> {
                    if (plan.inCore(s.x(), s.z())) {
                        // the old neighbourhoods inside the wall: rows of walk-ups
                        districts.add(new District(s.id(), s.x(), s.z(), "old_city", 700, 0.8));
                    }
                }
                case "industry" -> {
                    if (plan.inCore(s.x(), s.z())) {
                        districts.add(new District(s.id(), s.x(), s.z(), "old_city", 600, 0.6));
                    }
                }
                case "city" -> {
                    double r = plan.urban.getOrDefault(s.id(), 600.0);
                    districts.add(new District(s.id(), s.x(), s.z(), s.variant(), (int) (r * 0.55), 0));
                    inland.add(new int[] {s.x(), s.z(), (int) (r * 0.6)});
                }
                default -> {
                }
            }
        }
    }

    /** The city's ground: inside the wall, or the middle of an inland city. */
    private boolean urban(double x, double z) {
        if (plan.inCore(x, z)) {
            return true;
        }
        for (int[] c : inland) {
            if (Math.hypot(x - c[0], z - c[1]) < c[2]) {
                return true;
            }
        }
        return false;
    }

    private void fixed() {
        for (Plan.Spot s : plan.spots) {
            if (s.id().equals("airfield") || s.id().equals("ranges") || s.id().equals("camp") || s.id().equals("ravenrock")) {
                // Caldwell Army Airfield, the tank ranges, Camp Black Pine and Ravenrock: tiles like the airport's, each carrying the gates its road comes to
                long h = Noise.hash(seed ^ 0x9A1AL, s.x(), s.z());
                List<Place> tiles = s.id().equals("airfield") ? ArmyField.places(s, county, plan, h)
                        : s.id().equals("camp") ? CampBlackPine.places(s, county, plan, h)
                        : s.id().equals("ravenrock") ? Ravenrock.places(s, county, plan, h) : TankRanges.places(s, county, plan, h);
                for (int i = 0; i < tiles.size(); i++) {
                    places.add(tiles.get(i));
                    named.put(i == 0 ? s.id() : s.id() + "#" + i, tiles.get(i));
                }
                continue;
            }
            if (s.id().equals("airport")) {
                // Grayhaven International: too big to build whole, so it comes as a row of tiles
                List<Place> tiles = Airport.places(s, county, Noise.hash(seed ^ 0x9A1AL, s.x(), s.z()));
                for (int i = 0; i < tiles.size(); i++) {
                    places.add(tiles.get(i));
                    named.put(i == 0 ? s.id() : s.id() + "#" + i, tiles.get(i));
                }
                continue;
            }
            Place p = fixedPlace(s);
            if (p != null) {
                places.add(p);
                named.put(s.id(), p);
                if (urban(p.centreX(), p.centreZ())) {
                    // a lot cut out of the grid keeps the blocks round it, which own its streets
                    int in = p.type == PlaceType.LANDMARK ? EDGE + 4 : 0;
                    reserved.add(new int[] {p.x0 + in, p.z0 + in, p.x1 - in, p.z1 - in});
                }
            }
        }
    }

    private Place fixedPlace(Plan.Spot s) {
        long h = Noise.hash(seed ^ 0x9A1AL, s.x(), s.z());
        if (s.id().equals("stadium")) {
            return lot(s, PlaceType.LANDMARK, "stadium", 6, 7, h);
        }
        if (s.id().equals("union")) {
            // the terminal: two blocks wide and two long, the el's street between them built over so
            // the trains run through the station's top floor; the subway passes its front
            return lot(s, PlaceType.LANDMARK, "union_station", 2, 2, h);
        }
        if (s.id().equals("mall")) {
            // the galleria: four blocks along the front, three deep, the car park and decks round it
            return lot(s, PlaceType.LANDMARK, "mall", 4, 3, h);
        }
        if (s.id().equals("mercy")) {
            // Mercy General: two blocks square, nudged off the street the subway runs under so its
            // Mercy General platform stays under the street in front (the Glass Line deeper still)
            return lot(new Plan.Spot(s.id(), s.kind(), PlaceType.LANDMARK, "mercy", 2880, -320, s.flags(), s.name()),
                    PlaceType.LANDMARK, "mercy", 2, 2, h);
        }
        if (s.id().equals("university")) {
            // State University: three blocks square, nudged east of the street the subway runs under
            return lot(new Plan.Spot(s.id(), s.kind(), PlaceType.LANDMARK, "university", 2760, -1720, s.flags(), s.name()),
                    PlaceType.LANDMARK, "university", 3, 3, h);
        }
        if (s.id().equals("civic")) {
            // the Civic Center: City Hall, the council chamber and the plaza, two blocks square
            return lot(s, PlaceType.LANDMARK, "civic", 2, 2, h);
        }
        if (s.id().equals("park")) {
            // City Park and the museum along its north side, three blocks square
            return lot(s, PlaceType.LANDMARK, "park", 3, 3, h);
        }
        if (s.id().equals("arena")) {
            // Harbor Arena: three blocks along the shore, two deep, nudged west off the water
            return lot(new Plan.Spot(s.id(), s.kind(), PlaceType.LANDMARK, "arena", 3640, -480, s.flags(), s.name()),
                    PlaceType.LANDMARK, "arena", 3, 2, h);
        }
        if (s.id().equals("ballpark")) {
            // Grayhaven Ballpark: three blocks square by the west shore, the outfield toward the water
            return lot(s, PlaceType.LANDMARK, "ballpark", 3, 3, h);
        }
        if (s.id().equals("tower")) {
            // the Broadcast Center: two blocks square, its front to the street the subway runs under
            return lot(s, PlaceType.LANDMARK, "tower", 2, 2, h);
        }
        if (s.id().equals("power")) {
            // Harbor Point Generating Station: three blocks along the subway's street, two deep, nudged
            // west so the street the subway runs under is its front
            return lot(new Plan.Spot(s.id(), s.kind(), PlaceType.LANDMARK, "power", 3800, s.z(), s.flags(), s.name()),
                    PlaceType.LANDMARK, "power", 3, 2, h);
        }
        if (s.id().equals("market")) {
            // the Old Market: the market house down the middle of three blocks by two, the lanes and rows either side
            return lot(s, PlaceType.LANDMARK, "market", 3, 2, h);
        }
        if (s.id().equals("waterfront")) {
            // the Waterfront: from the avenue the subway runs under out over the harbour, two blocks
            // east of it by three along the shore; the land to a straight new seawall, the rest the
            // basin the pier, the ferry and the marina stand in (left as water, see County.ground)
            int i0 = Math.floorDiv(s.x() + PITCH, PITCH), j0 = (int) Math.round(s.z() / (double) PITCH - 1.5);
            int x0 = i0 * PITCH + EDGE + 1, z0 = j0 * PITCH + EDGE + 1;
            int x1 = (i0 + 2) * PITCH - EDGE - 1, z1 = (j0 + 3) * PITCH - EDGE - 1;
            return new Place(PlaceType.LANDMARK, "waterfront", x0, z0, x1, z1, Terrain.CITY, h, 1, x1 - x0 - Waterfront.SEAWALL);
        }
        if (s.id().equals("lab")) {
            // Ridgeview Labs and the ground zero round it: 300 square on the mountain, near its spot
            return near(PlaceType.MILITARY, "ridgeview", s.x(), s.z(), h);
        }
        if (s.id().equals("naval")) {
            // the page builds the naval station as a base; here it's the naval wharf, the carrier along it
            return port(new Plan.Spot(s.id(), s.kind(), PlaceType.PORT, "naval", s.x(), s.z(), s.flags(), s.name()), h);
        }
        if (s.type() == null) {
            return null;
        }
        return switch (s.type()) {
            case PORT -> port(s, h);
            case TOWN, SUBURB, MILITARY, FARM -> plan.inCore(s.x(), s.z()) ? null : near(s.type(), s.variant(), s.x(), s.z(), h);
            default -> null;
        };
    }

    /**
     * A landmark's lot cut out of the city's grid: nx by nz blocks whose streets round it belong to
     * the pieces next door, so the lot runs to their sidewalks and its avenues meet them.
     */
    private Place lot(Plan.Spot s, PlaceType type, String variant, int nx, int nz, long h) {
        int i0 = (int) Math.round(s.x() / (double) PITCH - nx / 2.0), j0 = (int) Math.round(s.z() / (double) PITCH - nz / 2.0);
        int x0 = i0 * PITCH + EDGE + 1, z0 = j0 * PITCH + EDGE + 1;
        int x1 = (i0 + nx) * PITCH - EDGE - 1, z1 = (j0 + nz) * PITCH - EDGE - 1;
        return new Place(type, variant, x0, z0, x1, z1, Terrain.CITY, h, -1, -1);
    }

    private static int[] size(PlaceType type, String variant) {
        return switch (type + ":" + variant) {
            case "TOWN:hamlet" -> new int[] {150, 150};
            case "TOWN:main_street" -> new int[] {240, 200};
            case "TOWN:county_seat" -> new int[] {300, 300};
            case "TOWN:mill_town" -> new int[] {260, 230};
            case "SUBURB:cul_de_sac" -> new int[] {260, 240};
            case "SUBURB:old_grid" -> new int[] {240, 220};
            case "SUBURB:estates" -> new int[] {300, 260};
            case "SUBURB:trailer_park" -> new int[] {150, 130};
            case "MILITARY:quarantine" -> new int[] {210, 190};
            case "MILITARY:army_base" -> new int[] {280, 260};
            case "MILITARY:depot" -> new int[] {190, 170};
            case "MILITARY:ridgeview" -> new int[] {300, 300};
            case "FARM:crop" -> new int[] {220, 200};
            case "FARM:dairy" -> new int[] {200, 180};
            case "FARM:horse_ranch" -> new int[] {220, 210};
            case "FARM:orchard" -> new int[] {180, 170};
            case "FARM:homestead" -> new int[] {130, 120};
            case "ROADSIDE:gas_station", "ROADSIDE:diner" -> new int[] {90, 108};
            case "ROADSIDE:motel" -> new int[] {100, 112};
            case "ROADSIDE:church" -> new int[] {96, 128};
            case "ROADSIDE:school" -> new int[] {150, 190};
            case "ROADSIDE:warehouse" -> new int[] {104, 136};
            case "ROADSIDE:trailer_lot" -> new int[] {90, 116};
            default -> new int[] {200, 200};
        };
    }

    /** A place as near (x, z) as dry land and its neighbours allow, within 400 blocks. */
    private Place near(PlaceType type, String variant, int x, int z, long h) {
        int[] sz = size(type, variant);
        for (int ring = 0; ring <= 400; ring += 40) {
            int steps = ring == 0 ? 1 : 8;
            for (int k = 0; k < steps; k++) {
                double a = k * Math.PI / 4;
                int px = x + (int) Math.round(Math.cos(a) * ring), pz = z + (int) Math.round(Math.sin(a) * ring);
                Place p = county.fit(type, variant, px, pz, sz[0], sz[1], h);
                if (p == null && type != PlaceType.MILITARY) {
                    p = county.fit(type, variant, px, pz, sz[0] * 3 / 4, sz[1] * 3 / 4, h);
                }
                if (p != null && clear(p, 40)) {
                    return p;
                }
            }
        }
        return null;
    }

    /** The country stops with a depot behind the platform (the rw_ kit has their names for its walls). */
    static final Set<String> DEPOTS = Set.of("pinecrest", "millbrook", "fairview", "kings", "seabright");
    /** A depot's lot: along the platform, and back from its back edge. */
    static final int DEPOT_LEN = 44, DEPOT_DEPTH = 24;
    /**
     * A halt's car park ({@link HaltSite}) behind the fence of a country stop the plan's roads pass
     * well clear of: along the platform's middle (out past its gates), and back from the fence.
     */
    static final int HALT_LEN = 36, HALT_DEPTH = 14;
    /** No plan road within this of a stop's middle: it gets a halt, whose own road the county runs in to it. */
    private static final double HALT_ROAD = 90;

    /**
     * A depot's lot ({@link DepotSite}) behind the platform at each of the bigger country stops:
     * along the platform's middle, from its back edge out, levelled to the land beside the line (a
     * block under the platform), its front to the line, the side no road leaves it by. At a stop
     * the plan's roads pass well clear of, a halt's car park instead, which links() runs a road to.
     */
    private void depots() {
        for (Track t : railway.tracks) {
            if (t.kind != Track.Kind.RAIL) {
                continue;
            }
            for (int k = 0; k < t.stops.size(); k++) {
                Track.Stop st = t.stops.get(k);
                String key = RailGen.key(st);
                if (key != null && DEPOTS.contains(key)) {
                    Place p = lot(t, st, "depot", DEPOT_LEN, DEPOT_DEPTH, 7);
                    if (p != null) {
                        places.add(p);
                        t.depots.set(k);
                    }
                } else if (t.street[st.middle()] == 0 && roadless(t, st)) {
                    Place p = lot(t, st, "halt", HALT_LEN, HALT_DEPTH, 8);
                    if (p != null) {
                        places.add(p);
                    }
                }
            }
        }
    }

    /** True if none of the plan's roads comes within HALT_ROAD of the stop's middle. */
    private boolean roadless(Track t, Track.Stop st) {
        int m = st.middle();
        for (Plan.Line rd : plan.roads) {
            for (double[] q : resample(rd.shape, Road.STEP)) {
                if (Math.hypot(q[0] - t.xs[m], q[1] - t.zs[m]) < HALT_ROAD) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A lot behind a country stop's platform, len along it and depth back, from gap out from the
     * middle of the line; its variant's Site draws it. Null where the line isn't a straight squared
     * to the compass and level the whole way, or the lot would be wet or in another's way.
     */
    private Place lot(Track t, Track.Stop st, String variant, int len, int depth, int gap) {
        int m = st.middle(), half = len / 2;
        double tx = t.tx[m], tz = t.tz[m];
        if (Math.max(Math.abs(tx), Math.abs(tz)) < 0.999) {
            return null;   // only on a straight squared to the compass
        }
        for (int i = m - half - 2; i <= m + half + 2; i++) {
            int k = t.wrap(i);
            if (t.mode[k] != Track.GROUND || t.street[k] > 0 || t.stopAt[k] != t.stopAt[m] || t.ys[k] != t.ys[m]) {
                return null;
            }
        }
        // the bed top as the platform has it (RailGen's Cut): the land beside the line is a block under it
        double y = t.ys[m];
        int whole = (int) Math.floor(y), Y = whole + (Math.round((y - whole) * 4) == 4 ? 1 : 0);
        // the platform's side, out from the line: right of the way the trains run is (-tz, tx)
        int nx = (int) Math.round(-tz * st.side()), nz = (int) Math.round(tx * st.side());
        boolean alongX = Math.abs(tx) > Math.abs(tz);
        double lat = alongX ? t.zs[m] : t.xs[m];
        int n = alongX ? nz : nx;
        // the first column off the platform (offset 7.5 or more on its side)
        int near = n > 0 ? (int) Math.ceil(lat + gap - 1e-6) : (int) Math.floor(lat - gap - 1 + 1e-6);
        int far = near + n * (depth - 1);
        int a0 = (int) Math.floor(alongX ? t.xs[m] : t.zs[m]) - half, a1 = a0 + len - 1;
        int c0 = Math.min(near, far), c1 = Math.max(near, far);
        // its front faces the line: 0 north, 1 east, 2 south, 3 west
        int facing = alongX ? (n > 0 ? 0 : 2) : (n > 0 ? 3 : 1);
        Place p = alongX
                ? new Place(PlaceType.ROADSIDE, variant, a0, c0, a1, c1, Y - 1, Noise.hash(seed ^ 0xDE90L, a0, c0), facing, 0)
                : new Place(PlaceType.ROADSIDE, variant, c0, a0, c1, a1, Y - 1, Noise.hash(seed ^ 0xDE90L, c0, a0), facing, 0);
        for (Place o : places) {
            if (p.overlaps(o, 16)) {
                return null;
            }
        }
        // on dry land, and no other line (nor this one coming round again) through it
        for (int x = p.x0; x <= p.x1; x += 8) {
            for (int z = p.z0; z <= p.z1; z += 8) {
                if (terrain.height(x, z) < Terrain.WATER + 1 || terrain.riverChannel(x, z)) {
                    return null;
                }
            }
        }
        for (Track o : railway.tracks) {
            if (o.kind == Track.Kind.SUBWAY) {
                continue;
            }
            for (int i = 0; i < o.n; i += 2) {
                boolean own = o == t && Math.abs(i - m) < 200 || o == t && o.n - Math.abs(i - m) < 200;
                if (!own && p.contains((int) o.xs[i], (int) o.zs[i], 8)) {
                    return null;
                }
            }
        }
        return p;
    }

    /** True if p keeps margin clear of every place laid so far and of the county fence. */
    private boolean clear(Place p, int margin) {
        if (Math.abs(p.x0) > FENCE - 60 || Math.abs(p.x1) > FENCE - 60 || Math.abs(p.z0) > FENCE - 60 || Math.abs(p.z1) > FENCE - 60) {
            return false;
        }
        for (Place o : places) {
            if (p.overlaps(o, margin)) {
                return false;
            }
        }
        return railway == null || railway.clear(p, margin);
    }

    /**
     * A port with its quay on the nearest open water (sea, river arm or the river), the footprint
     * running from the land out over the water, as the endless county lays its ports.
     */
    private Place port(Plan.Spot s, long h) {
        String v = s.variant();
        int width, land, basin;
        switch (v) {
            case "container" -> {
                width = s.has("huge") ? 480 : 340;
                land = s.has("huge") ? 220 : 190;
                basin = s.has("huge") ? 130 : 110;
            }
            case "industrial" -> {
                width = 280;
                land = 150;
                basin = 90;
            }
            case "naval" -> {
                // long enough for the carrier, the basin wide enough for her overhang
                width = 430;
                land = 170;
                basin = 130;
            }
            default -> {
                width = 170;
                land = 90;
                basin = 70;
            }
        }
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int side = -1, shore = Integer.MAX_VALUE;
        // where the plan draws piers off it, the water is that way
        int only = -1;
        List<double[]> piers = plan.piers.get(s.id());
        if (piers != null) {
            double px = 0, pz = 0;
            for (double[] q : piers) {
                px += q[0] + q[2];
                pz += q[1] + q[3];
            }
            only = Math.abs(px) > Math.abs(pz) ? (px > 0 ? 1 : 3) : (pz > 0 ? 2 : 0);
        }
        for (int sd = 0; sd < 4; sd++) {
            if (only >= 0 && sd != only) {
                continue;
            }
            for (int dist = 0; dist <= 1600; dist += 8) {
                double x = s.x() + dirs[sd][0] * dist, z = s.z() + dirs[sd][1] * dist;
                if (plan.coast(x, z) < -6 || plan.river(x, z, null) < -10) {
                    if (dist < shore) {
                        shore = dist;
                        side = sd;
                    }
                    break;
                }
            }
        }
        if (side < 0) {
            return null;
        }
        int sx = s.x() + dirs[side][0] * shore, sz = s.z() + dirs[side][1] * shore;
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
        int level = urban(sx, sz) ? Terrain.CITY : Terrain.WATER + 3;
        return new Place(PlaceType.PORT, v, x0, z0, x1, z1, level, h, side, basin);
    }

    // ------------------------------------------------------------------ the city

    private boolean blockIn(int i, int j) {
        double x0 = i * PITCH - EDGE - 2, z0 = j * PITCH - EDGE - 2, span = PITCH + 2 * EDGE + 4;
        for (int a = 0; a <= 4; a++) {
            for (int b = 0; b <= 4; b++) {
                double x = x0 + span * a / 4, z = z0 + span * b / 4;
                if (!urban(x, z) || Math.abs(x) > FENCE - 40 || Math.abs(z) > FENCE - 40 || plan.toWater(x, z) < 4) {
                    return false;
                }
            }
        }
        int bx0 = i * PITCH - EDGE - 16, bz0 = j * PITCH - EDGE - 16, bx1 = (i + 1) * PITCH + EDGE + 16, bz1 = (j + 1) * PITCH + EDGE + 16;
        for (int[] r : reserved) {
            if (r[0] <= bx1 && r[2] >= bx0 && r[1] <= bz1 && r[3] >= bz0) {
                return false;
            }
        }
        return true;
    }

    private boolean in(int i, int j) {
        return cityBlocks.contains(County.key(i, j));
    }

    /** True if the grid block (i, j) is the city's. */
    boolean cityBlock(int i, int j) {
        return in(i, j);
    }

    /** True if (x, z) is on a lot the city keeps clear, or on one of the places laid so far that isn't the city's. */
    boolean reservedAt(double x, double z) {
        for (int[] r : reserved) {
            if (x >= r[0] - 20 && x <= r[2] + 20 && z >= r[1] - 20 && z <= r[3] + 20) {
                return true;
            }
        }
        for (Place p : places) {
            if (p.tile == null && p.contains((int) Math.floor(x), (int) Math.floor(z), 20)) {
                return true;
            }
        }
        return false;
    }

    /** A run of city blocks along one row, with the same neighbours above and below all along it. */
    private record Run(int i0, int i1, int j, boolean north, boolean south) {}

    private void city() {
        // every block of the grid whose square, streets and all, is the city's dry land
        int lo = Math.floorDiv(-FENCE, PITCH), hi = Math.floorDiv(FENCE, PITCH);
        for (int i = lo; i <= hi; i++) {
            double x = (i + 0.5) * PITCH;
            if (x < plan.core.minX - PITCH && inland.stream().noneMatch(c -> Math.abs(c[0] - x) < c[2] + PITCH)) {
                continue;
            }
            for (int j = lo; j <= hi; j++) {
                if (blockIn(i, j)) {
                    cityBlocks.add(County.key(i, j));
                }
            }
        }
        // cut into pieces: each SUPER x SUPER square of the grid into rectangles whose every side
        // has the city next door all along it, or nothing at all
        Map<Long, List<int[]>> squares = new HashMap<>();
        for (long k : cityBlocks) {
            int i = (int) (k >> 32), j = (int) k;
            squares.computeIfAbsent(County.key(Math.floorDiv(i, SUPER), Math.floorDiv(j, SUPER)), q -> new ArrayList<>()).add(new int[] {i, j});
        }
        List<Long> order = new ArrayList<>(squares.keySet());
        order.sort(Comparator.naturalOrder());
        for (long sq : order) {
            int si = (int) (sq >> 32) * SUPER, sj = (int) sq * SUPER;
            List<int[]> open = new ArrayList<>();  // rectangles still growing: {i0, i1, j0, j1, north}
            for (int j = sj; j < sj + SUPER; j++) {
                List<Run> runs = new ArrayList<>();
                for (int i = si; i < si + SUPER; i++) {
                    if (!in(i, j)) {
                        continue;
                    }
                    boolean n = in(i, j - 1), s = in(i, j + 1);
                    Run last = runs.isEmpty() ? null : runs.get(runs.size() - 1);
                    if (last != null && last.i1 == i - 1 && last.north == n && last.south == s) {
                        runs.set(runs.size() - 1, new Run(last.i0, i, j, n, s));
                    } else {
                        runs.add(new Run(i, i, j, n, s));
                    }
                }
                List<int[]> next = new ArrayList<>();
                for (Run r : runs) {
                    int[] grow = null;
                    for (int[] o : open) {
                        if (o[0] == r.i0 && o[1] == r.i1 && o[3] == j - 1 && o[5] == 1 && r.north
                                && in(r.i0 - 1, j) == in(r.i0 - 1, j - 1) && in(r.i1 + 1, j) == in(r.i1 + 1, j - 1)
                                && sameSides(o, j)) {
                            grow = o;
                        }
                    }
                    if (grow != null) {
                        open.remove(grow);
                        grow[3] = j;
                        grow[5] = r.south ? 1 : 0;
                        next.add(grow);
                    } else {
                        next.add(new int[] {r.i0, r.i1, j, j, r.north ? 1 : 0, r.south ? 1 : 0});
                    }
                }
                for (int[] o : open) {
                    tile(o);
                }
                open = next;
            }
            for (int[] o : open) {
                tile(o);
            }
        }
    }

    /** True if the rectangle's west and east neighbours are the same in every row down to row j. */
    private boolean sameSides(int[] o, int j) {
        boolean w = in(o[0] - 1, o[2]), e = in(o[1] + 1, o[2]);
        for (int r = o[2]; r <= j; r++) {
            if (in(o[0] - 1, r) != w || in(o[1] + 1, r) != e) {
                return false;
            }
        }
        return true;
    }

    /** One piece of the city: blocks i0..i1 by j0..j1, its edges shared or whole. */
    private void tile(int[] o) {
        int i0 = o[0], i1 = o[1], j0 = o[2], j1 = o[3];
        boolean north = o[4] == 1, south = o[5] == 1, west = in(i0 - 1, j0), east = in(i1 + 1, j0);
        int x0 = west ? i0 * PITCH : i0 * PITCH - EDGE, x1 = east ? (i1 + 1) * PITCH - 1 : (i1 + 1) * PITCH + EDGE;
        int z0 = north ? j0 * PITCH : j0 * PITCH - EDGE, z1 = south ? (j1 + 1) * PITCH - 1 : (j1 + 1) * PITCH + EDGE;
        double cx = (x0 + x1) / 2.0, cz = (z0 + z1) / 2.0;
        District best = null;
        double bestT = 1e9;
        for (District d : districts) {
            double t = Math.hypot(cx - d.x, cz - d.z) / d.radius + d.offset;
            if (t < bestT) {
                bestT = t;
                best = d;
            }
        }
        if (best == null) {
            return;
        }
        // far from every district's heart the city is mid-rise brick and walk-ups
        String variant = bestT > 1.6 ? "city" : best.variant;
        Place p = new Place(PlaceType.DOWNTOWN, variant, x0, z0, x1, z1, Terrain.CITY, Noise.hash(seed ^ 0x711EL, x0, z0), -1, -1,
                new Place.Tile(PITCH, best.x, best.z, best.radius, best.offset));
        places.add(p);
    }

    /** The piece of the city standing at (x, z), if any. */
    private boolean inCity(double x, double z) {
        for (Place p : placesNear((int) x, (int) z, (int) x, (int) z, 0)) {
            if (p.tile != null) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the fill

    private double roll(long salt, int a, int b) {
        return Noise.unit(Noise.hash(seed ^ salt, a, b));
    }

    private boolean nearSpot(double x, double z, double d) {
        for (Plan.Spot s : plan.spots) {
            if (Math.hypot(s.x() - x, s.z() - z) < d) {
                return true;
            }
        }
        return false;
    }

    private boolean blocked(double x, double z) {
        return plan.inAny(plan.military, x, z) || plan.toWater(x, z) < 140 || Math.abs(x) > FENCE - 150 || Math.abs(z) > FENCE - 150
                || plan.marsh.contains(x, z);
    }

    /** Suburbs through the sprawl outside the wall and round the towns, a stop here and there. */
    private void suburbs() {
        int step = 380;
        for (int gx = Math.floorDiv(-FENCE, step); gx <= Math.floorDiv(FENCE, step); gx++) {
            for (int gz = Math.floorDiv(-FENCE, step); gz <= Math.floorDiv(FENCE, step); gz++) {
                int x = gx * step + step / 2 + (int) ((roll(0x5B1L, gx, gz) - 0.5) * 80);
                int z = gz * step + step / 2 + (int) ((roll(0x5B2L, gx, gz) - 0.5) * 80);
                if (!plan.inSprawl(x, z) || urban(x, z) || plan.inAny(plan.military, x, z) || plan.marsh.contains(x, z)
                        || Math.abs(x) > FENCE - 200 || Math.abs(z) > FENCE - 200 || nearSpot(x, z, 330)) {
                    continue;
                }
                double r = roll(0x5B3L, gx, gz), v = roll(0x5B4L, gx, gz);
                long h = Noise.hash(seed ^ 0x5B5L, gx, gz);
                Place p;
                if (r < 0.74) {
                    // the old grids hug the wall and the town centres, the newer streets lie further out
                    double out = -Plan.depth(plan.core, x, z);
                    String variant = v < 0.08 ? "trailer_park" : out < 500 && v < 0.7 ? "old_grid" : v < 0.65 ? "cul_de_sac" : "estates";
                    int[] sz = size(PlaceType.SUBURB, variant);
                    p = county.fitOrShrink(PlaceType.SUBURB, variant, x, z, sz[0], sz[1], h);
                } else if (r < 0.86) {
                    String[] kinds = {"gas_station", "motel", "diner", "church", "school", "warehouse", "trailer_lot"};
                    String kind = kinds[(int) (v * kinds.length)];
                    int[] sz = size(PlaceType.ROADSIDE, kind);
                    p = county.fitOrShrink(PlaceType.ROADSIDE, kind, x, z, sz[0], sz[1], h);
                } else {
                    p = null;  // a patch of woods or a park left between the streets
                }
                if (p != null && clear(p, 30)) {
                    places.add(p);
                    filler.add(p);
                }
            }
        }
    }

    /** Farm country and the woods, as the plan's fill rule lays them out, and stops along the roads. */
    private void country() {
        int step = 820;
        for (int gx = 0; gx * step < 2 * 8192 - 500; gx++) {
            for (int gz = 0; gz * step < 2 * 8192 - 500; gz++) {
                int x = -8192 + 500 + gx * step + (int) ((roll(0xFA1L, gx, gz) - 0.5) * 520);
                int z = -8192 + 500 + gz * step + (int) ((roll(0xFA2L, gx, gz) - 0.5) * 520);
                double r = roll(0xFA3L, gx, gz);
                if (blocked(x, z) || plan.inSprawl(x, z) || urban(x, z) || nearSpot(x, z, 650)) {
                    continue;
                }
                boolean farm = plan.inAny(plan.farms, x, z), mtn = plan.mountains.contains(x, z);
                if (!(farm && r < 0.62 || mtn && r < 0.12 || !farm && !mtn && r < 0.18)) {
                    continue;
                }
                double v = roll(0xFA4L, gx, gz);
                String variant = v < 0.30 ? "crop" : v < 0.52 ? "dairy" : v < 0.70 ? "horse_ranch" : v < 0.85 ? "orchard" : "homestead";
                int[] sz = size(PlaceType.FARM, variant);
                long h = Noise.hash(seed ^ 0xFA5L, gx, gz);
                Place p = variant.equals("homestead") ? county.fit(PlaceType.FARM, variant, x, z, sz[0], sz[1], h)
                        : county.fitOrShrink(PlaceType.FARM, variant, x, z, sz[0], sz[1], h);
                if (p != null && clear(p, 60)) {
                    places.add(p);
                    filler.add(p);
                }
            }
        }
        // the stops along the state roads and US-1, a side of the road each in turn
        String[] kinds = {"gas_station", "motel", "diner", "church", "school", "warehouse", "trailer_lot"};
        for (Plan.Line rd : plan.roads) {
            if (!rd.cls.equals("ky") && !rd.cls.equals("us") || rd.blownAt >= 0) {
                continue;
            }
            Plan.Shape s = rd.shape;
            double[] at = cumulative(s);
            double len = at[at.length - 1];
            int side = 1, n = 0;
            for (double d = 600; d < len - 300; d += 800 + roll(0x5709L, rd.id.hashCode(), n) * 600, n++) {
                double[] q = pointAt(s, at, d);
                int x = (int) (q[0] - q[3] * 130 * side), z = (int) (q[1] + q[2] * 130 * side);
                side = -side;
                if (blocked(x, z) || urban(x, z) || nearSpot(x, z, 650)) {
                    continue;
                }
                String kind = kinds[(int) (roll(0x570AL, x, z) * kinds.length)];
                int[] sz = size(PlaceType.ROADSIDE, kind);
                Place p = county.fitOrShrink(PlaceType.ROADSIDE, kind, x, z, sz[0], sz[1], Noise.hash(seed ^ 0x570BL, x, z));
                if (p != null && clear(p, 60)) {
                    places.add(p);
                    filler.add(p);
                }
            }
        }
    }

    private static double[] cumulative(Plan.Shape s) {
        double[] at = new double[s.count()];
        for (int i = 1; i < s.count(); i++) {
            at[i] = at[i - 1] + Math.hypot(s.x(i) - s.x(i - 1), s.z(i) - s.z(i - 1));
        }
        return at;
    }

    /** The point d along a line: {x, z, tangent x, tangent z}. */
    private static double[] pointAt(Plan.Shape s, double[] at, double d) {
        int i = 0;
        while (i < at.length - 2 && at[i + 1] < d) {
            i++;
        }
        double len = Math.max(1e-9, at[i + 1] - at[i]), t = Math.max(0, Math.min(1, (d - at[i]) / len));
        double tx = (s.x(i + 1) - s.x(i)) / len, tz = (s.z(i + 1) - s.z(i)) / len;
        return new double[] {s.x(i) + (s.x(i + 1) - s.x(i)) * t, s.z(i) + (s.z(i + 1) - s.z(i)) * t, tx, tz};
    }

    // ------------------------------------------------------------------ roads

    private static Road.Kind kind(String cls, String id) {
        return switch (cls) {
            case "i", "us" -> Road.Kind.HIGHWAY;
            default -> id.equals("tanktrail") ? Road.Kind.TRACK : Road.Kind.COUNTY;
        };
    }

    /** The named place whose footprint (plus margin) holds (x, z). */
    private Place placeAt(double x, double z, int margin) {
        for (Place p : named.values()) {
            if (p.contains((int) Math.floor(x), (int) Math.floor(z), margin)) {
                return p;
            }
        }
        return null;
    }

    /**
     * The plan's roads, each resampled and broken where it runs into one of the plan's places (it
     * ends at the gate facing the way it came), into the city (it ends at the city's edge street,
     * which carries on) or at a blown span; bridged where it crosses water.
     */
    private void planRoads() {
        for (Plan.Line rd : plan.roads) {
            Road.Kind kind = kind(rd.cls, rd.id);
            List<double[]> pts = resample(rd.shape, Road.STEP);
            double total = (pts.size() - 1) * (double) Road.STEP;
            double blown = rd.blownAt >= 0 ? rd.blownAt * total : -1;
            // what each point is in: 0 open road, 1 a place, 2 the city, 3 the blown gap
            int[] what = new int[pts.size()];
            Place[] at = new Place[pts.size()];
            for (int i = 0; i < pts.size(); i++) {
                double[] q = pts.get(i);
                Place p = placeAt(q[0], q[1], 24);
                if (blown >= 0 && Math.abs(i * Road.STEP - blown) < 45) {
                    what[i] = 3;
                } else if (p != null) {
                    what[i] = 1;
                    at[i] = p;
                } else if (inCity(q[0], q[1])) {
                    what[i] = 2;
                }
            }
            int i = 0;
            while (i < pts.size()) {
                if (what[i] != 0) {
                    i++;
                    continue;
                }
                int j = i;
                while (j + 1 < pts.size() && what[j + 1] == 0) {
                    j++;
                }
                List<double[]> run = new ArrayList<>(pts.subList(i, j + 1));
                Place from = i > 0 && what[i - 1] == 1 ? at[i - 1] : null, to = j + 1 < pts.size() && what[j + 1] == 1 ? at[j + 1] : null;
                // into the city: a few steps on, so it reaches the edge street
                if (i > 0 && what[i - 1] == 2) {
                    for (int k = 1; k <= 3 && i - k >= 0; k++) {
                        run.add(0, pts.get(i - k));
                    }
                }
                if (j + 1 < pts.size() && what[j + 1] == 2) {
                    for (int k = 1; k <= 3 && j + k < pts.size(); k++) {
                        run.add(pts.get(j + k));
                    }
                }
                Road r = road(kind, run, from, to, Noise.hash(seed ^ 0x40ADL, rd.id.hashCode(), i));
                if (r != null) {
                    roads.add(r);
                    planRoads.add(r);
                }
                i = j + 1;
            }
        }
    }

    private static List<double[]> resample(Plan.Shape s, double step) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < s.count(); i++) {
            double ax = s.x(i), az = s.z(i), bx = s.x(i + 1), bz = s.z(i + 1);
            int n = Math.max(1, (int) Math.round(Math.hypot(bx - ax, bz - az) / step));
            for (int k = 0; k < n; k++) {
                out.add(new double[] {ax + (bx - ax) * k / n, az + (bz - az) * k / n});
            }
        }
        out.add(new double[] {s.x(s.count() - 1), s.z(s.count() - 1)});
        return out;
    }

    /**
     * A road along the points, starting at from's gate and ending at to's (either may be null: the
     * road starts where the points do). Corners rounded, heights from the ground, bridged over
     * water, the climb smoothed, the ends pinned to the places' ground.
     */
    private Road road(Road.Kind kind, List<double[]> run, Place from, Place to, long rs) {
        if (run.size() < 3 && from == null && to == null) {
            return null;
        }
        int[][] out = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int lead = 24;
        List<double[]> pts = new ArrayList<>();
        if (from != null) {
            int side = gateSide(from, run.get(Math.min(run.size() - 1, 6)));
            int[] g = from.gate(side);
            pts.add(new double[] {g[0], g[1]});
            pts.add(new double[] {g[0] + out[side][0] * lead, g[1] + out[side][1] * lead});
            trim(run, from, true);
        }
        pts.addAll(run);
        if (to != null) {
            trim(pts, to, false);
            int side = gateSide(to, pts.get(Math.max(0, pts.size() - 7)));
            int[] g = to.gate(side);
            pts.add(new double[] {g[0] + out[side][0] * lead, g[1] + out[side][1] * lead});
            pts.add(new double[] {g[0], g[1]});
        }
        if (pts.size() < 3) {
            return null;
        }
        // resample every STEP, then round the corners with the ends held
        List<double[]> line = new ArrayList<>();
        for (int i = 0; i + 1 < pts.size(); i++) {
            double[] p = pts.get(i), q = pts.get(i + 1);
            int n = Math.max(1, (int) Math.round(Math.hypot(q[0] - p[0], q[1] - p[1]) / Road.STEP));
            for (int s = 0; s < n; s++) {
                line.add(new double[] {p[0] + (q[0] - p[0]) * s / n, p[1] + (q[1] - p[1]) * s / n});
            }
        }
        line.add(pts.get(pts.size() - 1));
        int keep = lead / Road.STEP + 1;
        for (int pass = 0; pass < 6; pass++) {
            for (int i = keep; i < line.size() - keep; i++) {
                double[] p = line.get(i - 1), q = line.get(i + 1);
                line.set(i, new double[] {(p[0] + q[0] + line.get(i)[0] * 2) / 4, (p[1] + q[1] + line.get(i)[1] * 2) / 4});
            }
        }
        int n = line.size();
        double[] xs = new double[n], zs = new double[n], ys = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = line.get(i)[0];
            zs[i] = line.get(i)[1];
            List<Place> near = placesNear((int) xs[i], (int) zs[i], (int) xs[i], (int) zs[i], County.SKIRT);
            double g = county.ground(xs[i], zs[i], near);
            ys[i] = Terrain.isWater(g) ? Terrain.WATER + 4 : Math.max(g, Terrain.WATER + 2);
        }
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
            if (from != null) {
                ys[i] = Terrain.lerp(ys[i], from.level, Math.max(0, 1 - i / (double) keep));
            }
            if (to != null) {
                ys[i] = Terrain.lerp(ys[i], to.level, Math.max(0, 1 - (n - 1 - i) / (double) keep));
            }
        }
        return new Road(kind, xs, zs, ys, rs, from, to);
    }

    /** The side of p a road arriving from (or leaving toward) the point should use. */
    private static int gateSide(Place p, double[] toward) {
        int side;
        double dx = toward[0] - p.centreX(), dz = toward[1] - p.centreZ();
        side = Math.abs(dx) * p.depth() > Math.abs(dz) * p.width() ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
        if (side == p.waterSide) {
            boolean alongX = (side & 1) == 0;
            double d = alongX ? dx : dz;
            side = alongX ? (d > 0 ? 1 : 3) : (d > 0 ? 2 : 0);
        }
        return side;
    }

    /** Drops the points at the start (or end) of the list that come within 40 of the place. */
    private static void trim(List<double[]> pts, Place p, boolean start) {
        while (pts.size() > 2) {
            double[] q = start ? pts.get(0) : pts.get(pts.size() - 1);
            if (p.outside(q[0], q[1]) >= 40) {
                return;
            }
            pts.remove(start ? 0 : pts.size() - 1);
        }
    }

    /**
     * A road from each suburb, farm and stop: to its nearest neighbour or two, and to the nearest
     * of the plan's roads, so there's always a way out to the highway.
     */
    private void links() {
        Set<Long> pairs = new HashSet<>();
        Map<Place, Integer> ids = new IdentityHashMap<>();
        for (Place p : places) {
            ids.put(p, ids.size());
        }
        for (Place a : filler) {
            List<Place> near = new ArrayList<>();
            for (Place b : placesNear(a.centreX() - 700, a.centreZ() - 700, a.centreX() + 700, a.centreZ() + 700, 0)) {
                if (b != a && b.tile == null && b.type != PlaceType.LANDMARK) {
                    near.add(b);
                }
            }
            near.sort(Comparator.comparingDouble(b -> Math.hypot(b.centreX() - a.centreX(), b.centreZ() - a.centreZ())));
            int want = a.type == PlaceType.SUBURB ? 2 : 1;
            for (int k = 0; k < Math.min(want, near.size()); k++) {
                Place b = near.get(k);
                long key = Math.min(ids.get(a), ids.get(b)) * 100000L + Math.max(ids.get(a), ids.get(b));
                if (pairs.add(key)) {
                    Road r = county.build(a, b, 120);
                    if (r != null) {
                        roads.add(r);
                    }
                }
            }
            spur(a);
        }
        // a fixed place no plan road comes into (a port the coast moved off its pin) gets a spur too
        Set<Place> fill = Collections.newSetFromMap(new IdentityHashMap<>());
        fill.addAll(filler);
        for (Place p : places) {
            if (p.tile == null && p.type != PlaceType.LANDMARK && !fill.contains(p) && !reached(p)) {
                spur(p);
            }
        }
    }

    /**
     * Grayhaven International's road off I-95: its tiles are landmarks, which get no spur, so this
     * runs from the gap in the airport's west fence where its access road comes out
     * ({@link Airport#ACCESS_Z}) to the nearest point on the plan's roads.
     */
    private void airportRoad() {
        Place tile = named.get("airport");
        if (tile == null) {
            return;
        }
        int[] o = Airport.origin(tile.variant);
        double gx = o[0] + Airport.X0 - 1, gz = o[1] + Airport.ACCESS_Z + 0.5;
        Road.Hit hit = new Road.Hit();
        for (Road r : planRoads) {
            for (int i = 0; i < r.xs.length; i++) {
                double d = Math.hypot(r.xs[i] - gx, r.zs[i] - gz);
                if (d < hit.distance) {
                    hit.distance = d;
                    hit.road = r;
                    hit.along = i;
                }
            }
        }
        if (hit.road == null || hit.distance > 1800) {
            return;
        }
        int i = (int) hit.along;
        double[] target = {hit.road.xs[i], hit.road.zs[i]};
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {gx, gz});
        pts.add(new double[] {gx - 12, gz});
        pts.add(new double[] {gx - 24, gz});
        county.wander(pts, gx - 24, gz, target[0], target[1], Noise.hash(seed ^ 0xA1A0L, o[0], o[1]), 0, 0.1);
        pts.add(target);
        Road r = road(Road.Kind.COUNTY, pts, null, null, Noise.hash(seed ^ 0xA1A1L, o[0], o[1]));
        if (r != null) {
            int n = r.ys.length;
            for (int k = 0; k < Math.min(8, n); k++) {
                r.ys[n - 1 - k] = Terrain.lerp(r.ys[n - 1 - k], hit.road.ys[i], 1 - k / 8.0);
                r.ys[k] = Terrain.lerp(r.ys[k], tile.level, 1 - k / 8.0);
            }
            roads.add(r);
        }
    }

    /** Whether one of the plan's roads ends at a place's gate. */
    private boolean reached(Place p) {
        for (Road r : planRoads) {
            for (int e : new int[] {0, r.xs.length - 1}) {
                if (r.xs[e] >= p.x0 - 40 && r.xs[e] <= p.x1 + 40 && r.zs[e] >= p.z0 - 40 && r.zs[e] <= p.z1 + 40) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A road from a place's gate to the nearest point on one of the plan's roads, if one is within 1800. */
    private void spur(Place a) {
        Road.Hit hit = new Road.Hit();
        double cx = a.centreX() + 0.5, cz = a.centreZ() + 0.5;
        for (Road r : planRoads) {
            for (int i = 0; i < r.xs.length; i += 2) {
                double d = Math.hypot(r.xs[i] - cx, r.zs[i] - cz);
                if (d < hit.distance) {
                    hit.distance = d;
                    hit.road = r;
                    hit.along = i;
                }
            }
        }
        if (hit.road == null || hit.distance > 1800 || hit.distance < Math.max(a.width(), a.depth()) / 2.0 + 20) {
            return;
        }
        int i = (int) hit.along;
        double[] target = {hit.road.xs[i], hit.road.zs[i]};
        int side = gateSide(a, target);
        int[] g = a.gate(side);
        int[][] out = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[] {g[0] + out[side][0] * 24, g[1] + out[side][1] * 24});
        county.wander(pts, pts.get(0)[0], pts.get(0)[1], target[0], target[1], Noise.hash(seed ^ 0x5EEDL, a.centreX(), a.centreZ()), 0, 0.15);
        pts.add(target);
        Road r = road(a.type == PlaceType.FARM ? Road.Kind.TRACK : Road.Kind.COUNTY, pts, a, null, Noise.hash(seed ^ 0x5B0L, g[0], g[1]));
        if (r != null) {
            // the far end comes down to the highway's own height
            int n = r.ys.length;
            for (int k = 0; k < Math.min(8, n); k++) {
                r.ys[n - 1 - k] = Terrain.lerp(r.ys[n - 1 - k], hit.road.ys[i], 1 - k / 8.0);
            }
            roads.add(r);
        }
    }
}
