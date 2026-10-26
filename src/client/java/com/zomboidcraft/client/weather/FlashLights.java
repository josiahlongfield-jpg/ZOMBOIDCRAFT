package com.zomboidcraft.client.weather;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Real light for a lightning flash, for shader packs: the sky and fog brightening (WeatherLayers)
 * is enough without one, but Photon lights the world its own way and ignores it. So for a few
 * ticks a flash also puts invisible light blocks in the air: a cluster where a bolt comes down,
 * a few high over the street round you for a flash in the clouds. Client side only, the same
 * trick the gun lights use, and every one goes back to air.
 */
final class FlashLights {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final List<Lit> LIT = new ArrayList<>();

    private record Lit(BlockPos pos, BlockState state, int until) {
    }

    private static int clock;

    /** A bolt coming down at x, z: its strike lights the street round it. */
    static void bolt(ClientLevel level, double x, double z) {
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
        for (int i = 0; i < 4; i++) {
            put(level, new BlockPos(Mth.floor(x) + RANDOM.nextInt(5) - 2, top + 1 + i * 3, Mth.floor(z) + RANDOM.nextInt(5) - 2),
                    15 - i, 5 + RANDOM.nextInt(3));
        }
    }

    /** A flash in the clouds, strength 0..1: light from above over the open street round the player. */
    static void sky(ClientLevel level, BlockPos around, float strength) {
        int count = 2 + Math.round(3 * strength);
        for (int i = 0; i < count; i++) {
            int x = around.getX() + RANDOM.nextInt(25) - 12, z = around.getZ() + RANDOM.nextInt(25) - 12;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(top - around.getY()) > 12) {
                continue;
            }
            put(level, new BlockPos(x, top + 5 + RANDOM.nextInt(4), z), Math.round(6 + 8 * strength), 3 + RANDOM.nextInt(3));
        }
    }

    private static void put(ClientLevel level, BlockPos pos, int brightness, int ticks) {
        if (!level.getBlockState(pos).isAir() || !level.isLoaded(pos)) {
            return;
        }
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Mth.clamp(brightness, 1, 15));
        level.setBlock(pos, light, FLAGS);
        LIT.add(new Lit(pos, light, clock + ticks));
    }

    static void tick(ClientLevel level) {
        clock++;
        LIT.removeIf(lit -> {
            if (clock < lit.until()) {
                return false;
            }
            if (level.getBlockState(lit.pos()) == lit.state()) {
                level.setBlock(lit.pos(), Blocks.AIR.defaultBlockState(), FLAGS);
            }
            return true;
        });
    }

    /** Drops what's in hand without touching the world (it was another world's). */
    static void forget() {
        LIT.clear();
    }

    private FlashLights() {
    }
}
