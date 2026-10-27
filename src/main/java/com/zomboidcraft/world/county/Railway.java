package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The county's two train lines, laid from the plan: the railway loop through every station and
 * the island's subway. Out in the country the railway follows the plan's line, its curves as
 * wide as the line allows and each station on a straight squared to the compass. In the cities
 * it keeps to the street grid, as the old lines did: on Grayhaven Island an el over the streets
 * (Josiah: elevated only on the island), on the mainland and in the inland cities at street
 * level down a street of its own, crossing the others on the level; the subway under the
 * island's streets. Where a line turns at a corner the curve cuts across the corner block,
 * which is left open (a vacant lot, or a square over the subway). Built once with the layout.
 *
 * <p>Heights are the top of the track bed, where the trains' wheels run (the trains' contract):
 * the el at {@link #EL_Y}, street level at {@link #STREET_Y}, the subway at {@link #SUBWAY_Y},
 * and in the country a block above the ground, never steeper than {@link #GRADE}.
 */
final class Railway {
    static final int EL_Y = Terrain.CITY + 11;
    static final int STREET_Y = Terrain.CITY + 1;
    static final int SUBWAY_Y = Terrain.CITY - 10;
    /** The steepest grade: the trains' limit is 3%. */
    static final double GRADE = 0.029;
    /** A bridge's bed over water is at least this high: room for a boat under its girders. */
    static final int BRIDGE_Y = Terrain.WATER + 8;
    private static final int PITCH = Layout.PITCH, EDGE = DowntownSite.TILE_EDGE;
    private static final int[] DX = {1, 0, -1, 0}, DZ = {0, 1, 0, -1};

    /** One kind of line's numbers: its corners on the grid, how long its platforms are. */
    private record Spec(Track.Kind kind, double gridRadius, int minGap, double platformHalf, double turnCost, double devWeight) {
        /** Straight needed either side of a stop's middle on the grid: the platform, ten more, and a corner's curve. */
        double stationHalf() {
            return platformHalf + 10 + gridRadius;
        }
    }

    private static final Spec RAIL = new Spec(Track.Kind.RAIL, 60, 2, 50, 700, 0.6);
    private static final Spec SUBWAY = new Spec(Track.Kind.SUBWAY, 40, 1, 38, 260, 0.25);

    /** What the railway asks of one piece of the city. */
    static final class TileRails {
        /** Grid blocks (by {@link County#key}) a curve crosses, and what they're left as: "vacant" or "plaza". */
        final Map<Long, String> roles = new HashMap<>();
        /** Grid street stretches the railway runs down at street level: {0 for a line of constant x or 1 of z, line, from, to}. */
        final List<int[]> streets = new ArrayList<>();
        /** Grid blocks (by {@link County#key}) that hold a street stop's station, beside its platform. */
        final Map<Long, Station> stations = new HashMap<>();
    }

    /**
     * A town station wanted beside a street stop's platform: the stop's name key (as the kit's
     * lettering has it), the world (x, z) of the platform's middle, the side of its block that faces
     * the line (0 north, 1 east, 2 south, 3 west) and the street's half width there.
     */
    record Station(String key, int x, int z, int face, int street) {}

    private final Layout layout;
    private final Plan plan;
    private final Terrain terrain;
    final List<Track> tracks = new ArrayList<>();
    /** The forced roles, before they're handed to the city's pieces. */
    private final Map<Long, String> roles = new HashMap<>();
    private final List<int[]> streets = new ArrayList<>();

    Railway(Layout layout, Plan plan, Terrain terrain) {
        this.layout = layout;
        this.plan = plan;
        this.terrain = terrain;
        for (Plan.Line l : plan.rails) {
            List<String> stops = plan.stops.getOrDefault(l.id, List.of());
            try {
                Track t = l.cls.equals("subway") ? subway(l, stops) : railway(l, stops);
                if (t != null) {
                    tracks.add(t);
                }
            } catch (RuntimeException e) {
                // a line that can't be laid is left out rather than the whole county
                System.out.println("Couldn't lay the " + l.id + " line: " + e);
                e.printStackTrace(System.out);
            }
        }
    }

    // ------------------------------------------------------------------ the city's grid

    /** True if the grid block (i, j) is the city's. */
    private boolean block(int i, int j) {
        return layout.cityBlock(i, j);
    }

    /** True if a street runs from node (i, j) one block on in heading h. */
    private boolean seg(int i, int j, int h) {
        return switch (h) {
            case 0 -> block(i, j - 1) || block(i, j);
            case 2 -> block(i - 1, j - 1) || block(i - 1, j);
            case 1 -> block(i - 1, j) || block(i, j);
            default -> block(i - 1, j - 1) || block(i, j - 1);
        };
    }

    /** The block a curve from heading a into heading b at node (i, j) cuts across, as {i, j}. */
    private static int[] corner(int i, int j, int a, int b) {
        int vx = -DX[a] + DX[b], vz = -DZ[a] + DZ[b];
        return new int[] {i + (vx < 0 ? -1 : 0), j + (vz < 0 ? -1 : 0)};
    }

    /** True if a curve may cut across the corner block: the city's (it'll be left open) or open dry land. */
    private boolean cornerOk(int i, int j, int a, int b) {
        int[] c = corner(i, j, a, b);
        if (block(c[0], c[1])) {
            return true;
        }
        double x = (c[0] + 0.5) * PITCH, z = (c[1] + 0.5) * PITCH;
        return !layout.reservedAt(x, z) && plan.toWater(x, z) > 30;
    }

    /** True inside a piece of the city: on a block of its grid, or within the edge streets round it. */
    private boolean cityAt(double x, double z) {
        int i = Math.floorDiv((int) Math.floor(x), PITCH), j = Math.floorDiv((int) Math.floor(z), PITCH);
        for (int a = i - 1; a <= i + 1; a++) {
            for (int b = j - 1; b <= j + 1; b++) {
                if (block(a, b) && x >= a * PITCH - EDGE && x <= (a + 1) * PITCH + EDGE && z >= b * PITCH - EDGE && z <= (b + 1) * PITCH + EDGE) {
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ routing on the grid

    /** A way through a stop on the grid: down a line from one node to another, straight. */
    private record Pass(int stop, int i0, int j0, int i1, int j1, int h, double cost, double cx, double cz) {}

    /** Where a route may come into the grid (or leave it): a node, the heading, and what getting there costs. */
    private record Gate(int i, int j, int h, double cost) {}

    private final class Router {
        final Spec spec;
        final Plan.Shape guide;
        final int i0, j0, ni, nj;

        Router(Spec spec, Plan.Shape guide, double minX, double minZ, double maxX, double maxZ) {
            this.spec = spec;
            this.guide = guide;
            i0 = Math.floorDiv((int) minX, PITCH) - 3;
            j0 = Math.floorDiv((int) minZ, PITCH) - 3;
            ni = Math.floorDiv((int) maxX, PITCH) + 4 - i0;
            nj = Math.floorDiv((int) maxZ, PITCH) + 4 - j0;
        }

        boolean inside(int i, int j) {
            return i >= i0 && i < i0 + ni && j >= j0 && j < j0 + nj;
        }

        int state(int i, int j, int h, int g) {
            return (((i - i0) * nj + (j - j0)) * 4 + h) * 3 + g;
        }

        int si(int s) {
            return s / 12 / nj + i0;
        }

        int sj(int s) {
            return s / 12 % nj + j0;
        }

        int sh(int s) {
            return s / 3 % 4;
        }

        int sg(int s) {
            return s % 3;
        }

        double dev(int i, int j) {
            return Math.min(guide.distance(i * PITCH, j * PITCH, 400), 400) * spec.devWeight;
        }

        /** Shortest ways out from the sources: cost and the state each was reached from. */
        double[] run(Map<Integer, Double> sources, int[] prev) {
            int size = ni * nj * 12;
            double[] dist = new double[size];
            Arrays.fill(dist, Double.MAX_VALUE);
            Arrays.fill(prev, -1);
            PriorityQueue<double[]> q = new PriorityQueue<>((a, b) -> Double.compare(a[0], b[0]));
            sources.forEach((s, c) -> {
                if (c < dist[s]) {
                    dist[s] = c;
                    q.add(new double[] {c, s});
                }
            });
            while (!q.isEmpty()) {
                double[] top = q.poll();
                int s = (int) top[1];
                if (top[0] > dist[s]) {
                    continue;
                }
                int i = si(s), j = sj(s), h = sh(s), g = sg(s);
                for (int b : new int[] {h, (h + 1) & 3, (h + 3) & 3}) {
                    boolean turn = b != h;
                    if (turn && (g < spec.minGap || !cornerOk(i, j, h, b))) {
                        continue;
                    }
                    int ni2 = i + DX[b], nj2 = j + DZ[b];
                    if (!inside(ni2, nj2) || !seg(i, j, b)) {
                        continue;
                    }
                    int ng = turn ? 1 : Math.min(g + 1, 2);
                    int t = state(ni2, nj2, b, ng);
                    double c = top[0] + PITCH + (turn ? spec.turnCost : 0) + dev(ni2, nj2) * PITCH / 80.0;
                    if (c < dist[t]) {
                        dist[t] = c;
                        prev[t] = s;
                        q.add(new double[] {c, t});
                    }
                }
            }
            return dist;
        }

        /** The cheapest state at node (i, j) able to go on in heading h, with what that costs, or {-1, inf}. */
        double[] arrive(double[] dist, int i, int j, int h) {
            double best = Double.MAX_VALUE;
            int at = -1;
            if (!inside(i, j)) {
                return new double[] {-1, best};
            }
            for (int a = 0; a < 4; a++) {
                for (int g = 0; g < 3; g++) {
                    int s = state(i, j, a, g);
                    if (dist[s] == Double.MAX_VALUE || a == (h + 2 & 3)) {
                        continue;
                    }
                    double c = dist[s];
                    if (a != h) {
                        if (g < spec.minGap || !cornerOk(i, j, a, h)) {
                            continue;
                        }
                        c += spec.turnCost;
                    }
                    if (c < best) {
                        best = c;
                        at = s;
                    }
                }
            }
            return new double[] {at, best};
        }

        /**
         * The ways a stop's train may pass it: straight down a line of the grid either way, the
         * nearest lines first (a stop on a lot of its own, like the stadium, is served from the
         * street outside), the platform slid up to half a block along to fit.
         */
        List<Pass> passes(int stop, double sx, double sz) {
            List<Pass> out = new ArrayList<>();
            double half = spec.stationHalf();
            for (int axis = 0; axis < 2; axis++) {
                double across = axis == 0 ? sx : sz, along = axis == 0 ? sz : sx;
                int base = (int) Math.floor(across / PITCH);
                for (int line = base - 3; line <= base + 4; line++) {
                    double off = Math.abs(line * PITCH - across);
                    for (int shift = -1; shift <= 1; shift++) {
                        double mid = along + shift * PITCH / 2.0;
                        int a0 = (int) Math.floor((mid - half) / PITCH), a1 = (int) Math.ceil((mid + half) / PITCH);
                        boolean ok = true;
                        for (int a = a0; a < a1 && ok; a++) {
                            ok = axis == 0 ? seg(line, a, 1) : seg(a, line, 0);
                        }
                        if (!ok) {
                            continue;
                        }
                        double cost = off * 3 + Math.abs(shift) * PITCH * 1.5 + (a1 - a0) * PITCH;
                        double cx = axis == 0 ? line * PITCH : mid, cz = axis == 0 ? mid : line * PITCH;
                        if (axis == 0) {
                            out.add(new Pass(stop, line, a0, line, a1, 1, cost, cx, cz));
                            out.add(new Pass(stop, line, a1, line, a0, 3, cost, cx, cz));
                        } else {
                            out.add(new Pass(stop, a0, line, a1, line, 0, cost, cx, cz));
                            out.add(new Pass(stop, a1, line, a0, line, 2, cost, cx, cz));
                        }
                    }
                }
            }
            if (out.isEmpty()) {
                System.out.printf("No straight street for the stop at %.0f,%.0f%n", sx, sz);
            }
            return out;
        }

        /**
         * The cheapest way from one of the gates in, through each stop's pass in order, out through
         * one of the gates out (or, with no gates, round from the last stop back into the first): the
         * nodes it visits in order, the heading leaving each, and the passes it chose. Null if there's none.
         */
        Route route(List<Gate> in, List<List<Pass>> stops, List<Gate> out) {
            boolean loop = in.isEmpty();
            if (loop) {
                Route best = null;
                for (Pass first : stops.get(0)) {
                    Route r = chain(List.of(), first, stops.subList(1, stops.size()), List.of());
                    if (r != null && (best == null || r.cost < best.cost)) {
                        best = r;
                    }
                }
                return best;
            }
            return chain(in, null, stops, out);
        }

        private Route chain(List<Gate> in, Pass first, List<List<Pass>> stops, List<Gate> out) {
            int size = ni * nj * 12;
            List<int[]> prevs = new ArrayList<>();
            List<double[]> dists = new ArrayList<>();
            // the sources of each layer: state -> (the pass it leaves, or -1 for a gate) and cost
            Map<Integer, Double> sources = new HashMap<>();
            Map<Integer, Pass> fromPass = new HashMap<>();
            List<Map<Integer, Pass>> passOf = new ArrayList<>();
            if (first != null) {
                if (!inside(first.i1, first.j1)) {
                    return null;
                }
                int s = state(first.i1, first.j1, first.h, 2);
                sources.put(s, first.cost);
                fromPass.put(s, first);
            } else {
                for (Gate g : in) {
                    if (inside(g.i, g.j) && seg(g.i, g.j, g.h)) {
                        sources.merge(state(g.i, g.j, g.h, 2), g.cost, Math::min);
                    }
                }
            }
            if (sources.isEmpty()) {
                return null;
            }
            // each layer: a run from the last stop's exits, ending at the next stop's entries
            List<List<Pass>> layers = new ArrayList<>(stops);
            if (first != null) {
                layers.add(List.of(new Pass(first.stop, first.i0, first.j0, first.i0, first.j0, first.h, 0, first.cx, first.cz)));
            }
            // what each pass's exit state came from: the arrival state in the layer before
            List<Map<Integer, int[]>> enteredFrom = new ArrayList<>();
            for (List<Pass> layer : layers) {
                int[] prev = new int[size];
                double[] dist = run(sources, prev);
                prevs.add(prev);
                dists.add(dist);
                passOf.add(fromPass);
                Map<Integer, Double> next = new HashMap<>();
                Map<Integer, Pass> nextPass = new HashMap<>();
                Map<Integer, int[]> entered = new HashMap<>();
                for (Pass p : layer) {
                    double[] a = arrive(dist, p.i0, p.j0, p.h);
                    if (a[0] < 0) {
                        continue;
                    }
                    if (!inside(p.i1, p.j1)) {
                        continue;
                    }
                    int s = state(p.i1, p.j1, p.h, 2);
                    double c = a[1] + p.cost;
                    if (c < next.getOrDefault(s, Double.MAX_VALUE)) {
                        next.put(s, c);
                        nextPass.put(s, p);
                        entered.put(s, new int[] {(int) a[0]});
                    }
                }
                enteredFrom.add(entered);
                if (next.isEmpty()) {
                    return null;
                }
                sources = next;
                fromPass = nextPass;
            }
            // the way out
            int endState;
            double endCost;
            List<Integer> chainStates = new ArrayList<>();
            if (first != null) {
                // the last layer ended back at the first pass's entry: pick its cheapest
                endState = -1;
                endCost = Double.MAX_VALUE;
                for (Map.Entry<Integer, Double> e : sources.entrySet()) {
                    if (e.getValue() < endCost) {
                        endCost = e.getValue();
                        endState = e.getKey();
                    }
                }
            } else {
                int[] prev = new int[size];
                double[] dist = run(sources, prev);
                prevs.add(prev);
                dists.add(dist);
                passOf.add(fromPass);
                endState = -1;
                endCost = Double.MAX_VALUE;
                for (Gate g : out) {
                    double[] a = arrive(dist, g.i, g.j, g.h);
                    if (a[0] >= 0 && a[1] + g.cost < endCost) {
                        endCost = a[1] + g.cost;
                        endState = (int) a[0];
                        chainStates.clear();
                        chainStates.add(g.h);
                    }
                }
                if (endState < 0) {
                    return null;
                }
            }
            // walk back: within a layer by prev, across a pass to the state it was entered from
            List<int[]> nodes = new ArrayList<>();   // {i, j, heading out}
            List<Pass> chosen = new ArrayList<>();
            int layer = prevs.size() - 1;
            int s = endState;
            int outHeading = first == null ? chainStates.get(0) : -1;
            if (first != null) {
                // the final state is the exit of the closing pseudo-pass (the first pass's entry node): back over it
                Map<Integer, int[]> entered = enteredFrom.get(layer);
                s = entered.get(s)[0];
                outHeading = first.h;
            }
            nodes.add(new int[] {si(s), sj(s), outHeading});
            while (true) {
                int p = prevs.get(layer)[s];
                if (p >= 0) {
                    nodes.add(new int[] {si(p), sj(p), sh(s)});
                    s = p;
                    continue;
                }
                // s is a source of this layer: a gate, or the exit of a pass
                Pass pass = passOf.get(layer).get(s);
                if (pass == null) {
                    break;
                }
                chosen.add(pass);
                // the pass runs straight from its entry: add the nodes between (headings along it)
                int di = Integer.signum(pass.i1 - pass.i0), dj = Integer.signum(pass.j1 - pass.j0);
                int steps = Math.abs(pass.i1 - pass.i0) + Math.abs(pass.j1 - pass.j0);
                for (int k = steps - 1; k >= 0; k--) {
                    nodes.add(new int[] {pass.i0 + di * k, pass.j0 + dj * k, pass.h});
                }
                if (layer == 0) {
                    break;
                }
                layer--;
                s = enteredFrom.get(layer).get(s)[0];
                // the arrival state's node is the pass's entry, already added: replace its heading
                nodes.remove(nodes.size() - 1);
                nodes.add(new int[] {si(s), sj(s), pass.h});
            }
            java.util.Collections.reverse(nodes);
            java.util.Collections.reverse(chosen);
            return new Route(nodes, chosen, endCost);
        }
    }

    /** A way along the grid: nodes {i, j, heading leaving it} in order, and the passes through its stops. */
    private record Route(List<int[]> nodes, List<Pass> passes, double cost) {}

    // ------------------------------------------------------------------ corners and curves

    /** A corner of the line before its curves are drawn: where, how far its curve may reach along each side, and whether it must stay. */
    private record Corner(double x, double z, double tMax, boolean keep) {}

    /** The line drawn from its corners: points about a block apart, and which lie on curves. */
    private record Drawn(double[] xs, double[] zs, boolean[] arc) {}

    /**
     * Rounds every corner of the closed loop as widely as its sides allow (up to maxR), dropping
     * a corner that can't be rounded to minR unless it must stay, and draws it.
     */
    private static Drawn draw(List<Corner> in, double minR, double maxR) {
        List<Corner> cs = new ArrayList<>(in);
        // collapse corners on top of each other
        for (int i = cs.size() - 1; i >= 0 && cs.size() > 3; i--) {
            Corner a = cs.get(i), b = cs.get((i + 1) % cs.size());
            if (Math.hypot(a.x - b.x, a.z - b.z) < 1) {
                cs.remove(a.keep ? (i + 1) % cs.size() : i);
            }
        }
        double[] t;
        while (true) {
            int m = cs.size();
            t = new double[m];
            int worst = -1;
            double worstR = minR;
            for (int i = 0; i < m; i++) {
                Corner p = cs.get(i), a = cs.get((i + m - 1) % m), b = cs.get((i + 1) % m);
                double inL = Math.hypot(p.x - a.x, p.z - a.z), outL = Math.hypot(b.x - p.x, b.z - p.z);
                double theta = turn(a, p, b);
                if (theta < 1e-6) {
                    continue;
                }
                double tan = Math.tan(theta / 2);
                t[i] = Math.min(Math.min(p.tMax, 0.48 * Math.min(inL, outL)), maxR * tan);
                double r = t[i] / tan;
                if (r < worstR - 1e-6 && !p.keep) {
                    worstR = r;
                    worst = i;
                }
            }
            if (worst < 0 || m <= 3) {
                break;
            }
            cs.remove(worst);
        }
        List<double[]> pts = new ArrayList<>();
        List<Boolean> arcs = new ArrayList<>();
        int m = cs.size();
        for (int i = 0; i < m; i++) {
            Corner p = cs.get(i), a = cs.get((i + m - 1) % m), b = cs.get((i + 1) % m), c = cs.get((i + 2) % m);
            double inL = Math.hypot(p.x - a.x, p.z - a.z), outL = Math.hypot(b.x - p.x, b.z - p.z);
            double ix = (p.x - a.x) / inL, iz = (p.z - a.z) / inL, ox = (b.x - p.x) / outL, oz = (b.z - p.z) / outL;
            double theta = turn(a, p, b);
            // the curve round this corner
            if (t[i] > 1e-6) {
                double tan = Math.tan(theta / 2), r = t[i] / tan;
                double ax = p.x - ix * t[i], az = p.z - iz * t[i];
                double sign = Math.signum(ix * oz - iz * ox);
                double cx = ax - iz * r * sign, cz = az + ix * r * sign;
                double a0 = Math.atan2(az - cz, ax - cx);
                double sweep = theta * sign;
                int steps = Math.max(2, (int) Math.ceil(Math.abs(sweep) * r / 1.0));
                for (int k = 0; k <= steps; k++) {
                    double ang = a0 + sweep * k / steps;
                    pts.add(new double[] {cx + Math.cos(ang) * r, cz + Math.sin(ang) * r});
                    arcs.add(k > 0 && k < steps);
                }
            } else {
                pts.add(new double[] {p.x, p.z});
                arcs.add(false);
            }
            // the straight on to the next corner's curve
            double sx = p.x + ox * t[i], sz = p.z + oz * t[i];
            double ex = b.x - ox * t[(i + 1) % m], ez = b.z - oz * t[(i + 1) % m];
            double len = Math.hypot(ex - sx, ez - sz);
            int steps = (int) Math.floor(len);
            for (int k = 1; k < steps; k++) {
                pts.add(new double[] {sx + (ex - sx) * k / steps, sz + (ez - sz) * k / steps});
                arcs.add(false);
            }
        }
        double[] xs = new double[pts.size()], zs = new double[pts.size()];
        boolean[] arc = new boolean[pts.size()];
        for (int i = 0; i < pts.size(); i++) {
            xs[i] = pts.get(i)[0];
            zs[i] = pts.get(i)[1];
            arc[i] = arcs.get(i);
        }
        return new Drawn(xs, zs, arc);
    }

    /** How far the line turns at p, coming from a and going on to b (0 to pi). */
    private static double turn(Corner a, Corner p, Corner b) {
        double ix = p.x - a.x, iz = p.z - a.z, ox = b.x - p.x, oz = b.z - p.z;
        double il = Math.hypot(ix, iz), ol = Math.hypot(ox, oz);
        if (il < 1e-9 || ol < 1e-9) {
            return 0;
        }
        return Math.atan2(Math.abs(ix * oz - iz * ox), ix * ox + iz * oz) ;
    }

    // ------------------------------------------------------------------ the subway

    private Track subway(Plan.Line line, List<String> stopIds) {
        List<Plan.Spot> spots = spots(stopIds);
        if (spots.size() < 2) {
            return null;
        }
        Plan.Shape s = line.shape;
        Router r = new Router(SUBWAY, s, s.minX - 300, s.minZ - 300, s.maxX + 300, s.maxZ + 300);
        List<List<Pass>> passes = new ArrayList<>();
        for (int k = 0; k < spots.size(); k++) {
            passes.add(r.passes(k, spots.get(k).x(), spots.get(k).z()));
        }
        Route route = r.route(List.of(), passes, List.of());
        if (route == null) {
            System.out.println("No way round the island's streets for the subway");
            return null;
        }
        List<Corner> corners = gridCorners(route.nodes, SUBWAY, true);
        markCorners(route.nodes, "plaza", true);
        Drawn d = draw(corners, 30, 600);
        return finish(line.id, "Grayhaven Subway", SUBWAY, d, spots, route.passes, null);
    }

    /** The plan spots named, in order (unknown ones left out). */
    private List<Plan.Spot> spots(List<String> ids) {
        List<Plan.Spot> out = new ArrayList<>();
        for (String id : ids) {
            Plan.Spot s = plan.byId.get(id);
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    /** The corners of a way along the grid: every node it turns at (and, open, its ends). */
    private static List<Corner> gridCorners(List<int[]> nodes, Spec spec, boolean closed) {
        List<Corner> out = new ArrayList<>();
        int m = nodes.size();
        for (int k = 0; k < m; k++) {
            int[] n = nodes.get(k);
            int before = closed ? nodes.get((k + m - 1) % m)[2] : k == 0 ? n[2] : nodes.get(k - 1)[2];
            boolean end = !closed && (k == 0 || k == m - 1);
            if (before != n[2] || end) {
                out.add(new Corner(n[0] * PITCH, n[1] * PITCH, spec.gridRadius, true));
            }
        }
        return out;
    }

    /** Leaves open the corner block each turn cuts across. */
    private void markCorners(List<int[]> nodes, String role, boolean closed) {
        int m = nodes.size();
        for (int k = closed ? 0 : 1; k < m; k++) {
            int[] n = nodes.get(k);
            int before = nodes.get((k + m - 1) % m)[2];
            if (before != n[2] && n[2] >= 0 && before >= 0 && (before + 2 & 3) != n[2]) {
                int[] c = corner(n[0], n[1], before, n[2]);
                if (block(c[0], c[1])) {
                    roles.merge(County.key(c[0], c[1]), role, (a, b) -> a.equals("vacant") ? a : b);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the railway

    /** A stretch of the plan's line inside the city: from s0 to s1 along it (s1 may pass the loop's end). */
    private record Run(double s0, double s1) {}

    private Track railway(Plan.Line line, List<String> stopIds) {
        Plan.Shape shape = line.shape;
        int m = shape.count();
        double[] at = new double[m + 1];
        for (int i = 1; i <= m; i++) {
            at[i] = at[i - 1] + Math.hypot(shape.x(i) - shape.x(i - 1), shape.z(i) - shape.z(i - 1));
        }
        double total = at[m];
        // the stops, where they fall along the plan's line
        List<Plan.Spot> spots = spots(stopIds);
        double[] stopS = new double[spots.size()];
        for (int k = 0; k < spots.size(); k++) {
            stopS[k] = alongPlan(shape, at, spots.get(k).x(), spots.get(k).z());
        }
        // the city along it, sampled every 8 blocks, joined across short gaps and dropped where it only grazes
        int ns = (int) Math.ceil(total / 8);
        boolean[] city = new boolean[ns];
        for (int k = 0; k < ns; k++) {
            double[] p = pointAt(shape, at, k * 8.0);
            city[k] = cityAt(p[0], p[1]);
        }
        fill(city, 15, true);   // gaps under 120 blocks
        fill(city, 10, false);  // grazes under 80
        List<Run> runs = new ArrayList<>();
        int start = -1;
        for (int k = 0; k < ns; k++) {
            if (!city[k]) {
                start = k;
                break;
            }
        }
        if (start < 0) {
            return null;
        }
        for (int k = 0; k < ns; k++) {
            int a = (start + k) % ns;
            if (city[a] && !city[(a + ns - 1) % ns]) {
                int len = 0;
                while (city[(a + len) % ns]) {
                    len++;
                }
                runs.add(new Run(a * 8.0, (a + len) * 8.0));
            }
        }
        // walk the loop from the start of the first free stretch: free corners, then each city run on the grid
        double base = start * 8.0;
        List<Corner> corners = new ArrayList<>();
        List<Pass> passes = new ArrayList<>();
        List<double[]> freeStops = new ArrayList<>();   // {stop index, x, z, heading} for stops in the country
        double s = base;
        for (int ri = 0; ri <= runs.size(); ri++) {
            Run run = ri < runs.size() ? runs.get(ri) : null;
            double end = run == null ? base + total : unwrap(run.s0, base, total);
            // the free stretch from s to end
            List<Corner> free = new ArrayList<>();
            for (int k = 0; k < spots.size(); k++) {
                double ss = unwrap(stopS[k], base, total);
                if (ss >= s && ss < end) {
                    double[] p = pointAt(shape, at, ss);
                    int h = snap(p[2], p[3]);
                    // a pin by a creek or a lake: the platform slides along its straight onto dry land
                    double[] c = dry(spots.get(k).x(), spots.get(k).z(), h, RAIL.platformHalf + 14);
                    double cs = alongPlan(shape, at, c[0], c[1], base, total);
                    if (cs < s || cs >= end) {
                        c = new double[] {spots.get(k).x(), spots.get(k).z()};
                        cs = ss;
                    }
                    freeStops.add(new double[] {k, c[0], c[1], h, cs});
                }
            }
            for (int i = 0; i < m; i++) {
                double si = unwrap(at[i], base, total);
                if (si > s && si < end) {
                    boolean nearStop = false;
                    for (double[] fs : freeStops) {
                        if (Math.abs(fs[4] - si) < 130) {
                            nearStop = true;
                        }
                    }
                    if (!nearStop) {
                        free.add(new Corner(shape.x(i), shape.z(i), 1e9, false));
                    }
                }
            }
            // the stops' straights, squared to the compass, slotted in where they fall
            for (double[] fs : freeStops) {
                if (fs[4] < s || fs[4] >= end) {
                    continue;
                }
                int h = (int) fs[3];
                double half = RAIL.platformHalf + 30;
                Corner a = new Corner(fs[1] - DX[h] * half, fs[2] - DZ[h] * half, 22, true);
                Corner b = new Corner(fs[1] + DX[h] * half, fs[2] + DZ[h] * half, 22, true);
                int pos = 0;
                while (pos < free.size() && alongPlan(shape, at, free.get(pos).x, free.get(pos).z, base, total) < fs[4]) {
                    pos++;
                }
                free.add(pos, b);
                free.add(pos, a);
            }
            if (run == null) {
                trimNear(free, corners.isEmpty() ? null : corners.get(corners.size() - 1), null);
                corners.addAll(free);
                break;
            }
            // the city run on the grid
            double r0 = unwrap(run.s0, base, total), r1 = r0 + (run.s1 - run.s0 + total) % total;
            double[] e = pointAt(shape, at, r0), x = pointAt(shape, at, r1);
            double minX = Math.min(e[0], x[0]), maxX = Math.max(e[0], x[0]), minZ = Math.min(e[1], x[1]), maxZ = Math.max(e[1], x[1]);
            for (double q = r0; q < r1; q += 40) {
                double[] p = pointAt(shape, at, q);
                minX = Math.min(minX, p[0]);
                maxX = Math.max(maxX, p[0]);
                minZ = Math.min(minZ, p[1]);
                maxZ = Math.max(maxZ, p[1]);
            }
            Router router = new Router(RAIL, shape, minX - 400, minZ - 400, maxX + 400, maxZ + 400);
            List<List<Pass>> stopPasses = new ArrayList<>();
            for (int k = 0; k < spots.size(); k++) {
                double ss = unwrap(stopS[k], base, total);
                if (ss >= r0 && ss < r1) {
                    stopPasses.add(router.passes(k, spots.get(k).x(), spots.get(k).z()));
                }
            }
            List<Gate> in = gates(router, e, true), out = gates(router, x, false);
            Route route = router.route(in, stopPasses, out);
            if (route == null) {
                System.out.println("No way through the city's streets for the railway from " + Math.round(e[0]) + "," + Math.round(e[1]));
                s = r1;
                continue;
            }
            passes.addAll(route.passes);
            List<int[]> nodes = route.nodes;
            int[] first = nodes.get(0), last = nodes.get(nodes.size() - 1);
            // come in along the street from outside, and leave the same way
            Corner approach = new Corner(first[0] * PITCH - DX[first[2]] * 120, first[1] * PITCH - DZ[first[2]] * 120, 1e9, true);
            Corner away = new Corner(last[0] * PITCH + DX[last[2]] * 120, last[1] * PITCH + DZ[last[2]] * 120, 1e9, true);
            trimNear(free, corners.isEmpty() ? null : corners.get(corners.size() - 1), approach);
            corners.addAll(free);
            corners.add(approach);
            corners.addAll(gridCorners(nodes, RAIL, false));
            corners.add(away);
            boolean island = Plan.onIsland(first[0] * PITCH, first[1] * PITCH) || Plan.onIsland(last[0] * PITCH, last[1] * PITCH);
            markCorners(nodes, "vacant", false);
            if (!island) {
                // at street level: the streets it runs down are the railway's
                for (int k = 0; k + 1 < nodes.size(); k++) {
                    int[] a = nodes.get(k), b = nodes.get(k + 1);
                    if (a[0] == b[0]) {
                        streets.add(new int[] {0, a[0] * PITCH, Math.min(a[1], b[1]) * PITCH, Math.max(a[1], b[1]) * PITCH});
                    } else {
                        streets.add(new int[] {1, a[1] * PITCH, Math.min(a[0], b[0]) * PITCH, Math.max(a[0], b[0]) * PITCH});
                    }
                }
            }
            s = r1;
        }
        // the corners at the loop's seam: drop free corners crowding the first one
        Drawn d = draw(corners, 40, 700);
        return finish(line.id, "Grayhaven County Railway", RAIL, d, spots, passes, freeStops);
    }

    /** Where the route may come into (or leave) the grid near a point on the plan's line. */
    private List<Gate> gates(Router r, double[] p, boolean in) {
        List<Gate> out = new ArrayList<>();
        int ci = (int) Math.round(p[0] / PITCH), cj = (int) Math.round(p[1] / PITCH);
        for (int i = ci - 4; i <= ci + 4; i++) {
            for (int j = cj - 4; j <= cj + 4; j++) {
                double d = Math.hypot(i * PITCH - p[0], j * PITCH - p[1]);
                if (d > 300 || !r.inside(i, j)) {
                    continue;
                }
                for (int h = 0; h < 4; h++) {
                    // in: a street leads on in heading h and none comes from behind; out: the street ends going h
                    boolean ok = in ? seg(i, j, h) && !seg(i, j, h + 2 & 3) : !seg(i, j, h) && seg(i, j, h + 2 & 3);
                    if (!ok) {
                        continue;
                    }
                    // and the ground beyond the end, where the line curves away, is open and dry
                    double bx = i * PITCH + (in ? -DX[h] : DX[h]) * 100, bz = j * PITCH + (in ? -DZ[h] : DZ[h]) * 100;
                    if (cityAt(bx, bz) && !Plan.onIsland(bx, bz) || layout.reservedAt(bx, bz)) {
                        continue;
                    }
                    double dot = DX[h] * p[2] + DZ[h] * p[3];
                    if (dot < -0.2) {
                        continue;
                    }
                    out.add(new Gate(i, j, h, d * 2 + (1 - dot) * 400));
                }
            }
        }
        return out;
    }

    /** Drops the free corners too near the corner before them or the approach after them for a curve to fit. */
    private static void trimNear(List<Corner> free, Corner before, Corner after) {
        free.removeIf(c -> !c.keep && (before != null && Math.hypot(c.x - before.x, c.z - before.z) < 160
                || after != null && Math.hypot(c.x - after.x, c.z - after.z) < 160));
    }

    private static void fill(boolean[] v, int maxLen, boolean value) {
        int n = v.length;
        for (int k = 0; k < n; k++) {
            if (v[k] == value || v[(k + n - 1) % n] != value) {
                continue;
            }
            int len = 0;
            while (len < n && v[(k + len) % n] != value) {
                len++;
            }
            if (len <= maxLen && len < n) {
                for (int q = 0; q < len; q++) {
                    v[(k + q) % n] = value;
                }
            }
        }
    }

    /** s put in the range base .. base + total. */
    private static double unwrap(double s, double base, double total) {
        double r = (s - base) % total;
        return base + (r < 0 ? r + total : r);
    }

    /** The heading (0 east .. 3 north) nearest a direction. */
    private static int snap(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? 0 : 2) : (dz >= 0 ? 1 : 3);
    }

    private static double alongPlan(Plan.Shape s, double[] at, double x, double z) {
        double[] n = s.nearest(x, z);
        if (n == null) {
            double best = Double.MAX_VALUE, out = 0;
            for (int i = 0; i < s.count(); i++) {
                double d = Math.hypot(s.x(i) - x, s.z(i) - z);
                if (d < best) {
                    best = d;
                    out = at[i];
                }
            }
            return out;
        }
        int seg = (int) Math.floor(n[4]);
        return at[seg] + (at[seg + 1] - at[seg]) * (n[4] - seg);
    }

    private static double alongPlan(Plan.Shape s, double[] at, double x, double z, double base, double total) {
        return unwrap(alongPlan(s, at, x, z), base, total);
    }

    /** The point d along the closed line: {x, z, direction x, direction z}. */
    private static double[] pointAt(Plan.Shape s, double[] at, double d) {
        int m = s.count();
        double total = at[m];
        d = ((d % total) + total) % total;
        int i = 0;
        while (i < m - 1 && at[i + 1] < d) {
            i++;
        }
        double len = Math.max(1e-9, at[i + 1] - at[i]), t = (d - at[i]) / len;
        double ax = s.x(i), az = s.z(i), bx = s.x(i + 1), bz = s.z(i + 1);
        return new double[] {ax + (bx - ax) * t, az + (bz - az) * t, (bx - ax) / len, (bz - az) / len};
    }

    // ------------------------------------------------------------------ heights, what carries it, the stops

    private Track finish(String id, String name, Spec spec, Drawn d, List<Plan.Spot> spots, List<Pass> passes, List<double[]> freeStops) {
        // resample a block apart
        int dn = d.xs.length;
        double[] cum = new double[dn + 1];
        for (int i = 1; i <= dn; i++) {
            cum[i] = cum[i - 1] + Math.hypot(d.xs[i % dn] - d.xs[i - 1], d.zs[i % dn] - d.zs[i - 1]);
        }
        double total = cum[dn];
        int n = (int) Math.round(total / Track.STEP);
        double step = total / n;
        float[] xs = new float[n], zs = new float[n], ys = new float[n];
        int seg = 0;
        for (int i = 0; i < n; i++) {
            double at = i * step;
            while (seg < dn - 1 && cum[seg + 1] < at) {
                seg++;
            }
            double span = cum[seg + 1] - cum[seg], t = span <= 1e-9 ? 0 : (at - cum[seg]) / span;
            xs[i] = (float) (d.xs[seg] + (d.xs[(seg + 1) % dn] - d.xs[seg]) * t);
            zs[i] = (float) (d.zs[seg] + (d.zs[(seg + 1) % dn] - d.zs[seg]) * t);
        }
        // the stops' platforms: the samples beside them
        List<int[]> platforms = new ArrayList<>();   // {stop index, from, to, side}
        int half = (int) Math.round(spec.platformHalf + 6);
        for (Pass p : passes) {
            platforms.add(platform(xs, zs, p.stop, p.cx, p.cz, half, spots.get(p.stop)));
        }
        if (freeStops != null) {
            for (double[] fs : freeStops) {
                platforms.add(platform(xs, zs, (int) fs[0], fs[1], fs[2], half, spots.get((int) fs[0])));
            }
        }
        // heights
        double[] lb = new double[n], ub = new double[n], target = new double[n];
        byte[] kindAt = new byte[n];   // 0 country, 1 el, 2 street, 3 subway
        byte[] street = new byte[n];
        for (int i = 0; i < n; i++) {
            double x = xs[i], z = zs[i];
            lb[i] = -1e9;
            ub[i] = 1e9;
            boolean inCity = cityAt(x, z);
            street[i] = streetHalf(xs, zs, i, n);
            if (spec.kind == Track.Kind.SUBWAY) {
                kindAt[i] = 3;
                lb[i] = ub[i] = target[i] = SUBWAY_Y;
                continue;
            }
            if (inCity && Plan.onIsland(x, z)) {
                kindAt[i] = 1;
                lb[i] = ub[i] = target[i] = EL_Y;
                continue;
            }
            if (inCity) {
                kindAt[i] = 2;
                lb[i] = STREET_Y;
                target[i] = STREET_Y;
                continue;
            }
            double g = terrain.height(x, z);
            target[i] = Math.floor(g) + 1;
            if (water(x, z, xs, zs, i, n)) {
                lb[i] = BRIDGE_Y;
                target[i] = BRIDGE_Y;
            } else {
                // never buried more than a few blocks: past that it rides an embankment's worth over the land
                lb[i] = Math.floor(g) - 3;
            }
        }
        // street level is held exactly once well clear of where the line climbs out of the city
        for (int i = 0; i < n; i++) {
            if (kindAt[i] != 2) {
                continue;
            }
            boolean clear = true;
            for (int k = -160; k <= 160 && clear; k += 8) {
                clear = kindAt[Math.floorMod(i + k, n)] == 2;
            }
            if (clear) {
                ub[i] = STREET_Y;
            }
        }
        // the platforms are level, at their stretch's own height
        for (int[] pl : platforms) {
            double sum = 0;
            int cnt = 0;
            for (int i = pl[1] - 60; i <= pl[2] + 60; i++) {
                sum += target[Math.floorMod(i, n)];
                cnt++;
            }
            double level = Math.round(sum / Math.max(1, cnt));
            for (int i = pl[1] - 12; i <= pl[2] + 12; i++) {
                int k = Math.floorMod(i, n);
                if (kindAt[k] == 0) {
                    level = Math.max(level, lb[k]);
                }
            }
            for (int i = pl[1] - 12; i <= pl[2] + 12; i++) {
                int k = Math.floorMod(i, n);
                if (kindAt[k] == 0) {
                    lb[k] = ub[k] = level;
                }
            }
        }
        double[] y = profile(target, lb, ub, n);
        // what carries it
        byte[] mode = new byte[n];
        for (int i = 0; i < n; i++) {
            if (kindAt[i] == 3) {
                mode[i] = Track.TUNNEL;
            } else if (kindAt[i] == 1) {
                mode[i] = Track.VIADUCT;
            } else {
                double ground = kindAt[i] == 2 ? Terrain.CITY : Math.floor(terrain.height(xs[i], zs[i]));
                boolean wet = kindAt[i] == 0 && water(xs[i], zs[i], xs, zs, i, n);
                mode[i] = wet || y[i] - 1 - ground >= 5 ? Track.VIADUCT : Track.GROUND;
            }
            ys[i] = (float) y[i];
        }
        smoothModes(mode, Track.GROUND, 24);
        smoothModes(mode, Track.VIADUCT, 10);
        // reach: how far out the line lays anything (the subway as far as Union Station's passage under the sidewalk)
        Track track = new Track(id, name, spec.kind, xs, zs, ys, mode, street, spec.kind == Track.Kind.SUBWAY ? 16 : 24);
        // the stops in the order the trains reach them
        platforms.sort((a, b) -> Integer.compare(a[1], b[1]));
        for (int[] pl : platforms) {
            Plan.Spot sp = spots.get(pl[0]);
            short idx = (short) track.stops.size();
            track.stops.add(new Track.Stop(stopName(sp), sp.id(), pl[1], pl[2], pl[3]));
            for (int i = pl[1]; i <= pl[2]; i++) {
                track.stopAt[Math.floorMod(i, n)] = idx;
            }
        }
        // the points the trains run on: the curves every few blocks, the straights every couple of dozen
        int last = -100;
        double[] dcum = cum;
        int di = 0;
        for (int i = 0; i < n; i++) {
            double at = i * step;
            while (di < dn - 1 && dcum[di + 1] < at) {
                di++;
            }
            boolean arc = d.arc[di] || d.arc[(di + 1) % dn];
            boolean corner = d.arc[di] != d.arc[(di + 1) % dn];
            if (corner || arc && i - last >= 3 || i - last >= 24) {
                track.points.add(new double[] {round3(xs[i]), round3(y[i]), round3(zs[i])});
                last = i;
            }
        }
        System.out.printf("Laid the %s: %d blocks round, %d stops, %d points%n", name, n, track.stops.size(), track.points.size());
        return track;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    /** {stop, first sample, last sample, side} for the stop whose platform's middle is at (cx, cz). */
    private int[] platform(float[] xs, float[] zs, int stop, double cx, double cz, int half, Plan.Spot spot) {
        int n = xs.length, mid = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double d = (xs[i] - cx) * (xs[i] - cx) + (zs[i] - cz) * (zs[i] - cz);
            if (d < best) {
                best = d;
                mid = i;
            }
        }
        // the platform's side: toward the place it serves where that's off the line, else toward dry land and town
        int a = Math.floorMod(mid - 2, n), b = Math.floorMod(mid + 2, n);
        double tx = xs[b] - xs[a], tz = zs[b] - zs[a], len = Math.max(1e-9, Math.hypot(tx, tz));
        tx /= len;
        tz /= len;
        double o = -(spot.x() - xs[mid]) * tz + (spot.z() - zs[mid]) * tx;
        int side;
        if (Math.abs(o) > 3) {
            side = o > 0 ? 1 : -1;
        } else {
            double rx = xs[mid] - tz * 20, rz = zs[mid] + tx * 20, lx = xs[mid] + tz * 20, lz = zs[mid] - tx * 20;
            double right = plan.toWater(rx, rz) + (cityAt(rx, rz) ? 50 : 0), left = plan.toWater(lx, lz) + (cityAt(lx, lz) ? 50 : 0);
            side = right >= left ? 1 : -1;
        }
        return new int[] {stop, mid - half, mid + half, side};
    }

    private static String stopName(Plan.Spot s) {
        String name = s.name();
        if (name.endsWith(" station") && !name.equals("Union Station")) {
            name = name.substring(0, name.length() - " station".length());
        }
        return name;
    }

    /** Over a city street, half its road's width (from its line's rank); 0 off the grid. */
    private byte streetHalf(float[] xs, float[] zs, int i, int n) {
        int a = Math.floorMod(i - 2, n), b = Math.floorMod(i + 2, n);
        double dx = xs[b] - xs[a], dz = zs[b] - zs[a];
        double x = xs[i], z = zs[i];
        if (Math.abs(dx) < 0.02 * Math.abs(dz)) {
            int line = (int) Math.round(x / PITCH);
            if (Math.abs(x - line * PITCH) < 0.6 && cityAt(x, z)) {
                return (byte) (Math.floorMod(line, 3) == 0 ? 7 : 5);
            }
        } else if (Math.abs(dz) < 0.02 * Math.abs(dx)) {
            int line = (int) Math.round(z / PITCH);
            if (Math.abs(z - line * PITCH) < 0.6 && cityAt(x, z)) {
                return (byte) (Math.floorMod(line, 3) == 0 ? 7 : 5);
            }
        }
        return 0;
    }

    /** True if there's open water under the bed (its middle or either edge) at sample i. */
    /** The middle of a country stop's straight, moved along it (heading h) until its platform and ends are clear of water. */
    private double[] dry(double x, double z, int h, double half) {
        for (int k = 0; k <= 20; k++) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                double cx = x + DX[h] * 16.0 * k * sgn, cz = z + DZ[h] * 16.0 * k * sgn;
                if (dryAlong(cx, cz, h, half)) {
                    return new double[] {cx, cz};
                }
            }
        }
        return new double[] {x, z};
    }

    private boolean dryAlong(double cx, double cz, int h, double half) {
        for (double t = -half; t <= half; t += 4) {
            for (int o = -10; o <= 10; o += 4) {
                if (Terrain.isWater(terrain.height(cx + DX[h] * t - DZ[h] * o, cz + DZ[h] * t + DX[h] * o))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean water(double x, double z, float[] xs, float[] zs, int i, int n) {
        int a = Math.floorMod(i - 1, n), b = Math.floorMod(i + 1, n);
        double tx = xs[b] - xs[a], tz = zs[b] - zs[a], len = Math.max(1e-9, Math.hypot(tx, tz));
        tx /= len;
        tz /= len;
        for (int o = -4; o <= 4; o += 4) {
            if (Terrain.isWater(terrain.height(x - tz * o, z + tx * o))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The bed's height along the loop: the target smoothed over a stretch either side, kept
     * between the bounds, and no steeper than the grade anywhere.
     */
    private static double[] profile(double[] target, double[] lb0, double[] ub0, int n) {
        double[] lb = lb0.clone(), ub = ub0.clone();
        // the bounds each carry on at the grade (twice round, the loop being closed)
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 2 * n; i++) {
                int k = i % n, p = (i + n - 1) % n;
                lb[k] = Math.max(lb[k], lb[p] - GRADE);
                ub[k] = Math.min(ub[k], ub[p] + GRADE);
            }
            for (int i = 2 * n - 1; i >= 0; i--) {
                int k = i % n, p = (i + 1) % n;
                lb[k] = Math.max(lb[k], lb[p] - GRADE);
                ub[k] = Math.min(ub[k], ub[p] + GRADE);
            }
        }
        for (int i = 0; i < n; i++) {
            if (lb[i] > ub[i]) {
                // can't be had at the grade: split the difference
                double m = (lb[i] + ub[i]) / 2;
                lb[i] = ub[i] = m;
            }
        }
        // smooth the target over 64 blocks either side
        double[] pre = new double[2 * n + 1];
        for (int i = 0; i < 2 * n; i++) {
            pre[i + 1] = pre[i] + target[i % n];
        }
        int w = 64;
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            int a = i + n - w, b = i + n + w;
            double avg = (pre[Math.min(2 * n, b + 1)] - pre[Math.max(0, a)]) / (Math.min(2 * n, b + 1) - Math.max(0, a));
            y[i] = Math.max(lb[i], Math.min(ub[i], avg));
        }
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 0; i < 2 * n; i++) {
                int k = i % n, p = (i + n - 1) % n;
                y[k] = Math.max(lb[k], Math.min(ub[k], Math.max(y[p] - GRADE, Math.min(y[p] + GRADE, y[k]))));
            }
            for (int i = 2 * n - 1; i >= 0; i--) {
                int k = i % n, p = (i + 1) % n;
                y[k] = Math.max(lb[k], Math.min(ub[k], Math.max(y[p] - GRADE, Math.min(y[p] + GRADE, y[k]))));
            }
        }
        return y;
    }

    /** Runs of the given mode shorter than len, between runs of something else, take their neighbours' mode. */
    private static void smoothModes(byte[] mode, byte which, int len) {
        int n = mode.length;
        for (int i = 0; i < n; i++) {
            if (mode[i] != which || mode[Math.floorMod(i - 1, n)] == which) {
                continue;
            }
            int k = 0;
            while (k < n && mode[(i + k) % n] == which) {
                k++;
            }
            byte other = mode[Math.floorMod(i - 1, n)];
            if (k < len && k < n && mode[(i + k) % n] == other && other != Track.TUNNEL) {
                for (int q = 0; q < k; q++) {
                    mode[(i + q) % n] = other;
                }
            }
        }
    }

    // ------------------------------------------------------------------ handing out

    /** Gives the city's pieces what the railway needs of them. */
    void markTiles(List<Place> places) {
        for (Place p : places) {
            if (p.tile == null) {
                continue;
            }
            TileRails tr = null;
            for (Map.Entry<Long, String> e : roles.entrySet()) {
                int i = (int) (e.getKey() >> 32), j = (int) (long) e.getKey();
                double cx = (i + 0.5) * PITCH, cz = (j + 0.5) * PITCH;
                if (cx >= p.x0 && cx <= p.x1 && cz >= p.z0 && cz <= p.z1) {
                    tr = tr == null ? new TileRails() : tr;
                    tr.roles.put(e.getKey(), e.getValue());
                }
            }
            for (Map.Entry<Long, Station> e : stations().entrySet()) {
                int i = (int) (e.getKey() >> 32), j = (int) (long) e.getKey();
                double cx = (i + 0.5) * PITCH, cz = (j + 0.5) * PITCH;
                if (cx >= p.x0 && cx <= p.x1 && cz >= p.z0 && cz <= p.z1) {
                    tr = tr == null ? new TileRails() : tr;
                    tr.stations.put(e.getKey(), e.getValue());
                }
            }
            for (int[] st : streets) {
                boolean hit = st[0] == 0 ? st[1] >= p.x0 - EDGE && st[1] <= p.x1 + EDGE && st[3] >= p.z0 && st[2] <= p.z1
                        : st[1] >= p.z0 - EDGE && st[1] <= p.z1 + EDGE && st[3] >= p.x0 && st[2] <= p.x1;
                if (hit) {
                    tr = tr == null ? new TileRails() : tr;
                    tr.streets.add(st);
                }
            }
            p.rails = tr;
        }
    }

    /**
     * The stations the railway's street stops want, by the grid block beside each platform: the
     * block the platform's side of the street opens onto, across the sidewalk from the stop's middle.
     */
    Map<Long, Station> stations() {
        Map<Long, Station> out = new HashMap<>();
        for (Track t : tracks) {
            if (t.kind != Track.Kind.RAIL) {
                continue;
            }
            for (Track.Stop st : t.stops) {
                int m = st.middle();
                if (t.street[m] == 0 || t.mode[m] != Track.GROUND) {
                    continue;
                }
                // the platform's side: to the right of the way the trains run when side is 1
                double px = -t.tz[m] * st.side(), pz = t.tx[m] * st.side();
                int ax = Math.abs(px) >= Math.abs(pz) ? (int) Math.signum(px) : 0, az = ax == 0 ? (int) Math.signum(pz) : 0;
                double bx = t.xs[m] + ax * (t.street[m] + 24), bz = t.zs[m] + az * (t.street[m] + 24);
                int face = ax > 0 ? 3 : ax < 0 ? 1 : az > 0 ? 0 : 2;
                String key = st.spot().startsWith("st-") ? st.spot().substring(3) : st.spot();
                out.put(County.key(Math.floorDiv((int) Math.floor(bx), PITCH), Math.floorDiv((int) Math.floor(bz), PITCH)),
                        new Station(key, (int) Math.floor(t.xs[m]), (int) Math.floor(t.zs[m]), face, t.street[m]));
            }
        }
        return out;
    }

    /** How far (x, z) is from the nearest line (up to cap). */
    double distance(double x, double z, double cap) {
        Track.Hit hit = new Track.Hit();
        double best = cap;
        for (Track t : tracks) {
            int[] cand = t.candidates((int) Math.floor(x), (int) Math.floor(z));
            if (cand != null) {
                t.nearest(cand, x, z, hit);
                best = Math.min(best, hit.distance);
            }
        }
        return best;
    }

    /** True if a place's footprint, with margin, keeps clear of the railway (the subway runs under the streets). */
    boolean clear(Place p, int margin) {
        for (Track t : tracks) {
            if (t.kind == Track.Kind.SUBWAY) {
                continue;
            }
            for (int i = 0; i < t.n; i += 4) {
                if (t.xs[i] >= p.x0 - margin && t.xs[i] <= p.x1 + margin && t.zs[i] >= p.z0 - margin && t.zs[i] <= p.z1 + margin) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Where the county's roads cross the railway: on the level, the road brought to the bed's
     * height either side (bed top = road top); under a viaduct high enough, left alone.
     */
    void crossRoads(List<Road> roads) {
        for (Track t : tracks) {
            if (t.kind == Track.Kind.SUBWAY) {
                continue;
            }
            Track.Hit hit = new Track.Hit();
            for (Road r : roads) {
                int lastAt = -1000;
                for (int k = 0; k < r.xs.length; k++) {
                    int[] cand = t.candidates((int) Math.floor(r.xs[k]), (int) Math.floor(r.zs[k]));
                    if (cand == null) {
                        continue;
                    }
                    t.nearest(cand, r.xs[k], r.zs[k], hit);
                    if (hit.i < 0 || hit.distance > Road.STEP * 0.75 || k - lastAt < 10) {
                        continue;
                    }
                    lastAt = k;
                    double bed = t.ys[hit.i];
                    if (t.mode[hit.i] == Track.VIADUCT && bed - 1 - 3 - r.ys[k] >= 5) {
                        continue;   // it passes under
                    }
                    double level = bed - 1;
                    for (int q = Math.max(0, k - 12); q <= Math.min(r.xs.length - 1, k + 12); q++) {
                        double d = Math.abs(q - k) * Road.STEP;
                        double w = Terrain.smooth(48, 10, d);
                        r.ys[q] = Terrain.lerp(r.ys[q], level, w);
                    }
                    int a = Math.max(0, k - 2), b = Math.min(r.xs.length - 1, k + 2);
                    double rx = r.xs[b] - r.xs[a], rz = r.zs[b] - r.zs[a], rl = Math.max(1e-9, Math.hypot(rx, rz));
                    t.crossings.add(new int[] {(int) Math.floor(t.xs[hit.i]), (int) Math.floor(bed), (int) Math.floor(t.zs[hit.i]),
                            (int) Math.round(rx / rl * 1000), (int) Math.round(rz / rl * 1000), r.kind.half, 0});
                }
            }
            // and the city's own streets, where the line runs down one at street level and crosses the rest
            for (int i = 0; i < t.n; i++) {
                if (t.street[i] == 0 || t.mode[i] != Track.GROUND) {
                    continue;
                }
                double x = t.xs[i], z = t.zs[i];
                boolean alongZ = Math.abs(t.tz[i]) > Math.abs(t.tx[i]);
                double a = alongZ ? z : x;
                if (Math.abs(a - Math.round(a / PITCH) * PITCH) < 0.5) {
                    int ai = (int) Math.round(a / PITCH), li = (int) Math.round((alongZ ? x : z) / PITCH);
                    boolean cross = alongZ ? seg(li, ai, 0) || seg(li, ai, 2) : seg(ai, li, 1) || seg(ai, li, 3);
                    if (cross) {
                        // the street across runs square to the line
                        t.crossings.add(new int[] {(int) Math.floor(x), (int) Math.floor(t.ys[i]), (int) Math.floor(z),
                                alongZ ? 1000 : 0, alongZ ? 0 : 1000, Math.floorMod(ai, 3) == 0 ? 7 : 5, 1});
                    }
                }
            }
        }
    }

    Set<Long> roleKeys() {
        return new HashSet<>(roles.keySet());
    }
}
