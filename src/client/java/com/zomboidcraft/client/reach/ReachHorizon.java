package com.zomboidcraft.client.reach;

import com.zomboidcraft.world.reach.ReachLand;
import java.util.ArrayList;
import java.util.List;

/**
 * The land past the edge of the Reach map, out to the horizon (Josiah 2026-11-09: the landscape must never stop
 * suddenly and leave cities floating in the distance). Seen, never walked. The edge's own ground carries outward and
 * eases into far mountain ranges where the edge is land (the west's mountains, the south's desert highlands), and into
 * open sea where it is sea (the east, most of the north). Colours follow the climate along the edge, so snow only caps
 * the ranges beyond the cold north.
 *
 * <p>It meets the map's own land without a crack: a strip along each side zips the map's last row of vertices (every
 * 8 m, the very same points and heights) to the first row of the horizon's grid (every ~250 m), and the grid goes on
 * from there in 4 km tiles so those off screen are skipped.
 */
final class ReachHorizon {
    static final float REACH = 22000;  // how far past the map's edge it goes
    static final int CELLS_ACROSS = 64; // horizon cells along one side of the map (about 250 m each)
    static final int TILE = 16;         // cells per tile side
    static final float BLEND = 4500;    // the edge's own height fades into the far ranges over this distance

    private final ReachLand land;
    private final int iEnd;             // the last sample index of the map's near mesh, on both axes
    private final float mx0, mz0, span, cellH, seaEnd;
    private final int g, r;             // cells across the map, cells out past each side

    private ReachHorizon(ReachLand land) {
        this.land = land;
        iEnd = ReachLandMesh.end(land, ReachLandMesh.STEP);
        mx0 = land.firstX();
        mz0 = land.firstZ();
        span = iEnd * (float) land.cell();
        g = CELLS_ACROSS;
        cellH = span / g;
        r = (int) Math.ceil(REACH / cellH);
        seaEnd = ReachLandMesh.end(land, ReachLandMesh.FAR_STEP) * (float) land.cell();
    }

    static void build(ReachLand land, List<ReachGlb.Batch> out) {
        new ReachHorizon(land).build(out);
    }

    private void build(List<ReachGlb.Batch> out) {
        for (int tj = -r; tj < g + r; tj += TILE) {
            for (int ti = -r; ti < g + r; ti += TILE) {
                grid(ti, tj, Math.min(TILE, g + r - ti), Math.min(TILE, g + r - tj), out);
            }
        }
        for (int side = 0; side < 4; side++) {
            strip(side, out);
        }
    }

    // ---- the grid: every cell outside the map and its edge strips

    private void grid(int ti, int tj, int w, int h, List<ReachGlb.Batch> out) {
        Mesh m = new Mesh();
        int vw = w + 1;
        for (int j = 0; j <= h; j++) {
            for (int i = 0; i <= w; i++) {
                gridVertex(m, ti + i, tj + j);
            }
        }
        List<float[]> sea = new ArrayList<>();
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) {
                int ci = ti + i, cj = tj + j;
                boolean inI = ci >= 0 && ci < g, inJ = cj >= 0 && cj < g;
                if ((inI && cj >= -1 && cj <= g) || (inJ && ci >= -1 && ci <= g)) {
                    continue; // the map itself, or one of its edge strips
                }
                int a = j * vw + i, b = a + 1, d = a + vw, e = d + 1;
                m.tri(a, d, b);
                m.tri(b, d, e);
                if (m.y(a) < ReachLand.SEA || m.y(b) < ReachLand.SEA || m.y(d) < ReachLand.SEA || m.y(e) < ReachLand.SEA) {
                    sea.add(new float[] {gx(ci), gz(cj), gx(ci + 1), gz(cj + 1)});
                }
            }
        }
        m.emit(out);
        if (!sea.isEmpty()) {
            out.add(sea(sea));
        }
    }

    private float gx(int gi) {
        return mx0 + gi * cellH;
    }

    private float gz(int gj) {
        return mz0 + gj * cellH;
    }

    private int gridVertex(Mesh m, int gi, int gj) {
        float x = gx(gi), z = gz(gj);
        if (gi >= 0 && gi <= g && gj >= 0 && gj <= g) {
            // on the map (only its corners are ever used): the map's own vertex there
            return innerVertex(m, gi == 0 ? 0 : iEnd, gj == 0 ? 0 : iEnd);
        }
        float y = height(x, z);
        float e = cellH / 2;
        float dx = (height(x + e, z) - height(x - e, z)) / (2 * e), dz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        float len = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        return m.vertex(x, y, z, -dx / len, 1 / len, -dz / len, colour(x, z, y, 1 / len));
    }

    /** The map's own vertex at sample (si, sj), exactly as its near mesh makes it. */
    private int innerVertex(Mesh m, int si, int sj) {
        int step = ReachLandMesh.STEP;
        float c = land.cell() * step;
        float y = land.sample(si, sj);
        float dx = (land.sample(si + step, sj) - land.sample(si - step, sj)) / (2 * c);
        float dz = (land.sample(si, sj + step) - land.sample(si, sj - step)) / (2 * c);
        float len = (float) Math.sqrt(dx * dx + 1 + dz * dz);
        return m.vertex(land.firstX() + si * land.cell(), y, land.firstZ() + sj * land.cell(), -dx / len, 1 / len, -dz / len,
                ReachLandMesh.colour(land, si, sj, 1 / len));
    }

    // ---- the four edge strips: the map's last row zipped to the grid's first

    /** side 0 north (z min), 1 south (z max), 2 west (x min), 3 east (x max). */
    private void strip(int side, List<ReachGlb.Batch> out) {
        Mesh m = new Mesh();
        boolean alongX = side < 2;
        int fixedSample = side == 0 || side == 2 ? 0 : iEnd;
        int outer = side == 0 || side == 2 ? -1 : g + 1;
        List<Integer> in = new ArrayList<>(), ou = new ArrayList<>();
        List<Float> pin = new ArrayList<>(), pou = new ArrayList<>();
        for (int s = 0; s <= iEnd; s += ReachLandMesh.STEP) {
            in.add(alongX ? innerVertex(m, s, fixedSample) : innerVertex(m, fixedSample, s));
            pin.add(s * (float) land.cell());
        }
        for (int k = 0; k <= g; k++) {
            ou.add(alongX ? gridVertex(m, k, outer) : gridVertex(m, outer, k));
            pou.add(k * cellH);
        }
        int i = 0, j = 0;
        while (i < in.size() - 1 || j < ou.size() - 1) {
            if (j == ou.size() - 1 || (i < in.size() - 1 && pin.get(i + 1) <= pou.get(j + 1))) {
                m.tri(in.get(i), in.get(i + 1), ou.get(j));
                i++;
            } else {
                m.tri(in.get(i), ou.get(j + 1), ou.get(j));
                j++;
            }
        }
        m.emit(out);
        // the sea on from the map's own sea out to the grid
        List<float[]> sea = new ArrayList<>();
        float inner = side == 0 ? mz0 : side == 2 ? mx0 : (side == 1 ? mz0 : mx0) + seaEnd;
        float far = side == 0 ? mz0 - cellH : side == 2 ? mx0 - cellH : (side == 1 ? mz0 : mx0) + span + cellH;
        for (int k = 0; k < g; k++) {
            float a0 = (alongX ? mx0 : mz0) + k * cellH, a1 = a0 + cellH;
            boolean under = m.y(ou.get(k)) < ReachLand.SEA || m.y(ou.get(k + 1)) < ReachLand.SEA;
            for (float t = a0; t <= a1 && !under; t += 32) {
                float ex = alongX ? t : (side == 2 ? mx0 : mx0 + span), ez = alongX ? (side == 0 ? mz0 : mz0 + span) : t;
                under = land.height(ex, ez) < ReachLand.SEA;
            }
            if (under) {
                float lo = Math.min(inner, far), hi = Math.max(inner, far);
                sea.add(alongX ? new float[] {a0, lo, a1, hi} : new float[] {lo, a0, hi, a1});
            }
        }
        if (!sea.isEmpty()) {
            out.add(sea(sea));
        }
    }

    // ---- shape and colour

    /** How far (x, z) is outside the map. */
    private float outside(float x, float z) {
        float dx = Math.max(mx0 - x, x - (mx0 + span)), dz = Math.max(mz0 - z, z - (mz0 + span));
        return (float) Math.sqrt(Math.max(0, dx) * Math.max(0, dx) + Math.max(0, dz) * Math.max(0, dz));
    }

    private float clampX(float x) {
        return Math.max(mx0, Math.min(mx0 + span, x));
    }

    private float clampZ(float z) {
        return Math.max(mz0, Math.min(mz0 + span, z));
    }

    /** The edge's ground near (x, z), averaged along the edge so a ragged coast doesn't streak outward. */
    private float edgeAverage(float x, float z) {
        float sum = 0;
        for (int o = -2; o <= 2; o++) {
            sum += land.height(clampX(x + o * 300), clampZ(z + o * 300));
        }
        return sum / 5;
    }

    float height(float x, float z) {
        float d = outside(x, z);
        float exact = land.height(clampX(x), clampZ(z));
        float edge = exact + (edgeAverage(x, z) - exact) * smooth(d / 1500);
        float t = smooth(d / BLEND);
        // open sea shelving down where the edge is sea, far ranges where it is land, and a falling coast between them
        float landish = smooth((edge - ReachLand.SEA + 4) / 60);
        float rise = Math.min(1, d / 12000f);
        float ranges = Math.max(edge, 120) * 0.6f + 300 + 1500 * ridged(x / 3600f, z / 3600f) * (0.5f + 0.5f * rise);
        float target = -90 + (ranges + 90) * landish;
        return edge + (target - edge) * t;
    }

    private int colour(float x, float z, float y, float up) {
        // the edge's climates over a couple of kilometres, blended, so they fade rather than streak
        float rr = 0, gg = 0, bb = 0, cold = 0;
        for (int o = -2; o <= 2; o++) {
            int climate = ReachLand.climate(land.flagsAt(clampX(x + o * 500), clampZ(z + o * 500)));
            int c = ReachLandMesh.CLIMATE[Math.min(ReachLandMesh.CLIMATE.length - 1, climate == 0 ? 3 : climate)];
            rr += (c >>> 16) & 0xFF;
            gg += (c >>> 8) & 0xFF;
            bb += c & 0xFF;
            cold += climate == 1 || climate == 2 ? 0.2f : 0;
        }
        int c = 0xFF000000 | Math.round(rr / 5) << 16 | Math.round(gg / 5) << 8 | Math.round(bb / 5);
        float rock = Math.max(Math.max(0, Math.min(1, (0.85f - up) / 0.25f)), Math.max(0, Math.min(0.7f, (y - 700) / 900)));
        c = ReachLandMesh.mix(c, ReachLandMesh.ROCK, rock);
        if (cold > 0 && y > 950) {
            c = ReachLandMesh.mix(c, ReachLandMesh.CLIMATE[1], Math.min(1, (y - 950) / 250) * cold);
        }
        if (y < ReachLand.SEA + 3) {
            c = ReachLandMesh.mix(c, 0xFF8E8457, 0.5f); // wet sand at the waterline
        }
        return c;
    }

    private static float smooth(float t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Ridged fractal noise, 0..1: sharp crests like a mountain range. */
    private static float ridged(float x, float z) {
        float sum = 0, amp = 0.55f, norm = 0;
        for (int o = 0; o < 5; o++) {
            float n = 1 - Math.abs(2 * valueNoise(x, z, o) - 1);
            sum += n * n * amp;
            norm += amp;
            x *= 2.03f;
            z *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    private static float valueNoise(float x, float z, int seed) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        float fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        float a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed), c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    private static float hash(int x, int z, int seed) {
        int h = x * 374761393 + z * 668265263 + seed * 1442695041;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    // ---- mesh building

    private static ReachGlb.Batch sea(List<float[]> rects) {
        float[] pos = new float[rects.size() * 12], uv = new float[rects.size() * 8], normal = new float[rects.size() * 12];
        int[] color = new int[rects.size() * 4], index = new int[rects.size() * 6];
        for (int q = 0; q < rects.size(); q++) {
            float[] rc = rects.get(q);
            float[][] corners = {{rc[0], rc[1]}, {rc[2], rc[1]}, {rc[0], rc[3]}, {rc[2], rc[3]}};
            for (int v = 0; v < 4; v++) {
                pos[(q * 4 + v) * 3] = corners[v][0];
                pos[(q * 4 + v) * 3 + 1] = ReachLand.SEA;
                pos[(q * 4 + v) * 3 + 2] = corners[v][1];
                normal[(q * 4 + v) * 3 + 1] = 1;
                color[q * 4 + v] = ReachLandMesh.SEA_COLOUR;
            }
            int b = q * 4;
            index[q * 6] = b;
            index[q * 6 + 1] = b + 2;
            index[q * 6 + 2] = b + 1;
            index[q * 6 + 3] = b + 1;
            index[q * 6 + 4] = b + 2;
            index[q * 6 + 5] = b + 3;
        }
        return new ReachGlb.Batch(1, pos, uv, normal, color, index);
    }

    /** A growing ground mesh whose triangles always face up, the way the map's own land does. */
    private static final class Mesh {
        private float[] pos = new float[3 * 1024], normal = new float[3 * 1024];
        private int[] color = new int[1024];
        private int[] index = new int[3 * 1024];
        private int vertices, indices;

        int vertex(float x, float y, float z, float nx, float ny, float nz, int c) {
            if (vertices == color.length) {
                pos = java.util.Arrays.copyOf(pos, pos.length * 2);
                normal = java.util.Arrays.copyOf(normal, normal.length * 2);
                color = java.util.Arrays.copyOf(color, color.length * 2);
            }
            pos[vertices * 3] = x;
            pos[vertices * 3 + 1] = y;
            pos[vertices * 3 + 2] = z;
            normal[vertices * 3] = nx;
            normal[vertices * 3 + 1] = ny;
            normal[vertices * 3 + 2] = nz;
            color[vertices] = c;
            return vertices++;
        }

        float y(int v) {
            return pos[v * 3 + 1];
        }

        void tri(int a, int b, int c) {
            float ux = pos[b * 3] - pos[a * 3], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
            float vx = pos[c * 3] - pos[a * 3], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
            // the map's land winds so that (b - a) x (c - a) points up: uz * vx - ux * vz > 0
            if (uz * vx - ux * vz < 0) {
                int t = b;
                b = c;
                c = t;
            }
            if (indices + 3 > index.length) {
                index = java.util.Arrays.copyOf(index, index.length * 2);
            }
            index[indices++] = a;
            index[indices++] = b;
            index[indices++] = c;
        }

        void emit(List<ReachGlb.Batch> out) {
            if (indices == 0) {
                return;
            }
            // only the vertices the triangles use, so skipped cells don't widen the tile's box
            int[] remap = new int[vertices];
            java.util.Arrays.fill(remap, -1);
            int used = 0;
            for (int k = 0; k < indices; k++) {
                if (remap[index[k]] < 0) {
                    remap[index[k]] = used++;
                }
            }
            float[] p = new float[used * 3], n = new float[used * 3], uv = new float[used * 2];
            int[] c = new int[used], ix = new int[indices];
            for (int v = 0; v < vertices; v++) {
                int t = remap[v];
                if (t < 0) {
                    continue;
                }
                System.arraycopy(pos, v * 3, p, t * 3, 3);
                System.arraycopy(normal, v * 3, n, t * 3, 3);
                c[t] = color[v];
            }
            for (int k = 0; k < indices; k++) {
                ix[k] = remap[index[k]];
            }
            out.add(new ReachGlb.Batch(0, p, uv, n, c, ix));
        }
    }
}
