package com.zomboidcraft.world.cars;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/** One car's key (CarKeys): it starts that car, and its fob's panic button sets the car's alarm off from down the street. */
public class CarKeyItem extends Item {
    public CarKeyItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("Starts its own car and no other").withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.literal("Panic button: hold it and press Interact (G) away from the car, up to "
                + Math.round(com.zomboidcraft.ZcConfig.get().building.keyFobRange) + " blocks off").withStyle(ChatFormatting.DARK_GRAY));
    }
}
