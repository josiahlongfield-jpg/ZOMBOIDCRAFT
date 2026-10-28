package com.zomboidcraft.world.county;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps the last few built places in memory. A place is built once, by whichever chunk asks
 * first; chunks generating at the same time on other threads wait for that build rather than
 * repeating it.
 */
public final class PlaceCache {
    /** Builders share a little state (the shop and sign rotations), so one place is built at a time. */
    private static final Object BUILD_LOCK = new Object();
    /** World-gen builds waiting for or holding the lock; a background cache stands back while there are any. */
    private static final AtomicInteger WORLD_BUILDS = new AtomicInteger();
    /** The longest a background build stands back for, so it can't wait forever while the world keeps building. */
    private static final long YIELD_MS = 15_000;

    private final int capacity;
    private final Map<Place, CompletableFuture<Voxels>> built;
    /** For a background cache (the population census): the world's own cache, used where it has the place already. */
    private final PlaceCache borrow;

    public PlaceCache(int capacity) {
        this(capacity, null);
    }

    /**
     * A background cache beside the world's: it takes places the world has built already, builds the
     * rest itself (so it never pushes the world's places out of its cache), and lets world-gen builds
     * go first.
     */
    public PlaceCache(int capacity, PlaceCache borrow) {
        this.capacity = capacity;
        this.borrow = borrow;
        this.built = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Place, CompletableFuture<Voxels>> eldest) {
                return size() > PlaceCache.this.capacity;
            }
        };
    }

    public Voxels get(Place p, County county) {
        if (borrow != null) {
            CompletableFuture<Voxels> theirs;
            synchronized (borrow.built) {
                theirs = borrow.built.get(p);
            }
            if (theirs != null) {
                try {
                    return theirs.join();
                } catch (RuntimeException e) {
                    // their build died; build it here instead
                }
            }
        }
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
            if (borrow == null) {
                WORLD_BUILDS.incrementAndGet();
            } else {
                standBack();
            }
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
            } finally {
                if (borrow == null) {
                    WORLD_BUILDS.decrementAndGet();
                }
            }
        }
        return f.join();
    }

    /** Waits (a while at most) until no world-gen build wants the lock. */
    private static void standBack() {
        long until = System.currentTimeMillis() + YIELD_MS;
        while (WORLD_BUILDS.get() > 0 && System.currentTimeMillis() < until) {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
