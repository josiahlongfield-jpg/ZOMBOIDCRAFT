package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;

/**
 * Caldwell Army Airfield's plan, a block a metre (Josiah, 2026-10-30: the northwest military zone,
 * the airfield first): one concrete runway along x, 1,200 long and 31 wide, its parallel taxiway to
 * the north with a connector at each end and one in the middle; north of that the ramp with the
 * jets in their earth-filled revetments, the helicopters behind their low cribs, the round-roofed
 * hangars, the lattice tower, the ops building, the fuel bladders and the crash station; the
 * perimeter road and the fence round it all. Coordinates are the field's own: ax along the runway
 * from its middle, az from its centreline, the ramp's side negative.
 *
 * <p>The frame is the world's (x = RX + ax, z = RZ + az). The tank trail from Fort Caldwell runs
 * up from the south through the plan's spot and on to the ranges in the north-west; the plan's
 * roads stop at a place's gates, so {@link #places} works out where the trail meets the field's
 * tiles the way Layout will, and the tiles' variant carries those gates ("armyfield@RX,RZ@ax,az,side;...")
 * for {@link ArmyFieldSite} to open the fence and run the perimeter road to.
 *
 * <p>Like the airport the field is cut into tiles of {@link #TILE_W}, in two bands ({@link #LAND_Z0}..
 * {@link #AIR_Z0} the ramp and buildings, {@link #AIR_Z0}..{@link #Z1} the runway), all at one level.
 */
final class ArmyField {
    /** The runway: half its length, half its width (cells -HALF_W..HALF_W), its shoulders' outer edge, the overrun past each end. */
    static final int HALF_LEN = 600, HALF_W = 15, SHOULDER = 20, OVERRUN = 40;
    /** The field's land, along the runway and across it. */
    static final int X0 = -720, X1 = 719, LAND_Z0 = -380, AIR_Z0 = -150, Z1 = 89;
    /** The parallel taxiway's centreline, its pavement either side, its shoulders out to; the hold line. */
    static final int TWY_Z = -82, TWY_HALF = 6, TWY_SHOULDER = 9, HOLD_Z = -52;
    /** The connectors from the taxiway down to the runway. */
    static final int[] CONNECTORS = {-585, 0, 585};
    /** The ramp's concrete, from the taxiway's shoulder north. */
    static final int RAMP_Z0 = -218, RAMP_Z1 = TWY_Z - TWY_SHOULDER - 1, RAMP_X0 = -400, RAMP_X1 = 520;
    /** The perimeter road's middle, this far in from the fence. */
    static final int PERIM = 7;
    static final int TILE_W = 240;

    private ArmyField() {
    }

    /** The field's tiles at the level of the ground under the whole of it, each carrying the gates the plan's roads will come to. */
    static List<Place> places(Plan.Spot s, County county, Plan plan, long h) {
        int ox = s.x(), oz = s.z();
        double sum = 0;
        int n = 0;
        for (int i = 0; i <= 12; i++) {
            for (int j = 0; j <= 6; j++) {
                double x = ox + X0 + (X1 - X0) * i / 12.0, z = oz + LAND_Z0 + (Z1 - LAND_Z0) * j / 6.0;
                double g = county.terrain.height(x, z);
                if (Terrain.isWater(g - 1)) {
                    return List.of();
                }
                sum += g;
                n++;
            }
        }
        int level = Math.max((int) Math.round(sum / n), Terrain.WATER + 2);
        List<int[]> boxes = new ArrayList<>();
        for (int a = X0; a <= X1; a += TILE_W) {
            int a1 = Math.min(X1, a + TILE_W - 1);
            boxes.add(new int[] {ox + a, oz + LAND_Z0, ox + a1, oz + AIR_Z0 - 1});
            boxes.add(new int[] {ox + a, oz + AIR_Z0, ox + a1, oz + Z1});
        }
        StringBuilder variant = new StringBuilder("armyfield@" + ox + "," + oz + "@");
        for (int[] g : gates(boxes, plan)) {
            variant.append(g[0] - ox).append(',').append(g[1] - oz).append(',').append(g[2]).append(';');
        }
        List<Place> out = new ArrayList<>();
        for (int[] b : boxes) {
            out.add(new Place(PlaceType.LANDMARK, variant.toString(), b[0], b[1], b[2], b[3], level, h, -1, -1));
        }
        return out;
    }

    /**
     * Where the plan's roads will meet the tiles: Layout breaks a road where it comes within 24 of a
     * place and runs it to the middle of the side facing the way it came (its point six steps back),
     * so this walks each road the same way. World x, z and the side (0 north, 1 east, 2 south, 3 west).
     */
    private static List<int[]> gates(List<int[]> boxes, Plan plan) {
        List<int[]> out = new ArrayList<>();
        for (Plan.Line rd : plan.roads) {
            List<double[]> pts = new ArrayList<>();
            Plan.Shape sh = rd.shape;
            for (int i = 0; i + 1 < sh.count(); i++) {
                double ax = sh.x(i), az = sh.z(i), bx = sh.x(i + 1), bz = sh.z(i + 1);
                int k = Math.max(1, (int) Math.round(Math.hypot(bx - ax, bz - az) / Road.STEP));
                for (int j = 0; j < k; j++) {
                    pts.add(new double[] {ax + (bx - ax) * j / k, az + (bz - az) * j / k});
                }
            }
            int[] prev = null;
            for (int i = 0; i < pts.size(); i++) {
                int[] in = boxAt(boxes, pts.get(i));
                if (in != null && prev == null && i > 0) {
                    out.add(gate(in, pts.get(Math.max(0, i - 7))));
                } else if (in == null && prev != null) {
                    out.add(gate(prev, pts.get(Math.min(pts.size() - 1, i + 6))));
                }
                prev = in;
            }
        }
        return out;
    }

    private static int[] boxAt(List<int[]> boxes, double[] q) {
        int x = (int) Math.floor(q[0]), z = (int) Math.floor(q[1]);
        for (int[] b : boxes) {
            if (x >= b[0] - 24 && x <= b[2] + 24 && z >= b[1] - 24 && z <= b[3] + 24) {
                return b;
            }
        }
        return null;
    }

    /** Place.gate's point on the side of the box facing toward. */
    private static int[] gate(int[] b, double[] toward) {
        int w = b[2] - b[0] + 1, d = b[3] - b[1] + 1;
        double cx = b[0] + w / 2.0, cz = b[1] + d / 2.0, dx = toward[0] - cx, dz = toward[1] - cz;
        int side = Math.abs(dx) * d > Math.abs(dz) * w ? (dx > 0 ? 1 : 3) : (dz > 0 ? 2 : 0);
        return switch (side) {
            case 0 -> new int[] {b[0] + w / 2, b[1], 0};
            case 1 -> new int[] {b[2], b[1] + d / 2, 1};
            case 2 -> new int[] {b[0] + w / 2, b[3], 2};
            default -> new int[] {b[0], b[1] + d / 2, 3};
        };
    }

    /** The runway's centre in the world, from a tile's variant. */
    static int[] origin(String variant) {
        String[] parts = variant.split("@");
        String[] xz = parts[1].split(",");
        return new int[] {Integer.parseInt(xz[0]), Integer.parseInt(xz[1])};
    }

    /** The gates in the frame from a tile's variant: {ax, az, side}. */
    static List<int[]> gates(String variant) {
        List<int[]> out = new ArrayList<>();
        String[] parts = variant.split("@");
        if (parts.length > 2) {
            for (String g : parts[2].split(";")) {
                if (!g.isEmpty()) {
                    String[] v = g.split(",");
                    out.add(new int[] {Integer.parseInt(v[0]), Integer.parseInt(v[1]), Integer.parseInt(v[2])});
                }
            }
        }
        return out;
    }
}
