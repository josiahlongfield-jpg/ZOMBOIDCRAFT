package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Stairwell;
import java.util.SplittableRandom;

/**
 * Where {@link Mall}'s shoppers left their cars: the surface lot along the street front either
 * side of the cinema, the drop-off lanes before the bars, and the two parking decks out behind
 * the food court, three levels each (the ground, a deck and the open roof), with their ramps
 * up the end aisles and a stair tower at each front corner. Drawn in the mall's frame.
 */
final class MallParking {
    /** The decks' west corners and their size: 97 long, 52 deep, a level every four blocks. */
    static final int[] DECKS = {14, 182};
    static final int DZ = 150, DW = 97, DD = 52, H = 4, LEVELS = 3;
    /** Where the stair towers stand along each deck's front (their west walls), outside it to the north. */
    private static final int[] TOWERS = {10, 82};
    private static final String[] BAND = {"park_band_blue", "park_band_green", "park_band_orange"};
    /** The deck's rows from its north parapet: {first z, head side (0 north, 2 south)}; five deep, aisles of six between. */
    private static final int[][] ROWS = {{1, 0}, {12, 2}, {18, 0}, {29, 2}, {35, 0}, {46, 2}};
    /** The lines of columns across the deck (between the back-to-back rows), and its end aisles. */
    private static final int[] COLUMN_LINES = {0, 17, 34, 51};
    private static final int AISLE_W = 8, BAYS_X0 = AISLE_W + 1, BAYS_X1 = DW - AISLE_W - 2;

    private final Placer m;
    private final SplittableRandom rng;
    private final boolean power;
    private final int w, d;

    MallParking(Placer m, SplittableRandom rng, int w, int d, boolean power) {
        this.m = m;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.power = power;
    }

    private double chance() {
        return rng.nextDouble();
    }

    void build() {
        front();
        back();
        for (int x0 : DECKS) {
            new Deck(m.turned(x0, 0, DZ, 0)).build();
        }
    }

    // ------------------------------------------------------------------ the surface

    /**
     * The street front: two rows of stalls each side of the cinema's plaza, the sidewalk in front
     * of the anchors' doors, the drop-off lane and its kerb before the bars, the lot's lights.
     */
    private void front() {
        int cx0 = MallWings.NX0 - 9, cx1 = MallWings.NX1 + 9;
        for (int[] run : new int[][] {{6, cx0 - 2}, {cx1 + 2, w - 7}}) {
            stalls(run[0], run[1], 4, 0);
            stalls(run[0], run[1], 15, 2);
            for (int x = run[0]; x <= run[1]; x++) {
                for (int z = 20; z <= 23; z++) {
                    walk(x, z);
                }
            }
            lights(run[0] + 10, run[1] - 4, 11);
        }
        // the pylon by the street, between the cinema and the west anchor
        MallSigns.pylon(m, rng, 60, 1, power && chance() < 0.7);
        // the cinema's plaza out to the street, planters along it
        for (int x = cx0 - 1; x <= cx1 + 1; x++) {
            for (int z = 0; z < MallWings.NZ0; z++) {
                walk(x, z);
            }
        }
        // before the bars: the drop-off lane, its kerb, the sidewalk with its benches and bins
        for (int[] run : new int[][] {{Mall.AW + 1, MallWings.NX0 - 1}, {MallWings.NX1 + 1, Mall.AE - 1}}) {
            for (int x = run[0]; x <= run[1]; x++) {
                for (int z = 31; z < Mall.Z0; z++) {
                    walk(x, z);
                }
                m.set(x, 0, 30, "curb");
                if (x % 9 == 4) {
                    m.set(x, 1, 33, chance() < 0.7 ? "rw_bench[facing=north]" : "ext_trash_can");
                }
            }
        }
    }

    /**
     * Behind the mall: the sidewalk round the food court and along the bars' and anchors' backs,
     * the service drive, a row of stalls along each deck's front, the bus shelter by the food court.
     */
    private void back() {
        for (int x = 4; x < w - 4; x++) {
            for (int z = Mall.Z1 + 1; z <= Mall.Z1 + 4; z++) {
                if (x > Mall.AW && x < Mall.AE) {
                    walk(x, z);
                }
            }
            for (int z = Mall.AZ1 + 1; z <= Mall.AZ1 + 3; z++) {
                if (x >= Mall.X0 && x <= Mall.AW || x >= Mall.AE && x <= Mall.X1) {
                    walk(x, z);
                }
            }
        }
        for (int x = MallWings.FX0 - 3; x <= MallWings.FX1 + 3; x++) {
            for (int z = Mall.Z1 + 1; z <= MallWings.FZ1 + 6; z++) {
                walk(x, z);
            }
        }
        // the bus shelter on the plaza out of the food court's doors
        int bx = (MallWings.FX0 + MallWings.FX1) / 2 - 3, bz = MallWings.FZ1 + 5;
        for (int x = bx; x <= bx + 6; x++) {
            m.set(x, 4, bz, "stone_trim");
            m.set(x, 1, bz, x % 3 == 0 ? "rw_bench[facing=north]" : "air");
        }
        for (int x : new int[] {bx, bx + 6}) {
            for (int y = 1; y <= 3; y++) {
                m.set(x, y, bz, "tx_column");
            }
        }
        // a row of stalls before each deck, the drive past its stair towers
        for (int x0 : DECKS) {
            stalls(x0 + 2, x0 + DW - 3, DZ - 20, 2);
            lights(x0 + 8, x0 + DW - 8, DZ - 22);
        }
    }

    private void walk(int x, int z) {
        m.set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
    }

    /**
     * A row of stalls three wide from x0 to x1, five deep from z0, their heads to side head (0
     * north, 2 south): the lines, a car in one stall in sixteen.
     */
    private void stalls(int x0, int x1, int z0, int head) {
        for (int x = x0; x <= x1; x++) {
            boolean line = (x - x0) % 3 == 0;
            for (int z = z0; z < z0 + 5; z++) {
                if (line && chance() < 0.85) {
                    m.set(x, 0, z, "road_line_white[axis=z]");
                }
            }
            if (line && x + 3 <= x1 && chance() < 0.06) {
                m.vehicle(x + 2, 1, z0 + 2.5, (head == 0 ? 180 : 0) + (float) (chance() * 10 - 5),
                        "sedan,suv,pickup,hatchback,minivan,wagon,sedan,van", rng.nextLong());
            }
        }
    }

    /** The lot's light poles down an aisle at z, every two dozen blocks, a few still lit. */
    private void lights(int x0, int x1, int z) {
        for (int x = x0; x <= x1; x += 24) {
            m.set(x, 1, z, "st_lot_base");
            for (int y = 2; y <= 10; y++) {
                m.set(x, y, z, "st_lot_pole");
            }
            m.set(x, 11, z, (power && chance() < 0.35 ? "st_lot_head_on" : "st_lot_head") + "[facing=" + (chance() < 0.5 ? "north" : "east") + "]");
        }
    }

    // ------------------------------------------------------------------ the decks

    /**
     * One deck in its own frame: x along it from the west end (0 to DW - 1), z from the front
     * parapet facing the mall (0) back to the rear one (DD - 1), level k's slab at H * k.
     *
     * <p>Cars come in at the ground anywhere round its open sides; the ramp up to the deck climbs
     * south up the west end aisle's outer lanes, the ramp on to the roof north up the east's, each a
     * level over eight blocks in half-block steps, a kerb wall between it and the lane past it.
     */
    private final class Deck {
        private final Placer p;
        private final Noise grime = new Noise(rng.nextLong());

        Deck(Placer p) {
            this.p = p;
        }

        void build() {
            for (int k = 0; k < LEVELS; k++) {
                level(k);
            }
            ramp(1, 26, 1, 0);
            ramp(DW - 4, DD - 26, -1, 1);
            for (int k = 0; k < LEVELS; k++) {
                int y = H * k;
                for (int[] r : ROWS) {
                    bays(y, r[0], r[1], k);
                }
                arrows(y);
                if (k < LEVELS - 1) {
                    tubes(y);
                } else {
                    poles(y);
                }
            }
            for (int tx : TOWERS) {
                tower(tx);
            }
        }

        /** The level's slab (the lot's own paving at the ground), its columns, its parapet in the level's colour. */
        private void level(int k) {
            int y = H * k;
            for (int x = 0; x < DW; x++) {
                for (int z = 0; z < DD; z++) {
                    p.set(x, y, z, floor(x, z, k));
                    boolean edge = x == 0 || z == 0 || x == DW - 1 || z == DD - 1;
                    if (k > 0 && edge) {
                        p.set(x, y + 1, z, chance() < 0.15 ? "park_wall_b" : BAND[k]);
                        if (k == LEVELS - 1) {
                            String out = z == 0 ? "north" : z == DD - 1 ? "south" : x == 0 ? "west" : "east";
                            p.set(x, y + 2, z, chance() < 0.08 ? "air" : "st_rail[facing=" + out + "]");
                        }
                    }
                }
            }
            if (k == LEVELS - 1) {
                return;
            }
            // the columns on their lines, every eight along, banded at their feet in the level's colour
            for (int cz : COLUMN_LINES) {
                for (int x = 0; x < DW; x += 8) {
                    column(Math.min(x, DW - 1), y, cz, k);
                }
                column(DW - 1, y, cz, k);
            }
            for (int z = 8; z < DD - 1; z += 8) {
                column(0, y, z, k);
                column(DW - 1, y, z, k);
            }
        }

        private void column(int x, int y, int z, int k) {
            p.set(x, y + 1, z, BAND[k]);
            p.set(x, y + 2, z, "park_column");
            p.set(x, y + 3, z, chance() < 0.15 ? "park_wall_soot" : "park_wall");
        }

        private String floor(int x, int z, int k) {
            double g = grime.fbm(x, z + k * 41.0, 6, 2) * 0.5 + 0.5;
            if (g > 0.74 || chance() < 0.02) {
                return chance() < 0.7 ? "park_floor_grit" : "park_floor_crack";
            }
            boolean aisle = x < BAYS_X0 || x > BAYS_X1 || aisleZ(z);
            if (aisle && g > 0.35 && chance() < 0.5) {
                return "park_floor_tyre[axis=" + (x < BAYS_X0 || x > BAYS_X1 ? "z" : "x") + "]";
            }
            if (k == LEVELS - 1 && g < 0.25) {
                return chance() < 0.3 ? "asphalt_moss" : "park_floor_b";
            }
            return g > 0.6 ? "park_floor_b" : "park_floor";
        }

        private boolean aisleZ(int z) {
            return z >= 6 && z <= 11 || z >= 23 && z <= 28 || z >= 40 && z <= 45;
        }

        /**
         * A ramp from level k to k + 1 in lanes x0 to x0 + 2, from z0 climbing toward +z (s = 1) or
         * -z, a half block a cell over eight: solid under it, open over it to the slab above (cut
         * through the one it arrives at), the kerb wall along its inner side and the hole's edge.
         */
        private void ramp(int x0, int z0, int s, int k) {
            int base = H * k, roof = k + 2 < LEVELS ? H * (k + 2) - 1 : H * (k + 1) + 4;
            int kerb = s > 0 ? x0 + 3 : x0 - 1;
            for (int t = 0; t < 8; t++) {
                int z = z0 + s * t;
                int s2 = 2 * (base + 1) + t + 1;
                int whole = Math.floorDiv(s2, 2);
                boolean half = (s2 & 1) == 1;
                for (int x = x0; x < x0 + 3; x++) {
                    for (int y = base + 1; y < (half ? whole : whole - 1); y++) {
                        p.set(x, y, z, "bunk_concrete");
                    }
                    if (half) {
                        p.set(x, whole, z, "garage_ramp_half");
                    } else {
                        p.set(x, whole - 1, z, t % 3 == 1 ? "park_floor" : "park_floor_tyre[axis=z]");
                    }
                    for (int y = half ? whole + 1 : whole; y <= roof; y++) {
                        p.set(x, y, z, "air");
                    }
                }
                for (int y = base + 1; y <= base + H + 1; y++) {
                    p.set(kerb, y, z, y == base + H + 1 ? BAND[k + 1] : "park_wall");
                }
            }
            // the hole's end beyond the ramp's foot, railed where the slab stops over it
            int foot = z0 - s;
            for (int x = x0; x < x0 + 3; x++) {
                p.set(x, base + H + 1, foot, BAND[k + 1]);
            }
        }

        /** The stalls of a row five deep from z0, heads to side head: lines, wheel stops, oil, a car now and then. */
        private void bays(int y, int z0, int head, int k) {
            int headZ = head == 0 ? z0 : z0 + 4;
            String stop = "park_wheelstop[facing=" + (head == 0 ? "south" : "north") + "]";
            for (int x = BAYS_X0; x <= BAYS_X1; x += 3) {
                if (z0 == 1 && near(x, TOWERS)) {
                    // the walk from the stair tower's door across the front row
                    for (int z = z0; z < z0 + 5; z++) {
                        for (int i = 0; i < 3 && x + i <= BAYS_X1; i++) {
                            p.set(x + i, y, z, "park_line_yellow[axis=z]");
                        }
                    }
                    continue;
                }
                for (int z = z0; z < z0 + 5; z++) {
                    if (chance() < 0.85) {
                        p.set(x, y, z, "park_line[axis=z]");
                    }
                }
                if (x + 3 > BAYS_X1 + 1) {
                    break;
                }
                for (int i = 1; i <= 2; i++) {
                    if (chance() < 0.9) {
                        p.set(x + i, y + 1, headZ, stop);
                    }
                    if (chance() < 0.2) {
                        p.set(x + i, y, z0 + 2, "park_floor_oil");
                    }
                }
                // one stall in twenty-five still taken (every car is an entity), fewer up top
                if (chance() < (k == LEVELS - 1 ? 0.02 : 0.04)) {
                    float yaw = (head == 0 ? 180f : 0f) + (chance() < 0.3 ? 180f : 0f);
                    p.car(x + 2, y + 1, z0 + 2.5, yaw, 0, 5.2, rng.nextLong());
                }
            }
        }

        /** Whether stall column x is in front of a stair tower's door. */
        private boolean near(int x, int[] towers) {
            for (int t : towers) {
                if (x + 2 >= t && x <= t + 4) {
                    return true;
                }
            }
            return false;
        }

        /** The arrows down the end aisles: up the west, on round, down the east. */
        private void arrows(int y) {
            for (int z = 8; z < DD - 4; z += 10) {
                if (z >= 24 && z <= 35) {
                    continue;
                }
                p.set(5, y, z, "park_arrow[facing=south]");
                p.set(DW - 6, y, z, "park_arrow[facing=north]");
            }
        }

        /** Strip lights under the slab over the aisles, most dead, a few lit or stuttering, one fallen. */
        private void tubes(int y) {
            for (int x = 4; x < DW - 2; x += 6) {
                for (int z : new int[] {8, 25, 42}) {
                    if (p.get(x, y + 3, z) != null && !p.get(x, y + 3, z).equals("air")) {
                        continue;
                    }
                    double r = chance();
                    String t = r < 0.08 && power ? "bunk_tube_lit" : r < 0.16 ? "bunk_tube_flicker" : r < 0.2 ? "bunk_tube_dangle" : "bunk_tube";
                    p.set(x, y + 3, z, t + "[axis=x]");
                }
            }
        }

        /** The roof's lamp posts on the column lines. */
        private void poles(int y) {
            for (int x = 16; x < DW - 8; x += 24) {
                for (int z : new int[] {17, 34}) {
                    p.set(x, y + 1, z, "st_lot_base");
                    for (int yy = y + 2; yy <= y + 6; yy++) {
                        p.set(x, yy, z, "st_lot_pole");
                    }
                    p.set(x, y + 7, z, (power && chance() < 0.3 ? "st_lot_head_on" : "st_lot_head") + "[facing=" + (chance() < 0.5 ? "north" : "east") + "]");
                }
            }
        }

        /**
         * A stair tower against the front parapet, west wall at x0, standing out to the north: the
         * stair turned so its doors open south onto each level through the parapet, capped over
         * the roof level with its sign.
         */
        private void tower(int x0) {
            // the stair's frame turned half round: its door wall on the parapet line, its far end out to the north
            Placer q = p.turned(x0 + 4, 0, 0, 2);
            int top = H * (LEVELS - 1), cap = top + 5;
            for (int y = 0; y <= cap; y++) {
                for (int x = 0; x <= 4; x++) {
                    for (int z = 0; z <= 9; z++) {
                        boolean ring = x == 0 || x == 4 || z == 0 || z == 9;
                        if (y == 0 || y == cap) {
                            q.set(x, y, z, "concrete_dark");
                        } else if (ring) {
                            q.set(x, y, z, y % H == 1 ? "park_band_" + new String[] {"blue", "green", "orange"}[Math.min(y / H, 2)]
                                    : chance() < 0.12 ? "park_wall_b" : "park_wall");
                        }
                    }
                }
            }
            int[] last = Stairwell.build(q, rng, 1, 0, new int[] {0, H, top}, false, 0, power ? 0.2 : 0);
            Stairwell.capTop(q, last);
            // the slot windows up the far end, the sign over each door
            for (int y = 2; y < top + 3; y += 4) {
                q.set(2, y, 9, "tx_glass[axis=x]");
            }
            for (int k = 0; k < LEVELS; k++) {
                q.set(3, H * k + 2, -1, "park_st_stairs[facing=north]");
            }
        }
    }
}
