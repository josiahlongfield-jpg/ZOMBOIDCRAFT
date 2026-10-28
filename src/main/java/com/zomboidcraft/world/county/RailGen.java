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
     * Union Station's way in from the subway (see {@link UnionStation}): the passage from its
     * platform to the station's hall on the samples PASS_LO..PASS_HI from the stop's middle (its
     * walls one more each side) out to PASS_END, the lot's edge. The el runs through the station's
     * own train hall ({@link UnionHall}), which builds everything there but the bed.
     */
    private static final int PASS_LO = -12, PASS_HI = -9, PASS_END = 14;
    /**
     * A depot's lot ({@link DepotSite}) runs DEPOT_REACH samples either way of its stop's middle
     * behind the platform, its canopy DEPOT_CANOPY.
     */
    private static final int DEPOT_REACH = Layout.DEPOT_LEN / 2, DEPOT_CANOPY = DEPOT_REACH - 3;
    /** A subway station's steel bents stand this many samples apart: a column on the platform, a girder across the roof. */
    private static final int BENT = 5;

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
        h = trench(x, z, h);
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

    /**
     * The channel dredged for the Glass Line's tubes: the harbour's bed taken down to the tubes'
     * rock fill across them and a little either side, its sides sloping back up to the bed as it
     * lay. Only where the water was (ashore the tubes are buried). Bridges' columns ask too.
     */
    double trench(int x, int z, double h) {
        if (!Terrain.isWater(h)) {
            return h;
        }
        for (Track t : near) {
            if (!t.tubed || !at(t, x, z) || t.mode[hit.i] != Track.TUBE) {
                continue;
            }
            double target = bedTop(t.ys[hit.i]) - 3 + 0.5, s = hit.distance;
            if (s <= 6.5) {
                h = Math.min(h, target);
            } else {
                double side = Math.max(0, h - target) * 1.5;
                if (s < 6.5 + side) {
                    h = Math.min(h, Terrain.lerp(target, h, (s - 6.5) / side));
                }
            }
        }
        return h;
    }

    /** True if (x, z) is over the Glass Line's tubes, or close beside them: no piers there. */
    boolean overTube(int x, int z) {
        for (Track t : near) {
            if (t.tubed && at(t, x, z) && t.mode[hit.i] == Track.TUBE && hit.distance < 9) {
                return true;
            }
        }
        return false;
    }

    /** The whole-block bed top for a bed at y: the ballast's quarter blocks round up to a whole one past three. */
    static int bedTop(double y) {
        int whole = (int) Math.floor(y), quarter = (int) Math.round((y - whole) * 4);
        return whole + (quarter == 4 ? 1 : 0);
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
                    case Track.TUBE -> tube(out, c);
                    default -> ground(out, c);
                }
            }
            if (!t.islands.isEmpty()) {
                islands(out, t, bx, bz, cols);
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
        /** The stop has a depot behind its platform (DepotSite). */
        final boolean depot;

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
            Y = bedTop(y);
            q = (int) Math.round((y - Math.floor(y)) * 4) % 4;
            bed = Y - 1;
            tx = t.tx[i];
            tz = t.tz[i];
            stop = stop(t, i);
            plat = stop != null && Math.signum(o) == stop.side();
            da = stop == null ? 0 : i - stop.middle();
            depot = stop != null && t.depots.get(t.stopAt[i]);
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
        if (c.depot && Math.abs(da) <= DEPOT_REACH) {
            // the depot's lot runs along behind: no fence, no shelter; its canopy carried out over the platform
            if (Math.abs(da) <= DEPOT_CANOPY) {
                depotCanopy(out, c);
                return;
            }
            if (s >= 6) {
                return;
            }
        } else if (s >= 6) {
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
     * A platform's canopy in front of a depot, from the depot's own canopy out to the yellow line:
     * on posts every six, the valance along its edge, lamps under it, benches between the posts
     * facing the line.
     */
    private void depotCanopy(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, la = Math.abs(c.da);
        double s = c.s;
        if (s >= 3) {
            out.set(x, Y + 5, z, s < 4 ? "rw_canopy_fascia[facing=" + c.in() + "]" : "rw_canopy");
        }
        if (s >= 4 && s < 5 && la % 6 == 3) {
            out.column(x, z, Y + 1, Y + 4, "rw_post");
        }
        if (s >= 5 && s < 6 && la % 6 == 0 && la > 0) {
            out.set(x, Y + 1, z, "rw_bench[facing=" + c.in() + "]");
        }
        if (s >= 3 && s < 4 && la % 12 == 6) {
            out.set(x, Y + 4, z, "rw_lamp_hang");
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
                hungBoard(out, c, Y + 4, 17);
            }
        }
    }

    /** A name board hung under a canopy, at |da| lo..lo + 3 (four boards). */
    private void hungBoard(ChunkSink out, Cut c, int y, int lo) {
        String key = key(c.stop);
        int la = Math.abs(c.da);
        if (key == null || la < lo || la > lo + 3) {
            return;
        }
        int k = c.da > 0 ? c.da - lo : c.da + lo + 3;
        out.set(c.x, y, c.z, "rw_name_" + key + "_" + tile(c, k, 4) + "[facing=" + c.in() + "]");
    }

    // ------------------------------------------------------------------ the el and the bridges

    private void viaduct(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, bed = c.bed;
        double s = c.s;
        if (c.t.hall.get(c.i)) {
            // through Union Station's train hall: only the bed on the station's deck, the trains' room over it
            if (s < 2) {
                ballast(out, c, "rw_ballast");
                out.set(x, bed - 1, z, "rw_deck");
                clear(out, x, z, Y + (c.q > 0 ? 1 : 0), Y + 6);
            }
            return;
        }
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
            if ((k == 0 || k == 1) && s < 4.5 && !overTube(x, z)) {
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
            if (s >= 5 && la % 8 == 0 && la <= 40) {
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
                hungBoard(out, c, Y + 5, 17);
            }
        } else {
            out.set(x, Y, z, paving(c));
            out.set(x, Y + 1, z, stairTop ? "air" : "rw_guard[facing=" + c.out() + "]");
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
        if (station && c.plat && !c.t.islands.isEmpty()) {
            islandTunnel(out, c);
            return;
        }
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
                bentRoof(out, c);
            } else if (s < back) {
                out.set(x, bed, z, "rw_tunnel");
                out.set(x, Y, z, s < 3 ? "rw_platform_edge[facing=" + c.in() + "]" : paving(c));
                clear(out, x, z, Y + 1, Y + 5);
                out.set(x, Y + 6, z, "rw_tunnel");
                litter(out, c, Y + 1, 2);
                bent(out, c);
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
            if (station) {
                bentRoof(out, c);
            }
        } else if (s < 4) {
            out.set(x, bed - 1, z, "rw_tunnel");
            out.set(x, bed, z, "rw_tunnel_floor");
            clear(out, x, z, Y, Y + 5);
            out.set(x, Y + 6, z, "rw_tunnel");
            if (station) {
                bentRoof(out, c);
            }
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

    /**
     * A subway station's steel, every {@link #BENT} samples: on the platform an I-beam column a
     * block back from the edge, its girder hanging under the roof from it to the back wall; over
     * the track (kept clear) and the far walkway the girder runs on flush with the roof.
     */
    private void bent(ChunkSink out, Cut c) {
        if (Math.floorMod(c.da, BENT) != 0) {
            return;
        }
        int x = c.x, z = c.z, Y = c.Y;
        bentRoof(out, c);
        if (c.s >= 3 && c.s < 4) {
            String f = "[facing=" + c.along(1) + "]";
            out.set(x, Y + 1, z, "rw_subway_column_base" + f);
            out.column(x, z, Y + 2, Y + 4, "rw_subway_column" + f);
            out.set(x, Y + 5, z, "rw_subway_column_cap" + f);
        } else if (c.s >= 4) {
            out.set(x, Y + 5, z, "rw_subway_beam[axis=" + c.across() + "]");
        }
    }

    private void bentRoof(ChunkSink out, Cut c) {
        if (Math.floorMod(c.da, BENT) == 0) {
            out.set(c.x, c.Y + 6, c.z, "rw_subway_girder[axis=" + c.across() + "]");
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
        if (s >= back - 2 && s < back - 1 && la % 4 == 0 && Math.floorMod(c.da, BENT) != 0) {
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

    // ------------------------------------------------------------------ the Glass Line

    /**
     * Where a column falls across one of the Glass Line's bores: -1 beside its middle (either
     * side), counting out to -5 the glass wall's column and -6 the gap between the two bores.
     */
    private static int rank(double o) {
        int k = (int) Math.floor(o);
        return k < 0 ? k : -1 - k;
    }

    /**
     * The Glass Line under the water: its glass tube on the concrete raft the pair sits on, in
     * the channel dredged for them (see {@link #trench}), built of tools/city/glassline.py's gl_
     * blocks. Across the bore: the raft over a rock fill, the ballast and the track in the
     * middle, the walkways along the walls at the cars' floor, the glass walls on their sills
     * turning in at 45 degrees to the roof glass, a white steel ring every fourth block, the
     * cable trays and the strip lights along the roof's corners, escape signs now and then. It
     * runs ashore into the tunnel through a concrete portal; at Eastside Quay the island
     * platform's glass hall stands between the bores ({@link #islandHall}).
     */
    private void tube(ChunkSink out, Cut c) {
        int m = rank(c.o);
        if (m < -6) {
            return;
        }
        if (c.stop != null && c.plat && m <= -3) {
            islandHall(out, c, m);
            return;
        }
        int x = c.x, z = c.z, Y = c.Y, bed = c.bed;
        if (portal(c)) {
            portal(out, c, m);
            return;
        }
        String f = "[facing=" + c.out() + "]";
        boolean rib = Math.floorMod(c.i, 4) == 0;
        // where the grade steps the tube up a block on a curve, a wall column can stand beside
        // the next one in a block higher: its glass then turns in a block higher too
        int up = (m == -5 || m == -4) && stepsUp(c, m) ? 1 : 0;
        out.set(x, bed - 2, z, "minecraft:gravel");
        out.set(x, bed - 1, z, "gl_keel");
        switch (m) {
            case -6 -> out.set(x, bed, z, "gl_keel");
            case -5 -> {
                out.set(x, bed, z, "gl_keel");
                out.set(x, Y, z, "gl_sill" + f);
                out.set(x, Y + 1, z, pane(c, "gl_wall_lo", rib, 0x61A1L) + f);
                out.set(x, Y + 2, z, (Math.floorMod(c.i, 96) == 0 ? "gl_wall_exit_" + ((c.o < 0) == (c.t.tubeWay(c.i) > 0) ? "r" : "l")
                        : pane(c, "gl_wall", rib, 0x61A2L)) + f);
                if (up > 0) {
                    out.set(x, Y + 3, z, pane(c, "gl_wall", rib, 0x61A7L) + f);
                }
                out.set(x, Y + 3 + up, z, pane(c, "gl_chamfer", rib, 0x61A3L) + f);
            }
            case -4 -> {
                out.set(x, bed, z, "gl_keel");
                out.set(x, Y, z, "gl_walk" + f);
                clear(out, x, z, Y + 1, Y + 3);
                if (up > 0) {
                    out.set(x, Y + 4, z, pane(c, "gl_wall", rib, 0x61A8L) + f);
                }
                out.set(x, Y + 4 + up, z, pane(c, "gl_chamfer", rib, 0x61A4L) + f);
            }
            case -3 -> {
                ballast(out, c, "rw_ballast");
                clear(out, x, z, Y + (c.q > 0 ? 1 : 0), Y + 3);
                out.set(x, Y + 4, z, light(c) + f);
                out.set(x, Y + 5, z, pane(c, "gl_chamfer", rib, 0x61A5L) + f);
            }
            default -> {
                ballast(out, c, "rw_ballast");
                clear(out, x, z, Y + (c.q > 0 ? 1 : 0), Y + 4);
                out.set(x, Y + 5, z, pane(c, "gl_crown", rib, 0x61A6L) + f);
            }
        }
    }

    /** True if a column next to this one, a block further in across the bore, has its bed a block higher. */
    private boolean stepsUp(Cut c, int m) {
        for (int k = 0; k < 4; k++) {
            int nx = c.x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = c.z + (k == 2 ? 1 : k == 3 ? -1 : 0);
            if (at(c.t, nx, nz) && c.t.mode[hit.i] == Track.TUBE && rank(hit.offset) == m + 1 && bedTop(c.t.ys[hit.i]) > c.Y) {
                return true;
            }
        }
        return false;
    }

    /**
     * A pane of the tube as forty years and the end of the world left it: a steel ring where
     * one falls, else mostly sound, some cracked, a few weeping where a seal gave, the worst
     * boarded over with plywood or taped under a tarp.
     */
    private String pane(Cut c, String base, boolean rib, long salt) {
        if (rib) {
            return base + "_rib";
        }
        double r = roll(c, salt);
        return switch (base) {
            case "gl_wall" -> r < 0.05 ? "gl_wall_cracked" : r < 0.065 ? "gl_wall_leak" : r < 0.075 ? "gl_wall_patch" : r < 0.085 ? "gl_wall_tarp" : base;
            case "gl_wall_lo" -> r < 0.05 ? "gl_wall_lo_cracked" : r < 0.065 ? "gl_wall_lo_leak" : base;
            case "gl_chamfer" -> r < 0.04 ? "gl_chamfer_cracked" : r < 0.05 ? "gl_chamfer_patch" : base;
            default -> r < 0.05 ? base + "_cracked" : base;
        };
    }

    /** The roof corner's fitting: a strip light two blocks long every eight (a third of them dead), the cable tray between. */
    private String light(Cut c) {
        int k = Math.floorMod(c.i, 8);
        if (k != 2 && k != 3) {
            return "gl_tray";
        }
        double r = Noise.unit(Noise.hash(county.seed ^ 0x6A4DL, Math.floorDiv(c.i, 8), c.o > 0 ? 1 : 0));
        return r < 0.3 ? "gl_lamp_off" : "gl_lamp";
    }

    /** True in the last two samples of a tube before it runs ashore into the tunnel: its concrete portal. */
    private static boolean portal(Cut c) {
        for (int d = -2; d <= 2; d++) {
            if (c.t.mode[c.t.wrap(c.i + d)] != Track.TUBE) {
                return true;
            }
        }
        return false;
    }

    /**
     * The portal where a tube meets the tunnel: a collar of concrete round the bore with the
     * tube's own room through it (no more, so on a curve, where the collar's edge runs slantwise
     * across the bore, nowhere opens on the water past the glass).
     */
    private void portal(ChunkSink out, Cut c, int m) {
        int x = c.x, z = c.z, Y = c.Y, bed = c.bed;
        if (m <= -5) {
            out.column(x, z, bed - 1, Y + 7, "rw_tunnel");
        } else if (m == -4) {
            out.column(x, z, bed - 1, Y, "rw_tunnel");
            clear(out, x, z, Y + 1, Y + 3);
            out.column(x, z, Y + 4, Y + 7, "rw_tunnel");
        } else {
            out.set(x, bed - 1, z, "rw_tunnel");
            out.set(x, bed, z, "rw_track_slab");
            clear(out, x, z, Y, Y + 4);
            out.column(x, z, Y + 5, Y + 7, "rw_tunnel");
        }
    }

    /**
     * Eastside Quay's island platform under the water: a hall of glass between the bores, its
     * roof two blocks over theirs, granite pavers on the raft, the yellow-lined edges, pendant
     * lamps, benches back to back down the middle and the call buttons on a post; a concrete
     * bulkhead closes each end. The glass stair up to the pier stands in its middle ({@link #quay}).
     */
    private void islandHall(ChunkSink out, Cut c, int m) {
        int x = c.x, z = c.z, Y = c.Y, la = Math.abs(c.da);
        int half = (c.stop.to() - c.stop.from()) / 2;
        boolean rib = Math.floorMod(c.i, 4) == 0;
        out.set(x, c.bed - 2, z, "minecraft:gravel");
        out.set(x, c.bed - 1, z, "gl_keel");
        out.set(x, c.bed, z, "gl_keel");
        if (la >= half) {
            // the bulkhead at the hall's end, the platform's edge kept clear of the cars
            if (m == -3) {
                out.set(x, Y, z, "rw_tunnel");
                clear(out, x, z, Y + 1, Y + 4);
                out.column(x, z, Y + 5, Y + 7, "rw_tunnel");
            } else {
                out.column(x, z, Y, Y + 7, "rw_tunnel");
            }
            return;
        }
        out.set(x, Y, z, m == -3 ? "tx_edge[facing=" + c.in() + "]" : roll(c, 0x61B1L) < 0.7 ? "tx_paver" : "tx_paver_b");
        clear(out, x, z, Y + 1, Y + 6);
        if (m == -3) {
            // over the edge the hall's glass meets the bore's roof
            out.set(x, Y + 6, z, pane(c, "gl_wall", rib, 0x61B2L) + "[facing=" + c.in() + "]");
        }
        out.set(x, Y + 7, z, pane(c, "gl_crown", rib, 0x61B3L) + "[facing=" + c.out() + "]");
        litter(out, c, Y + 1, 2);
        String in = "[facing=" + c.in() + "]";
        if (m == -4 && la % 6 == 3) {
            out.set(x, Y + 6, z, roll(c, 0x61B4L) < 0.3 ? "tx_pendant_off" : "tx_pendant");
        }
        if (m == -6 && (la == 30 || la == 42)) {
            out.set(x, Y + 1, z, "tx_bench" + in);
        } else if (m == -6 && c.da == 24) {
            out.set(x, Y + 1, z, "tx_bin");
        } else if (m == -6 && la == 36) {
            out.column(x, z, Y + 1, Y + 6, "rw_post");
        } else if (m == -5 && la == 36) {
            out.set(x, Y + 2, z, "zomboidcraft:train_call_button" + in);
        }
    }

    /**
     * The Glass Line's island platform under Mercy General, in the tunnel a flight under the
     * subway's: the tracks either side, steel columns down its middle under girders across the
     * roof, lamps, benches facing the tracks, the call buttons on the columns; the station's name
     * in the tile of the walls across the tracks. The stairs up to the subway's platform rise from
     * its middle ({@link #mercy}).
     */
    private void islandTunnel(ChunkSink out, Cut c) {
        int x = c.x, z = c.z, Y = c.Y, la = Math.abs(c.da);
        double s = c.s;
        if (s < 2) {
            trackway(out, c);
            bentRoof(out, c);
            return;
        }
        out.set(x, c.bed - 1, z, "rw_tunnel");
        out.set(x, c.bed, z, "rw_tunnel");
        out.set(x, Y, z, s < 3 ? "rw_platform_edge[facing=" + c.in() + "]" : paving(c));
        clear(out, x, z, Y + 1, Y + 5);
        out.set(x, Y + 6, z, "rw_tunnel");
        litter(out, c, Y + 1, 2);
        bentRoof(out, c);
        boolean stairs = la >= 12 && la <= 26;
        String in = "[facing=" + c.in() + "]";
        if (s >= 5 && Math.floorMod(c.da, 2 * BENT) == 0 && !stairs) {
            String f = "[facing=" + c.along(1) + "]";
            out.set(x, Y + 1, z, "rw_subway_column_base" + f);
            out.column(x, z, Y + 2, Y + 4, "rw_subway_column" + f);
            out.set(x, Y + 5, z, "rw_subway_column_cap" + f);
        } else if (s >= 4 && Math.floorMod(c.da, BENT) == 0) {
            out.set(x, Y + 5, z, "rw_subway_beam[axis=" + c.across() + "]");
        }
        if (s >= 4 && s < 5 && la == 10) {
            out.set(x, Y + 2, z, "zomboidcraft:train_call_button" + in);
        } else if (s >= 4 && s < 5 && la % 12 == 6 && !stairs) {
            out.set(x, Y + 1, z, "rw_bench" + in);
        }
        if (s >= 3 && s < 4 && la % 4 == 0 && Math.floorMod(c.da, BENT) != 0 && !stairs) {
            out.set(x, Y + 5, z, "rw_subway_lamp[axis=" + c.axis() + "]");
        }
    }

    /** What the Glass Line's stations build round their island platforms, column by column of the chunk. */
    private void islands(ChunkSink out, Track t, int bx, int bz, Columns cols) {
        for (Track.Island isl : t.islands) {
            if (Math.abs(isl.x() - bx - 8) > 72 || Math.abs(isl.z() - bz - 8) > 72) {
                continue;
            }
            for (int k = 0; k < 256; k++) {
                int x = bx + (k & 15), z = bz + (k >> 4);
                double u = isl.along(x + 0.5, z + 0.5), v = isl.across(x + 0.5, z + 0.5);
                if (isl.key().equals("mercy")) {
                    mercy(out, isl, x, z, u, v);
                } else {
                    quay(out, isl, x, z, u, v, cols.top[k]);
                }
            }
        }
    }

    /** The subway's platform's floor under Mercy General (and its bed a block under). */
    private static final int SUBWAY_FLOOR = Railway.SUBWAY_Y;

    /**
     * Mercy General's stairs from the Glass Line's island platform up to the subway's platform
     * overhead, one at each end, two wide: glass either side in the Glass Line's hall, tiled
     * walls up through the rock, coming up through the subway platform's floor inside a glass
     * balustrade by its back wall. A SUBWAY sign hangs over each foot.
     */
    private void mercy(ChunkSink out, Track.Island isl, int x, int z, double u, double v) {
        int Y = isl.y(), vi = (int) Math.floor(v);
        if (vi < -1 || vi > 2) {
            return;
        }
        String along = Math.abs(isl.ax()) >= Math.abs(isl.az()) ? "x" : "z", across = along.equals("x") ? "z" : "x";
        for (int sg = -1; sg <= 1; sg += 2) {
            int j = (int) Math.floor(sg * u) - 14;   // 0 the foot, 1..10 the steps
            if (j < 0 || j > 10) {
                continue;
            }
            String climb = dir(isl.ax() * sg, isl.az() * sg);
            String back = dir(-isl.ax() * sg, -isl.az() * sg);
            int h = Y + j;
            if (vi == 0 || vi == 1) {
                if (j == 0) {
                    out.set(x, Y + 5, z, "tx_sign_subway_" + (sg > 0 ? vi : 1 - vi) + "[facing=" + back + "]");
                    continue;
                }
                out.column(x, z, Y + 1, h - 1, "rw_tile");
                out.set(x, h, z, "rw_stair[facing=" + climb + "]");
                // four clear over every step (a jump up them never bumps a head), up through the
                // hall's beams and roof into the rock
                if (h + 5 >= SUBWAY_FLOOR - 1) {
                    // through the subway platform's floor
                    clear(out, x, z, h + 1, SUBWAY_FLOOR + 4);
                } else {
                    clear(out, x, z, h + 1, h + 4);
                    if (h + 5 > Y + 6) {
                        out.set(x, h + 5, z, "rw_tunnel");
                    }
                }
                if (j == 3) {
                    out.set(x, SUBWAY_FLOOR + 1, z, "tx_glass_rail[axis=" + across + "]");
                }
                continue;
            }
            if (j == 0) {
                continue;
            }
            // the sides: glass in the hall, tile up through the rock to the subway's floor
            out.column(x, z, Y + 1, Y + 5, "tx_glass[axis=" + along + "]");
            out.column(x, z, Y + 6, Math.min(h + 5, SUBWAY_FLOOR - 2), "rw_tile");
            if (vi == 2 && j >= 4 && j <= 9 && !subwayBent(x, z)) {
                out.set(x, SUBWAY_FLOOR + 1, z, "tx_glass_rail[axis=" + along + "]");
            }
        }
    }

    /** True where one of the subway's station columns stands (they keep the stairs' balustrade's place). */
    private boolean subwayBent(int x, int z) {
        for (Track t : county.tracks()) {
            if (t.kind != Track.Kind.SUBWAY || t.tubed || !at(t, x, z)) {
                continue;
            }
            Track.Stop st = stop(t, hit.i);
            if (st != null && hit.distance >= 3 && hit.distance < 4 && Math.floorMod(hit.i - st.middle(), BENT) == 0) {
                return true;
            }
        }
        return false;
    }

    /** The pier's deck and the pavilion's floor: level with the quay's paving. */
    private static final int PIER = Terrain.CITY;
    /** Where Eastside Quay's stair starts, along the island from its middle. */
    private static final int QUAY_FOOT = -9;

    /**
     * Eastside Quay's way up: a granite stair in a glass box rising from the middle of the hall
     * up through the water to a glass pavilion on a pier over the tubes, and the jetty from its
     * door to the quay.
     */
    private void quay(ChunkSink out, Track.Island isl, int x, int z, double u, double v, int top) {
        int Y = isl.y(), ui = (int) Math.floor(u), vi = (int) Math.floor(v);
        String along = Math.abs(isl.ax()) >= Math.abs(isl.az()) ? "x" : "z", across = along.equals("x") ? "z" : "x";
        String up = dir(isl.ax(), isl.az()), right = dir(-isl.az(), isl.ax()), left = dir(isl.az(), -isl.ax());
        int j = ui - QUAY_FOOT;
        if (j >= 0 && j <= 19 && vi >= -2 && vi <= 1) {
            quayStair(out, x, z, Y, j, vi, vi < 0 ? left : right, along, up, dir(-isl.ax(), -isl.az()));
        }
        if (ui >= 1 && ui <= 16 && vi >= -5 && vi <= 4) {
            pavilion(out, x, z, ui, vi, j, along, across, right);
        }
        // the pavilion's steel: crossbeams under its ends on legs either side of the tubes, beams along its sides
        if ((ui == 1 || ui == 16) && vi >= -13 && vi <= 12) {
            out.set(x, PIER - 1, z, "tx_beam[axis=" + across + "]");
            if (vi == -13 || vi == 12) {
                out.column(x, z, top + 1, PIER - 2, "tx_column");
            }
        } else if (ui > 1 && ui < 16 && (vi == -5 || vi == 4 || vi == -13 || vi == 12)) {
            out.set(x, PIER - 1, z, "tx_beam[axis=" + along + "]");
        }
        // the glass canopy over the door on its arms from the wall's columns, the sign hung under it
        if (ui >= 6 && ui <= 11 && vi >= 5 && vi <= 7) {
            int k = vi - 4;
            out.set(x, PIER + 5, z, k == 3 ? "tx_canopy_edge[facing=" + right + "]"
                    : ui == 6 || ui == 11 ? "tx_canopy_arm_" + (k == 1 ? 0 : 6) + "[facing=" + right + "]" : "tx_canopy");
            if (vi == 6 && (ui == 8 || ui == 9)) {
                out.set(x, PIER + 4, z, "tx_sign_subway_" + (ui == 8 ? 0 : 1) + "[facing=" + right + "]");
            }
        }
        // the jetty from the door to the quay, on piles clear of the tubes
        if (ui >= 7 && ui <= 10 && vi >= 5 && vi <= 40) {
            boolean edge = ui == 7 || ui == 10;
            if (top < Terrain.WATER) {
                out.set(x, PIER, z, "rw_grate");
                clear(out, x, z, PIER + 1, PIER + 2);
                if (edge) {
                    out.set(x, PIER + 1, z, "railing[facing=" + (ui == 7 ? dir(-isl.ax(), -isl.az()) : up) + "]");
                    if (vi >= 13 && vi % 6 == 1) {
                        out.column(x, z, top + 1, PIER - 1, "tx_column");
                    }
                }
            } else {
                // ashore: on to the quay at the deck's level, through its railing
                boolean landing = false;
                for (int d = 1; d <= 3; d++) {
                    int px = x - d * (int) Math.round(-isl.az()), pz = z - d * (int) Math.round(isl.ax());
                    landing |= Terrain.isWater(county.terrain.height(px + 0.5, pz + 0.5));
                }
                if (landing) {
                    if (top < PIER) {
                        out.column(x, z, top + 1, PIER, "sidewalk");
                    }
                    clear(out, x, z, Math.max(top, PIER) + 1, Math.max(top, PIER) + 3);
                }
            }
        }
    }

    /**
     * A column of Eastside Quay's stair (j steps from its foot) or of the glass box round it (vi
     * -2 and 1, facing out to their sides): in the hall granite under the steps and glass over
     * the stringers; above it the box's glass walls and its sloping glass roof under the water.
     */
    private void quayStair(ChunkSink out, int x, int z, int Y, int j, int vi, String side, String along, String up, String down) {
        int step = Y + 1 + j;
        int hall = Y + 6;     // the hall's air goes up to here, its roof glass a block over
        if (vi == -1 || vi == 0) {
            for (int y = Y + 1; y < step; y++) {
                out.set(x, y, z, y <= hall ? "tx_granite" : "gl_keel");
            }
            out.set(x, step, z, "tx_step[facing=" + up + "]");
            // four clear over every step, the box's sloping roof a block over that
            clear(out, x, z, step + 1, Math.min(step + 4, PIER + 4));
            if (step + 5 > hall + 1 && step + 5 < PIER) {
                out.set(x, step + 5, z, "gl_chamfer[facing=" + down + "]");
            }
            return;
        }
        String f = "[facing=" + side + "]";
        for (int y = Y + 1; y <= hall; y++) {
            out.set(x, y, z, y <= step ? "tx_granite" : "tx_glass[axis=" + along + "]");
        }
        if (step + 1 <= hall + 1 && hall + 1 <= step + 4) {
            out.set(x, hall + 1, z, "gl_wall" + f);
        }
        for (int y = Math.max(hall + 2, step + 1); y <= Math.min(step + 5, PIER - 1); y++) {
            out.set(x, y, z, "gl_wall" + f);
        }
    }

    /**
     * Eastside Quay's pavilion on its pier over the tubes: a terrazzo floor with the stair
     * coming up through it inside a glass balustrade, glass walls on steel columns, the doors to
     * the jetty, a flat roof with a rooflight, ticket machines, validators, a bench and a bin.
     */
    private void pavilion(ChunkSink out, int x, int z, int ui, int vi, int j, String along, String across, String right) {
        boolean stair = vi == -1 || vi == 0;
        boolean ends = ui == 1 || ui == 16, sides = vi == -5 || vi == 4;
        long h = Noise.hash(county.seed ^ 0x9A71L, x, z);
        double r = Noise.unit(h);
        // the floor, open where the stair comes up
        if (!(stair && j >= 15 && j <= 19)) {
            boolean mat = vi >= 3 && (ui == 8 || ui == 9);
            out.set(x, PIER, z, mat ? "tx_terrazzo_d" : r < 0.75 ? "tx_terrazzo" : "tx_terrazzo_b");
        }
        // the roof (four clear over the stair's top step), its rooflight and the parapet round it
        boolean light = ui >= 4 && ui <= 13 && vi >= -3 && vi <= 2;
        out.set(x, PIER + 5, z, light ? "tx_roof_glass" : "tx_roof");
        if (ends || sides) {
            out.set(x, PIER + 6, z, "tx_parapet");
        }
        if (ends && sides || sides && (ui == 6 || ui == 11) || ends && vi == 0) {
            out.set(x, PIER + 1, z, "tx_column_base");
            out.column(x, z, PIER + 2, PIER + 3, "tx_column");
            out.set(x, PIER + 4, z, "tx_column_cap");
            return;
        }
        if (ends || sides) {
            String axis = sides ? along : across;
            if (vi == 4 && (ui == 8 || ui == 9)) {
                // the doors out to the jetty, one hanging open
                String props = "[facing=" + right + ",hinge=" + (ui == 8 ? "right" : "left") + ",open=" + (ui == 8) ;
                out.set(x, PIER + 1, z, "tx_door" + props + ",half=lower]");
                out.set(x, PIER + 2, z, "tx_door" + props + ",half=upper]");
                out.set(x, PIER + 3, z, "tx_glass_t[axis=" + axis + "]");
                out.set(x, PIER + 4, z, "tx_glass[axis=" + axis + "]");
                return;
            }
            for (int y = PIER + 1; y <= PIER + 4; y++) {
                double g = Noise.unit(Noise.hash(county.seed ^ 0x9A72L + y, x, z));
                out.set(x, y, z, (g < 0.08 ? "tx_glass_broken" : g < 0.18 ? "tx_glass_cracked" : g < 0.22 ? "tx_glass_frame"
                        : y == PIER + 1 ? "tx_glass_t" : "tx_glass") + "[axis=" + axis + "]");
            }
            return;
        }
        clear(out, x, z, PIER + 1, PIER + 4);
        if (stair && j >= 15 && j <= 18) {
            return;
        }
        // the balustrade round the opening
        if (stair && j == 14) {
            out.set(x, PIER + 1, z, "tx_glass_rail[axis=" + across + "]");
        } else if ((vi == -2 || vi == 1) && j >= 14 && j <= 18) {
            out.set(x, PIER + 1, z, "tx_glass_rail[axis=" + along + "]");
        } else if (vi == -4 && (ui == 3 || ui == 4)) {
            out.set(x, PIER + 1, z, ui == 3 && r < 0.4 ? "tx_tvm_smashed[facing=" + right + "]" : "tx_tvm[facing=" + right + "]");
            out.set(x, PIER + 2, z, ui == 3 && r < 0.4 ? "tx_tvm_top_smashed[facing=" + right + "]" : "tx_tvm_top[facing=" + right + "]");
        } else if (vi == -4 && ui == 14) {
            out.set(x, PIER + 1, z, "tx_bench[facing=" + right + "]");
        } else if (vi == 3 && ui == 2) {
            out.set(x, PIER + 1, z, "tx_bin");
        } else if ((vi == -3 || vi == 2) && ui == 12) {
            out.set(x, PIER + 1, z, "tx_validator[facing=" + right + "]");
        } else if (r < 0.06) {
            out.set(x, PIER + 1, z, r < 0.025 ? "paper" : r < 0.04 ? "glass_shards" : r < 0.05 ? "blood_splat" : "gc_leaves_1[facing=north]");
        }
        if (vi == 2 && (ui == 8 || ui == 9)) {
            // over the way in: down to the trains
            out.set(x, PIER + 4, z, "tx_sign_subway_" + (ui == 8 ? 0 : 1) + "[facing=" + right + "]");
        } else if ((ui == 4 || ui == 13) && (vi == -3 || vi == 2)) {
            out.set(x, PIER + 4, z, r < 0.3 ? "tx_pendant_off" : "tx_pendant");
        }
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
