package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Keeps the overworld populated with the dead, day and night: every ten seconds,
 * a player with few zombies around gets a small group spawned out of sight.
 */
public final class ZombieSpawner {
    private static final double NEARBY_RADIUS = 64;
    /** The overworld's natural ground height by column, worked out once (server thread only). */
    private static final Map<Long, Integer> GROUND = new HashMap<>();

    private static int ticks;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ZombieSpawner::onServerTick);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> GROUND.clear());   // another world, another ground
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
                String uniform = zombie == null ? null : uniform(level, pos, random);
                if (uniform != null) {
                    zombie.setAttached(ZombieLook.OUTFIT, uniform);
                }
                if (zombie != null && random.nextInt(4) == 0) {
                    // Some of the horde is already moving toward the player's area.
                    ((NoiseListener) zombie).zomboidcraft$hear(player.position());
                }
            }
        }
    }

    /**
     * A test horde (/zc horde): this many zombies scattered between half and one and a half times the distance around
     * the player, dressed for where they stand. Returns how many found room.
     */
    public static int spawnAround(ServerLevel level, ServerPlayer player, int count, int distance) {
        RandomSource random = player.getRandom();
        int made = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = null;
            for (int tries = 0; tries < 6 && pos == null; tries++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double r = distance * (0.5 + random.nextDouble());
                pos = findSpot(level, player.getBlockX() + (int) (Math.cos(angle) * r),
                        player.getBlockZ() + (int) (Math.sin(angle) * r), player.getBlockY());
            }
            Zombie zombie = pos == null ? null : EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
            if (zombie != null) {
                String uniform = uniform(level, pos, random);
                if (uniform != null) {
                    zombie.setAttached(ZombieLook.OUTFIT, uniform);
                }
                made++;
            }
        }
        return made;
    }

    /**
     * A horde for filming (/zc cinematic horde): this many zombies in a wedge up ahead, where the player is looking,
     * between 0.7 and 1.3 times the distance away and dressed for where they stand. Returns how many found room.
     */
    public static int spawnAhead(ServerLevel level, ServerPlayer player, int count, int distance) {
        RandomSource random = player.getRandom();
        double facing = Math.toRadians(player.getYRot());
        int made = 0;
        for (int i = 0; i < count; i++) {
            BlockPos pos = null;
            for (int tries = 0; tries < 6 && pos == null; tries++) {
                double angle = facing + (random.nextDouble() - 0.5) * Math.toRadians(50);
                double r = distance * (0.7 + random.nextDouble() * 0.6);
                pos = findSpot(level, player.getBlockX() - (int) Math.round(Math.sin(angle) * r),
                        player.getBlockZ() + (int) Math.round(Math.cos(angle) * r), player.getBlockY());
            }
            Zombie zombie = pos == null ? null : EntityTypes.ZOMBIE.spawn(level, pos, EntitySpawnReason.EVENT);
            if (zombie != null) {
                String uniform = uniform(level, pos, random);
                if (uniform != null) {
                    zombie.setAttached(ZombieLook.OUTFIT, uniform);
                }
                made++;
            }
        }
        return made;
    }

    /**
     * The dead dressed for where they died: most of the crew aboard the carrier and on the naval
     * wharf are soldiers, so are the army base and depot, and the quarantine camp is soldiers and
     * patients; the stadium shelter fell with its guards and the sick it took in, and Union
     * Station's camp with the soldiers who ran it and its field hospital's patients. Null leaves the
     * zombie to the general mix.
     */
    public static String uniform(ServerLevel level, BlockPos pos, RandomSource random) {
        if (onShip(level, pos)) {
            return random.nextInt(100) < 85 ? "soldier" : null;
        }
        if (!(level.getChunkSource().getGenerator() instanceof CountyChunkGenerator)) {
            return null;
        }
        int roll = random.nextInt(100);
        for (Place p : CountyChunkGenerator.gen(level.getSeed()).county().placesNear(pos.getX(), pos.getZ(), pos.getX(), pos.getZ(), 0)) {
            if (p.type == PlaceType.PORT && p.variant.equals("naval")) {
                return roll < 80 ? "soldier" : null;
            }
            if (p.type == PlaceType.MILITARY) {
                if (p.variant.equals("quarantine")) {
                    return roll < 40 ? "soldier" : roll < 70 ? "patient" : null;
                }
                return roll < 70 ? "soldier" : null;
            }
            if (p.type == PlaceType.LANDMARK && p.variant.equals("stadium")) {
                return roll < 20 ? "soldier" : roll < 45 ? "patient" : null;
            }
            if (p.type == PlaceType.LANDMARK && p.variant.equals("union_station")) {
                return roll < 20 ? "soldier" : roll < 50 ? "patient" : null;
            }
        }
        return null;
    }

    /** Standing on the carrier: her decks and compartments are all navy blocks. */
    private static boolean onShip(ServerLevel level, BlockPos pos) {
        return standingOn(level, pos).startsWith("navy_");
    }

    /** Standing on the flight deck itself (its plating, markings, catapults and wires), not the island's roof. */
    private static boolean onFlightDeck(ServerLevel level, BlockPos pos) {
        String name = standingOn(level, pos);
        return name.startsWith("navy_fl") || name.startsWith("navy_catapult") || name.startsWith("navy_cable");
    }

    private static String standingOn(ServerLevel level, BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.below()).getBlock()).getPath();
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
                && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                && !perched(level, pos);
    }

    /**
     * True for a spot no zombie should turn up on: in a tree, or out under the sky well above the ground the world
     * was made with (a roof, the top of a water tower or a wall). The ground itself, indoors at any height (a
     * ceiling over it, not just leaves) and the carrier's flight deck are fine. Natural spawns are held to it too
     * (MobMixin).
     */
    public static boolean perched(ServerLevel level, BlockPos pos) {
        if (tree(level.getBlockState(pos.below()))) {
            return true;
        }
        if (pos.getY() <= ground(level, pos) + 2 || onFlightDeck(level, pos)) {
            return false;
        }
        for (int dy = 2; dy <= 24; dy++) {
            BlockPos at = pos.above(dy);
            BlockState state = level.getBlockState(at);
            VoxelShape shape = state.getCollisionShape(level, at);
            if (!shape.isEmpty() && !tree(state)
                    && shape.bounds().getXsize() >= 0.75 && shape.bounds().getZsize() >= 0.75) {
                return false;  // a floor or roof over it (not a wire or a branch): upstairs in a building
            }
        }
        return true;
    }

    /** Our trees' bark, limbs and leaves, and vanilla leaves. */
    private static boolean tree(BlockState state) {
        return state.is(BlockTags.LEAVES) || BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("tree_");
    }

    /** Where the ground the world was made with lies here, before anything was built or grew on it: the first block above it. */
    private static int ground(ServerLevel level, BlockPos pos) {
        ServerChunkCache chunks = level.getChunkSource();
        if (level.dimension() != Level.OVERWORLD) {
            return chunks.getGenerator().getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level,
                    chunks.randomState());
        }
        if (GROUND.size() > 65536) {
            GROUND.clear();
        }
        return GROUND.computeIfAbsent(((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL), k -> chunks.getGenerator()
                .getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, level, chunks.randomState()));
    }

    private ZombieSpawner() {
    }
}
