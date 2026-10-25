package com.zomboidcraft.carry;

import java.util.function.Supplier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The inside of whatever bag is worn in one place, as a container. It keeps a working copy of the bag's contents
 * and writes it back to the bag stack on every change, so the bag can be taken off at any moment with its load.
 */
public final class BagContainer implements Container {
    private final Wear wear;
    private final Supplier<ItemStack> source;
    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    private ItemStack bound = ItemStack.EMPTY;
    private ItemContainerContents boundContents;

    public BagContainer(Wear wear, Supplier<ItemStack> source) {
        this.wear = wear;
        this.source = source;
    }

    public Wear wear() {
        return wear;
    }

    /** The bag worn here, or EMPTY. */
    public ItemStack bag() {
        ItemStack stack = source.get();
        return BagItem.spec(stack) != null ? stack : ItemStack.EMPTY;
    }

    /** The worn bag's spec, or null when nothing is worn here. */
    public BagSpec spec() {
        return BagItem.spec(bag());
    }

    /** Re-reads the contents when a different bag is worn or something else changed this one. */
    private void sync() {
        ItemStack bag = bag();
        ItemContainerContents contents = bag.get(DataComponents.CONTAINER);
        if (bag == bound && contents == boundContents) {
            return;
        }
        bound = bag;
        boundContents = contents;
        BagSpec spec = BagItem.spec(bag);
        items = NonNullList.withSize(spec == null ? 0 : spec.slots(), ItemStack.EMPTY);
        if (contents != null) {
            contents.copyInto(items);
        }
    }

    private void writeBack() {
        if (bound.isEmpty() || bound != bag()) {
            return;
        }
        ItemContainerContents contents = ItemContainerContents.fromItems(items);
        bound.set(DataComponents.CONTAINER, contents);
        boundContents = bound.get(DataComponents.CONTAINER);
    }

    /** Raw kilograms in the bag, leaving out one slot (pass -1 to count them all). */
    public double contentsWeight(int except) {
        sync();
        double w = 0;
        for (int i = 0; i < items.size(); i++) {
            if (i != except) {
                w += Weights.of(items.get(i));
            }
        }
        return w;
    }

    public int usableSlots() {
        sync();
        return items.size();
    }

    @Override
    public int getContainerSize() {
        return BagSpec.maxSlots(wear);
    }

    @Override
    public boolean isEmpty() {
        sync();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        sync();
        return slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        sync();
        if (slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = ContainerHelper.removeItem(items, slot, count);
        writeBack();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        sync();
        if (slot >= items.size()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = ContainerHelper.takeItem(items, slot);
        writeBack();
        return taken;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        sync();
        if (slot >= items.size()) {
            return;
        }
        items.set(slot, stack);
        writeBack();
    }

    @Override
    public void setChanged() {
        sync();
        writeBack();
    }

    /** Catches changes made to a stack in place without telling the container. */
    void flush() {
        if (bound.isEmpty() || bound != bag()) {
            return;
        }
        ItemContainerContents now = ItemContainerContents.fromItems(items);
        if (!now.equals(bound.get(DataComponents.CONTAINER))) {
            bound.set(DataComponents.CONTAINER, now);
            boundContents = bound.get(DataComponents.CONTAINER);
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        sync();
        items.replaceAll(stack -> ItemStack.EMPTY);
        writeBack();
    }
}
