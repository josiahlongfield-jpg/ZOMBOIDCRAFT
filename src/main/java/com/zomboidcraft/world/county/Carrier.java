package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The aircraft carrier at a naval wharf, at a supercarrier's real size: 333 blocks over her flight
 * deck and 77 across it, the deck fifteen over the quay (nineteen over the water) and the top of
 * her mast near fifty over that. Drawn in the {@link Ships} frame (the bow toward +x, the keel's
 * line along z = 0, starboard toward +z, heights from the quay's top) with the navy_ kit from
 * tools/city/navy.py:
 * <ul>
 * <li>the hull in haze grey, plated in strakes and butts, the black boot top and the red below
 * it, the bow flared out to the deck's edge, her number on both bows and her name across the
 * stern;</li>
 * <li>the flight deck: the angled landing area to port with its arresting wires, the four
 * catapults with their shuttles and blast deflectors, the deck-edge elevators (one left down at
 * the hangar), the lines, and the wear of tyres, oil and soot; the catwalks round its edge, the
 * sponsons with the guns and launchers (for show), life rafts, antennas, the landing lens;</li>
 * <li>the island to starboard: flight deck control at its foot, offices and the radio room, the
 * navigation bridge and primary flight control over it, the arrays, the mast and radars, her
 * number on both faces;</li>
 * <li>the gallery deck under the flight deck: ready rooms, the air traffic control centre,
 * offices and staterooms, the catapult and arresting gear machinery; the hangar in three bays
 * behind its fire doors; the jet shop aft, the fantail open to the stern, the forecastle with the
 * anchor gear;</li>
 * <li>below: the mess decks and galley, berthing, the sick bay, the brig, the armouries, the
 * magazines of bombs and missiles, the machinery rooms; stair trunks from the bottom to the
 * gallery deck and up the island;</li>
 * <li>alongside: the camels holding her off the wharf, her lines, the brows into the hangar.</li>
 * </ul>
 * Where her jets are parked is kept in {@link #jets}.
 */
final class Carrier {
    /** Her length over the flight deck. */
    static final int LEN = 333;
    /** The flight deck, the gallery deck's floor under it and the hangar deck, from the quay's top. */
    static final int FLIGHT = 15, GALLERY = 11, HANGAR = 2;
    /** The decks under the hangar deck (second, third, fourth), the tank top and the keel. */
    static final int[] DECKS = {-2, -6, -10};
    static final int TANK = -14, KEEL = -16;
    /** The hull proper starts this far forward of the flight deck's aft end. */
    private static final int HX = 4, HULL_LEN = 326, BEAM = 40;
    /** The bow and stern flare out from this height to meet the deck's edge. */
    private static final int FLARE0 = -3;
    /** The flight deck's edges, (x, z) points along the starboard and port sides. */
    private static final int[][] STBD = {{0, 20}, {8, 25}, {24, 29}, {40, 30}, {262, 30}, {280, 29}, {296, 26}, {310, 21},
            {322, 14}, {332, 7}};
    private static final int[][] PORT = {{0, -21}, {10, -27}, {30, -34}, {60, -40}, {100, -45}, {140, -47}, {214, -47},
            {222, -45}, {226, -33}, {240, -31}, {262, -30}, {290, -27}, {310, -22}, {322, -15}, {332, -8}};
    /** The angled deck's centreline z = ANG0 - ANGLE * x from the stern to ANG_END, ANG_HALF either side of it. */
    private static final double ANG0 = -3, ANGLE = 0.158;
    private static final int ANG_END = 222, ANG_HALF = 12;
    private static final int[] WIRES = {48, 60, 72, 84};
    /** The catapults: the track from x0 (where the shuttle waits) forward to x1 along row z. */
    private static final int[][] CATS = {{246, 322, 6}, {242, 316, -9}, {130, 204, -24}, {138, 212, -36}};
    /** The deck-edge elevators: x from and to, and the side (+1 starboard). */
    private static final int[][] LIFTS = {{202, 222, 1}, {170, 190, 1}, {96, 116, 1}, {60, 80, -1}};
    /** The hangar: x from and to, z from and to inside its walls; the fire doors across it. */
    private static final int BAY0 = 54, BAY1 = 262, HZ0 = -17, HZ1 = 16;
    private static final int[] FIRE_DOORS = {126, 196};
    /** The helicopter spot's middle, aft to starboard of the landing area. */
    private static final int PAD_X = 22, PAD_Z = 16;
    /** The island's footprint at the flight deck. */
    private static final int IX0 = 124, IX1 = 155, IZ0 = 20, IZ1 = 28;
    /** The gun sponsons: x, side, and what's on it (0 close-in gun, 1 missile launcher, 2 box launcher). */
    private static final int[][] GUNS = {{300, 1, 0}, {296, -1, 0}, {14, -1, 0}, {18, 1, 0}, {268, -1, 1}, {40, 1, 1},
            {284, 1, 2}, {32, -1, 2}};
    /** The stair trunks from the fourth deck to the gallery deck (their first step's x), up the starboard side. */
    private static final int[] TRUNKS = {40, 147, 243};
    private static final int TRUNK_Z = 15;
    /** The island's stair, from the gallery deck to its top. */
    private static final int ISLAND_STAIR = 138, ISLAND_STAIR_Z = 25;
    /** The wardroom: two frames of the gallery deck's middle band, from WARD_X0 to WARD_X0 + 2 FRAME. */
    private static final int WARD_X0 = 216;
    /** The captain's cabin: the frame from CAPTAIN_X0, starboard on the gallery deck, aft of flight deck control. */
    private static final int CAPTAIN_X0 = 168;
    /** The ship's frames: bulkheads every FRAME from FRAME0. */
    private static final int FRAME0 = 12, FRAME = 12;
    private static final String[] NAMES = {"GRAYHAVEN", "SENTINEL", "ALLEGIANCE", "STEADFAST", "VIGILANT", "LODESTAR"};
    /** A 3 x 5 figure for each digit, rows from the top. */
    private static final String[][] FIGURES = {
        {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
        {"###", "..#", ".##", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
        {"###", "#..", "###", "#.#", "###"}, {"###", "..#", ".#.", ".#.", ".#."}, {"###", "#.#", "###", "#.#", "###"},
        {"###", "#.#", "###", "..#", "###"},
    };
    /** The mask's bounds across her. */
    private static final int Z0 = -50, NZ = 88, NY = FLIGHT - KEEL + 1;

    /** What a compartment is fitted out as. */
    private enum Room { BERTHING, MESS, GALLEY, SICKBAY, BRIG, ARMORY, MAGAZINE, STORES, WORKSHOP, OFFICE, READY, CONTROL,
        STATEROOM, ARREST, CATAPULT, TRUNK, WARDROOM, CAPTAIN, HEAD }

    private interface Picker {
        Room pick(int xa, int xb, int za, int zb);
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final double hit;
    /** The quay's row in this frame (to starboard), or 0 at anchor. */
    private final int quayZ;
    private final Ships.Hull hull = new Ships.Hull(HULL_LEN, BEAM, KEEL, FLIGHT, 0.17, 0.07);
    private final int[] lo = new int[LEN], hi = new int[LEN];
    /** The rust run down her side from the drains at each x, in blocks. */
    private final int[] streak = new int[LEN];
    private final boolean[] down = new boolean[LIFTS.length];
    /** 0 outside her, 1 her plating, 2 inside. */
    private byte[] mask;
    private String name;
    private int number;
    /** Where her jets are parked: x, y, z and the way the nose points (yaw, degrees), on the deck and in the hangar. */
    final List<double[]> jets = new ArrayList<>();

    Carrier(Placer p, SplittableRandom rng, double hit, int quayZ) {
        this.p = p;
        this.rng = rng;
        this.hit = hit;
        this.quayZ = quayZ;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    void build() {
        plan();
        shell();
        hangar();
        gallery();
        below();
        machinery();
        fantail();
        jetShop();
        forecastle();
        for (int xs : TRUNKS) {
            trunk(xs, TRUNK_Z, DECKS[2], new int[] {4, 4, 4, 3, 3, 3}, "navy_deck_steel", new int[] {-10, -6, -2, 2, 11});
        }
        deck();
        lifts();
        catwalks();
        sponsons();
        underside();
        island();
        trunk(ISLAND_STAIR, ISLAND_STAIR_Z, GALLERY, new int[] {4, 3, 3, 3, 3, 3, 3}, "floor_lino",
                new int[] {11, 15, 18, 21, 24, 27, 30, 33});
        gear();
        paint();
        if (quayZ != 0) {
            camels();
            moor();
            brows();
        }
    }

    // ------------------------------------------------------------------ her shape

    private static double edge(int[][] pts, double x) {
        for (int i = 1; i < pts.length; i++) {
            if (x <= pts[i][0]) {
                double t = (x - pts[i - 1][0]) / (double) (pts[i][0] - pts[i - 1][0]);
                return pts[i - 1][1] + t * (pts[i][1] - pts[i - 1][1]);
            }
        }
        return pts[pts.length - 1][1];
    }

    private void plan() {
        for (int x = 0; x < LEN; x++) {
            lo[x] = (int) Math.round(edge(PORT, x));
            hi[x] = (int) Math.round(edge(STBD, x)) - 1;
            streak[x] = chance() < 0.12 + hit * 0.1 ? 2 + rng.nextInt(7) : 0;
        }
        name = NAMES[rng.nextInt(NAMES.length)];
        number = 86 + rng.nextInt(8);
        // the port elevator mostly left down at the hangar deck (the starboard ones would sit over the wharf)
        down[3] = chance() < 0.7;
    }

    /** The elevator whose platform covers (x, z), or -1. */
    private int lift(int x, int z) {
        for (int i = 0; i < LIFTS.length; i++) {
            int[] l = LIFTS[i];
            if (x >= l[0] && x <= l[1] && (l[2] > 0 ? z >= 20 && z <= 35 : z <= -21 && z >= lo[x])) {
                return i;
            }
        }
        return -1;
    }

    /** True on the flight deck's plan (not over an elevator). */
    private boolean plan(int x, int z) {
        return x >= 0 && x < LEN && z >= lo[x] && z <= hi[x] && lift(x, z) < 0;
    }

    /** True inside her outline: the hull, the overhang under the deck's edge, the flare at bow and stern. */
    private boolean solid(int x, int y, int z) {
        if (x < 0 || x >= LEN || y < KEEL || y > FLIGHT) {
            return false;
        }
        if (hull.in(x - HX, y, z)) {
            return true;
        }
        if (z < lo[x] || z > hi[x]) {
            return false;
        }
        if (y >= 10) {
            return lift(x, z) < 0;
        }
        double fw = x < HX ? 0 : x < 40 ? (40 - x) / 24.0 : x > 206 ? (x - 206) / 40.0 : 0;
        if (fw <= 0 || y <= FLARE0) {
            return false;
        }
        double t = (y - FLARE0) / (double) (10 - FLARE0);
        double hh = Math.max(0, hull.half(x - HX, y));
        double side = z < 0 ? -lo[x] : hi[x] + 1;
        return Math.abs(z + 0.5) < hh + (side - hh) * Math.min(1, fw) * Math.pow(t, 1.7);
    }

    private int kind(int x, int y, int z) {
        if (x < 0 || x >= LEN || y < KEEL || y > FLIGHT || z < Z0 || z >= Z0 + NZ) {
            return 0;
        }
        return mask[(x * NY + y - KEEL) * NZ + z - Z0];
    }

    /** True inside her, clear of the plating. */
    private boolean inside(int x, int y, int z) {
        return kind(x, y, z) == 2;
    }

    /** Sets a block only inside her. */
    private void in(int x, int y, int z, String block) {
        if (inside(x, y, z)) {
            p.set(x, y, z, block);
        }
    }

    /** The outermost z of her at (x, y) on a side, or Integer.MIN_VALUE where there's none. */
    private int edgeZ(int x, int y, int s) {
        for (int z = s > 0 ? Z0 + NZ - 1 : Z0; z >= Z0 && z < Z0 + NZ; z -= s) {
            if (kind(x, y, z) != 0) {
                return z;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** A decal or fitting on her side s at (x, y), facing out off the plating; false where there's no plating. */
    private boolean onSide(int x, int y, int s, String block) {
        if (y <= Ships.SEA) {
            return false;
        }
        int z = edgeZ(x, y, s);
        if (z == Integer.MIN_VALUE) {
            return false;
        }
        p.set(x, y, z + s, block + "[facing=" + (s > 0 ? "south" : "north") + "]");
        return true;
    }

    /** Her plating, decks and the hollow inside; the mask the rest is fitted to. */
    private void shell() {
        mask = new byte[LEN * NY * NZ];
        for (int x = 0; x < LEN; x++) {
            for (int y = KEEL; y <= FLIGHT; y++) {
                for (int z = Z0; z < Z0 + NZ; z++) {
                    if (solid(x, y, z)) {
                        mask[(x * NY + y - KEEL) * NZ + z - Z0] = 1;
                    }
                }
            }
        }
        byte[] edge = mask.clone();
        for (int x = 0; x < LEN; x++) {
            for (int y = KEEL; y <= FLIGHT; y++) {
                for (int z = Z0; z < Z0 + NZ; z++) {
                    int i = (x * NY + y - KEEL) * NZ + z - Z0;
                    if (edge[i] == 0) {
                        continue;
                    }
                    boolean shut = kind(x + 1, y, z) > 0 && kind(x - 1, y, z) > 0 && kind(x, y + 1, z) > 0
                            && kind(x, y - 1, z) > 0 && kind(x, y, z + 1) > 0 && kind(x, y, z - 1) > 0;
                    if (shut) {
                        edge[i] = 2;
                    }
                }
            }
        }
        mask = edge;
        for (int x = 0; x < LEN; x++) {
            for (int y = KEEL; y <= FLIGHT; y++) {
                for (int z = Z0; z < Z0 + NZ; z++) {
                    int k = kind(x, y, z);
                    if (k == 1) {
                        p.set(x, y, z, y == FLIGHT ? "navy_flight" : skin(x, y, z));
                    } else if (k == 2) {
                        boolean deck = y == GALLERY || y == DECKS[0] || y == DECKS[1] || y == DECKS[2];
                        // below the waterline the plating's black and red would show inside: line it with the bulkheads' enamel
                        boolean lined = y > TANK && y <= -2 && plated(x, y, z);
                        p.set(x, y, z, y <= TANK ? "vessel_tanktop" : y == HANGAR ? "navy_hangar_floor"
                                : lined ? "navy_wall" : deck ? "navy_deck_steel" : "air");
                    }
                }
            }
        }
    }

    /** True where (x, y, z) inside her lies against her plating. */
    private boolean plated(int x, int y, int z) {
        return kind(x + 1, y, z) == 1 || kind(x - 1, y, z) == 1 || kind(x, y, z + 1) == 1 || kind(x, y, z - 1) == 1;
    }

    /** The plate at a cell of her shell: red below the boot top, black at the waterline, haze grey above. */
    private String skin(int x, int y, int z) {
        boolean seam = (y - KEEL) % 3 == 0;
        if (y <= -6) {
            return y >= -7 && chance() < 0.6 ? "vessel_bottom_weed" : seam ? "vessel_bottom_seam" : "vessel_bottom";
        }
        if (y <= -2) {
            return seam ? "vessel_hull_black_seam" : chance() < 0.08 + hit * 0.1 ? "vessel_hull_black_rust" : "vessel_hull_black";
        }
        if (quayZ != 0 && z > 0 && y <= 3 && chance() < 0.3) {
            return "navy_hull_scrape";  // where the camels rub
        }
        if (y < GALLERY && y >= GALLERY - streak[x]) {
            return "navy_hull_rust";
        }
        if (chance() < 0.003 + hit * 0.008) {
            return "navy_hull_rust";
        }
        return seam ? "navy_hull_seam" : x % 12 == 0 ? "navy_hull_butt" : "navy_hull";
    }

    // ------------------------------------------------------------------ the hangar

    private boolean fireDoor(int x) {
        for (int f : FIRE_DOORS) {
            if (x == f) {
                return true;
            }
        }
        return false;
    }

    /** The elevator whose hangar opening is at x on side s, or -1. */
    private int opening(int x, int s) {
        for (int i = 0; i < LIFTS.length; i++) {
            if (LIFTS[i][2] == s && x > LIFTS[i][0] && x < LIFTS[i][1]) {
                return i;
            }
        }
        return -1;
    }

    private void hangar() {
        // the bulkheads at its ends and its inner walls, the fire doors across it
        for (int x = BAY0 - 1; x <= BAY1 + 1; x++) {
            for (int y = HANGAR + 1; y < GALLERY; y++) {
                for (int z = HZ0 - 1; z <= HZ1 + 1; z++) {
                    if (x == BAY0 - 1 || x == BAY1 + 1 || z == HZ0 - 1 || z == HZ1 + 1) {
                        in(x, y, z, "navy_bulkhead");
                    } else if (fireDoor(x)) {
                        boolean gap = z >= -8 && z <= 7 && y < GALLERY - 1;
                        p.set(x, y, z, gap ? "air" : y == GALLERY - 1 ? "navy_steel" : "navy_curtain[facing=east]");
                    }
                }
            }
        }
        // the deck: oil, pad eyes, the hazard line where the fire doors close, the yellow safety lines along both sides
        for (int x = BAY0; x <= BAY1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                p.set(x, HANGAR, z, fireDoor(x) ? "navy_hazard[axis=z]" : chance() < 0.05 + hit * 0.06 ? "navy_hangar_oil" : "navy_hangar_floor");
                if (!fireDoor(x) && (z == HZ0 + 3 || z == HZ1 - 3)) {
                    p.set(x, HANGAR + 1, z, "fit_line[axis=x]");
                } else if (!fireDoor(x) && x % 6 == 3 && Math.floorMod(z, 6) == 1) {
                    p.set(x, HANGAR + 1, z, "navy_padeye");
                }
            }
        }
        // the openings to the elevators: rolled down, half up, or open where the platform's down
        for (int i = 0; i < LIFTS.length; i++) {
            int[] l = LIFTS[i];
            int s = l[2];
            int zin = s > 0 ? HZ1 + 1 : HZ0 - 1, zout = s > 0 ? 19 : -20;
            int shut = down[i] ? GALLERY : chance() < 0.25 ? HANGAR + 4 : HANGAR + 1;
            for (int x = l[0] + 1; x < l[1]; x++) {
                for (int z = Math.min(zin, zout); z <= Math.max(zin, zout); z++) {
                    p.set(x, HANGAR, z, z == zout ? "navy_hazard[axis=x]" : "navy_hangar_floor");
                    for (int y = HANGAR + 1; y < GALLERY - 1; y++) {
                        p.set(x, y, z, z == zout && y >= shut ? "navy_roller[facing=" + (s > 0 ? "south" : "north") + "]" : "air");
                    }
                }
            }
        }
        // the bay numbers on the walls
        for (int x = BAY0 + 10; x < BAY1 - 4; x += 24) {
            int bay = x < FIRE_DOORS[0] ? 3 : x < FIRE_DOORS[1] ? 2 : 1;
            for (int s : new int[] {-1, 1}) {
                if (opening(x, s) < 0 && opening(x + 1, s) < 0) {
                    p.set(x, HANGAR + 4, s > 0 ? HZ1 : HZ0, "navy_st_hangar" + bay + "[facing=" + (s > 0 ? "north" : "south") + "]");
                }
            }
        }
        hangarBooth(150);
        hangarGear();
        hangarOverhead();
        hangarWalls();
    }

    /**
     * The hangar's overhead: a deep girder across it every sixth metre, sprinkler mains along it
     * between them, a vent trunk down the middle, pipes and cable trays along both sides, and the
     * dome lamps hung in rows between the girders: most dead with the power gone, some flickering.
     */
    private void hangarOverhead() {
        int top = GALLERY - 1;
        for (int x = BAY0; x <= BAY1; x++) {
            if (fireDoor(x)) {
                continue;
            }
            for (int z = HZ0; z <= HZ1; z++) {
                if (x % 6 == 0) {
                    over(x, top, z, "fit_girder[axis=x]");
                    continue;
                }
                String run = z == HZ0 ? "fit_pipes_b" : z == HZ1 ? "fit_pipes_a" : z == HZ0 + 1 || z == HZ1 - 1 ? "fit_tray"
                        : z == -1 ? "fit_duct" : Math.floorMod(z, 5) == 0 ? "fit_sprinkler" : null;
                if (z == -11 || z == -6 || z == 4 || z == 9) {
                    over(x, top, z, "fit_girder[axis=z]");   // the longitudinals, so the overhead is a grid like the photos
                } else if (x % 6 == 3 && Math.floorMod(z, 5) == 2 && z > HZ0 + 1 && z < HZ1 - 1) {
                    double r = chance();
                    over(x, top, z, r < 0.06 ? "fit_highbay_on" : r < 0.26 ? "fit_highbay_flicker" : "fit_highbay");
                } else if (run != null) {
                    over(x, top, z, (run.equals("fit_duct") && x % 6 == 3 ? "fit_duct_grille" : run) + "[axis=x]");
                }
            }
        }
    }

    /**
     * The hangar's side walls: a web frame up them under each girder, and between, the fire stations,
     * breathing sets, junction boxes, phones, speakers, chain racks, warning signs, the battle
     * lanterns and floodlights. Only where the wall gear and the openings left room.
     */
    private void hangarWalls() {
        for (int x = BAY0 + 1; x < BAY1; x++) {
            if (fireDoor(x) || fireDoor(x - 1) || fireDoor(x + 1)) {
                continue;
            }
            for (int s : new int[] {-1, 1}) {
                if (opening(x, s) >= 0 || opening(x - 1, s) >= 0 || opening(x + 1, s) >= 0) {
                    continue;
                }
                int z = s > 0 ? HZ1 : HZ0;
                String f = s > 0 ? "north" : "south", on = "[facing=" + f + "]";
                if (x % 6 == 0) {
                    for (int y = HANGAR + 1; y < GALLERY - 1; y++) {
                        over(x, y, z, "fit_frame" + on);
                    }
                    continue;
                }
                switch (x % 24) {
                    case 12 -> {
                        over(x, HANGAR + 1, z, "fit_firestation" + on);
                        over(x, HANGAR + 2, z, "fit_st_fire" + on);
                    }
                    case 2 -> over(x, HANGAR + 2, z, "fit_eebd" + on);
                    case 3, 15 -> over(x, HANGAR + 3, z, "fit_jbox" + on);
                    case 9 -> over(x, GALLERY - 3, z, "fit_speaker" + on);
                    case 21 -> over(x, HANGAR + 2, z, "fit_phone" + on);
                    case 7 -> over(x, HANGAR + 3, z, lantern(f));
                    case 17 -> over(x, HANGAR + 2, z, "fit_chain_rack" + on);
                    case 19 -> over(x, HANGAR + 3, z, CAUTIONS[rng.nextInt(2)] + on);
                    case 23 -> over(x, GALLERY - 3, z, (chance() < 0.3 ? "fit_flood_on" : "fit_flood") + on);
                    default -> {
                    }
                }
            }
        }
    }

    /** The hangar deck control booth high on the port wall at x, glazed toward the bays, a ladder up to it. */
    private void hangarBooth(int bx) {
        int y0 = HANGAR + 4;
        for (int x = bx; x <= bx + 6; x++) {
            for (int z = HZ0; z <= HZ0 + 2; z++) {
                p.set(x, y0, z, "navy_deck_steel");
                p.set(x, GALLERY - 1, z, "navy_steel");
                for (int y = y0 + 1; y < GALLERY - 1; y++) {
                    boolean glass = z == HZ0 + 2 || x == bx;
                    boolean wall = glass || x == bx + 6;
                    boolean doorway = x == bx + 6 && z == HZ0 && y < y0 + 3;
                    p.set(x, y, z, doorway || !wall ? "air" : glass && y == y0 + 2
                            ? (chance() < 0.25 + hit * 0.3 ? "navy_window_broken" : "navy_window") + (z == HZ0 + 2 ? "[facing=south]" : "[facing=west]")
                            : "navy_bulkhead");
                }
            }
            p.set(x, y0 - 1, HZ0 + 2, "steel_girder");
        }
        for (int x = bx + 1; x <= bx + 5; x++) {
            p.set(x, y0 + 1, HZ0 + 1, x == bx + 3 ? "ship_telegraph[facing=south]" : "ship_console[facing=south]");
        }
        p.set(bx + 2, y0 + 1, HZ0, "bunk_chair[facing=south]");
        p.set(bx + 4, y0 + 2, HZ0, "navy_screen[facing=south]");
        for (int y = HANGAR + 1; y <= y0; y++) {
            p.set(bx + 7, y, HZ0, "bunk_ladder[facing=south]");
        }
        p.set(bx + 7, y0 + 1, HZ0, "air");
    }

    /** Along the hangar's walls: racks, benches, reels, drums; spare engines, carts and tractors in the bays; where the jets are. */
    private void hangarGear() {
        for (int x = BAY0 + 1; x < BAY1; x++) {
            if (fireDoor(x) || fireDoor(x - 1) || fireDoor(x + 1)) {
                continue;
            }
            for (int s : new int[] {-1, 1}) {
                if (opening(x, s) >= 0 || opening(x - 1, s) >= 0 || opening(x + 1, s) >= 0) {
                    continue;
                }
                int z = s > 0 ? HZ1 : HZ0;
                if (s < 0 && x >= 149 && x <= 158) {
                    continue;  // under the booth and its ladder
                }
                if (s > 0 && trunkNear(x)) {
                    continue;
                }
                String f = s > 0 ? "north" : "south";
                double r = chance();
                if (r < 0.22) {
                    p.set(x, HANGAR + 1, z, any("bunk_rack_crates", "bunk_rack_drums", "bunk_rack_boxes") + "[facing=" + f + "]");
                } else if (r < 0.34) {
                    p.set(x, HANGAR + 1, z, "bunk_desk[facing=" + f + "]");
                    p.set(x, HANGAR + 2, z, "bunk_pegboard[facing=" + f + "]");
                } else if (r < 0.4) {
                    p.set(x, HANGAR + 1, z, "navy_toolbox[facing=" + f + "]");
                } else if (r < 0.44) {
                    p.set(x, HANGAR + 1, z, "navy_hose_reel[facing=" + f + "]");
                } else if (r < 0.48) {
                    p.set(x, HANGAR + 1, z, "navy_extinguisher[facing=" + f + "]");
                } else if (r < 0.53) {
                    p.set(x, HANGAR + 1, z, any("bunk_drum_water", "rust", "bunk_jerry_cans[facing=" + f + "]"));
                }
                if (x % 16 == 5) {
                    p.set(x, HANGAR + 3, z, "navy_lamp_wall[facing=" + f + "]");
                }
            }
        }
        // the jets packed in: two staggered rows to a bay, all nose forward, the support gear under their wings
        int[][] spots = {{67, -7, 1}, {90, -7, 1}, {113, -7, 1}, {79, 6, 0}, {102, 6, 0},
            {139, -7, 1}, {162, -7, 1}, {184, -7, 1}, {151, 6, 0}, {174, 6, 0},
            {209, -7, 1}, {232, -7, 1}, {221, 6, 0}, {244, 6, 0}};
        for (int[] s : spots) {
            if (chance() < 0.9) {
                park(s[0], HANGAR + 1, s[1], 270);
                for (int side : new int[] {-1, 1}) {
                    if (chance() < 0.7) {
                        String g = any("fit_firebottle", "fit_gse_power", "fit_gse_air", "fit_tyres", "fit_fod", "navy_toolbox");
                        p.setIfAir(s[0] - 3 + rng.nextInt(5), HANGAR + 1, s[1] + side * (4 + rng.nextInt(2)),
                                g.equals("fit_tyres") || g.equals("fit_fod") ? g : g + "[facing=" + any("north", "east", "south", "west") + "]");
                    }
                }
            }
        }
        // along the walls, clear of the jets: drop tanks on their cradles, spare engines, parked tractors, carts, stands
        for (int s : new int[] {-1, 1}) {
            int z = s > 0 ? HZ1 - 1 : HZ0 + 1;
            for (int x = BAY0 + 2; x < BAY1 - 6; ) {
                boolean clear = true;
                for (int dx = -1; dx <= 5; dx++) {
                    clear &= !fireDoor(x + dx) && opening(x + dx, s) < 0 && !(s > 0 && trunkNear(x + dx))
                            && !(s < 0 && x + dx >= 148 && x + dx <= 158);
                }
                if (!clear) {
                    x++;
                    continue;
                }
                double r = chance();
                int len = 1;
                if (r < 0.2) {
                    p.setIfAir(x, HANGAR + 1, z, "fit_droptank_nose[axis=x]");
                    p.setIfAir(x + 1, HANGAR + 1, z, "fit_droptank[axis=x]");
                    p.setIfAir(x + 2, HANGAR + 1, z, "fit_droptank[axis=x]");
                    len = 3;
                } else if (r < 0.35) {
                    for (int dx = 0; dx < 4; dx++) {
                        p.setIfAir(x + dx, HANGAR + 1, z, "navy_engine[axis=x]");
                    }
                    len = 4;
                } else if (r < 0.5) {
                    p.setIfAir(x, HANGAR + 1, z, "navy_tractor_back[facing=east]");
                    p.setIfAir(x + 1, HANGAR + 1, z, "navy_tractor_front[facing=east]");
                    p.setIfAir(x + 2, HANGAR + 1, z, "fit_towbar[facing=east]");
                    len = 3;
                } else if (r < 0.65) {
                    p.setIfAir(x, HANGAR + 1, z, any("fit_gse_power", "fit_gse_air") + "[facing=" + (s > 0 ? "north" : "south") + "]");
                    p.setIfAir(x + 1, HANGAR + 1, z, "fit_firebottle[facing=" + (s > 0 ? "north" : "south") + "]");
                    len = 2;
                } else if (r < 0.75) {
                    p.setIfAir(x, HANGAR + 1, z, "fit_stand[facing=" + (s > 0 ? "north" : "south") + "]");
                    p.setIfAir(x + 1, HANGAR + 1, z, "fit_tyres");
                    len = 2;
                } else if (r < 0.85) {
                    p.setIfAir(x, HANGAR + 1, z, x > BAY1 - 60 ? (chance() < 0.5 ? "navy_bomb[axis=x]" : "navy_missile[axis=x]") : "fit_fod");
                }
                x += len + 1 + rng.nextInt(3);
            }
        }
        // the forward end of the forward bay is a staging area: bomb and missile carts, tractors
        for (int i = 0; i < 10; i++) {
            int x = 247 + rng.nextInt(13), z = HZ0 + 3 + rng.nextInt(12);
            p.setIfAir(x, HANGAR + 1, z, chance() < 0.5 ? "navy_bomb[axis=x]" : "navy_missile[axis=x]");
        }
        for (int i = 0; i < 2; i++) {
            tractor(250 + rng.nextInt(8), HANGAR + 1, HZ0 + 4 + rng.nextInt(10));
        }
        dressing(BAY0, BAY1, HZ0, HZ1, HANGAR, 60);
    }

    /** True where a stair trunk stands by the starboard wall. */
    private boolean trunkNear(int x) {
        for (int t : TRUNKS) {
            if (x >= t - 3 && x <= t + 6) {
                return true;
            }
        }
        return false;
    }

    /** A tow tractor at (x, y, z), pointed any way. */
    private void tractor(int x, int y, int z) {
        String[] dirs = {"north", "east", "south", "west"};
        int d = rng.nextInt(4);
        int bx = d == 1 ? x - 1 : d == 3 ? x + 1 : x, bz = d == 0 ? z + 1 : d == 2 ? z - 1 : z;
        String there = p.get(x, y, z), back = p.get(bx, y, bz);
        if ((there == null || there.equals("air")) && (back == null || back.equals("air"))) {
            p.set(x, y, z, "navy_tractor_front[facing=" + dirs[d] + "]");
            p.set(bx, y, bz, "navy_tractor_back[facing=" + dirs[d] + "]");
        }
    }

    /**
     * A jet at its spot: its middle at (x, y, z), the nose toward yaw (0 is +z, 90 is -x, 180 is
     * -z, 270 is +x), one of the two fighters at full size (tools/cars/aircraft.py); chocks at its
     * wheels and chains to the pad eyes.
     */
    private void park(double x, int y, double z, double yaw) {
        jets.add(new double[] {x + 0.5, y, z + 0.5, yaw});
        tieDown(x, y, z, yaw, rng.nextBoolean() ? "jet_corvid" : "jet_gyrfalcon");
    }

    /** True if (x, z) on deck y lies under a parked jet or in the run ahead of its nose, where it taxis out. */
    private boolean inTaxiLane(int x, int y, int z) {
        for (double[] j : jets) {
            if (j[1] != y) {
                continue;
            }
            double a = Math.toRadians(j[3]), fx = -Math.sin(a), fz = Math.cos(a);
            double dx = x + 0.5 - j[0], dz = z + 0.5 - j[2];
            double ahead = dx * fx + dz * fz, aside = Math.abs(dx * fz - dz * fx);
            if (aside < 9 && ahead > -11 && ahead < 45) {
                return true;
            }
        }
        return false;
    }

    /** Where each aircraft's wheels touch, in metres from its middle: {forward, to the side}. */
    private static double[][] wheels(String model) {
        return switch (model) {
            case "jet_corvid" -> new double[][] {{3.9, 0}, {-2.2, 1.7}, {-2.2, -1.7}};
            case "jet_gyrfalcon" -> new double[][] {{2.6, 0}, {-3.0, 2.2}, {-3.0, -2.2}};
            default -> new double[][] {{2.75, 1.0}, {2.75, -1.0}};
        };
    }

    private void tieDown(double x, int y, double z, double yaw, String model) {
        p.vehicle(x + 0.5, y, z + 0.5, (float) yaw, model, rng.nextLong());
        double a = Math.toRadians(yaw);
        double fx = -Math.sin(a), fz = Math.cos(a);
        for (double[] w : wheels(model)) {
            int wx = (int) Math.floor(x + 0.5 + fx * w[0] - fz * w[1]), wz = (int) Math.floor(z + 0.5 + fz * w[0] + fx * w[1]);
            p.setIfAir(wx, y, wz, "navy_chocks[facing=" + (Math.abs(fx) > Math.abs(fz) ? "east" : "north") + "]");
        }
        for (int k = 0; k < 2; k++) {
            int cx = (int) Math.floor(x + 0.5 - fx * (1 + k * 3)), cz = (int) Math.floor(z + 0.5 - fz * (1 + k * 3));
            p.setIfAir(cx + (k == 0 ? 1 : -1), y, cz, "navy_chains");
        }
    }

    /** Litter over a floor: paper, glass, blood, spent cases, the odd body. */
    private void dressing(int x0, int x1, int z0, int z1, int y, int n) {
        for (int i = 0; i < n; i++) {
            int x = x0 + rng.nextInt(Math.max(1, x1 - x0 + 1)), z = z0 + rng.nextInt(Math.max(1, z1 - z0 + 1));
            String f = any("north", "east", "south", "west");
            double r = chance();
            p.setIfAir(x, y + 1, z, r < 0.3 ? "paper" : r < 0.45 ? "blood_splat" : r < 0.55 ? "bunk_casings" : r < 0.65 ? "glass_shards"
                    : r < 0.75 ? "debris_chunk[facing=" + f + "]" : r < 0.82 ? "bunk_bodybag[facing=" + f + "]"
                    : r < 0.9 ? "bunk_puddle" : "bunk_bootprints[facing=" + f + "]");
        }
    }

    // ------------------------------------------------------------------ compartments

    /** True if a stair trunk's walls stand between xa and xb. */
    private static boolean trunkIn(int xa, int xb) {
        for (int t : TRUNKS) {
            if (xb >= t - 2 && xa <= t + 5) {
                return true;
            }
        }
        return false;
    }

    /**
     * A level of compartments on floor y from x0 to x1: passages between the pairs of longitudinal
     * bulkheads in walls (sorted, port first), the frames across them every FRAME (a watertight door
     * across each passage every third frame), and in each band between, a compartment per frame,
     * fitted out as pick says, its door onto the nearest passage.
     */
    private void level(int y, int x0, int x1, int[] walls, Picker pick) {
        for (int x = x0; x <= x1; x++) {
            for (int w : walls) {
                for (int dy = 1; dy <= 3; dy++) {
                    in(x, y + dy, w, "navy_wall");
                }
            }
            for (int i = 0; i + 1 < walls.length; i += 2) {
                for (int z = walls[i] + 1; z < walls[i + 1]; z++) {
                    if (inside(x, y + 1, z)) {
                        p.set(x, y, z, "navy_tile_grey");
                    }
                }
            }
        }
        int bands = walls.length / 2 + 1;
        for (int k = 0; x0 + (k + 1) * FRAME <= x1 + FRAME; k++) {
            int fa = x0 + k * FRAME, fb = Math.min(x1, fa + FRAME);
            for (int z = Z0; z < Z0 + NZ; z++) {
                boolean passage = false;
                for (int i = 0; i + 1 < walls.length; i += 2) {
                    passage |= z > walls[i] && z < walls[i + 1];
                }
                if (passage && k % 3 != 0) {
                    continue;
                }
                for (int dy = 1; dy <= 3; dy++) {
                    in(fa, y + dy, z, "navy_wall");
                }
            }
            if (k % 3 == 0) {
                for (int i = 0; i + 1 < walls.length; i += 2) {
                    if (inside(fa, y + 1, walls[i] + 1)) {
                        Doors.abandoned(p, rng, fa, y + 1, walls[i] + 1, "ship_door", "east", Doors.hinge(rng));
                    }
                }
            }
            for (int b = 0; b < bands; b++) {
                int za = b == 0 ? Z0 : walls[2 * b - 1] + 1, zb = b == bands - 1 ? Z0 + NZ - 1 : walls[2 * b] - 1;
                int ca = Integer.MAX_VALUE, cb = Integer.MIN_VALUE;
                for (int x = fa + 1; x < fb; x++) {
                    for (int z = za; z <= zb; z++) {
                        if (inside(x, y + 1, z) && inside(x, y + 3, z)) {
                            ca = Math.min(ca, z);
                            cb = Math.max(cb, z);
                        }
                    }
                }
                if (cb - ca < 2 || fb - fa < 5) {
                    continue;
                }
                int doorZ = b == bands - 1 ? walls[walls.length - 1] : walls[2 * b];
                if (b > 0 && b < bands - 1 && Math.abs(walls[2 * b] + 0.5) > Math.abs(walls[2 * b - 1] + 0.5)) {
                    doorZ = walls[2 * b - 1];
                }
                if (!inside((fa + fb) / 2, y + 1, doorZ)) {
                    continue;
                }
                fit(pick.pick(fa + 1, fb - 1, ca, cb), fa + 1, fb - 1, ca, cb, y, doorZ);
            }
        }
        for (int i = 0; i + 1 < walls.length; i += 2) {
            passage(y, x0, x1, walls[i] + 1, ((y + i) & 2) == 0 ? "fit_pipes_b" : "fit_pipes_a");
        }
    }

    // ------------------------------------------------------------------ the fit-out: overheads, lights, bulkhead fittings

    /** True inside her where nothing has been put yet. */
    private boolean free(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return false;
        }
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    /** Sets a fitting inside her where nothing has been put yet, so the furniture and the doors keep their places. */
    private void over(int x, int y, int z, String block) {
        if (free(x, y, z)) {
            p.set(x, y, z, block);
        }
    }

    /**
     * A strip light with the power failing (the main lights dead or flickering, a few still on, the
     * odd one hanging by its wires); on the emergency generator (lit) most still burn.
     */
    private String tube(String axis, boolean lit) {
        double r = chance();
        String t = lit ? (r < 0.75 ? "fit_tube_on" : r < 0.95 ? "fit_tube_flicker" : "fit_tube")
                : r < 0.08 ? "fit_tube_on" : r < 0.3 ? "fit_tube_flicker" : r < 0.4 ? "bunk_tube_dangle" : "fit_tube";
        return t + "[axis=" + axis + "]";
    }

    /**
     * A console (or overhead console) facing f: on the emergency generator (lit) most displays still
     * show; on the dead main supply nearly all are dark, a few flickering.
     */
    private String console(String name, String f, boolean lit) {
        double r = chance();
        String c = lit ? (r < 0.7 ? "_on" : r < 0.9 ? "_flicker" : "") : r < 0.06 ? "_on" : r < 0.3 ? "_flicker" : "";
        return name + c + "[facing=" + f + "]";
    }

    /** The red night lights: on the battery circuit, so most still glow. */
    private String red() {
        return chance() < 0.8 ? "fit_red_on" : "fit_red";
    }

    /** A battery battle lantern on the bulkhead, facing f: most came on when the power failed. */
    private String lantern(String f) {
        return (chance() < 0.7 ? "fit_lantern_on" : "fit_lantern") + "[facing=" + f + "]";
    }

    /** The deep beam across a run (it takes the run's axis), with the run's fitting passing under it; run null for a bare beam. */
    private static String beam(String run, String axis) {
        String b = run == null ? "fit_beam" : run.equals("fit_pipes_b") ? "fit_beam_pipes" : "fit_beam_" + run.substring(4);
        return b + "[axis=" + axis + "]";
    }

    private static final String[] CAUTIONS = {"fit_st_hearing", "fit_st_nosmoking", "fit_st_oxygen", "fit_st_escape", "fit_st_ammo"};

    /**
     * A passage on floor y from x0 to x1, its rows a (to port, against the wall at a - 1) and a + 1:
     * the pipe run over row a and a cable tray over the other, a deep beam across both every third
     * metre (in line with the frames), the strip lights and red night lights between the beams; on
     * the bulkheads the battle lanterns either side of each watertight door, phones, junction boxes,
     * speakers, valves, pipe drops, fire stations, breathing sets, placards, signs and the frames.
     */
    private void passage(int y, int x0, int x1, int a, String pipes) {
        int b = a + 1;
        for (int x = x0; x <= x1; x++) {
            int m = x - x0;
            if (m % 3 == 0) {
                over(x, y + 3, a, beam(pipes, "x"));
                over(x, y + 3, b, beam("fit_tray", "x"));
            } else {
                over(x, y + 3, a, (pipes.equals("fit_pipes_b") && m % 18 == 10 ? "fit_pipes_valve" : pipes) + "[axis=x]");
                over(x, y + 3, b, m % 6 == 1 ? tube("x", false) : m % 12 == 8 ? red() : "fit_tray[axis=x]");
            }
            // row a hangs its fittings on the wall to port (facing south), row b on the wall to starboard
            String fa = "south", fb = "north";
            switch (m % 36) {
                case 1, 35 -> over(x, y + 2, a, lantern(fa));
                case 2 -> over(x, y + 2, b, "fit_st_keepclosed[facing=" + fb + "]");
                case 4 -> over(x, y + 2, a, "fit_pl_" + rng.nextInt(16) + "[facing=" + fa + "]");
                case 5, 22 -> over(x, y + 2, b, "fit_jbox[facing=" + fb + "]");
                case 7 -> over(x, y + 1, a, "fit_valve[facing=" + fa + "]");
                case 10 -> over(x, y + 2, a, "fit_phone[facing=" + fa + "]");
                case 11 -> {
                    over(x, y + 1, b, "fit_pipe_drop[facing=" + fb + "]");
                    over(x, y + 2, b, "fit_pipe_drop[facing=" + fb + "]");
                }
                case 14 -> over(x, y + 2, b, "fit_eebd[facing=" + fb + "]");
                case 16 -> over(x, y + 2, a, "fit_speaker[facing=" + fa + "]");
                case 18 -> {
                    over(x, y + 1, b, "fit_firestation[facing=" + fb + "]");
                    over(x, y + 2, b, "fit_st_fire[facing=" + fb + "]");
                }
                case 20 -> over(x, y + 2, a, "fit_exit[facing=" + fa + "]");
                case 25 -> {
                    over(x, y + 1, a, "fit_pipe_drop[facing=" + fa + "]");
                    over(x, y + 2, a, "fit_pipe_drop[facing=" + fa + "]");
                }
                case 28 -> over(x, y + 2, b, "fit_pl_" + rng.nextInt(16) + "[facing=" + fb + "]");
                case 29 -> over(x, y + 2, a, CAUTIONS[rng.nextInt(CAUTIONS.length)] + "[facing=" + fa + "]");
                case 31 -> over(x, y + 1, b, "fit_valve[facing=" + fb + "]");
                default -> {
                }
            }
            if (m % 3 == 0 && m % 36 != 0) {
                for (int dy = 1; dy <= 2; dy++) {
                    over(x, y + dy, a, "fit_frame[facing=" + fa + "]");
                    over(x, y + dy, b, "fit_frame[facing=" + fb + "]");
                }
            }
        }
    }

    /**
     * A compartment's overhead and bulkheads: a pipe run along the far side, a cable tray along the
     * near one, the lights down the middle (on the emergency generator where lit), a vent trunk where
     * people live, beams across every fourth metre; on the far bulkhead junction boxes, speakers and
     * pipe drops, a battle lantern by the door, a placard on each end bulkhead. Only where the
     * fit-out left room.
     */
    private void overheads(Room r, int xa, int xb, int near, int far, int y, String toNear, String toFar, int dx, boolean lit) {
        int in = far > near ? 1 : -1, mid = (near + far) / 2;
        String pipes = chance() < 0.5 ? "fit_pipes_a" : "fit_pipes_b";
        boolean duct = Math.abs(far - near) >= 4 && switch (r) {
            case BERTHING, MESS, GALLEY, OFFICE, STATEROOM, READY, CONTROL, SICKBAY -> true;
            default -> false;
        };
        int ductZ = mid + in;
        for (int x = xa; x <= xb; x++) {
            boolean beamX = (x - xa) % 4 == 0;
            for (int z = Math.min(near, far); z <= Math.max(near, far); z++) {
                String run = z == far ? (r == Room.MAGAZINE ? "fit_sprinkler" : pipes) : z == near ? "fit_tray"
                        : duct && z == ductZ ? "fit_duct" : r == Room.MAGAZINE && z == mid ? "fit_sprinkler" : null;
                if (beamX) {
                    over(x, y + 3, z, beam(run, "x"));
                } else if (z == mid && (x - xa) % 4 == 2 && r != Room.MAGAZINE) {
                    over(x, y + 3, z, tube("x", lit));
                } else if (z == near && (x - xa) % 8 == 5 && (r == Room.BERTHING || r == Room.STATEROOM || r == Room.MESS)) {
                    over(x, y + 3, z, red());
                } else if (run != null) {
                    over(x, y + 3, z, (run.equals("fit_duct") && (x - xa) % 4 == 2 ? "fit_duct_grille" : run) + "[axis=x]");
                }
            }
            switch ((x - xa) % 6) {
                case 1 -> over(x, y + 2, far, "fit_jbox[facing=" + toNear + "]");
                case 3 -> {
                    over(x, y + 1, far, "fit_pipe_drop[facing=" + toNear + "]");
                    over(x, y + 2, far, "fit_pipe_drop[facing=" + toNear + "]");
                }
                case 5 -> over(x, y + 2, far, (chance() < 0.5 ? "fit_speaker" : "fit_frame") + "[facing=" + toNear + "]");
                default -> {
                }
            }
        }
        over(dx + 1, y + 2, near, lantern(toFar));
        over(dx - 2, y + 2, near, chance() < 0.5 ? "fit_eebd[facing=" + toFar + "]" : "fit_phone[facing=" + toFar + "]");
        over(xa, y + 2, mid, "fit_pl_" + rng.nextInt(16) + "[facing=east]");
        over(xb, y + 2, mid - in, (chance() < 0.5 ? "fit_jbox" : "fit_speaker") + "[facing=west]");
        if (r == Room.WORKSHOP || r == Room.STORES) {
            over(xa, y + 1, near + in, "fit_dc_locker[facing=east]");
            over(xa, y + 1, near + 2 * in, "fit_dc_locker_b[facing=east]");
        }
    }

    /** Fits out a compartment from xa to xb and za to zb on floor y, its door in the bulkhead at row doorZ. */
    private void fit(Room r, int xa, int xb, int za, int zb, int y, int doorZ) {
        if (r == Room.WARDROOM) {
            return;
        }
        boolean port = doorZ > zb;
        int near = port ? zb : za, far = port ? za : zb, in = port ? -1 : 1;
        String toNear = port ? "south" : "north", toFar = port ? "north" : "south";
        int dx = (xa + xb) / 2;
        String floor = switch (r) {
            case MESS -> chance() < 0.7 ? "navy_tile_grey" : "navy_tile_blue";
            case GALLEY, BRIG, HEAD -> "navy_tile_grey";
            case CAPTAIN -> "fit_carpet";
            case SICKBAY -> "navy_tile_green";
            case BERTHING, STATEROOM, OFFICE, READY, CONTROL -> any("navy_tile_blue", "floor_lino");
            default -> null;
        };
        if (floor != null) {
            for (int x = xa; x <= xb; x++) {
                for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) {
                    if (inside(x, y + 1, z)) {
                        p.set(x, y, z, floor);
                    }
                }
            }
        }
        for (int x = xa + 2; x <= xb - 1; x += 4) {
            if (r == Room.MAGAZINE) {
                in(x, y + 2, far, "bunk_lamp_red" + (chance() < 0.3 ? "_lit" : "") + "[facing=" + toNear + "]");
            }
        }
        boolean steel = r == Room.ARMORY || r == Room.MAGAZINE;
        Doors.abandoned(p, rng, dx, y + 1, doorZ, steel ? "bunk_door_steel" : "ship_door", toFar, Doors.hinge(rng));
        in(dx + 1, y + 2, doorZ - in, "navy_pl_" + rng.nextInt(8) + "[facing=" + toNear + "]");
        String sign = switch (r) {
            case MESS -> "navy_st_mess";
            case GALLEY -> "navy_st_galley";
            case SICKBAY -> "navy_st_sickbay";
            case BRIG -> "navy_st_brig";
            case ARMORY -> "navy_st_armory";
            case MAGAZINE -> "navy_st_magazine";
            case BERTHING -> "navy_st_berthing";
            case READY -> "navy_st_ready";
            case CONTROL -> "navy_st_control";
            case ARREST, CATAPULT -> "navy_st_machinery";
            case HEAD -> "fit_st_head";
            default -> null;
        };
        if (sign != null) {
            in(dx - 1, y + 2, doorZ - in, sign + "[facing=" + toNear + "]");
        }
        int lo2 = Math.min(near, far) + (port ? 0 : 1), hi2 = Math.max(near, far) - (port ? 1 : 0);
        // the officers' rooms have drop ceilings of their own, not the bare overheads
        boolean dropped = r == Room.CAPTAIN;
        switch (r) {
            case BERTHING -> {
                // racks three high back to back in rows across the compartment, two long with a tall locker
                // between each pair (as in the berthing photos), the aisles between the rows
                int z0 = Math.min(near, far) + 1, z1 = Math.max(near, far) - 1;
                for (int x = xa; x <= xb; x++) {
                    int k = (x - xa) % 3;
                    if (k == 0) {
                        continue;
                    }
                    String f = k == 1 ? "west" : "east";
                    for (int z = z0; z <= z1; z++) {
                        int q = (z - z0) % 3;
                        boolean pair = q < 2 && (q == 1 || z + 1 <= z1);
                        String end = (q == 0) == f.equals("east") ? "a" : "b";
                        if (inside(x, y + 1, z) && inside(x, y + 2, z)) {
                            p.set(x, y + 1, z, (pair ? "fit_rack_lo_" + end : "fit_locker_lo") + "[facing=" + f + "]");
                            p.set(x, y + 2, z, (pair ? "fit_rack_hi_" + end : "fit_locker_hi") + "[facing=" + f + "]");
                        }
                    }
                    if (inside(x, y + 2, far)) {
                        in(x, y + 1, far, "fit_locker_lo[facing=" + toNear + "]");
                        in(x, y + 2, far, "fit_locker_hi[facing=" + toNear + "]");
                    }
                }
                litter(xa, xb, za, zb, y, "home_clothes_pile", 0.06);
            }
            case MESS -> {
                // tables in rows with their fixed stools either side, the serving line down one end, screens
                for (int z = Math.min(near, far) + 1; z < Math.max(near, far); z++) {
                    if (Math.floorMod(z - far, 3) != 1) {
                        continue;
                    }
                    for (int x = xa + 1; x <= xb - 2; x++) {
                        in(x, y + 1, z, "fit_mess_table[axis=x]");
                        if (free(x, y + 1, z - 1)) {
                            p.set(x, y + 1, z - 1, "fit_stools[facing=south]");
                        }
                        if (free(x, y + 1, z + 1)) {
                            p.set(x, y + 1, z + 1, "fit_stools[facing=north]");
                        }
                    }
                }
                for (int z = Math.min(near, far); z <= Math.max(near, far); z++) {
                    if (z != near) {
                        in(xb, y + 1, z, "fit_counter_steel[facing=west]");
                        if (Math.floorMod(z, 3) == 0) {
                            in(xb, y + 2, z, "fit_dispenser[facing=west]");
                        }
                    }
                }
                for (int x = xa + 1; x < xb; x += 4) {
                    in(x, y + 2, far, "navy_screen[facing=" + toNear + "]");
                }
                litter(xa, xb, za, zb, y, "paper", 0.05);
            }
            case GALLEY -> {
                for (int x = xa; x <= xb; x++) {
                    if (x % 3 == 0) {
                        in(x, y + 1, far, "fit_counter_steel[facing=" + toNear + "]");
                    } else {
                        in(x, y + 1, far, "fit_oven[facing=" + toNear + "]");
                        in(x, y + 2, far, "fit_oven[facing=" + toNear + "]");
                    }
                    // the islands: worktops, kettles and ovens back to back in rows down the galley
                    for (int z = far - in * 3; (z - near) * in > 1; z -= in * 3) {
                        if (x > xa + 1 && x < xb - 1) {
                            in(x, y + 1, z, (x % 4 == 0 ? "fit_kettle" : x % 4 == 2 ? "fit_oven" : "fit_counter_steel") + "[facing=" + toFar + "]");
                            if (x % 4 == 1) {
                                in(x, y + 2, z, "fit_dispenser[facing=" + toFar + "]");
                            }
                        }
                    }
                }
                for (int z = Math.min(near, far); z <= Math.max(near, far); z++) {
                    if (z != near) {
                        in(xa, y + 1, z, "bunk_sink[facing=east]");
                        in(xb, y + 1, z, "bunk_rack_boxes[facing=west]");
                    }
                }
            }
            case SICKBAY -> {
                for (int x = xa; x < xb; x += 2) {
                    if (inside(x, y + 1, far) && inside(x, y + 1, far - in)) {
                        p.set(x, y + 1, far, "bunk_medbed_head[facing=" + toNear + "]");
                        p.set(x, y + 1, far - in, "bunk_medbed_foot[facing=" + toNear + "]");
                        in(x + 1, y + 1, far, "bunk_iv_stand");
                        in(x, y + 2, far, "fit_cabinet[facing=" + toNear + "]");
                        in(x + 1, y + 2, far, "fit_fan[facing=" + toNear + "]");
                    }
                }
                for (int z = Math.min(near, far); z <= Math.max(near, far); z++) {
                    if (z != near && z != far) {
                        in(xa, y + 1, z, "bunk_medcab[facing=east]");
                        in(xa, y + 2, z, "fit_cabinet[facing=east]");
                    }
                }
                // the treatment table under its lamp, the litters on the bulkhead
                int mz = (near + far) / 2;
                in(dx, y + 1, mz, "bunk_medbed_head[facing=east]");
                in(dx + 1, y + 1, mz, "bunk_medbed_foot[facing=east]");
                in(dx, y + 3, mz, "fit_surgical_on");
                for (int z = Math.min(near, far) + 1; z < Math.max(near, far); z += 3) {
                    in(xb, y + 1, z, "fit_litter[facing=west]");
                    in(xb, y + 2, z, "fit_litter[facing=west]");
                }
                in(xb, y + 1, near, "bunk_desk[facing=west]");
                in(xb, y + 2, (near + far) / 2 + in, "bunk_first_aid[facing=west]");
                litter(xa, xb, za, zb, y, "blood_splat", 0.08);
            }
            case BRIG -> {
                int front = far - in * 3;
                for (int x = xa; x <= xb; x++) {
                    for (int dy = 1; dy <= 3; dy++) {
                        in(x, y + dy, front, "bunk_cage[axis=x]");
                    }
                    if ((x - xa) % 3 == 0) {
                        for (int z = Math.min(far, front) + 1; z < Math.max(far, front) + 1; z++) {
                            if (z != front) {
                                for (int dy = 1; dy <= 3; dy++) {
                                    in(x, y + dy, z, "bunk_cage[axis=z]");
                                }
                            }
                        }
                    } else if ((x - xa) % 3 == 1 && inside(x, y + 1, front)) {
                        Doors.abandoned(p, rng, x, y + 1, front, "bunk_door_cage", toFar, Doors.hinge(rng));
                        in(x, y + 1, far, "bunk_bed_head[facing=east]");
                        in(x + 1, y + 1, far, "bunk_bed_foot[facing=east]");
                    }
                }
                in(xa, y + 1, near + in, "bunk_desk[facing=east]");
                in(xa + 1, y + 1, near + in, "bunk_chair[facing=west]");
            }
            case ARMORY -> {
                boolean looted = chance() < 0.3 + hit * 0.2;
                for (int x = xa; x <= xb; x++) {
                    in(x, y + 1, far, (looted && chance() < 0.6 ? "bunk_locker_open" : x % 2 == 0 ? "bunk_gun_rack" : "store_gun_rack")
                            + "[facing=" + toNear + "]");
                }
                for (int z = Math.min(near, far) + 1; z < Math.max(near, far); z++) {
                    if (z == near + in || Math.abs(z - far) < 2) {
                        continue;
                    }
                    in(xa, y + 1, z, "bunk_gun_rack[facing=east]");
                    in(xb, y + 1, z, (looted ? "bunk_locker_open" : "bunk_gun_rack") + "[facing=west]");
                }
                int mid = (near + far) / 2;
                for (int x = xa + 2; x <= xb - 2; x++) {
                    if (!looted || chance() < 0.4) {
                        in(x, y + 1, mid, (x % 2 == 0 ? "bunk_crate_gun" : "bunk_crate_ammo") + "[facing=" + toNear + "]");
                        if (chance() < 0.5) {
                            in(x, y + 2, mid, "bunk_crate_ammo[facing=" + toNear + "]");
                        }
                    }
                }
                // the issue cage inside the door
                int cage = near + in * 2;
                for (int x = xa; x <= xb; x++) {
                    if (x != dx) {
                        for (int dy = 1; dy <= 3; dy++) {
                            in(x, y + dy, cage, "bunk_cage[axis=x]");
                        }
                    }
                }
                if (inside(dx, y + 1, cage)) {
                    Doors.abandoned(p, rng, dx, y + 1, cage, "bunk_door_cage", toFar, Doors.hinge(rng));
                    in(dx, y + 3, cage, "bunk_cage[axis=x]");
                }
                litter(xa, xb, za, zb, y, looted ? "bunk_casings" : "paper", looted ? 0.08 : 0.02);
            }
            case MAGAZINE -> {
                for (int z = Math.min(near, far) + 1; z < Math.max(near, far); z += 2) {
                    String what = Math.floorMod(z, 4) == 1 ? "navy_missile[axis=x]" : "navy_bomb[axis=x]";
                    for (int x = xa + 1; x < xb; x++) {
                        in(x, y + 1, z, what);
                        if (chance() < 0.8) {
                            in(x, y + 2, z, what);
                        }
                    }
                }
                for (int z = Math.min(near, far); z <= Math.max(near, far); z++) {
                    if (z != near) {
                        in(xb, y + 1, z, "bunk_crate_ammo[facing=west]");
                    }
                }
            }
            case STORES -> {
                for (int x = xa; x <= xb; x++) {
                    int k = (x - xa) % 3;
                    if (k == 0) {
                        continue;
                    }
                    for (int z = Math.min(near, far) + 1; z < Math.max(near, far); z++) {
                        in(x, y + 1, z, any("bunk_rack_boxes", "bunk_rack_crates", "bunk_rack_drums", "bunk_rack_boxes")
                                + "[facing=" + (k == 1 ? "west" : "east") + "]");
                    }
                }
                litter(xa, xb, za, zb, y, "cardboard_box[facing=north]", 0.05);
            }
            case WORKSHOP -> {
                for (int x = xa; x <= xb; x++) {
                    in(x, y + 1, far, (x % 4 == 0 ? "bunk_rack_crates" : "bunk_desk") + "[facing=" + toNear + "]");
                    in(x, y + 2, far, "bunk_pegboard[facing=" + toNear + "]");
                }
                for (int x = xa + 1; x < xb; x += 3) {
                    in(x, y + 1, (near + far) / 2, chance() < 0.5 ? "navy_toolbox[facing=" + toNear + "]" : "navy_engine[axis=x]");
                }
                litter(xa, xb, za, zb, y, "debris_chunk[facing=north]", 0.04);
            }
            case STATEROOM, OFFICE -> {
                if (r == Room.STATEROOM && staterooms(xa, xb, near, far, y, in)) {
                    dropped = true;
                    break;
                }
                for (int x = xa + 1; x < xb; x += 3) {
                    for (int z = far; Math.abs(z - near) > 1; z -= in * 3) {
                        if (r == Room.STATEROOM && z == far) {
                            in(x, y + 1, z, "bunk_bed_head[facing=east]");
                            in(x + 1, y + 1, z, "bunk_bed_foot[facing=east]");
                            continue;
                        }
                        in(x, y + 1, z, "bunk_desk[facing=" + toNear + "]");
                        in(x, y + 1, z - in, chance() < 0.3 ? "bunk_chair_down[facing=" + toFar + "]" : "bunk_chair[facing=" + toFar + "]");
                    }
                }
                in(xa, y + 1, near, "bunk_locker[facing=east]");
                in(xb, y + 2, (near + far) / 2, (r == Room.OFFICE ? "navy_whiteboard" : "navy_screen") + "[facing=west]");
                litter(xa, xb, za, zb, y, "paper", 0.1);
            }
            case READY -> {
                for (int x = xa; x <= xb - 3; x += 2) {
                    for (int z = Math.min(near, far) + (port ? 0 : 1); z <= Math.max(near, far) - (port ? 1 : 0); z++) {
                        in(x, y + 1, z, "navy_ready_seat[facing=east]");
                    }
                }
                for (int z = Math.min(near, far); z <= Math.max(near, far); z++) {
                    in(xb, y + 2, z, (Math.floorMod(z, 3) == 0 ? "navy_whiteboard" : "navy_screen") + "[facing=west]");
                }
                in(xb - 1, y + 1, (near + far) / 2, "bunk_desk[facing=west]");
                litter(xa, xb, za, zb, y, "paper", 0.06);
            }
            case CONTROL -> {
                for (int x = xa; x <= xb; x++) {
                    in(x, y + 1, far, "ship_console[facing=" + toNear + "]");
                    in(x, y + 2, far, "navy_screen[facing=" + toNear + "]");
                    if (x % 2 == 0) {
                        in(x, y + 1, far - in, "bunk_chair[facing=" + toFar + "]");
                    }
                }
                in((xa + xb) / 2, y + 1, (near + far) / 2, "bunk_map_table");
                in(xa, y + 1, (near + far) / 2, "bunk_server[facing=east]");
                in(xb, y + 1, (near + far) / 2, "bunk_server[facing=west]");
            }
            case ARREST -> {
                int mid = (near + far) / 2;
                for (int x = xa + 1; x < xb; x++) {
                    in(x, y + 1, mid - 2, "navy_turbine[axis=x]");
                    in(x, y + 1, mid + 1, "navy_turbine[axis=x]");
                    in(x, y + 3, mid, x % 5 == 0 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
                }
                in(xb, y + 1, mid, "bunk_tank");
                in(xb, y + 2, mid, "bunk_tank_top");
                in(xa, y + 1, near + in, "bunk_switchboard[facing=east]");
                in(xa, y + 2, near + in, "bunk_switchboard_top[facing=east]");
            }
            case CATAPULT -> {
                int mid = (near + far) / 2;
                for (int x = xa; x <= xb; x++) {
                    in(x, y + 1, mid, x % 6 == 0 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
                    in(x, y + 3, far, "vessel_pipe[axis=x]");
                }
                for (int x = xa + 1; x < xb; x += 4) {
                    in(x, y + 1, far, "bunk_tank");
                    in(x, y + 2, far, "bunk_tank_top");
                }
                in(xb, y + 1, near + in, "ship_console[facing=west]");
                in(xa, y + 1, near + in, "bunk_switchboard[facing=east]");
            }
            case CAPTAIN -> captain(xa, xb, near, far, y, in);
            case HEAD -> head(xa, xb, near, far, y, in);
            default -> {
            }
        }
        if (r != Room.TRUNK && !dropped) {
            overheads(r, xa, xb, near, far, y, toNear, toFar, dx, r == Room.SICKBAY);
            dressing(xa, xb, Math.min(za, zb), Math.max(za, zb), y, (int) ((xb - xa) * (hi2 - lo2 + 1) * (0.015 + hit * 0.03)));
        }
    }

    // ------------------------------------------------------------------ officers' country (from the wardroom, stateroom and head photos)

    /** A troffer in a drop ceiling, its tube along axis: on the main supply mostly dead or failing, on the emergency one mostly lit. */
    private String troffer(String axis, boolean lit) {
        double r = chance();
        String t = lit ? (r < 0.75 ? "fit_troffer_on" : r < 0.95 ? "fit_troffer_flicker" : "fit_troffer")
                : r < 0.1 ? "fit_troffer_on" : r < 0.32 ? "fit_troffer_flicker" : "fit_troffer";
        return t + "[axis=" + axis + "]";
    }

    /** A lamp's lit (name_on) or dead (name) block, lit with the chance given. */
    private String lamp(String name, double on) {
        return chance() < on ? name + "_on" : name;
    }

    /** Lines a steel bulkhead (the cells at x, z from y + 1 to y + 3) with the joiner laminate, its face looking f. */
    private void line(int x, int z, int y, String f, String lower, String upper) {
        for (int dy = 1; dy <= 3; dy++) {
            if ("navy_wall".equals(p.get(x, y + dy, z))) {
                p.set(x, y + dy, z, (dy == 1 ? lower : upper) + "[facing=" + f + "]");
            }
        }
    }

    private void line(int x, int z, int y, String f) {
        line(x, z, y, f, "fit_lining", "fit_lining");
    }

    /**
     * A drop ceiling in the top of the y + 3 cells from x0 to x1 and z0 to z1 wherever no light took
     * the place first: a tile out here and there (the pipes showing over it), down on the floor below.
     */
    private void ceiling(int x0, int x1, int z0, int z1, int y) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (!free(x, y + 3, z)) {
                    continue;
                }
                if (chance() < 0.025 + hit * 0.05) {
                    String f = any("north", "east", "south", "west");
                    p.set(x, y + 3, z, "fit_ceiling_open[facing=" + f + "]");
                    p.setIfAir(x, y + 1, z, "fit_ceiling_fallen[facing=" + f + "]");
                } else {
                    p.set(x, y + 3, z, "fit_ceiling");
                }
            }
        }
    }

    /** True if every cell from xa to xb over rows z0 to z0 + in * rows (not past far) is inside her, floor to deckhead. */
    private boolean fits(int xa, int xb, int z0, int in, int rows, int far, int y) {
        if ((z0 + in * rows - far) * in > 0) {
            return false;
        }
        for (int j = 0; j <= rows; j++) {
            for (int x = xa; x <= xb; x++) {
                for (int dy = 1; dy <= 3; dy++) {
                    if (!inside(x, y + dy, z0 + in * j)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * A compartment of officers' staterooms (as in the stateroom photos): a narrow passage of joiner
     * bulkheads in from its door, the staterooms off it either side behind their own doors, and at
     * its end a linen locker where the hull leaves room. Two officers share a stateroom (two-tier
     * berth, two desks); now and then a whole compartment is singles for department heads. False if
     * the compartment is too shallow for even one pair.
     */
    private boolean staterooms(int xa, int xb, int near, int far, int y, int in) {
        int dx = (xa + xb) / 2;
        if (xb - xa != 10) {
            return false;
        }
        boolean single = chance() < 0.3;
        int stride = single ? 5 : 4;
        int n = 0;
        while (fits(xa, xb, near + in * n * stride, in, stride - 1, far, y)) {
            n++;
        }
        if (n == 0) {
            return false;
        }
        String fwd = in > 0 ? "south" : "north", back = in > 0 ? "north" : "south";
        int last = near + in * (n * stride - 1);
        for (int z = near; (z - last) * in <= 0; z += in) {
            for (int x = xa; x <= xb; x++) {
                p.set(x, y, z, x == dx ? "navy_tile_blue" : "fit_carpet");
            }
            for (int dy = 1; dy <= 3; dy++) {
                p.set(dx - 1, y + dy, z, "fit_joiner");
                p.set(dx + 1, y + dy, z, "fit_joiner");
            }
        }
        for (int k = 0; k < n; k++) {
            int zp = near + in * (k * stride + stride - 1);
            for (int x = xa; x <= xb; x++) {
                for (int dy = 1; dy <= 3; dy++) {
                    if (x != dx || k == n - 1) {
                        p.set(x, y + dy, zp, "fit_joiner");
                    }
                }
            }
            for (int s : new int[] {-1, 1}) {
                cabin(dx, s, near + in * k * stride, in, single, y);
            }
        }
        // the passage: night lights in its ceiling, the odd downlight, a lantern at the door
        for (int z = near; (z - last) * in < 0; z += in) {
            int m = Math.abs(z - near);
            if (m % 4 == 1) {
                p.set(dx, y + 3, z, red());
            } else if (m % 4 == 3) {
                p.set(dx, y + 3, z, lamp("fit_downlight", 0.1));
            }
        }
        ceiling(dx, dx, near, last - in, y);
        in(dx, y + 2, near + in, lantern("east"));
        // behind the last partition, if the hull leaves room: the linen locker
        int zl = last + in;
        if ((zl - far) * in <= 0 && inside(dx, y + 1, zl) && inside(dx, y + 2, zl)) {
            Doors.abandoned(p, rng, dx, y + 1, last, "fit_door_cabin", fwd, Doors.hinge(rng));
            for (int z = zl; (z - far) * in <= 0 && inside(dx, y + 1, z); z += in) {
                for (int x = xa; x <= xb; x++) {
                    if ((x == xa || x == xb || Math.abs(x - dx) == 3) && chance() < 0.85) {
                        in(x, y + 1, z, any("bunk_rack_boxes", "bunk_rack_crates", "bunk_rack_boxes") + "[facing=" + (x < dx ? "east" : "west") + "]");
                    }
                }
                if (Math.abs(z - zl) % 3 == 1) {
                    in(dx, y + 3, z, lamp("fit_globe_down", 0.1));
                }
            }
        }
        return true;
    }

    /**
     * One stateroom on side s (-1 to the west of the passage at dx, 1 to the east), its rows from z0
     * on away from the door (in): the door off the passage in the middle row; the berth along the
     * frame, its head against the partition (two tiers for two officers, one for a department
     * head); the fold-down desks under their shelves against the same partition, chairs at them;
     * the little sink and its mirror cabinet by the door; wardrobes against the next partition; a
     * laundry bag, a painting; carpet, laminate over the steel, a drop ceiling with one troffer.
     */
    private void cabin(int dx, int s, int z0, int in, boolean single, int y) {
        String into = s > 0 ? "east" : "west", out = s > 0 ? "west" : "east";
        String fwd = in > 0 ? "south" : "north", back = in > 0 ? "north" : "south";
        int rows = single ? 4 : 3;
        int[] xs = {dx + 2 * s, dx + 3 * s, dx + 4 * s, dx + 5 * s};
        int[] zs = new int[rows];
        for (int j = 0; j < rows; j++) {
            zs[j] = z0 + in * j;
        }
        Doors.abandoned(p, rng, dx + s, y + 1, zs[1], "fit_door_cabin", into, Doors.hinge(rng));
        for (int j = 0; j < rows; j++) {
            line(xs[3] + s, zs[j], y, out);
        }
        for (int x : xs) {
            line(x, z0 - in, y, fwd);
        }
        // the berth
        p.set(xs[3], y + 1, zs[0], lamp("fit_berth_lo_head", 0.12) + "[facing=" + fwd + "]");
        p.set(xs[3], y + 1, zs[1], "fit_berth_lo_foot[facing=" + fwd + "]");
        if (single) {
            p.set(xs[3], y + 2, zs[1], "fit_painting_" + any("a", "b", "c") + "[facing=" + out + "]");
        } else {
            p.set(xs[3], y + 2, zs[0], lamp("fit_berth_hi_head", 0.12) + "[facing=" + fwd + "]");
            p.set(xs[3], y + 2, zs[1], "fit_berth_hi_foot[facing=" + fwd + "]");
        }
        // the desks, their shelves, their chairs
        for (int i = 1; i <= (single ? 1 : 2); i++) {
            p.set(xs[i], y + 1, zs[0], "fit_secretary[facing=" + fwd + "]");
            double r = chance();
            p.set(xs[i], y + 2, zs[0], (r < 0.08 ? "fit_shelves_on" : r < 0.22 ? "fit_shelves_flicker" : "fit_shelves") + "[facing=" + fwd + "]");
            if (chance() < 0.85) {
                p.set(xs[i], y + 1, zs[1], (chance() < 0.25 ? "office_chair_fallen" : "office_chair") + "[facing=" + back + "]");
            }
        }
        // the sink and its mirror by the door
        p.set(xs[0], y + 1, zs[0], "fit_sink_cabin[facing=" + fwd + "]");
        p.set(xs[0], y + 2, zs[0], lamp("fit_medcab", 0.08) + "[facing=" + fwd + "]");
        // the wardrobes, and in a single the armchair and a lamp
        int jw = rows - 1;
        for (int i = single ? 3 : 2; i <= 3; i++) {
            p.set(xs[i], y + 1, zs[jw], "fit_wardrobe_lo[facing=" + back + "]");
            p.set(xs[i], y + 2, zs[jw], "fit_wardrobe_hi[facing=" + back + "]");
        }
        if (single) {
            p.set(xs[2], y + 1, zs[jw], "home_armchair_blue[facing=" + back + "]");
            p.set(xs[1], y + 1, zs[jw], "home_floor_lamp");
            p.set(xs[0], y + 1, zs[jw], chance() < 0.6 ? "fit_laundry_bag" : "home_clothes_pile");
        } else {
            p.set(xs[0], y + 1, zs[jw], chance() < 0.6 ? "fit_laundry_bag" : "home_clothes_pile");
        }
        p.set(xs[0], y + 2, zs[jw], "fit_painting_" + any("a", "b", "c") + "[facing=" + back + "]");
        if (chance() < 0.5) {
            p.set(xs[1], y + 2, zs[jw], "bunk_clock[facing=" + back + "]");
        }
        // the ceiling and its one light, the floor's litter
        p.set(xs[1], y + 3, zs[1], troffer("x", false));
        ceiling(xs[0], xs[3], zs[0], zs[jw], y);
        for (int j = 0; j < rows; j++) {
            for (int x : xs) {
                if (chance() < 0.05 + hit * 0.05) {
                    p.setIfAir(x, y + 1, zs[j], any("paper", "paper", "glass_shards", "blood_splat", "home_shoes"));
                }
            }
        }
    }

    /**
     * The captain's in-port cabin (as in the cabin photo: framed ships on light walls, a chess set mid
     * game, a lamp, a screen): the day cabin in from the door with its conference table and chairs,
     * the sofa and coffee table, the chess table, the desk and bookcase; through a joiner bulkhead
     * the sleeping cabin, a proper bed, the wardrobe and dresser; behind that the captain's own head.
     */
    private void captain(int xa, int xb, int near, int far, int y, int in) {
        int dx = (xa + xb) / 2;
        String fwd = in > 0 ? "south" : "north", back = in > 0 ? "north" : "south";
        boolean lit = chance() < 0.45;
        int depth = Math.abs(far - near) + 1;
        int d1 = near + in * Math.min(9, depth - 1);
        int wall1 = d1 + in, s1 = wall1 + in * 6, wall2 = s1 + in;
        boolean sleep = (s1 - far) * in <= 0 && inside(dx, y + 1, s1);
        boolean ownHead = sleep && (wall2 + in * 2 - far) * in <= 0 && inside(xb - 1, y + 1, wall2 + in * 2);
        int end = ownHead ? far : sleep ? s1 : d1;
        for (int z = near; (z - end) * in <= 0; z += in) {
            line(xa - 1, z, y, "east");
            line(xb + 1, z, y, "west");
        }
        for (int x = xa; x <= xb; x++) {
            line(x, near - in, y, fwd);
        }
        // the day cabin, the rug under its conference table first
        int t0 = near + in * 3;
        rug(dx - 2, t0 - in * 2, dx + 2, t0 + in * 3, y);
        for (int x = dx - 1; x <= dx + 1; x++) {
            for (int k = 0; k < 2; k++) {
                in(x, y + 1, t0 + in * k, "home_table");
            }
            in(x, y + 1, t0 - in, "office_chair[facing=" + fwd + "]");
            in(x, y + 1, t0 + in * 2, "office_chair[facing=" + back + "]");
        }
        in(dx - 2, y + 1, t0, "office_chair[facing=east]");
        in(dx + 2, y + 1, t0 + in, chance() < 0.3 ? "office_chair_fallen[facing=west]" : "office_chair[facing=west]");
        in(dx, y + 2, t0, "home_lamp_table_low");
        // the sofa against the frame, the coffee table, an armchair
        int sz = near + in * 6;
        in(xa, y + 1, sz, "home_sofa_blue_l[facing=east]");
        in(xa, y + 1, sz + in, "home_sofa_blue_m[facing=east]");
        in(xa, y + 1, sz + 2 * in, "home_sofa_blue_r[facing=east]");
        in(xa + 1, y + 1, sz + in, "home_coffee_table[facing=east]");
        in(xa + 2, y + 1, sz + 2 * in, "home_armchair_blue[facing=west]");
        in(xa, y + 2, sz + in, "fit_painting_" + any("a", "b", "c") + "[facing=east]");
        in(xa, y + 1, near + in, "home_floor_lamp");
        in(xa, y + 1, near + in * 2, "home_tv[facing=east]");
        in(xa, y + 2, near + in * 3, "fit_painting_" + any("a", "b", "c") + "[facing=east]");
        // the chess table, a game left mid-way
        int cx = xb - 2, cz = near + in * 7;
        in(cx, y + 1, cz, "fit_chess[facing=" + fwd + "]");
        in(cx, y + 1, cz - in, "office_chair[facing=" + fwd + "]");
        in(cx, y + 1, cz + in, "office_chair[facing=" + back + "]");
        // the desk, its chair, the bookcases
        in(xb, y + 1, near + in, "home_desk_pc[facing=west]");
        in(xb - 1, y + 1, near + in, "office_chair[facing=east]");
        for (int k = 3; k <= 4; k++) {
            in(xb, y + 1, near + in * k, "home_bookcase[facing=west]");
            in(xb, y + 2, near + in * k, "home_bookcase_top[facing=west]");
        }
        in(xb, y + 2, near + in * 6, "fit_painting_" + any("a", "b", "c") + "[facing=west]");
        in(xb, y + 1, d1, "home_plant");
        in(dx + 1, y + 2, near, lantern(fwd));
        // the day cabin's lights: downlights round a troffer over the table
        in(dx, y + 3, t0, troffer("x", lit));
        for (int x : new int[] {xa + 1, dx, xb - 1}) {
            for (int z : new int[] {near + in, near + in * 6, d1 - in}) {
                if (free(x, y + 3, z)) {
                    p.set(x, y + 3, z, lamp("fit_downlight", lit ? 0.7 : 0.08));
                }
            }
        }
        ceiling(xa, xb, near, d1, y);
        if (!sleep) {
            in(dx, y + 2, d1, "fit_crest[facing=" + back + "]");
            return;
        }
        // the sleeping cabin through the joiner bulkhead
        for (int x = xa; x <= xb; x++) {
            for (int dy = 1; dy <= 3; dy++) {
                p.set(x, y + dy, wall1, "fit_joiner");
            }
        }
        Doors.abandoned(p, rng, dx, y + 1, wall1, "fit_door_cabin", fwd, Doors.hinge(rng));
        in(dx + 2, y + 2, d1, "fit_crest[facing=" + back + "]");
        int bz = s1;
        in(xa + 2, y + 1, bz, "home_bed_blue_head[facing=" + back + "]");
        in(xa + 2, y + 1, bz - in, "home_bed_blue_foot[facing=" + back + "]");
        in(xa + 1, y + 1, bz, "home_nightstand[facing=" + back + "]");
        in(xa + 3, y + 1, bz, "home_nightstand[facing=" + back + "]");
        in(xa + 2, y + 2, bz, "fit_painting_" + any("a", "b", "c") + "[facing=" + back + "]");
        in(xa, y + 1, wall1 + in, "home_dresser[facing=east]");
        for (int k = 1; k <= 2; k++) {
            in(xb, y + 1, wall1 + in * k, "fit_wardrobe_lo[facing=west]");
            in(xb, y + 2, wall1 + in * k, "fit_wardrobe_hi[facing=west]");
        }
        in(xb - 1, y + 1, bz, chance() < 0.5 ? "fit_laundry_bag" : "home_clothes_pile");
        in(xa + 2, y + 3, bz - in * 3, troffer("x", lit));
        in(xa + 1, y + 3, bz - in, lamp("fit_downlight", lit ? 0.6 : 0.06));
        ceiling(xa, xb, wall1 + in, s1, y);
        if (!ownHead) {
            return;
        }
        // the captain's head
        for (int x = xa; x <= xb; x++) {
            for (int dy = 1; dy <= 3; dy++) {
                p.set(x, y + dy, wall2, "fit_joiner");
            }
        }
        Doors.abandoned(p, rng, xb - 1, y + 1, wall2, "fit_door_cabin", fwd, Doors.hinge(rng));
        for (int z = wall2 + in; (z - far) * in <= 0; z += in) {
            for (int x = xa; x <= xb; x++) {
                if (inside(x, y + 1, z)) {
                    p.set(x, y, z, "navy_tile_grey");
                }
            }
        }
        int hz = wall2 + in * 2;
        in(xb, y + 1, hz, "fit_toilet_steel[facing=west]");
        in(xb - 3, y + 1, wall2 + in, "fit_sink_cabin[facing=" + fwd + "]");
        in(xb - 3, y + 2, wall2 + in, lamp("fit_medcab", lit ? 0.5 : 0.05) + "[facing=" + fwd + "]");
        in(xb - 5, y + 1, hz, "bunk_shower[facing=" + fwd + "]");
        in(xb - 2, y + 3, hz, tube("x", lit));
    }

    /** A rug on floor y from (x0, z0) to (x1, z1), edged and cornered as Trades.rug lays them. */
    private void rug(int x0, int z0, int x1, int z1, int y) {
        String c = chance() < 0.6 ? "blue" : "red";
        int za = Math.min(z0, z1), zb = Math.max(z0, z1);
        for (int x = x0; x <= x1; x++) {
            for (int z = za; z <= zb; z++) {
                boolean w = x == x0, e = x == x1, n = z == za, s = z == zb;
                String f = (w || e) && (n || s) ? "home_rug_" + c + "_corner[facing=" + (n ? (w ? "north" : "east") : e ? "south" : "west") + "]"
                        : n ? "home_rug_" + c + "_edge[facing=north]" : s ? "home_rug_" + c + "_edge[facing=south]"
                        : w ? "home_rug_" + c + "_edge[facing=west]" : e ? "home_rug_" + c + "_edge[facing=east]" : "home_rug_" + c;
                in(x, y + 1, z, f);
            }
        }
    }

    /**
     * The officers' head (as in the head photo): a row of stalls in orange laminate on stainless
     * posts along the far bulkhead, their doors in a row; the black counter with its stainless
     * bowls and the long mirror down one side, the showers down the other.
     */
    private void head(int xa, int xb, int near, int far, int y, int in) {
        String fwd = in > 0 ? "south" : "north", back = in > 0 ? "north" : "south";
        int dx = (xa + xb) / 2;
        // with the stalls facing back their panels fall to the east when back is north, to the west when south
        boolean east = back.equals("north");
        int n = 5;
        for (int k = 0; k < n; k++) {
            int x = east ? xa + k : xb - k;
            if (!inside(x, y + 1, far) || !inside(x, y + 1, far - in)) {
                continue;
            }
            p.set(x, y + 1, far, "fit_toilet_stall[facing=" + back + "]");
            p.set(x, y + 2, far, "fit_stall_top[facing=" + back + "]");
            Doors.abandoned(p, rng, x, y + 1, far - in, "fit_door_stall", fwd, Doors.hinge(rng));
        }
        int sx = east ? xb : xa;
        String sf = east ? "west" : "east";
        for (int z = near + in; (z - (far - in * 2)) * in <= 0; z += in) {
            in(sx, y + 1, z, "fit_vanity_black[facing=" + sf + "]");
            in(sx, y + 2, z, "fit_mirror_long[facing=" + sf + "]");
        }
        int wx = east ? xa : xb;
        String wf = east ? "east" : "west";
        for (int z = near + in; (z - (far - in * 3)) * in <= 0; z += in * 2) {
            in(wx, y + 1, z, "bunk_shower[facing=" + wf + "]");
            in(wx + (east ? 1 : -1), y + 1, z, "home_shower_curtain[facing=" + wf + "]");
        }
        in(dx, y + 1, (near + far) / 2, chance() < 0.5 ? "home_trash_can" : "home_bin_bags");
        litter(xa, xb, Math.min(near, far), Math.max(near, far), y, "paper", 0.05);
        litter(xa, xb, Math.min(near, far), Math.max(near, far), y, "bunk_puddle", 0.04);
    }

    /**
     * The wardroom on the gallery deck (as in the wardroom photos): two frames thrown into one long
     * room, cherry pillars where the frame stood, cream tiles, cream walls over a cherry wainscot,
     * paintings of ships between brass sconces, round tables under white cloths, the chairs in their
     * star-pattern covers, the serving line and its cooler aft, the screens and the mess's plaque
     * forward. When it all went wrong it was the battle dressing station: tables cleared for the
     * wounded, litters and body bags between them, blood; so it was put on the emergency supply and
     * most of its lights still burn.
     */
    private void wardroom() {
        int y = GALLERY, x0 = WARD_X0 + 1, x1 = WARD_X0 + 2 * FRAME - 1, z0 = -22, z1 = -3, mid = WARD_X0 + FRAME;
        for (int z = z0; z <= z1; z++) {
            for (int dy = 1; dy <= 3; dy++) {
                p.set(mid, y + dy, z, z == -17 || z == -8 ? "fit_pillar" : "air");
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, y, z, "fit_tile_cream");
                for (int dy = 1; dy <= 3; dy++) {
                    if (x != mid) {
                        p.set(x, y + dy, z, "air");
                    }
                }
            }
            line(x, z0 - 1, y, "south", "fit_lining_wainscot", "fit_lining_cream");
            line(x, z1 + 1, y, "north", "fit_lining_wainscot", "fit_lining_cream");
        }
        for (int z = z0; z <= z1; z++) {
            line(x0 - 1, z, y, "east", "fit_lining_wainscot", "fit_lining_cream");
            line(x1 + 1, z, y, "west", "fit_lining_wainscot", "fit_lining_cream");
        }
        // the doors off the passage, an escape sign by each
        for (int dxr : new int[] {x0 + 3, x1 - 3}) {
            Doors.abandoned(p, rng, dxr, y + 1, z1 + 1, "ship_door", "north", Doors.hinge(rng));
            p.set(dxr, y + 1, z1 + 2, "air");
            p.set(dxr, y + 2, z1 + 2, "air");
            p.set(dxr + 1, y + 2, z1, "fit_exit[facing=north]");
        }
        p.set(x0 + 5, y + 2, z1, "fit_st_wardroom[facing=north]");
        // the serving line along the after bulkhead, the cooler, the coffee
        for (int z = z0 + 2; z <= z0 + 9; z++) {
            p.set(x0, y + 1, z, "fit_serving[facing=east]");
        }
        p.set(x0, y + 1, z0 + 10, "store_cooler[facing=east]");
        p.set(x0, y + 2, z0 + 10, "store_cooler_top[facing=east]");
        p.set(x0, y + 1, z0 + 11, "store_cooler[facing=east]");
        p.set(x0, y + 2, z0 + 11, "store_cooler_top[facing=east]");
        for (int z = z0 + 12; z <= z0 + 14; z++) {
            p.set(x0, y + 1, z, "fit_counter_steel[facing=east]");
            p.set(x0, y + 2, z, z == z0 + 13 ? "fit_urn[facing=east]" : "home_dishes");
        }
        // the round tables and their chairs, a third of them used for the wounded
        String[] round = {"north", "east", "south", "west"};
        for (int tx = x0 + 4; tx <= x1 - 2; tx += 4) {
            for (int tz = z0 + 3; tz <= z1 - 4; tz += 4) {
                boolean used = chance() < 0.35 + hit * 0.2;
                p.set(tx, y + 1, tz, (used ? "fit_round_table_mess" : "fit_round_table") + "[facing=" + any(round) + "]");
                int[][] seats = {{0, -1, 2}, {1, 0, 3}, {0, 1, 0}, {-1, 0, 1}};
                for (int[] c : seats) {
                    double r = chance();
                    if (r < (used ? 0.35 : 0.1)) {
                        continue;
                    }
                    String chair = r < (used ? 0.6 : 0.2) ? "fit_chair_ward_tipped[facing=" + any(round) + "]"
                            : "fit_chair_ward[facing=" + round[c[2]] + "]";
                    p.set(tx + c[0], y + 1, tz + c[1], chair);
                }
            }
        }
        // the wounded's litters and the dead's bags in the aisles, drips, blood
        for (int i = 0; i < 4 + rng.nextInt(4); i++) {
            int x = x0 + 3 + rng.nextInt(x1 - x0 - 4), z = z0 + 1 + 4 * rng.nextInt(5);
            if (z > z1 || !free(x, y + 1, z) || !free(x + 1, y + 1, z)) {
                continue;
            }
            if (chance() < 0.5) {
                p.set(x, y + 1, z, "bunk_medbed_head[facing=east]");
                p.set(x + 1, y + 1, z, "bunk_medbed_foot[facing=east]");
                p.setIfAir(x, y + 1, z + (z > z0 ? -1 : 1), "bunk_iv_stand");
            } else {
                p.set(x, y + 1, z, "bunk_bodybag_head[facing=east]");
                p.set(x + 1, y + 1, z, "bunk_bodybag[facing=east]");
            }
            p.setIfAir(x + 2, y + 1, z, chance() < 0.5 ? "home_blood_pool" : "home_blood_drag[facing=" + any(round) + "]");
        }
        litter(x0, x1, z0, z1, y, "blood_splat", 0.03 + hit * 0.03);
        litter(x0, x1, z0, z1, y, "paper", 0.03);
        // paintings between sconces along both long walls; the screens and the plaque forward; palms in the corners
        for (int x = x0 + 2; x <= x1 - 2; x += 5) {
            for (int[] w : new int[][] {{z0, 0}, {z1, 1}}) {
                if (w[1] == 1 && (Math.abs(x - (x0 + 3)) <= 1 || Math.abs(x - (x1 - 3)) <= 1 || Math.abs(x - (x0 + 5)) <= 1)) {
                    continue;
                }
                String f = w[1] == 0 ? "south" : "north";
                p.set(x, y + 2, w[0], "fit_painting_" + any("a", "b", "c") + "[facing=" + f + "]");
                for (int sx : new int[] {x - 1, x + 1}) {
                    if (sx != mid) {
                        p.set(sx, y + 2, w[0], lamp("fit_sconce", 0.85) + "[facing=" + f + "]");
                    }
                }
            }
        }
        p.set(x1, y + 2, (z0 + z1) / 2, "fit_crest[facing=west]");
        p.set(x1, y + 2, z0 + 4, "navy_screen[facing=west]");
        p.set(x1, y + 2, z1 - 4, "navy_screen[facing=west]");
        for (int[] c : new int[][] {{x1, z0}, {x1, z1}, {x0 + 1, z1}}) {
            p.setIfAir(c[0], y + 1, c[1], chance() < 0.7 ? "home_plant" : "home_plant_dead");
        }
        // the ceiling: rows of troffers, downlights between, on the emergency supply
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == mid) {
                    continue;
                }
                if ((x - x0) % 4 == 2 && (z - z0) % 4 == 1) {
                    p.set(x, y + 3, z, troffer("x", true));
                } else if ((x - x0) % 4 == 0 && (z - z0) % 4 == 3) {
                    p.set(x, y + 3, z, lamp("fit_downlight", 0.6));
                }
            }
        }
        ceiling(x0, x1, z0, z1, y);
    }

    /** Scatters one thing over a compartment's floor. */
    private void litter(int xa, int xb, int za, int zb, int y, String what, double density) {
        for (int x = xa; x <= xb; x++) {
            for (int z = Math.min(za, zb); z <= Math.max(za, zb); z++) {
                if (chance() < density && inside(x, y + 1, z)) {
                    p.setIfAir(x, y + 1, z, what);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the decks

    /** The gallery deck under the flight deck: two passages, the ready rooms, offices and the launch and arresting machinery. */
    private void gallery() {
        level(GALLERY, FRAME0, 312, new int[] {-26, -23, -2, 1}, (xa, xb, za, zb) -> {
            int x = (xa + xb) / 2;
            boolean stbd = za > 0;
            if (stbd && (trunkIn(xa, xb) || xb >= ISLAND_STAIR - 2 && xa <= ISLAND_STAIR + 5)) {
                return Room.TRUNK;
            }
            for (int[] c : CATS) {
                if (x >= c[0] - 4 && x <= c[1] && c[2] >= za - 1 && c[2] <= zb + 1) {
                    return Room.CATAPULT;
                }
            }
            if (x >= 40 && x <= 92 && za > -40 && zb < 12) {
                return Room.ARREST;
            }
            if (stbd && x >= IX0 && x <= IX1 + 8) {
                return Room.CONTROL;
            }
            if (za >= -22 && zb <= -3 && xa > WARD_X0 && xb < WARD_X0 + 2 * FRAME) {
                return Room.WARDROOM;
            }
            if (stbd && xa == CAPTAIN_X0 + 1) {
                return Room.CAPTAIN;
            }
            double r = chance();
            if (x > 96 && x < 236 && r < 0.35) {
                return Room.READY;
            }
            return r < 0.55 ? Room.STATEROOM : r < 0.63 ? Room.HEAD : r < 0.76 ? Room.OFFICE : r < 0.88 ? Room.STORES : Room.WORKSHOP;
        });
        wardroom();
        // doors out onto the catwalks along both sides
        for (int x = FRAME0 + FRAME / 2; x < 312; x += FRAME * 2) {
            for (int s : new int[] {-1, 1}) {
                int z = edgeZ(x, GALLERY + 1, s);
                if (z != Integer.MIN_VALUE && inside(x, GALLERY + 1, z - s) && kind(x, GALLERY + 1, z) == 1 && plan(x, z)) {
                    Doors.place(p, x, GALLERY + 1, z, "ship_door", s > 0 ? "south" : "north", "left", chance() < 0.3);
                    p.set(x, GALLERY + 1, z - s, "air");
                    p.set(x, GALLERY + 2, z - s, "air");
                }
            }
        }
    }

    /** The second, third and fourth decks under the hangar. */
    private void below() {
        level(DECKS[0], FRAME0, 312, new int[] {-2, 1}, (xa, xb, za, zb) -> {
            int x = (xa + xb) / 2;
            boolean port = zb < 0;
            if (!port && trunkIn(xa, xb)) {
                return Room.TRUNK;
            }
            if (x < 60) {
                return Room.BERTHING;
            }
            if (x < 108) {
                return !port && x < 84 ? Room.GALLEY : Room.MESS;
            }
            if (x < 132) {
                return port ? Room.BRIG : Room.SICKBAY;
            }
            if (x < 204) {
                double r = chance();
                return r < 0.5 ? Room.BERTHING : r < 0.7 ? Room.OFFICE : r < 0.85 ? Room.STORES : Room.WORKSHOP;
            }
            if (x < 252) {
                return Room.ARMORY;
            }
            return x < 292 ? (chance() < 0.6 ? Room.BERTHING : Room.WORKSHOP) : Room.STORES;
        });
        level(DECKS[1], FRAME0, 300, new int[] {-2, 1}, (xa, xb, za, zb) -> {
            int x = (xa + xb) / 2;
            boolean port = zb < 0;
            if (!port && trunkIn(xa, xb)) {
                return Room.TRUNK;
            }
            if (x >= 132 && x < 168) {
                return port ? Room.MESS : Room.GALLEY;
            }
            if (x >= 216 && x < 240 && port) {
                return Room.ARMORY;
            }
            double r = chance();
            return r < 0.6 ? Room.BERTHING : r < 0.75 ? Room.STORES : r < 0.88 ? Room.WORKSHOP : Room.OFFICE;
        });
        Picker fourth = (xa, xb, za, zb) -> za > 0 && trunkIn(xa, xb) ? Room.TRUNK : chance() < 0.75 ? Room.MAGAZINE : Room.STORES;
        level(DECKS[2], FRAME0, 108, new int[] {-2, 1}, fourth);
        level(DECKS[2], 228, 288, new int[] {-2, 1}, fourth);
    }

    /**
     * The machinery spaces under the fourth deck from x 108 to 228: two main machinery rooms with
     * their turbines and gearing, two auxiliary rooms of generators and switchboards; gratings round
     * them at the fourth deck's height and down to the tank top.
     */
    private void machinery() {
        int x0 = 108, x1 = 228, top = DECKS[1] - 1, bottom = TANK + 1, walk = DECKS[2];
        int[] walls = {x0, 132, 168, 192, x1};
        for (int x = x0; x <= x1; x++) {
            boolean bulkhead = false;
            for (int w : walls) {
                bulkhead |= x == w;
            }
            for (int z = Z0; z < Z0 + NZ; z++) {
                if (!bulkhead && kind(x, TANK, z) == 2) {
                    p.set(x, TANK, z, "fit_deckplate");
                }
                for (int y = bottom; y <= top; y++) {
                    if (!inside(x, y, z)) {
                        continue;
                    }
                    if (bulkhead) {
                        p.set(x, y, z, "navy_wall");
                    } else if (y == walk) {
                        // the gratings: along both sides, across at each end
                        boolean side = Math.abs(z + 0.5) > 9 || x == x0 + 1 || x == x1 - 1;
                        p.set(x, y, z, plated(x, y, z) ? "navy_wall" : side ? "bunk_walkway" : "air");
                    }
                }
                if (!bulkhead && inside(x, walk, z) && Math.abs(z + 0.5) > 9 && Math.abs(z + 0.5) < 11) {
                    in(x, walk + 1, z, "vessel_rail[axis=x]");
                }
            }
            if (bulkhead) {
                for (int z : new int[] {-12, 11}) {
                    if (inside(x, walk + 1, z)) {
                        Doors.abandoned(p, rng, x, walk + 1, z, "ship_door", "east", Doors.hinge(rng));
                    }
                }
            }
            if (!bulkhead && x % 4 == 0) {
                for (int z : new int[] {-12, 11}) {
                    in(x, top, z, chance() < 0.15 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
                }
                for (int s : new int[] {-1, 1}) {
                    int e = edgeZ(x, top - 1, s);
                    if (e != Integer.MIN_VALUE) {
                        in(x, top - 1, e - 2 * s, x % 24 == 8 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
                    }
                }
            }
        }
        for (int i = 0; i + 1 < walls.length; i++) {
            int a = walls[i] + 1, b = walls[i + 1] - 1;
            boolean main = i % 2 == 0;
            // overhead: the lagged steam line and the firemain over the plant, cable trays outboard, a deep beam every fourth frame
            for (int x = a; x <= b; x++) {
                boolean bm = (x - a) % 4 == 0;
                for (int[] run : new int[][] {{-3, 0}, {2, 1}, {-7, 2}, {6, 2}}) {
                    String r = run[1] == 0 ? "fit_pipes_a" : run[1] == 1 ? "fit_pipes_b" : "fit_tray";
                    over(x, top, run[0], bm ? beam(r, "x") : r + "[axis=x]");
                }
            }
            if (main) {
                // two turbines, the gearing between them and the shaft aft
                for (int x = a + 3; x <= a + 12; x++) {
                    for (int z : new int[] {-6, -5, 4, 5}) {
                        in(x, bottom, z, "navy_turbine[axis=x]");
                        in(x, bottom + 1, z, "navy_turbine[axis=x]");
                    }
                }
                for (int x = a + 13; x <= a + 17; x++) {
                    for (int z = -6; z <= 5; z++) {
                        for (int y = bottom; y <= bottom + 3; y++) {
                            in(x, y, z, (y < bottom + 2 ? "ship_engine_lo" : "ship_engine_hi") + "[facing=" + (z < 0 ? "north" : "south") + "]");
                        }
                    }
                }
                for (int x = a; x < a + 3; x++) {
                    in(x, bottom, -1, "vessel_pipe[axis=x]");
                    in(x, bottom, 0, "vessel_pipe[axis=x]");
                }
                for (int z = -3; z <= 2; z++) {
                    in(a + 6, bottom, z, "bunk_tank");
                    in(a + 6, bottom + 1, z, "bunk_tank_top");
                }
                // the boiler fronts on the after bulkhead, cold, either side of the steam lines
                for (int z = -7; z <= 6; z++) {
                    if (z < -2 || z > 1) {
                        in(a, bottom, z, "fit_burner[facing=east]");
                        in(a, bottom + 1, z, "fit_burner[facing=east]");
                    }
                }
                // the throttle board on the forward bulkhead: the wheels under two tiers of gauges
                for (int z = -6; z <= 5; z++) {
                    in(b, bottom, z, "fit_throttle[facing=west]");
                    in(b, bottom + 1, z, "fit_gauges[facing=west]");
                    in(b, bottom + 2, z, z % 4 == 0 ? "fit_phone[facing=west]" : z % 4 == 2 ? "fit_jbox[facing=west]" : "fit_gauges[facing=west]");
                }
                controlRoom(b, walk, top);
            } else {
                for (int x = a + 2; x + 2 <= b; x += 5) {
                    for (int z : new int[] {-5, 4}) {
                        in(x, bottom, z, "bunk_gen_engine[facing=" + (z < 0 ? "south" : "north") + "]");
                        in(x + 1, bottom, z, "bunk_gen_alternator[facing=" + (z < 0 ? "south" : "north") + "]");
                    }
                }
                for (int x = a + 1; x < b; x += 2) {
                    in(x, bottom, 0, "bunk_switchboard[facing=north]");
                    in(x, bottom + 1, 0, "bunk_switchboard_top[facing=north]");
                }
            }
            // a ladder down from the gratings
            for (int k = 0; k < walk - bottom; k++) {
                in(a + 1 + k, walk - k, -9, "steel_stairs[facing=west]");
            }
            // along her sides: the fuel manifolds in yellow over their gate valves, the lagged risers, the low pipe runs
            for (int x = a + 2; x <= b - 2; x++) {
                int m = (x - a) % 6;
                for (int s : new int[] {-1, 1}) {
                    int e = edgeZ(x, bottom + 1, s);
                    if (e == Integer.MIN_VALUE) {
                        continue;
                    }
                    int z = e - 2 * s;
                    String f = s > 0 ? "north" : "south";
                    if (m == 0) {
                        over(x, bottom, z, "fit_valve_bronze[facing=" + f + "]");
                        over(x, bottom + 1, z, "fit_fuel_valves[facing=" + f + "]");
                        over(x, bottom + 2, z, "fit_fuel_valves[facing=" + f + "]");
                    } else if (m == 3) {
                        for (int y = bottom; y < walk; y++) {
                            over(x, y, z, "fit_riser[facing=" + f + "]");
                        }
                    } else {
                        over(x, bottom, z, "vessel_pipe[axis=x]");
                    }
                }
            }
            over(a, walk + 2, -10, lantern("east"));
            over(b, walk + 2, 9, lantern("west"));
            dressing(a, b, -8, 7, bottom - 1, 6);
        }
    }

    /**
     * The engine control room over the throttle board at b, on the emergency generator: glazed aft
     * over the turbines, a door each side onto the gratings; the boards and the telegraph on the
     * bulkhead, the console facing them, its chairs.
     */
    private void controlRoom(int b, int walk, int top) {
        int c0 = b - 3;
        for (int x = c0 - 1; x <= b; x++) {
            for (int z = -9; z <= 8; z++) {
                if (!inside(x, walk, z)) {
                    continue;
                }
                p.set(x, walk, z, "fit_deckplate");
                for (int y = walk + 1; y <= top; y++) {
                    boolean wall = x == c0 - 1 || z == -9 || z == 8;
                    p.set(x, y, z, !wall ? "air" : x == c0 - 1 && y == walk + 2 ? "navy_window[facing=west]" : "navy_wall");
                }
            }
        }
        Doors.abandoned(p, rng, b - 1, walk + 1, -9, "ship_door", "north", Doors.hinge(rng));
        Doors.abandoned(p, rng, b - 1, walk + 1, 8, "ship_door", "south", Doors.hinge(rng));
        for (int z = -8; z <= 7; z++) {
            in(b, walk + 1, z, z == -1 ? "ship_telegraph[facing=west]" : "fit_throttle[facing=west]");
            in(b, walk + 2, z, "fit_gauges[facing=west]");
            if (z > -7 && z < 6) {
                in(b - 2, walk + 1, z, console("fit_console", "west", true));
                if (Math.floorMod(z, 4) == 1) {
                    in(c0, walk + 1, z, "bunk_chair[facing=east]");
                }
            }
            if (Math.floorMod(z, 4) == 2) {
                in(b - 1, top, z, tube("x", true));
                in(c0, top, z, tube("x", true));
            }
        }
        in(c0, walk + 2, -8, lantern("east"));
        in(c0, walk + 2, 7, "fit_phone[facing=east]");
        in(b - 1, top, -1, red());
    }

    // ------------------------------------------------------------------ fore and aft at the hangar deck

    /** The fantail under the flight deck's end: open to the stern, the mooring gear, the door into the jet shop. */
    private void fantail() {
        int x1 = 16;
        for (int x = HX - 1; x <= x1; x++) {
            for (int z = Z0; z < Z0 + NZ; z++) {
                if (kind(x, HANGAR, z) == 0) {
                    continue;
                }
                boolean open = Math.abs(z + 0.5) < 12.5 && x <= HX + 1;
                for (int y = HANGAR + 1; y < GALLERY - 1; y++) {
                    if (inside(x, y, z) || open && kind(x, y, z) == 1) {
                        p.set(x, y, z, "air");
                    }
                }
                if (inside(x, HANGAR, z) || open) {
                    p.set(x, HANGAR, z, "navy_deck_steel");
                }
                if (x == x1 + 0 && inside(x, HANGAR + 1, z)) {
                    for (int y = HANGAR + 1; y < GALLERY; y++) {
                        p.set(x, y, z, "navy_bulkhead");
                    }
                }
            }
        }
        for (int z = -12; z <= 11; z++) {
            p.set(HX - 1, HANGAR + 1, z, z == -12 || z == 11 ? "vessel_rail_cross" : "vessel_rail[axis=z]");
        }
        for (int s : new int[] {-1, 1}) {
            int z = s * 8 - (s < 0 ? 1 : 0);
            p.set(HX + 2, HANGAR + 1, z, "vessel_bollard[axis=z]");
            p.set(HX + 7, HANGAR + 1, z, "vessel_winch[axis=z]");
            p.set(HX + 10, HANGAR + 1, s * 4, "vessel_rope_v");
            p.set(HX + 4, HANGAR + 1, s * 11 - (s < 0 ? 1 : 0), "vessel_fairlead[facing=west]");
        }
        for (int z = -8; z <= 8; z += 8) {
            p.set(HX + 6, GALLERY - 2, z, chance() < 0.3 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
        }
        Doors.pair(p, rng, x1, HANGAR + 1, -1, "ship_door", "east");
    }

    /** The aircraft maintenance shop between the fantail and the hangar: engines on test stands, benches, the hoist. */
    private void jetShop() {
        int x0 = 17, x1 = BAY0 - 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = Z0; z < Z0 + NZ; z++) {
                if (!inside(x, HANGAR + 1, z)) {
                    continue;
                }
                p.set(x, HANGAR, z, chance() < 0.08 ? "navy_hangar_oil" : "navy_hangar_floor");
                if (z == -6 || z == 5) {
                    p.set(x, GALLERY - 1, z, "steel_girder");
                }
            }
            for (int s : new int[] {-1, 1}) {
                int e = edgeZ(x, HANGAR + 1, s);
                if (e == Integer.MIN_VALUE || trunkNear(x) && s > 0) {
                    continue;
                }
                int z = e - s * 2;
                String f = s > 0 ? "north" : "south";
                if (x % 3 != 0) {
                    in(x, HANGAR + 1, z, (x % 7 == 1 ? "bunk_rack_crates" : "bunk_desk") + "[facing=" + f + "]");
                    in(x, HANGAR + 2, z, "bunk_pegboard[facing=" + f + "]");
                } else {
                    in(x, HANGAR + 1, z, "navy_toolbox[facing=" + f + "]");
                }
            }
        }
        // engines on their stands, one hung from the hoist
        for (int k = 0; k < 3; k++) {
            int x = x0 + 4 + k * 9;
            for (int dx = 0; dx < 4; dx++) {
                in(x + dx, HANGAR + 1, -2, "navy_engine[axis=x]");
            }
        }
        for (int dx = 0; dx < 4; dx++) {
            in(x0 + 8 + dx, HANGAR + 4, 5, "navy_engine[axis=x]");
        }
        for (int y = HANGAR + 5; y < GALLERY - 1; y++) {
            in(x0 + 9, y, 5, "vessel_chain_v");
        }
        for (int z = -8; z <= 8; z += 8) {
            for (int x = x0 + 4; x < x1; x += 10) {
                in(x, GALLERY - 1, z, chance() < 0.3 ? "navy_lamp[axis=x]" : "bunk_tube[axis=x]");
            }
        }
        Doors.pair(p, rng, BAY0 - 1, HANGAR + 1, -1, "ship_door", "east");
        dressing(x0, x1, -10, 10, HANGAR, 14);
    }

    /** The forecastle forward of the hangar: the anchor windlasses and their cables to the hawse pipes, bitts, the chain lockers. */
    private void forecastle() {
        int x0 = BAY1 + 2, x1 = 318;
        for (int x = x0; x <= x1; x++) {
            for (int z = Z0; z < Z0 + NZ; z++) {
                if (inside(x, HANGAR + 1, z)) {
                    p.set(x, HANGAR, z, chance() < 0.1 ? "vessel_tanktop" : "navy_deck_steel");
                }
            }
        }
        int wx = 290;
        for (int s : new int[] {-1, 1}) {
            int z = s * 6 - (s < 0 ? 1 : 0);
            in(wx, HANGAR + 1, z, "vessel_windlass[axis=x]");
            in(wx + 1, HANGAR + 1, z, "vessel_windlass[axis=x]");
            int hx = 306;
            int ez = edgeZ(hx, HANGAR + 2, s);
            for (int x = wx + 2; x <= hx; x++) {
                int cz = ez == Integer.MIN_VALUE ? z : z + (int) Math.round((ez - s * 2 - z) * (x - wx - 2) / (double) (hx - wx - 2));
                in(x, HANGAR + 1, cz, "vessel_chain[axis=x]");
            }
            for (int y = HANGAR; y > DECKS[0]; y--) {
                in(wx - 2, y, z, "vessel_chain_v");
            }
            in(wx - 2, HANGAR + 1, z, "vessel_chain_v");
            for (int k = 0; k < 3; k++) {
                in(wx - 10 - k * 6, HANGAR + 1, s * 10 - (s < 0 ? 1 : 0), "vessel_bollard[axis=x]");
                in(wx - 12 - k * 6, HANGAR + 1, s * 4 - (s < 0 ? 1 : 0), k == 1 ? "vessel_winch[axis=x]" : "vessel_rope_v");
            }
        }
        for (int x = x0 + 4; x < x1 - 4; x += 8) {
            for (int z = -8; z <= 8; z += 8) {
                in(x, GALLERY - 1, z, chance() < 0.25 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
            }
        }
        Doors.pair(p, rng, BAY1 + 1, HANGAR + 1, -1, "ship_door", "east");
        dressing(x0, x1, -12, 12, HANGAR, 12);
    }

    /**
     * A stair trunk from floor y0 up in the flights given, alternately forward on lane z and aft on
     * lane z + 1 from xs (landings at xs - 1 and xs + 4), walled in from xs - 2 to xs + 5 and z - 1
     * to z + 2, a door off the landing onto every floor listed.
     */
    private void trunk(int xs, int z, int y0, int[] flights, String floor, int[] doors) {
        int top = y0;
        for (int n : flights) {
            top += n;
        }
        for (int y = y0 + 1; y <= top + 2; y++) {
            for (int x = xs - 2; x <= xs + 5; x++) {
                for (int dz = -1; dz <= 2; dz++) {
                    boolean wall = x == xs - 2 || x == xs + 5 || dz == -1 || dz == 2;
                    if (wall || y > top) {
                        p.set(x, y, z + dz, wall ? "navy_wall" : "air");
                    }
                }
            }
        }
        int y = y0;
        for (int i = 0; i <= flights.length; i++) {
            boolean fwd = (i & 1) == 0;
            // the landing it leaves from (or the top one), and a door off it onto a deck
            int lx = i < flights.length ? (fwd ? xs - 1 : xs + 4) : ((flights.length & 1) == 1 ? xs + 4 : xs - 1);
            for (int dz = 0; dz <= 1; dz++) {
                p.set(lx, y, z + dz, floor);
                p.set(lx, y + 1, z + dz, "air");
                p.set(lx, y + 2, z + dz, "air");
            }
            for (int d : doors) {
                if (d == y) {
                    Doors.abandoned(p, rng, lx, y + 1, z - 1, "ship_door", "north", Doors.hinge(rng));
                    p.set(lx, y + 3, z - 1, "navy_wall");
                }
            }
            if (i == flights.length) {
                break;
            }
            int n = flights[i];
            int lane = fwd ? z : z + 1;
            for (int j = 0; j < n; j++) {
                int x = fwd ? xs + j : xs + 3 - j;
                p.set(x, y + 1 + j, lane, "steel_stairs[facing=" + (fwd ? "east" : "west") + "]");
                p.set(x, y + 2 + j, lane, "air");
                p.set(x, y + 3 + j, lane, "air");
            }
            // no deck three over the landing where it leaves, or a head on the first step up hits it
            p.set(fwd ? xs - 1 : xs + 4, y + 3, lane, "air");
            // the landing it arrives at fills the rest of the trunk's length
            for (int x = fwd ? xs + n : xs - 1; fwd ? x <= xs + 4 : x <= xs + 3 - n; x++) {
                for (int dz = 0; dz <= 1; dz++) {
                    p.set(x, y + n, z + dz, floor);
                }
            }
            if (i % 3 == 0) {
                p.set(fwd ? xs + 4 : xs - 1, y + n + 2, z + 1 - (fwd ? 0 : 1), "bunk_lamp_red[facing=" + (fwd ? "west" : "east") + "]");
            }
            y += n;
        }
    }

    // ------------------------------------------------------------------ the flight deck

    private void setDeck(int x, int z, String block) {
        if (plan(x, z)) {
            p.set(x, FLIGHT, z, block);
        }
    }

    private void deck() {
        for (int x = 0; x < LEN; x++) {
            for (int z = lo[x]; z <= hi[x]; z++) {
                if (!plan(x, z)) {
                    continue;
                }
                double c = ANG0 - ANGLE * x;
                boolean landing = x <= ANG_END && Math.abs(z + 0.5 - c) <= ANG_HALF;
                double r = chance();
                p.set(x, FLIGHT, z, landing && x > 30 && x < 110 && r < 0.35 ? "navy_flight_tyre"
                        : r < 0.02 + hit * 0.02 ? "navy_flight_oil" : r < 0.05 ? "navy_flight_bare" : "navy_flight");
            }
        }
        // the landing area: the dashed centreline, the line down its port side, the foul line to starboard
        for (int x = 2; x <= ANG_END; x++) {
            double c = ANG0 - ANGLE * x;
            if ((x / 5) % 2 == 0) {
                setDeck(x, (int) Math.floor(c), "navy_fl_white[axis=x]");
            }
            int pz = (int) Math.floor(c - ANG_HALF), sz = (int) Math.floor(c + ANG_HALF);
            if (pz > lo[x] + 1) {
                setDeck(x, pz, "navy_fl_white[axis=x]");
            }
            setDeck(x, sz, (x / 3) % 2 == 0 ? "navy_fl_red[axis=x]" : "navy_fl_white[axis=x]");
            if (x % 10 == 0) {
                if (plan(x, pz + 1)) {
                    p.set(x, FLIGHT + 1, pz + 1, "navy_edge_light");
                }
                if (plan(x, sz - 1)) {
                    p.set(x, FLIGHT + 1, sz - 1, "navy_edge_light");
                }
            }
        }
        // the arresting wires across it, their sheaves at each end
        for (int wx : WIRES) {
            double c = ANG0 - ANGLE * wx;
            int z0 = (int) Math.floor(c - ANG_HALF) + 2, z1 = (int) Math.floor(c + ANG_HALF) - 2;
            for (int z = z0; z <= z1; z++) {
                if (plan(wx, z)) {
                    p.set(wx, FLIGHT + 1, z, z == z0 || z == z1 ? "navy_wire_sheave[axis=z]" : "navy_wire[axis=z]");
                }
            }
        }
        // the catapults: track, shuttle, the lead-in line, the blast deflector behind, its soot
        for (int[] c : CATS) {
            for (int x = c[0]; x <= c[1]; x++) {
                setDeck(x, c[2], "navy_catapult[axis=x]");
            }
            p.set(c[0] + 1 + rng.nextInt(3), FLIGHT + 1, c[2], "navy_shuttle[facing=east]");
            for (int x = c[0] - 24; x < c[0] - 4; x++) {
                setDeck(x, c[2], "navy_fl_yellow[axis=x]");
            }
            boolean raised = chance() < 0.5;
            for (int z = c[2] - 3; z <= c[2] + 3; z++) {
                for (int x = c[0] - 4; x <= c[0] - 2; x++) {
                    setDeck(x, z, "navy_steel");
                }
                if (raised && plan(c[0] - 3, z)) {
                    p.set(c[0] - 3, FLIGHT + 1, z, "navy_jbd[facing=east]");
                    p.set(c[0] - 4, FLIGHT + 2, z, "navy_jbd[facing=east]");
                    p.set(c[0] - 4, FLIGHT + 1, z, "navy_steel");
                }
                for (int x = c[0] - 14; x < c[0] - 4; x++) {
                    if (chance() < (x >= c[0] - 9 ? 0.95 : 0.35) && Math.abs(z - c[2]) < 3 + (x >= c[0] - 9 ? 1 : 0)) {
                        setDeck(x, z, "navy_flight_soot");
                    }
                }
            }
            if (raised) {
                p.set(c[0] - 5, FLIGHT + 1, c[2] + 4, "navy_st_blast[facing=west]");
            }
        }
        // her number on the bow, read from astern
        for (int[] px : figure(2)) {
            setDeck(305 - px[1], -7 + px[0], "navy_fl_paint");
        }
        // the yellow lines: down the street to starboard, round the island
        for (int x = IX1 + 2; x < 236; x++) {
            setDeck(x, 12, "navy_fl_yellow[axis=x]");
        }
        for (int x = IX0 - 1; x <= IX1 + 1; x++) {
            setDeck(x, IZ0 - 1, "navy_fl_yellow[axis=x]");
        }
        for (int z = IZ0 - 1; z <= hi[IX0]; z++) {
            setDeck(IX0 - 1, z, "navy_fl_yellow[axis=z]");
            setDeck(IX1 + 1, z, "navy_fl_yellow[axis=z]");
        }
        // lights along the deck's edges
        for (int x = 2; x < LEN - 2; x += 9) {
            for (int s : new int[] {-1, 1}) {
                int z = s > 0 ? hi[x] : lo[x];
                if (plan(x, z) && kind(x, FLIGHT, z) != 0) {
                    p.set(x, FLIGHT + 1, z, "navy_edge_light");
                }
            }
        }
    }

    /** The cells of her number at scale blocks a pixel: {along from the left, down from the top}. */
    private List<int[]> figure(int scale) {
        List<int[]> out = new ArrayList<>();
        String digits = Integer.toString(number);
        for (int d = 0; d < digits.length(); d++) {
            String[] f = FIGURES[digits.charAt(d) - '0'];
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    if (f[row].charAt(col) != '#') {
                        continue;
                    }
                    for (int a = 0; a < scale; a++) {
                        for (int b = 0; b < scale; b++) {
                            out.add(new int[] {d * 4 * scale + col * scale + a, row * scale + b});
                        }
                    }
                }
            }
        }
        return out;
    }

    /** The elevators: up flush with the deck behind their hazard edges, or down at the hangar with the stanchions up round the hole. */
    private void lifts() {
        for (int i = 0; i < LIFTS.length; i++) {
            int[] l = LIFTS[i];
            int s = l[2];
            int y = down[i] ? HANGAR : FLIGHT;
            for (int x = l[0]; x <= l[1]; x++) {
                int za = s > 0 ? 20 : lo[x], zb = s > 0 ? 35 : -21;
                for (int z = za; z <= zb; z++) {
                    boolean rim = x == l[0] || x == l[1] || z == za || z == zb;
                    p.set(x, y, z, rim ? "navy_hazard[axis=" + (x == l[0] || x == l[1] ? "z" : "x") + "]" : chance() < 0.1 ? "navy_flight_tyre" : "navy_flight");
                    p.set(x, y - 1, z, "navy_steel");
                    if (down[i]) {
                        // the stanchions round the hole in the deck
                        for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                            if (plan(x + d[0], z + d[1]) && (x + z) % 2 == 0) {
                                p.set(x + d[0], FLIGHT + 1, z + d[1], "vessel_rail[axis=" + (d[0] != 0 ? "z" : "x") + "]");
                            }
                        }
                    }
                }
            }
            // the guides up the hull at each end
            for (int x : new int[] {l[0] - 1, l[1] + 1}) {
                int z = edgeZ(x, HANGAR + 4, s);
                if (z != Integer.MIN_VALUE) {
                    for (int yy = HANGAR; yy < GALLERY - 1; yy++) {
                        p.set(x, yy, z + s, "navy_steel");
                    }
                }
            }
        }
    }

    /**
     * The catwalks round the deck's edge, two wide a few feet down: steps up to the deck every so
     * often, the rail, life-raft canisters, guns on posts, whip antennas.
     */
    private void catwalks() {
        int y = GALLERY;
        java.util.List<int[]> rails = new java.util.ArrayList<>();
        for (int x = 0; x < LEN; x++) {
            for (int z = Z0; z < Z0 + NZ; z++) {
                if (plan(x, z) || lift(x, z) >= 0 || kind(x, y, z) != 0 || kind(x, y - 1, z) != 0) {
                    continue;
                }
                int d = 9;
                for (int dx = -3; dx <= 3; dx++) {
                    for (int dz = -3; dz <= 3; dz++) {
                        if (plan(x + dx, z + dz)) {
                            d = Math.min(d, Math.max(Math.abs(dx), Math.abs(dz)));
                        }
                    }
                }
                if (d <= 2) {
                    p.set(x, y, z, "bunk_walkway");
                    if (d == 1 && x % 6 == 0) {
                        p.set(x, y - 1, z, "steel_girder");
                    }
                } else if (d == 3) {
                    rails.add(new int[] {x, z});
                }
            }
        }
        // the rail along the outer edge, once all the gratings are down: it runs the way the
        // walk beside it runs (worked out mid-way, the gratings not yet laid on one side turned it)
        for (int[] c : rails) {
            int x = c[0], z = c[1];
            boolean alongX = near(x, z + 1) || near(x, z - 1), alongZ = near(x + 1, z) || near(x - 1, z);
            p.set(x, y + 1, z, alongX && alongZ ? "vessel_rail_cross" : alongX ? "vessel_rail[axis=x]" : "vessel_rail[axis=z]");
        }
        // steps up to the deck, rafts, guns and antennas along the sides
        for (int x = 10; x < LEN - 10; x++) {
            for (int s : new int[] {-1, 1}) {
                int e = s > 0 ? hi[x] : lo[x];
                if (!plan(x, e) || lift(x, e + s) >= 0) {
                    continue;
                }
                int inner = e + s, outer = e + 2 * s;
                String out = s > 0 ? "south" : "north";
                if (x % 48 == 20 && allWalk(x, x + 4, inner)) {
                    for (int k = 0; k < 4; k++) {
                        p.set(x + k, y + 1 + k, inner, "steel_stairs[facing=east]");
                    }
                } else if (x % 30 == 7 && allWalk(x, x + 2, outer)) {
                    for (int k = 0; k < 3; k++) {
                        p.set(x + k, y + 1, outer, "vessel_liferaft");
                    }
                } else if (x % 52 == 33 && allWalk(x, x, outer)) {
                    p.set(x, y + 1, outer, "navy_mg[facing=" + out + "]");
                } else if (x % 64 == 45 && allWalk(x, x, outer)) {
                    for (int k = 1; k <= 4; k++) {
                        p.set(x, y + k, outer + s, k == 1 ? "steel_girder" : "vessel_antenna");
                    }
                }
            }
        }
        // the landing lens on its platform to port, abreast the wires
        int lx = 70, lz = lo[lx] - 2;
        for (int x = lx - 1; x <= lx + 2; x++) {
            for (int z = lz - 2; z <= lz; z++) {
                p.set(x, y, z, "bunk_walkway");
            }
        }
        for (int z = lz - 1; z <= lz; z++) {
            p.set(lx, y + 1, z, "navy_steel");
            p.set(lx, y + 2, z, "navy_steel");
            p.set(lx, y + 3, z, "navy_lens[facing=west]");
            p.set(lx, y + 4, z, "navy_lens[facing=west]");
        }
    }

    /** True if a cell at the catwalk's height is catwalk (or deck). */
    private boolean near(int x, int z) {
        String b = p.get(x, GALLERY, z);
        return b != null && b.equals("bunk_walkway") || plan(x, z);
    }

    private boolean allWalk(int x0, int x1, int z) {
        for (int x = x0; x <= x1; x++) {
            String b = p.get(x, GALLERY, z);
            if (b == null || !b.equals("bunk_walkway")) {
                return false;
            }
        }
        return true;
    }

    /** The sponsons out past the catwalks with the guns and launchers on them, braced back to her side. */
    private void sponsons() {
        int y = GALLERY;
        for (int[] g : GUNS) {
            int x = g[0], s = g[1];
            int e = s > 0 ? hi[x] : lo[x];
            int z0 = e + s * 3, z1 = e + s * 8;
            String out = s > 0 ? "south" : "north";
            for (int dx = -3; dx <= 3; dx++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    boolean rim = Math.abs(dx) == 3 || z == z1;
                    p.set(x + dx, y, z, "bunk_walkway");
                    p.set(x + dx, y + 1, z, rim ? "vessel_rail[axis=" + (Math.abs(dx) == 3 && z != z1 ? "z" : "x") + "]" : "air");
                }
                // the plating under it braced back to her side
                for (int k = 1; k <= 5; k++) {
                    for (int z = e + s; s > 0 ? z <= z1 - k : z >= z1 + k; z += s) {
                        if (kind(x + dx, y - k, z) == 0) {
                            p.set(x + dx, y - k, z, Math.abs(dx) == 3 ? "navy_hull_seam" : "navy_hull");
                        }
                    }
                }
            }
            int wz = e + s * 6;
            switch (g[2]) {
                case 0 -> {
                    p.set(x, y + 1, wz, "navy_ciws_base");
                    p.set(x, y + 2, wz, "navy_ciws_body[facing=" + out + "]");
                    p.set(x, y + 3, wz, "navy_ciws_dome[facing=" + out + "]");
                    p.set(x, y + 2, wz + s, "navy_ciws_gun[facing=" + out + "]");
                }
                case 1 -> {
                    p.set(x, y + 1, wz, "navy_ciws_base");
                    p.set(x, y + 2, wz, "navy_ram[facing=" + (x > LEN / 2 ? "east" : "west") + "]");
                }
                default -> {
                    for (int dx = 0; dx <= 1; dx++) {
                        p.set(x + dx, y + 1, wz, "navy_ssm[facing=" + (x > LEN / 2 ? "east" : "west") + "]");
                        p.set(x + dx, y + 2, wz, "navy_ssm[facing=" + (x > LEN / 2 ? "east" : "west") + "]");
                    }
                    p.set(x - 2, y + 1, wz, "navy_ciws_base");
                    p.set(x - 2, y + 2, wz, "navy_dish[facing=" + out + "]");
                }
            }
            p.set(x + 2, y + 1, z1 - s, "navy_chaff[facing=" + out + "]");
        }
    }

    // ------------------------------------------------------------------ the island

    private void island() {
        // each level: floor y, x from and to, z from and to
        int[][] levels = {
            {FLIGHT, IX0, IX1, IZ0, IZ1},
            {18, IX0, IX1, IZ0, IZ1},
            {21, IX0, IX1, IZ0, IZ1},
            {24, IX0 + 1, IX1, IZ0, IZ1},
            {27, IX0 + 2, IX1, IZ0, IZ1},
            {30, IX0 + 4, IX1 + 1, IZ0 - 1, IZ1 + 1},
            {33, IX0, IX1 - 5, IZ0 - 2, IZ1},
            {36, IX0 + 6, IX1 - 5, IZ0 + 1, IZ1 - 1},
        };
        double broken = 0.1 + hit * 0.35;
        for (int i = 0; i < levels.length; i++) {
            int[] L = levels[i];
            int fy = L[0];
            for (int x = L[1]; x <= L[2]; x++) {
                for (int z = L[3]; z <= L[4]; z++) {
                    boolean wall = x == L[1] || x == L[2] || z == L[3] || z == L[4];
                    if (i > 0) {
                        p.set(x, fy, z, wall ? "navy_hull_seam" : i >= 4 && i <= 6 ? "navy_tile_grey" : "floor_lino");
                    }
                    for (int y = fy + 1; y <= fy + 2; y++) {
                        p.set(x, y, z, wall ? islandWall(i, x, y, z, L, broken) : "air");
                    }
                }
            }
            // the roof of this level where the next doesn't cover it, railed round
            int[] up = i + 1 < levels.length ? levels[i + 1] : null;
            int ry = fy + 3;
            for (int x = L[1]; x <= L[2]; x++) {
                for (int z = L[3]; z <= L[4]; z++) {
                    boolean covered = up != null && x >= up[1] && x <= up[2] && z >= up[3] && z <= up[4];
                    if (covered) {
                        continue;
                    }
                    p.set(x, ry, z, "navy_steel");
                    boolean edge = x == L[1] || x == L[2] || z == L[3] || z == L[4];
                    if (edge) {
                        boolean alongX = z == L[3] || z == L[4];
                        boolean corner = (x == L[1] || x == L[2]) && alongX;
                        p.set(x, ry + 1, z, corner ? "vessel_rail_cross" : alongX ? "vessel_rail[axis=x]" : "vessel_rail[axis=z]");
                    }
                }
            }
        }
        islandRooms(levels);
        // her number on both faces, five high, the bulbs that light it at night along every stroke
        for (int[] px : figure(1)) {
            p.set(152 - px[0], 23 - px[1], IZ0, "navy_hull_white");
            p.set(146 + px[0], 23 - px[1], IZ1, "navy_hull_white");
            p.setIfAir(152 - px[0], 23 - px[1], IZ0 - 1, bulbs("north"));
            p.setIfAir(146 + px[0], 23 - px[1], IZ1 + 1, bulbs("south"));
        }
        islandOutside(levels);
        // the arrays round the top, the masts, radars, domes, lights
        int[] t = levels[7];
        for (int y = t[0] + 1; y <= t[0] + 2; y++) {
            for (int x = t[1] + 2; x <= t[2] - 2; x++) {
                p.set(x, y, t[3] - 1, "navy_array[facing=north]");
                p.set(x, y, t[4] + 1, "navy_array[facing=south]");
            }
            for (int z = t[3] + 1; z <= t[4] - 1; z++) {
                p.set(t[2] + 1, y, z, "navy_array[facing=east]");
                p.set(t[1] - 1, y, z, "navy_array[facing=west]");
            }
        }
        int top = t[0] + 3, mx = 141, mz = 23;
        for (int y = top + 1; y <= top + 11; y++) {
            for (int dx = 0; dx <= 1; dx++) {
                for (int dz = 0; dz <= 1; dz++) {
                    p.set(mx + dx, y, mz + dz, "navy_lattice");
                }
            }
        }
        for (int x = mx - 1; x <= mx + 2; x++) {
            for (int z = mz - 1; z <= mz + 2; z++) {
                p.set(x, top + 12, z, "navy_steel");
                if ((x == mx - 1 || x == mx + 2) && (z == mz - 1 || z == mz + 2)) {
                    p.set(x, top + 6, z, "navy_steel");
                }
            }
        }
        p.set(mx, top + 13, mz, "navy_radar[axis=x]");
        p.set(mx + 2, top + 7, mz - 1, "navy_radar[axis=z]");
        for (int y = top + 13; y <= top + 20; y++) {
            p.set(mx + 1, y, mz + 1, "navy_lattice");
        }
        for (int k : new int[] {16, 19}) {
            int span = k == 16 ? 5 : 3;
            for (int z = mz + 1 - span; z <= mz + 1 + span; z++) {
                if (z != mz + 1) {
                    p.set(mx + 1, top + k, z, "vessel_yard[axis=z]");
                }
            }
            p.set(mx + 1, top + k + 1, mz + 1 - span, "vessel_antenna");
            p.set(mx + 1, top + k + 1, mz + 1 + span, "vessel_antenna");
        }
        p.set(mx + 1, top + 21, mz + 1, "vessel_light_white");
        p.set(mx + 1, top + 15, mz, "aviation_light");
        // the aft mast on the flight control roof with its radar, domes on the corners, the fire-control dishes
        int ax = IX0 + 2;
        for (int y = levels[6][0] + 4; y <= top + 6; y++) {
            p.set(ax, y, 24, "navy_lattice");
        }
        p.set(ax, top + 7, 24, "vessel_radar");
        p.set(ax, top + 3, 23, "vessel_yard[axis=z]");
        p.set(ax, top + 3, 25, "vessel_yard[axis=z]");
        p.set(t[1], top + 1, t[3], "vessel_dome");
        p.set(t[2], top + 1, t[4], "vessel_dome");
        p.set(t[1], top + 1, t[4], "vessel_antenna");
        p.set(t[2], top + 1, t[3], "vessel_antenna");
        int[] nav = levels[5];
        p.set(nav[2] - 1, nav[0] + 4, nav[3], "navy_dish[facing=north]");
        p.set(nav[2] - 1, nav[0] + 4, nav[4], "navy_dish[facing=south]");
        p.set(nav[2] - 4, nav[0] + 4, nav[3], "vessel_dome");
        p.set(nav[2] - 4, nav[0] + 4, nav[4], "vessel_dome");
        // the ladders up the aft face between the roofs, the floodlights over the deck
        for (int y = FLIGHT + 1; y <= levels[6][0] + 3; y++) {
            p.set(IX0 - 1, y, IZ1 - 1, "bunk_ladder[facing=west]");
        }
        for (int i = 1; i <= 6; i++) {
            int[] L = levels[i];
            p.set(L[1] + 3, L[0] + 2, L[3] - 1, "bunk_lamp" + (chance() < 0.2 ? "_lit" : "") + "[facing=north]");
        }
        // the stencils on her foot, the bump line's done on the deck
        p.set(IX0 + 3, FLIGHT + 2, IZ0 - 1, "navy_st_props[facing=north]");
        p.set(IX1 - 4, FLIGHT + 2, IZ0 - 1, "navy_st_flight[facing=north]");
    }

    /** A cell of the island's outer wall: glazed where the bridge, flight control and deck control look out, ports elsewhere. */
    private String islandWall(int i, int x, int y, int z, int[] L, double broken) {
        boolean top = y == L[0] + 2;
        boolean face = z == L[3];
        String w = chance() < broken ? "navy_window_broken" : "navy_window";
        if (i == 0 && top && (face && x >= 146 && x < L[2] || x == L[2] && z > L[3] && z < L[4])) {
            return w + "[facing=" + (x == L[2] ? "east" : "north") + "]";
        }
        if (i == 4 && top && (x == L[2] && z > L[3] && z < L[4] || (face || z == L[4]) && x >= L[2] - 6 && x < L[2])) {
            return w + "[facing=" + (x == L[2] ? "east" : face ? "north" : "south") + "]";
        }
        if (i == 5 && (x == L[2] && z > L[3] && z < L[4] || top && (face || z == L[4]) && x >= L[2] - 10 && x < L[2])) {
            return w + "[facing=" + (x == L[2] ? "east" : face ? "north" : "south") + "]";
        }
        if (i == 6 && (face && x < L[2] && x > L[1] || x == L[1] && z > L[3] && z < L[4])) {
            return w + "[facing=" + (face ? "north" : "west") + "]";
        }
        if ((i == 1 || i == 2 || i == 3) && top && x % 8 == 5 && (x < 145 || x > 153)) {
            return "ship_porthole[facing=" + (face ? "north" : z == L[4] ? "south" : x == L[2] ? "east" : "west") + "]";
        }
        return y == L[0] + 1 && x % 12 == 0 ? "navy_hull_butt" : "navy_hull";
    }

    /** The number's bulbs on the dead circuit: most out, the odd one still glowing or stuttering. */
    private String bulbs(String f) {
        double r = chance();
        return (r < 0.08 ? "fit_numlight_on" : r < 0.2 ? "fit_numlight_flicker" : "fit_numlight") + "[facing=" + f + "]";
    }

    /**
     * What hangs on the island's outside: floodlights over the deck under each level's roof, the
     * cable and pipe runs climbing its faces, visors over the bridge and flight control windows,
     * the signal searchlights on the bridge roof.
     */
    private void islandOutside(int[][] levels) {
        for (int i = 1; i <= 4; i++) {
            int[] L = levels[i];
            // (the third level's face is left clear over its catwalk)
            for (int x = L[1] + 6; x < L[2] - 1 && i != 3; x += 7) {
                p.setIfAir(x, L[0] + 2, L[3] - 1, (chance() < 0.1 ? "fit_flood_on" : "fit_flood") + "[facing=north]");
            }
            p.setIfAir(L[2] + 1, L[0] + 2, (L[3] + L[4]) / 2, "fit_speaker[facing=east]");
        }
        for (int y = FLIGHT + 1; y <= levels[4][0] + 2; y++) {
            for (int x : new int[] {131, 140}) {
                p.setIfAir(x, y, IZ0 - 1, "fit_pipe_drop[facing=north]");
                p.setIfAir(x + 2, y, IZ1 + 1, "fit_pipe_drop[facing=south]");
            }
            p.setIfAir(IX1 + 1, y, IZ0 + 2, "fit_pipe_drop[facing=east]");
            if (y % 3 == 1) {
                p.setIfAir(IX1 + 1, y, IZ1 - 2, "fit_jbox[facing=east]");
            }
        }
        // a catwalk along both faces of the third level, railed outboard, brackets under it, a door out
        int[] C = levels[3];
        for (int s : new int[] {-1, 1}) {
            int wz = s < 0 ? IZ0 - 1 : IZ1 + 1;
            for (int x = C[1]; x <= C[2]; x++) {
                p.set(x, C[0], wz, "bunk_walkway");
                p.setIfAir(x, C[0] + 1, wz + s, "vessel_rail[axis=x]");
                if (x % 6 == 1) {
                    p.setIfAir(x, C[0] - 1, wz, "steel_girder");
                }
            }
            p.setIfAir(C[1] - 1, C[0] + 1, wz, "vessel_rail[axis=z]");
            p.setIfAir(C[2] + 1, C[0] + 1, wz, "vessel_rail[axis=z]");
            Doors.abandoned(p, rng, 150, C[0] + 1, s < 0 ? IZ0 : IZ1, "ship_door", s < 0 ? "north" : "south", Doors.hinge(rng));
        }
        int[] B = levels[5], F = levels[6];
        for (int z = B[3] + 1; z < B[4]; z++) {
            p.setIfAir(B[2] + 1, B[0] + 2, z, "fit_visor[facing=east]");
        }
        for (int x = B[2] - 10; x < B[2]; x++) {
            p.setIfAir(x, B[0] + 2, B[3] - 1, "fit_visor[facing=north]");
            p.setIfAir(x, B[0] + 2, B[4] + 1, "fit_visor[facing=south]");
        }
        for (int x = F[1] + 1; x < F[2]; x++) {
            p.setIfAir(x, F[0] + 2, F[3] - 1, "fit_visor[facing=north]");
        }
        p.setIfAir(B[2] - 3, B[0] + 4, B[3] + 1, (chance() < 0.15 ? "fit_searchlight_on" : "fit_searchlight") + "[facing=north]");
        p.setIfAir(B[2] - 3, B[0] + 4, B[4] - 1, (chance() < 0.15 ? "fit_searchlight_on" : "fit_searchlight") + "[facing=south]");
    }

    private void islandRooms(int[][] levels) {
        // the foot: flight deck control forward behind its windows, the ouija board; doors out to the deck
        int fy = FLIGHT + 1;
        for (int z = IZ0 + 1; z < IZ1; z++) {
            for (int y = fy; y <= fy + 1; y++) {
                p.set(145, y, z, "navy_wall");
            }
        }
        Doors.abandoned(p, rng, 145, fy, 23, "ship_door", "east", Doors.hinge(rng));
        p.set(149, fy, 22, "bunk_map_table");
        p.set(150, fy, 22, "bunk_map_table");
        p.set(149, fy, 23, "bunk_map_table");
        p.set(150, fy, 23, "bunk_map_table");
        p.set(148, fy, 22, "bunk_chair[facing=east]");
        p.set(151, fy, 23, "bunk_chair_down[facing=west]");
        for (int x = 147; x <= 153; x++) {
            p.set(x, fy, IZ1 - 1, console("fit_console", "north", false));
            p.set(x, fy + 1, IZ1 - 1, x % 2 == 0 ? "navy_whiteboard[facing=north]" : "navy_screen[facing=north]");
        }
        p.set(146, fy + 1, 21, "navy_st_control[facing=east]");
        for (int x : new int[] {133, 143}) {
            Doors.abandoned(p, rng, x, fy, IZ0, "ship_door", "north", Doors.hinge(rng));
        }
        Doors.abandoned(p, rng, 133, fy, IZ1, "ship_door", "south", Doors.hinge(rng));
        for (int x = IX0 + 1; x < 136; x++) {
            p.set(x, fy, IZ1 - 1, "bunk_locker[facing=north]");
        }
        // the offices and the radio room
        int[] L1 = levels[1], L2 = levels[2], L3 = levels[3];
        for (int x = L1[1] + 9; x < L1[2]; x += 3) {
            p.set(x, L1[0] + 1, IZ0 + 1, "bunk_desk[facing=south]");
            p.set(x, L1[0] + 1, IZ0 + 2, "bunk_chair[facing=north]");
            p.set(x, L1[0] + 2, IZ1 - 1, "navy_whiteboard[facing=north]");
        }
        for (int x = L2[1] + 9; x < L2[2]; x++) {
            p.set(x, L2[0] + 1, IZ1 - 1, x % 3 == 0 ? "bunk_server[facing=north]" : "bunk_radio_desk[facing=north]");
            if (x % 3 == 1) {
                p.set(x, L2[0] + 1, IZ1 - 2, "bunk_chair[facing=south]");
            }
        }
        for (int z = IZ0 + 1; z < IZ1; z++) {
            p.set(L3[2] - 1, L3[0] + 1, z, console("fit_console", "west", false));
        }
        p.set(L3[2] - 3, L3[0] + 1, 24, "bunk_map_table");
        // the navigation bridge: consoles under the forward windows, the helm, the captain's chair, the chart table
        int[] G = levels[4];
        for (int z = G[3] + 1; z < G[4]; z++) {
            p.set(G[2] - 1, G[0] + 1, z, console("fit_console", "west", false));
            if (z % 3 == 0) {
                p.set(G[2] - 2, G[0] + 1, z, "bunk_chair[facing=east]");
            }
        }
        p.set(G[2] - 5, G[0] + 1, 24, "fit_chart_table[facing=west]");
        p.setIfAir(G[2] - 3, G[0] + 2, 22, tube("x", false));
        p.setIfAir(G[2] - 3, G[0] + 2, 26, red());
        int[] B = levels[5];
        int by = B[0] + 1;
        for (int z = B[3] + 1; z < B[4]; z++) {
            p.set(B[2] - 1, by, z, z == 23 || z == 24 ? "ship_telegraph[facing=west]" : console("fit_console", "west", false));
            if (Math.floorMod(z, 3) == 1) {
                p.set(B[2] - 1, by + 1, z, console("fit_overhead", "west", false));
            }
        }
        // the captain's chair to starboard, the navigator's to port; the helm and lee helm behind the telegraphs
        p.set(B[2] - 3, by, B[3] + 2, "fit_chair_high[facing=east]");
        p.set(B[2] - 3, by, B[4] - 2, "fit_chair_high[facing=east]");
        p.set(B[2] - 4, by, 23, console("fit_console", "east", false));
        p.set(B[2] - 4, by, 24, console("fit_console", "east", false));
        p.set(B[2] - 5, by, 23, "bunk_chair[facing=east]");
        p.set(B[2] - 6, by, 25, "fit_chart_table[facing=west]");
        p.set(B[2] - 6, by, 26, "fit_chart_table[facing=west]");
        p.set(B[2] - 8, by, B[3] + 1, "fit_pelorus");
        p.set(B[2] - 8, by, B[4] - 1, "fit_pelorus");
        for (int x : new int[] {B[2] - 4, B[2] - 9}) {
            p.setIfAir(x, by + 1, 22, tube("x", false));
            p.setIfAir(x, by + 1, 26, tube("x", false));
            p.setIfAir(x + 2, by + 1, 24, red());
        }
        p.set(B[2] - 9, by + 1, B[4] - 1, "navy_screen[facing=north]");
        p.set(B[2] - 10, by + 1, B[4] - 1, "navy_screen[facing=north]");
        p.set(B[2] - 11, by + 1, 22, "navy_st_bridge[facing=east]");
        // primary flight control: seats facing the deck behind its windows
        int[] F = levels[6];
        int fly = F[0] + 1;
        for (int x = F[1] + 2; x < F[2] - 1; x++) {
            p.set(x, fly, F[3] + 1, console("fit_console", "south", false));
            if (x % 3 == 0) {
                p.set(x, fly + 1, F[3] + 1, console("fit_overhead", "south", false));
            }
        }
        // the air boss and the mini boss in their high chairs, looking down the deck
        p.set(F[1] + 5, fly, F[3] + 2, "fit_chair_high[facing=north]");
        p.set(F[1] + 9, fly, F[3] + 2, "fit_chair_high[facing=north]");
        for (int x = F[1] + 3; x < F[2] - 1; x += 4) {
            p.setIfAir(x, fly + 1, (F[3] + F[4]) / 2 + 1, x % 8 < 4 ? red() : tube("x", false));
        }
        p.set(F[1] + 1, fly, 24, "ship_console[facing=west]");
        p.set(F[1] + 12, fly + 1, F[4] - 1, "navy_whiteboard[facing=north]");
        // the top: the arrays' cabinets
        int[] T = levels[7];
        for (int x = T[1] + 1; x < T[2]; x += 2) {
            p.set(x, T[0] + 1, T[3] + 1, "bunk_server[facing=south]");
        }
        dressing(IX0 + 1, IX1 - 1, IZ0 + 1, IZ1 - 1, FLIGHT, 6);
        dressing(B[1] + 1, B[2] - 1, B[3] + 1, B[4] - 1, B[0], 4);
    }

    // ------------------------------------------------------------------ on deck

    /**
     * The flight deck as it was left: jets parked on the bow, down the street and in the corral
     * aft, one on an elevator; tractors, the bomb farm by the island, the crash crane, fuel reels
     * and extinguishers at the edges.
     */
    private void gear() {
        int y = FLIGHT + 1;
        // every one nose to the bow, toward the catapults, with a clear run forward to taxi out
        double[][] spots = {
            {250, 19, 270}, {271, 19, 270},                     // the bow, starboard of the catapults
            {250, -22, 270}, {271, -22, 270},                   // and to port
            {176, 14, 270}, {196, 14, 270},                     // the street
            {40, 12, 270}, {62, 12, 270},                       // the corral aft of the island, clear of the bomb farm
            {92, -37, 270}, {114, -37, 270},                    // the junkyard to port, lined up on the waist catapult
            {212, 29, 270},                                     // on the forward elevator
        };
        for (double[] s : spots) {
            if (chance() < 0.85) {
                park(s[0], y, s[1], s[2]);
            }
        }
        helipad(y);
        // tractors by the jets and in the street
        for (int i = 0; i < 9; i++) {
            int x = 40 + rng.nextInt(260), z = -20 + rng.nextInt(44);
            if (plan(x, z) && plan(x + 1, z + 1) && plan(x - 1, z - 1) && !inTaxiLane(x, y, z)) {
                tractor(x, y, z);
            }
        }
        // the bomb farm aft of the island, the crash crane between it and the elevator
        for (int x = 108; x <= 116; x += 2) {
            for (int z = 12; z <= 17; z += 2) {
                if (chance() < 0.75) {
                    p.set(x, y, z, z == 14 ? "navy_missile[axis=x]" : "navy_bomb[axis=x]");
                }
            }
        }
        crane(117, 21);
        // fuel reels and extinguishers round the edges
        for (int x = 30; x < LEN - 30; x += 37) {
            for (int s : new int[] {-1, 1}) {
                int z = (s > 0 ? hi[x] : lo[x]) - s;
                if (plan(x, z) && p.get(x, y, z) == null) {
                    p.set(x, y, z, "navy_hose_reel[facing=" + (s > 0 ? "north" : "south") + "]");
                    if (chance() < 0.5) {
                        p.set(x + 2, y, z, "navy_extinguisher[facing=" + (s > 0 ? "north" : "south") + "]");
                    }
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            int x = 20 + rng.nextInt(LEN - 40), z = -30 + rng.nextInt(56);
            if (plan(x, z) && !onPad(x, z)) {
                p.setIfAir(x, y, z, any("navy_toolbox[facing=north]", "navy_chocks[facing=east]", "navy_extinguisher[facing=west]",
                        "navy_chains", "blood_splat", "debris_chunk[facing=north]", "bunk_casings"));
            }
        }
    }

    /**
     * The helicopter spot on the starboard quarter, clear of the landing area: a yellow ring, a
     * white H read from astern, and the gunship tied down on it with its nose to the bow.
     */
    private void helipad(int y) {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r >= 5.6 && r < 6.7) {
                    setDeck(PAD_X + dx, PAD_Z + dz, "navy_fl_paint_yellow");
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz : new int[] {-3, -2, 2, 3}) {
                setDeck(PAD_X + dx, PAD_Z + dz, "navy_fl_paint");
            }
        }
        for (int dz = -1; dz <= 1; dz++) {
            setDeck(PAD_X, PAD_Z + dz, "navy_fl_paint");
        }
        tieDown(PAD_X - 1.84, y, PAD_Z, 270, "heli_hornbill");  // its rotor hub over the H
    }

    private static boolean onPad(int x, int z) {
        return (x - PAD_X) * (x - PAD_X) + (z - PAD_Z) * (z - PAD_Z) <= 64;
    }

    /** The yellow crash crane parked at (x, z): its cab, wheels and boom laid back along its length. */
    private void crane(int x, int z) {
        int y = FLIGHT + 1;
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                boolean wheel = (dx == 1 || dx == 5) && (dz == 0 || dz == 3);
                p.set(x + dx, y, z + dz, wheel ? "tire[axis=z]" : "steel_yellow");
            }
        }
        for (int dz = 0; dz < 2; dz++) {
            p.set(x + 5, y + 1, z + dz, "steel_yellow");
            p.set(x + 6, y + 1, z + dz, chance() < 0.4 ? "navy_window_broken[facing=east]" : "navy_window[facing=east]");
        }
        for (int dx = -2; dx < 5; dx++) {
            p.set(x + dx, y + 1 + (dx < 2 ? 1 : 0), z + 2, "truss_yellow");
        }
        p.set(x - 2, y + 1, z + 2, "vessel_chain_v");
    }

    // ------------------------------------------------------------------ her marks

    private void paint() {
        // her name across the stern, under the fantail
        int n = name.length();
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            if (c == ' ') {
                continue;
            }
            int z = -n / 2 + i;
            for (int x = 0; x < 30; x++) {
                if (kind(x, 1, z) != 0) {
                    String l = "vessel_name_" + Character.toLowerCase(c);
                    p.set(x - 1, 1, z, l + "_top[facing=west]");
                    p.set(x - 1, 0, z, l + "_bot[facing=west]");
                    break;
                }
            }
        }
        for (int s : new int[] {-1, 1}) {
            for (int m = 14; m <= 20; m += 2) {
                int y = KEEL + m;
                onSide(316, y, s, "vessel_draft_" + m);
                onSide(HX + 3, y, s, "vessel_draft_" + m);
            }
            // the anchors in their hawse pipes, high on the bow
            int hx = 306;
            if (onSide(hx, HANGAR + 2, s, "vessel_hawse")) {
                onSide(hx, HANGAR + 1, s, "vessel_anchor_shank");
                onSide(hx, HANGAR, s, "vessel_anchor_crown");
            }
            // the scuppers, rust weeping down the plating under each; the hangar deck's drains lower down
            for (int x = 30; x < 300; x += 26) {
                if (onSide(x, HANGAR + 6, s, "vessel_scupper")) {
                    weep(x, HANGAR + 5, s, 3);
                }
            }
            for (int x = 17; x < 310; x += 13) {
                if (onSide(x, HANGAR + 1, s, "fit_scupper")) {
                    weep(x, HANGAR, s, 2);
                }
            }
            // vents in pairs high on her sides under the flight deck, and side doors lower down
            for (int x = 44; x < 290; x += 18) {
                hullFitting(x, GALLERY + 1, s, "fit_louvre");
                hullFitting(x + 1, GALLERY + 1, s, "fit_louvre");
            }
            for (int x = 61; x < 280; x += 37) {
                hullFitting(x, HANGAR + 3, s, "fit_hatch");
            }
            // her number on the bow where her side runs straight, ten metres high, read from outboard
            int x0 = 246, top = 11;
            for (int[] px : figure(2)) {
                int x = s > 0 ? x0 + px[0] : x0 + 15 - px[0], y = top - px[1];
                int z = edgeZ(x, y, s);
                if (z != Integer.MIN_VALUE && kind(x, y, z) == 1) {
                    p.set(x, y, z, "navy_hull_white");
                }
            }
        }
    }

    /** A fitting flat on her plating at (x, y) on side s, where nothing else is fitted. */
    private void hullFitting(int x, int y, int s, String block) {
        int z = edgeZ(x, y, s);
        if (z != Integer.MIN_VALUE && kind(x, y, z) == 1) {
            p.setIfAir(x, y, z + s, block + "[facing=" + (s > 0 ? "south" : "north") + "]");
        }
    }

    /** Rust run down n plates of her side s under (x, y). */
    private void weep(int x, int y, int s, int n) {
        for (int k = 0; k < n; k++) {
            int z = edgeZ(x, y - k, s);
            if (z != Integer.MIN_VALUE && kind(x, y - k, z) == 1 && y - k > -2) {
                p.set(x, y - k, z, "navy_hull_rust");
            }
        }
    }

    /** Under the flight deck where it overhangs her sides: deep girders across it every fourth frame, carrying it out. */
    private void underside() {
        int y = FLIGHT - 1;
        for (int x = 0; x < LEN; x += 4) {
            for (int z = Z0; z < Z0 + NZ; z++) {
                if (kind(x, FLIGHT, z) == 1 && kind(x, y, z) == 0 && lift(x, z) < 0) {
                    p.setIfAir(x, y, z, "fit_girder[axis=x]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ alongside

    /** The camels between her and the wharf: steel floats with fenders that keep her overhang clear of it. */
    private void camels() {
        for (int cx : new int[] {36, 112, 196, 276}) {
            for (int x = cx; x < cx + 14; x++) {
                for (int z = 21; z < quayZ; z++) {
                    p.set(x, Ships.SEA, z, "vessel_hull_black");
                    p.set(x, Ships.SEA + 1, z, z == 21 || z == quayZ - 1 ? "tire[axis=x]" : x % 4 == 0 ? "vessel_bollard[axis=z]" : "deck_steel");
                }
            }
        }
    }

    /** Her lines from fairleads at the hangar deck down to bollards on the wharf. */
    private void moor() {
        int[][] lines = {{20, 8}, {28, -20}, {120, 112}, {200, 210}, {296, 300}, {308, 330}};
        for (int[] l : lines) {
            int fx = l[0];
            int ez = edgeZ(fx, HANGAR + 2, 1);
            if (ez == Integer.MIN_VALUE) {
                continue;
            }
            p.set(fx, HANGAR + 2, ez + 1, "vessel_fairlead[facing=south]");
            int qx = l[1];
            p.set(qx, 1, quayZ, "vessel_bollard[axis=x]");
            rope(fx + 0.5, HANGAR + 2.5, ez + 1.5, qx + 0.5, 1.6, quayZ + 0.5);
        }
    }

    /** A rope from one point to another, sagging in the middle; it only passes through air. */
    private void rope(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sag = Math.min(3, dist * 0.06);
        int steps = (int) (dist * 4) + 1;
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.floor(ax + dx * t), z = (int) Math.floor(az + dz * t);
            int y = (int) Math.floor(ay + dy * t - sag * 4 * t * (1 - t));
            if (x == lx && y == ly && z == lz) {
                continue;
            }
            lx = x;
            ly = y;
            lz = z;
            if (p.get(x, y, z) != null) {
                continue;
            }
            double slope = Math.abs(dy - sag * 4 * (1 - 2 * t)) / Math.max(0.01, Math.hypot(dx, dz));
            p.set(x, y, z, slope > 1.2 ? "vessel_rope_v" : Math.abs(dx) >= Math.abs(dz) ? "vessel_rope[axis=x]" : "vessel_rope[axis=z]");
        }
    }

    /** The brows from the wharf to doors in her side into the hangar: the officers' aft by the quarterdeck, the crew's forward. */
    private void brows() {
        for (int bx : new int[] {150, 232}) {
            for (int x = bx; x <= bx + 1; x++) {
                for (int z = HZ1 + 1; z <= 19; z++) {
                    p.set(x, HANGAR, z, "navy_hangar_floor");
                    p.set(x, HANGAR + 1, z, "air");
                    p.set(x, HANGAR + 2, z, "air");
                }
                for (int z = 20; z < quayZ; z++) {
                    p.set(x, HANGAR, z, "bunk_walkway");
                    p.set(x, HANGAR - 1, z, z % 4 == 0 ? "steel_girder" : "air");
                }
                // two steps up off the wharf onto the brow (its walkway tops out a block over the first)
                p.set(x, 1, quayZ, "steel_stairs[facing=north]");
                p.set(x, HANGAR, quayZ - 1, "steel_stairs[facing=north]");
            }
            for (int z = 20; z < quayZ; z++) {
                p.set(bx - 1, HANGAR + 1, z, "vessel_rail[axis=z]");
                p.set(bx + 2, HANGAR + 1, z, "vessel_rail[axis=z]");
                for (int x = bx - 1; x <= bx + 2; x++) {
                    p.set(x, HANGAR + 4, z, "navy_steel");
                }
            }
        }
        // the quarterdeck inside the officers' brow
        p.set(152, HANGAR + 1, HZ1 - 2, "bunk_desk[facing=north]");
        p.set(149, HANGAR + 3, HZ1, "navy_st_quarterdeck[facing=north]");
    }
}
