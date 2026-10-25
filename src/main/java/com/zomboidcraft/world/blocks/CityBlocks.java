package com.zomboidcraft.world.blocks;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Registers the city's blocks (walls with their weathered variants, windows, doors, street
 * furniture, debris and plants) from the generated {@link CitySpecs}, with an item each in their own
 * creative tab, and turns the generator's block names into their states.
 */
public final class CityBlocks {
    private static final Map<String, Block> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, BlockState> CACHE = new HashMap<>();
    private static final List<Block> FITTINGS = new ArrayList<>();

    /** The table {@link CitySpecs} fills in; one call to {@link #s} per block. */
    abstract static class Specs {
        static final int PLAIN = 0, FACING = 1, AXIS = 2, DOOR = 3;
        /** Has see-through pixels, so it mustn't hide its neighbours' faces. */
        static final int SEE_THROUGH = 1;
        /** Plants and decals: no collision, breaks at once, other blocks can replace it. */
        static final int DECOR = 2;
        /** Opens like a chest with loot rolled from a table (see {@link Fittings}). */
        static final int LOOT = 4;

        protected void s(String name, int kind, int flags, int light, SoundType sound, float strength, double... boxes) {
            ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id(name));
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.of().setId(key).sound(sound)
                    .strength(strength, Math.max(strength, 3f));
            if (kind == DOOR) {
                properties.noOcclusion();
            }
            if ((flags & SEE_THROUGH) != 0) {
                properties.noOcclusion().isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, box) -> false);
            }
            if ((flags & DECOR) != 0) {
                properties.noCollision().replaceable();
            }
            if (light > 0) {
                // lit windows and lamps draw at full brightness, so they read as light sources at night
                properties.lightLevel(state -> light).emissiveRendering(state -> true);
            }
            VoxelShape shape = Shapes.empty();
            for (int i = 0; i + 5 < boxes.length; i += 6) {
                shape = Shapes.or(shape, Block.box(boxes[i], boxes[i + 1], boxes[i + 2], boxes[i + 3], boxes[i + 4], boxes[i + 5]));
            }
            boolean loot = (flags & LOOT) != 0;
            Block block = switch (kind) {
                // opened by hand like a wooden door; zombies bash them in (BashGoal)
                case DOOR -> new DoorBlock(sound == SoundType.METAL ? BlockSetType.COPPER : BlockSetType.SPRUCE, properties);
                case FACING -> loot ? new Fittings.Facing(properties, shape, (flags & DECOR) != 0)
                        : new CityBlock.Facing(properties, shape, (flags & DECOR) != 0);
                case AXIS -> new CityBlock.Axis(properties, shape);
                default -> loot ? new Fittings.Plain(properties, shape) : new CityBlock(properties, shape);
            };
            BLOCKS.put(name, Registry.register(BuiltInRegistries.BLOCK, key, block));
            if (loot) {
                FITTINGS.add(block);
            }
            ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(name));
            Item.Properties itemProperties = new Item.Properties().setId(itemKey).useBlockDescriptionPrefix();
            Registry.register(BuiltInRegistries.ITEM, itemKey, kind == DOOR
                    ? new DoubleHighBlockItem(block, itemProperties) : new BlockItem(block, itemProperties));
        }

        /** The loot table a searchable fitting rolls when the generator didn't name one. */
        protected void loot(String name, String table) {
            Fittings.defaultTable(BLOCKS.get(name), table);
        }
    }

    public static void register() {
        new CitySpecs();
        Fittings.register(FITTINGS);
        ResourceKey<CreativeModeTab> tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ZomboidCraft.id("city"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.zomboidcraft.city"))
                .icon(() -> new ItemStack(BLOCKS.get("brick_red_moss")))
                .displayItems((parameters, output) -> BLOCKS.values().forEach(output::accept))
                .build());
    }

    /** The state for a generator name like "window[axis=x]", or null when it isn't a city block. */
    public static BlockState resolve(String block) {
        return CACHE.computeIfAbsent(block, CityBlocks::lookup);
    }

    private static BlockState lookup(String block) {
        int open = block.indexOf('[');
        Block found = BLOCKS.get(open < 0 ? block : block.substring(0, open));
        if (found == null) {
            return null;
        }
        BlockState state = found.defaultBlockState();
        if (open >= 0) {
            for (String pair : block.substring(open + 1, block.length() - 1).split(",")) {
                int eq = pair.indexOf('=');
                Property<?> property = eq < 0 ? null : found.getStateDefinition().getProperty(pair.substring(0, eq));
                if (property != null) {
                    state = with(state, property, pair.substring(eq + 1));
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    private CityBlocks() {
    }
}
