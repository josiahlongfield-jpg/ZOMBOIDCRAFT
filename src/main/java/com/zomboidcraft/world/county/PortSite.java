package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;

/**
 * A port on the water, in three kinds:
 * <ul>
 * <li>container: the terminal, its quay lined with ship-to-shore gantries (booms out over a
 * container ship, or raised), the stacks in their blocks under the rubber-tyred gantries, the
 * truck gate with its canopy and booths, the terminal office, the empties piled high, the
 * high masts of sodium and purple light;</li>
 * <li>industrial: the docks behind a bulk carrier or a tanker, barges and a tug: harbour cranes,
 * a tank farm and its pipes to the quay or a grain elevator and its gallery, heaps of coal and
 * gravel, a saw-tooth factory and its chimney, warehouses;</li>
 * <li>fishing: a harbour behind its breakwater and lighthouse, wooden jetties with the trawlers,
 * sailboats and skiffs tied up (and the ones that sank there), the fish market, the cannery,
 * the net sheds and the boatyard, the bait shop and the harbourmaster;</li>
 * <li>naval: the aircraft carrier at her full size along the wharf ({@link Carrier}), held off by
 * her camels: the apron clear under her overhang, the shore power pits, the mobile cranes; the
 * security fence behind it with the gate and guard booth, the barracks, the supply warehouse, the
 * motor pool.</li>
 * </ul>
 * It's drawn in a frame turned so the water is always to the north (local -z) and the quay runs
 * along x at z = {@link Place#quay}: the basin to the north of it, the land to the south.
 */
final class PortSite extends Site {
    private Placer f;
    private BlockSink fs;
    private Kit kit;
    private Harbour hb;
    /** Local width (along the quay) and depth (from the open water to the land's far edge), and the quay row. */
    private int lw, ld, quay;
    private double hit;
    /** The lane along the quay's back, from the side gates, at this local z. */
    private int mainZ;
    private Kit.Style main;

    PortSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        int face = Math.max(0, p.waterSide);
        boolean turned = (face & 1) == 1;
        lw = turned ? d : w;
        ld = turned ? w : d;
        f = lot(0, 0, lw, ld, face);
        fs = new FrameSink(f);
        quay = p.quay > 0 ? p.quay : ld * 2 / 5;
        kit = new Kit(fs, rng, lw, ld);
        hit = 0.35 + chance() * 0.65;
        hb = new Harbour(f, rng, hit);
        main = variant("fishing") ? Kit.Style.LANE : Kit.Style.ROAD;
        kit.claim(0, 0, lw - 1, quay - 1, Kit.BUILT);
        roads(face);
        kit.pave();
        switch (p.variant) {
            case "industrial" -> industrial();
            case "fishing" -> fishing();
            case "naval" -> naval();
            default -> container();
        }
        kit.woods(variant("fishing") ? 0.18 : 0.3);
        kit.overgrow(0.8);
        kit.roadWeeds(0.4);
        kit.tidyPlants();
    }

    // ------------------------------------------------------------------ frame and roads

    /** The local position of the gate on a site side, as {x, z}. */
    private int[] localGate(int siteSide, int face) {
        int[] g = switch (siteSide & 3) {
            case 0 -> new int[] {p.gateX(), 0};
            case 1 -> new int[] {w - 1, p.gateZ()};
            case 2 -> new int[] {p.gateX(), d - 1};
            default -> new int[] {0, p.gateZ()};
        };
        return switch (face & 3) {
            case 0 -> g;
            case 1 -> new int[] {g[1], ld - 1 - g[0]};
            case 2 -> new int[] {lw - 1 - g[0], ld - 1 - g[1]};
            default -> new int[] {lw - 1 - g[1], g[0]};
        };
    }

    /** Just outside the gate on a local side (1 east, 2 south, 3 west), so a lane runs off the edge. */
    private double[] beyond(int localSide, int face) {
        int[] g = localGate((localSide + face) & 3, face);
        return switch (localSide & 3) {
            case 1 -> new double[] {lw + 2, g[1] + 0.5};
            case 2 -> new double[] {g[0] + 0.5, ld + 2};
            case 3 -> new double[] {-3, g[1] + 0.5};
            default -> new double[] {g[0] + 0.5, -3};
        };
    }

    private void roads(int face) {
        double[] west = beyond(3, face), east = beyond(1, face), south = beyond(2, face);
        // the side gates sit at the same depth: one lane straight across behind the quay
        mainZ = (int) Math.floor((west[1] + east[1]) / 2);
        Kit.Lane across = kit.lane(main, west[0], mainZ + 0.5, east[0], mainZ + 0.5);
        Kit.Lane in = kit.lane(main, south[0], south[1], south[0], mainZ + 0.5);
        kit.abandonedCars(across, 0.5);
        kit.abandonedCars(in, 0.6);
    }

    // ------------------------------------------------------------------ helpers

    /** True if the box is open ground (nothing paved for streets, built or claimed). */
    private boolean open(int x0, int z0, int x1, int z1) {
        if (x0 < 1 || z0 < 1 || x1 > lw - 2 || z1 > ld - 2) {
            return false;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                byte u = kit.use(x, z);
                if (u != Kit.FREE && u != Kit.PARK && u != Kit.LOT) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Room for an fw x fd footprint in the box, rows searched from z0 (nearest the water) or
     * from z1, margin all round; claimed and returned as {x0, z0}.
     */
    private int[] room(int bx0, int bz0, int bx1, int bz1, int fw, int fd, int margin, boolean fromBack) {
        int span = bx1 - bx0 - fw + 1;
        if (span < 0 || bz1 - bz0 + 1 < fd) {
            return null;
        }
        int start = rng.nextInt(span + 1);
        for (int row = 0; row + fd <= bz1 - bz0 + 1; row += 2) {
            int z0 = fromBack ? bz1 - row - fd + 1 : bz0 + row;
            for (int i = 0; i <= span; i += 2) {
                int x0 = bx0 + (start + i) % (span + 1);
                if (open(x0 - margin, z0 - margin, x0 + fw - 1 + margin, z0 + fd - 1 + margin)) {
                    kit.claim(x0, z0, x0 + fw - 1, z0 + fd - 1, Kit.BUILT);
                    return new int[] {x0, z0};
                }
            }
        }
        return null;
    }

    /** Paving over the open ground of a box: concrete or asphalt, cracked, weeds through, claimed as a lot. */
    private void slab(int x0, int z0, int x1, int z1, boolean asphalt) {
        for (int x = Math.max(0, x0); x <= Math.min(lw - 1, x1); x++) {
            for (int z = Math.max(0, z0); z <= Math.min(ld - 1, z1); z++) {
                byte u = kit.use(x, z);
                if (u != Kit.FREE && u != Kit.PARK) {
                    continue;
                }
                double r = chance();
                f.set(x, 0, z, asphalt
                        ? (r < 0.6 ? "asphalt" : r < 0.78 ? "asphalt_crack" : r < 0.88 ? "asphalt_patch" : r < 0.95 ? "asphalt_b" : "asphalt_moss")
                        : (r < 0.55 ? "floor_concrete" : r < 0.72 ? "floor_concrete_crack" : r < 0.84 ? "floor_concrete_b"
                        : r < 0.93 ? "concrete_dark_stain" : "floor_concrete_moss"));
                if (chance() < 0.03) {
                    f.set(x, 1, z, chance() < 0.6 ? "minecraft:short_grass" : "weeds");
                }
                kit.use[z * lw + x] = Kit.LOT;
            }
        }
    }

    /** A Placer for a piece's own frame at local (x, z), turned rot quarters. */
    private Placer at(int x, int z, int rot) {
        return f.turned(x, 0, z, rot);
    }

    private Harbour hb(int x, int z, int rot) {
        return new Harbour(at(x, z, rot), rng, hit);
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    /** Clutter over the open paving of a box: chassis, pallets, drums, the dead. */
    private void litter(int x0, int z0, int x1, int z1, int n) {
        for (int i = 0; i < n; i++) {
            int x = x0 + rng.nextInt(Math.max(1, x1 - x0)), z = z0 + rng.nextInt(Math.max(1, z1 - z0));
            if (!open(x, z, x + 1, z + 1)) {
                continue;
            }
            switch (rng.nextInt(5)) {
                case 0 -> {
                    f.setIfAir(x, 1, z, "fm_drum");
                    f.setIfAir(x + 1, 1, z, "pk_barrel_1[facing=north]");
                }
                case 1 -> f.setIfAir(x, 1, z, chance() < 0.5 ? "crate" : "cardboard_box");
                case 2 -> {
                    f.setIfAir(x, 1, z, "trash_bag[facing=north]");
                    f.setIfAir(x + 1, 1, z, "blood_splat");
                }
                case 3 -> f.setIfAir(x, 1, z, "debris_chunk");
                default -> f.setIfAir(x, 1, z, "tire[axis=x]");
            }
        }
    }

    // ------------------------------------------------------------------ the container terminal

    private void container() {
        hb.quay(0, lw - 1, quay);
        int gauge = 16, railA = quay + 3, railB = railA + gauge - 1;
        int apronEnd = Math.min(mainZ - (int) main.reach() - 2, railB + 20);
        slab(0, quay + 1, lw - 1, apronEnd, false);
        for (int x = 0; x < lw; x++) {
            for (int z : new int[] {railA, railA + 1, railB - 1, railB}) {
                if (chance() > 0.03) {
                    f.set(x, 1, z, "rail[axis=x]");
                }
            }
        }
        // the ship along the quay at her full size, a gap of two for the fenders; most carry a bunker deep aft
        int beam = 40 + 2 * rng.nextInt(2), hbm = beam / 2;
        int len = Math.min(lw - 40, 250 + rng.nextInt(40));
        int sx = 14 + rng.nextInt(Math.max(1, lw - len - 28));
        int zc = quay - 2 - hbm;
        boolean bowEast = chance() < 0.5;
        Bunker.State state = null;
        if (chance() < 0.6) {
            double r = chance();
            state = r < 0.45 ? Bunker.State.SEALED : r < 0.7 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        }
        new BigShip(bowEast ? at(sx, zc, 0) : at(sx + len - 1, zc - 1, 2), rng, hit, bowEast ? hbm + 2 : -hbm - 3)
                .container(len, beam, state);
        // a tug still made fast at her bow, a feeder ship riding at anchor out in the basin
        int bowX = bowEast ? sx + len + 3 : sx - 27;
        if (bowX > 2 && bowX + 25 < lw - 2) {
            new Ships(at(bowX, quay - 4 - hbm - rng.nextInt(4), 0), rng, hit).tug(false);
        }
        int fbeam = 26 + 2 * rng.nextInt(2);
        int outZ = zc - hbm - 16 - fbeam / 2;
        if (outZ - fbeam / 2 > 4 && lw > 200) {
            int flen = Math.min(lw - 20, 140 + rng.nextInt(40));
            int fx = rng.nextInt(Math.max(1, lw - flen - 10)) + 5;
            boolean east = chance() < 0.5;
            new BigShip(east ? at(fx, outZ, 0) : at(fx + flen - 1, outZ - 1, 2), rng, hit, 0).container(flen, fbeam, null);
        }
        // the gantries along the rails: the ones over the ship were working her when it stopped, booms
        // raised over her house (it stands higher than they reach)
        int n = Math.max(2, Math.min(5, lw / 75));
        String crane = any("steel_red", "steel_red", "steel_red", "steel_yellow");
        int hx0 = bowEast ? sx - 4 : sx + len - 34, hx1 = hx0 + 38;
        for (int i = 0; i < n; i++) {
            int cx = (int) ((i + 0.5) * lw / n) - 9 + rng.nextInt(9) - 4;
            boolean over = cx > sx - 4 && cx + 18 < sx + len + 4;
            boolean house = cx + 18 > hx0 && cx < hx1;
            hb(cx, railA, 0).gantry(18, gauge, beam + 10 + rng.nextInt(6), house || !over && chance() < 0.6, crane, 46);
            kit.claim(Math.max(0, cx - 3), railA - 1, Math.min(lw - 1, cx + 21), railB + 2, Kit.BUILT);
        }
        // post lamps along the quay edge, sodium mostly, a few gone purple
        for (int x = 7; x < lw - 3; x += 16) {
            if (f.get(x, 1, quay + 1) == null) {
                for (int y = 1; y <= 5; y++) {
                    f.set(x, y, quay + 1, "streetlight_pole");
                }
                f.set(x, 6, quay + 1, hb.head(0.2 + hit * 0.3, "south"));
            }
        }
        // hatch covers set down on the apron, a box or two dropped
        for (int i = 0; i < 4; i++) {
            int x = 6 + rng.nextInt(Math.max(1, lw - 20)), z = railB + 3 + rng.nextInt(Math.max(1, apronEnd - railB - 6));
            if (open(x, z, x + 12, z + 4)) {
                for (int k = 0; k < 4; k++) {
                    for (int j = 0; j < 12; j++) {
                        f.set(x + j, 1, z + k, j == 0 || j == 11 ? "hazard_stripes" : "deck_steel");
                        if (chance() < 0.5) {
                            f.set(x + j, 2, z + k, "deck_steel");
                        }
                    }
                }
                kit.claim(x, z, x + 11, z + 3, Kit.BUILT);
            }
        }
        for (int i = 0; i < 5; i++) {
            int x = 4 + rng.nextInt(Math.max(1, lw - 20)), z = railA + 3 + rng.nextInt(Math.max(1, gauge - 6));
            if (open(x, z, x + 12, z + 3)) {
                hb.chassis(x, z, chance() < 0.6);
                kit.claim(x, z, x + 11, z + 2, Kit.BUILT);
            }
        }
        // the yard behind the lane: blocks of stacks under their gantries, aisles between
        int yz0 = mainZ + (int) main.reach() + 3;
        int gateX = localGate((2 + Math.max(0, p.waterSide)) & 3, Math.max(0, p.waterSide))[0];
        int yz1 = ld - 34;
        slab(0, yz0 - 2, lw - 1, ld - 2, chance() < 0.5);
        int cols = 3 + rng.nextInt(2);
        int bw = cols * 13 - 1, pitchX = bw + 12, pitchZ = 30;
        String rtg = any("steel_yellow", "steel_yellow", "steel_red");
        for (int bz = yz0 + 2; bz + 22 <= yz1; bz += pitchZ) {
            for (int bx = 6; bx + bw + 6 <= lw; bx += pitchX) {
                if (Math.abs(bx + bw / 2 - gateX) < bw / 2 + 10) {
                    continue;  // the road in from the gate runs through here
                }
                if (!open(bx - 3, bz - 2, bx + bw + 3, bz + 20)) {
                    continue;
                }
                kit.claim(bx - 2, bz - 1, bx + bw + 2, bz + 19, Kit.BUILT);
                hb(bx, bz, 0).stacks(0, 0, cols, 6, 2 + rng.nextInt(3));
                if (chance() < 0.75) {
                    int at = rng.nextInt(Math.max(1, cols)) * 13;
                    hb(bx + at - 1, bz - 2, 0).rtg(13, 21, 16, rtg);
                }
            }
        }
        // the high masts over the yard
        for (int z = yz0 + 12; z < yz1; z += 55) {
            for (int x = 30; x < lw - 20; x += 60) {
                int mx = x + rng.nextInt(9) - 4;
                for (int dz = 0; dz < 30; dz++) {
                    int mz = z + dz;
                    if (open(mx, mz, mx, mz)) {
                        hb.lightMast(mx, mz, 20);
                        kit.claim(mx, mz, mx, mz, Kit.BUILT);
                        break;
                    }
                }
            }
        }
        for (int x = 25; x < lw - 20; x += 70) {
            int mz = apronEnd - 1;
            if (open(x, mz, x, mz)) {
                hb.lightMast(x, mz, 18);
                kit.claim(x, mz, x, mz, Kit.BUILT);
            }
        }
        // the truck gate on the road in, the office beside it, the empties and the shed at the back
        int gz = ld - 22;
        truckGate(gateX, gz);
        int[] off = room(gateX + 14, ld - 32, Math.min(lw - 4, gateX + 70), ld - 4, 30, 16, 2, true);
        if (off != null) {
            office(off[0], off[1], 30, 16);
        }
        int[] lotA = room(Math.max(3, gateX - 70), ld - 32, gateX - 14, ld - 4, 31, 14, 2, true);
        if (lotA != null) {
            carPark(lotA[0], lotA[1], 31, 14);
        }
        int[] shed = room(3, yz0, lw - 4, ld - 4, 44, 26, 3, true);
        if (shed != null) {
            hb(shed[0], shed[1] + 6, 0).warehouse(44, 20);
        }
        // the chassis pool: trailers in rows, some still loaded
        for (int i = 0; i < 2; i++) {
            int[] cp = room(3, yz1 - 10, lw - 4, ld - 4, 52, 15, 2, false);
            if (cp == null) {
                break;
            }
            for (int r = 0; r < 4; r++) {
                for (int c = 0; c < 4; c++) {
                    if (chance() < 0.8) {
                        hb.chassis(cp[0] + c * 13, cp[1] + r * 4, chance() < 0.4);
                    }
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            int[] e = room(3, yz0, lw - 4, ld - 4, 25, 9, 2, true);
            if (e == null) {
                break;
            }
            hb(e[0], e[1], 0).stacks(0, 0, 2, 3, 5);
        }
        litter(2, quay + 2, lw - 3, ld - 3, 60);
    }

    // ------------------------------------------------------------------ the naval wharf

    private void naval() {
        hb.quay(0, lw - 1, quay);
        int apronEnd = Math.min(mainZ - (int) main.reach() - 2, quay + 44);
        slab(0, quay + 1, lw - 1, apronEnd, false);
        // the carrier, her island to the wharf, held off it by her camels
        int sx = Math.max(4, (lw - Carrier.LEN) / 2 + rng.nextInt(9) - 4);
        int off = 33;
        new Carrier(at(sx, quay - off, 0), rng, hit, off).build();
        // the shore power pits and their cables up to her side, the hoses
        for (int x = sx + 24; x < sx + Carrier.LEN - 20; x += 36) {
            f.set(x, 0, quay + 4, "concrete_dark");
            f.set(x, 1, quay + 4, "bunk_switchboard[facing=north]");
            f.set(x, 2, quay + 4, "bunk_switchboard_top[facing=north]");
            for (int z = quay + 1; z < quay + 4; z++) {
                f.set(x, 1, z, "bunk_cable_loose");
            }
            if (chance() < 0.5) {
                f.set(x + 2, 1, quay + 2, "navy_hose_reel[facing=north]");
            }
        }
        // the mobile cranes on the apron, booms up over her deck
        for (int cx : new int[] {sx + 60 + rng.nextInt(20), sx + 250 + rng.nextInt(20)}) {
            if (cx + 12 < lw - 2) {
                mobileCrane(cx, quay + 9);
                kit.claim(cx - 2, quay + 6, cx + 13, quay + 15, Kit.BUILT);
            }
        }
        // a few trucks and jeeps left on the apron, pallets and crates by the brows
        for (int i = 0; i < 5; i++) {
            int x = 6 + rng.nextInt(Math.max(1, lw - 20)), z = quay + 18 + rng.nextInt(Math.max(1, apronEnd - quay - 22));
            if (open(x - 2, z - 3, x + 2, z + 3)) {
                f.vehicle(x + 0.5, 1, z + 0.5, rng.nextInt(4) * 90, "jeep,jeep_open,box_truck,pickup,ambulance,fire_truck", rng.nextLong());
            }
        }
        for (int bx : new int[] {sx + 150, sx + 232}) {
            for (int i = 0; i < 6; i++) {
                f.setIfAir(bx - 6 + rng.nextInt(14), 1, quay + 3 + rng.nextInt(6), any("crate", "crate", "bunk_crate_ration[facing=north]",
                        "cardboard_box[facing=north]", "bunk_jerry_cans[facing=north]"));
            }
        }
        // the light masts along the back of the apron
        for (int x = 15; x < lw - 10; x += 48) {
            int mz = apronEnd - 1;
            if (open(x, mz, x, mz)) {
                navalMast(x, mz, 18);
                kit.claim(x, mz, x, mz, Kit.BUILT);
            }
        }
        // the fence behind the apron, the gate in it with its booth and barrier
        int fz = apronEnd + 1, gx = sx + 190;
        Camp camp = new Camp(f, rng, hit);
        camp.fence(1, fz, gx - 6, fz, 0.04 + hit * 0.08);
        camp.fence(gx + 6, fz, lw - 2, fz, 0.04 + hit * 0.08);
        kit.claim(0, fz, lw - 1, fz, Kit.BUILT);
        guardBooth(gx + 7, fz + 1);
        for (int k = 1; k <= 5; k++) {
            f.set(gx - 6 + k, 1, fz, k % 2 == 0 ? "steel_red" : "steel_yellow");
        }
        f.set(gx - 6, 1, fz, "steel_yellow");
        f.set(gx - 6, 2, fz, "steel_yellow");
        for (int x = gx - 4; x <= gx + 4; x += 4) {
            f.setIfAir(x, 1, fz - 3, "jersey_barrier[facing=north]");
        }
        // behind the lane: the barracks, the supply warehouse, the motor pool, boxes stacked
        int yz0 = mainZ + (int) main.reach() + 3;
        int[] bk = room(3, yz0, lw - 4, ld - 4, 46, 16, 3, false);
        if (bk != null) {
            navalBarracks(bk[0], bk[1], 46, 16);
        }
        int[] wh = room(3, yz0, lw - 4, ld - 4, 40, 22, 3, false);
        if (wh != null) {
            supplyShed(wh[0], wh[1], 40, 22);
        }
        int[] mp = room(3, yz0, lw - 4, ld - 4, 34, 16, 2, true);
        if (mp != null) {
            Placer s = at(mp[0], mp[1], 0);
            for (int x = 0; x < 34; x++) {
                for (int z = 0; z < 16; z++) {
                    double r = chance();
                    s.set(x, 0, z, x % 4 == 0 && (z < 6 || z > 9) ? "road_line_white[axis=z]"
                            : r < 0.7 ? "asphalt" : r < 0.85 ? "asphalt_crack" : "asphalt_patch");
                }
                if (x % 4 == 2) {
                    if (chance() < 0.55) {
                        s.vehicle(x + 0.5, 1, 2.5, 0, "jeep,jeep_open,jeep,box_truck", rng.nextLong());
                    }
                    if (chance() < 0.55) {
                        s.vehicle(x + 0.5, 1, 13.5, 180, "jeep,jeep_open,pickup,swat_van", rng.nextLong());
                    }
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            int[] e = room(3, yz0, lw - 4, ld - 4, 25, 9, 2, true);
            if (e == null) {
                break;
            }
            hb(e[0], e[1], 0).stacks(0, 0, 2, 3, 3);
        }
        litter(2, quay + 2, lw - 3, ld - 3, 30);
    }

    /** A truck crane at (x, z) on its outriggers, the boom lifted out over the water toward her deck. */
    private void mobileCrane(int x, int z) {
        Placer s = at(x, z, 0);
        for (int dx = 0; dx < 12; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                boolean wheel = (dx == 1 || dx == 3 || dx == 8 || dx == 10) && (dz == 0 || dz == 3);
                s.set(dx, 1, dz, wheel ? "tire[axis=z]" : "steel_yellow");
            }
        }
        for (int dz = 0; dz < 2; dz++) {
            s.set(10, 2, dz, "steel_yellow");
            s.set(11, 2, dz, chance() < 0.4 ? "window_broken[axis=z]" : "window[axis=z]");
        }
        for (int[] o : new int[][] {{0, -2}, {0, 5}, {11, -2}, {11, 5}}) {
            s.set(o[0], 1, o[1], "steel_yellow");
            s.set(o[0], 0, o[1], "deck_steel");
        }
        for (int dx = 4; dx <= 7; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                s.set(dx, 2, dz, "steel_yellow");
                s.set(dx, 3, dz, dz == 3 && dx > 4 ? "window[axis=x]" : "steel_yellow");
            }
        }
        int tipY = 34 + rng.nextInt(6), tipZ = -18 - rng.nextInt(6);
        Harbour h = new Harbour(s, rng, hit);
        h.member(5, 4, 1, tipY, tipZ, "cr_lattice_yellow");
        h.member(6, 4, 1, tipY, tipZ, "cr_lattice_yellow");
        for (int y = tipY - 1; y > tipY - 8; y--) {
            s.set(5, y, tipZ, "vessel_chain_v");
        }
        s.set(5, tipY - 8, tipZ, "steel_yellow");
        s.set(5, tipY + 1, tipZ, "aviation_light");
    }

    /** A light mast at (x, z), h tall: a pole, a head of lamps, the dead ones dark. */
    private void navalMast(int x, int z, int h) {
        for (int y = 1; y < h; y++) {
            f.set(x, y, z, "streetlight_pole");
        }
        f.set(x, 0, z, "concrete_dark");
        f.set(x, h, z, "navy_steel");
        String[] look = {"west", "east", "north"};
        int[][] ds = {{-1, 0}, {1, 0}, {0, -1}};
        for (int i = 0; i < 3; i++) {
            f.set(x + ds[i][0], h, z + ds[i][1], Camp.lamp(rng, 0.3 + hit * 0.3, "cp_flood_bar", look[i]));
        }
        f.set(x, h + 1, z, "aviation_light");
    }

    /** The guard booth at the gate, (x, z) its north-west corner: glass all round, the desk and the log. */
    private void guardBooth(int x, int z) {
        Placer s = at(x, z, 0);
        for (int dx = 0; dx < 4; dx++) {
            for (int dz = 0; dz < 4; dz++) {
                boolean wall = dx == 0 || dx == 3 || dz == 0 || dz == 3;
                s.set(dx, 0, dz, "concrete_dark");
                for (int y = 1; y <= 3; y++) {
                    String w = y == 2 && wall && (dx == 1 || dx == 2 || dz == 1 || dz == 2)
                            ? (chance() < 0.3 + hit * 0.3 ? "window_broken" : "window") + "[axis=" + (dz == 0 || dz == 3 ? "x" : "z") + "]"
                            : "concrete_panel";
                    s.set(dx, y, dz, wall ? w : "air");
                }
                s.set(dx, 4, dz, "navy_steel");
            }
        }
        Doors.abandoned(s, rng, 1, 1, 3, "ship_door", "north", Doors.hinge(rng));
        s.set(2, 1, 1, "bunk_desk[facing=south]");
        s.set(1, 1, 1, "bunk_chair[facing=north]");
        s.set(2, 3, 0, "bunk_lamp_red[facing=north]");
        kit.claim(x, z, x + 3, z + 3, Kit.BUILT);
    }

    /** The barracks bw x bd at (x0, z0): two floors of bunks, lockers and footlockers, the heads at one end. */
    private void navalBarracks(int x0, int z0, int bw, int bd) {
        Placer s = at(x0, z0, 0);
        Bldg b = new Bldg(s, rng);
        b.damage = 0.3 + hit * 0.5;
        b.boarded = 0.15;
        b.shell(0, 0, bw - 1, bd - 1, 2, 4, any("concrete_panel", "brick_buff"), "floor_lino", "floor_lino");
        for (int y : new int[] {2, 6}) {
            b.windowRow(2, bw - 3, 3, 0, y, "north", 1, null);
            b.windowRow(2, bw - 3, 3, bd - 1, y, "south", 1, null);
        }
        b.doors(bw / 2 - 1, 1, 0, "shop_door", "north");
        b.flat(0, 0, bw - 1, bd - 1, 8, "concrete_panel", 1, "concrete_dark");
        b.stair(bw - 3, 2, 1, 4, "south", "steel");
        for (int fl : new int[] {1, 5}) {
            for (int x = 2; x < bw - 6; x += 3) {
                for (int z : new int[] {1, bd - 3}) {
                    boolean north = z == 1;
                    String f = north ? "south" : "north";
                    if (chance() < 0.9) {
                        s.set(x, fl, z, "bunk_bed_head[facing=" + f + "]");
                        s.set(x, fl, z + 1, "bunk_bed_foot[facing=" + f + "]");
                        s.set(x, fl + 1, z, "bunk_bed_head_top[facing=" + f + "]");
                        s.set(x, fl + 1, z + 1, "bunk_bed_foot_top[facing=" + f + "]");
                    }
                    s.set(x + 1, fl, north ? 1 : bd - 2, "bunk_locker[facing=" + f + "]");
                    s.set(x, fl, north ? 3 : bd - 4, "bunk_footlocker[facing=" + f + "]");
                }
            }
            for (int z = 2; z < bd - 2; z += 2) {
                s.set(bw - 6, fl, z, "bunk_sink[facing=west]");
            }
            b.lights(1, 1, bw - 2, bd - 2, fl + 2, 0.05);
            b.mess(1, 1, bw - 2, bd - 2, fl, 0.05 + hit * 0.05);
        }
    }

    /** The supply warehouse bw x bd at (x0, z0): corrugated, a big roller door, racks of stores inside. */
    private void supplyShed(int x0, int z0, int bw, int bd) {
        Placer s = at(x0, z0, 0);
        Bldg b = new Bldg(s, rng);
        b.damage = 0.3 + hit * 0.4;
        b.shell(0, 0, bw - 1, bd - 1, 1, 8, "corrugated_grey", "floor_concrete", "floor_concrete");
        b.flat(0, 0, bw - 1, bd - 1, 8, "corrugated_grey", 1, "concrete_dark");
        for (int x = bw / 2 - 3; x <= bw / 2 + 2; x++) {
            for (int y = 1; y <= 6; y++) {
                s.set(x, y, 0, y > 6 - rng.nextInt(3) * 2 ? "navy_roller[facing=north]" : "air");
            }
        }
        for (int z = 3; z < bd - 3; z += 3) {
            for (int x = 3; x < bw - 3; x++) {
                if (Math.abs(x - bw / 2) < 4) {
                    continue;
                }
                s.set(x, 1, z, any("bunk_rack_crates", "bunk_rack_boxes", "bunk_rack_drums") + "[facing=north]");
                if (chance() < 0.7) {
                    s.set(x, 2, z, any("bunk_rack_crates", "bunk_rack_boxes") + "[facing=north]");
                }
            }
        }
        b.lights(1, 1, bw - 2, bd - 2, 7, 0.05);
        b.mess(1, 1, bw - 2, bd - 2, 1, 0.04);
    }

    /** The truck gate across the road in at gate x, row z: a canopy over four lanes, booths, barriers, the queue that never moved. */
    private void truckGate(int gx, int z) {
        int half = 12;
        for (int x = gx - half; x <= gx + half; x++) {
            for (int k = 0; k < 6; k++) {
                f.set(x, 7, z + k, chance() < 0.05 + hit * 0.1 ? "air" : "deck_steel");
            }
            f.set(x, 8, z, "steel_red");
        }
        for (int x = gx - half; x <= gx + half; x += 6) {
            for (int y = 1; y < 7; y++) {
                f.set(x, y, z + 1, "steel_girder");
                f.set(x, y, z + 4, "steel_girder");
            }
            if (x > gx - half && x < gx + half) {
                Placer s = at(x - 1, z + 1, 0);
                for (int y = 1; y <= 3; y++) {
                    s.set(0, y, 0, y == 2 ? "window[axis=z]" : "concrete_panel");
                    s.set(0, y, 1, y == 2 ? "window[axis=z]" : "concrete_panel");
                    s.set(0, y, 2, "concrete_panel");
                }
                f.set(x + 1, 1, z, chance() < 0.5 ? "steel_yellow" : "jersey_barrier[facing=north]");
            }
        }
        for (int x = gx - half + 1; x < gx + half; x += 2) {
            f.set(x, 6, z + 2, hb.hang(0.4));
        }
        for (int i = 0; i < 4; i++) {
            if (chance() < 0.55) {
                f.vehicle(gx - 9 + (i % 4) * 6 + 0.5, 1, z + 11 + rng.nextInt(6), 180, "box_truck,moving_truck,step_van,pickup", rng.nextLong());
            }
        }
    }

    /** The terminal office bw x bd at (x0, z0): three floors of concrete and glass, its front to the north. */
    private void office(int x0, int z0, int bw, int bd) {
        Placer s = at(x0, z0, 0);
        Bldg b = new Bldg(s, rng);
        b.damage = 0.35 + hit * 0.5;
        b.boarded = 0.1;
        b.shell(0, 0, bw - 1, bd - 1, 3, 4, any("concrete_panel", "brick_buff", "plaster_cream"), "office_floor", "office_floor");
        for (int y : new int[] {2, 6, 10}) {
            b.windowRow(1, bw - 2, 2, 0, y, "north", 2, null);
            b.windowRow(1, bw - 2, 2, bd - 1, y, "south", 2, null);
            b.windowRow(2, bd - 3, 3, 0, y, "west", 2, null);
            b.windowRow(2, bd - 3, 3, bw - 1, y, "east", 2, null);
        }
        b.doors(bw / 2 - 1, 1, 0, "shop_door", "north");
        b.flat(0, 0, bw - 1, bd - 1, 12, "concrete_panel", 1, "stone_trim");
        for (int y : new int[] {1, 5, 9}) {
            for (int x = 2; x < bw - 2; x += 4) {
                for (int z = 3; z < bd - 3; z += 4) {
                    if (chance() < 0.7) {
                        s.set(x, y, z, "desk[facing=north]");
                        s.set(x, y, z + 1, chance() < 0.3 ? "office_chair_fallen" : "office_chair[facing=north]");
                    }
                }
            }
        }
        b.stair(bw - 3, bd - 3, 1, 8, "north", "stairs_concrete");
        b.lights(1, 1, bw - 2, bd - 2, 3, 0);
        b.mess(1, 1, bw - 2, bd - 2, 1, 0.05 + hit * 0.05);
        for (int y = 13; y <= 18; y++) {
            s.set(bw - 3, y, bd - 3, "wf_mast");
        }
        s.set(bw - 3, 19, bd - 3, "aviation_light");
    }

    /** A car park bw x bd at (x0, z0): two rows nose in, the lines, a few cars left. */
    private void carPark(int x0, int z0, int bw, int bd) {
        Placer s = at(x0, z0, 0);
        for (int x = 0; x < bw; x++) {
            for (int z = 0; z < bd; z++) {
                double r = chance();
                s.set(x, 0, z, x % 3 == 0 && (z < 5 || z > bd - 6) ? "road_line_white[axis=z]"
                        : r < 0.65 ? "asphalt" : r < 0.82 ? "asphalt_crack" : r < 0.92 ? "asphalt_patch" : "asphalt_moss");
            }
            if (x % 3 == 1 && x + 1 < bw) {
                if (chance() < 0.25) {
                    s.vehicle(x + 0.5, 1, 2.5, 0, "sedan,suv,pickup,hatchback,minivan,wagon", rng.nextLong());
                }
                if (chance() < 0.25) {
                    s.vehicle(x + 0.5, 1, bd - 2.5, 180, "sedan,suv,pickup,hatchback,minivan,wagon", rng.nextLong());
                }
            }
        }
    }

    // ------------------------------------------------------------------ the industrial docks

    private void industrial() {
        hb.quay(0, lw - 1, quay);
        int apronEnd = mainZ - (int) main.reach() - 2;
        slab(0, quay + 1, lw - 1, apronEnd, false);
        boolean tanker = chance() < 0.45;
        int beam = 32 + 2 * rng.nextInt(3), hbm = beam / 2;
        int len = Math.min(lw - 60, 190 + rng.nextInt(40));
        int sx = 10 + rng.nextInt(Math.max(1, lw - len - 70));
        int zc = quay - 2 - hbm;
        new BigShip(at(sx, zc, 0), rng, hit, hbm + 2).bulk(len, beam, tanker);
        // barges and the tug further along
        int bx = sx + len + 6;
        for (int i = 0; i < 2 && bx + 34 < lw - 4; i++) {
            new Ships(at(bx, quay - 9, 0), rng, hit).barge(30, 12);
            bx += 34;
        }
        if (bx + 25 < lw - 2) {
            new Ships(at(bx, quay - 6, 0), rng, hit).tug(chance() < 0.1 + hit * 0.2);
        }
        // the harbour cranes along the quay
        int nc = 1 + rng.nextInt(2);
        for (int i = 0; i < nc; i++) {
            int cx = sx + 15 + i * (len / 2) + rng.nextInt(10);
            if (cx > lw - 12) {
                break;
            }
            hb(cx, quay + 9, 0).harbourCrane(26 + rng.nextInt(10), any("steel_yellow", "cr_steel_white", "steel_red"));
            kit.claim(cx - 9, quay + 1, cx + 9, quay + 18, Kit.BUILT);
        }
        // behind the lane: three yards side by side, either side of the road in (the wider side split)
        int z0 = mainZ + (int) main.reach() + 3, z1 = ld - 3;
        int gx = localGate((2 + Math.max(0, p.waterSide)) & 3, Math.max(0, p.waterSide))[0];
        int road = (int) Math.ceil(main.reach()) + 3;
        // the siding along the back, wagons left standing on it, either side of the road in
        if (ld - z0 > 60) {
            int rz = ld - 6;
            z1 = rz - 4;
            for (int[] run : new int[][] {{2, gx - road + 1}, {gx + road - 1, lw - 3}}) {
                if (run[1] - run[0] < 30) {
                    continue;
                }
                slab(run[0], rz - 2, run[1], rz + 2, false);
                kit.claim(run[0], rz - 2, run[1], rz + 2, Kit.BUILT);
                hb.railSpur(run[0], run[1], rz);
                for (int x = run[0] + 3 + rng.nextInt(10); x + 16 < run[1]; x += 15 + (chance() < 0.3 ? 15 + rng.nextInt(20) : 0)) {
                    hb.wagon(x, rz);
                }
            }
        }
        int[] left = {3, gx - road}, right = {gx + road, lw - 4};
        List<int[]> yards = new ArrayList<>();
        int[] wide = left[1] - left[0] >= right[1] - right[0] ? left : right, narrow = wide == left ? right : left;
        int mid = (wide[0] + wide[1]) / 2;
        yards.add(new int[] {wide[0], mid - 3});
        yards.add(new int[] {mid + 3, wide[1]});
        yards.add(narrow);
        for (int i = 2; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int[] t = yards.get(i);
            yards.set(i, yards.get(j));
            yards.set(j, t);
        }
        for (int k = 0; k < 3; k++) {
            int x0 = yards.get(k)[0], x1 = yards.get(k)[1];
            if (x1 - x0 < 30) {
                continue;
            }
            switch (k) {
                case 0 -> {
                    if (tanker) {
                        tankFarm(x0, z0, x1, z1);
                    } else {
                        grain(x0, z0, x1, z1);
                    }
                }
                case 1 -> works(x0, z0, x1, z1);
                default -> bulkYard(x0, z0, x1, z1);
            }
        }
        for (int x = 20; x < lw - 10; x += 60) {
            int mz = apronEnd - 1;
            if (open(x, mz, x, mz)) {
                hb.lightMast(x, mz, 16);
                kit.claim(x, mz, x, mz, Kit.BUILT);
            }
        }
        litter(2, quay + 2, lw - 3, ld - 3, 40);
    }

    /** Tanks in their bund, the pipes run on their rack across the lane to the quay. */
    private void tankFarm(int x0, int z0, int x1, int z1) {
        int r = 7 + rng.nextInt(3), pitch = 2 * r + 5;
        int cols = Math.max(1, (x1 - x0 - 4) / pitch), rows = Math.max(1, Math.min(3, (z1 - z0 - 30) / pitch));
        int bwid = cols * pitch + 3, bdep = rows * pitch + 3;
        if (!open(x0, z0 + 4, x0 + bwid, z0 + 4 + bdep)) {
            return;
        }
        kit.claim(x0, z0 + 4, x0 + bwid, z0 + 4 + bdep, Kit.BUILT);
        hb.bund(x0, z0 + 4, x0 + bwid, z0 + 4 + bdep);
        String skin = any("tank_white", "tank_white", "tank_blue");
        for (int c = 0; c < cols; c++) {
            for (int rr = 0; rr < rows; rr++) {
                hb.tank(x0 + 2 + r + c * pitch, z0 + 6 + r + rr * pitch, r, 10 + rng.nextInt(6), skin);
            }
        }
        // the truck rack below the bund: a canopy over two bays, the arms, the trucks that never left
        int[] rack = room(x0, z0 + bdep + 8, x1, z1, 16, 12, 2, false);
        if (rack != null) {
            Placer q = at(rack[0], rack[1], 0);
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 12; z++) {
                    q.set(x, 0, z, chance() < 0.8 ? "floor_concrete" : "concrete_dark_stain");
                    q.set(x, 7, z, chance() < 0.08 + hit * 0.1 ? "air" : "deck_steel");
                }
            }
            for (int x : new int[] {0, 7, 8, 15}) {
                for (int z : new int[] {0, 11}) {
                    for (int y = 1; y < 7; y++) {
                        q.set(x, y, z, "steel_girder");
                    }
                }
            }
            for (int z = 1; z < 11; z++) {
                q.set(7, 1, z, "jersey_barrier[facing=east]");
                q.set(7, 5, z, "cv_iron_fence[axis=z]");
            }
            q.set(7, 4, 3, "steel_yellow");
            q.set(7, 4, 8, "steel_yellow");
            q.set(3, 6, 6, hb.hang(0.4));
            q.set(12, 6, 6, hb.hang(0.4));
            for (int b = 0; b < 2; b++) {
                if (chance() < 0.6) {
                    q.vehicle(3.5 + b * 8.5, 1, 6, 0, "box_truck,step_van", rng.nextLong());
                }
            }
        }
        int[] pump = room(x0, z0 + bdep + 6, x1, z1, 10, 8, 2, false);
        if (pump != null) {
            Bldg b = new Bldg(at(pump[0], pump[1], 0), rng);
            b.damage = 0.3 + hit * 0.5;
            b.shell(0, 0, 9, 7, 1, 4, any("concrete_panel", "brick_red", "corrugated_grey"), "floor_concrete", "floor_concrete");
            b.windowRow(2, 7, 3, 0, 2, "north", 2, null);
            b.doors(4, 1, 7, "flat_door", "south");
            b.flat(0, 0, 9, 7, 4, "concrete_panel", 1, "stone_trim");
            b.lights(1, 1, 8, 6, 3, 0);
        }
        // the pipe rack to the quay, over the lane
        int px = x0 + bwid / 2;
        new Harbour(at(px, z0 + 4, 3), rng, hit).pipeRack(0, z0 + 4 - quay - 1, 0, 6);
        for (int y = 1; y <= 5; y++) {
            f.set(px, y, quay + 1, "steel_girder");
        }
        f.set(px, 6, quay, "steel_yellow");
        hb.member(px, 6, quay, 2, quay - 4, "cr_strut_yellow");
    }

    /** The grain elevator: its silos, the head house and the gallery out to the quay. */
    private void grain(int x0, int z0, int x1, int z1) {
        int r = 4, n = Math.max(3, Math.min(8, (x1 - x0 - 2) / (2 * r)));
        if (!open(x0, z0 + 6, x0 + n * 2 * r + 1, z0 + 6 + 2 * r + 2)) {
            return;
        }
        kit.claim(x0, z0 + 6, x0 + n * 2 * r + 1, z0 + 6 + 2 * r + 2, Kit.BUILT);
        int h = 26 + rng.nextInt(8);
        int mid = hb.elevator(x0, z0 + 6, n, r, h);
        hb.gallery(mid, h + 3, z0 + 6, quay + 2, 12);
        int[] shed = room(x0, z0 + 2 * r + 10, x1, z1, 24, 14, 2, false);
        if (shed != null) {
            hb(shed[0], shed[1], 0).warehouse(24, 14);
        }
    }

    /** The works: a saw-tooth factory, its chimney, a warehouse or two. */
    private void works(int x0, int z0, int x1, int z1) {
        int fw = Math.min(48, x1 - x0 - 6), fd = 30;
        int[] fa = room(x0, z0, x1, z1, fw, fd + 4, 2, false);
        if (fa != null) {
            hb(fa[0], fa[1] + 4, 0).factory(fw, fd);
            int cx = fa[0] + fw - 4;
            int[] ch = room(cx - 3, fa[1] + fd + 5, cx + 5, z1, 7, 7, 1, false);
            if (ch != null) {
                hb.chimney(ch[0] + 3, ch[1] + 3, 3, 32 + rng.nextInt(10));
            }
        }
        for (int i = 0; i < 2; i++) {
            int[] wh = room(x0, z0, x1, z1, 32, 24, 3, true);
            if (wh == null) {
                break;
            }
            hb(wh[0], wh[1] + 4, 0).warehouse(32, 20);
        }
    }

    /** Heaps of coal, gravel and sand, the conveyor that fed them, a loader left by them. */
    private void bulkYard(int x0, int z0, int x1, int z1) {
        slab(x0, z0, x1, z1, false);
        String[] stuff = {"coal", "gravel", "sand", "dirt", "rubble_pile"};
        int cz = z0 + 18;
        for (int x = x0 + 12; x + 12 < x1; x += 24) {
            hb.heap(x, cz, 10, 7 + rng.nextInt(5), stuff[rng.nextInt(stuff.length)]);
        }
        // a second row of smaller heaps where there's room, a loader parked against one
        if (z1 - z0 > 90) {
            for (int x = x0 + 9; x + 9 < x1; x += 18) {
                if (chance() < 0.75) {
                    hb.heap(x, z1 - 12, 7, 4 + rng.nextInt(4), stuff[rng.nextInt(stuff.length)]);
                }
            }
            kit.claim(x0, z1 - 20, x1, z1 - 3, Kit.BUILT);
        }
        // the conveyor along the heaps on its legs
        for (int x = x0 + 2; x < x1 - 2; x++) {
            f.set(x, 9, cz + 13, "rail[axis=x]");
            f.set(x, 8, cz + 13, "deck_steel");
            if (x % 8 == 0) {
                for (int y = 1; y < 8; y++) {
                    f.set(x, y, cz + 13, "steel_girder");
                }
            }
        }
        kit.claim(x0, z0, x1, cz + 14, Kit.BUILT);
        int[] wh = room(x0, cz + 16, x1, z1, 30, 22, 3, false);
        if (wh != null) {
            hb(wh[0], wh[1] + 2, 0).warehouse(30, 20);
        }
    }

    // ------------------------------------------------------------------ the fishing harbour

    private void fishing() {
        // the waterfront: the quay's edge, a strip of concrete to the lane
        hb.quay(0, lw - 1, quay);
        slab(0, quay + 1, lw - 1, mainZ - (int) main.reach() - 1, false);
        // the breakwater round the outside, the lighthouse on its end
        int bwEnd = (int) (lw * (0.55 + chance() * 0.15));
        boolean fromWest = chance() < 0.5;
        int bx0 = fromWest ? 0 : lw - bwEnd, bx1 = fromWest ? bwEnd : lw - 1;
        hb.breakwater(bx0, bx1, 8, 5, -1);
        hb.lighthouse(fromWest ? bx1 - 2 : bx0 + 2, 8, 0, 14);
        // the jetties, boats along them
        int jetties = 3 + rng.nextInt(3);
        int spacing = lw / (jetties + 1);
        int jlen = Math.min(quay - 22, 26 + rng.nextInt(14));
        for (int j = 0; j < jetties; j++) {
            int jx = spacing * (j + 1) - 2 + rng.nextInt(5) - 2;
            int jw = 3 + rng.nextInt(2);
            hb.jetty(jx, quay - 1, jlen, jw, -2);
            // the gangway steps up from the jetty's head to the quay
            for (int i = 0; i < jw; i++) {
                f.set(jx + i, -1, quay - 1, "wf_gangway[facing=south]");
                f.set(jx + i, 0, quay, "wf_gangway[facing=south]");
            }
            for (int side : new int[] {-1, 1}) {
                int bz = quay - 4;
                while (bz - 8 > quay - jlen) {
                    double r = chance();
                    boolean sunk = chance() < 0.1 + hit * 0.25;
                    if (r < 0.4) {
                        int blen = 19 + rng.nextInt(3);
                        int cx = side < 0 ? jx - 5 : jx + jw + 4;
                        if (bz - blen < quay - jlen - 2) {
                            break;
                        }
                        new Ships(at(cx, bz, 3), rng, hit).trawler(sunk);
                        bz -= blen + 3;
                    } else if (r < 0.62) {
                        // lobster boats, and sloops, lie their length along the jetty a step off it
                        boolean lob = chance() < 0.6;
                        int cx = side < 0 ? jx - 3 : jx + jw + 2;
                        if (bz - 12 < quay - jlen - 2) {
                            break;
                        }
                        Ships s = new Ships(at(cx, bz, 3), rng, hit);
                        if (lob) {
                            s.lobster(sunk);
                        } else {
                            s.sailboat(sunk);
                        }
                        bz -= 14;
                    } else if (r < 0.85) {
                        int cx = side < 0 ? jx - 2 : jx + jw + 1;
                        new Ships(at(cx, bz, 3), rng, hit).skiff(sunk);
                        bz -= 8;
                    } else {
                        bz -= 10;
                    }
                }
            }
        }
        // the slipway down into the water
        int sw = fromWest ? lw - 14 : 6;
        for (int k = 0; k < 14; k++) {
            int y = -k / 2;
            for (int i = 0; i < 6; i++) {
                f.set(sw + i, y, quay + 2 - k, chance() < 0.2 ? "concrete_panel_moss" : "concrete_panel");
                for (int yy = y + 1; yy <= 0; yy++) {
                    f.set(sw + i, yy, quay + 2 - k, yy <= Ships.SEA ? "minecraft:water" : "air");
                }
            }
        }
        kit.claim(sw - 1, quay - 12, sw + 6, quay + 3, Kit.BUILT);
        // promenade clutter: bollards are on the quay; nets, pots and crates heaped
        for (int x = 3; x < lw - 3; x += 7) {
            if (open(x, quay + 2, x + 1, quay + 3) && chance() < 0.5) {
                f.set(x, 1, quay + 2, any("bunk_cobweb[facing=north]", "crate", "fm_drum", "cardboard_box",
                        "st_pallet"));
            }
        }
        for (int x = 10; x < lw - 6; x += 24) {
            int z = mainZ - (int) main.reach() - 1;
            if (open(x, z, x, z)) {
                kit.postLamp(x, z, chance() < 0.5);
            }
        }
        // behind the lane: the fish market and the cannery, sheds, the bait shop, the boatyard
        int z0 = mainZ + (int) main.reach() + 2, z1 = ld - 3;
        int[] m = room(2, z0, lw - 3, z1, 30, 12, 2, false);
        if (m != null) {
            hb(m[0], m[1], 0).openShed(30, 12, true);
        }
        int[] c = room(2, z0, lw - 3, z1, 40, 26, 3, false);
        if (c != null) {
            hb(c[0], c[1] + 4, 0).warehouse(40, 22);
        }
        for (int i = 0; i < 2; i++) {
            int[] s = room(2, z0, lw - 3, z1, 20, 9, 2, i == 1);
            if (s != null) {
                hb(s[0], s[1], 0).openShed(20, 9, false);
            }
        }
        int[] bait = room(2, z0, lw - 3, z1, 14, 14, 2, false);
        if (bait != null) {
            new Storefront(at(bait[0], bait[1], 0), rng, 14, 12, 1, Storefront.Style.FALSE_FRONT, Storefront.Trade.BAIT).build();
        }
        int[] hm = room(2, z0, lw - 3, z1, 12, 10, 2, false);
        if (hm != null) {
            harbourmaster(hm[0], hm[1]);
        }
        int[] yard = room(2, z0, lw - 3, z1, 34, 22, 2, true);
        if (yard != null) {
            boatyard(yard[0], yard[1], 34, 22);
        }
        // the fishermen's cottages, packed in wherever there's room
        for (int i = 0; i < 4 + lw / 25; i++) {
            int[] h = room(2, z0, lw - 3, z1, 11, 9, 3, chance() < 0.6);
            if (h == null) {
                break;
            }
            cottage(h[0], h[1]);
        }
        litter(2, quay + 2, lw - 3, ld - 3, 25);
    }

    /** The harbourmaster's office: a little clapboard box with windows on the water, a radio mast. */
    private void harbourmaster(int x0, int z0) {
        Placer s = at(x0, z0, 0);
        Bldg b = new Bldg(s, rng);
        b.damage = 0.3 + hit * 0.5;
        b.boarded = 0.2;
        String wall = any("siding_white", "siding_blue", "siding_grey");
        b.shell(0, 0, 11, 9, 1, 4, wall, "floor_boards", "floor_boards");
        b.windowRow(1, 10, 2, 0, 2, "north", 2, null);
        b.door(5, 1, 9, "flat_door", "south");
        b.gable(0, 11, 0, 9, 4, "minecraft:dark_oak", false, wall, 1);
        s.set(3, 1, 2, "desk[facing=north]");
        s.set(7, 1, 2, "desk[facing=north]");
        s.set(9, 1, 7, "filing_cabinet[facing=west]");
        for (int y = 1; y <= 14; y++) {
            s.set(13, y, 8, "wf_mast");
        }
        s.set(13, 15, 8, "aviation_light");
        b.lights(1, 1, 10, 8, 3, 0);
    }

    /** The boatyard: gravel, boats up on their cradles, a hull half stripped, the travel hoist. */
    private void boatyard(int x0, int z0, int bw, int bd) {
        for (int x = x0; x < x0 + bw; x++) {
            for (int z = z0; z < z0 + bd; z++) {
                double r = chance();
                f.set(x, 0, z, r < 0.6 ? "minecraft:gravel" : r < 0.85 ? "minecraft:coarse_dirt" : "minecraft:dirt_path");
            }
        }
        for (int i = 0; i < 3; i++) {
            int bx = x0 + 2 + i * 11;
            if (bx + 9 > x0 + bw) {
                break;
            }
            // the cradle and the boat up on it, drawn as if afloat but lifted clear
            for (int k : new int[] {3, 7, 11}) {
                f.set(bx + 2, 1, z0 + k, "by_stand[facing=east]");
                f.set(bx + 5, 1, z0 + k, "by_stand[facing=west]");
            }
            for (int k : new int[] {5, 9}) {
                f.set(bx + 3, 1, z0 + k, "by_keel[axis=z]");
            }
            if (chance() < 0.5) {
                at(bx + 4, z0 + 2, 1).vehicle(5.35, 2, 0.0, -90, "=boat_lobster@" + (chance() < 0.5 ? "abandoned" : "usable"),
                        rng.nextLong());
            } else {
                at(bx + 4, z0 + 2, 1).vehicle(4.85, 2, 0.0, -90, "=boat_sloop@" + (chance() < 0.5 ? "abandoned" : "usable"),
                        rng.nextLong());
            }
        }
        // the travel hoist over the end
        int tx = x0 + bw - 8;
        for (int[] c : new int[][] {{0, 0}, {6, 0}, {0, bd - 4}, {6, bd - 4}}) {
            for (int y = 1; y <= 9; y++) {
                f.set(tx + c[0], y, z0 + 2 + c[1], y % 5 == 0 ? "cr_plate_white_seam" : "cr_plate_white");
            }
            f.set(tx + c[0], 1, z0 + 2 + c[1], "tire[axis=z]");
            f.set(tx + c[0], 9, z0 + 2 + c[1], "cr_plate_grey");
        }
        for (int z = z0 + 2; z <= z0 + bd - 2; z++) {
            f.set(tx, 10, z, "cr_plate_white");
            f.set(tx + 6, 10, z, "cr_plate_white");
        }
        // the slings hanging off the hoist's winches, a caged ladder up one leg
        for (int z : new int[] {z0 + 4, z0 + bd - 5}) {
            for (int x = tx + 1; x <= tx + 5; x++) {
                f.set(x, 10, z, "cr_plate_white");
            }
            for (int y = 4; y <= 9; y++) {
                f.set(tx + 2, y, z, "vessel_rope_v");
                f.set(tx + 4, y, z, "vessel_rope_v");
            }
        }
        for (int y = 2; y <= 9; y++) {
            f.set(tx - 1, y, z0 + 2, "cr_cage_ladder[facing=west]");
        }
    }

    /** A fisherman's cottage: one storey of clapboard under a gable, a door to the lane. */
    private void cottage(int x0, int z0) {
        Placer s = at(x0, z0, 0);
        Bldg b = new Bldg(s, rng);
        b.damage = 0.35 + hit * 0.5;
        b.boarded = 0.3;
        String wall = any("siding_white", "siding_blue", "siding_sage", "siding_yellow", "siding_grey", "siding_red");
        b.shell(0, 0, 10, 8, 1, 4, wall, "floor_boards", "floor_boards");
        b.windowRow(2, 8, 3, 0, 2, "north", 1, null);
        b.windowRow(2, 8, 3, 8, 2, "south", 1, null);
        b.door(5, 1, 0, "flat_door", "north");
        b.gable(0, 10, 0, 8, 4, any("minecraft:dark_oak", "minecraft:spruce", "minecraft:deepslate_tile"), false, wall, 1);
        b.mess(1, 1, 9, 7, 1, 0.08);
        s.set(2, 1, -2, chance() < 0.5 ? "bunk_cobweb[facing=north]" : "crate");
    }

    /** The list a builder's quarters would hand back: unused here, kept for symmetry with the other sites. */
    @SuppressWarnings("unused")
    private List<int[]> none() {
        return new ArrayList<>();
    }
}
