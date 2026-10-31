package com.zomboidcraft.loot.items;

import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Something to make fire with: a lighter, matches, a firestarter. It lights what flint and steel lights (campfires,
 * candles, fires on the ground), a strike at a time, and it's used up when its strikes are. Lanterns and candles
 * (LightItem) take a strike from one in your inventory to light.
 */
public class FireItem extends FlintAndSteelItem {
    static final java.util.Map<String, Integer> STRIKES = java.util.Map.of(
            "lighter", 60, "brass_lighter", 120, "matches", 20, "firestarter", 200);

    FireItem(Properties properties, int strikes) {
        super(properties.durability(strikes));
    }

    /** Takes a strike from the first thing to make fire with in the player's inventory. False when there's none. */
    static boolean strike(ServerPlayer sp) {
        if (sp.hasInfiniteMaterials()) {
            return true;
        }
        Inventory inv = sp.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof FlintAndSteelItem && s.isDamageableItem()) {
                s.setDamageValue(s.getDamageValue() + 1);
                if (s.getDamageValue() >= s.getMaxDamage()) {
                    s.shrink(1);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        LootItem.uses(getDescriptionId(), tooltip);
    }
}
