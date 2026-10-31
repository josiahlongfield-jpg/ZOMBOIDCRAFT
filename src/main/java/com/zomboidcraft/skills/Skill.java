package com.zomboidcraft.skills;

public enum Skill {
    CARPENTRY("Carpentry"),
    FIRST_AID("First Aid"),
    BLUNT("Blunt"),
    BLADE("Blade"),
    SPRINTING("Sprinting"),
    SNEAKING("Sneaking"),
    AIMING("Aiming"),
    MAINTENANCE("Maintenance"),
    /** Knocking down brick, block and concrete with a sledgehammer (building/DigRules). */
    MASONRY("Masonry"),
    /** Cutting through steel with a propane torch (building/DigRules). */
    METALWORKING("Metalworking"),
    /** Flying the jets and the helicopter (world/cars/AirReadiness): steadier hands and softer landings. */
    PILOTING("Piloting"),
    /** Patching clothes: stitches that hold, sewn quicker (clothing/Tailoring). */
    TAILORING("Tailoring"),
    /** Taking cars apart and putting them back together (world/cars/CarParts): each level unlocks more of the car. */
    MECHANICS("Mechanics");

    public static final int MAX_LEVEL = 10;

    private final String displayName;

    Skill(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Total XP needed to reach a level: 50, 150, 300, ... 2750 for level 10. */
    public static int xpForLevel(int level) {
        return 25 * level * (level + 1);
    }

    public static int levelForXp(int xp) {
        int level = 0;
        while (level < MAX_LEVEL && xp >= xpForLevel(level + 1)) {
            level++;
        }
        return level;
    }
}
