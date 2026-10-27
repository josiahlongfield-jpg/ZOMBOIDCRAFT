package com.zomboidcraft.world.county;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Generates one chunk of the county: rock and soil up to the ground, water in the rivers and
 * lakes, roads cut through and bridged, the places' buildings copied in, and woods, scrub and
 * weeds over everything else. Pure Java, so the preview tool runs exactly what the game does.
 */
public final class CountyGen {
    /** Trees stand on a jittered grid this many blocks apart, so a chunk can find its neighbours'. */
    private static final int TREE_GRID = 7;
    private static final int TREE_REACH = Trees.REACH;

    private final County county;
    private final PlaceCache cache;

    public CountyGen(County county, PlaceCache cache) {
        this.county = county;
        this.cache = cache;
    }

    public County county() {
        return county;
    }

    /** The places' built blocks, shared with the paper maps so a place is only planned once. */
    public PlaceCache cache() {
        return cache;
    }

    /** A place's blocks as its builder left them (built now if no chunk has asked for them yet). */
    public Voxels voxels(Place p) {
        return cache.get(p, county);
    }

    /** What the column-pass found, kept for the vegetation pass. */
    private static final class Column {
        int top;
        boolean water, road, built, shore;
        double forest, moisture;
    }

    public void generate(int chunkX, int chunkZ, ChunkSink sink) {
        ChunkSink out = Cover.wrap(sink, county.seed);
        int bx = chunkX << 4, bz = chunkZ << 4;
        Terrain t = county.terrain;
        List<Place> places = county.placesNear(bx - TREE_REACH, bz - TREE_REACH, bx + 15 + TREE_REACH, bz + 15 + TREE_REACH, County.SKIRT);
        List<Road> roads = county.roadsNear(bx - 16, bz - 16, bx + 31, bz + 31);
        Column[] cols = new Column[256];
        int min = out.minY();

        for (int i = 0; i < 256; i++) {
            int x = bx + (i & 15), z = bz + (i >> 4);
            Column c = cols[i] = new Column();
            double natural = county.ground(x, z, places);
            double h = natural;
            Road.Hit hit = new Road.Hit();
            for (Road r : roads) {
                r.nearest(x + 0.5, z + 0.5, hit);
            }
            Place in = null;
            for (Place p : places) {
                if (p.contains(x, z, 0)) {
                    in = p;
                }
            }
            boolean bridge = false;
            if (hit.road != null && in == null) {
                int half = hit.road.kind.half;
                if (hit.distance <= half + 1.5) {
                    if (Terrain.isWater(natural) || natural < hit.y - 4) {
                        bridge = true;
                    } else {
                        h = hit.y;
                    }
                } else if (hit.distance <= half + 9) {
                    double w = Terrain.smooth(half + 9, half + 1.5, hit.distance);
                    h = Terrain.lerp(h, hit.y, w);
                }
            }
            int top = (int) Math.floor(h);
            c.top = top;
            c.water = top < Terrain.WATER;
            c.built = in != null;
            c.forest = t.forest(x, z);
            c.moisture = t.moisture(x, z);
            c.shore = !c.water && top <= Terrain.WATER + 1;
            // the planned city's quays are paved for a walk along the water; the open ground
            // further back, where the city's blocks stop short of the shore, is left as a park
            boolean paved = t.plan != null && !c.water && in == null && !bridge && (hit.road == null || hit.distance > hit.road.kind.half + 1.5)
                    && t.city(x, z) > 0.5 && t.plan.toWater(x + 0.5, z + 0.5) <= 16;
            if (paved) {
                c.built = true;
            }

            // rock, soil and the surface
            SplittableRandom rng = new SplittableRandom(Noise.hash(county.seed, x, z));
            out.set(x, min, z, "minecraft:bedrock");
            int dirt = 3 + rng.nextInt(2);
            out.column(x, z, min + 1, Math.min(top - dirt, -1), "minecraft:deepslate");
            out.column(x, z, Math.max(min + 1, 0), top - dirt, "minecraft:stone");
            out.column(x, z, top - dirt + 1, top - 1, c.water || c.shore ? "minecraft:mud" : "minecraft:dirt");
            out.set(x, top, z, paved ? (rng.nextDouble() < 0.12 ? "sidewalk_cracked" : "sidewalk") : surface(c, x, z, natural, top, rng));
            if (paved) {
                quayRail(out, x, z, top, places);
            }
            if (c.water) {
                out.column(x, z, top + 1, Terrain.WATER - 1, "minecraft:water");
            }

            if (bridge) {
                deck(out, hit, x, z, top, rng);
                c.road = true;
            } else if (hit.road != null && in == null && hit.distance <= hit.road.kind.half + 1.5) {
                road(out, hit, x, z, h, rng);
                c.road = true;
            }
        }

        // hideouts are copied in like places, but the land round them is left as it lies
        List<Place> hideouts = county.hideoutsNear(bx - TREE_REACH, bz - TREE_REACH, bx + 15 + TREE_REACH, bz + 15 + TREE_REACH, 0);
        for (Place p : places) {
            if (p.contains(bx, bz, 15) && p.x0 <= bx + 15 && p.z0 <= bz + 15) {
                copyPlace(p, bx, bz, out);
            }
        }
        for (Place p : hideouts) {
            if (p.contains(bx, bz, 15) && p.x0 <= bx + 15 && p.z0 <= bz + 15) {
                copyPlace(p, bx, bz, out);
            }
        }
        vegetation(bx, bz, cols, places, hideouts, out);
    }

    /**
     * What a place has in the column at (x, z), for trees reaching in from outside: {its level,
     * the place y of its highest built block (0 if none), 1 if its ground there is paved}; null
     * if no place is there after all.
     */
    private int[] placeColumn(List<Place> places, int x, int z) {
        for (Place p : places) {
            if (!p.contains(x, z, 0)) {
                continue;
            }
            Voxels v = cache.get(p, county);
            int lx = x - p.x0, lz = z - p.z0;
            int top = 0;
            for (int y = v.maxY(); y >= 1; y--) {
                String b = v.get(lx, y, lz);
                if (b != null && !Kit.soft(b) && !b.startsWith("tree_")) {
                    top = y;
                    break;
                }
            }
            return new int[] {p.level, top, Kit.soil(v.get(lx, 0, lz)) ? 0 : 1};
        }
        return null;
    }

    /** True within a hideout's clearing (reach blocks from its hatch). */
    private static boolean cleared(List<Place> hideouts, int x, int z, double reach) {
        for (Place p : hideouts) {
            if (Math.hypot(x - p.centreX(), z - p.centreZ()) <= reach) {
                return true;
            }
        }
        return false;
    }

    /** A railing along the quay's edge wherever the paving drops into the water. */
    private void quayRail(ChunkSink out, int x, int z, int top, List<Place> places) {
        if (county.terrain.plan.toWater(x + 0.5, z + 0.5) > 3) {
            return;
        }
        // which way the water lies, over a few blocks round, so a rail on a stepped or slanting
        // quay still runs along the edge instead of turning at every step
        boolean edge = false;
        int gx = 0, gz = 0;
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                if (Terrain.isWater(county.ground(x + dx, z + dz, places))) {
                    gx += dx;
                    gz += dz;
                    edge |= Math.abs(dx) + Math.abs(dz) == 1;
                }
            }
        }
        if (!edge || gx == 0 && gz == 0) {
            return;
        }
        String facing = Math.abs(gx) > Math.abs(gz) ? (gx > 0 ? "east" : "west") : (gz > 0 ? "south" : "north");
        out.set(x, top + 1, z, "railing[facing=" + facing + "]");
    }

    private String surface(Column c, int x, int z, double natural, int top, SplittableRandom rng) {
        Plan plan = county.terrain.plan;
        if (plan != null && (c.water || c.shore) && plan.coast(x, z) < 70 && plan.coast(x, z) > -220) {
            // the sea's beaches and the sand under the shallows
            return rng.nextDouble() < 0.08 ? "minecraft:gravel" : "minecraft:sand";
        }
        if (c.water) {
            double r = rng.nextDouble();
            return r < 0.5 ? "minecraft:mud" : r < 0.8 ? "minecraft:gravel" : "minecraft:clay";
        }
        if (c.shore) {
            return rng.nextDouble() < 0.6 ? "minecraft:mud" : "minecraft:coarse_dirt";
        }
        if (top > 92 && rng.nextDouble() < 0.35) {
            return rng.nextBoolean() ? "minecraft:stone" : "minecraft:andesite";
        }
        double r = rng.nextDouble();
        if (c.forest > 0.6) {
            return r < 0.25 ? "minecraft:podzol" : r < 0.35 ? "minecraft:coarse_dirt" : r < 0.4 ? "minecraft:moss_block" : "minecraft:grass_block";
        }
        return r < 0.05 ? "minecraft:coarse_dirt" : "minecraft:grass_block";
    }

    private static String axis(Road.Hit hit) {
        return Math.abs(hit.dx) > Math.abs(hit.dz) ? "x" : "z";
    }

    /** One column of road: asphalt with painted lines, or a dirt track, with gravel shoulders. */
    private void road(ChunkSink out, Road.Hit hit, int x, int z, double y, SplittableRandom rng) {
        Road.Kind kind = hit.road.kind;
        int top = (int) Math.floor(y);
        double s = Math.abs(hit.offset);
        double frac = y - top;
        // the road is the column's surface; a half-height block where it climbs between levels
        String block;
        if (s > kind.half + 0.5) {
            block = kind == Road.Kind.TRACK ? "minecraft:grass_block" : "minecraft:gravel";
        } else if (kind == Road.Kind.TRACK) {
            block = s < 0.7 ? "minecraft:grass_block" : rng.nextDouble() < 0.7 ? "minecraft:dirt_path" : "minecraft:coarse_dirt";
        } else {
            double along = hit.along;
            block = weathered(rng, kind == Road.Kind.HIGHWAY ? 0.12 : 0.22);
            if (s < 0.5 && (kind == Road.Kind.HIGHWAY || Math.floorMod((int) along, 10) < 5)) {
                block = rng.nextDouble() < 0.85 ? "road_line_yellow[axis=" + axis(hit) + "]" : block;
            } else if (kind == Road.Kind.HIGHWAY && Math.abs(s - 3.5) < 0.5 && Math.floorMod((int) along, 12) < 5) {
                block = rng.nextDouble() < 0.85 ? "road_line_white[axis=" + axis(hit) + "]" : block;
            }
        }
        out.set(x, top, z, block);
        out.set(x, top - 1, z, "minecraft:gravel");
        if (frac >= 0.5 && kind != Road.Kind.TRACK && s <= kind.half + 0.5) {
            out.set(x, top + 1, z, "asphalt_low");
        }
        // clear anything standing on the road (the terrain is already cut down to it)
        out.column(x, z, top + (frac >= 0.5 ? 2 : 1), top + 6, "air");
    }

    private static String weathered(SplittableRandom rng, double wear) {
        double r = rng.nextDouble();
        if (r > wear) {
            return "asphalt";
        }
        r /= wear;
        return r < 0.3 ? "asphalt_crack" : r < 0.55 ? "asphalt_b" : r < 0.8 ? "asphalt_patch" : "asphalt_moss";
    }

    /** A concrete bridge deck over water, with railings and a pier every so often. */
    private void deck(ChunkSink out, Road.Hit hit, int x, int z, int ground, SplittableRandom rng) {
        int y = (int) Math.floor(hit.y);
        double s = Math.abs(hit.offset);
        int half = hit.road.kind.half;
        out.set(x, y - 1, z, "concrete_dark");
        if (s > half + 0.5) {
            out.set(x, y, z, "concrete_dark");
            out.set(x, y + 1, z, "railing[facing=" + facing(hit) + "]");
        } else {
            out.set(x, y, z, hit.road.kind == Road.Kind.TRACK ? "minecraft:spruce_planks" : weathered(rng, 0.3));
        }
        if (Math.floorMod((int) hit.along, 24) < 2 && s < half - 1) {
            out.column(x, z, ground + 1, y - 2, "concrete_dark");
        }
    }

    private static String facing(Road.Hit hit) {
        boolean right = hit.offset > 0;
        if (Math.abs(hit.dx) > Math.abs(hit.dz)) {
            return (hit.dx > 0) == right ? "south" : "north";
        }
        return (hit.dz > 0) == right ? "west" : "east";
    }

    private void copyPlace(Place p, int bx, int bz, ChunkSink out) {
        Voxels v = cache.get(p, county);
        List<String> palette = v.palette();
        int lx0 = bx - p.x0, lz0 = bz - p.z0;
        for (int sx = lx0 >> 4; sx <= (lx0 + 15) >> 4; sx++) {
            for (int sz = lz0 >> 4; sz <= (lz0 + 15) >> 4; sz++) {
                v.forColumn(sx, sz, (x, y, z, id) -> {
                    int wx = x + p.x0, wz = z + p.z0;
                    if (wx >= bx && wx < bx + 16 && wz >= bz && wz < bz + 16) {
                        out.set(wx, y + p.level, wz, palette.get(id));
                    }
                });
            }
        }
        for (Voxels.Car car : v.cars) {
            double wx = car.x() + p.x0, wz = car.z() + p.z0;
            if (wx >= bx && wx < bx + 16 && wz >= bz && wz < bz + 16) {
                if (car.models() != null) {
                    out.vehicle(wx, car.y() + p.level, wz, car.yaw(), car.models(), car.seed());
                } else {
                    out.car(wx, car.y() + p.level, wz, car.yaw(), car.minLength(), car.maxLength(), car.seed());
                }
            }
        }
    }

    /** Woods, scrub, weeds and leaf litter everywhere nothing was built. */
    private void vegetation(int bx, int bz, Column[] cols, List<Place> places, List<Place> hideouts, ChunkSink out) {
        Map<Long, String> canopy = new HashMap<>();
        Map<Long, Boolean> isWood = new HashMap<>();
        Trees.Canopy c = new Trees.Canopy() {
            @Override
            public void wood(int x, int y, int z, String block) {
                if (x >= bx && x < bx + 16 && z >= bz && z < bz + 16) {
                    long k = key(x, y, z);
                    canopy.put(k, block);
                    isWood.put(k, true);
                }
            }

            @Override
            public void leaves(int x, int y, int z, String block) {
                if (x >= bx && x < bx + 16 && z >= bz && z < bz + 16) {
                    long k = key(x, y, z);
                    if (!isWood.containsKey(k)) {
                        canopy.putIfAbsent(k, block);
                    }
                }
            }
        };
        Terrain t = county.terrain;
        int g0 = Math.floorDiv(bx - TREE_REACH, TREE_GRID), g1 = Math.floorDiv(bx + 15 + TREE_REACH, TREE_GRID);
        int h0 = Math.floorDiv(bz - TREE_REACH, TREE_GRID), h1 = Math.floorDiv(bz + 15 + TREE_REACH, TREE_GRID);
        List<Road> roads = county.roadsNear(bx - 24, bz - 24, bx + 40, bz + 40);
        for (int gx = g0; gx <= g1; gx++) {
            for (int gz = h0; gz <= h1; gz++) {
                SplittableRandom rng = new SplittableRandom(Noise.hash(county.seed ^ 0x7EEEL, gx, gz));
                int x = gx * TREE_GRID + rng.nextInt(TREE_GRID), z = gz * TREE_GRID + rng.nextInt(TREE_GRID);
                double forest = t.forest(x, z);
                double chance = 0.03 + forest * 0.62;
                if (rng.nextDouble() > chance) {
                    continue;
                }
                boolean blocked = false;
                for (Place p : places) {
                    if (p.contains(x, z, 3)) {
                        blocked = true;
                    }
                }
                Road.Hit hit = new Road.Hit();
                for (Road r : roads) {
                    r.nearest(x, z, hit);
                }
                if (blocked || cleared(hideouts, x, z, County.CLEARING + 2) || (hit.road != null && hit.distance < hit.road.kind.half + 4)) {
                    continue;
                }
                double gr = county.ground(x, z, places);
                if (Terrain.isWater(gr) || gr <= Terrain.WATER + 0.5) {
                    continue;
                }
                boolean nearWater = t.river(x, z) > 0.2 || t.lake(x, z) < -0.24;
                Trees.Kind kind = Trees.pick(rng, forest, t.moisture(x, z), gr, nearWater);
                Trees.grow(c, kind, x, (int) Math.floor(gr) + 1, z, rng);
            }
        }
        // crowns keep clear of the roads (headroom over them) and give way to a place's buildings,
        // its paving and anything it put there
        Map<Integer, int[]> under = new HashMap<>();
        for (Map.Entry<Long, String> e : canopy.entrySet()) {
            long k = e.getKey();
            int x = (int) (k >> 38) << 6 >> 6, y = (int) (k >> 26 & 0xFFF) << 20 >> 20, z = (int) (k & 0x3FFFFFF) << 6 >> 6;
            int i = (z - bz) << 4 | (x - bx);
            Column col = cols[i];
            if (col.road && y < col.top + Kit.HEADROOM) {
                continue;
            }
            if (col.built) {
                int[] u = under.computeIfAbsent(i, j -> placeColumn(places, x, z));
                // u: the place's level, its highest built block (place y), whether its ground is paved
                if (u == null || y - u[0] <= u[1] || u[2] == 1 && y - u[0] < Kit.HEADROOM) {
                    continue;
                }
            }
            out.set(x, y, z, e.getValue());
        }

        // ground cover: grass and weeds, ferns and litter under trees, reeds at the water
        for (int i = 0; i < 256; i++) {
            Column col = cols[i];
            if (col.built || col.road || col.water) {
                continue;
            }
            int x = bx + (i & 15), z = bz + (i >> 4);
            if (canopy.containsKey(key(x, col.top + 1, z)) || cleared(hideouts, x, z, County.CLEARING)) {
                continue;
            }
            SplittableRandom rng = new SplittableRandom(Noise.hash(county.seed ^ 0x6A55L, x, z));
            double r = rng.nextDouble();
            int y = col.top + 1;
            if (col.shore) {
                if (r < 0.25) {
                    out.set(x, y, z, "minecraft:tall_grass[half=lower]");
                    out.set(x, y + 1, z, "minecraft:tall_grass[half=upper]");
                } else if (r < 0.35) {
                    out.set(x, y, z, "minecraft:sugar_cane");
                }
            } else if (col.forest > 0.55) {
                if (r < 0.18) {
                    out.set(x, y, z, "minecraft:leaf_litter[segment_amount=" + (1 + rng.nextInt(4)) + ",facing=" + DIRS[rng.nextInt(4)] + "]");
                } else if (r < 0.28) {
                    out.set(x, y, z, "minecraft:fern");
                } else if (r < 0.32) {
                    out.set(x, y, z, "minecraft:large_fern[half=lower]");
                    out.set(x, y + 1, z, "minecraft:large_fern[half=upper]");
                } else if (r < 0.36) {
                    out.set(x, y, z, "minecraft:bush");
                } else if (r < 0.42) {
                    out.set(x, y, z, "minecraft:short_grass");
                }
            } else {
                // abandoned fields: everything has gone to seed
                double tall = 0.25 + 0.3 * (1 - col.forest);
                if (r < tall) {
                    out.set(x, y, z, "minecraft:tall_grass[half=lower]");
                    out.set(x, y + 1, z, "minecraft:tall_grass[half=upper]");
                } else if (r < tall + 0.25) {
                    out.set(x, y, z, "minecraft:short_grass");
                } else if (r < tall + 0.27) {
                    out.set(x, y, z, "minecraft:bush");
                } else if (r < tall + 0.285) {
                    out.set(x, y, z, rng.nextBoolean() ? "minecraft:oxeye_daisy" : "minecraft:cornflower");
                } else if (r < tall + 0.30) {
                    out.set(x, y, z, "minecraft:short_dry_grass");
                }
            }
        }
    }

    private static final String[] DIRS = {"north", "east", "south", "west"};

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) (y & 0xFFF)) << 26 | ((long) z & 0x3FFFFFF);
    }
}
