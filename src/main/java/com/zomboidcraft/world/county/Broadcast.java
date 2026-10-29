package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The Grayhaven Broadcast Center, WGHV Channel 6 and its FM sister, on a lot of two blocks by two
 * with its front to the street the subway runs under: Broadcast House ({@link BroadcastHouse}), the
 * brown-brick block of three floors with the windowless studio wing beside it; behind them the
 * lattice tower ({@link BroadcastTower}) in its fenced compound, the transmitter building and the
 * ice bridge over to the tower, the farm of satellite dishes; the staff lot between, the news vans
 * in the service yard along the east side, the monument sign on the plaza out front.
 *
 * <p>The station stayed on the air as the county's emergency broadcaster to the end: the Guard's
 * sandbags and wire across the plaza, the bedsheet off the parapet, the staff living on the top
 * floor while the tape ran.
 *
 * <p>Drawn in the lot's frame: x along the front, z from the front (the street) back, the ground at
 * y = 0.
 */
final class Broadcast extends Grounds {
    /** Broadcast House: storeys, its walls (inclusive), the roof's slab. */
    static final int S = 5, ROOF = 15;
    static final int AX0 = 6, AX1 = 68, AZ0 = 12, AZ1 = 66;
    /** The studio wing east of it, set back behind its own forecourt, and its roof. */
    static final int BX1 = 113, BZ0 = 20, ROOF_B = 11;
    /** The corridor through every floor, its walls at HZ0 - 1 and HZ1 + 1. */
    static final int HZ0 = 25, HZ1 = 27;
    /** The tower's centre, its compound's fence, the transmitter building, the dish farm. */
    static final int TCX = 34, TCZ = 104;
    static final int KX0 = 16, KX1 = 52, KZ0 = 84, KZ1 = 124;
    static final int XX0 = 58, XX1 = 84, XZ0 = 90, XZ1 = 118;
    static final int DX0 = 92, DX1 = 128, DZ0 = 76, DZ1 = 126;

    Broadcast(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.4, 0.15, 0.35);
    }

    @Override
    String signs() {
        return "tv_sgn_";
    }

    void build() {
        ground();
        new BroadcastHouse(this).build();
        new BroadcastTower(this).build();
        plaza();
        lots();
        guard();
        litter();
    }

    // ------------------------------------------------------------------ the ground

    /** Sidewalk round the lot, the plaza's paving in front, asphalt for the lots, ballast in the compounds. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                String g;
                if (edge) {
                    g = chance() < 0.1 ? "sidewalk_cracked" : chance() < 0.05 ? "sidewalk_moss" : "sidewalk";
                } else if (z < AZ0 && x <= AX1) {
                    g = (x - 4) % 6 == 0 || (z - 4) % 6 == 0 ? "cc_plaza_band" : chance() < 0.12 ? "cc_plaza_b" : "cc_plaza";
                } else if (inCompound(x, z) || inDishes(x, z)) {
                    g = chance() < 0.2 ? "mu_gravel_b" : "rw_ballast";
                } else {
                    double r = chance();
                    g = r < 0.12 ? "asphalt_crack" : r < 0.18 ? "asphalt_patch" : r < 0.21 ? "asphalt_moss" : "asphalt";
                }
                set(x, 0, z, g);
            }
        }
        // the curb cuts in from the side streets
        for (int z = 70; z <= 80; z++) {
            for (int x = 0; x < 4; x++) {
                set(x, 0, z, "asphalt");
            }
        }
        for (int z = 30; z <= 40; z++) {
            for (int x = w - 4; x < w; x++) {
                set(x, 0, z, "asphalt");
            }
        }
    }

    static boolean inCompound(int x, int z) {
        return x >= KX0 && x <= KX1 && z >= KZ0 && z <= KZ1;
    }

    static boolean inDishes(int x, int z) {
        return x >= DX0 && x <= DX1 && z >= DZ0 && z <= DZ1;
    }

    // ------------------------------------------------------------------ the plaza out front

    /** The monument sign by the street, the flags, the planters, the canopy over the doors. */
    private void plaza() {
        // the monument: a brick pylon three wide with the logo on both faces
        int mx = 8, mz = 5;
        for (int x = mx; x <= mx + 2; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, mz, brick(x, y, mz));
                set(x, y, mz + 1, brick(x, y, mz + 1));
            }
            set(x, 5, mz, "cc_coping");
            set(x, 5, mz + 1, "cc_coping");
        }
        picture("tv_logo_", 2, 2, mx, 2, mz - 1, "north");
        picture("tv_logo_", 2, 2, mx + 1, 2, mz + 2, "south");
        // the flags at the doors
        String[] tops = {"cv_flagpole_top", "cc_pole_state_top", "cc_pole_city_top"};
        for (int i = 0; i < 3; i++) {
            int x = 32 + i * 3, z = 6;
            set(x, 1, z, "cv_flagpole_base");
            for (int y = 2; y <= 8; y++) {
                set(x, y, z, "cv_flagpole");
            }
            set(x, 9, z, tops[i] + "[facing=east]");
        }
        // planters along the front and the benches between
        for (int x = 14; x <= 60; x += 8) {
            if (x >= 18 && x <= 30) {
                continue;
            }
            set(x, 1, 9, "planter");
            set(x + 1, 1, 9, "planter");
            if (chance() < 0.7) {
                set(x + 3, 1, 9, "st_bench[facing=north]");
            }
        }
        // the canopy over the doors: a precast slab on two posts, the name along its edge
        for (int x = 20; x <= 29; x++) {
            for (int z = 6; z < AZ0; z++) {
                set(x, 4, z, z == 6 ? "cc_precast_b" : "cc_precast");
            }
        }
        for (int y = 1; y <= 3; y++) {
            set(20, y, 6, "tv_post");
            set(29, y, 6, "tv_post");
        }
        for (int x = 21; x <= 28; x++) {
            if (power && chance() < 0.4) {
                set(x, 3, 8, "fit_downlight_on");
            }
        }
        // the studio wing's forecourt: the live trucks' bay
        for (int x = AX1 + 2; x < BX1; x++) {
            for (int z = AZ0; z < BZ0; z++) {
                if (x % 7 == 0 && z == AZ0 + 1) {
                    set(x, 0, z, "road_line_white[axis=z]");
                }
            }
        }
        p.vehicle(80.5, 1, 15.5, 90f, "van", rng.nextLong());
        if (chance() < 0.7) {
            p.vehicle(96.5, 1, 15.5, 90f, "step_van", rng.nextLong());
        }
    }

    /** The staff lot behind the building, the service yard down the east side with the vans. */
    private void lots() {
        for (int x = 8; x <= 88; x += 4) {
            for (int z = 70; z <= 80; z++) {
                if (z == 70 || z == 80) {
                    continue;
                }
                if (z == 71 || z == 79) {
                    set(x, 0, z, "road_line_white[axis=z]");
                }
            }
        }
        for (int i = 0; i < 9; i++) {
            if (chance() < 0.55) {
                int x = 10 + i * 8;
                p.vehicle(x + 0.5, 1, chance() < 0.5 ? 73.5 : 77.5, chance() < 0.5 ? 0f : 180f, "sedan,wagon,hatchback,coupe,pickup,suv", rng.nextLong());
            }
        }
        // the service yard: the scene dock's door, the news vans nosed out, the satellite truck
        for (int z = 22; z <= 64; z += 7) {
            set(BX1 + 3, 0, z, "road_line_white[axis=x]");
        }
        p.vehicle(122.5, 1, 26.5, 270f, "box_truck", rng.nextLong());
        p.vehicle(122.5, 1, 44.5, 270f, "van", rng.nextLong());
        if (chance() < 0.6) {
            p.vehicle(122.5, 1, 52.5, 270f, "van,step_van", rng.nextLong());
        }
        set(BX1 + 2, 1, 36, "dumpster[facing=west]");
        set(BX1 + 2, 1, 38, "dumpster[facing=west]");
        for (int z = 56; z <= 62; z += 2) {
            set(BX1 + 1, 1, z, chance() < 0.5 ? "tv_case[facing=east]" : "crate");
        }
        // lamps round the lots
        for (int x = 12; x <= 84; x += 24) {
            lampPost(x, 82);
        }
        for (int z = 14; z <= 66; z += 26) {
            lampPost(w - 6, z);
        }
    }

    /**
     * The Guard's post in front: the wire coiled across the plaza with its one gap at the doors, the
     * sandbag nest at the corner, the jersey barriers along the curb, the jeep, the sign.
     */
    private void guard() {
        for (int x = 5; x <= AX1 + 2; x++) {
            if (x >= 22 && x <= 27) {
                continue;
            }
            if (empty(x, 1, 3) && chance() < 0.85) {
                set(x, 1, 3, "st_razor");
            }
        }
        for (int x = 18; x <= 31; x += 2) {
            if (x == 22 || x == 24 || x == 26) {
                continue;
            }
            set(x, 1, 1, "jersey_barrier[facing=north]");
        }
        // the nest at the east corner of the plaza
        int nx = 60, nz = 4;
        for (int x = nx; x <= nx + 4; x++) {
            set(x, 1, nz, "sandbags[facing=north]");
            set(x, 2, nz, x == nx + 2 ? "air" : "sandbags[facing=north]");
        }
        for (int z = nz + 1; z <= nz + 3; z++) {
            set(nx, 1, z, "sandbags[facing=west]");
            set(nx + 4, 1, z, "sandbags[facing=east]");
        }
        set(nx + 2, 1, nz + 2, "crate");
        sign("ebs", 18, 2, 2, "north");
        if (chance() < 0.6) {
            p.vehicle(44.5, 1, 7.5, 90f, "jeep,jeep_open", rng.nextLong());
        }
        // the bedsheet hung off the parapet over the doors
        row("tv_banner_", 6, 30, 12, AZ0 - 1, "north", 0.0);
        row("tv_banner_", 6, 30, 11, AZ0 - 1, "north", 1.0);
    }

    /** Paper and glass blown about the lots and the plaza. */
    private void litter() {
        for (int k = 0; k < 160; k++) {
            int x = 4 + rng.nextInt(w - 8), z = 4 + rng.nextInt(d - 8);
            if (empty(x, 1, z) && get(x, 0, z) != null && !get(x, 0, z).equals("air")) {
                double r = chance();
                set(x, 1, z, r < 0.5 ? "paper" : r < 0.65 ? "glass_shards" : r < 0.8 ? "trash_bag[facing=" + dir() + "]" : "blood_splat");
            }
        }
    }

    // ------------------------------------------------------------------ shared

    /** The station's brown brick, weathered here and there. */
    String brick(int x, int y, int z) {
        long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
        int k = (int) ((h >>> 3) % 100);
        if (k < 0) {
            k = -k;
        }
        return k < 8 ? "brick_dark_b" : k < 12 ? "brick_dark_stain" : k < 15 ? "brick_dark_crack"
                : k < 17 && y < 3 ? "brick_dark_moss" : k < 19 && y > 8 ? "brick_dark_soot" : "brick_dark";
    }

    /** A troffer ceiling one under the slab at ys, over a room's cells. */
    void ceiling(int ys, int x0, int z0, int x1, int z1, double on) {
        int cy = ys - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!empty(x, cy, z)) {
                    continue;
                }
                boolean lamp = (x - x0) % 3 == 1 && (z - z0) % 3 == 1;
                double r = chance();
                set(x, cy, z, lamp ? (power && r < on ? "fit_troffer_on" : r < 0.05 ? "fit_troffer_flicker" : "fit_troffer")
                        : r < 0.04 ? "fit_ceiling_fallen" : r < 0.08 ? "fit_ceiling_open" : "fit_ceiling");
            }
        }
    }

    /** One of the station's promo posters on a wall facing that way. */
    void promo(int x, int y, int z, String facing) {
        set(x, y, z, "tv_promo_" + rng.nextInt(4) + "[facing=" + facing + "]");
    }
}
