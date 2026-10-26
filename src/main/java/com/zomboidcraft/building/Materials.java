package com.zomboidcraft.building;

import com.zomboidcraft.carry.Carry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** What a player has on them to build with: hands, belt, pockets and whatever's in their worn bags. */
public final class Materials {
    public static List<Container> sources(Player player) {
        List<Container> out = new ArrayList<>();
        out.add(player.getInventory());
        if (player.inventoryMenu instanceof Carry.Bags bags) {
            for (var bag : bags.zomboidcraft$bags()) {
                if (bag.spec() != null) out.add(bag);
            }
        }
        return out;
    }

    public static int count(Player player, Predicate<ItemStack> fits) {
        int n = 0;
        for (Container c : sources(player)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!stack.isEmpty() && fits.test(stack)) n += stack.getCount();
            }
        }
        return n;
    }

    /** The first stack that fits, for a tool to wear down. */
    public static ItemStack find(Player player, Predicate<ItemStack> fits) {
        if (fits.test(player.getMainHandItem())) return player.getMainHandItem();
        for (Container c : sources(player)) {
            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack stack = c.getItem(i);
                if (!stack.isEmpty() && fits.test(stack)) return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Takes that many; returns false (taking nothing) if there aren't enough. */
    public static boolean take(Player player, Predicate<ItemStack> fits, int count) {
        if (count(player, fits) < count) return false;
        for (Container c : sources(player)) {
            for (int i = 0; i < c.getContainerSize() && count > 0; i++) {
                ItemStack stack = c.getItem(i);
                if (stack.isEmpty() || !fits.test(stack)) continue;
                int taken = Math.min(count, stack.getCount());
                c.removeItem(i, taken);
                count -= taken;
            }
            c.setChanged();
        }
        return true;
    }

    private Materials() {
    }
}
