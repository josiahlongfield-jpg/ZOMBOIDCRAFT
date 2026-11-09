package com.zomboidcraft.world.reach;

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
 * The Reach world's invisible walls: one block of a Halo level's own collision. You never see it (the level's real
 * mesh is drawn over it), can't target or break it; you stand on it and bump into it. The top block of a floor keeps,
 * for each quarter (half a block each way), the height the level's surface really reaches there in eighths of a block,
 * so feet follow the level's floors, ramps and hillsides closely instead of sitting on whole blocks.
 *
 * <p>It stops rain (it's in the blocks_motion tags) and the sky's light, so a roof keeps the rain and daylight out as
 * the level's roof does.
 */
public class ReachSolid extends Block {
    /** Quarter heights in eighths of a block (0 = nothing there): north-west, north-east, south-west, south-east. */
    public static final List<IntegerProperty> QUARTERS = List.of(IntegerProperty.create("nw", 0, 8),
            IntegerProperty.create("ne", 0, 8), IntegerProperty.create("sw", 0, 8), IntegerProperty.create("se", 0, 8));
    /** The packed code of a whole block: every quarter 8 eighths tall. */
    public static final int WHOLE = 8 | 8 << 4 | 8 << 8 | 8 << 12;
    private static final VoxelShape[] SHAPES = new VoxelShape[1 << 16];

    /** One quarter's height (0-8) from a code with four nibbles, north-west lowest. */
    public static int quarter(int code, int q) {
        return Math.min(8, (code >> (4 * q)) & 15);
    }

    /** The taller of two codes, quarter by quarter. */
    public static int max(int a, int b) {
        int out = 0;
        for (int q = 0; q < 4; q++) {
            out |= Math.max(quarter(a, q), quarter(b, q)) << (4 * q);
        }
        return out;
    }

    private static VoxelShape shape(int code) {
        VoxelShape s = SHAPES[code];
        if (s == null) {
            if (code == WHOLE) {
                s = Shapes.block();
            } else {
                s = Shapes.empty();
                for (int q = 0; q < 4; q++) {
                    int h = quarter(code, q);
                    if (h > 0) {
                        int x = (q & 1) * 8, z = (q >> 1) * 8;
                        s = Shapes.or(s, Block.box(x, 0, z, x + 8, h * 2, z + 8));
                    }
                }
                s = s.optimize();
            }
            SHAPES[code] = s;
        }
        return s;
    }

    public ReachSolid(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (IntegerProperty q : QUARTERS) {
            state = state.setValue(q, 8);
        }
        registerDefaultState(state);
    }

    /** The state with these four quarter heights (a code as {@link #quarter} reads it). */
    public BlockState state(int code) {
        BlockState state = defaultBlockState();
        for (int q = 0; q < 4; q++) {
            state = state.setValue(QUARTERS.get(q), quarter(code, q));
        }
        return state;
    }

    /** The code of one of this block's states. */
    public static int code(BlockState state) {
        int code = 0;
        for (int q = 0; q < 4; q++) {
            code |= state.getValue(QUARTERS.get(q)) << (4 * q);
        }
        return code;
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
        return shape(code(state));
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
