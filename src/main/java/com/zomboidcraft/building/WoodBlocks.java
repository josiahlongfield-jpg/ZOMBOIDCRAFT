package com.zomboidcraft.building;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Wood building, base customisation wave 2 (Carpentry 3 and 4): a wall goes up as a frame of studs first, quick but
 * stopping nothing, then gets boarded into a wall zombies have to beat down; window and door frames sit in the run of
 * the wall, and a plank door hangs in a door frame. Floors stand on posts (up to three high), on walls or on solid
 * ground, and span a few boards out from what holds them; take that away and the floor sags, creaks for about a
 * minute, then comes down (the beam rule). Only ever built from the Build page, so no block items.
 */
public final class WoodBlocks {
    public static final Block WALL_FRAME = register("wood_wall_frame", Frame::new, wood(1.5f));
    public static final Block WALL = register("wood_wall", Wall::new,
            BlockBehaviour.Properties.of().strength(3f, 3f).sound(SoundType.WOOD));
    public static final Block WINDOW_FRAME = register("wood_window_frame", Window::new, wood(2f));
    public static final Block DOOR_FRAME = register("wood_door_frame", DoorFrame::new, wood(2f));
    public static final Block DOOR = register("wood_plank_door", p -> new LockDoor(BlockSetType.OAK, p), wood(3f));
    public static final Block POST = register("wood_post", Post::new, wood(2f));
    public static final Block FLOOR = register("wood_floor", Floor::new, wood(2f));
    // wave 2b: getting up, and keeping the weather and the dead out
    public static final Block STAIRS = register("wood_stairs", p -> new Stairs(Blocks.OAK_PLANKS.defaultBlockState(), p), wood(2f));
    public static final Block LADDER = register("wood_ladder", Ladder::new, wood(0.6f));
    public static final Block ROOF = register("wood_roof", p -> new Stairs(Blocks.OAK_PLANKS.defaultBlockState(), p), wood(2f));
    public static final Block SHUTTERS = register("wood_window_shutters", Shutters::new, wood(2f));
    // wave 2c: holding the line
    public static final Block REINFORCED = register("wood_wall_reinforced", Wall::new, wood(4f));
    public static final Block FENCE = register("wood_fence", net.minecraft.world.level.block.FenceBlock::new, wood(2f));
    public static final Block GATE = register("wood_gate", p -> new net.minecraft.world.level.block.FenceGateBlock(
            net.minecraft.world.level.block.state.properties.WoodType.OAK, p), wood(2f));
    public static final Block HIDDEN_DOOR = register("wood_hidden_door", HiddenDoor::new, wood(3f));

    private static BlockBehaviour.Properties wood(float strength) {
        return BlockBehaviour.Properties.of().strength(strength, 3f).sound(SoundType.WOOD).noOcclusion();
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    public static void register() {
        // a swing at something that holds a floor up warns first (the beam rule): it'll still come out
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player instanceof ServerPlayer server && !level.isClientSide() && holdsUp(level.getBlockState(pos))
                    && level.getBlockState(pos.above()).is(FLOOR)) {
                server.sendOverlayMessage(Component.literal("This holds up the floor above. Put a post under it first, or the floor comes down.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Sets a built piece down: a door frame's or door's upper half with it, a floor checked for what holds it up, a
     * crate emptied of the stock its city twin would roll. Called once the build's materials are used up.
     */
    static void place(ServerLevel level, BlockPos pos, BlockState state, int carpentry) {
        if (state.is(WALL) || state.is(REINFORCED)) state = state.setValue(Wall.STRENGTH, Math.min(10, carpentry));
        if (state.is(HIDDEN_DOOR)) {
            level.setBlock(pos, state.setValue(HiddenDoor.HALF, DoubleBlockHalf.LOWER), 18);
            level.setBlock(pos.above(), state.setValue(HiddenDoor.HALF, DoubleBlockHalf.UPPER), 18);
            return;
        }
        if (state.is(DOOR)) {
            // into the frame: both halves at once, without the halves seeing each other half-built
            level.setBlock(pos, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 18);
            level.setBlock(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 18);
            return;
        }
        if (state.is(DOOR_FRAME)) {
            level.setBlock(pos, state.setValue(DoorFrame.HALF, DoubleBlockHalf.LOWER), 18);
            level.setBlock(pos.above(), state.setValue(DoorFrame.HALF, DoubleBlockHalf.UPPER), 18);
            return;
        }
        if (state.is(STAIRS) || state.is(ROOF) || state.is(FENCE) || state.is(GATE)) state = Block.updateFromNeighbourShapes(state, level, pos);   // turns a corner to meet the run beside it
        level.setBlockAndUpdate(pos, state);
        if (state.is(FLOOR)) level.scheduleTick(pos, FLOOR, 2);
        if (level.getBlockEntity(pos) instanceof net.minecraft.world.RandomizableContainer crate) crate.setLootTable(null);
    }

    /** A finished wall: the blows it stands, more for a better carpenter. */
    public static int wallHits(BlockState state) {
        int base = ZcConfig.get().building.woodWallHits;
        double reinforced = state.is(REINFORCED) ? ZcConfig.get().building.reinforcedWallFactor : 1.0;
        return (int) Math.round(base * reinforced * (1 + 0.1 * state.getValue(Wall.STRENGTH)));
    }

    // ---------------------------------------------------------------- what holds what up

    /** Something a wall, post or floor can stand on: solid ground, or one of these. */
    public static boolean base(BlockGetter level, BlockPos below) {
        BlockState state = level.getBlockState(below);
        return holdsUp(state) || state.is(FLOOR) || state.isFaceSturdy(level, below, Direction.UP);
    }

    /** The pieces that carry a floor laid on top of them. */
    static boolean holdsUp(BlockState state) {
        return state.is(WALL_FRAME) || state.is(WALL) || state.is(REINFORCED) || state.is(HIDDEN_DOOR) || state.is(POST) || state.is(WINDOW_FRAME) || state.is(SHUTTERS)
                || state.is(DOOR_FRAME) && state.getValue(DoorFrame.HALF) == DoubleBlockHalf.UPPER;
    }

    /**
     * Whether a roof section at {@code pos} has something to sit on: the ground, a wall, post or floor under it, or a
     * wall, floor or more roof beside it (a roof runs out from its wall plate like rafters).
     */
    public static boolean roofHeld(BlockGetter level, BlockPos pos) {
        if (base(level, pos.below()) || level.getBlockState(pos.below()).is(ROOF)) return true;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.below().relative(d)).is(ROOF)) return true;     // the next course up the slope
            BlockState beside = level.getBlockState(pos.relative(d));
            if (beside.is(ROOF) || beside.is(FLOOR) || holdsUp(beside) || beside.isFaceSturdy(level, pos.relative(d), d.getOpposite())) return true;
        }
        return false;
    }

    /** How many posts already stand in a column under here. */
    public static int postsBelow(BlockGetter level, BlockPos pos) {
        int n = 0;
        while (n < 8 && level.getBlockState(pos.below(n + 1)).is(POST)) n++;
        return n;
    }

    /**
     * Whether a floor at {@code pos} is held up: something under it, or a solid wall beside it, or another floor that is,
     * no more than floorSpan boards away across the floor. {@code ignoring} is left out (a floor that's coming down).
     */
    public static boolean supported(BlockGetter level, BlockPos pos, @Nullable BlockPos ignoring) {
        int span = Math.max(0, ZcConfig.get().building.floorSpan);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        ArrayDeque<Integer> steps = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(pos);
        steps.add(0);
        seen.add(pos);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int step = steps.poll();
            BlockState under = level.getBlockState(at.below());
            if (!at.below().equals(ignoring) && (holdsUp(under) || under.isFaceSturdy(level, at.below(), Direction.UP))) return true;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos next = at.relative(d);
                if (next.equals(ignoring)) continue;
                BlockState beside = level.getBlockState(next);
                if (!beside.is(FLOOR) && !beside.is(WALL_FRAME) && beside.isFaceSturdy(level, next, d.getOpposite())) return true;
                if (beside.is(FLOOR) && step < span && seen.add(next.immutable())) {
                    queue.add(next.immutable());
                    steps.add(step + 1);
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- the pieces

    /** Studs on a sole and a top plate, with a brace: the first step of a wall, quick, but you can see and reach through it. */
    public static class Frame extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape ALONG_X = Block.box(0, 0, 6, 16, 16, 10);
        private static final VoxelShape ALONG_Z = Block.box(6, 0, 0, 10, 16, 16);

        public Frame(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
        }
    }

    /**
     * A frame boarded both sides: a full wall, the first real defence you build. Zombies beat at it like boards; it
     * stands woodWallHits blows, a tenth more for each level of Carpentry the builder had.
     */
    public static class Wall extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 7);
        public static final IntegerProperty STRENGTH = IntegerProperty.create("strength", 0, 10);

        public Wall(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(DAMAGE, 0).setValue(STRENGTH, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS, DAMAGE, STRENGTH);
        }
    }

    /** A window frame in the run of a wall: sill, header and jambs round an opening you can board over. */
    public static class Window extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        private static final VoxelShape ALONG_X = Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 13, 0, 16, 16, 16),
                Block.box(0, 5, 0, 2, 13, 16), Block.box(14, 5, 0, 16, 13, 16));
        private static final VoxelShape ALONG_Z = Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 13, 0, 16, 16, 16),
                Block.box(0, 5, 0, 16, 13, 2), Block.box(0, 5, 14, 16, 13, 16));

        public Window(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
        }
    }

    /** A door frame, two blocks tall: jambs, and a header on the upper half. A plank door hangs in it. */
    public static class DoorFrame extends Block {
        public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        private static final VoxelShape LOWER_X = Shapes.or(Block.box(0, 0, 0, 2.5, 16, 16), Block.box(13.5, 0, 0, 16, 16, 16));
        private static final VoxelShape LOWER_Z = Shapes.or(Block.box(0, 0, 0, 16, 16, 2.5), Block.box(0, 0, 13.5, 16, 16, 16));
        private static final VoxelShape UPPER_X = Shapes.or(LOWER_X, Block.box(0, 12, 0, 16, 16, 16));
        private static final VoxelShape UPPER_Z = Shapes.or(LOWER_Z, Block.box(0, 12, 0, 16, 16, 16));

        public DoorFrame(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.X).setValue(HALF, DoubleBlockHalf.LOWER));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(AXIS, HALF);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            boolean x = state.getValue(AXIS) == Direction.Axis.X;
            return state.getValue(HALF) == DoubleBlockHalf.LOWER ? (x ? LOWER_X : LOWER_Z) : (x ? UPPER_X : UPPER_Z);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                         Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
            if (direction == (lower ? Direction.UP : Direction.DOWN) && !neighbour.is(this) && !neighbour.is(DOOR)) {
                return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            }
            return state;
        }
    }

    /** A squared timber post. Up to three stand on each other; a floor goes on top. */
    public static class Post extends Block {
        private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

        public Post(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }
    }

    /**
     * Boards nailed over joists. Held up by what's under it or beside it (see {@link #supported}); when that goes it
     * sags, creaking, in three stages of floorSagSeconds/3 each, then falls in, and the floors it held check in turn.
     */
    public static class Floor extends Block {
        public static final IntegerProperty SAG = IntegerProperty.create("sag", 0, 3);

        public Floor(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(SAG, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(SAG);
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                         Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            // once it's sagging its own stage ticks carry it on, so a neighbour's creak doesn't hurry it
            if (state.getValue(SAG) == 0) ticks.scheduleTick(pos, this, 2);
            return state;
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            int sag = state.getValue(SAG);
            if (supported(level, pos, null)) {
                if (sag > 0) {
                    level.setBlock(pos, state.setValue(SAG, 0), Block.UPDATE_CLIENTS);
                    level.destroyBlockProgress(crack(pos), pos, -1);
                }
                return;
            }
            int stage = Math.max(20, ZcConfig.get().building.floorSagSeconds * 20 / 3);
            if (sag < 3) {
                level.setBlock(pos, state.setValue(SAG, sag + 1), Block.UPDATE_CLIENTS);
                level.destroyBlockProgress(crack(pos), pos, 3 + sag * 3);
                level.playSound(null, pos, SoundEvents.WOOD_STEP, SoundSource.BLOCKS, 1.4f, 0.5f + sag * 0.1f);
                level.scheduleTick(pos, this, stage);
                return;
            }
            level.destroyBlockProgress(crack(pos), pos, -1);
            level.destroyBlock(pos, true);
            level.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1f, 0.7f);
            Noise.emit(level, Vec3.atCenterOf(pos), 14);
        }

        private static int crack(BlockPos pos) {
            return -1000 - (int) (pos.asLong() & 0x3fffffff);
        }
    }

    /** Wooden stairs, and the shingled roof sections that share their shape. */
    public static class Stairs extends StairBlock {
        public Stairs(BlockState base, Properties properties) {
            super(base, properties);
        }
    }

    /** A plank ladder nailed up against a wall. */
    public static class Ladder extends LadderBlock {
        public Ladder(Properties properties) {
            super(properties);
        }

        /** A wall behind it, or a post: a lookout tower's ladder is nailed to its leg. */
        @Override
        protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
            return super.canSurvive(state, level, pos) || level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).is(POST);
        }
    }

    private static void say(Player player, String text) {
        if (player instanceof ServerPlayer sp) sp.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /** The lock id a padlocked door's key carries: where the door hangs. */
    public static String lockId(BlockPos lower) {
        return "door:" + lower.asLong();
    }

    /**
     * The plank door, which can take a padlock: locked, nobody opens it by hand; the key it was fitted with (use it on
     * either side) locks and unlocks it. Zombies don't care either way, they beat it in.
     */
    public static class LockDoor extends DoorBlock {
        public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

        public LockDoor(BlockSetType type, Properties properties) {
            super(type, properties);
            registerDefaultState(defaultBlockState().setValue(LOCKED, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            super.createBlockStateDefinition(builder);
            builder.add(LOCKED);
        }

        @Override
        protected InteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
            String lock = data == null ? "" : data.copyTag().getStringOr("lock", "");
            if (!lock.startsWith("door:")) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (!level.isClientSide()) {
                String no = !lock.equals(lockId(lower)) ? "Wrong key." : !hasPadlock(level, lower) ? "There's no padlock on it." : null;
                if (no != null) {
                    say(player, no);
                    return InteractionResult.SUCCESS;
                }
                boolean locked = !state.getValue(LOCKED);
                BlockState now = level.getBlockState(lower);
                level.setBlock(lower, now.setValue(LOCKED, locked).setValue(OPEN, false), Block.UPDATE_ALL);
                level.playSound(null, pos, locked ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8f, 1.6f);
                say(player, locked ? "Locked." : "Unlocked.");
            }
            return InteractionResult.SUCCESS;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (state.getValue(LOCKED)) {
                if (!level.isClientSide()) {
                    say(player, "It's padlocked.");
                    level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.6f, 1.4f);
                }
                return InteractionResult.SUCCESS;
            }
            return super.useWithoutItem(state, level, pos, player, hit);
        }
    }

    /** Whether the door at {@code lower} has had a padlock fitted (one fitting, then its key locks and unlocks it). */
    static boolean hasPadlock(Level level, BlockPos lower) {
        return level instanceof ServerLevel server && server.getAttachedOrElse(PADLOCKS, java.util.List.<Long>of()).contains(lower.asLong());
    }

    /** Doors in this level with a padlock fitted. */
    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<java.util.List<Long>> PADLOCKS =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(ZomboidCraft.id("door_padlocks"),
                    b -> b.persistent(com.mojang.serialization.Codec.LONG.listOf()).initializer(java.util.List::of));

    /** Fits a padlock to the plank door at {@code lower}, locks it, and gives the fitter its key. */
    static void padlock(ServerLevel level, BlockPos lower, ServerPlayer player) {
        java.util.List<Long> now = new java.util.ArrayList<>(level.getAttachedOrElse(PADLOCKS, java.util.List.of()));
        if (!now.contains(lower.asLong())) now.add(lower.asLong());
        level.setAttached(PADLOCKS, java.util.List.copyOf(now));
        BlockState door = level.getBlockState(lower);
        if (door.is(DOOR)) level.setBlock(lower, door.setValue(LockDoor.LOCKED, true).setValue(DoorBlock.OPEN, false), Block.UPDATE_ALL);
        var item = com.zomboidcraft.loot.items.LootItems.item("padlock_key");
        if (item == null) return;
        net.minecraft.world.item.ItemStack key = new net.minecraft.world.item.ItemStack(item);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, key, tag -> tag.putString("lock", lockId(lower)));
        key.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Door Key (" + lower.getX() + ", " + lower.getY() + ", " + lower.getZ() + ")")
                .withStyle(st -> st.withItalic(false)));
        if (!player.getInventory().add(key)) player.spawnAtLocation(level, key);
        player.sendOverlayMessage(Component.literal("Padlocked. The key's yours: don't lose it.").withStyle(ChatFormatting.GRAY));
    }

    /**
     * A door that looks like the wall it's in, two blocks tall: boards to match outside and in. Push it and it swings
     * back flat against the jamb; zombies never take it for a way in.
     */
    public static class HiddenDoor extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

        public HiddenDoor(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(OPEN, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, HALF, OPEN);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            if (!state.getValue(OPEN)) return Shapes.block();
            // swung back against the left jamb as you push through it
            return switch (state.getValue(FACING)) {
                case NORTH -> Block.box(0, 0, 0, 3, 16, 16);
                case SOUTH -> Block.box(13, 0, 0, 16, 16, 16);
                case EAST -> Block.box(0, 0, 0, 16, 16, 3);
                default -> Block.box(0, 0, 13, 16, 16, 16);
            };
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            boolean open = !state.getValue(OPEN);
            BlockPos lower = state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            for (BlockPos p : new BlockPos[] {lower, lower.above()}) {
                BlockState s = level.getBlockState(p);
                if (s.is(this)) level.setBlock(p, s.setValue(OPEN, open), Block.UPDATE_CLIENTS | 16);
            }
            level.playSound(null, pos, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.7f, 0.7f);
            return InteractionResult.SUCCESS;
        }

        @Override
        protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                         Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
            boolean lower = state.getValue(HALF) == DoubleBlockHalf.LOWER;
            if (direction == (lower ? Direction.UP : Direction.DOWN) && !neighbour.is(this)) return Blocks.AIR.defaultBlockState();
            return state;
        }
    }

    /**
     * A lookout tower in front of {@code anchor}, its near side facing back toward the builder: four posts three high,
     * a 3x3 floor on them, a rail round it but for the corner the ladder comes up at, and the ladder on that leg.
     */
    public static boolean towerFits(Level level, BlockPos anchor, Direction looking) {
        for (BlockPos p : towerBlocks(anchor, looking).keySet()) {
            if (!level.getBlockState(p).canBeReplaced()) return false;
        }
        Direction right = looking.getClockWise();
        for (int[] c : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            BlockPos leg = anchor.relative(right, c[0]).relative(looking, c[1]);
            if (!base(level, leg.below())) return false;
        }
        return true;
    }

    public static java.util.Map<BlockPos, BlockState> towerBlocks(BlockPos anchor, Direction looking) {
        java.util.Map<BlockPos, BlockState> out = new java.util.LinkedHashMap<>();
        Direction right = looking.getClockWise();
        for (int[] c : new int[][] {{0, 0}, {2, 0}, {0, 2}, {2, 2}}) {
            for (int y = 0; y < 3; y++) out.put(anchor.relative(right, c[0]).relative(looking, c[1]).above(y).immutable(), POST.defaultBlockState());
        }
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) out.put(anchor.relative(right, a).relative(looking, b).above(3).immutable(), FLOOR.defaultBlockState());
        }
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                if (a == 1 && b == 1 || a == 0 && b == 0) continue;     // the middle, and the gap the ladder comes up at
                out.put(anchor.relative(right, a).relative(looking, b).above(4).immutable(), FENCE.defaultBlockState());
            }
        }
        for (int y = 0; y < 4; y++) {
            out.put(anchor.relative(looking.getOpposite()).above(y).immutable(), LADDER.defaultBlockState().setValue(LadderBlock.FACING, looking.getOpposite()));
        }
        return out;
    }

    static void tower(ServerLevel level, BlockPos anchor, Direction looking) {
        for (var e : towerBlocks(anchor, looking).entrySet()) level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
        for (BlockPos p : towerBlocks(anchor, looking).keySet()) {
            BlockState s = level.getBlockState(p);
            level.setBlock(p, Block.updateFromNeighbourShapes(s, level, p), Block.UPDATE_ALL);
        }
    }

    /**
     * A window frame with plank shutters hung on its outside ({@code FACING} points out). Open them by hand to look out;
     * shut, they fill the opening and zombies have to beat them in, and when they give the frame is left bare.
     */
    public static class Shutters extends Block {
        public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
        public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
        public static final IntegerProperty DAMAGE = IntegerProperty.create("damage", 0, 7);

        public Shutters(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(DAMAGE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, OPEN, DAMAGE);
        }

        /** The bare window frame it stands in, the way it faces. */
        public static BlockState frame(BlockState state) {
            Direction.Axis axis = state.getValue(FACING).getClockWise().getAxis();
            return WINDOW_FRAME.defaultBlockState().setValue(Window.AXIS, axis);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return state.getValue(OPEN) ? WINDOW_FRAME.defaultBlockState().setValue(Window.AXIS, state.getValue(FACING).getClockWise().getAxis())
                    .getShape(level, pos, context) : Shapes.block();
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            boolean open = !state.getValue(OPEN);
            level.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
            level.playSound(null, pos, open ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1f, 0.85f);
            return InteractionResult.SUCCESS;
        }
    }

    private WoodBlocks() {
    }
}
