package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;

/**
 * The Millbrook Interchange (Josiah, 2026-10-31: the highway places), the cloverleaf where I-495
 * crosses I-88 west of Grayhaven, after refs/roads/highway-interchange and overpass: I-88 at grade
 * across the place, I-495 up on its embankment and over it on a single-span bridge, its abutments
 * faced in panel wall.
 * <ul>
 * <li>a loop in each quadrant for the left turns, three-quarters round and climbing (or falling)
 * the eight between the two roads, chevrons round its outside, a lamp ring inside;</li>
 * <li>an outer ramp round each loop for the right turns, off the one road well before the crossing
 * and onto the other well past it;</li>
 * <li>over each approach a gantry naming the loop ahead, the green board at the foot of each outer
 * ramp, RAMP 25 at the loops' tops, the lamps, delineators, the call boxes;</li>
 * <li>the loops' middles left to grow: grass gone long, young pines and birch.</li>
 * </ul>
 * The westbound out of Grayhaven jammed solid the day it closed, back up the loop from the
 * southbound and onto the bridge; a pile-up across the northbound on the bridge burnt where it
 * stopped; the eastbound in stands empty under its QUARANTINE board.
 *
 * <p>Everything is laid out once in a quadrant's own frame (u along the road the loop leaves, v
 * along the one it joins, both from the crossing) and turned a quarter for each of the four.
 * The place is {@link #S} square, its gates on the two interstates.
 */
final class Interchange extends Site {
    static final int S = 360, C = S / 2;
    /** The loops' centres (L, L) out from the crossing, their radius; the outer ramps' ends out O; the right lanes' offset. */
    private static final double L = 40, R = 26, O = 165, LANE = 5.25;
    /** What each loop's gantry says (the road it joins), what each outer ramp's board says. */
    private static final String[] LOOP = {"i495n", "i88e", "i495s", "i88w"}, OUTER = {"i88e", "i495s", "i88w", "i495n"};

    private final Placer a;
    private Lanes ns;

    Interchange(Place p, BlockSink out) {
        super(p, out);
        this.a = new Placer(out, 0, 0, 0, 0);
    }

    @Override
    void build() {
        lawn();
        Lanes ew = new Lanes.Path(-2, C, 0).to(S + 1, C, 0).lanes(Lanes.Style.HIGHWAY, 7);
        ns = new Lanes.Path(C, -2, 0).to(C, C - 70, 8).to(C, C + 70, 8).to(C, S + 1, 0).lanes(Lanes.Style.HIGHWAY, 7).bridge(C - 12 + 2, C + 12 + 2);
        ns.railOver = 1.0;
        List<Lanes> all = new ArrayList<>(List.of(ew, ns));
        Lanes[] loops = new Lanes[4], outers = new Lanes[4];
        for (int k = 0; k < 4; k++) {
            loops[k] = loop(k);
            outers[k] = outer(k);
            all.add(loops[k]);
            all.add(outers[k]);
        }
        Lanes.paint(out, all, w, d, rng);
        for (int k = 0; k < 4; k++) {
            furnish(k, loops[k], outers[k]);
        }
        Hwy.lamps(out, ew, 10, S, 48, true, w, d);
        Hwy.lamps(out, ew, 34, S, 48, false, w, d);
        Hwy.lamps(out, ns, 10, S, 48, true, w, d);
        Hwy.lamps(out, ns, 34, S, 48, false, w, d);
        woods();
        story(loops);
    }

    // ------------------------------------------------------------------ the frame

    /** A quadrant's (u, v) in the place: turned k quarters clockwise round the crossing. */
    private static double[] g(int k, double u, double v) {
        double x = u, z = v;
        for (int i = 0; i < k; i++) {
            double t = x;
            x = -z;
            z = t;
        }
        return new double[] {C + x, C + z};
    }

    /** I-495's surface at z. */
    private double hNS(double z) {
        return ns.point(z + 2)[2];
    }

    /** The road a quadrant's loop leaves (I-88 in the even ones) at u along it; the one it joins at v. */
    private double yA(int k, double u) {
        return k % 2 == 0 ? 0 : hNS(g(k, u, 0)[1]);
    }

    private double yB(int k, double v) {
        return k % 2 == 0 ? hNS(g(k, 0, v)[1]) : 0;
    }

    private static int facing(int k, double du, double dv) {
        double[] o = g(k, 0, 0), t = g(k, du, dv);
        double dx = t[0] - o[0], dz = t[1] - o[1];
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
    }

    /** Off the leaving road's right lane past the crossing, round three-quarters, onto the joining road's before it. */
    private Lanes loop(int k) {
        double[] s0 = g(k, 16, LANE), s1 = g(k, 22, LANE), c1 = g(k, 30, LANE), c2 = g(k, L - 8, L - R), top = g(k, L, L - R);
        double[] cen = g(k, L, L), m1 = g(k, L - R, L - 10), m2 = g(k, LANE, 30), m3 = g(k, LANE, 20), end = g(k, LANE, 15);
        double ya = yA(k, 16), yb = yB(k, L);
        return new Lanes.Path(s0[0], s0[1], ya).to(s1[0], s1[1], yA(k, 22))
                .curve(c1[0], c1[1], c2[0], c2[1], top[0], top[1], ya + (yb - ya) * 0.06)
                .arc(cen[0], cen[1], R, 270 + 90 * k, 540 + 90 * k, yb)
                .curve(m1[0], m1[1], m2[0], m2[1], m3[0], m3[1], yB(k, 20))
                .to(end[0], end[1], yB(k, 15))
                .lanes(Lanes.Style.RAMP, 3.5);
    }

    /** Off the joining road's right lane far out, round the outside of the loop, onto the leaving road's far out. */
    private Lanes outer(int k) {
        double[] s0 = g(k, LANE, O), p1 = g(k, LANE, O - 40), p2 = g(k, 50, 100), mid = g(k, 75, 75);
        double[] q1 = g(k, 100, 50), q2 = g(k, O - 40, LANE), e = g(k, O, LANE);
        return new Lanes.Path(s0[0], s0[1], yB(k, O))
                .curve(p1[0], p1[1], p2[0], p2[1], mid[0], mid[1], 0)
                .curve(q1[0], q1[1], q2[0], q2[1], e[0], e[1], yA(k, O))
                .lanes(Lanes.Style.RAMP, 3.5);
    }

    // ------------------------------------------------------------------ the ground

    private void lawn() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double r = chance();
                boolean bare = Noise.hash(p.seed, x / 6, z / 6) % 13 == 0 && r < 0.5;
                out.set(x, 0, z, bare ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                if (r > 0.7) {
                    out.set(x, 1, z, r > 0.96 ? "weeds_tall" : r > 0.9 ? "weeds" : r > 0.82 ? "minecraft:tall_grass[half=lower]" : "minecraft:short_grass");
                    if (r > 0.82 && r <= 0.9) {
                        out.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                    }
                }
            }
        }
    }

    /** Where a tree can stand: on the grass, nothing over it, nothing near paved. */
    private boolean open(int x, int z) {
        if (x < 2 || z < 2 || x >= w - 2 || z >= d - 2) {
            return false;
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                String gnd = out.get(x + dx, 0, z + dz);
                if (gnd != null && !gnd.equals("minecraft:grass_block") && !gnd.equals("minecraft:coarse_dirt")) {
                    return false;
                }
            }
        }
        for (int y = 1; y <= 10; y++) {
            String b = out.get(x, y, z);
            if (b != null && !b.equals("air") && !b.startsWith("minecraft:short_grass") && !b.startsWith("minecraft:tall_grass") && !b.startsWith("weeds")) {
                return false;
            }
        }
        return true;
    }

    /** Thick in the loops' middles and the far corners, thin along the roads. */
    private void woods() {
        for (int k = 0; k < 4; k++) {
            for (int i = 0; i < 16; i++) {
                // young trees and pines in the loops, clear of the ramp's rail
                double an = rng.nextDouble() * Math.PI * 2, r = Math.sqrt(rng.nextDouble()) * (R - 15);
                double[] c = g(k, L + Math.cos(an) * r, L + Math.sin(an) * r);
                if (open((int) c[0], (int) c[1])) {
                    Trees.grow(Kit.canopy(a), chance() < 0.55 ? Trees.Kind.YOUNG : Trees.Kind.PINE, (int) c[0], 1, (int) c[1], rng);
                }
            }
            for (int i = 0; i < 40; i++) {
                double[] c = g(k, 20 + rng.nextDouble() * (C - 22), 20 + rng.nextDouble() * (C - 22));
                plant((int) c[0], (int) c[1], 0.25);
            }
        }
    }

    private void plant(int x, int z, double young) {
        if (!open(x, z)) {
            return;
        }
        double r = chance();
        Trees.Kind kind = r < young ? Trees.Kind.YOUNG : r < 0.75 ? Trees.Kind.PINE : r < 0.9 ? Trees.Kind.OAK : Trees.Kind.DEAD;
        Trees.grow(Kit.canopy(a), kind, x, 1, z, rng);
    }

    // ------------------------------------------------------------------ the signs and furniture

    private void furnish(int k, Lanes loop, Lanes outer) {
        // the gantry over the leaving road's approach, naming the loop
        double ya = yA(k, -95);
        int top = (int) Math.floor(ya);
        BlockSink up = Hwy.shifted(out, top);
        double[] p0 = g(k, -95, -10), p1 = g(k, -95, 10), sg = g(k, -96, 1);
        Hwy.gantry(up, (int) p0[0], (int) p0[1], (int) p1[0], (int) p1[1], 8);
        String key = LOOP[k].equals("i88e") && chance() < 0.75 ? "i88e_q" : LOOP[k];
        Hwy.sign(up, key, 5, 2, (int) sg[0], 6, (int) sg[1], Hwy.FACING[facing(k, -1, 0)], null, null);
        // the outer ramp's board at its foot, on its right verge
        double[] q = outer.point(26);
        int[] c = outer.beside(26, outer.half + 3);
        String okey = OUTER[k].equals("i88e") && chance() < 0.75 ? "i88e_q" : OUTER[k];
        Hwy.sign(Hwy.shifted(out, (int) Math.floor(q[2])), okey, 5, 2, c[0], 3, c[1], Hwy.FACING[toward(-q[3], -q[4])], new int[] {0, 4}, "hw_sign_leg");
        // RAMP 25 at the loop's top, chevrons round its outside, delineators down both ramps
        double topAlong = nearestAlong(loop, g(k, L, L - R));
        signPost(loop, topAlong - 6, "ramp");
        for (double s = topAlong + 8; s < topAlong + R * Math.PI * 1.4; s += 11) {
            int[] e = loop.beside(s, -(loop.half + 2));
            double[] pt = loop.point(s);
            BlockSink at = Hwy.shifted(out, (int) Math.floor(pt[2]));
            if (at.get(e[0], 1, e[1]) == null) {
                at.set(e[0], 1, e[1], "hw_chevron[facing=" + Hwy.FACING[toward(-pt[3], -pt[4])] + "]");
            }
        }
        for (Lanes l : new Lanes[] {loop, outer}) {
            for (double s = 20; s < l.length() - 20; s += 24) {
                int[] e = l.beside(s, l.half + 2);
                BlockSink at = Hwy.shifted(out, (int) Math.floor(l.point(s)[2]));
                if (at.get(e[0], 1, e[1]) == null) {
                    at.set(e[0], 1, e[1], "hw_delineator");
                }
            }
        }
        Hwy.lamps(out, loop, 30, loop.length() - 20, 26, true, w, d);
        // a call box on the leaving road's verge before the crossing
        double[] cb = g(k, -60, 11);
        Hwy.shifted(out, (int) Math.floor(yA(k, -60))).set((int) cb[0], 1, (int) cb[1], "hw_callbox[facing=" + Hwy.FACING[facing(k, 0, -1)] + "]");
    }

    /** The facing whose front looks along (dx, dz). */
    private static int toward(double dx, double dz) {
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
    }

    private static double nearestAlong(Lanes l, double[] pt) {
        double[] h = l.nearest(pt[0], pt[1]);
        return h == null ? 0 : h[2];
    }

    /** A one-tile sign on its post on a ramp's right verge s along, facing the traffic. */
    private void signPost(Lanes l, double s, String key) {
        double[] pt = l.point(s);
        int[] e = l.beside(s, l.half + 2);
        Hwy.sign(Hwy.shifted(out, (int) Math.floor(pt[2])), key, 1, 1, e[0], 2, e[1], Hwy.FACING[toward(-pt[3], -pt[4])], new int[] {0}, "hw_sign_leg");
    }

    // ------------------------------------------------------------------ the day it closed

    private void story(Lanes[] loops) {
        // the westbound out of Grayhaven: solid both lanes, a gap here and there where someone got out and walked
        Lanes wb = new Lanes.Path(S + 1, C, 0).to(-2, C, 0).lanes(Lanes.Style.HIGHWAY, 7);
        Hwy.jam(out, wb, 4, S - 4, 5.25, 0.06, 0.12, rng);
        Hwy.jam(out, wb, 8, S - 4, 1.75, 0.1, 0.12, rng);
        // backed up the loop from the southbound (the NW loop joins the westbound) and the northbound's loop (NE)
        Hwy.jam(out, loops[2], loops[2].length() - 70, loops[2].length() - 6, 0, 0.15, 0.1, rng);
        Hwy.jam(out, loops[3], loops[3].length() - 50, loops[3].length() - 6, 0, 0.3, 0.1, rng);
        // I-495: the southbound thin, the northbound stopped behind the pile-up on the bridge
        Hwy.jam(out, ns, 10, S - 10, 3.5, 0.6, 0.1, rng);
        Lanes nb = new Lanes.Path(C, S + 1, 0).to(C, C + 70, 8).to(C, C - 70, 8).to(C, -2, 0).lanes(Lanes.Style.HIGHWAY, 7);
        Hwy.jam(out, nb, 30, S / 2.0 - 16, 1.75, 0.2, 0.15, rng);
        Hwy.jam(out, nb, 40, S / 2.0 - 16, 5.25, 0.25, 0.15, rng);
        int bt = (int) Math.floor(hNS(C)) + 1;
        out.vehicle(C + 3.5, bt, C + 6, 160f, "burnt", rng.nextLong());
        out.vehicle(C + 1.5, bt, C + 0.5, 215f, "burnt", rng.nextLong());
        out.vehicle(C + 5.5, bt, C - 4, 120f, "box_truck", rng.nextLong());
        for (int i = 0; i < 18; i++) {
            int x = C + rng.nextInt(9), z = C - 8 + rng.nextInt(18);
            if (out.get(x, bt, z) == null) {
                out.set(x, bt, z, i % 3 == 0 ? "glass_shards" : i % 3 == 1 ? "debris_chunk[facing=north]" : "tire[axis=x]");
            }
        }
        // one went through the rail and down the bank by the NE loop
        double[] off = g(3, -40, 14);
        out.vehicle(off[0], 1, off[1], 35f, "burnt", rng.nextLong());
        // the eastbound in: two left on the shoulder, the rest gone on
        out.car(C - 120, 1, C + 8.5, 270f, 3.5, 5.6, rng.nextLong());
        out.car(C + 90, 1, C + 8.5, 272f, 3.5, 5.6, rng.nextLong());
        Hwy.litter(out, wb, 0, S, 0.05, rng);
        Hwy.litter(out, loops[2], 0, loops[2].length(), 0.05, rng);
    }
}
