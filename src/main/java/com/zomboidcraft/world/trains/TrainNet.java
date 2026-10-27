package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The trains' messages: the lines to the client, where riders stand in their cars both ways, and call buttons. */
public final class TrainNet {
    /** Every line: its track, stops and crossings, so the client lays the same route the server runs. */
    public record Lines(List<LineData> lines) implements CustomPacketPayload {
        public static final Type<Lines> TYPE = new Type<>(ZomboidCraft.id("train_lines"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Lines> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.lines.size());
            for (LineData l : p.lines) {
                buf.writeUtf(l.id);
                buf.writeUtf(l.kind);
                buf.writeUtf(l.name);
                buf.writeVarInt(l.cars);
                buf.writeVarInt(l.points.size());
                for (double[] pt : l.points) {
                    buf.writeDouble(pt[0]);
                    buf.writeDouble(pt[1]);
                    buf.writeDouble(pt[2]);
                }
                buf.writeVarInt(l.stops.size());
                for (StopData st : l.stops) {
                    buf.writeUtf(st.name);
                    buf.writeDouble(st.x);
                    buf.writeDouble(st.z);
                }
                buf.writeVarInt(l.crossings.size());
                for (double c : l.crossings) {
                    buf.writeDouble(c);
                }
            }
        }, buf -> {
            int n = buf.readVarInt();
            List<LineData> lines = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                String id = buf.readUtf(), kind = buf.readUtf(), name = buf.readUtf();
                int cars = buf.readVarInt();
                int pn = buf.readVarInt();
                List<double[]> pts = new ArrayList<>();
                for (int j = 0; j < pn; j++) {
                    pts.add(new double[] {buf.readDouble(), buf.readDouble(), buf.readDouble()});
                }
                int sn = buf.readVarInt();
                List<StopData> stops = new ArrayList<>();
                for (int j = 0; j < sn; j++) {
                    stops.add(new StopData(buf.readUtf(), buf.readDouble(), buf.readDouble()));
                }
                int cn = buf.readVarInt();
                List<Double> crossings = new ArrayList<>();
                for (int j = 0; j < cn; j++) {
                    crossings.add(buf.readDouble());
                }
                lines.add(new LineData(id, kind, name, cars, pts, stops, crossings));
            }
            return new Lines(lines);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record LineData(String id, String kind, String name, int cars, List<double[]> points, List<StopData> stops,
                           List<Double> crossings) {
    }

    public record StopData(String name, double x, double z) {
    }

    /** From a player's game: where in which car of which train they stand (train -1: not on one). */
    public record Ride(int train, int car, float x, float y, float z, float yaw) implements CustomPacketPayload {
        public static final Type<Ride> TYPE = new Type<>(ZomboidCraft.id("train_ride"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Ride> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Ride::train, ByteBufCodecs.VAR_INT, Ride::car, ByteBufCodecs.FLOAT, Ride::x,
                ByteBufCodecs.FLOAT, Ride::y, ByteBufCodecs.FLOAT, Ride::z, ByteBufCodecs.FLOAT, Ride::yaw, Ride::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** To everyone near a train, each tick: who's aboard and where in their car, so they're drawn moving with it. */
    public record Riders(int train, int[] ids, int[] cars, float[] at) implements CustomPacketPayload {
        public static final Type<Riders> TYPE = new Type<>(ZomboidCraft.id("train_riders"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Riders> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.train);
            buf.writeVarInt(p.ids.length);
            for (int i = 0; i < p.ids.length; i++) {
                buf.writeVarInt(p.ids[i]);
                buf.writeVarInt(p.cars[i]);
                for (int k = 0; k < 4; k++) {
                    buf.writeFloat(p.at[i * 4 + k]);
                }
            }
        }, buf -> {
            int train = buf.readVarInt(), n = buf.readVarInt();
            int[] ids = new int[n], cars = new int[n];
            float[] at = new float[n * 4];
            for (int i = 0; i < n; i++) {
                ids[i] = buf.readVarInt();
                cars[i] = buf.readVarInt();
                for (int k = 0; k < 4; k++) {
                    at[i * 4 + k] = buf.readFloat();
                }
            }
            return new Riders(train, ids, cars, at);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A call button pressed (with the interact key). */
    public record Call(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Call> TYPE = new Type<>(ZomboidCraft.id("train_call"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Call> CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Call::pos, Call::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** A stop button pressed inside a train (its entity id): stop and let me off. */
    public record StopRequest(int train) implements CustomPacketPayload {
        public static final Type<StopRequest> TYPE = new Type<>(ZomboidCraft.id("train_stop_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StopRequest> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, StopRequest::train, StopRequest::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** OTTO says a line: the voice clips to play one after another, from the train's speakers (train -1: the station's). */
    public record OttoSay(int train, List<String> clips) implements CustomPacketPayload {
        public static final Type<OttoSay> TYPE = new Type<>(ZomboidCraft.id("train_otto"));
        public static final StreamCodec<RegistryFriendlyByteBuf, OttoSay> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OttoSay::train, ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), OttoSay::clips, OttoSay::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private TrainNet() {
    }
}
