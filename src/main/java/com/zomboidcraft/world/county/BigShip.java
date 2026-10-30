package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * The ocean-going ships at a port's quay, at their real size: the container ship is near three
 * hundred blocks long, her deck fourteen over the quay and the top of her funnel fifty. Drawn in
 * the {@link Ships} frame (the bow toward +x, the keel's line along z = 0, heights from the quay's
 * top) with the vessel_ kit from tools/city/vessel.py:
 * <ul>
 * <li>the hull plated in strakes and butts, red below the load line she's riding high above and
 * slimed where the water laps, draft marks at bow and stern, her name on both bows and across the
 * stern, the anchors in their hawses, rust weeping from the scuppers, scraped where the fenders
 * rubbed;</li>
 * <li>the forecastle behind its bulwark, the windlasses and their chain, the foremast, the
 * breakwater;</li>
 * <li>the bays of boxes on their hatch covers up to seven high, lashing bridges between them, the
 * holds packed with more;</li>
 * <li>the house aft: decks of cabins round a stair that climbs from the engine room to the
 * bridge, the wings out to her sides, the funnel with her line's mark, the radar mast, the
 * lifeboats in their davits;</li>
 * <li>the engine room under it and, behind a false plate in its forward bulkhead, a bunker built
 * into the bottom of the aft hold;</li>
 * <li>made fast with her lines, the accommodation ladder down her side to the quay.</li>
 * </ul>
 * The bulk carrier and the tanker share the hull, the house and the fittings.
 */
final class BigShip {
    /** The big ships' keel, down in the dredged bed. */
    static final int KEEL = -16;

    private static final String[] BOXES = {"container_red", "container_blue", "container_green", "container_orange",
            "container_white", "container_grey", "container_red", "container_blue"};
    private static final String[] HULLS = {"black", "black", "navy", "navy", "green", "maroon", "grey"};
    private static final String[] DECKS = {"green", "grey", "grey", "red"};
    private static final String[] FUNNELS = {"red", "blue", "black", "ochre", "green"};
    private static final String[] EMBLEMS = {"star", "diamond", "ring"};
    private static final String[] NAME_A = {"NORTH", "CAPE", "IRON", "GREY", "SILVER", "OCEAN", "STORM", "WINTER", "AMBER",
            "HARBOUR", "SOUTHERN", "EASTERN", "COLD"};
    private static final String[] NAME_B = {"HERON", "MERIDIAN", "HARVEST", "TIDE", "PROSPECT", "BEACON", "KESTREL", "LANTERN",
            "RIDGE", "VALIANT", "SOUND", "CROWN", "PETREL", "WARDEN"};

    private final Placer p;
    private final SplittableRandom rng;
    private final double hit;
    /** The quay's row in this frame, and the side it's on: +1 toward +z, -1 toward -z, 0 at anchor. */
    private final int quayZ, side;

    private Ships.Hull h;
    private int len, hb, deck, tank, lwl;
    private String hull, deckCol, deckBlock, name;
    /** The house from x0 to x1 along her, hw either side of the keel line, levels decks under the bridge. */
    private int x0, x1, hw, levels;
    /** Where the forecastle starts, and its deck. */
    private int fc, fcTop;

    BigShip(Placer p, SplittableRandom rng, double hit, int quayZ) {
        this.p = p;
        this.rng = rng;
        this.hit = hit;
        this.quayZ = quayZ;
        this.side = Integer.signum(quayZ);
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ the ships

    /**
     * A container ship len long and beam wide (36 and up is a big one; the feeders are smaller
     * and lower), with a bunker in her aft hold in the state given, or none for null.
     */
    Ships.Hull container(int len, int beam, Bunker.State bunker) {
        boolean big = beam >= 36;
        setUp(len, beam, big ? 14 : 9, big ? KEEL : Ships.BED - 1, big ? 5 : 3, 0.16, 0.07);
        shell();
        rails(0, len);
        int houseLen = big ? 16 : 12;
        x0 = big ? 8 : 5;
        x1 = x0 + houseLen - 1;
        hw = Math.min(hb - 6, big ? 13 : 9);
        levels = big ? 8 : 5;
        fc = h.bowStart() - 2;
        forecastle(3);
        // the bays from just forward of the house to the breakwater, the engine room under the first
        int bay0 = x1 + 4, bays = (fc - 7 - bay0) / 13;
        int engineEnd = bay0 - 1;
        while (engineEnd < x1 + 10) {
            engineEnd += 13;
        }
        int holdEnd = holds(bay0, bays, engineEnd, bunker != null && big ? 64 : 0);
        int[] bunkerBox = bunker != null && big ? bunker(bunker, engineEnd, holdEnd) : null;
        cargo(bay0, bays, engineEnd, big ? 4 : 2, big ? 7 : 4, bunkerBox);
        breakwater(fc - 3);
        boolean burnt = chance() < hit * 0.45;
        house(burnt);
        engineRoom(engineEnd, bunkerBox != null);
        stairs(x0 + 2);
        funnel(burnt);
        lifeboats();
        fair();
        paint();
        if (side != 0) {
            moor();
            ladder(x1 + (big ? 20 : 14));
        }
        pilotLadder(len / 2 + 9);
        if (hit > 0.5) {
            // boxes washed over the side, awash
            int out = side != 0 ? -side : 1;
            for (int i = 0; i < 1 + rng.nextInt(3); i++) {
                int z = out > 0 ? hb + 4 + rng.nextInt(8) : -hb - 7 - rng.nextInt(8);
                box(bay0 + rng.nextInt(Math.max(1, len - bay0 - 40)), Ships.SEA - 2, z, BOXES[rng.nextInt(BOXES.length)]);
            }
        }
        return h;
    }

    /**
     * A bulk carrier or a tanker len long: the same hull and house aft; the bulker's hatch covers
     * over her holds (one or two thrown back on the cargo) with her deck cranes between them, the
     * tanker's pipes down her deck under the catwalk, the manifold amidships and its crane.
     */
    Ships.Hull bulk(int len, int beam, boolean tanker) {
        setUp(len, beam, 12, KEEL, 5, 0.14, 0.07);
        shell();
        rails(0, len);
        x0 = 6;
        x1 = x0 + 13;
        hw = Math.min(hb - 5, 11);
        levels = 6;
        fc = h.bowStart() - 1;
        forecastle(3);
        int engineEnd = x1 + 12;
        boolean burnt = chance() < hit * 0.4;
        holds(engineEnd + 1, 0, engineEnd, 0);
        if (tanker) {
            tankDeck(engineEnd + 2, fc - 4);
        } else {
            bulkDeck(engineEnd + 3, fc - 4);
        }
        house(burnt);
        engineRoom(engineEnd, false);
        stairs(x0 + 2);
        funnel(burnt);
        lifeboats();
        fair();
        paint();
        if (side != 0) {
            moor();
            ladder(x1 + 16);
        }
        pilotLadder(len / 2 + 7);
        return h;
    }

    /**
     * A hull on the building berth (Pelican Point Shipyard's hull 1047), len long and beam wide, her
     * keel on y = 0 and her deck at `deck`: aft of `built` her blocks are joined and plated to the
     * deck in red-oxide or zinc primer, the top strakes still to go on over some of them so the
     * frames stand bare there, the deck laid over the after part and its beams open across the
     * holds forward of it, a bulkhead every few blocks; forward of `built` the next blocks' double
     * bottom with its frames standing up like ribs a little way, then only the double bottom. The
     * yard number on both bows. (The bow block waits elsewhere: {@link #block}.)
     */
    Ships.Hull onSlip(int len, int beam, int deck, int built) {
        setUp(len, beam, deck, 0, 0, 0.16, 0.08);
        int ribs = Math.min(len, built + 24);
        for (int x = 0; x < len; x++) {
            int seg = x / 12;
            boolean zinc = Math.floorMod(seg * 7 + 3, 5) < 2;
            // the strakes missing off this block's top, if any
            int gap = x < built ? (Math.floorMod(seg * 11 + 5, 4) == 0 ? 4 : Math.floorMod(seg * 5 + 1, 4) == 0 ? 2 : 0) : 0;
            int plated = x < built ? deck - gap : 3;
            for (int y = 0; y <= deck; y++) {
                for (int z = -hb - 1; z <= hb; z++) {
                    if (!h.in(x, y, z)) {
                        continue;
                    }
                    boolean edge = y == 0 || !h.in(x + 1, y, z) || !h.in(x - 1, y, z) || !h.in(x, y, z + 1)
                            || !h.in(x, y, z - 1) || !h.in(x, y - 1, z);
                    if (edge && y <= plated && (x < built || y <= 3)) {
                        p.set(x, y, z, primed(zinc, y, x));
                    } else if (edge && x % 3 == 0 && x < ribs && (x < built || y <= 3 + (ribs - x) / 2)) {
                        p.set(x, y, z, "sy_frame[facing=" + (z < 0 ? "north" : "south") + "]");
                    } else if (edge) {
                        p.set(x, y, z, "air");
                    } else if (y == 3) {
                        p.set(x, y, z, "vessel_tanktop");
                    } else if (y < 3) {
                        p.set(x, y, z, zinc ? "sy_zinc" : "sy_primer");
                    } else if (y == deck && x < built - 14) {
                        p.set(x, y, z, chance() < 0.04 ? "air" : "sy_zinc_seam");
                    } else if (y == deck && x < built && x % 3 == 0) {
                        p.set(x, y, z, "sf_beam[axis=z]");
                    } else if (x < built && x % 24 == 12 && y < deck && !(Math.abs(z) <= 1 && y <= 6)) {
                        p.set(x, y, z, "sy_zinc_seam");
                    } else {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
        // the yard number on both bows of the blocks that are there, where the name will go
        int nx = Math.min(built - 10, len - 30);
        for (int i = 0; i < 6; i++) {
            onSide(nx - 5 + i, deck - 3, 1, "sy_hull_" + i);
            onSide(nx - i, deck - 3, -1, "sy_hull_" + i);
        }
        return h;
    }

    /**
     * One of her blocks built on its own, from x0 to x1 of a hull len long and beam wide (keel on
     * y = 0, deck at `deck`), plated all round in primer, open at its cut ends where the next
     * block will be joined: the web frames across it there, the decks' edges, the double bottom.
     */
    void block(int len, int beam, int deck, int x0, int x1) {
        setUp(len, beam, deck, 0, 0, 0.16, 0.08);
        for (int x = x0; x <= x1; x++) {
            for (int y = 0; y <= deck; y++) {
                for (int z = -hb - 1; z <= hb; z++) {
                    if (!h.in(x, y, z)) {
                        continue;
                    }
                    boolean shell = y == 0 || !h.in(x + 1, y, z) || !h.in(x, y, z + 1) || !h.in(x, y, z - 1) || !h.in(x, y - 1, z);
                    boolean cut = x == x0 || x == x1 && h.in(x + 1, y, z);
                    if (shell) {
                        p.set(x, y, z, primed(false, y, x));
                    } else if (y == deck) {
                        p.set(x, y, z, "sy_primer_seam");
                    } else if (y == 3 || y == deck - 6 && deck > 12) {
                        p.set(x, y, z, cut ? "sy_stiff[axis=x]" : "vessel_tanktop");
                    } else if (y < 3) {
                        p.set(x, y, z, "sy_primer");
                    } else if (cut) {
                        p.set(x, y, z, "sy_web[axis=z]");
                    } else {
                        p.set(x, y, z, "air");
                    }
                }
            }
        }
    }

    /** A shell plate in primer: the seam welds every third strake, a fitter's chalk now and then. */
    private String primed(boolean zinc, int y, int x) {
        if (zinc) {
            return y % 3 == 0 ? "sy_zinc_seam" : "sy_zinc";
        }
        return y % 3 == 0 ? "sy_primer_seam" : chance() < 0.03 ? "sy_primer_chalk" : "sy_primer";
    }

    private void setUp(int len, int beam, int deck, int keel, int paintAbove, double bow, double stern) {
        this.len = len;
        this.hb = beam / 2;
        this.deck = deck;
        h = new Ships.Hull(len, beam, keel, deck, bow, stern);
        // the tank top a whole number of stair flights under the deck
        tank = deck - 3 * ((deck - keel - 3) / 3);
        lwl = Ships.SEA + paintAbove;
        hull = HULLS[rng.nextInt(HULLS.length)];
        deckCol = DECKS[rng.nextInt(DECKS.length)];
        deckBlock = "vessel_deck_" + deckCol;
        String a = NAME_A[rng.nextInt(NAME_A.length)], b = NAME_B[rng.nextInt(NAME_B.length)];
        name = a + " " + b;
    }

    // ------------------------------------------------------------------ the hull

    /** True inside the hull at least two blocks in from its plating (clear of the shell and its lining). */
    private boolean inner(int x, int y, int z) {
        return h.in(x, y, z) && h.in(x - 2, y, z) && h.in(x + 2, y, z) && h.in(x, y, z - 2) && h.in(x, y, z + 2)
                && h.in(x, y - 2, z);
    }

    /** The hull's plating, the deck over it, the holds lined inside and floored at the tank top. */
    private void shell() {
        for (int x = 0; x < len; x++) {
            for (int y = h.keel(); y <= deck; y++) {
                for (int z = -hb - 1; z <= hb; z++) {
                    if (!h.in(x, y, z)) {
                        continue;
                    }
                    boolean edge = y == h.keel() || !h.in(x + 1, y, z) || !h.in(x - 1, y, z) || !h.in(x, y, z + 1)
                            || !h.in(x, y, z - 1) || !h.in(x, y - 1, z);
                    if (edge) {
                        p.set(x, y, z, skin(x, y, z));
                    } else if (y == deck) {
                        p.set(x, y, z, deckBlock);
                    } else if (y <= tank) {
                        p.set(x, y, z, "vessel_tanktop");
                    } else {
                        p.set(x, y, z, inner(x, y, z) ? "air" : "ship_hold");
                    }
                }
            }
        }
    }

    /** The plate at a cell of the shell: bottom red below the load line, the boot top, the topsides. */
    private String skin(int x, int y, int z) {
        boolean seam = (y - h.keel()) % 3 == 0;
        if (y <= lwl) {
            if (y >= Ships.SEA - 1 && y <= Ships.SEA + 1 && chance() < 0.75) {
                return "vessel_bottom_weed";
            }
            return seam ? "vessel_bottom_seam" : "vessel_bottom";
        }
        String c = "vessel_hull_" + (y <= lwl + 2 ? "black" : hull);
        int s = z < 0 ? -1 : 1;
        if (y < deck && y >= deck - 6 && x % 14 == 7 && x > 20 && x < len - 24 && chance() < 0.8) {
            return c + "_rust";  // weeping from the scupper over it
        }
        if (side != 0 && s == side && y > lwl && y <= lwl + 5 && chance() < 0.3) {
            return c + "_scrape";
        }
        if (chance() < 0.015 + hit * 0.05) {
            return c + "_rust";
        }
        return seam ? c + "_seam" : x % 12 == 0 ? c + "_butt" : c;
    }

    /**
     * Eases her plating over the blocks' steps where she rounds at the bow and the stern: on each
     * row above the water a plate a quarter, a half or three quarters of a block proud of the last
     * block (vessel_fair_), out to where her lines really run, along her sides and forward at the
     * stem, the forecastle's sides the same. The parallel body is flat already.
     */
    private void fair() {
        for (int y = Ships.SEA + 1; y <= fcTop + 1; y++) {
            int yy = Math.min(y, deck);
            int from = y > deck ? fc : 0;
            for (int x = from; x < len + 2; x++) {
                double hh = h.half(x, yy);
                if (hh <= 0 || hh > hb - 0.01) {
                    continue;
                }
                for (int s : new int[] {-1, 1}) {
                    int ze = edgeZ(x, yy, s);
                    if (ze != Integer.MIN_VALUE) {
                        plate(x, y, ze + s, hh - (s > 0 ? ze + 1 : -ze), s > 0 ? "south" : "north");
                    }
                }
            }
            // forward at the stem: each row's end out to where the bow's line crosses it
            for (int z = -hb - 1; z <= hb; z++) {
                int xe = -1;
                for (int x = len + 1; x >= h.bowStart() - 4; x--) {
                    if (h.in(x, yy, z)) {
                        xe = x;
                        break;
                    }
                }
                if (xe < h.bowStart() - 3) {
                    continue;
                }
                double target = Math.abs(z + 0.5), lo = xe + 0.5, hi = xe + 1.5;
                for (int i = 0; i < 12; i++) {
                    double m = (lo + hi) / 2;
                    if (h.halfAt(m, yy) > target) {
                        lo = m;
                    } else {
                        hi = m;
                    }
                }
                plate(xe + 1, y, z, lo - (xe + 1), "east");
            }
        }
    }

    /** A fairing plate g of a block thick against the hull, in the paint of the plating at y. */
    private void plate(int x, int y, int z, double g, String facing) {
        int k = Math.min(3, (int) Math.round(g * 4));
        if (k < 1) {
            return;
        }
        String c = (y <= lwl ? "bottom" : y <= lwl + 2 ? "black" : hull) + ((y - h.keel()) % 3 == 0 ? "_seam" : "");
        p.setIfAir(x, y, z, "vessel_fair_" + c + "_" + k + "[facing=" + facing + "]");
    }

    /** The rail round the deck's edge from x0 to x1: along her sides, across the stern, crossed at the turns. */
    private void rails(int from, int to) {
        for (int x = from; x < to; x++) {
            for (int z = -hb - 1; z <= hb; z++) {
                if (!h.in(x, deck, z)) {
                    continue;
                }
                boolean sideEdge = !h.in(x, deck, z + 1) || !h.in(x, deck, z - 1);
                boolean end = !h.in(x - 1, deck, z) || !h.in(x + 1, deck, z);
                if (!sideEdge && !end) {
                    continue;
                }
                String r = sideEdge && !end ? "vessel_rail[axis=x]" : end && !sideEdge ? "vessel_rail[axis=z]" : "vessel_rail_cross";
                p.set(x, deck + 1, z, chance() < 0.02 + hit * 0.07 ? "air" : r);
            }
        }
    }

    /** The outermost z of the hull at (x, y) on a side, or Integer.MIN_VALUE where she has none. */
    private int edgeZ(int x, int y, int s) {
        for (int z = s > 0 ? hb + 1 : -hb - 2; s > 0 ? z >= -hb - 1 : z <= hb; z -= s) {
            if (h.in(x, y, z)) {
                return z;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** A decal or fitting on her side s at (x, y), facing out off the plating; false where there's no plating. */
    private boolean onSide(int x, int y, int s, String block) {
        if (y <= Ships.SEA) {
            return false;
        }
        int z = edgeZ(x, y, s);
        if (z == Integer.MIN_VALUE) {
            return false;
        }
        p.set(x, y, z + s, block + "[facing=" + (s > 0 ? "south" : "north") + "]");
        return true;
    }

    /** A decal on her transom at (z, y), facing aft. */
    private void onStern(int z, int y, String block) {
        for (int x = 0; x < len / 4; x++) {
            if (h.in(x, y, z)) {
                p.set(x - 1, y, z, block + "[facing=west]");
                return;
            }
        }
    }

    /** The painted marks: her name, the draft marks, the load line, the thruster, scuppers, hawses and anchors. */
    private void paint() {
        int n = name.length();
        int nameY = deck - 2;
        int xEnd = Math.min(h.bowStart() + 3, len - 16);  // clear of the hawse on a short hull
        for (int i = 0; i < n; i++) {
            char c = name.charAt(i);
            if (c == ' ') {
                continue;
            }
            String l = "vessel_name_" + Character.toLowerCase(c);
            // read from the water: left to right is aft to forward on the starboard (+z) side, the other way to port
            onSide(xEnd - (n - 1) + i, nameY, 1, l + "_top");
            onSide(xEnd - (n - 1) + i, nameY - 1, 1, l + "_bot");
            onSide(xEnd - i, nameY, -1, l + "_top");
            onSide(xEnd - i, nameY - 1, -1, l + "_bot");
            onStern(-n / 2 + i, nameY, l + "_top");
            onStern(-n / 2 + i, nameY - 1, l + "_bot");
        }
        for (int s : new int[] {-1, 1}) {
            for (int m = 14; m <= 20; m += 2) {
                int y = h.keel() + m;
                if (y < nameY - 2) {
                    onSide(len - 10, y, s, "vessel_draft_" + m);
                    onSide(4, y, s, "vessel_draft_" + m);
                }
            }
            onSide(len / 2 - 6, lwl, s, "vessel_plimsoll");
            onSide(len - 24, lwl + 2, s, "vessel_thruster");
            for (int x = 21; x < len - 24; x += 14) {
                onSide(x, deck - 1, s, "vessel_scupper");
            }
            int hx = len - 13;
            if (onSide(hx, deck - 2, s, "vessel_hawse")) {
                onSide(hx, deck - 3, s, "vessel_anchor_shank");
                onSide(hx, deck - 4, s, "vessel_anchor_crown");
            }
        }
    }

    // ------------------------------------------------------------------ fore and aft

    /** Raises her bow n over the deck from fc on: the forecastle decked over behind its bulwark, the windlasses, the foremast. */
    private void forecastle(int n) {
        fcTop = deck + n;
        for (int x = fc; x < len + 2; x++) {
            for (int z = -hb - 1; z <= hb; z++) {
                if (!h.in(x, deck, z)) {
                    continue;
                }
                boolean edge = !h.in(x + 1, deck, z) || !h.in(x, deck, z + 1) || !h.in(x, deck, z - 1);
                boolean aft = x == fc;
                for (int y = deck + 1; y <= fcTop; y++) {
                    p.set(x, y, z, edge ? skin(x, y, z) : aft ? "vessel_house" : y == fcTop ? deckBlock : "air");
                }
                if (edge) {
                    p.set(x, fcTop + 1, z, skin(x, fcTop + 1, z));
                } else if (aft) {
                    p.set(x, fcTop + 1, z, "vessel_rail[axis=z]");
                }
            }
        }
        // the bosun's store under it: a door aft, ropes, drums and paint
        Doors.place(p, fc, deck + 1, -1, "ship_door", "west", "left", chance() < 0.4);
        p.set(fc - 1, deck + 2, 2, "vessel_st_stores[facing=west]");
        for (int i = 0; i < 6; i++) {
            int x = fc + 2 + rng.nextInt(8), z = -4 + rng.nextInt(9);
            p.setIfAir(x, deck + 1, z, any("vessel_rope_v", "bunk_drum_water", "bunk_rack_drums[facing=west]", "crate", "rust"));
        }
        // ladders up from the main deck either side
        for (int s : new int[] {-1, 1}) {
            int z = s * (hb - 5) - (s < 0 ? 1 : 0);
            for (int k = 0; k < n; k++) {
                p.set(fc - n + k, deck + 1 + k, z, "steel_stairs[facing=east]");
            }
            p.set(fc, fcTop + 1, z, "air");
        }
        // the windlasses and their chain forward to the hawse pipes, the mooring winches aft of them
        int wx = len - 22;
        for (int s : new int[] {-1, 1}) {
            int z = s * 4 - (s < 0 ? 1 : 0);
            p.set(wx, fcTop + 1, z, "vessel_windlass[axis=x]");
            int ez = edgeZ(len - 13, deck, s) - s;
            for (int x = wx + 1; x <= len - 13; x++) {
                int cz = z + (int) Math.round((ez - z) * (x - wx) / (double) (len - 13 - wx));
                p.set(x, fcTop + 1, cz, "vessel_chain[axis=x]");
            }
            p.set(wx - 4, fcTop + 1, s * 7 - (s < 0 ? 1 : 0), "vessel_winch[axis=x]");
            p.set(wx - 7, fcTop + 1, s * 9 - (s < 0 ? 1 : 0), "vessel_bollard[axis=x]");
        }
        // the foremast, its masthead light and yard
        int mx = len - 9;
        for (int y = fcTop + 1; y <= fcTop + 14; y++) {
            p.set(mx, y, 0, "vessel_mast");
        }
        for (int z = -2; z <= 2; z++) {
            if (z != 0) {
                p.set(mx, fcTop + 11, z, "vessel_yard[axis=z]");
            }
        }
        p.set(mx, fcTop + 15, 0, "vessel_light_white");
        p.set(mx - 1, fcTop + 8, 0, "vessel_horn[facing=east]");
    }

    /** The V of plate across the deck at x that keeps the green water off the first bay. */
    private void breakwater(int x) {
        for (int z = -hb + 6; z < hb - 6; z++) {
            int bx = x - (int) (Math.abs(z + 0.5) / 4);
            if (!h.in(bx, deck, z)) {
                continue;
            }
            for (int y = deck + 1; y <= deck + 4; y++) {
                p.set(bx, y, z, skin(bx, y, z));
            }
            if (z % 3 == 0) {
                p.set(bx - 1, deck + 1, z, "steel_girder");
            }
        }
    }

    // ------------------------------------------------------------------ holds and cargo

    /**
     * The transverse bulkheads under the bays: the engine room's forward one at engineEnd, the aft
     * hold long enough for a bunker of the length given (0: none), then a hold every two bays.
     * Returns the x of the aft hold's forward bulkhead.
     */
    private int holds(int bay0, int bays, int engineEnd, int bunkerLen) {
        bulkhead(engineEnd);
        int last = engineEnd, firstEnd = -1;
        for (int b = 0; b <= bays; b++) {
            int gap = bay0 - 1 + b * 13;
            if (gap <= engineEnd + 1) {
                continue;
            }
            boolean first = firstEnd < 0;
            if (first ? gap >= engineEnd + 1 + bunkerLen && gap - last >= 24 : gap - last >= 26) {
                bulkhead(gap);
                if (first) {
                    firstEnd = gap;
                }
                last = gap;
            }
        }
        bulkhead(fc - 2);
        return firstEnd < 0 ? fc - 2 : firstEnd;
    }

    /** A transverse bulkhead across her at x, from the tank top to the deck. */
    private void bulkhead(int x) {
        for (int y = tank + 1; y < deck; y++) {
            for (int z = -hb; z < hb; z++) {
                if (h.in(x, y, z) && h.in(x, y, z - 1) && h.in(x, y, z + 1) && h.in(x, y - 1, z)) {
                    p.set(x, y, z, "vessel_bulkhead");
                }
            }
        }
    }

    /**
     * The bays of twelve from bay0, hatch covers on their coamings, stacks three wide across the
     * hatch from lo to hi tiers high (some bays unloaded to the covers), lashing bridges between;
     * the holds under them packed from the tank top, over the bunker where there is one.
     */
    private void cargo(int bay0, int bays, int engineEnd, int lo, int hi, int[] bunkerBox) {
        int n = (2 * hb - 8) / 3, zs0 = -n * 3 / 2;
        String hatch = "vessel_hatch_" + deckCol, coaming = "vessel_coaming_" + deckCol;
        for (int b = 0; b < bays; b++) {
            int bx = bay0 + b * 13;
            boolean hold = bx > engineEnd;
            // the coaming round the hatch, its cover on top, the deck open under it into the hold
            for (int x = bx; x < bx + 12; x++) {
                for (int z = zs0 - 1; z <= zs0 + 3 * n; z++) {
                    boolean rim = x == bx || x == bx + 11 || z == zs0 - 1 || z == zs0 + 3 * n;
                    if (hold && !rim) {
                        p.set(x, deck, z, "air");
                    }
                    p.set(x, deck + 1, z, rim ? coaming : "air");
                    p.set(x, deck + 2, z, rim ? coaming : hatch);
                }
            }
            // a mushroom vent and a fire box along the walkways either side
            for (int s : new int[] {-1, 1}) {
                int wz = s > 0 ? zs0 + 3 * n + 1 : zs0 - 2;
                if (b % 2 == 1) {
                    p.set(bx + 6, deck + 1, wz, "vessel_vent");
                }
                if (b % 3 == 0) {
                    p.set(bx + 3, deck + 1, wz, "vessel_firebox[facing=" + (s > 0 ? "south" : "north") + "]");
                }
            }
            if (b > 0) {
                // the lashing bridge in the gap behind the bay
                for (int z = zs0; z < zs0 + 3 * n; z++) {
                    for (int y = deck + 1; y <= deck + 2; y++) {
                        p.set(bx - 1, y, z, coaming);
                    }
                    for (int y = deck + 3; y <= deck + 8; y++) {
                        p.set(bx - 1, y, z, z % 3 == 0 && y > deck + 5 ? "vessel_lash_open[axis=z]" : "vessel_lash[axis=z]");
                    }
                    p.set(bx - 1, deck + 9, z, "vessel_deck_walk[axis=z]");
                }
            }
            // on deck
            boolean empty = chance() < 0.05 + hit * 0.1;
            int tiers = lo + rng.nextInt(hi - lo + 1);
            for (int c = 0; c < n && !empty; c++) {
                int t = tiers - (c == 0 || c == n - 1 ? rng.nextInt(2) : 0) - (chance() < 0.12 ? 1 + rng.nextInt(2) : 0);
                for (int j = 0; j < t; j++) {
                    box(bx, deck + 3 + j * 3, zs0 + c * 3, BOXES[rng.nextInt(BOXES.length)]);
                }
            }
            // below, in the cell guides
            if (!hold) {
                continue;
            }
            for (int c = 0; c < n; c++) {
                int cz = zs0 + c * 3;
                int base = tank + 1;
                if (bunkerBox != null && bx <= bunkerBox[2] + 1 && bx + 11 >= bunkerBox[0] - 1 && cz <= bunkerBox[3] + 1
                        && cz + 2 >= bunkerBox[1] - 1) {
                    base = bunkerBox[4] + 1;
                }
                if (!h.in(bx, base, cz - 2) || !h.in(bx, base, cz + 4) || !h.in(bx + 11, base, cz - 2) || !h.in(bx + 11, base, cz + 4)) {
                    base += 3;
                    if (!h.in(bx, base, cz - 2) || !h.in(bx, base, cz + 4) || !h.in(bx + 11, base, cz - 2)
                            || !h.in(bx + 11, base, cz + 4)) {
                        continue;
                    }
                }
                if (chance() < 0.12) {
                    continue;  // an empty cell, dark to the tank top
                }
                for (int y = base; y + 2 < deck; y += 3) {
                    box(bx, y, cz, BOXES[rng.nextInt(BOXES.length)]);
                }
            }
        }
    }

    /** A box 12 long along x and 3 x 3 from (x, y, z), its doors on the aft end. */
    private void box(int x, int y, int z, String colour) {
        Boxes.place(p, x, y, z, 12, colour, true, true, false);
    }

    /** The bulker's deck from x to end: hatch covers over the holds, a deck crane between every other pair. */
    private void bulkDeck(int from, int end) {
        // the covers in another colour from the deck's, so the hatches read from the quay
        String cover = deckCol.equals("red") ? any("grey", "green")
                : deckCol.equals("green") ? any("red", "grey") : any("red", "green");
        String hatch = "vessel_hatch_" + cover, coaming = "vessel_coaming_" + cover;
        int hz = hb - 6;
        for (int x = from - 4; x < end; x++) {
            for (int z : new int[] {-hb + 2, hb - 3}) {
                p.set(x, deck + 1, z, x % 16 == 5 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
            }
        }
        int bay = 0;
        for (int bx = from; bx + 14 <= end; bx += 18, bay++) {
            boolean open = chance() < 0.25 + hit * 0.2;
            String load = any("rubble_pile", "rubble", "rust", "debris_chunk");
            if (bay > 0) {
                bulkhead(bx - 2);
            }
            for (int i = 0; i < 14; i++) {
                for (int z = -hz; z < hz; z++) {
                    boolean rim = i == 0 || i == 13 || z == -hz || z == hz - 1;
                    p.set(bx + i, deck, z, rim ? deckBlock : "air");
                    p.set(bx + i, deck + 1, z, rim ? coaming : "air");
                    p.set(bx + i, deck + 2, z, rim ? coaming : open ? "air" : hatch);
                    if (!rim) {
                        // the cargo heaped in the hold, highest under the hatch's middle
                        double heap = 7 - Math.abs(i - 6.5) / 2.5 - Math.abs(z + 0.5) / 3 + chance() * 0.8;
                        for (int y = tank + 1; y < tank + 1 + Math.max(1, (int) heap) && y < deck; y++) {
                            if (h.in(bx + i, y, z - 2) && h.in(bx + i, y, z + 2)) {
                                p.set(bx + i, y, z, load);
                            }
                        }
                    }
                }
            }
            if (open) {
                // the cover thrown back on its rollers against the coaming
                for (int i = 1; i < 13; i++) {
                    for (int z = -hz + 1; z < -hz + 4; z++) {
                        p.set(bx + i, deck + 3, z, hatch);
                    }
                }
            }
            if (bay % 2 == 1) {
                crane(bx - 2, 0);
            } else if (bay > 0) {
                // the air pipes and the access hatches down into the hold between the coamings
                for (int s : new int[] {-1, 1}) {
                    p.set(bx - 2, deck + 1, s * (hz - 2) - (s < 0 ? 1 : 0), "vessel_vent");
                    p.set(bx - 3, deck + 1, s * 4 - (s < 0 ? 1 : 0), "vessel_manhole");
                }
            }
        }
    }

    /** A deck crane at (x, z): the pedestal, the cab, the jib down along the deck and its hook. */
    private void crane(int x, int z) {
        for (int y = deck + 1; y <= deck + 8; y++) {
            for (int i = 0; i < 2; i++) {
                for (int k = -1; k <= 0; k++) {
                    p.set(x + i, y, z + k, y <= deck + 2 ? "hazard_stripes" : "steel_yellow");
                }
            }
        }
        for (int y = deck + 9; y <= deck + 11; y++) {
            for (int i = -1; i <= 2; i++) {
                for (int k = -2; k <= 1; k++) {
                    boolean wall = i == -1 || i == 2 || k == -2 || k == 1;
                    p.set(x + i, y, z + k, y == deck + 11 ? "steel_yellow" : wall ? (i == 2 && y == deck + 10 ? "vessel_window[facing=east]"
                            : "steel_yellow") : "air");
                }
            }
        }
        for (int k = 1; k < 16; k++) {
            p.set(x + 2 + k, deck + 10 - k / 3, z, "truss_yellow");
        }
        for (int y = deck + 3; y < deck + 5; y++) {
            p.set(x + 16, y, z, "vessel_rope_v");
        }
    }

    /** The tanker's deck: pipes down her length, the catwalk over them, the manifold and its crane, tank hatches and vents. */
    private void tankDeck(int from, int end) {
        for (int x = from; x < end; x++) {
            for (int z = -4; z <= 3; z++) {
                if (z == -4 || z == -2 || z == 1 || z == 3) {
                    p.set(x, deck + 1, z, x % 20 == 9 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
                }
            }
            if (x % 8 == 0) {
                p.set(x, deck + 1, -1, "vessel_mast");
                p.set(x, deck + 2, -1, "vessel_mast");
                p.set(x, deck + 1, 0, "vessel_mast");
                p.set(x, deck + 2, 0, "vessel_mast");
            }
            p.set(x, deck + 3, -1, "vessel_deck_walk[axis=x]");
            p.set(x, deck + 3, 0, "vessel_deck_walk[axis=x]");
            p.set(x, deck + 4, -2, "vessel_rail[axis=x]");
            p.set(x, deck + 4, 1, "vessel_rail[axis=x]");
            // the fire main along both sides, a hydrant valve every sixteen
            for (int z : new int[] {-hb + 2, hb - 3}) {
                p.set(x, deck + 1, z, x % 16 == 5 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
            }
            if (x % 22 == 11 && x + 6 < end) {
                for (int s : new int[] {-1, 1}) {
                    int z = s * (hb - 6) - (s < 0 ? 1 : 0), in = s < 0 ? -5 : 4;
                    p.set(x, deck + 1, z, "vessel_manhole");
                    // the tank's branch off the bundle, its valve at the bundle
                    for (int bz = in; bz != z; bz += s) {
                        p.set(x + 1, deck + 1, bz, bz == in ? "vessel_pipe_valve[axis=z]" : "vessel_pipe[axis=z]");
                    }
                    // the vent riser with its pressure valve on top, the tank-cleaning plates
                    p.set(x + 3, deck + 1, z, "vessel_mast");
                    p.set(x + 3, deck + 2, z, "vessel_mast");
                    p.set(x + 3, deck + 3, z, "vessel_vent");
                    p.set(x + 6, deck + 1, z - s * 3, "vessel_manhole");
                    p.set(x - 5, deck + 1, z - s * 3, "vessel_manhole");
                }
            }
            // a foam monitor on a stand off the catwalk every forty-four
            if (x % 44 == 30) {
                int z = (x / 44 & 1) == 0 ? -2 : 1;
                p.set(x, deck + 3, z, "vessel_deck_walk[axis=x]");
                p.set(x, deck + 4, z, "vessel_pipe_valve[axis=z]");
                p.set(x, deck + 2, z, "vessel_mast");
                p.set(x, deck + 1, z, "vessel_mast");
            }
        }
        // the manifold amidships, out to both sides, and the hose crane over it
        int mx = (from + end) / 2;
        for (int z = -hb + 3; z < hb - 3; z++) {
            p.set(mx, deck + 1, z, Math.abs(z + 0.5) > hb - 6 ? "vessel_pipe_valve[axis=z]" : "vessel_pipe[axis=z]");
            p.set(mx + 2, deck + 1, z, "vessel_pipe[axis=z]");
        }
        crane(mx + 4, hb - 6);
        for (int k = 0; k < 4; k++) {
            p.set(mx + 4 - k, deck + 1, hb - 9, "vessel_rope[axis=x]");
        }
    }

    // ------------------------------------------------------------------ the bunker

    /**
     * A bunker on the tank top of the aft hold, its blast door facing aft down a passage to a false
     * plate in the engine room's forward bulkhead. Returns its box {x0, z0, x1, z1, top} in this
     * frame, or null if it doesn't fit.
     */
    private int[] bunker(Bunker.State state, int engineEnd, int holdEnd) {
        long seed = rng.nextLong();
        int[] box = new Bunker(p, new SplittableRandom(seed), Bunker.Kind.SHIP, state).bounds();
        int dz = Math.floorDiv(box[0] + box[2], 2), dx = engineEnd + 4;
        // the bunker's (x, z) is (dx + z, dz - x) here
        int bx0 = dx + box[1], bx1 = dx + box[3], bz0 = dz - box[2], bz1 = dz - box[0];
        if (bx1 > holdEnd - 2) {
            return null;
        }
        for (int x : new int[] {bx0, bx1}) {
            for (int y : new int[] {tank + 1, tank + 4}) {
                if (!h.in(x, y, bz0 - 2) || !h.in(x, y, bz1 + 2)) {
                    return null;
                }
            }
        }
        Placer bp = p.turned(dx, tank, dz, 3);
        Bunker b = new Bunker(bp, new SplittableRandom(seed), Bunker.Kind.SHIP, state);
        b.build();
        // the passage from the false plate to the blast door
        for (int x = engineEnd + 1; x < dx; x++) {
            for (int z = dz - 2; z <= dz + 1; z++) {
                for (int y = tank; y <= tank + 4; y++) {
                    boolean way = z >= dz - 1 && z <= dz && y > tank && y < tank + 4;
                    p.set(x, y, z, way ? "air" : y == tank ? "vessel_tanktop" : "ship_hold");
                }
            }
        }
        p.set(engineEnd + 1, tank + 3, dz + 1, (state == Bunker.State.SEALED ? "bunk_lamp_red_lit" : "bunk_lamp_red") + "[facing=north]");
        if (state != Bunker.State.SEALED) {
            p.set(engineEnd + 2, tank + 1, dz, state == Bunker.State.BREACHED ? "home_blood_drag[facing=east]" : "bunk_grit");
        }
        Doors.place(p, engineEnd, tank + 1, dz, "vessel_false_door", "east", "right", state != Bunker.State.SEALED && chance() < 0.7);
        p.mark("bunker " + state.name().toLowerCase(java.util.Locale.ROOT) + " ship", engineEnd - 1, tank + 1, dz);
        bp.mark("bunker door", 0, 1, -2);
        return new int[] {engineEnd + 1, bz0, bx1, bz1, tank + 4};
    }

    // ------------------------------------------------------------------ the house

    /** The house: decks of cabins along a corridor, the stair hall aft, the bridge on top with its wings. */
    private void house(boolean burnt) {
        int bridge = deck + 3 * levels, roof = bridge + 4;
        double broken = burnt ? 0.7 : 0.06 + hit * 0.3;
        int bx = x1 - 5;  // the bridge's aft wall
        for (int y = deck + 1; y <= roof; y++) {
            int r = (y - deck) % 3;
            boolean top = y > bridge;
            for (int x = x0; x <= x1; x++) {
                for (int z = -hw; z < hw; z++) {
                    boolean wall = x == x0 || x == x1 || z == -hw || z == hw - 1;
                    if (y == roof) {
                        p.set(x, y, z, "vessel_roof");
                    } else if (wall) {
                        p.set(x, y, z, houseWall(x, y, z, r, top, broken, burnt));
                    } else if (top) {
                        p.set(x, y, z, x == bx ? "ship_wall_white" : "air");
                    } else {
                        p.set(x, y, z, r == 0 ? "floor_lino" : "air");
                    }
                }
            }
        }
        for (int k = 0; k < levels; k++) {
            deckRooms(deck + 3 * k, k);
        }
        // doors out of the main deck's corridor, fore and aft
        Doors.place(p, x1, deck + 1, -1, "ship_door", "east", "left", chance() < 0.4);
        Doors.place(p, x1, deck + 1, 0, "ship_door", "east", "right", chance() < 0.4);
        Doors.place(p, x0, deck + 1, 0, "ship_door", "west", "left", chance() < 0.5);
        p.set(x1 + 1, deck + 2, 2, "vessel_st_nosmoking[facing=east]");
        p.set(x1 + 1, deck + 2, -3, "vessel_firebox[facing=east]");
        p.set(x0 - 1, deck + 2, -2, "vessel_st_engine[facing=west]");
        for (int s : new int[] {-1, 1}) {
            int wz = s > 0 ? hw : -hw - 1;
            p.set(x0 + 3, deck + 2, wz, "ship_lifebuoy[facing=" + (s > 0 ? "south" : "north") + "]");
            p.set(x1 - 2, deck + 2, wz, "vessel_firebox[facing=" + (s > 0 ? "south" : "north") + "]");
        }
        aftDecks();
        bridge(bridge, bx, broken);
        // the radar mast on the bridge roof and what's round it
        int mx = x1 - 3;
        for (int y = roof + 1; y <= roof + 9; y++) {
            p.set(mx, y, 0, "vessel_mast");
        }
        for (int z = -3; z <= 3; z++) {
            if (z != 0) {
                p.set(mx, roof + 6, z, "vessel_yard[axis=z]");
            }
        }
        p.set(mx, roof + 7, -3, "vessel_radar[axis=z]");
        p.set(mx, roof + 7, 3, "vessel_radar[axis=z]");
        p.set(mx, roof + 10, 0, "vessel_light_white");
        p.set(mx, roof + 4, 1, "vessel_horn[facing=east]");
        p.set(x1 - 7, roof + 1, -4, "vessel_dome");
        p.set(x1 - 7, roof + 1, 3, "vessel_dome");
        for (int y = roof + 1; y <= roof + 3; y++) {
            p.set(x0 + 9, y, -hw + 2, "vessel_antenna");
            p.set(x0 + 9, y, hw - 3, "vessel_antenna");
        }
        for (int x = x0; x <= x1; x++) {
            p.set(x, roof + 1, -hw, "vessel_rail[axis=x]");
            p.set(x, roof + 1, hw - 1, "vessel_rail[axis=x]");
        }
    }

    /**
     * The open decks across the house's aft face, one to each deck with a door in from the stair
     * hall, and the outside stair zigzagging up them from the main deck to the bridge.
     */
    private void aftDecks() {
        for (int k = 0; k <= levels; k++) {
            int y = deck + 3 * k;
            if (k > 0) {
                for (int z = -hw; z < hw; z++) {
                    p.set(x0 - 1, y, z, "vessel_roof");
                    p.set(x0 - 2, y, z, "vessel_roof");
                    p.set(x0 - 3, y + 1, z, "vessel_rail[axis=z]");
                }
                Doors.abandoned(p, rng, x0, y + 1, 0, "ship_door", "west", Doors.hinge(rng));
            }
            if (k == levels) {
                break;
            }
            boolean south = (k & 1) == 0;
            for (int j = 0; j < 3; j++) {
                int z = south ? -hw + 1 + j : hw - 2 - j;
                p.set(x0 - 2, y + 1 + j, z, "steel_stairs[facing=" + (south ? "south" : "north") + "]");
                p.set(x0 - 2, y + 2 + j, z, "air");
                if (j < 2) {
                    p.set(x0 - 2, y + 3, z, "air");
                }
            }
        }
    }

    /** A cell of the house's outside wall: windows round each deck, the bridge's big ones, plate between. */
    private String houseWall(int x, int y, int z, int r, boolean top, double broken, boolean burnt) {
        boolean corner = (x == x0 || x == x1) && (z == -hw || z == hw - 1);
        String facing = x == x1 ? "east" : x == x0 ? "west" : z < 0 ? "north" : "south";
        if (!corner) {
            int bridge = deck + 3 * levels;
            boolean pane;
            String kind = "vessel_window";
            if (top) {
                boolean fore = x == x1 || x >= x1 - 5 && (z == -hw || z == hw - 1);
                pane = y > bridge + 1 && (fore || r == 0 && (x - x0) % 3 == 1);
                if (fore) {
                    kind = "vessel_bridge";
                }
            } else if (x == x1) {
                pane = r == 2 && (z + hw) % 2 == 1;
            } else if (x == x0) {
                pane = r == 2 && (z + hw) % 3 == 1;
            } else {
                pane = r == 2 && (x - x0) % 2 == 1;
            }
            if (pane) {
                return kind + (chance() < broken ? "_broken" : "") + "[facing=" + facing + "]";
            }
        }
        if (burnt && chance() < 0.6) {
            return "vessel_house_soot";
        }
        if (r == 1 && chance() < 0.25 + hit * 0.2) {
            return "vessel_house_rust";
        }
        return r == 0 ? "vessel_house_seam" : "vessel_house";
    }

    /**
     * One deck of the house, its floor at y: the corridor down the middle from the stair hall aft,
     * cabins either side behind their doors, the stores behind the stair hall.
     */
    private void deckRooms(int y, int k) {
        int c0 = x0 + 6;
        for (int x = x0 + 1; x < x1; x++) {
            for (int r = 1; r <= 2; r++) {
                p.set(x, y + r, -2, "ship_wall_white");
                p.set(x, y + r, 1, "ship_wall_white");
            }
        }
        for (int s : new int[] {-1, 1}) {
            int zIn = s > 0 ? 2 : -hw + 1, zOut = s > 0 ? hw - 2 : -3;
            int wallZ = s > 0 ? 1 : -2;
            String door = s > 0 ? "south" : "north";
            // the store behind the stair hall, its door onto the aft landing; to starboard it stands
            // back a lane, the way past the stair from one landing to the other on every deck
            int storeZ = s > 0 ? wallZ + 1 : wallZ;
            for (int z = zIn; z <= zOut; z++) {
                for (int r = 1; r <= 2; r++) {
                    p.set(x0 + 5, y + r, z, "ship_wall_white");
                }
            }
            if (s > 0) {
                for (int x = x0 + 1; x <= x0 + 5; x++) {
                    for (int r = 1; r <= 2; r++) {
                        p.set(x, y + r, wallZ, "air");
                        if (x < x0 + 5) {
                            p.set(x, y + r, storeZ, "ship_wall_white");
                        }
                    }
                }
            }
            Doors.abandoned(p, rng, x0 + 1, y + 1, storeZ, "ship_door", door, Doors.hinge(rng));
            p.set(x0 + 2, y + 1, s > 0 ? zOut : zIn, "bunk_rack_boxes[facing=" + (s > 0 ? "north" : "south") + "]");
            // the cabins, four wide
            for (int cx = c0; cx + 3 < x1; cx += 5) {
                int end = Math.min(cx + 4, x1);
                for (int z = zIn; z <= zOut; z++) {
                    for (int r = 1; r <= 2; r++) {
                        if (end < x1) {
                            p.set(end, y + r, z, "ship_wall_white");
                        }
                    }
                }
                Doors.abandoned(p, rng, cx + 2, y + 1, wallZ, "ship_door", door, Doors.hinge(rng));
                int outer = s > 0 ? zOut : zIn;
                if (k == 0) {
                    // the main deck: changing rooms and the ship's office
                    for (int x = cx; x < cx + 4 && x < x1; x++) {
                        p.set(x, y + 1, outer, "bunk_locker[facing=" + (s > 0 ? "north" : "south") + "]");
                    }
                    continue;
                }
                // a bunk along the outer wall, a locker, a desk and chair
                p.set(cx, y + 1, outer, "bunk_bed_head[facing=east]");
                p.set(cx + 1, y + 1, outer, "bunk_bed_foot[facing=east]");
                p.set(cx + 3, y + 1, outer, "bunk_locker[facing=west]");
                int dz = outer - s * 2;
                p.set(cx + 3, y + 1, dz, "bunk_desk[facing=west]");
                if (chance() < 0.7) {
                    p.set(cx + 2, y + 1, dz, "bunk_chair[facing=east]");
                }
                if (chance() < 0.5) {
                    p.setIfAir(cx + 1, y + 1, outer - s * (2 + rng.nextInt(3)), any("paper", "home_clothes_pile", "glass_shards"));
                }
            }
        }
    }

    /** The bridge from bx forward, its consoles along the windows, the chart table, the wings out to her sides. */
    private void bridge(int y, int bx, double broken) {
        Doors.abandoned(p, rng, bx, y + 1, 0, "ship_door", "west", Doors.hinge(rng));
        for (int z = -hw + 2; z < hw - 2; z++) {
            if (z == 0 || z == -1) {
                p.set(x1 - 1, y + 1, z, "ship_telegraph[facing=west]");
            } else if (Math.abs(z + 0.5) < hw - 4) {
                p.set(x1 - 1, y + 1, z, "ship_console[facing=west]");
            }
        }
        p.set(bx + 2, y + 1, -hw + 2, "bunk_map_table");
        p.set(bx + 2, y + 1, hw - 3, "bunk_radio_desk[facing=north]");
        p.set(x1 - 3, y + 1, 1, "bunk_chair[facing=east]");
        // the wings out to the ship's sides, a console and the side light at each end
        for (int s : new int[] {-1, 1}) {
            Doors.abandoned(p, rng, x1 - 2, y + 1, s > 0 ? hw - 1 : -hw, "ship_door", s > 0 ? "south" : "north", Doors.hinge(rng));
            int zFrom = s > 0 ? hw : -hb, zTo = s > 0 ? hb - 1 : -hw - 1;
            for (int z = zFrom; z <= zTo; z++) {
                for (int x = x1 - 3; x <= x1; x++) {
                    p.set(x, y, z, "vessel_roof");
                    boolean edge = x == x1 - 3 || x == x1 || z == (s > 0 ? zTo : zFrom);
                    p.set(x, y + 1, z, edge ? (z == (s > 0 ? zTo : zFrom) ? "vessel_rail[axis=x]" : "vessel_rail[axis=z]") : "air");
                    p.set(x, y + 2, z, "air");
                }
                if ((z & 3) == 0) {
                    p.set(x1 - 3, y - 1, z, "steel_girder");
                }
            }
            int tip = s > 0 ? zTo : zFrom;
            p.set(x1 - 1, y + 1, tip - s, "ship_console[facing=west]");
            p.set(x1 - 2, y + 2, tip, s > 0 ? "vessel_light_green" : "vessel_light_red");
        }
    }

    /** The funnel on the house roof aft, her line's colour and mark, sooted at the top, the exhausts in it. */
    private void funnel(boolean burnt) {
        int roof = deck + 3 * levels + 4;
        int fx0 = x0 + 1, fx1 = x0 + 6, fz0 = -3, fz1 = 2;
        int top = roof + (levels >= 8 ? 9 : 6);
        String c = "vessel_funnel_" + FUNNELS[rng.nextInt(FUNNELS.length)];
        String mark = "vessel_emblem_" + EMBLEMS[rng.nextInt(EMBLEMS.length)];
        for (int y = roof + 1; y <= top; y++) {
            for (int x = fx0; x <= fx1; x++) {
                for (int z = fz0; z <= fz1; z++) {
                    boolean wall = x == fx0 || x == fx1 || z == fz0 || z == fz1;
                    boolean soot = y > top - 2 || burnt && chance() < 0.3;
                    p.set(x, y, z, wall ? (soot ? "vessel_funnel_soot" : c) : y == top ? "vessel_exhaust" : "air");
                }
            }
        }
        int my = top - 4;
        for (int x = fx0 + 2; x <= fx0 + 3; x++) {
            p.set(x, my, fz0 - 1, mark + "[facing=north]");
            p.set(x, my, fz1 + 1, mark + "[facing=south]");
        }
        p.set(fx0 - 1, my, 0, mark + "[facing=west]");
        p.set(fx0 + 2, top + 1, -1, "vessel_exhaust");
    }

    /** The lifeboats either side of the house, hung in their davits off the boat deck (one gone now and then), life rafts aft. */
    private void lifeboats() {
        int y = deck + 6;
        for (int s : new int[] {-1, 1}) {
            int in = s > 0 ? hw : -hw - 1;
            // the boat deck along the house's side and its rail
            for (int x = x0 + 1; x <= x0 + 12; x++) {
                for (int k = 0; k < 2; k++) {
                    p.set(x, y, in + s * k, "vessel_roof");
                }
                p.set(x, y + 1, in + s * 2, "vessel_rail[axis=x]");
            }
            for (int yy = deck + 1; yy <= y; yy++) {
                p.set(x0 + 12, yy, in, "bunk_ladder[facing=" + (s > 0 ? "south" : "north") + "]");
            }
            p.set(x0 + 7, y + 2, in, "vessel_st_muster[facing=" + (s > 0 ? "south" : "north") + "]");
            p.set(x0 + 9, y + 2, in, "vessel_st_lifeboat[facing=" + (s > 0 ? "south" : "north") + "]");
            int bz = in + s * 4;
            for (int dx : new int[] {x0 + 1, x0 + 11}) {
                for (int yy = y + 1; yy <= y + 3; yy++) {
                    p.set(dx, yy, bz, "vessel_mast");
                }
                p.set(dx, y + 4, bz, "vessel_davit[facing=" + (s > 0 ? "south" : "north") + "]");
            }
            if (chance() < 0.2 + hit * 0.25) {
                continue;  // launched, the falls hanging empty
            }
            for (int x = x0 + 2; x <= x0 + 10; x++) {
                for (int k = -1; k <= 1; k++) {
                    boolean end = x == x0 + 2 || x == x0 + 10;
                    if (end && k != 0) {
                        continue;
                    }
                    int z = bz + k;
                    p.set(x, y + 1, z, "vessel_gfrp");
                    p.set(x, y + 2, z, k == s && x % 2 == 0 ? "vessel_gfrp_window[facing=" + (s > 0 ? "south" : "north") + "]" : "vessel_gfrp");
                    p.set(x, y + 3, z, "vessel_gfrp_white");
                }
            }
            for (int i = 0; i < 2; i++) {
                p.set(x0 + 14 + i * 2, deck + 1, s * (hw + 3) - (s < 0 ? 1 : 0), "vessel_liferaft[axis=x]");
            }
        }
    }

    // ------------------------------------------------------------------ below

    /**
     * The engine room under the house, aft of the bulkhead at engineEnd: the main engine on its
     * bed, the generators, the platforms round it, the control room, the stores against the
     * forward bulkhead (and its false plate, where there's a bunker behind).
     */
    private void engineRoom(int engineEnd, boolean bunker) {
        int ex0 = x0 + 8, ex1 = engineEnd - 5, top = tank + 12;
        int engTop = tank + 15;
        // the flats along her sides every other deck of the stair, catwalks across at the stair and
        // every dozen blocks; the well between open from the tank top round the engine
        for (int y = tank + 6; y < deck - 2; y += 6) {
            for (int x = 3; x < engineEnd; x++) {
                boolean across = x == x0 || x == x0 + 6 || x % 12 == 0;
                for (int z = -hb; z < hb; z++) {
                    boolean shaft = x >= x0 + 1 && x <= x0 + 5 && z >= -1 && z <= 0;
                    boolean flat = Math.abs(z + 0.5) > 6.5;
                    boolean engine = y < engTop && x >= ex0 && x <= ex1 && z >= -4 && z <= 3;
                    if (shaft || engine || !inner(x, y, z) || !flat && !across) {
                        continue;
                    }
                    p.set(x, y, z, flat && !across ? "vessel_deck_walk[axis=x]" : "bunk_walkway");
                    if (flat && !across && Math.abs(z + 0.5) < 8) {
                        p.set(x, y + 1, z, "vessel_rail[axis=x]");
                    }
                    if (flat && (x & 3) == 1 && (z & 3) == 1) {
                        p.set(x, y - 1, z, chance() < 0.08 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
                    }
                }
            }
        }
        // the main engine: the block and its cylinder heads, the shaft aft to the stern tube
        for (int x = ex0; x <= ex1; x++) {
            for (int z = -4; z <= 3; z++) {
                for (int y = tank + 1; y < engTop; y++) {
                    p.set(x, y, z, (y < tank + 8 ? "ship_engine_lo" : "ship_engine_hi") + "[facing=" + (z < 0 ? "north" : "south") + "]");
                }
            }
        }
        for (int x = 3; x < ex0; x++) {
            if (x < x0 || x > x0 + 6) {
                p.set(x, tank + 2, 0, "vessel_pipe[axis=x]");
            }
        }
        // the generators along the port side, the pipes along both
        for (int i = 0; i < 3; i++) {
            int gx = ex0 + 1 + i * 5;
            if (!inner(gx, tank + 1, -11) || !inner(gx + 2, tank + 1, -11)) {
                continue;
            }
            for (int x = gx; x < gx + 3; x++) {
                for (int z = -10; z <= -8; z++) {
                    p.set(x, tank + 1, z, "ship_engine_hi[facing=north]");
                    p.set(x, tank + 2, z, z == -9 ? "ship_engine_hi[facing=north]" : "air");
                }
            }
        }
        for (int x = 3; x < engineEnd; x++) {
            for (int s : new int[] {-1, 1}) {
                for (int y = tank + 4; y < deck - 1; y += 6) {
                    int e = edgeZ(x, y, s);
                    int z = e - 2 * s;
                    if (e != Integer.MIN_VALUE && inner(x, y, z)) {
                        p.set(x, y, z, x % 23 == 11 ? "vessel_pipe_valve[axis=x]" : "vessel_pipe[axis=x]");
                    }
                }
            }
        }
        // the control room on the engine-top platform, starboard forward
        int cy = top, cx0 = engineEnd - 8, cx1 = engineEnd - 1, cz0 = 6, cz1 = Math.min(12, hb - 6);
        for (int x = cz1 - cz0 >= 4 ? cx0 : cx1 + 1; x <= cx1; x++) {
            for (int z = cz0; z <= cz1; z++) {
                for (int y = cy + 1; y <= cy + 4; y++) {
                    boolean wall = x == cx0 || x == cx1 || z == cz0 || z == cz1 || y == cy + 4;
                    p.set(x, y, z, wall ? (y == cy + 2 && x == cx0 && (z - cz0) % 2 == 1 ? "vessel_window[facing=west]" : "ship_wall_white")
                            : "air");
                }
                p.set(x, cy, z, "floor_lino");
            }
        }
        if (cz1 - cz0 >= 4) {
            controlRoom(cy, cx0, cx1, cz0, cz1);
        }
        // the stores against the forward bulkhead, port side
        for (int z = -12; z <= -5; z++) {
            if (inner(engineEnd - 1, tank + 1, z)) {
                p.set(engineEnd - 1, tank + 1, z, any("bunk_rack_boxes", "bunk_rack_crates", "bunk_rack_drums") + "[facing=west]");
            }
        }
        for (int i = 0; i < 4; i++) {
            p.setIfAir(engineEnd - 3 - rng.nextInt(6), tank + 1, -8 + rng.nextInt(16), any("bunk_drum_water", "crate", "rust", "paper"));
        }
        // emergency lamps on the forward bulkhead
        for (int y = tank + 3; y < deck - 1; y += 6) {
            p.set(engineEnd - 1, y, -3, (chance() < 0.3 ? "bunk_lamp_red_lit" : "bunk_lamp_red") + "[facing=west]");
        }
    }

    private void controlRoom(int cy, int cx0, int cx1, int cz0, int cz1) {
        Doors.abandoned(p, rng, cx0, cy + 1, cz0 + 2, "ship_door", "west", Doors.hinge(rng));
        for (int z = cz0 + 1; z < cz1; z++) {
            p.set(cx1 - 1, cy + 1, z, z == cz0 + 3 ? "bunk_switchboard[facing=west]" : "ship_console[facing=west]");
        }
        p.set(cx1 - 3, cy + 1, cz0 + 2, "bunk_chair[facing=east]");
    }

    /**
     * The stair in two lanes (z = -1 and 0), from the tank top to the bridge: flights of three
     * from xs, alternately forward and aft, landings at xs - 1 and xs + 3 on every deck.
     */
    private void stairs(int xs) {
        int top = deck + 3 * levels;
        for (int y = tank, i = 0; y < top; y += 3, i++) {
            boolean fwd = (i & 1) == 0;
            int lane = fwd ? -1 : 0;
            String floor = y == deck ? deckBlock : y > deck ? "floor_lino" : "bunk_walkway";
            for (int x = xs - 1; x <= xs + 3; x++) {
                for (int z = -1; z <= 0; z++) {
                    p.set(x, y + 1, z, "air");
                    p.set(x, y + 2, z, "air");
                }
            }
            for (int z = -1; z <= 0; z++) {
                p.set(xs - 1, y, z, floor);
                p.set(xs + 3, y, z, floor);
            }
            for (int j = 0; j < 3; j++) {
                int x = fwd ? xs + j : xs + 2 - j;
                p.set(x, y, lane, floor);
                p.set(x, y + 1 + j, lane, "steel_stairs[facing=" + (fwd ? "east" : "west") + "]");
                if (j < 2) {
                    p.set(x, y + 3, lane, "air");
                }
            }
            if (y < deck) {
                // the stair tower's rails where it stands open in the engine room
                for (int x = xs - 1; x <= xs + 3; x++) {
                    p.set(x, y + 1, -2, "vessel_rail[axis=x]");
                    p.set(x, y + 1, 1, "vessel_rail[axis=x]");
                }
            }
        }
    }

    // ------------------------------------------------------------------ alongside

    /** Her lines out to bollards on the quay: head and stern lines, springs, a breast line each end. */
    private void moor() {
        int[][] lines = {
            {len - 14, fcTop + 1, len + 6},
            {len - 18, fcTop + 1, len - 2},
            {len - 30, fcTop + 1, len - 62},
            {4, deck + 1, -8},
            {8, deck + 1, 2},
            {16, deck + 1, 44},
        };
        for (int[] l : lines) {
            int fx = l[0], fy = l[1];
            int ez = edgeZ(fx, deck, side);
            if (ez == Integer.MIN_VALUE) {
                continue;
            }
            p.set(fx, fy, ez, "vessel_fairlead[facing=" + (side > 0 ? "south" : "north") + "]");
            p.set(fx - 1, fy, ez - side * 2, "vessel_bollard[axis=x]");
            if (fy == deck + 1) {
                p.set(fx + 2, fy, ez - side * 3, "vessel_winch[axis=x]");
            }
            int qx = l[2];
            p.set(qx, 1, quayZ, "vessel_bollard[axis=x]");
            rope(fx + 0.5, fy + 0.5, ez + side + 0.5, qx + 0.5, 1.6, quayZ + 0.5);
        }
    }

    /** A rope from one point to another, sagging in the middle; it only passes through air. */
    private void rope(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double sag = Math.min(3.5, dist * 0.06);
        int steps = (int) (dist * 4) + 1;
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            int x = (int) Math.floor(ax + dx * t), z = (int) Math.floor(az + dz * t);
            int y = (int) Math.floor(ay + dy * t - sag * 4 * t * (1 - t));
            if (x == lx && y == ly && z == lz) {
                continue;
            }
            lx = x;
            ly = y;
            lz = z;
            if (p.get(x, y, z) != null) {
                continue;
            }
            double slope = Math.abs(dy - sag * 4 * (1 - 2 * t)) / Math.max(0.01, Math.hypot(dx, dz));
            p.set(x, y, z, slope > 1.2 ? "vessel_rope_v" : Math.abs(dx) >= Math.abs(dz) ? "vessel_rope[axis=x]" : "vessel_rope[axis=z]");
        }
    }

    /**
     * The accommodation ladder down her side toward the quay: a landing out from the deck at xt,
     * the flight down aft against her plating to a platform across the gap at the quay's top.
     */
    private void ladder(int xt) {
        int zg = side > 0 ? hb : -hb - 1, ez = zg - side;
        for (int x = xt - 1; x <= xt + 2; x++) {
            p.set(x, deck + 1, ez, "air");
        }
        // the landing is the flight's top tread, in the deck's plane, so its last step is a step
        p.set(xt + 1, deck, zg, "steel_stairs[facing=east]");
        p.set(xt + 1, deck + 1, zg + side, "vessel_rail[axis=x]");
        p.set(xt + 2, deck + 1, zg, "vessel_rail[axis=z]");
        int k = 0;
        for (int y = deck - 1; y >= 1; y--, k++) {
            p.set(xt - k, y, zg, "steel_stairs[facing=east]");
            p.set(xt - k, y + 1, zg + side, "vessel_rail[axis=x]");
            p.set(xt - k, y + 2, zg, "air");
        }
        for (int x = xt - k - 1; x <= xt - k; x++) {
            for (int z = zg; z != quayZ; z += side) {
                p.set(x, 0, z, "vessel_deck_walk[axis=z]");
            }
        }
    }

    /** The pilot ladder hung down her offshore side at x, from the rail to just over the water. */
    private void pilotLadder(int x) {
        int s = side != 0 ? -side : 1;
        int ez = edgeZ(x, deck, s);
        if (ez == Integer.MIN_VALUE) {
            return;
        }
        p.set(x, deck + 1, ez, "air");
        for (int y = deck; y >= Ships.SEA + 2; y--) {
            onSide(x, y, s, "vessel_pilot_ladder");
        }
    }
}
