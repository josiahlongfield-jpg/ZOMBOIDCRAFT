package com.zomboidcraft.client.creature;

import com.zomboidcraft.client.camera.CameraRig;
import com.zomboidcraft.creature.HarvestmanEntity;
import com.zomboidcraft.creature.Harvestmen;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The Harvestman on the client: its renderer, its presence (HarvestmanPresence: its hum, your unease when it's watching
 * you, the animals going quiet), the glimpses of it only the hunted get (HarvestmanPhantoms), and your view while it holds you (a hard, uneven shake as its scythes
 * grip and shift, and a slow sickening roll as it feeds).
 */
public final class HarvestmanClient {
    public static void register() {
        EntityRendererRegistry.register(Harvestmen.HARVESTMAN, HarvestmanRenderer::new);
        HarvestmanPresence.register();
        HarvestmanPhantoms.register();
        CameraRig.register(5, (camera, entity, partialTick, out) -> {
            Minecraft mc = Minecraft.getInstance();
            if (entity != mc.player || mc.options.getCameraType() != CameraType.FIRST_PERSON
                    || !(mc.player.getVehicle() instanceof HarvestmanEntity h)) {
                return false;
            }
            boolean feeding = h.pose() == HarvestmanEntity.FEED;
            float t = mc.player.tickCount + partialTick;
            // layered sines make a shake that never quite repeats; harder when it lunges and grips, rolling as it feeds
            float hard = feeding ? 0.6f : 1f;
            float yawJ = hard * (Mth.sin(t * 1.9f) * 1.1f + Mth.sin(t * 4.7f) * 0.5f);
            float pitchJ = hard * (Mth.sin(t * 2.3f + 1f) * 0.9f + Mth.sin(t * 5.3f) * 0.4f);
            float roll = feeding ? Mth.sin(t * 0.21f) * 9f + Mth.sin(t * 0.07f) * 4f : Mth.sin(t * 1.3f) * 3f;
            float yaw = mc.player.getViewYRot(partialTick) + yawJ, pitch = mc.player.getViewXRot(partialTick) + pitchJ;
            out.rotation.rotationYXZ((float) (Math.PI - Math.toRadians(yaw)), (float) -Math.toRadians(pitch),
                    (float) Math.toRadians(roll));
            return true;
        });
    }

    private HarvestmanClient() {
    }
}
