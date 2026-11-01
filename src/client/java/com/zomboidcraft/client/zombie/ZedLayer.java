package com.zomboidcraft.client.zombie;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.client.survivor.ZedBodies;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Draws a walker as Project Zomboid's own zombie where WalkerRenderer chose to (survivor/ZedBodies). */
public class ZedLayer extends RenderLayer<WalkerRenderState, WalkerModel> {
    public ZedLayer(RenderLayerParent<WalkerRenderState, WalkerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WalkerRenderState state,
            float yRot, float xRot) {
        if (state.zed == null || state.isInvisible) {
            return;
        }
        ZedBodies.submit(state.zed, poseStack, collector, light, LivingEntityRenderer.getOverlayCoords(state, 0f));
    }
}
