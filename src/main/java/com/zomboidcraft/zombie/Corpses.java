package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Deaths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The dead stay where they fall: a walker that dies falls for a moment (drawn by each client from combat.Deaths), then
 * its body (Corpse) takes its place, with no puff of smoke. The bodies loaded in each world are counted, and past
 * corpses.maxBodies the oldest go.
 */
public final class Corpses {
    private static final ResourceKey<EntityType<?>> KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("corpse"));
    public static final EntityType<Corpse> CORPSE = Registry.register(BuiltInRegistries.ENTITY_TYPE, KEY,
            EntityType.Builder.<Corpse>of(Corpse::new, MobCategory.MISC)
                    .sized(0.6f, 0.3f).clientTrackingRange(8).updateInterval(20).noLootTable()
                    .build(KEY));

    /** The bodies loaded in each world. Server thread only. */
    private static final Map<ServerLevel, List<Corpse>> LOADED = new WeakHashMap<>();
    /** A body came in since the count was last checked. */
    private static boolean added;

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Corpse corpse) {
                LOADED.computeIfAbsent(level, l -> new ArrayList<>()).add(corpse);
                added = true;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (added) {
                added = false;
                LOADED.values().forEach(Corpses::trim);
            }
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            if (entity instanceof Corpse corpse) {
                List<Corpse> bodies = LOADED.get(level);
                if (bodies != null) {
                    bodies.remove(corpse);
                }
            }
        });
    }

    /**
     * A dead walker's moment of falling (LivingEntityMixin, in place of vanilla's tickDeath): it stays a zombie for
     * Deaths.HOLD ticks while every client draws its fall, then its body takes its place. False for anything else,
     * which dies as vanilla has it.
     */
    public static boolean holds(LivingEntity entity) {
        if (!(entity instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie) || !ZcConfig.get().corpses.enabled) {
            return false;
        }
        zombie.deathTime++;
        if (zombie.level() instanceof ServerLevel level && !zombie.isRemoved()) {
            if (zombie.deathTime == Deaths.HOLD - 2) {
                lay(level, zombie);
            } else if (zombie.deathTime >= Deaths.HOLD) {
                zombie.remove(Entity.RemovalReason.KILLED);
            }
        }
        return true;
    }

    private static void lay(ServerLevel level, Zombie zombie) {
        long death = zombie.getAttachedOrElse(Deaths.DEATH, 0L);
        Corpse corpse = CORPSE.create(level, EntitySpawnReason.EVENT);
        if (corpse == null) {
            return;
        }
        corpse.copy(zombie, death);
        corpse.snapTo(zombie.getX(), zombie.getY(), zombie.getZ(), Deaths.yaw(death), 0f);
        level.addFreshEntity(corpse);
    }

    /** Past the most bodies a world keeps loaded, the oldest go. */
    private static void trim(List<Corpse> bodies) {
        int max = Math.max(0, ZcConfig.get().corpses.maxBodies);
        while (bodies.size() > max) {
            Corpse oldest = bodies.get(0);
            for (Corpse c : bodies) {
                if (c.laidAt < oldest.laidAt) {
                    oldest = c;
                }
            }
            bodies.remove(oldest);
            oldest.discard();
        }
    }

    /** Clears every body in a world (the command); how many went. */
    public static int clear(ServerLevel level) {
        List<Corpse> bodies = LOADED.get(level);
        if (bodies == null) {
            return 0;
        }
        List<Corpse> all = new ArrayList<>(bodies);
        all.forEach(Entity::discard);
        return all.size();
    }

    private Corpses() {
    }
}
