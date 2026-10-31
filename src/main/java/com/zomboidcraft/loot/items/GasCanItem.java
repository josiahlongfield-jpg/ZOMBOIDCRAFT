package com.zomboidcraft.loot.items;

import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Zomboid's gas can: holds up to 10 L, filled at a pump or by siphoning a car, poured into a tank or a generator.
 * The fuel lives in CUSTOM_DATA "fuel" (litres, 0-10); a missing key is an empty can. Cans don't stack. Cars, pumps
 * and generators read and write it through {@link #fuel} and {@link #setFuel}.
 */
public class GasCanItem extends LootItem {
    public static final float CAPACITY = 10f;

    public GasCanItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static float fuel(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0f : Math.max(0f, Math.min(CAPACITY, data.copyTag().getFloatOr("fuel", 0f)));
    }

    /** Sets the fuel in litres (clamped to 0-10); an empty can drops the key so empty cans match. */
    public static void setFuel(ItemStack stack, float litres) {
        float l = Math.max(0f, Math.min(CAPACITY, litres));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (l < 0.05f) tag.remove("fuel");
            else tag.putFloat("fuel", l);
        });
    }

    @Override
    public Component getName(ItemStack stack) {
        float l = fuel(stack);
        String state = l < 0.05f ? "Empty" : l >= CAPACITY - 0.05f ? "Full" : String.format(Locale.ROOT, "%.1f L", l);
        return Component.translatable(getDescriptionId()).append(" (" + state + ")");
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return fuel(stack) >= 0.05f;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13f * fuel(stack) / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xC8A02A;
    }
}
