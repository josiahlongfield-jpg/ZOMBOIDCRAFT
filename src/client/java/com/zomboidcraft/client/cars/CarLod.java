package com.zomboidcraft.client.cars;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Coarser copies of a car's mesh for drawing it further off. A car is about 5,000 triangles
 * rebuilt every frame, and a town street parks over a hundred of them, so past a few metres each
 * car is drawn from a copy with its corners snapped together on a grid (bigger cells further
 * out): small parts like grilles, mirrors and wheel spokes fold away, the body keeps its shape.
 */
final class CarLod {
    /** Grid cell (metres) for each level; level 0 is the full mesh. */
    private static final float[] CELL = {0, 0.08f, 0.16f, 0.32f, 0.6f};
    /** How far (blocks) the camera must be for each level. */
    private static final double[] FROM = {0, 10, 24, 44, 68};

    static final int LEVELS = CELL.length;
    /** From this level on the windows are drawn solid and dark: no see-through pass for far cars. */
    static final int SOLID_GLASS = 3;

    /** The level to draw a car at, from its squared distance to the camera. */
    static int level(double distanceSq) {
        int level = 0;
        for (int i = 1; i < LEVELS; i++) {
            if (distanceSq >= FROM[i] * FROM[i]) {
                level = i;
            }
        }
        return level;
    }

    /** A copy of mesh at level, with its glass still after its solid triangles. */
    static CarAssets.Mesh simplify(CarAssets.Mesh mesh, int level) {
        if (level == 0) {
            return mesh;
        }
        float cell = CELL[level];
        float[] p = mesh.pos();
        int corners = mesh.triangles() * 3;
        // every corner falls into a grid cell; the cell's corners all move to their average
        int[] cellOf = new int[corners];
        Map<Long, Integer> cells = new HashMap<>();
        for (int i = 0; i < corners; i++) {
            long key = key(p[i * 3] / cell, p[i * 3 + 1] / cell, p[i * 3 + 2] / cell);
            Integer c = cells.get(key);
            if (c == null) {
                c = cells.size();
                cells.put(key, c);
            }
            cellOf[i] = c;
        }
        float[] sum = new float[cells.size() * 3];
        int[] count = new int[cells.size()];
        for (int i = 0; i < corners; i++) {
            int c = cellOf[i];
            sum[c * 3] += p[i * 3];
            sum[c * 3 + 1] += p[i * 3 + 1];
            sum[c * 3 + 2] += p[i * 3 + 2];
            count[c]++;
        }
        for (int c = 0; c < count.length; c++) {
            sum[c * 3] /= count[c];
            sum[c * 3 + 1] /= count[c];
            sum[c * 3 + 2] /= count[c];
        }

        float[] pos = new float[mesh.pos().length], uv = new float[mesh.uv().length], normal = new float[mesh.normal().length];
        int kept = keep(mesh, 0, mesh.opaque(), cellOf, sum, pos, uv, normal, 0);
        int opaque = kept;
        kept = keep(mesh, mesh.opaque(), mesh.triangles(), cellOf, sum, pos, uv, normal, kept);
        return new CarAssets.Mesh(kept, opaque, Arrays.copyOf(pos, kept * 9), Arrays.copyOf(uv, kept * 6),
                Arrays.copyOf(normal, kept * 3));
    }

    /**
     * Copies triangles from to end that still span three cells, once per cell triple and facing
     * (so both sides of a thin panel stay), into the output from triangle at. Returns the new count.
     */
    private static int keep(CarAssets.Mesh mesh, int from, int end, int[] cellOf, float[] avg,
                            float[] pos, float[] uv, float[] normal, int at) {
        Set<Long> seen = new HashSet<>();
        float[] srcUv = mesh.uv();
        for (int t = from; t < end; t++) {
            int a = cellOf[t * 3], b = cellOf[t * 3 + 1], c = cellOf[t * 3 + 2];
            if (a == b || b == c || a == c) {
                continue;
            }
            // the same triangle turned round to start at its lowest cell, so duplicates match
            long id = a < b && a < c ? triple(a, b, c) : b < c ? triple(b, c, a) : triple(c, a, b);
            if (!seen.add(id)) {
                continue;
            }
            int[] corner = {a, b, c};
            float ux = avg[b * 3] - avg[a * 3], uy = avg[b * 3 + 1] - avg[a * 3 + 1], uz = avg[b * 3 + 2] - avg[a * 3 + 2];
            float vx = avg[c * 3] - avg[a * 3], vy = avg[c * 3 + 1] - avg[a * 3 + 1], vz = avg[c * 3 + 2] - avg[a * 3 + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-6f) {
                continue;  // flattened to a sliver
            }
            for (int k = 0; k < 3; k++) {
                System.arraycopy(avg, corner[k] * 3, pos, at * 9 + k * 3, 3);
                uv[at * 6 + k * 2] = srcUv[(t * 3 + k) * 2];
                uv[at * 6 + k * 2 + 1] = srcUv[(t * 3 + k) * 2 + 1];
            }
            normal[at * 3] = nx / len;
            normal[at * 3 + 1] = ny / len;
            normal[at * 3 + 2] = nz / len;
            at++;
        }
        return at;
    }

    private static long key(float x, float y, float z) {
        long bias = 1 << 20;
        return ((long) Math.floor(x) + bias) << 42 | ((long) Math.floor(y) + bias) << 21 | ((long) Math.floor(z) + bias);
    }

    private static long triple(int a, int b, int c) {
        return (long) a << 42 | (long) b << 21 | c;
    }

    private CarLod() {
    }
}
