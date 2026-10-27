package com.zomboidcraft.combat;

import com.zomboidcraft.world.cars.CarHull;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a bullet struck, by what the block is made of, for the sound of the hit: the ground (earth, grass, gravel,
 * sand, snow), concrete and asphalt, a building's brick, plaster or tile, wood, sheet and structural metal, a car's
 * body, glass, or something soft. Read from the block's name first (the city's blocks say what they are), then from
 * the sound the block makes underfoot.
 */
public enum Surface {
    GROUND, CONCRETE, BRICK, WOOD, METAL, CAR, GLASS, SOFT;

    public static Surface of(BlockState state) {
        if (state.getBlock() instanceof CarHull) {
            return CAR;
        }
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("glass") || path.contains("window") || path.contains("pane")) {
            return GLASS;
        }
        if (path.startsWith("car_") || path.contains("_car_") || path.contains("vehicle")) {
            return CAR;
        }
        if (has(path, "brick", "plaster", "stucco", "tile", "terracotta", "cinder")) {
            return BRICK;
        }
        if (has(path, "concrete", "asphalt", "road", "curb", "sidewalk", "cement", "pavement", "kerb")) {
            return CONCRETE;
        }
        if (has(path, "metal", "steel", "iron", "copper", "tin", "chain", "rail", "pipe", "vent", "duct", "shutter")) {
            return METAL;
        }
        if (has(path, "wood", "plank", "log", "board", "crate", "pallet", "fence", "door", "shelf", "table")) {
            return WOOD;
        }
        if (has(path, "dirt", "grass", "sand", "gravel", "mud", "soil", "snow", "leaves", "turf", "farmland")) {
            return GROUND;
        }
        if (has(path, "wool", "carpet", "bed", "sofa", "couch", "cushion", "curtain", "mattress")) {
            return SOFT;
        }
        SoundType sound = state.getSoundType();
        if (sound == SoundType.GLASS) {
            return GLASS;
        }
        if (sound == SoundType.METAL || sound == SoundType.CHAIN || sound == SoundType.ANVIL || sound == SoundType.COPPER
                || sound == SoundType.NETHERITE_BLOCK || sound == SoundType.LANTERN) {
            return METAL;
        }
        if (sound == SoundType.WOOD || sound == SoundType.BAMBOO_WOOD || sound == SoundType.CHERRY_WOOD
                || sound == SoundType.NETHER_WOOD || sound == SoundType.LADDER || sound == SoundType.SCAFFOLDING) {
            return WOOD;
        }
        if (sound == SoundType.GRASS || sound == SoundType.GRAVEL || sound == SoundType.SAND || sound == SoundType.ROOTED_DIRT
                || sound == SoundType.MUD || sound == SoundType.SNOW || sound == SoundType.POWDER_SNOW
                || sound == SoundType.MOSS || sound == SoundType.AZALEA_LEAVES) {
            return GROUND;
        }
        if (sound == SoundType.WOOL || sound == SoundType.MOSS_CARPET) {
            return SOFT;
        }
        if (sound == SoundType.MUD_BRICKS || sound == SoundType.NETHER_BRICKS || sound == SoundType.DECORATED_POT) {
            return BRICK;
        }
        return CONCRETE;    // stone and everything else hard
    }

    /** The sound of a bullet hitting it. */
    public SoundEvent impact() {
        return switch (this) {
            case GROUND -> ModSounds.IMPACT_GROUND;
            case CONCRETE -> ModSounds.IMPACT_CONCRETE;
            case BRICK -> ModSounds.IMPACT_BRICK;
            case WOOD -> ModSounds.IMPACT_WOOD;
            case METAL -> ModSounds.IMPACT_METAL;
            case CAR -> ModSounds.IMPACT_CAR;
            case GLASS -> ModSounds.IMPACT_GLASS;
            case SOFT -> ModSounds.IMPACT_SOFT;
        };
    }

    /** A bullet can whine off it when it strikes at a glancing angle. */
    public boolean hard() {
        return this == CONCRETE || this == BRICK || this == METAL || this == CAR;
    }

    private static boolean has(String path, String... words) {
        for (String w : words) {
            if (path.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
