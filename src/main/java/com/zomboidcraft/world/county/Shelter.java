package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The civil-defence shelter under a public building: a Cold War fallout shelter the town dug
 * under its courthouse or town hall. A flight of concrete stairs drops through the ground floor
 * into the boiler room; stencilled SHELTER arrows lead to a steel door in its far wall, and past
 * the door a long stair runs straight down to the landing in front of the blast door. The
 * shelter itself is the {@link Bunker} core dressed as {@link Bunker.Kind#SHELTER}: rows of
 * bunks, the pantry, the washrooms and the emergency council chamber. The clue from the street is
 * the faded yellow-and-black shelter plate on the front wall.
 *
 * Drawn in the bunker's frame (blast door at x = 0..1 in the wall at z = 0, its floor y = 0): the
 * landing is z -5..-1, the long stair climbs north from there, and the boiler room sits at its
 * top, x -2..6, under the host's floor.
 */
final class Shelter {
    /** The boiler room inside its walls, in the bunker's frame relative to the stair's top (see {@link #build}). */
    private static final int BX0 = -2, BX1 = 6, DEEP = 7;

    private final Placer bp;
    private final SplittableRandom rng;
    private final Bunker.State state;
    /** Height of the boiler room's floor over the bunker's; the long stair has as many steps. */
    private final int rise;
    /** The boiler room's south wall, with the steel door in it; the stair starts one past it. */
    private final int doorZ;

    private Shelter(Placer bp, SplittableRandom rng, Bunker.State state, int rise) {
        this.bp = bp;
        this.rng = rng;
        this.state = state;
        this.rise = rise;
        this.doorZ = -6 - rise;
    }

    /**
     * Digs the shelter under a building. host draws in the building's frame; interior is the
     * room inside its walls on the ground floor as {x0, z0, x1, z1} in that frame, its floor slab
     * at host y = floor; the bunker's floor goes down to host y = depth. The way down and the
     * bunker take the first of the four turns and the corners of the interior (tried in a random
     * order) that keeps the boiler room under the building and everything inside area, given in
     * the host's sink coordinates. Returns false (and draws nothing) when nothing fits.
     */
    static boolean dig(Placer host, SplittableRandom rng, Bunker.State state, int[] interior, int floor, int depth, int[] area) {
        int rise = floor - 5 - depth;
        if (rise < 6) {
            return false;
        }
        int doorZ = -6 - rise;
        // the boiler room with its walls, plus the row in front of the stairs down from the hall
        int x0 = BX0 - 1, x1 = BX1 + 1, z0 = doorZ - DEEP - 2, z1 = doorZ;
        int[] order = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
        for (int i = order.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        long seed = rng.nextLong();
        for (int k : order) {
            int q = k & 3;
            int[] a = turn(q, x0, z0), b = turn(q, x1, z1);
            int mx0 = Math.min(a[0], b[0]), mx1 = Math.max(a[0], b[0]), mz0 = Math.min(a[1], b[1]), mz1 = Math.max(a[1], b[1]);
            if (mx1 - mx0 > interior[2] - interior[0] || mz1 - mz0 > interior[3] - interior[1]) {
                continue;
            }
            int ax = (k & 4) == 0 ? interior[0] - mx0 : interior[2] - mx1;
            int az = (k & 8) == 0 ? interior[1] - mz0 : interior[3] - mz1;
            Placer bp = host.turned(ax, depth, az, q);
            Bunker bunker = new Bunker(bp, new SplittableRandom(seed), Bunker.Kind.SHELTER, state);
            int[] box = bunker.bounds();
            if (!inside(bp, Math.min(box[0], x0), z0, Math.max(box[2], x1), box[3], area)) {
                continue;
            }
            Shelter s = new Shelter(bp, new SplittableRandom(seed ^ 0x5E17E2L), state, rise);
            s.boilerRoom();
            s.stair();
            s.landing();
            bunker.build();
            bp.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " shelter", BX1 - 1, rise + 6, doorZ - DEEP - 1);
            bp.mark("bunker door", 0, 1, -2);
            return true;
        }
        return false;
    }

    /**
     * The faded yellow-and-black shelter plate on the building's front wall (z = 0 in host's
     * frame, facing the street): on the first two wall blocks in a row at y 2..3 with air in
     * front of them, nearest the middle.
     */
    static void plate(Placer host, int x0, int x1, int z) {
        int mid = (x0 + x1) / 2;
        for (int d = 0; d <= (x1 - x0) / 2; d++) {
            for (int x : new int[] {mid + d, mid - d - 1}) {
                for (int y = 3; y >= 2; y--) {
                    if (x >= x0 && x + 1 <= x1 && solid(host.get(x, y, z)) && solid(host.get(x + 1, y, z))
                            && host.get(x, y, z - 1) == null && host.get(x + 1, y, z - 1) == null) {
                        // read from the street (facing south), the plate's left half is the east one
                        host.set(x + 1, y, z - 1, "bunk_shelter_a[facing=north]");
                        host.set(x, y, z - 1, "bunk_shelter_b[facing=north]");
                        return;
                    }
                }
            }
        }
    }

    private static boolean solid(String block) {
        return block != null && !block.contains("glass") && !block.contains("door") && !block.contains("sill")
                && !block.contains("bars") && !block.equals("air");
    }

    private static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static boolean inside(Placer bp, int x0, int z0, int x1, int z1, int[] area) {
        for (int[] c : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(c[0], c[1]);
            if (at[0] < area[0] || at[1] < area[1] || at[0] > area[2] || at[1] > area[3]) {
                return false;
            }
        }
        return true;
    }

    private double chance() {
        return rng.nextDouble();
    }

    // ------------------------------------------------------------------ the boiler room

    /**
     * Under the building's floor (y = rise + 5): painted block walls going damp at the foot, the
     * boiler and the water heater on the west wall, the town's drums of shelter water down the
     * east, pipes along the walls, and a flight of concrete stairs down from the hall above in the
     * north-east corner, its well railed off upstairs.
     */
    private void boilerRoom() {
        int f = rise, top = rise + 5;
        int z0 = doorZ - DEEP, z1 = doorZ - 1;
        for (int x = BX0 - 1; x <= BX1 + 1; x++) {
            for (int z = z0 - 1; z <= doorZ; z++) {
                boolean wall = x < BX0 || x > BX1 || z < z0 || z > z1;
                bp.set(x, f, z, wall ? "cellar_wall" : chance() < 0.08 ? "bunk_floor_drain" : "floor_concrete");
                for (int y = f + 1; y < top; y++) {
                    bp.set(x, y, z, wall ? (y == f + 1 && chance() < 0.5 ? "cellar_wall_damp" : "cellar_wall") : "air");
                }
            }
        }
        // the stairs down from the hall: x 4..5 against the east wall, top step under the floor
        // at the room's north end, five steps down to the boiler room's floor
        for (int k = 0; k < 5; k++) {
            int y = top - k, z = z0 + k;
            for (int x = BX1 - 2; x <= BX1 - 1; x++) {
                bp.set(x, y, z, "stairs_concrete[facing=north]");
                for (int h = 1; h <= 3; h++) {
                    bp.set(x, y + h, z, "air");
                }
            }
        }
        // the hall's floor in front of the top step, and the rails round the open well
        for (int x = BX1 - 2; x <= BX1 - 1; x++) {
            for (int h = 1; h <= 3; h++) {
                bp.set(x, top + h, z0 - 1, "air");
            }
            bp.set(x, top + 1, z0 + 4, "railing[facing=south]");
        }
        for (int z = z0; z <= z0 + 3; z++) {
            bp.set(BX1 - 3, top + 1, z, "railing[facing=west]");
            bp.set(BX1, top + 1, z, "railing[facing=east]");
        }
        // the boiler and the water heater, pipes off them along the ceiling
        int bz = z0 + 1 + rng.nextInt(2);
        bp.set(BX0, f + 1, bz, "cellar_furnace[facing=east]");
        bp.set(BX0, f + 2, bz, "cellar_furnace_top[facing=east]");
        bp.set(BX0, f + 1, bz + 2, "home_water_heater");
        bp.set(BX0, f + 2, bz + 2, "home_water_heater_top");
        for (int z = z0; z <= z1; z++) {
            bp.set(BX0, f + 4, z, "bunk_pipes[facing=east]");
        }
        for (int x = BX0 + 1; x <= BX1 - 3; x++) {
            bp.set(x, f + 4, z0, "bunk_pipes[facing=south]");
        }
        // shelter water and tins: what the town laid in, stacked against the east wall
        for (int z = z0 + 6; z <= z1; z++) {
            if (state == Bunker.State.SEALED || chance() < 0.5) {
                bp.set(BX1, f + 1, z, chance() < 0.7 ? "bunk_drum_water" : "bunk_tins[facing=west]");
            }
        }
        bp.set(BX0, f + 1, z1, "cellar_shelf_cans[facing=east]");
        bp.set(BX0, f + 1, z1 - 1, "bunk_rack_drums[facing=east]");
        // the arrows: SHELTER stencilled on the walls toward the steel door
        bp.set(3, f + 2, z1, "bunk_st_shelter_r[facing=north]");
        bp.set(BX0, f + 3, z0 + 5, "bunk_st_shelter_l[facing=east]");
        bp.set(2, f + 4, (z0 + z1) / 2, "cellar_bulb[axis=z]");
        // the steel door out to the stairs
        for (int x = 0; x <= 1; x++) {
            Doors.abandoned(bp, rng, x, f + 1, doorZ, "bunk_door_steel", "south", x == 0 ? "left" : "right");
            bp.set(x, f + 3, doorZ, "cellar_wall");
            bp.set(x, f + 4, doorZ, "cellar_wall");
        }
        bp.set(-1, f + 3, doorZ - 1, "bunk_shelter_a[facing=north]");
        bp.set(-2, f + 3, doorZ - 1, "bunk_shelter_b[facing=north]");
        scatter(BX0, z0, BX1, z1, f + 1, state == Bunker.State.SEALED ? 2 : 6);
    }

    // ------------------------------------------------------------------ the long stair

    /** Straight down from the steel door to the landing: concrete steps between painted walls, a red lamp now and then. */
    private void stair() {
        for (int j = 0; j < rise; j++) {
            int z = doorZ + 1 + j, y = rise - j;
            for (int x = -1; x <= 2; x++) {
                boolean wall = x == -1 || x == 2;
                bp.set(x, y - 1, z, "bunk_concrete");
                for (int h = 0; h <= 4; h++) {
                    String b;
                    if (h == 4) {
                        b = "bunk_concrete";
                    } else if (wall) {
                        b = h <= 1 ? "bunk_dado" : chance() < 0.12 ? "bunk_paint_flake" : "bunk_paint";
                    } else {
                        b = h == 0 ? "stairs_concrete[facing=north]" : "air";
                    }
                    bp.set(x, y + h, z, b);
                }
                if (j > 0) {
                    bp.set(x, y + 5, z, "bunk_concrete");  // the slope over the step above
                }
            }
            if (j % 6 == 3) {
                boolean lit = state == Bunker.State.SEALED && chance() < 0.5;
                bp.set(1, y + 3, z, "bunk_lamp_red" + (lit ? "_lit" : "") + "[facing=west]");
            }
            if (j % 6 == 0) {
                bp.set(0, y + 2, z, "bunk_st_shelter_l[facing=east]");
            }
        }
    }

    // ------------------------------------------------------------------ the landing

    /** In front of the blast door: a wider landing, the shelter plate over the door, the emergency lamps. */
    private void landing() {
        for (int x = -2; x <= 3; x++) {
            for (int z = -6; z <= -1; z++) {
                for (int y = 0; y <= 5; y++) {
                    boolean room = x >= -1 && x <= 2 && z >= -5 && y >= 1 && y <= 4;
                    if (z == -6 && x >= 0 && x <= 1 && y >= 1) {
                        continue;  // the stair comes in here
                    }
                    bp.set(x, y, z, room ? "air" : y == 0 ? "bunk_floor_dirty" : y == 5 ? "bunk_concrete" : y <= 1 ? "bunk_dado" : "bunk_paint");
                }
            }
        }
        bp.set(1, 4, -1, "bunk_shelter_a[facing=north]");
        bp.set(0, 4, -1, "bunk_shelter_b[facing=north]");
        boolean lit = state == Bunker.State.SEALED;
        bp.set(-1, 3, -1, "bunk_lamp_red" + (lit ? "_lit" : "") + "[facing=north]");
        bp.set(2, 3, -1, "bunk_lamp_red[facing=north]");
        bp.set(-1, 1, -4, "bunk_extinguisher[facing=east]");
        bp.set(2, 2, -3, "bunk_radmeter[facing=west]");
        bp.set(2, 1, -5, chance() < 0.5 ? "bunk_drum_water" : "bunk_jerry_cans[facing=west]");
        if (state == Bunker.State.BREACHED) {
            bp.set(0, 1, -3, "home_blood_drag[facing=north]");
            bp.set(1, 1, -2, "blood_splat");
        }
        scatter(-1, -5, 2, -1, 1, state == Bunker.State.SEALED ? 1 : 4);
    }

    /** Paper, grit and, where people fought, glass and blood over a floor at y. */
    private void scatter(int x0, int z0, int x1, int z1, int y, int n) {
        for (int i = 0; i < n; i++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1), z = z0 + rng.nextInt(z1 - z0 + 1);
            String there = bp.get(x, y, z);
            if (there == null || there.equals("air")) {
                double r = chance();
                bp.set(x, y, z, r < 0.5 ? "paper" : r < 0.8 ? "bunk_grit" : state == Bunker.State.SEALED ? "paper" : "glass_shards");
            }
        }
    }
}
