package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.ShopLights;
import com.zomboidcraft.world.city.Shops;
import java.util.SplittableRandom;

/**
 * A building on a small town's main street, in its lot's frame (front along z = 0 facing the
 * street, x along the front): a shop on the ground floor behind a painted shopfront, with a
 * sign or an awning over the glass, and offices or flats above, reached by a wooden stair at
 * the back.
 *
 * Four kinds of front, as Kentucky towns built them:
 * VICTORIAN brick with piers standing proud of the wall, stone lintels, a bracketed cornice and
 * a pediment with a date stone, sometimes a bay window; ITALIANATE with tall windows under hood
 * moulds and a deep timber cornice; FALSE_FRONT, a one-storey shop whose square facade rises
 * past its gable roof; and MODERN, a 1950s front of plate glass and a cantilevered canopy.
 */
final class Storefront {
    enum Style { VICTORIAN, ITALIANATE, FALSE_FRONT, MODERN }

    /** What the ground floor was: its sign, and the city shop it's fitted out like (null: its own fit-out). */
    enum Trade {
        GROCERY("grocery", Shops.Type.GROCERY), DRUGSTORE("drugstore", Shops.Type.PHARMACY),
        HARDWARE("kessler", Shops.Type.HARDWARE), GUNS("patriot", Shops.Type.GUNS), CAFE("cafe", Shops.Type.DINER),
        DINER("joes", Shops.Type.DINER), LAUNDROMAT("suds", Shops.Type.LAUNDROMAT), PAWN("luckypawn", Shops.Type.PAWN),
        PIZZA("tonys", Shops.Type.PIZZA), THRIFT("thrift", Shops.Type.THRIFT), LIQUOR("riverside", Shops.Type.LIQUOR),
        CASH("cash", Shops.Type.CASH), DIME("dime", Shops.Type.THRIFT), FURNITURE("furniture", Shops.Type.THRIFT),
        ANTIQUES("antiques", Shops.Type.PAWN), AUTOPARTS("autoparts", Shops.Type.HARDWARE),
        GENERAL("generalstore", Shops.Type.GROCERY), FEED("feed", Shops.Type.HARDWARE), COMPANY("company", Shops.Type.GROCERY),
        BANK("bank", null), BARBER("barber", null), TAVERN("tavern", null), REALTY("realty", null), VFW("vfw", null),
        HOTEL("hotel", null), VACANT(null, null);

        final String sign;
        final Shops.Type fit;

        Trade(String sign, Shops.Type fit) {
            this.sign = sign;
            this.fit = fit;
        }
    }

    private static final String[] BRICKS = {"brick_red", "brick_red", "brick_red", "brick_dark", "brick_dark", "brick_buff",
            "brick_orange"};
    private static final String[] PAINTED = {"plaster_cream", "plaster_sage", "plaster_blue", "plaster_rose"};
    private static final String[] WOOD = {"siding_white", "siding_grey", "siding_red", "siding_brown", "barn_grey"};
    private static final String[] PAINT = {"shop_paint_red", "shop_paint_navy", "shop_paint_teal", "shop_paint_black",
            "shop_paint_mustard", "shop_paint_green"};
    private static final String[] AWNINGS = {"awning", "awning_red", "awning_red", "awning_blue", "awning_maroon",
            "awning_yellow", "awning_teal"};
    /** Painted signs that last as ghost ads on a bare side wall. */
    private static final String[] GHOSTS = {"feed", "grain", "bait", "joes", "tonys", "route9", "furniture", "dime",
            "generalstore", "company"};
    private static final String[] BEDS = {"red", "blue", "white", "gray", "brown", "green", "light_blue", "yellow"};

    static final int STOREY = 4;

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final int w, d, floors;
    private final Style style;
    private final Trade trade;
    private final String wall, trim, paint;
    /** The building at the end of a row: its side walls are open to the street or a vacant lot. */
    boolean openWest, openEast;
    /** How much yard the lot has behind the building. */
    int yard;

    Storefront(Placer p, SplittableRandom rng, int w, int d, int floors, Style style, Trade trade) {
        this.p = p;
        this.rng = rng;
        this.b = new Bldg(p, rng);
        this.w = w;
        this.d = d;
        this.style = style;
        this.trade = trade;
        this.floors = style == Style.FALSE_FRONT || style == Style.MODERN ? 1 : floors;
        double r = rng.nextDouble();
        this.wall = switch (style) {
            case FALSE_FRONT -> r < 0.6 ? WOOD[rng.nextInt(WOOD.length)] : BRICKS[rng.nextInt(BRICKS.length)];
            case MODERN -> r < 0.4 ? "brick_buff" : r < 0.7 ? PAINTED[rng.nextInt(PAINTED.length)] : "concrete_panel";
            default -> r < 0.82 ? BRICKS[rng.nextInt(BRICKS.length)] : PAINTED[rng.nextInt(PAINTED.length)];
        };
        this.trim = wall.startsWith("siding") || wall.startsWith("barn") ? "minecraft:pale_oak_planks"
                : rng.nextDouble() < 0.7 ? "stone_trim" : "brick_dark";
        this.paint = PAINT[rng.nextInt(PAINT.length)];
        b.damage = 0.55 + rng.nextDouble() * 0.35;
        b.boarded = rng.nextDouble() < 0.25 ? 0.5 : 0.08;
        b.soot = rng.nextDouble() < 0.12 ? 0.5 : 0;
        b.lit = rng.nextDouble() < 0.06 ? 0.4 : 0;
        if (trade == Trade.VACANT) {
            b.boarded = 0.7;
        }
    }

    static Style style(SplittableRandom rng) {
        double r = rng.nextDouble();
        return r < 0.42 ? Style.VICTORIAN : r < 0.7 ? Style.ITALIANATE : r < 0.84 ? Style.MODERN : Style.FALSE_FRONT;
    }

    /** A trade for main street, weighted toward what a Kentucky county town had. */
    static Trade trade(SplittableRandom rng) {
        Trade[] common = {Trade.GROCERY, Trade.DRUGSTORE, Trade.HARDWARE, Trade.CAFE, Trade.DINER, Trade.BARBER,
                Trade.TAVERN, Trade.DIME, Trade.FURNITURE, Trade.ANTIQUES, Trade.AUTOPARTS, Trade.PAWN, Trade.THRIFT,
                Trade.LIQUOR, Trade.LAUNDROMAT, Trade.PIZZA, Trade.GUNS, Trade.REALTY, Trade.CASH, Trade.VFW,
                Trade.VACANT, Trade.VACANT};
        return common[rng.nextInt(common.length)];
    }

    int height() {
        return floors * STOREY;
    }

    void build() {
        int roofY = floors * STOREY;
        b.shell(0, 0, w - 1, d - 1, floors, STOREY, wall, "floor_lino", "floor_boards");
        shopfront();
        for (int k = 1; k < floors; k++) {
            upperFront(k);
        }
        top(roofY);
        sides(roofY);
        back(roofY);
        ground();
        for (int k = 1; k < floors; k++) {
            upstairs(k);
        }
        for (int k = 0; k < floors; k++) {
            b.lights(0, 0, w - 1, d - 1, k * STOREY + STOREY - 1, k == 0 ? 0.05 : 0.02);
        }
        weather(roofY);
    }

    // ------------------------------------------------------------------ the front

    private void shopfront() {
        boolean pair = w >= 12;
        int door = w / 2;
        boolean modern = style == Style.MODERN, wood = style == Style.FALSE_FRONT;
        String bulkhead = modern ? "concrete_dark" : wood ? "minecraft:spruce_planks" : paint;
        String pier = modern ? "minecraft:cobbled_deepslate" : wood ? trim : wall.startsWith("brick") ? wall : trim;
        for (int x = 0; x < w; x++) {
            boolean end = x == 0 || x == w - 1;
            boolean isDoor = x == door || pair && x == door - 1;
            if (end) {
                for (int y = 1; y <= 4; y++) {
                    p.set(x, y, 0, b.wall(pier, y));
                }
                continue;
            }
            if (!isDoor) {
                p.set(x, 1, 0, b.wall(bulkhead, 1));
                p.set(x, 2, 0, b.glass("x"));
            }
            p.set(x, 3, 0, b.glass("x"));
            // a mullion between the panes of a modern front
            if (modern && x % 3 == 0 && !isDoor) {
                p.set(x, 2, 0, "steel_mullion");
                p.set(x, 3, 0, "steel_mullion");
            }
            p.set(x, 4, 0, style == Style.VICTORIAN || style == Style.ITALIANATE ? b.wall(paint, 4) : b.wall(wall, 4));
        }
        if (pair) {
            b.doors(door - 1, 1, 0, "shop_door", "north");
        } else {
            b.door(door, 1, 0, wood ? "minecraft:spruce_door" : "shop_door", "north");
        }
        // the piers stand proud of the glass, up to the sign band (and on up the front of a brick block)
        if (!modern) {
            int top = style == Style.VICTORIAN ? floors * STOREY : 4;
            for (int y = 1; y <= top; y++) {
                p.set(0, y, -1, b.wall(y <= 4 ? pier : wall, y));
                p.set(w - 1, y, -1, b.wall(y <= 4 ? pier : wall, y));
            }
            p.set(0, 0, -1, "concrete_dark");
            p.set(w - 1, 0, -1, "concrete_dark");
        }
        // the sign over the window, an awning under it, or both
        boolean sign = trade.sign != null && rng.nextDouble() < 0.85;
        boolean awning = !modern && !wood && (!sign || rng.nextDouble() < 0.45);
        if (wood) {
            // a plank canopy over the boardwalk on two posts; the name goes up on the false front
            for (int x = 0; x < w; x++) {
                p.set(x, 4, -1, "minecraft:spruce_slab[type=top]");
                p.set(x, 4, -2, "minecraft:spruce_slab[type=top]");
                p.set(x, 0, -1, "minecraft:spruce_planks");
                p.set(x, 0, -2, "minecraft:spruce_planks");
            }
            for (int y = 1; y <= 3; y++) {
                p.set(0, y, -2, "minecraft:spruce_fence");
                p.set(w - 1, y, -2, "minecraft:spruce_fence");
            }
        } else if (modern) {
            for (int x = -1; x <= w; x++) {
                p.set(x, 4, -1, "minecraft:smooth_stone_slab[type=top]");
                p.set(x, 4, -2, "minecraft:smooth_stone_slab[type=top]");
            }
            if (sign) {
                Boards.fascia(p, rng, trade.sign, 1, 5, -1, w - 2);
            }
        } else if (sign && !Boards.fascia(p, rng, trade.sign, 1, 4, -1, w - 2)) {
            awning = true;
        }
        if (awning) {
            String a = AWNINGS[rng.nextInt(AWNINGS.length)];
            boolean deep = rng.nextDouble() < 0.4;
            for (int x = 1; x < w - 1; x++) {
                if (rng.nextDouble() < 0.12) {
                    continue;  // torn
                }
                p.set(x, 3, -1, a + "[facing=north]");
                if (deep && rng.nextDouble() > 0.15) {
                    p.set(x, 2, -2, a + "[facing=north]");  // a second fold, sagging lower
                }
            }
        }
        if (rng.nextDouble() < 0.5) {
            p.set(door + 1 < w - 1 ? door + 1 : door - 1, 3, -1, rng.nextDouble() < b.lit + 0.1 ? "wall_lantern" : "wall_lantern_off");
        }
        // a lamp box over the door: hung under a canopy, set into the awning, or lighting the sign
        if (modern && rng.nextDouble() < 0.7) {
            p.set(door, 3, -2, ShopLights.lamp(rng, 0.2));
            if (w >= 10 && rng.nextDouble() < 0.5) {
                p.set(1, 3, -2, ShopLights.lamp(rng, 0.3));
                p.set(w - 2, 3, -2, ShopLights.lamp(rng, 0.3));
            }
        } else if (!wood && rng.nextDouble() < 0.85) {
            int y = awning ? 3 : 5;
            if (w >= 10 && rng.nextDouble() < 0.5) {
                p.set(2, y, -1, ShopLights.lamp(rng, 0.15));
                p.set(w - 3, y, -1, ShopLights.lamp(rng, 0.15));
            } else {
                p.set(door, y, -1, ShopLights.lamp(rng, 0.15));
            }
        }
        // neon hung off the corner, for the trades that had it
        String blade = switch (trade) {
            case CAFE -> "cafe";
            case DRUGSTORE -> "drugs";
            case TAVERN, LIQUOR -> "bar";
            case HOTEL -> "hotel";
            case PAWN -> "pawn";
            case DINER -> "eat";
            default -> null;
        };
        int letters = blade == null ? 0 : switch (blade) {
            case "cafe" -> 4;
            case "drugs", "hotel" -> 5;
            case "pawn" -> 4;
            default -> 3;
        };
        int bladeTop = 4 + letters;
        if (blade != null && (floors >= 2 && bladeTop <= floors * STOREY + 1 || floors == 1 && letters <= 3)) {
            Boards.blade(p, rng, blade, letters, w - 1, bladeTop, -2, rng.nextDouble() < 0.4);
        }
    }

    /** The windows of an upper floor, with sills and lintels or hood moulds, maybe a bay window. */
    private void upperFront(int k) {
        int y0 = k * STOREY;
        boolean bay = style == Style.VICTORIAN && floors == 3 && k == 2 && w >= 9 && rng.nextDouble() < 0.5;
        int c = w / 2;
        for (int x = 1; x < w - 1; x++) {
            boolean window = style == Style.ITALIANATE ? (x - 1) % 2 == 1 || w <= 6 : (x - 1) % 3 != 0;
            if (!window || bay && Math.abs(x - c) <= 2) {
                continue;
            }
            b.window(x, y0 + 1, 0, "north", 2, "sill");
            if (style == Style.ITALIANATE) {
                p.set(x, y0 + 3, -1, "minecraft:stone_brick_stairs[facing=south,half=top]");
            } else {
                p.set(x, y0 + 3, 0, b.wall(trim, y0 + 3));
            }
        }
        // a sill course in stone across the whole front
        if (style == Style.VICTORIAN) {
            for (int x = 1; x < w - 1; x++) {
                p.set(x, y0, 0, b.wall(trim, y0));
            }
        }
        if (bay) {
            for (int x = c - 2; x <= c + 2; x++) {
                p.set(x, y0, -1, b.wall(trim, y0));
                if (x != c - 2 && x != c + 2) {
                    p.set(x, y0 - 1, -1, "minecraft:stone_brick_stairs[facing=south,half=top]");
                    p.set(x, y0 + 1, 0, "air");
                    p.set(x, y0 + 2, 0, "air");
                    p.set(x, y0 + 3, 0, "air");
                    for (int y = y0 + 1; y <= y0 + 3; y++) {
                        p.set(x, y, -1, y == y0 + 3 ? b.wall(wall, y) : b.glass("x"));
                    }
                } else {
                    for (int y = y0 + 1; y <= y0 + 3; y++) {
                        p.set(x, y, -1, y == y0 + 3 ? b.wall(wall, y) : b.glass("z"));
                    }
                }
                p.set(x, y0 + 4, -1, b.wall(trim, y0 + 4));
            }
        }
    }

    /** The roof line: cornice, parapet and pediment, a false front, or a modern parapet. */
    private void top(int roofY) {
        int c = w / 2;
        switch (style) {
            case VICTORIAN -> {
                b.flat(0, 0, w - 1, d - 1, roofY, wall, 2, "stone_trim");
                for (int x = -1; x <= w; x++) {
                    p.set(x, roofY + 1, -1, b.wall("stone_trim", roofY + 1));
                    if (x >= 1 && x <= w - 2 && x % 2 == 1 && p.get(x, roofY, -1) == null) {
                        p.set(x, roofY, -1, "minecraft:dark_oak_stairs[facing=south,half=top]");
                    }
                }
                if (w >= 7) {
                    for (int x = c - 1; x <= c + 1; x++) {
                        p.set(x, roofY + 3, 0, b.wall(wall, roofY + 3));
                    }
                    p.set(c, roofY + 4, 0, "stone_trim");
                    p.set(c - 1, roofY + 4, 0, "minecraft:stone_brick_stairs[facing=east]");
                    p.set(c + 1, roofY + 4, 0, "minecraft:stone_brick_stairs[facing=west]");
                    p.set(c, roofY + 2, 0, "minecraft:chiseled_stone_bricks");
                }
            }
            case ITALIANATE -> {
                b.flat(0, 0, w - 1, d - 1, roofY, wall, 1, null);
                for (int x = -1; x <= w; x++) {
                    p.set(x, roofY + 1, -1, "minecraft:dark_oak_planks");
                    p.set(x, roofY + 1, -2, "minecraft:dark_oak_slab[type=top]");
                    if (x >= 0 && x <= w - 1 && x % 2 == 0) {
                        p.set(x, roofY, -1, "minecraft:dark_oak_stairs[facing=south,half=top]");
                    }
                }
            }
            case MODERN -> b.flat(0, 0, w - 1, d - 1, roofY, wall, 2, "minecraft:smooth_stone_slab[type=bottom]");
            case FALSE_FRONT -> {
                String family = rng.nextBoolean() ? "minecraft:waxed_weathered_cut_copper" : "minecraft:spruce";
                b.gable(0, w - 1, 1, d - 1, roofY + 1, family, true, wall, 1);
                int rise = (w + 1) / 2 + 1;
                boolean stepped = rng.nextBoolean();
                for (int x = 0; x < w; x++) {
                    int top = roofY + rise - (stepped && (x < 2 || x > w - 3) ? 1 : 0);
                    for (int y = roofY + 1; y <= top; y++) {
                        p.set(x, y, 0, b.wall(wall, y));
                    }
                    p.set(x, top + 1, 0, "minecraft:spruce_slab[type=bottom]");
                    p.set(x, top, -1, "minecraft:spruce_slab[type=top]");
                }
                // the name painted big across the false front
                if (trade.sign != null) {
                    Boards.fascia(p, rng, trade.sign, 0, roofY + 2, -1, w);
                }
            }
        }
        // roof clutter: a vent stack, a chimney on the party wall, an aerial
        if (style != Style.FALSE_FRONT) {
            if (rng.nextDouble() < 0.6 && w >= 6 && d >= 8) {
                int x = 2 + rng.nextInt(w - 4), z = d / 2 + rng.nextInt(Math.max(1, d / 2 - 2));
                p.fill(x, roofY + 1, z, x + (w >= 9 ? 1 : 0), roofY + 1, z, "hvac");
            }
            if (rng.nextDouble() < 0.5) {
                int x = rng.nextBoolean() ? 0 : w - 1, z = 2 + rng.nextInt(Math.max(1, d - 4));
                p.fill(x, roofY + 1, z, x, roofY + 3, z, "minecraft:bricks");
                p.set(x, roofY + 4, z, "minecraft:brick_wall");
            }
            if (rng.nextDouble() < 0.3) {
                int x = 1 + rng.nextInt(Math.max(1, w - 2)), z = d - 3;
                p.set(x, roofY + 1, z, "minecraft:iron_bars");
                p.set(x, roofY + 2, z, "minecraft:iron_bars");
                p.set(x, roofY + 3, z, "minecraft:lightning_rod");
            }
        }
    }

    // ------------------------------------------------------------------ sides and back

    private void sides(int roofY) {
        for (int side = 0; side < 2; side++) {
            boolean open = side == 0 ? openWest : openEast;
            if (!open) {
                continue;
            }
            // draw the side as if it were a front: west side turned three quarters, east one quarter
            Placer s = side == 0 ? p.turned(0, 0, d - 1, 3) : p.turned(w - 1, 0, 0, 1);
            Bldg sb = new Bldg(s, rng);
            sb.damage = b.damage;
            sb.boarded = b.boarded;
            for (int k = 1; k < floors; k++) {
                for (int x = 3; x < d - 3; x += 4) {
                    sb.window(x, k * STOREY + 1, 0, "north", 2, "sill");
                }
            }
            if (rng.nextDouble() < 0.6 && floors >= 2) {
                Boards.fascia(s, rng, GHOSTS[rng.nextInt(GHOSTS.length)], 1, roofY - 1, -1, d - 2);
            }
            // ivy up the bare brick
            for (int i = 0; i < 3; i++) {
                int x = rng.nextInt(d);
                for (int y = 1; y <= 2 + rng.nextInt(roofY); y++) {
                    s.setIfAir(x, y, -1, "vines[facing=south]");
                }
            }
        }
    }

    private void back(int roofY) {
        int z = d - 1;
        b.door(w - 2, 1, z, "fire_door", "south");
        for (int x = 2; x < w - 3; x += 3) {
            b.window(x, 2, z, "south", 1, null);
        }
        for (int k = 1; k < floors; k++) {
            for (int x = 2; x < w - 1; x += 3) {
                if (k == 1 && x >= 4 && x <= 6) {
                    continue;
                }
                b.window(x, k * STOREY + 1, z, "south", 2, null);
            }
        }
        if (floors >= 2 && w >= 8) {
            // a wooden stair up the back to the flats over the shop
            for (int i = 0; i < 4; i++) {
                p.set(1 + i, 1 + i, d, "minecraft:spruce_stairs[facing=east]");
                if (i > 0) {
                    p.set(1 + i, i, d, "minecraft:spruce_fence");
                }
            }
            for (int x = 5; x <= 6; x++) {
                p.set(x, 4, d, "minecraft:spruce_slab[type=top]");
                p.set(x, 4, d + 1, "minecraft:spruce_slab[type=top]");
                p.set(x, 5, d + 1, "minecraft:spruce_fence");
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, d + 1, x == 6 ? "minecraft:spruce_fence" : "air");
                }
            }
            p.set(4, 5, d + 1, "minecraft:spruce_fence");
            b.door(6, 5, z, "flat_door", "south");
        }
        // the yard: rubbish, a dumpster, pallets, someone's truck
        for (int zz = d; zz < d + yard; zz++) {
            for (int x = 0; x < w; x++) {
                if (p.get(x, 0, zz) == null || p.get(x, 1, zz) == null) {
                    p.set(x, 0, zz, rng.nextDouble() < 0.6 ? "minecraft:gravel" : rng.nextDouble() < 0.5 ? "minecraft:coarse_dirt" : "asphalt_crack");
                }
            }
        }
        if (yard >= 3) {
            int x = w >= 8 ? w - 4 : 1;
            p.setIfAir(x, 1, d + yard - 2, "dumpster[facing=north]");
            for (int i = 0; i < 3; i++) {
                p.setIfAir(rng.nextInt(w), 1, d + rng.nextInt(yard), rng.nextBoolean() ? "trash_bag[facing=east]" : "crate");
            }
        }
    }

    // ------------------------------------------------------------------ inside

    private void ground() {
        if (trade.fit != null) {
            new Shops(p, rng, trade.fit, w, d).fitOut();
            return;
        }
        int r = w - 2, back = d - 2;
        switch (trade) {
            case BANK -> {
                b.floor(1, 1, r, back, 0, "lobby_floor_white");
                int counter = d / 2;
                for (int x = 1; x <= r; x++) {
                    if (x == r) {
                        b.door(x, 1, counter, "fire_door", "north");
                        continue;
                    }
                    p.set(x, 1, counter, "store_counter[facing=north]");
                    p.set(x, 2, counter, "store_teller_glass[axis=x]");
                    p.set(x, 3, counter, "store_teller_glass[axis=x]");
                }
                p.set(2, 1, back, "store_safe[facing=north]");
                p.set(3, 1, back, "store_safe[facing=north]");
                p.set(r - 1, 1, counter + 2, "desk[facing=north]");
                p.set(r - 1, 1, counter + 3, "office_chair[facing=north]");
                for (int x = 2; x < r; x += 3) {
                    p.set(x, 1, 3, "store_chair[facing=north]");
                }
                p.set(1, 1, 2, "planter");
            }
            case BARBER -> {
                b.floor(1, 1, r, back, 0, "floor_checker");
                for (int z = 3; z < back - 1; z += 3) {
                    p.set(1, 1, z, "store_chair[facing=east]");
                    p.set(1, 2, z, "minecraft:glass_pane");
                    p.set(r, 1, z, "store_chair[facing=west]");
                }
                p.set(r, 1, 2, "store_register[facing=west]");
                p.set(1, 1, back, "minecraft:cauldron");
                p.set(2, 1, back, "store_counter[facing=north]");
            }
            case TAVERN -> {
                b.floor(1, 1, r, back, 0, "floor_boards");
                for (int z = 2; z < back - 1; z++) {
                    p.set(r - 1, 1, z, "store_counter[facing=west]");
                    p.set(r, 1, z, z % 3 == 0 ? "store_shelf_liquor[facing=west]" : "store_cooler[facing=west]");
                    p.set(r - 2, 1, z, z % 2 == 0 ? "store_stool" : "air");
                }
                for (int z = 3; z < back - 1; z += 4) {
                    p.set(2, 1, z, "store_table");
                    p.set(2, 1, z + 1, "store_chair[facing=north]");
                    p.set(3, 1, z, "store_chair[facing=west]");
                }
                // the pool table, its cloth long gone to mould
                if (w >= 9 && d >= 14) {
                    for (int z = back - 4; z <= back - 2; z++) {
                        p.set(4, 1, z, "minecraft:dark_oak_planks");
                        p.set(4, 2, z, "minecraft:green_carpet");
                    }
                }
            }
            case HOTEL -> {
                b.floor(1, 1, r, back, 0, "lobby_floor_dark");
                p.set(r, 1, 3, "reception_desk[facing=west]");
                p.set(r, 1, 4, "reception_desk[facing=west]");
                p.set(1, 1, 3, "minecraft:dark_oak_stairs[facing=east]");
                p.set(1, 1, 4, "minecraft:dark_oak_stairs[facing=east]");
                p.set(2, 1, 6, "planter");
            }
            case REALTY, VFW -> {
                b.floor(1, 1, r, back, 0, trade == Trade.VFW ? "floor_boards" : "office_floor");
                for (int x = 2; x < r; x += 3) {
                    for (int z = 3; z < back - 1; z += 4) {
                        p.set(x, 1, z, trade == Trade.VFW ? "store_table" : "desk[facing=north]");
                        p.set(x, 1, z + 1, trade == Trade.VFW ? "store_chair[facing=north]" : "office_chair[facing=north]");
                    }
                }
                p.set(r, 1, back, "filing_cabinet[facing=west]");
                p.set(r, 1, back - 1, "filing_cabinet[facing=west]");
            }
            default -> {
                // vacant: stripped to the boards, a ladder and paint tins from a refit that never finished
                b.floor(1, 1, r, back, 0, "floor_concrete");
                p.set(2, 1, back, "store_paint_cans");
                p.set(r, 1, 3, "minecraft:ladder[facing=west]");
            }
        }
        b.mess(1, 1, r, back, 1, 0.06);
    }

    /** A flat or an office over the shop. */
    private void upstairs(int k) {
        int y = k * STOREY + 1;
        int r = w - 2, back = d - 2;
        int mid = d / 2;
        boolean office = trade == Trade.BANK || trade == Trade.REALTY || rng.nextDouble() < 0.3;
        if (trade == Trade.HOTEL) {
            hotelFloor(y, r, back, k + 1 < floors);
            return;
        }
        for (int x = 1; x <= r; x++) {
            for (int h = 0; h < 3; h++) {
                p.set(x, y + h, mid, rng.nextDouble() < 0.15 ? "wallpaper_rotten" : rng.nextDouble() < 0.1 ? "drywall_peel" : "drywall");
            }
        }
        b.door(w / 2, y, mid, "flat_door", "north");
        if (office) {
            for (int x = 2; x < r; x += 3) {
                p.set(x, y, 2, "desk[facing=north]");
                p.set(x, y, 3, rng.nextDouble() < 0.3 ? "office_chair_fallen[facing=east]" : "office_chair[facing=north]");
            }
            p.set(r, y, mid + 1, "filing_cabinet[facing=west]");
            p.set(r, y, mid + 2, "filing_cabinet[facing=west]");
        } else {
            String colour = BEDS[rng.nextInt(BEDS.length)];
            p.set(1, y, back - 1, "minecraft:" + colour + "_bed[facing=south,part=head]");
            p.set(1, y, back - 2, "minecraft:" + colour + "_bed[facing=south,part=foot]");
            p.set(2, y, back, "minecraft:barrel[facing=up,loot=clothes]");
            for (int x = 1; x <= Math.min(r, 3); x++) {
                p.set(x, y, mid - 1, "minecraft:dark_oak_stairs[facing=north]");
            }
            p.set(r, y, 2, "store_oven[facing=west]");
            p.set(r, y, 3, "minecraft:barrel[facing=up,loot=kitchen]");
            p.set(r - 2, y, 3, "store_table");
            if (rng.nextDouble() < 0.5) {
                p.set(r - 2, y, 2, "store_chair[facing=south]");
            }
        }
        // the stair on to the next floor up
        if (k + 1 < floors) {
            b.stair(r, back, y, STOREY, "north", "minecraft:spruce");
        }
        b.mess(1, 1, r, back, y, 0.05);
    }

    private void hotelFloor(int y, int r, int back, boolean stairUp) {
        // a corridor down the middle, rooms either side
        int c = w / 2;
        for (int z = 1; z <= back; z++) {
            for (int h = 0; h < 3; h++) {
                p.set(c - 1, y + h, z, "drywall");
                p.set(c + 1, y + h, z, "drywall");
            }
        }
        for (int z = 2; z < back; z += 4) {
            b.door(c - 1, y, z, "flat_door", "east");
            b.door(c + 1, y, z, "flat_door", "west");
            String colour = BEDS[rng.nextInt(BEDS.length)];
            if (c - 2 >= 1) {
                p.set(1, y, z, "minecraft:" + colour + "_bed[facing=west,part=head]");
                p.set(2, y, z, c - 2 >= 2 ? "minecraft:" + colour + "_bed[facing=west,part=foot]" : "air");
            }
            if (r >= c + 3) {
                p.set(r, y, z, "minecraft:" + colour + "_bed[facing=east,part=head]");
                p.set(r - 1, y, z, "minecraft:" + colour + "_bed[facing=east,part=foot]");
            }
        }
        if (stairUp) {
            b.stair(c, back, y, STOREY, "north", "minecraft:spruce");
        }
        b.mess(1, 1, r, back, y, 0.05);
    }

    // ------------------------------------------------------------------ weathering

    private void weather(int roofY) {
        for (int i = 0; i < 2 + rng.nextInt(3); i++) {
            int x = rng.nextInt(w);
            int len = 2 + rng.nextInt(roofY);
            for (int y = 1; y <= len; y++) {
                p.setIfAir(x, y, -1, "vines[facing=south]");
            }
        }
        if (b.soot > 0) {
            for (int x = 1; x < w - 1; x++) {
                p.set(x, 0, 1, "burnt_ground");
            }
        }
    }
}
