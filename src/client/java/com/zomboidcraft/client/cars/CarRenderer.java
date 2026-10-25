package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
        float t = car.lidOpenO + (car.lidOpen - car.lidOpenO) * partialTick;
        state.open = t * t * (3 - 2 * t);
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
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -state.yaw);
        float open = state.open;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(car.texture()), (pose, out) -> {
            draw(car.mesh(), null, 0, pose, out, light);
            for (CarAssets.Lid lid : car.lids()) {
                draw(lid.mesh(), lid, lid.angle() * open, pose, out, light);
            }
        });
        poseStack.popPose();
    }

    /** A mesh's triangles as quads (last corner doubled), a lid's turned about its hinge by angle radians. */
    private static void draw(CarAssets.Mesh mesh, CarAssets.Lid lid, float angle, PoseStack.Pose pose, VertexConsumer out, int light) {
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        boolean turn = lid != null && angle != 0;
        float[] v = new float[3], m = new float[3];
        for (int t = 0; t < mesh.triangles(); t++) {
            m[0] = n[t * 3]; m[1] = n[t * 3 + 1]; m[2] = n[t * 3 + 2];
            if (turn) {
                rotate(m, lid.yAxis(), cos, sin);
            }
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + Math.min(c, 2);
                v[0] = p[i * 3]; v[1] = p[i * 3 + 1]; v[2] = p[i * 3 + 2];
                if (turn) {
                    v[0] -= lid.px(); v[1] -= lid.py(); v[2] -= lid.pz();
                    rotate(v, lid.yAxis(), cos, sin);
                    v[0] += lid.px(); v[1] += lid.py(); v[2] += lid.pz();
                }
                out.addVertex(pose, v[0], v[1], v[2])
                        .setColor(-1)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, m[0], m[1], m[2]);
            }
        }
    }

    /** Same turns as tools/cars/wreck.py rot_x and rot_y. */
    private static void rotate(float[] v, boolean yAxis, float cos, float sin) {
        if (yAxis) {
            float x = v[0] * cos + v[2] * sin, z = -v[0] * sin + v[2] * cos;
            v[0] = x;
            v[2] = z;
        } else {
            float y = v[1] * cos - v[2] * sin, z = v[1] * sin + v[2] * cos;
            v[1] = y;
            v[2] = z;
        }
    }
}
