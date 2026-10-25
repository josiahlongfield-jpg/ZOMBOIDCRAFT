package com.zomboidcraft.world.county;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Keeps the last few built places in memory. A place is built once, by whichever chunk asks
 * first; chunks generating at the same time on other threads wait for that build rather than
 * repeating it.
 */
public final class PlaceCache {
    /** Builders share a little state (the shop and sign rotations), so one place is built at a time. */
    private static final Object BUILD_LOCK = new Object();

    private final int capacity;
    private final Map<Place, CompletableFuture<Voxels>> built;

    public PlaceCache(int capacity) {
        this.capacity = capacity;
        this.built = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Place, CompletableFuture<Voxels>> eldest) {
                return size() > PlaceCache.this.capacity;
            }
        };
    }

    public Voxels get(Place p, County county) {
        CompletableFuture<Voxels> f;
        boolean mine = false;
        synchronized (built) {
            f = built.get(p);
            if (f == null) {
                f = new CompletableFuture<>();
                built.put(p, f);
                mine = true;
            }
        }
        if (mine) {
            try {
                Voxels v = new Voxels();
                synchronized (BUILD_LOCK) {
                    Builders.build(p, county, v);
                }
                f.complete(v);
            } catch (RuntimeException | Error e) {
                // complete it either way: a chunk waiting on a build that died would wait forever
                f.completeExceptionally(e);
                synchronized (built) {
                    built.remove(p);
                }
                throw e;
            }
        }
        return f.join();
    }
}
