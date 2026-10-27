package com.zomboidcraft.world.lifts;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
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
 * One leaf of a lift's landing doors, half its height: brushed steel in the wall, flush with the
 * landing side. FACING looks out of the shaft onto the landing; SIDE is the leaf as you stand in
 * front of the doors (left or right); PART the lower or upper block. Only the lift opens them
 * (OPEN: gone from the doorway, the car drawing the leaves as they slide). LOBBY marks the street
 * floor's doors, which the lift numbers 1.
 */
public class LiftDoorBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Side> SIDE = EnumProperty.create("side", Side.class);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty LOBBY = BooleanProperty.create("lobby");
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.box(0, 0, 0, 16, 16, 4));

    public enum Part implements StringRepresentable {
        LOWER, UPPER;

        @Override
        public String getSerializedName() {
            return this == LOWER ? "lower" : "upper";
        }
    }

    public enum Side implements StringRepresentable {
        LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return this == LEFT ? "left" : "right";
        }
    }

    public LiftDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.LOWER)
                .setValue(SIDE, Side.LEFT).setValue(OPEN, false).setValue(LOBBY, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, SIDE, OPEN, LOBBY);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : SHAPES.get(state.getValue(FACING));
    }
}
