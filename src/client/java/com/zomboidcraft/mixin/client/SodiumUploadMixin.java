package com.zomboidcraft.mixin.client;

import com.zomboidcraft.zombie.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spreads Sodium's chunk uploads over more frames when it has no persistent-mapped staging buffer.
 *
 * Without one, Sodium's MojangStagingBuffer writes every new chunk mesh straight into the GPU buffer
 * (glNamedBufferSubData) and allows 25.6 MB of that per frame. While a shader pack keeps the graphics card busy, each
 * of those writes waits for the card, so a burst of new chunks froze the frame (half of it in the 2026-11-01 16:28
 * profile). Capping the per-frame bytes leaves the rest queued for the next frames, which Sodium already supports.
 * Applies only when Sodium is installed; the mapped path keeps Sodium's own limit.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gpu.arena.staging.MojangStagingBuffer", remap = false)
public abstract class SodiumUploadMixin {
    /** Sodium's fallback limit, returned only on the unmapped path. */
    private static final long FALLBACK_LIMIT = 25_600_000L;
    /** Bytes of chunk mesh written per frame on the unmapped path: about 240 MB a second at 60 FPS. */
    private static final long CAP = 4_000_000L;

    @Inject(method = "getUploadSizeLimit(J)J", at = @At("RETURN"), cancellable = true, require = 0)
    private void zomboidcraft$capUploads(long frameDuration, CallbackInfoReturnable<Long> cir) {
        boolean fallback = cir.getReturnValueJ() == FALLBACK_LIMIT;
        Profiler.uploadPath = fallback ? "direct writes (no persistent mapping), capped to 4 MB a frame" : "mapped staging buffer";
        if (fallback) {
            cir.setReturnValue(CAP);
        }
    }
}
