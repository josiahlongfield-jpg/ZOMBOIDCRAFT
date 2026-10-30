package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ridgeview Labs in the Black Pine Mountains, where it started: a defence research site whose
 * programme ("Threshold") opened a breach into the Nether under the hill. The red spore and the
 * first Harvestman came through it; the lab poured a concrete plug over the ring, the plug
 * cracked, the army threw a cordon round the mountain and the cordon failed.
 *
 * On the surface the campus inside its wire: the headhouse over the stair down, the admin block,
 * and what the army brought when it took the place over (the tents, the gate, the towers, the
 * trucks), all of it left as it fell. Under it, four floors cut into the rock, from the lab
 * photos (refs/lab): B1 offices and records, B2 the labs and the specimen hall with the dead
 * Harvestman's tank, B3 the suits, decontamination and the plant, and off B3 the gallery high in
 * the ring hall, where the ring stands on its plinth with the plug glowing in its hole.
 *
 * The footprint is 300 square round the breach (ground zero's rings come later); the
 * underground runs x {@link #UX0} .. {@link #UX1}, z {@link #UZ0} .. {@link #HZ1}, floors
 * {@link #STOREY} apart from the ground's y = 0 down to the hall's floor at {@link #HALL_Y}.
 */
final class Ridgeview extends Site {
    static final int STOREY = 7;
    /** The floors: ground, B1, B2, B3 (their floor blocks' y); the hall's floor under them. */
    static final int B1 = -7, B2 = -14, B3 = -21, HALL_Y = -31;
    /** The floors' plan: x UX0 .. UX1, z UZ0 .. UZ1 (outer walls on those lines), corridor CZ0 .. CZ1. */
    static final int UX0 = 110, UX1 = 190, UZ0 = 112, UZ1 = 148, CZ0 = 129, CZ1 = 131;
    /** The ring hall south of them: inside x HX0 .. HX1, z HZ0 .. HZ1, up to y HALL_TOP. */
    static final int HX0 = 112, HX1 = 170, HZ0 = 150, HZ1 = 180, HALL_TOP = -6;
    /** The ring: its centre's x and y, and the three blocks of z it fills from RZ. */
    static final int RX = 141, RY = -19, RZ = 168;
    /** The stairwell's inside (Stairwell's x0, z0). */
    static final int SX = 112, SZ = 114;
    /** The way from B3 onto the gallery: two wide at x GX, GX + 1 through the floors' south wall. */
    static final int GX = 140;

    private final Placer p;
    private Kit kit;
    private Camp camp;

    Ridgeview(Place place, BlockSink out) {
        super(place, out);
        p = new Placer(out, 0, 0, 0, 0);
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        camp = new Camp(p, rng, 0.8);
        roads();
        kit.pave();
        // inside the wire the lawn's kept clear of woods, the army's camp on it
        for (int x = 96; x <= 204; x++) {
            for (int z = 96; z <= 210; z++) {
                if (kit.use(x, z) == Kit.FREE) {
                    kit.use[z * w + x] = Kit.PARK;
                }
            }
        }
        hall();
        floors();
        headhouse();
        new Stairwell(p, rng, SX, SZ, B3, STOREY).build(4, new String[] {"south", "south", "south", "south"}, true, "south");
        surface();
        clearDoors();
        kit.woods(0.25);
        kit.overgrow(0.7);
        kit.roadWeeds(0.5);
        kit.tidyPlants();
    }

    // ------------------------------------------------------------------ helpers

    private boolean chance(double c) {
        return rng.nextDouble() < c;
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private void set(int x, int y, int z, String b) {
        out.set(x, y, z, b);
    }

    private void box(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        fill(x0, y0, z0, x1, y1, z1, b);
    }

    /** A room's shell: the floor at y, walls round x0 .. x1 by z0 .. z1 (the inside), the ceiling over h rows of air. */
    private void shell(int x0, int z0, int x1, int z1, int y, int h, String floor, String wall, String ceiling) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x < x0 || x > x1 || z < z0 || z > z1;
                set(x, y, z, edge ? wall : floor);
                set(x, y + h + 1, z, ceiling);
                for (int r = 1; r <= h; r++) {
                    if (edge) {
                        setIfAir(x, y + r, z, wall);
                    } else {
                        set(x, y + r, z, "air");
                    }
                }
            }
        }
    }

    /** Strip lights down the middle of a room along x, every few blocks, some flickering, some dead or down. */
    private void lights(int x0, int z0, int x1, int z1, int y, boolean alongX) {
        int step = 4;
        if (alongX) {
            int z = (z0 + z1) / 2;
            for (int x = x0 + 1; x <= x1 - 1; x += step) {
                lamp(x, y, z, "x");
            }
        } else {
            int x = (x0 + x1) / 2;
            for (int z = z0 + 1; z <= z1 - 1; z += step) {
                lamp(x, y, z, "z");
            }
        }
    }

    private void lamp(int x, int y, int z, String axis) {
        double r = rng.nextDouble();
        if (r < 0.15) {
            set(x, y - 4, z, "bunk_tube_fallen[axis=" + axis + "]");
        } else if (r < 0.55) {
            set(x, y, z, "bunk_tube_flicker[axis=" + axis + "]");
        } else if (r < 0.75) {
            set(x, y, z, "bunk_tube_dangle[axis=" + axis + "]");
        } else {
            set(x, y, z, "bunk_tube[axis=" + axis + "]");
        }
    }

    /** A door through a wall along x (at z) or z (at x), two wide, steel; doorway cleared both sides. */
    /** Every door laid, as {x, y, z, along x ? 1 : 0}: kept clear on both sides once the rooms are furnished. */
    private final List<int[]> doors = new ArrayList<>();

    private void clearDoors() {
        for (int[] dr : doors) {
            for (int i = 0; i < 2; i++) {
                for (int k = -2; k <= 2; k++) {
                    if (k == 0) {
                        continue;
                    }
                    int x = dr[3] == 1 ? dr[0] + i : dr[0] + k, z = dr[3] == 1 ? dr[2] + k : dr[2] + i;
                    for (int r = 1; r <= 2; r++) {
                        String b = out.get(x, dr[1] + r, z);
                        if (b != null && !b.startsWith("rl_panel") && !b.startsWith("rl_shotcrete") && !b.startsWith("concrete_panel")
                                && !b.startsWith("home_wall") && !b.startsWith("window")) {
                            set(x, dr[1] + r, z, "air");
                        }
                    }
                }
            }
        }
    }

    /**
     * One of the files (world/files/LabFiles) dropped near (cx, cz) on the floor at y: the nearest
     * clear spot within reach that has something under it, kept off the door rows. On the
     * surface (y 0) the ground under it is terrain, so nothing under it is fine there.
     */
    private void file(String id, int cx, int y, int cz, int reach) {
        for (int r = 0; r <= reach; r++) {
            for (int tries = 0; tries < 12; tries++) {
                int x = cx + (r == 0 ? 0 : rng.nextInt(2 * r + 1) - r), z = cz + (r == 0 ? 0 : rng.nextInt(2 * r + 1) - r);
                if (nearDoor(x, z)) {
                    continue;
                }
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    String at = out.get(x, yy, z), under = out.get(x, yy - 1, z), over = out.get(x, yy + 1, z);
                    boolean free = at == null || at.startsWith("rl_files") || at.equals("blood_splat") || at.startsWith("sg_crust");
                    boolean floor = under != null ? !under.equals("air") && !under.startsWith("rl_doc") : yy == 1;
                    if (free && floor && over == null) {
                        set(x, yy, z, "rl_doc_" + id);
                        out.mark("file " + id, x, yy, z);
                        return;
                    }
                    if (at != null && !free) {
                        continue;
                    }
                    break;
                }
            }
        }
    }

    private boolean nearDoor(int x, int z) {
        for (int[] dr : doors) {
            if (Math.abs(x - dr[0]) <= 3 && Math.abs(z - dr[2]) <= 3) {
                return true;
            }
        }
        return false;
    }

    private void doorX(int x, int y, int z, String facing) {
        doors.add(new int[] {x, y, z, 1});
        for (int i = 0; i < 2; i++) {
            set(x + i, y + 3, z, "rl_panel");
        }
        Doors.pair(p, rng, x, y + 1, z, Doors.STEEL, facing);
    }

    private void doorZ(int x, int y, int z, String facing) {
        doors.add(new int[] {x, y, z, 0});
        set(x, y + 3, z, "rl_panel");
        set(x, y + 3, z + 1, "rl_panel");
        Doors.abandoned(p, rng, x, y + 1, z, Doors.STEEL, facing, "left");
        Doors.abandoned(p, rng, x, y + 1, z + 1, Doors.STEEL, facing, "right");
    }

    /** The floor's mess: papers, glass, blood, fallen tiles, the red growth where it has reached. */
    private void mess(int x0, int z0, int x1, int z1, int y, double papers, double blood, double growth) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (out.get(x, y + 1, z) != null) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < papers) {
                    set(x, y + 1, z, "rl_files[facing=" + any(FACINGS) + "]");
                } else if (r < papers + blood) {
                    set(x, y + 1, z, "blood_splat");
                } else if (r < papers + blood + growth) {
                    set(x, y + 1, z, "sg_crust_" + rng.nextInt(2) + "[facing=" + any(FACINGS) + "]");
                } else if (r < papers + blood + growth + 0.01) {
                    set(x, y + 1, z, "fit_ceiling_fallen[facing=" + any(FACINGS) + "]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ roads and the surface

    private void roads() {
        int cx = 150;
        double[] s = kit.beyond(2), n = kit.beyond(0), e = kit.beyond(1), wv = kit.beyond(3);
        kit.lane(Kit.Style.ROAD, s[0], s[1], cx + 0.5, 206);
        kit.lane(Kit.Style.LANE, cx + 0.5, 206, cx + 0.5, 186);
        kit.lane(Kit.Style.LANE, n[0], n[1], cx + 0.5, 60);
        kit.lane(Kit.Style.LANE, e[0], e[1], 240, d / 2 + 0.5);
        kit.lane(Kit.Style.LANE, wv[0], wv[1], 60, d / 2 + 0.5);
    }

    private void surface() {
        // the lab's own wire round the campus, the army's checkpoint at its gate
        camp.fence(96, 96, 204, 96, 0.15);
        camp.fence(96, 96, 96, 210, 0.15);
        camp.fence(204, 96, 204, 210, 0.15);
        camp.fence(96, 210, 146, 210, 0.2);
        camp.fence(156, 210, 204, 210, 0.2);
        camp.booth(158, 212);
        camp.boom(146, 213, 10, chance(0.5));
        camp.jersey(140, 216, 147, 216);
        camp.jersey(154, 216, 161, 216);
        camp.watchtower(98, 98);
        camp.watchtower(200, 98);
        camp.watchtower(98, 206);
        camp.watchtower(200, 206);
        camp.lightTower(132, 196, true);
        camp.lightTower(168, 196, false);
        // the army's camp on the lawn: tents, the aid station, the trucks, the pit they burned the dead in
        for (int i = 0; i < 4; i++) {
            camp.tent(102 + i * 9, 140, 12, any("olive", "olive", "tan"), true);
        }
        camp.hospital(176, 130, 12, 20);
        camp.helipad(180, 104);
        camp.helicopter(186, 110, chance(0.6));
        camp.fuelTanks(102, 186, 3);
        camp.toilets(120, 200, 4);
        camp.burnPit(176, 186, 5);
        camp.crates(130, 186, 6, 4);
        camp.trench(102, 160, 14);
        // the army's papers where they left them: orders in the tents, the log at the aid station, the pit, the gate
        file("army01", 105, 0, 146, 3);
        file("army02", 114, 0, 146, 3);
        file("army05", 123, 0, 146, 3);
        file("study02", 132, 0, 146, 3);
        file("army03", 182, 0, 140, 5);
        file("army04", 176, 0, 193, 3);
        file("army06", 156, 0, 214, 3);
        for (int i = 0; i < 4; i++) {
            p.vehicle(150.5 + i * 6, 1, 186 + (i & 1) * 2, 180f + (float) (rng.nextDouble() * 20 - 10),
                    any("=jeep@abandoned", "=jeep_open@wrecked", "=box_truck@burnt", "=ambulance@abandoned"), rng.nextLong());
        }
        groundZeroCore();
    }

    /**
     * The headhouse over the stair: a low concrete block with a flat roof, the stair's door
     * inside the lobby, the reception and security desk, the turnstiles, the lockers, the
     * tape and the plastic the army hung over the way down.
     */
    private void headhouse() {
        int x0 = 107, z0 = 108, x1 = 127, z1 = 128;
        shell(x0, z0, x1, z1, 0, 6, "lobby_floor_dark", "concrete_panel", "roof");
        // windows along the front and sides, two high, with a sill
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            if (Math.abs(x - 117) > 3) {
                set(x, 2, z1 + 1, any("window", "window", "window_broken") + "[axis=x]");
                set(x, 3, z1 + 1, any("window", "window_broken") + "[axis=x]");
            }
        }
        for (int z = z0 + 12; z <= z1 - 2; z += 3) {
            set(x1 + 1, 2, z, "window[axis=z]");
            set(x1 + 1, 3, z, "window[axis=z]");
        }
        doorX(116, 0, z1 + 1, "south");
        set(115, 4, z1 + 2, "rl_sgn_ridgeview_1[facing=south]");
        set(118, 4, z1 + 2, "rl_sgn_ridgeview_0[facing=south]");
        // the security desk and turnstile line, the lockers, the board
        for (int x = 120; x <= 125; x++) {
            set(x, 1, 122, "reception_desk");
        }
        set(122, 1, 120, "office_chair[facing=south]");
        set(123, 2, 122, "tv_monitor_on[facing=south]");
        for (int x = 112; x <= 119; x += 2) {
            set(x, 1, 121, "railing[facing=south]");
        }
        for (int z = 110; z <= 118; z++) {
            set(126, 1, z, "hs_locker[facing=west]");
            set(126, 2, z, "hs_locker_top[facing=west]");
        }
        set(108, 3, 124, "rl_sgn_threshold_0[facing=east]");
        set(108, 3, 125, "rl_sgn_threshold_1[facing=east]");
        // the army's plastic over the stair's door, the quarantine tape across it
        for (int x = SX; x <= SX + 1; x++) {
            set(x, 1, 120, "hs_plastic[facing=south]");
            set(x, 2, 120, "hs_plastic[facing=south]");
        }
        mess(x0, z0, x1, z1, 0, 0.05, 0.03, 0.0);
        file("lab01", 122, 0, 124, 3);
        file("study01", 112, 0, 112, 4);
        lights(x0, z0, x1, z1, 0 + 5, true);
    }

    /**
     * The core of ground zero over the ring hall: the ground gone to ash and the red growth
     * thickening toward the breach, stalks and pods where it's thickest, a ring of it creeping
     * up the headhouse. Placed from a smooth field so the stages run into each other in drifts.
     */
    private void groundZeroCore() {
        for (int x = 96; x <= 204; x++) {
            for (int z = 96; z <= 210; z++) {
                double r = Math.hypot(x - RX, z - (RZ + 1));
                double t = 1 - r / 42.0 + field(x, z) * 0.45 + field(z * 1.7, x * 1.7) * 0.2;
                if (t < 0.15 || out.get(x, 1, z) != null) {
                    continue;
                }
                String g = out.get(x, 0, z);
                if (g != null && !g.startsWith("grass") && !g.startsWith("minecraft:grass") && !g.startsWith("dirt")) {
                    continue;
                }
                set(x, 0, z, "burnt_ground");
                if (t < 0.35) {
                    set(x, 1, z, field(x * 2.3, z * 2.3) > 0.5 ? "sg_ash_drift" : "sg_ash[facing=north]");
                    continue;
                }
                int s = t < 0.5 ? 0 : t < 0.7 ? 1 : 2;
                set(x, 1, z, "sg_crust_" + s + "[facing=north]");
                if (s == 2 && chance((t - 0.6) * 0.25)) {
                    set(x, 1, z, "sg_stalk[facing=" + any(FACINGS) + "]");
                    set(x, 2, z, "sg_stalk_top[facing=north]");
                } else if (s >= 1 && chance((t - 0.45) * 0.08)) {
                    set(x, 1, z, "sg_pods[facing=north]");
                } else if (s >= 1 && chance(0.025)) {
                    set(x, 1, z, "sg_blister[facing=north]");
                }
            }
        }
    }

    private static double field(double x, double z) {
        double f = 0.11;
        return (Math.sin(x * f + 1.3) + Math.sin(z * f * 1.3 + 0.4) + Math.sin((x + z) * f * 0.7 + 2.1)) / 3;
    }

    // ------------------------------------------------------------------ the floors under the hill

    private void floors() {
        int[] ys = {B1, B2, B3};
        String[] floor = {"office_floor", "rl_epoxy", "bunk_floor"};
        for (int i = 0; i < 3; i++) {
            int y = ys[i];
            // the whole floor's box, then the corridor and the rooms cut into it
            box(UX0, y, UZ0, UX1, y + 6, UZ1, "rl_shotcrete");
            shell(UX0 + 1, CZ0, UX1 - 1, CZ1, y, 5, floor[i], "rl_panel_b", "bunk_ceiling");
            lights(UX0 + 1, CZ0, UX1 - 1, CZ1, y + 5, true);
            mess(UX0 + 1, CZ0, UX1 - 1, CZ1, y, 0.04, 0.03, i == 2 ? 0.08 : 0.0);
            // the stair's lobby: from the stair's way out (z SZ + 4) down to the corridor
            shell(UX0 + 1, SZ + 5, 121, CZ0 - 2, y, 5, floor[i], "rl_panel", "bunk_ceiling");
            for (int x = UX0 + 2; x <= 120; x++) {
                set(x, y + 1, CZ0 - 1, "air");
                set(x, y + 2, CZ0 - 1, "air");
                set(x, y + 3, CZ0 - 1, "air");
            }
            set(116, y + 3, SZ + 9, (i == 0 ? "rl_sgn_records_0" : i == 1 ? "rl_sgn_specimen_0" : "rl_sgn_decon_0") + "[facing=north]");
        }
        b1();
        b2();
        b3();
    }

    /** A room off the corridor: north of it (z UZ0 + 1 .. CZ0 - 2) or south (CZ1 + 2 .. UZ1 - 1), its door at dx from x0. */
    private int[] room(int x0, int x1, boolean north, int y, String floor, String wall, int doorAt) {
        int z0 = north ? UZ0 + 1 : CZ1 + 2, z1 = north ? CZ0 - 2 : UZ1 - 1;
        shell(x0, z0, x1, z1, y, 5, floor, wall, "bunk_ceiling");
        doorX(x0 + doorAt, y, north ? CZ0 - 1 : CZ1 + 1, north ? "north" : "south");
        lights(x0, z0, x1, z1, y + 5, true);
        return new int[] {x0, z0, x1, z1};
    }

    // B1: the offices and the records

    private void b1() {
        int y = B1;
        int[] sec = room(123, 133, true, y, "office_floor", "rl_panel", 4);
        for (int x = sec[0] + 1; x <= sec[2] - 1; x++) {           // the monitor wall
            set(x, y + 2, sec[1], "tv_monitor" + (chance(0.4) ? "_on" : "") + "[facing=south]");
            set(x, y + 3, sec[1], "tv_monitor" + (chance(0.3) ? "_on" : "") + "[facing=south]");
            set(x, y + 1, sec[1] + 1, "tv_desk[facing=south]");
        }
        for (int x = sec[0] + 2; x <= sec[2] - 2; x += 3) {
            set(x, y + 1, sec[1] + 3, "office_chair" + (chance(0.3) ? "_fallen" : "") + "[facing=north]");
        }
        set(sec[2], y + 1, sec[3], "bunk_switchboard[facing=west]");
        mess(sec[0], sec[1], sec[2], sec[3], y, 0.06, 0.03, 0);
        file("lab08", (sec[0] + sec[2]) / 2, y, (sec[1] + sec[3]) / 2, 5);

        int[] dir = room(135, 147, true, y, "home_floor_carpet_brown", "home_wall_panelling", 2);
        for (int x = dir[0] + 3; x <= dir[0] + 6; x++) {
            set(x, y + 1, dir[1] + 4, "desk[facing=south]");
        }
        set(dir[0] + 4, y + 1, dir[1] + 2, "office_chair[facing=south]");
        set(dir[0] + 5, y + 2, dir[1] + 4, "home_lamp_table[facing=south]");
        for (int z = dir[1]; z <= dir[1] + 6; z++) {
            set(dir[2], y + 1, z, "filing_cabinet[facing=west]");
        }
        set(dir[0], y + 2, dir[1] + 6, "bunk_map_wall[facing=east]");
        set(dir[2] - 2, y + 1, dir[3] - 1, "home_sofa_brown_m[facing=north]");
        mess(dir[0], dir[1], dir[2], dir[3], y, 0.18, 0.02, 0);
        file("lab11", (dir[0] + dir[2]) / 2, y, (dir[1] + dir[3]) / 2, 5);
        file("lab12", dir[0] + 4, y, dir[1] + 3, 4);

        office(149, 172, true, y);
        int[] brk = room(174, UX1 - 1, true, y, "floor_lino", "rl_panel", 3);
        for (int x = brk[0] + 1; x <= brk[0] + 5; x++) {
            set(x, y + 1, brk[1], any("home_counter_white", "home_counter_white") + "[facing=south]");
        }
        set(brk[0] + 6, y + 1, brk[1], "home_fridge[facing=south]");
        set(brk[0] + 6, y + 2, brk[1], "home_fridge_top[facing=south]");
        set(brk[2], y + 1, brk[1] + 2, "store_vending[facing=west]");
        set(brk[2], y + 2, brk[1] + 2, "store_vending_top[facing=west]");
        set(brk[2], y + 1, brk[1] + 4, "pk_coffee[facing=west]");
        for (int x = brk[0] + 3; x <= brk[0] + 9; x += 3) {
            set(x, y + 1, brk[1] + 8, "pk_coffee_table_round");
            set(x - 1, y + 1, brk[1] + 8, "pk_chair_dining[facing=east]");
            set(x + 1, y + 1, brk[1] + 8, "home_chair_tipped[facing=west]");
        }
        mess(brk[0], brk[1], brk[2], brk[3], y, 0.04, 0.05, 0);

        int[] rec = room(UX0 + 1, 136, false, y, "office_floor", "rl_panel", 10);
        for (int x = rec[0] + 1; x <= rec[2] - 1; x += 3) {           // rows of filing cabinets and boxes
            for (int z = rec[1] + 3; z <= rec[3] - 1; z++) {
                set(x, y + 1, z, chance(0.8) ? "filing_cabinet[facing=east]" : "rl_boxes[facing=east]");
                if (chance(0.5)) {
                    set(x, y + 2, z, "rl_boxes[facing=" + any(FACINGS) + "]");
                }
            }
        }
        mess(rec[0], rec[1], rec[2], rec[3], y, 0.3, 0.02, 0);
        int[] conf = room(138, 151, false, y, "home_floor_carpet_blue", "rl_panel", 2);
        for (int x = conf[0] + 3; x <= conf[2] - 3; x++) {
            set(x, y + 1, conf[1] + 5, "pk_desk_metal[facing=north]");
            set(x, y + 1, conf[1] + 4, "office_chair[facing=south]");
            set(x, y + 1, conf[1] + 6, "office_chair" + (chance(0.4) ? "_fallen" : "") + "[facing=north]");
        }
        set(conf[0] + 6, y + 3, conf[3], "navy_whiteboard[facing=north]");
        set(conf[0] + 7, y + 3, conf[3], "rl_sgn_threshold_1[facing=north]");
        set(conf[0] + 8, y + 3, conf[3], "rl_sgn_threshold_0[facing=north]");
        mess(conf[0], conf[1], conf[2], conf[3], y, 0.12, 0.02, 0);
        file("lab02", (conf[0] + conf[2]) / 2, y, (conf[1] + conf[3]) / 2, 5);
        office(153, 176, false, y);
        int[] lk = room(178, UX1 - 1, false, y, "home_floor_tile", "home_wall_tile_white", 2);
        for (int x = lk[0]; x <= lk[2]; x++) {
            set(x, y + 1, lk[3], "hs_locker[facing=north]");
            set(x, y + 2, lk[3], "hs_locker_top[facing=north]");
        }
        mess(lk[0], lk[1], lk[2], lk[3], y, 0.02, 0.06, 0);
    }

    /** An open-plan office: desks in pairs facing each other down the room, chairs, cabinets on the back wall. */
    private void office(int x0, int x1, boolean north, int y) {
        int[] r = room(x0, x1, north, y, "office_floor", "rl_panel", 3);
        for (int x = r[0] + 2; x <= r[2] - 2; x += 4) {
            for (int z = r[1] + 3; z <= r[3] - 3; z += 4) {
                set(x, y + 1, z, "desk[facing=north]");
                set(x + 1, y + 1, z, "desk[facing=north]");
                set(x, y + 1, z + 1, "desk[facing=south]");
                set(x + 1, y + 1, z + 1, "desk[facing=south]");
                if (chance(0.6)) {
                    set(x, y + 2, z, "home_desk_pc[facing=north]");
                }
                set(x, y + 1, z - 1, "office_chair" + (chance(0.3) ? "_fallen" : "") + "[facing=south]");
                set(x + 1, y + 1, z + 2, "office_chair[facing=north]");
            }
        }
        int back = north ? r[1] : r[3];
        for (int x = r[0]; x <= r[2]; x += 2) {
            set(x, y + 1, back, "filing_cabinet[facing=" + (north ? "south" : "north") + "]");
        }
        mess(r[0], r[1], r[2], r[3], y, 0.14, 0.03, 0);
    }

    // B2: the labs and the specimen hall

    private void b2() {
        int y = B2;
        int[] la = room(123, 147, true, y, "rl_epoxy", "rl_panel", 4);
        bench(la, y);
        file("lab05", (la[0] + la[2]) / 2, y, (la[1] + la[3]) / 2, 6);
        int[] cr = room(149, 166, true, y, "lobby_floor_white", "rl_panel", 3);
        for (int x = cr[0] + 1; x <= cr[2] - 1; x += 4) {                 // the cleanroom's benches and racks
            for (int z = cr[1] + 3; z <= cr[3] - 2; z++) {
                set(x, y + 1, z, "un_lab_scope[facing=east]");
            }
        }
        for (int z = cr[1]; z <= cr[3]; z += 3) {
            set(cr[2], y + 1, z, "rl_glovebox[facing=west]");
            set(cr[2], y + 2, z, "rl_glovebox_top[facing=west]");
        }
        for (int x = cr[0]; x <= cr[2]; x++) {
            set(x, y + 1, cr[1] + 1, "hs_plastic[facing=south]");
            set(x, y + 2, cr[1] + 1, "hs_plastic[facing=south]");
        }
        mess(cr[0], cr[1], cr[2], cr[3], y, 0.05, 0.03, 0.02);
        file("lab04", (cr[0] + cr[2]) / 2, y, (cr[1] + cr[3]) / 2, 5);
        int[] sv = room(168, UX1 - 1, true, y, "lobby_floor_dark", "rl_panel", 2);
        for (int x = sv[0] + 2; x <= sv[2] - 1; x += 3) {
            for (int z = sv[1] + 1; z <= sv[3] - 3; z++) {
                set(x, y + 1, z, "bunk_server[facing=east]");
                set(x, y + 2, z, "bunk_server_top[facing=east]");
            }
            for (int z = sv[1]; z <= sv[3]; z++) {
                set(x, y + 5, z, "bunk_cable_tray[axis=z]");
            }
        }
        mess(sv[0], sv[1], sv[2], sv[3], y, 0.03, 0.02, 0);

        specimenHall(y);
        int[] ah = room(149, 165, false, y, "rl_epoxy_b", "rl_panel_b", 2);
        for (int x = ah[0]; x <= ah[2]; x++) {                           // the holding pens: mesh fronts down the back
            set(x, y + 1, ah[3] - 3, "bunk_cage[axis=x]");
            set(x, y + 2, ah[3] - 3, "bunk_cage[axis=x]");
            if (x % 4 == 0) {
                for (int z = ah[3] - 2; z <= ah[3]; z++) {
                    set(x, y + 1, z, "bunk_cage[axis=z]");
                    set(x, y + 2, z, "bunk_cage[axis=z]");
                }
            } else if (chance(0.3)) {
                set(x, y + 1, ah[3] - 1, "blood_splat");
            }
        }
        mess(ah[0], ah[1], ah[2], ah[3] - 4, y, 0.03, 0.08, 0.05);
        int[] lb = room(167, UX1 - 1, false, y, "rl_epoxy", "rl_panel", 2);
        bench(lb, y);
        file("lab09", (lb[0] + lb[2]) / 2, y, (lb[1] + lb[3]) / 2, 6);
    }

    /** A lab: benches in rows with sinks and microscopes, fume hoods down one wall, a glove box. */
    private void bench(int[] r, int y) {
        for (int z = r[1] + 3; z <= r[3] - 2; z += 4) {
            for (int x = r[0] + 2; x <= r[2] - 3; x++) {
                String b = x % 5 == 0 ? "un_lab_sink" : x % 3 == 0 ? "un_lab_scope" : any("un_lab_bench", "hs_lab_bench");
                set(x, y + 1, z, b + "[facing=north]");
                set(x, y + 1, z + 1, any("un_lab_bench", "un_lab_scope") + "[facing=south]");
            }
        }
        for (int z = r[1]; z <= r[3]; z += 2) {
            set(r[2], y + 1, z, "un_fume_lo[facing=west]");
            set(r[2], y + 2, z, "un_fume_hi[facing=west]");
        }
        set(r[0], y + 1, r[1] + 1, "rl_eyewash[facing=east]");
        set(r[0], y + 2, r[1] + 1, "rl_eyewash[facing=east]");
        mess(r[0], r[1], r[2], r[3], y, 0.08, 0.03, 0.01);
    }

    /**
     * The specimen hall: two tanks on their plinths, the dead Harvestman's whole, the other one
     * cracked and drained across the floor; the desk that watched them, the biohazard signs.
     */
    private void specimenHall(int y) {
        int[] r = room(UX0 + 1, 147, false, y, "rl_epoxy", "rl_panel", 14);
        tank(r[0] + 6, r[1] + 5, y, true);
        tank(r[0] + 20, r[1] + 5, y, false);
        for (int x = r[0] + 5; x <= r[0] + 12; x++) {
            set(x, y + 1, r[1] + 1, (x % 3 == 0 ? "rl_console_on" : "rl_console") + "[facing=south]");
        }
        for (int[] s : new int[][] {{r[0] + 25, r[3] - 1}, {r[0] + 21, r[3]}, {r[0] + 22, r[3] - 2}, {r[0] + 19, r[3] - 1}}) {
            set(s[0], y + 1, s[1], "rl_spill");
        }
        set(r[0] + 2, y + 3, r[3] + 1, "rl_sgn_bsl4_1[facing=north]");
        set(r[0] + 3, y + 3, r[3] + 1, "rl_sgn_bsl4_0[facing=north]");
        mess(r[0], r[1], r[2], r[3], y, 0.04, 0.03, 0.04);
    }

    /** A specimen tank three square and five high at (x0, z0): plinth, three of glass round its posts, the cap. */
    private void tank(int x0, int z0, int y, boolean whole) {
        for (int dx = 0; dx < 3; dx++) {
            for (int dz = 0; dz < 3; dz++) {
                set(x0 + dx, y + 1, z0 + dz, "rl_tank_plinth");
                set(x0 + dx, y + 5, z0 + dz, "rl_tank_cap");
                boolean corner = dx != 1 && dz != 1;
                for (int k = 2; k <= 4; k++) {
                    String b;
                    if (corner) {
                        b = "rl_tank_post";
                    } else if (whole) {
                        b = dx == 1 && dz == 1 && k < 4 ? "rl_tank_body" : "rl_tank_glass";
                    } else if (dz == 2 && k < 4) {
                        b = "air";      // the side it broke out of
                    } else {
                        b = k == 2 ? "rl_tank_empty" : "rl_tank_broken";
                    }
                    set(x0 + dx, y + k, z0 + dz, b);
                }
            }
        }
        if (!whole) {
            set(x0 + 1, y + 2, z0 + 2, "air");
            set(x0 + 1, y + 3, z0 + 2, "air");
            p.mark("harvestman spawn", x0 + 1, y + 2, z0 + 4);
        } else {
            p.mark("harvestman stasis", x0 + 1, y + 2, z0 + 1);
        }
    }

    // B3: the suits, the decon line, the plant, and the way onto the gallery

    private void b3() {
        int y = B3;
        int[] su = room(123, 139, true, y, "bunk_floor", "rl_panel", 3);
        for (int x = su[0] + 1; x <= su[2] - 1; x += 2) {
            set(x, y + 1, su[1], "rl_suit[facing=south]");
            set(x, y + 2, su[1], "rl_suit_top[facing=south]");
        }
        for (int x = su[0]; x <= su[2]; x++) {
            set(x, y + 1, su[3], "bunk_locker[facing=north]");
            set(x, y + 2, su[3], "bunk_locker_top[facing=north]");
        }
        set(su[0] + 6, y + 3, su[1], "rl_sgn_bsl4_1[facing=south]");
        set(su[0] + 7, y + 3, su[1], "rl_sgn_bsl4_0[facing=south]");
        mess(su[0], su[1], su[2], su[3], y, 0.02, 0.03, 0.04);
        file("lab06", (su[0] + su[2]) / 2, y, (su[1] + su[3]) / 2, 5);
        int[] dc = room(141, 151, true, y, "bunk_floor_drain", "home_wall_tile_white", 3);
        for (int x = dc[0]; x <= dc[2]; x += 2) {
            set(x, y + 1, dc[1], "bunk_shower[facing=south]");
            set(x, y + 1, dc[3], "bunk_shower[facing=north]");
        }
        for (int z = dc[1] + 2; z <= dc[3] - 2; z += 3) {
            for (int x = dc[0]; x <= dc[2]; x++) {
                set(x, y + 1, z, "hs_plastic[facing=south]");
                set(x, y + 2, z, "hs_plastic[facing=south]");
            }
            set(dc[0] + 4, y + 1, z, "air");
            set(dc[0] + 4, y + 2, z, "air");
            set(dc[0] + 5, y + 1, z, "air");
            set(dc[0] + 5, y + 2, z, "air");
        }
        set(dc[0] + 4, y + 3, dc[1], "rl_sgn_decon_1[facing=south]");
        set(dc[0] + 5, y + 3, dc[1], "rl_sgn_decon_0[facing=south]");
        mess(dc[0], dc[1], dc[2], dc[3], y, 0.01, 0.05, 0.06);
        file("lab13", (dc[0] + dc[2]) / 2, y, (dc[1] + dc[3]) / 2, 5);
        int[] pl = room(153, UX1 - 1, true, y, "bunk_floor", "rl_shotcrete", 3);
        for (int x = pl[0] + 2; x <= pl[2] - 6; x += 7) {               // the generators in a row, their exhausts up
            for (int z = pl[1] + 2; z <= pl[1] + 4; z++) {
                set(x, y + 1, z, "bunk_gen_engine[facing=south]");
                set(x + 1, y + 1, z, "bunk_gen_alternator[facing=south]");
            }
            set(x, y + 5, pl[1] + 3, "rl_duct[axis=z]");
        }
        for (int x = pl[0]; x <= pl[2]; x++) {
            set(x, y + 1, pl[3] - 1, "rl_pipe[axis=x]");
            set(x, y + 4, pl[1], "rl_pipe[axis=x]");
        }
        set(pl[0] + 3, y + 3, pl[3] + 1, "rl_sgn_plant_1[facing=north]");
        set(pl[0] + 4, y + 3, pl[3] + 1, "rl_sgn_plant_0[facing=north]");
        mess(pl[0], pl[1], pl[2], pl[3], y, 0.01, 0.02, 0.05);

        int[] ct = room(UX0 + 1, 137, false, y, "bunk_floor", "rl_panel", 8);
        for (int x = ct[0] + 2; x <= ct[2] - 2; x++) {                  // the ring's control room, facing the hall
            set(x, y + 1, ct[3] - 1, (chance(0.4) ? "rl_console_on" : "rl_console") + "[facing=south]");
            set(x, y + 2, ct[3], "tv_monitor" + (chance(0.5) ? "_on" : "") + "[facing=north]");
        }
        for (int x = ct[0] + 3; x <= ct[2] - 3; x += 3) {
            set(x, y + 1, ct[3] - 3, "office_chair" + (chance(0.4) ? "_fallen" : "") + "[facing=south]");
        }
        set(ct[0], y + 1, ct[1], "rl_beacon");
        set(ct[2], y + 1, ct[1], "rl_beacon");
        mess(ct[0], ct[1], ct[2], ct[3], y, 0.12, 0.04, 0.05);
        file("lab03", ct[0] + 4, y, ct[3] - 2, 4);
        file("lab07", ct[0] + 10, y, ct[3] - 2, 4);
        file("lab10", ct[0] + 16, y, ct[3] - 2, 4);
        file("lab14", (ct[0] + ct[2]) / 2, y, ct[1] + 2, 5);
        // the way through to the gallery: the passage south from the corridor, the blast door hanging off it
        shell(GX - 1, CZ1 + 2, GX + 2, UZ1 - 1, y, 5, "bunk_floor_line", "rl_panel_b", "bunk_ceiling");
        doorX(GX, y, CZ1 + 1, "south");
        for (int z = CZ1 + 2; z <= UZ1 - 1; z++) {
            set(GX - 1, y + 1, z, "rl_hazard[axis=z]");
            set(GX + 2, y + 1, z, "rl_hazard[axis=z]");
        }
        set(GX - 1, y + 3, CZ1 + 2, "rl_sgn_ring_1[facing=east]");
        set(GX - 1, y + 3, CZ1 + 3, "rl_sgn_ring_0[facing=east]");
        for (int dy = 1; dy <= 3; dy++) {                                   // the opening through both walls
            for (int x = GX; x <= GX + 1; x++) {
                set(x, y + dy, UZ1, "air");
                set(x, y + dy, UZ1 + 1, "air");
            }
        }
        set(GX - 1, y + 1, UZ1 + 1, "bunk_blast_w0[facing=north]");
        set(GX - 1, y + 2, UZ1 + 1, "bunk_blast_w1[facing=north]");
        set(GX - 1, y + 3, UZ1 + 1, "bunk_blast_w2[facing=north]");
        set(GX, y, UZ1, "bunk_floor_line");
        set(GX + 1, y, UZ1, "bunk_floor_line");
        set(GX, y, UZ1 + 1, "grate");
        set(GX + 1, y, UZ1 + 1, "grate");
        int[] st = room(152, UX1 - 1, false, y, "bunk_floor", "rl_shotcrete", 3);
        for (int x = st[0] + 1; x <= st[2] - 1; x += 3) {
            for (int z = st[1] + 2; z <= st[3]; z++) {
                set(x, y + 1, z, any("bunk_rack_crates", "bunk_rack_drums", "bunk_rack_boxes") + "[facing=east]");
            }
        }
        mess(st[0], st[1], st[2], st[3], y, 0.01, 0.02, 0.08);
    }

    // ------------------------------------------------------------------ the ring hall

    /**
     * The ring hall: a cavern of shotcrete under the hill, the gallery high along its north wall
     * off B3 with the stair down to the floor, the ring on its plinth with the plug in its hole,
     * the cooling lines and cable running to it, the desks along the front, the duct in the
     * crown, and the red growth spreading from the plug's foot.
     */
    private void hall() {
        box(HX0 - 1, HALL_Y, HZ0 - 1, HX1 + 1, HALL_TOP + 1, HZ1 + 1, "rl_shotcrete");
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                set(x, HALL_Y, z, chance(0.2) ? "rl_epoxy_b" : chance(0.08) ? "rl_epoxy_crack" : "rl_epoxy");
                for (int y = HALL_Y + 1; y <= HALL_TOP; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0 - 1; z <= HZ1 + 1; z += HZ1 - HZ0 + 2) {
                for (int y = HALL_Y + 1; y <= HALL_TOP; y++) {
                    if (chance(0.15)) {
                        set(x, y, z, "rl_shotcrete_b");
                    }
                }
            }
            if (x % 2 == 0) {
                set(x, HALL_TOP, HZ1 - 1, "rl_duct[axis=x]");
            }
        }
        // the plinth and the ring on it, the plug in its middle layer
        int py = HALL_Y + 1;
        box(RX - 7, py, RZ - 1, RX + 7, py, RZ + 3, "rl_tank_plinth");
        for (String[] c : layout("ring")) {
            int x = RX + Integer.parseInt(c[0]), y = RY + Integer.parseInt(c[1]);
            for (int z = RZ; z <= RZ + 2; z++) {
                boolean behind = z > RZ && c[2].contains("_i");
                set(x, y, z, behind ? c[2] + "_p" : c[2]);
            }
        }
        for (String[] c : layout("plug")) {
            int x = RX + Integer.parseInt(c[0]), y = RY + Integer.parseInt(c[1]);
            set(x, y, RZ + 1, c[2]);
            set(x, y, RZ + 2, "rl_plug");
            if (c[2].endsWith("_glow")) {
                p.mark("breach glow " + c[2], x, y, RZ + 1);
            }
        }
        p.mark("breach", RX, RY, RZ + 1);
        for (int x = RX - 7; x <= RX + 7; x++) {
            if (chance(0.6)) {
                set(x, py + 1, RZ - 2, "rl_rubble[facing=" + any(FACINGS) + "]");
            }
        }
        // the lines and the cable from the plant side, the desks and beacons along the front
        for (int z = HZ0 + 6; z < RZ - 2; z++) {
            set(RX - 7, py, z, z % 6 == 0 ? "rl_pipe_valve[axis=z]" : "rl_pipe[axis=z]");
            set(RX + 7, py, z, "rl_cable[axis=z]");
        }
        for (int x = RX - 12; x <= RX + 12; x++) {
            if (x != RX - 7 && x != RX + 7) {
                set(x, py, RZ - 6, "rl_hazard[axis=x]");
            }
            if (Math.abs(x - RX) > 2) {
                set(x, py, HZ0 + 4, (x % 3 == 0 ? "rl_console_on" : "rl_console") + "[facing=south]");
            }
        }
        set(RX - 14, py, RZ - 4, "rl_beacon");
        set(RX + 14, py, RZ - 4, "rl_beacon");
        // the growth: thick at the plug's foot, thinning across the floor, veins up the back wall
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                if (out.get(x, py, z) != null) {
                    continue;
                }
                double t = 1 - Math.hypot(x - RX, (z - RZ) * 1.4) / 26.0 + field(x, z) * 0.25;
                if (t > 0.25) {
                    set(x, py, z, "sg_crust_" + (t < 0.45 ? 0 : t < 0.65 ? 1 : 2) + "[facing=north]");
                    if (t > 0.7 && chance(0.12)) {
                        set(x, py, z, chance(0.5) ? "sg_pods[facing=north]" : "sg_blister[facing=north]");
                    }
                }
            }
        }
        for (int x = HX0; x <= HX1; x++) {
            for (int y = py; y <= py + 12; y++) {
                double t = 1 - Math.abs(x - RX) / 30.0 - (y - py) / 14.0 + field(x, y * 3) * 0.3;
                if (t > 0.2 && chance(0.8)) {
                    set(x, y, HZ1, "sg_vein_" + (t < 0.4 ? 0 : t < 0.6 ? 1 : 2) + "[facing=north]");
                }
            }
        }
        // lights: caged lamps along both long walls, most dead
        for (int x = HX0 + 2; x <= HX1 - 2; x += 6) {
            set(x, HALL_TOP - 1, HZ0, chance(0.4) ? "bunk_lamp_lit" : "bunk_lamp");
            set(x, HALL_TOP - 1, HZ1, chance(0.3) ? "bunk_lamp_red_lit" : "bunk_lamp_red");
        }
        gallery();
    }

    /** The gallery along the hall's north wall at B3, three wide, railed, and the stair down the west end to the floor. */
    private void gallery() {
        int y = B3;
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z <= HZ0 + 2; z++) {
                set(x, y, z, "grate");
            }
            if (x > HX0 + 2) {
                set(x, y + 1, HZ0 + 3, "railing[facing=south]");
            }
            if (x % 4 == 0) {
                set(x, y - 1, HZ0 + 2, "rl_pipe[axis=z]");
            }
        }
        // the stair down: two wide in x HX0 .. HX0 + 1, descending south from the gallery's end
        int rise = y - HALL_Y;
        for (int i = 1; i < rise; i++) {
            for (int x = HX0; x <= HX0 + 1; x++) {
                set(x, y - i, HZ0 + 2 + i, "stairs_concrete[facing=north]");
                for (int k = y - i - 1; k >= HALL_Y + 1; k--) {
                    set(x, k, HZ0 + 2 + i, "rl_shotcrete");
                }
            }
            set(HX0 + 2, y - i + 1, HZ0 + 2 + i, "railing[facing=east]");
        }
        set(HX0 + 2, y + 1, HZ0 + 3, "railing[facing=east]");
    }

    /** One of the layouts lab.py writes (ring, plug): each line's dx, dy and block. */
    private static List<String[]> layout(String name) {
        List<String[]> rows = new ArrayList<>();
        String path = "/data/zomboidcraft/ridgeview/" + name + ".txt";
        try (InputStream in = Ridgeview.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing " + path);
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    rows.add(line.trim().split("\\s+"));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows;
    }
}
