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

    /**
     * Like {@link #car}, but one of the named models or kinds ("school_bus", "police_sedan,police_suv",
     * "emergency"): the fire engine at the fire station, the cruisers outside the sheriff's. A name
     * ending "@wrecked" (or another of the cars' variants) leaves it that way.
     */
    default void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        if (models.startsWith("!")) {
            return;     // a lift ("!hoist", see BlockSink#hoist), which only the chunk writer places
        }
        car(x, y, z, yaw, 0, 99, seed);
    }

    /**
     * A two-post car lift (an entity, not blocks) centred in its bay at (x, z) on floor level y, the bay running
     * along the yaw (0: along z, a car's nose to +z). Its posts stand 1.7 either side of the centre line, so
     * centre it on a block line of a 4-wide bay. With car models (as {@link #vehicle} takes them, "=sedan@usable"
     * or "sedan,coupe"), one that fits stands in the bay, up at notch 0 (down), 1 (wheels free), 2 (chest
     * height) or 3 (full height). It goes through {@link #vehicle} so every sink passes it on.
     */
    default void hoist(double x, int y, double z, float yaw, int notch, String car, long seed) {
        vehicle(x, y, z, yaw, "!hoist:" + notch + (car == null ? "" : ";" + car), seed);
    }

    /** Notes a spot worth finding again (what: "bunker" for a bunker's hatch); the game ignores it. */
    default void mark(String what, int x, int y, int z) {
    }
}
