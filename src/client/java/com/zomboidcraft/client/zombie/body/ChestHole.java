package com.zomboidcraft.client.zombie.body;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A chest blown open by buckshot from point-blank range (Dismember.CHEST_HOLE): a hole right through the body and
 * its clothes, lined with wet flesh, broken ribs sticking into it, the snapped ends of the spine at the back, torn
 * lips of flesh round both rims and shreds hanging into it. The holed body is cut once an outfit: every chest quad
 * round the hole is cut into the rectangles left over, its texture cut to match, so it draws as cheaply as a whole
 * one. Render thread only.
 */
public final class ChestHole {
    /** The hole on the chest in the spine bone's space (x across, y up), as rectangles laid over each other. */
    private static final float[][] RECTS = {{-1.4f, 1.2f, 4.55f, 8.35f}, {-2.05f, 1.85f, 5.2f, 7.7f},
            {-1.8f, 1.6f, 4.85f, 8.05f}};
    /** How deep it cuts: through the thickest coat in front, and out of the back short of a backpack or air tank. */
    private static final float FRONT = 4.0f, BACK = -2.62f;
    /** The body's own front and back there, under the clothes. */
    private static final float BODY_FRONT = 2.1f, BODY_BACK = -2.1f;
    /** The middle of the hole: the clothes over it decide how deep the lining goes. */
    private static final float[] CORE = {-1.4f, 1.2f, 5.2f, 7.7f};
    /** Points round the rim (x, y) and the way out from the hole there: where the torn lips go. */
    private static final float[][] RIM = {{-0.1f, 4.55f, 0f, -1f}, {0.85f, 4.6f, 0.3f, -1f}, {1.6f, 5.0f, 0.7f, -0.7f},
            {1.85f, 5.9f, 1f, 0f}, {1.85f, 7.0f, 1f, 0.1f}, {1.6f, 7.9f, 0.7f, 0.7f}, {0.55f, 8.35f, 0f, 1f},
            {-0.7f, 8.35f, -0.1f, 1f}, {-1.7f, 7.95f, -0.7f, 0.7f}, {-2.05f, 6.9f, -1f, 0f}, {-2.05f, 5.8f, -1f, -0.1f},
            {-1.55f, 4.8f, -0.7f, -0.7f}};
    /** Broken ribs: the side of the hole they stick out of (x, y), which way (1 to the right), and front or back. */
    private static final float[][] RIBS = {{-2.05f, 5.75f, 1f, 1f}, {-2.05f, 6.5f, 1f, 1f}, {-2.05f, 7.25f, 1f, 1f},
            {1.85f, 6.0f, -1f, 1f}, {1.85f, 6.85f, -1f, 1f}, {-2.05f, 6.15f, 1f, -1f}, {1.85f, 6.55f, -1f, -1f}};
    private static final float EPS = 1e-4f;
    /** The lining round the hole's outline: each edge's ends (x0, y0, x1, y1) and the way it faces into the hole. */
    private static final float[][] WALLS = walls();

    private static final Matrix4f BOX = new Matrix4f();
    private static final Vector3f P = new Vector3f(), N = new Vector3f();
    private static final Vector3f[] C = {new Vector3f(), new Vector3f(), new Vector3f(), new Vector3f()};

    /** The body with its chest blown open, cut the first time it's wanted. */
    static ZBodyMesh of(ZBodyMesh mesh) {
        if (mesh.holed == null) {
            mesh.holed = cut(mesh);
        }
        return mesh.holed;
    }

    /**
     * Draws the inside of the hole on a holed body: the lining, and close up the ribs, spine, lips and shreds.
     *
     * @param at a spare matrix
     * @param seed picks how the bits sit, so no two holes look alike
     */
    static void draw(Gore gore, Matrix4f at, float[] mats, ZBodyMesh holed, boolean detail, int seed,
                     PoseStack.Pose pose, VertexConsumer out, int light, int overlay) {
        at.set(mats, ZMove.SPINE * 16);
        float front = holed.holeFront, back = holed.holeBack;
        float[] flesh = Gore.region(Gore.FLESH);
        for (float[] w : WALLS) {
            C[0].set(w[0], w[1], back);
            C[1].set(w[2], w[3], back);
            C[2].set(w[2], w[3], front);
            C[3].set(w[0], w[1], front);
            quad(out, pose, at, w[4], w[5], 0f, flesh, light, overlay);
        }
        if (!detail) {
            return;
        }
        int r = seed * 0x9E3779B9 + 0x51ED;
        // broken ribs poking in from the sides, under the skin in front and at the back
        for (float[] rib : RIBS) {
            r = r * 1103515245 + 12345;
            float len = 0.4f + (r >>> 8 & 0xFF) / 255f * 0.45f, turn = ((r >>> 16 & 0xFF) / 255f - 0.5f) * 0.5f;
            float z = rib[3] > 0 ? BODY_FRONT - 0.55f : BODY_BACK + 0.6f;
            gore.box(out, pose, at, rib[0] + rib[2] * (len / 2 - 0.15f), rib[1], z, turn, len, 0.34f, 0.4f,
                    Gore.BONE, light, overlay);
        }
        // the spine snapped off above and below, at the back of the hole
        gore.box(out, pose, at, -0.1f, RECTS[0][2] + 0.4f, BODY_BACK + 0.45f, 0f, 0.8f, 0.85f, 0.75f, Gore.BONE, light, overlay);
        gore.box(out, pose, at, -0.1f, RECTS[0][3] - 0.36f, BODY_BACK + 0.45f, 0.2f, 0.8f, 0.75f, 0.75f, Gore.BONE, light, overlay);
        // torn lips of flesh round the rims, lying over the edges of the clothes
        for (int i = 0; i < RIM.length; i++) {
            float[] p = RIM[i];
            float angle = (float) Math.atan2(p[3], p[2]);
            for (int side = 0; side < 2; side++) {
                if (side == 1 && i % 3 == 1) {
                    continue;   // the exit's a little tidier
                }
                r = r * 1103515245 + 12345;
                float radial = 0.3f + (r >>> 8 & 0xFF) / 255f * 0.3f, along = 0.45f + (r >>> 16 & 0xFF) / 255f * 0.4f;
                float z = side == 0 ? holed.rimFront[i] + 0.06f : holed.rimBack[i] - 0.06f;
                BOX.set(at).translate(p[0], p[1], z).rotateZ(angle + ((r >>> 24) / 255f - 0.5f) * 0.4f);
                gore.box(out, pose, BOX, radial * 0.3f, 0f, 0f, 0f, radial, along, 0.14f,
                        (r >>> 4 & 3) == 0 ? Gore.SHRED : Gore.FLESH, light, overlay);
            }
        }
        // shreds hanging into the hole from its top, near the front
        for (int i = 0; i < 2; i++) {
            r = r * 1103515245 + 12345;
            float x = -0.75f + i * 1.1f + ((r >>> 8 & 0xFF) / 255f - 0.5f) * 0.4f, h = 0.5f + (r >>> 16 & 0xFF) / 255f * 0.5f;
            float top = inside(x, RECTS[0][3] - 0.05f) ? RECTS[0][3] : RECTS[2][3];
            gore.box(out, pose, at, x, top - h / 2 + 0.1f, front - 0.2f - (r >>> 24) / 255f * 0.5f, 0f,
                    0.25f + (r >>> 4 & 7) * 0.03f, h, 0.14f, Gore.SHRED, light, overlay);
        }
    }

    /** A quad of the lining from the corners in C, facing (nx, ny, nz) in the spine's space. */
    private static void quad(VertexConsumer out, PoseStack.Pose pose, Matrix4f at, float nx, float ny, float nz,
                             float[] uv, int light, int overlay) {
        // wind it so it faces the right way (culled from behind)
        float ax = C[1].x - C[0].x, ay = C[1].y - C[0].y, az = C[1].z - C[0].z;
        float bx = C[2].x - C[0].x, by = C[2].y - C[0].y, bz = C[2].z - C[0].z;
        boolean flip = (ay * bz - az * by) * nx + (az * bx - ax * bz) * ny + (ax * by - ay * bx) * nz < 0f;
        at.transformDirection(N.set(nx, ny, nz)).normalize();
        for (int k = 0; k < 4; k++) {
            int c = flip ? (4 - k) % 4 : k;
            at.transformPosition(P.set(C[c]));
            out.addVertex(pose, P.x, P.y, P.z)
                    .setColor(-1)
                    .setUv(c == 1 || c == 2 ? uv[2] : uv[0], c >= 2 ? uv[3] : uv[1])
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, N.x, N.y, N.z);
        }
    }

    /** Cuts the hole out of a body: every chest quad, through the clothes in front and behind. */
    private static ZBodyMesh cut(ZBodyMesh m) {
        List<float[]> kept = new ArrayList<>();
        List<float[]> pieces = new ArrayList<>();
        float front = BODY_FRONT, back = BODY_BACK;
        for (int q = 0; q < m.count; q++) {
            pieces.clear();
            pieces.add(new float[] {0f, 1f, 0f, 1f});
            if (m.bone[q] == ZMove.SPINE) {
                carve(m, q, pieces);
                // what's worn over the middle of the hole: the lining goes as deep
                float nz = m.normal[q * 3 + 2], z = m.pos[q * 12 + 2];
                if (Math.abs(nz) > 0.9f && overlaps(m, q, CORE)) {
                    if (nz > 0f && z <= FRONT) {
                        front = Math.max(front, z);
                    } else if (nz < 0f && z >= BACK) {
                        back = Math.min(back, z);
                    }
                }
            }
            for (float[] p : pieces) {
                kept.add(new float[] {q, p[0], p[1], p[2], p[3]});
            }
        }
        int n = kept.size();
        byte[] bone = new byte[n], flags = new byte[n];
        float[] pos = new float[n * 12], normal = new float[n * 3], uv = new float[n * 4], minEdge = new float[n];
        for (int i = 0; i < n; i++) {
            float[] k = kept.get(i);
            int q = (int) k[0];
            bone[i] = m.bone[q];
            flags[i] = m.flags[q];
            System.arraycopy(m.normal, q * 3, normal, i * 3, 3);
            minEdge[i] = m.minEdge[q];  // a strip left beside the hole goes when the whole piece would
            float s0 = k[1], s1 = k[2], t0 = k[3], t1 = k[4];
            corner(m, q, s0, t0, pos, i * 12);
            corner(m, q, s1, t0, pos, i * 12 + 3);
            corner(m, q, s1, t1, pos, i * 12 + 6);
            corner(m, q, s0, t1, pos, i * 12 + 9);
            float u0 = m.uv[q * 4], v0 = m.uv[q * 4 + 1], u1 = m.uv[q * 4 + 2], v1 = m.uv[q * 4 + 3];
            uv[i * 4] = u0 + (u1 - u0) * s0;
            uv[i * 4 + 1] = v0 + (v1 - v0) * t0;
            uv[i * 4 + 2] = u0 + (u1 - u0) * s1;
            uv[i * 4 + 3] = v0 + (v1 - v0) * t1;
        }
        ZBodyMesh holed = new ZBodyMesh(m, n, bone, flags, pos, normal, uv, minEdge);
        holed.holeFront = front;
        holed.holeBack = back;
        holed.rimFront = new float[RIM.length];
        holed.rimBack = new float[RIM.length];
        for (int i = 0; i < RIM.length; i++) {
            // just outside the rim, on whatever's worn there
            float[] p = RIM[i];
            float len = (float) Math.sqrt(p[2] * p[2] + p[3] * p[3]);
            float x = p[0] + p[2] / len * 0.3f, y = p[1] + p[3] / len * 0.3f;
            holed.rimFront[i] = surface(m, x, y, true);
            holed.rimBack[i] = surface(m, x, y, false);
        }
        return holed;
    }

    /** Cuts the hole out of one quad: what's left of it, as rectangles of its texture (s0, s1, t0, t1, 0 to 1). */
    private static void carve(ZBodyMesh m, int q, List<float[]> pieces) {
        int o = q * 12;
        float[] tl = {m.pos[o], m.pos[o + 1], m.pos[o + 2]};
        float[] e1 = {m.pos[o + 3] - tl[0], m.pos[o + 4] - tl[1], m.pos[o + 5] - tl[2]};
        float[] e2 = {m.pos[o + 9] - tl[0], m.pos[o + 10] - tl[1], m.pos[o + 11] - tl[2]};
        int a1 = axis(e1), a2 = axis(e2);
        if (a1 < 0 || a2 < 0 || a1 == a2) {
            // a slanted piece (a pen, a stethoscope): gone if its middle was in the hole
            float cx = tl[0] + (e1[0] + e2[0]) / 2, cy = tl[1] + (e1[1] + e2[1]) / 2, cz = tl[2] + (e1[2] + e2[2]) / 2;
            if (cz > BACK && cz < FRONT && inside(cx, cy)) {
                pieces.clear();
            }
            return;
        }
        int a3 = 3 - a1 - a2;
        for (float[] r : RECTS) {
            float[] lo = {r[0], r[2], BACK}, hi = {r[1], r[3], FRONT};
            if (tl[a3] <= lo[a3] + EPS || tl[a3] >= hi[a3] - EPS) {
                continue;   // it lies beside this part of the hole, not across it
            }
            float s0 = (lo[a1] - tl[a1]) / e1[a1], s1 = (hi[a1] - tl[a1]) / e1[a1];
            float t0 = (lo[a2] - tl[a2]) / e2[a2], t1 = (hi[a2] - tl[a2]) / e2[a2];
            subtract(pieces, Math.max(0f, Math.min(s0, s1)), Math.min(1f, Math.max(s0, s1)),
                    Math.max(0f, Math.min(t0, t1)), Math.min(1f, Math.max(t0, t1)));
        }
    }

    /** Takes a rectangle out of every piece, leaving up to four round it. */
    private static void subtract(List<float[]> pieces, float s0, float s1, float t0, float t1) {
        if (s1 - s0 < EPS || t1 - t0 < EPS) {
            return;
        }
        List<float[]> left = new ArrayList<>();
        for (float[] p : pieces) {
            float a0 = Math.max(p[0], s0), a1 = Math.min(p[1], s1), b0 = Math.max(p[2], t0), b1 = Math.min(p[3], t1);
            if (a1 - a0 < EPS || b1 - b0 < EPS) {
                left.add(p);
                continue;
            }
            add(left, p[0], p[1], p[2], b0);
            add(left, p[0], p[1], b1, p[3]);
            add(left, p[0], a0, b0, b1);
            add(left, a1, p[1], b0, b1);
        }
        pieces.clear();
        pieces.addAll(left);
    }

    private static void add(List<float[]> pieces, float s0, float s1, float t0, float t1) {
        if (s1 - s0 > EPS && t1 - t0 > EPS) {
            pieces.add(new float[] {s0, s1, t0, t1});
        }
    }

    /** A point of a quad by how far across (s) and down (t) its texture it is. */
    private static void corner(ZBodyMesh m, int q, float s, float t, float[] out, int o) {
        int b = q * 12;
        for (int k = 0; k < 3; k++) {
            float top = m.pos[b + k] + (m.pos[b + 3 + k] - m.pos[b + k]) * s;
            float bottom = m.pos[b + 9 + k] + (m.pos[b + 6 + k] - m.pos[b + 9 + k]) * s;
            out[o + k] = top + (bottom - top) * t;
        }
    }

    /** The axis a quad's edge runs along (0 x, 1 y, 2 z), or -1 if it runs slant. */
    private static int axis(float[] e) {
        float len = (float) Math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]);
        for (int a = 0; a < 3; a++) {
            if (len > 0f && Math.abs(e[a]) > len * 0.999f) {
                return a;
            }
        }
        return -1;
    }

    /** Whether a point of the chest (x, y) is in the hole. */
    private static boolean inside(float x, float y) {
        for (float[] r : RECTS) {
            if (x > r[0] && x < r[1] && y > r[2] && y < r[3]) {
                return true;
            }
        }
        return false;
    }

    /** Whether a quad lies over a rectangle of the chest (x0, x1, y0, y1). */
    private static boolean overlaps(ZBodyMesh m, int q, float[] rect) {
        float x0 = Float.MAX_VALUE, x1 = -Float.MAX_VALUE, y0 = Float.MAX_VALUE, y1 = -Float.MAX_VALUE;
        for (int c = 0; c < 4; c++) {
            float x = m.pos[q * 12 + c * 3], y = m.pos[q * 12 + c * 3 + 1];
            x0 = Math.min(x0, x);
            x1 = Math.max(x1, x);
            y0 = Math.min(y0, y);
            y1 = Math.max(y1, y);
        }
        return x1 > rect[0] && x0 < rect[1] && y1 > rect[2] && y0 < rect[3];
    }

    /** How far out the outermost thing worn over a point of the chest is, in front or behind. */
    private static float surface(ZBodyMesh m, float x, float y, boolean front) {
        float best = front ? BODY_FRONT : BODY_BACK;
        float[] point = {x - EPS, x + EPS, y - EPS, y + EPS};
        for (int q = 0; q < m.count; q++) {
            float nz = m.normal[q * 3 + 2], z = m.pos[q * 12 + 2];
            if (m.bone[q] != ZMove.SPINE || !overlaps(m, q, point)) {
                continue;
            }
            if (front && nz > 0.9f && z <= FRONT) {
                best = Math.max(best, z);
            } else if (!front && nz < -0.9f && z >= BACK) {
                best = Math.min(best, z);
            }
        }
        return best;
    }

    /** The outline's edges: between each cell of the hole's grid that's in it and its neighbour that isn't. */
    private static float[][] walls() {
        float[] xs = edges(0), ys = edges(2);
        List<float[]> walls = new ArrayList<>();
        for (int i = 0; i + 1 < xs.length; i++) {
            for (int j = 0; j + 1 < ys.length; j++) {
                if (!cell(xs, ys, i, j)) {
                    continue;
                }
                if (!cell(xs, ys, i - 1, j)) {
                    walls.add(new float[] {xs[i], ys[j], xs[i], ys[j + 1], 1f, 0f});
                }
                if (!cell(xs, ys, i + 1, j)) {
                    walls.add(new float[] {xs[i + 1], ys[j], xs[i + 1], ys[j + 1], -1f, 0f});
                }
                if (!cell(xs, ys, i, j - 1)) {
                    walls.add(new float[] {xs[i], ys[j], xs[i + 1], ys[j], 0f, 1f});
                }
                if (!cell(xs, ys, i, j + 1)) {
                    walls.add(new float[] {xs[i], ys[j + 1], xs[i + 1], ys[j + 1], 0f, -1f});
                }
            }
        }
        return walls.toArray(new float[0][]);
    }

    /** The rectangles' edges across (k 0) or up (k 2), in order, once each. */
    private static float[] edges(int k) {
        TreeSet<Float> set = new TreeSet<>();
        for (float[] r : RECTS) {
            set.add(r[k]);
            set.add(r[k + 1]);
        }
        float[] edges = new float[set.size()];
        int i = 0;
        for (float v : set) {
            edges[i++] = v;
        }
        return edges;
    }

    private static boolean cell(float[] xs, float[] ys, int i, int j) {
        return i >= 0 && j >= 0 && i + 1 < xs.length && j + 1 < ys.length
                && inside((xs[i] + xs[i + 1]) / 2, (ys[j] + ys[j + 1]) / 2);
    }

    private ChestHole() {
    }
}
