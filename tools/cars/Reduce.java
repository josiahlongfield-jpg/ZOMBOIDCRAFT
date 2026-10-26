import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.PriorityQueue;

/**
 * Helpers aircraft.py runs for a bought model far too detailed for the game (javac, then java -cp).
 *
 *   java Reduce decimate TRIS.f32 GROUPS.i32 TARGET OUT.f32 OUT_SRC.i32
 * welds the triangle soup by position and collapses edges by quadric error (Garland and Heckbert)
 * until TARGET triangles are left. Open edges and the edges between groups (materials) carry heavy
 * extra quadrics, so outlines and colour borders keep their shape. Writes the triangles left and,
 * for each, the input triangle it came from.
 *
 *   java Reduce nearest TRIS.f32 GROUPS.i32 QUERIES.f32 OUT.f32
 * finds, for each query (x, y, z, nx, ny, nz, group), the closest point on an input triangle of the
 * same group facing the same way (any triangle if none is near) and writes its triangle index and
 * barycentrics, so the reduced model can be painted from the original's texture.
 *
 * All files are little-endian; a triangle is three corners of three floats.
 */
public final class Reduce {
    public static void main(String[] args) throws IOException {
        if (args[0].equals("decimate")) {
            decimate(floats(args[1]), ints(args[2]), Integer.parseInt(args[3]), args[4], args[5]);
        } else {
            nearest(floats(args[1]), ints(args[2]), floats(args[3]), args[4]);
        }
    }

    // ------------------------------------------------------------------ decimate

    static double[] pos;          // welded vertices
    static double[][] quad;       // per vertex: the 10 coefficients of a symmetric 4x4 quadric
    static int[] face;            // 3 vertex ids per face
    static boolean[] dead;
    static int[][] vfaces;        // faces round each vertex (may list dead faces)
    static int[] vcount;
    static int[] stamp;

    static void decimate(float[] tris, int[] groups, int target, String out, String outSrc) throws IOException {
        int n = tris.length / 9;
        HashMap<Long, Integer> weld = new HashMap<>();
        double[] p = new double[n * 9];
        int nv = 0;
        face = new int[n * 3];
        for (int i = 0; i < n * 3; i++) {
            long kx = Math.round(tris[i * 3] * 2000.0) + (1 << 20), ky = Math.round(tris[i * 3 + 1] * 2000.0) + (1 << 20);
            long kz = Math.round(tris[i * 3 + 2] * 2000.0) + (1 << 20);
            long key = (kx << 42) | (ky << 21) | kz;
            Integer id = weld.get(key);
            if (id == null) {
                id = nv++;
                weld.put(key, id);
                p[id * 3] = tris[i * 3];
                p[id * 3 + 1] = tris[i * 3 + 1];
                p[id * 3 + 2] = tris[i * 3 + 2];
            }
            face[i] = id;
        }
        pos = Arrays.copyOf(p, nv * 3);
        dead = new boolean[n];
        quad = new double[nv][10];
        vcount = new int[nv];
        vfaces = new int[nv][];
        stamp = new int[nv];
        int live = 0;
        for (int f = 0; f < n; f++) {
            int a = face[f * 3], b = face[f * 3 + 1], c = face[f * 3 + 2];
            if (a == b || b == c || a == c) {
                dead[f] = true;
                continue;
            }
            live++;
            double[] pl = plane(a, b, c);
            if (pl == null) {
                continue;
            }
            double w = pl[4];
            for (int k = 0; k < 3; k++) {
                add(quad[face[f * 3 + k]], pl, w);
            }
        }
        for (int f = 0; f < n; f++) {
            if (!dead[f]) {
                for (int k = 0; k < 3; k++) {
                    vcount[face[f * 3 + k]]++;
                }
            }
        }
        for (int v = 0; v < nv; v++) {
            vfaces[v] = new int[Math.max(4, vcount[v])];
            vcount[v] = 0;
        }
        for (int f = 0; f < n; f++) {
            if (!dead[f]) {
                for (int k = 0; k < 3; k++) {
                    attach(face[f * 3 + k], f);
                }
            }
        }
        // open edges and colour borders: a plane through the edge, upright to the face
        HashMap<Long, int[]> edges = new HashMap<>();
        for (int f = 0; f < n; f++) {
            if (dead[f]) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                int a = face[f * 3 + k], b = face[f * 3 + (k + 1) % 3];
                long key = Math.min(a, b) * 4294967296L + Math.max(a, b);
                int[] e = edges.get(key);
                if (e == null) {
                    edges.put(key, new int[] {f, -1, 1});
                } else {
                    e[2]++;
                    if (e[1] < 0) {
                        e[1] = f;
                    }
                }
            }
        }
        for (var en : edges.entrySet()) {
            int[] e = en.getValue();
            int a = (int) (en.getKey() / 4294967296L), b = (int) (en.getKey() % 4294967296L);
            boolean open = e[2] == 1, border = e[2] >= 2 && e[1] >= 0 && groups[e[0]] != groups[e[1]];
            if (!open && !border) {
                continue;
            }
            int f = e[0];
            double[] pl = plane(face[f * 3], face[f * 3 + 1], face[f * 3 + 2]);
            if (pl == null) {
                continue;
            }
            double ex = pos[b * 3] - pos[a * 3], ey = pos[b * 3 + 1] - pos[a * 3 + 1], ez = pos[b * 3 + 2] - pos[a * 3 + 2];
            double nx = ey * pl[2] - ez * pl[1], ny = ez * pl[0] - ex * pl[2], nz = ex * pl[1] - ey * pl[0];
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-12) {
                continue;
            }
            nx /= len;
            ny /= len;
            nz /= len;
            double d = -(nx * pos[a * 3] + ny * pos[a * 3 + 1] + nz * pos[a * 3 + 2]);
            double w = (ex * ex + ey * ey + ez * ez) * (open ? 40 : 20);
            double[] cp = {nx, ny, nz, d};
            add(quad[a], cp, w);
            add(quad[b], cp, w);
        }
        PriorityQueue<double[]> heap = new PriorityQueue<>((x, y) -> Double.compare(x[0], y[0]));
        for (long key : edges.keySet()) {
            int a = (int) (key / 4294967296L), b = (int) (key % 4294967296L);
            push(heap, a, b);
        }
        double[] np = new double[3];
        while (live > target && !heap.isEmpty()) {
            double[] e = heap.poll();
            int u = (int) e[1], v = (int) e[2];
            if (stamp[u] != (int) e[3] || stamp[v] != (int) e[4] || vfaces[u] == null || vfaces[v] == null) {
                continue;
            }
            np[0] = e[5];
            np[1] = e[6];
            np[2] = e[7];
            if (flips(u, v, np) || flips(v, u, np)) {
                continue;
            }
            // collapse v into u at np
            pos[u * 3] = np[0];
            pos[u * 3 + 1] = np[1];
            pos[u * 3 + 2] = np[2];
            for (int k = 0; k < 10; k++) {
                quad[u][k] += quad[v][k];
            }
            for (int i = 0; i < vcount[v]; i++) {
                int f = vfaces[v][i];
                if (dead[f]) {
                    continue;
                }
                boolean hasU = face[f * 3] == u || face[f * 3 + 1] == u || face[f * 3 + 2] == u;
                if (hasU) {
                    dead[f] = true;
                    live--;
                } else {
                    for (int k = 0; k < 3; k++) {
                        if (face[f * 3 + k] == v) {
                            face[f * 3 + k] = u;
                        }
                    }
                    attach(u, f);
                }
            }
            vfaces[v] = null;
            stamp[u]++;
            // tidy u's list and requeue its edges
            int m = 0;
            for (int i = 0; i < vcount[u]; i++) {
                int f = vfaces[u][i];
                if (!dead[f]) {
                    vfaces[u][m++] = f;
                }
            }
            vcount[u] = m;
            java.util.HashSet<Integer> ring = new java.util.HashSet<>();
            for (int i = 0; i < m; i++) {
                int f = vfaces[u][i];
                for (int k = 0; k < 3; k++) {
                    int w = face[f * 3 + k];
                    if (w != u) {
                        ring.add(w);
                    }
                }
            }
            for (int w : ring) {
                push(heap, u, w);
            }
        }
        ByteBuffer o = le(live * 36), s = le(live * 4);
        for (int f = 0; f < n; f++) {
            if (dead[f]) {
                continue;
            }
            for (int k = 0; k < 3; k++) {
                int v = face[f * 3 + k];
                o.putFloat((float) pos[v * 3]).putFloat((float) pos[v * 3 + 1]).putFloat((float) pos[v * 3 + 2]);
            }
            s.putInt(f);
        }
        write(out, o);
        write(outSrc, s);
        System.out.println("decimated " + n + " -> " + live + " triangles (" + nv + " welded vertices)");
    }

    static void attach(int v, int f) {
        if (vcount[v] == vfaces[v].length) {
            vfaces[v] = Arrays.copyOf(vfaces[v], vfaces[v].length * 2);
        }
        vfaces[v][vcount[v]++] = f;
    }

    /** Unit normal, offset and area of a face's plane, or null if it has none. */
    static double[] plane(int a, int b, int c) {
        double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
        double vx = pos[c * 3] - pos[a * 3], vy = pos[c * 3 + 1] - pos[a * 3 + 1], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len < 1e-14) {
            return null;
        }
        nx /= len;
        ny /= len;
        nz /= len;
        return new double[] {nx, ny, nz, -(nx * pos[a * 3] + ny * pos[a * 3 + 1] + nz * pos[a * 3 + 2]), len / 2};
    }

    static void add(double[] q, double[] pl, double w) {
        double a = pl[0], b = pl[1], c = pl[2], d = pl[3];
        q[0] += w * a * a;
        q[1] += w * a * b;
        q[2] += w * a * c;
        q[3] += w * a * d;
        q[4] += w * b * b;
        q[5] += w * b * c;
        q[6] += w * b * d;
        q[7] += w * c * c;
        q[8] += w * c * d;
        q[9] += w * d * d;
    }

    static double err(double[] q, double x, double y, double z) {
        return q[0] * x * x + 2 * q[1] * x * y + 2 * q[2] * x * z + 2 * q[3] * x + q[4] * y * y + 2 * q[5] * y * z
                + 2 * q[6] * y + q[7] * z * z + 2 * q[8] * z + q[9];
    }

    static void push(PriorityQueue<double[]> heap, int a, int b) {
        double[] q = new double[10];
        for (int k = 0; k < 10; k++) {
            q[k] = quad[a][k] + quad[b][k];
        }
        double bx = 0, by = 0, bz = 0, best = Double.MAX_VALUE;
        // the optimal point if the quadric pins one down, else the better end or the middle
        double a00 = q[0], a01 = q[1], a02 = q[2], a11 = q[4], a12 = q[5], a22 = q[7];
        double det = a00 * (a11 * a22 - a12 * a12) - a01 * (a01 * a22 - a12 * a02) + a02 * (a01 * a12 - a11 * a02);
        double scale = Math.abs(a00) + Math.abs(a11) + Math.abs(a22);
        if (Math.abs(det) > 1e-9 * scale * scale * scale && scale > 0) {
            double r0 = -q[3], r1 = -q[6], r2 = -q[8];
            double x = (r0 * (a11 * a22 - a12 * a12) - a01 * (r1 * a22 - a12 * r2) + a02 * (r1 * a12 - a11 * r2)) / det;
            double y = (a00 * (r1 * a22 - a12 * r2) - r0 * (a01 * a22 - a12 * a02) + a02 * (a01 * r2 - r1 * a02)) / det;
            double z = (a00 * (a11 * r2 - r1 * a12) - a01 * (a01 * r2 - r1 * a02) + r0 * (a01 * a12 - a11 * a02)) / det;
            // keep it near the edge: a far-flung optimum means a badly shaped quadric
            double mx = (pos[a * 3] + pos[b * 3]) / 2, my = (pos[a * 3 + 1] + pos[b * 3 + 1]) / 2, mz = (pos[a * 3 + 2] + pos[b * 3 + 2]) / 2;
            double ex = pos[a * 3] - pos[b * 3], ey = pos[a * 3 + 1] - pos[b * 3 + 1], ez = pos[a * 3 + 2] - pos[b * 3 + 2];
            double el = ex * ex + ey * ey + ez * ez;
            if ((x - mx) * (x - mx) + (y - my) * (y - my) + (z - mz) * (z - mz) < el * 1.0) {
                best = err(q, x, y, z);
                bx = x;
                by = y;
                bz = z;
            }
        }
        for (int k = 0; k < 3; k++) {
            double x, y, z;
            if (k == 2) {
                x = (pos[a * 3] + pos[b * 3]) / 2;
                y = (pos[a * 3 + 1] + pos[b * 3 + 1]) / 2;
                z = (pos[a * 3 + 2] + pos[b * 3 + 2]) / 2;
            } else {
                int v = k == 0 ? a : b;
                x = pos[v * 3];
                y = pos[v * 3 + 1];
                z = pos[v * 3 + 2];
            }
            double e = err(q, x, y, z);
            if (e < best) {
                best = e;
                bx = x;
                by = y;
                bz = z;
            }
        }
        heap.add(new double[] {Math.max(0, best), a, b, stamp[a], stamp[b], bx, by, bz});
    }

    /** Whether moving v (about to merge with other) to np turns any of its other faces over. */
    static boolean flips(int v, int other, double[] np) {
        for (int i = 0; i < vcount[v]; i++) {
            int f = vfaces[v][i];
            if (dead[f]) {
                continue;
            }
            int a = face[f * 3], b = face[f * 3 + 1], c = face[f * 3 + 2];
            if (a == other || b == other || c == other) {
                continue;
            }
            double[] before = normal(f, -1, null);
            double[] after = normal(f, v, np);
            if (before == null) {
                continue;
            }
            if (after == null || before[0] * after[0] + before[1] * after[1] + before[2] * after[2] < 0.3) {
                return true;
            }
        }
        return false;
    }

    static double[] normal(int f, int moved, double[] np) {
        double[][] c = new double[3][];
        for (int k = 0; k < 3; k++) {
            int v = face[f * 3 + k];
            c[k] = v == moved ? np : new double[] {pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2]};
        }
        double ux = c[1][0] - c[0][0], uy = c[1][1] - c[0][1], uz = c[1][2] - c[0][2];
        double vx = c[2][0] - c[0][0], vy = c[2][1] - c[0][1], vz = c[2][2] - c[0][2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
        return len < 1e-14 ? null : new double[] {nx / len, ny / len, nz / len};
    }

    // ------------------------------------------------------------------ nearest

    static void nearest(float[] tris, int[] groups, float[] queries, String out) throws IOException {
        int n = tris.length / 9, nq = queries.length / 7;
        final double cell = 0.12, reach = 0.2;
        float[] lo = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] hi = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < n * 3; i++) {
            for (int k = 0; k < 3; k++) {
                lo[k] = Math.min(lo[k], tris[i * 3 + k]);
                hi[k] = Math.max(hi[k], tris[i * 3 + k]);
            }
        }
        int gx = (int) ((hi[0] - lo[0]) / cell) + 2, gy = (int) ((hi[1] - lo[1]) / cell) + 2, gz = (int) ((hi[2] - lo[2]) / cell) + 2;
        int[] counts = new int[gx * gy * gz + 1];
        int[][] boxes = new int[n][];
        for (int t = 0; t < n; t++) {
            int[] b = box(tris, t, lo, cell, reach, gx, gy, gz);
            boxes[t] = b;
            for (int x = b[0]; x <= b[3]; x++) {
                for (int y = b[1]; y <= b[4]; y++) {
                    for (int z = b[2]; z <= b[5]; z++) {
                        counts[(x * gy + y) * gz + z + 1]++;
                    }
                }
            }
        }
        for (int i = 1; i < counts.length; i++) {
            counts[i] += counts[i - 1];
        }
        int[] fill = Arrays.copyOf(counts, counts.length);
        int[] list = new int[counts[counts.length - 1]];
        for (int t = 0; t < n; t++) {
            int[] b = boxes[t];
            for (int x = b[0]; x <= b[3]; x++) {
                for (int y = b[1]; y <= b[4]; y++) {
                    for (int z = b[2]; z <= b[5]; z++) {
                        list[fill[(x * gy + y) * gz + z]++] = t;
                    }
                }
            }
        }
        ByteBuffer o = le(nq * 16);
        double[] best = new double[4], cp = new double[3];
        for (int q = 0; q < nq; q++) {
            double px = queries[q * 7], py = queries[q * 7 + 1], pz = queries[q * 7 + 2];
            double qx = queries[q * 7 + 3], qy = queries[q * 7 + 4], qz = queries[q * 7 + 5];
            int g = (int) queries[q * 7 + 6];
            int cx = (int) ((px - lo[0]) / cell), cy = (int) ((py - lo[1]) / cell), cz = (int) ((pz - lo[2]) / cell);
            int bt = -1;
            double bd = Double.MAX_VALUE, b0 = 0, b1 = 0;
            if (cx >= 0 && cy >= 0 && cz >= 0 && cx < gx && cy < gy && cz < gz) {
                int c = (cx * gy + cy) * gz + cz;
                for (int i = counts[c]; i < counts[c + 1]; i++) {
                    int t = list[i];
                    double[] bc = closest(tris, t, px, py, pz, cp);
                    double dx = cp[0] - px, dy = cp[1] - py, dz = cp[2] - pz;
                    double d = dx * dx + dy * dy + dz * dz;
                    double[] tn = triNormal(tris, t);
                    if (groups[t] != g) {
                        d += 0.01;
                    }
                    if (tn[0] * qx + tn[1] * qy + tn[2] * qz < 0.2) {
                        d += 0.02;
                    }
                    if (d < bd) {
                        bd = d;
                        bt = t;
                        b0 = bc[0];
                        b1 = bc[1];
                    }
                }
            }
            o.putInt(bt).putFloat((float) b0).putFloat((float) b1).putFloat((float) (1 - b0 - b1));
        }
        write(out, o);
        System.out.println("matched " + nq + " points to " + n + " triangles");
    }

    static int[] box(float[] tris, int t, float[] lo, double cell, double reach, int gx, int gy, int gz) {
        double[] mn = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE}, mx = {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int k = 0; k < 3; k++) {
            for (int a = 0; a < 3; a++) {
                double v = tris[t * 9 + k * 3 + a];
                mn[a] = Math.min(mn[a], v);
                mx[a] = Math.max(mx[a], v);
            }
        }
        int[] g = {gx, gy, gz};
        int[] b = new int[6];
        for (int a = 0; a < 3; a++) {
            b[a] = Math.max(0, (int) ((mn[a] - reach - lo[a]) / cell));
            b[a + 3] = Math.min(g[a] - 1, (int) ((mx[a] + reach - lo[a]) / cell));
        }
        return b;
    }

    static double[] triNormal(float[] T, int t) {
        double ux = T[t * 9 + 3] - T[t * 9], uy = T[t * 9 + 4] - T[t * 9 + 1], uz = T[t * 9 + 5] - T[t * 9 + 2];
        double vx = T[t * 9 + 6] - T[t * 9], vy = T[t * 9 + 7] - T[t * 9 + 1], vz = T[t * 9 + 8] - T[t * 9 + 2];
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        double len = Math.max(1e-14, Math.sqrt(nx * nx + ny * ny + nz * nz));
        return new double[] {nx / len, ny / len, nz / len};
    }

    /** Closest point on triangle t to p (Ericson): fills out, returns its barycentrics of corners 0 and 1. */
    static double[] closest(float[] T, int t, double px, double py, double pz, double[] out) {
        double ax = T[t * 9], ay = T[t * 9 + 1], az = T[t * 9 + 2];
        double bx = T[t * 9 + 3], by = T[t * 9 + 4], bz = T[t * 9 + 5];
        double cx = T[t * 9 + 6], cy = T[t * 9 + 7], cz = T[t * 9 + 8];
        double abx = bx - ax, aby = by - ay, abz = bz - az, acx = cx - ax, acy = cy - ay, acz = cz - az;
        double apx = px - ax, apy = py - ay, apz = pz - az;
        double d1 = abx * apx + aby * apy + abz * apz, d2 = acx * apx + acy * apy + acz * apz;
        double u, v;
        if (d1 <= 0 && d2 <= 0) {
            u = 0;
            v = 0;
        } else {
            double bpx = px - bx, bpy = py - by, bpz = pz - bz;
            double d3 = abx * bpx + aby * bpy + abz * bpz, d4 = acx * bpx + acy * bpy + acz * bpz;
            double cpx = px - cx, cpy = py - cy, cpz = pz - cz;
            double d5 = abx * cpx + aby * cpy + abz * cpz, d6 = acx * cpx + acy * cpy + acz * cpz;
            double vc = d1 * d4 - d3 * d2, vb = d5 * d2 - d1 * d6, va = d3 * d6 - d5 * d4;
            if (d3 >= 0 && d4 <= d3) {
                u = 1;
                v = 0;
            } else if (vc <= 0 && d1 >= 0 && d3 <= 0) {
                u = d1 / (d1 - d3);
                v = 0;
            } else if (d6 >= 0 && d5 <= d6) {
                u = 0;
                v = 1;
            } else if (vb <= 0 && d2 >= 0 && d6 <= 0) {
                u = 0;
                v = d2 / (d2 - d6);
            } else if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
                double w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
                u = 1 - w;
                v = w;
            } else {
                double den = 1 / (va + vb + vc);
                u = vb * den;
                v = vc * den;
            }
        }
        out[0] = ax + abx * u + acx * v;
        out[1] = ay + aby * u + acy * v;
        out[2] = az + abz * u + acz * v;
        return new double[] {1 - u - v, u};
    }

    // ------------------------------------------------------------------ files

    static float[] floats(String path) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(Path.of(path))).order(ByteOrder.LITTLE_ENDIAN);
        float[] out = new float[b.remaining() / 4];
        b.asFloatBuffer().get(out);
        return out;
    }

    static int[] ints(String path) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(Path.of(path))).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[b.remaining() / 4];
        b.asIntBuffer().get(out);
        return out;
    }

    static ByteBuffer le(int bytes) {
        return ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    static void write(String path, ByteBuffer b) throws IOException {
        b.flip();
        try (FileChannel ch = FileChannel.open(Path.of(path), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (b.hasRemaining()) {
                ch.write(b);
            }
        }
    }
}
