package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * One tile of Grayhaven International ({@link Airport}): everything of the airport that falls in
 * it, drawn in the airport's frame through a placer turned to the world and clipped to the tile,
 * every choice made from a hash of where it is so the next tile draws the same. The af_ kit
 * (tools/city/airfield.py) supplies the ground, the paint, the lights and the signs:
 * <ul>
 * <li>the runway in grooved asphalt, its shoulders, the blast pads at the ends with their yellow
 * chevrons; the threshold's piano keys, the numbers (9 at the west end, 27 at the east), the
 * aiming points, the touchdown zone's bars, the centreline's dashes, the side stripes, the tyre
 * rubber down the touchdown zones and the tar snakes over the cracks;</li>
 * <li>the parallel taxiway and its connectors with their yellow centrelines, edge lines and the
 * hold-short bars across each connector;</li>
 * <li>the apron's concrete in five-metre slabs;</li>
 * <li>the edge lights down the runway and the blue ones along the taxiways (a few knocked off
 * their stems), the green threshold bars, the PAPIs, the approach lights out past the west end,
 * the windsocks, the hold and location signs at every connector and the distance-remaining signs;</li>
 * <li>the long grass between, gone to seed, and the perimeter fence with its wire.</li>
 * </ul>
 */
final class AirportSite extends Site {
    private final int rx, rz;
    /** The airport's frame, turned to the world and clipped to this tile. */
    private final Placer a;
    /** This tile's extent in the frame. */
    private final int ax0, ax1, az0, az1;
    private final long seed;

    AirportSite(Place p, BlockSink out) {
        super(p, out);
        int[] o = Airport.origin(p.variant);
        rx = o[0];
        rz = o[1];
        BlockSink clip = new BlockSink() {
            @Override
            public void set(int x, int y, int z, String block) {
                if (inside(x, z)) {
                    out.set(x, y, z, block);
                }
            }

            @Override
            public String get(int x, int y, int z) {
                return inside(x, z) ? out.get(x, y, z) : null;
            }

            @Override
            public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
                if (inside((int) Math.floor(x), (int) Math.floor(z))) {
                    out.vehicle(x, y, z, yaw, models, seed);
                }
            }
        };
        a = new Placer(clip, rx - p.x0, 0, rz - p.z0, Airport.ROT);
        // the world's (x, z) is the frame's (rx + ax, rz + az)
        ax0 = p.x0 - rx;
        ax1 = p.x1 - rx;
        az0 = p.z0 - rz;
        az1 = p.z1 - rz;
        seed = Noise.hash(0xA1270L, rx, rz);
    }

    private double rnd(int ax, int az, long salt) {
        return Noise.unit(Noise.hash(seed ^ salt, ax, az));
    }

    @Override
    void build() {
        for (int ax = ax0; ax <= ax1; ax++) {
            for (int az = az0; az <= az1; az++) {
                String g = surface(ax, az);
                a.set(ax, 0, az, g);
                if (g.equals("minecraft:grass_block")) {
                    double r = rnd(ax, az, 0x6A55);
                    if (r < 0.06) {
                        a.set(ax, 1, az, r < 0.012 ? "weeds_tall" : r < 0.035 ? "grass_tuft" : "weeds");
                    }
                }
            }
        }
        runwayLights();
        taxiwayLights();
        approach();
        signs();
        fence();
        if (ax1 >= AirportLandside.BX0 && ax0 <= AirportLandside.BX1 && az1 >= AirportLandside.BZ0 && az0 <= AirportLandside.BZ1) {
            copy(piece("landside", 0x1A2DL, (q, r) -> new AirportLandside(q, r).build()));
        }
        if (ax1 >= AirportTerminal.BX0 && ax0 <= AirportTerminal.BX1 && az1 >= AirportTerminal.BZ0 && az0 <= AirportTerminal.BZ1) {
            copy(terminal());
        }
        if (ax1 >= AirportGates.BX0 && ax0 <= AirportGates.BX1 && az1 >= AirportGates.BZ0 && az0 <= AirportGates.BZ1) {
            copy(piece("gates", 0x6A7E5L, (q, r) -> new AirportGates(q, r).build()));
        }
        if (ax1 >= AirportGrounds.BX0 && ax0 <= AirportGrounds.BX1 && az1 >= AirportGrounds.BZ0 && az0 <= AirportGrounds.BZ1) {
            copy(piece("grounds", 0x6A0D5L, (q, r) -> new AirportGrounds(q, r).build()));
        }
        if (ax1 >= AirportFire.BX0 && ax0 <= AirportFire.BX1 && az1 >= AirportFire.BZ0 && az0 <= AirportFire.BZ1) {
            copy(piece("fire", 0xF12EL, (q, r) -> new AirportFire(q, r).build()));
        }
        if (ax1 >= AirportHangars.BX0 && ax0 <= AirportHangars.BX1 && az1 >= AirportHangars.BZ0 && az0 <= AirportHangars.BZ1) {
            copy(piece("hangars", 0x4A6EL, (q, r) -> new AirportHangars(q, r).build()));
        }
        if (ax1 >= AirportCargo.BX0 && ax0 <= AirportCargo.BX1 && az1 >= AirportCargo.BZ0 && az0 <= AirportCargo.BZ1) {
            copy(piece("cargo", 0xCA60L, (q, r) -> new AirportCargo(q, r).build()));
        }
    }

    // ------------------------------------------------------------------ the big pieces

    /**
     * The pieces too big for one tile (the terminal, the tower and the buildings round it) are built whole, once, in the frame, then
     * each tile copies its part: their builders draw their choices one after another, so a tile
     * building only its own part would draw different ones from its neighbour.
     */
    private static final Map<String, Voxels> PIECES = new LinkedHashMap<>(4, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Voxels> eldest) {
            return size() > 8;
        }
    };

    private Voxels terminal() {
        String key = "terminal@" + rx + "," + rz;
        synchronized (PIECES) {
            Voxels v = PIECES.get(key);
            if (v == null) {
                v = new Voxels();
                new AirportTerminal(new Placer(v, 0, 0, 0, 0), new SplittableRandom(seed ^ 0x7E2A1L)).build();
                PIECES.put(key, v);
            }
            return v;
        }
    }

    /** A piece built whole in the frame once (by build, from its own seed), kept while tiles round it are made. */
    private Voxels piece(String name, long salt, java.util.function.BiConsumer<Placer, SplittableRandom> build) {
        String key = name + "@" + rx + "," + rz;
        synchronized (PIECES) {
            Voxels v = PIECES.get(key);
            if (v == null) {
                v = new Voxels();
                build.accept(new Placer(v, 0, 0, 0, 0), new SplittableRandom(seed ^ salt));
                PIECES.put(key, v);
            }
            return v;
        }
    }

    /** Copies the part of a piece built in the frame that falls in this tile. */
    private void copy(Voxels v) {
        List<String> palette = v.palette();
        v.forEach((x, y, z, id) -> {
            if (x >= ax0 && x <= ax1 && z >= az0 && z <= az1) {
                a.set(x, y, z, palette.get(id));
            }
        });
        for (Voxels.Car c : v.cars) {
            if (c.models() == null) {
                a.car(c.x(), c.y(), c.z(), c.yaw(), c.minLength(), c.maxLength(), c.seed());
            } else {
                a.vehicle(c.x(), c.y(), c.z(), c.yaw(), c.models(), c.seed());
            }
        }
        for (Voxels.Mark m : v.marks) {
            if (m.x() < ax0 || m.x() > ax1 || m.z() < az0 || m.z() > az1) {
                continue;
            }
            a.mark(m.what(), m.x(), m.y(), m.z());
        }
    }

    // ------------------------------------------------------------------ the ground

    private String surface(int ax, int az) {
        int aaz = Math.abs(az);
        // the runway's cells are ax = -HALF_LEN..HALF_LEN - 1, the pads 60 past each end
        boolean on = ax >= -Airport.HALF_LEN && ax < Airport.HALF_LEN, pad = ax >= -Airport.HALF_LEN - 60 && ax < Airport.HALF_LEN + 60;
        if (on && aaz <= Airport.HALF_W) {
            return runway(ax, az);
        }
        if (pad && aaz <= Airport.HALF_W) {
            return blastPad(ax, az);
        }
        if (pad && aaz <= Airport.SHOULDER) {
            return rnd(ax, az, 0x5401) < 0.04 ? "af_rwy_tar" : "af_twy";
        }
        String t = taxiway(ax, az);
        if (t != null) {
            return t;
        }
        if (ax >= Airport.APRON_X0 && ax <= Airport.APRON_X1 && az >= Airport.APRON_Z0
                && az < Airport.TWY_Z - Airport.TWY_SHOULDER) {
            return apron(ax, az);
        }
        if (perimeterRoad(ax, az)) {
            return rnd(ax, az, 0x9A7E) < 0.3 ? "minecraft:coarse_dirt" : "minecraft:gravel";
        }
        return "minecraft:grass_block";
    }

    /** Distance along the runway from the threshold the landing aircraft crosses, at ax. */
    private static int fromThreshold(int ax) {
        return ax < 0 ? ax + Airport.HALF_LEN : Airport.HALF_LEN - 1 - ax;
    }

    private String runway(int ax, int az) {
        int u = fromThreshold(ax), aaz = Math.abs(az);
        boolean touchdown = u >= 140 && u < 700 && aaz <= 9;
        boolean paint = aaz == Airport.HALF_W
                || u >= 6 && u < 51 && aaz >= 4 && aaz <= 20 && (aaz - 4) % 3 != 2
                || number(ax, az, u)
                || u >= 305 && u < 355 && aaz >= 6 && aaz <= 14
                || tdz(u, aaz)
                || az == 0 && aax(ax) < Airport.HALF_LEN - 90 && Math.floorMod(ax + Airport.HALF_LEN, 61) < 37;
        if (paint) {
            return touchdown ? "af_paint_rubber[axis=x]" : "af_paint";
        }
        if (touchdown && rnd(ax, az, 0x7B8E) < 0.85 - aaz * 0.05) {
            return "af_rwy_rubber[axis=x]";
        }
        return rnd(ax, az, 0x7A85) < 0.025 ? "af_rwy_tar" : "af_rwy";
    }

    private static int aax(int ax) {
        return Math.abs(ax);
    }

    /** The touchdown zone's bars: three a side 150 m in, two at 450 and 600, one at 750 and 900. */
    private static boolean tdz(int u, int aaz) {
        int[][] sets = {{152, 3}, {457, 2}, {610, 2}, {762, 1}, {915, 1}};
        for (int[] s : sets) {
            if (u >= s[0] && u < s[0] + 23 && aaz >= 6 && aaz < 6 + 3 * s[1] && (aaz - 6) % 3 != 2) {
                return true;
            }
        }
        return false;
    }

    /** 3x5 digits, rows top first. */
    private static final String[][] FONT = {
            {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
            {"###", "..#", ".##", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"}, {"###", "..#", "..#", ".#.", ".#."}, {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"}};

    /**
     * The runway's number painted past the piano keys, 20 long and each digit 6 wide, the foot of
     * the figures toward the threshold, read left to right by the pilot coming in: 9 at the west
     * end (ax negative), 27 at the east.
     */
    private static boolean number(int ax, int az, int u) {
        int up = u - 63;
        if (up < 0 || up >= 20) {
            return false;
        }
        String num = ax < 0 ? "9" : "27";
        int width = num.length() * 9 - 3;
        int left = (ax < 0 ? az : -az) + width / 2;  // from the pilot's left: north of the frame coming in from the west, south from the east
        if (left < 0 || left >= width || left % 9 >= 6) {
            return false;
        }
        int col = left % 9 / 2, row = 4 - up / 4;
        return FONT[num.charAt(left / 9) - '0'][row].charAt(col) == '#';
    }

    /**
     * The blast pad past each end: shoulder asphalt with yellow chevrons pointing in at the
     * runway, a band every 20 m, its slanted edges cut through the blocks in halves.
     */
    private String blastPad(int ax, int az) {
        // the frame's x away from the threshold, and z from the centreline, both continuous at the block's corners
        double best = 1e9, worst = -1e9;
        int bx = 0, bz = 0, wx = 0, wz = 0;
        for (int cx = 0; cx <= 1; cx++) {
            for (int cz = 0; cz <= 1; cz++) {
                double x = ax + cx, z = az + cz - 0.5;
                double out = ax < 0 ? -Airport.HALF_LEN - x : x - Airport.HALF_LEN;
                double f = out + Math.abs(z) - 0.5;  // whole at the corners off the centreline
                if (f < best) {
                    best = f;
                    bx = cx;
                    bz = cz;
                }
                if (f > worst) {
                    worst = f;
                    wx = cx;
                    wz = cz;
                }
            }
        }
        double m = best;
        double band = Math.floor(m / 20) * 20;
        if (Math.abs(az) == Airport.HALF_W) {
            return "af_paint_y";
        }
        if (band > 0 && Math.abs(m - band) < 1e-6 && worst - best <= 2.0001) {
            return "af_paint_y_half[facing=" + corner(bx, bz) + "]";
        }
        if (Math.abs(m + 1 - (band + 20)) < 1e-6) {
            return "af_paint_y_half[facing=" + corner(wx, wz) + "]";
        }
        return "af_twy";
    }

    /** The facing that turns af_paint_y_half's painted triangle to the block's corner (cx, cz). */
    private static String corner(int cx, int cz) {
        return cx == 0 ? (cz == 0 ? "north" : "west") : (cz == 0 ? "east" : "south");
    }

    /** The parallel taxiway and its connectors down to the runway; null off them. */
    private String taxiway(int ax, int az) {
        int lim = Airport.CONNECTORS[Airport.CONNECTORS.length - 1];
        int d = az - Airport.TWY_Z;
        boolean onA = ax >= -lim - Airport.TWY_HALF && ax <= lim + Airport.TWY_HALF && Math.abs(d) <= Airport.TWY_SHOULDER;
        for (int c : Airport.CONNECTORS) {
            int e = ax - c;
            if (Math.abs(e) > Airport.TWY_SHOULDER || az < Airport.TWY_Z - Airport.TWY_SHOULDER || az > -Airport.SHOULDER - 1) {
                continue;
            }
            boolean pave = Math.abs(e) <= Airport.TWY_HALF;
            boolean inA = Math.abs(d) <= Airport.TWY_HALF;
            if (!pave) {
                if (inA) {
                    break;  // the taxiway's own pavement here
                }
                return Math.abs(d) <= Airport.TWY_SHOULDER ? "af_rwy" : "af_rwy";
            }
            if (az == Airport.HOLD_Z) {
                return "af_hold[facing=north]";
            }
            if (e == 0 && az > Airport.TWY_Z) {
                return "af_twy_cl[axis=z]";
            }
            if (Math.abs(e) == Airport.TWY_HALF && az > Airport.TWY_Z + Airport.TWY_HALF) {
                return "af_twy_edge[facing=" + (e < 0 ? "north" : "south") + "]";
            }
            if (inA) {
                break;
            }
            return "af_twy";
        }
        if (!onA) {
            return null;
        }
        if (Math.abs(d) > Airport.TWY_HALF) {
            return "af_rwy";
        }
        if (d == 0) {
            return "af_twy_cl[axis=x]";
        }
        if (Math.abs(d) == Airport.TWY_HALF && !openTo(ax, d)) {
            return "af_twy_edge[facing=" + (d < 0 ? "east" : "west") + "]";
        }
        return "af_twy";
    }

    /** True where the taxiway's edge opens onto a connector (on its runway side) or the apron (on the other). */
    private static boolean openTo(int ax, int d) {
        if (d > 0) {
            for (int c : Airport.CONNECTORS) {
                if (Math.abs(ax - c) <= Airport.TWY_HALF) {
                    return true;
                }
            }
            return false;
        }
        return ax >= Airport.APRON_X0 && ax <= Airport.APRON_X1;
    }

    /** The apron's concrete, its joints every five metres. */
    private static String apron(int ax, int az) {
        boolean jx = Math.floorMod(ax, 5) == 0, jz = Math.floorMod(az, 5) == 0;
        return jx && jz ? "af_apron_jj[facing=north]" : jx ? "af_apron_j[facing=north]" : jz ? "af_apron_j[facing=east]" : "af_apron";
    }

    /** The perimeter road just inside the fence, four wide. */
    private static boolean perimeterRoad(int ax, int az) {
        int in = Math.min(Math.min(ax - Airport.X0, Airport.X1 - ax), Math.min(az - Airport.LAND_Z0, Airport.Z1 - az));
        return in >= 4 && in < 8;
    }

    // ------------------------------------------------------------------ the lights and signs

    private void light(int ax, int az, String block) {
        double r = rnd(ax, az, 0x11E7);
        if (r < 0.05) {
            a.set(ax, 1, az, "af_light_broken[facing=" + FACINGS[(int) (r * 80) & 3] + "]");
        } else if (r > 0.02) {
            a.set(ax, 1, az, block);
        }
    }

    private void runwayLights() {
        for (int ax = -Airport.HALF_LEN; ax <= Airport.HALF_LEN; ax += 60) {
            for (int s = -1; s <= 1; s += 2) {
                light(Math.min(ax, Airport.HALF_LEN - 1), s * (Airport.HALF_W + 2), "af_light_edge[facing=north]");
            }
        }
        // the threshold wing bars, green out to the approach, red back over the runway. They stand
        // on stems, so they run out either side of the paving and leave the runway ends clear for
        // jets rolling out long
        for (int w = 2; w <= 11; w += 3) {
            for (int s = -1; s <= 1; s += 2) {
                int az = s * (Airport.HALF_W + w);
                light(-Airport.HALF_LEN - 1, az, "af_light_thr[facing=west]");
                light(Airport.HALF_LEN, az, "af_light_thr[facing=east]");
            }
        }
        // the PAPIs, four boxes on the pilot's left 300 m in
        for (int k = 0; k < 4; k++) {
            a.set(-Airport.HALF_LEN + 300, 1, -(Airport.HALF_W + 15) - k * 9, "af_papi[facing=west]");
            a.set(Airport.HALF_LEN - 300, 1, Airport.HALF_W + 15 + k * 9, "af_papi[facing=east]");
        }
        // the windsocks by each end
        for (int s = -1; s <= 1; s += 2) {
            int ax = s * (Airport.HALF_LEN - 150), az = -(Airport.HALF_W + 60);
            a.set(ax, 1, az, "af_windsock_mast");
            a.set(ax, 2, az, "af_windsock_mast");
            a.set(ax, 3, az, "af_windsock[facing=" + FACINGS[(int) (rnd(ax, az, 0x50C) * 4)] + "]");
        }
    }

    private void taxiwayLights() {
        int lim = Airport.CONNECTORS[Airport.CONNECTORS.length - 1];
        for (int ax = -lim - Airport.TWY_HALF; ax <= lim + Airport.TWY_HALF; ax += 30) {
            for (int s = -1; s <= 1; s += 2) {
                int d = s * (Airport.TWY_HALF + 1);
                if (!openTo(ax, s)) {
                    light(ax, Airport.TWY_Z + d, "af_light_twy[facing=north]");
                }
            }
        }
        for (int c : Airport.CONNECTORS) {
            for (int az = Airport.TWY_Z + Airport.TWY_HALF + 6; az < -Airport.SHOULDER; az += 15) {
                light(c - Airport.TWY_HALF - 1, az, "af_light_twy[facing=north]");
                light(c + Airport.TWY_HALF + 1, az, "af_light_twy[facing=north]");
            }
        }
    }

    /** The approach lights out past runway 9's end: a bar every 30 m on its mast, the crossbar 150 m out. */
    private void approach() {
        for (int k = 2; k * 30 <= -Airport.X0 - Airport.HALF_LEN - 12; k++) {
            int ax = -Airport.HALF_LEN - k * 30;
            int half = k == 5 ? 7 : 1;
            for (int az = -half; az <= half; az++) {
                if (az == 0) {
                    a.set(ax, 1, az, "af_als_mast");
                    a.set(ax, 2, az, "af_als_bar[facing=west]");
                } else {
                    a.set(ax, 1, az, "af_als_bar[facing=west]");
                }
            }
        }
    }

    private void signs() {
        for (int i = 0; i < Airport.CONNECTORS.length; i++) {
            int c = Airport.CONNECTORS[i];
            // on the pilot's left coming down to the hold, read from the left: the location, then the runway's ends
            int x = c + Airport.TWY_HALF + 6;
            String[] tiles = {"af_sign_loc_" + Airport.CONNECTOR_NAMES[i], "af_sign_hold_0", "af_sign_hold_1"};
            for (int k = 0; k < tiles.length; k++) {
                a.set(x + 2 - k, 1, Airport.HOLD_Z, tiles[k] + "[facing=north]");
            }
            // and the back of the hold for the aircraft leaving the runway: the sign that says it's clear
            a.set(c - Airport.TWY_HALF - 4, 1, Airport.HOLD_Z, "af_sign_loc_" + Airport.CONNECTOR_NAMES[i] + "[facing=south]");
        }
        // the distance remaining, in thousands of feet, on the left of the landing roll each way
        for (int k = 1; k <= 6; k++) {
            int d = k * 305;
            a.set(Airport.HALF_LEN - d, 1, -(Airport.HALF_W + 12), "af_sign_dist_" + k + "[facing=west]");
            a.set(-Airport.HALF_LEN + d, 1, Airport.HALF_W + 12, "af_sign_dist_" + k + "[facing=east]");
        }
    }

    /** The perimeter fence: chain link on posts with barbed wire along the top, round the whole airport. */
    private void fence() {
        for (int ax = Airport.X0 + 1; ax < Airport.X1; ax++) {
            fencePost(ax, Airport.LAND_Z0 + 1, true);
            fencePost(ax, Airport.Z1 - 1, true);
        }
        for (int az = Airport.LAND_Z0 + 1; az < Airport.Z1; az++) {
            // the gap for the access road through the west fence
            if (az < AirportLandside.ACC_Z0 - 1 || az > AirportLandside.ACC_Z1 + 1) {
                fencePost(Airport.X0 + 1, az, false);
            }
            fencePost(Airport.X1 - 1, az, false);
        }
    }

    private void fencePost(int ax, int az, boolean alongX) {
        boolean post = Math.floorMod(alongX ? ax : az, 3) == 0;
        boolean down = rnd(ax, az, 0xFE7C) < 0.015;
        if (down) {
            a.set(ax, 1, az, "weeds_tall");
            return;
        }
        String run = "[axis=" + (alongX ? "x" : "z") + "]";
        for (int y = 1; y <= 2; y++) {
            a.set(ax, y, az, post ? "fence_post" : "chainlink" + run);
        }
        a.set(ax, 3, az, post ? "fence_post" : "barbed_wire" + run);
    }
}
