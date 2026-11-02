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
public final class NpcSkinDraw {
    /** Past this (blocks) she's drawn from the thinned copy. */
    private static final float NEAR = 10f;
    /** Triangles a submit holds when drawn in small batches (look.companionDraw 2). */
    private static final int BATCH = 2048;

    static void submit(String model, NpcSkin skin, Matrix4f[] bones, Set<String> hidden, Object[] buffers, double distSq,
                       int light, int overlay, float[] press, PoseStack poseStack, SubmitNodeCollector collector) {
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
            if (press != null) {
                press(gr.vertices, pos, normal, press);
            }
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

    /**
     * Draws the chosen meshes of any skinned model with the textures given (the survivor bodies: their skin is a look
     * composed on the fly, their hair tinted), every one at the detail it has. Closed meshes front faces only, sheets
     * (flag both sides) from behind as well, cut out where their texture is clear.
     */
    public static void submitWith(NpcSkin skin, Matrix4f[] bones, java.util.function.Predicate<NpcSkin.Group> show,
                                  java.util.function.Function<NpcSkin.Group, Identifier> textures, Object[] buffers,
                                  int light, int overlay, PoseStack poseStack, SubmitNodeCollector collector) {
        submitWith(skin, bones, 0, show, textures, buffers, light, overlay, poseStack, collector);
    }

    /** As above, from the meshes of one level of detail (0 full, 1 the thinned copies a pack may carry). */
    public static void submitWith(NpcSkin skin, Matrix4f[] bones, int level, java.util.function.Predicate<NpcSkin.Group> show,
                                  java.util.function.Function<NpcSkin.Group, Identifier> textures, Object[] buffers,
                                  int light, int overlay, PoseStack poseStack, SubmitNodeCollector collector) {
        // no bones: the buffers were skinned already (skinAll), shared by everyone in that pose
        float[] m = bones == null ? null : matrices(bones);
        for (int g = 0; g < skin.groups.length; g++) {
            NpcSkin.Group gr = skin.groups[g];
            if (gr.level != level || !show.test(gr)) {
                continue;
            }
            Identifier tex = textures.apply(gr);
            if (tex == null) {
                continue;
            }
            if (m == null) {
                if (buffers[g * 2] == null) continue;
                float[] pos = (float[]) buffers[g * 2], normal = (float[]) buffers[g * 2 + 1];
                collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(tex),
                        (pose, out) -> draw(gr, pos, normal, 0, gr.tris.length, gr.bothSides, pose, out, light, overlay));
                continue;
            }
            if (buffers[g * 2] == null) {
                buffers[g * 2] = new float[gr.vertices * 3];
                buffers[g * 2 + 1] = new float[gr.vertices * 3];
            }
            float[] pos = (float[]) buffers[g * 2], normal = (float[]) buffers[g * 2 + 1];
            skin(gr, m, pos, normal);
            collector.submitCustomGeometry(poseStack, RenderTypes.entityCutoutCull(tex),
                    (pose, out) -> draw(gr, pos, normal, 0, gr.tris.length, gr.bothSides, pose, out, light, overlay));
        }
    }

    private static float[] matrices(Matrix4f[] bones) {
        float[] m = new float[bones.length * 12];
        for (int b = 0; b < bones.length; b++) {
            Matrix4f x = bones[b];
            int o = b * 12;
            m[o] = x.m00(); m[o + 1] = x.m10(); m[o + 2] = x.m20(); m[o + 3] = x.m30();
            m[o + 4] = x.m01(); m[o + 5] = x.m11(); m[o + 6] = x.m21(); m[o + 7] = x.m31();
            m[o + 8] = x.m02(); m[o + 9] = x.m12(); m[o + 10] = x.m22(); m[o + 11] = x.m32();
        }
        return m;
    }

    /**
     * Skins every mesh of one level into the buffers (two per group, as submitWith keeps them), for many bodies in the
     * same pose to be drawn from with submitWith and no bones: a far crowd skinned once, not once a figure.
     */
    public static void skinAll(NpcSkin skin, Matrix4f[] bones, int level, Object[] buffers) {
        float[] m = matrices(bones);
        for (int g = 0; g < skin.groups.length; g++) {
            NpcSkin.Group gr = skin.groups[g];
            if (gr.level != level) continue;
            if (buffers[g * 2] == null) {
                buffers[g * 2] = new float[gr.vertices * 3];
                buffers[g * 2 + 1] = new float[gr.vertices * 3];
            }
            skin(gr, m, (float[]) buffers[g * 2], (float[]) buffers[g * 2 + 1]);
        }
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

    /** How far around a held gun her skin and clothes give (blocks past its surface), and the deepest they press in. */
    private static final float PRESS_REACH = 0.035f, PRESS_DEPTH = 0.008f;

    /**
     * Where her held gun touches her, the skin and clothes under it press in a few millimetres, deepest where it rests
     * and easing out around it, and come back when it moves off. The gun is a line (stock to muzzle) with a thickness.
     */
    private static void press(int vertices, float[] pos, float[] normal, float[] line) {
        float ax = line[0], ay = line[1], az = line[2], bx = line[3], by = line[4], bz = line[5], reach = line[6] + PRESS_REACH;
        float minX = Math.min(ax, bx) - reach, maxX = Math.max(ax, bx) + reach, minY = Math.min(ay, by) - reach;
        float maxY = Math.max(ay, by) + reach, minZ = Math.min(az, bz) - reach, maxZ = Math.max(az, bz) + reach;
        float abx = bx - ax, aby = by - ay, abz = bz - az, len = abx * abx + aby * aby + abz * abz;
        if (len < 1e-8f) {
            return;
        }
        for (int v = 0, i = 0; v < vertices; v++, i += 3) {
            float px = pos[i], py = pos[i + 1], pz = pos[i + 2];
            if (px < minX || px > maxX || py < minY || py > maxY || pz < minZ || pz > maxZ) {
                continue;
            }
            float t = Math.max(0, Math.min(1, ((px - ax) * abx + (py - ay) * aby + (pz - az) * abz) / len));
            float dx = px - (ax + abx * t), dy = py - (ay + aby * t), dz = pz - (az + abz * t);
            float d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d >= reach) {
                continue;
            }
            float k = 1 - d / reach, dent = k * k * PRESS_DEPTH;
            pos[i] -= normal[i] * dent;
            pos[i + 1] -= normal[i + 1] * dent;
            pos[i + 2] -= normal[i + 2] * dent;
        }
    }

    /**
     * Every triangle paired with a neighbour across a shared edge into one quad (a, b, c, d: its two triangles are just
     * the pair again, as the quad is drawn), a lone one with its last corner repeated; a sheet's again wound the other
     * way, facing back. Half the vertices of a triangle each, which is most of a skinned body's drawing cost.
     */
    private static void draw(NpcSkin.Group g, float[] p, float[] n, int from, int to, boolean back, PoseStack.Pose pose,
                             VertexConsumer out, int light, int overlay) {
        float[] uv = g.uv;
        int[] quads = quads(g);
        // a batch of the triangles (from, to) is the same share of the quads
        int nq = quads.length / 4, nt = g.tris.length / 3;
        int q0 = (int) ((long) (from / 3) * nq / Math.max(1, nt)) * 4, q1 = (int) ((long) (to / 3) * nq / Math.max(1, nt)) * 4;
        for (int s = 0; s < (back ? 2 : 1); s++) {
            float f = s == 0 ? 1 : -1;
            for (int q = q0; q < q1; q += 4) {
                for (int k = 0; k < 4; k++) {
                    int v = quads[q + (s == 0 ? k : 3 - k)], i = v * 3;
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

    private static final java.util.Map<NpcSkin.Group, int[]> QUADS = new java.util.WeakHashMap<>();

    /** The group's triangles paired into quads, worked out once. */
    static int[] quads(NpcSkin.Group g) {
        int[] have = QUADS.get(g);
        if (have != null) return have;
        int[] t = g.tris;
        int tris = t.length / 3;
        // each directed edge (from, to) -> the triangle it's in
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap edges = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(tris * 3);
        edges.defaultReturnValue(-1);
        for (int i = 0; i < tris; i++) {
            for (int e = 0; e < 3; e++) edges.putIfAbsent((long) t[i * 3 + e] << 32 | t[i * 3 + (e + 1) % 3] & 0xFFFFFFFFL, i);
        }
        boolean[] used = new boolean[tris];
        int[] out = new int[tris * 4];
        int n = 0;
        for (int i = 0; i < tris; i++) {
            if (used[i]) continue;
            used[i] = true;
            int qa = t[i * 3], qb = t[i * 3 + 1], qc = t[i * 3 + 2], qd = qc;
            for (int e = 0; e < 3; e++) {
                int a = t[i * 3 + e], b = t[i * 3 + (e + 1) % 3], c = t[i * 3 + (e + 2) % 3];
                // across the edge c -> a lies a triangle running a -> c: a, b, c then c, d, a make the quad
                int u = edges.get((long) a << 32 | c & 0xFFFFFFFFL);
                if (u < 0 || used[u]) continue;
                int d = -1;
                for (int k = 0; k < 3; k++) {
                    int x = t[u * 3 + k];
                    if (x != a && x != c) d = x;
                }
                if (d < 0) continue;
                used[u] = true;
                qa = a; qb = b; qc = c; qd = d;
                break;
            }
            out[n++] = qa;
            out[n++] = qb;
            out[n++] = qc;
            out[n++] = qd;
        }
        int[] q = java.util.Arrays.copyOf(out, n);
        QUADS.put(g, q);
        return q;
    }

    private NpcSkinDraw() {
    }
}
