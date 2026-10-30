package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Seabright Boardwalk (Josiah, 2026-10-30: the coast and industry places, the boardwalk third),
 * after refs/coast: the beach town's front on the sea a little east of Seabright.
 * <ul>
 * <li>Ocean Avenue behind, the row of shops and the Funland arcade between it and the boardwalk,
 * their fronts on the boards: surf shop, pizza, fries, taffy, the arcade's neon, ice cream,
 * gifts, the beach patrol (boardwalk-arcade);</li>
 * <li>the boardwalk on its creosoted piles along the top of the beach, its white rail, lamps and
 * benches, the ramps down to the sand; the beach with its umbrellas, chairs and towels, the snow
 * fence along the dunes, the lifeguard chairs (beach-pier, lifeguard-tower);</li>
 * <li>the amusement pier out from the plaza at the arcade's end: the Ferris wheel, the carousel,
 * the swing ride and the game booths, the wooden coaster out at the end of it, then the fishing
 * pier on out to the bait shop on its T (boardwalk-amusements, beach-pier) ({@link BoardwalkPier}).</li>
 * </ul>
 * The town was evacuated from here in the last week: the buses came down Ocean Avenue to the
 * plaza; the barriers are still across the pier, the crowd's leavings all down the boards.
 *
 * <p>Built where the land lies ({@link Place#natural}), like {@link Cape}: the place's level is
 * the boardwalk's deck; the town side is brought to it, the beach and the sea left as they lie.
 * Its frame puts the plan's spot at (0, 0), x east toward the sea, z south along the shore.
 */
final class Boardwalk extends Site {
    static final int FX0 = -60, FX1 = 440, FZ0 = -210, FZ1 = 210;
    /** The deck's level: five over the sea. */
    static final int LEVEL = Terrain.WATER + 5;
    /** Ocean Avenue's kerbs, the shops' backs and fronts, the boardwalk's deck from its rail on the shop side to its rail on the beach. */
    static final int AVE0 = 2, AVE1 = 12, BACK = 22, FRONT = 56, BW0 = 57, BW1 = 74;
    /** The amusement pier: from the boardwalk out to PX1, z PZ0 .. PZ1; the fishing pier on to FX at its T. */
    static final int PZ0 = -28, PZ1 = 28, PX1 = 262, FPX1 = 424;
    /** The Hollis ferry's bell on the fishing pier's T and where she puts people ashore, in the frame. */
    static final int[] BELL = {FPX1 - 2, 1, 13}, LANDING = {FPX1 - 6, 1, 11};

    private final County county;

    Boardwalk(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    static Place place(Plan.Spot s, long h) {
        Place p = new Place(PlaceType.LANDMARK, "boardwalk", s.x() + FX0, s.z() + FZ0, s.x() + FX1, s.z() + FZ1, LEVEL, h, -1, -1);
        p.natural = true;
        return p;
    }

    @Override
    void build() {
        int[][] g = new int[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
        int cx = p.x0 - FX0, cz = p.z0 - FZ0;
        List<Place> near = county == null ? List.of() : county.placesNear(p.x0, p.z0, p.x1, p.z1, County.SKIRT);
        for (int x = FX0; x <= FX1; x++) {
            for (int z = FZ0; z <= FZ1; z++) {
                g[x - FX0][z - FZ0] = county == null ? (x < 40 ? 0 : -Math.min(20, (x - 40) / 8))
                        : (int) Math.floor(county.ground(cx + x, cz + z, near)) - p.level;
            }
        }
        Placer a = new Placer(out, -FX0, 0, -FZ0, 0);
        Front f = new Front(a, new SplittableRandom(p.seed), g, new Noise(p.seed));
        f.build();
    }

    /** The front drawn in its frame. */
    static final class Front extends Grounds {
        /** The sea's top block (frame y). */
        static final int SEA = Terrain.WATER - 1 - LEVEL;
        private final int[][] g;
        final Noise noise;

        Front(Placer p, SplittableRandom rng, int[][] g, Noise noise) {
            super(p, rng, FX1 - FX0 + 1, FZ1 - FZ0 + 1, 0.35, 0.35, 0.3);
            this.g = g;
            this.noise = noise;
        }

        @Override
        String signs() {
            return "sb_sgn_";
        }

        /** The land as it lies at (x, z), frame y. */
        int nat(int x, int z) {
            return g[Math.max(FX0, Math.min(FX1, x)) - FX0][Math.max(FZ0, Math.min(FZ1, z)) - FZ0];
        }

        boolean wet(int x, int z) {
            return nat(x, z) < SEA;
        }

        void build() {
            land();
            avenue();
            new BoardwalkShops(this).build();
            boards();
            beach();
            new BoardwalkPier(this).build();
            plaza();
            story();
        }

        // ------------------------------------------------------------------ the land and the beach

        /**
         * The town side brought to the deck's level, easing back to the land at the frame's west
         * and its ends; east of the boardwalk the beach as it lies, sand over it, the sea past it.
         */
        private void land() {
            for (int x = FX0; x <= FX1; x++) {
                for (int z = FZ0; z <= FZ1; z++) {
                    int n = nat(x, z);
                    if (x > BW1) {
                        if (n >= SEA) {
                            // the beach: sand down to the water, a little shell and wrack along the tideline
                            boolean tide = n <= SEA + 1;
                            for (int y = n - 2; y <= n; y++) {
                                set(x, y, z, "minecraft:sand");
                            }
                            if (tide && chance() < 0.15) {
                                set(x, n, z, any("minecraft:gravel", "minecraft:suspicious_sand", "minecraft:sand"));
                            }
                            for (int y = n + 1; y <= 2; y++) {
                                set(x, y, z, "air");
                            }
                        }
                        continue;
                    }
                    double hold = Math.min(Terrain.smooth(FX0, FX0 + 30, x), Terrain.smooth(FZ1, FZ1 - 20, Math.abs(z)));
                    int t = x >= BW0 ? Math.min(n, -1) : (int) Math.round(Terrain.lerp(n, 0, hold));
                    for (int y = Math.min(n, t) - 3; y < t; y++) {
                        set(x, y, z, x >= BW0 ? "minecraft:sand" : "minecraft:dirt");
                    }
                    set(x, t, z, x >= BW0 ? "minecraft:sand" : chance() < 0.1 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                    for (int y = t + 1; y <= Math.max(n, t) + 2; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
        }

        /**
         * Ocean Avenue along the back of the shops, the road in from town meeting it at the plaza;
         * the sidewalks and their lamps.
         */
        private void avenue() {
            for (int z = FZ0 + 4; z <= FZ1 - 4; z++) {
                for (int x = AVE0 - 3; x <= BACK - 1; x++) {
                    boolean road = x >= AVE0 && x <= AVE1;
                    String b = road ? (chance() < 0.05 ? "asphalt_crack" : "asphalt_b")
                            : chance() < 0.06 ? "sidewalk_cracked" : "sidewalk";
                    if (road && x == (AVE0 + AVE1) / 2 && Math.floorMod(z, 4) < 2) {
                        b = "road_line_yellow[axis=z]";
                    }
                    set(x, 0, z, b);
                    set(x, 1, z, "air");
                }
                if (Math.floorMod(z, 24) == 0) {
                    lampPost(AVE1 + 2, z);
                    lampPost(AVE0 - 2, z + 12);
                }
            }
            // the road in from the west
            for (int x = FX0; x < AVE0 - 3; x++) {
                for (int z = -5; z <= 5; z++) {
                    set(x, groundAt(x, z), z, z == 0 && Math.floorMod(x, 4) < 2 ? "road_line_yellow[axis=x]" : chance() < 0.05 ? "asphalt_crack" : "asphalt_b");
                }
            }
        }

        /** The ground's top at (x, z) on the town side once it is laid. */
        int groundAt(int x, int z) {
            for (int y = 4; y > -24; y--) {
                String b = get(x, y, z);
                if (b != null && !b.equals("air")) {
                    return y;
                }
            }
            return 0;
        }

        /**
         * The boardwalk: planks across it on stringers on the piles, the white rail along the beach
         * side, lamps and benches facing the sea, the ramps down to the sand.
         */
        private void boards() {
            for (int z = FZ0 + 6; z <= FZ1 - 6; z++) {
                for (int x = BW0; x <= BW1; x++) {
                    set(x, 0, z, chance() < 0.005 ? "air" : chance() < 0.03 ? "wf_deck_b" : "sb_deck");
                    set(x, 1, z, "air");
                    set(x, 2, z, "air");
                    if (Math.floorMod(z, 6) == 0) {
                        set(x, -1, z, "sb_beam[axis=x]");
                    }
                    if (Math.floorMod(z, 6) == 0 && (x == BW0 || x == BW1 || x == (BW0 + BW1) / 2)) {
                        for (int y = -2; y >= nat(x, z); y--) {
                            set(x, y, z, "sb_pile");
                        }
                    }
                }
                boolean pier = z >= PZ0 && z <= PZ1;
                boolean ramp = Math.abs(Math.floorMod(z + 90, 130) - 0) <= 1 && !pier;
                if (!pier && !ramp) {
                    set(BW1, 1, z, "sb_rail[facing=east]");
                }
                if (Math.floorMod(z, 16) == 8 && !pier) {
                    lampPost(BW1 - 1, z);
                    set(BW1 - 1, 1, z + 2, "st_bench[facing=east]");
                    set(BW1 - 1, 1, z + 3, "st_bench[facing=east]");
                    set(BW1 - 1, 1, z + 5, chance() < 0.2 ? "ext_trash_can_tipped[facing=" + dir() + "]" : "ext_trash_can");
                }
            }
            // the ramps down to the beach, a flight of steps east off the rail
            for (int z = FZ0 + 10; z <= FZ1 - 10; z++) {
                if (Math.floorMod(z + 90, 130) > 1 || z >= PZ0 - 2 && z <= PZ1 + 2) {
                    continue;
                }
                int y = 0;
                for (int x = BW1 + 1; y > nat(x, z) + 1 && x < BW1 + 12; x++) {
                    y--;
                    set(x, y, z, "ext_stairs_wood[facing=west]");
                    set(x, y + 1, z, "air");
                    set(x, y + 2, z, "air");
                }
                if (Math.floorMod(z + 90, 130) == 0) {
                    sign("ramp", BW1 - 1, 1, z - 1, "east");
                }
            }
        }

        /**
         * The beach: snow fence along the dune at the boardwalk's foot, the lifeguard chairs, the
         * umbrellas still up where the families left them, the chairs and towels and coolers.
         */
        private void beach() {
            for (int z = FZ0 + 8; z <= FZ1 - 8; z++) {
                if (z >= PZ0 - 4 && z <= PZ1 + 4) {
                    continue;
                }
                int x = BW1 + 3 + (int) Math.round(noise.fbm(0, z, 20, 2) * 2);
                int n = nat(x, z);
                if (n >= SEA + 1 && Math.floorMod(z + 90, 130) > 2 && chance() < 0.88) {
                    set(x, n + 1, z, "sb_fence[axis=z]");
                }
                // dune grass between the fence and the boards
                for (int xx = BW1 + 1; xx < x; xx++) {
                    int m = nat(xx, z);
                    if (m >= SEA + 1 && chance() < 0.35 && empty(xx, m + 1, z)) {
                        set(xx, m + 1, z, any("minecraft:short_dry_grass", "minecraft:tall_dry_grass", "minecraft:short_dry_grass", "weeds"));
                    }
                }
            }
            for (int z : new int[] {-170, -100, 70, 150}) {
                int x = BW1 + 30;
                int n = nat(x, z);
                if (n < SEA + 1) {
                    continue;
                }
                set(x, n + 1, z, "sb_guard_lo[facing=east]");
                set(x, n + 2, z, "sb_guard_hi[facing=east]");
                set(x + 2, n + 1, z + 1, "st_barrier[facing=east]");
            }
            for (int i = 0; i < 70; i++) {
                int x = BW1 + 8 + rng.nextInt(40), z = FZ0 + 12 + rng.nextInt(FZ1 - FZ0 - 24);
                if (z >= PZ0 - 6 && z <= PZ1 + 6) {
                    continue;
                }
                int n = nat(x, z);
                if (n < SEA + 1 || !empty(x, n + 1, z) || !empty(x, n + 2, z)) {
                    continue;
                }
                double r = chance();
                if (r < 0.3) {
                    set(x, n + 1, z, "sb_umbrella_lo");
                    set(x, n + 2, z, chance() < 0.5 ? "sb_umbrella_hi_a" : "sb_umbrella_hi_b");
                    set(x + 1, n + 1, z, "sb_chair[facing=" + any("east", "east", "south", "north") + "]");
                    if (chance() < 0.6) {
                        set(x - 1, n + 1, z + 1, chance() < 0.5 ? "sb_towel_a[facing=east]" : "sb_towel_b[facing=east]");
                    }
                    if (chance() < 0.4) {
                        set(x, n + 1, z - 1, "sb_cooler[facing=" + dir() + "]");
                    }
                } else if (r < 0.55) {
                    set(x, n + 1, z, chance() < 0.5 ? "sb_towel_a[facing=" + dir() + "]" : "sb_towel_b[facing=" + dir() + "]");
                } else if (r < 0.7) {
                    // an umbrella blown over, its canopy on the sand
                    set(x, n + 1, z, "sb_chair[facing=" + dir() + "]");
                } else if (r < 0.82) {
                    set(x, n + 1, z, any("trash_bag[facing=" + dir() + "]", "paper", "glass_shards", "pk_trash_bag"));
                } else if (r < 0.9) {
                    set(x, n + 1, z, chance() < 0.5 ? "home_corpse_head[facing=" + dir() + "]" : "blood_splat");
                }
            }
            sign("beach", BW1 - 1, 1, FZ0 + 30, "east");
            sign("nodogs", BW1 - 1, 1, FZ1 - 30, "east");
        }

        /**
         * The plaza at the pier's root between the shops: the arch over the pier's start, the
         * ticket booth, the bus turnaround on the avenue behind; the barriers the Guard put across.
         */
        private void plaza() {
            for (int x = BACK; x <= FRONT; x++) {
                for (int z = PZ0 - 4; z <= PZ1 + 4; z++) {
                    set(x, 0, z, (x + z) % 7 == 0 ? "wf_pavers_moss" : chance() < 0.06 ? "wf_pavers_b" : "wf_pavers");
                    for (int y = 1; y <= 8; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
            for (int z = PZ0 + 2; z <= PZ1 - 2; z += 8) {
                lampPost((BACK + FRONT) / 2 - 6, z);
                lampPost((BACK + FRONT) / 2 + 6, z);
            }
            // the arch at the pier's start on the boardwalk's edge, its neon facing back toward town
            int ax = BW1 + 1;
            for (int z : new int[] {-9, 9}) {
                for (int y = 1; y <= 8; y++) {
                    set(ax, y, z, "wf_trim");
                }
                set(ax, 9, z, power && chance() < 0.6 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            }
            for (int z = -9; z <= 9; z++) {
                set(ax, 9, z, "wf_trim");
            }
            neon("pier", 6, 2, ax - 1, 7, -3, "west");
            // the ticket booth and the barriers across
            set(ax + 3, 1, -12, "tr_booth_lo[facing=west]");
            set(ax + 3, 2, -12, "tr_booth_hi[facing=west]");
            sign("tickets", ax + 2, 3, -14, "west");
            for (int z = -8; z <= 8; z++) {
                if (Math.abs(z) > 1 || chance() < 0.3) {
                    set(ax + 1, 1, z, chance() < 0.85 ? "st_barrier[facing=west]" : "st_barrier_down[facing=west]");
                }
            }
            sign("evac", ax + 1, 2, -1, "west");
            // benches round the plaza
            for (int z = -6; z <= 6; z += 4) {
                set(BACK + 4, 1, z, "st_bench[facing=east]");
                set(FRONT - 4, 1, z, "st_bench[facing=west]");
            }
        }

        /**
         * The evacuation: the buses on the avenue, the luggage and the dead along the boards, the
         * police line at the plaza, the cars left at the kerb.
         */
        private void story() {
            p.vehicle(AVE0 + 3.5, 1, -40.0, 0f, "city_bus,school_bus", rng.nextLong());
            p.vehicle(AVE0 + 3.5, 1, 52.0, 180f, "city_bus,school_bus", rng.nextLong());
            for (int z = FZ0 + 20; z < FZ1 - 20; z += 9 + rng.nextInt(12)) {
                if (z > PZ0 - 10 && z < PZ1 + 10) {
                    continue;
                }
                p.vehicle(AVE1 - 1.5, 1, z + 0.5, chance() < 0.5 ? 0f : 180f, "", rng.nextLong());
            }
            for (int x = AVE0; x <= AVE1; x += 3) {
                set(x, 1, PZ0 - 6, "jersey_barrier[facing=north]");
                set(x, 1, PZ1 + 6, "jersey_barrier[facing=south]");
            }
            mess(BW0, FZ0 + 8, BW1 - 1, FZ1 - 8, 0, 0.025);
            for (int i = 0; i < 26; i++) {
                int x = BW0 + rng.nextInt(BW1 - BW0), z = FZ0 + 10 + rng.nextInt(FZ1 - FZ0 - 20);
                if (empty(x, 1, z) && get(x, 0, z) != null) {
                    set(x, 1, z, any("home_clothes_pile", "pk_trash_bag", "home_corpse_head[facing=" + dir() + "]", "home_blood_pool", "trash_bag[facing=" + dir() + "]"));
                }
            }
        }

        /** A neon sign cols by rows from x0/z0, its bottom row at y, lit or dark as a whole. */
        void neon(String key, int cols, int rows, int x0, int y, int z0, String facing) {
            boolean lit = power && chance() < 0.85;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int[] a = MallSigns.at(x0, z0, cols, c, facing);
                    set(a[0], y + rows - 1 - r, a[1], "sb_neon_" + key + "_" + (r * cols + c) + (lit ? "_on" : "") + "[facing=" + facing + "]");
                }
            }
        }

        /** Piles under a deck from x0..x1, z0..z1 at y 0, every `every` blocks, down to the bed or the sand. */
        void piles(int x0, int z0, int x1, int z1, int every) {
            for (int x = x0; x <= x1; x += every) {
                for (int z = z0; z <= z1; z += every) {
                    for (int y = -1; y >= nat(x, z); y--) {
                        set(x, y, z, "sb_pile");
                    }
                }
                for (int z = z0; z <= z1; z++) {
                    set(x, -1, z, "sb_beam[axis=z]");
                }
            }
        }
    }
}
