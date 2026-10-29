package com.zomboidcraft.world.city;

import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * The fire stair every tower core carries: a 3-wide well (inner x0..x0+2) behind a steel door in
 * the core's north wall at z = cz0. Each floor's landing is by the door (cz0+1..2); the far
 * landing is the row at cz0+8; the flights run between, one lane each side of a railed middle.
 *
 * <p>Flights take turns: up the west lane towards the back, then up the east lane towards the
 * door, never two in a row up the same lane. A lane is used only every other flight, so the next
 * tread over any tread is two flights (six blocks or more) up and nothing solid comes within four
 * blocks over a step: no bumped heads, even jumping. A flight climbs three to five steps, so a
 * storey of four is one flight, a lobby of eight two. A floor reached at the far landing has a
 * bridge down the middle back to its door.
 */
public final class Stairwell {
    private Stairwell() {}

    /** How a rise splits into flights of three to five steps (two for a rise of two or less). */
    public static int[] flights(int rise) {
        int k = Math.max(1, (rise + 4) / 5);
        int[] out = new int[k];
        for (int i = 0; i < k; i++) {
            out[i] = rise / k + (i < rise % k ? 1 : 0);
        }
        return out;
    }

    /** How many flights climb from ys[0] to the last level. */
    public static int count(int[] ys) {
        int n = 0;
        for (int i = 0; i + 1 < ys.length; i++) {
            n += flights(ys[i + 1] - ys[i]).length;
        }
        return n;
    }

    /**
     * The stair from level ys[0] up to the last one, every level a landing and a fire door.
     * eastFirst starts on the east lane (from the far landing, so ys[0]'s far row is laid too);
     * nextRise, if over 0, is the first flight of a stair carrying on above the last level, whose
     * start this lays (so a stair below a tower's can stop on its lobby). lit is the chance a
     * landing's light still works.
     */
    public static int[] build(Placer p, SplittableRandom rng, int x0, int cz0, int[] ys, boolean eastFirst, int nextRise, double lit) {
        int land = cz0 + 1, lanes = cz0 + 3, far = cz0 + 8;
        int top = ys[ys.length - 1];
        p.fill(x0, ys[0] + 1, land, x0 + 2, top, far, "air");
        Set<Integer> landings = new HashSet<>();
        Set<Integer> bridges = new HashSet<>();
        for (int y : ys) {
            landings.add(y);
        }
        boolean east = eastFirst;
        if (east) {
            p.fill(x0, ys[0], far, x0 + 2, ys[0], far, "floor_concrete");
            bridges.add(ys[0]);
        }
        int h = ys[0];
        int[] last = null;
        for (int i = 0; i + 1 < ys.length; i++) {
            for (int r : flights(ys[i + 1] - ys[i])) {
                if (!east) {
                    // from the door's end: a walk to the foot, steps up the west lane, the far row
                    walk(p, x0, h, lanes, far - 1 - r);
                    for (int s = 0; s < r; s++) {
                        p.set(x0, h + 1 + s, far - r + s, "stairs_concrete[facing=south]");
                    }
                    last = new int[] {x0, h + r, far - 1};
                    h += r;
                    p.fill(x0, h, far, x0 + 2, h, far, "floor_concrete");
                    if (h == ys[i + 1]) {
                        bridges.add(h);
                    }
                } else {
                    // from the far row: steps up the east lane, a walk on to the door's landing
                    for (int s = 0; s < r; s++) {
                        p.set(x0 + 2, h + 1 + s, far - 1 - s, "stairs_concrete[facing=north]");
                    }
                    last = new int[] {x0 + 2, h + r, far - r};
                    h += r;
                    walk(p, x0 + 2, h, lanes, far - 1 - r);
                    p.fill(x0, h, land, x0 + 2, h, land + 1, "floor_concrete");
                    landings.add(h);
                }
                east = !east;
            }
        }
        if (nextRise > 0) {
            if (!east) {
                walk(p, x0, top, lanes, far - 1 - nextRise);
            } else {
                p.fill(x0, top, far, x0 + 2, top, far, "floor_concrete");
            }
        }
        for (int i = 0; i < ys.length; i++) {
            int y = ys[i];
            p.fill(x0, y, land, x0 + 2, y, land + 1, "floor_concrete");
            Doors.abandoned(p, rng, x0 + 1, y + 1, cz0, Doors.STEEL, "south", Doors.hinge(rng));
            if (bridges.contains(y)) {
                p.fill(x0 + 1, y, lanes, x0 + 1, y, far - 1, "floor_concrete");
            }
            if (!landings.contains(y + 1) && !landings.contains(y + 2) && !landings.contains(y + 3)) {
                p.set(x0 + 1, y + 3, land + 1, rng.nextDouble() < lit ? "ceiling_light" : "ceiling_light_off");
            }
            if (i + 1 < ys.length) {
                for (int yy = y + (bridges.contains(y) ? 3 : 2); yy < ys[i + 1]; yy++) {
                    if (!bridges.contains(yy) && rng.nextDouble() < 0.9) {
                        p.set(x0 + 1, yy, lanes + 2, "railing[facing=west]");
                    }
                }
            }
        }
        return last;
    }

    /**
     * Four clear blocks over the top step (from {@link #build}) where the top floor's ceiling would
     * come down to three: a hole there capped a block higher.
     */
    public static void capTop(Placer p, int[] tread) {
        if (tread != null) {
            p.fill(tread[0], tread[1] + 1, tread[2], tread[0], tread[1] + 4, tread[2], "air");
            p.set(tread[0], tread[1] + 5, tread[2], "concrete_dark");
        }
    }

    /** Floor down one lane from z0 to z1, if there's any between. */
    private static void walk(Placer p, int x, int y, int z0, int z1) {
        if (z1 >= z0) {
            p.fill(x, y, z0, x, y, z1, "floor_concrete");
        }
    }
}
