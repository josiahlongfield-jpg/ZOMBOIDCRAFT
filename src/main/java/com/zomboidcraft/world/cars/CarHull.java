package com.zomboidcraft.world.cars;

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
 * The solid part of a car: an invisible block as tall as the car body above it, in sixteenths.
 * It can't be seen, targeted or broken, so you climb on the bonnet and roof of the model itself.
 */
public class CarHull extends Block {
    public static final IntegerProperty HEIGHT = IntegerProperty.create("h", 1, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[17];

    static {
        for (int h = 1; h <= 16; h++) {
            SHAPES[h] = Block.box(0, 0, 0, 16, h, 16);
        }
    }

    public CarHull(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HEIGHT, 16));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HEIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(HEIGHT)];
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
