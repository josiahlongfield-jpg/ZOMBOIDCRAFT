package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A garage reloading bench (tools/weapons/reloading.py): a red press bolted to its front edge, the powder measure,
 * scale and loading block beside it, a pegboard up the back. Used with the interact key's menu (Reloading); FACING
 * is the side the press and whoever works it are on.
 */
public class ReloadingBenchBlock extends Block {
    private static final ResourceKey<Block> KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("reloading_bench"));
    public static final Block BENCH = Registry.register(BuiltInRegistries.BLOCK, KEY, new ReloadingBenchBlock(
            BlockBehaviour.Properties.of().strength(2.5f, 6f).sound(SoundType.WOOD).noOcclusion().setId(KEY)));

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // the top and legs, plus the pegboard along the back
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.NORTH, Shapes.or(Block.box(0, 0, 0.5, 16, 15, 15.5), Block.box(0.5, 15, 14.8, 15.5, 16, 15.6)),
            Direction.SOUTH, Shapes.or(Block.box(0, 0, 0.5, 16, 15, 15.5), Block.box(0.5, 15, 0.4, 15.5, 16, 1.2)),
            Direction.WEST, Shapes.or(Block.box(0.5, 0, 0, 15.5, 15, 16), Block.box(14.8, 15, 0.5, 15.6, 16, 15.5)),
            Direction.EAST, Shapes.or(Block.box(0.5, 0, 0, 15.5, 15, 16), Block.box(0.4, 15, 0.5, 1.2, 16, 15.5)));

    public ReloadingBenchBlock(Properties properties) {
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
