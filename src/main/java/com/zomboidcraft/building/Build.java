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
    ALARM_CLOCK("Alarm clock decoy", Where.GROUND, null, 0, 20, 0, 0,
            "Set it down in a street and walk away. When it rings, everything in earshot goes to it instead of you. "
                    + "Right-click the clock first to choose when (10 seconds to 5 minutes).",
            null, need("Alarm clock", s -> s.getItem() instanceof AlarmClockItem, 1, ModItems.ALARM_CLOCK), need("AA battery", ModItems.AA_BATTERY, 1)),
    CAR_ALARM("Car alarm decoy", Where.CAR, null, 0, 60, 0, 0,
            "Wire an alarm clock into a parked car's alarm. When the clock's time is up the car goes off, far louder and longer "
                    + "than the clock. Its battery has to be good. Got its key? Hold it and press Interact (G) up to 40 blocks off.",
            null, need("Alarm clock", s -> s.getItem() instanceof AlarmClockItem, 1, ModItems.ALARM_CLOCK)),
    PLANTER("Planter box", Where.GROUND, Skill.CARPENTRY, 0, 120, 10, 14,
            "A raised bed of planks full of earth. Plant a seed packet in it and keep it wet (rain, or pour a bottle of water on); "
                    + "it's ripe in about four days. Up on a roof it's out of reach.",
            tool("Hammer", ModItems.HAMMER), need("Planks", s -> s.is(ItemTags.PLANKS), 4, Items.OAK_PLANKS), need("Nails", ModItems.NAILS, 4),
            need("Dirt", s -> s.is(Items.DIRT) || s.is(Items.COARSE_DIRT) || s.is(Items.ROOTED_DIRT), 4, Items.DIRT)),
    SPIKED_WALL("Spiked log wall", Where.GROUND, Skill.CARPENTRY, 1, 160, 14, 18,
            "Logs sharpened to points and driven in side by side, too tall to climb. Zombies pushing on it or beating at it "
                    + "tear themselves on the spikes.",
            tool("Axe", s -> s.is(ModItems.FIRE_AXE) || s.is(ItemTags.AXES), ModItems.FIRE_AXE), need("Logs", s -> s.is(ItemTags.LOGS), 3, Items.OAK_LOG)),
    WELD_SHEET("Metal sheet from scrap", Where.MAKE, Skill.METALWORKING, 1, 120, 10, 10,
            "Cut and weld scrap into a flat sheet you can put over a window.",
            tool("Propane torch", ModItems.PROPANE_TORCH), need("Scrap metal", ModItems.SCRAP_METAL, 4)),
    // wood building (base customisation wave 2): frame first, then finish
    CRATE("Wooden crate", Where.GROUND, Skill.CARPENTRY, 1, 100, 6, 12,
            "A plank box with a lid, nailed square. Storage you can put anywhere, and your first thing built from scratch.",
            hammer(), planks(3), need("Nails", ModItems.NAILS, 3)),
    WALL_FRAME("Wall frame", Where.WALL, Skill.CARPENTRY, 3, 100, 8, 14,
            "Studs nailed between a sole plate and a top plate, with a brace. Quick to put up and the start of every wall, "
                    + "but zombies see, reach and push straight through it. Board it to finish it. Stand it on the ground, "
                    + "a floor, or another wall.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 2)),
    WOOD_WALL("Board a wall", Where.FRAME, Skill.CARPENTRY, 4, 160, 12, 16,
            "Board a wall frame on both sides into a solid wall. Zombies have to beat it down plank by plank, and it "
                    + "stands longer the better the carpenter who built it.",
            hammer(), planks(3), need("Nails", ModItems.NAILS, 3)),
    WINDOW_FRAME("Window frame", Where.WALL, Skill.CARPENTRY, 3, 120, 8, 14,
            "A sill, a header and jambs in the run of a wall, round an opening to look out of. Board it over when they come.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 2)),
    DOOR_FRAME("Door frame", Where.WALL, Skill.CARPENTRY, 3, 140, 10, 14,
            "Two jambs and a header, two blocks tall, in the run of a wall. Walk through it, or hang a door in it.",
            hammer(), planks(3), need("Nails", ModItems.NAILS, 3)),
    WOOD_DOOR("Wooden door", Where.DOORWAY, Skill.CARPENTRY, 4, 200, 14, 14,
            "A ledged and braced plank door hung in a door frame. Opens by hand; zombies have to beat it in.",
            hammer(), planks(4), need("Nails", ModItems.NAILS, 4),
            need("Door hinges", s -> BuiltInRegistries.ITEM.getKey(s.getItem()).equals(com.zomboidcraft.ZomboidCraft.id("hinges")), 1, ModItems.NAILS)),
    POST("Wooden post", Where.POST, Skill.CARPENTRY, 3, 80, 6, 10,
            "A squared timber post. Up to three stand one on another, and a floor goes on top: how a room is built out "
                    + "over open ground or raised off it.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 2)),
    FLOOR("Wooden floor", Where.FLOOR, Skill.CARPENTRY, 3, 80, 6, 12,
            "Boards over joists. It has to stand on something (a post, a wall or solid ground) or reach no more than "
                    + "three boards from something that does. Take its support away and it sags, creaks, then falls in.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 2)),
    // wave 2b
    WOOD_STAIRS("Wooden stairs", Where.STAIRS, Skill.CARPENTRY, 6, 160, 10, 18,
            "Treads on stringers, climbing away from you. Stand them on the ground, a floor or a wall to get up a storey, "
                    + "the way Zomboid's stairs take a good carpenter.",
            hammer(), planks(3), need("Nails", ModItems.NAILS, 3)),
    LADDER("Wooden ladder", Where.FACE, Skill.CARPENTRY, 2, 80, 6, 8,
            "Two rails and rungs nailed up against a wall. Climb it to a roof or a lookout; zombies can't.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 2)),
    ROOF("Shingled roof", Where.ROOF, Skill.CARPENTRY, 5, 120, 8, 14,
            "A pitched roof section, boards under tarred shingles. It sits on a wall, a post or a floor, or runs on from "
                    + "more roof beside it. Keeps the rain off what you store.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 3)),
    SHUTTERS("Window shutters", Where.SHUTTER, Skill.CARPENTRY, 3, 120, 8, 12,
            "Plank shutters hung outside a window frame you built. Open them by hand to look out; shut, zombies have to "
                    + "beat them in, and the frame's left bare when they give.",
            hammer(), planks(2), need("Nails", ModItems.NAILS, 2),
            need("Door hinges", s -> BuiltInRegistries.ITEM.getKey(s.getItem()).equals(com.zomboidcraft.ZomboidCraft.id("hinges")), 1, ModItems.NAILS));

    /**
     * Where a build goes: on a window or doorway, on open ground, on a parked car, into your pack; or for wood
     * building, in the run of a wall, onto a wall frame, into a door frame, as a post, or as a floor.
     */
    public enum Where { OPENING, GROUND, CAR, MAKE, WALL, FRAME, DOORWAY, POST, FLOOR, STAIRS, FACE, ROOF, SHUTTER }

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
    /** The skill it trains and goes quicker with; null for something anyone does as quick (setting down a clock). */
    @Nullable
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

    Build(String label, Where where, @Nullable Skill skill, int level, int ticks, int xp, double noise, String about, @Nullable Need tool, Need... needs) {
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

    private static Need hammer() {
        return tool("Hammer", ModItems.HAMMER);
    }

    /** Planks: the game's own, or the loot plank from a hardware store or a lumber pile. */
    private static Need planks(int count) {
        return need("Planks", s -> s.is(ItemTags.PLANKS) || BuiltInRegistries.ITEM.getKey(s.getItem()).equals(com.zomboidcraft.ZomboidCraft.id("plank")),
                count, Items.OAK_PLANKS);
    }

    private static Need tool(String label, Item item) {
        return new Need(label, s -> s.is(item), 1, item);
    }

    private static Need tool(String label, Predicate<ItemStack> fits, Item icon) {
        return new Need(label, fits, 1, icon);
    }

    private static Need need(String label, Item item, int count) {
        return new Need(label, s -> s.is(item), count, item);
    }

    private static Need need(String label, Predicate<ItemStack> fits, int count, Item icon) {
        return new Need(label, fits, count, icon);
    }

    /** The player's level in its skill, 0 when it has none. */
    public int skillLevel(Player player) {
        return skill == null ? 0 : Skills.level(player, skill);
    }

    /** How long it takes this player: a tenth quicker per level of its skill. */
    public int ticksFor(Player player) {
        double m = com.zomboidcraft.ZcConfig.get().building.timeMultiplier;
        return Math.max(10, (int) Math.round(ticks * m / (1 + 0.1 * skillLevel(player))));
    }

    /** What's missing, as a sentence, or null when it can be built (where aside). */
    @Nullable
    public String missing(Player player) {
        if (player.hasInfiniteMaterials()) return null;
        if (skill != null && Skills.level(player, skill) < level) return "Needs " + skill.displayName() + " " + level + ".";
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
            case CAR -> {
                return Spot.no("Look at a parked car.");
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
            case FRAME -> {
                if (!there.is(WoodBlocks.WALL_FRAME)) return Spot.no("Look at a wall frame.");
                return new Spot(hit, WoodBlocks.WALL.defaultBlockState().setValue(WoodBlocks.Wall.AXIS, there.getValue(WoodBlocks.Frame.AXIS)), "");
            }
            case DOORWAY -> {
                if (!there.is(WoodBlocks.DOOR_FRAME)) return Spot.no("Look at a door frame.");
                BlockPos lower = there.getValue(WoodBlocks.DoorFrame.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER ? hit : hit.below();
                Direction.Axis axis = there.getValue(WoodBlocks.DoorFrame.AXIS);
                // the door faces the way you look as you hang it, as a door you place yourself would
                Direction facing = looking.getAxis() != axis ? looking : Direction.fromAxisAndDirection(
                        axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X, Direction.AxisDirection.POSITIVE);
                return new Spot(lower.immutable(), WoodBlocks.DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing), "");
            }
            case SHUTTER -> {
                if (!there.is(WoodBlocks.WINDOW_FRAME)) return Spot.no("Look at a window frame you built.");
                Direction.Axis axis = there.getValue(WoodBlocks.Window.AXIS);
                // they hang on the far side, the outside as you stand in the room looking out
                Direction out = looking.getAxis() != axis ? looking
                        : Direction.fromAxisAndDirection(axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X, Direction.AxisDirection.POSITIVE);
                return new Spot(hit, WoodBlocks.SHUTTERS.defaultBlockState().setValue(WoodBlocks.Shutters.FACING, out), "");
            }
            case FACE -> {
                if (face.getAxis().isVertical()) return Spot.no("Look at the side of a wall.");
                BlockPos pos = hit.relative(face);
                if (!level.getBlockState(pos).canBeReplaced()) return Spot.no("Something's in the way.");
                if (!there.isFaceSturdy(level, hit, face)) return Spot.no("It needs a solid wall behind it.");
                return new Spot(pos.immutable(), WoodBlocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, face), "");
            }
            case STAIRS, ROOF -> {
                BlockPos pos = there.canBeReplaced() ? hit : hit.relative(face);
                if (!level.getBlockState(pos).canBeReplaced()) return Spot.no("Something's in the way.");
                if (where == Where.STAIRS && !WoodBlocks.base(level, pos.below())) return Spot.no("Needs the ground, a floor or a wall under it.");
                if (where == Where.ROOF && !WoodBlocks.roofHeld(level, pos)) return Spot.no("Nothing to sit on: a wall, a post, a floor or more roof beside it.");
                net.minecraft.world.level.block.Block block = where == Where.ROOF ? WoodBlocks.ROOF : WoodBlocks.STAIRS;
                return new Spot(pos.immutable(), block.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, looking), "");
            }
            case WALL, POST, FLOOR -> {
                BlockPos pos = there.canBeReplaced() ? hit : hit.relative(face);
                if (!level.getBlockState(pos).canBeReplaced()) return Spot.no("Something's in the way.");
                // walls run across the way you're looking
                Direction.Axis axis = looking.getClockWise().getAxis();
                if (where == Where.FLOOR) {
                    if (!WoodBlocks.supported(level, pos, null)) {
                        return Spot.no("Nothing would hold it up: a post, a wall or ground under it, within " + com.zomboidcraft.ZcConfig.get().building.floorSpan + " boards.");
                    }
                    return new Spot(pos.immutable(), WoodBlocks.FLOOR.defaultBlockState(), "");
                }
                if (!WoodBlocks.base(level, pos.below())) return Spot.no("Needs the ground, a floor or a wall under it.");
                if (where == Where.POST) {
                    if (WoodBlocks.postsBelow(level, pos) >= com.zomboidcraft.ZcConfig.get().building.postStack) return Spot.no("Posts stand no more than three high.");
                    return new Spot(pos.immutable(), WoodBlocks.POST.defaultBlockState(), "");
                }
                return switch (this) {
                    case WINDOW_FRAME -> new Spot(pos.immutable(), WoodBlocks.WINDOW_FRAME.defaultBlockState().setValue(WoodBlocks.Window.AXIS, axis), "");
                    case DOOR_FRAME -> level.getBlockState(pos.above()).canBeReplaced()
                            ? new Spot(pos.immutable(), WoodBlocks.DOOR_FRAME.defaultBlockState().setValue(WoodBlocks.DoorFrame.AXIS, axis), "")
                            : Spot.no("Needs two blocks of room.");
                    default -> new Spot(pos.immutable(), WoodBlocks.WALL_FRAME.defaultBlockState().setValue(WoodBlocks.Frame.AXIS, axis), "");
                };
            }
            default -> {
                BlockPos pos = there.canBeReplaced() ? hit : hit.relative(face);
                if (!level.getBlockState(pos).canBeReplaced()) return Spot.no("Something's in the way.");
                if (!level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)) return Spot.no("Needs solid ground under it.");
                if (this == SPIKED_WALL && !level.getBlockState(pos.above()).canBeReplaced()) return Spot.no("Not enough room above.");
                BlockState state = switch (this) {
                    case ALARM_CLOCK -> BuildBlocks.ALARM_CLOCK.defaultBlockState().setValue(BuildBlocks.AlarmClock.FACING, looking.getOpposite());
                    case PLANTER -> BuildBlocks.PLANTER.defaultBlockState();
                    case SPIKED_WALL -> BuildBlocks.SPIKED_WALL.defaultBlockState().setValue(BuildBlocks.SpikedWall.FACING, looking);
                    case TRIPWIRE -> BuildBlocks.TRIPWIRE.defaultBlockState().setValue(BuildBlocks.Tripwire.AXIS,
                            looking.getClockWise().getAxis());
                    case RAIN_BARREL -> BuildBlocks.RAIN_BARREL.defaultBlockState();
                    case CRATE -> com.zomboidcraft.world.blocks.CityBlocks.resolve("crate");
                    default -> BuildBlocks.SANDBAGS.defaultBlockState().setValue(BuildBlocks.Sandbags.FACING, looking);
                };
                return new Spot(pos.immutable(), state, "");
            }
        }
    }
}
