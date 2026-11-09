package com.zomboidcraft.world.reach;

import com.zomboidcraft.ZomboidCraft;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The land between the Halo levels of the Reach world (Josiah 2026-11-09: one world whose missions flow into each other,
 * snow never beside beach or desert): a height map over the whole world, made by tools/reach/land.py from the levels'
 * own surrounding land and the approved climate map, read from config/zomboidcraft/reach/world/land.zrh on the player's
 * own computer. Inside a level's footprint the level's own walls (.zrv) are used instead; everywhere else the server
 * lays this ground as invisible walls and the client draws it.
 *
 * <p>File: 'ZRH1', int version 1, int samples per side n, int x of the first sample, int z of the first sample, int
 * metres between samples, int zlib length, then zlib data: n * n big-endian shorts (height in tenths of a metre, rows
 * running north to south, each row west to east), then n * n flag bytes: bits 0-1 the kind ({@link #LAND} new land,
 * {@link #MISSION} a level's own surrounding land, {@link #FOOTPRINT} a level's walls), bit 2 under the sea, bits 3-5
 * the climate (0 sea, 1 snow, 2 tundra, 3 green, 4 dry grass, 5 desert).
 */
public final class ReachLand {
    public static final int LAND = 0, MISSION = 1, FOOTPRINT = 2;
    public static final float SEA = -14f;

    private final int n, x0, z0, cell;
    private final short[] height;
    private final byte[] flags;

    private ReachLand(int n, int x0, int z0, int cell, short[] height, byte[] flags) {
        this.n = n;
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.height = height;
        this.flags = flags;
    }

    private static volatile ReachLand loaded;
    private static volatile boolean tried;

    /** The land in config/zomboidcraft/reach/world/land.zrh, read once, or null if there is none. */
    public static ReachLand get() {
        if (!tried) {
            synchronized (ReachLand.class) {
                if (!tried) {
                    loaded = read(ReachWorld.DIR.resolve("world").resolve("land.zrh"));
                    tried = true;
                }
            }
        }
        return loaded;
    }

    static ReachLand read(Path f) {
        if (!Files.isRegularFile(f)) {
            ZomboidCraft.LOGGER.info("Reach world: no land file {}, only the levels' own walls", f);
            return null;
        }
        try (DataInputStream in = new DataInputStream(Files.newInputStream(f))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'Z' || magic[1] != 'R' || magic[2] != 'H' || magic[3] != '1') {
                ZomboidCraft.LOGGER.warn("Not a Reach land file: {}", f);
                return null;
            }
            int version = in.readInt();
            if (version != 1) {
                ZomboidCraft.LOGGER.warn("Reach land file {} is version {}, wants 1", f, version);
                return null;
            }
            int n = in.readInt(), x0 = in.readInt(), z0 = in.readInt(), cell = in.readInt(), len = in.readInt();
            byte[] packed = new byte[len];
            in.readFully(packed);
            byte[] raw = inflate(packed, n * n * 3);
            ByteBuffer b = ByteBuffer.wrap(raw);
            short[] h = new short[n * n];
            b.asShortBuffer().get(h);
            byte[] fl = new byte[n * n];
            System.arraycopy(raw, n * n * 2, fl, 0, n * n);
            ZomboidCraft.LOGGER.info("Reach world: land {} x {} samples every {} m from {}, {}", n, n, cell, x0, z0);
            return new ReachLand(n, x0, z0, cell, h, fl);
        } catch (IOException | DataFormatException | RuntimeException e) {
            ZomboidCraft.LOGGER.error("Couldn't read the Reach land file {}", f, e);
            return null;
        }
    }

    private static byte[] inflate(byte[] packed, int size) throws DataFormatException {
        Inflater inf = new Inflater();
        inf.setInput(packed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);
        byte[] buf = new byte[1 << 16];
        while (!inf.finished()) {
            int k = inf.inflate(buf);
            if (k == 0 && (inf.needsInput() || inf.needsDictionary())) {
                break;
            }
            out.write(buf, 0, k);
        }
        inf.end();
        byte[] raw = out.toByteArray();
        if (raw.length != size) {
            throw new DataFormatException("land data is " + raw.length + " bytes, wants " + size);
        }
        return raw;
    }

    public int samples() {
        return n;
    }

    public int cell() {
        return cell;
    }

    public int firstX() {
        return x0;
    }

    public int firstZ() {
        return z0;
    }

    /** The height of sample (i east, j south) in metres. */
    public float sample(int i, int j) {
        i = Math.max(0, Math.min(n - 1, i));
        j = Math.max(0, Math.min(n - 1, j));
        return height[j * n + i] / 10f;
    }

    /** The flag byte of sample (i, j), or a footprint outside the map (nothing laid there). */
    public int flags(int i, int j) {
        if (i < 0 || j < 0 || i >= n || j >= n) {
            return FOOTPRINT;
        }
        return flags[j * n + i] & 0xFF;
    }

    public boolean covers(double x, double z) {
        return x >= x0 && z >= z0 && x <= x0 + (n - 1) * (double) cell && z <= z0 + (n - 1) * (double) cell;
    }

    /** The ground height at world (x, z), smoothly between samples. */
    public float height(double x, double z) {
        double fx = (x - x0) / cell, fz = (z - z0) / cell;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        float tx = (float) (fx - i), tz = (float) (fz - j);
        float a = sample(i, j), b = sample(i + 1, j), c = sample(i, j + 1), d = sample(i + 1, j + 1);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    /** The flags of the sample nearest world (x, z). */
    public int flagsAt(double x, double z) {
        return flags((int) Math.round((x - x0) / cell), (int) Math.round((z - z0) / cell));
    }

    public static int kind(int flags) {
        return flags & 3;
    }

    public static boolean wet(int flags) {
        return (flags & 4) != 0;
    }

    public static int climate(int flags) {
        return (flags >> 3) & 7;
    }
}
