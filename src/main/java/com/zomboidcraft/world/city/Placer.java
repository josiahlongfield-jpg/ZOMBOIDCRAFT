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
