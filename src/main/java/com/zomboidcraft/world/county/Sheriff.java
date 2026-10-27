package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The county sheriff's office. There are no photos of one in the library yet, so this follows what
 * the small-county offices of the seventies and eighties are known for: one low storey of
 * split-face block or brick under a flat roof, the entry raised a little higher with the county's
 * name in metal letters on it and the star over the door, a flat canopy on two posts; the cruisers
 * parked nose in, the reserved signs, the after-hours phone; the sally port's roller door at the
 * side; the radio tower on the roof and razor wire along the back over the cells.
 *
 * Inside, front to back: the lobby (benches, the wanted board, the flags) behind its
 * bullet-resistant window with the deal tray, the buzzer door; dispatch (the console and its
 * radios, the county map); the sally port where the cars came in. Then the sheriff's office, the
 * squad room (desks back to back with their typewriters, the shotgun rack, the lockers, the coffee)
 * and booking (the counter, the height chart and the camera, the ink plate, the cuff bench). At the
 * back, off a corridor: three cells behind bars (steel bunks, steel toilets, glass-block lights,
 * tally marks scratched in the paint), the armory and evidence store, the interview room behind its
 * one-way glass.
 *
 * Drawn in its lot's frame, the parking lot along z = 0..Z0-1, the front wall at Z0 facing north.
 * The floor at y = 0 (level with the lot), the roof at TOP. Rooms: the front band to the wall at
 * ZA = Z0+7, the middle band to ZB = Z0+14, the cell block behind; across, the lobby x 1..6 (window
 * wall at 7), dispatch and the squad room 8..12 (wall at 13), the sally port and booking 14..18.
 */
final class Sheriff {
    static final int Z0 = 8, TOP = 6, EY = 9;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final String outside, inside, letters;
    private final boolean lit, overrun;

    Sheriff(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.25 + rng.nextDouble() * 0.3;
        b.boarded = rng.nextDouble() < 0.4 ? 0.5 : 0.15;
        double r = rng.nextDouble();
        this.outside = r < 0.5 ? "sh_cmu_tan" : r < 0.8 ? "brick_buff" : "brick_red";
        this.inside = rng.nextDouble() < 0.6 ? "sh_cmu" : "sh_cmu_grey";
        this.letters = rng.nextDouble() < 0.6 ? "a" : "b";
        this.lit = rng.nextDouble() < 0.4;
        this.overrun = rng.nextDouble() < 0.35;
    }

    /** An office w wide (at least 20) and d deep (at least 30, the lot in the first Z0 rows). */
    void build(int w, int d) {
        int x1 = w - 1, z0 = Z0, z1 = d - 1;
        int za = z0 + 7, zb = z0 + 14;
        lot(x1, z0);
        shell(x1, z0, z1, za, zb);
        front(x1, z0);
        sides(x1, z0, z1);
        b.flat(0, z0, x1, z1, TOP, outside, 2, "stone_trim");
        entry(z0);
        lobby(z0, za);
        dispatch(z0, za);
        sallyPort(x1, z0, za);
        office(za, zb);
        squad(za, zb);
        booking(x1, za, zb);
        cells(z1, zb);
        armory(z1, zb);
        interview(z1, zb);
        roof(x1, z0, z1);
        b.lights(0, z0, x1, z1, TOP - 1, lit ? 0.15 : 0.03);
    }

    // ------------------------------------------------------------------ the shell

    private String wall(int y) {
        if (outside.startsWith("brick")) {
            return b.wall(outside, y);
        }
        // the split-face block weathers as the brick does: soot, the pocks where they shot at the
        // door, moss at the foot, rust runs and cracks
        double r = rng.nextDouble();
        return y <= 2 && r < 0.18 ? "sh_cmu_tan_moss" : r < 0.1 ? "sh_cmu_tan_stain" : r < 0.17 ? "sh_cmu_tan_crack" : outside;
    }

    private String inner() {
        return inside;
    }

    private void shell(int x1, int z0, int z1, int za, int zb) {
        for (int x = 0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == 0 || x == x1 || z == z0 || z == z1;
                String floor = z > zb ? "floor_concrete"
                        : x >= 14 && z < za ? (rng.nextDouble() < 0.2 ? "park_floor_oil" : "park_floor")
                        : x <= 5 && z > za ? "home_floor_oak" : "sh_floor_vct";
                p.set(x, 0, z, edge ? wall(0) : floor);
                for (int y = 1; y <= TOP; y++) {
                    p.set(x, y, z, edge ? wall(y) : "air");
                }
            }
        }
        // the partitions: across at ZA and ZB, the window wall, the sally port's side, the office's
        wallZ(za, 1, x1 - 1);
        wallZ(zb, 1, x1 - 1, "sh_cmu_band");
        wallX(7, z0 + 1, za - 1);
        wallX(13, z0 + 1, zb - 1);
        wallX(6, za + 1, zb - 1);
    }

    private void wallX(int x, int za, int zb) {
        for (int z = za; z <= zb; z++) {
            for (int y = 1; y < TOP; y++) {
                p.set(x, y, z, inner());
            }
        }
    }

    private void wallZ(int z, int xa, int xb) {
        wallZ(z, xa, xb, inner());
    }

    private void wallZ(int z, int xa, int xb, String block) {
        for (int x = xa; x <= xb; x++) {
            for (int y = 1; y < TOP; y++) {
                p.set(x, y, z, block);
            }
        }
    }

    // ------------------------------------------------------------------ outside

    /** The front: the glass doors, the lobby and dispatch windows, the sally port's roller door, the lamps. */
    private void front(int x1, int z0) {
        Doors.pair(p, rng, 3, 1, z0, Doors.SHOP, "south");
        p.set(3, 3, z0, b.glass("x"));
        p.set(4, 3, z0, b.glass("x"));
        for (int x : new int[] {1, 9, 10, 11}) {
            for (int y = 2; y <= 3; y++) {
                p.set(x, y, z0, b.glass("x"));
            }
        }
        // the sally port: a roller door, run up or forced
        boolean up = rng.nextDouble() < 0.35;
        for (int x = 15; x <= 17; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, z0, up ? "air" : "garage_door[facing=north]");
            }
        }
        for (int x : new int[] {0, 14, x1 - 1}) {
            p.set(x, 3, z0 - 1, (lit && rng.nextDouble() < 0.6 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        }
        // the after-hours phone by the door, its light on where the power held
        p.set(6, 2, z0 - 1, (lit ? "sh_phone_lit" : "sh_phone") + "[facing=north]");
        // smoke up the wall over a window that burned; the pocks round the door they held
        for (int x : new int[] {1, 9, 10, 11}) {
            if (rng.nextDouble() < 0.2) {
                p.set(x, 4, z0, outside.startsWith("brick") ? outside + "_soot" : "sh_cmu_tan_soot");
                p.set(x, 5, z0, outside.startsWith("brick") ? outside + "_soot" : "sh_cmu_tan_soot");
            }
        }
        String pock = outside.equals("sh_cmu_tan") ? "sh_cmu_tan_pocks" : outside.equals("brick_red") ? "brick_red_holes" : null;
        for (int x = 1; x <= 7 && pock != null; x++) {
            for (int y = 1; y <= 5; y++) {
                if (outside.equals(p.get(x, y, z0)) && rng.nextDouble() < (overrun ? 0.3 : 0.12)) {
                    p.set(x, y, z0, pock);
                }
            }
        }
        // a tag sprayed on the blank wall by the sally port; ivy up the corners
        if (rng.nextDouble() < 0.6) {
            p.set(12 + rng.nextInt(2), 2, z0 - 1, "graffiti_" + rng.nextInt(8) + "[facing=south]");
        }
        for (int x : new int[] {0, x1}) {
            if (rng.nextDouble() < 0.45) {
                for (int y = 1; y <= 1 + rng.nextInt(4); y++) {
                    p.setIfAir(x, y, z0 - 1, "vines[facing=south]");
                }
            }
        }
    }

    /** The entry raised over the lobby, the letters on it, the star over the canopy. */
    private void entry(int z0) {
        for (int x = 0; x <= 10; x++) {
            for (int y = TOP + 1; y <= EY; y++) {
                p.set(x, y, z0, wall(y));
            }
            p.set(x, EY + 1, z0, "stone_trim");
        }
        for (int z = z0 + 1; z <= z0 + 2; z++) {
            for (int y = TOP + 1; y <= EY; y++) {
                p.set(0, y, z, wall(y));
                p.set(10, y, z, wall(y));
            }
            p.set(0, EY + 1, z, "stone_trim");
            p.set(10, EY + 1, z, "stone_trim");
        }
        // letters across x 2..9, a line to each of the top two rows, read from the lot: the first
        // tile at the viewer's left (high x)
        int fallen = rng.nextDouble() < 0.4 ? rng.nextInt(16) : -1;  // a tile's letters come down
        for (int row = 0; row < 2; row++) {
            for (int i = 0; i < 8; i++) {
                if (row * 8 + i == fallen) {
                    continue;
                }
                p.set(9 - i, EY - row, z0 - 1, "sh_letters_" + letters + "_" + row + "_" + i + "[facing=north]");
            }
        }
        // the star, two by two over the canopy: 0 top left as seen, so at the higher x
        p.set(4, 5, z0 - 1, "sh_star_0[facing=north]");
        p.set(3, 5, z0 - 1, "sh_star_1[facing=north]");
        p.set(4, 4, z0 - 1, "sh_star_2[facing=north]");
        p.set(3, 4, z0 - 1, "sh_star_3[facing=north]");
        // the canopy on its two posts
        for (int x = 2; x <= 5; x++) {
            p.set(x, 3, z0 - 1, "front_canopy_metal[facing=north]");
            p.set(x, 3, z0 - 2, "front_canopy_metal[facing=north]");
        }
        for (int x : new int[] {2, 5}) {
            p.set(x, 1, z0 - 2, "ext_post_base");
            p.set(x, 2, z0 - 2, "ext_post");
        }
    }

    private void sides(int x1, int z0, int z1) {
        for (int y = 2; y <= 3; y++) {
            p.set(0, y, z0 + 3, b.glass("z"));
            p.set(0, y, z0 + 10, b.glass("z"));
            p.set(0, y, z0 + 11, b.glass("z"));
        }
        // booking's glass block; the cells' slits high in the back wall
        for (int y = 3; y <= 4; y++) {
            p.set(x1, y, z0 + 9, "sh_glassblock");
            p.set(x1, y, z0 + 10, "sh_glassblock");
            for (int x : new int[] {2, 6, 10}) {
                p.set(x, y, z1, "sh_glassblock");
            }
        }
        // the corridor's way out at the side
        Doors.abandoned(p, rng, 0, 1, z0 + 15, Doors.STEEL, "west", Doors.hinge(rng));
        p.set(1, 3, z0 + 15, "cv_exit_red[facing=east]");
    }

    /** The lot: three stalls before the lobby and dispatch, the cruisers in them nose in; the sally port's apron clear. */
    private void lot(int x1, int z0) {
        for (int x = 0; x <= x1; x++) {
            for (int z = 0; z < z0; z++) {
                double r = rng.nextDouble();
                p.set(x, 0, z, z == z0 - 1 ? "fs_apron" : r < 0.25 ? "asphalt_crack" : r < 0.32 ? "asphalt_patch" : "asphalt");
                p.set(x, 1, z, "air");
            }
        }
        for (int x : new int[] {0, 4, 8, 12}) {
            for (int z = 2; z <= z0 - 2; z++) {
                if (rng.nextDouble() < 0.85) {
                    p.set(x, 0, z, "road_line_white[axis=z]");
                }
            }
        }
        for (int i = 0; i < 3; i++) {
            if (rng.nextDouble() < 0.75) {
                p.vehicle(2.5 + 4 * i, 1, 3.5, (float) (rng.nextDouble() * 6 - 3), "police_sedan,police_suv,police_sedan", rng.nextLong());
            }
        }
        p.set(2, 1, z0 - 1, "sh_sign_reserved[facing=north]");
        p.set(10, 1, z0 - 1, "sh_sign_reserved[facing=north]");
        new Civic(p, rng).flagpole(13, 3, 9);
        // where they held the door: sandbags, the casings, the blood; a barrier dragged across the drive
        if (overrun || rng.nextDouble() < 0.4) {
            for (int x = 1; x <= 7; x++) {
                if (x != 3 && x != 4 && rng.nextDouble() < 0.85) {
                    p.set(x, 1, z0 - 4, "sandbags[facing=north]");
                }
            }
            for (int i = 0; i < 6; i++) {
                p.setIfAir(1 + rng.nextInt(7), 1, z0 - 3 - rng.nextInt(3), rng.nextDouble() < 0.6 ? "bunk_casings" : "blood_splat");
            }
        }
        if (rng.nextDouble() < 0.5) {
            p.set(15 + rng.nextInt(2), 1, 1, "jersey_barrier[facing=" + (rng.nextBoolean() ? "north" : "east") + "]");
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(rng.nextInt(x1 + 1), 1, rng.nextInt(z0 - 1), rng.nextBoolean() ? "paper" : "glass_shards");
        }
    }

    /** The flat roof: the radio tower and its beacon, the plant, razor wire along the back over the cells. */
    private void roof(int x1, int z0, int z1) {
        int y = TOP + 1, tx = x1 - 3, tz = z1 - 3;
        int h = 7 + rng.nextInt(3);
        for (int k = 0; k < h; k++) {
            p.set(tx, y + k, tz, "sh_tower");
        }
        p.set(tx, y + h, tz, (lit || rng.nextDouble() < 0.3 ? "sh_tower_top_lit" : "sh_tower_top"));
        p.set(4, y, z1 - 4, "hvac");
        p.set(9, y, z0 + 4, "hvac");
        p.set(12, y, z1 - 2, "ext_flue");
        p.set(12, y + 1, z1 - 2, "ext_flue_cap");
        for (int x = 0; x <= 12; x++) {
            p.set(x, TOP + 3, z1, "barbed_wire[axis=x]");
        }
        for (int i = 0; i < 3; i++) {
            p.setIfAir(1 + rng.nextInt(x1 - 1), y, z0 + 3 + rng.nextInt(z1 - z0 - 4), rng.nextBoolean() ? "rubble_pile" : "paper");
        }
    }

    // ------------------------------------------------------------------ the front band

    /** The lobby: benches under the wanted board, the flags, the window and its counter, the buzzer door. */
    private void lobby(int z0, int za) {
        // the window in the wall at x = 7: the counter, the glass with its tray and speaker over it
        for (int z = z0 + 2; z <= z0 + 3; z++) {
            p.set(7, 1, z, "sh_counter[facing=west]");
            p.set(7, 2, z, "sh_window_lo[facing=west]");
            p.set(7, 3, z, "sh_window_hi[facing=west]");
        }
        Doors.abandoned(p, rng, 7, 1, z0 + 5, "bunk_door_steel", "west", Doors.hinge(rng));
        p.set(6, 3, z0 + 5, "sh_plaque_staff[facing=west]");
        // benches along the west wall, the wanted board over them (facing east: tile 0 at the higher z)
        for (int z = z0 + 4; z <= z0 + 6; z++) {
            p.set(1, 1, z, overrun && rng.nextDouble() < 0.4 ? "air" : "cv_hall_bench[facing=east]");
        }
        p.set(1, 3, z0 + 5, "sh_wanted_0[facing=east]");
        p.set(1, 3, z0 + 4, "sh_wanted_1[facing=east]");
        p.set(1, 1, z0 + 1, "cv_flag_us[facing=east]");
        p.set(1, 2, z0 + 1, "cv_flag_us_top[facing=east]");
        p.set(6, 1, z0 + 1, rng.nextDouble() < 0.5 ? "home_plant_dead" : "home_plant");
        p.set(6, 1, za - 1, "trade_water_cooler[facing=west]");
        p.set(3, 3, za - 1, "bk_wall_clock[facing=north]");
        b.mess(2, z0 + 1, 6, za - 1, 1, overrun ? 0.18 : 0.06);
    }

    /** Dispatch: the clerk's desk at the window, the console against the front wall, the county map. */
    private void dispatch(int z0, int za) {
        for (int z = z0 + 2; z <= z0 + 3; z++) {
            p.set(8, 1, z, "desk[facing=west]");
            p.set(9, 1, z, rng.nextDouble() < 0.75 ? "office_chair[facing=west]" : "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        p.set(8, 2, z0 + 2, rng.nextBoolean() ? "home_phone[facing=east]" : "paper");
        p.set(8, 2, z0 + 3, "sh_typewriter[facing=east]");
        // the console faces south off the front wall: its end panel sits at the model's west, so at the higher x
        String on = lit ? "_lit" : "";
        p.set(11, 1, z0 + 1, "sh_dispatch_l" + on + "[facing=south]");
        p.set(10, 1, z0 + 1, "sh_dispatch_r" + on + "[facing=south]");
        p.set(10, 1, z0 + 2, rng.nextDouble() < 0.8 ? "office_chair[facing=north]" : "air");
        p.set(11, 1, z0 + 2, rng.nextDouble() < 0.5 ? "office_chair[facing=north]" : "air");
        // the county map on the wall at x = 13, facing west: the first column at the lower z
        p.set(12, 3, z0 + 2, "sh_map_0[facing=west]");
        p.set(12, 3, z0 + 3, "sh_map_1[facing=west]");
        p.set(12, 2, z0 + 2, "sh_map_2[facing=west]");
        p.set(12, 2, z0 + 3, "sh_map_3[facing=west]");
        p.set(12, 1, za - 2, "filing_cabinet[facing=west]");
        p.set(12, 1, za - 1, "pk_file_cabinet[facing=west]");
        p.set(8, 3, za - 1, "home_calendar[facing=north]");
        Doors.abandoned(p, rng, 10, 1, za, Doors.STEEL, "north", Doors.hinge(rng));
        p.set(11, 2, za + 1, "sh_plaque_dispatch[facing=south]");
        b.mess(8, z0 + 4, 12, za - 1, 1, 0.06);
    }

    /** The sally port: a cruiser left in it or not, the gun lockers they used before going through to booking. */
    private void sallyPort(int x1, int z0, int za) {
        if (rng.nextDouble() < 0.5) {
            p.vehicle(16.5, 1, z0 + 3.5, 0, "police_sedan,police_suv", rng.nextLong());
        }
        for (int z = za - 2; z <= za - 1; z++) {
            p.set(14, 1, z, "sh_locker[facing=east]");
            p.set(14, 2, z, "sh_locker_top[facing=east]");
        }
        Doors.abandoned(p, rng, 16, 1, za, "bunk_door_steel", "north", Doors.hinge(rng));
        p.set(17, 3, za - 1, "sh_plaque_booking[facing=north]");
        p.set(18, 4, za - 1, (lit ? "bunk_lamp_red_lit" : "bunk_lamp_red") + "[facing=north]");
        p.set(x1 - 1, 2, z0 + 3, "bunk_extinguisher[facing=west]");
        for (int x = 14; x <= 18; x++) {
            p.set(x, 0, za - 3, "park_line_yellow[axis=x]");
        }
    }

    // ------------------------------------------------------------------ the middle band

    /** The sheriff's own office: the desk facing the door, the flags behind, the gun cabinet. */
    private void office(int za, int zb) {
        Doors.abandoned(p, rng, 6, 1, za + 3, "home_door_oak", "west", Doors.hinge(rng));
        p.set(7, 3, za + 3, "sh_plaque_sheriff[facing=east]");
        p.set(3, 1, za + 3, "desk[facing=east]");
        p.set(3, 1, za + 4, "desk[facing=east]");
        p.set(2, 1, za + 3, rng.nextDouble() < 0.8 ? "office_chair[facing=east]" : "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.set(3, 2, za + 3, "home_phone[facing=west]");
        p.set(3, 2, za + 4, rng.nextBoolean() ? "paper" : "cv_stack[axis=z]");
        p.set(5, 1, za + 3, "pk_chair_wooden[facing=west]");
        p.set(5, 1, za + 4, "pk_chair_wooden[facing=west]");
        p.set(1, 1, za + 1, "cv_flag_us[facing=east]");
        p.set(1, 2, za + 1, "cv_flag_us_top[facing=east]");
        p.set(1, 1, zb - 1, "cv_flag_state[facing=east]");
        p.set(1, 2, zb - 1, "cv_flag_state_top[facing=east]");
        p.set(1, 3, za + 4, "cv_portrait[facing=east]");
        p.set(5, 1, za + 1, "home_gun_cabinet[facing=south]");
        p.set(5, 2, za + 1, "home_gun_cabinet_top[facing=south]");
        p.set(5, 1, zb - 1, "pk_file_cabinet[facing=north]");
        p.set(4, 3, zb - 1, "fit_painting_" + "abc".charAt(rng.nextInt(3)) + "[facing=north]");
        b.mess(2, za + 1, 5, zb - 1, 1, 0.05);
    }

    /** The squad room: desks back to back with their typewriters, the shotgun rack, the lockers, the coffee. */
    private void squad(int za, int zb) {
        for (int x = 8; x <= 9; x++) {
            p.set(x, 1, za + 3, "desk[facing=south]");
            p.set(x, 1, za + 2, chair("south"));
            p.set(x, 1, za + 4, "desk[facing=north]");
            p.set(x, 1, za + 5, chair("north"));
            p.set(x, 2, za + 3, rng.nextDouble() < 0.5 ? "sh_typewriter[facing=north]" : rng.nextBoolean() ? "paper" : "air");
            p.set(x, 2, za + 4, rng.nextDouble() < 0.5 ? "sh_typewriter[facing=south]" : rng.nextBoolean() ? "home_phone[facing=south]" : "air");
        }
        p.set(12, 2, zb - 2, "sh_gun_rack[facing=west]");
        for (int z = zb - 2; z <= zb - 1; z++) {
            p.set(7, 1, z, "sh_locker[facing=east]");
            p.set(7, 2, z, "sh_locker_top[facing=east]");
        }
        p.set(12, 1, za + 1, "home_counter_white[facing=west]");
        p.set(12, 2, za + 1, "home_coffee_maker[facing=west]");
        p.set(12, 1, za + 2, "pk_vending[facing=west]");
        p.set(7, 3, za + 5, "home_calendar[facing=east]");
        Doors.abandoned(p, rng, 13, 1, za + 3, Doors.STEEL, "west", Doors.hinge(rng));
        Doors.abandoned(p, rng, 9, 1, zb, "bunk_door_steel", "north", Doors.hinge(rng));
        p.set(10, 3, zb - 1, "sh_plaque_holding[facing=north]");
        b.mess(7, za + 1, 12, zb - 1, 1, overrun ? 0.12 : 0.06);
    }

    private String chair(String facing) {
        double r = rng.nextDouble();
        return r < 0.7 ? "office_chair[facing=" + facing + "]" : r < 0.85 ? "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]" : "air";
    }

    /** Booking: the counter across from the sally port's door, the height chart and the camera, the ink, the cuff bench. */
    private void booking(int x1, int za, int zb) {
        p.set(16, 1, za + 3, "sh_booking[facing=north]");
        p.set(17, 1, za + 3, "sh_booking[facing=north]");
        p.set(16, 1, za + 4, chair("north"));
        p.set(17, 2, za + 3, "paper");
        p.set(x1 - 1, 1, za + 5, "sh_height_0[facing=west]");
        p.set(x1 - 1, 2, za + 5, "sh_height_1[facing=west]");
        p.set(15, 1, za + 5, "sh_camera[facing=east]");
        p.set(x1 - 1, 1, za + 1, "sh_ink[facing=west]");
        p.set(x1 - 1, 1, za + 2, "pk_file_cabinet[facing=west]");
        p.set(17, 1, zb - 1, "sh_cuff_bench[facing=north]");
        p.set(18, 1, zb - 1, "sh_cuff_bench[facing=north]");
        p.set(17, 3, zb - 1, "bunk_clock[facing=north]");
        Doors.abandoned(p, rng, 16, 1, zb, "bunk_door_steel", "north", Doors.hinge(rng));
        b.mess(14, za + 1, 18, zb - 1, 1, overrun ? 0.12 : 0.05);
    }

    // ------------------------------------------------------------------ the cell block

    /** Three cells behind bars off the corridor; the corridor's caged lamps. */
    private void cells(int z1, int zb) {
        int zc = zb + 1, bars = zb + 2;
        for (int x = 2; x <= 17; x += 4) {
            p.set(x, 4, zc, (lit && rng.nextDouble() < 0.5 ? "bunk_lamp_lit" : "bunk_lamp") + "[facing=south]");
        }
        if (lit) {
            p.set(1, 4, zc, "bunk_lamp_red_lit[facing=east]");
        }
        for (int c = 0; c < 3; c++) {
            int cx0 = 1 + 4 * c, cx = cx0 + 1;
            // the walls between, scratched
            if (c < 2) {
                for (int z = bars; z < z1; z++) {
                    for (int y = 1; y < TOP; y++) {
                        p.set(cx0 + 3, y, z, scratched());
                    }
                }
            }
            for (int y = 1; y < TOP; y++) {
                for (int z = bars; z < z1; z++) {
                    if (c == 2) {
                        p.set(12, y, z, scratched());
                    }
                }
            }
            // the bars under a block header, the cage door, the number over it
            for (int x = cx0; x <= cx0 + 2; x++) {
                for (int y = 1; y <= 3; y++) {
                    boolean bent = overrun && x != cx && y == 1 && rng.nextDouble() < 0.15;
                    p.set(x, y, bars, bent ? "air" : "cv_cell_bars[axis=x]");
                }
                p.set(x, 4, bars, inner());
                p.set(x, 5, bars, inner());
            }
            if (cx0 + 3 <= 12) {
                for (int y = 1; y < TOP; y++) {
                    p.set(cx0 + 3, y, bars, inner());
                }
            }
            boolean open = rng.nextDouble() < (overrun ? 0.85 : 0.4);
            p.set(cx, 1, bars, "bunk_door_cage[facing=north,half=lower,hinge=left,open=" + open + "]");
            p.set(cx, 2, bars, "bunk_door_cage[facing=north,half=upper,hinge=left,open=" + open + "]");
            p.set(cx, 4, zc, "sh_cell_sign_" + (c + 1) + "[facing=north]");
            // inside: the bunk along the west wall (pillow end to the north), a second over it in some,
            // the steel toilet in the back corner, the drain, the light
            for (int y : rng.nextDouble() < 0.4 ? new int[] {1, 2} : new int[] {1}) {
                p.set(cx0, y, z1 - 2, "sh_bunk_l[facing=east]");
                p.set(cx0, y, z1 - 1, "sh_bunk_r[facing=east]");
            }
            p.set(cx0 + 2, 1, z1 - 1, "fit_toilet_steel[facing=north]");
            p.set(cx, 0, bars + 2, "bunk_floor_drain");
            p.set(cx0 + 2, 4, z1 - 2, (lit && rng.nextDouble() < 0.4 ? "bunk_lamp_lit" : "bunk_lamp") + "[facing=west]");
            if (rng.nextDouble() < (overrun ? 0.8 : 0.35)) {
                p.setIfAir(cx0 + 1 + rng.nextInt(2), 1, bars + 1 + rng.nextInt(Math.max(1, z1 - bars - 2)),
                        rng.nextBoolean() ? "home_blood_pool" : "home_mattress_floor[facing=" + DIRS[rng.nextInt(4)] + "]");
            }
            if (rng.nextDouble() < 0.4) {
                p.setIfAir(cx0 + 1, 1, bars + 1, rng.nextBoolean() ? "paper" : "trash_bag[facing=" + DIRS[rng.nextInt(4)] + "]");
            }
        }
        if (overrun) {
            for (int i = 0; i < 6; i++) {
                p.setIfAir(1 + rng.nextInt(17), 1, zc, rng.nextDouble() < 0.5 ? "home_blood_pool" : "bunk_casings");
            }
        }
    }

    private String scratched() {
        double r = rng.nextDouble();
        return r < 0.3 ? "sh_cmu_scratch" : r < 0.55 ? "sh_cmu_scratch_b" : inner();
    }

    /** The armory and evidence store: gun lockers down one side, evidence shelves the other, the rack on the back wall. */
    private void armory(int z1, int zb) {
        int bars = zb + 2;
        for (int x = 13; x <= 15; x++) {
            for (int y = 1; y < TOP; y++) {
                p.set(x, y, bars, inner());
            }
        }
        for (int z = bars; z < z1; z++) {
            for (int y = 1; y < TOP; y++) {
                p.set(16, y, z, inner());
            }
        }
        Doors.abandoned(p, rng, 14, 1, bars, "bunk_door_steel", "north", Doors.hinge(rng));
        p.set(14, 3, bars - 1, "sh_plaque_armory[facing=north]");
        boolean raided = overrun || rng.nextDouble() < 0.5;
        for (int z = bars + 1; z < z1; z++) {
            p.set(13, 1, z, raided && rng.nextDouble() < 0.5 ? "air" : "sh_locker[facing=east]");
            p.set(13, 2, z, p.get(13, 1, z) == null ? "air" : "sh_locker_top[facing=east]");
            p.set(15, 1, z, "sh_evidence[facing=west]");
            if (rng.nextDouble() < 0.5) {
                p.set(15, 2, z, "sh_evidence[facing=west]");
            }
        }
        p.set(14, 2, z1 - 1, "sh_gun_rack[facing=north]");
        if (raided) {
            for (int i = 0; i < 3; i++) {
                p.setIfAir(14, 1, bars + 1 + rng.nextInt(Math.max(1, z1 - bars - 1)), rng.nextBoolean() ? "cardboard_box[facing=north]" : "bunk_casings");
            }
        }
    }

    /** The interview room behind its one-way glass: the steel table, the two chairs. */
    private void interview(int z1, int zb) {
        int bars = zb + 2;
        for (int x = 17; x <= 18; x++) {
            for (int y = 1; y < TOP; y++) {
                p.set(x, y, bars, inner());
            }
        }
        Doors.abandoned(p, rng, 17, 1, bars, "home_door_white", "north", Doors.hinge(rng));
        p.set(18, 2, bars, "sh_mirror[facing=north]");
        p.set(17, 3, bars - 1, "sh_plaque_interview[facing=north]");
        int tz = bars + 2;
        p.set(18, 1, tz, "sh_interview_table[facing=north]");
        p.set(18, 1, tz - 1, rng.nextDouble() < 0.7 ? "bunk_chair[facing=south]" : "bunk_chair_down[facing=" + DIRS[rng.nextInt(4)] + "]");
        p.set(18, 1, tz + 1, "bunk_chair[facing=north]");
        p.set(17, 4, z1 - 1, "bunk_lamp[facing=north]");
    }
}
