package com.zomboidcraft.world.county;

/**
 * {@link Ballpark}'s field as the Guard left it, an evacuation landing zone: the two pads painted
 * on the outfield, the helicopter that never lifted off one of them, the tents in left-centre, the
 * hesco and the sandbags, the cots on the infield where people waited for their flight, the dead
 * laid out along the third-base wall; the service ramp the trucks came down; the plazas outside.
 */
final class BallparkLz {
    private static final int EV = Ballpark.EV, CONC = Ballpark.CONC, HX = Ballpark.HX, HZ = Ballpark.HZ;
    /** The pads' centres, (along the first-base line, along the third). */
    private static final int[][] PADS = {{62, 26}, {26, 62}};
    private final Ballpark a;

    BallparkLz(Ballpark a) {
        this.a = a;
    }

    void build() {
        lawn();
        pads();
        tents();
        defences();
        cots();
        dead();
        ramp();
        plazas();
    }

    private static int x(int along) {
        return HX - along;
    }

    private static int z(int along) {
        return HZ + along;
    }

    /** On the field's grass (or clay) with nothing on it yet. */
    private boolean clear(int x, int z) {
        return x >= 0 && z >= 0 && x < a.w && z < a.d && a.field[x][z] && a.empty(x, EV + 1, z) && a.empty(x, EV + 2, z);
    }

    // ------------------------------------------------------------------ the pads

    private void pads() {
        for (int k = 0; k < PADS.length; k++) {
            int cx = x(PADS[k][0]), cz = z(PADS[k][1]);
            for (int i = -10; i <= 10; i++) {
                for (int j = -10; j <= 10; j++) {
                    double r = Math.hypot(i, j);
                    boolean ring = r >= 8.5 && r < 10;
                    boolean h = (Math.abs(i) == 3 && Math.abs(j) <= 4) || (j == 0 && Math.abs(i) <= 3);
                    if (!a.field[cx + i][cz + j] || !(ring || h)) {
                        continue;
                    }
                    a.set(cx + i, EV, cz + j, a.chance() < 0.12 ? "bp_grass_rough" : ring ? "bp_grass_paint_y" : "bp_grass_paint");
                }
            }
            // the pad's lights round the ring, most knocked flat
            for (int deg = 0; deg < 360; deg += 45) {
                int lx = cx + (int) Math.round(Math.cos(Math.toRadians(deg)) * 11), lz = cz + (int) Math.round(Math.sin(Math.toRadians(deg)) * 11);
                if (clear(lx, lz) && a.chance() < 0.6) {
                    a.set(lx, EV + 1, lz, a.chance() < 0.5 ? "st_cone" : "debris_chunk");
                }
            }
        }
        // the one that never lifted: on the first pad, rotor slack
        if (a.chance() < 0.6) {
            a.p.vehicle(x(PADS[0][0]) + 0.5, EV + 1, z(PADS[0][1]) + 0.5, (float) (a.chance() * 360), "heli_hornbill", a.rng.nextLong());
        }
        // the jeeps and the truck by the ramp
        a.p.vehicle(x(88) + 0.5, EV + 1, z(14) + 0.5, 270 + (float) (a.chance() * 30 - 15), "jeep,jeep_open", a.rng.nextLong());
        a.p.vehicle(x(80) + 0.5, EV + 1, z(20) + 0.5, 250 + (float) (a.chance() * 30 - 15), "box_truck", a.rng.nextLong());
        a.p.vehicle(x(44) + 0.5, EV + 1, z(78) + 0.5, (float) (a.chance() * 360), "jeep,jeep_open,ambulance", a.rng.nextLong());
    }

    // ------------------------------------------------------------------ the camp

    /** The tents in left-centre: triage, the shelter, the supplies, their pallets. */
    private void tents() {
        String[] labels = {"triage", "shelter", "supply", "shelter"};
        for (int k = 0; k < labels.length; k++) {
            int lx = x(78 - k * 8), lz = z(66);
            a.tent(lx, EV, lz, 8, labels[k]);
        }
        for (int k = 0; k < 8; k++) {
            int px = x(52 - (k % 4) * 2), pz = z(68 + (k / 4) * 2);
            if (clear(px, pz)) {
                a.set(px, EV + 1, pz, a.any("st_pallet_water", "st_pallet_meals", "st_pallet_water_half", "st_pallet_cots", "pk_military_crate"));
            }
        }
        // the camo net over the operations table by the pads
        int ox = x(46), oz = z(42);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if (Math.abs(i) == 2 && Math.abs(j) == 2) {
                    for (int y = EV + 1; y <= EV + 3; y++) {
                        a.set(ox + i, y, oz + j, "fence_post");
                    }
                }
                a.set(ox + i, EV + 4, oz + j, "camo_net");
            }
        }
        a.set(ox, EV + 1, oz, "bunk_map_table[facing=east]");
        a.set(ox + 1, EV + 1, oz + 1, "bunk_radio_desk[facing=east]");
        a.set(ox - 1, EV + 1, oz - 1, "trade_folding_chair[facing=west]");
        a.set(ox + 1, EV + 1, oz - 1, "bunk_st_generator[facing=north]");
    }

    /** The hesco across left field, the sandbag nest by the pads, the wire. */
    private void defences() {
        for (int al = 20; al <= 54; al++) {
            int x = x(al), z = z(92);
            if (Math.floorMod(al, 9) >= 7 || !clear(x, z)) {
                continue;
            }
            a.set(x, EV + 1, z, "hesco");
            a.set(x, EV + 2, z, a.chance() < 0.8 ? "hesco" : "air");
        }
        int nx = x(74), nz = z(10);
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                if ((Math.abs(i) == 2 || Math.abs(j) == 2) && !(i == 0 && j == 2) && clear(nx + i, nz + j)) {
                    a.set(nx + i, EV + 1, nz + j, "sandbags");
                    if (a.chance() < 0.7) {
                        a.set(nx + i, EV + 2, nz + j, "sandbags");
                    }
                }
            }
        }
        a.set(nx, EV + 1, nz, "bunk_crate_ammo[facing=south]");
        for (int al = 4; al < 30; al++) {
            int x = x(al), z = z(-3);
            if (a.chance() < 0.7 && clear(x, z) && Math.floorMod(al, 6) != 0) {
                a.set(x, EV + 1, z, "jersey_barrier[facing=north]");
            }
        }
    }

    /** The cots on the infield grass and behind second, where people waited for the next flight. */
    private void cots() {
        for (int ai = 30; ai <= 48; ai += 2) {
            for (int ci = 30; ci <= 48; ci += 3) {
                int x = x(ai), z = z(ci);
                if (a.chance() < 0.75 && clear(x, z) && clear(x, z + 1)) {
                    boolean b = a.chance() < 0.4;
                    a.set(x, EV + 1, z, (b ? "sc_cot_head_b" : "sc_cot_head") + "[facing=north]");
                    a.set(x, EV + 1, z + 1, (b ? "sc_cot_foot_b" : "sc_cot_foot") + "[facing=north]");
                    if (a.chance() < 0.3 && clear(x + 1, z)) {
                        a.set(x + 1, EV + 1, z, a.any("sc_cases_water[facing=north]", "trash_bag[facing=north]", "home_books_stack_low[facing=north]",
                                "home_blood_pool"));
                    }
                }
            }
        }
        for (int k = 0; k < 160; k++) {
            int x = a.rng.nextInt(a.w), z = a.rng.nextInt(a.d);
            if (clear(x, z)) {
                a.set(x, EV + 1, z, a.any("paper", "paper", "trash_bag[facing=" + a.dir() + "]", "blood_splat", "glass_shards"));
            }
        }
    }

    /** The dead, bagged and laid in rows along the third-base wall under a tarp's corner. */
    private void dead() {
        for (int al = 40; al < 64; al += 2) {
            for (int b = -6; b <= -3; b += 3) {
                int[] q = Ballpark.at(1, al, b), r = Ballpark.at(1, al, b + 1);
                if (a.chance() < 0.75 && clear(q[0], q[1]) && clear(r[0], r[1])) {
                    a.set(q[0], EV + 1, q[1], "bunk_bodybag_head[facing=west]");
                    a.set(r[0], EV + 1, r[1], "bunk_bodybag[facing=west]");
                }
            }
        }
        int[] s = Ballpark.at(1, 51, -1);
        a.sign("morgue", s[0], EV + 3, s[1], "west");
    }

    // ------------------------------------------------------------------ the ramp

    /** The service ramp down through the right-field bleachers by the pole, the trucks' way onto the field. */
    private void ramp() {
        int z0 = HZ + 3, z1 = HZ + 6, bottom = 53, top = 31;
        for (int x = top; x <= bottom; x++) {
            int f = rampY(x, bottom);
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                if (a.field[x][z]) {
                    continue;
                }
                boolean wall = z == z0 - 1 || z == z1 + 1;
                for (int y = f - 2; y <= CONC + 3; y++) {
                    a.set(x, y, z, wall ? (y <= CONC ? a.brick(x, y, z) : "air") : y < f ? Ballpark.FILL : y == f ? (a.chance() < 0.15 ? "asphalt_crack" : "asphalt") : "air");
                }
                if (wall) {
                    a.set(x, CONC + 1, z, Ballpark.RAIL + "[facing=" + (z < z0 ? "south" : "north") + "]");
                } else if (rampY(x - 1, bottom) > f) {
                    a.set(x, f + 1, z, "stairs_concrete[facing=west]");
                }
            }
        }
        for (int z = z0; z <= z1; z++) {
            a.set(top - 1, CONC + 1, z, "air");
        }
    }

    private static int rampY(int x, int bottom) {
        return x >= bottom ? EV : Math.min(CONC, EV + (int) Math.floor((bottom - x) / 2.0));
    }

    // ------------------------------------------------------------------ outside

    /**
     * The lawn over the outfield's plaza down toward the water: grass between the walks (a ring
     * round the bleachers' back wall, spokes out from it), the old trees on it.
     */
    private void lawn() {
        for (int x = 0; x < a.w; x++) {
            for (int z = 0; z < a.d; z++) {
                if (Ballpark.grand(x, z) || a.band[x][z] < Ballpark.BACK + 6 || spoke(x, z) || x < 2 || z < 2 || x > a.w - 3 || z > a.d - 3) {
                    continue;
                }
                a.set(x, CONC, z, "minecraft:grass_block");
            }
        }
        for (int gx = 4; gx < a.w - 4; gx += 8) {
            for (int gz = 4; gz < a.d - 4; gz += 8) {
                int x = gx + a.rng.nextInt(4), z = gz + a.rng.nextInt(4);
                if (!lawnAround(x, z, 3) || a.chance() < 0.3 || Math.abs(x - 20) < 6 && Math.abs(z - 100) < 10) {
                    continue;
                }
                double r = a.chance();
                Trees.Kind k = r < 0.3 ? Trees.Kind.OAK : r < 0.55 ? Trees.Kind.MAPLE : r < 0.75 ? Trees.Kind.SYCAMORE
                        : r < 0.88 ? Trees.Kind.YOUNG : r < 0.95 ? Trees.Kind.SICK : Trees.Kind.DEAD;
                Trees.grow(Kit.canopy(a.p), k, x, 1, z, a.rng);
            }
        }
    }

    /** A walk out from the bleachers: every fifteen degrees round home, three wide. */
    private static boolean spoke(int x, int z) {
        int ax = HX - x, cz = z - HZ;
        double r = Math.hypot(ax, cz), t = Math.toDegrees(Math.atan2(cz, ax));
        for (int k = -30; k <= 120; k += 15) {
            if (Math.abs(t - k) * Math.PI / 180 * r < 1.6) {
                return true;
            }
        }
        return false;
    }

    private boolean lawnAround(int x, int z, int n) {
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                String g = a.get(x + i, CONC, z + j);
                if (g == null || !g.equals("minecraft:grass_block") || !a.empty(x + i, CONC + 1, z + j)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The plazas: the lamps, the benches, the Guard's barriers at the gates, the cars left where they stopped. */
    private void plazas() {
        for (int x = 2; x < a.w - 2; x += 9) {
            for (int z = 2; z < a.d - 2; z += 9) {
                int b = a.band[x][z];
                boolean out = Ballpark.grand(x, z) ? b > Ballpark.FACADE + 1 : b > Ballpark.BACK + 2;
                if (!out || !a.empty(x, CONC + 1, z) || !a.empty(x, CONC + 2, z)) {
                    continue;
                }
                double q = a.chance();
                if (q < 0.35) {
                    a.lampPost(x, z);
                } else if (q < 0.5) {
                    a.set(x, CONC + 1, z, "st_bench[facing=" + Ballpark.in(x, z) + "]");
                } else if (q < 0.6) {
                    a.set(x, CONC + 1, z, "planter");
                } else if (q < 0.75) {
                    a.set(x, CONC + 1, z, a.any("st_barrier", "st_barrier_down", "jersey_barrier") + "[facing=" + Ballpark.in(x, z) + "]");
                } else if (q < 0.85) {
                    a.set(x, CONC + 1, z, a.any("paper", "trash_bag[facing=north]", "blood_splat", "debris_chunk"));
                }
            }
        }
        // the queue outside the main gate between its barriers, the checkpoint
        int g = Ballpark.GATES[0];
        for (int k = 0; k < 10; k++) {
            int[] l = Ballpark.at(0, g - 1, Ballpark.FACADE + 2 + k), r = Ballpark.at(0, g + 4, Ballpark.FACADE + 2 + k);
            if (l[1] >= 0) {
                a.set(l[0], CONC + 1, l[1], "st_barrier[facing=east]");
                a.set(r[0], CONC + 1, r[1], "st_barrier[facing=west]");
            }
        }
        int[] c = Ballpark.at(0, g + 6, Ballpark.FACADE + 3);
        if (c[1] >= 0) {
            a.set(c[0], CONC + 1, c[1], "sandbags");
            a.set(c[0] - 1, CONC + 1, c[1], "sandbags");
            a.set(c[0], CONC + 1, c[1] + 1, "trade_banquet_table[facing=north]");
        }
        a.kit.car(60.5, 3.5, 92, 0, 5.6);
        a.kit.car(88.5, 2.5, 265, 0, 5.6);
        a.kit.car(209.5, 112.5, 4, 0, 5.6);
        a.kit.car(30.5, 22.5, 250, 7, 9.5);
    }
}
