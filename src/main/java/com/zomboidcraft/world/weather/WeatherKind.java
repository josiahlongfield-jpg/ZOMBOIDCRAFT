package com.zomboidcraft.world.weather;

/**
 * The kinds of weather the city gets, with what each one does at full strength. Rain, thunder,
 * fog and wind run 0..1; hearing and sight scale how far zombies hear and see.
 *
 * Weights say how often a kind comes up next (storms are scaled by weather.stormFrequency),
 * and minutes how long it lasts in real time.
 */
public enum WeatherKind {
    DRIZZLE("drizzle", 0.32f, 0f, 0.10f, 0.15f, 1.0, 1.0, 40, 6, 14),
    RAIN("rain", 0.62f, 0f, 0.15f, 0.25f, 0.85, 0.9, 22, 4, 9),
    DOWNPOUR("downpour", 1.0f, 0f, 0.35f, 0.40f, 0.5, 0.7, 12, 3, 7),
    FOG("fog", 0.12f, 0f, 1.0f, 0.05f, 1.0, 0.35, 10, 4, 9),
    GALE("gale", 0.40f, 0f, 0f, 0.80f, 0.7, 0.9, 7, 3, 7),
    /** A grumbling storm: dark, heavy rain, thunder in the distance. */
    STORM("storm", 0.85f, 0.6f, 0.20f, 0.55f, 0.6, 0.75, 6, 4, 8),
    /** Lightning striking the city, setting roofs alight. */
    THUNDERSTORM("thunderstorm", 1.0f, 1.0f, 0.25f, 0.70f, 0.5, 0.65, 2.5, 3, 6),
    /** Lightning, hail and cyclone winds: get inside. */
    SEVERE("severe_storm", 1.0f, 1.0f, 0.30f, 1.0f, 0.4, 0.55, 0.8, 2, 4);

    public final String id;
    public final float rain, thunder, fog, wind;
    public final double hearing, sight;
    public final double weight;
    public final int minMinutes, maxMinutes;

    WeatherKind(String id, float rain, float thunder, float fog, float wind, double hearing, double sight,
                double weight, int minMinutes, int maxMinutes) {
        this.id = id;
        this.rain = rain;
        this.thunder = thunder;
        this.fog = fog;
        this.wind = wind;
        this.hearing = hearing;
        this.sight = sight;
        this.weight = weight;
        this.minMinutes = minMinutes;
        this.maxMinutes = maxMinutes;
    }

    public boolean isStorm() {
        return this == STORM || this == THUNDERSTORM || this == SEVERE;
    }

    /** Thunderstorms and severe storms: the ones that can hurt you. */
    public boolean isDangerous() {
        return this == THUNDERSTORM || this == SEVERE;
    }

    public static WeatherKind byId(String id) {
        for (WeatherKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return null;
    }
}
