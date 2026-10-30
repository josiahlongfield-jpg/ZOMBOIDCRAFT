package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven City Park, three blocks square, and the Grayhaven Museum ({@link Museum}) along its
 * north side: the museum's plaza on the street, its loading yard to the east; behind it the
 * promenade, and the park: the lake in the middle with the island and its little round temple,
 * the footbridge, the boathouse and its rowboats on the north shore, the green iron bandstand on
 * the west lawn, the glass palm house on the east, the playground and the court in the south-west
 * corner, the founder on his plinth inside the south gate; the gravel walks, the lamps, the
 * benches, the old trees, the iron railings round it all.
 *
 * <p>The last weeks: the families camped round the bandstand in the state's tents, the barrels
 * they burned, the board at the gate; in the south-east corner the trenches the county dug for the
 * dead, half filled, the crosses along the filled ones.
 *
 * <p>Drawn in the lot's frame: x along the front (west to east), z from the front (north) back,
 * the ground at y = 0.
 */
final class CityPark extends Grounds {
    /** The lake: its centre and half-axes; the island; the footbridge's line. */
    static final int LX = 106, LZ = 150, LA = 46, LB = 27, IX = 122, IZ = 150, IR = 7;
    /** The promenade behind the museum. */
    static final int PROM_Z0 = 82, PROM_Z1 = 87;
    /** The bandstand, the palm house, the boathouse. */
    static final int BX = 40, BZ = 128, GX0 = 166, GX1 = 196, GZ0 = 112, GZ1 = 140, HX0 = 84, HX1 = 98, HZ0 = 113, HZ1 = 123;
    /** The service yard east of the museum. */
    static final int YX0 = 183, YX1 = 204, YZ1 = 82;

    private final Kit kit;
    private final double[] wobble = new double[64];

    CityPark(Placer p, SplittableRandom rng, int w, int d, Kit kit) {
        super(p, rng, w, d, 0.45, 0.1, 0.4);
        this.kit = kit;
        for (int i = 0; i < wobble.length; i++) {
            wobble[i] = rng.nextDouble() - 0.5;
        }
    }

    @Override
    String signs() {
        return "mu_sgn_";
    }

    void build() {
        ground();
        new Museum(this).build();
        yard();
        lake();
        island();
        boathouse();
        bandstand();
        palmHouse();
        paths();
        play();
        statue();
        camp();
        burials();
        railings();
        furnish();
        trees();
        Kit.lawn(p, rng, 4, 4, w - 5, d - 5, 0.45);
        litter();
    }

    // ------------------------------------------------------------------ the ground

    /** The sidewalks round the lot, the stone plaza in front of the museum, a walk round its sides. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                if (edge) {
                    set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
                } else if (z < Museum.WZ0 || (z <= PROM_Z0 && (x >= 30 && x <= 33 || x >= 179 && x <= 182))) {
                    set(x, 0, z, chance() < 0.08 ? "stone_trim_crack" : "pt_stone_flags");
                }
            }
        }
    }

    /** The loading yard: the drive in from the street, the dock at the museum's east door, the truck backed to it. */
    private void yard() {
        for (int x = YX0; x <= YX1; x++) {
            for (int z = 4; z <= YZ1; z++) {
                if (z < Museum.WZ0 && (x < 190 || x > 197)) {
                    continue;
                }
                set(x, 0, z, chance() < 0.12 ? "asphalt_crack" : "asphalt");
            }
        }
        for (int x = 179; x <= 182; x++) {
            for (int z = 53; z <= 63; z++) {
                fill(x, 0, z, x, 2, z, "concrete_dark");
                set(x, 3, z, "floor_concrete");
            }
            // the steps down off the dock's north end
            fill(x, 1, 53, x, 3, 55, "air");
            set(x, 1, 53, "cv_step_stone[facing=south]");
            set(x, 1, 54, "concrete_dark");
            set(x, 2, 54, "cv_step_stone[facing=south]");
            fill(x, 1, 55, x, 2, 55, "concrete_dark");
            set(x, 3, 55, "cv_step_stone[facing=south]");
        }
        for (int z = 56; z <= 63; z++) {
            set(183, 1, z, "gs_bollard");
        }
        p.vehicle(190, 1, 60, 90 + (float) (chance() * 6 - 3), "box_truck,step_van", rng.nextLong());
        if (chance() < 0.6) {
            p.vehicle(195, 1, 32, 180 + (float) (chance() * 20), "pickup,van", rng.nextLong());
        }
        for (int z = 40; z <= 76; z += 4) {
            if (chance() < 0.5) {
                set(201, 1, z, chance() < 0.5 ? "mu_crate[facing=" + dir() + "]" : "st_pallet[facing=west]");
            }
        }
        for (int x = 186; x <= 198; x += 8) {
            set(x, 1, YZ1 - 1, "dumpster[facing=north]");
        }
        set(YX1, 1, 30, "fs_hydrant[facing=east]");
    }

    // ------------------------------------------------------------------ the lake

    /** How far out of the lake's edge a point is: under 1 in the water. */
    double lakeE(int x, int z) {
        double dx = (x - LX) / (double) LA, dz = (z - LZ) / (double) LB;
        double a = Math.atan2(dz, dx);
        int i = (int) Math.floor((a + Math.PI) / (2 * Math.PI) * 16) & 15;
        double f = (a + Math.PI) / (2 * Math.PI) * 16 - Math.floor((a + Math.PI) / (2 * Math.PI) * 16);
        double wob = wobble[i] * (1 - f) + wobble[(i + 1) & 15] * f;
        return Math.sqrt(dx * dx + dz * dz) * (1 + 0.12 * wob);
    }

    private boolean onIsland(int x, int z) {
        return Math.hypot(x - IX, z - IZ) <= IR;
    }

    private void lake() {
        for (int x = LX - LA - 10; x <= LX + LA + 10; x++) {
            for (int z = LZ - LB - 10; z <= LZ + LB + 10; z++) {
                double e = lakeE(x, z);
                if (onIsland(x, z) || e >= 1.36) {
                    continue;
                }
                if (e < 1) {
                    set(x, 0, z, "minecraft:water");
                    if (e < 0.75) {
                        set(x, -1, z, "minecraft:water");
                        set(x, -2, z, "minecraft:mud");
                    } else {
                        set(x, -1, z, "minecraft:mud");
                    }
                    if (chance() < 0.02 && e > 0.8) {
                        set(x, 1, z, "minecraft:lily_pad");
                    }
                } else if (e < 1.07) {
                    set(x, 0, z, "mu_lime");
                } else {
                    set(x, 0, z, chance() < 0.12 ? "mu_gravel_b" : "mu_gravel");
                }
            }
        }
        // the ends of the lake gone back to marsh the way park ponds do when nobody cuts them: the kerb
        // grassed over, a reed bed of mud in the shallows, the lilies thick off it
        for (int x = LX - LA - 10; x <= LX + LA + 10; x++) {
            for (int z = LZ - LB - 10; z <= LZ + LB + 10; z++) {
                double e = lakeE(x, z), c = Math.cos(Math.atan2((z - LZ) / (double) LB, (x - LX) / (double) LA));
                if (onIsland(x, z) || e >= 1.07 || e < 0.82 || c > -0.55 && c < 0.7) {
                    continue;
                }
                if (e >= 1) {
                    set(x, 0, z, "minecraft:grass_block");
                    reeds(x, z, 0.55);
                } else if (e >= 0.93 && chance() < 0.7) {
                    set(x, 0, z, "minecraft:mud");
                    reeds(x, z, 0.85);
                } else if (chance() < 0.18) {
                    set(x, 1, z, "minecraft:lily_pad");
                }
            }
        }
        // the benches along the shore walk facing the water, the lamps between
        for (int k = 0; k < 28; k++) {
            double a = k * Math.PI * 2 / 28;
            int x = (int) Math.round(LX + Math.cos(a) * LA * 1.3), z = (int) Math.round(LZ + Math.sin(a) * LB * 1.3);
            String g = get(x, 0, z);
            if (g == null || !g.startsWith("mu_gravel")) {
                continue;
            }
            if (k % 2 == 0) {
                lampPost(x, z);
            } else if (empty(x, 1, z)) {
                String f = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "west" : "east") : (Math.sin(a) > 0 ? "north" : "south");
                set(x, 1, z, chance() < 0.9 ? "mu_bench[facing=" + f + "]" : "debris_chunk[facing=" + f + "]");
            }
        }
    }

    /** Reeds on a shore cell: canes two or three high, else the long grass; bare mud the rest. */
    private void reeds(int x, int z, double p) {
        double r = chance();
        if (r < p * 0.6) {
            int h = 2 + rng.nextInt(2);
            for (int y = 1; y <= h; y++) {
                set(x, y, z, "minecraft:sugar_cane");
            }
        } else if (r < p) {
            set(x, 1, z, "minecraft:tall_grass[half=lower]");
            set(x, 2, z, "minecraft:tall_grass[half=upper]");
        }
    }

    /** The island: grass and a stone rim, the little round temple of eight columns under its copper dome, the footbridge south. */
    private void island() {
        for (int x = IX - IR; x <= IX + IR; x++) {
            for (int z = IZ - IR; z <= IZ + IR; z++) {
                double d = Math.hypot(x - IX, z - IZ);
                if (d > IR) {
                    continue;
                }
                fill(x, -2, z, x, -1, z, "minecraft:mud");
                set(x, 0, z, d > IR - 1 ? "mu_lime" : d < 4.6 ? "pt_stone_flags" : "minecraft:grass_block");
            }
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(IX + Math.cos(a) * 3.6), z = (int) Math.round(IZ + Math.sin(a) * 3.6);
            set(x, 1, z, "cv_column_base_white");
            set(x, 2, z, "cv_column_white");
            set(x, 3, z, "cv_column_white");
            set(x, 4, z, "cv_column_cap_white");
        }
        for (int x = IX - 5; x <= IX + 5; x++) {
            for (int z = IZ - 5; z <= IZ + 5; z++) {
                double d = Math.hypot(x - IX, z - IZ);
                if (d <= 4.6) {
                    set(x, 5, z, d > 3 ? "mu_lime" : "us_coffer");
                }
                if (d <= 3.6) {
                    set(x, 6, z, d > 2.2 ? "cv_copper_slab" : "cv_copper");
                }
                if (d <= 2.2) {
                    set(x, 7, z, d > 1 ? "cv_copper_slab" : "cv_copper");
                }
            }
        }
        set(IX, 8, IZ, "cv_finial_c");
        set(IX, 1, IZ, "mu_plinth");
        set(IX, 2, IZ, chance() < 0.7 ? "mu_urn" : "debris_chunk[facing=north]");
        // the footbridge to the south shore
        for (int z = IZ + IR - 1; z <= LZ + LB + 4; z++) {
            String g = get(IX, 0, z);
            if (g != null && g.startsWith("mu_gravel") && z > IZ + IR + 2) {
                break;
            }
            for (int x = IX - 1; x <= IX + 1; x++) {
                set(x, 0, z, "mu_boards[axis=z]");
                set(x, 1, z, "air");
            }
            set(IX - 2, 0, z, "mu_boards[axis=z]");
            set(IX + 2, 0, z, "mu_boards[axis=z]");
            set(IX - 2, 1, z, chance() < 0.05 ? "air" : "mu_rail[axis=z]");
            set(IX + 2, 1, z, chance() < 0.05 ? "air" : "mu_rail[axis=z]");
        }
    }

    /** The boathouse on the north shore: white boards and the green roof, the boats inside, the dock out over the water. */
    private void boathouse() {
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z <= HZ1 + 7; z++) {
                boolean walls = z <= HZ1 && (x == HX0 || x == HX1 || z == HZ0 || z == HZ1);
                set(x, -1, z, "concrete_dark");
                set(x, 0, z, z > HZ1 && (x == HX0 || x == HX1 || z == HZ1 + 7) ? "fence_post" : "mu_boards[axis=x]");
                if (z > HZ1) {
                    if (z == HZ1 + 7 || x == HX0 || x == HX1) {
                        set(x, 1, z, (x + z) % 3 == 0 ? "fence_post" : "mu_rail[axis=" + (z == HZ1 + 7 ? "x" : "z") + "]");
                    }
                    continue;
                }
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z, walls ? b.wall("siding_white", y) : "air");
                }
            }
        }
        // the roof: a gable along x, the ridge over the middle
        int mz = (HZ0 + HZ1) / 2;
        for (int x = HX0 - 1; x <= HX1 + 1; x++) {
            for (int z = HZ0 - 1; z <= HZ1 + 1; z++) {
                int k = Math.min(z - (HZ0 - 1), HZ1 + 1 - z);
                int y = 5 + k / 2;
                if (z == mz) {
                    set(x, y, z, "ext_roof_green_ridge[facing=north]");
                } else {
                    String face = z < mz ? "south" : "north";
                    set(x, y, z, "ext_roof_green" + (k == 0 ? "_eave" : "") + "[facing=" + face + "]");
                    for (int yy = 5; yy < y; yy++) {
                        if (x == HX0 || x == HX1) {
                            set(x, yy, z, "siding_white");
                        }
                    }
                }
            }
        }
        // the doors: from the walk at the north, the wide way out to the dock at the south
        door(91, 0, HZ0, "flat_door", "north");
        fill(88, 1, HZ1, 94, 3, HZ1, "air");
        sign("boats", 90, 4, HZ0 - 1, "north");
        set(93, 3, HZ0 - 1, "mu_lifering[facing=north]");
        for (int x = HX0 + 2; x <= HX1 - 2; x += 4) {
            if (x >= 89 && x <= 93) {
                continue;
            }
            set(x, 1, HZ0 + 3, "mu_boat_0[facing=south]");
            set(x, 1, HZ0 + 4, "mu_boat_1[facing=south]");
        }
        set(HX0 + 1, 1, HZ1 - 2, "mu_oars[facing=east]");
        set(HX1 - 1, 1, HZ1 - 2, "mu_oars[facing=west]");
        set(HX1 - 1, 1, HZ0 + 1, "un_circ[facing=west]");
        set(HX1 - 1, 2, HZ0 + 1, "pk_till_1[facing=west]");
        // the boats still tied at the dock, one swamped
        for (int x = HX0 + 1; x <= HX1 - 1; x += 3) {
            int z = HZ1 + 8;
            if (chance() < 0.7 && "minecraft:water".equals(get(x, 0, z)) && "minecraft:water".equals(get(x, 0, z + 1))) {
                set(x, 1, z, "mu_boat_0[facing=north]");
                set(x, 1, z + 1, "mu_boat_1[facing=north]");
            }
        }
    }

    // ------------------------------------------------------------------ the bandstand

    /** How far out a point is on the bandstand's octagon (6 its edge). */
    private static double oct(int dx, int dz) {
        return Math.max(Math.max(Math.abs(dx), Math.abs(dz)), (Math.abs(dx) + Math.abs(dz)) / 1.42);
    }

    private void bandstand() {
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                double o = oct(dx, dz);
                int x = BX + dx, z = BZ + dz;
                if (o <= 6.4) {
                    set(x, 0, z, "mu_lime");
                    set(x, 1, z, "mu_boards[axis=x]");
                    if (o > 5.4 && !(dx == 6 && Math.abs(dz) <= 1)) {
                        set(x, 2, z, "mu_rail[axis=" + (Math.abs(dx) >= Math.abs(dz) ? "z" : "x") + "]");
                    }
                } else if (o <= 7.4) {
                    set(x, 0, z, "mu_gravel");
                }
                // the roof: copper rings stepping in to the finial
                for (int k = 0; k < 4; k++) {
                    double ro = 7.4 - k * 1.8;
                    if (o <= ro && o > ro - 1.8) {
                        String f = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "west" : "east") : (dz > 0 ? "north" : "south");
                        set(x, 6 + k, z, k == 3 ? "cv_copper" : "cv_copper_step[facing=" + f + "]");
                    } else if (o <= ro - 1.8) {
                        set(x, 6 + k, z, k == 0 ? "us_coffer" : "cv_copper");
                    }
                }
            }
        }
        set(BX, 10, BZ, "cv_finial_c");
        // the step up at the east, the eight columns
        for (int dz = -1; dz <= 1; dz++) {
            set(BX + 7, 1, BZ + dz, "cv_step_stone[facing=west]");
            set(BX + 6, 2, BZ + dz, "air");
        }
        for (int[] o : new int[][] {{6, 2}, {6, -2}, {-6, 2}, {-6, -2}, {2, 6}, {-2, 6}, {2, -6}, {-2, -6}}) {
            for (int y = 2; y <= 5; y++) {
                set(BX + o[0], y, BZ + o[1], "mu_bs_post");
            }
        }
        // the music stands left in their arc, and the families' cots between them
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 0.6 + k * Math.PI * 0.16;
            set((int) Math.round(BX + Math.cos(a) * 3), 2, (int) Math.round(BZ + Math.sin(a) * 3), chance() < 0.7 ? "mu_music_stand[facing=east]" : "home_chair[facing=east]");
        }
        for (int dz = -3; dz <= 3; dz += 2) {
            if (empty(BX - 1, 2, BZ + dz)) {
                set(BX - 1, 2, BZ + dz, "sc_cot_head[facing=west]");
                set(BX, 2, BZ + dz, "sc_cot_foot[facing=west]");
            }
        }
        sign("bandstand", BX - 1, 5, BZ - 6, "south");
    }

    // ------------------------------------------------------------------ the palm house

    private int vault(int x) {
        double half = (GX1 - GX0) / 2.0, cx = (GX0 + GX1) / 2.0;
        double t = Math.min(1, Math.abs(x - cx) / (half + 0.5));
        return 7 + (int) Math.round(6 * Math.sqrt(1 - t * t));
    }

    private void palmHouse() {
        int cx = (GX0 + GX1) / 2, cz = (GZ0 + GZ1) / 2;
        for (int x = GX0; x <= GX1; x++) {
            int top = vault(x);
            int lo = Math.min(x > GX0 ? vault(x - 1) : 7, x < GX1 ? vault(x + 1) : 7);
            for (int z = GZ0; z <= GZ1; z++) {
                boolean side = x == GX0 || x == GX1, end = z == GZ0 || z == GZ1;
                boolean post = (side && (z - GZ0) % 7 == 0) || (end && (x - GX0) % 5 == 0);
                set(x, 0, z, Math.abs(x - cx) <= 2 || Math.abs(z - cz) <= 2 ? "cv_hex_tile" : "bed_soil");
                if (side || end) {
                    set(x, 0, z, "mu_lime");
                    set(x, 1, z, "mu_lime");
                    for (int y = 2; y < (end ? top : 7); y++) {
                        set(x, y, z, post ? "mu_gh_post" : pane());
                    }
                }
                for (int y = Math.min(lo + 1, top); y <= top; y++) {
                    set(x, y, z, (z - GZ0) % 7 == 0 ? "mu_gh_post" : pane());
                }
                for (int y = 1; y < Math.min(lo + 1, top) && !side && !end; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        // the lily pool in the middle, the paths' benches, the doors at the ends
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean rim = Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3;
                set(x, 0, z, rim ? "mu_lime" : "minecraft:water");
                set(x, -1, z, "mu_lime");
                if (!rim && chance() < 0.15) {
                    set(x, 1, z, "minecraft:lily_pad");
                }
            }
        }
        for (int z : new int[] {GZ0, GZ1}) {
            fill(cx, 1, z, cx + 1, 3, z, "air");
            doors(cx, 0, z, "shop_door", z == GZ0 ? "north" : "south");
            set(cx, 0, z, "cv_hex_tile");
            set(cx + 1, 0, z, "cv_hex_tile");
        }
        sign("palms", cx, 5, GZ0 - 1, "north");
        // the palms and the ferns in the beds, some dead in the cold since the heating stopped
        for (int x = GX0 + 2; x <= GX1 - 2; x++) {
            for (int z = GZ0 + 2; z <= GZ1 - 2; z++) {
                if (!"bed_soil".equals(get(x, 0, z)) || !empty(x, 1, z)) {
                    continue;
                }
                double r = chance();
                if (r < 0.06 && (x - GX0) % 3 == 1 && (z - GZ0) % 3 == 1) {
                    int h = 3 + rng.nextInt(Math.max(1, vault(x) - 6));
                    for (int y = 1; y <= h; y++) {
                        set(x, y, z, "mu_palm_trunk");
                    }
                    set(x, h + 1, z, chance() < 0.8 ? "mu_palm_crown" : "air");
                } else if (r < 0.3) {
                    set(x, 1, z, chance() < 0.8 ? "mu_fern" : "home_plant_dead");
                }
            }
        }
        for (int z = GZ0 + 4; z <= GZ1 - 4; z += 6) {
            if (Math.abs(z - cz) > 4) {
                set(cx - 3, 1, z, "mu_bench[facing=east]");
                set(cx + 4, 1, z, "mu_bench[facing=west]");
            }
        }
    }

    private String pane() {
        return chance() < 0.09 ? (chance() < 0.5 ? "air" : "mu_gh_glass_broken") : "mu_gh_glass";
    }

    // ------------------------------------------------------------------ the walks

    /** A gravel walk three wide along a line of points, where the ground is still free. */
    private void walk(int... pts) {
        for (int i = 0; i + 3 < pts.length; i += 2) {
            int x0 = pts[i], z0 = pts[i + 1], x1 = pts[i + 2], z1 = pts[i + 3];
            int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
            for (int k = 0; k <= n; k++) {
                int x = x0 + (x1 - x0) * k / Math.max(1, n), z = z0 + (z1 - z0) * k / Math.max(1, n);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (get(x + dx, 0, z + dz) == null) {
                            set(x + dx, 0, z + dz, chance() < 0.1 ? "mu_gravel_b" : "mu_gravel");
                        }
                    }
                }
                if (k % 14 == 7 && get(x + 2, 0, z + 2) == null && empty(x + 2, 1, z + 2)) {
                    lampPost(x + 2, z + 2);
                }
            }
        }
    }

    private void paths() {
        // the promenade behind the museum
        for (int x = 8; x <= w - 9; x++) {
            for (int z = PROM_Z0; z <= PROM_Z1; z++) {
                if (get(x, 0, z) == null || get(x, 0, z).startsWith("asphalt")) {
                    set(x, 0, z, chance() < 0.08 ? "mu_gravel_b" : "mu_gravel");
                }
            }
            if ((x - 8) % 12 == 0) {
                lampPost(x, PROM_Z1 + 1);
            } else if ((x - 8) % 12 == 6 && x > 30 && x < w - 30) {
                set(x, 1, PROM_Z1 + 1, "mu_bench[facing=north]");
            }
        }
        // the walk down the museum's west side, the spokes off the promenade, the gates' walks
        walk(31, Museum.WZ0, 31, PROM_Z0);
        walk(BX, PROM_Z1, BX, BZ - 8);
        walk(BX + 8, BZ, 62, LZ - 6);
        walk(LX, PROM_Z1, LX, HZ0 - 1);
        walk(HX1 + 1, HZ0 - 2, 112, 124);
        walk(181, PROM_Z1, 181, GZ0 - 1);
        walk(181, GZ1 + 1, 181, 150, 154, 150);
        walk(4, 149, BX - 8, 140, BX - 7, BZ + 2);
        walk(w - 5, 150, GX1 + 1, 150);
        walk(LX, d - 5, LX, 200, LX, 182);
        walk(LX - 6, 196, 40, 196, 30, 192);
        walk(12, 160, BX, BZ + 8);
    }

    // ------------------------------------------------------------------ the rest of the park

    private void play() {
        kit.playground(12, 184);
        kit.court(22, 184);
        kit.sandbox(14, 194, 5, 4);
        for (int x = 12; x <= 30; x += 6) {
            set(x, 1, 182, "mu_bench[facing=south]");
        }
    }

    /** The founder on his plinth in the roundel inside the south gate. */
    private void statue() {
        int sx = LX, sz = 196;
        for (int x = sx - 4; x <= sx + 4; x++) {
            for (int z = sz - 4; z <= sz + 4; z++) {
                if (Math.hypot(x - sx, z - sz) <= 4.5) {
                    set(x, 0, z, "pt_stone_flags");
                }
            }
        }
        fill(sx - 1, 1, sz - 1, sx + 1, 1, sz + 1, "mu_rustic");
        set(sx, 2, sz, "mu_lime");
        set(sx, 3, sz, "ch_statue[facing=north]");
        set(sx, 4, sz, "ch_statue_top[facing=north]");
        set(sx, 2, sz - 1, "cc_plaque[facing=north]");
    }

    /** The families' camp round the bandstand: the tents, the barrels, the water, their board at the walk. */
    private void camp() {
        int[][] tents = {{8, 96}, {18, 96}, {52, 96}, {8, 140}, {56, 106}, {20, 150}};
        String[] labels = {"shelter", "shelter", "supply", "shelter", "triage", "shelter"};
        for (int i = 0; i < tents.length; i++) {
            int lx = tents[i][0], lz = tents[i][1];
            if (free(lx, lz, lx + 5, lz + 9)) {
                tent(lx, lz, 10, labels[i]);
            }
        }
        for (int[] f : new int[][] {{30, 112}, {50, 142}, {26, 138}}) {
            if (free(f[0] - 1, f[1] - 1, f[0] + 1, f[1] + 1)) {
                set(f[0], 0, f[1], "burnt_ground");
                set(f[0], 1, f[1], "barrel");
            }
        }
        for (int x = 46; x <= 50; x++) {
            if (empty(x, 1, 116)) {
                set(x, 1, 116, chance() < 0.5 ? "st_pallet_water_half[facing=north]" : "sc_cases_water[facing=north]");
            }
        }
        // the board on its posts at the west walk
        for (int y = 1; y <= 2; y++) {
            set(10, y, 145, "fence_post");
            set(12, y, 145, "fence_post");
        }
        fill(10, 2, 145, 12, 2, 145, "wood_panel");
        picture("mu_board_camp_", 3, 1, 10, 2, 146, "south");
    }

    private boolean free(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (get(x, 0, z) != null || !empty(x, 1, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The trenches in the south-east corner: two filled and crossed, two open with the bags in them, the earth heaped beside. */
    private void burials() {
        int x0 = 140, x1 = 168;
        for (int t = 0; t < 4; t++) {
            int z = 180 + t * 6;
            boolean open = t >= 2;
            for (int x = x0; x <= x1; x++) {
                for (int dz = 0; dz <= 1; dz++) {
                    if (get(x, 0, z + dz) != null) {
                        continue;
                    }
                    if (open) {
                        set(x, -1, z + dz, "minecraft:coarse_dirt");
                        set(x, 0, z + dz, x % 2 == 0 && chance() < 0.85 ? "bunk_bodybag[facing=east]" : "air");
                    } else {
                        set(x, 0, z + dz, "minecraft:coarse_dirt");
                        set(x, 1, z + dz, "ch_grave_mound");
                    }
                }
                if (!open && x % 3 == 0 && get(x, 0, z - 1) == null) {
                    set(x, 0, z - 1, "minecraft:grass_block");
                    set(x, 1, z - 1, chance() < 0.85 ? "ch_grave_cross[facing=north]" : "home_plant_dead");
                }
                if (open && get(x, 0, z + 2) == null) {
                    set(x, 0, z + 2, "minecraft:coarse_dirt");
                    if (chance() < 0.7) {
                        set(x, 1, z + 2, "ch_grave_mound");
                    }
                }
            }
        }
        for (int y = 1; y <= 2; y++) {
            set(x0 - 2, y, 176, "fence_post");
            set(x0, y, 176, "fence_post");
        }
        fill(x0 - 2, 2, 176, x0, 2, 176, "wood_panel");
        picture("mu_board_burial_", 3, 1, x0 - 2, 2, 175, "north");
        p.vehicle(176, 1, 192, 80 + (float) (chance() * 20), "pickup", rng.nextLong());
        for (int x = x0; x <= x1; x += 4) {
            if (chance() < 0.6) {
                set(x, 1, 178, "st_barrier[facing=north]");
            }
        }
    }

    /** The iron railings round the park on their stone curb, the piers, the gates left open. */
    private void railings() {
        int e = w - 5, s = d - 5;
        for (int x = 4; x <= e; x++) {
            rail(x, s, "x", x >= LX - 4 && x <= LX + 4);
        }
        for (int z = Museum.WZ0; z <= s; z++) {
            rail(4, z, "z", z >= 146 && z <= 152);
            if (z > YZ1 + 2) {
                rail(e, z, "z", z >= 147 && z <= 153);
            }
        }
        for (int x = YX0 - 1; x <= e; x++) {
            rail(x, YZ1 + 2, "x", false);
        }
        // the name at the south gate
        for (int x = LX + 6; x <= LX + 17; x++) {
            set(x, 1, s - 1, "mu_lime");
            set(x, 2, s - 1, "mu_lime");
        }
        row("mu_name_park_", 10, LX + 7, 2, s, "south", 0.02);
    }

    private void rail(int x, int z, String axis, boolean gate) {
        String g = get(x, 0, z);
        if (g != null && (g.equals("asphalt") || g.equals("asphalt_crack") || g.startsWith("mu_gravel") || g.equals("pt_stone_flags"))) {
            gate = true;
        }
        if (gate) {
            return;
        }
        set(x, 0, z, "mu_lime");
        boolean pier = (axis.equals("x") ? x : z) % 8 == 0;
        set(x, 1, z, pier ? "cv_pier_stone" : chance() < 0.04 ? "air" : "cv_iron_fence[axis=" + axis + "]");
    }

    /** The ice cream cart by the south walk, bins along the walks. */
    private void furnish() {
        set(LX + 3, 1, 186, "mu_icecream[facing=west]");
        set(LX + 4, 1, 186, "mu_parasol");
        for (int[] t : new int[][] {{LX + 2, 100}, {LX - 2, 100}, {BX + 2, 100}, {181 + 2, 100}, {LX + 2, 190}}) {
            if (empty(t[0], 1, t[1])) {
                set(t[0], 1, t[1], chance() < 0.3 ? "ext_trash_can_tipped[facing=" + dir() + "]" : "ext_trash_can");
            }
        }
        // the street's own: news boxes, the payphone and the hydrants by the plaza
        for (int k = 0; k < 3; k++) {
            set(70 + k, 1, 5, "gs_news_" + k + "[facing=north]");
        }
        set(140, 1, 5, "gs_payphone[facing=north]");
        set(20, 1, 5, "fs_hydrant[facing=north]");
        for (int x = 40; x <= 172; x += 12) {
            if (x < Museum.QX0 - 2 || x > Museum.QX1 + 2) {
                lampPost(x, 6);
            }
        }
    }

    /** The old trees over the lawns, wherever the ground is still free. */
    private void trees() {
        for (int gx = 8; gx < w - 8; gx += 7) {
            for (int gz = 8; gz < d - 8; gz += 7) {
                int x = gx + rng.nextInt(4), z = gz + rng.nextInt(4);
                if (!free(x - 2, z - 2, x + 2, z + 2) || chance() < 0.25 || lakeE(x, z) < 1.5 && lakeE(x, z) > 0.5) {
                    continue;
                }
                if (z < PROM_Z1 + 3 && x > Museum.MX0 - 3 && x < Museum.MX1 + 3 || Math.hypot(x - BX, z - BZ) < 14) {
                    continue;
                }
                set(x, 0, z, "minecraft:grass_block");
                double r = chance();
                Trees.Kind k = r < 0.3 ? Trees.Kind.OAK : r < 0.55 ? Trees.Kind.MAPLE : r < 0.75 ? Trees.Kind.SYCAMORE
                        : r < 0.85 ? Trees.Kind.YOUNG : r < 0.92 ? Trees.Kind.PINE : r < 0.96 ? Trees.Kind.SICK : Trees.Kind.DEAD;
                Trees.grow(Kit.canopy(p), k, x, 1, z, rng);
            }
        }
    }

    private void litter() {
        for (int i = 0; i < 220; i++) {
            int x = 5 + rng.nextInt(w - 10), z = 5 + rng.nextInt(d - 10);
            String g = get(x, 0, z);
            if (g != null && (g.startsWith("mu_gravel") || g.equals("pt_stone_flags")) && empty(x, 1, z)) {
                set(x, 1, z, any("paper", "paper", "trash_bag[facing=" + dir() + "]", "blood_splat", "debris_chunk[facing=" + dir() + "]"));
            }
        }
    }
}
