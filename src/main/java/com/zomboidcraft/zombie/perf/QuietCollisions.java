package com.zomboidcraft.zombie.perf;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieTypes;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Walkers skip Minecraft's search for solid entities in their way while the world has none loaded
 * (performance.quietCollisions). Only boats, minecarts and shulkers are solid that way; our cars, lifts and trains
 * aren't, so with none about the search looked through every zombie nearby and came back empty, every move.
 */
public final class QuietCollisions {
    /** Per world, the loaded entities that other entities collide with. Server thread. */
    private static final Map<Level, Set<Entity>> SOLID = new IdentityHashMap<>();

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (solid(entity)) {
                SOLID.computeIfAbsent(level, l -> new ReferenceOpenHashSet<>()).add(entity);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            Set<Entity> set = SOLID.get(level);
            if (set != null) {
                set.remove(entity);
            }
        });
    }

    private static boolean solid(Entity e) {
        try {
            return e.canBeCollidedWith(null);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /** In place of the level's own entity collision search inside Entity.collide. */
    public static List<VoxelShape> collisions(Level level, Entity self, AABB box) {
        if (!level.isClientSide() && ZcConfig.get().performance.quietCollisions && ZombieTypes.isWalker(self)) {
            Set<Entity> set = SOLID.get(level);
            if (set == null || set.isEmpty()) {
                HordeStats.quiet++;
                return List.of();
            }
            set.removeIf(Entity::isRemoved);
            if (set.isEmpty()) {
                HordeStats.quiet++;
                return List.of();
            }
        }
        return level.getEntityCollisions(self, box);
    }

    private QuietCollisions() {
    }
}
