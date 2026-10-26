package com.zomboidcraft.gear;

import com.zomboidcraft.ZcConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Night vision goggles, which clip onto headgear: an old Gen 1 monocular, Gen 3 binoculars, or white phosphor ones that
 * show the night in grey-blue rather than green. Each runs on one AA battery. Right-click them while wearing headgear
 * to clip them on; right-click them in the inventory with a battery to change it.
 */
public class NightVisionItem extends Item {
    /** The battery bar: a pale tube green. */
    static final int BAR = 0x8CE07A;

    public enum Generation {
        GEN1("nvg_gen1", "Gen 1 tube: grainy and dim, one eye"),
        GEN3("nvg_gen3", "Gen 3 tubes: bright and clear"),
        WHITE("nvg_white", "White phosphor tubes: the sharpest view, grey-blue");

        /** The item's id, and the key the headgear's item model switches on. */
        public final String key;
        final String blurb;

        Generation(String key, String blurb) {
            this.key = key;
            this.blurb = blurb;
        }
    }

    private final Generation generation;

    public NightVisionItem(Generation generation, Properties properties) {
        super(properties);
        this.generation = generation;
    }

    public Generation generation() {
        return generation;
    }

    /** Right-click: clips them onto the helmet or head mount you're wearing, swapping off any already on it. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack self = player.getItemInHand(hand);
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        if (!(head.getItem() instanceof HeadGearItem)) {
            if (!level.isClientSide()) {
                ((net.minecraft.server.level.ServerPlayer) player).sendOverlayMessage(Component.literal("These clip onto a helmet or a head mount. Wear one first.")
                        .withStyle(ChatFormatting.GRAY));
            }
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            ItemStack old = Headgear.mount(head, self);
            player.setItemInHand(hand, old);
            player.playSound(SoundEvents.ITEM_FRAME_ADD_ITEM, 0.6f, 1.2f);
            ((net.minecraft.server.level.ServerPlayer) player).sendOverlayMessage(Component.literal("Clipped on. Press N to flip them down.").withStyle(ChatFormatting.GRAY));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack self, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess carried) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player) || !(other.getItem() instanceof BatteryItem)) {
            return false;
        }
        return Headgear.swapBattery(self, other, player);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal(generation.blurb).withStyle(ChatFormatting.GRAY));
        if (ZcConfig.get().nightVision.batteries) {
            tooltip.accept(Component.literal(stack.has(Headgear.CHARGE) ? Headgear.chargeText(stack) : "Battery: not checked yet")
                    .withStyle(Headgear.charge(stack) > 0 ? ChatFormatting.GRAY : ChatFormatting.RED));
        }
        tooltip.accept(Component.literal("Clips onto a helmet or head mount").withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ZcConfig.get().nightVision.batteries && stack.has(Headgear.CHARGE) && Headgear.chargeFraction(stack) < 1f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * Headgear.chargeFraction(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR;
    }
}
