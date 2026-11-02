package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.perf.GpuTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU timers for /zc profile (GpuTimer): the whole frame and the world. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.GameRenderer", remap = false)
public abstract class GpuTimerGameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void zomboidcraft$frameStart(CallbackInfo ci) {
        GpuTimer.frameStart();
        GpuTimer.begin(GpuTimer.FRAME);
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void zomboidcraft$frameEnd(CallbackInfo ci) {
        GpuTimer.end(GpuTimer.FRAME);
    }

    @Inject(method = "renderLevel", at = @At("HEAD"), require = 0)
    private void zomboidcraft$renderLevelStart(CallbackInfo ci) {
        GpuTimer.begin(GpuTimer.WORLD);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void zomboidcraft$renderLevelEnd(CallbackInfo ci) {
        GpuTimer.end(GpuTimer.WORLD);
    }
}
