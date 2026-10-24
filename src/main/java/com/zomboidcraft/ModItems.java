package com.zomboidcraft;

import com.zomboidcraft.survival.BandageItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final Item BANDAGE = register("bandage", new Item.Properties().stacksTo(16));

    private static Item register(String name, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new BandageItem(properties.setId(key)));
    }

    public static void register() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(BANDAGE));
    }

    private ModItems() {
    }
}
