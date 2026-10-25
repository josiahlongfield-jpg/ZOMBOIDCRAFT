package com.zomboidcraft.client.carry;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.carry.BagItem;
import com.zomboidcraft.carry.BagSpec;
import com.zomboidcraft.carry.Weights;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Weights on every tooltip, what a bag holds on its own, and worn bags drawn on players. */
public final class CarryClient {
    public static void register() {
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
            if (renderer instanceof AvatarRenderer<?> avatar) {
                helper.register(new WornGearLayer(avatar, context.getItemModelResolver()));
            }
        });
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (!ZcConfig.get().carry.weight || stack.isEmpty()) {
                return;
            }
            BagSpec bag = BagItem.spec(stack);
            if (bag != null) {
                lines.add(Component.literal(bag.slots() + " slots, holds " + Weights.format(bag.capacity())
                        + (bag.reduction() > 0 ? ", carries " + Math.round(bag.reduction() * 100) + "% lighter" : ""))
                        .withStyle(ChatFormatting.GRAY));
                lines.add(Component.literal("Worn: " + bag.wear().label + (bag.wear() == com.zomboidcraft.carry.Wear.HAND
                        ? " (takes your off hand)" : "")).withStyle(ChatFormatting.DARK_GRAY));
                double inside = Weights.contents(stack);
                if (inside > 0) {
                    lines.add(Component.literal("Inside: " + Weights.format(inside)).withStyle(ChatFormatting.GRAY));
                }
            }
            double each = Weights.unit(stack);
            String total = stack.getCount() > 1 ? "  (" + Weights.format(Weights.of(stack)) + " in all)" : "";
            lines.add(Component.literal("Weight " + Weights.format(each) + total).withStyle(ChatFormatting.DARK_GRAY));
        });
    }

    private CarryClient() {
    }
}
