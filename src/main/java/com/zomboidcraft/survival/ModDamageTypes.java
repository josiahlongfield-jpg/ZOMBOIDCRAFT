package com.zomboidcraft.survival;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> INFECTION = key("infection");
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
    public static final ResourceKey<DamageType> THIRST = key("thirst");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ZomboidCraft.id(name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key) {
        return level.damageSources().source(key);
    }

    private ModDamageTypes() {
    }
}
