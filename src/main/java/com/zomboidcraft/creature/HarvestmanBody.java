package com.zomboidcraft.creature;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Harvestman's shape, as the server and every client share it: where its legs hang from, how long they are, the
 * knee rule that bends each one (the approved preview's), and where a shot strikes it: the armoured hull, the red core
 * under its belly, or a leg. All in blocks. The model's own copy of the leg sizes (in harvestman.zhm) matches these.
 */
public final class HarvestmanBody {
    /** One leg pair (the left; the right mirrors it in x): hip in the body's frame, thigh, knee to point (straight). */
    public record Leg(double hipX, double hipY, double hipZ, double a, double lc, double restA, double restR, double arc) {
    }

    public static final Leg[] LEGS = {
            new Leg(0.46, -0.08, 0.06, 1.28, 3.6086, 0.74, 1.75, 3.75),     // front
            new Leg(0.55, 0.12, -0.45, 1.28, 3.7048, 2.36, 1.85, 3.85)};    // rear

    /** Leg index: 0 left front, 1 left rear, 2 right front, 3 right rear. */
    public static Leg leg(int li) {
        return LEGS[li & 1];
    }

    public static double side(int li) {
        return li < 2 ? -1 : 1;
    }

    /** The body's frame: origin and its three axes (x to its right, y off the surface, z forward). */
    public record Frame(Vec3 o, Vec3 x, Vec3 y, Vec3 z) {
        public Vec3 at(double px, double py, double pz) {
            return o.add(x.scale(px)).add(y.scale(py)).add(z.scale(pz));
        }

        public Vec3 local(Vec3 p) {
            Vec3 d = p.subtract(o);
            return new Vec3(d.dot(x), d.dot(y), d.dot(z));
        }

        public Vec3 hip(int li) {
            Leg L = leg(li);
            return at(side(li) * L.hipX(), L.hipY(), L.hipZ());
        }
    }

    /** A solved leg: hip, knee, the point, and the bend's normal (the lower leg bows toward n x along it). */
    public record Bent(Vec3 hip, Vec3 knee, Vec3 tip, Vec3 n) {
    }

    private static Vec3 norm(Vec3 v) {
        double l = v.length();
        return l < 1e-9 ? v : v.scale(1 / l);
    }

    /**
     * One leg from its hip to where its foot is planted (foot normal fn): a foot closer than the folded leg reaches
     * slides out along its surface, and the knee bends toward a pole up and out over the foot, so it leans out from the
     * hull instead of in across it.
     */
    public static Bent bend(Frame body, Vec3 up, int li, Vec3 foot, Vec3 fn) {
        Leg L = leg(li);
        Vec3 H = body.hip(li), F = foot;
        double dmin = L.lc() - L.a() + 0.2;
        if (F.distanceTo(H) < dmin) {
            double hh = H.subtract(F).dot(fn);
            Vec3 Hp = H.subtract(fn.scale(hh));
            double r = Math.sqrt(Math.max(0, dmin * dmin - hh * hh));
            Vec3 dr = F.subtract(Hp);
            if (dr.length() < 1e-3) {
                Vec3 ho = H.subtract(body.o());
                dr = ho.subtract(fn.scale(ho.dot(fn)));
            }
            F = Hp.add(norm(dr).scale(r));
        }
        Vec3 ho = H.subtract(body.o()), fo = F.subtract(body.o()), fh = fo.subtract(up.scale(fo.dot(up)));
        Vec3 out = fh.length() > 0.05 ? norm(fh) : norm(ho.subtract(up.scale(ho.dot(up))));
        Vec3 dn = norm(F.subtract(H)), p0 = norm(up.scale(0.4).add(out));
        Vec3 pole = p0.subtract(dn.scale(p0.dot(dn))).add(up.subtract(dn.scale(up.dot(dn))).scale(0.6));
        double a = L.a(), b = L.lc();
        double dl = Mth.clamp(F.distanceTo(H), Math.abs(a - b) + 0.02, a + b - 0.02);
        double cb = Mth.clamp((a * a + dl * dl - b * b) / (2 * a * dl), -1, 1), sb = Math.sqrt(1 - cb * cb);
        Vec3 bp = pole.subtract(dn.scale(pole.dot(dn)));
        bp = bp.length() > 1e-4 ? norm(bp) : norm(dn.cross(new Vec3(0.3, 0.9, 0.2)));
        Vec3 K = H.add(dn.scale(a * cb)).add(bp.scale(a * sb));
        Vec3 P = K.add(norm(H.add(dn.scale(dl)).subtract(K)).scale(b));
        return new Bent(H, K, P, norm(dn.cross(bp)));
    }

    // ------------------------------------------------------------------------------------------------ the lower leg's bow

    /** The lower leg's centre line in its own frame (x along the straight line to the point, bowing toward +y). */
    private static final double[][][] ARCS = new double[2][][];
    private static final double CURVE = 0.35, KINK = 0.22, KINK_W = 0.08;
    public static final int ARC_POINTS = 7;

    static {
        for (int i = 0; i < 2; i++) {
            ARCS[i] = arc(LEGS[i].arc());
        }
    }

    private static double smooth(double x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static double[][] arc(double S) {
        int n = 480;
        double ds = S / n;
        double[] px = new double[n + 1], py = new double[n + 1];
        for (int k = 1; k <= n; k++) {
            double s = (k - 0.5) * ds;
            double th = CURVE * s / S + KINK * (smooth((s - S / 3 + KINK_W) / (2 * KINK_W)) + smooth((s - 2 * S / 3 + KINK_W) / (2 * KINK_W)));
            px[k] = px[k - 1] + Math.cos(th) * ds;
            py[k] = py[k - 1] - Math.sin(th) * ds;
        }
        double psi = -Math.atan2(py[n], px[n]), c = Math.cos(psi), s = Math.sin(psi);
        double[][] out = new double[ARC_POINTS][];
        for (int j = 0; j < ARC_POINTS; j++) {
            int k = j * n / (ARC_POINTS - 1);
            out[j] = new double[] {px[k] * c - py[k] * s, px[k] * s + py[k] * c};
        }
        return out;
    }

    /** Points along a solved leg's lower part, knee to point, following its bow. */
    public static Vec3[] lowerLine(Bent b, int li) {
        Vec3 sx = norm(b.tip().subtract(b.knee())), sy = b.n().cross(sx);
        double[][] arc = ARCS[li & 1];
        Vec3[] out = new Vec3[ARC_POINTS];
        for (int j = 0; j < ARC_POINTS; j++) {
            out[j] = b.knee().add(sx.scale(arc[j][0])).add(sy.scale(arc[j][1]));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------ being shot

    public static final int HULL = 0, CORE = 1, LEG = 2;

    /** Where a shot struck: how far along it, what (HULL, CORE or LEG) and which leg. */
    public record Strike(double distance, int zone, int leg, Vec3 at) {
    }

    /** The hull, as an ellipsoid round the dart in the body's frame. */
    private static final double HX = 0.62, HY = 0.42, HZ = 1.3, HCY = 0.05, HCZ = -0.3;

    /**
     * Where a shot from eye along dir (unit) first strikes it, within range; legs gone are passed in lost (bits by leg)
     * and bent holds the legs where they are now (null for one that's gone).
     */
    public static Strike strike(Frame body, Bent[] bent, int lost, Vec3 eye, Vec3 dir, double range) {
        Strike best = null;
        // the hull: in its frame, scaled to a unit sphere
        Vec3 o = body.local(eye), d = new Vec3(dir.dot(body.x()), dir.dot(body.y()), dir.dot(body.z()));
        Vec3 os = new Vec3(o.x / HX, (o.y - HCY) / HY, (o.z - HCZ) / HZ), ds = new Vec3(d.x / HX, d.y / HY, d.z / HZ);
        double qa = ds.dot(ds), qb = 2 * os.dot(ds), qc = os.dot(os) - 1, disc = qb * qb - 4 * qa * qc;
        if (disc >= 0) {
            double t = (-qb - Math.sqrt(disc)) / (2 * qa);
            if (t < 0) {
                t = 0;
            }
            if (t <= range && (-qb + Math.sqrt(disc)) / (2 * qa) >= 0) {
                Vec3 at = eye.add(dir.scale(t));
                Vec3 l = body.local(at);
                // the red core shows between the plates under its belly
                int zone = l.y < HCY - 0.12 && Math.abs(l.x) < 0.4 ? CORE : HULL;
                best = new Strike(t, zone, -1, at);
            }
        }
        for (int li = 0; li < 4; li++) {
            Bent b = bent[li];
            if (b == null) {
                continue;
            }
            double t = capsule(eye, dir, b.hip(), b.knee(), 0.14);
            boolean lostLower = (lost >> li & 1) != 0;
            if (!lostLower) {
                Vec3[] line = lowerLine(b, li);
                for (int j = 0; j + 1 < line.length; j++) {
                    double r = 0.12 - 0.07 * j / (line.length - 2);
                    t = Math.min(t, capsule(eye, dir, line[j], line[j + 1], r));
                }
            }
            if (t <= range && (best == null || t < best.distance())) {
                best = new Strike(t, LEG, li, eye.add(dir.scale(t)));
            }
        }
        return best;
    }

    /** How far along a ray it first comes within r of segment a-b, or infinity. */
    private static double capsule(Vec3 eye, Vec3 dir, Vec3 a, Vec3 b, double r) {
        Vec3 ab = b.subtract(a), ao = eye.subtract(a);
        double abab = ab.dot(ab), abd = ab.dot(dir), abao = ab.dot(ao), dao = dir.dot(ao);
        double denom = abab - abd * abd;
        // closest approach between the two lines, clamped to the segment
        double t = denom > 1e-9 ? (abd * abao - abab * dao) / denom : -dao;
        if (t < 0) {
            t = 0;
        }
        double s = Mth.clamp((abao + abd * t) / Math.max(abab, 1e-9), 0, 1);
        Vec3 c = a.add(ab.scale(s));
        // refine: the nearest ray point to that segment point
        t = Math.max(0, c.subtract(eye).dot(dir));
        Vec3 p = eye.add(dir.scale(t));
        double gap = p.distanceTo(c);
        if (gap > r) {
            return Double.POSITIVE_INFINITY;
        }
        // back off along the ray to where it enters the tube
        return Math.max(0, t - Math.sqrt(r * r - gap * gap));
    }

    private HarvestmanBody() {
    }
}
