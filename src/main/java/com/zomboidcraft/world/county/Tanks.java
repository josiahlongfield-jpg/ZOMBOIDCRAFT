package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * A tank hulk from the rg_ kit (tools/city/ranges.py), after refs/ranges: five blocks wide and eight
 * long, the tracks down each side, the hull between with its stepped glacis, the turret three by
 * three on top and the gun out over the nose. Built in the tank's own frame, nose to the north from
 * (x, z) its north-west corner, turned by quarters; a damaged one has lost track links, its gun or
 * its whole turret, blown off and lying beside it.
 */
final class Tanks {
    private Tanks() {
    }

    /**
     * A tank at (x, z) turned quarters clockwise from nose north, in finish v (sand, rust, burnt);
     * damage 0..1 the chance of each part gone.
     */
    static void tank(Placer world, int x, int z, int quarters, String v, double damage, SplittableRandom rng) {
        Placer p = world.turned(x, 0, z, quarters);
        String f = "_" + v;
        for (int k = 0; k < 8; k++) {
            for (int i : new int[] {0, 4}) {
                if (k > 0 && k < 7 && rng.nextDouble() < damage * 0.15) {
                    p.set(i, 1, k, "rg_frag");  // the track broken, its links lying
                    continue;
                }
                boolean end = k == 0 || k == 7;
                String face = k == 7 ? "south" : i == 4 && !end ? "south" : "north";
                p.set(i, 1, k, (end ? "rg_track_end" : "rg_track") + f + "[facing=" + face + "]");
            }
            for (int i = 1; i <= 3; i++) {
                p.set(i, 1, k, (k == 0 ? "rg_glacis" : "rg_hull") + f + "[facing=north]");
            }
        }
        boolean turretGone = rng.nextDouble() < damage * 0.3;
        int tx = 1, tz = 2;
        if (turretGone) {
            // blown off, lying upside down a few blocks off to the side
            tx = rng.nextBoolean() ? -4 : 6;
            tz = 1 + rng.nextInt(4);
        }
        int ty = turretGone ? 0 : 2;
        String[][] parts = {
            {"rg_turret_c" + f + "[facing=north]", "rg_turret_f" + f + "[facing=north]", "rg_turret_c" + f + "[facing=east]"},
            {"rg_turret_e" + f + "[facing=north]", "rg_turret_m" + f + "[facing=north]", "rg_turret_e" + f + "[facing=south]"},
            {"rg_turret_c" + f + "[facing=west]", "rg_turret_e" + f + "[facing=west]", "rg_turret_c" + f + "[facing=south]"}};
        for (int k = 0; k < 3; k++) {
            for (int i = 0; i < 3; i++) {
                if (turretGone) {
                    p.set(tx + i, 1, tz + k, "rg_hull" + f + "[facing=north]");
                } else {
                    p.set(tx + i, ty, tz + k, parts[k][i]);
                }
            }
        }
        if (turretGone) {
            p.set(2, 1, 3, "scorch");
            return;
        }
        // the gun out over the nose, or snapped off short
        int len = rng.nextDouble() < damage * 0.3 ? 1 + rng.nextInt(2) : 4;
        for (int k = 1; k > 1 - len; k--) {
            p.set(2, 2, k, "rg_barrel" + f + "[facing=north]");
        }
        if (len == 4) {
            p.set(2, 2, -3, "rg_muzzle" + f + "[facing=north]");
        }
    }
}
