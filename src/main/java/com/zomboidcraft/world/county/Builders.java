package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;

/** Runs the right builder for a place, drawing in its own coordinates (see {@link Place}). */
public final class Builders {
    public static void build(Place p, County county, BlockSink out) {
        switch (p.type) {
            case DOWNTOWN -> new DowntownSite(p, out).build();
            case TOWN -> new TownSite(p, out).build();
            case SUBURB -> new SuburbSite(p, out).build();
            case FARM -> new FarmSite(p, out).build();
            case PORT -> new PortSite(p, out).build();
            case MILITARY -> new MilitarySite(p, out).build();
            case ROADSIDE -> new RoadsideSite(p, out).build();
        }
    }

    private Builders() {
    }
}
