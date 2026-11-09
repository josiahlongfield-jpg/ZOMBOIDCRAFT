package com.zomboidcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.hud.SpartanFigure;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Armory's Spartan rides a dropped item's render state into the entity-in-GUI renderer; here it's drawn instead. */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$spartan(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (SpartanFigure.submit(state, pose, collector)) ci.cancel();
    }
}
