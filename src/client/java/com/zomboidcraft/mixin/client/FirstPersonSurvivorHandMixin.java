package com.zomboidcraft.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.survivor.SurvivorArms;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Minecraft's first-person arm (an empty hand, the hands on a map) is a Zomboid survivor's own arm when she has one. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonSurvivorHandMixin {
    @Inject(method = "renderPlayerHand", at = @At("HEAD"), cancellable = true, require = 1)
    private void zomboidcraft$survivorHand(PoseStack pose, SubmitNodeCollector out, int light, HumanoidArm arm,
                                           PlayerRenderState state, CallbackInfo ci) {
        // the Tarkov operator shows no arm with nothing in his hands (Josiah 2026-11-06: no bent Zomboid arm on screen)
        if (com.zomboidcraft.client.operator.Operator.active(net.minecraft.client.Minecraft.getInstance().player)
                || SurvivorArms.vanillaHand(pose, out, light, arm)) ci.cancel();
    }
}
