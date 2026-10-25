package com.zomboidcraft;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import java.util.List;

/** Adds survival supplies to every chest and to zombie drops. */
public final class Loot {
    public static void register() {
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            String path = table.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
            RandomSource random = context.getRandom();
            if (path.startsWith("chests/")) {
                addChestLoot(random, drops);
            } else if (path.equals("entities/zombie") || path.equals("entities/husk")
                    || path.equals("entities/zombie_villager")) {
                addZombieLoot(random, drops);
            }
        });
    }

    private static void addChestLoot(RandomSource random, List<ItemStack> drops) {
        maybe(random, drops, 0.35f, ModItems.BANDAGE, 1, 3);
        maybe(random, drops, 0.30f, ModItems.NAILS, 4, 12);
        maybe(random, drops, 0.10f, ModItems.DUCT_TAPE, 1, 2);
        maybe(random, drops, 0.05f, ModItems.WOOD_GLUE, 1, 1);
        maybe(random, drops, 0.06f, ModItems.HAMMER, 1, 1);
        maybe(random, drops, 0.06f, ModItems.BASEBALL_BAT, 1, 1);
        maybe(random, drops, 0.05f, ModItems.CROWBAR, 1, 1);
        maybe(random, drops, 0.06f, ModItems.KITCHEN_KNIFE, 1, 1);
        maybe(random, drops, 0.03f, ModItems.MACHETE, 1, 1);
        maybe(random, drops, 0.05f, ModItems.SCHOOL_BACKPACK, 1, 1);
        maybe(random, drops, 0.04f, ModItems.FANNY_PACK, 1, 1);
        maybe(random, drops, 0.025f, ModItems.HIKING_BACKPACK, 1, 1);
        maybe(random, drops, 0.02f, ModItems.DUFFEL_BAG, 1, 1);
        maybe(random, drops, 0.008f, ModItems.MILITARY_RUCKSACK, 1, 1);
        maybe(random, drops, 0.006f, ModItems.PLATE_CARRIER, 1, 1);
        if (random.nextFloat() < 0.25f) {
            drops.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
    }

    private static void addZombieLoot(RandomSource random, List<ItemStack> drops) {
        maybe(random, drops, 0.08f, ModItems.NAILS, 1, 3);
        maybe(random, drops, 0.04f, ModItems.BANDAGE, 1, 1);
        maybe(random, drops, 0.01f, ModItems.KITCHEN_KNIFE, 1, 1);
        maybe(random, drops, 0.03f, ModItems.DIGITAL_WATCH, 1, 1);
        maybe(random, drops, 0.015f, ModItems.DUCT_TAPE, 1, 1);
        // the dead still wear what they ran with
        maybe(random, drops, 0.015f, ModItems.SCHOOL_BACKPACK, 1, 1);
        maybe(random, drops, 0.01f, ModItems.FANNY_PACK, 1, 1);
        maybe(random, drops, 0.006f, ModItems.HIKING_BACKPACK, 1, 1);
        if (random.nextFloat() < 0.02f) {
            drops.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        }
    }

    private static void maybe(RandomSource random, List<ItemStack> drops, float chance, Item item, int min, int max) {
        if (random.nextFloat() < chance) {
            drops.add(new ItemStack(item, min + random.nextInt(max - min + 1)));
        }
    }

    private Loot() {
    }
}
