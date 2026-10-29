package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven's Old Market on a lot of three blocks by two in the old harbor quarter: the granite
 * market house down the middle ({@link MarketHall}) with its porticoes at the ends and its dome,
 * a cobbled lane either side of it full of pushcarts and umbrellas under strings of bulbs, and
 * beyond the lanes two long rows of brick shops with lofts and flats over them ({@link Storefront}),
 * their fronts to the lanes and their back porches on the service alleys along the streets. The
 * plazas at the ends: the founder's statue and the flagpole at the west portico, the fountain at
 * the east.
 *
 * <p>The market fed the island in the first weeks; when the food ran out the stall keepers kept
 * a barter post in the rotunda, chained the lanes' east ends shut with carts and barriers, and
 * painted the way in on the walls.
 *
 * <p>Drawn in the lot's frame: x along the lot (west to east), z from the north street south, the
 * ground at y = 0.
 */
final class Market extends Grounds {
    /** The hall's walls (inclusive): long walls at HZ0 and HZ1, end walls at HX0 and HX1; its middle. */
    static final int HX0 = 36, HX1 = 176, HZ0 = 54, HZ1 = 78, CX = 106, CZ = 66;
    /** The porticoes' depth beyond the end walls. */
    static final int PORCH = 7;
    /** The lanes either side, z inclusive. */
    static final int NL0 = 38, NL1 = 53, SL0 = 79, SL1 = 94;
    /** The rows: the north row's front wall at NF (facing south), the south row's at SF; their depth, alley. */
    static final int NF = 37, SF = 95, BD = 20, YARD = 14;
    /** The rows run x RX0 .. RX1 with a passage through each at GX0 .. GX1. */
    static final int RX0 = 5, RX1 = 207, GX0 = 101, GX1 = 111;

    Market(Placer p, SplittableRandom rng, int w, int d) {
        super(p, rng, w, d, 0.35, 0.25, 0.3);
    }

    @Override
    String signs() {
        return "om_sgn_";
    }

    void build() {
        ground();
        rows();
        new MarketHall(this).build();
        lanes();
        plazas();
        barricades();
    }

    // ------------------------------------------------------------------ the ground

    /** Sidewalk round the lot, granite setts in the lanes and plazas, the passages through the rows. */
    private void ground() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean edge = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                String g;
                if (edge) {
                    g = chance() < 0.1 ? "sidewalk_cracked" : chance() < 0.05 ? "sidewalk_moss" : "sidewalk";
                } else if (z >= NF + 1 && z <= SF - 1 || x >= GX0 && x <= GX1) {
                    g = cobble(x, z);
                } else {
                    double r = chance();
                    g = r < 0.12 ? "asphalt_crack" : r < 0.18 ? "asphalt_patch" : r < 0.21 ? "asphalt_moss" : "asphalt";
                }
                set(x, 0, z, g);
            }
        }
    }

    /** The setts: moss where nobody walks, near the walls and the lot's edges; patched here and there. */
    String cobble(int x, int z) {
        boolean quiet = z == NF + 1 || z == SF - 1 || z == HZ0 - 1 || z == HZ1 + 1 || x < 8 || x > w - 9;
        double r = chance();
        return quiet && r < 0.35 ? "om_cobble_moss" : r < 0.08 ? "om_cobble_b" : r < 0.11 ? "om_cobble_moss" : "om_cobble";
    }

    // ------------------------------------------------------------------ the rows

    /**
     * Brick shops two to four storeys along both lanes, fronts to the lane, back porches and the
     * service alley behind; a passage through the middle of each row.
     */
    private void rows() {
        Storefront.Trade[] trades = {Storefront.Trade.GROCERY, Storefront.Trade.CAFE, Storefront.Trade.DINER,
                Storefront.Trade.TAVERN, Storefront.Trade.BARBER, Storefront.Trade.ANTIQUES, Storefront.Trade.LIQUOR,
                Storefront.Trade.PIZZA, Storefront.Trade.DRUGSTORE, Storefront.Trade.HARDWARE, Storefront.Trade.THRIFT,
                Storefront.Trade.PAWN, Storefront.Trade.DIME, Storefront.Trade.BANK, Storefront.Trade.FURNITURE,
                Storefront.Trade.REALTY, Storefront.Trade.GENERAL, Storefront.Trade.VACANT};
        for (int side = 0; side < 2; side++) {
            int x = RX0;
            while (x <= RX1) {
                int end = x < GX0 ? GX0 - 1 : RX1;
                int left = end - x + 1;
                int bw = 9 + rng.nextInt(7);
                if (left - bw < 9) {
                    bw = left;
                }
                Storefront.Trade t = trades[rng.nextInt(trades.length)];
                double r = chance();
                int floors = r < 0.15 ? 2 : r < 0.75 ? 3 : 4;
                Storefront.Style style = chance() < 0.55 ? Storefront.Style.VICTORIAN : Storefront.Style.ITALIANATE;
                // the north row faces south: its frame turned half round from its east end on the front wall
                Placer q = side == 0 ? p.turned(x + bw - 1, 0, NF, 2) : p.shifted(x, 0, SF);
                Storefront s = new Storefront(q, rng, bw, BD, floors, style, t);
                s.yard = YARD;
                boolean first = x == RX0 || x == GX1 + 1, last = x + bw - 1 == end;
                // the turned frame's west is the world's east
                s.openWest = side == 0 ? last : first;
                s.openEast = side == 0 ? first : last;
                s.build();
                x += bw;
                if (x == GX0) {
                    x = GX1 + 1;
                }
            }
        }
        // the passages through: setts, a lamp on the wall, the arch's name on the lane side
        for (int x = GX0; x <= GX1; x++) {
            for (int z = 4; z < d - 4; z++) {
                if (z < NF + 1 || z > SF - 1) {
                    set(x, 0, z, cobble(x, z));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the lanes

    /** Carts, umbrellas and tables, the strings of bulbs, lamp standards, benches and planters. */
    private void lanes() {
        for (int lane = 0; lane < 2; lane++) {
            int z0 = lane == 0 ? NL0 : SL0, z1 = lane == 0 ? NL1 : SL1;
            int zr = lane == 0 ? z0 + 1 : z1 - 1;          // the row's side
            int zh = lane == 0 ? z1 - 1 : z0 + 1;          // the hall's side
            String toRow = lane == 0 ? "north" : "south", toHall = lane == 0 ? "south" : "north";
            // the strings of bulbs from the shopfronts to the hall's cornice
            for (int x = 12; x < w - 12; x += 9) {
                if (x >= GX0 - 1 && x <= GX1 + 1) {
                    continue;
                }
                boolean lit = power && chance() < 0.7;
                for (int z = z0; z <= z1; z++) {
                    int y = 7 - Math.min(z - z0, z1 - z) / 4;
                    if (chance() < 0.04) {
                        continue;
                    }
                    set(x, Math.max(5, y), z, (lit ? "om_lights_on" : "om_lights") + "[axis=z]");
                }
            }
            // lamp standards along the hall's side, benches and planters between them
            for (int x = 14; x < w - 14; x += 18) {
                lampPost(x, zh);
                if (chance() < 0.7) {
                    set(x + 3, 1, zh, "st_bench[facing=" + toRow + "]");
                }
                set(x + 7, 1, zh, "om_planter");
            }
            // the carts and the umbrellas, the stalls the pushcart men set up every morning
            for (int x = 10; x < w - 10; x += 7 + rng.nextInt(5)) {
                if (x >= GX0 - 2 && x <= GX1 + 2) {
                    continue;
                }
                int z = zr + (lane == 0 ? 2 + rng.nextInt(3) : -2 - rng.nextInt(3));
                double r = chance();
                if (r < 0.4) {
                    String cart = chance() < 0.12 ? "om_cart_tipped" : chance() < 0.6 ? "om_cart_fruit" : chance() < 0.5 ? "om_cart_flowers" : "om_cart_empty";
                    set(x, 1, z, cart + "[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
                } else if (r < 0.75) {
                    umbrella(x, z);
                    set(x + 1, 1, z, "sc_cafe_table");
                    set(x + 2, 1, z, "trade_folding_chair[facing=west]");
                    if (chance() < 0.5) {
                        set(x + 1, 1, z + (lane == 0 ? 1 : -1), "trade_folding_chair_tipped[facing=" + dir() + "]");
                    }
                } else if (r < 0.88) {
                    set(x, 1, z, "om_crates[facing=" + toHall + "]");
                    if (chance() < 0.5) {
                        set(x + 1, 1, z, "om_sacks[facing=" + toHall + "]");
                    }
                } else {
                    set(x, 1, z, "om_produce_rot[facing=" + toHall + "]");
                }
            }
            // what the crowd dropped
            for (int x = 6; x < w - 6; x++) {
                for (int z = z0; z <= z1; z++) {
                    if (empty(x, 1, z) && chance() < 0.012) {
                        double r = chance();
                        set(x, 1, z, r < 0.4 ? "paper" : r < 0.6 ? "blood_splat" : r < 0.75 ? "trash_bag[facing=" + dir() + "]"
                                : r < 0.9 ? "glass_shards" : "home_blood_pool");
                    }
                }
            }
        }
    }

    /** A market umbrella: the pole two high, the canopy on its third block. */
    private void umbrella(int x, int z) {
        set(x, 1, z, "om_umb_pole");
        set(x, 2, z, "om_umb_pole");
        set(x, 3, z, "om_umb_" + any("red", "green", "cream", "red"));
    }

    // ------------------------------------------------------------------ the plazas

    /**
     * West of the hall the founder on his pedestal with the flagpole, bollards keeping the carts in
     * the lanes; east of it the fountain, its basin dry and full of leaves, and the benches.
     */
    private void plazas() {
        int px = (4 + HX0 - PORCH) / 2;
        // the statue: a granite pedestal three square, the figure on it
        for (int x = px - 1; x <= px + 1; x++) {
            for (int z = CZ - 1; z <= CZ + 1; z++) {
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, y == 3 ? "stone_trim" : "om_granite");
                }
            }
        }
        set(px, 4, CZ, "ch_statue");
        set(px, 5, CZ, "ch_statue_top");
        for (int z : new int[] {NL1 + 3, SL0 - 3}) {
            set(px - 6, 1, z, "cv_flagpole_base");
            for (int y = 2; y <= 10; y++) {
                set(px - 6, y, z, "cv_flagpole");
            }
            set(px - 6, 11, z, "cv_flagpole_top[facing=east]");
        }
        for (int z = NL0; z <= SL1; z += 3) {
            if (z < HZ0 - 1 || z > HZ1 + 1) {
                set(5, 1, z, "om_bollard");
                set(w - 6, 1, z, "om_bollard");
            }
        }
        for (int z = HZ0; z <= HZ1; z += 12) {
            lampPost(px - 8, z);
        }
        // the east plaza's fountain, benches round it
        int fx = (HX1 + PORCH + w - 4) / 2;
        fountain(fx, CZ);
        for (int k = -1; k <= 1; k += 2) {
            set(fx + 4 * k, 1, CZ - 3, "st_bench[facing=" + (k < 0 ? "east" : "west") + "]");
            set(fx + 4 * k, 1, CZ + 3, "st_bench[facing=" + (k < 0 ? "east" : "west") + "]");
            set(fx, 1, CZ + 5 * k, "om_planter");
        }
        for (int z = HZ0; z <= HZ1; z += 12) {
            lampPost(fx + 7, z);
        }
        mess(4, HZ0 - 1, HX0 - PORCH - 1, HZ1 + 1, 0, 0.02);
        mess(HX1 + PORCH + 1, HZ0 - 1, w - 5, HZ1 + 1, 0, 0.02);
    }

    /**
     * A granite basin three across with a column in the middle and a copper finial on it, the
     * water long gone to a green scum, or drained to mud and litter.
     */
    private void fountain(int cx, int cz) {
        boolean dry = chance() < 0.4;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                double dd = Math.hypot(dx, dz);
                int x = cx + dx, z = cz + dz;
                if (dd > 3.4) {
                    continue;
                }
                set(x, -1, z, "om_granite");
                if (dd > 2.4) {
                    set(x, 0, z, "om_granite");
                    set(x, 1, z, chance() < 0.25 ? "om_granite_moss" : "om_granite");
                } else if (dry) {
                    set(x, 0, z, any("minecraft:mud", "minecraft:coarse_dirt", "minecraft:packed_mud"));
                    set(x, 1, z, chance() < 0.25 ? any("leaf_litter", "paper", "trash_bag[facing=north]") : "air");
                } else {
                    set(x, 0, z, "minecraft:water");
                    set(x, 1, z, chance() < 0.12 ? "minecraft:lily_pad" : "air");
                }
            }
        }
        for (int y = 0; y <= 2; y++) {
            set(cx, y, cz, y == 2 ? "om_granite_b" : "om_granite");
        }
        set(cx, 3, cz, "cv_finial");
    }

    // ------------------------------------------------------------------ the barricades

    /**
     * The stall keepers' wall at the lanes' east ends and across the east plaza: jersey barriers,
     * carts on their sides, sandbags, a gap a person can squeeze through by the hall; their word
     * sprayed on the hall's east end.
     */
    private void barricades() {
        int bx = HX1 + PORCH + 2;
        for (int z = NL0; z <= SL1; z++) {
            if (z >= HZ0 && z <= HZ1) {
                continue;
            }
            boolean gap = z == NL1 - 1 || z == SL0 + 1;
            if (gap) {
                continue;
            }
            double r = chance();
            set(bx, 1, z, r < 0.5 ? "jersey_barrier[facing=east]" : r < 0.75 ? "om_cart_tipped[facing=" + dir() + "]" : "sandbags");
            if (r < 0.5 && chance() < 0.5) {
                set(bx, 2, z, "sandbags");
            }
        }
        row("om_spray_trade_", 4, HX1 + 1, 3, HZ0 + 1, "east", 0.0);
        row("om_spray_food_", 3, 70, 2, HZ0 - 1, "north", 0.0);
        row("om_spray_rats_", 2, 140, 2, HZ1 + 1, "south", 0.0);
    }
}
