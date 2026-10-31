package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Camp Black Pine (Josiah, 2026-10-30: the northwest military zone), the training camp cut into the
 * pines of the Black Pine woods, after refs/camp (the quonset huts, the parade ground, the motor
 * pool under the trees, the obstacle course, the steel water tower) and refs/military; in its own
 * frame round the plan's spot and built in tiles ({@link FrameSite}), the whole camp drawn once:
 * <ul>
 * <li>the wire round it on the patrol track, the watchtowers at the corners and halfway along, and at
 * each place a road comes in the gate: the booth, the barrier, the jersey blocks;</li>
 * <li>the ring road of gravel inside, the avenue and the cross street meeting at the parade ground:
 * the old asphalt drill square with its border, the bollards round it, the reviewing stand and the
 * flag, the headquarters along its north side;</li>
 * <li>to the north-west the lines of quonset huts the recruits slept in, round-roofed in corrugated
 * steel, the doors and windows in their wooden ends; west, the barracks blocks and the mess hall;</li>
 * <li>south-west, where the outbreak caught it: the tent lines, the field hospital, the burn pit, the
 * long trench and the graves;</li>
 * <li>north-east, the motor pool under the trees: the open sheds, the hesco bays, the maintenance
 * hangar, the fuel bladders, the wash rack; east, the armoury, the helipad and the radio mast;</li>
 * <li>south-east, the training ground: the obstacle course, the pistol range, the water tower on its
 * steel legs;</li>
 * <li>and everywhere between them the pines left standing, the needles over the forest floor.</li>
 * </ul>
 */
final class CampBlackPine extends FrameSite {
    /** The frame round the spot, the wire this far in, the ring road's middle this far in. */
    static final int X0 = -180, X1 = 179, Z0 = -150, Z1 = 149, TILE_W = 240, WIRE = 4, RING = 24;
    /** The parade ground. */
    static final int PX0 = -45, PX1 = 44, PZ0 = -32, PZ1 = 31;

    CampBlackPine(Place p, BlockSink out) {
        super(p, out, 0xB1AC9L);
    }

    static List<Place> places(Plan.Spot s, County county, Plan plan, long h) {
        return FrameSite.tiles("camp", s, county, plan, h, X0, Z0, X1, Z1, TILE_W);
    }

    @Override
    void build() {
        List<int[]> g = gates;
        copy(piece("camp", 0xCA3BL, (q, r) -> new Yard(q, r, g, seed).build()));
    }

    /** The camp, drawn whole in the frame. */
    static final class Yard extends Grounds {
        private final List<int[]> gates;
        private final Noise noise;
        private final Camp camp;
        private final double hit = 0.55;
        /** Where the camp's cleared ground is: no pine left standing in these. */
        private static final int[][] CLEARED = {
            {-50, -70, 50, 45}, {-150, -40, -60, 40}, {-152, 44, -58, 120}, {56, -122, 152, -50}, {56, -35, 152, 35}, {56, 44, 152, 120}};

        Yard(Placer p, SplittableRandom rng, List<int[]> gates, long seed) {
            super(p, rng, X1 - X0 + 1, Z1 - Z0 + 1, 0.4, 0.15, 0.15);
            this.gates = gates;
            this.noise = new Noise(seed);
            this.camp = new Camp(p, rng, hit);
        }

        /** A camp helper drawing in a building's frame: its front along local z = 0 facing that way (0 north .. 3 west). */
        private Camp at(int x0, int z0, int bw, int bd, int facing) {
            Placer q = switch (facing & 3) {
                case 0 -> p.turned(x0, 0, z0, 0);
                case 1 -> p.turned(x0 + bd - 1, 0, z0, 1);
                case 2 -> p.turned(x0 + bw - 1, 0, z0 + bd - 1, 2);
                default -> p.turned(x0, 0, z0 + bw - 1, 3);
            };
            return new Camp(q, rng, hit);
        }

        void build() {
            ground();
            roads();
            parade();
            huts();
            barracksRow();
            outbreak();
            motorPool();
            east();
            training();
            perimeter();
            pines();
        }

        // ------------------------------------------------------------------ the ground

        private boolean inBox(int x, int z, int x0, int z0, int x1, int z1) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        /** The forest floor: needles and moss under the pines, grass in the clearings, bare earth where feet wore it. */
        private void ground() {
            for (int x = X0; x <= X1; x++) {
                for (int z = Z0; z <= Z1; z++) {
                    double n = noise.fbm(x, z, 22, 3) * 0.5 + 0.5, r = chance();
                    String g = n < 0.3 ? "minecraft:podzol" : n < 0.4 ? (r < 0.5 ? "minecraft:coarse_dirt" : "minecraft:podzol")
                            : n < 0.75 ? "minecraft:grass_block" : r < 0.35 ? "minecraft:moss_block" : "minecraft:grass_block";
                    set(x, 0, z, g);
                    if (g.equals("minecraft:podzol") && r < 0.05) {
                        set(x, 1, z, "gc_twigs_" + (1 + rng.nextInt(3)) + "[facing=" + dir() + "]");
                    } else if (g.equals("minecraft:grass_block") && r < 0.08) {
                        set(x, 1, z, r < 0.02 ? "weeds_tall" : "grass_tuft");
                    }
                }
            }
        }

        private String gravel() {
            double r = chance();
            return r < 0.55 ? "minecraft:gravel" : r < 0.8 ? "mu_gravel" : r < 0.93 ? "minecraft:coarse_dirt" : "minecraft:mud";
        }

        private void road(int x0, int z0, int x1, int z1, boolean paved) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    double r = chance();
                    set(x, 0, z, paved ? (r < 0.08 ? "asphalt_crack" : r < 0.12 ? "asphalt_patch" : r < 0.15 ? "asphalt_moss" : "asphalt") : gravel());
                    set(x, 1, z, "air");
                }
            }
        }

        /** The ring road, the avenue and the cross street, the patrol track inside the wire, the spurs from the gates. */
        private void roads() {
            int a = X0 + RING, b = X1 - RING, c = Z0 + RING, d = Z1 - RING;
            road(a - 3, c - 3, b + 3, c + 2, false);
            road(a - 3, d - 2, b + 3, d + 3, false);
            road(a - 3, c - 3, a + 2, d + 3, false);
            road(b - 2, c - 3, b + 3, d + 3, false);
            road(-2, c, 2, PZ0 - 1, true);
            road(-2, PZ1 + 1, 2, d, true);
            road(a, -2, PX0 - 1, 2, true);
            road(PX1 + 1, -2, b, 2, true);
            int t0 = WIRE + 3, t1 = WIRE + 6;
            road(X0 + t0, Z0 + t0, X1 - t0, Z0 + t1, false);
            road(X0 + t0, Z1 - t1, X1 - t0, Z1 - t0, false);
            road(X0 + t0, Z0 + t0, X0 + t1, Z1 - t0, false);
            road(X1 - t1, Z0 + t0, X1 - t0, Z1 - t0, false);
            for (int[] g : gates) {
                int gx = Math.max(X0, Math.min(X1, g[0])), gz = Math.max(Z0, Math.min(Z1, g[1]));
                switch (g[2]) {
                    case 0 -> road(gx - 3, Z0, gx + 3, c, false);
                    case 2 -> road(gx - 3, d, gx + 3, Z1, false);
                    case 1 -> road(b, gz - 3, X1, gz + 3, false);
                    default -> road(X0, gz - 3, a, gz + 3, false);
                }
            }
        }

        // ------------------------------------------------------------------ the parade ground and the headquarters

        private void parade() {
            for (int x = PX0; x <= PX1; x++) {
                for (int z = PZ0; z <= PZ1; z++) {
                    boolean edge = x == PX0 + 2 || x == PX1 - 2 || z == PZ0 + 2 || z == PZ1 - 2;
                    boolean inner = x > PX0 + 2 && x < PX1 - 2 && z > PZ0 + 2 && z < PZ1 - 2;
                    double r = chance();
                    set(x, 0, z, edge && r > 0.15 ? "af_paint" : r < 0.06 ? "asphalt_crack" : r < 0.09 ? "asphalt_moss" : "asphalt_b");
                    set(x, 1, z, "air");
                    // the squads' marks across it every six
                    if (inner && Math.floorMod(x - PX0, 6) == 0 && Math.floorMod(z - PZ0, 8) == 4 && chance() < 0.8) {
                        set(x, 0, z, "af_paint_y");
                    }
                }
            }
            // the bollards round it, gaps where the streets come in
            for (int x = PX0 - 1; x <= PX1 + 1; x += 3) {
                for (int z : new int[] {PZ0 - 1, PZ1 + 1}) {
                    if (Math.abs(x) > 4 && chance() > hit * 0.15) {
                        set(x, 1, z, "cp_post");
                    }
                }
            }
            for (int z = PZ0 - 1; z <= PZ1 + 1; z += 3) {
                for (int x : new int[] {PX0 - 1, PX1 + 1}) {
                    if (Math.abs(z) > 4 && chance() > hit * 0.15) {
                        set(x, 1, z, "cp_post");
                    }
                }
            }
            // the floodlight masts at the corners
            for (int[] c : new int[][] {{PX0 - 3, PZ0 - 3}, {PX1 + 3, PZ0 - 3}, {PX0 - 3, PZ1 + 3}, {PX1 + 3, PZ1 + 3}}) {
                camp.floodMast(c[0], c[1], 9);
            }
            reviewingStand(-8, PZ1 + 4);
            // a squad's kit dropped where they broke ranks, a tipped stretcher, the mess of it
            for (int i = 0; i < 26; i++) {
                int x = PX0 + 4 + rng.nextInt(PX1 - PX0 - 8), z = PZ0 + 4 + rng.nextInt(PZ1 - PZ0 - 8);
                set(x, 1, z, any("ap_bag_flat[facing=" + dir() + "]", "debris_chunk[facing=" + dir() + "]", "trash_bag[facing=" + dir() + "]",
                        "bunk_crate_ammo[facing=" + dir() + "]", "blood_splat", "scorch"));
            }
            // the headquarters along the north side, its front to the square
            at(-20, -66, 40, 16, 2).headquarters(40, 16);
        }

        /** The reviewing stand south of the square: a concrete dais three high with its steps, a tin roof on posts, the flag beside it. */
        private void reviewingStand(int x0, int z0) {
            int w = 16, d = 6;
            for (int x = x0; x < x0 + w; x++) {
                for (int z = z0; z < z0 + d; z++) {
                    set(x, 0, z, "concrete_dark");
                    boolean front = z == z0;
                    for (int y = 1; y <= 2; y++) {
                        set(x, y, z, front && y == 2 ? "stone_trim" : "concrete_panel");
                    }
                    set(x, 3, z, "floor_concrete");
                    if ((x == x0 || x == x0 + w - 1) && (z == z0 || z == z0 + d - 1)) {
                        for (int y = 4; y <= 6; y++) {
                            set(x, y, z, "steel_olive");
                        }
                    }
                    set(x, 7, z, chance() < 0.05 + hit * 0.1 ? "air" : "corrugated_green");
                }
                set(x, 4, z0, x % 2 == 0 ? "railing" : "air");
            }
            // the steps up the back
            for (int k = 0; k < 3; k++) {
                for (int x = x0 + 6; x <= x0 + 9; x++) {
                    set(x, 1 + k, z0 + d + 2 - k, "stairs_concrete[facing=north]");
                    for (int y = 1; y <= k; y++) {
                        set(x, y, z0 + d + 2 - k, "concrete_panel");
                    }
                }
            }
            for (int x = x0 + 2; x < x0 + w - 2; x += 2) {
                set(x, 4, z0 + 3, chance() < 0.2 ? "trade_folding_chair_tipped[facing=" + dir() + "]" : "trade_folding_chair[facing=north]");
            }
            set(x0 + 8, 4, z0 + 1, "ch_lectern[facing=north]");
            new Civic(p, rng).flagpole(x0 + w + 3, z0 + 2, 13);
            new Civic(p, rng).flagpole(x0 - 4, z0 + 2, 13);
        }

        // ------------------------------------------------------------------ the huts and the barracks

        /** Four huts a row, two rows, in the north-west under the trees. */
        private void huts() {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 2; j++) {
                    hut(-148 + i * 24, -118 + j * 32, 11, 22);
                }
            }
            road(-150, -94, -60, -91, false);
            for (int i = 0; i < 4; i++) {
                road(-148 + i * 24 + 4, -124, -148 + i * 24 + 6, -119, false);
            }
        }

        /**
         * A quonset hut span wide (x) and len long from (x0, z0): the corrugated steel semicircle in the
         * mf_vault pieces down to the ground, its wooden ends with the door and two windows, the
         * concrete steps, inside the cots in two rows and the stove.
         */
        private void hut(int x0, int z0, int span, int len) {
            double r = span / 2.0, cx = x0 + r;
            Map<Long, double[]> cells = ArmyFieldBase.arc(cx, 0.6, r, x0, x0 + span);
            int z1 = z0 + len - 1;
            for (int x = x0; x < x0 + span; x++) {
                for (int z = z0; z <= z1; z++) {
                    set(x, 0, z, "floor_concrete");
                    for (int y = 1; y <= (int) r + 1; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
            boolean holed = chance() < hit * 0.5;
            for (double[] c : cells.values()) {
                for (int z = z0; z <= z1; z++) {
                    if (holed && z > z0 + 6 && z < z0 + 11 && c[1] > r - 1) {
                        continue;  // the roof stove in
                    }
                    set((int) c[0], (int) c[1], z, "mf_vault_" + (z == z0 || z == z1 ? "end_" : "") + (int) c[2] + "_" + (int) c[3]);
                }
            }
            for (int x = x0; x < x0 + span; x++) {
                double top = 0.6 + Math.sqrt(Math.max(0, r * r - (x + 0.5 - cx) * (x + 0.5 - cx)));
                for (int y = 1; y < top - 0.6; y++) {
                    if (cells.containsKey(((long) x << 32) ^ (y & 0xffffffffL))) {
                        continue;
                    }
                    for (int z : new int[] {z0, z1}) {
                        String out = z == z0 ? "north" : "south";
                        boolean door = Math.abs(x + 0.5 - cx) < 1.1 && y <= 2;
                        boolean win = Math.abs(Math.abs(x + 0.5 - cx) - 3) < 0.6 && y == 2;
                        set(x, y, z, door ? "air" : win ? (chance() < 0.3 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]" : "siding_sage");
                    }
                }
            }
            int dx = (int) Math.floor(cx - 0.5);
            door(dx, 0, z0, Doors.FLAT, "north");
            door(dx, 0, z1, Doors.FLAT, "south");
            for (int x = dx - 1; x <= dx + 2; x++) {
                set(x, 0, z0 - 1, "concrete_dark");
                set(x, 0, z1 + 1, "concrete_dark");
            }
            // the cots down both sides, the footlockers, the stove in the middle
            for (int z = z0 + 2; z < z1 - 1; z += 2) {
                for (int x : new int[] {x0 + 1, x0 + span - 2}) {
                    if (chance() < 0.85) {
                        String f = x == x0 + 1 ? "east" : "west";
                        set(x, 1, z, "ch_cot_head[facing=" + f + "]");
                        if (chance() < 0.4) {
                            set(x == x0 + 1 ? x + 1 : x - 1, 1, z, "pk_military_crate");
                        }
                    }
                }
            }
            set(dx, 1, z0 + len / 2, "home_wood_stove[facing=north]");
            mess(x0 + 1, z0 + 1, x0 + span - 2, z1 - 1, 0, 0.04 + hit * 0.04);
        }

        /** The two barracks blocks and the mess hall west of the square. */
        private void barracksRow() {
            String wall = any("siding_sage", "siding_white", "siding_grey");
            at(-146, -36, 32, 10, 1).barracks(32, 10, wall, "minecraft:dark_oak");
            at(-146, 4, 32, 10, 1).barracks(32, 10, wall, "minecraft:dark_oak");
            at(-110, -32, 30, 18, 1).messHall(30, 18);
            road(-132, -12, -60, -9, false);
            camp.toilets(-126, -16, 4);
        }

        // ------------------------------------------------------------------ the outbreak

        /** The south-west: the tent lines they put up when the camp filled, the hospital, the pit, the trench, the graves. */
        private void outbreak() {
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 3; j++) {
                    camp.tent(-148 + i * 11, 48 + j * 16, 10, j == 0 ? "white" : "green", true);
                }
            }
            camp.hospital(-98, 48, 18, 24);
            camp.burnPit(-70, 98, 6);
            camp.trench(-110, 96, 30);
            for (int x = -110; x <= -76; x += 3) {
                for (int z = 108; z <= 116; z += 4) {
                    if (chance() < 0.75) {
                        camp.grave(x, z);
                    }
                }
            }
            camp.fence(-150, 44, -60, 44, 0.3);
            camp.lightTower(-80, 46, chance() < 0.5);
        }

        // ------------------------------------------------------------------ the motor pool and the east side

        private void motorPool() {
            for (int k = 0; k < 2; k++) {
                for (int x = 60; x < 108; x++) {
                    for (int z = -118 + k * 18; z < -118 + k * 18 + 9; z++) {
                        set(x, 1, z, "air");
                    }
                }
                camp.shed(60, -118 + k * 18, 48);
            }
            for (int i = 0; i < 4; i++) {
                camp.bay(114 + i * 8, -118, any("jeep", "jeep_open", "box_truck"));
            }
            maintenanceHangar(84, -80, 22, 28);
            camp.fuelTanks(118, -86, 3);
            // the wash rack: a concrete pad with its drain and hoses
            for (int x = 58; x <= 76; x++) {
                for (int z = -80; z <= -64; z++) {
                    set(x, 0, z, x == 67 ? "bd_drain" : chance() < 0.2 ? "concrete_dark_stain" : "floor_concrete");
                    set(x, 1, z, "air");
                }
            }
            set(58, 1, -80, "navy_hose_reel[facing=east]");
            set(58, 1, -64, "navy_hose_reel[facing=east]");
            road(58, -90, 150, -88, false);
        }

        /** The maintenance hangar: a big quonset on low concrete walls, the doors at the south end. */
        private void maintenanceHangar(int x0, int z0, int span, int len) {
            double r = span / 2.0, cx = x0 + r;
            int wall = 3;
            Map<Long, double[]> cells = ArmyFieldBase.arc(cx, wall + 0.6, r, x0, x0 + span);
            int z1 = z0 + len - 1;
            for (int x = x0; x < x0 + span; x++) {
                boolean side = x == x0 || x == x0 + span - 1;
                for (int z = z0; z <= z1; z++) {
                    set(x, 0, z, chance() < 0.08 ? "concrete_dark_stain" : "floor_concrete");
                    for (int y = 1; y <= wall + (int) r + 1; y++) {
                        set(x, y, z, side && y <= wall ? "concrete_panel" : "air");
                    }
                }
            }
            for (double[] c : cells.values()) {
                for (int z = z0; z <= z1; z++) {
                    set((int) c[0], (int) c[1], z, "mf_vault_" + (z == z0 || z == z1 ? "end_" : "") + (int) c[2] + "_" + (int) c[3]);
                }
            }
            for (int x = x0; x < x0 + span; x++) {
                double top = wall + 0.6 + Math.sqrt(Math.max(0, r * r - (x + 0.5 - cx) * (x + 0.5 - cx)));
                for (int y = 1; y < top - 0.6; y++) {
                    if (cells.containsKey(((long) x << 32) ^ (y & 0xffffffffL))) {
                        continue;
                    }
                    set(x, y, z0, y <= 2 ? "concrete_panel" : "corrugated_green");
                    boolean door = Math.abs(x + 0.5 - cx) < r - 2.5 && y <= 8;
                    set(x, y, z1, door ? (y == 1 || chance() < 0.7 ? "air" : "garage_door[facing=south]") : "corrugated_green");
                }
            }
            p.vehicle(cx, 1, z0 + len / 2.0, 0f, any("jeep", "box_truck", "jeep_open"), rng.nextLong());
            for (int z = z0 + 2; z < z1 - 2; z += 3) {
                set(x0 + 1, 1, z, any("home_workbench[facing=east]", "pk_tool_chest[facing=east]", "tire_stack", "pw_drum"));
                set(x0 + span - 2, 1, z, any("tire_stack", "pk_military_crate", "pw_drum", "navy_toolbox[facing=west]"));
            }
            set((int) cx, wall + (int) r - 1, z0 + len / 2, Camp.lamp(rng, 0.5, "cp_flood_hang", "south"));
        }

        private void east() {
            at(62, -30, 18, 10, 3).armoury(18, 10);
            camp.helipad(96, -28);
            camp.helicopter(103, -21, chance() < 0.5);
            camp.mast(136, -10, 18);
            camp.crates(100, 10, 10, 8);
            camp.toilets(70, 20, 5);
            camp.lightTower(124, 24, true);
            road(76, -2, 76, 30, false);
            keypadBunker(BUNKER_X, BUNKER_Z);
        }

        // ------------------------------------------------------------------ the coded bunker

        /** The coded bunker's blast door (its two leaves at x .. x + 1) in the frame, the core's floor this far down. */
        static final int BUNKER_X = 128, BUNKER_Z = 22, BUNKER_FLOOR = -9;

        /**
         * The bunker the radio gives the code for (417702, world/Keypads; Josiah 2026-10-31: "Hillside bunker by
         * the radio mast"): a grassed mound south of the cross street by the mast, the concrete portal in its north
         * face, a long stair down through it to the landing, the blast door with its keypad on the wall beside it,
         * and under the training ground the Bunker core dressed as the army left it: the guard room, the bunks, the
         * plant, the store and the armoury past it. Sealed: the code is the only quiet way in (a crowbar the loud one).
         */
        private void keypadBunker(int bx, int bz) {
            int rise = -BUNKER_FLOOR;
            Placer bp = p.turned(bx, BUNKER_FLOOR, bz, 0);
            Bunker core = new Bunker(bp, new SplittableRandom(rng.nextLong()), Bunker.Kind.ARMY, Bunker.State.SEALED);
            core.build();
            // the landing in front of the blast door: x -1..2, z -5..-1, the stair coming in at its north wall
            for (int x = -2; x <= 3; x++) {
                for (int z = -6; z <= -1; z++) {
                    for (int y = 0; y <= 5; y++) {
                        boolean room = x >= -1 && x <= 2 && z >= -5 && y >= 1 && y <= 4;
                        if (z == -6 && x >= 0 && x <= 1 && y >= 1) {
                            continue;
                        }
                        bp.set(x, y, z, room ? "air" : y == 0 ? "bunk_floor_dirty" : y == 5 ? "bunk_concrete"
                                : y <= 1 ? "bunk_dado" : chance() < 0.15 ? "bunk_paint_flake" : "bunk_paint");
                    }
                }
            }
            bp.set(2, 2, -1, "bunk_keypad[facing=north]");
            bp.set(2, 3, -1, "bunk_lamp_red_lit[facing=north]");
            bp.set(-1, 3, -1, "bunk_lamp_red[facing=north]");
            bp.set(-1, 1, -4, "bunk_extinguisher[facing=east]");
            bp.set(2, 1, -5, "bunk_jerry_cans[facing=west]");
            bp.set(-1, 2, -2, "bunk_radmeter[facing=east]");
            // the long stair up to the portal, climbing north: two wide between painted walls under a stepped slab
            int top = -6 - rise;
            for (int k = 0; k < rise; k++) {
                int z = -6 - k, y = 1 + k;
                for (int x = -1; x <= 2; x++) {
                    boolean wall = x == -1 || x == 2;
                    bp.set(x, y - 1, z, "bunk_concrete");
                    for (int h = 0; h <= 4; h++) {
                        String b = h == 4 ? "bunk_concrete" : wall ? (h <= 1 ? "bunk_dado" : chance() < 0.12 ? "bunk_paint_flake" : "bunk_paint")
                                : h == 0 ? "stairs_concrete[facing=north]" : "air";
                        bp.set(x, y + h, z, b);
                    }
                    bp.set(x, y + 5, z, "bunk_concrete");
                }
                if (k % 4 == 2) {
                    bp.set(2, y + 3, z, "bunk_lamp_red[facing=west]");
                }
            }
            portal(bp, rise, top);
            p.mark("bunker sealed army", bx, 1, bz + top);
            bp.mark("bunker door", 0, 1, 0);
        }

        /**
         * The way in at ground level: the stair comes up out of the mound through a concrete headwall facing north
         * onto an apron by the cross street, its wing walls splayed back into the slope, the stencil and the
         * warning signs; the mound grassed over the stair and the bunker's head.
         */
        private void portal(Placer bp, int rise, int top) {
            // the portal's throat past the top step: two rows at ground level (bp y = rise), a hood over them
            int gy = rise, hz = top - 1;
            for (int z = hz; z <= top; z++) {
                for (int x = -1; x <= 2; x++) {
                    boolean wall = x == -1 || x == 2;
                    bp.set(x, gy, z, "bunk_floor_dirty");
                    for (int h = 1; h <= 4; h++) {
                        bp.set(x, gy + h, z, h == 4 ? "bunk_concrete" : wall ? "bunk_concrete" : "air");
                    }
                }
            }
            // the headwall across the mound's face, the opening two wide and three tall in it
            for (int x = -5; x <= 6; x++) {
                int off = (int) Math.ceil(Math.abs(x - 0.5)), h = off <= 4 ? 5 : off == 5 ? 4 : 3;
                for (int y = 1; y <= h; y++) {
                    if (x >= 0 && x <= 1 && y <= 3) {
                        continue;
                    }
                    bp.set(x, gy + y, hz, chance() < 0.2 ? "bunk_concrete_damp" : chance() < 0.15 ? "bunk_concrete_crack" : "bunk_concrete");
                }
            }
            // the wing walls, stepping down as they run back out to the apron's corners
            for (int k = 1; k <= 4; k++) {
                for (int y = 1; y <= 4 - k + 1; y++) {
                    bp.set(-5 - (k > 2 ? 1 : 0), gy + y, hz - k, "bunk_concrete");
                    bp.set(6 + (k > 2 ? 1 : 0), gy + y, hz - k, "bunk_concrete");
                }
            }
            // the apron, the stencil over the opening, the warnings either side, a jerrycan someone left (the refs:
            // refs/bunker/small-military-bunker-entrance, grey weathered concrete, weeds at the threshold)
            for (int x = -4; x <= 5; x++) {
                for (int z = hz - 4; z <= hz - 1; z++) {
                    bp.set(x, gy, z, chance() < 0.25 ? "bunk_floor_crack" : "concrete_dark");
                    // the slope's weeds already coming in at the apron's edges and up against the wing walls
                    boolean edge = x == -4 || x == 5 || z == hz - 4;
                    bp.set(x, gy + 1, z, edge && chance() < 0.45 ? (chance() < 0.4 ? "weeds_tall" : "grass_tuft") : "air");
                }
            }
            bp.set(0, gy + 4, hz - 1, "bunk_st_restricted[facing=north]");
            bp.set(-2, gy + 2, hz - 1, "bunk_sign_warning[facing=north]");
            bp.set(3, gy + 2, hz - 1, "bunk_sign_warning[facing=north]");
            bp.set(2, gy + 4, hz - 1, "bunk_lamp_red[facing=north]");
            bp.set(-1, gy + 1, hz - 2, "bunk_jerry_cans[facing=" + dir() + "]");
            // the mound: earth heaped over the stair and back over the landing, grassed, falling away to the sides
            for (int x = -12; x <= 13; x++) {
                for (int z = hz; z <= 0; z++) {
                    double cx = Math.abs(x - 0.5) / 12.5, along = (z - hz) / (double) (-hz);
                    double crest = 6.2 * Math.min(1, 0.35 + along * 1.4) * Math.max(0, 1 - Math.pow(cx, 2.2))
                            * Math.min(1, (0 - z + 3) / 6.0) + (noiseAt(x, z) - 0.5) * 0.8;
                    int hgt = (int) Math.round(crest);
                    // never into the way down: the passage's air reads as nothing, so keep under its roof clear
                    int roof = x < 0 || x > 1 ? 0 : z <= top ? 4 : z <= -6 ? -z - gy : 0;
                    for (int y = 1; y <= hgt; y++) {
                        if (y > roof && bp.get(x, gy + y, z) == null) {
                            bp.set(x, gy + y, z, y == hgt ? (chance() < 0.12 ? "minecraft:moss_block" : "minecraft:grass_block") : "minecraft:dirt");
                        }
                    }
                    if (hgt > roof && chance() < 0.1 && bp.get(x, gy + hgt + 1, z) == null) {
                        bp.set(x, gy + hgt + 1, z, chance() < 0.3 ? "weeds_tall" : "grass_tuft");
                    }
                }
            }
            // the vent stack poking out of the mound's top, over the landing
            for (int y = 1; y <= 7; y++) {
                bp.setIfAir(4, gy + y, -3, y == 7 ? "bunk_vent_cap" : "bunk_vent_stack");
            }
        }

        private double noiseAt(int x, int z) {
            return noise.fbm(x * 3.1, z * 3.1, 9, 2) * 0.5 + 0.5;
        }

        // ------------------------------------------------------------------ the training ground

        private void training() {
            camp.assaultCourse(60, 48, 82);
            camp.range(62, 72, 40, 40);
            waterTower(126, 92);
        }

        /**
         * The water tower: four steel legs braced in tiers up to the deck, the round tank on it with its
         * cone roof, the ladder up a leg to the catwalk.
         */
        private void waterTower(int x, int z) {
            int deck = 14, s = 6;
            int[][] corner = {{0, 0}, {s, 0}, {s, s}, {0, s}};
            String[] turn = {"north", "east", "south", "west"};
            for (int c = 0; c < 4; c++) {
                for (int y = 1; y < deck; y++) {
                    set(x + corner[c][0], y, z + corner[c][1], "cp_tw_leg[facing=" + turn[c] + "]");
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
                        set(at[0], y, at[1], "cp_tw_girt[facing=" + turn[c] + "]");
                    }
                }
            }
            for (int i = -1; i <= s + 1; i++) {
                for (int k = -1; k <= s + 1; k++) {
                    set(x + i, deck, z + k, "cp_tw_deck");
                    if (i == -1 || k == -1 || i == s + 1 || k == s + 1) {
                        set(x + i, deck + 1, z + k, "railing");
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

        // ------------------------------------------------------------------ the wire

        private boolean gap(int x, int z) {
            for (int[] g : gates) {
                if (Math.abs(x - g[0]) + Math.abs(z - g[1]) <= 6 + WIRE && (g[2] % 2 == 0 ? Math.abs(x - g[0]) <= 5 : Math.abs(z - g[1]) <= 5)) {
                    return true;
                }
            }
            return false;
        }

        /** The wire on the patrol track, the towers, and at each gate the booth, the barrier, the blocks. */
        private void perimeter() {
            int a = X0 + WIRE, b = X1 - WIRE, c = Z0 + WIRE, d = Z1 - WIRE;
            for (int x = a; x <= b; x++) {
                for (int z : new int[] {c, d}) {
                    if (!gap(x, z)) {
                        camp.fence(x, z, x, z, 0.02 + hit * 0.04);
                    }
                }
            }
            for (int z = c; z <= d; z++) {
                for (int x : new int[] {a, b}) {
                    if (!gap(x, z)) {
                        camp.fence(x, z, x, z, 0.02 + hit * 0.04);
                    }
                }
            }
            int[][] towers = {{a + 2, c + 2}, {b - 6, c + 2}, {b - 6, d - 6}, {a + 2, d - 6}, {-2, c + 2}, {-2, d - 6}, {a + 2, -2}, {b - 6, -2}};
            for (int[] t : towers) {
                if (!gap(t[0] + 2, t[1] + 2)) {
                    camp.watchtower(t[0], t[1]);
                }
            }
            for (int[] g : gates) {
                boolean alongX = g[2] % 2 == 0;
                int gx = Math.max(a, Math.min(b, g[0])), gz = Math.max(c, Math.min(d, g[1]));
                int inX = g[2] == 1 ? -1 : g[2] == 3 ? 1 : 0, inZ = g[2] == 0 ? 1 : g[2] == 2 ? -1 : 0;
                int bx = gx + inX * 5 + (alongX ? 5 : 0), bz = gz + inZ * 5 + (alongX ? 0 : 5);
                camp.booth(bx - 1, bz - 1);
                if (alongX) {
                    camp.boom(gx - 4, gz + inZ * 2, 8, chance() < 0.4);
                }
                for (int k = -1; k <= 1; k += 2) {
                    int jx = alongX ? gx + k * 6 : gx + inX * 2, jz = alongX ? gz + inZ * 2 : gz + k * 6;
                    set(jx, 1, jz, "jersey_barrier[facing=" + (alongX ? "north" : "east") + "]");
                }
                // the camp's sign by the way in
                int sx = gx + inX * 3 + (alongX ? -6 : 0), sz = gz + inZ * 3 + (alongX ? 0 : -6);
                set(sx, 1, sz, "cp_post");
                set(sx, 2, sz, "cp_post");
            }
        }

        // ------------------------------------------------------------------ the pines

        /** The pines left standing between everything: on the forest floor wherever there's room. */
        private void pines() {
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
            for (int x = X0 + 2; x <= X1 - 2; x += 5) {
                for (int z = Z0 + 2; z <= Z1 - 2; z += 5) {
                    int tx = x + rng.nextInt(4), tz = z + rng.nextInt(4);
                    double n = noise.fbm(tx, tz, 40, 2) * 0.5 + 0.5;
                    boolean cleared = false;
                    for (int[] c : CLEARED) {
                        cleared |= inBox(tx, tz, c[0] - 3, c[1] - 3, c[2] + 3, c[3] + 3);
                    }
                    if (cleared || chance() > 0.25 + n * 0.5 || !clear(tx, tz)) {
                        continue;
                    }
                    Kit.plant(spot, chance() < 0.08 ? Trees.Kind.DEAD : Trees.Kind.PINE, tx, tz, false, rng);
                }
            }
        }

        /** Nothing standing within four of (x, z) at head height: room for a trunk and its low limbs. */
        private boolean clear(int x, int z) {
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    String g = p.get(x + dx, 0, z + dz);
                    if (g == null || !Kit.soil(g)) {
                        return false;
                    }
                    for (int y = 1; y <= 3; y++) {
                        String s = p.get(x + dx, y, z + dz);
                        if (s != null && !s.equals("air") && !s.startsWith("gc_") && !s.startsWith("grass") && !s.startsWith("weeds")) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}
