package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Cape Merrow Lighthouse (Josiah, 2026-10-30: the coast and industry places, the lighthouse
 * second), after refs/coast and refs/port/lighthouse: the light station on the headland past the
 * fishing hamlet, raised on its rock over the sea.
 * <ul>
 * <li>the headland: the land brought up level round the station and the rest left as it lies,
 * the sea cliff where it meets the water, its broken rock, the boulders at its foot and the
 * stacks standing off it (cliff-headland);</li>
 * <li>the tower: a whitewashed masonry cone on its granite plinth, the iron stair winding up round
 * its newel to the watch room, the gallery on its corbels with the X-lattice rail, the lantern
 * with its diagonal astragals over the first-order lens on its red pedestal, the red dome, the
 * ventilator ball and the rod (port/lighthouse, lighthouse-interior-stairs-lens);</li>
 * <li>the entry house on the tower's foot, the keepers' house and the assistant's cottage in
 * white board with red roofs and their picket fences (lighthouse-keepers-house), the brick oil
 * house, the fog signal on the cliff with its horns to the sea, the radio mast, the water tank,
 * the flagpole;</li>
 * <li>the stone wall round the station and its white field gate, the drive from the road, the car
 * park with the board about the light, the coastal path along the cliff top behind its rail
 * fence, the fingerpost.</li>
 * </ul>
 * The last keeper kept the light going after the power went, from the oil in the oil house; the
 * tower's door is barricaded from inside and the lens still turns at night.
 *
 * <p>Built where the land lies ({@link Place#natural}), like {@link Dam}: the place's level is
 * the station's ground and the builder reads the county's terrain to raise or cut every column to
 * it and to find the sea. Its frame puts the plan's spot at (0, 0), x east toward the sea.
 */
final class Cape extends Site {
    /** The frame's bounds round the spot. */
    static final int FX0 = -90, FX1 = 200, FZ0 = -150, FZ1 = 150;
    /** The station's ground, twelve over the sea. */
    static final int LEVEL = Terrain.WATER + 12;
    /** Natural ground under this (frame y) is the shore the cliff stops at. */
    static final double SHORE = 64.6 - LEVEL;
    /** The tower's centre; the watch room's floor and its gallery; the lantern's floor. */
    static final int TX = 110, TZ = 0, WATCH = 38, LANTERN = 45;
    /** The tower's radius at its foot and under the gallery. */
    static final double R0 = 6.5, R1 = 5.6;

    private final County county;

    Cape(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    /** The station's place: the frame round the spot, its level the headland's top, the land left as it lies. */
    static Place place(Plan.Spot s, long h) {
        Place p = new Place(PlaceType.LANDMARK, "cape", s.x() + FX0, s.z() + FZ0, s.x() + FX1, s.z() + FZ1, LEVEL, h, -1, -1);
        p.natural = true;
        return p;
    }

    @Override
    void build() {
        int[][] g = new int[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
        int[][] wet = new int[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
        int cx = p.x0 - FX0, cz = p.z0 - FZ0;
        List<Place> near = county == null ? List.of() : county.placesNear(p.x0, p.z0, p.x1, p.z1, County.SKIRT);
        for (int x = FX0; x <= FX1; x++) {
            for (int z = FZ0; z <= FZ1; z++) {
                if (county == null) {
                    // a preview without the county: a slope down to the sea
                    g[x - FX0][z - FZ0] = x < 130 ? -2 - x / 20 : -12 - (x - 130) / 4;
                    wet[x - FX0][z - FZ0] = Terrain.WATER - 1 - LEVEL;
                    continue;
                }
                int wx = cx + x, wz = cz + z;
                g[x - FX0][z - FZ0] = (int) Math.floor(county.ground(wx, wz, near)) - p.level;
                wet[x - FX0][z - FZ0] = county.terrain.water(wx, wz) - 1 - p.level;
            }
        }
        Placer a = new Placer(out, -FX0, 0, -FZ0, 0);
        new Station(a, new SplittableRandom(p.seed), g, wet, new Noise(p.seed)).build();
    }

    /** The station drawn in its frame. */
    static final class Station extends Grounds {
        private final int[][] g, wet;
        private final Noise noise;
        private final Camp camp;
        /** The headland's top per column after the land is laid (frame y), and which columns are sea. */
        private final int[][] top;
        private final boolean[][] sea;

        Station(Placer p, SplittableRandom rng, int[][] g, int[][] wet, Noise noise) {
            super(p, rng, FX1 - FX0 + 1, FZ1 - FZ0 + 1, 0.4, 0.3, 0.0);
            this.g = g;
            this.wet = wet;
            this.noise = noise;
            this.camp = new Camp(p, rng, 0.5);
            this.top = new int[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
            this.sea = new boolean[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
        }

        @Override
        String signs() {
            return "cm_sgn_";
        }

        private int nat(int x, int z) {
            return g[clampX(x) - FX0][clampZ(z) - FZ0];
        }

        private int waterTop(int x, int z) {
            return wet[clampX(x) - FX0][clampZ(z) - FZ0];
        }

        /** The ground's top at (x, z) once the headland is laid. */
        int ground(int x, int z) {
            return top[clampX(x) - FX0][clampZ(z) - FZ0];
        }

        boolean isSea(int x, int z) {
            return sea[clampX(x) - FX0][clampZ(z) - FZ0];
        }

        private static boolean inBox(int x, int z, int x0, int z0, int x1, int z1) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        private static int clampX(int x) {
            return Math.max(FX0, Math.min(FX1, x));
        }

        private static int clampZ(int z) {
            return Math.max(FZ0, Math.min(FZ1, z));
        }

        void build() {
            land();
            cliff();
            stacks();
            drive();
            enclosure();
            tower();
            entry();
            keepers();
            oilHouse();
            fogSignal();
            yard();
            coastPath();
            landing();
            heath();
            story();
        }

        // ------------------------------------------------------------------ the headland

        /** How far the station's levelling holds at (x, z): whole over the core, easing out to the frame's edges. */
        private static double hold(int x, int z) {
            double wx = Terrain.smooth(FX0, -60, x);
            double wz = Terrain.smooth(FZ1, 115, Math.abs(z));
            return Math.min(wx, wz);
        }

        /**
         * The land: each column brought up (or cut down) to the station's level where it holds,
         * easing back to the land as it lies; the sea side of the shore left alone. Rock shows
         * through where the ground is cut or built high, soil and grass over the rest.
         */
        private void land() {
            for (int x = FX0; x <= FX1; x++) {
                for (int z = FZ0; z <= FZ1; z++) {
                    int n = nat(x, z);
                    // the cliff's line, a rounded point with bays bitten out of it; below it the land as it lies
                    boolean water = x > cliffLine(z) || n < SHORE - 2;
                    sea[x - FX0][z - FZ0] = water;
                    int t = water ? n : (int) Math.round(Terrain.lerp(n, 0, hold(x, z)));
                    top[x - FX0][z - FZ0] = t;
                }
            }
            // the cliff's lip: broken back a block or two where it's highest
            for (int x = FX0 + 1; x < FX1; x++) {
                for (int z = FZ0 + 1; z < FZ1; z++) {
                    if (!isSea(x, z) && seaNear(x, z, 1) && ground(x, z) > nat(x, z) + 3 && chance() < 0.45) {
                        top[x - FX0][z - FZ0] -= 1 + rng.nextInt(2);
                    }
                }
            }
            for (int x = FX0; x <= FX1; x++) {
                for (int z = FZ0; z <= FZ1; z++) {
                    if (isSea(x, z)) {
                        continue;
                    }
                    int n = nat(x, z), t = ground(x, z);
                    boolean face = seaNear(x, z, 3) || Math.abs(t - n) > 5 && seaNear(x, z, 6);
                    for (int y = Math.min(n, t) - 2; y < t; y++) {
                        set(x, y, z, face || y < t - 3 ? rock(x, y, z) : "minecraft:dirt");
                    }
                    double r = chance();
                    set(x, t, z, face && seaNear(x, z, 1) ? (r < 0.5 ? rock(x, t, z) : "minecraft:coarse_dirt")
                            : r < 0.06 ? "minecraft:coarse_dirt" : r < 0.09 ? "minecraft:moss_block" : "minecraft:grass_block");
                    for (int y = t + 1; y <= Math.max(n, t) + 1; y++) {
                        set(x, y, z, "air");
                    }
                    if (!face && chance() < 0.18) {
                        set(x, t + 1, z, r < 0.5 ? "weeds_tall" : "grass_tuft");
                    }
                }
            }
        }

        /** Where the cliff stands at z: furthest out off the tower, curving back each side, bitten into bays. */
        private double cliffLine(int z) {
            double q = z / 72.0;
            return 140 - q * q * 34 + noise.fbm(0, z, 14, 2) * 5 + noise.fbm(7, z, 4, 2) * 1.5;
        }

        private boolean seaNear(int x, int z, int reach) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (isSea(x + dx, z + dz)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** The headland's rock: grey stone and andesite in beds, tuff seams, moss low down by the sea. */
        private String rock(int x, int y, int z) {
            double n = noise.fbm(x * 0.6, y * 1.8 + z * 0.4, 7, 2);
            if (y < -9 && chance() < 0.35) {
                return "minecraft:mossy_cobblestone";
            }
            return n > 0.3 ? "minecraft:tuff" : n > 0.0 ? "minecraft:andesite" : n > -0.35 ? "minecraft:stone" : "minecraft:cobblestone";
        }

        /** The boulders fallen at the cliff's foot, some awash, some standing clear of the water. */
        private void cliff() {
            for (int x = FX0 + 2; x < FX1 - 2; x++) {
                for (int z = FZ0 + 2; z < FZ1 - 2; z++) {
                    if (!isSea(x, z) || hold(x, z) < 0.3 || !landNear(x, z, 5)) {
                        continue;
                    }
                    if (!landNear(x, z, 2) || chance() > 0.4) {
                        if (nat(x, z) >= waterTop(x, z) && (landNear(x, z, 3) || chance() < 0.5)) {
                            set(x, nat(x, z), z, any("minecraft:gravel", "minecraft:gravel", "minecraft:cobblestone", "minecraft:andesite", "mu_gravel"));
                            set(x, nat(x, z) + 1, z, chance() < 0.08 ? rock(x, nat(x, z) + 1, z) : "air");
                        }
                        continue;
                    }
                    int y0 = nat(x, z) + 1, y1 = waterTop(x, z) + rng.nextInt(4) - 1;
                    if (nat(x, z) >= waterTop(x, z)) {
                        // a dry foot: shingle, and the fallen blocks lying on it
                        set(x, y0 - 1, z, any("minecraft:gravel", "minecraft:gravel", "minecraft:cobblestone", "mu_gravel"));
                        y1 = chance() < 0.4 ? y0 + rng.nextInt(2) + (landNear(x, z, 1) ? 1 : 0) : y0 - 1;
                    }
                    for (int y = y0; y <= y1; y++) {
                        set(x, y, z, rock(x, y, z));
                    }
                }
            }
        }

        private boolean landNear(int x, int z, int reach) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (!isSea(x + dx, z + dz)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /** The stacks standing off the point, worn round, the gulls' white on their tops. */
        private void stacks() {
            int[][] at = {{160, -46, 3, 4}, {172, 18, 2, 7}, {152, 64, 3, 2}, {186, -10, 1, 0}};
            for (int[] s : at) {
                if (!isSea(s[0], s[1])) {
                    continue;
                }
                int rad = s[2], up = s[3];
                for (int dx = -rad - 1; dx <= rad + 1; dx++) {
                    for (int dz = -rad - 1; dz <= rad + 1; dz++) {
                        double d = Math.hypot(dx, dz) + noise.fbm(s[0] + dx, s[1] + dz, 3, 2) * 1.2;
                        if (d > rad + 0.6) {
                            continue;
                        }
                        int x = s[0] + dx, z = s[1] + dz;
                        int hi = waterTop(x, z) + 1 + (int) Math.round((up + 1) * (1 - d / (rad + 1.2)));
                        for (int y = nat(x, z) + 1; y <= hi; y++) {
                            set(x, y, z, rock(x, y, z));
                        }
                        if (chance() < 0.4) {
                            set(x, hi, z, "minecraft:calcite");
                        }
                    }
                }
            }
        }

        /**
         * The headland's heath outside the station's lawn: gorse and bramble in clumps the wind has
         * cut low, dry grass and fern between, grey rock breaking through the turf, a few trees
         * bent away from the sea on the landward side.
         */
        private void heath() {
            Kit.Spot spot = new Kit.Spot() {
                @Override
                public String get(int x, int y, int z) {
                    return p.get(x, y, z);
                }

                @Override
                public void set(int x, int y, int z, String block) {
                    p.set(x, y, z, block);
                }

                @Override
                public boolean hard(int x, int z) {
                    String b = p.get(x, 0, z);
                    return b == null || !Kit.soil(b);
                }
            };
            for (int x = FX0 + 2; x <= FX1 - 2; x++) {
                for (int z = FZ0 + 2; z <= FZ1 - 2; z++) {
                    if (isSea(x, z) || seaNear(x, z, 2) || inBox(x, z, -7, -63, 129, 63) || inBox(x, z, -52, -28, -12, 4) || Math.abs(z) <= 4 && x < -6) {
                        continue;
                    }
                    int t = ground(x, z);
                    String soil = p.get(x, t, z);
                    if (soil == null || !Kit.soil(soil) || p.get(x, t + 1, z) != null && !p.get(x, t + 1, z).equals("weeds_tall") && !p.get(x, t + 1, z).equals("grass_tuft")) {
                        continue;
                    }
                    double n = noise.fbm(x, z, 11, 2), r = chance();
                    if (n > 0.42 && r < 0.12) {
                        // the rock through the turf, a block or two of it
                        set(x, t, z, rock(x, t, z));
                        set(x, t + 1, z, r < 0.05 ? rock(x, t + 1, z) : "air");
                    } else if (n > 0.18) {
                        set(x, t + 1, z, r < 0.45 ? "minecraft:bush" : r < 0.6 ? "minecraft:sweet_berry_bush[age=1]" : r < 0.8 ? "minecraft:short_dry_grass" : "air");
                    } else if (r < 0.35) {
                        set(x, t + 1, z, any("minecraft:short_dry_grass", "minecraft:short_dry_grass", "minecraft:fern", "grass_tuft", "minecraft:tall_dry_grass"));
                    }
                }
            }
            // the trees on the landward side, few and low, leaning off the sea
            for (int x = FX0 + 6; x < -10; x += 9) {
                for (int z = FZ0 + 6; z <= FZ1 - 6; z += 9) {
                    int tx = x + rng.nextInt(5), tz = z + rng.nextInt(5);
                    if (Math.abs(tz) < 34 || ground(tx, tz) != 0 || chance() > 0.4) {
                        continue;
                    }
                    Kit.plant(spot, chance() < 0.6 ? Trees.Kind.BUSH : chance() < 0.5 ? Trees.Kind.DEAD : Trees.Kind.PINE, tx, tz, false, rng);
                }
            }
        }

        // ------------------------------------------------------------------ the way in

        /** The drive in from the road at the west edge, the car park, the board and the fingerpost. */
        private void drive() {
            for (int x = FX0; x <= -12; x++) {
                for (int z = -3; z <= 3; z++) {
                    int t = ground(x, z);
                    set(x, t, z, chance() < 0.7 ? "minecraft:gravel" : "mu_gravel");
                    set(x, t + 1, z, "air");
                }
            }
            // the car park north of the drive's end
            for (int x = -50; x <= -14; x++) {
                for (int z = -26; z <= -4; z++) {
                    set(x, 0, z, chance() < 0.08 ? "asphalt_crack" : chance() < 0.05 ? "asphalt_moss" : "asphalt_b");
                    set(x, 1, z, "air");
                    if ((z == -26 || z == -15) && Math.floorMod(x + 50, 3) == 0 && x < -16) {
                        set(x, 0, z, "road_line_white[axis=z]");
                    }
                }
            }
            for (int x = -50; x <= -14; x += 5) {
                set(x, 1, -27, "gs_bollard");
            }
            lampPost(-32, -3);
            // the board about the light, the bench, the fingerpost to the coastal path
            set(-12, 1, -8, "sy_timber");
            set(-11, 1, -8, "sy_timber");
            sign("light", -12, 2, -8, "south");
            set(-16, 1, -5, "st_bench[facing=south]");
            set(-8, 1, -5, "cm_fpost[facing=north]");
        }

        /**
         * The stone wall round the station, two high with its coping, the white field gate on the
         * drive and the sign on it, the picket fence round the keepers' gardens.
         */
        private void enclosure() {
            int x0 = -6, x1 = 128, z0 = -62, z1 = 62;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = x == x0 || z == z0 || z == z1;
                    if (!side || isSea(x, z) || seaNear(x, z, 2)) {
                        continue;
                    }
                    boolean gap = x == x0 && Math.abs(z) <= 2;
                    int t = ground(x, z);
                    if (gap) {
                        continue;
                    }
                    if (chance() < 0.04) {
                        set(x, t + 1, z, "fm_fieldstone_moss");  // a stretch tumbled to one course
                        continue;
                    }
                    set(x, t + 1, z, chance() < 0.25 ? "fm_fieldstone_moss" : "fm_fieldstone");
                    set(x, t + 2, z, "stone_trim");
                }
            }
            // the gate: two leaves of white rail, one swung back; the sign on the post
            for (int z = -2; z <= 2; z++) {
                set(x0, 0, z, "minecraft:gravel");
            }
            set(x0, 1, -3, "yd_picket_post_white");
            set(x0, 1, 3, "yd_picket_post_white");
            set(x0, 2, -3, "yd_picket_post_white");
            set(x0, 2, 3, "yd_picket_post_white");
            for (int z = -2; z <= 0; z++) {
                set(x0, 1, z, "fm_rail_w[axis=z]");
            }
            for (int x = x0 + 1; x <= x0 + 3; x++) {
                set(x, 1, 2, "fm_rail_w[axis=x]");
            }
            sign("noentry", x0 - 1, 2, -4, "west");
            // the way up to the tower: a gravel path between edgings
            for (int x = x0 + 1; x < TX - 14; x++) {
                for (int z = -1; z <= 1; z++) {
                    set(x, 0, z, chance() < 0.75 ? "minecraft:gravel" : "mu_gravel");
                    set(x, 1, z, "air");
                }
                if (x % 2 == 0) {
                    set(x, 0, -2, "stone_trim");
                    set(x, 0, 2, "stone_trim");
                }
            }
        }

        // ------------------------------------------------------------------ the tower

        /** The tower's radius at y, tapering from the plinth to the gallery. */
        private static double radius(int y) {
            return R0 - (R0 - R1) * Math.max(0, y - 1) / (double) (WATCH - 2);
        }

        /** The stair's cells round the newel: the square seven across, from the door's side round to the north. */
        private static final int[][] FLIGHT = new int[24][];

        static {
            int k = 0;
            for (int z = 0; z > -3; z--) {
                FLIGHT[k++] = new int[] {-3, z};
            }
            for (int x = -3; x < 3; x++) {
                FLIGHT[k++] = new int[] {x, -3};
            }
            for (int z = -3; z < 3; z++) {
                FLIGHT[k++] = new int[] {3, z};
            }
            for (int x = 3; x > -3; x--) {
                FLIGHT[k++] = new int[] {x, 3};
            }
            for (int z = 3; z > 0; z--) {
                FLIGHT[k++] = new int[] {-3, z};
            }
        }

        private static String toward(int[] a, int[] b) {
            return b[0] > a[0] ? "east" : b[0] < a[0] ? "west" : b[1] > a[1] ? "south" : "north";
        }

        /**
         * The tower: the plinth, the walls course by course, the floor, the stair round the newel,
         * the windows up the stair, the watch room and its gallery, the lantern and the lens, the
         * dome, the ventilator.
         */
        private void tower() {
            double ox = TX + 0.5, oz = TZ + 0.5;
            // the plinth: dressed granite two courses, a step round it
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    double d = Math.hypot(dx, dz);
                    int x = TX + dx, z = TZ + dz;
                    for (int y = ground(x, z) + 1; y < 0; y++) {
                        set(x, y, z, "om_granite");
                    }
                    if (d <= 8.6) {
                        set(x, 0, z, d <= 7.6 ? "om_granite" : "stone_trim");
                    }
                    if (d < R0 - 0.4) {
                        set(x, 0, z, "cm_tread");
                        for (int y = 1; y < WATCH; y++) {
                            set(x, y, z, "air");
                        }
                    }
                }
            }
            for (int y = 1; y < WATCH; y++) {
                double r = radius(y);
                Round.wall(p, ox, oz, r, y, y, y >= WATCH - 4 ? "lhr" : "lhw", 0);
            }
            // the newel up the middle, the stair round it, the hole in the watch room's floor over the last steps
            for (int y = 1; y < LANTERN; y++) {
                set(TX, y, TZ, "cm_newel");
            }
            int steps = WATCH - 1;
            for (int k = 0; k < steps; k++) {
                int[] c = FLIGHT[k % 24], n = FLIGHT[(k + 1) % 24];
                set(TX + c[0], 1 + k, TZ + c[1], "steel_stairs[facing=" + toward(c, n) + "]");
                // the rail on the well's side, a step behind
                int ix = c[0] == -3 ? -2 : c[0] == 3 ? 2 : c[0], iz = c[1] == -3 ? -2 : c[1] == 3 ? 2 : c[1];
                if (Math.abs(c[0]) == 3 && Math.abs(c[1]) == 3) {
                    continue;
                }
                String out = c[0] == -3 ? "west" : c[0] == 3 ? "east" : c[1] == -3 ? "north" : "south";
                set(TX + ix, 2 + k, TZ + iz, "railing[facing=" + out + "]");
            }
            // the windows up the stair, one a lap on each side in turn, and the door to the west
            String[] sides = {"north", "east", "south", "west"};
            for (int i = 0; i < 4; i++) {
                int y = 8 + i * 8;
                String side = sides[i];
                for (Round.Cell c : Round.ring(ox, oz, radius(y))) {
                    boolean on = switch (side) {
                        case "north" -> c.x() == TX && c.z() < TZ;
                        case "south" -> c.x() == TX && c.z() > TZ;
                        case "east" -> c.z() == TZ && c.x() > TX;
                        default -> c.z() == TZ && c.x() < TX;
                    };
                    if (on) {
                        set(c.x(), y, c.z(), (chance() < 0.3 ? "wf_glass_broken" : "wf_glass") + "[facing=" + side + "]");
                        set(c.x(), y + 1, c.z(), "wf_glass[facing=" + side + "]");
                    }
                }
            }
            int dx = TX;
            for (Round.Cell c : Round.ring(ox, oz, radius(1))) {
                if (c.z() == TZ) {
                    dx = Math.min(dx, c.x());
                }
            }
            set(dx, 1, TZ, "flat_door[facing=west,half=lower,hinge=left,open=false]");
            set(dx, 2, TZ, "flat_door[facing=west,half=upper,hinge=left,open=false]");
            set(dx + 1, 1, TZ, "air");
            set(dx + 1, 2, TZ, "air");
            watchRoom(ox, oz);
            lantern(ox, oz);
        }

        /**
         * The watch room: its floor out over the corbels to the gallery, the walls up to the
         * lantern's floor with a window each way and the door out east, the ladder up through the
         * ceiling beside the newel; the keeper's table, his log, his oil.
         */
        private void watchRoom(double ox, double oz) {
            for (int dx = -9; dx <= 9; dx++) {
                for (int dz = -9; dz <= 9; dz++) {
                    double d = Math.hypot(dx + 0.5 - 0.5, dz);
                    int x = TX + dx, z = TZ + dz;
                    if (d <= 8.4) {
                        set(x, WATCH, z, d < R1 - 0.6 ? "cm_tread" : "cm_grate");
                    }
                    if (d < R1 - 0.6) {
                        for (int y = WATCH + 1; y < LANTERN; y++) {
                            set(x, y, z, "air");
                        }
                        set(x, LANTERN, z, "cm_grate");
                    }
                }
            }
            // the hole over the last steps of the stair
            for (int k = WATCH - 5; k < WATCH - 1; k++) {
                int[] c = FLIGHT[k % 24];
                set(TX + c[0], WATCH, TZ + c[1], "air");
            }
            // the corbels round under the gallery's edge, facing out
            for (Round.Cell c : Round.ring(ox, oz, 7.2)) {
                double ang = Math.atan2(c.z() + 0.5 - oz, c.x() + 0.5 - ox);
                String out = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? (Math.cos(ang) > 0 ? "east" : "west")
                        : (Math.sin(ang) > 0 ? "south" : "north");
                set(c.x(), WATCH - 1, c.z(), "cm_corbel[facing=" + out + "]");
            }
            Round.wall(p, ox, oz, R1, WATCH + 1, LANTERN - 1, "lhw", 0);
            Round.wall(p, ox, oz, 8.4, WATCH + 1, WATCH + 1, "xrail", 0);
            for (Round.Cell c : Round.ring(ox, oz, R1)) {
                if (c.x() == TX) {
                    String side = c.z() < TZ ? "north" : "south";
                    set(c.x(), WATCH + 3, c.z(), "wf_glass[facing=" + side + "]");
                    set(c.x(), WATCH + 4, c.z(), "wf_glass[facing=" + side + "]");
                } else if (c.z() == TZ && c.x() < TX) {
                    set(c.x(), WATCH + 3, c.z(), "wf_glass[facing=west]");
                    set(c.x(), WATCH + 4, c.z(), "wf_glass[facing=west]");
                } else if (c.z() == TZ) {
                    // the door out to the gallery, to seaward
                    set(c.x(), WATCH + 1, c.z(), "flat_door[facing=east,half=lower,hinge=left,open=true]");
                    set(c.x(), WATCH + 2, c.z(), "flat_door[facing=east,half=upper,hinge=left,open=true]");
                }
            }
            // the ladder up beside the weight tube, through the lantern floor
            for (int y = WATCH + 1; y < LANTERN; y++) {
                set(TX, y, TZ + 1, "cm_iron");
            }
            for (int y = WATCH + 1; y <= LANTERN; y++) {
                set(TX, y, TZ + 2, "ladder[facing=south]");
            }
            // the keeper's table under the north window, his chair, the log and the lamp; the oil cans
            set(TX - 1, WATCH + 1, TZ - 4, "home_desk[facing=south]");
            set(TX - 1, WATCH + 2, TZ - 4, "paper");
            set(TX, WATCH + 1, TZ - 3, "office_chair[facing=north]");
            set(TX + 1, WATCH + 2, TZ - 4, "cv_table_lamp");
            set(TX + 1, WATCH + 1, TZ - 4, "crate");
            for (int[] c : new int[][] {{-4, 1}, {-4, 2}, {-3, 3}}) {
                set(TX + c[0], WATCH + 1, TZ + c[1], "pw_drum[axis=z]");
            }
            set(TX + 2, WATCH + 1, TZ + 3, "ch_cot_head[facing=north]");
            set(TX + 2, WATCH + 1, TZ + 4, "ch_cot_foot[facing=north]");
        }

        /**
         * The lantern on the watch room: the upper gallery's rail round it, its iron base course,
         * the glazing in diagonal astragals, the lens on its pedestal (lit: the keeper's oil), the
         * dome in red, the ventilator ball and the rod.
         */
        private void lantern(double ox, double oz) {
            Round.wall(p, ox, oz, R1 - 0.2, LANTERN + 1, LANTERN + 1, "rail", 0);
            Round.wall(p, ox, oz, 3.6, LANTERN + 1, LANTERN + 1, "lhr", 0);
            Round.wall(p, ox, oz, 3.6, LANTERN + 2, LANTERN + 4, "glazex", 0);
            set(TX, LANTERN + 1, TZ, "cm_ped");
            set(TX, LANTERN + 2, TZ, "cm_lens_lo_lit");
            set(TX, LANTERN + 3, TZ, "cm_lens_mid_lit");
            set(TX, LANTERN + 4, TZ, "cm_lens_top_lit");
            int cap = Round.roof(p, ox, oz, 4.3, "smmm", LANTERN + 5, "lhcap");
            set(TX, cap, TZ, "cm_vent");
        }

        /**
         * The entry house on the tower's foot to the west: whitewashed, a red gable roof, its door
         * on the path, a window each side; the coats and the oil lamp inside.
         */
        private void entry() {
            int x0 = TX - 14, x1 = TX - 6, z0 = TZ - 3, z1 = TZ + 3;
            b.shell(x0, z0, x1, z1, 1, 4, "siding_white", "home_floor_oak", "home_floor_oak");
            b.gable(x0, x1, z0, z1, 5, "minecraft:brick", false, "siding_white", 1);
            b.window(x0 + 4, 2, z0, "north", 2, "front_trim_white");
            b.window(x0 + 4, 2, z1, "south", 2, "front_trim_white");
            fill(x0, 1, TZ, x0, 2, TZ, "air");
            b.door(x0, 1, TZ, Doors.FLAT, "west");
            fill(x0 + 1, 1, z0 + 1, x1, 3, z1 - 1, "air");
            set(x1, 1, TZ, "air");
            set(x1, 2, TZ, "air");
            set(x0 + 1, 1, z0 + 1, "crate");
            set(x0 + 2, 1, z0 + 1, "pw_drum[axis=x]");
            sign("tower", x0 - 1, 3, TZ - 2, "west");
        }

        // ------------------------------------------------------------------ the houses

        /**
         * The keepers' house north of the path, two storeys facing it, and the assistant's
         * cottage south of it facing back; their gardens in white pickets, the sign at the gate.
         */
        private void keepers() {
            House.Palette pal = new House.Palette("siding_white", "front_trim_white", "red", "home_floor_oak", "stone_trim",
                    "white", "black", "red");
            House big = new House(p.turned(58, 0, -14, 2), rng, House.Style.FARMHOUSE, House.Condition.BOARDED, pal, 18, 16, 2);
            big.build();
            House small = new House(p.turned(32, 0, 14, 0), rng, House.Style.BUNGALOW, House.Condition.LOOTED, pal, 15, 13, 1);
            small.build();
            // the front yards between the houses and the station path, the gates on their walks
            int bigDoor = 58 - big.doorX(), smallDoor = 32 + small.doorX();
            picket(40, -13, 59, -4, bigDoor);
            picket(31, 4, 47, 13, smallDoor);
            for (int z = -12; z <= -4; z++) {
                set(bigDoor, 0, z, "mu_gravel");
            }
            for (int z = 4; z <= 12; z++) {
                set(smallDoor, 0, z, "mu_gravel");
            }
            sign("private", 41, 2, -3, "south");
            garden(40, -46, 59, -31);
            garden(31, 28, 47, 42);
        }

        /**
         * A keeper's kitchen garden behind his house, gone to seed: the rows under weeds, the
         * scarecrow, the privy, the woodpile and the block, the hives, the washing line's poles.
         */
        private void garden(int x0, int z0, int x1, int z1) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (edge) {
                        boolean alongX = z == z0 || z == z1;
                        set(x, 1, z, (x == x0 || x == x1) && (z == z0 || z == z1) || (alongX ? x : z) % 3 == 0 ? "fm_rail_post_w[axis=" + (alongX ? "x" : "z") + "]"
                                : chance() < 0.1 ? "fm_rail_broken[axis=" + (alongX ? "x" : "z") + "]" : "fm_rail_w[axis=" + (alongX ? "x" : "z") + "]");
                        continue;
                    }
                    // the beds, a row of earth each side of a path, in the half nearer the house
                    boolean bed = x > x0 + 1 && x < x0 + (x1 - x0) * 2 / 3 && Math.floorMod(x - x0, 3) != 0;
                    if (bed) {
                        set(x, 0, z, "minecraft:farmland");
                        double r = chance();
                        set(x, 1, z, r < 0.25 ? "minecraft:potatoes[age=" + (4 + rng.nextInt(4)) + "]" : r < 0.4 ? "minecraft:carrots[age=" + (3 + rng.nextInt(5)) + "]"
                                : r < 0.5 ? "minecraft:beetroots[age=3]" : r < 0.8 ? any("weeds_tall", "weeds", "minecraft:short_dry_grass") : "air");
                    } else if (chance() < 0.2) {
                        set(x, 1, z, any("weeds", "grass_tuft", "minecraft:short_grass"));
                    }
                }
            }
            int mx = x0 + (x1 - x0) / 3, mz = (z0 + z1) / 2;
            set(mx, 1, mz, "fm_scarecrow_lo[facing=" + dir() + "]");
            set(mx, 2, mz, "fm_scarecrow_hi[facing=" + dir() + "]");
            // the privy in the far corner, the woodpile along the end, the block and its axe-marks
            set(x1 - 2, 1, z0 + 2, "fm_outhouse_lo[facing=west]");
            set(x1 - 2, 2, z0 + 2, "fm_outhouse_hi[facing=west]");
            for (int z = z0 + 5; z <= z0 + 9; z++) {
                set(x1 - 1, 1, z, "fm_firewood[axis=z]");
                if (chance() < 0.7) {
                    set(x1 - 1, 2, z, "fm_firewood[axis=z]");
                }
            }
            set(x1 - 3, 1, z0 + 7, "fm_chop_block[facing=west]");
            set(x1 - 2, 1, z1 - 2, chance() < 0.5 ? "fm_hive[facing=west]" : "fm_hive_tipped[facing=west]");
            set(x1 - 4, 1, z1 - 2, "fm_hive[facing=west]");
            // the washing line's poles, the basket left under them
            for (int y = 1; y <= 3; y++) {
                set(x1 - 7, y, z0 + 2, "fm_post");
                set(x1 - 7, y, z1 - 2, "fm_post");
            }
            set(x1 - 6, 1, (z0 + z1) / 2, "home_laundry");
        }

        /** A white picket fence round x0..x1, z0..z1, the gate in the side on the path at gateX. */
        private void picket(int x0, int z0, int x1, int z1, int gateX) {
            boolean north = z1 < 0;
            int pathZ = north ? z1 : z0;
            for (int x = x0; x <= x1; x++) {
                for (int z : new int[] {z0, z1}) {
                    if (z == pathZ && Math.abs(x - gateX) <= 0) {
                        set(x, 1, z, "yd_picket_gate_white_open[axis=x]");
                        continue;
                    }
                    set(x, 1, z, x % 4 == 0 ? "yd_picket_post_white" : chance() < 0.08 ? "yd_picket_white_broken[axis=x]" : "yd_picket_white[axis=x]");
                }
            }
            for (int z = z0 + 1; z < z1; z++) {
                for (int x : new int[] {x0, x1}) {
                    set(x, 1, z, z % 4 == 0 ? "yd_picket_post_white" : "yd_picket_white[axis=z]");
                }
            }
        }

        /** The oil house: a little brick store by the tower, its iron door, the drums inside. */
        private void oilHouse() {
            int x0 = 82, x1 = 88, z0 = 12, z1 = 18;
            b.shell(x0, z0, x1, z1, 1, 4, "brick_red", "floor_concrete", "floor_concrete");
            b.gable(x0, x1, z0, z1, 5, "minecraft:andesite", true, "brick_red", 1);
            fill(x0 + 1, 1, z0 + 1, x1 - 1, 3, z1 - 1, "air");
            b.door(x0 + 3, 1, z0, Doors.STEEL, "north");
            sign("oil", x0 + 1, 3, z0 - 1, "north");
            for (int x = x0 + 1; x < x1; x++) {
                set(x, 1, z1 - 1, "pw_drum[axis=z]");
                if (chance() < 0.5) {
                    set(x, 2, z1 - 1, "pw_drum[axis=z]");
                }
            }
            set(x0 + 1, 1, z0 + 1, "bunk_jerry_cans[facing=east]");
        }

        /**
         * The fog signal on the cliff south-east of the tower: its engine house in white board with a
         * red roof, the two horns out of its seaward wall, the air receivers along it.
         */
        private void fogSignal() {
            int x0 = 104, x1 = 118, z0 = 22, z1 = 32;
            for (int x = x0 - 1; x <= x1 + 1; x++) {
                for (int z = z0 - 1; z <= z1 + 1; z++) {
                    for (int y = ground(x, z) + 1; y <= 0; y++) {
                        set(x, y, z, y == 0 ? "floor_concrete" : "concrete_dark");
                    }
                }
            }
            b.shell(x0, z0, x1, z1, 1, 6, "siding_white", "floor_concrete", "floor_concrete");
            b.gable(x0, x1, z0, z1, 7, "minecraft:brick", false, "siding_white", 1);
            b.window(x0 + 3, 2, z0, "north", 3, "front_trim_white");
            b.window(x0 + 9, 2, z0, "north", 3, "front_trim_white");
            b.door(x0, 1, z0 + 5, Doors.FLAT, "west");
            fill(x0 + 1, 1, z0 + 1, x1 - 1, 5, z1 - 1, "air");
            set(x1 + 1, 4, z0 + 3, "cm_horn[facing=east]");
            set(x1 + 1, 4, z1 - 3, "cm_horn[facing=east]");
            sign("fog", x0 - 1, 4, z0 + 2, "west");
            // the engines and their receivers inside
            for (int x = x0 + 2; x <= x0 + 6; x += 4) {
                set(x, 1, z0 + 3, "tn_genset[facing=south]");
                set(x, 1, z1 - 3, "tn_genset[facing=north]");
            }
            for (int x = x0 + 9; x < x1; x++) {
                set(x, 1, z0 + 5, "tk_pipe[axis=x]");
            }
        }

        /** The rest of the station: the radio mast, the water tank, the flagpole, the washing line, the lamps. */
        private void yard() {
            camp.mast(70, 44, 26);
            double tx = 74.5, tz = -46.5;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (Math.hypot(dx, dz) <= 3.2) {
                        set((int) tx + dx, 1, (int) tz + dz, "concrete_dark");
                    }
                }
            }
            Round.wall(p, tx, tz, 2.6, 2, 6, "wtw", 0);
            Round.roof(p, tx, tz, 2.6, Round.cone(2.6), 7, "wtw");
            for (int y = 1; y <= 9; y++) {
                set(94, y, -12, "cv_flagpole");
            }
            set(94, 0, -12, "cv_flagpole_base");
            set(94, 10, -12, "cv_flagpole_top[facing=" + any("north", "east", "south", "west") + "]");
            lampPost(20, -4);
            lampPost(60, 4);
            lampPost(92, -4);
        }

        // ------------------------------------------------------------------ the path

        /**
         * The coastal path along the cliff top outside the wall, a few blocks back from the edge:
         * worn earth, the rail fence between it and the drop, the warnings on it.
         */
        private void coastPath() {
            for (int z = FZ0 + 10; z <= FZ1 - 10; z++) {
                int edge = Integer.MIN_VALUE;
                for (int x = FX1; x >= 0; x--) {
                    if (!isSea(x, z) && !seaNear(x, z, 1)) {
                        edge = x;
                        break;
                    }
                }
                if (edge == Integer.MIN_VALUE || Math.abs(z) <= 1) {
                    continue;
                }
                for (int x = edge - 5; x <= edge - 3 && Math.abs(z) > 63; x++) {
                    int t = ground(x, z);
                    set(x, t, z, chance() < 0.6 ? "minecraft:coarse_dirt" : "minecraft:dirt_path");
                    set(x, t + 1, z, "air");
                }
                int fx = edge - 1, t = ground(fx, z);
                set(fx, t + 1, z, Math.floorMod(z, 3) == 0 ? "fm_rail_post[axis=z]" : chance() < 0.07 ? "fm_rail_broken[axis=z]" : "fm_rail[axis=z]");
                if (Math.floorMod(z, 40) == 0) {
                    set(edge - 6, t + 1, z, "sy_timber");
                    sign("cliff", edge - 6, t + 2, z - 1, "east");
                }
            }
            set(-2, 1, 66, "cm_fpost[facing=east]");
            sign("quiet", -4, 1, 68, "south");
        }

        /**
         * The landing stair: steps cut down through the cliff north of the tower to the shingle,
         * rock both sides, the life ring on its post at the top, the keepers' skiff dragged up
         * at the foot and left there.
         */
        private void landing() {
            // a gully cut straight down to seaward through the cliff, two wide, rock walls each side
            int z0 = -41, z1 = -40, edge = Integer.MAX_VALUE, out = Integer.MIN_VALUE;
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                edge = Math.min(edge, landEdge(z));
                out = Math.max(out, landEdge(z));
            }
            int foot = ground(out + 1, z0);
            for (int z = z0; z <= z1; z++) {
                foot = Math.max(foot, ground(out + 1, z));
            }
            int h = Math.max(1, -foot), xa = edge - h;
            for (int k = 0; k < h; k++) {
                int x = xa + k, y = -k;
                for (int z = z0; z <= z1; z++) {
                    set(x, y - 1, z, rock(x, y - 1, z));
                    set(x, y, z, "stairs_concrete[facing=west]");
                    for (int yy = y + 1; yy <= 2; yy++) {
                        set(x, yy, z, "air");
                    }
                }
                // the rail along each lip, stepping down with the rock where the gully meets the face
                for (int z : new int[] {z0 - 1, z1 + 1}) {
                    int t = ground(x, z);
                    if (t >= 0) {
                        set(x, t + 1, z, Math.floorMod(x, 3) == 0 ? "fm_rail_post[axis=x]" : "fm_rail[axis=x]");
                    }
                }
            }
            // out through the face onto the shingle
            int yb = -h;
            for (int x = xa + h; x <= out + 2; x++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, yb - 1, z, x < out ? "cv_step_stone" : any("minecraft:gravel", "mu_gravel", "minecraft:cobblestone"));
                    for (int y = yb; y <= 2; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
            // the head of the stair: its posts, the life ring, the sign
            for (int z : new int[] {z0 - 1, z1 + 1}) {
                set(xa - 1, 1, z, "sy_timber");
                set(xa - 1, 2, z, "sy_timber");
            }
            set(xa - 2, 2, z0 - 1, "ship_lifebuoy[facing=west]");
            sign("steps", xa - 2, 2, z1 + 1, "west");
            for (int x = xa - 8; x < xa; x++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, ground(x, z), z, chance() < 0.6 ? "minecraft:coarse_dirt" : "minecraft:dirt_path");
                    set(x, ground(x, z) + 1, z, "air");
                }
            }
            p.vehicle(out + 6.5, yb, z0 - 4.0, 200f, "=boat_skiff@wrecked", rng.nextLong());
        }

        /** The last land column eastward at z: the cliff's top edge. */
        private int landEdge(int z) {
            for (int x = FX1; x >= FX0; x--) {
                if (!isSea(x, z)) {
                    return x;
                }
            }
            return FX0;
        }

        // ------------------------------------------------------------------ what happened

        /**
         * The keeper's last weeks: the tower door barricaded from inside, the trail of oil cans
         * from the oil house to it, the cars left in the car park, the dead at the wall and on the
         * cliff path, the bones the gulls have had.
         */
        private void story() {
            for (int y = 1; y <= 2; y++) {
                set(TX - 5, y, TZ, chance() < 0.5 ? "crate" : "pw_drum[axis=z]");
            }
            set(TX - 5, 1, TZ + 1, "home_dresser[facing=west]");
            for (int x = 86; x < TX - 14; x += 3) {
                set(x, 1, 2 + rng.nextInt(2), chance() < 0.5 ? "bunk_jerry_cans[facing=" + dir() + "]" : "paper");
            }
            for (int i = 0; i < 4; i++) {
                p.car(-46 + i * 9 + 0.5, 1, -20 + rng.nextInt(10) + 0.5, rng.nextInt(4) * 90f, 4, 6, rng.nextLong());
            }
            for (int i = 0; i < 28; i++) {
                int x = -40 + rng.nextInt(170), z = -70 + rng.nextInt(140);
                if (isSea(x, z)) {
                    continue;
                }
                int t = ground(x, z);
                if (empty(x, t + 1, z) && get(x, t, z) != null) {
                    set(x, t + 1, z, chance() < 0.45 ? "home_corpse_head[facing=" + dir() + "]" : chance() < 0.5 ? "blood_splat" : "trash_bag[facing=" + dir() + "]");
                }
            }
            // the keeper's words on the oil house wall
            set(81, 2, 15, "blood_splat");
        }
    }
}
