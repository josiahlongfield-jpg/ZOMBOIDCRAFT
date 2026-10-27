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
            clearHeadroom(v);
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
                if (b != null && !tread(b) && !b.contains("door")) {
                    v.set(t[0], t[1] + k, t[2], "air");
                }
            }
        }
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
