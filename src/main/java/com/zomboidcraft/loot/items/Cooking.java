package com.zomboidcraft.loot.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Cooking our own raw food (no vanilla cooked meat: Josiah 2026-11-02): a steak, chops, a chicken or fish fillets over a
 * stove or a fire come back the same item marked cooked and hot, named "Cooked ...", and no longer make you sick.
 */
public final class Cooking {
    public static boolean raw(ItemStack s) {
        LootSpec spec = LootItems.spec(s);
        return spec != null && spec.food() != null && spec.food().is(LootSpec.Food.RAW) && !cooked(s);
    }

    public static boolean cooked(ItemStack s) {
        CustomData data = s.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr("cooked", false);
    }

    /** The cooked version of a raw stack, `count` of them. */
    public static ItemStack cook(ItemStack raw, int count) {
        ItemStack out = raw.copyWithCount(Math.max(1, Math.min(count, raw.getMaxStackSize())));
        Component name = raw.getItem().getName(raw);
        CustomData.update(DataComponents.CUSTOM_DATA, out, tag -> {
            tag.putBoolean("cooked", true);
            tag.putBoolean("hot", true);
        });
        out.set(DataComponents.CUSTOM_NAME, Component.literal("Cooked ").append(name).withStyle(s -> s.withItalic(false)));
        return out;
    }

    private Cooking() {
    }
}
