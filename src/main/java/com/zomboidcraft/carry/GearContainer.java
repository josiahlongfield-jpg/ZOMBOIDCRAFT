package com.zomboidcraft.carry;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The player's gear slots as a container for the inventory menu. Looks the store up each time, so respawns are fine. */
public final class GearContainer implements Container {
    private final Player player;

    public GearContainer(Player player) {
        this.player = player;
    }

    private GearStore store() {
        return Carry.gear(player);
    }

    @Override
    public int getContainerSize() {
        return GearStore.SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : store().items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return store().items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(store().items, slot, count);
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(store().items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        store().items.set(slot, stack);
        setChanged();
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        store().items.replaceAll(stack -> ItemStack.EMPTY);
    }
}
