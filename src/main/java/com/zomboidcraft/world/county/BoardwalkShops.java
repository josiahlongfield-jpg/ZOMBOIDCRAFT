package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;

/**
 * The row along Seabright Boardwalk ({@link Boardwalk}): one-storey shops from Ocean Avenue to the
 * boards, each its own colour, a tall false front over its awning carrying its neon, the shop
 * windows either side of the door, fitted out for what it sold; the back doors and the bins on
 * the avenue. The Funland arcade takes the long frontage past the plaza, open to the boards behind
 * its shutters, its floor rows of claw machines, skee-ball and pinball, the prize counter at the
 * end (boardwalk-arcade 01-03).
 *
 * <p>Drawn in the front's frame: a shop runs x from {@link Boardwalk#BACK} (the avenue) to
 * {@link Boardwalk#FRONT} (the boards, facing east), z its frontage.
 */
final class BoardwalkShops {
    private final Boardwalk.Front c;
    private static final int X0 = Boardwalk.BACK, X1 = Boardwalk.FRONT, H = 5;

    BoardwalkShops(Boardwalk.Front c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    void build() {
        shop(-196, -178, "surf", "plaster_blue", "surf", 4);
        shop(-172, -152, "games", "wf_shingle", "games", 3);
        shop(-146, -126, "pizza", "plaster_cream", "pizza", 6);
        shop(-120, -102, "fries", "brick_red", "fries", 5);
        shop(-96, -74, "taffy", "plaster_sage", "taffy", 6);
        shop(-68, -40, "gifts", "wf_clap", null, 0);
        arcade(40, 100);
        shop(106, 124, "cream", "plaster_cream", "cream", 4);
        shop(130, 150, "tees", "wf_shingle_b", null, 0);
        shop(156, 176, "patrol", "siding_white", null, 0);
        shop(182, 198, "restroom", "plaster_blue", null, 0);
    }

    // ------------------------------------------------------------------ a shop

    /**
     * A shop from z0 to z1: its walls, the flat roof and its coping, the false front up over the
     * roof with the neon on it (cols wide), the awning, the windows and the door; fitted out.
     */
    private void shop(int z0, int z1, String key, String wall, String neon, int cols) {
        int top = H + 1;
        for (int x = X0; x <= X1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == X0 || x == X1 || z == z0 || z == z1;
                set(x, 0, z, edge ? "stone_trim" : key.equals("restroom") ? "floor_checker" : "floor_boards");
                for (int y = 1; y <= H; y++) {
                    set(x, y, z, edge ? wall : "air");
                }
                set(x, top, z, edge ? "wf_trim" : "un_roof");
                if (edge && x != X1) {
                    String out = x == X0 ? "west" : z == z0 ? "north" : "south";
                    boolean corner = (x == X0) && (z == z0 || z == z1);
                    set(x, top + 1, z, corner ? "bd_coping_corner" : "bd_coping[facing=" + out + "]");
                }
            }
            if (x % 9 == 0 && x > X0 + 2 && x < X1 - 4) {
                // the rooftop units and their ducts
                set(x, top + 1, (z0 + z1) / 2, "bd_hvac_a[facing=east]");
            }
        }
        // the false front: two higher than the roof, stepping up two more over its middle third,
        // the pilasters at its ends, the sign band under the cap
        int dz = (z0 + z1) / 2, mid = (z1 - z0) / 4;
        for (int z = z0; z <= z1; z++) {
            boolean raised = Math.abs(z - dz) <= mid;
            int capY = top + (raised ? 5 : 3);
            for (int y = top + 1; y < capY; y++) {
                set(X1, y, z, wall);
            }
            set(X1, capY, z, "wf_trim");
            if (raised && Math.abs(z - dz) == mid) {
                set(X1, top + 3, z, "wf_trim");
            }
            if (z == z0 || z == z1) {
                for (int y = 1; y <= top + 3; y++) {
                    set(X1, y, z, "wf_trim");
                }
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            if (Math.abs(z - dz) >= 2 && Math.abs(z - dz) < (z1 - z0) / 2 - 1) {
                set(X1, 1, z, wall);
                set(X1, 2, z, c.chance() < c.b.damage * 0.5 ? "window_broken[axis=z]" : "wf_window[facing=east]");
                set(X1, 3, z, "wf_window[facing=east]");
            }
            // the awning over the boards, torn here and there
            if (c.chance() > 0.12) {
                set(X1 + 1, 4, z, awning(key) + "[facing=east]");
            }
        }
        c.door(X1, 0, dz, Doors.SHOP, "east");
        c.door(X0, 0, dz, Doors.STEEL, "west");
        set(X0 - 2, 1, dz + 3, "dumpster[facing=west]");
        if (neon != null) {
            c.neon(neon, cols, 1, X1 + 1, top + 2, dz - cols / 2, "east");
        } else {
            c.sign(key.equals("patrol") ? "beach" : key.equals("restroom") ? "nodogs" : "ramp", X1 + 1, 5, dz - 1, "east");
        }
        fitOut(key, z0, z1);
        c.lamps(X0, z0, X1, z1, top, 0.5);
        c.mess(X0 + 1, z0 + 1, X1 - 1, z1 - 1, 0, 0.06);
    }

    private String awning(String key) {
        return switch (key) {
            case "pizza", "fries" -> "awning_red";
            case "surf", "patrol" -> "awning_blue";
            case "taffy", "cream" -> "awning_teal";
            default -> "awning_yellow";
        };
    }

    /** Fitted out for what it sold: the back wall's line, the counter across, the shelves down the sides. */
    private void fitOut(String key, int z0, int z1) {
        int dz = (z0 + z1) / 2, ck = X1 - 6;
        switch (key) {
            case "pizza", "fries" -> {
                String[] line = key.equals("pizza") ? new String[] {"store_oven", "store_oven", "dn_prep", "dn_lowboy"}
                        : new String[] {"dn_fryer", "dn_fryer", "dn_griddle", "dn_prep"};
                for (int z = z0 + 1; z < z1; z++) {
                    set(ck - 4, 1, z, line[Math.floorMod(z, line.length)] + "[facing=east]");
                    set(ck, 1, z, (z == dz ? "store_register" : "store_counter") + "[facing=east]");
                }
                c.row("wf_menu_", 2, ck - 5, 3, dz + 1, "east", 0);
                for (int z = z0 + 2; z < z1 - 1; z += 4) {
                    set(X1 - 3, 1, z, c.any("dn_table_set", "dn_table_mess", "dn_table"));
                    set(X1 - 3, 1, z + 1, c.chance() < 0.3 ? "home_chair_tipped[facing=north]" : "home_chair[facing=north]");
                }
            }
            case "taffy" -> {
                for (int x = X0 + 3; x < X1 - 2; x++) {
                    set(x, 1, z0 + 1, (c.chance() < 0.6 ? "tr_candy_empty" : "tr_candy") + "[facing=south]");
                    set(x, 1, z1 - 1, (c.chance() < 0.6 ? "tr_candy_empty" : "tr_candy") + "[facing=north]");
                }
                // the pulling machines in the window, where the crowds watched them
                set(X1 - 2, 1, dz - 3, "wf_taffy[facing=east]");
                set(X1 - 2, 1, dz + 3, "wf_taffy[facing=east]");
                for (int z = z0 + 3; z < z1 - 2; z++) {
                    set(ck - 3, 1, z, (z == dz ? "store_register" : "store_counter") + "[facing=east]");
                }
            }
            case "gifts", "tees", "surf" -> {
                for (int x = X0 + 2; x < X1 - 1; x++) {
                    set(x, 1, z0 + 1, (key.equals("gifts") ? "wf_gifts" : "wf_tees") + "[facing=south]");
                    set(x, 1, z1 - 1, (key.equals("gifts") ? "wf_gifts" : "wf_tees") + "[facing=north]");
                    if (!key.equals("gifts")) {
                        set(x, 2, z0 + 1, "wf_tees[facing=south]");
                        set(x, 2, z1 - 1, "wf_tees[facing=north]");
                    }
                }
                for (int x = X0 + 5; x < X1 - 4; x += 4) {
                    for (int z = z0 + 4; z <= z1 - 4; z += 3) {
                        set(x, 1, z, key.equals("gifts") ? "store_table" : c.chance() < 0.7 ? "store_clothes_rack[facing=east]" : "air");
                    }
                }
                if (key.equals("surf")) {
                    for (int z = z0 + 3; z < z1 - 2; z++) {
                        set(X0 + 1, 1, z, "store_shelf_hardware[facing=east]");
                    }
                }
                set(X1 - 3, 1, z1 - 3, "store_register[facing=east]");
            }
            case "cream" -> {
                for (int z = z0 + 1; z < z1; z++) {
                    set(ck - 3, 1, z, "dn_prep[facing=east]");
                    set(ck, 1, z, (z == z0 + 2 ? "store_register" : "wf_freezer") + "[facing=east]");
                }
                c.row("wf_menu_", 2, ck - 4, 3, dz + 1, "east", 0);
                for (int z = z0 + 2; z < z1 - 1; z += 3) {
                    set(X1 - 2, 1, z, "store_table");
                    set(X1 - 3, 1, z, c.any("dn_stool_r", "dn_stool_b", "dn_stool_torn"));
                }
            }
            case "games" -> {
                for (int x = X0 + 2; x < X1 - 2; x++) {
                    cabinet(x, z0 + 1, "south");
                    cabinet(x, z1 - 1, "north");
                }
                for (int z = z0 + 4; z <= z1 - 4; z += 2) {
                    set(X0 + 6, 1, z, "sb_claw_lo[facing=east]");
                    set(X0 + 6, 2, z, (c.power && c.chance() < 0.6 ? "sb_claw_hi_on" : "sb_claw_hi") + "[facing=east]");
                }
            }
            case "patrol" -> {
                // the beach patrol's room: the rescue boards on the wall, the desk, the cots, the first aid
                for (int x = X0 + 2; x < X1 - 2; x += 2) {
                    set(x, 1, z0 + 1, "ch_cot_head[facing=south]");
                    set(x, 1, z0 + 2, "ch_cot_foot[facing=south]");
                }
                set(X1 - 4, 1, dz, "home_desk[facing=east]");
                set(X1 - 5, 1, dz, "office_chair[facing=east]");
                for (int x = X0 + 3; x < X1 - 6; x += 3) {
                    set(x, 2, z1 - 1, "ship_lifebuoy[facing=north]");
                }
                set(X0 + 2, 1, z1 - 2, "sb_guard_lo[facing=north]");
            }
            case "restroom" -> {
                // the stalls down one side, the basins the other; the showers out the back
                for (int x = X0 + 2; x < X1 - 2; x += 2) {
                    set(x, 1, z0 + 1, "fit_toilet_stall[facing=south]");
                    set(x, 1, z1 - 1, "home_sink_white[facing=north]");
                }
            }
            default -> {
            }
        }
    }

    private void cabinet(int x, int z, String facing) {
        set(x, 1, z, "un_arcade_lo[facing=" + facing + "]");
        set(x, 2, z, "un_arcade_hi" + (c.power && c.chance() < 0.5 ? "_on" : "") + "[facing=" + facing + "]");
    }

    // ------------------------------------------------------------------ the arcade

    /**
     * Funland: the long arcade past the plaza, seven high inside, its front open to the boards
     * between the posts behind roll shutters half down; the marquee over it. Inside the claw
     * machines in rows down the front, the skee-ball lanes against the back wall, the pinball and
     * the cabinets between, the prize counter and its wall of plush at the south end.
     */
    private void arcade(int z0, int z1) {
        int h = 7, top = h + 1;
        for (int x = X0; x <= X1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == X0 || x == X1 || z == z0 || z == z1;
                set(x, 0, z, edge ? "stone_trim" : (x + z) % 2 == 0 ? "tr_carpet" : "tr_carpet_b");
                for (int y = 1; y <= h; y++) {
                    set(x, y, z, edge ? "wf_shingle" : "air");
                }
                set(x, top, z, edge ? "wf_trim" : "un_roof");
                if (edge && x != X1) {
                    String out = x == X0 ? "west" : z == z0 ? "north" : "south";
                    set(x, top + 1, z, x == X0 && (z == z0 || z == z1) ? "bd_coping_corner" : "bd_coping[facing=" + out + "]");
                }
            }
        }
        // the open front: posts every eight, shutters half down between
        for (int z = z0 + 1; z < z1; z++) {
            boolean post = Math.floorMod(z - z0, 8) == 0;
            for (int y = 1; y <= h; y++) {
                set(X1, y, z, post ? "wf_trim" : y >= 5 ? "wf_shingle" : y == 4 ? (c.chance() < 0.7 ? "shutter[axis=z]" : "air") : "air");
            }
            if (!post && c.chance() < 0.25) {
                set(X1, 3, z, "shutter[axis=z]");
            }
        }
        // the facade over the posts painted blue, the bulbs along under the sign, the sign up past the roof
        int dz = (z0 + z1) / 2;
        for (int z = z0; z <= z1; z++) {
            boolean raised = Math.abs(z - dz) <= 8;
            for (int y = 5; y <= top + (raised ? 5 : 2); y++) {
                if (Math.floorMod(z - z0, 8) != 0 || y > h) {
                    set(X1, y, z, y == 5 ? "sb_white" : "sb_blue");
                }
            }
            set(X1, top + (raised ? 6 : 3), z, "wf_trim");
            set(X1 + 1, 5, z, (c.power && c.chance() < 0.85 ? "sb_marquee_on" : "sb_marquee") + "[facing=east]");
        }
        c.neon("funland", 12, 3, X1 + 1, top + 1, dz - 6, "east");
        c.neon("skee", 4, 1, X1 + 1, 5, z0 + 3, "east");
        c.neon("skee", 4, 1, X1 + 1, 5, z1 - 6, "east");
        c.door(X0, 0, dz, Doors.STEEL, "west");
        // claw machines in two rows down the front
        for (int z = z0 + 2; z < z1 - 8; z++) {
            if (Math.floorMod(z - z0, 8) == 0) {
                continue;
            }
            for (int x : new int[] {X1 - 4, X1 - 5}) {
                String f = x == X1 - 4 ? "east" : "west";
                set(x, 1, z, "sb_claw_lo[facing=" + f + "]");
                set(x, 2, z, (c.power && c.chance() < 0.6 ? "sb_claw_hi_on" : c.chance() < c.b.damage * 0.4 ? "glass_shards" : "sb_claw_hi") + "[facing=" + f + "]");
            }
        }
        // the skee-ball lanes against the back wall, rolling west
        for (int z = z0 + 2; z < z1 - 10; z++) {
            set(X0 + 1, 1, z, (c.power && c.chance() < 0.5 ? "sb_skee_b_on" : "sb_skee_b") + "[facing=west]");
            set(X0 + 2, 1, z, "sb_skee_a[facing=west]");
        }
        // pinball and cabinets back to back between
        for (int z = z0 + 3; z < z1 - 10; z++) {
            if (Math.floorMod(z - z0, 6) == 0) {
                continue;  // the aisles across
            }
            set(X0 + 9, 1, z, "sb_pinball[facing=west]");
            set(X0 + 10, 1, z, "sb_pinball[facing=east]");
            cabinet(X0 + 17, z, "west");
            cabinet(X0 + 18, z, "east");
            if (c.chance() < 0.12) {
                set(X0 + 13, 1, z, c.any("home_corpse_head[facing=" + c.dir() + "]", "home_blood_pool", "paper", "pk_trash_bag"));
            }
        }
        // the prize counter and its wall
        for (int x = X0 + 2; x < X1 - 6; x++) {
            set(x, 1, z1 - 1, "sb_prize[facing=north]");
            set(x, 2, z1 - 1, "sb_prize[facing=north]");
            set(x, 1, z1 - 5, (x == X0 + 8 ? "store_register" : "store_counter") + "[facing=north]");
        }
        c.lamps(X0, z0, X1, z1, top, 0.6);
        c.mess(X0 + 1, z0 + 1, X1 - 1, z1 - 1, 0, 0.05);
    }
}
