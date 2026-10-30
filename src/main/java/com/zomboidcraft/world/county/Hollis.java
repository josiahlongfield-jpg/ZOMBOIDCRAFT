package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Hollis Island Quarantine Station (Josiah, 2026-10-30: the coast and industry places, Hollis
 * Island fourth), after refs/quarantine: the low island off the coast where the ships' sick were
 * put ashore from 1893, taken over again in the outbreak.
 * <ul>
 * <li>the ferry wharf on the west shore: the jetty out to the quay head, the ferry from Seabright
 * alongside, the car slip with its gantry, the dolphins, the freight shed (ferry-dock);</li>
 * <li>the processing line inland of it: the container wall across the island with its one gate,
 * the chutes the arrivals were fed down, the decontamination tents (decon-lines,
 * hazmat-triage-tents);</li>
 * <li>the old station on the drive ({@link HollisStation}): the administration building with its
 * cupola, the disinfecting plant and its chimney, the surgeon's house with its veranda and the
 * nurses' home, the six brick pavilion wards under their clerestories along the covered walk, the
 * stone morgue, the old burying ground and its memorial (historic-quarantine-station,
 * body-bags-mass-graves);</li>
 * <li>the army's compound on the east end ({@link HollisCamp}): the chain-link and the towers, the
 * new isolation wards up on their stilts, the field hospital, the command post, the helipad, the
 * generators, and past them the trenches, the lime and the burn pit (isolation-ward,
 * body-bags-mass-graves).</li>
 * </ul>
 * The station held for nineteen days. The last helicopter came down on its pad; the ferry made
 * her last run with nobody aboard to tie her up at the far end.
 *
 * <p>Built where the island lies ({@link Place#natural}), like {@link Boardwalk}: the island is
 * flat, a block or two over the sea; the place's level is its grass, the shore and the sea left as
 * they lie. Its frame puts the plan's spot at (0, 0), x east, z south.
 */
final class Hollis extends Site {
    static final int FX0 = -440, FX1 = 380, FZ0 = -270, FZ1 = 270;
    /** The island's grass: two over the sea. */
    static final int LEVEL = Terrain.WATER + 2;
    /** The quay head's face (west), its deck one over the grass; the road along the drive z ROAD0 .. ROAD1. */
    static final int QX = -412, ROAD0 = -3, ROAD1 = 3;
    /** The container wall across the island, and the compound's west fence. */
    static final int WALL = -300, CAMP = 40;
    /** The ferry's bell on the quay and where she puts people ashore, in the frame. */
    static final int[] BELL = {QX + 2, 2, -6}, LANDING = {QX + 5, 2, -2};

    private final County county;

    Hollis(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    static Place place(Plan.Spot s, long h) {
        Place p = new Place(PlaceType.LANDMARK, "hollis", s.x() + FX0, s.z() + FZ0, s.x() + FX1, s.z() + FZ1, LEVEL, h, -1, -1);
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
                // without a county: an oval island like the plan's
                double e = Math.hypot(x / 370.0, z / 248.0);
                g[x - FX0][z - FZ0] = county == null ? (e < 1 ? 0 : -(int) Math.min(12, (e - 1) * 60))
                        : (int) Math.floor(county.ground(cx + x, cz + z, near)) - p.level;
            }
        }
        Placer a = new Placer(out, -FX0, 0, -FZ0, 0);
        new Island(a, new SplittableRandom(p.seed), g, new Noise(p.seed)).build();
    }

    /** The island drawn in its frame. */
    static final class Island extends Grounds {
        /** The sea's top block (frame y). */
        static final int SEA = Terrain.WATER - 1 - LEVEL;
        private final int[][] g;
        final Noise noise;
        final Camp camp;
        /** Where the shore is along the drive: the first land west to east. */
        int shore;

        Island(Placer p, SplittableRandom rng, int[][] g, Noise noise) {
            super(p, rng, FX1 - FX0 + 1, FZ1 - FZ0 + 1, 0.45, 0.4, 0.25);
            this.g = g;
            this.noise = noise;
            this.camp = new Camp(p, rng, 0.6);
        }

        @Override
        String signs() {
            return "qs_sgn_";
        }

        /** The land as it lies at (x, z), frame y. */
        int nat(int x, int z) {
            return g[Math.max(FX0, Math.min(FX1, x)) - FX0][Math.max(FZ0, Math.min(FZ1, z)) - FZ0];
        }

        /** Grass to build on at (x, z): the island's top, not its beach. */
        boolean land(int x, int z) {
            return nat(x, z) >= -1;
        }

        void build() {
            shore = FX0;
            while (shore < 0 && nat(shore, 0) <= SEA) {
                shore++;
            }
            ground();
            drive();
            wharf();
            processing();
            new HollisStation(this).build();
            new HollisCamp(this).build();
            story();
        }

        // ------------------------------------------------------------------ the island

        /**
         * The island's top brought to the grass's level (it lies within a block of it), rough grass
         * and the odd bush on it; the beach round it sand and shingle down to the water, wrack along
         * the tideline; the sea left as it lies.
         */
        private void ground() {
            for (int x = FX0; x <= FX1; x++) {
                for (int z = FZ0; z <= FZ1; z++) {
                    int n = nat(x, z);
                    if (n <= SEA) {
                        continue;
                    }
                    // a hollow inland is filled to the grass; only the shore's own slope is beach
                    boolean beach = n < 0 && nearSea(x, z);
                    int t = beach ? n : 0;
                    for (int y = t - 3; y <= t; y++) {
                        set(x, y, z, beach ? (y == t && noise.get(x * 0.2, z * 0.2) > 0.6 ? "minecraft:gravel" : "minecraft:sand")
                                : y == t ? (chance() < 0.06 ? "minecraft:coarse_dirt" : "minecraft:grass_block") : "minecraft:dirt");
                    }
                    for (int y = t + 1; y <= Math.max(n, t) + 3; y++) {
                        set(x, y, z, "air");
                    }
                    double r = chance();
                    if (beach) {
                        // the wrack along the tideline: weed thrown up in a line, the odd bit of driftwood and rubbish
                        if (t == SEA + 1 && r < 0.1) {
                            set(x, t, z, "minecraft:gravel");
                        } else if (t == SEA + 1 && r < 0.13) {
                            set(x, t + 1, z, any("minecraft:dead_bush", "debris_chunk[facing=" + dir() + "]", "fallen_pole"));
                        }
                    } else if (r < 0.25) {
                        set(x, 1, z, noise.get(x * 0.05, z * 0.05) > 0.55 ? "minecraft:tall_dry_grass" : "minecraft:short_grass");
                    } else if (r < 0.27) {
                        set(x, 1, z, any("minecraft:fern", "minecraft:bush", "minecraft:short_dry_grass"));
                    }
                }
            }
        }

        /** Whether the sea is within a few blocks of (x, z). */
        private boolean nearSea(int x, int z) {
            for (int d = 2; d <= 8; d += 3) {
                if (nat(x - d, z) <= SEA || nat(x + d, z) <= SEA || nat(x, z - d) <= SEA || nat(x, z + d) <= SEA) {
                    return true;
                }
            }
            return false;
        }

        /** The drive from the shore to the compound and on through it: asphalt patched and broken, the verges. */
        private void drive() {
            for (int x = shore; x <= 300; x++) {
                for (int z = ROAD0 - 1; z <= ROAD1 + 1; z++) {
                    boolean verge = z == ROAD0 - 1 || z == ROAD1 + 1;
                    double r = chance();
                    set(x, 0, z, verge ? (r < 0.5 ? "minecraft:gravel" : "minecraft:coarse_dirt")
                            : z == 0 && x % 4 < 2 && x < CAMP ? "road_line_yellow[axis=x]"
                            : r < 0.12 ? "asphalt_crack" : "asphalt");
                    set(x, 1, z, "air");
                    set(x, 2, z, "air");
                }
                if (x > shore + 10 && x < WALL - 6 || x > WALL + 10 && x < CAMP - 4) {
                    if (Math.floorMod(x, 24) == 0) {
                        lampPost(x, ROAD0 - 2);
                    } else if (Math.floorMod(x, 24) == 12) {
                        lampPost(x, ROAD1 + 2);
                    }
                }
            }
        }

        // ------------------------------------------------------------------ the wharf

        /**
         * The ferry wharf (ferry-dock 01-04): the jetty from the shore out on its piles, the quay
         * head across its end with its fenders, bollards and ladders, the ferry alongside with her
         * gangway down, the car slip off the head's north end under its gantry, the timber
         * dolphins, the bell; ashore the freight shed and the booth on the road.
         */
        private void wharf() {
            int f = 1, sea = SEA;
            int jx1 = shore + 3;
            // the jetty: concrete deck on piles, a rail each side, its lamps
            for (int x = QX + 8; x <= jx1; x++) {
                for (int z = -5; z <= 5; z++) {
                    boolean edge = z == -5 || z == 5;
                    set(x, f, z, edge ? "wf_cap" : chance() < 0.1 ? "asphalt_crack" : "asphalt");
                    set(x, f - 1, z, "concrete_dark");
                    for (int y = f + 1; y <= f + 3; y++) {
                        set(x, y, z, "air");
                    }
                    if (edge) {
                        set(x, f + 1, z, "railing[facing=" + (z < 0 ? "south" : "north") + "]");
                    }
                }
                if (Math.floorMod(x, 5) == 0) {
                    for (int z : new int[] {-5, 0, 5}) {
                        for (int y = sea - 8; y < f - 1; y++) {
                            set(x, y, z, "wf_pile");
                        }
                    }
                }
                if (Math.floorMod(x, 14) == 3) {
                    for (int y = f + 1; y <= f + 3; y++) {
                        set(x, y, -4, "streetlight_pole");
                    }
                    set(x, f + 4, -4, power && chance() < 0.4 ? "streetlight_head[facing=south]" : "streetlight_head_off[facing=south]");
                }
            }
            // the ramp from the jetty down to the road ashore
            for (int x = jx1 + 1; x <= jx1 + 4; x++) {
                for (int z = ROAD0 - 1; z <= ROAD1 + 1; z++) {
                    set(x, 0, z, "asphalt");
                    set(x, 1, z, "air");
                }
            }
            // the quay head
            for (int x = QX; x <= QX + 7; x++) {
                for (int z = -24; z <= 34; z++) {
                    set(x, f, z, x == QX ? "wf_cap" : chance() < 0.08 ? "floor_concrete_crack" : "floor_concrete");
                    set(x, f - 1, z, "concrete_dark");
                    for (int y = f + 1; y <= f + 4; y++) {
                        set(x, y, z, "air");
                    }
                    if ((x == QX + 7 && (z < -5 || z > 5)) || z == -24 || z == 34) {
                        set(x, f + 1, z, "railing[facing=" + (z == -24 ? "south" : z == 34 ? "north" : "west") + "]");
                    }
                    if (Math.floorMod(x - QX, 3) == 0 && Math.floorMod(z, 4) == 0) {
                        for (int y = sea - 8; y < f - 1; y++) {
                            set(x, y, z, "wf_pile");
                        }
                    }
                }
            }
            for (int z = -24; z <= 34; z++) {
                if (z % 4 == 0) {
                    for (int y = sea; y < f; y++) {
                        set(QX - 1, y, z, "wf_fender[facing=west]");
                    }
                }
                if (Math.floorMod(z, 8) == 2) {
                    set(QX, f + 1, z, "vessel_bollard");
                }
                if (Math.floorMod(z, 24) == 5) {
                    for (int y = sea - 1; y <= f; y++) {
                        set(QX - 1, y, z, "bunk_ladder[facing=west]");
                    }
                }
            }
            // the ferry alongside, her bow north, the gangway down to her doors
            PrisonFerry ferry = new PrisonFerry(this, p.turned(QX - 8, 0, 30, 3), sea);
            ferry.build(chance() < 0.5 ? "HOLLIS" : "SEABRIGHT");
            int dz = 30 - PrisonFerry.DOOR;
            for (int z = dz - 1; z <= dz; z++) {
                set(QX - 1, f - 1, z, "wf_gangway[facing=east]");
                set(QX - 2, f - 2, z, "wf_gangway[facing=east]");
                set(QX - 3, f - 2, z, "wf_gangway[facing=east]");
                set(QX - 1, f, z, "air");
                set(QX - 2, f - 1, z, "air");
            }
            set(BELL[0], BELL[1], BELL[2], "zomboidcraft:ferry_bell[facing=east]");
            set(BELL[0] + 1, f + 1, BELL[2] - 2, "pn_post");
            set(BELL[0] + 1, f + 2, BELL[2] - 2, "pn_post");
            // the boards on their posts facing the boat: the crossing, and the warning to anyone else
            for (int[] s : new int[][] {{-9}, {7}}) {
                for (int z = s[0]; z <= s[0] + 1; z++) {
                    set(QX + 1, f + 1, z, "pn_post");
                    set(QX + 1, f + 2, z, "pn_post");
                }
            }
            sign("ferry", QX, f + 2, -9, "west");
            sign("landing", QX, f + 2, 7, "west");
            // the car slip off the north end: the transfer span sloping to the water, its gantry over it
            int s0 = QX + 1, s1 = QX + 6;
            for (int i = 0; i < 12; i++) {
                int z = -25 - i, y = f - i / 3;
                for (int x = s0; x <= s1; x++) {
                    set(x, y, z, x == s0 || x == s1 ? "rd_galv_flat" : "cm_tread");
                    for (int k = y + 1; k <= f + 3; k++) {
                        set(x, k, z, "air");
                    }
                }
                set(s0, y + 1, z, "railing[facing=east]");
                set(s1, y + 1, z, "railing[facing=west]");
            }
            for (int x : new int[] {s0 - 1, s1 + 1}) {
                for (int z : new int[] {-30, -33}) {
                    for (int y = sea - 6; y <= f + 9; y++) {
                        set(x, y, z, y <= f ? "wf_pile" : "steel_girder");
                    }
                }
            }
            for (int x = s0 - 1; x <= s1 + 1; x++) {
                set(x, f + 10, -30, "truss_grey");
                set(x, f + 10, -33, "truss_grey");
            }
            for (int z = -33; z <= -30; z++) {
                set(s0 - 1, f + 10, z, "truss_grey");
                set(s1 + 1, f + 10, z, "truss_grey");
            }
            set((s0 + s1) / 2, f + 9, -31, "sy_chain");
            set((s0 + s1) / 2, f + 8, -31, "sy_chain");
            // the timber dolphins off each end: piles bound together, their heads over the tide
            for (int[] d : new int[][] {{QX - 6, -40}, {QX - 6, 46}, {QX - 14, 8}}) {
                for (int dx = 0; dx <= 1; dx++) {
                    for (int dzz = 0; dzz <= 1; dzz++) {
                        for (int y = sea - 7; y <= f + 1 + (dx + dzz == 0 ? 1 : 0); y++) {
                            set(d[0] + dx, y, d[1] + dzz, "sb_pile");
                        }
                    }
                }
                set(d[0], f + 3, d[1], "vessel_bollard");
            }
            // ashore: the freight shed, the arrivals' luggage, the booth and the barrier
            int x0 = shore + 8, x1 = shore + 26, z0 = 9, z1 = 26;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    set(x, 0, z, "floor_concrete");
                    for (int y = 1; y <= 6; y++) {
                        set(x, y, z, edge ? (chance() < 0.15 ? "corrugated_rust" : "corrugated_grey") : "air");
                    }
                    set(x, 7, z, chance() < 0.04 ? "air" : "ext_porch_roof_tin_galv[facing=north]");
                }
            }
            fill(x0 + 4, 1, z0, x0 + 9, 4, z0, "air");
            for (int x = x0 + 2; x < x1; x += 3) {
                for (int z = z0 + 3; z < z1 - 1; z += 4) {
                    double r = chance();
                    set(x, 1, z, r < 0.3 ? "crate" : r < 0.5 ? "st_pallet[facing=north]" : r < 0.65 ? "ap_bag_pile" : "air");
                    if (r < 0.3 && chance() < 0.5) {
                        set(x, 2, z, "crate");
                    }
                }
            }
            for (int i = 0; i < 26; i++) {
                int x = shore + 2 + rng.nextInt(24), z = ROAD0 - 10 + rng.nextInt(8);
                set(x, 1, z, any("ap_bag_pile", "home_clothes_pile", "pk_trash_bag", "cardboard_box[facing=" + dir() + "]", "blood_splat"));
            }
            camp.booth(shore + 30, ROAD0 - 7);
            camp.boom(shore + 34, ROAD0 - 1, 7, true);
        }

        // ------------------------------------------------------------------ processing

        /**
         * The line every arrival went through (decon-lines 01-03, hazmat-triage-tents 01-06): the
         * container wall right across the island with its one gate on the drive, the guard booth,
         * the chutes the queue was fed down north of the road and the decontamination tents south
         * of it, the showers' run-off, the flood masts, the signs.
         */
        private void processing() {
            int z0 = FZ0, z1 = FZ1;
            while (z0 < 0 && !land(WALL, z0)) {
                z0++;
            }
            while (z1 > 0 && !land(WALL, z1)) {
                z1--;
            }
            camp.containerWall(WALL, z0 + 2, WALL, ROAD0 - 4, 2, 0.05);
            camp.containerWall(WALL, ROAD1 + 4, WALL, z1 - 2, 2, 0.08);
            for (int z = ROAD0 - 3; z <= ROAD1 + 3; z += ROAD1 - ROAD0 + 6) {
                camp.jersey(WALL - 2, z, WALL + 2, z);
            }
            camp.boom(WALL, ROAD0 - 2, 8, chance() < 0.4);
            camp.booth(WALL + 4, ROAD0 - 9);
            camp.floodMast(WALL - 3, ROAD0 - 6, 9);
            camp.floodMast(WALL - 3, ROAD1 + 6, 9);
            sign("decon", WALL - 1, 4, ROAD0 - 5, "west");
            sign("restricted", WALL - 1, 4, ROAD1 + 6, "west");
            // the chutes, the arrivals fed down them to the desks
            camp.processing(WALL - 38, ROAD0 - 34, 22);
            // the decontamination tents south of the road: a shower line of white triage tents, the run-off
            Tents t = new Tents(p, rng);
            t.hit = 0.6;
            for (int i = 0; i < 3; i++) {
                t.tent(WALL - 44 + i * 12, ROAD1 + 8, 6, 12, "white", i == 2 ? "triage" : "supply", 0.2);
            }
            sign("triage", WALL - 20, 3, ROAD1 + 7, "north");
            for (int x = WALL - 46; x <= WALL - 8; x++) {
                for (int z = ROAD1 + 21; z <= ROAD1 + 23; z++) {
                    double r = chance();
                    set(x, 0, z, r < 0.5 ? "minecraft:mud" : r < 0.8 ? "minecraft:packed_mud" : "minecraft:coarse_dirt");
                }
                if (x % 3 == 0) {
                    set(x, 1, ROAD1 + 22, any("bunk_hazmat", "hs_biohazard", "trash_bag[facing=north]", "pw_drum[axis=x]", "blood_splat"));
                }
            }
            camp.toilets(WALL - 20, ROAD1 + 26, 4);
            set(WALL - 8, 1, ROAD1 + 9, "tn_genset[facing=west]");
            camp.lightTower(WALL - 10, ROAD0 - 14, true);
        }

        // ------------------------------------------------------------------ what happened

        /**
         * The last of it, all along the drive: the bodies the burial detail never got to, the
         * stretchers dropped, the hazmat suits torn off and left, the papers out of the office.
         */
        private void story() {
            for (int i = 0; i < 60; i++) {
                int x = shore + 30 + rng.nextInt(CAMP + 200 - shore), z = ROAD0 - 6 + rng.nextInt(ROAD1 - ROAD0 + 13);
                if (!empty(x, 1, z) || !land(x, z)) {
                    continue;
                }
                double r = chance();
                if (r < 0.25) {
                    set(x, 1, z, "qs_shroud_head[facing=" + (z < 0 ? "north" : "south") + "]");
                    set(x, 1, z + (z < 0 ? 1 : -1), "qs_shroud_foot[facing=" + (z < 0 ? "north" : "south") + "]");
                } else if (r < 0.4) {
                    set(x, 1, z, "bunk_bodybag[facing=" + any("east", "west") + "]");
                } else if (r < 0.55) {
                    set(x, 1, z, "st_stretcher[facing=" + dir() + "]");
                } else if (r < 0.7) {
                    set(x, 1, z, any("bunk_hazmat", "paper", "hs_sharps"));
                } else {
                    set(x, 1, z, any("blood_splat", "paper", "glass_shards", "debris_chunk"));
                }
            }
        }
    }
}
