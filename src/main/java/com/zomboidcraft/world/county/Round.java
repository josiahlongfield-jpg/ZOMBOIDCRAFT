package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Round walls and domes from the rd_ kit (tools/city/round.py): each block the circle passes gets a
 * panel turned to the circle's tangent and set at its radius, so silos, bins and tanks are smooth
 * drums instead of stepped block circles.
 *
 * Centres are in blocks (a block's middle is x + 0.5); radii to the outside face.
 */
final class Round {
    private static final String[] FACING = {"north", "east", "south", "west"};

    private Round() {
    }

    /** A block the circle passes and its piece: the facing and the rd_ suffix (turn and depth). */
    record Cell(int x, int z, String facing, String suffix) {
    }

    /**
     * The blocks the circle of radius r round (cx, cz) passes, each with the facing and suffix of the
     * panel that puts the wall through the middle of the circle's arc in that block.
     */
    static List<Cell> ring(double cx, double cz, double r) {
        Map<Long, double[]> arcs = new LinkedHashMap<>();
        int n = Math.max(64, (int) (2 * Math.PI * r / 0.02));
        for (int i = 0; i < n; i++) {
            double th = 2 * Math.PI * i / n;
            int x = (int) Math.floor(cx + r * Math.sin(th)), z = (int) Math.floor(cz - r * Math.cos(th));
            double[] a = arcs.computeIfAbsent(((long) x << 32) ^ (z & 0xffffffffL), k -> new double[] {x, z, 0, 0});
            a[2] += Math.sin(th);
            a[3] += Math.cos(th);
        }
        List<Cell> out = new ArrayList<>();
        for (double[] a : arcs.values()) {
            int x = (int) a[0], z = (int) a[1];
            double th = Math.atan2(a[2], a[3]);
            double mx = cx + r * Math.sin(th), mz = cz - r * Math.cos(th);
            int bi = Math.floorMod((int) Math.round(Math.toDegrees(th) / 22.5), 16);
            int m = bi % 4;  // 0: on an axis, 1: 22.5 past it, 2: 45, 3: 67.5
            String turn = switch (m) {
                case 0 -> "0";
                case 1 -> "m22";
                case 2 -> "p45";
                default -> "p22";
            };
            int f = Math.floorMod((m == 0 ? bi : m == 1 ? bi - 1 : m == 2 ? bi + 2 : bi + 1) / 4, 4);
            double bs = Math.toRadians(bi * 22.5);
            double depth = ((mx - x - 0.5) * Math.sin(bs) - (mz - z - 0.5) * Math.cos(bs)) * 16;
            int di = Math.max(0, Math.min(8, (int) Math.round((depth + 8) / 2)));
            out.add(new Cell(x, z, FACING[f], turn + "_" + di));
        }
        return out;
    }

    /** A drum wall from y0 to y1 of rd_{mat}; every `hoopEvery` blocks (0: none) the stave_hoop piece. */
    static void wall(Placer p, double cx, double cz, double r, int y0, int y1, String mat, int hoopEvery) {
        List<Cell> cells = ring(cx, cz, r);
        for (int y = y0; y <= y1; y++) {
            String m = hoopEvery > 0 && (y - y0) % hoopEvery == hoopEvery - 1 ? mat + "_hoop" : mat;
            for (Cell c : cells) {
                p.set(c.x(), y, c.z(), "rd_" + m + "_" + c.suffix() + "[facing=" + c.facing() + "]");
            }
        }
    }

    /**
     * A roof of rings from y up, a block a ring, the first with its outside at radius r: each ring's
     * courses step in half a block (profile letter s) or a whole one (m), and the top inside the last
     * ring is flat. Returns the y above the roof.
     */
    static int roof(Placer p, double cx, double cz, double r, String profile, int y, String mat) {
        double rr = r;
        for (int k = 0; k < profile.length(); k++) {
            char s = profile.charAt(k);
            Set<Long> rim = new HashSet<>();
            for (Cell c : ring(cx, cz, rr)) {
                p.set(c.x(), y + k, c.z(), "rd_" + mat + "_" + s + "_" + c.suffix() + "[facing=" + c.facing() + "]");
                rim.add(key(c.x(), c.z()));
            }
            rr -= s == 's' ? 0.5 : 1;
            if (k == profile.length() - 1) {
                int x0 = (int) Math.floor(cx - rr), x1 = (int) Math.ceil(cx + rr);
                int z0 = (int) Math.floor(cz - rr), z1 = (int) Math.ceil(cz + rr);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        if (Math.hypot(x + 0.5 - cx, z + 0.5 - cz) < rr - 0.3 && !rim.contains(key(x, z))) {
                            p.set(x, y + k, z, "rd_" + mat + "_flat");
                        }
                    }
                }
            }
        }
        return y + profile.length();
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    /** A silo dome's profile over radius r: steep at the eave, rounding over, a flat crown. */
    static String dome(double r) {
        return r < 2.5 ? "sm" : r < 4 ? "smm" : "ssmm";
    }

    /** A cone's profile over radius r at 45 degrees, to a small cap. */
    static String cone(double r) {
        return "m".repeat(Math.max(1, (int) Math.floor(r - 1)));
    }
}
