package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Base for the place builders: the place, where it draws, its random source, and helpers shared
 * by every kind of place. Coordinates are the place's own (see {@link Place}).
 */
abstract class Site {
    protected final Place p;
    protected final BlockSink out;
    protected final SplittableRandom rng;
    protected final int w, d;

    Site(Place p, BlockSink out) {
        this.p = p;
        this.out = out;
        this.rng = new SplittableRandom(p.seed);
        this.w = p.width();
        this.d = p.depth();
    }

    abstract void build();

    protected boolean variant(String name) {
        return p.variant.equals(name);
    }

    /** Lays one block over the whole footprint's ground (y = 0). */
    protected void ground(String block) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                out.set(x, 0, z, block);
            }
        }
    }

    protected void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    out.set(x, y, z, block);
                }
            }
        }
    }

    protected void setIfAir(int x, int y, int z, String block) {
        String there = out.get(x, y, z);
        if (there == null || there.equals("air")) {
            out.set(x, y, z, block);
        }
    }

    protected boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < w && z < d;
    }

    /** A placer for a building of w x d (its own coordinates) with its front facing the given side. */
    protected Placer lot(int minX, int minZ, int bw, int bd, int facing) {
        return Placer.forLot(out, minX, 0, minZ, bw, bd, facing);
    }

    protected double chance() {
        return rng.nextDouble();
    }

    protected <T> T pick(T[] options) {
        return options[rng.nextInt(options.length)];
    }

    static final String[] FACINGS = {"north", "east", "south", "west"};
}
