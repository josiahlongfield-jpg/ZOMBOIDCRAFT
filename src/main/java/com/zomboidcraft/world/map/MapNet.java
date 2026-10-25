package com.zomboidcraft.world.map;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A map you've read, sent to your client to fill in your own map: a grid of {@link MapPalette} cells. */
public final class MapNet {
    /** {@code w} x {@code h} cells, {@code step} blocks apart, the first at (x0, z0); {@code title} names the map. */
    public record Reveal(String title, int x0, int z0, int step, int w, int h, byte[] cells) implements CustomPacketPayload {
        public static final Type<Reveal> TYPE = new Type<>(ZomboidCraft.id("map_reveal"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Reveal> CODEC = StreamCodec.of(
                (buf, r) -> {
                    buf.writeUtf(r.title());
                    buf.writeVarInt(r.x0());
                    buf.writeVarInt(r.z0());
                    buf.writeVarInt(r.step());
                    buf.writeVarInt(r.w());
                    buf.writeVarInt(r.h());
                    buf.writeByteArray(r.cells());
                },
                buf -> new Reveal(buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readByteArray()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Reveal.TYPE, Reveal.CODEC);
    }

    private MapNet() {
    }
}
