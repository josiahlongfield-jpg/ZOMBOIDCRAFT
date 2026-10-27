package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.world.cars.AirBreakup;
import com.zomboidcraft.world.cars.AirDebris;
import com.zomboidcraft.world.cars.CarModels;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Draws a piece of a crashed aircraft: its part of the burnt mesh, tumbled about its own middle. */
public class DebrisRenderer extends EntityRenderer<AirDebris, DebrisRenderer.State> {
    public static class State extends EntityRenderState {
        public String model = "jet_corvid", variant = "burnt";
        public int piece;
        public final Quaternionf turn = new Quaternionf();
    }

    public DebrisRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AirDebris debris, State state, float partialTick) {
        super.extractRenderState(debris, state, partialTick);
        state.model = debris.model();
        state.variant = debris.variant();
        state.piece = debris.piece();
        debris.turnO.slerp(debris.turn, partialTick, state.turn);
    }

    @Override
    protected AABB getBoundingBoxForCulling(AirDebris debris, float partialTick) {
        return debris.getBoundingBox().inflate(8);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        CarAssets.Broken broken = CarAssets.broken(state.model, state.variant);
        CarModels.Model spec = CarModels.get(state.model);
        if (broken == null || spec == null || state.piece < 0 || state.piece >= broken.pieces().size()) {
            return;
        }
        CarAssets.Mesh mesh = broken.pieces().get(state.piece);
        float[] a = AirBreakup.pieces(state.model).get(state.piece).anchor(spec.size);
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.mulPose(new Matrix4f().rotation(state.turn));
        poseStack.translate(-a[0], -a[1], -a[2]);
        var texture = broken.remains().texture();
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, out) -> CarRenderer.drawPiece(mesh, pose, out, light, false));
        if (mesh.triangles() > mesh.opaque()) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(texture),
                    (pose, out) -> CarRenderer.drawPiece(mesh, pose, out, light, true));
        }
        poseStack.popPose();
    }
}
