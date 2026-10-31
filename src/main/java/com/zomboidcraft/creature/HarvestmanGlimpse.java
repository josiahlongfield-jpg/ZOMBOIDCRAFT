package com.zomboidcraft.creature;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Something only the hunted sees: a Harvestman standing at the edge of their sight for a moment, drawn by their game
 * alone (the client's HarvestmanPhantoms), gone when they look straight at it or after a few seconds. Nobody else sees
 * it, so their friends can only take their word for it.
 */
public final class HarvestmanGlimpse {
    /** Where it stands (its position: the body's centre is HarvestmanEntity.MID above), and for how long at most. */
    public record Glimpse(float x, float y, float z, int ticks) implements CustomPacketPayload {
        public static final Type<Glimpse> TYPE = new Type<>(ZomboidCraft.id("harvestman_glimpse"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Glimpse> CODEC = new StreamCodec<>() {
            @Override
            public Glimpse decode(RegistryFriendlyByteBuf buf) {
                return new Glimpse(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, Glimpse g) {
                buf.writeFloat(g.x);
                buf.writeFloat(g.y);
                buf.writeFloat(g.z);
                buf.writeVarInt(g.ticks);
            }
        };

        public Vec3 pos() {
            return new Vec3(x, y, z);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Glimpse.TYPE, Glimpse.CODEC);
    }

    /**
     * Shows them one, off toward the edge of where they're looking, 14-28 blocks out, on a floor in the dark with nothing
     * between. True if there was somewhere to.
     */
    static boolean show(ServerLevel level, ServerPlayer t) {
        Vec3 eye = t.getEyePosition(), look = t.getLookAngle().multiply(1, 0, 1);
        if (look.lengthSqr() < 1e-4) {
            return false;
        }
        look = look.normalize();
        var rnd = t.getRandom();
        for (int k = 0; k < 24; k++) {
            // toward the edge of their view, one side or the other
            double a = (rnd.nextBoolean() ? 1 : -1) * Math.toRadians(25 + rnd.nextDouble() * 20), r = 14 + rnd.nextDouble() * 14;
            Vec3 d = new Vec3(look.x * Math.cos(a) - look.z * Math.sin(a), 0, look.x * Math.sin(a) + look.z * Math.cos(a));
            Vec3 col = eye.add(d.scale(r));
            BlockHitResult down = level.clip(new ClipContext(col.add(0, 4, 0), col.add(0, -10, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, t));
            if (down.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 pos = down.getLocation().add(0, HarvestmanEntity.STAND - HarvestmanEntity.MID, 0);
            Vec3 centre = pos.add(0, HarvestmanEntity.MID, 0);
            if (level.getBrightness(LightLayer.BLOCK, net.minecraft.core.BlockPos.containing(centre)) > 6
                    || !level.noCollision(Harvestmen.HARVESTMAN.getDimensions().makeBoundingBox(pos))
                    || level.clip(new ClipContext(eye, centre, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, t)).getType() != HitResult.Type.MISS) {
                continue;
            }
            ServerPlayNetworking.send(t, new Glimpse((float) pos.x, (float) pos.y, (float) pos.z, 20 * (3 + rnd.nextInt(4))));
            return true;
        }
        return false;
    }

    private HarvestmanGlimpse() {
    }
}
