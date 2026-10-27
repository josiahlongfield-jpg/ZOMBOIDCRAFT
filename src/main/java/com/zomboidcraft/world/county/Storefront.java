package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.ShopLights;
import com.zomboidcraft.world.city.Shops;
import com.zomboidcraft.world.city.Signs;
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
        HOTEL("hotel", null), FUEL("fuel", Shops.Type.GROCERY), BAIT("bait", Shops.Type.HARDWARE), VACANT(null, null);

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
    private static final String[] VITROLITE = {"front_vitrolite_black", "front_vitrolite_black", "front_vitrolite_green",
            "front_vitrolite_cream"};
    private static final String[] CORNICE = {"white", "white", "brown", "green", "black"};
    private static final String[] YEARS = {"1884", "1891", "1896", "1902", "1909"};
    private static final String[] AWNINGS = {"awning", "awning_red", "awning_red", "awning_blue", "awning_maroon",
            "awning_yellow", "awning_teal"};
    /** Painted signs that last as ghost ads on a bare side wall. */
    private static final String[] GHOSTS = {"feed", "grain", "bait", "joes", "tonys", "route9", "furniture", "dime",
            "generalstore", "company"};

    static final int STOREY = 4;

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final int w, d, floors;
    private final Style style;
    private final Trade trade;
    private final String wall, trim, paint;
    /** A 1950s front's structural glass (null: plain concrete), an Italianate cornice's paint. */
    private final String glass, cornicePaint;
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
        boolean frame = wall.startsWith("siding") || wall.startsWith("barn");
        this.trim = frame ? (wall.equals("siding_brown") || wall.equals("barn_grey") ? "front_trim_brown" : "front_trim_white")
                : rng.nextDouble() < 0.7 ? "stone_trim" : "brick_dark";
        this.paint = PAINT[rng.nextInt(PAINT.length)];
        this.glass = rng.nextDouble() < 0.6 ? VITROLITE[rng.nextInt(VITROLITE.length)] : null;
        this.cornicePaint = CORNICE[rng.nextInt(CORNICE.length)];
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
        Trades trades = new Trades(p, rng, b, w, d);
        boolean stairs = trade == Trade.HOTEL && floors > 1 && Trades.innerStair(w, d);
        ground(trades, stairs);
        b.lights(0, 0, w - 1, d - 1, STOREY - 1, 0.05);
        if (stairs) {
            // the hotel's stairs inside at the back, a floor of rooms off each landing
            // up through a flat roof into a hut (a false front's pitched roof has no way out)
            trades.hotelStairs(floors, STOREY, b.lit, style == Style.FALSE_FRONT ? null : wall);
        } else if (floors > 1) {
            // a flat on each floor over the shop (rooms in a small hotel), up the porches at the back
            int[] doors = new int[floors];
            for (int k = 1; k < floors; k++) {
                doors[k] = trade == Trade.HOTEL ? trades.rooms(k * STOREY, d - 1, STOREY)
                        : Flats.floor(p, rng, w, d - 1, k * STOREY, 1, b.lit);
                Doors.abandoned(p, rng, doors[k], k * STOREY + 1, d - 1, Doors.FLAT, "north", Doors.hinge(rng));
            }
            Flats.porches(p, rng, w, d, floors, STOREY, doors, style != Style.FALSE_FRONT);
        }
        weather(roofY);
    }

    // ------------------------------------------------------------------ the front

    private void shopfront() {
        boolean pair = w >= 12;
        int door = w / 2;
        boolean modern = style == Style.MODERN, wood = style == Style.FALSE_FRONT;
        String bulkhead = modern ? (glass != null ? glass : "concrete_dark") : wood ? "wood_panel" : paint;
        String pier = modern ? (glass != null ? glass : "concrete_dark") : wood ? trim : wall.startsWith("brick") ? wall : trim;
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
            b.door(door, 1, 0, wood ? (trim.equals("front_trim_white") ? "ext_door_white" : "ext_door_oak") : "shop_door", "north");
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
        // the sign over the window, an awning under it, or both; or a tin gallery over the sidewalk
        boolean sign = trade.sign != null && rng.nextDouble() < 0.85;
        boolean gallery = !modern && !wood && floors >= 2 && rng.nextDouble() < 0.22;
        boolean awning = !modern && !wood && !gallery && (!sign || rng.nextDouble() < 0.45);
        if (wood) {
            // a plank canopy over the boardwalk on posts; the name goes up on the false front
            for (int x = 0; x < w; x++) {
                p.set(x, 4, -1, "front_canopy_wood[facing=north]");
                p.set(x, 4, -2, rng.nextDouble() < b.damage * 0.08 ? "air" : "front_canopy_wood_edge[facing=north]");
                p.set(x, 0, -1, "ext_porch_boards");
                p.set(x, 0, -2, "ext_porch_boards");
            }
            posts(w >= 10, "ext_post_base", "ext_post", "ext_post");
        } else if (modern) {
            // the aluminium canopy, hung on rods from the wall at its ends
            String edge = "front_canopy_metal_edge_" + (rng.nextBoolean() ? "red" : "teal") + "[facing=north]";
            for (int x = 0; x < w; x++) {
                p.set(x, 4, -1, "front_canopy_metal[facing=north]");
                p.set(x, 4, -2, edge);
            }
            p.set(0, 5, -1, "front_canopy_rod[facing=north]");
            p.set(w - 1, 5, -1, "front_canopy_rod[facing=north]");
            if (sign) {
                Boards.fascia(p, rng, trade.sign, 1, 5, -1, w - 2);
            }
        } else if (gallery) {
            // tin on a plate along the wall and a beam on fluted iron posts at the curb, the name on its edge
            String tin = rng.nextDouble() < 0.6 ? "red" : "green";
            for (int x = 0; x < w; x++) {
                if (x > 0 && x < w - 1) {
                    p.set(x, 4, -1, "front_gallery_" + tin + "[facing=north]");
                }
                p.set(x, 4, -2, "front_gallery_" + tin + "_edge[facing=north]");
            }
            posts(true, "front_gallery_post_base", "front_gallery_post", "front_gallery_post_top[facing=north]");
            if (sign) {
                Boards.fascia(p, rng, trade.sign, 1, 4, -3, w - 2);
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
                    p.set(x, 3, -2, a + "[facing=north]");  // a deeper awning, still a body's height up
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
            case TAVERN -> "bar";
            case LIQUOR -> "liquor";
            case HOTEL -> "hotel";
            case PAWN -> "pawn";
            case DINER -> "eat";
            case GUNS -> "guns";
            case LAUNDROMAT -> "wash";
            case PIZZA -> "pizza";
            case CASH -> "cash";
            default -> null;
        };
        int letters = blade == null ? 0 : Signs.bladeLength(blade);
        int bladeTop = 4 + letters;
        if (blade != null && (floors >= 2 && bladeTop <= floors * STOREY + 1 || floors == 1 && letters <= 3)) {
            Boards.blade(p, rng, blade, letters, w - 1, bladeTop, -2, rng.nextDouble() < 0.4);
        }
    }

    /**
     * Posts at the curb (z = -2) carrying a canopy's beam: at the ends, and every four between when
     * middle, kept out of the way in to the door.
     */
    private void posts(boolean middle, String base, String shaft, String top) {
        int door = w / 2;
        for (int x = 0; x < w; x++) {
            boolean end = x == 0 || x == w - 1;
            boolean between = middle && x % 4 == 0 && x > 1 && x < w - 2 && Math.abs(x - door) > 1 && x != door - 2;
            if (!end && !between) {
                continue;
            }
            p.set(x, 1, -2, base);
            p.set(x, 2, -2, shaft);
            p.set(x, 3, -2, top);
        }
    }

    /** The windows of an upper floor, with sills and lintels or hood moulds, maybe a bay window. */
    private void upperFront(int k) {
        int y0 = k * STOREY;
        boolean bay = style == Style.VICTORIAN && floors == 3 && k == 2 && w >= 9 && rng.nextDouble() < 0.5;
        // stone lintels with a keystone standing proud over a brick block's windows
        boolean hoods = trim.equals("stone_trim") && style == Style.VICTORIAN && (k + w) % 2 == 0;
        int c = w / 2;
        for (int x = 1; x < w - 1; x++) {
            boolean window = style == Style.ITALIANATE ? (x - 1) % 2 == 1 || w <= 6 : (x - 1) % 3 != 0;
            if (!window || bay && Math.abs(x - c) <= 2) {
                continue;
            }
            b.window(x, y0 + 1, 0, "north", 2, "sill");
            if (style == Style.ITALIANATE) {
                p.set(x, y0 + 3, -1, "front_hood_arch[facing=north]");
            } else {
                p.set(x, y0 + 3, 0, b.wall(trim, y0 + 3));
                if (hoods) {
                    p.set(x, y0 + 3, -1, "front_hood_flat[facing=north]");
                }
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
                    p.set(x, y0 - 1, -1, "front_bay_corbel[facing=north]");
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
                // a corbelled table of brick (stone on a painted front) under a moulded stone cornice
                b.flat(0, 0, w - 1, d - 1, roofY, wall, 2, "stone_trim");
                String corbel = "front_corbel_" + (wall.startsWith("brick_") ? wall.substring(6) : "stone") + "[facing=north]";
                for (int x = 0; x < w; x++) {
                    p.set(x, roofY + 1, -1, "front_cornice_stone[facing=north]");
                    if (x >= 1 && x <= w - 2 && p.get(x, roofY, -1) == null) {
                        p.set(x, roofY, -1, corbel);
                    }
                }
                if (w >= 7) {
                    // the pediment, its year cut in the stone under it and a finial on top
                    for (int x = c - 1; x <= c + 1; x++) {
                        p.set(x, roofY + 3, 0, b.wall(wall, roofY + 3));
                    }
                    p.set(c, roofY + 4, 0, "stone_trim");
                    p.set(c - 1, roofY + 4, 0, "front_pediment_l[facing=north]");
                    p.set(c + 1, roofY + 4, 0, "front_pediment_r[facing=north]");
                    p.set(c, roofY + 5, 0, "front_finial");
                    p.set(c, roofY + 2, 0, "front_datestone_" + YEARS[rng.nextInt(YEARS.length)] + "[facing=north]");
                }
            }
            case ITALIANATE -> {
                // a deep cornice of painted boards on scroll brackets
                b.flat(0, 0, w - 1, d - 1, roofY, wall, 1, null);
                for (int x = 0; x < w; x++) {
                    p.set(x, roofY + 1, -1, "front_cornice_wood_" + cornicePaint + "[facing=north]");
                    if (x % 2 == 0 && p.get(x, roofY, -1) == null) {
                        p.set(x, roofY, -1, "front_bracket_wood_" + cornicePaint + "[facing=north]");
                    }
                }
            }
            case MODERN -> b.flat(0, 0, w - 1, d - 1, roofY, wall, 2, "front_coping_metal");
            case FALSE_FRONT -> {
                String[] kinds = {"tin_galv", "tin_galv", "tin_red", "tin_green", "brown", "charcoal"};
                b.roofGable(0, w - 1, 1, d - 1, roofY + 1, kinds[rng.nextInt(kinds.length)], true, wall, 1);
                int rise = (w + 1) / 2 + 1;
                boolean stepped = rng.nextBoolean();
                // a cap board along the top of a frame front, a stone cornice on a brick one
                String cap = wall.startsWith("brick") ? "front_cornice_stone[facing=north]"
                        : (trim.equals("front_trim_white") ? "front_cap_white" : "front_cap_brown") + "[facing=north]";
                for (int x = 0; x < w; x++) {
                    int top = roofY + rise - (stepped && (x < 2 || x > w - 3) ? 1 : 0);
                    for (int y = roofY + 1; y <= top; y++) {
                        p.set(x, y, 0, b.wall(wall, y));
                    }
                    p.set(x, top, -1, cap);
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
                // a chimney stack on the party wall, its crown and flue pot on top
                int x = rng.nextBoolean() ? 0 : w - 1, z = 2 + rng.nextInt(Math.max(1, d - 4));
                String brick = wall.startsWith("brick") ? wall : "brick_red";
                for (int y = roofY + 1; y <= roofY + 3; y++) {
                    p.set(x, y, z, b.wall(brick, y));
                }
                p.set(x, roofY + 4, z, "ext_crown");
            }
            if (rng.nextDouble() < 0.3) {
                p.set(1 + rng.nextInt(Math.max(1, w - 2)), roofY + 1, d - 3, "ext_aerial[facing=" + (rng.nextBoolean() ? "north" : "east") + "]");
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
                b.window(x, k * STOREY + 1, z, "south", 2, null);
            }
        }
        // the yard: old asphalt broken up by patches of dirt and weeds; rubbish, a dumpster, pallets
        int[][] patches = new int[2 + rng.nextInt(3)][];
        for (int i = 0; i < patches.length; i++) {
            patches[i] = new int[] {rng.nextInt(w), d + rng.nextInt(Math.max(1, yard)), 1 + rng.nextInt(3)};
        }
        for (int zz = d; zz < d + yard; zz++) {
            for (int x = 0; x < w; x++) {
                if (p.get(x, 0, zz) != null && p.get(x, 1, zz) != null) {
                    continue;
                }
                boolean dirt = false;
                for (int[] c : patches) {
                    int dx = x - c[0], dz = zz - c[1];
                    dirt |= dx * dx + dz * dz <= c[2] * c[2] + rng.nextInt(2);
                }
                p.set(x, 0, zz, dirt ? "dirt_withered" : rng.nextDouble() < 0.4 ? "asphalt_crack" : "asphalt");
                if (dirt && rng.nextDouble() < 0.35) {
                    p.setIfAir(x, 1, zz, "weeds");
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

    private void ground(Trades trades, boolean stairs) {
        if (trade.fit != null) {
            new Shops(p, rng, trade.fit, w, d).fitOut();
            return;
        }
        switch (trade) {
            case BANK -> trades.bank();
            case BARBER -> trades.barber();
            case TAVERN -> trades.tavern();
            case HOTEL -> trades.hotelLobby(stairs);
            case REALTY -> trades.realty();
            case VFW -> trades.vfw();
            default -> trades.vacant();
        }
        b.mess(1, 1, w - 2, d - 2, 1, 0.06);
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
