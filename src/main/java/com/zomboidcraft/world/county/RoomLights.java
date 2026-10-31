package com.zomboidcraft.world.county;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Lights for every room (Josiah 2026-10-31): run over a place's blocks once its builder is done.
 * Every room lamp under a roof starts dark (data/zomboidcraft/lamps.txt, the pairs a switch turns);
 * every room with a door gets a light switch on the wall beside it, inside (lx_switch, the
 * interact key flips it: world/blocks/LightSwitch); a room with no lamp gets one in the middle of
 * its ceiling; and in the modern buildings (the city's towers, the hospital, mall, airport...) the
 * ceiling lights are all the blue cube lamps, a red one here and there.
 *
 * A room is what the game's switch will find: the open cells round the switch at the height of a
 * door's upper half, walled in by anything solid and by doors, with a ceiling over all of it.
 */
final class RoomLights {
    /** Rooms bigger than this (open ground, a hangar) get no switch. */
    static final int CELLS = 2500, RANGE = 40, CEILING = 12;
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final String[] FACING = {"north", "east", "south", "west"};
    private static Map<String, String> dark;
    private static Set<String> lamps;

    private final Voxels v;
    private final boolean modern;
    private final SplittableRandom rng;
    private final Map<Long, Integer> roomOf = new HashMap<>();

    private RoomLights(Place p, Voxels v) {
        this.v = v;
        this.modern = modern(p);
        this.rng = new SplittableRandom(p.seed ^ 0x116475L);
    }

    static void fit(Place p, Voxels v) {
        pairs();
        new RoomLights(p, v).run();
    }

    /** The city's glass and steel: blue cube lamps in it, some red. */
    static boolean modern(Place p) {
        if (p.type == PlaceType.DOWNTOWN) {
            return !p.variant.equals("old_city");
        }
        return p.type == PlaceType.LANDMARK && switch (p.variant.split("@")[0]) {
            case "mall", "airport", "mercy", "arena", "tower", "civic", "university", "broadcast" -> true;
            default -> false;
        };
    }

    private void run() {
        List<String> pal = v.palette();
        // every room lamp under a roof starts dark
        List<int[]> lit = new ArrayList<>(), doors = new ArrayList<>(), cubes = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            if (id == 0) {
                return;
            }
            String b = pal.get(id), base = base(b);
            if (modern && base.startsWith("ceiling_light")) {
                cubes.add(new int[] {x, y, z});
            } else if (dark.containsKey(base)) {
                lit.add(new int[] {x, y, z, id});
            } else if (b.contains("half=lower") && base.contains("door") && !base.contains("trapdoor")) {
                doors.add(new int[] {x, y, z, id});
            }
        });
        for (int[] c : cubes) {
            v.set(c[0], c[1], c[2], cube());
        }
        for (int[] l : lit) {
            if (covered(l[0], l[1], l[2], 24)) {
                String b = pal.get(l[3]);
                v.set(l[0], l[1], l[2], dark.get(base(b)) + b.substring(base(b).length()));
            }
        }
        for (int[] d : doors) {
            door(d[0], d[1], d[2], pal.get(d[3]));
        }
    }

    /** A switch on each side of a door that opens into a room, unless that room has one. */
    private void door(int x, int y, int z, String b) {
        int f = facing(b);
        if (f < 0) {
            return;
        }
        int ly = y + 1;
        for (int s = -1; s <= 1; s += 2) {
            int rx = DIRS[f][0] * s, rz = DIRS[f][1] * s;
            int cx = x + rx, cz = z + rz;
            if (v.get(cx, ly, cz) != null || roomOf.containsKey(key(cx, cz, ly))) {
                continue;
            }
            Set<Long> room = room(cx, ly, cz);
            if (room == null) {
                continue;
            }
            int id = roomOf.size() + 1;
            for (long c : room) {
                roomOf.put(c, id);
            }
            // beside the door against its wall, one cell into the room, on whichever side is clear
            int px = DIRS[(f + 1) & 3][0], pz = DIRS[(f + 1) & 3][1];
            boolean placed = false;
            for (int k : new int[] {1, -1, 2, -2}) {
                int wx = x + px * k, wz = z + pz * k, sx = wx + rx, sz = wz + rz;
                String wall = v.get(wx, ly, wz);
                if (wall != null && !wall.contains("door") && room.contains(key(sx, sz, ly)) && v.get(sx, ly, sz) == null) {
                    v.set(sx, ly, sz, "lx_switch[facing=" + FACING[dir(rx, rz)] + "]");
                    placed = true;
                    break;
                }
            }
            if (placed) {
                light(room, ly);
            }
        }
    }

    /** The room's lamps: modern ceiling lights to cube lamps; a ceiling light if it has none. */
    private void light(Set<Long> room, int ly) {
        boolean any = false;
        long sx = 0, sz = 0;
        for (long c : room) {
            int x = (int) (c >> 40), z = (int) (c << 24 >> 40);
            sx += x;
            sz += z;
            for (int y = ly - 2; y <= ly + CEILING; y++) {
                String b = v.get(x, y, z);
                if (b == null) {
                    continue;
                }
                String base = base(b);
                any |= lamps.contains(base);
                if (y > ly) {
                    break;
                }
            }
        }
        if (any) {
            return;
        }
        // the column nearest the middle, and the cell under its ceiling
        double mx = (double) sx / room.size(), mz = (double) sz / room.size();
        long best = 0;
        double near = Double.MAX_VALUE;
        for (long c : room) {
            double d = Math.hypot((int) (c >> 40) - mx, (int) (c << 24 >> 40) - mz);
            if (d < near) {
                near = d;
                best = c;
            }
        }
        int x = (int) (best >> 40), z = (int) (best << 24 >> 40);
        for (int y = ly + 1; y <= ly + CEILING; y++) {
            if (v.get(x, y, z) != null) {
                if (y - 1 > ly) {
                    v.set(x, y - 1, z, modern ? cube() : "ceiling_light_off");
                }
                return;
            }
        }
    }

    private String cube() {
        return rng.nextDouble() < 0.15 ? "lx_cube_red" : "lx_cube_blue";
    }

    /**
     * The open cells round (x, ly, z) the switch will light, or null when it isn't a room: no
     * ceiling over some of it, or bigger than {@link #CELLS}.
     */
    private Set<Long> room(int x0, int ly, int z0) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        seen.add(key(x0, z0, ly));
        queue.add(new int[] {x0, z0});
        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            if (!covered(c[0], ly, c[1], CEILING)) {
                return null;
            }
            for (int[] d : DIRS) {
                int x = c[0] + d[0], z = c[1] + d[1];
                long k = key(x, z, ly);
                if (seen.contains(k) || v.get(x, ly, z) != null) {
                    continue;
                }
                if (Math.abs(x - x0) > RANGE || Math.abs(z - z0) > RANGE || seen.size() >= CELLS) {
                    return null;
                }
                seen.add(k);
                queue.add(new int[] {x, z});
            }
        }
        return seen;
    }

    /** Whether something built (not a tree) is over the cell within up blocks. */
    private boolean covered(int x, int y, int z, int up) {
        for (int k = 1; k <= up; k++) {
            String b = v.get(x, y + k, z);
            if (b != null && !b.startsWith("tree_")) {
                return true;
            }
        }
        return false;
    }

    private static long key(int x, int z, int y) {
        return (long) x << 40 | ((long) z & 0xFFFFFF) << 16 | (y & 0xFFFF);
    }

    private static int dir(int dx, int dz) {
        return dz < 0 ? 0 : dx > 0 ? 1 : dz > 0 ? 2 : 3;
    }

    private static int facing(String b) {
        for (int i = 0; i < 4; i++) {
            if (b.contains("facing=" + FACING[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String base(String b) {
        int i = b.indexOf('[');
        return i < 0 ? b : b.substring(0, i);
    }

    private static synchronized void pairs() {
        if (dark != null) {
            return;
        }
        Map<String, String> off = new HashMap<>();
        Set<String> all = new HashSet<>();
        try (InputStream in = RoomLights.class.getResourceAsStream("/data/zomboidcraft/lamps.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    String[] f = line.trim().split(" ");
                    if (f.length == 2 && !line.startsWith("#")) {
                        off.put(f[1], f[0]);
                        all.add(f[0]);
                        all.add(f[1]);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        lamps = all;
        dark = off;
    }
}
