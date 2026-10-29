package com.zomboidcraft.loot.items;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;
import org.jetbrains.annotations.Nullable;

/**
 * Registers the world loot roster's items (LootSpecs, generated from tools/items/loot) and answers what any roster item
 * is: its spec by id or by stack, including items that existed before the roster (bandages, duct tape...).
 */
public final class LootItems {
    private static final Map<String, LootSpec> BY_ID = new HashMap<>();
    private static final Map<Item, LootSpec> BY_ITEM = new IdentityHashMap<>();
    private static final Map<String, Item> ITEMS = new HashMap<>();
    private static final List<Item> FOOD_TAB = new ArrayList<>(), OTHER_TAB = new ArrayList<>();
    private static boolean linked;

    public static void register() {
        Spoil.register();
        LightItem.register();
        InfoItem.register();
        for (LootSpec spec : LootSpecs.ALL) {
            BY_ID.put(spec.id(), spec);
        }
        // the leftovers first, so food can be told what it leaves
        for (LootSpec spec : LootSpecs.ALL) {
            if (spec.loot() && spec.food() == null) {
                item(spec);
            }
        }
        for (LootSpec spec : LootSpecs.ALL) {
            if (spec.loot() && spec.food() != null) {
                item(spec);
            }
        }
        ResourceKey<CreativeModeTab> food = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("food_and_drinks"));
        ResourceKey<CreativeModeTab> tools = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.withDefaultNamespace("tools_and_utilities"));
        CreativeModeTabEvents.modifyOutputEvent(food).register(output -> FOOD_TAB.forEach(output::accept));
        CreativeModeTabEvents.modifyOutputEvent(tools).register(output -> OTHER_TAB.forEach(output::accept));
    }

    private static void item(LootSpec spec) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(spec.id()));
        Item.Properties props = new Item.Properties().stacksTo(spec.stack()).setId(key);
        LootSpec.Food food = spec.food();
        Item item;
        if (Packs.has(spec.id())) {
            item = new PackItem(props, spec.id());
        } else if (LightItem.SPECS.containsKey(spec.id())) {
            item = new LightItem(props, LightItem.SPECS.get(spec.id()));
        } else if (FireItem.STRIKES.containsKey(spec.id())) {
            item = new FireItem(props, FireItem.STRIKES.get(spec.id()));
        } else if (InfoItem.KINDS.containsKey(spec.id())) {
            item = new InfoItem(props, InfoItem.KINDS.get(spec.id()));
        } else if (food == null) {
            item = new LootItem(props);
        } else if (food.is(LootSpec.Food.CAN)) {
            // a sealed can: nothing to eat until it's opened (SealedCanItem gives the opened stack its food)
            item = new SealedCanItem(props);
        } else {
            props.food(new FoodProperties(food.hunger(), food.saturation(), food.is(LootSpec.Food.ALWAYS)),
                    food.is(LootSpec.Food.DRINK) ? Consumables.DEFAULT_DRINK : Consumables.DEFAULT_FOOD);
            Item left = food.left() == null ? null : ITEMS.get(food.left());
            if (left != null) {
                props.usingConvertsTo(left);
            }
            item = new LootItem(props);
        }
        Registry.register(BuiltInRegistries.ITEM, key, item);
        ITEMS.put(spec.id(), item);
        BY_ITEM.put(item, spec);
        (spec.tab().equals("food") ? FOOD_TAB : OTHER_TAB).add(item);
    }

    /** Items that were in the mod before the roster are found by id once the registry is complete. */
    private static void link() {
        if (linked) {
            return;
        }
        linked = true;
        for (LootSpec spec : LootSpecs.ALL) {
            if (!spec.loot()) {
                BuiltInRegistries.ITEM.getOptional(ZomboidCraft.id(spec.id())).ifPresent(it -> BY_ITEM.put(it, spec));
            }
        }
    }

    public static @Nullable LootSpec spec(ItemStack stack) {
        return stack.isEmpty() ? null : spec(stack.getItem());
    }

    public static @Nullable LootSpec spec(Item item) {
        link();
        return BY_ITEM.get(item);
    }

    public static @Nullable LootSpec spec(String id) {
        return BY_ID.get(id);
    }

    public static @Nullable Item item(String id) {
        Item item = ITEMS.get(id);
        return item != null ? item : BuiltInRegistries.ITEM.getOptional(ZomboidCraft.id(id)).orElse(null);
    }

    private LootItems() {
    }
}
