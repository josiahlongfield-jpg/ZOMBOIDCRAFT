package com.zomboidcraft.world.reach;

import com.zomboidcraft.ZomboidCraft;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * The Reach world's invisible walls, read from the .zrv files that tools/reach/voxelize.py writes from each Halo
 * level's collision (config/zomboidcraft/reach/world, on the player's own computer: the files come from their own
 * copy of the game and are never shipped). Each file is already in world blocks; where two files reach the same
 * chunk, both are laid.
 *
 * <p>File layout: 'ZRV1', int version, int chunk count, then per chunk int cx, int cz, int offset, int length; each
 * chunk's zlib data is, for the 256 columns (lz * 16 + lx), a ushort run count and per run a short first y, a ushort
 * length and a byte top layer (1..16). Big-endian.
 */
public final class ReachVoxels {
    /** One solid run in one column: blocks y0 .. y0 + length - 1, the last one `top` sixteenths tall. */
    public record Run(int y0, int length, int top) {
    }

    private record Slot(Path file, long offset, int length) {
    }

    private final Map<Long, List<Slot>> chunks = new HashMap<>();
    private final int files;

    private ReachVoxels(Path dir) {
        int n = 0;
        if (Files.isDirectory(dir)) {
            try (var list = Files.list(dir)) {
                for (Path f : (Iterable<Path>) list.sorted()::iterator) {
                    if (f.getFileName().toString().endsWith(".zrv") && index(f)) {
                        n++;
                    }
                }
            } catch (IOException e) {
                ZomboidCraft.LOGGER.error("Couldn't list the Reach world files in {}", dir, e);
            }
        }
        files = n;
        ZomboidCraft.LOGGER.info("Reach world: {} level files, {} chunks of walls from {}", n, chunks.size(), dir);
    }

    private static volatile ReachVoxels loaded;

    /** The walls in config/zomboidcraft/reach/world, read once. */
    public static ReachVoxels get() {
        ReachVoxels v = loaded;
        if (v == null) {
            synchronized (ReachVoxels.class) {
                if (loaded == null) {
                    loaded = new ReachVoxels(ReachWorld.DIR.resolve("world"));
                }
                v = loaded;
            }
        }
        return v;
    }

    public int files() {
        return files;
    }

    public boolean has(int cx, int cz) {
        return chunks.containsKey(key(cx, cz));
    }

    private static long key(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xffffffffL);
    }

    private boolean index(Path f) {
        try (RandomAccessFile in = new RandomAccessFile(f.toFile(), "r")) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (magic[0] != 'Z' || magic[1] != 'R' || magic[2] != 'V' || magic[3] != '1') {
                ZomboidCraft.LOGGER.warn("Not a Reach world file: {}", f);
                return false;
            }
            in.readInt();   // version 1
            int count = in.readInt();
            for (int i = 0; i < count; i++) {
                int cx = in.readInt(), cz = in.readInt(), off = in.readInt(), len = in.readInt();
                chunks.computeIfAbsent(key(cx, cz), k -> new ArrayList<>(1)).add(new Slot(f, off, len));
            }
            return true;
        } catch (IOException e) {
            ZomboidCraft.LOGGER.error("Couldn't read the Reach world file {}", f, e);
            return false;
        }
    }

    /** The solid runs of each of the chunk's 256 columns (lz * 16 + lx), or null if the chunk has none. */
    public List<Run>[] columns(int cx, int cz) {
        List<Slot> slots = chunks.get(key(cx, cz));
        if (slots == null) {
            return null;
        }
        @SuppressWarnings("unchecked")
        List<Run>[] cols = new List[256];
        for (Slot s : slots) {
            try {
                byte[] packed = new byte[s.length];
                try (RandomAccessFile in = new RandomAccessFile(s.file.toFile(), "r")) {
                    in.seek(s.offset);
                    in.readFully(packed);
                }
                Inflater inf = new Inflater();
                inf.setInput(packed);
                byte[] buf = new byte[256 * 64];
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(buf.length);
                while (!inf.finished()) {
                    int n = inf.inflate(buf);
                    if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                        break;
                    }
                    out.write(buf, 0, n);
                }
                inf.end();
                DataInputStream d = new DataInputStream(new java.io.ByteArrayInputStream(out.toByteArray()));
                for (int c = 0; c < 256; c++) {
                    int runs = d.readUnsignedShort();
                    for (int r = 0; r < runs; r++) {
                        int y0 = d.readShort(), len = d.readUnsignedShort(), top = d.readUnsignedByte();
                        if (cols[c] == null) {
                            cols[c] = new ArrayList<>(2);
                        }
                        cols[c].add(new Run(y0, len, Math.max(1, Math.min(16, top))));
                    }
                }
            } catch (IOException | DataFormatException e) {
                ZomboidCraft.LOGGER.error("Couldn't read Reach chunk {} {} from {}", cx, cz, s.file, e);
            }
        }
        return cols;
    }
}
