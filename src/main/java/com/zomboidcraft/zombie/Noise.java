package com.zomboidcraft.zombie;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Zombies hunt by sound: anything loud draws every idle zombie in earshot. */
public final class Noise {
    public static final double WALK = 5;
    public static final double SPRINT = 14;
    public static final double ATTACK = 10;
    public static final double BREAK_BLOCK = 12;

    public static void emit(ServerLevel level, Vec3 pos, double radius) {
        if (radius <= 0) {
            return;
        }
        double radiusSqr = radius * radius;
        for (Zombie zombie : level.getEntitiesOfClass(Zombie.class, new AABB(pos, pos).inflate(radius))) {
            if (ZombieTypes.isWalker(zombie) && zombie.getTarget() == null
                    && zombie.position().distanceToSqr(pos) <= radiusSqr) {
                ((NoiseListener) zombie).zomboidcraft$hear(pos);
            }
        }
    }

    private Noise() {
    }
}
