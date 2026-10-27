package com.zomboidcraft.world.county;

/**
 * One planned place: its kind and variant, its footprint in world blocks (both corners
 * included), the height its ground is levelled to, and the seed its builder draws from.
 *
 * Builders draw in the place's own coordinates: (0, 0, 0) is the footprint's north-west corner
 * at ground level, x runs east up to {@link #width()} - 1 and z south up to {@link #depth()} - 1.
 * Roads arrive at the middle of each side ({@link #gateX()}, {@link #gateZ()}), so every builder
 * runs a street or lane out to each side's midpoint.
 */
public final class Place {
    public final PlaceType type;
    /** Which of the type's variants, as the builder names them. */
    public final String variant;
    public final int x0, z0, x1, z1;
    public final int level;
    public final long seed;
    /**
     * For a port, the side the water is on (0 north, 1 east, 2 south, 3 west), and how far in from
     * that side the quay runs; -1 elsewhere.
     */
    public final int waterSide, quay;
    /** For a piece of the planned city's street grid, how it fits the grid; null for a place of its own. */
    public final Tile tile;

    /**
     * A piece of the planned city: the street grid runs on world lines every {@code pitch} blocks
     * (every third one an avenue), and the footprint runs either to the centre line of a street
     * shared with the next piece or out past the whole street where nothing is next door. Its
     * buildings grow taller toward its district's centre (cx, cz): density is the distance from
     * it over radius, plus offset (more walk-ups the higher it is).
     */
    public record Tile(int pitch, int cx, int cz, int radius, double offset) {}

    public Place(PlaceType type, String variant, int x0, int z0, int x1, int z1, int level, long seed, int waterSide, int quay) {
        this(type, variant, x0, z0, x1, z1, level, seed, waterSide, quay, null);
    }

    public Place(PlaceType type, String variant, int x0, int z0, int x1, int z1, int level, long seed, int waterSide, int quay, Tile tile) {
        this.type = type;
        this.variant = variant;
        this.x0 = x0;
        this.z0 = z0;
        this.x1 = x1;
        this.z1 = z1;
        this.level = level;
        this.seed = seed;
        this.waterSide = waterSide;
        this.quay = quay;
        this.tile = tile;
    }

    public int width() {
        return x1 - x0 + 1;
    }

    public int depth() {
        return z1 - z0 + 1;
    }

    public int centreX() {
        return (x0 + x1) / 2;
    }

    public int centreZ() {
        return (z0 + z1) / 2;
    }

    /** Local x of the north and south gates. */
    public int gateX() {
        return width() / 2;
    }

    /** Local z of the east and west gates. */
    public int gateZ() {
        return depth() / 2;
    }

    /** World position of the gate on a side (0 north, 1 east, 2 south, 3 west), as {x, z}. */
    public int[] gate(int side) {
        return switch (side & 3) {
            case 0 -> new int[] {x0 + gateX(), z0};
            case 1 -> new int[] {x1, z0 + gateZ()};
            case 2 -> new int[] {x0 + gateX(), z1};
            default -> new int[] {x0, z0 + gateZ()};
        };
    }

    public boolean contains(int x, int z, int margin) {
        return x >= x0 - margin && x <= x1 + margin && z >= z0 - margin && z <= z1 + margin;
    }

    /** Distance outside the footprint (0 inside). */
    public double outside(double x, double z) {
        double dx = Math.max(Math.max(x0 - x, x - x1), 0), dz = Math.max(Math.max(z0 - z, z - z1), 0);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** True if the column is in the port's basin (the water side of the quay). */
    public boolean inBasin(int x, int z) {
        if (waterSide < 0) {
            return false;
        }
        return switch (waterSide) {
            case 0 -> z - z0 < quay;
            case 1 -> x1 - x < quay;
            case 2 -> z1 - z < quay;
            default -> x - x0 < quay;
        };
    }

    public boolean overlaps(Place o, int margin) {
        return x0 - margin <= o.x1 && x1 + margin >= o.x0 && z0 - margin <= o.z1 && z1 + margin >= o.z0;
    }

    @Override
    public String toString() {
        return type + "/" + variant + " at " + centreX() + "," + centreZ() + " (" + width() + "x" + depth() + ", y=" + level + ")";
    }
}
