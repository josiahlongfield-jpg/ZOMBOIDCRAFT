package com.zomboidcraft.client.camera;

import com.zomboidcraft.world.Breach;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The ground shaking when the breach surges (world/Breach): a low, rolling shake of the view on foot,
 * strongest near ground zero, easing off over its last second. Any other rig (a car, an aircraft, the
 * Harvestman's grip) goes first.
 */
public final class BreachTremor {
    private static float strength;
    private static int start, ticks;

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(Breach.Tremor.TYPE, (payload, context) -> context.client().execute(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                strength = payload.strength();
                ticks = payload.ticks();
                start = mc.player.tickCount;
            }
        }));
        CameraRig.register(90, (camera, entity, partialTick, out) -> {
            Minecraft mc = Minecraft.getInstance();
            if (strength <= 0 || entity != mc.player || mc.player.isPassenger() || mc.options.getCameraType() != CameraType.FIRST_PERSON) {
                return false;
            }
            float t = mc.player.tickCount - start + partialTick;
            if (t > ticks) {
                strength = 0;
                return false;
            }
            float s = strength * Math.min(1, (ticks - t) / 20f) * Math.min(1, t / 6f);
            float yaw = mc.player.getViewYRot(partialTick) + s * (Mth.sin(t * 2.7f) * 0.6f + Mth.sin(t * 6.1f) * 0.25f);
            float pitch = mc.player.getViewXRot(partialTick) + s * (Mth.sin(t * 3.3f + 1f) * 0.7f + Mth.sin(t * 7.9f) * 0.3f);
            float roll = s * Mth.sin(t * 1.7f) * 1.2f;
            out.rotation.rotationYXZ((float) (Math.PI - Math.toRadians(yaw)), (float) -Math.toRadians(pitch), (float) Math.toRadians(roll));
            return true;
        });
    }

    private BreachTremor() {
    }
}
