package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;

/**
 * A sink that draws through a turned frame, so a builder (and the {@link Kit} it lays its streets
 * with) can work as if its place always faced the same way: a port drawn with its water to the
 * north whichever side the water is really on.
 */
final class FrameSink implements BlockSink {
    private final Placer frame;

    FrameSink(Placer frame) {
        this.frame = frame;
    }

    @Override
    public void set(int x, int y, int z, String block) {
        frame.set(x, y, z, block);
    }

    @Override
    public String get(int x, int y, int z) {
        return frame.get(x, y, z);
    }

    @Override
    public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
        frame.car(x, y, z, yaw, minLength, maxLength, seed);
    }

    @Override
    public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        frame.vehicle(x, y, z, yaw, models, seed);
    }
}
