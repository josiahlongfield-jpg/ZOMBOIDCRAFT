package com.zomboidcraft;

import com.zomboidcraft.barricade.HammerItem;
import com.zomboidcraft.survival.BandageItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;

public final class ModItems {
    private static final List<Item> ALL = new ArrayList<>();

    public static final Item BANDAGE = register("bandage", BandageItem::new, new Item.Properties().stacksTo(16));
    public static final Item NAILS = register("nails", Item::new, new Item.Properties());
    public static final Item HAMMER = register("hammer", HammerItem::new,
            new Item.Properties().sword(ToolMaterial.STONE, 1.5f, -3.0f).durability(250));

    // Melee weapons. Blunt: bat, crowbar, hammer. Blade: knife, machete.
    public static final Item BASEBALL_BAT = register("baseball_bat", Item::new,
            new Item.Properties().sword(ToolMaterial.WOOD, 4.0f, -3.0f).durability(220));
    public static final Item CROWBAR = register("crowbar", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 3.0f, -2.8f).durability(400));
    public static final Item KITCHEN_KNIFE = register("kitchen_knife", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 0.5f, -1.6f).durability(150));
    public static final Item MACHETE = register("machete", Item::new,
            new Item.Properties().sword(ToolMaterial.IRON, 2.5f, -2.4f).durability(300));

    private static Item register(String name, Function<Item.Properties, Item> factory, Item.Properties properties) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
        ALL.add(item);
        return item;
    }

    public static void register() {
        ResourceKey<CreativeModeTab> combat = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
        CreativeModeTabEvents.modifyOutputEvent(combat).register(output -> ALL.forEach(output::accept));
    }

    private ModItems() {
    }
}
