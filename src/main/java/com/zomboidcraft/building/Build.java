package com.zomboidcraft.building;

import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ModItems;
import com.zomboidcraft.barricade.BarricadeBlock;
import com.zomboidcraft.skills.Skill;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.world.blocks.CityBlock;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the Build page makes: what each takes, how long, which skill, and where it can go. The same rules run
 * on the client (to show what you can build and the see-through preview) and on the server (to build it).
 */
public enum Build {
    BOARD("Board up", Where.OPENING, Skill.CARPENTRY, 0, 50, 6, 16,
            "Nail a plank across a window or doorway. Up to four boards, and zombies have to knock each one off.",
            tool("Hammer", ModItems.HAMMER), need("Plank", s -> s.is(ItemTags.PLANKS), 1, Items.OAK_PLANKS), need("Nails", ModItems.NAILS, 2)),
    METAL_SHEET("Metal sheet", Where.OPENING, Skill.METALWORKING, 0, 100, 10, 12,
            "Tack a steel sheet over a window or doorway. Several times tougher than planks, but you can't see or shoot through it.",
            tool("Propane torch", ModItems.PROPANE_TORCH), need("Metal sheet", ModItems.METAL_SHEET, 1)),
    TRIPWIRE("Tin-can tripwire", Where.GROUND, Skill.CARPENTRY, 0, 40, 3, 0,
            "String strung across a gap with cans on it. Anything walking through sets them rattling, so you hear it coming. Zombies hear it too.",
            null, need("String", Items.STRING, 2), need("Tin cans", ModItems.TIN_CAN, 3)),
    RAIN_BARREL("Rain barrel", Where.GROUND, Skill.CARPENTRY, 0, 80, 8, 8,
            "A drum under a tarp funnel. It fills while it rains, faster in a storm. Fill bottles from it.",
            null, need("Plastic drum", ModItems.PLASTIC_DRUM, 1), need("Tarp", ModItems.TARP, 1)),
    SANDBAGS("Sandbag wall", Where.GROUND, Skill.MASONRY, 0, 100, 6, 6,
            "Fill sandbags with earth and stack them waist high. Crawlers can't get past, and walkers slow down climbing over.",
            tool("Shovel", ModItems.SHOVEL), need("Empty sandbags", ModItems.EMPTY_SANDBAG, 4),
            need("Dirt, sand, gravel or rubble", s -> s.is(Items.DIRT) || s.is(Items.COARSE_DIRT) || s.is(Items.SAND)
                    || s.is(Items.GRAVEL) || s.is(Items.ROOTED_DIRT) || s.is(ModItems.RUBBLE), 4, Items.DIRT)),
    WELD_SHEET("Metal sheet from scrap", Where.MAKE, Skill.METALWORKING, 1, 120, 10, 10,
            "Cut and weld scrap into a flat sheet you can put over a window.",
            tool("Propane torch", ModItems.PROPANE_TORCH), need("Scrap metal", ModItems.SCRAP_METAL, 4));

    /** Where a build goes: on a window or doorway, on open ground, or into your pack. */
    public enum Where { OPENING, GROUND, MAKE }

    /** Something a build uses up (or, for the tool, only needs on you). */
    public record Need(String label, Predicate<ItemStack> fits, int count, Item icon) {
    }

    /** Where it would go and what it would put there, or why it can't. */
    public record Spot(@Nullable BlockPos pos, @Nullable BlockState state, String problem) {
        static Spot no(String why) {
            return new Spot(null, null, why);
        }

        public boolean ok() {
            return pos != null;
        }
    }

    public final String label;
    public final Where where;
    public final Skill skill;
    public final int level;
    /** Ticks at level 0. */
    public final int ticks;
    public final int xp;
    public final double noise;
    public final String about;
    @Nullable
    public final Need tool;
    public final List<Need> needs;

    Build(String label, Where where, Skill skill, int level, int ticks, int xp, double noise, String about, @Nullable Need tool, Need... needs) {
        this.label = label;
        this.where = where;
        this.skill = skill;
        this.level = level;
        this.ticks = ticks;
        this.xp = xp;
        this.noise = noise;
        this.about = about;
        this.tool = tool;
        this.needs = List.of(needs);
    }

    private static Need tool(String label, Item item) {
        return new Need(label, s -> s.is(item), 1, item);
    }

    private static Need need(String label, Item item, int count) {
        return new Need(label, s -> s.is(item), count, item);
    }

    private static Need need(String label, Predicate<ItemStack> fits, int count, Item icon) {
        return new Need(label, fits, count, icon);
    }

    /** How long it takes this player: a tenth quicker per level of its skill. */
    public int ticksFor(Player player) {
        double m = com.zomboidcraft.ZcConfig.get().building.timeMultiplier;
        return Math.max(10, (int) Math.round(ticks * m / (1 + 0.1 * Skills.level(player, skill))));
    }

    /** What's missing, as a sentence, or null when it can be built (where aside). */
    @Nullable
    public String missing(Player player) {
        if (player.hasInfiniteMaterials()) return null;
        if (Skills.level(player, skill) < level) return "Needs " + skill.displayName() + " " + level + ".";
        if (tool != null && Materials.count(player, tool.fits()) < 1) return "Needs a " + tool.label().toLowerCase(java.util.Locale.ROOT) + ".";
        for (Need n : needs) {
            if (Materials.count(player, n.fits()) < n.count()) return "Needs " + n.count() + " " + n.label().toLowerCase(java.util.Locale.ROOT) + ".";
        }
        return null;
    }

    // ---------------------------------------------------------------- where it goes

    public static boolean isWindow(BlockState state) {
        if (state.getBlock() instanceof CityBlock) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            return path.startsWith("window") || path.startsWith("ext_win_") || path.contains("_window");
        }
        return state.getSoundType() == SoundType.GLASS && (state.getBlock().getDescriptionId().contains("glass")
                || state.getBlock().getDescriptionId().contains("window"));
    }

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock;
    }

    /**
     * Where this would go when you look at {@code hit} from its {@code face}, with which way you're facing. Mirrors
     * the hammer: on a window it goes in the window, on a door in the doorway in front of it, on boards it adds one.
     */
    public Spot spot(Level level, BlockPos hit, Direction face, Direction looking) {
        BlockState there = level.getBlockState(hit);
        switch (where) {
            case MAKE -> {
                return Spot.no("");
            }
            case OPENING -> {
                if (this == BOARD && there.is(ModBlocks.BARRICADE)) {
                    int planks = there.getValue(BarricadeBlock.PLANKS);
                    if (planks >= BarricadeBlock.MAX_PLANKS && there.getValue(BarricadeBlock.DAMAGE) == 0) return Spot.no("No room for another board.");
                    return new Spot(hit, there, "");
                }
                if (face.getAxis().isVertical()) return Spot.no("Look at a window or a door.");
                BlockPos pos;
                Direction facing;
                if (isWindow(there)) {
                    pos = hit;
                    facing = face;
                } else if (isDoor(there) && level.getBlockState(hit.relative(face)).canBeReplaced()) {
                    pos = hit.relative(face);
                    facing = face.getOpposite();
                } else {
                    return Spot.no("Look at a window or a door.");
                }
                BlockState state = this == BOARD
                        ? ModBlocks.BARRICADE.defaultBlockState().setValue(BarricadeBlock.FACING, facing)
                        : BuildBlocks.METAL_SHEET.defaultBlockState().setValue(BuildBlocks.MetalSheet.FACING, facing);
                return new Spot(pos.immutable(), state, "");
            }
            default -> {
                BlockPos pos = there.canBeReplaced() ? hit : hit.relative(face);
                if (!level.getBlockState(pos).canBeReplaced()) return Spot.no("Something's in the way.");
                if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return Spot.no("Needs solid ground under it.");
                BlockState state = switch (this) {
                    case TRIPWIRE -> BuildBlocks.TRIPWIRE.defaultBlockState().setValue(BuildBlocks.Tripwire.AXIS,
                            looking.getClockWise().getAxis());
                    case RAIN_BARREL -> BuildBlocks.RAIN_BARREL.defaultBlockState();
                    default -> BuildBlocks.SANDBAGS.defaultBlockState().setValue(BuildBlocks.Sandbags.FACING, looking);
                };
                return new Spot(pos.immutable(), state, "");
            }
        }
    }
}
