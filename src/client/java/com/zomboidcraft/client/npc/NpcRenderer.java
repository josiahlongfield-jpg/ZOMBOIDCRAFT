package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.zomboidcraft.npc.NpcEntity;
import com.zomboidcraft.npc.Npcs;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Draws an NPC's mesh (NpcAssets) turned to its body's facing, breathing a little. Both sides of every triangle are drawn,
 * since bought clothes are often single sheets. The "raw" look is lit like any mob. The "toon" look, on its painted
 * texture, is lit in three flat bands from one fixed light (as the gun renders are) and gets an ink outline: the mesh
 * pushed out a few millimetres along its normals, then back away from the eye further than that, drawn in near-black.
 * Where the body faces you the shell ends up behind it; only round the edges, where nothing covers it, does it show.
 * (It doesn't lean on back-face culling, which shader packs may turn off: drawn inside out, it hid the whole body.)
 */
public class NpcRenderer extends EntityRenderer<NpcEntity, NpcRenderer.State> {
    /** How far the outline shell stands off the body, in blocks. */
    private static final float INK = 0.0065f;
    /** How far the shell is pushed back from the eye, so its front sits behind the body. */
    private static final float BACK = INK * 2.5f;
    private static final int INK_COLOUR = 0xFF14110F;
    /** The toon light (world space, normalised below) and its three bands. */
    private static final float LX, LY, LZ;
    private static final float LIT = 1.06f, MID = 0.86f, SHADE = 0.68f;

    static {
        float x = -0.4f, y = 0.8f, z = 0.45f, len = (float) Math.sqrt(x * x + y * y + z * z);
        LX = x / len;
        LY = y / len;
        LZ = z / len;
    }

    public static void register() {
        EntityRendererRegistry.register(Npcs.NPC, NpcRenderer::new);
        try {
            // made up front so it's there to drop local model files into
            java.nio.file.Files.createDirectories(Npcs.localFolder());
        } catch (java.io.IOException e) {
            // the folder is only a convenience
        }
    }

    public static class State extends EntityRenderState {
        String model = "", look = "raw";
        float bodyYaw;
        boolean hurt;
    }

    public NpcRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.35f;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(NpcEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.model = entity.model();
        state.look = entity.look();
        state.bodyYaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot);
        state.hurt = entity.hurtTime > 0 && entity.isAlive();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        NpcAssets.Mesh mesh = NpcAssets.mesh(state.model);
        Identifier texture = NpcAssets.texture(state.model, state.look);
        if (mesh == null || texture == null) {
            return;
        }
        boolean toon = state.look.equals("toon");
        int light = state.lightCoords, overlay = OverlayTexture.pack(0, state.hurt ? 3 : 10);
        float yaw = state.bodyYaw;
        // the eye in the model's own frame (undoing the turn below), for the outline
        double dx = camera.pos.x - state.x, dy = camera.pos.y - state.y, dz = camera.pos.z - state.z, r = Math.toRadians(yaw);
        float ex = (float) (dx * Math.cos(r) + dz * Math.sin(r)), ey = (float) dy, ez = (float) (-dx * Math.sin(r) + dz * Math.cos(r));
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, -yaw);
        // a slow breath: the chest rises a few millimetres and the body sways a touch
        float breath = Mth.sin(state.ageInTicks * 0.09f);
        poseStack.scale(1f, 1f + breath * 0.004f, 1f);
        poseStack.rotateDegrees(Axis.ZP, Mth.sin(state.ageInTicks * 0.031f) * 0.6f);
        int[] shade = toon ? bands(mesh, yaw) : null;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, out) -> body(mesh, shade, pose, out, light, overlay));
        if (toon) {
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, out) -> ink(mesh, ex, ey, ez, pose, out, light));
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** Each vertex's toon colour: which band its normal falls in, turned to the body's facing, under the fixed light. */
    private static int[] bands(NpcAssets.Mesh mesh, float yawDegrees) {
        float a = (float) Math.toRadians(-yawDegrees), c = Mth.cos(a), s = Mth.sin(a);
        float[] n = mesh.normal();
        int[] out = new int[mesh.vertices()];
        for (int v = 0; v < out.length; v++) {
            float nx = n[v * 3], ny = n[v * 3 + 1], nz = n[v * 3 + 2];
            float wx = nx * c + nz * s, wz = -nx * s + nz * c;
            float d = wx * LX + ny * LY + wz * LZ;
            float k = d > 0.45f ? LIT : d > 0f ? MID : SHADE;
            k *= ny > 0.6f ? 1.06f : ny < -0.5f ? 0.85f : 1f;
            int g = Math.min(255, (int) (k * 235));
            out[v] = 0xFF000000 | g << 16 | g << 8 | g;
        }
        return out;
    }

    /** Every triangle twice, front and back (each as a quad with its last corner repeated). */
    private static void body(NpcAssets.Mesh mesh, int[] shade, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        int[] tris = mesh.tris();
        for (int t = 0; t < tris.length; t += 3) {
            for (int side = 0; side < 2; side++) {
                float flip = side == 0 ? 1f : -1f;
                for (int k = 0; k < 4; k++) {
                    int corner = Math.min(k, 2);
                    int v = tris[t + (side == 0 ? corner : 2 - corner)];
                    int i = v * 3;
                    out.addVertex(pose, p[i], p[i + 1], p[i + 2])
                            .setColor(shade == null ? 0xFFFFFFFF : shade[v])
                            .setUv(uv[v * 2], uv[v * 2 + 1])
                            .setOverlay(overlay)
                            .setLight(light);
                    if (shade == null) {
                        out.setNormal(pose, n[i] * flip, n[i + 1] * flip, n[i + 2] * flip);
                    } else {
                        // toon shading is in the colour; an upward normal keeps the game's own lighting even
                        out.setNormal(pose, 0f, 1f, 0f);
                    }
                }
            }
        }
    }

    /** The outline: the mesh pushed out along its normals and back from the eye (ex, ey, ez, model space), so only its rim shows. */
    private static void ink(NpcAssets.Mesh mesh, float ex, float ey, float ez, PoseStack.Pose pose, VertexConsumer out, int light) {
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        int[] tris = mesh.tris();
        float[] moved = new float[p.length];
        for (int v = 0, i = 0; v < mesh.vertices(); v++, i += 3) {
            float vx = ex - p[i], vy = ey - p[i + 1], vz = ez - p[i + 2];
            float len = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float back = len > 1e-4f ? BACK / len : 0f;
            moved[i] = p[i] + n[i] * INK - vx * back;
            moved[i + 1] = p[i + 1] + n[i + 1] * INK - vy * back;
            moved[i + 2] = p[i + 2] + n[i + 2] * INK - vz * back;
        }
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)];
                int i = v * 3;
                out.addVertex(pose, moved[i], moved[i + 1], moved[i + 2])
                        .setColor(INK_COLOUR)
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(light)
                        .setNormal(pose, 0f, 1f, 0f);
            }
        }
    }
}
