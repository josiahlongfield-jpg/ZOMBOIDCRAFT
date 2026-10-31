package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZcConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Zomboid's Nature settings for the weather (config section nature): the climate and fog cycles,
 * how rainy and how cold it is, and the caps on how much fog and rain you see. The director asks
 * here what the weather may do; the client asks how much of it to draw.
 */
public final class Climate {
    public enum Cycle { NORMAL, NO_WEATHER, ENDLESS_RAIN, ENDLESS_STORM }

    public enum FogCycle { NORMAL, NO_FOG, ENDLESS_FOG }

    /** Thick fog under the endless fog cycle: a street or so of sight. */
    static final float ENDLESS_FOG = 0.85f;

    public static Cycle cycle() {
        return switch (config().climateCycle) {
            case "no_weather" -> Cycle.NO_WEATHER;
            case "endless_rain" -> Cycle.ENDLESS_RAIN;
            case "endless_storm" -> Cycle.ENDLESS_STORM;
            default -> Cycle.NORMAL;
        };
    }

    public static FogCycle fogCycle() {
        return switch (config().fogCycle) {
            case "no_fog" -> FogCycle.NO_FOG;
            case "endless_fog" -> FogCycle.ENDLESS_FOG;
            default -> FogCycle.NORMAL;
        };
    }

    /** Zomboid's rain setting as a step from normal: -2 very dry .. +2 very rainy. */
    static int rainStep() {
        return switch (config().rain) {
            case "very_dry" -> -2;
            case "dry" -> -1;
            case "rainy" -> 1;
            case "very_rainy" -> 2;
            default -> 0;
        };
    }

    /** How far the rain floor sits from weather.minRain: drier settings let it ease off to a drizzle. */
    static float rainFloor(double minRain) {
        double scale = switch (rainStep()) {
            case -2 -> 0.35;
            case -1 -> 0.65;
            case 1 -> 1.15;
            case 2 -> 1.3;
            default -> 1;
        };
        return (float) Mth.clamp(minRain * scale, 0, 1);
    }

    /** Scales how often a kind of weather comes up under the rain setting: wetter brings more downpours and storms. */
    static double rainWeight(WeatherKind kind) {
        int step = rainStep();
        if (step == 0) {
            return 1;
        }
        return switch (kind) {
            case DOWNPOUR, STORM, THUNDERSTORM, SEVERE -> Math.pow(1.35, step);
            case GALE, FOG -> 1 + 0.15 * -step;
            default -> 1;
        };
    }

    /** How often drizzle comes up: never in the city's own mix, but a dry setting brings it back. */
    static double drizzleWeight() {
        return Math.max(0, -rainStep()) * 15;
    }

    /** Whether the climate cycle lets a kind of weather come up at all. */
    static boolean allowed(WeatherKind kind) {
        return switch (cycle()) {
            case NO_WEATHER -> kind == WeatherKind.RAIN;  // held dry and still in target()
            case ENDLESS_RAIN -> kind == WeatherKind.RAIN || kind == WeatherKind.DOWNPOUR;
            case ENDLESS_STORM -> kind.isStorm();
            default -> true;
        };
    }

    /** Air temperature at the start of the day, in °C, under Zomboid's temperature setting. */
    private static double baseTemp() {
        return switch (config().temperature) {
            case "very_cold" -> -2;
            case "cold" -> 5;
            case "hot" -> 20;
            case "very_hot" -> 28;
            default -> 12;
        };
    }

    /**
     * The air temperature at a spot, in °C: colder at night, in rain and, out in the open, in the
     * wind; indoors it stays nearer a room's 16 °C. For body temperature (SURVIVAL) when that is on.
     */
    public static double airTemp(Level level, BlockPos pos) {
        double t = baseTemp() - 5 * WeatherView.nightness(level);
        Weather w = WeatherDirector.of(level);
        boolean outside = level.canSeeSky(pos);
        if (w != null) {
            t -= 3 * w.rain();
            if (outside) {
                t -= 7 * w.wind();  // wind chill
            }
        }
        return outside ? t : Mth.lerp(0.6, t, 16);
    }

    /** How much of the fog you see, under Zomboid's max fog intensity: 1 all of it, 0 none. */
    public static float maxFog() {
        return switch (config().maxFog) {
            case "moderate" -> 0.7f;
            case "low" -> 0.4f;
            case "none" -> 0f;
            default -> 1f;
        };
    }

    /** How much of the rain you see, under Zomboid's max rain effects intensity. */
    public static float maxRainFx() {
        return switch (config().maxRainFx) {
            case "moderate" -> 0.7f;
            case "low" -> 0.45f;
            default -> 1f;
        };
    }

    private static ZcConfig.Nature config() {
        return ZcConfig.get().nature;
    }

    private Climate() {
    }
}
