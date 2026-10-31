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
 * comfortably), so moving house is slow, heavy work. As in Zomboid, almost anything comes up with the right tool,
 * and your skill only decides whether it survives: small loose things and fridges need no tool and never break;
 * furniture needs a hammer and breaks 75% of the time at Carpentry 0, 7.5% less a level; appliances and wired lights
 * need a screwdriver, by Electrical the same way; sinks, toilets and tubs a pipe wrench, a flat 25% in Zomboid that
 * Plumbing (ours) wears down. A broken piece leaves what it was made of. Anything with things inside is emptied first.
 */
public final class Moving {
    public enum Size {
        SMALL("small"), MEDIUM("medium"), LARGE("large");

        public final String word;

        Size(String word) {
            this.word = word;
        }
    }

    /** What lifting it takes: the tool that gets it loose, and the skill that keeps it whole. */
    public enum Kind {
        /** Small loose things: picked up by hand, never break. */
        LOOSE(null, null),
        /** Fridges and freezers: no tool, never break, just heavy (as in Zomboid). */
        COLD(null, null),
        /** Tables, beds, sofas, wardrobes, counters, shelving, lockers: a hammer, kept whole by Carpentry. */
        FURNITURE("a hammer", Skill.CARPENTRY),
        /** Stoves, ovens, washers, dryers, dishwashers, TVs: a screwdriver, by Electrical. */
        APPLIANCE("a screwdriver", Skill.ELECTRICAL),
        /** Switches, wall and ceiling lights: a screwdriver, by Electrical. */
        WIRED("a screwdriver", Skill.ELECTRICAL),
        /** Sinks, toilets, tubs, showers: a pipe wrench, by Plumbing. */
        PLUMBED("a pipe wrench", Skill.PLUMBING);

        @Nullable public final String tool;
        @Nullable public final Skill skill;

        Kind(@Nullable String tool, @Nullable Skill skill) {
            this.tool = tool;
            this.skill = skill;
        }
    }

    /**
     * A piece of furniture as the moving rules see it: what it takes, what it's made of (for what's left when it
     * breaks), the chance it breaks at skill 0, and its weight.
     */
    public record Piece(Size size, Kind kind, Skill material, double risk, double kg) {
        /** The chance it comes apart in your hands at your level of its skill, 0 to 1: falls evenly to none at 10. */
        public double breakChance(Player player) {
            return kind.skill == null ? 0 : chance(risk, Skills.level(player, kind.skill));
        }
    }

    /** Zomboid's break chance: the skill-0 chance, less a tenth of it for every level. */
    public static double chance(double risk, int level) {
        return Math.max(0, risk * (10 - level) / 10.0);
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
        Kind kind = plumbed(path) ? Kind.PLUMBED : wired(path) ? Kind.WIRED : cold(path) ? Kind.COLD
                : appliance(path) ? Kind.APPLIANCE : size == Size.SMALL ? Kind.LOOSE : Kind.FURNITURE;
        double risk = switch (kind) {
            case LOOSE, COLD -> 0;
            case PLUMBED -> b.plumbedBreak;
            case FURNITURE, APPLIANCE, WIRED -> b.toolBreak;
        };
        return new Piece(size, kind, material(block.defaultBlockState()), risk, kg);
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

    private static boolean cold(String path) {
        return path.contains("fridge") || path.contains("freezer");
    }

    private static boolean appliance(String path) {
        return path.contains("stove") && !path.contains("wood_stove") && !path.contains("_pipe") || path.contains("oven")
                || path.contains("washer") || path.contains("dryer") || path.endsWith("_tv") || path.contains("_tv_");
    }

    private static boolean wired(String path) {
        return path.contains("switch") || path.startsWith("lx_") || path.contains("sconce") || path.contains("ceiling_light")
                || path.contains("fluoro") || path.contains("strip_light") || path.contains("wall_light");
    }

    /** What one of these weighs, in kilograms: the size of its shape times how dense its material is. */
    public static double kg(Block block) {
        return KG.computeIfAbsent(block, k -> {
            double density = switch (densityOf(k.defaultBlockState().getSoundType())) {
                case 1 -> 52;   // steel
                case 2 -> 82;   // stone, ceramic
                case 3 -> 14;   // upholstery, cloth
                case 4 -> 26;   // glass
                default -> 30;  // wood and the rest
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

    /** Whether the tool this kind of piece needs is in hand (always, when it needs none). */
    public static boolean hasTool(Kind kind, ItemStack held) {
        return switch (kind) {
            case LOOSE, COLD -> true;
            case FURNITURE -> held.is(ModItems.HAMMER);
            case PLUMBED -> held.is(ModItems.PIPE_WRENCH);
            case APPLIANCE, WIRED -> BuiltInRegistries.ITEM.getKey(held.getItem()).equals(ZomboidCraft.id("screwdriver"));
        };
    }

    /** Whether you can lift it now, and if not, why. Null means yes. */
    @Nullable
    public static String refusal(Piece piece, Player player, BlockGetter level, BlockPos pos) {
        if (!hasTool(piece.kind, player.getMainHandItem())) {
            return switch (piece.kind) {
                case PLUMBED -> "It's plumbed in. Take it out with a pipe wrench.";
                case WIRED -> "It's wired in. Take it down with a screwdriver.";
                case APPLIANCE -> "It's hooked up. Unhook it with a screwdriver.";
                default -> "Too big to just lift. Take it apart with a hammer.";
            };
        }
        if (level.getBlockEntity(pos) instanceof BlockEntity be && !empty(be)) {
            return "Empty it out first.";
        }
        return null;
    }

    private static boolean empty(BlockEntity be) {
        // still to be searched: whatever's in it is in it. Only the server knows (a fitting on the client always
        // carries its default table), so the client lets the crack run and the server has the last word.
        if (be instanceof RandomizableContainer r && r.getLootTable() != null && be.getLevel() != null && !be.getLevel().isClientSide()) {
            return false;
        }
        return !(be instanceof Container c) || c.isEmpty();
    }

    /** What lifting it risks, said on the first swing: its weight, and the chance it breaks at your level. */
    public static String risk(Piece piece, Player player) {
        double chance = piece.breakChance(player);
        return Weights.format(piece.kg) + (chance <= 0 ? "." : String.format(java.util.Locale.ROOT,
                ". %s %d: %.1f%% chance it breaks.", piece.kind.skill.displayName(), Skills.level(player, piece.kind.skill),
                chance * 100).replace(".0%", "%"));
    }

    /** How much of it one tick of lifting gets loose: heavier is slower, practice quicker. */
    public static float progress(Piece piece, Player player, BlockGetter level, BlockPos pos) {
        if (refusal(piece, player, level, pos) != null) {
            return 0f;
        }
        int skill = piece.kind.skill == null ? 0 : Skills.level(player, piece.kind.skill);
        double ticks = (10 + piece.kg * ZcConfig.get().building.liftTicksPerKg) / (1 + 0.08 * skill);
        return (float) (1 / Math.max(4, ticks));
    }

    /**
     * Takes it up into your inventory (or sets it at your feet when there's no room), unless it breaks: without the
     * skill it can come apart in your hands, Zomboid style, leaving only what it was made of. Every tool job teaches
     * its skill, broken or not.
     */
    static void pickUp(ServerPlayer player, ServerLevel level, BlockPos pos, BlockState state, Piece piece) {
        boolean broke = player.getRandom().nextDouble() < piece.breakChance(player);
        level.removeBlock(pos, false);
        if (broke) {
            scraps(level, pos, piece, player.getRandom());
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("It came apart in your hands.")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        } else {
            ItemStack stack = new ItemStack(state.getBlock().asItem());
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                net.minecraft.world.level.block.Block.popResource(level, player.blockPosition(), stack);
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("No room to carry it. It's at your feet.")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        }
        if (piece.kind.skill != null) {
            Skills.addXp(player, piece.kind.skill, piece.size == Size.LARGE ? 8 : 4);
        }
        Noise.emit(level, Vec3.atCenterOf(pos), (broke ? 8 : 3) + piece.kg / 10);
    }

    /** What a broken piece leaves: planks and nails, scrap metal or rubble, by what it was made of. */
    private static void scraps(ServerLevel level, BlockPos pos, Piece piece, net.minecraft.util.RandomSource random) {
        int n = piece.size == Size.LARGE ? 2 + random.nextInt(2) : 1 + random.nextInt(2);
        Skill made = piece.kind == Kind.APPLIANCE || piece.kind == Kind.WIRED ? Skill.METALWORKING : piece.material;
        ItemStack bits = switch (made) {
            case METALWORKING -> new ItemStack(ModItems.SCRAP_METAL, n);
            case MASONRY -> new ItemStack(ModItems.RUBBLE, n);
            default -> new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS, n);
        };
        net.minecraft.world.level.block.Block.popResource(level, pos, bits);
        if (made == Skill.CARPENTRY && random.nextFloat() < 0.5f) {
            net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(ModItems.NAILS, 1 + random.nextInt(3)));
        }
    }

    private Moving() {
    }
}
