package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The interact key on a car: get in, search the boot, or (pressed inside one) get out. A key of
 * its own rather than right-click, which aims a gun.
 */
public final class CarNet {
    /** Use car (its entity id) at a point relative to it; id -1 means get out of the one you're in. */
    public record Use(int car, float x, float y, float z) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(ZomboidCraft.id("use_car"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Use> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Use::car, ByteBufCodecs.FLOAT, Use::x, ByteBufCodecs.FLOAT, Use::y,
                ByteBufCodecs.FLOAT, Use::z, Use::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Use.TYPE, Use.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Use.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (payload.car() < 0) {
                    if (player.getVehicle() instanceof CarProp) {
                        player.stopRiding();
                    }
                    return;
                }
                if (player.level().getEntity(payload.car()) instanceof CarProp car && !player.isSpectator()
                        && car.getBoundingBox().inflate(1).distanceToSqr(player.getEyePosition()) < 4 * 4) {
                    car.use(player, new Vec3(payload.x(), payload.y(), payload.z()));
                }
            });
        });
    }

    private CarNet() {
    }
}
