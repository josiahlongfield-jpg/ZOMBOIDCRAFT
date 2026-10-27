package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Lays the county's train lines into a chunk (see {@link Railway}), after its places are in so
 * the line runs through them: the bed and its shoulders in the country, eased into the land on
 * embankments and cuttings; the street track flush with the asphalt where it runs down a city
 * street; the island's el on its steel bents over the street, the bridges on their piers; the
 * subway's tunnel and its stations under the island's streets with their stairs up to the
 * sidewalk; the platforms, shelters, lamps, name boards and call buttons at every stop; and the
 * crossing signals where country roads cross on the level.
 *
 * <p>Everything is worked out per column from the nearest point of the line (how far along, how
 * far to the side), so a chunk lays its share of the line without its neighbours. Offsets are to
 * the right going the way the trains run; the trains' own contract fixes the numbers: the bed 6
 * wide, nothing within 3 of the middle for 6 above the bed top, platforms' edges 2 from the
 * middle and their tops a block over the bed.
 */
final class RailGen {
    /** The stops the rw_ kit has name boards for (tools/city/railway.py STOPS). */
    static final Set<String> SIGNED = Set.of("union", "uptown", "pinecrest", "fort", "riverside", "millbrook", "dunmore", "fairview",
            "ashby", "hollow", "kings", "seabright", "airport", "foundry", "southport", "stadium", "tower", "power", "waterfront", "mercy");
    /** Street level in the city: the top of the street's blocks. */
    private static final int STREET_TOP = Terrain.CITY + 1;
    /**
     * Union Station's ways in (see {@link UnionStation}): the el's bridge from its platform to the
     * mezzanine's door runs on the samples BRIDGE_LO..BRIDGE_HI from the stop's middle (its rails
     * one more each side) out to BRIDGE_END; the subway's passage from its platform to the station's
     * hall on PASS_LO..PASS_HI (its walls one more each side) out to PASS_END, the lot's edge.
     */
    private static final int BRIDGE_LO = -2, BRIDGE_HI = 1, BRIDGE_END = 15, PASS_LO = -12, PASS_HI = -9, PASS_END = 14;

    private final County county;
    private final List<Track> near = new ArrayList<>();
    private final Track.Hit hit = new Track.Hit();

    private RailGen(County county, int bx, int bz) {
        this.county = county;
        for (Track t : county.tracks()) {
            if (t.near(bx, bz)) {
                near.add(t);
            }
        }
    }

    /** The lines passing the chunk at (bx, bz), or null if none does. */
    static RailGen near(County county, int bx, int bz) {
        if (county.tracks().isEmpty()) {
            return null;
        }
        RailGen g = new RailGen(county, bx, bz);
        return g.near.isEmpty() ? null : g;
    }

    /** How far (x, z) is from the nearest line above ground (up to cap). */
    static double distance(County county, double x, double z, double cap) {
        double best = cap;
        Track.Hit h = new Track.Hit();
        for (Track t : county.tracks()) {
            if (t.kind == Track.Kind.SUBWAY) {
                continue;
            }
            int[] cand = t.candidates((int) Math.floor(x), (int) Math.floor(z));
            if (cand != null) {
                t.nearest(cand, x, z, h);
                best = Math.min(best, h.distance);
            }
        }
        return best;
    }

    /** The chunk's columns within reach of a line above ground (index (z & 15) << 4 | (x & 15)). */
    boolean[] corridor(int bx, int bz, double reach) {
        boolean[] in = new boolean[256];
        for (Track t : near) {
            if (t.kind == Track.Kind.SUBWAY) {
                continue;
            }
            for (int k = 0; k < 256; k++) {
                if (!in[k] && at(t, bx + (k & 15), bz + (k >> 4))) {
                    in[k] = hit.distance < reach;
                }
            }
        }
        return in;
    }

    private Track.Stop stop(Track t, int i) {
        return t.stopAt[i] < 0 ? null : t.stops.get(t.stopAt[i]);
    }

    // ------------------------------------------------------------------ the land

    /**
     * The ground a column outside every place takes under the line: the bed's level across the
     * bed and its shoulders (and a platform), eased back to the land over its embankment or
     * cutting. h unchanged where the line wants nothing; rail[0] set where the column is the
     * line's own (no trees or weeds).
     */
    double ground(int x, int z, double h, boolean[] rail) {
        rail[0] = false;
        for (Track t : near) {
            if (t.kind == Track.Kind.SUBWAY || !at(t, x, z)) {
                continue;
            }
            int i = hit.i;
            double s = hit.distance;
            if (t.mode[i] != Track.GROUND) {
                rail[0] |= s < 5.5;
                continue;
            }
            if (t.street[i] > 0) {
                continue;
            }
            double target = Math.floor(t.ys[i]) - 1 + 0.5;
            Track.Stop st = stop(t, i);
            double inner = st != null && Math.signum(hit.offset) == st.side() ? 7.5 : 4.5;
            if (s <= inner) {
                h = target;
                rail[0] = true;
            } else {
                double width = Math.max(3, Math.min(16, Math.abs(h - target) * 1.6));
                if (s < inner + width) {
                    h = Terrain.lerp(h, target, Terrain.smooth(inner + width, inner, s));
                    rail[0] |= s < inner + 1.5;
                }
            }
        }
        return h;
    }

    /** Finds the nearest point of t to the column's middle: true (with {@link #hit} set) if it's within reach. */
    private boolean at(Track t, int x, int z) {
        int[] cand = t.candidates(x, z);
        if (cand == null) {
            return false;
        }
        t.nearest(cand, x + 0.5, z + 0.5, hit);
        return hit.i >= 0 && hit.distance < t.reach();
    }

    // ------------------------------------------------------------------ the line

    /** The column pass's findings, for the chunk's 256 columns (index (z & 15) << 4 | (x & 15)). */
    record Columns(int[] top, boolean[] road, boolean[] built) {}

    /** Lays every line passing the chunk, subway first so the el's footings stand on the street over it. */
    void lay(ChunkSink out, int bx, int bz, Columns cols) {
        near.sort((a, b) -> a.kind == b.kind ? 0 : a.kind == Track.Kind.SUBWAY ? -1 : 1);
        for (Track t : near) {
            for (int k = 0; k < 256; k++) {
                int x = bx + (k & 15), z = bz + (k >> 4);
                if (!at(t, x, z)) {
                    continue;
                }
                Cut c = new Cut(t, hit, x, z, cols.top[k], cols.road[k], cols.built[k]);
                switch (t.mode[c.i]) {
                    case Track.TUNNEL -> tunnel(out, c);
                    case Track.VIADUCT -> viaduct(out, c);
                    default -> ground(out, c);
                }
            }
            crossings(out, t, bx, bz, cols);
        }
    }

    /** One column's place across the line. */
    private final class Cut {
        final Track t;
        final int i, x, z, top;
        final double o, s, y;
        /** The bed block's y, and the whole-block bed top over it. */
        final int bed, Y;
        /** Quarter blocks of ballast laid on the bed's top where the bed top isn't whole (0..3). */
        final int q;
        final boolean road, built;
        final double tx, tz;
        final Track.Stop stop;
        /** On the platform's side of a stop (within its run). */
        final boolean plat;
        /** Samples from the stop's middle. */
        final int da;

        Cut(Track t, Track.Hit h, int x, int z, int top, boolean road, boolean built) {
            this.t = t;
            i = h.i;
            this.x = x;
            this.z = z;
            this.top = top;
            this.road = road;
            this.built = built;
            o = h.offset;
            s = Math.abs(o);
            y = t.ys[i];
            int whole = (int) Math.floor(y), quarter = (int) Math.round((y - whole) * 4);
            Y = whole + (quarter == 4 ? 1 : 0);
            q = quarter == 4 ? 0 : quarter;
            bed = Y - 1;
            tx = t.tx[i];
            tz = t.tz[i];
            stop = stop(t, i);
            plat = stop != null && Math.signum(o) == stop.side();
            da = stop == null ? 0 : i - stop.middle();
        }

        /** The compass way out from the line on this column's side. */
        String out() {
            double sg = Math.signum(o) == 0 ? 1 : Math.signum(o);
            return dir(-tz * sg, tx * sg);
        }

        /** The compass way in toward the line. */
        String in() {
            double sg = Math.signum(o) == 0 ? 1 : Math.signum(o);
            return dir(tz * sg, -tx * sg);
        }

        /** The compass way trains run here (sign +1), or back (-1). */
        String along(int sign) {
            return dir(tx * sign, tz * sign);
        }

        String axis() {
            return Math.abs(tx) >= Math.abs(tz) ? "x" : "z";
        }

        String across() {
            return Math.abs(tx) >= Math.abs(tz) ? "z" : "x";
        }

        /** The platform's back: two columns of a 4-wide platform past its edge at 2 (the trains' contract). */
        int back() {
            return 6;
        }
    }

    static String dir(double dx, double dz) {
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    private static void clear(ChunkSink out, int x, int z, int y0, int y1) {
        out.column(x, z, y0, y1, "air");
    }

    /** The ballast up to the bed top: the bed block and, where the top isn't whole, a part block on it. */
    private static void ballast(ChunkSink out, Cut c, String block) {
        out.set(c.x, c.bed, c.z, block);
        if (c.q > 0) {
            out.set(c.x, c.Y, c.z, "rw_ballast_" + c.q * 4);
        }
    }

    // ------------------------------------------------------------------ on the ground

    private void ground(ChunkSink out, Cut c) {
        boolean city = c.t.street[c.i] > 0 || c.built;
        if (c.stop != null && c.plat && c.s >= 2 && c.s < 7.5) {
            if (city) {
                streetPlatform(out, c);
            } else {
                platform(out, c);
            }
            return;
        }
        if (c.stop == null && !city && c.s >= 2 && c.s < 7 && platformEnd(out, c)) {
            return;
        }
        if (c.s < 3) {
            if (c.road) {
                // a level crossing: the road's surface carried across between the rails, up to their feet
                out.set(c.x, c.bed, c.z, "rw_crossing");
                if (c.q > 0) {
                    out.set(c.x, c.Y, c.z, "rw_crossing_" + c.q * 4);
                }
            } else {
                ballast(out, c, c.t.street[c.i] > 0 ? "rw_street_bed" : "rw_ballast");
                if (!city) {
                    out.set(c.x, c.bed - 1, c.z, "rw_subballast");
                }
            }
            clear(out, c.x, c.z, c.Y + (c.q > 0 ? 1 : 0), c.Y + 6);
            if (!city && !c.road && c.s >= 2.5 && c.q == 0) {
                weeds(out, c, c.Y);   // clear of the cars, which reach two blocks out
            }
            return;
        }
        if (city) {
            if (c.t.street[c.i] == 0 && c.s >= 4.5 && c.s < 5.5 && offStreet(c.x, c.z)) {
                // through a vacant corner lot: chain-link along the cut
                out.set(c.x, c.Y, c.z, "chainlink[axis=" + c.axis() + "]");
                out.set(c.x, c.Y + 1, c.z, "chainlink[axis=" + c.axis() + "]");
            } else if (c.t.street[c.i] == 0 && c.s < 4.5) {
                out.set(c.x, c.bed, c.z, "rw_cinder");
                clear(out, c.x, c.z, c.Y, c.Y + 4);
            }
            return;
        }
        if (c.s < 4.5) {
            if (!c.road) {
                out.set(c.x, c.bed, c.z, "rw_cinder");
                clear(out, c.x, c.z, c.Y, c.Y + 3);
                weeds(out, c, c.Y);
            }
            return;
        }
        // the telegraph line along the left of the line, a pole every 48
        if (c.o < -6.5 && c.o >= -7.5 && Math.floorMod(c.i, 48) == 0 && !c.road && !c.built && c.top >= Terrain.WATER) {
            out.column(c.x, c.z, c.top + 1, c.top + 7, "rw_pole");
            out.set(c.x, c.top + 8, c.z, "rw_pole_arm[axis=" + c.across() + "]");
        }
    }

    /** True if (x, z) is inside a block of the city's grid, off its streets and sidewalks. */
    private static boolean offStreet(int x, int z) {
        int p = Layout.PITCH;
        int dx = Math.abs(Math.floorMod(x, p) - p / 2), dz = Math.abs(Math.floorMod(z, p) - p / 2);
        return dx < p / 2 - 12 && dz < p / 2 - 12;
    }

    /**
     * A country platform: concrete on a footing, its edge 2 from the middle (yellow line, the
     * track side), the fence along its back with a gate in the middle, the shelter at its middle,
     * lamps, the name boards and the call buttons.
     */
    private void platform(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, da = c.da;
        double s = c.s;
        out.column(x, z, Math.min(c.top, c.bed) - 2, c.bed, "rw_platform_base");
        out.set(x, Y, z, s < 3 ? "rw_platform_edge[facing=" + c.in() + "]" : paving(c));
        clear(out, x, z, Y + 1, Y + 6);
        litter(out, c, Y + 1, 0);
        int len = c.stop.to() - c.stop.from();
        if (s >= 6) {
            // the fence along the back, a gate in the middle; the shelter's back wall
            if (Math.abs(da) <= 10) {
                if (Math.abs(da) % 5 == 0) {
                    out.column(x, z, Y + 1, Y + 4, "rw_post");
                } else {
                    out.set(x, Y + 1, z, "rw_shelter_wall[facing=" + c.in() + "]");
                    out.set(x, Y + 2, z, Math.abs(da) == 2 ? "rw_timetable[facing=" + c.in() + "]"
                            : "rw_shelter_window[facing=" + c.in() + "]");
                    out.set(x, Y + 3, z, "rw_shelter_wall[facing=" + c.in() + "]");
                    out.set(x, Y + 4, z, "rw_shelter_wall[facing=" + c.in() + "]");
                }
                out.set(x, Y + 5, z, "rw_canopy");
            } else if (Math.abs(da) > 13 && Math.abs(da) < 16) {
                // the gates: either side of the shelter, out to the land behind
                return;
            } else {
                out.set(x, Y + 1, z, "rw_fence[facing=" + c.out() + "]");
                for (int c0 : new int[] {-30, 30}) {
                    if (da >= c0 - 2 && da <= c0 + 1) {
                        nameBoard(out, c, da - c0 + 2, Y + 1);
                    }
                }
            }
            return;
        }
        if (Math.abs(da) <= 10) {
            // the shelter: its roof from the platform's middle back, the ends walled
            if (s >= 3) {
                out.set(x, Y + 5, z, s < 4 ? "rw_canopy_fascia[facing=" + c.in() + "]" : "rw_canopy");
            }
            if (Math.abs(da) == 10 && s >= 4) {
                out.column(x, z, Y + 1, Y + 4, "rw_shelter_wall[facing=" + c.along(da > 0 ? -1 : 1) + "]");
            }
            if (s >= 5 && Math.abs(da) < 10 && Math.abs(da) % 4 == 1) {
                out.set(x, Y + 1, z, "rw_bench[facing=" + c.in() + "]");
            }
            if (s >= 3 && s < 4 && Math.abs(da) == 6) {
                out.set(x, Y + 4, z, "rw_lamp_hang");
            }
            if (s >= 5 && da == -7) {
                // on the shelter's back wall
                out.set(x, Y + 2, z, "zomboidcraft:train_call_button[facing=" + c.in() + "]");
            }
            return;
        }
        // lamps along the platform, and a second call button by one of them
        int la = Math.abs(da);
        if (s >= 5 && (la == 22 || la == 40) && la < len / 2) {
            out.column(x, z, Y + 1, Y + 3, "rw_lamp_post");
            out.set(x, Y + 4, z, "rw_lamp_head[facing=" + c.in() + "]");
        }
        if (s >= 4 && s < 5 && da == 22) {
            // on the lamp post behind
            out.set(x, Y + 2, z, "zomboidcraft:train_call_button[facing=" + c.in() + "]");
        }
        if (s >= 4 && s < 5 && (la == 31 || la == 47) && la < len / 2) {
            out.set(x, Y + 1, z, "rw_bench[facing=" + c.in() + "]");
        }
    }

    /**
     * Just past a country platform's end on its side: a half block down off the end, so it can be
     * walked onto from the ground. True if the column was it.
     */
    private boolean platformEnd(ChunkSink out, Cut c) {
        Track t = c.t;
        for (int d = -1; d <= 1; d += 2) {
            short at = t.stopAt[t.wrap(c.i + d)];
            if (at < 0) {
                continue;
            }
            Track.Stop st = t.stops.get(at);
            if (Math.signum(c.o) != st.side()) {
                continue;
            }
            out.set(c.x, c.Y, c.z, "rw_platform_half");
            out.column(c.x, c.z, Math.min(c.top, c.bed) - 1, c.bed, "rw_platform_base");
            clear(out, c.x, c.z, c.Y + 1, c.Y + 4);
            return true;
        }
        return false;
    }

    /** The board of a sign k along it (counting the way trains run) is this tile: tile 0 is the viewer's left. */
    private static int tile(Cut c, int k, int count) {
        // a viewer facing a board that faces the line has the way back along the line on their
        // right when the board is right of it, so the tiles count against the way trains run
        return c.o > 0 ? count - 1 - k : k;
    }

    /** A stop's name board on two legs, four boards long, facing the line; k = 0..3 along it from the viewer's left. */
    private void nameBoard(ChunkSink out, Cut c, int k, int y) {
        String key = key(c.stop);
        if (key == null || k < 0 || k > 3) {
            return;
        }
        if (k == 0 || k == 3) {
            out.set(c.x, y, c.z, "rw_post");
        } else {
            out.set(c.x, y, c.z, "air");
        }
        out.set(c.x, y + 1, c.z, "rw_name_" + key + "_" + tile(c, k, 4) + "[facing=" + c.in() + "]");
    }

    /** The name-board key for a stop, or null if the kit has none. */
    static String key(Track.Stop st) {
        String k = st.spot().startsWith("st-") ? st.spot().substring(3) : st.spot();
        return SIGNED.contains(k) ? k : null;
    }

    /** A platform in a city street: up a step from the sidewalk, under a canopy. */
    private void streetPlatform(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, da = c.da;
        double s = c.s;
        if (s < 2) {
            return;
        }
        if (s >= 6) {
            // a step up off the sidewalk all along the back
            if (s < 7) {
                out.set(x, Y, z, "rw_platform_half");
                clear(out, x, z, Y + 1, Y + 3);
            }
            return;
        }
        out.set(x, Y, z, s < 3 ? "rw_platform_edge[facing=" + c.in() + "]" : paving(c));
        clear(out, x, z, Y + 1, Y + 6);
        litter(out, c, Y + 1, 1);
        if (Math.abs(da) <= 24) {
            if (s >= 3) {
                out.set(x, Y + 5, z, s < 4 ? "rw_canopy_fascia[facing=" + c.in() + "]" : "rw_canopy");
            }
            if (s >= 5 && Math.abs(da) % 8 == 0) {
                out.column(x, z, Y + 1, Y + 4, "rw_post");
            } else if (s >= 5 && Math.abs(da) % 8 == 4) {
                out.set(x, Y + 1, z, "rw_bench[facing=" + c.in() + "]");
            }
            if (s >= 4 && s < 5 && Math.abs(da) == 8) {
                out.set(x, Y + 2, z, "zomboidcraft:train_call_button[facing=" + c.in() + "]");
            }
            if (s >= 3 && s < 4 && Math.abs(da) % 8 == 4) {
                out.set(x, Y + 4, z, "rw_lamp_hang");
            }
            if (s >= 4 && s < 5) {
                hungBoard(out, c, Y + 4);
            }
        }
    }

    /** A name board hung under a canopy, at |da| 17..20 (four boards). */
    private void hungBoard(ChunkSink out, Cut c, int y) {
        String key = key(c.stop);
        int la = Math.abs(c.da);
        if (key == null || la < 17 || la > 20) {
            return;
        }
        int k = c.da > 0 ? c.da - 17 : c.da + 20;
        out.set(c.x, y, c.z, "rw_name_" + key + "_" + tile(c, k, 4) + "[facing=" + c.in() + "]");
    }

    // ------------------------------------------------------------------ the el and the bridges

    private void viaduct(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, bed = c.bed;
        double s = c.s;
        int street = c.t.street[c.i];
        boolean el = street > 0;
        boolean wet = c.top < Terrain.WATER;
        boolean station = c.stop != null && c.plat;
        int wide = station ? 7 : 4;
        if (s < 3 && !(station && s >= 2)) {
            ballast(out, c, "rw_ballast");
            out.set(x, bed - 1, z, "rw_deck");
            clear(out, x, z, Y + (c.q > 0 ? 1 : 0), Y + 6);
        } else if (s < wide) {
            boolean edge = s >= wide - 1;
            out.set(x, bed, z, station ? "rw_deck" : "rw_grate");
            if (edge) {
                out.set(x, bed - 1, z, "rw_girder");
                out.set(x, bed - 2, z, "rw_girder");
            } else {
                out.set(x, bed - 1, z, "rw_deck");
            }
            if (station) {
                elPlatform(out, c);
            } else {
                out.set(x, Y, z, "rw_guard[facing=" + c.out() + "]");
                clear(out, x, z, Y + 1, Y + 3);
            }
        }
        if (station && s >= 7 && s < 9) {
            elStairs(out, c);
        }
        if (station && s >= 7 && s < BRIDGE_END && unionRun(c, BRIDGE_LO, BRIDGE_HI, true)) {
            unionBridge(out, c);
        }
        // what holds it up
        int span = el ? 16 : wet ? 24 : 16;
        int k = Math.floorMod(c.i, span);
        if (el) {
            if (k == 0 && s < street + 1) {
                out.set(x, bed - 2, z, "rw_crossbeam[axis=" + c.across() + "]");
                if (s >= street) {
                    out.set(x, STREET_TOP, z, "rw_column_base");
                    out.column(x, z, STREET_TOP + 1, bed - 4, "rw_column");
                    out.set(x, bed - 3, z, "rw_column_cap");
                }
            }
        } else if (wet) {
            if ((k == 0 || k == 1) && s < 4.5) {
                out.column(x, z, Math.min(c.top, Terrain.WATER - 1) - 2, bed - 3, "rw_pier");
                out.set(x, bed - 2, z, "rw_pier_cap");
            }
        } else if (k == 0 && s < 3.5 && c.top < bed - 2) {
            out.column(x, z, c.top - 1, bed - 3, "rw_pier");
            out.set(x, bed - 2, z, "rw_pier_cap");
        }
    }

    /** The el's platform on its side of the deck: concrete, a guard rail along its back, the canopy, lamps, boards, buttons. */
    private void elPlatform(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, da = c.da, la = Math.abs(da);
        double s = c.s;
        int top = stairStart(c, EL_STAIRS, 5, 13);
        boolean stairTop = la >= top && la <= top + 1;
        if (s < 6) {
            out.set(x, Y, z, s < 3 ? "rw_platform_edge[facing=" + c.in() + "]" : paving(c));
            clear(out, x, z, Y + 1, Y + 5);
            litter(out, c, Y + 1, 1);
            boolean mouth = unionRun(c, BRIDGE_LO, BRIDGE_HI, false);
            if (s >= 5 && la % 8 == 0 && la <= 40 && !mouth) {
                out.column(x, z, Y + 1, Y + 5, "rw_post");
            } else if (s >= 5 && la % 8 == 4 && la <= 40) {
                out.set(x, Y + 1, z, "rw_bench[facing=" + c.in() + "]");
            }
            if (s >= 4 && s < 5 && la == 8) {
                out.set(x, Y + 2, z, "zomboidcraft:train_call_button[facing=" + c.in() + "]");
            }
            if (s >= 3 && s < 4 && la % 8 == 4 && la <= 40) {
                out.set(x, Y + 5, z, "rw_lamp_hang");
            }
            if (s >= 4 && s < 5) {
                hungBoard(out, c, Y + 5);
            }
            if (mouth && s >= 5 && (c.da == BRIDGE_LO + 1 || c.da == BRIDGE_LO + 2)) {
                // over the way across to Union Station: down to the subway
                out.set(x, Y + 5, z, "us_sign_subway_" + tile(c, c.da - BRIDGE_LO - 1, 2) + "[facing=" + c.in() + "]");
            }
        } else {
            out.set(x, Y, z, paving(c));
            boolean open = stairTop || unionRun(c, BRIDGE_LO, BRIDGE_HI, false);
            out.set(x, Y + 1, z, open ? "air" : "rw_guard[facing=" + c.out() + "]");
            clear(out, x, z, Y + 2, Y + 5);
        }
        if (la <= 40 && s >= 2) {
            out.set(x, Y + 6, z, s < 3 ? "rw_canopy_fascia[facing=" + c.in() + "]" : "rw_canopy");
        }
    }

    /**
     * The stairs from the el's platform down to the sidewalk, one at each end, two wide beside
     * the deck: a landing at the platform's level, then a step down a block at a time.
     */
    private void elStairs(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, la = Math.abs(c.da);
        int sign = c.da >= 0 ? 1 : -1;   // the stair runs on away from the middle
        int k = la - stairStart(c, EL_STAIRS, 5, 13);   // 0..1 landing, then the steps
        int rise = Y + 1 - STREET_TOP;
        boolean inner = c.s < 8;
        if (k < 0 || k > rise + 1) {
            return;
        }
        if (k <= 1) {
            out.set(x, Y, z, paving(c));
            out.set(x, Y + 1, z, inner ? "air" : "rw_guard[facing=" + c.out() + "]");
            clear(out, x, z, Y + 2, Y + 4);
            return;
        }
        int step = k - 1;                // 1.. down
        // this step's block, a block under the one before: the first level with the landing's
        // slab, so its high half is flush with the landing and every rise is half a block, the
        // last a half step over the sidewalk
        int h = Y + 1 - step;
        if (h <= STREET_TOP) {
            return;
        }
        out.set(x, h, z, "rw_stair_steel[facing=" + c.along(-sign) + "]");
        out.set(x, h + 1, z, "rw_guard[facing=" + (inner ? c.in() : c.out()) + "]");
        clear(out, x, z, h + 2, h + 4);
        if (step == rise / 2 && !inner) {
            out.column(x, z, STREET_TOP, h - 1, "rw_column");
        }
    }

    private static boolean union(Cut c) {
        return c.stop != null && c.stop.spot().equals("union");
    }

    /** True on Union Station's platform side within the samples lo..hi from its middle (and one more each side with sides). */
    private static boolean unionRun(Cut c, int lo, int hi, boolean sides) {
        int m = sides ? 1 : 0;
        return union(c) && c.plat && c.da >= lo - m && c.da <= hi + m;
    }

    /**
     * Union Station's bridge from the el's platform across the sidewalk to the mezzanine's door: a
     * steel deck on plate girders, railed both sides with posts every three, under the platform's
     * roof carried on across, on a column each side at the sidewalk's back.
     */
    private void unionBridge(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y;
        boolean side = !unionRun(c, BRIDGE_LO, BRIDGE_HI, false);
        String away = c.along(c.da > BRIDGE_HI ? 1 : -1);
        out.set(x, c.bed, z, side ? "rw_girder" : "rw_deck");
        if (side) {
            out.set(x, c.bed - 1, z, "rw_girder");
        }
        out.set(x, Y, z, paving(c));
        clear(out, x, z, Y + 1, Y + 5);
        if (side) {
            if ((int) c.s % 3 == 1) {
                out.column(x, z, Y + 1, Y + 5, "rw_post");
            } else {
                out.set(x, Y + 1, z, "rw_guard[facing=" + away + "]");
            }
            if (c.s >= 12 && c.s < 13) {
                out.set(x, STREET_TOP, z, "rw_column_base");
                out.column(x, z, STREET_TOP + 1, c.bed - 3, "rw_column");
                out.set(x, c.bed - 2, z, "rw_column_cap");
            }
        } else {
            litter(out, c, Y + 1, 1);
        }
        out.set(x, Y + 6, z, side ? "rw_canopy_fascia[facing=" + away + "]" : "rw_canopy");
    }

    /** A platform's slab: the plain one mostly, the worn ones here and there. */
    private String paving(Cut c) {
        double r = roll(c, 0x9A7EL);
        return r < 0.6 ? "rw_platform" : r < 0.85 ? "rw_platform_2" : "rw_platform_3";
    }

    /** A number in [0, 1) fixed for the column and what asks for it (salt). */
    private double roll(Cut c, long salt) {
        return Noise.unit(Noise.hash(county.seed ^ salt, c.x, c.z));
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};

    /**
     * What was left on a platform when the trains stopped: paper, bin bags, broken glass and blood
     * where people waited and fled (kind 1 the el's and the street stops', 2 the subway's), weeds
     * and leaves in the cracks of a country one (kind 0). Only on its open middle (not the edge).
     */
    private void litter(ChunkSink out, Cut c, int y, int kind) {
        if (c.s < 3) {
            return;
        }
        double r = roll(c, 0x11773L);
        String dir = DIRS[(int) (roll(c, 0x11774L) * 4)];
        String b;
        if (kind == 0) {
            b = r < 0.05 ? "weeds" : r < 0.08 ? "gc_leaves_" + (1 + (int) (roll(c, 0x11775L) * 3)) + "[facing=" + dir + "]" : r < 0.095 ? "paper" : null;
        } else {
            b = r < 0.04 ? "paper" : r < 0.055 ? "glass_shards" : r < 0.065 ? "trash_bag[facing=" + dir + "]"
                    : r < 0.072 ? "blood_splat" : kind == 2 && r < 0.076 ? "home_blood_pool" : kind == 1 && r < 0.085 ? "gc_leaves_" + (1 + (int) (roll(c, 0x11775L) * 3)) + "[facing=" + dir + "]" : null;
        }
        if (b != null) {
            out.set(c.x, y, c.z, b);
        }
    }

    /** The weeds taking the line's edges back: grass, seedlings and weeds in the cinders and the ballast's shoulders. */
    private void weeds(ChunkSink out, Cut c, int y) {
        double r = roll(c, 0x3EED5L);
        int n = 1 + (int) (roll(c, 0x3EED6L) * 3);
        String b = r < 0.1 ? "gc_grass_" + (n * 3) : r < 0.15 ? "gc_grass_dry_" + n : r < 0.19 ? "gc_sprig_" + n : r < 0.22 ? "weeds" : null;
        if (b != null) {
            out.set(c.x, y, c.z, b);
        }
    }

    /** Where a station's stairs usually start, |da| from its middle: the el's at its platform's ends, the subway's a little in. */
    private static final int EL_STAIRS = 32, SUBWAY_STAIRS = 26;

    /**
     * Where the stairs at this column's end of its station start (|da|): the usual place, or the
     * nearest place toward the middle (or out to the platform's end) where what they put on the
     * street (|da| start+lo..start+hi: the subway's opening and lamps, the el's foot and the
     * column under its middle) keeps out of a cross street's road, so they come out on a sidewalk.
     */
    private static int stairStart(Cut c, int usual, int lo, int hi) {
        int sign = c.da >= 0 ? 1 : -1, m = c.stop.middle();
        int[] tries = new int[32];
        int n = 0;
        tries[n++] = usual;
        for (int d = 2; d <= 20; d += 2) {
            if (usual - d >= 6) {
                tries[n++] = usual - d;
            }
            if (usual + d + hi <= 47 && d <= 8) {
                tries[n++] = usual + d;
            }
        }
        for (int t = 0; t < n; t++) {
            if (!crossStreet(c.t, m, sign, tries[t] + lo, tries[t] + hi)) {
                return tries[t];
            }
        }
        return usual;
    }

    /** True if any of the samples |da| a..b on one side of a stop lies in the road of a city street crossing the line. */
    private static boolean crossStreet(Track t, int m, int sign, int a, int b) {
        boolean alongX = Math.abs(t.tx[m]) > Math.abs(t.tz[m]);
        int p = Layout.PITCH;
        for (int la = a; la <= b; la++) {
            int i = t.wrap(m + sign * la);
            double v = alongX ? t.xs[i] : t.zs[i];
            long line = Math.round(v / p);
            int half = Math.floorMod(line, 3) == 0 ? 7 : 5;
            if (Math.abs(v - line * p) < half + 1.5) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the subway

    private void tunnel(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, bed = c.bed;
        double s = c.s;
        int street = Math.max(5, c.t.street[c.i]);
        boolean station = c.stop != null;
        int back = street + 1;   // the platform's back wall: under the curb
        if (station && c.plat) {
            if (s >= back && s < PASS_END && unionRun(c, PASS_LO, PASS_HI, true)) {
                unionPassage(out, c);
                return;
            }
            boolean mouth = unionRun(c, PASS_LO, PASS_HI, false);
            if (s >= back - 1 && s < back + 3 && subwayStairs(out, c, back)) {
                return;
            }
            if (s < 2) {
                trackway(out, c);
            } else if (s < back) {
                out.set(x, bed, z, "rw_tunnel");
                out.set(x, Y, z, s < 3 ? "rw_platform_edge[facing=" + c.in() + "]" : paving(c));
                clear(out, x, z, Y + 1, Y + 5);
                out.set(x, Y + 6, z, "rw_tunnel");
                litter(out, c, Y + 1, 2);
                if (s >= back - 1 && !mouth && roll(c, 0x6A1L) < 0.07) {
                    // tags on the tile, at arm's height
                    int g = (int) (roll(c, 0x6A2L) * 8);
                    out.set(x, Y + 1 + (int) (roll(c, 0x6A3L) * 2), z, "graffiti_" + g + "[facing=" + c.out() + "]");
                }
                stationFloor(out, c, back);
                if (mouth && s >= back - 1 && (c.da == PASS_LO + 1 || c.da == PASS_LO + 2)) {
                    // over the way through to Union Station: up to the trains
                    out.set(x, Y + 5, z, "us_sign_trains_" + tile(c, c.da - PASS_LO - 1, 2) + "[facing=" + c.in() + "]");
                }
            } else if (s < back + 1) {
                wall(out, c, true);
            }
            return;
        }
        if (s < 3) {
            trackway(out, c);
        } else if (s < 4) {
            out.set(x, bed - 1, z, "rw_tunnel");
            out.set(x, bed, z, "rw_tunnel_floor");
            clear(out, x, z, Y, Y + 5);
            out.set(x, Y + 6, z, "rw_tunnel");
            if (!station && Math.floorMod(c.i, 16) == (c.o > 0 ? 0 : 8)) {
                out.set(x, Y + 4, z, "rw_tunnel_lamp[facing=" + c.in() + "]");
            }
        } else if (s < 5) {
            wall(out, c, station);
        }
    }

    /**
     * Union Station's passage from the subway's platform through its back wall and under the
     * sidewalk to the lot's edge, where the station's own carries on to its hall: tiled walls,
     * a lamp now and then.
     */
    private void unionPassage(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y;
        if (!unionRun(c, PASS_LO, PASS_HI, false)) {
            out.column(x, z, c.bed - 1, Y + 6, "rw_tunnel");
            for (int k = 0; k < 5; k++) {
                boolean broken = roll(c, 0x7120L + k) < (k < 2 ? 0.24 : 0.16);
                out.set(x, Y + k, z, (k < 2 ? "rw_tile_dado" : "rw_tile") + (broken ? "_broken" : ""));
            }
            return;
        }
        out.set(x, c.bed - 1, z, "rw_tunnel");
        out.set(x, c.bed, z, "rw_tunnel");
        out.set(x, Y, z, paving(c));
        clear(out, x, z, Y + 1, Y + 4);
        out.set(x, Y + 5, z, "rw_tunnel");
        litter(out, c, Y + 1, 2);
        if ((int) c.s % 4 == 1 && c.da == PASS_LO + 1) {
            out.set(x, Y + 4, z, "rw_subway_lamp[axis=" + c.across() + "]");
        }
    }

    /** The trackway: the slab under the bed, ballast, the tunnel's height clear and its roof. */
    private void trackway(ChunkSink out, Cut c) {
        out.set(c.x, c.bed - 1, c.z, "rw_tunnel");
        out.set(c.x, c.bed, c.z, "rw_track_slab");
        clear(out, c.x, c.z, c.Y, c.Y + 5);
        out.set(c.x, c.Y + 6, c.z, "rw_tunnel");
    }

    /** A tunnel wall: bare concrete, or at a station white tile with the band and the stop's name. */
    private void wall(ChunkSink out, Cut c, boolean tiled) {
        int x = c.x, z = c.z, Y = c.Y;
        out.column(x, z, c.bed - 1, Y + 6, "rw_tunnel");
        if (!tiled) {
            return;
        }
        String f = "[facing=" + c.in() + "]";
        for (int k = 0; k < 6; k++) {
            if (k == 3) {
                out.set(x, Y + k, z, "rw_tile_band");
                continue;
            }
            // decades without upkeep: a block in five has lost tiles, more of them low down and up by the leaking roof
            boolean broken = roll(c, 0x7110L + k) < (k < 2 ? 0.24 : k == 5 ? 0.3 : 0.16);
            out.set(x, Y + k, z, (k < 2 ? "rw_tile_dado" : "rw_tile") + (broken ? "_broken" : ""));
        }
        // the name set in the tile, every 24 along the platform
        String key = key(c.stop);
        int k = Math.floorMod(c.da + 2, 24);
        if (key != null && k < 4 && Math.abs(c.da) < 40) {
            out.set(x, Y + 3, z, "rw_mosaic_" + key + "_" + tile(c, k, 4) + f);
        }
    }

    /** What stands on a subway platform: benches, lamps under the roof, the call buttons on the back wall. */
    private void stationFloor(ChunkSink out, Cut c, int back) {
        int x = c.x, z = c.z, Y = c.Y, la = Math.abs(c.da);
        double s = c.s;
        if (s >= back - 1 && la % 12 == 6 && !(la >= 24 && la <= 40)) {
            out.set(x, Y + 1, z, "rw_bench[facing=" + c.in() + "]");
        }
        if (s >= back - 1 && la == 10 && !unionRun(c, PASS_LO, PASS_HI, false)) {
            out.set(x, Y + 2, z, "zomboidcraft:train_call_button[facing=" + c.in() + "]");
        }
        if (s >= back - 2 && s < back - 1 && la % 4 == 0) {
            out.set(x, Y + 5, z, "rw_subway_lamp[axis=" + c.axis() + "]");
        }
    }

    /**
     * The stairs up to the street from a subway platform, one at each end: two wide behind the
     * platform's back wall (under the sidewalk), ten steps up to it, the last five open to the
     * sky with railings round them, a globe lamp either side of the top and the SUBWAY sign on
     * the street side. v is the column's place across them: -1..0 the platform's last column
     * (the stairwell's street side above the station), 0..2 the stairs, 2..3 the wall behind.
     * True if the column was theirs.
     */
    private boolean subwayStairs(ChunkSink out, Cut c, int back) {
        int la = Math.abs(c.da);
        int sign = c.da >= 0 ? 1 : -1;   // the stairs climb away from the platform's middle
        int rise = STREET_TOP - (c.Y + 1);
        int k = la - stairStart(c, SUBWAY_STAIRS, 5, rise + 1);   // -1 the wall at the foot, 0 the landing, 1..rise the steps
        double v = c.s - back;
        if (k < -1 || k > rise) {
            return false;
        }
        int x = c.x, z = c.z, Y = c.Y;
        int h = Y + Math.max(0, k);      // the step's block, its top a block over the one before
        boolean open = k >= 6;           // the steps open to the sky
        if (v < 0) {
            // the platform's last column: over the station's roof, the stairwell's street side
            if (k >= 1) {
                out.column(x, z, Y + 7, Math.min(h + 5, STREET_TOP - 2), "rw_tile");
            }
            if (open) {
                out.set(x, STREET_TOP - 1, z, "sidewalk");
                if (k == rise) {
                    out.set(x, STREET_TOP, z, "rw_globe_post");
                    out.set(x, STREET_TOP + 1, z, "rw_globe");
                } else if (k == 7 || k == 8) {
                    int kk = sign > 0 ? k - 7 : 8 - k;
                    out.set(x, STREET_TOP, z, "rw_subway_sign_" + (c.o > 0 ? 1 - kk : kk) + "[facing=" + c.in() + "]");
                } else {
                    out.set(x, STREET_TOP, z, "rw_subway_rail[facing=" + c.out() + "]");
                }
            }
            return false;   // the platform below is laid as usual
        }
        if (v >= 2) {
            // the wall behind, up to the stairwell's roof; on the sidewalk over it the railing
            out.column(x, z, c.bed, Math.max(Y + 6, Math.min(h + 5, STREET_TOP - 2)), "rw_tile");
            if (open) {
                out.set(x, STREET_TOP - 1, z, "sidewalk");
                out.set(x, STREET_TOP, z, k == rise ? "rw_globe_post" : "rw_subway_rail[facing=" + c.in() + "]");
                if (k == rise) {
                    out.set(x, STREET_TOP + 1, z, "rw_globe");
                }
            }
            return true;
        }
        if (k == -1) {
            out.column(x, z, c.bed, Y + 6, "rw_tile");
            return true;
        }
        if (k == 0) {
            out.set(x, c.bed, z, "rw_tunnel");
            out.set(x, Y, z, paving(c));
            clear(out, x, z, Y + 1, Y + 5);
            out.set(x, Y + 6, z, "rw_tunnel");
            return true;
        }
        out.column(x, z, c.bed, h - 1, "rw_tunnel");
        out.set(x, h, z, "rw_stair[facing=" + c.along(sign) + "]");
        if (h + 5 < STREET_TOP - 1) {
            clear(out, x, z, h + 1, h + 4);
            out.set(x, h + 5, z, "rw_tunnel");
        } else if (!open) {
            // the last covered step: the sidewalk is its roof, the railing across the opening's end on it
            clear(out, x, z, h + 1, STREET_TOP - 2);
            out.set(x, STREET_TOP - 1, z, "sidewalk");
            out.set(x, STREET_TOP, z, "rw_subway_rail[facing=" + c.along(sign) + "]");
            clear(out, x, z, STREET_TOP + 1, STREET_TOP + 3);
        } else {
            clear(out, x, z, h + 1, STREET_TOP + 3);
        }
        return true;
    }

    // ------------------------------------------------------------------ level crossings

    /**
     * The signals at each level crossing of a county road: a mast on the right of each way in,
     * set back from the line, its crossbuck and lights turned to the traffic coming.
     */
    private void crossings(ChunkSink out, Track t, int bx, int bz, Columns cols) {
        if (t.kind == Track.Kind.SUBWAY) {
            return;
        }
        for (int[] cr : t.crossings) {
            if (cr.length < 7 || cr[6] != 0 || Math.abs(cr[0] - bx - 8) > 40 || Math.abs(cr[2] - bz - 8) > 40) {
                continue;
            }
            double rx = cr[3] / 1000.0, rz = cr[4] / 1000.0;
            int at = t.project(cr[0] + 0.5, cr[2] + 0.5);
            double tx = t.tx[at], tz = t.tz[at];
            double sin = Math.abs(rx * tz - rz * tx);
            double back = Math.min(14, 5.5 / Math.max(0.35, sin));
            for (int way = -1; way <= 1; way += 2) {
                // traffic heading way * r: its mast before the line, on its right
                double hx = rx * way, hz = rz * way;
                double px = cr[0] + 0.5 - hx * back - hz * (cr[5] + 1.5), pz = cr[2] + 0.5 - hz * back + hx * (cr[5] + 1.5);
                int x = (int) Math.floor(px), z = (int) Math.floor(pz);
                if (x < bx || x > bx + 15 || z < bz || z > bz + 15) {
                    continue;
                }
                int k = (z - bz) << 4 | (x - bx);
                if (cols.road[k] || cols.top[k] < Terrain.WATER) {
                    continue;
                }
                int y = cols.top[k] + 1;
                String face = dir(-hx, -hz);
                out.set(x, y, z, "rw_xing_post");
                out.set(x, y + 1, z, "rw_xing_post");
                out.set(x, y + 2, z, "rw_xing_lights[facing=" + face + "]");
                out.set(x, y + 3, z, "rw_xing_buck[facing=" + face + "]");
            }
        }
    }
}
