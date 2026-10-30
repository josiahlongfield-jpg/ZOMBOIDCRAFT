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
 * or about her clothes (NpcWardrobe: LIKE, DISLIKE, SUGGEST + outfit, PUT_ON or TAKE_OFF + piece).
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
                npc.attend(player);
                NpcBond.Act act = NpcBond.Act.of(payload.act());
                if (act != null) {
                    npc.bond.act(npc, player, act);
                } else if (payload.act() == TAKE_GUN) {
                    takeGun(npc, player);
                } else if (payload.act() == CALM) {
                    npc.bond.calm(npc, player);
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

    /** The talk panel's "take her weapon" act: what she holds goes to your hand if it's empty, else your pack, else your feet. */
    public static final int TAKE_GUN = 50;
    /** The talk panel's "calm her down" (NpcBond.calm). */
    public static final int CALM = 51;

    private static void takeGun(NpcEntity npc, ServerPlayer player) {
        net.minecraft.world.item.ItemStack held = npc.getMainHandItem();
        if (held.isEmpty()) {
            player.sendOverlayMessage(Component.literal("She isn't holding anything").withStyle(ChatFormatting.GRAY));
            return;
        }
        npc.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, held);
        } else if (!player.getInventory().add(held)) {
            player.spawnAtLocation((net.minecraft.server.level.ServerLevel) player.level(), held);
        }
        npc.lines.sayLine(npc, NpcLines.Kind.REPLY, NpcWork.pick(npc, "Here. Take it.", "Fine, it's yours.", "Careful, it's loaded."));
    }

    private NpcNet() {
    }
}
