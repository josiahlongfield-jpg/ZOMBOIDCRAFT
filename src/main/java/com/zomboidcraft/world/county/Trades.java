package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Shops;
import java.util.SplittableRandom;

/**
 * The ground floors of main street's own trades (the rest are fitted out like the city's shops,
 * see {@link Shops}): the bank's teller line and open vault, the barber's chairs, the tavern's bar
 * and pool table, the hotel's lobby and its floors of rooms, the realty office, the VFW post's
 * hall and the empty shop left mid-refit. Most have the city shops' back rooms (toilet, office,
 * stock room) behind a partition at d - 5. Fittings come from tools/city/trades.py, furniture.py
 * and interiors.py; the searchable ones carry their own loot.
 *
 * The room: x from 1 to w - 2, z from 1 (inside the window) to d - 2. The front door is at w / 2
 * (a pair from w / 2 - 1 when 12 or wider), the back door at (w - 2, d - 1). Furniture faces the
 * way it looks into the room; wall pieces face the wall they hang on.
 */
final class Trades {
    private static final String[] QUILTS = {"quilt", "blue", "rose"};
    private static final String[] SOFAS = {"floral", "brown", "blue"};
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final int w, d, r, back, door;
    private final boolean pair;

    Trades(Placer p, SplittableRandom rng, Bldg b, int w, int d) {
        this.p = p;
        this.rng = rng;
        this.b = b;
        this.w = w;
        this.d = d;
        this.r = w - 2;
        this.back = d - 2;
        this.door = w / 2;
        this.pair = w >= 12;
    }

    /** Whether a hotel this size has its stairs inside, at the back (else it's the back porches). */
    static boolean innerStair(int w, int d) {
        return w >= 9 && d >= 14;
    }

    /** The partition the back rooms sit behind, or the back wall when the shop's too shallow for them. */
    private int partition() {
        return d >= 12 ? d - 5 : d - 1;
    }

    private void backRooms(String shelf, String table) {
        if (d >= 12) {
            Shops.backRooms(p, rng, w, d, r, shelf, table);
        }
    }

    // ------------------------------------------------------------------ the bank

    /**
     * A marble lobby with the check desk, a roped queue and chairs under the rates board; the teller
     * line across the room, glass to the ceiling, a gate at the end; the officers' desks behind it;
     * and at the back the vault, its round door swung open and the deposit boxes inside jemmied.
     */
    void bank() {
        int vz = d >= 14 ? d - 6 : -1;
        int end = vz > 0 ? vz : partition();
        int tc = Math.max(4, Math.min(end - 3, 1 + (end - 1) * 2 / 5));
        for (int x = 1; x <= r; x++) {
            for (int z = 1; z <= back; z++) {
                double k = rng.nextDouble();
                p.set(x, 0, z, k < 0.08 ? "lobby_floor_white_crack" : k < 0.3 ? "lobby_floor_white_b" : "lobby_floor_white");
            }
        }
        // the teller line: counters with glass to the ceiling, a window every third, the gate at the east end
        for (int x = 1; x <= r; x++) {
            if (x == r) {
                Doors.abandoned(p, rng, x, 1, tc, Doors.FLAT, "north", Doors.hinge(rng));
                p.set(x, 3, tc, "store_teller_glass[axis=x]");
                continue;
            }
            boolean window = x % 3 == 2;
            p.set(x, 1, tc, window ? "store_register[facing=north]" : "store_counter[facing=north]");
            p.set(x, 3, tc, "store_teller_glass[axis=x]");
            if (rng.nextDouble() < 0.22) {
                p.set(x, 2, tc, "air");  // smashed through
                p.setIfAir(x, 1, tc - 1, "glass_shards");
            } else {
                p.set(x, 2, tc, "store_teller_glass[axis=x]");
            }
            if (window && tc + 1 < end) {
                p.set(x, 1, tc + 1, rng.nextDouble() < 0.3 ? "office_chair_fallen[facing=east]" : "office_chair[facing=north]");
            }
        }
        // the lobby
        p.set(1, 1, 1, "planter");
        p.set(r, 1, 1, "planter");
        int qx = door + (pair ? 2 : 1);
        if (qx < r && tc - 2 >= 3) {
            // the queue, roped off beside the way in
            for (int z = 2; z <= tc - 2; z++) {
                p.set(qx, 1, z, z == 2 ? "trade_rope_post_end[facing=south]" : z == tc - 2 ? "trade_rope_post_end[facing=north]"
                        : "trade_rope_post[axis=z]");
            }
        }
        int cx = Math.max(2, door - (pair ? 3 : 2));
        if (cx < door - (pair ? 1 : 0)) {
            p.set(cx, 1, tc - 2, "trade_check_desk");
        }
        for (int z = 2; z < tc - 1; z++) {
            p.set(1, 1, z, rng.nextDouble() < 0.15 ? "store_chair[facing=north]" : "store_chair[facing=east]");
        }
        p.set(1, 2, Math.max(2, tc - 2), "trade_rates_board[facing=west]");
        p.set(1, 3, Math.max(2, tc - 2), "clock_face[facing=west]");
        p.set(r, 2, 2, "home_picture_lake[facing=east]");
        // behind the counter: the officers' desks, files, the coat rack
        if (tc + 3 < end) {
            for (int x : new int[] {2, r - 2}) {
                p.set(x, 1, tc + 2, "desk[facing=north]");
                p.set(x, 1, tc + 3, rng.nextDouble() < 0.25 ? "office_chair_fallen[facing=west]" : "office_chair[facing=north]");
            }
        }
        p.set(1, 1, tc + 1, "home_coat_rack");
        for (int z = tc + 2; z < end; z++) {
            if (rng.nextDouble() < 0.7) {
                p.set(1, 1, z, "filing_cabinet[facing=east,loot=office]");
            }
        }
        if (vz > 0) {
            vault(vz);
        } else {
            p.set(r - 1, 1, end - 1, "store_safe[facing=north,loot=bank]");
            backRooms("store_shelf_empty", "office");
        }
    }

    /** The vault across the back (its front wall at vz), the strip at x = r left as the way to the back door. */
    private void vault(int vz) {
        int ex = r - 1;
        for (int y = 1; y <= 3; y++) {
            for (int x = 1; x <= ex; x++) {
                p.set(x, y, vz, "concrete_dark");
            }
            for (int z = vz + 1; z <= back; z++) {
                p.set(ex, y, z, "concrete_dark");
            }
        }
        b.floor(1, vz + 1, ex - 1, back, 0, "floor_concrete");
        // the doorway, the round door swung open against the wall beside it
        int vx = 2;
        p.set(vx, 1, vz, "air");
        p.set(vx, 2, vz, "air");
        p.set(vx + 1, 1, vz - 1, "trade_vault_door[facing=north]");
        p.set(vx + 1, 2, vz - 1, "trade_vault_door_top[facing=north]");
        // the deposit boxes round the walls, the cash safe on the floor
        for (int x = 1; x < ex; x++) {
            p.set(x, 1, back, "trade_deposit_boxes[facing=north]");
            p.set(x, 2, back, "trade_deposit_boxes_b[facing=north]");
        }
        for (int z = vz + 1; z < back; z++) {
            p.set(1, 1, z, "trade_deposit_boxes[facing=east]");
            p.set(1, 2, z, "trade_deposit_boxes_b[facing=east]");
        }
        if (ex - 1 > vx) {
            p.set(ex - 1, 1, vz + 1, "store_safe[facing=west,loot=bank]");
        }
        for (int i = 0; i < 3; i++) {
            p.setIfAir(2 + rng.nextInt(Math.max(1, ex - 2)), 1, vz + 1 + rng.nextInt(Math.max(1, back - vz - 1)), "paper");
        }
        p.set(vx, 3, vz + 2, "ceiling_light_off");
    }

    // ------------------------------------------------------------------ the barber's

    /** Chairs down the west wall, each at its station under a mirror, hair round them; the waiting chairs opposite. */
    void barber() {
        int end = partition();
        b.floor(1, 1, r, end - 1, 0, "floor_checker");
        for (int z = 2; z <= end - 3; z += 3) {
            p.set(1, 1, z, "trade_barber_station[facing=east]");
            p.set(1, 2, z, "home_mirror[facing=west]");
            p.set(3, 1, z, "trade_barber_chair[facing=west]");
            for (int i = 0; i < 3; i++) {
                p.setIfAir(2 + rng.nextInt(3), 1, z - 1 + rng.nextInt(3), "trade_hair");
            }
        }
        p.set(r, 1, 2, "store_register[facing=west]");
        p.set(r, 1, 3, "home_coat_rack");
        p.set(1, 1, 1, rng.nextDouble() < 0.6 ? "home_plant_dead" : "home_plant");
        p.set(r, 3, Math.max(4, end / 2), "clock_face[facing=east]");
        for (int z = 4; z <= end - 2; z++) {
            if (z % 4 == 2) {
                p.set(r, 1, z, "home_table");
                p.set(r, 2, z, rng.nextBoolean() ? "home_books_stack_low[facing=west]" : "home_radio_low[facing=west]");
            } else if (rng.nextDouble() < 0.85) {
                p.set(r, 1, z, rng.nextDouble() < 0.12 ? "store_chair[facing=north]" : "store_chair[facing=west]");
            }
        }
        p.set(r, 2, end - 2, "home_picture_family[facing=east]");
        backRooms("store_shelf_empty", "home_bathroom");
    }

    // ------------------------------------------------------------------ the tavern

    /**
     * The bar down the east side with the back bar and its mirror behind, stools along it; tables,
     * the jukebox and a dartboard on the west side, the pool table at the back, neon in the window.
     */
    void tavern() {
        int end = partition();
        b.floor(1, 1, r, end - 1, 0, "floor_boards");
        int bx = r - 2, z0 = 3, z1 = end - 2, taps = (z0 + z1) / 2;
        for (int z = z0; z < z1; z++) {
            p.set(bx, 1, z, z == taps ? "trade_bar_taps[facing=west]" : "trade_bar[facing=west]");
            p.set(r, 1, z, "trade_back_bar[facing=west]");
            p.set(r, 2, z, z == taps ? "trade_bar_mirror_logo[facing=east]" : "trade_bar_mirror[facing=east]");
            if ((z - z0) % 2 == 0 && rng.nextDouble() < 0.85) {
                p.set(bx - 1, 1, z, "store_stool");
            }
            if (z != taps && rng.nextDouble() < 0.25) {
                p.set(bx, 2, z, "home_bottles[facing=" + DIRS[rng.nextInt(4)] + "]");
            }
        }
        // the pool table at the back of the west side, where there's room to play round it
        int pz = -1;
        if (bx - 2 >= 4 && z1 - 2 >= z0 + 3) {
            pz = z1 - 2;
            p.set(2, 1, pz, "trade_pool_l[facing=north]");
            p.set(3, 1, pz, "trade_pool_r[facing=north]");
            p.set(1, 2, pz, "trade_dartboard[facing=west]");
        } else {
            p.set(1, 2, z1 - 1, "trade_dartboard[facing=west]");
        }
        // booths down the west wall when there's room for an aisle past them, tables in the middle, a chair either side
        int tablesTo = pz > 0 ? pz - 2 : z1 - 1;
        boolean booths = bx - 1 >= 5;
        if (booths) {
            for (int z = 4; z + 2 <= tablesTo; z += 4) {
                p.set(1, 1, z, "store_booth[facing=south]");
                p.set(1, 1, z + 1, "store_table");
                p.set(1, 1, z + 2, "store_booth[facing=north]");
                if (rng.nextDouble() < 0.4) {
                    p.set(1, 2, z + 1, "home_bottles_low[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        for (int z = 3; z + 1 <= tablesTo; z += 3) {
            for (int x = booths ? 3 : 2; x <= bx - 3; x += 3) {
                if (Math.abs(x - door) <= (pair ? 2 : 1) && z <= 3) {
                    continue;
                }
                p.set(x, 1, z, "store_table");
                p.set(x, 1, z - 1, "store_chair[facing=south]");
                if (rng.nextDouble() < 0.8) {
                    p.set(x, 1, z + 1, rng.nextDouble() < 0.15 ? "store_chair[facing=east]" : "store_chair[facing=north]");
                }
            }
        }
        p.set(1, 1, 2, "trade_jukebox[facing=east]");
        p.set(1, 2, 2, "trade_jukebox_top[facing=east]");
        p.set(2, 2, 1, rng.nextDouble() < 0.12 ? "trade_neon_beer_on[facing=north]" : "trade_neon_beer[facing=north]");
        if (bx - 2 >= 4) {
            p.set(bx - 2, 2, 1, rng.nextDouble() < 0.12 ? "trade_neon_beer_on[facing=north]" : "trade_neon_beer[facing=north]");
        }
        p.set(1, 3, Math.max(3, end / 2), "clock_face[facing=west]");
        backRooms("store_shelf_liquor", "liquor");
    }

    // ------------------------------------------------------------------ the hotel

    /**
     * The lobby: the front desk on the east side with the key rack behind it, a sofa and chairs on
     * the west, the luggage cart; behind it the stairs and the back office (stairs: see
     * {@link #hotelStairs}) or the back rooms.
     */
    void hotelLobby(boolean stairs) {
        int end = stairs ? d - 5 : partition();
        for (int x = 1; x <= r; x++) {
            for (int z = 1; z <= back; z++) {
                double k = rng.nextDouble();
                p.set(x, 0, z, k < 0.08 ? "lobby_floor_dark_crack" : k < 0.3 ? "lobby_floor_dark_b" : "lobby_floor_dark");
            }
        }
        for (int z = 3; z <= 5; z++) {
            p.set(r - 1, 1, z, "reception_desk[facing=west]");
        }
        p.set(r, 1, 3, "office_chair[facing=west]");
        p.set(r, 2, 4, "trade_key_rack[facing=east]");
        p.set(r, 3, 4, "clock_face[facing=east]");
        p.set(r, 2, 3, "home_phone[facing=east]");
        p.set(r, 1, 5, "filing_cabinet[facing=west,loot=office]");
        // the runner in from the door, worn to the boards in the middle
        int rx = pair ? door - 1 : door;
        rug(rx, 2, rx + 1 <= r - 2 ? rx + 1 : rx, Math.min(end - 2, 6), rng.nextBoolean() ? "red" : "blue");
        String sofa = SOFAS[rng.nextInt(SOFAS.length)];
        p.set(1, 1, 3, "home_sofa_" + sofa + "_l[facing=east]");
        p.set(1, 1, 4, "home_sofa_" + sofa + "_m[facing=east]");
        p.set(1, 1, 5, "home_sofa_" + sofa + "_r[facing=east]");
        p.set(1, 2, 4, "home_picture_lake[facing=west]");
        if (door - (pair ? 1 : 0) > 3) {
            p.set(2, 1, 4, "home_table");
            p.set(2, 2, 4, rng.nextBoolean() ? "home_lamp_table_low[facing=east]" : "home_books_stack_low[facing=east]");
            p.set(3, 1, 3, "home_armchair_" + sofa + "[facing=west]");
            if (door - (pair ? 1 : 0) > 4) {
                p.set(3, 1, 5, "home_armchair_" + sofa + "[facing=west]");
            }
        }
        p.set(1, 1, 1, "planter");
        p.set(r, 1, 1, "planter");
        if (end - 1 >= 7) {
            p.set(r - 1, 1, 7, "trade_luggage_cart[facing=" + DIRS[rng.nextInt(4)] + "]");
        }
        // the piano at the back of the lounge, the cigarette machine by the stairs
        if (end - 3 >= 7) {
            p.set(1, 1, end - 3, "home_piano_l[facing=east]");
            p.set(1, 1, end - 2, "home_piano_r[facing=east]");
            p.set(2, 1, end - 3, rng.nextDouble() < 0.2 ? "home_chair_tipped[facing=west]" : "home_chair[facing=west]");
        }
        if (end - 2 >= 8) {
            p.set(r, 1, end - 2, "store_vending[facing=west]");
            p.set(r, 2, end - 2, "store_vending_top[facing=west]");
        }
        if (!stairs) {
            backRooms("store_shelf_empty", "clothes");
        }
    }

    /**
     * The stairs up the back of the hotel and its floors of rooms: the back office west of the
     * stairwell, the stairwell (see {@link Flats#stairwell}), a door from the lobby to its foot.
     */
    void hotelStairs(int floors, int storey, double lit, String hutWall) {
        int a = w - 7;
        if (a - 1 >= 3) {
            Shops.backRooms(p, rng, w, d, a - 2, "store_shelf_empty", "clothes");
        } else {
            for (int x = 1; x <= r; x++) {
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, d - 5, "drywall");
                }
            }
        }
        Flats.stairwell(p, rng, w, d, floors, storey, lit, hutWall, (k, y) -> rooms(y, d - 5, storey));
        Doors.abandoned(p, rng, w - 2, 1, d - 5, Doors.FLAT, "south", Doors.hinge(rng));
    }

    /**
     * A floor of rooms (slab at y) from the front wall back to wall zb: a corridor down the middle
     * (down the east side when narrow) to a door in zb, a room every four blocks either side, each
     * with its bed, nightstand, wardrobe and dresser. Returns the x of the corridor.
     */
    int rooms(int y, int zb, int storey) {
        boolean two = w >= 11;
        int c = two ? w / 2 : r, top = y + 1;
        for (int z = 1; z < zb; z++) {
            for (int h = 0; h < storey - 1; h++) {
                p.set(c - 1, top + h, z, wallpaper(c - 1, z, h));
                if (two) {
                    p.set(c + 1, top + h, z, wallpaper(c + 1, z, h));
                }
            }
        }
        for (int z0 = 1; z0 + 2 < zb; z0 += 4) {
            room(1, c - 2, z0, top, storey, zb);
            Doors.abandoned(p, rng, c - 1, top, z0 + 1, Doors.FLAT, "east", Doors.hinge(rng));
            if (two) {
                room(r, c + 2, z0, top, storey, zb);
                Doors.abandoned(p, rng, c + 1, top, z0 + 1, Doors.FLAT, "west", Doors.hinge(rng));
            }
        }
        p.set(c, top + storey - 2, zb / 2, rng.nextDouble() < lit() ? "ceiling_light" : "ceiling_light_off");
        b.mess(1, 1, r, zb - 1, top, 0.04);
        return c;
    }

    private double lit() {
        return b.lit + 0.03;
    }

    /** One room, x from outer (against the outside wall) to inner (by the corridor), z from z0 to z0 + 2. */
    private void room(int outer, int inner, int z0, int top, int storey, int zb) {
        int dir = Integer.signum(inner - outer);
        if (dir == 0 && outer < 1) {
            return;
        }
        // the cross wall behind it
        if (z0 + 3 < zb) {
            for (int x = Math.min(outer, inner); x <= Math.max(outer, inner); x++) {
                for (int h = 0; h < storey - 1; h++) {
                    p.set(x, top + h, z0 + 3, wallpaper(x, z0 + 3, h));
                }
            }
        }
        String quilt = rng.nextDouble() < 0.08 ? "bloody" : QUILTS[rng.nextInt(QUILTS.length)];
        p.set(outer, top, z0 + 2, "home_bed_" + quilt + "_head[facing=north]");
        p.set(outer, top, z0 + 1, "home_bed_" + quilt + "_foot[facing=north]");
        p.set(outer, top, z0, "home_wardrobe[facing=south]");
        p.set(outer, top + 1, z0, "home_wardrobe_top[facing=south]");
        if (dir != 0) {
            p.set(outer + dir, top, z0 + 2, "home_nightstand[facing=north]");
            p.set(inner, top, z0, "home_dresser[facing=south]");
            if (outer + dir != inner) {
                p.set(outer + dir, top, z0 + 1, "home_rug_" + (rng.nextBoolean() ? "red" : "blue") + "[facing=north]");
                if (Math.abs(inner - outer) >= 3) {
                    p.set(inner, top, z0 + 2, rng.nextDouble() < 0.2 ? "home_chair_tipped[facing=west]" : "home_chair[facing=north]");
                }
            }
        }
        p.set(outer + dir, top + storey - 2, z0 + 1, rng.nextDouble() < lit() ? "ceiling_light" : "ceiling_light_off");
    }

    /** A rug over x0..x1, z0..z1, edged and cornered. */
    private void rug(int x0, int z0, int x1, int z1, String colour) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean n = z == z0, s = z == z1, w = x == x0, e = x == x1;
                String block;
                if ((n || s) && (w || e) && x0 != x1) {
                    block = "home_rug_" + colour + "_corner[facing=" + (n ? (w ? "north" : "east") : (e ? "south" : "west")) + "]";
                } else if (n || s || w || e) {
                    block = "home_rug_" + colour + "_edge[facing=" + (x0 == x1 ? (n ? "north" : s ? "south" : "east")
                            : n ? "north" : s ? "south" : w ? "west" : "east") + "]";
                } else {
                    block = "home_rug_" + colour;
                }
                p.set(x, 1, z, block);
            }
        }
    }

    private String wallpaper(int x, int z, int row) {
        long h = x * 73856093L ^ z * 19349663L ^ row * 83492791L;
        return (h >>> 9) % 7 == 0 ? "home_wall_cream_worn" : "home_wall_cream";
    }

    // ------------------------------------------------------------------ offices and the hall

    /** Desks either side of the aisle, a client chair at each; listings on the walls, the water cooler, files. */
    void realty() {
        int end = partition();
        b.floor(1, 1, r, end - 1, 0, "office_floor");
        for (int z = 3; z + 1 <= end - 2; z += 4) {
            for (int x : new int[] {2, r - 1}) {
                if (Math.abs(x - door) <= (pair ? 1 : 0)) {
                    continue;
                }
                p.set(x, 1, z, "desk[facing=north]");
                p.set(x, 1, z + 1, rng.nextDouble() < 0.25 ? "office_chair_fallen[facing=east]" : "office_chair[facing=north]");
                p.set(x, 1, z - 1, rng.nextDouble() < 0.15 ? "store_chair[facing=east]" : "store_chair[facing=south]");
            }
        }
        for (int z = 2; z <= end - 2; z += 2) {
            p.set(1, 2, z, rng.nextBoolean() ? "trade_listings[facing=west]" : "trade_listings_b[facing=west]");
            if (z > 2) {
                p.set(r, 2, z, rng.nextBoolean() ? "trade_listings[facing=east]" : "trade_listings_b[facing=east]");
            }
        }
        p.set(r, 1, 2, "trade_water_cooler[facing=west]");
        p.set(1, 1, 1, rng.nextDouble() < 0.6 ? "home_plant_dead" : "home_plant");
        p.set(r, 1, end - 1, "filing_cabinet[facing=west,loot=office]");
        p.set(r, 1, end - 2, "filing_cabinet[facing=west,loot=office]");
        p.set(1, 1, end - 1, "home_coat_rack");
        backRooms("store_shelf_empty", "office");
    }

    /**
     * The post's hall: banquet tables down it with folding chairs (some kicked over), the canteen
     * bar in the back corner, the flag on the back wall and the honor roll on the side.
     */
    void vfw() {
        int end = partition();
        b.floor(1, 1, r, end - 1, 0, "floor_boards");
        boolean bar = w >= 10 && end - 2 >= 7;
        int barZ = end - 4;
        for (int tx = 2; tx <= r - 1; tx += 4) {
            // short of the bar where they'd crowd the drinkers
            int last = bar && tx + 1 >= r - 3 ? barZ - 2 : end - 3;
            for (int z = 3; z <= last; z++) {
                p.set(tx, 1, z, "trade_banquet_table");
                for (int side : new int[] {-1, 1}) {
                    double k = rng.nextDouble();
                    if (k < 0.75) {
                        p.set(tx + side, 1, z, (k < 0.1 ? "trade_folding_chair_tipped" : "trade_folding_chair")
                                + "[facing=" + (side < 0 ? "east" : "west") + "]");
                    }
                }
            }
        }
        if (bar) {
            for (int z = barZ; z <= end - 2; z++) {
                p.set(r - 2, 1, z, "trade_bar[facing=west]");
                p.set(r, 1, z, "trade_back_bar[facing=west]");
            }
            p.set(r, 2, barZ + 1, "trade_bar_mirror[facing=east]");
            p.set(r - 2, 2, barZ, "store_register[facing=west]");
        } else {
            p.set(r, 1, end - 2, "store_coffee[facing=west]");
        }
        int fz = end - 1, fx = w / 2 - 1;
        p.set(fx + 1, 2, fz, "trade_flag_l[facing=south]");
        p.set(fx, 2, fz, "trade_flag_r[facing=south]");
        p.set(fx + 3 <= r ? fx + 3 : 1, 3, fz, "clock_face[facing=south]");
        p.set(1, 2, 3, "trade_honor_board[facing=west]");
        if (end - 2 > 6) {
            p.set(1, 2, 6, "trade_honor_board[facing=west]");
        }
        p.set(r, 1, 1, "home_coat_rack");
        backRooms("store_shelf_empty", "drinks");
    }

    /** Stripped to the concrete: drop cloths, a stepladder and paint tins from a refit that never finished. */
    void vacant() {
        int end = partition();
        b.floor(1, 1, r, end - 1, 0, "floor_concrete");
        for (int i = 0; i < 3; i++) {
            int x0 = 1 + rng.nextInt(Math.max(1, r - 1)), z0 = 2 + rng.nextInt(Math.max(1, end - 4));
            for (int x = x0; x <= Math.min(r, x0 + 1); x++) {
                for (int z = z0; z <= Math.min(end - 1, z0 + 1 + rng.nextInt(2)); z++) {
                    p.setIfAir(x, 1, z, "trade_drop_cloth[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        int lx = 1 + rng.nextInt(r), lz = 3 + rng.nextInt(Math.max(1, end - 4));
        p.set(lx, 1, lz, "trade_stepladder[facing=" + DIRS[rng.nextInt(4)] + "]");
        for (int i = 0; i < 2 + rng.nextInt(3); i++) {
            p.setIfAir(1 + rng.nextInt(r), 1, 2 + rng.nextInt(Math.max(1, end - 3)), "store_paint_cans");
        }
        p.set(1, 1, end - 1, "home_boxes");
        backRooms("store_shelf_empty", "scraps");
    }
}
