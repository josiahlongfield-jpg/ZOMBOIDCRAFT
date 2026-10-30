package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.List;

/**
 * Grayhaven International Airport's plan, a block a metre: one runway, 9/27, 2,000 long and 45
 * wide along x, its parallel taxiway to the north with the connectors down to it, the apron and
 * the terminal north of that with its concourses out over the apron, the landside (the kerb, the
 * garage, the lots) north again; the cargo shed, the fire station and the fuel farm to the west,
 * the hangars, the general aviation ramp and the tower to the east; the perimeter fence round it
 * all. Coordinates here are the airport's own: ax along the runway from its middle, az from its
 * centreline, the terminal's side negative ("north" in the airport's frame).
 *
 * <p>In the world the frame is the world's ({@link #ROT} 0: x = RX + ax, z = RZ + az), the runway
 * east and west with the terminal on its north side facing the city: the airport stands east of
 * I-95 between the city wall and the lake, the one stretch near the Airport station that the
 * railway and the interstates don't cross.
 *
 * <p>The airport is too big to build in one go, so {@link #places} cuts it into tiles of
 * {@link #TILE_W} by one of the two bands ({@link #LAND_Z0}..{@link #AIR_Z0} the landside and
 * buildings, {@link #AIR_Z0}..{@link #Z1} the airfield), each a LANDMARK place of variant
 * "airport@RX,RZ" that {@link AirportSite} builds by drawing everything that falls in it, so the
 * tiles meet without a seam. Every tile stands at the one level.
 */
final class Airport {
    /** The runway: half its length, half its width (cells -HALF_W..HALF_W), its paved shoulders' outer edge. */
    static final int HALF_LEN = 1000, HALF_W = 22, SHOULDER = 29;
    /** Where the airport's land runs, along the runway (out past the west end for the approach lights) and across it. */
    static final int X0 = -1250, X1 = 1149, LAND_Z0 = -560, AIR_Z0 = -200, Z1 = 199;
    /** The parallel taxiway's centreline; its pavement either side, and its shoulders out to. */
    static final int TWY_Z = -122, TWY_HALF = 7, TWY_SHOULDER = 11;
    /** The connectors from the taxiway down to the runway, at these ax. */
    static final int[] CONNECTORS = {-985, -500, 0, 500, 985};
    /** The connectors' names, west to east, as their location signs give them. */
    static final String[] CONNECTOR_NAMES = {"a1", "a2", "a3", "a4", "a5"};
    /** The hold-short line across a connector, this far north of the runway's centreline. */
    static final int HOLD_Z = -76;
    /** The apron, from the taxiway's north shoulder to the terminal's face. */
    static final int APRON_Z0 = -330, APRON_Z1 = -134, APRON_X0 = -420, APRON_X1 = 420;
    static final int TILE_W = 250;
    /** The access road's middle across the landside, through the gate in the west fence to the terminal ({@link AirportLandside}). */
    static final int ACCESS_Z = -475;
    /** The quarter turns from the airport's frame to the world's. */
    static final int ROT = 0;

    private Airport() {
    }

    /** The runway's centre in the world: the plan's spot. */
    static int[] origin(Plan.Spot s) {
        return new int[] {s.x(), s.z()};
    }

    /** The airport's tiles, every one at the level of the ground under the whole of it (null if it's on water). */
    static List<Place> places(Plan.Spot s, County county, long h) {
        int[] o = origin(s);
        double sum = 0;
        int n = 0;
        for (int i = 0; i <= 12; i++) {
            for (int j = 0; j <= 6; j++) {
                double x = o[0] + X0 + (X1 - X0) * i / 12.0, z = o[1] + LAND_Z0 + (Z1 - LAND_Z0) * j / 6.0;
                double g = county.terrain.height(x, z);
                if (Terrain.isWater(g - 1)) {
                    return List.of();
                }
                sum += g;
                n++;
            }
        }
        int level = Math.max((int) Math.round(sum / n), Terrain.WATER + 2);
        String variant = "airport@" + o[0] + "," + o[1];
        List<Place> out = new ArrayList<>();
        for (int a = X0; a <= X1; a += TILE_W) {
            int a1 = Math.min(X1, a + TILE_W - 1);
            out.add(new Place(PlaceType.LANDMARK, variant, o[0] + a, o[1] + LAND_Z0, o[0] + a1, o[1] + AIR_Z0 - 1, level, h, -1, -1));
            out.add(new Place(PlaceType.LANDMARK, variant, o[0] + a, o[1] + AIR_Z0, o[0] + a1, o[1] + Z1, level, h, -1, -1));
        }
        return out;
    }

    /** The runway's centre in the world, from a tile's variant ("airport@RX,RZ"). */
    static int[] origin(String variant) {
        String[] xz = variant.substring(variant.indexOf('@') + 1).split(",");
        return new int[] {Integer.parseInt(xz[0]), Integer.parseInt(xz[1])};
    }
}
