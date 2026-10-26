package com.zomboidcraft.world.weather;

import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The client's copy of the weather, eased every client tick toward what the server last sent
 * so the sky never jumps, plus the time-of-day helper both sides use.
 */
public final class WeatherView {
    private static @Nullable Weather client;
    /** A lightning flash lighting this client's world, 0 .. 1 (set by client/weather/WeatherFx). */
    private static float flash;

    /** The weather this client is showing, or null when the server isn't running the mod's weather here. */
    public static @Nullable Weather client() {
        return client;
    }

    /** How bright a lightning flash is lighting the world right now on this client, 0 .. 1. */
    public static float flash() {
        return flash;
    }

    public static void setFlash(float value) {
        flash = value;
    }

    /** Called each client tick with what the server last sent (null when nothing was sent). */
    public static void update(@Nullable Weather sent) {
        if (sent == null) {
            client = null;
        } else if (client == null) {
            client = sent;
        } else {
            client = client.toward(sent, 0.02f);
        }
    }

    /**
     * 0 by day, 1 in the dead of night, easing through dusk and dawn. Read from the sun's
     * angle, which is 0 at noon and 180 at midnight.
     */
    public static double nightness(Level level) {
        float angle = level.environmentAttributes().getDimensionValue(EnvironmentAttributes.SUN_ANGLE);
        double height = Math.cos(Math.toRadians(angle));  // 1 noon, 0 at the horizon, -1 midnight
        return Mth.clamp(0.5 - height * 3, 0, 1);
    }

    private WeatherView() {
    }
}
