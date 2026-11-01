package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * EXCHANGE HALL, the Grayhaven Subway's stop that closed in 1961, and the transit authority's
 * control bunker dug under it (ghost station, wave 2 of the hidden bunkers; tools/city/ghost.py is
 * its kit, after the photos in refs/subway).
 *
 * Trains run straight past. From the track all that shows is a strip of old platform edge and a
 * concrete wall where the platform was, with three arches in it bricked up; the middle one has a
 * newer steel door in it, grey-blue, a grille in the arch's head over it. Behind the wall the
 * station is still there in the dark: the vault of green and cream tile with its brass lamps and
 * the skylight the street was laid over, the name in mosaic, benches, the posters of 1960, the
 * stairs to the street bricked up at the top. Off the platform a tiled head house with a steel
 * door (TRANSIT AUTHORITY, AUTHORIZED PERSONNEL ONLY) holds the stairs down, twenty-three steps
 * under the street, to the landing and the blast door of the {@link Bunker} dressed as
 * {@link Bunker.Kind#SUBWAY}: the control room with its model board of the line, the relay room,
 * the radio room and the rooms to live in.
 *
 * Its frame (a {@link Placer}): x runs along the track (the way that keeps the ghost side on the
 * right), z out from the track's middle toward the old platform, in whole columns (z = k holds the
 * columns k..k+1 blocks out), y up from the bed top (the platform's floor is y = 0). The tunnel leaves
 * every column of that side from two blocks out to this place (RailGen.tunnel), so the two agree on
 * the columns by asking the track the same question ({@link #ours}).
 */
final class GhostSite extends Site {
    /** How deep the bunker's floor is under the platform's. */
    static final int DEPTH = 23;

    /** The frame: the track, its ghost, the world column of (0, 0) and the ways x and z run. */
    record Frame(Track t, Track.Ghost g, int ox, int oz, int ax, int az, int bx, int bz, int rot, int back) {
        int[] world(int x, int k) {
            return new int[] {ox + x * ax + k * bx, oz + x * az + k * bz};
        }
    }

    private final County county;
    private final Track.Hit hit = new Track.Hit();
    private Frame f;
    private Placer g;

    GhostSite(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    /** The subway's ghost station's frame, or null if there's none. */
    static Frame frame(County county) {
        for (Track t : county.tracks()) {
            if (t.kind == Track.Kind.SUBWAY && t.ghost != null) {
                return frame(t, t.ghost);
            }
        }
        return null;
    }

    static Frame frame(Track t, Track.Ghost gh) {
        int m = gh.mid(), side = gh.side();
        int tx = Math.round(t.tx[m]), tz = Math.round(t.tz[m]);
        int ax = side * tx, az = side * tz;           // along x: the ghost side on the right
        int bx = side * -tz, bz = side * tx;          // out: toward it
        int rot = ax == 1 ? 0 : az == 1 ? 1 : ax == -1 ? 2 : 3;
        int back = Math.max(5, t.street[m]) + 1;
        double px = t.xs[m] + bx * 0.5, pz = t.zs[m] + bz * 0.5;
        Track.Hit h = new Track.Hit();
        for (int r = 0; r <= 3; r++) {
            for (int cx = (int) Math.floor(px) - r; cx <= (int) Math.floor(px) + r; cx++) {
                for (int cz = (int) Math.floor(pz) - r; cz <= (int) Math.floor(pz) + r; cz++) {
                    t.nearest(t.candidates(cx, cz), cx + 0.5, cz + 0.5, h);
                    if (h.i < 0 || Math.signum(h.offset) != side || (int) Math.floor(h.distance) != 0) {
                        continue;
                    }
                    int x = gh.da(h.i, t.n) * side;
                    if (Math.abs(x) <= 2) {
                        return new Frame(t, gh, cx - x * ax, cz - x * az, ax, az, bx, bz, rot, back);
                    }
                }
            }
        }
        return null;
    }

    /** The world column on the old platform just inside the arch's door, or null. */
    static int[] platform(County county) {
        Frame fr = frame(county);
        return fr == null ? null : fr.world(0, 5);
    }

    /** The place: everything the station and its bunker may reach, at the bed top's level. */
    static Place place(County county, long seed) {
        Frame fr = frame(county);
        if (fr == null) {
            return null;
        }
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] c : new int[][] {{-36, -34}, {-36, 44}, {104, -34}, {104, 44}}) {
            int[] w = fr.world(c[0], c[1]);
            x0 = Math.min(x0, w[0]);
            z0 = Math.min(z0, w[1]);
            x1 = Math.max(x1, w[0]);
            z1 = Math.max(z1, w[1]);
        }
        return new Place(PlaceType.HIDEOUT, "ghost_station", x0, z0, x1, z1, fr.g().y(), seed, -1, -1);
    }

    /** True if the column at (x, k) is the ghost station's rather than the tunnel's. */
    private boolean ours(int x, int k) {
        int[] w = f.world(x, k);
        Track t = f.t();
        t.nearest(t.candidates(w[0], w[1]), w[0] + 0.5, w[1] + 0.5, hit);
        return hit.i >= 0 && hit.distance < t.reach() && hit.distance >= 2 && f.g().covers(hit.i, hit.offset, t.n);
    }

    @Override
    void build() {
        f = frame(county);
        if (f == null) {
            return;
        }
        g = new Placer(out, f.ox() - p.x0, 0, f.oz() - p.z0, f.rot());
        double r = chance();
        Bunker.State state = r < 0.45 ? Bunker.State.SEALED : r < 0.75 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int half = f.g().half(), back = f.back();
        for (int x = -half - 4; x <= half + 4; x++) {
            for (int k = 2; k <= back + 1; k++) {
                if (ours(x, k)) {
                    column(x, k, half, back, state);
                }
            }
        }
        dress(half, back, state);
        sealedStairs(19, back);
        headHouse(back, state);
        int xv = stairs(back);
        Placer bp = g.turned(xv + 5, -DEPTH, back - 1, 3);
        vestibule(bp, state);
        Bunker bunker = new Bunker(bp, new SplittableRandom(rng.nextLong()), Bunker.Kind.SUBWAY, state);
        bunker.build();
        g.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " subway", 0, 1, 4);
        bp.mark("bunker door", 0, 1, -2);
    }

    // ------------------------------------------------------------------ the old platform

    /** One column of it: k = 2 the old platform's edge, 3 the wall, then the platform, its back wall and the earth behind. */
    private void column(int x, int k, int half, int back, Bunker.State state) {
        boolean end = Math.abs(x) >= half;
        if (Math.abs(x) > half) {
            // past the platform: the tunnel as it runs everywhere else
            g.set(x, -2, k, "rw_tunnel");
            g.set(x, -1, k, k < 3 ? "rw_track_slab" : k < 4 ? "rw_tunnel_floor" : "rw_tunnel");
            for (int y = 0; y <= 5; y++) {
                g.set(x, y, k, k < 4 ? "air" : "rw_tunnel");
            }
            g.set(x, 6, k, "rw_tunnel");
            return;
        }
        g.set(x, -2, k, "rw_tunnel");
        g.set(x, -1, k, "rw_tunnel");
        if (k == 2) {
            g.set(x, 0, k, "rw_platform_edge[facing=north]");
            clear(x, k, 1, 5);
            g.set(x, 6, k, "rw_tunnel");
            if (rng.nextDouble() < 0.06) {
                g.set(x, 1, k, pick("paper", "glass_shards", "rubble"));
            }
            return;
        }
        if (k == 3) {
            g.set(x, 0, k, "rw_platform");
            for (int y = 1; y <= 5; y++) {
                String b = end ? "rw_tunnel" : wall(x, y, state);
                if (b != null) {
                    g.set(x, y, k, b);
                }
            }
            g.set(x, 6, k, "rw_tunnel");
            g.set(x, 7, k, "rw_tunnel");
            return;
        }
        if (k > back) {
            for (int y = -2; y <= 8; y++) {
                g.set(x, y, k, "rw_tunnel");
            }
            return;
        }
        if (k == back || end) {
            // the station's wall: the green dado, the band at eye height, cream tile to the vault
            g.set(x, 0, k, "rw_tunnel");
            for (int y = 1; y <= 6; y++) {
                g.set(x, y, k, y <= 2 ? "gh_tile_green" : y == 3 ? "gh_tile_band" : "gh_tile");
            }
            g.set(x, 7, k, "gh_vault");
            g.set(x, 8, k, "rw_tunnel");
            return;
        }
        // the platform: worn paving, the tile vault over it springing from both walls
        double w = rng.nextDouble();
        g.set(x, 0, k, w < 0.5 ? "rw_platform_3" : w < 0.85 ? "rw_platform_2" : "rw_platform");
        clear(x, k, 1, 6);
        if (k == 4) {
            g.set(x, 6, k, "gh_haunch[facing=south]");
        } else if (k == back - 1) {
            g.set(x, 6, k, "gh_haunch[facing=north]");
        }
        int mid = (4 + back - 1) / 2;
        String crown = Math.floorMod(x, 6) == 0 ? "gh_vault_rib" : "gh_vault";
        if (k == mid && Math.floorMod(x, 12) == 6) {
            crown = "gh_skylight";
        }
        g.set(x, 7, k, crown);
        g.set(x, 8, k, "rw_tunnel");
    }

    /**
     * The wall where the platform's edge was, seen from the track: the tunnel's concrete with three
     * arches in it bricked up (the old way out from the platform at each end of its middle), the
     * middle one with the steel door. Tile 0 is the viewer's left, looking from the track (x falls
     * left to right).
     */
    private String wall(int x, int y, Bunker.State state) {
        for (int cx : new int[] {-12, 0, 12}) {
            int col = cx + 1 - x;
            if (col < 0 || col > 3 || y > 4) {
                continue;
            }
            int row = y - 1;
            boolean middle = col == 1 || col == 2;
            if (cx == 0) {
                if (middle && row < 2) {
                    return null;   // the door, laid by dress()
                }
                return "gh_portal_" + col + "_" + row + "[facing=north]";
            }
            if (middle && row < 3) {
                return "gh_brick";
            }
            return middle ? "gh_arch_" + col + "_3[facing=north]" : "gh_portal_" + col + "_" + row + "[facing=north]";
        }
        return "gh_infill[facing=north]";
    }

    private void clear(int x, int k, int y0, int y1) {
        for (int y = y0; y <= y1; y++) {
            g.set(x, y, k, "air");
        }
    }

    private String pick(String... s) {
        return s[rng.nextInt(s.length)];
    }

    /** The door in the arch, and what the station kept: benches, lamps, the posters, its name, the map, the dust. */
    private void dress(int half, int back, Bunker.State state) {
        // the steel double door, flush with the track's side of the wall; whoever got in left it open
        boolean open = state != Bunker.State.SEALED;
        Doors.place(g, 0, 1, 3, "gh_door", "south", "left", false);
        Doors.place(g, -1, 1, 3, "gh_door", "south", "right", open);
        // the name in the band, twice; tile 0 at the viewer's left (looking at the back wall, x rises to the left)
        for (int start : new int[] {-16, 12}) {
            for (int i = 0; i < 4; i++) {
                g.set(start + 3 - i, 3, back, "gh_name_" + i + "[facing=north]");
            }
        }
        int mid = (4 + back - 1) / 2;
        for (int x = -half + 1; x < half; x++) {
            boolean busy = x >= 0 && x <= 8 || x >= 17 && x <= 23;
            if (Math.floorMod(x, 6) == 3) {
                g.set(x, 6, mid, "gh_fixture");
            }
            if (!busy && Math.floorMod(x, 10) == 5) {
                g.set(x, 1, back - 1, "rw_bench[facing=north]");
            } else if (!busy && Math.floorMod(x, 10) == 0 && x != -10) {
                g.set(x, 2, back - 1, "gh_poster_" + rng.nextInt(3) + "[facing=north]");
            }
            // the litter of the years: papers, plaster off the vault, a bottle, what the rats left
            for (int k = 4; k < back; k++) {
                if (g.get(x, 1, k) != null && !"air".equals(g.get(x, 1, k))) {
                    continue;
                }
                double d = rng.nextDouble();
                String junk = d < 0.05 ? "paper" : d < 0.07 ? "rubble" : d < 0.08 ? "debris_chunk" : d < 0.085 ? "trash_bag[facing=north]"
                        : d < 0.09 ? "cardboard_box" : d < 0.092 ? "fm_bones[facing=" + pick("north", "east", "south", "west") + "]" : null;
                if (junk != null) {
                    g.set(x, 1, k, junk);
                }
            }
            if (rng.nextDouble() < 0.25) {
                g.set(x, 5, rng.nextBoolean() ? 4 : back - 1, "bunk_cobweb");
            }
        }
        // the line's map on the back wall, this stop struck out; the notice by the door
        g.set(-5, 2, back - 1, "gh_line_map_0[facing=north]");
        g.set(-6, 2, back - 1, "gh_line_map_1[facing=north]");
        g.set(-10, 2, back - 1, "gh_closed[facing=north]");
        g.set(1, 3, 4, "gh_closed[facing=south]");
        // the station's furniture stacked under a tarp at the far end
        for (int x = -half + 2; x <= -half + 4; x++) {
            g.set(x, 1, back - 1, "tarp_green");
        }
    }

    /**
     * The stairs up to the street at the platform's east end, bricked up at the top in 1961: through
     * the back wall a landing under the sidewalk and three steps up to the brick, the notice on it.
     */
    private void sealedStairs(int x0, int back) {
        for (int x = x0 - 1; x <= x0 + 6; x++) {
            for (int k = back + 1; k <= back + 3; k++) {
                for (int y = -1; y <= 7; y++) {
                    g.set(x, y, k, "rw_tunnel");
                }
            }
        }
        for (int x = x0; x <= x0 + 1; x++) {
            g.set(x, 0, back, "rw_platform_3");
            clear(x, back, 1, 3);
            g.set(x, 4, back, "gh_tile_band");
        }
        for (int x = x0; x <= x0 + 5; x++) {
            int step = Math.max(0, x - x0 - 1);
            for (int k = back + 1; k <= back + 2; k++) {
                g.set(x, step, k, step == 0 ? "rw_platform_3" : "rw_stair[facing=east]");
                for (int y = step + 1; y <= step + 3; y++) {
                    g.set(x, y, k, x == x0 + 5 ? "gh_brick" : "air");
                }
                if (x < x0 + 5 && rng.nextDouble() < 0.3) {
                    g.set(x, step + 1, k, pick("rubble", "paper", "debris_chunk"));
                }
            }
        }
        g.set(x0 + 4, 5, back + 1, "gh_closed[facing=west]");
        g.set(x0 + 1, 3, back + 2, "bunk_cobweb");
    }

    // ------------------------------------------------------------------ the way down

    /**
     * The head house on the platform: a tiled box over the stairs, x 2..7 between the platform and
     * the back wall (z back - 3 .. back), its steel door at the west end, the sign over it.
     */
    private void headHouse(int back, Bunker.State state) {
        for (int x = 2; x <= 7; x++) {
            for (int k = back - 3; k < back; k++) {
                boolean wall = x == 2 || x == 7 || k == back - 3;
                for (int y = 1; y <= 4; y++) {
                    g.set(x, y, k, wall ? (y == 1 ? "gh_tile_green" : "gh_tile") : "air");
                }
                g.set(x, 5, k, "gh_tile");
            }
        }
        Doors.place(g, 2, 1, back - 2, "gh_door", "west", "right", state == Bunker.State.BREACHED);
        Doors.place(g, 2, 1, back - 1, "gh_door", "west", "left", false);
        g.set(1, 3, back - 1, "gh_staff[facing=west]");
        g.set(1, 3, back - 2, "gh_closed[facing=west]");
        g.set(5, 2, back - 4, "gh_line_map_0[facing=north]");
        g.set(4, 2, back - 4, "gh_line_map_1[facing=north]");
    }

    /**
     * The stairs down from the head house, two wide at z back - 2 .. back - 1, under the platform
     * and on under the street: twelve steps, a landing, the rest, lined in concrete, pipes down one
     * wall, a lamp now and then. Returns the first column of the landing at the foot.
     */
    private int stairs(int back) {
        int y = 0, steps = 0, landing = 0, x = 4;
        while (y >= 1 - DEPTH) {
            boolean flat = steps == 12 && landing < 2;
            int floor = flat ? y - 1 : y;
            for (int k = back - 3; k <= back; k++) {
                boolean in = k == back - 2 || k == back - 1;
                int top = x <= 6 ? 4 : -1;
                for (int yy = floor - 1; yy <= Math.min(floor + 5, top); yy++) {
                    String b;
                    if (!in) {
                        b = yy >= 1 ? null : "bunk_concrete";
                    } else if (yy < floor) {
                        b = "bunk_concrete";
                    } else if (yy == floor) {
                        b = flat ? "bunk_floor_dirty" : "rw_stair[facing=west]";
                    } else if (yy <= floor + 4) {
                        b = "air";
                    } else {
                        b = "bunk_concrete";
                    }
                    if (b != null) {
                        g.set(x, yy, k, b);
                    }
                }
            }
            if (x > 6 && Math.floorMod(x, 5) == 0 && floor + 4 <= -1) {
                g.set(x, floor + 4, back - 2, "bunk_lamp_hang");
            }
            if (x > 6 && floor + 3 <= -1) {
                g.set(x, floor + 3, back - 1, "bunk_pipes[facing=north]");
            }
            if (flat) {
                landing++;
                if (landing == 2) {
                    y--;
                }
            } else {
                steps++;
                y--;
            }
            x++;
        }
        return x;
    }

    /**
     * In the bunker's frame: the landing in front of the blast door (x 0..1, z -5..-1, the stairs
     * arriving at z -6), the stencils and the sign, the phone to the control room, the red lamp.
     */
    private void vestibule(Placer bp, Bunker.State state) {
        for (int x = -1; x <= 2; x++) {
            for (int z = -5; z <= -1; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean room = x >= 0 && x <= 1 && y >= 1 && y <= 3;
                    bp.set(x, y, z, room ? "air" : y == 0 ? "bunk_floor_dirty" : y == 4 ? "bunk_concrete" : "gh_tile");
                }
                bp.set(x, 1, z, x == -1 || x == 2 ? "gh_tile_green" : "air");
            }
        }
        bp.set(1, 3, -1, state == Bunker.State.SEALED ? "bunk_lamp_red_lit[facing=north]" : "bunk_lamp_red[facing=north]");
        bp.set(0, 2, -3, "gh_staff[facing=east]");
        bp.set(1, 2, -2, "bunk_phone[facing=west]");
        bp.set(1, 2, -4, "bunk_extinguisher[facing=west]");
        bp.set(0, 3, -4, "bunk_lamp_hang");
        if (state == Bunker.State.BREACHED) {
            bp.set(1, 1, -3, "home_blood_drag[facing=north]");
        }
        if (state != Bunker.State.SEALED) {
            bp.set(0, 1, -2, "paper");
            bp.set(1, 1, -4, "glass_shards");
        }
    }
}
