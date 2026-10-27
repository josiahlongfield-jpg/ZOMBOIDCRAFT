package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The inside of a {@link House}, after the homes of Project Zomboid's later builds: a hall from
 * the front door to the back door, rooms either side opening onto it through doors or wide
 * archways, stairs rising beside the hall with a banister round the stairwell above, and each
 * room lined, floored and furnished for what it is. Walls are two layers, the house's siding or
 * brick outside and wallpaper or tile inside, so each window sits in a niche hung with curtains
 * or blinds. Furniture keeps to the walls and off the line between a room's doorways, so every
 * room can be crossed without climbing over anything. Then the house's story on top: a looted
 * house strewn with clothes and broken glass, a survivor's mattress and candles behind boarded
 * windows, a body in the bed.
 *
 * Works in the house's own frame (x along the front, z back from the front wall at z0), one
 * floor of {@link House#STOREY} blocks at a time: the floor slab, then three rows of room.
 */
final class Interior {
    private static final int H = House.STOREY;
    private static final byte OUT = 0, LINING = 1, WALL = 2, ROOM = 3, DOOR = 4, ARCH = 5, STAIR = 6, VOID = 7, TOP = 8,
            PASS = 9;
    private static final String[] DIRS = {"north", "east", "south", "west"};
    /** One block toward each direction (north, east, south, west). */
    private static final int[][] STEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    /** Bedside-table-like pieces: never a second one past a bed's nightstand. */
    private static final Set<String> TABLES = Set.of("home_nightstand", "home_dresser", "home_desk", "home_desk_pc",
            "home_toy_chest");
    /** Pieces with doors or drawers in front: the cell before them is kept clear of other furniture. */
    private static final Set<String> FRONTED = Set.of("home_fridge", "home_stove", "home_wardrobe", "home_bookcase",
            "home_gun_cabinet", "home_dresser", "home_fireplace", "home_wood_stove", "home_tool_chest", "home_freezer",
            "home_vanity", "home_toilet", "home_tv", "home_tv_smashed", "home_piano_l", "home_piano_r", "home_garage_shelf");
    /** Pieces without a facing. */
    /** Pieces with no facing: their state is dropped when they are placed. */
    static final Set<String> PLAIN = Set.of("home_table", "home_floor_lamp", "home_trash_can", "home_laundry",
            "home_boxes", "home_water_heater", "home_water_heater_top", "home_rug_red", "home_rug_blue", "home_candles",
            "home_clothes_pile", "home_blood_pool", "ceiling_light", "ceiling_light_off", "paper", "glass_shards",
            "blood_splat", "home_stain_brown", "home_stain_dark", "scorch", "home_plant", "home_plant_dead", "home_bin_bags");
    /**
     * Kinds of room from the first to go when space is short to the last (every home keeps its
     * kitchen and bathroom), the smallest depth along the strip each needs, and its share of what's left.
     */
    private static final List<String> KINDS = List.of("closet", "bath_half", "laundry", "den", "dining", "bed_kid", "bed",
            "living", "bed_master", "bath", "kitchen");
    private static final int[] MIN = {2, 2, 2, 3, 3, 3, 3, 3, 3, 2, 3};
    private static final double[] SHARE = {1.2, 1.6, 1.8, 3.2, 3.6, 3.2, 3.6, 5.0, 4.8, 2.4, 4.4};

    static final class Room {
        String kind;
        final int a0, b0, a1, b1;
        String paper, floor;
        /** Cells inside the room next to each way in, with the wall side the way in is on: {x, z, side}. */
        final List<int[]> ways = new ArrayList<>();

        Room(String kind, int a0, int b0, int a1, int b1) {
            this.kind = kind;
            this.a0 = a0;
            this.b0 = b0;
            this.a1 = a1;
            this.b1 = b1;
        }

        int w() {
            return a1 - a0 + 1;
        }

        int d() {
            return b1 - b0 + 1;
        }

        boolean has(int x, int z) {
            return x >= a0 && x <= a1 && z >= b0 && z <= b1;
        }
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final House.Style style;
    private final House.Condition cond;
    private final int x0, x1, z0, z1, floors, nx, nz;
    private final boolean lined, garageEast;
    private final double lit;
    private int ux0, ux1, uz0, uz1;
    private byte[][][] cell;
    private Room[][][] owner;
    private final List<List<Room>> rooms = new ArrayList<>();
    /** Windows, as {x, z, floor, side}: the exterior wall block and the side of the house it is on. */
    private final List<int[]> windows = new ArrayList<>();
    /** Each fireplace or wood stove: x, z of its cell, floor, the wall side it backs onto, 1 for a stove. */
    private final List<int[]> hearths = new ArrayList<>();
    private int[][][] winMask;
    private int doorX, backX = -1, garageZ = -1;
    /** One floor of a bigger building ({@link Flats}): its windows are given, and it has no hearth, back door or attic. */
    private boolean flat;
    /** The windows a flat's building already has, as {x, z, side} on its outer walls. */
    private List<int[]> fixedWindows;
    /** The row of a window's lower pane: 2 in a house, 1 over a shop. */
    private int sill = 2;
    /** The stairs: the first of their two columns (-1 for none) and the z of the bottom step. */
    private int sc0 = -1, sz0;
    /** The hall's clear columns, beside the stairs. */
    private int hx0 = -1, hx1 = -1;
    /** The way down to a {@link Cellar}: {0 for stairs or 1 for a hatch, x, z, side}; null for none. */
    private int[] cellarWay;
    // the household's taste
    private final String finish, sofa, drapes, rug, stairsKind, hallPaper, hallFloor;
    private final double worn;
    private final long seedBits;
    // per floor while furnishing: what's in each row of each cell, and the paths kept clear
    private byte[][][] occ;
    private boolean[][] path;
    /** What stands in each cell and row on the floor being furnished (the block's name, no state). */
    private String[][][] what;
    /** Cells in front of a fridge, a wardrobe or the like, kept clear so its doors can open. */
    private boolean[][] keep;
    /** Cells along the wall just past a bed's nightstands, where no second bedside table may stand. */
    private boolean[][] noTable;

    Interior(Placer p, SplittableRandom rng, House.Style style, House.Condition cond, int x0, int x1, int z0, int z1,
             int floors, boolean lined, boolean garageEast, double lit) {
        this.p = p;
        this.rng = rng;
        this.style = style;
        this.cond = cond;
        this.x0 = x0;
        this.x1 = x1;
        this.z0 = z0;
        this.z1 = z1;
        this.floors = floors;
        this.nx = x1 - x0 + 1;
        this.nz = z1 - z0 + 1;
        this.lined = lined;
        this.garageEast = garageEast;
        this.lit = lit;
        finish = pick("oak", "oak", "white", "walnut");
        sofa = pick("floral", "brown", "blue");
        drapes = pick("gold", "rose", "green");
        rug = pick("red", "blue");
        boolean grand = style == House.Style.COLONIAL || style == House.Style.MANSION;
        hallPaper = grand ? pick("damask", "stripe", "wainscot") : pick("cream", "stripe", "sage", "panelling");
        hallFloor = pick("home_floor_oak", "home_floor_oak", "home_floor_parquet");
        stairsKind = rng.nextBoolean() ? "carpet" : "oak";
        seedBits = rng.nextLong();
        worn = switch (cond) {
            case SHUT -> 0.05;
            case BOARDED -> 0.12;
            case LOOTED -> 0.22;
            default -> 0.4;
        };
    }

    /**
     * Makes this one flat of a bigger building, before {@link #plan()}: the building's own windows
     * as {x, z, side} on the outer walls (their lower panes on row sill), and no hearth, back door
     * or attic floor. Its front door is the way in from the stairs.
     */
    Interior asFlat(List<int[]> windows, int sill) {
        this.flat = true;
        this.fixedWindows = windows;
        this.sill = sill;
        return this;
    }

    int doorX() {
        return doorX;
    }

    /** The back door's x on the back wall, or -1. */
    int backX() {
        return backX;
    }

    /** Where a door through the east wall reaches the garage, or -1. */
    int garageZ() {
        return garageZ;
    }

    List<int[]> windows() {
        return windows;
    }

    /** After {@link #build()}: the hearths, for the chimneys outside. */
    /** A seed of the household's own, for choices that mustn't disturb the house's other dice. */
    long seed() {
        return seedBits;
    }

    /**
     * Makes the way down to a basement, after {@link #plan()} and before {@link #build()}. A house
     * with stairs gets a flight down under its stairs, in the column away from the hall, reached
     * from the hall behind them; one without has a trapdoor against a wall, in the laundry, a
     * closet, the hall or the kitchen if it can. The cells are kept clear like a doorway, so the
     * furniture leaves a way to them. Returns {0 for stairs or 1 for a hatch, x, z, side}: for
     * stairs the column, the bottom step's z and the side the column is on away from the hall;
     * for a hatch its cell and the wall the ladder goes down. Null when there is nowhere for one.
     */
    int[] cellar(SplittableRandom r) {
        if (sc0 >= 0) {
            boolean outerLeft = sc0 == hx0 - 2;
            int col = outerLeft ? sc0 : sc0 + 1, other = outerLeft ? sc0 + 1 : sc0;
            Room hall = ownerAt(0, col, sz0 + 4);
            if (hall != null && cellAt(0, col, sz0 + 4) == ROOM && cellAt(0, other, sz0 + 4) == ROOM) {
                hall.ways.add(new int[] {col, sz0 + 4, 0});
                return cellarWay = new int[] {0, col, sz0, outerLeft ? 3 : 1};
            }
        }
        for (String kind : List.of("laundry", "closet", "hall", "kitchen", "den", "dining", "living", "studio")) {
            List<int[]> spots = new ArrayList<>();
            for (Room rm : rooms.get(0)) {
                if (!rm.kind.equals(kind)) {
                    continue;
                }
                for (int x = rm.a0; x <= rm.a1; x++) {
                    for (int z = rm.b0; z <= rm.b1; z++) {
                        if (!inRoom(0, rm, x, z) || cellAt(0, x, z) != ROOM || nearWay(rm, x, z)) {
                            continue;
                        }
                        for (int s = 0; s < 4; s++) {
                            int ix = x - STEP[s][0], iz = z - STEP[s][1];
                            if (wallBehind(0, x, z, s) && inRoom(0, rm, ix, iz) && cellAt(0, ix, iz) == ROOM) {
                                spots.add(new int[] {x, z, s});
                            }
                        }
                    }
                }
            }
            if (!spots.isEmpty()) {
                int[] c = spots.get(r.nextInt(spots.size()));
                ownerAt(0, c[0], c[1]).ways.add(new int[] {c[0], c[1], c[2]});
                return cellarWay = new int[] {1, c[0], c[1], c[2]};
            }
        }
        return null;
    }

    /** True on or beside one of the room's ways in. */
    private static boolean nearWay(Room r, int x, int z) {
        for (int[] w : r.ways) {
            if (Math.abs(w[0] - x) + Math.abs(w[1] - z) <= 1) {
                return true;
            }
        }
        return false;
    }

    List<int[]> hearths() {
        return hearths;
    }

    /** The rooms, floor by floor, as "floor kind x0,z0..x1,z1" lines (for previews). */
    String describe() {
        StringBuilder out = new StringBuilder();
        for (int f = 0; f < rooms.size(); f++) {
            for (Room r : rooms.get(f)) {
                out.append(f).append(' ').append(r.kind).append(' ').append(r.a0).append(',').append(r.b0).append("..")
                        .append(r.a1).append(',').append(r.b1).append('\n');
            }
        }
        if (sc0 >= 0) {
            out.append("stairs x ").append(sc0).append(" z ").append(sz0).append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ plan

    void plan() {
        int l = lined ? 1 : 0;
        ux0 = x0 + 1 + l;
        ux1 = x1 - 1 - l;
        uz0 = z0 + 1 + l;
        uz1 = z1 - 1 - l;
        cell = new byte[floors][nx][nz];
        owner = new Room[floors][nx][nz];
        winMask = new int[floors][nx][nz];
        for (int f = 0; f < floors; f++) {
            rooms.add(new ArrayList<>());
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean ring = x == x0 || x == x1 || z == z0 || z == z1;
                    boolean inner = x < ux0 || x > ux1 || z < uz0 || z > uz1;
                    cell[f][x - x0][z - z0] = ring ? OUT : inner ? LINING : ROOM;
                }
            }
        }
        int uw = ux1 - ux0 + 1, ud = uz1 - uz0 + 1;
        doorX = (x0 + x1) / 2;
        if (uw < 3 || ud < 3) {
            return;
        }
        if (floors == 1 && uw <= 9 && ud <= 9 && uw >= 5 && ud >= 5) {
            studio(uw, ud);
        } else if (uw >= 6 && ud >= 6) {
            hall(uw, ud);
        } else {
            row(uw <= ud);
        }
        essentials();
        // through the lining at the front and back doors, and into the garage
        way(0, doorX, uz0, 0, true);
        if (backX >= 0 && !flat) {
            way(0, backX, uz1, 2, true);
        }
        if (garageEast) {
            garageDoor();
        }
        if (fixedWindows != null) {
            fixedWindows();
        } else {
            placeWindows();
        }
    }

    /**
     * A small house that ran out of room for a bedroom sleeps in its living room (or, failing
     * that, its kitchen): that room becomes a studio, with the bed, the sofa and, if the house
     * has no kitchen, a kitchenette.
     */
    private void essentials() {
        Room bed = null, use = null;
        for (List<Room> fl : rooms) {
            for (Room r : fl) {
                if (r.kind.startsWith("bed") || r.kind.equals("studio")) {
                    bed = r;
                }
            }
        }
        if (bed != null) {
            return;
        }
        for (String k : List.of("living", "den", "dining", "kitchen")) {
            for (List<Room> fl : rooms) {
                for (Room r : fl) {
                    if (use == null && r.kind.equals(k)) {
                        use = r;
                    }
                }
            }
        }
        if (use != null) {
            use.kind = "studio";
        }
    }

    private boolean hasKitchen() {
        for (List<Room> fl : rooms) {
            for (Room r : fl) {
                if (r.kind.equals("kitchen")) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A hall down the middle (or along one side), the stairs beside it, rooms off it in strips. */
    private void hall(int uw, int ud) {
        int hw = floors > 1 || uw >= 15 ? 3 : 2;
        boolean stairs = floors > 1 && ud >= 6;
        int zw = hw + (stairs ? 2 : 0);
        if (uw < zw + 4) {
            row(uw <= ud);
            return;
        }
        boolean two = uw >= zw + 8;
        int zx0;
        if (two) {
            int slack = uw - zw - 8;
            int left = 3 + slack / 2 + (slack >= 4 ? rng.nextInt(3) - 1 : 0);
            zx0 = ux0 + left + 1;
        } else {
            zx0 = rng.nextBoolean() ? ux0 : ux1 - zw + 1;
        }
        int zx1 = zx0 + zw - 1;
        boolean leftStrip = zx0 > ux0, rightStrip = zx1 < ux1;
        // stairs against the outer wall when the hall runs along one side, else on the wider side
        boolean stairsLeft = !leftStrip || rightStrip && (zx0 - ux0 >= ux1 - zx1);
        if (stairs) {
            sc0 = stairsLeft ? zx0 : zx1 - 1;
            hx0 = stairsLeft ? zx0 + 2 : zx0;
            hx1 = hx0 + hw - 1;
            sz0 = uz0 + 2 + 4 <= uz1 ? uz0 + 2 : uz0 + 1;
        } else {
            hx0 = zx0;
            hx1 = zx1;
        }
        doorX = hx0 + (hw == 3 ? 1 : rng.nextInt(2));
        backX = doorX;
        boolean leftBig = !rightStrip || leftStrip && zx0 - ux0 >= ux1 - zx1;
        for (int f = 0; f < floors; f++) {
            room(f, "hall", zx0, uz0, zx1, uz1);
            if (stairs && f < 2) {
                for (int x = sc0; x <= sc0 + 1; x++) {
                    if (f == 0) {
                        for (int i = 0; i < 4; i++) {
                            cell[f][x - x0][sz0 + i - z0] = STAIR;
                        }
                    } else {
                        cell[f][x - x0][sz0 + 1 - z0] = VOID;
                        cell[f][x - x0][sz0 + 2 - z0] = VOID;
                        cell[f][x - x0][sz0 + 3 - z0] = TOP;
                    }
                }
            }
            String[][] prog = program(f, leftStrip && rightStrip, ud);
            if (leftStrip) {
                for (int z = uz0; z <= uz1; z++) {
                    cell[f][zx0 - 1 - x0][z - z0] = WALL;
                }
                strip(f, ux0, zx0 - 2, prog[leftBig ? 0 : 1], 1);
            }
            if (rightStrip) {
                for (int z = uz0; z <= uz1; z++) {
                    cell[f][zx1 + 1 - x0][z - z0] = WALL;
                }
                strip(f, zx1 + 2, ux1, prog[leftBig ? 1 : 0], 3);
            }
        }
    }

    /**
     * A cabin's or a small cottage's one main room across the front (sitting room and bedroom in
     * one), with the kitchen and the bathroom behind it.
     */
    private void studio(int uw, int ud) {
        int fd = Math.min(Math.max(4, ud / 2), ud - 3);
        int bz = uz0 + fd;
        int bw = uw >= 7 ? 3 : 2;
        Room front = room(0, "studio", ux0, uz0, ux1, bz - 1);
        for (int x = ux0; x <= ux1; x++) {
            cell[0][x - x0][bz - z0] = WALL;
        }
        for (int z = bz + 1; z <= uz1; z++) {
            cell[0][ux1 - bw - x0][z - z0] = WALL;
        }
        Room kitchen = room(0, "kitchen", ux0, bz + 1, ux1 - bw - 1, uz1);
        Room bath = room(0, "bath", ux1 - bw + 1, bz + 1, ux1, uz1);
        opening(0, (kitchen.a0 + kitchen.a1) / 2, bz, kitchen.w() >= 3 ? 1 : -1, front, kitchen, 2);
        opening(0, (bath.a0 + bath.a1) / 2, bz, -1, front, bath, 2);
        doorX = (ux0 + ux1) / 2;
        backX = (kitchen.a0 + kitchen.a1) / 2;
    }

    /** What each strip holds on a floor: {bigger strip, smaller strip}, front to back. */
    private String[][] program(int f, boolean two, int ud) {
        String bed2 = rng.nextBoolean() ? "bed_kid" : "bed";
        if (floors == 1) {
            if (!two) {
                return new String[][] {{"living", "kitchen", "bath", "bed_master", bed2}, {}};
            }
            String[] big = ud >= 15 ? new String[] {"living", "dining", "kitchen"} : new String[] {"living", "kitchen"};
            String[] small = ud >= 12 ? new String[] {"bed_master", "bath", bed2} : new String[] {"bed_master", "bath"};
            return new String[][] {big, small};
        }
        if (f == 0) {
            if (!two) {
                return new String[][] {{"living", "dining", "kitchen", "bath_half"}, {}};
            }
            String front = style == House.Style.FARMHOUSE && rng.nextBoolean() ? "laundry" : "den";
            String[] small = ud >= 12 ? new String[] {front, "bath_half", "kitchen"} : new String[] {front, "kitchen"};
            return new String[][] {{"living", "dining"}, small};
        }
        if (!two) {
            return new String[][] {{"bed_master", "bath", bed2, "bed"}, {}};
        }
        String[] small = ud >= 13 ? new String[] {"bed_kid", "closet", "bed"} : new String[] {"bed_kid", "bed"};
        return new String[][] {{"bed_master", "bath"}, small};
    }

    /** Rooms one behind another in a strip of the floor, each with its way onto the hall. */
    private void strip(int f, int a0, int a1, String[] kinds, int hallSide) {
        List<String> ks = fit(kinds, uz1 - uz0 + 1);
        int[] sizes = sizes(ks, uz1 - uz0 + 1);
        int z = uz0;
        Room prev = null;
        boolean prevReached = true;
        for (int i = 0; i < ks.size(); i++) {
            Room r = room(f, ks.get(i), a0, z, a1, z + sizes[i] - 1);
            z += sizes[i];
            if (i < ks.size() - 1) {
                for (int x = a0; x <= a1; x++) {
                    cell[f][x - x0][z - z0] = WALL;
                }
                z++;
            }
            boolean reached = toHall(f, r, hallSide);
            if (prev != null && (!reached || !prevReached || joined(prev.kind, r.kind))) {
                // through the partition between them: wide between living spaces, else a door
                int x = (a0 + a1) / 2;
                boolean wide = joined(prev.kind, r.kind) && a1 - a0 >= 3;
                opening(f, x, r.b0 - 1, wide ? 1 : -1, prev, r, 2);
            }
            prev = r;
            prevReached = reached;
        }
    }

    /** Rooms in a row with a path through them: a shotgun house, or a trailer along x. */
    private void row(boolean alongZ) {
        String[] kinds = floors > 1 ? new String[] {"living", "kitchen", "bath"}
                : alongZ ? new String[] {"living", "bed_master", "kitchen", "bath"}
                : new String[] {"bed_master", "bath", "living", "kitchen", "bed"};
        if (!alongZ && ux1 - ux0 + 1 < 18) {
            kinds = new String[] {"bed_master", "bath", "living", "kitchen"};
        }
        int len = alongZ ? uz1 - uz0 + 1 : ux1 - ux0 + 1;
        List<String> ks = fit(kinds, len);
        int[] sizes = sizes(ks, len);
        // the path: one column (or row) in from a side, so furniture can stand both sides of it
        int across = alongZ ? ux1 - ux0 + 1 : uz1 - uz0 + 1;
        int lane = (alongZ ? ux0 : uz0) + (across >= 4 ? 1 : across / 2);
        for (int f = 0; f < Math.min(floors, 1); f++) {
            int at = alongZ ? uz0 : ux0;
            Room prev = null;
            for (int i = 0; i < ks.size(); i++) {
                Room r = alongZ ? room(f, ks.get(i), ux0, at, ux1, at + sizes[i] - 1)
                        : room(f, ks.get(i), at, uz0, at + sizes[i] - 1, uz1);
                at += sizes[i];
                if (i < ks.size() - 1) {
                    for (int k = alongZ ? ux0 : uz0; k <= (alongZ ? ux1 : uz1); k++) {
                        if (alongZ) {
                            cell[f][k - x0][at - z0] = WALL;
                        } else {
                            cell[f][at - x0][k - z0] = WALL;
                        }
                    }
                    at++;
                }
                if (prev != null) {
                    boolean wide = joined(prev.kind, r.kind) && across >= 4;
                    if (alongZ) {
                        opening(f, lane, r.b0 - 1, wide ? 1 : -1, prev, r, 2);
                    } else {
                        opening(f, r.a0 - 1, lane, wide ? 1 : -1, prev, r, 1);
                    }
                }
                prev = r;
            }
        }
        if (alongZ) {
            doorX = lane;
            backX = lane;
        } else {
            // the front door opens into the living room; the back door from the kitchen
            for (Room r : rooms.get(0)) {
                if (r.kind.equals("living")) {
                    doorX = (r.a0 + r.a1) / 2;
                } else if (r.kind.equals("kitchen")) {
                    backX = (r.a0 + r.a1) / 2;
                }
            }
        }
    }

    /** Drops the least needed kinds until they fit a length (partitions included). */
    private static List<String> fit(String[] kinds, int len) {
        List<String> ks = new ArrayList<>(Arrays.asList(kinds));
        while (ks.size() > 1) {
            int need = ks.size() - 1;
            for (String k : ks) {
                need += MIN[KINDS.indexOf(k)];
            }
            if (need <= len) {
                break;
            }
            String least = ks.get(0);
            for (String k : ks) {
                if (KINDS.indexOf(k) < KINDS.indexOf(least)) {
                    least = k;
                }
            }
            ks.remove(least);
        }
        return ks;
    }

    private static int[] sizes(List<String> ks, int len) {
        int n = ks.size();
        int[] out = new int[n];
        int spare = len - (n - 1);
        double total = 0;
        for (int i = 0; i < n; i++) {
            out[i] = MIN[KINDS.indexOf(ks.get(i))];
            spare -= out[i];
            total += SHARE[KINDS.indexOf(ks.get(i))];
        }
        int given = 0;
        for (int i = 0; i < n; i++) {
            int extra = (int) (Math.max(0, spare) * SHARE[KINDS.indexOf(ks.get(i))] / total);
            out[i] += extra;
            given += extra;
        }
        for (int i = 0; given < spare; i = (i + 1) % n, given++) {
            out[i]++;
        }
        if (spare < 0) {
            out[n - 1] = Math.max(1, out[n - 1] + spare);
        }
        return out;
    }

    private static boolean joined(String a, String b) {
        Set<String> open = Set.of("living", "dining", "kitchen");
        return open.contains(a) && open.contains(b);
    }

    private Room room(int f, String kind, int a0, int b0, int a1, int b1) {
        Room r = new Room(kind, a0, b0, a1, b1);
        r.paper = switch (kind) {
            case "hall" -> hallPaper;
            case "kitchen" -> pick("cream", "sage", "blue", "tile_white");
            case "bath", "bath_half" -> pick("tile_pink", "tile_teal", "tile_white");
            case "laundry" -> pick("tile_white", "cream");
            case "den" -> pick("panelling", "damask", "stripe");
            case "dining" -> pick("damask", "stripe", "cream", "wainscot");
            case "living" -> pick("damask", "stripe", "floral", "sage", "cream");
            case "bed_master" -> pick("floral", "blue", "cream", "stripe");
            case "bed_kid" -> pick("blue", "sage", "stripe");
            default -> pick("cream", "sage", "blue", "floral");
        };
        r.floor = switch (kind) {
            case "hall" -> hallFloor;
            case "kitchen" -> pick("home_floor_lino", "home_floor_lino_check", "home_floor_tile");
            case "bath", "bath_half" -> "home_floor_tile";
            case "laundry" -> pick("home_floor_lino_check", "home_floor_tile");
            case "dining" -> pick("home_floor_parquet", "home_floor_oak");
            case "living" -> pick("home_floor_carpet_beige", "home_floor_carpet_brown", "home_floor_carpet_green",
                    "home_floor_carpet_rose", "home_floor_oak", "home_floor_parquet");
            case "den" -> pick("home_floor_carpet_brown", "home_floor_oak");
            case "closet" -> "home_floor_oak";
            default -> pick("home_floor_carpet_blue", "home_floor_carpet_beige", "home_floor_carpet_rose",
                    "home_floor_carpet_green");
        };
        rooms.get(f).add(r);
        for (int x = a0; x <= a1; x++) {
            for (int z = b0; z <= b1; z++) {
                owner[f][x - x0][z - z0] = r;
            }
        }
        return r;
    }

    /** A way from a strip room through the partition onto the hall; false when none fits. */
    private boolean toHall(int f, Room r, int hallSide) {
        int wx = hallSide == 1 ? r.a1 + 1 : r.a0 - 1;
        int hx = hallSide == 1 ? wx + 1 : wx - 1;
        boolean wide = Set.of("living", "dining", "kitchen", "den").contains(r.kind) && r.d() >= 3;
        int best = -1, bestScore = Integer.MAX_VALUE;
        for (int z = r.b0; z <= r.b1 - (wide ? 1 : 0); z++) {
            boolean ok = true;
            for (int k = 0; k <= (wide ? 1 : 0); k++) {
                ok &= walkable(f, hx, z + k) && cellAt(f, wx, z + k) == WALL;
            }
            int score = Math.abs(z - (r.b0 + 1));
            if (ok && score < bestScore) {
                best = z;
                bestScore = score;
            }
        }
        if (best < 0 && wide) {
            wide = false;
            for (int z = r.b0; z <= r.b1; z++) {
                int score = Math.abs(z - (r.b0 + 1));
                if (walkable(f, hx, z) && cellAt(f, wx, z) == WALL && score < bestScore) {
                    best = z;
                    bestScore = score;
                }
            }
        }
        if (best < 0) {
            return false;
        }
        Room hall = owner[f][hx - x0][best - z0];
        opening(f, wx, best, wide ? 2 : -1, hallSide == 1 ? r : hall, hallSide == 1 ? hall : r, 1);
        return true;
    }

    /**
     * An opening in a partition at (x, z) between room a (on the low side) and room b: a door
     * (wide -1), or a two-wide archway running on along x (wide 1) or along z (wide 2). across is
     * the direction from a to b: 1 east, 2 south.
     */
    private void opening(int f, int x, int z, int wide, Room a, Room b, int across) {
        int n = wide < 0 ? 1 : 2;
        for (int k = 0; k < n; k++) {
            int cx = x + (wide == 1 ? k : 0), cz = z + (wide == 2 ? k : 0);
            if (cellAt(f, cx, cz) != WALL) {
                continue;
            }
            cell[f][cx - x0][cz - z0] = wide < 0 ? DOOR : ARCH;
            int[] s = STEP[across];
            a.ways.add(new int[] {cx - s[0], cz - s[1], across});
            b.ways.add(new int[] {cx + s[0], cz + s[1], (across + 2) & 3});
        }
    }

    /** A way through the lining (front, back or garage door) into the room at (x, z). */
    private void way(int f, int x, int z, int side, boolean through) {
        Room r = owner[f][x - x0][z - z0];
        if (r == null) {
            return;
        }
        r.ways.add(new int[] {x, z, side});
        if (lined && through) {
            int[] s = STEP[side];
            cell[f][x + s[0] - x0][z + s[1] - z0] = PASS;
        }
    }

    private void garageDoor() {
        Room best = null;
        for (Room r : rooms.get(0)) {
            if (r.a1 != ux1 || r.kind.startsWith("bed") || r.kind.startsWith("bath") || r.kind.equals("living")) {
                continue;
            }
            if (best == null || r.kind.equals("kitchen") || r.kind.equals("laundry") && !best.kind.equals("kitchen")) {
                best = r;
            }
        }
        if (best == null) {
            return;
        }
        for (int z = best.b1; z >= best.b0; z--) {
            if (cellAt(0, ux1, z) == ROOM && !(best.kind.equals("hall") && z >= sz0 - 1 && z <= sz0 + 4 && sc0 >= 0)) {
                garageZ = z;
                way(0, ux1, z, 1, true);
                return;
            }
        }
    }

    /** Windows centred on each room's stretch of outside wall, one or two to a stretch. */
    private void placeWindows() {
        for (int f = 0; f < floors; f++) {
            for (Room r : rooms.get(f)) {
                if (r.kind.equals("closet")) {
                    continue;
                }
                for (int s = 0; s < 4; s++) {
                    boolean outside = switch (s) {
                        case 0 -> r.b0 == uz0;
                        case 1 -> r.a1 == ux1 && !(garageEast && f == 0);
                        case 2 -> r.b1 == uz1;
                        default -> r.a0 == ux0;
                    };
                    if (!outside || r.kind.equals("hall") && f == 0 && (s == 0 || s == 2)) {
                        continue;
                    }
                    boolean alongX = s == 0 || s == 2;
                    int lo = alongX ? r.a0 : r.b0, hi = alongX ? r.a1 : r.b1;
                    int n = hi - lo + 1;
                    int[] at = n >= 8 ? new int[] {lo + n / 4, hi - n / 4} : n >= 2 || r.kind.startsWith("bath")
                            ? new int[] {(lo + hi) / 2} : new int[0];
                    for (int a : at) {
                        int wx = alongX ? a : s == 1 ? x1 : x0, wz = alongX ? (s == 0 ? z0 : z1) : a;
                        if (f == 0 && (Math.abs(wx - doorX) <= 1 && wz == z0 || backX >= 0 && Math.abs(wx - backX) <= 1
                                && wz == z1 || garageZ >= 0 && wx == x1 && Math.abs(wz - garageZ) <= 1)) {
                            continue;
                        }
                        windows.add(new int[] {wx, wz, f, s});
                        int[] in = STEP[(s + 2) & 3];
                        int rx = wx + in[0] * (lined ? 2 : 1), rz = wz + in[1] * (lined ? 2 : 1);
                        if (inside(rx, rz)) {
                            winMask[f][rx - x0][rz - z0] |= 1 << s;
                        }
                    }
                }
            }
        }
    }

    /** A flat's windows are its building's: furniture keeps clear of them all the same. */
    private void fixedWindows() {
        for (int[] w : fixedWindows) {
            if (w[2] == 0 && w[0] == doorX) {
                continue;  // the door takes its place
            }
            windows.add(new int[] {w[0], w[1], 0, w[2]});
            int[] in = STEP[(w[2] + 2) & 3];
            int rx = w[0] + in[0] * (lined ? 2 : 1), rz = w[1] + in[1] * (lined ? 2 : 1);
            if (inside(rx, rz)) {
                winMask[0][rx - x0][rz - z0] |= 1 << w[2];
            }
        }
    }

    // ------------------------------------------------------------------ build

    /** Lays the lining, partitions, doors, floors, ceilings and stairs, then furnishes. */
    void build() {
        for (int f = 0; f < floors; f++) {
            int base = f * H;
            for (int x = x0 + 1; x < x1; x++) {
                for (int z = z0 + 1; z < z1; z++) {
                    byte t = cell[f][x - x0][z - z0];
                    // the floor (the ceiling of the floor below)
                    if (t == VOID || f == 0 && cellarOpen(x, z)) {
                        p.set(x, base, z, "air");
                    } else if (f == 0 && cellarWay != null && cellarWay[0] == 0 && x == cellarWay[1] && z == sz0 + 3) {
                        p.set(x, base, z, "home_stairs_oak[facing=south]");
                    } else if (f == 0 && cellarWay != null && cellarWay[0] == 1 && x == cellarWay[1] && z == cellarWay[2]) {
                        // looters leave the hatch thrown open
                        boolean open = (cond == House.Condition.LOOTED || cond == House.Condition.RUIN) && rng.nextBoolean();
                        String facing = DIRS[(cellarWay[3] + 2) & 3];
                        p.set(x, base, z, "minecraft:spruce_trapdoor[facing=" + facing + ",half=top,open=" + open + "]");
                    } else if (t == TOP) {
                        p.set(x, base, z, "home_stairs_" + stairsKind + "[facing=south]");
                    } else {
                        p.set(x, base, z, floorAt(f, x, z));
                    }
                    for (int row = 1; row < H; row++) {
                        p.set(x, base + row, z, rowBlock(f, x, z, t, row));
                    }
                }
            }
            if (sc0 >= 0 && f == 0) {
                for (int x = sc0; x <= sc0 + 1; x++) {
                    for (int i = 0; i < 3; i++) {
                        p.set(x, base + 1 + i, sz0 + i, "home_stairs_" + stairsKind + "[facing=south]");
                    }
                }
            }
            niches(f);
        }
        // the attic floor over the top storey: the ceiling seen from below (a flat's is the floor or roof above)
        for (int x = x0 + 1; x < x1 && !flat; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x, floors * H, z, "home_floor_oak");
            }
        }
        for (int f = 0; f < floors; f++) {
            furnish(f);
        }
    }

    private String rowBlock(int f, int x, int z, byte t, int row) {
        return switch (t) {
            case LINING -> lining(f, x, z, row);
            case WALL -> partition(f, x, z, row);
            case DOOR -> row == 3 ? partition(f, x, z, row) : door(f, x, z, row);
            case ARCH -> row == 3 ? partition(f, x, z, row) : "air";
            case PASS -> row == 3 ? lining(f, x, z, row) : "air";
            case STAIR -> {
                // under the flight: solid up to each step, but for the way down to the basement
                int i = z - sz0;
                boolean down = f == 0 && cellarWay != null && cellarWay[0] == 0 && x == cellarWay[1] && row <= 2;
                yield row <= i && !down ? wallBlock(hallPaper, row, f, x, z) : "air";
            }
            default -> "air";
        };
    }

    /** The ground floor opened over the basement stairs: the cells the flight goes down through. */
    private boolean cellarOpen(int x, int z) {
        return cellarWay != null && cellarWay[0] == 0 && x == cellarWay[1] && z >= sz0 && z <= sz0 + 2;
    }

    private String door(int f, int x, int z, int row) {
        boolean alongX = cellAt(f, x - 1, z) == WALL || cellAt(f, x + 1, z) == WALL;
        long h = (long) x * 31 + z * 17 + f;
        String facing = alongX ? (h % 3 == 0 ? "north" : "south") : (h % 3 == 0 ? "east" : "west");
        boolean open = switch (cond) {
            case SHUT -> h % 5 == 0;
            case BOARDED -> h % 3 == 0;
            default -> h % 4 != 0;
        };
        if ((cond == House.Condition.RUIN || cond == House.Condition.LOOTED) && h % 7 == 0) {
            return "air";  // taken off its hinges
        }
        String wood = (style == House.Style.COLONIAL || style == House.Style.MANSION || style == House.Style.CABIN)
                ? "home_door_oak" : "home_door_white";
        return wood + "[facing=" + facing + ",half=" + (row == 1 ? "lower" : "upper") + ",hinge="
                + (h % 2 == 0 ? "left" : "right") + ",open=" + open + "]";
    }

    /** The lining of an outside wall takes the paper of the room it faces. */
    private String lining(int f, int x, int z, int row) {
        Room r = near(f, x, z);
        if (!lined) {
            return "air";
        }
        return wallBlock(r == null ? hallPaper : r.paper, row, f, x, z);
    }

    private String partition(int f, int x, int z, int row) {
        Room a = null, b = null;
        for (int[] s : STEP) {
            Room r = ownerAt(f, x + s[0], z + s[1]);
            if (r == null) {
                continue;
            }
            if (a == null) {
                a = r;
            } else if (r != a) {
                b = r;
            }
        }
        Room use = a;
        if (b != null) {
            if (b.kind.startsWith("bath")) {
                use = b;
            } else if (!a.kind.startsWith("bath") && b.kind.equals("hall")) {
                use = b;
            }
        }
        return wallBlock(use == null ? hallPaper : use.paper, row, f, x, z);
    }

    /** A wall block of a paper: wainscot keeps its panelling to the bottom row. */
    private String wallBlock(String paper, int row, int f, int x, int z) {
        // damp rises from the floor: a worn stretch of wall is worn at the bottom, patchy higher up
        boolean w = wornAt(f, x, z) && (row == 1 || ((x * 7 + z * 13 + row * 5) & 3) < (row == 2 ? 2 : 1));
        if (paper.equals("wainscot")) {
            return row == 1 ? "home_wall_wainscot" : w ? "home_wall_cream_worn" : "home_wall_cream";
        }
        return "home_wall_" + paper + (w ? "_worn" : "");
    }

    /** Wear comes in patches (a damp corner, a wall someone fought along), not block by block. */
    private boolean wornAt(int f, int x, int z) {
        long h = (x / 3) * 73856093L ^ (z / 3) * 19349663L ^ f * 83492791L ^ seedBits;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 1023) < worn * 1024;
    }

    private String floorAt(int f, int x, int z) {
        Room r = ownerAt(f, x, z);
        if (r == null) {
            r = near(f, x, z);
        }
        return r == null ? hallFloor : r.floor;
    }

    /** Curtains, blinds or nothing in the niche behind each window. */
    private void niches(int f) {
        if (!lined) {
            return;
        }
        for (int[] w : windows) {
            if (w[2] != f) {
                continue;
            }
            int[] in = STEP[(w[3] + 2) & 3];
            int x = w[0] + in[0], z = w[1] + in[1];
            int base = f * H;
            p.set(x, base + sill, z, "air");
            p.set(x, base + sill + 1, z, "air");
            Room r = near(f, x, z);
            if (cond == House.Condition.BOARDED || (cond == House.Condition.LOOTED || cond == House.Condition.RUIN)
                    && rng.nextDouble() < 0.3 || r == null) {
                continue;
            }
            String facing = "[facing=" + DIRS[w[3]] + "]";
            if (Set.of("kitchen", "bath", "bath_half", "den", "laundry", "hall").contains(r.kind)) {
                p.set(x, base + sill + 1, z, "home_blinds" + facing);
            } else {
                p.set(x, base + sill, z, "home_curtains_" + drapes + facing);
                p.set(x, base + sill + 1, z, "home_curtains_" + drapes + "_top" + facing);
            }
        }
    }

    // ------------------------------------------------------------------ furnishing

    private void furnish(int f) {
        occ = new byte[nx][nz][H];
        path = new boolean[nx][nz];
        what = new String[nx][nz][H];
        keep = new boolean[nx][nz];
        noTable = new boolean[nx][nz];
        List<Room> list = rooms.get(f);
        for (Room r : list) {
            keepClear(f, r);
        }
        if (f == 0 && cellarWay != null && cellarWay[0] == 1) {
            occ[cellarWay[1] - x0][cellarWay[2] - z0][1] = 2;  // nothing, not even a rug, over the hatch
        }
        for (Room r : list) {
            switch (r.kind) {
                case "kitchen" -> kitchen(f, r);
                case "living" -> living(f, r);
                case "dining" -> dining(f, r);
                case "den" -> den(f, r);
                case "bed_master", "bed", "bed_kid" -> bedroom(f, r);
                case "bath", "bath_half" -> bathroom(f, r);
                case "laundry" -> laundry(f, r);
                case "closet" -> closet(f, r);
                case "studio" -> studio(f, r);
                case "hall" -> hallway(f, r);
                default -> {
                }
            }
            light(f, r);
        }
        if (f == 0) {
            entry(f);
        }
        for (Room r : list) {
            extras(f, r);
        }
        surfaces(f);
        story(f);
    }

    /** The cell inside each way into a room stays clear, and so does the hall itself. */
    private void keepClear(int f, Room r) {
        for (int[] w : r.ways) {
            if (inside(w[0], w[1])) {
                path[w[0] - x0][w[1] - z0] = true;
            }
        }
        if (r.kind.equals("hall") && hx0 >= 0) {
            for (int x = hx0; x <= hx1; x++) {
                for (int z = r.b0; z <= r.b1; z++) {
                    path[x - x0][z - z0] = true;
                }
            }
        }
    }

    /** True while every way into the room can still be walked to from the first, round the furniture. */
    private boolean connected(int f, Room r) {
        if (r.ways.size() < 2) {
            return r.ways.isEmpty() || occ[r.ways.get(0)[0] - x0][r.ways.get(0)[1] - z0][1] != 1;
        }
        int w = r.w(), d = r.d();
        boolean[] seen = new boolean[w * d];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        int[] s0 = r.ways.get(0);
        if (!r.has(s0[0], s0[1]) || occ[s0[0] - x0][s0[1] - z0][1] == 1) {
            return false;
        }
        seen[(s0[0] - r.a0) * d + (s0[1] - r.b0)] = true;
        q.add(new int[] {s0[0], s0[1]});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] st : STEP) {
                int x = c[0] + st[0], z = c[1] + st[1];
                if (!r.has(x, z) || seen[(x - r.a0) * d + (z - r.b0)]) {
                    continue;
                }
                byte t = cell[f][x - x0][z - z0];
                if (!(t == ROOM || t == TOP) || occ[x - x0][z - z0][1] == 1) {
                    continue;
                }
                seen[(x - r.a0) * d + (z - r.b0)] = true;
                q.add(new int[] {x, z});
            }
        }
        for (int[] way : r.ways) {
            if (r.has(way[0], way[1]) && !seen[(way[0] - r.a0) * d + (way[1] - r.b0)]) {
                return false;
            }
        }
        return true;
    }

    private void kitchen(int f, Room r) {
        int s = farWall(f, r);
        List<int[]> run = run(f, r, s, false);
        if (run.size() < 3) {
            s = longestWall(f, r);
            run = run(f, r, s, false);
        }
        String fin = finish;
        String[] seq;
        int n = run.size();
        int fs = (s + 2) & 3;
        int lWall = -1;
        List<int[]> lRun = List.of();
        boolean fridgeInL = false;
        if (n >= 3) {
            seq = new String[n];
            Arrays.fill(seq, "counter");
            int sink = n / 2;
            for (int i = 0; i < n; i++) {
                if (window(f, run.get(i), s)) {
                    sink = i;
                }
            }
            seq[sink] = "sink";
            boolean fridgeLeft = sink >= n / 2;
            int fr = fridgeLeft ? 0 : n - 1;
            if (!window(f, run.get(fr), s) && fr != sink) {
                seq[fr] = "fridge";
            }
            int dw = sink + (fridgeLeft ? 1 : -1);
            if (dw >= 0 && dw < n && seq[dw].equals("counter")) {
                seq[dw] = "dishwasher";
            }
            int stove = fridgeLeft ? Math.max(sink - 2, 1) : Math.min(sink + 2, n - 2);
            if (stove >= 0 && stove < n && seq[stove].equals("counter")) {
                seq[stove] = "stove";
            } else {
                // the ends may turn a corner: keep the stove off them if it can be
                for (int pass = 0; pass < 2 && !Arrays.asList(seq).contains("stove"); pass++) {
                    for (int i = pass == 0 ? 1 : 0; i < (pass == 0 ? n - 1 : n); i++) {
                        if (seq[i].equals("counter") && Math.abs(i - sink) >= 2) {
                            seq[i] = "stove";
                            break;
                        }
                    }
                }
            }
            for (int i = 0; i < n; i++) {
                if (seq[i].equals("counter") && rng.nextDouble() < 0.4) {
                    seq[i] = "drawers";
                }
            }
            // round the corner at one end, more cupboards; at the fridge's end the fridge moves to
            // the far end of them and a cupboard takes the corner, so nothing stands in front of its door
            if (r.w() >= 4 && r.d() >= 4) {
                int[] ends = {fr, n - 1 - fr};
                for (int e : ends) {
                    String at = seq[e];
                    if (!(at.equals("counter") || at.equals("drawers") || at.equals("fridge"))) {
                        continue;
                    }
                    int[] sv = side(fs);
                    int s2 = wallToward(e == 0 ? -sv[0] : sv[0], e == 0 ? -sv[1] : sv[1]);
                    int[] corner = run.get(e);
                    if (!wallBehind(f, corner[0], corner[1], s2)) {
                        continue;  // the run stops short of the corner (a door)
                    }
                    List<int[]> l = lCells(f, r, corner, s, s2, r.kind.equals("kitchen") ? 4 : 2);
                    boolean fridge = at.equals("fridge");
                    if (fridge) {
                        int last = l.size() - 1;
                        while (last >= 0 && window(f, l.get(last), s2)) {
                            last--;
                        }
                        if (last < 0) {
                            continue;
                        }
                        l = new ArrayList<>(l.subList(0, last + 1));
                    } else if (l.size() < 2) {
                        continue;
                    }
                    if (fridge) {
                        seq[e] = "counter";
                        fridgeInL = true;
                    }
                    lWall = s2;
                    lRun = l;
                    break;
                }
            }
            List<String> small = new ArrayList<>(List.of("home_microwave", "home_toaster", "home_coffee_maker", "home_dishes"));
            for (int i = 0; i < n; i++) {
                int[] c = run.get(i);
                String block = switch (seq[i]) {
                    case "fridge" -> "home_fridge";
                    case "stove" -> "home_stove";
                    case "sink" -> "home_sink_" + fin;
                    case "dishwasher" -> "home_dishwasher_" + fin;
                    case "drawers" -> "home_drawers_" + fin;
                    default -> "home_counter_" + fin;
                };
                boolean in = seq[i].equals("fridge")
                        ? putAll(f, new int[][] {{c[0], 1, c[1]}, {c[0], 2, c[1]}}, new String[] {block + face(fs), "home_fridge_top" + face(fs)})
                        : put(f, c[0], 1, c[1], block + face(fs));
                if (!in) {
                    continue;
                }
                if (seq[i].equals("fridge")) {
                    // nothing on top of the fridge but the cabinet
                } else if ((seq[i].equals("counter") || seq[i].equals("drawers")) && !small.isEmpty() && rng.nextDouble() < 0.6) {
                    put(f, c[0], 2, c[1], small.remove(rng.nextInt(small.size())) + face(fs));
                }
                if (!window(f, c, s) && occ[c[0] - x0][c[1] - z0][3] == 0) {
                    put(f, c[0], 3, c[1], "home_wallcab_" + fin + face(fs));
                }
            }
        }
        if (lWall >= 0 && !kitchenL(f, lRun, lWall, fridgeInL) && fridgeInL) {
            tall(f, r, "home_fridge", "home_fridge_top");  // the corner run was cut short: the fridge stands elsewhere
        }
        table(f, r, 2, 4);
        corner(f, r, "home_trash_can");
        decor(f, r, "home_calendar", 2);
        radiator(f, r);
    }

    /**
     * Cells along wall s2 going out from the corner cell of the run on wall s, side by side and free,
     * at most len of them.
     */
    private List<int[]> lCells(int f, Room r, int[] corner, int s, int s2, int len) {
        List<int[]> out = new ArrayList<>();
        int[] in = STEP[(s + 2) & 3];
        for (int k = 1; k <= len; k++) {
            int x = corner[0] + in[0] * k, z = corner[1] + in[1] * k;
            if (!freeFloor(f, r, x, z) || !wallBehind(f, x, z, s2) || keep[x - x0][z - z0]) {
                break;
            }
            out.add(new int[] {x, z});
        }
        return out;
    }

    /** The side whose wall lies in direction (dx, dz) from a cell. */
    private static int wallToward(int dx, int dz) {
        for (int k = 0; k < 4; k++) {
            if (STEP[k][0] == dx && STEP[k][1] == dz) {
                return k;
            }
        }
        return 0;
    }

    /**
     * More cupboards round the corner from the main run along wall s2, wall cabinets over them, and
     * with fridge the fridge at their far end. False if the fridge was meant to go in but didn't.
     */
    private boolean kitchenL(int f, List<int[]> l, int s2, boolean fridge) {
        int fs = (s2 + 2) & 3;
        for (int k = 0; k < l.size(); k++) {
            int[] c = l.get(k);
            boolean last = k == l.size() - 1;
            if (fridge && last) {
                if (!putAll(f, new int[][] {{c[0], 1, c[1]}, {c[0], 2, c[1]}},
                        new String[] {"home_fridge" + face(fs), "home_fridge_top" + face(fs)})) {
                    return false;
                }
            } else if (!put(f, c[0], 1, c[1], (k % 2 == 0 ? "home_counter_" : "home_drawers_") + finish + face(fs))) {
                return !fridge;
            }
            if (!window(f, c, s2) && occ[c[0] - x0][c[1] - z0][3] == 0) {
                put(f, c[0], 3, c[1], "home_wallcab_" + finish + face(fs));
            }
        }
        return true;
    }

    private void living(int f, Room r) {
        int s = farWall(f, r);
        List<int[]> run = run(f, r, s, false);
        if (run.size() < 2) {
            s = longestWall(f, r);
            run = run(f, r, s, false);
        }
        int fs = (s + 2) & 3;
        if (run.size() >= 2) {
            int len = Math.min(3, run.size());
            int start = (run.size() - len) / 2;
            String[] parts = len == 3 ? new String[] {"_l", "_m", "_r"} : new String[] {"_l", "_r"};
            int[][] at = new int[len][];
            String[] blocks = new String[len];
            for (int i = 0; i < len; i++) {
                int[] c = run.get(start + i);
                at[i] = new int[] {c[0], 1, c[1]};
                blocks[i] = "home_sofa_" + sofa + parts[i] + face(fs);
            }
            if (adjacent(run, start, len - 1) && putAll(f, at, blocks)) {
                sofaAround(f, r, run, start, len, s);
            }
        }
        // armchairs at the sides, bookcases where there's wall, the radiator under a window
        for (int side : new int[] {(s + 1) & 3, (s + 3) & 3}) {
            List<int[]> sr = run(f, r, side, false);
            if (!sr.isEmpty() && rng.nextDouble() < 0.7) {
                int[] c = sr.get(side == ((s + 1) & 3) ? sr.size() - 1 : 0);
                put(f, c[0], 1, c[1], "home_armchair_" + sofa + face((side + 2) & 3));
            }
        }
        pair(f, r, "home_bookcase", "home_bookcase_top");
        radiator(f, r);
        corner(f, r, "home_floor_lamp");
        decor(f, r, pick("home_mirror", "home_picture_stitch", "clock_face"), 2);
    }

    /** Round a sofa against wall s: a picture over it, an end table and lamp, the coffee table, the TV, a rug. */
    private void sofaAround(int f, Room r, List<int[]> run, int start, int len, int s) {
        int fs = (s + 2) & 3;
        int[] m = run.get(start + len / 2);
        hang(f, m[0], m[1], s, 2, pick("home_picture_lake", "home_picture_family"));
        if (start > 0) {
            int[] e = run.get(start - 1);
            put(f, e[0], 1, e[1], "home_nightstand" + face(fs));
        }
        if (start + len < run.size()) {
            int[] e = run.get(start + len);
            put(f, e[0], 1, e[1], "home_floor_lamp");
        }
        // the coffee table in front, the television across the room facing the sofa
        int[] ft = STEP[fs];
        int depth = s == 0 || s == 2 ? r.d() : r.w();
        int gap = depth >= 6 ? 2 : 1;
        int tx = m[0] + ft[0] * gap, tz = m[1] + ft[1] * gap;
        if (depth >= 4 && freeFloor(f, r, tx, tz)) {
            put(f, tx, 1, tz, "home_coffee_table" + face(fs));
        }
        int vx = m[0] + ft[0] * (depth - 1), vz = m[1] + ft[1] * (depth - 1);
        if (depth >= 4 && freeFloor(f, r, vx, vz) && wallBehind(f, vx, vz, fs)) {
            boolean smashed = (cond == House.Condition.LOOTED || cond == House.Condition.RUIN) && rng.nextBoolean();
            put(f, vx, 1, vz, (smashed ? "home_tv_smashed" : "home_tv") + face(s));
        }
        rugBetween(f, r, m, fs, depth, len);
    }

    private void dining(int f, Room r) {
        table(f, r, r.w() >= 6 && r.d() >= 5 ? 4 : 2, 6);
        int s = longestWall(f, r);
        List<int[]> run = run(f, r, s, false);
        for (int k = 0; k < run.size(); k++) {
            int i = run.size() / 2 + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
            if (i < 0 || i >= run.size()) {
                continue;
            }
            int[] c = run.get(i);
            if (frontFree(c, (s + 2) & 3) && put(f, c[0], 1, c[1], "home_dresser" + face((s + 2) & 3))) {
                hang(f, c[0], c[1], s, 2, pick("home_picture_lake", "home_mirror", "home_picture_family"));
                break;
            }
        }
        pair(f, r, "home_bookcase", "home_bookcase_top");
        radiator(f, r);
    }

    private void den(int f, Room r) {
        int s = farWall(f, r);
        List<int[]> run = run(f, r, s, false);
        if (!run.isEmpty()) {
            int[] c = run.get(run.size() / 2);
            int fs = (s + 2) & 3;
            put(f, c[0], 1, c[1], "home_desk_pc" + face(fs));
            int[] ft = STEP[fs];
            if (clear(f, r, c[0] + ft[0], c[1] + ft[1])) {
                put(f, c[0] + ft[0], 1, c[1] + ft[1], "office_chair" + face(fs));
            }
        }
        pair(f, r, "home_bookcase", "home_bookcase_top");
        boolean rural = style == House.Style.FARMHOUSE || style == House.Style.CABIN;
        if (rural && rng.nextDouble() < 0.5 || rng.nextDouble() < 0.15) {
            tall(f, r, "home_gun_cabinet", "home_gun_cabinet_top");
        }
        against(f, r, "home_armchair_" + sofa);
        against(f, r, "home_boxes");
        radiator(f, r);
        decor(f, r, "home_picture_lake", 2);
    }

    private void bedroom(int f, Room r) {
        boolean master = r.kind.equals("bed_master");
        boolean kid = r.kind.equals("bed_kid");
        String quilt = kid ? pick("blue", "rose") : pick("quilt", "blue", "rose", "quilt");
        if (cond == House.Condition.SHUT && master && rng.nextDouble() < 0.2) {
            quilt = "bloody";  // someone lay down here and didn't get up
        }
        int s = farWall(f, r);
        bed(f, r, s, quilt, master && r.w() >= 4 && r.d() >= 4 ? 2 : 1, false,
                kid ? "home_picture_stitch" : pick("home_picture_lake", "home_picture_family"), kid ? "blue" : rug);
        if (kid) {
            against(f, r, "home_toy_chest");
            int[] desk = against(f, r, "home_desk");
            if (desk != null) {
                int[] ft2 = STEP[desk[2]];
                if (clear(f, r, desk[0] + ft2[0], desk[1] + ft2[1])) {
                    put(f, desk[0] + ft2[0], 1, desk[1] + ft2[1], "home_chair" + face((desk[2] + 2) & 3));
                }
            }
        } else {
            int[] dr = against(f, r, "home_dresser");
            if (dr != null) {
                hang(f, dr[0], dr[1], (dr[2] + 2) & 3, 2, "home_mirror");
            }
        }
        tall(f, r, "home_wardrobe", "home_wardrobe_top");
        if (master || rng.nextBoolean()) {
            corner(f, r, "home_laundry");
        }
        radiator(f, r);
        decor(f, r, kid ? "home_picture_stitch" : "home_calendar", 2);
    }

    /**
     * A bed with its head against wall s, as near the middle of the wall as it fits with room to
     * walk round its foot, then a nightstand each open side, a picture over it and a rug at its foot.
     * With any, the other walls are tried if s has no room. False if no bed went in.
     */
    private boolean bed(int f, Room r, int s0, String quilt, int width, boolean any, String picture, String rugColour) {
        for (int k0 = 0; k0 < (any ? 4 : 1); k0++) {
            int s = (s0 + k0) & 3;
            int fs = (s + 2) & 3;
            int[] ft = STEP[fs];
            List<int[]> run = run(f, r, s, false);
            int placed = -1;
            for (int k = 0; k + width <= run.size() && placed < 0; k++) {
                int i = (run.size() - width) / 2 + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
                if (i < 0 || i + width > run.size()) {
                    continue;
                }
                boolean ok = true;
                for (int j = 0; j < width; j++) {
                    int[] c = run.get(i + j);
                    if (!adjacent(run, i, j) || !freeFloor(f, r, c[0] + ft[0], c[1] + ft[1])) {
                        ok = false;
                    }
                }
                if (!ok) {
                    continue;
                }
                int[][] at = new int[width * 2][];
                String[] blocks = new String[width * 2];
                for (int j = 0; j < width; j++) {
                    int[] c = run.get(i + j);
                    at[2 * j] = new int[] {c[0], 1, c[1]};
                    at[2 * j + 1] = new int[] {c[0] + ft[0], 1, c[1] + ft[1]};
                    blocks[2 * j] = "home_bed_" + quilt + "_head" + face(fs);
                    blocks[2 * j + 1] = "home_bed_" + quilt + "_foot" + face(fs);
                }
                if (putAll(f, at, blocks)) {
                    placed = i;
                }
            }
            if (placed < 0) {
                continue;
            }
            int[] c = run.get(placed);
            hang(f, c[0], c[1], s, 2, picture);
            // one nightstand on each side the bed is open, right by the head (none on a side
            // against a wall), and no second bedside table beyond it
            int[] sv = side(fs);
            int[] last = run.get(placed + width - 1);
            for (int[] e : new int[][] {{c[0] - sv[0], c[1] - sv[1], -1}, {last[0] + sv[0], last[1] + sv[1], 1}}) {
                if (freeFloor(f, r, e[0], e[1]) && wallBehind(f, e[0], e[1], s)) {
                    put(f, e[0], 1, e[1], "home_nightstand" + face(fs));
                }
                int bx = e[0] + sv[0] * e[2], bz = e[1] + sv[1] * e[2];
                if (inside(bx, bz)) {
                    noTable[bx - x0][bz - z0] = true;
                }
                if (inside(e[0], e[1]) && occ[e[0] - x0][e[1] - z0][1] == 0) {
                    noTable[e[0] - x0][e[1] - z0] = true;  // the side left bare stays bare of tables too
                }
            }
            // a rug at the foot
            int rx = c[0] + ft[0] * 2, rz = c[1] + ft[1] * 2;
            if (freeFloor(f, r, rx, rz) || path[rx - x0][rz - z0] && inRoom(f, r, rx, rz)) {
                for (int j = -1; j <= width; j++) {
                    int x = rx + sv[0] * j, z = rz + sv[1] * j;
                    if (inRoom(f, r, x, z) && occ[x - x0][z - z0][1] == 0) {
                        put(f, x, 1, z, "home_rug_" + rugColour + "_edge" + face(fs));
                    }
                }
            }
            return true;
        }
        return false;
    }

    private void bathroom(int f, Room r) {
        boolean full = r.kind.equals("bath");
        int s = farWall(f, r);
        List<int[]> run = run(f, r, s, false);
        int fs = (s + 2) & 3;
        int at = 0;
        if (full && run.size() >= 2 && adjacent(run, 0, 1)) {
            int[] a = run.get(0), b = run.get(1);
            boolean in = putAll(f, new int[][] {{a[0], 1, a[1]}, {b[0], 1, b[1]}},
                    new String[] {"home_tub_l" + face(fs), "home_tub_r" + face(fs)});
            int[] ft = in ? STEP[fs] : new int[] {0, 0};
            for (int[] c : new int[][] {a, b}) {
                int x = c[0] + ft[0], z = c[1] + ft[1];
                if (inRoom(f, r, x, z) && occ[x - x0][z - z0][1] == 0 && !path[x - x0][z - z0]) {
                    put(f, x, 1, z, "home_shower_curtain" + face(s));
                }
            }
            if (in) {
                hang(f, a[0], a[1], s, 2, "home_towel");
                at = 2;
            }
        }
        if (run.size() > at) {
            int[] c = run.get(at);
            put(f, c[0], 1, c[1], "home_toilet" + face(fs));
        }
        int[] v = run.size() > at + 1 ? new int[] {run.get(at + 1)[0], run.get(at + 1)[1], fs} : against(f, r, "-");
        if (v != null) {
            put(f, v[0], 1, v[1], "home_vanity" + face(v[2]));
            put(f, v[0], 2, v[1], "home_medicine" + face(v[2]));
        }
        decor(f, r, "home_towel", 2);
        if (cond == House.Condition.LOOTED || cond == House.Condition.RUIN) {
            scatter(f, r, "home_pills", 1);
        }
    }

    /** The one room of a cabin: a bed in the corner, a sofa and the stove for warmth. */
    private void studio(int f, Room r) {
        if (!hasKitchen()) {
            kitchen(f, r);
        }
        int s = farWall(f, r);
        bed(f, r, s, pick("quilt", "blue", "rose"), r.w() >= 6 && r.d() >= 6 ? 2 : 1, true,
                pick("home_picture_lake", "home_picture_family"), rug);
        living(f, r);
        tall(f, r, "home_wardrobe", "home_wardrobe_top");
    }

    private void laundry(int f, Room r) {
        int s = longestWall(f, r);
        List<int[]> run = run(f, r, s, false);
        int fs = (s + 2) & 3;
        if (run.size() >= 2 && adjacent(run, 0, 1)) {
            put(f, run.get(0)[0], 1, run.get(0)[1], "store_washer" + face(fs));
            put(f, run.get(1)[0], 1, run.get(1)[1], "store_dryer" + face(fs));
        }
        tall(f, r, "home_water_heater", "home_water_heater_top");
        against(f, r, "home_garage_shelf");
        corner(f, r, "home_laundry");
        corner(f, r, "home_boxes");
    }

    private void closet(int f, Room r) {
        against(f, r, "home_boxes");
        against(f, r, "home_boxes");
        corner(f, r, "home_laundry");
        tall(f, r, "home_wardrobe", "home_wardrobe_top");
    }

    private void hallway(int f, Room r) {
        // a runner down the hall, the phone by the front door, pictures, banisters at the stairwell
        if (hx0 >= 0 && hx1 > hx0) {
            int za = r.b0 + 1, zb = r.b1 - 1;
            if (f > 0 && sc0 >= 0) {
                za = sz0 + 4;
            }
            rugRect(f, r, hx0, za, hx1, zb, rug);
        }
        if (f == 0) {
            for (int s : new int[] {1, 3}) {
                int x = s == 1 ? r.a1 : r.a0;
                if (wallBehind(f, x, r.b0, s) && occ[x - x0][r.b0 - z0][2] == 0) {
                    hang(f, x, r.b0, s, 2, rng.nextBoolean() ? "home_phone" : "home_mirror");
                    break;
                }
            }
        }
        if (sc0 >= 0 && f == 1) {
            int bx = sc0 == hx0 - 2 ? hx0 : hx1;
            for (int z = sz0 + 1; z <= sz0 + 2; z++) {
                p.set(bx, f * H + 1, z, "home_banister[axis=z]");
                occ[bx - x0][z - z0][1] = 1;
            }
            for (int x = sc0; x <= sc0 + 1; x++) {
                p.set(x, f * H + 1, sz0, "home_banister[axis=x]");
                occ[x - x0][sz0 - z0][1] = 1;
            }
        }
        for (int i = 0; i < 2; i++) {
            decor(f, r, pick("home_picture_lake", "home_picture_family", "home_picture_stitch"), 2);
        }
        radiator(f, r);
    }

    private void light(int f, Room r) {
        if (r.kind.equals("closet")) {
            return;
        }
        int x = (r.a0 + r.a1) / 2, z = (r.b0 + r.b1) / 2;
        if (inRoom(f, r, x, z) && occ[x - x0][z - z0][3] == 0 && cellAt(f, x, z) == ROOM) {
            boolean on = lit > 0 && rng.nextDouble() < lit + 0.2;
            put(f, x, 3, z, on ? "ceiling_light" : "ceiling_light_off");
        }
    }

    // ------------------------------------------------------------------ the lived-in layer

    /** A coat rack and shoes inside the front door. */
    private void entry(int f) {
        int z = uz0;
        Room r = ownerAt(f, doorX, z);
        if (r == null) {
            return;
        }
        boolean rack = false, shoes = false;
        for (int d : new int[] {-1, 1, -2, 2}) {
            int x = doorX + d;
            if (!inRoom(f, r, x, z) || cellAt(f, x, z) != ROOM) {
                continue;
            }
            if (!rack && clear(f, r, x, z) && wallBehind(f, x, z, 0) && rng.nextDouble() < 0.6) {
                rack = put(f, x, 1, z, "home_coat_rack" + face(2));
            } else if (!shoes && occ[x - x0][z - z0][1] == 0 && rng.nextDouble() < 0.7) {
                shoes = put(f, x, 1, z, "home_shoes" + face(2));
            }
        }
    }

    /** What makes a room someone's: the hearth, the piano, plants, the fan, the baby's things. */
    private void extras(int f, Room r) {
        boolean grand = style == House.Style.COLONIAL || style == House.Style.MANSION;
        boolean rural = style == House.Style.FARMHOUSE || style == House.Style.CABIN;
        boolean big = r.w() * r.d() >= 30;
        switch (r.kind) {
            case "living" -> {
                if (rng.nextDouble() < (grand || rural ? 0.75 : 0.3)) {
                    hearth(f, r, style == House.Style.CABIN);
                }
                if (rng.nextDouble() < (style == House.Style.MANSION ? 0.6 : grand ? 0.3 : 0.08)) {
                    piano(f, r);
                }
                plants(f, r, big ? 2 : 1);
                fill(f, r, "home_armchair_" + sofa, "home_bookcase/home_bookcase_top", "home_dresser", "home_nightstand",
                        "home_floor_lamp", "home_boxes", "home_plant_dead");
            }
            case "studio" -> {
                hearth(f, r, true);
                plants(f, r, 1);
                if (rng.nextDouble() < 0.4) {
                    against(f, r, "home_fan");
                }
            }
            case "dining" -> {
                plants(f, r, big ? 2 : 1);
                if (style == House.Style.MANSION && rng.nextDouble() < 0.3) {
                    piano(f, r);
                }
                fill(f, r, "home_dresser", "home_bookcase/home_bookcase_top", "home_chair", "home_floor_lamp", "home_plant_dead");
            }
            case "den" -> {
                if (rng.nextDouble() < 0.35) {
                    against(f, r, "home_fan");
                }
                plants(f, r, 1);
                fill(f, r, "home_bookcase/home_bookcase_top", "home_desk", "home_boxes", "home_garage_shelf", "home_floor_lamp");
            }
            case "bed_master", "bed", "bed_kid" -> {
                boolean kid = r.kind.equals("bed_kid");
                if (kid && rng.nextDouble() < 0.6) {
                    scatter(f, r, "home_teddy", 1);
                }
                if (kid && r.w() * r.d() <= 16 && rng.nextDouble() < 0.3) {
                    against(f, r, "home_crib");
                }
                if (rng.nextDouble() < 0.35) {
                    against(f, r, "home_fan");
                }
                if (rng.nextDouble() < 0.3) {
                    plants(f, r, 1);
                }
                if (kid) {
                    fill(f, r, "home_bookcase/home_bookcase_top", "home_dresser", "home_boxes", "home_laundry", "home_floor_lamp");
                } else {
                    fill(f, r, "home_armchair_" + sofa, "home_dresser", "home_wardrobe/home_wardrobe_top", "home_desk",
                            "home_bookcase/home_bookcase_top", "home_floor_lamp", "home_laundry", "home_boxes");
                }
            }
            case "laundry" -> {
                against(f, r, "home_ironing_board");
                corner(f, r, "home_vacuum");
            }
            case "closet" -> {
                if (rng.nextDouble() < 0.5) {
                    corner(f, r, "home_vacuum");
                }
                if (rng.nextDouble() < 0.3) {
                    against(f, r, "home_ironing_board");
                }
                corner(f, r, "home_boxes");
            }
            case "kitchen" -> {
                boolean messy = cond == House.Condition.LOOTED || cond == House.Condition.RUIN || cond == House.Condition.BOARDED;
                if (messy && rng.nextDouble() < 0.6) {
                    corner(f, r, "home_bin_bags");
                }
            }
            case "hall" -> {
                if (f == 0 && rng.nextDouble() < 0.5) {
                    plants(f, r, 1);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Furniture against the walls until at most half the wall is bare, from a list in the order
     * it's wanted: "low/top" for a two-high piece, a desk comes with its chair.
     */
    private void fill(int f, Room r, String... pieces) {
        int perimeter = 2 * (r.w() + r.d());
        for (int i = 0, misses = 0; misses < pieces.length && bareWall(f, r) * 2 > perimeter; i++) {
            String piece = pieces[i % pieces.length];
            boolean in;
            if (piece.contains("/")) {
                String[] lt = piece.split("/");
                int before = bareWall(f, r);
                tall(f, r, lt[0], lt[1]);
                in = bareWall(f, r) < before;
            } else {
                int[] at = against(f, r, piece);
                in = at != null;
                if (in && piece.equals("home_desk")) {
                    int[] ft = STEP[at[2]];
                    if (clear(f, r, at[0] + ft[0], at[1] + ft[1])) {
                        put(f, at[0] + ft[0], 1, at[1] + ft[1], "home_chair" + face((at[2] + 2) & 3));
                    }
                }
            }
            misses = in ? 0 : misses + 1;
        }
    }

    /** Cells along the room's walls still free to stand something against. */
    private int bareWall(int f, Room r) {
        int n = 0;
        for (int s = 0; s < 4; s++) {
            n += run(f, r, s, false).size();
        }
        return n;
    }

    /** Houseplants in the corners: dead, mostly, nobody having watered them since. */
    private void plants(int f, Room r, int n) {
        for (int i = 0; i < n; i++) {
            corner(f, r, cond == House.Condition.SHUT && rng.nextDouble() < 0.3 ? "home_plant" : "home_plant_dead");
        }
    }

    /**
     * A fireplace against an outside wall, the chimney breast going up to the ceiling; in a cabin
     * (or with stove) a wood stove and its pipe.
     */
    private void hearth(int f, Room r, boolean stove) {
        if (flat) {
            return;  // no chimney to take the smoke
        }
        String low = stove ? "home_wood_stove" : "home_fireplace", up = stove ? "home_stove_pipe" : "home_chimney";
        // the end walls first, where the chimney can go up the gable outside; the front last, behind the porch
        boolean east = rng.nextBoolean();
        int[] order = {east ? 1 : 3, east ? 3 : 1, 2, 0};
        for (int s : order) {
            List<int[]> run = run(f, r, s, false);
            int fs = (s + 2) & 3;
            for (int k = 0; k < run.size(); k++) {
                // from the middle of the wall outward
                int i = run.size() / 2 + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
                if (i < 0 || i >= run.size()) {
                    continue;
                }
                int[] c = run.get(i);
                byte out = cellAt(f, c[0] + STEP[s][0], c[1] + STEP[s][1]);
                if ((out != LINING && out != OUT) || window(f, c, s) || occ[c[0] - x0][c[1] - z0][2] != 0
                        || occ[c[0] - x0][c[1] - z0][3] != 0 || !frontFree(c, fs)) {
                    continue;
                }
                if (putAll(f, new int[][] {{c[0], 1, c[1]}, {c[0], 2, c[1]}, {c[0], 3, c[1]}},
                        new String[] {low + face(fs), up + face(fs), up + face(fs)})) {
                    hearths.add(new int[] {c[0], c[1], f, s, stove ? 1 : 0});
                    return;
                }
            }
        }
    }

    /** An upright piano against a wall, a chair drawn up to it. */
    private void piano(int f, Room r) {
        int s0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            List<int[]> run = run(f, r, s, false);
            int fs = (s + 2) & 3;
            for (int i = 0; i + 1 < run.size(); i++) {
                int[] a = run.get(i), b = run.get(i + 1);
                if (!adjacent(run, i, 1) || window(f, a, s) || window(f, b, s)
                        || !clear(f, r, a[0] + STEP[fs][0], a[1] + STEP[fs][1])) {
                    continue;
                }
                if (putAll(f, new int[][] {{a[0], 1, a[1]}, {b[0], 1, b[1]}},
                        new String[] {"home_piano_l" + face(fs), "home_piano_r" + face(fs)})) {
                    int[] ft = STEP[fs];
                    if (freeFloor(f, r, a[0] + ft[0], a[1] + ft[1])) {
                        put(f, a[0] + ft[0], 1, a[1] + ft[1], "home_chair" + face(s));
                    }
                    return;
                }
            }
        }
    }

    /**
     * What sits on every surface: canisters, cans, bottles, the radio and books on the worktops,
     * dishes and cans left on the table, a lamp and a book on the dresser and the desk.
     */
    private void surfaces(int f) {
        boolean radio = false;
        boolean holdout = cond == House.Condition.BOARDED;
        for (Room r : rooms.get(f)) {
            boolean lamp = false;
            for (int x = r.a0; x <= r.a1; x++) {
                for (int z = r.b0; z <= r.b1; z++) {
                    if (!inRoom(f, r, x, z)) {
                        continue;
                    }
                    String on = what[x - x0][z - z0][1];
                    if (on == null || occ[x - x0][z - z0][2] != 0) {
                        continue;
                    }
                    String put = null;
                    if (on.startsWith("home_counter_") || on.startsWith("home_drawers_")) {
                        if (rng.nextDouble() < 0.45) {
                            put = pick("home_jars", "home_cans", "home_bottles", "home_books_stack", "home_dishes",
                                    radio ? "home_cans" : "home_radio");
                        }
                    } else if (on.equals("home_table")) {
                        if (rng.nextDouble() < (holdout ? 0.8 : 0.45)) {
                            put = holdout ? pick("home_cans_low", "home_bottles_low", "home_cans_low")
                                    : pick("home_dishes_low", "home_dishes_low", "home_cans_low", "home_bottles_low",
                                    "home_books_stack_low");
                        }
                    } else if (on.equals("home_dresser") || on.equals("home_desk")) {
                        if (rng.nextDouble() < 0.6) {
                            put = lamp ? pick("home_books_stack_low", radio ? "home_books_stack_low" : "home_radio_low")
                                    : "home_lamp_table_low";
                        }
                    }
                    if (put == null) {
                        continue;
                    }
                    int fs = facingAt(f, x, z);
                    if (put(f, x, 2, z, put + face(fs))) {
                        radio |= put.startsWith("home_radio");
                        lamp |= put.startsWith("home_lamp");
                    }
                }
            }
        }
    }

    /** The way a cell's piece faces, read back from the walls round it (into the room). */
    private int facingAt(int f, int x, int z) {
        for (int s = 0; s < 4; s++) {
            if (wallBehind(f, x, z, s)) {
                return (s + 2) & 3;
            }
        }
        return rng.nextInt(4);
    }

    // ------------------------------------------------------------------ the story

    private void story(int f) {
        List<Room> list = rooms.get(f);
        boolean messy = cond == House.Condition.LOOTED || cond == House.Condition.RUIN;
        for (Room r : list) {
            if (messy) {
                scatter(f, r, "paper", 1 + rng.nextInt(2));
                if (rng.nextDouble() < 0.4) {
                    scatter(f, r, "glass_shards", 1);
                }
                if (r.kind.startsWith("bed") && rng.nextDouble() < 0.7) {
                    scatter(f, r, "home_clothes_pile", 1 + rng.nextInt(2));
                }
                if (rng.nextDouble() < 0.2) {
                    scatter(f, r, pick("home_stain_brown", "home_stain_dark", "blood_splat"), 1);
                }
                if (rng.nextDouble() < 0.12) {
                    decor(f, r, "home_blood_hand", 2);
                }
            } else if (rng.nextDouble() < 0.08) {
                scatter(f, r, pick("home_stain_brown", "home_stain_dark"), 1);
            }
        }
        if (f != 0) {
            return;
        }
        Room hall = null, living = null;
        for (Room r : list) {
            if (r.kind.equals("hall")) {
                hall = r;
            } else if (r.kind.equals("living")) {
                living = r;
            }
        }
        if (messy && hall != null && rng.nextDouble() < 0.3) {
            // something dragged from the front door down the hall
            int len = 3 + rng.nextInt(4);
            int x = doorX, z = uz0 + 1;
            for (int i = 0; i < len && z <= uz1; i++, z++) {
                if (occ[x - x0][z - z0][1] == 0 && cellAt(f, x, z) == ROOM) {
                    put(f, x, 1, z, "home_blood_drag[facing=south]");
                }
            }
            if (z <= uz1 && occ[x - x0][z - z0][1] == 0 && cellAt(f, x, z) == ROOM) {
                put(f, x, 1, z, "home_blood_pool");
            }
        }
        if (cond == House.Condition.BOARDED && living != null) {
            // someone held out in here: a mattress, candles, a note for whoever came looking
            int[] c = freeCell(f, living);
            if (c != null) {
                put(f, c[0], 1, c[1], "home_mattress_floor[facing=" + DIRS[rng.nextInt(4)] + "]");
                int[] d = freeCell(f, living);
                if (d != null) {
                    put(f, d[0], 1, d[1], "home_candles");
                }
            }
            decor(f, living, pick("home_note_stadium", "home_note_basement", "home_note_mom"), 2);
            if (hall != null && rng.nextDouble() < 0.4) {
                sprayed(f, hall, rng.nextBoolean() ? "help" : "dead");
            }
        }
        if ((cond == House.Condition.SHUT || messy) && rng.nextDouble() < 0.15 && !list.isEmpty()) {
            body(f, list.get(rng.nextInt(list.size())));
        }
        if (cond == House.Condition.SHUT && rng.nextDouble() < 0.15 && hall != null) {
            decor(f, hall, pick("home_note_stadium", "home_note_mom", "home_note_bitten"), 2);
        }
    }

    private void body(int f, Room r) {
        for (int tries = 0; tries < 20; tries++) {
            int x = r.a0 + rng.nextInt(r.w()), z = r.b0 + rng.nextInt(r.d());
            int fc = rng.nextInt(4);
            int[] ft = STEP[fc];
            int fx = x + ft[0], fz = z + ft[1];
            if (inRoom(f, r, x, z) && inRoom(f, r, fx, fz) && occ[x - x0][z - z0][1] == 0 && occ[fx - x0][fz - z0][1] == 0
                    && cellAt(f, x, z) == ROOM && cellAt(f, fx, fz) == ROOM) {
                put(f, x, 1, z, "home_corpse_head" + face(fc));
                put(f, fx, 1, fz, "home_corpse_foot" + face(fc));
                int[] sv = side(fc);
                int bx = x + sv[0], bz = z + sv[1];
                if (inRoom(f, r, bx, bz) && occ[bx - x0][bz - z0][1] == 0 && cellAt(f, bx, bz) == ROOM) {
                    put(f, bx, 1, bz, "home_blood_pool");
                }
                return;
            }
        }
    }

    /** Two-block spray paint on a wall of the room, read left to right. */
    private void sprayed(int f, Room r, String word) {
        for (int s = 0; s < 4; s++) {
            List<int[]> run = run(f, r, s, true);
            for (int i = 0; i + 1 < run.size(); i++) {
                int[] a = run.get(i), b = run.get(i + 1);
                // decor faces the wall; its parts run along that facing's side vector
                int[] sv = side(s);
                if (b[0] != a[0] + sv[0] || b[1] != a[1] + sv[1]) {
                    int[] t = a;
                    a = b;
                    b = t;
                }
                if (b[0] != a[0] + sv[0] || b[1] != a[1] + sv[1]) {
                    continue;
                }
                if (hangable(f, a[0], a[1], s, 2) && hangable(f, b[0], b[1], s, 2)) {
                    put(f, a[0], 2, a[1], "home_spray_" + word + "_0" + face(s));
                    put(f, b[0], 2, b[1], "home_spray_" + word + "_1" + face(s));
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ placing

    /** The cells of a room along wall s, in the order a multi-block piece facing away from it runs. */
    private List<int[]> run(int f, Room r, int s, boolean any) {
        List<int[]> out = new ArrayList<>();
        int fs = (s + 2) & 3;
        int[] sv = side(fs);
        int n = s == 0 || s == 2 ? r.w() : r.d();
        for (int i = 0; i < n; i++) {
            int x, z;
            if (s == 0 || s == 2) {
                z = s == 0 ? r.b0 : r.b1;
                x = sv[0] > 0 ? r.a0 + i : r.a1 - i;
            } else {
                x = s == 3 ? r.a0 : r.a1;
                z = sv[1] > 0 ? r.b0 + i : r.b1 - i;
            }
            if (any || freeFloor(f, r, x, z) && wallBehind(f, x, z, s) && !keep[x - x0][z - z0]) {
                out.add(new int[] {x, z});
            }
        }
        return out;
    }

    /** True when run cells i and i + j are j apart (nothing skipped between them). */
    private static boolean adjacent(List<int[]> run, int i, int j) {
        if (i + j >= run.size()) {
            return false;
        }
        int[] a = run.get(i), b = run.get(i + j);
        return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]) == j;
    }

    /** The wall opposite the room's first way in. */
    private int farWall(int f, Room r) {
        if (r.ways.isEmpty()) {
            return longestWall(f, r);
        }
        int s = (r.ways.get(0)[2] + 2) & 3;
        return run(f, r, s, false).size() >= 2 ? s : longestWall(f, r);
    }

    private int longestWall(int f, Room r) {
        int best = 2, n = -1;
        for (int s = 0; s < 4; s++) {
            int k = run(f, r, s, false).size();
            if (k > n) {
                best = s;
                n = k;
            }
        }
        return best;
    }

    /** A piece against any free stretch of wall; returns {x, z, facing} or null. "-" only finds the spot. */
    private int[] against(int f, Room r, String name) {
        int s0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            List<int[]> run = run(f, r, s, false);
            if (run.isEmpty()) {
                continue;
            }
            int[] c = run.get(rng.nextInt(run.size()));
            int fs = (s + 2) & 3;
            if (name.equals("-") || frontFree(c, fs) && put(f, c[0], 1, c[1], name + face(fs))) {
                return new int[] {c[0], c[1], fs};
            }
        }
        return null;
    }

    /** A two-high piece against a wall clear of windows. */
    private void tall(int f, Room r, String low, String top) {
        int s0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            for (int[] c : run(f, r, s, false)) {
                int fs = (s + 2) & 3;
                if (!window(f, c, s) && occ[c[0] - x0][c[1] - z0][2] == 0 && frontFree(c, fs)
                        && putAll(f, new int[][] {{c[0], 1, c[1]}, {c[0], 2, c[1]}}, new String[] {low + face(fs), top + face(fs)})) {
                    return;
                }
            }
        }
    }

    /** Two tall pieces side by side (a pair of bookcases). */
    private void pair(int f, Room r, String low, String top) {
        int s0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            List<int[]> run = run(f, r, s, false);
            for (int i = 0; i + 1 < run.size(); i++) {
                int[] a = run.get(i), b = run.get(i + 1);
                int fs = (s + 2) & 3;
                if (adjacent(run, i, 1) && !window(f, a, s) && !window(f, b, s) && frontFree(a, fs) && frontFree(b, fs)
                        && putAll(f, new int[][] {{a[0], 1, a[1]}, {a[0], 2, a[1]}, {b[0], 1, b[1]}, {b[0], 2, b[1]}},
                        new String[] {low + face(fs), top + face(fs), low + face(fs), top + face(fs)})) {
                    return;
                }
            }
        }
    }

    /** A piece in a free corner of the room. */
    private void corner(int f, Room r, String name) {
        int[][] cs = {{r.a0, r.b0}, {r.a1, r.b0}, {r.a0, r.b1}, {r.a1, r.b1}};
        int k0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int[] c = cs[(k0 + k) & 3];
            if (freeFloor(f, r, c[0], c[1]) && !keep[c[0] - x0][c[1] - z0] && put(f, c[0], 1, c[1], name + face(rng.nextInt(4)))) {
                return;
            }
        }
    }

    private void radiator(int f, Room r) {
        for (int s = 0; s < 4; s++) {
            for (int[] c : run(f, r, s, false)) {
                if (window(f, c, s) && put(f, c[0], 1, c[1], "home_radiator" + face((s + 2) & 3))) {
                    return;
                }
            }
        }
    }

    /** A table of n blocks (1, 2 or 4) out in the room with chairs round it. */
    private void table(int f, Room r, int n, int chairs) {
        int tw = n == 4 ? 2 : n == 2 && r.w() >= r.d() ? 2 : 1, td = n / tw;
        int bx = -1, bz = -1, best = Integer.MAX_VALUE;
        for (int x = r.a0 + 1; x + tw - 1 <= r.a1 - 1; x++) {
            for (int z = r.b0 + 1; z + td - 1 <= r.b1 - 1; z++) {
                boolean ok = true;
                for (int i = -1; i <= tw && ok; i++) {
                    for (int j = -1; j <= td && ok; j++) {
                        int cx = x + i, cz = z + j;
                        boolean core = i >= 0 && i < tw && j >= 0 && j < td;
                        if (core ? !freeFloor(f, r, cx, cz) || keep[cx - x0][cz - z0]
                                : !inRoom(f, r, cx, cz) || occ[cx - x0][cz - z0][1] != 0 || keep[cx - x0][cz - z0]) {
                            ok = false;
                        }
                    }
                }
                int score = Math.abs(2 * x + tw - r.a0 - r.a1 - 1) + Math.abs(2 * z + td - r.b0 - r.b1 - 1);
                if (ok && score < best) {
                    best = score;
                    bx = x;
                    bz = z;
                }
            }
        }
        if (bx < 0) {
            return;
        }
        boolean messy = cond == House.Condition.LOOTED || cond == House.Condition.RUIN;
        int[][] at = new int[tw * td][];
        String[] blocks = new String[tw * td];
        for (int i = 0; i < tw; i++) {
            for (int j = 0; j < td; j++) {
                at[i * td + j] = new int[] {bx + i, 1, bz + j};
                blocks[i * td + j] = "home_table";
            }
        }
        if (!putAll(f, at, blocks)) {
            return;
        }
        int seated = 0;
        for (int i = -1; i <= tw && seated < chairs; i++) {
            for (int j = -1; j <= td && seated < chairs; j++) {
                boolean edgeX = i == -1 || i == tw, edgeZ = j == -1 || j == td;
                if (edgeX == edgeZ) {
                    continue;  // corners, and the table itself
                }
                int cx = bx + i, cz = bz + j;
                if (path[cx - x0][cz - z0] || occ[cx - x0][cz - z0][1] != 0) {
                    continue;
                }
                int toward = i == -1 ? 1 : i == tw ? 3 : j == -1 ? 2 : 0;
                put(f, cx, 1, cz, (messy && rng.nextDouble() < 0.35 ? "home_chair_tipped" : "home_chair") + face(toward));
                seated++;
            }
        }
    }

    /** A bordered rug between the sofa and the television. */
    private void rugBetween(int f, Room r, int[] mid, int fs, int depth, int len) {
        if (depth < 4) {
            return;
        }
        int[] ft = STEP[fs], sv = side(fs);
        int half = len >= 3 ? 1 : 1;
        int ax = mid[0] + ft[0] - sv[0] * half, az = mid[1] + ft[1] - sv[1] * half;
        int bx = mid[0] + ft[0] * (depth - 2) + sv[0] * half, bz = mid[1] + ft[1] * (depth - 2) + sv[1] * half;
        rugRect(f, r, Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz), rug);
    }

    /** A rug over a rectangle, with its border round the edge; furniture already there stays. */
    private void rugRect(int f, Room r, int ax, int az, int bx, int bz, String colour) {
        if (bx - ax < 1 || bz - az < 1) {
            return;
        }
        for (int x = ax; x <= bx; x++) {
            for (int z = az; z <= bz; z++) {
                if (!inRoom(f, r, x, z) || occ[x - x0][z - z0][1] != 0 || cellAt(f, x, z) != ROOM) {
                    continue;
                }
                boolean n = z == az, s = z == bz, w = x == ax, e = x == bx;
                String block;
                if ((n || s) && (w || e)) {
                    block = "home_rug_" + colour + "_corner[facing=" + (n ? (w ? "north" : "east") : (e ? "south" : "west")) + "]";
                } else if (n || s || w || e) {
                    block = "home_rug_" + colour + "_edge[facing=" + (n ? "north" : s ? "south" : w ? "west" : "east") + "]";
                } else {
                    block = "home_rug_" + colour;
                }
                put(f, x, 1, z, block);
            }
        }
    }

    /** Wall decor at a row, on any wall of the room with room for it. */
    private void decor(int f, Room r, String name, int row) {
        int s0 = rng.nextInt(4);
        for (int k = 0; k < 4; k++) {
            int s = (s0 + k) & 3;
            List<int[]> run = run(f, r, s, true);
            if (run.isEmpty()) {
                continue;
            }
            int i0 = rng.nextInt(run.size());
            for (int i = 0; i < run.size(); i++) {
                int[] c = run.get((i0 + i) % run.size());
                if (hangable(f, c[0], c[1], s, row)) {
                    put(f, c[0], row, c[1], name + face(s));
                    return;
                }
            }
        }
    }

    private void hang(int f, int x, int z, int s, int row, String name) {
        if (hangable(f, x, z, s, row)) {
            put(f, x, row, z, name + face(s));
        }
    }

    private boolean hangable(int f, int x, int z, int s, int row) {
        return inside(x, z) && wallBehind(f, x, z, s) && (winMask[f][x - x0][z - z0] & (1 << s)) == 0
                && occ[x - x0][z - z0][row] == 0 && cellAt(f, x, z) == ROOM;
    }

    /** Floor litter (decals and the odd bag) on any free cell, paths included. */
    private void scatter(int f, Room r, String name, int n) {
        for (int i = 0; i < n; i++) {
            for (int tries = 0; tries < 8; tries++) {
                int x = r.a0 + rng.nextInt(r.w()), z = r.b0 + rng.nextInt(r.d());
                if (occ[x - x0][z - z0][1] == 0 && cellAt(f, x, z) == ROOM) {
                    put(f, x, 1, z, name + face(rng.nextInt(4)));
                    break;
                }
            }
        }
    }

    private int[] freeCell(int f, Room r) {
        for (int tries = 0; tries < 20; tries++) {
            int x = r.a0 + 1 + rng.nextInt(Math.max(1, r.w() - 2)), z = r.b0 + 1 + rng.nextInt(Math.max(1, r.d() - 2));
            if (inRoom(f, r, x, z) && occ[x - x0][z - z0][1] == 0 && cellAt(f, x, z) == ROOM) {
                return new int[] {x, z};
            }
        }
        return null;
    }

    /**
     * Sets a block in a room row. A piece on the floor that you can't step over goes in only if
     * every way into its room can still reach the others; returns false when it didn't.
     */
    private boolean put(int f, int x, int row, int z, String block) {
        return putAll(f, new int[][] {{x, row, z}}, new String[] {block});
    }

    /** Several blocks as one piece (a sofa, a bed): all of them, or none. */
    private boolean putAll(int f, int[][] at, String[] blocks) {
        Room r = null;
        boolean blocks1 = false;
        for (int i = 0; i < at.length; i++) {
            int[] c = at[i];
            if (inside(c[0], c[2]) && c[1] == 1 && noTable[c[0] - x0][c[2] - z0] && TABLES.contains(base(blocks[i]))) {
                return false;
            }
        }
        for (int i = 0; i < at.length; i++) {
            int[] c = at[i];
            if (!inside(c[0], c[2])) {
                continue;
            }
            if (c[1] == 1 && blocking(blocks[i])) {
                blocks1 = true;
                r = owner[f][c[0] - x0][c[2] - z0];
                occ[c[0] - x0][c[2] - z0][1] = 1;
            }
        }
        if (blocks1 && r != null && !connected(f, r)) {
            for (int[] c : at) {
                if (inside(c[0], c[2]) && c[1] == 1) {
                    occ[c[0] - x0][c[2] - z0][1] = 0;
                }
            }
            return false;
        }
        for (int i = 0; i < at.length; i++) {
            int[] c = at[i];
            String block = blocks[i];
            int open = block.indexOf('[');
            if (open > 0 && PLAIN.contains(block.substring(0, open))) {
                block = block.substring(0, open);
            }
            p.set(c[0], f * H + c[1], c[2], block);
            if (inside(c[0], c[2]) && occ[c[0] - x0][c[2] - z0][c[1]] == 0) {
                occ[c[0] - x0][c[2] - z0][c[1]] = 2;
            }
            if (inside(c[0], c[2]) && c[1] < H) {
                what[c[0] - x0][c[2] - z0][c[1]] = open > 0 ? block.substring(0, open) : block;
            }
            int fi = block.indexOf("facing=");
            if (c[1] == 1 && fi > 0 && FRONTED.contains(fronted(base(block)))) {
                int d = List.of(DIRS).indexOf(block.substring(fi + 7).replaceAll("[,\\]].*", ""));
                if (d >= 0 && inside(c[0] + STEP[d][0], c[2] + STEP[d][1])) {
                    keep[c[0] + STEP[d][0] - x0][c[2] + STEP[d][1] - z0] = true;
                }
            }
        }
        return true;
    }

    private static String base(String block) {
        int open = block.indexOf('[');
        return open > 0 ? block.substring(0, open) : block;
    }

    /** A fitted piece's family name: home_dishwasher_oak and the like count as their kind. */
    private static String fronted(String b) {
        return b.startsWith("home_dishwasher") ? "home_stove" : b;
    }

    /** Whether the cell in front of a piece at c facing fs is clear of anything you can't step over. */
    private boolean frontFree(int[] c, int fs) {
        int fx = c[0] + STEP[fs][0], fz = c[1] + STEP[fs][1];
        return !inside(fx, fz) || occ[fx - x0][fz - z0][1] != 1;
    }

    /** Whether a piece stands in the way: rugs, litter, bodies and the like can be walked over. */
    private static boolean blocking(String block) {
        String b = block.indexOf('[') > 0 ? block.substring(0, block.indexOf('[')) : block;
        return !(b.startsWith("home_rug") || b.startsWith("home_stain") || b.startsWith("home_blood")
                || b.startsWith("home_corpse") || b.equals("paper") || b.equals("glass_shards") || b.equals("blood_splat")
                || b.equals("scorch") || b.equals("home_pills") || b.equals("home_clothes_pile")
                || b.equals("home_shower_curtain") || b.equals("home_candles") || b.equals("home_mattress_floor")
                || b.equals("home_shoes") || b.equals("home_teddy"));
    }

    private boolean freeFloor(int f, Room r, int x, int z) {
        return inRoom(f, r, x, z) && cellAt(f, x, z) == ROOM && occ[x - x0][z - z0][1] == 0 && !path[x - x0][z - z0];
    }

    /** Free floor that isn't in front of a fridge, a wardrobe or the like. */
    private boolean clear(int f, Room r, int x, int z) {
        return freeFloor(f, r, x, z) && !keep[x - x0][z - z0];
    }

    private boolean inRoom(int f, Room r, int x, int z) {
        return inside(x, z) && owner[f][x - x0][z - z0] == r;
    }

    private boolean wallBehind(int f, int x, int z, int s) {
        byte t = cellAt(f, x + STEP[s][0], z + STEP[s][1]);
        return t == LINING || t == WALL || t == OUT;
    }

    private boolean window(int f, int[] c, int s) {
        return (winMask[f][c[0] - x0][c[1] - z0] & (1 << s)) != 0;
    }

    private boolean walkable(int f, int x, int z) {
        if (!inside(x, z) || cellAt(f, x, z) != ROOM) {
            return false;
        }
        // upstairs, the banister cells round the stairwell
        if (f > 0 && sc0 >= 0) {
            int bx = sc0 == hx0 - 2 ? hx0 : hx1;
            if (z == sz0 && (x == sc0 || x == sc0 + 1) || x == bx && z >= sz0 + 1 && z <= sz0 + 2) {
                return false;
            }
        }
        return true;
    }

    private byte cellAt(int f, int x, int z) {
        return inside(x, z) ? cell[f][x - x0][z - z0] : OUT;
    }

    private Room ownerAt(int f, int x, int z) {
        return inside(x, z) ? owner[f][x - x0][z - z0] : null;
    }

    /** The room a wall cell faces (straight across first, then diagonally). */
    private Room near(int f, int x, int z) {
        for (int[] s : STEP) {
            Room r = ownerAt(f, x + s[0], z + s[1]);
            if (r != null) {
                return r;
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                Room r = ownerAt(f, x + dx, z + dz);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    private boolean inside(int x, int z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
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

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }
}
