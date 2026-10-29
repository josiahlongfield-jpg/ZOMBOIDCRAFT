package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The county builders' shared tools, in a place's own coordinates (y = 0 is the ground).
 *
 * Streets are polylines and turning circles laid as a distance field, so a street that curves
 * keeps its curb, grass verge and sidewalk the same width all the way round, and junctions
 * merge without seams. Lots are then packed along the streets, each turned to face its street,
 * and the builder fills them in through a {@link Placer}. Everything else here is dressing that
 * every kind of place uses: street lights, power lines, fences and hedges, trees, parked and
 * abandoned cars, and the overgrowth that takes every lawn and verge once nobody mows it.
 *
 * Every column of the footprint carries a use (free, road, sidewalk, lot...) so later steps
 * know where they may build.
 */
final class Kit {
    static final byte FREE = 0, ROAD = 1, WALK = 2, VERGE = 3, LOT = 4, PARK = 5, BUILT = 6;

    /** How a street is surfaced and edged. */
    enum Style {
        /** A town's main street: four lanes, centre line, wide sidewalks straight off the curb. */
        MAIN(7, 0, 4, true, true),
        /** A downtown cross street: two lanes and a dashed line, wide sidewalks straight off the curb. */
        CITY(5, 0, 4, true, true),
        /** A collector road: two lanes with a dashed centre line, a grass verge and sidewalks. */
        ROAD(5, 1, 2, true, true),
        /** A residential street: curb, grass verge with the trees, a narrow sidewalk. */
        STREET(4, 2, 2, true, false),
        /** A private drive through big lots: curbed, wide grass verges, no sidewalk. */
        ESTATE(3.5, 3, 0, true, false),
        /** A county road between the towns: two lanes, a dashed yellow centre, white edge lines, gravel shoulders. */
        COUNTY(4.5, 0, 0, false, true),
        /** A country lane or a trailer park loop: asphalt, gravel shoulders, no curb. */
        LANE(3, 0, 0, false, false),
        /** A gravel drive. */
        GRAVEL(2, 0, 0, false, false),
        /** A dirt farm track, grass down the middle. */
        TRACK(1.5, 0, 0, false, false);

        final double half;
        final int verge, walk;
        final boolean curb, lines;

        Style(double half, int verge, int walk, boolean curb, boolean lines) {
            this.half = half;
            this.verge = verge;
            this.walk = walk;
            this.curb = curb;
            this.lines = lines;
        }

        /** Distance from the centre line to the outside of everything the street lays. */
        double reach() {
            return half + 1 + verge + walk;
        }
    }

    /** A street's centre line, with the distance along it at each point. */
    static final class Lane {
        final Style style;
        final double[] xs, zs, at;

        Lane(Style style, List<double[]> pts) {
            this.style = style;
            int n = pts.size();
            xs = new double[n];
            zs = new double[n];
            at = new double[n];
            for (int i = 0; i < n; i++) {
                xs[i] = pts.get(i)[0];
                zs[i] = pts.get(i)[1];
                at[i] = i == 0 ? 0 : at[i - 1] + Math.hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1]);
            }
        }

        double length() {
            return at[at.length - 1];
        }

        /** The point s blocks along, as {x, z, tangent x, tangent z}. */
        double[] point(double s) {
            s = Math.max(0, Math.min(length(), s));
            int i = 0;
            while (i < at.length - 2 && at[i + 1] < s) {
                i++;
            }
            double len = Math.max(1e-9, at[i + 1] - at[i]);
            double t = (s - at[i]) / len;
            double tx = (xs[i + 1] - xs[i]) / len, tz = (zs[i + 1] - zs[i]) / len;
            return new double[] {xs[i] + (xs[i + 1] - xs[i]) * t, zs[i] + (zs[i + 1] - zs[i]) * t, tx, tz};
        }
    }

    /** A turning circle (or a roundabout's ring) of the given style. */
    record Bulb(double x, double z, double r, Style style) {}

    /**
     * A lot: its corner in place coordinates and its size in its building's own frame (w along
     * the street, d back from it), and the side its front faces (0 north, 1 east, 2 south, 3 west).
     */
    record Lot(int x0, int z0, int w, int d, int facing) {
        int sizeX() {
            return (facing & 1) == 0 ? w : d;
        }

        int sizeZ() {
            return (facing & 1) == 0 ? d : w;
        }

        Placer placer(BlockSink out) {
            return Placer.forLot(out, x0, 0, z0, w, d, facing);
        }
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};
    final BlockSink out;
    final SplittableRandom rng;
    final int w, d;
    final byte[] use;
    final List<Lane> lanes = new ArrayList<>();
    final List<Bulb> bulbs = new ArrayList<>();
    final List<Lot> lots = new ArrayList<>();
    /** Ground kept clear of street trees so a front can be seen (a church's): {x0, z0, x1, z1}. */
    final List<int[]> treeless = new ArrayList<>();
    /** The cars left so far, {x, z, longest}: trees stand clear of them. */
    final List<double[]> parked = new ArrayList<>();

    Kit(BlockSink out, SplittableRandom rng, int w, int d) {
        this.out = out;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.use = new byte[w * d];
    }

    boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < w && z < d;
    }

    byte use(int x, int z) {
        return inside(x, z) ? use[z * w + x] : -1;
    }

    void claim(int x0, int z0, int x1, int z1, byte kind) {
        for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(w - 1, Math.max(x0, x1)); x++) {
            for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(d - 1, Math.max(z0, z1)); z++) {
                use[z * w + x] = kind;
            }
        }
    }

    /** True when every column of the box is inside and free. */
    boolean free(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (use(x, z) != FREE) {
                    return false;
                }
            }
        }
        return true;
    }

    double chance() {
        return rng.nextDouble();
    }

    String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ streets

    /** A straight street through the given points (x, z pairs). */
    Lane lane(Style style, double... pts) {
        List<double[]> list = new ArrayList<>();
        for (int i = 0; i + 1 < pts.length; i += 2) {
            list.add(new double[] {pts[i], pts[i + 1]});
        }
        Lane l = new Lane(style, list);
        lanes.add(l);
        return l;
    }

    /** A street that curves smoothly through the given points (x, z pairs), passing through each. */
    Lane curve(Style style, double... pts) {
        int n = pts.length / 2;
        List<double[]> list = new ArrayList<>();
        for (int i = 0; i + 1 < n; i++) {
            double[] p0 = at(pts, Math.max(0, i - 1)), p1 = at(pts, i), p2 = at(pts, i + 1), p3 = at(pts, Math.min(n - 1, i + 2));
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int steps = Math.max(1, (int) Math.ceil(len / 1.5));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps, t2 = t * t, t3 = t2 * t;
                double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2
                        + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                double z = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2
                        + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                list.add(new double[] {x, z});
            }
        }
        list.add(at(pts, n - 1));
        Lane l = new Lane(style, list);
        lanes.add(l);
        return l;
    }

    private static double[] at(double[] pts, int i) {
        return new double[] {pts[2 * i], pts[2 * i + 1]};
    }

    /** An arc of a circle from angle a0 to a1 (radians, 0 = east, increasing toward south). */
    Lane arc(Style style, double cx, double cz, double r, double a0, double a1) {
        List<double[]> list = new ArrayList<>();
        int steps = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) * r / 1.5));
        for (int s = 0; s <= steps; s++) {
            double a = a0 + (a1 - a0) * s / steps;
            list.add(new double[] {cx + Math.cos(a) * r, cz + Math.sin(a) * r});
        }
        Lane l = new Lane(style, list);
        lanes.add(l);
        return l;
    }

    Bulb bulb(Style style, double x, double z, double r) {
        Bulb b = new Bulb(x, z, r, style);
        bulbs.add(b);
        return b;
    }

    /** The gate on a side (0 north, 1 east, 2 south, 3 west) as {x, z}, on the footprint's edge. */
    int[] gate(int side) {
        return switch (side & 3) {
            case 0 -> new int[] {w / 2, 0};
            case 1 -> new int[] {w - 1, d / 2};
            case 2 -> new int[] {w / 2, d - 1};
            default -> new int[] {0, d / 2};
        };
    }

    /** A point just outside a gate, so a street to it runs off the edge of the footprint. */
    double[] beyond(int side) {
        int[] g = gate(side);
        return switch (side & 3) {
            case 0 -> new double[] {g[0] + 0.5, -3};
            case 1 -> new double[] {w + 2, g[1] + 0.5};
            case 2 -> new double[] {g[0] + 0.5, d + 2};
            default -> new double[] {-3, g[1] + 0.5};
        };
    }

    /**
     * Lays every street added so far: asphalt (or gravel, or dirt) with its lines, then curbs,
     * verges and sidewalks out to each street's reach, and marks the columns used.
     */
    void pave() {
        int n = w * d;
        float[] ex = new float[n], off = new float[n], along = new float[n];
        byte[] style = new byte[n], axis = new byte[n], hits = new byte[n];
        int[] stamp = new int[n];
        java.util.Arrays.fill(ex, 1e9f);
        java.util.Arrays.fill(stamp, -1);
        for (int li = 0; li < lanes.size(); li++) {
            Lane l = lanes.get(li);
            double reach = l.style.reach() + 1;
            for (int s = 0; s + 1 < l.xs.length; s++) {
                double ax = l.xs[s], az = l.zs[s], bx = l.xs[s + 1], bz = l.zs[s + 1];
                double len = Math.hypot(bx - ax, bz - az);
                if (len < 1e-9) {
                    continue;
                }
                double tx = (bx - ax) / len, tz = (bz - az) / len;
                int x0 = (int) Math.floor(Math.min(ax, bx) - reach), x1 = (int) Math.ceil(Math.max(ax, bx) + reach);
                int z0 = (int) Math.floor(Math.min(az, bz) - reach), z1 = (int) Math.ceil(Math.max(az, bz) + reach);
                for (int x = Math.max(0, x0); x <= Math.min(w - 1, x1); x++) {
                    for (int z = Math.max(0, z0); z <= Math.min(d - 1, z1); z++) {
                        double px = x + 0.5 - ax, pz = z + 0.5 - az;
                        double t = Math.max(0, Math.min(len, px * tx + pz * tz));
                        double dx = px - tx * t, dz = pz - tz * t;
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        int i = z * w + x;
                        float e = (float) (dist - l.style.half);
                        if (dist <= l.style.half + 0.5 && stamp[i] != li) {
                            stamp[i] = li;
                            hits[i]++;
                        }
                        if (e < ex[i]) {
                            ex[i] = e;
                            style[i] = (byte) l.style.ordinal();
                            off[i] = (float) (tx * dz - tz * dx);
                            along[i] = (float) (l.at[s] + t);
                            axis[i] = (byte) (Math.abs(tx) > Math.abs(tz) ? 0 : 1);
                        }
                    }
                }
            }
        }
        for (Bulb b : bulbs) {
            double reach = b.r + b.style.reach() - b.style.half + 1;
            for (int x = Math.max(0, (int) (b.x - reach)); x <= Math.min(w - 1, (int) (b.x + reach) + 1); x++) {
                for (int z = Math.max(0, (int) (b.z - reach)); z <= Math.min(d - 1, (int) (b.z + reach) + 1); z++) {
                    double dist = Math.hypot(x + 0.5 - b.x, z + 0.5 - b.z);
                    int i = z * w + x;
                    float e = (float) (dist - b.r);
                    if (dist <= b.r + 1) {
                        hits[i] = 9;
                    }
                    if (e < ex[i]) {
                        ex[i] = e;
                        style[i] = (byte) b.style.ordinal();
                        off[i] = 99;
                        axis[i] = 0;
                    }
                }
            }
        }
        Style[] styles = Style.values();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                int i = z * w + x;
                float e = ex[i];
                if (e > 50) {
                    continue;
                }
                Style s = styles[style[i]];
                String ax = axis[i] == 0 ? "x" : "z";
                if (e <= 0) {
                    use[i] = ROAD;
                    out.set(x, 0, z, surface(s, Math.abs(off[i]), along[i], hits[i] == 1, ax));
                    out.set(x, -1, z, "minecraft:gravel");
                    continue;
                }
                double k = e;
                if (k <= 1) {
                    use[i] = WALK;
                    out.set(x, 0, z, s.curb ? "curb" : s == Style.LANE || s == Style.COUNTY ? "minecraft:gravel" : grassOr("minecraft:coarse_dirt", 0.5));
                } else if (k <= 1 + s.verge) {
                    use[i] = VERGE;
                    out.set(x, 0, z, "minecraft:grass_block");
                } else if (k <= 1 + s.verge + s.walk) {
                    use[i] = WALK;
                    double r = rng.nextDouble();
                    out.set(x, 0, z, r < 0.07 ? "sidewalk_cracked" : r < 0.1 ? "minecraft:moss_block" : "sidewalk");
                    if (r < 0.04) {
                        out.set(x, 1, z, rng.nextBoolean() ? "weeds" : "grass_tuft");
                    }
                }
            }
        }
    }

    private String grassOr(String other, double p) {
        return rng.nextDouble() < p ? other : "minecraft:grass_block";
    }

    /** One column of street surface. */
    private String surface(Style s, double off, double along, boolean single, String axis) {
        switch (s) {
            case GRAVEL -> {
                double r = rng.nextDouble();
                return off < 0.6 && r < 0.4 ? "minecraft:grass_block" : r < 0.7 ? "minecraft:gravel" : r < 0.9 ? "minecraft:coarse_dirt" : "minecraft:dirt_path";
            }
            case TRACK -> {
                double r = rng.nextDouble();
                return off < 0.6 ? (r < 0.8 ? "minecraft:grass_block" : "minecraft:coarse_dirt") : r < 0.75 ? "minecraft:dirt_path" : "minecraft:coarse_dirt";
            }
            default -> {
                if (single && s.lines && off < 0.5 && (s == Style.MAIN || Math.floorMod((int) along, 8) < 4)) {
                    return rng.nextDouble() < 0.9 ? "road_line_yellow[axis=" + axis + "]" : "asphalt_crack";
                }
                if (single && s == Style.COUNTY && off > s.half - 1 && rng.nextDouble() < 0.85) {
                    return "road_line_white[axis=" + axis + "]";  // the edge line, worn away in places
                }
                String a = asphalt(s == Style.MAIN ? 0.2 : 0.3);
                return a;
            }
        }
    }

    /** Asphalt, now and then cracked, patched or mossy. */
    String asphalt(double wear) {
        double r = rng.nextDouble();
        if (r > wear) {
            return "asphalt";
        }
        r /= wear;
        return r < 0.3 ? "asphalt_crack" : r < 0.55 ? "asphalt_b" : r < 0.8 ? "asphalt_patch" : "asphalt_moss";
    }

    /** Grass and weeds pushing up through cracked road, a little of it. */
    void roadWeeds(double density) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (use(x, z) != ROAD || rng.nextDouble() > density) {
                    continue;
                }
                String g = out.get(x, 0, z);
                if (g != null && (g.startsWith("asphalt_crack") || g.startsWith("asphalt_moss")) && out.get(x, 1, z) == null) {
                    out.set(x, 1, z, rng.nextBoolean() ? "grass_tuft" : "weeds");
                }
            }
        }
    }

    /** A paved area: car park, forecourt, yard. Concrete or asphalt, worn. */
    void pad(int x0, int z0, int x1, int z1, String material, byte kind) {
        for (int x = Math.max(0, Math.min(x0, x1)); x <= Math.min(w - 1, Math.max(x0, x1)); x++) {
            for (int z = Math.max(0, Math.min(z0, z1)); z <= Math.min(d - 1, Math.max(z0, z1)); z++) {
                String m = material;
                if (material.equals("asphalt")) {
                    m = asphalt(0.35);
                } else if (material.equals("floor_concrete") && rng.nextDouble() < 0.12) {
                    m = rng.nextBoolean() ? "sidewalk_cracked" : "concrete_dark";
                }
                out.set(x, 0, z, m);
                use[z * w + x] = kind;
            }
        }
    }

    // ------------------------------------------------------------------ lots

    private static final int[][] DIR = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /**
     * Packs lots along a street, both sides (or one: side +1 is the left of the direction of
     * travel, -1 the right), from s0 to s1 along it. Each lot is between minW and maxW along the
     * street and depth deep, turned to face the street, and starts where the sidewalk ends.
     */
    List<Lot> lotsAlong(Lane l, int minW, int maxW, int depth, int gap, double s0, double s1, int sides) {
        List<Lot> made = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2) {
            if (sides != 0 && side != sides) {
                continue;
            }
            double s = s0 + rng.nextInt(4);
            while (s < Math.min(s1, l.length())) {
                int lw = minW + (maxW > minW ? rng.nextInt(maxW - minW + 1) : 0);
                double[] p = l.point(s + lw / 2.0);
                // the normal toward this side; the lot's back points that way, its front faces back
                double nx = -p[3] * side, nz = p[2] * side;
                Lot lot = fit(p[0], p[1], nx, nz, lw, depth);
                if (lot != null) {
                    made.add(lot);
                    s += lw + gap;
                } else {
                    s += 3;
                }
            }
        }
        lots.addAll(made);
        return made;
    }

    /** Lots facing a turning circle, spread round it from angle a0 to a1. */
    List<Lot> lotsAround(Bulb b, int count, int lw, int depth, double a0, double a1) {
        List<Lot> made = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = a0 + (a1 - a0) * (i + 0.5) / count;
            double nx = Math.cos(a), nz = Math.sin(a);
            Lot lot = fit(b.x() + nx * b.r(), b.z() + nz * b.r(), nx, nz, lw, depth);
            if (lot != null) {
                made.add(lot);
            }
        }
        lots.addAll(made);
        return made;
    }

    /**
     * The lot whose front row is the first free row behind (px, pz) in the cardinal direction
     * nearest (nx, nz), lw wide and depth deep; claimed, or null when it won't fit.
     */
    Lot fit(double px, double pz, double nx, double nz, int lw, int depth) {
        int back = Math.abs(nx) > Math.abs(nz) ? (nx > 0 ? 1 : 3) : (nz > 0 ? 2 : 0);
        int facing = (back + 2) & 3;
        int bx = DIR[back][0], bz = DIR[back][1];
        int cx = (int) Math.floor(px), cz = (int) Math.floor(pz);
        int k = 0;
        while (k < 30) {
            int x = cx + bx * k, z = cz + bz * k;
            byte u = use(x, z);
            if (u == -1) {
                return null;
            }
            if (u == FREE) {
                break;
            }
            k++;
        }
        if (k >= 30) {
            return null;
        }
        // where the street runs at a slant, step the lot back until its whole front row is clear
        for (int push = 0; push <= 9; push++) {
            int fx = cx + bx * (k + push), fz = cz + bz * (k + push);
            for (int dd = depth; dd >= depth * 3 / 4; dd -= 2) {
                Lot lot = switch (back) {
                    case 2 -> new Lot(fx - lw / 2, fz, lw, dd, facing);
                    case 0 -> new Lot(fx - lw / 2, fz - dd + 1, lw, dd, facing);
                    case 1 -> new Lot(fx, fz - lw / 2, lw, dd, facing);
                    default -> new Lot(fx - dd + 1, fz - lw / 2, lw, dd, facing);
                };
                int x1 = lot.x0() + lot.sizeX() - 1, z1 = lot.z0() + lot.sizeZ() - 1;
                if (lot.x0() >= 1 && lot.z0() >= 1 && x1 <= w - 2 && z1 <= d - 2 && free(lot.x0(), lot.z0(), x1, z1)) {
                    claim(lot.x0(), lot.z0(), x1, z1, LOT);
                    // the strip between the sidewalk and the lot is the lot's too (lawn, not woods)
                    for (int i = 0; i < push; i++) {
                        int sx0 = back == 1 || back == 3 ? fx - bx * (i + 1) : lot.x0();
                        int sz0 = back == 0 || back == 2 ? fz - bz * (i + 1) : lot.z0();
                        int sx1 = back == 1 || back == 3 ? sx0 : x1, sz1 = back == 0 || back == 2 ? sz0 : z1;
                        for (int x = sx0; x <= sx1; x++) {
                            for (int z = sz0; z <= sz1; z++) {
                                if (use(x, z) == FREE) {
                                    use[z * w + x] = VERGE;
                                }
                            }
                        }
                    }
                    return lot;
                }
            }
            if (push > 0 && push % 3 == 0 && use(cx + bx * (k + push), cz + bz * (k + push)) != FREE) {
                break;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ street furniture

    /**
     * Street lights down a street, alternating sides every spacing blocks: a pole at the back of
     * the curb with an arm reaching out over the road. Some still burn.
     */
    void streetLights(Lane l, int spacing, int height, double lit) {
        int side = 1;
        for (double s = spacing / 2.0; s < l.length() - 4; s += spacing) {
            double[] p = l.point(s);
            double nx = -p[3] * side, nz = p[2] * side;
            double o = l.style.half + 1.6;
            int x = (int) Math.floor(p[0] + nx * o), z = (int) Math.floor(p[1] + nz * o);
            side = -side;
            byte u = use(x, z);
            if ((u != WALK && u != VERGE) || out.get(x, 1, z) != null) {
                continue;
            }
            // the arm points at the road: the cardinal nearest back across
            int toward = Math.abs(nx) > Math.abs(nz) ? (nx > 0 ? 3 : 1) : (nz > 0 ? 0 : 2);
            for (int y = 1; y <= height; y++) {
                out.set(x, y, z, "streetlight_pole");
            }
            String headFacing = FACING[toward];
            int ax = x + DIR[toward][0], az = z + DIR[toward][1];
            out.set(ax, height, az, "streetlight_arm[facing=" + ((toward & 1) == 1 ? "east" : "south") + "]");
            int hx = ax + DIR[toward][0], hz = az + DIR[toward][1];
            out.set(hx, height, hz, (rng.nextDouble() < lit ? "streetlight_head" : "streetlight_head_off") + "[facing=" + headFacing + "]");
        }
    }

    /** A lamp post with a lantern on top, for parks, drives and old town squares. */
    void postLamp(int x, int z, boolean lit) {
        out.set(x, 1, z, "minecraft:polished_blackstone_wall");
        out.set(x, 2, z, "minecraft:polished_blackstone_wall");
        out.set(x, 3, z, "minecraft:polished_blackstone_wall");
        out.set(x, 4, z, lit ? "minecraft:lantern" : "minecraft:iron_chain[axis=y]");
    }

    /**
     * Power poles every spacing blocks along a straight run from (x0, z0) to (x1, z1) (one axis),
     * with two wires strung between them at height y. A pole or two leans dead and its wires
     * are down.
     */
    void powerLine(int x0, int z0, int x1, int z1, int y, int spacing) {
        boolean alongX = z0 == z1;
        int len = alongX ? Math.abs(x1 - x0) : Math.abs(z1 - z0);
        int dir = alongX ? Integer.signum(x1 - x0) : Integer.signum(z1 - z0);
        String across = alongX ? "z" : "x", run = alongX ? "x" : "z";
        boolean down = false;
        for (int k = 0; k <= len; k++) {
            int x = alongX ? x0 + dir * k : x0, z = alongX ? z0 : z0 + dir * k;
            if (!inside(x, z)) {
                continue;
            }
            if (k % spacing == 0) {
                down = rng.nextDouble() < 0.08;
                if (down) {
                    // snapped at the base and lying across the verge
                    out.set(x, 1, z, "minecraft:stripped_spruce_log");
                    for (int i = 1; i <= 5; i++) {
                        int fx = x + (alongX ? 0 : i), fz = z + (alongX ? i : 0);
                        if (inside(fx, fz) && out.get(fx, 1, fz) == null) {
                            out.set(fx, 1, fz, "minecraft:stripped_spruce_log[axis=" + across + "]");
                        }
                    }
                    continue;
                }
                for (int h = 1; h < y; h++) {
                    out.set(x, h, z, "pole_wood");
                }
                out.set(x, y, z, "pole_top[axis=" + across + "]");
                if (rng.nextDouble() < 0.3) {
                    out.set(x, y - 2, z, "minecraft:lightning_rod");  // a transformer can's stand-in
                }
            } else if (!down && out.get(x, y, z) == null) {
                out.set(x, y, z, "power_line[axis=" + run + "]");
            }
        }
    }

    private static final String[] FACING = {"north", "east", "south", "west"};

    /** A car (or van, or truck) on the road at (x, z), yaw in Minecraft's degrees. */
    void car(double x, double z, float yaw, double minLength, double maxLength) {
        parked.add(new double[] {x, z, maxLength});
        out.car(x, 1, z, yaw, minLength, maxLength, rng.nextLong());
    }

    /** Minecraft yaw for travelling along (tx, tz). */
    static float yaw(double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-tx, tz));
    }

    /**
     * Cars left along a street: parked at the curb, abandoned in the lane, the odd one slewed
     * across both lanes where it crashed.
     */
    void abandonedCars(Lane l, double perHundred) {
        int n = (int) Math.round(l.length() / 100 * perHundred + rng.nextDouble() - 0.5);
        for (int i = 0; i < n; i++) {
            double s = 8 + rng.nextDouble() * Math.max(1, l.length() - 16);
            double[] p = l.point(s);
            int side = rng.nextBoolean() ? 1 : -1;
            double r = rng.nextDouble();
            double o = r < 0.6 ? l.style.half - 1.4 : r < 0.9 ? l.style.half * 0.45 : rng.nextDouble() * l.style.half;
            double x = p[0] - p[3] * side * o, z = p[1] + p[2] * side * o;
            if (use((int) x, (int) z) != ROAD) {
                continue;
            }
            float yaw = yaw(p[2] * -side, p[3] * -side);
            float slew = r < 0.9 ? (float) (rng.nextDouble() * 10 - 5) : (float) (40 + rng.nextDouble() * 60) * (rng.nextBoolean() ? 1 : -1);
            car(x, z, yaw + slew, 0, rng.nextDouble() < 0.15 ? 9.5 : 5.6);
        }
    }

    // ------------------------------------------------------------------ greenery

    /** A canopy writing straight into the place, trunks over leaves. */
    Trees.Canopy canopy() {
        return new Trees.Canopy() {
            @Override
            public void wood(int x, int y, int z, String block) {
                if (inside(x, z) && soft(out.get(x, y, z))) {
                    out.set(x, y, z, block);
                }
            }

            @Override
            public void leaves(int x, int y, int z, String block) {
                if (inside(x, z) && out.get(x, y, z) == null) {
                    out.set(x, y, z, block);
                }
            }
        };
    }

    /** A canopy drawing through a lot's placer. */
    static Trees.Canopy canopy(Placer p) {
        return new Trees.Canopy() {
            @Override
            public void wood(int x, int y, int z, String block) {
                if (soft(p.get(x, y, z))) {
                    p.set(x, y, z, block);
                }
            }

            @Override
            public void leaves(int x, int y, int z, String block) {
                p.setIfAir(x, y, z, block);
            }
        };
    }

    /** Branches grow through air, leaves and plants, never through anything built. */
    static boolean soft(String there) {
        return there == null || there.equals("air") || there.contains("leaves") || there.contains("grass")
                || there.startsWith("weeds") || there.contains("bush");
    }

    /**
     * A tree at (x, z), if it has room: never on a road, a car park, a path or in a building, never
     * through a car; on a sidewalk only in a pit dug for it. Beside roads and buildings it gets a
     * tidy bed of soil round its trunk.
     */
    void tree(Trees.Kind kind, int x, int z) {
        boolean near = false;
        for (int dx = -4; dx <= 4 && !near; dx++) {
            for (int dz = -4; dz <= 4 && !near; dz++) {
                byte u = use(x + dx, z + dz);
                near = u == ROAD || u == WALK || u == PARK || u == BUILT || u == LOT;
            }
        }
        Spot spot = new Spot() {
            @Override
            public String get(int x, int y, int z) {
                return out.get(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, String block) {
                if (inside(x, z)) {
                    out.set(x, y, z, block);
                }
            }

            @Override
            public boolean pavement(int x, int z) {
                return use(x, z) == WALK && !"curb".equals(out.get(x, 0, z));
            }

            @Override
            public boolean hard(int x, int z) {
                byte u = use(x, z);
                return u < 0 || u == ROAD || u == PARK || u == BUILT || !soil(out.get(x, 0, z)) && u != WALK;
            }

            @Override
            public boolean car(int x, int z) {
                for (double[] c : parked) {
                    if (Math.hypot(x + 0.5 - c[0], z + 0.5 - c[1]) < Math.min(c[2], 10) / 2 + 0.8) {
                        return true;
                    }
                }
                return false;
            }
        };
        // where it was meant to go, or the nearest spot a block or two off that has room
        for (int r = 0; r <= 2; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == r && inside(x + dx, z + dz) && !treeless(x + dx, z + dz)
                            && plant(spot, kind, x + dx, z + dz, near, rng)) {
                        return;
                    }
                }
            }
        }
    }

    /** Headroom under a crown wherever people walk or cars go: no leaf or limb lower over paving. */
    static final int HEADROOM = 5;

    /** Where a tree is planted: the blocks it grows among, and the ground under them. */
    interface Spot {
        String get(int x, int y, int z);

        void set(int x, int y, int z, String block);

        /** A sidewalk: a trunk may stand in it, in a pit. */
        default boolean pavement(int x, int z) {
            return false;
        }

        /** Road, car park, a floor: no trunk stands on it, nothing hangs low over it. */
        boolean hard(int x, int z);

        /** A car stands there. */
        default boolean car(int x, int z) {
            return false;
        }
    }

    /** Ground a tree grows out of: the terrain under the place, grass, earth, a bed. */
    static boolean soil(String ground) {
        return ground == null || ground.equals("air") || ground.equals("minecraft:grass_block") || ground.equals("minecraft:dirt")
                || ground.equals("minecraft:coarse_dirt") || ground.equals("minecraft:podzol") || ground.equals("minecraft:rooted_dirt")
                || ground.equals("minecraft:moss_block") || ground.equals("minecraft:mud") || ground.startsWith("bed_soil")
                || ground.equals("dirt_withered");
    }

    /**
     * Grows the tree into a list first and plants it only if it fits: trunk and low limbs over
     * soil (or the pit), no limb through anything built or through a car. A big tree that doesn't
     * fit is tried as a sapling; if that doesn't fit either, nothing is planted. Leaves give way
     * where they'd hang low over paving or a car, or meet something built.
     */
    static boolean plant(Spot s, Trees.Kind kind, int x, int z, boolean bed, SplittableRandom rng) {
        boolean soil = soil(s.get(x, 0, z));
        if (soil ? s.hard(x, z) : !s.pavement(x, z)) {
            return false;
        }
        // in or beside a sidewalk the trunk gets a pit dug for it
        boolean pit = !soil;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                pit |= s.pavement(x + dx, z + dz);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (pit && !s.pavement(x + dx, z + dz) && (s.hard(x + dx, z + dz) || !soil(s.get(x + dx, 0, z + dz)))) {
                    return false;
                }
                if (!soft(s.get(x + dx, 1, z + dz)) || s.car(x + dx, z + dz)) {
                    return false;
                }
            }
        }
        // a few goes at the kind asked for (each grows another shape and size), then a sapling
        Trees.Kind[] tries = kind == Trees.Kind.YOUNG || kind == Trees.Kind.BUSH ? new Trees.Kind[] {kind, kind}
                : new Trees.Kind[] {kind, kind, kind, kind, Trees.Kind.YOUNG, Trees.Kind.YOUNG};
        for (Trees.Kind k : tries) {
            List<int[]> at = new ArrayList<>();
            List<String> blocks = new ArrayList<>();
            List<Boolean> wood = new ArrayList<>();
            Trees.grow(new Trees.Canopy() {
                @Override
                public void wood(int x, int y, int z, String block) {
                    at.add(new int[] {x, y, z});
                    blocks.add(block);
                    wood.add(true);
                }

                @Override
                public void leaves(int x, int y, int z, String block) {
                    at.add(new int[] {x, y, z});
                    blocks.add(block);
                    wood.add(false);
                }
            }, k, x, 1, z, rng);
            // a low branch in the way is pruned, as a street tree's would be (a few); the trunk in
            // the way means the tree doesn't fit
            boolean fits = true;
            int limbs = 0, pruned = 0;
            boolean[] cut = new boolean[at.size()];
            for (int i = 0; i < at.size() && fits; i++) {
                int[] c = at.get(i);
                if (!wood.get(i)) {
                    continue;
                }
                boolean trunk = Math.abs(c[0] - x) <= 1 && Math.abs(c[2] - z) <= 1;
                boolean limb = !trunk || blocks.get(i).contains("_limb_");
                limbs += limb ? 1 : 0;
                boolean inPit = pit && trunk;
                boolean ok = soft(s.get(c[0], c[1], c[2]))
                        && (c[1] >= HEADROOM || inPit || !s.hard(c[0], c[2]) && soil(s.get(c[0], 0, c[2])))
                        && (c[1] > 3 || !s.car(c[0], c[2]));
                if (!ok && limb) {
                    cut[i] = true;
                    pruned++;
                } else {
                    fits = ok;
                }
            }
            if (!fits || pruned > 3 + limbs / 6) {
                continue;
            }
            if (pit || bed) {
                bed(s, x, z, pit, rng);
            }
            for (int i = 0; i < at.size(); i++) {
                int[] c = at.get(i);
                String there = s.get(c[0], c[1], c[2]);
                if (wood.get(i)) {
                    if (soft(there) && !cut[i]) {
                        s.set(c[0], c[1], c[2], blocks.get(i));
                    }
                    continue;
                }
                boolean inPit = pit && Math.abs(c[0] - x) <= 1 && Math.abs(c[2] - z) <= 1;
                boolean low = c[1] < HEADROOM && !inPit && (s.hard(c[0], c[2]) || !soil(s.get(c[0], 0, c[2])) || s.car(c[0], c[2]));
                if ((there == null || there.equals("air")) && !low) {
                    s.set(c[0], c[1], c[2], blocks.get(i));
                }
            }
            if (pit && rng.nextDouble() < 0.45) {
                // an iron grate over the pit, a piece or two gone
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        String there = s.get(x + dx, 1, z + dz);
                        if ((dx != 0 || dz != 0) && (there == null || there.equals("air")) && rng.nextDouble() < 0.8) {
                            s.set(x + dx, 1, z + dz, "grate");
                        }
                    }
                }
            }
            return true;
        }
        return false;
    }

    /**
     * The soil round a planted trunk, so it doesn't come straight up out of a lawn or the concrete:
     * a round bed in grass; in a sidewalk, a square pit edged with old brick pavers.
     */
    private static void bed(Spot s, int x, int z, boolean pit, SplittableRandom rng) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int bx = x + dx, bz = z + dz;
                boolean ring = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                if (pit) {
                    if (ring) {
                        if (s.pavement(bx, bz) && soft(s.get(bx, 1, bz))) {
                            s.set(bx, 0, bz, "pit_pavers");
                        }
                        continue;
                    }
                } else if (ring || Math.abs(dx) + Math.abs(dz) == 2 && rng.nextDouble() < 0.45
                        || s.hard(bx, bz) || !soil(s.get(bx, 0, bz))) {
                    continue;
                }
                s.set(bx, 0, bz, rng.nextDouble() < 0.6 ? "bed_soil" : "bed_soil_b");
                String plant = s.get(bx, 1, bz);
                if (plant != null && soft(plant) && !plant.contains("leaves")) {
                    s.set(bx, 1, bz, "air");
                    if (String.valueOf(s.get(bx, 2, bz)).endsWith("[half=upper]")) {
                        s.set(bx, 2, bz, "air");
                    }
                }
            }
        }
    }

    /** A yard or street tree: mostly maples and oaks, a few dead or sick. */
    Trees.Kind yardTree() {
        double r = rng.nextDouble();
        return r < 0.38 ? Trees.Kind.MAPLE : r < 0.66 ? Trees.Kind.OAK : r < 0.76 ? Trees.Kind.YOUNG
                : r < 0.84 ? Trees.Kind.SYCAMORE : r < 0.9 ? Trees.Kind.PINE : r < 0.95 ? Trees.Kind.DEAD : Trees.Kind.SICK;
    }

    private boolean treeless(int x, int z) {
        for (int[] r : treeless) {
            if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) {
                return true;
            }
        }
        return false;
    }

    /** Street trees planted in the verges every so often, both sides. */
    void streetTrees(Lane l, int spacing, double chance) {
        for (int side = -1; side <= 1; side += 2) {
            for (double s = spacing / 2.0 + rng.nextInt(4); s < l.length(); s += spacing) {
                if (rng.nextDouble() > chance) {
                    continue;
                }
                double[] p = l.point(s);
                double o = l.style.half + 1 + l.style.verge / 2.0 + 0.5;
                int x = (int) Math.floor(p[0] - p[3] * side * o), z = (int) Math.floor(p[1] + p[2] * side * o);
                if (use(x, z) == VERGE && out.get(x, 1, z) == null && !treeless(x, z)) {
                    tree(yardTree(), x, z);
                }
            }
        }
    }

    /**
     * Unmowed grass over everything still bare: free ground goes to meadow, verges to long
     * grass and weeds. Only columns nothing else has used.
     */
    void overgrow(double wild) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                byte u = use[z * w + x];
                if (u != FREE && u != VERGE && u != PARK) {
                    continue;
                }
                if (out.get(x, 0, z) == null) {
                    out.set(x, 0, z, rng.nextDouble() < 0.06 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                }
                if (out.get(x, 1, z) == null && "minecraft:grass_block".equals(out.get(x, 0, z))) {
                    plant(x, z, u == FREE ? wild : wild * 0.6);
                }
            }
        }
    }

    private void plant(int x, int z, double wild) {
        double r = rng.nextDouble();
        if (r < 0.22 * wild + 0.08) {
            out.set(x, 1, z, "minecraft:tall_grass[half=lower]");
            out.set(x, 2, z, "minecraft:tall_grass[half=upper]");
        } else if (r < 0.5 * wild + 0.2) {
            out.set(x, 1, z, "minecraft:short_grass");
        } else if (r < 0.5 * wild + 0.24) {
            out.set(x, 1, z, rng.nextBoolean() ? "weeds" : "weeds_tall");
        } else if (r < 0.5 * wild + 0.26) {
            out.set(x, 1, z, pick("minecraft:dandelion", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:poppy"));
        } else if (r < 0.5 * wild + 0.27) {
            out.set(x, 1, z, "minecraft:bush");
        } else if (r < 0.5 * wild + 0.285) {
            out.set(x, 1, z, "minecraft:short_dry_grass");
        }
    }

    /** Takes off the tops of tall plants whose lower half something was built over. */
    void tidyPlants() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                for (int y = 2; y <= 4; y++) {
                    String b = out.get(x, y, z);
                    if (b != null && b.endsWith("[half=upper]")) {
                        String below = out.get(x, y - 1, z);
                        if (below == null || !below.equals(b.replace("[half=upper]", "[half=lower]"))) {
                            out.set(x, y, z, "air");
                        }
                    }
                }
            }
        }
    }

    /** Lawn gone to seed inside a lot, in the lot's own frame; only where nothing is built. */
    static void lawn(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1, double wild) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (p.get(x, 0, z) == null) {
                    p.set(x, 0, z, rng.nextDouble() < 0.04 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                }
                if (p.get(x, 1, z) != null || !"minecraft:grass_block".equals(p.get(x, 0, z))) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < 0.16 * wild + 0.04) {
                    p.set(x, 1, z, "minecraft:tall_grass[half=lower]");
                    p.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                } else if (r < 0.5 * wild + 0.12) {
                    p.set(x, 1, z, "minecraft:short_grass");
                } else if (r < 0.5 * wild + 0.15) {
                    p.set(x, 1, z, rng.nextBoolean() ? "weeds" : "minecraft:dandelion");
                } else if (r < 0.5 * wild + 0.16) {
                    p.set(x, 1, z, "minecraft:bush");
                }
            }
        }
    }

    /** A planted island in a roundabout or turning circle: grass, a ring of hedge, a tree. */
    void island(double cx, double cz, double r) {
        for (int x = (int) (cx - r - 1); x <= cx + r + 1; x++) {
            for (int z = (int) (cz - r - 1); z <= cz + r + 1; z++) {
                double dist = Math.hypot(x + 0.5 - cx, z + 0.5 - cz);
                if (!inside(x, z) || dist > r) {
                    continue;
                }
                use[z * w + x] = PARK;
                out.set(x, 0, z, dist > r - 1 ? "curb" : "minecraft:grass_block");
                if (dist > r - 2 && dist <= r - 1 && rng.nextDouble() < 0.7) {
                    out.set(x, 1, z, "minecraft:azalea_leaves[persistent=true]");
                }
            }
        }
        tree(rng.nextBoolean() ? Trees.Kind.MAPLE : Trees.Kind.OAK, (int) cx, (int) cz);
    }

    /**
     * The biggest free square (side between min and max) found on a coarse search, as
     * {x0, z0, side}, or null. Claimed as PARK when found.
     */
    int[] freeSquare(int min, int max) {
        int[] sum = new int[(w + 1) * (d + 1)];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int f = use[z * w + x] == FREE ? 0 : 1;
                sum[(z + 1) * (w + 1) + x + 1] = f + sum[z * (w + 1) + x + 1] + sum[(z + 1) * (w + 1) + x] - sum[z * (w + 1) + x];
            }
        }
        for (int side = max; side >= min; side -= 2) {
            int[] best = null;
            double bestScore = -1;
            for (int z = 2; z + side < d - 2; z += 2) {
                for (int x = 2; x + side < w - 2; x += 2) {
                    int blocked = sum[(z + side) * (w + 1) + x + side] - sum[z * (w + 1) + x + side] - sum[(z + side) * (w + 1) + x] + sum[z * (w + 1) + x];
                    if (blocked == 0) {
                        // prefer the middle of the place
                        double score = -Math.hypot(x + side / 2.0 - w / 2.0, z + side / 2.0 - d / 2.0);
                        if (best == null || score > bestScore) {
                            best = new int[] {x, z, side};
                            bestScore = score;
                        }
                    }
                }
            }
            if (best != null) {
                claim(best[0], best[1], best[0] + side - 1, best[1] + side - 1, PARK);
                return best;
            }
        }
        return null;
    }

    /**
     * A neighbourhood park in the square: a path loop, benches, a playground, a gazebo or a
     * basketball court, a pond if there's room, lamp posts and big old trees.
     */
    void park(int x0, int z0, int side) {
        int x1 = x0 + side - 1, z1 = z0 + side - 1;
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        claim(x0, z0, x1, z1, PARK);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out.set(x, 0, z, "minecraft:grass_block");
            }
        }
        // a path ring with spokes out to the edges
        int r = side / 2 - 4;
        String path = rng.nextBoolean() ? "minecraft:gravel" : "sidewalk";
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double dist = Math.hypot(x - cx, z - cz);
                boolean ring = Math.abs(dist - r) < 0.9;
                boolean spoke = (Math.abs(x - cx) <= 0 || Math.abs(z - cz) <= 0) && dist > r;
                if (ring || spoke) {
                    out.set(x, 0, z, rng.nextDouble() < 0.12 ? "minecraft:coarse_dirt" : path);
                }
            }
        }
        // benches and lamps on the ring
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int bx = (int) Math.round(cx + Math.cos(a) * (r + 2)), bz = (int) Math.round(cz + Math.sin(a) * (r + 2));
            if (i % 2 == 0) {
                String f = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "west" : "east") : (Math.sin(a) > 0 ? "north" : "south");
                out.set(bx, 1, bz, "minecraft:dark_oak_stairs[facing=" + opposite(f) + "]");
            } else {
                postLamp(bx, bz, rng.nextDouble() < 0.4);
            }
        }
        // in the middle: a playground, a gazebo or a court
        int pick = rng.nextInt(3);
        if (r >= 6) {
            if (pick == 0) {
                playground(cx - 3, cz - 3);
            } else if (pick == 1) {
                gazebo(cx - 2, cz - 2);
            } else {
                court(cx - 4, cz - 3);
            }
        }
        // a pond in one corner, trees in the others
        int corner = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int qx = (k & 1) == 0 ? x0 + 4 : x1 - 4, qz = (k & 2) == 0 ? z0 + 4 : z1 - 4;
            if (k == corner && side >= 30) {
                pond(qx, qz, 3 + rng.nextInt(2));
            } else {
                tree(rng.nextDouble() < 0.6 ? Trees.Kind.OAK : Trees.Kind.MAPLE, qx, qz);
            }
        }
        if (rng.nextBoolean()) {
            out.set(cx + r + 2, 1, cz + r, "minecraft:cauldron");
        }
    }

    private static String opposite(String f) {
        return switch (f) {
            case "north" -> "south";
            case "south" -> "north";
            case "east" -> "west";
            default -> "east";
        };
    }

    /**
     * A playground on bark chips, seven by seven: the swing set along the north side (a swing
     * snapped, one wound round the bar, one gone), the slide down the east side, the merry-go-round,
     * the spring rider, the seesaw, the monkey bars along the south edge.
     */
    void playground(int x, int z) {
        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                String edge = j == 0 ? "north" : j == 6 ? "south" : i == 0 ? "west" : i == 6 ? "east" : null;
                out.set(x + i, 0, z + j, edge != null ? "sc_chips_edge[facing=" + edge + "]" : rng.nextDouble() < 0.3 ? "sc_chips_b" : "sc_chips");
                for (int y = 1; y <= 3; y++) {
                    out.set(x + i, y, z + j, "air");
                }
            }
        }
        // the swing set: the A-frames at each end, four bays under the bar
        for (int y = 1; y <= 3; y++) {
            out.set(x, y, z + 1, "sc_swing_leg_" + (y - 1) + "[axis=x]");
            out.set(x + 5, y, z + 1, "sc_swing_leg_" + (y - 1) + "[axis=x]");
        }
        for (int i = 1; i <= 4; i++) {
            double r = rng.nextDouble();
            if (r < 0.6) {
                out.set(x + i, 1, z + 1, "sc_swing_seat[axis=x]");
                out.set(x + i, 2, z + 1, "sc_swing_chain[axis=x]");
                out.set(x + i, 3, z + 1, "sc_swing_top[axis=x]");
            } else if (r < 0.75) {
                out.set(x + i, 1, z + 1, "sc_swing_snapped[axis=x]");
                out.set(x + i, 2, z + 1, "sc_swing_chain[axis=x]");
                out.set(x + i, 3, z + 1, "sc_swing_top[axis=x]");
            } else if (r < 0.9) {
                out.set(x + i, 3, z + 1, "sc_swing_wrapped[axis=x]");
            } else {
                out.set(x + i, 3, z + 1, "sc_swing_bar[axis=x]");
            }
        }
        // the slide: up the ladder at the north end, down the chute to the south
        out.set(x + 6, 1, z + 1, "sc_slide_ladder[facing=south]");
        out.set(x + 6, 2, z + 1, "sc_slide_ladder[facing=south]");
        out.set(x + 6, 3, z + 1, "sc_slide_ladder_top[facing=south]");
        out.set(x + 6, 1, z + 2, "sc_slide_post[facing=south]");
        out.set(x + 6, 2, z + 2, "sc_slide_post[facing=south]");
        out.set(x + 6, 3, z + 2, "sc_slide_deck[facing=south]");
        out.set(x + 6, 2, z + 3, "sc_slide_hi[facing=south]");
        out.set(x + 6, 1, z + 4, "sc_slide_lo[facing=south]");
        out.set(x + 6, 1, z + 5, "sc_slide_out[facing=south]");
        // the merry-go-round, the spring rider, the seesaw
        for (int i = 0; i < 4; i++) {
            out.set(x + 1 + i % 2, 1, z + 3 + i / 2, "sc_merry_" + i);
        }
        out.set(x + 4, 1, z + 3, "sc_horse[facing=" + (rng.nextBoolean() ? "west" : "north") + "]");
        for (int t = 0; t < 3; t++) {
            out.set(x + 4, 1, z + 4 + t, "sc_seesaw_" + (rng.nextBoolean() ? t : 2 - t) + "[axis=z]");
        }
        // the monkey bars along the south edge
        for (int i = 0; i <= 3; i++) {
            boolean end = i == 0 || i == 3;
            if (end) {
                out.set(x + i, 1, z + 6, "sc_monkey_ladder[axis=x]");
                out.set(x + i, 2, z + 6, "sc_monkey_ladder[axis=x]");
            }
            out.set(x + i, 3, z + 6, (end ? "sc_monkey_end" : "sc_monkey_bars") + "[axis=x]");
        }
        // what was left: a bucket, a backpack, the litter
        if (rng.nextDouble() < 0.6) {
            out.set(x + 3, 1, z + 3, "sc_bucket[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        if (rng.nextDouble() < 0.4) {
            out.set(x + 5, 1, z + 5, "sc_pack_" + new String[] {"red", "blue", "green"}[rng.nextInt(3)] + "[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        if (rng.nextDouble() < 0.5) {
            out.set(x + 1 + rng.nextInt(4), 1, z + 2, rng.nextBoolean() ? "paper" : "blood_splat");
        }
    }

    /** A sandbox, its timber edge round it, a bucket and a spade left in it. */
    void sandbox(int x, int z, int w, int d) {
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < d; j++) {
                boolean n = j == 0, s = j == d - 1, west = i == 0, e = i == w - 1;
                String b;
                if ((n || s) && (west || e)) {
                    b = "sc_sand_corner[facing=" + (n ? (west ? "north" : "east") : (e ? "south" : "west")) + "]";
                } else if (n || s || west || e) {
                    b = "sc_sand_edge[facing=" + (n ? "north" : s ? "south" : west ? "west" : "east") + "]";
                } else {
                    b = "sc_sand";
                }
                out.set(x + i, 0, z + j, b);
                out.set(x + i, 1, z + j, "air");
            }
        }
        if (w > 2 && d > 2) {
            out.set(x + 1 + rng.nextInt(w - 2), 1, z + 1 + rng.nextInt(d - 2), "sc_bucket[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
    }

    /** A little white gazebo with a pyramid roof. */
    void gazebo(int x, int z) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                out.set(x + i, 0, z + j, "minecraft:pale_oak_planks");
                boolean corner = (i == 0 || i == 4) && (j == 0 || j == 4);
                boolean rail = (i == 0 || i == 4 || j == 0 || j == 4) && !corner && !(i == 2 && j == 4);
                if (corner) {
                    out.set(x + i, 1, z + j, "minecraft:pale_oak_fence");
                    out.set(x + i, 2, z + j, "minecraft:pale_oak_fence");
                    out.set(x + i, 3, z + j, "minecraft:pale_oak_fence");
                } else if (rail && rng.nextDouble() < 0.85) {
                    out.set(x + i, 1, z + j, "minecraft:pale_oak_fence");
                }
                out.set(x + i, 4, z + j, i == 0 || i == 4 || j == 0 || j == 4 ? "minecraft:deepslate_tile_slab[type=bottom]" : "minecraft:deepslate_tiles");
            }
        }
        out.set(x + 2, 5, z + 2, "minecraft:deepslate_tile_slab[type=bottom]");
        out.set(x + 1, 5, z + 2, "minecraft:deepslate_tile_slab[type=bottom]");
        out.set(x + 3, 5, z + 2, "minecraft:deepslate_tile_slab[type=bottom]");
        out.set(x + 2, 5, z + 1, "minecraft:deepslate_tile_slab[type=bottom]");
        out.set(x + 2, 5, z + 3, "minecraft:deepslate_tile_slab[type=bottom]");
        out.set(x + 2, 6, z + 2, "minecraft:lightning_rod");
    }

    /** A cracked basketball court with two hoops on goosenecks, the chain nets rusted, one gone. */
    void court(int x, int z) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 7; j++) {
                boolean line = i == 0 || i == 8 || j == 0 || j == 6 || i == 4;
                out.set(x + i, 0, z + j, line ? "road_line_white[axis=z]" : rng.nextDouble() < 0.1 ? "asphalt_crack" : "asphalt");
            }
        }
        for (int i : new int[] {0, 8}) {
            String f = i == 0 ? "east" : "west";
            int bx = x + (i == 0 ? 1 : 7);
            for (int y = 1; y <= 3; y++) {
                out.set(x + i, y, z + 3, "sc_hoop_post");
            }
            out.set(x + i, 4, z + 3, "sc_hoop_neck[facing=" + f + "]");
            out.set(bx, 4, z + 3, "sc_hoop_board[facing=" + f + "]");
            if (rng.nextDouble() < 0.7) {
                out.set(bx, 3, z + 3, "sc_hoop_net[facing=" + f + "]");
            }
        }
        if (rng.nextDouble() < 0.5) {
            out.set(x + 2 + rng.nextInt(5), 1, z + 1 + rng.nextInt(5), rng.nextBoolean() ? "sc_ball_orange" : "sc_ball_red");
        }
    }

    /** A pond, dug two deep, reeds and lily pads, mud at the edge. */
    void pond(int cx, int cz, int r) {
        for (int x = cx - r - 1; x <= cx + r + 1; x++) {
            for (int z = cz - r - 1; z <= cz + r + 1; z++) {
                double dist = Math.hypot(x - cx, z - cz) + (rng.nextDouble() - 0.5) * 0.6;
                if (!inside(x, z) || dist > r + 1) {
                    continue;
                }
                if (dist <= r) {
                    out.set(x, -1, z, "minecraft:mud");
                    out.set(x, 0, z, "minecraft:water");
                    if (dist < r - 1.5) {
                        out.set(x, -1, z, "minecraft:water");
                        out.set(x, -2, z, "minecraft:mud");
                    }
                    if (rng.nextDouble() < 0.12) {
                        out.set(x, 1, z, "minecraft:lily_pad");
                    }
                } else {
                    out.set(x, 0, z, rng.nextBoolean() ? "minecraft:mud" : "minecraft:grass_block");
                    if (rng.nextDouble() < 0.3) {
                        out.set(x, 1, z, "minecraft:sugar_cane");
                        if (rng.nextBoolean()) {
                            out.set(x, 2, z, "minecraft:sugar_cane");
                        }
                    }
                }
            }
        }
    }

    /** Woods left standing between the streets: trees scattered over free ground. */
    void woods(double density) {
        for (int x = 3; x < w - 3; x += 4) {
            for (int z = 3; z < d - 3; z += 4) {
                int tx = x + rng.nextInt(3), tz = z + rng.nextInt(3);
                if (use(tx, tz) == FREE && rng.nextDouble() < density && free(tx - 4, tz - 4, tx + 4, tz + 4)) {
                    double r = rng.nextDouble();
                    tree(r < 0.3 ? Trees.Kind.OAK : r < 0.55 ? Trees.Kind.MAPLE : r < 0.75 ? Trees.Kind.PINE
                            : r < 0.85 ? Trees.Kind.YOUNG : r < 0.93 ? Trees.Kind.BUSH : Trees.Kind.DEAD, tx, tz);
                }
            }
        }
    }

    // ------------------------------------------------------------------ fences

    /** A post-and-rail fence (white paddock boards when white) from (x0, z0) to (x1, z1) along one axis, in a lot's frame: a post every three, rails broken or gone here and there. */
    static void railFence(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1, boolean white, double broken) {
        String w = white ? "_w" : "", axis = "[axis=" + (z0 == z1 ? "x" : "z") + "]";
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean post = n++ % 3 == 0;
                double r = rng.nextDouble();
                if (r < broken * 0.5) {
                    continue;
                }
                p.set(x, 1, z, post ? "fm_rail_post" + w + axis : !white && r < broken ? "fm_rail_broken" + axis : "fm_rail" + w + axis);
            }
        }
    }

    /** A wooden privacy fence two blocks high with posts every three, boards fallen here and there. */
    static void privacyFence(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1) {
        boolean alongX = z0 == z1;
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean post = n++ % 3 == 0;
                if (post) {
                    p.set(x, 1, z, "minecraft:spruce_fence");
                    p.set(x, 2, z, "minecraft:spruce_fence");
                    continue;
                }
                double r = rng.nextDouble();
                if (r < 0.06) {
                    p.set(x, 1, z, "fence_boards[axis=" + (alongX ? "x" : "z") + "]");
                    continue;  // top board gone
                } else if (r < 0.1) {
                    continue;  // kicked through
                }
                p.set(x, 1, z, "fence_boards[axis=" + (alongX ? "x" : "z") + "]");
                p.set(x, 2, z, "fence_boards[axis=" + (alongX ? "x" : "z") + "]");
            }
        }
    }

    /** A chain-link fence in a lot's frame, posts every four, the odd panel sagging or cut. */
    static void chainlink(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1, int height) {
        boolean alongX = z0 == z1;
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean post = n++ % 4 == 0;
                boolean cut = !post && rng.nextDouble() < 0.06;
                for (int h = 1; h <= height; h++) {
                    if (cut && h <= 2) {
                        continue;
                    }
                    p.set(x, h, z, post ? "fence_post" : "chainlink[axis=" + (alongX ? "x" : "z") + "]");
                }
            }
        }
    }

    /** A clipped hedge gone shaggy, one or two blocks high. */
    static void hedge(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1, String leaves) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (rng.nextDouble() < 0.05) {
                    continue;
                }
                p.set(x, 1, z, leaves + "[persistent=true]");
                if (rng.nextDouble() < 0.7) {
                    p.set(x, 2, z, leaves + "[persistent=true]");
                }
                if (rng.nextDouble() < 0.12) {
                    p.set(x, 3, z, leaves + "[persistent=true]");
                }
            }
        }
    }

    /** Litter, blood and the odd lost belonging over an area, in a lot's frame, on bare ground only. */
    static void litter(Placer p, SplittableRandom rng, int x0, int z0, int x1, int z1, double density) {
        String[] things = {"paper", "trash_bag[facing=north]", "cardboard_box[facing=east]", "blood_splat", "glass_shards",
                "debris_chunk[facing=south]", "minecraft:leaf_litter[segment_amount=3,facing=west]"};
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (rng.nextDouble() < density && p.get(x, 1, z) == null && p.get(x, 0, z) != null) {
                    p.set(x, 1, z, things[rng.nextInt(things.length)]);
                }
            }
        }
    }
}
