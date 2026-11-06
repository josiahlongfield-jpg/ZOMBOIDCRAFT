package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/**
 * Zomboid's zone stories (its RandomizedZoneStory set) out in the county's country: the camps people made in the
 * woods when the towns went bad, the hunters' and trappers' camps, the graves someone dug for their own, a family's
 * fishing trip at the lake, the cookout, the beach party, the festival in a farmer's field, the sandlot game. Each is
 * built from refs/stories/zone with the kits (the zs_ kit, tools/city/zonestory.py).
 * <p>
 * The county is cut into cells of {@link #CELL} blocks, and each rolls for a story from the county's seed alone, so
 * every chunk agrees on what stands where and builds just its own part. A story goes where its zone is, as Zomboid
 * keeps them to its Forest, Beach and lake zones: the camps under the trees, the fishing trip and the beach party at
 * the water's edge facing it, the rest in open country; always on ground that is near enough level, clear of the
 * places, the roads and the railway. The trees give way round it (CountyGen.vegetation asks {@link #clearing}).
 * <p>
 * How often is the sandbox's Randomized Zone Stories Chance (ZoneStoryChance, LOOT's wiring, read through
 * {@link #rate}; new chunks only). The payoff is Zomboid's: the bag, box or cooler each story leaves, tagged
 * loot=story_&lt;name&gt; for LOOT's tables, and the bodies and the walkers for world.gen.StoryMarks
 * ("!body:outfit;limbs;pose", "!walkers:outfit;count;radius").
 */
public final class ZoneStories {
    /** The side of each story roll's square. */
    static final int CELL = 160;
    /** A cell's chance of a story at "sometimes"; the other settings scale it. */
    static final double BASE = 0.3;
    /** The widest story's reach from its middle, so a chunk knows which cells can touch it. */
    private static final int REACH = 16;

    /** The world's Randomized Zone Stories Chance, as {@link RoadStories#rate(String)} reads it. */
    static volatile DoubleSupplier rate = () -> Double.parseDouble(System.getProperty("zonestories.rate", "0.5"));

    /** Hands the generator the world's chance (world.gen.StoryMarks, from the sandbox). */
    public static void setRate(DoubleSupplier supplier) {
        rate = supplier;
    }

    /** Where Zomboid puts them: under trees, at a lake's or river's edge, on the sea's beach, in open country. */
    enum Zone { FOREST, SHORE, BEACH, FIELD }

    /** The stories, with weights in their zone, how far they reach from their middle and their zone. */
    enum Story {
        FOREST_CAMP(30, 7, Zone.FOREST), FOREST_CAMP_EATEN(10, 7, Zone.FOREST), HUNTER_CAMP(14, 8, Zone.FOREST),
        TRAPPER_CAMP(12, 9, Zone.FOREST), BURYING_CAMP(8, 8, Zone.FOREST), FISHING_TRIP(26, 7, Zone.SHORE),
        BEACH_PARTY(24, 8, Zone.BEACH), BBQ_PARTY(16, 7, Zone.FIELD), MUSIC_FEST(6, 13, Zone.FIELD),
        BASEBALL(8, 12, Zone.FIELD), FIELD_BURIAL(3, 8, Zone.FIELD);

        final int weight, radius;
        final Zone zone;

        Story(int weight, int radius, Zone zone) {
            this.weight = weight;
            this.radius = radius;
            this.zone = zone;
        }

        /** LOOT's table for the story's searchable: data/zomboidcraft/loot_table/shop/story_&lt;name&gt;.json. */
        String loot() {
            return "story_" + (this == FOREST_CAMP_EATEN ? "forest_camp" : this == FIELD_BURIAL ? "burying_camp"
                    : name().toLowerCase(Locale.ROOT));
        }
    }

    /** One piece, in the world: a block standing on its column's ground (dy up), or a mark (x, z its spot). */
    private record Piece(int x, int z, int dy, String block, double fx, double fz, float yaw, String models, long seed) {
    }

    /** A story as rolled: its middle and reach, and its pieces. */
    record Scene(Story story, int x, int z, int radius, List<Piece> pieces) {
    }

    private static final Map<Long, Scene> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Scene> e) {
            return size() > 1024;
        }
    };
    private static final Scene NOTHING = new Scene(null, 0, 0, 0, List.of());
    private static double cachedRate = Double.NaN;

    private ZoneStories() {
    }

    private static double currentRate() {
        try {
            return Math.max(0, rate.getAsDouble());
        } catch (RuntimeException e) {
            return 0.5;
        }
    }

    /** The stories' pieces that fall in the 16x16 chunk at (bx, bz), each passed to out. */
    static void place(County county, int bx, int bz, RoadStories.Out out) {
        for (Scene sc : near(county, bx - REACH, bz - REACH, bx + 15 + REACH, bz + 15 + REACH)) {
            for (Piece p : sc.pieces) {
                if (p.models != null) {
                    if (p.fx >= bx && p.fx < bx + 16 && p.fz >= bz && p.fz < bz + 16) {
                        out.vehicle(p.fx, p.fz, p.yaw, p.models, p.seed);
                    }
                } else if (p.x >= bx && p.x < bx + 16 && p.z >= bz && p.z < bz + 16) {
                    out.block(p.x, p.z, p.dy, p.block);
                }
            }
        }
    }

    /** Whether (x, z) lies in a story's ground (within its reach plus pad), where trees and long grass give way. */
    static boolean clearing(County county, int x, int z, int pad) {
        for (Scene sc : near(county, x - REACH - pad, z - REACH - pad, x + REACH + pad, z + REACH + pad)) {
            int r = sc.radius + pad;
            if (Math.abs(x - sc.x) <= r && Math.abs(z - sc.z) <= r && Math.hypot(x - sc.x, z - sc.z) <= r + 0.5) {
                return true;
            }
        }
        return false;
    }

    /** Whether (x, z) lies within pad of one of the scenes' ground (a chunk's list from {@link #near}). */
    static boolean inside(List<Scene> scenes, int x, int z, int pad) {
        for (Scene sc : scenes) {
            int r = sc.radius + pad;
            if (Math.abs(x - sc.x) <= r && Math.abs(z - sc.z) <= r && Math.hypot(x - sc.x, z - sc.z) <= r + 0.5) {
                return true;
            }
        }
        return false;
    }

    /** Every story whose middle lies in the box (for the preview and the tools). */
    static List<Scene> near(County county, int x0, int z0, int x1, int z1) {
        double rate = currentRate();
        List<Scene> out = new ArrayList<>();
        if (rate <= 0) {
            return out;
        }
        for (int gx = Math.floorDiv(x0, CELL); gx <= Math.floorDiv(x1, CELL); gx++) {
            for (int gz = Math.floorDiv(z0, CELL); gz <= Math.floorDiv(z1, CELL); gz++) {
                Scene sc = scene(county, gx, gz, rate);
                if (sc != null && sc.x >= x0 && sc.x <= x1 && sc.z >= z0 && sc.z <= z1) {
                    out.add(sc);
                }
            }
        }
        return out;
    }

    private static Scene scene(County county, int gx, int gz, double rate) {
        long key = (long) gx << 32 ^ (gz & 0xFFFFFFFFL) ^ county.seed * 0x9E3779B97F4A7C15L;
        synchronized (CACHE) {
            if (rate != cachedRate) {
                CACHE.clear();
                cachedRate = rate;
            }
            Scene hit = CACHE.get(key);
            if (hit != null) {
                return hit == NOTHING ? null : hit;
            }
        }
        Scene sc = roll(county, gx, gz, rate);
        synchronized (CACHE) {
            CACHE.put(key, sc == null ? NOTHING : sc);
        }
        return sc;
    }

    private static Scene roll(County county, int gx, int gz, double rate) {
        SplittableRandom rng = new SplittableRandom(Noise.hash(county.seed ^ 0x2057L, gx, gz));
        if (rng.nextDouble() >= Math.min(1, BASE * rate)) {
            return null;
        }
        for (int t = 0; t < 8; t++) {
            int x = gx * CELL + REACH + rng.nextInt(CELL - 2 * REACH), z = gz * CELL + REACH + rng.nextInt(CELL - 2 * REACH);
            if (rng.nextDouble() < 0.75) {
                // people camp by water: a spot that has some in reach is walked down to a few blocks from its edge
                int[] d = shoreward(county, x, z);
                if (d != null) {
                    x = d[0];
                    z = d[1];
                }
            }
            Zone zone = zone(county, x, z);
            if (zone == null || zone == Zone.FIELD && rng.nextDouble() < 0.5) {
                continue;   // open country is most of the county; the woods and the water are where people went
            }
            Story story = pick(rng, zone);
            int q = zone == Zone.SHORE || zone == Zone.BEACH ? toWater(county, x, z, story.radius) : rng.nextInt(4);
            if (q < 0 || !fits(county, x, z, story, q)) {
                continue;
            }
            Scene sc = new Scene(story, x, z, story.radius, new ArrayList<>());
            new Builder(county, sc, q, new SplittableRandom(rng.nextLong())).build();
            return sc;
        }
        return null;
    }

    /** The spot five or six blocks back from the nearest water straight along x or z within 80, or null. */
    private static int[] shoreward(County county, int x, int z) {
        int[] best = null;
        int bestK = 81;
        for (int q = 0; q < 4; q++) {
            int[] d = turn(q, 0, -1);
            for (int k = 1; k < bestK; k++) {
                if (dryTop(county.terrain, x + d[0] * k, z + d[1] * k) == Integer.MIN_VALUE) {
                    bestK = k;
                    int back = Math.max(0, k - 5 - (k & 1));
                    best = new int[] {x + d[0] * back, z + d[1] * back};
                    break;
                }
            }
        }
        return best;
    }

    /** The zone at (x, z): under the trees, by water (the sea's beaches apart), or open country away from town. */
    static Zone zone(County county, int x, int z) {
        Terrain t = county.terrain;
        if (dryTop(t, x, z) == Integer.MIN_VALUE) {
            return null;
        }
        Plan plan = t.plan;
        if (plan != null && (t.city(x, z) > 0.15 || t.sprawl(x, z) > 0.35)) {
            return null;
        }
        for (int r = 3; r <= 9; r += 2) {
            for (int k = 0; k < 4; k++) {
                int wx = x + (k == 0 ? r : k == 1 ? -r : 0), wz = z + (k == 2 ? r : k == 3 ? -r : 0);
                if (dryTop(t, wx, wz) == Integer.MIN_VALUE) {
                    boolean sea = plan != null && plan.coast(wx, wz) < 2 && t.water(wx, wz) == Terrain.WATER;
                    return sea ? Zone.BEACH : Zone.SHORE;
                }
            }
        }
        double f = t.forest(x, z);
        return f > 0.6 ? Zone.FOREST : f < 0.3 ? Zone.FIELD : null;
    }

    private static Story pick(SplittableRandom rng, Zone zone) {
        int total = 0;
        for (Story s : Story.values()) {
            total += s.zone == zone ? s.weight : 0;
        }
        int roll = rng.nextInt(total);
        for (Story s : Story.values()) {
            if (s.zone == zone) {
                roll -= s.weight;
                if (roll < 0) {
                    return s;
                }
            }
        }
        return Story.FOREST_CAMP;
    }

    /** The quarter turn that points the story's front (its -z) at the nearest water, or -1 if none is near. */
    static int toWater(County county, int x, int z, int radius) {
        int best = -1, bestD = Integer.MAX_VALUE;
        for (int q = 0; q < 4; q++) {
            int[] d = turn(q, 0, -1);
            for (int k = 2; k <= radius + 4 && k < bestD; k++) {
                if (dryTop(county.terrain, x + d[0] * k, z + d[1] * k) == Integer.MIN_VALUE) {
                    best = q;
                    bestD = k;
                    break;
                }
            }
        }
        return bestD >= 3 ? best : -1;
    }

    /**
     * Whether the story fits round (x, z) turned q: the ground near level (one step at most) and dry behind the
     * water's edge, no place, hideout, road or railway near.
     */
    static boolean fits(County county, int x, int z, Story story, int q) {
        int r = story.radius;
        int reach = r + County.SKIRT;
        for (Place p : county.placesNear(x - reach, z - reach, x + reach, z + reach, 0)) {
            if (p.contains(x, z, reach)) {
                return false;
            }
        }
        if (!county.hideoutsNear(x - r - 20, z - r - 20, x + r + 20, z + r + 20, 0).isEmpty()) {
            return false;
        }
        Road.Hit hit = new Road.Hit();
        for (Road road : county.roadsNear(x - r - 30, z - r - 30, x + r + 30, z + r + 30)) {
            road.nearest(x + 0.5, z + 0.5, hit);
        }
        if (hit.road != null && hit.distance < hit.road.kind.half + 9 + r) {
            return false;
        }
        if (RailGen.distance(county, x + 0.5, z + 0.5, r + 40) < r + 30) {
            return false;
        }
        Terrain t = county.terrain;
        boolean watery = story.zone == Zone.SHORE || story.zone == Zone.BEACH;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, dry = 0, all = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r + r) {
                    continue;
                }
                all++;
                int top = dryTop(t, x + dx, z + dz);
                if (top == Integer.MIN_VALUE) {
                    // only the front may run into the water, and only for the stories at its edge
                    int[] local = turn((4 - q) & 3, dx, dz);
                    if (!watery || local[1] > -2) {
                        return false;
                    }
                    continue;
                }
                dry++;
                lo = Math.min(lo, top);
                hi = Math.max(hi, top);
            }
        }
        return hi - lo <= 1 && dry > all * 0.6;
    }

    /** The ground's top block at (x, z) as CountyGen lays it away from places and roads; MIN_VALUE under water. */
    static int dryTop(Terrain t, int x, int z) {
        int top = (int) Math.floor(t.height(x, z));
        return top < t.water(x, z) ? Integer.MIN_VALUE : top;
    }

    /** (x, z) turned q quarters clockwise (north to east). */
    static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static final String[] FACES = {"north", "east", "south", "west"};

    /** Lays one story out in its own frame (front toward -z), turned q into the world. */
    private static final class Builder {
        private static final int N = 0, E = 1, S = 2, W = 3, X = 4, Z = 5, NONE = -1;
        private final County county;
        private final Scene sc;
        private final int q;
        private final SplittableRandom rng;
        private final boolean[][] used;

        Builder(County county, Scene sc, int q, SplittableRandom rng) {
            this.county = county;
            this.sc = sc;
            this.q = q;
            this.rng = rng;
            int n = 2 * sc.radius + 1;
            used = new boolean[n][n];
        }

        // ---- frame helpers

        private int[] world(int dx, int dz) {
            int[] w = turn(q, dx, dz);
            return new int[] {sc.x + w[0], sc.z + w[1]};
        }

        private int top(int dx, int dz) {
            int[] w = world(dx, dz);
            return dryTop(county.terrain, w[0], w[1]);
        }

        private boolean free(int dx, int dz) {
            int r = sc.radius;
            if (Math.abs(dx) > r || Math.abs(dz) > r) {
                return false;
            }
            return !used[dx + r][dz + r] && top(dx, dz) != Integer.MIN_VALUE;
        }

        private String turned(String block, int face) {
            if (face == NONE) {
                return block;
            }
            String prop;
            if (face == X || face == Z) {
                boolean xAxis = (face == X) == (q % 2 == 0);
                prop = "axis=" + (xAxis ? "x" : "z");
            } else {
                prop = "facing=" + FACES[(face + q) & 3];
            }
            int b = block.indexOf('[');
            return b < 0 ? block + "[" + prop + "]" : block.substring(0, b + 1) + prop + "," + block.substring(b + 1);
        }

        /** A block on the ground at (dx, dz) in the story's frame, if that ground is dry and free. */
        private boolean put(int dx, int dz, int dy, String block, int face) {
            if (dy == 0 && !free(dx, dz) || top(dx, dz) == Integer.MIN_VALUE) {
                return false;
            }
            if (dy == 0) {
                used[dx + sc.radius][dz + sc.radius] = true;
            }
            int[] w = world(dx, dz);
            sc.pieces.add(new Piece(w[0], w[1], dy, turned(block, face), 0, 0, 0, null, 0));
            return true;
        }

        private boolean put(int dx, int dz, String block, int face) {
            return put(dx, dz, 0, block, face);
        }

        /** The ground worn bare: the top block itself, under whatever stands there. */
        private void bare(int dx, int dz, String block) {
            put(dx, dz, -1, block, NONE);
        }

        private void mark(double dx, double dz, String models) {
            double ax = dx, az = dz;
            for (int i = 0; i < q; i++) {
                double t = ax;
                ax = -az;
                az = t;
            }
            sc.pieces.add(new Piece(0, 0, 0, null, sc.x + ax + 0.5, sc.z + az + 0.5, (float) (rng.nextDouble() * 360), models, rng.nextLong()));
        }

        private void body(double dx, double dz, String outfit, int limbs) {
            mark(dx, dz, "!body:" + outfit + ";" + limbs + ";0");
            if (rng.nextDouble() < 0.6) {
                put((int) Math.round(dx), (int) Math.round(dz), rng.nextBoolean() ? "home_blood_pool" : "blood_splat", NONE);
            }
        }

        private void walkers(String outfit, int count, int radius) {
            if (count > 0) {
                mark(0, 0, "!walkers:" + outfit + ";" + count + ";" + radius);
            }
        }

        private boolean chance(double p) {
            return rng.nextDouble() < p;
        }

        private int any(int n) {
            return rng.nextInt(n);
        }

        private int limbs() {
            return chance(0.35) ? 1 << any(8) : 0;
        }

        /** A spot on the story's ground at a distance between r0 and r1 from its middle, free; null if none. */
        private int[] spot(int r0, int r1) {
            for (int t = 0; t < 30; t++) {
                double a = rng.nextDouble() * Math.PI * 2, d = r0 + rng.nextDouble() * (r1 - r0);
                int dx = (int) Math.round(Math.cos(a) * d), dz = (int) Math.round(Math.sin(a) * d);
                if (free(dx, dz)) {
                    return new int[] {dx, dz};
                }
            }
            return null;
        }

        private void scatter(String block, int face, int n, int r0, int r1) {
            for (int i = 0; i < n; i++) {
                int[] s = spot(r0, r1);
                if (s != null) {
                    put(s[0], s[1], block, face < 0 ? NONE : face == 9 ? any(4) : face);
                }
            }
        }

        /**
         * A two-by-two tent whose door's left block is at (dx, dz), its door facing face (in the story's frame):
         * pitched where its four blocks share one level, come down where they don't (or when asked).
         */
        private void tent(int dx, int dz, int face, String colour, boolean open, boolean down) {
            if (!down) {
                // pitched on the nearest level ground to where it was meant to go
                for (int[] o : new int[][] {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                    if (pitch(dx + o[0], dz + o[1], face, colour, open, false, true)) {
                        return;
                    }
                }
            }
            pitch(dx, dz, face, colour, open, true, false);
        }

        /** The tent at (dx, dz), standing or down; with level, only if its four blocks share a level. */
        private boolean pitch(int dx, int dz, int face, String colour, boolean open, boolean down, boolean level) {
            int[][] cells = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
            String[] parts = {open ? "open_l" : "door_l", open ? "open_r" : "door_r", "end_l", "end_r"};
            int[][] at = new int[4][];
            int ground = Integer.MIN_VALUE;
            boolean flat = true;
            for (int i = 0; i < 4; i++) {
                int[] o = turn(face, cells[i][0], cells[i][1]);
                at[i] = new int[] {dx + o[0], dz + o[1]};
                if (!free(at[i][0], at[i][1])) {
                    return false;
                }
                int t = top(at[i][0], at[i][1]);
                flat &= ground == Integer.MIN_VALUE || t == ground;
                ground = t;
            }
            if (level && !flat) {
                return false;
            }
            for (int i = 0; i < 4; i++) {
                if (down || !flat) {
                    if (i == 0 || i == 3 || chance(0.5)) {
                        put(at[i][0], at[i][1], "zs_tent_" + colour + "_down", any(4));
                    }
                } else {
                    put(at[i][0], at[i][1], "zs_tent_" + colour + "_" + parts[i], face);
                }
            }
            return true;
        }

        private String colour() {
            return switch (any(3)) {
                case 0 -> "green";
                case 1 -> "blue";
                default -> "orange";
            };
        }

        /** The fire and what's round it: the ring of stones, the ground trodden bare, wood, logs to sit on. */
        private void fire(int dx, int dz, boolean seats) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    if (x * z == 0 || chance(0.5)) {
                        bare(dx + x, dz + z, chance(0.7) ? "minecraft:coarse_dirt" : "minecraft:podzol");
                    }
                }
            }
            put(dx, dz, "yd_firering", NONE);
            put(dx - 2, dz - 1, "fm_firewood", X);
            if (seats) {
                put(dx + 2, dz, "zs_log", Z);
                if (chance(0.6)) {
                    put(dx, dz - 2, "zs_log", X);
                }
            }
        }

        // ---- the stories

        void build() {
            switch (sc.story) {
                case FOREST_CAMP -> forestCamp(false);
                case FOREST_CAMP_EATEN -> forestCamp(true);
                case HUNTER_CAMP -> hunterCamp();
                case TRAPPER_CAMP -> trapperCamp();
                case BURYING_CAMP, FIELD_BURIAL -> buryingCamp();
                case FISHING_TRIP -> fishingTrip();
                case BEACH_PARTY -> beachParty();
                case BBQ_PARTY -> bbqParty();
                case MUSIC_FEST -> musicFest();
                case BASEBALL -> baseball();
                default -> { }
            }
        }

        /** A camp in the woods: a tent or two round a fire, chairs, the cooler, a pack (refs: forest-camp). */
        private void forestCamp(boolean eaten) {
            fire(0, 0, true);
            String c = colour();
            tent(-1, 2, N, c, eaten || chance(0.5), eaten && chance(0.4));
            if (chance(0.55)) {
                tent(-3, 1, E, colour(), chance(0.4), eaten && chance(0.5));
            }
            put(2, 2, "zs_ruck_forest", W);
            put(-2, -3, "sb_cooler", any(4));
            put(1, -3, "zs_cans", NONE);
            put(2, -2, eaten ? "trade_folding_chair_tipped" : "trade_folding_chair", W);
            if (chance(0.5)) {
                put(-2, -2, eaten && chance(0.5) ? "trade_folding_chair_tipped" : "trade_folding_chair", E);
            }
            put(1, 2, chance(0.5) ? "fm_lantern_floor" : "fm_bedroll", N);
            if (eaten) {
                // they were caught at the fire: the bodies where they fell, dragged, the blood
                int n = 2 + any(2);
                for (int i = 0; i < n; i++) {
                    int[] s = spot(1, 5);
                    if (s != null) {
                        body(s[0], s[1], chance(0.5) ? "civilian" : "hoodie", 1 << any(8) | (chance(0.4) ? 1 << any(8) : 0));
                    }
                }
                scatter("blood_splat", -1, 3, 1, 5);
                walkers("any", 2 + any(3), 8);
            } else {
                if (chance(0.3)) {
                    body(3, -1, chance(0.5) ? "civilian" : "hoodie", limbs());
                }
                walkers(chance(0.5) ? "civilian" : "hoodie", 1 + any(2), 6);
            }
        }

        /** The hunters' camp: a canvas tent, the fire, the deer they shot, the hide on its frame, the rifle case. */
        private void hunterCamp() {
            fire(0, 0, true);
            tent(-1, 3, N, "green", chance(0.5), false);
            put(3, 0, "zs_deer_b", N);
            put(3, 1, "zs_deer_a", N);
            put(-3, 1, "zs_hide_rack", E);
            put(2, 3, "zs_gun_case", W);
            put(-2, 3, "zs_ruck_hunter", E);
            put(1, -3, "trade_folding_chair", S);
            put(-1, -3, chance(0.5) ? "trade_folding_chair" : "sb_cooler", S);
            put(3, -2, "zs_cans", NONE);
            if (chance(0.25)) {
                body(4, 3, "farmer", limbs());
            }
            walkers("farmer", 1 + any(2), 7);
        }

        /** The trapper's camp: a tarp lean-to open to the fire, hides drying, traps and snares set round it. */
        private void trapperCamp() {
            fire(0, 0, false);
            put(-1, 2, "zs_lean", N);
            put(0, 2, "zs_lean_mid", N);
            put(1, 2, "zs_lean", N);
            put(2, 0, "zs_log", Z);
            put(-3, 0, "zs_hide_rack", E);
            put(-3, 2, "zs_hoop", E);
            put(3, 2, "zs_hoop", W);
            put(2, 3, "zs_ruck_trapper", W);
            put(-2, 3, "fm_firewood", X);
            scatter("zs_trap", 9, 2 + any(2), 5, 9);
            scatter("zs_snare", 9, 3 + any(3), 4, 9);
            walkers("farmer", any(2), 7);
        }

        /** The burial: graves in a row, one still open with the earth heaped by it, the dead waiting in their bags,
         * crosses of two sticks; the tent and fire of whoever dug them (refs: burying-camp). */
        private void buryingCamp() {
            int n = 2 + any(3);
            boolean open = true;
            for (int i = 0; i < n; i++) {
                int gx = -4 + i * 2;
                if (open && i == n - 1) {
                    put(gx, -3, -1, "zs_grave_a", N);
                    put(gx, -2, -1, "zs_grave_b", N);
                    used(gx, -3);
                    used(gx, -2);
                    put(gx + 1, -3, "zs_spoil", NONE);
                    put(gx + 1, -2, "zs_spoil_low", NONE);
                } else {
                    put(gx, -3, "ch_grave_mound", NONE);
                    put(gx, -2, "ch_grave_mound", NONE);
                    put(gx, -4, "cp_grave_cross", S);
                }
            }
            for (int i = 0; i < 1 + any(3); i++) {
                put(-3 + i * 2, 0, "bunk_bodybag", chance(0.5) ? E : W);
            }
            fire(2, 3, false);
            tent(-3, 3, N, colour(), chance(0.5), false);
            put(4, 2, "zs_ruck_burying", W);
            if (chance(0.5)) {
                body(-1, 1, "civilian", limbs());
            }
            walkers(chance(0.5) ? "survivor" : "civilian", 1 + any(2), 6);
        }

        private void used(int dx, int dz) {
            int r = sc.radius;
            if (Math.abs(dx) <= r && Math.abs(dz) <= r) {
                used[dx + r][dz + r] = true;
            }
        }

        /** The last dry block from (dx, dz) toward the water in front, or MIN_VALUE if it's all wet. */
        private int edge(int dx) {
            for (int dz = 0; dz >= -sc.radius; dz--) {
                if (top(dx, dz - 1) == Integer.MIN_VALUE) {
                    return dz;
                }
            }
            return Integer.MIN_VALUE;
        }

        /** A family's day at the water: rods propped at the edge, the tackle box open, chairs, the catch (refs: fishing-trip). */
        private void fishingTrip() {
            int rods = 2 + any(2);
            for (int i = 0; i < rods; i++) {
                int dx = -2 + i * 2;
                int e = edge(dx);
                if (e != Integer.MIN_VALUE) {
                    put(dx, e, "zs_rod", N);
                    put(dx, e + 2, "trade_folding_chair", N);
                }
            }
            int e = edge(1);
            if (e != Integer.MIN_VALUE) {
                put(1, e + 1, "zs_tackle", N);
                put(-1, e + 1, "zs_stringer", any(4));
                put(3, e + 1, "zs_rod_down", any(4));
                put(2, e + 3, "sb_cooler", N);
            }
            if (chance(0.5)) {
                fire(-3, 3, true);
            }
            if (chance(0.5)) {
                tent(1, 4, N, colour(), chance(0.5), false);
            }
            if (chance(0.3)) {
                body(0, 1, "civilian", limbs());
            }
            walkers("civilian", 1 + any(3), 6);
        }

        /** The beach party: towels, umbrellas, chairs facing the water, the cooler and the radio (refs: beach-party). */
        private void beachParty() {
            for (int i = 0; i < 4 + any(3); i++) {
                int dx = -6 + i * 2 + any(2), e = edge(dx);
                if (e == Integer.MIN_VALUE) {
                    continue;
                }
                int dz = e + 2 + any(2);
                put(dx, dz, chance(0.5) ? "sb_towel_a" : "sb_towel_b", N);
                if (chance(0.5)) {
                    put(dx + 1, dz, "sb_umbrella_lo", NONE);
                    put(dx + 1, dz, 1, chance(0.5) ? "sb_umbrella_hi_a" : "sb_umbrella_hi_b", NONE);
                } else if (chance(0.6)) {
                    put(dx, dz + 1, "sb_chair", N);
                }
            }
            put(0, 5, "zs_tote", N);
            put(1, 5, "sb_cooler", N);
            put(-1, 5, "pk_boombox", N);
            scatter("zs_cans", -1, 3, 2, 7);
            if (chance(0.5)) {
                body(2, 3, chance(0.5) ? "jogger" : "civilian", limbs());
            }
            walkers(chance(0.5) ? "jogger" : "civilian", 3 + any(3), 8);
        }

        /** The cookout: the grill, two picnic tables, chairs round, the cooler, the radio (refs: bbq-party). */
        private void bbqParty() {
            put(0, -3, "of_bbq", S);
            put(-1, 0, "hw_picnic_l", S);
            put(0, 0, "hw_picnic_r", S);
            if (chance(0.6)) {
                put(-1, 3, "hw_picnic_l", S);
                put(0, 3, "hw_picnic_r", S);
            }
            put(2, 0, "trade_folding_chair", W);
            put(2, 2, chance(0.5) ? "trade_folding_chair" : "trade_folding_chair_tipped", W);
            put(-3, 1, "trade_folding_chair", E);
            put(2, -3, "zs_cooler", W);
            put(-2, -3, "pk_boombox", S);
            scatter("zs_cans", -1, 3, 2, 6);
            if (chance(0.4)) {
                body(1, -2, "civilian", limbs());
            }
            walkers("civilian", 3 + any(3), 7);
        }

        /** A festival in a field: the speaker stacks at the front, the toilets, tents pitched behind, cans and bags
         * everywhere, and the crowd that never left (refs: music-fest). */
        private void musicFest() {
            for (int dx = -3; dx <= 3; dx++) {
                if (Math.abs(dx) >= 2) {
                    put(dx, -10, "of_speaker", S);
                    put(dx, -10, 1, "of_speaker", S);
                }
            }
            for (int i = 0; i < 3 + any(2); i++) {
                put(8, -4 + i, "st_porta_lo", W);
                put(8, -4 + i, 1, "st_porta_hi", W);
            }
            for (int i = 0; i < 4 + any(3); i++) {
                tent(-8 + i * 3 + any(2), 3 + any(5), any(4), colour(), chance(0.4), chance(0.25));
            }
            scatter("zs_cans", -1, 10, 2, 12);
            scatter("zs_fest_pack", 9, 2, 2, 10);
            put(0, 0, "pk_boombox", N);
            for (int i = 0; i < 1 + any(2); i++) {
                int[] s = spot(2, 9);
                if (s != null) {
                    body(s[0], s[1], chance(0.5) ? "hoodie" : "civilian", limbs());
                }
            }
            walkers(chance(0.5) ? "hoodie" : "civilian", 7 + any(5), 10);
        }

        /** A sandlot game: home, the bases a short diamond apart, the backstop, the bench, bats and gloves (refs: baseball). */
        private void baseball() {
            put(0, 8, "zs_home", NONE);
            put(7, 1, "zs_base", NONE);
            put(0, -6, "zs_base", NONE);
            put(-7, 1, "zs_base", NONE);
            put(0, 1, "zs_rubber", N);
            for (int dx = -2; dx <= 2; dx++) {
                put(dx, 11, "zs_fence", N);
                put(dx, 11, 1, "zs_fence_top", N);
            }
            put(4, 10, "zs_bench", X);
            put(5, 10, "zs_bench", X);
            put(6, 10, "bp_bats", N);
            put(4, 9, "zs_kitbag", N);
            put(3, 10, "bp_balls", N);
            put(1, 7, "zs_bat", any(4));
            put(5, 1, "zs_glove", any(4));
            if (chance(0.5)) {
                put(-2, 4, "zs_glove", any(4));
            }
            // the dirt worn round home and along the base paths, the mound
            for (int x = -1; x <= 1; x++) {
                for (int z = 7; z <= 9; z++) {
                    bare(x, z, "minecraft:coarse_dirt");
                }
            }
            int[][] bases = {{0, 8}, {7, 1}, {0, -6}, {-7, 1}, {0, 8}};
            for (int i = 0; i < 4; i++) {
                int[] a = bases[i], b = bases[i + 1];
                for (int k = 1; k < 7; k++) {
                    bare(a[0] + (b[0] - a[0]) * k / 7, a[1] + (b[1] - a[1]) * k / 7, chance(0.8) ? "minecraft:coarse_dirt" : "minecraft:dirt");
                }
            }
            bare(0, 1, "minecraft:coarse_dirt");
            if (chance(0.4)) {
                body(2, 5, "civilian", limbs());
            }
            walkers(chance(0.5) ? "jogger" : "civilian", 4 + any(4), 10);
        }
    }
}
