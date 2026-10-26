package com.zomboidcraft.client.weather;

import com.zomboidcraft.world.weather.Weather;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where the rain goes after it lands: drips off roof edges, ledges and awnings, water pouring
 * out of the downpipes, splashes round your feet in the puddles, and now and then a drip
 * through the ceiling of a top-floor room. All of it scales with how hard it's raining.
 */
final class Water {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int[][] SIDES = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int SPOUT_REACH = 16, SPOUT_HEIGHT = 8, MAX_SPOUTS = 8;
    private static final List<BlockPos> SPOUTS = new ArrayList<>();
    private static int clock;

    static void tick(ClientLevel level, LocalPlayer player, Weather w) {
        clock++;
        float rain = w.rain();
        if (rain < 0.12f) {
            return;
        }
        edges(level, player, rain);
        if (clock % 40 == 0) {
            findSpouts(level, player.blockPosition());
        }
        spouts(level, rain);
        feet(level, player, rain);
        if (clock % 3 == 0) {
            leaks(level, player, rain);
        }
    }

    static void forget() {
        SPOUTS.clear();
    }

    /** Drips off the lip of a roof, ledge or awning where the ground beside it is lower, and from under overhangs. */
    private static void edges(ClientLevel level, LocalPlayer player, float rain) {
        int samples = Math.round(28 * rain);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            int x = player.getBlockX() + RANDOM.nextInt(29) - 14, z = player.getBlockZ() + RANDOM.nextInt(29) - 14;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(top - player.getY()) > 14) {
                continue;
            }
            int[] side = SIDES[RANDOM.nextInt(4)];
            int beside = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x + side[0], z + side[1]);
            if (beside <= top - 2) {
                // water running off the edge, just outside its face
                level.addParticle(ParticleTypes.DRIPPING_WATER, x + 0.5 + side[0] * 0.56 + (RANDOM.nextDouble() - 0.5) * 0.8 * Math.abs(side[1]),
                        top - 0.08, z + 0.5 + side[1] * 0.56 + (RANDOM.nextDouble() - 0.5) * 0.8 * Math.abs(side[0]), 0, 0, 0);
            }
            pos.set(x, top - 2, z);
            if (level.getBlockState(pos).isAir() && RANDOM.nextFloat() < 0.5f) {
                // an awning or overhang: soaked through, dripping from underneath
                level.addParticle(ParticleTypes.DRIPPING_WATER, x + RANDOM.nextDouble(), top - 1.06, z + RANDOM.nextDouble(), 0, 0, 0);
            }
        }
    }

    /** Looks round for the shoes of downpipes, where the water comes out. */
    private static void findSpouts(ClientLevel level, BlockPos center) {
        SPOUTS.clear();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -SPOUT_REACH; x <= SPOUT_REACH && SPOUTS.size() < MAX_SPOUTS; x++) {
            for (int z = -SPOUT_REACH; z <= SPOUT_REACH && SPOUTS.size() < MAX_SPOUTS; z++) {
                for (int y = -SPOUT_HEIGHT; y <= SPOUT_HEIGHT; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (isShoe(level.getBlockState(pos))) {
                        SPOUTS.add(pos.immutable());
                        break;
                    }
                }
            }
        }
    }

    private static boolean isShoe(BlockState state) {
        return !state.isAir() && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals("ext_downspout_shoe");
    }

    /** A stream out of each downpipe, splashing where it lands. */
    private static void spouts(ClientLevel level, float rain) {
        for (BlockPos spout : SPOUTS) {
            if (RANDOM.nextFloat() > rain) {
                continue;
            }
            double x = spout.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.25, z = spout.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 0.25;
            level.addParticle(ParticleTypes.FALLING_WATER, x, spout.getY() + 0.2, z, 0, 0, 0);
            if (RANDOM.nextFloat() < 0.5f) {
                level.addParticle(ParticleTypes.SPLASH, x, spout.getY() + 0.05, z, 0, 0.05, 0);
            }
        }
    }

    /** Puddle splashes round your feet as you walk or run out in the rain. */
    private static void feet(ClientLevel level, LocalPlayer player, float rain) {
        double speed = player.getDeltaMovement().horizontalDistance();
        if (!player.onGround() || speed < 0.05 || rain < 0.2f) {
            return;
        }
        int every = player.isSprinting() ? 4 : 7;
        if (clock % every != 0 || !level.isRainingAt(player.blockPosition().above())) {
            return;
        }
        int n = 3 + Math.round(5 * rain);
        for (int i = 0; i < n; i++) {
            level.addParticle(ParticleTypes.SPLASH, player.getX() + (RANDOM.nextDouble() - 0.5) * 0.7, player.getY() + 0.05,
                    player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.7, 0, 0.1, 0);
        }
    }

    /** A drip through the ceiling now and then in a top-floor room, from the roof right above it. */
    private static void leaks(ClientLevel level, LocalPlayer player, float rain) {
        if (RANDOM.nextFloat() > 0.35f * rain) {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = player.getBlockX() + RANDOM.nextInt(17) - 8, z = player.getBlockZ() + RANDOM.nextInt(17) - 8;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // the roof is the top block of the column: is there a room right under it, near your height?
        int ceiling = top - 1;
        if (ceiling - player.getBlockY() < 2 || ceiling - player.getBlockY() > 6) {
            return;
        }
        pos.set(x, ceiling - 1, z);
        if (level.getBlockState(pos).isAir()) {
            level.addParticle(ParticleTypes.DRIPPING_WATER, x + 0.2 + RANDOM.nextDouble() * 0.6, ceiling - 0.06, z + 0.2 + RANDOM.nextDouble() * 0.6, 0, 0, 0);
        }
    }

    private Water() {
    }
}
