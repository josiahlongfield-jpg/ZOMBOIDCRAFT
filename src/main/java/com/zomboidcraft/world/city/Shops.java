package com.zomboidcraft.world.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Fits out the shop on a walk-up's ground floor to match its sign: aisles and coolers in the
 * grocery, washers in the laundromat, gun cases, a diner counter with stools and booths. Each
 * has its own floor and a stockroom behind a partition at the back. The shelves look stocked;
 * what's on the floor is what looters dropped. Blocks come from tools/city/interiors.py.
 *
 * Shop floor: x from 1 to w - 2, z from 1 (the window) to the partition at d - 5. The front door
 * is in the middle of the window (a pair in wide shops); the back door at (w - 2, d - 1).
 * Wall pieces face the wall they're fixed to; shelves and machines face into the room.
 */
public final class Shops {
    public enum Type {
        PHARMACY("medline"), GROCERY("stopnsave"), LAUNDROMAT("suds"), GUNS("patriot"), CASH("cash"),
        DINER("joes"), PAWN("luckypawn"), PIZZA("tonys"), THRIFT("thrift"), LIQUOR("riverside"),
        HARDWARE("kessler");

        /** The brand on its fascia sign (see {@link Signs}). */
        public final String brand;

        Type(String brand) {
            this.brand = brand;
        }
    }

    /** The fittings you can search (the "loot" blocks in tools/city/interiors.py). */
    private static final Set<String> SEARCHABLE = Set.of(
            "cardboard_box", "crate", "filing_cabinet", "store_bin", "store_case_guns", "store_case_jewelry",
            "store_case_pies", "store_clothes_rack", "store_coffee", "store_cooler", "store_counter", "store_dryer",
            "store_grill", "store_gun_rack", "store_lumber", "store_oven", "store_paint_cans", "store_pawn_wall",
            "store_pegboard", "store_produce", "store_register", "store_safe", "store_shelf_ammo",
            "store_shelf_empty", "store_shelf_grocery", "store_shelf_hardware", "store_shelf_liquor",
            "store_shelf_pawn", "store_shelf_pharmacy", "store_shelf_thrift", "store_vending", "store_washer");

    /** The last few shops fitted out, so the same kind doesn't open three doors in a row. */
    private static final Type[] RECENT = new Type[4];
    private static int recentAt;

    public static Type pick(SplittableRandom rng) {
        Type[] all = Type.values();
        Type t = all[rng.nextInt(all.length)];
        for (int tries = 0; tries < all.length && recent(t); tries++) {
            t = all[(t.ordinal() + 1) % all.length];
        }
        RECENT[recentAt] = t;
        recentAt = (recentAt + 1) % RECENT.length;
        return t;
    }

    private static boolean recent(Type t) {
        for (Type r : RECENT) {
            if (r == t) {
                return true;
            }
        }
        return false;
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final Type type;
    private final int w, d, left, right, back, partition, door;
    private final boolean pairDoor;
    /** The last x the back rooms may use (a walk-up's stairwell takes the rest). */
    private int lastBack;
    /** The doors through the partition: the cells in front of them are kept clear. */
    private final List<Integer> backDoors = new ArrayList<>();
    /** For a shop fitted out elsewhere (no type): what racks its stock room, and the loot its fittings roll. */
    private String stockShelf = "store_shelf_empty", stockTable = "scraps";

    public Shops(Placer p, SplittableRandom rng, Type type, int w, int d) {
        this.p = p;
        this.rng = rng;
        this.type = type;
        this.w = w;
        this.d = d;
        this.left = 1;
        this.right = w - 2;
        this.partition = d - 5;
        this.back = partition - 1;
        this.door = w / 2;
        this.pairDoor = w >= 12;
        this.lastBack = right;
    }

    /** Keeps the back rooms west of x (a walk-up's stairwell takes the strip beyond it). */
    public Shops backTo(int x) {
        lastBack = x;
        return this;
    }

    /**
     * Only the back rooms (toilet, office, stock room behind a partition at d - 5) of a w by d shop
     * whose floor is fitted out elsewhere, kept west of backTo; shelf racks the stock room and
     * table is the loot its fittings roll.
     */
    public static void backRooms(Placer p, SplittableRandom rng, int w, int d, int backTo, String shelf, String table) {
        Shops s = new Shops(p, rng, null, w, d).backTo(backTo);
        s.stockShelf = shelf;
        s.stockTable = table;
        s.stockroom();
        for (int x : s.backDoors) {
            s.put(x, 1, s.back, "air");
            s.put(x, 2, s.back, "air");
        }
    }

    public void fitOut() {
        floor();
        stockroom();
        switch (type) {
            case PHARMACY -> pharmacy();
            case GROCERY -> grocery();
            case LAUNDROMAT -> laundromat();
            case GUNS -> cases("store_case_guns", "store_gun_rack", "store_shelf_ammo");
            case CASH -> checkCashing();
            case DINER -> diner();
            case PAWN -> cases("store_case_jewelry", "store_pawn_wall", "store_shelf_pawn");
            case PIZZA -> pizza();
            case THRIFT -> thrift();
            case LIQUOR -> liquor();
            case HARDWARE -> hardware();
        }
        for (int x : backDoors) {
            put(x, 1, back, "air");
            put(x, 2, back, "air");
        }
        mess();
    }

    // ---------------------------------------------------------------- the room

    private void floor() {
        String f = switch (type) {
            case DINER, PIZZA -> "floor_checker";
            case GUNS, PAWN, THRIFT -> "floor_boards";
            case HARDWARE -> "floor_concrete";
            default -> "floor_lino";
        };
        p.fill(left, 0, 1, right, 0, partition - 1, f);
    }

    /**
     * Behind the partition across the back: a staff toilet and the manager's office where the
     * shop is wide enough, each with its own door off the shop floor, and the stock room.
     */
    private void stockroom() {
        for (int x = left; x <= right; x++) {
            for (int y = 1; y <= 3; y++) {
                put(x, y, partition, "drywall");
            }
        }
        int end = Math.min(right, lastBack), z0 = partition + 1, z1 = d - 2;
        int x = left;
        if (end - x + 1 >= 6) {
            toilet(x, z0, z1);
            wall(x + 2, z0, z1);
            x += 3;
        }
        if (end - x + 1 >= 7) {
            office(x, z0, z1);
            wall(x + 3, z0, z1);
            x += 4;
        }
        if (end >= x) {
            stock(x, end, z0, z1);
        } else if (end < left) {
            backDoor(left + 1);  // straight through to the stairwell
        }
    }

    private void wall(int x, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            for (int y = 1; y <= 3; y++) {
                put(x, y, z, "drywall");
            }
        }
    }

    private void backDoor(int x) {
        Doors.abandoned(p, rng, x, 1, partition, Doors.FLAT, "south", Doors.hinge(rng));
        backDoors.add(x);
    }

    /** The staff toilet: two wide, tiled, a toilet and a basin against the back wall. */
    private void toilet(int x, int z0, int z1) {
        p.fill(x, 0, z0, x + 1, 0, z1, "home_floor_tile");
        backDoor(x + 1);
        put(x, 1, z1, "home_toilet[facing=north]");
        put(x + 1, 1, z1, "home_sink_white[facing=north]");
        put(x + 1, 2, z1, "home_mirror[facing=south]");
        put(x, 2, z0 + 1, "home_towel[facing=west]");
        put(x, 1, z0, rng.nextDouble() < 0.5 ? "home_trash_can" : "home_bin_bags");
        put(x, 3, z0 + 1, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        if (rng.nextDouble() < 0.3) {
            put(x + 1, 1, z0 + 1, rng.nextDouble() < 0.5 ? "home_blood_pool" : "paper");
        }
    }

    /** The manager's office: the desk against the back wall, files, the safe, the calendar and the phone. */
    private void office(int x, int z0, int z1) {
        p.fill(x, 0, z0, x + 2, 0, z1, "home_floor_lino");
        backDoor(x + 1);
        put(x + 1, 1, z1, "home_desk[facing=north]");
        put(x + 1, 1, z1 - 1, rng.nextDouble() < 0.3 ? "office_chair[facing=east]" : "office_chair[facing=south]");
        put(x + 2, 1, z1, "filing_cabinet[facing=north]");
        put(x + 2, 1, z1 - 1, "filing_cabinet[facing=west]");
        put(x, 1, z1, "store_safe[facing=north]");
        put(x, 2, z0, "home_calendar[facing=west]");
        put(x + 2, 2, z0, "home_phone[facing=east]");
        put(x + 1, 3, z0 + 1, rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
        if (rng.nextDouble() < 0.5) {
            put(x, 1, z0, "paper");
        }
    }

    /** The stock room: racking down one wall, boxes and crates stacked along the back. */
    private void stock(int x0, int x1, int z0, int z1) {
        int in = x0 == left ? left + 1 : (x0 + x1 + 1) / 2;
        backDoor(in);
        String stock = type == null ? stockShelf : switch (type) {
            case PHARMACY -> "store_shelf_pharmacy";
            case GROCERY, DINER, PIZZA -> "store_shelf_grocery";
            case LIQUOR -> "store_shelf_liquor";
            case HARDWARE -> "store_shelf_hardware";
            case GUNS -> "store_shelf_ammo";
            case PAWN -> "store_shelf_pawn";
            case THRIFT -> "store_shelf_thrift";
            default -> "store_shelf_empty";
        };
        boolean rack = in != x0;
        for (int z = z0; z <= z1 && rack; z++) {
            stack(x0, z, stock + "[facing=east]", 2);
        }
        for (int x = x0 + (rack ? 2 : 0); x <= x1; x++) {
            if (x == w - 2 || x == in) {
                continue;  // keep the ways to the back door and in from the shop clear
            }
            if (rng.nextDouble() < 0.7) {
                put(x, 1, z1, rng.nextDouble() < 0.6 ? "cardboard_box[facing=north]" : "crate");
                if (rng.nextDouble() < 0.4) {
                    put(x, 2, z1, "crate");
                }
            }
        }
        put(in, 3, z0 + 1, rng.nextDouble() < 0.05 ? "ceiling_light" : "ceiling_light_off");
    }

    /** Clutter left by looters: stock across the floor, a box or two, now and then a toppled bay. */
    private void mess() {
        for (int i = 0; i < 4 + rng.nextInt(4); i++) {
            int x = left + rng.nextInt(right - left + 1), z = 1 + rng.nextInt(back);
            if (air(x, 1, z) && !doorway(x, z)) {
                put(x, 1, z, rng.nextDouble() < 0.75 ? "store_spill" : "paper");
            }
        }
        if (rng.nextDouble() < 0.5) {
            int x = left + rng.nextInt(right - left + 1), z = 2 + rng.nextInt(back - 1);
            if (air(x, 1, z) && !doorway(x, z)) {
                put(x, 1, z, "cardboard_box[facing=" + (rng.nextBoolean() ? "east" : "north") + "]");
            }
        }
    }

    // ---------------------------------------------------------------- shops

    private void grocery() {
        // drinks coolers down the right wall (as you walk in), food aisles, checkouts by the door
        for (int z = 3; z <= back; z++) {
            put(right, 1, z, "store_cooler[facing=west]");
            put(right, 2, z, "store_cooler_top[facing=west]");
        }
        aisles("store_shelf_grocery", 6, back - 1, right - 1);
        checkout(left + 1, 2);
        if (right - left >= 9) {
            checkout(left + 3, 2);
        }
        for (int x = door + (pairDoor ? 2 : 2); x < right - 1; x++) {
            put(x, 1, 4, "store_produce[facing=north]");
        }
        put(door + 1, 1, 2, "store_cart[facing=" + (rng.nextBoolean() ? "north" : "east") + "]");
        toppled(6, back - 1);
        openSign();
    }

    private void pharmacy() {
        // the dispensary across the back: a counter, and medicine shelved behind it
        for (int x = left; x <= right; x++) {
            stack(x, back, "store_shelf_pharmacy[facing=north]", 2);
        }
        for (int x = left + 1; x <= right - 1; x++) {
            put(x, 1, back - 2, x == (left + right) / 2 ? "store_register[facing=south]" : "store_counter[facing=south]");
        }
        aisles("store_shelf_pharmacy", 5, back - 4, right - 1);
        for (int z = 3; z <= back - 4; z++) {
            stack(right, z, "store_shelf_pharmacy[facing=west]", 2);
        }
        checkout(left + 1, 2);
        toppled(5, back - 4);
    }

    private void laundromat() {
        // washers down one wall, dryers stacked two high down the other, folding tables between
        for (int z = 2; z <= back; z++) {
            put(left, 1, z, "store_washer[facing=east]");
            put(right, 1, z, "store_dryer[facing=west]");
            put(right, 2, z, "store_dryer[facing=west]");
        }
        // folding tables in short runs down the middle, plastic chairs pulled up to them
        int mid = (left + right) / 2;
        boolean wideRoom = right - left >= 10;
        for (int z = 5; z <= back - 1; z++) {
            if ((z - 5) % 4 == 3) {
                continue;
            }
            put(mid, 1, z, "store_table");
            if (wideRoom) {
                put(mid + 1, 1, z, "store_table");
            }
            if (rng.nextDouble() < 0.35) {
                put(mid - 1, 1, z, "store_chair[facing=east]");
            }
            if (rng.nextDouble() < 0.35) {
                put(wideRoom ? mid + 2 : mid + 1, 1, z, "store_chair[facing=west]");
            }
        }
        for (int x = left + 1; x < door - 1; x++) {
            put(x, 1, 1, "store_chair[facing=south]");
        }
        put(left + 1, 1, back, "store_vending[facing=north]");
        put(left + 1, 2, back, "store_vending_top[facing=north]");
        openSign();
    }

    /** Gun store or pawn shop: a U of glass cases, racks and shelves round the walls. */
    private void cases(String caseBlock, String board, String shelf) {
        int x0 = left + 2, x1 = right - 2, z0 = 4;
        for (int x = x0; x <= x1; x++) {
            if (x != door || !pairDoor) {
                put(x, 1, z0, caseBlock + "[facing=north]");
            }
        }
        for (int z = z0 + 1; z <= back - 2; z++) {
            put(x0, 1, z, caseBlock + "[facing=west]");
            put(x1, 1, z, caseBlock + "[facing=east]");
        }
        put((x0 + x1) / 2, 1, back - 1, "store_register[facing=north]");
        // a big shop keeps more stock behind the counter: shelving islands, with room to walk
        for (int x = x0 + 3; x <= x1 - 3; x += 3) {
            for (int z = z0 + 3; z <= back - 4; z++) {
                stack(x, z, shelf + "[facing=east]", 2);
            }
        }
        for (int z = 2; z <= back; z++) {
            put(left, 1, z, shelf + "[facing=east]");
            put(left, 2, z, board + "[facing=west]");
            put(right, 1, z, shelf + "[facing=west]");
            put(right, 2, z, board + "[facing=east]");
        }
        for (int x = left + 1; x < right; x++) {
            put(x, 2, back, board + "[facing=south]");
            put(x, 1, back, shelf + "[facing=north]");
        }
        if (type == Type.PAWN) {
            openSign();
        }
    }

    private void checkCashing() {
        // a small lobby, then a teller counter wall to wall behind bulletproof glass
        int z = Math.min(back - 3, 6);
        for (int x = left; x <= right; x++) {
            put(x, 1, z, (x - left) % 3 == 1 ? "store_register[facing=north]" : "store_counter[facing=north]");
            put(x, 2, z, "store_teller_glass[axis=x]");
            put(x, 3, z, "store_teller_glass[axis=x]");
        }
        put(left + 1, 1, back, "store_safe[facing=north]");
        put(right - 1, 1, back, "store_safe[facing=north]");
        for (int x = left + 2; x <= right - 2; x += 3) {
            put(x, 1, z + 2, "reception_desk");
        }
        for (int x = left; x < door - 1; x++) {
            put(x, 1, 1, "store_chair[facing=south]");
        }
        // a writing counter for the forms in the lobby, a drinks machine by the wall
        if (z >= 5) {
            put(door + (pairDoor ? 3 : 2), 1, 3, "store_table");
            put(right, 1, z - 1, "store_vending[facing=west]");
            put(right, 2, z - 1, "store_vending_top[facing=west]");
        }
        put(left, 2, 3, "store_menu[facing=west]");
        put(right, 2, 3, "store_menu[facing=east]");
        openSign();
    }

    private void diner() {
        // kitchen along the back, the counter and stools in front of it, booths by the windows
        for (int x = left; x <= right; x++) {
            put(x, 1, back, x >= right - 1 ? "store_coffee[facing=north]" : "store_grill[facing=north]");
            put(x, 2, back, x % 2 == 0 ? "store_menu[facing=south]" : "store_tile[facing=south]");
        }
        for (int x = left + 2; x <= right; x++) {
            String b = x == left + 2 ? "store_case_pies[facing=north]" : x == right ? "store_register[facing=north]" : "store_counter[facing=north]";
            put(x, 1, back - 2, b);
            if (x < right) {
                put(x, 1, back - 3, "store_stool");
            }
        }
        booths(left, back - 5);
        booths(right, back - 5);
        if (right - left >= 11) {
            tables(back - 6, 4, left + 3, right - 3);
        }
        openSign();
    }

    private void pizza() {
        for (int x = left; x <= left + 1; x++) {
            put(x, 1, back, "store_oven[facing=north]");
            put(x, 2, back, "store_oven[facing=north]");
        }
        for (int x = left + 2; x <= right; x++) {
            put(x, 2, back, x % 2 == 0 ? "store_menu[facing=south]" : "store_tile[facing=south]");
        }
        put(right, 1, back, "store_cooler[facing=north]");
        put(right - 1, 1, back, "store_counter[facing=north]");
        for (int x = left + 1; x <= right - 1; x++) {
            put(x, 1, back - 2, x == right - 1 ? "store_register[facing=north]" : "store_counter[facing=north]");
        }
        tables(back - 5, 3);
        openSign();
    }

    private void thrift() {
        // rails of clothes either side of the middle, bins along one wall, shelves along the other
        for (int z = 3; z <= back - 1; z += 2) {
            for (int x = left + 2; x <= right - 2; x++) {
                if (Math.abs(x - door) > 1) {
                    put(x, 1, z, "store_clothes_rack[facing=north]");
                }
            }
        }
        for (int z = 2; z <= back; z += 2) {
            put(left, 1, z, "store_bin");
        }
        for (int z = 4; z <= back; z++) {
            stack(right, z, "store_shelf_thrift[facing=west]", 2);
        }
        checkout(right - 1, 1);
    }

    private void liquor() {
        for (int z = 2; z <= back; z++) {
            stack(left, z, "store_shelf_liquor[facing=east]", 2);
            stack(right, z, "store_shelf_liquor[facing=west]", 2);
        }
        for (int x = left + 1; x < right; x++) {
            put(x, 1, back, "store_cooler[facing=north]");
            put(x, 2, back, "store_cooler_top[facing=north]");
        }
        aisles("store_shelf_liquor", 5, back - 2, right - 2, left + 3);
        for (int x = door + 2; x < right; x++) {
            put(x, 1, 2, x == door + 2 ? "store_register[facing=north]" : "store_counter[facing=north]");
        }
        toppled(5, back - 2);
        openSign();
    }

    private void hardware() {
        for (int z = 2; z <= back; z++) {
            put(left, 2, z, "store_pegboard[facing=west]");
            put(left, 1, z, z % 2 == 0 ? "store_paint_cans" : "store_shelf_hardware[facing=east]");
            put(right, 2, z, "store_pegboard[facing=east]");
            put(right, 1, z, "store_shelf_hardware[facing=west]");
        }
        for (int x = left + 1; x < right; x++) {
            stack(x, back, "store_lumber[facing=north]", 2);
        }
        aisles("store_shelf_hardware", 5, back - 2, right - 2, left + 3);
        checkout(left + 1, 2);
        toppled(5, back - 2);
    }

    // ---------------------------------------------------------------- pieces

    /** Double-sided shelving bays two high running back from z0 to z1, aisles two wide between. */
    private void aisles(String shelf, int z0, int z1, int maxX) {
        aisles(shelf, z0, z1, maxX, left + 2);
    }

    /**
     * Double-sided gondolas: two bays back to back, stocked both ways, with a walk between pairs and
     * one left free at maxX, so no bay faces a wall or the coolers; a shop too narrow for a pair gets
     * one single-sided run.
     */
    private void aisles(String shelf, int z0, int z1, int maxX, int firstX) {
        int x = firstX;
        for (; x + 1 <= maxX - 1; x += 4) {
            for (int z = z0; z <= z1; z++) {
                stack(x, z, shelf + "[facing=west]", 2);
                stack(x + 1, z, shelf + "[facing=east]", 2);
            }
        }
        if (x == firstX && firstX <= maxX - 1) {
            for (int z = z0; z <= z1; z++) {
                stack(firstX, z, shelf + "[facing=east]", 2);
            }
        }
    }

    /** One bay pulled over into the aisle it faces, its stock across the floor. */
    private void toppled(int z0, int z1) {
        if (rng.nextDouble() > 0.4 || z1 <= z0) {
            return;
        }
        int z = z0 + rng.nextInt(z1 - z0 + 1);
        for (int x = left + 1; x < right; x++) {
            String b = p.get(x, 1, z);
            if (b == null || !b.startsWith("store_shelf")) {
                continue;
            }
            int to = b.contains("facing=west") ? x - 1 : x + 1;
            if (air(to, 1, z)) {
                put(x, 1, z, "air");
                put(x, 2, z, "air");
                put(to, 1, z, "store_shelf_fallen[facing=north]");
                p.setIfAir(to, 1, z + 1, "store_spill");
                return;
            }
        }
    }

    /** Café tables with a chair either side, in a grid over the floor from the window back to z1. */
    private void tables(int z1, int stepX) {
        tables(z1, stepX, left + 1, right - 1);
    }

    private void tables(int z1, int stepX, int x0, int x1) {
        for (int z = 2; z + 2 <= z1; z += 3) {
            for (int x = x0; x <= x1; x += stepX) {
                if (Math.abs(x - door) <= (pairDoor ? 2 : 1) || !air(x, 1, z + 1)) {
                    continue;  // keep the way in from the door clear
                }
                put(x, 1, z + 1, "store_table");
                put(x, 1, z, "store_chair[facing=south]");
                if (rng.nextDouble() < 0.8) {
                    put(x, 1, z + 2, rng.nextDouble() < 0.15 ? "store_chair[facing=east]" : "store_chair[facing=north]");
                }
            }
        }
    }

    private void checkout(int x, int z) {
        put(x, 1, z, "store_register[facing=east]");
        put(x, 1, z + 1, "store_counter[facing=east]");
    }

    /** Booths in facing pairs with a table between, down a wall from the window to z1. */
    private void booths(int x, int z1) {
        for (int z = 1; z + 2 <= z1; z += 4) {
            if (doorway(x, z)) {
                continue;
            }
            put(x, 1, z, "store_booth[facing=south]");
            put(x, 1, z + 1, "store_table");
            put(x, 1, z + 2, "store_booth[facing=north]");
        }
    }

    /** The neon OPEN sign hung behind the window at the left end, as you face the shop. */
    private void openSign() {
        if (w >= 8 && rng.nextDouble() < 0.35 && air(right, 2, 1) && air(right - 1, 2, 1)) {
            Signs.open(p, rng, right, 2, 1);
        }
    }

    /** Places a block, naming the loot table it rolls when it's one of the searchable fittings. */
    private void put(int x, int y, int z, String block) {
        int open = block.indexOf('[');
        p.set(x, y, z, SEARCHABLE.contains(open < 0 ? block : block.substring(0, open)) ? withLoot(block, theme(block)) : block);
    }

    /** The loot table (data/zomboidcraft/loot_table/shop) for a fitting in this shop. */
    private String theme(String block) {
        if (block.contains("register") || block.contains("safe")) {
            return "till";
        }
        if (block.startsWith("filing_cabinet")) {
            return "office";
        }
        if (type == null) {
            return stockTable;
        }
        if (block.contains("cooler") || block.contains("vending")) {
            return type == Type.LIQUOR ? "liquor" : "drinks";
        }
        if (block.contains("washer") || block.contains("dryer") || block.contains("clothes") || block.contains("bin")) {
            return "clothes";
        }
        return switch (type) {
            case DINER, PIZZA -> "kitchen";
            default -> type.name().toLowerCase(Locale.ROOT);
        };
    }

    /** "store_shelf_grocery[facing=east]" becomes "store_shelf_grocery[facing=east,loot=grocery]". */
    static String withLoot(String block, String table) {
        int open = block.indexOf('[');
        return open < 0 ? block + "[loot=" + table + "]"
                : block.substring(0, block.length() - 1) + ",loot=" + table + "]";
    }

    private void stack(int x, int z, String block, int high) {
        for (int y = 1; y <= high; y++) {
            put(x, y, z, y > 1 && block.startsWith("store_shelf_") ? top(block) : block);
        }
    }

    /** "store_shelf_grocery[facing=east]" becomes "store_shelf_grocery_top[facing=east]": a bay's upper block. */
    static String top(String block) {
        int open = block.indexOf('[');
        return open < 0 ? block + "_top" : block.substring(0, open) + "_top" + block.substring(open);
    }

    private boolean air(int x, int y, int z) {
        String b = p.get(x, y, z);
        return b == null || b.equals("air");
    }

    private boolean doorway(int x, int z) {
        return z <= 2 && Math.abs(x - door) <= (pairDoor ? 2 : 1);
    }
}
