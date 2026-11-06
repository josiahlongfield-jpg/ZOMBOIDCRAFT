package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * The missing floor: half way up an office tower, a private security firm (Halvard Security) took a
 * plant floor for itself when it started and turned it into a strongpoint. Steel plates are bolted
 * over every window, a firing slit cut in some, which is the clue from the street: one row of the
 * tower's windows plated over. The lift's doors on the floor are welded shut, so the lift skips it
 * (its machine only knows the landings that have doors); the stairwell's door is bricked up, NO
 * ACCESS sprayed across the blocks. Inside: sandbagged firing positions behind the slits along the
 * front, a barricade facing the stair door, the guards' bunks down one side, their radio and map
 * table down the other, stores along the back, the generator in the core's east pocket, and in its
 * west pocket the armoury behind a cage.
 *
 * Built over a finished floor of {@link Tower} (its own random, so the tower and the street round it
 * are drawn exactly as before), in the tower's frame: y is the floor's slab, the rooms y+1..y+3.
 */
final class Strongpoint {
    private final Placer p;
    private final SplittableRandom g;
    private final int w, d, y, cx0, cz0, kx, state;

    /** state: 0 sealed (nobody has been in since), 1 looted, 2 breached. */
    Strongpoint(Placer p, SplittableRandom g, int w, int d, int y, int cx0, int cz0, int kx, int state) {
        this.p = p;
        this.g = g;
        this.w = w;
        this.d = d;
        this.y = y;
        this.cx0 = cx0;
        this.cz0 = cz0;
        this.kx = kx;
        this.state = state;
    }

    private boolean slimCore(int x, int z) {
        return x >= kx && x < kx + 8 && z >= cz0 && z < cz0 + 10;
    }

    void build() {
        strip();
        plates();
        core();
        frontLine();
        bunks();
        comms();
        armoury();
        plant();
        stores();
        lamps();
        occupants();
        String s = state == 0 ? "sealed" : state == 1 ? "looted" : "breached";
        p.mark("bunker " + s + " floor", kx + 2, y + 1, cz0 + 1);
        p.mark("bunker door", cx0, y + 1, cz0 + 4);
    }

    /** Everything the office had cleared out: bare concrete floor, nothing standing. */
    private void strip() {
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                if (slimCore(x, z)) {
                    continue;
                }
                p.set(x, y, z, g.nextDouble() < 0.15 ? "bunk_floor_crack" : g.nextDouble() < 0.3 ? "bunk_floor_dirty" : "bunk_floor");
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    p.set(x, yy, z, "air");
                }
            }
        }
    }

    /** Every window of the floor plated over, a firing slit in every third along the front. */
    private void plates() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (x != 0 && z != 0 && x != w - 1 && z != d - 1) {
                    continue;
                }
                boolean front = z == 0;
                int along = front || z == d - 1 ? x : z;
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    String was = p.get(x, yy, z);
                    if (was == null || !was.startsWith("window")) {
                        continue;
                    }
                    boolean slit = yy == y + 2 && along % 3 == 1 && (front || g.nextDouble() < 0.4);
                    p.set(x, yy, z, slit ? "xf_slit" : "xf_plate");
                }
            }
        }
    }

    /**
     * The core on this floor: the stair door bricked up (or bashed through), the lift's doors welded
     * over with plate and its call button gone, the firm's notice by the door.
     */
    private void core() {
        int sx = kx + 2, lx = kx + 5;
        if (state == 0) {
            p.set(sx, y + 1, cz0, "xf_bricked_plain");
            p.set(sx, y + 2, cz0, "xf_bricked");
        } else {
            // the blocks knocked in: the hole, the rubble both sides
            p.set(sx, y + 1, cz0, "air");
            p.set(sx, y + 2, cz0, g.nextBoolean() ? "air" : "xf_bricked");
            if ("air".equals(p.get(sx, y + 2, cz0))) {
                p.set(sx, y + 1, cz0 + 1, "rubble_pile");
            } else {
                p.set(sx, y + 1, cz0 + 1, "debris_chunk");
            }
            p.set(sx, y + 1, cz0 - 1, "rubble_pile");
            p.set(sx - 1, y + 1, cz0 - 1, "debris_chunk");
        }
        for (int x = lx; x <= lx + 1; x++) {
            p.set(x, y + 1, cz0, "xf_plate");
            p.set(x, y + 2, cz0, "xf_plate");
        }
        p.set(lx + 2, y + 2, cz0 - 1, "air");
        p.set(kx + 3, y + 2, cz0 - 1, "xf_sign[facing=north]");
    }

    /** Sandbags under the slits along the front, ammo behind them, a barricade facing the stair door. */
    private void frontLine() {
        for (int x = 1; x < w - 1; x++) {
            if (x % 3 == 1 || x % 3 == 2 && g.nextDouble() < 0.5) {
                p.set(x, y + 1, 1, "sandbags[facing=north]");
            }
        }
        for (int x = 2; x < w - 2; x += 4 + g.nextInt(3)) {
            if (cz0 - 1 >= 3) {
                p.set(x, y + 1, 2, g.nextDouble() < 0.6 ? "bunk_crate_ammo[facing=north]" : "bunk_jerry_cans[facing=north]");
            }
        }
        // the barricade facing the stair door: anything coming through the blocks meets it
        int bz = cz0 - 3;
        if (bz >= 2) {
            for (int x = kx; x <= kx + 4; x++) {
                p.set(x, y + 1, bz, x == kx + 4 && state != 0 ? "air" : "sandbags[facing=south]");
            }
            p.set(kx - 1, y + 1, bz, "bunk_crate_ammo[facing=south]");
        }
    }

    /** The guards' bunks two high down the west side, heads to the plated windows, footlockers at the feet. */
    private void bunks() {
        if (cx0 < 3) {
            return;
        }
        for (int z = cz0; z <= cz0 + 8; z += 2) {
            p.set(1, y + 1, z, "bunk_bed_head[facing=east]");
            p.set(1, y + 2, z, "bunk_bed_head_top[facing=east]");
            p.set(2, y + 1, z, "bunk_bed_foot[facing=east]");
            p.set(2, y + 2, z, "bunk_bed_foot_top[facing=east]");
            if (cx0 >= 5 && g.nextDouble() < 0.7) {
                p.set(3, y + 1, z, "bunk_footlocker[facing=east]");
            }
            if (z + 1 <= cz0 + 9 && g.nextDouble() < 0.6) {
                p.set(1, y + 1, z + 1, "bunk_locker[facing=east]");
                p.set(1, y + 2, z + 1, "bunk_locker_top[facing=east]");
            }
        }
    }

    /** The radio desk and the map table down the east side, the duty roster on the wall. */
    private void comms() {
        int x = w - 2;
        if (w - 2 - (cx0 + 13) < 2) {
            return;
        }
        p.set(x, y + 1, cz0 + 1, "bunk_radio_desk[facing=west]");
        p.set(x, y + 1, cz0 + 2, "bunk_radio_desk[facing=west]");
        p.set(x - 1, y + 1, cz0 + 1, g.nextDouble() < 0.3 ? "office_chair_fallen[facing=north]" : "office_chair[facing=east]");
        p.set(x - 1, y + 1, cz0 + 5, "bunk_map_table");
        p.set(x - 1, y + 1, cz0 + 6, "bunk_map_table");
        p.set(x, y + 2, cz0 + 4, "bunk_map_wall[facing=west]");
        p.set(x, y + 2, cz0 + 7, "bunk_tally[facing=west]");
        p.set(x - 1, y + 1, cz0 + 8, "bunk_mess_table[axis=z]");
        p.set(x - 1, y + 1, cz0 + 9, "bunk_mess_table[axis=z]");
    }

    /**
     * The armoury in the core's west pocket: a cage across it with its door, gun racks on the core
     * wall inside, crates of guns and ammunition. A looted or breached one has been emptied.
     */
    private void armoury() {
        int x = cx0, z1 = cz0 + 9, door = cz0 + 4;
        for (int z = cz0; z <= z1; z++) {
            for (int yy = y + 1; yy <= y + 3; yy++) {
                if (z == door && yy <= y + 2) {
                    continue;
                }
                p.set(x, yy, z, "bunk_cage[axis=z]");
            }
        }
        boolean open = state != 0;
        p.set(x, y + 1, door, "bunk_door_cage[facing=east,half=lower,hinge=left,open=" + open + "]");
        p.set(x, y + 2, door, "bunk_door_cage[facing=east,half=upper,hinge=left,open=" + open + "]");
        int in = kx - 1;
        for (int z = cz0; z <= z1; z++) {
            if (z == door) {
                continue;
            }
            boolean full = state == 0 || g.nextDouble() < 0.3;
            p.set(in, y + 2, z, full ? "bunk_gun_rack[facing=west]" : "bunk_pegboard[facing=west]");
            if (in - 1 > x && z != door - 1 && z != door + 1 && g.nextDouble() < (full ? 0.6 : 0.25)) {
                p.set(in - 1, y + 1, z, g.nextBoolean() ? "bunk_crate_gun[facing=west]" : "bunk_crate_ammo[facing=west]");
            }
        }
    }

    /** The generator and its fuel in the core's east pocket, drums of water beside them. */
    private void plant() {
        int x = kx + 9;
        if (x > cx0 + 13) {
            return;
        }
        p.set(x, y + 1, cz0 + 2, "bunk_gen_engine[facing=west]");
        p.set(x, y + 1, cz0 + 3, "bunk_gen_alternator[facing=west]");
        p.set(x, y + 1, cz0 + 5, "bunk_jerry_cans[facing=west]");
        p.set(x, y + 1, cz0 + 6, "bunk_jerry_cans[facing=west]");
        for (int z = cz0 + 7; z <= cz0 + 9; z++) {
            p.set(x, y + 1, z, "bunk_drum_water");
        }
    }

    /** Racks of stores along the back wall, a gap every few for the way between them. */
    private void stores() {
        int z = d - 2;
        if (z <= cz0 + 10) {
            return;
        }
        for (int x = 2; x < w - 2; x++) {
            if (x % 5 == 0) {
                continue;
            }
            boolean full = state == 0 || g.nextDouble() < 0.35;
            String rack = g.nextDouble() < 0.6 ? "bunk_rack_crates" : "bunk_rack_drums";
            p.set(x, y + 1, z, (full ? rack : "bunk_rack_boxes") + "[facing=north]");
        }
    }

    /** Work lamps hung from the slab over, dark like every other light in the tower. */
    private void lamps() {
        for (int x = 3; x < w - 2; x += 6) {
            for (int z = 3; z < d - 2; z += 6) {
                if (!slimCore(x, z) && p.get(x, y + 3, z) != null && "air".equals(p.get(x, y + 3, z))) {
                    p.set(x, y + 3, z, "bunk_lamp_hang");
                }
            }
        }
    }

    /** The guards, by what happened: sealed in, gone through, or overrun. */
    private void occupants() {
        int mx = Math.max(2, cx0 / 2), mz = cz0 + 5;
        switch (state) {
            case 0 -> {
                body(2, cz0 + 3, "security");
                walkers(mx, mz, "security", 1 + g.nextInt(2));
                walkers(w / 2, Math.max(2, cz0 / 2), "security", 1);
            }
            case 1 -> {
                if (g.nextDouble() < 0.6) {
                    body(kx + 1, Math.max(2, cz0 - 2), "survivor");
                }
            }
            default -> {
                body(kx + 2, Math.max(2, cz0 - 2), "security");
                body(w - 3, cz0 + 3, "security");
                p.set(kx + 1, y + 1, Math.max(2, cz0 - 1), "home_blood_drag[facing=north]");
                walkers(w / 2, Math.max(2, cz0 / 2), "any", 2 + g.nextInt(3));
                walkers(mx, mz, "security", 1 + g.nextInt(2));
            }
        }
    }

    private void body(int x, int z, String outfit) {
        if (p.get(x, y + 1, z) != null && !"air".equals(p.get(x, y + 1, z))) {
            return;
        }
        int limbs = g.nextDouble() < 0.25 ? 1 << g.nextInt(8) : 0;
        p.vehicle(x + 0.5, y + 1, z + 0.5, (float) (g.nextDouble() * 360), "!body:" + outfit + ";" + limbs + ";0", g.nextLong());
        if (g.nextDouble() < 0.6) {
            p.set(x, y + 1, z, g.nextBoolean() ? "home_blood_pool" : "blood_splat");
        }
    }

    private void walkers(int x, int z, String outfit, int n) {
        p.vehicle(x + 0.5, y + 1, z + 0.5, 0, "!walkers:" + outfit + ";" + n + ";-3", g.nextLong());
    }
}
