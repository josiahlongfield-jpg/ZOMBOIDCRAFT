package com.zomboidcraft.carry;

import com.mojang.serialization.Codec;
import java.util.List;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

/** What a survivor wears beyond armour: a pack, a vest, a waist bag and a long gun on a sling. Lives on the player. */
public final class GearStore {
    public static final int BACK = 0;
    public static final int VEST = 1;
    public static final int WAIST = 2;
    public static final int SLING = 3;
    public static final int SIZE = 4;

    public static final Codec<GearStore> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(GearStore::new, GearStore::list);

    final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public GearStore() {
    }

    private GearStore(List<ItemStack> saved) {
        for (int i = 0; i < Math.min(SIZE, saved.size()); i++) {
            items.set(i, saved.get(i));
        }
    }

    private List<ItemStack> list() {
        return List.copyOf(items);
    }

    public ItemStack get(int slot) {
        return items.get(slot);
    }

    /** Empties the store, handing each worn piece to the caller (for dropping on death). */
    public void takeAll(java.util.function.Consumer<ItemStack> sink) {
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
