package com.zomboidcraft.world.county;

import java.util.List;

/**
 * The county's power districts and the substation that feeds each (Josiah 2026-10-31, the landscape plan's
 * "district by district" failure): the lights go out a district at a time, its substation the place you can
 * see it happen and the place a skilled player can bring it back. LOOT owns the grid itself (the shutoff, its
 * timing, the always-on toggle, the restore); this is the map it runs on: which district any column is in,
 * and where each district's yard is.
 * <p>
 * The districts follow the plan's places: Grayhaven Island on Harbor Point's own switchyard; the mainland city
 * by its parts (Midtown, Uptown, Eastside, Westgate, Northside, Southport); Riverside and Ashby; the four towns;
 * the hamlets and the fishing hamlets. Every column belongs to the nearest district's middle, except that the
 * island is the island (the river is the line). Each district's substation is a plan place ("sub-" + id, see
 * county_plan.txt and {@link Layout}): a full yard for a city district, a compact yard for a town, an H-pole
 * and a kiosk for a hamlet; {@link Substation} builds them.
 */
public final class PowerGrid {
    /** How big a district's substation is. */
    public enum Kind {
        /** Harbor Point Generating Station's own switchyard: nothing new to build. */
        STATION,
        /** A full yard: two transformers, gantries, breakers, the control house. */
        CITY,
        /** A compact yard: one transformer, a gantry, the brick kiosk. */
        TOWN,
        /** A pole-top transformer on its H-frame and a kiosk on a fenced pad. */
        HAMLET
    }

    /** A district: its id (the substation's plan place is "sub-" + id), its name, its middle and its yard's kind. */
    public record District(String id, String name, int x, int z, Kind kind) {
    }

    public static final List<District> DISTRICTS = List.of(
            new District("island", "Grayhaven Island", 3400, -1250, Kind.STATION),
            new District("midtown", "Midtown", 2100, -2550, Kind.CITY),
            new District("uptown", "Uptown", 1900, -3850, Kind.CITY),
            new District("eastside", "Eastside", 3450, 650, Kind.CITY),
            new District("westgate", "Westgate", 900, -1650, Kind.CITY),
            new District("northside", "Northside", 600, -3150, Kind.CITY),
            new District("southport", "Southport", 2200, 1150, Kind.CITY),
            new District("riverside", "Riverside", -2600, -1950, Kind.CITY),
            new District("ashby", "Ashby", -3000, 2300, Kind.CITY),
            new District("seabright", "Seabright", 3900, 5800, Kind.TOWN),
            new District("kingsbridge", "Kingsbridge", 800, 6400, Kind.TOWN),
            new District("millbrook", "Millbrook", -5000, -1300, Kind.TOWN),
            new District("fairview", "Fairview", -5400, 3600, Kind.TOWN),
            new District("hollowcreek", "Hollow Creek", -1400, 5400, Kind.HAMLET),
            new District("dunmore", "Dunmore", -6100, 1300, Kind.HAMLET),
            new District("timberline", "Timberline", -7200, 5600, Kind.HAMLET),
            new District("capemerrow", "Cape Merrow", 6250, -5150, Kind.HAMLET),
            new District("gullharbor", "Gull Harbor", 4650, 7500, Kind.HAMLET));

    private PowerGrid() {
    }

    /** The district a column is in: the island's on the island, everywhere else the nearest middle off it. */
    public static District district(Plan plan, double x, double z) {
        boolean isle = plan != null && plan.isle != null && plan.isle.contains(x, z);
        District best = null;
        double far = Double.MAX_VALUE;
        for (District d : DISTRICTS) {
            if ((d.kind == Kind.STATION) != isle) {
                continue;
            }
            double dist = Math.hypot(d.x - x, d.z - z);
            if (dist < far) {
                far = dist;
                best = d;
            }
        }
        return best;
    }

    /** The district with this id, or null. */
    public static District byId(String id) {
        for (District d : DISTRICTS) {
            if (d.id.equals(id)) {
                return d;
            }
        }
        return null;
    }

    /**
     * Where a district's substation stands: the middle of its plan place ("sub-" + id; Harbor Point's for the
     * island), {x, z}, or null when the county has none (a world from before the substations).
     */
    public static int[] substation(County county, District d) {
        String variant = d.kind == Kind.STATION ? "power" : null;
        for (Place p : county.placesNear(d.x - 900, d.z - 900, d.x + 900, d.z + 900, 0)) {
            boolean sub = variant == null ? p.variant.startsWith("substation") : p.variant.equals(variant);
            if (sub && district(county.terrain.plan, p.centreX(), p.centreZ()) == d) {
                return new int[] {(int) Math.round(p.centreX()), (int) Math.round(p.centreZ())};
            }
        }
        return null;
    }
}
