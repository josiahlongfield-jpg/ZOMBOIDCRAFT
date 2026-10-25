package com.zomboidcraft.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.combat.RifleView;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHandsRendererMixin {
    /** The held rifle is posed and drawn by RifleView, with the player's arms and the loose reload pieces. */
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void zomboidcraft$rifle(ItemStackRenderState item, PoseStack pose, SubmitNodeCollector out, int light, int overlay,
                                    int outline, Operation<Void> original, @Local(argsOnly = true) PlayerRenderState state,
                                    @Local(argsOnly = true) InteractionHand hand, @Local(argsOnly = true) ItemStack stack) {
        Runnable draw = () -> original.call(item, pose, out, light, overlay, outline);
        if (hand != InteractionHand.MAIN_HAND || !RifleView.submit(item, pose, out, light, state, stack, draw)) {
            draw.run();
        }
    }
}
