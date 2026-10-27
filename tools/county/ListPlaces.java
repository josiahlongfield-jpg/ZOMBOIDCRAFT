import com.zomboidcraft.world.county.County;
import com.zomboidcraft.world.county.Place;
import com.zomboidcraft.world.county.PlaceType;

import java.util.Comparator;
import java.util.List;

/**
 * Prints the nearest place of each kind (and each variant) to the origin for a seed, as
 * "type variant centreX centreZ x0 z0 x1 z1", so a test can generate chunks there.
 *
 *   java -cp build/county ListPlaces [seed] [radius]
 */
public final class ListPlaces {
    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 12345L;
        int r = args.length > 1 ? Integer.parseInt(args[1]) : 6000;
        County county = Boolean.getBoolean("county.endless") ? County.endless(seed) : new County(seed);
        List<Place> found = new java.util.ArrayList<>(county.placesNear(-r, -r, r, r, 0));
        found.addAll(county.hideoutsNear(-r, -r, r, r, 0));
        List<Place> all = found.stream()
                .sorted(Comparator.comparingDouble(p -> Math.hypot(p.centreX(), p.centreZ()))).toList();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Place p : all) {
            if (seen.add(p.type + "/" + p.variant)) {
                System.out.println(p.type.name().toLowerCase() + " " + p.variant + " " + p.centreX() + " " + p.centreZ()
                        + " " + p.x0 + " " + p.z0 + " " + p.x1 + " " + p.z1);
            }
        }
        for (PlaceType t : PlaceType.values()) {
            if (all.stream().noneMatch(p -> p.type == t)) {
                System.err.println("no " + t + " within " + r);
            }
        }
    }
}
