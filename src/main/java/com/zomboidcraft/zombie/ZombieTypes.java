package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;

/** The ordinary walking dead. Drowned and zombified piglins keep vanilla behaviour. */
public final class ZombieTypes {
    public static boolean isWalker(Entity entity) {
        if (!(entity instanceof Zombie)) {
            return false;
        }
        return isWalkerType(entity.getType());
    }

    public static boolean isWalkerType(EntityType<?> type) {
        return type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.ZOMBIE_VILLAGER;
    }

    /** A share of zombies run (one in twenty by default). Derived from the UUID so it never changes. */
    public static boolean isSprinter(Entity entity) {
        return Math.floorMod(entity.getUUID().getLeastSignificantBits(), 10000)
                < ZcConfig.get().zombies.sprinterChance * 10000;
    }

    private ZombieTypes() {
    }
}
