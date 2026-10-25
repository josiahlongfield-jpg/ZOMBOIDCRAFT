package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.AABB;

/** Draws a car prop's 3D model, each triangle sent as a quad with its last corner doubled. */
public class CarRenderer extends EntityRenderer<CarProp, CarRenderState> {
    public CarRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public CarRenderState createRenderState() {
        return new CarRenderState();
    }

    @Override
    public void extractRenderState(CarProp car, CarRenderState state, float partialTick) {
        super.extractRenderState(car, state, partialTick);
        state.model = car.model();
        state.variant = car.variant();
        state.yaw = car.getYRot();
    }

    /** The whole car, not the entity's small box, decides whether it's on screen. */
    @Override
    protected AABB getBoundingBoxForCulling(CarProp car, float partialTick) {
        CarModels.Model m = CarModels.get(car.model());
        double r = m == null ? 3 : Math.hypot(m.size[0], m.size[2]) / 2;
        double h = m == null ? 2 : m.size[1];
        return new AABB(car.getX() - r, car.getY(), car.getZ() - r, car.getX() + r, car.getY() + h, car.getZ() + r);
    }

    @Override
    public void submit(CarRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        CarAssets.Ready car = CarAssets.get(state.model, state.variant);
        if (car == null) {
            return;
        }
        CarAssets.Mesh mesh = car.mesh();
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(car.texture()), (pose, out) -> {
            float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
            for (int t = 0; t < mesh.triangles(); t++) {
                for (int c = 0; c < 4; c++) {
                    int v = t * 3 + Math.min(c, 2);
                    out.addVertex(pose, p[v * 3], p[v * 3 + 1], p[v * 3 + 2])
                            .setColor(-1)
                            .setUv(uv[v * 2], uv[v * 2 + 1])
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(light)
                            .setNormal(pose, n[t * 3], n[t * 3 + 1], n[t * 3 + 2]);
                }
            }
        });
        poseStack.popPose();
    }
}
