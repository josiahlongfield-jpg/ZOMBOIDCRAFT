package com.zomboidcraft.survival;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.component.Consumable;

/**
 * The quick-use wheel's other half: let go over a bandage or splint and it goes on the part that needs it most; over
 * food or a drink and you eat or drink it there and then, as a timed action. Anything else is just drawn (the
 * client picks the slot).
 */
public final class QuickUse {
    public static void use(ServerPlayer player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack.isEmpty()) return;
        if (Treatment.Kind.BANDAGE.is(stack)) {
            Treatment.start(player, Treatment.Kind.BANDAGE, null);
            return;
        }
        if (Treatment.Kind.SPLINT.is(stack)) {
            Treatment.start(player, Treatment.Kind.SPLINT, null);
            return;
        }
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null) return;
        boolean drink = consumable.animation() == ItemUseAnimation.DRINK;
        String name = stack.getHoverName().getString();
        ItemStack what = stack.copyWithCount(1);
        TimedActions.start(player, "consume:" + slot, (drink ? "Drinking " : "Eating ") + name,
                Math.max(10, Math.round(consumable.consumeSeconds() * 20)), p -> {
                    Inventory inventory = p.getInventory();
                    ItemStack now = inventory.getItem(slot);
                    if (!ItemStack.isSameItemSameComponents(now, what)) return;
                    Drinks.onConsumed(p, now.copy());
                    ItemStack left = now.finishUsingItem(p.level(), p);
                    if (left != now || now.isEmpty()) inventory.setItem(slot, left);
                    p.inventoryMenu.broadcastChanges();
                });
    }

    private QuickUse() {
    }
}
