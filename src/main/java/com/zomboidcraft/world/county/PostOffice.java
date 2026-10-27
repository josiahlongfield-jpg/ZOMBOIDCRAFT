package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The town's post office, after the photos in refs/town/post-office. Either the federal one of
 * the thirties (Boonville): red brick on a limestone base up a short flight of steps, tall
 * round-headed windows with white keystones, UNITED STATES POST OFFICE cut in a stone band over
 * the doors, lanterns either side, a hipped green roof; or the sixties one most small towns got
 * (Waterville, Freeburn): a low brick box with six-pane windows and shutters, the name in
 * aluminium letters on the brick, glass doors up a step and a pipe rail, a low brown roof.
 *
 * Inside, the plan they all share: the lobby across the front, the screen line across the
 * building with the bronze box doors either side of the counter and its brass grilles, the
 * writing desks and the notice board of WANTED sheets; behind the screen the workroom (sorting
 * cases round the walls and back to back down the middle, canvas hampers, sacks of mail nobody
 * delivered), the postmaster's office with its safe, the restroom, and the loading dock at the
 * back under its canopy.
 *
 * Drawn in its lot's frame, front along z = 0 facing north, an even number of blocks wide so the
 * doors centre on a block line. Ground at y = 0, the floor's slab at y = F.
 */
final class PostOffice {
    static final int F = 2, Z0 = 4;

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final boolean federal;
    private final int top;
    private final String roof, shutter;

    PostOffice(Placer p, SplittableRandom rng) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        b.damage = 0.35 + rng.nextDouble() * 0.45;
        b.boarded = rng.nextDouble() < 0.3 ? 0.5 : 0.1;
        this.federal = rng.nextDouble() < 0.5;
        this.top = federal ? F + 6 : F + 5;
        this.roof = federal ? (rng.nextDouble() < 0.6 ? "green" : "slate") : (rng.nextDouble() < 0.6 ? "red" : "charcoal");
        this.shutter = rng.nextDouble() < 0.5 ? "black" : rng.nextDouble() < 0.5 ? "green" : "maroon";
    }

    /** Where a fallout shelter may be dug under a post office w by d: inside its walls, as {x0, z0, x1, z1}; its floor is at F. */
    static int[] host(int w, int d) {
        return new int[] {1, Z0 + 1, w - (w & 1) - 2, d - 2};
    }

    /** A post office w wide and d deep (its steps in the first four rows); d at least 18. */
    void build(int w, int d) {
        w -= w & 1;
        int z0 = Z0, z1 = d - 1, cx = w / 2, zs = z0 + 5, zo = z1 - 4;
        shell(w, z0, z1);
        windows(w, z0, z1, zs, zo, cx);
        entrance(w, z0, cx);
        hipDeck(-1, w, z0 - 1, z1 + 1, top + 1, federal ? 3 : 2);
        lobby(w, z0, zs, cx);
        screen(w, zs, cx);
        workroom(w, zs, zo, z1, cx);
        backRooms(w, zo, z1, cx);
        dock(w, z1, cx);
        grounds(w, z0, cx);
    }

    // ------------------------------------------------------------------ the shell

    private void shell(int w, int z0, int z1) {
        int x1 = w - 1;
        for (int y = 0; y <= top; y++) {
            for (int x = 0; x <= x1; x++) {
                p.set(x, y, z0, outer(y));
                p.set(x, y, z1, outer(y));
            }
            for (int z = z0 + 1; z < z1; z++) {
                p.set(0, y, z, outer(y));
                p.set(x1, y, z, outer(y));
            }
        }
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                for (int y = 0; y < F; y++) {
                    p.set(x, y, z, "stone_trim");
                }
            }
        }
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, F, federal ? "floor_boards" : "floor_lino");
        b.clear(1, F + 1, z0 + 1, x1 - 1, top - 1, z1 - 1);
        b.floor(1, z0 + 1, x1 - 1, z1 - 1, top, "cv_plaster");
        ring(w, z0, z1, F, federal ? "cv_water_table" : null);
        ring(w, z0, z1, top, federal ? "cv_cornice_white" : null);
    }

    private String outer(int y) {
        if (y <= F && federal) {
            return b.wall("stone_trim", y);
        }
        return b.wall(y == F && !federal ? "concrete_dark" : "brick_red", y);
    }

    /** One piece in front of every wall cell at y, all round, facing out from its wall. */
    private void ring(int w, int z0, int z1, int y, String piece) {
        if (piece == null) {
            return;
        }
        int x1 = w - 1;
        for (int x = 0; x <= x1; x++) {
            p.set(x, y, z0 - 1, piece + "[facing=north]");
            p.set(x, y, z1 + 1, piece + "[facing=south]");
        }
        for (int z = z0; z <= z1; z++) {
            p.set(-1, y, z, piece + "[facing=west]");
            p.set(x1 + 1, y, z, piece + "[facing=east]");
        }
    }

    /** The front's windows either side of the doors, then every third block down the sides and across the back. */
    private void windows(int w, int z0, int z1, int zs, int zo, int cx) {
        int x1 = w - 1;
        for (int x = 2; x <= cx - 4; x += 2) {
            window(x, z0, "north");
            window(x1 - x, z0, "north");
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            if (z == zs || z == zo) {
                continue;
            }
            window(0, z, "west");
            window(x1, z, "east");
        }
        window(3, z1, "south");
        window(x1 - 3, z1, "south");
    }

    /**
     * A window in a wall facing out: the federal one two panes under a round brick arch with a
     * white keystone and a stone sill; the sixties one a six-pane sash with shutters either side.
     */
    private void window(int x, int z, String out) {
        int[] o = Bldg.step(out);
        if (federal) {
            String a = Bldg.along(out);
            String g = b.glass(a);
            p.set(x, F + 2, z, g);
            p.set(x, F + 3, z, g.startsWith("boarded") ? g : b.glass(a).replace("boarded_window", "window"));
            p.set(x, F + 4, z, "cv_arch_red_1[facing=" + out + "]");
            p.set(x + o[0], F + 4, z + o[1], "cv_keystone[facing=" + out + "]");
            p.set(x + o[0], F + 1, z + o[1], "sill[facing=" + out + "]");
            return;
        }
        double r = rng.nextDouble();
        String state = r < b.boarded ? "boarded" : r < b.boarded + b.damage * 0.4 ? "broken" : r < b.boarded + b.damage * 0.55 ? "empty" : "glass";
        p.set(x, F + 2, z, "ext_win_white_six_" + state + "_lo[facing=" + out + "]");
        p.set(x, F + 3, z, "ext_win_white_six_" + state + "_hi[facing=" + out + "]");
        p.set(x, F + 4, z, "stone_trim");
        int[] sv = out.equals("north") || out.equals("south") ? new int[] {1, 0} : new int[] {0, 1};
        for (int k = -1; k <= 1; k += 2) {
            int sx = x + sv[0] * k + o[0], sz = z + sv[1] * k + o[1];
            if (rng.nextDouble() < 0.15 || p.get(sx, F + 2, sz) != null) {
                continue;
            }
            p.set(sx, F + 2, sz, "ext_shutter_" + shutter + "_lo[facing=" + out + "]");
            p.set(sx, F + 3, sz, "ext_shutter_" + shutter + "_hi[facing=" + out + "]");
        }
    }

    // ------------------------------------------------------------------ the roof

    /** A roof hipped on all four sides from y, `courses` courses up to a flat of roofing felt. */
    private void hipDeck(int x0, int x1, int z0, int z1, int y, int courses) {
        String k = "ext_roof_" + roof;
        for (int i = 0; i < courses; i++) {
            int lx = x0 + i, hx = x1 - i, lz = z0 + i, hz = z1 - i;
            String piece = i == 0 ? k + "_eave" : k;
            for (int x = lx + 1; x < hx; x++) {
                p.set(x, y + i, lz, course(piece) + "[facing=south]");
                p.set(x, y + i, hz, course(piece) + "[facing=north]");
            }
            for (int z = lz + 1; z < hz; z++) {
                p.set(lx, y + i, z, course(piece) + "[facing=east]");
                p.set(hx, y + i, z, course(piece) + "[facing=west]");
            }
            p.set(lx, y + i, hz, "cv_hip_" + roof + "[facing=north]");
            p.set(lx, y + i, lz, "cv_hip_" + roof + "[facing=east]");
            p.set(hx, y + i, lz, "cv_hip_" + roof + "[facing=south]");
            p.set(hx, y + i, hz, "cv_hip_" + roof + "[facing=west]");
        }
        for (int x = x0 + courses; x <= x1 - courses; x++) {
            for (int z = z0 + courses; z <= z1 - courses; z++) {
                p.set(x, y + courses - 1, z, rng.nextDouble() < 0.12 ? "roof_moss" : "roof");
            }
        }
    }

    private String course(String piece) {
        double r = rng.nextDouble();
        if (piece.endsWith("_eave")) {
            return r < 0.12 ? piece + "_bare" : piece;
        }
        return r < 0.1 ? piece + "_moss" : r < 0.15 ? piece + "_patch" : piece;
    }

    // ------------------------------------------------------------------ the front

    /**
     * The doors and what is round them: the federal front's steps between stone cheeks, the
     * lettered band, the fanlight and lanterns; the sixties front's glass doors up a step with a
     * pipe rail, the aluminium letters beside them, a porch light.
     */
    private void entrance(int w, int z0, int cx) {
        if (federal) {
            for (int x = cx - 3; x <= cx + 2; x++) {
                boolean cheek = x == cx - 3 || x == cx + 2;
                for (int z = z0 - 3; z < z0; z++) {
                    int h = cheek ? F : z == z0 - 3 ? F - 2 : z == z0 - 2 ? F - 1 : F;
                    for (int y = 0; y <= h; y++) {
                        p.set(x, y, z, "stone_trim");
                    }
                    if (!cheek) {
                        if (z == z0 - 3) {
                            p.set(x, F - 1, z, "cv_step_stone[facing=south]");
                        } else if (z == z0 - 2) {
                            p.set(x, F, z, "cv_step_stone[facing=south]");
                        } else {
                            p.set(x, F, z, rng.nextDouble() < 0.1 ? "stone_trim_crack" : "pt_stone_flags");
                        }
                    }
                }
                if (cheek) {
                    p.set(x, F + 1, z0 - 3, "cv_lamp_post");
                    p.set(x, F + 2, z0 - 3, rng.nextDouble() < 0.4 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
                    p.set(x, F + 1, z0 - 1, "cv_iron_post");
                }
            }
            Doors.pair(p, rng, cx - 1, F + 1, z0, "ext_door_oak", "south");
            p.set(cx - 1, F + 3, z0, b.glass("x"));
            p.set(cx, F + 3, z0, b.glass("x"));
            p.set(cx, F + 4, z0, "cv_arch_red_l[facing=north]");
            p.set(cx - 1, F + 4, z0, "cv_arch_red_r[facing=north]");
            // the stone band across the front with the name cut in it, first letter at the viewer's left (east)
            int bx0 = cx - 5, bx1 = cx + 4;
            for (int x = bx0; x <= bx1; x++) {
                p.set(x, top - 1, z0, "cv_pofrieze_10_" + (bx1 - x) + "[facing=north]");
            }
            for (int x : new int[] {cx - 2, cx + 1}) {
                p.set(x, F + 3, z0 - 1, rng.nextDouble() < 0.35 ? "ext_sconce_lit[facing=north]" : "ext_sconce[facing=north]");
            }
        } else {
            for (int x = cx - 3; x <= cx + 2; x++) {
                for (int z = z0 - 2; z < z0; z++) {
                    for (int y = 0; y < F; y++) {
                        p.set(x, y, z, "floor_concrete");
                    }
                    // two steps up to the landing, half a block each (a block's rise strands a zombie)
                    boolean inner = x > cx - 3 && x < cx + 2;
                    p.set(x, F, z, inner && z == z0 - 2 ? "cv_step_stone[facing=south]" : b.wall("floor_concrete", 3));
                }
                for (int y = 0; y < F - 1; y++) {
                    p.set(x, y, z0 - 3, "floor_concrete");
                }
                p.set(x, F - 1, z0 - 3, "cv_step_stone[facing=south]");
            }
            for (int x : new int[] {cx - 3, cx + 2}) {
                p.set(x, F + 1, z0 - 2, "ext_rail[axis=z]");
                p.set(x, F + 1, z0 - 1, "ext_rail[axis=z]");
            }
            // a step up through the sill, the glass doors, the transom over them
            Doors.pair(p, rng, cx - 1, F + 1, z0, Doors.SHOP, "south");
            p.set(cx - 1, F, z0, "stone_trim");
            p.set(cx, F, z0, "stone_trim");
            p.set(cx - 1, F + 3, z0, b.glass("x"));
            p.set(cx, F + 3, z0, b.glass("x"));
            p.set(cx - 2, F + 3, z0 - 1, rng.nextDouble() < 0.35 ? "ext_sconce_lit[facing=north]" : "ext_sconce[facing=north]");
            // the aluminium letters on the top course of brick, centred over the doors, clear of the lintels
            for (int i = 0; i < 6; i++) {
                if (rng.nextDouble() > 0.08) {
                    p.set(cx + 2 - i, F + 5, z0 - 1, "cv_poletters_6_" + i + "[facing=north]");
                }
            }
            p.set(1, 1, z0 - 1, "pk_aircon[facing=north]");
        }
    }

    /** The flagpole and the blue box by the walk; an old cast iron post at the curb. */
    private void grounds(int w, int z0, int cx) {
        int fx = rng.nextBoolean() ? 2 : w - 3;
        p.set(fx, 1, 1, "cv_flagpole_base");
        for (int y = 2; y < 11; y++) {
            p.set(fx, y, 1, "cv_flagpole");
        }
        p.set(fx, 11, 1, "cv_flagpole_top[facing=north]");
        int mx = fx == 2 ? w - 4 : 3;
        p.set(mx, 1, 0, rng.nextDouble() < 0.25 ? "cv_mailbox_tipped[facing=north]" : "cv_mailbox[facing=north]");
        if (rng.nextDouble() < 0.6) {
            p.set(mx + (mx < cx ? 1 : -1), 1, 0, "cv_mailbox[facing=north]");
        }
    }

    // ------------------------------------------------------------------ inside

    /** The lobby: terrazzo or lino, writing desks, the WANTED board, a bench, the flag, globes or tubes overhead. */
    private void lobby(int w, int z0, int zs, int cx) {
        int x1 = w - 1, y = F;
        for (int x = 1; x < x1; x++) {
            for (int z = z0 + 1; z < zs; z++) {
                boolean edge = x == 1 || x == x1 - 1 || z == z0 + 1 || z == zs - 1;
                p.set(x, y, z, federal ? (edge ? "cv_hex_border" : "cv_terrazzo") : (edge ? "floor_lino" : "home_floor_lino_check"));
            }
        }
        p.set(cx - 3, y + 1, z0 + 2, "cv_po_desk[facing=north]");
        p.set(cx + 2, y + 1, z0 + 2, "cv_po_desk[facing=north]");
        p.set(cx - 3, y + 2, z0 + 1, "cv_wanted[facing=south]");
        p.set(cx + 2, y + 2, z0 + 1, "trade_honor_board[facing=south]");
        p.set(1, y + 1, z0 + 2, "cv_hall_bench[facing=east]");
        p.set(1, y + 1, z0 + 3, "cv_hall_bench[facing=east]");
        p.set(x1 - 1, y + 1, z0 + 1, "cv_flag_us[facing=west]");
        p.set(x1 - 1, y + 2, z0 + 1, "cv_flag_us_top[facing=west]");
        p.set(x1 - 1, y + 1, z0 + 3, "home_radiator[facing=west]");
        p.set(1, y + 1, z0 + 1, "ext_trash_can");
        for (int x = cx - 4; x <= cx + 3; x += 7) {
            if (federal) {
                p.set(x, top - 1, z0 + 2, "cv_chain");
                p.set(x, top - 2, z0 + 2, rng.nextDouble() < 0.15 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
            } else {
                p.set(x, top - 1, z0 + 2, (rng.nextDouble() < 0.1 ? "fit_tube_on" : rng.nextDouble() < 0.2 ? "fit_tube_flicker" : "fit_tube") + "[axis=x]");
            }
        }
        // the people who came here last: mail dropped, the queue's blood, a sack dragged out of the back
        for (int i = 0; i < 5; i++) {
            p.setIfAir(1 + rng.nextInt(w - 2), y + 1, z0 + 1 + rng.nextInt(zs - z0 - 1), rng.nextDouble() < 0.6 ? "paper" : "blood_splat");
        }
        p.setIfAir(cx + rng.nextInt(3) - 1, y + 1, zs - 1, "cv_mail_sack[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
        b.mess(1, z0 + 1, x1 - 1, zs - 1, y + 1, 0.04);
    }

    /**
     * The screen line across the building: bronze box doors three high either side, the counter
     * between them under its brass grilles (a door at one end for the clerks), panelling over all
     * of it and the clock above the counter.
     */
    private void screen(int w, int zs, int cx) {
        int x1 = w - 1, y = F, bw = (w - 2 - 6) / 2;
        for (int x = 1; x < x1; x++) {
            boolean boxes = x <= bw || x >= x1 - bw;
            for (int yy = y + 1; yy < top; yy++) {
                String s;
                if (yy <= y + 3 && boxes) {
                    s = (rng.nextDouble() < 0.15 + b.damage * 0.3 ? "cv_po_boxes_b" : "cv_po_boxes") + "[facing=north]";
                } else if (!boxes && yy == y + 1) {
                    s = "cv_po_counter[facing=north]";
                } else if (!boxes && yy == y + 2) {
                    s = rng.nextDouble() < b.damage * 0.25 ? "air" : "cv_po_grille[facing=north]";
                } else {
                    s = yy == y + 3 || yy == y + 4 && boxes ? "cv_panel_oak" : "cv_plaster";
                }
                p.set(x, yy, zs, s);
            }
        }
        int dx = bw + 1;
        Doors.abandoned(p, rng, dx, y + 1, zs, "home_door_oak", "south", Doors.hinge(rng));
        p.set(dx, y + 3, zs, "cv_panel_oak");
        p.set(dx, y + 4, zs - 1, "cv_plaque_office[facing=north]");
        p.set(cx, y + 4, zs - 1, "bunk_clock[facing=north]");
        // the clerks' side: stools, the scales' table, a hamper pushed up behind the windows
        for (int x = bw + 2; x < x1 - bw; x += 2) {
            p.set(x, y + 1, zs + 1, rng.nextDouble() < 0.3 ? "store_stool" : "pk_stool_1[facing=north]");
        }
    }

    /**
     * The workroom: sorting cases round the walls and a double row down the middle, the hampers
     * and sacks, tubes overhead, the floor deep in undelivered mail.
     */
    private void workroom(int w, int zs, int zo, int z1, int cx) {
        int x1 = w - 1, y = F;
        for (int z = zs + 2; z < zo - 1; z++) {
            p.set(1, y + 1, z, "cv_sorting_case[facing=east]");
            p.set(x1 - 1, y + 1, z, "cv_sorting_case[facing=west]");
        }
        int iz = zs + 2;
        if (iz + 1 < zo - 1) {
            for (int x = cx - 2; x <= cx + 1; x++) {
                p.set(x, y + 1, iz, "cv_sorting_case[facing=north]");
                p.set(x, y + 1, iz + 1, "cv_sorting_case[facing=south]");
            }
        }
        for (int i = 0; i < 5; i++) {
            int x = 2 + rng.nextInt(w - 4), z = zs + 2 + rng.nextInt(Math.max(1, zo - zs - 2));
            if (p.get(x, y + 1, z) == null) {
                p.set(x, y + 1, z, rng.nextDouble() < 0.6 ? "cv_mail_hamper" : "cv_mail_sack[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
            }
        }
        // parcel racks against the postmaster's wall, the carriers' lockers and the coffee bench by the restroom
        int rx = x1 - 5;
        if (zo - 1 > zs + 2) {
            for (int x : new int[] {1, 2}) {
                p.set(x, y + 1, zo - 1, "pk_metal_rack[facing=north]");
                p.set(x, y + 2, zo - 1, rng.nextDouble() < 0.6 ? "cardboard_box" : "air");
            }
            p.set(4, y + 2, zo - 1, "home_calendar[facing=north]");
            for (int x = x1 - 2; x < x1; x++) {
                boolean open = rng.nextDouble() < 0.4;
                p.set(x, y + 1, zo - 1, "fit_locker_lo[facing=north]");
                p.set(x, y + 2, zo - 1, "fit_locker_hi[facing=north]");
                if (open) {
                    p.setIfAir(x, y + 1, zo - 2, "paper");
                }
            }
            p.set(rx + 1, y + 1, zo - 1, "home_workbench[facing=north]");
            p.set(rx + 1, y + 2, zo - 1, "home_coffee_maker[facing=north]");
        }
        for (int i = 0; i < 4; i++) {
            int x = 2 + rng.nextInt(w - 4), z = zs + 2 + rng.nextInt(Math.max(1, zo - zs - 2));
            if (p.get(x, y + 1, z) == null) {
                p.set(x, y + 1, z, i < 2 ? "cardboard_box" : "store_stool");
            }
        }
        for (int x = 2; x < x1 - 1; x++) {
            for (int z = zs + 1; z < z1; z++) {
                if (rng.nextDouble() < 0.12) {
                    p.setIfAir(x, y + 1, z, "paper");
                }
            }
        }
        for (int x = 3; x < x1 - 1; x += 4) {
            for (int z = zs + 2; z < zo; z += 3) {
                p.set(x, top - 1, z, (rng.nextDouble() < 0.08 ? "fit_tube_on" : rng.nextDouble() < 0.15 ? "fit_tube_flicker" : "fit_tube") + "[axis=z]");
            }
        }
        b.mess(2, zs + 1, x1 - 2, z1 - 1, y + 1, 0.04);
    }

    /** The postmaster's office in one back corner (desk, safe, files, the flag), the restroom in the other. */
    private void backRooms(int w, int zo, int z1, int cx) {
        int x1 = w - 1, y = F, ox = 5, rx = x1 - 5;
        for (int yy = y + 1; yy < top; yy++) {
            for (int x = 1; x <= ox; x++) {
                p.set(x, yy, zo, "cv_plaster");
            }
            for (int x = rx; x < x1; x++) {
                p.set(x, yy, zo, "cv_plaster");
            }
            for (int z = zo + 1; z < z1; z++) {
                p.set(ox, yy, z, "cv_plaster");
                p.set(rx, yy, z, "cv_plaster");
            }
        }
        Doors.abandoned(p, rng, 3, y + 1, zo, "home_door_oak", "south", Doors.hinge(rng));
        p.set(3, y + 3, zo - 1, "cv_plaque_postmaster[facing=north]");
        for (int x = 1; x < ox; x++) {
            for (int z = zo + 1; z < z1; z++) {
                p.set(x, y, z, "home_floor_carpet_green");
            }
        }
        p.set(3, y + 1, z1 - 2, "home_desk[facing=north]");
        p.set(3, y + 1, z1 - 1, rng.nextDouble() < 0.3 ? "office_chair_fallen[facing=west]" : "office_chair[facing=north]");
        p.set(1, y + 1, zo + 1, "cellar_safe[facing=east]");
        p.set(1, y + 2, zo + 1, "cellar_safe_top[facing=east]");
        p.set(ox - 1, y + 1, zo + 1, "pk_file_tall[facing=west]");
        p.set(ox - 1, y + 2, zo + 1, "pk_file_tall_010[facing=west]");
        p.set(1, y + 1, z1 - 1, "cv_flag_us[facing=east]");
        p.set(1, y + 2, z1 - 1, "cv_flag_us_top[facing=east]");
        p.set(2, top - 1, zo + 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        // the restroom
        Doors.abandoned(p, rng, x1 - 3, y + 1, zo, "home_door_oak", "south", Doors.hinge(rng));
        p.set(x1 - 3, y + 3, zo - 1, "cv_plaque_restroom[facing=north]");
        for (int x = rx + 1; x < x1; x++) {
            for (int z = zo + 1; z < z1; z++) {
                p.set(x, y, z, "cv_hex_tile");
            }
        }
        p.set(x1 - 1, y + 1, z1 - 1, "fit_toilet_stall[facing=west]");
        p.set(rx + 1, y + 1, z1 - 1, "home_sink_white[facing=east]");
        p.set(x1 - 2, top - 1, zo + 2, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
    }

    /** The loading dock at the back: the roll-up door, a platform at the floor's height, the canopy over it. */
    private void dock(int w, int z1, int cx) {
        for (int x = cx - 1; x <= cx; x++) {
            p.set(x, F + 1, z1, "garage_door[facing=south]");
            p.set(x, F + 2, z1, rng.nextDouble() < b.damage * 0.5 ? "air" : "garage_door[facing=south]");
            p.set(x, F + 3, z1, "stone_trim");
        }
        for (int x = cx - 3; x <= cx + 2; x++) {
            for (int z = z1 + 1; z <= z1 + 2; z++) {
                for (int y = 0; y <= F; y++) {
                    p.set(x, y, z, y == F ? b.wall("floor_concrete", 3) : "floor_concrete");
                }
                p.set(x, F + 4, z, "front_canopy_metal[facing=south]");
            }
        }
        p.set(cx - 3, F + 1, z1 + 2, "cv_mail_hamper");
        p.set(cx + 2, F + 1, z1 + 1, rng.nextBoolean() ? "cv_mail_sack[facing=south]" : "pk_crate_1");
        p.set(cx - 2, F + 3, z1 + 1, "ext_sconce[facing=south]");
    }
}
