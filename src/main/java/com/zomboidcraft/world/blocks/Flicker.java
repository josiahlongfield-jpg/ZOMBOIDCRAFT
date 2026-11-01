package com.zomboidcraft.world.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Lamps on a dying circuit: the strip lights of a broken-into bunker and the fittings hanging off
 * their cables. LIT flips on scheduled ticks. Half of them are mostly dark and stutter into light
 * in short bursts; the rest stay on and drop out for a moment now and then. Blocks the world
 * generator placed get no placement callback, so a random tick starts the first flicker, and from
 * then on each flip schedules the next. With performance.flickerLamps off they stay lit instead.
 */
final class Flicker {
    static final BooleanProperty LIT = BlockStateProperties.LIT;

    static void start(Block block, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!com.zomboidcraft.ZcConfig.get().performance.flickerLamps) {
            steady(level, pos, level.getBlockState(pos));
            return;
        }
        if (!level.getBlockTicks().hasScheduledTick(pos, block)) {
            level.scheduleTick(pos, block, 1 + random.nextInt(10));
        }
    }

    /** With flickering off (performance.flickerLamps): the lamp just stays on, and nothing more is scheduled. */
    private static void steady(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.hasProperty(LIT) && !state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_CLIENTS);
        }
    }

    static void flip(Block block, BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!com.zomboidcraft.ZcConfig.get().performance.flickerLamps) {
            steady(level, pos, state);
            return;
        }
        boolean lit = !state.getValue(LIT);
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
        com.zomboidcraft.zombie.Profiler.FLICKERS.increment();
        boolean dying = (pos.asLong() * 0x9E3779B97F4A7C15L >>> 63) == 0;
        int next;
        if (dying) {
            // dark, then a stutter of quick flashes
            next = lit ? 1 + random.nextInt(4) : random.nextFloat() < 0.65 ? 1 + random.nextInt(4) : 30 + random.nextInt(140);
        } else {
            // on, with a quick stutter out every so often
            next = lit ? random.nextFloat() < 0.4 ? 1 + random.nextInt(3) : 40 + random.nextInt(200) : 1 + random.nextInt(3);
        }
        level.scheduleTick(pos, block, next);
    }

    static class Plain extends CityBlock {
        Plain(Properties properties, VoxelShape shape) {
            super(properties, shape);
            registerDefaultState(stateDefinition.any().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LIT);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            start(this, level, pos, random);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            flip(this, state, level, pos, random);
        }
    }

    static class Facing extends CityBlock.Facing {
        Facing(Properties properties, VoxelShape north) {
            super(properties, north, true);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LIT);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            start(this, level, pos, random);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            flip(this, state, level, pos, random);
        }
    }

    static class Axis extends CityBlock.Axis {
        Axis(Properties properties, VoxelShape alongZ) {
            super(properties, alongZ);
            registerDefaultState(defaultBlockState().setValue(LIT, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LIT);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            start(this, level, pos, random);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            flip(this, state, level, pos, random);
        }
    }

    private Flicker() {
    }
}
