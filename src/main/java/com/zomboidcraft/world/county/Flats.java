package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The flats over the shops on a main street and in the city's walk-ups: each upper floor a home
 * of its own, planned and furnished room by room like a house ({@link Interior}) behind the
 * building's own windows, and the way up to them: a stack of wooden back porches with a stair
 * switching back between them, the kind that hangs off the back of old brick shop rows.
 */
public final class Flats {
    /** The porch floor: no boards missing, as a gap here is a fall of a whole storey. */
    private static final String DECK = "ext_porch_boards";

    private Flats() {
    }

    /** What's on an upper floor off a stairwell's hall: builds floor k (slab at y), returns the x of its door in the hall wall. */
    public interface Upstairs {
        int floor(int k, int y);
    }

    /**
     * Plans and furnishes one floor of a building w wide (x along the front, z back from the front
     * wall at z = 0, the floor's slab at y) as a flat running from the front wall back to wall zb,
     * the way in through that wall. Reads the building's windows off its walls, so it must be
     * built after them. Returns the x of the flat's door in wall zb (the caller hangs it).
     */
    public static int floor(Placer p, SplittableRandom rng, int w, int zb, int y, int sill, double lit) {
        // the planner draws a house whose front is the wall it is entered by: turned to face back
        Placer q = p.turned(w - 1, y, zb, 2);
        List<int[]> windows = new ArrayList<>();
        for (int x = 1; x < w - 1; x++) {
            if (window(p.get(x, y + sill, 0))) {
                windows.add(new int[] {w - 1 - x, zb, 2});
            }
            if (window(p.get(x, y + sill, zb))) {
                windows.add(new int[] {w - 1 - x, 0, 0});
            }
        }
        for (int z = 1; z < zb; z++) {
            if (window(p.get(0, y + sill, z))) {
                windows.add(new int[] {w - 1, zb - z, 1});
            }
            if (window(p.get(w - 1, y + sill, z))) {
                windows.add(new int[] {0, zb - z, 3});
            }
        }
        double r = rng.nextDouble();
        House.Condition cond = r < 0.45 ? House.Condition.LOOTED : r < 0.65 ? House.Condition.SHUT
                : r < 0.8 ? House.Condition.BOARDED : r < 0.9 ? House.Condition.BURNT : House.Condition.RUIN;
        House.Style style = rng.nextDouble() < 0.6 ? House.Style.SHOTGUN : House.Style.BUNGALOW;
        Interior in = new Interior(q, rng, style, cond, 0, w - 1, 0, zb, 1, w >= 10 && zb >= 9, false, lit)
                .asFlat(windows, sill);
        in.plan();
        in.build();
        return w - 1 - in.doorX();
    }

    private static boolean window(String block) {
        if (block == null) {
            return false;
        }
        String name = block.indexOf('[') < 0 ? block : block.substring(0, block.indexOf('['));
        return name.equals("window") || name.equals("window_lit") || name.equals("window_broken")
                || name.equals("window_empty") || name.equals("boarded_window");
    }

    /**
     * Wooden porches up the back wall (z = d - 1) of a building w wide with floors storeys of
     * height storey, one at each upper floor's level, and a stair switching back between them
     * from the yard. doors[k] is the x of floor k's back door (k from 1); each porch reaches it.
     * With toRoof the stair climbs one flight more to a landing level with a flat roof at
     * floors * storey, through a gap in the parapet onto it. Takes three blocks of yard behind
     * the wall, and needs w of at least 8.
     */
    public static void porches(Placer p, SplittableRandom rng, int w, int d, int floors, int storey, int[] doors, boolean toRoof) {
        // the flights run between landings at a (west) and a + 5 (east), in two lanes behind the walk by the doors
        int a = 1 + rng.nextInt(Math.max(1, w - 7));
        int walk = d, inner = d + 1, outer = d + 2;
        boolean worn = rng.nextDouble() < 0.5;
        int last = toRoof ? floors : floors - 1;     // the level the stair climbs to
        int topY = last * storey;
        for (int k = 0; k < last; k++) {
            int y = k * storey, py = y + storey;
            // even floors climb east up the outer lane, odd ones west up the inner
            boolean east = k % 2 == 0;
            int lane = east ? outer : inner;
            String step = "ext_stairs_wood[facing=" + (east ? "east" : "west") + "]";
            for (int i = 0; i < 3; i++) {
                p.set(east ? a + 1 + i : a + 4 - i, y + 1 + i, lane, step);
            }
            // the porch at the top, holed where the flight comes up through it
            boolean roof = k + 1 == floors;      // the landing by the roof: the way on is a gap in the parapet
            int door = roof ? (east ? a + 4 : a + 1) : doors[k + 1];
            int lo = Math.max(0, Math.min(a - 1, door - 1)), hi = Math.min(w - 1, Math.max(a + 6, door + 1));
            boolean climbs = k + 1 < last;  // a flight goes on up from it
            if (roof) {
                for (int x = door; x <= door + 1; x++) {
                    for (int r = 1; r <= 3; r++) {
                        p.set(x, py + r, d - 1, "air");
                    }
                }
            }
            for (int x = lo; x <= hi; x++) {
                p.set(x, py, walk, DECK);
                boolean back = x >= a - 1 && x <= a + 6;
                if (back) {
                    for (int z : new int[] {inner, outer}) {
                        if (z == lane && x == (east ? a + 4 : a + 1)) {
                            p.set(x, py, z, step);
                        } else if (!(z == lane && (x == a + 2 || x == a + 3))) {
                            p.set(x, py, z, DECK);
                        }
                    }
                }
            }
            // rails round the open edges, clear of the posts, the flights and the holes they climb through
            for (int x = a; x <= a + 5; x++) {
                boolean free = east ? x != a + 2 && x != a + 3 : !(climbs && x >= a + 1 && x <= a + 3);
                if (free) {
                    rail(p, rng, x, py + 1, outer, "south", worn);
                }
            }
            for (int x = lo; x <= hi; x++) {
                if (x < a - 1 || x > a + 6 || !east && (x == a + 2 || x == a + 3)) {
                    rail(p, rng, x, py + 1, walk, "south", worn);
                }
            }
            for (int z = walk; z <= inner; z++) {
                rail(p, rng, z == walk ? lo : a - 1, py + 1, z, "west", worn);
                rail(p, rng, z == walk ? hi : a + 6, py + 1, z, "east", worn);
            }
            // a lamp on the wall by the door, now and then still lit
            if (!roof && rng.nextDouble() < 0.5 && door + 1 <= hi) {
                p.set(door + 1, py + 2, walk, (rng.nextDouble() < 0.15 ? "ext_sconce_lit" : "ext_sconce") + "[facing=south]");
            }
        }
        // the two posts the porches hang from, at the far corners, up from the yard to the top rail
        for (int yy = 1; yy <= topY + 1; yy++) {
            if (yy % storey == 0) {
                continue;
            }
            p.set(a - 1, yy, outer, yy == 1 ? "ext_post_base" : "ext_post");
            p.set(a + 6, yy, outer, yy == 1 ? "ext_post_base" : "ext_post");
        }
    }

    /**
     * The stairwell at the back of a walk-up w wide and d deep (z = d - 1 its back wall), floors
     * storeys of height storey with the shop on the ground floor: a hall along the flats' back
     * wall (z = zb, where zb = d - 5) and a stair switching back between landings at its east end,
     * so the back door (x = w - 2) opens onto the foot of it. Then a flat on each floor above,
     * its door onto the hall. Build after the shell and the shop; clears what the shop left there.
     */
    public static void stairwell(Placer p, SplittableRandom rng, int w, int d, int floors, int storey, double lit, String hutWall) {
        stairwell(p, rng, w, d, floors, storey, lit, hutWall, (k, y) -> floor(p, rng, w, d - 5, y, 1, lit));
    }

    /**
     * The same stairwell with something else on the floors above (a hotel's rooms): upstairs
     * builds each. With a hutWall the stair climbs on through the flat roof at floors * storey
     * (build the roof first) into a hut of that wall with a door out onto the roof.
     */
    public static void stairwell(Placer p, SplittableRandom rng, int w, int d, int floors, int storey, double lit,
                                 String hutWall, Upstairs upstairs) {
        int zb = d - 5, hall = d - 4, inner = d - 3, outer = d - 2;
        int a = w - 7;  // the west landing; the flights run a + 1 .. a + 4, the east landing a + 5 = w - 2
        String paper = rng.nextBoolean() ? "home_wall_cream" : rng.nextBoolean() ? "home_wall_sage" : "home_wall_stripe";
        String tread = "home_stairs_oak";
        for (int k = 0; k < floors; k++) {
            int y = k * storey;
            // the hall and the stair lanes, cleared; on the ground floor only behind the landings
            int x0 = k == 0 ? (a - 1 >= 3 ? a : 1) : 1;
            // the flight that comes up into this floor: its lane, and its top step at floor level
            int inLane = (k - 1) % 2 == 0 ? outer : inner, inTop = (k - 1) % 2 == 0 ? a + 4 : a + 1;
            for (int x = x0; x <= w - 2; x++) {
                for (int z = hall; z <= outer; z++) {
                    for (int r = 1; r < storey; r++) {
                        p.set(x, y + r, z, "air");
                    }
                    boolean through = z == inLane && (x == inTop || x == a + 2 || x == a + 3);
                    if (k > 0 && !through) {
                        p.set(x, y, z, "home_floor_oak");
                    }
                }
            }
            if (k == 0 && a - 1 >= 3) {
                // the shop's back room ends at a wall with a door through to the stairs
                for (int z = hall; z <= outer; z++) {
                    for (int r = 1; r < storey; r++) {
                        p.set(a - 1, y + r, z, wall(paper, x0, z, r));
                    }
                }
                Doors.abandoned(p, rng, a - 1, y + 1, hall, Doors.FLAT, "west", Doors.hinge(rng));
            }
            if (k > 0 && inLane == inner) {
                // a banister between the hall and the stairwell's hole
                p.set(a + 2, y + 1, hall, "ext_rail_edge_dark[facing=south]");
                p.set(a + 3, y + 1, hall, "ext_rail_edge_dark[facing=south]");
            }
            if (k > 0) {
                int door = upstairs.floor(k, y);
                for (int x = 1; x <= w - 2; x++) {
                    for (int r = 1; r < storey; r++) {
                        p.set(x, y + r, zb, wall(paper, x, k, r));
                    }
                }
                Doors.abandoned(p, rng, door, y + 1, zb, Doors.FLAT, "north", Doors.hinge(rng));
                // the dead end of the hall past the stairs: what the tenants kept out there
                for (int x = 1; x < a; x++) {
                    if (rng.nextDouble() < 0.35) {
                        p.set(x, y + 1, outer, rng.nextBoolean() ? "cardboard_box[facing=north]" : "home_boxes");
                    }
                }
                p.set(a + 2 + rng.nextInt(2), y + storey - 1, hall, rng.nextDouble() < lit + 0.08 ? "ceiling_light" : "ceiling_light_off");
            }
            if (k == floors - 1 && hutWall == null) {
                continue;
            }
            // the flight up: even floors climb east up the outer lane, odd ones west up the inner
            boolean east = k % 2 == 0;
            int lane = east ? outer : inner;
            String step = tread + "[facing=" + (east ? "east" : "west") + "]";
            for (int i = 0; i < 3; i++) {
                p.set(east ? a + 1 + i : a + 4 - i, y + 1 + i, lane, step);
            }
            int up = y + storey;
            p.set(east ? a + 4 : a + 1, up, lane, step);
            p.set(a + 2, up, lane, "air");
            p.set(a + 3, up, lane, "air");
        }
        if (hutWall != null) {
            // the last flight comes up through the roof at its east end (or its west): the doors there
            boolean east = (floors - 1) % 2 == 0;
            RoofHut.build(p, rng, a - 1, zb, w - 1, d - 1, floors * storey, hutWall, east ? a + 4 : a, "north");
        }
    }

    private static String wall(String paper, int x, int z, int row) {
        long h = x * 73856093L ^ z * 19349663L ^ row * 83492791L;
        return paper + ((h >>> 7) % 5 == 0 ? "_worn" : "");
    }

    /** A rail along one edge of a porch cell (facing that edge), now and then broken or gone. */
    private static void rail(Placer p, SplittableRandom rng, int x, int y, int z, String edge, boolean worn) {
        if (worn && rng.nextDouble() < 0.15) {
            return;
        }
        p.set(x, y, z, (rng.nextDouble() < 0.12 ? "ext_rail_edge_broken" : "ext_rail_edge") + "[facing=" + edge + "]");
    }
}
