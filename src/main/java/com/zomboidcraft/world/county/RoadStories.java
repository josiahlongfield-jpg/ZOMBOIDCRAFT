package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.DoubleSupplier;

/**
 * Zomboid's road stories (its RandomizedVehicleStory set) on the county's open roads: the scenes the first days left
 * where they happened, a crash, a police blockade, a car up on its jack, a felled tree and the robbed travellers in
 * front of it. Like the jams ({@link Jams}), every highway and county road is cut into stretches of {@link #STRETCH}
 * blocks and each rolls for a story from the road's seed alone, so every chunk agrees on what stands where and
 * builds just its own part. A story only goes where the road runs level and alone: off a bridge, clear of the jams,
 * the places, the junctions and the railway.
 * <p>
 * How often is the sandbox's Randomized Road Stories Chance (VehicleStoryChance, LOOT's wiring, read through
 * {@link #rate}; new chunks only). Each story is built from refs/stories/road with the kits (the rv_ kit,
 * tools/city/roadstory.py), and has its payoff as Zomboid's does: the cars' own loot, a searchable bag, case or
 * box tagged loot=story_&lt;name&gt; for LOOT's tables, and the bodies and the walkers the chunk writer leaves
 * for world.gen.StoryMarks to lay ("!body:outfit;limbs;pose", "!walkers:outfit;count;radius").
 */
final class RoadStories {
    /** The length of road each story roll covers. */
    static final double STRETCH = 256;
    /** A stretch's chance of a story at "sometimes"; the other settings scale it. */
    static final double BASE = 0.3;
    /** The towns' outskirts, where the police closed the roads. */
    private static final double TOWN_REACH = 320;

    /**
     * The world's Randomized Road Stories Chance as a multiple of {@link #BASE}: 0 for never, 1 for sometimes,
     * anything past 1 / BASE for always. The game sets it from the sandbox; the preview from -Dstories.rate.
     */
    static volatile DoubleSupplier rate = () -> Double.parseDouble(System.getProperty("stories.rate", "0.5"));

    /** Zomboid's sandbox choice as a {@link #rate}. */
    static double rate(String choice) {
        if (choice == null) {
            return 0.5;
        }
        return switch (choice.toLowerCase(java.util.Locale.ROOT).replace(' ', '_')) {
            case "never", "none" -> 0;
            case "extremely_rare" -> 0.2;
            case "rare" -> 0.5;
            case "sometimes" -> 1;
            case "often" -> 2;
            case "very_often" -> 3;
            case "always" -> 1000;
            default -> 0.5;
        };
    }

    /** The stories, with Zomboid's weights and the road each takes up. */
    enum Story {
        CAR_CRASH(22, 34), BURNT_CAR(13, 18), FLIPPED_CRASH(11, 26), CAR_CRASH_CORPSE(10, 26), CHANGING_TIRE(9, 16),
        TRAILER_CRASH(8, 32), UTILITY_VEHICLE(7, 34), BANDIT_ROAD(6, 28), CONSTRUCTION_SITE(6, 48),
        AMBULANCE_CRASH(4, 26), POLICE_BLOCKADE(4, 30), POLICE_BLOCKADE_SHOOTING(2, 34), CRASH_HORDE(2, 32);

        final int weight;
        final double length;

        Story(int weight, double length) {
            this.weight = weight;
            this.length = length;
        }

        /** LOOT's table for the story's searchables: data/zomboidcraft/loot_table/shop/story_&lt;name&gt;.json. */
        String loot() {
            return "story_" + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /** Where a story's pieces go: a block standing on the ground there (dy up), or a car, body or walkers. */
    interface Out {
        void block(int x, int z, int dy, String block);

        void vehicle(double x, double z, float yaw, String models, long seed);
    }

    private static final int BLOCK = 0, CAR = 1, MARK = 2;
    /** Facing relative to the story's way: ahead, right, back, left; or its axis along or across the road. */
    static final int AHEAD = 0, RIGHT = 1, BACK = 2, LEFT = 3, ALONG = 4, ACROSS = 5, NONE = -1;

    /** One piece in the story's frame: s ahead of its middle, l right of the road's centre line. */
    private record Piece(int type, double s, double l, int dy, String what, int turn, float yaw, long seed) {
    }

    /** A story as rolled: its road, middle, which way it faces, and its pieces. */
    record Scene(Story story, Road road, double at, int sign, List<Piece> pieces) {
    }

    private static final Map<Long, Scene> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Scene> e) {
            return size() > 512;
        }
    };
    private static final Scene NOTHING = new Scene(null, null, 0, 0, List.of());
    private static double cachedRate = Double.NaN;

    private RoadStories() {
    }

    private static double currentRate() {
        try {
            return Math.max(0, rate.getAsDouble());
        } catch (RuntimeException e) {
            return 0.5;
        }
    }

    /** The stories' pieces whose spots fall in the 16x16 chunk at (bx, bz), each passed to out. */
    static void place(County county, List<Road> roads, int bx, int bz, Out out) {
        double rate = currentRate();
        if (rate <= 0) {
            return;
        }
        for (Road r : roads) {
            if (r.kind == Road.Kind.TRACK || r.xs.length < 2) {
                continue;
            }
            double first = Jams.along(r, bx - 40, bz - 40, bx + 56, bz + 56, true);
            if (first < 0) {
                continue;
            }
            double last = Jams.along(r, bx - 40, bz - 40, bx + 56, bz + 56, false);
            for (int w = (int) Math.floor(first / STRETCH); w <= (int) Math.floor(last / STRETCH); w++) {
                Scene sc = scene(county, r, w, rate);
                if (sc != null) {
                    emit(sc, bx, bz, out);
                }
            }
        }
    }

    /** Every story on the road, for the preview and the map: where each one's middle is. */
    static List<Scene> all(County county, Road r) {
        List<Scene> out = new ArrayList<>();
        double length = (r.xs.length - 1) * (double) Road.STEP;
        for (int w = 0; w * STRETCH < length; w++) {
            Scene sc = scene(county, r, w, currentRate());
            if (sc != null) {
                out.add(sc);
            }
        }
        return out;
    }

    private static Scene scene(County county, Road r, int w, double rate) {
        long key = r.seed * 31 + w;
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
        Scene sc = roll(county, r, w, rate);
        synchronized (CACHE) {
            CACHE.put(key, sc == null ? NOTHING : sc);
        }
        return sc;
    }

    private static Scene roll(County county, Road r, int w, double rate) {
        if (r.kind == Road.Kind.TRACK || r.xs.length < 2) {
            return null;
        }
        double length = (r.xs.length - 1) * (double) Road.STEP;
        double a0 = w * STRETCH, a1 = Math.min(length, a0 + STRETCH);
        SplittableRandom rng = new SplittableRandom(Noise.hash(r.seed ^ 0x5701L, w, 313));
        if (rng.nextDouble() >= Math.min(1, BASE * rate)) {
            return null;
        }
        double fromEnd = Math.min((a0 + a1) / 2, length - (a0 + a1) / 2);
        Story story = pick(rng, r, fromEnd < TOWN_REACH);
        double len = story.length;
        for (int t = 0; t < 5; t++) {
            double at = a0 + len / 2 + 8 + rng.nextDouble() * Math.max(1, a1 - a0 - len - 16);
            if (fits(county, r, at, len, length)) {
                int sign = rng.nextBoolean() ? 1 : -1;
                Scene sc = new Scene(story, r, at, sign, new ArrayList<>());
                new Builder(sc, new SplittableRandom(rng.nextLong())).build();
                return sc;
            }
        }
        return null;
    }

    private static Story pick(SplittableRandom rng, Road r, boolean nearTown) {
        int total = 0;
        Story[] all = Story.values();
        int[] weights = new int[all.length];
        for (int i = 0; i < all.length; i++) {
            Story s = all[i];
            int wgt = s.weight;
            // the police closed the roads into town; the road crews worked the highways
            if ((s == Story.POLICE_BLOCKADE || s == Story.POLICE_BLOCKADE_SHOOTING) && nearTown) {
                wgt *= 3;
            }
            if (s == Story.CONSTRUCTION_SITE && r.kind == Road.Kind.HIGHWAY) {
                wgt *= 2;
            }
            weights[i] = wgt;
            total += wgt;
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < all.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return all[i];
            }
        }
        return Story.CAR_CRASH;
    }

    /** Whether a story len long fits with its middle at distance at along r. */
    private static boolean fits(County county, Road r, double at, double len, double length) {
        double s0 = at - len / 2, s1 = at + len / 2;
        if (s0 < 48 || s1 > length - 48) {
            return false;
        }
        // level: the road holds one height (no half steps on the asphalt) the whole way
        int i0 = (int) Math.floor(s0 / Road.STEP), i1 = Math.min(r.ys.length - 1, (int) Math.ceil(s1 / Road.STEP));
        int floor = (int) Math.floor(r.ys[i0]);
        for (int i = i0; i <= i1; i++) {
            if ((int) Math.floor(r.ys[i]) != floor || r.ys[i] - floor >= 0.5) {
                return false;
            }
        }
        if (Jams.bridged(county, r, s0 - 8, s1 + 8) || Jams.jammed(county, r, s0 - 12, s1 + 12)) {
            return false;
        }
        int reach = r.kind.half + 12;
        for (double s = s0; s <= s1 + 0.01; s += len / 4) {
            double[] q = Jams.point(r, s);
            if (q == null) {
                return false;
            }
            int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[1]);
            for (Place p : county.placesNear(x - reach, z - reach, x + reach, z + reach, County.SKIRT)) {
                if (p.contains(x, z, reach)) {
                    return false;
                }
            }
            for (Road o : county.roadsNear(x - 30, z - 30, x + 30, z + 30)) {
                if (o == r) {
                    continue;
                }
                Road.Hit hit = new Road.Hit();
                o.nearest(q[0], q[1], hit);
                if (hit.road != null && hit.distance < o.kind.half + r.kind.half + 12) {
                    return false;
                }
            }
            if (RailGen.distance(county, q[0], q[1], 40) < 30) {
                return false;
            }
        }
        return true;
    }

    private static void emit(Scene sc, int bx, int bz, Out out) {
        Road r = sc.road;
        for (Piece p : sc.pieces) {
            double[] q = Jams.point(r, sc.at + sc.sign * p.s);
            if (q == null) {
                continue;
            }
            double dx = q[2] * sc.sign, dz = q[3] * sc.sign;
            // right of the story's way is (-dz, dx); l counts from the centre line, positive to its right
            double x = q[0] - dz * p.l, z = q[1] + dx * p.l;
            if (x < bx || x >= bx + 16 || z < bz || z >= bz + 16) {
                continue;
            }
            switch (p.type) {
                case BLOCK -> out.block((int) Math.floor(x), (int) Math.floor(z), p.dy, turned(p.what, p.turn, dx, dz));
                case CAR -> out.vehicle(x, z, Hwy.yaw(dx, dz) + p.yaw, p.what, p.seed);
                default -> out.vehicle(x, z, Hwy.yaw(dx, dz) + p.yaw, p.what, p.seed);
            }
        }
    }

    /** The block with its facing or axis turned from the story's frame to the world's. */
    private static String turned(String block, int turn, double dx, double dz) {
        if (turn == NONE) {
            return block;
        }
        String prop;
        if (turn == ALONG || turn == ACROSS) {
            boolean xAlong = Math.abs(dx) >= Math.abs(dz);
            prop = "axis=" + (xAlong == (turn == ALONG) ? "x" : "z");
        } else {
            double vx, vz;
            switch (turn) {
                case RIGHT -> { vx = -dz; vz = dx; }
                case BACK -> { vx = -dx; vz = -dz; }
                case LEFT -> { vx = dz; vz = -dx; }
                default -> { vx = dx; vz = dz; }
            }
            prop = "facing=" + (Math.abs(vx) >= Math.abs(vz) ? (vx > 0 ? "east" : "west") : (vz > 0 ? "south" : "north"));
        }
        int b = block.indexOf('[');
        return b < 0 ? block + "[" + prop + "]" : block.substring(0, b + 1) + prop + "," + block.substring(b + 1);
    }

    /** Lays one story out in its own frame (s ahead along its way, l right of the centre line). */
    private static final class Builder {
        private final Scene sc;
        private final SplittableRandom rng;
        /** The road's half width, the middle of the right-hand lane, the shoulder's middle. */
        private final double half, lane, shoulder;
        private final boolean highway;

        Builder(Scene sc, SplittableRandom rng) {
            this.sc = sc;
            this.rng = rng;
            this.half = sc.road.kind.half;
            this.highway = sc.road.kind == Road.Kind.HIGHWAY;
            this.lane = highway ? 5.25 : 2;
            this.shoulder = half + 1.2;
        }

        // ---- pieces

        private void block(double s, double l, String name) {
            block(s, l, 0, name, NONE);
        }

        private void block(double s, double l, String name, int turn) {
            block(s, l, 0, name, turn);
        }

        private void block(double s, double l, int dy, String name, int turn) {
            sc.pieces.add(new Piece(BLOCK, s, l, dy, name, turn, 0, 0));
        }

        private void car(double s, double l, float yaw, String models) {
            sc.pieces.add(new Piece(CAR, s, l, 0, models, NONE, yaw, rng.nextLong()));
        }

        /** A body: Zomboid's outfit, what it's missing (Dismember's bits, 0 whole) and how it lies. */
        private void body(double s, double l, String outfit, int limbs, int pose) {
            sc.pieces.add(new Piece(MARK, s, l, 0, "!body:" + outfit + ";" + limbs + ";" + pose, NONE, (float) (rng.nextDouble() * 360), rng.nextLong()));
            if (rng.nextDouble() < 0.7) {
                block(s + rng.nextDouble() - 0.5, l + rng.nextDouble() - 0.5, rng.nextBoolean() ? "home_blood_pool" : "blood_splat");
            }
        }

        /** Walkers that were the story's people, or came to it: dressed so, or the general mix ("any"). */
        private void walkers(double s, double l, String outfit, int count, int radius) {
            if (count > 0) {
                sc.pieces.add(new Piece(MARK, s, l, 0, "!walkers:" + outfit + ";" + count + ";" + radius, NONE, 0, rng.nextLong()));
            }
        }

        private double jit(double amount) {
            return (rng.nextDouble() * 2 - 1) * amount;
        }

        private float skew(double amount) {
            return (float) jit(amount);
        }

        private int any(int n) {
            return rng.nextInt(n);
        }

        private boolean chance(double p) {
            return rng.nextDouble() < p;
        }

        private String loot(String block) {
            return block + "[loot=" + sc.story.loot() + "]";
        }

        /** Glass and broken bits round a point. */
        private void wreckage(double s, double l, int n, double spread) {
            for (int k = 0; k < n; k++) {
                if (chance(0.5)) {
                    block(s + jit(spread), l + jit(spread * 0.7), "glass_shards");
                } else {
                    block(s + jit(spread), l + jit(spread * 0.7), "rv_debris", any(4));
                }
            }
        }

        /** Rubber laid down along the way from s0 to s1, drifting from l0 to l1. */
        private void skid(double s0, double s1, double l0, double l1) {
            int n = (int) Math.max(1, Math.abs(s1 - s0));
            for (int k = 0; k <= n; k++) {
                double t = k / (double) n;
                block(s0 + (s1 - s0) * t, l0 + (l1 - l0) * t, "rv_skid", ALONG);
            }
        }

        private String outfit() {
            String[] o = {"civilian", "civilian", "civilian", "office", "hoodie", "jogger", "farmer"};
            return o[any(o.length)];
        }

        private int limbs() {
            return chance(0.35) ? 1 << any(8) : 0;
        }

        // ---- the stories

        void build() {
            switch (sc.story) {
                case CAR_CRASH -> carCrash();
                case BURNT_CAR -> burntCar();
                case FLIPPED_CRASH -> flippedCrash();
                case CAR_CRASH_CORPSE -> carCrashCorpse();
                case CHANGING_TIRE -> changingTire();
                case TRAILER_CRASH -> trailerCrash();
                case UTILITY_VEHICLE -> utilityVehicle();
                case BANDIT_ROAD -> banditRoad();
                case CONSTRUCTION_SITE -> constructionSite();
                case AMBULANCE_CRASH -> ambulanceCrash();
                case POLICE_BLOCKADE -> policeBlockade(false);
                case POLICE_BLOCKADE_SHOOTING -> policeBlockade(true);
                case CRASH_HORDE -> crashHorde();
            }
        }

        /** Two cars met (refs: car-crash): head on where one pulled out to pass, or one into the other's side. */
        private void carCrash() {
            if (chance(0.5)) {
                car(-2.6, lane - 0.8, skew(20), "car,van@wrecked");
                car(2.6, lane - 1.2, 180 + skew(25), "car,van@wrecked");
                skid(-16, -5, lane - 0.4, lane - 0.9);
                skid(5, 13, lane - 1.6, -lane + 0.5);
            } else {
                car(0, lane, (chance(0.5) ? 70 : -70) + skew(15), "car,van@wrecked");
                car(-5, lane - 0.3, skew(12), "car,van@wrecked");
                skid(-18, -8, lane, lane - 0.4);
            }
            wreckage(0, lane - 1, 6, 4);
            block(jit(2), lane - 1 + jit(1), "rv_fluid");
            block(jit(3), lane + jit(1), "rv_fluid");
            if (chance(0.6)) {
                body(6 + jit(2), lane + 2 + jit(1), outfit(), limbs(), 0);
            }
            block(4 + jit(2), shoulder, loot("wf_bags_a"), any(4));
            walkers(0, lane, "any", chance(0.5) ? 1 + any(2) : 0, 8);
        }

        /** A burnt-out shell left on the shoulder (refs: burnt-car), the asphalt scorched round it. */
        private void burntCar() {
            car(0, shoulder - 0.6, skew(14), "car,van,pickup@burnt");
            for (int k = 0; k < 10; k++) {
                block(jit(3.5), shoulder - 0.6 + jit(2.4), "scorch");
            }
            block(jit(2), shoulder - 3, "glass_shards");
            int dead = chance(0.6) ? 1 + any(2) : 0;
            for (int k = 0; k < dead; k++) {
                body(jit(2), shoulder - 2.6 - k, "civilian", 1 << any(8), 0);
            }
            block(-3.2, shoulder + 1.6, loot("rv_tin"), any(4));
        }

        /**
         * A car that rolled and lies on its roof or its side (refs: flipped-crash; VEHICLES' "^roof" and "^side"
         * poses), the skid from where it went over, the bits it shed, its driver thrown clear.
         */
        private void flippedCrash() {
            skid(-14, -3, lane - 0.5, half - 0.5);
            car(1, half + 0.8, 30 + skew(50), "car,van,pickup@wrecked" + (chance(0.65) ? "^roof" : "^side"));
            wreckage(-1, half + 0.5, 5, 3);
            block(1 + jit(1), half + 1.4, "rv_fluid");
            body(6 + jit(1.5), half + 4 + jit(1), outfit(), limbs(), 0);
            block(4, half + 1, "ap_bag_flat", any(4));
            block(5, half + 2, "home_clothes_pile");
            block(3, half + 3, loot("wf_bags_a"), any(4));
        }

        /** A car into the ditch with its driver through the windscreen (refs: car-crash-corpse). */
        private void carCrashCorpse() {
            skid(-15, -3, lane, half + 0.2);
            car(0, half + 3, 35 + skew(15), "car,van,pickup@wrecked");
            block(2.6, half + 3.5, "glass_shards");
            block(3.6, half + 4.2, "glass_shards");
            body(5 + jit(1), half + 4.5 + jit(1), outfit(), limbs(), 0);
            block(4, half + 2.5, loot("wf_bags_a"), any(4));
            wreckage(-1, half + 1, 3, 2);
        }

        /** A car stopped on the shoulder up on its jack, the spare out, the driver dead beside it (refs: changing-tire). */
        private void changingTire() {
            car(0, shoulder + 0.3, skew(4), "car,van,pickup@abandoned");
            block(1.3, shoulder - 1.4, loot("rv_jack"), LEFT);
            block(2.6, shoulder - 2.4, "tire_flat");
            block(0.2, shoulder - 2.3, "rv_lug_wrench");
            block(-1.2, shoulder - 2.2, "tire_flat");
            body(-1.6 + jit(0.8), shoulder - 3.2, outfit(), limbs(), 0);
            walkers(-6, shoulder, "any", chance(0.6) ? 1 : 0, 6);
        }

        /** A pickup with its trailer swung round across the lanes, the load spilled (refs: trailer-crash). */
        private void trailerCrash() {
            skid(-18, -4, lane, lane - 1);
            car(5, lane + 0.5, 25 + skew(10), "pickup@wrecked");
            car(-2.5, lane - 2.5, -55 + skew(15), "horse_trailer@wrecked");
            for (int k = 0; k < 3; k++) {
                block(-6 + jit(3), -lane + jit(1.5), "fm_bale_one", any(4));
            }
            block(-4, -lane + 1, loot("cardboard_box"), any(4));
            block(-5.5, -lane - 0.5, loot("mu_crate_open"), any(4));
            block(-3, -lane - 1.2, "mu_crate_flat", any(4));
            block(-7, 0, "cardboard_box", any(4));
            wreckage(1, lane - 1, 4, 3);
            if (chance(0.4)) {
                body(8, lane + 2, "farmer", limbs(), 0);
            }
        }

        /** A lineman's van at a job on the shoulder, coned off, the cable drum out, the worker dead (refs: utility-vehicle). */
        private void utilityVehicle() {
            car(0, shoulder, skew(3), "step_van@abandoned");
            block(-16, shoulder + 0.5, "rv_sign_work", BACK);
            for (double s : new double[] {-9, -6.5, -4, 4.5, 7}) {
                block(s, shoulder - 2.2 + (s < 0 ? (s + 9) * 0.1 : 0), "st_cone");
            }
            block(3.5, shoulder + 2.6, "rv_spool", ALONG);
            block(-2.5, shoulder + 2.4, loot("pk_tool_chest"), LEFT);
            block(1, shoulder + 2.6, "pl_wire", ALONG);
            body(1.5, shoulder + 3.6, "construction", limbs(), 0);
            walkers(-4, shoulder + 3, "construction", chance(0.5) ? 1 : 0, 5);
        }

        /** A tree felled across the road, a car stopped at it, its people shot, their cases torn open (refs: bandit-road). */
        private void banditRoad() {
            double cut = 8;
            for (double l = -half - 2; l <= half + 1; l += 1) {
                block(cut, l, "rv_trunk", ACROSS);
            }
            block(cut, half + 2, "rv_trunk_crown", RIGHT);
            block(cut - 1, -half - 2, "debris_chunk", any(4));
            car(-1, lane - 0.4, skew(6), "car,van,pickup@abandoned");
            int dead = 2 + any(2);
            for (int k = 0; k < dead; k++) {
                body(-2 + jit(3), lane + jit(2.5), outfit(), 0, 0);
            }
            for (int k = 0; k < 4; k++) {
                block(2 + jit(4), half + 3 + jit(1), "bunk_casings");
            }
            block(-4, -0.5, "ap_bag_flat", any(4));
            block(-5, 0.5, "home_clothes_pile");
            block(-3, 1.4, "home_clothes_pile");
            block(-6, -1.2, "paper");
            block(-4.5, -lane, loot("wf_bags_a"), any(4));
            if (chance(0.5)) {
                block(-6.5, -lane + 1, loot("wf_bags_a"), any(4));
            }
        }

        /** A lane closure (refs: construction-site): the warning sign, a taper of drums, cones along the line, the dug lane. */
        private void constructionSite() {
            double edge = lane - 2;     // the line between the lanes, where the cones stood
            block(-22, half + 1.5, "rv_sign_work", BACK);
            for (int k = 0; k <= 4; k++) {
                block(-14 + k * 2, half - (half - edge) * k / 4.0, chance(0.12) ? "hw_drum_down" : "hw_drum", chance(0.12) ? any(4) : NONE);
            }
            for (double s = -5; s <= 16; s += 3) {
                block(s, edge, "st_cone");
            }
            block(18, lane, "rv_closed_sign", AHEAD);
            block(18, lane + 1, "rv_closed", AHEAD);
            // the work: the asphalt cut out down to the gravel, a patch already laid
            for (double s = -2; s <= 4; s++) {
                for (double l = lane - 1; l <= lane + 1; l++) {
                    block(s, l, -1, "asphalt_patch", NONE);
                }
            }
            car(9, lane, skew(4), "pickup,box_truck@abandoned");
            block(6, half + 2, loot("pk_tool_chest"), LEFT);
            block(12, half + 2.6, "st_porta_lo", LEFT);
            block(12, half + 2.6, 1, "st_porta_hi", LEFT);
            if (chance(0.5)) {
                body(1, lane + jit(1), "construction", limbs(), 0);
            }
            walkers(4, half + 3, "construction", chance(0.6) ? 1 + any(2) : 0, 6);
        }

        /** An ambulance gone over on its side, its gurney thrown out, the crew and the patient dead (refs: ambulance-crash). */
        private void ambulanceCrash() {
            skid(-14, -3, lane, half + 0.3);
            car(0, half + 1.6, 20 + skew(10), "ambulance,ambulance_van@wrecked" + (chance(0.7) ? "^side" : ""));
            block(-4, half - 1, chance(0.5) ? "st_stretcher" : "st_stretcher_bag", RIGHT);
            body(-3, lane - 0.5, "nurse", limbs(), 0);
            body(-6, half - 0.5, "patient", limbs(), 0);
            block(-2, half + 0.4, loot("bunk_first_aid"), LEFT);
            wreckage(-1, half, 4, 3);
            walkers(-8, lane, chance(0.5) ? "patient" : "nurse", chance(0.6) ? 1 : 0, 6);
        }

        /**
         * Police cars angled across the road with the barriers between them, flares set out ahead, the road
         * closed (refs: police-blockade); or after it fell (police-blockade-shooting): the barriers down, casings
         * behind the cars, the officers and the people they held back dead, the line taped.
         */
        private void policeBlockade(boolean shot) {
            car(0, -lane + 0.4, 62 + skew(10), "police_sedan,police_suv@abandoned");
            car(0.6, lane - 0.4, 118 + skew(10), "police_sedan,police_suv@abandoned");
            for (double l : new double[] {-0.5, 0.5, -half - 1, half + 1, -half - 2, half + 2}) {
                block(3, l, shot && chance(0.6) ? "st_barrier_down" : "st_barrier", AHEAD);
            }
            for (double l = -half; l <= half; l += 2) {
                block(-9 + jit(0.4), l + jit(0.4), shot ? "rv_flare_out" : "rv_flare", any(4));
            }
            for (double l = -half + 1; l <= half - 1; l += 2.5) {
                block(-5, l, "st_cone");
            }
            block(-3, highway ? 0 : 0.5, "rv_closed_sign", AHEAD);
            block(5, -half - 1, loot("rv_case"), any(4));
            if (shot) {
                for (double l = -half; l <= half; l += 1) {
                    block(-6, l, "rv_tape", AHEAD);
                }
                for (int k = 0; k < 8; k++) {
                    block(3 + jit(2.5), jit(half), "bunk_casings");
                }
                int cops = 2 + any(2), crowd = 3 + any(3);
                for (int k = 0; k < cops; k++) {
                    body(4 + jit(2), jit(half), "police", limbs(), 0);
                }
                for (int k = 0; k < crowd; k++) {
                    body(-6 - any(8), jit(half), outfit(), limbs(), 0);
                }
                walkers(-8, 0, "any", 2 + any(3), 10);
                walkers(4, 0, "police", 1 + any(2), 6);
            } else {
                if (chance(0.3)) {
                    body(4, jit(2), "police", limbs(), 0);
                }
                walkers(5, 0, "police", chance(0.5) ? 1 + any(2) : 0, 6);
            }
        }

        /** A pile-up with the dead it drew in standing round it (Zomboid's crash horde). */
        private void crashHorde() {
            int n = 3 + any(2);
            for (int k = 0; k < n; k++) {
                car(-8 + k * 5 + jit(1), (k % 2 == 0 ? lane : -lane) * 0.8 + jit(1), (k % 2 == 0 ? 0 : 180) + skew(60),
                        chance(0.25) ? "car,van,truck@burnt" : "car,van,truck@wrecked");
            }
            wreckage(0, 0, 8, 7);
            block(jit(4), jit(2), "rv_fluid");
            int dead = 2 + any(3);
            for (int k = 0; k < dead; k++) {
                body(jit(12), jit(half + 1), outfit(), limbs(), 0);
            }
            walkers(0, 0, "any", 8 + any(7), 12);
        }
    }
}
