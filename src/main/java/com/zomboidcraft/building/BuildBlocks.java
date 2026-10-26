package com.zomboidcraft.building;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.Noise;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the Build page puts in the world (besides the plank barricade): a metal sheet over a window or doorway, a
 * tin-can tripwire, a rain barrel and a sandbag wall. No block items: they're only ever built, never placed.
 */
public final class BuildBlocks {
    public static final Block METAL_SHEET = register("metal_sheet_cover", MetalSheet::new, BlockBehaviour.Properties.of()
            .strength(5f, 6f).sound(SoundType.METAL).noOcclusion());
    public static final Block TRIPWIRE = register("can_tripwire", Tripwire::new, BlockBehaviour.Properties.of()
            .strength(0.3f).sound(SoundType.CHAIN).noCollision().noOcclusion());
    public static final Block RAIN_BARREL = register("rain_barrel", RainBarrel::new, BlockBehaviour.Properties.of()
            .strength(1.5f).sound(SoundType.WOOD).noOcclusion().randomTicks());
    public static final Block SANDBAGS = register("sandbag_wall", Sandbags::new, BlockBehaviour.Properties.of()
            .strength(2f, 8f).sound(SoundType.WOOL).noOcclusion());

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    public static void register() {
        // loading the class registers the blocks
    }

    /** Thin panels hung on one side of a block, like the plank barricade. */
    static Map<Direction, VoxelShape> panel(double thick) {
        return Map.of(
                Direction.NORTH, Block.box(0, 0, 0, 16, 16, thick),
                Direction.SOUTH, Block.box(0, 0, 16 - thick, 16, 16, 16),
                Direction.WEST, Block.box(0, 0, 0, thick, 16, 16),
                Direction.EAST, Block.box(16 - thick, 0, 0, 16, 16, 16));
    }

    // ---------------------------------------------------------------- metal sheet

    /** A steel sheet tacked over a window or doorway: zombies need many more blows than for planks. */
    public static class MetalSheet extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        /** Blows taken, in eighths of what it can stand. */
        public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 7);
        private static final Map<Direction, VoxelShape> SHAPES = panel(2);

        public MetalSheet(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DAMAGE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, DAMAGE);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPES.get(state.getValue(FACING));
        }
    }

    // ---------------------------------------------------------------- tripwire

    /** String strung across a gap with tin cans on it: anything walking through sets them rattling. */
    public static class Tripwire extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final BooleanProperty RATTLING = BooleanProperty.create("rattling");
        private static final VoxelShape ALONG_X = Block.box(0, 2, 7, 16, 6, 9);
        private static final VoxelShape ALONG_Z = Block.box(7, 2, 0, 9, 6, 16);

        public Tripwire(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(RATTLING, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS, RATTLING);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
            if (!(level instanceof ServerLevel server) || state.getValue(RATTLING) || !(entity instanceof LivingEntity)) {
                return;
            }
            server.setBlockAndUpdate(pos, state.setValue(RATTLING, true));
            server.scheduleTick(pos, this, 30);
            RandomSource random = server.getRandom();
            for (int i = 0; i < 3; i++) {
                server.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 1.2f, 1.3f + random.nextFloat() * 0.5f);
            }
            server.playSound(null, pos, SoundEvents.LANTERN_HIT, SoundSource.BLOCKS, 1.0f, 1.6f + random.nextFloat() * 0.3f);
            Noise.emit(server, Vec3.atCenterOf(pos), ZcConfig.get().building.tripwireNoise);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(RATTLING)) level.setBlockAndUpdate(pos, state.setValue(RATTLING, false));
        }
    }

    // ---------------------------------------------------------------- rain barrel

    /** A drum under a tarp funnel that fills while it rains. Fill a bottle from it. */
    public static class RainBarrel extends Block {
        public static final int MAX = 8;
        public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, MAX);
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

        public RainBarrel(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(LEVEL, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(LEVEL);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int now = state.getValue(LEVEL);
            if (now < MAX && level.isRainingAt(pos.above())
                    && random.nextDouble() < ZcConfig.get().building.rainBarrelFill * (level.isThundering() ? 2 : 1)) {
                level.setBlockAndUpdate(pos, state.setValue(LEVEL, now + 1));
            }
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            if (!stack.is(Items.GLASS_BOTTLE)) {
                return InteractionResult.TRY_WITH_EMPTY_HAND;
            }
            int now = state.getValue(LEVEL);
            if (now <= 0) {
                return InteractionResult.FAIL;
            }
            if (!level.isClientSide()) {
                ItemStack water = PotionContents.createItemStack(Items.POTION, Potions.WATER);
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, water));
                level.setBlockAndUpdate(pos, state.setValue(LEVEL, now - 1));
                level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }
    }

    // ---------------------------------------------------------------- sandbags

    /** A waist-high wall of filled sandbags: crawlers can't get over, walkers clamber and slow down on top. */
    public static class Sandbags extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        private static final VoxelShape ACROSS_X = Block.box(0, 0, 3, 16, 12, 13);
        private static final VoxelShape ACROSS_Z = Block.box(3, 0, 0, 13, 12, 16);

        public Sandbags(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ACROSS_X : ACROSS_Z;
        }

        @Override
        public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
            if (entity instanceof Zombie) {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(0.5, 1, 0.5));
            }
            super.stepOn(level, pos, state, entity);
        }
    }

    private BuildBlocks() {
    }
}
