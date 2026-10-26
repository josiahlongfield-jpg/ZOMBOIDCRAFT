package com.zomboidcraft.carry;

import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * What things weigh, in kilograms, Zomboid style. The mod's own items have their own numbers; everything else is
 * guessed from what it is, kept light enough that building with Minecraft-sized stacks stays possible.
 */
public final class Weights {
    private static final Map<String, Double> EXACT = new HashMap<>();
    private static final TagKey<Item> INGOTS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots"));
    private static final TagKey<Item> NUGGETS = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "nuggets"));

    static {
        mod("bandage", 0.05);
        mod("nails", 0.01);
        mod("duct_tape", 0.15);
        mod("wood_glue", 0.2);
        mod("gun_oil", 0.15);
        mod("gun_parts", 0.25);
        mod("street_map", 0.05);
        mod("road_atlas", 0.3);
        mod("bunker_map", 0.05);
        mod("hammer", 0.7);
        mod("kitchen_knife", 0.3);
        mod("frying_pan", 1.2);
        mod("crowbar", 1.5);
        mod("pipe_wrench", 1.4);
        mod("lead_pipe", 1.3);
        mod("golf_club", 0.6);
        mod("hockey_stick", 0.8);
        mod("baseball_bat", 1.1);
        mod("spiked_bat", 1.3);
        mod("shovel", 1.8);
        mod("machete", 0.8);
        mod("katana", 1.2);
        mod("fire_axe", 2.2);
        mod("sledgehammer", 4.5);
        mod("assault_rifle", 3.0);
        mod("rifle_magazine", 0.5);
        mod("rifle_ammo", 0.012);
        mod("red_dot_sight", 0.25);
        mod("holo_sight", 0.3);
        mod("scope_4x", 0.45);
        mod("rifle_suppressor", 0.5);
        mod("foregrip", 0.15);
        mod("weapon_light", 0.2);
        mod("laser_sight", 0.15);
        mod("rifle_stock", 0.6);
        mod("pistol", 0.95);
        mod("pistol_magazine", 0.25);
        mod("pistol_ammo", 0.008);
        mod("pistol_suppressor", 0.3);
        mod("shotgun", 3.4);
        mod("shotgun_shell", 0.04);
        mod("double_barrel", 3.2);
        mod("revolver", 1.0);
        mod("revolver_ammo", 0.011);
        mod("hunting_rifle", 3.6);
        mod("hunting_rifle_magazine", 0.2);
        mod("hunting_rifle_ammo", 0.025);
        mod("rimfire", 2.3);
        mod("rimfire_magazine", 0.1);
        mod("rimfire_ammo", 0.0035);
        mod("smg", 2.5);
        mod("smg_magazine", 0.17);
        mod("sawn_off", 2.3);
        mod("magnum", 1.35);
        mod("magnum_ammo", 0.022);
        mod("lever_action", 3.0);
        mod("lever_ammo", 0.025);
        mod("crossbow", 3.2);
        mod("crossbow_bolt", 0.031);
        vanilla("stick", 0.1);
        vanilla("arrow", 0.03);
        vanilla("bow", 0.8);
        vanilla("crossbow", 2.5);
        vanilla("shield", 3.0);
        vanilla("potion", 0.5);
        vanilla("water_bucket", 4.0);
        vanilla("bucket", 0.6);
        vanilla("torch", 0.1);
        vanilla("coal", 0.1);
        vanilla("flint_and_steel", 0.2);
        vanilla("string", 0.01);
        vanilla("paper", 0.01);
        vanilla("book", 0.4);
        vanilla("anvil", 30.0);
    }

    private static void mod(String name, double kg) {
        EXACT.put(ZomboidCraft.MOD_ID + ":" + name, kg);
    }

    private static void vanilla(String name, double kg) {
        EXACT.put("minecraft:" + name, kg);
    }

    /** Weight of one of this item, ignoring what a bag holds. */
    public static double unit(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        BagSpec bag = BagItem.spec(stack);
        if (bag != null) {
            return bag.weight();
        }
        Double exact = EXACT.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (exact != null) {
            return exact;
        }
        if (stack.is(ItemTags.SWORDS)) return 1.2;
        if (stack.is(ItemTags.AXES)) return 1.8;
        if (stack.is(ItemTags.PICKAXES)) return 2.2;
        if (stack.is(ItemTags.SHOVELS)) return 1.5;
        if (stack.is(ItemTags.HOES)) return 1.0;
        if (stack.has(DataComponents.EQUIPPABLE)) return 1.5;
        if (stack.has(DataComponents.FOOD)) return 0.3;
        if (stack.is(INGOTS)) return 0.3;
        if (stack.is(NUGGETS)) return 0.03;
        if (stack.getItem() instanceof BlockItem) {
            if (stack.is(ItemTags.LOGS)) return 2.0;
            if (stack.is(ItemTags.PLANKS)) return 0.6;
            if (stack.is(ItemTags.WOOL) || stack.is(ItemTags.LEAVES)) return 0.2;
            return 0.4;
        }
        return 0.1;
    }

    /** What a stack weighs on you: count times unit, plus a bag's contents less what the bag takes off. */
    public static double of(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        double w = unit(stack) * stack.getCount();
        BagSpec bag = BagItem.spec(stack);
        if (bag != null) {
            w += contents(stack) * (1 - bag.reduction());
        }
        return w;
    }

    /** Raw weight of what a bag holds. */
    public static double contents(ItemStack bag) {
        ItemContainerContents contents = bag.get(DataComponents.CONTAINER);
        if (contents == null) {
            return 0;
        }
        double w = 0;
        for (ItemStack inside : (Iterable<ItemStack>) contents.nonEmptyItemCopyStream()::iterator) {
            w += of(inside);
        }
        return w;
    }

    /** "1.25 kg" with sensible rounding. */
    public static String format(double kg) {
        if (kg < 0.095) {
            return String.format("%.2f kg", kg);
        }
        return String.format(kg < 10 ? "%.1f kg" : "%.0f kg", kg);
    }

    private Weights() {
    }
}
