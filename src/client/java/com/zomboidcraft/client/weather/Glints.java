package com.zomboidcraft.client.weather;

import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Rain lit up round every lamp: the drops falling through a streetlight's or a sign's light
 * catch it and flash past in its colour, so each lamp stands in its own cone of sparkling rain.
 */
final class Glints {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int REACH = 20, BELOW = 6, ABOVE = 16, MAX = 16;
    /** How bright a block's light must be to light the rain round it. */
    private static final int BRIGHT = 10;
    private static final List<Lamp> LAMPS = new ArrayList<>();
    private static int clock;
    /** The wind's lean on the falling drops, given to each new glint. */
    static double leanX, leanZ;

    private record Lamp(BlockPos pos, float r, float g, float b) {
    }

    static void tick(ClientLevel level, LocalPlayer player, Weather w, double windX, double windZ) {
        if (clock++ % 40 == 0) {
            find(level, player.blockPosition());
        }
        float rain = w.rain();
        if (rain < 0.15f || LAMPS.isEmpty()) {
            return;
        }
        double lean = 0.05 + 0.2 * w.wind();
        leanX = windX * lean;
        leanZ = windZ * lean;
        for (Lamp lamp : LAMPS) {
            for (int i = 0, n = Math.round(3 * rain + RANDOM.nextFloat()); i < n; i++) {
                double a = RANDOM.nextDouble() * Math.PI * 2, d = Math.sqrt(RANDOM.nextDouble()) * 3;
                double x = lamp.pos.getX() + 0.5 + Math.cos(a) * d, z = lamp.pos.getZ() + 0.5 + Math.sin(a) * d;
                double y = lamp.pos.getY() + 1.5 - RANDOM.nextDouble() * 5;
                // only where the rain actually falls
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z)) > y) {
                    continue;
                }
                // brightest close to the lamp
                float k = (float) (1 - d / 4);
                level.addParticle(WeatherParticles.GLINT, x, y, z, lamp.r * k, lamp.g * k, lamp.b * k);
            }
        }
    }

    /** Looks round for the lamps: anything giving off bright light. */
    private static void find(ClientLevel level, BlockPos center) {
        LAMPS.clear();
        List<Lamp> found = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -REACH; x <= REACH; x++) {
            for (int z = -REACH; z <= REACH; z++) {
                for (int y = -BELOW; y <= ABOVE; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = level.getBlockState(pos);
                    if (state.getLightEmission() >= BRIGHT && !state.is(Blocks.LIGHT) && !state.is(Blocks.FIRE)) {
                        found.add(colour(pos.immutable(), state));
                    }
                }
            }
        }
        while (LAMPS.size() < MAX && !found.isEmpty()) {
            LAMPS.add(found.remove(RANDOM.nextInt(found.size())));
        }
    }

    private static Lamp colour(BlockPos pos, BlockState state) {
        if (state.is(Blocks.PEARLESCENT_FROGLIGHT)) {
            return new Lamp(pos, 0.95f, 0.72f, 1f);
        }
        if (state.is(Blocks.VERDANT_FROGLIGHT)) {
            return new Lamp(pos, 0.62f, 1f, 0.74f);
        }
        if (state.is(Blocks.OCHRE_FROGLIGHT)) {
            return new Lamp(pos, 1f, 0.86f, 0.5f);
        }
        return new Lamp(pos, 1f, 0.9f, 0.74f);  // a sodium streetlight's warm white
    }

    static void forget() {
        LAMPS.clear();
    }

    private Glints() {
    }
}
