package com.zomboidcraft.zombie;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Keeps the overworld populated with the dead, day and night: every ten seconds,
 * a player with few zombies around gets a small group spawned out of sight.
 */
public final class ZombieSpawner {
    private static final int INTERVAL_TICKS = 200;
    private static final int NEARBY_CAP = 24;
    private static final double NEARBY_RADIUS = 64;
    private static final int MIN_DISTANCE = 32;
    private static final int MAX_DISTANCE = 56;

    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZombieSpawner::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        if (++ticks % INTERVAL_TICKS != 0) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            if (level.dimension() != Level.OVERWORLD || level.getDifficulty() == Difficulty.PEACEFUL
                    || player.isSpectator()) {
                continue;
            }
            int nearby = level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(NEARBY_RADIUS),
                    ZombieTypes::isWalker).size();
            if (nearby < NEARBY_CAP) {
                spawnGroup(level, player, Math.min(NEARBY_CAP - nearby, 3 + player.getRandom().nextInt(5)));
            }
        }
    }

    private static void spawnGroup(ServerLevel level, ServerPlayer player, int count) {
        RandomSource random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE);
        int centerX = player.getBlockX() + (int) (Math.cos(angle) * distance);
        int centerZ = player.getBlockZ() + (int) (Math.sin(angle) * distance);

        for (int i = 0; i < count; i++) {
            int x = centerX + random.nextInt(7) - 3;
            int z = centerZ + random.nextInt(7) - 3;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            if (canSpawnAt(level, pos)) {
                Zombie zombie = EntityType.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
                if (zombie != null && random.nextInt(4) == 0) {
                    // Some of the horde is already moving toward the player's area.
                    ((NoiseListener) zombie).zomboidcraft$hear(player.position());
                }
            }
        }
    }

    private static boolean canSpawnAt(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.isLoaded(pos)
                && level.getFluidState(below).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty();
    }

    private ZombieSpawner() {
    }
}
