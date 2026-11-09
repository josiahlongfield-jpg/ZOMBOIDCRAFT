package com.zomboidcraft.world.reach;

import com.google.gson.Gson;
import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Halo Reach world type ("zomboidcraft:reach", Josiah 2026-11-08): registers its generator and invisible-wall
 * block, puts a newcomer at the world's start spot, and lets everyone in it step up a little higher than usual (the
 * walls follow the level's floors in half-block quarters, so a slope or stair rises in small steps you walk up).
 *
 * <p>Everything Halo stays on the player's computer under config/zomboidcraft/reach (personal use only, never in
 * the mod): world/*.zrv are the walls, reach.json the start spot ({"spawn": [x, y, z, yaw]}).
 */
public final class ReachWorld {
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("zomboidcraft").resolve("reach");

    private static final ResourceKey<Block> SOLID_KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("reach_solid"));
    public static final Block SOLID = Registry.register(BuiltInRegistries.BLOCK, SOLID_KEY, new ReachSolid(BlockBehaviour.Properties.of()
            .strength(-1f, 3600000f).noOcclusion().noLootTable().noTerrainParticles().forceSolidOn()
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos, box) -> false).setId(SOLID_KEY)));

    /** Whether this player has been put at the start spot of this world yet. */
    public static final AttachmentType<Boolean> PLACED = AttachmentRegistry.create(ZomboidCraft.id("reach_placed"),
            builder -> builder.persistent(Codec.BOOL));

    private static final Identifier STEP = ZomboidCraft.id("reach_step");

    private static final class Settings {
        double[] spawn;
    }

    public static void register() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, ZomboidCraft.id("reach"), ReachChunkGenerator.CODEC);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            ServerPlayer player = handler.getPlayer();
            if (is(player.level()) && !player.getAttachedOrElse(PLACED, false)) {
                place(player);
                player.setAttached(PLACED, true);
            }
        }));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive && is(newPlayer.level())) {
                place(newPlayer);
            }
            if (oldPlayer.getAttachedOrElse(PLACED, false)) {
                newPlayer.setAttached(PLACED, true);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) {
                return;
            }
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                step(p, is(p.level()));
            }
        });
    }

    /** Whether the level is the Reach world. */
    public static boolean is(ServerLevel level) {
        return level.getChunkSource().getGenerator() instanceof ReachChunkGenerator;
    }

    private static void step(ServerPlayer p, boolean on) {
        AttributeInstance step = p.getAttribute(Attributes.STEP_HEIGHT);
        if (step == null) {
            return;
        }
        if (on) {
            step.addOrUpdateTransientModifier(new AttributeModifier(STEP, 0.15, AttributeModifier.Operation.ADD_VALUE));
        } else {
            step.removeModifier(STEP);
        }
    }

    /** The start spot from reach.json, or above the first walls near the middle of the world. */
    static double[] spawn() {
        Path f = DIR.resolve("reach.json");
        if (Files.isRegularFile(f)) {
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                Settings s = new Gson().fromJson(r, Settings.class);
                if (s != null && s.spawn != null && s.spawn.length >= 3) {
                    return s.spawn;
                }
            } catch (Exception e) {
                ZomboidCraft.LOGGER.error("Couldn't read {}", f, e);
            }
        }
        return new double[]{0.5, 100, 0.5, 0};
    }

    /** Puts the player at the start spot; with no walls under it (no level files yet), on a small invisible floor. */
    static void place(ServerPlayer player) {
        ServerLevel level = player.level();
        double[] s = spawn();
        BlockPos at = BlockPos.containing(s[0], s[1], s[2]);
        boolean ground = false;
        for (int dy = 1; dy <= 6 && !ground; dy++) {
            ground = !level.getBlockState(at.below(dy)).isAir();
        }
        if (!ground) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    level.setBlockAndUpdate(at.offset(dx, -1, dz), SOLID.defaultBlockState());
                }
            }
            player.sendSystemMessage(Component.literal("No Halo Reach level files found in " + DIR.resolve("world")
                    + " - standing on a small invisible floor."));
        }
        float yaw = s.length > 3 ? (float) s[3] : 0f;
        player.teleportTo(level, s[0], s[1], s[2], Set.of(), yaw, 0f, true);
        ZomboidCraft.LOGGER.info("Reach world: {} placed at {} {} {} ({} level files)", player.getName().getString(),
                s[0], s[1], s[2], ReachVoxels.get().files());
    }

    private ReachWorld() {
    }
}
