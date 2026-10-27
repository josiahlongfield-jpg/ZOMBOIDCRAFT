package com.zomboidcraft.zombie.population;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The dead standing out in the open round one player that aren't real zombies yet, for the client to draw as
 * pictures (CrowdFx): where each stands, relative to (bx, bz), and which outfit, by index into {@code outfits}.
 * Sent about once a second; each replaces the last.
 */
public record CrowdPayload(int bx, int bz, List<String> outfits, short[] dx, short[] dz, short[] y, byte[] outfit)
        implements CustomPacketPayload {
    public static final Type<CrowdPayload> TYPE = new Type<>(ZomboidCraft.id("crowd"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CrowdPayload> CODEC = new StreamCodec<>() {
        @Override
        public CrowdPayload decode(RegistryFriendlyByteBuf buf) {
            int bx = buf.readInt(), bz = buf.readInt();
            int names = buf.readVarInt();
            List<String> outfits = new ArrayList<>(names);
            for (int i = 0; i < names; i++) {
                outfits.add(buf.readUtf(64));
            }
            int n = buf.readVarInt();
            short[] dx = new short[n], dz = new short[n], y = new short[n];
            byte[] outfit = new byte[n];
            for (int i = 0; i < n; i++) {
                dx[i] = buf.readShort();
                dz[i] = buf.readShort();
                y[i] = buf.readShort();
                outfit[i] = buf.readByte();
            }
            return new CrowdPayload(bx, bz, outfits, dx, dz, y, outfit);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, CrowdPayload p) {
            buf.writeInt(p.bx);
            buf.writeInt(p.bz);
            buf.writeVarInt(p.outfits.size());
            for (String s : p.outfits) {
                buf.writeUtf(s, 64);
            }
            buf.writeVarInt(p.dx.length);
            for (int i = 0; i < p.dx.length; i++) {
                buf.writeShort(p.dx[i]);
                buf.writeShort(p.dz[i]);
                buf.writeShort(p.y[i]);
                buf.writeByte(p.outfit[i]);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
