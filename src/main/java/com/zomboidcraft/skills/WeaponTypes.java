package com.zomboidcraft.skills;

import com.zomboidcraft.ModItems;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

public final class WeaponTypes {
    public static boolean isBlunt(ItemStack stack) {
        return stack.is(ModItems.BASEBALL_BAT) || stack.is(ModItems.CROWBAR) || stack.is(ModItems.HAMMER)
                || stack.is(ItemTags.SHOVELS);
    }

    public static boolean isBlade(ItemStack stack) {
        return stack.is(ModItems.KITCHEN_KNIFE) || stack.is(ModItems.MACHETE)
                || stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES);
    }

    /** The skill that governs a weapon, or null for fists and odd items. */
    public static Skill skillFor(ItemStack stack) {
        return isBlunt(stack) ? Skill.BLUNT : isBlade(stack) ? Skill.BLADE : null;
    }

    private WeaponTypes() {
    }
}
