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
        state.smashed = car.smashed();
        state.inside = mc.player != null && car.hasPassenger(mc.player) && mc.options.getCameraType().isFirstPerson();
        Entity eye = mc.getCameraEntity();
        state.lod = eye == null || car.hasPassenger(eye) ? 0 : CarLod.level(car.distanceToSqr(eye));
        float t = car.lidOpenO + (car.lidOpen - car.lidOpenO) * partialTick;
        state.open = t * t * (3 - 2 * t);
        state.rolled = car.rolledO + (car.rolled - car.rolledO) * partialTick;
        state.steer = car.steerO + (car.steer - car.steerO) * partialTick;
        state.lights = CarLights.on(car);
        state.braking = state.lights && car.braking > 0;
        state.reversing = state.lights && car.reversing > 0;
        state.dark = state.lights && mc.level != null ? CarLights.darkness(mc.level, car.blockPosition().above()) : 0;
        state.shaders = state.lights && CarLights.shaderPackOn();
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
        boolean solidGlass = state.lod >= CarLod.SOLID_GLASS && !state.smashed;
        CarAssets.Ready full = !state.lights ? null : state.lod == 0 ? car : CarAssets.get(state.model, state.variant, 0);
        CarLamps.Lamps lamps = full == null ? null : CarLamps.get(state.model, state.variant, full);
        float[] glow = lamps != null && state.lod == 0 && state.dark > 0.05f ? cabinGlow(lamps, state.dark) : null;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(car.texture()), (pose, out) -> {
            each(car, lids, wheels, (mesh, turn) -> {
                draw(mesh, 0, mesh.opaque(), turn, pose, out, light, -1, mesh == car.mesh() ? glow : null);
                if (solidGlass) {
                    draw(mesh, mesh.opaque(), mesh.triangles(), turn, pose, out, light, FAR_GLASS, null);
                }
            });
        });
        if (!state.inside && !state.smashed && !solidGlass) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(car.texture()), (pose, out) -> {
                each(car, lids, wheels, (mesh, turn) -> draw(mesh, mesh.opaque(), mesh.triangles(), turn, pose, out, light, GLASS, null));
            });
        }
        if (lamps != null) {
            // the camera, in the car's own frame, for the glow round each lamp
            double dx = camera.pos.x - state.x, dz = camera.pos.z - state.z, r = Math.toRadians(state.yaw);
            float[] eye = {(float) (dx * Math.cos(r) + dz * Math.sin(r)), (float) (camera.pos.y - state.y),
                    (float) (-dx * Math.sin(r) + dz * Math.cos(r))};
            collector.submitCustomGeometry(poseStack, CarLights.glowType(state.shaders),
                    (pose, out) -> lamps(state, lamps, full, lids, eye, pose, out));
        }
        poseStack.popPose();
    }

    /** Lamp colours: a warm headlight, tail and brake red, reversing white, and the dash's green. */
    private static final float[] HEAD = {1f, 0.93f, 0.78f}, RED = {1f, 0.1f, 0.06f}, WHITE = {1f, 1f, 1f}, DASH = {0.4f, 1f, 0.6f};

    /**
     * The lit lamps: each lens redrawn glowing just in front of itself, then a soft glow round each lamp
     * facing you, in the dark. Tail lights are dim until the brakes go on; reversing lights only in reverse.
     */
    private static void lamps(CarRenderState state, CarLamps.Lamps lamps, CarAssets.Ready full, Turn[] lids, float[] eye,
                              PoseStack.Pose pose, VertexConsumer out) {
        float day = 0.5f + 0.5f * state.dark;
        for (CarLamps.Run run : lamps.runs()) {
            float[] rgb;
            float strength;
            switch (run.kind()) {
                case CarLamps.HEAD -> { rgb = HEAD; strength = 0.9f * day; }
                case CarLamps.TAIL -> { rgb = RED; strength = state.braking ? 1f : 0.45f * day; }
                case CarLamps.REVERSE -> { rgb = WHITE; strength = state.reversing ? 0.9f : 0; }
                default -> { rgb = DASH; strength = 0.75f * day; }
            }
            if (strength <= 0) {
                continue;
            }
            Turn turn = run.mesh() > 0 && run.mesh() - 1 < lids.length ? lids[run.mesh() - 1] : null;
            glowing(CarLamps.mesh(full, run.mesh()), run.start(), run.start() + run.count(), turn, pose, out,
                    CarLights.shade(rgb, strength, state.shaders));
        }
        if (state.dark < 0.03f) {
            return;
        }
        for (CarLamps.Spot spot : lamps.spots()) {
            float[] rgb;
            float strength, size;
            switch (spot.kind()) {
                case CarLamps.HEAD -> { rgb = HEAD; strength = 0.5f; size = 1.6f; }
                case CarLamps.TAIL -> { rgb = RED; strength = state.braking ? 0.65f : 0.3f; size = state.braking ? 1.5f : 1.1f; }
                case CarLamps.REVERSE -> { rgb = WHITE; strength = state.reversing ? 0.4f : 0; size = 1.2f; }
                default -> { continue; }
            }
            float cx = spot.x() + spot.nx() * 0.06f, cy = spot.y() + spot.ny() * 0.06f, cz = spot.z() + spot.nz() * 0.06f;
            float tx = eye[0] - cx, ty = eye[1] - cy, tz = eye[2] - cz;
            float len = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (strength <= 0 || len < 0.3f) {
                continue;
            }
            tx /= len; ty /= len; tz /= len;
            float facing = tx * spot.nx() + ty * spot.ny() + tz * spot.nz();
            if (facing < 0.05f) {
                continue;       // seen from behind the lamp
            }
            halo(cx, cy, cz, tx, ty, tz, spot.size() * size, pose, out,
                    CarLights.shade(rgb, strength * state.dark * (float) Math.sqrt(facing), state.shaders));
        }
    }

    /** Triangles from to end redrawn a few millimetres proud of the car, in one flat glowing colour. */
    private static void glowing(CarAssets.Mesh mesh, int from, int end, Turn turn, PoseStack.Pose pose, VertexConsumer out, int colour) {
        float[] p = mesh.pos(), n = mesh.normal();
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
                out.addVertex(pose, v[0] + m[0] * 0.004f, v[1] + m[1] * 0.004f, v[2] + m[2] * 0.004f)
                        .setColor(colour)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(CarLights.FULL_BRIGHT)
                        .setNormal(pose, m[0], m[1], m[2]);
            }
        }
    }

    /** A soft disc of light at c, half-size half, turned to face the way t (toward the camera). */
    private static void halo(float cx, float cy, float cz, float tx, float ty, float tz, float half, PoseStack.Pose pose,
                             VertexConsumer out, int colour) {
        // right = t x up, then up = right x t; wound so the quad faces t
        float rx = -tz, rz = tx, rl = (float) Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        float ux = -rz * ty, uy = rz * tx - rx * tz, uz = rx * ty;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        rx *= half;
        rz *= half;
        ux *= half / ul;
        uy *= half / ul;
        uz *= half / ul;
        float[][] corners = {{-1, -1, 0, 0}, {-1, 1, 0, 1}, {1, 1, 1, 1}, {1, -1, 1, 0}};
        for (float[] k : corners) {
            out.addVertex(pose, cx + rx * k[0] + ux * k[1], cy + uy * k[1], cz + rz * k[0] + uz * k[1])
                    .setColor(colour)
                    .setUv(k[2], k[3])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(CarLights.FULL_BRIGHT)
                    .setNormal(pose, tx, ty, tz);
        }
    }

    /**
     * Where the dash's glow comes from, a little back from the dials toward the driver, and how strong
     * it is: {x, y, z, strength}.
     */
    private static float[] cabinGlow(CarLamps.Lamps lamps, float dark) {
        CarLamps.Spot dash = lamps.first(CarLamps.DASH);
        if (dash == null) {
            return null;
        }
        return new float[]{dash.x() + dash.nx() * 0.3f, dash.y() + dash.ny() * 0.3f, dash.z() + dash.nz() * 0.3f, dark};
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

    /**
     * Triangles from to end of a mesh as quads (last corner doubled), moved by turn if it has one; with a
     * glow ({x, y, z, strength}, from {@link #cabinGlow}), the inside round the dash is lit by it.
     */
    private static void draw(CarAssets.Mesh mesh, int from, int end, Turn turn, PoseStack.Pose pose, VertexConsumer out, int light, int colour,
                             float[] glow) {
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        int block = (light >> 4) & 0xF, sky = (light >> 20) & 0xF;
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
                int lit = light;
                if (glow != null) {
                    float gx = glow[0] - v[0], gy = glow[1] - v[1], gz = glow[2] - v[2];
                    float d = (float) Math.sqrt(gx * gx + gy * gy + gz * gz);
                    // faces turned toward the dash, near it: the cabin, not the outside of the body
                    if (d < 1.5f && gx * m[0] + gy * m[1] + gz * m[2] > 0) {
                        int b = Math.round(glow[3] * 9 * (1 - d / 1.5f));
                        if (b > block) {
                            lit = sky << 20 | b << 4;
                        }
                    }
                }
                out.addVertex(pose, v[0], v[1], v[2])
                        .setColor(colour)
                        .setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(lit)
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
