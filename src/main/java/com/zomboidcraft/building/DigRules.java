package com.zomboidcraft.building;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.blocks.CityBlock;
import com.zomboidcraft.zombie.Noise;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Why you can't just dig under everything. Natural ground only digs so deep below where the land first lay; built
 * places (every city block) don't count, so basements and bunkers stay open. The town's own walls and floors come down
 * only with the right tool and enough skill: wood with a hammer, crowbar or axe and Carpentry, brick and concrete with
 * a sledgehammer and Masonry, steel with a propane torch and Metalworking. Knocking one through is loud, tiring, wears
 * the tool and leaves planks, rubble or scrap rather than the block. Furniture still comes away whole by hand, or
 * strips down into the same materials with the tool for its material, which is how the skills start. Placing is free.
 */
public final class DigRules {
    public enum Tier {
        WOOD(Skill.CARPENTRY, "Wood"), MASONRY(Skill.MASONRY, "Brick and concrete"), METAL(Skill.METALWORKING, "Steel");

        public final Skill skill;
        final String what;

        Tier(Skill skill, String what) {
            this.skill = skill;
            this.what = what;
        }

        public int level() {
            ZcConfig.Digging d = ZcConfig.get().digging;
            return switch (this) {
                case WOOD -> d.carpentryLevel;
                case MASONRY -> d.masonryLevel;
                case METAL -> d.metalworkingLevel;
            };
        }

        public String tools() {
            return switch (this) {
                case WOOD -> "a hammer, crowbar or axe";
                case MASONRY -> "a sledgehammer";
                case METAL -> "a propane torch";
            };
        }

        double noise(ZcConfig.Digging d) {
            return switch (this) {
                case WOOD -> d.woodNoise;
                case MASONRY -> d.masonryNoise;
                case METAL -> d.metalNoise;
            };
        }

        double endurance(ZcConfig.Digging d) {
            return switch (this) {
                case WOOD -> d.woodEndurance;
                case MASONRY -> d.masonryEndurance;
                case METAL -> d.metalEndurance;
            };
        }

        double seconds(ZcConfig.Digging d) {
            return switch (this) {
                case WOOD -> d.woodSeconds;
                case MASONRY -> d.masonrySeconds;
                case METAL -> d.metalSeconds;
            };
        }
    }

    /**
     * The kind of wall, which sets the level it takes: a wooden wall or a brick partition between rooms comes down
     * early, brick facing the outside later, poured concrete and heavy steel (hulls, bulkheads, vaults) last.
     */
    public enum Wall {
        WOOD(Tier.WOOD, "Wooden wall", 1.0), PARTITION(Tier.MASONRY, "Inside brick and plaster", 1.0),
        OUTSIDE(Tier.MASONRY, "Outside brickwork", 1.3), CONCRETE(Tier.MASONRY, "Poured concrete", 1.6),
        STEEL(Tier.METAL, "Steel", 1.0), HEAVY_STEEL(Tier.METAL, "Heavy steel", 1.6);

        public final Tier tier;
        public final String what;
        final double slower;

        Wall(Tier tier, String what, double slower) {
            this.tier = tier;
            this.what = what;
            this.slower = slower;
        }

        public int level() {
            ZcConfig.Digging d = ZcConfig.get().digging;
            return switch (this) {
                case WOOD, PARTITION, STEEL -> tier.level();
                case OUTSIDE -> d.outsideMasonryLevel;
                case CONCRETE -> d.concreteLevel;
                case HEAVY_STEEL -> d.heavySteelLevel;
            };
        }
    }

    /** Which kind of wall this is, or null when it isn't one. */
    @Nullable
    public static Wall wall(BlockState state, BlockGetter level, BlockPos pos) {
        Tier tier = wallTier(state, level, pos);
        if (tier == null) {
            return null;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return switch (tier) {
            case WOOD -> Wall.WOOD;
            case METAL -> path.matches(".*(hull|bulkhead|vault|blast|bunk_plate|armou?r|navy_).*") ? Wall.HEAVY_STEEL : Wall.STEEL;
            case MASONRY -> path.matches(".*(concrete|cement|bunk_|park_|cellar|pillar|column|slab|bunker|silo|dam_|dm_).*") ? Wall.CONCRETE
                    : outside(level, pos) ? Wall.OUTSIDE : Wall.PARTITION;
        };
    }

    /** A wall with open sky beside it on one side: it faces the outside, so it holds the building up and keeps the weather out. */
    private static boolean outside(BlockGetter level, BlockPos pos) {
        if (!(level instanceof net.minecraft.world.level.LevelReader reader)) {
            return false;
        }
        for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(d);
            if (reader.getBlockState(next).isAir() && reader.canSeeSky(next)) {
                return true;
            }
        }
        return false;
    }

    /** The last ground column the server checked for you, so your own client stops cracking a block it won't let go. */
    public record Floor(int x, int z, int y) {
        public static final StreamCodec<io.netty.buffer.ByteBuf, Floor> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Floor::x, ByteBufCodecs.VAR_INT, Floor::z, ByteBufCodecs.VAR_INT, Floor::y, Floor::new);
    }

    public static final AttachmentType<Floor> FLOOR = AttachmentRegistry.create(ZomboidCraft.id("dig_floor"),
            builder -> builder.syncWith(Floor.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    private static final java.util.Map<Long, Integer> GROUND = new java.util.concurrent.ConcurrentHashMap<>();

    public static void register() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player instanceof ServerPlayer server && active(player)) {
                explain(server, (ServerLevel) level, pos, level.getBlockState(pos));
            }
            return InteractionResult.PASS;
        });
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer server && active(player) && knockDown(server, (ServerLevel) level, pos, state)));
        MoveNet.register();
        unlocks();
    }

    /** The level-up card's lines for the building skills: each wall a level opens, and the levels where nothing breaks. */
    private static void unlocks() {
        for (Wall w : Wall.values()) {
            if (w.level() > 0) com.zomboidcraft.skills.SkillUnlocks.add(w.tier.skill, w.level(), "Knock through " + w.what.toLowerCase(java.util.Locale.ROOT));
        }
        com.zomboidcraft.skills.SkillUnlocks.add(Skill.CARPENTRY, 10, "Furniture never breaks as you pick it up");
        com.zomboidcraft.skills.SkillUnlocks.add(Skill.ELECTRICAL, 10, "Appliances and lights never break as you unhook them");
        com.zomboidcraft.skills.SkillUnlocks.add(Skill.PLUMBING, 10, "Sinks, toilets and tubs never break as you take them out");
    }

    private static boolean active(Player player) {
        return ZcConfig.get().digging.rules && !player.hasInfiniteMaterials() && !player.isSpectator();
    }

    /** A town wall, floor or roof: a full city block that isn't a fitting with things in it. */
    @Nullable
    public static Tier wallTier(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        if (!(block instanceof CityBlock) || block instanceof EntityBlock || state.getDestroySpeed(level, pos) < 1f
                || !state.isCollisionShapeFullBlock(level, pos)) {
            return null;
        }
        return tierOf(state);
    }

    /** A city block's material, from its sound: the city's tables give every block the sound of what it's made of. */
    @Nullable
    private static Tier tierOf(BlockState state) {
        SoundType sound = state.getSoundType();
        if (sound == SoundType.WOOD) return Tier.WOOD;
        if (sound == SoundType.STONE) return Tier.MASONRY;
        if (sound == SoundType.METAL) return Tier.METAL;
        return null;
    }

    /** Furniture and fixtures you can strip down with the tool for their material. */
    @Nullable
    public static Tier propTier(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        if (!(block instanceof CityBlock) || block instanceof EntityBlock || state.getDestroySpeed(level, pos) <= 0f) {
            return null;
        }
        return tierOf(state);
    }

    /**
     * Whether a swing at a piece of furniture takes it apart for materials (Zomboid's Scrap) rather than lifting it:
     * crouching with a tool that strips its material. Standing, the same hammer lifts it whole.
     */
    private static boolean stripping(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        Tier strip = propTier(state, level, pos);
        return player.isShiftKeyDown() && strip != null && fits(strip, player.getMainHandItem());
    }

    public static boolean fits(Tier tier, ItemStack tool) {
        return switch (tier) {
            case WOOD -> tool.is(ModItems.HAMMER) || tool.is(ModItems.CROWBAR) || tool.is(ModItems.FIRE_AXE)
                    || tool.is(ModItems.SLEDGEHAMMER) || tool.is(ItemTags.AXES);
            case MASONRY -> tool.is(ModItems.SLEDGEHAMMER) || tool.is(ItemTags.PICKAXES);
            case METAL -> tool.is(ModItems.PROPANE_TORCH);
        };
    }

    /**
     * How much of the block one tick of mining breaks, or NaN to leave it to the game. Zero when it won't come at all:
     * a wall without the tool or the skill, or natural ground under the floor.
     */
    public static float progress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!active(player) || state.isAir()) {
            return Float.NaN;
        }
        if (belowFloor(state, player, level, pos)) {
            return 0f;
        }
        Wall wall = wall(state, level, pos);
        if (wall == null) {
            Moving.Piece piece = Moving.piece(state, level, pos);
            if (piece == null || stripping(state, player, level, pos)) {
                return Float.NaN;  // stripping it down for materials goes at the game's pace
            }
            return Moving.progress(piece, player, level, pos);
        }
        Tier tier = wall.tier;
        int level0 = Skills.level(player, tier.skill), need = wall.level();
        if (!fits(tier, player.getMainHandItem()) || level0 < need) {
            return 0f;
        }
        double ticks = tier.seconds(ZcConfig.get().digging) * wall.slower * 20 / (1 + 0.12 * (level0 - need));
        return (float) (1 / Math.max(2, ticks));
    }

    private static boolean belowFloor(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        if (!natural(state)) {
            return false;
        }
        if (level instanceof ServerLevel server) {
            return server.dimension() == Level.OVERWORLD && pos.getY() <= ground(server, pos.getX(), pos.getZ()) - ZcConfig.get().digging.floorDepth;
        }
        // the client goes by the column the server last checked for this player
        Floor floor = player.getAttached(FLOOR);
        return floor != null && floor.x() == pos.getX() && floor.z() == pos.getZ() && pos.getY() <= floor.y();
    }

    /** Ground the world put there: anything that isn't a mod block (every building block is one). */
    static boolean natural(BlockState state) {
        return state.getFluidState().isEmpty()
                && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft");
    }

    /** The top block of natural ground here, as the world generator laid it. */
    static int ground(ServerLevel level, int x, int z) {
        if (GROUND.size() > 65536) {
            GROUND.clear();
        }
        return GROUND.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> {
            var source = level.getChunkSource();
            return source.getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, source.randomState()) - 1;
        });
    }

    /** On the first swing at a block that won't come, says why. */
    private static void explain(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        if (natural(state) && level.dimension() == Level.OVERWORLD) {
            int floor = ground(level, pos.getX(), pos.getZ()) - ZcConfig.get().digging.floorDepth;
            player.setAttached(FLOOR, new Floor(pos.getX(), pos.getZ(), floor));
            if (pos.getY() <= floor) {
                say(player, "The ground's packed too hard to dig any deeper here.");
            }
            return;
        }
        Wall wall = wall(state, level, pos);
        if (wall == null) {
            Moving.Piece piece = Moving.piece(state, level, pos);
            if (piece != null && !stripping(state, player, level, pos)) {
                String no = Moving.refusal(piece, player, level, pos);
                say(player, no != null ? no : Moving.risk(piece, player));
            }
            return;
        }
        Tier tier = wall.tier;
        int have = Skills.level(player, tier.skill), need = wall.level();
        if (!fits(tier, player.getMainHandItem())) {
            say(player, wall.what + " needs " + tier.tools() + " to knock through" + (need > 0 ? ", and " + tier.skill.displayName() + " " + need + "." : "."));
        } else if (have < need) {
            say(player, "You need " + tier.skill.displayName() + " " + need + " to knock through this. You're at " + have
                    + ". Strip furniture down with the same tool to learn.");
        }
    }

    private static void say(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    /**
     * Breaks a wall the hard way, or strips a piece of furniture with the tool for its material. Returns false to let
     * the game break the block as usual.
     */
    private static boolean knockDown(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state) {
        ItemStack tool = player.getMainHandItem();
        Tier tier = wallTier(state, level, pos);
        boolean wall = tier != null;
        if (!wall) {
            tier = propTier(state, level, pos);
            Moving.Piece piece = Moving.piece(state, level, pos);
            if (tier == null || !fits(tier, tool) || piece != null && !player.isShiftKeyDown()) {
                if (piece == null || Moving.refusal(piece, player, level, pos) != null) {
                    return piece != null;  // not liftable yet: it stays put
                }
                Moving.pickUp(player, level, pos, state, piece);
                return true;
            }
        }
        strip(player, level, pos, tier, wall);
        return true;
    }

    /** Takes a wall down, or a piece of furniture apart for what it's made of (Zomboid's Scrap), with the tool in hand. */
    static void strip(ServerPlayer player, ServerLevel level, BlockPos pos, Tier tier, boolean wall) {
        ItemStack tool = player.getMainHandItem();
        ZcConfig.Digging d = ZcConfig.get().digging;
        double share = wall ? 1 : 0.5;
        level.destroyBlock(pos, false, player);
        RandomSource random = player.getRandom();
        switch (tier) {
            case WOOD -> {
                drop(level, pos, new ItemStack(Items.OAK_PLANKS, wall && random.nextBoolean() ? 2 : 1));
                if (random.nextFloat() < (wall ? 0.3f : 0.4f)) drop(level, pos, new ItemStack(ModItems.NAILS, 1 + random.nextInt(2)));
            }
            case MASONRY -> drop(level, pos, new ItemStack(ModItems.RUBBLE, wall ? 1 + random.nextInt(2) : 1));
            case METAL -> drop(level, pos, new ItemStack(ModItems.SCRAP_METAL, random.nextFloat() < (wall ? 0.4f : 0.3f) ? 2 : 1));
        }
        Noise.emit(level, Vec3.atCenterOf(pos), tier.noise(d) * share);
        Stamina.use(player, tier.endurance(d) * share);
        com.zomboidcraft.combat.Wear.use(tool, player, InteractionHand.MAIN_HAND, wall ? 2 : 1);
        Skills.addXp(player, tier.skill, wall ? 12 : 6);
    }

    private static void drop(ServerLevel level, BlockPos pos, ItemStack stack) {
        Block.popResource(level, pos, stack);
    }

    private DigRules() {
    }
}
