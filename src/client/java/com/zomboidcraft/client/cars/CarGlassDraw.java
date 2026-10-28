package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarGlass;
import com.zomboidcraft.world.cars.CarModels;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * A car's windows one by one, close up: a shattered one left out, a wound-down one slid down into its door, and
 * on each that's there its marks (tools/cars/glass_tex.py): road dust and dried rain, the bullet holes exactly
 * where they went in, the cracks where zombies beat on it. The marks are cut out rather than see-through, so
 * shader packs draw them solid, and they're drawn from inside too, where the see-through glass is left out: the
 * dust tells you the glass is there, its going tells you it isn't.
 */
final class CarGlassDraw {
    static final Identifier MARKS = ZomboidCraft.id("textures/cars/glass_marks.png");

    /** How far off the glass the marks sit, each face, so they don't fight it. */
    private static final float LIFT = 0.004f;
    /** Half the width of a bullet hole's cracks and of a blow's, on the glass (metres). */
    private static final float HOLE = 0.12f, CRACK = 0.2f;

    /** One window's frame for drawing: unit down its glass and across it, how far down its sill is and its drop. */
    record Frame(Vec3 down, Vec3 side, double sill, double drop, double reach) {
        static Frame of(CarGlass.Window w) {
            Vec3 down = w.down();
            double reach = Math.max(0.2, Math.max(w.a().length(), w.b().length()));
            return new Frame(down, down.cross(w.normal()).normalize(), w.sill(), w.drop(), reach);
        }
    }

    /** The car's windows as drawn now: which window each glass triangle of each mesh is, and how each one is. */
    record Set(Map<CarAssets.Mesh, int[]> ids, CarGlass.Window[] windows, Frame[] frames, CarGlass.Panes panes, float[] wound) {
        boolean gone(int w) {
            return w < 0 || w >= windows.length || windows[w] == null || panes.state[w] == CarGlass.SHATTERED;
        }

        float wound(int w) {
            return w < wound.length ? wound[w] : 0;
        }
    }

    /** The windows of the car being drawn, if it's drawn close enough (whole mesh) to show them one by one; else null. */
    static Set of(CarRenderState state, CarAssets.Ready car, CarModels.Model m) {
        if (state.air || state.broken || state.lod != 0 || m == null || m.glassTris == null || state.panes == null
                || state.look < 0 || state.look >= m.glassTris.length || m.glassTris[state.look] == null) {
            return null;
        }
        CarGlass.Window[] ws = CarGlass.windows(m, state.look);
        int[][] tris = m.glassTris[state.look];
        if (ws.length == 0 || ws.length != state.panes.size() || tris.length != 1 + car.lids().size()) {
            return null;
        }
        Map<CarAssets.Mesh, int[]> ids = new IdentityHashMap<>();
        for (int i = 0; i < tris.length; i++) {
            CarAssets.Mesh mesh = i == 0 ? car.mesh() : car.lids().get(i - 1).mesh();
            if (tris[i] == null || tris[i].length != mesh.triangles() - mesh.opaque()) {
                return null;    // a model packed before its windows were: draw its glass whole
            }
            ids.put(mesh, tris[i]);
        }
        Frame[] frames = new Frame[ws.length];
        for (int i = 0; i < ws.length; i++) {
            frames[i] = ws[i] == null ? null : Frame.of(ws[i]);
        }
        return new Set(ids, ws, frames, state.panes, state.wound);
    }

    /** A mesh's see-through glass: each window's triangles but the shattered ones, the wound-down ones slid down. */
    static void glass(Set set, CarAssets.Mesh mesh, CarRenderer.Turn turn, PoseStack.Pose pose, VertexConsumer out, int light, int colour) {
        int[] ids = set.ids().get(mesh);
        float[] p = mesh.pos(), uv = mesh.uv(), n = mesh.normal();
        float[] v = new float[3], m = new float[3];
        for (int t = mesh.opaque(); t < mesh.triangles(); t++) {
            int w = ids == null ? -1 : ids[t - mesh.opaque()];
            if (ids != null && set.gone(w)) {
                continue;
            }
            float f = ids == null ? 0 : set.wound(w);
            m[0] = n[t * 3]; m[1] = n[t * 3 + 1]; m[2] = n[t * 3 + 2];
            if (turn != null) {
                turn.apply(m, true);
            }
            for (int c = 0; c < 4; c++) {
                int i = t * 3 + Math.min(c, 2);
                v[0] = p[i * 3]; v[1] = p[i * 3 + 1]; v[2] = p[i * 3 + 2];
                if (f > 0) {
                    slide(v, set.windows()[w], set.frames()[w], f);
                }
                if (turn != null) {
                    turn.apply(v, false);
                }
                out.addVertex(pose, v[0], v[1], v[2]).setColor(colour).setUv(uv[i * 2], uv[i * 2 + 1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, m[0], m[1], m[2]);
            }
        }
    }

    /** The marks on a mesh's windows: the dust over each window's glass, then each of its holes and cracks. */
    static void marks(Set set, CarAssets.Mesh mesh, CarRenderer.Turn turn, PoseStack.Pose pose, VertexConsumer out, int light) {
        int[] ids = set.ids().get(mesh);
        if (ids == null) {
            return;
        }
        float[] p = mesh.pos();
        float[][] corner = new float[3][3];
        float[] tex = new float[6];
        java.util.BitSet here = new java.util.BitSet();
        for (int t = mesh.opaque(); t < mesh.triangles(); t++) {
            int w = ids[t - mesh.opaque()];
            if (set.gone(w)) {
                continue;
            }
            here.set(w);
            CarGlass.Window win = set.windows()[w];
            Frame fr = set.frames()[w];
            float f = set.wound(w);
            for (int c = 0; c < 3; c++) {
                int i = t * 3 + c;
                float[] v = corner[c];
                v[0] = p[i * 3]; v[1] = p[i * 3 + 1]; v[2] = p[i * 3 + 2];
                // the dust sheet laid over the window by where on it each corner is, the way the glass hangs
                double dx = v[0] - win.centre().x, dy = v[1] - win.centre().y, dz = v[2] - win.centre().z;
                double across = (dx * fr.side().x + dy * fr.side().y + dz * fr.side().z) / fr.reach();
                double down = (dx * fr.down().x + dy * fr.down().y + dz * fr.down().z) / fr.reach();
                tex[c * 2] = (float) (0.25 + 0.25 * clamp(across));
                tex[c * 2 + 1] = (float) (0.5 + 0.5 * clamp(down));
                if (f > 0) {
                    slide(v, win, fr, f);
                }
            }
            both(corner, tex, win.normal(), turn, pose, out, light);
        }
        for (int w = here.nextSetBit(0); w >= 0; w = here.nextSetBit(w + 1)) {
            List<CarGlass.Mark> marks = set.panes().marks.get(w);
            CarGlass.Window win = set.windows()[w];
            Frame fr = set.frames()[w];
            double dropped = set.wound(w) * fr.drop();
            for (int k = 0; k < marks.size(); k++) {
                CarGlass.Mark mk = marks.get(k);
                Vec3 at = win.at(mk.u(), mk.v());
                double below = at.subtract(win.centre()).dot(fr.down()) + dropped;
                if (below > fr.sill() - 0.01) {
                    continue;   // wound down into the door
                }
                at = at.add(fr.down().scale(dropped));
                // which of the two of each, and turned some way, so no two look alike
                int seed = w * 31 + k * 17 + Math.round(mk.u() * 64) * 7 + Math.round(mk.v() * 64);
                double angle = (seed * 2.399963) % (Math.PI * 2);
                float half = mk.crack() ? CRACK : Math.min(HOLE, Math.max(0.035f, (float) edge(win, at) * 1.6f));
                float u0 = (seed & 1) == 0 ? 0.5f : 0.75f, v0 = mk.crack() ? 0.5f : 0f;
                Vec3 x = fr.side().scale(Math.cos(angle)).add(fr.down().scale(Math.sin(angle))).scale(half);
                Vec3 y = fr.down().scale(Math.cos(angle)).subtract(fr.side().scale(Math.sin(angle))).scale(half);
                float[][] quad = new float[4][];
                Vec3[] q = {at.subtract(x).subtract(y), at.add(x).subtract(y), at.add(x).add(y), at.subtract(x).add(y)};
                for (int c = 0; c < 4; c++) {
                    quad[c] = new float[]{(float) q[c].x, (float) q[c].y, (float) q[c].z};
                }
                float[] quv = {u0, v0, u0 + 0.25f, v0, u0 + 0.25f, v0 + 0.5f, u0, v0 + 0.5f};
                bothQuad(quad, quv, win.normal(), turn, pose, out, light);
            }
        }
    }

    /** How far a point on the window is from the nearest edge of its outline (metres). */
    private static double edge(CarGlass.Window w, Vec3 at) {
        float[] r = w.ring();
        int n = r.length / 2;
        if (n < 3) {
            return 1;
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            Vec3 a = w.at(r[i * 2], r[i * 2 + 1]), b = w.at(r[(i + 1) % n * 2], r[(i + 1) % n * 2 + 1]);
            Vec3 ab = b.subtract(a);
            double t = ab.lengthSqr() < 1e-9 ? 0 : clamp01(at.subtract(a).dot(ab) / ab.lengthSqr());
            best = Math.min(best, at.distanceTo(a.add(ab.scale(t))));
        }
        return best;
    }

    /** Slides a point of a window down into its door by f of the way, no further than the sill. */
    private static void slide(float[] v, CarGlass.Window w, Frame fr, float f) {
        Vec3 d = fr.down();
        double s = (v[0] - w.centre().x) * d.x + (v[1] - w.centre().y) * d.y + (v[2] - w.centre().z) * d.z;
        double by = Math.min(f * fr.drop(), fr.sill() - s);
        if (by > 0) {
            v[0] += (float) (d.x * by);
            v[1] += (float) (d.y * by);
            v[2] += (float) (d.z * by);
        }
    }

    /** A triangle of marks drawn on both faces of the glass (each face just off it, wound to face its own way). */
    private static void both(float[][] tri, float[] uv, Vec3 normal, CarRenderer.Turn turn, PoseStack.Pose pose, VertexConsumer out, int light) {
        for (int face = 0; face < 2; face++) {
            float sgn = face == 0 ? 1 : -1;
            float[] nm = {(float) normal.x * sgn, (float) normal.y * sgn, (float) normal.z * sgn};
            // wound so its front faces along the normal it's lifted toward
            float[] e1 = sub(tri[1], tri[0]), e2 = sub(tri[2], tri[0]);
            boolean flip = (e1[1] * e2[2] - e1[2] * e2[1]) * nm[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * nm[1]
                    + (e1[0] * e2[1] - e1[1] * e2[0]) * nm[2] < 0;
            int[] order = flip ? new int[]{0, 2, 1, 1} : new int[]{0, 1, 2, 2};
            emit(tri, uv, order, nm, turn, pose, out, light);
        }
    }

    private static void bothQuad(float[][] quad, float[] uv, Vec3 normal, CarRenderer.Turn turn, PoseStack.Pose pose, VertexConsumer out, int light) {
        for (int face = 0; face < 2; face++) {
            float sgn = face == 0 ? 1 : -1;
            float[] nm = {(float) normal.x * sgn, (float) normal.y * sgn, (float) normal.z * sgn};
            float[] e1 = sub(quad[1], quad[0]), e2 = sub(quad[2], quad[0]);
            boolean flip = (e1[1] * e2[2] - e1[2] * e2[1]) * nm[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * nm[1]
                    + (e1[0] * e2[1] - e1[1] * e2[0]) * nm[2] < 0;
            emit(quad, uv, flip ? new int[]{0, 3, 2, 1} : new int[]{0, 1, 2, 3}, nm, turn, pose, out, light);
        }
    }

    private static void emit(float[][] pts, float[] uv, int[] order, float[] nm, CarRenderer.Turn turn, PoseStack.Pose pose,
                             VertexConsumer out, int light) {
        float[] n = nm.clone();
        if (turn != null) {
            turn.apply(n, true);
        }
        float[] v = new float[3];
        for (int c : order) {
            v[0] = pts[c][0] + nm[0] * LIFT;
            v[1] = pts[c][1] + nm[1] * LIFT;
            v[2] = pts[c][2] + nm[2] * LIFT;
            if (turn != null) {
                turn.apply(v, false);
            }
            out.addVertex(pose, v[0], v[1], v[2]).setColor(-1).setUv(uv[c * 2], uv[c * 2 + 1])
                    .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, n[0], n[1], n[2]);
        }
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double clamp(double x) {
        return Math.max(-1, Math.min(1, x));
    }

    private static double clamp01(double x) {
        return Math.max(0, Math.min(1, x));
    }

    private CarGlassDraw() {
    }
}
