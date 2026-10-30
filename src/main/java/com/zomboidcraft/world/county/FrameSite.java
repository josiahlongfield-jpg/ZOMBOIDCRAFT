package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * One tile of a site too big to build in one go (the army airfield, the tank ranges): the site has
 * its own frame round the plan's spot (x = RX + fx, z = RZ + fz, no turn), each tile draws what of it
 * falls in its bounds through a placer clipped to the tile, every choice from a hash of where it is,
 * and the big pieces are built whole once in the frame and copied.
 *
 * <p>The tiles' variant is "kind@RX,RZ@fx,fz,side;..." (see {@link #tiles}), the gates the plan's
 * roads will come to.
 */
abstract class FrameSite extends Site {
    protected final int rx, rz;
    /** The frame, clipped to this tile. */
    protected final Placer a;
    /** This tile's extent in the frame. */
    protected final int fx0, fx1, fz0, fz1;
    protected final long seed;
    /** The gates in the frame: {fx, fz, side}, side 0 north, 1 east, 2 south, 3 west. */
    protected final List<int[]> gates;

    FrameSite(Place p, BlockSink out, long salt) {
        super(p, out);
        String[] parts = p.variant.split("@");
        String[] xz = parts[1].split(",");
        rx = Integer.parseInt(xz[0]);
        rz = Integer.parseInt(xz[1]);
        gates = new ArrayList<>();
        if (parts.length > 2) {
            for (String g : parts[2].split(";")) {
                if (!g.isEmpty()) {
                    String[] v = g.split(",");
                    gates.add(new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
                }
            }
        }
        BlockSink clip = new BlockSink() {
            @Override
            public void set(int x, int y, int z, String block) {
                if (inside(x, z)) {
                    out.set(x, y, z, block);
                }
            }

            @Override
            public String get(int x, int y, int z) {
                return inside(x, z) ? out.get(x, y, z) : null;
            }

            @Override
            public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
                if (inside((int) Math.floor(x), (int) Math.floor(z))) {
                    out.vehicle(x, y, z, yaw, models, seed);
                }
            }

            @Override
            public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
                if (inside((int) Math.floor(x), (int) Math.floor(z))) {
                    out.car(x, y, z, yaw, minLength, maxLength, seed);
                }
            }

            @Override
            public void mark(String what, int x, int y, int z) {
                if (inside(x, z)) {
                    out.mark(what, x, y, z);
                }
            }
        };
        a = new Placer(clip, rx - p.x0, 0, rz - p.z0, 0);
        fx0 = p.x0 - rx;
        fx1 = p.x1 - rx;
        fz0 = p.z0 - rz;
        fz1 = p.z1 - rz;
        seed = Noise.hash(salt, rx, rz);
    }

    protected double rnd(int fx, int fz, long salt) {
        return Noise.unit(Noise.hash(seed ^ salt, fx, fz));
    }

    protected boolean overlaps(int x0, int z0, int x1, int z1) {
        return fx1 >= x0 && fx0 <= x1 && fz1 >= z0 && fz0 <= z1;
    }

    private static final Map<String, Voxels> PIECES = new LinkedHashMap<>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Voxels> eldest) {
            return size() > 4;
        }
    };

    /** A piece built whole in the frame once (by build, from its own seed), kept while the tiles round it are made. */
    protected Voxels piece(String name, long salt, java.util.function.BiConsumer<Placer, SplittableRandom> build) {
        String key = name + "@" + rx + "," + rz;
        synchronized (PIECES) {
            Voxels v = PIECES.get(key);
            if (v == null) {
                v = new Voxels();
                build.accept(new Placer(v, 0, 0, 0, 0), new SplittableRandom(seed ^ salt));
                PIECES.put(key, v);
            }
            return v;
        }
    }

    /** Copies the part of a piece built in the frame that falls in this tile. */
    protected void copy(Voxels v) {
        List<String> palette = v.palette();
        v.forEach((x, y, z, id) -> {
            if (x >= fx0 && x <= fx1 && z >= fz0 && z <= fz1) {
                a.set(x, y, z, palette.get(id));
            }
        });
        for (Voxels.Car c : v.cars) {
            if (c.models() == null) {
                a.car(c.x(), c.y(), c.z(), c.yaw(), c.minLength(), c.maxLength(), c.seed());
            } else {
                a.vehicle(c.x(), c.y(), c.z(), c.yaw(), c.models(), c.seed());
            }
        }
        for (Voxels.Mark m : v.marks) {
            if (m.x() >= fx0 && m.x() <= fx1 && m.z() >= fz0 && m.z() <= fz1) {
                a.mark(m.what(), m.x(), m.y(), m.z());
            }
        }
    }

    // ------------------------------------------------------------------ laying out the tiles

    /**
     * The site's tiles over the frame's box fx0..fx1, fz0..fz1 round the spot, tileW square, all at
     * the level of the ground under the whole (none if any of it is water). Each tile's variant
     * carries the gates: Layout breaks a plan road where it comes within 24 of a place and runs it to
     * the middle of the side facing the way it came (its point six steps back), so this walks each
     * road the same way.
     */
    static List<Place> tiles(String kind, Plan.Spot s, County county, Plan plan, long h, int x0, int z0, int x1, int z1, int tileW) {
        int ox = s.x(), oz = s.z();
        double sum = 0;
        int n = 0;
        for (int i = 0; i <= 12; i++) {
            for (int j = 0; j <= 8; j++) {
                double x = ox + x0 + (x1 - x0) * i / 12.0, z = oz + z0 + (z1 - z0) * j / 8.0;
                double g = county.terrain.height(x, z);
                if (Terrain.isWater(g - 1)) {
                    return List.of();
                }
                sum += g;
                n++;
            }
        }
        int level = Math.max((int) Math.round(sum / n), Terrain.WATER + 2);
        List<int[]> boxes = new ArrayList<>();
        for (int a = x0; a <= x1; a += tileW) {
            for (int b = z0; b <= z1; b += tileW) {
                boxes.add(new int[] {ox + a, oz + b, ox + Math.min(x1, a + tileW - 1), oz + Math.min(z1, b + tileW - 1)});
            }
        }
        StringBuilder variant = new StringBuilder(kind + "@" + ox + "," + oz + "@");
        for (int[] g : gates(boxes, plan)) {
            variant.append(g[0] - ox).append(',').append(g[1] - oz).append(',').append(g[2]).append(';');
        }
        List<Place> out = new ArrayList<>();
        for (int[] b : boxes) {
            out.add(new Place(PlaceType.LANDMARK, variant.toString(), b[0], b[1], b[2], b[3], level, h, -1, -1));
        }
        return out;
    }

    private static List<int[]> gates(List<int[]> boxes, Plan plan) {
        List<int[]> out = new ArrayList<>();
        for (Plan.Line rd : plan.roads) {
            List<double[]> pts = new ArrayList<>();
            Plan.Shape sh = rd.shape;
            for (int i = 0; i + 1 < sh.count(); i++) {
                double ax = sh.x(i), az = sh.z(i), bx = sh.x(i + 1), bz = sh.z(i + 1);
                int k = Math.max(1, (int) Math.round(Math.hypot(bx - ax, bz - az) / Road.STEP));
                for (int j = 0; j < k; j++) {
                    pts.add(new double[] {ax + (bx - ax) * j / k, az + (bz - az) * j / k});
                }
            }
            int[] prev = null;
            for (int i = 0; i < pts.size(); i++) {
                int[] in = boxAt(boxes, pts.get(i));
                if (in != null && prev == null && i > 0) {
                    out.add(gate(in, pts.get(Math.max(0, i - 7))));
                } else if (in == null && prev != null) {
                    out.add(gate(prev, pts.get(Math.min(pts.size() - 1, i + 6))));
                }
                prev = in;
            }
        }
        return out;
    }

    private static int[] boxAt(List<int[]> boxes, double[] q) {
        int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[1]);
        for (int[] b : boxes) {
            if (x >= b[0] - 24 && x <= b[2] + 24 && z >= b[1] - 24 && z <= b[3] + 24) {
                return b;
            }
        }
        return null;
    }

    /** Place.gate's point on the side of the box facing toward. */
    private static int[] gate(int[] b, double[] toward) {
        int w = b[2] - b[0] + 1, d = b[3] - b[1] + 1;
        double cx = b[0] + w / 2.0, cz = b[1] + d / 2.0, dx = toward[0] - cx, dz = toward[1] - cz;
        int side = Math.abs(dx) * d > Math.abs(dz) * w ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
        return switch (side) {
            case 0 -> new int[] {b[0] + w / 2, b[1], 0};
            case 1 -> new int[] {b[2], b[1] + d / 2, 1};
            case 2 -> new int[] {b[0] + w / 2, b[3], 2};
            default -> new int[] {b[0], b[1] + d / 2, 3};
        };
    }
}
