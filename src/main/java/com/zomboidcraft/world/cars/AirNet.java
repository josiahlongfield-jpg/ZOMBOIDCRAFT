package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaternionf;

/**
 * The pilot's game flies the aircraft, as a driver's drives a car; each tick it tells the server how
 * the aircraft is turned (the vehicle move packet only carries yaw and pitch), how hard the engine's
 * working, and whether it's just crashed. The server passes the attitude on to everyone else.
 */
public final class AirNet {
    /** Set on a state when the pilot's game saw the aircraft hit something hard enough to wreck it. */
    public static final int CRASHED = 1;
    /** Set when it touched down safely; violence then carries how fast it was sinking (m/s). */
    public static final int LANDED = 2;

    public record State(float qx, float qy, float qz, float qw, float power, float rpm, float throttle, int flags, float violence)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(ZomboidCraft.id("air_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeFloat(s.qx);
            buf.writeFloat(s.qy);
            buf.writeFloat(s.qz);
            buf.writeFloat(s.qw);
            buf.writeFloat(s.power);
            buf.writeFloat(s.rpm);
            buf.writeFloat(s.throttle);
            buf.writeByte(s.flags);
            buf.writeFloat(s.violence);
        }, buf -> new State(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readByte(), buf.readFloat()));

        public Quaternionf attitude() {
            return new Quaternionf(qx, qy, qz, qw).normalize();
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A pilot's switch: ENGINE starts or shuts down the engines. */
    public static final int ENGINE = 1;

    public record Control(int code) implements CustomPacketPayload {
        public static final Type<Control> TYPE = new Type<>(ZomboidCraft.id("air_control"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Control> CODEC = StreamCodec.of((buf, c) -> buf.writeVarInt(c.code),
                buf -> new Control(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Control.TYPE, Control.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Control.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (payload.code() == ENGINE && player.getVehicle() instanceof CarProp car && car.aircraft()) {
                    car.toggleAirEngine(player);
                }
            });
        });
        PayloadTypeRegistry.serverboundPlay().register(State.TYPE, State.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(State.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> {
                if (player.getVehicle() instanceof CarProp car && car.getControllingPassenger() == player && car.aircraft()) {
                    car.pilotState(payload);
                }
            });
        });
    }

    private AirNet() {
    }
}
