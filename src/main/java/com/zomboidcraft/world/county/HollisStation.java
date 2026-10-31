package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;

/**
 * The old quarantine station on Hollis Island's drive (refs/quarantine/historic-quarantine-station:
 * the Clifton station's brick pavilions under their clerestory monitors, the Boca Grande keeper's
 * house with its veranda and lantern, Ellis Island's wards gone to ruin), drawn in
 * {@link Hollis.Island}'s frame on the island's grass (y 0):
 * <ul>
 * <li>the administration building north of the drive, two storeys of brick under a slate hip, its
 * centre pavilion carried up a storey more with the cupola on it, the yellow jack on the flagpole
 * in front;</li>
 * <li>the disinfecting plant beside it, its boilers and steam chambers, the tall stack;</li>
 * <li>the surgeon's house, white clapboard under a tin hip with a lantern, the veranda, the
 * picket fence; the nurses' home, buff brick;</li>
 * <li>south of the drive the covered walk and the six wards off it, A to F, each a long brick
 * pavilion under a slate roof with the clerestory along its ridge, beds down both walls;</li>
 * <li>the stone morgue and the old burying ground behind it, its iron railings, the headstones
 * gone over, the granite memorial at the gate.</li>
 * </ul>
 */
final class HollisStation {
    /** The wards: six of them from WX0, WSTEP apart, each WW wide, WZ0 .. WZ1. */
    static final int WX0 = -210, WSTEP = 33, WW = 13, WZ0 = 18, WZ1 = 58;
    /** The covered walk along the wards' north ends. */
    static final int WALK0 = 11, WALK1 = 14;

    private final Hollis.Island c;
    private final Bldg b;

    HollisStation(Hollis.Island c) {
        this.c = c;
        this.b = c.b;
    }

    private void set(int x, int y, int z, String block) {
        c.set(x, y, z, block);
    }

    void build() {
        admin();
        plant();
        surgeon();
        nurses();
        walk();
        for (int i = 0; i < 6; i++) {
            ward(i, WX0 + i * WSTEP);
        }
        morgue();
        cemetery();
    }

    // ------------------------------------------------------------------ paths

    /** A gravel path from (x0, z0) to (x1, z1), straight along x or z, w wide. */
    private void path(int x0, int z0, int x1, int z1) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                double r = c.chance();
                set(x, 0, z, r < 0.6 ? "minecraft:gravel" : r < 0.85 ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
                set(x, 1, z, "air");
            }
        }
    }

    // ------------------------------------------------------------------ the administration building

    /**
     * Two storeys of red brick, 35 by 21, under a slate hip; the pavilion in the middle of the
     * front carried up a third storey with its own hip and the cupola, the doors under a fanlight
     * at the head of its steps; the plaque. Inside the hall and the stair, the offices either side,
     * the records upstairs; the yellow jack still flying on the pole out front.
     */
    private void admin() {
        int x0 = -272, x1 = -238, z0 = -34, z1 = -13, px0 = -260, px1 = -250, pz1 = -9;
        b.shell(x0, z0, x1, z1, 2, 5, "brick_red", "home_floor_oak", "home_floor_oak");
        b.shell(px0, z1 - 4, px1, pz1, 3, 5, "brick_red", "home_floor_oak", "home_floor_oak");
        for (int y = 1; y <= 9; y++) {
            for (int x = px0 + 1; x < px1; x++) {
                if (y != 5) {
                    set(x, y, z1, "air");
                }
            }
        }
        for (int s = 0; s < 2; s++) {
            int y = 2 + s * 5;
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                if (x < px0 - 1 || x > px1 + 1) {
                    b.window(x, y, z1, "south", 2, "stone_trim");
                }
                b.window(x, y, z0, "north", 2, "stone_trim");
            }
            for (int z = z0 + 3; z <= z1 - 3; z += 4) {
                b.window(x0, y, z, "west", 2, "stone_trim");
                b.window(x1, y, z, "east", 2, "stone_trim");
            }
        }
        for (int s = 0; s < 3; s++) {
            int y = 2 + s * 5;
            for (int x : new int[] {px0 + 2, px1 - 2}) {
                b.window(x, y, pz1, "south", s == 2 ? 2 : 3, "stone_trim");
            }
            if (s > 0) {
                b.window((px0 + px1) / 2, y, pz1, "south", 3, "stone_trim");
            }
        }
        int dx = (px0 + px1) / 2 - 1;
        set(dx, 1, pz1, "air");
        set(dx + 1, 1, pz1, "air");
        b.doors(dx, 1, pz1, Doors.FLAT, "south");
        b.window(dx, 3, pz1, "south", 1, null);
        b.window(dx + 1, 3, pz1, "south", 1, null);
        for (int x = dx - 1; x <= dx + 2; x++) {
            set(x, 0, pz1 + 1, "stairs_concrete[facing=north]");
        }
        // a stone band round the building at each floor, the hips, the cupola
        for (int x = x0; x <= x1; x++) {
            if (x < px0 || x > px1) {
                set(x, 5, z1 + 1, "stone_trim");
            }
        }
        b.hip(x0, x1, z0, z1, 11, "minecraft:deepslate_tile", 1);
        b.hip(px0, px1, z1 - 4, pz1, 16, "minecraft:deepslate_tile", 1);
        set((px0 + px1) / 2, 21, (z1 - 4 + pz1) / 2, "fm_cupola_slate");
        c.sign("plaque", dx, 2, pz1 + 1, "south");
        c.sign("office", dx, 4, pz1 + 1, "south");
        // the hall, the stair, the offices
        int hz = (z0 + z1) / 2;
        b.stair(dx, z1 - 2, 0, 5, "north", "minecraft:oak");
        b.stair(dx + 1, z1 - 2, 0, 5, "north", "minecraft:oak");
        for (int y : new int[] {0, 5}) {
            for (int x = x0 + 1; x < x1; x++) {
                if (x == dx - 6 || x == dx + 7) {
                    c.partZ(x, z0 + 1, z1 - 1, y, 5, "home_wall_cream", "home_wall_cream");
                    set(x, y + 1, hz, "air");
                    set(x, y + 2, hz, "air");
                }
            }
            for (int x = x0 + 2; x < dx - 7; x += 4) {
                set(x, y + 1, z0 + 2, "desk[facing=south]");
                set(x, y + 1, z0 + 3, c.chance() < 0.5 ? "office_chair_fallen[facing=" + c.dir() + "]" : "office_chair[facing=north]");
                set(x + 1, y + 1, z0 + 1, "filing_cabinet[facing=south]");
            }
            for (int x = dx + 9; x < x1 - 1; x += 4) {
                set(x, y + 1, z0 + 2, y == 0 ? "bunk_radio_desk[facing=south]" : "desk[facing=south]");
                set(x, y + 1, z0 + 3, "office_chair[facing=north]");
                set(x + 1, y + 1, z0 + 1, "filing_cabinet[facing=south]");
            }
            b.lights(x0, z0, x1, z1, y + 4, c.power ? 0.2 : 0);
            b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1, 0.06);
        }
        for (int z = z0 + 2; z < z1 - 1; z += 3) {
            set(x0 + 1, 6, z, "filing_cabinet[facing=east]");
            set(x1 - 1, 6, z, "filing_cabinet[facing=west]");
        }
        // the lawn out front, the path, the flag
        path(dx - 1, pz1 + 2, dx + 2, Hollis.ROAD0 - 2);
        int fx = px0 - 6, fz = Hollis.ROAD0 - 5;
        set(fx, 1, fz, "cv_flagpole_base");
        for (int y = 2; y <= 9; y++) {
            set(fx, y, fz, "cv_flagpole");
        }
        set(fx, 10, fz, "qs_flag_q[facing=north]");
        set(fx, 11, fz, "qs_flag_q_top[facing=north]");
        // the station's board on its posts by the drive
        for (int x = px1 + 4; x <= px1 + 5; x++) {
            set(x, 1, Hollis.ROAD0 - 4, "pn_post");
            set(x, 2, Hollis.ROAD0 - 4, "pn_post");
        }
        c.sign("station", px1 + 4, 2, Hollis.ROAD0 - 3, "south");
    }

    // ------------------------------------------------------------------ the disinfecting plant

    /**
     * The disinfecting plant: a tall single storey of brick under a tin gable, the big doors the
     * baggage carts went in by, two boilers and the steam chambers inside, the drums, the carts;
     * its stack beside it, round brick going sooty at the top.
     */
    private void plant() {
        int x0 = -226, x1 = -200, z0 = -36, z1 = -15;
        b.shell(x0, z0, x1, z1, 1, 8, "brick_red", "floor_concrete", "floor_concrete");
        b.gable(x0, x1, z0, z1, 9, "minecraft:andesite", false, "brick_red", 1);
        for (int x = x0 + 3; x <= x1 - 3; x += 4) {
            b.window(x, 4, z0, "north", 3, "stone_trim");
            if (Math.abs(x - (x0 + x1) / 2) > 3) {
                b.window(x, 4, z1, "south", 3, "stone_trim");
            }
        }
        int dx = (x0 + x1) / 2 - 1;
        c.fill(dx - 1, 1, z1, dx + 2, 5, z1, "air");
        b.doors(dx, 1, z1, Doors.STEEL, "south");
        c.sign("plant", dx, 7, z1 + 1, "south");
        path(dx - 1, z1 + 1, dx + 2, Hollis.ROAD0 - 2);
        for (int i = 0; i < 2; i++) {
            int bx = x0 + 4 + i * 8;
            set(bx, 1, z0 + 3, "pw_boiler[facing=south]");
            set(bx + 1, 1, z0 + 3, "pw_boiler[facing=south]");
            for (int y = 2; y <= 7; y++) {
                set(bx, y, z0 + 2, "fit_pipe_drop");
            }
        }
        // the steam chambers: long steel boxes the bedding and baggage were rolled into
        for (int x = x1 - 9; x <= x1 - 2; x++) {
            for (int z = z0 + 3; z <= z0 + 6; z++) {
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, z == z0 + 3 || z == z0 + 6 || y == 3 || x == x1 - 2 ? "rd_galv_flat" : "air");
                }
            }
            set(x, 4, z0 + 4, "vessel_pipe[axis=x]");
        }
        set(x1 - 9, 1, z0 + 4, "air");
        set(x1 - 9, 2, z0 + 4, "air");
        set(x1 - 10, 1, z0 + 4, "home_clothes_pile");
        for (int i = 0; i < 6; i++) {
            set(x0 + 3 + c.rng.nextInt(x1 - x0 - 6), 1, z0 + 9 + c.rng.nextInt(z1 - z0 - 11),
                    c.any("hs_cart_linen", "pw_drum[axis=x]", "home_clothes_pile", "fit_laundry_bag", "crate"));
        }
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.04);
        // the stack
        int sx = x1 + 4, sz = (z0 + z1) / 2;
        Round.wall(c.p, sx + 0.5, sz + 0.5, 2.4, 0, 30, "brick", 0);
        Round.wall(c.p, sx + 0.5, sz + 0.5, 2.4, 31, 36, "soot", 0);
        for (int x = x1 + 1; x <= sx - 2; x++) {
            set(x, 6, sz, "vessel_pipe[axis=x]");
        }
    }

    // ------------------------------------------------------------------ the houses

    /**
     * The surgeon's house (historic-quarantine-station 03): two storeys of white clapboard under a
     * tin hip with the lantern on its peak, the veranda across the front and down one side, the
     * picket fence and the gate; the bags packed in the hall.
     */
    private void surgeon() {
        int x0 = -170, x1 = -154, z0 = -42, z1 = -28;
        b.shell(x0, z0, x1, z1, 2, 5, "siding_white", "home_floor_oak", "home_floor_oak");
        b.hip(x0, x1, z0, z1, 11, "minecraft:andesite", 1);
        set((x0 + x1) / 2, 17, (z0 + z1) / 2, "fm_cupola_tin_galv");
        for (int s = 0; s < 2; s++) {
            int y = 2 + s * 5;
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                if (s == 1 || Math.abs(x - (x0 + x1) / 2) > 1) {
                    b.window(x, y, z1, "south", 2, "front_trim_white");
                }
                b.window(x, y, z0, "north", 2, "front_trim_white");
            }
            for (int z = z0 + 3; z <= z1 - 3; z += 4) {
                b.window(x0, y, z, "west", 2, "front_trim_white");
                b.window(x1, y, z, "east", 2, "front_trim_white");
            }
        }
        int dx = (x0 + x1) / 2;
        b.door(dx, 1, z1, Doors.FLAT, "south");
        b.stair(x0 + 2, z0 + 2, 0, 5, "east", "minecraft:oak");
        // the veranda: boards, posts, a rail, the roof at the first floor, across the front and down the east side
        for (int x = x0 - 1; x <= x1 + 3; x++) {
            for (int z = z0; z <= z1 + 3; z++) {
                boolean front = z > z1, side = x > x1;
                if (!front && !side) {
                    continue;
                }
                set(x, 0, z, "ext_porch_boards");
                set(x, 1, z, "air");
                boolean outer = z == z1 + 3 || x == x1 + 3;
                set(x, 5, z, "ext_porch_roof_grey" + (outer ? "_edge" : "") + "[facing=" + (front ? "north" : "west") + "]");
                if (outer) {
                    boolean post = (front ? x - x0 + 1 : z - z0) % 4 == 0 || x == x1 + 3 && z == z1 + 3;
                    if (post) {
                        set(x, 1, z, "ext_post_base");
                        set(x, 2, z, "ext_post");
                        set(x, 3, z, "ext_post");
                        set(x, 4, z, "ext_post_cap[axis=" + (front ? "x" : "z") + "]");
                    } else {
                        set(x, 4, z, "ext_beam[axis=" + (front ? "x" : "z") + "]");
                        if (!(front && Math.abs(x - dx) <= 1)) {
                            set(x, 1, z, (c.chance() < 0.15 ? "ext_rail_broken" : "ext_rail") + "[axis=" + (front ? "x" : "z") + "]");
                        }
                    }
                }
            }
        }
        set(dx + 3, 1, z1 + 1, "ext_rocker[facing=south]");
        set(x1 + 2, 1, z0 + 3, "ext_rocker[facing=east]");
        for (int z = z0 + 2; z <= z0 + 3; z++) {
            set(x0 + 6, 1, z, c.any("ap_bag_pile", "home_clothes_pile", "cardboard_box[facing=north]"));
        }
        // the garden's picket fence and the gate on the path down to the drive
        int fz = Hollis.ROAD0 - 4;
        for (int x = x0 - 4; x <= x1 + 6; x++) {
            boolean gate = Math.abs(x - dx) <= 0;
            set(x, 1, fz, gate ? "yd_picket_gate_white_open[axis=x]" : x % 4 == 0 ? "yd_picket_post_white"
                    : (c.chance() < 0.1 ? "yd_picket_white_broken" : "yd_picket_white") + "[axis=x]");
        }
        for (int z = z0 - 2; z < fz; z++) {
            for (int x : new int[] {x0 - 4, x1 + 6}) {
                set(x, 1, z, z % 4 == 0 ? "yd_picket_post_white" : "yd_picket_white[axis=z]");
            }
        }
        path(dx, z1 + 4, dx, fz - 1);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.04);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 6, 0.03);
    }

    /** The nurses' home: two storeys of buff brick under a slate gable, the rooms off a corridor, the beds made or not. */
    private void nurses() {
        int x0 = -134, x1 = -104, z0 = -40, z1 = -24;
        b.shell(x0, z0, x1, z1, 2, 5, "brick_buff", "floor_lino", "floor_lino");
        b.gable(x0, x1, z0, z1, 11, "minecraft:deepslate_tile", false, "brick_buff", 1);
        for (int s = 0; s < 2; s++) {
            int y = 2 + s * 5;
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                if (Math.abs(x - (x0 + x1) / 2) > 2) {
                    b.window(x, y, z1, "south", 2, "stone_trim");
                }
                b.window(x, y, z0, "north", 2, "stone_trim");
            }
            // the rooms along the north side, the corridor along the south
            int cz = z0 + 6, yf = s * 5;
            for (int x = x0 + 1; x < x1; x++) {
                for (int k = 1; k <= 4; k++) {
                    set(x, yf + k, cz, k <= 2 && x % 5 == 2 ? "air" : "home_wall_cream");
                }
            }
            for (int x = x0 + 5; x < x1; x += 5) {
                c.partZ(x, z0 + 1, cz - 1, yf, 5, "home_wall_cream", "home_wall_cream");
            }
            for (int x = x0 + 3; x < x1; x += 5) {
                set(x, y - 1, z0 + 1, (c.chance() < 0.6 ? "hs_bed_head" : "hs_bed_head_blood") + "[facing=north]");
                set(x, y - 1, z0 + 2, "hs_bed_foot[facing=north]");
                set(x - 1, y - 1, z0 + 1, "hs_nightstand[facing=south]");
            }
            b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y - 1, 0.04);
        }
        int dx = (x0 + x1) / 2;
        b.doors(dx, 1, z1, Doors.FLAT, "south");
        b.stair(x1 - 2, z1 - 2, 0, 5, "north", "minecraft:oak");
        b.stair(x1 - 3, z1 - 2, 0, 5, "north", "minecraft:oak");
        path(dx, z1 + 1, dx + 1, Hollis.ROAD0 - 2);
    }

    // ------------------------------------------------------------------ the wards

    /**
     * The covered walk along the wards' north ends: boards on the ground, a post every four each
     * side, the slate-grey porch roof over, the paths across from the drive.
     */
    private void walk() {
        int x0 = WX0 - 4, x1 = WX0 + 5 * WSTEP + WW + 3;
        for (int x = x0; x <= x1; x++) {
            for (int z = WALK0; z <= WALK1; z++) {
                set(x, 0, z, "ext_porch_boards");
                set(x, 1, z, "air");
                set(x, 4, z, "ext_porch_roof_slate" + (z == WALK0 || z == WALK1 ? "_edge" : "") + "[facing=" + (z == WALK0 ? "south" : "north") + "]");
            }
            for (int z : new int[] {WALK0, WALK1}) {
                if ((x - x0) % 4 == 0) {
                    set(x, 1, z, "ext_post_base");
                    set(x, 2, z, "ext_post");
                    set(x, 3, z, "ext_post_cap[axis=x]");
                } else {
                    set(x, 3, z, "ext_beam[axis=x]");
                }
            }
            if (c.chance() < 0.04) {
                set(x, 4, WALK0 + 1 + c.rng.nextInt(2), "air");
            }
        }
        for (int x : new int[] {x0 + 6, (x0 + x1) / 2, x1 - 6}) {
            path(x - 1, Hollis.ROAD1 + 2, x + 1, WALK0 - 1);
        }
        for (int i = 0; i < 18; i++) {
            int x = x0 + c.rng.nextInt(x1 - x0), z = WALK0 + 1 + c.rng.nextInt(2);
            set(x, 1, z, c.any("hs_gurney_bag_a[facing=east]", "hs_wheelchair_tipped[facing=" + c.dir() + "]", "hs_cart_linen",
                    "blood_splat", "qs_shroud_head[facing=east]", "paper"));
        }
    }

    /**
     * A ward (historic-quarantine-station 01, 02): a long brick pavilion WW wide, its doors on the
     * covered walk, tall windows down both sides, the slate roof with the clerestory along its
     * ridge; inside the beds down both walls with their lockers, the radiators between the
     * windows, the nurses' station by the doors. Ward F was let go years back and has gone the
     * way of Ellis Island's.
     */
    private void ward(int i, int x0) {
        int x1 = x0 + WW - 1, z0 = WZ0, z1 = WZ1, mid = x0 + WW / 2;
        boolean ruin = i == 5;
        double was = b.damage;
        b.damage = ruin ? 0.9 : was;
        b.shell(x0, z0, x1, z1, 1, 6, "brick_red", ruin ? "hs_lino_b" : "hs_lino", "hs_lino");
        for (int z = z0 + 3; z <= z1 - 3; z += 3) {
            b.window(x0, 2, z, "west", 3, "stone_trim");
            b.window(x1, 2, z, "east", 3, "stone_trim");
        }
        for (int x : new int[] {x0 + 3, x1 - 3}) {
            b.window(x, 2, z1, "south", 3, "stone_trim");
            b.window(x, 2, z0, "north", 3, "stone_trim");
        }
        b.doors(mid, 1, z0, Doors.FLAT, "north");
        path(mid, WALK1 + 1, mid + 1, z0 - 1);
        c.sign("ward_" + (char) ('a' + i), mid, 5, z0 - 1, "north");
        // the roof and the clerestory: a monitor five wide on the ridge, windowed down both sides
        b.gable(x0, x1, z0, z1, 7, "minecraft:deepslate_tile", true, "brick_red", 1);
        int m0 = mid - 2, m1 = mid + 2, top = 7 + WW / 2 + 1;
        int foot = 7 + (m0 - (x0 - 1));
        for (int z = z0 + 3; z <= z1 - 3; z++) {
            for (int y = foot; y <= top; y++) {
                boolean end = z == z0 + 3 || z == z1 - 3;
                for (int x = m0; x <= m1; x++) {
                    boolean wall = x == m0 || x == m1 || end;
                    set(x, y, z, wall ? (y == foot ? "stone_trim" : "siding_white") : "air");
                }
            }
            if (z % 2 == 0 && z > z0 + 3 && z < z1 - 3) {
                b.window(m0, foot + 1, z, "west", 1, null);
                b.window(m1, foot + 1, z, "east", 1, null);
            }
        }
        b.gable(m0, m1, z0 + 3, z1 - 3, top + 1, "minecraft:deepslate_tile", true, "siding_white", 1);
        // inside
        for (int z = z0 + 3; z <= z1 - 4; z += 3) {
            boolean blood = c.chance() < (ruin ? 0.05 : 0.25);
            if (!(ruin && c.chance() < 0.5)) {
                set(x0 + 1, 1, z, (blood ? "hs_bed_head_blood" : "hs_bed_head") + "[facing=west]");
                set(x0 + 2, 1, z, (c.chance() < 0.15 ? "hs_bed_foot_body" : "hs_bed_foot") + "[facing=west]");
                set(x0 + 1, 1, z + 1, "hs_nightstand[facing=east]");
            }
            if (!(ruin && c.chance() < 0.5)) {
                set(x1 - 1, 1, z, (blood ? "hs_bed_head_blood" : "hs_bed_head") + "[facing=east]");
                set(x1 - 2, 1, z, (c.chance() < 0.15 ? "hs_bed_foot_body" : "hs_bed_foot") + "[facing=east]");
                set(x1 - 1, 1, z + 1, "hs_nightstand[facing=west]");
            }
            if (!ruin && c.chance() < 0.4) {
                set(x0 + 3, 1, z + 1, c.any("bunk_iv_stand", "hs_iv", "hs_oxygen"));
            }
            set(x0 + 1, 1, z + 2, "home_radiator[facing=east]");
            set(x1 - 1, 1, z + 2, "home_radiator[facing=west]");
        }
        set(mid - 2, 1, z0 + 2, "hs_station[facing=north]");
        set(mid - 3, 1, z0 + 2, "hs_station_end[facing=north]");
        set(mid + 3, 1, z0 + 2, ruin ? "hs_wheelchair_tipped[facing=west]" : "hs_cart_med[facing=north]");
        b.lights(x0, z0, x1, z1, 5, ruin || !c.power ? 0 : 0.15);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, ruin ? 0.25 : 0.06);
        if (ruin) {
            // the roof in over the middle, the floor gone to moss, the ward's own murals on the walls
            for (int z = z0 + 14; z <= z0 + 22; z++) {
                for (int x = x0 + 1; x < x1; x++) {
                    for (int y = 6; y <= top + 4; y++) {
                        set(x, y, z, "air");
                    }
                    if (c.chance() < 0.5) {
                        set(x, 1, z, c.any("rubble_pile", "moss_carpet", "minecraft:short_grass", "debris_chunk"));
                    }
                }
            }
        }
        b.damage = was;
    }

    // ------------------------------------------------------------------ the dead

    /** The morgue: stone, one storey, a slate gable; the drawers along the back, the table, the gurneys waiting. */
    private void morgue() {
        int x0 = 0, x1 = 16, z0 = 66, z1 = 80;
        b.shell(x0, z0, x1, z1, 1, 5, "ch_ashlar", "hs_tile", "hs_tile");
        b.gable(x0, x1, z0, z1, 6, "minecraft:deepslate_tile", false, "ch_ashlar", 1);
        for (int x = x0 + 3; x <= x1 - 3; x += 5) {
            b.window(x, 3, z0, "north", 1, "stone_trim");
        }
        b.doors(x0 + 7, 1, z0, Doors.STEEL, "north");
        c.sign("morgue", x0 + 7, 4, z0 - 1, "north");
        for (int x = x0 + 2; x < x1 - 1; x++) {
            set(x, 1, z1 - 1, x % 3 == 0 ? "hs_morgue_open[facing=north]" : "hs_morgue_lo[facing=north]");
            set(x, 2, z1 - 1, "hs_morgue_hi[facing=north]");
        }
        set(x0 + 8, 1, z0 + 7, "hs_autopsy[facing=north]");
        for (int i = 0; i < 5; i++) {
            set(x0 + 2 + i * 3, 1, z0 + 3, c.any("hs_gurney_bag_a[facing=east]", "hs_gurney_bag_b[facing=east]", "qs_shroud_head[facing=east]"));
        }
        path(x0 + 7, WALK1 + 1, x0 + 8, z0 - 1);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.05);
    }

    /**
     * The old burying ground (body-bags-mass-graves 03, 04): iron railings round it, the gate at
     * the walk's end, the headstones in their rows gone over and sunk, a few cypress-dark spruces,
     * and the granite memorial put up to them all inside the gate.
     */
    private void cemetery() {
        int x0 = -170, x1 = -30, z0 = 70, z1 = 136;
        int gx = (x0 + x1) / 2;
        for (int x = x0; x <= x1; x++) {
            for (int z : new int[] {z0, z1}) {
                if (z == z0 && Math.abs(x - gx) <= 1) {
                    continue;
                }
                if (c.chance() > 0.06) {
                    set(x, 1, z, "cv_iron_fence[axis=x]");
                }
            }
        }
        for (int z = z0; z <= z1; z++) {
            for (int x : new int[] {x0, x1}) {
                if (c.chance() > 0.06) {
                    set(x, 1, z, "cv_iron_fence[axis=z]");
                }
            }
        }
        path(gx - 1, WZ1 + 2, gx + 1, z1 - 2);
        // the memorial's facing north: tile _l is the viewer's left from the north (east side, larger x)
        set(gx, 1, z0 + 4, "qs_memorial_l[facing=north]");
        set(gx - 1, 1, z0 + 4, "qs_memorial_r[facing=north]");
        for (int x = x0 + 3; x <= x1 - 3; x += 3) {
            if (Math.abs(x - gx) <= 2) {
                continue;
            }
            for (int z = z0 + 5; z <= z1 - 3; z += 4) {
                double r = c.chance();
                if (r < 0.1) {
                    continue;
                }
                String stone = "ch_headstone_" + c.rng.nextInt(6) + (r < 0.4 ? "_sunk" : "");
                set(x, 1, z, stone + "[facing=" + (c.chance() < 0.85 ? "north" : c.dir()) + "]");
                set(x, 0, z + 1, c.chance() < 0.3 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                if (c.chance() < 0.08) {
                    set(x, 1, z + 1, "ch_grave_open");
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            int x = x0 + 4 + c.rng.nextInt(x1 - x0 - 8), z = z0 + 4 + c.rng.nextInt(z1 - z0 - 8);
            if (Math.abs(x - gx) > 3 && c.empty(x, 1, z)) {
                spruce(x, z);
            }
        }
    }

    /** A dark narrow spruce, the burying ground's planting gone tall. */
    private void spruce(int x, int z) {
        int h = 7 + c.rng.nextInt(5);
        for (int y = 1; y <= h; y++) {
            set(x, y, z, "minecraft:spruce_log");
        }
        for (int y = 3; y <= h + 1; y++) {
            int r = y > h - 1 ? 0 : y > h - 4 ? 1 : (y % 2 == 0 ? 2 : 1);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if ((dx != 0 || dz != 0 || y > h) && Math.abs(dx) + Math.abs(dz) <= r + 1) {
                        c.p.setIfAir(x + dx, y, z + dz, "minecraft:spruce_leaves[persistent=true]");
                    }
                }
            }
        }
    }
}
