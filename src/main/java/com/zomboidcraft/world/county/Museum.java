package com.zomboidcraft.world.county;

/**
 * The Grayhaven Museum along the north side of City Park: pale limestone on a rusticated base,
 * two tall floors, the middle pavilion standing forward under its portico of six columns with
 * GRAYHAVEN MUSEUM OF NATURAL HISTORY AND ART in the frieze and the city's seal in the pediment,
 * the copper dome over the rotunda behind it; the wings either side top-lit, their lanterns along
 * the roof. Inside ({@link MuseumHalls}) natural history to the west, art and the ancient world to
 * the east, the rotunda between with the T. rex.
 *
 * <p>The rotunda is the hub: the entrance hall north of it, the wings through the passages east
 * and west, the store and the café south, the balcony on its columns at the upper floor reaching
 * the same four ways. The fire stairs at the wings' far ends, their huts on the roof.
 *
 * <p>Drawn in the park's frame: x along the front (west to east), z from the front (north) back.
 */
final class Museum {
    /** The walls (inclusive): the wings' fronts set back behind the pavilion's. */
    static final int MX0 = 34, MX1 = 178, PV0 = 86, PV1 = 126, MZ0 = 18, WZ0 = 22, MZ1 = 78;
    /** The floors: ground, upper, roof. */
    static final int F0 = 3, F1 = 11, ROOF = 19;
    static final int[] YS = {F0, F1, ROOF};
    /** The rotunda: its centre, the inside of its ring wall, the ring's outside. */
    static final int RX = 106, RZ = 50;
    static final double RIN = 15, ROUT = 16.6;
    /** The passages out of the rotunda: west and east between z PZ0..PZ1, north and south between x PX0..PX1. */
    static final int PZ0 = 47, PZ1 = 53, PX0 = 103, PX1 = 109;
    /** The entrance hall's back wall, the back hall's front wall. */
    static final int HALL_Z = 34, BACK_Z = 66;
    /** The fire stairs' shafts (x0, z0): walls x0..x0 + 4, z0..z0 + 9, the doors south. */
    static final int SW_X = 35, SE_X = 173, ST_Z = 24;
    /** The lanterns over the upper galleries, and the lantern's roof. */
    static final int LW0 = 53, LW1 = 83, LE0 = 129, LE1 = 145, LZ0 = 38, LZ1 = 62, LTOP = 24;
    /** The portico: deck, columns, steps. */
    static final int QX0 = 89, QX1 = 123, QZ = 8, STEP_Z = 5;
    /** The drum and the dome's spring line. */
    static final int DRUM = 25, DOME_H = 9;

    private final CityPark c;

    Museum(CityPark c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    private double chance() {
        return c.chance();
    }

    static double r(int x, int z) {
        return Math.hypot(x - RX, z - RZ);
    }

    /** Inside the building's walls (the pavilion's or a wing's). */
    static boolean inside(int x, int z) {
        if (x <= MX0 || x >= MX1 || z >= MZ1) {
            return false;
        }
        return x > PV0 && x < PV1 ? z > MZ0 : z > WZ0;
    }

    void build() {
        shell();
        poche();
        rotunda();
        lanterns();
        c.stair(SW_X, ST_Z, true, YS, "mu_lime", "un_roof");
        c.stair(SE_X, ST_Z, true, YS, "mu_lime", "un_roof");
        new MuseumHalls(c).build();
        portico();
        dome();
        front();
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int x = MX0; x <= MX1; x++) {
            for (int z = MZ0; z <= MZ1; z++) {
                boolean pav = x >= PV0 && x <= PV1;
                if (!pav && z < WZ0) {
                    continue;
                }
                boolean wall = x == MX0 || x == MX1 || z == MZ1 || (pav ? z == MZ0 : z == WZ0) || ((x == PV0 || x == PV1) && z < WZ0);
                c.fill(x, 0, z, x, 2, z, wall ? "mu_rustic" : "concrete_dark");
                if (wall) {
                    for (int y = 3; y <= ROOF; y++) {
                        set(x, y, z, y == F1 || y == ROOF ? "mu_lime" : chance() < 0.08 ? "mu_lime_b" : "mu_lime");
                    }
                    continue;
                }
                set(x, F0, z, (x + z) % 2 == 0 ? "us_marble_a" : "us_marble_b");
                set(x, F1, z, x > PV0 && x < PV1 ? "un_slab_terrazzo" : "un_slab_boards");
                set(x, ROOF, z, "un_roof");
            }
        }
        // the cornice round the roof, the balustrade over it with piers at the corners and every eight
        for (int x = MX0; x <= MX1; x++) {
            int fz = x >= PV0 && x <= PV1 ? MZ0 : WZ0;
            set(x, ROOF, fz - 1, "cv_cornice_stone[facing=north]");
            set(x, ROOF, MZ1 + 1, "cv_cornice_stone[facing=south]");
            set(x, F1, MZ1 + 1, "cv_water_table[facing=south]");
            set(x, ROOF + 1, fz, (x - MX0) % 8 == 0 || x == PV0 || x == PV1 ? "cv_pier_stone" : "cv_balustrade_stone[axis=x]");
            set(x, ROOF + 1, MZ1, (x - MX0) % 8 == 0 ? "cv_pier_stone" : "cv_balustrade_stone[axis=x]");
            set(x, 1, fz - 1, "cv_water_table[facing=north]");
            set(x, 1, MZ1 + 1, "cv_water_table[facing=south]");
        }
        for (int z = MZ0; z <= MZ1; z++) {
            for (int x : new int[] {MX0, MX1}) {
                if (z < WZ0) {
                    continue;
                }
                String out = x == MX0 ? "west" : "east";
                set(x + (x == MX0 ? -1 : 1), ROOF, z, "cv_cornice_stone[facing=" + out + "]");
                set(x + (x == MX0 ? -1 : 1), 1, z, "cv_water_table[facing=" + out + "]");
                set(x, ROOF + 1, z, (z - WZ0) % 8 == 0 || z == MZ1 ? "cv_pier_stone" : "cv_balustrade_stone[axis=z]");
            }
            if (z < WZ0) {
                // the pavilion's sides where it stands forward of the wings
                for (int x : new int[] {PV0, PV1}) {
                    String out = x == PV0 ? "west" : "east";
                    set(x + (x == PV0 ? -1 : 1), ROOF, z, "cv_cornice_stone[facing=" + out + "]");
                    set(x, ROOF + 1, z, "cv_balustrade_stone[axis=z]");
                }
            }
        }
        // the inner walls between the wings and the pavilion, both floors
        for (int x : new int[] {PV0, PV1}) {
            for (int z = WZ0 + 1; z < MZ1; z++) {
                for (int y = F0 + 1; y < ROOF; y++) {
                    set(x, y, z, y == F1 ? "un_slab_boards" : "mu_lime");
                }
            }
        }
        // the entrance hall's back wall and the back hall's front wall
        for (int x = PV0 + 1; x < PV1; x++) {
            for (int y = F0 + 1; y < ROOF; y++) {
                if (y != F1) {
                    set(x, y, HALL_Z, "mu_lime");
                    set(x, y, BACK_Z, "mu_lime");
                }
            }
        }
        windows();
    }

    /** Galleries lit from above or lined with dioramas keep their outside walls blind. */
    private static boolean blind(int x, int f) {
        return (x >= 51 && x <= 85) || (f == 1 && x >= 127 && x <= 146);
    }

    /** The tall arched windows: the wings' bays where the galleries want light, blind niches where they don't. */
    private void windows() {
        for (int f = 0; f < 2; f++) {
            int y = YS[f];
            for (int x = MX0 + 4; x <= MX1 - 4; x += 6) {
                if (x >= PV0 - 1 && x <= PV1 + 1) {
                    continue;
                }
                boolean stairs = (x >= SW_X - 1 && x <= SW_X + 5) || (x >= SE_X - 1 && x <= SE_X + 5);
                if (!stairs) {
                    if (blind(x, f)) {
                        niche(x, y, WZ0, "north");
                    } else {
                        window(x, y, WZ0, "north");
                    }
                }
                if (blind(x, f)) {
                    niche(x, y, MZ1, "south");
                } else {
                    window(x, y, MZ1, "south");
                }
            }
            for (int z = WZ0 + 14; z < MZ1 - 3; z += 6) {
                window(MX0, y, z, "west");
                if (!(f == 0 && z > 50 && z < 66)) {
                    window(MX1, y, z, "east");
                }
            }
        // the pavilion's front either side of the doors, and its back over the café
            for (int x : new int[] {89, 94, 118, 123}) {
                window(x, y, MZ0, "north");
            }
            for (int x : new int[] {92, 98, 114, 120}) {
                window(x, y, MZ1, "south");
            }
        }
        // pilasters on the wings' fronts between the bays
        for (int x = MX0 + 1; x < MX1; x += 6) {
            if (x > PV0 - 2 && x < PV1 + 2) {
                continue;
            }
            for (int y = 2; y < ROOF; y++) {
                set(x, y, WZ0 - 1, y == 2 ? "cv_column_base_stone" : y == ROOF - 1 ? "cv_column_cap_stone" : "cv_column_stone");
            }
        }
    }

    /** A window two wide in a wall facing out from x, z (along the wall): four panes, the stone arch over them, the sills. */
    void window(int x, int y, int z, String out) {
        int[] o = Bldg.step(out);
        String a = Bldg.along(out);
        int[] l = MallSigns.at(x, z, 2, 0, out), r = MallSigns.at(x, z, 2, 1, out);
        for (int[] c2 : new int[][] {l, r}) {
            for (int k = 2; k <= 5; k++) {
                set(c2[0], y + k, c2[1], c.b.glass(a));
            }
            set(c2[0] + o[0], y + 1, c2[1] + o[1], "sill[facing=" + out + "]");
        }
        set(l[0], y + 6, l[1], "cv_arch_stone_l[facing=" + out + "]");
        set(r[0], y + 6, r[1], "cv_arch_stone_r[facing=" + out + "]");
    }

    /** A blind niche two wide where a window would be: the stained stone set in under its sills. */
    private void niche(int x, int y, int z, String out) {
        int[] o = Bldg.step(out);
        for (int i = 0; i < 2; i++) {
            int[] c2 = MallSigns.at(x, z, 2, i, out);
            for (int k = 2; k <= 6; k++) {
                set(c2[0], y + k, c2[1], "mu_lime_b");
            }
            set(c2[0] + o[0], y + 1, c2[1] + o[1], "sill[facing=" + out + "]");
        }
        int[] l = MallSigns.at(x, z, 2, 0, out);
        set(l[0] + o[0], y + 6, l[1] + o[1], "cv_keystone[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the rotunda

    /** The stone filling the pavilion round the rotunda's ring, on both floors, the passages cut through it. */
    private void poche() {
        for (int x = PV0 + 1; x < PV1; x++) {
            for (int z = HALL_Z + 1; z < BACK_Z; z++) {
                if (r(x, z) < ROUT) {
                    continue;
                }
                for (int y = F0 + 1; y < ROOF; y++) {
                    set(x, y, z, "mu_lime");
                }
            }
        }
    }

    private void rotunda() {
        // the ring wall from the floor up to the drum's top, the floor inside it, open to the dome
        for (int x = RX - 17; x <= RX + 17; x++) {
            for (int z = RZ - 17; z <= RZ + 17; z++) {
                double d = r(x, z);
                if (d >= RIN && d < ROUT) {
                    for (int y = F0 + 1; y <= DRUM; y++) {
                        set(x, y, z, chance() < 0.06 ? "mu_lime_b" : "mu_lime");
                    }
                } else if (d < RIN) {
                    set(x, F0, z, d < 8 ? "us_marble_b" : (int) d % 3 == 0 ? "us_marble_b" : "us_marble_a");
                    for (int y = F0 + 1; y <= DRUM; y++) {
                        set(x, y, z, "air");
                    }
                }
            }
        }
        // the four passages out, both floors: tall below, a storey high above
        for (int f = 0; f < 2; f++) {
            int y = YS[f], top = f == 0 ? F0 + 5 : F1 + 4;
            String floor = f == 0 ? "us_marble_a" : "un_slab_terrazzo";
            for (int x = PV0; x <= PV1; x++) {
                for (int z = HALL_Z; z <= BACK_Z; z++) {
                    boolean we = z >= PZ0 && z <= PZ1 && (x <= RX - 10 || x >= RX + 10);
                    boolean ns = x >= PX0 && x <= PX1 && (z <= RZ - 10 || z >= RZ + 10);
                    if ((we || ns) && r(x, z) >= 10.5 && !(f == 0 && (z == HALL_Z || z == BACK_Z) && !ns)) {
                        c.fill(x, y + 1, z, x, top, z, "air");
                        set(x, y, z, floor);
                    }
                }
            }
        }
        // the balcony on the upper floor, its balustrade along the inner edge, the twelve columns under it
        for (int x = RX - 15; x <= RX + 15; x++) {
            for (int z = RZ - 15; z <= RZ + 15; z++) {
                double d = r(x, z);
                if (d >= 10.5 && d < RIN) {
                    set(x, F1, z, "un_slab_terrazzo");
                }
                if (d >= 10.5 && d < 11.5) {
                    double ang = Math.atan2(z - RZ, x - RX);
                    String axis = Math.abs(Math.sin(ang)) > Math.abs(Math.cos(ang)) ? "x" : "z";
                    set(x, F1 + 1, z, "cv_balustrade_stone[axis=" + axis + "]");
                }
            }
        }
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6 + Math.PI / 12;
            int x = (int) Math.round(RX + Math.cos(a) * 11), z = (int) Math.round(RZ + Math.sin(a) * 11);
            set(x, F0 + 1, z, "cv_column_base_white");
            for (int y = F0 + 2; y < F1 - 1; y++) {
                set(x, y, z, "cv_column_white");
            }
            set(x, F1 - 1, z, "cv_column_cap_white");
            set(x, F1, z, "un_slab_terrazzo");
            set(x, F1 + 1, z, "cv_pier_stone");
        }
        // the names of the halls over the passages, on the ring facing in
        c.sign("dinosaurs", PX0 + 3, F0 + 6, RZ - 14, "south");
        c.sign("mammals", RX - 14, F0 + 6, PZ0 + 3, "east");
        c.sign("painting", RX + 14, F0 + 6, PZ1 - 3, "west");
        c.sign("store", PX1 - 3, F0 + 6, RZ + 14, "north");
        c.sign("ocean", RX - 14, F1 + 5, PZ0 + 3, "east");
        c.sign("ancient", RX + 14, F1 + 5, PZ1 - 3, "west");
        c.sign("american", PX1 - 3, F1 + 5, RZ + 14, "north");
    }

    private String get(int x, int y, int z) {
        String s = c.get(x, y, z);
        return s == null ? "air" : s;
    }

    /** The lanterns: clerestory walls round an opening in the roof over each upper gallery, the skylight over them. */
    private void lanterns() {
        for (int[] l : new int[][] {{LW0, LW1}, {LE0, LE1}}) {
            for (int x = l[0]; x <= l[1]; x++) {
                for (int z = LZ0; z <= LZ1; z++) {
                    boolean edge = x == l[0] || x == l[1] || z == LZ0 || z == LZ1;
                    if (edge) {
                        for (int y = ROOF; y < LTOP; y++) {
                            boolean glass = y >= ROOF + 1 && y <= ROOF + 3 && ((x - l[0]) % 4 != 0) && ((z - LZ0) % 4 != 0)
                                    && !((x == l[0] || x == l[1]) && (z == LZ0 || z == LZ1));
                            set(x, y, z, glass ? c.b.glass(z == LZ0 || z == LZ1 ? "x" : "z") : "mu_lime");
                        }
                        set(x, LTOP, z, "un_roof");
                    } else {
                        set(x, ROOF, z, "air");
                        set(x, LTOP, z, chance() < 0.04 ? "air" : "us_skylight");
                    }
                }
            }
            for (int x = l[0] - 1; x <= l[1] + 1; x++) {
                set(x, LTOP, LZ0 - 1, "cv_cornice_stone[facing=north]");
                set(x, LTOP, LZ1 + 1, "cv_cornice_stone[facing=south]");
            }
            for (int z = LZ0; z <= LZ1; z++) {
                set(l[0] - 1, LTOP, z, "cv_cornice_stone[facing=west]");
                set(l[1] + 1, LTOP, z, "cv_cornice_stone[facing=east]");
            }
        }
    }

    // ------------------------------------------------------------------ the portico

    private void portico() {
        int deck = F0, top = ROOF - 2;
        // the steps across the front, the deck
        for (int x = QX0; x <= QX1; x++) {
            set(x, 1, STEP_Z, "cv_step_stone[facing=south]");
            set(x, 1, STEP_Z + 1, "mu_rustic");
            set(x, 2, STEP_Z + 1, "cv_step_stone[facing=south]");
            c.fill(x, 1, STEP_Z + 2, x, 2, STEP_Z + 2, "mu_rustic");
            set(x, 3, STEP_Z + 2, "cv_step_stone[facing=south]");
            for (int z = QZ; z < MZ0; z++) {
                c.fill(x, 0, z, x, 2, z, "mu_rustic");
                set(x, deck, z, (x + z) % 2 == 0 ? "us_marble_a" : "mu_lime");
            }
        }
        // the cheek walls at the steps' ends
        for (int z = STEP_Z; z < QZ; z++) {
            for (int x : new int[] {QX0 - 1, QX1 + 1}) {
                c.fill(x, 0, z, x, 4, z, "mu_rustic");
            }
        }
        for (int z = QZ; z < MZ0; z++) {
            for (int x : new int[] {QX0 - 1, QX1 + 1}) {
                c.fill(x, 0, z, x, 3, z, "mu_rustic");
                set(x, 4, z, "cv_balustrade_stone[axis=z]");
            }
        }
        // six columns two floors high
        for (int x : new int[] {93, 98, 103, 109, 114, 119}) {
            set(x, deck + 1, QZ + 1, "cv_column_base_stone");
            for (int y = deck + 2; y < top; y++) {
                set(x, y, QZ + 1, "cv_column_stone");
            }
            set(x, top, QZ + 1, "cv_column_cap_stone");
        }
        // the entablature: the coffered soffit, the frieze with the name, the cornice
        for (int x = QX0 - 1; x <= QX1 + 1; x++) {
            for (int z = QZ; z < MZ0; z++) {
                set(x, top + 1, z, z > QZ && x >= QX0 && x <= QX1 ? "us_coffer" : "mu_lime");
                set(x, top + 2, z, "mu_lime");
            }
            set(x, top + 3, QZ - 1, "cv_cornice_stone[facing=north]");
        }
        c.row("mu_name_museum_", 28, 92, top + 2, QZ - 1, "north", 0.02);
        for (int z = QZ; z < MZ0; z++) {
            set(QX0 - 2, top + 3, z, "cv_cornice_stone[facing=west]");
            set(QX1 + 2, top + 3, z, "cv_cornice_stone[facing=east]");
        }
        // the pediment stepping in three a course, the city's seal in the tympanum
        for (int i = 0; i < 6; i++) {
            int y = top + 3 + i;
            for (int x = QX0 - 1 + 3 * i; x <= QX1 + 1 - 3 * i; x++) {
                for (int z = QZ; z <= MZ0; z++) {
                    set(x, y, z, z == QZ ? "mu_lime" : chance() < 0.1 ? "mu_lime_b" : "mu_lime");
                }
                // the raking cornice down the slopes, a course's outer three in front of its face
                if (x - (QX0 - 1 + 3 * i) < 3 || (QX1 + 1 - 3 * i) - x < 3 || i == 5) {
                    set(x, y, QZ - 1, "cv_cornice_stone[facing=north]");
                }
            }
        }
        c.picture("cc_seal_", 3, 3, 105, top + 4, QZ - 1, "north");
        set(RX, top + 9, QZ, "cv_finial");
        // the banners down the wall behind the columns, over the doors
        String[] ban = {"rex", "nile", "light"};
        int[] bx = {101, 107, 113};
        for (int k = 0; k < 3; k++) {
            if (chance() < 0.85) {
                c.picture("mu_vbanner_" + ban[k] + "_", 1, 3, bx[k] + (k == 1 ? -1 : 0), F0 + 7, MZ0 - 1, "north");
            }
        }
        // the lamps on the cheek walls, litter and the barricade's leavings on the deck
        for (int x : new int[] {QX0 - 1, QX1 + 1}) {
            set(x, 5, STEP_Z, "cv_lamp_post");
            set(x, 6, STEP_Z, c.power && chance() < 0.5 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        for (int x = QX0 + 2; x <= QX1 - 2; x++) {
            if (chance() < 0.08) {
                set(x, deck + 1, QZ + 3 + c.rng.nextInt(6), c.any("paper", "blood_splat", "glass_shards", "trash_bag[facing=" + c.dir() + "]"));
            }
        }
    }

    // ------------------------------------------------------------------ the dome

    private int domeY(double d) {
        if (d > ROUT) {
            return -1;
        }
        return DRUM + 1 + (int) Math.round(DOME_H * Math.sqrt(Math.max(0, 1 - (d / ROUT) * (d / ROUT))));
    }

    private void dome() {
        // the drum: the ring carried up from the roof with windows round it
        for (int x = RX - 17; x <= RX + 17; x++) {
            for (int z = RZ - 17; z <= RZ + 17; z++) {
                double d = r(x, z);
                if (d >= RIN && d < ROUT) {
                    double a = Math.atan2(z - RZ, x - RX);
                    int k = (int) Math.floor((a + Math.PI) / (Math.PI / 8));
                    boolean mid = Math.abs((a + Math.PI) - (k + 0.5) * Math.PI / 8) < 0.07;
                    for (int y = ROOF + 2; y <= ROOF + 4; y++) {
                        if (mid) {
                            String axis = Math.abs(Math.sin(a)) > Math.abs(Math.cos(a)) ? "x" : "z";
                            set(x, y, z, c.b.glass(axis));
                        }
                    }
                }
                if (d >= ROUT && d < ROUT + 1.2) {
                    double a = Math.atan2(z - RZ, x - RX);
                    String f = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "east" : "west") : (Math.sin(a) > 0 ? "south" : "north");
                    set(x, DRUM, z, "cv_cornice_stone[facing=" + f + "]");
                }
            }
        }
        // the shell: copper outside, the coffers under it, the oculus at the top
        for (int x = RX - 17; x <= RX + 17; x++) {
            for (int z = RZ - 17; z <= RZ + 17; z++) {
                double d = r(x, z);
                int top = domeY(d);
                if (top < 0) {
                    continue;
                }
                int lo = top;
                for (int[] n : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int t = domeY(r(x + n[0], z + n[1]));
                    lo = Math.min(lo, t < 0 ? DRUM + 1 : t + 1);
                }
                for (int y = lo; y <= top; y++) {
                    set(x, y, z, "cv_copper");
                }
                if (d < RIN - 0.5 && d >= 2.5) {
                    set(x, lo - 1, z, "us_coffer");
                }
                if (d < 2.5) {
                    set(x, top, z, "us_skylight");
                }
            }
        }
        // the lantern on the crown
        int crown = domeY(0);
        for (int[] o : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            set(RX + o[0], crown + 1, RZ + o[1], "cv_column_white");
        }
        for (int x = RX - 2; x <= RX + 2; x++) {
            for (int z = RZ - 2; z <= RZ + 2; z++) {
                set(x, crown + 2, z, Math.abs(x - RX) == 2 || Math.abs(z - RZ) == 2 ? "cv_copper_slab" : "cv_copper");
            }
        }
        set(RX, crown + 3, RZ, "cv_finial_c");
    }

    // ------------------------------------------------------------------ the front

    /** The doors in the pavilion's front behind the portico, the lamps and the plan inside them. */
    private void front() {
        for (int x : new int[] {99, 105, 111}) {
            c.fill(x, F0 + 1, MZ0, x + 1, F0 + 3, MZ0, "air");
            c.doors(x, F0, MZ0, "ext_door_oak", "north");
            set(x, F0 + 4, MZ0, "mu_lime");
            set(x + 1, F0 + 4, MZ0, "mu_lime");
        }
        for (int x : new int[] {97, 103, 109, 115}) {
            set(x, F0 + 3, MZ0 - 1, "fit_globe" + (c.power && chance() < 0.5 ? "_on" : "") + "[facing=north]");
        }
        // the service door at the east end onto the loading dock, the café's back door to the park
        c.fill(MX1, F0 + 1, 58, MX1, F0 + 3, 61, "air");
        c.door(116, F0, MZ1, "flat_door", "south");
        set(116, 3, MZ1 + 1, "cv_step_stone[facing=north]");
        set(116, 1, MZ1 + 1, "mu_rustic");
        set(116, 2, MZ1 + 1, "mu_rustic");
        set(116, 2, MZ1 + 2, "cv_step_stone[facing=north]");
        set(116, 1, MZ1 + 2, "mu_rustic");
        set(116, 1, MZ1 + 3, "cv_step_stone[facing=north]");
        set(117, F0 + 3, MZ1 + 1, "fit_globe[facing=south]");
        c.sign("staff", 120, F0 + 3, MZ1 + 1, "south");
    }
}
