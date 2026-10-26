package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Where shots land, told to everyone nearby so their game can show it: a hole, dust and chips where a bullet hits a
 * block, and blood where it hits flesh, sprayed out along the shot and splashed on the wall or floor behind. The
 * looks are all drawn on each client (client.combat.ImpactFx).
 */
public final class Impacts {
    public static final int HOLE = 0, BLOOD = 1, SPLASH = 2;
    /** How far away players are told about a shot landing (blocks). */
    private static final double RANGE = 96;
    /** How far behind a hit blood can reach a wall, and below it the floor (blocks). */
    private static final double SPRAY_REACH = 3.5, DRIP_REACH = 3.0;

    /**
     * One impact: what kind, where, which way the shot was going, the face it struck (Direction index, for holes and
     * splashes), the block there (its state id, for dust and chips) and how big (1 = a single bullet).
     */
    public record Impact(int kind, float x, float y, float z, float dx, float dy, float dz, int face, int block,
                         float size) implements CustomPacketPayload {
        public static final Type<Impact> TYPE = new Type<>(ZomboidCraft.id("impact"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Impact> CODEC = new StreamCodec<>() {
            @Override
            public Impact decode(RegistryFriendlyByteBuf buf) {
                return new Impact(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Impact i) {
                buf.writeVarInt(i.kind);
                buf.writeFloat(i.x);
                buf.writeFloat(i.y);
                buf.writeFloat(i.z);
                buf.writeFloat(i.dx);
                buf.writeFloat(i.dy);
                buf.writeFloat(i.dz);
                buf.writeVarInt(i.face);
                buf.writeVarInt(i.block);
                buf.writeFloat(i.size);
            }
        };

        public Vec3 pos() {
            return new Vec3(x, y, z);
        }

        public Vec3 dir() {
            return new Vec3(dx, dy, dz);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Impact.TYPE, Impact.CODEC);
    }

    /** A bullet or pellet struck a block face. */
    public static void block(ServerLevel level, BlockHitResult hit, Vec3 dir, BlockState state, float size) {
        Vec3 p = hit.getLocation();
        send(level, new Impact(HOLE, (float) p.x, (float) p.y, (float) p.z, (float) dir.x, (float) dir.y, (float) dir.z,
                hit.getDirection().get3DDataValue(), Block.getId(state), size));
    }

    /**
     * Shots struck flesh at a point: the spray there, then a splash where the blood carries to on the wall behind
     * and on the floor under it. size grows with the pellets that hit and the gun's power.
     */
    public static void flesh(ServerLevel level, LivingEntity target, Vec3 at, Vec3 dir, float size) {
        send(level, new Impact(BLOOD, (float) at.x, (float) at.y, (float) at.z, (float) dir.x, (float) dir.y,
                (float) dir.z, 0, 0, size));
        Vec3 d = dir.normalize();
        // out the far side and on along the shot, falling a little
        Vec3 from = at.add(d.scale(0.4));
        Vec3 to = from.add(d.scale(SPRAY_REACH)).add(0, -0.6, 0);
        splash(level, target, from, to, d, size);
        // and down at its feet
        Vec3 drop = at.add(d.scale(0.3 + level.getRandom().nextDouble() * 0.6));
        splash(level, target, drop, drop.add(0, -DRIP_REACH, 0), new Vec3(0, -1, 0), size * 0.8f);
    }

    private static void splash(ServerLevel level, LivingEntity target, Vec3 from, Vec3 to, Vec3 dir, float size) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target));
        if (hit.getType() == HitResult.Type.MISS || hit.isInside()) {
            return;
        }
        Vec3 p = hit.getLocation();
        // the further it carried, the more it spread and the thinner it is
        float spread = (float) (0.75 + 0.25 * from.distanceTo(p));
        Direction face = hit.getDirection();
        send(level, new Impact(SPLASH, (float) p.x, (float) p.y, (float) p.z, (float) dir.x, (float) dir.y, (float) dir.z,
                face.get3DDataValue(), Block.getId(level.getBlockState(hit.getBlockPos())), size * spread));
    }

    private static void send(ServerLevel level, Impact impact) {
        for (ServerPlayer player : PlayerLookup.around(level, impact.pos(), RANGE)) {
            ServerPlayNetworking.send(player, impact);
        }
    }

    private Impacts() {
    }
}
