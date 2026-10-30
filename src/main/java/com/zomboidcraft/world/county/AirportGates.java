package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The airliners at Pier B and on the remote stands east of it (Josiah 2026-10-30: "airliners of
 * various types, private and commercial", tools/city/fleet.py): on the pier's west side a
 * Coastal and a Skyline twinjet nose in at gates 5 and 9, on its east side the Northstar
 * widebody at gate 8, each with its jet bridge from the gate's door to its front door (the
 * widebody's climbing two steps to its higher floor); out on the remote stands two Coastal
 * turboprops with the stair trucks at their back doors. Round each the ground crew's gear where
 * they left it: the belt loader at the hold, the baggage train, the power cart, chocks, cones,
 * the lead-in line and stop bar painted on the apron.
 */
final class AirportGates extends Grounds {
    /** Pier B's walls, the gate doors' level. */
    static final int PX0 = AirportTerminal.PIER_X[0], PX1 = PX0 + AirportTerminal.PIER_W, UP = AirportTerminal.UP;
    /** The remote stands: the turboprops' noses. */
    static final int[] REMOTE_X = {270, 330};
    static final int REMOTE_Z = -318;
    /** Everything this builds lies within these. */
    static final int BX0 = 30, BX1 = 380, BZ0 = -340, BZ1 = Airport.TWY_Z - Airport.TWY_SHOULDER - 1;

    AirportGates(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.2, 0.0, 0.25);
    }

    void build() {
        int[] gz = AirportTerminal.GATE_Z;
        gate(Airliner.plane("twinjet_coastal"), gz[0], -1);
        gate(Airliner.plane("twinjet_skyline"), gz[2], -1);
        gate(Airliner.plane("widebody_north"), gz[1], 1);
        for (int x : REMOTE_X) {
            remote(Airliner.plane("turboprop_coastal"), x, REMOTE_Z);
        }
    }

    // ------------------------------------------------------------------ the gates

    /**
     * A plane nosed in to the pier's wall on side d (-1 the west wall, 1 the east) at the gate whose
     * door is at gz: its front door lined up with the bridge's cab, which comes off the bridge's
     * side; the bridge out from the gate's door.
     */
    private void gate(Airliner.Plane pl, int gz, int d) {
        int wall = d < 0 ? PX0 : PX1, nx = wall + 5 * d;
        int cz = gz - d * (3 - pl.skinW());
        Placer q = p.turned(nx, 0, cz, d < 0 ? 1 : 3);
        pl.place(q, 0, 0, 0);
        int w0 = pl.west()[0];
        int[] door = {nx + d * w0, nx + d * (w0 + 1)};
        int sz = gz - 2 * d;
        bridge(wall, d, gz, door, sz, pl.floor());
        // the lead-in line down the plane's middle from the taxi lane, the stop bar under the nose
        for (int k = 3; k <= pl.length() + 30; k++) {
            int x = nx - d * k;
            if (Math.floorMod(k, 4) != 3) {
                set(x, 0, cz, "af_paint_y");
            }
        }
        for (int z = cz - 3; z <= cz + 3; z++) {
            set(nx - d * 7, 0, z, "af_paint_y");
        }
        rampGear(q, pl);
    }

    /** True inside the bridge: out along gz from the wall to the door, then the cab at the door. */
    private static boolean inBridge(int x, int z, int wall, int d, int gz, int[] door, int sz) {
        int lo = Math.min(wall + d, door[1]), hi = Math.max(wall + d, door[1]);
        return x >= lo && x <= hi && Math.abs(z - gz) <= 1 || (x == door[0] || x == door[1]) && z == sz;
    }

    /**
     * The jet bridge: its floor at the gate's slab, climbing a block a step two blocks out when the
     * plane's floor stands higher; the sides with their window band, the roof with its lamps; the
     * cab at the door with the bellows against the skin, the rotunda's column by the wall, the
     * drive leg and its wheels under the cab.
     */
    private void bridge(int wall, int d, int gz, int[] door, int sz, int floor) {
        int rise = floor - 1 - UP;
        String along = d < 0 ? "west" : "east", back = d < 0 ? "east" : "west";
        String[] faces = {"north", "east", "south", "west"};
        int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int x0 = Math.min(wall + d, door[1]) - 1, x1 = Math.max(wall + d, door[1]) + 1;
        for (int x = x0; x <= x1; x++) {
            int k = Math.abs(x - wall);
            int y = UP + Math.max(0, Math.min(rise, k - 4));
            boolean climb = rise > 0 && k >= 4 && k - 4 < rise;
            for (int z = gz - 3; z <= gz + 3; z++) {
                boolean cab = z == sz;
                if (inBridge(x, z, wall, d, gz, door, sz)) {
                    int yc = cab ? floor - 1 : y;
                    set(x, yc, z, "rp_jb_floor[facing=" + along + "]");
                    for (int yy = yc + 1; yy <= yc + 4; yy++) {
                        set(x, yy, z, climb && yy == yc + 1 && !cab ? "rp_as_step[facing=" + along + "]" : "air");
                    }
                    boolean lamp = Math.floorMod(x, 3) == 0;
                    set(x, yc + 5, z, cab ? "rp_jb_bellows_top[facing=" + (d < 0 ? "south" : "north") + "]"
                            : lamp ? "rp_jb_roof_lamp" + (power && chance() < 0.4 ? "_lit" : "") + "[facing=" + along + "]"
                            : "rp_jb_roof[facing=" + along + "]");
                    continue;
                }
                if (x == wall) {
                    continue;
                }
                int sides = 0, n = 0;
                for (int e = 0; e < 4; e++) {
                    if (inBridge(x - step[e][0], z - step[e][1], wall, d, gz, door, sz)) {
                        sides |= 1 << e;
                        n++;
                    }
                }
                String kind = null;
                int f = -1;
                if (n == 1) {
                    f = Integer.numberOfTrailingZeros(sides);
                    kind = "wall";
                } else if (n == 2) {
                    for (int e = 0; e < 4; e++) {
                        if ((sides >> e & 1) == 1 && (sides >> ((e + 3) % 4) & 1) == 1) {
                            f = e;
                        }
                    }
                    kind = f < 0 ? null : "corner";
                } else if (n == 0) {
                    for (int e = 0; e < 4; e++) {
                        int ee = (e + 3) % 4;
                        if (inBridge(x - step[e][0] - step[ee][0], z - step[e][1] - step[ee][1], wall, d, gz, door, sz)) {
                            f = e;
                            kind = "corner";
                        }
                    }
                }
                // nothing against the fuselage's skin: the bellows close that side
                if (kind == null || !empty(x, y + 1, z)) {
                    continue;
                }
                int yb = Math.abs(z - sz) <= 1 && (x == door[0] - 1 || x == door[1] + 1 || x == door[0] + 1 || x == door[1] - 1) ? Math.max(y, floor - 1) : y;
                set(x, yb, z, "rp_jb_floor[facing=" + along + "]");
                for (int yy = yb + 1; yy <= yb + 4; yy++) {
                    String piece = kind.equals("corner") ? "rp_jb_corner" : yy == yb + 2 ? "rp_jb_window" : yy == yb + 3 ? "rp_jb_wall_band" : "rp_jb_wall";
                    set(x, yy, z, piece + "[facing=" + faces[f] + "]");
                }
                set(x, yb + 5, z, "rp_jb_eave[facing=" + faces[f] + "]");
            }
        }
        // the bellows either side of the door against the skin
        String toward = d < 0 ? "south" : "north";
        for (int yy = floor; yy <= floor + 3; yy++) {
            for (int x : new int[] {Math.min(door[0], door[1]) - 1, Math.max(door[0], door[1]) + 1}) {
                if (empty(x, yy, sz)) {
                    set(x, yy, sz, "rp_jb_bellows[facing=" + (x < Math.min(door[0], door[1]) ? "east" : "west") + "]");
                }
            }
        }
        set(door[0] - d, floor, gz - d, "rp_jb_console[facing=" + toward + "]");
        // the rotunda's column by the wall, the drive leg under the cab
        for (int yy = 1; yy < UP; yy++) {
            set(wall + 3 * d, yy, gz, "rp_jb_column[facing=north]");
        }
        int lx = door[0] - d;
        for (int z : new int[] {gz - 1, gz + 1}) {
            set(lx, 1, z, "rp_jb_bogie[facing=" + back + "]");
            for (int yy = 2; yy < floor - 1; yy++) {
                set(lx, yy, z, yy < 4 ? "rp_jb_leg_lo[facing=north]" : "rp_jb_leg[facing=north]");
            }
        }
    }

    // ------------------------------------------------------------------ the remote stands

    /** A plane on a remote stand nose north at (x, z), the stair truck at its open door. */
    private void remote(Airliner.Plane pl, int x, int z) {
        Placer q = p.turned(x, 0, z, 0);
        pl.place(q, 0, 0, 0);
        for (int w : pl.west()) {
            stairTruck(q, pl.skinW() - 1, pl.floor() - 1, w);
        }
        for (int k = -20; k < pl.length() + 6; k++) {
            if (Math.floorMod(k, 4) != 3 && k > 1) {
                set(x, 0, z + k, "af_paint_y");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            set(x + dx, 0, z - 2, "af_paint_y");
        }
        rampGear(q, pl);
    }

    /**
     * The stair truck at a door on the west side of a plane, in the plane's frame: its platform at
     * the sill (px the cell outside the skin, fy the step under the floor), the cab under it, the
     * flight falling away west to the apron.
     */
    static void stairTruck(Placer q, int px, int fy, int lz) {
        for (int dz = 0; dz <= 1; dz++) {
            q.set(px, fy, lz + dz, "rp_as_top[facing=east]");
            q.set(px - 1, fy, lz + dz, "rp_as_top[facing=east]");
            q.set(px - 1, fy + 1, lz + dz, "railing");
            for (int yy = 1; yy < fy; yy++) {
                q.set(px, yy, lz + dz, yy == 1 ? "rp_as_cab_lo[facing=west]" : yy == 2 ? "rp_as_cab_hi[facing=west]" : "rp_as_lift[facing=east]");
                q.set(px - 1, yy, lz + dz, yy == 1 ? "rp_as_bed_wheels[facing=east]" : "rp_as_lift[facing=east]");
            }
            for (int k = 1; k < fy; k++) {
                int x = px - 1 - k;
                q.set(x, fy - k, lz + dz, "rp_as_step[facing=east]");
                for (int yy = 1; yy < fy - k; yy++) {
                    q.set(x, yy, lz + dz, yy == 1 ? "rp_as_bed[facing=east]" : "rp_as_lift[facing=east]");
                }
            }
        }
    }

    /**
     * The ground crew's gear about a plane, in its frame: the belt loader up to the forward hold on
     * the right, the tug and its carts stopped under the wing, the power cart at the nose, chocks at
     * the nose wheel, cones off the wing tips and the tail.
     */
    private void rampGear(Placer q, Airliner.Plane pl) {
        int e = pl.skinE() + 1, hz = Math.max(8, pl.length() / 4);
        if (pl.floor() >= 7) {
            q.set(e + 4, 1, hz, "rp_bl_0[facing=west]");
            q.set(e + 3, 1, hz, "rp_bl_1[facing=west]");
            q.set(e + 2, 2, hz, "rp_bl_2[facing=west]");
            q.set(e + 1, 2, hz, "rp_bl_3[facing=west]");
        }
        int tx = e + 7, tz = hz + 2;
        q.set(tx, 1, tz, "rp_tug_front[facing=north]");
        q.set(tx, 1, tz + 1, "rp_tug_rear[facing=north]");
        int carts = 2 + rng.nextInt(3);
        for (int k = 0; k < carts; k++) {
            boolean bags = chance() < 0.7;
            q.set(tx, 1, tz + 3 + k * 3, "rp_dolly_front" + (bags ? "_bags" : "") + "[facing=north]");
            q.set(tx, 1, tz + 4 + k * 3, "rp_dolly_rear" + (bags ? "_bags" : "") + "[facing=north]");
        }
        if (chance() < 0.4) {
            q.set(tx + 2, 1, tz + 5, "rp_uld" + (chance() < 0.4 ? "_open" : "") + "[facing=" + dir() + "]");
        }
        if (chance() < 0.5) {
            q.set(tx - 1, 1, tz + 8, "ap_bag_open[facing=" + dir() + "]");
        }
        q.set(2, 1, -2, "rp_gpu[facing=north]");
        q.set(0, 1, Math.max(4, pl.length() / 8), "rp_chocks[facing=east]");
        int span = pl.span();
        for (int[] c : new int[][] {{-span - 1, pl.length() / 2}, {span + 1, pl.length() / 2}, {0, pl.length() + 2}}) {
            if (chance() < 0.85) {
                q.set(c[0], 1, c[1], "st_cone");
            }
        }
    }
}
