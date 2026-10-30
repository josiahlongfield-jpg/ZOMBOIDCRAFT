package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Black Pine Dam (Josiah, 2026-10-30: the northwest military zone's fourth site, the reservoir
 * raised behind it), after refs/dam: a concrete gravity dam across the Calder's gorge where the
 * river leaves the reservoir, the lake held {@link Terrain#RESERVOIR} high behind it and the river
 * at the sea's level below.
 * <ul>
 * <li>the wall: its upstream face sheer, the downstream face stepping out down the gorge, lift
 * lines and the streaks under the crest, the name cast in it; the crest road along the top
 * between its parapets, the lamps;</li>
 * <li>the gated spillway over the river's channel: the piers, the vertical lift gates in their
 * slots under the hoist gantry, the road bridged over the bays, the chute down the face into the
 * stilling basin and its baffles;</li>
 * <li>the two intake towers standing out in the lake on their bridges, the racks under the water,
 * the penstocks coming out of the face to the powerhouse at the toe: its two units, the crane,
 * the control room, the transformers on the roof, the yard cut into the bank;</li>
 * <li>inside the wall the inspection gallery and the lift down from the crest to it and to the
 * powerhouse;</li>
 * <li>at the west end the overlook and the keeper's house, at the east the Guard's checkpoint.</li>
 * </ul>
 * The Guard held the crest as a choke on the road east; when they went the power went with
 * them, and the keeper stayed.
 *
 * <p>Built where the land lies ({@link Place#natural}): the place's level is the crest's road, and
 * the builder reads the gorge from the county's terrain to found every column on it. Its frame
 * puts the plan's spot at (0, 0): x east along the crest, z south down the river.
 */
final class Dam extends Site {
    /** The frame's bounds round the spot. */
    static final int FX0 = -150, FX1 = 149, FZ0 = -63, FZ1 = 57;
    /** The upstream face, the crest's far parapet; the crest road's centre line. */
    static final int UP = -Terrain.FACE + 1, DOWN = 3, MID = -2;
    /** The spillway's piers (their west columns, three wide) and the gates' crest. */
    static final int[] PIERS = {8, 16, 24, 32, 40};
    static final int OGEE = -9;
    /** The stilling basin: its floor, the end sill. */
    static final int BASIN = -35, SILL_Z = 46;
    /** The units (and their intake towers and penstocks) along x; the intake towers' middle z. */
    static final int[] UNITS = {-52, -32};
    static final int TOWER_Z = -24;
    /** The powerhouse and the yard cut for it; its floor, its roof. */
    static final int PX0 = -66, PX1 = -18, PZ0 = 30, PZ1 = 48;
    static final int EX0 = -74, EX1 = -14, EZ0 = 22, EZ1 = 52;
    static final int FLOOR = -27, ROOF = -12;
    /** The gallery's floor and its run (z); the lift's shaft. */
    static final int GALLERY = -20, GZ0 = 1, GZ1 = 3;
    static final int LX = -44, LZ = 5;
    /** The penstocks' middle, low over the yard on their saddles. */
    static final int PEN_Y = FLOOR + 3;

    private final County county;

    Dam(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    /** The dam's place: the frame round the spot, its level the crest road's, the land left as it lies. */
    static Place place(Plan.Spot s, long h) {
        Place p = new Place(PlaceType.LANDMARK, "dam", s.x() + FX0, s.z() + FZ0, s.x() + FX1, s.z() + FZ1, Terrain.RESERVOIR + 4, h, 2, 0);
        p.natural = true;
        return p;
    }

    /** The downstream face's z at height y (frame): sheer under the crest, then out 0.8 a block. */
    static int face(int y) {
        return y >= -4 ? DOWN : DOWN + (int) (0.8 * (-4 - y));
    }

    /** The wall's top at z: the crest, or the step of the downstream face there. */
    static int top(int z) {
        if (z <= DOWN) {
            return 0;
        }
        int y = -4;
        while (face(y) < z) {
            y--;
        }
        return y;
    }

    static boolean pier(int x) {
        for (int px : PIERS) {
            if (x >= px && x <= px + 2) {
                return true;
            }
        }
        return false;
    }

    /** A bay of the spillway, between two piers. */
    static boolean bay(int x) {
        return x > PIERS[0] + 2 && x < PIERS[PIERS.length - 1] && !pier(x);
    }

    /** The chute's surface down the face of a bay, and the basin's floor beyond it. */
    static int chute(int z) {
        return z <= 0 ? OGEE : Math.max(BASIN, OGEE - (int) Math.round(z * 1.25));
    }

    @Override
    void build() {
        int[][] g = new int[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
        int[][] wet = new int[FX1 - FX0 + 1][FZ1 - FZ0 + 1];
        int cx = p.x0 - FX0, cz = p.z0 - FZ0;
        List<Place> near = county == null ? List.of() : county.placesNear(p.x0, p.z0, p.x1, p.z1, County.SKIRT);
        for (int x = FX0; x <= FX1; x++) {
            for (int z = FZ0; z <= FZ1; z++) {
                int wx = cx + x, wz = cz + z;
                if (county == null) {
                    // a preview without the county: a gorge cut through a bank at the crest
                    int d = Math.abs(x - 10);
                    g[x - FX0][z - FZ0] = z < UP ? -20 : d < 80 ? -34 : -1;
                    wet[x - FX0][z - FZ0] = z < UP ? -5 : -29;
                    continue;
                }
                g[x - FX0][z - FZ0] = (int) Math.floor(county.ground(wx, wz, near)) - p.level;
                wet[x - FX0][z - FZ0] = county.terrain.water(wx, wz) - 1 - p.level;
            }
        }
        Placer a = new Placer(out, -FX0, 0, -FZ0, 0);
        new Works(a, new SplittableRandom(p.seed), g, wet, new Noise(p.seed)).build();
    }

    /** The dam drawn in its frame. */
    static final class Works extends Grounds {
        private final int[][] g, wet;
        private final Noise noise;
        private final Camp camp;

        Works(Placer p, SplittableRandom rng, int[][] g, int[][] wet, Noise noise) {
            super(p, rng, FX1 - FX0 + 1, FZ1 - FZ0 + 1, 0.45, 0.2, 0.0);
            this.g = g;
            this.wet = wet;
            this.noise = noise;
            this.camp = new Camp(p, rng, 0.55);
        }

        @Override
        String signs() {
            return "dm_sgn_";
        }

        /** The ground's top block at (x, z), frame y. */
        int ground(int x, int z) {
            x = Math.max(FX0, Math.min(FX1, x));
            z = Math.max(FZ0, Math.min(FZ1, z));
            return g[x - FX0][z - FZ0];
        }

        /** The water's top block over (x, z): the lake's or the river's. */
        int water(int x, int z) {
            x = Math.max(FX0, Math.min(FX1, x));
            z = Math.max(FZ0, Math.min(FZ1, z));
            return wet[x - FX0][z - FZ0];
        }

        void build() {
            wall();
            crest();
            spillway();
            yard();
            gallery();
            powerhouse();
            penstocks();
            towers();
            lift();
            name();
            boom();
            overlook();
            keeper();
            checkpoint();
            story();
        }

        // ------------------------------------------------------------------ the wall

        /** Mass concrete: now and then the stained or mossy pour; wet and green low on the faces. */
        private String conc(int x, int y, int z) {
            double n = noise.fbm(x * 0.7, y * 1.3 + z * 0.3, 9, 2) * 0.5 + 0.5, r = chance();
            if (z == UP && y <= -4 && y >= -7) {
                // the band the lake's level wanders in
                return r < 0.7 ? "dm_conc_moss" : "dm_conc_stain";
            }
            if (z == UP && y < -7) {
                return r < 0.35 ? "dm_conc_moss" : "dm_conc_stain";
            }
            if (z > DOWN && y < -26) {
                return r < 0.4 ? "dm_conc_wet" : r < 0.6 ? "dm_conc_moss" : "dm_conc";
            }
            // the streaks down the downstream face from the crest's drains, and the lime bled from the joints
            if (z > DOWN && (Math.floorMod(x * 7 + 3, 11) == 0 || Math.floorMod(x * 5 + 1, 13) == 0) && r < 0.85) {
                return "dm_conc_stain";
            }
            if (z > DOWN && r < 0.12) {
                return "dm_conc_stain";
            }
            return n < 0.18 ? "dm_conc_stain" : n > 0.86 ? "dm_conc_moss" : "dm_conc";
        }

        /**
         * Every column from the upstream face down the gorge to the downstream face's toe, founded
         * two under the rock; where the bank stands above the crest the road is cut through it.
         */
        private void wall() {
            for (int x = FX0; x <= FX1; x++) {
                for (int z = UP; z <= 45; z++) {
                    int t = top(z), gr = ground(x, z);
                    if (gr >= t) {
                        continue;
                    }
                    for (int y = Math.max(Voxels.MIN_Y + 1, gr - 2); y <= t; y++) {
                        set(x, y, z, conc(x, y, z));
                    }
                }
                // the bank cut back over the crest
                for (int z = UP - 2; z <= DOWN + 2; z++) {
                    for (int y = 1; y <= Math.max(6, ground(x, z) + 1); y++) {
                        set(x, y, z, "air");
                    }
                }
                // an earth shoulder either side of the crest where the bank is lower than the road
                for (int k = 1; k <= 6; k++) {
                    for (int z : new int[] {UP - k, DOWN + k}) {
                        int t = -k, gr = ground(x, z);
                        if (gr >= t || (z > DOWN && gr < top(z) && top(z) > -40)) {
                            continue;
                        }
                        if (z < UP && water(x, z) > gr) {
                            continue; // the lake comes right up to the face
                        }
                        for (int y = gr + 1; y < t; y++) {
                            set(x, y, z, "minecraft:dirt");
                        }
                        set(x, t, z, chance() < 0.2 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                        for (int y = t + 1; y <= 4; y++) {
                            set(x, y, z, "air");
                        }
                    }
                }
            }
        }

        /**
         * The crest: the road between its sidewalks, the parapets either side, the lamps over it
         * on the upstream side; across the spillway it runs on the bridge.
         */
        private void crest() {
            for (int x = FX0; x <= FX1; x++) {
                boolean spill = x >= PIERS[0] && x <= PIERS[PIERS.length - 1] + 2;
                for (int z = UP; z <= DOWN; z++) {
                    double r = chance();
                    String b;
                    if (z == UP || z == DOWN) {
                        b = conc(x, 0, z);
                    } else if (z == UP + 1 || z == DOWN - 1) {
                        b = r < 0.12 ? "sidewalk_cracked" : "sidewalk";
                    } else if (z == MID && Math.floorMod(x, 4) < 2) {
                        b = "road_line_yellow[axis=x]";
                    } else {
                        b = r < 0.08 ? "asphalt_crack" : r < 0.12 ? "asphalt_patch" : "asphalt";
                    }
                    if (spill && z <= UP + 2) {
                        continue;
                    }
                    set(x, 0, z, b);
                }
                if (!spill) {
                    set(x, 1, UP, "dm_parapet[facing=north]");
                }
                set(x, 1, DOWN, "dm_parapet[facing=south]");
                // the lamp standards along both parapets, staggered
                int lz = Math.floorMod(x, 24) == 6 && !spill ? UP + 1 : Math.floorMod(x, 24) == 18 ? DOWN - 1 : 0;
                if (lz != 0 && Math.abs(x - LX - 1) > 4 && Math.abs(x - UNITS[0]) > 2 && Math.abs(x - UNITS[1]) > 2) {
                    for (int y = 1; y <= 3; y++) {
                        set(x, y, lz, "cv_lamp_post");
                    }
                    set(x, 4, lz, chance() < 0.2 ? "air" : "cv_lamp_globe");
                }
            }
            // the danger notices on the parapet's lamp standards, facing the road
            for (int x = -120; x <= 120; x += 48) {
                if (!(x >= PIERS[0] - 2 && x <= PIERS[PIERS.length - 1] + 4)) {
                    sign(x % 96 == 0 ? "danger" : "swim", x - 1, 1, DOWN - 1, "north");
                }
            }
        }

        // ------------------------------------------------------------------ the spillway

        /**
         * Five piers and four bays: in each bay the ogee crest under its gate, the gate down in its
         * slot holding the lake, the chute down the face to the basin; the piers carried up into
         * the hoist gantry over the slots and down the chute as its walls; the road on its bridge
         * over the bays; the basin's baffles and its end sill.
         */
        private void spillway() {
            int x0 = PIERS[0], x1 = PIERS[PIERS.length - 1] + 2;
            for (int x = x0; x <= x1; x++) {
                boolean pr = pier(x);
                for (int z = UP; z <= SILL_Z; z++) {
                    int c = chute(z), gr = ground(x, z);
                    if (pr) {
                        // the pier: tower up to the gantry upstream, the bridge's bearing, then the chute's wall
                        int t = z <= UP + 2 ? 4 : z <= DOWN ? -1 : c + 3;
                        for (int y = Math.min(gr - 2, c - 2); y <= t; y++) {
                            set(x, y, z, conc(x, y, z));
                        }
                        if (z > DOWN) {
                            for (int y = t + 1; y <= 0; y++) {
                                set(x, y, z, c < -29 && y <= -29 ? "minecraft:water" : "air");
                            }
                        }
                        continue;
                    }
                    for (int y = Math.min(gr - 2, c - 3); y <= c; y++) {
                        set(x, y, z, y == c ? (z <= 0 ? "dm_conc" : chance() < 0.7 ? "dm_conc_wet" : "dm_conc_moss") : "dm_conc");
                    }
                    for (int y = c + 1; y <= 0; y++) {
                        set(x, y, z, "air");
                    }
                    if (c < -29) {
                        // the basin and the chute's foot full to the river's level
                        for (int y = c + 1; y <= -29; y++) {
                            set(x, y, z, "minecraft:water");
                        }
                    }
                }
                // the bays' gates and the lake against them, the bridge deck over them
                if (!pr) {
                    for (int y = OGEE + 1; y <= -5; y++) {
                        set(x, y, UP, "minecraft:water");
                    }
                    for (int y = OGEE + 1; y <= -5; y++) {
                        set(x, y, UP + 1, "dm_gate[axis=x]");
                    }
                    set(x, -4, UP + 1, "dm_gate_top[axis=x]");
                    for (int z = UP + 2; z <= DOWN; z++) {
                        set(x, -1, z, "dm_conc");
                    }
                    set(x, 1, UP + 2, "dm_parapet[facing=north]");
                }
                for (int z = UP + 2; z <= DOWN; z++) {
                    double r = chance();
                    String b = z == DOWN ? "dm_conc" : z == DOWN - 1 ? (r < 0.12 ? "sidewalk_cracked" : "sidewalk")
                            : z == MID && Math.floorMod(x, 4) < 2 ? "road_line_yellow[axis=x]"
                            : r < 0.08 ? "asphalt_crack" : r < 0.12 ? "asphalt_patch" : "asphalt";
                    set(x, 0, z, b);
                }
                // the gantry over the slots, the hoists on it
                for (int z = UP; z <= UP + 1; z++) {
                    set(x, 5, z, "dm_conc");
                }
                set(x, 6, UP, "dm_parapet[facing=north]");
            }
            for (int i = 0; i + 1 < PIERS.length; i++) {
                int mx = PIERS[i] + 5;
                set(mx, 6, UP + 1, "dm_hoist[facing=south]");
                set(mx - 1, 6, UP + 1, chance() < 0.5 ? "fit_jbox" : "air");
            }
            // a ladder up the first pier's back to the gantry
            for (int y = 1; y <= 5; y++) {
                set(PIERS[0] + 1, y, UP + 2, "ladder[facing=south]");
            }
            // the baffle blocks and the end sill in the basin
            for (int x = x0 + 3; x <= x1 - 3; x++) {
                if (pier(x)) {
                    continue;
                }
                for (int zb : new int[] {30, 36}) {
                    if (Math.floorMod(x + zb / 6, 3) != 0) {
                        set(x, BASIN + 1, zb, "dm_conc_moss");
                        set(x, BASIN + 2, zb, "dm_conc_wet");
                    }
                }
                for (int y = BASIN + 1; y <= BASIN + 3; y++) {
                    set(x, y, SILL_Z, y == BASIN + 3 ? "dm_conc_moss" : "dm_conc");
                }
            }
            // the basin's side walls on past the chute to the sill
            for (int x : new int[] {x0 - 1, x1 + 1}) {
                for (int z = 4; z <= SILL_Z; z++) {
                    for (int y = Math.min(ground(x, z), BASIN) - 1; y <= Math.max(chute(z) + 3, -27); y++) {
                        set(x, y, z, conc(x, y, z));
                    }
                }
            }
            sign("danger", PIERS[1], -2, UP + 2, "south");
        }

        // ------------------------------------------------------------------ inside the wall

        /** The inspection gallery along the wall over the rock, its drain and lamps, the adits off it. */
        private void gallery() {
            for (int x = FX0; x <= FX1; x++) {
                boolean deep = true;
                for (int z = GZ0 - 1; z <= GZ1 + 1; z++) {
                    deep &= ground(x, z) <= GALLERY - 3;
                }
                if (!deep) {
                    continue;
                }
                for (int z = GZ0; z <= GZ1; z++) {
                    set(x, GALLERY, z, z != GZ0 ? "dm_conc" : Math.floorMod(x, 6) == 3 ? "bd_drain" : "dm_conc_wet");
                    for (int y = GALLERY + 1; y <= GALLERY + 3; y++) {
                        set(x, y, z, "air");
                    }
                }
                if (Math.floorMod(x, 6) == 0) {
                    set(x, GALLERY + 3, GZ0 + 1, chance() < 0.25 ? "fit_tube_flicker" : "fit_tube");
                }
                if (Math.floorMod(x, 9) == 4) {
                    set(x, GALLERY + 2, GZ1, "fit_jbox");
                }
                if (chance() < 0.06) {
                    set(x, GALLERY + 1, GZ1, any("debris_chunk", "leaf_litter", "crate"));
                }
            }
            sign("gallery", LX - 3, GALLERY + 2, GZ0, "south");
            // the low passage at the powerhouse's level from the lift's foot out through the toe
            for (int x = LX; x <= LX + 6; x++) {
                for (int z = GZ0; z <= GZ1; z++) {
                    set(x, FLOOR, z, "dm_conc");
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
            for (int x = LX + 5; x <= LX + 6; x++) {
                for (int z = GZ0; z <= EZ0; z++) {
                    set(x, FLOOR, z, "dm_conc");
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        set(x, y, z, "air");
                    }
                    if (z % 7 == 0 && x == LX + 5) {
                        set(x, FLOOR + 3, z, "fit_tube");
                    }
                }
            }
        }

        /**
         * The lift from the crest down to the gallery and the powerhouse's passage, its doors to
         * the north at every stop, its machine room a hut on the crest's downstream edge.
         */
        private void lift() {
            lift(LX, LZ, 3, 3, new int[] {FLOOR, GALLERY, 0, 4}, "dm_conc", 4);
            for (int x = LX - 1; x <= LX + 3; x++) {
                set(x, 1, DOWN, "air");
                set(x, 0, DOWN, "sidewalk");
            }
            sign("lift", LX, 4, LZ - 2, "north");
            set(LX + 1, 9, LZ + 1, "bd_vent");
            // the machine room's windows, the coping round its roof
            for (int x = LX; x <= LX + 2; x++) {
                set(x, 6, LZ + 3, sash("south"));
            }
            for (int z = LZ; z <= LZ + 2; z++) {
                set(LX - 1, 6, z, sash("west"));
                set(LX + 3, 6, z, sash("east"));
            }
            for (int x = LX - 1; x <= LX + 3; x++) {
                set(x, 9, LZ - 1, "bd_coping[facing=north]");
                set(x, 9, LZ + 3, "bd_coping[facing=south]");
            }
        }

        /** BLACK PINE DAM 1961 cast into the downstream face under the crest. */
        private void name() {
            row("dm_name_", 8, -24, -3, DOWN + 1, "south", 0.0);
            // the elevation board and the staff gauge on the upstream face
            sign("elev", -12, -3, UP - 1, "north");
            for (int y = -14; y <= -4; y++) {
                set(-8, y, UP - 1, "dm_gauge[facing=north]");
            }
        }

        /** The boat barrier strung across the lake upstream of the towers, a buoy's sign on it. */
        private void boom() {
            int z = TOWER_Z - 18;
            for (int x = FX0 + 2; x <= FX1 - 2; x++) {
                if (water(x, z) == -5 && ground(x, z) < -5) {
                    set(x, -4, z, chance() < 0.04 ? "air" : "dm_floats[axis=x]");
                }
            }
        }

        // ------------------------------------------------------------------ the powerhouse

        /**
         * The yard cut into the west bank at the toe for the powerhouse: the floor at the
         * powerhouse's level, the cut walled in concrete round it, a platform out over the river.
         */
        private void yard() {
            for (int x = EX0; x <= EX1; x++) {
                for (int z = EZ0; z <= EZ1; z++) {
                    int gr = ground(x, z);
                    boolean edge = x == EX0 || z == EZ0 || z == EZ1;
                    if (gr > FLOOR) {
                        for (int y = FLOOR + 1; y <= gr + 1; y++) {
                            if (z > face(y)) {
                                set(x, y, z, edge ? conc(x, y, z) : "air");
                            }
                        }
                        if (edge) {
                            set(x, gr + 2, z, "dm_parapet[facing=" + (x == EX0 ? "east" : z == EZ0 ? "south" : "north") + "]");
                        }
                    } else {
                        for (int y = gr - 1; y < FLOOR; y++) {
                            set(x, y, z, conc(x, y, z));
                        }
                    }
                    double r = chance(), n = noise.fbm(x, z, 7, 2);
                    set(x, FLOOR, z, gr < FLOOR ? (r < 0.3 ? "dm_conc_wet" : "dm_conc")
                            : n > 0.35 ? (r < 0.5 ? "minecraft:gravel" : "mu_gravel") : r < 0.06 ? "dm_conc_wet" : "floor_concrete");
                }
            }
            // the bollards along the platform's edge over the tailrace
            for (int x = PX1 + 2; x <= EX1; x += 3) {
                set(x, FLOOR + 1, EZ1, "vessel_bollard");
            }
            // the stair in the bank behind the cut's west wall, up to the top; its foot tunnelled through
            int sx = EX0 - 6, sz = 30, top = Math.max(FLOOR + 8, ground(sx + 2, sz + 10));
            stair(sx, sz, true, new int[] {FLOOR, top}, "dm_conc", "dm_conc");
            for (int x = sx + 1; x < EX0 + 1; x++) {
                for (int z = sz + 10; z <= sz + 11; z++) {
                    set(x, FLOOR, z, "dm_conc");
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        set(x, y, z, y == FLOOR + 3 && x == sx + 3 ? "fit_tube" : "air");
                    }
                    set(x, FLOOR + 4, z, "dm_conc");
                }
            }
            for (int[] f : new int[][] {{EX1 - 2, EZ0 + 2}, {EX0 + 8, EZ1 - 2}}) {
                for (int y = FLOOR + 1; y < FLOOR + 8; y++) {
                    set(f[0], y, f[1], "streetlight_pole");
                }
                set(f[0], FLOOR + 8, f[1], Camp.lamp(rng, 0.9, "cp_flood_bar", "north"));
            }
        }

        /**
         * The powerhouse: concrete walls with the high windows, the two units on the floor, the
         * crane over them, the control room at the west end behind its glass, the transformers on
         * the roof; dark since the Guard pulled out.
         */
        private void powerhouse() {
            for (int y = FLOOR + 1; y < ROOF; y++) {
                for (int x = PX0; x <= PX1; x++) {
                    set(x, y, PZ0, conc(x, y, PZ0 + 1));
                    set(x, y, PZ1, conc(x, y, PZ1 + 1));
                }
                for (int z = PZ0; z <= PZ1; z++) {
                    set(PX0, y, z, conc(PX0, y, z + 50));
                    set(PX1, y, z, conc(PX1, y, z + 50));
                }
            }
            for (int x = PX0 + 1; x < PX1; x++) {
                for (int z = PZ0 + 1; z < PZ1; z++) {
                    set(x, FLOOR, z, z == PZ0 + 3 ? "pw_tile_line" : "pw_tile");
                    for (int y = FLOOR + 1; y < ROOF; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
            for (int x = PX0; x <= PX1; x++) {
                for (int z = PZ0; z <= PZ1; z++) {
                    set(x, ROOF, z, "dm_conc");
                }
            }
            // the high windows along the south and east walls
            for (int y = FLOOR + 8; y <= FLOOR + 10; y++) {
                for (int x = PX0 + 3; x <= PX1 - 3; x++) {
                    if (Math.floorMod(x - PX0, 4) != 0) {
                        set(x, y, PZ1, sash("south"));
                    }
                }
                for (int z = PZ0 + 3; z <= PZ1 - 3; z++) {
                    if (Math.floorMod(z - PZ0, 4) != 0) {
                        set(PX1, y, z, sash("east"));
                    }
                }
            }
            // the doors: the passage from the lift, the yard's, the tailrace's
            Doors.abandoned(p, rng, LX + 5, FLOOR + 1, PZ0, Doors.STEEL, "north", "left");
            Doors.abandoned(p, rng, PX1, FLOOR + 1, 40, Doors.STEEL, "east", Doors.hinge(rng));
            Doors.abandoned(p, rng, -60, FLOOR + 1, PZ1, Doors.STEEL, "south", Doors.hinge(rng));
            sign("power", LX + 3, FLOOR + 4, PZ0 - 1, "north");
            for (int i = 0; i < UNITS.length; i++) {
                unit(UNITS[i], i);
            }
            control();
            crane();
            // the lamps hung from the roof, all dead
            for (int x = PX0 + 6; x < PX1; x += 8) {
                for (int z = PZ0 + 5; z < PZ1; z += 8) {
                    set(x, ROOF - 1, z, chance() < 0.3 ? "fit_highbay_flicker" : "fit_highbay");
                }
            }
            roof();
            set(PX0 + 3, FLOOR + 1, PZ1 - 2, "pk_tool_chest[facing=north]");
            set(PX1 - 3, FLOOR + 1, PZ0 + 2, "navy_toolbox[facing=west]");
            mess(PX0 + 1, PZ0 + 1, PX1 - 1, PZ1 - 1, FLOOR + 1, 0.025);
        }

        private String sash(String out) {
            double r = chance();
            return (r < 0.3 ? "pw_sash_broken" : "pw_sash") + "[facing=" + out + "]";
        }

        /** A unit: the generator's housing on its barrel, the exciter on top, the platform round it, its sign. */
        private void unit(int ux, int i) {
            int z0 = 36, z1 = 42;
            for (int x = ux - 5; x <= ux + 5; x++) {
                for (int z = z0 - 2; z <= z1 + 2; z++) {
                    set(x, FLOOR, z, "fit_deckplate");
                }
            }
            for (int x = ux - 3; x <= ux + 3; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean corner = (x == ux - 3 || x == ux + 3) && (z == z0 || z == z1);
                    if (corner) {
                        continue;
                    }
                    for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
                        boolean side = z == z0 || z == z1;
                        String b = y == FLOOR + 2 && (side || x == ux - 3 || x == ux + 3) ? "pw_tg_joint" : "pw_tg";
                        if (y == FLOOR + 3 && side) {
                            b = "pw_tg_round[facing=" + (z == z0 ? "north" : "south") + "]";
                        }
                        set(x, y, z, b);
                    }
                }
            }
            for (int x = ux - 1; x <= ux + 1; x++) {
                for (int z = 38; z <= 40; z++) {
                    set(x, FLOOR + 4, z, z == 39 ? "pw_tg_joint" : "pw_tg_round[facing=" + (z == 38 ? "north" : "south") + "]");
                }
            }
            set(ux, FLOOR + 5, 39, "pw_tg");
            set(ux - 4, FLOOR + 3, z0 - 1, "pw_tg_plate[facing=north]");
            sign(i == 0 ? "unit1" : "unit2", ux - 1, FLOOR + 6, PZ0 + 1, "south");
            // the governor's cabinets beside it, and its gauges
            set(ux + 5, FLOOR + 1, z0, "pw_vb_lo[facing=west]");
            set(ux + 5, FLOOR + 2, z0, "pw_vb_hi[facing=west]");
            set(ux + 5, FLOOR + 1, z1, "pw_gauge[facing=west]");
        }

        /** The control room at the west end: the boards along the wall, the desks, the mimic, its glass. */
        private void control() {
            int cx = PX0 + 8;
            for (int z = PZ0 + 1; z < PZ1; z++) {
                for (int y = FLOOR + 1; y <= FLOOR + 4; y++) {
                    boolean glass = y >= FLOOR + 2 && y <= FLOOR + 3 && z % 3 != 0;
                    set(cx, y, z, glass ? sash("east") : "dm_conc");
                }
            }
            for (int x = PX0 + 1; x <= cx; x++) {
                for (int z = PZ0 + 1; z < PZ1; z++) {
                    set(x, FLOOR + 5, z, "un_slab_vct");
                }
            }
            Doors.abandoned(p, rng, cx, FLOOR + 1, 44, Doors.STEEL, "east", Doors.hinge(rng));
            for (int z = PZ0 + 3; z <= PZ1 - 3; z++) {
                set(PX0 + 1, FLOOR + 1, z, "pw_vb_lo[facing=east]");
                set(PX0 + 1, FLOOR + 2, z, "pw_vb_hi[facing=east]");
            }
            for (int z = 34; z <= 40; z += 3) {
                set(PX0 + 4, FLOOR + 1, z, "pw_bench[facing=west]");
                set(PX0 + 5, FLOOR + 1, z, chance() < 0.4 ? "un_stool" : "air");
            }
            picture("pw_mimic_", 6, 2, PX0 + 1, FLOOR + 2, PZ0 + 1, "south");
            set(cx - 2, FLOOR + 1, PZ1 - 2, "pw_desk[facing=north]");
            set(cx - 3, FLOOR + 1, PZ1 - 2, "home_radio_low[facing=north]");
            mess(PX0 + 2, PZ0 + 2, cx - 1, PZ1 - 2, FLOOR + 1, 0.06);
        }

        /** The crane: its rails along the long walls, the bridge across over Unit 1, the trolley. */
        private void crane() {
            int y = ROOF - 2;
            for (int x = PX0 + 9; x < PX1; x++) {
                set(x, y - 1, PZ0 + 1, "pw_crane_rail[facing=south]");
                set(x, y - 1, PZ1 - 1, "pw_crane_rail[facing=north]");
            }
            int bx = UNITS[0] + 2;
            for (int z = PZ0 + 1; z <= PZ1 - 1; z++) {
                set(bx, y, z, "pw_crane[axis=z]");
                set(bx + 2, y, z, "pw_crane[axis=z]");
            }
            for (int x = bx; x <= bx + 2; x++) {
                set(x, y, PZ0 + 1, "pw_crane_end[facing=north]");
                set(x, y, PZ1 - 1, "pw_crane_end[facing=south]");
            }
            set(bx + 2, y - 1, PZ1 - 3, "pw_crane_cab[facing=west]");
            for (int x = bx; x <= bx + 2; x++) {
                set(x, y + 1, 39, "pw_trolley[facing=north]");
            }
            for (int yy = FLOOR + 7; yy < y; yy++) {
                set(bx + 1, yy, 39, "cv_chain");
            }
        }

        /** On the roof: the two transformers on their pads, the vents, the hatch, the drains. */
        private void roof() {
            for (int ux : UNITS) {
                int z0 = 36;
                for (int x = ux - 2; x <= ux + 2; x++) {
                    for (int z = z0; z <= z0 + 4; z++) {
                        set(x, ROOF + 1, z, "concrete_dark");
                        if (Math.abs(x - ux) <= 1 && z > z0 && z < z0 + 4) {
                            set(x, ROOF + 2, z, "pw_xfmr");
                            set(x, ROOF + 3, z, "pw_xfmr");
                        }
                    }
                }
                for (int z = z0 + 1; z <= z0 + 3; z++) {
                    set(ux - 2, ROOF + 2, z, "pw_fins[facing=west]");
                    set(ux + 2, ROOF + 2, z, "pw_fins[facing=east]");
                }
                for (int dx = -1; dx <= 1; dx++) {
                    set(ux + dx, ROOF + 4, z0 + 1, "pw_bushing");
                    set(ux + dx, ROOF + 4, z0 + 3, "pw_bushing_lo");
                }
                set(ux, ROOF + 4, z0 + 2, "pw_conservator[axis=x]");
                set(ux - 3, ROOF + 1, z0 - 1, "sign_post");
                sign("danger", ux - 3, ROOF + 2, z0 - 1, "north");
            }
            set(PX0 + 4, ROOF + 1, PZ0 + 4, "bd_hatch");
            for (int x = PX0 + 2; x < PX1; x += 9) {
                set(x, ROOF + 1, PZ1 - 2, "bd_vent");
                set(x + 4, ROOF + 1, PZ0 + 2, "bd_drain");
            }
            for (int x = PX0; x <= PX1; x++) {
                set(x, ROOF + 1, PZ0, "bd_coping[facing=north]");
                set(x, ROOF + 1, PZ1, "bd_coping[facing=south]");
            }
            for (int z = PZ0 + 1; z < PZ1; z++) {
                set(PX0, ROOF + 1, z, "bd_coping[facing=west]");
                set(PX1, ROOF + 1, z, "bd_coping[facing=east]");
            }
        }

        /** The penstocks out of the face to the units, on their saddles, the valves halfway. */
        private void penstocks() {
            for (int ux : UNITS) {
                for (int z = face(PEN_Y) - 1; z <= PZ0; z++) {
                    set(ux, PEN_Y, z, z == 25 ? "dm_valve[axis=z]" : "dm_penstock[axis=z]");
                    if (z > face(PEN_Y) && z < PZ0 && z % 3 == 0) {
                        for (int y = FLOOR + 1; y < PEN_Y; y++) {
                            set(ux, y, z, "dm_conc");
                        }
                    }
                }
                // in the powerhouse the pipe runs on into the unit's spiral case
                for (int z = PZ0 + 1; z <= 35; z++) {
                    set(ux, PEN_Y, z, z == 35 ? "dm_valve[axis=z]" : "dm_penstock[axis=z]");
                }
            }
        }

        // ------------------------------------------------------------------ the intakes

        /**
         * The intake towers out in the lake over the units' penstocks: the racks down their faces
         * under the water, the gate house on top with its hoist and windows, the bridge to the
         * crest on its piers.
         */
        private void towers() {
            for (int ux : UNITS) {
                int x0 = ux - 3, x1 = ux + 3, z0 = TOWER_Z - 3, z1 = TOWER_Z + 3;
                int foot = Math.min(ground(ux, TOWER_Z), -8) - 2;
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean wallC = x == x0 || x == x1 || z == z0 || z == z1;
                        for (int y = foot; y <= 5; y++) {
                            String b;
                            if (y == foot || y == 0 || y == 5) {
                                b = conc(x, y, z == z0 ? UP : z);
                            } else if (!wallC) {
                                b = y < -4 ? "minecraft:water" : "air";
                            } else if (y < -6 && y > foot + 1 && Math.abs((z == z0 || z == z1 ? x - ux : z - TOWER_Z)) <= 1) {
                                b = "dm_rack[axis=" + (z == z0 || z == z1 ? "x" : "z") + "]";
                            } else if (y >= 2 && y <= 3 && y > 0 && ((z == z0 || z == z1) ? Math.abs(x - ux) == 2 : Math.abs(z - TOWER_Z) == 2)) {
                                b = sash(z == z0 ? "north" : z == z1 ? "south" : x == x0 ? "west" : "east");
                            } else {
                                b = conc(x, y, y <= -4 ? UP : z);
                            }
                            set(x, y, z, b);
                        }
                    }
                }
                for (int x = x0; x <= x1; x++) {
                    set(x, 6, z0, "dm_parapet[facing=north]");
                    set(x, 6, z1, "dm_parapet[facing=south]");
                }
                for (int z = z0 + 1; z < z1; z++) {
                    set(x0, 6, z, "dm_parapet[facing=west]");
                    set(x1, 6, z, "dm_parapet[facing=east]");
                }
                set(ux, 6, TOWER_Z, "bd_vent");
                // inside: the hoist over the gate's well, the panel, the hatch
                set(ux, 1, TOWER_Z - 1, "dm_hoist[facing=south]");
                set(x0 + 1, 1, z0 + 1, "pw_vb_lo[facing=east]");
                set(x0 + 1, 2, z0 + 1, "pw_vb_hi[facing=east]");
                set(x1 - 1, 1, z0 + 1, "bd_hatch");
                set(ux, 4, TOWER_Z, "fit_tube");
                Doors.abandoned(p, rng, ux, 1, z1, Doors.STEEL, "south", Doors.hinge(rng));
                sign("intake", ux - 3, 3, z1 + 1, "south");
                // the bridge back to the crest
                for (int z = z1 + 1; z < UP; z++) {
                    for (int x = ux - 1; x <= ux + 1; x++) {
                        set(x, 0, z, x == ux ? "dm_conc" : "dm_conc_stain");
                        set(x, 1, z, x == ux - 1 ? "dm_parapet[facing=west]" : x == ux + 1 ? "dm_parapet[facing=east]" : "air");
                    }
                    if ((z - z1) % 6 == 3) {
                        for (int y = Math.min(ground(ux, z), -6) - 1; y < 0; y++) {
                            set(ux, y, z, conc(ux, y, UP));
                        }
                    }
                }
                for (int x = ux; x <= ux; x++) {
                    set(x, 1, UP, "air");
                }
            }
        }

        // ------------------------------------------------------------------ the ends

        /** The overlook at the west end over the lake: the paving, the parapet, the telescopes, the benches. */
        private void overlook() {
            int x0 = -146, x1 = -126, z0 = UP - 12, z1 = UP - 3;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = Math.min(ground(x, z), 0) - 1; y < 0; y++) {
                        set(x, y, z, conc(x, y, z));
                    }
                    set(x, 0, z, chance() < 0.15 ? "un_paver_b" : "un_paver");
                    for (int y = 1; y <= 4; y++) {
                        set(x, y, z, "air");
                    }
                    boolean edge = x == x0 || x == x1 || z == z0;
                    if (edge) {
                        set(x, 1, z, "dm_parapet[facing=" + (z == z0 ? "north" : x == x0 ? "west" : "east") + "]");
                    }
                }
            }
            for (int x = x0 + 4; x <= x1 - 4; x += 6) {
                set(x, 1, z0 + 1, "wf_telescope[facing=north]");
                set(x + 2, 1, z1, "mu_bench[facing=north]");
            }
            set(x0 + 2, 1, z1, "dm_conc");
            set(x0 + 3, 1, z1, "dm_conc");
            sign("lookout", x0 + 2, 1, z1 - 1, "north");
            for (int y = 1; y <= 3; y++) {
                set(x1 - 2, y, z1 - 1, "cv_lamp_post");
            }
            set(x1 - 2, 4, z1 - 1, "cv_lamp_globe");
        }

        /** The keeper's house on the west bank below the crest, its drive to the road, his gate piers. */
        private void keeper() {
            int x0 = -146, z0 = 14;
            for (int x = x0 - 3; x <= x0 + 17; x++) {
                for (int z = DOWN + 1; z <= z0 + 14; z++) {
                    int gr = ground(x, z);
                    for (int y = gr + 1; y < 0; y++) {
                        set(x, y, z, "minecraft:dirt");
                    }
                    set(x, 0, z, chance() < 0.1 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                    for (int y = 1; y <= Math.max(gr + 1, 1); y++) {
                        set(x, y, z, "air");
                    }
                    if (chance() < 0.08) {
                        set(x, 1, z, chance() < 0.4 ? "weeds_tall" : "grass_tuft");
                    }
                }
            }
            House h = new House(p.turned(x0, 0, z0, 0), rng, House.Style.BUNGALOW, House.Condition.BOARDED,
                    House.palette(House.Style.BUNGALOW, rng), 14, 11, 1);
            h.build();
            int dx = x0 + h.doorX();
            for (int z = DOWN + 1; z < z0 + h.frontZ(); z++) {
                set(dx, 0, z, chance() < 0.6 ? "minecraft:gravel" : "mu_gravel");
                set(dx + 1, 0, z, chance() < 0.6 ? "minecraft:gravel" : "mu_gravel");
                set(dx, 1, z, "air");
                set(dx + 1, 1, z, "air");
            }
            for (int px : new int[] {dx - 2, dx - 1, dx + 2}) {
                set(px, 1, DOWN + 4, "dm_conc");
                set(px, 2, DOWN + 4, "dm_conc");
            }
            sign("keeper", dx - 2, 2, DOWN + 3, "north");
            for (int x = dx - 1; x <= dx + 2; x++) {
                set(x, 1, DOWN, "air");
            }
        }

        /** The Guard's checkpoint at the east end: the booth, the barrier across, the barriers and sandbags. */
        private void checkpoint() {
            int bx = 134;
            for (int x = bx - 2; x <= bx + 6; x++) {
                for (int z = DOWN + 1; z <= DOWN + 6; z++) {
                    for (int y = Math.min(ground(x, z), 0); y < 0; y++) {
                        set(x, y, z, "dm_conc");
                    }
                    set(x, 0, z, "floor_concrete");
                    for (int y = 1; y <= 4; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
            for (int x = bx - 2; x <= bx + 6; x++) {
                set(x, 1, DOWN, "air");
            }
            camp.booth(bx, DOWN + 2);
            new Camp(p.turned(bx + 8, 0, UP + 1, 1), rng, 0.55).boom(0, 0, DOWN - UP - 2, chance() < 0.5);
            camp.jersey(bx - 14, UP + 1, bx - 14, MID);
            camp.jersey(bx - 22, MID + 1, bx - 22, DOWN - 1);
            for (int z = UP + 1; z <= UP + 3; z++) {
                set(bx - 30, 1, z, "sandbags[facing=west]");
                set(bx - 30, 2, z, chance() < 0.6 ? "sandbags[facing=west]" : "air");
            }
            set(bx + 4, 1, DOWN + 6, "crate");
            set(bx + 5, 1, DOWN + 6, "barrel");
            for (int y = 1; y <= 6; y++) {
                set(bx + 7, y, DOWN + 6, "streetlight_pole");
            }
            set(bx + 7, 7, DOWN + 6, Camp.lamp(rng, 0.9, "cp_flood_bar", "north"));
        }

        /**
         * What happened here: the cars left on the crest when the Guard shut it, the one that went
         * into the parapet, the paint on the piers and the intakes, the litter.
         */
        private void story() {
            for (int i = 0; i < 6; i++) {
                double x = -110 + i * 33 + rng.nextInt(9);
                if (x >= PIERS[0] - 4 && x <= PIERS[PIERS.length - 1] + 6 || Math.abs(x - LX) < 6) {
                    continue;
                }
                int lane = chance() < 0.5 ? MID - 2 : MID + 2;
                p.car(x, 1, lane + 0.5, chance() < 0.5 ? 90 : 270, 4.0, 5.4, rng.nextLong());
            }
            p.vehicle(-74.0, 1, UP + 2.5, 80, "pickup@wrecked", rng.nextLong());
            set(-74, 1, UP, "air");
            set(-73, 1, UP, "debris_chunk");
            p.vehicle(122.0, 1, MID - 1.5, 270, "jeep@wrecked", rng.nextLong());
            row("dm_spray_gates_", 4, 52, -3, DOWN + 1, "south", 0.0);
            row("dm_spray_drink_", 4, UNITS[1] - 2, 2, TOWER_Z - 4, "north", 0.0);
            row("dm_spray_lights_", 4, PX0 + 6, FLOOR + 4, PZ1 + 1, "south", 0.0);
            for (int x = FX0 + 4; x <= FX1 - 4; x++) {
                if (chance() < 0.05) {
                    int z = UP + 1 + rng.nextInt(DOWN - UP - 1);
                    if (empty(x, 1, z) && get(x, 0, z) != null && !get(x, 0, z).startsWith("dm_")) {
                        set(x, 1, z, any("debris_chunk", "leaf_litter", "leaf_litter"));
                    }
                }
            }
        }
    }
}
