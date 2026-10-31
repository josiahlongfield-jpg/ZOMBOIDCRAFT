package com.zomboidcraft.building;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.Weights;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.blocks.CityBlock;
import com.zomboidcraft.zombie.Noise;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Picking furniture up and carrying it off, Zomboid style. Every piece of the town's furniture and fittings goes into
 * your inventory whole and weighs what it really would (a table lamp a kilo or two, a fridge more than you can carry
 * comfortably), so moving house is slow, heavy work. What you can lift depends on its size and what it's made of:
 * small things anyone can take, a table, bed or sofa needs the skill for its material, a wardrobe or counter more.
 * Plumbed fixtures (sinks, toilets, tubs) come out with a pipe wrench and Plumbing; wired ones (switches, wall and
 * ceiling lights) with a screwdriver and Electrical. Anything with things inside has to be emptied first.
 */
public final class Moving {
    public enum Size {
        SMALL("small"), MEDIUM("medium"), LARGE("large");

        public final String word;

        Size(String word) {
            this.word = word;
        }
    }

    /** What lifting it takes: plain handling (by material), plumbing or wiring. */
    public enum Kind { PLAIN, PLUMBED, WIRED }

    /** A piece of furniture as the moving rules see it. */
    public record Piece(Size size, Kind kind, Skill skill, int level, double kg) {
    }

    private static final Map<Block, Double> KG = new ConcurrentHashMap<>();

    /** The town's furniture and fittings: a city block that isn't a wall, floor or roof. Null for anything else. */
    @Nullable
    public static Piece piece(BlockState state, BlockGetter level, BlockPos pos) {
        Block block = state.getBlock();
        if (!(block instanceof CityBlock) || state.getDestroySpeed(level, pos) <= 0f || DigRules.wallTier(state, level, pos) != null
                || structure(state)) {
            return null;
        }
        return piece(block);
    }

    static Piece piece(Block block) {
        ZcConfig.Building b = ZcConfig.get().building;
        double kg = kg(block);
        Size size = kg >= b.largeKg ? Size.LARGE : kg >= b.mediumKg ? Size.MEDIUM : Size.SMALL;
        String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
        Kind kind = plumbed(path) ? Kind.PLUMBED : wired(path) ? Kind.WIRED : Kind.PLAIN;
        Skill skill = switch (kind) {
            case PLUMBED -> Skill.PLUMBING;
            case WIRED -> Skill.ELECTRICAL;
            case PLAIN -> material(block.defaultBlockState());
        };
        int need = switch (kind) {
            case PLUMBED -> size == Size.LARGE ? b.plumbedLargeLevel : b.plumbedLevel;
            case WIRED -> b.wiredLevel;
            case PLAIN -> switch (size) {
                case SMALL -> 0;
                case MEDIUM -> b.mediumLevel;
                case LARGE -> b.largeLevel;
            };
        };
        return new Piece(size, kind, skill, need, kg);
    }

    /** Stonework that isn't a full block (kerbs, steps, a lowered road): it's broken up, never carried off. */
    private static boolean structure(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return state.getSoundType() == SoundType.STONE && !plumbed(path) && !wired(path) && volume(state.getBlock()) >= 0.7;
    }

    private static Skill material(BlockState state) {
        SoundType sound = state.getSoundType();
        if (sound == SoundType.METAL || sound == SoundType.CHAIN) return Skill.METALWORKING;
        if (sound == SoundType.STONE) return Skill.MASONRY;
        return Skill.CARPENTRY;
    }

    private static boolean plumbed(String path) {
        return path.contains("sink") || path.contains("toilet") && !path.contains("sign") || path.contains("_tub")
                || path.contains("bathtub") || path.contains("shower") || path.contains("urinal");
    }

    private static boolean wired(String path) {
        return path.contains("switch") || path.startsWith("lx_") || path.contains("sconce") || path.contains("ceiling_light")
                || path.contains("fluoro") || path.contains("strip_light") || path.contains("wall_light");
    }

    /** What one of these weighs, in kilograms: the size of its shape times how dense its material is. */
    public static double kg(Block block) {
        return KG.computeIfAbsent(block, k -> {
            double density = switch (densityOf(k.defaultBlockState().getSoundType())) {
                case 1 -> 70;   // steel
                case 2 -> 110;  // stone, ceramic
                case 3 -> 18;   // upholstery, cloth
                case 4 -> 35;   // glass
                default -> 40;  // wood and the rest
            };
            double kg = Math.max(0.3, volume(k) * density * ZcConfig.get().building.furnitureWeight);
            return Math.round(kg * 2) / 2.0;
        });
    }

    private static int densityOf(SoundType sound) {
        if (sound == SoundType.METAL || sound == SoundType.CHAIN) return 1;
        if (sound == SoundType.STONE) return 2;
        if (sound == SoundType.WOOL) return 3;
        if (sound == SoundType.GLASS) return 4;
        return 0;
    }

    /** How much of the block its shape fills, 0 to 1. */
    private static double volume(Block block) {
        double v = 0;
        for (AABB box : block.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()) {
            v += box.getXsize() * box.getYsize() * box.getZsize();
        }
        return Math.min(1, v);
    }

    /** The tool a kind of piece needs in hand to come out, or null when bare hands do. */
    public static boolean hasTool(Kind kind, ItemStack held) {
        return switch (kind) {
            case PLUMBED -> held.is(ModItems.PIPE_WRENCH);
            case WIRED -> BuiltInRegistries.ITEM.getKey(held.getItem()).equals(ZomboidCraft.id("screwdriver"));
            case PLAIN -> true;
        };
    }

    private static String tool(Kind kind) {
        return kind == Kind.PLUMBED ? "a pipe wrench" : "a screwdriver";
    }

    /** Whether you can lift it now, and if not, why. Null means yes. */
    @Nullable
    public static String refusal(Piece piece, Player player, BlockGetter level, BlockPos pos) {
        int have = Skills.level(player, piece.skill);
        if (!hasTool(piece.kind, player.getMainHandItem())) {
            return "It's " + (piece.kind == Kind.PLUMBED ? "plumbed in" : "wired in") + ". Take it out with " + tool(piece.kind)
                    + (piece.level > 0 ? " and " + piece.skill.displayName() + " " + piece.level + "." : ".");
        }
        if (have < piece.level) {
            return "Too " + (piece.size == Size.LARGE ? "big" : "heavy") + " to move safely (" + Weights.format(piece.kg) + "). You need "
                    + piece.skill.displayName() + " " + piece.level + ". You're at " + have + ".";
        }
        if (level.getBlockEntity(pos) instanceof BlockEntity be && !empty(be)) {
            return "Empty it out first.";
        }
        return null;
    }

    private static boolean empty(BlockEntity be) {
        if (be instanceof RandomizableContainer r && r.getLootTable() != null) {
            return false;  // still to be searched: whatever's in it is in it
        }
        return !(be instanceof Container c) || c.isEmpty();
    }

    /** How much of it one tick of lifting gets loose: heavier is slower, practice quicker. */
    public static float progress(Piece piece, Player player, BlockGetter level, BlockPos pos) {
        if (refusal(piece, player, level, pos) != null) {
            return 0f;
        }
        int over = Skills.level(player, piece.skill) - piece.level;
        double ticks = (10 + piece.kg * ZcConfig.get().building.liftTicksPerKg) / (1 + 0.08 * Math.max(0, over));
        return (float) (1 / Math.max(4, ticks));
    }

    /** Takes it up into your inventory (or sets it at your feet when there's no room). */
    static void pickUp(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, Piece piece) {
        ItemStack stack = new ItemStack(state.getBlock().asItem());
        level.removeBlock(pos, false);
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        // only the work that takes know-how teaches: unbolting plumbing and wiring
        if (piece.kind != Kind.PLAIN) {
            Skills.addXp(player, piece.skill, piece.size == Size.LARGE ? 10 : 6);
        }
        Noise.emit(level, Vec3.atCenterOf(pos), 3 + piece.kg / 10);
    }

    private Moving() {
    }
}
