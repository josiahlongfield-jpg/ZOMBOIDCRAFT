package com.zomboidcraft.client.kit;

import com.zomboidcraft.survival.Foods;
import com.zomboidcraft.survival.character.Traits;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Food on a tooltip, as Zomboid shows it: everyone sees how much hunger it takes off; a Nutritionist also sees its
 * calories, carbs, fats and proteins.
 */
public final class FoodTips {
    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            Foods.Value v = stack.isEmpty() ? null : Foods.of(stack);
            if (v == null) return;
            lines.add(Component.literal("Hunger -" + Math.round(v.hunger() * 100)).withStyle(ChatFormatting.GRAY));
            var player = Minecraft.getInstance().player;
            if (player != null && Traits.has(player, "nutritionist")) {
                lines.add(Component.literal(Foods.describe(v)).withStyle(ChatFormatting.DARK_GREEN));
            }
        });
    }

    private FoodTips() {
    }
}
