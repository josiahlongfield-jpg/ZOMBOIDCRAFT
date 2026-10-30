package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;

/**
 * Ravenrock's cellhouse (refs/prison/cellblock-tiers, cell-interior, solitary/04): the Center under
 * its lantern with the gun gallery caged in the middle, and a wing either side of it, A and B
 * blocks west, C and D east. In each wing two rows of cells back to back down the middle, three
 * tiers high, the pipe chase between their back walls; the flats and the void round them up to
 * the roof, the tiers' walkways along the fronts with their mesh rails, the stairs at the far end
 * and the bridges across it; the tall barred windows down both long walls; at the Center's end the
 * barred grille that shuts a wing off.
 *
 * <p>When it went the officers ran the gang locks open on the flats and left; the tiers above
 * stayed locked. Some cells are as their men left them, some were turned over, some were
 * barricaded from inside and held. Drawn in the site's frame: the Center x -15 .. 15, z -116 ..
 * -85; each wing in its own frame, x 0 at the Center's wall going out, z 0 its north wall.
 */
final class RavenrockCells {
    /** A wing: its length out from the Center, its depth; the cells from C0, three apiece. */
    static final int WL = 80, WD = 24, C0 = 6, CELLS = 20;
    /** The tiers' floors. */
    static final int[] TIERS = {0, 4, 8};
    static final int ROOF = 13;
    /** The Center. */
    static final int HX0 = -15, HX1 = 15, HZ0 = -116, HZ1 = -85;

    private final Grounds g;
    private Placer q;

    RavenrockCells(Grounds g) {
        this.g = g;
    }

    private void set(int x, int y, int z, String b) {
        q.set(x, y, z, b);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    q.set(x, y, z, b);
                }
            }
        }
    }

    private double chance() {
        return g.chance();
    }

    void build() {
        // west: x 0 at -16 going west, z 0 at -89 going north; east: x 0 at 16 going east, z 0 at -112
        q = g.p.turned(-16, 0, -89, 2);
        wing("a", "b");
        q = g.p.turned(16, 0, -112, 0);
        wing("c", "d");
        q = g.p;
        center();
    }

    // ------------------------------------------------------------------ a wing

    private String ext() {
        double r = chance();
        return r < 0.12 ? "pn_ext_stain" : r < 0.2 ? "pn_ext_moss" : "pn_ext";
    }

    private void wing(String north, String south) {
        // the shell: two thick, whitewash outside, the paint inside over its dado
        for (int x = 0; x < WL; x++) {
            for (int z = 0; z < WD; z++) {
                set(x, 0, z, z == 3 || z == 20 ? "pn_floor_line" : "pn_floor");
                boolean outer = z == 0 || z == WD - 1 || x == WL - 1;
                boolean inner = z == 1 || z == WD - 2 || x == WL - 2;
                if (!outer && !inner) {
                    continue;
                }
                for (int y = 1; y <= ROOF + 1; y++) {
                    set(x, y, z, outer ? (y == ROOF + 1 ? "bd_coping" : ext()) : y == 1 ? "pn_dado" : "pn_paint");
                }
            }
        }
        // the tall windows down both sides, every four, and in the far end
        for (int x = 4; x < WL - 4; x += 4) {
            for (int y = 2; y <= 10; y++) {
                boolean broken = chance() < 0.12 + g.b.damage * 0.2;
                set(x, y, 0, "pn_window" + (broken ? "_broken" : "") + "[facing=north]");
                set(x, y, 1, "air");
                set(x, y, WD - 1, "pn_window" + (chance() < 0.12 + g.b.damage * 0.2 ? "_broken" : "") + "[facing=south]");
                set(x, y, WD - 2, "air");
            }
        }
        for (int z : new int[] {3, 20}) {
            for (int y = 2; y <= 10; y++) {
                set(WL - 1, y, z, "pn_window[facing=east]");
                set(WL - 2, y, z, "air");
            }
        }
        // the roof: tar over the slab, skylights over the void each side, the parapet
        for (int x = 0; x < WL - 2; x++) {
            for (int z = 2; z < WD - 2; z++) {
                boolean sky = (z == 2 || z == 3 || z == 20 || z == 21) && x % 6 >= 2 && x % 6 <= 3;
                set(x, ROOF, z, sky ? (chance() < 0.15 ? "us_skylight_broken" : "us_skylight") : "un_roof");
            }
        }
        for (int x = 3; x < WL - 3; x += 12) {
            set(x, ROOF + 1, 11, "bunk_vent_stack");
            set(x, ROOF + 2, 11, "bunk_vent_cap");
        }
        // the plant on the roof: air handlers over the tiers, the hatch from the stair, drains, vents
        for (int x = 9; x < WL - 8; x += 18) {
            set(x, ROOF + 1, 8, "bd_hvac_a[facing=north]");
            set(x + 1, ROOF + 1, 8, "bd_hvac_b[facing=north]");
            set(x, ROOF + 1, 15, "bd_hvac_a[facing=south]");
            set(x - 1, ROOF + 1, 15, "bd_hvac_b[facing=south]");
        }
        set(WL - 5, ROOF + 1, 12, "bd_hatch[facing=west]");
        for (int x = 6; x < WL - 3; x += 14) {
            set(x, ROOF + 1, 5, "bd_drain");
            set(x + 7, ROOF + 1, 18, "bd_vent");
        }
        block(north, south);
        stairs();
        grille();
        lights();
    }

    /** The block of cells down the middle, the chase between, the floors of the tiers. */
    private void block(String north, String south) {
        int x0 = C0, x1 = C0 + CELLS * 3;
        for (int t = 0; t < TIERS.length; t++) {
            int y = TIERS[t];
            // the tier's floor and its walkways (the flats are the ground)
            if (y > 0) {
                fill(x0, y, 6, x1 + 3, y, 17, "pn_floor");
                fill(x0, y, 4, WL - 3, y, 5, "pn_catwalk");
                fill(x0, y, 18, WL - 3, y, 19, "pn_catwalk");
                for (int x = x0; x <= WL - 3; x++) {
                    // not across the first tier's landing, nor the second's at the top of the stairs
                    if (y == 4 && x >= 74) {
                        continue;
                    }
                    set(x, y + 1, 4, (x == x0 ? "pn_rail_end" : "pn_rail") + "[facing=north]");
                    if (!(y == 8 && x >= 66 && x <= 69)) {
                        set(x, y + 1, 19, (x == x0 ? "pn_rail_end" : "pn_rail") + "[facing=south]");
                    }
                }
            }
            for (int k = 0; k < CELLS; k++) {
                int cx = x0 + k * 3;
                // the divider, the back walls, the chase
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    for (int z = 6; z <= 17; z++) {
                        set(cx, yy, z, z == 11 || z == 12 ? "pn_paint" : yy == y + 1 ? "pn_dado" : "pn_paint");
                    }
                    set(cx + 1, yy, 10, "pn_paint");
                    set(cx + 2, yy, 10, "pn_paint");
                    set(cx + 1, yy, 13, "pn_paint");
                    set(cx + 2, yy, 13, "pn_paint");
                    set(cx + 1, yy, 11, yy == y + 2 ? "bunk_pipes" : "air");
                    set(cx + 2, yy, 12, yy == y + 2 ? "bunk_pipes" : "air");
                }
                cell(cx + 1, y, false, t, k, north);
                cell(cx + 1, y, true, t, k, south);
            }
            // the block's end toward the stairs: solid, the gang locks on it
            fill(x1, y + 1, 6, x1 + 3, y + 3, 17, "pn_paint");
            set(x1 + 1, y + 2, 5, "pn_lockbox[facing=north]");
            set(x1 + 1, y + 2, 18, "pn_lockbox[facing=south]");
            if (t == 0) {
                sign("line", x1 + 2, y + 2, 5, "north");
            }
        }
        // the blocks' signs on the end facing the Center
        sign(north, 1, 3, 7, "west");
        sign(south, 1, 3, 15, "west");
        // the roof of the block, the chase's top
        fill(x0, ROOF - 1, 6, x1 + 3, ROOF - 1, 17, "pn_paint");
    }

    /**
     * A cell two wide and three deep at x (its west column) on the tier at y, its front on the
     * north (z = 6) or the south (z = 17): the barred front with its door, the bunks against the
     * west wall, the toilet and basin at the back, the desk, the shelf; then how it was left.
     */
    private void cell(int x, int y, boolean south, int tier, int k, String block) {
        int front = south ? 17 : 6, back = south ? 14 : 9, mid = south ? 15 : 8, near = south ? 16 : 7;
        String out = south ? "south" : "north", in = south ? "north" : "south";
        // the front: the door at x, bars at x + 1 and over the door
        set(x + 1, y + 1, front, "pn_bars[axis=x]");
        set(x + 1, y + 2, front, "pn_bars[axis=x]");
        set(x + 1, y + 3, front, "pn_bars[axis=x]");
        set(x, y + 3, front, "pn_bars[axis=x]");
        // the flats were thrown open; the tiers stayed locked, a few forced
        boolean open = tier == 0 ? chance() < 0.75 : chance() < 0.2;
        Doors.place(q, x, y + 1, front, "pn_cell_door", in, "left", open);
        // inside
        boolean upper = chance() < 0.6;
        set(x, y + 1, back, "pn_bunk[facing=east]");
        set(x, y + 1, mid, "pn_bunk[facing=east]");
        if (upper) {
            set(x, y + 2, back, "pn_bunk_upper[facing=east]");
            set(x, y + 2, mid, "pn_bunk_upper[facing=east]");
        }
        set(x + 1, y + 1, back, "pn_toilet_sink[facing=" + out + "]");
        set(x + 1, y + 1, mid, "pn_cell_desk[facing=west]");
        set(x, y + 3, back, "pn_shelf[facing=east]");
        double r = chance();
        if (r < 0.18) {
            // turned over: the mattress on the floor, paper everywhere, blood
            set(x, y + 1, back, "air");
            set(x, y + 1, mid, "home_mattress_floor[facing=" + g.dir() + "]");
            set(x + 1, y + 1, near, chance() < 0.5 ? "paper" : "blood_splat");
            set(x, y + 1, near, chance() < 0.4 ? "home_blood_pool" : "paper");
        } else if (r < 0.26 && tier > 0) {
            // held: the mattresses up against the bars, the door tied shut
            set(x, y + 1, near, "home_mattress_floor[facing=" + out + "]");
            set(x + 1, y + 1, near, "pn_cart[facing=" + out + "]");
            set(x + 1, y + 2, near, "cardboard_box");
        } else if (r < 0.32) {
            set(x, y + 1, near, "home_corpse_head[facing=" + out + "]");
        } else if (r < 0.5) {
            set(x, y + 1, near, any("paper", "home_books_stack_low", "trash_bag[facing=north]"));
        }
    }

    /** A sign two wide on the wall behind (x, z), read facing. */
    private void sign(String key, int x, int y, int z, String facing) {
        for (int c = 0; c < 2; c++) {
            int[] a = MallSigns.at(x, z, 2, c, facing);
            set(a[0], y, a[1], "pn_sgn_" + key + "_" + c + "[facing=" + facing + "]");
        }
    }

    private String any(String... options) {
        return g.any(options);
    }

    /** At the far end: up the north void to the first tier, the bridge across, up the south void. */
    private void stairs() {
        int e = WL - 3;
        for (int i = 0; i < 4; i++) {
            for (int z = 2; z <= 3; z++) {
                set(70 + i, 1 + i, z, "steel_stairs[facing=east]");
                set(73 - i, 5 + i, 20 + z - 2, "steel_stairs[facing=west]");
            }
        }
        // the first tier's landing at the end, the bridge round the block's end
        fill(74, 4, 2, e, 4, 21, "pn_catwalk");
        fill(66, 8, 20, 69, 8, 21, "pn_catwalk");
        fill(74, 8, 4, e, 8, 19, "pn_catwalk");
        // the rails round the well between the block's end and the bridges
        for (int z = 6; z <= 17; z++) {
            set(74, 5, z, "pn_rail[facing=west]");
            set(74, 9, z, "pn_rail[facing=west]");
        }
    }

    /** At the Center's end: the barred grille across each void, its door, the sign. */
    private void grille() {
        for (int z = 2; z <= WD - 3; z++) {
            if (z >= 6 && z <= 17) {
                continue;
            }
            for (int y = 1; y <= ROOF - 1; y++) {
                set(2, y, z, "pn_bars[axis=z]");
            }
        }
        boolean open = chance() < 0.6;
        Doors.place(q, 2, 1, 3, "pn_cell_door", "west", "left", open);
        Doors.place(q, 2, 1, 20, "pn_cell_door", "west", "right", chance() < 0.6);
        fill(2, 1, 6, 5, ROOF - 1, 17, "pn_paint");
        fill(2, 1, 6, 5, 1, 17, "pn_dado");
        sign("hostage", 3, 3, 5, "north");
    }

    /** The lamps hung over the flats, a few still burning off the generator. */
    private void lights() {
        for (int x = 6; x < WL - 4; x += 6) {
            for (int z : new int[] {2, 21}) {
                set(x, ROOF - 1, z, g.power && chance() < 0.4 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
            }
        }
        // the flats: what was dropped running
        for (int x = 3; x < WL - 3; x++) {
            for (int z : new int[] {2, 3, 20, 21}) {
                if (chance() < 0.04) {
                    set(x, 1, z, any("paper", "paper", "blood_splat", "home_mattress_floor[facing=east]", "trash_bag[facing=north]"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the Center

    /**
     * The Center: a hall two storeys high where the wings meet, under a lantern of barred lights;
     * the gun gallery in the middle up on its steel legs, caged, its ladder; the doors to the
     * wings, the corridor south to the dining hall, the count board, the bell.
     */
    private void center() {
        int x0 = HX0, x1 = HX1, z0 = HZ0, z1 = -85, top = 16;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, Math.abs(x) == 6 || Math.abs(z + 100) == 6 ? "pn_floor_line" : "pn_floor");
                boolean outer = x == x0 || x == x1 || z == z0 || z == z1;
                boolean inner = x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1;
                for (int y = 1; y <= top + 1; y++) {
                    if (outer) {
                        set(x, y, z, y == top + 1 ? "bd_coping" : ext());
                    } else if (inner) {
                        set(x, y, z, y == 1 ? "pn_dado" : "pn_paint");
                    } else if (y == top) {
                        set(x, y, z, Math.abs(x) <= 4 && Math.abs(z + 100) <= 4 ? "air" : "un_roof");
                    }
                }
            }
        }
        // the openings into the wings (their grilles shut them), the corridor south
        for (int z = -110; z <= -91; z++) {
            for (int y = 1; y <= ROOF - 1; y++) {
                for (int x : new int[] {x0, x0 + 1, x1 - 1, x1}) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, z1, "air");
                set(x, y, z1 - 1, "air");
            }
        }
        // windows high in the north wall, the lantern over the middle
        for (int x = -11; x <= 11; x += 4) {
            for (int y = 9; y <= 13; y++) {
                set(x, y, z0, "pn_window[facing=north]");
                set(x, y, z0 + 1, "air");
            }
        }
        for (int x = -5; x <= 5; x++) {
            for (int z = -105; z <= -95; z++) {
                boolean edge = Math.abs(x) == 5 || Math.abs(z + 100) == 5;
                if (edge) {
                    set(x, top, z, "pn_ext");
                    for (int y = top + 1; y <= top + 3; y++) {
                        set(x, y, z, (Math.abs(x) == 5 && Math.abs(z + 100) == 5) ? "pn_ext"
                                : "pn_window[facing=" + (z == -105 ? "north" : z == -95 ? "south" : x < 0 ? "west" : "east") + "]");
                    }
                }
                set(x, top + 4, z, edge ? "bd_coping" : "un_roof");
            }
        }
        // the gun gallery: a cage five square up on four legs, the ladder, the rifle rack in it
        int gy = 5;
        for (int x = -2; x <= 2; x++) {
            for (int z = -102; z <= -98; z++) {
                set(x, gy, z, "pn_catwalk");
                boolean edge = Math.abs(x) == 2 || Math.abs(z + 100) == 2;
                if (edge) {
                    for (int y = gy + 1; y <= gy + 3; y++) {
                        set(x, y, z, "pn_bars[axis=" + (Math.abs(x) == 2 ? "z" : "x") + "]");
                    }
                }
                set(x, gy + 4, z, "pn_catwalk");
            }
        }
        for (int[] c : new int[][] {{-2, -102}, {2, -102}, {-2, -98}, {2, -98}}) {
            for (int y = 1; y < gy; y++) {
                set(c[0], y, c[1], "pn_post");
            }
        }
        for (int y = 1; y <= gy; y++) {
            set(0, y, -97, "bunk_ladder[facing=south]");
        }
        set(0, gy + 1, -98, "air");
        set(0, gy + 2, -98, "air");
        set(-1, gy + 1, -101, "sh_gun_rack[facing=south]");
        set(1, gy + 1, -101, "bunk_crate_ammo[facing=south]");
        set(1, gy + 3, -99, "bunk_lamp_hang");
        // the count board and the desk under it, the bell on the wall
        set(-13, 3, -113, "pn_panel[facing=south]");
        set(-11, 1, -113, "desk[facing=south]");
        set(-11, 1, -112, "office_chair[facing=north]");
        set(-9, 1, -113, "filing_cabinet[facing=south]");
        set(12, 3, -113, "sh_phone[facing=south]");
        set(9, 4, -114, "pn_spray_count_0[facing=south]");
        set(10, 4, -114, "pn_spray_count_1[facing=south]");
        set(11, 4, -114, "pn_spray_count_2[facing=south]");
        for (int x = -12; x <= 12; x += 6) {
            set(x, top - 1, -108, g.power && chance() < 0.5 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
            set(x, top - 1, -92, g.power && chance() < 0.5 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
        }
        g.mess(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 0, 0.03);
        g.row("pn_spray_left_", 4, -13, 2, -87, "north", 0);
    }
}
