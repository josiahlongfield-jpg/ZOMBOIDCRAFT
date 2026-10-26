package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * The weather as it is right now, eased between kinds: what the sky, the light and the
 * zombies react to. Each player's client gets a copy through a synced attachment.
 *
 * @param kind     the WeatherKind being moved toward (its ordinal)
 * @param rain     0 dry .. 1 downpour
 * @param thunder  0 .. 1, darkens the sky like a vanilla thunderstorm
 * @param fog      0 clear .. 1 a few blocks of sight
 * @param wind     0 still .. 1 cyclone
 * @param windYaw  where the wind blows toward, in degrees (Minecraft yaw)
 * @param moon     0 .. 1, how far the clouds have parted for the moon (night only)
 * @param hail     0 .. 1, hail falling
 */
public record Weather(int kind, float rain, float thunder, float fog, float wind, float windYaw, float moon, float hail) {
    public static final Weather CALM = new Weather(WeatherKind.RAIN.ordinal(), WeatherKind.RAIN.rain, 0, 0.1f, 0.15f, 45, 0, 0);

    public static final StreamCodec<io.netty.buffer.ByteBuf, Weather> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Weather decode(io.netty.buffer.ByteBuf buf) {
            return new Weather(ByteBufCodecs.VAR_INT.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(io.netty.buffer.ByteBuf buf, Weather w) {
            ByteBufCodecs.VAR_INT.encode(buf, w.kind);
            buf.writeFloat(w.rain);
            buf.writeFloat(w.thunder);
            buf.writeFloat(w.fog);
            buf.writeFloat(w.wind);
            buf.writeFloat(w.windYaw);
            buf.writeFloat(w.moon);
            buf.writeFloat(w.hail);
        }
    };

    /** Not saved: the weather picks up again from the director when the player joins. */
    public static final AttachmentType<Weather> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("weather"),
            builder -> builder.syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    public WeatherKind weatherKind() {
        return WeatherKind.values()[Mth.clamp(kind, 0, WeatherKind.values().length - 1)];
    }

    /** Moves every value a step toward another weather, turning the wind the short way round. */
    public Weather toward(Weather target, float step) {
        float yaw = windYaw + Mth.clamp(Mth.wrapDegrees(target.windYaw - windYaw), -step * 90, step * 90);
        return new Weather(target.kind, approach(rain, target.rain, step), approach(thunder, target.thunder, step),
                approach(fog, target.fog, step), approach(wind, target.wind, step), Mth.wrapDegrees(yaw),
                approach(moon, target.moon, step), approach(hail, target.hail, step * 2));
    }

    private static float approach(float from, float to, float step) {
        return from + Mth.clamp(to - from, -step, step);
    }

    /** Close enough to another weather that a client wouldn't see the difference. */
    public boolean near(Weather other) {
        return kind == other.kind && Math.abs(rain - other.rain) < 0.01f && Math.abs(thunder - other.thunder) < 0.01f
                && Math.abs(fog - other.fog) < 0.01f && Math.abs(wind - other.wind) < 0.01f
                && Math.abs(Mth.wrapDegrees(windYaw - other.windYaw)) < 2f && Math.abs(moon - other.moon) < 0.01f
                && Math.abs(hail - other.hail) < 0.01f;
    }
}
