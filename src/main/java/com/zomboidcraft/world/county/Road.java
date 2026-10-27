package com.zomboidcraft.world.county;

/**
 * A road between two places, as a polyline sampled every {@link #STEP} blocks with the height of
 * the road surface at each point (already smoothed, so it rises and falls gently and crosses
 * rivers on a bridge).
 */
public final class Road {
    public static final int STEP = 4;

    public enum Kind {
        /** Four lanes, double yellow line, dashed lanes, guard rails. */
        HIGHWAY(7),
        /** Two lanes with a centre line and gravel shoulders. */
        COUNTY(4),
        /** A gravel or dirt farm track. */
        TRACK(2);

        public final int half;

        Kind(int half) {
            this.half = half;
        }
    }

    public final Kind kind;
    public final double[] xs, zs, ys;
    public final int minX, minZ, maxX, maxZ;
    public final long seed;
    /** The places at its two ends. */
    public final Place from, to;

    Road(Kind kind, double[] xs, double[] zs, double[] ys, long seed, Place from, Place to) {
        this.kind = kind;
        this.from = from;
        this.to = to;
        this.xs = xs;
        this.zs = zs;
        this.ys = ys;
        this.seed = seed;
        double a = Double.MAX_VALUE, b = Double.MAX_VALUE, c = -Double.MAX_VALUE, d = -Double.MAX_VALUE;
        for (int i = 0; i < xs.length; i++) {
            a = Math.min(a, xs[i]);
            b = Math.min(b, zs[i]);
            c = Math.max(c, xs[i]);
            d = Math.max(d, zs[i]);
        }
        int m = kind.half + 12;
        minX = (int) Math.floor(a) - m;
        minZ = (int) Math.floor(b) - m;
        maxX = (int) Math.ceil(c) + m;
        maxZ = (int) Math.ceil(d) + m;
    }

    public boolean near(int x0, int z0, int x1, int z1) {
        return maxX >= x0 && minX <= x1 && maxZ >= z0 && minZ <= z1;
    }

    /** The nearest point on the road to a column. */
    public static final class Hit {
        /** Signed distance from the centre line (positive to the right of travel). */
        public double offset;
        public double distance = Double.MAX_VALUE;
        /** Road surface height there. */
        public double y;
        /** Direction of travel there, as a unit vector. */
        public double dx, dz;
        /** Distance along the road from its start. */
        public double along;
        public Road road;
    }

    /** Updates hit if this road passes closer to (x, z) than whatever it holds. */
    public void nearest(double x, double z, Hit hit) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) {
            return;
        }
        for (int i = 0; i + 1 < xs.length; i++) {
            double ax = xs[i], az = zs[i], bx = xs[i + 1], bz = zs[i + 1];
            double vx = bx - ax, vz = bz - az;
            double len2 = vx * vx + vz * vz;
            double t = len2 == 0 ? 0 : ((x - ax) * vx + (z - az) * vz) / len2;
            t = Math.max(0, Math.min(1, t));
            double px = ax + vx * t, pz = az + vz * t;
            double d = Math.hypot(x - px, z - pz);
            if (d < hit.distance) {
                double len = Math.sqrt(len2);
                hit.distance = d;
                hit.dx = len == 0 ? 1 : vx / len;
                hit.dz = len == 0 ? 0 : vz / len;
                hit.offset = (x - px) * -hit.dz + (z - pz) * hit.dx;
                hit.y = ys[i] + (ys[i + 1] - ys[i]) * t;
                hit.along = (i + t) * STEP;
                hit.road = this;
            }
        }
    }
}
