package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The smugglers' way under the container yard (refs/port/shipping-containers, refs/bunker/
 * bunker-corridor). One box down in the middle of a stack, another still on top of it, its doors
 * on the narrow gap between two columns: from the aisles it is one box of thousands. What gives it
 * away is the power cable that comes out under its doors and runs into an open manhole in the gap,
 * and that a sealed one is chained and padlocked from the inside. In it, the cable runs along the
 * floor to a hatch cut through the slab at the far end; under the hatch a ladder drops to a lined
 * tunnel, cable trays and lamps along it and the stores they moved stacked down one side, which
 * runs off under the yard to the antechamber of a {@link Bunker} core dressed as
 * {@link Bunker.Kind#SHIP} (its rooms are more containers, buried).
 *
 * Draws into the port's frame from PortSite, with a random of its own so the rest of the terminal
 * is laid out exactly as it was before. The tunnel's length and the four ways are tried until the
 * whole of it stays inside the site and clear of the quay.
 */
final class ContainerTunnel {
    /** The antechamber's room in the bunker's frame runs x -1..2, z -5..-1 (as HatchSite's). */
    private static final int TX0 = -1, TX1 = 0;

    private ContainerTunnel() {
    }

    /**
     * Digs it under the box whose low (door) end is at (bx, 1, bz), 12 along x, in the port frame f
     * (w by d, the quay's land from z = minZ): true if it was built.
     */
    static boolean dig(Placer f, int bx, int bz, String colour, String above, long seed, int w, int d, int minZ) {
        SplittableRandom rng = new SplittableRandom(seed);
        double roll = rng.nextDouble();
        Bunker.State state = roll < 0.45 ? Bunker.State.SEALED : roll < 0.7 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int floor = -13 - rng.nextInt(3);
        long core = rng.nextLong();
        int len = 10 + rng.nextInt(12);
        int hx = bx + 10, hz = bz + 1;
        int[] order = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        for (int l : new int[] {len, len + 8, Math.max(8, len - 6)}) {
            int ze = -5 - l;
            for (int q : order) {
                int[] s = turn(q, TX0, ze);
                Placer probe = f.turned(hx - s[0], 0, hz - s[1], q);
                int[] box = new Bunker(probe, new SplittableRandom(core), Bunker.Kind.SHIP, state).bounds();
                if (!inside(probe, Math.min(box[0], -2) - 1, ze - 2, Math.max(box[2], 3) + 1, box[3] + 1, w, d, minZ)) {
                    continue;
                }
                Placer bp = f.turned(hx - s[0], floor, hz - s[1], q);
                Bunker bunker = new Bunker(bp, new SplittableRandom(core), Bunker.Kind.SHIP, state);
                HatchSite.antechamber(bp, rng, 4, state);
                bunker.build();
                tunnel(bp, ze, -floor, rng, state);
                box(f, bx, bz, colour, above, rng, state);
                f.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " container", bx - 1, 1, bz + 1);
                bp.mark("bunker door", 0, 1, -2);
                return true;
            }
        }
        return false;
    }

    private static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private static boolean inside(Placer bp, int x0, int z0, int x1, int z1, int w, int d, int minZ) {
        for (int[] k : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(k[0], k[1]);
            if (at[0] < 1 || at[1] < minZ || at[0] > w - 2 || at[1] > d - 2) {
                return false;
            }
        }
        return true;
    }

    private static String wall(SplittableRandom rng) {
        double r = rng.nextDouble();
        return r < 0.3 ? "bunk_concrete_damp" : r < 0.42 ? "bunk_concrete_crack" : "bunk_concrete";
    }

    /**
     * The tunnel from the antechamber's north wall (z = -6) to the foot of the shaft at z = ze, two
     * wide (x -1..0) and three high; the ladder up the shaft to the hatch at rise. The antechamber's
     * own ladder and hatch are taken out: this is its way in.
     */
    private static void tunnel(Placer bp, int ze, int rise, SplittableRandom rng, Bunker.State state) {
        boolean sealed = state == Bunker.State.SEALED;
        for (int z = ze - 1; z <= -6; z++) {
            for (int x = TX0 - 1; x <= TX1 + 1; x++) {
                for (int y = 0; y <= 4; y++) {
                    boolean room = z >= ze && x >= TX0 && x <= TX1 && y >= 1 && y <= 3;
                    bp.set(x, y, z, room ? "air" : y == 0 ? (rng.nextDouble() < 0.25 ? "bunk_floor_crack" : "bunk_floor_dirty") : wall(rng));
                }
            }
        }
        // the antechamber's own ladder and hatch go; the crate in the mouth is shifted out of the way
        for (int y = 1; y <= 3; y++) {
            bp.set(-1, y, -5, "air");
        }
        bp.set(-1, 4, -5, "bunk_concrete");
        bp.set(0, 1, -5, "air");
        bp.set(2, 2, -5, "bunk_crate_ration[facing=west]");

        // the shaft: lined round, the ladder on its north side, the hatch through the slab above
        for (int y = 4; y < rise; y++) {
            for (int x = TX0 - 1; x <= TX0 + 1; x++) {
                for (int z = ze - 1; z <= ze + 1; z++) {
                    bp.set(x, y, z, x == TX0 && z == ze ? "air" : wall(rng));
                }
            }
        }
        for (int y = 1; y < rise; y++) {
            bp.set(TX0, y, ze, "bunk_ladder[facing=south]");
        }
        bp.set(TX0, rise, ze, "bunk_hatch[facing=north,half=top,open=" + (state == Bunker.State.BREACHED) + "]");

        // the cable tray along the east side under the roof, lamps on the west wall, a vent;
        // the stores they ran through it stacked on the east side, the west kept clear to walk
        for (int z = ze + 1; z <= -6; z++) {
            bp.set(TX1, 3, z, "bunk_cable_tray[axis=z]");
            int k = z - ze;
            if (k % 6 == 3) {
                bp.set(TX0, 3, z, sealed ? "bunk_lamp_red_lit[facing=east]" : "bunk_lamp_red[facing=east]");
            }
            if (z <= -8 && k >= 2) {
                double r = rng.nextDouble();
                String s = r < 0.16 ? "bunk_rack_crates[facing=west]" : r < 0.28 ? "bunk_jerry_cans[facing=west]"
                        : r < 0.36 ? "bunk_drum_water" : r < 0.44 ? "bunk_crate_ammo[facing=west]" : null;
                if (s != null) {
                    bp.set(TX1, 1, z, s);
                }
            }
            if (rng.nextDouble() < 0.18) {
                bp.set(TX0, 1, z, "bunk_floor_drain");
            }
        }
        bp.set(TX1 + 1, 2, ze + 2, "bunk_vent[facing=west]");
        bp.set(TX0, 1, -6, sealed ? "bunk_tripwire[axis=x]" : "bunk_bootprints[facing=south]");
        if (state == Bunker.State.BREACHED) {
            bp.set(TX0, 1, ze + 3, "home_blood_drag[facing=south]");
            bp.set(TX1, 1, ze + 1, "blood_splat");
            bp.set(TX0, 2, -6 - 1, "bunk_scrawl_a[facing=east]");
        }
    }

    /**
     * The box itself, from (bx, 1, bz), 12 along x with its doors at the low end on the gap, and
     * another on top. Its middle hollowed through (a box is a solid core under its skin), the cable
     * along its floor, the hatch at its far end; the cable out under the doors into the manhole.
     */
    private static void box(Placer f, int bx, int bz, String colour, String above, SplittableRandom rng, Bunker.State state) {
        Boxes.place(f, bx, 1, bz, 12, colour, true, true, false);
        if (f.get(bx + 5, 5, bz + 1) == null) {
            Boxes.place(f, bx, 4, bz, 12, above, true, true, false);
        }
        int z = bz + 1;
        for (int i = 1; i <= 10; i++) {
            f.set(bx + i, 1, z, "air");
            f.set(bx + i, 2, z, "air");
            // the ply floor and the inside of the corrugations; a side only where the next box in
            // the stack hides its outside, the roof always (a box stands on it)
            if (i < 10) {
                f.set(bx + i, 0, z, "container_floor");   // the hatch is at the far end
            }
            f.set(bx + i, 3, z, "container_inside");
            for (int side : new int[] {-1, 1}) {
                String n = f.get(bx + i, 1, z + 2 * side);
                if (n != null && (n.startsWith("ct_") || n.startsWith("container_"))) {
                    f.set(bx + i, 1, z + side, "container_inside");
                    f.set(bx + i, 2, z + side, "container_inside");
                }
            }
        }
        boolean sealed = state == Bunker.State.SEALED;
        for (int i = 1; i <= 9; i++) {
            f.set(bx + i, 1, z, "tn_cable[axis=x]");
        }
        f.set(bx + 3, 1, z, "bunk_jerry_cans[facing=north]");
        f.set(bx + 6, 1, z, sealed ? "bunk_crate_ration[facing=north]" : "paper");
        f.set(bx + 5, 2, z, sealed ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
        if (sealed) {
            // chained from inside: the chain through the door bars, the padlock on it
            f.set(bx + 1, 2, z, "as_chain");
            f.set(bx + 1, 1, z, "sl_padlock[facing=east]");
        } else {
            // the lock cut off, the doors forced: the middle of the door gone, the filings
            f.set(bx, 1, z, "air");
            f.set(bx, 2, z, "air");
            f.set(bx + 1, 1, z, "bunk_grit");
        }
        if (state == Bunker.State.BREACHED) {
            f.set(bx + 8, 1, z, "blood_splat");
        }
        // out under the doors, along the gap and down the manhole with its cover off
        f.set(bx - 1, 1, z, "tn_cable[axis=z]");
        f.set(bx - 1, 1, z + 1, "sy_manhole[facing=north]");
        for (int y = 2; y <= 12; y++) {
            for (int dz = 0; dz <= 1; dz++) {
                String g = f.get(bx - 1, y, z + dz);
                if (g != null && !g.startsWith("ct_") && !g.startsWith("container_")) {
                    f.set(bx - 1, y, z + dz, "air");
                }
            }
        }
    }
}
