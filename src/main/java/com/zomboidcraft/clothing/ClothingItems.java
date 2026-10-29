package com.zomboidcraft.clothing;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.Equippable;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;

/**
 * Registers the clothing that has its models made (tools/items/gen_clothes.py), batch by batch. A row of
 * {@link Clothes} without a model stays out until its batch lands, so nothing turns up as a missing-texture cube.
 */
public final class ClothingItems {
    /** Ids made so far, in the order they were built. */
    public static final List<String> MADE = List.of(
            // batch 1: everyday basics
            "t_shirt", "flannel_shirt", "hoodie", "denim_jacket", "parka", "jeans", "work_pants", "sneakers", "work_boots",
            "baseball_cap", "beanie", "work_gloves", "sunglasses", "bandana");

    private static final List<Item> ITEMS = new ArrayList<>();

    public static void register() {
        for (String id : MADE) {
            Garment g = Clothes.byId(id);
            if (g == null) {
                throw new IllegalStateException("No clothing row for " + id);
            }
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(id));
            Item.Properties props = new Item.Properties().stacksTo(1).setId(key);
            if (g.slot().armour != null) {
                // no equipment asset: the vanilla armour layer draws nothing, ClothingLayer draws the garment
                props.component(DataComponents.EQUIPPABLE, Equippable.builder(g.slot().armour)
                        .setEquipSound(SoundEvents.ARMOR_EQUIP_LEATHER).setDamageOnHurt(false).build());
                if (g.armour() > 0) {
                    props.attributes(ItemAttributeModifiers.builder().add(Attributes.ARMOR,
                            new AttributeModifier(ZomboidCraft.id("clothing_" + id), g.armour(), AttributeModifier.Operation.ADD_VALUE),
                            EquipmentSlotGroup.bySlot(g.slot().armour)).build());
                }
            }
            ITEMS.add(Registry.register(BuiltInRegistries.ITEM, key, new ClothingItem(props)));
        }
        ResourceKey<CreativeModeTab> combat = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("combat"));
        CreativeModeTabEvents.modifyOutputEvent(combat).register(output -> ITEMS.forEach(output::accept));
    }

    private ClothingItems() {
    }
}
