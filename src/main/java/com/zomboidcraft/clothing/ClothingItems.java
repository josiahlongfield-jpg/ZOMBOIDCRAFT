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
            "fire_gloves", "fire_pants", "g_suit",
            // wave 2, military batch 1 (the rigs are bags: ModItems)
            "high_cut_helmet", "steel_pot_helmet", "tanker_helmet", "boonie_hat", "patrol_cap", "beret",
            "ballistic_glasses", "ballistic_goggles", "shemagh", "neck_gaiter", "elbow_pads", "knee_pads",
            "field_shirt", "combat_trousers", "tactical_boots",
            // wave 2, military batch 2: cold, wet, CBRN, aircrew and riot
            "fur_ushanka", "watch_cap", "cbrn_mask", "cbrn_suit", "combat_shirt", "fleece_liner", "wet_weather_jacket",
            "flight_coverall", "sniper_smock", "arctic_parka", "snow_overwhites", "jungle_boots", "desert_boots",
            "arctic_boots", "flight_gloves", "shooting_gloves", "arctic_mittens", "riot_arm_guards", "riot_leg_guards",
            "wool_commando_sweater",
            // wave 2, batch 3: useful kit, pads and sport armour
            "shooting_earmuffs", "chainsaw_chaps", "beekeeper_suit", "cut_resistant_gloves",
            "shin_guards", "gaiters", "football_shoulder_pads", "hockey_shoulder_pads", "hockey_neck_guard",
            "leather_neck_guard", "padded_football_pants", "hockey_pants", "catchers_vest", "hockey_gloves",
            "chainmail_butcher_glove", "chainmail_sleeve", "athletic_cup",
            // wave 2, batch 4: civilian
            "puffer_jacket", "ski_jacket", "ski_trousers", "ski_goggles", "cardigan", "blazer", "gilet", "tracksuit_top",
            "tracksuit_bottoms", "bathrobe", "rugby_shirt", "chef_jacket", "mechanic_coveralls", "prison_jumpsuit",
            "security_shirt", "flat_cap", "fedora", "straw_hat", "fingerless_gloves", "sandals",
            // wave 2, batch 5: Zomboid's headwear, face and eyes
            "ranger_hat", "peaked_cap", "captains_hat", "chefs_hat", "surgical_cap", "visor_cap", "sweatband",
            "winter_earmuffs", "miner_helmet", "batting_helmet", "hockey_helmet", "riding_helmet", "boxing_headgear",
            "swat_helmet", "welder_mask", "dust_mask", "half_mask_respirator", "safety_goggles", "eyepatch", "monocle",
            "prescription_glasses",
            // wave 2, batch 6: the rest of Zomboid's wardrobe, and survivor-made scrap and tyre armour
            "hunting_vest", "foreman_vest", "waistcoat", "leather_biker_vest", "sheepskin_vest", "sheepskin_jacket",
            "padded_jacket", "hunting_jacket", "paramedic_jacket", "apron", "leather_apron", "long_johns", "socks",
            "legwarmers", "riding_boots", "bowling_shoes", "flip_flops", "boxing_gloves", "pyjama_top", "pyjama_bottoms",
            "sports_jersey", "postal_shirt", "cycling_jersey", "cycling_shorts", "wetsuit", "fishing_waders",
            "scrap_cuirass", "tyre_cuirass", "scrap_arm_guards", "scrap_leg_guards", "scrap_helmet", "hunting_cap",
            // wave 2, batch 7: prized and grail (the black crusader kit is our own design)
            "crusader_great_helm", "crusader_plate", "crusader_tabard", "crusader_pauldrons", "crusader_cloak",
            "crusader_greaves", "crusader_gauntlets", "crusader_sabatons", "gothic_sallet", "gothic_cuirass",
            "gothic_arm_harness", "gothic_leg_harness", "gothic_gauntlets", "gothic_sabatons", "bomb_disposal_suit",
            "bomb_disposal_helmet", "shark_mesh_suit", "motorcycle_leathers", "scba_set", "scba_mask", "bodyguard_suit");

    /** What ripping clothes up gives, beside the ripped sheets the loot roster already has (clothing/Tailoring). */
    public static final List<String> SUPPLIES = List.of("denim_strips", "leather_strips");

    private static final List<Item> ITEMS = new ArrayList<>();

    public static void register() {
        ClothWear.register();
        ClothingLoot.register();
        Grime.register();
        for (String id : SUPPLIES) {
            ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, ZomboidCraft.id(id));
            ITEMS.add(Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().stacksTo(16).setId(key))));
        }
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
