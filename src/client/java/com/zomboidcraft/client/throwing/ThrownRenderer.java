package com.zomboidcraft.client.throwing;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.combat.throwing.Thrown;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Draws a thrown thing: its own item model, tumbling about its middle as it flies, lying on its side once down. */
public class ThrownRenderer extends EntityRenderer<Thrown, ThrownRenderer.State> {
    public static class State extends EntityRenderState {
        public final ItemStackRenderState item = new ItemStackRenderState();
        public final Quaternionf turn = new Quaternionf();
    }

    public ThrownRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.12f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Thrown thrown, State state, float partialTick) {
        super.extractRenderState(thrown, state, partialTick);
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state.item, thrown.item(), ItemDisplayContext.GROUND,
                thrown.level(), null, thrown.getId());
        thrown.turnO.slerp(thrown.turn, partialTick, state.turn);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0, 0.125f, 0);
        poseStack.mulPose(new Matrix4f().rotation(state.turn));
        state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }
}
