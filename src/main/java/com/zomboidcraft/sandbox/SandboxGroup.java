package com.zomboidcraft.sandbox;

/** The Sandbox page's groups, in Zomboid's order, then the one for what only ZomboidCraft has. */
public enum SandboxGroup {
    POPULATION("population", "Zombie Population"),
    LORE("lore", "Zombie Lore"),
    TIME("time", "Time and Start"),
    UTILITIES("utilities", "Water, Power and Events"),
    LOOT("loot", "Loot"),
    NATURE("nature", "Nature, Weather and Farming"),
    CHARACTER("character", "Character, Health and Food"),
    SKILLS("skills", "Skills and XP"),
    VEHICLES("vehicles", "Vehicles and Fuel"),
    FIREARMS("firearms", "Firearms"),
    ANIMALS("animals", "Animals and Vermin"),
    MAP("map", "Map and Clean-up"),
    ZOMBOIDCRAFT("zomboidcraft", "ZomboidCraft Only");

    public final String id;
    public final String label;

    SandboxGroup(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static SandboxGroup byId(String id) {
        for (SandboxGroup g : values()) {
            if (g.id.equalsIgnoreCase(id)) {
                return g;
            }
        }
        return null;
    }
}
