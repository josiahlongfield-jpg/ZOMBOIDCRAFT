package com.zomboidcraft.zombie.population;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.ChunkSink;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.CountyGen;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceCache;
import com.zomboidcraft.world.gen.ChunkWriter;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How much floor space a place has, indoors and out, so its dead can be shared over it before a player has seen most
 * of it: off the server thread, a sample of the place's chunks is built the way the world builds them and its spots
 * counted, then scaled up to the whole place. Worked out once a session per place (the generator gives the same
 * place each time).
 */
final class Census {
    /** Standing spots in a place: indoors and out. */
    record Count(double indoor, double outdoor) {
    }

    /** Chunks built per place; a place this size or smaller is counted whole. */
    private static final int SAMPLE = 64;

    private static final Map<String, Count> DONE = new ConcurrentHashMap<>();
    private static final Set<String> RUNNING = ConcurrentHashMap.newKeySet();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ZomboidCraft population census");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /**
     * The census's own generator beside each world one: same county, but its own small place cache, so
     * counting a big landmark doesn't push the places round the players out of the world's cache (they'd
     * be built again while chunks wait, which is what made the game stall without the frame rate dropping).
     */
    private static final Map<CountyGen, CountyGen> GENS = Collections.synchronizedMap(new java.util.WeakHashMap<>());

    private static CountyGen side(CountyGen world) {
        return GENS.computeIfAbsent(world, w -> new CountyGen(w.county(), new PlaceCache(4, w.cache())));
    }

    static String key(Place p) {
        return p.type + "/" + p.variant + "/" + p.x0 + "," + p.z0 + "/" + p.seed;
    }

    /** The place's count, or null while it's still being worked out (asked for here the first time). */
    static Count get(CountyGen gen, Place p, int minY, int maxY) {
        String key = key(p);
        Count c = DONE.get(key);
        if (c == null && RUNNING.add(key)) {
            POOL.submit(() -> {
                try {
                    DONE.put(key, count(side(gen), p, minY, maxY));
                } catch (Throwable e) {
                    ZomboidCraft.LOGGER.error("Population census failed for {}", key, e);
                    // a rough guess from its size, so the place still fills
                    double area = (double) p.width() * p.depth();
                    DONE.put(key, new Count(area * 1.5, area * 0.5));
                } finally {
                    RUNNING.remove(key);
                }
            });
        }
        return c;
    }

    static int pending() {
        return RUNNING.size();
    }

    static void clear() {
        DONE.clear();
    }

    private static Count count(CountyGen gen, Place p, int minY, int maxY) {
        long start = System.nanoTime();
        List<long[]> chunks = new ArrayList<>();
        for (int cx = Math.floorDiv(p.x0, 16); cx <= Math.floorDiv(p.x1, 16); cx++) {
            for (int cz = Math.floorDiv(p.z0, 16); cz <= Math.floorDiv(p.z1, 16); cz++) {
                chunks.add(new long[] {cx, cz});
            }
        }
        int all = chunks.size();
        if (all > SAMPLE) {
            Collections.shuffle(chunks, new Random(p.seed));
            chunks = chunks.subList(0, SAMPLE);
        }
        County county = gen.county();
        double[] n = new double[2];
        for (long[] c : chunks) {
            int bx = (int) c[0] * 16, bz = (int) c[1] * 16;
            Sink sink = new Sink(bx, bz, minY, maxY);
            gen.generate((int) c[0], (int) c[1], sink);
            List<Place> near = county.placesNear(bx, bz, bx + 15, bz + 15, County.SKIRT);
            Spots.scan(sink, bx, bz, minY, maxY, (x, z) -> CountyChunkGenerator.surface(county, x, z, near),
                    (lx, y, lz, indoor) -> {
                        if (p.contains(bx + lx, bz + lz, 0)) {
                            n[indoor ? 0 : 1]++;
                        }
                    });
        }
        double scale = (double) all / chunks.size();
        ZomboidCraft.LOGGER.info("Population census of {} {}: {} indoor and {} outdoor spots ({} of {} chunks, {} ms)",
                p.type, p.variant, Math.round(n[0] * scale), Math.round(n[1] * scale), chunks.size(), all,
                (System.nanoTime() - start) / 1_000_000);
        return new Count(n[0] * scale, n[1] * scale);
    }

    /** A chunk built off to the side: just its blocks, by name turned to the same states the world uses. */
    private static final class Sink implements ChunkSink, Spots.Blocks {
        private static final BlockState AIR = Blocks.AIR.defaultBlockState();
        private final int bx, bz, minY, h;
        private final BlockState[] states;

        Sink(int bx, int bz, int minY, int maxY) {
            this.bx = bx;
            this.bz = bz;
            this.minY = minY;
            this.h = maxY - minY;
            this.states = new BlockState[16 * 16 * h];
        }

        @Override
        public void set(int x, int y, int z, String block) {
            int lx = x - bx, lz = z - bz, i = y - minY;
            if (lx < 0 || lz < 0 || lx > 15 || lz > 15 || i < 0 || i >= h) {
                return;
            }
            states[(i * 16 + lz) * 16 + lx] = ChunkWriter.resolve(block);
        }

        @Override
        public int minY() {
            return minY;
        }

        @Override
        public BlockState at(int lx, int y, int lz) {
            BlockState s = states[((y - minY) * 16 + lz) * 16 + lx];
            return s == null ? AIR : s;
        }
    }

    private Census() {
    }
}
