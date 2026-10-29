package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The vessels left in a port, drawn through a {@link Placer} in their own frame: the bow toward
 * +x, the keel's line along z = 0 (the hull's cells from -beam/2 to beam/2 - 1), heights from the
 * quay's top (y = 0), so the water's top block is {@link #SEA} and the basin's bed {@link #BED}.
 * The ocean-going ships are {@link BigShip}'s.
 * <ul>
 * <li>a barge of coal or scrap;</li>
 * <li>the harbour tug and the fishing boats, vehicle models like the marina's (tools/cars/boats.py):
 * trawlers, lobster boats, sloops, jon boats, and the ones that sank at their moorings.</li>
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

    // ------------------------------------------------------------------ harbour craft

    /**
     * A 72-foot harbour tug (boat_tug): tyres hung all round her and the bow fender, the wheelhouse
     * glazed all round on its deckhouse, twin stacks, the towing winch and H-bitts aft.
     */
    void tug(boolean sunk) {
        boat("boat_tug", 23.6, sunk);
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
     * A 60-foot stern trawler at her berth (boat_trawler): the wheelhouse forward, the gantry over the
     * stern with the trawl doors hung on it, the net on its drum; or sunk at her mooring, her
     * wheelhouse and mast standing out of the water.
     */
    void trawler(boolean sunk) {
        boat("boat_trawler", 18.9, sunk);
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
