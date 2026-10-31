package com.zomboidcraft.world.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The traffic signals over the town crossings, still running their cycle: green a long while, a
 * short amber, then red while the cross street has its turn, a moment of all red between. Every
 * head reads the same world clock, so the heads at a crossing agree; a head facing north or south
 * faces the traffic along z. PHASE: 0 dark (not started yet), 1 north-south green, 2 north-south
 * amber, 3 all red, 4 east-west green, 5 east-west amber; the blockstates pick each head's lens.
 * The world generator marks them for post-processing, so the first neighbour update starts them;
 * a random tick starts any that missed it, and from then on each change schedules the next. With the district's
 * power off (power/PowerGrid) a head is dark.
 */
public final class Signal {
    static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 5);
    /** Ticks: the green each way, the amber after it, the all red before the other way goes. */
    static final int GREEN = 800, AMBER = 80, CLEAR = 40, HALF = GREEN + AMBER + CLEAR, CYCLE = 2 * HALF;

    /** A block the world generator should mark so it starts on its own (see {@link #start}). */
    public interface Lamp {
    }

    static int phase(long time) {
        int t = (int) Math.floorMod(time, (long) CYCLE);
        int half = t < HALF ? 0 : 3;
        int u = t % HALF;
        return u < GREEN ? 1 + half : u < GREEN + AMBER ? 2 + half : 3;
    }

    /** Ticks from `time` to the next change of phase. */
    static int untilNext(long time) {
        int u = (int) Math.floorMod(time, (long) HALF);
        int edge = u < GREEN ? GREEN : u < GREEN + AMBER ? GREEN + AMBER : HALF;
        return Math.max(1, edge - u);
    }

    static void step(Block block, BlockState state, ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        if (!com.zomboidcraft.power.PowerGrid.hasPower(level, pos)) {
            // the district's mains are off: the head goes dark, and looks again now and then (a restored substation)
            if (state.getValue(PHASE) != 0) {
                level.setBlock(pos, state.setValue(PHASE, 0), Block.UPDATE_CLIENTS);
            }
            level.scheduleTick(pos, block, 1200);
            return;
        }
        int phase = phase(now);
        if (state.getValue(PHASE) != phase) {
            level.setBlock(pos, state.setValue(PHASE, phase), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, block, untilNext(now));
    }

    static class Facing extends CityBlock.Facing implements Lamp {
        Facing(Properties properties, VoxelShape north) {
            super(properties, north, false);
            registerDefaultState(defaultBlockState().setValue(PHASE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(PHASE);
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                step(this, state, level, pos);
            }
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            step(this, state, level, pos);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                         Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            // one pending tick per spot: this only starts a head that isn't running yet
            ticks.scheduleTick(pos, this, 1);
            return state;
        }
    }

    private Signal() {
    }
}
