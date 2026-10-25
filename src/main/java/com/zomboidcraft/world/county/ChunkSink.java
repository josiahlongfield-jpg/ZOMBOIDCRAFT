package com.zomboidcraft.world.county;

/**
 * Where {@link CountyGen} writes one chunk, in world coordinates. In game it wraps the chunk
 * being generated; the preview tool writes into a voxel file instead. Block names follow
 * {@link com.zomboidcraft.world.city.BlockSink}: bare names are ZomboidCraft blocks,
 * "minecraft:..." vanilla ones.
 */
public interface ChunkSink {
    void set(int x, int y, int z, String block);

    /** Fills a column from y0 to y1, both included. */
    default void column(int x, int z, int y0, int y1, String block) {
        for (int y = y0; y <= y1; y++) {
            set(x, y, z, block);
        }
    }

    /** A parked car from a place's builder, standing at world coordinates. */
    default void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
    }

    /** One of the named models or kinds (see {@link com.zomboidcraft.world.city.BlockSink#vehicle}). */
    default void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        car(x, y, z, yaw, 0, 99, seed);
    }

    /** The bottom of the world. */
    default int minY() {
        return -64;
    }
}
