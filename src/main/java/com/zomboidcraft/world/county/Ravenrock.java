package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Ravenrock State Penitentiary (Josiah, 2026-10-30: the northwest military zone's last site, and
 * reached by its ferry), on the cliff-girt island off Grayhaven's east shore, after refs/prison:
 * <ul>
 * <li>the wall of grey ashlar round the compound, its coping and the razor wire, the towers on the
 * corners and halfway along, the warning fence inside it;</li>
 * <li>the cellhouse along the north ({@link RavenrockCells}); the dining hall, the industries, the
 * infirmary, segregation and the gatehouse with its sally port in the west wall
 * ({@link RavenrockHalls}); the recreation yard south of them, its track and courts and the iron
 * pile;</li>
 * <li>outside the wall the ring road, the officers' row and the warden's house, the power house
 * and the water tower, the old prison graveyard on the east point, the checkpoint where the
 * causeway (blown, the plan's) comes in on the north;</li>
 * <li>and down the west cliff, in a cove cut for it, the landing: the quay, the ferry alongside
 * ({@link PrisonFerry}), the bell to call her across, the boat shed and the road up the cutting.</li>
 * </ul>
 * When it went the staff took the ferry and left the men inside. Some got out through the south
 * wall where a truck went into it; the rest held the infirmary, and then didn't.
 *
 * <p>Drawn whole in its own frame round the plan's spot ({@link FrameSite}), and the landing in a
 * tile of its own west of it, its basin left as water.
 */
final class Ravenrock extends FrameSite {
    static final int X0 = -400, X1 = 359, Z0 = -160, Z1 = 159, TILE_W = 240;
    /** The landing's tile, west of the frame: its basin QUAY wide off its west side. */
    static final int DX0 = -610, DX1 = -401, DZ0 = -32, DZ1 = 47, QUAY = 46;
    /** The compound's wall, its outer face. */
    static final int WX0 = -170, WX1 = 230, WZ0 = -130, WZ1 = 130;
    /** The road in from the landing, along z. */
    static final int RZ0 = 4, RZ1 = 11;
    /** The quay's face (its first column, x) and the bell on it, and where the ferry lands people (z). */
    static final int QX = DX0 + QUAY, BELL_Z = 10, LAND_Z = 12;
    /** The landing's floor above the water's top block. */
    static final int ABOVE_SEA = 4;

    Ravenrock(Place p, BlockSink out) {
        super(p, out, 0x7A5E1L);
    }

    static List<Place> places(Plan.Spot s, County county, Plan plan, long h) {
        List<Place> tiles = new ArrayList<>(FrameSite.tiles("ravenrock", s, county, plan, h, X0, Z0, X1, Z1, TILE_W));
        if (!tiles.isEmpty()) {
            Place t = tiles.get(0);
            tiles.add(new Place(PlaceType.LANDMARK, t.variant, s.x() + DX0, s.z() + DZ0, s.x() + DX1, s.z() + DZ1, t.level, h, 3, QUAY));
        }
        return tiles;
    }

    /** The bell on the quay, in the frame, for a frame at level. */
    static int[] bell(int level) {
        return new int[] {QX + 2, Terrain.WATER - level + ABOVE_SEA + 1, BELL_Z};
    }

    /** Where the ferry puts people ashore, in the frame. */
    static int[] landing(int level) {
        return new int[] {QX + 5, Terrain.WATER - level + ABOVE_SEA + 1, LAND_Z};
    }

    @Override
    void build() {
        List<int[]> g = gates;
        int level = p.level;
        copy(piece("prison", 0x9A15L, (q, r) -> new Yard(q, r, g, seed, level).build()));
    }

    /** The island, drawn whole in the frame. */
    static final class Yard extends Grounds {
        @Override
        String signs() {
            return "pn_sgn_";
        }

        private final List<int[]> gates;
        private final Noise noise;
        private final Camp camp;
        /** The water's top block, the landing's floor. */
        private final int sea, floor;

        Yard(Placer p, SplittableRandom rng, List<int[]> gates, long seed, int level) {
            super(p, rng, X1 - X0 + 1, Z1 - Z0 + 1, 0.5, 0.25, 0.15);
            this.gates = gates;
            this.noise = new Noise(seed);
            this.camp = new Camp(p, rng, 0.5);
            this.sea = Terrain.WATER - level;
            this.floor = sea + ABOVE_SEA;
        }

        void build() {
            ground();
            roads();
            wall();
            fence();
            new RavenrockCells(this).build();
            new RavenrockHalls(this).build();
            walks();
            recYard();
            towers();
            breach();
            checkpoint();
            village();
            works();
            graveyard();
            landing();
            trees();
        }

        private boolean inside(int x, int z) {
            return x > WX0 + 1 && x < WX1 - 1 && z > WZ0 + 1 && z < WZ1 - 1;
        }

        private String ashlar() {
            double r = chance();
            return r < 0.1 ? "ch_ashlar_moss" : r < 0.18 ? "ch_ashlar_soot" : r < 0.24 ? "ch_ashlar_crack" : r < 0.3 ? "ch_ashlar_b" : "ch_ashlar";
        }

        // ------------------------------------------------------------------ the ground and the roads

        /** Salt-burnt grass and rock outside, the yard's packed dirt and gravel inside the wall. */
        private void ground() {
            for (int x = X0; x <= X1; x++) {
                for (int z = Z0; z <= Z1; z++) {
                    double n = noise.fbm(x, z, 26, 3) * 0.5 + 0.5, r = chance();
                    String g;
                    if (inside(x, z)) {
                        // grass gone to seed, worn bare where they walked it, a hard edge of gravel nowhere
                        g = n < 0.22 ? "minecraft:coarse_dirt" : n < 0.26 && r < 0.5 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                    } else {
                        g = n < 0.18 ? (r < 0.6 ? "minecraft:stone" : "minecraft:andesite") : n < 0.3 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                    }
                    set(x, 0, z, g);
                    if (g.equals("minecraft:grass_block") && r < 0.1) {
                        set(x, 1, z, r < 0.03 ? "weeds_tall" : "grass_tuft");
                    }
                }
            }
        }

        private void road(int x0, int z0, int x1, int z1, boolean paved) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    double r = chance();
                    set(x, 0, z, paved ? (r < 0.08 ? "asphalt_crack" : r < 0.12 ? "asphalt_patch" : r < 0.15 ? "asphalt_moss" : "asphalt")
                            : r < 0.55 ? "minecraft:gravel" : r < 0.85 ? "mu_gravel" : "minecraft:coarse_dirt");
                    set(x, 1, z, "air");
                }
            }
        }

        /**
         * The road up from the landing to the forecourt in front of the gatehouse and its car park;
         * the ring road round the wall; the road down from the causeway's gate to it; the lane to
         * the officers' row.
         */
        private void roads() {
            road(X0, RZ0, -196, RZ1, true);
            for (int x = X0; x <= -196; x += 3) {
                set(x, 0, (RZ0 + RZ1) / 2, "road_line_yellow[axis=x]");
            }
            road(-234, -34, -196, 44, true);
            for (int z = -32; z <= 42; z += 3) {
                if (z < RZ0 - 1 || z > RZ1 + 1) {
                    set(-226, 0, z, "road_line_white[axis=z]");
                    set(-204, 0, z, "road_line_white[axis=z]");
                }
            }
            int a = WX0 - 16, b = WX1 + 16, c = WZ0 - 16, d = WZ1 + 16;
            road(a, c, b, c + 5, false);
            road(a, d - 5, b, d, false);
            road(a, c, a + 5, d, false);
            road(b - 5, c, b, d, false);
            for (int[] gt : gates) {
                if (gt[2] == 0) {
                    road(gt[0] - 4, Z0, gt[0] + 3, c, true);
                }
            }
            road(-330, -74, -326, RZ0, true);
            road(-390, -74, -250, -70, true);
            road(-250, -74, a, -70, false);
        }

        // ------------------------------------------------------------------ the wall and the fence

        /** The wall: two thick, eight high, the coping, razor wire along its inner edge. */
        private void wall() {
            for (int x = WX0; x <= WX1; x++) {
                for (int z = WZ0; z <= WZ1; z++) {
                    boolean outer = x == WX0 || x == WX1 || z == WZ0 || z == WZ1;
                    boolean inner = !outer && (x == WX0 + 1 || x == WX1 - 1 || z == WZ0 + 1 || z == WZ1 - 1);
                    if (!outer && !inner) {
                        continue;
                    }
                    set(x, 0, z, "concrete_dark");
                    for (int y = 1; y <= 8; y++) {
                        set(x, y, z, y == 1 && chance() < 0.4 ? "ch_ashlar_moss" : ashlar());
                    }
                    set(x, 9, z, "bd_coping");
                    if (inner && chance() > 0.08) {
                        set(x, 10, z, "st_razor");
                    }
                }
            }
            // its foot: a strip of gravel outside, the rain's green stain along the base
            for (int x = WX0 - 2; x <= WX1 + 2; x++) {
                for (int z : new int[] {WZ0 - 1, WZ0 - 2, WZ1 + 1, WZ1 + 2}) {
                    set(x, 0, z, "minecraft:gravel");
                }
            }
            for (int z = WZ0 - 2; z <= WZ1 + 2; z++) {
                for (int x : new int[] {WX0 - 1, WX0 - 2, WX1 + 1, WX1 + 2}) {
                    set(x, 0, z, "minecraft:gravel");
                }
            }
        }

        private void fenceRun(int x0, int z0, int x1, int z1) {
            boolean alongX = z0 == z1;
            int n = alongX ? x1 - x0 : z1 - z0;
            for (int i = 0; i <= n; i++) {
                int x = alongX ? x0 + i : x0, z = alongX ? z0 : z0 + i;
                boolean post = i % 4 == 0;
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, post ? "pn_post" : "chainlink[axis=" + (alongX ? "x" : "z") + "]");
                }
                set(x, 4, z, post ? "pn_post" : "st_razor");
                set(x, 0, z, "minecraft:gravel");
            }
        }

        /** The warning fence nine in from the wall: chain link, razor on top; the buildings break it. */
        private void fence() {
            int a = WX0 + 9, b = WX1 - 9, c = WZ0 + 9, d = WZ1 - 9;
            fenceRun(a, c, b, c);
            fenceRun(a, d, b, d);
            fenceRun(a, c, a, d);
            fenceRun(b, c, b, d);
            for (int x = a; x <= b; x++) {
                if (x % 20 == 0) {
                    set(x, 2, c + 1, "pn_sgn_warn_" + (x % 40 == 0 ? 0 : 1) + "[facing=south]");
                }
            }
        }

        // ------------------------------------------------------------------ the towers

        /** The towers on the wall: the corners, halfway along the north, south and east. */
        private void towers() {
            int[][] at = {{WX0 + 1, WZ0 + 1}, {WX1 - 1, WZ0 + 1}, {WX0 + 1, WZ1 - 1}, {WX1 - 1, WZ1 - 1},
                {30, WZ0 + 1}, {30, WZ1 - 1}, {WX1 - 1, 0}};
            for (int[] t : at) {
                tower(t[0], t[1]);
            }
        }

        /**
         * A tower on the wall centred on (x, z): an ashlar shaft five square up past the wall, the
         * ladder inside it; the platform round the top with its rail, the glazed cab on it under a
         * roof reaching over, the searchlight; inside the cab the stool, the rack, the radio.
         */
        private void tower(int x, int z) {
            int deck = 12;
            for (int i = -2; i <= 2; i++) {
                for (int k = -2; k <= 2; k++) {
                    boolean edge = Math.abs(i) == 2 || Math.abs(k) == 2;
                    for (int y = 1; y < deck; y++) {
                        set(x + i, y, z + k, edge ? (y == deck - 1 ? "bd_coping" : ashlar()) : "air");
                    }
                    set(x + i, 0, z + k, "concrete_dark");
                    set(x + i, deck, z + k, "concrete_panel");
                    if (edge) {
                        String f = k == -2 ? "north" : k == 2 ? "south" : i == -2 ? "west" : "east";
                        set(x + i, deck + 1, z + k, "pn_rail[facing=" + f + "]");
                    }
                }
            }
            for (int y = 1; y <= deck; y++) {
                set(x, y, z + 1, "bunk_ladder[facing=north]");
            }
            // the cab: the booth's ring of glazed panels three by three
            String[] faces = {"north", "east", "south", "west"};
            int[][] ring = {{-1, -1, 0, 1}, {0, -1, 0, 0}, {1, -1, 1, 1}, {1, 0, 1, 0}, {1, 1, 2, 1}, {0, 1, 2, 0}, {-1, 1, 3, 1}, {-1, 0, 3, 0}};
            for (int[] c : ring) {
                int cx = x + c[0], cz = z + c[1];
                String f = "[facing=" + faces[c[2]] + "]", k = c[3] == 1 ? "_corner" : "";
                set(cx, deck + 3, cz, "cp_gb_head" + k + f);
                if (c[0] == 0 && c[1] == 1) {
                    set(cx, deck + 1, cz, "air");
                    set(cx, deck + 2, cz, "air");
                    continue;  // the hatch the ladder comes up through
                }
                set(cx, deck + 1, cz, "cp_gb_wall" + k + f);
                set(cx, deck + 2, cz, "cp_gb_win" + k + (chance() < 0.25 + b.damage * 0.3 ? "_broken" : "") + f);
            }
            set(x, deck + 3, z, "cp_gb_roof[facing=north]");
            set(x, deck + 1, z, chance() < 0.5 ? "un_stool" : "sh_gun_rack[facing=" + dir() + "]");
            if (chance() < 0.5) {
                set(x, deck + 2, z, "air");
            }
            String look = z < 0 ? "north" : z > 0 ? "south" : "east";
            if (x == WX0 + 1 && z == 0) {
                look = "west";
            }
            set(x, deck + 4, z, (power && chance() < 0.3 ? "fit_searchlight_on" : "fit_searchlight") + "[facing=" + look + "]");
            if (chance() < 0.4) {
                set(x - 1, deck + 1, z - 2, "bunk_crate_ammo[facing=north]");
            }
            set(x, 1, z + 2, "air");
            set(x, 2, z + 2, "air");
            Doors.abandoned(p, rng, x, 1, z + 2, "bunk_door_steel", "south", "left");
        }

        // ------------------------------------------------------------------ the walks and the yard

        /** Concrete walks: the avenue across the compound and the spurs to each building's door. */
        private void walk(int x0, int z0, int x1, int z1) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    if (empty(x, 1, z)) {
                        set(x, 0, z, chance() < 0.06 ? "sidewalk_cracked" : "sidewalk");
                    }
                }
            }
        }

        private void walks() {
            walk(-157, -21, 200, -17);
            walk(-157, -8, -152, -21);
            walk(-160, 18, -152, 27);
            walk(-128, -29, -124, -21);
            walk(-160, -46, -151, -44);
            walk(-99, -46, -90, -44);
            walk(-90, -46, -88, -21);
            walk(26, -49, 30, -47);
            walk(28, -49, 30, -21);
            walk(-2, -39, 2, -21);
            walk(107, -103, 109, -101);
            walk(100, -103, 109, -101);
            walk(100, -103, 104, -21);
            walk(116, -49, 119, -47);
            walk(116, -49, 118, -21);
            walk(28, -16, 32, 13);
        }

        /**
         * The recreation yard, x -60 .. 120, z 14 .. 118, in its own fence: the dirt track round
         * the field, the courts and the handball wall in the west end, the iron pile, the bleachers;
         * the tables someone dragged out to make a wall when the gates fell.
         */
        private void recYard() {
            int x0 = -60, x1 = 120, z0 = 14, z1 = 118;
            fenceRun(x0, z0, x1, z0);
            fenceRun(x0, z1, x1, z1);
            fenceRun(x0, z0, x0, z1);
            fenceRun(x1, z0, x1, z1);
            for (int x = 28; x <= 32; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z0, "air");
                }
            }
            for (int z = z0; z <= z0 + 4; z++) {
                set(27, 1, z, "pn_gate[axis=z]");
                set(27, 2, z, "pn_gate[axis=z]");
                set(27, 3, z, "pn_gate_top[axis=z]");
            }
            // the track: four wide round the field, its corners cut
            int tx0 = -24, tx1 = 112, tz0 = 22, tz1 = 110;
            for (int x = tx0; x <= tx1; x++) {
                for (int z = tz0; z <= tz1; z++) {
                    int dx = Math.min(x - tx0, tx1 - x), dz = Math.min(z - tz0, tz1 - z);
                    boolean corner = dx + dz < 6;
                    if (corner) {
                        continue;
                    }
                    boolean track = dx < 4 || dz < 4 || dx + dz < 10;
                    set(x, 0, z, track ? (chance() < 0.7 ? "minecraft:coarse_dirt" : "mu_gravel") : chance() < 0.85 ? "minecraft:grass_block" : "minecraft:coarse_dirt");
                    set(x, 1, z, !track && chance() < 0.05 ? "grass_tuft" : "air");
                }
            }
            // the courts: asphalt, the hoops at the ends
            for (int x = x0 + 3; x <= x0 + 30; x++) {
                for (int z = z0 + 4; z <= z0 + 34; z++) {
                    boolean line = z == z0 + 4 || z == z0 + 34 || x == x0 + 3 || x == x0 + 30 || z == z0 + 19;
                    set(x, 0, z, line ? "road_line_white[axis=" + (x == x0 + 3 || x == x0 + 30 ? "z" : "x") + "]" : chance() < 0.08 ? "asphalt_crack" : "asphalt");
                    set(x, 1, z, "air");
                }
            }
            for (int x : new int[] {x0 + 16}) {
                set(x, 1, z0 + 5, "sc_hoop_post");
                set(x, 2, z0 + 5, "sc_hoop_post");
                set(x, 3, z0 + 5, "sc_hoop_neck[facing=south]");
                set(x, 3, z0 + 6, "sc_hoop_board[facing=south]");
                set(x, 1, z0 + 33, "sc_hoop_post");
                set(x, 2, z0 + 33, "sc_hoop_post");
                set(x, 3, z0 + 33, "sc_hoop_neck[facing=north]");
                set(x, 3, z0 + 32, "sc_hoop_board[facing=north]");
            }
            // the handball wall
            for (int x = x0 + 4; x <= x0 + 14; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z0 + 44, y == 4 ? "bd_coping" : "pn_ext");
                }
            }
            for (int c = 0; c < 4; c++) {
                set(x0 + 6 + c, 2, z0 + 43, "pn_spray_open_" + (3 - c) + "[facing=north]");
            }
            // the iron pile
            for (int x = x0 + 4; x <= x0 + 26; x += 4) {
                for (int z = z0 + 52; z <= z0 + 76; z += 6) {
                    set(x, 0, z, "ar_rubber");
                    set(x + 1, 0, z, "ar_rubber");
                    double r = chance();
                    set(x, 1, z, r < 0.5 ? "pn_bench[facing=" + any("north", "south") + "]" : r < 0.75 ? "pn_dumbbells[facing=east]" : "air");
                }
            }
            for (int z = z0 + 52; z <= z0 + 70; z += 6) {
                set(x0 + 30, 1, z, "pn_pullup[axis=x]");
                set(x0 + 30, 2, z, "pn_pullup_bar[axis=x]");
            }
            // the bleachers along the track's north straight, facing the field
            for (int x = 10; x <= 90; x += 1) {
                if (x % 20 > 14) {
                    continue;
                }
                set(x, 1, tz0 - 3, "sc_bleacher[facing=south]");
                set(x, 1, tz0 - 4, "sc_bleacher_frame[facing=south]");
                set(x, 2, tz0 - 4, "sc_bleacher[facing=south]");
            }
            // the barricade: the bleachers' planks, the carts, sandbags, across the yard's west end
            for (int z = z0 + 40; z <= z1 - 4; z++) {
                if (chance() < 0.75) {
                    set(-30, 1, z, any("sandbags[facing=east]", "pn_cart[facing=east]", "jersey_barrier[facing=east]", "sandbags[facing=east]"));
                }
            }
            for (int i = 0; i < 40; i++) {
                int x = -28 + rng.nextInt(130), z = z0 + 4 + rng.nextInt(z1 - z0 - 8);
                if (empty(x, 1, z)) {
                    set(x, 1, z, any("blood_splat", "paper", "home_blood_pool", "trash_bag[facing=north]"));
                }
            }
            sign("warn", 40, 2, z0 + 1, "south");
        }

        // ------------------------------------------------------------------ the breach

        /**
         * The south wall, x 150 .. 158: a truck put through it, the wall down in a heap either side,
         * the warning fence torn behind; inside, the sandbags they stopped it with, too late.
         */
        private void breach() {
            for (int x = 150; x <= 158; x++) {
                for (int z = WZ1 - 1; z <= WZ1; z++) {
                    int keep = Math.abs(x - 154) >= 4 ? 3 + rng.nextInt(3) : 0;
                    for (int y = keep + 1; y <= 10; y++) {
                        set(x, y, z, "air");
                    }
                }
                for (int z = WZ1 - 6; z <= WZ1 + 5; z++) {
                    if (chance() < 0.45) {
                        set(x, 1, z, chance() < 0.6 ? "rubble" : "rubble_pile");
                    }
                }
            }
            for (int x = 151; x <= 157; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, WZ1 - 9, "air");
                }
                set(x, 1, WZ1 - 9, chance() < 0.5 ? "debris_chunk" : "air");
            }
            p.vehicle(154.5, 1, WZ1 - 1.0, 180, "box_truck@wrecked", rng.nextLong());
            for (int x = 146; x <= 162; x++) {
                if (chance() < 0.7) {
                    set(x, 1, WZ1 - 14, "sandbags[facing=south]");
                }
            }
            for (int i = 0; i < 18; i++) {
                int x = 140 + rng.nextInt(30), z = WZ1 - 20 + rng.nextInt(18);
                if (empty(x, 1, z)) {
                    set(x, 1, z, any("blood_splat", "home_blood_pool", "home_blood_drag[facing=north]", "glass_shards"));
                }
            }
        }

        // ------------------------------------------------------------------ outside the wall

        /** Where the causeway comes in: the booth, the barrier, the blocks, the sign. */
        private void checkpoint() {
            for (int[] gt : gates) {
                if (gt[2] != 0) {
                    continue;
                }
                int x = gt[0], z = Z0 + 6;
                camp.booth(x + 6, z);
                camp.boom(x - 4, z + 4, 7, false);
                camp.jersey(x - 4, z + 1, x - 4, z + 1);
                camp.jersey(x + 3, z + 1, x + 3, z + 1);
                for (int k = -1; k <= 1; k += 2) {
                    set(x + k * 7, 1, z - 3, "jersey_barrier[facing=north]");
                }
                set(x + 5, 3, z, "pn_sgn_staff_0[facing=north]");
                camp.floodMast(x - 7, z + 2, 6);
            }
        }

        /**
         * The officers' row: five houses north of the lane, their fronts to it; the warden's house on
         * the point south of the road in, its lawn to the sea; the staff car park in the forecourt.
         */
        private void village() {
            for (int i = 0; i < 5; i++) {
                int lx = -388 + i * 27;
                House.Style style = i == 2 ? House.Style.BUNGALOW : House.Style.COLONIAL;
                Placer q = p.turned(lx + 20, 0, -78, 2);
                House h = new House(q.shifted(3, 0, 4), rng, style, House.condition(rng), House.palette(style, rng), 14, 11,
                        style == House.Style.COLONIAL ? 2 : 1);
                h.build();
                for (int z = -80; z >= -82; z--) {
                    set(lx + 20 - 3 - h.doorX(), 0, z, "sidewalk");
                }
            }
            Placer w = p.turned(-386, 0, 30, 0);
            House warden = new House(w, rng, House.Style.MANSION, House.Condition.LOOTED, House.palette(House.Style.MANSION, rng), 22, 16, 2);
            warden.build();
            for (int z = RZ1 + 1; z < 30; z++) {
                set(-386 + warden.doorX(), 0, z, "sidewalk");
            }
            set(-388, 1, 28, "cv_flagpole_base");
            for (int y = 2; y <= 8; y++) {
                set(-388, y, 28, "cv_flagpole");
            }
            set(-388, 9, 28, "cv_flag_state_top[facing=north]");
            // the car park: the staff's cars left where they ran for the boat, some burnt
            for (int x = -232; x <= -206; x += 3) {
                for (int z : new int[] {-30, 38}) {
                    if (chance() < 0.55) {
                        p.car(x + 0.5, 1, z + 0.5, z < 0 ? 0 : 180, 4.0, 5.4, rng.nextLong());
                    }
                }
            }
            p.vehicle(-214.0, 1, -10.5, 90, "police_suv@wrecked", rng.nextLong());
            p.vehicle(-222.0, 1, 24.5, 270, "swat_van", rng.nextLong());
        }

        /**
         * The works north of the road: the power house, its stack and the fuel tanks, the water
         * tower on its steel legs, the radio mast.
         */
        private void works() {
            int x0 = -300, z0 = -46, x1 = -272, z1 = -30, top = 8;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, 0, z, "floor_concrete");
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    for (int y = 1; y <= top; y++) {
                        set(x, y, z, edge ? (y == top ? "bd_coping" : chance() < 0.1 ? "brick_red_soot" : "brick_red") : y == top - 1 ? "un_roof" : "air");
                    }
                }
            }
            for (int x = x0 + 3; x <= x1 - 3; x += 4) {
                for (int y = 3; y <= 5; y++) {
                    set(x, y, z1, chance() < 0.3 ? "pn_window_broken[facing=south]" : "pn_window[facing=south]");
                }
                set(x, 1, z0 + 4, "pw_drum[facing=south]");
            }
            for (int x = x0 + 4; x <= x1 - 4; x += 6) {
                set(x, 1, (z0 + z1) / 2, "gs_compressor_hi[facing=east]");
                set(x + 1, 1, (z0 + z1) / 2, "gs_compressor_lo[facing=east]");
            }
            set(x0 + 1, 1, z1, "air");
            set(x0 + 1, 2, z1, "air");
            Doors.abandoned(p, rng, x0 + 2, 1, z1, "garage_door", "south", "left");
            for (int y = 1; y <= top + 10; y++) {
                set(x1 - 2, y, z0 + 2, y > top + 8 ? "brick_dark_soot" : "brick_dark");
            }
            camp.fuelTanks(x1 + 6, z0, 2);
            waterTower(-262, -60);
            camp.mast(-312, -50, 18);
            set(-312, 0, -50, "concrete_dark");
        }

        private void waterTower(int x, int z) {
            int deck = 16, s = 6;
            String[] turn = {"north", "east", "south", "west"};
            int[][] corner = {{0, 0}, {s, 0}, {s, s}, {0, s}};
            for (int c = 0; c < 4; c++) {
                for (int y = 1; y < deck; y++) {
                    set(x + corner[c][0], y, z + corner[c][1], "tv_leg_w");
                }
                set(x + corner[c][0], 0, z + corner[c][1], "concrete_dark");
            }
            for (int c = 0; c < 4; c++) {
                for (int u = 1; u < s; u++) {
                    int[] at = switch (c) {
                        case 0 -> new int[] {x + u, z};
                        case 1 -> new int[] {x + s, z + u};
                        case 2 -> new int[] {x + s - u, z + s};
                        default -> new int[] {x, z + s - u};
                    };
                    for (int y = 4; y < deck; y += 5) {
                        set(at[0], y, at[1], "tv_girt_w[facing=" + turn[c] + "]");
                    }
                }
            }
            for (int i = -1; i <= s + 1; i++) {
                for (int k = -1; k <= s + 1; k++) {
                    set(x + i, deck, z + k, "tv_grate");
                    if (i == -1 || k == -1 || i == s + 1 || k == s + 1) {
                        set(x + i, deck + 1, z + k, "railing[facing=" + (k == -1 ? "north" : k == s + 1 ? "south" : i == -1 ? "west" : "east") + "]");
                    }
                }
            }
            double cx = x + s / 2.0 + 0.5, cz = z + s / 2.0 + 0.5;
            Round.wall(p, cx, cz, 3.6, deck + 1, deck + 6, "wtw", 0);
            Round.roof(p, cx, cz, 3.6, Round.cone(3.6), deck + 7, "wtw");
            for (int y = 1; y <= deck; y++) {
                set(x + s / 2, y, z + s + 1, "ladder[facing=south]");
            }
            set(x + s / 2, deck + 1, z + s + 1, "air");
        }

        /**
         * The prison's graveyard on the east point, x 268 .. 340, z -44 .. 44: an iron fence, the
         * numbered stones of the men nobody claimed in their rows, the new graves dug open.
         */
        private void graveyard() {
            int x0 = 268, x1 = 340, z0 = -44, z1 = 44;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (edge && !(x == x0 && Math.abs(z) <= 1)) {
                        set(x, 1, z, "cv_iron_fence[axis=" + (z == z0 || z == z1 ? "x" : "z") + "]");
                    }
                }
            }
            for (int x = x0 + 4; x <= x1 - 4; x += 3) {
                for (int z = z0 + 4; z <= z1 - 4; z += 4) {
                    if (Math.abs(z) <= 2) {
                        continue;
                    }
                    double r = chance();
                    if (x > x1 - 16 && r < 0.4) {
                        set(x, 0, z, "ch_grave_open[facing=west]");
                        set(x, 0, z + 1, "ch_grave_open[facing=west]");
                        set(x, 1, z - 1, "ch_grave_mound");
                    } else {
                        int k = rng.nextInt(6);
                        set(x, 1, z, r < 0.2 ? "ch_grave_cross[facing=west]" : "ch_headstone_" + k + (r < 0.4 ? "_sunk" : "") + "[facing=west]");
                        set(x, 0, z + 1, chance() < 0.3 ? "ch_grave_mound" : "minecraft:grass_block");
                    }
                }
            }
            for (int x = x0; x <= x1; x++) {
                set(x, 0, 0, "mu_gravel");
                set(x, 0, 1, "mu_gravel");
                set(x, 0, -1, "mu_gravel");
            }
            road(WX1 + 16, -1, x0, 1, false);
        }

        // ------------------------------------------------------------------ the landing

        /**
         * The landing in its cove: the quay along the basin, granite to its cap, fenders and bitts;
         * the ferry alongside, the gangway to her door, the bell; the guard booth, the boat shed and
         * the patrol boat; the road up the cutting to the plateau, ashlar walls either side.
         */
        private void landing() {
            int f = floor;
            int qx = QX, top = -406;
            // the cove: dug down to the floor from the quay to the foot of the cutting
            for (int x = qx; x <= -520; x++) {
                for (int z = -28; z <= 43; z++) {
                    for (int y = f + 1; y <= 0; y++) {
                        set(x, y, z, "air");
                    }
                    set(x, f, z, x <= qx + 7 ? (chance() < 0.08 ? "floor_concrete_crack" : "floor_concrete") : chance() < 0.6 ? "mu_gravel" : "minecraft:gravel");
                }
            }
            // the cove's walls, and the cutting's either side of the road up
            for (int x = qx; x <= -520; x++) {
                for (int z : new int[] {-29, 44}) {
                    for (int y = f + 1; y <= 0; y++) {
                        set(x, y, z, ashlar());
                    }
                    set(x, 1, z, "railing[facing=" + (z < 0 ? "south" : "north") + "]");
                }
            }
            for (int z = -29; z <= 44; z++) {
                if (z >= 0 && z <= 15) {
                    continue;
                }
                for (int y = f + 1; y <= 0; y++) {
                    set(-519, y, z, ashlar());
                }
                set(-519, 1, z, "railing[facing=west]");
            }
            for (int x = -519; x <= top; x++) {
                int h = f + (int) Math.round((x + 519) * (double) -f / (top + 519));
                for (int z = 0; z <= 15; z++) {
                    boolean wallZ = z <= 1 || z >= 14;
                    for (int y = h + 1; y <= 0; y++) {
                        set(x, y, z, wallZ ? ashlar() : "air");
                    }
                    if (!wallZ) {
                        boolean lane = z >= RZ0 && z <= RZ1;
                        set(x, h, z, lane ? (z == (RZ0 + RZ1) / 2 && x % 3 == 0 ? "road_line_yellow[axis=x]" : chance() < 0.1 ? "asphalt_crack" : "asphalt")
                                : "sidewalk");
                    }
                    if (wallZ && (z == 0 || z == 15)) {
                        set(x, 1, z, "railing[facing=" + (z == 0 ? "south" : "north") + "]");
                    }
                }
                if ((x + 519) % 14 == 7) {
                    set(x, h + 1, 2, "streetlight_pole");
                    set(x, h + 2, 2, "streetlight_pole");
                    set(x, h + 3, 2, "streetlight_pole");
                    boolean roll = power && chance() < 0.4;  // the old dead-bulb roll, kept so everything after it lands where it did: every lamp works now
                    set(x, h + 4, 2, "streetlight_head[facing=south]");
                }
            }
            for (int x = top + 1; x <= DX1; x++) {
                for (int z = RZ0; z <= RZ1; z++) {
                    set(x, 0, z, chance() < 0.1 ? "asphalt_crack" : "asphalt");
                    set(x, 1, z, "air");
                }
            }
            // the quay: granite from the basin's bed to its cap, the fenders, the bitts, the ladders
            for (int z = -28; z <= 43; z++) {
                for (int y = sea - 9; y < f; y++) {
                    set(qx, y, z, y <= sea + 1 ? "wf_wall_slime" : "wf_wall");
                }
                set(qx, f, z, "wf_cap");
                if (z % 4 == 0) {
                    for (int y = sea; y < f; y++) {
                        set(qx - 1, y, z, "wf_fender[facing=west]");
                    }
                }
                if (z % 8 == 2) {
                    set(qx, f + 1, z, "vessel_bollard");
                }
                if (z % 24 == 5) {
                    for (int y = sea - 1; y <= f; y++) {
                        set(qx - 1, y, z, "bunk_ladder[facing=west]");
                    }
                }
            }
            // the ferry alongside, her bow north, her starboard to the quay
            PrisonFerry ferry = new PrisonFerry(this, p.turned(qx - 8, 0, 28, 3), sea);
            ferry.build(chance() < 0.5 ? "RAVENROCK II" : "WARDEN HALE");
            int dz = 28 - PrisonFerry.DOOR;
            for (int z = dz - 1; z <= dz; z++) {
                set(qx - 1, f - 1, z, "wf_gangway[facing=east]");
                set(qx - 2, f - 2, z, "wf_gangway[facing=east]");
                set(qx - 3, f - 2, z, "wf_gangway[facing=east]");
                set(qx - 1, f, z, "air");
                set(qx - 2, f - 1, z, "air");
            }
            int[] bl = bell(Terrain.WATER - sea);
            set(bl[0], bl[1], bl[2], "zomboidcraft:ferry_bell[facing=east]");
            set(bl[0] + 1, f + 1, bl[2] - 2, "pn_post");
            set(bl[0] + 1, f + 2, bl[2] - 2, "pn_post");
            // the guard booth, the barrier across the road up, the signs
            Camp at = new Camp(p.shifted(0, f, 0), rng, 0.5);
            at.booth(qx + 12, -6);
            at.boom(-524, RZ0 - 1, 9, false);
            for (int c = 0; c < 2; c++) {
                int[] s1 = MallSigns.at(-520, 18, 2, c, "west"), s2 = MallSigns.at(-520, -14, 2, c, "west"), s3 = MallSigns.at(-520, -6, 2, c, "west");
                set(s1[0], f + 3, s1[1], "pn_sgn_dock_" + c + "[facing=west]");
                set(s2[0], f + 3, s2[1], "pn_sgn_ferry_" + c + "[facing=west]");
                set(s3[0], f + 3, s3[1], "pn_sgn_bell_" + c + "[facing=west]");
            }
            // the boat shed at the south end of the quay, the patrol boat at the steps below it
            int sx0 = qx + 2, sx1 = qx + 22, sz0 = 31, sz1 = 43;
            for (int x = sx0; x <= sx1; x++) {
                for (int z = sz0; z <= sz1; z++) {
                    boolean edge = x == sx1 || z == sz0 || z == sz1 || x == sx0;
                    for (int y = f + 1; y <= f + 6; y++) {
                        set(x, y, z, y == f + 6 ? "corrugated_grey" : edge ? (chance() < 0.15 ? "corrugated_rust" : "corrugated_grey") : "air");
                    }
                }
            }
            for (int z = sz0 + 2; z <= sz1 - 2; z++) {
                for (int y = f + 1; y <= f + 4; y++) {
                    set(sx0, y, z, "air");
                }
            }
            set(sx1 - 2, f + 1, sz0 + 2, "home_workbench[facing=west]");
            set(sx1 - 2, f + 1, sz0 + 3, "store_shelf_hardware[facing=west]");
            set(sx1 - 2, f + 1, sz1 - 2, "wf_outboard[facing=west]");
            set(sx1 - 4, f + 1, sz1 - 2, "pw_drum[facing=north]");
            set(sx1 - 5, f + 1, sz1 - 2, "pw_drum[facing=north]");
            p.vehicle(qx - 4.0, sea, 38.5, 180, "=boat_console@usable", rng.nextLong());
            p.vehicle(qx - 9.0, sea - 2, 41.5, 160, "=boat_skiff@wrecked", rng.nextLong());
            // what was dropped running for her
            for (int i = 0; i < 20; i++) {
                int x = qx + 1 + rng.nextInt(30), z = -26 + rng.nextInt(56);
                if (empty(x, f + 1, z)) {
                    set(x, f + 1, z, any("wf_bags_a", "wf_bags_b", "paper", "blood_splat", "trash_bag[facing=north]", "wf_bags_c"));
                }
            }
            p.car(qx + 20.5, f + 1, 8.5, 90, 4.0, 5.2, rng.nextLong());
            p.vehicle(qx + 26.0, f + 1, -18.5, 0, "police_sedan@wrecked", rng.nextLong());
        }

        // ------------------------------------------------------------------ the trees

        private void trees() {
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
                    String g = p.get(x, 0, z);
                    return !Kit.soil(g) || g == null;
                }
            };
            for (int x = X0 + 3; x <= X1 - 3; x += 7) {
                for (int z = Z0 + 3; z <= Z1 - 3; z += 7) {
                    int tx = x + rng.nextInt(5), tz = z + rng.nextInt(5);
                    if (tx > WX0 - 22 && tx < WX1 + 22 && tz > WZ0 - 22 && tz < WZ1 + 22) {
                        continue;
                    }
                    double n = noise.fbm(tx, tz, 50, 2) * 0.5 + 0.5;
                    if (chance() > 0.12 + n * 0.25 || !clear(tx, tz)) {
                        continue;
                    }
                    Kit.plant(spot, chance() < 0.25 ? Trees.Kind.DEAD : Trees.Kind.PINE, tx, tz, false, rng);
                }
            }
        }

        private boolean clear(int x, int z) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    String g = p.get(x + dx, 0, z + dz);
                    if (g == null || !Kit.soil(g)) {
                        return false;
                    }
                    for (int y = 1; y <= 3; y++) {
                        String s = p.get(x + dx, y, z + dz);
                        if (s != null && !s.equals("air") && !s.startsWith("grass") && !s.startsWith("weeds")) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}
