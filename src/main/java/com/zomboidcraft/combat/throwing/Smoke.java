package com.zomboidcraft.combat.throwing;

import com.zomboidcraft.ZcConfig;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Smoke-grenade clouds, which zombies can't see through: Sight asks {@link #blocks} before spotting anyone, and a
 * zombie already chasing someone it can no longer see loses them and goes to search where they were.
 */
public final class Smoke {
    private record Cloud(ResourceKey<Level> dimension, Vec3 at, double radius, long until) {
    }

    private static final Map<Long, Cloud> CLOUDS = new HashMap<>();
    private static int ticks;

    private Smoke() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(Smoke::tick);
    }

    /** A grenade's cloud this tick (it calls every tick while it smokes). */
    static void hide(ServerLevel level, Vec3 at, double radius) {
        if (radius > 0.5) {
            CLOUDS.put(BlockPos.containing(at).asLong(), new Cloud(level.dimension(), at, radius, level.getGameTime() + 5));
        }
    }

    /** Whether a cloud stands between from and to. */
    public static boolean blocks(Level level, Vec3 from, Vec3 to) {
        if (CLOUDS.isEmpty()) {
            return false;
        }
        for (Cloud c : CLOUDS.values()) {
            if (c.dimension == level.dimension() && level.getGameTime() <= c.until && crosses(c, from, to)) {
                return true;
            }
        }
        return false;
    }

    /** The line from-to passes through the thick of the cloud (or starts or ends inside it). */
    private static boolean crosses(Cloud c, Vec3 from, Vec3 to) {
        Vec3 d = to.subtract(from);
        double len = d.lengthSqr();
        double t = len < 1e-6 ? 0 : Math.max(0, Math.min(1, c.at.subtract(from).dot(d) / len));
        double thick = c.radius * 0.85;
        return from.add(d.scale(t)).distanceToSqr(c.at) <= thick * thick;
    }

    private static void tick(MinecraftServer server) {
        if (CLOUDS.isEmpty() || ++ticks % 5 != 0) {
            return;
        }
        double close = ZcConfig.get().senses.closeRange;
        for (ServerLevel level : server.getAllLevels()) {
            long now = level.getGameTime();
            CLOUDS.values().removeIf(c -> c.dimension == level.dimension() && now > c.until);
            for (Cloud c : CLOUDS.values()) {
                if (c.dimension != level.dimension()) {
                    continue;
                }
                for (Zombie z : level.getEntitiesOfClass(Zombie.class, new AABB(c.at, c.at).inflate(c.radius + 40))) {
                    LivingEntity target = z.getTarget();
                    if (target instanceof Player && z.distanceTo(target) > close && crosses(c, z.getEyePosition(), target.getEyePosition())) {
                        z.setTarget(null);
                    }
                }
            }
        }
    }
}
