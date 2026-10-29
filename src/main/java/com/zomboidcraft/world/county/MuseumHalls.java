package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Museum.BACK_Z;
import static com.zomboidcraft.world.county.Museum.F0;
import static com.zomboidcraft.world.county.Museum.F1;
import static com.zomboidcraft.world.county.Museum.HALL_Z;
import static com.zomboidcraft.world.county.Museum.MX0;
import static com.zomboidcraft.world.county.Museum.MX1;
import static com.zomboidcraft.world.county.Museum.MZ0;
import static com.zomboidcraft.world.county.Museum.MZ1;
import static com.zomboidcraft.world.county.Museum.PV0;
import static com.zomboidcraft.world.county.Museum.PV1;
import static com.zomboidcraft.world.county.Museum.PZ0;
import static com.zomboidcraft.world.county.Museum.PZ1;
import static com.zomboidcraft.world.county.Museum.RX;
import static com.zomboidcraft.world.county.Museum.RZ;
import static com.zomboidcraft.world.county.Museum.WZ0;

/**
 * The museum's halls. The west wing natural history: the gems and minerals at the end, their
 * cases smashed for what was in them; the hall of mammals between its dioramas, the bears in
 * their forest, the bison on the prairie, the wolves in the snow, the deer in the marsh; upstairs
 * the birds and the ocean hall with the blue whale hung under the lantern. The rotunda the
 * T. rex's. The east wing art: three galleries of paintings, half of them taken down for the
 * crates; shipping and receiving at the end, where the curators packed what they could for the
 * state archive and never sent it; upstairs Egypt under its lantern, the Greeks and Romans, arms
 * and armor. Over the entrance hall the conservation rooms the curators lived in at the end; the
 * store and the café behind the rotunda, American art over them.
 */
final class MuseumHalls {
    /** The storey, floor to floor. */
    private static final int OS = F1 - F0;
    /** The dioramas along the hall of mammals: their west ends, eight wide. */
    private static final int[] DIO = {55, 64, 73};
    private static final String[] NORTH_DIO = {"forest", "prairie", "winter"}, SOUTH_DIO = {"marsh", "winter", "forest"};
    /** The skeletons' cells, as tools/city/museum.py cells prints them: x,y,z;... */
    static final String REX = MuseumCells.REX, WHALE = MuseumCells.WHALE;

    private final CityPark c;

    MuseumHalls(CityPark c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    private double chance() {
        return c.chance();
    }

    private boolean empty(int x, int y, int z) {
        return c.empty(x, y, z);
    }

    void build() {
        gems(F0);
        mammals(F0);
        birds(F1);
        ocean(F1);
        rotunda();
        galleries(F0);
        shipping(F0);
        egypt(F1);
        classical(F1);
        arms(F1);
        entrance(F0);
        conservation(F1);
        back(F0);
        american(F1);
    }

    // ------------------------------------------------------------------ helpers

    private void pendants(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0 + 3; x <= x1 - 2; x += 7) {
            for (int z = z0 + 4; z <= z1 - 3; z += 9) {
                if (empty(x, y + OS - 1, z)) {
                    set(x, y + OS - 1, z, "ch_pendant" + (c.power && chance() < 0.35 ? "_lit" : ""));
                }
            }
        }
    }

    /** A painting cols wide (from ART) on a wall facing that way, its bottom at y, its label under it; now and then taken down. */
    private void painting(String key, int x0, int y, int z0, String facing) {
        int[] size = switch (key) {
            case "portrait", "lady" -> new int[] {2, 3};
            case "landscape", "storm" -> new int[] {3, 2};
            case "harbor" -> new int[] {4, 2};
            default -> new int[] {2, 2};
        };
        if (chance() < 0.3) {
            key = "gone";
            size = new int[] {2, 2};
        }
        c.picture("mu_art_" + key + "_", size[0], size[1], x0, y, z0, facing);
        int[] l = MallSigns.at(x0, z0, size[0], size[0] - 1, facing);
        if (!key.equals("gone")) {
            set(l[0], y - 1, l[1], "mu_label[facing=" + facing + "]");
        }
    }

    private static final String[] ART_KEYS = {"portrait", "landscape", "lady", "harbor", "still", "storm", "abstract"};

    private String artKey() {
        return ART_KEYS[c.rng.nextInt(ART_KEYS.length)];
    }

    /** Paintings along an inside wall at x, its face toward +x (east) or -x, from z0 to z1. */
    private void hangZ(int x, int z0, int z1, int y, boolean east) {
        for (int z = z0 + 1; z + 4 <= z1; z += 6) {
            painting(artKey(), east ? x + 1 : x - 1, y + 2, z, east ? "east" : "west");
        }
    }

    private void hangX(int z, int x0, int x1, int y, boolean south) {
        for (int x = x0 + 1; x + 4 <= x1; x += 6) {
            painting(artKey(), x, y + 2, south ? z + 1 : z - 1, south ? "south" : "north");
        }
    }

    private void vitrine(int x, int y, int z, String kind, double smashed) {
        if (chance() < smashed) {
            set(x, y + 1, z, "mu_vit_smashed");
            for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (chance() < 0.4 && empty(x + o[0], y + 1, z + o[1])) {
                    set(x + o[0], y + 1, z + o[1], "glass_shards");
                }
            }
        } else {
            set(x, y + 1, z, "mu_vit_" + kind);
        }
    }

    private void ottoman(int x, int y, int z, String axis) {
        set(x, y + 1, z, "mu_ottoman[axis=" + axis + "]");
    }

    private void bust(int x, int y, int z, String facing) {
        set(x, y + 1, z, "mu_plinth");
        set(x, y + 2, z, (chance() < 0.85 ? "mu_bust" : "debris_chunk") + "[facing=" + facing + "]");
    }

    /** A rope barrier round a box (corners left open for the posts' sake). */
    private void ropes(int x0, int z0, int x1, int z1, int y) {
        for (int x = x0 + 1; x < x1; x++) {
            if (chance() < 0.85) {
                set(x, y + 1, z0, "mu_stanchion[axis=x]");
            }
            if (chance() < 0.85) {
                set(x, y + 1, z1, "mu_stanchion[axis=x]");
            }
        }
        for (int z = z0 + 1; z < z1; z++) {
            if (chance() < 0.85) {
                set(x0, y + 1, z, "mu_stanchion[axis=z]");
            }
            if (chance() < 0.85) {
                set(x1, y + 1, z, "mu_stanchion[axis=z]");
            }
        }
    }

    private void cells(String list, String prefix, int ox, int oy, int oz) {
        for (String cell : list.split(";")) {
            String[] v = cell.split(",");
            int x = Integer.parseInt(v[0]), y = Integer.parseInt(v[1]), z = Integer.parseInt(v[2]);
            set(ox + x, oy + y, oz + z, prefix + x + "_" + y + "_" + z);
        }
    }

    // ------------------------------------------------------------------ natural history

    private void gems(int y) {
        int x0 = MX0 + 1, x1 = 49;
        c.partZ(50, WZ0 + 1, MZ1 - 1, y, OS, "mu_lime", "mu_lime");
        c.opening(50, 50, PZ0, PZ1, y, true);
        for (int z = 37; z <= MZ1 - 4; z += 3) {
            vitrine(41, y, z, z % 2 == 0 ? "gems" : "minerals", 0.45);
            vitrine(47, y, z, z % 3 == 0 ? "shells" : "minerals", 0.15);
        }
        for (int z = 40; z <= MZ1 - 6; z += 6) {
            set(44, y + 1, z, chance() < 0.4 ? "mu_table_smashed" : "mu_table_jewels");
        }
        c.sign("minerals", 51, y + 5, 46, "east");
        c.sign("minerals", x0 + 6, y + 5, WZ0 + 1, "south");
        set(x0, y + 1, MZ1 - 3, "mu_plinth");
        set(x0, y + 2, MZ1 - 3, "mu_dio_rock");
        pendants(x0, WZ0, x1, MZ1, y);
        c.mess(x0, WZ0 + 12, x1, MZ1 - 1, y, 0.05);
        // whoever came for the gems, and what happened to them
        set(44, y + 1, 52, "blood_splat");
        set(45, y + 1, 53, "home_blood_pool");
    }

    private void mammals(int y) {
        // the diorama walls along both sides, solid between the cases
        for (int x = 51; x <= 85; x++) {
            for (int yy = y + 1; yy < y + OS; yy++) {
                for (int z = WZ0 + 1; z <= WZ0 + 6; z++) {
                    set(x, yy, z, "mu_lime");
                }
                for (int z = MZ1 - 6; z < MZ1; z++) {
                    set(x, yy, z, "mu_lime");
                }
            }
        }
        for (int i = 0; i < DIO.length; i++) {
            diorama(DIO[i], y, true, NORTH_DIO[i]);
            diorama(DIO[i], y, false, SOUTH_DIO[i]);
        }
        // the big bear on its rock in the middle, the ropes round it, the benches facing it
        int bx = 68, bz = 50;
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                set(x, y + 1, z, "mu_dio_rock");
            }
        }
        set(bx, y + 2, bz, "mu_bear_lo[facing=south]");
        set(bx, y + 3, bz, "mu_bear_hi[facing=south]");
        ropes(bx - 3, bz - 3, bx + 3, bz + 3, y);
        for (int x : new int[] {bx - 6, bx + 6}) {
            ottoman(x, y, bz, "z");
        }
        for (int z : new int[] {38, 62}) {
            vitrine(58, y, z, "birds", 0.1);
            vitrine(78, y, z, "birds", 0.1);
        }
        c.sign("mammals", 52, y + 5, PZ0 + 2, "east");
        c.sign("mammals", 84, y + 5, PZ1 - 2, "west");
        pendants(51, WZ0 + 7, 85, MZ1 - 7, y);
        c.mess(52, WZ0 + 8, 84, MZ1 - 8, y, 0.03);
    }

    /**
     * A diorama eight wide from x0, in the north wall (north) or the south: the glass along the
     * hall, the ground raised a step behind it, the painted country on the back wall, the animals.
     */
    private void diorama(int x0, int y, boolean north, String key) {
        int back = north ? WZ0 : MZ1, glass = north ? WZ0 + 6 : MZ1 - 6, s = north ? 1 : -1;
        String look = north ? "south" : "north";
        for (int x = x0; x < x0 + 8; x++) {
            for (int z = back + s; z != glass; z += s) {
                set(x, y + 1, z, key.equals("winter") ? "mu_dio_snow" : "mu_dio_ground");
                for (int yy = y + 2; yy <= y + 5; yy++) {
                    set(x, yy, z, "air");
                }
                set(x, y + 6, z, (x - x0) % 3 == 1 && (z - back) * s == 3 ? "fit_troffer" + (c.power ? "_on" : "") : "mu_lime");
            }
            for (int yy = y + 2; yy <= y + 5; yy++) {
                set(x, yy, glass, chance() < 0.04 ? "air" : "mu_dio_glass[axis=x]");
            }
        }
        c.picture("mu_bd_" + key + "_", 4, 3, x0, y + 2, back + s, look);
        c.picture("mu_bd_" + key + "_", 4, 3, x0 + 4, y + 2, back + s, look);
        int z1 = back + 2 * s, z2 = back + 3 * s, z3 = back + 4 * s;
        String side = chance() < 0.5 ? "east" : "west";
        switch (key) {
            case "forest" -> {
                set(x0 + 2, y + 2, z2, "mu_bear_lo[facing=" + look + "]");
                set(x0 + 2, y + 3, z2, "mu_bear_hi[facing=" + look + "]");
                set(x0 + 5, y + 2, z3, "mu_fox[facing=" + side + "]");
                set(x0 + 6, y + 2, z1, "mu_dio_rock");
                set(x0 + 1, y + 2, z3, "mu_fern");
                set(x0 + 7, y + 2, z3, "mu_fern");
                set(x0 + 4, y + 2, z1, "mu_fern");
            }
            case "prairie" -> {
                set(x0 + 4, y + 2, z2, "mu_bison_0[facing=east]");
                set(x0 + 3, y + 2, z2, "mu_bison_1[facing=east]");
                set(x0 + 2, y + 2, z3, "mu_bison_0[facing=west]");
                set(x0 + 3, y + 2, z3, "mu_bison_1[facing=west]");
                set(x0 + 6, y + 2, z1, "mu_dio_rock");
            }
            case "winter" -> {
                set(x0 + 1, y + 2, z2, "mu_wolf[facing=" + side + "]");
                set(x0 + 4, y + 2, z3, "mu_wolf[facing=" + look + "]");
                set(x0 + 6, y + 2, z2, "mu_wolf[facing=west]");
                set(x0 + 3, y + 2, z1, "mu_dio_rock");
            }
            default -> {
                set(x0 + 3, y + 2, z2, "mu_deer_lo[facing=" + side + "]");
                set(x0 + 3, y + 3, z2, "mu_deer_hi[facing=" + side + "]");
                set(x0 + 6, y + 2, z3, "mu_fox[facing=" + look + "]");
                set(x0 + 1, y + 2, z1, "mu_dio_rock");
                set(x0 + 1, y + 3, z1, "mu_owl[facing=" + look + "]");
            }
        }
        // the label rail under the glass
        set(x0 + 4, y + 1, glass + s, "mu_label[facing=" + look + "]");
    }

    private void birds(int y) {
        int x0 = MX0 + 1, x1 = 49;
        c.partZ(50, WZ0 + 1, MZ1 - 1, y, OS, "mu_lime", "mu_lime");
        c.opening(50, 50, PZ0, PZ1, y, true);
        for (int z = 37; z <= MZ1 - 4; z += 4) {
            vitrine(41, y, z, "birds", 0.08);
            vitrine(47, y, z, chance() < 0.5 ? "birds" : "shells", 0.08);
        }
        set(44, y + 1, 45, "mu_plinth");
        set(44, y + 2, 45, "mu_eagle[facing=north]");
        set(44, y + 1, 65, "mu_plinth");
        set(44, y + 2, 65, "mu_owl[facing=north]");
        c.sign("birds", 51, y + 5, 46, "east");
        pendants(x0, WZ0, x1, MZ1, y);
        c.mess(x0, WZ0 + 12, x1, MZ1 - 1, y, 0.03);
    }

    /** The ocean hall: the blue whale hung from the lantern on its cables, the cases of shells and the fossils under it. */
    private void ocean(int y) {
        int ox = 56, oy = F1 + 6, oz = 46;
        cells(WHALE, "mu_whale_", ox, oy, oz);
        for (int x : new int[] {ox + 4, ox + 11, ox + 18}) {
            for (int yy = oy + 4; yy < Museum.LTOP; yy++) {
                set(x, yy, oz + 4, "mu_cable");
            }
        }
        for (int x = 56; x <= 80; x += 6) {
            vitrine(x, y, 30, "shells", 0.1);
            vitrine(x, y, 70, "shells", 0.1);
            set(x + 3, y + 1, 36, "mu_table_fossils");
            set(x + 3, y + 1, 64, "mu_table_fossils");
        }
        for (int x = 60; x <= 76; x += 8) {
            ottoman(x, y, 50, "x");
        }
        c.sign("ocean", 52, y + 5, PZ0 + 2, "east");
        c.sign("ocean", 84, y + 5, PZ1 - 2, "west");
        for (int x = 54; x <= 82; x += 7) {
            set(x, y + OS - 1, 26, "ch_pendant" + (c.power && chance() < 0.3 ? "_lit" : ""));
            set(x, y + OS - 1, 74, "ch_pendant");
        }
        c.mess(52, WZ0 + 1, 84, MZ1 - 1, y, 0.02);
    }

    // ------------------------------------------------------------------ the rotunda

    /** The T. rex striding east on its plinth, the ropes round it, the benches under the balcony. */
    private void rotunda() {
        int ox = RX - 8, oz = RZ - 1, y = F0 + 1;
        for (int x = ox; x <= ox + 15; x++) {
            for (int z = oz - 1; z <= oz + 3; z++) {
                set(x, y, z, "mu_dino_base");
            }
        }
        cells(REX, "mu_rex_", ox, y + 1, oz);
        ropes(ox - 2, oz - 3, ox + 17, oz + 5, F0);
        set(ox + 16, F0 + 1, oz + 6, "mu_label[facing=south]");
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4;
            int x = (int) Math.round(RX + Math.cos(a) * 13), z = (int) Math.round(RZ + Math.sin(a) * 13);
            set(x, F0 + 1, z, "cv_hall_bench[facing=" + (Math.abs(x - RX) > Math.abs(z - RZ) ? (x > RX ? "west" : "east") : (z > RZ ? "north" : "south")) + "]");
        }
        set(RX, F1 + 6, RZ - 13, "mu_vbanner_rex_0[facing=south]");
        c.mess(RX - 14, RZ - 14, RX + 14, RZ + 14, F0, 0.02);
    }

    // ------------------------------------------------------------------ art

    private void galleries(int y) {
        int[] walls = {140, 153, 165};
        for (int x : walls) {
            c.partZ(x, WZ0 + 1, MZ1 - 1, y, OS, "cv_wainscot", "cv_plaster");
        }
        c.opening(140, 140, 48, 52, y, true);
        c.opening(153, 153, 48, 52, y, true);
        c.door(165, y, 50, "flat_door", "east");
        c.sign("staff", 164, y + 4, 52, "west");
        int[][] rooms = {{PV1 + 1, 139}, {141, 152}, {154, 164}};
        for (int i = 0; i < rooms.length; i++) {
            int x0 = rooms[i][0], x1 = rooms[i][1], cx = (x0 + x1) / 2;
            // the walls between: both faces hung, either side of the doorway
            hangZ(x0 - 1, WZ0 + 1, 46, y, true);
            hangZ(x0 - 1, 54, MZ1 - 1, y, true);
            if (i < 2) {
                hangZ(x1 + 1, WZ0 + 1, 46, y, false);
                hangZ(x1 + 1, 54, MZ1 - 1, y, false);
            }
            ottoman(cx, y, 44, "z");
            ottoman(cx, y, 56, "z");
            bust(cx, y, 34, "south");
            if (i == 1) {
                set(cx, y + 1, 66, "ch_statue[facing=north]");
                set(cx, y + 2, 66, "ch_statue_top[facing=north]");
            }
            // the crates the paintings were going into, leaning on the walls
            for (int k = 0; k < 2; k++) {
                int z = 28 + c.rng.nextInt(44);
                if (empty(x0 + 1, y + 1, z) && (z < 47 || z > 53)) {
                    set(x0 + 1, y + 1, z, "mu_crate_flat[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
                }
            }
            pendants(x0, WZ0, x1, MZ1, y);
            c.mess(x0, WZ0 + 1, x1, MZ1 - 1, y, 0.02);
        }
        c.sign("painting", PV1 + 1, y + 6, PZ0 - 3, "east");
    }

    /** Shipping and receiving: the crates packed for the state archive and never sent, the dock door. */
    private void shipping(int y) {
        int x0 = 166, x1 = MX1 - 1;
        for (int z = 38; z <= 72; z += 4) {
            for (int x = x0 + 1; x <= x0 + 3; x++) {
                if (chance() < 0.8) {
                    set(x, y + 1, z, chance() < 0.25 ? "mu_crate_open[facing=" + c.dir() + "]" : "mu_crate[facing=" + c.dir() + "]");
                    if (chance() < 0.5) {
                        set(x, y + 2, z, "mu_crate[facing=" + c.dir() + "]");
                    }
                }
            }
        }
        for (int z = 40; z <= 48; z++) {
            set(x1 - 1, y + 1, z, "cv_table[axis=z]");
            if (chance() < 0.4) {
                set(x1 - 1, y + 2, z, "paper");
            }
        }
        set(x1 - 2, y + 1, 44, "office_chair[facing=east]");
        for (int z = 56; z <= 63; z++) {
            if (z < 58 || z > 61) {
                set(x1, y + 1, z, "st_pallet[facing=west]");
            }
        }
        c.picture("mu_board_crates_", 3, 1, x0, y + 3, 36, "east");
        c.sign("shipping", x1, y + 5, 56, "west");
        // the curator who stayed with them
        set(x1 - 3, y + 1, 70, "sc_cot_head[facing=north]");
        set(x1 - 3, y + 1, 71, "sc_cot_foot[facing=north]");
        set(x1 - 4, y + 1, 60, "dn_body_w[facing=" + c.dir() + "]");
        set(x1 - 5, y + 1, 60, "home_blood_pool");
        pendants(x0, 34, x1, MZ1, y);
        c.mess(x0, 34, x1, MZ1 - 1, y, 0.05);
    }

    private void egypt(int y) {
        c.partZ(147, WZ0 + 1, MZ1 - 1, y, OS, "mu_lime", "mu_lime");
        c.opening(147, 147, PZ0, PZ1, y, true);
        int cx = 137;
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = 47; z <= 52; z++) {
                set(x, y + 1, z, "ch_ashlar");
            }
        }
        set(cx, y + 2, 49, "mu_sarc_0[facing=north]");
        set(cx, y + 2, 50, "mu_sarc_1[facing=north]");
        ropes(cx - 3, 45, cx + 3, 54, y);
        for (int z = 30; z <= 70; z += 8) {
            set(PV1 + 1, y + 1, z, "mu_coffin_lo[facing=east]");
            set(PV1 + 1, y + 2, z, "mu_coffin_hi[facing=east]");
        }
        for (int x = 130; x <= 142; x += 6) {
            c.picture("mu_hiero_", 4, 2, x, y + 3, WZ0 + 1, "south");
            c.picture("mu_hiero_", 4, 2, x, y + 3, MZ1 - 1, "north");
        }
        for (int z = 34; z <= 66; z += 8) {
            vitrine(144, y, z, "jars", 0.2);
            set(132, y + 1, z + 2, "mu_plinth");
            set(132, y + 2, z + 2, "mu_canopic");
        }
        c.sign("egypt", 148, y + 5, 46, "east");
        c.sign("egypt", cx - 1, y + 5, WZ0 + 1, "south");
        c.mess(PV1 + 1, WZ0 + 1, 146, MZ1 - 1, y, 0.02);
    }

    private void classical(int y) {
        c.partZ(165, WZ0 + 1, MZ1 - 1, y, OS, "mu_lime", "mu_lime");
        c.opening(165, 165, PZ0, PZ1, y, true);
        for (int z = 30; z <= 70; z += 5) {
            if (z >= PZ0 - 1 && z <= PZ1 + 1) {
                continue;
            }
            set(150, y + 1, z, "mu_plinth");
            set(150, y + 2, z, chance() < 0.5 ? "mu_amphora" : "mu_urn");
            vitrine(162, y, z, "pots", 0.15);
        }
        for (int z : new int[] {36, 64}) {
            bust(156, y, z, "east");
        }
        set(157, y + 1, 50, "ch_statue[facing=west]");
        set(157, y + 2, 50, "ch_statue_top[facing=west]");
        pendants(148, WZ0, 164, MZ1, y);
        c.mess(148, WZ0 + 1, 164, MZ1 - 1, y, 0.02);
    }

    private void arms(int y) {
        int x0 = 166, x1 = MX1 - 1;
        for (int z = 38; z <= 72; z += 4) {
            set(x0 + 1, y + 1, z, "mu_armour_lo[facing=east]");
            set(x0 + 1, y + 2, z, "mu_armour_hi[facing=east]");
            if (chance() < 0.7) {
                set(x1, y + 3, z, "mu_swords[facing=west]");
            }
            if (chance() < 0.6) {
                set(x1, y + 3, z + 2, "mu_shield[facing=west]");
            }
        }
        for (int z = 44; z <= 64; z += 10) {
            vitrine(x0 + 6, y, z, "smashed", 0.6);
        }
        c.sign("arms", x0, y + 5, 46, "east");
        pendants(x0, 34, x1, MZ1, y);
        c.mess(x0, 34, x1, MZ1 - 1, y, 0.03);
    }

    // ------------------------------------------------------------------ the pavilion

    private void entrance(int y) {
        // the marble star in the middle of the hall, the desks either side, the plan by the doors
        for (int i = 0; i < 25; i++) {
            set(RX - 2 + i % 5, y, 24 + i / 5, "mu_rose_" + i);
        }
        for (int x = 90; x <= 97; x++) {
            set(x, y + 1, 29, "un_circ[facing=north]");
        }
        set(93, y + 2, 29, "pk_till_1[facing=north]");
        c.sign("admissions", 92, y + 5, HALL_Z - 1, "north");
        for (int x = 115; x <= 122; x++) {
            set(x, y + 1, 29, "un_circ[facing=north]");
        }
        c.sign("coat", 117, y + 5, HALL_Z - 1, "north");
        for (int x = 116; x <= 121; x++) {
            if (chance() < 0.6) {
                set(x, y + 1, 32, "store_shelf_thrift[facing=north]");
            }
        }
        c.picture("mu_plan_", 2, 1, PV0 + 1, y + 3, 24, "east");
        set(PV0 + 1, y + 1, 28, "home_plant_dead");
        set(PV1 - 1, y + 1, 28, "home_plant_dead");
        for (int x : new int[] {96, 116}) {
            set(x, y + OS - 1, 25, "ch_chandelier" + (c.power ? "_lit" : ""));
        }
        // the barricade they built against the doors, pulled apart again
        for (int x = 96; x <= 116; x++) {
            if ((x < 98 || x > 101) && (x < 104 || x > 107) && (x < 110 || x > 113) && chance() < 0.5) {
                set(x, y + 1, MZ0 + 3, chance() < 0.5 ? "mu_vit_smashed" : "mu_ottoman[axis=x]");
            }
        }
        c.mess(PV0 + 1, MZ0 + 1, PV1 - 1, HALL_Z - 1, y, 0.04);
    }

    /** Over the entrance hall: the conservation rooms where the curators lived the last weeks. */
    private void conservation(int y) {
        for (int x = 92; x <= 120; x += 7) {
            for (int z = 23; z <= 26; z++) {
                set(x, y + 1, z, "cv_table[axis=z]");
                if (chance() < 0.4) {
                    set(x, y + 2, z, chance() < 0.5 ? "paper" : "home_books_stack_low");
                }
            }
        }
        for (int x = 90; x <= 100; x += 3) {
            set(x, y + 1, 31, "sc_cot_head[facing=north]");
            set(x, y + 1, 32, "sc_cot_foot[facing=north]");
        }
        set(118, y + 1, 32, "bunk_radio_desk[facing=north]");
        set(118, y + 1, 31, "bunk_chair[facing=south]");
        set(121, y + 1, 32, "bunk_jerry_cans[facing=north]");
        set(122, y + 1, 32, "bunk_crate_ration[facing=north]");
        set(110, y + 1, 32, "fit_burner[facing=north]");
        for (int x = 102; x <= 108; x++) {
            if (chance() < 0.6) {
                set(x, y + 1, 20, "mu_crate[facing=south]");
            }
        }
        c.sign("conservation", Museum.PX0 + 3, y + 5, HALL_Z - 1, "north");
        pendants(PV0 + 1, MZ0 + 1, PV1 - 1, HALL_Z - 1, y);
        c.mess(PV0 + 1, MZ0 + 1, PV1 - 1, HALL_Z - 1, y, 0.06);
    }

    /** Behind the rotunda: the museum store west, the café east, their wall half way. */
    private void back(int y) {
        c.partZ(RX, BACK_Z + 4, MZ1 - 1, y, OS, "cv_wainscot", "cv_plaster");
        for (int x = PV0 + 2; x <= RX - 3; x += 3) {
            for (int z = BACK_Z + 5; z <= MZ1 - 2; z++) {
                set(x, y + 1, z, chance() < 0.3 ? "store_shelf_fallen[facing=east]" : "store_shelf_thrift[facing=east]");
            }
        }
        set(RX - 1, y + 1, BACK_Z + 2, "pk_till_1[facing=north]");
        c.sign("store", RX - 4, y + 5, MZ1 - 1, "north");
        for (int x = RX + 3; x <= PV1 - 4; x += 4) {
            for (int z : new int[] {BACK_Z + 4, BACK_Z + 8}) {
                set(x, y + 1, z, "sc_cafe_table[axis=x]");
                set(x - 1, y + 1, z, chance() < 0.2 ? "home_chair_tipped[facing=" + c.dir() + "]" : "home_chair[facing=east]");
                set(x + 1, y + 1, z, "home_chair[facing=west]");
            }
        }
        set(PV1 - 1, y + 1, BACK_Z + 2, "dn_brewer[facing=west]");
        set(PV1 - 1, y + 1, BACK_Z + 3, "pk_fridge[facing=west]");
        c.sign("cafe", PV1 - 1, y + 5, BACK_Z + 5, "west");
        pendants(PV0 + 1, BACK_Z + 1, PV1 - 1, MZ1 - 1, y);
        c.mess(PV0 + 1, BACK_Z + 1, PV1 - 1, MZ1 - 1, y, 0.04);
    }

    private void american(int y) {
        hangX(MZ1, PV0 + 1, PV1 - 1, y, false);
        hangZ(PV0, BACK_Z + 1, MZ1 - 1, y, true);
        hangZ(PV1, BACK_Z + 1, MZ1 - 1, y, false);
        for (int x = 96; x <= 116; x += 10) {
            ottoman(x, y, 71, "x");
        }
        for (int k = 0; k < 4; k++) {
            int x = PV0 + 3 + c.rng.nextInt(PV1 - PV0 - 6);
            if (empty(x, y + 1, MZ1 - 2) && (x < Museum.PX0 || x > Museum.PX1)) {
                set(x, y + 1, MZ1 - 2, "mu_crate_flat[facing=north]");
            }
        }
        pendants(PV0 + 1, BACK_Z + 1, PV1 - 1, MZ1 - 1, y);
        c.mess(PV0 + 1, BACK_Z + 1, PV1 - 1, MZ1 - 1, y, 0.02);
    }
}
