package com.zomboidcraft.decor;

import com.zomboidcraft.power.PowerGrid;
import com.zomboidcraft.zombie.Noise;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A decor piece set down ({@link Decor}): its loot item's model, turned the way it went down, showing its LOOK. ON is
 * working: switched on from the G menu (or, for a plain light, simply powered), and for a powered piece only while
 * {@link PowerGrid#hasPower} says so, so a cut or a dry generator puts it out. While a loud piece is on, the dead
 * hear it. It can't be mined: small pieces come up by hand and big ones with the moving rules (building/Moving).
 */
public class DecorBlock extends Block {
    public static final int LOOKS = 16;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ON = BooleanProperty.create("on");
    public static final IntegerProperty LOOK = IntegerProperty.create("look", 0, LOOKS - 1);
    private static final VoxelShape SMALL = Block.box(3, 0, 3, 13, 10, 13), BIG = Block.box(0, 0, 0, 16, 16, 16);

    public final Decor.Spec spec;

    public DecorBlock(Properties properties, Decor.Spec spec) {
        super(properties.randomTicks());
        this.spec = spec;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false).setValue(LOOK, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, LOOK);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(LOOK, look(context.getItemInHand()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return spec.big() ? BIG : SMALL;
    }

    /** The look an item carries (CUSTOM_DATA "look"), fitted to what the block can show. */
    public int look(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        int look = data == null ? 0 : data.copyTag().getIntOr("look", 0);
        return Math.floorMod(look, spec.maxLook() + 1);
    }

    /** The item this block goes back to, with its look. */
    public ItemStack stack(BlockState state) {
        ItemStack out = new ItemStack(asItem());
        int look = state.getValue(LOOK);
        if (look > 0) {
            CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> tag.putInt("look", look));
        }
        return out;
    }

    /** Can it work here now? Unpowered pieces always can. */
    public boolean powered(ServerLevel level, BlockPos pos) {
        return !spec.power() || PowerGrid.hasPower(level, pos);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean on = state.getValue(ON);
        boolean can = powered(level, pos);
        if (on && !can) {
            level.setBlock(pos, state.setValue(ON, false), Block.UPDATE_CLIENTS);
        } else if (!on && can && spec.power() && !spec.toggle() && spec.lightSource()) {
            level.setBlock(pos, state.setValue(ON, true), Block.UPDATE_CLIENTS);  // a plain light: on when there's power
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ON) || spec.noise() <= 0) {
            return;
        }
        if (!powered(level, pos)) {
            level.setBlock(pos, state.setValue(ON, false), Block.UPDATE_CLIENTS);
            return;
        }
        Noise.emit(level, Vec3.atCenterOf(pos), spec.noise());
        level.scheduleTick(pos, this, 100);
    }

    /** Turns it on or off; false if it can't come on (no power). */
    boolean setOn(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        if (on && !powered(level, pos)) {
            return false;
        }
        level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_CLIENTS);
        if (on && spec.noise() > 0) {
            level.scheduleTick(pos, this, 20);
        }
        return true;
    }
}
