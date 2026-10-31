package com.zomboidcraft.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cat's Eyes: a little of night vision's lift on the dark, all the time (the potion still wins when it's stronger). */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapCatsEyesMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void zomboidcraft$catsEyes(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        float lift = com.zomboidcraft.client.kit.SightTraits.nightVision(player);
        if (lift > state.nightVisionEffectIntensity) state.nightVisionEffectIntensity = lift;
    }
}
