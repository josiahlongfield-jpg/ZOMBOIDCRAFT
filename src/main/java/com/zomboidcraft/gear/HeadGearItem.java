package com.zomboidcraft.gear;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Something worn on the head that night vision clips onto: a combat helmet or a bare head mount. Right-click it with
 * goggles to clip them on (or swap them), with nothing to take them off, with an AA battery to change the goggles'
 * battery. Worn, the N key flips the goggles down and up ({@link Headgear#flip}). A helmet with side rails also takes
 * a comms headset, the same way.
 */
public class HeadGearItem extends Item {
    private final boolean rails;

    public HeadGearItem(Properties properties) {
        this(properties, false);
    }

    public HeadGearItem(Properties properties, boolean rails) {
        super(properties);
        this.rails = rails;
    }

    /** Side rails that take a comms headset. */
    public boolean rails() {
        return rails;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || self.getCount() != 1) {
            return false;
        }
        if (other.isEmpty()) {
            // the goggles come off first, then the headset
            ItemStack off = Headgear.unmount(self);
            if (off.isEmpty()) {
                off = Headgear.unmountHeadset(self);
            }
            if (off.isEmpty()) {
                return false;
            }
            carried.set(off);
            player.playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 0.6f, 1.3f);
            return true;
        }
        if (other.getItem() instanceof NightVisionItem) {
            ItemStack old = Headgear.mount(self, other);
            other.shrink(1);
            if (!old.isEmpty()) {
                carried.set(old);
            }
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 1.2f);
            return true;
        }
        if (other.getItem() instanceof HeadsetItem && rails) {
            ItemStack old = Headgear.mountHeadset(self, other);
            other.shrink(1);
            if (!old.isEmpty()) {
                carried.set(old);
            }
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 1.0f);
            return true;
        }
        if (other.getItem() instanceof BatteryItem) {
            return Headgear.swapBattery(self, other, player);
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        ItemStack headset = Headgear.headset(stack);
        if (headset != null) {
            tooltip.accept(Component.literal(headset.getHoverName().getString() + " on the rails").withStyle(ChatFormatting.GRAY));
        }
        Headgear.Mounted m = Headgear.mounted(stack);
        if (m == null) {
            tooltip.accept(Component.literal(rails && headset == null ? "Has a mount for night vision and rails for a headset"
                    : "Has a mount for night vision").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        tooltip.accept(Component.literal(m.goggles().getHoverName().getString() + ", flipped " + (m.up() ? "up" : "down"))
                .withStyle(ChatFormatting.GRAY));
        if (com.zomboidcraft.ZcConfig.get().nightVision.batteries) {
            tooltip.accept(Component.literal(Headgear.chargeText(m.goggles()))
                    .withStyle(Headgear.charge(m.goggles()) > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        tooltip.accept(Component.literal("N flips it, right-click with nothing to take it off").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        Headgear.Mounted m = Headgear.mounted(stack);
        return m != null && com.zomboidcraft.ZcConfig.get().nightVision.batteries && Headgear.chargeFraction(m.goggles()) < 1f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        Headgear.Mounted m = Headgear.mounted(stack);
        return m == null ? 0 : Math.round(13f * Headgear.chargeFraction(m.goggles()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return NightVisionItem.BAR;
    }
}
