package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Gray Eagle 214, the twin-jet parked at gate 1 (tools/city/airliner.py): its skin, wings, engines,
 * cabin and cockpit, block by block from data/zomboidcraft/airliner/twinjet.txt. In its own frame
 * the nose is at z = 0 pointing north, the tail at z = 55, the captain's side (and the doors used,
 * L1 at z 10..11 and L2 at z 42..43, on the cabin floor at y = 7) to the west, x = 0 down the
 * middle, y = 0 the apron's surface block, so the wheels stand on y = 1.
 */
final class Airliner {
    /** The cabin floor's walking level, the doors' first z and the fuselage's west skin. */
    static final int FLOOR = 7, L1 = 10, L2 = 42, WEST = -4, LENGTH = 56, SPAN = 28;

    private record Cell(int x, int y, int z, String block) {}

    private static volatile List<Cell> cells;

    private Airliner() {}

    private static List<Cell> cells() {
        List<Cell> c = cells;
        if (c == null) {
            synchronized (Airliner.class) {
                c = cells;
                if (c == null) {
                    c = new ArrayList<>();
                    try (InputStream in = Airliner.class.getResourceAsStream("/data/zomboidcraft/airliner/twinjet.txt")) {
                        if (in == null) {
                            throw new IllegalStateException("airliner/twinjet.txt is missing from the jar");
                        }
                        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        for (String line; (line = r.readLine()) != null; ) {
                            if (line.isEmpty() || line.startsWith("#")) {
                                continue;
                            }
                            String[] f = line.split(" ", 4);
                            c.add(new Cell(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3]));
                        }
                    } catch (IOException e) {
                        throw new IllegalStateException("Couldn't read the airliner", e);
                    }
                    cells = c = List.copyOf(c);
                }
            }
        }
        return c;
    }

    /** The plane with its frame's origin at (x, y, z) of p (p's own turn turns the plane). */
    static void place(Placer p, int x, int y, int z) {
        for (Cell c : cells()) {
            p.set(x + c.x(), y + c.y(), z + c.z(), c.block());
        }
    }
}
