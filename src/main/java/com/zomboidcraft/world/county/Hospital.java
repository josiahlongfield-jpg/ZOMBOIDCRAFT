package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.LiftBuild;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Stairwell;
import java.util.SplittableRandom;

/**
 * Mercy General Hospital, the island's general hospital, on a lot of two blocks by two south of
 * its subway stop: a brown brick podium of three floors (the lobby, the emergency department,
 * radiology, the lab and the cafeteria on the ground; the theatres and intensive care over them;
 * offices, records and the clinics on the third) and the white ward tower rising nine floors out
 * of it, MERCY GENERAL HOSPITAL along its top, the red cross lit at each end, the helipad on its
 * roof. Behind, the service wing on the dock: the kitchen, the laundry, the stores, the morgue,
 * the boiler house and its stack; the staff lot beside it.
 *
 * <p>It was where the county sent its bitten. The army came, set up its triage tents in the staff
 * lot, sandbagged the drive, and lost it: the wards taped off one floor after another, the last
 * of them in polythene, DEAD INSIDE sprayed on the doors they chained, the body bags left on
 * the gurneys down the corridors.
 *
 * <p>Drawn in the lot's frame: x along the front (west to east), z from the front (north) back.
 * Every storey is {@link #S} tall: floor blocks at 0, 5 and 10 in the podium, its roof at 15
 * (the tower's first ward floor), the tower's floors every 5 up to 55, its roof at 60.
 */
final class Hospital {
    static final int S = 5;
    /** The podium's walls. */
    static final int PX0 = 8, PX1 = 124, PZ0 = 16, PZ1 = 88, PROOF = 15;
    /** The tower's walls, its ward floors (from the podium's roof up) and its roof. */
    static final int TX0 = 26, TX1 = 104, TZ0 = 28, TZ1 = 59, WARDS = 9, TROOF = PROOF + WARDS * S;
    /**
     * Across the tower (and the podium under it): the north rooms 29 to 34, the north corridor,
     * the core's walls, the south corridor, the south rooms 53 to 58.
     */
    static final int NR0 = 29, CN0 = 36, CN1 = 38, CORE0 = 39, CORE1 = 48, CS0 = 49, CS1 = 51, SR1 = 58;
    /** The core's west and east walls (the cross corridors round its ends outside them). */
    static final int CX0 = 30, CX1 = 100;
    /** The fire stairs' wells (x0, inner three wide) at either end of the core. */
    static final int SW = 32, SE = 96;
    /** The lifts: three cars, three wide and four deep, their doors in the core's north wall. */
    static final int[] LIFTS = {57, 61, 65};
    static final int LIFT_D = 4, LIFT_TOP = TROOF + 2;
    /** The emergency department east of x = 100; the service wing behind the podium. */
    static final int EX0 = 101, WX0 = 30, WX1 = 124, WZ1 = 116;

    final Placer p;
    final SplittableRandom rng;
    final Bldg b;
    final int w, d;
    /** The generator in the boiler house still runs: a few lights, the monitors, the red cross. */
    final boolean power;

    Hospital(Placer p, SplittableRandom rng, int w, int d) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.b = new Bldg(p, rng);
        b.damage = 0.5;
        b.boarded = 0.15;
        this.power = rng.nextDouble() < 0.7;
    }

    void build() {
        grounds();
        podium();
        tower();
        serviceWing();
        new HospitalDepts(this).build();
        new HospitalWard(this).build();
        core();
        roof();
        signs();
        outbreak();
    }

    // ------------------------------------------------------------------ helpers the parts share

    void set(int x, int y, int z, String block) {
        p.set(x, y, z, block);
    }

    String get(int x, int y, int z) {
        return p.get(x, y, z);
    }

    boolean empty(int x, int y, int z) {
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    /** Whether the wall at row z is whole from x0 to x1 (no door or opening a cart against it would block). */
    boolean solid(int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            String s = p.get(x, y + 1, z);
            if (s == null || !s.startsWith("hs_wall") && !s.startsWith("hs_pad") && !s.startsWith("hs_tile")) {
                return false;
            }
        }
        return true;
    }

    double chance() {
        return rng.nextDouble();
    }

    String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    static final String[] DIRS = {"north", "east", "south", "west"};

    String dir() {
        return DIRS[rng.nextInt(4)];
    }

    /** The floor levels of every storey, ground up: the podium's three and the tower's nine. */
    static int[] levels() {
        int[] ys = new int[3 + WARDS];
        for (int i = 0; i < ys.length; i++) {
            ys[i] = i * S;
        }
        return ys;
    }

    /** In the stairs' wells or the lifts' shafts (and their walls): rooms keep out. */
    static boolean inCore(int x, int z) {
        boolean stairs = z >= CORE0 && z <= CORE1 && (x >= SW - 1 && x <= SW + 3 || x >= SE - 1 && x <= SE + 3);
        boolean lifts = z >= CORE0 && z <= CORE0 + LIFT_D + 1 && x >= LIFTS[0] - 1 && x <= LIFTS[2] + 3;
        return stairs || lifts;
    }

    /** An inside wall along x from x0 to x1 at z on the storey at y: the dado at the floor, the paint over it. */
    void wallX(int x0, int x1, int z, int y) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            if (!inCore(x, z)) {
                column(x, z, y);
            }
        }
    }

    void wallZ(int x, int z0, int z1, int y) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            if (!inCore(x, z)) {
                column(x, z, y);
            }
        }
    }

    private void column(int x, int z, int y) {
        set(x, y + 1, z, chance() < 0.12 ? "hs_wall_lo_b" : "hs_wall_lo");
        for (int k = 2; k < S; k++) {
            set(x, y + k, z, chance() < 0.06 ? "hs_wall_b" : "hs_wall");
        }
    }

    /** Glazed tile instead of paint (the theatres, the morgue, the washrooms). */
    void tileX(int x0, int x1, int z, int y) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int k = 1; k < S; k++) {
                set(x, y + k, z, chance() < 0.15 ? "hs_tile_b" : "hs_tile");
            }
        }
    }

    void tileZ(int x, int z0, int z1, int y) {
        for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
            for (int k = 1; k < S; k++) {
                set(x, y + k, z, chance() < 0.15 ? "hs_tile_b" : "hs_tile");
            }
        }
    }

    /**
     * A door in a wall at (x, z) on the storey at y; facing the way a door in that wall faces
     * (north or south in a wall along x, east or west in one along z). Glass for the shop doors.
     */
    void door(int x, int y, int z, String type, String facing) {
        set(x, y + 3, z, chance() < 0.1 ? "hs_wall_b" : "hs_wall");
        set(x, y + 4, z, "hs_wall");
        Doors.abandoned(p, rng, x, y + 1, z, type, facing, Doors.hinge(rng));
    }

    /** A doorway with no door, two wide or one, the wall over it kept. */
    void opening(int x0, int x1, int z0, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, y + 1, z, "air");
                set(x, y + 2, z, "air");
                set(x, y + 3, z, "air");
            }
        }
    }

    /** The ceiling's troffers in the slab at ys over a room, every three blocks; a few lit. */
    void lamps(int x0, int z0, int x1, int z1, int ys, double on) {
        for (int x = x0 + 1; x <= x1 - 1; x += 3) {
            for (int z = z0 + 1; z <= z1 - 1; z += 3) {
                String s = get(x, ys, z);
                if (s != null && (s.startsWith("hs_slab") || s.startsWith("hs_roof"))) {
                    boolean roof = s.startsWith("hs_roof");
                    set(x, ys, z, (roof ? "hs_roof_lamp" : "hs_slab_lamp") + (power && chance() < on ? "_on" : ""));
                }
            }
        }
    }

    /** What's left on a floor: paper, blood, glass, a tray, a pill bottle's spill. */
    void mess(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!empty(x, y + 1, z) || chance() > density) {
                    continue;
                }
                double r = chance();
                set(x, y + 1, z, r < 0.4 ? "paper" : r < 0.62 ? "blood_splat" : r < 0.72 ? "glass_shards"
                        : r < 0.82 ? "hs_tray[facing=" + dir() + "]" : r < 0.9 ? "trash_bag[facing=" + dir() + "]" : "home_blood_pool");
            }
        }
    }

    /** A sign two wide on a wall: tile 0 at the viewer's left. */
    void sign(String key, int x, int y, int z, String facing) {
        int[] a = MallSigns.at(x, z, 2, 0, facing), c = MallSigns.at(x, z, 2, 1, facing);
        set(a[0], y, a[1], "hs_sgn_" + key + "_0[facing=" + facing + "]");
        set(c[0], y, c[1], "hs_sgn_" + key + "_1[facing=" + facing + "]");
    }

    // ------------------------------------------------------------------ the grounds

    private void grounds() {
        // the walks round the lot's edge and along the front, the drive and the drop-off before the doors
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean walk = x < 4 || z < 4 || x >= w - 4 || z >= d - 4 || z >= 13 && z < PZ0 || x >= 4 && x < PX0 && z < WZ1;
                boolean drive = z >= 5 && z <= 12 && x >= 4 && x < w - 4;
                String g;
                if (walk && !drive) {
                    g = chance() < 0.1 ? "sidewalk_cracked" : "sidewalk";
                } else {
                    double r = chance();
                    g = r < 0.12 ? "asphalt_crack" : r < 0.18 ? "asphalt_patch" : "asphalt";
                }
                set(x, 0, z, g);
            }
        }
        // the drive's arrows and the kerb's red paint before the emergency doors
        for (int x = 8; x < w - 8; x += 3) {
            if (chance() < 0.8) {
                set(x, 0, 8, "road_line_white[axis=x]");
            }
        }
        for (int x = EX0 + 4; x <= PX1 - 2; x++) {
            set(x, 0, 12, "road_line_yellow[axis=x]");
        }
        // the staff lot west of the service wing: stalls along both sides of an aisle, its poles
        for (int z = PZ1 + 3; z <= d - 8; z += 3) {
            for (int x = 4; x <= 10; x++) {
                if (x == 4 && chance() < 0.85) {
                    set(x, 0, z, "road_line_white[axis=x]");
                }
            }
            if (chance() < 0.1) {
                p.vehicle(7, 1, z + 1.5, 270 + (float) (chance() * 10 - 5), "sedan,suv,hatchback,minivan,wagon,pickup", rng.nextLong());
            }
            if (chance() < 0.08) {
                p.vehicle(24, 1, z + 1.5, 90 + (float) (chance() * 10 - 5), "sedan,suv,hatchback,minivan,wagon", rng.nextLong());
            }
        }
        for (int z = PZ1 + 10; z <= d - 10; z += 22) {
            set(16, 1, z, "st_lot_base");
            for (int y = 2; y <= 9; y++) {
                set(16, y, z, "st_lot_pole");
            }
            set(16, 10, z, (power && chance() < 0.3 ? "st_lot_head_on" : "st_lot_head") + "[facing=east]");
        }
        // benches and bins along the front walk, lamp posts on the kerb
        for (int x = 12; x < w - 12; x += 14) {
            if (x > 52 && x < 80) {
                continue;
            }
            set(x, 1, 14, "rw_bench[facing=north]");
            set(x + 2, 1, 14, "ext_trash_can");
        }
        for (int x = 10; x < w - 6; x += 20) {
            set(x, 1, 4, "lamp_post");
        }
        // a blue H on its post at each end of the drive
        for (int x : new int[] {5, w - 7}) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, 2, "tx_rod");
                set(x + 1, y, 2, "tx_rod");
            }
            for (int i = 0; i < 4; i++) {
                String on = power && chance() < 0.6 ? "_on" : "";
                set(x + 1 - i % 2, 5 - i / 2, 2, "hs_h_" + i + on + "[facing=north]");
            }
        }
    }

    // ------------------------------------------------------------------ the podium

    /** The outside wall's block at height y on a storey: the white band at each floor, brick, the ribbon of glass, the spandrel. */
    private String podiumWall(int y, String facing, boolean glassFront) {
        int k = y % S;
        if (y >= PROOF) {
            return y == PROOF ? "hs_precast" : y == PROOF + 1 ? (chance() < 0.25 ? "hs_brick_b" : "hs_brick") : "stone_trim";
        }
        if (k == 0) {
            return chance() < 0.3 ? "hs_precast_b" : "hs_precast";
        }
        if (glassFront && k <= 3 || k == 2 || k == 3) {
            return (chance() < b.damage * 0.3 ? "hs_ribbon_broken" : "hs_ribbon") + "[facing=" + facing + "]";
        }
        if (k == 4) {
            return "hs_spandrel";
        }
        return chance() < 0.2 ? "hs_brick_b" : "hs_brick";
    }

    private void podium() {
        for (int y = 0; y <= PROOF + 2; y++) {
            for (int x = PX0; x <= PX1; x++) {
                boolean lobby = y < S && x >= 44 && x <= 88;
                set(x, y, PZ0, podiumWall(y, "north", lobby));
                set(x, y, PZ1, podiumWall(y, "south", false));
            }
            for (int z = PZ0 + 1; z < PZ1; z++) {
                set(PX0, y, z, podiumWall(y, "west", false));
                set(PX1, y, z, podiumWall(y, "east", false));
            }
        }
        // the brick piers between the lobby's glass, every six
        for (int x = 44; x <= 88; x += 6) {
            for (int y = 1; y < S; y++) {
                set(x, y, PZ0, "hs_brick");
            }
        }
        // the floors: vinyl on the ground, slabs over (tile under), the roof's deck (the tower's own floor inside it)
        for (int x = PX0 + 1; x < PX1; x++) {
            for (int z = PZ0 + 1; z < PZ1; z++) {
                set(x, 0, z, chance() < 0.25 ? "hs_lino_b" : "hs_lino");
                set(x, S, z, chance() < 0.12 ? "hs_slab_b" : "hs_slab");
                set(x, 2 * S, z, chance() < 0.12 ? "hs_slab_b" : "hs_slab");
                boolean tower = x > TX0 && x < TX1 && z > TZ0 && z < TZ1;
                set(x, PROOF, z, tower ? "hs_slab" : "hs_roof");
            }
        }
        // the roof: the parapet's inside, the units on it
        for (int x = PX0 + 6; x < PX1 - 4; x += 9) {
            for (int z : new int[] {PZ0 + 5, PZ1 - 6}) {
                if (x > TX0 - 2 && x < TX1 + 2 && z > TZ0 - 2 && z < TZ1 + 2) {
                    continue;
                }
                set(x, PROOF + 1, z, "hvac");
                set(x + 1, PROOF + 1, z, "hvac");
            }
        }
        // the main doors: two pairs of glass under the canopy, the emergency's walk-in and ambulance doors
        for (int x : new int[] {63, 67}) {
            b.doors(x, 1, PZ0, "shop_door", "north");
            for (int y = 3; y < S; y++) {
                set(x, y, PZ0, "hs_spandrel");
                set(x + 1, y, PZ0, "hs_spandrel");
            }
        }
        for (int x : new int[] {104, 113, 115}) {
            b.doors(x, 1, PZ0, "shop_door", "north");
            for (int y = 3; y < S; y++) {
                set(x, y, PZ0, "hs_spandrel");
                set(x + 1, y, PZ0, "hs_spandrel");
            }
        }
        // the side doors to the walks and the staff lot
        b.door(PX0, 1, 37, "fire_door", "west");
        b.door(PX0, 1, 50, "fire_door", "west");
        b.door(PX1, 1, 37, "fire_door", "east");
        canopy(56, 76, 6, "front");
        canopy(EX0 + 4, PX1 - 2, 4, "er");
    }

    /**
     * A flat canopy over the drive from x0 to x1, out to z0, on posts; the main doors' carries the
     * hospital's name, the emergency department's EMERGENCY in red.
     */
    private void canopy(int x0, int x1, int z0, String name) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z < PZ0; z++) {
                set(x, S, z, x == x0 || x == x1 || z == z0 ? "hs_spandrel" : "hs_roof");
                set(x, S + 1, z, x == x0 || x == x1 || z == z0 ? "stone_trim" : "air");
            }
        }
        for (int x = x0 + 1; x < x1; x += 3) {
            for (int z = z0 + 1; z < PZ0 - 1; z += 3) {
                set(x, S, z, power && chance() < 0.4 ? "hs_roof_lamp_on" : "hs_roof_lamp");
            }
        }
        for (int x : new int[] {x0, x1}) {
            for (int y = 1; y < S; y++) {
                set(x, y, z0, "tx_column");
            }
        }
        int cols = name.equals("er") ? 8 : 10;
        int nx = (x0 + x1) / 2 - cols / 2 + 1;
        for (int i = 0; i < cols; i++) {
            if (chance() < 0.05) {
                continue;
            }
            String on = power && chance() > 0.12 ? "_on" : "";
            // facing north, tile 0 at the high x
            set(nx + cols - 1 - i, S, z0 - 1, "hs_name_" + name + "_" + i + on + "[facing=north]");
        }
    }

    // ------------------------------------------------------------------ the tower

    private String towerWall(int y) {
        int k = (y - PROOF) % S;
        if (y > TROOF) {
            return y == TROOF + 1 ? "hs_precast" : "stone_trim";
        }
        return k == 0 ? (chance() < 0.4 ? "hs_precast_b" : "hs_precast") : chance() < 0.15 ? "hs_precast_b" : "hs_precast";
    }

    /** Is (a) one of the tower's window columns along a front (x) or an end (z)? */
    static boolean towerWindow(int a, boolean front) {
        if (front) {
            int r = (a - TX0 - 1) % 6;
            return a > TX0 && a < TX1 && r >= 1 && r <= 3;
        }
        return a >= 30 && a <= 32 || a == 37 || a == 50 || a >= 55 && a <= 57;
    }

    private void tower() {
        for (int y = PROOF; y <= TROOF + 2; y++) {
            int k = (y - PROOF) % S;
            for (int x = TX0; x <= TX1; x++) {
                for (int z : new int[] {TZ0, TZ1}) {
                    String f = z == TZ0 ? "north" : "south";
                    if (y <= TROOF && (k == 2 || k == 3) && towerWindow(x, true)) {
                        set(x, y, z, "hs_window_" + (k == 2 ? "lo" : "hi") + (chance() < b.damage * 0.35 ? "_broken" : "") + "[facing=" + f + "]");
                    } else {
                        set(x, y, z, towerWall(y));
                    }
                }
            }
            for (int z = TZ0 + 1; z < TZ1; z++) {
                for (int x : new int[] {TX0, TX1}) {
                    String f = x == TX0 ? "west" : "east";
                    if (y <= TROOF && (k == 2 || k == 3) && towerWindow(z, false)) {
                        set(x, y, z, "hs_window_" + (k == 2 ? "lo" : "hi") + (chance() < b.damage * 0.35 ? "_broken" : "") + "[facing=" + f + "]");
                    } else {
                        set(x, y, z, towerWall(y));
                    }
                }
            }
        }
        // the floors, the roof
        for (int x = TX0 + 1; x < TX1; x++) {
            for (int z = TZ0 + 1; z < TZ1; z++) {
                for (int i = 1; i < WARDS; i++) {
                    set(x, PROOF + i * S, z, chance() < 0.12 ? "hs_slab_b" : "hs_slab");
                }
                set(x, TROOF, z, "hs_roof");
            }
        }
        // the fire doors out on to the podium's roof from the cross corridors' ends
        for (int x : new int[] {TX0, TX1}) {
            Doors.abandoned(p, rng, x, PROOF + 1, 43, Doors.STEEL, x == TX0 ? "east" : "west", Doors.hinge(rng));
            set(x, PROOF + 3, 43, "hs_precast");
            set(x, PROOF + 4, 43, "hs_precast");
        }
    }

    // ------------------------------------------------------------------ the service wing

    private void serviceWing() {
        for (int y = 0; y <= S + 1; y++) {
            for (int x = WX0; x <= WX1; x++) {
                set(x, y, WZ1, y == S + 1 ? "stone_trim" : y == S ? "hs_precast" : chance() < 0.25 ? "hs_brick_b" : "hs_brick");
            }
            for (int z = PZ1 + 1; z < WZ1; z++) {
                for (int x : new int[] {WX0, WX1}) {
                    set(x, y, z, y == S + 1 ? "stone_trim" : y == S ? "hs_precast" : chance() < 0.25 ? "hs_brick_b" : "hs_brick");
                }
            }
        }
        for (int x = WX0 + 1; x < WX1; x++) {
            for (int z = PZ1 + 1; z < WZ1; z++) {
                set(x, 0, z, chance() < 0.3 ? "floor_concrete_crack" : "floor_concrete");
                set(x, S, z, "hs_roof");
            }
        }
        // high windows along the back, the dock's doors and the bumpers under them, the apron
        for (int x = WX0 + 3; x < WX1 - 2; x += 4) {
            if (x > 62 && x < 82) {
                continue;
            }
            set(x, 3, WZ1, (chance() < 0.3 ? "hs_ribbon_broken" : "hs_ribbon") + "[facing=south]");
        }
        for (int x = 64; x <= 80; x += 4) {
            for (int dx = 0; dx < 3; dx++) {
                for (int y = 1; y <= 3; y++) {
                    set(x + dx, y, WZ1, chance() < 0.7 ? "garage_door[facing=south]" : "air");
                }
                set(x + dx, 4, WZ1, "hs_spandrel");
            }
            set(x - 1, 1, WZ1 + 1, "sandbags[facing=south]");
        }
        for (int x = WX0; x <= WX1; x++) {
            for (int z = WZ1 + 1; z < d - 4; z++) {
                double r = chance();
                set(x, 0, z, r < 0.15 ? "asphalt_crack" : r < 0.22 ? "asphalt_patch" : "asphalt");
            }
        }
        // the boiler house's stack, brick to well over the tower's podium
        for (int y = 1; y <= 34; y++) {
            for (int x = WX0 + 2; x <= WX0 + 4; x++) {
                for (int z = WZ1 - 4; z <= WZ1 - 2; z++) {
                    boolean in = x == WX0 + 3 && z == WZ1 - 3;
                    set(x, y, z, in && y > S ? "air" : y == 34 ? "stone_trim" : chance() < 0.3 ? "hs_brick_b" : "hs_brick");
                }
            }
        }
        b.door(WX0, 1, 100, "fire_door", "west");
        b.door(WX0, 1, 110, "fire_door", "west");
        b.door(WX1, 1, 104, "fire_door", "east");
        // a ladder up the wing's west end to its roof, beside the staff lot
        for (int y = 1; y <= S; y++) {
            set(WX0 - 1, y, 106, "ladder[facing=west]");
        }
        set(WX0, S + 1, 106, "air");
    }

    // ------------------------------------------------------------------ the core: stairs and lifts

    private void core() {
        int[] ys = new int[3 + WARDS + 1];
        for (int i = 0; i < ys.length; i++) {
            ys[i] = i * S;
        }
        int hut = TROOF + 5;
        for (int x0 : new int[] {SW, SE}) {
            // the well's walls from the ground to its hut on the roof
            for (int y = 0; y <= hut; y++) {
                for (int z = CORE0; z <= CORE1; z++) {
                    set(x0 - 1, y, z, y > TROOF ? "hs_precast" : "hs_wall");
                    set(x0 + 3, y, z, y > TROOF ? "hs_precast" : "hs_wall");
                }
                for (int x = x0 - 1; x <= x0 + 3; x++) {
                    set(x, y, CORE0, y > TROOF ? "hs_precast" : "hs_wall");
                    set(x, y, CORE1, y > TROOF ? "hs_precast" : "hs_wall");
                }
            }
            for (int x = x0 - 1; x <= x0 + 3; x++) {
                for (int z = CORE0; z <= CORE1; z++) {
                    set(x, hut, z, "hs_roof");
                }
            }
            int[] last = Stairwell.build(p, rng, x0, CORE0, ys, false, 0, power ? 0.4 : 0.0);
            Stairwell.capTop(p, last);
            // the stair's signs by its doors
            for (int y : ys) {
                if (y < TROOF) {
                    set(x0, y + 3, CORE0 - 1, "fit_st_escape[facing=north]");
                }
            }
        }
        // the lift shafts' walls, the machine room over them on the roof
        int x0 = LIFTS[0] - 1, x1 = LIFTS[2] + 3, zb = CORE0 + LIFT_D + 1;
        for (int y = 0; y <= LIFT_TOP + 3; y++) {
            String wall = y > TROOF ? "hs_precast" : "hs_wall";
            for (int x = x0; x <= x1; x += 4) {
                for (int z = CORE0; z <= zb; z++) {
                    set(x, y, z, wall);
                }
            }
            for (int x = x0; x <= x1; x++) {
                set(x, y, zb, wall);
                if (y > TROOF) {
                    set(x, y, CORE0, wall);
                } else if (y % S >= 3 || y % S == 0) {
                    set(x, y, CORE0, wall);
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = CORE0; z <= zb; z++) {
                set(x, LIFT_TOP + 4, z, "hs_roof");
            }
        }
        int[] floors = levels();
        for (int lx : LIFTS) {
            LiftBuild.shaft(p::set, lx, CORE0 + 1, 3, LIFT_D, floors, 0, 0, LIFT_TOP);
        }
        // ELEVATORS over the doors on every floor
        for (int y : floors) {
            sign("lifts", LIFTS[1], y + 4, CORE0 - 1, "north");
        }
    }

    // ------------------------------------------------------------------ the roof

    private void roof() {
        // the parapet's screen on the tower's front, high enough to carry the name
        for (int x = 51; x <= 79; x++) {
            for (int y = TROOF + 1; y <= TROOF + 5; y++) {
                set(x, y, TZ0, y == TROOF + 5 ? "stone_trim" : "hs_precast");
            }
        }
        // the helipad over the tower's east end, its lights; the helicopter that made it here, or what's left
        int hx = 76, hz = 40;
        for (int i = 0; i < 49; i++) {
            set(hx + i % 7, TROOF, hz + i / 7, "hs_pad_h_" + i);
        }
        for (int[] c : new int[][] {{hx - 1, hz - 1}, {hx + 7, hz - 1}, {hx - 1, hz + 7}, {hx + 7, hz + 7}}) {
            set(c[0], TROOF + 1, c[1], (power && chance() < 0.6 ? "fit_numlight_on" : "fit_numlight") + "[facing=north]");
        }
        if (chance() < 0.55) {
            p.vehicle(hx + 3.5, TROOF + 1, hz + 3.5, (float) (chance() * 360), "heli_hornbill", rng.nextLong());
        }
        // the units, the vents
        for (int x = 34; x < 50; x += 5) {
            set(x, TROOF + 1, 33, "hvac");
            set(x, TROOF + 1, 54, "hvac");
        }
        for (int x = 86; x < 94; x += 4) {
            set(x, TROOF + 1, 33, "hvac");
        }
        // the machine room's door out to the roof
        Doors.abandoned(p, rng, LIFTS[0] + 1, TROOF + 1, CORE0 + LIFT_D + 1, Doors.STEEL, "south", Doors.hinge(rng));
        set(LIFTS[0] + 1, TROOF + 3, CORE0 + LIFT_D + 1, "hs_precast");
    }

    // ------------------------------------------------------------------ the names

    private void signs() {
        // MERCY GENERAL HOSPITAL on the screen, twenty by two, tile 0 at the high x facing north
        int x0 = 55;
        for (int i = 0; i < 40; i++) {
            if (chance() < 0.04) {
                continue;
            }
            String on = power && chance() > 0.15 ? "_on" : "";
            set(x0 + 19 - i % 20, TROOF + 3 - i / 20, TZ0 - 1, "hs_name_mercy_" + i + on + "[facing=north]");
        }
        // the red cross at each end of the tower, high up, three by three
        for (int end = 0; end < 2; end++) {
            String f = end == 0 ? "west" : "east";
            int x = end == 0 ? TX0 - 1 : TX1 + 1;
            for (int i = 0; i < 9; i++) {
                int c = i % 3, r = i / 3;
                // west faces: tile 0 at the low z; east faces: at the high z
                int z = end == 0 ? 42 + c : 44 - c;
                set(x, TROOF - 2 - r, z, "hs_cross_" + i + (power ? "_on" : "") + "[facing=" + f + "]");
            }
        }
        // the departments' signs outside: EMERGENCY over the walk-in
        sign("emergency", 104, 4, PZ0 - 1, "north");
    }

    // ------------------------------------------------------------------ the last days

    private void outbreak() {
        // the army's line across the front: barriers and sandbags, a gap at each door
        for (int x = 6; x < w - 6; x++) {
            boolean gap = x >= 60 && x <= 72 || x >= EX0 + 4 && x <= EX0 + 18;
            if (gap || chance() < 0.25) {
                continue;
            }
            set(x, 1, 3, chance() < 0.55 ? "jersey_barrier[facing=north]" : "sandbags[facing=north]");
        }
        for (int x = 10; x < w - 10; x += 26) {
            set(x, 1, 2, "sign_quarantine_" + rng.nextInt(5) + "[facing=north]");
        }
        // the ambulances in the bay and on the drive, an army truck, a police car slewed across
        p.vehicle(EX0 + 9.5, 1, 9.5, 90 + (float) (chance() * 10 - 5), "ambulance,ambulance_van", rng.nextLong());
        if (chance() < 0.7) {
            p.vehicle(EX0 + 17.5, 1, 9.5, 270 + (float) (chance() * 40 - 20), "ambulance,ambulance_van", rng.nextLong());
        }
        if (chance() < 0.6) {
            p.vehicle(34.5, 1, 9.5, 90 + (float) (chance() * 30 - 15), "jeep,jeep_open,box_truck", rng.nextLong());
        }
        if (chance() < 0.5) {
            p.vehicle(86.5, 1, 9.5, (float) (chance() * 360), "police_sedan,police_suv", rng.nextLong());
        }
        // the triage tents in the staff lot, the body bags laid out by the dock
        Placer q = p;
        tent(q, 12, PZ1 + 6, 12, "triage");
        tent(q, 12, PZ1 + 21, 10, "supply");
        for (int k = 0; k < 18; k++) {
            int x = WX0 + 4 + (k % 9) * 2, z = WZ1 + 3 + (k / 9) * 3;
            if (chance() < 0.85) {
                set(x, 1, z, "bunk_bodybag_head[facing=north]");
                set(x, 1, z + 1, "bunk_bodybag[facing=north]");
            }
        }
        for (int x = WX0 + 2; x < WX0 + 24; x += 2) {
            if (chance() < 0.4) {
                set(x, 1, WZ1 + 9, any("st_stretcher[facing=east]", "hs_biohazard[facing=north]", "trash_bag[facing=east]", "blood_splat"));
            }
        }
        // the debris on the drive
        for (int i = 0; i < 40; i++) {
            int x = 6 + rng.nextInt(w - 12), z = 4 + rng.nextInt(11);
            if (empty(x, 1, z)) {
                set(x, 1, z, any("paper", "paper", "blood_splat", "glass_shards", "trash_bag[facing=" + dir() + "]", "hs_tray[facing=north]"));
            }
        }
    }

    /**
     * A shelter tent on the lot, six across from x and len along from z, its door and word at its
     * north end ({@link Tents}).
     */
    private void tent(Placer q, int lx, int lz, int len, String label) {
        Tents t = new Tents(q, rng);
        t.hit = 0.5;
        t.tent(lx, lz, 6, len, Tents.canvasFor(label), label, 0.08);
    }
}
