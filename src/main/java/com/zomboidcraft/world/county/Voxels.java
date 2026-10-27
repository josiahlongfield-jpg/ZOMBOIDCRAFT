package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A place's blocks held in memory, in 16-block sections, once its builder has run: chunks copy
 * their share out of it as they generate. Coordinates are the place's own (y = 0 is its ground;
 * cellars and subways go below). Cars are kept as a list for the chunk that holds each one.
 */
public final class Voxels implements BlockSink {
    /** Lowest y a builder may write (basements, tunnels). */
    public static final int MIN_Y = -32;

    public record Car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed, String models) {}

    private final Map<Long, short[]> sections = new HashMap<>();
    private final List<String> palette = new ArrayList<>(List.of("air"));
    private final Map<String, Short> ids = new HashMap<>(Map.of("air", (short) 0));
    /** Which sections were written at all, so explicit air (cleared rooms) carves the terrain. */
    private final Map<Long, long[]> written = new HashMap<>();
    public final List<Car> cars = new ArrayList<>();
    /** Spots the builders marked: {@link BlockSink#mark}. */
    public final List<Mark> marks = new ArrayList<>();
    private int maxY = 0;

    public record Mark(String what, int x, int y, int z) {}

    static long key(int sx, int sy, int sz) {
        return ((long) sx & 0x1FFFFF) << 42 | ((long) sz & 0x1FFFFF) << 21 | ((long) sy & 0x1FFFFF);
    }

    @Override
    public void set(int x, int y, int z, String block) {
        if (y < MIN_Y) {
            return;
        }
        Short id = ids.get(block);
        if (id == null) {
            id = (short) palette.size();
            palette.add(block);
            ids.put(block, id);
        }
        long k = key(x >> 4, (y - MIN_Y) >> 4, z >> 4);
        short[] s = sections.get(k);
        if (s == null) {
            s = new short[4096];
            sections.put(k, s);
            written.put(k, new long[64]);
        }
        int i = ((y - MIN_Y) & 15) << 8 | (z & 15) << 4 | (x & 15);
        s[i] = id;
        written.get(k)[i >> 6] |= 1L << (i & 63);
        maxY = Math.max(maxY, y);
    }

    @Override
    public String get(int x, int y, int z) {
        if (y < MIN_Y) {
            return null;
        }
        short[] s = sections.get(key(x >> 4, (y - MIN_Y) >> 4, z >> 4));
        short id = s == null ? 0 : s[((y - MIN_Y) & 15) << 8 | (z & 15) << 4 | (x & 15)];
        return id == 0 ? null : palette.get(id);
    }

    @Override
    public void car(double x, int y, double z, float yaw, double minLength, double maxLength, long seed) {
        cars.add(new Car(x, y, z, yaw, minLength, maxLength, seed, null));
    }

    @Override
    public void vehicle(double x, int y, double z, float yaw, String models, long seed) {
        cars.add(new Car(x, y, z, yaw, 0, 99, seed, models));
    }

    @Override
    public void mark(String what, int x, int y, int z) {
        marks.add(new Mark(what, x, y, z));
    }

    public int maxY() {
        return maxY;
    }

    public List<String> palette() {
        return palette;
    }

    /**
     * Calls out for every block written in the 16x16 column of sections at (sx, sz) (place
     * coordinates divided by 16): explicit air included, as palette id 0.
     */
    public void forColumn(int sx, int sz, Visitor out) {
        for (int sy = 0; (sy << 4) + MIN_Y <= maxY; sy++) {
            long k = key(sx, sy, sz);
            short[] s = sections.get(k);
            if (s == null) {
                continue;
            }
            long[] w = written.get(k);
            for (int i = 0; i < 4096; i++) {
                if ((w[i >> 6] >>> (i & 63) & 1) != 0) {
                    out.visit((sx << 4) + (i & 15), (sy << 4) + MIN_Y + (i >> 8), (sz << 4) + (i >> 4 & 15), s[i]);
                }
            }
        }
    }

    /** Calls out for every block written anywhere, explicit air included (palette id 0). */
    public void forEach(Visitor out) {
        for (Map.Entry<Long, short[]> e : sections.entrySet()) {
            long k = e.getKey();
            int sx = (int) (k >> 42) << 11 >> 11, sz = (int) (k >> 21 & 0x1FFFFF) << 11 >> 11, sy = (int) (k & 0x1FFFFF);
            short[] s = e.getValue();
            long[] w = written.get(k);
            for (int i = 0; i < 4096; i++) {
                if ((w[i >> 6] >>> (i & 63) & 1) != 0) {
                    out.visit((sx << 4) + (i & 15), (sy << 4) + MIN_Y + (i >> 8), (sz << 4) + (i >> 4 & 15), s[i]);
                }
            }
        }
    }

    public interface Visitor {
        void visit(int x, int y, int z, short id);
    }
}
