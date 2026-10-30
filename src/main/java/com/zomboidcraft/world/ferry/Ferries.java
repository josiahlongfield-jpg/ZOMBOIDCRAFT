package com.zomboidcraft.world.ferry;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.FerryRoutes;
import com.zomboidcraft.world.gen.CountyChunkGenerator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The ferries: the boats themselves are scenery, so a crossing is the bell. Ring a landing's bell
 * with the interact key and it clangs out over the water (every zombie near hears it too); a few
 * seconds on the ferry's come in, and you and whoever stands with you at the bell go dark for the
 * crossing and step off at the far landing to her horn.
 */
public final class Ferries {
    private static final ResourceKey<Block> BELL_KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("ferry_bell"));
    public static final Block BELL = Registry.register(BuiltInRegistries.BLOCK, BELL_KEY, new FerryBellBlock(
            BlockBehaviour.Properties.of().strength(3f, 6f).sound(SoundType.METAL).noOcclusion().setId(BELL_KEY)));

    /** How near the bell (blocks) you must stand to ring it, and to go across with whoever rang it. */
    private static final double REACH = 6, PARTY = 5;
    /** Ticks from the bell to the ferry coming in, and the crossing's dark. */
    private static final int WAIT = 60, DARK = 50;
    /** Ticks before the same player can ring again. */
    private static final int COOLDOWN = 200;

    private record Crossing(BlockPos bell, FerryRoutes.End to, long at) {
    }

    private static final List<Crossing> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> RUNG = new HashMap<>();

    /** From a player's game: the bell at pos rung. */
    public record Ring(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Ring> TYPE = new Type<>(ZomboidCraft.id("ferry_ring"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ring> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Ring::pos, Ring::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private Ferries() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Ring.TYPE, Ring.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Ring.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!player.isSpectator() && player.blockPosition().distSqr(payload.pos()) < REACH * REACH
                        && player.level().getBlockState(payload.pos()).is(BELL)) {
                    ring((ServerLevel) player.level(), payload.pos(), player);
                }
            });
        });
        ServerTickEvents.END_SERVER_TICK.register(Ferries::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> {
            PENDING.clear();
            RUNG.clear();
        });
    }

    private static void ring(ServerLevel level, BlockPos pos, ServerPlayer player) {
        long now = level.getGameTime();
        Long last = RUNG.get(player.getUUID());
        level.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.5f, 0.9f);
        if (last != null && now - last < COOLDOWN) {
            return;
        }
        RUNG.put(player.getUUID(), now);
        for (Crossing c : PENDING) {
            if (c.bell.equals(pos)) {
                return;  // already called; she's on her way
            }
        }
        County county = level.getChunkSource().getGenerator() instanceof CountyChunkGenerator
                ? CountyChunkGenerator.gen(level.getSeed()).county() : null;
        FerryRoutes.End to = county == null ? null : FerryRoutes.across(county, pos.getX(), pos.getY(), pos.getZ());
        if (to == null) {
            player.sendOverlayMessage(Component.literal("The bell rings out over the water. No ferry comes.").withStyle(ChatFormatting.GRAY));
            return;
        }
        player.sendOverlayMessage(Component.literal("The ferry to " + to.name() + " is coming in. Stay by the bell.").withStyle(ChatFormatting.GRAY));
        PENDING.add(new Crossing(pos.immutable(), to, now + WAIT));
    }

    private static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        ServerLevel level = server.overworld();
        long now = level.getGameTime();
        for (Iterator<Crossing> it = PENDING.iterator(); it.hasNext(); ) {
            Crossing c = it.next();
            if (now < c.at) {
                continue;
            }
            it.remove();
            FerryRoutes.End to = c.to;
            int n = 0;
            for (ServerPlayer p : level.players()) {
                if (p.isSpectator() || p.isPassenger() || p.position().distanceToSqr(c.bell.getX() + 0.5, c.bell.getY(), c.bell.getZ() + 0.5) > PARTY * PARTY) {
                    continue;
                }
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, DARK, 0, false, false));
                double side = (n % 3 - 1) * 1.0;
                p.teleportTo(level, to.x() + (Math.abs(to.yaw()) == 90f ? 0 : side), to.y(), to.z() + (Math.abs(to.yaw()) == 90f ? side : 0),
                        java.util.Set.of(), to.yaw(), 0f, false);
                n++;
            }
            if (n > 0) {
                level.playSound(null, BlockPos.containing(to.x(), to.y(), to.z()), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 0.7f);
            } else {
                level.playSound(null, c.bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5f, 0.7f);
            }
        }
    }
}
