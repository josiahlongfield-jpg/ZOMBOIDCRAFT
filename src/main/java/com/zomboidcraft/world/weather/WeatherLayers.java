package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZcConfig;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeLayer;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.attribute.modifier.ColorModifier;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * The gloom: layers on top of vanilla's day cycle and weather that keep the sky a low grey
 * ceiling, hold daylight down to an endless slate dusk barely brighter than the night, darken
 * nights (until the moon breaks through), drop fog over the streets, and hide the sunsets and
 * stars.
 *
 * They run last, after the dimension, biome, timelines and vanilla weather, on both sides:
 * the client's drive the sky, light and fog you see; the server's set the light level the
 * zombies' eyes and spawning go by.
 */
public final class WeatherLayers {
    /** Daylight on a plain overcast day, as a share of vanilla's: a little darker than a vanilla thunderstorm. */
    private static final double OVERCAST_DAY = 0.55;
    /** The endless night's day sky and fog: a cold slate dusk. */
    private static final Vector3fc DUSK_SKY = new Vector3f(0.11f, 0.13f, 0.17f);
    private static final Vector3fc DUSK_FOG = new Vector3f(0.14f, 0.16f, 0.20f);
    /** The cold white of the sky and air in a lightning flash. */
    private static final Vector3fc FLASH = new Vector3f(0.72f, 0.76f, 0.88f);

    public static void add(EnvironmentAttributeSystem.Builder builder, Level level) {
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        // a lightning flash lights the street, day or night, whatever the gloom (client side only)
        layer(builder, level, EnvironmentAttributes.SKY_LIGHT_FACTOR, (v, w, night, c) ->
                (float) Mth.lerp(flash(level) * 0.9, v * light(w, night, c), 1));
        // the light level the game goes by (spawning, zombie eyes) keeps the plain overcast day:
        // endless night only darkens what you see
        layer(builder, level, EnvironmentAttributes.SKY_LIGHT_LEVEL, (v, w, night, c) ->
                v * (float) Mth.lerp(night, overcast(c) ? Math.min(1, OVERCAST_DAY + 0.25) : 1, 1 + 0.25 * w.moon()));
        layer(builder, level, EnvironmentAttributes.SKY_COLOR, (v, w, night, c) -> {
            Vector3fc sky = overcast(c) ? gray(v, 0.42f, 0.9f) : v;
            sky = endless(c) ? toward(sky, DUSK_SKY, 0.8f * (float) (1 - night)) : sky;
            sky = gray(sky, 0.55f, 0.8f * w.fog());
            // moonlight through the gap: a faint cold blue
            sky = w.moon() > 0 ? new Vector3f(sky).add(0.035f * w.moon(), 0.045f * w.moon(), 0.07f * w.moon()) : sky;
            return toward(sky, FLASH, 0.75f * flash(level));
        });
        layer(builder, level, EnvironmentAttributes.FOG_COLOR, (v, w, night, c) -> {
            Vector3fc fog = overcast(c) ? gray(v, 0.5f, 0.75f) : v;
            fog = endless(c) ? toward(fog, DUSK_FOG, 0.7f * (float) (1 - night)) : fog;
            return toward(gray(fog, 0.6f, 0.85f * w.fog()), FLASH, 0.6f * flash(level));
        });
        layer(builder, level, EnvironmentAttributes.CLOUD_COLOR, (v, w, night, c) ->
                overcast(c) ? ColorModifier.BLEND_TO_GRAY_ARGB.apply(v, new ColorModifier.BlendToGray(endless(c) ? 0.22f : 0.32f, 0.85f)) : v);
        layer(builder, level, EnvironmentAttributes.SUNRISE_SUNSET_COLOR, (v, w, night, c) ->
                overcast(c) ? ColorModifier.MULTIPLY_ARGB.apply(v, ARGB.vector4fFromARGB32(ARGB.colorFromFloat(0.15f, 0.45f, 0.45f, 0.5f))) : v);
        layer(builder, level, EnvironmentAttributes.STAR_BRIGHTNESS, (v, w, night, c) -> overcast(c) ? v * w.moon() : v);
        // a light haze always hangs over the distance; in thick fog the street fades out a
        // couple of dozen blocks away
        layer(builder, level, EnvironmentAttributes.FOG_START_DISTANCE, (v, w, night, c) ->
                (float) Mth.lerp(w.fog(), haze(v, c, 0.15), 0));
        // a lightning flash throws the fog back for a moment: shapes down the street show up
        layer(builder, level, EnvironmentAttributes.FOG_END_DISTANCE, (v, w, night, c) ->
                (float) Mth.lerp(flash(level), fogEnd(haze(v, c, 1), w, 22), Math.max(fogEnd(haze(v, c, 1), w, 22), 110)));
        layer(builder, level, EnvironmentAttributes.SKY_FOG_END_DISTANCE, (v, w, night, c) -> fogEnd(haze(v, c, 1), w, 30));
        layer(builder, level, EnvironmentAttributes.CLOUD_FOG_END_DISTANCE, (v, w, night, c) -> fogEnd(v, w, 30));
    }

    /** Daylight held low (under endless night, barely above the night), and a little light back when the moon shows. */
    private static float light(Weather w, double night, ZcConfig.Weather c) {
        double day = !overcast(c) ? 1 : endless(c) ? c.endlessDaylight : OVERCAST_DAY;
        double dark = c.nightLight * (1 + 0.6 * w.moon());
        return (float) (Mth.lerp(night, day, dark) * (1 - 0.15 * w.fog()));
    }

    private static float fogEnd(float v, Weather w, float near) {
        return (float) Mth.lerp(w.fog(), v, Math.min(v, near));
    }

    /** Pulls a fog distance in to the haze (scaled by share), when the haze is on. */
    private static float haze(float v, ZcConfig.Weather c, double share) {
        return c.hazeDistance > 0 ? (float) Math.min(v, c.hazeDistance * share) : v;
    }

    private static boolean overcast(ZcConfig.Weather c) {
        return c.alwaysOvercast;
    }

    private static float flash(Level level) {
        return level.isClientSide() ? WeatherView.flash() : 0;
    }

    private static boolean endless(ZcConfig.Weather c) {
        return c.alwaysOvercast && c.endlessNight;
    }

    /** Pulls a colour toward target by factor. */
    private static Vector3fc toward(Vector3fc v, Vector3fc target, float factor) {
        return factor <= 0 ? v : new Vector3f(v).lerp(target, factor);
    }

    /** Pulls a colour toward a grey of its own brightness (times brightness) by factor. */
    private static Vector3fc gray(Vector3fc v, float brightness, float factor) {
        if (factor <= 0) {
            return v;
        }
        float g = (0.299f * v.x() + 0.587f * v.y() + 0.114f * v.z()) * brightness;
        return new Vector3f(v.x() + (g - v.x()) * factor, v.y() + (g - v.y()) * factor, v.z() + (g - v.z()) * factor);
    }

    private interface Change<T> {
        T apply(T value, Weather weather, double night, ZcConfig.Weather config);
    }

    private static <T> void layer(EnvironmentAttributeSystem.Builder builder, Level level, EnvironmentAttribute<T> attribute, Change<T> change) {
        builder.addTimeBasedLayer(attribute, (EnvironmentAttributeLayer.TimeBased<T>) (value, ticks) -> {
            Weather w = WeatherDirector.of(level);
            if (w == null) {
                return value;
            }
            return change.apply(value, w, WeatherView.nightness(level), ZcConfig.get().weather);
        });
    }

    private WeatherLayers() {
    }
}
