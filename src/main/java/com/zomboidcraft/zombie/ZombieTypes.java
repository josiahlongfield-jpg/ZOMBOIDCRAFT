package com.zomboidcraft.zombie;

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
        EntityType<?> type = entity.getType();
        return type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.ZOMBIE_VILLAGER;
    }

    /** About one in twenty zombies runs. Derived from the UUID so it never changes. */
    public static boolean isSprinter(Entity entity) {
        return Math.floorMod(entity.getUUID().getLeastSignificantBits(), 20) == 0;
    }

    private ZombieTypes() {
    }
}
