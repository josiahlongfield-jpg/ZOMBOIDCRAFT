package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A county school: the elementary in town, or the high school out on the highway. There are no
 * photos of one in the library yet, so this follows what the schools of the sixties and seventies
 * are known for: a long flat-roofed brick wing of classrooms (two storeys for the high school) with
 * ribbon windows over coloured enamel panels, the entry raised over the rest with the name in
 * aluminium letters and a canopy out to the bus loop, the gym at the end standing taller, the
 * letter board on its brick base out on the lawn.
 *
 * Inside, a corridor runs the length of the wing between painted block walls with the school's
 * colour up to the chair rail: lockers, the drinking fountain, the pennants and the banner, the
 * children's mural; on the front side the office (the counter, the mail slots, the PA, the
 * principal's room behind), the lobby with the trophy case, the nurse, the library; classrooms on
 * both sides (the chalkboard and the alphabet, chair-desks in rows, the teacher's desk, cubbies and
 * coat hooks, the reading corner, the TV on its cart), the restrooms by the gym. The gym doubled as
 * the cafeteria (the serving line and the kitchen behind it) and the county ran its shelter there
 * at the end: cots across the court, water and blankets stacked by the wall, the registration
 * table, the wall of notes the families left.
 *
 * Every classroom tells its own end: left tidy with the chairs at the desks, left in a hurry (desks
 * knocked over, bags on the floor), barricaded (desks piled against the door from inside), or
 * overrun.
 *
 * Drawn in its lot's frame: the bus loop along z = 1..8, the walk to the door, the front wall at
 * Z0 facing north, the back wall at d - 2. The wing is x 0..GX, the gym GX..w - 1. In the wing
 * the front rooms run to the wall at ZA, the corridor to ZB, the back rooms behind; each storey ST
 * high, its drop ceiling in the top block.
 */
final class School {
    static final int Z0 = 12, ST = 5;
    /** The foot of the high school's stair up to the roof, along the top floor's corridor. */
    private static final int ROOF_STAIR = 8;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private record Room(int x0, int x1, int z0, int z1, boolean front, int f, String kind, String no) {
        int y() {
            return f * ST;
        }

        /** The edge on the corridor, the edge on the windows, and the step from the windows inward. */
        int hall() {
            return front ? z1 : z0;
        }

        int win() {
            return front ? z0 : z1;
        }

        int in() {
            return front ? 1 : -1;
        }

        int door() {
            return x1 - 1;
        }
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final String brick, key, dado;
    private final boolean lit, shelter, overrun;
    private int w, x1, z1, gx, za, zb, floors, top, gh;
    private boolean high;
    /** The front loop given over to an aid station: the buses and the parent's car gone from it. */
    boolean evacuated;
    private final List<Room> rooms = new ArrayList<>();
    /** Per storey, the x of each door through the corridor's front and back walls (no lockers there). */
    private final List<List<Integer>> frontDoors = new ArrayList<>(), backDoors = new ArrayList<>();

    School(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.3 + rng.nextDouble() * 0.4;
        b.boarded = rng.nextDouble() < 0.35 ? 0.45 : 0.12;
        this.brick = pick("brick_red", "brick_buff", "brick_orange", "brick_red");
        this.key = String.valueOf("abc".charAt(rng.nextInt(3)));
        this.dado = "sc_cmu_dado_" + key;
        this.lit = rng.nextDouble() < 0.35;
        this.shelter = rng.nextDouble() < 0.7;
        this.overrun = rng.nextDouble() < 0.3;
        b.lit = lit ? 0.15 : 0;
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private double chance() {
        return rng.nextDouble();
    }

    /** A school w wide (at least 44) and d deep (at least 30); high: the two-storey high school. */
    void build(int w, int d, boolean high) {
        this.w = w;
        this.high = high;
        x1 = w - 1;
        z1 = d - 2;
        gx = x1 - 15;
        za = Z0 + 8;
        zb = za + 4;
        floors = high ? 2 : 1;
        top = floors * ST;
        gh = high ? 11 : 9;
        grounds();
        shell();
        for (int f = 0; f < floors; f++) {
            frontDoors.add(new ArrayList<>());
            backDoors.add(new ArrayList<>());
            plan(f);
        }
        for (Room r : rooms) {
            room(r);
        }
        for (int f = 0; f < floors; f++) {
            corridor(f);
        }
        if (high) {
            stairs();
        }
        gym();
        front();
        backAndSides();
        roof();
        if (high) {
            // on up from the top floor's corridor (two lanes of it, the lockers held back) to a hut on the roof
            RoofHut.flight(p, rng, ROOF_STAIR, za + 1, top - ST, top, brick, "stairs_concrete", "north", "ext_rail_edge_dark");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static int[] step(String dir) {
        return switch (dir) {
            case "north" -> new int[] {0, -1};
            case "south" -> new int[] {0, 1};
            case "east" -> new int[] {1, 0};
            default -> new int[] {-1, 0};
        };
    }

    /** The viewer's right as they look at a face turned that way. */
    private static int[] right(String facing) {
        return switch (facing) {
            case "north" -> new int[] {-1, 0};
            case "south" -> new int[] {1, 0};
            case "east" -> new int[] {0, -1};
            default -> new int[] {0, 1};
        };
    }

    /** A row of n tiles prefix0.. on a face turned that way, tile 0 (the viewer's left) at (x, z). */
    private void tiles(String prefix, int n, int x, int y, int z, String facing) {
        int[] r = right(facing);
        for (int i = 0; i < n; i++) {
            p.set(x + r[0] * i, y, z + r[1] * i, prefix + i + "[facing=" + facing + "]");
        }
    }

    /** The cell n tiles to the left of (x, z) as seen on a face turned that way (to start a row centred there). */
    private int[] leftOf(int x, int z, String facing, int n) {
        int[] r = right(facing);
        return new int[] {x - r[0] * n, z - r[1] * n};
    }

    private String chair(String facing) {
        double r = chance();
        return r < 0.7 ? "office_chair[facing=" + facing + "]" : r < 0.85 ? "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]" : "air";
    }

    private boolean empty(int x, int y, int z) {
        String s = p.get(x, y, z);
        return s == null || s.equals("air");
    }

    /** Splits a..b into rooms about `target` wide with a wall between each: {x0, x1} pairs. */
    private static List<int[]> split(int a, int b, int target) {
        int total = b - a + 1;
        int n = Math.max(1, Math.round((total + 1) / (float) (target + 1)));
        int cells = total - (n - 1);
        List<int[]> out = new ArrayList<>();
        int x = a;
        for (int i = 0; i < n; i++) {
            int wdt = cells / n + (i < cells % n ? 1 : 0);
            out.add(new int[] {x, x + wdt - 1});
            x += wdt + 1;
        }
        return out;
    }

    // ------------------------------------------------------------------ the shell

    private String outer(int y) {
        return b.wall(brick, y);
    }

    private void shell() {
        // the wing: floor, the outer walls, the floors between storeys
        for (int x = 0; x <= gx; x++) {
            for (int z = Z0; z <= z1; z++) {
                boolean edge = x == 0 || z == Z0 || z == z1;
                boolean party = x == gx;
                p.set(x, 0, z, edge || party ? outer(0) : "sc_vct");
                for (int y = 1; y <= top; y++) {
                    String s = party ? (y < top ? "sc_cmu" : outer(y)) : edge ? outer(y) : "air";
                    p.set(x, y, z, s);
                }
                for (int f = 1; f < floors; f++) {
                    if (!edge && !party) {
                        p.set(x, f * ST, z, "sc_vct");
                    }
                }
            }
        }
        b.flat(0, Z0, gx, z1, top, brick, 2, "stone_trim");
        // the corridor walls, every storey
        for (int f = 0; f < floors; f++) {
            int y0 = f * ST;
            for (int x = 1; x < gx; x++) {
                for (int y = y0 + 1; y < y0 + ST; y++) {
                    String s = y == y0 + 1 ? dado : "sc_cmu";
                    p.set(x, y, za, s);
                    p.set(x, y, zb, s);
                }
            }
        }
    }

    private void wallX(int x, int za, int zb, int f) {
        int y0 = f * ST;
        for (int z = za; z <= zb; z++) {
            for (int y = y0 + 1; y < y0 + ST; y++) {
                p.set(x, y, z, "sc_cmu");
            }
        }
    }

    /** The rooms of a storey: the office, lobby, nurse and library on the ground floor's front, classrooms, the restrooms by the gym. */
    private void plan(int f) {
        int no = f * 100 + 101;
        List<int[]> front = new ArrayList<>();
        List<String> kinds = new ArrayList<>();
        if (f == 0) {
            front.add(new int[] {1, 10});
            kinds.add("office");
            front.add(new int[] {12, 17});
            kinds.add("lobby");
            front.add(new int[] {19, 22});
            kinds.add("nurse");
            int lib1 = Math.min(gx - 1, 33);
            if (gx - 1 - 34 < 6) {
                lib1 = gx - 1;
            }
            front.add(new int[] {24, lib1});
            kinds.add("library");
            if (lib1 < gx - 1) {
                for (int[] r : split(lib1 + 2, gx - 1, 8)) {
                    front.add(r);
                    kinds.add("class");
                }
            }
        } else {
            for (int[] r : split(1, gx - 1, 8)) {
                front.add(r);
                kinds.add("class");
            }
        }
        for (int i = 0; i < front.size(); i++) {
            int[] r = front.get(i);
            String kind = kinds.get(i);
            String n = kind.equals("class") ? String.valueOf(no++) : kind;
            rooms.add(new Room(r[0], r[1], Z0 + 1, za - 1, true, f, kind, n));
            if (r[1] + 1 < gx) {
                wallX(r[1] + 1, Z0 + 1, za - 1, f);
            }
        }
        // the back: classrooms, then the boys' and the girls' restrooms against the gym
        List<int[]> back = split(1, gx - 9, 8);
        for (int[] r : back) {
            rooms.add(new Room(r[0], r[1], zb + 1, z1 - 1, false, f, "class", String.valueOf(no++)));
            wallX(r[1] + 1, zb + 1, z1 - 1, f);
        }
        rooms.add(new Room(gx - 7, gx - 5, zb + 1, z1 - 1, false, f, "boys", "boys"));
        wallX(gx - 4, zb + 1, z1 - 1, f);
        rooms.add(new Room(gx - 3, gx - 1, zb + 1, z1 - 1, false, f, "girls", "girls"));
    }

    // ------------------------------------------------------------------ rooms

    private void room(Room r) {
        int y = r.y();
        // the door through the corridor wall, the sign beside it on the corridor side
        int dz = r.front() ? za : zb;
        int side = r.front() ? za + 1 : zb - 1;
        String out = r.front() ? "south" : "north";
        if (!r.kind().equals("lobby")) {
            String type = r.kind().equals("class") ? (chance() < 0.6 ? "home_door_oak" : Doors.STEEL)
                    : r.kind().equals("boys") || r.kind().equals("girls") ? "home_door_white" : "home_door_oak";
            int dx = r.kind().equals("boys") || r.kind().equals("girls") ? r.x0() + 1 : r.door();
            Doors.abandoned(p, rng, dx, y + 1, dz, type, r.front() ? "north" : "south", Doors.hinge(rng));
            (r.front() ? frontDoors : backDoors).get(r.f()).add(dx);
            String sign = r.kind().equals("class") ? "sc_room_" + r.no() : "sc_plaque_" + r.kind();
            if (dx + 1 < gx && !r.kind().equals("office")) {
                p.set(dx + 1, y + 2, side, sign + "[facing=" + out + "]");
            }
        }
        // the floor and the drop ceiling
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                String fl = switch (r.kind()) {
                    case "boys", "girls" -> "fit_tile_cream";
                    case "lobby" -> x == r.x0() || x == r.x1() || z == r.z0() ? "sc_vct_" + key : "sc_vct";
                    default -> chance() < 0.3 ? "sc_vct_b" : "sc_vct";
                };
                p.set(x, y, z, fl);
            }
        }
        switch (r.kind()) {
            case "office" -> office(r);
            case "lobby" -> lobby(r);
            case "nurse" -> nurse(r);
            case "library" -> library(r);
            case "boys", "girls" -> restroom(r);
            default -> classroom(r);
        }
        ceiling(r.x0(), r.z0(), r.x1(), r.z1(), y + ST - 1);
    }

    /** The drop ceiling in the top block: lay-in tiles, the troffers in a grid (lit where the power held), tiles fallen through. */
    private void ceiling(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!empty(x, y, z)) {
                    continue;
                }
                boolean light = (x - x0) % 3 == 1 && (z - z0) % 3 == 1;
                if (light) {
                    double r = chance();
                    p.set(x, y, z, (lit ? (r < 0.5 ? "fit_troffer_on" : r < 0.7 ? "fit_troffer_flicker" : "fit_troffer") : "fit_troffer") + "[axis=x]");
                } else if (chance() < 0.05) {
                    p.set(x, y, z, "fit_ceiling_open");
                } else if (chance() < 0.04) {
                    if (empty(x, y - 3, z)) {
                        p.set(x, y - 3, z, "fit_ceiling_fallen[facing=" + DIRS[rng.nextInt(4)] + "]");
                    }
                } else {
                    p.set(x, y, z, "fit_ceiling");
                }
            }
        }
    }

    /**
     * A classroom: the chalkboard on the west wall with the alphabet over it, the teacher's desk
     * by the windows, the chair-desks in rows facing the board, the back wall with the cubbies and
     * the coat hooks (the bookcases in the high school), the reading corner, the clock and the
     * speaker; then how it ended.
     */
    private void classroom(Room r) {
        int y = r.y() + 1, x0 = r.x0(), rx1 = r.x1();
        int zc = (r.z0() + r.z1()) / 2;
        int wz = r.win(), in = r.in(), hall = r.hall();
        String board = pick("lesson", "lesson", "pickup", "shelter");
        tiles("sc_chalk_" + board + "_", 3, x0, y + 1, zc + 1, "east");
        if (r.z1() - r.z0() >= 6) {
            tiles("sc_alpha_", 5, x0, y + 2, zc + 2, "east");
        }
        // the teacher's desk by the windows, the knee to the sitter's right (south as they face east)
        int pz = Math.min(wz, wz + in);
        p.set(x0 + 1, y, pz, "sc_tdesk_ped[facing=east]");
        p.set(x0 + 1, y, pz + 1, "sc_tdesk_knee[facing=east]");
        p.set(x0 + 1, y + 1, chance() < 0.5 ? pz : pz + 1, chance() < 0.7 ? "sc_desk_things[facing=east]" : "paper");
        p.set(x0, y, pz + (chance() < 0.5 ? 0 : 1), chair("east"));
        p.set(x0, y, hall, "cv_flag_us[facing=east]");
        p.set(x0, y + 1, hall, "cv_flag_us_top[facing=east]");
        // how it ended
        double s = chance();
        String end = s < 0.3 ? "tidy" : s < 0.68 ? "fled" : s < 0.88 ? "barricade" : "overrun";
        String desk = "sc_desk_" + (chance() < 0.6 ? "a" : "b");
        for (int z = wz + 2 * in; r.front() ? z <= r.z1() : z >= r.z0(); z += 2 * in) {
            for (int x = x0 + 3; x <= rx1 - 1; x++) {
                if (x == r.door() && z == hall) {
                    continue;
                }
                if (end.equals("tidy") || chance() < 0.55) {
                    p.set(x, y, z, desk + "[facing=west]");
                } else if (chance() < 0.6) {
                    p.set(x, y, z, desk + "_tipped[facing=" + DIRS[rng.nextInt(4)] + "]");
                } else if (chance() < 0.5) {
                    p.set(x, y, z, "sc_pack_" + pick("red", "blue", "green") + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        // the back wall: cubbies and the coats over them, or the high school's bookcases; the board
        List<Integer> back = new ArrayList<>();
        for (int z = wz + 2 * in; r.front() ? z < hall : z > hall; z += in) {
            back.add(z);
        }
        int k = 0;
        for (; k < Math.min(3, back.size()); k++) {
            int z = back.get(k);
            if (high) {
                p.set(rx1, y, z, k == 1 ? "pk_file_cabinet[facing=west]" : "pk_bookshelf_worn[facing=west]");
            } else {
                p.set(rx1, y, z, "sc_cubby[facing=west]");
                p.set(rx1, y + 1, z, (chance() < 0.6 ? "sc_hooks_a" : "sc_hooks_b") + "[facing=west]");
            }
        }
        if (back.size() - k >= 2) {
            int za0 = Math.min(back.get(k), back.get(k + 1));
            tiles("sc_board_" + (high || chance() < 0.4 ? "info" : "art") + "_", 2, rx1, y + 1, za0, "west");
        }
        p.set(rx1, y + 2, zc, "bk_wall_clock[facing=west]");
        if (zc + 2 * in != hall) {
            p.set(rx1, y + 2, zc - 2 * in, "sc_pa[facing=west]");
        }
        // the reading corner by the windows at the back, or the AV cart
        if (!high && chance() < 0.65) {
            int ra = Math.min(wz, wz + in);
            p.set(rx1 - 2, y, ra, "sc_rug_0");
            p.set(rx1 - 1, y, ra, "sc_rug_1");
            p.set(rx1 - 2, y, ra + 1, "sc_rug_2");
            p.set(rx1 - 1, y, ra + 1, "sc_rug_3");
            p.set(rx1, y, wz, "sc_shelf_low[facing=west]");
            p.set(rx1, y, wz + in, chance() < 0.5 ? "sc_tank[facing=west]" : "sc_shelf_low[facing=west]");
        } else {
            p.set(rx1 - 1, y, wz, chance() < 0.5 ? "sc_av_cart[facing=west]" : "sc_projector[facing=west]");
            if (p.get(rx1 - 1, y, wz).startsWith("sc_av")) {
                p.set(rx1 - 1, y + 1, wz, "pk_tv_crt[facing=west]");
            }
            p.set(rx1, y, wz, high ? "pk_bookshelf_worn[facing=west]" : "sc_shelf_low[facing=west]");
        }
        p.set(x0 + 2, y, hall, chance() < 0.5 ? "pk_trash_can[facing=north]" : "air");
        switch (end) {
            case "fled" -> {
                b.mess(x0 + 1, Math.min(r.z0(), r.z1()), rx1 - 1, Math.max(r.z0(), r.z1()), y, 0.06);
                for (int i = 0; i < 3; i++) {
                    p.setIfAir(x0 + 2 + rng.nextInt(Math.max(1, rx1 - x0 - 2)), y, r.z0() + rng.nextInt(r.z1() - r.z0() + 1),
                            chance() < 0.5 ? "sc_lunchbox[facing=" + DIRS[rng.nextInt(4)] + "]" : "sc_pack_" + pick("red", "blue", "green") + "[facing=north]");
                }
            }
            case "barricade" -> {
                // desks dragged against the door and piled; the scratches and the blood outside
                int dx = r.door();
                for (int x = dx - 1; x <= dx + 1; x++) {
                    for (int z = hall; Math.abs(z - hall) <= 1; z -= in) {
                        p.set(x, y, z, desk + "_tipped[facing=" + DIRS[rng.nextInt(4)] + "]");
                        if (z == hall && chance() < 0.7) {
                            p.set(x, y + 1, z, desk + "_tipped[facing=" + DIRS[rng.nextInt(4)] + "]");
                        }
                    }
                }
                p.set(rx1, y, hall, "pk_bookshelf_worn[facing=west]");
                int outZ = r.front() ? za + 1 : zb - 1;
                p.setIfAir(dx, r.y() + 1, outZ, "home_blood_pool");
                p.setIfAir(dx + 1, r.y() + 1, outZ, "blood_splat");
                b.mess(x0 + 1, Math.min(r.z0(), r.z1()), rx1 - 1, Math.max(r.z0(), r.z1()), y, 0.03);
            }
            case "overrun" -> {
                for (int i = 0; i < 6; i++) {
                    p.setIfAir(x0 + 1 + rng.nextInt(rx1 - x0), y, r.z0() + rng.nextInt(r.z1() - r.z0() + 1),
                            chance() < 0.5 ? "home_blood_pool" : "blood_splat");
                }
                b.mess(x0 + 1, Math.min(r.z0(), r.z1()), rx1 - 1, Math.max(r.z0(), r.z1()), y, 0.1);
            }
            default -> b.mess(x0 + 1, Math.min(r.z0(), r.z1()), rx1 - 1, Math.max(r.z0(), r.z1()), y, 0.02);
        }
    }

    /** The front office: the counter onto the lobby, the secretary's desks, the mail slots, the PA, the duplicator; the principal's room behind. */
    private void office(Room r) {
        int y = r.y() + 1;
        // the counter and the glass over it in the wall onto the lobby (x = 11), the door beside
        for (int z = Z0 + 2; z <= Z0 + 4; z++) {
            p.set(11, y, z, "sc_counter[facing=east]");
            p.set(11, y + 1, z, b.glass("z"));
            p.set(11, y + 2, z, b.glass("z"));
        }
        Doors.abandoned(p, rng, 11, y, za - 2, "home_door_oak", "west", Doors.hinge(rng));
        p.set(12, y + 1, za - 1, "sc_plaque_office[facing=east]");
        // the principal's room behind its wall at x = 5
        wallX(5, Z0 + 1, za - 1, 0);
        Doors.abandoned(p, rng, 5, y, za - 3, "home_door_oak", "west", Doors.hinge(rng));
        p.set(6, y + 1, za - 2, "sc_plaque_principal[facing=east]");
        p.set(3, y, Z0 + 3, "desk[facing=east]");
        p.set(3, y, Z0 + 4, "desk[facing=east]");
        p.set(2, y, Z0 + 3, chair("east"));
        p.set(3, y + 1, Z0 + 3, "home_phone[facing=west]");
        p.set(3, y + 1, Z0 + 4, chance() < 0.5 ? "paper" : "cv_stack[axis=z]");
        p.set(4, y, Z0 + 3, "pk_chair_wooden[facing=west]");
        p.set(4, y, Z0 + 4, "pk_chair_wooden[facing=west]");
        p.set(1, y, Z0 + 1, "cv_flag_us[facing=east]");
        p.set(1, y + 1, Z0 + 1, "cv_flag_us_top[facing=east]");
        p.set(1, y, za - 1, "cv_flag_state[facing=east]");
        p.set(1, y + 1, za - 1, "cv_flag_state_top[facing=east]");
        p.set(1, y + 1, Z0 + 4, "cv_portrait[facing=east]");
        p.set(4, y, za - 1, "pk_bookshelf_worn[facing=north]");
        p.set(2, y, za - 1, "pk_file_cabinet[facing=north]");
        // the secretaries, facing the counter
        for (int z = Z0 + 2; z <= Z0 + 3; z++) {
            p.set(9, y, z, "desk[facing=east]");
            p.set(8, y, z, chair("east"));
        }
        p.set(9, y + 1, Z0 + 2, "sh_typewriter[facing=west]");
        p.set(9, y + 1, Z0 + 3, chance() < 0.5 ? "home_phone[facing=west]" : "paper");
        // the PA on its desk under the window, the slots and the duplicator along the back
        p.set(7, y, Z0 + 1, "desk[facing=north]");
        p.set(7, y + 1, Z0 + 1, "sc_pa_console[facing=south]");
        p.set(7, y, Z0 + 2, chair("north"));
        for (int x = 7; x <= 9; x++) {
            p.set(x, y, za - 1, x == 8 ? "filing_cabinet[facing=north]" : "pk_file_cabinet[facing=north]");
            p.set(x, y + 1, za - 1, "sc_mailboxes[facing=north]");
        }
        p.set(8, y + 2, za - 1, "bk_wall_clock[facing=north]");
        p.set(6, y, za - 1, "sc_mimeo[facing=east]");
        p.set(10, y, Z0 + 1, "trade_water_cooler[facing=west]");
        p.set(6, y + 1, Z0 + 3, "home_calendar[facing=east]");
        b.mess(6, Z0 + 1, 10, za - 1, y, 0.06);
        b.mess(1, Z0 + 1, 4, za - 1, y, 0.04);
    }

    /** The lobby: the doors in from the canopy, the trophy case, the visitors' sign, the flag, a bench; open to the corridor. */
    private void lobby(Room r) {
        int y = r.y() + 1;
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int yy = y; yy <= y + 2; yy++) {
                p.set(x, yy, za, "air");
            }
        }
        p.set(r.x1(), y, Z0 + 2, "sc_trophy_lo_0[facing=west]");
        p.set(r.x1(), y, Z0 + 3, "sc_trophy_lo_1[facing=west]");
        p.set(r.x1(), y + 1, Z0 + 2, "sc_trophy_hi_0[facing=west]");
        p.set(r.x1(), y + 1, Z0 + 3, "sc_trophy_hi_1[facing=west]");
        p.set(r.x1(), y, Z0 + 5, "cv_hall_bench[facing=west]");
        p.set(r.x1(), y, Z0 + 6, "cv_hall_bench[facing=west]");
        p.set(r.x1(), y, Z0 + 1, "cv_flag_us[facing=west]");
        p.set(r.x1(), y + 1, Z0 + 1, "cv_flag_us_top[facing=west]");
        p.set(r.x0(), y + 1, Z0 + 1, "sc_sign_visitors[facing=east]");
        p.set(r.x0(), y, Z0 + 1, rng.nextBoolean() ? "home_plant_dead" : "pk_trash_can[facing=east]");
        p.set(r.x0() + 2, y + 2, za - 1, "bk_wall_clock[facing=south]");
        if (overrun) {
            for (int z = Z0 + 1; z < za; z++) {
                p.setIfAir(r.x0() + 2 + rng.nextInt(2), y, z, chance() < 0.6 ? "blood_splat" : "glass_shards");
            }
        }
        b.mess(r.x0(), Z0 + 1, r.x1(), za - 1, y, 0.04);
    }

    /** The nurse: two cots by the window, the eye chart, the first aid box, the sink, her desk. */
    private void nurse(Room r) {
        int y = r.y() + 1, x0 = r.x0(), rx1 = r.x1();
        for (int x : new int[] {x0, rx1}) {
            p.set(x, y, Z0 + 1, "bunk_medbed_head[facing=south]");
            p.set(x, y, Z0 + 2, "bunk_medbed_foot[facing=south]");
        }
        p.set(x0 + 1, y, Z0 + 1, "bunk_iv_stand");
        p.set(x0, y + 1, Z0 + 4, "sc_eye_chart[facing=east]");
        p.set(rx1, y + 1, Z0 + 4, "bunk_first_aid[facing=west]");
        p.set(rx1, y, Z0 + 4, "home_sink_white[facing=west]");
        p.set(x0, y, za - 2, "desk[facing=east]");
        p.set(x0 + 1, y, za - 2, chair("west"));
        p.set(x0, y, za - 1, "pk_file_cabinet[facing=east]");
        p.set(x0, y + 1, za - 2, chance() < 0.5 ? "paper" : "home_phone[facing=east]");
        if (chance() < 0.5) {
            p.set(x0, y, Z0 + 3, "home_blood_pool");
        }
        b.mess(x0, Z0 + 1, rx1, za - 1, y, 0.06);
    }

    /** The library: stacks in rows, the circulation desk by the door, the catalogue, tables, the story corner with the rug and the globe. */
    private void library(Room r) {
        int y = r.y() + 1, x0 = r.x0(), rx1 = r.x1();
        for (int z : new int[] {Z0 + 2, Z0 + 4}) {
            for (int x = x0 + 1; x <= Math.min(x0 + 5, rx1 - 4); x++) {
                p.set(x, y, z, chance() < 0.08 ? "home_books_stack" : "cv_stack[axis=x]");
            }
        }
        p.set(rx1 - 3, y, za - 2, "cv_circ_desk[facing=south]");
        p.set(rx1 - 2, y, za - 2, "cv_circ_desk[facing=south]");
        p.set(rx1 - 2, y, za - 3, chair("south"));
        p.set(x0, y, za - 1, "cv_catalog[facing=east]");
        p.set(x0, y, za - 2, "cv_catalog[facing=east]");
        p.set(x0 + 2, y, za - 1, "pk_table_wooden");
        p.set(x0 + 3, y, za - 1, "pk_chair_wooden[facing=west]");
        p.set(x0 + 1, y, za - 1, "pk_chair_wooden[facing=east]");
        // the story corner by the windows at the far end
        p.set(rx1 - 2, y, Z0 + 1, "sc_rug_0");
        p.set(rx1 - 1, y, Z0 + 1, "sc_rug_1");
        p.set(rx1 - 2, y, Z0 + 2, "sc_rug_2");
        p.set(rx1 - 1, y, Z0 + 2, "sc_rug_3");
        for (int z = Z0 + 1; z <= Z0 + 4; z++) {
            p.set(rx1, y, z, "sc_shelf_low[facing=west]");
        }
        p.set(rx1 - 3, y, Z0 + 1, "cv_globe_stand");
        p.set(x0, y, Z0 + 1, "pk_bookshelf_worn[facing=east]");
        for (int i = 0; i < 6; i++) {
            p.setIfAir(x0 + rng.nextInt(rx1 - x0 + 1), y, Z0 + 1 + rng.nextInt(za - Z0 - 1), chance() < 0.6 ? "home_books_stack" : "paper");
        }
        b.mess(x0, Z0 + 1, rx1, za - 1, y, 0.03);
    }

    /** A restroom: the basins and the long mirror down one side, the stalls along the back. */
    private void restroom(Room r) {
        int y = r.y() + 1, x0 = r.x0();
        for (int z = r.z0() + 1; z <= r.z0() + 3; z++) {
            p.set(x0, y, z, "fit_vanity_black[facing=east]");
            p.set(x0, y + 1, z, "fit_mirror_long[facing=east]");
        }
        for (int x = x0; x <= r.x1(); x++) {
            p.set(x, y, r.z1(), "fit_toilet_stall[facing=north]");
            p.set(x, y + 1, r.z1(), "fit_stall_top[facing=north]");
        }
        p.set(r.x1(), y, r.z0() + 2, "pk_trash_can[facing=west]");
        if (chance() < 0.4) {
            p.set(x0 + 1, y, r.z1() - 2, chance() < 0.5 ? "home_blood_pool" : "paper");
        }
        if (chance() < 0.5) {
            p.set(r.x1(), y + 1, r.z1() - 2, "graffiti_" + rng.nextInt(8) + "[facing=west]");
        }
    }

    // ------------------------------------------------------------------ the corridor

    /**
     * The corridor of a storey: the colour band across the floor every few tiles, lockers down the
     * walls (both sides in the high school), the drinking fountain and the alarm, pennants and the
     * banner over the lockers, the mural on the ground floor, the notes left on the walls, the
     * ceiling with its lights; the exit at the west end.
     */
    private void corridor(int f) {
        int y0 = f * ST, y = y0 + 1, mid = za + 2;
        for (int x = 1; x < gx; x++) {
            for (int z = za + 1; z < zb; z++) {
                p.set(x, y0, z, x % 6 == 0 ? "sc_vct_" + key : chance() < 0.2 ? "sc_vct_b" : "sc_vct");
            }
        }
        int muralX = f == 0 && !high ? 25 : -100;
        // clear of the stairs: up from the ground floor at the west end, on up to the roof from the top floor
        int stairEnd = high ? (f == floors - 1 ? ROOF_STAIR + ST + 3 : 7) : 0;
        String banner = "sc_banner_" + key + "_";
        // lockers on the back wall; on the front wall too in the high school
        for (int pass = 0; pass < 2; pass++) {
            boolean frontSide = pass == 0;
            if (frontSide && !high && f == 0) {
                // the elementary's front wall: hooks and boards instead, the mural past the library door
                for (int x = 19; x < gx - 1; x += 7) {
                    if (clearOf(frontDoors.get(f), x, 2) && x + 1 < gx && (x < muralX - 1 || x > muralX + 6)) {
                        tiles("sc_board_art_", 2, x, y + 1, za + 1, "south");
                    }
                }
                continue;
            }
            int z = frontSide ? za + 1 : zb - 1;
            String facing = frontSide ? "south" : "north";
            List<Integer> doors = (frontSide ? frontDoors : backDoors).get(f);
            for (int x = Math.max(1, stairEnd); x < gx; x++) {
                if (!clearOf(doors, x, 1) || frontSide && f == 0 && x >= 11 && x <= 18 || x >= muralX && x <= muralX + 5 && frontSide) {
                    continue;
                }
                boolean open = chance() < 0.12;
                if (overrun && chance() < 0.05) {
                    continue;
                }
                p.set(x, y, z, "sc_locker_" + (open ? "open_" : "") + key + "[facing=" + facing + "]");
                p.set(x, y + 1, z, "sc_locker_top_" + (open ? "open_" : "") + key + "[facing=" + facing + "]");
            }
        }
        // the mural on the ground floor of the elementary, two rows of six over a bench
        if (muralX > 0) {
            for (int i = 0; i < 12; i++) {
                p.set(muralX + i % 6, y + 2 - i / 6, za, "sc_mural_" + i + "[facing=south]");
            }
            p.set(muralX + 2, y, za + 1, "cv_hall_bench[facing=south]");
            p.set(muralX + 3, y, za + 1, "cv_hall_bench[facing=south]");
        }
        // pennants over the lockers, the banner on the back wall, the bells, the alarms, the fountain
        for (int x = Math.max(3, stairEnd); x < gx - 1; x += 5) {
            if (empty(x, y + 2, zb - 1)) {
                p.set(x, y + 2, zb - 1, "sc_pennant_" + key + "_" + rng.nextInt(3) + "[facing=north]");
            }
        }
        int bx = Math.max(stairEnd, 1) + 8;
        if (bx + 3 < gx) {
            int[] at = leftOf(bx + 1, zb - 1, "north", 1);
            tiles(banner, 3, at[0], y + 2, zb - 1, "north");
        }
        p.set(Math.max(2, stairEnd), y + 2, za + 1, "sc_bell[facing=south]");
        p.set(gx - 2, y + 2, za + 1, "sc_bell[facing=south]");
        p.set(1, y + 1, high ? zb - 1 : za + 1, "sc_pull[facing=east]");
        p.set(gx - 8, y, zb - 1, "sc_fountain[facing=north]");
        p.set(gx - 8, y + 1, zb - 1, "air");
        p.set(gx - 9, y + 1, zb - 1, "sc_pull[facing=north]");
        p.set(gx - 9, y, zb - 1, "air");
        // the notes: the way to the shelter, the warnings, the hands
        if (shelter) {
            for (int x = 6 + rng.nextInt(4); x < gx - 3; x += 9 + rng.nextInt(5)) {
                boolean frontSide = chance() < 0.5;
                int z = frontSide ? za : zb;
                if (clearOf((frontSide ? frontDoors : backDoors).get(f), x, 1) && "sc_cmu".equals(p.get(x, y + 2, z))) {
                    // the arrow points to the gym (east): the front wall faces south, so east is the viewer's right
                    p.set(x, y + 2, z, (frontSide ? "sc_note_gym_r" : "sc_note_gym_l") + "[facing=" + (frontSide ? "south" : "north") + "]");
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            int x = 3 + rng.nextInt(gx - 6);
            boolean frontSide = chance() < 0.5;
            int z = frontSide ? za : zb;
            if (clearOf((frontSide ? frontDoors : backDoors).get(f), x, 1) && (x < muralX - 1 || x > muralX + 6) && (x < 11 || x > 18 || !frontSide)) {
                if ("sc_cmu".equals(p.get(x, y + 2, z))) {
                    p.set(x, y + 2, z, "sc_note_" + pick("x", "bus", "kids", "hands") + "[facing=" + (frontSide ? "south" : "north") + "]");
                }
            }
        }
        // the exit at the west end on the ground floor, the sign over it; the ceiling
        if (f == 0) {
            Doors.abandoned(p, rng, 0, y, mid, Doors.STEEL, "west", Doors.hinge(rng));
            p.set(1, y + 2, mid, "cv_exit_red[facing=east]");
        } else {
            p.set(0, y + 1, mid, b.glass("z"));
            p.set(0, y + 2, mid, b.glass("z"));
        }
        if (f == 0) {
            p.set(gx - 1, y + 2, mid, "cv_exit_red[facing=west]");
        }
        b.mess(1, za + 1, gx - 1, zb - 1, y, overrun ? 0.08 : 0.035);
        ceiling(1, za + 1, gx - 1, zb - 1, y0 + ST - 1);
    }

    private static boolean clearOf(List<Integer> doors, int x, int gap) {
        for (int d : doors) {
            if (Math.abs(d - x) <= gap) {
                return false;
            }
        }
        return true;
    }

    /**
     * The high school's stair: up the front side of the corridor from the west end, the block
     * under each step, three clear over it through the ceiling and the floor above (the well), the
     * rail round the well upstairs.
     */
    private void stairs() {
        int z = za + 1;
        for (int i = 0; i < ST; i++) {
            int x = 1 + i;
            for (int y = 1; y < 1 + i; y++) {
                p.set(x, y, z, "sc_cmu");
            }
            p.set(x, 1 + i, z, "stairs_concrete[facing=east]");
            for (int y = 2 + i; y <= Math.min(Math.max(ST + 1, 4 + i), 2 * ST - 2); y++) {
                p.set(x, y, z, "air");
            }
        }
        for (int x = 1; x < ST; x++) {
            p.set(x, ST + 1, z + 1, "railing[facing=north]");
        }
    }

    // ------------------------------------------------------------------ the gym

    /**
     * The gym, standing taller at the east end: the maple court with its lines, keys and the
     * centre circle, the hoops and the pads, the scoreboard, the ropes; the serving line through the
     * kitchen wall and the kitchen behind; the shelter on the court or the cafeteria tables.
     */
    private void gym() {
        int kz = z1 - 5;  // the kitchen wall
        int cx0 = gx + 1, cx1 = x1 - 1;
        for (int x = gx; x <= x1; x++) {
            for (int z = Z0; z <= z1; z++) {
                boolean edge = x == gx || x == x1 || z == Z0 || z == z1;
                p.set(x, 0, z, edge ? outer(0) : z > kz ? "floor_concrete" : "sc_court");
                for (int y = 1; y <= gh; y++) {
                    if (x == gx && y < top) {
                        p.set(x, y, z, "sc_cmu");
                    } else {
                        p.set(x, y, z, edge ? outer(y) : "air");
                    }
                }
            }
        }
        b.flat(gx, Z0, x1, z1, gh, brick, 2, "stone_trim");
        // the court: sidelines, baselines, the centre line, the keys and the circle
        int sl = cx0 + 1, sr = high ? cx1 - 2 : cx1 - 1, bt = Z0 + 2, bb = kz - 2;
        int mx = (cx0 + cx1) / 2 - 1;  // the circle's first column (four wide)
        int mz = (bt + bb + 1) / 2 - 1;  // the circle's second row (four deep, centred on the court)
        int kd = Math.max(2, Math.min(5, mz - 3 - bt));  // the keys as deep as the court allows
        for (int z = bt; z <= bb; z++) {
            p.set(sl, 0, z, "sc_court_line[axis=z]");
            p.set(sr, 0, z, "sc_court_line[axis=z]");
        }
        for (int x = sl; x <= sr; x++) {
            p.set(x, 0, bt, "sc_court_line[axis=x]");
            p.set(x, 0, bb, "sc_court_line[axis=x]");
            p.set(x, 0, mz + 1, "sc_court_line[axis=x]");
        }
        for (int x = mx; x < mx + 4; x++) {
            for (int z = bt + 1; z <= bt + kd; z++) {
                p.set(x, 0, z, "sc_court_key_" + key);
            }
            for (int z = bb - kd; z < bb; z++) {
                p.set(x, 0, z, "sc_court_key_" + key);
            }
        }
        for (int i = 0; i < 16; i++) {
            p.set(mx + i % 4, 0, mz + i / 4 - 1, "sc_court_c_" + key + "_" + i);
        }
        // the hoops over each baseline, the pads under the south one, the scoreboard
        p.set(mx + 1, 4, Z0 + 1, "sc_hoop_l[facing=south]");
        p.set(mx + 2, 4, Z0 + 1, "sc_hoop_r[facing=south]");
        p.set(mx + 2, 4, kz - 1, "sc_hoop_l[facing=north]");
        p.set(mx + 1, 4, kz - 1, "sc_hoop_r[facing=north]");
        String on = lit ? "_lit" : "";
        for (int i = 0; i < 3; i++) {
            p.set(mx + i, gh - 3, Z0 + 1, "sc_score_" + i + on + "[facing=south]");
        }
        // the kitchen wall with the serving line through it; the kitchen
        for (int x = cx0; x <= cx1; x++) {
            for (int y = 1; y <= 5; y++) {
                p.set(x, y, kz, y <= 2 ? "sc_pad_" + key + "[facing=north]" : "sc_cmu");
            }
            for (int z = kz + 1; z < z1; z++) {
                p.set(x, 5, z, "concrete_dark");
            }
        }
        for (int x = cx0 + 2; x <= cx0 + 5; x++) {
            p.set(x, 1, kz, "fit_serving[facing=north]");
            p.set(x, 2, kz, "air");
            p.set(x, 3, kz, chance() < 0.4 ? "garage_door[facing=north]" : "air");
        }
        p.set(cx0 + 1, 1, kz - 1, "fit_counter_steel[facing=north]");
        p.set(cx0 + 1, 2, kz - 1, "sc_trays");
        Doors.abandoned(p, rng, cx1 - 1, 1, kz, Doors.STEEL, "north", Doors.hinge(rng));
        p.set(cx1, 3, kz - 1, "sc_plaque_kitchen[facing=north]");
        kitchen(cx0, cx1, kz);
        // the walls: the corridor's doors in, the doors out the front and the side, the high windows
        int dz = za + 1;
        Doors.abandoned(p, rng, gx, 1, dz, Doors.STEEL, "east", "left");
        Doors.abandoned(p, rng, gx, 1, dz + 1, Doors.STEEL, "east", "right");
        p.set(gx - 1, 3, dz, "sc_plaque_gym[facing=west]");
        Doors.pair(p, rng, cx0 + 1, 1, Z0, Doors.STEEL, "south");
        p.set(cx0 + 1, 3, Z0 + 1, "cv_exit_red[facing=south]");
        Doors.abandoned(p, rng, x1, 1, dz, Doors.STEEL, "east", Doors.hinge(rng));
        p.set(cx1, 3, dz, "cv_exit_red[facing=west]");
        for (int x = cx0 + 1; x < cx1; x++) {
            if (x < mx - 1 || x > mx + 4) {
                p.set(x, gh - 2, Z0, b.glass("x"));
                p.set(x, gh - 1, Z0, b.glass("x"));
            }
        }
        for (int z = Z0 + 2; z < kz - 1; z += 2) {
            p.set(x1, gh - 2, z, b.glass("z"));
            p.set(x1, gh - 1, z, b.glass("z"));
        }
        // the lights hung from the roof, the ropes
        for (int x = cx0 + 2; x < cx1; x += 4) {
            for (int z = Z0 + 3; z < kz; z += 4) {
                double r = chance();
                p.set(x, gh - 1, z, lit ? (r < 0.45 ? "fit_highbay_on" : r < 0.7 ? "fit_highbay_flicker" : "fit_highbay") : "fit_highbay");
            }
        }
        for (int z : new int[] {mz - 3, mz + 6}) {
            int rx = high ? cx1 - 3 : cx1;
            for (int y = 3; y < gh - 1; y++) {
                p.set(rx, y, z, "sc_rope");
            }
            p.set(rx, 2, z, "sc_rope_end");
        }
        if (high) {
            for (int z = bt + 1; z < bb; z++) {
                if (Math.abs(z - dz) <= 1) {
                    continue;  // the aisle to the side door
                }
                p.set(cx1 - 1, 1, z, "sc_bleacher[facing=west]");
                p.set(cx1, 1, z, "sc_bleacher_frame[facing=west]");
                p.set(cx1, 2, z, "sc_bleacher[facing=west]");
            }
        }
        if (shelter) {
            shelter(cx0, cx1, kz, sl, sr, bt, bb, dz);
        } else {
            cafeteria(cx0, cx1, kz, sl, sr, bt, bb);
        }
    }

    private void kitchen(int cx0, int cx1, int kz) {
        int back = z1 - 1;
        String[] line = {"fit_oven", "fit_oven", "fit_counter_steel", "bunk_sink", "fit_counter_steel", "pk_fridge", "pk_fridge"};
        for (int i = 0; i < line.length && cx0 + i <= cx1; i++) {
            p.set(cx0 + i, 1, back, line[i] + "[facing=north]");
        }
        p.set(cx0 + 2, 2, back, "fit_urn[facing=north]");
        p.set(cx0 + 4, 2, back, chance() < 0.5 ? "sc_trays" : "paper");
        for (int z = kz + 1; z < back; z++) {
            p.set(cx1, 1, z, "pk_metal_rack[facing=west]");
        }
        p.set(cx0 + 3, 1, kz + 1, "sc_milk[facing=north]");
        p.set(cx0 + 4, 1, kz + 1, "sc_milk[facing=north]");
        Doors.abandoned(p, rng, cx1 - 2, 1, z1, Doors.STEEL, "north", Doors.hinge(rng));
        for (int x = cx0; x <= cx1; x++) {
            for (int z = kz + 1; z < z1; z++) {
                if ((x - cx0) % 3 == 1 && (z - kz) % 2 == 0 && empty(x, 4, z)) {
                    p.set(x, 4, z, (lit && chance() < 0.4 ? "fit_troffer_on" : "fit_troffer") + "[axis=x]");
                }
            }
        }
        b.mess(cx0, kz + 1, cx1, z1 - 1, 1, 0.08);
    }

    /**
     * The shelter: cots in rows across the court, the supplies stacked along the west wall, the
     * registration table by the corridor doors with the notes on the wall behind it, the banner;
     * where it was overrun, the cots thrown about, the body bags, the casings.
     */
    private void shelter(int cx0, int cx1, int kz, int sl, int sr, int bt, int bb, int dz) {
        for (int x = cx0 + 2; x <= cx1 - (high ? 3 : 1); x += 2) {
            for (int z = bt + 1; z + 1 < bb; z += 3) {
                if (chance() < 0.12) {
                    continue;
                }
                if (overrun && chance() < 0.3) {
                    p.set(x, 1, z, chance() < 0.5 ? "bunk_bodybag[facing=" + DIRS[rng.nextInt(4)] + "]" : "home_blood_pool");
                    continue;
                }
                boolean messy = chance() < 0.4;
                p.set(x, 1, z, (messy ? "sc_cot_head_b" : "sc_cot_head") + "[facing=north]");
                p.set(x, 1, z + 1, (messy && chance() < 0.7 ? "sc_cot_foot_b" : "sc_cot_foot") + "[facing=north]");
                if (chance() < 0.2) {
                    p.set(x + 1, 1, z, pick("sc_pack_red", "sc_pack_blue", "sc_pack_green", "sc_lunchbox") + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        // supplies down the west wall, the first aid and the water at the end
        for (int z = Z0 + 1; z < dz - 1; z++) {
            String s = pick("sc_cases_water", "sc_cases_blankets", "bunk_crate_ration", "sc_cases_water");
            p.set(cx0, 1, z, s + "[facing=east]");
            if (chance() < 0.6) {
                p.set(cx0, 2, z, pick("sc_cases_water", "sc_cases_blankets", "bunk_crate_ration") + "[facing=east]");
            }
        }
        p.set(cx0, 1, dz + 2, "bunk_drum_water");
        p.set(cx0, 1, dz + 3, "bunk_drum_water");
        p.set(cx0, 2, dz + 3, "bunk_first_aid[facing=east]");
        // the registration table by the doors from the corridor, the message wall behind it
        p.set(cx0 + 1, 1, dz + 4, "trade_banquet_table[facing=east]");
        p.set(cx0 + 1, 1, dz + 5, "trade_banquet_table[facing=east]");
        p.set(cx0 + 1, 2, dz + 4, "sc_register[facing=east]");
        p.set(cx0, 1, dz + 4, "trade_folding_chair[facing=east]");
        p.set(cx0, 1, dz + 5, chance() < 0.7 ? "trade_folding_chair[facing=east]" : "trade_folding_chair_tipped[facing=north]");
        tiles("sc_messages_", 2, cx0, 2, dz + 5, "east");
        tiles("sc_shelter_", 2, cx0, gh - 4, dz + 1, "east");
        p.set(cx0, 1, Z0 + 1, "cv_flag_us[facing=east]");
        p.set(cx0, 2, Z0 + 1, "cv_flag_us_top[facing=east]");
        // the folded tables along the east wall, two pulled across the front doors
        for (int z = Z0 + 2; z < kz - 1; z += 2) {
            if (!high && empty(cx1, 1, z)) {
                p.set(cx1, 1, z, "sc_cafe_folded[facing=west]");
                p.set(cx1, 2, z, "sc_cafe_folded_top[facing=west]");
            }
        }
        if (overrun || chance() < 0.4) {
            p.set(cx0 + 1, 1, Z0 + 1, "sc_cafe_table[axis=x]");
            p.set(cx0 + 2, 1, Z0 + 1, "sc_cafe_table[axis=x]");
            p.set(cx0 + 1, 2, Z0 + 1, "sc_cafe_folded[facing=north]");
            for (int x = cx0 + 1; x <= cx0 + 3; x++) {
                p.setIfAir(x, 1, Z0 + 2, chance() < 0.5 ? "bunk_casings" : "blood_splat");
            }
        }
        b.mess(cx0 + 1, Z0 + 1, cx1 - 1, kz - 1, 1, overrun ? 0.08 : 0.03);
    }

    /** Lunch as it was left: the tables out in rows with their stools, trays on them, the ball cart. */
    private void cafeteria(int cx0, int cx1, int kz, int sl, int sr, int bt, int bb) {
        for (int x = cx0 + 2; x <= cx1 - (high ? 4 : 2); x += 3) {
            for (int z = bt + 1; z < bb - 1; z++) {
                p.set(x, 1, z, chance() < 0.08 ? "air" : "sc_cafe_table[axis=z]");
                if (chance() < 0.12) {
                    p.set(x, 2, z, chance() < 0.5 ? "sc_trays" : "paper");
                }
            }
        }
        p.set(cx0, 1, Z0 + 2, "sc_ball_cage");
        for (int i = 0; i < 4; i++) {
            p.setIfAir(sl + rng.nextInt(Math.max(1, sr - sl)), 1, bt + rng.nextInt(Math.max(1, bb - bt)), chance() < 0.5 ? "sc_ball_orange" : "sc_ball_red");
        }
        b.mess(cx0 + 1, Z0 + 1, cx1 - 1, kz - 1, 1, 0.04);
    }

    // ------------------------------------------------------------------ outside

    /** The bus loop along the front, the lawn before the gym with the sign and the flag, the walks. */
    private void grounds() {
        for (int x = 0; x <= x1; x++) {
            for (int z = 0; z < Z0; z++) {
                boolean loop = x < gx && z >= 2 && z <= 8 || (x <= 2 || x >= gx - 3 && x < gx) && z < 2;
                String g;
                if (loop) {
                    double r = chance();
                    g = r < 0.2 ? "asphalt_crack" : r < 0.27 ? "asphalt_patch" : "asphalt";
                } else if (z >= 9 && x < gx || z >= 10) {
                    g = chance() < 0.1 ? "sidewalk_cracked" : "sidewalk";
                } else {
                    g = chance() < 0.1 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
                }
                p.set(x, 0, z, g);
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, "air");
                }
            }
        }
        for (int x = 3; x < gx - 3; x++) {
            p.set(x, 0, 9, "curb");
        }
        // the stencil across the loop at the door, read from the street; the buses that never left
        int sx = 13;
        for (int i = 0; i < 3; i++) {
            p.set(sx + i, 0, 4, "sc_stencil_" + i);
        }
        int buses = 1 + rng.nextInt(3);
        for (int i = 0; i < buses; i++) {
            double bx = 6 + i * 13 + chance() * 2;
            if (bx < gx - 6) {
                // the same dice either way, so an evacuated school is otherwise the same school
                float yaw = 270 + (float) (chance() * 8 - 4);
                long seed = rng.nextLong();
                if (!evacuated) {
                    p.vehicle(bx, 1, 6.5, yaw, "school_bus", seed);
                }
            }
        }
        if (chance() < 0.6) {
            double cx = gx - 5 + chance();
            float yaw = 90 + (float) (chance() * 20 - 10);
            long seed = rng.nextLong();
            if (!evacuated) {
                p.vehicle(cx, 1, 3.5, yaw, "sedan,hatchback,minivan,wagon,suv", seed);
            }
        }
        p.set(gx - 2, 1, 9, "sc_sign_bus[facing=north]");
        p.set(1, 1, 0, "sc_sign_school[facing=north]");
        // the sign on its brick base on the lawn, the flag, the bike rack by the gym doors
        int mx = gx + 6;
        String board = pick("closed", "shelter", "shelter", "picture");
        for (int i = 0; i < 3; i++) {
            p.set(mx + 2 - i, 1, 2, outer(1));
            p.set(mx + 2 - i, 2, 2, "sc_lboard_" + board + "_" + i + "[facing=north]");
            p.set(mx + 2 - i, 3, 2, "sc_monument_" + (high ? "h" : "e") + "_" + i + "[facing=north]");
        }
        new Civic(p, rng).flagpole(gx + 3, 5, 10);
        for (int x = gx + 6; x <= gx + 8; x++) {
            p.set(x, 1, 10, "sc_bike_rack[axis=x]");
        }
        for (int i = 0; i < 5; i++) {
            p.setIfAir(rng.nextInt(x1 + 1), 1, 1 + rng.nextInt(Z0 - 2), pick("paper", "paper", "glass_shards", "sc_pack_red[facing=north]", "trash_bag[facing=east]"));
        }
        if (overrun || chance() < 0.3) {
            for (int x = gx + 1; x <= gx + 5; x++) {
                if (chance() < 0.8) {
                    p.set(x, 1, Z0 - 3, "sandbags[facing=north]");
                }
            }
            for (int i = 0; i < 5; i++) {
                p.setIfAir(gx + 1 + rng.nextInt(5), 1, Z0 - 1 - rng.nextInt(2), chance() < 0.5 ? "bunk_casings" : "blood_splat");
            }
        }
    }

    /**
     * The front: ribbon windows over the enamel panels in each room, the brick piers between; the
     * lobby's glass front and doors, the entry raised over it with the name in letters, the canopy
     * out to the loop, the lamps; the gym's doors.
     */
    private void front() {
        for (int f = 0; f < floors; f++) {
            int y0 = f * ST;
            for (int x = 1; x < gx; x++) {
                // a pier where a partition meets the front wall
                boolean pier = "sc_cmu".equals(p.get(x, y0 + 2, Z0 + 1));
                if (pier || f == 0 && x >= 11 && x <= 18) {
                    continue;
                }
                p.set(x, y0 + 2, Z0, b.glass("x"));
                p.set(x, y0 + 3, Z0, b.glass("x"));
                p.set(x, y0 + 4, Z0, "sc_spandrel_" + key);
            }
        }
        // the lobby: the glass front, the doors, the transom
        for (int x = 12; x <= 17; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, Z0, b.glass("x"));
            }
            p.set(x, 4, Z0, "sc_spandrel_" + key);
        }
        Doors.pair(p, rng, 14, 1, Z0, Doors.SHOP, "south");
        // the entry raised over the lobby, the name across it (tile 0 at the viewer's left, the higher x)
        int ex0 = 10, ex1 = 19, ty = top;
        for (int x = ex0; x <= ex1; x++) {
            for (int y = ty + 1; y <= ty + 3; y++) {
                p.set(x, y, Z0, outer(y));
            }
            p.set(x, ty + 4, Z0, "stone_trim");
        }
        for (int z = Z0 + 1; z <= Z0 + 2; z++) {
            for (int y = ty + 1; y <= ty + 3; y++) {
                p.set(ex0, y, z, outer(y));
                p.set(ex1, y, z, outer(y));
            }
            p.set(ex0, ty + 4, z, "stone_trim");
            p.set(ex1, ty + 4, z, "stone_trim");
        }
        int fallen = chance() < 0.4 ? rng.nextInt(10) : -1;
        for (int i = 0; i < 10; i++) {
            if (i != fallen) {
                p.set(ex1 - i, ty + 2, Z0 - 1, "sc_letters_" + (high ? "h" : "e") + "_" + i + "[facing=north]");
            }
        }
        // the canopy out over the walk to the loop, on its posts
        for (int x = 13; x <= 16; x++) {
            for (int z = 9; z < Z0; z++) {
                p.set(x, 3, z, "front_canopy_metal[facing=north]");
            }
        }
        for (int x : new int[] {13, 16}) {
            p.set(x, 1, 9, "ext_post_base");
            p.set(x, 2, 9, "ext_post");
        }
        for (int x : new int[] {11, 18, gx + 1, gx + 4}) {
            p.set(x, 3, Z0 - 1, (lit && chance() < 0.6 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        }
        // tags on the gym's blank wall, ivy up the corners
        if (chance() < 0.7) {
            p.set(gx + 6 + rng.nextInt(4), 2, Z0 - 1, "graffiti_" + rng.nextInt(8) + "[facing=north]");
        }
        for (int x : new int[] {0, x1}) {
            if (chance() < 0.5) {
                for (int y = 1; y <= 1 + rng.nextInt(5); y++) {
                    p.setIfAir(x, y, Z0 - 1, "vines[facing=south]");
                }
            }
        }
    }

    /** The back and the ends: the back rooms' windows (high and small in the restrooms), the end rooms' side windows, the dumpster by the kitchen door. */
    private void backAndSides() {
        for (int f = 0; f < floors; f++) {
            int y0 = f * ST;
            for (int x = 1; x < gx; x++) {
                String in = p.get(x, y0 + 2, z1 - 1);
                if ("sc_cmu".equals(in)) {
                    continue;
                }
                boolean wet = x >= gx - 7;
                if (!wet) {
                    p.set(x, y0 + 2, z1, b.glass("x"));
                }
                p.set(x, y0 + 3, z1, b.glass("x"));
                p.set(x, y0 + 4, z1, wet ? outer(y0 + 4) : "sc_spandrel_" + key);
            }
            for (int z : new int[] {Z0 + 2, Z0 + 3, Z0 + 4, zb + 3, zb + 4, zb + 5, zb + 6}) {
                p.set(0, y0 + 2, z, b.glass("z"));
                p.set(0, y0 + 3, z, b.glass("z"));
            }
        }
        p.set(x1 - 5, 1, z1 + 1, "dumpster[facing=north]");
        p.set(x1 - 4, 1, z1 + 1, "dumpster[facing=north]");
        p.set(x1 - 2, 3, z1 + 1, "ext_sconce[facing=south]");
    }

    /** The flat roofs: the units, the flues, the drains; the ball that went up there and never came down. */
    private void roof() {
        int y = top + 1;
        for (int x = 4; x < gx - 2; x += 9) {
            p.set(x, y, Z0 + 4 + rng.nextInt(3), "hvac");
        }
        p.set(gx - 4, y, z1 - 3, "ext_flue");
        p.set(gx - 4, y + 1, z1 - 3, "ext_flue_cap");
        p.setIfAir(3 + rng.nextInt(gx - 6), y, Z0 + 2 + rng.nextInt(z1 - Z0 - 4), chance() < 0.5 ? "sc_ball_red" : "sc_ball_orange");
        p.set(x1 - 4, gh + 1, z1 - 4, "hvac");
        p.set(x1 - 3, gh + 1, z1 - 2, "ext_flue");
        p.set(x1 - 3, gh + 2, z1 - 2, "ext_flue_cap");
    }
}
