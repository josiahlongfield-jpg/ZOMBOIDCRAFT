package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gray Eagle 214, the three-engined jet parked at gate 1 (tools/city/airliner.py): its skin,
 * wings, engines, cabin and cockpit, block by block from data/zomboidcraft/airliner/trijet.txt. In
 * its own frame the nose is at z = 0 pointing north, the tail at z = 55, the captain's side (and
 * the doors used, L1 at z 10..11 and L2 at z 42..43, on the cabin floor at y = 7) to the west, R2
 * across from L2 on the east (the slide's door), x = 0 down the middle, y = 0 the apron's surface
 * block, so the wheels stand on y = 1.
 */
final class Airliner {
    /** The cabin floor's walking level, the doors' first z and the fuselage's west and east skins. */
    static final int FLOOR = 7, L1 = 10, L2 = 42, R2 = 42, WEST = -4, EAST = 3, LENGTH = 56, SPAN = 28;

    record Cell(int x, int y, int z, String block) {}

    private static volatile List<Cell> cells;

    private Airliner() {}

    private static List<Cell> cells() {
        List<Cell> c = cells;
        if (c == null) {
            synchronized (Airliner.class) {
                c = cells;
                if (c == null) {
                    c = new ArrayList<>();
                    try (InputStream in = Airliner.class.getResourceAsStream("/data/zomboidcraft/airliner/trijet.txt")) {
                        if (in == null) {
                            throw new IllegalStateException("airliner/trijet.txt is missing from the jar");
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

    // ------------------------------------------------------------------ the rest of the fleet

    /**
     * One of the fleet's planes (tools/city/fleet.py: twinjet, widebody, turboprop and bizjet, each
     * in its liveries), in the same frame as the trijet: the cabin floor's walking level, the open
     * doors' first z on the west and east sides, the skins' x (the cells the fuselage's sides are
     * in), nose to tail, the wing tips' reach either side.
     */
    record Plane(String name, int floor, int[] west, int[] east, int skinW, int skinE, int length, int span, List<Cell> cells) {
        /** The plane with its frame's origin at (x, y, z) of p. */
        void place(Placer p, int x, int y, int z) {
            for (Cell c : cells) {
                p.set(x + c.x(), y + c.y(), z + c.z(), c.block());
            }
        }
    }

    private static final Map<String, Plane> PLANES = new ConcurrentHashMap<>();

    /** The plane laid out in data/zomboidcraft/airliner/NAME.txt (e.g. "twinjet_coastal"). */
    static Plane plane(String name) {
        return PLANES.computeIfAbsent(name, Airliner::load);
    }

    private static int[] ints(String s) {
        if (s.equals("-")) {
            return new int[0];
        }
        String[] f = s.split(",");
        int[] out = new int[f.length];
        for (int i = 0; i < f.length; i++) {
            out[i] = Integer.parseInt(f[i]);
        }
        return out;
    }

    private static Plane load(String name) {
        List<Cell> c = new ArrayList<>();
        Map<String, String> head = new java.util.HashMap<>();
        try (InputStream in = Airliner.class.getResourceAsStream("/data/zomboidcraft/airliner/" + name + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("airliner/" + name + ".txt is missing from the jar");
            }
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                if (line.startsWith("#! ")) {
                    for (String kv : line.substring(3).split(" ")) {
                        String[] f = kv.split("=", 2);
                        head.put(f[0], f[1]);
                    }
                    continue;
                }
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split(" ", 4);
                c.add(new Cell(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3]));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't read the plane " + name, e);
        }
        int[] skin = ints(head.get("skin"));
        return new Plane(name, Integer.parseInt(head.get("floor")), ints(head.get("west")), ints(head.get("east")), skin[0], skin[1],
                Integer.parseInt(head.get("length")), Integer.parseInt(head.get("span")), List.copyOf(c));
    }
}
