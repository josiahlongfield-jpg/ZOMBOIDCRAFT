package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven International's landside, in the airport's frame ({@link Airport}), after
 * refs/airport/terminal-exterior and parking-garage: the access road in from I-95 through the gate
 * in the west fence (the gatehouse, its barriers up, the guide sign), along the north of the field
 * to the terminal; the kerb road along the terminal's face, the legs round to it, the surface lot
 * between in its rows with the lamp standards; the ground transport island at the kerb with the
 * shuttle to the Airport station's stop and the taxi rank; the roads off it to the fire station,
 * the cargo shed, the fuel farm and the hangars, the path to the tower.
 *
 * <p>The evacuation came this way: the queue of cars that never got to the kerb stands abandoned
 * down the access road, doors open; the lot is full to its last rows.
 */
final class AirportLandside extends Grounds {
    /** The access road, eight wide, from the west fence to the terminal's east leg; its gate. */
    static final int ACC_Z0 = Airport.ACCESS_Z - 3, ACC_Z1 = Airport.ACCESS_Z + 4, ACC_X0 = Airport.X0 + 1;
    /** The kerb road along the terminal's face (its north lane the island's), the legs, the lot between. */
    static final int KZ0 = -416, KZ1 = -405, LEG = 198, LOT_Z0 = ACC_Z1 + 1, LOT_Z1 = KZ0 - 5;
    /** The spur roads south off the access road, each eight wide from its west edge, down to these. */
    static final int FIRE_X = AirportFire.HX1 - 1, FIRE_Z = AirportFire.Z0 - 31;
    static final int CARGO_X = AirportCargo.FX0 - 20, CARGO_Z = AirportCargo.CZ0 - 31;
    static final int FUEL_X = AirportCargo.FX1 + 19, FUEL_Z = AirportCargo.FZ0 - 4;
    static final int HANGAR_X = 593, HANGAR_Z = AirportHangars.BZ0 - 1;
    /** Everything this builds lies within these (in the frame). */
    static final int BX0 = ACC_X0, BX1 = HANGAR_X + 8, BZ0 = ACC_Z0 - 12, BZ1 = KZ1;

    /** The cars the travellers came in. */
    private static final String CARS = "sedan,sedan,suv,suv,pickup,hatchback,minivan,wagon,coupe,sedan_ltd,convertible,van";

    AirportLandside(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.3, 0.0, 0.2);
    }

    void build() {
        roads();
        lot();
        island();
        gate();
        lights();
        queue();
    }

    private String asphalt() {
        double r = chance();
        return r < 0.08 ? "asphalt_crack" : r < 0.12 ? "asphalt_patch" : r < 0.14 ? "asphalt_moss" : "asphalt";
    }

    /** A strip of road x0..x1 by z0..z1, its kerbs, the centre line down it along x or z. */
    private void road(int x0, int x1, int z0, int z1, boolean alongX) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, asphalt());
            }
        }
        if (alongX) {
            int cz = (z0 + z1 + 1) / 2;
            for (int x = x0; x <= x1; x++) {
                if (Math.floorMod(x, 6) < 3) {
                    set(x, 0, cz, "road_line_yellow[axis=x]");
                }
            }
        } else {
            int cx = (x0 + x1 + 1) / 2;
            for (int z = z0; z <= z1; z++) {
                if (Math.floorMod(z, 6) < 3) {
                    set(cx, 0, z, "road_line_yellow[axis=z]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the roads

    private void roads() {
        // the access road from the gate to the east leg, on to the hangars
        road(ACC_X0, HANGAR_X + 7, ACC_Z0, ACC_Z1, true);
        // the legs round the lot to the kerb road, and the kerb road itself
        road(-LEG, -LEG + 7, ACC_Z1 + 1, KZ0 - 1, false);
        road(LEG - 7, LEG, ACC_Z1 + 1, KZ0 - 1, false);
        for (int x = -LEG; x <= LEG; x++) {
            for (int z = KZ0; z <= KZ1; z++) {
                set(x, 0, z, asphalt());
            }
            if (Math.floorMod(x, 6) < 3) {
                set(x, 0, KZ0 + 3, "road_line_white[axis=x]");
                set(x, 0, KZ0 + 7, "road_line_white[axis=x]");
            }
            set(x, 0, KZ1, "road_line_yellow[axis=x]");
        }
        // the crossings from the island to the doors
        for (int cx : new int[] {-96, -24, 48, 120}) {
            for (int x = cx; x < cx + 4; x++) {
                for (int z = KZ0; z <= KZ1; z++) {
                    set(x, 0, z, Math.floorMod(x, 2) == 0 ? "road_line_white[axis=z]" : "asphalt");
                }
            }
        }
        // the spurs: the fire station's car park, the cargo yard, the fuel farm's gate, the hangars' back
        road(FIRE_X, FIRE_X + 7, ACC_Z1 + 1, FIRE_Z, false);
        road(CARGO_X, CARGO_X + 7, ACC_Z1 + 1, CARGO_Z, false);
        road(FUEL_X, FUEL_X + 5, ACC_Z1 + 1, FUEL_Z, false);
        road(HANGAR_X, HANGAR_X + 7, ACC_Z1 + 1, HANGAR_Z, false);
        // the path to the tower's door from the kerb's east end
        for (int x = AirportGrounds.TX - 1; x <= AirportGrounds.TX + 2; x++) {
            for (int z = KZ1 + 1; z < AirportGrounds.TZ - AirportGrounds.BASE - 3; z++) {
                set(x, 0, z, chance() < 0.12 ? "sidewalk_cracked" : "sidewalk");
            }
        }
        for (int x = LEG + 1; x < AirportGrounds.TX - 1; x++) {
            for (int z = KZ1 - 3; z <= KZ1; z++) {
                set(x, 0, z, chance() < 0.12 ? "sidewalk_cracked" : "sidewalk");
            }
        }
    }

    // ------------------------------------------------------------------ the lot

    /**
     * The surface lot between the legs: two aisles along x, a row of bays three wide either side of
     * each, the lamp island between the pairs; the cars the evacuees left, most bays full.
     */
    private void lot() {
        int x0 = -LEG + 8, x1 = LEG - 8;
        int[] aisles = {LOT_Z0 + 6, LOT_Z0 + 27};
        for (int x = x0; x <= x1; x++) {
            for (int z = LOT_Z0; z <= LOT_Z1; z++) {
                set(x, 0, z, asphalt());
            }
        }
        for (int a : aisles) {
            for (int side : new int[] {-1, 1}) {
                // the bay row: z from the aisle's edge six deep, a line every three
                int zr0 = side < 0 ? a - 6 : a + 7, zr1 = zr0 + 5;
                for (int x = x0; x <= x1; x++) {
                    if (Math.floorMod(x - x0, 3) == 0) {
                        for (int z = zr0; z <= zr1; z++) {
                            set(x, 0, z, "road_line_white[axis=z]");
                        }
                    }
                }
                for (int x = x0 + 1; x < x1 - 1; x += 3) {
                    if (chance() < 0.35) {
                        float yaw = side < 0 ? 180f : 0f;
                        p.vehicle(x + 1.0, 1, (zr0 + zr1 + 1) / 2.0, yaw + (float) (chance() * 10 - 5), CARS, rng.nextLong());
                    }
                }
            }
        }
        // the islands between the bay rows: a kerb, grass, the lamp standards
        int iz = aisles[0] + 13;
        for (int x = x0; x <= x1; x++) {
            for (int z = iz; z <= iz + 1; z++) {
                set(x, 0, z, Math.floorMod(x - x0, 30) == 0 ? "sidewalk" : "minecraft:grass_block");
                if (chance() < 0.2) {
                    set(x, 1, z, chance() < 0.5 ? "weeds" : "grass_tuft");
                }
            }
        }
        // the pay stations at the lot's ends, the sign
        for (int x : new int[] {x0 + 1, x1 - 1}) {
            set(x, 1, iz, "gs_payphone_post");
        }
        mess(x0, LOT_Z0, x1, LOT_Z1, 0, 0.01);
    }

    /**
     * The ground transport island between the lot and the kerb road: its sidewalk, the shuttle's
     * shelters and stop, the taxi rank at its east end, the shuttle bus at the kerb.
     */
    private void island() {
        for (int x = -LEG + 8; x <= LEG - 8; x++) {
            for (int z = LOT_Z1 + 1; z < KZ0; z++) {
                set(x, 0, z, chance() < 0.12 ? "sidewalk_cracked" : "sidewalk");
            }
            set(x, 0, KZ0 - 1, "curb");
        }
        // two shelters, their backs to the lot, the benches in them, the stop between
        for (int sx : new int[] {-30, -14}) {
            for (int x = sx; x < sx + 8; x++) {
                set(x, 1, LOT_Z1 + 1, "ah_shelter_back[facing=south]");
                set(x, 2, LOT_Z1 + 1, "ah_shelter_back[facing=south]");
                set(x, 3, LOT_Z1 + 1, "ah_shelter_roof[facing=south]");
                set(x, 3, LOT_Z1 + 2, "ah_shelter_roof[facing=south]");
                if (Math.floorMod(x - sx, 4) == 1) {
                    set(x, 1, LOT_Z1 + 2, "tx_bench[facing=south]");
                }
            }
            for (int y = 1; y <= 2; y++) {
                set(sx, y, LOT_Z1 + 2, "ah_shelter_side[facing=south]");
                set(sx + 7, y, LOT_Z1 + 2, "ah_shelter_side[facing=north]");
            }
        }
        set(-20, 1, KZ0 - 2, "ah_stop");
        set(-20, 2, KZ0 - 2, "ah_stop");
        set(-20, 3, KZ0 - 2, "ah_stop_sign[facing=south]");
        p.vehicle(-22.0, 1, KZ0 + 1.5, 270f, "city_bus", rng.nextLong());
        // the taxi rank: the sign, the cabs nose to tail at the kerb
        set(60, 1, KZ0 - 2, "ah_stop");
        set(60, 2, KZ0 - 2, "ah_stop");
        set(60, 3, KZ0 - 2, "sign_post");
        for (int k = 0; k < 4; k++) {
            if (chance() < 0.7) {
                p.vehicle(66.0 + k * 7, 1, KZ0 + 1.5, 270f, "taxi", rng.nextLong());
            }
        }
        for (int x = -LEG + 10; x < LEG - 8; x += 9) {
            if (chance() < 0.4) {
                set(x, 1, KZ0 - 2, chance() < 0.2 ? "ext_trash_can_tipped" : "ext_trash_can");
            }
        }
        mess(-LEG + 8, LOT_Z1 + 1, LEG - 8, KZ0 - 1, 0, 0.05);
    }

    // ------------------------------------------------------------------ the gate

    /** The gatehouse in the west fence: the booth by the road, its barriers up, the guide sign. */
    private void gate() {
        int gx = ACC_X0 + 10, gz0 = ACC_Z0 - 6, gz1 = ACC_Z0 - 2;
        for (int x = gx; x <= gx + 4; x++) {
            for (int z = gz0; z <= gz1; z++) {
                boolean wall = x == gx || x == gx + 4 || z == gz0 || z == gz1;
                set(x, 0, z, wall ? "concrete_dark" : "un_slab_vct");
                for (int y = 1; y < 4; y++) {
                    String out = z == gz1 ? "south" : z == gz0 ? "north" : x == gx ? "west" : "east";
                    boolean corner = (x == gx || x == gx + 4) && (z == gz0 || z == gz1);
                    set(x, y, z, !wall ? "air" : y == 2 && !corner ? (chance() < 0.3 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]" : "ml_precast");
                }
                set(x, 4, z, "un_roof");
                set(x, 5, z, wall ? "ml_precast_band" : "air");
            }
        }
        set(gx + 2, 1, gz0, "air");
        set(gx + 2, 2, gz0, "air");
        door(gx + 2, 0, gz0, Doors.FLAT, "north");
        set(gx + 1, 1, gz1 - 1, "store_counter[facing=south]");
        set(gx + 3, 1, gz1 - 1, "store_chair[facing=south]");
        set(gx + 1, 1, gz0 + 1, "home_radio_low");
        // the barriers raised, the striped arms standing up over their housings, one each way
        for (int[] b : new int[][] {{gx + 6, ACC_Z0 - 1}, {gx + 6, ACC_Z1 + 1}}) {
            set(b[0], 1, b[1], "cp_boom_post[facing=north]");
            for (int y = 2; y <= 6; y++) {
                set(b[0], y, b[1], (y == 6 ? "cp_boom_cap" : "cp_boom_up") + "[facing=north]");
            }
        }
        // the guide sign for drivers coming in, on its two posts south of the road, read facing east
        int sx = gx + 16, sz = ACC_Z1 + 2;
        for (int y = 1; y <= 4; y++) {
            set(sx, y, sz, "streetlight_pole");
            set(sx, y, sz + 3, "streetlight_pole");
        }
        for (int i = 0; i < 4; i++) {
            set(sx, 6, sz + i, "ah_guide_" + i + "[facing=west]");
            set(sx, 5, sz + i, "ah_guide_" + (4 + i) + "[facing=west]");
        }
    }

    // ------------------------------------------------------------------ the lights and the queue

    /** Lamp standards down the access road's north side every thirty, along the kerb road and through the lot. */
    private void lights() {
        for (int x = ACC_X0 + 30; x < HANGAR_X; x += 30) {
            standard(x, ACC_Z0 - 1, 2);
        }
        for (int x = -LEG + 8; x <= LEG - 8; x += 30) {
            standard(x, LOT_Z0 + 13 + 6, 0);
        }
    }

    /** A lamp standard at (x, z), its arm reaching toward (0 north, 2 south). */
    private void standard(int x, int z, int toward) {
        int[][] dir = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        int h = 8;
        if (chance() < 0.06) {
            for (int k = 0; k < 5; k++) {
                set(x + k, 1, z, "streetlight_pole");
            }
            return;
        }
        for (int y = 1; y <= h; y++) {
            set(x, y, z, "streetlight_pole");
        }
        int ax = x + dir[toward][0], az = z + dir[toward][1];
        set(ax, h, az, "streetlight_arm[facing=" + ((toward & 1) == 1 ? "east" : "south") + "]");
        set(ax + dir[toward][0], h, az + dir[toward][1], (power && chance() < 0.5 ? "streetlight_head" : "streetlight_head_off")
                + "[facing=" + new String[] {"south", "west", "north", "east"}[toward] + "]");
    }

    /** The evacuees' queue: cars nose to tail inbound down the access road, some on the verge, doors open. */
    private void queue() {
        for (double x = -330; x < -LEG - 4; x += 6.5 + chance() * 2) {
            if (chance() < 0.75) {
                double z = ACC_Z1 - 1.5 + (chance() < 0.1 ? 3 : 0);
                p.vehicle(x, 1, z, 270f + (float) (chance() * 16 - 8), CARS, rng.nextLong());
            }
            if (chance() < 0.25) {
                set((int) x + 2, 1, ACC_Z1 + 2, any("ap_bag_up[facing=east]", "ap_bag_flat[facing=north]", "trash_bag[facing=south]", "paper"));
            }
        }
    }
}
