package com.zomboidcraft.barricade;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Planks nailed across a window or doorway. FACING is the side the boards sit on.
 * STRENGTH comes from the builder's carpentry and makes each board harder to knock off.
 */
public class BarricadeBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PLANKS = IntegerProperty.create("planks", 1, 4);
    public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 0, 2);
    /** Blows the outer board has taken, in eighths of what it can stand (ZcConfig building.boardHits). */
    public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 7);
    public static final int MAX_PLANKS = 4;

    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, 3),
            Direction.SOUTH, Block.box(0, 0, 13, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, 3, 16, 16),
            Direction.EAST, Block.box(13, 0, 0, 16, 16, 16));

    public BarricadeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(PLANKS, 1).setValue(STRENGTH, 0).setValue(DAMAGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLANKS, STRENGTH, DAMAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Blows one board stands, by how well it was nailed. */
    public static int boardHits(BlockState state) {
        var c = com.zomboidcraft.ZcConfig.get().building;
        return Math.max(1, c.boardHits + c.boardHitsPerStrength * state.getValue(STRENGTH));
    }

    public static int strengthFor(int carpentryLevel) {
        return carpentryLevel >= 8 ? 2 : carpentryLevel >= 4 ? 1 : 0;
    }
}
