package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;

/**
 * What fills {@link Arena}: the event level's rooms round the corridor under the bowl (the
 * resurfacer's garage and the dock, the relief depot, the ice plant, the cold room the shelter
 * made its morgue, the commissary, the officials', the equipment room, both locker rooms, the
 * workshop, first aid and the isolation ward, the press room, security), the stands and the
 * restrooms along the concourse, the turnstiles in the halls, the camps in the high corners, and
 * the shelter on the plywood over the ice.
 */
final class ArenaRooms {
    private final Arena a;

    ArenaRooms(Arena a) {
        this.a = a;
    }

    private static final int EV = Arena.EV, CONC = Arena.CONC, UP = Arena.UP, X = Arena.X, Z = Arena.Z;

    /** The resurfacer's cells (x, y, z from its back corner, nose east), from tools/city/arena.py cells. */
    static final String RESURF = "0,0,0;0,0,1;0,1,0;0,1,1;1,0,0;1,0,1;1,1,0;1,1,1;2,0,0;2,0,1;2,1,0;2,1,1;2,2,0;2,2,1;3,0,0;3,0,1;3,1,0;3,1,1";

    /** An event-level room: its rectangle (clipped to the band beyond the corridor), what it is. */
    private record Room(String kind, int x0, int x1, int z0, int z1) {
        int cx() {
            return (x0 + x1) / 2;
        }

        int cz() {
            return (z0 + z1) / 2;
        }

        boolean has(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    private static final Room[] ROOMS = {
        new Room("garage", 29, 55, 6, 62), new Room("storage", 29, 55, 69, 125),
        new Room("iceplant", 57, 75, 6, 52), new Room("cold", 77, 95, 6, 30), new Room("kitchen", 97, 115, 6, 30),
        new Room("officials", 117, 135, 6, 30), new Room("equipment", 137, 155, 6, 52),
        new Room("home", 157, 182, 6, 62), new Room("visitors", 157, 182, 69, 125),
        new Room("workshop", 57, 75, 80, 125), new Room("aid", 77, 95, 101, 125), new Room("isolation", 97, 115, 101, 125),
        new Room("press", 117, 135, 101, 125), new Room("security", 137, 155, 80, 125)};
    private static final int[] PARTS = {56, 76, 96, 116, 136, 156};

    void build() {
        eventLevel();
        concourse();
        upperLevel();
        shelter();
    }

    // ------------------------------------------------------------------ the event level

    /** In the band of rooms beyond the corridor (not the tunnels). */
    private static boolean roomZone(int x, int z) {
        if (!Arena.inBox(x, z) || Arena.band(x, z) < 22 || Arena.tunnel(x, z)) {
            return false;
        }
        for (int[] c : Arena.CORES) {
            if (x >= c[0] && x <= c[0] + 4 && z >= c[1] && z <= c[1] + 9) {
                return false;
            }
        }
        return true;
    }

    /** The underside over a room cell: the last rows' treads, else the concourse slab. */
    private static int ceil(int x, int z) {
        int b = Arena.band(x, z);
        return b <= Arena.LOW_LAST ? Arena.tread(b) : CONC;
    }

    private boolean open(int x, int z) {
        if (!roomZone(x, z) || !a.empty(x, EV + 1, z) || !a.empty(x, EV + 2, z)) {
            return false;
        }
        for (int[] s : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            String n = a.get(x + s[0], EV + 1, z + s[1]);
            if (n != null && n.contains("door") || a.empty(x + s[0], EV + 1, z + s[1]) && !roomZone(x + s[0], z + s[1])
                    && Arena.inBox(x + s[0], z + s[1]) && !Arena.tunnelWall(x + s[0], z + s[1])) {
                return false;
            }
        }
        return true;
    }

    /** A concourse cell with nothing near it that a cot would block: a door, a stair, a turnstile. */
    private boolean lobby(int x, int z) {
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                String n = a.get(x + i, CONC + 1, z + j);
                if (n != null && (n.contains("door") || n.contains("stair") || n.contains("step") || n.contains("turnstile"))) {
                    return false;
                }
            }
        }
        return true;
    }

    private void eventLevel() {
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                if (Arena.band(x, z) < 22) {
                    continue;
                }
                boolean part = false;
                for (int px : PARTS) {
                    part |= x == px;
                }
                boolean tw = Arena.tunnelWall(x, z);
                if (part && !Arena.tunnel(x, z) || tw) {
                    a.fill(x, EV + 1, z, x, ceil(x, z) - 1, z, Arena.WALL);
                }
            }
        }
        // the tunnels' doors: the garage's wide mouth, the depot's door, the locker rooms' doubles
        a.fill(40, EV + 1, Arena.TZ0 - 1, 43, EV + 4, Arena.TZ0 - 1, "air");
        a.door(46, EV, Arena.TZ1 + 1, Doors.STEEL, "south");
        a.doors(165, EV, Arena.TZ0 - 1, Doors.STEEL, "north");
        a.doors(165, EV, Arena.TZ1 + 1, Doors.STEEL, "south");
        a.sign("resurfacer", 40, EV + 6, Arena.TZ0 - 1, "south");
        a.sign("home", 164, EV + 4, Arena.TZ0, "south");
        a.sign("visitors", 165, EV + 4, Arena.TZ1, "north");
        for (Room r : ROOMS) {
            corridorDoor(r);
            switch (r.kind()) {
                case "garage" -> garage(r);
                case "storage" -> depot(r);
                case "iceplant" -> icePlant(r);
                case "cold" -> morgue(r);
                case "kitchen" -> kitchen(r);
                case "officials" -> officials(r);
                case "equipment" -> equipment(r);
                case "home", "visitors" -> lockers(r, r.kind().equals("home"));
                case "workshop" -> workshop(r);
                case "aid" -> aid(r);
                case "isolation" -> isolation(r);
                case "press" -> press(r);
                default -> security(r);
            }
            if (TALL.contains(r.kind())) {
                lights(r);
            } else {
                dropCeiling(r);
            }
            litter(r, EV, 0.04);
        }
        // the corridor round: its lights, its mess
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                int b = Arena.band(x, z);
                if (b == 18 && (x + z) % 7 == 0 && !Arena.tunnel(x, z)) {
                    a.set(x, Arena.tread(b) - 1, z, a.power && a.chance() < 0.3 ? "ceiling_light" : "ceiling_light_off");
                }
                if (b >= Arena.COR0 && b <= Arena.COR1 && a.empty(x, EV + 1, z) && a.chance() < 0.03) {
                    a.set(x, EV + 1, z, a.any("paper", "blood_splat", "trash_bag[facing=" + a.dir() + "]", "st_stretcher[facing=" + a.dir() + "]"));
                }
            }
        }
    }

    /** A door in the corridor's outer wall (band 21) into the room, as near its middle as the wall runs. */
    private void corridorDoor(Room r) {
        int best = -1, bx = 0, bz = 0;
        for (int x = r.x0() + 1; x < r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (Arena.band(x, z) != 21 || Arena.tunnelWall(x, z) || Arena.tunnel(x, z)) {
                    continue;
                }
                String f = Arena.in(x, z);
                int[] s = step(x, z, f);
                if (Arena.band(x + s[0], z + s[1]) != 20 || Arena.band(x - s[0], z - s[1]) != 22) {
                    continue;
                }
                int dd = Math.abs(x - r.cx()) + Math.abs(z - r.cz());
                if (best < 0 || dd < best) {
                    best = dd;
                    bx = x;
                    bz = z;
                }
            }
        }
        if (best < 0) {
            return;
        }
        String in = Arena.in(bx, bz);
        a.door(bx, EV, bz, Doors.STEEL, in);
        String key = switch (r.kind()) {
            case "garage" -> "dock";
            case "cold" -> "morgue";
            case "aid" -> "aid";
            case "home", "visitors", "officials", "iceplant", "kitchen", "equipment", "isolation", "press", "security", "storage" -> r.kind();
            default -> "workshop";
        };
        // the sign over it on the corridor side, where the wall runs on straight under it
        int[] st = step(bx, bz, in);
        int sx = bx + st[0], sz = bz + st[1];
        boolean alongX = in.equals("north") || in.equals("south");
        int ex = alongX ? sx + 1 : sx, ez = alongX ? sz : sz + 1;
        if (Arena.band(sx, sz) == 20 && Arena.band(ex, ez) == 20 && Arena.band(alongX ? bx + 1 : bx, alongX ? bz : bz + 1) == 21) {
            a.sign(key, sx, EV + 4, sz, in);
        }
    }

    private static int[] step(int x, int z, String f) {
        return switch (f) {
            case "north" -> new int[] {0, -1};
            case "south" -> new int[] {0, 1};
            case "east" -> new int[] {1, 0};
            default -> new int[] {-1, 0};
        };
    }

    /** The rooms kept to the slab (machines, stock); the rest have a drop ceiling four over the floor. */
    private static final java.util.Set<String> TALL = java.util.Set.of("garage", "storage", "iceplant", "workshop");

    private void dropCeiling(Room r) {
        int y = EV + 5;
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (roomZone(x, z) && a.empty(x, y, z)) {
                    boolean lamp = Math.floorMod(x, 4) == 1 && Math.floorMod(z, 4) == 1;
                    a.set(x, y, z, lamp ? (a.power && a.chance() < 0.3 ? "fit_troffer_on" : "fit_troffer") + "[axis=x]" : "fit_ceiling");
                }
            }
        }
    }

    private void lights(Room r) {
        for (int x = r.x0() + 2; x <= r.x1() - 1; x += 5) {
            for (int z = r.z0() + 2; z <= r.z1() - 1; z += 5) {
                if (roomZone(x, z) && a.empty(x, ceil(x, z) - 1, z)) {
                    a.set(x, ceil(x, z) - 1, z, a.power && a.chance() < 0.25 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
    }

    private void litter(Room r, int y, double density) {
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (roomZone(x, z) && a.empty(x, y + 1, z) && a.chance() < density) {
                    double q = a.chance();
                    a.set(x, y + 1, z, q < 0.4 ? "paper" : q < 0.6 ? "blood_splat" : q < 0.75 ? "trash_bag[facing=" + a.dir() + "]"
                            : q < 0.85 ? "glass_shards" : "home_blood_pool");
                }
            }
        }
    }

    /** Along a room's walls: cells that are open with a wall (or anything solid) on the given side. */
    private void alongWalls(Room r, java.util.function.BiConsumer<int[], String> put, int every) {
        int k = 0;
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (!open(x, z)) {
                    continue;
                }
                for (String f : new String[] {"north", "south", "east", "west"}) {
                    int[] s = step(x, z, f);
                    String n = a.get(x + s[0], EV + 1, z + s[1]);
                    if (n != null && (n.equals(Arena.WALL) || n.equals(Arena.FILL)) && doorClear(x, z)) {
                        if (k++ % every == 0) {
                            put.accept(new int[] {x, z}, Arena.opposite(f));
                        }
                        break;
                    }
                }
            }
        }
    }

    /** Not in front of a door or opening. */
    private boolean doorClear(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                String s = a.get(x + dx, EV + 1, z + dz);
                if (s != null && (s.contains("door") || !roomZone(x + dx, z + dz) && a.empty(x + dx, EV + 1, z + dz) && Math.abs(dx) + Math.abs(dz) == 1)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void floor(Room r, String block) {
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (roomZone(x, z)) {
                    a.set(x, EV, z, block);
                }
            }
        }
    }

    private void put(int x, int z, String block) {
        if (open(x, z)) {
            a.set(x, EV + 1, z, block);
        }
    }

    /** The resurfacer's garage: the machine parked nose to the tunnel, the hot-water tank, the shelves, the dock door to the ramp. */
    private void garage(Room r) {
        int rx = 44, rz = 52;
        for (String c : RESURF.split(";")) {
            String[] v = c.split(",");
            int cx = Integer.parseInt(v[0]), cy = Integer.parseInt(v[1]), cz = Integer.parseInt(v[2]);
            a.set(rx + cx, EV + 1 + cy, rz + cz, "ar_resurf_" + cx + "_" + cy + "_" + cz);
        }
        for (int y = EV + 1; y <= EV + 3; y++) {
            a.set(31, y, 52, y == EV + 3 ? "water_tank_top" : "water_tank");
        }
        alongWalls(r, (c, f) -> {
            double q = a.chance();
            if (c[1] < 30 || q < 0.3) {
                return;
            }
            a.set(c[0], EV + 1, c[1], q < 0.6 ? "store_shelf_hardware[facing=" + f + "]" : q < 0.8 ? "fit_counter_steel[facing=" + f + "]"
                    : "propane_tank[axis=x]");
            if (q >= 0.3 && q < 0.6) {
                a.set(c[0], EV + 2, c[1], "store_shelf_hardware_top[facing=" + f + "]");
            }
        }, 2);
        for (int z = Arena.DZ0; z <= Arena.DZ1; z++) {
            for (int x = 30; x <= 34; x++) {
                if (a.chance() < 0.4) {
                    put(x, z, a.any("st_pallet_water", "st_pallet_meals", "st_pallet", "cardboard_box[facing=east]"));
                }
            }
        }
        a.sign("dock", 29, EV + 5, Arena.DZ0 - 2, "east");
    }

    /** The relief depot: pallets in rows with aisles between, cases broken open. */
    private void depot(Room r) {
        String[] stock = {"st_pallet_water", "st_pallet_water_half", "st_pallet_meals", "st_pallet_cots", "st_pallet", "sc_cases_water[facing=north]",
            "sc_cases_blankets[facing=north]", "cardboard_box[facing=north]", "bunk_rack_boxes[facing=north]"};
        for (int x = r.x0() + 2; x <= r.x1() - 2; x++) {
            for (int z = r.z0() + 2; z <= r.z1() - 2; z++) {
                if ((x - r.x0()) % 4 == 0 || (z - r.z0()) % 6 == 0 || a.chance() < 0.25) {
                    continue;
                }
                put(x, z, stock[a.rng.nextInt(stock.length)]);
            }
        }
    }

    /** The ice plant: the compressors in a row, the brine tanks, the pipes along the walls under the slab. */
    private void icePlant(Room r) {
        for (int x = r.x0() + 3; x <= r.x1() - 3; x += 2) {
            int z = 14;
            if (open(x, z)) {
                a.set(x, EV + 1, z, "gs_compressor_lo[facing=south]");
                a.set(x, EV + 2, z, "gs_compressor_hi[facing=south]");
            }
        }
        for (int z : new int[] {22, 26}) {
            for (int x = r.x0() + 3; x <= r.x1() - 4; x += 3) {
                if (open(x, z)) {
                    a.set(x, EV + 1, z, "tank_blue");
                    a.set(x, EV + 2, z, "tank_blue");
                }
            }
        }
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                if (roomZone(x, z) && (z - r.z0()) % 5 == 2 && a.empty(x, CONC - 2, z)) {
                    a.set(x, CONC - 2, z, "fit_pipes_a[axis=x]");
                }
            }
        }
        alongWalls(r, (c, f) -> {
            if (a.chance() < 0.3) {
                a.set(c[0], EV + 2, c[1], "fit_valve[facing=" + f + "]");
            }
        }, 3);
    }

    /** The cold room under the north stands, where the shelter laid its dead in rows. */
    private void morgue(Room r) {
        for (int z = r.z0() + 2; z <= r.z1() - 2; z += 3) {
            for (int x = r.x0() + 2; x <= r.x1() - 2; x++) {
                if (!open(x, z) || !open(x, z + 1) || a.chance() < 0.2) {
                    continue;
                }
                double q = a.chance();
                if (q < 0.8) {
                    a.set(x, EV + 1, z, "bunk_bodybag_head[facing=north]");
                    a.set(x, EV + 1, z + 1, "bunk_bodybag[facing=north]");
                } else {
                    a.set(x, EV + 1, z, "st_stretcher_bag[facing=north]");
                }
            }
        }
        floor(r, "ar_rubber");
    }

    /** The commissary: the line of cookers, the prep tables, the meals stacked, the trestles where people ate. */
    private void kitchen(Room r) {
        String[] line = {"dn_griddle", "store_oven", "dn_prep", "dn_fryer", "dn_prep", "store_oven", "dn_lowboy", "fit_counter_steel"};
        for (int x = r.x0() + 2, k = 0; x <= r.x1() - 2; x++, k++) {
            if (open(x, r.z0() + 1)) {
                a.set(x, EV + 1, r.z0() + 1, line[k % line.length] + "[facing=south]");
            }
        }
        for (int z = r.z0() + 6; z <= r.z1() - 3; z += 4) {
            for (int x = r.x0() + 3; x <= r.x1() - 3; x++) {
                put(x, z, "trade_banquet_table");
                if (a.chance() < 0.7) {
                    put(x, z - 1, a.chance() < 0.85 ? "trade_folding_chair[facing=south]" : "trade_folding_chair_tipped[facing=" + a.dir() + "]");
                }
                if (a.chance() < 0.7) {
                    put(x, z + 1, "trade_folding_chair[facing=north]");
                }
            }
        }
        alongWalls(r, (c, f) -> {
            if (a.chance() < 0.5) {
                a.set(c[0], EV + 1, c[1], a.any("st_pallet_meals", "st_pallet_water_half", "sc_cases_water[facing=" + f + "]"));
            }
        }, 2);
    }

    /** The officials' room: lockers, a bench, the desk with the game sheets. */
    private void officials(Room r) {
        alongWalls(r, (c, f) -> {
            a.set(c[0], EV + 1, c[1], "fs_locker[facing=" + f + "]");
            a.set(c[0], EV + 2, c[1], "fs_locker_top[facing=" + f + "]");
        }, 1);
        for (int x = r.cx() - 3; x <= r.cx() + 3; x++) {
            put(x, r.cz(), "st_bench[facing=north]");
        }
        put(r.cx(), r.cz() - 4, "desk[facing=south]");
        put(r.cx(), r.cz() - 5, "office_chair[facing=south]");
    }

    /** The equipment room: the sticks in their racks, the skates, the boxes of pucks and tape. */
    private void equipment(Room r) {
        alongWalls(r, (c, f) -> {
            double q = a.chance();
            a.set(c[0], EV + 1, c[1], (q < 0.5 ? "ar_sticks" : q < 0.75 ? "store_shelf_thrift" : "bunk_rack_boxes") + "[facing=" + f + "]");
            if (q >= 0.5 && q < 0.75) {
                a.set(c[0], EV + 2, c[1], "store_shelf_thrift_top[facing=" + f + "]");
            }
        }, 1);
        for (int k = 0; k < 14; k++) {
            put(r.x0() + 2 + a.rng.nextInt(Math.max(1, r.x1() - r.x0() - 3)), r.z0() + 2 + a.rng.nextInt(Math.max(1, r.z1() - r.z0() - 3)),
                    a.any("ar_skates[facing=" + a.dir() + "]", "cardboard_box[facing=" + a.dir() + "]", "paper"));
        }
    }

    /** A locker room: the stalls round the walls, the carpet (the Gulls'), the benches, the whiteboard, the trainer's table. */
    /**
     * A locker suite: the dressing room next to the tunnel (the stalls round its walls, the
     * benches, the Gulls' carpet), the showers and the toilets behind it, the trainer's room and
     * the coaches' office at the back.
     */
    private void lockers(Room r, boolean home) {
        // home runs north from the tunnel, the visitors south: the walls between the three parts
        int w1 = home ? 34 : 97, w2 = home ? 20 : 111, d = home ? -1 : 1;
        for (int wz : new int[] {w1, w2}) {
            for (int x = r.x0(); x <= r.x1(); x++) {
                if (roomZone(x, wz)) {
                    a.fill(x, EV + 1, wz, x, ceil(x, wz) - 1, wz, Arena.WALL);
                }
            }
            a.door(162, EV, wz, Doors.SHOP, home ? "south" : "north");
            a.door(178, EV, wz, Doors.SHOP, home ? "south" : "north");
        }
        Room dress = home ? new Room("dress", r.x0(), r.x1(), w1 + 1, r.z1()) : new Room("dress", r.x0(), r.x1(), r.z0(), w1 - 1);
        Room wash = home ? new Room("wash", r.x0(), r.x1(), w2 + 1, w1 - 1) : new Room("wash", r.x0(), r.x1(), w1 + 1, w2 - 1);
        Room back = home ? new Room("back", r.x0(), r.x1(), r.z0(), w2 - 1) : new Room("back", r.x0(), r.x1(), w2 + 1, r.z1());
        if (home) {
            floor(dress, "ar_carpet");
        }
        floor(wash, "dn_quarry");
        alongWalls(dress, (c, f) -> a.set(c[0], EV + 1, c[1], (home ? "ar_stall" : "ar_stall_away") + "[facing=" + f + "]"), 1);
        for (int z = dress.cz() - 4; z <= dress.cz() + 4; z += 8) {
            for (int x = dress.x0() + 6; x <= dress.x1() - 6; x++) {
                put(x, z, "st_bench[facing=" + (z < dress.cz() ? "south" : "north") + "]");
            }
        }
        put(dress.x1() - 3, dress.cz(), "of_whiteboard[facing=west]");
        for (int k = 0; k < 8; k++) {
            put(dress.x0() + 3 + a.rng.nextInt(dress.x1() - dress.x0() - 5), dress.z0() + 3 + a.rng.nextInt(dress.z1() - dress.z0() - 5),
                    a.any("ar_skates[facing=" + a.dir() + "]", "trash_bag[facing=" + a.dir() + "]", "home_blood_pool", "paper"));
        }
        alongWalls(wash, (c, f) -> {
            boolean showers = c[0] > wash.cx();
            a.set(c[0], EV + 1, c[1], (showers ? "bunk_shower" : a.chance() < 0.6 ? "fit_toilet_stall" : "home_sink_white") + "[facing=" + f + "]");
        }, 2);
        put(back.x0() + 4, back.cz(), "hs_gurney_a[facing=north]");
        put(back.x0() + 7, back.cz(), "hs_gurney_a[facing=north]");
        put(back.x0() + 5, back.cz() + 2 * d, "hs_cart_med[facing=north]");
        put(back.x0() + 10, back.cz(), "desk[facing=" + (home ? "south" : "north") + "]");
        put(back.x0() + 10, back.cz() - d, "office_chair[facing=" + (home ? "south" : "north") + "]");
        put(back.x0() + 12, back.cz(), "home_tv[facing=" + (home ? "south" : "north") + "]");
        alongWalls(back, (c, f) -> {
            if (a.chance() < 0.5) {
                a.set(c[0], EV + 1, c[1], a.any("hs_cabinet", "filing_cabinet", "cardboard_box") + "[facing=" + f + "]");
            }
        }, 3);
    }

    private void workshop(Room r) {
        alongWalls(r, (c, f) -> {
            double q = a.chance();
            a.set(c[0], EV + 1, c[1], (q < 0.4 ? "fit_counter_steel" : q < 0.75 ? "store_shelf_hardware" : "trade_stepladder") + "[facing=" + f + "]");
            if (q >= 0.4 && q < 0.75) {
                a.set(c[0], EV + 2, c[1], "store_shelf_hardware_top[facing=" + f + "]");
            }
        }, 2);
        for (int k = 0; k < 5; k++) {
            put(r.x0() + 3 + a.rng.nextInt(r.x1() - r.x0() - 5), r.z0() + 3 + a.rng.nextInt(r.z1() - r.z0() - 5), "propane_tank[axis=z]");
        }
    }

    /** First aid, the shelter's infirmary: gurneys and cots close together, the carts, the blood. */
    private void aid(Room r) {
        for (int z = r.z0() + 2; z <= r.z1() - 2; z += 3) {
            for (int x = r.x0() + 2; x <= r.x1() - 2; x += 2) {
                if (!open(x, z) || !open(x, z + 1)) {
                    continue;
                }
                double q = a.chance();
                if (q < 0.35) {
                    a.set(x, EV + 1, z, (a.chance() < 0.3 ? "hs_gurney_bag_a" : "hs_gurney_a") + "[facing=north]");
                } else if (q < 0.8) {
                    a.set(x, EV + 1, z, "sc_cot_head[facing=north]");
                    a.set(x, EV + 1, z + 1, "sc_cot_foot[facing=north]");
                }
                if (a.chance() < 0.25) {
                    put(x + 1, z, a.any("hs_cart_med[facing=north]", "home_blood_pool", "hs_monitor[facing=south]"));
                }
            }
        }
        alongWalls(r, (c, f) -> {
            if (a.chance() < 0.4) {
                a.set(c[0], EV + 1, c[1], "hs_cabinet[facing=" + f + "]");
            }
        }, 2);
    }

    /** The isolation ward the bitten were put in: cots, some of them still occupied. */
    private void isolation(Room r) {
        for (int z = r.z0() + 2; z <= r.z1() - 2; z += 3) {
            for (int x = r.x0() + 2; x <= r.x1() - 2; x += 2) {
                if (!open(x, z) || !open(x, z + 1) || a.chance() < 0.25) {
                    continue;
                }
                if (a.chance() < 0.4) {
                    a.set(x, EV + 1, z, "home_corpse_head[facing=north]");
                    a.set(x, EV + 1, z + 1, "home_corpse_foot[facing=north]");
                } else {
                    a.set(x, EV + 1, z, "sc_cot_head_b[facing=north]");
                    a.set(x, EV + 1, z + 1, "sc_cot_foot_b[facing=north]");
                }
            }
        }
        litter(r, EV, 0.1);
    }

    private void press(Room r) {
        for (int z = r.z0() + 3; z <= r.z1() - 3; z += 4) {
            for (int x = r.x0() + 2; x <= r.x1() - 2; x += 2) {
                put(x, z, (a.chance() < 0.5 ? "home_desk_pc" : "desk") + "[facing=north]");
                put(x, z + 1, a.chance() < 0.8 ? "office_chair[facing=north]" : "office_chair_fallen[facing=" + a.dir() + "]");
            }
        }
        alongWalls(r, (c, f) -> a.set(c[0], EV + 1, c[1], "filing_cabinet[facing=" + f + "]"), 4);
        litter(r, EV, 0.12);
    }

    private void security(Room r) {
        for (int x = r.cx() - 4; x <= r.cx() + 4; x++) {
            put(x, r.cz() - 2, "desk[facing=south]");
            if (open(x, r.cz() - 2) || a.get(x, EV + 1, r.cz() - 2) != null) {
                a.set(x, EV + 2, r.cz() - 2, (a.power && a.chance() < 0.5 ? "hs_monitor_on" : "hs_monitor") + "[facing=south]");
            }
            put(x, r.cz() - 1, "office_chair[facing=north]");
        }
        alongWalls(r, (c, f) -> {
            double q = a.chance();
            a.set(c[0], EV + 1, c[1], (q < 0.6 ? "fs_locker" : "home_gun_cabinet") + "[facing=" + f + "]");
            a.set(c[0], EV + 2, c[1], (q < 0.6 ? "fs_locker_top" : "home_gun_cabinet_top") + "[facing=" + f + "]");
        }, 2);
    }

    // ------------------------------------------------------------------ the concourse

    private record Unit(String kind, int x0, int x1) {
    }

    private static final Unit[] NORTH = {new Unit("grill", 84, 93), new Unit("men", 94, 105), new Unit("women", 106, 117), new Unit("brew", 118, 127)};
    private static final Unit[] SOUTH = {new Unit("pizza", 84, 93), new Unit("store", 94, 105), new Unit("aid", 106, 117), new Unit("nacho", 118, 127)};
    private static final java.util.Map<String, String[]> LINE = java.util.Map.of(
            "grill", new String[] {"dn_griddle", "dn_griddle", "dn_fryer", "dn_fryer", "st_warmer", "dn_prep", "st_fountain"},
            "pizza", new String[] {"store_oven", "store_oven", "dn_prep", "dn_prep", "st_warmer", "dn_lowboy", "st_fountain"},
            "brew", new String[] {"store_cooler", "store_cooler", "trade_back_bar", "trade_back_bar", "dn_ice_lo", "store_cooler", "st_fountain"},
            "nacho", new String[] {"st_warmer", "dn_griddle", "dn_prep", "dn_prep", "dn_fryer", "dn_lowboy", "st_fountain"});
    /** The stands with the Gulls' own headers (the rest share the stadium's). */
    private static final java.util.Set<String> ARENA_SIGNS = java.util.Set.of("grill", "nacho", "store");
    /** The rooms' drop ceiling along the concourse. */
    private static final int ROOM_CEIL = 5;

    private void concourse() {
        // the openings through the concourse's outer wall: into the halls at the ends, the lobbies at the corners
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                if (Arena.band(x, z) != Arena.CONC_WALL) {
                    continue;
                }
                boolean hall = Arena.endSide(z) && Math.abs(Arena.pz(z)) < 6;
                boolean lobby = Arena.inCorner(x, z) && Arena.corner(x, z, 45)[0] < 3.5;
                if (hall || lobby) {
                    a.fill(x, CONC + 1, z, x, CONC + (hall ? 6 : 4), z, "air");
                }
            }
        }
        for (Unit u : NORTH) {
            unit(u, true);
        }
        for (Unit u : SOUTH) {
            unit(u, false);
        }
        // the turnstiles across the halls, a gap kept every few
        for (int x : new int[] {Arena.BX0 + 3, Arena.BX1 - 3}) {
            String f = x < X ? "east" : "west";
            for (int z = Z - 21; z <= Z + 20; z++) {
                if (Math.floorMod(z - Z, 5) == 0) {
                    continue;
                }
                double q = a.chance();
                a.set(x, CONC + 1, z, (z % 2 == 0 ? "st_turnstile" : q < 0.5 ? "st_barrier_down" : "st_barrier") + "[facing=" + f + "]");
            }
        }
        // along the portal wall's back: drink machines, bins, the missing posters taped up
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                if (Arena.band(x, z) != Arena.UP_FIRST || Arena.portal(x, z) || Arena.inCorner(x, z) || !a.empty(x, CONC + 1, z)) {
                    continue;
                }
                boolean nearPortal = false;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        nearPortal |= Arena.portal(x + dx, z + dz) && Arena.band(x + dx, z + dz) == Arena.PORTAL;
                    }
                }
                int k = Math.floorMod(x * 7 + z * 11, 13);
                String f = Arena.opposite(Arena.in(x, z));
                if (nearPortal) {
                    continue;
                }
                if (k == 0) {
                    a.set(x, CONC + 1, z, "store_vending[facing=" + f + "]");
                    a.set(x, CONC + 2, z, "store_vending_top[facing=" + f + "]");
                } else if (k == 4) {
                    a.set(x, CONC + 1, z, "pk_vending[facing=" + f + "]");
                } else if (k == 7) {
                    a.set(x, CONC + 1, z, "bin_ribbed");
                } else if (k == 2 || k == 9) {
                    a.set(x, CONC + 2, z, (k == 2 ? "st_poster_missing" : "st_poster_missing_b") + "[facing=" + f + "]");
                }
            }
        }
        // the lights under the upper deck, the mess
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                int b = Arena.band(x, z);
                if (b == 33 && Math.floorMod(x * 3 + z * 5, 11) == 0) {
                    a.set(x, Arena.tread(b) - 1, z, a.power && a.chance() < 0.35 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        strew(CONC, 0.025);
        // the overflow: cots in rows across two of the corner lobbies (the north-west, and the
        // south-east turned round), clear of the curve, the core and the doors
        for (int side = 0; side < 2; side++) {
            int placed = 0;
            for (int r = 0; r < 6 && placed < 14; r++) {
                for (int k = 0; k < 12 && placed < 14; k++) {
                    int x = Arena.BX0 + 3 + k * 2, z = Arena.BZ0 + 11 + r * 3;
                    if (side == 1) {
                        x = 2 * X - 1 - x;
                        z = 2 * Z - 2 - z;
                    }
                    if (a.empty(x, CONC + 1, z) && a.empty(x, CONC + 1, z + 1) && Arena.band(x, z) > 42 && Arena.band(x, z + 1) > 42
                            && lobby(x, z) && lobby(x, z + 1)) {
                        a.set(x, CONC + 1, z, "sc_cot_head[facing=north]");
                        a.set(x, CONC + 1, z + 1, "sc_cot_foot[facing=north]");
                        placed++;
                    }
                }
            }
        }
    }

    /**
     * A unit behind the concourse's outer wall on a long side: a stand (its counter in the wall,
     * the grille, the header, the line and the menus behind), a restroom, the team store, first aid.
     */
    private void unit(Unit u, boolean north) {
        int front = north ? Z - Arena.RD - 1 - Arena.CONC_WALL : Z + Arena.RD + Arena.CONC_WALL;
        int back = north ? Arena.BZ0 + 1 : Arena.BZ1 - 1, dz = north ? -1 : 1;
        String face = north ? "south" : "north", inward = north ? "north" : "south";
        int z0 = Math.min(front + dz, back), z1 = Math.max(front + dz, back);
        // the side walls, the drop ceiling, the wall above solid
        for (int z = z0; z <= z1; z++) {
            for (int y = CONC + 1; y < ROOM_CEIL; y++) {
                a.set(u.x0(), y, z, Arena.WALL);
                a.set(u.x1(), y, z, Arena.WALL);
            }
        }
        for (int x = u.x0(); x <= u.x1(); x++) {
            for (int z = z0; z <= z1; z++) {
                a.fill(x, ROOM_CEIL, z, x, UP - 1, z, Arena.FILL);
                a.set(x, ROOM_CEIL, z, "concrete_panel");
            }
        }
        int mid = (u.x0() + u.x1()) / 2;
        switch (u.kind()) {
            case "men", "women" -> {
                a.door(mid, CONC, front, Doors.SHOP, face);
                a.set(mid - 1, CONC + 3, front + (north ? 1 : -1), "st_plaque_" + u.kind() + "[facing=" + face + "]");
                for (int x = u.x0() + 1; x < u.x1(); x++) {
                    for (int z = z0; z <= z1; z++) {
                        a.set(x, CONC, z, "dn_quarry");
                    }
                    a.set(x, CONC + 1, back, "fit_toilet_stall[facing=" + face + "]");
                }
                for (int z = z0 + 1; z < z1; z++) {
                    a.set(u.x0() + 1, CONC + 1, z, "home_sink_white[facing=east]");
                    a.set(u.x0() + 1, CONC + 2, z, "fit_mirror_long[facing=east]");
                }
            }
            case "store" -> {
                standFront(u, front, face, "store");
                for (int x = u.x0() + 1; x < u.x1(); x++) {
                    a.set(x, CONC + 1, back, "store_shelf_thrift[facing=" + face + "]");
                    a.set(x, CONC + 2, back, "store_shelf_thrift_top[facing=" + face + "]");
                }
            }
            case "aid" -> {
                a.door(mid, CONC, front, Doors.SHOP, face);
                a.set(mid - 1, CONC + 3, front + (north ? 1 : -1), "st_plaque_aid[facing=" + face + "]");
                for (int x = u.x0() + 1; x < u.x1() - 1; x += 2) {
                    a.set(x, CONC + 1, back, "sc_cot_head[facing=" + inward + "]");
                    a.set(x, CONC + 1, back - dz, "sc_cot_foot[facing=" + inward + "]");
                }
                a.set(u.x1() - 1, CONC + 1, back, "hs_cabinet[facing=" + face + "]");
            }
            default -> {
                standFront(u, front, face, u.kind());
                String[] line = LINE.get(u.kind());
                for (int x = u.x0() + 1, k = 0; x < u.x1() && k < line.length; x++, k++) {
                    String e = north ? line[k] : line[line.length - 1 - k];
                    a.set(x, CONC + 1, back, e + "[facing=" + face + "]");
                    if (e.equals("store_cooler")) {
                        a.set(x, CONC + 2, back, "store_cooler_top[facing=" + face + "]");
                    }
                    if (e.startsWith("dn_griddle") || e.startsWith("dn_fryer") || e.equals("store_oven")) {
                        a.set(x, CONC + 3, back, "dn_hood[facing=" + face + "]");
                    }
                }
                int mx = u.x1() - 3;
                String menu = ARENA_SIGNS.contains(u.kind()) ? "ar_menu_" : "st_menu_";
                a.set(mx, CONC + 3, back, menu + u.kind() + "_" + (north ? 0 : 1) + "[facing=" + face + "]");
                a.set(mx + 1, CONC + 3, back, menu + u.kind() + "_" + (north ? 1 : 0) + "[facing=" + face + "]");
            }
        }
    }

    /** A stand's front in the wall: the counter with its tills, the grille down or bent, the lit header. */
    private void standFront(Unit u, int front, String face, String kind) {
        double g = a.chance();
        for (int x = u.x0() + 1; x <= u.x1() - 1; x++) {
            int i = face.equals("south") ? x - u.x0() - 1 : u.x1() - 1 - x;
            if (i < 8) {
                a.set(x, ROOM_CEIL, front, (ARENA_SIGNS.contains(kind) ? "ar_stand_" : "st_stand_") + kind + "_" + i + "[facing=" + face + "]");
            }
            a.set(x, ROOM_CEIL - 1, front, "st_grille_box[facing=" + face + "]");
            if (x == u.x0() + 1) {
                a.door(x, CONC, front, Doors.STEEL, face);
                continue;
            }
            a.set(x, CONC + 1, front, (i % 3 == 1 ? "store_register" : "store_counter") + "[facing=" + face + "]");
            String lo = "air", hi = "air";
            if (g < 0.25) {
                lo = "st_grille";
                hi = "st_grille";
            } else if (g < 0.5) {
                hi = "st_grille_half";
            } else if (g < 0.7) {
                hi = a.chance() < 0.5 ? "st_grille_bent" : "air";
            }
            a.set(x, CONC + 2, front, lo.equals("air") ? lo : lo + "[facing=" + face + "]");
            a.set(x, CONC + 3, front, hi.equals("air") ? hi : hi + "[facing=" + face + "]");
        }
    }

    // ------------------------------------------------------------------ the upper level

    /** The walkway's high corners, where families moved up out of the crowd: tarps, cots, their things. */
    private void upperLevel() {
        String[] tarps = {"st_tarp_green", "st_tarp_black", "st_tarp_red", "st_tarp_yellow"};
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                int b = Arena.band(x, z);
                if (b < 47 || !Arena.inCorner(x, z) || !a.empty(x, UP + 1, z)) {
                    continue;
                }
                int cell = Math.floorMod(x / 4 * 7 + z / 4 * 13, 9);
                if (cell < 3) {
                    a.set(x, UP + 1, z, tarps[cell]);
                } else if (cell < 5 && a.chance() < 0.12) {
                    a.set(x, UP + 1, z, a.any("sc_cases_water[facing=" + a.dir() + "]", "sc_cases_blankets[facing=" + a.dir() + "]", "trash_bag[facing="
                            + a.dir() + "]", "home_books_stack_low[facing=" + a.dir() + "]", "home_blood_pool"));
                }
            }
        }
        strew(UP, 0.02);
    }

    private static final java.util.Set<String> FLOORS = java.util.Set.of("st_floor", "floor_concrete", "st_tread", "st_tread_gum", "dn_quarry");

    /** What's left on the concourse or the walkway: on the floor only, never on a step or a seat. */
    private void strew(int y, double density) {
        for (int x = Arena.BX0 + 1; x < Arena.BX1; x++) {
            for (int z = Arena.BZ0 + 1; z < Arena.BZ1; z++) {
                String g = a.get(x, y, z);
                if (g == null || !FLOORS.contains(g) || !a.empty(x, y + 1, z) || a.chance() > density) {
                    continue;
                }
                double r = a.chance();
                a.set(x, y + 1, z, r < 0.45 ? "paper" : r < 0.6 ? "blood_splat" : r < 0.68 ? "glass_shards"
                        : r < 0.8 ? "home_books_stack_low[facing=" + a.dir() + "]" : r < 0.9 ? "trash_bag[facing=" + a.dir() + "]" : "home_blood_pool");
            }
        }
    }

    // ------------------------------------------------------------------ the shelter on the floor

    /**
     * Cots in rows over the plywood with aisles between, people's things round them; the triage
     * tent and the shelter's own tent on the bare ice at the ends, the check-in tables at the east
     * tunnel, the pallets by the west one, the stretchers waiting.
     */
    private void shelter() {
        int[] rows = {54, 57, 60, 70, 73, 76};
        for (int z : rows) {
            for (int x = X - 17; x <= X + 16; x += 2) {
                if (Math.abs(x - X) <= 4 && z > 56 && z < 74 || !a.ice[x][z] || !a.ice[x][z + 1] || a.chance() < 0.12) {
                    continue;
                }
                boolean north = z < Z;
                String f = north ? "north" : "south";
                int head = north ? z : z + 1, foot = north ? z + 1 : z;
                if (a.chance() < 0.07) {
                    a.set(x, EV + 1, head, "bunk_bodybag_head[facing=" + f + "]");
                    a.set(x, EV + 1, foot, "bunk_bodybag[facing=" + f + "]");
                    continue;
                }
                boolean b = a.chance() < 0.3;
                a.set(x, EV + 1, head, (b ? "sc_cot_head_b" : "sc_cot_head") + "[facing=" + f + "]");
                a.set(x, EV + 1, foot, (b ? "sc_cot_foot_b" : "sc_cot_foot") + "[facing=" + f + "]");
                if (a.chance() < 0.45) {
                    int bx = x + 1;
                    if (a.empty(bx, EV + 1, head) && a.ice[bx][head]) {
                        a.set(bx, EV + 1, head, a.any("sc_cases_water[facing=" + f + "]", "trash_bag[facing=" + a.dir() + "]", "home_books_stack_low[facing="
                                + a.dir() + "]", "paper", "cardboard_box[facing=" + a.dir() + "]", "home_blood_pool", "st_pallet_water_half"));
                    }
                }
            }
        }
        a.tent(X - 27, EV, Z + 2, 8, "triage");
        a.tent(X + 21, EV, Z - 12, 7, "shelter");
        // the check-in at the east tunnel's mouth
        for (int z = Z - 4; z <= Z + 3; z++) {
            a.set(X + 23, EV + 1, z, "trade_banquet_table");
            a.set(X + 24, EV + 1, z, a.chance() < 0.8 ? "trade_folding_chair[facing=west]" : "trade_folding_chair_tipped[facing=" + a.dir() + "]");
            if (a.chance() < 0.5) {
                a.set(X + 23, EV + 2, z, a.any("paper", "home_books_stack_low[facing=west]"));
            }
        }
        a.sign("register", X + Arena.RW, EV + 3, Z - 4, "west");
        // the pallets by the west tunnel, the stretchers by the east
        for (int x = X - 29; x <= X - 24; x++) {
            for (int z = Z - 11; z <= Z - 5; z++) {
                if (a.ice[x][z] && a.empty(x, EV + 1, z) && a.chance() < 0.55) {
                    a.set(x, EV + 1, z, a.any("st_pallet_water", "st_pallet_meals", "st_pallet_cots", "st_pallet_water_half", "sc_cases_blankets[facing=east]"));
                }
            }
        }
        for (int x = X + 20; x <= X + 26; x += 2) {
            for (int z = Z + 6; z <= Z + 10; z += 2) {
                if (a.ice[x][z] && a.empty(x, EV + 1, z) && a.chance() < 0.6) {
                    a.set(x, EV + 1, z, (a.chance() < 0.6 ? "st_stretcher_bag" : "st_stretcher") + "[facing=north]");
                }
            }
        }
        for (int x = X - 30; x <= X + 30; x++) {
            for (int z = Z - 13; z <= Z + 13; z++) {
                if (x >= 0 && z >= 0 && a.ice[x][z] && a.empty(x, EV + 1, z) && a.chance() < 0.035) {
                    a.set(x, EV + 1, z, a.any("paper", "paper", "blood_splat", "trash_bag[facing=" + a.dir() + "]", "home_blood_pool", "glass_shards"));
                }
            }
        }
    }
}
