package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.perf.GpuTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GPU timers for /zc profile (GpuTimer): Sodium's block and building draws, split into shadow, solid and see-through. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
public abstract class GpuTimerSodiumMixin {
    private static String zomboidcraft$label() {
        return GpuTimer.inShadow ? GpuTimer.SHADOW_TERRAIN : GpuTimer.afterDeferred ? GpuTimer.TRANSLUCENT : GpuTimer.TERRAIN;
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void zomboidcraft$renderStart(CallbackInfo ci) {
        GpuTimer.begin(zomboidcraft$label());
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void zomboidcraft$renderEnd(CallbackInfo ci) {
        GpuTimer.end(zomboidcraft$label());
    }
}
