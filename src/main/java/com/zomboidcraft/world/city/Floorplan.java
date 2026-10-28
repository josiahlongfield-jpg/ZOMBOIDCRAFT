package com.zomboidcraft.world.city;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Plans one floor of a city building and fits it out ({@link Floors} says what goes where): a
 * corridor from the stair and lift round the floor a few blocks in from the windows, rooms cut
 * along the windows and round the core either side of it, the open floor between them filled
 * with whatever the floor is for. Every room has a door onto the corridor or the open floor,
 * and nothing is set where it would cut a cell off from the stairs, so players and zombies
 * can reach every corner.
 *
 * Works on any outline: the host (a generated {@link Tower}, a building from the pack in
 * {@link PackTower}) says which cells of the floor are open, which are the core and which must
 * stay walkable in front of its doors. Coordinates are the host's, the floor's slab at y and
 * rows y + 1 .. y + rows of room above it.
 */
public final class Floorplan {
    /** What the host knows about its floor. */
    public interface Site {
        /** A cell of open floor with full headroom that nothing else has claimed. */
        boolean open(int x, int z);

        /** The stair and lift core (and anything else built through the floor). */
        boolean core(int x, int z);

        /** Floor that must stay walkable: the landing in front of the core's doors. */
        boolean keep(int x, int z);
    }

    // cell states
    static final byte SHELL = 0, OPEN = 1, WALK = 2, WALL = 3, ROOM = 4, ITEM = 5, CLEAR = 6, CORE = 7, DOOR = 8;

    final Placer p;
    final SplittableRandom rng;
    final int w, d, y, rows;
    final double lit;
    final byte[] g;
    private final boolean[] keep0;
    /** Distance in from the outside walls (0 on them), for the cells of the floor. */
    final int[] dist;
    final List<Room> rooms = new ArrayList<>();
    /** The open floor the floor's type fills (cells not in a room, not corridor). */
    final List<int[]> open = new ArrayList<>();
    int band;

    public Floorplan(Placer p, SplittableRandom rng, int w, int d, int y, int rows, Site s, double lit) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.y = y;
        this.rows = rows;
        this.lit = lit;
        g = new byte[w * d];
        keep0 = new boolean[w * d];
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                g[z * w + x] = s.core(x, z) ? CORE : s.keep(x, z) ? WALK : s.open(x, z) ? OPEN : SHELL;
                keep0[z * w + x] = g[z * w + x] == WALK;
            }
        }
        dist = new int[w * d];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int i = 0; i < g.length; i++) {
            dist[i] = g[i] == SHELL ? 0 : Integer.MAX_VALUE;
            if (g[i] == SHELL) {
                q.add(i);
            }
        }
        // cells on the grid's edge count as against the outside too
        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % w, z = i / w;
            for (int[] n : N4) {
                int xx = x + n[0], zz = z + n[1];
                if (xx < 0 || zz < 0 || xx >= w || zz >= d) {
                    continue;
                }
                int j = zz * w + xx;
                if (g[j] != SHELL && g[j] != CORE && dist[j] == Integer.MAX_VALUE) {
                    dist[j] = dist[i] + 1;
                    q.add(j);
                }
            }
        }
        for (int i = 0; i < g.length; i++) {
            if (dist[i] == Integer.MAX_VALUE) {
                dist[i] = 0;
            }
        }
    }

    static final int[][] N4 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    boolean in(int x, int z) {
        return x >= 0 && z >= 0 && x < w && z < d;
    }

    byte at(int x, int z) {
        return in(x, z) ? g[z * w + x] : SHELL;
    }

    void put(int x, int z, byte v) {
        if (in(x, z)) {
            g[z * w + x] = v;
        }
    }

    /** A cell anyone can stand in. */
    static boolean walkable(byte v) {
        return v == OPEN || v == WALK || v == ROOM || v == CLEAR || v == DOOR;
    }

    int deepest() {
        int m = 0;
        for (int i = 0; i < g.length; i++) {
            if (g[i] == OPEN) {
                m = Math.max(m, dist[i]);
            }
        }
        return m;
    }

    // ------------------------------------------------------------------ the corridor

    /**
     * The corridor: a ring two wide, band + 1 .. band + 2 in from the windows (as far in as the
     * floor allows), tied to the landing in front of the core by the shortest ways across. When
     * the floor is too shallow for a ring the way runs along the core instead.
     */
    void corridor(int want, boolean ring) {
        int deep = deepest();
        band = Math.max(2, Math.min(want, deep - 4));
        if (ring && deep >= band + 3) {
            for (int i = 0; i < g.length; i++) {
                if (g[i] == OPEN && (dist[i] == band + 1 || dist[i] == band + 2)) {
                    g[i] = WALK;
                }
            }
        }
        spokes();
        connect();
    }

    /**
     * Straight ways out from the landing in front of the core: from the middle two cells of its
     * outer edge, on out the way the core's doors face, until they meet the ring or a wall.
     */
    void spokes() {
        for (int s = 0; s < 4; s++) {
            // the landing's cells whose next cell outward (away from the core) is no longer landing
            List<int[]> edge = new ArrayList<>();
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (at(x, z) != WALK || !landing(x, z)) {
                        continue;
                    }
                    int[] back = step(x, z, s + 2);
                    boolean fromCore = false;
                    for (int t = 0, bx = x, bz = z; t < 3; t++) {
                        bx -= DIRS[s][0];
                        bz -= DIRS[s][1];
                        if (at(bx, bz) == CORE) {
                            fromCore = true;
                            break;
                        }
                    }
                    int[] out = step(x, z, s);
                    if (fromCore && at(out[0], out[1]) == OPEN && back != null) {
                        edge.add(new int[] {x, z});
                    }
                }
            }
            if (edge.size() < 2) {
                continue;
            }
            int m = edge.size() / 2;
            for (int[] c : List.of(edge.get(m - 1), edge.get(m))) {
                int x = c[0] + DIRS[s][0], z = c[1] + DIRS[s][1];
                while (at(x, z) == OPEN) {
                    put(x, z, WALK);
                    x += DIRS[s][0];
                    z += DIRS[s][1];
                }
            }
        }
    }

    /** The landing the host keeps clear in front of the core (as it came in). */
    private boolean landing(int x, int z) {
        return in(x, z) && keep0[z * w + x];
    }

    /** Joins every piece of corridor to the one at the core, by the shortest way over open floor. */
    void connect() {
        int root = -1;
        for (int i = 0; i < g.length && root < 0; i++) {
            if (g[i] == WALK && touchesCore(i)) {
                root = i;
            }
        }
        if (root < 0) {
            for (int i = 0; i < g.length && root < 0; i++) {
                if (g[i] == WALK) {
                    root = i;
                }
            }
        }
        if (root < 0) {
            return;
        }
        for (int guard = 0; guard < 64; guard++) {
            boolean[] reach = flood(root, v -> v == WALK);
            // the nearest piece of corridor not yet reached, over open floor
            int[] prev = new int[g.length];
            java.util.Arrays.fill(prev, -2);
            ArrayDeque<Integer> q = new ArrayDeque<>();
            for (int i = 0; i < g.length; i++) {
                if (reach[i]) {
                    prev[i] = -1;
                    q.add(i);
                }
            }
            int hit = -1;
            while (!q.isEmpty() && hit < 0) {
                int i = q.poll();
                int x = i % w, z = i / w;
                for (int[] n : N4) {
                    int xx = x + n[0], zz = z + n[1];
                    if (!in(xx, zz)) {
                        continue;
                    }
                    int j = zz * w + xx;
                    if (prev[j] != -2) {
                        continue;
                    }
                    if (g[j] == WALK) {
                        prev[j] = i;
                        hit = j;
                        break;
                    }
                    if (g[j] == OPEN) {
                        prev[j] = i;
                        q.add(j);
                    }
                }
            }
            if (hit < 0) {
                break;
            }
            // lay the way two wide: each cell and its neighbour across the way it runs
            for (int i = prev[hit]; i >= 0 && !reach[i]; i = prev[i]) {
                g[i] = WALK;
                int pi = prev[i];
                if (pi >= 0) {
                    int dx = i % w - pi % w, dz = i / w - pi / w;
                    int sx = i % w + dz, sz = i / w + dx;
                    if (at(sx, sz) == OPEN) {
                        put(sx, sz, WALK);
                    }
                }
            }
        }
        // corridor that still can't be reached is just floor
        boolean[] reach = flood(root, v -> v == WALK);
        for (int i = 0; i < g.length; i++) {
            if (g[i] == WALK && !reach[i]) {
                g[i] = OPEN;
            }
        }
    }

    boolean touchesCore(int i) {
        int x = i % w, z = i / w;
        for (int[] n : N4) {
            if (at(x + n[0], z + n[1]) == CORE) {
                return true;
            }
        }
        return false;
    }

    interface CellTest {
        boolean ok(byte v);
    }

    boolean[] flood(int start, CellTest t) {
        boolean[] seen = new boolean[g.length];
        if (start < 0) {
            return seen;
        }
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(start);
        seen[start] = true;
        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % w, z = i / w;
            for (int[] n : N4) {
                int xx = x + n[0], zz = z + n[1];
                if (!in(xx, zz)) {
                    continue;
                }
                int j = zz * w + xx;
                if (!seen[j] && t.ok(g[j])) {
                    seen[j] = true;
                    q.add(j);
                }
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ rooms

    /** A room: its rectangle (walls on its edges where it meets other floor), its door, what it is. */
    public final class Room {
        public final int x0, z0, x1, z1;
        public final String kind;
        /** The door in the wall, and the cell inside it kept clear; -1 until it has one. */
        int dx = -1, dz = -1, fx, fz;
        /** Which way the door faces into the room: 0 north (-z), 1 east, 2 south, 3 west. */
        int dside;
        /** Whether a side runs along the building's outside wall: 0 north, 1 east, 2 south, 3 west. */
        final boolean[] outside = new boolean[4];
        boolean glass;
        String wall;

        Room(int x0, int z0, int x1, int z1, String kind) {
            this.x0 = x0;
            this.z0 = z0;
            this.x1 = x1;
            this.z1 = z1;
            this.kind = kind;
        }

        public int w() {
            return x1 - x0 + 1;
        }

        public int d() {
            return z1 - z0 + 1;
        }

        boolean has(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        /** Inside the room and not wall. */
        boolean inside(int x, int z) {
            return has(x, z) && (at(x, z) == ROOM || at(x, z) == ITEM || at(x, z) == CLEAR);
        }

        int area() {
            int n = 0;
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    n += inside(x, z) ? 1 : 0;
                }
            }
            return n;
        }
    }

    /**
     * Takes a rectangle of open floor as a room: walls on its edge cells wherever beyond them is
     * floor that isn't already some room's wall. Returns null if too little would be left inside.
     */
    Room room(int x0, int z0, int x1, int z1, String kind, String wall) {
        Room r = new Room(x0, z0, x1, z1, kind);
        r.wall = wall;
        List<int[]> walls = new ArrayList<>();
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                boolean edge = false;
                for (int[] n : N4) {
                    int xx = x + n[0], zz = z + n[1];
                    if (r.has(xx, zz)) {
                        continue;
                    }
                    byte v = at(xx, zz);
                    if (v != SHELL && v != CORE && v != WALL) {
                        edge = true;
                    }
                }
                if (edge) {
                    walls.add(new int[] {x, z});
                }
            }
        }
        int inner = (x1 - x0 + 1) * (z1 - z0 + 1) - walls.size();
        if (inner < 4) {
            return null;
        }
        for (int z = z0; z <= z1; z++) {
            for (int x = x0; x <= x1; x++) {
                put(x, z, ROOM);
            }
        }
        for (int[] c : walls) {
            put(c[0], c[1], WALL);
        }
        // the sides along the outside: every cell beyond the side is shell
        r.outside[0] = line(x0, z0 - 1, x1, z0 - 1, SHELL);
        r.outside[2] = line(x0, z1 + 1, x1, z1 + 1, SHELL);
        r.outside[3] = line(x0 - 1, z0, x0 - 1, z1, SHELL);
        r.outside[1] = line(x1 + 1, z0, x1 + 1, z1, SHELL);
        rooms.add(r);
        return r;
    }

    private boolean line(int x0, int z0, int x1, int z1, byte v) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (at(x, z) != v) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The door: a wall cell between the room's inside and corridor (or open floor, with
     * openFloor), as near the middle of its side as it can be. Prefers the corridor. False
     * when there's no way in at all.
     */
    boolean door(Room r, boolean openFloor) {
        int best = -1, bestScore = Integer.MAX_VALUE;
        int[] pick = null;
        for (int z = r.z0; z <= r.z1; z++) {
            for (int x = r.x0; x <= r.x1; x++) {
                if (at(x, z) != WALL) {
                    continue;
                }
                for (int s = 0; s < 4; s++) {
                    // s: the way into the room from the door
                    int[] in = DIRS[s];
                    int ix = x + in[0], iz = z + in[1], ox = x - in[0], oz = z - in[1];
                    if (!r.has(ix, iz) || at(ix, iz) != ROOM) {
                        continue;
                    }
                    byte o = at(ox, oz);
                    boolean ok = o == WALK || openFloor && (o == OPEN || o == CLEAR) || intoRooms && (o == ROOM || o == CLEAR);
                    if (!ok || r.has(ox, oz) || !intoRooms && inOtherRoom(r, ox, oz)) {
                        continue;
                    }
                    // not hard in a corner: walls either side of the door along the wall
                    int ax = in[1], az = in[0];
                    if (at(x + ax, z + az) != WALL || at(x - ax, z - az) != WALL) {
                        continue;
                    }
                    int mid = s % 2 == 0 ? Math.abs(2 * x - r.x0 - r.x1) : Math.abs(2 * z - r.z0 - r.z1);
                    int score = mid + (o == WALK ? 0 : 6) + rng.nextInt(3);
                    if (score < bestScore) {
                        bestScore = score;
                        pick = new int[] {x, z, s, ix, iz};
                    }
                }
            }
        }
        if (pick == null) {
            return false;
        }
        r.dx = pick[0];
        r.dz = pick[1];
        r.dside = pick[2];
        r.fx = pick[3];
        r.fz = pick[4];
        put(r.dx, r.dz, DOOR);
        put(r.fx, r.fz, CLEAR);
        return true;
    }

    /** While set, door() may open into a neighbouring room (the last way out of a room boxed in). */
    private boolean intoRooms;

    /** north, east, south, west as steps. */
    static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    static final String[] FACING = {"north", "east", "south", "west"};

    /** Gives a room back to the open floor (no way in). */
    void unroom(Room r) {
        for (int z = r.z0; z <= r.z1; z++) {
            for (int x = r.x0; x <= r.x1; x++) {
                byte v = at(x, z);
                if (v == ROOM || v == WALL || v == CLEAR || v == DOOR) {
                    put(x, z, OPEN);
                }
            }
        }
        rooms.remove(r);
    }

    /** The biggest rectangle of cells passing t, at least minW by minD (either way round), or null. */
    int[] biggest(CellTest t, int minW, int minD, int maxW, int maxD) {
        int[] hgt = new int[w];
        int[] best = null;
        int bestArea = 0;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                hgt[x] = t.ok(g[z * w + x]) ? hgt[x] + 1 : 0;
            }
            for (int x = 0; x < w; x++) {
                int h = Integer.MAX_VALUE;
                for (int x2 = x; x2 < w && hgt[x2] > 0 && x2 - x < maxW; x2++) {
                    h = Math.min(h, hgt[x2]);
                    int hh = Math.min(h, maxD);
                    int wd = x2 - x + 1;
                    int a = wd * hh;
                    boolean fits = wd >= minW && hh >= minD || wd >= minD && hh >= minW;
                    if (fits && a > bestArea) {
                        bestArea = a;
                        best = new int[] {x, z - hh + 1, x2, z};
                    }
                }
            }
        }
        return best;
    }

    /**
     * Cuts a strip of floor into rooms side by side along its long way, each about size wide
     * (shared walls between them), naming each with kinds.next(). The rooms that get no way in
     * go back to open floor.
     */
    List<Room> cut(int[] b, int minSize, int maxSize, Kinds kinds, String wall, boolean openFloor) {
        List<Room> out = new ArrayList<>();
        boolean alongX = b[2] - b[0] >= b[3] - b[1];
        int a0 = alongX ? b[0] : b[1], a1 = alongX ? b[2] : b[3];
        int at = a0;
        while (at < a1) {
            String kind = kinds.next(this);
            int size = kinds.size(kind, minSize, maxSize, rng);
            int next = at + size;
            if (a1 - next < minSize) {
                next = a1;
            }
            Room r = alongX ? room(at, b[1], next, b[3], kind, wall) : room(b[0], at, b[2], next, kind, wall);
            if (r != null) {
                if (door(r, openFloor)) {
                    out.add(r);
                } else {
                    unroom(r);
                }
            }
            at = next;
        }
        return out;
    }

    /** What the rooms of a zone are, in order, and how wide. */
    interface Kinds {
        String next(Floorplan f);

        default int size(String kind, int min, int max, SplittableRandom rng) {
            return min + rng.nextInt(Math.max(1, max - min + 1));
        }
    }

    /** The cells in the window band (within band of the outside walls) still open. */
    boolean bandCell(int i) {
        return g[i] == OPEN && dist[i] <= band;
    }

    boolean innerCell(int i) {
        return g[i] == OPEN && dist[i] > band;
    }

    /** Fills a zone with rooms, biggest strip first, until no strip of at least min x 3 is left. */
    List<Room> rooms(boolean inner, int depthMax, int minSize, int maxSize, Kinds kinds, String wall, boolean openFloor, int maxRooms) {
        List<Room> out = new ArrayList<>();
        for (int guard = 0; guard < 40 && out.size() < maxRooms; guard++) {
            int[] b = biggestZone(inner, Math.max(3, minSize), depthMax);
            if (b == null) {
                break;
            }
            List<Room> got = cut(b, minSize, maxSize, kinds, wall, openFloor);
            if (got.isEmpty()) {
                // nothing would take: leave the strip as floor but stop trying it
                for (int z = b[1]; z <= b[3]; z++) {
                    for (int x = b[0]; x <= b[2]; x++) {
                        if (at(x, z) == OPEN) {
                            put(x, z, CLEAR);
                        }
                    }
                }
                continue;
            }
            out.addAll(got);
        }
        // strips that were tried and left go back to floor
        for (int i = 0; i < g.length; i++) {
            if (g[i] == CLEAR && !inSomeRoom(i)) {
                g[i] = OPEN;
            }
        }
        return out;
    }

    private boolean inSomeRoom(int i) {
        int x = i % w, z = i / w;
        for (Room r : rooms) {
            if (r.has(x, z)) {
                return true;
            }
        }
        return false;
    }

    private int[] biggestZone(boolean inner, int min, int depthMax) {
        int[] hgt = new int[w];
        int[] best = null;
        int bestArea = 0;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int i = z * w + x;
                hgt[x] = (inner ? innerCell(i) : bandCell(i)) ? hgt[x] + 1 : 0;
            }
            for (int x = 0; x < w; x++) {
                int h = Integer.MAX_VALUE;
                for (int x2 = x; x2 < w && hgt[x2] > 0; x2++) {
                    h = Math.min(h, hgt[x2]);
                    int wd = x2 - x + 1;
                    // a strip: its short side no deeper than the zone allows
                    int hh = h, ww = wd;
                    if (Math.min(hh, ww) > depthMax) {
                        if (hh <= ww) {
                            hh = depthMax;
                        } else {
                            ww = depthMax;
                        }
                    }
                    if (Math.min(hh, ww) < 3 || Math.max(hh, ww) < min) {
                        continue;
                    }
                    int a = hh * ww;
                    if (a > bestArea) {
                        bestArea = a;
                        best = new int[] {x, z - hh + 1, x + ww - 1, z};
                    }
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ setting things

    /** The block standing on the floor at a cell (rows above the slab: 1 is floor level). */
    void set(int x, int row, int z, String block) {
        p.set(x, y + row, z, block);
    }

    /**
     * Sets a piece of furniture on the floor if that leaves every other open cell of the area
     * (the room, or the floor round it) still reachable; marks it taken. front: the cell in front
     * of it must stay clear too (a desk's chair, a bed's side), or null.
     */
    boolean place(int x, int z, String block, int[] front) {
        byte v = at(x, z);
        if (v != ROOM && v != OPEN) {
            return false;
        }
        if (front != null && at(front[0], front[1]) != ROOM && at(front[0], front[1]) != OPEN && at(front[0], front[1]) != CLEAR
                && at(front[0], front[1]) != WALK) {
            return false;
        }
        put(x, z, ITEM);
        if (!stillConnected(x, z)) {
            put(x, z, v);
            return false;
        }
        set(x, 1, z, block);
        if (front != null && (at(front[0], front[1]) == ROOM || at(front[0], front[1]) == OPEN)) {
            put(front[0], front[1], CLEAR);
        }
        return true;
    }

    /** Every walkable neighbour of a newly taken cell still joins up with the others round it. */
    private boolean stillConnected(int x, int z) {
        List<Integer> ns = new ArrayList<>();
        for (int[] n : N4) {
            int xx = x + n[0], zz = z + n[1];
            if (in(xx, zz) && walkable(g[zz * w + xx])) {
                ns.add(zz * w + xx);
            }
        }
        if (ns.size() <= 1) {
            return true;
        }
        // locally: the neighbours meet again within a small box round the cell
        boolean[] seen = new boolean[g.length];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        q.add(ns.get(0));
        seen[ns.get(0)] = true;
        int found = 1, budget = 4000;
        while (!q.isEmpty() && found < ns.size() && budget-- > 0) {
            int i = q.poll();
            int cx = i % w, cz = i / w;
            for (int[] n : N4) {
                int xx = cx + n[0], zz = cz + n[1];
                if (!in(xx, zz)) {
                    continue;
                }
                int j = zz * w + xx;
                if (!seen[j] && walkable(g[j])) {
                    seen[j] = true;
                    if (ns.contains(j)) {
                        found++;
                    }
                    q.add(j);
                }
            }
        }
        return found >= ns.size();
    }

    /** Cells of a room's inside against a wall, with the way they face out from it (0 n, 1 e, 2 s, 3 w). */
    List<int[]> alongWalls(Room r) {
        List<int[]> out = new ArrayList<>();
        for (int z = r.z0; z <= r.z1; z++) {
            for (int x = r.x0; x <= r.x1; x++) {
                if (at(x, z) != ROOM) {
                    continue;
                }
                for (int s = 0; s < 4; s++) {
                    int[] o = DIRS[s];
                    byte b = at(x - o[0], z - o[1]);
                    if (b == WALL || b == SHELL || b == CORE) {
                        out.add(new int[] {x, z, s});
                    }
                }
            }
        }
        return out;
    }

    static String face(int s) {
        return FACING[s & 3];
    }

    static int[] step(int x, int z, int s) {
        return new int[] {x + DIRS[s & 3][0], z + DIRS[s & 3][1]};
    }

    /** Whether the window glass is in the outside wall just beyond a cell, the way s points. */
    boolean window(int x, int z, int s) {
        String b = p.get(x + DIRS[s & 3][0], y + 2, z + DIRS[s & 3][1]);
        return b != null && (b.startsWith("window") || b.startsWith("boarded_window") || b.contains("glass"));
    }

    /** Walls of the rooms, the doors in them, and their floors. */
    /** Whether a cell is inside a room other than r. */
    private boolean inOtherRoom(Room r, int x, int z) {
        for (Room o : rooms) {
            if (o != r && o.has(x, z) && at(x, z) != WALL && at(x, z) != DOOR) {
                return true;
            }
        }
        return false;
    }

    /**
     * Doors made before the rooms round them: one that came to open into a later room is moved
     * to the corridor or open floor if the room has any other way out; if not it stays as a door
     * between the two rooms, the cell past it kept clear.
     */
    private void fixDoors() {
        for (Room r : rooms) {
            int ox = 2 * r.dx - r.fx, oz = 2 * r.dz - r.fz;
            byte o = at(ox, oz);
            if (!inOtherRoom(r, ox, oz) && o != WALL && o != CORE && o != SHELL) {
                continue;
            }
            put(r.dx, r.dz, WALL);
            put(r.fx, r.fz, ROOM);
            int dx = r.dx, dz = r.dz, ds = r.dside, fx = r.fx, fz = r.fz;
            boolean moved = door(r, false) || door(r, true);
            if (!moved) {
                intoRooms = true;
                moved = door(r, true);
                intoRooms = false;
                if (moved) {
                    int px = 2 * r.dx - r.fx, pz = 2 * r.dz - r.fz;
                    if (at(px, pz) == ROOM) {
                        put(px, pz, CLEAR);
                    }
                }
            }
            if (!moved) {
                r.dx = dx;
                r.dz = dz;
                r.dside = ds;
                r.fx = fx;
                r.fz = fz;
                put(dx, dz, DOOR);
                put(fx, fz, CLEAR);
                if (at(ox, oz) == ROOM) {
                    put(ox, oz, CLEAR);
                } else if (at(ox, oz) == WALL) {
                    // a door into the back of a wall: nothing for it but to give the room up
                    put(dx, dz, WALL);
                    put(fx, fz, ROOM);
                    r.dx = -1;
                }
            }
        }
    }

    /**
     * Doors through walls so nothing is boxed in: from the landing, every room and every piece of
     * open floor must be reachable; where some isn't, the first wall cell between it and the
     * reachable part (not at a corner) becomes a door. Returns {x, z, side into the new part}.
     */
    private List<int[]> joinUp() {
        List<int[]> made = new ArrayList<>();
        int root = -1;
        for (int i = 0; i < g.length && root < 0; i++) {
            if (keep0[i] && walkable(g[i])) {
                root = i;
            }
        }
        if (root < 0) {
            return made;
        }
        for (int guard = 0; guard < 40; guard++) {
            boolean[] reach = flood(root, Floorplan::walkable);
            int[] best = null;
            for (int i = 0; i < g.length && best == null; i++) {
                if (g[i] != WALL) {
                    continue;
                }
                int x = i % w, z = i / w;
                for (int s = 0; s < 4 && best == null; s++) {
                    int ax = x - DIRS[s][0], az = z - DIRS[s][1], bx = x + DIRS[s][0], bz = z + DIRS[s][1];
                    if (!in(ax, az) || !in(bx, bz)) {
                        continue;
                    }
                    int a = az * w + ax, b = bz * w + bx;
                    if (reach[a] && !reach[b] && walkable(g[b]) && g[a] != DOOR && g[b] != DOOR) {
                        // a straight wall either side along it, so the door has jambs
                        int px = DIRS[(s + 1) % 4][0], pz = DIRS[(s + 1) % 4][1];
                        if (at(x + px, z + pz) == WALL && at(x - px, z - pz) == WALL) {
                            best = new int[] {x, z, s};
                        }
                    }
                }
            }
            if (best == null) {
                break;
            }
            put(best[0], best[1], DOOR);
            int[] fr = step(best[0], best[1], best[2]), bk = step(best[0], best[1], best[2] + 2);
            for (int[] c : new int[][] {fr, bk}) {
                if (at(c[0], c[1]) == ROOM || at(c[0], c[1]) == OPEN) {
                    put(c[0], c[1], CLEAR);
                }
            }
            made.add(best);
        }
        return made;
    }

    /** A wall or a door in one: what a glass wall runs on from. */
    private boolean walled(int x, int z) {
        byte v = at(x, z);
        return v == WALL || v == DOOR;
    }

    void buildRooms() {
        fixDoors();
        List<int[]> extra = joinUp();
        for (Room r : rooms) {
            String floor = Floors.floorOf(r.kind, rng);
            for (int z = r.z0; z <= r.z1; z++) {
                for (int x = r.x0; x <= r.x1; x++) {
                    byte v = at(x, z);
                    if (v == WALL) {
                        // glass rooms: a wall of glass between drywall posts
                        boolean ex = walled(x - 1, z) || walled(x + 1, z), ez = walled(x, z - 1) || walled(x, z + 1);
                        boolean alongX = ex;
                        boolean post = (alongX ? x : z) % 3 == 0 || isCorner(x, z) || ex == ez;
                        for (int k = 1; k <= rows; k++) {
                            String b = r.glass && !post && k < rows ? Placer.inWall("window", alongX) : r.wall;
                            set(x, k, z, b);
                        }
                    } else if (v != DOOR && floor != null) {
                        p.set(x, y, z, floor);
                    }
                }
            }
            if (r.dx >= 0) {
                for (int k = 3; k <= rows; k++) {
                    set(r.dx, k, r.dz, r.wall);
                }
                set(r.dx, 1, r.dz, "air");
                set(r.dx, 2, r.dz, "air");
                if (floor != null) {
                    p.set(r.dx, y, r.dz, floor);
                }
                Doors.abandoned(p, rng, r.dx, y + 1, r.dz, Floors.doorOf(r.kind), face(r.dside), Doors.hinge(rng));
            }
        }
        for (int[] e : extra) {
            set(e[0], 1, e[1], "air");
            set(e[0], 2, e[1], "air");
            Doors.abandoned(p, rng, e[0], y + 1, e[1], Doors.FLAT, face(e[2]), Doors.hinge(rng));
        }
    }

    private boolean isCorner(int x, int z) {
        boolean ax = at(x - 1, z) == WALL || at(x + 1, z) == WALL;
        boolean az = at(x, z - 1) == WALL || at(x, z + 1) == WALL;
        return ax && az;
    }

    /** The open floor's cells (not corridor), for a floor type's open-plan fill. */
    List<int[]> openCells() {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < g.length; i++) {
            if (g[i] == OPEN) {
                out.add(new int[] {i % w, i / w});
            }
        }
        return out;
    }

    /** Lays a floor block over every corridor and open cell (rooms lay their own). */
    void floors(String corridor, String openFloor) {
        for (int i = 0; i < g.length; i++) {
            int x = i % w, z = i / w;
            if (g[i] == WALK && corridor != null) {
                p.set(x, y, z, corridor);
            } else if ((g[i] == OPEN || g[i] == ITEM && !inSomeRoom(i)) && openFloor != null) {
                p.set(x, y, z, openFloor);
            }
        }
    }
}
