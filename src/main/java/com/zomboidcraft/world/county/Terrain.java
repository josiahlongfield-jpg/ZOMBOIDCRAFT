package com.zomboidcraft.world.county;

/**
 * The natural land before anyone built on it, modelled on the Kentucky of Knox Country: low
 * rolling farmland and woods, steep wooded "knobs" in places, wide lakes and bays, and rivers
 * winding through shallow valleys. Everything is a pure function of the seed and the column.
 */
public final class Terrain {
    /** The water surface: rivers, lakes and bays are filled up to this height. */
    public static final int WATER = 62;

    private final Noise hills, detail, warp, knobs, knobMask, lakes, rivers, forest, moist, urban;

    public Terrain(long seed) {
        hills = new Noise(seed ^ 0x51ED2701L);
        detail = new Noise(seed ^ 0x2C1B3C6DL);
        warp = new Noise(seed ^ 0x297A2D39L);
        knobs = new Noise(seed ^ 0x6C8E9CF5L);
        knobMask = new Noise(seed ^ 0x1B873593L);
        lakes = new Noise(seed ^ 0x7F4A7C15L);
        rivers = new Noise(seed ^ 0x68E31DA4L);
        forest = new Noise(seed ^ 0x3C6EF372L);
        moist = new Noise(seed ^ 0x0BADC0DEL);
        urban = new Noise(seed ^ 0x5EED5EEDL);
    }

    static double smooth(double e0, double e1, double v) {
        double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Below about -0.36 is open water: a lake, reservoir or bay. */
    public double lake(double x, double z) {
        return lakes.fbm(x, z, 2600, 3) + 0.04 * detail.fbm(x, z, 300, 2) - 0.10 + 0.5 * (1 - spawnCalm(x, z));
    }

    /** 0 at the world origin rising to 1 by 500 blocks out: the spawn town is never under water. */
    static double spawnCalm(double x, double z) {
        return smooth(260, 520, Math.hypot(x, z));
    }

    /** 0 away from rivers, rising to 1 in the middle of the channel. */
    public double river(double x, double z) {
        double wx = x + 260 * warp.fbm(x, z, 1400, 2), wz = z + 260 * warp.fbm(x + 9000, z - 4000, 1400, 2);
        double r = rivers.ridge(wx, wz, 4200, 2);
        // the channel narrows and widens along its course
        double width = 0.0045 + 0.003 * (rivers.get(x / 1300.0, z / 1300.0) + 1);
        return smooth(1 - width * 8, 1 - width, r) * spawnCalm(x, z);
    }

    /** True where the river channel itself runs (not just its valley). */
    public boolean riverChannel(double x, double z) {
        return river(x, z) > 0.86;
    }

    /** The natural ground height, before any town flattens it or a road cuts through it. */
    public double height(double x, double z) {
        double wx = x + 140 * warp.fbm(x, z, 800, 2), wz = z + 140 * warp.fbm(x - 7000, z + 3000, 800, 2);
        double h = 70 + 7 * hills.fbm(wx, wz, 560, 4) + 1.6 * detail.fbm(x, z, 48, 2);
        // knobs: steep wooded hills in some districts
        double mask = smooth(0.05, 0.45, knobMask.fbm(x, z, 3400, 2));
        if (mask > 0) {
            double k = Math.max(0, knobs.ridge(wx, wz, 620, 3) - 0.42) / 0.58;
            h += mask * 30 * Math.pow(k, 1.7);
        }
        // the river valley, then the channel
        double r = river(x, z);
        if (r > 0) {
            h = lerp(h, WATER + 1.5, smooth(0, 0.6, r));
            if (r > 0.75) {
                h = lerp(h, WATER - 6, smooth(0.75, 1, r));
            }
        }
        // lakes and bays: a gentle shore down to deep water
        double l = lake(x, z);
        if (l < -0.28) {
            h = lerp(h, WATER - 14, smooth(-0.30, -0.44, l));
            h = Math.min(h, lerp(h, WATER + 1, smooth(-0.28, -0.33, l)));
        }
        return h;
    }

    /** How thick the woods are here, 0 (open fields) to 1 (dense forest). */
    public double forest(double x, double z) {
        double f = forest.fbm(x, z, 900, 3) * 0.8 + 0.2 * forest.fbm(x, z, 120, 2);
        return smooth(-0.25, 0.25, f);
    }

    /** Damp ground: swampy near water, dry on hilltops. -1 to 1. */
    public double moisture(double x, double z) {
        return moist.fbm(x, z, 700, 3);
    }

    /** How built-up the district is, -1 (deep country) to 1 (city). */
    public double urban(double x, double z) {
        return urban.fbm(x, z, 5200, 2) * 1.6;
    }

    public static boolean isWater(double h) {
        return h < WATER + 0.5;
    }
}
