package com.zomboidcraft.carry.grid;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.carry.GearStore;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * What Tarkov's own worn slots take: a headset in the earpiece slot, an armband, body armour, and the special slots'
 * small kit. Items join by tag ({@code zomboidcraft:tarkov/<slot>}); the Tarkov gear batches add theirs.
 */
public final class TarkovSlots {
    public static final TagKey<Item> EARPIECE = tag("earpiece");
    public static final TagKey<Item> ARMBAND = tag("armband");
    public static final TagKey<Item> ARMOUR = tag("armour");
    public static final TagKey<Item> SPECIAL = tag("special");

    private TarkovSlots() {
    }

    private static TagKey<Item> tag(String path) {
        return TagKey.create(Registries.ITEM, ZomboidCraft.id("tarkov/" + path));
    }

    public static boolean fits(int gear, ItemStack stack) {
        if (gear == GearStore.EARPIECE) return stack.is(EARPIECE);
        if (gear == GearStore.ARMBAND) return stack.is(ARMBAND);
        if (gear == GearStore.ARMOUR) return stack.is(ARMOUR);
        if (gear >= GearStore.SPECIAL && gear < GearStore.SPECIAL + GearStore.SPECIAL_COUNT) return stack.is(SPECIAL);
        return false;
    }
}
