package com.zomboidcraft.world.city;

/**
 * Where the city generator writes blocks. In game this wraps the chunk being generated (and
 * drops anything outside it); the preview tool collects the blocks into a file instead.
 * Blocks are named like "concrete_panel" or "desk[facing=east]"; a bare name is a ZomboidCraft
 * block, and "minecraft:..." names a vanilla one.
 */
public interface BlockSink {
    void set(int x, int y, int z, String block);

    /** The block already there, or null for air. Used to avoid overwriting roads with props. */
    default String get(int x, int y, int z) {
        return null;
    }

    /**
     * A car standing on the block below y, centred on (x, z) and turned to yaw (Minecraft's: 0
     * faces south, 90 west). The game picks a model between minLength and maxLength metres long and
     * how wrecked it is, from seed; it also lays the car's invisible collision blocks. The preview
     * ignores cars.
     */
    default void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
    }
}
