package com.zomboidcraft.world.trains;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A station's call button: a yellow box on the wall or a post with a big red button. Pressed with
 * the interact key (right-click does nothing; it aims guns), it brings the train straight here, and
 * sends up a flare (railway) or sets off the siren (subway) that every zombie around hears.
 * FACING is the way the button faces, out from what it's fixed to.
 */
public class CallButtonBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CallButtonBlock> CODEC = simpleCodec(CallButtonBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(4, 3, 12, 12, 13, 16),
            Direction.SOUTH, Block.box(4, 3, 0, 12, 13, 4),
            Direction.WEST, Block.box(12, 3, 4, 16, 13, 12),
            Direction.EAST, Block.box(0, 3, 4, 4, 13, 12));

    public CallButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }
}
