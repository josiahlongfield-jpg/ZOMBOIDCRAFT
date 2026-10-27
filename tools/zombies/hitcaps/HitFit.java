import com.zomboidcraft.client.zombie.body.*;
import java.util.*;

/**
 * Measures how well hit capsules fit the drawn zombie body: renders an id buffer of the real mesh (every quad, kit
 * included) from many directions, over the whole gait / attack cycle, and compares it with what the capsules say.
 * Frame: blocks, x to the zombie's left, y up from its feet, z ahead (as Dismember works).
 */
public class HitFit {
    static final float K = 32f / 35.2f;

    /** Each bone's matrix (column major, 16 floats a bone) as ZBodyLayer.matrices builds it. */
    static float[] matrices(ZBodyMesh mesh, ZMove.Pose p, boolean legless) {
        double[][] world = new double[ZMove.BONES][];
        float[] mats = new float[ZMove.BONES * 16];
        for (int b = 0; b < ZMove.BONES; b++) {
            float rx = p.rot[b * 3], ry = p.rot[b * 3 + 1], rz = p.rot[b * 3 + 2];
            double[] m;
            if (b == ZMove.HIPS) {
                m = mul(translate(0, (24f + 0.4f * K) / 16f, 0), scale(K / 16f, -K / 16f, -K / 16f));
                m = mul(m, translate(p.hx, p.hy, p.hz));
                m = p.lying ? mul(m, rotXYZ(rx, ry, rz)) : mul(mul(mul(m, rot(1, ry)), rot(0, rx)), rot(2, rz));
            } else {
                m = mul(world[mesh.parent[b]], translate(mesh.rest[b * 3], mesh.rest[b * 3 + 1], mesh.rest[b * 3 + 2]));
                m = mul(m, rotXYZ(rx, ry, rz));
                if (legless && (b == ZMove.HIP_R || b == ZMove.HIP_L)) m = mul(m, scale(1, 0.34, 1));
            }
            world[b] = m;
            for (int i = 0; i < 16; i++) mats[b * 16 + i] = (float) m[i];
        }
        return mats;
    }

    /** A point in a bone's space to the zombie's frame (the walker model's y down and 180 turn undone). */
    static double[] tf(float[] a, int o, float x, float y, float z) {
        double px = a[o] * x + a[o + 4] * y + a[o + 8] * z + a[o + 12];
        double py = a[o + 1] * x + a[o + 5] * y + a[o + 9] * z + a[o + 13];
        double pz = a[o + 2] * x + a[o + 6] * y + a[o + 10] * z + a[o + 14];
        return new double[] {px, 1.501 - py, -pz};
    }

    // column-major 4x4 helpers
    static double[] mul(double[] a, double[] b) {
        double[] o = new double[16];
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) {
            double s = 0;
            for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
            o[c * 4 + r] = s;
        }
        return o;
    }
    static double[] translate(double x, double y, double z) { return new double[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1}; }
    static double[] scale(double x, double y, double z) { return new double[] {x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1}; }
    static double[] rot(int axis, double a) {
        double c = Math.cos(a), s = Math.sin(a);
        return switch (axis) {
            case 0 -> new double[] {1, 0, 0, 0, 0, c, s, 0, 0, -s, c, 0, 0, 0, 0, 1};
            case 1 -> new double[] {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, 0, 0, 0, 1};
            default -> new double[] {c, s, 0, 0, -s, c, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        };
    }
    static double[] rotXYZ(double x, double y, double z) { return mul(mul(rot(0, x), rot(1, y)), rot(2, z)); }

    // bone -> Dismember part (neck -> -2: head or torso both count)
    static final int[] PART_OF_BONE = {3, 1, -2, 0, 4, 5, 5, 6, 7, 7, 8, 9, 9, 10, 11, 11};

    static int region(int part) {
        return part == -2 ? -2 : part == 0 ? 0 : part <= 3 ? 1 : part <= 5 ? 2 : part <= 7 ? 3 : part <= 9 ? 4 : 5;
    }

    /** The capsule caught the right part: the very limb part (upper or lower), any of the torso, head or torso at the neck. */
    static boolean agree(int meshPart, int capPart) {
        if (meshPart == -2) return capPart >= 0 && capPart <= 3;
        if (meshPart >= 1 && meshPart <= 3) return capPart >= 1 && capPart <= 3;
        return meshPart == capPart;
    }

    /** Triangles of one drawn pose, in the zombie frame (blocks: x left, y up, z ahead). */
    static final class Body {
        final List<float[]> tris = new ArrayList<>();
        final List<Integer> part = new ArrayList<>();
        // per-part vertex clouds, for fitting
        final List<List<double[]>> verts = new ArrayList<>();
        Body() { for (int i = 0; i < 12; i++) verts.add(new ArrayList<>()); }
    }

    static int bonesOf(int cut) {
        return switch (cut) {
            case 1 -> 1 << 5 | 1 << 6;
            case 2 -> 1 << 8 | 1 << 9;
            case 4 -> 1 << 4 | 1 << 5 | 1 << 6;
            case 8 -> 1 << 7 | 1 << 8 | 1 << 9;
            case 16 -> 1 << 11 | 1 << 12;
            case 32 -> 1 << 14 | 1 << 15;
            case 64 -> 1 << 10 | 1 << 11 | 1 << 12;
            default -> 1 << 13 | 1 << 14 | 1 << 15;
        };
    }

    static Body body(ZBodyMesh mesh, ZMove.Pose p, int cuts, boolean legless) {
        float[] a = matrices(mesh, p, legless);
        int hidden = 0;
        for (int c = 1; c <= 128; c <<= 1) if ((cuts & c) != 0) hidden |= bonesOf(c);
        if (legless) hidden |= 1 << 11 | 1 << 12 | 1 << 14 | 1 << 15;
        Body b = new Body();
        for (int q = 0; q < mesh.count; q++) {
            int f = mesh.flags[q];
            if ((hidden >> mesh.bone[q] & 1) != 0 || ((f & ZBodyMesh.STUMP) != 0 && !legless)) continue;
            int o = mesh.bone[q] * 16;
            double[][] c = new double[4][];
            for (int k = 0; k < 4; k++) c[k] = tf(a, o, mesh.pos[q * 12 + k * 3], mesh.pos[q * 12 + k * 3 + 1], mesh.pos[q * 12 + k * 3 + 2]);
            int part = PART_OF_BONE[mesh.bone[q]];
            for (int[] t : new int[][] {{0, 1, 2}, {0, 2, 3}}) {
                float[] tri = new float[9];
                for (int k = 0; k < 3; k++) for (int d = 0; d < 3; d++) tri[k * 3 + d] = (float) c[t[k]][d];
                b.tris.add(tri);
                b.part.add(part);
            }
            int vp = part == -2 ? 0 : part;    // the neck goes with the head for fitting
            // corners and the quad's middle, so big quads count for more than tiny ones
            for (int k = 0; k < 4; k++) b.verts.get(vp).add(c[k]);
            double[] mid = new double[3];
            for (int k = 0; k < 4; k++) for (int d = 0; d < 3; d++) mid[d] += c[k][d] / 4;
            b.verts.get(vp).add(mid);
        }
        return b;
    }

    /** One view's result: per pixel the part hit (or -1), over a grid of parallel rays. */
    static final double PIX = 0.02;

    static double[][] basis(double az, double el) {
        // d = direction the shot travels; from azimuth (0 = shooting the zombie in the face) and elevation (negative = down)
        double ca = Math.cos(az), sa = Math.sin(az), ce = Math.cos(el), se = Math.sin(el);
        double[] d = {sa * ce, se, -ca * ce};   // az 0: travels toward -z (into the zombie's face)
        double[] u = {ca, 0, sa};
        double[] v = {u[1] * d[2] - u[2] * d[1], u[2] * d[0] - u[0] * d[2], u[0] * d[1] - u[1] * d[0]};
        return new double[][] {d, u, v};
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double dot(float[] a, int o, double[] b) { return a[o] * b[0] + a[o + 1] * b[1] + a[o + 2] * b[2]; }

    static final int G = 160;   // grid size: 3.2 blocks across

    static int[] render(Body b, double[][] B, double[] centre) {
        double[] d = B[0], u = B[1], v = B[2];
        int[] id = new int[G * G];
        double[] z = new double[G * G];
        Arrays.fill(id, -1);
        Arrays.fill(z, Double.MAX_VALUE);
        double cu = dot(centre, u), cv = dot(centre, v);
        for (int t = 0; t < b.tris.size(); t++) {
            float[] tr = b.tris.get(t);
            double[] pu = new double[3], pv = new double[3], pz = new double[3];
            for (int k = 0; k < 3; k++) {
                pu[k] = (dot(tr, k * 3, u) - cu) / PIX + G / 2.0;
                pv[k] = (dot(tr, k * 3, v) - cv) / PIX + G / 2.0;
                pz[k] = dot(tr, k * 3, d);
            }
            double area = (pu[1] - pu[0]) * (pv[2] - pv[0]) - (pu[2] - pu[0]) * (pv[1] - pv[0]);
            if (Math.abs(area) < 1e-9) continue;
            int x0 = Math.max(0, (int) Math.floor(Math.min(pu[0], Math.min(pu[1], pu[2])))), x1 = Math.min(G - 1, (int) Math.ceil(Math.max(pu[0], Math.max(pu[1], pu[2]))));
            int y0 = Math.max(0, (int) Math.floor(Math.min(pv[0], Math.min(pv[1], pv[2])))), y1 = Math.min(G - 1, (int) Math.ceil(Math.max(pv[0], Math.max(pv[1], pv[2]))));
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    double w0 = ((pu[1] - px) * (pv[2] - py) - (pu[2] - px) * (pv[1] - py)) / area;
                    double w1 = ((pu[2] - px) * (pv[0] - py) - (pu[0] - px) * (pv[2] - py)) / area;
                    double w2 = 1 - w0 - w1;
                    if (w0 < 0 || w1 < 0 || w2 < 0) continue;
                    double depth = w0 * pz[0] + w1 * pz[1] + w2 * pz[2];
                    int i = y * G + x;
                    if (depth < z[i]) {
                        z[i] = depth;
                        id[i] = b.part.get(t);
                    }
                }
            }
        }
        return id;
    }

    /** Capsule sets: each float[] is a 12-part table (7 floats each) like Dismember's; a hit is the nearest over all sets. */
    static int[] capsules(List<float[]> sets, double[][] B, double[] centre, int goneParts) {
        double[] d = B[0], u = B[1], v = B[2];
        int[] id = new int[G * G];
        for (int y = 0; y < G; y++) {
            for (int x = 0; x < G; x++) {
                double su = (x + 0.5 - G / 2.0) * PIX, sv = (y + 0.5 - G / 2.0) * PIX;
                double ox = centre[0] + su * u[0] + sv * v[0] - 4 * d[0];
                double oy = centre[1] + su * u[1] + sv * v[1] - 4 * d[1];
                double oz = centre[2] + su * u[2] + sv * v[2] - 4 * d[2];
                double best = Double.MAX_VALUE;
                int part = -1;
                for (float[] caps : sets) {
                    for (int k = 0; k < caps.length / 8; k++) {
                        int real = (int) caps[k * 8];
                        if ((goneParts >> real & 1) != 0) continue;
                        double t = enter(ox, oy, oz, d[0], d[1], d[2], caps, k * 8 + 1);
                        if (t >= 0 && t < best) { best = t; part = real; }
                    }
                }
                id[y * G + x] = part;
            }
        }
        return id;
    }

    static double enter(double px, double py, double pz, double dx, double dy, double dz, float[] c, int o) {
        double ax = c[o], ay = c[o + 1], az = c[o + 2];
        double ux = c[o + 3] - ax, uy = c[o + 4] - ay, uz = c[o + 5] - az, r = c[o + 6];
        double wx = px - ax, wy = py - ay, wz = pz - az;
        double a = dx * dx + dy * dy + dz * dz, b = dx * ux + dy * uy + dz * uz, uu = ux * ux + uy * uy + uz * uz;
        double dw = dx * wx + dy * wy + dz * wz, uw = ux * wx + uy * wy + uz * wz;
        double den = a * uu - b * b;
        double s = den > 1e-9 ? Math.clamp((a * uw - b * dw) / den, 0, 1) : 0;
        double t = (s * b - dw) / a;
        if (t < 0) {
            t = 0;
            s = uu > 1e-12 ? Math.clamp(uw / uu, 0, 1) : 0;
        }
        double cx = wx + t * dx - s * ux, cy = wy + t * dy - s * uy, cz = wz + t * dz - s * uz;
        double d2 = cx * cx + cy * cy + cz * cz;
        if (d2 > r * r) return -1;
        return Math.max(0, t - Math.sqrt((r * r - d2) / a));
    }

    /** Tallies over many views and samples. */
    static final class Score {
        long mesh, caught, right, extra;
        long[] regMesh = new long[6], regCaught = new long[6], regRight = new long[6];

        void add(int[] m, int[] c) {
            for (int i = 0; i < m.length; i++) {
                if (m[i] != -1) {
                    mesh++;
                    int r = m[i] == -2 ? 1 : region(m[i]);
                    regMesh[r]++;
                    if (c[i] >= 0) {
                        caught++;
                        regCaught[r]++;
                        if (agree(m[i], c[i])) { right++; regRight[r]++; }
                    }
                } else if (c[i] >= 0) {
                    extra++;
                }
            }
        }

        String brief() {
            return String.format("%.1f%%, %.1f%%, %.1f%%", 100.0 * caught / mesh, 100.0 * right / Math.max(1, caught), 100.0 * extra / mesh);
        }

        String line() {
            StringBuilder sb = new StringBuilder(String.format("caught %5.1f%%  right part %5.1f%%  extra %5.1f%%  |", 100.0 * caught / mesh, 100.0 * right / Math.max(1, caught), 100.0 * extra / mesh));
            String[] n = {"head", "torso", "rarm", "larm", "rleg", "lleg"};
            for (int r = 0; r < 6; r++) {
                if (regMesh[r] == 0) continue;
                sb.append(String.format(" %s %3.0f/%3.0f", n[r], 100.0 * regCaught[r] / regMesh[r], 100.0 * regRight[r] / regMesh[r]));
            }
            return sb.toString();
        }
    }

    static final double[][] VIEWS;
    static {
        List<double[]> vs = new ArrayList<>();
        for (int a = 0; a < 8; a++) for (double el : new double[] {0, -15, -35}) vs.add(new double[] {Math.toRadians(a * 45), Math.toRadians(el)});
        VIEWS = vs.toArray(new double[0][]);
    }

    /** An old 12-part table (7 floats each) in the tagged form (part, then 7 floats). */
    static float[] tagged(float[] old) {
        float[] t = new float[96];
        for (int k = 0; k < 12; k++) {
            t[k * 8] = k;
            System.arraycopy(old, k * 7, t, k * 8 + 1, 7);
        }
        return t;
    }

    static double[] centre(Body b) {
        double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        for (float[] t : b.tris) for (int k = 0; k < 3; k++) for (int d = 0; d < 3; d++) { lo[d] = Math.min(lo[d], t[k * 3 + d]); hi[d] = Math.max(hi[d], t[k * 3 + d]); }
        return new double[] {(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2};
    }

    static Score score(List<Body> samples, List<float[]> sets, int goneParts) {
        Score s = new Score();
        for (Body b : samples) {
            double[] c = centre(b);
            for (double[] view : VIEWS) {
                double[][] B = basis(view[0], view[1]);
                s.add(render(b, B, c), capsules(sets, B, c, goneParts));
            }
        }
        return s;
    }
}
