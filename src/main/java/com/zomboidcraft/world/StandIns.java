package com.zomboidcraft.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Vanilla look-alikes for the city's own blocks, so the generator can be walked in game before
 * those blocks exist. Picked for the wet, rotting look: deepslate roads, terracotta cars,
 * oxidised copper fire escapes, cobwebs for barbed wire. A block's facing or axis carries over
 * when the stand-in has one too.
 */
public final class StandIns {
    private static final Map<String, String> TABLE = new HashMap<>();
    /** Stand-ins whose facing points the other way from the city block's. */
    private static final Set<String> FLIP = Set.of("office_chair");

    static {
        put("air", "air");
        // streets
        put("asphalt", "polished_deepslate");
        put("road_line_yellow", "yellow_terracotta");
        put("road_line_white", "light_gray_concrete");
        put("crosswalk", "light_gray_concrete");
        put("sidewalk", "smooth_stone");
        put("sidewalk_cracked", "tuff");
        put("curb", "stone");
        put("streetlight_pole", "polished_deepslate_wall");
        put("streetlight_arm", "polished_deepslate_slab[type=top]");
        put("streetlight_head", "ochre_froglight");
        put("streetlight_head_off", "polished_deepslate_slab[type=top]");
        put("jersey_barrier", "andesite_wall");
        // walls and floors
        put("concrete_panel", "light_gray_concrete");
        put("concrete_dark", "gray_concrete");
        put("floor_concrete", "polished_andesite");
        put("brick_red", "bricks");
        put("brick_dark", "deepslate_bricks");
        put("stone_trim", "stone_bricks");
        put("sill", "stone_brick_slab[type=top]");
        put("steel_mullion", "smooth_basalt");
        put("window", "gray_stained_glass");
        put("window_lit", "glowstone");
        put("window_broken", "gray_stained_glass_pane");
        put("glass_spandrel", "black_stained_glass");
        put("boarded_window", "spruce_planks");
        put("office_floor", "gray_wool");
        put("lobby_floor_white", "quartz_block");
        put("lobby_floor_dark", "polished_blackstone");
        put("marble_white", "quartz_block");
        put("roof", "tuff");
        put("drywall", "calcite");
        put("wood_panel", "dark_oak_planks");
        put("awning", "dark_oak_slab[type=top]");
        for (String c : new String[] {"red", "blue", "maroon", "yellow", "teal"}) {
            put("awning_" + c, "dark_oak_slab[type=top]");
        }
        put("plaster_sage", "green_terracotta");
        put("plaster_cream", "white_terracotta");
        put("plaster_blue", "cyan_terracotta");
        put("plaster_rose", "pink_terracotta");
        put("brick_buff", "cut_sandstone");
        put("brick_orange", "bricks");
        put("shop_paint_red", "red_terracotta");
        put("shop_paint_navy", "blue_terracotta");
        put("shop_paint_teal", "cyan_terracotta");
        put("shop_paint_black", "black_terracotta");
        put("shop_paint_mustard", "yellow_terracotta");
        put("shop_paint_green", "green_terracotta");
        put("stairs_concrete", "stone_brick_stairs");
        put("fire_door", "copper_door");
        put("shop_door", "copper_door");
        put("flat_door", "spruce_door");
        put("elevator_door", "iron_block");
        put("hvac", "blast_furnace");
        put("water_tank", "stripped_spruce_log");
        put("water_tank_top", "spruce_slab");
        // fire escapes, railway, fences
        put("grate", "iron_trapdoor[half=bottom]");
        put("railing", "iron_bars");
        put("steel_stairs", "waxed_oxidized_cut_copper_stairs");
        put("ladder", "ladder");
        put("steel_girder", "waxed_weathered_cut_copper");
        put("train_body", "light_gray_terracotta");
        put("fence_post", "polished_deepslate_wall");
        put("chainlink", "iron_bars");
        put("barbed_wire", "cobweb");
        // inside
        put("ceiling_light", "lantern[hanging=true]");
        put("ceiling_light_off", "air");
        put("aviation_light", "redstone_torch");
        put("desk", "spruce_slab[type=top]");
        put("office_chair", "dark_oak_stairs");
        put("cubicle_wall", "light_gray_wool");
        put("filing_cabinet", "barrel");
        put("reception_desk", "stripped_dark_oak_wood");
        put("planter", "potted_dead_bush");
        put("wall_lantern", "lantern");
        put("wall_lantern_off", "air");
        // wrecks and decay
        put("tire", "black_concrete");
        put("rust", "brown_terracotta");
        put("rubble", "cobblestone");
        put("rubble_pile", "cobblestone_slab");
        put("debris_chunk", "mossy_cobblestone_slab");
        put("moss_carpet", "moss_carpet");
        put("weeds", "dead_bush");
        put("vines_hanging", "vine[up=true]");
        put("vines", "vine");
        put("rail", "rail");
        String[][] cars = {{"red", "red_terracotta", "red_carpet"}, {"white", "white_concrete", "white_carpet"},
                {"blue", "blue_terracotta", "blue_carpet"}, {"green", "green_terracotta", "green_carpet"},
                {"grey", "gray_terracotta", "gray_carpet"}, {"burnt", "blackstone", "black_carpet"}};
        for (String[] car : cars) {
            put("car_" + car[0], car[1]);
            put("car_door_" + car[0], car[1]);
            put("car_hood_" + car[0], car[2]);
            put("car_cabin_" + car[0], "tinted_glass");
            put("car_cabin_" + car[0] + "_broken", "gray_stained_glass_pane");
        }
    }

    private static final Map<String, BlockState> CACHE = new HashMap<>();

    private StandIns() {
    }

    private static void put(String city, String vanilla) {
        TABLE.put(city, vanilla);
    }

    /** The block state for a city block name like "desk[facing=east]" or "minecraft:stone". */
    public static BlockState resolve(String block) {
        return CACHE.computeIfAbsent(block, StandIns::lookup);
    }

    private static BlockState lookup(String block) {
        String name = block;
        Map<String, String> props = new HashMap<>();
        int open = block.indexOf('[');
        if (open >= 0) {
            name = block.substring(0, open);
            props = parseProps(block.substring(open + 1, block.length() - 1));
        }
        String id;
        if (name.startsWith("minecraft:")) {
            id = block.substring("minecraft:".length());
            props = new HashMap<>();
        } else {
            id = TABLE.get(name);
            if (id == null && (name.startsWith("sign_") || name.startsWith("blade_") || name.startsWith("neon_"))) {
                return Blocks.AIR.defaultBlockState();  // shop signs only come with the real blocks
            }
            if (id == null) {
                return Blocks.STONE.defaultBlockState();
            }
        }
        // the stand-in's own properties come first, then what carries over from the city block
        Map<String, String> fixed = Map.of();
        int at = id.indexOf('[');
        if (at >= 0) {
            fixed = parseProps(id.substring(at + 1, id.length() - 1));
            id = id.substring(0, at);
        }
        Block vanilla = BuiltInRegistries.BLOCK.getValue(Identifier.withDefaultNamespace(id));
        BlockState state = vanilla.defaultBlockState();
        for (Map.Entry<String, String> e : fixed.entrySet()) {
            state = with(state, e.getKey(), e.getValue());
        }
        String facing = props.get("facing");
        String axis = props.get("axis");
        if (name.equals("vines") && facing != null) {
            // a vine names the side it clings to
            return with(state, facing, "true");
        }
        if (name.equals("rail") && axis != null) {
            return with(state, "shape", axis.equals("x") ? "east_west" : "north_south");
        }
        if (facing != null) {
            if (FLIP.contains(name)) {
                facing = switch (facing) {
                    case "north" -> "south";
                    case "south" -> "north";
                    case "east" -> "west";
                    default -> "east";
                };
            }
            state = with(state, "facing", facing);
        }
        if (axis != null) {
            state = with(state, "axis", axis);
        }
        for (String key : new String[] {"half", "hinge", "open"}) {  // doors
            if (props.containsKey(key)) {
                state = with(state, key, props.get(key));
            }
        }
        return state;
    }

    private static Map<String, String> parseProps(String text) {
        Map<String, String> props = new HashMap<>();
        for (String pair : text.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                props.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return props;
    }

    private static BlockState with(BlockState state, String key, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(key);
        return property == null ? state : set(state, property, value);
    }

    private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }
}
