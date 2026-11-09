package com.zomboidcraft.client.reach;

import net.minecraft.client.Minecraft;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The region sky where the camera is now (ReachSkies), kept for the frame so the many reads the game makes each frame
 * cost nothing, and handed to the game in place of the dimension's own sky and fog settings.
 */
public final class ReachSkyState {
    private static ReachSkies.Blend blend;
    private static double bx = Double.NaN, by, bz;
    private static Vector3f sky, fog;
    private static int lastRegion = -1;

    private ReachSkyState() {
    }

    /** The blended sky at the camera, or null outside the Reach world or off the render thread. */
    public static ReachSkies.Blend current() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread() || !ReachMeshes.inReach(mc.level)) {
            return null;
        }
        Vec3 eye = mc.gameRenderer.mainCamera().position();
        if (blend == null || Math.abs(eye.x - bx) + Math.abs(eye.z - bz) > 4 || Math.abs(eye.y - by) > 4) {
            ReachSkies.Blend b = ReachSkies.at(eye.x, eye.y, eye.z);
            if (b == null) {
                return null;
            }
            blend = b;
            bx = eye.x;
            by = eye.y;
            bz = eye.z;
            sky = colour(b.skyColour());
            fog = colour(b.fogColour());
            if (b.main != lastRegion) {
                lastRegion = b.main;
                com.zomboidcraft.ZomboidCraft.LOGGER.info("Reach world: sky region {}", ReachSkies.REGIONS[b.main].name());
            }
        }
        return blend;
    }

    /** What the game should see for this attribute in the Reach world, or null to leave its own value. */
    public static Object override(EnvironmentAttribute<?> attribute, Object vanilla) {
        if (attribute != EnvironmentAttributes.SKY_COLOR && attribute != EnvironmentAttributes.FOG_COLOR
                && attribute != EnvironmentAttributes.SKY_LIGHT_FACTOR && attribute != EnvironmentAttributes.STAR_BRIGHTNESS) {
            return null;
        }
        ReachSkies.Blend b = current();
        if (b == null) {
            return null;
        }
        if (attribute == EnvironmentAttributes.SKY_COLOR) {
            return sky;
        }
        if (attribute == EnvironmentAttributes.FOG_COLOR) {
            return fog;
        }
        if (attribute == EnvironmentAttributes.SKY_LIGHT_FACTOR && vanilla instanceof Float f) {
            // a night region (New Alexandria) is dark whatever the clock says
            return f * (1 - 0.8f * b.dark);
        }
        if (attribute == EnvironmentAttributes.STAR_BRIGHTNESS && vanilla instanceof Float f) {
            return Math.max(f, b.dark * 0.6f);
        }
        return null;
    }

    private static Vector3f colour(int rgb) {
        return new Vector3f((rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f);
    }
}
