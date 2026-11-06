package com.zomboidcraft.loot;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.loot.items.LootItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Josiah's rule (2026-11-02): no vanilla item, ever. Whatever any loot table rolls (a shelf, a car, a zombie, a cow, a
 * broken block, a fishing line, a vanilla chest) is checked last: a vanilla item becomes our own version of it, or
 * it is gone. Registered after every other loot hook so nothing slips in after it.
 */
public final class NoVanilla {
    /** Vanilla item id -> our item id (a LootItems id or a ModItems id). Anything not here is dropped. */
    private static final Map<String, String> OURS = Map.ofEntries(
            Map.entry("potion", "water_bottle"),
            Map.entry("glass_bottle", "glass_bottle"),
            Map.entry("potato", "potato"), Map.entry("baked_potato", "potato"), Map.entry("poisonous_potato", "potato"),
            Map.entry("carrot", "carrot"), Map.entry("apple", "apple"), Map.entry("bread", "sliced_bread"),
            Map.entry("beef", "steak"), Map.entry("cooked_beef", "steak"),
            Map.entry("porkchop", "pork_chops"), Map.entry("cooked_porkchop", "pork_chops"),
            Map.entry("mutton", "venison"), Map.entry("cooked_mutton", "venison"),
            Map.entry("rabbit", "venison"), Map.entry("cooked_rabbit", "venison"),
            Map.entry("chicken", "chicken_whole"), Map.entry("cooked_chicken", "chicken_whole"),
            Map.entry("cod", "fish_fillets"), Map.entry("cooked_cod", "fish_fillets"),
            Map.entry("salmon", "fish_fillets"), Map.entry("cooked_salmon", "fish_fillets"),
            Map.entry("egg", "eggs_carton"), Map.entry("milk_bucket", "milk_carton"),
            Map.entry("sugar", "sugar_bag"), Map.entry("string", "rope"), Map.entry("lead", "rope"),
            Map.entry("leather", "leather_strips"), Map.entry("bone", "bone_scrap"),
            Map.entry("coal", "charcoal"), Map.entry("charcoal", "charcoal"),
            Map.entry("torch", "flashlight"), Map.entry("flint_and_steel", "lighter"),
            Map.entry("dirt", "rubble_chunks"), Map.entry("coarse_dirt", "rubble_chunks"),
            Map.entry("rooted_dirt", "rubble_chunks"), Map.entry("gravel", "rubble_chunks"),
            Map.entry("cobblestone", "rubble_chunks"), Map.entry("sand", "rubble_chunks"),
            Map.entry("iron_ingot", "scrap_metal"), Map.entry("iron_nugget", "scrap_metal"));

    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (drops.isEmpty()) {
                return;
            }
            List<ItemStack> out = new ArrayList<>(drops.size());
            for (ItemStack stack : drops) {
                ItemStack mine = ours(stack);
                if (!mine.isEmpty()) {
                    out.add(mine);
                }
            }
            drops.clear();
            drops.addAll(out);
        });
    }

    /** The stack itself if it's ours, our version of a vanilla one, or EMPTY. */
    public static ItemStack ours(ItemStack stack) {
        if (stack.isEmpty()) {
            return stack;
        }
        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!key.getNamespace().equals("minecraft")) {
            return stack;
        }
        String path = key.getPath();
        Item item = null;
        if (path.endsWith("_log") || path.endsWith("_wood") || path.endsWith("_stem")) {
            item = find("firewood");
        } else if (path.equals("stick")) {
            item = ModItems.TREE_BRANCH;
        } else if (path.endsWith("_planks") || path.endsWith("_slab")) {
            item = find("plank");
        } else if (path.startsWith("music_disc_")) {
            item = find("vinyl_record");
        } else if (OURS.containsKey(path)) {
            item = find(OURS.get(path));
        }
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int count = Math.min(stack.getCount(), item.getDefaultMaxStackSize());
        return new ItemStack(item, count);
    }

    /** Our item for a vanilla one (planks -> our plank...), for code that used to hand out vanilla items. */
    public static ItemStack ours(ItemLike vanilla, int count) {
        ItemStack s = ours(new ItemStack(vanilla, count));
        return s;
    }

    /** Is this stack our item with this id? */
    public static boolean is(ItemStack s, String id) {
        return BuiltInRegistries.ITEM.getKey(s.getItem()).equals(com.zomboidcraft.ZomboidCraft.id(id));
    }

    /** Our item by id; a pile of rubble if it somehow isn't registered (never a vanilla one). */
    public static Item item(String id) {
        Item item = find(id);
        return item != null ? item : ModItems.RUBBLE;
    }

    private static Item find(String id) {
        Item item = LootItems.item(id);
        if (item == null && id.equals("rubble_chunks")) {
            item = ModItems.RUBBLE;
        }
        return item;
    }

    private NoVanilla() {
    }
}
