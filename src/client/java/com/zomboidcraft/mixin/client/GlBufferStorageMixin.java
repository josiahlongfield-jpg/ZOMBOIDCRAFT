package com.zomboidcraft.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.zomboidcraft.ZcConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets an NVIDIA card use OpenGL buffer storage (persistent mapping) again.
 *
 * 26.3's GlDevice turns GL_ARB_buffer_storage off on NVIDIA (and old Intel) by passing isNvidia() into
 * BufferStorage.create. Without it the device reports no persistent mapping, so Sodium falls back to writing every
 * chunk mesh with glNamedBufferSubData, which waits on the graphics card whenever a shader pack keeps it busy: half the
 * frame in Josiah's 2026-11-01 city profiles (RTX 4070, Photon). With it Sodium uses its mapped staging buffer.
 * performance.nvidiaBufferStorage off restores Minecraft's choice (needs a restart).
 */
@Pseudo
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlDevice", remap = false)
public abstract class GlBufferStorageMixin {
    @ModifyExpressionValue(method = "<init>", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/opengl/GlHeuristics;isNvidia()Z"))
    private boolean zomboidcraft$keepBufferStorage(boolean nvidia) {
        boolean keep = ZcConfig.get().performance.nvidiaBufferStorage;
        com.zomboidcraft.zombie.Profiler.bufferStorageFix = "ran at start-up (NVIDIA card: " + (nvidia ? "yes" : "no")
                + ", buffer storage kept on: " + (nvidia && keep ? "yes" : nvidia ? "no, switched off in config" : "not needed") + ")";
        return nvidia && !keep;
    }
}
