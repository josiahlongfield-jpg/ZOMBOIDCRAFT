package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Keys and clicks the client sends: fire, reload, shove, struggle. The server decides what happens. */
public final class CombatNet {
    /** Pull the trigger, while aiming or from the hip. */
    public record Fire(boolean aiming) implements CustomPacketPayload {
        public static final Type<Fire> TYPE = new Type<>(ZomboidCraft.id("fire"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, Fire::aiming, Fire::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One of the action keys: reload, shove, a struggle tap, the light or laser switch. */
    public record Action(int action) implements CustomPacketPayload {
        public static final int RELOAD = 0;
        public static final int SHOVE = 1;
        public static final int STRUGGLE = 2;
        public static final int RAIL = 3;
        public static final Type<Action> TYPE = new Type<>(ZomboidCraft.id("action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, Action::action, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Fire.TYPE, Fire.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Fire.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> Guns.fire(player, payload.aiming()));
        });
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                switch (payload.action()) {
                    case Action.RELOAD -> Guns.reload(player);
                    case Action.SHOVE -> Shove.shove(player);
                    case Action.STRUGGLE -> Grabs.struggle(player);
                    case Action.RAIL -> Guns.toggleRail(player);
                    default -> {
                    }
                }
            });
        });
    }

    private CombatNet() {
    }
}
