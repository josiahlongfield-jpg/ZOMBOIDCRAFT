package com.zomboidcraft.loot.items;

import com.zomboidcraft.decor.Decor;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** A decor piece in the hand: set it down like a block ({@link com.zomboidcraft.decor.DecorBlock}), with its look. */
public class DecorItem extends BlockItem {
    public DecorItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        LootItem.uses(getDescriptionId(), tooltip);
    }

    /** The decor block for this roster id, or null if it has none. */
    static Block blockFor(String id) {
        return Decor.block(id);
    }
}
