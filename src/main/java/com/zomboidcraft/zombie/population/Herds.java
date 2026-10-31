package com.zomboidcraft.zombie.population;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.Road;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The dead on the move, as numbers rather than entities: Zomboid's following of sound, the growth of the count to its
 * peak (Population Peak Multiplier and Day, the extra walking in from the county's edges, Josiah's 31 Oct answer), and
 * the herds approved on 26 Oct, a few hundred strong, walking the county's roads. A herd turns toward a loud enough noise
 * within Follow Sound Distance, lingers where it was, then takes to the roads again. Within a player's street range it
 * spills out as real zombies, each still heading where the herd was going; once they go idle and nobody's near they
 * fold into the chunk records like any others, so a herd that has passed leaves its stragglers behind.
 * Kept on the overworld (zomboidcraft:herds), so every player meets the same herds.
 */
public final class Herds {
    /** A herd: where it is, how many, the road it walks (by seed, how far along, which way) or the noise it's after. */
    public record Herd(double x, double z, int count, long road, double along, int dir, double tx, double tz, int mode,
                       long until) {
        static final Codec<Herd> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.fieldOf("x").forGetter(Herd::x),
                Codec.DOUBLE.fieldOf("z").forGetter(Herd::z),
                Codec.INT.fieldOf("count").forGetter(Herd::count),
                Codec.LONG.optionalFieldOf("road", 0L).forGetter(Herd::road),
                Codec.DOUBLE.optionalFieldOf("along", 0.0).forGetter(Herd::along),
                Codec.INT.optionalFieldOf("dir", 1).forGetter(Herd::dir),
                Codec.DOUBLE.optionalFieldOf("tx", 0.0).forGetter(Herd::tx),
                Codec.DOUBLE.optionalFieldOf("tz", 0.0).forGetter(Herd::tz),
                Codec.INT.optionalFieldOf("mode", ROAD).forGetter(Herd::mode),
                Codec.LONG.optionalFieldOf("until", 0L).forGetter(Herd::until)).apply(i, Herd::new));

        Herd with(double x, double z, int count) {
            return new Herd(x, z, count, road, along, dir, tx, tz, mode, until);
        }
    }

    /** What the county's herds are doing, and how many dead have walked in toward the peak so far. */
    public record State(List<Herd> herds, boolean started, int grown) {
        static final Codec<State> CODEC = RecordCodecBuilder.create(i -> i.group(
                Herd.CODEC.listOf().optionalFieldOf("herds", List.of()).forGetter(State::herds),
                Codec.BOOL.optionalFieldOf("started", false).forGetter(State::started),
                Codec.INT.optionalFieldOf("grown", 0).forGetter(State::grown)).apply(i, State::new));
    }

    public static final AttachmentType<State> HERDS = AttachmentRegistry.create(ZomboidCraft.id("herds"),
            builder -> builder.persistent(State.CODEC).initializer(() -> new State(List.of(), false, 0)));

    static final int ROAD = 0, SOUND = 1, LINGER = 2;
    /** How often herds move (ticks), and how far each step: about half a block a second, a shambling crowd's pace. */
    private static final int STEP_TICKS = 20;
    private static final double STEP = 0.55;
    /** How long a herd stands where the noise was before it moves on (ticks: an in-game hour). */
    private static final long LINGER_TICKS = 1000;
    /** How many of a herd turn real each step once a player is near (Population's cap holds too). */
    private static final int SPILL_PER_STEP = 12;
    /** Noises quieter than this don't carry to a herd or to records out of sight. */
    private static final double LOUD = 30;
    /** How often the peak's growth is checked (ticks), and the smallest party that walks in. */
    private static final int GROWTH_EVERY = 1200;

    private static double countyTotal = -1;
    private static long countySeed;

    public static void register() {
        // loading the class registers the attachment
    }

    /** Zombie Count as a multiplier on the approved table. */
    public static double countFactor(ZcConfig.ZombiePopulation zp) {
        return switch (zp.count) {
            case INSANE -> 4;
            case VERY_HIGH -> 3;
            case HIGH -> 2;
            case NORMAL -> 1;
            case LOW -> 0.35;
            case NONE -> 0;
        };
    }

    /** The share of the starting count the dead have grown to by now: start, rising evenly to peak on the peak day. */
    static double growth(ZcConfig.ZombiePopulation zp, long day) {
        double t = Math.min(1, day / (double) Math.max(1, zp.peakDay));
        return zp.startMultiplier + (zp.peakMultiplier - zp.startMultiplier) * t;
    }

    public static List<Herd> list(ServerLevel level) {
        return level.getAttachedOrElse(HERDS, new State(List.of(), false, 0)).herds();
    }

    static void tick(ServerLevel level, List<ServerPlayer> players) {
        County county = county(level);
        if (level.getGameTime() % STEP_TICKS != 0) {
            return;
        }
        ZcConfig.ZombiePopulation zp = ZcConfig.get().zombiePopulation;
        State state = level.getAttachedOrElse(HERDS, new State(List.of(), false, 0));
        if (county == null && state.herds().isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        List<Herd> herds = new ArrayList<>(state.herds());
        int grown = state.grown();
        double scale = ZcConfig.get().population.density * countFactor(zp);
        if (!state.started() && county != null) {
            for (int i = 0; i < zp.herds && scale > 0; i++) {
                Herd h = startOnRoad(county, random, false);
                if (h != null) {
                    herds.add(h.with(h.x(), h.z(), size(zp, random, scale)));
                }
            }
        }
        if (county != null && level.getGameTime() % GROWTH_EVERY == 0 && scale > 0 && zp.peakMultiplier > zp.startMultiplier) {
            // the peak: the dead that weren't there at the start walk in from the edge of the county
            double base = total(county) * scale;
            long day = level.getGameTime() / 24000;
            int due = (int) Math.round(base * (growth(zp, day) - zp.startMultiplier)) - grown;
            if (due >= Math.max(20, zp.herdMin / 4)) {
                Herd h = startOnRoad(county, random, true);
                if (h != null) {
                    int n = Math.min(due, Math.max(zp.herdMin, zp.herdMax));
                    herds.add(h.with(h.x(), h.z(), n));
                    grown += n;
                }
            }
        }
        List<Herd> moved = new ArrayList<>(herds.size());
        for (Herd h : herds) {
            Herd next = step(level, county, h, random);
            next = spill(level, county, next, players);
            if (next != null && next.count() > 0) {
                moved.add(next);
            }
        }
        level.setAttached(HERDS, new State(List.copyOf(moved), true, grown));
    }

    private static int size(ZcConfig.ZombiePopulation zp, RandomSource random, double scale) {
        int lo = Math.max(1, Math.min(zp.herdMin, zp.herdMax)), hi = Math.max(lo, zp.herdMax);
        return Math.max(1, (int) Math.round((lo + random.nextInt(hi - lo + 1)) * Math.min(scale, 4)));
    }

    // ---- walking

    private static Herd step(ServerLevel level, County county, Herd h, RandomSource random) {
        long now = level.getGameTime();
        switch (h.mode()) {
            case SOUND -> {
                double dx = h.tx() - h.x(), dz = h.tz() - h.z(), d = Math.sqrt(dx * dx + dz * dz);
                if (d <= STEP * 2) {
                    return new Herd(h.tx(), h.tz(), h.count(), 0, 0, 1, h.tx(), h.tz(), LINGER, now + LINGER_TICKS);
                }
                return new Herd(h.x() + dx / d * STEP, h.z() + dz / d * STEP, h.count(), 0, 0, 1, h.tx(), h.tz(), SOUND, 0);
            }
            case LINGER -> {
                if (now < h.until()) {
                    return h;
                }
                Herd back = county == null ? null : joinRoad(county, h.x(), h.z(), h.count(), random);
                return back != null ? back : new Herd(h.x(), h.z(), h.count(), 0, 0, 1, h.x(), h.z(), LINGER, now + LINGER_TICKS * 4);
            }
            default -> {
                if (county == null) {
                    return h;
                }
                Road road = road(county, h.x(), h.z(), h.road());
                if (road == null) {
                    Herd back = joinRoad(county, h.x(), h.z(), h.count(), random);
                    return back != null ? back : h;
                }
                double len = (road.xs.length - 1) * (double) Road.STEP;
                double along = h.along() + STEP * h.dir();
                if (along <= 0 || along >= len) {
                    // the end of this road: on along another one leaving the same place
                    double ex = along <= 0 ? road.xs[0] : road.xs[road.xs.length - 1];
                    double ez = along <= 0 ? road.zs[0] : road.zs[road.zs.length - 1];
                    Herd next = nextRoad(county, road, ex, ez, h.count(), random);
                    return next != null ? next : new Herd(h.x(), h.z(), h.count(), road.seed, h.along(), -h.dir(), 0, 0, ROAD, 0);
                }
                double[] at = point(road, along);
                return new Herd(at[0], at[1], h.count(), road.seed, along, h.dir(), 0, 0, ROAD, 0);
            }
        }
    }

    /** Back on the nearest road after a noise, going whichever way. */
    private static Herd joinRoad(County county, double x, double z, int count, RandomSource random) {
        Road.Hit hit = new Road.Hit();
        for (Road r : county.roadsNear((int) x - 96, (int) z - 96, (int) x + 96, (int) z + 96)) {
            r.nearest(x, z, hit);
        }
        if (hit.road == null) {
            return null;
        }
        double[] at = point(hit.road, hit.along);
        return new Herd(at[0], at[1], count, hit.road.seed, hit.along, random.nextBoolean() ? 1 : -1, 0, 0, ROAD, 0);
    }

    private static Herd nextRoad(County county, Road from, double ex, double ez, int count, RandomSource random) {
        List<Road> out = new ArrayList<>();
        for (Road r : county.roadsNear((int) ex - 24, (int) ez - 24, (int) ex + 24, (int) ez + 24)) {
            if (r.seed != from.seed) {
                out.add(r);
            }
        }
        if (out.isEmpty()) {
            return null;
        }
        Road r = out.get(random.nextInt(out.size()));
        Road.Hit hit = new Road.Hit();
        r.nearest(ex, ez, hit);
        double len = (r.xs.length - 1) * (double) Road.STEP;
        int dir = hit.along < len / 2 ? 1 : -1;
        double[] at = point(r, hit.along);
        return new Herd(at[0], at[1], count, r.seed, hit.along, dir, 0, 0, ROAD, 0);
    }

    private static Road road(County county, double x, double z, long seed) {
        if (seed == 0) {
            return null;
        }
        for (Road r : county.roadsNear((int) x - 8, (int) z - 8, (int) x + 8, (int) z + 8)) {
            if (r.seed == seed) {
                return r;
            }
        }
        return null;
    }

    private static double[] point(Road r, double along) {
        double f = along / Road.STEP;
        int i = Math.clamp((int) Math.floor(f), 0, r.xs.length - 2);
        double t = Math.clamp(f - i, 0, 1);
        return new double[] {r.xs[i] + (r.xs[i + 1] - r.xs[i]) * t, r.zs[i] + (r.zs[i + 1] - r.zs[i]) * t};
    }

    /** A herd's start: somewhere on the roads, or (for the peak's new arrivals) by a place on the county's edge. */
    private static Herd startOnRoad(County county, RandomSource random, boolean edge) {
        List<Place> places = county.allPlaces();
        if (places.isEmpty()) {
            return null;
        }
        Place p;
        if (edge) {
            double cx = 0, cz = 0;
            for (Place q : places) {
                cx += (q.x0 + q.x1) / 2.0;
                cz += (q.z0 + q.z1) / 2.0;
            }
            cx /= places.size();
            cz /= places.size();
            List<Place> sorted = new ArrayList<>(places);
            final double fx = cx, fz = cz;
            sorted.sort((a, b) -> Double.compare(dist2(b, fx, fz), dist2(a, fx, fz)));
            p = sorted.get(random.nextInt(Math.max(1, sorted.size() / 5)));
        } else {
            p = places.get(random.nextInt(places.size()));
        }
        return joinRoad(county, (p.x0 + p.x1) / 2.0, (p.z0 + p.z1) / 2.0, 1, random);
    }

    private static double dist2(Place p, double x, double z) {
        double dx = (p.x0 + p.x1) / 2.0 - x, dz = (p.z0 + p.z1) / 2.0 - z;
        return dx * dx + dz * dz;
    }

    // ---- turning real near a player

    /** Test hook: what the last spill did. */
    public static String why = "";

    private static Herd spill(ServerLevel level, County county, Herd h, List<ServerPlayer> players) {
        if (h == null || h.count() <= 0 || players.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return h;
        }
        double range = ZcConfig.get().population.streetRange;
        boolean near = false;
        for (ServerPlayer p : players) {
            double dx = p.getX() - h.x(), dz = p.getZ() - h.z();
            if (dx * dx + dz * dz <= range * range) {
                near = true;
                break;
            }
        }
        if (!near) {
            why = "far";
            return h;
        }
        int room = Math.min(SPILL_PER_STEP, Population.room());
        if (room <= 0) {
            why = "no room";
            return h;
        }
        RandomSource random = level.getRandom();
        // they walk as a loose crowd, wider the more there are, heading where the herd was going
        double spread = 2 + Math.sqrt(h.count()) * 0.5;
        Vec3 heading = heading(county, h);
        int made = 0;
        for (int tries = 0; tries < room * 3 && made < room && made < h.count(); tries++) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * spread;
            int x = (int) Math.floor(h.x() + Math.cos(a) * r), z = (int) Math.floor(h.z() + Math.sin(a) * r);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                continue;
            }
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (!level.isPositionEntityTicking(pos) || !Spots.free(level, pos) || Spots.roofed(level, pos)) {
                continue;
            }
            if (Population.spawnWalker(level, pos, heading)) {
                made++;
            }
        }
        Population.spilled(made);
        why = "made " + made + " of " + room;
        return h.with(h.x(), h.z(), h.count() - made);
    }

    /** Where a herd's dead walk on to once they're real: the noise, or a way down its road. */
    private static Vec3 heading(County county, Herd h) {
        if (h.mode() == SOUND || h.mode() == LINGER) {
            return new Vec3(h.tx(), 0, h.tz());
        }
        if (county == null) {
            return null;
        }
        Road road = road(county, h.x(), h.z(), h.road());
        if (road == null) {
            return null;
        }
        double len = (road.xs.length - 1) * (double) Road.STEP;
        double[] at = point(road, Math.clamp(h.along() + 40 * h.dir(), 0, len));
        return new Vec3(at[0], 0, at[1]);
    }

    // ---- noise

    /**
     * A loud noise: herds within Follow Sound Distance that could hear it turn toward it, and records out of sight in
     * loaded chunks walk toward it as a herd of their own (Population gathers them).
     */
    public static void noise(ServerLevel level, Vec3 pos, double radius) {
        if (radius < LOUD || level != level.getServer().overworld()) {
            return;
        }
        double follow = ZcConfig.get().zombiePopulation.followSoundDistance;
        double reach = Math.min(radius * 1.5, follow);
        State state = level.getAttachedOrElse(HERDS, new State(List.of(), false, 0));
        List<Herd> herds = new ArrayList<>(state.herds());
        boolean changed = false;
        for (int i = 0; i < herds.size(); i++) {
            Herd h = herds.get(i);
            double dx = pos.x - h.x(), dz = pos.z - h.z();
            if (dx * dx + dz * dz <= reach * reach) {
                herds.set(i, new Herd(h.x(), h.z(), h.count(), 0, 0, 1, pos.x, pos.z, SOUND, 0));
                changed = true;
            }
        }
        Herd gathered = Population.gatherToward(level, pos, reach);
        if (gathered != null) {
            herds.add(gathered);
            changed = true;
        }
        if (changed) {
            level.setAttached(HERDS, new State(List.copyOf(herds), state.started(), state.grown()));
        }
    }

    /** A herd set down by hand (the preview test, or a later event): count dead at x, z, standing until it hears something. */
    public static void add(ServerLevel level, double x, double z, int count) {
        State state = level.getAttachedOrElse(HERDS, new State(List.of(), false, 0));
        List<Herd> herds = new ArrayList<>(state.herds());
        herds.add(new Herd(x, z, count, 0, 0, 1, x, z, LINGER, level.getGameTime() + LINGER_TICKS * 24));
        level.setAttached(HERDS, new State(List.copyOf(herds), state.started(), state.grown()));
    }

    /** A new herd made of records walking to a noise. */
    static Herd toward(double x, double z, int count, Vec3 pos) {
        return new Herd(x, z, count, 0, 0, 1, pos.x, pos.z, SOUND, 0);
    }

    // ---- the county

    static County county(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) {
            return null;
        }
        return CountyChunkGenerator.gen(level.getSeed()).county();
    }

    /** The approved table's whole count for the county, at Normal: every place's budget, the island's pool once. */
    static double total(County county) {
        if (countyTotal >= 0 && countySeed == System.identityHashCode(county)) {
            return countyTotal;
        }
        double sum = 0;
        boolean island = false;
        for (Place p : county.allPlaces()) {
            if (county.island((p.x0 + p.x1) / 2, (p.z0 + p.z1) / 2)) {
                island = true;
            } else {
                sum += Budgets.of(p).total();
            }
        }
        if (island) {
            sum += Budgets.ISLAND.total();
        }
        countyTotal = sum;
        countySeed = System.identityHashCode(county);
        return sum;
    }

    /** For /zc perf. */
    public static String stats(ServerLevel level) {
        List<Herd> herds = list(level);
        int n = 0;
        for (Herd h : herds) {
            n += h.count();
        }
        State s = level.getAttachedOrElse(HERDS, new State(List.of(), false, 0));
        return String.format("herds %d (%,d dead on the move) · grown toward the peak %,d", herds.size(), n, s.grown());
    }

    private Herds() {
    }
}
