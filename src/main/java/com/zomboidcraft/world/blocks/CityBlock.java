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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
            return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getClockWise().getAxis());
        }
    }
    /**
     * A tree limb: a post as thick as the limb, with an arm into each of its six neighbours that the
     * limb runs on into (the tree templates set them; the blockstate turns one arm model each way).
     */
    public static class Branch extends CityBlock {
        private static final BooleanProperty[] SIDES = {BlockStateProperties.NORTH, BlockStateProperties.SOUTH,
                BlockStateProperties.EAST, BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN};
        private final VoxelShape[] shapes = new VoxelShape[64];

        Branch(Properties properties, double width) {
            super(properties, Shapes.empty());
            double a = 8 - width / 2, b = 8 + width / 2;
            VoxelShape[] arms = {Block.box(a, a, 0, b, b, a), Block.box(a, a, b, b, b, 16), Block.box(b, a, a, 16, b, b),
                    Block.box(0, a, a, a, b, b), Block.box(a, b, a, b, 16, b), Block.box(a, 0, a, b, a, b)};
            for (int m = 0; m < 64; m++) {
                VoxelShape s = Block.box(a, a, a, b, b, b);
                for (int i = 0; i < 6; i++) {
                    if ((m >> i & 1) != 0) {
                        s = Shapes.or(s, arms[i]);
                    }
                }
                shapes[m] = s;
            }
            BlockState state = stateDefinition.any();
            for (BooleanProperty side : SIDES) {
                state = state.setValue(side, false);
            }
            registerDefaultState(state);
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SIDES);
        }

        @Override
        protected VoxelShape shape(BlockState state) {
            int m = 0;
            for (int i = 0; i < 6; i++) {
                if (state.getValue(SIDES[i])) {
                    m |= 1 << i;
                }
            }
            return shapes[m];
        }
    }
}
