package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven State University, the island's campus, on a lot of three blocks by three: the iron
 * railings round it on their brick wall, the gate with the university's name over it on the north
 * street, the quad behind it with its walks, its fountain and its rows of oaks, and round the quad
 * the halls: the library ({@link UniLibrary}) and the lecture hall ({@link UniLecture}) down the
 * west side, the science hall and the student union ({@link UniScience}, {@link UniUnion}) down the
 * east, Old Main ({@link UniOldMain}) closing the south end under its clock tower; the dormitory
 * tower ({@link UniDorm}) and the staff lot either side of the gate.
 *
 * <p>The governor shut it in the second week. The buses took who they could to the stadium; the
 * National Guard put its tents up on the quad and its sandbags in the gate; the ones who stayed
 * locked themselves in the dorm and the union's ballroom, and wrote on the boards what they knew.
 *
 * <p>Drawn in the lot's frame: x along the front (west to east), z from the front (north) back,
 * the ground at y = 0. Storeys are {@link #S} tall unless a hall says otherwise.
 */
final class University extends Grounds {
    static final int S = 5;
    /** The halls' walls (inclusive). */
    static final int LIB_X0 = 8, LIB_X1 = 58, LIB_Z0 = 54, LIB_Z1 = 122;
    static final int LEC_X0 = 8, LEC_X1 = 58, LEC_Z0 = 132, LEC_Z1 = 204;
    static final int SCI_X0 = 154, SCI_X1 = 204, SCI_Z0 = 54, SCI_Z1 = 122;
    static final int UNI_X0 = 154, UNI_X1 = 204, UNI_Z0 = 132, UNI_Z1 = 204;
    static final int OM_X0 = 66, OM_X1 = 146, OM_Z0 = 172, OM_Z1 = 204;
    static final int DORM_X0 = 152, DORM_X1 = 204, DORM_Z0 = 12, DORM_Z1 = 34;
    static final int LOT_X0 = 8, LOT_X1 = 76, LOT_Z0 = 8, LOT_Z1 = 46;
    /** The walks: the main one up the middle, the two across, the ones along the halls' fronts. */
    static final int MAIN_X0 = 102, MAIN_X1 = 110, CROSS_A0 = 86, CROSS_A1 = 92, CROSS_B0 = 140, CROSS_B1 = 146;
    static final int WEST_WALK0 = 59, WEST_WALK1 = 63, EAST_WALK0 = 149, EAST_WALK1 = 153, FRONT_WALK0 = 159, FRONT_WALK1 = 163;
    /** The fountain in the middle of the first crossing. */
    static final int FX = 106, FZ = 89, FR = 5;

    University(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.45, 0.12, 0.6);
    }

    void build() {
        walks();
        new UniLibrary(this).build();
        new UniLecture(this).build();
        new UniScience(this).build();
        new UniUnion(this).build();
        new UniOldMain(this).build();
        new UniDorm(this).build();
        lot();
        fence();
        gate();
        quad();
        guard();
    }

    // ------------------------------------------------------------------ the ground

    /** Every walk on the lot laid first, so the halls build over them and the lawns go round. */
    private void walks() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                if (edge) {
                    set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
                }
            }
        }
        // the plaza inside the gate, the main walk up the middle to Old Main's steps
        pave(88, 5, 124, 22);
        pave(MAIN_X0, 23, MAIN_X1, FRONT_WALK1);
        // the two walks across, the walks down the halls' fronts and along Old Main's
        pave(WEST_WALK0, CROSS_A0, EAST_WALK1, CROSS_A1);
        pave(WEST_WALK0, CROSS_B0, EAST_WALK1, CROSS_B1);
        pave(WEST_WALK0, 48, WEST_WALK1, FRONT_WALK1);
        pave(EAST_WALK0, 36, EAST_WALK1, FRONT_WALK1);
        pave(WEST_WALK0, FRONT_WALK0, EAST_WALK1, FRONT_WALK1);
        // the side gates' walks between the halls, the dorm's walk, the walk from the lot
        pave(4, 124, WEST_WALK1, 130);
        pave(EAST_WALK0, 124, w - 5, 130);
        pave(EAST_WALK0, 36, DORM_X0 + 12, 40);
        pave(LOT_X1 + 1, 18, 87, 22);
        pave(WEST_WALK0, 47, WEST_WALK1, 50);
        // the diagonals the students wore across the lawns from the quad's corners to the fountain, paved after
        for (int[] c : new int[][] {{WEST_WALK1 + 1, 51}, {EAST_WALK0 - 1, 51}, {WEST_WALK1 + 1, CROSS_B0 - 1}, {EAST_WALK0 - 1, CROSS_B0 - 1}}) {
            diagonal(c[0], c[1], FX, FZ);
        }
        // round the fountain
        for (int x = FX - FR - 3; x <= FX + FR + 3; x++) {
            for (int z = FZ - FR - 3; z <= FZ + FR + 3; z++) {
                double r = Math.hypot(x - FX, z - FZ);
                if (r <= FR + 3) {
                    set(x, 0, z, chance() < 0.25 ? "un_paver_b" : "un_paver");
                }
            }
        }
    }

    /** A walk three wide from (x0, z0) straight to (x1, z1), its lamps and benches every so often along it. */
    private void diagonal(int x0, int z0, int x1, int z1) {
        double len = Math.hypot(x1 - x0, z1 - z0);
        for (double t = 0; t <= len; t += 0.5) {
            double x = x0 + (x1 - x0) * t / len, z = z0 + (z1 - z0) * t / len;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dx) + Math.abs(dz) < 2) {
                        set((int) Math.round(x) + dx, 0, (int) Math.round(z) + dz, chance() < 0.25 ? "un_paver_b" : "un_paver");
                    }
                }
            }
        }
    }

    /** The lamps and benches down a diagonal walk, set off its edge; done with the quad's furniture. */
    private void diagonalFurniture(int x0, int z0, int x1, int z1) {
        double len = Math.hypot(x1 - x0, z1 - z0), nx = -(z1 - z0) / len, nz = (x1 - x0) / len;
        for (double t = 8; t < len - FR - 4; t += 11) {
            double x = x0 + (x1 - x0) * t / len, z = z0 + (z1 - z0) * t / len;
            int lx = (int) Math.round(x + nx * 2.6), lz = (int) Math.round(z + nz * 2.6);
            if (empty(lx, 1, lz)) {
                lampPost(lx, lz);
            }
            int bx = (int) Math.round(x - nx * 2.4), bz = (int) Math.round(z - nz * 2.4);
            String f = Math.abs(nx) > Math.abs(nz) ? (nx > 0 ? "west" : "east") : (nz > 0 ? "north" : "south");
            bench(bx, bz, f);
            if ((int) (t / 11) % 2 == 1) {
                tree((int) Math.round(x - nx * 6), (int) Math.round(z - nz * 6));
            }
        }
    }

    private void pave(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, chance() < 0.2 ? "un_paver_b" : "un_paver");
            }
        }
    }

    /** The staff lot: rows of stalls, the light poles, the cars left in it, the campus police's. */
    private void lot() {
        for (int x = LOT_X0; x <= LOT_X1; x++) {
            for (int z = LOT_Z0; z <= LOT_Z1; z++) {
                set(x, 0, z, chance() < 0.15 ? "asphalt_crack" : "asphalt");
            }
        }
        // the drive in from the street
        for (int x = 30; x <= 38; x++) {
            for (int z = 4; z < LOT_Z0; z++) {
                set(x, 0, z, "asphalt");
            }
        }
        for (int row = 0; row < 3; row++) {
            int z0 = LOT_Z0 + 2 + row * 13;
            for (int x = LOT_X0 + 2; x <= LOT_X1 - 2; x++) {
                boolean line = (x - LOT_X0 - 2) % 3 == 0;
                for (int z = z0; z < z0 + 5; z++) {
                    if (line && chance() < 0.85) {
                        set(x, 0, z, "road_line_white[axis=z]");
                    }
                }
                if (line && x + 3 <= LOT_X1 - 2 && chance() < 0.18) {
                    p.vehicle(x + 2, 1, z0 + 2.5, 180 + (float) (chance() * 10 - 5),
                            row == 0 && x < 30 ? "police_sedan,sedan" : "sedan,suv,pickup,hatchback,wagon,van", rng.nextLong());
                }
            }
        }
        for (int x = LOT_X0 + 10; x <= LOT_X1; x += 24) {
            int z = LOT_Z0 + 10;
            set(x, 1, z, "st_lot_base");
            for (int y = 2; y <= 9; y++) {
                set(x, y, z, "st_lot_pole");
            }
            set(x, 10, z, (power && chance() < 0.35 ? "st_lot_head_on" : "st_lot_head") + "[facing=north]");
        }
        for (int i = 0; i < 25; i++) {
            int x = LOT_X0 + rng.nextInt(LOT_X1 - LOT_X0), z = LOT_Z0 + rng.nextInt(LOT_Z1 - LOT_Z0);
            if (empty(x, 1, z)) {
                set(x, 1, z, any("paper", "glass_shards", "trash_bag[facing=" + dir() + "]", "home_books_stack_low"));
            }
        }
    }

    /** The railings round the campus on their brick wall, brick piers with stone caps every eight. */
    private void fence() {
        int lo = 4, hi = w - 5;
        for (int i = lo; i <= hi; i++) {
            rail(i, lo, true);
            rail(i, d - 5, true);
            rail(lo, i, false);
            rail(hi, i, false);
        }
    }

    private void rail(int x, int z, boolean alongX) {
        int a = alongX ? x : z;
        boolean north = alongX && z == 4;
        // the gaps: the gate, the lot's drive, the side gates
        if (north && (x >= 94 && x <= 118 || x >= 30 && x <= 38)) {
            return;
        }
        if (!alongX && a >= 124 && a <= 130) {
            return;
        }
        boolean pier = a % 8 == 0 || (x == 4 || x == w - 5) && (z == 4 || z == d - 5);
        set(x, 0, z, "stone_trim");
        set(x, 1, z, b.wall("brick_red", 1));
        if (pier) {
            set(x, 2, z, b.wall("brick_red", 2));
            set(x, 3, z, b.wall("brick_red", 3));
            set(x, 4, z, "cv_pier_stone");
        } else if (chance() > 0.08) {
            set(x, 2, z, "cv_iron_fence[axis=" + (alongX ? "x" : "z") + "]");
            set(x, 3, z, "cv_iron_fence[axis=" + (alongX ? "x" : "z") + "]");
        }
    }

    /**
     * The gate on the north street: brick piers three square either side of the drive, a stone
     * lintel across them, GRAYHAVEN STATE UNIVERSITY over it in cut steel, the seal on the piers.
     */
    private void gate() {
        for (int x0 : new int[] {94, 116}) {
            for (int x = x0; x <= x0 + 2; x++) {
                for (int z = 3; z <= 5; z++) {
                    for (int y = 0; y <= 8; y++) {
                        set(x, y, z, y == 0 || y == 8 ? "stone_trim" : b.wall("brick_red", y));
                    }
                }
            }
            set(x0 + 1, 9, 4, "cv_pier_stone");
        }
        for (int x = 97; x <= 115; x++) {
            set(x, 8, 4, "stone_trim");
            set(x, 0, 4, "un_paver");
            set(x, 0, 3, "un_paver");
        }
        row("un_name_gate_", 24, 94, 9, 3, "north", 0.04);
        for (int x = 94; x <= 118; x++) {
            if (get(x, 9, 3) != null) {
                set(x, 9, 4, "stone_trim");
            }
        }
        // the old wooden sawhorses and the jersey barriers half across it, a sandbag nest inside
        Camp camp = new Camp(p, rng, 0.6);
        camp.jersey(97, 6, 101, 6);
        camp.jersey(111, 6, 115, 6);
        camp.nest(96, 14);
        for (int x = 88; x <= 106; x++) {
            for (int z = 8; z <= 22; z++) {
                String g = get(x, 1, z);
                if (g != null && g.startsWith("minecraft:barrel")) {
                    set(x, 1, z, "crate");
                }
            }
        }
        set(105, 1, 7, "st_barrier[facing=north]");
        // the flagpoles, the founder on his plinth, the campus map
        for (int x : new int[] {90, 122}) {
            set(x, 0, 10, "concrete_dark");
            set(x, 1, 10, "cv_flagpole_base");
            for (int y = 2; y < 12; y++) {
                set(x, y, 10, "cv_flagpole");
            }
            set(x, 12, 10, chance() < 0.7 ? "cv_flagpole_top[facing=north]" : "cv_flagpole");
        }
        for (int x = 104; x <= 108; x++) {
            for (int z = 14; z <= 16; z++) {
                set(x, 1, z, "stone_trim");
            }
        }
        fill(105, 2, 15, 107, 2, 15, "stone_trim");
        set(106, 3, 15, "ch_statue[facing=north]");
        set(106, 4, 15, "ch_statue_top[facing=north]");
        picture("un_map_", 3, 2, 112, 2, 12, "north");
    }

    // ------------------------------------------------------------------ the quad

    /** The lawns gone to seed, the oaks down the walks, the fountain, the benches and lamps. */
    private void quad() {
        // the fountain: a smooth stone drum round the water gone green, the statue in the middle on its column
        double ox = FX + 0.5, oz = FZ + 0.5;
        java.util.Set<Long> rim = new java.util.HashSet<>();
        for (Round.Cell c : Round.ring(ox, oz, FR + 0.4)) {
            rim.add(((long) c.x() << 32) ^ (c.z() & 0xffffffffL));
        }
        for (int x = FX - FR - 1; x <= FX + FR + 1; x++) {
            for (int z = FZ - FR - 1; z <= FZ + FR + 1; z++) {
                if (Math.hypot(x + 0.5 - ox, z + 0.5 - oz) < FR + 0.2 && !rim.contains(((long) x << 32) ^ (z & 0xffffffffL))) {
                    set(x, -1, z, "stone_trim");
                    set(x, 0, z, "minecraft:water");
                    if (chance() < 0.15) {
                        set(x, 1, z, any("paper", "trash_bag[facing=" + dir() + "]"));
                    }
                }
            }
        }
        Round.wall(p, ox, oz, FR + 0.4, 0, 1, "twr", 0);
        Round.wall(p, ox, oz, 1.4, 1, 2, "twr", 0);
        fill(FX, 0, FZ, FX, 2, FZ, "stone_trim");
        set(FX, 3, FZ, "ch_statue[facing=north]");
        set(FX, 4, FZ, "ch_statue_top[facing=north]");
        // lamps, oaks and benches down the main walk and along the cross walks
        for (int z = 26; z <= FRONT_WALK0 - 4; z += 12) {
            if (Math.abs(z - FZ) < FR + 4) {
                continue;
            }
            lampPost(MAIN_X0 - 1, z);
            lampPost(MAIN_X1 + 1, z + 6);
            if ((z / 12) % 2 == 0) {
                tree(MAIN_X0 - 6, z + 3);
            } else {
                tree(MAIN_X1 + 6, z + 9);
            }
            bench(MAIN_X0, z + 2, "east");
            bench(MAIN_X1, z + 8, "west");
        }
        for (int x = WEST_WALK1 + 6; x < EAST_WALK0 - 4; x += 12) {
            if (Math.abs(x - FX) < FR + 6) {
                continue;
            }
            for (int[] c : new int[][] {{CROSS_A0, CROSS_A1}, {CROSS_B0, CROSS_B1}}) {
                lampPost(x, c[0] - 1);
                if ((x / 12 + c[0]) % 2 == 0) {
                    tree(x + 4, c[0] - 6);
                } else {
                    tree(x + 6, c[1] + 6);
                }
                bench(x + 2, c[1], "north");
            }
        }
        for (int[] c : new int[][] {{WEST_WALK1 + 1, 51}, {EAST_WALK0 - 1, 51}, {WEST_WALK1 + 1, CROSS_B0 - 1}, {EAST_WALK0 - 1, CROSS_B0 - 1}}) {
            diagonalFurniture(c[0], c[1], FX, FZ);
        }
        // the lawns, worst where the tents stood
        Kit.lawn(p, rng, 5, 5, w - 6, d - 6, 0.7);
        // hedges along the halls' fronts
        for (int z = 50; z <= FRONT_WALK0 - 2; z++) {
            if (z >= CROSS_A0 - 1 && z <= CROSS_A1 + 1 || z >= CROSS_B0 - 1 && z <= CROSS_B1 + 1 || z >= 123 && z <= 131) {
                continue;
            }
            set(WEST_WALK1 + 1, 1, z, chance() < 0.25 ? "un_hedge_dead" : "un_hedge_low");
            set(EAST_WALK0 - 1, 1, z, chance() < 0.25 ? "un_hedge_dead" : "un_hedge_low");
        }
        // litter blown across it all
        for (int i = 0; i < 160; i++) {
            int x = 6 + rng.nextInt(w - 12), z = 6 + rng.nextInt(d - 12);
            String g = get(x, 0, z);
            if (g != null && (g.startsWith("un_paver") || g.equals("minecraft:grass_block")) && empty(x, 1, z)) {
                set(x, 1, z, any("paper", "paper", "trash_bag[facing=" + dir() + "]", "home_books_stack_low", "blood_splat", "glass_shards"));
            }
        }
    }

    private void tree(int x, int z) {
        String g = get(x, 0, z);
        if (g == null || g.equals("minecraft:grass_block")) {
            set(x, 0, z, "minecraft:grass_block");
            double r = chance();
            Trees.grow(Kit.canopy(p), r < 0.55 ? Trees.Kind.YOUNG : r < 0.8 ? Trees.Kind.MAPLE : r < 0.9 ? Trees.Kind.OAK : Trees.Kind.SICK, x, 1, z, rng);
        }
    }

    private void bench(int x, int z, String facing) {
        if (empty(x, 1, z)) {
            set(x, 1, z, (chance() < 0.85 ? "cv_hall_bench" : "debris_chunk") + "[facing=" + facing + "]");
        }
    }

    /**
     * The National Guard's camp on the quad's north-east lawn: shelter and supply tents in a row,
     * sandbags, the pallets of water they never handed out, a Humvee on the main walk.
     */
    private void guard() {
        for (int i = 0; i < 4; i++) {
            int x = 118 + i * 7, z = 30;
            if (chance() < 0.85) {
                tent(x, z, 12, i == 3 ? "supply" : "shelter");
            }
        }
        for (int x = 118; x < 146; x += 2) {
            if (chance() < 0.6) {
                set(x, 1, 46, "sandbags[facing=south]");
            }
        }
        for (int x = 120; x < 140; x += 3) {
            set(x, 1, 25, chance() < 0.5 ? "st_pallet_water" : "st_pallet_meals");
        }
        p.vehicle(MAIN_X0 + 4, 1, 30, 10f + (float) (chance() * 20), "jeep,jeep_open", rng.nextLong());
        for (int x = 70; x < 96; x += 2) {
            if (chance() < 0.5) {
                set(x, 1, 56, "st_stretcher_bag[facing=east]");
            }
        }
    }
}
