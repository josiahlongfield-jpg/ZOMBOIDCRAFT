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
            case ROADSIDE -> new RoadsideSite(p, out).build();
            case HIDEOUT -> new HatchSite(p, county, out).build();
        }
        if (out instanceof Voxels v) {
            finishFlights(v);
            clearHeadroom(v);
            fitHatches(v);
            clearDoorways(v);
            clearGrass(v);
            clearTrees(v);
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
     * barricades are left be: those are meant.)
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
     * Trees give way to what's built, whichever went in first: no leaf inside or under anything
     * built (a house, an awning, a lamp's arm), none lower than {@link Kit#HEADROOM} over paving,
     * none in a car; a car that would stand in a trunk or a limb isn't left there at all.
     */
    static void clearTrees(Voxels v) {
        List<String> pal = v.palette();
        Map<Long, Integer> roof = new HashMap<>();
        List<int[]> leaves = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id == 0 || y < 1) {
                return;
            }
            String b = pal.get(id);
            if (b.startsWith("tree_") && b.contains("_leaves")) {
                leaves.add(new int[] {x, y, z});
            } else if (!b.startsWith("tree_") && !Kit.soft(b) && !b.equals("moss_carpet") && !b.equals("leaf_litter")) {
                roof.merge(column(x, z), y, Math::max);
            }
        });
        for (int[] c : leaves) {
            Integer top = roof.get(column(c[0], c[2]));
            if (top != null && c[1] < top || c[1] < Kit.HEADROOM && !Kit.soil(v.get(c[0], 0, c[2]))) {
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
