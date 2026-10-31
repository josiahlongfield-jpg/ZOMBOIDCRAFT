package com.zomboidcraft.client.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.creature.spider.SpiderDraw;
import com.zomboidcraft.creature.HarvestmanEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Draws a Harvestman: its body as the model has it, its scythes and beard hunting or closed in the feeding grip, and
 * each leg's thigh and lower leg where its rig (HarvestmanRig) puts them this frame, with the lower legs that were shot
 * off lying where they fell. The model's colours are baked into its corners (on a plain white texture). Its red seams
 * and core glow, darkened as far as it chooses to hide (black while it roams and hunts, lit when it feeds), and pulse
 * while it feeds; its six small eyes always glow, and flare when a light catches them. Red-tinted for a moment when hit. Past about twenty blocks it's drawn
 * from lighter models
 * (near, middle, far; each about a fifth to a twelfth of the preview mesh), and its shadow always from the far one.
 */
public class HarvestmanRenderer extends EntityRenderer<HarvestmanEntity, HarvestmanRenderer.State> {
    private static final Identifier WHITE = ZomboidCraft.id("textures/entity/harvestman/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Past these it's drawn from the middle and then the far model (tools/creatures/harvestman/lod.py). */
    private static final double MID = 8, FAR = 20;
    /** What its red goes to when it hides: the black of its plates. */
    private static final int DARK_R = 14, DARK_G = 12, DARK_B = 12;

    public static class State extends EntityRenderState {
        boolean ready, feedArms, hurt, shaders;
        /** Its Spider 7 body, posed this frame; null if that model's missing (the old one's drawn). */
        SpiderDraw spider;
        int lod;
        int lost;
        float camo, pulse, eyeShine;
        /** Body, then thigh and lower leg for each leg, relative to where the entity is drawn. */
        final Matrix4f[] parts = new Matrix4f[9];
        final List<Matrix4f> flying = new ArrayList<>();
        final List<Integer> flyingLeg = new ArrayList<>();

        public State() {
            for (int i = 0; i < parts.length; i++) {
                parts[i] = new Matrix4f();
            }
        }
    }

    public HarvestmanRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    protected AABB getBoundingBoxForCulling(HarvestmanEntity entity, float partialTick) {
        // its legs reach out four blocks and more, any way round
        return entity.getBoundingBox().inflate(5);
    }

    @Override
    public void extractRenderState(HarvestmanEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        HarvestmanModel model = HarvestmanModel.get();
        state.ready = model != null;
        if (model == null) {
            return;
        }
        HarvestmanRig rig = entity.rig instanceof HarvestmanRig r ? r : new HarvestmanRig();
        entity.rig = rig;
        Vec3 drawnAt = entity.getPosition(partialTick);
        Quaternionf q = new Quaternionf(entity.turnO).slerp(entity.turn, partialTick);
        rig.update(entity, entity.level(), drawnAt.add(0, HarvestmanEntity.MID, 0), drawnAt, q, partialTick);
        // its body now is the Spider 7 creature, playing its own animations; the old rig still works out its hiding
        state.spider = SpiderDraw.of(entity);
        if (state.spider != null) {
            state.spider.update(entity, drawnAt, q, rig.camo, rig.glowPulse, partialTick);
            state.shaders = com.zomboidcraft.client.cars.CarLights.shaderPackOn();
            state.hurt = entity.hurtTime > 0 && entity.isAlive();
            return;
        }
        state.parts[0].set(rig.body);
        for (int li = 0; li < 4; li++) {
            state.parts[1 + li * 2].set(rig.thigh[li]);
            state.parts[2 + li * 2].set(rig.shin[li]);
        }
        state.lost = rig.lost;
        state.camo = rig.camo;
        state.pulse = rig.glowPulse;
        state.eyeShine = rig.eyeShine;
        state.feedArms = rig.feedArms;
        state.hurt = entity.hurtTime > 0 && entity.isAlive();
        state.flying.clear();
        state.flyingLeg.clear();
        for (HarvestmanRig.Flying f : rig.flying) {
            state.flying.add(new Matrix4f(f.at));
            state.flyingLeg.add(f.leg);
        }
        state.lod = state.distanceToCameraSq > FAR * FAR ? 2 : state.distanceToCameraSq > MID * MID ? 1 : 0;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.ready) {
            return;
        }
        if (state.spider != null) {
            state.spider.submit(poseStack, collector, state.lightCoords, state.shaders);
            return;
        }
        HarvestmanModel.Part[][] levels = HarvestmanModel.get().levels;
        // a shader pack's shadows only need its outline: the far model
        int lod = com.zomboidcraft.client.zombie.body.ZDetail.shadowPass() ? 2 : state.lod;
        HarvestmanModel.Part[] level = levels[Math.min(lod, levels.length - 1)];
        int light = state.lightCoords, overlay = OverlayTexture.pack(0, state.hurt ? 3 : 10);
        float camo = state.camo, pulse = state.pulse;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHITE), (pose, out) -> {
            emit(level[HarvestmanModel.BODY], state.parts[0], pose, out, light, overlay, camo, pulse);
            emit(level[state.feedArms ? HarvestmanModel.ARMS_FEED : HarvestmanModel.ARMS_HUNT], state.parts[0], pose, out, light, overlay, camo, pulse);
            for (int li = 0; li < 4; li++) {
                boolean front = (li & 1) == 0;
                emit(level[front ? HarvestmanModel.THIGH_F : HarvestmanModel.THIGH_R], state.parts[1 + li * 2], pose, out, light, overlay, camo, pulse);
                if ((state.lost >> li & 1) == 0) {
                    emit(level[front ? HarvestmanModel.LOW_F : HarvestmanModel.LOW_R], state.parts[2 + li * 2], pose, out, light, overlay, camo, pulse);
                }
            }
            for (int k = 0; k < state.flying.size(); k++) {
                boolean front = (state.flyingLeg.get(k) & 1) == 0;
                emit(level[front ? HarvestmanModel.LOW_F : HarvestmanModel.LOW_R], state.flying.get(k), pose, out, light,
                        OverlayTexture.NO_OVERLAY, 1f, 1f);
            }
            eyes(state.parts[0], pose, out, state.eyeShine);
        });
    }

    /** One part through its matrix: each triangle as a quad with its last corner repeated. */
    private static void emit(HarvestmanModel.Part part, Matrix4f m, PoseStack.Pose pose, VertexConsumer out, int light, int overlay,
                             float camo, float pulse) {
        Matrix4f w = new Matrix4f(pose.pose()).mul(m);
        Matrix3f nm = new Matrix3f(pose.normal()).mul(new Matrix3f(m));
        Vector3f p = new Vector3f(), n = new Vector3f();
        float[] pos = part.pos, normal = part.normal;
        int[] colour = part.colour;
        boolean[] red = part.red;
        // hidden, the red is just more black plate; showing, it glows (pulsing as it feeds)
        boolean lit = camo < 0.5f;
        float show = (1 - camo) * pulse;
        for (int t = 0, tris = part.triangles(); t < tris; t++) {
            int l = part.glow[t] && lit ? FULL_BRIGHT : light;
            for (int k = 0; k < 4; k++) {
                int c = t * 3 + Math.min(k, 2), i = c * 3;
                w.transformPosition(pos[i], pos[i + 1], pos[i + 2], p);
                nm.transform(normal[i], normal[i + 1], normal[i + 2], n);
                float len = n.length();
                if (len > 0f) {
                    n.div(len);
                }
                int col = colour[c];
                if (red[c] && show < 0.999f) {
                    int r = (col >> 16) & 255, g = (col >> 8) & 255, b = col & 255;
                    col = 0xFF000000 | (int) (DARK_R + (r - DARK_R) * show) << 16 | (int) (DARK_G + (g - DARK_G) * show) << 8
                            | (int) (DARK_B + (b - DARK_B) * show);
                }
                out.addVertex(p.x, p.y, p.z)
                        .setColor(col)
                        .setUv(0.5f, 0.5f)
                        .setOverlay(overlay)
                        .setLight(l)
                        .setNormal(n.x, n.y, n.z);
            }
        }
    }

    /**
     * Its six eyes: small glowing red beads, three down each side of its front. Caught in a light they flare: bigger,
     * and burning toward orange-white.
     */
    private static void eyes(Matrix4f body, PoseStack.Pose pose, VertexConsumer out, float shine) {
        float grow = 1 + 1.3f * shine;
        int r = 0xFF, g = (int) (0x24 + (0xC0 - 0x24) * shine * 0.75f), bl = (int) (0x14 + (0x80 - 0x14) * shine * 0.6f);
        int colour = 0xFF000000 | r << 16 | g << 8 | bl;
        Matrix4f w = new Matrix4f(pose.pose()).mul(body);
        Vector3f p = new Vector3f();
        float[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        // an octahedron: eight faces, each from three of the six points round the centre
        int[][] faces = {{0, 2, 4}, {2, 1, 4}, {1, 3, 4}, {3, 0, 4}, {2, 0, 5}, {1, 2, 5}, {3, 1, 5}, {0, 3, 5}};
        for (float[] e : HarvestmanRig.EYES) {
            for (int[] f : faces) {
                for (int k = 0; k < 4; k++) {
                    float[] d = dirs[f[Math.min(k, 2)]];
                    float s = e[3] * grow;
                    w.transformPosition(e[0] + d[0] * s, e[1] + d[1] * s, e[2] + d[2] * s, p);
                    out.addVertex(p.x, p.y, p.z)
                            .setColor(colour)
                            .setUv(0.5f, 0.5f)
                            .setOverlay(OverlayTexture.NO_OVERLAY)
                            .setLight(FULL_BRIGHT)
                            .setNormal(0f, 1f, 0f);
                }
            }
        }
    }
}
