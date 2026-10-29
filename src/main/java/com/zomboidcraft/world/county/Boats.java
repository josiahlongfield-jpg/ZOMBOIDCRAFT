package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The marina's small craft, from the Waterfront kit (wf_) and the vessels' fibreglass, drawn
 * through a {@link Placer} in their own frame like {@link Ships}: the bow toward +x, the keel's
 * line along z = 0, the hull's waterline row at a given y. A runabout with its windshield and
 * outboard, a cabin cruiser, a sloop with her mast stepped and the sail furled on the boom, an old
 * skiff; sunk at her lines, the hull goes under and only the cabin top or the mast shows.
 */
final class Boats {
    private final Placer p;
    private final SplittableRandom rng;
    private final double hit;
    /** The water's top block in the frame (cells inside a hull at or under it fill with water), or none. */
    private final int water;

    Boats(Placer p, SplittableRandom rng, double hit) {
        this(p, rng, hit, Waterfront.SEA);
    }

    Boats(Placer p, SplittableRandom rng, double hit, int water) {
        this.p = p;
        this.rng = rng;
        this.hit = hit;
        this.water = water;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    /** A boat of the kind (run, cruiser, sail, skiff) len long, her waterline row at y; sunk drops her three. */
    void hull(String kind, int len, int y, boolean sunk) {
        int drop = sunk ? 3 : 0;
        int beam = kind.equals("cruiser") ? 5 : kind.equals("skiff") ? 3 : 4;
        int keel = y - 1 - drop, deck = y + 1 - drop;
        Ships.Hull h = new Ships.Hull(len, beam, keel, deck, kind.equals("skiff") ? 0.3 : 0.38, 0.12);
        String skin = kind.equals("skiff") ? any("vessel_hull_green", "vessel_hull_grey") : chance() < 0.25 ? "vessel_gfrp" : "vessel_gfrp_white";
        String band = any("vessel_hull_navy", "vessel_hull_maroon", "vessel_hull_green", "vessel_hull_navy");
        int hb = beam / 2 + 1;
        for (int x = 0; x < len; x++) {
            for (int yy = keel; yy <= deck; yy++) {
                for (int z = -hb; z < hb; z++) {
                    if (!h.in(x, yy, z)) {
                        continue;
                    }
                    boolean edge = yy == keel || !h.in(x + 1, yy, z) || !h.in(x - 1, yy, z) || !h.in(x, yy, z + 1) || !h.in(x, yy, z - 1);
                    if (edge && yy == deck && (h.in(x, yy, z + 1) && h.in(x, yy, z - 1) && h.in(x + 1, yy, z) && h.in(x - 1, yy, z))) {
                        edge = false;
                    }
                    if (edge) {
                        p.set(x, yy, z, yy == deck - 1 && !kind.equals("skiff") ? band : yy == keel ? "vessel_bottom" : skin);
                    } else if (yy == keel + 1 || yy < deck && kind.equals("skiff")) {
                        p.set(x, yy, z, yy <= water ? "minecraft:water" : "wf_deck_b");
                    } else {
                        p.set(x, yy, z, yy <= water ? "minecraft:water" : "air");
                    }
                }
            }
        }
        int bow = h.bowStart();
        switch (kind) {
            case "run" -> {
                // the windshield across, the seats behind it, the outboard on the transom
                int wx = Math.max(2, bow - 1);
                for (int z = -1; z <= 0; z++) {
                    p.set(wx, deck, z, "wf_windshield[facing=east]");
                    p.set(wx - 2, deck - 0, z, "wf_cover");
                }
                p.set(-1, keel + 1, -1, sunk ? "air" : "wf_outboard[facing=west]");
                if (chance() < 0.4) {
                    p.set(wx - 1, deck, 0, "wf_coil");
                }
            }
            case "cruiser" -> {
                // the cabin forward of the cockpit, windows down its sides, the flybridge rail on top
                int c0 = 2, c1 = Math.max(c0 + 2, bow);
                for (int x = c0; x <= c1; x++) {
                    for (int z = -2; z <= 2; z++) {
                        boolean wall = x == c0 || x == c1 || z == -2 || z == 2;
                        for (int k = 1; k <= 2; k++) {
                            if (wall) {
                                boolean pane = k == 2 && x > c0 && x < c1 && (z == -2 || z == 2) && x % 2 == 0;
                                p.set(x, deck + k, z, pane ? "vessel_gfrp_window[facing=" + (z < 0 ? "north" : "south") + "]" : "vessel_gfrp_white");
                            } else {
                                p.set(x, deck + k, z, deck + k <= water ? "minecraft:water" : "air");
                            }
                        }
                        p.set(x, deck + 3, z, "vessel_gfrp_white");
                    }
                }
                p.set(c0, deck + 1, 0, "air");
                p.set(c0, deck + 2, 0, "air");
                p.set(c1, deck + 2, -1, "wf_windshield[facing=east]");
                p.set(c1, deck + 2, 1, "wf_windshield[facing=east]");
                for (int x = c0; x <= c0 + 2; x++) {
                    p.set(x, deck + 4, -2, "vessel_rail[axis=x]");
                    p.set(x, deck + 4, 2, "vessel_rail[axis=x]");
                }
                p.set(c0 + 1, deck + 4, 0, "wf_windshield[facing=east]");
                if (chance() < 0.5) {
                    p.set(c0 - 1, deck, 0, "wf_cover");
                }
                p.set(-1, keel + 1, 0, sunk ? "air" : "wf_outboard[facing=west]");
            }
            case "sail" -> {
                // the mast stepped forward of the middle, the boom aft along the deck, a cover over the cockpit
                int mx = len / 2 + 1;
                int top = deck + 9 + rng.nextInt(3);
                for (int yy = deck + 1; yy <= top; yy++) {
                    p.set(mx, yy, -1, "wf_mast");
                }
                for (int x = 1; x < mx; x++) {
                    p.set(x, deck + 2, -1, "wf_boom[axis=x]");
                }
                p.set(mx, top + 1, -1, chance() < 0.4 ? "vessel_light_white" : "air");
                if (!sunk && chance() < 0.6) {
                    p.set(2, deck, -1, "wf_cover");
                    p.set(2, deck, 0, "wf_cover");
                }
            }
            default -> {
                // the skiff's thwart and oars
                p.set(len / 2, deck - 1, -1, "wf_deck");
                if (chance() < 0.5) {
                    p.set(1, keel + 1, 0, "wf_coil");
                }
            }
        }
        // her name on the transom, now and then a fender over the side
        if (!sunk && chance() < 0.5 + hit * 0.2) {
            p.set(len / 2, deck, -beam / 2 - 1, "wf_fender[facing=north]");
        }
    }
}
