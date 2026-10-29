package com.zomboidcraft.world.map;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The map's packets: a paper map you've read ({@link Reveal}), the county your client draws the full map from
 * ({@link Atlas}), and where every survivor is ({@link Players}).
 */
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

    /**
     * The county this world was generated from, sent when you join: your client works the whole map out from it, so
     * the map shows every street and building without anyone having been there.
     */
    public record Atlas(long seed, boolean planned) implements CustomPacketPayload {
        public static final Type<Atlas> TYPE = new Type<>(ZomboidCraft.id("map_atlas"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Atlas> CODEC = StreamCodec.of(
                (buf, a) -> {
                    buf.writeLong(a.seed());
                    buf.writeBoolean(a.planned());
                },
                buf -> new Atlas(buf.readLong(), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** One survivor on the map. */
    /** How coarse the map's player marks are, in blocks. */
    public static final int AREA = 64;

    private static float area(double v) {
        return (float) (Math.floor(v / AREA) * AREA + AREA / 2.0);
    }

    public record Spot(java.util.UUID id, String name, String dim, float x, float z, float yaw) {
    }

    /** Where every player is, sent to everyone twice a second. */
    public record Players(java.util.List<Spot> spots) implements CustomPacketPayload {
        public static final Type<Players> TYPE = new Type<>(ZomboidCraft.id("map_players"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Players> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.spots().size());
                    for (Spot s : p.spots()) {
                        buf.writeUUID(s.id());
                        buf.writeUtf(s.name());
                        buf.writeUtf(s.dim());
                        buf.writeFloat(s.x());
                        buf.writeFloat(s.z());
                        buf.writeFloat(s.yaw());
                    }
                },
                buf -> {
                    int n = buf.readVarInt();
                    java.util.List<Spot> out = new java.util.ArrayList<>(n);
                    for (int i = 0; i < n; i++) {
                        out.add(new Spot(buf.readUUID(), buf.readUtf(), buf.readUtf(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
                    }
                    return new Players(out);
                });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Reveal.TYPE, Reveal.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Atlas.TYPE, Atlas.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Players.TYPE, Players.CODEC);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> server.execute(() -> {
            var overworld = server.overworld();
            if (overworld.getChunkSource().getGenerator() instanceof com.zomboidcraft.world.gen.CountyChunkGenerator county) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(handler.getPlayer(), new Atlas(overworld.getSeed(), county.planned()));
            }
        }));
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // only the general area goes out (Josiah 2026-10-29): the middle of the 64-block square someone is in
            if (server.getTickCount() % 40 != 0) return;
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty()) return;
            java.util.List<Spot> spots = new java.util.ArrayList<>(players.size());
            for (var p : players) {
                if (p.isSpectator()) continue;
                spots.add(new Spot(p.getUUID(), p.getName().getString(), p.level().dimension().identifier().toString(),
                        area(p.getX()), area(p.getZ()), 0));
            }
            Players packet = new Players(spots);
            for (var p : players) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, packet);
            }
        });
    }

    private MapNet() {
    }
}
