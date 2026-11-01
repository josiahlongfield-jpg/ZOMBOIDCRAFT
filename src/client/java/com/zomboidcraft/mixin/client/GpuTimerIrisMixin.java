package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.perf.GpuTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU timers for /zc profile (GpuTimer): Photon's shadow, deferred and composite passes. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap = false)
public abstract class GpuTimerIrisMixin {
    @Inject(method = "renderShadows", at = @At("HEAD"), require = 0)
    private void zomboidcraft$renderShadowsStart(CallbackInfo ci) {
        GpuTimer.inShadow = true;
        GpuTimer.begin(GpuTimer.SHADOW);
    }

    @Inject(method = "renderShadows", at = @At("RETURN"), require = 0)
    private void zomboidcraft$renderShadowsEnd(CallbackInfo ci) {
        GpuTimer.end(GpuTimer.SHADOW);
        GpuTimer.inShadow = false;
    }

    @Inject(method = "beginTranslucents", at = @At("HEAD"), require = 0)
    private void zomboidcraft$beginTranslucentsStart(CallbackInfo ci) {
        GpuTimer.begin(GpuTimer.DEFERRED);
    }

    @Inject(method = "beginTranslucents", at = @At("RETURN"), require = 0)
    private void zomboidcraft$beginTranslucentsEnd(CallbackInfo ci) {
        GpuTimer.end(GpuTimer.DEFERRED);
        GpuTimer.afterDeferred = true;
    }

    @Inject(method = "finalizeLevelRendering", at = @At("HEAD"), require = 0)
    private void zomboidcraft$finalizeLevelRenderingStart(CallbackInfo ci) {
        GpuTimer.begin(GpuTimer.COMPOSITE);
    }

    @Inject(method = "finalizeLevelRendering", at = @At("RETURN"), require = 0)
    private void zomboidcraft$finalizeLevelRenderingEnd(CallbackInfo ci) {
        GpuTimer.end(GpuTimer.COMPOSITE);
    }
}
