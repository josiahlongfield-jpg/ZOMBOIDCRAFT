package com.zomboidcraft.client.reach;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.client.Minecraft;

/**
 * The Reach world's far drawing distance. The game only draws out to about four times the render distance (some
 * 500 m at render distance 8), so the Reach land, cities and ships past that would stop in a hard line. In the Reach
 * world the camera's far plane and cull frustum go out past the whole 16 km map instead, and the distance fog is
 * stretched to match, so far skylines fade into haze rather than end. Nothing else changes: the game's own blocks and
 * mobs still stop at the render distance, which is fine there because the walls you walk on are invisible.
 *
 * <p>Tunable for testing: {@code -Dzomboidcraft.reach.far=<blocks>} (0 turns the far pass off),
 * {@code -Dzomboidcraft.reach.fogStart} and {@code -Dzomboidcraft.reach.fogEnd}.
 */
public final class ReachFar {
    public static final float FAR = Float.parseFloat(System.getProperty("zomboidcraft.reach.far", "32000"));
    public static final float FOG_START = Float.parseFloat(System.getProperty("zomboidcraft.reach.fogStart", "900"));
    public static final float FOG_END = Float.parseFloat(System.getProperty("zomboidcraft.reach.fogEnd", "22000"));
    private static boolean told;

    private ReachFar() {
    }

    /** Whether the far pass is on for the world being drawn. */
    public static boolean active() {
        return FAR > 0 && ReachMeshes.inReach(Minecraft.getInstance().level);
    }

    /** The camera's far plane: the game's own, or the Reach distance when that's further. */
    public static float far(float vanilla) {
        if (!active() || vanilla >= FAR) {
            return vanilla;
        }
        if (!told) {
            told = true;
            ZomboidCraft.LOGGER.info("Reach world: far plane {} -> {} blocks, fog {}..{}", vanilla, FAR, FOG_START, FOG_END);
        }
        return FAR;
    }
}
