package com.zomboidcraft.loot.items;

import com.zomboidcraft.combat.rolls.Rarity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * One place to ask what any item is worth and how rare it is: a weapon's own rarity (combat/rolls) first, then the
 * loot roster's. Value is barter worth, 1 = a can of beans; spoilt food is worth less.
 */
public final class ItemInfo {
    public static @Nullable Rarity rarity(ItemStack stack) {
        Rarity weapon = Rarity.of(stack);
        if (weapon != null) {
            return weapon;
        }
        LootSpec spec = LootItems.spec(stack);
        return spec == null ? null : spec.rarity();
    }

    public static int value(ItemStack stack) {
        LootSpec spec = LootItems.spec(stack);
        if (spec == null) {
            return 0;
        }
        Spoil spoil = stack.get(Spoil.TYPE);
        if (spoil != null && spoil.state() != Spoil.FRESH) {
            return spoil.state() == Spoil.ROTTEN ? 0 : spec.value() / 2;
        }
        return spec.value();
    }

    /** Kilograms for one, or -1 when the roster doesn't know it. */
    public static double kg(ItemStack stack) {
        LootSpec spec = LootItems.spec(stack);
        return spec == null ? -1 : spec.kg();
    }

    /** The kit's Supplies tab: food, medical, household, tools, materials, electronics, books, keys, valuables. */
    public static @Nullable String tab(ItemStack stack) {
        LootSpec spec = LootItems.spec(stack);
        return spec == null ? null : spec.tab();
    }

    private ItemInfo() {
    }
}
