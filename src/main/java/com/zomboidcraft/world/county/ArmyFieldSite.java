package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import java.util.SplittableRandom;

/**
 * One tile of Caldwell Army Airfield ({@link ArmyField}): everything of the field that falls in it,
 * drawn in the field's frame through a placer clipped to the tile, every choice from a hash of where
 * it is so the next tile draws the same. The af_ kit gives the paint, the lights and the signs:
 * <ul>
 * <li>the runway in jointed concrete slabs, its white edge stripes, the threshold bars, the numbers
 * (9 at the west end, 27 at the east), the aiming points and the centreline's dashes; the
 * overruns past the ends in asphalt with their yellow chevrons, the shoulders;</li>
 * <li>the parallel taxiway and its three connectors, the centrelines, the hold bars and signs;</li>
 * <li>the ramp's concrete; the edge lights, the taxiway's blue ones, the threshold wing bars, the
 * windsocks;</li>
 * <li>the perimeter road inside the fence, the fence with its wire, and at each place the tank trail
 * meets the field a gate: the guard booth, the barrier raised, the gravel spur in to the road.</li>
 * </ul>
 * {@link ArmyFieldBase}, built once in the frame and copied, holds everything standing on the ramp.
 */
final class ArmyFieldSite extends FrameSite {
    ArmyFieldSite(Place p, BlockSink out) {
        super(p, out, 0xA2F1EL);
    }

    @Override
    void build() {
        for (int ax = fx0; ax <= fx1; ax++) {
            for (int az = fz0; az <= fz1; az++) {
                String g = surface(ax, az);
                a.set(ax, 0, az, g);
                if (g.equals("minecraft:grass_block")) {
                    double r = rnd(ax, az, 0x6A55);
                    if (r < 0.07) {
                        a.set(ax, 1, az, r < 0.015 ? "weeds_tall" : r < 0.04 ? "grass_tuft" : "weeds");
                    }
                }
            }
        }
        lights();
        signs();
        fence();
        gates();
        if (overlaps(ArmyFieldBase.BX0, ArmyFieldBase.BZ0, ArmyFieldBase.BX1, ArmyFieldBase.BZ1)) {
            copy(piece("base", 0xBA5EL, (q, r) -> new ArmyFieldBase(q, r).build()));
        }
    }

    // ------------------------------------------------------------------ the ground

    private String surface(int ax, int az) {
        int aaz = Math.abs(az), L = ArmyField.HALF_LEN;
        boolean on = ax >= -L && ax < L, over = ax >= -L - ArmyField.OVERRUN && ax < L + ArmyField.OVERRUN;
        if (on && aaz <= ArmyField.HALF_W) {
            return runway(ax, az);
        }
        if (over && aaz <= ArmyField.HALF_W) {
            // the overrun: asphalt, its yellow edge, the chevrons pointing in at the runway every 12
            int out = ax < 0 ? -L - 1 - ax : ax - L;
            if (aaz == ArmyField.HALF_W || Math.floorMod(out - aaz, 12) == 0) {
                return "af_paint_y";
            }
            return "af_twy";
        }
        if (over && aaz <= ArmyField.SHOULDER) {
            return rnd(ax, az, 0x5401) < 0.05 ? "af_rwy_tar" : "af_twy";
        }
        String t = taxiway(ax, az);
        if (t != null) {
            return t;
        }
        if (ax >= ArmyField.RAMP_X0 && ax <= ArmyField.RAMP_X1 && az >= ArmyField.RAMP_Z0 && az <= ArmyField.RAMP_Z1) {
            return slab(ax, az);
        }
        if (perimeter(ax, az) || spur(ax, az)) {
            return rnd(ax, az, 0x9A7E) < 0.3 ? "minecraft:coarse_dirt" : "minecraft:gravel";
        }
        return "minecraft:grass_block";
    }

    /** Concrete in five-metre slabs, the joints between. */
    private static String slab(int ax, int az) {
        boolean jx = Math.floorMod(ax, 5) == 0, jz = Math.floorMod(az, 5) == 0;
        return jx && jz ? "af_apron_jj[facing=north]" : jx ? "af_apron_j[facing=north]" : jz ? "af_apron_j[facing=east]" : "af_apron";
    }

    /** How far in from each threshold the arresting gear's cable crosses the runway. */
    private static final int GEAR = 460;

    private String runway(int ax, int az) {
        int L = ArmyField.HALF_LEN, u = ax < 0 ? ax + L : L - 1 - ax, aaz = Math.abs(az);
        boolean paint = aaz == ArmyField.HALF_W
                || u >= 6 && u < 36 && aaz >= 3 && aaz <= 13 && (aaz - 3) % 3 != 2
                || number(ax, az, u)
                || u >= 250 && u < 290 && aaz >= 5 && aaz <= 9
                || az == 0 && u >= 70 && Math.floorMod(ax + L, 50) < 30;
        if (paint) {
            return "af_paint";
        }
        // the arresting gear's cable across the runway near each end, the yellow discs on the
        // pavement marking where it lies
        if (u == GEAR && aaz < ArmyField.HALF_W) {
            return "af_rwy_tar";
        }
        if (u == GEAR + 1 && aaz % 5 == 2) {
            return "af_paint_y";
        }
        // the touchdown zones gone black with tyre rubber, the cracks tarred over between the slabs
        if (u >= 120 && u < 400 && aaz <= 7 && rnd(ax, az, 0x7B8E) < 0.6 - aaz * 0.06) {
            return "af_rwy_rubber[axis=x]";
        }
        return rnd(ax, az, 0x7A85) < 0.02 ? "af_rwy_tar" : slab(ax, az);
    }

    private static final String[][] FONT = {
            {"###", "#.#", "#.#", "#.#", "###"}, {".#.", "##.", ".#.", ".#.", "###"}, {"###", "..#", "###", "#..", "###"},
            {"###", "..#", ".##", "..#", "###"}, {"#.#", "#.#", "###", "..#", "..#"}, {"###", "#..", "###", "..#", "###"},
            {"###", "#..", "###", "#.#", "###"}, {"###", "..#", "..#", ".#.", ".#."}, {"###", "#.#", "###", "#.#", "###"},
            {"###", "#.#", "###", "..#", "###"}};

    /** The runway's number past the threshold bars, 15 long, the foot of the figures toward the threshold. */
    private static boolean number(int ax, int az, int u) {
        int up = u - 44;
        if (up < 0 || up >= 15) {
            return false;
        }
        String num = ax < 0 ? "9" : "27";
        int width = num.length() * 7 - 2;
        int left = (ax < 0 ? az : -az) + width / 2;
        if (left < 0 || left >= width || left % 7 >= 5) {
            return false;
        }
        int col = Math.min(2, left % 7 * 3 / 5), row = 4 - up / 3;
        return FONT[num.charAt(left / 7) - '0'][row].charAt(col) == '#';
    }

    /** The parallel taxiway and its connectors down to the runway; null off them. */
    private String taxiway(int ax, int az) {
        int lim = ArmyField.CONNECTORS[ArmyField.CONNECTORS.length - 1], d = az - ArmyField.TWY_Z;
        boolean onA = ax >= -lim - ArmyField.TWY_HALF && ax <= lim + ArmyField.TWY_HALF && Math.abs(d) <= ArmyField.TWY_SHOULDER;
        for (int c : ArmyField.CONNECTORS) {
            int e = ax - c;
            if (Math.abs(e) > ArmyField.TWY_SHOULDER || az < ArmyField.TWY_Z - ArmyField.TWY_SHOULDER || az > -ArmyField.SHOULDER - 1) {
                continue;
            }
            boolean inA = Math.abs(d) <= ArmyField.TWY_HALF;
            if (Math.abs(e) > ArmyField.TWY_HALF) {
                if (inA) {
                    break;
                }
                return "af_rwy";
            }
            if (az == ArmyField.HOLD_Z) {
                return "af_hold[facing=north]";
            }
            if (e == 0 && az > ArmyField.TWY_Z) {
                return "af_twy_cl[axis=z]";
            }
            if (Math.abs(e) == ArmyField.TWY_HALF && az > ArmyField.TWY_Z + ArmyField.TWY_HALF) {
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
        if (Math.abs(d) > ArmyField.TWY_HALF) {
            return "af_rwy";
        }
        if (d == 0) {
            return "af_twy_cl[axis=x]";
        }
        if (Math.abs(d) == ArmyField.TWY_HALF && !openTo(ax, d)) {
            return "af_twy_edge[facing=" + (d < 0 ? "east" : "west") + "]";
        }
        return "af_twy";
    }

    private static boolean openTo(int ax, int d) {
        if (d > 0) {
            for (int c : ArmyField.CONNECTORS) {
                if (Math.abs(ax - c) <= ArmyField.TWY_HALF) {
                    return true;
                }
            }
            return false;
        }
        return ax >= ArmyField.RAMP_X0 && ax <= ArmyField.RAMP_X1;
    }

    /** How far in from the fence (the fence on 1). */
    private static int in(int ax, int az) {
        return Math.min(Math.min(ax - ArmyField.X0, ArmyField.X1 - ax), Math.min(az - ArmyField.LAND_Z0, ArmyField.Z1 - az));
    }

    /** The perimeter road, four wide, round inside the fence; it stops short of the runway's overruns, the road crossing on the grass past them. */
    private static boolean perimeter(int ax, int az) {
        int i = in(ax, az);
        return i >= ArmyField.PERIM - 2 && i <= ArmyField.PERIM + 1;
    }

    /** The gravel in from each gate to the perimeter road. */
    private boolean spur(int ax, int az) {
        for (int[] g : gates) {
            boolean alongZ = (g[2] & 1) == 0;
            int across = alongZ ? ax - g[0] : az - g[1], along = alongZ ? Math.abs(az - g[1]) : Math.abs(ax - g[0]);
            if (Math.abs(across) <= 2 && along <= ArmyField.PERIM + 1) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the lights, the signs

    private void light(int ax, int az, String block) {
        double r = rnd(ax, az, 0x11E7);
        if (r < 0.06) {
            a.set(ax, 1, az, "af_light_broken[facing=" + FACINGS[(int) (r * 66) & 3] + "]");
        } else {
            a.set(ax, 1, az, block);
        }
    }

    private void lights() {
        int L = ArmyField.HALF_LEN, W = ArmyField.HALF_W;
        for (int ax = -L; ax <= L; ax += 60) {
            for (int s = -1; s <= 1; s += 2) {
                light(Math.min(ax, L - 1), s * (W + 2), "af_light_edge[facing=north]");
            }
        }
        // the threshold wing bars out either side of the paving, clear of a jet rolling long
        for (int w = 2; w <= 8; w += 3) {
            for (int s = -1; s <= 1; s += 2) {
                light(-L - 1, s * (W + w), "af_light_thr[facing=west]");
                light(L, s * (W + w), "af_light_thr[facing=east]");
            }
        }
        int lim = ArmyField.CONNECTORS[ArmyField.CONNECTORS.length - 1];
        for (int ax = -lim - ArmyField.TWY_HALF; ax <= lim + ArmyField.TWY_HALF; ax += 30) {
            for (int s = -1; s <= 1; s += 2) {
                if (!openTo(ax, s)) {
                    light(ax, ArmyField.TWY_Z + s * (ArmyField.TWY_HALF + 1), "af_light_twy[facing=north]");
                }
            }
        }
        // the arresting gear's housings either side of the runway at each cable: a low concrete
        // pit cover with its hatch, the tape reel drum, the yellow disc marker on its post
        for (int end = -1; end <= 1; end += 2) {
            int gx = end < 0 ? -L + GEAR : L - 1 - GEAR;
            for (int s = -1; s <= 1; s += 2) {
                int z0 = s * (W + 6);
                for (int dx = -1; dx <= 2; dx++) {
                    for (int k = 0; k < 3; k++) {
                        a.set(gx + dx, 1, z0 + s * k, "concrete_panel");
                    }
                }
                a.set(gx, 2, z0 + s, "pw_reel[axis=x]");
                a.set(gx + 2, 2, z0 + s, "bd_hatch[facing=" + (s < 0 ? "north" : "south") + "]");
                a.set(gx + 3, 1, s * (W + 4), "fence_post");
                a.set(gx + 3, 2, s * (W + 4), "af_light_thr[facing=" + (s < 0 ? "north" : "south") + "]");
                for (int z = W + 1; z <= W + 5; z++) {
                    a.set(gx, 0, s * z, "af_rwy_tar");
                }
            }
        }
        for (int s = -1; s <= 1; s += 2) {
            int ax = s * (L - 120), az = W + 40;
            a.set(ax, 1, az, "af_windsock_mast");
            a.set(ax, 2, az, "af_windsock_mast");
            a.set(ax, 3, az, "af_windsock[facing=" + FACINGS[(int) (rnd(ax, az, 0x50C) * 4)] + "]");
        }
    }

    private void signs() {
        String[] names = {"a1", "a2", "a3"};
        for (int i = 0; i < ArmyField.CONNECTORS.length; i++) {
            int c = ArmyField.CONNECTORS[i], x = c + ArmyField.TWY_HALF + 5;
            String[] tiles = {"af_sign_loc_" + names[i], "af_sign_hold_0", "af_sign_hold_1"};
            for (int k = 0; k < tiles.length; k++) {
                a.set(x + 2 - k, 1, ArmyField.HOLD_Z, tiles[k] + "[facing=north]");
            }
        }
    }

    // ------------------------------------------------------------------ the fence and the gates

    private boolean gap(int ax, int az) {
        for (int[] g : gates) {
            boolean alongZ = (g[2] & 1) == 0;
            if (alongZ ? Math.abs(ax - g[0]) <= 4 && Math.abs(az - g[1]) <= 2 : Math.abs(az - g[1]) <= 4 && Math.abs(ax - g[0]) <= 2) {
                return true;
            }
        }
        return false;
    }

    private void fence() {
        for (int ax = ArmyField.X0 + 1; ax < ArmyField.X1; ax++) {
            for (int az : new int[] {ArmyField.LAND_Z0 + 1, ArmyField.Z1 - 1}) {
                if (!gap(ax, az)) {
                    fencePost(ax, az, true);
                }
            }
        }
        for (int az = ArmyField.LAND_Z0 + 1; az < ArmyField.Z1; az++) {
            for (int ax : new int[] {ArmyField.X0 + 1, ArmyField.X1 - 1}) {
                if (!gap(ax, az)) {
                    fencePost(ax, az, false);
                }
            }
        }
    }

    /** Chain link on its posts, the barbed wire strung along the top, the odd panel down in the grass. */
    private void fencePost(int ax, int az, boolean alongX) {
        boolean post = Math.floorMod(alongX ? ax : az, 3) == 0;
        if (rnd(ax, az, 0xFE7C) < 0.015) {
            a.set(ax, 1, az, "weeds_tall");
            return;
        }
        String run = "[axis=" + (alongX ? "x" : "z") + "]";
        for (int y = 1; y <= 2; y++) {
            a.set(ax, y, az, post ? "fence_post" : "chainlink" + run);
        }
        a.set(ax, 3, az, post ? "fence_post" : "barbed_wire" + run);
    }

    /** At each gate: the guard booth to one side inside the wire, the barrier standing raised, the sign. */
    private void gates() {
        for (int[] g : gates) {
            SplittableRandom r = new SplittableRandom(Noise.hash(seed ^ 0x6A7EL, g[0], g[1]));
            Camp camp = new Camp(a, r, 0.4);
            boolean alongZ = (g[2] & 1) == 0;
            int inX = g[2] == 1 ? -1 : g[2] == 3 ? 1 : 0, inZ = g[2] == 0 ? 1 : g[2] == 2 ? -1 : 0;
            // the booth on the gate's right going in, back from the wire
            int bx = g[0] + inX * 4 + (alongZ ? 4 : 0), bz = g[1] + inZ * 4 + (alongZ ? 0 : 4);
            camp.booth(bx - 1, bz - 1);
            if (alongZ) {
                camp.boom(g[0] - 4, g[1] + inZ * 2, 8, r.nextDouble() < 0.4);
            } else {
                for (int y = 1; y <= 4; y++) {
                    a.set(g[0] + inX * 2, y, g[1] - 4, y == 4 ? "cp_boom_cap[facing=north]" : y == 1 ? "cp_boom_post[facing=north]" : "cp_boom_up[facing=north]");
                }
            }
            for (int k = -1; k <= 1; k += 2) {
                int jx = alongZ ? g[0] + k * 6 : g[0] - inX * 2, jz = alongZ ? g[1] - inZ * 2 : g[1] + k * 6;
                a.set(jx, 1, jz, "jersey_barrier[facing=" + (alongZ ? "north" : "east") + "]");
            }
        }
    }
}
