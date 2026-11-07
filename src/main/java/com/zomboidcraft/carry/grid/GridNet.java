package com.zomboidcraft.carry.grid;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** R while dragging, as in Tarkov: the client asks for the item on its cursor to be turned a quarter. */
public final class GridNet {
    public record Turn() implements CustomPacketPayload {
        public static final Type<Turn> TYPE = new Type<>(ZomboidCraft.id("grid_turn"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Turn> CODEC = StreamCodec.unit(new Turn());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private GridNet() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Turn.TYPE, Turn.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Turn.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                ItemStack carried = player.containerMenu.getCarried();
                if (!carried.isEmpty()) {
                    ItemSize.turn(carried);
                    player.containerMenu.broadcastChanges();
                }
            });
        });
    }
}
