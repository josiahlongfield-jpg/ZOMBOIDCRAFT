package com.zomboidcraft.world.city;

import com.zomboidcraft.world.county.Flats;
import com.zomboidcraft.world.lifts.LiftBuild;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.zip.GZIPInputStream;

/**
 * A downtown building from the buildings pack Josiah bought: twenty real city buildings (a
 * skyscraper with its mast, glass and stone office towers, white apartment slabs, painted
 * corner blocks with shops under them), each cut block by block from the pack's model by
 * tools/towers/towers.py into data/zomboidcraft/packtowers.bin: its walls in the pack's own
 * plaster, tile and stone, its windows, roller shutters and shop windows showing the pack's
 * photos of window displays, its roofs, doors, the spot lamps over its shops, and which cells
 * are inside, where its floors go and where its stairs climb.
 *
 * Here the shell is laid and fitted out: shops along the street behind the shop windows, a
 * lobby where it's an office block, open-plan office floors or flats off a corridor upstairs,
 * a stairwell (with a working lift beside it in anything over 10 floors) from the street to a hut on the roof. A few
 * floors still have power from a generator somewhere and glow.
 *
 * Same conventions as {@link Tower}: front along local z = 0 facing north, a storey 4 blocks.
 */
public final class PackTower {
    /** One building: cells[(y * d + z) * w + x] is 0 outside, 1 inside, n >= 2 palette entry n - 2. */
    public record Template(String name, String kind, int w, int h, int d, int ground, int[] levels, int[][] rects,
                           int[][] cores, String[] pal, short[] cells) {
        int at(int x, int y, int z) {
            return x < 0 || y < 0 || z < 0 || x >= w || y >= h || z >= d ? 0 : cells[(y * d + z) * w + x];
        }

        /** The palette entry at a cell, or null outside and inside. */
        String entry(int x, int y, int z) {
            int v = at(x, y, z);
            return v >= 2 ? pal[v - 2] : null;
        }

        boolean inside(int x, int y, int z) {
            return at(x, y, z) == 1;
        }

        /** Floor height of the top floor anyone can reach. */
        public int height() {
            return h;
        }

        public int floors() {
            return levels.length;
        }
    }

    private static final int SINGLE = 0, DOUBLE = 1;
    private static List<Template> all;

    /** Every template in the pack's file (empty when the file is missing). */
    public static synchronized List<Template> all() {
        if (all == null) {
            all = load();
        }
        return all;
    }

    private static List<Template> load() {
        List<Template> out = new ArrayList<>();
        try (InputStream raw = PackTower.class.getResourceAsStream("/data/zomboidcraft/packtowers.bin")) {
            if (raw == null) {
                return out;
            }
            DataInputStream in = new DataInputStream(new GZIPInputStream(raw));
            int n = in.readInt();
            for (int t = 0; t < n; t++) {
                String name = in.readUTF();
                String kind = in.readUTF();
                int w = in.readShort(), h = in.readShort(), d = in.readShort(), ground = in.readShort();
                int nl = in.readShort();
                int[] levels = new int[nl];
                int[][] rects = new int[nl][];
                for (int i = 0; i < nl; i++) {
                    levels[i] = in.readShort();
                    rects[i] = new int[] {in.readShort(), in.readShort(), in.readShort(), in.readShort()};
                }
                int nc = in.readShort();
                int[][] cores = new int[nc][];
                for (int i = 0; i < nc; i++) {
                    cores[i] = new int[] {in.readShort(), in.readShort(), in.readShort(), in.readShort(), in.readShort()};
                }
                String[] pal = new String[in.readShort()];
                for (int i = 0; i < pal.length; i++) {
                    pal[i] = in.readUTF().intern();
                }
                short[] cells = new short[w * h * d];
                int runs = in.readInt(), at = 0;
                for (int i = 0; i < runs; i++) {
                    int len = in.readShort();
                    short v = in.readShort();
                    java.util.Arrays.fill(cells, at, at + len, v);
                    at += len;
                }
                out.add(new Template(name, kind, w, h, d, ground, levels, rects, cores, pal, cells));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Can't read the pack building templates", e);
        }
        return out;
    }

    /**
     * A template that fits a lot maxW along the street and maxD deep, as near height blocks tall
     * as there is (office blocks for "office", the rest for "flats"); null when none fits.
     */
    public static Template pick(SplittableRandom rng, int maxW, int maxD, int height, boolean office) {
        Template best = null;
        double bestScore = Double.MAX_VALUE;
        for (Template t : all()) {
            if (t.w > maxW || t.d > maxD || t.kind.equals("office") != office) {
                continue;
            }
            // close in height, filling the lot, with some luck in it
            double score = Math.abs(t.h - height) / (double) Math.max(height, 1) + (1 - t.w * (double) t.d / (maxW * maxD)) * 0.5
                    + rng.nextDouble() * 0.6;
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        return best;
    }

    private final Placer p;
    private final SplittableRandom rng;
    private final Template t;
    private final boolean furnished;
    private final boolean[] powered;
    /** Cells the stair cores and corridors keep clear: [level][z * w + x]. */
    private final boolean[][] kept;
    /** Per core: the first cell of the lift shaft added beside a single stair core (-1 for none; see {@link #liftBeside}). */
    private final int[] sideLift;

    public PackTower(Placer p, SplittableRandom rng, Template t, boolean furnished) {
        this.p = p;
        this.rng = rng;
        this.t = t;
        this.furnished = furnished;
        this.powered = new boolean[t.levels.length];
        for (int k = 1; k < powered.length; k++) {
            powered[k] = rng.nextDouble() < 0.12;
        }
        this.kept = new boolean[t.levels.length][t.w * t.d];
        this.sideLift = new int[t.cores.length];
        java.util.Arrays.fill(sideLift, -1);
    }

    public void build() {
        shell();
        for (int[] c : t.cores) {
            reserve(c);
        }
        for (int i = 0; i < t.cores.length; i++) {
            sideLift[i] = liftBeside(t.cores[i]);
        }
        for (int k = 0; k < t.levels.length; k++) {
            lights(k);
            if (k == 0) {
                groundFloor();
            } else if (furnished) {
                if (t.kind.equals("office")) {
                    officeFloor(k);
                } else {
                    flatsFloor(k);
                }
            }
        }
        for (int i = 0; i < t.cores.length; i++) {
            core(t.cores[i]);
            if (sideLift[i] >= 0) {
                sideShaft(t.cores[i], sideLift[i]);
            }
        }
    }

    private int level(int k) {
        return t.levels[k];
    }

    /** The rows of room on level k: up to the next slab, or the roof. */
    private int top(int k) {
        return k + 1 < t.levels.length ? level(k + 1) - 1 : level(k) + 3;
    }

    // ------------------------------------------------------------------ the shell

    private void shell() {
        for (int y = 0; y < t.h; y++) {
            int k = levelOf(y);
            for (int z = 0; z < t.d; z++) {
                for (int x = 0; x < t.w; x++) {
                    int v = t.at(x, y, z);
                    if (v == 0) {
                        continue;
                    }
                    if (v == 1) {
                        p.set(x, y, z, "air");
                        continue;
                    }
                    String e = t.pal[v - 2];
                    if (e.charAt(0) != '@') {
                        p.set(x, y, z, e.startsWith("window[") ? window(e, k, y) : e);
                        continue;
                    }
                    switch (e) {
                        case "@ground" -> p.set(x, y, z, "floor_concrete");
                        case "@slab" -> p.set(x, y, z, "office_floor");
                        case "@lamp" -> p.set(x, y, z, ShopLights.lamp(rng, 0.3));
                        case "@doortop" -> { }
                        default -> {
                            if (e.startsWith("@door:")) {
                                String[] s = e.split(":");
                                String hinge = s[3].equals("?") ? Doors.hinge(rng) : s[3];
                                Doors.abandoned(p, rng, x, y, z, s[1], s[2], hinge);
                            }
                        }
                    }
                }
            }
        }
    }

    /** Which level a height belongs to (0 for the ground floor). */
    private int levelOf(int y) {
        int k = 0;
        while (k + 1 < t.levels.length && y >= level(k + 1)) {
            k++;
        }
        return k;
    }

    /** A pane: lit on the floors with power, the ground floor's mostly smashed or boarded. */
    private String window(String e, int k, int y) {
        String axis = e.substring(e.indexOf('['));
        double r = rng.nextDouble();
        if (k == 0) {
            return (r < 0.3 ? "window_broken" : r < 0.45 ? "boarded_window" : "window") + axis;
        }
        if (powered[k] ? r < 0.75 : r < 0.03) {
            return "window_lit" + axis;
        }
        return (rng.nextDouble() < 0.08 ? "window_broken" : "window") + axis;
    }

    /** Ceiling lights on a level, a few on where the power still runs; under the slab above. */
    private void lights(int k) {
        int y = top(k);
        for (int z = 1; z < t.d - 1; z++) {
            for (int x = 1; x < t.w - 1; x++) {
                if (x % 4 == 2 && z % 4 == 2 && t.inside(x, y, z) && t.at(x, y + 1, z) != 1 && t.at(x, y + 1, z) != 0) {
                    p.set(x, y, z, powered[k] || rng.nextDouble() < 0.03 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        // now and then a room high up still glows behind its glass
        if (k > 0) {
            int fy = level(k) + 2;
            for (int z = 1; z < t.d - 1; z++) {
                for (int x = 1; x < t.w - 1; x++) {
                    if (!t.inside(x, fy, z) || !t.inside(x, fy + 1, z)) {
                        continue;
                    }
                    for (int[] dd : new int[][] {{0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
                        String e = t.entry(x + dd[0], fy, z + dd[1]);
                        if (e != null && e.startsWith("window[")) {
                            String lamp = ShopLights.window(ShopLights.spot(p, x, fy + 1, z), powered[k] ? 0.06 : 0.004);
                            if (lamp != null) {
                                p.set(x, fy + 1, z, lamp);
                            }
                            break;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ cores

    private int coreW(int[] c) {
        return c[0] == DOUBLE ? 14 : 5;
    }

    /** Keeps a core and the two rows in front of its doors clear on every level it serves. */
    private void reserve(int[] c) {
        int cw = coreW(c);
        for (int k = c[3]; k <= c[4]; k++) {
            for (int z = c[2] - 2; z < c[2] + 10; z++) {
                for (int x = c[1]; x < c[1] + cw; x++) {
                    if (x >= 0 && z >= 0 && x < t.w && z < t.d) {
                        kept[k][z * t.w + x] = true;
                    }
                }
            }
        }
    }

    private boolean free(int k, int x, int z) {
        int y = level(k);
        return x >= 0 && z >= 0 && x < t.w && z < t.d && !kept[k][z * t.w + x] && t.inside(x, y + 1, z) && t.inside(x, y + 2, z)
                && t.inside(x, y + 3, z);
    }

    /**
     * A stair core from level c[3] to c[4] (and on to the roof when it's flat over the core):
     * concrete walls, a switchback stair in each stairwell, lifts between the two of a double core.
     */
    private void core(int[] c) {
        int cx0 = c[1], cz0 = c[2], cw = coreW(c);
        int y0 = level(c[3]), yTop = level(c[4]);
        // the roof over the top floor: a hut on it for the stairs when there's room
        int roofY = yTop + 1;
        while (roofY < t.h && t.inside(cx0 + 2, roofY, cz0 + 4)) {
            roofY++;
        }
        boolean hut = c[4] == t.levels.length - 1 && (roofY - yTop) % 2 == 0 && roofY - yTop >= 4 && roofY + 4 < 320
                && clearAbove(cx0, cz0, cw, roofY);
        int last = hut ? roofY : yTop;
        int wallTop = hut ? roofY + 3 : top(c[4]);
        for (int y = y0; y <= wallTop; y++) {
            boolean ground = y < t.ground;
            for (int x = cx0; x < cx0 + cw; x++) {
                for (int z = cz0; z < cz0 + 10; z++) {
                    boolean edge = x == cx0 || x == cx0 + cw - 1 || z == cz0 || z == cz0 + 9;
                    if (c[0] == DOUBLE) {
                        int sa = cx0 + 4, lift = cx0 + 5, sb = cx0 + 9, back = cz0 + 4;
                        edge |= x == sa || x == sb || x >= lift && x <= lift + 3 && z == back || x == lift + 1 && z < back;
                    }
                    if (edge) {
                        p.set(x, y, z, y > (hut ? roofY : 1 << 20) ? "concrete_dark" : ground ? "wood_panel" : "concrete_dark");
                    } else if (y > y0 || c[3] > 0) {
                        p.set(x, y, z, "air");
                    } else {
                        p.set(x, y, z, "floor_concrete");
                    }
                }
            }
        }
        if (hut) {
            p.fill(cx0, roofY + 4, cz0, cx0 + cw - 1, roofY + 4, cz0 + 9, "roof");
        }
        List<Integer> ys = new ArrayList<>();
        for (int k = c[3]; k <= c[4]; k++) {
            ys.add(level(k));
        }
        if (hut) {
            ys.add(roofY);
        }
        if (c[0] == DOUBLE) {
            stairwell(cx0 + 1, cz0, ys);
            stairwell(cx0 + 10, cz0, ys);
            lifts(cx0 + 5, cz0, ys, c[3]);
        } else {
            stairwell(cx0 + 1, cz0, ys);
        }
    }

    /** Nothing of the building in the way of a stair hut on the roof. */
    private boolean clearAbove(int cx0, int cz0, int cw, int roofY) {
        for (int y = roofY + 1; y <= roofY + 4; y++) {
            for (int x = cx0 - 1; x <= cx0 + cw; x++) {
                for (int z = cz0 - 1; z <= cz0 + 10; z++) {
                    if (t.at(x, y, z) != 0) {
                        return false;
                    }
                }
            }
        }
        for (int x = cx0; x < cx0 + cw; x++) {
            for (int z = cz0; z < cz0 + 10; z++) {
                if (t.at(x, roofY, z) < 2) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * A 3-wide switchback stairwell, inner x0..x0+2, its door in the core's north wall at z = cz0,
     * landing by the door (cz0+1..2), flights over cz0+3..4 and the far landing (cz0+5..8).
     * Flights climb two steps at a time, so every rise must be even; where one ends at the far
     * landing, a bridge over the well leads back to the door.
     */
    private void stairwell(int x0, int cz0, List<Integer> ys) {
        int land = cz0 + 1, flight = cz0 + 3, far = cz0 + 5;
        boolean atFar = false;
        int side = 0;
        for (int i = 0; i < ys.size(); i++) {
            int y = ys.get(i);
            p.fill(x0, y, land, x0 + 2, y, land + 1, "floor_concrete");
            if (atFar) {
                p.fill(x0, y, far, x0 + 2, y, far + 3, "floor_concrete");
                p.set(x0 + 1, y, flight, "floor_concrete");
                p.set(x0 + 1, y, flight + 1, "floor_concrete");
            }
            Doors.abandoned(p, rng, x0 + 1, y + 1, cz0, Doors.STEEL, "south", Doors.hinge(rng));
            p.set(x0 + 1, y + 3, land + 1, rng.nextDouble() < 0.1 ? "ceiling_light" : "ceiling_light_off");
            if (i == ys.size() - 1) {
                break;
            }
            int next = ys.get(i + 1);
            int h = y;
            while (h < next) {
                boolean south = !atFar;
                int x = side == 0 ? x0 : x0 + 2;
                for (int s = 0; s < 2 && h < next; s++) {
                    h++;
                    int z = south ? flight + s : flight + 1 - s;
                    p.set(x, h, z, "stairs_concrete[facing=" + (south ? "south" : "north") + "]");
                    p.set(x, h + 1, z, "air");
                    p.set(x, h + 2, z, "air");
                }
                atFar = !atFar;
                if (h < next) {
                    if (atFar) {
                        p.fill(x0, h, far, x0 + 2, h, far + 3, "floor_concrete");
                    } else {
                        p.fill(x0, h, land, x0 + 2, h, land + 1, "floor_concrete");
                    }
                }
                side ^= 1;
            }
            for (int yy = y + 1; yy < next; yy++) {
                if (rng.nextDouble() < 0.9) {
                    p.set(x0 + 1, yy + 1, flight, "railing[facing=west]");
                }
            }
        }
    }

    /**
     * The lift shafts x0..x0+3 (doors in the north wall): the working lift in the two-wide one, the
     * narrow one beside it carrying the risers, a plant floor behind them on every level. first is
     * the level the core starts on (the street's is the lobby, numbered 1).
     */
    private void lifts(int x0, int cz0, List<Integer> ys, int first) {
        int[] floors = new int[ys.size()];
        for (int i = 0; i < floors.length; i++) {
            floors[i] = ys.get(i);
            p.fill(x0, floors[i], cz0 + 5, x0 + 3, floors[i], cz0 + 8, "floor_concrete");
        }
        LiftBuild.shaft(p::set, x0 + 2, cz0 + 1, 2, 3, floors, first == 0 ? 0 : -1, first, floors[floors.length - 1] + 4);
    }

    /**
     * Where a lift can go beside a single stair core in a building over 10 floors: a shaft two wide
     * and three deep with its doors beside the stair door, east of the core or else west, on every
     * level the core serves, with room in front. Reserves it and returns its first cell's x, or -1.
     */
    private int liftBeside(int[] c) {
        if (c[0] != SINGLE || t.levels.length <= 10 || c[4] - c[3] < 2) {
            return -1;
        }
        int cx0 = c[1], cz0 = c[2];
        for (int x0 : new int[] {cx0 + 5, cx0 - 2}) {
            int wall = x0 > cx0 ? x0 + 2 : x0 - 1;
            int lo = Math.min(x0, wall), hi = Math.max(x0 + 1, wall);
            boolean fits = lo >= 1 && hi < t.w - 1;
            for (int k = c[3]; k <= c[4] && fits; k++) {
                int y = level(k);
                for (int x = lo; x <= hi && fits; x++) {
                    for (int z = cz0 - 2; z <= cz0 + 4 && fits; z++) {
                        fits = z >= 1 && z < t.d - 1 && !kept[k][z * t.w + x] && t.at(x, y, z) >= 2
                                && t.inside(x, y + 1, z) && t.inside(x, y + 2, z);
                    }
                }
            }
            if (!fits) {
                continue;
            }
            for (int k = c[3]; k <= c[4]; k++) {
                for (int x = lo; x <= hi; x++) {
                    for (int z = cz0 - 2; z <= cz0 + 4; z++) {
                        kept[k][z * t.w + x] = true;
                    }
                }
            }
            return x0;
        }
        return -1;
    }

    /** The lift beside a single stair core: concrete walls round its shaft (sharing the core's), the lift in it. */
    private void sideShaft(int[] c, int x0) {
        int cz0 = c[2], wall = x0 > c[1] ? x0 + 2 : x0 - 1;
        int y0 = level(c[3]), last = level(c[4]), top = last + 4;
        for (int y = y0; y <= top; y++) {
            for (int z = cz0; z <= cz0 + 4; z++) {
                p.set(wall, y, z, "concrete_dark");
            }
            for (int x = x0; x <= x0 + 1; x++) {
                p.set(x, y, cz0, "concrete_dark");
                p.set(x, y, cz0 + 4, "concrete_dark");
            }
            for (int z = cz0; z <= cz0 + 4; z++) {
                // the core's wall it shares, carried up as high as the shaft goes
                p.set(x0 > c[1] ? x0 - 1 : x0 + 2, y, z, "concrete_dark");
            }
        }
        int[] floors = new int[c[4] - c[3] + 1];
        for (int k = c[3]; k <= c[4]; k++) {
            floors[k - c[3]] = level(k);
        }
        LiftBuild.shaft(p::set, x0, cz0 + 1, 2, 3, floors, c[3] == 0 ? 0 : -1, c[3], top);
    }

    // ------------------------------------------------------------------ the ground floor

    /**
     * Shops along the street, one behind each run of shop windows, and a hall from the main door
     * back to the stairs; an office block's lobby is marble with a desk and planters instead.
     */
    private void groundFloor() {
        int[] r = t.rects[0];
        if (r[2] < r[0]) {
            return;
        }
        int y = 0, ceil = top(0);
        boolean office = t.kind.equals("office");
        int coreZ = t.cores.length > 0 ? t.cores[0][2] : r[3] + 1;
        // the front wall and the doors in it at street level
        int frontZ = wallZ(0, r, -1);
        List<Integer> doors = new ArrayList<>();
        for (int x = r[0]; x <= r[2]; x++) {
            String e = t.entry(x, 1, frontZ);
            if (e != null && e.startsWith("@door:")) {
                doors.add(x);
            }
        }
        boolean shops = furnished && r[2] - r[0] >= 8 && (!office || doors.size() >= 3);
        // the hall: from the door nearest the core straight back to it
        int coreMid = t.cores.length > 0 ? t.cores[0][1] + coreW(t.cores[0]) / 2 : (r[0] + r[2]) / 2;
        int hallX = doors.isEmpty() ? coreMid : doors.get(0);
        for (int dx : doors) {
            if (Math.abs(dx - coreMid) < Math.abs(hallX - coreMid)) {
                hallX = dx;
            }
        }
        for (int z = 0; z < t.d; z++) {
            for (int x = 0; x < t.w; x++) {
                if (t.inside(x, 1, z)) {
                    boolean dark = ((x / 2) + (z / 2)) % 2 == 0;
                    p.set(x, y, z, office ? (dark ? "lobby_floor_dark" : "lobby_floor_white") : "floor_concrete_crack");
                }
            }
        }
        // the hall is kept clear from the doors to the stairs
        for (int z = frontZ + 1; z < coreZ; z++) {
            for (int x = hallX - 1; x <= hallX + 1; x++) {
                claim(0, x, z);
            }
        }
        if (shops) {
            // shop units either side of the 3-wide hall, as deep as the floor runs clear behind the windows
            side(r[0] - 1, hallX - 2, frontZ, doors, ceil);
            side(hallX + 2, r[2] + 1, frontZ, doors, ceil);
        }
        if (furnished && office) {
            lobby(r, hallX, coreZ, ceil);
        } else if (furnished) {
            // whatever's left behind the shops: storerooms off the hall
            leftovers(0, true);
        }
        if (furnished) {
            scatter(0, r, 6);
        }
    }

    /**
     * Shop units between walls a and b at street level: the longest stretch whose floor runs
     * clear at least 10 back from the front wall, the units as deep as its shallowest (18 at most).
     */
    private void side(int a, int b, int frontZ, List<Integer> doors, int ceil) {
        int bestA = -1, bestB = -1, run0 = -1;
        int[] clear = new int[t.w];
        for (int x = a + 1; x <= b; x++) {
            int c = 0;
            if (x < b) {
                while (c < 18 && free(0, x, frontZ + 1 + c)) {
                    c++;
                }
                clear[x] = c;
            }
            boolean ok = x < b && c >= 9;
            if (ok && run0 < 0) {
                run0 = x;
            }
            if (!ok && run0 >= 0) {
                if (x - run0 > bestB - bestA) {
                    bestA = run0;
                    bestB = x;
                }
                run0 = -1;
            }
        }
        if (bestA < 0 || bestB - bestA < 5) {
            return;
        }
        int depth = 18;
        for (int x = bestA; x < bestB; x++) {
            depth = Math.min(depth, clear[x]);
        }
        units(bestA - 1, bestB, frontZ, depth + 1, doors, ceil);
    }

    /** The wall in front of (dir -1) or behind (dir 1) a level's rect: the commonest along it. */
    private int wallZ(int k, int[] r, int dir) {
        int y = level(k) + 1;
        int[] count = new int[t.d + 2];
        for (int x = r[0]; x <= r[2]; x++) {
            int z = dir < 0 ? r[1] : r[3];
            while (z + dir >= 0 && z + dir < t.d && t.inside(x, y, z + dir)) {
                z += dir;
            }
            count[z + dir + 1]++;
        }
        int best = dir < 0 ? r[1] - 1 : r[3] + 1;
        for (int i = 0; i < count.length; i++) {
            if (count[i] > count[best + 1]) {
                best = i - 1;
            }
        }
        return best;
    }

    private void claim(int k, int x, int z) {
        if (x >= 0 && z >= 0 && x < t.w && z < t.d) {
            kept[k][z * t.w + x] = true;
        }
    }

    /** Shop units side by side from wall a to wall b (both shared), depth back from the front wall. */
    private void units(int a, int b, int frontZ, int depth, List<Integer> doors, int ceil) {
        if (b - a < 6) {
            return;
        }
        List<Integer> cuts = new ArrayList<>();
        cuts.add(a);
        int x = a;
        while (b - x > 20) {
            int next = x + 9 + rng.nextInt(6);
            // never through a door
            while (doors.contains(next) || doors.contains(next - 1) || doors.contains(next + 1)) {
                next++;
            }
            if (b - next < 8) {
                break;
            }
            cuts.add(next);
            x = next;
        }
        cuts.add(b);
        for (int i = 0; i + 1 < cuts.size(); i++) {
            int u0 = cuts.get(i), u1 = cuts.get(i + 1);
            int uw = u1 - u0 + 1;
            // the walls between units and across the back, up to the ceiling
            for (int yy = 1; yy <= ceil; yy++) {
                for (int z = frontZ + 1; z < frontZ + depth; z++) {
                    for (int wx : new int[] {u0, u1}) {
                        if (t.inside(wx, yy, z)) {
                            p.set(wx, yy, z, "drywall");
                        }
                    }
                }
                for (int xx = u0; xx <= u1; xx++) {
                    if (t.inside(xx, yy, frontZ + depth - 1)) {
                        p.set(xx, yy, frontZ + depth - 1, "drywall");
                    }
                }
            }
            if (!unitClear(u0 + 1, u1 - 1, frontZ + 1, frontZ + depth - 2)) {
                continue;
            }
            Placer q = p.shifted(u0, 0, frontZ);
            new Shops(q, rng, Shops.pick(rng), uw, depth).fitOut();
            for (int xx = u0; xx <= u1; xx++) {
                for (int z = frontZ + 1; z < frontZ + depth; z++) {
                    claim(0, xx, z);
                }
            }
            // the back door onto the hall
            Doors.abandoned(p, rng, u1 - 1, 1, frontZ + depth - 1, Doors.FLAT, "north", Doors.hinge(rng));
        }
    }

    private boolean unitClear(int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (!t.inside(x, 1, z) || !t.inside(x, 3, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A lobby: reception desk facing the doors, planters gone wild, a security desk, coloured lamps hung high. */
    private void lobby(int[] r, int hallX, int coreZ, int ceil) {
        int rz = Math.max(r[1] + 3, coreZ - 5);
        if (rz + 1 < coreZ - 2) {
            for (int x = hallX - 4; x <= hallX + 3; x++) {
                if (free(0, x, rz)) {
                    p.set(x, 1, rz, "reception_desk");
                }
            }
            if (free(0, hallX - 2, rz + 1)) {
                p.set(hallX - 2, 1, rz + 1, "office_chair[facing=north]");
            }
        }
        for (int[] c : new int[][] {{r[0] + 1, r[1] + 1}, {r[2] - 1, r[1] + 1}, {r[0] + 1, r[3] - 1}, {r[2] - 1, r[3] - 1}}) {
            if (free(0, c[0], c[1])) {
                p.set(c[0], 1, c[1], "planter");
                p.set(c[0], 2, c[1], "weeds");
            }
        }
        // lamps hung from the high ceiling on chains: purple mostly, the odd amber
        for (int x = r[0] + 3; x <= r[2] - 3; x += 6) {
            for (int z = r[1] + 3; z <= r[3] - 3; z += 6) {
                if (!free(0, x, z) || ceil < 5) {
                    continue;
                }
                if (rng.nextDouble() < 0.15) {
                    p.set(x, 1, z, "glass_shards");
                    continue;
                }
                for (int yy = ceil - 1; yy >= ceil - 2; yy--) {
                    p.set(x, yy, z, "cv_chain");
                }
                p.set(x, ceil - 3, z, ShopLights.lamp(rng, 0.35));
            }
        }
    }

    // ------------------------------------------------------------------ offices

    /** Desk pods in rows round the core, filing cabinets along a wall, a meeting room behind glass. */
    private void officeFloor(int k) {
        int[] r = t.rects[k];
        if (r[2] < r[0]) {
            return;
        }
        int fy = level(k) + 1;
        for (int z = 2; z + 1 < t.d - 2; z += 5) {
            for (int x = 2; x + 3 < t.w - 2; x += 7) {
                if (podFree(k, x, z)) {
                    pod(x, fy, z);
                }
            }
        }
        for (int z = r[1] + 1; z <= r[3] - 1; z += 2) {
            if (free(k, r[2], z) && rng.nextDouble() < 0.6) {
                p.set(r[2], fy, z, "filing_cabinet[facing=west]");
            }
        }
        scatter(k, r, 4);
    }

    private boolean podFree(int k, int x0, int z) {
        for (int x = x0 - 1; x <= x0 + 4; x++) {
            for (int dz = -1; dz <= 2; dz++) {
                if (!free(k, x, z + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Two rows of desks back to back, four wide, with chairs and end panels. */
    private void pod(int x0, int y, int z) {
        for (int x = x0; x < x0 + 4; x++) {
            p.set(x, y, z, "desk[facing=south]");
            p.set(x, y, z + 1, "desk[facing=north]");
            if (rng.nextDouble() < 0.85) {
                p.set(x, y, z - 1, chair("south"));
            }
            if (rng.nextDouble() < 0.85) {
                p.set(x, y, z + 2, chair("north"));
            }
        }
        p.set(x0 - 1, y, z, "cubicle_wall[facing=east]");
        p.set(x0 - 1, y, z + 1, "cubicle_wall[facing=east]");
        p.set(x0 + 4, y, z, "cubicle_wall[facing=east]");
        p.set(x0 + 4, y, z + 1, "cubicle_wall[facing=east]");
    }

    private String chair(String facing) {
        String[] dirs = {"north", "east", "south", "west"};
        return "office_chair[facing=" + (rng.nextDouble() < 0.3 ? dirs[rng.nextInt(4)] : facing) + "]";
    }

    private void scatter(int k, int[] r, int n) {
        int y = level(k) + 1;
        for (int i = 0; i < n; i++) {
            int x = r[0] + rng.nextInt(r[2] - r[0] + 1);
            int z = r[1] + rng.nextInt(r[3] - r[1] + 1);
            if (free(k, x, z)) {
                String there = p.get(x, y, z);
                if (there == null || there.equals("air")) {
                    p.set(x, y, z, rng.nextBoolean() ? "rubble_pile" : "moss_carpet");
                }
            }
        }
    }

    // ------------------------------------------------------------------ flats

    /**
     * Flats off a corridor that runs past the stair door: a row along the front, and on each side
     * of the core a row along the back, each planned and furnished like a home ({@link Flats}).
     */
    private void flatsFloor(int k) {
        int[] r = t.rects[k];
        if (r[2] < r[0] || t.cores.length == 0) {
            officeFloor(k);
            return;
        }
        int[] c = coreAt(k);
        if (c == null) {
            officeFloor(k);
            return;
        }
        int y = level(k);
        int cx0 = c[1], cz0 = c[2], cw = coreW(c);
        int hall0 = cz0 - 2, hall1 = cz0 - 1;
        String paper = rng.nextBoolean() ? "home_wall_cream" : rng.nextBoolean() ? "home_wall_sage" : "home_wall_stripe";
        // the corridor
        for (int x = r[0]; x <= r[2]; x++) {
            for (int z = hall0; z <= hall1; z++) {
                if (t.inside(x, y + 1, z)) {
                    p.set(x, y, z, "home_floor_oak");
                    claim(k, x, z);
                }
            }
            if (x % 5 == 2 && t.inside(x, y + 3, hall0)) {
                p.set(x, y + 3, hall0, powered[k] || rng.nextDouble() < 0.06 ? "ceiling_light" : "ceiling_light_off");
            }
        }
        double lit = powered[k] ? 0.6 : 0.03;
        // the front row: from the front wall back to the corridor's wall
        int frontWall = wallZ(k, r, -1), zb = hall0 - 1 - frontWall;
        if (zb >= 7) {
            row(k, r[0] - 1, r[2] + 1, frontWall, zb, false, paper, lit);
        }
        // the back rows either side of the core: from the back wall forward to the corridor
        int backWall = wallZ(k, r, 1), zbBack = backWall - (hall1 + 1);
        if (zbBack >= 7) {
            row(k, r[0] - 1, cx0, backWall, zbBack, true, paper, lit);
            row(k, cx0 + cw - 1, r[2] + 1, backWall, zbBack, true, paper, lit);
        }
        // wings and corners the rows missed
        leftovers(k, false);
    }

    /**
     * The floor nobody has planned yet on level k, taken a rectangle at a time (biggest first):
     * each becomes a flat (a storeroom at street level) opening onto the open floor beside it.
     */
    private void leftovers(int k, boolean store) {
        int y = level(k);
        String paper = k == 0 ? "drywall" : rng.nextBoolean() ? "home_wall_cream" : "home_wall_sage";
        double lit = powered[k] ? 0.6 : 0.03;
        for (int tries = 0; tries < 12; tries++) {
            int[] b = biggest(k);
            if (b == null) {
                return;
            }
            int x0 = b[0], z0 = b[1], x1 = b[2], z1 = b[3];
            // the way in: the side with the most open floor beyond it
            int[] open = new int[4];
            for (int x = x0; x <= x1; x++) {
                open[0] += free(k, x, z0 - 1) ? 1 : 0;
                open[2] += free(k, x, z1 + 1) ? 1 : 0;
            }
            for (int z = z0; z <= z1; z++) {
                open[3] += free(k, x0 - 1, z) ? 1 : 0;
                open[1] += free(k, x1 + 1, z) ? 1 : 0;
            }
            int side = 0;
            for (int i = 1; i < 4; i++) {
                if (open[i] > open[side]) {
                    side = i;
                }
            }
            // the room's walls: the building's own where it stands against them, else on the rect's
            // edge cells (so it never walls up a corridor, the stairs or the open floor beside it)
            int ax0 = shell(k, x0 - 1, z0, x0 - 1, z1) ? x0 - 1 : x0;
            int ax1 = shell(k, x1 + 1, z0, x1 + 1, z1) ? x1 + 1 : x1;
            int az0 = shell(k, x0, z0 - 1, x1, z0 - 1) ? z0 - 1 : z0;
            int az1 = shell(k, x0, z1 + 1, x1, z1 + 1) ? z1 + 1 : z1;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    claim(k, x, z);
                }
            }
            if (ax1 - ax0 < 6 || az1 - az0 < 6) {
                continue;
            }
            for (int r = 1; r <= 3; r++) {
                for (int x = ax0; x <= ax1; x++) {
                    for (int z = az0; z <= az1; z++) {
                        boolean edge = x == ax0 || x == ax1 || z == az0 || z == az1;
                        if (edge && t.inside(x, y + r, z)) {
                            p.set(x, y + r, z, paper);
                        }
                    }
                }
            }
            // the room drawn as a flat facing its way in: its outer wall opposite, the door in the near wall
            Placer q;
            int w, zb;
            switch (side) {
                case 0 -> { q = p.turned(ax1, 0, az1, 2); w = ax1 - ax0 + 1; zb = az1 - az0; }
                case 1 -> { q = p.turned(ax0, 0, az1, 3); w = az1 - az0 + 1; zb = ax1 - ax0; }
                case 2 -> { q = p.shifted(ax0, 0, az0); w = ax1 - ax0 + 1; zb = az1 - az0; }
                default -> { q = p.turned(ax1, 0, az0, 1); w = az1 - az0 + 1; zb = ax1 - ax0; }
            }
            int door;
            if (store) {
                door = storeroom(q, w, zb);
            } else {
                door = Flats.floor(q, rng, w, zb, y, sill(y), lit);
            }
            // the door in the near wall, in the frame's own terms
            int dx, dz;
            String facing;
            switch (side) {
                case 0 -> { dx = ax1 - door; dz = az0; facing = "south"; }
                case 1 -> { dx = ax1; dz = az1 - door; facing = "west"; }
                case 2 -> { dx = ax0 + door; dz = az1; facing = "north"; }
                default -> { dx = ax0; dz = az0 + door; facing = "east"; }
            }
            Doors.abandoned(p, rng, dx, y + 1, dz, Doors.FLAT, facing, Doors.hinge(rng));
            // the floor outside the door stays open
            claim(k, dx + (side == 1 ? 1 : side == 3 ? -1 : 0), dz + (side == 0 ? -1 : side == 2 ? 1 : 0));
        }
    }

    private static boolean empty(String block) {
        return block == null || block.equals("air");
    }

    /** Whether a line of cells on level k is all the building's own wall (nothing inside). */
    private boolean shell(int k, int x0, int z0, int x1, int z1) {
        int y = level(k) + 1;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (t.inside(x, y, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The biggest rectangle of unplanned floor on level k, if it's at least 7 by 7. */
    private int[] biggest(int k) {
        int[] hgt = new int[t.w];
        int[] best = null;
        int bestArea = 0;
        for (int z = 0; z < t.d; z++) {
            for (int x = 0; x < t.w; x++) {
                hgt[x] = free(k, x, z) ? hgt[x] + 1 : 0;
            }
            // widest run under each height, by brute force: floors are small
            for (int x = 0; x < t.w; x++) {
                int h = Integer.MAX_VALUE;
                for (int x2 = x; x2 < t.w && hgt[x2] > 0; x2++) {
                    h = Math.min(h, hgt[x2]);
                    int wdt = x2 - x + 1;
                    int area = wdt * h;
                    if (wdt >= 7 && h >= 7 && area > bestArea) {
                        bestArea = area;
                        best = new int[] {x, z - h + 1, x2, z};
                    }
                }
            }
        }
        return best;
    }

    /** A storeroom behind the shops: shelving round the walls and in rows, boxes. Returns its door's x. */
    private int storeroom(Placer q, int w, int zb) {
        int door = w / 2;
        String[] racks = {"store_shelf_empty", "cellar_shelf_cans", "cellar_shelf_preserves", "bunk_rack_boxes", "store_shelf_empty"};
        for (int x = 1; x < w - 1; x++) {
            if (rng.nextDouble() < 0.85) {
                q.set(x, 1, 1, racks[rng.nextInt(racks.length)] + "[facing=south]");
            }
        }
        // rows of racks back to back, an aisle down the middle from the door
        for (int z = 3; z + 1 < zb - 2; z += 3) {
            for (int x = 2; x < w - 2; x++) {
                if (Math.abs(x - door) <= 1 || rng.nextDouble() < 0.1) {
                    continue;
                }
                q.set(x, 1, z, racks[rng.nextInt(racks.length)] + "[facing=north]");
                if (z + 1 < zb - 2) {
                    q.set(x, 1, z + 1, racks[rng.nextInt(racks.length)] + "[facing=south]");
                }
            }
        }
        for (int z = 2; z < zb - 1; z++) {
            for (int x : new int[] {1, w - 2}) {
                if (rng.nextDouble() < 0.5 && empty(q.get(x, 1, z))) {
                    q.set(x, 1, z, rng.nextDouble() < 0.6 ? "cardboard_box" : "crate");
                    if (rng.nextDouble() < 0.4) {
                        q.set(x, 2, z, "cardboard_box");
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            int x = 2 + rng.nextInt(Math.max(1, w - 4)), z = 2 + rng.nextInt(Math.max(1, zb - 3));
            if (z != zb - 1 && empty(q.get(x, 1, z))) {
                q.set(x, 1, z, rng.nextBoolean() ? "trash_bag" : "cardboard_box");
            }
        }
        return door;
    }

    /** The core serving level k, if any. */
    private int[] coreAt(int k) {
        for (int[] c : t.cores) {
            if (c[3] <= k && k <= c[4]) {
                return c;
            }
        }
        return null;
    }

    /**
     * A row of flats between walls a and b (shared), from the outside wall at wallZ to a corridor
     * wall zb further in (behind = the row backs onto the back wall, so it's drawn turned round).
     */
    private void row(int k, int a, int b, int wallZ, int zb, boolean behind, String paper, double lit) {
        int y = level(k);
        int x = a;
        while (b - x >= 8) {
            int next = b - x <= 22 ? (b - x <= 14 ? b : x + (b - x) / 2) : x + 9 + rng.nextInt(5);
            flat(k, x, next, wallZ, zb, behind, paper, lit);
            x = next;
        }
    }

    private void flat(int k, int a, int b, int wallZ, int zb, boolean behind, String paper, double lit) {
        int y = level(k);
        int w = b - a + 1;
        // all of it must be inside the building, walls and all
        for (int xx = a + 1; xx < b; xx++) {
            for (int dz = 1; dz < zb; dz++) {
                int z = behind ? wallZ - dz : wallZ + dz;
                if (!t.inside(xx, y + 1, z) || !t.inside(xx, y + 3, z) || kept[k][z * t.w + xx]) {
                    return;
                }
            }
        }
        int corridorZ = behind ? wallZ - zb : wallZ + zb;
        for (int xx = a; xx <= b; xx++) {
            for (int dz = 0; dz <= zb; dz++) {
                claim(k, xx, behind ? wallZ - dz : wallZ + dz);
            }
        }
        // the walls between flats and onto the corridor, papered
        for (int r = 1; r <= 3; r++) {
            for (int dz = 0; dz <= zb; dz++) {
                int z = behind ? wallZ - dz : wallZ + dz;
                for (int xx : new int[] {a, b}) {
                    if (t.inside(xx, y + r, z)) {
                        p.set(xx, y + r, z, paper);
                    }
                }
            }
            for (int xx = a; xx <= b; xx++) {
                if (t.inside(xx, y + r, corridorZ)) {
                    p.set(xx, y + r, corridorZ, paper);
                }
            }
        }
        int sill = sill(y);
        Placer q = behind ? p.turned(b, 0, wallZ, 2) : p.shifted(a, 0, wallZ);
        int door = Flats.floor(q, rng, w, zb, y, sill, lit);
        int dx = behind ? b - door : a + door;
        Doors.abandoned(p, rng, dx, y + 1, corridorZ, Doors.FLAT, behind ? "south" : "north", Doors.hinge(rng));
    }

    /** The row above a slab where the windows start (for the flats' planner to find them). */
    private int sill(int y) {
        int best = 1, most = -1;
        for (int r = 1; r <= 2; r++) {
            int n = 0;
            for (int x = 0; x < t.w; x++) {
                for (int z = 0; z < t.d; z++) {
                    String e = t.entry(x, y + r, z);
                    if (e != null && e.startsWith("window[")) {
                        n++;
                    }
                }
            }
            if (n > most) {
                most = n;
                best = r;
            }
        }
        return best;
    }
}
