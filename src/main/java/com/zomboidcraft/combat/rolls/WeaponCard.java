package com.zomboidcraft.combat.rolls;

import com.zomboidcraft.ZcConfig;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Asks the client to draw a weapon's tooltip as the kit screen's card instead of lines of text. */
public record WeaponCard(ItemStack stack) implements TooltipComponent {
    public static Optional<TooltipComponent> of(ItemStack stack) {
        return ZcConfig.get().look.weaponCards && Rarity.of(stack) != null ? Optional.of(new WeaponCard(stack)) : Optional.empty();
    }
}
