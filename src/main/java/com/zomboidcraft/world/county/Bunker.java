package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The core every hidden bunker shares, dressed for whoever built it: a blast door, the
 * decontamination room behind it, one long corridor with the living rooms off both sides, the
 * storage hall across its far end, and the armoury deepest of all, behind the hall and a weapons
 * cage. Reaching the guns means passing every other room. All of it is the bunker kit (bunk_
 * blocks): thick concrete walls painted green and cream, riveted plate in the plant room, tile in
 * the washrooms, pipes and cable trays along the ceilings, stencilled names by the doors. The
 * generator is dead, so every lamp is out.
 *
 * Works in its own frame: the blast door is two wide at x = 0..1 in the north wall (z = 0),
 * facing out (north) toward whatever way in the kind of bunker has; the floor is y = 0, three
 * rows of room above it, the ceiling y = 4. Everything else runs south (+z) from the door, and
 * west of x = 0 as far as {@link #bounds()} says. Walls between rooms are two blocks thick, so
 * each room paints its own face. Pieces keep to the walls, and a piece goes in only if every free
 * cell of its room can still be walked to from the room's door.
 */
public final class Bunker {
    /** Who built it; each kind dresses the same core its own way. */
    /** ARMY: the small coded bunker under Camp Black Pine (CampBlackPine.keypadBunker, world/Keypads). */
    public enum Kind { TOWER, SHIP, SHELTER, FOREST, ARMY }

    /**
     * SEALED: nobody has been in since. LOOTED: a second copy of the map led someone here first.
     * BREACHED: the blast door was forced and the dead poured in; the lights hang off their cables
     * and stutter on a dying circuit, and there is blood, but every room is still what it was and
     * still worth searching.
     */
    public enum State { SEALED, LOOTED, BREACHED }

    enum Use { DECON, CORRIDOR, BUNKS, MESS, WASH, MED, COMMS, PLANT, PANTRY, SERVER, COMMAND, EXEC, STORAGE, ARMOURY }

    private static final String[] DIRS = {"north", "east", "south", "west"};
    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final byte FREE = 0, TAKEN = 1, WAY = 2, FRONT = 3;
    private static final int TOP = 4;
    /** Kit pieces that don't turn. */
    private static final Set<String> PLAIN = Set.of("bunk_lamp_hang", "bunk_lamp_hang_lit", "bunk_map_table", "bunk_iv_stand",
            "bunk_drum_water", "bunk_tank", "bunk_tank_top", "bunk_casings", "bunk_walkway", "bunk_floor_drain");
    /** Kit containers, which a looted bunker has mostly emptied. */
    private static final Set<String> LOOT = Set.of("bunk_first_aid", "bunk_footlocker", "bunk_locker", "bunk_locker_top",
            "bunk_range", "bunk_sink", "bunk_counter", "bunk_desk", "bunk_radio_desk", "bunk_medcab", "bunk_rack_boxes",
            "bunk_rack_drums", "bunk_rack_crates", "bunk_crate_ration", "bunk_crate_ammo", "bunk_crate_gun", "bunk_tins",
            "bunk_gun_rack", "bunk_pegboard");

    private final Placer p;
    private final SplittableRandom rng;
    private final Kind kind;
    private final State state;
    private final List<Room> rooms = new ArrayList<>();
    /** The corridor's width; it runs x = 0 .. cw - 1. */
    private int cw;
    /** The outer faces of the walls round everything. */
    private int bx0, bz0, bx1, bz1;
    private boolean[][] carved;
    private double damp;
    private boolean ship;
    private Room decon, corridor, storage, armoury;
    /** How far gone the place is (0 clean .. 1 rotten), and the noise that spreads the wear in patches. */
    private double wear;
    private Noise grime;

    public Bunker(Placer p, SplittableRandom rng, Kind kind, State state) {
        this.p = p;
        this.rng = rng;
        this.kind = kind;
        this.state = state;
        plan();
        grime = new Noise(rng.nextLong());
        wear = switch (kind) {
            case TOWER -> 0.3;
            case SHIP -> 0.45;
            case SHELTER -> 0.5;
            case FOREST -> 0.65;
            case ARMY -> 0.35;
        } + (state == State.LOOTED ? 0.1 : state == State.BREACHED ? 0.2 : 0);
    }

    /** {x0, z0, x1, z1}: the outside of the walls, in the bunker's frame. */
    public int[] bounds() {
        return new int[] {bx0, bz0, bx1, bz1};
    }

    public String describe() {
        StringBuilder b = new StringBuilder("bunker " + kind.name().toLowerCase() + " " + state.name().toLowerCase()
                + " " + (bx1 - bx0 + 1) + "x" + (bz1 - bz0 + 1) + " at " + bx0 + "," + bz0 + "\n");
        for (Room r : rooms) {
            b.append("  ").append(r.use.name().toLowerCase()).append(' ').append(r.x0).append(',').append(r.z0)
                    .append(' ').append(r.x1).append(',').append(r.z1)
                    .append(" door ").append(r.ways.get(0)[0]).append(',').append(r.ways.get(0)[1]).append('\n');
        }
        return b.toString();
    }

    public void build() {
        shell();
        for (Room r : rooms) {
            carve(r);
        }
        surfaces();
        openings();
        for (Room r : rooms) {
            dress(r);
        }
        for (Room r : rooms) {
            weather(r);
            if (state == State.BREACHED) {
                breach(r);
            }
        }
        if (state == State.BREACHED) {
            forced();
        }
        if (state != State.SEALED) {
            for (Room r : rooms) {
                r.scatter("paper", 1 + rng.nextInt(3));
                if (rng.nextDouble() < 0.3) {
                    r.scatter("glass_shards", 1);
                }
            }
        }
        if (state == State.LOOTED) {
            pried();
        }
    }

    // ------------------------------------------------------------------ the plan

    private void plan() {
        ship = kind == Kind.SHIP;
        boolean small = kind == Kind.FOREST || kind == Kind.ARMY;
        cw = small ? 2 : 3;
        damp = switch (kind) {
            case FOREST -> 0.25;
            case ARMY -> 0.12;
            case SHELTER -> 0.15;
            case SHIP -> 0.1;
            case TOWER -> 0.04;
        };
        int dMin = switch (kind) {
            case FOREST, ARMY -> 5;
            case SHELTER -> 8;
            default -> 6;
        };
        int dLen = small ? 4 : 5;
        decon = room(Use.DECON, -2, 1, cw + 1, dLen);
        decon.way(0, 1);
        decon.way(1, 1);
        decon.way(0, dLen);
        decon.way(1, dLen);
        int c0 = dLen + 3;
        int curW = c0, curE = c0;
        List<Room> side = new ArrayList<>();
        for (Use u : uses()) {
            int len = length(u);
            int depth = dMin + rng.nextInt(3);
            boolean west = curW < curE || curW == curE && rng.nextBoolean();
            int z0 = west ? curW : curE, z1 = z0 + len - 1;
            Room r = west ? room(u, -2 - depth, z0, -3, z1) : room(u, cw + 2, z0, cw + 1 + depth, z1);
            r.toCorridor = west ? 1 : 3;
            side.add(r);
            if (west) {
                curW = z1 + 3;
            } else {
                curE = z1 + 3;
            }
        }
        int cEnd = Math.max(curW, curE) - 3;
        corridor = room(Use.CORRIDOR, 0, c0, cw - 1, cEnd);
        corridor.way(0, c0);
        corridor.way(1, c0);
        // a door into each side room from the corridor, through the two-thick wall
        for (Room r : side) {
            int dz = r.z0 + 1 + rng.nextInt(Math.max(1, r.d() - 2));
            boolean west = r.toCorridor == 1;
            r.way(west ? r.x1 : r.x0, dz);
            corridor.way(west ? 0 : cw - 1, dz);
            r.door = new int[] {west ? -1 : cw, dz, west ? 3 : 1};
        }
        int minX = -2 - dMin, maxX = cw + 1 + dMin;
        for (Room r : side) {
            minX = Math.min(minX, r.x0);
            maxX = Math.max(maxX, r.x1);
        }
        int sLen = small ? 6 + rng.nextInt(2) : kind == Kind.SHELTER ? 11 + rng.nextInt(3) : 9 + rng.nextInt(3);
        storage = room(Use.STORAGE, minX, cEnd + 3, maxX, cEnd + 2 + sLen);
        for (int x = 0; x < cw; x++) {
            storage.way(x, storage.z0);
            corridor.way(x, cEnd);
        }
        int aw = small ? 5 : 7 + rng.nextInt(3), aLen = small ? 5 : 6 + rng.nextInt(2);
        int mid = cw / 2;
        int ax0 = mid - aw / 2;
        armoury = room(Use.ARMOURY, ax0, storage.z1 + 3, ax0 + aw - 1, storage.z1 + 2 + aLen);
        armoury.way(mid, armoury.z0);
        storage.way(mid, storage.z1);
        armoury.door = new int[] {mid, storage.z1 + 1, 2};
        rooms.add(decon);
        rooms.add(corridor);
        rooms.addAll(side);
        rooms.add(storage);
        rooms.add(armoury);
        bx0 = Integer.MAX_VALUE;
        bx1 = Integer.MIN_VALUE;
        for (Room r : rooms) {
            bx0 = Math.min(bx0, r.x0 - 1);
            bx1 = Math.max(bx1, r.x1 + 1);
        }
        bz0 = 0;
        bz1 = armoury.z1 + 1;
        carved = new boolean[bx1 - bx0 + 1][bz1 - bz0 + 1];
        for (Room r : rooms) {
            palette(r);
        }
    }

    private Room room(Use u, int x0, int z0, int x1, int z1) {
        return new Room(u, x0, z0, x1, z1);
    }

    /** The living rooms off the corridor, nearest the door first; the plant room is always last. */
    private List<Use> uses() {
        List<Use> u = new ArrayList<>();
        switch (kind) {
            case FOREST -> {
                u.add(Use.BUNKS);
                u.add(Use.MESS);
                if (rng.nextBoolean()) {
                    u.add(Use.MED);
                }
                if (rng.nextBoolean()) {
                    u.add(Use.WASH);
                }
            }
            case TOWER -> {
                u.add(Use.EXEC);
                u.add(Use.SERVER);
                u.add(Use.COMMAND);
                u.add(Use.BUNKS);
                u.add(Use.MESS);
                u.add(Use.WASH);
                u.add(Use.MED);
            }
            case SHELTER -> {
                u.add(Use.BUNKS);
                u.add(Use.BUNKS);
                u.add(Use.WASH);
                u.add(Use.MESS);
                u.add(Use.MED);
                u.add(Use.COMMAND);
                u.add(Use.BUNKS);
                u.add(Use.BUNKS);
                u.add(Use.PANTRY);
                if (rng.nextBoolean()) {
                    u.add(Use.BUNKS);
                }
            }
            case SHIP -> {
                u.add(Use.BUNKS);
                u.add(Use.MESS);
                u.add(Use.COMMS);
                u.add(Use.MED);
            }
            case ARMY -> {
                // the guard room by the door (the radio desk and the map), then the bunks
                u.add(Use.COMMS);
                u.add(Use.BUNKS);
            }
        }
        u.add(Use.PLANT);
        return u;
    }

    private int length(Use u) {
        int big = kind == Kind.SHELTER ? 2 : 0;
        return switch (u) {
            case BUNKS -> 7 + rng.nextInt(3) + big;
            case MESS -> 7 + rng.nextInt(3) + big;
            case WASH -> 4 + rng.nextInt(2);
            case MED -> 5 + rng.nextInt(3);
            case COMMS -> 5 + rng.nextInt(2);
            case PLANT -> 7 + rng.nextInt(2);
            case PANTRY -> 5 + rng.nextInt(2);
            case SERVER -> 8 + rng.nextInt(3);
            case COMMAND -> 6 + rng.nextInt(3) + big;
            case EXEC -> 6 + rng.nextInt(3);
            default -> 6;
        };
    }

    /** Each room's floor and the paint on its walls. */
    private void palette(Room r) {
        if (ship) {
            r.lower = "ship_wall_grey";
            r.upper = "ship_wall_white";
            r.ground = "deck_steel";
            if (r.use == Use.PLANT || r.use == Use.STORAGE || r.use == Use.ARMOURY) {
                r.lower = r.upper = "ship_hold";
            }
            return;
        }
        boolean bare = kind == Kind.FOREST;
        switch (r.use) {
            case DECON, WASH -> {
                r.lower = r.upper = "bunk_tile";
            }
            case PLANT, ARMOURY -> {
                r.lower = r.upper = "bunk_plate";
            }
            case STORAGE -> {
                r.lower = r.upper = "bunk_concrete";
            }
            case SERVER, EXEC -> {
                r.lower = r.upper = "bunk_paint";
                r.ground = "office_floor";
            }
            default -> {
                r.lower = bare ? "bunk_concrete" : "bunk_dado";
                r.upper = bare ? "bunk_concrete" : "bunk_paint";
            }
        }
    }

    // ------------------------------------------------------------------ the shell

    /** Solid concrete over the whole footprint, floor to ceiling; the rooms are carved out of it. */
    private void shell() {
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                for (int y = 0; y <= TOP; y++) {
                    p.set(x, y, z, y > 0 && y < TOP && rng.nextDouble() < damp * 0.5 ? "bunk_concrete_damp" : "bunk_concrete");
                }
            }
        }
    }

    private void carve(Room r) {
        for (int x = r.x0; x <= r.x1; x++) {
            for (int z = r.z0; z <= r.z1; z++) {
                carved[x - bx0][z - bz0] = true;
                p.set(x, 0, z, r.ground.equals("bunk_floor") ? floorAt(x, z) : r.ground);
                for (int y = 1; y < TOP; y++) {
                    p.set(x, y, z, "air");
                }
                if (!ship) {
                    double g = patch(x, 3, z);
                    p.set(x, TOP, z, g > 0.72 ? "bunk_ceiling_b" : g > 0.5 ? "bunk_ceiling" : "bunk_concrete");
                }
            }
        }
    }

    /** Each room paints the face of the wall round it: the low row and the rows above. */
    private void surfaces() {
        for (Room r : rooms) {
            for (int x = r.x0 - 1; x <= r.x1 + 1; x++) {
                for (int z = r.z0 - 1; z <= r.z1 + 1; z++) {
                    if (r.in(x, z) || (x < r.x0 || x > r.x1) && (z < r.z0 || z > r.z1) || isCarved(x, z)) {
                        continue;
                    }
                    for (int y = 1; y < TOP; y++) {
                        p.set(x, y, z, finish(y == 1 ? r.lower : r.upper, x, y, z));
                    }
                }
            }
        }
    }

    /** 0..1, in patches a few blocks across, pushed up by how far gone the place is. */
    private double patch(int x, int y, int z) {
        return grime.fbm(x, z + y * 9.0, 5, 2) * 0.5 + 0.5 + (wear - 0.45) + (rng.nextDouble() - 0.5) * 0.2;
    }

    /** A wall finish worn the way the patch it falls in has worn: scuffed, stained, peeling, rotten. */
    private String finish(String m, int x, int y, int z) {
        double g = patch(x, y, z);
        if (rng.nextDouble() < damp) {
            return wet(m, y);
        }
        return switch (m) {
            case "bunk_paint" -> y == 3 && g > 0.62 && rng.nextDouble() < 0.5 ? "bunk_paint_leak"
                    : g > 0.82 ? "bunk_paint_grime" : g > 0.7 ? (rng.nextBoolean() ? "bunk_paint_stain" : "bunk_paint_flake")
                    : g > 0.48 ? "bunk_paint_b" : m;
            case "bunk_dado" -> g > 0.78 ? (damp > 0.12 ? "bunk_dado_rot" : "bunk_dado_scuff") : g > 0.62 ? "bunk_dado_scuff"
                    : g > 0.45 ? "bunk_dado_b" : m;
            case "bunk_concrete" -> g > 0.78 ? (rng.nextBoolean() ? "bunk_concrete_crack" : "bunk_concrete_damp")
                    : g > 0.5 ? "bunk_concrete_b" : m;
            case "bunk_tile" -> g > 0.55 ? "bunk_tile_dirty" : m;
            case "bunk_plate" -> g > 0.7 ? "bunk_plate_rust" : g > 0.45 ? "bunk_plate_b" : m;
            case "ship_wall_grey", "ship_wall_white" -> g > 0.5 ? m + "_b" : m;
            default -> m;
        };
    }

    private String floorAt(int x, int z) {
        double g = patch(x, 0, z);
        return g > 0.85 ? (rng.nextDouble() < 0.3 ? "bunk_floor_stain" : "bunk_floor_crack") : g > 0.66 ? "bunk_floor_dirty"
                : g > 0.45 ? "bunk_floor_b" : "bunk_floor";
    }

    /** The damp version of a wall finish, where there is one. */
    private static String wet(String m, int y) {
        return switch (m) {
            case "bunk_concrete" -> "bunk_concrete_damp";
            case "bunk_dado" -> "bunk_dado_damp";
            case "bunk_paint" -> y == 2 ? "bunk_paint_flake" : m;
            case "bunk_plate" -> "bunk_plate_rust";
            default -> m;
        };
    }

    private boolean isCarved(int x, int z) {
        return x >= bx0 && x <= bx1 && z >= bz0 && z <= bz1 && carved[x - bx0][z - bz0];
    }

    /** The blast door, the doors through the two-thick walls, and the corridor's mouth into the hall. */
    private void openings() {
        String door = ship ? "ship_door" : "bunk_door_steel";
        for (int r = 0; r < 3; r++) {
            p.set(0, 1 + r, 0, "bunk_blast_w" + r + "[facing=north]");
            p.set(1, 1 + r, 0, "bunk_blast_e" + r + "[facing=north]");
        }
        // the double door out of the decon room, its reveal behind it
        int dz = decon.z1 + 1;
        for (int x = 0; x <= 1; x++) {
            doorAt(x, dz, 2, door, x == 0 ? "left" : "right");
            reveal(x, dz + 1, corridor.ground);
        }
        for (Room r : rooms) {
            if (r.door == null || r == armoury) {
                continue;
            }
            int wx = r.door[0], wz = r.door[1], s = r.door[2];
            String d = r.use == Use.SERVER && kind == Kind.TOWER ? "bunk_door_elec" : door;
            doorAt(wx, wz, s, d, rng.nextBoolean() ? "left" : "right");
            reveal(wx + STEP[s][0], wz + STEP[s][1], r.ground);
        }
        // the corridor runs straight into the storage hall under a lintel
        for (int x = 0; x < cw; x++) {
            for (int z = corridor.z1 + 1; z <= corridor.z1 + 2; z++) {
                reveal(x, z, corridor.ground);
            }
        }
        doorAt(armoury.door[0], armoury.door[1], 2, door, "left");
        reveal(armoury.door[0], armoury.door[1] + 1, armoury.ground);
    }

    private void doorAt(int x, int z, int s, String door, String hinge) {
        String f = DIRS[s];
        p.set(x, 0, z, "bunk_floor");
        p.set(x, 1, z, door + "[facing=" + f + ",half=lower,hinge=" + hinge + ",open=false]");
        p.set(x, 2, z, door + "[facing=" + f + ",half=upper,hinge=" + hinge + ",open=false]");
        carved[x - bx0][z - bz0] = true;
    }

    private void reveal(int x, int z, String ground) {
        p.set(x, 0, z, ground);
        p.set(x, 1, z, "air");
        p.set(x, 2, z, "air");
        carved[x - bx0][z - bz0] = true;
    }

    // ------------------------------------------------------------------ the rooms

    private void dress(Room r) {
        switch (r.use) {
            case DECON -> decon(r);
            case CORRIDOR -> corridor(r);
            case BUNKS -> bunks(r);
            case MESS -> mess(r);
            case WASH -> wash(r);
            case MED -> med(r);
            case COMMS -> comms(r, false);
            case COMMAND -> comms(r, true);
            case PLANT -> plant(r);
            case PANTRY -> pantry(r);
            case SERVER -> server(r);
            case EXEC -> exec(r);
            case STORAGE -> storeHall(r);
            case ARMOURY -> armoury(r);
        }
        if (r.use != Use.CORRIDOR) {
            lights(r);
            services(r);
        }
        r.tops();
    }

    /** Through the blast door: showers down one side, hazmat suits on hooks down the other, drains. */
    private void decon(Room r) {
        for (int x = 0; x <= 1; x++) {
            p.set(x, 0, r.z0, "bunk_floor_line[axis=x]");
        }
        for (int x = r.x0 + 1; x < r.x1; x++) {
            for (int z = r.z0 + 1; z < r.z1; z++) {
                if ((x + z) % 3 == 0) {
                    p.set(x, 0, z, "bunk_floor_drain");
                }
            }
        }
        int sw = rng.nextBoolean() ? 3 : 1;
        int sx = sw == 3 ? r.x0 : r.x1;
        for (int z = r.z0 + 1; z <= r.z1 - 1; z++) {
            r.decor(sx, z, sw, 1, "bunk_shower");
        }
        int hx = sw == 3 ? r.x1 : r.x0;
        for (int z = r.z0 + 1; z <= r.z1 - 1; z++) {
            if (rng.nextDouble() < 0.7 && r.decor(hx, z, (sw + 2) & 3, 1, "bunk_hazmat")) {
                r.decor(hx, z, (sw + 2) & 3, 2, "bunk_hazmat_top");
            }
        }
        // the emergency lamps by the blast door, some still glowing on their batteries
        r.decor(-1 < r.x0 ? r.x0 : -1, r.z0, 0, 3, rng.nextDouble() < 0.6 ? "bunk_lamp_red_lit" : "bunk_lamp_red");
        r.decor(2 > r.x1 ? r.x1 : 2, r.z0, 0, 3, rng.nextDouble() < 0.4 ? "bunk_lamp_red_lit" : "bunk_lamp_red");
        r.decor(rng.nextBoolean() ? 0 : 1, r.z1, 2, 3, "bunk_st_decon");
        r.wallDecor(2, "bunk_radmeter");
        r.wallDecor(2, "bunk_phone");
    }

    /** Pipes down one wall and a duct down the other under the ceiling, lamps and cable tray down
     *  the middle, a stencilled name by every door, and arrows back to the way out. */
    private void corridor(Room r) {
        int wx = r.x0, ex = r.x1;
        boolean pipesWest = rng.nextBoolean();
        for (int z = r.z0; z <= r.z1; z++) {
            r.decor(pipesWest ? wx : ex, z, pipesWest ? 3 : 1, 3, (z - r.z0) % 7 == 3 ? "bunk_pipes_valve" : "bunk_pipes");
            if (cw >= 3) {
                int cx = r.x0 + 1;
                if ((z - r.z0) % 5 == 2) {
                    r.ceiling(cx, z, "bunk_lamp_hang");
                } else {
                    r.ceiling(cx, z, "bunk_cable_tray[axis=z]");
                }
                r.ceiling(pipesWest ? ex : wx, z, (z - r.z0) % 6 == 4 ? "bunk_duct_vent[axis=z]" : "bunk_duct[axis=z]");
            } else if ((z - r.z0) % 5 == 2) {
                r.ceiling(pipesWest ? ex : wx, z, "bunk_lamp_hang");
            }
        }
        // the name of each room beside its door
        for (Room o : rooms) {
            if (o.door == null || o == armoury) {
                continue;
            }
            String sign = sign(o.use);
            if (sign == null) {
                continue;
            }
            int s = o.door[2];  // from the corridor toward the room
            int cx = o.door[0] - STEP[s][0];
            for (int dz : rng.nextBoolean() ? new int[] {1, -1} : new int[] {-1, 1}) {
                if (r.decor(cx, o.door[1] + dz, s, 2, sign)) {
                    break;
                }
            }
            if (o.use == Use.PLANT && rng.nextBoolean()) {
                r.decor(cx, o.door[1] + 2, s, 2, "bunk_st_nosmoking");
            }
        }
        // arrows back toward the blast door, every so often
        for (int z = r.z0 + 6; z < r.z1; z += 9) {
            boolean west = rng.nextBoolean();
            r.decor(west ? wx : ex, z, west ? 3 : 1, 2, west ? "bunk_st_exit_r" : "bunk_st_exit_l");
        }
        if (kind == Kind.TOWER) {
            r.decor(wx, r.z0 + 1, 3, 2, "bunk_st_b4");
        } else if (kind == Kind.SHELTER) {
            r.decor(ex, r.z0 + 2, 1, 2, "bunk_shelter_a");
            r.decor(ex, r.z0 + 3, 1, 2, "bunk_shelter_b");
        }
        r.wallDecor(2, "bunk_fusebox");
        r.wallDecor(1, "bunk_extinguisher");
        if (r.d() > 20) {
            r.wallDecor(1, "bunk_extinguisher");
            r.wallDecor(2, "bunk_phone");
        }
    }

    /** Steel bunks two high with a footlocker at each foot, and wall lockers between them. */
    private void bunks(Room r) {
        int beds = 0;
        for (int s0 = rng.nextInt(4), k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            if (s == r.toCorridor) {
                continue;
            }
            for (int[] c : r.along(s)) {
                if (beds > 0 && (beds % 2 == 0) && rng.nextDouble() < 0.6
                        && r.place(c[0], c[1], s, "bunk_locker", "bunk_locker_top")) {
                    beds++;
                    continue;
                }
                boolean top = rng.nextDouble() < 0.85;
                String[] head = top ? new String[] {"bunk_bed_head", "bunk_bed_head_top"} : new String[] {"bunk_bed_head"};
                String[] foot = top ? new String[] {"bunk_bed_foot", "bunk_bed_foot_top"} : new String[] {"bunk_bed_foot"};
                boolean locker = rng.nextDouble() < 0.6;
                if (locker ? r.run(c[0], c[1], s, head, foot, new String[] {"bunk_footlocker"}) : r.run(c[0], c[1], s, head, foot)) {
                    beds++;
                }
            }
        }
        // a double row head to head down the middle when the room is big enough
        boolean acrossX = r.d() >= r.w();
        int zc = (r.z0 + r.z1) / 2, xc = (r.x0 + r.x1) / 2;
        int from = acrossX ? r.x0 + 3 : r.z0 + 3, to = acrossX ? r.x1 - 3 : r.z1 - 3;
        for (int i = from, n = 0; i <= to; i++, n++) {
            if (n % 3 == 2) {
                continue;  // a gap to get between them
            }
            String[] head = {"bunk_bed_head", "bunk_bed_head_top"}, foot = {"bunk_bed_foot", "bunk_bed_foot_top"};
            if (acrossX) {
                r.runFree(i, zc, 0, head, foot);
                r.runFree(i, zc + 1, 2, head, foot);
            } else {
                r.runFree(xc, i, 3, head, foot);
                r.runFree(xc + 1, i, 1, head, foot);
            }
        }
        for (int i = 0; i < 2; i++) {
            r.against("bunk_locker", "bunk_locker_top");
        }
        r.wallDecor(2, "bunk_lamp");
        r.scatter("home_clothes_pile", rng.nextInt(3));
    }

    /** The galley along one wall, mess tables down the room with chairs at them. */
    private void mess(Room r) {
        r.row("bunk_range", "bunk_counter", "bunk_sink", "bunk_counter", "bunk_range");
        r.row("bunk_counter", "bunk_sink", "bunk_counter");
        r.against("bunk_crate_ration");
        r.against("bunk_tins");
        r.against(ship ? "bunk_counter" : "bunk_drum_water");
        // tables along the room's length, a chair either side of each
        boolean alongZ = r.d() >= r.w();
        int span = alongZ ? r.d() : r.w(), across = alongZ ? r.w() : r.d();
        int n = Math.max(2, span - 4);
        for (int line = 2; line < across - 2; line += 3) {
            for (int i = 2; i < 2 + n; i++) {
                int x = alongZ ? r.x0 + line : r.x0 + i, z = alongZ ? r.z0 + i : r.z0 + line;
                r.table(x, z, alongZ);
            }
        }
        r.wallDecor(2, "bunk_clock");
        r.wallDecor(1, "bunk_extinguisher");
    }

    private void wash(Room r) {
        int s = r.otherWall();
        for (int[] c : r.along(s)) {
            r.decor(c[0], c[1], s, 1, "bunk_shower");
        }
        r.row("bunk_sink", "bunk_sink", "bunk_sink");
        for (int i = 0; i < 3; i++) {
            r.against("home_toilet");
        }
        for (int x = r.x0; x <= r.x1; x++) {
            for (int z = r.z0; z <= r.z1; z++) {
                if (r.at(x, z) != TAKEN && rng.nextDouble() < 0.2) {
                    p.set(x, 0, z, "bunk_floor_drain");
                }
            }
        }
    }

    /** Sick bay: beds with a drip stand by each, the cabinets, a desk; sometimes the ones who didn't make it. */
    private void med(Room r) {
        for (int s0 = rng.nextInt(4), k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            if (s == r.toCorridor) {
                continue;
            }
            int i = 0;
            for (int[] c : r.along(s)) {
                if (i++ % 2 == 1) {
                    r.place(c[0], c[1], s, "bunk_iv_stand");
                } else {
                    r.run(c[0], c[1], s, new String[] {"bunk_medbed_head"}, new String[] {"bunk_medbed_foot"});
                }
            }
        }
        r.against("bunk_medcab", "bunk_medcab");
        r.against("bunk_medcab");
        int[] d = r.against("bunk_desk");
        if (d != null) {
            r.chair(d);
        }
        r.wallDecor(2, "bunk_first_aid");
        if (rng.nextDouble() < 0.4) {
            r.bodyBag();
        }
    }

    /** Radio desks and their chairs along a wall, the map table, maps pinned up, the phone. */
    private void comms(Room r, boolean command) {
        int[] desk = r.row("bunk_radio_desk", "bunk_radio_desk", rng.nextBoolean() ? "bunk_desk" : "bunk_radio_desk");
        if (desk != null) {
            int[] sv = side(desk[2]);
            for (int i = 0; i < 3; i++) {
                r.chair(new int[] {desk[0] + sv[0] * i, desk[1] + sv[1] * i, desk[2]});
            }
        }
        int mw = command ? 3 : 2;
        r.mapTable(mw, 2);
        for (int i = 0, n = command ? 3 : 2; i < n; i++) {
            r.wallDecor(2, "bunk_map_wall");
        }
        r.wallDecor(2, "bunk_phone");
        r.wallDecor(3, "bunk_clock");
        r.wallDecor(2, "bunk_fusebox");
        if (command) {
            r.against("filing_cabinet");
            r.against("filing_cabinet");
        }
    }

    /** The generator set, the air filter, fuel tanks, the switchboard and fan; grating in front. */
    private void plant(Room r) {
        // the generator set stands out on the floor with room to work round it
        if (!r.pair("bunk_gen_engine", "bunk_gen_alternator") && r.row("bunk_gen_engine", "bunk_gen_alternator") == null) {
            r.against("bunk_gen_engine");
        }
        if (r.w() * r.d() > 60 && rng.nextBoolean()) {
            r.pair("bunk_gen_engine", "bunk_gen_alternator");
        }
        r.against("bunk_filter", "bunk_filter_top");
        r.against("bunk_filter", "bunk_filter_top");
        r.rowStack(new String[] {"bunk_tank", "bunk_tank_top"}, new String[] {"bunk_tank", "bunk_tank_top"},
                new String[] {"bunk_tank", "bunk_tank_top"});
        r.rowStack(new String[] {"bunk_switchboard", "bunk_switchboard_top"}, new String[] {"bunk_switchboard", "bunk_switchboard_top"});
        r.against("bunk_jerry_cans");
        r.against("bunk_jerry_cans");
        r.wallDecor(2, "bunk_fan");
        r.wallDecor(3, "bunk_fusebox");
        for (int x = r.x0; x <= r.x1; x++) {
            for (int z = r.z0; z <= r.z1; z++) {
                if (r.at(x, z) == FRONT) {
                    p.set(x, 0, z, "bunk_walkway");
                }
            }
        }
        for (int s = 0; s < 4; s++) {
            if (s == r.toCorridor) {
                continue;
            }
            for (int[] c : r.along(s)) {
                r.decor(c[0], c[1], s, 3, "bunk_pipes");
                if (r.at(c[0], c[1]) != TAKEN && r.at(c[0], c[1]) != WAY) {
                    r.decor(c[0], c[1], s, 1, "bunk_pipes_low");
                }
            }
        }
    }

    private void pantry(Room r) {
        for (int i = 0; i < r.d() + r.w(); i++) {
            String a = rack(), b = rack();
            if (r.against(a, b) == null) {
                break;
            }
        }
        r.against("bunk_drum_water");
        r.against("bunk_crate_ration");
    }

    /** Rows of server cabinets with aisles between, the admin's desk by the door. */
    private void server(Room r) {
        int[] d = r.against("bunk_desk");
        if (d != null) {
            r.chair(d);
        }
        // the rows run away from the door's wall, so you look down an aisle as you come in
        boolean alongZ = r.toCorridor == 0 || r.toCorridor == 2;
        int across = alongZ ? r.w() : r.d(), span = alongZ ? r.d() : r.w();
        boolean doorAtStart = r.toCorridor == 0 || r.toCorridor == 3;
        for (int line = 1, k = 0; line < across - 1; line += 2, k++) {
            int fs = alongZ ? (k % 2 == 0 ? 3 : 1) : (k % 2 == 0 ? 0 : 2);
            for (int i = doorAtStart ? 2 : 1; i < span - (doorAtStart ? 1 : 2); i++) {
                int x = alongZ ? r.x0 + line : r.x0 + i, z = alongZ ? r.z0 + i : r.z0 + line;
                r.free(x, z, fs, "bunk_server", "bunk_server_top");
            }
        }
        r.wallDecor(2, "bunk_fusebox");
    }

    /** The executives' suite: a good desk, a sofa and armchair, books, a plant gone brown. */
    private void exec(Room r) {
        for (String desk : new String[] {"home_desk_pc", "home_desk"}) {
            int[] d = r.against(desk);
            if (d != null) {
                r.chair(d, "office_chair");
            }
        }
        r.row("home_sofa_brown_l", "home_sofa_brown_m", "home_sofa_brown_r");
        r.against("home_armchair_brown");
        r.against("home_armchair_brown");
        r.row("filing_cabinet", "filing_cabinet");
        r.rowStack(new String[] {"home_bookcase", "home_bookcase_top"}, new String[] {"home_bookcase", "home_bookcase_top"});
        r.against("home_floor_lamp");
        r.against("home_plant_dead");
        r.against("home_plant_dead");
        int[] t = r.middle("home_coffee_table");
        if (t != null) {
            r.rug(t[0] - 1, t[1] - 1, t[0] + 1, t[1] + 1);
        }
        r.wallDecor(2, "bunk_clock");
        r.wallDecor(2, "bunk_phone");
    }

    /** Pallet racking two high round the walls and in double rows down the hall, crates and drums. */
    private void storeHall(Room r) {
        // freestanding double rows, back to back, with aisles either side
        for (int x = r.x0 + 2; x + 1 <= r.x1 - 2; x += 4) {
            if (x <= cw && x + 1 >= -1) {
                continue;  // keep the line from the corridor to the armoury door clear
            }
            for (int z = r.z0 + 2; z <= r.z1 - 2; z++) {
                r.free(x, z, 3, rack(), rack());
                r.free(x + 1, z, 1, rack(), rack());
            }
        }
        for (int i = 0; i < 2 * (r.w() + r.d()); i++) {
            r.against(rack(), rack());
        }
        for (int i = 0; i < 4; i++) {
            r.against(rng.nextBoolean() ? "bunk_crate_ration" : "bunk_tins");
            r.against("bunk_drum_water");
        }
        r.against("bunk_jerry_cans");
        for (int x = r.x0 + 2; x < r.x1 - 1; x += 5) {
            for (int z = r.z0 + 2; z < r.z1 - 1; z += 4) {
                r.ceiling(x, z, "bunk_lamp_hang");
            }
        }
        int mid = cw / 2;
        r.decor(mid + 1, r.z1, 2, 3, "bunk_st_armoury");
        r.decor(cw, r.z0, 0, 3, "bunk_st_stores");
    }

    /** A counter just inside the door, then the weapons cage across the room: gun racks on the
     *  walls behind it, ammo and gun crates under them. */
    private void armoury(Room r) {
        int cz = r.z0 + 2;
        int mid = cw / 2;
        for (int x = r.x0; x <= r.x1; x++) {
            if (x == mid) {
                p.set(x, 1, cz, "bunk_door_cage[facing=south,half=lower,hinge=left,open=false]");
                p.set(x, 2, cz, "bunk_door_cage[facing=south,half=upper,hinge=left,open=false]");
                p.set(x, 3, cz, "bunk_cage[axis=x]");
                r.mark(x, cz, WAY);
            } else {
                for (int y = 1; y < TOP; y++) {
                    p.set(x, y, cz, "bunk_cage[axis=x]");
                }
                r.mark(x, cz, TAKEN);
            }
            r.used(x, cz);
        }
        int[] d = r.against("bunk_desk");
        if (d != null) {
            r.chair(d);
        }
        r.against("bunk_footlocker");
        // behind the cage: the racks and the crates
        for (int s = 1; s < 4; s++) {
            for (int[] c : r.along(s)) {
                if (c[1] <= cz) {
                    continue;
                }
                String crate = rng.nextDouble() < 0.6 ? "bunk_crate_ammo" : "bunk_crate_gun";
                if (rng.nextDouble() < 0.75) {
                    r.place(c[0], c[1], s, crate);
                }
                double v = rng.nextDouble();
                if (v < 0.7) {
                    r.decor(c[0], c[1], s, 2, v < 0.55 ? "bunk_gun_rack" : "bunk_pegboard");
                }
            }
        }
        r.ceiling(mid, r.z0, "bunk_lamp_hang");
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private String rack() {
        double v = rng.nextDouble();
        return v < 0.4 ? "bunk_rack_boxes" : v < 0.7 ? "bunk_rack_crates" : "bunk_rack_drums";
    }

    private static String sign(Use u) {
        return switch (u) {
            case BUNKS -> "bunk_st_bunks";
            case MESS -> "bunk_st_mess";
            case MED -> "bunk_st_medical";
            case COMMS, COMMAND -> "bunk_st_comms";
            case PLANT -> "bunk_st_generator";
            case PANTRY -> "bunk_st_stores";
            case WASH -> "bunk_st_water";
            default -> null;
        };
    }

    // ------------------------------------------------------------------ wear and tear

    /**
     * The months since it was sealed: water through the ceiling and down the walls into puddles,
     * cobwebs in the corners, grit along the skirting, chairs pushed back and lockers left open,
     * and what the people down here left behind: the days counted on a wall, a warning painted on
     * another, a trail of glow sticks when the lights went.
     */
    private void weather(Room r) {
        int area = r.w() * r.d();
        int leaks = (int) Math.round(area * wear * 0.025 + rng.nextDouble());
        for (int i = 0; i < leaks; i++) {
            int[] c = r.wallSpot(3);
            if (c == null) {
                break;
            }
            r.decor(c[0], c[1], c[2], 3, "bunk_leak");
            if (rng.nextBoolean()) {
                r.decor(c[0], c[1], c[2], 2, "bunk_leak");
            }
            if (rng.nextDouble() < 0.6) {
                r.floor(c[0], c[1], "bunk_puddle");
            }
        }
        for (int[] k : new int[][] {{r.x0, r.z0, 0}, {r.x1, r.z0, 1}, {r.x1, r.z1, 2}, {r.x0, r.z1, 3}}) {
            if (rng.nextDouble() < wear * 0.9) {
                r.decor(k[0], k[1], rng.nextBoolean() ? k[2] : (k[2] + 3) & 3, 3, "bunk_cobweb");
            }
        }
        for (int x = r.x0; x <= r.x1; x++) {
            for (int z = r.z0; z <= r.z1; z++) {
                boolean edge = x == r.x0 || x == r.x1 || z == r.z0 || z == r.z1;
                if (rng.nextDouble() < (edge ? 0.3 : 0.06) * wear) {
                    r.floor(x, z, "bunk_grit");
                }
            }
        }
        knockAbout(r, state == State.SEALED ? 0.06 : 0.25, state == State.SEALED ? 0.12 : 0.4);
        switch (r.use) {
            case BUNKS -> {
                if (rng.nextDouble() < 0.5) {
                    wallMark(r, 2, "bunk_tally");
                }
            }
            case MESS -> {
                int[] c = r.wallSpot(3);
                if (c != null && rng.nextDouble() < 0.4) {
                    r.decor(c[0], c[1], c[2], 3, "bunk_soot");
                    r.decor(c[0], c[1], c[2], 2, "bunk_soot");
                }
            }
            case WASH -> {
                if (rng.nextDouble() < 0.35) {
                    wallMark(r, 2, "bunk_scrawl_c");
                }
            }
            case CORRIDOR -> corridorWear(r);
            default -> { }
        }
    }

    /** A decal painted flat on a free stretch of wall at row y. */
    private void wallMark(Room r, int y, String block) {
        int[] c = r.wallSpot(y);
        if (c != null) {
            r.decor(c[0], c[1], c[2], y, block);
        }
    }

    /** Chairs pushed back and knocked over, lockers burst open (still with whatever is in them). */
    private void knockAbout(Room r, double chairs, double lockers) {
        for (int x = r.x0; x <= r.x1; x++) {
            for (int z = r.z0; z <= r.z1; z++) {
                if ("bunk_chair".equals(r.what[x - r.x0][z - r.z0])) {
                    double d = rng.nextDouble();
                    if (d < chairs) {
                        p.set(x, 1, z, "bunk_chair_down" + face(rng.nextInt(4)));
                    } else if (d < chairs + 0.25) {
                        p.set(x, 1, z, "bunk_chair" + face(rng.nextInt(4)));
                    }
                }
                String b = p.get(x, 1, z);
                if (b != null && b.startsWith("bunk_locker[") && rng.nextDouble() < lockers) {
                    p.set(x, 1, z, "bunk_locker_open" + b.substring("bunk_locker".length()));
                }
            }
        }
    }

    /** Boot prints up and down the corridor, red emergency lamps on their last batteries, a
     *  warning painted by the sick bay door, and the glow sticks someone dropped to mark the way. */
    private void corridorWear(Room r) {
        int mid = r.x0 + r.w() / 2;
        for (int z = r.z0; z <= r.z1; z++) {
            if (rng.nextDouble() < 0.3 * wear) {
                r.floor(mid + (rng.nextBoolean() ? 0 : -1), z, "bunk_bootprints" + face(rng.nextBoolean() ? 0 : 2));
            }
        }
        boolean west = rng.nextBoolean();
        for (int z = r.z0 + 4 + rng.nextInt(4); z <= r.z1; z += 10 + rng.nextInt(5)) {
            boolean lit = rng.nextDouble() < (state == State.SEALED ? 0.5 : 0.25);
            r.decor(west ? r.x0 : r.x1, z, west ? 3 : 1, 3, lit ? "bunk_lamp_red_lit" : "bunk_lamp_red");
        }
        for (Room o : rooms) {
            if (o.use == Use.MED && o.door != null && rng.nextDouble() < 0.4) {
                int s = o.door[2], wx = o.door[0] - STEP[s][0], wz = o.door[1] - STEP[s][1];
                int[] sv = side((s + 2) & 3);
                r.decor(wx + sv[0], wz + sv[1], s, 2, "bunk_scrawl_b");
            }
        }
        if (state != State.BREACHED && rng.nextDouble() < 0.7) {
            int n = 3 + rng.nextInt(5);
            for (int i = 0; i < n; i++) {
                int z = r.z0 + (int) ((i + rng.nextDouble()) * r.d() / n);
                boolean lit = rng.nextDouble() < (state == State.SEALED ? 0.6 : 0.25);
                r.floor(r.x0 + rng.nextInt(r.w()), Math.min(r.z1, z), lit ? "bunk_glowstick" : "bunk_glowstick_dead");
            }
        }
    }

    /**
     * The dead got in. The strip lights hang off their cables or lie smashed on the floor, and the
     * ones still up stutter on a dying circuit; cables and duct have come down; there's blood
     * dragged across the floors and smeared on the walls, bullet holes and casings where someone
     * fought. The furniture is knocked about but all still there, and still worth searching.
     */
    private void breach(Room r) {
        for (int[] l : r.lamps) {
            int x = l[0], z = l[1];
            String ax = l[2] == 1 ? "x" : "z", lit = rng.nextBoolean() ? "true" : "false";
            double d = rng.nextDouble();
            if (l[3] == 0) {
                if (d < 0.3) {
                    p.set(x, 3, z, "bunk_tube_dangle[axis=" + ax + ",lit=" + lit + "]");
                } else if (d < 0.45) {
                    p.set(x, 3, z, "air");
                    r.floor(x, z, "bunk_tube_fallen[axis=" + ax + "]");
                } else if (d < 0.75) {
                    p.set(x, 3, z, "bunk_tube_flicker[axis=" + ax + ",lit=" + lit + "]");
                }
            } else if (d < 0.4) {
                p.set(x, 3, z, "bunk_lamp_dangle[lit=" + lit + "]");
            } else if (d < 0.55) {
                p.set(x, 3, z, "air");
                r.floor(x, z, "glass_shards");
            }
        }
        for (int i = rng.nextInt(3); i > 0; i--) {
            int x = r.x0 + rng.nextInt(r.w()), z = r.z0 + rng.nextInt(r.d());
            r.ceiling(x, z, "bunk_cable_loose");
        }
        if (r.use == Use.CORRIDOR) {
            int downs = 1 + rng.nextInt(2);
            for (int z = r.z0 + 2; z <= r.z1 && downs > 0; z++) {
                for (int x : new int[] {r.x0, r.x1}) {
                    String b = p.get(x, 3, z);
                    if (downs > 0 && b != null && b.startsWith("bunk_duct[") && rng.nextDouble() < 0.08
                            && r.floor(x, z, "bunk_duct_fallen[axis=z]")) {
                        p.set(x, 3, z, "air");
                        downs--;
                    }
                }
            }
        }
        // blood: pooled, dragged, a hand print on the wall
        int[] c = r.clearFloor();
        if (c != null && rng.nextDouble() < 0.7) {
            r.floor(c[0], c[1], rng.nextBoolean() ? "home_blood_pool" : "blood_splat");
        }
        if (rng.nextDouble() < 0.5 && (c = r.clearFloor()) != null) {
            int fs = rng.nextInt(4);
            for (int i = 0, x = c[0], z = c[1]; i < 2 + rng.nextInt(3); i++, x += STEP[fs][0], z += STEP[fs][1]) {
                if (!r.floor(x, z, "home_blood_drag" + face(fs))) {
                    break;
                }
            }
        }
        if (rng.nextDouble() < 0.45) {
            int[] w = r.wallSpot(2);
            if (w != null && !r.used[w[0] - r.x0][2][w[1] - r.z0]) {
                p.set(w[0], 2, w[1], "home_blood_hand" + face(w[2]));
                r.used[w[0] - r.x0][2][w[1] - r.z0] = true;
            }
        }
        if (rng.nextDouble() < (r.use == Use.CORRIDOR || r.use == Use.STORAGE ? 0.8 : 0.3)) {
            int[] w = r.wallSpot(2);
            if (w != null) {
                r.decor(w[0], w[1], w[2], 2, "bunk_bullets");
                r.floor(w[0], w[1], "bunk_casings");
            }
        }
        r.scatter("debris_chunk", rng.nextInt(3));
        r.scatter("glass_shards", rng.nextInt(2));
        r.scatter("paper", 1 + rng.nextInt(3));
        knockAbout(r, 0.5, 0.6);
    }

    /** The blast door forced: one leaf torn out of its frame, scorched round the cut. */
    private void forced() {
        for (int y = 1; y <= 3; y++) {
            p.set(0, y, 0, "air");
        }
        decon.floor(decon.x0, decon.z0, "debris_chunk" + face(rng.nextInt(4)));
        decon.floor(0, decon.z0, "bunk_scorch" + face(0));
        decon.floor(0, decon.z0 + 2, "home_blood_drag" + face(2));
    }

    /**
     * The blast door pried with a crowbar by whoever got here first, swung in on its hinges and left
     * standing open against the decon room's wall, as barricade/CrowbarItem leaves a sealed one.
     */
    private void pried() {
        for (int y = 1; y <= 3; y++) {
            p.set(0, y, 0, "air");
            p.set(1, y, 0, "air");
            p.set(1, y, 1, "bunk_blast_e" + (y - 1) + "[facing=west]");
            p.set(1, y, 2, "bunk_blast_w" + (y - 1) + "[facing=west]");
        }
        decon.floor(0, decon.z0, "debris_chunk" + face(rng.nextInt(4)));
    }

    /** Strip lights under the ceiling of a long room, a hanging lamp in a small one; all dead. */
    private void lights(Room r) {
        if (r.use == Use.STORAGE || r.use == Use.ARMOURY) {
            return;
        }
        boolean alongZ = r.d() >= r.w();
        int span = alongZ ? r.d() : r.w();
        if (span >= 6) {
            int line = alongZ ? (r.x0 + r.x1) / 2 : (r.z0 + r.z1) / 2;
            for (int i = 1; i < span - 1; i++) {
                if (i % 3 == 0) {
                    continue;
                }
                int x = alongZ ? line : r.x0 + i, z = alongZ ? r.z0 + i : line;
                r.ceiling(x, z, "bunk_tube[axis=" + (alongZ ? "z" : "x") + "]");
            }
        } else {
            r.ceiling((r.x0 + r.x1) / 2, (r.z0 + r.z1) / 2, "bunk_lamp_hang");
        }
    }

    /** A pipe run along the corridor wall under the ceiling, and a vent. */
    private void services(Room r) {
        if (r.toCorridor >= 0 && r.use != Use.PLANT) {
            for (int[] c : r.along(r.toCorridor)) {
                r.decor(c[0], c[1], r.toCorridor, 3, rng.nextDouble() < 0.12 ? "bunk_pipes_valve" : "bunk_pipes");
            }
        }
        r.wallDecor(3, "bunk_vent");
    }

    // ------------------------------------------------------------------ one room's floor plan

    private final class Room {
        final Use use;
        final int x0, z0, x1, z1;
        /** What stands in each cell. */
        final byte[][] at;
        /** Which rows (1..3) of each cell are already used, by furniture or by things on the wall. */
        final boolean[][][] used;
        /** The one-high piece standing in each cell (its name, no state), for what goes on top of it. */
        final String[][] what;
        final int[][] facing;
        final List<int[]> ways = new ArrayList<>();
        /** The light fittings hung so far: {x, z, 1 if along x, 0 strip light or 1 caged lamp}. */
        final List<int[]> lamps = new ArrayList<>();
        String lower = "bunk_dado", upper = "bunk_paint", ground = "bunk_floor";
        /** The wall side (0..3) the corridor is behind, for a room off it; -1 otherwise. */
        int toCorridor = -1;
        /** {wall x, wall z, side from the corridor toward the room}: the door into this room. */
        int[] door;

        Room(Use use, int x0, int z0, int x1, int z1) {
            this.use = use;
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            at = new byte[x1 - x0 + 1][z1 - z0 + 1];
            used = new boolean[x1 - x0 + 1][TOP][z1 - z0 + 1];
            what = new String[x1 - x0 + 1][z1 - z0 + 1];
            facing = new int[x1 - x0 + 1][z1 - z0 + 1];
        }

        int w() {
            return x1 - x0 + 1;
        }

        int d() {
            return z1 - z0 + 1;
        }

        boolean in(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        byte at(int x, int z) {
            return at[x - x0][z - z0];
        }

        void way(int x, int z) {
            ways.add(new int[] {x, z});
            at[x - x0][z - z0] = WAY;
        }

        void mark(int x, int z, byte what) {
            if (in(x, z) && (what == TAKEN || at[x - x0][z - z0] != TAKEN && at[x - x0][z - z0] != WAY)) {
                at[x - x0][z - z0] = what;
            }
        }

        void used(int x, int z) {
            for (int y = 1; y < TOP; y++) {
                used[x - x0][y][z - z0] = true;
            }
        }

        /** The cells along wall side s, in order along it. */
        List<int[]> along(int s) {
            List<int[]> out = new ArrayList<>();
            if (s == 0 || s == 2) {
                int z = s == 0 ? z0 : z1;
                for (int x = x0; x <= x1; x++) {
                    out.add(new int[] {x, z});
                }
            } else {
                int x = s == 3 ? x0 : x1;
                for (int z = z0; z <= z1; z++) {
                    out.add(new int[] {x, z});
                }
            }
            return out;
        }

        /** A wall that isn't the corridor's, for a row of showers and the like. */
        int otherWall() {
            int s = rng.nextInt(4);
            return s == toCorridor ? (s + 2) & 3 : s;
        }

        /** Is the wall beside (x, z) on side s solid at row y (not a doorway there)? */
        boolean solid(int x, int z, int s, int y) {
            int wx = x + STEP[s][0], wz = z + STEP[s][1];
            if (in(wx, wz)) {
                return false;
            }
            return y >= 3 || !isCarved(wx, wz);
        }

        /** A thing on the wall at row y, its back to side s; it doesn't stand in anyone's way. */
        boolean decor(int x, int z, int s, int y, String block) {
            if (!in(x, z) || used[x - x0][y][z - z0] || !solid(x, z, s, y)) {
                return false;
            }
            p.set(x, y, z, faced(block, (s + 2) & 3));
            used[x - x0][y][z - z0] = true;
            return true;
        }

        /** A thing on any free stretch of wall at row y. */
        boolean wallDecor(int y, String block) {
            List<int[]> spots = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                for (int[] c : along(s)) {
                    if (!used[c[0] - x0][y][c[1] - z0] && solid(c[0], c[1], s, y) && (y > 1 || at(c[0], c[1]) == FREE)) {
                        spots.add(new int[] {c[0], c[1], s});
                    }
                }
            }
            if (spots.isEmpty()) {
                return false;
            }
            int[] c = spots.get(rng.nextInt(spots.size()));
            return decor(c[0], c[1], c[2], y, block);
        }

        /** A thing hung from the ceiling, in the top row. */
        boolean ceiling(int x, int z, String block) {
            if (!in(x, z) || used[x - x0][3][z - z0]) {
                return false;
            }
            p.set(x, 3, z, block);
            used[x - x0][3][z - z0] = true;
            if (block.startsWith("bunk_tube") || block.startsWith("bunk_lamp_hang")) {
                lamps.add(new int[] {x, z, block.contains("axis=x") ? 1 : 0, block.startsWith("bunk_tube") ? 0 : 1});
            }
            return true;
        }

        /** Something lying on the floor of (x, z), if nothing stands there. */
        boolean floor(int x, int z, String block) {
            if (!in(x, z) || at(x, z) == TAKEN || used[x - x0][1][z - z0]) {
                return false;
            }
            p.set(x, 1, z, block);
            used[x - x0][1][z - z0] = true;
            return true;
        }

        /** A free stretch of wall at row y: {x, z, side}, or null. */
        int[] wallSpot(int y) {
            List<int[]> spots = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                for (int[] c : along(s)) {
                    if (!used[c[0] - x0][y][c[1] - z0] && solid(c[0], c[1], s, y) && (y > 1 || at(c[0], c[1]) == FREE)) {
                        spots.add(new int[] {c[0], c[1], s});
                    }
                }
            }
            return spots.isEmpty() ? null : spots.get(rng.nextInt(spots.size()));
        }

        /** A random cell whose floor is clear: {x, z}, or null. */
        int[] clearFloor() {
            for (int tries = 0; tries < 20; tries++) {
                int x = x0 + rng.nextInt(w()), z = z0 + rng.nextInt(d());
                if (at(x, z) != TAKEN && !used[x - x0][1][z - z0]) {
                    return new int[] {x, z};
                }
            }
            return null;
        }

        /**
         * Stands a piece at (x, z) with its back to wall side s, if the cell in front of it is
         * free and every free cell can still be reached from the door.
         */
        boolean place(int x, int z, int s, String... stack) {
            int fx = x - STEP[s][0], fz = z - STEP[s][1];
            if (!in(x, z) || in(x + STEP[s][0], z + STEP[s][1]) || at(x, z) != FREE || !in(fx, fz) || at(fx, fz) == TAKEN
                    || !rowsFree(x, z, stack.length)) {
                return false;
            }
            at[x - x0][z - z0] = TAKEN;
            if (!connected()) {
                at[x - x0][z - z0] = FREE;
                return false;
            }
            put(x, z, (s + 2) & 3, stack);
            mark(fx, fz, FRONT);
            return true;
        }

        /** A piece standing out in the room, facing fs, wherever it doesn't cut the room in two. */
        boolean free(int x, int z, int fs, String... stack) {
            if (!in(x, z) || at(x, z) != FREE || !rowsFree(x, z, stack.length)) {
                return false;
            }
            at[x - x0][z - z0] = TAKEN;
            if (!connected()) {
                at[x - x0][z - z0] = FREE;
                return false;
            }
            put(x, z, fs, stack);
            return true;
        }

        /** Pieces in a line straight out from wall side s (a bed's head and foot, a locker at its foot). */
        boolean run(int x, int z, int s, String[]... cells) {
            int n = cells.length;
            for (int i = 0; i < n; i++) {
                int cx = x - STEP[s][0] * i, cz = z - STEP[s][1] * i;
                if (!in(cx, cz) || at(cx, cz) != FREE || !rowsFree(cx, cz, cells[i].length)) {
                    return false;
                }
            }
            if (in(x + STEP[s][0], z + STEP[s][1])) {
                return false;
            }
            for (int i = 0; i < n; i++) {
                at[x - STEP[s][0] * i - x0][z - STEP[s][1] * i - z0] = TAKEN;
            }
            if (!connected()) {
                for (int i = 0; i < n; i++) {
                    at[x - STEP[s][0] * i - x0][z - STEP[s][1] * i - z0] = FREE;
                }
                return false;
            }
            for (int i = 0; i < n; i++) {
                put(x - STEP[s][0] * i, z - STEP[s][1] * i, (s + 2) & 3, cells[i]);
            }
            return true;
        }

        /** Pieces in a line out in the room from (x, z) the way they face (a bed's head, then its foot). */
        boolean runFree(int x, int z, int fs, String[]... cells) {
            int n = cells.length;
            for (int i = 0; i < n; i++) {
                int cx = x + STEP[fs][0] * i, cz = z + STEP[fs][1] * i;
                if (!in(cx, cz) || at(cx, cz) != FREE || !rowsFree(cx, cz, cells[i].length)) {
                    return false;
                }
            }
            for (int i = 0; i < n; i++) {
                at[x + STEP[fs][0] * i - x0][z + STEP[fs][1] * i - z0] = TAKEN;
            }
            if (!connected()) {
                for (int i = 0; i < n; i++) {
                    at[x + STEP[fs][0] * i - x0][z + STEP[fs][1] * i - z0] = FREE;
                }
                return false;
            }
            for (int i = 0; i < n; i++) {
                put(x + STEP[fs][0] * i, z + STEP[fs][1] * i, fs, cells[i]);
            }
            return true;
        }

        /** Two pieces side by side out in the room, a cell clear of the walls all round. */
        boolean pair(String a, String b) {
            for (int tries = 0; tries < 40; tries++) {
                int fs = rng.nextInt(4);
                int[] sv = side(fs);
                int x = x0 + 2 + rng.nextInt(Math.max(1, w() - 4)), z = z0 + 2 + rng.nextInt(Math.max(1, d() - 4));
                int bx = x + sv[0], bz = z + sv[1];
                if (!in(bx, bz) || bx < x0 + 1 || bx > x1 - 1 || bz < z0 + 1 || bz > z1 - 1
                        || at(x, z) != FREE || at(bx, bz) != FREE) {
                    continue;
                }
                at[x - x0][z - z0] = TAKEN;
                at[bx - x0][bz - z0] = TAKEN;
                if (!connected()) {
                    at[x - x0][z - z0] = FREE;
                    at[bx - x0][bz - z0] = FREE;
                    continue;
                }
                put(x, z, fs, new String[] {a});
                put(bx, bz, fs, new String[] {b});
                for (int[] c : new int[][] {{x, z}, {bx, bz}}) {
                    for (int[] st : STEP) {
                        mark(c[0] + st[0], c[1] + st[1], FRONT);
                    }
                }
                return true;
            }
            return false;
        }

        /** A piece against any free stretch of wall; {x, z, facing} or null. */
        int[] against(String... stack) {
            List<int[]> spots = new ArrayList<>();
            for (int s = 0; s < 4; s++) {
                for (int[] c : along(s)) {
                    if (at(c[0], c[1]) == FREE) {
                        spots.add(new int[] {c[0], c[1], s});
                    }
                }
            }
            while (!spots.isEmpty()) {
                int[] c = spots.remove(rng.nextInt(spots.size()));
                if (place(c[0], c[1], c[2], stack)) {
                    return new int[] {c[0], c[1], (c[2] + 2) & 3};
                }
            }
            return null;
        }

        /** Single pieces side by side along one wall, left to right as you face them; {x, z, facing} of the first. */
        int[] row(String... pieces) {
            String[][] stacks = new String[pieces.length][];
            for (int i = 0; i < pieces.length; i++) {
                stacks[i] = new String[] {pieces[i]};
            }
            return rowStack(stacks);
        }

        int[] rowStack(String[]... pieces) {
            int s0 = rng.nextInt(4);
            for (int k = 0; k < 4; k++) {
                int s = (s0 + k) & 3, fs = (s + 2) & 3;
                int[] sv = side(fs);
                List<int[]> starts = new ArrayList<>();
                for (int[] c : along(s)) {
                    boolean fits = true;
                    for (int i = 0; i < pieces.length && fits; i++) {
                        int cx = c[0] + sv[0] * i, cz = c[1] + sv[1] * i;
                        int fx = cx - STEP[s][0], fz = cz - STEP[s][1];
                        fits = in(cx, cz) && !in(cx + STEP[s][0], cz + STEP[s][1]) && at(cx, cz) == FREE
                                && rowsFree(cx, cz, pieces[i].length) && in(fx, fz) && at(fx, fz) != TAKEN;
                    }
                    if (fits) {
                        starts.add(c);
                    }
                }
                while (!starts.isEmpty()) {
                    int[] c = starts.remove(rng.nextInt(starts.size()));
                    for (int i = 0; i < pieces.length; i++) {
                        at[c[0] + sv[0] * i - x0][c[1] + sv[1] * i - z0] = TAKEN;
                    }
                    if (connected()) {
                        for (int i = 0; i < pieces.length; i++) {
                            int x = c[0] + sv[0] * i, z = c[1] + sv[1] * i;
                            put(x, z, fs, pieces[i]);
                            mark(x - STEP[s][0], z - STEP[s][1], FRONT);
                        }
                        return new int[] {c[0], c[1], fs};
                    }
                    for (int i = 0; i < pieces.length; i++) {
                        at[c[0] + sv[0] * i - x0][c[1] + sv[1] * i - z0] = FREE;
                    }
                }
            }
            return null;
        }

        /** A piece out in the room, two cells clear of the walls; {x, z} or null. */
        int[] middle(String block) {
            List<int[]> spots = new ArrayList<>();
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                for (int z = z0 + 2; z <= z1 - 2; z++) {
                    if (at(x, z) == FREE) {
                        spots.add(new int[] {x, z});
                    }
                }
            }
            while (!spots.isEmpty()) {
                int[] c = spots.remove(rng.nextInt(spots.size()));
                if (free(c[0], c[1], rng.nextInt(4), block)) {
                    return c;
                }
            }
            return null;
        }

        /** A chair pulled up to the piece at {x, z, facing}, in the cell in front of it, facing it. */
        void chair(int[] d) {
            chair(d, "bunk_chair");
        }

        void chair(int[] d, String chair) {
            int fs = d[2];
            int x = d[0] + STEP[fs][0], z = d[1] + STEP[fs][1];
            if (!in(x, z) || at(x, z) != FRONT) {
                return;
            }
            // the cell in front of the desk is kept clear for this
            at[x - x0][z - z0] = FREE;
            if (!free(x, z, (fs + 2) & 3, chair)) {
                at[x - x0][z - z0] = FRONT;
            }
        }

        /** One cell of a mess table, a chair either side when there's room. */
        void table(int x, int z, boolean alongZ) {
            if (!free(x, z, 0, "bunk_mess_table[axis=" + (alongZ ? "z" : "x") + "]")) {
                return;
            }
            int[] a = alongZ ? new int[] {1, 3} : new int[] {0, 2};
            for (int s : a) {
                if (rng.nextDouble() < 0.7) {
                    free(x + STEP[s][0], z + STEP[s][1], (s + 2) & 3, "bunk_chair");
                }
            }
        }

        /** The map table, w by d cells, somewhere out in the room with chairs round it. */
        void mapTable(int w, int d) {
            for (int tries = 0; tries < 30; tries++) {
                int x = x0 + 1 + rng.nextInt(Math.max(1, w() - w - 1)), z = z0 + 1 + rng.nextInt(Math.max(1, d() - d - 1));
                boolean ok = true;
                for (int i = 0; i < w && ok; i++) {
                    for (int j = 0; j < d && ok; j++) {
                        ok = in(x + i, z + j) && at(x + i, z + j) == FREE;
                    }
                }
                if (!ok) {
                    continue;
                }
                for (int i = 0; i < w; i++) {
                    for (int j = 0; j < d; j++) {
                        at[x + i - x0][z + j - z0] = TAKEN;
                    }
                }
                if (!connected()) {
                    for (int i = 0; i < w; i++) {
                        for (int j = 0; j < d; j++) {
                            at[x + i - x0][z + j - z0] = FREE;
                        }
                    }
                    continue;
                }
                for (int i = 0; i < w; i++) {
                    for (int j = 0; j < d; j++) {
                        put(x + i, z + j, 0, new String[] {"bunk_map_table"});
                    }
                }
                for (int i = 0; i < w; i++) {
                    if (rng.nextBoolean()) {
                        free(x + i, z - 1, 2, "bunk_chair");
                    }
                    if (rng.nextBoolean()) {
                        free(x + i, z + d, 0, "bunk_chair");
                    }
                }
                return;
            }
        }

        /** A red rug over a rectangle, its border round the edge, round whatever already stands there. */
        void rug(int ax, int az, int bx, int bz) {
            for (int x = ax; x <= bx; x++) {
                for (int z = az; z <= bz; z++) {
                    if (!in(x, z) || at(x, z) == TAKEN || at(x, z) == WAY || used[x - x0][1][z - z0]) {
                        continue;
                    }
                    boolean n = z == az, so = z == bz, w = x == ax, e = x == bx;
                    String block;
                    if ((n || so) && (w || e)) {
                        block = "home_rug_red_corner[facing=" + (n ? (w ? "north" : "east") : (e ? "south" : "west")) + "]";
                    } else if (n || so || w || e) {
                        block = "home_rug_red_edge[facing=" + (n ? "north" : so ? "south" : w ? "west" : "east") + "]";
                    } else {
                        block = "home_rug_red";
                    }
                    p.set(x, 1, z, block);
                    used[x - x0][1][z - z0] = true;
                }
            }
        }

        /** A body bag on the floor, head and foot. */
        void bodyBag() {
            for (int tries = 0; tries < 20; tries++) {
                int x = x0 + rng.nextInt(w()), z = z0 + rng.nextInt(d());
                int fc = rng.nextInt(4);
                int fx = x + STEP[fc][0], fz = z + STEP[fc][1];
                if (in(fx, fz) && at(x, z) == FREE && at(fx, fz) == FREE && rowsFree(x, z, 1) && rowsFree(fx, fz, 1)) {
                    p.set(x, 1, z, "bunk_bodybag_head" + face(fc));
                    p.set(fx, 1, fz, "bunk_bodybag" + face(fc));
                    used[x - x0][1][z - z0] = true;
                    used[fx - x0][1][fz - z0] = true;
                    mark(x, z, FRONT);
                    mark(fx, fz, FRONT);
                    return;
                }
            }
        }

        /** Floor litter on free floor. */
        void scatter(String name, int n) {
            for (int i = 0; i < n; i++) {
                for (int tries = 0; tries < 10; tries++) {
                    int x = x0 + rng.nextInt(w()), z = z0 + rng.nextInt(d());
                    byte a = at(x, z);
                    if ((a == FREE || a == FRONT || a == WAY) && !used[x - x0][1][z - z0]) {
                        p.set(x, 1, z, Interior.PLAIN.contains(name) ? name : name + face(rng.nextInt(4)));
                        used[x - x0][1][z - z0] = true;
                        break;
                    }
                }
            }
        }

        private boolean rowsFree(int x, int z, int n) {
            for (int y = 1; y <= n; y++) {
                if (used[x - x0][y][z - z0]) {
                    return false;
                }
            }
            return true;
        }

        private void put(int x, int z, int fs, String[] stack) {
            for (int i = 0; i < stack.length; i++) {
                String b = faced(stack[i], fs);
                if (state != State.SEALED && LOOT.contains(base(stack[i]))
                        && rng.nextDouble() < (state == State.LOOTED ? 0.6 : 0.45)) {
                    b = withLoot(b, "scraps");
                }
                p.set(x, 1 + i, z, b);
                used[x - x0][1 + i][z - z0] = true;
            }
            if (stack.length == 1) {
                what[x - x0][z - z0] = base(stack[0]);
                facing[x - x0][z - z0] = fs;
            }
        }

        /** What was left on the tables, counters and desks. */
        void tops() {
            boolean radio = false;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    String on = what[x - x0][z - z0];
                    if (on == null || used[x - x0][2][z - z0]) {
                        continue;
                    }
                    String put = switch (on) {
                        case "bunk_counter" -> rng.nextDouble() < 0.5 ? pick("home_cans_low", "home_dishes_low", "home_bottles_low") : null;
                        case "bunk_mess_table" -> rng.nextDouble() < 0.35 ? pick("home_dishes_low", "home_cans_low", "home_bottles_low") : null;
                        case "bunk_desk", "home_desk" -> rng.nextDouble() < 0.7
                                ? pick("home_lamp_table_low", "home_books_stack_low", radio ? "home_books_stack_low" : "home_radio_low") : null;
                        case "bunk_map_table" -> rng.nextDouble() < 0.2 ? "home_books_stack_low" : null;
                        default -> null;
                    };
                    if (put == null) {
                        continue;
                    }
                    radio |= put.startsWith("home_radio");
                    p.set(x, 2, z, put + face(facing[x - x0][z - z0]));
                    used[x - x0][2][z - z0] = true;
                }
            }
        }

        /** True while every cell not taken can be reached from the first door. */
        private boolean connected() {
            int[] a = ways.get(0);
            boolean[][] seen = new boolean[w()][d()];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            seen[a[0] - x0][a[1] - z0] = true;
            q.add(a);
            int reached = 1;
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] st : STEP) {
                    int x = c[0] + st[0], z = c[1] + st[1];
                    if (!in(x, z) || seen[x - x0][z - z0] || at(x, z) == TAKEN) {
                        continue;
                    }
                    seen[x - x0][z - z0] = true;
                    reached++;
                    q.add(new int[] {x, z});
                }
            }
            int free = 0;
            for (byte[] col : at) {
                for (byte b : col) {
                    if (b != TAKEN) {
                        free++;
                    }
                }
            }
            return reached == free;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static String base(String block) {
        int open = block.indexOf('[');
        return open < 0 ? block : block.substring(0, open);
    }

    private static String withLoot(String block, String table) {
        int open = block.indexOf('[');
        return open < 0 ? block + "[loot=" + table + "]" : block.substring(0, block.length() - 1) + ",loot=" + table + "]";
    }

    /** Facing for pieces that turn; plain ones and pieces already given a state as they are. */
    private static String faced(String block, int fs) {
        if (block.indexOf('[') >= 0 || PLAIN.contains(block) || Interior.PLAIN.contains(block)) {
            return block;
        }
        return block + face(fs);
    }

    /** The side vector of a piece facing fs: its parts run this way. */
    private static int[] side(int fs) {
        return switch (fs) {
            case 0 -> new int[] {1, 0};
            case 1 -> new int[] {0, 1};
            case 2 -> new int[] {-1, 0};
            default -> new int[] {0, -1};
        };
    }

    private static String face(int d) {
        return "[facing=" + DIRS[d & 3] + "]";
    }
}
