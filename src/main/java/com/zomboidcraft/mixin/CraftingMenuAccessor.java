package com.zomboidcraft.mixin;

import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The player's 2x2 result container, so the nearby menu can share the inventory's crafting grid. */
@Mixin(AbstractCraftingMenu.class)
public interface CraftingMenuAccessor {
    @Accessor("resultSlots")
    ResultContainer zomboidcraft$resultSlots();
}
