package com.zomboidcraft.loot.items;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A plain roster item. Its tooltip says what it's for (the roster's use line, lang key item.zomboidcraft.ID.use,
 * written by tools/items/loot/gen_java.py), wrapped so it doesn't run across the screen.
 */
public class LootItem extends Item {
    private static final int WIDTH = 40;

    public LootItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        uses(getDescriptionId(), tooltip);
    }

    static void uses(String descriptionId, Consumer<Component> tooltip) {
        String key = descriptionId + ".use";
        Language lang = Language.getInstance();
        if (!lang.has(key)) {
            return;
        }
        StringBuilder line = new StringBuilder();
        for (String word : lang.getOrDefault(key).split(" ")) {
            if (line.length() > 0 && line.length() + 1 + word.length() > WIDTH) {
                tooltip.accept(Component.literal(line.toString()).withStyle(ChatFormatting.DARK_GRAY));
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            tooltip.accept(Component.literal(line.toString()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
