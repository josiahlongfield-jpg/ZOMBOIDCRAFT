package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            case ROADSIDE -> {
                if (p.variant.equals("depot")) {
                    new DepotSite(p, county, out).build();
                } else {
                    new RoadsideSite(p, out).build();
                }
            }
            case HIDEOUT -> new HatchSite(p, county, out).build();
            case LANDMARK -> new LandmarkSite(p, out).build();
        }
        if (out instanceof Voxels v) {
            finishFlights(v);
            clearHeadroom(v);
            fitHatches(v);
            clearDoorways(v);
            clearGrass(v);
            clearTrees(v);
            if (p.type != PlaceType.LANDMARK && county != null) {
                clearFrontage(p, county, v);
            }
        }
    }

    /** How far out from a landmark's lot the street keeps no tree: its sidewalks and the near lane. */
    static final int FRONTAGE = 10;

    /**
     * A landmark stands clear: the street trees of the places round it (a downtown piece's
     * sidewalk pits) are left out within {@link #FRONTAGE} of its lot, whole, trunk and crown,
     * so the station's front and the bridge to the el aren't lost behind a row of maples. Each
     * leaf or limb goes with the trunk nearest it, so a tree further off keeps all its crown.
     */
    static void clearFrontage(Place p, County county, Voxels v) {
        List<int[]> boxes = new ArrayList<>();
        for (Place o : county.placesNear(p.x0, p.z0, p.x1, p.z1, FRONTAGE)) {
            if (o.type == PlaceType.LANDMARK && o != p) {
                boxes.add(new int[] {o.x0 - FRONTAGE - p.x0, o.z0 - FRONTAGE - p.z0, o.x1 + FRONTAGE - p.x0, o.z1 + FRONTAGE - p.z0});
            }
        }
        if (boxes.isEmpty()) {
            return;
        }
        List<String> pal = v.palette();
        List<int[]> trunks = new ArrayList<>();
        List<int[]> wood = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id == 0 || y < 1 || !pal.get(id).startsWith("tree_")) {
                return;
            }
            wood.add(new int[] {x, y, z});
            String b = pal.get(id), under = v.get(x, y - 1, z);
            if (!b.contains("_leaves") && !b.contains("_limb") && (under == null || !under.startsWith("tree_"))) {
                boolean gone = false;
                for (int[] r : boxes) {
                    gone |= x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3];
                }
                trunks.add(new int[] {x, z, gone ? 1 : 0});
            }
        });
        if (trunks.stream().noneMatch(t -> t[2] == 1)) {
            return;
        }
        for (int[] c : wood) {
            int[] best = null;
            double near = 81;
            for (int[] t : trunks) {
                double dx = t[0] - c[0], dz = t[1] - c[2], dd = dx * dx + dz * dz;
                if (dd < near) {
                    near = dd;
                    best = t;
                }
            }
            if (best != null && best[2] == 1) {
                v.set(c[0], c[1], c[2], "air");
            }
        }
    }

    /** The treads of a real flight (vanilla stairs are mostly roofs and eaves, so they're left be). */
    static boolean tread(String b) {
        return b != null && !b.startsWith("minecraft:") && (b.contains("stairs") || b.startsWith("tr_stair"));
    }

    /**
     * A body's headroom over every flight, whatever the builder left there: nothing but air in
     * the three blocks over a tread (bar the treads of a flight above), so a player or a zombie
     * climbing never meets the floor above, a lamp or a shelf with their head. Without it a stair
     * whose floor opening started a step late couldn't be climbed at all.
     */
    static void clearHeadroom(Voxels v) {
        List<String> pal = v.palette();
        List<int[]> treads = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id != 0 && tread(pal.get(id))) {
                treads.add(new int[] {x, y, z});
            }
        });
        for (int[] t : treads) {
            for (int k = 1; k <= 3; k++) {
                String b = v.get(t[0], t[1] + k, t[2]);
                // a banister up the flight's edge stays: it stands at the side, clear of heads
                if (b != null && !tread(b) && !b.contains("door") && !b.contains("rail_edge") && !b.contains("banister")) {
                    v.set(t[0], t[1] + k, t[2], "air");
                }
            }
            // and over the floor at its foot: stepping up onto the first tread a head rises half
            // a block while still under the ceiling there, so a ceiling two over that floor (a
            // cellar's joists under the hall floor) stops it; a floor open above (a deck's
            // landing, walked on itself) is left
            String b = v.get(t[0], t[1], t[2]);
            int[] f = STEP.get(prop(b, "facing"));
            if (f == null || "top".equals(prop(b, "half"))) {
                continue;
            }
            int fx = t[0] - f[0], fz = t[2] - f[1];
            String c = v.get(fx, t[1] + 2, fz);
            if (!tread(v.get(fx, t[1] - 1, fz)) && v.get(fx, t[1], fz) == null && v.get(fx, t[1] + 1, fz) == null
                    && c != null && !tread(c) && !c.contains("door") && !c.contains("wall") && !c.contains("window")
                    && !c.contains("rail") && !c.contains("banister") && v.get(fx, t[1] + 3, fz) != null) {
                v.set(fx, t[1] + 2, fz, "air");
            }
        }
    }

    /**
     * A flight that stops a tread short of the floor it climbs to: its top tread one under the
     * floor's plane, so the last step up is a whole block that a body has to jump (a landing laid
     * over the top tread does it). The floor's edge cell in front of it becomes one more tread.
     */
    static void finishFlights(Voxels v) {
        List<String> pal = v.palette();
        List<int[]> treads = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id != 0 && tread(pal.get(id))) {
                treads.add(new int[] {x, y, z});
            }
        });
        for (int[] t : treads) {
            String b = v.get(t[0], t[1], t[2]);
            int[] f = STEP.get(prop(b, "facing"));
            if (f == null || "top".equals(prop(b, "half"))) {
                continue;
            }
            int nx = t[0] + f[0], ny = t[1] + 1, nz = t[2] + f[1];
            String n = v.get(nx, ny, nz), on = v.get(nx + f[0], ny, nz + f[1]);
            if (n != null && on != null && !tread(n) && !tread(on) && FLOOR.matcher(n).find() && FLOOR.matcher(on).find()
                    && v.get(nx, ny + 1, nz) == null && v.get(nx + f[0], ny + 1, nz + f[1]) == null) {
                v.set(nx, ny, nz, b);
            }
        }
    }

    private static final Map<String, int[]> STEP = Map.of("north", new int[] {0, -1}, "south", new int[] {0, 1},
            "east", new int[] {1, 0}, "west", new int[] {-1, 0});

    /** Blocks a floor is laid in. */
    private static final java.util.regex.Pattern FLOOR = java.util.regex.Pattern.compile(
            "floor|terrazzo|parquet|lino|carpet|boards|plank|deck|tile|concrete|slab|marble|grate|plate");

    /** Ladders a hatch can stand over (not a stepladder, a slide's or the monkey bars). */
    private static final Set<String> LADDERS = Set.of("ladder", "bunk_ladder", "minecraft:ladder", "vessel_pilot_ladder");

    /**
     * Every hatch over a ladder hinges on the far side from it: open, its leaf stands against the
     * opposite wall, clear of the ladder, so whoever climbs comes straight up through the hole (a
     * leaf on the ladder's own wall stands proud of the rungs and stops a head). A vanilla
     * trapdoor there becomes the wooden cellar hatch, which climbs like the ladder below it.
     */
    static void fitHatches(Voxels v) {
        List<String> pal = v.palette();
        List<int[]> hatches = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            String b = id == 0 ? null : pal.get(id);
            if (b != null && b.contains("half=") && (b.contains("trapdoor") || b.startsWith("bunk_hatch"))) {
                hatches.add(new int[] {x, y, z});
            }
        });
        for (int[] h : hatches) {
            String below = v.get(h[0], h[1] - 1, h[2]);
            if (below == null || !LADDERS.contains(below.split("\\[")[0])) {
                continue;
            }
            String side = prop(below, "facing");
            String b = v.get(h[0], h[1], h[2]);
            String name = b.split("\\[")[0];
            if (name.startsWith("minecraft:")) {
                name = "bunk_hatch_wood";
            }
            String open = prop(b, "open");
            v.set(h[0], h[1], h[2], name + "[facing=" + OPPOSITE.getOrDefault(side, "north") + ",half=" + prop(b, "half")
                    + ",open=" + (open == null ? "false" : open) + "]");
        }
    }

    /**
     * Nothing right in front of a door or behind it: rubble, a trash bag or a box there lifts a
     * body high enough that its head meets the lintel, and a bench, a desk or a radiator a builder
     * lined a wall with stops it dead, so the doorway can't be walked through. (Sandbags and
     * barricades are left be: those are meant.) A building's door that opens flat against the
     * next building's wall (a side exit on a lot built up to its neighbour) is bricked up with
     * its own wall: a door nobody can go through is no door.
     */
    static void clearDoorways(Voxels v) {
        List<String> pal = v.palette();
        List<int[]> doors = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            String b = id == 0 ? null : pal.get(id);
            if (b != null && b.contains("door") && b.contains("half=lower") && !b.contains("trapdoor")) {
                String f = prop(b, "facing");
                doors.add(new int[] {x, y, z, "north".equals(f) || "south".equals(f) ? 1 : 0});
            }
        });
        doors.removeIf(d -> {
            String b = v.get(d[0], d[1], d[2]);
            if (SEALED_OFF.matcher(b).find()) {
                return false;
            }
            for (int s = -1; s <= 1; s += 2) {
                int x = d[0] + (d[3] == 0 ? s : 0), z = d[2] + (d[3] == 1 ? s : 0);
                if (!wall(v.get(x, d[1], z)) || !wall(v.get(x, d[1] + 1, z))) {
                    continue;
                }
                String[] own = new String[2];
                for (int k = 0; k < 2; k++) {
                    for (int t = -1; t <= 1 && own[k] == null; t += 2) {
                        String w = v.get(d[0] + (d[3] == 1 ? t : 0), d[1] + k, d[2] + (d[3] == 0 ? t : 0));
                        own[k] = wall(w) ? w : null;
                    }
                }
                if (own[0] != null && own[1] != null) {
                    v.set(d[0], d[1], d[2], own[0]);
                    v.set(d[0], d[1] + 1, d[2], own[1]);
                    return true;
                }
            }
            return false;
        });
        // and the doorways whose doors are gone: through whichever way the wall isn't
        for (Voxels.Mark m : v.marks) {
            if (m.what().equals("doorway")) {
                boolean alongX = v.get(m.x() - 1, m.y(), m.z()) != null && v.get(m.x() + 1, m.y(), m.z()) != null;
                boolean alongZ = v.get(m.x(), m.y(), m.z() - 1) != null && v.get(m.x(), m.y(), m.z() + 1) != null;
                if (alongX != alongZ) {
                    doors.add(new int[] {m.x(), m.y(), m.z(), alongX ? 1 : 0});
                }
            }
        }
        for (int[] d : doors) {
            for (int s = -1; s <= 1; s += 2) {
                int x = d[0] + (d[3] == 0 ? s : 0), z = d[2] + (d[3] == 1 ? s : 0);
                String b = v.get(x, d[1], z);
                if (b != null && (LITTER.matcher(b).find() || FURNITURE.matcher(b).find() && !KEEP.matcher(b).find())) {
                    v.set(x, d[1], z, "air");
                    String up = v.get(x, d[1] + 1, z);
                    // and what stood on it, or its top half
                    if (up != null && (up.startsWith(b.split("\\[")[0]) || FURNITURE.matcher(up).find() && !KEEP.matcher(up).find())) {
                        v.set(x, d[1] + 1, z, "air");
                    }
                }
            }
        }
    }

    /** A wall a door can't open through: masonry, siding, plaster, a building's wall block. */
    static boolean wall(String b) {
        return b != null && WALL.matcher(b).find() && !NOT_WALL.matcher(b).find();
    }

    private static final java.util.regex.Pattern WALL = java.util.regex.Pattern.compile(
            "brick|^home_wall|^siding|^drywall|plaster|^stucco|wainscot|^corrugated|^cinder|^concrete_block|_wall($|_|\\[)");
    private static final java.util.regex.Pattern NOT_WALL = java.util.regex.Pattern.compile(
            "window|door|glass|sign|lamp|light|rail|stair|ladder|vent|pipe|poster|graffiti|hatch|pile|rubble");
    /** Doors meant to stand where they stand whatever's in front: ships', bunkers', vaults', cells'. */
    private static final java.util.regex.Pattern SEALED_OFF = java.util.regex.Pattern.compile(
            "^(bunk_|ship_|navy_|vessel|vault|cell_|garage_door|car_)");

    private static final java.util.regex.Pattern LITTER =
            java.util.regex.Pattern.compile("^(rubble|trash_bag|debris|cardboard_box|brick_pile|ch_rubble|home_boxes)");
    /** Furniture and fittings that don't belong in a doorway. */
    private static final java.util.regex.Pattern FURNITURE = java.util.regex.Pattern.compile(
            "bench|chair|desk|table|radiator|cabinet|bookcase|books_sta|shelf|shelves|shelving|stepladder|crate|barrel|drum|locker|counter|"
            + "safe|coat_rack|sofa|couch|stool|cooler|bin\\b|cart|trolley|plant|potted|flag|cot_|dresser|wardrobe|fridge|"
            + "stove|washer|dryer|piano|pew|lectern|podium|vending|rack|armchair|tv|lamp_floor|filing|copier|mailbox");
    /** What stays even in front of a door. */
    private static final java.util.regex.Pattern KEEP = java.util.regex.Pattern.compile(
            "door|window|wall|water_table|trim|sill|sandbag|barricade|boarded|stairs|rail|ladder|hatch");

    private static final Map<String, String> OPPOSITE = Map.of("north", "south", "south", "north", "east", "west", "west", "east");

    /** A block state's property, or null. */
    static String prop(String state, String key) {
        int i = state.indexOf(key + "=");
        if (i < 0) {
            return null;
        }
        int j = i + key.length() + 1, k = j;
        while (k < state.length() && state.charAt(k) != ',' && state.charAt(k) != ']') {
            k++;
        }
        return state.substring(j, k);
    }

    /**
     * Trees give way to what's built, whichever went in first: no leaf or limb inside or under
     * anything built (a house, an awning, a fire escape, a lamp's arm), none lower than
     * {@link Kit#HEADROOM} over paving, no leaf in a car; a car that would stand in a trunk or a
     * limb isn't left there at all; nor within a body's height over the top of anything built (a
     * balcony, a porch roof, a roof). A bough (bark up in the crown) goes the same way, between
     * built things in its column (one through a fire escape's landing shut its ladder off) or
     * over them (one at head height on a balcony shut its door), but a trunk the built thing
     * stands round, an arm through it, is left whole.
     */
    static void clearTrees(Voxels v) {
        List<String> pal = v.palette();
        Map<Long, Integer> roof = new HashMap<>();
        Map<Long, Integer> base = new HashMap<>();
        List<int[]> leaves = new ArrayList<>();
        List<int[]> boughs = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id == 0 || y < 1) {
                return;
            }
            String b = pal.get(id);
            if (b.startsWith("tree_") && (b.contains("_leaves") || b.contains("_limb"))) {
                leaves.add(new int[] {x, y, z});
            } else if (b.startsWith("tree_")) {
                boughs.add(new int[] {x, y, z});
            } else if (!Kit.soft(b) && !b.equals("moss_carpet") && !b.equals("leaf_litter")) {
                roof.merge(column(x, z), y, Math::max);
                base.merge(column(x, z), y, Math::min);
            }
        });
        for (int[] c : leaves) {
            Integer top = roof.get(column(c[0], c[2]));
            if (top != null && c[1] <= top + 2 || c[1] < Kit.HEADROOM && !Kit.soil(v.get(c[0], 0, c[2]))) {
                v.set(c[0], c[1], c[2], "air");
            }
        }
        for (int[] c : boughs) {
            Integer top = roof.get(column(c[0], c[2]));
            if (top == null) {
                continue;
            }
            int foot = base.get(column(c[0], c[2]));
            String under = v.get(c[0], foot - 1, c[2]);
            boolean trunk = under != null && under.startsWith("tree_") && !under.contains("_leaves") && !under.contains("_limb");
            if (foot < c[1] && c[1] < top || !trunk && top < c[1] && c[1] <= top + 2) {
                v.set(c[0], c[1], c[2], "air");
            }
        }
        v.cars.removeIf(car -> {
            double rad = Math.toRadians(car.yaw());
            double ax = -Math.sin(rad), az = Math.cos(rad);
            double half = Math.min(car.maxLength(), 10) / 2;
            boolean wood = false;
            for (int x = (int) Math.floor(car.x() - half - 1); x <= car.x() + half + 1; x++) {
                for (int z = (int) Math.floor(car.z() - half - 1); z <= car.z() + half + 1; z++) {
                    double dx = x + 0.5 - car.x(), dz = z + 0.5 - car.z();
                    if (Math.abs(dx * ax + dz * az) > half + 0.3 || Math.abs(dx * az - dz * ax) > 1.5) {
                        continue;
                    }
                    for (int y = car.y(); y <= car.y() + 2; y++) {
                        String b = v.get(x, y, z);
                        if (b != null && b.startsWith("tree_")) {
                            if (b.contains("_leaves")) {
                                v.set(x, y, z, "air");
                            } else {
                                wood = true;
                            }
                        }
                    }
                }
            }
            return wood;
        });
    }

    private static long column(int x, int z) {
        return (long) x << 32 | (z & 0xFFFFFFFFL);
    }

    /** Grass, ferns and wild flowers: what only grows out of soil. */
    private static final Set<String> GRASS = Set.of("minecraft:short_grass", "minecraft:tall_grass[half=lower]",
            "minecraft:short_dry_grass", "minecraft:tall_dry_grass", "minecraft:fern", "minecraft:large_fern[half=lower]",
            "minecraft:bush", "minecraft:dandelion", "minecraft:poppy", "minecraft:oxeye_daisy", "minecraft:cornflower",
            "grass_tuft", "weeds", "weeds_tall");
    private static final Set<String> SOIL = Set.of("minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
            "minecraft:podzol", "minecraft:rooted_dirt", "minecraft:moss_block", "minecraft:mud",
            "minecraft:farmland");

    /**
     * No grass where roads, paths, lots, floors and buildings are (Josiah's rule): a lawn sown
     * before a drive, a pad or a house went over it leaves its grass standing on the asphalt or the
     * boards, and weeds in a road's cracks come up in game as clumps of grass. Grass and weeds stay
     * only on soil (or on the terrain under the place, where the place wrote no ground) and in
     * planters; the cracks keep their moss.
     */
    static void clearGrass(Voxels v) {
        List<String> pal = v.palette();
        List<int[]> gone = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id == 0) {
                return;
            }
            String b = pal.get(id);
            if (!GRASS.contains(b) && !b.startsWith("gc_")) {
                return;
            }
            String under = v.get(x, y - 1, z);
            if (under == null || SOIL.contains(under) || under.contains("planter") || under.contains("flower")
                    || under.contains("soil") || under.contains("garden") || under.startsWith("sc_turf")) {
                return;
            }
            gone.add(new int[] {x, y, z});
        });
        for (int[] c : gone) {
            v.set(c[0], c[1], c[2], "air");
            String above = v.get(c[0], c[1] + 1, c[2]);
            if (above != null && above.endsWith("[half=upper]")) {
                v.set(c[0], c[1] + 1, c[2], "air");
            }
        }
    }

    private Builders() {
    }
}
