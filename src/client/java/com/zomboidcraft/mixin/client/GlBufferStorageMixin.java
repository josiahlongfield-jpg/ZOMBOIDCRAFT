package com.zomboidcraft.mixin.client;

import com.zomboidcraft.ZcConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Lets an NVIDIA card use OpenGL buffer storage (persistent mapping) again.
 *
 * 26.3's GlDevice turns GL_ARB_buffer_storage off on NVIDIA (and old Intel) by passing
 * {@code couldBeIntelGen7() || isNvidia()} into BufferStorage.create as its "don't" flag. Without it the device reports
 * no persistent mapping, so Sodium falls back to writing every chunk mesh with glNamedBufferSubData, which waits on the
 * graphics card whenever a shader pack keeps it busy: half the frame in Josiah's 2026-11-01 city profiles (RTX 4070,
 * Photon). This clears that flag on NVIDIA so Sodium uses its mapped staging buffer. performance.nvidiaBufferStorage
 * off restores Minecraft's choice (needs a restart).
 */
@Pseudo
@Mixin(targets = "com.mojang.renderpearl.backend.opengl.BufferStorage", remap = false)
public abstract class GlBufferStorageMixin {
    @ModifyVariable(method = "create", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private static boolean zomboidcraft$keepBufferStorage(boolean avoid) {
        boolean keep = ZcConfig.get().performance.nvidiaBufferStorage;
        String vendor = org.lwjgl.opengl.GL11C.glGetString(org.lwjgl.opengl.GL11C.GL_VENDOR);
        boolean nvidia = vendor != null && vendor.toLowerCase(java.util.Locale.ROOT).contains("nvidia");
        com.zomboidcraft.zombie.Profiler.bufferStorageFix = "ran at start-up (card " + vendor + ", Minecraft wanted it "
                + (avoid ? "off" : "on") + ", now " + (avoid && !(nvidia && keep) ? "off" : "on") + ")";
        // only undo the NVIDIA rule: old Intel cards keep Minecraft's choice
        return avoid && !(nvidia && keep);
    }
}
