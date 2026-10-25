package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * Street dressing that tells the story of the collapse: abandoned and burnt-out cars, police
 * barriers, fallen masonry, weeds pushing through the asphalt, chain-link around empty lots.
 */
public final class Props {
    private final BlockSink sink;
    private final SplittableRandom rng;

    public Props(BlockSink sink, SplittableRandom rng) {
        this.sink = sink;
        this.rng = rng;
    }

    /**
     * A car in a three by six block space, its nose toward `facing` (0 north, 1 east, 2 south,
     * 3 west), the space's corner block at (x, y, z) in world space. Parked a little crooked.
     */
    public void car(int x, int y, int z, int facing) {
        boolean alongZ = (facing & 1) == 0;
        double cx = x + (alongZ ? 1.5 : 3.0) + (rng.nextDouble() - 0.5) * 0.4;
        double cz = z + (alongZ ? 3.0 : 1.5) + (rng.nextDouble() - 0.5) * 0.4;
        vehicle(cx, y, cz, yaw(facing) + (float) ((rng.nextDouble() - 0.5) * 14), 0, 5.6);
    }

    /**
     * Any vehicle between minLength and maxLength metres long (cars are 4 to 5.5, vans and trucks
     * 5 to 9.5, buses 11 to 12), centred on (x, z) and standing on the block below y, at any
     * angle. Street lanes, car park bays and multi-storey decks all use this.
     */
    public void vehicle(double x, int y, double z, float yaw, double minLength, double maxLength) {
        sink.car(x, y, z, yaw, minLength, maxLength, rng.nextLong());
    }

    /** Minecraft's yaw for a quarter-turn facing: north 180, east 270, south 0, west 90. */
    public static float yaw(int facing) {
        return ((facing & 3) * 90 + 180) % 360;
    }

    /** A row of concrete barriers across a road, with a gap someone pushed open. */
    public void barrierLine(int x0, int z0, int x1, int z1, int y) {
        boolean alongX = z0 == z1;
        int gap = rng.nextInt(Math.max(1, Math.abs(alongX ? x1 - x0 : z1 - z0)));
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (Math.abs(n++ - gap) < 2) {
                    continue;
                }
                sink.set(x, y, z, "jersey_barrier[facing=" + (alongX ? "north" : "east") + "]");
            }
        }
    }

    /** Broken masonry and litter scattered over an area, plus weeds through the cracks. */
    public void debris(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                String under = sink.get(x, y - 1, z);
                if (under == null || sink.get(x, y, z) != null) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < density * 0.25) {
                    sink.set(x, y, z, "rubble_pile");
                } else if (r < density * 0.4) {
                    sink.set(x, y, z, "debris_chunk[facing=" + dir() + "]");
                } else if (r < density * 0.75) {
                    sink.set(x, y, z, "weeds");
                } else if (r < density) {
                    sink.set(x, y, z, "moss_carpet");
                }
            }
        }
    }

    /** A chain-link fence with barbed wire on top, from (x0, z0) to (x1, z1) along one axis. */
    public void fence(int x0, int z0, int x1, int z1, int y, int height) {
        boolean alongX = z0 == z1;
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean post = n++ % 4 == 0;
                for (int h = 0; h < height; h++) {
                    sink.set(x, y + h, z, post ? "fence_post" : "chainlink[axis=" + (alongX ? "x" : "z") + "]");
                }
                sink.set(x, y + height, z, "barbed_wire[axis=" + (alongX ? "x" : "z") + "]");
            }
        }
    }

    private String dir() {
        return new String[] {"north", "east", "south", "west"}[rng.nextInt(4)];
    }
}
