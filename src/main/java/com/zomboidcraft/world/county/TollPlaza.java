package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.List;

/**
 * The Westgate Toll Plaza (Josiah, 2026-10-31: the highway places), where the Grayhaven Turnpike
 * (I-88) comes into the county from the west, after refs/roads/toll-plaza: the four lanes fanning
 * out to ten, the islands with their booths and striped curbs, the long canopy over them with its
 * lane lights, the blue name board along its fascia.
 * <ul>
 * <li>the turnpike across the place (z {@link #CZ}), widening from its four lanes to the plaza's ten
 * and back, concrete under the canopy, the lane lines run out from the islands;</li>
 * <li>eleven islands (the median longer), a booth on each between: steel and glass, the pay window,
 * the stool and the till inside, the lane light on its roof; the crash cushions on the noses, the
 * gate arms past the booths, the exact-change baskets;</li>
 * <li>the canopy on its columns, the soffit lights over the lanes, the green arrow or red X over
 * each lane, the CASH / E-PASS / EXACT CHANGE / LANE CLOSED boards under the name board;</li>
 * <li>the toll authority's office north of the plaza, brick, its car park and fence: the count
 * room and its safe, the supervisor's room with the camera monitors and the radio, the lockers;</li>
 * <li>the approach boards (TOLL PLAZA 1/2 MILE, the rates), the high lamps.</li>
 * </ul>
 * The day the county closed, the state police and the guard shut the westbound here: the jersey
 * barriers across it past the booths, the sandbags at the median, the drums, the tent, the cruisers
 * across the gap. The westbound jammed for miles behind it, a few rammed through the arms and
 * didn't get far; the eastbound into Grayhaven stands nearly empty.
 *
 * <p>The place is {@link #W} by {@link #D}, its west and east gates on the turnpike.
 */
final class TollPlaza extends Site {
    static final int W = 260, D = 140;
    /** The turnpike's centre line; islands from z {@link #Z0}, one every 8, four wide. */
    static final int CZ = D / 2, Z0 = CZ - 42, LANES = 10;
    /** The islands' ends (the median's), the booths, the canopy. */
    static final int IX0 = 116, IX1 = 143, MX0 = 104, MX1 = 156, BX0 = 125, BX1 = 130, CX0 = 118, CX1 = 137;
    /** The widening: from the turnpike's half at T0 to the plaza's at T1, back from T2 to T3. */
    private static final int T0 = 50, T1 = 100, T2 = 160, T3 = 210;
    private static final double FULL = 42;
    /** What each lane is, north to south (westbound 0-4, its fast lane by the median; eastbound 5-9). */
    private static final String[] KIND = {"closed", "change", "cash", "epass", "epass", "epass", "epass", "cash", "change", "closed"};

    private final Placer a;

    TollPlaza(Place p, BlockSink out) {
        super(p, out);
        this.a = new Placer(out, 0, 0, 0, 0);
    }

    @Override
    void build() {
        lawn();
        Lanes hwy = new Lanes.Path(-2, CZ, 0).to(w + 1, CZ, 0).lanes(Lanes.Style.HIGHWAY, 7);
        Lanes.paint(out, List.of(hwy), w, d, rng);
        plaza();
        for (int k = 0; k <= LANES; k++) {
            island(k);
        }
        for (int j = 0; j < LANES; j++) {
            gate(j);
        }
        canopy();
        office();
        signs();
        lamps(hwy);
        woods();
        story();
    }

    /** The plaza's half width at x (the turnpike's 7 outside the widening). */
    static double half(int x) {
        double t;
        if (x <= T0 || x >= T3) {
            return 7;
        } else if (x < T1) {
            t = (x - T0) / (double) (T1 - T0);
        } else if (x <= T2) {
            t = 1;
        } else {
            t = (T3 - x) / (double) (T3 - T2);
        }
        return 7 + (FULL - 7) * t * t * (3 - 2 * t);
    }

    private static int islandZ(int k) {
        return Z0 + 8 * k;
    }

    private static int laneZ(int j) {
        return Z0 + 4 + 8 * j;
    }

    private static boolean west(int j) {
        return j < LANES / 2;
    }

    /** Rough grass on the open hills, gone to seed. */
    private void lawn() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                double r = chance();
                boolean bare = Noise.hash(p.seed, x / 6, z / 6) % 11 == 0 && r < 0.55;
                out.set(x, 0, z, bare ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                if (r > 0.78) {
                    out.set(x, 1, z, r > 0.97 ? "weeds_tall" : r > 0.92 ? "weeds" : r > 0.87 ? "minecraft:tall_grass[half=lower]" : "minecraft:short_grass");
                    if (r > 0.87 && r <= 0.92) {
                        out.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                    }
                }
            }
        }
    }

    /** The widening and the plaza: asphalt, concrete under the booths, the shoulders, the lines. */
    private void plaza() {
        for (int x = T0; x <= T3; x++) {
            double h = half(x);
            for (int z = 0; z < d; z++) {
                double off = Math.abs(z + 0.5 - CZ);
                if (off <= h + 0.5) {
                    boolean conc = x >= MX0 && x <= MX1;
                    out.set(x, 0, z, conc ? (chance() < 0.08 ? "floor_concrete_crack" : "concrete_panel") : Lanes.weathered(rng, 0.16));
                    for (int y = 1; y <= 4; y++) {
                        out.set(x, y, z, "air");
                    }
                } else if (off <= h + 2.5 && h > 7.5) {
                    out.set(x, 0, z, chance() < 0.3 ? "minecraft:coarse_dirt" : "minecraft:gravel");
                    out.set(x, 1, z, "air");
                }
            }
        }
        // the lane lines run out from the islands; the double yellow to the median's nose
        for (int k = 0; k <= LANES; k++) {
            int z = islandZ(k) + 1;
            boolean median = k == LANES / 2;
            for (int x = T1; x <= T2; x++) {
                boolean on = median ? x < MX0 || x > MX1 : x < IX0 || x > IX1;
                if (on && Math.abs(z + 1 - CZ) <= half(x) - 1 && chance() < 0.88) {
                    String line = median ? "road_line_yellow[axis=x]" : "road_line_white[axis=x]";
                    out.set(x, 0, z, line);
                    out.set(x, 0, z + 1, line);
                }
            }
        }
        for (int x = T0; x < T1; x++) {
            for (int z = CZ - 1; z <= CZ; z++) {
                out.set(x, 0, z, chance() < 0.86 ? "road_line_yellow[axis=x]" : Lanes.weathered(rng, 0.16));
            }
        }
        for (int x = T2 + 1; x <= T3; x++) {
            for (int z = CZ - 1; z <= CZ; z++) {
                out.set(x, 0, z, chance() < 0.86 ? "road_line_yellow[axis=x]" : Lanes.weathered(rng, 0.16));
            }
        }
    }

    /** Island k: the raised curb, the cushions on the noses that face the traffic, its booth. */
    private void island(int k) {
        boolean median = k == LANES / 2;
        int x0 = median ? MX0 : IX0, x1 = median ? MX1 : IX1, z0 = islandZ(k);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z0 + 3; z++) {
                out.set(x, 1, z, "hw_island");
            }
        }
        // westbound comes in from the east, eastbound from the west
        if (k <= LANES / 2) {
            out.set(x1, 1, z0 + 1, "hw_cushion[facing=east]");
            out.set(x1, 1, z0 + 2, "hw_cushion[facing=east]");
        }
        if (k >= LANES / 2) {
            out.set(x0, 1, z0 + 1, "hw_cushion[facing=west]");
            out.set(x0, 1, z0 + 2, "hw_cushion[facing=west]");
        }
        if (median) {
            Hwy.sign(out, "pay", 2, 1, x1 - 1, 2, z0 + 2, "east", new int[] {0, 1}, "hw_sign_leg");
            Hwy.sign(out, "pay", 2, 1, x0 + 1, 2, z0 + 1, "west", new int[] {0, 1}, "hw_sign_leg");
        }
        if (k > 0 && k < LANES) {
            booth(z0, k);
        }
        // the canopy's columns stand on the islands
        for (int x : new int[] {CX0 + 2, CX1 - 2}) {
            out.set(x, 1, z0 + 1, "st_column_base");
            for (int y = 2; y <= 6; y++) {
                out.set(x, y, z0 + 1, "st_column");
            }
        }
    }

    /**
     * The booth on the island from z0: panels round its two by four (the steel below, the glass
     * above, the glass now and then smashed), the door at its downstream end, the roof and its lamp.
     */
    private void booth(int z0, int k) {
        int zi = z0 + 1;
        boolean westDoor = k <= LANES / 2;
        // its floor the plaza's, a step down from the island
        for (int x = BX0 + 1; x < BX1; x++) {
            for (int z = zi; z <= zi + 1; z++) {
                out.set(x, 1, z, "air");
            }
        }
        for (int x = BX0 + 1; x < BX1; x++) {
            panel(x, z0, "north");
            panel(x, z0 + 3, "south");
        }
        for (int z = zi; z <= zi + 1; z++) {
            boolean door = z == zi;
            if (!(door && westDoor)) {
                panel(BX0, z, "west");
            }
            if (!(door && !westDoor)) {
                panel(BX1, z, "east");
            }
        }
        for (int x = BX0; x <= BX1; x++) {
            for (int z = z0; z <= z0 + 3; z++) {
                if (x == BX0 || x == BX1 || z == z0 || z == z0 + 3) {
                    if ((x == BX0 || x == BX1) && (z == z0 || z == z0 + 3)) {
                        continue;
                    }
                }
                out.set(x, 4, z, "hw_booth_cap");
            }
        }
        out.set(BX0 + 2, 5, zi, chance() < 0.6 ? "hw_booth_lamp_lit" : "hw_booth_lamp");
        // inside: the stool by the window on the lane it served, the till, the paper and the coffee
        String toward = k <= LANES / 2 ? "north" : "south";
        int wz = k <= LANES / 2 ? zi : zi + 1, bz = k <= LANES / 2 ? zi + 1 : zi;
        if (k == LANES / 2) {
            toward = chance() < 0.5 ? "north" : "south";
        }
        out.set(BX0 + 2, 1, wz, "store_register[facing=" + Hwy.FACING[(Hwy.side(toward) + 2) & 3] + "]");
        out.set(BX0 + 3, 1, bz, chance() < 0.3 ? "air" : "office_chair[facing=" + toward + "]");
        if (chance() < 0.5) {
            out.set(BX0 + 1, 1, bz, chance() < 0.5 ? "paper" : "trash_bag[facing=north]");
        }
    }

    /** One wall cell of a booth: the panel in the cell, set against the booth (facing out). */
    private void panel(int x, int z, String facing) {
        boolean broken = chance() < 0.25;
        out.set(x, 1, z, "hw_booth_lo[facing=" + facing + "]");
        out.set(x, 2, z, (broken ? "hw_booth_hi_x" : "hw_booth_hi") + "[facing=" + facing + "]");
        out.set(x, 3, z, "hw_booth_hi[facing=" + facing + "]");
    }

    /**
     * Lane j's fittings: the gate arm past the booth (the E-PASS lanes ran open), the exact-change
     * basket or the receipt machine before it on the booth's island.
     */
    private void gate(int j) {
        boolean wb = west(j);
        int lz = laneZ(j);
        // the booth's island: south of a westbound lane (the driver's side), north of an eastbound
        int iz = wb ? islandZ(j + 1) : islandZ(j) + 3;
        int ax = wb ? BX0 - 2 : BX1 + 2, before = wb ? BX1 + 3 : BX0 - 3;
        String kind = KIND[j];
        if (!kind.equals("epass")) {
            out.set(ax, 1, iz, "hw_arm_box[facing=" + (wb ? "west" : "east") + "]");
            boolean snapped = wb ? chance() < 0.6 : chance() < 0.15;
            for (int z = lz; z <= lz + 3; z++) {
                if (snapped) {
                    if (z == lz + 1 || z == lz + 2) {
                        out.set(ax + (wb ? -1 : 1) * (z - lz), 1, z, "hw_arm_snap[facing=" + Hwy.FACING[rng.nextInt(4)] + "]");
                    }
                } else {
                    out.set(ax, 1, z, "hw_arm[axis=z]");
                }
            }
        }
        String face = wb ? "north" : "south";
        if (kind.equals("change")) {
            out.set(before, 1, iz, "hw_coin[facing=" + face + "]");
        } else if (kind.equals("cash")) {
            out.set(before, 1, iz, "hw_ticket[facing=" + face + "]");
        }
        if (kind.equals("closed")) {
            for (int x = wb ? IX1 + 2 : IX0 - 6; x <= (wb ? IX1 + 6 : IX0 - 2); x += 2) {
                out.set(x, 1, lz + 1 + (x / 2 & 1), chance() < 0.3 ? "hw_drum_down[facing=" + Hwy.FACING[rng.nextInt(4)] + "]" : "hw_drum");
            }
        }
    }

    /** The canopy over the booths, its lights over the lanes, the lane signals and boards, the name. */
    private void canopy() {
        int za = islandZ(0), zb = islandZ(LANES) + 3;
        for (int x = CX0; x <= CX1; x++) {
            for (int z = za; z <= zb; z++) {
                boolean overLane = (z - za) % 8 >= 4;
                boolean lamp = overLane && (z - za) % 8 >= 5 && (z - za) % 8 <= 6 && (x - CX0) % 3 == 1;
                out.set(x, 7, z, lamp ? (chance() < 0.7 ? "hw_canopy_lit" : "hw_canopy") : "hw_canopy_plain");
            }
        }
        for (int j = 0; j < LANES; j++) {
            boolean wb = west(j);
            int lz = laneZ(j);
            boolean shut = KIND[j].equals("closed") || wb && chance() < 0.4;
            boolean lit = chance() < 0.8;
            String sig = "hw_lane_" + (shut ? "stop" : "go") + (lit ? "_lit" : "");
            if (wb) {
                out.set(CX1, 6, lz + 2, sig + "[facing=east]");
                Hwy.sign(out, KIND[j], 2, 1, CX1 + 1, 7, lz + 2, "east", null, null);
            } else {
                out.set(CX0, 6, lz + 1, sig + "[facing=west]");
                Hwy.sign(out, KIND[j], 2, 1, CX0 - 1, 7, lz + 1, "west", null, null);
            }
        }
        // the name board along the top of each face, sprayed over where they turned the westbound back
        Hwy.sign(out, chance() < 0.75 ? "toll_q" : "toll", 10, 1, CX1, 8, CZ - 16, "east", null, null);
        Hwy.sign(out, "toll", 10, 1, CX0, 8, CZ + 16, "west", null, null);
    }

    // ------------------------------------------------------------------ the toll authority's office

    private void office() {
        Bldg b = new Bldg(a, rng);
        b.damage = 0.3;
        int x0 = 110, x1 = 148, z0 = 5, z1 = 17, top = 5, p1 = 122, p2 = 136, cz = 11;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                a.set(x, 0, z, "concrete_dark");
            }
        }
        b.walls(x0, z0, x1, z1, 1, top - 1, "brick_buff");
        b.clear(x0 + 1, 1, z0 + 1, x1 - 1, top - 1, z1 - 1);
        b.floor(x0 + 1, z0 + 1, p1 - 1, z1 - 1, 0, "floor_concrete");
        b.floor(p1 + 1, z0 + 1, p2 - 1, z1 - 1, 0, "home_floor_carpet_blue");
        b.floor(p2 + 1, z0 + 1, x1 - 1, z1 - 1, 0, "lobby_floor_white");
        b.flat(x0, z0, x1, z1, top, "brick_buff", 1, "stone_trim");
        for (int z = z0 + 1; z < z1; z++) {
            for (int y = 1; y < top; y++) {
                a.set(p1, y, z, "plaster_cream");
                a.set(p2, y, z, "plaster_cream");
            }
        }
        // the front on the plaza side: the doors in the middle, windows along, the count room's barred
        b.doors(129, 1, z1, "shop_door", "south");
        b.lit = 0.0;
        for (int x = x0 + 3; x <= x1 - 3; x += 3) {
            if (Math.abs(x - 129) > 2 && x != p1 && x != p2) {
                b.window(x, 2, z1, "south", 2, null);
                b.window(x, 2, z0, "north", 2, null);
            }
        }
        b.door(x0, 1, cz, "flat_door", "west");
        Doors.abandoned(a, rng, p1, 1, z1 - 3, "flat_door", "west", Doors.hinge(rng));
        Doors.abandoned(a, rng, p2, 1, z1 - 3, "flat_door", "east", Doors.hinge(rng));
        countRoom(x0 + 1, z0 + 1, p1 - 1, z1 - 1);
        control(p1 + 1, z0 + 1, p2 - 1, z1 - 1);
        lockers(p2 + 1, z0 + 1, x1 - 1, z1 - 1);
        b.lights(x0 + 1, z0 + 1, p1 - 1, z1 - 1, top - 1, 0.0);
        b.lights(p1 + 1, z0 + 1, p2 - 1, z1 - 1, top - 1, 0.0);
        b.lights(p2 + 1, z0 + 1, x1 - 1, z1 - 1, top - 1, 0.0);
        // the roof: the units, the vents, the hatch, the radio mast
        a.set(x0 + 6, top + 1, z0 + 4, "bd_hvac_a[facing=south]");
        a.set(x0 + 18, top + 1, z0 + 6, "bd_hvac_b[facing=south]");
        a.set(x1 - 8, top + 1, z0 + 3, "bd_vent");
        a.set(x1 - 4, top + 1, z0 + 8, "bd_hatch[facing=south]");
        for (int y = top + 1; y <= top + 9; y++) {
            a.set(x1 - 3, y, z0 + 3, "streetlight_pole");
        }
        lot();
    }

    /** The count room: the safe, the counting tables, the bags, the cabinet. */
    private void countRoom(int x0, int z0, int x1, int z1) {
        a.set(x0, 1, z0, "store_safe[facing=east]");
        a.set(x0, 1, z0 + 1, "store_safe[facing=east]");
        for (int x = x0 + 3; x <= x0 + 7; x++) {
            a.set(x, 1, z0 + 4, "pk_desk_metal[facing=south]");
            a.set(x, 1, z0 + 6, "pk_desk_metal[facing=north]");
            if (chance() < 0.5) {
                a.set(x, 1, z0 + 5, "office_chair[facing=" + (chance() < 0.5 ? "north" : "south") + "]");
            }
        }
        for (int z = z0 + 1; z <= z1 - 1; z += 2) {
            a.set(x1, 1, z, "filing_cabinet[facing=west]");
        }
        a.set(x0 + 1, 1, z1, "store_register[facing=north]");
        new Bldg(a, rng).mess(x0, z0, x1, z1, 1, 0.12);
    }

    /** The supervisor's room: the camera monitors on the desks, the radio, the plaza on the screens. */
    private void control(int x0, int z0, int x1, int z1) {
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            a.set(x, 1, z0, "desk[facing=south]");
            if (x % 2 == 0) {
                a.set(x, 2, z0, "tv_monitor[facing=south]");
            }
            if (x % 3 == 0) {
                a.set(x, 1, z0 + 1, "office_chair[facing=north]");
            }
        }
        a.set(x1, 1, z0 + 3, "bunk_radio_desk[facing=west]");
        a.set(x1 - 1, 1, z0 + 3, "office_chair[facing=east]");
        a.set(x0, 1, z1 - 1, "filing_cabinet[facing=east]");
        a.set(x0, 1, z1 - 2, "filing_cabinet[facing=east]");
        a.set(x0 + 4, 1, z0 + 6, "home_desk_pc[facing=south]");
        a.set(x0 + 4, 1, z0 + 7, "office_chair[facing=north]");
        new Bldg(a, rng).mess(x0, z0, x1, z1, 1, 0.1);
    }

    /** The collectors' room: the lockers, the vending machine, the table where they ate. */
    private void lockers(int x0, int z0, int x1, int z1) {
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            a.set(x, 1, z0, "fit_locker_lo[facing=south]");
            a.set(x, 2, z0, "fit_locker_hi[facing=south]");
        }
        a.set(x1, 1, z1 - 2, "store_vending[facing=west]");
        a.set(x1, 2, z1 - 2, "store_vending_top[facing=west]");
        for (int x = x0 + 3; x <= x0 + 5; x++) {
            a.set(x, 1, z0 + 5, "pk_desk_metal[facing=south]");
        }
        a.set(x0 + 3, 1, z0 + 4, "office_chair[facing=south]");
        a.set(x0 + 5, 1, z0 + 6, "office_chair[facing=north]");
        a.set(x0 + 1, 1, z1 - 1, "pk_trash_can[facing=east]");
        new Bldg(a, rng).mess(x0, z0, x1, z1, 1, 0.1);
    }

    /** The staff car park in front, the fence round the compound, its gate onto the plaza. */
    private void lot() {
        int x0 = 98, x1 = 152, z0 = 19, z1 = 25;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean line = z <= z0 + 4 && (x - x0) % 3 == 0 && x > 108;
                out.set(x, 0, z, line ? "park_line[axis=z]" : Lanes.weathered(rng, 0.2));
                out.set(x, 1, z, "air");
            }
        }
        for (int x = 108; x <= x1; x++) {
            out.set(x, 0, 18, "sidewalk");
            out.set(x, 1, 18, "air");
        }
        for (int x = 110; x <= x1 - 2; x += 3) {
            if (chance() < 0.4) {
                out.car(x + 1.5, 1, z0 + 2, 180f + (float) rng.nextGaussian() * 4, 3.5, 5.0, rng.nextLong());
            }
        }
        Kit.chainlink(a, rng, 96, 2, 154, 2, 3);
        Kit.chainlink(a, rng, 96, 2, 96, z1, 3);
        Kit.chainlink(a, rng, 154, 2, 154, z1, 3);
        Kit.chainlink(a, rng, 110, z1 + 1, 154, z1 + 1, 3);
        // the compound's gate stands open onto the westbound shoulder
        Kit.chainlink(a, rng, 96, z1 + 1, 100, z1 + 1, 3);
    }

    // ------------------------------------------------------------------ signs, lamps, the woods

    private void signs() {
        // TOLL PLAZA 1/2 MILE and the rates on the right verge of each approach
        Hwy.sign(out, "plaza", 4, 2, w - 8, 3, CZ - 10, "east", new int[] {0, 3}, "hw_sign_leg");
        Hwy.sign(out, "rates", 3, 2, T3 + 10, 3, CZ - 10, "east", new int[] {0, 2}, "hw_sign_leg");
        Hwy.sign(out, "plaza", 4, 2, 7, 3, CZ + 10, "west", new int[] {0, 3}, "hw_sign_leg");
        Hwy.sign(out, "rates", 3, 2, T0 - 10, 3, CZ + 10, "west", new int[] {0, 2}, "hw_sign_leg");
        out.set(30, 1, CZ + 11, "hw_callbox[facing=north]");
        out.set(w - 30, 1, CZ - 12, "hw_callbox[facing=south]");
        out.set(w - 50, 1, CZ - 11, "hw_marker[facing=south]");
        for (int x = 12; x < w - 8; x += 40) {
            if (x < T0 - 2 || x > T3 + 2) {
                out.set(x, 1, CZ - 10, "hw_delineator");
                out.set(x, 1, CZ + 10, "hw_delineator");
            }
        }
    }

    private void lamps(Lanes hwy) {
        Hwy.lamps(out, hwy, 6, T0 - 4, 40, true, w, d);
        Hwy.lamps(out, hwy, T3 + 8, w, 40, false, w, d);
        // the high lamps round the plaza's edges, clear of the canopy and the office's lot
        for (int x = T0 + 18; x <= T3 - 18; x += 26) {
            if (x > CX0 - 4 && x < CX1 + 4) {
                continue;
            }
            int e = (int) Math.ceil(half(x)) + 3;
            if (x < 94 || x > 156) {
                Hwy.lamp(out, x, CZ - e, 2, 11);
            }
            Hwy.lamp(out, x, CZ + e - 1, 0, 11);
        }
    }

    private void woods() {
        for (int i = 0; i < 90; i++) {
            int x = 1 + rng.nextInt(w - 2), z = 1 + rng.nextInt(d - 2);
            double off = Math.abs(z + 0.5 - CZ) - half(x);
            if (off < 6 || x > 76 && x < 158 && z < 30 || a.get(x, 1, z) != null) {
                continue;
            }
            if (chance() < 0.25 + Math.min(0.6, off / 50.0)) {
                double r = chance();
                Trees.grow(Kit.canopy(a), r < 0.45 ? Trees.Kind.PINE : r < 0.7 ? Trees.Kind.OAK : r < 0.88 ? Trees.Kind.MAPLE : Trees.Kind.DEAD, x, 1, z, rng);
            }
        }
    }

    // ------------------------------------------------------------------ the checkpoint and the jam

    private void story() {
        int bx = 106;
        // the barriers across the westbound past the booths, a gap in the middle the cruisers closed
        int gap = laneZ(2) + 1;
        for (int z = islandZ(0); z < islandZ(LANES / 2); z++) {
            if (z >= gap && z <= gap + 2) {
                continue;
            }
            out.set(bx, 1, z, chance() < 0.06 ? "debris_chunk[facing=east]" : "jersey_barrier[facing=east]");
        }
        out.vehicle(bx - 3, 1, gap + 1.5, 0f + (float) rng.nextGaussian() * 6, "police_suv", rng.nextLong());
        out.vehicle(bx - 9, 1, laneZ(0) + 2, 90f + (float) rng.nextGaussian() * 10, "jeep,police_sedan", rng.nextLong());
        // the sandbags round the median's nose, the drums ahead of the barriers, the floodlights
        for (int x = MX0 - 5; x <= MX0 - 1; x++) {
            out.set(x, 1, CZ - 6, "sandbags[facing=north]");
            out.set(x, 2, CZ - 6, chance() < 0.7 ? "sandbags[facing=north]" : "air");
        }
        for (int z = CZ - 5; z <= CZ - 2; z++) {
            out.set(MX0 - 6, 1, z, "sandbags[facing=west]");
            out.set(MX0 - 6, 2, z, "sandbags[facing=west]");
        }
        for (int z = islandZ(0) + 2; z < CZ - 6; z += 3) {
            out.set(bx + 4, 1, z, chance() < 0.4 ? "hw_drum_down[facing=" + Hwy.FACING[rng.nextInt(4)] + "]" : "hw_drum");
        }
        out.set(bx - 1, 1, islandZ(0) - 1, "cp_flood_lit[facing=east]");
        out.set(bx - 1, 1, CZ - 7, "cp_flood[facing=east]");
        for (int z = gap - 1; z <= gap + 3; z += 4) {
            out.set(bx + 2, 1, z, "st_cone");
        }
        // their tent off the shoulder by the office fence
        Tents t = new Tents(a, rng);
        t.hit = 0.4;
        t.lit = 0.1;
        t.tent(84, 8, 6, 8, Tents.canvasFor("gp"), "gp", 0.25);
        // the westbound behind it: a queue in every lane back from the booths, the jam on down the turnpike
        Lanes wb = new Lanes.Path(w + 1, CZ, 0).to(-2, CZ, 0).lanes(Lanes.Style.HIGHWAY, 7);
        Hwy.jam(out, wb, 4, w + 1 - T3 + 4, 5.25, 0.08, 0.1, rng);
        Hwy.jam(out, wb, 10, w + 1 - T3 + 4, 1.75, 0.12, 0.1, rng);
        for (int j = 0; j < LANES / 2; j++) {
            double zc = laneZ(j) + 2;
            for (double x = IX1 + 4 + rng.nextDouble() * 3; x < T3 - 4; x += 6.8 + rng.nextDouble() * 2) {
                if (Math.abs(zc - CZ) > half((int) x) - 2) {
                    break;
                }
                if (KIND[j].equals("closed") && x < IX1 + 9) {
                    continue;
                }
                double r = chance();
                float yaw = 90f + (float) rng.nextGaussian() * 5;
                if (r < 0.06) {
                    out.vehicle(x, 1, zc, yaw, "burnt", rng.nextLong());
                } else if (r < 0.12) {
                    out.vehicle(x, 1, zc, yaw, "abandoned", rng.nextLong());
                } else {
                    out.car(x, 1, zc, yaw, 3.5, 5.6, rng.nextLong());
                }
            }
        }
        // the ones who went through the arms and hit the wall
        for (int j = 1; j <= 3; j++) {
            if (chance() < 0.65) {
                out.vehicle(bx + 3.5, 1, laneZ(j) + 2, 90f + (float) rng.nextGaussian() * 25, chance() < 0.4 ? "burnt" : "abandoned", rng.nextLong());
            }
        }
        // the eastbound: a few left in the lanes, a door open
        for (int j = LANES / 2; j < LANES; j++) {
            if (chance() < 0.3) {
                out.car(IX0 - 8 - rng.nextDouble() * 30, 1, laneZ(j) + 2, 270f + (float) rng.nextGaussian() * 8, 3.5, 6.0, rng.nextLong());
            }
        }
        Lanes eb = new Lanes.Path(-2, CZ, 0).to(w + 1, CZ, 0).lanes(Lanes.Style.HIGHWAY, 7);
        Hwy.jam(out, eb, 10, T0 - 6, 1.75, 0.6, 0.2, rng);
        Hwy.litter(out, wb, 0, w, 0.05, rng);
        for (int i = 0; i < 160; i++) {
            int x = T1 + rng.nextInt(T2 - T1), z = islandZ(0) + rng.nextInt(84);
            if (out.get(x, 1, z) == null) {
                out.set(x, 1, z, i % 5 == 0 ? "glass_shards" : i % 5 == 1 ? "trash_bag[facing=north]" : "paper");
            }
        }
    }
}
