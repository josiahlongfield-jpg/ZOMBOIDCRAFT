package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * The forest hatch: a survivalist's bunker under thick woods, far from any road. All that shows
 * is an olive steel hatch in a cracked concrete collar, half buried in leaves. What gives it away
 * is what the man left round it: a clearing of stumps where he felled the trees, his logs stacked
 * under a camouflage net, a rusty vent pipe standing out of the ground, a CABLE marker. Under the
 * hatch a ladder drops down a lined shaft to the antechamber in front of the blast door, and past
 * it is the {@link Bunker} core dressed as {@link Bunker.Kind#FOREST}.
 *
 * The hatch stands in the footprint's middle ({@link County#HIDE_REACH} in from each side), on
 * the ground the place is levelled to; nothing else is levelled, so every clue sits on the land
 * as it lies.
 */
final class HatchSite extends Site {
    /** The shaft in the bunker's frame: the antechamber's north-west corner. */
    private static final int SX = -1, SZ = -5;

    private final County county;
    private final int c = County.HIDE_REACH;

    HatchSite(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    /** The height of the ground's top block at a column of the footprint, in its own frame. */
    private int top(int x, int z) {
        return (int) Math.floor(county.terrain.height(p.x0 + x, p.z0 + z)) - p.level;
    }

    @Override
    void build() {
        double r = chance();
        Bunker.State state = r < 0.5 ? Bunker.State.SEALED : r < 0.75 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        Placer host = new Placer(out, 0, 0, 0, 0);
        int[] order = {0, 1, 2, 3};
        for (int i = 3; i > 0; i--) {
            int j = rng.nextInt(i + 1), t = order[i];
            order[i] = order[j];
            order[j] = t;
        }
        long seed = rng.nextLong();
        for (int q : order) {
            int[] s = turn(q, SX, SZ);
            Placer probe = host.turned(c - s[0], 0, c - s[1], q);
            int[] box = new Bunker(probe, new SplittableRandom(seed), Bunker.Kind.FOREST, state).bounds();
            if (!inside(probe, box[0] - 1, SZ - 2, box[2] + 1, box[3] + 1)) {
                continue;
            }
            // deep enough for the hatch's ladder and for plenty of earth over the lowest of the roof
            int lowest = Integer.MAX_VALUE;
            for (int x = Math.min(box[0], SX - 1); x <= Math.max(box[2], 3); x += 2) {
                for (int z = SZ - 1; z <= box[3]; z += 2) {
                    int[] w = probe.world(x, z);
                    lowest = Math.min(lowest, top(w[0], w[1]));
                }
            }
            int floor = Math.min(-13 - rng.nextInt(3), lowest - 11);
            if (floor < -27) {
                continue;
            }
            Placer bp = host.turned(c - s[0], floor, c - s[1], q);
            Bunker bunker = new Bunker(bp, new SplittableRandom(seed), Bunker.Kind.FOREST, state);
            antechamber(bp, -floor, state);
            bunker.build();
            clearing(bp, -floor, state);
            out.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " forest", c, 1, c);
            bp.mark("bunker door", 0, 1, -2);
            return;
        }
    }

    private static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private boolean inside(Placer bp, int x0, int z0, int x1, int z1) {
        for (int[] k : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(k[0], k[1]);
            if (at[0] < 1 || at[1] < 1 || at[0] > w - 2 || at[1] > d - 2) {
                return false;
            }
        }
        return true;
    }

    /**
     * In the bunker's frame: the antechamber in front of the blast door (x -1..2, z -5..-1), and the
     * lined shaft over its north-west corner with a ladder up its north wall to the hatch, rise
     * rows up. The owner kept his stores here, and in a sealed bunker his tripwire is still strung.
     */
    private void antechamber(Placer bp, int rise, Bunker.State state) {
        for (int x = -2; x <= 3; x++) {
            for (int z = -6; z <= -1; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean room = x >= -1 && x <= 2 && z >= -5 && y >= 1 && y <= 3;
                    String wall = rng.nextDouble() < 0.3 ? "bunk_concrete_damp" : rng.nextDouble() < 0.2 ? "bunk_concrete_crack" : "bunk_concrete";
                    bp.set(x, y, z, room ? "air" : y == 0 ? (rng.nextDouble() < 0.3 ? "bunk_floor_crack" : "bunk_floor_dirty") : wall);
                }
            }
        }
        for (int y = 4; y < rise; y++) {
            for (int x = SX - 1; x <= SX + 1; x++) {
                for (int z = SZ - 1; z <= SZ + 1; z++) {
                    bp.set(x, y, z, x == SX && z == SZ ? "air" : y > 4 && rng.nextDouble() < 0.25 ? "bunk_concrete_damp" : "bunk_concrete");
                }
            }
        }
        for (int y = 1; y < rise; y++) {
            bp.set(SX, y, SZ, "bunk_ladder[facing=south]");
        }
        // the hatch's leaf hinges on the ladder's wall, so it opens back against it
        bp.set(SX, rise, SZ, "bunk_hatch[facing=south,half=top,open=" + (state == Bunker.State.BREACHED) + "]");
        // the vent's grille, under the pipe that comes down through the earth (see clearing)
        bp.set(2, 3, -3, "bunk_vent[facing=west]");

        boolean sealed = state == Bunker.State.SEALED;
        bp.set(1, 3, -5, sealed ? "bunk_lamp_red_lit[facing=south]" : "bunk_lamp_red[facing=south]");
        bp.set(0, 1, -5, "bunk_crate_ration[facing=south]");
        bp.set(1, 1, -5, "bunk_jerry_cans[facing=south]");
        bp.set(2, 1, -5, "bunk_jerry_cans[facing=west]");
        bp.set(2, 1, -4, "cellar_shelf_preserves[facing=west]");
        bp.set(2, 1, -3, "cellar_shelf_cans[facing=west]");
        bp.set(SX, 1, -4, "bunk_hazmat[facing=east]");
        bp.set(SX, 2, -4, "bunk_hazmat_top[facing=east]");
        bp.set(SX, 2, -2, "bunk_radmeter[facing=east]");
        bp.set(2, 2, -2, "bunk_tally[facing=west]");
        bp.set(SX, 3, -1, "bunk_cobweb[facing=east]");
        bp.set(2, 3, -5, "bunk_cobweb[facing=south]");
        if (sealed) {
            // he shut himself in: the warning on the wall, the cans still strung across the way
            bp.set(2, 2, -1, "bunk_scrawl_a[facing=north]");
            bp.set(SX, 1, -2, "bunk_tripwire[axis=x]");
            bp.set(0, 1, -2, "bunk_tripwire[axis=x]");
            bp.set(1, 1, -2, "bunk_tripwire[axis=x]");
        } else {
            bp.set(0, 1, -3, "bunk_bootprints[facing=north]");
            bp.set(1, 1, -1, "paper");
            bp.set(SX, 1, -2, rng.nextBoolean() ? "glass_shards" : "bunk_grit");
        }
        if (state == Bunker.State.BREACHED) {
            bp.set(0, 1, -2, "home_blood_drag[facing=north]");
            bp.set(1, 1, -4, "blood_splat");
            bp.set(2, 2, -1, "bunk_scrawl_a[facing=north]");
        }
    }

    /**
     * Round the hatch, on the land as it lies: the collar, the leaves, the stumps of the trees he
     * felled, the log pile under its camouflage net, the vent's cap and the cable marker.
     */
    private void clearing(Placer bp, int rise, Bunker.State state) {
        // the collar, flush with the hatch, on concrete down to the shaft's lining
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                int x = c + dx, z = c + dz;
                out.set(x, 0, z, "bunk_collar");
                for (int y = -1; y >= -3; y--) {
                    out.set(x, y, z, "bunk_concrete");
                }
                for (int y = 1; y <= top(x, z); y++) {
                    out.set(x, y, z, "air");
                }
                if (chance() < 0.55) {
                    out.set(x, 1, z, chance() < 0.75 ? "leaf_litter" : "windfall");
                }
            }
        }
        if (state == Bunker.State.SEALED) {
            out.set(c, 1, c, "leaf_litter");
        }
        // the vent's cap, standing out of the ground over the antechamber
        int[] v = bp.world(2, -3);
        int vg = top(v[0], v[1]);
        out.set(v[0], vg + 1, v[1], "bunk_vent_stack");
        out.set(v[0], vg + 2, v[1], "bunk_vent_cap");
        for (int y = vg; y > -rise + 4; y--) {
            out.set(v[0], y, v[1], "bunk_vent_stack");
        }

        // the rest is laid out round the hatch, turned any way
        Placer cl = new Placer(out, c, 0, c, rng.nextInt(4));
        hide(cl);
        // stumps where he cleared the trees, on the far side from the hide
        int stumps = 4 + rng.nextInt(3);
        for (int i = 0; i < stumps * 3 && stumps > 0; i++) {
            int x = -6 + rng.nextInt(5), z = -6 + rng.nextInt(13);
            if (Math.abs(x) <= 2 && Math.abs(z) <= 2 || x * x + z * z > 40) {
                continue;
            }
            if (place(cl, x, z, "bunk_stump[facing=" + pick(FACINGS) + "]")) {
                stumps--;
            }
        }
        // a trunk he never got round to cutting up
        int lz = -5 + rng.nextInt(2);
        for (int x = -2; x <= 1; x++) {
            place(cl, x, lz, "bunk_log[axis=x]");
        }
        // the cable marker, out on the line the buried cable runs
        place(cl, 0, 6, "bunk_cable_post[facing=" + pick(FACINGS) + "]");
        // leaves, moss and a fern or two over everything else in the clearing
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (x * x + z * z > 46 || Math.abs(x) <= 1 && Math.abs(z) <= 1) {
                    continue;
                }
                double k = chance();
                String cover = k < 0.42 ? "leaf_litter" : k < 0.5 ? "windfall" : k < 0.58 ? "moss_carpet" : k < 0.62 ? "minecraft:fern" : null;
                if (cover != null) {
                    place(cl, x, z, cover);
                }
            }
        }
    }

    /** The hide: four poles and a camouflage net over his log piles, a tarp and a ration crate. */
    private void hide(Placer cl) {
        int x0 = 3, z0 = -2, x1 = 5, z1 = 1;
        int roof = Integer.MIN_VALUE;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                roof = Math.max(roof, ground(cl, x, z) + 3);
            }
        }
        for (int[] k : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            for (int y = ground(cl, k[0], k[1]) + 1; y < roof; y++) {
                cl.set(k[0], y, k[1], "bunk_pole");
            }
            cl.set(k[0], roof, k[1], "bunk_pole");
        }
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                boolean edge = x < x0 || x > x1 || z < z0 || z > z1;
                if (edge && chance() < 0.5) {
                    continue;
                }
                if (cl.get(x, roof, z) == null) {
                    // the edges sag a block lower
                    cl.set(x, edge ? roof - 1 : roof, z, "camo_net");
                }
            }
        }
        String[] under = {"bunk_logpile[facing=west]", "bunk_logpile[facing=west]", "tarp_green", "bunk_crate_ration[facing=west]"};
        int i = 0;
        for (int z = z0; z <= z1 && i < under.length; z++) {
            if (place(cl, x1 - 1, z, under[i])) {
                i++;
            }
        }
        place(cl, x0 + 1, z0, "bunk_logpile[facing=north]");
        place(cl, x0 + 1, z1, rng.nextBoolean() ? "bunk_jerry_cans[facing=west]" : "bunk_logpile[facing=south]");
    }

    private int ground(Placer cl, int x, int z) {
        int[] at = cl.world(x, z);
        return top(at[0], at[1]);
    }

    /** Sets a piece on the ground at (x, z) if nothing stands there yet. */
    private boolean place(Placer cl, int x, int z, String block) {
        int y = ground(cl, x, z) + 1;
        if (cl.get(x, y, z) != null || cl.get(x, y - 1, z) != null) {
            return false;
        }
        cl.set(x, y, z, block);
        return true;
    }
}
