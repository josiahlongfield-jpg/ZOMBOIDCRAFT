package com.zomboidcraft.sandbox;

import com.zomboidcraft.ZomboidCraft;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Sandbox page's traffic. The server sends Sync (the world's preset and values) when you join and after every
 * change, and Open when /zc sandbox asks for the page. The page sends Set and Preset; only a host (an operator, like
 * every /zc command) can change the world.
 */
public final class SandboxNet {
    private static final StreamCodec<ByteBuf, Map<String, String>> VALUES =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8);

    /** Server to client: the world's preset (or "custom") and every per-world value by setting id. */
    public record Sync(String preset, Map<String, String> values) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(ZomboidCraft.id("sandbox_sync"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Sync::preset, VALUES, Sync::values, Sync::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Server to client: open the Sandbox page (from /zc sandbox). */
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ZomboidCraft.id("sandbox_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the host sets one setting. */
    public record Set(String id, String value) implements CustomPacketPayload {
        public static final Type<Set> TYPE = new Type<>(ZomboidCraft.id("sandbox_set"));
        public static final StreamCodec<ByteBuf, Set> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Set::id, ByteBufCodecs.STRING_UTF8, Set::value, Set::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: the host picks a preset. */
    public record Preset(String preset) implements CustomPacketPayload {
        public static final Type<Preset> TYPE = new Type<>(ZomboidCraft.id("sandbox_preset"));
        public static final StreamCodec<ByteBuf, Preset> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Preset::preset, Preset::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Set.TYPE, Set.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Preset.TYPE, Preset.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Set.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (host(player)) {
                    try {
                        Sandbox.set(payload.id(), payload.value());
                    } catch (IllegalArgumentException e) {
                        player.sendSystemMessage(Component.literal(e.getMessage()));
                        Sandbox.sync(player);
                    }
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(Preset.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (host(player)) {
                    try {
                        Sandbox.applyPreset(payload.preset());
                    } catch (IllegalArgumentException e) {
                        player.sendSystemMessage(Component.literal(e.getMessage()));
                    }
                }
            });
        });
    }

    /** Whoever may change the world's settings: an operator, as for /zc. */
    static boolean host(ServerPlayer player) {
        if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(player.createCommandSourceStack())) {
            return true;
        }
        player.sendSystemMessage(Component.literal("Only the host can change the world's settings."));
        Sandbox.sync(player);
        return false;
    }

    private SandboxNet() {
    }
}
