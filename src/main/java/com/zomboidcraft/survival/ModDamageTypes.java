package com.zomboidcraft.survival;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

public final class ModDamageTypes {
    public static final ResourceKey<DamageType> INFECTION = key("infection");
    public static final ResourceKey<DamageType> BLEEDING = key("bleeding");
    public static final ResourceKey<DamageType> THIRST = key("thirst");
    public static final ResourceKey<DamageType> HAIL = key("hail");
    /** Carrying a crushing load. */
    public static final ResourceKey<DamageType> STRAIN = key("strain");
    /** A rifle round. Ignores the hurt cooldown, so quick shots all land. */
    public static final ResourceKey<DamageType> BULLET = key("bullet");
    /** A jet's cannon shells, the Apache's, or a bomb (WorldEvents' air strikes); counts as an explosion. */
    public static final ResourceKey<DamageType> AIR_STRIKE = key("air_strike");

    private static ResourceKey<DamageType> key(String name) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ZomboidCraft.id(name));
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key) {
        return level.damageSources().source(key);
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> key, Entity attacker) {
        return level.damageSources().source(key, attacker);
    }

    private ModDamageTypes() {
    }
}
