package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The farming family's shelter under their old silo (refs/farm/silos: the Berea College farm, the
 * stave silos and grain bins at Skelton Banks and the Zander farm). From the yard it is only an old
 * silo nobody fills any more; what gives it away is the steel door cut into the foot of the drum
 * with a new brass padlock on its hasp, and the path worn to it through the grass. Inside, the old
 * silage has been shovelled back off the middle of the floor round a steel hatch, and under the
 * hatch a lined shaft drops to the antechamber in front of a {@link Bunker} core dressed as
 * {@link Bunker.Kind#FOREST} (the family lined the rooms they lived in with boards).
 *
 * Draws into the silo's own frame, from FarmSite, with a random of its own so the rest of the farm
 * is laid out exactly as it was before. The bunker runs off under the yard and fields, the four ways
 * tried until it stays inside the farm.
 */
final class SiloBunker {
    /** The shaft in the bunker's frame: the antechamber's north-west corner (as HatchSite's). */
    private static final int SX = -1, SZ = -5;

    private SiloBunker() {
    }

    /**
     * Digs the shelter under the silo centred on (cx, cz) of radius r in the frame f, if it fits in the
     * w by d place: true if it was built.
     */
    static boolean dig(Placer f, int cx, int cz, int r, long seed, int w, int d) {
        SplittableRandom rng = new SplittableRandom(seed);
        double roll = rng.nextDouble();
        Bunker.State state = roll < 0.5 ? Bunker.State.SEALED : roll < 0.75 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int floor = -13 - rng.nextInt(3);
        long core = rng.nextLong();
        int[] order = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        for (int q : order) {
            int[] s = turn(q, SX, SZ);
            Placer probe = f.turned(cx - s[0], 0, cz - s[1], q);
            int[] box = new Bunker(probe, new SplittableRandom(core), Bunker.Kind.FOREST, state).bounds();
            if (!inside(probe, box[0] - 1, SZ - 2, box[2] + 1, box[3] + 1, w, d)) {
                continue;
            }
            Placer bp = f.turned(cx - s[0], floor, cz - s[1], q);
            Bunker bunker = new Bunker(bp, new SplittableRandom(core), Bunker.Kind.FOREST, state);
            HatchSite.antechamber(bp, rng, -floor, state);
            bunker.build();
            silo(f, cx, cz, r, rng, state);
            f.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " silo", cx, 1, cz);
            bp.mark("bunker door", 0, 1, -2);
            return true;
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

    private static boolean inside(Placer bp, int x0, int z0, int x1, int z1, int w, int d) {
        for (int[] k : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(k[0], k[1]);
            if (at[0] < 1 || at[1] < 1 || at[0] > w - 2 || at[1] > d - 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * In the silo: the hatch in the middle of its floor (the shaft's top, already laid by the
     * antechamber), the old silage heaped back against the drum, a lamp on a hook; outside, the steel
     * door in the west side of the drum, its hasp and new padlock, and the path worn to it.
     */
    private static void silo(Placer f, int cx, int cz, int r, SplittableRandom rng, Bunker.State state) {
        for (int dx = -r + 1; dx <= r - 1; dx++) {
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                double k = Math.hypot(dx, dz);
                if (k > r - 0.3 || k < 1.2) {
                    continue;
                }
                // the silage shovelled back to the walls all round, a little trodden out toward the hatch
                if (k > r - 1.8 || rng.nextDouble() < 0.2) {
                    f.set(cx + dx, 1, cz + dz, "straw_floor");
                }
            }
        }
        f.set(cx + 1, 1, cz + 1, state == Bunker.State.SEALED ? "bunk_jerry_cans[facing=west]" : "paper");
        f.set(cx - 1, 3, cz - r + 1, state == Bunker.State.SEALED ? "bunk_lamp_red_lit[facing=south]" : "bunk_lamp_red[facing=south]");
        if (state == Bunker.State.BREACHED) {
            f.set(cx - 1, 1, cz, "blood_splat");
        }

        // the door they cut into the foot of the drum, its leaf on the outer face, and the lock on it
        int dx = cx - r;
        boolean cut = state != Bunker.State.SEALED;
        f.set(dx, 1, cz, "bunk_door_steel[facing=east,half=lower,hinge=left,open=" + cut + "]");
        f.set(dx, 2, cz, "bunk_door_steel[facing=east,half=upper,hinge=left,open=" + cut + "]");
        f.set(dx - 1, 1, cz, cut ? "bunk_grit" : "sl_padlock[facing=west]");
        // the path worn through the grass from the door, bending away toward the yard; the grass beside
        // it trodden short, so nothing tall grows up along it later
        for (int k = 1; k <= 10; k++) {
            int x = dx - k, z = cz - Math.max(0, k - 4) / 2;
            for (int side = -1; side <= 1; side++) {
                String g = f.get(x, 0, z + side);
                boolean soil = g == null || g.contains("grass") || g.contains("dirt") || g.contains("mud");
                if (!soil || f.get(x, 1, z + side) != null && !(k == 1 && side == 0)) {
                    continue;
                }
                if (side == 0) {
                    f.set(x, 0, z, rng.nextDouble() < 0.85 ? "minecraft:dirt_path" : "minecraft:coarse_dirt");
                    if (k > 1) {
                        f.set(x, 1, z, "air");
                    }
                } else {
                    f.set(x, 1, z + side, rng.nextDouble() < 0.3 ? "minecraft:short_grass" : "air");
                }
            }
        }
    }

    private static String face(SplittableRandom rng) {
        return new String[] {"north", "east", "south", "west"}[rng.nextInt(4)];
    }
}
