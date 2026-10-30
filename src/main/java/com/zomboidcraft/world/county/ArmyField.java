package com.zomboidcraft.world.county;

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
 * <p>Like the airport the field is cut into tiles of {@link #TILE_W}, all at one level ({@link FrameSite}).
 */
final class ArmyField {
    /** The runway: half its length, half its width (cells -HALF_W..HALF_W), its shoulders' outer edge, the overrun past each end. */
    static final int HALF_LEN = 600, HALF_W = 15, SHOULDER = 20, OVERRUN = 40;
    /** The field's land, along the runway and across it. */
    static final int X0 = -720, X1 = 719, LAND_Z0 = -380, Z1 = 89;
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

    /** The field's tiles, each carrying the gates the plan's roads will come to ({@link FrameSite#tiles}). */
    static List<Place> places(Plan.Spot s, County county, Plan plan, long h) {
        return FrameSite.tiles("armyfield", s, county, plan, h, X0, LAND_Z0, X1, Z1, TILE_W);
    }
}
