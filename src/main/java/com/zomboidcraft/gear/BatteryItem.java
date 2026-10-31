package com.zomboidcraft.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** An AA battery. Right-click with one to change the battery in the night vision you're wearing. */
public class BatteryItem extends Item {
    public BatteryItem(Properties properties) {
        super(properties);
    }

    /** How many AA batteries' worth one of these lasts: a military radio battery pack runs four times as long. */
    public static int lasts(ItemStack battery) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(battery.getItem()).getPath()
                .equals("radio_battery_pack") ? 4 : 1;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (Headgear.mounted(head) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            Headgear.swapBattery(head, player.getItemInHand(hand), player);
            ((net.minecraft.server.level.ServerPlayer) player).sendOverlayMessage(Component.literal("Fresh battery in.").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Powers night vision for about " + Math.round(com.zomboidcraft.ZcConfig.get().nightVision.batteryMinutes)
                + " minutes").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Right-click it onto goggles or a helmet with them on").withStyle(ChatFormatting.DARK_GRAY));
    }
}
