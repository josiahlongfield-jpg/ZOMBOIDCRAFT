package com.zomboidcraft.world.reach;

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
 * The Reach world's invisible walls: one block of a Halo level's own collision. You never see it (the level's real
 * mesh is drawn over it), can't target or break it; you stand on it and bump into it. The top block of a floor keeps
 * the height the level's floor really reaches inside it, in sixteenths ("layers", 16 = a whole block), so floors and
 * slopes sit where the level has them rather than on whole blocks.
 *
 * <p>It stops rain (it's in the blocks_motion tags) and the sky's light, so a roof keeps the rain and daylight out as
 * the level's roof does.
 */
public class ReachSolid extends Block {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 16);
    private static final VoxelShape[] SHAPES = new VoxelShape[17];

    static {
        for (int i = 1; i <= 16; i++) {
            SHAPES[i] = i == 16 ? Shapes.block() : Block.box(0, 0, 0, 16, i, 16);
        }
    }

    public ReachSolid(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 16));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS)];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return false;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1f;
    }
}
