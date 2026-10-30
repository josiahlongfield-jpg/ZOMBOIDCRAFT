package com.zomboidcraft.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZcConfig;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;

/**
 * Draws a skinned companion (NpcSkin) posed by NpcSkinAnimator: each mesh of the pieces she has on, at full detail up close
 * and the thinned copy further off, every vertex moved by up to four bones. Closed meshes (her body, eyes, teeth, heels) are
 * drawn front faces only, each triangle turned to face along its normals when loaded (Iris shades a quad by the way it's
 * wound, so a backwards one went dark); sheets (hair, lashes, underwear, bra, stockings) get a turned back copy as well, and
 * are cut out where their texture is clear. look.companionDraw picks other ways of drawing her, to find what lets a shader
 * pack show the world through her.
 * With her heels off she stands on the floor herself.
 */
final class NpcSkinDraw {
    /** Past this (blocks) she's drawn from the thinned copy. */
    private static final float NEAR = 10f;
    /** Triangles a submit holds when drawn in small batches (look.companionDraw 2). */
    private static final int BATCH = 2048;

    static void submit(String model, NpcSkin skin, Matrix4f[] bones, Set<String> hidden, Object[] buffers, double distSq,
                       int light, int overlay, PoseStack poseStack, SubmitNodeCollector collector) {
        int level = distSq > NEAR * NEAR ? 1 : 0;
        int mode = ZcConfig.get().look.companionDraw;
        float[] m = new float[bones.length * 12];
        for (int b = 0; b < bones.length; b++) {
            Matrix4f x = bones[b];
            int o = b * 12;
            m[o] = x.m00(); m[o + 1] = x.m10(); m[o + 2] = x.m20(); m[o + 3] = x.m30();
            m[o + 4] = x.m01(); m[o + 5] = x.m11(); m[o + 6] = x.m21(); m[o + 7] = x.m31();
            m[o + 8] = x.m02(); m[o + 9] = x.m12(); m[o + 10] = x.m22(); m[o + 11] = x.m32();
        }
        poseStack.pushPose();
        // she's modelled on tiptoe for heels: barefoot (no heels, no boots) she comes down to the ground
        if (hidden.contains("heels") && hidden.contains("boots")) {
            poseStack.translate(0, -skin.heel, 0);
        }
        String look = skin.lookKey(hidden);
        for (int g = 0; g < skin.groups.length; g++) {
            NpcSkin.Group gr = skin.groups[g];
            if (gr.level != level || !gr.piece.equals("body") && hidden.contains(gr.piece) || gr.looks != null && !gr.looks.contains(look)) {
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
            if (mode == 1) {
                collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(tex),
                        (pose, out) -> draw(gr, pos, normal, 0, gr.tris.length, false, pose, out, light, overlay));
                continue;
            }
            RenderType type = mode == 3 && !gr.cutout ? RenderTypes.entitySolid(tex) : RenderTypes.entityCutoutCull(tex);
            int step = mode == 2 ? BATCH * 3 : gr.tris.length;
            for (int from = 0; from < gr.tris.length; from += step) {
                int start = from, end = Math.min(gr.tris.length, from + step);
                collector.submitCustomGeometry(poseStack, type,
                        (pose, out) -> draw(gr, pos, normal, start, end, gr.bothSides, pose, out, light, overlay));
            }
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

    /** Every triangle as a quad with its last corner repeated; a sheet's again wound the other way, facing back. */
    private static void draw(NpcSkin.Group g, float[] p, float[] n, int from, int to, boolean back, PoseStack.Pose pose,
                             VertexConsumer out, int light, int overlay) {
        float[] uv = g.uv;
        int[] tris = g.tris;
        for (int s = 0; s < (back ? 2 : 1); s++) {
            float f = s == 0 ? 1 : -1;
            for (int t = from; t < to; t += 3) {
                for (int k = 0; k < 4; k++) {
                    int c = Math.min(k, 2), v = tris[t + (s == 0 ? c : 2 - c)], i = v * 3;
                    out.addVertex(pose, p[i], p[i + 1], p[i + 2])
                            .setColor(0xFFFFFFFF)
                            .setUv(uv[v * 2], uv[v * 2 + 1])
                            .setOverlay(overlay)
                            .setLight(light)
                            .setNormal(pose, f * n[i], f * n[i + 1], f * n[i + 2]);
                }
            }
        }
    }

    private NpcSkinDraw() {
    }
}
