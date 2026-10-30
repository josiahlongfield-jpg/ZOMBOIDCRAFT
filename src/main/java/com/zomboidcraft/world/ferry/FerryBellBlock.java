package com.zomboidcraft.world.ferry;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ferry landing's bell: an iron post with a bracket and a brass bell hung from it, its pull rope
 * down the front. Rung with the interact key (right-click does nothing; it aims guns), it calls the
 * ferry and takes you across ({@link Ferries}). FACING is the way the bell faces, out from its post.
 */
public class FerryBellBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // a hair under a block tall, so rain still lands on the quay round it (see tools/city/heightmap_tags.py)
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Shapes.or(Block.box(7, 0, 12, 9, 15, 14), Block.box(5, 6, 5, 11, 14, 11)),
            Direction.SOUTH, Shapes.or(Block.box(7, 0, 2, 9, 15, 4), Block.box(5, 6, 5, 11, 14, 11)),
            Direction.WEST, Shapes.or(Block.box(12, 0, 7, 14, 15, 9), Block.box(5, 6, 5, 11, 14, 11)),
            Direction.EAST, Shapes.or(Block.box(2, 0, 7, 4, 15, 9), Block.box(5, 6, 5, 11, 14, 11)));

    public FerryBellBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
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
