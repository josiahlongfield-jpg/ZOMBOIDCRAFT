package com.zomboidcraft.world.city;

/**
 * Writes blocks in a building's own coordinates. Every building is drawn with its front along
 * local z = 0 facing north (-z) and x running along the front; the placer turns it to face its
 * street, rotating "facing" and "axis" block properties to match.
 */
public final class Placer {
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final BlockSink sink;
    private final int ox, oy, oz, rot;

    /** rot: quarter turns clockwise seen from above; 0 faces north, 1 east, 2 south, 3 west. */
    public Placer(BlockSink sink, int ox, int oy, int oz, int rot) {
        this.sink = sink;
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.rot = rot & 3;
    }

    /**
     * A placer for a lot whose world footprint starts at (minX, minZ), for a building w wide
     * and d deep (in its own coordinates) whose front faces the given direction.
     */
    public static Placer forLot(BlockSink sink, int minX, int y, int minZ, int w, int d, int facing) {
        return switch (facing & 3) {
            case 0 -> new Placer(sink, minX, y, minZ, 0);
            case 1 -> new Placer(sink, minX + d - 1, y, minZ, 1);
            case 2 -> new Placer(sink, minX + w - 1, y, minZ + d - 1, 2);
            default -> new Placer(sink, minX, y, minZ + w - 1, 3);
        };
    }

    /** A placer drawing in the same turned frame, with its origin moved to local (dx, dy, dz). */
    public Placer shifted(int dx, int dy, int dz) {
        return switch (rot) {
            case 0 -> new Placer(sink, ox + dx, oy + dy, oz + dz, rot);
            case 1 -> new Placer(sink, ox - dz, oy + dy, oz + dx, rot);
            case 2 -> new Placer(sink, ox - dx, oy + dy, oz - dz, rot);
            default -> new Placer(sink, ox + dz, oy + dy, oz - dx, rot);
        };
    }

    /**
     * A placer whose own frame starts at local (dx, dy, dz) and is turned a further quarters
     * clockwise: draws a side or back wall as if it were a front (quarters 1 for the east side,
     * 2 the back, 3 the west side).
     */
    public Placer turned(int dx, int dy, int dz, int quarters) {
        Placer s = shifted(dx, dy, dz);
        return new Placer(sink, s.ox, s.oy, s.oz, rot + quarters);
    }

    public void set(int x, int y, int z, String block) {
        int wx, wz;
        switch (rot) {
            case 0 -> { wx = x; wz = z; }
            case 1 -> { wx = -z; wz = x; }
            case 2 -> { wx = -x; wz = -z; }
            default -> { wx = z; wz = -x; }
        }
        sink.set(ox + wx, oy + y, oz + wz, rotate(block, rot));
    }

    /**
     * A car centred on local (x, z) (block coordinates, so x + 0.5 is a block's middle), turned
     * to a local yaw (180 faces the front, north); see {@link BlockSink#car}.
     */
    public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
        double lx = x - 0.5, lz = z - 0.5, wx, wz;
        switch (rot) {
            case 0 -> { wx = lx; wz = lz; }
            case 1 -> { wx = -lz; wz = lx; }
            case 2 -> { wx = -lx; wz = -lz; }
            default -> { wx = lz; wz = -lx; }
        }
        sink.car(ox + wx + 0.5, oy + y, oz + wz + 0.5, yaw + 90 * rot, minLength, maxLength, seed);
    }

    /** One of the named models or kinds at local (x, z); see {@link BlockSink#vehicle}. */
    public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        double lx = x - 0.5, lz = z - 0.5, wx, wz;
        switch (rot) {
            case 0 -> { wx = lx; wz = lz; }
            case 1 -> { wx = -lz; wz = lx; }
            case 2 -> { wx = -lx; wz = -lz; }
            default -> { wx = lz; wz = -lx; }
        }
        sink.vehicle(ox + wx + 0.5, oy + y, oz + wz + 0.5, yaw + 90 * rot, models, seed);
    }

    /** An auto shop floor tool at local (x, z); see {@link BlockSink#shopTool}. */
    public void shopTool(double x, int y, double z, float yaw, String kind, long seed) {
        vehicle(x, y, z, yaw, "!tool:" + kind, seed);
    }

    /** Marks a local block for the sink (see {@link BlockSink#mark}). */
    public void mark(String what, int x, int y, int z) {
        int[] at = world(x, z);
        sink.mark(what, at[0], oy + y, at[1]);
    }

    /** The place (sink) coordinates of a local block, as {x, z}. */
    public int[] world(int x, int z) {
        return switch (rot) {
            case 0 -> new int[] {ox + x, oz + z};
            case 1 -> new int[] {ox - z, oz + x};
            case 2 -> new int[] {ox - x, oz - z};
            default -> new int[] {ox + z, oz - x};
        };
    }

    /** The block at a local position, or null for air (or when the sink can't tell). */
    public String get(int x, int y, int z) {
        int wx, wz;
        switch (rot) {
            case 0 -> { wx = x; wz = z; }
            case 1 -> { wx = -z; wz = x; }
            case 2 -> { wx = -x; wz = -z; }
            default -> { wx = z; wz = -x; }
        }
        return sink.get(ox + wx, oy + y, oz + wz);
    }

    /** Places a block only where there is nothing yet (vines, weeds and other dressing). */
    public void setIfAir(int x, int y, int z, String block) {
        String there = get(x, y, z);
        if (there == null || there.equals("air")) {
            set(x, y, z, block);
        }
    }

    /** Fills a box, both corners included. */
    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) {
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    set(x, y, z, block);
                }
            }
        }
    }

    /** The window pieces that turn by an axis: written bare, the game stands them along z. */
    private static final java.util.Set<String> AXIS_WINDOWS = java.util.Set.of("window", "window_lit", "window_broken",
            "window_empty", "boarded_window", "shutter");

    /**
     * A window set in a wall running along x (alongX) or along z, so its glass faces out of the
     * wall: adds the axis to a bare window piece and leaves anything else as it is.
     */
    public static String inWall(String block, boolean alongX) {
        return block.indexOf('[') < 0 && AXIS_WINDOWS.contains(block) ? block + "[axis=" + (alongX ? "x" : "z") + "]" : block;
    }

    public static String rotate(String block, int rot) {
        int open = block.indexOf('[');
        if (open < 0 || rot == 0) {
            return block;
        }
        StringBuilder out = new StringBuilder(block.substring(0, open + 1));
        String[] props = block.substring(open + 1, block.length() - 1).split(",");
        for (int i = 0; i < props.length; i++) {
            String[] kv = props[i].split("=", 2);
            String value = kv[1];
            if (kv[0].equals("facing")) {
                for (int d = 0; d < 4; d++) {
                    if (DIRS[d].equals(value)) {
                        value = DIRS[(d + rot) & 3];
                        break;
                    }
                }
            } else if (kv[0].equals("axis") && (rot & 1) == 1) {
                value = value.equals("x") ? "z" : value.equals("z") ? "x" : value;
            }
            out.append(i > 0 ? "," : "").append(kv[0]).append('=').append(value);
        }
        return out.append(']').toString();
    }
}
