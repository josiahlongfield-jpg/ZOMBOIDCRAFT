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
            "baseball_cap", "beanie", "work_gloves", "sunglasses", "bandana",
            // batch 2: headwear, masks and glasses
            "trucker_cap", "cowboy_hat", "bucket_hat", "hard_hat", "bike_helmet", "football_helmet", "hockey_mask",
            "motorcycle_helmet", "police_cap", "fire_helmet", "riot_helmet", "gas_mask", "welding_goggles",
            "aviator_sunglasses", "reading_glasses", "balaclava", "surgical_mask_worn", "cranial_helmet", "pilot_helmet",
            // batch 3: tops and outerwear
            "band_tshirt", "tank_top", "dress_shirt", "polo_shirt", "hawaiian_shirt", "sweater", "sweatshirt",
            "turtleneck", "scrubs_top", "dress", "wedding_dress", "leather_jacket", "bomber_jacket", "varsity_jacket",
            "windbreaker", "raincoat", "trench_coat", "suit_jacket", "lab_coat", "hi_vis_vest",
            // batch 4: legs, feet, hands and the neck
            "cargo_pants", "camo_pants", "slacks", "sweatpants", "shorts", "skirt", "overalls", "scrubs_pants",
            "leather_chaps", "running_shoes", "dress_shoes", "cowboy_boots", "hiking_boots", "rubber_boots",
            "combat_boots", "slippers", "leather_gloves", "winter_gloves", "rubber_gloves", "tactical_gloves",
            "scarf", "necktie",
            // batch 5: uniforms, armour and aircrew
            "flight_deck_jersey", "police_jacket", "fire_coat", "field_jacket", "ghillie_suit", "ww2_flight_jacket",
            "fur_coat", "bespoke_suit", "poncho", "flak_vest", "police_vest", "survival_vest",
            "fire_gloves", "fire_pants", "g_suit");

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
