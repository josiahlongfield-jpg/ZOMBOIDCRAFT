package com.zomboidcraft.combat.rolls;

/** Tier names, 1 to 5. */
public final class Tier {
    private static final String[] LABELS = {"Rough", "Used", "Serviceable", "Fine", "Pristine"};

    public static String label(int tier) {
        return LABELS[Math.clamp(tier, 1, 5) - 1];
    }

    private Tier() {
    }
}
