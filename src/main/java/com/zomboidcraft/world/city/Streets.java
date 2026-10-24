package com.zomboidcraft.world.city;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Lays the street grid in world coordinates: 15-wide roads with a double yellow centre line and
 * dashed white lanes, crosswalks at every junction, sidewalks with a curb, and street lights
 * leaning out over the road. Road and sidewalk share y = ground: road blocks are two pixels
 * lower than a full block, which makes the curb without a step to jump.
 *
 * Add every road first, then call {@link #build()}: junctions are worked out from the overlaps.
 */
public final class Streets {
    public static final int HALF_ROAD = 7;
    public static final int SIDEWALK = 4;
    /** Half the width of a street from centre line to the outer edge of the sidewalk. */
    public static final int HALF_WIDTH = HALF_ROAD + SIDEWALK;

    private record Road(boolean alongZ, int centre, int from, int to) {}

    private final BlockSink sink;
    private final SplittableRandom rng;
    private final int y;
    private final List<Road> roads = new ArrayList<>();
    private final List<int[]> lamps = new ArrayList<>();

    public Streets(BlockSink sink, SplittableRandom rng, int groundY) {
        this.sink = sink;
        this.rng = rng;
        this.y = groundY;
    }

    /** A road running north-south along x = centre, from z0 to z1. */
    public Streets alongZ(int centre, int z0, int z1) {
        roads.add(new Road(true, centre, Math.min(z0, z1), Math.max(z0, z1)));
        return this;
    }

    /** A road running east-west along z = centre, from x0 to x1. */
    public Streets alongX(int centre, int x0, int x1) {
        roads.add(new Road(false, centre, Math.min(x0, x1), Math.max(x0, x1)));
        return this;
    }

    /** Street light heads placed by {@link #build()}, as {x, y, z}: where the light comes from. */
    public List<int[]> lamps() {
        return lamps;
    }

    private boolean inJunction(int x, int z) {
        int hits = 0;
        for (Road r : roads) {
            int across = r.alongZ ? x - r.centre : z - r.centre;
            int along = r.alongZ ? z : x;
            if (Math.abs(across) <= HALF_ROAD && along >= r.from && along <= r.to) {
                hits++;
            }
        }
        return hits > 1;
    }

    private boolean onRoad(int x, int z) {
        for (Road r : roads) {
            int across = r.alongZ ? x - r.centre : z - r.centre;
            int along = r.alongZ ? z : x;
            if (Math.abs(across) <= HALF_ROAD && along >= r.from - HALF_ROAD && along <= r.to + HALF_ROAD) {
                return true;
            }
        }
        return false;
    }

    /** How far (along the road) a point is from the nearest junction box, or a big number. */
    private int junctionDistance(Road r, int along) {
        int best = Integer.MAX_VALUE;
        for (Road o : roads) {
            if (o.alongZ == r.alongZ) {
                continue;
            }
            int crossAt = o.centre;
            if (r.centre < o.from - HALF_ROAD || r.centre > o.to + HALF_ROAD) {
                continue;
            }
            best = Math.min(best, Math.abs(along - crossAt) - HALF_ROAD);
        }
        return best;
    }

    public void build() {
        for (Road r : roads) {
            String axis = r.alongZ ? "z" : "x";
            for (int along = r.from - HALF_ROAD; along <= r.to + HALF_ROAD; along++) {
                int jd = junctionDistance(r, along);
                for (int across = -HALF_ROAD; across <= HALF_ROAD; across++) {
                    int x = r.alongZ ? r.centre + across : along;
                    int z = r.alongZ ? along : r.centre + across;
                    if (inJunction(x, z)) {
                        sink.set(x, y, z, "asphalt");
                        continue;
                    }
                    String block = "asphalt";
                    if (jd >= 1 && jd <= 3) {
                        // crosswalk stripes run with the traffic, so they cross the road
                        block = "crosswalk[axis=" + axis + "]";
                    } else if (across == 0) {
                        block = "road_line_yellow[axis=" + axis + "]";
                    } else if (Math.abs(across) == 4 && Math.floorMod(along, 6) < 3 && jd > 5) {
                        block = "road_line_white[axis=" + axis + "]";
                    }
                    sink.set(x, y, z, block);
                }
            }
        }
        for (Road r : roads) {
            for (int along = r.from - HALF_WIDTH; along <= r.to + HALF_WIDTH; along++) {
                for (int side = -1; side <= 1; side += 2) {
                    for (int k = 1; k <= SIDEWALK; k++) {
                        int across = side * (HALF_ROAD + k);
                        int x = r.alongZ ? r.centre + across : along;
                        int z = r.alongZ ? along : r.centre + across;
                        if (onRoad(x, z)) {
                            continue;
                        }
                        sink.set(x, y, z, k == 1 ? "curb" : rng.nextDouble() < 0.06 ? "sidewalk_cracked" : "sidewalk");
                    }
                }
            }
            lights(r);
        }
    }

    private void lights(Road r) {
        for (int side = -1; side <= 1; side += 2) {
            // stagger the two sides so the pools of light alternate down the street
            int offset = side < 0 ? 0 : 12;
            for (int along = r.from + offset; along <= r.to; along += 24) {
                if (junctionDistance(r, along) < 6) {
                    continue;
                }
                int poleAcross = side * (HALF_ROAD + 2);
                int px = r.alongZ ? r.centre + poleAcross : along;
                int pz = r.alongZ ? along : r.centre + poleAcross;
                for (int h = 1; h <= 7; h++) {
                    sink.set(px, y + h, pz, "streetlight_pole");
                }
                // the arm reaches back over the road; heads face back toward their pole
                String armFacing = r.alongZ ? "east" : "south";
                String headFacing = r.alongZ ? (side > 0 ? "west" : "east") : (side > 0 ? "north" : "south");
                for (int k = 1; k <= 3; k++) {
                    int across = poleAcross - side * k;
                    int x = r.alongZ ? r.centre + across : along;
                    int z = r.alongZ ? along : r.centre + across;
                    sink.set(x, y + 7, z, k < 3 ? "streetlight_arm[facing=" + armFacing + "]" : "streetlight_head[facing=" + headFacing + "]");
                    if (k == 3) {
                        // some bulbs died with the grid; the rest run off the last generators
                        if (rng.nextDouble() < 0.8) {
                            lamps.add(new int[] {x, y + 7, z});
                        } else {
                            sink.set(x, y + 7, z, "streetlight_head_off[facing=" + headFacing + "]");
                        }
                    }
                }
            }
        }
    }
}
