package com.zomboidcraft.world.county;

/** The kinds of place the county planner lays out, each with its own builder and variants. */
public enum PlaceType {
    /** Skyscrapers, walk-ups, an elevated railway: the big city. */
    DOWNTOWN,
    /** A main street of brick shops, a church and a water tower, with houses around it. */
    TOWN,
    /** Streets of houses with yards, fences and garages. */
    SUBURB,
    /** A farmstead: house, barns, silos, fields and fences. */
    FARM,
    /** Docks on a lake or bay: cranes, containers, ships, warehouses. */
    PORT,
    /** A fenced base or quarantine zone: walls, watchtowers, hangars, sandbags. */
    MILITARY,
    /** Something by the road: a gas station, motel, diner or church on its own. */
    ROADSIDE,
    /**
     * Something hidden in the deep woods, far from every road: a survivalist's hatch over a
     * bunker. Planned on a layer of its own ({@link County#hideout}), never levelled or linked.
     */
    HIDEOUT
}
