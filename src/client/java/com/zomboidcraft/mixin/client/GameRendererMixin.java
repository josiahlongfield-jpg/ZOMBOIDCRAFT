package com.zomboidcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.combat.RifleView;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** On the sights, the walking bob of both the view and the held gun is scaled down (guns.aimSway). */
    @WrapOperation(method = {"renderItemInHand", "renderLevel"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"))
    private void zomboidcraft$steadyAim(GameRenderer renderer, CameraRenderState camera, PoseStack pose, Operation<Void> original) {
        float keep = RifleView.bobScale();
        CameraEntityRenderState entity = camera.entityRenderState;
        if (keep >= 1f || entity == null) {
            original.call(renderer, camera, pose);
            return;
        }
        float bob = entity.bob;
        entity.bob = bob * keep;
        original.call(renderer, camera, pose);
        entity.bob = bob;
    }
}
