package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * A power district's substation ({@link PowerGrid}), after refs/power: the city yards like the 230 kV switchyard
 * by its control house (lattice gantries over the transformers and breakers, chain-link and razor wire, the pale
 * control house with its vents on the roof), the town yards smaller with the brick kiosk and its green double
 * doors, the hamlets an H-pole with the transformer on its platform over a kiosk on a fenced pad.
 * <p>
 * Every part is there for the grid's story (LOOT owns the shutoff and the restore): the transformers and the
 * breakers are what fails and what a skilled player brings back; the control house holds the relay panels,
 * the battery racks, the fuse board, the standby generator and the reels of copper a dead yard is worth
 * scavenging for. Built in the lot's frame: x across, z back from the road at z = 0, the gate in the front fence.
 */
final class Substation {
    private final Placer q;
    private final SplittableRandom rng;
    private final PowerGrid.District district;
    /** How hard the end hit the yard (0..1): breaches in the fence, scorch, weeds. */
    private final double hit;

    Substation(Placer q, SplittableRandom rng, PowerGrid.District district, double hit) {
        this.q = q;
        this.rng = rng;
        this.district = district;
        this.hit = hit;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private void set(int x, int y, int z, String b) {
        q.set(x, y, z, b);
    }

    /** The yard for the lot's size and the district's kind. */
    void build(int w, int d) {
        switch (district.kind()) {
            case CITY -> city(Math.min(w, 60), Math.min(d, 58));
            case TOWN -> town(Math.min(w, 44), Math.min(d, 38));
            default -> hamlet(Math.min(w, 28), Math.min(d, 22));
        }
        q.mark("substation " + district.id(), w / 2, 1, 2);
    }

    // ------------------------------------------------------------------ the city yard

    private void city(int w, int d) {
        int x0 = 1, x1 = w - 2, z0 = 4, z1 = d - 2, gate = w / 2 - 3;
        yardGround(x0, z0, x1, z1);
        apron(gate - 1, gate + 7, 0, z0);
        fence(x0, z0, x1, z1, gate, gate + 6);
        controlHouse(4, 7, 19, 17);
        // the two main transformers on their pads behind the house, a firewall between them
        int tz = 23;
        int[] xs = {w * 2 / 5 + 1, w * 3 / 4};
        for (int xc : xs) {
            transformer(xc, tz, 3);
        }
        int fx = (xs[0] + xs[1]) / 2;
        for (int z = tz - 3; z <= tz + 5; z++) {
            for (int y = 1; y <= 8; y++) {
                set(fx, y, z, y == 8 ? "cc_coping" : "concrete_panel");
            }
        }
        // the gantries across the yard, the lines strung back between them, the breakers and switches under
        int g0 = tz + 9, g1 = z1 - 4;
        gantry(x0 + 4, x1 - 4, g0, 12, 9);
        gantry(x0 + 4, x1 - 4, g1, 12, 9);
        wires(x0 + 4, x1 - 4, g0, g1, 10, 9);
        for (int bx = x0 + 8; bx <= x1 - 6; bx += 9) {
            breaker(bx, (g0 + g1) / 2);
        }
        busOnPosts(x0 + 6, x1 - 6, g0 + 2, 7);
        // the line leaving the back of the yard
        lineTower(w / 2, z1 + 0, 18);
        floodlight(x0 + 2, z0 + 2);
        floodlight(x1 - 2, z0 + 2);
        floodlight(x1 - 2, z1 - 2);
        floodlight(x0 + 2, z1 - 2);
        // the crew's truck where the end found them, a cable drum, the spill kit
        q.vehicle(gate + 3.5, 1, z0 + 9, (float) (chance() < 0.5 ? 0 : 180), "pickup,step_van,van@" + (chance() < 0.6 ? "abandoned" : "wrecked"), rng.nextLong());
        set(x1 - 4, 1, z0 + 3, "pw_reel[axis=z]");
        set(x1 - 4, 1, z0 + 5, "pw_reel[axis=z]");
        set(x1 - 6, 1, z0 + 3, "bunk_jerry_cans[facing=north]");
        weeds(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0.05 + hit * 0.05);
    }

    /** The control house (refs/power/substation-control-house): pale block, sash windows, the vents on its flat roof. */
    private void controlHouse(int ax, int az, int bx, int bz) {
        Bldg b = new Bldg(q, rng);
        b.shell(ax, az, bx, bz, 1, 5, "sh_cmu_tan", "floor_concrete", "floor_concrete");
        b.flat(ax, az, bx, bz, 5, "sh_cmu_tan", 1, "stone_trim");
        for (int x = ax + 2; x < bx - 1; x += 3) {
            if (x != ax + 5 && x != ax + 6) {
                set(x, 2, az, (chance() < 0.25 + hit * 0.3 ? "pw_sash_broken" : "pw_sash") + "[facing=north]");
            }
        }
        for (int z = az + 2; z < bz - 1; z += 3) {
            set(bx, 2, z, (chance() < 0.25 + hit * 0.3 ? "pw_sash_broken" : "pw_sash") + "[facing=east]");
        }
        b.door(ax + 5, 1, az, "flat_door", "north");
        sign("relay", ax + 7, 3, az - 1, "north");
        sign("danger", ax + 2, 3, az - 1, "north");
        // the relay panels in two rows, the mimic board on the back wall, the annunciators over the desk
        for (int x = ax + 2; x <= bx - 2; x++) {
            set(x, 1, az + 4, "pw_vb_lo[facing=north]");
            set(x, 2, az + 4, "pw_vb_hi[facing=north]");
        }
        for (int x = ax + 4; x <= bx - 2; x++) {
            set(x, 1, az + 7, "pw_vb_lo[facing=south]");
            set(x, 2, az + 7, "pw_vb_hi[facing=south]");
        }
        for (int c = 0; c < 6; c++) {
            set(bx - 2 - c, 3, bz - 1, "pw_mimic_" + c + "[facing=north]");
        }
        set(ax + 1, 1, az + 2, "pw_desk[facing=east]");
        set(ax + 1, 2, az + 3, "pw_annun[facing=east]");
        set(ax + 2, 1, az + 2, "office_chair[facing=west]");
        // the battery racks and the fuse board by the back door, the standby set in the corner, copper on reels
        for (int x = ax + 1; x <= ax + 5; x += 2) {
            set(x, 1, bz - 1, "bunk_rack_boxes[facing=north]");
        }
        set(ax + 1, 2, bz - 2, "bunk_fusebox[facing=east]");
        set(bx - 1, 1, bz - 2, "bunk_gen_engine[facing=west]");
        set(bx - 1, 1, bz - 3, "bunk_gen_alternator[facing=west]");
        set(bx - 2, 1, bz - 1, "pw_reel[axis=x]");
        set(bx - 1, 1, az + 2, "bunk_jerry_cans[facing=west]");
        for (int x = ax + 2; x < bx; x += 4) {
            for (int z = az + 2; z < bz; z += 4) {
                q.setIfAir(x, 4, z, "fit_tube[axis=x]");
            }
        }
    }

    // ------------------------------------------------------------------ the town yard

    private void town(int w, int d) {
        int x0 = 1, x1 = w - 2, z0 = 4, z1 = d - 2, gate = w - 12;
        yardGround(x0, z0, x1, z1);
        apron(gate - 1, gate + 6, 0, z0);
        fence(x0, z0, x1, z1, gate, gate + 5);
        kiosk(4, z0 + 3, 12, z0 + 10);
        int tc = w / 2 + 6;
        transformer(tc, z0 + 10, 2);
        int gz = z1 - 6;
        gantry(x0 + 4, x1 - 4, gz, 10, 8);
        for (int bx = x0 + 8; bx <= x1 - 6; bx += 10) {
            breaker(bx, gz - 4);
        }
        busOnPosts(x0 + 6, x1 - 6, gz + 2, 6);
        poleLine(w / 2, z1, z1 + 12);
        floodlight(x1 - 2, z0 + 2);
        floodlight(x0 + 2, z1 - 2);
        set(gate + 2, 1, z0 + 3, "pw_reel[axis=x]");
        weeds(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0.07 + hit * 0.06);
    }

    // ------------------------------------------------------------------ the hamlet's pole and kiosk

    private void hamlet(int w, int d) {
        int x0 = 2, x1 = w - 3, z0 = 5, z1 = d - 2;
        yardGround(x0, z0, x1, z1);
        apron(x0 + 2, x0 + 6, 0, z0);
        fence(x0, z0, x1, z1, x0 + 3, x0 + 5);
        kiosk(x0 + 2, z0 + 3, x0 + 8, z0 + 8);
        hPole(x1 - 6, z1 - 3);
        poleLine(x1 - 6, z1 - 3, z1 + 14);
        weeds(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0.12 + hit * 0.06);
        // the hedge grown up round the pole's feet (refs/power/rural-substation 01)
        for (int dx = -3; dx <= 3; dx++) {
            if (chance() < 0.6) {
                q.setIfAir(x1 - 6 + dx, 1, z1 - 2, chance() < 0.5 ? "weeds_tall" : "grass_tuft");
            }
        }
    }

    /** The H-pole: two wooden legs, the crossarm with its insulators, the transformer on its platform halfway up. */
    private void hPole(int x, int z) {
        for (int y = 1; y <= 10; y++) {
            set(x - 2, y, z, "pole_wood");
            set(x + 2, y, z, "pole_wood");
        }
        for (int dx = -2; dx <= 2; dx++) {
            set(x + dx, 11, z, "pole_top[axis=x]");
            set(x + dx, 6, z, "pw_girt[axis=x]");
        }
        set(x - 1, 12, z, "pw_bushing");
        set(x + 1, 12, z, "pw_bushing");
        set(x, 12, z, "pw_bushing_lo");
        set(x, 7, z, "pl_transformer[facing=north]");
        set(x - 1, 7, z, "pl_transformer[facing=north]");
        set(x, 9, z, "pw_ins_string");
        sign("danger", x - 2, 3, z - 1, "north");
    }

    // ------------------------------------------------------------------ the pieces

    /** A brick kiosk (refs/power/city-substation 01): green double doors in the gable end, a tiled gable roof. */
    private void kiosk(int ax, int az, int bx, int bz) {
        Bldg b = new Bldg(q, rng);
        b.shell(ax, az, bx, bz, 1, 4, "brick_red", "floor_concrete", "floor_concrete");
        b.gable(ax, bx, az, bz, 4, "minecraft:deepslate_tile", true, "brick_red", 1);
        int mid = (ax + bx) / 2;
        b.doors(mid - 1 + ((bx - ax) % 2 == 0 ? 1 : 0), 1, az, "ext_door_green", "north");
        sign("danger", mid + 1, 3, az - 1, "north");
        // inside: the switchgear cabinets, the fuse board, the meter, a reel of cable
        for (int x = ax + 1; x < bx; x++) {
            if (x != mid && x != mid + 1) {
                q.setIfAir(x, 1, bz - 1, "pw_vb_lo[facing=north]");
            }
        }
        q.setIfAir(ax + 1, 2, az + 2, "bunk_fusebox[facing=east]");
        q.setIfAir(bx - 1, 1, az + 1, "pw_reel[axis=z]");
        q.setIfAir(mid, 3, (az + bz) / 2, "fit_tube[axis=x]");
    }

    /** A transformer on its pad, its radiator fins down the sides, bushings and the conservator on top. */
    private void transformer(int xc, int zc, int r) {
        for (int x = xc - r - 2; x <= xc + r + 2; x++) {
            for (int z = zc - r - 1; z <= zc + r + 1; z++) {
                set(x, 0, z, "concrete_dark");
            }
        }
        int top = r + 2;
        for (int x = xc - r; x <= xc + r; x++) {
            for (int z = zc - r + 1; z <= zc + r - 1; z++) {
                for (int y = 1; y <= top; y++) {
                    set(x, y, z, "pw_xfmr");
                }
            }
        }
        for (int z = zc - r + 1; z <= zc + r - 1; z++) {
            for (int y = 1; y <= top; y++) {
                set(xc - r - 1, y, z, "pw_fins[facing=west]");
                set(xc + r + 1, y, z, "pw_fins[facing=east]");
            }
        }
        for (int dx = -r + 1; dx <= r - 1; dx += Math.max(1, r - 1)) {
            set(xc + dx, top + 1, zc - r + 1, "pw_bushing");
            set(xc + dx, top + 2, zc - r + 1, "pw_bushing");
            set(xc + dx, top + 1, zc + r - 1, "pw_bushing_lo");
        }
        for (int x = xc - r + 1; x <= xc + r - 1; x++) {
            set(x, top + 1, zc, "pw_conservator[axis=x]");
        }
        sign("danger", xc - 1, 2, zc - r - 1 + (r > 2 ? 0 : 0), "north");
        // the night it went: soot up one side, the oil it lost on the pad
        if (chance() < 0.4 + hit * 0.4) {
            q.setIfAir(xc + r + 2, 1, zc, "bunk_scorch[facing=north]");
            q.setIfAir(xc - r - 2, 1, zc + 1, "bunk_puddle");
        }
    }

    /** A breaker on its plinth with a disconnect switch on a post either side of it. */
    private void breaker(int bx, int bz) {
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int z = bz - 1; z <= bz + 1; z++) {
                set(x, 0, z, "concrete_dark");
            }
        }
        set(bx, 1, bz, "pw_breaker[facing=north]");
        for (int sz : new int[] {bz - 3, bz + 3}) {
            for (int y = 1; y <= 4; y++) {
                set(bx, y, sz, "pw_leg");
            }
            set(bx, 5, sz, "pw_disconnect[axis=z]");
        }
    }

    /** A lattice gantry across x0..x1 at z: columns every `every`, the beam on top, insulator strings hung from it. */
    private void gantry(int x0, int x1, int z, int h, int every) {
        for (int x = x0; x <= x1; x += every) {
            for (int y = 1; y < h; y++) {
                set(x, y, z, y % 4 == 0 ? "pw_girt[axis=x]" : "pw_leg");
            }
            set(x, 0, z, "concrete_dark");
        }
        for (int x = x0; x <= x1; x++) {
            set(x, h, z, "pw_girt[axis=x]");
            if (Math.floorMod(x - x0, 3) == 1) {
                set(x, h - 1, z, "pw_ins_string");
            }
        }
    }

    /** The lines strung between two gantries, three phases every `every` along x. */
    private void wires(int x0, int x1, int z0, int z1, int y, int every) {
        for (int x = x0 + 1; x < x1; x += 3) {
            for (int z = z0 + 1; z < z1; z++) {
                if (chance() > 0.04 + hit * 0.06) {
                    q.setIfAir(x, y, z, "pw_wire[axis=z]");
                }
            }
        }
    }

    /** The bus along x on its insulator posts. */
    private void busOnPosts(int x0, int x1, int z, int h) {
        for (int x = x0; x <= x1; x++) {
            if (Math.floorMod(x - x0, 5) == 0) {
                for (int y = 1; y < h - 1; y++) {
                    set(x, y, z, "pw_leg");
                }
                set(x, h - 1, z, "pw_post_ins");
            }
            q.setIfAir(x, h, z, "pw_bus[axis=x]");
        }
    }

    /** The lattice tower the circuits leave by: four legs braced up, the arm with its strings. */
    private void lineTower(int tx, int tz, int h) {
        for (int y = 0; y <= h; y++) {
            for (int dx = -1; dx <= 1; dx += 2) {
                for (int dz = -1; dz <= 1; dz += 2) {
                    set(tx + dx, y, tz + dz, y == 0 ? "concrete_dark" : "pw_leg");
                }
            }
            if (y > 0 && y % 3 == 0) {
                set(tx, y, tz - 1, y % 6 == 0 ? "pw_girt[axis=x]" : "pw_diagx[facing=north]");
                set(tx, y, tz + 1, y % 6 == 0 ? "pw_girt[axis=x]" : "pw_diagx[facing=south]");
            }
        }
        for (int x = tx - 6; x <= tx + 6; x++) {
            if (Math.abs(x - tx) > 1) {
                set(x, h, tz, "pw_girt[axis=x]");
            }
        }
        for (int x : new int[] {tx - 5, tx - 3, tx + 3, tx + 5}) {
            set(x, h - 1, tz, "pw_ins_string");
        }
        sign("danger", tx - 1, 3, tz - 2, "north");
    }

    /** The wooden pole line the feed comes in by, back from (x, z0) to z1. */
    private void poleLine(int x, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            if (Math.floorMod(z - z0, 7) == 0) {
                for (int y = 1; y <= 9; y++) {
                    set(x, y, z, "pole_wood");
                }
                set(x, 10, z, "pole_top[axis=x]");
            } else {
                q.setIfAir(x, 10, z, "power_line[axis=z]");
            }
        }
    }

    /** Gravel through the fence, the concrete strips the crews drove on. */
    private void yardGround(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, chance() < 0.25 ? "rw_subballast" : "rw_ballast");
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, "air");
                }
            }
        }
    }

    /** The drive in from the road to the gate. */
    private void apron(int x0, int x1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, chance() < 0.15 + hit * 0.1 ? "asphalt_crack" : "asphalt");
                set(x, 1, z, "air");
            }
        }
    }

    /** Chain-link and razor wire round the yard, the gate left open, the warning plates on it. */
    private void fence(int x0, int z0, int x1, int z1, int g0, int g1) {
        double breach = 0.01 + hit * 0.03;
        for (int x = x0; x <= x1; x++) {
            if (x < g0 || x > g1) {
                post(x, z0, (x - x0) % 6 == 0, "x", breach);
            }
            post(x, z1, (x - x0) % 6 == 0, "x", breach);
        }
        for (int z = z0 + 1; z < z1; z++) {
            post(x0, z, (z - z0) % 6 == 0, "z", breach);
            post(x1, z, (z - z0) % 6 == 0, "z", breach);
        }
        // the gate's two leaves swung back inside, one hanging
        for (int y = 1; y <= 2; y++) {
            set(g0, y, z0 + 1, "chainlink[axis=z]");
            set(g1, y, z0 + 1, "chainlink[axis=z]");
            set(g0 - 1, y, z0, "fence_post");
            set(g1 + 1, y, z0, "fence_post");
        }
        sign("keepout", g0 - 3, 2, z0 - 1, "north");
        sign("danger", g1 + 2, 2, z0 - 1, "north");
        for (int x = x0 + 5; x < x1 - 2; x += 14) {
            if (x + 1 < g0 - 3 || x > g1 + 3) {
                continue;
            }
        }
        for (int z = z0 + 6; z < z1 - 2; z += 14) {
            sign("danger", x0 - 1, 2, z, "west");
            sign("danger", x1 + 1, 2, z, "east");
        }
    }

    private void post(int x, int z, boolean post, String axis, double breach) {
        if (chance() < breach) {
            if (chance() < 0.5) {
                set(x, 1, z, "chainlink[axis=" + axis + "]");
            }
            return;
        }
        for (int y = 1; y <= 2; y++) {
            set(x, y, z, post ? "fence_post" : "chainlink[axis=" + axis + "]");
        }
        if (chance() < 0.9) {
            set(x, 3, z, "st_razor");
        }
    }

    private void floodlight(int x, int z) {
        for (int y = 1; y <= 9; y++) {
            set(x, y, z, "rw_pole");
        }
        set(x, 10, z, "fit_flood[facing=" + (z < 10 ? "south" : "north") + "]");
    }

    /** Weeds through the gravel where nothing stands. */
    private void weeds(int x0, int z0, int x1, int z1, double share) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (chance() < share && q.get(x, 1, z) == null && q.get(x, 0, z) != null && q.get(x, 0, z).startsWith("rw_")) {
                    set(x, 1, z, chance() < 0.35 ? "weeds_tall" : "grass_tuft");
                }
            }
        }
    }

    /** A two-tile power-company sign (pw_sgn_KEY_0/1) on a wall or fence, read facing. */
    private void sign(String key, int x, int y, int z, String facing) {
        int[] a = MallSigns.at(x, z, 2, 0, facing), b = MallSigns.at(x, z, 2, 1, facing);
        set(a[0], y, a[1], "pw_sgn_" + key + "_0[facing=" + facing + "]");
        set(b[0], y, b[1], "pw_sgn_" + key + "_1[facing=" + facing + "]");
    }
}
