package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The vessels left in a port, drawn through a {@link Placer} in their own frame: the bow toward
 * +x, the keel's line along z = 0 (the hull's cells from -beam/2 to beam/2 - 1), heights from the
 * quay's top (y = 0), so the water's top block is {@link #SEA} and the basin's bed {@link #BED}.
 * The ocean-going ships are {@link BigShip}'s.
 * <ul>
 * <li>a harbour tug, a barge of coal or scrap;</li>
 * <li>the fishing boats: trawlers with their wheelhouses, gantries and nets; and the small boats, which
 * are vehicle models like the marina's (lobster boats, sloops, jon boats), and the ones that sank at
 * their moorings.</li>
 * </ul>
 */
final class Ships {
    /** The water's top block, under the quay's top. */
    static final int SEA = -4;
    /** The basin's dredged bed. */
    static final int BED = -12;

    private final Placer p;
    private final SplittableRandom rng;
    /** How far gone the port is, 0 to 1: rust, fire, boats sunk at their moorings. */
    private final double hit;

    Ships(Placer p, SplittableRandom rng, double hit) {
        this.p = p;
        this.rng = rng;
        this.hit = hit;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ hulls

    /**
     * A hull's shape: len long, beam wide, from its keel up to its deck, the bow and the stern
     * rounded over those fractions of its length, the stem raked forward toward the deck.
     */
    record Hull(int len, int beam, int keel, int deck, double bow, double stern) {
        /** Half the beam of the hull's section at x, height y; negative outside its length. */
        double half(int x, int y) {
            double f = (y - keel) / (double) Math.max(1, deck - keel);
            double length = len - len * 0.05 * (1 - f);
            double cx = x + 0.5;
            if (cx < 0 || cx >= length) {
                return -1;
            }
            double hb = beam / 2.0;
            double h = hb;
            double bowLen = length * bow, fromBow = length - cx;
            if (fromBow < bowLen) {
                double u = 1 - fromBow / bowLen;
                h = hb * Math.sqrt(Math.max(0, 1 - u * u));
            } else if (cx < len * stern) {
                double u = 1 - cx / (len * stern);
                h = hb * Math.sqrt(Math.max(0, 1 - u * u * 0.45));
            }
            // the bilge: round toward the keel
            if (f < 0.3) {
                h *= 0.55 + 0.45 * Math.sqrt(f / 0.3);
            }
            return h;
        }

        boolean in(int x, int y, int z) {
            if (y < keel || y > deck) {
                return false;
            }
            double h = half(x, y);
            return h > 0 && Math.abs(z + 0.5) < h;
        }

        /** The first x of the bow's taper at deck height. */
        int bowStart() {
            return (int) (len * (1 - bow));
        }
    }

    /**
     * Plates a hull: the bottom colour up to boot, the skin above (rust breaking through), the
     * deck, the inside hollow; railings round the deck's edge where rail is true.
     */
    void hull(Hull h, String skin, String bottom, String deck, int boot, boolean rail) {
        int hb = h.beam() / 2 + 1;
        double rust = 0.03 + hit * 0.12;
        for (int x = 0; x < h.len(); x++) {
            for (int y = h.keel(); y <= h.deck(); y++) {
                for (int z = -hb; z < hb; z++) {
                    if (!h.in(x, y, z)) {
                        continue;
                    }
                    boolean edge = y == h.keel() || !h.in(x + 1, y, z) || !h.in(x - 1, y, z) || !h.in(x, y, z + 1)
                            || !h.in(x, y, z - 1) || !h.in(x, y - 1, z);
                    if (y == h.deck() && !edge) {
                        p.set(x, y, z, deck);
                    } else if (edge) {
                        p.set(x, y, z, y <= boot ? bottom : y == boot + 1 && skin.startsWith("hull") ? "minecraft:black_concrete"
                                : chance() < rust && y > boot + 1 ? "rust" : skin);
                        if (rail && y == h.deck()) {
                            p.set(x, y + 1, z, chance() < 0.04 + hit * 0.1 ? "air" : "minecraft:iron_bars");
                        }
                    } else {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
    }

    /** Raises the hull's sides by n over its fore part from x on: the forecastle, decked over. */
    private void forecastle(Hull h, int from, int n, String skin, String deck) {
        int hb = h.beam() / 2 + 1;
        for (int x = from; x < h.len() + 2; x++) {
            for (int z = -hb; z < hb; z++) {
                if (!h.in(x, h.deck(), z)) {
                    continue;
                }
                boolean edge = !h.in(x + 1, h.deck(), z) || !h.in(x, h.deck(), z + 1) || !h.in(x, h.deck(), z - 1) || x == from;
                for (int y = h.deck() + 1; y <= h.deck() + n; y++) {
                    p.set(x, y, z, edge ? skin : y == h.deck() + n ? deck : "air");
                }
                if (edge) {
                    p.set(x, h.deck() + n + 1, z, "minecraft:iron_bars");
                }
            }
        }
    }

    /** A deckhouse from (x0, z0) to (x1, z1), floors of 3 from y up to n of them, windows round each. */
    private int house(int x0, int z0, int x1, int z1, int y, int floors, String wall, double broken) {
        for (int f = 0; f < floors; f++) {
            int fy = y + f * 3;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = x == x0 || x == x1 || z == z0 || z == z1;
                    for (int k = 0; k < 3; k++) {
                        int yy = fy + k;
                        if (!side) {
                            p.set(x, yy, z, k == 0 ? "floor_lino" : "air");
                            continue;
                        }
                        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                        boolean pane = k == 1 && !corner && ((x == x1 && (z - z0) % 2 == 1) || ((z == z0 || z == z1) && (x - x0) % 3 == 1)
                                || (x == x0 && (z - z0) % 4 == 2));
                        if (pane) {
                            String axis = x == x0 || x == x1 ? "z" : "x";
                            p.set(x, yy, z, chance() < broken ? "window_broken[axis=" + axis + "]" : "window[axis=" + axis + "]");
                        } else {
                            p.set(x, yy, z, chance() < 0.04 + hit * 0.08 ? "rust" : wall);
                        }
                    }
                }
            }
        }
        int roof = y + floors * 3;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                p.set(x, roof, z, "deck_steel");
            }
        }
        return roof;
    }

    /** A funnel from (x, z), w x d, standing from y to top, banded in a line's colour, sooted black at the top. */
    private void funnel(int x, int z, int w, int d, int y, int top, String band) {
        for (int yy = y; yy <= top; yy++) {
            for (int i = 0; i < w; i++) {
                for (int k = 0; k < d; k++) {
                    boolean edge = i == 0 || k == 0 || i == w - 1 || k == d - 1;
                    String m = yy > top - 2 ? "minecraft:black_concrete" : yy > top - 5 && yy <= top - 3 ? band : "minecraft:white_concrete";
                    p.set(x + i, yy, z + k, edge ? m : yy == top ? "minecraft:coal_block" : "air");
                }
            }
        }
    }

    /** A mast from (x, y, z) h high with a light on top. */
    private void mast(int x, int y, int z, int h) {
        for (int yy = y; yy < y + h; yy++) {
            p.set(x, yy, z, "minecraft:iron_bars");
        }
        p.set(x, y + h, z, chance() < 0.5 + hit * 0.3 ? "minecraft:redstone_lamp" : "aviation_light");
    }



    // ------------------------------------------------------------------ harbour craft

    /** A harbour tug len long: tyre fenders all round, the tall wheelhouse, a funnel, the towing winch aft. */
    Hull tug(int len) {
        Hull h = new Hull(len, 8, SEA - 5, SEA + 3, 0.32, 0.3);
        String skin = any("hull_red", "minecraft:black_concrete", "minecraft:blue_concrete");
        hull(h, skin, "hull_black", "deck_steel", SEA, false);
        int hb = 4;
        for (int x = 0; x < len; x++) {
            for (int z = -hb - 1; z <= hb; z++) {
                if (h.in(x, h.deck() - 1, z) && (!h.in(x, h.deck() - 1, z + 1) || !h.in(x, h.deck() - 1, z - 1) || !h.in(x + 1, h.deck() - 1, z)
                        || !h.in(x - 1, h.deck() - 1, z))) {
                    p.set(x, h.deck() - 1, z, "tire[axis=" + (h.in(x, h.deck() - 1, z + 1) && h.in(x, h.deck() - 1, z - 1) ? "z" : "x") + "]");
                }
                if (h.in(x, h.deck(), z) && (!h.in(x, h.deck(), z + 1) || !h.in(x, h.deck(), z - 1) || !h.in(x + 1, h.deck(), z))) {
                    p.set(x, h.deck() + 1, z, skin);
                }
            }
        }
        int wx = len / 2 - 2;
        int roof = house(wx, -2, wx + 5, 1, h.deck() + 1, 2, "minecraft:white_concrete", 0.15 + hit * 0.4);
        mast(wx + 3, roof + 1, -1, 4);
        funnel(wx - 3, -1, 2, 2, h.deck() + 1, h.deck() + 7, any("minecraft:yellow_concrete", "minecraft:red_concrete"));
        p.set(2, h.deck() + 1, -1, "steel_olive");
        p.set(2, h.deck() + 1, 0, "steel_olive");
        p.set(3, h.deck() + 1, 0, "minecraft:iron_chain[axis=x]");
        return h;
    }

    /** A barge len long, a box of a hull with its hold open, heaped with coal, gravel or scrap. */
    Hull barge(int len, int beam) {
        Hull h = new Hull(len, beam, SEA - 4, SEA + 2, 0.05, 0.04);
        hull(h, chance() < 0.6 ? "rust" : "hull_black", "rust", "deck_steel", SEA, false);
        String load = any("minecraft:coal_block", "minecraft:gravel", "minecraft:sand", "rubble_pile", "debris_chunk");
        int hb = beam / 2;
        for (int x = 2; x < len - 2; x++) {
            for (int z = -hb + 1; z < hb - 1; z++) {
                p.set(x, h.deck(), z, "air");
                double heap = 3 - Math.abs(x - len / 2.0) / (len / 2.0) * 2 - Math.abs(z + 0.5) / hb * 1.5 + chance() * 0.6;
                for (int y = h.keel() + 1; y < h.keel() + 1 + Math.max(1, (int) heap + 2); y++) {
                    p.set(x, y, z, load.equals("debris_chunk") && y > h.keel() + 1 ? "rust" : load);
                }
            }
        }
        return h;
    }

    // ------------------------------------------------------------------ fishing boats

    /**
     * A trawler len long: a painted hull with its high bow, the wheelhouse forward, the gantry
     * over the stern, nets heaped on deck, fish boxes, outrigger booms up; sunk at her
     * mooring, only the wheelhouse and the mast stand out of the water.
     */
    Hull trawler(int len, boolean sunk) {
        int beam = len > 18 ? 7 : 6;
        int drop = sunk ? 5 : 0;
        Hull h = new Hull(len, beam, SEA - 4 - drop, SEA + 3 - drop, 0.3, 0.18);
        String skin = any("minecraft:white_concrete", "minecraft:blue_concrete", "minecraft:red_concrete", "minecraft:cyan_concrete",
                "minecraft:green_concrete", "minecraft:light_blue_concrete");
        hull(h, chance() < 0.15 + hit * 0.2 ? "rust" : skin, "hull_red", "minecraft:spruce_planks", SEA - 1 - drop, true);
        forecastle(h, h.bowStart() - 1, 2, skin, "minecraft:spruce_planks");
        int wx = h.bowStart() - 6;
        int roof = house(wx, -2, wx + 3, 1, h.deck() + 1, 1, "minecraft:white_concrete", 0.2 + hit * 0.5);
        p.set(wx + 1, roof + 1, -1, "minecraft:iron_bars");
        mast(wx + 1, roof + 1, 0, 5);
        // outriggers raised either side of the mast
        for (int k = 1; k <= 4; k++) {
            p.set(wx + 1, roof + 1 + k, -1 - k, "minecraft:iron_bars");
            p.set(wx + 1, roof + 1 + k, k, "minecraft:iron_bars");
        }
        // the gantry over the stern
        int hb = beam / 2;
        for (int y = h.deck() + 1; y <= h.deck() + 4; y++) {
            p.set(1, y, -hb + 1, "steel_yellow");
            p.set(1, y, hb - 2, "steel_yellow");
        }
        for (int z = -hb + 1; z <= hb - 2; z++) {
            p.set(1, h.deck() + 5, z, "steel_yellow");
        }
        if (!sunk) {
            for (int i = 0; i < 4; i++) {
                p.setIfAir(2 + rng.nextInt(Math.max(1, wx - 3)), h.deck() + 1, -hb + 1 + rng.nextInt(Math.max(1, beam - 2)),
                        any("minecraft:cobweb", "minecraft:cobweb", "minecraft:barrel[facing=up,loot=storage]", "crate", "minecraft:orange_concrete"));
            }
        }
        return h;
    }

    /** How a small boat's been left at her mooring: most abandoned, some smashed, a few burnt out. */
    private String state(boolean sunk) {
        double r = chance() - hit * 0.25;
        return sunk ? "wrecked" : r < 0.1 ? "burnt" : r < 0.25 ? "wrecked" : r < 0.75 ? "abandoned" : "usable";
    }

    /**
     * One of the small boat models (tools/cars/boats.py) lying along the keel line with her bow toward
     * +x, her stern at x = 0: afloat in the top water block, or settled a block or two when sunk.
     */
    void boat(String model, double length, boolean sunk) {
        p.vehicle(length / 2, sunk ? SEA - 1 - rng.nextInt(2) : SEA, 0.0, -90, "=" + model + "@" + state(sunk), rng.nextLong());
    }

    /** A 32-foot sloop at her mooring, her sail furled on the boom (boat_sloop). */
    void sailboat(boolean sunk) {
        boat("boat_sloop", 9.7, sunk);
    }

    /** A 35-foot lobster boat in from the grounds with her traps aboard (boat_lobster). */
    void lobster(boolean sunk) {
        boat("boat_lobster", 10.7, sunk);
    }

    /** A riveted jon boat at her painter, swamped or afloat (boat_skiff). */
    void skiff(boolean swamped) {
        boat("boat_skiff", 5.1, swamped);
    }
}
