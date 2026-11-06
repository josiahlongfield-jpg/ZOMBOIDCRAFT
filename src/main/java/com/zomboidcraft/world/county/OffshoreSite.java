package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The ship at anchor (refs/ships/cargo-ship-exterior, refs/bunker/ship-cargo-hold-interior): a
 * container ship riding at one of the plan's anchorages off the coast, the only one there, named
 * on the marked maps that lead to her. Something gave way in her forward holds and she has
 * taken a list toward the side her pilot ladder hangs from, its foot
 * now almost in the water; the lower holds forward of the engine room are flooded to the sea's
 * level. Her crew never got off: they are still about her decks and in her house. Behind the false
 * plate in her engine room's forward bulkhead is the bunker the port ships have
 * ({@link BigShip}), and you get to it by swimming out and climbing the ladder.
 *
 * A hideout of its own (one in the county, like EXCHANGE HALL): nothing is levelled for it. Drawn
 * in her own frame (bow toward +x, the keel's line z = 0, heights from a quay's top), then copied
 * into the place sheared to her list, the marks and the people with her.
 */
final class OffshoreSite extends Site {
    /** The place's y = 0, as a quay's top: the sea's top block is four under it (Ships.SEA). */
    static final int LEVEL = Terrain.WATER + 3;
    private static final int BEAM = 40;
    /**
     * Her list, in bands across her: the middle sixteen blocks (her name across the stern, the
     * house's front) stand as built, the side toward +z is down STEP and the other side up STEP.
     * A smooth shear cut her name and every deck into steps; she is not trimmed for the same reason.
     */
    private static final int BAND = 16, STEP = 2;

    OffshoreSite(Place p, County county, BlockSink out) {
        super(p, out);
    }

    /**
     * The plan's anchorage she rides at: from one the seed picks, the first out in open water (the
     * sea deep under her bow, middle and stern, no place near: some of the plan's ships lie in the
     * ports' basins), or null if none is.
     */
    private static Plan.Ship anchorage(County county, long seed) {
        List<Plan.Ship> ships = Plan.get().ships;
        int n = ships.size();
        for (int i = 0; i < n; i++) {
            Plan.Ship s = ships.get((int) Math.floorMod(seed + i, (long) n));
            double a = Math.toRadians(s.angle()), half = length(s) / 2.0 + 10;
            boolean deep = true;
            for (int k = -1; k <= 1 && deep; k++) {
                deep = county.terrain.height(s.x() + Math.cos(a) * half * k, s.z() + Math.sin(a) * half * k) < Terrain.WATER - 10;
            }
            int r = (int) half + 40;
            if (deep && county.placesNear((int) s.x() - r, (int) s.z() - r, (int) s.x() + r, (int) s.z() + r, 0).isEmpty()) {
                return s;
            }
        }
        return null;
    }

    /** The plan's ship nearest (x, z). */
    private static Plan.Ship nearest(double x, double z) {
        Plan.Ship best = null;
        for (Plan.Ship s : Plan.get().ships) {
            if (best == null || Math.hypot(s.x() - x, s.z() - z) < Math.hypot(best.x() - x, best.z() - z)) {
                best = s;
            }
        }
        return best;
    }

    private static int length(Plan.Ship s) {
        return (int) Math.max(240, Math.min(300, s.length()));
    }

    /** The place round her, in the planned county; null in the endless one. */
    static Place place(County county, long seed) {
        if (!county.planned()) {
            return null;
        }
        Plan.Ship s = anchorage(county, seed);
        if (s == null) {
            return null;
        }
        int half = length(s) / 2 + 24;
        int cx = (int) Math.round(s.x()), cz = (int) Math.round(s.z());
        return new Place(PlaceType.HIDEOUT, "offshore_ship", cx - half, cz - half, cx + half, cz + half, LEVEL, seed, -1, -1);
    }

    /** Her name, as her bows and the marked maps give it. */
    static String name(Place p) {
        return BigShip.shipName(new SplittableRandom(p.seed ^ 0x5A11L));
    }

    @Override
    void build() {
        Plan.Ship s = nearest(p.centreX(), p.centreZ());
        if (s == null) {
            return;
        }
        SplittableRandom r = new SplittableRandom(p.seed ^ 0x0FF5L);
        double roll = r.nextDouble();
        Bunker.State state = roll < 0.45 ? Bunker.State.SEALED : roll < 0.7 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int len = length(s);
        Voxels v = new Voxels();
        BigShip ship = new BigShip(new Placer(v, 0, 0, 0, 0), r, 0.55, 0).named(name(p));
        ship.container(len, BEAM, state);
        crew(new Placer(v, 0, 0, 0, 0), r, len, state);

        // sheared to her list, flooding what's open under the sea forward of the engine room
        int dry = ship.dryEnd();
        Voxels sh = new Voxels();
        List<String> pal = v.palette();
        List<int[]> open = new ArrayList<>();
        v.forEach((x, y, z, id) -> {
            int y2 = y - drop(x, z, len);
            sh.set(x, y2, z, pal.get(id));
            if (id == 0 && y2 <= Ships.SEA && x > dry) {
                open.add(new int[] {x, y2, z});
            }
        });
        for (int[] c : open) {
            sh.set(c[0], c[1], c[2], "minecraft:water");
        }

        // into the place: her middle on its middle, turned to the anchorage's heading
        int q = Math.floorMod(Math.round((float) (s.angle() / 90.0)), 4);
        int cx = w / 2, cz = d / 2;
        int[] mid = turn(q, len / 2, 0);
        Placer f = new Placer(out, cx - mid[0], 0, cz - mid[1], q);
        List<String> sp = sh.palette();
        sh.forEach((x, y, z, id) -> f.set(x, y, z, sp.get(id)));
        for (Voxels.Mark m : v.marks) {
            String what = m.what().endsWith(" ship") ? m.what().substring(0, m.what().length() - 5) + " offshore" : m.what();
            f.mark(what, m.x(), m.y() - drop(m.x(), m.z(), len), m.z());
        }
        for (Voxels.Car c : v.cars) {
            int y = c.y() - drop((int) Math.floor(c.x()), (int) Math.floor(c.z()), len);
            if (c.models() != null) {
                f.vehicle(c.x(), y, c.z(), c.yaw(), c.models(), c.seed());
            }
        }
    }

    /** How far her list has put (x, z) down, in blocks. */
    private static int drop(int x, int z, int len) {
        return STEP * Math.floorDiv(z + BAND / 2, BAND);
    }

    private static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    /**
     * Her crew, in their boiler suits and the galley's whites: a few still on their feet along the
     * gangways either side of the boxes and in the house, a couple who went over on the deck. The
     * deck is 14 over the quay's top on a ship her size; the house's decks are three apart above it.
     */
    private static void crew(Placer v, SplittableRandom r, int len, Bunker.State state) {
        int deck = 14, gang = BEAM / 2 - 2;
        v.vehicle(len * 0.55 + 0.5, deck + 1, gang + 0.5, 0, "!walkers:construction;2;-2", r.nextLong());
        v.vehicle(len * 0.35 + 0.5, deck + 1, -gang + 0.5, 0, "!walkers:construction;2;-2", r.nextLong());
        v.vehicle(len * 0.75 + 0.5, deck + 1, -gang + 0.5, 0, "!walkers:construction;1;-2", r.nextLong());
        for (int k = 1; k <= 3; k++) {
            v.vehicle(14.5, deck + 3 * k + 1, 0.5, 0, "!walkers:" + (k == 1 ? "civilian" : "construction") + ";2;-3", r.nextLong());
        }
        for (int i = 0; i < 2; i++) {
            int x = (int) (len * (0.3 + 0.4 * r.nextDouble()));
            int z = (r.nextBoolean() ? 1 : -1) * gang;
            v.vehicle(x + 0.5, deck + 1, z + 0.5, (float) (r.nextDouble() * 360), "!body:construction;0;0", r.nextLong());
        }
    }
}
