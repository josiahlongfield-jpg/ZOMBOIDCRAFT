package com.zomboidcraft.mixin.client;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.client.zombie.body.ZDetail;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only things near the camera cast shader shadows. Photon's shadow map is drawn from above over a wide area all round
 * you, so every zombie, car, corpse and prop in it was drawn a second time, behind you and behind walls too. The GPU
 * timers (2026-11-01 20:32 profile) put that at 104 ms of a 262 ms frame, more than all the buildings' shadows. Past
 * performance.shadowObjectDistance blocks an entity is left out of the shadow pass; it is still drawn normally.
 */
@Mixin(EntityRenderer.class)
public abstract class ShadowCasterMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true, require = 0)
    private void zomboidcraft$nearShadowsOnly(Entity entity, Frustum frustum, double camX, double camY, double camZ,
                                             float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        double reach = ZcConfig.get().performance.shadowObjectDistance;
        if (reach <= 0 || !ZDetail.shadowPass()) {
            return;
        }
        if (entity.distanceToSqr(camX, camY, camZ) > reach * reach) {
            cir.setReturnValue(false);
        }
    }
}
