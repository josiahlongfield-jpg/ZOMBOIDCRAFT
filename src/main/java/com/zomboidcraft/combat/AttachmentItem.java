package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A gun attachment: which slot it takes, which guns it fits, and the key the gun's item model
 * switches on when it's mounted (see {@link Attachments}).
 */
public class AttachmentItem extends Item {
    private final Attachments.Slot slot;
    private final String modelKey;

    public AttachmentItem(Attachments.Slot slot, String modelKey, Properties properties) {
        super(properties);
        this.slot = slot;
        this.modelKey = modelKey;
    }

    public Attachments.Slot slot() {
        return slot;
    }

    /** The name the gun's item model selects on, e.g. "red_dot". */
    public String modelKey() {
        return modelKey;
    }

    /** Whether it fits this gun. Every attachment so far fits the assault rifle. */
    public boolean fits(ItemStack gun) {
        return gun.getItem() instanceof RifleItem;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal(slotName(slot) + ". Fits: assault rifle").withStyle(ChatFormatting.GRAY));
        if (!ZcConfig.get().guns.attachments) {
            tooltip.accept(Component.literal("Attachments are turned off in settings").withStyle(ChatFormatting.DARK_RED));
        } else {
            tooltip.accept(Component.literal("Right-click a rifle with this to mount it").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    public static String slotName(Attachments.Slot slot) {
        return switch (slot) {
            case OPTIC -> "Optic";
            case MUZZLE -> "Muzzle";
            case UNDERBARREL -> "Underbarrel";
            case STOCK -> "Stock";
        };
    }
}
