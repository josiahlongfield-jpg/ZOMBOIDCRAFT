package com.zomboidcraft.client.zombie;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;

/** Draws the outfit's 3D pieces over the walker, posed like the body they sit on. */
public class WalkerAddonLayer extends RenderLayer<WalkerRenderState, WalkerModel> {
    private final WalkerAddonModel model;

    public WalkerAddonLayer(RenderLayerParent<WalkerRenderState, WalkerModel> parent, WalkerAddonModel model) {
        super(parent);
        this.model = model;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WalkerRenderState state,
            float yRot, float xRot) {
        if (state.addonTexture != null) {
            coloredCutoutModelCopyLayerRender(model, state.addonTexture, poseStack, collector, light, state, -1, 1);
        }
    }
}
