package com.zomboidcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.combat.RifleView;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On the sights, the walking bob is calmed (guns.aimSway). It's done inside bobView itself, so it holds wherever the
 * bob is called from: shader packs (Iris) draw the hands with their own call to it.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Unique
    private float zomboidcraft$bob;

    /** The bob's rocking: the view and the gun turn together, so it never pulls the sights off what they're on. */
    @Inject(method = "bobView", at = @At("HEAD"))
    private void zomboidcraft$calmBob(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        if (camera.entityRenderState != null) {
            zomboidcraft$bob = camera.entityRenderState.bob;
            camera.entityRenderState.bob *= RifleView.bobScale();
        }
    }

    @Inject(method = "bobView", at = @At("RETURN"))
    private void zomboidcraft$restoreBob(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
        if (camera.entityRenderState != null) {
            camera.entityRenderState.bob = zomboidcraft$bob;
        }
    }

    /**
     * The bob's sideways and up-down shift: harmless to the far-off world, but it throws a sight a few inches from the
     * eye right off the target, so on the sights almost none of it is kept.
     */
    @WrapOperation(method = "bobView", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void zomboidcraft$steadySights(PoseStack pose, float x, float y, float z, Operation<Void> original) {
        float keep = RifleView.bobShiftScale();
        original.call(pose, x * keep, y * keep, z * keep);
    }
}
