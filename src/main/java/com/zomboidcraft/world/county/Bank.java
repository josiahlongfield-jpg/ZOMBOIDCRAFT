package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The bank on the square. There are no bank photos in the library yet, so this follows what the
 * small-town banks of 1900 to 1925 are known for: a limestone temple front up two steps, two
 * columns between square piers carrying the frieze with the bank's name, a pediment over (or a
 * plain attic), bronze doors under a tall round-arched window, the brick behind it; and what 1993
 * added: the cash machine in the portico (some still glowing), the night depository, the clock on
 * its post at the curb, the red alarm lamp.
 *
 * Inside, the banking hall two storeys high under a coffered ceiling, black and white marble
 * underfoot, the officers' desks behind their rail, check desks, the queue's rope; the teller
 * line of green marble under bronze grilles with an arched wicket at each window and cresting
 * along the top; behind it the tellers' stools and the stair to the mezzanine (the board room
 * and the bookkeepers); and at the back the vault, its round door three blocks across: hanging
 * open where the bank was robbed in the last days (the deposit boxes pried, cash and money bags
 * on the floor), or still shut on whatever is inside. BANK CLOSED on the doors.
 *
 * Drawn in its lot's frame, front along z = 0 facing north, an even number of blocks wide (12 on
 * the squares). Ground at y = 0, the floor's slab at F, the mezzanine's at M, the roof at TOP.
 */
final class Bank {
    static final int F = 2, M = F + 4, TOP = F + 10, Z0 = 4;
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final String brick, name;
    private final boolean robbed, pediment, atmLit;

    Bank(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.3 + rng.nextDouble() * 0.35;
        b.boarded = rng.nextDouble() < 0.4 ? 0.55 : 0.15;
        this.brick = rng.nextDouble() < 0.6 ? "brick_buff" : "brick_red";
        this.name = rng.nextBoolean() ? "a" : "b";
        this.robbed = rng.nextDouble() < 0.7;
        this.pediment = rng.nextDouble() < 0.45;
        this.atmLit = rng.nextDouble() < 0.35;
    }

    /** A bank w wide and d deep (its steps and portico in the first four rows); w at least 12, d at least 18. */
    void build(int w, int d) {
        w -= w & 1;
        int z0 = Z0, z1 = d - 1, cx = w / 2;
        int zv = z1 - (d >= 22 ? 6 : 5);              // the vault's front wall
        int zt = Math.min(z0 + 8, zv - 5);            // the teller line
        boolean mezz = zt - z0 - 1 >= 4;
        if (!mezz) {
            zt = zv - 3;
        }
        shell(w, z0, z1);
        portico(w, cx, z0);
        front(cx, z0);
        sides(w, z0, z1, zt, zv);
        roof(w, z0, z1, cx);
        lobby(w, z0, zt, cx);
        tellers(w, zt, zv, cx, mezz);
        vault(w, zv, z1, cx);
        if (mezz) {
            mezzanine(w, zt, z1, cx);
        }
        street(w, cx);
    }

    // ------------------------------------------------------------------ the shell

    private void shell(int w, int z0, int z1) {
        int x1 = w - 1;
        for (int y = 0; y <= TOP; y++) {
            for (int x = 0; x <= x1; x++) {
                p.set(x, y, z0, stone(y));
                p.set(x, y, z1, y <= F ? stone(y) : b.wall(brick, y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                String s = y <= F || z <= z0 + 1 ? stone(y) : b.wall(brick, y);
                p.set(0, y, z, s);
                p.set(x1, y, z, s);
            }
        }
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                p.set(x, 0, z, "cellar_wall");
                p.set(x, 1, z, "cellar_wall");
                p.set(x, F, z, "bk_floor_check");
                for (int y = F + 1; y < TOP; y++) {
                    p.set(x, y, z, "air");
                }
                p.set(x, TOP, z, "bk_coffer");
            }
        }
        // the water table round the foot, the stone band at the mezzanine's level down the sides
        for (int z = z0; z <= z1; z++) {
            p.set(-1, F, z, "cv_water_table[facing=west]");
            p.set(x1 + 1, F, z, "cv_water_table[facing=east]");
            p.set(-1, M, z, "cv_belt[facing=west]");
            p.set(x1 + 1, M, z, "cv_belt[facing=east]");
        }
        for (int x = 0; x <= x1; x++) {
            p.set(x, F, z1 + 1, "cv_water_table[facing=south]");
        }
    }

    private String stone(int y) {
        return y < F ? "ch_rubble" : b.wall("stone_trim", y);
    }

    // ------------------------------------------------------------------ the portico

    /** The steps, the porch between the piers, the two columns, the entablature with the name, the pediment or attic. */
    private void portico(int w, int cx, int z0) {
        int x1 = w - 1;
        // the piers either side, stone from the ground to the cornice
        for (int y = 0; y <= TOP; y++) {
            for (int z = 1; z < z0; z++) {
                p.set(0, y, z, stone(y));
                p.set(x1, y, z, stone(y));
            }
        }
        // the steps up from the walk, the porch floor
        for (int x = 1; x < x1; x++) {
            p.set(x, 0, 0, "stone_trim");
            p.set(x, 1, 0, "cv_step_stone[facing=south]");
            p.set(x, 0, 1, "stone_trim");
            p.set(x, 1, 1, "stone_trim");
            p.set(x, F, 1, "cv_step_stone[facing=south]");
            for (int z = 2; z < z0; z++) {
                p.set(x, 0, z, "stone_trim");
                p.set(x, 1, z, "stone_trim");
                p.set(x, F, z, rng.nextDouble() < 0.1 ? "stone_trim_crack" : "pt_stone_flags");
                for (int y = F + 1; y < TOP; y++) {
                    p.set(x, y, z, "air");
                }
                p.set(x, TOP, z, "stone_trim");
            }
        }
        // the columns, a block in from each pier
        for (int x : new int[] {cx - 3, cx + 2}) {
            p.set(x, F + 1, 2, "cv_column_base_stone");
            for (int y = F + 2; y < TOP - 1; y++) {
                p.set(x, y, 2, "cv_column_stone");
            }
            p.set(x, TOP - 1, 2, "cv_column_cap_stone[facing=north]");
        }
        // the frieze over them with the bank's name, its first tile at the viewer's left; the cornice
        int fw = x1 - 1;
        for (int x = 1; x < x1; x++) {
            int i = x1 - 1 - x;
            p.set(x, TOP, 2, fw == 10 ? "bk_frieze_" + name + "_10_" + i + "[facing=north]" : "stone_trim");
        }
        for (int x = 0; x <= x1; x++) {
            p.set(x, TOP + 1, 1, "cv_cornice_stone[facing=north]");
            for (int z = 2; z < z0 + 2; z++) {
                p.set(x, TOP + 1, z, "stone_trim");
            }
        }
        for (int z = 2; z <= z0 + 1; z++) {
            p.set(-1, TOP + 1, z, "cv_cornice_stone[facing=west]");
            p.set(x1 + 1, TOP + 1, z, "cv_cornice_stone[facing=east]");
        }
        if (pediment) {
            // the pediment: low, one in two, its stone run back over the portico as the roof; the round window in its face
            for (int i = 0; 2 * i + 1 < x1 - 2 * i; i++) {
                int y = TOP + 2 + i;
                for (int z = 2; z <= z0 + 1; z++) {
                    for (int x = 2 * i; x <= x1 - 2 * i; x++) {
                        int k = x - 2 * i, r = x1 - 2 * i - x;
                        p.set(x, y, z, k < 2 ? "bk_rake_l_" + k + "[facing=north]" : r < 2 ? "bk_rake_r_" + r + "[facing=north]" : "stone_trim");
                    }
                }
            }
            p.set(cx, TOP + 3, 2, "cv_oculus_white_l[facing=north]");
            p.set(cx - 1, TOP + 3, 2, "cv_oculus_white_r[facing=north]");
        } else {
            // the attic: a plain stone stage with its coping, the date of the bank cut in a panel
            for (int x = 0; x <= x1; x++) {
                p.set(x, TOP + 2, 2, "stone_trim");
                p.set(x, TOP + 3, 2, "cv_cornice_stone[facing=north]");
            }
        }
    }

    /** The front wall behind the columns: the bronze doors under the tall arched window, the bays either side. */
    private void front(int cx, int z0) {
        int y = F;
        for (int x = cx - 1; x <= cx; x++) {
            for (int yy = y + 3; yy <= TOP - 3; yy++) {
                p.set(x, yy, z0, b.glass("x"));
            }
        }
        Doors.pair(p, rng, cx - 1, y + 1, z0, "ext_door_oak", "south");
        p.set(cx, TOP - 2, z0, "cv_arch_stone_l[facing=north]");
        p.set(cx - 1, TOP - 2, z0, "cv_arch_stone_r[facing=north]");
        p.set(cx, y + 3, z0 + 1, "cv_exit_red[facing=south]");
        for (int x : new int[] {cx - 4, cx - 3, cx + 2, cx + 3}) {
            for (int yy = y + 2; yy <= TOP - 3; yy++) {
                p.set(x, yy, z0, b.glass("x"));
            }
            p.set(x, TOP - 2, z0, "cv_arch_stone_1[facing=north]");
            p.set(x, y + 1, z0 - 1, "sill[facing=north]");
        }
        // the cash machine in the west bay, the night depository under the east one, the notices on the doors
        p.set(cx - 4, y + 1, z0, (atmLit ? "bk_atm_lit" : "bk_atm") + "[facing=north]");
        p.set(cx - 4, y + 1, z0 - 1, "air");
        p.set(cx + 3, y + 1, z0 - 1, "bk_night_drop[facing=north]");
        p.set(cx - 2, y + 2, z0 - 1, "bk_notice[facing=north]");
        p.set(cx + 1, y + 2, z0 - 1, rng.nextBoolean() ? "bk_notice[facing=north]" : "th_notice_" + rng.nextInt(3) + "[facing=north]");
        p.set(cx - 2, y + 4, z0 - 1, (rng.nextDouble() < 0.4 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        p.set(cx + 1, y + 4, z0 - 1, (rng.nextDouble() < 0.4 ? "ext_sconce_lit" : "ext_sconce") + "[facing=north]");
        if (robbed) {
            // the doors were forced: glass on the porch, a door off its hinges
            for (int i = 0; i < 5; i++) {
                p.setIfAir(1 + rng.nextInt(2 * cx - 2), y + 1, 2 + rng.nextInt(z0 - 2), rng.nextBoolean() ? "glass_shards" : "paper");
            }
        }
    }

    /** Tall windows down the sides over the lobby, small barred ones over the tellers; the staff door. */
    private void sides(int w, int z0, int z1, int zt, int zv) {
        int x1 = w - 1;
        for (int z = z0 + 2; z < zt - 1; z += 3) {
            for (int[] s : new int[][] {{0, -1}, {x1, 1}}) {
                String out = s[1] < 0 ? "west" : "east";
                for (int yy = F + 2; yy <= TOP - 3; yy++) {
                    p.set(s[0], yy, z, b.glass("z"));
                }
                p.set(s[0], TOP - 2, z, "cv_arch_" + (brick.equals("brick_buff") ? "buff" : "red") + "_1[facing=" + out + "]");
                p.set(s[0] + s[1], F + 1, z, "sill[facing=" + out + "]");
            }
        }
        for (int z = zt + 2; z < zv; z += 3) {
            for (int[] s : new int[][] {{0, -1}, {x1, 1}}) {
                p.set(s[0], F + 3, z, b.glass("z"));
                p.set(s[0] + s[1], F + 3, z, "cv_grille[facing=" + (s[1] < 0 ? "west" : "east") + "]");
            }
        }
        Doors.abandoned(p, rng, x1, F + 1, zt + 1, "fire_door", "east", Doors.hinge(rng));
        p.set(x1 + 1, F, zt + 1, "stone_trim");
        p.set(x1 + 1, F - 1, zt + 1, "stone_trim");
        p.set(x1 + 2, F - 1, zt + 1, "cv_step_stone[facing=west]");
        // the alarm lamp high on the side, still flashing red in some
        p.set(x1 + 1, TOP - 2, z0 + 1, (rng.nextDouble() < 0.45 ? "bunk_lamp_red_lit" : "bunk_lamp_red") + "[facing=east]");
    }

    /** Flat behind the front: a brick parapet with a stone coping round the sides and back, a skylight. */
    private void roof(int w, int z0, int z1, int cx) {
        int x1 = w - 1;
        for (int z = z0 + 2; z <= z1; z++) {
            p.set(0, TOP + 1, z, b.wall(brick, TOP + 1));
            p.set(x1, TOP + 1, z, b.wall(brick, TOP + 1));
            p.set(0, TOP + 2, z, "stone_trim");
            p.set(x1, TOP + 2, z, "stone_trim");
        }
        for (int x = 0; x <= x1; x++) {
            p.set(x, TOP + 1, z1, b.wall(brick, TOP + 1));
            p.set(x, TOP + 2, z1, "stone_trim");
        }
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 2; z < z1; z++) {
                if (rng.nextDouble() < 0.1) {
                    p.set(x, TOP + 1, z, rng.nextBoolean() ? "moss_carpet" : "rubble_pile");
                }
            }
        }
        // the skylight over the hall, its laylight of leaded glass in the coffers below
        int sz = z0 + 3;
        for (int x = cx - 1; x <= cx; x++) {
            for (int z = sz; z <= sz + 1; z++) {
                p.set(x, TOP, z, "cv_skylight");
            }
        }
        p.set(2, TOP + 1, z1 - 2, "hvac");
    }

    // ------------------------------------------------------------------ the banking hall

    private void lobby(int w, int z0, int zt, int cx) {
        int x1 = w - 1, y = F;
        // the marble border round the walls
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 1; z < zt; z++) {
                if (x == 1 || x == x1 - 1 || z == z0 + 1) {
                    p.set(x, y, z, "bk_floor_border");
                }
            }
        }
        // the marble dado round the walls, under everything that stands against them
        for (int z = z0 + 1; z < zt; z++) {
            p.set(1, y + 1, z, "bk_dado[facing=east]");
            p.set(x1 - 1, y + 1, z, "bk_dado[facing=west]");
        }
        for (int x = 1; x < x1; x++) {
            if (x != cx - 1 && x != cx) {
                p.set(x, y + 1, z0 + 1, "bk_dado[facing=south]");
            }
        }
        // the officers' platform in the front west corner behind its rail: the president's and the cashier's desks
        int ox = 3, oz = Math.min(z0 + 4, zt - 2);
        for (int z = z0 + 1; z <= oz; z++) {
            p.set(ox + 1, y + 1, z, z == z0 + 2 ? "air" : "cv_bar_rail[axis=z]");
        }
        for (int x = 1; x <= ox + 1; x++) {
            p.set(x, y + 1, oz + 1, x == ox + 1 ? "cv_bar_post" : "cv_bar_rail[axis=x]");
        }
        for (int z = z0 + 1; z <= oz; z++) {
            for (int x = 1; x <= ox; x++) {
                p.set(x, y, z, "home_floor_carpet_green");
            }
        }
        p.set(2, y + 1, z0 + 1, "desk[facing=east]");
        p.set(1, y + 1, z0 + 1, rng.nextDouble() < 0.7 ? "office_chair[facing=east]" : "air");
        p.set(2, y + 2, z0 + 1, (rng.nextDouble() < 0.25 ? "cv_table_lamp_lit" : "cv_table_lamp") + "[axis=z]");
        if (oz >= z0 + 3) {
            p.set(2, y + 1, z0 + 3, "desk[facing=east]");
            p.set(1, y + 1, z0 + 3, rng.nextDouble() < 0.7 ? "office_chair[facing=east]" : "air");
            p.set(3, y + 1, z0 + 3, "pk_chair_wooden[facing=west]");
            p.set(2, y + 2, z0 + 3, rng.nextBoolean() ? "home_phone[facing=east]" : "cv_stack[axis=z]");
        }
        p.set(1, y + 3, z0 + 2, "bk_plaque_president[facing=east]");
        p.set(1, y + 5, z0 + 2, "cv_portrait[facing=east]");
        // the check desks down the middle, the rope for the queue along the east side
        for (int z = z0 + 2; z < zt - 1; z += 3) {
            p.set(cx - 1, y + 1, z, "bk_check_desk");
            p.set(cx, y + 1, z, "bk_check_desk");
        }
        int qx = x1 - 3;
        for (int z = z0 + 2; z <= zt - 2; z++) {
            p.set(qx, y + 1, z, z == z0 + 2 ? "trade_rope_post_end[facing=south]" : z == zt - 2 ? "trade_rope_post_end[facing=north]"
                    : rng.nextDouble() < 0.2 ? "air" : "trade_rope_post[axis=z]");
        }
        p.set(x1 - 1, y + 2, z0 + 2, "trade_rates_board[facing=west]");
        p.set(x1 - 1, y + 2, z0 + 4, "bk_poster[facing=west]");
        p.set(x1 - 1, y + 1, z0 + 1, "planter");
        // the bench by the east wall where customers waited for the officers
        for (int z = z0 + 5; z < Math.min(z0 + 7, zt - 1); z++) {
            p.set(x1 - 1, y + 1, z, "cv_hall_bench[facing=west]");
        }
        p.set(x1 - 1, y + 5, z0 + 3, "cv_portrait_b[facing=west]");
        // the globes hung from the coffers on chains
        for (int x : new int[] {cx - 3, cx + 2}) {
            for (int z = z0 + 2; z < zt; z += 4) {
                p.set(x, TOP - 1, z, "cv_chain");
                p.set(x, TOP - 2, z, "cv_chain");
                p.set(x, TOP - 3, z, rng.nextDouble() < 0.15 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            }
        }
        if (robbed) {
            for (int i = 0; i < 4; i++) {
                p.setIfAir(2 + rng.nextInt(x1 - 3), y + 1, z0 + 1 + rng.nextInt(zt - z0 - 1), "bk_notes[facing=" + DIRS[rng.nextInt(4)] + "]");
            }
            p.setIfAir(cx + 1, y + 1, zt - 1, "home_blood_pool");
            for (int i = 0; i < 6; i++) {
                p.setIfAir(2 + rng.nextInt(x1 - 3), y + 1, z0 + 1 + rng.nextInt(zt - z0 - 1), "bunk_casings");
            }
        }
        b.mess(1, z0 + 1, x1 - 1, zt - 1, y + 1, 0.04);
    }

    /**
     * The teller line at zt: green marble counters, the bronze grilles with a wicket at every third,
     * the cresting, the gate at the east end; behind it the stools, the tills, the stair up.
     */
    private void tellers(int w, int zt, int zv, int cx, boolean mezz) {
        int x1 = w - 1, y = F, gate = x1 - 1, k = 0;
        for (int x = 1; x < x1; x++) {
            if (x == gate) {
                Doors.abandoned(p, rng, x, y + 1, zt, Doors.FLAT, "north", Doors.hinge(rng));
                p.set(x, y + 3, zt, "bk_crest[facing=north]");
                continue;
            }
            boolean wicket = x % 3 == 2;
            p.set(x, y + 1, zt, "bk_counter[facing=north]");
            String top = wicket ? "bk_wicket_" + (k++ % 3) : "bk_grille";
            p.set(x, y + 2, zt, rng.nextDouble() < b.damage * 0.25 ? "air" : top + "[facing=north]");
            p.set(x, y + 3, zt, "bk_crest[facing=north]");
            if (wicket) {
                p.set(x, y + 1, zt + 1, rng.nextDouble() < 0.3 ? "store_stool" : "pk_stool_1[facing=north]");
            }
        }
        for (int x = 1; x < x1; x++) {
            for (int z = zt + 1; z < zv; z++) {
                p.set(x, y, z, "home_floor_oak");
            }
        }
        // the tellers' side: the cash drawers' cabinet against the vault wall, the adding machines' desk, the coat rack
        for (int x = 1; x < cx - 2; x++) {
            p.set(x, y + 1, zv - 1, (rng.nextBoolean() ? "filing_cabinet" : "pk_file_cabinet") + "[facing=north]");
        }
        p.set(1, y + 1, zt + 2, "desk[facing=east]");
        p.set(1, y + 2, zt + 2, rng.nextBoolean() ? "paper" : "air");
        p.set(cx + 2, y + 1, zv - 1, "home_coat_rack[facing=north]");
        p.set(cx + 1, y + 1, zv - 1, "store_safe[facing=north]");
        if (mezz) {
            // the stair up the east wall to the mezzanine, a rail on its open side
            for (int i = 0; i < M - F; i++) {
                int z = zt + 1 + i;
                p.set(x1 - 1, y + 1 + i, z, "home_stairs_oak[facing=south]");
                for (int yy = y + 1; yy < y + 1 + i; yy++) {
                    p.set(x1 - 1, yy, z, "cv_panel_oak");
                }
                for (int yy = y + 2 + i; yy <= y + 4 + i && yy < TOP; yy++) {
                    p.set(x1 - 1, yy, z, "air");
                }
                p.set(x1 - 2, y + 1 + i, z, "cv_bar_rail[axis=z]");
            }
            for (int x = 1; x < x1; x++) {
                p.set(x, M - 1, zt + 2, (x - 1) % 4 == 1 ? (rng.nextDouble() < 0.1 ? "ceiling_light" : "ceiling_light_off") : "air");
            }
        }
        if (robbed) {
            for (int i = 0; i < 4; i++) {
                p.setIfAir(1 + rng.nextInt(x1 - 2), y + 1, zt + 1 + rng.nextInt(Math.max(1, zv - zt - 1)),
                        rng.nextDouble() < 0.6 ? "bk_notes[facing=" + DIRS[rng.nextInt(4)] + "]" : "paper");
            }
        }
        b.mess(1, zt + 1, x1 - 2, zv - 1, y + 1, 0.05);
    }

    // ------------------------------------------------------------------ the vault

    /** The vault across the back: steel walls, the round door, the day gate, walls of deposit boxes, the safes. */
    private void vault(int w, int zv, int z1, int cx) {
        int x1 = w - 1, y = F, fx0 = cx - 2, fx1 = cx;
        for (int x = 1; x < x1; x++) {
            for (int yy = y + 1; yy <= M; yy++) {
                p.set(x, yy, zv, "cellar_wall");
            }
            for (int z = zv + 1; z < z1; z++) {
                p.set(x, y, z, "floor_concrete");
                p.set(x, M, z, "cellar_wall");
            }
        }
        // the door: its frame in the wall; the door open on its west hinge, or shut
        for (int j = 0; j < 3; j++) {
            for (int i = 0; i < 3; i++) {
                int t = j * 3 + i, yy = y + 3 - j;
                p.set(fx1 - i, yy, zv, (robbed ? "bk_vault_frame_" : "bk_vault_shut_") + t + "[facing=north]");
                if (robbed) {
                    p.set(fx0 - 1, yy, zv - 3 + i, "bk_vault_door_" + t + "[facing=west]");
                }
            }
        }
        p.set(fx1 + 1, y + 3, zv - 1, "bk_plaque_vault[facing=north]");
        // inside: the day gate behind the door, deposit boxes round the walls three high, the cash safes
        for (int x = fx0; x <= fx1; x++) {
            for (int yy = y + 1; yy <= y + 3; yy++) {
                boolean way = x == cx - 1 && yy <= y + 2;
                p.set(x, yy, zv + 1, way ? (robbed ? "air" : "cv_cell_bars[axis=x]") : "cv_cell_bars[axis=x]");
            }
        }
        String box = robbed ? "bk_deposit_pried" : "bk_deposit";
        for (int yy = y + 1; yy <= y + 3; yy++) {
            for (int z = zv + 1; z < z1; z++) {
                p.set(1, yy, z, (robbed && rng.nextDouble() < 0.3 ? "bk_deposit" : box) + "[facing=east]");
                p.set(x1 - 1, yy, z, (robbed && rng.nextDouble() < 0.3 ? "bk_deposit" : box) + "[facing=west]");
            }
            for (int x = 2; x < x1 - 1; x++) {
                p.set(x, yy, z1 - 1, (robbed && rng.nextDouble() < 0.3 ? "bk_deposit" : box) + "[facing=north]");
            }
        }
        p.set(2, y + 1, zv + 1, "store_safe[facing=east]");
        p.set(x1 - 2, y + 1, zv + 1, "cellar_safe[facing=west]");
        p.set(x1 - 2, y + 2, zv + 1, "cellar_safe_top[facing=west]");
        // the table where the box holders sat with their boxes
        int tz = (zv + z1) / 2;
        p.set(cx - 1, y + 1, tz, "cv_table[axis=x]");
        p.set(cx, y + 1, tz, "cv_table[axis=x]");
        p.set(cx - 1, y + 1, tz + 1, "pk_chair_wooden[facing=north]");
        p.set(cx - 1, M - 1, tz, rng.nextDouble() < 0.15 ? "bunk_tube_lit[axis=x]" : "bunk_tube[axis=x]");
        if (robbed) {
            for (int i = 0; i < 7; i++) {
                p.setIfAir(2 + rng.nextInt(x1 - 3), y + 1, zv + 2 + rng.nextInt(Math.max(1, z1 - zv - 3)),
                        rng.nextDouble() < 0.55 ? "bk_notes[facing=" + DIRS[rng.nextInt(4)] + "]" : rng.nextDouble() < 0.6 ? "paper" : "blood_splat");
            }
            p.setIfAir(cx, y + 2, tz, "bk_notes[facing=north]");
        } else {
            p.setIfAir(cx, y + 2, tz, "bk_bags");
            p.setIfAir(2, y + 1, z1 - 2, "bk_bags");
            p.setIfAir(x1 - 2, y + 1, z1 - 2, "bk_bags");
        }
    }

    // ------------------------------------------------------------------ the mezzanine

    /** Over the tellers and the vault: the gallery along its rail, the board room west, the bookkeepers east. */
    private void mezzanine(int w, int zt, int z1, int cx) {
        int x1 = w - 1, y = M, za = zt + 3;
        for (int x = 1; x < x1; x++) {
            for (int z = zt + 1; z < z1; z++) {
                if (!(x == x1 - 1 && z <= zt + M - F - 1)) {
                    p.set(x, y, z, "home_floor_oak");
                }
            }
            p.set(x, y + 1, zt + 1, x == x1 - 1 ? "air" : "cv_bar_rail[axis=x]");
        }
        for (int z = zt + 1; z < zt + M - F; z++) {
            p.set(x1 - 2, y + 1, z, "cv_bar_rail[axis=z]");
        }
        // the partitions: along the gallery's back, between the two rooms
        for (int x = 1; x < x1; x++) {
            for (int yy = y + 1; yy < TOP; yy++) {
                // the stair comes up through the partition's east end into the bookkeepers' room
                if (x == x1 - 1 && yy <= y + 2) {
                    continue;
                }
                p.set(x, yy, za, yy == y + 1 ? "cv_panel_oak" : "cv_plaster");
            }
        }
        for (int z = za + 1; z < z1; z++) {
            for (int yy = y + 1; yy < TOP; yy++) {
                p.set(cx, yy, z, "cv_plaster");
            }
        }
        Doors.abandoned(p, rng, cx - 2, y + 1, za, "home_door_oak", "north", Doors.hinge(rng));
        Doors.abandoned(p, rng, cx + 2, y + 1, za, "home_door_oak", "north", Doors.hinge(rng));
        p.set(cx - 2, y + 3, za - 1, "bk_plaque_board[facing=north]");
        p.set(cx, y + 3, za - 1, "bk_wall_clock[facing=north]");
        for (int z = za + 2; z < z1 - 1; z += 3) {
            for (int[] s : new int[][] {{0, -1}, {x1, 1}}) {
                String in = s[1] < 0 ? "east" : "west", out = s[1] < 0 ? "west" : "east";
                p.set(s[0], y + 2, z, b.glass("z"));
                p.set(s[0], y + 3, z, b.glass("z"));
                p.set(s[0], y + 4, z, "cv_arch_" + (brick.equals("brick_buff") ? "buff" : "red") + "_1[facing=" + out + "]");
                p.set(s[0] + s[1], y + 1, z, "sill[facing=" + out + "]");
                if (p.get(s[0] - s[1], y + 1, z) == null) {
                    p.set(s[0] - s[1], y + 1, z, "home_radiator[facing=" + in + "]");
                }
            }
        }
        p.set(cx + 2, y + 3, za - 1, "bk_plaque_cashier[facing=north]");
        // the board room: the long table, its chairs, the founder looking down
        for (int z = za + 2; z < z1 - 1; z++) {
            p.set(cx - 3, y + 1, z, "cv_table[axis=z]");
            p.set(cx - 4, y + 1, z, rng.nextDouble() < 0.85 ? "pk_chair_wooden[facing=east]" : "air");
            p.set(cx - 2, y + 1, z, rng.nextDouble() < 0.85 ? "pk_chair_wooden[facing=west]" : "air");
        }
        p.set(cx - 3, y + 3, z1 - 1, "cv_portrait[facing=north]");
        for (int z = za + 3; z < z1 - 1; z += 3) {
            p.set(1, y + 3, z, "fit_painting_" + "abc".charAt(rng.nextInt(3)) + "[facing=east]");
        }
        p.set(1, y + 1, za + 1, "home_coat_rack[facing=east]");
        p.set(cx - 3, TOP - 1, (za + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        // the bookkeepers: desks with their ledgers, the cabinets, the calendar
        for (int z = za + 2; z < z1; z += 2) {
            p.set(cx + 2, y + 1, z, "desk[facing=west]");
            p.set(cx + 3, y + 1, z, rng.nextDouble() < 0.75 ? "office_chair[facing=west]" : "air");
            p.set(cx + 2, y + 2, z, rng.nextBoolean() ? "cv_stack[axis=z]" : "paper");
        }
        for (int z = za + 3; z < z1; z++) {
            if (rng.nextDouble() < 0.7) {
                p.set(x1 - 1, y + 1, z, (rng.nextBoolean() ? "filing_cabinet" : "pk_file_cabinet") + "[facing=west]");
            }
        }
        p.set(x1 - 1, y + 3, za + 2, "home_calendar[facing=west]");
        p.set(cx + 2, TOP - 1, (za + z1) / 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        b.mess(1, zt + 2, x1 - 2, z1 - 1, y + 1, 0.04);
    }

    // ------------------------------------------------------------------ the curb

    /** The clock on its post by the steps, both faces to the street. */
    private void street(int w, int cx) {
        int x = 0, z = 0;
        p.set(x, 1, z, "cv_lamp_post");
        p.set(x, 2, z, "cv_lamp_post");
        p.set(x, 3, z, "cv_lamp_post");
        p.set(x, 4, z, (rng.nextDouble() < 0.35 ? "bk_post_clock_lit" : "bk_post_clock") + "[facing=east]");
    }
}
