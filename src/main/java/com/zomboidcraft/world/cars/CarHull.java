package com.zomboidcraft.world.cars;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The solid part of a car: an invisible block whose four quarters (half a block each way) are each
 * as tall as the car body above them, in quarter blocks, so the car's outline is followed to within
 * a few inches. It can't be seen, targeted or broken, so you climb on the bonnet and roof of the
 * model itself, and bullets stop where the body is.
 */
public class CarHull extends Block {
    /** Quarter heights, 0 to 4 quarter blocks: north-west, north-east, south-west, south-east. */
    public static final List<IntegerProperty> QUARTERS = List.of(IntegerProperty.create("nw", 0, 4),
            IntegerProperty.create("ne", 0, 4), IntegerProperty.create("sw", 0, 4), IntegerProperty.create("se", 0, 4));
    private static final VoxelShape[] SHAPES = new VoxelShape[625];

    static {
        for (int code = 0; code < 625; code++) {
            VoxelShape shape = Shapes.empty();
            for (int q = 0; q < 4; q++) {
                int h = level(code, q);
                if (h > 0) {
                    int x = (q & 1) * 8, z = (q >> 1) * 8;
                    shape = Shapes.or(shape, Block.box(x, 0, z, x + 8, h * 4, z + 8));
                }
            }
            SHAPES[code] = shape.optimize();
        }
    }

    /** One quarter's height (0-4) out of a code packing all four in base 5, north-west first. */
    public static int level(int code, int quarter) {
        for (int i = 0; i < quarter; i++) {
            code /= 5;
        }
        return code % 5;
    }

    public CarHull(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (IntegerProperty quarter : QUARTERS) {
            state = state.setValue(quarter, 0);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        QUARTERS.forEach(builder::add);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int code = 0;
        for (int q = 3; q >= 0; q--) {
            code = code * 5 + state.getValue(QUARTERS.get(q));
        }
        return SHAPES[code];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1f;
    }
}
