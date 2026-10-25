package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Keeps the overworld populated with the dead, day and night: every ten seconds,
 * a player with few zombies around gets a small group spawned out of sight.
 */
public final class ZombieSpawner {
    private static final double NEARBY_RADIUS = 64;

    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZombieSpawner::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        ZcConfig.Spawning config = ZcConfig.get().spawning;
        if (++ticks % Math.max(20, config.intervalSeconds * 20) != 0 || !config.enabled) {
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
            if (nearby < config.nearbyCap) {
                int size = config.groupMin + player.getRandom().nextInt(Math.max(1, config.groupMax - config.groupMin + 1));
                spawnGroup(level, player, Math.min(config.nearbyCap - nearby, size), config);
            }
        }
    }

    private static void spawnGroup(ServerLevel level, ServerPlayer player, int count, ZcConfig.Spawning config) {
        RandomSource random = player.getRandom();
        double angle = random.nextDouble() * Math.PI * 2;
        int distance = config.minDistance + random.nextInt(Math.max(1, config.maxDistance - config.minDistance));
        int centerX = player.getBlockX() + (int) (Math.cos(angle) * distance);
        int centerZ = player.getBlockZ() + (int) (Math.sin(angle) * distance);

        for (int i = 0; i < count; i++) {
            BlockPos pos = null;
            for (int tries = 0; tries < 4 && pos == null; tries++) {
                int x = centerX + random.nextInt(7 + tries * 4) - 3 - tries * 2;
                int z = centerZ + random.nextInt(7 + tries * 4) - 3 - tries * 2;
                pos = findSpot(level, x, z, player.getBlockY());
            }
            if (pos != null) {
                Zombie zombie = EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
                if (zombie != null && random.nextInt(4) == 0) {
                    // Some of the horde is already moving toward the player's area.
                    ((NoiseListener) zombie).zomboidcraft$hear(player.position());
                }
            }
        }
    }

    /**
     * Somewhere near the player's own height first, so in a city they come up the street (or out
     * of the building next door) instead of landing on the rooftops; then the surface.
     */
    private static BlockPos findSpot(ServerLevel level, int x, int z, int playerY) {
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[] {-1, 1}) {
                BlockPos pos = new BlockPos(x, playerY + sign * dy, z);
                if (canSpawnAt(level, pos)) {
                    return pos;
                }
            }
        }
        BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
        return canSpawnAt(level, top) ? top : null;
    }

    private static boolean canSpawnAt(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.isLoaded(pos) || !level.getFluidState(below).isEmpty() || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        // something to stand on: a full top face, or a lowered one like the city's asphalt
        VoxelShape floor = level.getBlockState(below).getCollisionShape(level, below);
        boolean standable = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                || (!floor.isEmpty() && floor.max(Direction.Axis.Y) >= 0.5);
        return standable
                && !level.getBlockState(below).is(BlockTags.LEAVES)
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !perched(level, pos);
    }

    /**
     * True for a spot out under the sky that stands on something hollow: a roof, a treetop, the
     * top of a water tower. Indoors (anything over it) and solid ground outdoors are fine.
     */
    private static boolean perched(ServerLevel level, BlockPos pos) {
        if (pos.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ())) {
            return false;  // under a roof or a floor: inside a building
        }
        for (int k = 2; k <= 6; k++) {
            BlockPos at = pos.below(k);
            if (level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return true;  // air under the block it would stand on
            }
        }
        return false;
    }

    private ZombieSpawner() {
    }
}
