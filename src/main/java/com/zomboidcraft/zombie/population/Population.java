package com.zomboidcraft.zombie.population;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import com.zomboidcraft.zombie.NoiseListener;
import com.zomboidcraft.zombie.ZombieLook;
import com.zomboidcraft.zombie.ZombieSpawner;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The world's dead. Each chunk gets its share of its place's dead the first time a player comes near (so every town is
 * full when you first walk in, and a cleared house stays cleared): spread over the floors, rooms and streets the chunk
 * actually has, and kept with the chunk as records, a few bytes each. Only the ones near a player are real zombies
 * that think and walk; the rest wait where they stood. Out in the open a zombie wakes within streetRange of anyone,
 * indoors only for the building you're in or beside and the floors near yours, so a tower fills floor by floor as you
 * climb it. A loud noise wakes every record in earshot, nearest first, and they come out of the doors and stairwells.
 * Real zombies left idle well away from everyone go back to being records where they stand.
 */
public final class Population {
    /** A chunk's records (see {@link #pack}), how many it was given, and the day it was given them. */
    public record Pop(int[] recs, int given, long day) {
        static final Codec<Pop> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of).fieldOf("z").forGetter(Pop::recs),
                Codec.INT.fieldOf("given").forGetter(Pop::given),
                Codec.LONG.fieldOf("day").forGetter(Pop::day)).apply(i, Pop::new));
    }

    public static final AttachmentType<Pop> POP = AttachmentRegistry.create(ZomboidCraft.id("population"),
            builder -> builder.persistent(Pop.CODEC));

    private static final int SURVEY_EVERY = 10;
    /** Chunks this far round a player (in chunks) get their dead. */
    private static final int SEED_RADIUS = 8;
    /** Time spent placing dead in new chunks each tick. */
    private static final long SEED_BUDGET_NS = 2_500_000;
    private static final int FOLDS_PER_STEP = 32;
    /** Noises this loud or louder (block radius) wake the records in earshot. */
    private static final double WAKE_RADIUS = 20;

    private static final LinkedHashSet<Long> QUEUE = new LinkedHashSet<>();
    private static final List<Zone> ZONES = new ArrayList<>();
    private static final Map<BlockState, Boolean> ROAD = new HashMap<>();
    private static List<String> outfits;

    private static int lastRecords, lastReal;
    private static long placed;

    private record Zone(Vec3 pos, double radius, long until) {
    }

    private record Cand(LevelChunk chunk, int index, double score, Vec3 woke) {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Population::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            QUEUE.clear();
            ZONES.clear();
            Census.clear();
        });
    }

    /** For /zc perf. */
    public static String stats() {
        ZcConfig.Population cfg = ZcConfig.get().population;
        return String.format("population %s · records near players %,d · real %d of %d · placed this session %,d · chunks waiting %d"
                        + " · places being counted %d", cfg.enabled ? "on" : "off", lastRecords, lastReal, cfg.realCap,
                placed, QUEUE.size(), Census.pending());
    }

    /** A noise (Noise.emit, already scaled by hearing and weather): loud ones wake the records round it. */
    public static void wake(ServerLevel level, Vec3 pos, double radius) {
        if (radius < WAKE_RADIUS || !ZcConfig.get().population.enabled || level != level.getServer().overworld()) {
            return;
        }
        if (ZONES.size() >= 16) {
            ZONES.remove(0);
        }
        ZONES.add(new Zone(pos, radius, level.getGameTime() + 100));
    }

    private static void tick(MinecraftServer server) {
        ZcConfig.Population cfg = ZcConfig.get().population;
        if (!cfg.enabled) {
            return;
        }
        ServerLevel level = server.overworld();
        place(level, cfg);
        if (server.getTickCount() % SURVEY_EVERY == 0) {
            survey(level, cfg);
        }
    }

    // ---- placing the dead in new chunks

    private static void place(ServerLevel level, ZcConfig.Population cfg) {
        if (QUEUE.isEmpty()) {
            return;
        }
        long end = System.nanoTime() + SEED_BUDGET_NS;
        List<Long> later = new ArrayList<>();
        Iterator<Long> it = QUEUE.iterator();
        while (it.hasNext() && System.nanoTime() < end) {
            long key = it.next();
            it.remove();
            LevelChunk chunk = level.getChunkSource().getChunkNow((int) (key >> 32), (int) key);
            if (chunk == null) {
                continue;
            }
            Pop pop = chunk.getAttached(POP);
            if (pop != null && !respawnDue(level, pop, cfg)) {
                continue;
            }
            if (!place(level, chunk, pop, cfg)) {
                later.add(key);   // a place it touches is still being counted
            }
        }
        QUEUE.addAll(later);
    }

    private static boolean place(ServerLevel level, LevelChunk chunk, Pop old, ZcConfig.Population cfg) {
        County county = level.getChunkSource().getGenerator() instanceof CountyChunkGenerator
                ? CountyChunkGenerator.gen(level.getSeed()).county() : null;
        int bx = chunk.getPos().x() << 4, bz = chunk.getPos().z() << 4;
        int minY = level.getMinY(), maxY = level.getMaxY() + 1;
        List<Place> places = county == null ? List.of() : county.placesNear(bx, bz, bx + 15, bz + 15, 0);
        double[] rin = new double[places.size()], rout = new double[places.size()];
        if (!places.isEmpty()) {
            CountyGen gen = CountyChunkGenerator.gen(level.getSeed());
            for (int i = 0; i < places.size(); i++) {
                Census.Count c = Census.get(gen, places.get(i), minY, maxY);
                if (c == null) {
                    return false;
                }
                Budgets.Budget b = Budgets.of(places.get(i));
                double total = b.total() * cfg.density, in = c.indoor() < 1 ? 0 : total * b.indoors();
                rin[i] = in == 0 ? 0 : in / c.indoor();
                rout[i] = c.outdoor() < 1 ? 0 : (total - in) / c.outdoor();
            }
        }
        Spots.Ground ground;
        if (county != null) {
            List<Place> near = county.placesNear(bx, bz, bx + 15, bz + 15, County.SKIRT);
            ground = (x, z) -> CountyChunkGenerator.surface(county, x, z, near);
        } else {
            ground = (x, z) -> level.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    level, level.getChunkSource().randomState());
        }
        net.minecraft.world.level.chunk.LevelChunkSection[] sections = chunk.getSections();
        Spots.Blocks blocks = (lx, y, lz) -> sections[(y - minY) >> 4].getBlockState(lx, y & 15, lz);
        RandomSource random = RandomSource.create(level.getSeed() ^ (bx * 341873128712L + bz * 132897987541L) ^ (old == null ? 0 : old.day()));
        long seed = level.getSeed();
        double empty = Math.min(0.95, Math.max(0, cfg.emptyHouses));
        int[][] out = {new int[32]};
        int[] n = {0};
        Spots.scan(blocks, bx, bz, minY, maxY, ground, (lx, y, lz, indoor) -> {
            int x = bx + lx, z = bz + lz;
            double rate = -1;
            for (int i = 0; i < places.size(); i++) {
                Place p = places.get(i);
                if (p.contains(x, z, 0)) {
                    rate = indoor ? rin[i] : rout[i];
                    if (indoor && empty > 0 && Budgets.houses(p)) {
                        if (emptyHouse(seed, x, z, empty)) {
                            return;
                        }
                        rate /= 1 - empty;
                    }
                    break;
                }
            }
            if (rate < 0) {
                rate = Budgets.WILD * cfg.density * (road(blocks.at(lx, y - 1, lz)) ? Budgets.WILD_ROAD : Budgets.WILD_OFF_ROAD);
            }
            if (random.nextDouble() < rate) {
                int rec = pack(lx, y, lz, indoor, 0);
                if (rec != -1) {
                    if (n[0] == out[0].length) {
                        out[0] = Arrays.copyOf(out[0], n[0] * 2);
                    }
                    out[0][n[0]++] = rec;
                }
            }
        });
        int[] recs = Arrays.copyOf(out[0], n[0]);
        int given = recs.length;
        if (old != null) {
            // a respawn tops the chunk back up to what it was given, no more
            int add = Math.max(0, Math.min(recs.length, old.given() - old.recs().length));
            int[] merged = Arrays.copyOf(old.recs(), old.recs().length + add);
            System.arraycopy(recs, 0, merged, old.recs().length, add);
            recs = merged;
            given = old.given();
        }
        chunk.setAttached(POP, new Pop(recs, given, day(level)));
        placed += recs.length - (old == null ? 0 : old.recs().length);
        return true;
    }

    /** Whether the house standing on this spot is one the family got out of: a 12-block lot, the same every time. */
    private static boolean emptyHouse(long seed, int x, int z, double share) {
        long h = seed ^ Math.floorDiv(x, 12) * 0x9E3779B97F4A7C15L ^ Math.floorDiv(z, 12) * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53 < share;
    }

    private static boolean road(BlockState state) {
        return ROAD.computeIfAbsent(state, s -> {
            String name = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
            return name.contains("asphalt") || name.contains("road") || name.contains("gravel") || name.contains("dirt_path");
        });
    }

    private static boolean respawnDue(ServerLevel level, Pop pop, ZcConfig.Population cfg) {
        return cfg.respawnDays > 0 && day(level) - pop.day() >= cfg.respawnDays && pop.recs().length < pop.given();
    }

    private static long day(ServerLevel level) {
        return level.getGameTime() / 24000;
    }

    // ---- waking and folding back

    private static void survey(ServerLevel level, ZcConfig.Population cfg) {
        long now = level.getGameTime();
        ZONES.removeIf(z -> z.until() < now);
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (!p.isSpectator()) {
                players.add(p);
            }
        }
        if (players.isEmpty()) {
            lastRecords = 0;
            return;
        }
        int reach = (int) Math.ceil(Math.max(cfg.streetRange, cfg.indoorRange) / 16) + 1;
        Map<Long, LevelChunk> chunks = new LinkedHashMap<>();
        for (ServerPlayer p : players) {
            int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
            for (int r = 0; r <= SEED_RADIUS; r++) {
                // ring by ring, so the nearest chunks get their dead first
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                            continue;
                        }
                        long key = ((long) (pcx + dx) << 32) | ((pcz + dz) & 0xFFFFFFFFL);
                        LevelChunk chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                        if (chunk == null) {
                            continue;
                        }
                        Pop pop = chunk.getAttached(POP);
                        if (pop == null || respawnDue(level, pop, cfg)) {
                            QUEUE.add(key);
                        } else if (r <= reach) {
                            chunks.put(key, chunk);
                        }
                    }
                }
            }
        }
        List<? extends Zombie> walkers = level.getEntities(EntityTypeTest.forClass(Zombie.class),
                z -> z.isAlive() && ZombieTypes.isWalker(z));
        int real = walkers.size() - fold(level, walkers, players, cfg);
        lastReal = real;
        int room = Math.min(cfg.perStep, cfg.realCap - real);
        int records = 0;
        List<Cand> cands = new ArrayList<>();
        for (LevelChunk chunk : chunks.values()) {
            int[] recs = chunk.getAttached(POP).recs();
            records += recs.length;
            if (room <= 0) {
                continue;
            }
            int bx = chunk.getPos().x() << 4, bz = chunk.getPos().z() << 4;
            for (int i = 0; i < recs.length; i++) {
                int rec = recs[i];
                double x = bx + (rec & 15) + 0.5, y = y(rec), z = bz + (rec >> 4 & 15) + 0.5;
                boolean indoor = (rec >> 17 & 1) != 0;
                double best = Double.MAX_VALUE;
                Vec3 woke = null;
                for (ServerPlayer p : players) {
                    double dx = x - p.getX(), dy = Math.abs(y - p.getY()), dz = z - p.getZ(), h2 = dx * dx + dz * dz;
                    boolean near = indoor ? h2 <= cfg.indoorRange * cfg.indoorRange && dy <= cfg.indoorFloors || h2 <= 64 && dy <= 24
                            : h2 <= cfg.streetRange * cfg.streetRange && dy <= 40;
                    if (near) {
                        best = Math.min(best, h2 + dy * dy);
                    }
                }
                for (Zone zone : ZONES) {
                    double d2 = zone.pos().distanceToSqr(x, y, z);
                    if (d2 <= zone.radius() * zone.radius() && d2 * 0.25 < best) {
                        best = d2 * 0.25;   // the ones that heard it come first
                        woke = zone.pos();
                    }
                }
                if (best < Double.MAX_VALUE) {
                    cands.add(new Cand(chunk, i, best, woke));
                }
            }
        }
        lastRecords = records;
        if (room <= 0 || cands.isEmpty() || level.getDifficulty() == Difficulty.PEACEFUL) {
            return;
        }
        cands.sort(Comparator.comparingDouble(Cand::score));
        Map<LevelChunk, BitSet> gone = new HashMap<>();
        int made = 0;
        for (Cand c : cands) {
            if (made >= room) {
                break;
            }
            int rec = c.chunk().getAttached(POP).recs()[c.index()];
            BlockPos pos = new BlockPos((c.chunk().getPos().x() << 4) + (rec & 15), y(rec), (c.chunk().getPos().z() << 4) + (rec >> 4 & 15));
            if (!level.isPositionEntityTicking(pos)) {
                continue;   // it would only stand frozen; it waits
            }
            if (Spots.free(level, pos) && spawn(level, pos, rec, c.woke())) {
                made++;
            }
            // made real, or something's been built where it stood: either way the record goes
            gone.computeIfAbsent(c.chunk(), k -> new BitSet()).set(c.index());
        }
        for (Map.Entry<LevelChunk, BitSet> e : gone.entrySet()) {
            Pop pop = e.getKey().getAttached(POP);
            int[] recs = pop.recs();
            int[] kept = new int[recs.length - e.getValue().cardinality()];
            for (int i = 0, k = 0; i < recs.length; i++) {
                if (!e.getValue().get(i)) {
                    kept[k++] = recs[i];
                }
            }
            e.getKey().setAttached(POP, new Pop(kept, pop.given(), pop.day()));
        }
    }

    private static boolean spawn(ServerLevel level, BlockPos pos, int rec, Vec3 woke) {
        Zombie zombie = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
        if (zombie == null) {
            return false;
        }
        int outfit = rec >>> 18 & 63;
        List<String> names = outfits();
        String look = outfit > 0 && outfit <= names.size() ? names.get(outfit - 1) : ZombieSpawner.uniform(level, pos, zombie.getRandom());
        if (look != null) {
            zombie.setAttached(ZombieLook.OUTFIT, look);
        }
        zombie.setYRot(zombie.getRandom().nextFloat() * 360);
        zombie.setYHeadRot(zombie.getYRot());
        if (woke != null) {
            ((NoiseListener) zombie).zomboidcraft$hear(woke);
        }
        return true;
    }

    /** Idle walkers well away from everyone go back to being records. Returns how many. */
    private static int fold(ServerLevel level, List<? extends Zombie> walkers, List<ServerPlayer> players, ZcConfig.Population cfg) {
        int folded = 0;
        for (Zombie z : walkers) {
            if (folded >= FOLDS_PER_STEP) {
                break;
            }
            if (z.tickCount < 200 || z.getTarget() != null || ((NoiseListener) z).zomboidcraft$heardAt() != null
                    || z.isPersistenceRequired() || z.hasCustomName() || z.isPassenger() || z.isVehicle()
                    || !z.getNavigation().isDone()) {
                continue;
            }
            Integer limbs = z.getAttached(Dismember.LIMBS);
            if (limbs != null && limbs != 0) {
                continue;   // a crawler or a one-armed one stays as it is
            }
            BlockPos pos = z.blockPosition();
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            Pop pop = chunk == null ? null : chunk.getAttached(POP);
            if (pop == null) {
                continue;   // its chunk hasn't had its own dead yet
            }
            boolean indoor = Spots.roofed(level, pos);
            if (!farFromAll(z.position(), indoor, players, cfg)) {
                continue;
            }
            int rec = pack(pos.getX() & 15, pos.getY(), pos.getZ() & 15, indoor, outfitIndex(ZombieLook.outfit(z)));
            if (rec == -1) {
                continue;
            }
            int[] recs = Arrays.copyOf(pop.recs(), pop.recs().length + 1);
            recs[recs.length - 1] = rec;
            chunk.setAttached(POP, new Pop(recs, pop.given(), pop.day()));
            z.discard();
            folded++;
        }
        return folded;
    }

    private static boolean farFromAll(Vec3 at, boolean indoor, List<ServerPlayer> players, ZcConfig.Population cfg) {
        double m = cfg.foldMargin;
        for (ServerPlayer p : players) {
            double dx = at.x - p.getX(), dy = Math.abs(at.y - p.getY()), dz = at.z - p.getZ(), h2 = dx * dx + dz * dz;
            if (h2 + dy * dy < 24 * 24) {
                return false;
            }
            double r = (indoor ? cfg.indoorRange : cfg.streetRange) + m;
            if (h2 <= r * r && dy <= (indoor ? cfg.indoorFloors + m / 2 : 56)) {
                return false;
            }
        }
        return true;
    }

    // ---- records: x and z in the chunk (4 bits each), y + 64 (9 bits), indoors (1), outfit + 1 (6, 0 = by the place)

    private static int pack(int lx, int y, int lz, boolean indoor, int outfit) {
        int yy = y + 64;
        if (yy < 0 || yy > 511) {
            return -1;
        }
        return lx | lz << 4 | yy << 8 | (indoor ? 1 << 17 : 0) | (outfit & 63) << 18;
    }

    private static int y(int rec) {
        return (rec >> 8 & 511) - 64;
    }

    private static List<String> outfits() {
        if (outfits == null) {
            List<String> names = new ArrayList<>(ZombieLook.outfits());
            for (String extra : List.of("soldier", "patient", "police", "doctor", "firefighter", "farmer")) {
                if (!names.contains(extra)) {
                    names.add(extra);
                }
            }
            outfits = names;
        }
        return outfits;
    }

    private static int outfitIndex(String name) {
        int i = outfits().indexOf(name);
        if (i < 0 && name != null) {
            List<String> names = new ArrayList<>(outfits());
            names.add(name);
            outfits = names;   // a new outfit name met this session; records keep it only while it's under 63
            i = names.size() - 1;
        }
        return i >= 0 && i < 63 ? i + 1 : 0;
    }

    private Population() {
    }
}
