package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.University.S;
import static com.zomboidcraft.world.county.University.SCI_X0;
import static com.zomboidcraft.world.county.University.SCI_X1;
import static com.zomboidcraft.world.county.University.SCI_Z0;
import static com.zomboidcraft.world.county.University.SCI_Z1;

/**
 * Prescott Science Hall, down the quad's east side: four floors of buff brick with ribbon windows
 * and stone bands at the floors, PRESCOTT SCIENCE HALL in bronze over the doors, the greenhouse on
 * the roof. A corridor runs the length of every floor between the fire stairs at its ends, labs
 * and classrooms either side: physics and the workshop on the ground floor with the entry hall,
 * chemistry over it, biology over that, the research floor at the top.
 *
 * <p>The research floor took blood samples from Mercy General in the first week. Someone sealed
 * its corridor with plastic sheeting and tape; the sheeting has been torn through from inside.
 */
final class UniScience {
    private final University u;
    private static final int[] YS = {0, S, 2 * S, 3 * S, 4 * S};
    private static final int ROOF = 4 * S;
    /** The corridor's walls; the stairs fill its ends. */
    private static final int CW = 175, CE = 181, ST_X = 176;
    private static final int SN_Z = SCI_Z0 + 1, SS_Z = SCI_Z1 - 10;
    /** The cross walls between rooms, either side of the corridor. */
    private static final int[] CROSS = {70, 86, 102};
    /** The entry hall on the ground floor's west side, the main doors in its outer wall. */
    private static final int HALL_Z0 = 87, HALL_Z1 = 101, DOOR_Z = 88;

    private enum Room { CLASS, PHYSICS, WORKSHOP, CHEM, STOCK, BIO, SPECIMEN, OFFICES, RESEARCH, COLD, RESTROOM }

    UniScience(University u) {
        this.u = u;
    }

    private void set(int x, int y, int z, String b) {
        u.set(x, y, z, b);
    }

    private double chance() {
        return u.chance();
    }

    void build() {
        shell();
        for (int f = 0; f < 4; f++) {
            floor(f);
        }
        u.stair(ST_X, SN_Z, true, YS, "brick_buff", "un_roof");
        u.stair(ST_X, SS_Z, false, YS, "brick_buff", "un_roof");
        roof();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int x = SCI_X0; x <= SCI_X1; x++) {
            for (int z = SCI_Z0; z <= SCI_Z1; z++) {
                set(x, 0, z, "concrete_dark");
                for (int f = 1; f < 4; f++) {
                    set(x, f * S, z, "un_slab_vct");
                }
                set(x, ROOF, z, "un_roof");
            }
        }
        for (int y = 1; y <= ROOF + 1; y++) {
            for (int z = SCI_Z0; z <= SCI_Z1; z++) {
                set(SCI_X0, y, z, wallAt(y, z - SCI_Z0, "west"));
                set(SCI_X1, y, z, wallAt(y, z - SCI_Z0, "east"));
            }
            for (int x = SCI_X0; x <= SCI_X1; x++) {
                set(x, y, SCI_Z0, wallAt(y, x - SCI_X0, "north"));
                set(x, y, SCI_Z1, wallAt(y, x - SCI_X0, "south"));
            }
        }
        // the entrance: a stone surround, the doors, the name over them, the steps' rails
        for (int y = 1; y <= 5; y++) {
            for (int z = DOOR_Z - 2; z <= DOOR_Z + 5; z++) {
                set(SCI_X0, y, z, y == 5 || z == DOOR_Z - 2 || z == DOOR_Z + 5 ? "stone_trim" : "brick_buff");
            }
        }
        u.doors(SCI_X0, 0, DOOR_Z, "shop_door", "west");
        u.doors(SCI_X0, 0, DOOR_Z + 2, "shop_door", "west");
        for (int z = DOOR_Z; z <= DOOR_Z + 3; z++) {
            set(SCI_X0, 3, z, "glass_spandrel");
        }
        u.row("un_name_science_", 14, SCI_X0 - 1, 6, DOOR_Z - 5, "west", 0.05);
        set(SCI_X0 - 1, 4, DOOR_Z - 1, "un_seal_2[facing=west]");
        // a back door to the service walk
        u.door(SCI_X1, 0, 58, "fire_door", "east");
    }

    /** The outer wall at height y, a along it: stone bands at the floors, ribbon windows between brick piers. */
    private String wallAt(int y, int a, String out) {
        int k = y % S;
        if (y > ROOF) {
            return "stone_trim";
        }
        if (k == 0) {
            return "stone_trim";
        }
        boolean pier = a % 4 == 0 || a <= 1 || a >= (out.equals("west") || out.equals("east") ? SCI_Z1 - SCI_Z0 - 1 : SCI_X1 - SCI_X0 - 1);
        if ((k == 2 || k == 3) && !pier) {
            String state = chance() < u.b.boarded ? "boarded" : chance() < u.b.damage * 0.5 ? (chance() < 0.7 ? "broken" : "empty")
                    : u.power && chance() < 0.08 ? "lit" : "glass";
            return "ext_win_alu_one_" + state + "_" + (k == 2 ? "lo" : "hi") + "[facing=" + out + "]";
        }
        return u.b.wall("brick_buff", y);
    }

    // ------------------------------------------------------------------ the floors

    /** The rooms each side of the corridor on a floor, west then east, between the cross walls. */
    private static final Room[][][] PLAN = {
            {{Room.CLASS, Room.OFFICES, null, Room.STOCK}, {Room.PHYSICS, Room.PHYSICS, Room.WORKSHOP, Room.RESTROOM}},
            {{Room.CHEM, Room.CHEM, Room.STOCK, Room.CHEM}, {Room.CLASS, Room.CHEM, Room.CHEM, Room.RESTROOM}},
            {{Room.BIO, Room.SPECIMEN, Room.BIO, Room.CLASS}, {Room.BIO, Room.BIO, Room.OFFICES, Room.RESTROOM}},
            {{Room.RESEARCH, Room.COLD, Room.OFFICES, Room.RESEARCH}, {Room.RESEARCH, Room.RESEARCH, Room.OFFICES, Room.RESTROOM}},
    };

    private void floor(int f) {
        int y = f * S;
        boolean sealed = f == 3;
        // the corridor walls, the cross walls, the rooms off them
        u.partZ(CW, SCI_Z0 + 1, SCI_Z1 - 1, y, S, "brick_buff", "drywall");
        u.partZ(CE, SCI_Z0 + 1, SCI_Z1 - 1, y, S, "brick_buff", "drywall");
        for (int z : CROSS) {
            u.partX(SCI_X0 + 1, CW - 1, z, y, S, "drywall", "drywall");
            u.partX(CE + 1, SCI_X1 - 1, z, y, S, "drywall", "drywall");
        }
        if (f == 0) {
            u.opening(CW, CW, HALL_Z0 + 2, HALL_Z1 - 2, 0, true);
            hall();
        }
        for (int z = SCI_Z0 + 1; z < SCI_Z1; z++) {
            for (int x = CW + 1; x < CE; x++) {
                set(x, y, z, f == 0 ? "sc_vct" : "un_slab_vct");
            }
        }
        if (f == 0) {
            for (int x = SCI_X0 + 1; x < SCI_X1; x++) {
                for (int z = SCI_Z0 + 1; z < SCI_Z1; z++) {
                    if (u.get(x, 0, z).equals("concrete_dark")) {
                        set(x, 0, z, chance() < 0.2 ? "sc_vct_a" : "sc_vct");
                    }
                }
            }
        }
        int[] zs = {SCI_Z0, CROSS[0], CROSS[1], CROSS[2], SCI_Z1};
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? SCI_X0 + 1 : CE + 1, x1 = side == 0 ? CW - 1 : SCI_X1 - 1;
            int dx = side == 0 ? CW : CE;
            for (int i = 0; i < 4; i++) {
                Room r = PLAN[f][side][i];
                int z0 = zs[i] + 1, z1 = zs[i + 1] - 1;
                if (r == null) {
                    continue;
                }
                // the door off the corridor (clear of the stairs at its ends), a sign by it
                int dz = i == 0 ? z1 - 2 : i == 3 ? z0 + 2 : (z0 + z1) / 2;
                u.door(dx, y, dz, r == Room.COLD ? "fire_door" : "flat_door", side == 0 ? "west" : "east");
                String sg = switch (r) {
                    case CHEM, STOCK -> "chem";
                    case BIO, SPECIMEN -> "bio";
                    case PHYSICS, WORKSHOP -> "physics";
                    case OFFICES -> "faculty";
                    case RESEARCH, COLD -> "staff";
                    default -> null;
                };
                if (sg != null && (dz + 3 < z1 || dz - 3 > z0)) {
                    u.sign(sg, side == 0 ? CW + 1 : CE - 1, y + 3, dz + 2, side == 0 ? "east" : "west");
                }
                room(r, x0, z0, x1, z1, y, side == 0, sealed);
            }
        }
        // the corridor: display cases and the periodic table, the notice boards, extinguishers
        corridor(y, f);
        u.lamps(SCI_X0, SCI_Z0, SCI_X1, SCI_Z1, y + S, sealed ? 0.05 : 0.3);
    }

    /** The entry hall: the trophy cases, the donors' wall, the pendulum in its rail, benches, the founder's portrait. */
    private void hall() {
        int x0 = SCI_X0 + 1, x1 = CW - 1, mz = (HALL_Z0 + HALL_Z1) / 2;
        for (int x = x0 + 2; x <= x1 - 3; x += 2) {
            set(x, 1, HALL_Z0, "sc_trophy_lo_" + (x % 4 / 2) + "[facing=south]");
            set(x, 2, HALL_Z0, "sc_trophy_hi_" + (x % 4 / 2) + "[facing=south]");
        }
        u.picture("cv_frieze_14_", 14, 1, x0 + 3, 3, HALL_Z1, "north");
        set(x0 + 9, 2, HALL_Z1, "cv_portrait[facing=north]");
        // the pendulum: a brass bob on its chain from the ceiling, the rail round it, the pegs knocked flat
        int px = (x0 + x1) / 2 + 1;
        for (int y = 2; y < S; y++) {
            set(px, y, mz, "cv_chain");
        }
        set(px, 1, mz, "cv_bell_c");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    set(px + dx, 0, mz + dz, "stone_trim");
                    if ((dx + dz) % 2 == 0) {
                        set(px + dx, 1, mz + dz, "cv_bar_post");
                    }
                }
            }
        }
        for (int z : new int[] {HALL_Z0 + 3, HALL_Z1 - 3}) {
            set(x0 + 3, 1, z, "cv_hall_bench[facing=east]");
        }
        set(x1, 1, HALL_Z0 + 1, "sc_mailboxes[facing=west]");
        u.sign("desk", x0, 4, mz - 1, "east");
    }

    private void corridor(int y, int f) {
        int cx = CW + 1, ex = CE - 1;
        if (f > 0) {
            u.picture("un_periodic_", 3, 2, cx, y + 2, 71, "east");
            u.poster(cx, y + 2, 98, "east");
            set(cx, y + 2, 100, "bunk_extinguisher[facing=east]");
        }
        u.picture("un_bulletin_rooms_", 2, 1, ex, y + 2, 84, "west");
        u.poster(ex, y + 2, 88, "west");
        set(ex, y + 1, 82, "sc_fountain[facing=west]");
        set(ex, y + 4, 72, "bk_wall_clock[facing=west]");
        if (f == 3) {
            // the plastic curtains across the corridor at both ends, slashed through; the tape, the warning
            for (int z : new int[] {69, 100}) {
                for (int x = cx; x <= ex; x++) {
                    for (int yy = y + 1; yy <= y + 3; yy++) {
                        boolean torn = x == cx + 2 && yy <= y + 2;
                        set(x, yy, z, torn ? "air" : "hs_plastic[axis=x]");
                    }
                }
                set(cx, y + 1, z - 1, "hs_biohazard[facing=north]");
                set(ex, y + 1, z + 1, "bunk_hazmat[facing=south]");
                set(ex, y + 2, z + 1, "bunk_hazmat_top[facing=south]");
            }
            u.row("hs_spray_dont_", 3, cx, y + 2, 72, "east", 0);
        }
        u.mess(cx, SCI_Z0 + 11, ex, SCI_Z1 - 11, y, f == 3 ? 0.1 : 0.05);
    }

    /** A room between x0..x1, z0..z1 (insides) on the floor at y; west: it's on the corridor's west side. */
    private void room(Room r, int x0, int z0, int x1, int z1, int y, boolean west, boolean sealed) {
        String in = west ? "east" : "west", out = west ? "west" : "east";
        int wx = west ? x0 : x1, cx = west ? x1 : x0;
        switch (r) {
            case CLASS -> {
                // desks in rows facing the board on the end wall
                for (int x = x0 + 3; x <= x1 - 2; x += 2) {
                    for (int z = z0 + 4; z <= z1 - 1; z += 2) {
                        set(x, y + 1, z, chance() < 0.12 ? "sc_desk_a_tipped[facing=" + u.dir() + "]" : "sc_desk_a[facing=north]");
                    }
                }
                u.row("sc_chalk_lesson_", 3, (x0 + x1) / 2 - 1, y + 2, z0, "south", 0);
                set((x0 + x1) / 2 + 3, y + 1, z0 + 1, "sc_tdesk_ped[facing=south]");
                set(wx, y + 1, z1, "sc_av_cart[facing=" + in + "]");
            }
            case PHYSICS -> {
                for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                    for (int x = x0 + 2; x <= x1 - 2; x++) {
                        set(x, y + 1, z, "un_lab_bench[facing=north]");
                        set(x, y + 1, z + 1, x % 3 == 0 ? "un_stool" : "air");
                    }
                }
                set(wx, y + 1, z0, "sc_ball_cage");
                set(wx, y + 1, z1, "store_pegboard[facing=" + in + "]");
                u.row("sc_chalk_pickup_", 3, (x0 + x1) / 2 - 1, y + 2, z0, "south", 0);
            }
            case WORKSHOP -> {
                for (int z = z0 + 1; z <= z1 - 1; z += 3) {
                    set(wx, y + 1, z, "home_workbench[facing=" + in + "]");
                    set(wx, y + 2, z + 1, "store_pegboard[facing=" + in + "]");
                }
                set(x0 + 4, y + 1, z0 + 4, "home_garage_shelf[facing=south]");
                set(x0 + 6, y + 1, z0 + 4, "propane_tank");
                set(x0 + 8, y + 1, z0 + 6, "cardboard_box[facing=" + u.dir() + "]");
            }
            case CHEM -> {
                // islands of benches with a sink in each, the fume hoods along the outer wall, the shelves on the end walls
                for (int z = z0 + 3; z <= z1 - 3; z += 5) {
                    for (int x = x0 + 4; x <= x1 - 3; x++) {
                        boolean sink = x == (x0 + x1) / 2;
                        set(x, y + 1, z, (sink ? "un_lab_sink" : "un_lab_bench") + "[facing=north]");
                        set(x, y + 1, z + 1, (sink ? "un_lab_sink" : "un_lab_bench") + "[facing=south]");
                        if (x % 2 == 0) {
                            set(x, y + 1, z - 1, chance() < 0.2 ? "air" : "un_stool");
                        }
                    }
                }
                for (int z = z0 + 1; z <= z1 - 1; z += 3) {
                    set(wx, y + 1, z, "un_fume_lo[facing=" + in + "]");
                    set(wx, y + 2, z, "un_fume_hi[facing=" + in + "]");
                }
                for (int x = x0 + 3; x <= x1 - 3; x += 2) {
                    set(x, y + 1, z1, "un_chem_shelf[facing=north]");
                }
                set(cx, y + 3, z0 + 1, "un_periodic_" + u.rng.nextInt(6) + "[facing=" + out + "]");
                set(wx + (west ? 1 : -1), y + 1, z1 - 1, "bunk_first_aid[facing=" + in + "]");
            }
            case STOCK -> {
                for (int z = z0 + 1; z <= z1 - 1; z += 2) {
                    for (int x = x0 + 1; x <= x1 - 2; x++) {
                        if (x % 4 != 0) {
                            set(x, y + 1, z, "un_chem_shelf[facing=" + (z % 4 == 1 ? "north" : "south") + "]");
                        }
                    }
                }
                set(wx, y + 1, z0, "hs_biohazard[facing=" + in + "]");
                set(wx, y + 1, z1, "bunk_drum_water");
            }
            case BIO -> {
                for (int z = z0 + 3; z <= z1 - 3; z += 4) {
                    for (int x = x0 + 4; x <= x1 - 3; x++) {
                        set(x, y + 1, z, (x % 3 == 0 ? "un_lab_scope" : "un_lab_bench") + "[facing=north]");
                        if (x % 2 == 0) {
                            set(x, y + 1, z - 1, "un_stool");
                        }
                    }
                }
                set(wx, y + 1, z0 + 1, "un_skeleton_lo[facing=" + in + "]");
                set(wx, y + 2, z0 + 1, "un_skeleton_hi[facing=" + in + "]");
                for (int z = z0 + 4; z <= z1 - 1; z += 2) {
                    set(wx, y + 1, z, "un_chem_shelf[facing=" + in + "]");
                }
                u.row("sc_chalk_lesson_", 3, (x0 + x1) / 2 - 1, y + 2, z0, "south", 0);
                set(x0 + 3, y + 1, z1, "sc_tank[facing=north]");
            }
            case SPECIMEN -> {
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    if (x % 5 != 0) {
                        set(x, y + 1, z0, "cellar_shelf_preserves[facing=south]");
                        set(x, y + 1, z1, "cellar_shelf_preserves[facing=north]");
                    }
                }
                set((x0 + x1) / 2, y + 1, (z0 + z1) / 2, "hs_autopsy[facing=north]");
                set((x0 + x1) / 2 + 2, y + 1, (z0 + z1) / 2, "hs_mayo[facing=north]");
                set(wx, y + 1, (z0 + z1) / 2, "un_skeleton_lo[facing=" + in + "]");
                set(wx, y + 2, (z0 + z1) / 2, "un_skeleton_hi[facing=" + in + "]");
            }
            case OFFICES -> {
                // three offices off a short hall from the door
                int span = (z1 - z0 + 1) / 3;
                int hx = west ? x1 - 2 : x0 + 2;
                for (int k = 1; k < 3; k++) {
                    int z = z0 + k * span;
                    if (west) {
                        u.partX(x0, hx - 1, z, y, S, "drywall", "drywall");
                    } else {
                        u.partX(hx + 1, x1, z, y, S, "drywall", "drywall");
                    }
                }
                u.partZ(west ? hx - 1 : hx + 1, z0, z1, y, S, "drywall", "drywall");
                for (int k = 0; k < 3; k++) {
                    int oz0 = z0 + k * span + (k == 0 ? 0 : 1), oz1 = k == 2 ? z1 : z0 + (k + 1) * span - 1;
                    u.door(west ? hx - 1 : hx + 1, y, (oz0 + oz1) / 2, "flat_door", in);
                    int dx = west ? x0 + 2 : x1 - 2;
                    set(dx, y + 1, (oz0 + oz1) / 2, "home_desk[facing=" + in + "]");
                    set(dx + (west ? -1 : 1), y + 1, (oz0 + oz1) / 2, chance() < 0.2 ? "office_chair_fallen[facing=" + u.dir() + "]" : "office_chair[facing=" + in + "]");
                    set(wx, y + 1, oz0, "filing_cabinet[facing=" + in + "]");
                    set(west ? hx - 2 : hx + 2, y + 1, oz1, "home_bookcase[facing=" + out + "]");
                    set(west ? hx - 2 : hx + 2, y + 2, oz1, "home_bookcase_top[facing=" + out + "]");
                }
            }
            case RESEARCH -> {
                for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                    for (int x = x0 + 3; x <= x1 - 3; x++) {
                        set(x, y + 1, z, (x % 4 == 0 ? "un_lab_scope" : x % 4 == 2 ? "hs_lab_bench" : "un_lab_bench") + "[facing=north]");
                    }
                }
                for (int z = z0 + 1; z <= z1 - 1; z += 3) {
                    set(wx, y + 1, z, "un_fume_lo[facing=" + in + "]");
                    set(wx, y + 2, z, "un_fume_hi[facing=" + in + "]");
                }
                set(cx + (west ? -1 : 1), y + 1, z0, "hs_bloodfridge" + (u.power ? "_on" : "") + "[facing=south]");
                set(cx + (west ? -2 : 2), y + 1, z0, "hs_sharps[facing=south]");
                set((x0 + x1) / 2, y + 1, z1, "bunk_cage[facing=north]");
                set((x0 + x1) / 2 + 1, y + 1, z1, "bunk_cage[facing=north]");
                for (int i = 0; i < 3; i++) {
                    int x = x0 + 1 + u.rng.nextInt(x1 - x0 - 1), z = z0 + 1 + u.rng.nextInt(z1 - z0 - 1);
                    if (u.empty(x, y + 1, z)) {
                        set(x, y + 1, z, chance() < 0.5 ? "home_blood_pool" : "bunk_bodybag[facing=" + u.dir() + "]");
                    }
                }
            }
            case COLD -> {
                // the cold room: steel shelves, the freezers, the samples gone warm
                for (int z = z0 + 1; z <= z1 - 1; z += 2) {
                    set(wx, y + 1, z, "hs_bloodfridge[facing=" + in + "]");
                }
                for (int x = x0 + 2; x <= x1 - 2; x += 2) {
                    set(x, y + 1, z0, "cellar_shelf_medical[facing=south]");
                    set(x, y + 1, z1, "cellar_shelf_medical[facing=north]");
                }
                set((x0 + x1) / 2, y + 1, (z0 + z1) / 2, "bunk_bodybag_head[facing=north]");
            }
            case RESTROOM -> {
                // a lobby off the corridor, the two rooms behind it either side of a block wall
                int vz = z0 + 4, xm = (x0 + x1) / 2;
                u.partX(x0, x1, vz, y, S, "sc_cmu_dado_a", "sc_cmu");
                u.partZ(xm, vz, z1, y, S, "sc_cmu_dado_a", "sc_cmu");
                u.door((x0 + xm) / 2, y, vz, "flat_door", "south");
                u.door((xm + x1) / 2, y, vz, "flat_door", "south");
                for (int x = x0; x <= x1; x++) {
                    for (int z = vz + 1; z <= z1; z++) {
                        if (x != xm && y == 0) {
                            set(x, y, z, "bunk_tile");
                        }
                    }
                    if (x % 2 == 0 && x != xm) {
                        set(x, y + 1, z1, "home_toilet[facing=north]");
                    }
                }
                set(xm - 1, y + 1, vz + 2, "hs_sink[facing=west]");
                set(xm + 1, y + 1, vz + 2, "hs_sink[facing=east]");
                set((x0 + xm) / 2 + 1, y + 3, vz - 1, "tx_sign_toilets_0[facing=north]");
                set((xm + x1) / 2 + 1, y + 3, vz - 1, "tx_sign_toilets_1[facing=north]");
            }
        }
        u.mess(x0, z0, x1, z1, y, sealed ? 0.05 : 0.025);
    }

    // ------------------------------------------------------------------ the roof

    /** The greenhouse on the roof over the east rooms, its glass gone in places, the plants dead in their beds. */
    private void roof() {
        int gx0 = CE + 2, gx1 = SCI_X1 - 3, gz0 = SCI_Z0 + 12, gz1 = SCI_Z0 + 34, top = ROOF + 4;
        for (int x = gx0; x <= gx1; x++) {
            for (int z = gz0; z <= gz1; z++) {
                boolean edge = x == gx0 || x == gx1 || z == gz0 || z == gz1;
                boolean post = (x == gx0 || x == gx1) && (z - gz0) % 4 == 0 || (z == gz0 || z == gz1) && (x - gx0) % 4 == 0;
                for (int y = ROOF + 1; y < top; y++) {
                    if (!edge) {
                        continue;
                    }
                    String axis = z == gz0 || z == gz1 ? "x" : "z";
                    set(x, y, z, post ? "steel_mullion" : y == ROOF + 1 ? "glass_spandrel"
                            : chance() < u.b.damage * 0.4 ? "tx_glass_broken[axis=" + axis + "]" : "tx_glass[axis=" + axis + "]");
                }
                set(x, top, z, edge ? "steel_mullion" : chance() < u.b.damage * 0.3 ? "air" : "tx_roof_glass");
            }
        }
        // the door from the stair hut side, the beds in rows, the benches of pots
        u.opening(gx0, gx0, gz0 + 2, gz0 + 3, ROOF, false);
        for (int z = gz0 + 2; z <= gz1 - 2; z++) {
            for (int x : new int[] {gx0 + 3, gx0 + 4, gx0 + 8, gx0 + 9}) {
                if (x < gx1 - 1) {
                    set(x, ROOF + 1, z, chance() < 0.3 ? "bed_soil_b" : "bed_soil");
                    if (chance() < 0.5) {
                        set(x, ROOF + 2, z, chance() < 0.75 ? "home_plant_dead" : "home_plant");
                    }
                }
            }
        }
        for (int z = gz0 + 2; z <= gz1 - 2; z += 2) {
            set(gx1 - 1, ROOF + 1, z, "home_workbench[facing=west]");
            set(gx1 - 1, ROOF + 2, z, chance() < 0.5 ? "pk_planter" : "home_plant_dead");
        }
        set(gx0 + 1, ROOF + 1, gz1 - 1, "bunk_drum_water");
        // the air handlers on the rest of the roof
        for (int z = SCI_Z0 + 44; z <= SCI_Z1 - 14; z += 8) {
            set(162, ROOF + 1, z, "hvac");
            set(163, ROOF + 1, z, "hvac");
        }
    }
}
