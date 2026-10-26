package com.zomboidcraft.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * A rail-mount comms headset, which clips onto a combat helmet's side rails. With it on, what comes over your radios
 * plays in your ears and nobody else's (and the zombies don't hear it), you can talk on a radio that isn't in your
 * hand, and gunshots are dulled. Right-click it while wearing the helmet to clip it on.
 */
public class HeadsetItem extends Item {
    public HeadsetItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!Headgear.hasRails(head)) {
            if (player instanceof ServerPlayer sp) {
                sp.sendOverlayMessage(Component.literal("This clips onto a combat helmet's rails. Wear one first.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        if (player instanceof ServerPlayer sp) {
            ItemStack old = Headgear.mountHeadset(head, player.getItemInHand(hand));
            player.setItemInHand(hand, old);
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 1.0f);
            sp.sendOverlayMessage(Component.literal("Headset on. Your radios play in your ears now.").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Your radios play in your ears, and nobody else's").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Talk on a radio that isn't in your hand; dulls gunshots").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Clips onto a combat helmet's rails").withStyle(ChatFormatting.DARK_GRAY));
    }
}
