package com.zomboidcraft.client.zombie;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

/** Red eyes that burn in the dark: the skin's eye mask drawn full-bright over the face. */
public class WalkerEyesLayer extends EyesLayer<WalkerRenderState, WalkerModel> {
    /** The mask for the walker being drawn; every walker has its own, but the layer asks without the state. */
    private Identifier mask;

    public WalkerEyesLayer(RenderLayerParent<WalkerRenderState, WalkerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, WalkerRenderState state,
            float yRot, float xRot) {
        if (state.eyesTexture != null) {
            mask = state.eyesTexture;
            super.submit(poseStack, collector, light, state, yRot, xRot);
        }
    }

    @Override
    public RenderType renderType() {
        return RenderTypes.entityTranslucentEmissive(mask);
    }
}
