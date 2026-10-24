package com.zomboidcraft.survival;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

/** How much thirst each consumable restores. */
public final class Drinks {
    public static void onConsumed(Player player, ItemStack stack) {
        float value = thirstValue(stack);
        if (value > 0) {
            SurvivalStats stats = SurvivalStats.get(player);
            SurvivalStats.set(player, stats.withThirst(stats.thirst() + value));
        }
    }

    public static float thirstValue(ItemStack stack) {
        if (stack.is(Items.POTION)) {
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            return contents != null && contents.is(Potions.WATER) ? 40f : 20f;
        }
        if (stack.is(Items.MILK_BUCKET)) return 30f;
        if (stack.is(Items.MUSHROOM_STEW) || stack.is(Items.BEETROOT_SOUP)
                || stack.is(Items.RABBIT_STEW) || stack.is(Items.SUSPICIOUS_STEW)) return 15f;
        if (stack.is(Items.HONEY_BOTTLE) || stack.is(Items.MELON_SLICE)) return 10f;
        if (stack.is(Items.APPLE)) return 6f;
        if (stack.is(Items.SWEET_BERRIES) || stack.is(Items.GLOW_BERRIES)) return 4f;
        return 0f;
    }

    private Drinks() {
    }
}
