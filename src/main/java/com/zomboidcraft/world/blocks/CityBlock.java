package com.zomboidcraft.world.blocks;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the city's blocks from tools/city/blocks.json. Shapes are drawn facing north; the
 * {@link Facing} and {@link Axis} kinds turn them the same way their blockstates turn the models.
 */
public class CityBlock extends Block {
    private final VoxelShape shape;

    CityBlock(Properties properties, VoxelShape shape) {
        super(properties);
        this.shape = shape;
    }

    protected VoxelShape shape(BlockState state) {
        return shape;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    /** Turns to face the player, or for wall decals like ivy and graffiti, hangs on the clicked wall. */
    public static class Facing extends CityBlock {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        private final Map<Direction, VoxelShape> shapes;
        private final boolean onWall;

        Facing(Properties properties, VoxelShape north, boolean onWall) {
            super(properties, north);
            this.shapes = Shapes.rotateHorizontal(north);
            this.onWall = onWall;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        protected VoxelShape shape(BlockState state) {
            return shapes.get(state.getValue(FACING));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            Direction face = context.getClickedFace();
            Direction facing = onWall && face.getAxis().isHorizontal() ? face.getOpposite()
                    : context.getHorizontalDirection().getOpposite();
            return defaultBlockState().setValue(FACING, facing);
        }
    }

    /** Runs along x or z, like windows, fences and rails; placed across the player's view. */
    public static class Axis extends CityBlock {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private final VoxelShape alongX;

        Axis(Properties properties, VoxelShape alongZ) {
            super(properties, alongZ);
            this.alongX = Shapes.rotateHorizontal(alongZ).get(Direction.EAST);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS);
        }

        @Override
        protected VoxelShape shape(BlockState state) {
            return state.getValue(AXIS) == Direction.Axis.X ? alongX : super.shape(state);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
        }
    }
}
