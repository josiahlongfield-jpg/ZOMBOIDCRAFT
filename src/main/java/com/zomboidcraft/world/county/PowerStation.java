package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Harbor Point Generating Station, Grayhaven Light &amp; Power's coal plant on a lot of three blocks
 * by two with its front to the street the subway runs under. Along the street the plant office
 * ({@link PowerHalls#office}) with the control room in it and the turbine hall beside it, its
 * tall arched windows; behind them the boiler house with the four stacks and the machine shop;
 * behind those, over the yard road, the coal yard with its pile, the crusher tower and the
 * gallery up into the boiler house, the fuel oil tanks, and the switchyard ({@link PowerYards}).
 *
 * <p>The night shift kept Unit 3 on for Mercy General and the shelters to the end, living in the
 * control room until the coal ran out: the banner off the office, the barricaded doors, the
 * grease pencil on the one-line diagram.
 *
 * <p>Drawn in the lot's frame: x along the front (the street), z from the front back, the ground
 * at y = 0.
 */
final class PowerStation extends Grounds {
    /** The plant office: storeys, its walls (inclusive), the roof. */
    static final int S = 5, ROOF_A = 15;
    static final int AX0 = 6, AX1 = 40, AZ0 = 8, AZ1 = 44;
    /** The turbine hall east of the office (sharing its east wall), its roof. */
    static final int TX1 = 126, TROOF = 28;
    /** The boiler house behind the hall (sharing its back wall), the tripper floor, the roof. */
    static final int BZ1 = 92, TRIP = 34, BROOF = 46;
    /** The machine shop and stores behind the office, its roof. */
    static final int MZ0 = 50, MROOF = 10;
    /** The yard road across the lot behind the boiler house. */
    static final int RZ0 = 93, RZ1 = 99;
    /** The coal yard, the service road, the switchyard. */
    static final int CX1 = 60, SX0 = 70, SZ0 = 106, YZ1 = 208;

    PowerStation(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.4, 0.2, 0.35);
    }

    @Override
    String signs() {
        return "pw_sgn_";
    }

    void build() {
        ground();
        new PowerHalls(this).build();
        new PowerYards(this).build();
        front();
        gate();
        litter();
    }

    // ------------------------------------------------------------------ the ground

    /** Sidewalk round the lot, the lawn strip along the front, asphalt for the roads and aprons, gravel in the yards. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                String g;
                if (edge) {
                    g = chance() < 0.1 ? "sidewalk_cracked" : chance() < 0.05 ? "sidewalk_moss" : "sidewalk";
                } else if (z < AZ0) {
                    g = chance() < 0.3 ? "bp_grass_rough" : "bp_grass_b";
                } else if (z > RZ1 && x < CX1 + 1 && x > 4 || x > SX0 - 1 && z >= SZ0 && z <= YZ1) {
                    g = chance() < 0.12 ? "mu_gravel" : "rw_ballast";
                } else {
                    double r = chance();
                    g = r < 0.12 ? "asphalt_crack" : r < 0.18 ? "asphalt_patch" : r < 0.21 ? "asphalt_moss" : "asphalt";
                }
                set(x, 0, z, g);
            }
        }
        // the curb cuts: the yard road out to both side streets, the turbine hall's apron
        for (int z = RZ0; z <= RZ1; z++) {
            for (int x = 0; x < 4; x++) {
                set(x, 0, z, "asphalt");
                set(w - 1 - x, 0, z, "asphalt");
            }
        }
        for (int z = 20; z <= 26; z++) {
            for (int x = w - 4; x < w; x++) {
                set(x, 0, z, "asphalt");
            }
        }
        for (int x = 8; x < w - 8; x += 6) {
            set(x, 0, (RZ0 + RZ1) / 2, "road_line_yellow[axis=x]");
        }
    }

    // ------------------------------------------------------------------ out front

    /** The lawn along the street: the monument sign, the flagpole, the lamps, the walk to the office doors. */
    private void front() {
        for (int z = 4; z < AZ0; z++) {
            for (int x = 11; x <= 16; x++) {
                set(x, 0, z, z % 2 == 0 ? "cc_plaza_band" : "cc_plaza");
            }
        }
        // the monument: a brick pylon with the company's seal on both faces
        int mx = 30, mz = 5;
        for (int x = mx; x <= mx + 2; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, mz, brick(x, y, mz));
                set(x, y, mz + 1, brick(x, y, mz + 1));
            }
            set(x, 5, mz, "cc_coping");
            set(x, 5, mz + 1, "cc_coping");
        }
        picture("pw_logo_", 2, 2, mx, 2, mz - 1, "north");
        picture("pw_logo_", 2, 2, mx + 1, 2, mz + 2, "south");
        set(7, 1, 5, "cv_flagpole_base");
        for (int y = 2; y <= 9; y++) {
            set(7, y, 5, "cv_flagpole");
        }
        set(7, 10, 5, "cv_flagpole_top[facing=east]");
        for (int x = 44; x <= TX1 - 4; x += 12) {
            set(x, 1, 5, "planter");
            if (chance() < 0.4) {
                set(x + 3, 1, 5, "st_bench[facing=north]");
            }
        }
        for (int x = 10; x <= TX1; x += 24) {
            lampPost(x, 6);
        }
        // the crew's last word, off the office's parapet
        row("pw_banner_", 6, 22, 13, AZ0 - 1, "north", 0.0);
    }

    // ------------------------------------------------------------------ the gate

    /**
     * The plant gate on the yard road at the east side street: the guard shack, the sliding gate the
     * line truck went through, the stop sign; the gate at the west end chained shut.
     */
    private void gate() {
        int gx = w - 6;
        // the shack: a brick booth with windows all round
        int sx0 = gx - 6, sz0 = SZ0 - 6;
        for (int x = sx0; x <= sx0 + 4; x++) {
            for (int z = sz0; z <= sz0 + 3; z++) {
                boolean wall = x == sx0 || x == sx0 + 4 || z == sz0 || z == sz0 + 3;
                set(x, 0, z, "floor_concrete");
                for (int y = 1; y <= 3; y++) {
                    if (wall) {
                        boolean win = y == 2 && x != sx0 && x != sx0 + 4 || y == 2 && z != sz0 && z != sz0 + 3;
                        String f = z == sz0 ? "north" : z == sz0 + 3 ? "south" : x == sx0 ? "west" : "east";
                        set(x, y, z, win ? (chance() < 0.4 ? "cc_win_broken" : "cc_win") + "[facing=" + f + "]" : brick(x, y, z));
                    }
                }
                set(x, 4, z, "un_roof");
            }
        }
        door(sx0 + 2, 0, sz0, "flat_door", "north");
        set(sx0 + 1, 1, sz0 + 1, "reception_desk[facing=north]");
        set(sx0 + 3, 1, sz0 + 2, "office_chair_fallen[facing=" + dir() + "]");
        set(sx0 + 1, 1, sz0 + 2, "filing_cabinet[facing=east]");
        sign("gate", sx0 + 1, 3, sz0 - 1, "north");
        // the sliding gate across the road, one leaf down, the truck through it
        for (int z = RZ0; z <= RZ1; z++) {
            boolean down = z >= RZ0 + 3;
            if (!down) {
                set(gx, 1, z, "chainlink[axis=z]");
                set(gx, 2, z, "chainlink[axis=z]");
            }
        }
        set(gx, 1, RZ0 - 1, "fence_post");
        set(gx, 2, RZ0 - 1, "fence_post");
        for (int z = RZ0 + 3; z <= RZ1; z++) {
            set(gx - 2 - (z - RZ0 - 3) / 3, 1, z, chance() < 0.5 ? "rubble" : "glass_shards");
        }
        p.vehicle(gx - 9.5, 1, RZ0 + 4.5, 250f, "box_truck", rng.nextLong());
        if (chance() < 0.7) {
            p.vehicle(gx - 24.5, 1, RZ0 + 2.5, 270f, "pickup", rng.nextLong());
        }
        // the west gate, chained
        for (int z = RZ0; z <= RZ1; z++) {
            set(5, 1, z, "chainlink[axis=z]");
            set(5, 2, z, "chainlink[axis=z]");
            set(5, 3, z, "st_razor");
        }
        set(5, 1, (RZ0 + RZ1) / 2, "cv_chain");
        for (int x = 7; x <= 11; x += 2) {
            set(x, 1, RZ0 + 1, "jersey_barrier[facing=west]");
        }
    }

    /** Paper and glass blown about, a few of the dead the crew bagged. */
    private void litter() {
        for (int k = 0; k < 220; k++) {
            int x = 4 + rng.nextInt(w - 8), z = 4 + rng.nextInt(d - 8);
            if (empty(x, 1, z) && get(x, 0, z) != null && !get(x, 0, z).equals("air")) {
                double r = chance();
                set(x, 1, z, r < 0.45 ? "paper" : r < 0.6 ? "glass_shards" : r < 0.75 ? "trash_bag[facing=" + dir() + "]" : "blood_splat");
            }
        }
        // body bags laid along the shop's wall by the alley
        for (int x = 9; x <= 21; x += 3) {
            if (chance() < 0.75 && empty(x, 1, MZ0 - 2) && empty(x + 1, 1, MZ0 - 2)) {
                set(x, 1, MZ0 - 2, "bunk_bodybag_head[facing=west]");
                set(x + 1, 1, MZ0 - 2, "bunk_bodybag[facing=west]");
            }
        }
    }

    // ------------------------------------------------------------------ shared

    String dir() {
        return DIRS[rng.nextInt(4)];
    }

    /** The plant's red brick: a darker brick here and there, a stain, a crack, moss at the foot, soot up high. */
    String brick(int x, int y, int z) {
        long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
        int k = (int) Math.floorMod(h >>> 3, 100L);
        return k < 6 ? "brick_red_b" : k < 9 ? "brick_red_stain" : k < 10 ? "brick_red_crack"
                : k < 13 && y < 3 ? "brick_red_moss" : k < 12 && y > 30 ? "brick_red_soot" : "brick_red";
    }

    /** A troffer ceiling one under the slab at ys, over a room's cells. */
    void ceiling(int ys, int x0, int z0, int x1, int z1, double on) {
        int cy = ys - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!empty(x, cy, z) || shaft(x, z)) {
                    continue;
                }
                boolean lamp = (x - x0) % 3 == 1 && (z - z0) % 3 == 1;
                double r = chance();
                set(x, cy, z, lamp ? (power && r < on ? "fit_troffer_on" : r < 0.05 ? "fit_troffer_flicker" : "fit_troffer")
                        : r < 0.04 ? "fit_ceiling_fallen" : r < 0.08 ? "fit_ceiling_open" : "fit_ceiling");
            }
        }
    }

    /** Inside one of the stair shafts (the office's, the boiler house's, the crusher tower's): fills keep out. */
    boolean shaft(int x, int z) {
        return x >= PowerHalls.ST_X && x <= PowerHalls.ST_X + 4 && z >= PowerHalls.ST_Z && z <= PowerHalls.ST_Z + 9
                || x >= PowerHalls.BST_X && x <= PowerHalls.BST_X + 4 && z >= PowerHalls.BST_Z && z <= PowerHalls.BST_Z + 9
                || x >= PowerYards.TST_X && x <= PowerYards.TST_X + 4 && z >= PowerYards.TST_Z && z <= PowerYards.TST_Z + 9;
    }

    /** A chainlink fence along x or z with razor wire on top, a post every eight. */
    void fence(int x0, int z0, int x1, int z1, double breach) {
        boolean alongX = z0 == z1;
        int n = alongX ? x1 - x0 : z1 - z0;
        for (int i = 0; i <= n; i++) {
            int x = alongX ? x0 + i : x0, z = alongX ? z0 : z0 + i;
            if (!empty(x, 1, z) || chance() < breach) {
                continue;
            }
            boolean post = i % 8 == 0;
            for (int y = 1; y <= 2; y++) {
                set(x, y, z, post ? "fence_post" : "chainlink[axis=" + (alongX ? "x" : "z") + "]");
            }
            if (chance() < 0.85) {
                set(x, 3, z, "st_razor");
            }
        }
    }
}
