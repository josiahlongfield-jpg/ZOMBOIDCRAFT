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

/**
 * The interact key pressed on an NPC: she follows the player who pressed it, or waits where she is. Sneaking, it opens
 * the talk panel instead (client), whose picks come back as Talk: a compliment, a flirt, a gift, a hug, her hand, a kiss,
 * or about her clothes (NpcWardrobe's LIKE, DISLIKE, SUGGEST + outfit, and DRESS + outfit from the wardrobe panel).
 */
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

    public record Talk(int npc, int act) implements CustomPacketPayload {
        public static final Type<Talk> TYPE = new Type<>(ZomboidCraft.id("npc_talk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Talk> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Talk::npc, ByteBufCodecs.VAR_INT, Talk::act, Talk::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(Talk.TYPE, Talk.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Talk.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (!(player.level().getEntity(payload.npc()) instanceof NpcEntity npc) || !npc.isAlive() || player.isSpectator()
                        || npc.distanceToSqr(player) >= 4 * 4) {
                    return;
                }
                NpcBond.Act act = NpcBond.Act.of(payload.act());
                if (act != null) {
                    npc.bond.act(npc, player, act);
                } else if (payload.act() >= NpcWardrobe.LIKE) {
                    npc.wardrobe.answer(npc, player, payload.act());
                }
            });
        });
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
