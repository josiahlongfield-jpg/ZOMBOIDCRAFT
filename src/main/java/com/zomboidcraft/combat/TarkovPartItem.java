package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A Tarkov part in hand (combat/TarkovParts): its bench slot and look come from its id. Without this computer's files
 * it fits nothing.
 */
public class TarkovPartItem extends AttachmentItem {
    public TarkovPartItem(Properties properties) {
        super(Attachments.Slot.OPTIC, "tarkov", properties);
    }

    @Override
    public boolean fits(ItemStack gun) {
        return false;       // which gun takes which part is the part's own (Attachments.offered), not the item's
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        TarkovParts.Part p = TarkovParts.part(stack);
        if (p == null) {
            tooltip.accept(Component.literal("A Tarkov part this computer has no file for").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<String> guns = LocalGuns.all().stream().filter(s -> TarkovFit.reaches(s, TarkovParts.of(stack)))
                .map(s -> s.name != null ? s.name : s.id).sorted().toList();
        String slot = p.slot() == null ? "Mount" : slotName(p.slot());
        tooltip.accept(Component.literal(slot + ". Fits: " + (guns.isEmpty() ? "nothing yet" : String.join(", ", guns)))
                .withStyle(ChatFormatting.GRAY));
        if (p.aims != null && !p.aims.isEmpty() && p.slot() == Attachments.Slot.OPTIC) {
            StringBuilder b = new StringBuilder();
            for (TarkovParts.Aim a : p.aims) {
                for (TarkovParts.Mode m : a.modes) {
                    String z = m.zoom == Math.round(m.zoom) ? String.valueOf(Math.round(m.zoom)) : String.format(Locale.ROOT, "%.2g", m.zoom);
                    String one = z + "x";
                    if (b.indexOf(one) < 0) b.append(b.length() == 0 ? "" : ", ").append(one);
                }
            }
            tooltip.accept(Component.literal("Sight: " + b).withStyle(ChatFormatting.GRAY));
        }
        if (p.capacity > 0) tooltip.accept(Component.literal(p.capacity + " rounds").withStyle(ChatFormatting.GRAY));
        if (!ZcConfig.get().guns.attachments) {
            tooltip.accept(Component.literal("Attachments are turned off in settings").withStyle(ChatFormatting.DARK_RED));
        } else {
            tooltip.accept(Component.literal("Fit it at the bench").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
