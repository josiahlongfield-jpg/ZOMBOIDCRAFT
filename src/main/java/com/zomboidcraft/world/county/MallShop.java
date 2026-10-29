package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.Shops;
import java.util.SplittableRandom;

/**
 * One shop unit of {@link Mall}'s rows, one storey: its storefront to the concourse, the trade
 * fitted out behind it, the stock room at the back and the door out to the service corridor.
 *
 * <p>Drawn in the unit's own frame, the walk-up shop's: the front along local z = 0 facing north
 * (out to the concourse), x from the party wall at 0 to the one at W - 1, the back wall at D - 1
 * with the corridor beyond it, y from the storey's floor (0) to its ceiling slab (H).
 */
final class MallShop {
    /** What the unit sells; the mall's own trades and the city's shops fitted out at mall size. */
    enum Trade {
        FASHION, JEANS, SHOES, SPORTS, ELECTRONICS, RECORDS, TOYS, BOOKS, JEWELRY, PHARMACY, CANDY, ARCADE, VIDEO,
        SALON, TRAVEL, THRIFT, VACANT
    }

    private static final Trade[] COMMON = {Trade.FASHION, Trade.FASHION, Trade.JEANS, Trade.SHOES, Trade.SHOES,
            Trade.SPORTS, Trade.ELECTRONICS, Trade.RECORDS, Trade.TOYS, Trade.BOOKS, Trade.JEWELRY, Trade.JEWELRY,
            Trade.PHARMACY, Trade.CANDY, Trade.ARCADE, Trade.VIDEO, Trade.SALON, Trade.TRAVEL, Trade.THRIFT,
            Trade.VACANT, Trade.VACANT};

    private final Placer q;
    private final SplittableRandom rng;
    private final Bldg b;
    private final int W, D, H;
    private final boolean power;
    private final Trade trade;
    /** The stock room's partition. */
    private final int part;

    /**
     * The unit between party walls x0 - 1 and x1 + 1 in the mall's frame, its back wall at
     * zBack and its front at zFront, the storey's floor at y.
     */
    MallShop(Placer mall, SplittableRandom rng, Bldg b, int x0, int x1, int zBack, int zFront, int y, int h, boolean power) {
        boolean north = zBack < zFront;
        // the north row faces south: turn half round about the unit's front corner so its front faces local north
        this.q = north ? mall.turned(x1 + 1, y, zFront, 2) : mall.turned(x0 - 1, y, zFront, 0);
        this.rng = rng;
        this.b = b;
        this.W = x1 - x0 + 3;
        this.D = Math.abs(zBack - zFront) + 1;
        this.H = h;
        this.power = power;
        this.trade = COMMON[rng.nextInt(COMMON.length)];
        this.part = D - 5;
    }

    private void set(int x, int y, int z, String block) {
        q.set(x, y, z, block);
    }

    private double chance() {
        return rng.nextDouble();
    }

    void build() {
        front();
        switch (trade) {
            case SPORTS -> city(Shops.Type.GUNS);
            case PHARMACY -> city(Shops.Type.PHARMACY);
            case JEWELRY -> city(Shops.Type.PAWN);
            case THRIFT -> city(Shops.Type.THRIFT);
            case VACANT -> vacant();
            default -> racks();
        }
        ceiling();
        backDoor();
    }

    // ------------------------------------------------------------------ the front

    /**
     * The storefront: glass in a bronze frame, the doors in the middle (a wide unit has its whole
     * front open behind a roll-down grille instead), the sign band over it.
     */
    private void front() {
        boolean open = W >= 12 && chance() < 0.6;
        int mid = W / 2;
        for (int x = 1; x <= W - 2; x++) {
            for (int y = 1; y < H; y++) {
                String block;
                if (y >= H - 2) {
                    block = "ml_band";
                } else if (open) {
                    block = "air";
                } else if (x == mid - 1 || x == mid) {
                    block = null;
                } else {
                    double r = chance();
                    block = r < b.damage * 0.25 ? "air" : r < b.damage * 0.45 ? "tx_glass_broken[axis=x]"
                            : "tx_glass[axis=x]";
                    if (block.equals("air") && y == 1) {
                        set(x, 1, 1, "glass_shards");
                    }
                }
                if (block != null) {
                    set(x, y, 0, block);
                }
            }
        }
        if (open) {
            // the grille: down and locked, or left half up (room to duck under), or rolled right up
            double r = chance();
            String g = r < 0.35 ? "st_grille" : r < 0.7 ? "st_grille_half" : null;
            for (int x = 1; x <= W - 2; x++) {
                set(x, H - 3, 0, "st_grille_box[facing=north]");
                if (g != null) {
                    for (int y = 1; y < H - 3; y++) {
                        if (g.equals("st_grille") || y == H - 4) {
                            set(x, y, 0, g + "[facing=north]");
                        }
                    }
                }
            }
        } else {
            Doors.pair(q, rng, mid - 1, 1, 0, Doors.SHOP, "south");
        }
        // the name over it in channel letters, lit on the generator or dark; FOR LEASE on an empty one
        if (trade != Trade.VACANT ? chance() < 0.92 : chance() < 0.6) {
            String key = trade == Trade.VACANT ? "lease" : trade.name().toLowerCase(java.util.Locale.ROOT);
            MallSigns.brand(q, rng, key, (W - 4) / 2, H - 2, -1, power && trade != Trade.VACANT && chance() < 0.55);
        }
        // the window's mannequins, dressed, one knocked over
        if (!open && (trade == Trade.FASHION || trade == Trade.JEANS || trade == Trade.SHOES)) {
            for (int x : new int[] {2, W - 3}) {
                if (x == mid - 1 || x == mid || chance() < 0.25) {
                    continue;
                }
                if (chance() < 0.2) {
                    set(x, 1, 1, "ml_mannequin_fallen[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
                } else {
                    set(x, 1, 1, "ml_mannequin_lo[facing=north]");
                    set(x, 2, 1, "ml_mannequin_hi[facing=north]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the trades

    /** A city shop's fit-out at mall size, its stock room's partition raised to the ceiling. */
    private void city(Shops.Type type) {
        new Shops(q, rng, type, W, D).fitOut();
        for (int x = 1; x <= W - 2; x++) {
            for (int y = 4; y < H; y++) {
                if ("drywall".equals(q.get(x, 3, part))) {
                    set(x, y, part, "drywall");
                }
            }
        }
    }

    /**
     * Racks of whatever it sold down the floor in rows, the counter and till by the door, the
     * fitting rooms or the stock room behind the partition.
     */
    private void racks() {
        String floor = switch (trade) {
            case FASHION, SHOES, JEWELRY, BOOKS, TRAVEL -> "home_floor_carpet_" + new String[] {"beige", "blue", "rose", "brown"}[rng.nextInt(4)];
            case JEANS, RECORDS, VIDEO -> "floor_boards";
            case ARCADE -> "home_floor_carpet_blue";
            default -> "floor_lino";
        };
        q.fill(1, 0, 1, W - 2, 0, part - 1, floor);
        Shops.backRooms(q, rng, W, D, W - 2, "store_shelf_empty", "scraps");
        for (int x = 1; x <= W - 2; x++) {
            for (int y = 4; y < H; y++) {
                set(x, y, part, "drywall");
            }
        }
        String rack = switch (trade) {
            case FASHION, JEANS, THRIFT -> "store_clothes_rack";
            case SHOES -> "store_shelf_thrift";
            case ELECTRONICS -> "store_shelf_pawn";
            case BOOKS -> "home_bookcase";
            case RECORDS, VIDEO -> "store_bin";
            case TOYS, CANDY -> "store_shelf_grocery";
            default -> "store_table";
        };
        // the counter along one side wall by the door, the till on it
        int cx = rng.nextBoolean() ? 1 : W - 2;
        for (int z = 2; z <= 4; z++) {
            set(cx, 1, z, "store_counter[facing=" + (cx == 1 ? "east" : "west") + "]");
        }
        set(cx, 2, 3, "store_register[facing=" + (cx == 1 ? "east" : "west") + "]");
        for (int x = 2; x <= W - 3; x += 3) {
            if (Math.abs(x - cx) < 2) {
                continue;
            }
            for (int z = 6; z < part - 1; z++) {
                if (chance() < 0.85) {
                    set(x, 1, z, chance() < 0.12 ? "store_shelf_empty[facing=east]" : rack + "[facing=east]");
                }
            }
        }
        b.mess(1, 1, W - 2, part - 1, 1, 0.05);
    }

    /** An empty unit: bare floor, the fittings gone, paper over the glass, a ladder and paint left. */
    private void vacant() {
        q.fill(1, 0, 1, W - 2, 0, D - 2, "floor_concrete");
        for (int x = 1; x <= W - 2; x++) {
            for (int y = 1; y <= 2; y++) {
                if (q.get(x, y, 0) != null && q.get(x, y, 0).startsWith("tx_glass")) {
                    set(x, y, 1, "paper");
                }
            }
        }
        b.mess(1, 1, W - 2, D - 2, 1, 0.04);
    }

    // ------------------------------------------------------------------ the rest

    /** Lamps in the ceiling in rows, a few still on (the mall's generator). */
    private void ceiling() {
        for (int x = 2; x <= W - 3; x += 3) {
            for (int z = 2; z < D - 1; z += 4) {
                if (q.get(x, H - 1, z) == null) {
                    set(x, H - 1, z, power && chance() < 0.15 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        // the city shops hang their lamps at a walk-up's height: up to this ceiling
        for (int x = 1; x <= W - 2; x++) {
            for (int z = 1; z < D - 1; z++) {
                String l = q.get(x, 3, z);
                if (l != null && l.startsWith("ceiling_light")) {
                    set(x, 3, z, "air");
                    set(x, H - 1, z, l);
                }
            }
        }
    }

    /** The door out the back to the service corridor. */
    private void backDoor() {
        Doors.abandoned(q, rng, W - 2, 1, D - 1, Doors.STEEL, "south", Doors.hinge(rng));
        set(W - 2, 1, D - 2, "air");
        set(W - 2, 2, D - 2, "air");
    }
}
