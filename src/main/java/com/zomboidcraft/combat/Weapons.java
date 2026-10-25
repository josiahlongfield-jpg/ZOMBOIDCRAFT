package com.zomboidcraft.combat;

import com.zomboidcraft.skills.Skill;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Looks up the stats of the weapon in a stack. */
public final class Weapons {
    private static final Map<String, WeaponSpecs.WeaponSpec> BY_NAME = new HashMap<>();
    private static final Map<Item, WeaponSpecs.WeaponSpec> BY_ITEM = new HashMap<>();

    static {
        for (WeaponSpecs.WeaponSpec spec : WeaponSpecs.ALL) {
            BY_NAME.put(spec.name(), spec);
        }
    }

    public static WeaponSpecs.WeaponSpec byName(String name) {
        WeaponSpecs.WeaponSpec spec = BY_NAME.get(name);
        if (spec == null) {
            throw new IllegalArgumentException("No weapon spec " + name);
        }
        return spec;
    }

    public static @Nullable WeaponSpecs.WeaponSpec spec(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        return BY_ITEM.computeIfAbsent(stack.getItem(), item -> {
            String path = BuiltInRegistries.ITEM.getKey(item).getPath();
            return item instanceof MeleeWeaponItem ? BY_NAME.get(path) : null;
        });
    }

    public static @Nullable Skill skill(ItemStack stack) {
        WeaponSpecs.WeaponSpec spec = spec(stack);
        return spec == null ? null : spec.skill();
    }

    private Weapons() {
    }
}
