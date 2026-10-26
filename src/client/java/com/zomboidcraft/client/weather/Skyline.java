package com.zomboidcraft.client.weather;

import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Fires burning out across the city: a few smoke columns standing over the skyline, leaning with
 * the wind, an orange glow at their feet; and where lightning really struck, the ground smoulders
 * for a few minutes after the flames die.
 *
 * The far fires are made up on this side: the world is cut into big cells and a few cells have a
 * fire, picked by a hash of the cell, so the same columns stand in the same places every visit.
 * They're only drawn from a distance (they are never there when you walk up).
 */
final class Skyline {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int CELL = 176;
    /** Far fires are drawn between these distances: beyond the street you're in, inside the haze. */
    private static final double NEAR = 80, FAR = 240;
    private static final int MAX_FIRES = 4;
    private static final List<Smoulder> SMOULDERS = new ArrayList<>();

    private record Smoulder(double x, double z, int until) {
    }

    private static int clock;

    /** Lightning struck here for real: let it smoke a while. */
    static void struck(double x, double z) {
        SMOULDERS.add(new Smoulder(x, z, clock + 20 * (150 + RANDOM.nextInt(150))));
    }

    static void tick(ClientLevel level, LocalPlayer player, Weather w) {
        clock++;
        double yaw = Math.toRadians(w.windYaw());
        double windX = -Math.sin(yaw), windZ = Math.cos(yaw);
        double lean = 0.02 + 0.09 * w.wind();
        if (clock % 5 == 0) {
            int fires = 0;
            int cx = Mth.floor(player.getX() / CELL), cz = Mth.floor(player.getZ() / CELL);
            for (int dx = -2; dx <= 2 && fires < MAX_FIRES; dx++) {
                for (int dz = -2; dz <= 2 && fires < MAX_FIRES; dz++) {
                    long h = hash(cx + dx, cz + dz);
                    if ((h & 1023) >= 170) {
                        continue;  // about one cell in six has something burning
                    }
                    double x = (cx + dx) * CELL + 24 + ((h >>> 10) & 127), z = (cz + dz) * CELL + 24 + ((h >>> 17) & 127);
                    double d = Math.hypot(x - player.getX(), z - player.getZ());
                    if (d < NEAR || d > FAR) {
                        continue;
                    }
                    fires++;
                    double y = ground(level, player, x, z);
                    float big = 5 + ((h >>> 24) & 7);
                    level.addParticle(WeatherParticles.PLUME, x + RANDOM.nextGaussian() * 2, y + RANDOM.nextDouble() * 3, z + RANDOM.nextGaussian() * 2,
                            windX * lean, big, windZ * lean);
                    level.addParticle(WeatherParticles.EMBER, x + RANDOM.nextGaussian() * 2.5, y + RANDOM.nextDouble() * 2, z + RANDOM.nextGaussian() * 2.5,
                            0, big * 0.7, 0);
                }
            }
        }
        if (clock % 7 == 0) {
            SMOULDERS.removeIf(s -> clock > s.until());
            for (Smoulder s : SMOULDERS) {
                double y = ground(level, player, s.x(), s.z());
                level.addParticle(WeatherParticles.PLUME, s.x() + RANDOM.nextGaussian() * 0.6, y + 0.2, s.z() + RANDOM.nextGaussian() * 0.6,
                        windX * lean * 0.6, 1.2f + RANDOM.nextFloat(), windZ * lean * 0.6);
            }
        }
    }

    static void forget() {
        SMOULDERS.clear();
    }

    /** The ground under a fire: the real roofline where it's loaded, a guess above you where it isn't. */
    private static double ground(ClientLevel level, LocalPlayer player, double x, double z) {
        BlockPos at = BlockPos.containing(x, player.getY(), z);
        if (level.isLoaded(at)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING, at.getX(), at.getZ());
        }
        return player.getY() + 18;
    }

    private static long hash(int x, int z) {
        long h = x * 0x9E3779B97F4A7C15L + z * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return (h ^ (h >>> 29)) & Long.MAX_VALUE;
    }

    private Skyline() {
    }
}
