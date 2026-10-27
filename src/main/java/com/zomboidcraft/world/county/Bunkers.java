package com.zomboidcraft.world.county;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Finds the hidden bunkers near a spot. Each one leaves two marks in its place's blocks: "bunker
 * STATE KIND" where you get in (a tower's hatch in its electrical room, a ship's false plate, the
 * top of a shelter's stairs, a forest hatch) and "bunker door" at its blast door straight after.
 * Only building a place shows whether it has one, so the places that may hold one are built (or
 * taken from the cache) nearest first.
 */
public final class Bunkers {
    public static final List<String> KINDS = List.of("tower", "ship", "shelter", "forest");

    /** One bunker: its kind and state, and in world blocks the way in and its blast door. */
    public record Found(String kind, String state, int x, int y, int z, int doorX, int doorY, int doorZ, Place place) {
    }

    /**
     * The places within radius of (x, z) that may hold a bunker of a kind (null: any), nearest
     * first: downtowns (towers, and the old cities' shelters), ports (ships), towns (shelters) and
     * hideouts (forest hatches).
     */
    public static List<Place> candidates(County county, int x, int z, String kind, int radius) {
        List<Place> places = county.placesNear(x - radius, z - radius, x + radius, z + radius, 0);
        List<Place> out = new ArrayList<>();
        if (kind == null || kind.equals("tower") || kind.equals("shelter")) {
            out.addAll(nearest(places, PlaceType.DOWNTOWN, x, z, 3));
        }
        if (kind == null || kind.equals("ship")) {
            out.addAll(nearest(places, PlaceType.PORT, x, z, 3));
        }
        if (kind == null || kind.equals("shelter")) {
            out.addAll(nearest(places, PlaceType.TOWN, x, z, 6));
        }
        if (kind == null || kind.equals("forest")) {
            out.addAll(nearest(county.hideoutsNear(x - radius, z - radius, x + radius, z + radius, 0), PlaceType.HIDEOUT, x, z, 3));
        }
        out.sort(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - x, p.centreZ() - z)));
        return out;
    }

    private static List<Place> nearest(List<Place> places, PlaceType type, int x, int z, int n) {
        return places.stream().filter(p -> p.type == type)
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.centreX() - x, p.centreZ() - z)))
                .limit(n).toList();
    }

    /** The bunkers of a kind (null: any) a place holds, as its builder marked them. */
    public static List<Found> in(CountyGen gen, Place p, String kind) {
        List<Voxels.Mark> marks = gen.voxels(p).marks;
        List<Found> out = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            String[] what = marks.get(i).what().split(" ");
            if (what.length < 3 || !what[0].equals("bunker") || kind != null && !what[2].equals(kind)) {
                continue;
            }
            // its blast door is the next "bunker door" after it (other marks may come between)
            Voxels.Mark at = marks.get(i), door = at;
            for (int j = i + 1; j < marks.size(); j++) {
                String w = marks.get(j).what();
                if (w.equals("bunker door")) {
                    door = marks.get(j);
                    break;
                }
                if (w.startsWith("bunker ")) {
                    break;
                }
            }
            out.add(new Found(what[2], what[1], p.x0 + at.x(), p.level + at.y(), p.z0 + at.z(),
                    p.x0 + door.x(), p.level + door.y(), p.z0 + door.z(), p));
        }
        return out;
    }

    private Bunkers() {
    }
}
