package com.zomboidcraft.npc;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** The interact key pressed on an NPC: she follows the player who pressed it, or waits where she is. */
public final class NpcNet {
    public record Toggle(int npc) implements CustomPacketPayload {
        public static final Type<Toggle> TYPE = new Type<>(ZomboidCraft.id("npc_toggle"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Toggle> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Toggle::npc, Toggle::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!(player.level().getEntity(payload.npc()) instanceof NpcEntity npc) || player.isSpectator()
                        || npc.distanceToSqr(player) > 5 * 5) {
                    return;
                }
                boolean follows = npc.toggleFollow(player);
                player.sendOverlayMessage(Component.literal(follows ? "She's following you" : "She'll wait here")
                        .withStyle(ChatFormatting.GRAY));
            });
        });
    }

    private NpcNet() {
    }
}
