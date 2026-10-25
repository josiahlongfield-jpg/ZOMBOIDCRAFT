package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The assault rifle. Hold right-click to aim down the sights, left-click to fire, R to reload.
 * Firing and reloading run on the server (see {@link Guns}); this class is the item's own side:
 * aiming is "using" the item, which the item model turns into the sight picture.
 */
public class RifleItem extends Item {
    public RifleItem(Properties properties) {
        super(properties);
    }

    public static int capacity() {
        return Math.max(1, ZcConfig.get().guns.magazineSize);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    /** Rounds going down, the condition wearing and the flash don't make the gun bob as if swapped. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        GunState gun = stack.getOrDefault(GunState.TYPE, GunState.EMPTY_MAG);
        tooltip.accept(gun.mag()
                ? Component.literal("Magazine: " + gun.rounds() + "/" + capacity()).withStyle(ChatFormatting.GRAY)
                : Component.literal("No magazine").withStyle(ChatFormatting.DARK_RED));
        if (gun.jammed()) {
            tooltip.accept(Component.literal("Jammed: press R to clear").withStyle(ChatFormatting.RED));
        }
        tooltip.accept(MeleeWeaponItem.condition(stack));
        tooltip.accept(Component.literal("Right-click: aim   Left-click: fire   R: reload").withStyle(ChatFormatting.DARK_GRAY));
    }
}
