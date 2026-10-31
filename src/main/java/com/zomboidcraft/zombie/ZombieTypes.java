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
        return isWalkerType(entity.getType());
    }

    public static boolean isWalkerType(EntityType<?> type) {
        return type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK || type == EntityTypes.ZOMBIE_VILLAGER;
    }

    /** Runs once it spots you: zombieLore's speed, rolled from the UUID so it never changes (Lore.pace). */
    public static boolean isSprinter(Entity entity) {
        return Lore.pace(entity) == Lore.Pace.SPRINTER;
    }

    private ZombieTypes() {
    }
}
