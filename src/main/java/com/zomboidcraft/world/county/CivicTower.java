package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.CivicCentre.CORE_X0;
import static com.zomboidcraft.world.county.CivicCentre.CORE_X1;
import static com.zomboidcraft.world.county.CivicCentre.CORE_Z0;
import static com.zomboidcraft.world.county.CivicCentre.CORE_Z1;
import static com.zomboidcraft.world.county.CivicCentre.FLOORS;
import static com.zomboidcraft.world.county.CivicCentre.GLASS_Z;
import static com.zomboidcraft.world.county.CivicCentre.ROOF;
import static com.zomboidcraft.world.county.CivicCentre.S;
import static com.zomboidcraft.world.county.CivicCentre.TX0;
import static com.zomboidcraft.world.county.CivicCentre.TX1;
import static com.zomboidcraft.world.county.CivicCentre.TZ0;
import static com.zomboidcraft.world.county.CivicCentre.TZ1;

import com.zomboidcraft.world.city.Floorplan;
import com.zomboidcraft.world.city.Floors;

/**
 * Grayhaven City Hall: the slab tower of white precast over the plaza, twelve floors and the
 * roof, its fins running the full height between the bronze windows, the colonnade under it.
 *
 * <p>The ground floor is the public's: the glass front behind the columns, the guards' lanes
 * with their detectors, the city clerk's counter down the west side and the tax collector's down
 * the east under NOW SERVING, the model of downtown in its case, the mail room, the restrooms
 * and the service door at the back. Above it the departments a floor each, as the directory by
 * the lifts lists them (permits, the assessor, records, public works, planning, data
 * processing, the staff cafeteria, personnel, the city attorney, the mayor's floor), fitted out
 * by {@link Floors}; the top floor the emergency operations center the mayor ran the last weeks
 * from: the radios, the map tables, the quarantine map on the core, the cots behind sandbags.
 * The helipad on the roof.
 *
 * <p>The core in the middle: two fire stairs and two lifts, every floor's landing in front of
 * them along its north side.
 */
final class CivicTower {
    /** The stairs' shafts (x0, door north at z = CORE_Z0) and the lifts' cars (x0, z0, 3 by 3). */
    static final int SA = 84, SB = 104, LA = 91, LB = 99, LZ = 81;
    /** The departments, floor 1 up to 10 (11 is the operations center). */
    private static final Floors.Type[] PROGRAM = {null, Floors.Type.GOVERNMENT, Floors.Type.GOVERNMENT, Floors.Type.RECORDS,
            Floors.Type.CELLULAR, Floors.Type.OPEN_OFFICE, Floors.Type.COMPUTERS, Floors.Type.CAFETERIA, Floors.Type.CELLULAR,
            Floors.Type.LAW, Floors.Type.EXECUTIVE};
    private static final String[] SIGN = {"info", "permits", "assessor", "records", "works", "planning", "data", "cafeteria",
            "personnel", "attorney", "mayor", "eoc"};

    private final CivicCentre c;

    CivicTower(CivicCentre c) {
        this.c = c;
    }

    void build() {
        slabs();
        facade();
        core();
        lobby();
        for (int k = 1; k < FLOORS - 1; k++) {
            office(k);
        }
        operations(FLOORS - 1);
        roof();
    }

    static int[] levels() {
        int[] ys = new int[FLOORS + 1];
        for (int k = 0; k <= FLOORS; k++) {
            ys[k] = k * S;
        }
        return ys;
    }

    static boolean inCore(int x, int z) {
        return x >= CORE_X0 && x <= CORE_X1 && z >= CORE_Z0 && z <= CORE_Z1;
    }

    // ------------------------------------------------------------------ the shell

    private void slabs() {
        for (int k = 0; k <= FLOORS; k++) {
            int y = k * S;
            for (int x = TX0 + 1; x < TX1; x++) {
                for (int z = TZ0 + 1; z < TZ1; z++) {
                    c.set(x, y, z, k == 0 ? "tx_terrazzo" : k == FLOORS ? "un_roof" : "floor_concrete");
                }
            }
        }
        // the colonnade's floor under the tower's front, the granite run in under it
        for (int x = TX0; x <= TX1; x++) {
            for (int z = TZ0; z < GLASS_Z; z++) {
                c.set(x, 0, z, c.chance() < 0.15 ? "cc_plaza_b" : "cc_plaza");
            }
        }
    }

    /** Is (x, z) on the wall line a pier (every third block from the corner)? */
    private static boolean pier(int x, int z) {
        return (z == TZ0 || z == TZ1) ? (x - TX0) % 3 == 0 : (z - TZ0) % 3 == 0;
    }

    private void facade() {
        // the ground floor: travertine down the sides and back, the columns at the front
        for (int y = 1; y < S; y++) {
            for (int z = GLASS_Z; z <= TZ1; z++) {
                c.set(TX0, y, z, "cc_travertine");
                c.set(TX1, y, z, "cc_travertine");
            }
            for (int x = TX0; x <= TX1; x++) {
                c.set(x, y, TZ1, "cc_travertine");
            }
            for (int x = TX0; x <= TX1; x += 6) {
                c.set(x, y, TZ0, "cc_precast");
            }
        }
        // the floors above: piers through, the spandrel at the slab and under the ceiling, the windows between
        int[][] sides = {{TX0, TZ0, TX1, TZ0, 0}, {TX0, TZ1, TX1, TZ1, 2}, {TX0, TZ0, TX0, TZ1, 3}, {TX1, TZ0, TX1, TZ1, 1}};
        String[] out = {"north", "east", "south", "west"};
        int[][] step = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] s : sides) {
            int o = s[4];
            for (int x = s[0]; x <= s[2]; x++) {
                for (int z = s[1]; z <= s[3]; z++) {
                    boolean corner = (x == TX0 || x == TX1) && (z == TZ0 || z == TZ1);
                    boolean pier = corner || pier(x, z);
                    for (int y = S; y < ROOF; y++) {
                        int r = y % S;
                        String b;
                        if (pier) {
                            b = c.chance() < 0.2 ? "cc_precast_b" : "cc_precast";
                        } else if (r == 0 || r == 4) {
                            b = "cc_spandrel";
                        } else {
                            double q = c.chance();
                            boolean low = y < 3 * S;
                            b = "cc_win" + (q < (low ? 0.16 : 0.07) ? "_broken" : c.power && q > 0.93 ? "_lit" : "") + "[facing=" + out[o] + "]";
                        }
                        c.set(x, y, z, b);
                    }
                    // the parapet: three of precast over the roof, the coping on top
                    for (int y = ROOF; y <= ROOF + 3; y++) {
                        c.set(x, y, z, y == ROOF + 3 ? "cc_coping" : c.chance() < 0.3 ? "cc_precast_b" : "cc_precast");
                    }
                    // the fins stand out from the interior piers, up through the parapet
                    if (pier && !corner) {
                        int fx = x + step[o][0], fz = z + step[o][1];
                        int from = S + 1;
                        if (o == 0 && x >= 86 && x <= 106) {
                            from = 2 * S;  // over the name on the front
                        }
                        int to = ROOF + 2;
                        if (o == 0 && x >= 93 && x <= 99) {
                            to = ROOF - 2;  // under the seal
                        }
                        for (int y = from; y <= to; y++) {
                            c.set(fx, y, fz, "cc_fin[facing=" + out[o] + "]");
                        }
                        c.set(fx, to + 1, fz, "cc_fin_cap[facing=" + out[o] + "]");
                    }
                }
            }
        }
        // the name along the front over the colonnade, the seal on the parapet at the top
        c.row("cc_name_hall_", 16, 88, S, TZ0 - 1, "north", 0.03);
        c.picture("cc_seal_", 3, 3, 95, ROOF + 1, TZ0 - 1, "north");
        // the lobby's glass front behind the columns, the two pairs of doors
        for (int x = TX0 + 1; x < TX1; x++) {
            for (int y = 1; y < S; y++) {
                c.set(x, y, GLASS_Z, y == S - 1 ? "cc_spandrel" : c.chance() < 0.12 ? "tx_glass_broken[axis=x]" : "tx_glass[axis=x]");
            }
        }
        c.doors(92, 0, GLASS_Z, "shop_door", "north");
        c.doors(99, 0, GLASS_Z, "shop_door", "north");
        // the colonnade's soffit lights
        for (int x = TX0 + 3; x < TX1; x += 6) {
            c.set(x, S - 1, TZ0 + 1, c.power && c.chance() < 0.5 ? "fit_troffer_on" : "fit_troffer");
        }
    }

    /** The core: solid round its two stairs and two lifts, travertine where the lobby sees it. */
    private void core() {
        for (int x = CORE_X0; x <= CORE_X1; x++) {
            for (int z = CORE_Z0; z <= CORE_Z1; z++) {
                for (int y = 1; y < ROOF; y++) {
                    c.set(x, y, z, y < S ? "cc_travertine" : y % S == 0 ? "floor_concrete" : "drywall");
                }
            }
        }
        int[] ys = levels();
        for (int sx : new int[] {SA, SB}) {
            c.fill(sx + 1, 1, CORE_Z0 + 1, sx + 3, ROOF + 4, CORE_Z0 + 8, "air");
            c.stair(sx, CORE_Z0, false, ys, "concrete_dark", "un_roof");
        }
        for (int lx : new int[] {LA, LB}) {
            c.fill(lx, 1, LZ, lx + 2, ROOF + 4, LZ + 2, "air");
            c.lift(lx, LZ, 3, 3, ys, "concrete_dark", ROOF + 2);
        }
        // the shafts' faces where the floors see them: travertine in the lobby, drywall above
        for (int y = 1; y < ROOF; y++) {
            if (y % S == 0) {
                continue;
            }
            String skin = y < S ? "cc_travertine" : "drywall";
            for (int x = CORE_X0; x <= CORE_X1; x++) {
                for (int z : new int[] {CORE_Z0, CORE_Z1}) {
                    if ("concrete_dark".equals(c.get(x, y, z))) {
                        c.set(x, y, z, skin);
                    }
                }
            }
            for (int z = CORE_Z0; z <= CORE_Z1; z++) {
                for (int x : new int[] {CORE_X0, CORE_X1}) {
                    if ("concrete_dark".equals(c.get(x, y, z))) {
                        c.set(x, y, z, skin);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the ground floor

    private void lobby() {
        int y = 0;
        // the side corridors either side of the core, the corridor across behind it
        int wW = CORE_X0 - 3, wE = CORE_X1 + 3;  // the clerk's and the collector's office walls
        c.partZ(wW, CORE_Z0, TZ1 - 1, y, S, "cc_travertine", "cc_travertine");
        c.partZ(wE, CORE_Z0, TZ1 - 1, y, S, "cc_travertine", "cc_travertine");
        c.door(wW, y, 86, "flat_door", "east");
        c.door(wE, y, 86, "flat_door", "west");
        counter(TX0 + 1, wW - 1, "clerk", true);
        counter(wE + 1, TX1 - 1, "tax", false);
        // behind the core: the corridor across at z 90 .. 91, the rooms off it
        int rz = CORE_Z1 + 3;
        c.partX(wW + 1, wE - 1, rz, y, S, "drywall", "drywall");
        int[] cuts = {CORE_X0 + 4, 92, 100, 104, wE};
        String[] rooms = {"wc_m", "wc_w", "mail", "security", "store"};
        int x0 = wW + 1;
        for (int i = 0; i < cuts.length; i++) {
            int x1 = cuts[i] - 1;
            if (i < cuts.length - 1) {
                c.partZ(cuts[i], rz + 1, TZ1 - 1, y, S, "drywall", "drywall");
            }
            int dx = (x0 + x1) / 2;
            c.door(dx, y, rz, rooms[i].startsWith("wc") ? "flat_door" : rooms[i].equals("store") ? "fire_door" : "flat_door", "north");
            backRoom(rooms[i], x0, rz + 1, x1, TZ1 - 1);
            x0 = cuts[i] + 1;
        }
        // the service door out the back, from the store
        c.doors(CORE_X1 - 2, y, TZ1, "fire_door", "south");
        // the doors west to the concourse and the council chamber
        c.doors(TX0, y, 75, "shop_door", "west");
        // the guards' lanes inside the doors: the detector and the belt, the desk between
        for (int x : new int[] {93, 99}) {
            c.set(x, 1, 76, "cc_detector_lo[facing=north]");
            c.set(x, 2, 76, "cc_detector_hi[facing=north]");
        }
        c.set(92, 1, 76, "cc_xray[facing=" + (c.chance() < 0.85 ? "east" : "south") + "]");
        c.set(100, 1, 76, "cc_xray[facing=west]");
        for (int x = 95; x <= 97; x++) {
            c.set(x, 1, 76, "reception_desk[facing=north]");
        }
        c.set(96, 1, 77, "office_chair[facing=north]");
        c.set(96, 2, CORE_Z0 - 1, "cv_directory[facing=north]");
        c.sign("info", 95, 3, CORE_Z0 - 1, "north");
        // the model of downtown in its case, the flags by the doors
        c.set(104, 1, 75, "cc_model");
        c.set(105, 1, 75, "cc_model_b");
        c.set(104, 1, 76, "cc_model_b");
        c.set(105, 1, 76, "cc_model");
        c.set(TX0 + 2, 1, 74, "cv_flag_us[facing=south]");
        c.set(TX0 + 2, 2, 74, "cv_flag_us_top[facing=south]");
        c.set(TX0 + 3, 1, 74, "cc_flag_city[facing=south]");
        c.set(TX0 + 3, 2, 74, "cc_flag_city_top[facing=south]");
        // planters and benches along the glass, the honour roll, past mayors on the core
        for (int x = TX0 + 6; x < TX1 - 3; x += 8) {
            if (x >= 90 && x <= 102) {
                continue;
            }
            c.set(x, 1, GLASS_Z + 1, c.chance() < 0.6 ? "pk_planter" : "home_plant_dead");
        }
        c.set(89, 2, CORE_Z0 - 1, "cv_portrait[facing=north]");
        c.set(103, 2, CORE_Z0 - 1, "cv_portrait_b[facing=north]");
        c.set(CORE_X0 - 1, 2, 84, "cc_proclamation[facing=west]");
        c.set(CORE_X1 + 1, 2, 84, "cc_proclamation[facing=east]");
        // the troffers in the ceiling, the litter
        ceiling(y, TX0 + 1, GLASS_Z + 1, TX1 - 1, TZ1 - 1, c.power ? 0.4 : 0.0);
        c.mess(TX0 + 1, GLASS_Z + 1, TX1 - 1, TZ1 - 1, y, 0.05);
    }

    /**
     * A public counter across the office's front at z = CORE_Z0 from x0 to x1, the grilles in it,
     * the bulkhead over it with the sign, NOW SERVING and the ticket post, the benches facing it;
     * behind it the clerks' desks, files and (the tax side) the vault.
     */
    private void counter(int x0, int x1, String key, boolean west) {
        int z = CORE_Z0, y = 0;
        for (int x = x0; x <= x1; x++) {
            boolean gate = west ? x == x1 : x == x0;
            if (gate) {
                c.set(x, 1, z, "air");
                c.set(x, 2, z, "air");
            } else {
                c.set(x, 1, z, "cv_po_counter[facing=north]");
                c.set(x, 2, z, (x - x0) % 2 == 0 ? "cv_po_grille[facing=north]" : "air");
            }
            c.set(x, 3, z, "cc_travertine");
            c.set(x, 4, z, "cc_travertine");
        }
        int mid = (x0 + x1) / 2;
        c.sign(key, mid - 1, 3, z - 1, "north");
        if (!west) {
            c.sign("cashier", x0, 3, z - 1, "north");
        }
        c.set(west ? x0 : x1, 3, z - 1, (c.power ? "cc_serving_on" : "cc_serving") + "[facing=north]");
        c.set(west ? x0 + 1 : x1 - 1, 1, z - 3, "cc_ticket[facing=north]");
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            if (c.chance() < 0.8) {
                c.set(x, 1, z - 4, (c.chance() < 0.9 ? "cv_hall_bench" : "debris_chunk") + "[facing=south]");
            }
        }
        // behind it: the clerks' desks in rows facing the counter, the files along the back, the vault
        for (int dz = z + 3; dz <= TZ1 - 5; dz += 3) {
            for (int x = x0 + 1; x <= x1 - 1; x += 3) {
                c.set(x, 1, dz, "desk[facing=north]");
                c.set(x, 1, dz + 1, c.chance() < 0.75 ? "office_chair[facing=north]" : "office_chair_fallen[facing=" + c.dir() + "]");
            }
        }
        for (int x = x0; x <= x1; x++) {
            if (!west && x >= x1 - 3) {
                continue;
            }
            c.set(x, 1, TZ1 - 1, c.chance() < 0.2 ? "filing_cabinet[facing=north]" : "pk_file_cabinet[facing=north]");
        }
        if (!west) {
            c.partX(x1 - 4, x1, TZ1 - 4, y, S, "concrete_dark", "concrete_dark");
            c.partZ(x1 - 4, TZ1 - 3, TZ1 - 1, y, S, "concrete_dark", "concrete_dark");
            c.set(x1 - 2, 1, TZ1 - 4, "trade_vault_door[facing=north]");
            c.set(x1 - 2, 2, TZ1 - 4, "trade_vault_door_top[facing=north]");
            c.set(x1 - 1, 1, TZ1 - 2, "store_safe[facing=west]");
            c.set(x1 - 3, 1, TZ1 - 1, "cardboard_box[facing=north]");
        } else {
            c.set(x0, 3, z + 4, "cv_portrait[facing=east]");
            c.set(x0, 2, z + 8, "home_calendar[facing=east]");
        }
        c.mess(x0, z + 1, x1, TZ1 - 1, y, 0.1);
    }

    private void backRoom(String kind, int x0, int z0, int x1, int z1) {
        switch (kind) {
            case "wc_m", "wc_w" -> {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        c.set(x, 0, z, "hs_tile");
                    }
                }
                for (int x = x0; x <= x1 - 1; x++) {
                    c.set(x, 1, z1, "fit_toilet_stall[facing=north]");
                }
                c.set(x1, 1, z0 + 1, "hs_sink[facing=west]");
                c.set(x1, 1, z0 + 2, "hs_sink[facing=west]");
                c.set(x1, 2, z0 + 1, "home_mirror[facing=west]");
            }
            case "mail" -> {
                for (int z = z0 + 1; z <= z1; z++) {
                    c.set(x1, 1, z, "cv_sorting_case[facing=west]");
                }
                c.set(x0 + 1, 1, z0 + 2, "cv_mail_hamper");
                c.set(x0 + 2, 1, z0 + 4, "cv_mail_sack");
                c.set(x0 + 1, 1, z1, "cv_mail_sack");
            }
            case "security" -> {
                c.set(x0, 1, z0 + 1, "desk[facing=east]");
                c.set(x0 + 1, 1, z0 + 1, "office_chair[facing=west]");
                c.set(x0, 1, z0 + 3, "desk[facing=east]");
                c.set(x0, 2, z0 + 3, "pk_tv_crt[facing=east]");
                c.set(x0, 2, z0 + 1, "pk_tv_crt[facing=east]");
                c.set(x1, 1, z1, "hs_locker[facing=west]");
                c.set(x1, 2, z1, "hs_locker_top[facing=west]");
            }
            default -> {
                for (int z = z0 + 1; z <= z1 - 2; z += 2) {
                    c.set(x0, 1, z, "pk_garage_shelves[facing=east]");
                }
                c.set(x1 - 1, 1, z0 + 2, "cardboard_box[facing=north]");
                c.set(x1 - 1, 2, z0 + 2, "cardboard_box[facing=east]");
                c.set(x1 - 2, 1, z0 + 3, "st_pallet_cots");
            }
        }
    }

    /** The suspended ceiling one under the slab over a floor at y: tiles, a troffer every third, some lit. */
    private void ceiling(int y, int x0, int z0, int x1, int z1, double on) {
        int cy = y + S - 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (inCore(x, z) || !c.empty(x, cy, z)) {
                    continue;
                }
                boolean lamp = (x - x0) % 3 == 1 && (z - z0) % 3 == 1;
                double r = c.chance();
                c.set(x, cy, z, lamp ? (r < on ? "fit_troffer_on" : r < on + 0.05 ? "fit_troffer_flicker" : "fit_troffer")
                        : r < 0.04 ? "fit_ceiling_fallen" : r < 0.08 ? "fit_ceiling_open" : "fit_ceiling");
            }
        }
    }

    // ------------------------------------------------------------------ the departments

    private void office(int k) {
        int y = k * S;
        Floorplan fp = new Floorplan(c.p, c.rng, TX1 + 1, TZ1 + 1, y, S - 1, new Floorplan.Site() {
            @Override
            public boolean open(int x, int z) {
                return x > TX0 && x < TX1 && z > TZ0 && z < TZ1 && !inCore(x, z) && !keep(x, z);
            }

            @Override
            public boolean core(int x, int z) {
                return inCore(x, z);
            }

            @Override
            public boolean keep(int x, int z) {
                return z >= CORE_Z0 - 3 && z < CORE_Z0 && x >= CORE_X0 && x <= CORE_X1;
            }
        }, c.power ? 0.5 : 0.03);
        Floors.fit(fp, PROGRAM[k], c.rng);
        ceiling(y, TX0 + 1, TZ0 + 1, TX1 - 1, TZ1 - 1, c.power ? 0.35 : 0.0);
        c.sign(SIGN[k], 95, y + 3, CORE_Z0 - 1, "north");
        c.set(89, y + 2, CORE_Z0 - 1, "cc_proclamation[facing=north]");
    }

    /**
     * The emergency operations center on the top floor: the map tables up the middle of the north
     * side, the radios along the windows, the quarantine map on the core, the communications room
     * west, the chief's office east, the cots behind the sandbags along the south.
     */
    private void operations(int k) {
        int y = k * S;
        for (int x = TX0 + 1; x < TX1; x++) {
            for (int z = TZ0 + 1; z < TZ1; z++) {
                if (!inCore(x, z)) {
                    c.set(x, y, z, z > CORE_Z1 ? "bunk_floor_dirty" : "sc_vct");
                }
            }
        }
        int pz = CORE_Z1 + 3;
        // the partitions: the dormitory behind z = pz, the two rooms either side of the core
        c.partX(TX0 + 1, TX1 - 1, pz, y, S, "drywall", "drywall");
        c.partX(TX0 + 1, CORE_X0 - 1, CORE_Z0 - 1, y, S, "drywall", "drywall");
        c.partX(CORE_X1 + 1, TX1 - 1, CORE_Z0 - 1, y, S, "drywall", "drywall");
        for (int x : new int[] {78, 96, 114}) {
            c.opening(x, x + 1, pz, pz, y, false);
        }
        c.door(78, y, CORE_Z0 - 1, "flat_door", "north");
        c.door(114, y, CORE_Z0 - 1, "flat_door", "north");
        // the operations room: map tables and chairs, the radios along the north windows
        for (int[] t : new int[][] {{78, 81}, {94, 98}, {110, 113}}) {
            for (int x = t[0]; x <= t[1]; x++) {
                for (int z = 73; z <= 74; z++) {
                    c.set(x, y + 1, z, "bunk_map_table");
                }
                if (c.chance() < 0.7) {
                    c.set(x, y + 1, 75, (c.chance() < 0.8 ? "bunk_chair" : "trade_folding_chair_tipped") + "[facing=north]");
                }
            }
        }
        for (int x = TX0 + 2; x < TX1 - 1; x += 3) {
            c.set(x, y + 1, TZ0 + 1, (c.chance() < 0.85 ? "bunk_radio_desk" : "bunk_rack_boxes") + "[facing=south]");
            if (c.chance() < 0.6) {
                c.set(x, y + 1, TZ0 + 2, "bunk_chair[facing=north]");
            }
        }
        c.picture("cc_qmap_", 4, 3, 94, y + 1, CORE_Z0 - 1, "north");
        c.sign("eoc", 89, y + 3, CORE_Z0 - 1, "north");
        for (int x : new int[] {TX0 + 1, TX1 - 1}) {
            c.set(x, y + 3, 76, (c.power ? "bunk_lamp_red_lit" : "bunk_lamp_red") + "[facing=" + (x == TX0 + 1 ? "east" : "west") + "]");
        }
        // the communications room west: radio racks along the core, the generator panel
        for (int z = CORE_Z0 + 1; z < CORE_Z1; z++) {
            c.set(CORE_X0 - 1, y + 1, z, (z % 2 == 0 ? "bunk_radio_desk" : "bunk_rack_boxes") + "[facing=west]");
        }
        c.set(TX0 + 1, y + 1, CORE_Z0 + 2, "desk[facing=east]");
        c.set(TX0 + 2, y + 1, CORE_Z0 + 2, "bunk_chair[facing=west]");
        c.set(TX0 + 1, y + 2, CORE_Z0 + 5, "bunk_map_wall[facing=east]");
        c.set(TX0 + 1, y + 2, CORE_Z0 + 6, "bunk_map_wall[facing=east]");
        // the chief's office east: the desk, the flags, the safe, the board of who's where
        c.set(116, y + 1, CORE_Z0 + 4, "desk[facing=west]");
        c.set(117, y + 1, CORE_Z0 + 4, "office_chair[facing=west]");
        c.set(TX1 - 1, y + 1, CORE_Z0 + 1, "cv_flag_us[facing=west]");
        c.set(TX1 - 1, y + 2, CORE_Z0 + 1, "cv_flag_us_top[facing=west]");
        c.set(TX1 - 1, y + 1, CORE_Z1, "cellar_safe[facing=west]");
        c.set(TX1 - 1, y + 2, CORE_Z1, "cellar_safe_top[facing=west]");
        c.set(TX1 - 1, y + 2, CORE_Z0 + 5, "cc_proclamation[facing=west]");
        // the dormitory: cots in rows, the sandbags across the windows, the stores
        for (int x = TX0 + 2; x < TX1 - 1; x += 2) {
            for (int z : new int[] {pz + 2, pz + 5}) {
                if (x >= 77 && x <= 80 || x >= 95 && x <= 98 || x >= 113 && x <= 116 || c.chance() < 0.15) {
                    continue;
                }
                c.set(x, y + 1, z, "ch_cot_head[facing=north]");
                c.set(x, y + 1, z + 1, "ch_cot_foot[facing=north]");
            }
        }
        for (int x = TX0 + 1; x < TX1; x++) {
            if (c.chance() < 0.7) {
                c.set(x, y + 1, TZ1 - 1, "sandbags[facing=north]");
            }
        }
        for (int z = pz + 1; z < TZ1 - 1; z += 2) {
            c.set(TX0 + 1, y + 1, z, c.chance() < 0.5 ? "bunk_crate_ration[facing=east]" : "bunk_drum_water");
            c.set(TX1 - 1, y + 1, z, c.chance() < 0.5 ? "st_pallet_water" : "bunk_rack_boxes[facing=west]");
        }
        // sandbags either side of the stair doors, the last of the lights
        for (int sx : new int[] {SA, SB}) {
            c.set(sx + 1, y + 1, CORE_Z0 - 2, "sandbags[facing=north]");
            c.set(sx + 3, y + 1, CORE_Z0 - 2, "sandbags[facing=north]");
        }
        ceiling(y, TX0 + 1, TZ0 + 1, TX1 - 1, TZ1 - 1, c.power ? 0.25 : 0.0);
        c.mess(TX0 + 1, TZ0 + 1, TX1 - 1, TZ1 - 1, y, 0.06);
    }

    // ------------------------------------------------------------------ the roof

    private void roof() {
        int y = ROOF;
        // the helipad over the east end, its lights; the helicopter that came for the mayor
        int hx = 110, hz = 82;
        for (int i = 0; i < 49; i++) {
            c.set(hx + i % 7, y, hz + i / 7, "hs_pad_h_" + i);
        }
        for (int[] q : new int[][] {{hx - 1, hz - 1}, {hx + 7, hz - 1}, {hx - 1, hz + 7}, {hx + 7, hz + 7}}) {
            if (q[0] < TX1 && q[1] < TZ1) {
                c.set(q[0], y + 1, q[1], (c.power && c.chance() < 0.6 ? "fit_numlight_on" : "fit_numlight") + "[facing=north]");
            }
        }
        if (c.chance() < 0.45) {
            c.p.vehicle(hx + 3.5, y + 1, hz + 3.5, (float) (c.chance() * 360), "heli_hornbill", c.rng.nextLong());
        }
        // the mast and the dish on the west end, the plant, the sandbag nest at the front corner
        for (int k = 1; k <= 14; k++) {
            c.set(78, y + k, 90, "st_lt_mast");
        }
        c.set(78, y + 15, 90, "aviation_light");
        c.set(80, y + 1, 92, "hvac");
        c.set(81, y + 1, 92, "hvac");
        c.set(76, y + 1, 95, "hvac");
        c.set(77, y + 1, 95, "hvac");
        for (int x = 74; x <= 78; x++) {
            c.set(x, y + 1, TZ0 + 2, "sandbags[facing=north]");
        }
        c.set(TX0 + 2, y + 1, TZ0 + 4, "sandbags[facing=west]");
        c.set(TX0 + 2, y + 1, TZ0 + 5, "sandbags[facing=west]");
        for (int i = 0; i < 18; i++) {
            int x = TX0 + 2 + c.rng.nextInt(TX1 - TX0 - 3), z = TZ0 + 2 + c.rng.nextInt(TZ1 - TZ0 - 3);
            if (c.empty(x, y + 1, z) && "un_roof".equals(c.get(x, y, z))) {
                c.set(x, y + 1, z, c.any("roof_moss", "trash_bag[facing=" + c.dir() + "]", "paper", "rubble_pile"));
            }
        }
    }
}
