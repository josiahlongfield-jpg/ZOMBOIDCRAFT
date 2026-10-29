package com.zomboidcraft.carry;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/**
 * What a survivor wears beyond armour: a pack, a vest and a waist bag, the belt, holster, sheath and watch that
 * open quick slots (see {@link QuickSlots}), and the clothing layers the armour slots don't hold (glasses, mask,
 * neck, shirt and gloves). Lives on the player.
 */
public final class GearStore {
    public static final int BACK = 0;
    public static final int VEST = 1;
    public static final int WAIST = 2;
    public static final int BELT = 3;
    public static final int HOLSTER = 4;
    public static final int SHEATH = 5;
    public static final int WRIST = 6;
    /** Clothing layers beyond the armour slots (see clothing/ClothingSlot). */
    public static final int EYES = 7;
    public static final int FACE = 8;
    public static final int NECK = 9;
    public static final int SHIRT = 10;
    public static final int HANDS = 11;
    public static final int SIZE = 12;

    public static final Codec<GearStore> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(GearStore::new, GearStore::list);

    final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
    /**
     * A weapon from a save made before the quick slots, when slot 3 was the sling: it moves to the Back quick slot the
     * next time the player is ticked.
     */
    ItemStack fromSling = ItemStack.EMPTY;

    public GearStore() {
    }

    private GearStore(List<ItemStack> saved) {
        for (int i = 0; i < Math.min(SIZE, saved.size()); i++) {
            items.set(i, saved.get(i));
        }
        if (saved.size() == 4 && !saved.get(3).isEmpty() && !QuickSlots.belt(saved.get(3))) {
            fromSling = saved.get(3);
            items.set(BELT, ItemStack.EMPTY);
        }
    }

    private List<ItemStack> list() {
        if (!fromSling.isEmpty()) {
            // saved before it moved: keep it on the old sling place so it isn't lost
            List<ItemStack> out = new java.util.ArrayList<>(items);
            out.set(BELT, fromSling);
            return out.subList(0, 4);
        }
        return List.copyOf(items);
    }

    public ItemStack get(int slot) {
        return items.get(slot);
    }

    /** Empties the store, handing each worn piece to the caller (for dropping on death). */
    public void takeAll(java.util.function.Consumer<ItemStack> sink) {
        if (!fromSling.isEmpty()) {
            sink.accept(fromSling);
            fromSling = ItemStack.EMPTY;
        }
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                items.set(i, ItemStack.EMPTY);
                sink.accept(stack);
            }
        }
    }

    public static int slotFor(Wear wear) {
        return switch (wear) {
            case BACK -> BACK;
            case VEST -> VEST;
            case WAIST -> WAIST;
            case HAND -> -1;
        };
    }
}
