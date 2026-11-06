package com.zomboidcraft.mixin.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Lets the scope lens draw a copied frame with a pipeline of its own choosing (no blending), which the public blit of a
 * texture view doesn't take: a shader pack can leave its picture's alpha at nothing (ScopeView).
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsBlitInvoker {
    @Invoker("innerBlit")
    void zomboidcraft$innerBlit(RenderPipeline pipeline, GpuTextureView view, GpuSampler sampler, int x0, int y0, int x1, int y1,
            float u0, float u1, float v0, float v1, int color);
}
