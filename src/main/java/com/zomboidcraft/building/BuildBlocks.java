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
import org.jetbrains.annotations.Nullable;

/**
 * What the Build page puts in the world (besides the plank barricade): a metal sheet over a window or doorway, a
 * tin-can tripwire, a rain barrel, a sandbag wall, an alarm clock decoy, a planter box and a spiked log wall. No block items: they're only ever built, never placed.
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

    public static final Block ALARM_CLOCK = register("alarm_clock_decoy", AlarmClock::new, BlockBehaviour.Properties.of()
            .strength(0.3f).sound(SoundType.METAL).noOcclusion().noCollision());
    public static final Block PLANTER = register("planter_box", Planter::new, BlockBehaviour.Properties.of()
            .strength(2f, 3f).sound(SoundType.WOOD).noOcclusion().randomTicks());
    public static final Block SPIKED_WALL = register("spiked_log_wall", SpikedWall::new, BlockBehaviour.Properties.of()
            .strength(3f, 6f).sound(SoundType.WOOD).noOcclusion());

    /** The damage a zombie's blows have done to a one-piece build it beats on (sheet, spiked wall), or null for anything else. */
    public static @Nullable IntegerProperty bashDamage(BlockState state) {
        if (state.is(METAL_SHEET)) return MetalSheet.DAMAGE;
        if (state.is(SPIKED_WALL)) return SpikedWall.DAMAGE;
        return null;
    }

    /** Blows such a build stands. */
    public static int bashHits(BlockState state) {
        return Math.max(1, state.is(SPIKED_WALL) ? ZcConfig.get().building.spikedWallHits : ZcConfig.get().building.metalSheetHits);
    }

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

    // ---------------------------------------------------------------- alarm clock

    /**
     * An alarm clock set down on the ground, counting down (a scheduled tick at the time it was set to), then ringing in
     * bursts for alarmClockRingSeconds, loud enough to pull in every zombie in earshot. Rung out, it sits quiet; pick it
     * up to get the clock back (the battery's spent).
     */
    public static class AlarmClock extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        /** Bursts of ringing left; 0 while it's still counting down or once it's rung out. */
        public static final IntegerProperty RINGS = IntegerProperty.create("rings", 0, 63);
        public static final BooleanProperty RUNG = BooleanProperty.create("rung");
        /** Ticks between bursts. */
        private static final int BURST = 8;
        private static final VoxelShape SHAPE = Block.box(5, 0, 5.5, 11, 7, 10.5);

        public AlarmClock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RINGS, 0).setValue(RUNG, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, RINGS, RUNG);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        /** Sets it counting down: it rings in {@code ticks}. */
        public static void wind(ServerLevel level, BlockPos pos, int ticks) {
            level.scheduleTick(pos, ALARM_CLOCK, Math.max(1, ticks));
        }

        public static boolean ringing(BlockState state) {
            return state.getValue(RINGS) > 0;
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            if (state.getValue(RUNG)) return;
            int left = state.getValue(RINGS);
            if (left == 0) {
                left = Math.clamp(ZcConfig.get().building.alarmClockRingSeconds * 20 / BURST, 1, 63);
            }
            // the bell hammer: a quick rattle of high, slightly sour strikes
            for (int i = 0; i < 3; i++) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.6f, 1.75f + random.nextFloat() * 0.25f);
            }
            level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8f, 1.9f);
            if (left % 5 == 0) Noise.emit(level, Vec3.atCenterOf(pos), ZcConfig.get().building.alarmClockNoise);
            if (left == 1) {
                level.setBlockAndUpdate(pos, state.setValue(RINGS, 0).setValue(RUNG, true));
            } else {
                level.setBlockAndUpdate(pos, state.setValue(RINGS, left - 1));
                level.scheduleTick(pos, this, BURST);
            }
        }

        @Override
        protected boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader level, BlockPos pos) {
            return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        }

        @Override
        protected BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level, net.minecraft.world.level.ScheduledTickAccess ticks,
                                         BlockPos pos, Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            return direction == Direction.DOWN && !canSurvive(state, level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : state;
        }
    }

    // ---------------------------------------------------------------- planter box

    /** What's growing in a planter. */
    public enum Crop implements net.minecraft.util.StringRepresentable {
        NONE("none"), CARROT("carrot"), POTATO("potato"), BEET("beet");

        private final String name;

        Crop(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public static @Nullable Crop from(ItemStack seeds) {
            if (seeds.is(com.zomboidcraft.ModItems.CARROT_SEEDS)) return CARROT;
            if (seeds.is(com.zomboidcraft.ModItems.SEED_POTATOES)) return POTATO;
            if (seeds.is(com.zomboidcraft.ModItems.BEET_SEEDS)) return BEET;
            return null;
        }

        net.minecraft.world.item.Item seeds() {
            return switch (this) {
                case CARROT -> com.zomboidcraft.ModItems.CARROT_SEEDS;
                case POTATO -> com.zomboidcraft.ModItems.SEED_POTATOES;
                default -> com.zomboidcraft.ModItems.BEET_SEEDS;
            };
        }

        net.minecraft.world.item.Item produce() {
            return switch (this) {
                case CARROT -> Items.CARROT;
                case POTATO -> Items.POTATO;
                default -> Items.BEETROOT;
            };
        }

        String label() {
            return switch (this) {
                case CARROT -> "carrots";
                case POTATO -> "potatoes";
                case BEET -> "beets";
                default -> "nothing";
            };
        }
    }

    /**
     * A raised bed of planks full of earth. Plant a seed packet in it, keep it wet (rain, or a bottle of water poured on)
     * and it grows from shoots to ripe over planterGrowDays; dry, it just waits. Ripe, use it to pull the crop, with a
     * fair chance of seed to plant again.
     */
    public static class Planter extends Block {
        public static final EnumProperty<Crop> CROP = EnumProperty.create("crop", Crop.class);
        public static final IntegerProperty AGE = IntegerProperty.create("age", 0, 7);
        public static final BooleanProperty WET = BooleanProperty.create("wet");
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);
        /** A block's random ticks in a day at the default tick speed: 24000 * 3 / 4096. */
        private static final double TICKS_A_DAY = 24000 * 3 / 4096.0;

        public Planter(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(CROP, Crop.NONE).setValue(AGE, 0).setValue(WET, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(CROP, AGE, WET);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            ZcConfig.Building config = ZcConfig.get().building;
            BlockState next = state;
            boolean rain = level.isRainingAt(pos.above());
            if (rain) {
                next = next.setValue(WET, true);
            }
            if (next.getValue(WET) && next.getValue(CROP) != Crop.NONE && next.getValue(AGE) < 7
                    && random.nextDouble() < 7 / (Math.max(0.05, config.planterGrowDays) * TICKS_A_DAY)) {
                next = next.setValue(AGE, next.getValue(AGE) + 1);
            }
            if (!rain && next.getValue(WET) && random.nextDouble() < 1 / (Math.max(0.05, config.planterWaterDays) * TICKS_A_DAY)) {
                next = next.setValue(WET, false);
            }
            if (next != state) level.setBlockAndUpdate(pos, next);
        }

        @Override
        protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
            Crop crop = Crop.from(stack);
            if (crop != null && state.getValue(CROP) == Crop.NONE) {
                if (!level.isClientSide()) {
                    stack.consume(1, player);
                    level.setBlockAndUpdate(pos, state.setValue(CROP, crop).setValue(AGE, 0));
                    level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1f, 1f);
                    tell(player, "Planted " + crop.label() + "." + (state.getValue(WET) ? "" : " Keep it watered."));
                }
                return InteractionResult.SUCCESS;
            }
            boolean water = stack.is(Items.POTION) && stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS) != null
                    && stack.get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(Potions.WATER);
            if (water && !state.getValue(WET)) {
                if (!level.isClientSide()) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    level.setBlockAndUpdate(pos, state.setValue(WET, true));
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1f, 1f);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            Crop crop = state.getValue(CROP);
            if (crop == Crop.NONE) {
                if (!level.isClientSide()) tell(player, "Plant a seed packet in it" + (state.getValue(WET) ? "." : ", and water it."));
                return InteractionResult.SUCCESS;
            }
            if (state.getValue(AGE) < 7) {
                if (!level.isClientSide()) {
                    tell(player, "The " + crop.label() + " aren't ready" + (state.getValue(WET) ? " yet." : ", and the soil's dry."));
                }
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel server) {
                RandomSource random = server.getRandom();
                int n = (crop == Crop.BEET ? 2 : 3) + random.nextInt(3);
                Block.popResource(server, pos.above(), new ItemStack(crop.produce(), n));
                if (random.nextFloat() < 0.6f) Block.popResource(server, pos.above(), new ItemStack(crop.seeds()));
                server.setBlockAndUpdate(pos, state.setValue(CROP, Crop.NONE).setValue(AGE, 0));
                server.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        }

        private static void tell(Player player, String text) {
            if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                sp.sendOverlayMessage(net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
    }

    // ---------------------------------------------------------------- spiked log wall

    /**
     * Logs sharpened and driven in side by side, a head taller than you can climb. A zombie pushing on it (its body
     * comes into the block, as with a cactus) or beating at it tears itself on the points.
     */
    public static class SpikedWall extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 7);
        private static final VoxelShape ACROSS_X = Block.box(0, 0, 4, 16, 24, 12);
        private static final VoxelShape ACROSS_Z = Block.box(4, 0, 0, 12, 24, 16);
        private static final VoxelShape OUTLINE_X = Block.box(0, 0, 4.5, 16, 28, 11.5);
        private static final VoxelShape OUTLINE_Z = Block.box(4.5, 0, 0, 11.5, 28, 16);

        public SpikedWall(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DAMAGE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, DAMAGE);
        }

        private static boolean acrossX(BlockState state) {
            return state.getValue(FACING).getAxis() == Direction.Axis.Z;
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return acrossX(state) ? OUTLINE_X : OUTLINE_Z;
        }

        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return acrossX(state) ? ACROSS_X : ACROSS_Z;
        }

        @Override
        protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects, boolean precise) {
            if (level instanceof ServerLevel server && entity instanceof Zombie zombie) {
                spike(server, zombie);
            }
        }

        /** The points go in. */
        public static void spike(ServerLevel level, LivingEntity zombie) {
            float damage = ZcConfig.get().building.spikeDamage;
            if (damage > 0) zombie.hurtServer(level, level.damageSources().cactus(), damage);
        }
    }

    private BuildBlocks() {
    }
}
