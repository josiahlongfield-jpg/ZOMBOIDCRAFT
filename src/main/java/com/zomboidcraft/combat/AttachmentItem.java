package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
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

    /** Whether it fits this gun (see {@link GunSlotData}). */
    public boolean fits(ItemStack gun) {
        return Attachments.fits(gun, this);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        String colour = Attachments.colour(stack);
        if (colour != null) {
            tooltip.accept(Component.literal(colour.substring(0, 1).toUpperCase(Locale.ROOT) + colour.substring(1))
                    .withStyle(ChatFormatting.GRAY));
        }
        tooltip.accept(Component.literal(slotName(slot) + ". Fits: " + String.join(", ", gunsFitted(this)))
                .withStyle(ChatFormatting.GRAY));
        if (!ZcConfig.get().guns.attachments) {
            tooltip.accept(Component.literal("Attachments are turned off in settings").withStyle(ChatFormatting.DARK_RED));
        } else {
            tooltip.accept(Component.literal("Right-click a gun with this to fit it").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** The names of the guns a part fits, for tooltips. */
    static List<String> gunsFitted(Item part) {
        String id = BuiltInRegistries.ITEM.getKey(part).getPath();
        List<String> names = new ArrayList<>();
        GunSlotData.FITS.forEach((gun, slots) -> {
            if (slots.values().stream().anyMatch(parts -> parts.contains(id))) {
                names.add(BuiltInRegistries.ITEM.getValue(ZomboidCraft.id(gun)).getDefaultInstance().getHoverName().getString().toLowerCase(Locale.ROOT));
            }
        });
        names.sort(null);
        return names;
    }

    public static String slotName(Attachments.Slot slot) {
        return switch (slot) {
            case OPTIC -> "Optic";
            case MUZZLE -> "Muzzle";
            case UNDERBARREL -> "Underbarrel";
            case SIDE_RAIL -> "Light rail";
            case STOCK -> "Stock";
            case MAGAZINE -> "Magazine";
            case BIPOD -> "Bipod";
            case RIGHT_RAIL -> "Right rail";
            case MAGNIFIER -> "Magnifier";
        };
    }
}
