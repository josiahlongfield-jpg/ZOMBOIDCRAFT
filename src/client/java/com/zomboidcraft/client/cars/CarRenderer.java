package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * Draws a car's 3D model, each triangle sent as a quad with its last corner doubled: the body,
 * boot lids swung open while it's searched, wheels rolled and steered, then the glass see-through.
 * Cars further off are drawn from coarser copies of the mesh ({@link CarLod}), with solid
 * windows past a point.
 */
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
        // turned smoothly between ticks, or a car you're steering judders against your smooth view
        state.yaw = car.getYRot(partialTick);
        // from the driver's seat the windows are left out: shaders fog see-through glass right in front of you
        Minecraft mc = Minecraft.getInstance();
        state.inside = mc.player != null && car.hasPassenger(mc.player) && mc.options.getCameraType().isFirstPerson();
        Entity eye = mc.getCameraEntity();
        state.lod = eye == null || car.hasPassenger(eye) ? 0 : CarLod.level(car.distanceToSqr(eye));
        float t = car.lidOpenO + (car.lidOpen - car.lidOpenO) * partialTick;
        state.open = t * t * (3 - 2 * t);
        state.rolled = car.rolledO + (car.rolled - car.rolledO) * partialTick;
        state.steer = car.steerO + (car.steer - car.steerO) * partialTick;
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
        CarAssets.Ready car = CarAssets.get(state.model, state.variant, state.lod);
        if (car == null) {
            return;
        }
        int light = state.lightCoords;
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -state.yaw);
        Turn[] lids = new Turn[car.lids().size()];
        for (int i = 0; i < lids.length; i++) {
            lids[i] = lid(car.lids().get(i), state.open);
        }
        Turn[] wheels = new Turn[car.wheels().size()];
        for (int i = 0; i < wheels.length; i++) {
            wheels[i] = wheel(car.wheels().get(i), state.rolled, state.steer);
        }
        boolean solidGlass = state.lod >= CarLod.SOLID_GLASS;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(car.texture()), (pose, out) -> {
            each(car, lids, wheels, (mesh, turn) -> {
                draw(mesh, 0, mesh.opaque(), turn, pose, out, light, -1);
                if (solidGlass) {
                    draw(mesh, mesh.opaque(), mesh.triangles(), turn, pose, out, light, FAR_GLASS);
                }
            });
        });
        if (!state.inside && !solidGlass) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(car.texture()), (pose, out) -> {
                each(car, lids, wheels, (mesh, turn) -> draw(mesh, mesh.opaque(), mesh.triangles(), turn, pose, out, light, GLASS));
            });
        }
        poseStack.popPose();
    }

    /** Glass keeps its colour from the texture but lets most of the light through. */
    private static final int GLASS = 0x80FFFFFF;
    /** Far off, glass is drawn solid, darkened to look like the dim inside seen through it. */
    private static final int FAR_GLASS = 0xFF5A6068;

    /** Moves a point (or, with normal set, a direction) of a moving part into place, in place. */
    private interface Turn {
        void apply(float[] v, boolean normal);
    }

    private static void each(CarAssets.Ready car, Turn[] lids, Turn[] wheels, java.util.function.BiConsumer<CarAssets.Mesh, Turn> part) {
        part.accept(car.mesh(), null);
        for (int i = 0; i < lids.length; i++) {
            part.accept(car.lids().get(i).mesh(), lids[i]);
        }
        for (int i = 0; i < wheels.length; i++) {
            part.accept(car.wheels().get(i).mesh(), wheels[i]);
        }
    }

    /** A lid turned about its hinge by open (0 to 1) of its full swing. */
    private static Turn lid(CarAssets.Lid lid, float open) {
        float a = lid.angle() * open;
        if (a == 0) {
            return null;
        }
        float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
        return (v, normal) -> {
            if (normal) {
                rotate(v, lid.yAxis(), cos, sin);
                return;
            }
            v[0] -= lid.px(); v[1] -= lid.py(); v[2] -= lid.pz();
            rotate(v, lid.yAxis(), cos, sin);
            v[0] += lid.px(); v[1] += lid.py(); v[2] += lid.pz();
        };
    }

    /** A wheel rolled on by rolled metres and, if it's a front wheel, steered by steer radians (left +). */
    private static Turn wheel(CarAssets.Wheel w, double rolled, float steer) {
        double spin = (rolled / Math.max(0.1f, w.radius())) % (Math.PI * 2);
        float sc = (float) Math.cos(spin), ss = (float) Math.sin(spin);
        float s = w.steers() ? steer : 0;
        float tc = (float) Math.cos(s), ts = (float) Math.sin(s);
        return (v, normal) -> {
            if (!normal) {
                v[0] -= w.cx(); v[1] -= w.cy(); v[2] -= w.cz();
            }
            rotate(v, false, sc, ss);
            if (s != 0) {
                rotate(v, true, tc, ts);
            }
            if (!normal) {
                v[0] += w.cx(); v[1] += w.cy(); v[2] += w.cz();
            }
        };
    }

    /** Triangles from to end of a mesh as quads (last corner doubled), moved by turn if it has one. */
    private static void draw(CarAssets.Mesh mesh, int from, int end, Turn turn, PoseStack.Pose pose, VertexConsumer out, int light, int colour) {
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        float[] v = new float[3], m = new float[3];
        for (int t = from; t < end; t++) {
            m[0] = n[t * 3]; m[1] = n[t * 3 + 1]; m[2] = n[t * 3 + 2];
            if (turn != null) {
                turn.apply(m, true);
            }
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + Math.min(c, 2);
                v[0] = p[i * 3]; v[1] = p[i * 3 + 1]; v[2] = p[i * 3 + 2];
                if (turn != null) {
                    turn.apply(v, false);
                }
                out.addVertex(pose, v[0], v[1], v[2])
                        .setColor(colour)
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
