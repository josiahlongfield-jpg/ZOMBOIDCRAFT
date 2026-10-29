package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Draws a skinned companion (NpcSkin) posed by NpcSkinAnimator: each mesh of the pieces she has on, at full detail up close
 * and the thinned copy further off, every vertex moved by up to four bones. Every mesh is drawn with both sides kept (so
 * culling the wrong side can never show her through herself); sheets (hair, lashes, underwear, bra, stockings) are cut out
 * where their texture is clear.
 * With her heels off she stands on the floor herself.
 */
final class NpcSkinDraw {
    /** Past this (blocks) she's drawn from the thinned copy. */
    private static final float NEAR = 10f;

    static void submit(String model, NpcSkin skin, Matrix4f[] bones, Set<String> hidden, Object[] buffers, double distSq,
                       int light, int overlay, PoseStack poseStack, SubmitNodeCollector collector) {
        int level = distSq > NEAR * NEAR ? 1 : 0;
        float[] m = new float[bones.length * 12];
        for (int b = 0; b < bones.length; b++) {
            Matrix4f x = bones[b];
            int o = b * 12;
            m[o] = x.m00(); m[o + 1] = x.m10(); m[o + 2] = x.m20(); m[o + 3] = x.m30();
            m[o + 4] = x.m01(); m[o + 5] = x.m11(); m[o + 6] = x.m21(); m[o + 7] = x.m31();
            m[o + 8] = x.m02(); m[o + 9] = x.m12(); m[o + 10] = x.m22(); m[o + 11] = x.m32();
        }
        poseStack.pushPose();
        if (hidden.contains("heels")) {
            poseStack.translate(0, -skin.heel, 0);
        }
        for (int g = 0; g < skin.groups.length; g++) {
            NpcSkin.Group gr = skin.groups[g];
            if (gr.level != level || !gr.piece.equals("body") && hidden.contains(gr.piece)) {
                continue;
            }
            Identifier tex = skin.texture(gr.texture, model);
            if (tex == null) {
                continue;
            }
            if (buffers[g * 2] == null) {
                buffers[g * 2] = new float[gr.vertices * 3];
                buffers[g * 2 + 1] = new float[gr.vertices * 3];
            }
            float[] pos = (float[]) buffers[g * 2], normal = (float[]) buffers[g * 2 + 1];
            skin(gr, m, pos, normal);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tex),
                    (pose, out) -> draw(gr, pos, normal, pose, out, light, overlay));
        }
        poseStack.popPose();
    }

    /** Each vertex by its bones (each a 3x4 in m, row by row: x', y', z'), weighted. */
    private static void skin(NpcSkin.Group g, float[] m, float[] pos, float[] normal) {
        float[] p = g.pos, n = g.normal, w = g.weight;
        byte[] j = g.joint;
        for (int v = 0; v < g.vertices; v++) {
            float px = p[v * 3], py = p[v * 3 + 1], pz = p[v * 3 + 2];
            float nx = n[v * 3], ny = n[v * 3 + 1], nz = n[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, qx = 0, qy = 0, qz = 0;
            for (int k = 0; k < 4; k++) {
                float k4 = w[v * 4 + k];
                if (k4 <= 0) {
                    continue;
                }
                int o = (j[v * 4 + k] & 255) * 12;
                ox += k4 * (m[o] * px + m[o + 1] * py + m[o + 2] * pz + m[o + 3]);
                oy += k4 * (m[o + 4] * px + m[o + 5] * py + m[o + 6] * pz + m[o + 7]);
                oz += k4 * (m[o + 8] * px + m[o + 9] * py + m[o + 10] * pz + m[o + 11]);
                qx += k4 * (m[o] * nx + m[o + 1] * ny + m[o + 2] * nz);
                qy += k4 * (m[o + 4] * nx + m[o + 5] * ny + m[o + 6] * nz);
                qz += k4 * (m[o + 8] * nx + m[o + 9] * ny + m[o + 10] * nz);
            }
            float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (len > 1e-6f) {
                qx /= len;
                qy /= len;
                qz /= len;
            }
            pos[v * 3] = ox;
            pos[v * 3 + 1] = oy;
            pos[v * 3 + 2] = oz;
            normal[v * 3] = qx;
            normal[v * 3 + 1] = qy;
            normal[v * 3 + 2] = qz;
        }
    }

    /** Every triangle as a quad with its last corner repeated. */
    private static void draw(NpcSkin.Group g, float[] p, float[] n, PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        float[] uv = g.uv;
        int[] tris = g.tris;
        for (int t = 0; t < tris.length; t += 3) {
            for (int k = 0; k < 4; k++) {
                int v = tris[t + Math.min(k, 2)], i = v * 3;
                out.addVertex(pose, p[i], p[i + 1], p[i + 2])
                        .setColor(0xFFFFFFFF)
                        .setUv(uv[v * 2], uv[v * 2 + 1])
                        .setOverlay(overlay)
                        .setLight(light)
                        .setNormal(pose, n[i], n[i + 1], n[i + 2]);
            }
        }
    }

    private NpcSkinDraw() {
    }
}
