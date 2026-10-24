package com.zomboidcraft.client.zombie;

/**
 * Which extra model parts an outfit shows: a cap visor, a hat brim (and how wide), a backpack.
 * Keep in step with the outfits in tools/gen_zombie_skins.py, which paints these parts.
 */
public record WalkerGear(boolean visor, float brim, boolean pack) {
    private static final WalkerGear NONE = new WalkerGear(false, 0f, false);

    public static WalkerGear of(String outfit) {
        return switch (outfit) {
            case "police", "security" -> new WalkerGear(true, 0f, false);
            case "construction" -> new WalkerGear(false, 1f, false);
            case "firefighter" -> new WalkerGear(false, 1.15f, false);
            case "farmer" -> new WalkerGear(false, 1.35f, false);
            case "survivor" -> new WalkerGear(false, 1.15f, true);
            default -> NONE;
        };
    }
}
