package com.zomboidcraft.world.county;

/**
 * The natural land before anyone built on it, modelled on the Kentucky of Knox Country: low
 * rolling farmland and woods, steep wooded "knobs" in places, wide lakes and bays, and rivers
 * winding through shallow valleys. Everything is a pure function of the seed and the column.
 *
 * With the county {@link Plan}, the plan draws the big shapes and the noise only the small ones:
 * the sea and its beaches, the Calder and its two arms round Grayhaven Island, the reservoir, the
 * Black Pine mountains and wooded hills, the marshes, and the flat ground the city stands on, its
 * banks walled where they meet the water.
 */
public final class Terrain {
    /** The water surface: rivers, lakes and bays are filled up to this height. */
    public static final int WATER = 62;
    /** The city's ground inside the wall and on the island: a seawall five blocks above the water. */
    public static final int CITY = WATER + 4;
    /**
     * The one water that stands higher: the reservoir behind Black Pine Dam, held this far up by
     * the wall (see {@link #reservoir}); it is filled up to here the way the rest is up to WATER.
     */
    public static final int RESERVOIR = WATER + 24;
    /**
     * The reservoir's shape round the plan's lake: the dam's upstream face FACE north of its spot,
     * the arm from the lake down to it ARM out each side of its line; the shore's slope SHORE wide
     * up to the rim of hills that holds the water in, easing back to the land RIM out.
     */
    static final int FACE = 8, ARM = 75, SHORE = 15, RIM = 280;

    private final Noise hills, detail, warp, knobs, knobMask, lakes, rivers, forest, moist, urban;
    /** The county plan, or null for the endless county the seed alone lays out. */
    public final Plan plan;

    public Terrain(long seed) {
        this(seed, null);
    }

    /** The reservoir lake and the dam's spot, when the plan has both; the arm's two ends. */
    private final Plan.Oval resLake;
    private final double damX, damZ, armX0, armZ0, armX1, armZ1;

    public Terrain(long seed, Plan plan) {
        this.plan = plan;
        Plan.Oval lake = null;
        Plan.Spot dam = plan == null ? null : plan.byId.get("dam");
        if (plan != null) {
            for (Plan.Oval o : plan.lakes) {
                if (o.id().equals("reservoir")) {
                    lake = o;
                }
            }
        }
        if (lake != null && dam != null) {
            resLake = lake;
            damX = dam.x();
            damZ = dam.z();
            // the arm leaves the lake three quarters of the way out toward the dam's end of it
            armX0 = lake.x() + 0.75 * lake.rx() * Math.signum(damX - lake.x());
            armZ0 = lake.z();
            armX1 = damX;
            armZ1 = damZ - FACE - 10;
        } else {
            resLake = null;
            damX = damZ = armX0 = armZ0 = armX1 = armZ1 = 0;
        }
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
        if (plan != null) {
            double c = Math.min(plan.coast(x, z), plan.lake(x, z));
            return c < 0 ? -1 : c < 40 ? -0.3 : 0.1;
        }
        return lakes.fbm(x, z, 2600, 3) + 0.04 * detail.fbm(x, z, 300, 2) - 0.10 + 0.5 * (1 - spawnCalm(x, z));
    }

    /** 0 at the world origin rising to 1 by 500 blocks out: the spawn town is never under water. */
    static double spawnCalm(double x, double z) {
        return smooth(260, 520, Math.hypot(x, z));
    }

    /** 0 away from rivers, rising to 1 in the middle of the channel. */
    public double river(double x, double z) {
        if (plan != null) {
            return smooth(110, -20, plan.river(x, z, null));
        }
        double wx = x + 260 * warp.fbm(x, z, 1400, 2), wz = z + 260 * warp.fbm(x + 9000, z - 4000, 1400, 2);
        double r = rivers.ridge(wx, wz, 4200, 2);
        // the channel narrows and widens along its course
        double width = 0.0045 + 0.003 * (rivers.get(x / 1300.0, z / 1300.0) + 1);
        return smooth(1 - width * 8, 1 - width, r) * spawnCalm(x, z);
    }

    /** True where the river channel itself runs (not just its valley). */
    public boolean riverChannel(double x, double z) {
        if (plan != null) {
            return plan.river(x, z, null) < 0;
        }
        return river(x, z) > 0.86;
    }

    /** The natural ground height, before any town flattens it or a road cuts through it. */
    public double height(double x, double z) {
        if (plan != null) {
            return planned(x, z);
        }
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
        double v = smooth(-0.25, 0.25, f);
        if (plan != null) {
            // the plan's forests and mountains are thick with trees, its farm country open, the city bare
            double wood = Math.max(deepest(plan.forests, x, z), Plan.depth(plan.mountains, x, z));
            v = lerp(v, Math.max(v, 0.82 + 0.18 * v), smooth(-200, 300, wood));
            v *= 1 - 0.55 * smooth(-200, 200, deepest(plan.farms, x, z));
            v *= 1 - 0.75 * Math.max(city(x, z), 0.6 * sprawl(x, z));
        }
        return v;
    }

    private static double deepest(java.util.List<Plan.Shape> shapes, double x, double z) {
        double best = -1e9;
        for (Plan.Shape s : shapes) {
            best = Math.max(best, Plan.depth(s, x, z));
        }
        return best;
    }

    /** 1 on the city's ground (inside the wall, or the middle of an inland city), easing to 0 outside. */
    public double city(double x, double z) {
        double c = smooth(-400, 0, Plan.depth(plan.core, x, z));
        for (java.util.Map.Entry<String, Double> u : plan.urban.entrySet()) {
            Plan.Spot s = plan.byId.get(u.getKey());
            if (s.type() == PlaceType.DOWNTOWN) {
                c = Math.max(c, smooth(u.getValue() * 1.2, u.getValue() * 0.7, Math.hypot(x - s.x(), z - s.z())));
            }
        }
        return c;
    }

    /** 1 in the suburbs round the wall and the towns, easing to 0 outside them. */
    public double sprawl(double x, double z) {
        double c = smooth(-300, 100, Plan.depth(plan.sprawl, x, z));
        for (java.util.Map.Entry<String, Double> u : plan.urban.entrySet()) {
            Plan.Spot s = plan.byId.get(u.getKey());
            c = Math.max(c, smooth(u.getValue() * 1.7, u.getValue() * 1.3, Math.hypot(x - s.x(), z - s.z())));
        }
        return c;
    }

    /** The ground the plan asks for: the noise's hills, reshaped by the plan's land and water. */
    private double planned(double x, double z) {
        double wx = x + 140 * warp.fbm(x, z, 800, 2), wz = z + 140 * warp.fbm(x - 7000, z + 3000, 800, 2);
        double h = 68 + 6 * hills.fbm(wx, wz, 560, 4) + 1.6 * detail.fbm(x, z, 48, 2);
        double k = Math.max(0, knobs.ridge(wx, wz, 620, 3) - 0.35) / 0.65;
        // the Black Pine mountains: high wooded ridges, rising out of the foothills
        double mtn = smooth(-500, 600, Plan.depth(plan.mountains, x, z));
        h += mtn * (16 + 58 * Math.pow(k, 1.4) + 10 * hills.fbm(wx + 4000, wz, 1400, 2));
        // the forests stand on knobby hills; the open country keeps the odd knob of its own
        double woods = smooth(-300, 400, deepest(plan.forests, x, z));
        double cityW = city(x, z), sprawlW = sprawl(x, z);
        double mask = Math.max(woods, 0.5 * smooth(0.05, 0.45, knobMask.fbm(x, z, 3400, 2))) * (1 - mtn) * (1 - Math.max(cityW, sprawlW));
        h += mask * 24 * Math.pow(k, 1.7);
        // the city stands on flat ground, the suburbs on gentle ground easing up to it
        h = lerp(h, CITY + 2 + 2 * hills.fbm(wx, wz, 900, 2), 0.7 * sprawlW * (1 - cityW));
        h = lerp(h, CITY, cityW);
        // marshes: sodden flats barely above the water, pools in the hollows
        double marsh = smooth(-150, 150, Plan.depth(plan.marsh, x, z));
        if (marsh > 0) {
            h = lerp(h, WATER + 0.3 + 1.4 * detail.fbm(x, z, 30, 2), marsh);
        }
        boolean walled = cityW > 0.5;
        // rivers: a valley down to the banks out in the country, a seawall in the city
        Plan.Line[] which = new Plan.Line[1];
        double r = plan.river(x, z, which);
        if (r < 110) {
            double w = which[0].width;
            if (!walled) {
                h = lerp(h, WATER + 1.5, smooth(Math.min(110, 30 + w), 3, r));
            }
            if (r < 0) {
                h = lerp(WATER - 1.5, WATER - Math.min(14, 3 + w / 20), smooth(0, Math.min(w / 2, 40), -r));
            }
        }
        // the sea: beaches and low shores in the country, the quays in the city, deep water offshore
        double c = plan.coast(x, z);
        if (c < 160) {
            if (!walled) {
                h = lerp(h, WATER + 1, smooth(140, 2, c));
            }
            if (c < 0) {
                h = lerp(WATER - 2, WATER - 24, smooth(0, 320, -c));
            }
        }
        Plan.Oval isl = null;
        for (Plan.Oval o : plan.islands) {
            if (o.at(x, z) < 1.05) {
                isl = o;
            }
        }
        if (isl != null) {
            double e = isl.at(x, z);
            double top = isl.cliff() ? WATER + 17 + 3 * hills.fbm(wx, wz, 200, 2) : WATER + 2.5 + detail.fbm(x, z, 60, 2);
            h = lerp(WATER - 5, top, smooth(1.05, isl.cliff() ? 0.93 : 0.8, e));
        }
        // the reservoir behind the dam
        if (resLake != null) {
            return dammed(h, x, z);
        }
        double l = plan.lake(x, z);
        if (l < 80) {
            h = lerp(h, WATER + 1.5, smooth(80, 2, l));
            if (l < 0) {
                h = lerp(WATER - 2, WATER - 16, smooth(0, 120, -l));
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ Black Pine Dam's reservoir

    /** How far outside the reservoir's water (negative inside) the lake and its arm put (x, z), the dam's cut aside. */
    private double reach(double x, double z) {
        double lake = (resLake.at(x, z) - 1) * Math.min(resLake.rx(), resLake.rz());
        double dx = armX1 - armX0, dz = armZ1 - armZ0;
        double t = Math.max(0, Math.min(1, ((x - armX0) * dx + (z - armZ0) * dz) / (dx * dx + dz * dz)));
        double arm = Math.hypot(x - armX0 - t * dx, z - armZ0 - t * dz) - ARM;
        return Math.min(lake, arm);
    }

    /**
     * How far outside the reservoir (negative inside) (x, z) is: the lake, the arm down to the dam,
     * cut off at the dam's upstream face (the dam stands south of its water); 1e9 with no dam.
     */
    public double reservoir(double x, double z) {
        if (resLake == null) {
            return 1e9;
        }
        return Math.max(reach(x, z), z - (damZ - FACE));
    }

    /** The water's surface over (x, z): the reservoir's behind the dam, everywhere else the sea's. */
    public int water(double x, double z) {
        return resLake != null && z <= damZ - FACE && reservoir(x, z) < SHORE ? RESERVOIR : WATER;
    }

    /** Whether ground at h over (x, z) is under water there. */
    public boolean wet(double x, double z, double h) {
        return h < water(x, z) + 0.5;
    }

    /**
     * The land round the raised reservoir: its bed deepening from the shore, the shore up to a rim
     * of hills a few blocks over the water all the way round (higher land left as it is), easing
     * back down to the land around; below the dam the Calder's gorge, its walls rising from the
     * river to the rim either side of the wall and falling away downstream.
     */
    private double dammed(double h, double x, double z) {
        double d = reservoir(x, z);
        if (d < 0) {
            return lerp(RESERVOIR - 1.5, RESERVOIR - 22, smooth(0, 110, -reach(x, z)));
        }
        if (d > RIM) {
            return h;
        }
        double rim = RESERVOIR + 3 + 1.5 * detail.fbm(x, z, 40, 2);
        double hold = lerp(rim, h, smooth(SHORE, RIM, d));
        if (z > damZ - FACE) {
            double w = smooth(0, 50, plan.river(x, z, null)) * (1 - smooth(200, 520, Math.hypot(x - damX, z - damZ)));
            return Math.max(h, lerp(h, hold, w));
        }
        if (d < SHORE) {
            return lerp(RESERVOIR - 1.5, rim, smooth(0, SHORE, d));
        }
        return Math.max(h, hold);
    }

    /** Damp ground: swampy near water, dry on hilltops. -1 to 1. */
    public double moisture(double x, double z) {
        return moist.fbm(x, z, 700, 3);
    }

    /** How built-up the district is, -1 (deep country) to 1 (city). */
    public double urban(double x, double z) {
        if (plan != null) {
            return city(x, z) > 0.5 ? 1 : sprawl(x, z) > 0.5 ? 0.6 : -0.4;
        }
        return urban.fbm(x, z, 5200, 2) * 1.6;
    }

    public static boolean isWater(double h) {
        return h < WATER + 0.5;
    }
}
