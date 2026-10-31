package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The Pine Ridge Welcome Center (Josiah, 2026-10-31: the highway places, the rest area first), the
 * rest area on I-95 where it comes into the county from the north, after refs/roads/rest-area: the
 * Nevada stop's ledgestone towers and timber portico, the Wasta stop's lawns and shelters, the
 * Louisiana and Alberta welcome boards.
 * <ul>
 * <li>the interstate down the middle (x {@link #HX}), the southbound exit off its right lane and
 * the entrance back on, the overhead gantry where the ramp leaves: REST AREA for the southbound,
 * NEXT REST AREA 38 MILES for the northbound;</li>
 * <li>the welcome board on the verge as you come in, brown and routed, INFECTED sprayed across it;</li>
 * <li>the car park: the aisle down from the ramp, the stalls either side, the walk along the
 * front; the truck loop round the back with the rigs parked along it;</li>
 * <li>the welcome center: ledgestone walls, the two towers at its front corners, the timber
 * portico; inside, the lobby with the lit map cases, the visitor desk, the vending alcove, the
 * men's and women's rooms either side;</li>
 * <li>the pet area fenced north of it, the picnic grove south of it with its three shelters,
 * tables and grills, the lamps, the woods along the east verge, the call box and the mile marker.</li>
 * </ul>
 * The state closed it when the roads jammed: the drums across the ramp, the orange board, the cars
 * that came in anyway nose to tail down the aisle, a camp in the picnic grove that didn't last.
 *
 * <p>The place is {@link #W} by {@link #D}, its north and south gates on the interstate.
 */
final class RestArea extends Site {
    static final int W = 180, D = 240;
    /** The interstate's centre line; the aisle, the truck lane, the building. */
    static final int HX = W / 2, AX = 66, TX = 14, BX0 = 24, BX1 = 50, BZ0 = 100, BZ1 = 140;

    private final Placer a;

    RestArea(Place p, BlockSink out) {
        super(p, out);
        this.a = new Placer(out, 0, 0, 0, 0);
    }

    @Override
    void build() {
        lawn();
        Lanes hwy = new Lanes.Path(HX, -2, 0).to(HX, d + 1, 0).lanes(Lanes.Style.HIGHWAY, 7);
        double lane = HX - 5.25;
        Lanes in = new Lanes.Path(lane, 6, 0).curve(lane, 26, AX, 30, AX, 52, 0).lanes(Lanes.Style.RAMP, 3.5);
        Lanes aisle = new Lanes.Path(AX, 50, 0).to(AX, 190, 0).lanes(Lanes.Style.PLAIN, 3);
        Lanes back = new Lanes.Path(AX, 188, 0).curve(AX, 210, lane, 204, lane, 232, 0).lanes(Lanes.Style.RAMP, 3.5);
        Lanes trucks = new Lanes.Path(AX - 3, 60, 0).to(TX + 12, 60, 0).curve(TX + 4, 60, TX, 64, TX, 72, 0).to(TX, 178, 0)
                .curve(TX, 186, TX + 4, 190, TX + 12, 190, 0).to(AX - 3, 190, 0).lanes(Lanes.Style.RAMP, 3.5);
        List<Lanes> all = List.of(hwy, in, aisle, back, trucks);
        Lanes.paint(out, all, w, d, rng);
        stalls();
        truckPark();
        walks();
        building();
        petArea();
        picnic();
        signs(hwy);
        Hwy.lamps(out, hwy, 20, d, 44, true, w, d);
        Hwy.lamps(out, hwy, 42, d, 44, false, w, d);
        Hwy.lamps(out, aisle, 10, 140, 30, false, w, d);
        Hwy.lamps(out, trucks, 20, trucks.length(), 36, false, w, d);
        woods();
        story(hwy, in, aisle);
    }

    /** Mown once, gone to seed: grass, clover, dry patches, weeds. */
    private void lawn() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double r = chance();
                boolean bare = Noise.hash(p.seed, x / 5, z / 5) % 9 == 0 && r < 0.6;
                out.set(x, 0, z, bare ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                if (r > 0.82) {
                    out.set(x, 1, z, r > 0.97 ? "weeds_tall" : r > 0.93 ? "weeds" : r > 0.88 ? "minecraft:tall_grass[half=lower]" : "minecraft:short_grass");
                    if (r > 0.88 && r <= 0.93) {
                        out.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                    }
                }
            }
        }
    }

    /** The car stalls either side of the aisle: lines every three, the stop blocks, a curb behind. */
    private void stalls() {
        for (int z = 64; z <= 186; z++) {
            for (int x = AX - 10; x <= AX + 9; x++) {
                if (x >= AX - 4 && x <= AX + 3) {
                    continue;  // the aisle
                }
                boolean line = z % 3 == 0;
                out.set(x, 0, z, line ? "park_line[axis=x]" : Lanes.weathered(rng, 0.2));
                out.set(x, 1, z, "air");
                out.set(x, 2, z, "air");
            }
            out.set(AX - 11, 0, z, "curb");
            out.set(AX + 10, 0, z, "curb");
        }
        // the stalls' ends: the curb along the front walk, the verge on the east
        for (int z = 64; z <= 186; z++) {
            if (z % 3 != 0) {
                if (chance() < 0.7) {
                    out.set(AX - 9, 1, z, "dn_wheelstop[axis=z]");
                }
                if (chance() < 0.7) {
                    out.set(AX + 8, 1, z, "dn_wheelstop[axis=z]");
                }
            }
        }
    }

    /** The rigs' strip along the back of the truck loop. */
    private void truckPark() {
        for (int z = 74; z <= 176; z++) {
            for (int x = TX - 11; x <= TX - 4; x++) {
                out.set(x, 0, z, (z - 74) % 18 == 0 ? "park_line[axis=x]" : Lanes.weathered(rng, 0.25));
                out.set(x, 1, z, "air");
                out.set(x, 2, z, "air");
            }
            out.set(TX - 12, 0, z, "curb");
        }
        for (int k = 0; k < 5; k++) {
            int z = 83 + k * 18;
            if (chance() < 0.75) {
                out.vehicle(TX - 7.5, 1, z, 0f, chance() < 0.5 ? "box_truck" : chance() < 0.5 ? "moving_truck" : "step_van", rng.nextLong());
            }
        }
    }

    /** The walk along the front of the building and down to the grove and the pet area, the portico's floor. */
    private void walks() {
        for (int z = 64; z <= 188; z++) {
            for (int x = BX1 + 1; x <= AX - 12; x++) {
                out.set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
                out.set(x, 1, z, "air");
            }
        }
        // the paths out to the shelters and round the pet area
        for (int x = BX0 - 2; x <= BX1; x++) {
            for (int z : new int[] {96, 97, 143, 144}) {
                out.set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
            }
        }
        for (int z = 143; z <= 186; z++) {
            out.set(BX0 - 2, 0, z, "sidewalk");
            out.set(BX0 - 1, 0, z, "sidewalk");
        }
    }

    // ------------------------------------------------------------------ the welcome center

    private void building() {
        Bldg b = new Bldg(a, rng);
        b.damage = 0.35;
        int x0 = BX0, x1 = BX1, z0 = BZ0, z1 = BZ1, top = 6, cz = (z0 + z1) / 2;
        b.walls(x0, z0, x1, z1, 1, top - 1, "hw_ledge");
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                a.set(x, 0, z, "concrete_dark");
            }
        }
        b.floor(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, "lobby_floor_white");
        b.clear(x0 + 1, 1, z0 + 1, x1 - 1, top - 1, z1 - 1);
        b.flat(x0, z0, x1, z1, top, "hw_ledge", 1, "stone_trim");
        // the capstone band round the walls at the head of the windows
        for (int x = x0; x <= x1; x++) {
            a.set(x, top - 1, z0, "hw_capstone");
            a.set(x, top - 1, z1, "hw_capstone");
        }
        for (int z = z0; z <= z1; z++) {
            a.set(x0, top - 1, z, "hw_capstone");
            a.set(x1, top - 1, z, "hw_capstone");
        }
        // the towers at the front corners, taller than the roof, capped
        for (int[] t : new int[][] {{x1 - 2, z0 - 1}, {x1 - 2, z1 - 1}}) {
            for (int x = t[0]; x <= t[0] + 3; x++) {
                for (int z = t[1]; z <= t[1] + 2; z++) {
                    for (int y = 1; y <= 11; y++) {
                        a.set(x, y, z, y == 11 ? "hw_capstone" : "hw_ledge");
                    }
                }
            }
        }
        // the interior: partitions for the restrooms, the lobby between
        int mz = z0 + 12, wz = z1 - 12;
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = 1; y < top; y++) {
                a.set(x, y, mz, "plaster_cream");
                a.set(x, y, wz, "plaster_cream");
            }
        }
        // front: the glass doors in the middle, picture windows either side, small windows to the rooms
        b.doors(x1, 1, cz, "shop_door", "east");
        b.lit = 0.0;
        for (int z = mz + 2; z <= wz - 2; z++) {
            if (Math.abs(z - cz) > 1 && Math.abs(z - cz - 1) > 0) {
                b.window(x1, 1, z, "east", 3, null);
            }
        }
        for (int z : new int[] {z0 + 4, z0 + 8, z1 - 4, z1 - 8}) {
            b.window(x1, 3, z, "east", 1, null);
            b.window(x0, 3, z, "west", 1, null);
        }
        for (int z = mz + 3; z <= wz - 3; z += 4) {
            b.window(x0, 2, z, "west", 2, null);
        }
        // the restroom doors off the lobby, their signs
        int dx = x1 - 6;
        Doors.abandoned(a, rng, dx, 1, mz, "flat_door", "south", Doors.hinge(rng));
        Doors.abandoned(a, rng, dx, 1, wz, "flat_door", "north", Doors.hinge(rng));
        a.set(dx + 1, 3, mz + 1, "gs_sign_restrooms[facing=south]");
        a.set(dx + 1, 3, wz - 1, "gs_sign_restrooms[facing=north]");
        restroom(x0 + 1, z0 + 1, x1 - 1, mz - 1, true);
        restroom(x0 + 1, wz + 1, x1 - 1, z1 - 1, false);
        lobby(x0 + 1, mz + 1, x1 - 1, wz - 1, cz);
        portico(x1, z0 + 2, z1 - 2, top);
        b.lights(x0 + 1, mz + 1, x1 - 1, wz - 1, top - 1, 0.0);
    }

    private void restroom(int x0, int z0, int x1, int z1, boolean men) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                a.set(x, 0, z, "cv_hex_tile");
            }
        }
        // the stalls along the back wall, the urinals (men) and sinks along the inner
        int back = men ? z0 : z1, in = men ? z1 : z0;
        String toward = men ? "south" : "north";
        for (int x = x0 + 1; x <= x0 + 11; x += 2) {
            a.set(x, 1, back, "fit_toilet_stall[facing=" + toward + "]");
        }
        for (int z = Math.min(back, in) + 2; z <= Math.max(back, in) - 2; z += 2) {
            a.set(x0, 1, z, "home_sink_white[facing=east]");
            a.set(x0, 2, z, "home_mirror[facing=east]");
            a.set(x0, 3, z + 1, "hw_dryer[facing=east]");
        }
        if (men) {
            for (int x = x0 + 14; x <= x1 - 8; x += 2) {
                a.set(x, 1, back, "hw_urinal[facing=south]");
            }
        }
        a.set((x0 + x1) / 2, 5, (z0 + z1) / 2, "ceiling_light_off");
        new Bldg(a, rng).mess(x0, z0, x1, z1, 1, 0.07);
    }

    private void lobby(int x0, int z0, int x1, int z1, int cz) {
        // the map cases down the middle, facing the doors
        for (int z = cz - 4; z <= cz + 4; z += 4) {
            a.set(x0 + 9, 1, z, "hw_map_lo[facing=east]");
            a.set(x0 + 9, 2, z, "hw_map_hi[facing=east]");
        }
        // the visitor desk on the back wall, its sign over it, the brochure racks
        for (int z = cz - 3; z <= cz + 3; z++) {
            a.set(x0 + 1, 1, z, "gs_counter_g[facing=east]");
        }
        sign("info", 2, 1, x0, 3, cz - 1, "east");
        a.set(x0, 2, cz - 5, "ap_mag_rack[facing=east]");
        a.set(x0, 2, cz + 5, "ap_mag_rack[facing=east]");
        // the vending alcove on the north side, the benches facing it, the fountain
        for (int x = x0 + 12; x <= x0 + 17; x++) {
            a.set(x, 1, z0, (x % 3 == 0 ? "store_vending" : "pk_vending") + "[facing=south]");
            if (x % 3 == 0) {
                a.set(x, 2, z0, "store_vending_top[facing=south]");
            }
        }
        a.set(x0 + 19, 1, z0, "st_fountain[facing=south]");
        for (int x = x0 + 12; x <= x0 + 17; x++) {
            a.set(x, 1, z0 + 5, "tx_bench[facing=north]");
            a.set(x, 1, z1 - 3, "tx_bench[facing=south]");
        }
        a.set(x1 - 1, 1, z0 + 1, "pk_trash_can[facing=west]");
        a.set(x1 - 1, 1, z1 - 1, "pk_trash_can[facing=west]");
        // the ones who sheltered here: sleeping bags on the floor, a barricade of benches at the doors that didn't hold
        for (int i = 0; i < 6; i++) {
            int x = x0 + 2 + rng.nextInt(Math.max(1, x1 - x0 - 4)), z = z0 + 1 + rng.nextInt(Math.max(1, z1 - z0 - 2));
            if (a.get(x, 1, z) == null) {
                a.set(x, 1, z, chance() < 0.5 ? "fm_bedroll[facing=" + Hwy.FACING[rng.nextInt(4)] + "]" : "trash_bag[facing=north]");
            }
        }
        a.set(x1 - 2, 1, cz - 2, "tx_bench[facing=west]");
        a.set(x1 - 3, 1, cz + 2, "tx_bench[facing=north]");
        new Bldg(a, rng).mess(x0, z0, x1, z1, 1, 0.08);
    }

    /** The portico along the front: timber posts on stone bases, the beams, the rafters, the shade roof. */
    private void portico(int x1, int z0, int z1, int top) {
        int px = x1 + 5;
        for (int z = z0; z <= z1; z++) {
            boolean post = (z - z0) % 4 == 0;
            if (post) {
                a.set(px, 1, z, "hw_ledge");
                for (int y = 2; y < top; y++) {
                    a.set(px, y, z, "hw_timber_post");
                }
            }
            a.set(px, top, z, "hw_timber_beam[axis=z]");
            if (z % 2 == 0) {
                for (int x = x1 + 1; x <= px + 1; x++) {
                    a.set(x, top, z, "hw_timber_beam[axis=x]");
                }
            }
            for (int x = x1 + 1; x <= px + 1; x++) {
                a.set(x, top + 1, z, chance() < 0.08 ? "air" : "roof");
            }
        }
    }

    // ------------------------------------------------------------------ the grounds

    private void petArea() {
        int x0 = BX0, x1 = BX1 - 2, z0 = 70, z1 = 93;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                if (edge) {
                    boolean post = (x + z) % 4 == 0;
                    String axis = z == z0 || z == z1 ? "x" : "z";
                    a.set(x, 1, z, post ? "yd_picket_post_white[axis=" + axis + "]"
                            : chance() < 0.08 ? "yd_picket_white_broken[axis=" + axis + "]" : "yd_picket_white[axis=" + axis + "]");
                } else if (chance() < 0.25) {
                    a.set(x, 0, z, "minecraft:coarse_dirt");
                }
            }
        }
        a.set(x1, 1, z1 - 3, "yd_picket_gate_white_open[axis=z]");
        a.set(x1 - 1, 1, z1 - 5, "hw_bags[facing=east]");
        a.set(x0 + 4, 1, z1 - 1, "pk_trash_can[facing=north]");
        sign("pet", 1, 1, x1 + 1, 2, z1 - 6, "east");
    }

    /** Three shelters down the grove, tables and grills under and round them, the trees. */
    private void picnic() {
        for (int k = 0; k < 3; k++) {
            int sx = BX0 + 2, sz = 148 + k * 13;
            shelter(sx, sz, 9, 7);
            for (int t = 0; t < 2; t++) {
                table(sx + 2 + t * 4, sz + 3, chance() < 0.15);
            }
            a.set(sx + 10, 1, sz + 3, "hw_grill[facing=west]");
            a.set(sx - 1, 1, sz + 1, "pk_trash_can[facing=east]");
        }
        // tables out on the grass under the trees
        for (int i = 0; i < 5; i++) {
            int x = BX1 - 4 - rng.nextInt(8), z = 150 + rng.nextInt(34);
            if (a.get(x, 1, z) == null && a.get(x + 1, 1, z) == null) {
                table(x, z, chance() < 0.3);
            }
        }
        for (int i = 0; i < 9; i++) {
            int x = BX0 - 3 + rng.nextInt(BX1 - BX0 + 4), z = 146 + rng.nextInt(42);
            if (a.get(x, 1, z) == null) {
                tree(x, z);
            }
        }
    }

    private void shelter(int x0, int z0, int sw, int sd) {
        for (int x = x0; x < x0 + sw; x++) {
            for (int z = z0; z < z0 + sd; z++) {
                a.set(x, 0, z, chance() < 0.1 ? "floor_concrete_crack" : "floor_concrete");
            }
        }
        for (int[] c : new int[][] {{x0, z0}, {x0 + sw - 1, z0}, {x0, z0 + sd - 1}, {x0 + sw - 1, z0 + sd - 1}}) {
            a.set(c[0], 1, c[1], "hw_ledge");
            for (int y = 2; y <= 3; y++) {
                a.set(c[0], y, c[1], "hw_timber_post");
            }
        }
        Bldg b = new Bldg(a, rng);
        b.roofGable(x0, x0 + sw - 1, z0, z0 + sd - 1, 4, "tin_green", false, "air", 1);
    }

    /** A picnic table along x from (x, z), two blocks; upended now and then. */
    private void table(int x, int z, boolean over) {
        if (over) {
            a.set(x, 1, z, "hw_picnic_l[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
            return;
        }
        a.set(x, 1, z, "hw_picnic_l[facing=north]");
        a.set(x + 1, 1, z, "hw_picnic_r[facing=north]");
    }

    private void tree(int x, int z) {
        double r = chance();
        Trees.grow(Kit.canopy(a), r < 0.4 ? Trees.Kind.OAK : r < 0.7 ? Trees.Kind.MAPLE : r < 0.85 ? Trees.Kind.PINE : Trees.Kind.SICK, x, 1, z, rng);
    }

    /** The woods along the east verge and in the corners, thicker further from the road. */
    private void woods() {
        for (int i = 0; i < 70; i++) {
            int x = HX + 14 + rng.nextInt(w - HX - 16), z = 2 + rng.nextInt(d - 4);
            double r = chance();
            if (a.get(x, 1, z) == null && r < 0.3 + (x - HX - 14) / (double) (w - HX) * 0.7) {
                Trees.grow(Kit.canopy(a), chance() < 0.55 ? Trees.Kind.PINE : chance() < 0.5 ? Trees.Kind.OAK : Trees.Kind.DEAD, x, 1, z, rng);
            }
        }
        for (int i = 0; i < 14; i++) {
            int x = 1 + rng.nextInt(TX - 12), z = 2 + rng.nextInt(d - 4);
            if (a.get(x, 1, z) == null && (z < 70 || z > 180)) {
                tree(x, z);
            }
        }
        for (int i = 0; i < 8; i++) {
            int x = AX + 11 + rng.nextInt(4), z = 70 + rng.nextInt(110);
            if (a.get(x, 1, z) == null) {
                tree(x, z);
            }
        }
    }

    // ------------------------------------------------------------------ signs and the story

    private void sign(String key, int cols, int rows, int x, int y, int z, String facing) {
        Hwy.sign(out, key, cols, rows, x, y, z, facing, null, null);
    }

    private void signs(Lanes hwy) {
        // the welcome board on the southbound verge, facing the traffic coming in
        boolean spray = chance() < 0.7;
        Hwy.sign(out, spray ? "welcome_q" : "welcome", 6, 2, HX - 15, 2, 4, "north", new int[] {0, 5}, "hw_sign_leg_wood");
        for (int x = HX - 21; x <= HX - 14; x++) {
            out.set(x, 0, 5, "minecraft:coarse_dirt");
            if (chance() < 0.5) {
                out.set(x, 1, 5, chance() < 0.5 ? "weeds" : "minecraft:short_grass");
            }
        }
        // the gantry where the ramp leaves: REST AREA over the southbound, the next one for the northbound
        Hwy.gantry(out, HX - 11, 16, HX + 10, 16, 8);
        Hwy.sign(out, "rest_in", 3, 1, HX - 3, 7, 15, "north", null, null);
        Hwy.sign(out, "next", 4, 2, HX + 2, 7, 17, "south", null, null);
        // the blue board on the ramp, the closed board and the call box, the mile marker
        Hwy.sign(out, "cars", 2, 1, AX + 5, 2, 56, "north", new int[] {1}, "hw_sign_leg");
        Hwy.sign(out, "trucks", 2, 1, AX - 6, 2, 56, "north", new int[] {0}, "hw_sign_leg");
        out.set(HX + 10, 1, 120, "hw_callbox[facing=west]");
        out.set(HX + 10, 1, 60, "hw_marker[facing=west]");
        out.set(HX - 11, 1, 200, "hw_callbox[facing=east]");
        for (int z = 40; z <= 200; z += 60) {
            out.set(HX + 9, 1, z + 7, "hw_delineator");
            out.set(HX - 10, 1, z + 7, "hw_delineator");
        }
    }

    private void story(Lanes hwy, Lanes in, Lanes aisle) {
        // the state shut it: drums across the ramp (half of them knocked flat by the ones who came in anyway), the board
        double[] q = in.point(26);
        for (int s = -3; s <= 3; s += 2) {
            int[] c = in.beside(26, s);
            out.set(c[0], 1, c[1], chance() < 0.45 ? "hw_drum_down[facing=" + Hwy.FACING[rng.nextInt(4)] + "]" : "hw_drum");
        }
        int[] b = in.beside(30, -6);
        Hwy.sign(out, "rest_closed", 3, 1, b[0] + 1, 2, b[1], "north", new int[] {0, 2}, "hw_sign_leg");
        // cars down the aisle where they gave up, more in the stalls, the doors open
        Hwy.jam(out, aisle, 6, 60, 0, 0.3, 0.1, rng);
        for (int z = 65; z <= 185; z += 3) {
            if (chance() < 0.4) {
                out.car(AX - 7.5, 1, z + 1.5, 90f + (float) rng.nextGaussian() * 4, 3.5, 5.5, rng.nextLong());
            }
            if (chance() < 0.4) {
                out.car(AX + 6.5, 1, z + 1.5, 270f + (float) rng.nextGaussian() * 4, 3.5, 5.5, rng.nextLong());
            }
        }
        // the southbound jammed solid past the exit, the northbound nearly empty
        Hwy.jam(out, hwy, 34, d - 6, 5.25, 0.15, 0.12, rng);
        Hwy.jam(out, hwy, 60, d - 6, 1.75, 0.35, 0.12, rng);
        if (chance() < 0.6) {
            double[] c = hwy.point(90 + rng.nextDouble() * 60);
            out.vehicle(c[0] - 3.5, 1, c[1], 0f + (float) rng.nextGaussian() * 30, "burnt", rng.nextLong());
        }
        Hwy.litter(out, hwy, 30, d - 4, 0.05, rng);
        Hwy.litter(out, aisle, 0, aisle.length(), 0.08, rng);
        // the camp in the grove: a tent or two, a ring of stones round a dead fire, bags left behind
        for (int k = 0; k < 2; k++) {
            int x = BX0 + 14 + k * 6, z = 150 + rng.nextInt(28);
            if (a.get(x, 1, z) == null && a.get(x + 1, 1, z) == null) {
                a.set(x, 1, z, "fm_bedroll[facing=north]");
                a.set(x + 1, 1, z, chance() < 0.5 ? "trash_bag[facing=north]" : "cardboard_box[facing=north]");
            }
        }
        List<int[]> ring = new ArrayList<>();
        int fx = BX0 + 12, fz = 186;
        for (int[] c : new int[][] {{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
            ring.add(new int[] {fx + c[0], fz + c[1]});
        }
        for (int[] c : ring) {
            a.set(c[0], 1, c[1], "debris_chunk[facing=north]");
        }
        a.set(fx, 0, fz, "minecraft:coarse_dirt");
    }
}
