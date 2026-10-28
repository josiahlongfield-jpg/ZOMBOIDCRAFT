package com.zomboidcraft.client.creature;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.creature.HarvestmanEntity;
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
 * Draws a Harvestman: its body as the model has it, and each leg's thigh and lower leg where its rig (HarvestmanRig)
 * puts them this frame. The model's colours are baked into its corners (on a plain white texture); the red seams glow.
 * Past about twenty blocks it's drawn from the lighter model.
 */
public class HarvestmanRenderer extends EntityRenderer<HarvestmanEntity, HarvestmanRenderer.State> {
    private static final Identifier WHITE = ZomboidCraft.id("textures/entity/harvestman/white.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final double FAR = 20;

    public static class State extends EntityRenderState {
        boolean ready, far;
        /** Body, then thigh and lower leg for each leg, relative to where the entity is drawn. */
        final Matrix4f[] parts = new Matrix4f[9];

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
        rig.update(entity, entity.level(), model, drawnAt.add(0, HarvestmanEntity.MID, 0), drawnAt, q);
        state.parts[0].set(rig.body);
        for (int li = 0; li < 4; li++) {
            state.parts[1 + li * 2].set(rig.thigh[li]);
            state.parts[2 + li * 2].set(rig.shin[li]);
        }
        state.far = state.distanceToCameraSq > FAR * FAR;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (!state.ready) {
            return;
        }
        HarvestmanModel.Part[] level = HarvestmanModel.get().levels[state.far ? 1 : 0];
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(WHITE), (pose, out) -> {
            emit(level[HarvestmanModel.BODY], state.parts[0], pose, out, light);
            for (int li = 0; li < 4; li++) {
                boolean front = (li & 1) == 0;
                emit(level[front ? HarvestmanModel.THIGH_F : HarvestmanModel.THIGH_R], state.parts[1 + li * 2], pose, out, light);
                emit(level[front ? HarvestmanModel.LOW_F : HarvestmanModel.LOW_R], state.parts[2 + li * 2], pose, out, light);
            }
        });
    }

    /** One part through its matrix: each triangle as a quad with its last corner repeated. */
    private static void emit(HarvestmanModel.Part part, Matrix4f m, PoseStack.Pose pose, VertexConsumer out, int light) {
        Matrix4f w = new Matrix4f(pose.pose()).mul(m);
        Matrix3f nm = new Matrix3f(pose.normal()).mul(new Matrix3f(m));
        Vector3f p = new Vector3f(), n = new Vector3f();
        float[] pos = part.pos, normal = part.normal;
        int[] colour = part.colour;
        for (int t = 0, tris = part.triangles(); t < tris; t++) {
            int l = part.glow[t] ? FULL_BRIGHT : light;
            for (int k = 0; k < 4; k++) {
                int c = t * 3 + Math.min(k, 2), i = c * 3;
                w.transformPosition(pos[i], pos[i + 1], pos[i + 2], p);
                nm.transform(normal[i], normal[i + 1], normal[i + 2], n);
                float len = n.length();
                if (len > 0f) {
                    n.div(len);
                }
                out.addVertex(p.x, p.y, p.z)
                        .setColor(colour[c])
                        .setUv(0.5f, 0.5f)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(l)
                        .setNormal(n.x, n.y, n.z);
            }
        }
    }
}
