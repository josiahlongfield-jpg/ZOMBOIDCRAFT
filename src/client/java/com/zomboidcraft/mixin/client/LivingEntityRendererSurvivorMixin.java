package com.zomboidcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * On 26.3 a model is only posed when its submit is drawn, after every layer has submitted. A player drawn as a
 * Zomboid survivor is posed here first, so the survivor layer (and the layers that step aside for it) know this
 * frame's pose while they submit. The draw then poses it again, the same.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererSurvivorMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), require = 1)
    private void zomboidcraft$poseSurvivor(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                           CameraRenderState camera, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && com.zomboidcraft.client.survivor.Survivors.drawsAsSurvivor(avatar)
                && ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel() instanceof PlayerModel model) {
            model.setupAnim(avatar);
        }
    }
}
