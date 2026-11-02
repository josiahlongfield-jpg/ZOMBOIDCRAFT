package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.perf.GpuTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU timers for /zc profile (GpuTimer): objects (entities, block entities) drawn solid in the main view. */
@Pseudo
@Mixin(targets = "net.minecraft.client.renderer.LevelRenderer", remap = false)
public abstract class GpuTimerLevelRendererMixin {
    @Inject(method = "executeSolid", at = @At("HEAD"), require = 0)
    private void zomboidcraft$executeSolidStart(CallbackInfo ci) {
        if (GpuTimer.inShadow) { return; }
        GpuTimer.begin(GpuTimer.OBJECTS);
    }

    @Inject(method = "executeSolid", at = @At("RETURN"), require = 0)
    private void zomboidcraft$executeSolidEnd(CallbackInfo ci) {
        GpuTimer.end(GpuTimer.OBJECTS);
    }
}
