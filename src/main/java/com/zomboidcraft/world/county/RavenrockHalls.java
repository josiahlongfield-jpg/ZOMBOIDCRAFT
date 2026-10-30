package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;

/**
 * Ravenrock's other buildings inside the wall, drawn in the site's frame (refs/prison): the dining
 * hall south of the Center with its kitchen, the corridor to it; the industries building, the
 * laundry and the plate shop; the infirmary, two floors, its upstairs ward the last place held;
 * segregation, the hole, its solid doors and the cages it exercised in; and in the west wall the
 * gatehouse, the visitors' lobby and the visiting room, the armoury, the control room over the
 * sally port, and the sally port itself, two gates and the trap between them.
 */
final class RavenrockHalls {
    private final Grounds g;

    RavenrockHalls(Grounds g) {
        this.g = g;
    }

    private void set(int x, int y, int z, String b) {
        g.set(x, y, z, b);
    }

    private void fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        g.fill(x0, y0, z0, x1, y1, z1, b);
    }

    private double chance() {
        return g.chance();
    }

    void build() {
        dining();
        industries();
        infirmary();
        segregation();
        gatehouse();
    }

    // ------------------------------------------------------------------ shells

    private String ext() {
        double r = chance();
        return r < 0.12 ? "pn_ext_stain" : r < 0.2 ? "pn_ext_moss" : "pn_ext";
    }

    /**
     * A building's shell x0..x1 by z0..z1: the floor, walls two thick (whitewash out, paint over
     * the dado in), the roof's slab at top, its coping; storeys' slabs at each of floors.
     */
    private void shell(int x0, int z0, int x1, int z1, int top, int... floors) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, "pn_floor");
                boolean outer = x == x0 || x == x1 || z == z0 || z == z1;
                boolean inner = !outer && (x == x0 + 1 || x == x1 - 1 || z == z0 + 1 || z == z1 - 1);
                for (int y = 1; y <= top + 1; y++) {
                    if (outer) {
                        set(x, y, z, y == top + 1 ? "bd_coping" : ext());
                    } else if (inner && y < top) {
                        boolean dado = y == 1;
                        for (int f : floors) {
                            dado |= y == f + 1;
                        }
                        set(x, y, z, dado ? "pn_dado" : "pn_paint");
                    } else if (y == top) {
                        set(x, y, z, "un_roof");
                    } else if (!inner) {
                        set(x, y, z, "air");
                    }
                }
                for (int f : floors) {
                    if (!outer && !inner) {
                        set(x, f, z, "pn_floor");
                    } else if (inner) {
                        set(x, f, z, "pn_paint");
                    }
                }
            }
        }
        // on the roof: the air handlers, a hatch, the vents and drains, spaced over the deck
        int w = x1 - x0, d = z1 - z0;
        for (int x = x0 + 5; x < x1 - 5; x += 14) {
            for (int z = z0 + 5; z < z1 - 4; z += 12) {
                double r = chance();
                if (r < 0.45) {
                    set(x, top + 1, z, "bd_hvac_a[facing=north]");
                    set(x + 1, top + 1, z, "bd_hvac_b[facing=north]");
                } else if (r < 0.75) {
                    set(x, top + 1, z, "bd_vent");
                } else {
                    set(x, top + 1, z, "bd_drain");
                }
            }
        }
        if (w > 8 && d > 8) {
            set(x0 + 3, top + 1, z0 + 3, "bd_hatch[facing=south]");
        }
    }

    /** Barred windows in both layers of a wall along x at z (the outer layer at z, facing out). */
    private void windowsX(int x0, int x1, int z, int dz, int y0, int y1, int step, String facing) {
        for (int x = x0; x <= x1; x += step) {
            for (int y = y0; y <= y1; y++) {
                set(x, y, z, (chance() < 0.12 + g.b.damage * 0.25 ? "pn_window_broken" : "pn_window") + "[facing=" + facing + "]");
                set(x, y, z + dz, "air");
            }
        }
    }

    private void windowsZ(int x, int dx, int z0, int z1, int y0, int y1, int step, String facing) {
        for (int z = z0; z <= z1; z += step) {
            for (int y = y0; y <= y1; y++) {
                set(x, y, z, (chance() < 0.12 + g.b.damage * 0.25 ? "pn_window_broken" : "pn_window") + "[facing=" + facing + "]");
                set(x + dx, y, z, "air");
            }
        }
    }

    /** A doorway through both layers of a wall, the door in the outer one. */
    private void door(int x, int y, int z, int dx, int dz, String type, String facing) {
        set(x + dx, y + 1, z + dz, "air");
        set(x + dx, y + 2, z + dz, "air");
        Doors.abandoned(g.p, g.rng, x, y + 1, z, type, facing, Doors.hinge(g.rng));
    }

    /** A two-wide sign on the wall behind (x, z), read facing. */
    private void sign(String key, int x, int y, int z, String facing) {
        for (int c = 0; c < 2; c++) {
            int[] a = MallSigns.at(x, z, 2, c, facing);
            set(a[0], y, a[1], "pn_sgn_" + key + "_" + c + "[facing=" + facing + "]");
        }
    }

    private void lamps(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x += 5) {
            for (int z = z0; z <= z1; z += 5) {
                set(x, y, z, g.power && chance() < 0.35 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
            }
        }
    }

    // ------------------------------------------------------------------ the dining hall

    /**
     * The dining hall, x -25 .. 25, z -70 .. -40, reached down the corridor from the Center: the
     * steel tables in their rows, the serving line across the east end, the kitchen behind it.
     */
    private void dining() {
        // the corridor from the Center's south door
        for (int z = -84; z <= -71; z++) {
            for (int x = -4; x <= 4; x++) {
                set(x, 0, z, "pn_floor");
                for (int y = 1; y <= 6; y++) {
                    boolean wall = Math.abs(x) >= 3;
                    set(x, y, z, y == 6 ? (Math.abs(x) == 4 ? "bd_coping" : "air") : y == 5 ? "un_roof"
                            : wall ? (Math.abs(x) == 4 ? ext() : y == 1 ? "pn_dado" : "pn_paint") : "air");
                }
            }
            if (z % 4 == 0) {
                set(-4, 3, z, "pn_window[facing=west]");
                set(-3, 3, z, "air");
                set(4, 3, z, "pn_window[facing=east]");
                set(3, 3, z, "air");
            }
        }
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, -78, x == 0 && y <= 2 ? "air" : "pn_bars[axis=x]");
            }
        }
        Doors.place(g.p, 0, 1, -78, "pn_cell_door", "south", "left", chance() < 0.7);
        int x0 = -25, x1 = 25, z0 = -70, z1 = -40, top = 7;
        shell(x0, z0, x1, z1, top);
        windowsX(x0 + 3, x1 - 3, z0, 1, 2, 5, 4, "north");
        windowsX(x0 + 3, x1 - 3, z1, -1, 2, 5, 4, "south");
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, z0, "air");
                set(x, y, z0 + 1, "air");
            }
        }
        sign("mess", -1, 5, z0 - 1, "north");
        // the tables
        for (int x = x0 + 4; x <= 9; x += 3) {
            for (int z = z0 + 4; z <= z1 - 4; z += 3) {
                if (Math.abs(x) <= 1) {
                    continue;
                }
                set(x, 1, z, "pn_mess_table");
                if (chance() < 0.12) {
                    set(x + 1, 1, z + 1, any("sc_trays", "paper", "blood_splat"));
                }
            }
        }
        // the serving line and the kitchen behind it
        for (int z = z0 + 3; z <= z1 - 3; z++) {
            if (z == -55 || z == -54) {
                continue;
            }
            set(12, 1, z, (z % 3 == 0 ? "cc_serving" : "fit_serving") + "[facing=west]");
        }
        sign("line", 10, 3, -56, "west");
        int k = 0;
        String[] cook = {"dn_griddle", "dn_fryer", "fit_oven", "store_oven", "dn_griddle", "fit_kettle", "fit_kettle", "dn_sink", "dn_sink"};
        for (int z = z0 + 3; z <= z1 - 3; z += 2) {
            set(x1 - 2, 1, z, cook[k++ % cook.length] + "[facing=west]");
            if (k % 3 == 0) {
                set(x1 - 2, 3, z, "dn_hood[facing=west]");
            }
        }
        for (int z = z0 + 6; z <= z1 - 6; z++) {
            set(17, 1, z, (z % 2 == 0 ? "dn_prep" : "dn_prep_bare") + "[facing=east]");
            set(18, 1, z, (z % 2 == 0 ? "dn_prep_bare" : "dn_prep") + "[facing=west]");
        }
        set(x1 - 3, 1, z0 + 2, "dn_walkin[facing=south]");
        set(x1 - 3, 2, z0 + 2, "dn_walkin[facing=south]");
        set(14, 1, z1 - 2, "sc_trays[facing=north]");
        set(15, 1, z1 - 2, "sc_trays[facing=north]");
        door(x1, 0, -48, -1, 0, "fire_door", "east");
        lamps(x0 + 4, z0 + 4, x1 - 4, z1 - 4, top - 1);
        g.mess(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 0, 0.03);
    }

    private String any(String... o) {
        return g.any(o);
    }

    // ------------------------------------------------------------------ industries

    /** The industries building, x -150 .. -100, z -60 .. -30: the laundry west, the plate shop east. */
    private void industries() {
        int x0 = -150, x1 = -100, z0 = -60, z1 = -30, top = 7;
        shell(x0, z0, x1, z1, top);
        windowsX(x0 + 3, x1 - 3, z0, 1, 2, 5, 4, "north");
        windowsX(x0 + 3, x1 - 3, z1, -1, 2, 5, 4, "south");
        // the partition between them, its door
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            for (int y = 1; y < top; y++) {
                set(-126, y, z, y == 1 ? "pn_dado" : "pn_paint");
            }
        }
        set(-126, 1, -45, "air");
        set(-126, 2, -45, "air");
        Doors.abandoned(g.p, g.rng, -126, 1, -45, "fire_door", "east", "left");
        door(x0, 0, -45, 1, 0, "fire_door", "west");
        door(x1, 0, -45, -1, 0, "fire_door", "east");
        sign("laundry", x0 - 1, 4, -46, "west");
        sign("shop", x1 + 1, 4, -45, "east");
        // the laundry: the machines along both walls, the folding tables, the carts
        for (int x = x0 + 3; x <= -128; x += 2) {
            set(x, 1, z0 + 2, "store_washer[facing=south]");
            set(x, 1, z1 - 2, "store_dryer[facing=north]");
            if (chance() < 0.5) {
                set(x, 2, z1 - 2, "store_dryer[facing=north]");
            }
        }
        for (int x = x0 + 5; x <= -130; x += 5) {
            set(x, 1, -46, "store_table");
            set(x + 1, 1, -46, "store_table");
            set(x, 2, -46, chance() < 0.6 ? "home_clothes_pile" : "air");
            set(x + 2, 1, -42, "pn_cart[facing=" + g.dir() + "]");
        }
        // the plate shop: the presses in a row, the benches with the plates, the racks
        for (int x = -122; x <= x1 - 4; x += 4) {
            set(x, 1, z0 + 4, "pn_plate_press[facing=south]");
            set(x, 1, z0 + 7, "home_workbench[facing=north]");
            set(x + 1, 1, z0 + 7, "home_workbench[facing=north]");
            set(x, 2, z0 + 7, "pn_plates[axis=x]");
            set(x, 1, z1 - 3, "store_shelf_hardware[facing=north]");
            if (chance() < 0.5) {
                set(x + 2, 1, -45, "pn_plates[axis=z]");
            }
        }
        lamps(x0 + 4, z0 + 4, x1 - 4, z1 - 4, top - 1);
        g.mess(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 0, 0.03);
    }

    // ------------------------------------------------------------------ the infirmary

    /**
     * The infirmary, x 120 .. 160, z -60 .. -36, two floors: below, the exam rooms and the pharmacy
     * cage north of the corridor, the day ward south; above, the long ward, the beds pushed up
     * against the stair head where the last of them held out.
     */
    private void infirmary() {
        int x0 = 120, x1 = 160, z0 = -60, z1 = -36, up = 5, top = 10;
        shell(x0, z0, x1, z1, top, up);
        windowsX(x0 + 3, x1 - 3, z0, 1, 2, 3, 4, "north");
        windowsX(x0 + 3, x1 - 3, z1, -1, 2, 3, 4, "south");
        windowsX(x0 + 3, x1 - 3, z0, 1, up + 2, up + 3, 4, "north");
        windowsX(x0 + 3, x1 - 3, z1, -1, up + 2, up + 3, 4, "south");
        door(x0, 0, -48, 1, 0, "fire_door", "west");
        sign("infirm", x0 - 1, 4, -49, "west");
        // the corridor along z -49 .. -47; the rooms north of it, the ward south
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            for (int y = 1; y < up; y++) {
                set(x, y, -50, y == 1 ? "pn_dado" : "pn_paint");
                set(x, y, -46, y == 1 ? "pn_dado" : "pn_paint");
            }
        }
        for (int x = x0 + 8; x <= x1 - 2; x += 8) {
            for (int z = z0 + 2; z <= -51; z++) {
                for (int y = 1; y < up; y++) {
                    set(x, y, z, y == 1 ? "pn_dado" : "pn_paint");
                }
            }
            set(x - 4, 1, -50, "air");
            set(x - 4, 2, -50, "air");
            Doors.abandoned(g.p, g.rng, x - 4, 1, -50, "fire_door", "north", "left");
            // an exam room: the table, the cabinet, the lamp
            set(x - 4, 1, z0 + 4, "of_exam_table[facing=north]");
            set(x - 6, 1, z0 + 2, "filing_cabinet[facing=south]");
            set(x - 2, 1, z0 + 2, "bunk_sink[facing=south]");
        }
        // the pharmacy cage at the east end of the rooms
        for (int z = z0 + 2; z <= -51; z++) {
            for (int y = 1; y < up; y++) {
                set(x1 - 9, y, z, "pn_bars[axis=z]");
            }
        }
        for (int x = x1 - 8; x <= x1 - 2; x++) {
            set(x, 1, z0 + 2, "store_shelf_pharmacy[facing=south]");
        }
        // the day ward
        for (int x = x0 + 4; x <= x1 - 4; x += 3) {
            set(x, 1, -44, "hs_bed_head[facing=south]");
            set(x, 1, -43, (chance() < 0.25 ? "hs_bed_foot_blood" : "hs_bed_foot") + "[facing=south]");
            set(x, 1, z1 - 3, "hs_bed_foot[facing=north]");
            set(x, 1, z1 - 2, "hs_bed_head[facing=north]");
        }
        for (int x = x0 + 3; x <= x1 - 3; x += 9) {
            set(x, 1, -47, "air");
            set(x, 2, -46, "air");
            set(x, 1, -46, "air");
        }
        // the stair up at the west end, against the north side of the corridor
        for (int i = 0; i < up; i++) {
            set(x0 + 3 + i, 1 + i, -48, "stairs_concrete[facing=east]");
            set(x0 + 3 + i, 1 + i, -47, "stairs_concrete[facing=east]");
            set(x0 + 3 + i, up, -48, "air");
            set(x0 + 3 + i, up, -47, "air");
        }
        // upstairs: one long ward; the beds dragged across the stair head, the rest in rows
        for (int x = x0 + 10; x <= x1 - 4; x += 3) {
            for (int z : new int[] {z0 + 3, z1 - 3}) {
                boolean n = z < -48;
                set(x, up + 1, z, "hs_bed_head[facing=" + (n ? "south" : "north") + "]");
                set(x, up + 1, z + (n ? 1 : -1), (chance() < 0.3 ? "hs_bed_foot_body" : "hs_bed_foot") + "[facing=" + (n ? "south" : "north") + "]");
            }
        }
        for (int z = -50; z <= -45; z++) {
            set(x0 + 9, up + 1, z, z % 2 == 0 ? "hs_bed_head[facing=east]" : "home_mattress_floor[facing=east]");
            set(x0 + 9, up + 2, z, chance() < 0.5 ? "cardboard_box" : "air");
        }
        set(x0 + 12, up + 1, -48, "home_blood_pool");
        set(x0 + 14, up + 1, -47, "hs_monitor[facing=west]");
        lamps(x0 + 4, z0 + 4, x1 - 4, z1 - 4, up - 1);
        lamps(x0 + 4, z0 + 4, x1 - 4, z1 - 4, top - 1);
        g.mess(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 0, 0.03);
        g.mess(x0 + 2, z0 + 2, x1 - 2, z1 - 2, up, 0.04);
    }

    // ------------------------------------------------------------------ segregation

    /**
     * Segregation, x 110 .. 160, z -112 .. -92: one storey, the corridor down the middle, the
     * cells either side behind their solid doors, nothing in them but the concrete shelf and the
     * steel pan; the exercise cages along the south side.
     */
    private void segregation() {
        int x0 = 110, x1 = 160, z0 = -112, z1 = -92, top = 5;
        shell(x0, z0, x1, z1, top);
        door(x0, 0, -102, 1, 0, "pn_seg_door", "west");
        sign("seg", x0 - 1, 3, -103, "west");
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            for (int y = 1; y < top; y++) {
                set(x, y, -105, y == 1 ? "pn_dado" : "pn_paint");
                set(x, y, -99, y == 1 ? "pn_dado" : "pn_paint");
            }
        }
        for (int x = x0 + 5; x <= x1 - 2; x += 4) {
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                if (z >= -104 && z <= -100) {
                    continue;
                }
                for (int y = 1; y < top; y++) {
                    set(x, y, z, y == 1 ? "pn_dado" : "pn_paint");
                }
            }
            for (int s = 0; s < 2; s++) {
                int dz = s == 0 ? -105 : -99, cx = x - 2;
                set(cx, 1, dz, "air");
                set(cx, 2, dz, "air");
                boolean open = chance() < 0.3;
                Doors.place(g.p, cx, 1, dz, "pn_seg_door", s == 0 ? "south" : "north", "left", open);
                int back = s == 0 ? z0 + 2 : z1 - 2, bunk = s == 0 ? z0 + 3 : z1 - 3;
                set(cx - 1, 1, back, "pn_toilet_sink[facing=" + (s == 0 ? "south" : "north") + "]");
                set(cx + 1, 1, back, "concrete_panel");
                set(cx + 1, 1, bunk, "concrete_panel");
                set(cx + 1, 2, bunk, chance() < 0.6 ? "home_mattress_floor[facing=north]" : "air");
                if (chance() < 0.25) {
                    set(cx - 1, 1, bunk, chance() < 0.5 ? "home_corpse_head[facing=north]" : "blood_splat");
                }
            }
        }
        // the cages: chain link six by five, a gate each, open to the sky
        for (int c = 0; c < 6; c++) {
            int cx = x0 + 2 + c * 8;
            for (int x = cx; x <= cx + 6; x++) {
                for (int z = z1 + 1; z <= z1 + 7; z++) {
                    set(x, 0, z, "floor_concrete");
                    boolean edge = x == cx || x == cx + 6 || z == z1 + 7;
                    if (edge) {
                        boolean post = (x == cx || x == cx + 6) && (z == z1 + 1 || z == z1 + 7 || z == z1 + 4) || z == z1 + 7 && x == cx + 3;
                        for (int y = 1; y <= 3; y++) {
                            set(x, y, z, post ? "pn_post" : "chainlink[axis=" + (z == z1 + 7 ? "x" : "z") + "]");
                        }
                        set(x, 4, z, "st_razor");
                    }
                }
            }
            set(cx + 3, 1, z1, "air");
            set(cx + 3, 2, z1, "air");
            set(cx + 3, 1, z1 - 1, "air");
            set(cx + 3, 2, z1 - 1, "air");
            Doors.place(g.p, cx + 3, 1, z1, "pn_cell_door", "south", "left", chance() < 0.5);
        }
        lamps(x0 + 4, -103, x1 - 4, -101, top - 1);
    }

    // ------------------------------------------------------------------ the gatehouse

    /**
     * The gatehouse in the west wall, x -190 .. -158, z -16 .. 16, two floors, its name across the
     * front: the lobby where visitors were searched, the visiting room's booths, the armoury;
     * upstairs the offices and the control room looking down into the sally port south of it,
     * x -190 .. -158, z 17 .. 28, the outer gate and the inner, roofed over between.
     */
    private void gatehouse() {
        int x0 = -190, x1 = -158, z0 = -16, z1 = 16, up = 5, top = 10;
        shell(x0, z0, x1, z1, top, up);
        windowsZ(x0, 1, z0 + 3, z1 - 3, 2, 3, 4, "west");
        windowsZ(x0, 1, z0 + 3, z1 - 3, up + 2, up + 3, 4, "west");
        windowsZ(x1, -1, z0 + 3, z1 - 3, up + 2, up + 3, 4, "east");
        windowsX(x0 + 3, x1 - 3, z1, -1, up + 2, up + 3, 3, "south");
        // the front doors, the name over them
        for (int z = -1; z <= 0; z++) {
            set(x0 + 1, 1, z, "air");
            set(x0 + 1, 2, z, "air");
        }
        Doors.abandoned(g.p, g.rng, x0, 1, -1, "shop_door", "west", "left");
        Doors.abandoned(g.p, g.rng, x0, 1, 0, "shop_door", "west", "right");
        g.row("pn_name_", 6, x0 - 1, 8, -3, "west", 0.04);
        sign("visit", x0 - 1, 4, 3, "west");
        set(x0 - 1, 4, -5, "pn_sgn_hostage_1[facing=west]");
        set(x0 - 1, 4, -4, "pn_sgn_hostage_0[facing=west]");
        // inside: the lobby x -188 .. -178 down the front
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            for (int y = 1; y < up; y++) {
                set(-177, y, z, y == 1 ? "pn_dado" : "pn_paint");
            }
        }
        for (int z = -3; z <= 2; z++) {
            set(-180, 1, z, z == -1 || z == 0 ? "wf_turnstile[facing=east]" : "store_counter[facing=west]");
            set(-180, 2, z, z == -1 || z == 0 ? "air" : "pn_bars[axis=z]");
        }
        for (int z = -1; z <= 0; z++) {
            set(-177, 1, z, "air");
            set(-177, 2, z, "air");
        }
        Doors.place(g.p, -177, 1, -1, "pn_cell_door", "east", "left", chance() < 0.5);
        for (int z = z0 + 3; z <= -5; z += 1) {
            set(-187, 1, z, "wf_seats[facing=east]");
        }
        for (int z = 5; z <= z1 - 3; z++) {
            set(-188, 1, z, "sc_locker_a[facing=east]");
            set(-188, 2, z, "sc_locker_top_a[facing=east]");
        }
        set(-183, 1, 10, "desk[facing=west]");
        set(-182, 1, 10, "office_chair[facing=west]");
        // the visiting room x -176 .. -160, z -14 .. -2: the booths down x = -168, visitors west
        for (int x = -176; x <= -160; x++) {
            for (int y = 1; y < up; y++) {
                set(x, y, -1, y == 1 ? "pn_dado" : "pn_paint");
            }
        }
        for (int z = z0 + 2; z <= -2; z++) {
            set(-168, 1, z, "pn_visit_booth[facing=west]");
            set(-168, 2, z, "pn_visit_top[facing=west]");
            set(-168, 3, z, "pn_paint");
            set(-168, 4, z, "pn_paint");
        }
        set(-177, 1, -8, "air");
        set(-177, 2, -8, "air");
        Doors.abandoned(g.p, g.rng, -177, 1, -8, "fire_door", "east", "left");
        door(x1, 0, -8, -1, 0, "pn_cell_door", "east");
        sign("visit", -175, 3, -15, "south");
        // the armoury x -176 .. -160, z 0 .. 14: the racks stripped, the lockers forced
        set(-170, 1, -1, "air");
        set(-170, 2, -1, "air");
        Doors.abandoned(g.p, g.rng, -170, 1, -1, "bunk_door_steel", "north", "left");
        for (int x = -175; x <= -161; x += 2) {
            set(x, 1, z1 - 2, "sh_gun_rack[facing=north]");
            set(x + 1, 1, z1 - 2, chance() < 0.5 ? "sh_locker[facing=north]" : "bunk_crate_ammo[facing=north]");
        }
        set(-168, 1, 6, "bunk_map_table");
        // the stair up in the lobby's south end
        for (int i = 0; i < up; i++) {
            set(-188 + i, 1 + i, 13, "stairs_concrete[facing=east]");
            set(-188 + i, 1 + i, 14, "stairs_concrete[facing=east]");
            set(-188 + i, up, 13, "air");
            set(-188 + i, up, 14, "air");
        }
        // upstairs: the offices along the front, the control room over the sally port
        for (int z = z0 + 2; z <= z1 - 2; z++) {
            for (int y = up + 1; y < top; y++) {
                set(-177, y, z, y == up + 1 ? "pn_dado" : "pn_paint");
            }
        }
        set(-177, up + 1, 6, "air");
        set(-177, up + 2, 6, "air");
        Doors.abandoned(g.p, g.rng, -177, up + 1, 6, "fire_door", "east", "left");
        for (int z = z0 + 3; z <= 8; z += 4) {
            set(-185, up + 1, z, "desk[facing=east]");
            set(-184, up + 1, z, "office_chair[facing=west]");
            set(-187, up + 1, z, "filing_cabinet[facing=east]");
        }
        sign("control", -176, up + 3, 7, "east");
        for (int x = -175; x <= -161; x++) {
            set(x, up + 1, z1 - 2, (x % 3 == 0 ? "pn_panel" : "fit_console" + (g.power && chance() < 0.4 ? "_on" : "")) + "[facing=south]");
            if (x % 2 == 0) {
                set(x, up + 1, z1 - 4, "office_chair[facing=south]");
            }
        }
        for (int x = -175; x <= -161; x += 2) {
            set(x, up + 3, z0 + 2, "tv_monitor" + (g.power && chance() < 0.4 ? "_on" : "") + "[facing=south]");
            set(x, up + 4, z0 + 2, "tv_monitor" + (g.power && chance() < 0.3 ? "_on" : "") + "[facing=south]");
        }
        set(-163, up + 1, 2, "sc_pa_console[facing=west]");
        set(-163, up + 1, 4, "bunk_radio_desk[facing=west]");
        lamps(x0 + 4, z0 + 4, x1 - 4, z1 - 4, up - 1);
        lamps(x0 + 4, z0 + 4, x1 - 4, z1 - 4, top - 1);
        sallyPort();
        g.mess(x0 + 2, z0 + 2, x1 - 2, z1 - 2, 0, 0.03);
    }

    /** The sally port, z 17 .. 28: the walls either side, the roof, the two gates, the bus left in it. */
    private void sallyPort() {
        int x0 = -190, x1 = -158, z0 = 17, z1 = 28, top = 8;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, chance() < 0.1 ? "asphalt_crack" : "asphalt");
                boolean wall = z == z1 || z == z1 - 1;
                for (int y = 1; y <= top + 1; y++) {
                    set(x, y, z, wall ? (y == top + 1 ? "bd_coping" : z == z1 ? ext() : "ch_ashlar") : y == top ? "concrete_panel" : "air");
                }
                if ((z == 21 || z == 24) && x % 2 == 0) {
                    set(x, 0, z, "pn_floor_line");
                }
            }
        }
        // the gates: the outer shut, the inner slid open
        for (int z = z0; z <= z1 - 2; z++) {
            set(x0, 1, z, "pn_gate[axis=z]");
            set(x0, 2, z, "pn_gate[axis=z]");
            set(x0, 3, z, "pn_gate_top[axis=z]");
            if (z > z1 - 6) {
                set(x1, 1, z, "pn_gate[axis=z]");
                set(x1, 2, z, "pn_gate[axis=z]");
                set(x1, 3, z, "pn_gate_top[axis=z]");
            }
        }
        sign("sally", x0 - 1, 5, 21, "west");
        for (int x = x0 + 4; x <= x1 - 4; x += 6) {
            set(x, top - 1, 22, g.power && chance() < 0.4 ? "cp_flood_hang_lit[facing=south]" : "cp_flood_hang[facing=south]");
        }
        g.p.vehicle(-174.0, 1, 22.5, 90, "school_bus@wrecked", g.rng.nextLong());
        set(-162, 1, 18, "sandbags[facing=east]");
        set(-162, 1, 19, "sandbags[facing=east]");
        set(-163, 1, 25, "jersey_barrier[facing=east]");
    }
}
