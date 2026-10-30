package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;

/**
 * The army's compound on Hollis Island's east end, drawn in {@link Hollis.Island}'s frame (after
 * refs/quarantine: isolation-ward 01-06, hazmat-triage-tents 01-06, body-bags-mass-graves 01-02):
 * <ul>
 * <li>the chain-link round it with the watchtowers on the corners and the gate on the drive, the
 * flood masts;</li>
 * <li>the two new isolation wards up on their concrete stilts, white ribbed sheeting and louvre
 * windows, the air conditioners, the wide stair up to each; single rooms off a corridor, a bed and
 * a monitor in each;</li>
 * <li>the triage tents by the gate, the field hospital, the command post, the helipad with the
 * last helicopter on it, the generators and the fuel, the mast;</li>
 * <li>past them the trenches with the bags in rows and the lime over, the shrouds waiting their
 * turn, the burn pit, the soldiers' own graves.</li>
 * </ul>
 */
final class HollisCamp {
    /** The fence: x CAMP .. X1, z Z0 .. Z1. */
    static final int X1 = 290, Z0 = -140, Z1 = 140;

    private final Hollis.Island c;
    private final Bldg b;
    private final Camp camp;

    HollisCamp(Hollis.Island c) {
        this.c = c;
        this.b = c.b;
        this.camp = c.camp;
    }

    private void set(int x, int y, int z, String block) {
        c.set(x, y, z, block);
    }

    void build() {
        fence();
        triage();
        ward(60, -62, 124, -48);
        ward(60, -104, 124, -90);
        hospital();
        command();
        air();
        graves();
    }

    // ------------------------------------------------------------------ the wire

    /** The chain-link round it, the towers on the corners, the gate on the drive with its booth and floods. */
    private void fence() {
        int x0 = Hollis.CAMP;
        run(x0, Z0, x0, Hollis.ROAD0 - 3, 0.04);
        run(x0, Hollis.ROAD1 + 3, x0, Z1, 0.04);
        run(x0, Z0, X1, Z0, 0.06);
        run(x0, Z1, X1, Z1, 0.06);
        // the east side, where they came through
        run(X1, Z0, X1, Z1, 0.3);
        for (int[] t : new int[][] {{x0 + 2, Z0 + 2}, {X1 - 6, Z0 + 2}, {x0 + 2, Z1 - 6}, {X1 - 6, Z1 - 6}}) {
            if (c.land(t[0] + 2, t[1] + 2)) {
                camp.watchtower(t[0], t[1]);
            }
        }
        camp.boom(x0, Hollis.ROAD0 - 2, 7, c.chance() < 0.5);
        camp.booth(x0 + 4, Hollis.ROAD0 - 9);
        camp.jersey(x0 - 2, Hollis.ROAD0 - 3, x0 - 2, Hollis.ROAD0 - 6);
        camp.jersey(x0 - 2, Hollis.ROAD1 + 3, x0 - 2, Hollis.ROAD1 + 6);
        c.sign("restricted", x0 - 1, 2, Hollis.ROAD1 + 7, "west");
        for (int z = Z0 + 30; z < Z1 - 20; z += 60) {
            camp.floodMast(x0 + 3, z, 10);
            camp.floodMast(X1 - 3, z, 10);
        }
    }

    /** A run of the chain-link along x or z, only where there's land under it. */
    private void run(int x0, int z0, int x1, int z1, double breach) {
        int a = 0;
        boolean alongX = z0 == z1;
        int n = alongX ? Math.abs(x1 - x0) : Math.abs(z1 - z0);
        for (int i = 0; i <= n; i += 8) {
            int ax = alongX ? Math.min(x0, x1) + i : x0, az = alongX ? z0 : Math.min(z0, z1) + i;
            int bx = alongX ? Math.min(ax + 7, Math.max(x0, x1)) : x0, bz = alongX ? z0 : Math.min(az + 7, Math.max(z0, z1));
            if (c.land(ax, az) && c.land(bx, bz)) {
                camp.fence(ax, az, bx, bz, breach);
            }
            a++;
        }
    }

    // ------------------------------------------------------------------ the triage

    /** The triage tents inside the gate, the line of chairs, the yellow board, the stretchers. */
    private void triage() {
        int x0 = Hollis.CAMP + 8;
        for (int i = 0; i < 2; i++) {
            camp.tent(x0 + i * 9, Hollis.ROAD1 + 6, 12, "white", true);
        }
        for (int x = x0; x < x0 + 16; x += 2) {
            set(x, 1, Hollis.ROAD1 + 4, c.chance() < 0.7 ? "hs_chairs[facing=north]" : "hs_chairs_tipped[facing=north]");
        }
        camp.toilets(x0 + 24, Hollis.ROAD1 + 8, 4);
        camp.crates(x0 + 34, Hollis.ROAD1 + 8, 8, 6);
        for (int i = 0; i < 6; i++) {
            set(x0 + c.rng.nextInt(24), 1, Hollis.ROAD1 + 20 + c.rng.nextInt(4),
                    c.any("st_stretcher[facing=east]", "hs_gurney_bag_a[facing=east]", "bunk_hazmat", "hs_biohazard"));
        }
    }

    // ------------------------------------------------------------------ the isolation wards

    /**
     * A new isolation ward (isolation-ward 02, 03): up on concrete stilts five over the grass, the
     * stores in block under its west end, the rest open below; the storey of white ribbed sheeting
     * with louvres down both sides under a shallow tin roof; the wide concrete stair up to its door;
     * inside the corridor along the north side and the single rooms off it, each with its bed, the
     * monitor and the drip, the air conditioner outside.
     */
    private void ward(int x0, int z0, int x1, int z1) {
        int f = 5, top = f + 5;
        // the stilts and the slab
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, f, z, "floor_concrete");
                for (int y = 1; y < f; y++) {
                    set(x, y, z, "air");
                }
                set(x, 0, z, c.chance() < 0.7 ? "minecraft:coarse_dirt" : "minecraft:gravel");
            }
            if ((x - x0) % 5 == 0 || x == x1) {
                for (int z : new int[] {z0, (z0 + z1) / 2, z1}) {
                    for (int y = 1; y < f; y++) {
                        set(x, y, z, "qs_stilt");
                    }
                }
            }
        }
        // the stores under the west end
        for (int x = x0; x <= x0 + 10; x++) {
            for (int z = z0; z <= z1; z++) {
                if (x == x0 || x == x0 + 10 || z == z0 || z == z1) {
                    for (int y = 1; y < f; y++) {
                        set(x, y, z, c.chance() < 0.2 ? "sh_cmu_scratch" : "sh_cmu");
                    }
                }
            }
        }
        b.door(x0 + 10, 1, (z0 + z1) / 2 + 2, Doors.STEEL, "east");
        for (int z = z0 + 2; z < z1 - 1; z += 2) {
            set(x0 + 2, 1, z, c.any("hs_oxygen[facing=east]", "crate", "st_pallet[facing=north]", "hs_cart_linen[facing=east]"));
        }
        // under the open part: what was dumped there
        for (int i = 0; i < 6; i++) {
            set(x0 + 12 + c.rng.nextInt(x1 - x0 - 14), 1, z0 + 1 + c.rng.nextInt(z1 - z0 - 2),
                    c.any("sandbags[facing=north]", "hs_gurney_a[facing=east]", "pw_drum[axis=x]", "trash_bag[facing=north]", "hs_biohazard"));
        }
        // the walls, the louvres, the roof
        for (int y = f + 1; y < top; y++) {
            for (int x = x0; x <= x1; x++) {
                set(x, y, z0, c.chance() < 0.12 ? "qs_rib_white_stain" : "qs_rib_white");
                set(x, y, z1, c.chance() < 0.12 ? "qs_rib_white_stain" : "qs_rib_white");
            }
            for (int z = z0; z <= z1; z++) {
                set(x0, y, z, "qs_rib_white");
                set(x1, y, z, "qs_rib_white");
            }
        }
        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            for (int y = f + 2; y <= f + 3; y++) {
                set(x, y, z0, c.chance() < 0.1 ? "air" : "qs_louvre[facing=north]");
                set(x, y, z1, c.chance() < 0.1 ? "air" : "qs_louvre[facing=south]");
            }
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x == x0 - 1 || x == x1 + 1 || z == z0 - 1 || z == z1 + 1;
                String face = z == z0 - 1 ? "north" : z == z1 + 1 ? "south" : x == x0 - 1 ? "west" : x == x1 + 1 ? "east" : "north";
                if (c.chance() > 0.02) {
                    set(x, top, z, "ext_porch_roof_tin_galv" + (edge ? "_edge" : "") + "[facing=" + face + "]");
                }
            }
        }
        // the stair up to the door at the west end of the north wall, three wide
        int sx = x0 + 2;
        for (int i = 0; i < f; i++) {
            int z = z0 - f + i;
            for (int x = sx; x <= sx + 2; x++) {
                set(x, i + 1, z, "stairs_concrete[facing=south]");
                for (int y = 0; y <= i; y++) {
                    set(x, y, z, "concrete_dark");
                }
                for (int y = i + 2; y <= i + 4; y++) {
                    set(x, y, z, "air");
                }
            }
            set(sx - 1, i + 2, z, "railing[facing=east]");
            set(sx + 3, i + 2, z, "railing[facing=west]");
        }
        for (int x = sx; x <= sx + 2; x++) {
            set(x, f, z0 - 1, "floor_concrete");
        }
        b.doors(sx, f + 1, z0, Doors.STEEL, "north");
        c.sign("isolation", sx + 4, f + 3, z0 - 1, "north");
        // inside: the corridor, the rooms off it
        int cz = z0 + 4;
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < z1; z++) {
                set(x, f, z, c.chance() < 0.05 ? "hs_lino_blood" : "hs_lino");
                for (int y = f + 1; y < top; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = f + 1; y < top; y++) {
                set(x, y, cz, y == f + 1 ? "hs_wall_lo" : "hs_wall");
            }
        }
        for (int x = x0 + 7; x < x1; x += 7) {
            for (int z = cz + 1; z < z1; z++) {
                for (int y = f + 1; y < top; y++) {
                    set(x, y, z, y == f + 1 ? "hs_wall_lo" : "hs_wall");
                }
            }
        }
        for (int rx = x0 + 1; rx + 5 < x1; rx += 7) {
            int dx = rx + 2;
            set(dx, f + 1, cz, "air");
            set(dx, f + 2, cz, "air");
            b.door(dx, f + 1, cz, Doors.FLAT, "north");
            boolean dead = c.chance() < 0.3;
            set(rx + 3, f + 1, z1 - 1, (dead ? "hs_bed_head_blood" : "hs_bed_head") + "[facing=south]");
            set(rx + 3, f + 1, z1 - 2, (dead && c.chance() < 0.5 ? "hs_bed_foot_body" : "hs_bed_foot") + "[facing=south]");
            set(rx + 4, f + 1, z1 - 1, (c.power && c.chance() < 0.3 ? "hs_monitor_on" : "hs_monitor") + "[facing=north]");
            set(rx + 2, f + 1, z1 - 1, "bunk_iv_stand");
            set(rx + 1, f + 1, cz + 1, c.any("hs_sharps[facing=south]", "hs_biohazard", "office_chair[facing=south]"));
            set(rx + 3, f + 4, z1 + 1, "pk_aircon[facing=south]");
            if (c.chance() < 0.3) {
                set(rx + 4, f + 1, cz + 2, "hs_plastic");
            }
        }
        for (int x = x0 + 1; x < x1; x += 3) {
            set(x, top - 1, z0 + 2, c.power && c.chance() < 0.3 ? "ceiling_light" : "ceiling_light_off");
        }
        b.mess(x0 + 1, z0 + 1, x1 - 1, cz - 1, f + 1, 0.1);
        set(x1 - 2, f + 1, z0 + 2, "hs_station[facing=west]");
        set(x1 - 2, f + 1, z0 + 1, "hs_station_end[facing=west]");
    }

    // ------------------------------------------------------------------ the field hospital and the command post

    /** The long white frame tents of the field hospital in a row, the oxygen and the generators between. */
    private void hospital() {
        camp.hospital(140, -124, 18, 32);
        camp.hospital(162, -124, 10, 24);
        camp.hospital(176, -124, 10, 24);
        for (int x = 140; x <= 186; x += 3) {
            set(x, 1, -88, c.any("hs_oxygen[facing=north]", "pw_drum[axis=x]", "crate", "hs_gurney_bag_a[facing=east]"));
        }
        for (int i = 0; i < 4; i++) {
            set(190 + i * 2, 1, -100, "tn_genset[facing=west]");
        }
        camp.fuelTanks(196, -120, 3);
    }

    /** The command post: a block building with the radios, the map, the cots; the mast behind it. */
    private void command() {
        int x0 = 140, x1 = 160, z0 = -44, z1 = -30;
        b.shell(x0, z0, x1, z1, 1, 4, "sh_cmu", "floor_concrete", "floor_concrete");
        b.flat(x0, z0, x1, z1, 4, "sh_cmu", 1, "stone_trim");
        for (int x = x0 + 3; x <= x1 - 3; x += 4) {
            b.window(x, 2, z1, "south", 2, null);
        }
        b.door(x0 + 2, 1, z1, Doors.STEEL, "south");
        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            set(x, 1, z0 + 1, "bunk_radio_desk[facing=south]");
            set(x, 1, z0 + 2, c.chance() < 0.5 ? "office_chair_fallen[facing=" + c.dir() + "]" : "office_chair[facing=north]");
        }
        set(x0 + 10, 1, z0 + 7, "desk[facing=north]");
        set(x0 + 11, 1, z0 + 7, "desk[facing=north]");
        for (int z = z0 + 5; z < z1 - 1; z += 2) {
            set(x1 - 2, 1, z, "ch_cot_head[facing=east]");
            set(x1 - 3, 1, z, "ch_cot_foot[facing=east]");
        }
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.1);
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            set(x, 1, z1 + 2, x % 3 == 0 ? "air" : "sandbags[facing=south]");
        }
        camp.mast(x1 + 6, z0 + 2, 26);
    }

    /** The helipad, the helicopter on it that never lifted again, the light towers. */
    private void air() {
        camp.helipad(206, -70);
        camp.helicopter(213, -66, true);
        camp.lightTower(202, -50, true);
        camp.lightTower(224, -50, false);
        c.sign("restricted", 211, 2, -72, "north");
    }

    // ------------------------------------------------------------------ the dead

    /**
     * The trenches dug with the excavator in rows, the bags laid in them and limed, the spoil
     * along them; the shrouded dead from the old wards waiting along the track; the burn pit when
     * the trenches weren't fast enough; the soldiers buried apart with a cross each.
     */
    private void graves() {
        for (int i = 0; i < 3; i++) {
            camp.trench(150, 60 + i * 10, i == 2 ? 24 : 34);
        }
        for (int x = 146; x <= 196; x++) {
            for (int z = 56; z <= 92; z++) {
                if (c.empty(x, 1, z) && c.chance() < 0.3 && c.get(x, 0, z) != null && !c.get(x, 0, z).equals("air")) {
                    set(x, 1, z, "qs_lime");
                }
            }
        }
        for (int x = 150; x <= 190; x += 2) {
            set(x, 1, 52, "qs_shroud_head[facing=north]");
            set(x, 1, 53, "qs_shroud_foot[facing=north]");
        }
        c.sign("burial", 140, 2, 52, "west");
        set(141, 1, 52, "pn_post");
        set(141, 1, 53, "pn_post");
        camp.burnPit(226, 78, 5);
        for (int x = 230; x <= 262; x += 4) {
            for (int z = 108; z <= 120; z += 6) {
                if (c.land(x, z)) {
                    camp.grave(x, z);
                }
            }
        }
    }
}
