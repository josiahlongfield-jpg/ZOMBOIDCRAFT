package com.zomboidcraft.world;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * Only the dead walk the overworld: creepers, skeletons, spiders and the other vanilla monsters
 * are removed as they appear (setting spawning.vanillaMonsters). Named or otherwise persistent
 * mobs are left alone, and so are the Nether and the End.
 */
public final class MobFilter {
    private static final List<Entity> doomed = new ArrayList<>();

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!ZcConfig.get().spawning.vanillaMonsters && level.dimension() == Level.OVERWORLD
                    && entity instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER
                    && !ZombieTypes.isWalker(mob) && !mob.isPersistenceRequired()) {
                doomed.add(mob);  // removing an entity while it is being added is unsafe; do it after the tick
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (!doomed.isEmpty()) {
                doomed.forEach(Entity::discard);
                doomed.clear();
            }
        });
    }

    private MobFilter() {
    }
}
