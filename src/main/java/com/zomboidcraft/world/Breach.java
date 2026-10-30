package com.zomboidcraft.world;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.CommandContext;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.creature.HarvestmanDirector;
import com.zomboidcraft.world.county.BreachSite;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * The breach under Ridgeview Labs never quite closed (Josiah, 2026-10-29). Every so often, at night, the
 * ring wakes: the cracks in its plug flare from orange to white, a hum comes up through the mountain and
 * the ground shakes. With enemy respawning on (population respawnDays) each of these is a surge, one
 * every respawnDays days: the dead pour out of it, so places cleared before it fill again (Population)
 * and a Harvestman lost since the last one is replaced (HarvestmanDirector). With respawning off the
 * ring only flickers for a few seconds every day or three and nothing comes through.
 *
 * <p>The surge is seen in the ring hall and felt round ground zero: the hum and the tremor reach
 * 400 blocks, weaker with distance. Saved to zomboidcraft_breach.json.
 */
public final class Breach {
    /** The shaking a player feels: how hard (0..1) and for how many ticks. */
    public record Tremor(float strength, int ticks) implements CustomPacketPayload {
        public static final Type<Tremor> TYPE = new Type<>(ZomboidCraft.id("breach_tremor"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tremor> CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Tremor::strength, ByteBufCodecs.VAR_INT, Tremor::ticks, Tremor::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private static final int SURGE_TICKS = 1200, FLICKER_TICKS = 160;
    /** How far the hum and the tremor carry. */
    private static final double FELT = 400;

    private static CompletableFuture<BreachSite.Found> site;
    private static long next, activeUntil, lastSurgeDay = -1;
    private static boolean surging, dirty, loaded;

    private Breach() {
    }

    /** The day of the last surge (-1: none yet); places cleared before it are due their refill. */
    public static long lastSurgeDay() {
        return lastSurgeDay;
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Tremor.TYPE, Tremor.CODEC);
        ServerLifecycleEvents.SERVER_STARTED.register(s -> {
            ServerLevel level = s.overworld();
            site = level.getChunkSource().getGenerator() instanceof CountyChunkGenerator
                    ? CompletableFuture.supplyAsync(() -> BreachSite.find(CountyChunkGenerator.gen(level.getSeed())))
                    : CompletableFuture.completedFuture(null);
            load(s);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            if (activeUntil > 0) {
                glow(s.overworld(), false);
                activeUntil = 0;
            }
            save(s);
            loaded = false;
            site = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(Breach::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
                Commands.literal("zc")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("breach")
                                .executes(Breach::status)
                                .then(Commands.literal("surge").executes(ctx -> force(ctx, true)))
                                .then(Commands.literal("flicker").executes(ctx -> force(ctx, false))))));
    }

    /** The breach of this world, once it's been found (null: none, or not yet). */
    public static BreachSite.Found site() {
        return site != null && site.isDone() && !site.isCompletedExceptionally() ? site.join() : null;
    }

    /** Whether the ring is surging right now (not a flicker). */
    public static boolean surging() {
        return activeUntil > 0 && surging;
    }

    private static void tick(MinecraftServer s) {
        if (!loaded) {
            return;
        }
        ServerLevel level = s.overworld();
        long now = level.getGameTime();
        if (next == 0) {
            next = schedule(level, now);
        }
        if (activeUntil == 0 && now >= next) {
            start(level, ZcConfig.get().population.respawnDays > 0);
        }
        if (activeUntil > 0) {
            during(level, now);
        } else if (dirty && now % 200 == 0) {
            // a surge cut off by a crash leaves the cracks white: put them back once they're loaded
            BreachSite.Found b = site();
            if (b != null && level.isLoaded(new BlockPos(b.x(), b.y(), b.z()))) {
                glow(level, false);
                dirty = false;
            }
        }
    }

    /** The next one: respawnDays days on for a surge, one to three for a flicker; in the night either way. */
    private static long schedule(ServerLevel level, long now) {
        int days = ZcConfig.get().population.respawnDays;
        if (days <= 0) {
            days = 1 + level.getRandom().nextInt(3);
        }
        return (now / 24000 + days) * 24000 + 13500 + level.getRandom().nextInt(9000);
    }

    private static void start(ServerLevel level, boolean surge) {
        long now = level.getGameTime();
        surging = surge;
        activeUntil = now + (surge ? SURGE_TICKS : FLICKER_TICKS);
        next = schedule(level, now);
        dirty = true;
        if (surge) {
            lastSurgeDay = now / 24000;
            HarvestmanDirector.breachSurge(level);
            ZomboidCraft.LOGGER.info("Breach surge on day {}", lastSurgeDay);
        }
        save(level.getServer());
        BreachSite.Found b = site();
        if (b == null) {
            return;
        }
        Vec3 c = new Vec3(b.x() + 0.5, b.y() + 0.5, b.z() + 0.5);
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(c);
            if (d > FELT) {
                continue;
            }
            float near = (float) (1 - d / FELT);
            ServerPlayNetworking.send(p, new Tremor((surge ? 0.4f + 0.6f * near : 0.25f * near), surge ? 140 : 40));
            // the hum from the mountain's way, close enough to the player to be heard
            Vec3 at = p.position().add(c.subtract(p.position()).normalize().scale(Math.min(d, 12)));
            level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 0.6f + near, 0.5f);
            if (surge && d < 250) {
                p.sendOverlayMessage(Component.literal("The ground shakes. Something under the mountain is awake.")
                        .withStyle(ChatFormatting.DARK_RED));
            } else if (!surge && d < 120) {
                p.sendOverlayMessage(Component.literal("A low hum comes up through the ground.").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    private static void during(ServerLevel level, long now) {
        BreachSite.Found b = site();
        if (now >= activeUntil) {
            glow(level, false);
            activeUntil = 0;
            dirty = false;
            return;
        }
        if (b == null || !level.isLoaded(new BlockPos(b.x(), b.y(), b.z()))) {
            return;
        }
        long left = activeUntil - now;
        // a surge burns steady with the odd stutter; a flicker is mostly stutter
        if (now % 3 == 0) {
            double stutter = surging ? 0.08 : 0.5;
            glow(level, level.getRandom().nextDouble() >= stutter || left > (surging ? SURGE_TICKS : FLICKER_TICKS) - 6);
        }
        if (surging && now % 40 == 0) {
            level.playSound(null, b.x() + 0.5, b.y() + 0.5, b.z() - 2.5, SoundEvents.BEACON_AMBIENT, SoundSource.AMBIENT, 6f, 0.5f);
            level.sendParticles(ParticleTypes.CRIMSON_SPORE, b.x() + 0.5, b.y() + 0.5, b.z() - 1.5, 80, 4, 4, 2, 0.02);
        }
    }

    /** Every crack of the plug that's still there: white (a surge's) or back to its orange. */
    private static void glow(ServerLevel level, boolean on) {
        BreachSite.Found b = site();
        if (b == null) {
            return;
        }
        for (BreachSite.Glow g : b.glows()) {
            BlockPos pos = new BlockPos(g.x(), g.y(), g.z());
            if (!level.isLoaded(pos)) {
                continue;
            }
            String rest = g.block(), lit = rest.replace("_glow", "_surge");
            String now = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
            String want = on ? lit : rest;
            if (!now.equals(want) && (now.equals(rest) || now.equals(lit))) {
                Block block = BuiltInRegistries.BLOCK.getValue(ZomboidCraft.id(want));
                level.setBlock(pos, block.defaultBlockState(), 2);
            }
        }
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        long now = level.getGameTime();
        BreachSite.Found b = site();
        int days = ZcConfig.get().population.respawnDays;
        String where = b == null ? "no breach in this world (needs a ZomboidCraft County world)" : "breach at " + b.x() + " " + b.y() + " " + b.z();
        String text = String.format("%s. Respawning %s. Next %s in %.1f in-game hours. Last surge: %s.", where,
                days > 0 ? "every " + days + " days" : "off", days > 0 ? "surge" : "flicker", (next - now) / 1000.0,
                lastSurgeDay < 0 ? "none yet" : "day " + lastSurgeDay);
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int force(CommandContext<CommandSourceStack> ctx, boolean surge) {
        ServerLevel level = ctx.getSource().getServer().overworld();
        if (activeUntil > 0) {
            glow(level, false);
        }
        start(level, surge);
        ctx.getSource().sendSuccess(() -> Component.literal(surge ? "The breach surges." : "The breach flickers."), true);
        return 1;
    }

    private static Path file(MinecraftServer s) {
        return s.getWorldPath(LevelResource.ROOT).resolve("zomboidcraft_breach.json");
    }

    private static void load(MinecraftServer s) {
        next = 0;
        activeUntil = 0;
        lastSurgeDay = -1;
        dirty = false;
        try {
            Path f = file(s);
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                next = o.has("next") ? o.get("next").getAsLong() : 0;
                lastSurgeDay = o.has("lastSurgeDay") ? o.get("lastSurgeDay").getAsLong() : -1;
                dirty = o.has("dirty") && o.get("dirty").getAsBoolean();
            }
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't read the breach", e);
        }
        loaded = true;
    }

    private static void save(MinecraftServer s) {
        if (!loaded) {
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("next", next);
        o.addProperty("lastSurgeDay", lastSurgeDay);
        o.addProperty("dirty", dirty);
        try {
            Files.writeString(file(s), o.toString());
        } catch (Exception e) {
            ZomboidCraft.LOGGER.error("Couldn't save the breach", e);
        }
    }
}
