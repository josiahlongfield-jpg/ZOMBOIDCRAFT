package com.zomboidcraft.combat.throwing;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Flames from a blast, a molotov or a flare. Unlike Minecraft's fire they never spread by themselves: ContainedFire
 * moves them, inside the reach of the fire they belong to, and takes them away when it burns out. One that has lost
 * its fire (the world was reloaded) goes out on its next random tick.
 */
public class ContainedFireBlock extends BaseFireBlock {
    public static final MapCodec<ContainedFireBlock> CODEC = simpleCodec(ContainedFireBlock::new);

    public ContainedFireBlock(Properties properties) {
        super(properties, 1.0f);
    }

    @Override
    protected MapCodec<? extends BaseFireBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean canBurn(BlockState state) {
        return ContainedFire.flammable(state);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
            return true;
        }
        for (Direction d : Direction.values()) {
            if (ContainedFire.flammable(level.getBlockState(pos.relative(d)))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!ContainedFire.tracked(level, pos)) {
            level.removeBlock(pos, false);
        }
    }
}
