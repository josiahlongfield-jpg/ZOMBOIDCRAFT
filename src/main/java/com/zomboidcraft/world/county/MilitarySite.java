package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the army made its stand, in three kinds:
 * <ul>
 * <li>quarantine: a zone walled with shipping containers, the processing gate with its chutes,
 * triage and decontamination tents, streets of GP tents with their latrines, the field
 * hospital and its helipad, the supply dump, and in the far corner the burn pit and the
 * trench;</li>
 * <li>army_base: a post behind wire and watchtowers, the headquarters and its parade ground,
 * barracks and the mess hall, the motor pool with its sheds, quonset hangar, helipads and
 * fuel farm, and the supply yard;</li>
 * <li>depot: a supply depot behind hesco: warehouses with their docks, stacks of containers,
 * crates under camo nets, the trucks and the fuel.</li>
 * </ul>
 * Roads come in at the middle of all four sides through manned gates (the main one with its
 * sign, towers, nests and chicane) and cross in the middle; each quarter between them is given
 * over to one part of the camp, every building placed where it fits. Every one was overrun:
 * wire down, tents fallen, the last sandbags across the road, the dead in their bags.
 */
final class MilitarySite extends Site {
    private Kit kit;
    private double hit;
    private int fx0, fz0, fx1, fz1, gx, gz, gateHalf;
    private Kit.Style main;

    MilitarySite(Place p, BlockSink out) {
        super(p, out);
    }

    /** A quarter of the camp, with the side its buildings face (toward the cross road). */
    private record Quad(int x0, int z0, int x1, int z1, boolean north) {
        int facing() {
            return north ? 2 : 0;
        }
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        hit = 0.35 + chance() * 0.65;
        int m = 7;
        fx0 = m;
        fz0 = m;
        fx1 = w - 1 - m;
        fz1 = d - 1 - m;
        gx = p.gateX();
        gz = p.gateZ();
        main = variant("army_base") ? Kit.Style.ROAD : Kit.Style.LANE;
        gateHalf = (int) Math.ceil(main.reach()) + 1;
        roads();
        kit.pave();
        // inside the wire the ground's kept clear: grass gone long, no woods
        for (int x = fx0; x <= fx1; x++) {
            for (int z = fz0; z <= fz1; z++) {
                if (kit.use(x, z) == Kit.FREE) {
                    kit.use[z * w + x] = Kit.PARK;
                }
            }
        }
        List<Quad> quads = quads();
        switch (p.variant) {
            case "quarantine" -> quarantine(quads);
            case "depot" -> depot(quads);
            default -> armyBase(quads);
        }
        lastStand();
        kit.woods(0.4);
        kit.overgrow(0.9);
        kit.roadWeeds(0.45);
        kit.tidyPlants();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ roads and quarters

    private void roads() {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        Kit.Lane we = kit.lane(main, W[0], W[1], E[0], E[1]);
        Kit.Lane ns = kit.lane(main, N[0], N[1], S[0], S[1]);
        // a ring road inside the wire on the bigger camps
        int in = 22;
        if (fx1 - fx0 > 190 && fz1 - fz0 > 170) {
            kit.lane(Kit.Style.LANE, fx0 + in, fz0 + in, fx1 - in, fz0 + in);
            kit.lane(Kit.Style.LANE, fx1 - in, fz0 + in, fx1 - in, fz1 - in);
            kit.lane(Kit.Style.LANE, fx1 - in, fz1 - in, fx0 + in, fz1 - in);
            kit.lane(Kit.Style.LANE, fx0 + in, fz1 - in, fx0 + in, fz0 + in);
        }
        // the cars that queued at the gates to get in, and never moved again
        kit.abandonedCars(we, 0.8);
        kit.abandonedCars(ns, 0.8);
    }

    private List<Quad> quads() {
        int r = (int) Math.ceil(main.reach()) + 2;
        List<Quad> q = new ArrayList<>();
        q.add(new Quad(fx0 + 4, fz0 + 4, gx - r, gz - r, true));
        q.add(new Quad(gx + r, fz0 + 4, fx1 - 4, gz - r, true));
        q.add(new Quad(fx0 + 4, gz + r, gx - r, fz1 - 4, false));
        q.add(new Quad(gx + r, gz + r, fx1 - 4, fz1 - 4, false));
        // shuffled, so the headquarters isn't always in the same corner
        for (int i = q.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            Quad t = q.get(i);
            q.set(i, q.get(j));
            q.set(j, t);
        }
        return q;
    }

    /** True if the box is all open ground (nothing paved, built or claimed). */
    private boolean open(int x0, int z0, int x1, int z1) {
        if (x0 < 1 || z0 < 1 || x1 > w - 2 || z1 > d - 2) {
            return false;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                byte u = kit.use(x, z);
                if (u != Kit.FREE && u != Kit.PARK) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Room in the quarter for a footprint fw x fd (world x, z), searched from the edge nearest
     * the cross road (or the far edge), with margin all round; claimed and returned as {x0, z0}.
     */
    private int[] room(Quad q, int fw, int fd, int margin, boolean far) {
        int span = q.x1 - q.x0 - fw;
        if (span < 0 || q.z1 - q.z0 < fd) {
            return null;
        }
        boolean fromHigh = q.north != far;
        int start = rng.nextInt(span + 1);
        for (int row = 0; row + fd <= q.z1 - q.z0 + 1; row += 2) {
            int z0 = fromHigh ? q.z1 - row - fd + 1 : q.z0 + row;
            for (int i = 0; i <= span; i += 2) {
                int x0 = q.x0 + (start + i) % (span + 1);
                if (open(x0 - margin, z0 - margin, x0 + fw - 1 + margin, z0 + fd - 1 + margin)) {
                    kit.claim(x0, z0, x0 + fw - 1, z0 + fd - 1, Kit.BUILT);
                    return new int[] {x0, z0};
                }
            }
        }
        return null;
    }

    /**
     * Every spot in the quarter where a fw x fd footprint is open on a grid of pitch px by pz,
     * centred, rows taken from the cross-road side first, each claimed, up to max of them; each
     * comes back as {x0, z0, row, column}.
     */
    private List<int[]> grid(Quad q, int fw, int fd, int px, int pz, int margin, int max) {
        List<int[]> got = new ArrayList<>();
        int cols = (q.x1 - q.x0 + 1 - fw) / px + 1, rows = (q.z1 - q.z0 + 1 - fd) / pz + 1;
        if (q.x1 - q.x0 + 1 < fw || q.z1 - q.z0 + 1 < fd) {
            return got;
        }
        int ox = q.x0 + (q.x1 - q.x0 + 1 - fw - (cols - 1) * px) / 2;
        int oz = q.z0 + (q.z1 - q.z0 + 1 - fd - (rows - 1) * pz) / 2;
        for (int r = 0; r < rows && got.size() < max; r++) {
            int z0 = q.north ? oz + (rows - 1 - r) * pz : oz + r * pz;
            for (int c = 0; c < cols && got.size() < max; c++) {
                int x0 = ox + c * px;
                if (open(x0 - margin, z0 - margin, x0 + fw - 1 + margin, z0 + fd - 1 + margin)) {
                    kit.claim(x0, z0, x0 + fw - 1, z0 + fd - 1, Kit.BUILT);
                    got.add(new int[] {x0, z0, r, c});
                }
            }
        }
        return got;
    }

    /** Yard ground over the open ground of a box (left as it is where anything else is), claimed so nothing grows on it. */
    private void yardOver(int x0, int z0, int x1, int z1) {
        Camp c = camp(0, 0, 0);
        for (int x = Math.max(1, x0); x <= Math.min(w - 2, x1); x++) {
            for (int z = Math.max(1, z0); z <= Math.min(d - 2, z1); z++) {
                if (kit.use(x, z) == Kit.PARK) {
                    c.yard(x, z, x, z);
                    kit.use[z * w + x] = Kit.BUILT;
                }
            }
        }
    }

    /** Yard ground round a set of grid spots of fw x fd, pad blocks out. */
    private void yardAround(List<int[]> spots, int fw, int fd, int pad) {
        if (spots.isEmpty()) {
            return;
        }
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] s : spots) {
            x0 = Math.min(x0, s[0]);
            z0 = Math.min(z0, s[1]);
            x1 = Math.max(x1, s[0] + fw - 1);
            z1 = Math.max(z1, s[1] + fd - 1);
        }
        yardOver(x0 - pad, z0 - pad, x1 + pad, z1 + pad);
    }

    /** Clutter scattered over the open ground of a quarter. */
    private void litter(Quad q, int n) {
        Camp c = camp(0, 0, 0);
        for (int i = 0; i < n; i++) {
            int x = q.x0 + rng.nextInt(Math.max(1, q.x1 - q.x0 - 2)), z = q.z0 + rng.nextInt(Math.max(1, q.z1 - q.z0 - 2));
            if (open(x, z, x + 1, z + 1)) {
                c.litter(x, z);
            }
        }
    }

    /** A camp drawn in a frame at (x, z) turned to face the given way. */
    private Camp camp(int x, int z, int rot) {
        return new Camp(new Placer(out, x, 0, z, rot), rng, hit);
    }

    /** A camp drawn in a building's frame over a world footprint starting (x0, z0), bw along its front. */
    private Camp camp(int x0, int z0, int bw, int bd, int facing) {
        return new Camp(lot(x0, z0, bw, bd, facing), rng, hit);
    }

    // ------------------------------------------------------------------ the wire

    /** The perimeter, a gate where each road crosses it, towers at the corners and along the sides. */
    private void perimeter(String kind, int towerEvery, String sign) {
        int mainSide = rng.nextInt(4);
        Camp c = camp(0, 0, 0);
        double breach = 0.01 + hit * 0.06;
        // N, E, S, W runs, each in two halves either side of its gate
        int[][] sides = {{fx0, fz0, fx1, fz0}, {fx1, fz0, fx1, fz1}, {fx0, fz1, fx1, fz1}, {fx0, fz0, fx0, fz1}};
        for (int s = 0; s < 4; s++) {
            int[] l = sides[s];
            boolean alongX = l[1] == l[3];
            int a = alongX ? l[0] : l[1], b = alongX ? l[2] : l[3], g = alongX ? gx : gz;
            for (int[] run : new int[][] {{a, g - gateHalf - 1}, {g + gateHalf + 1, b}}) {
                if (run[1] < run[0]) {
                    continue;
                }
                int x0 = alongX ? run[0] : l[0], z0 = alongX ? l[1] : run[0];
                int x1 = alongX ? run[1] : l[0], z1 = alongX ? l[1] : run[1];
                switch (kind) {
                    case "containers" -> c.containerWall(x0, z0, x1, z1, 1 + (chance() < 0.6 ? 1 : 0), breach * 2);
                    case "hesco" -> {
                        c.hesco(x0, z0, x1, z1, 2, breach);
                        int ox = alongX ? 0 : (s == 1 ? 1 : -1), oz = alongX ? (s == 2 ? 1 : -1) : 0;
                        c.fence(x0 + ox, z0 + oz, x1 + ox, z1 + oz, breach);
                    }
                    default -> {
                        c.fence(x0, z0, x1, z1, breach);
                        int ox = alongX ? 0 : (s == 1 ? 3 : -3), oz = alongX ? (s == 2 ? 3 : -3) : 0;
                        if (inside(x0 + ox, z0 + oz) && inside(x1 + ox, z1 + oz)) {
                            c.fence(x0 + ox, z0 + oz, x1 + ox, z1 + oz, breach);  // the outer fence, a sterile strip between
                        }
                    }
                }
            }
            gate(s, s == mainSide, sign);
            // towers along the side, clear of the gate
            int len = b - a;
            for (int t = towerEvery; t < len - 10; t += towerEvery) {
                int at = a + t;
                if (Math.abs(at - g) < gateHalf + 16) {
                    continue;
                }
                tower(alongX ? at : l[0], alongX ? l[1] : at, s);
            }
        }
        tower(fx0, fz0, 0);
        tower(fx1, fz0, 1);
        tower(fx1, fz1, 2);
        tower(fx0, fz1, 3);
    }

    /** A watchtower just inside the wire at (x, z), its light looking out over the given side. */
    private void tower(int x, int z, int side) {
        int[] in = {0, -1, 0, 1};
        int[] inZ = {1, 0, -1, 0};
        int cx = x + (side == 1 ? -5 : side == 3 ? 5 : in[side] * 0) + (side == 0 || side == 2 ? 0 : 0);
        int cz = z + inZ[side] * 5 + (side == 1 || side == 3 ? 0 : 0);
        if (side == 0 || side == 2) {
            cx = Math.max(fx0 + 5, Math.min(fx1 - 5, x));
        } else {
            cz = Math.max(fz0 + 5, Math.min(fz1 - 5, z));
        }
        if (!open(cx - 3, cz - 3, cx + 3, cz + 3) && !(kit.use(cx, cz) == Kit.BUILT)) {
            return;
        }
        kit.claim(cx - 3, cz - 3, cx + 3, cz + 3, Kit.BUILT);
        new Camp(new Placer(out, cx, 0, cz, side).shifted(-2, 0, -2), rng, hit).watchtower(0, 0);
    }

    /**
     * The gate where the road crosses the wire on a side: a booth, the barrier, sandbag nests
     * outside, jersey barriers zigzagged inside, and on the main gate the sign and two more towers.
     */
    private void gate(int side, boolean isMain, String sign) {
        int x = side == 0 || side == 2 ? gx : side == 1 ? fx1 : fx0;
        int z = side == 1 || side == 3 ? gz : side == 0 ? fz0 : fz1;
        Placer g = new Placer(out, x, 0, z, side);
        Camp c = new Camp(g, rng, hit);
        int rw = gateHalf - 1;
        c.booth(rw + 2, 1);
        c.boom(-rw - 1, -1, rw, chance() < 0.4);
        c.boom(rw + 1, 1, 0, false);
        c.nest(rw + 5, -4);
        if (isMain || chance() < 0.5) {
            c.nest(-rw - 5, -4);
        }
        // the chicane: barriers across half the road, then the other half
        c.jersey(-rw, 8, -1, 8);
        c.jersey(1, 14, rw, 14);
        if (isMain) {
            // the gantry over the road with the sign on it, and the sign again out on the verge
            int beam = 7;
            for (int sx : new int[] {-rw - 1, rw + 1}) {
                for (int y = 1; y < beam; y++) {
                    g.set(sx, y, -2, "steel_girder");
                }
                g.set(sx, 0, -2, "concrete_dark");
            }
            for (int bx = -rw - 1; bx <= rw + 1; bx++) {
                g.set(bx, beam, -2, "steel_olive");
                g.set(bx, beam - 1, -2, "steel_olive");
            }
            int k = Boards.WIDTH.getOrDefault(sign, 5);
            Boards.fascia(g, rng, sign, -k / 2, beam - 1, -3, k);
            g.set(-rw, beam + 1, -2, Camp.flood(rng, 0.3 + hit * 0.3));
            g.set(rw, beam + 1, -2, Camp.flood(rng, 0.3 + hit * 0.3));
            Boards.board(g, rng, sign, rw + 4, 3, -16);
            for (int sx : new int[] {-1, 1}) {
                int tx = sx * (rw + 9);
                new Camp(g.shifted(tx - 2, 0, 2), rng, hit).watchtower(0, 0);
            }
            c.floodMast(-rw - 2, 4, 9);
            c.lightTower(rw + 7, 9, true);
            // the queue that never got in
            for (int i = 0; i < 3; i++) {
                if (chance() < 0.7) {
                    g.vehicle(rw / 2.0 * (i % 2 == 0 ? -1 : 1), 1, -6 - i * 6, 0, "sedan,hatchback,pickup,suv,minivan,wagon", rng.nextLong());
                }
            }
        }
        // a light tower trailer towed up to light the lane, wound down on some
        if (!isMain && chance() < 0.6) {
            c.lightTower(-rw - 4, 6, chance() < 0.7);
        }
        // claim round the gate so nothing else lands on it
        int[] a = g.world(-rw - 12, -6), b = g.world(rw + 12, 18);
        kit.claim(Math.max(0, Math.min(a[0], b[0])), Math.max(0, Math.min(a[1], b[1])), Math.min(w - 1, Math.max(a[0], b[0])),
                Math.min(d - 1, Math.max(a[1], b[1])), Kit.BUILT);
    }

    /** The last line: sandbags across the cross roads near the middle, a burnt-out jeep, the casings. */
    private void lastStand() {
        int rw = gateHalf - 1;
        int side = rng.nextInt(4);
        int[] dir = {0, 1, 0, -1}, dirZ = {-1, 0, 1, 0};
        int at = 18 + rng.nextInt(10);
        int x = gx + dir[side] * at, z = gz + dirZ[side] * at;
        Camp c = new Camp(new Placer(out, x, 0, z, side), rng, hit);
        for (int i = -rw; i <= rw; i++) {
            if (Math.abs(i) <= 1 && chance() < 0.7) {
                continue;  // the gap they kept to drive through
            }
            new Placer(out, x, 0, z, side).set(i, 1, 0, "sandbags[facing=north]");
            if (chance() < 0.6) {
                new Placer(out, x, 0, z, side).set(i, 2, 0, "sandbags[facing=north]");
            }
        }
        c.nest(-rw - 3, 2);
        c.nest(rw + 3, 2);
        new Placer(out, x, 0, z, side).vehicle(rw / 2.0, 1, -5, 90, "jeep,jeep_open", rng.nextLong());
        for (int i = 0; i < 6; i++) {
            new Placer(out, x, 0, z, side).setIfAir(-rw + rng.nextInt(2 * rw + 1), 1, -1 - rng.nextInt(8), chance() < 0.5 ? "blood_splat" : "debris_chunk");
        }
    }

    // ------------------------------------------------------------------ the army base

    private void armyBase(List<Quad> quads) {
        perimeter(chance() < 0.7 ? "fence" : "hesco", 70, "restricted");
        headquarters(quads.get(0));
        barracks(quads.get(1));
        motorPool(quads.get(2));
        supplyYard(quads.get(3), true);
        for (Quad q : quads) {
            litter(q, 10 + rng.nextInt(10));
        }
        masts();
    }

    private void headquarters(Quad q) {
        int bw = 40, bd = 16;
        int[] at = room(q, bw + 4, bd + 14, 2, false);
        if (at != null) {
            int z0 = q.north ? at[1] : at[1] + 12;
            camp(at[0] + 2, z0, bw, bd, q.facing()).headquarters(bw, bd);
            // jeeps parked along the front, barriers and a nest at the door
            Placer f = lot(at[0] + 2, z0, bw, bd, q.facing());
            for (int x = 2; x < bw - 2; x += 4) {
                if (Math.abs(x - bw / 2) > 6 && chance() < 0.45) {
                    f.vehicle(x + 0.5, 1, -9.5, 0, "jeep,jeep_open,police_suv", rng.nextLong());
                }
            }
            new Camp(f, rng, hit).jersey(bw / 2 - 8, -11, bw / 2 - 4, -11);
            new Camp(f, rng, hit).jersey(bw / 2 + 3, -11, bw / 2 + 7, -11);
        }
        // the parade ground beside it, the flag in the middle
        int[] pg = room(q, 48, 30, 2, false);
        if (pg != null) {
            for (int x = pg[0]; x < pg[0] + 48; x++) {
                for (int z = pg[1]; z < pg[1] + 30; z++) {
                    boolean line = (x - pg[0]) % 8 == 4 && (z - pg[1]) > 3 && (z - pg[1]) < 26;
                    out.set(x, 0, z, line ? "road_line_white[axis=z]" : chance() < 0.7 ? "asphalt" : chance() < 0.5 ? "asphalt_crack" : "asphalt_moss");
                }
            }
            new Civic(new Placer(out, 0, 0, 0, 0), rng).flagpole(pg[0] + 24, pg[1] + 15, 14);
            if (chance() < 0.5) {
                // a helicopter put down where it could
                camp(pg[0] + 30, pg[1] + 8, 0).helicopter(0, 0, hit > 0.6);
            }
        }
        int[] ar = room(q, 18, 13, 3, false);
        if (ar != null) {
            camp(ar[0], ar[1] + (q.north ? 0 : 3), 18, 10, q.facing()).armoury(18, 10);
        }
        int[] mt = room(q, 4, 4, 3, true);
        if (mt != null) {
            camp(mt[0] + 1, mt[1] + 1, 0).mast(0, 0, 30 + rng.nextInt(10));
        }
        int[] wt = room(q, 14, 14, 2, true);
        if (wt != null) {
            new Landmarks(new Placer(out, wt[0] + 7, 0, wt[1] + 7, 0), rng).waterTower(0, 0, true);
        }
        // the staff car park, what the officers drove in in
        int[] cp = room(q, 31, 12, 2, false);
        if (cp != null) {
            Placer lotP = lot(cp[0], cp[1], 31, 12, q.facing());
            for (int x = 0; x < 31; x++) {
                for (int z = 0; z < 12; z++) {
                    double r = chance();
                    lotP.set(x, 0, z, x % 3 == 0 && (z < 5 || z > 6) ? "road_line_white[axis=z]"
                            : r < 0.65 ? "asphalt" : r < 0.82 ? "asphalt_crack" : r < 0.92 ? "asphalt_patch" : "asphalt_moss");
                }
                if (x % 3 == 1 && x + 1 < 31) {
                    for (int z : new int[] {2, 9}) {
                        if (chance() < 0.2) {
                            lotP.vehicle(x + 0.5, 1, z + 0.5, z < 6 ? 0 : 180, "sedan,suv,pickup,sedan_ltd,wagon,police_sedan", rng.nextLong());
                        }
                    }
                }
            }
        }
        // the commissary, stripped
        int[] px = room(q, 26, 24, 3, false);
        if (px != null) {
            new Storefront(lot(px[0], px[1] + (q.north ? 0 : 6), 26, 18, q.facing()), rng, 26, 18, 1, Storefront.Style.MODERN,
                    Storefront.Trade.GROCERY).build();
        }
        // officers' quarters and offices
        for (int i = 0; i < 5; i++) {
            int[] o = room(q, 20, 10, 4, i == 2);
            if (o == null) {
                break;
            }
            camp(o[0], o[1], 20, 10, q.facing()).barracks(20, 10, any("brick_buff", "siding_white", "concrete_panel"), "minecraft:dark_oak");
        }
    }

    private void barracks(Quad q) {
        int[] mh = room(q, 30, 18, 3, false);
        if (mh != null) {
            camp(mh[0], mh[1], 30, 18, q.facing()).messHall(30, 18);
        }
        String wall = any("siding_white", "siding_grey", "brick_buff", "concrete_panel");
        String roof = any("minecraft:deepslate_tile", "minecraft:cobbled_deepslate", "minecraft:dark_oak");
        for (int i = 0; i < 6 + rng.nextInt(5); i++) {
            int[] b = room(q, 32, 10, 4, false);
            if (b == null) {
                break;
            }
            camp(b[0], b[1], 32, 10, q.facing()).barracks(32, 10, wall, roof);
        }
        // the training ground at the back: the assault course, the range
        int[] ac = room(q, 54, 11, 3, true);
        if (ac != null) {
            camp(ac[0], ac[1], 0).assaultCourse(0, 0, 54);
        }
        int[] rg = room(q, 46, 30, 3, true);
        if (rg != null) {
            camp(rg[0] + 7, rg[1] + 2, 0).range(0, 0, 39, 28);
        }
        int[] t = room(q, 12, 2, 2, true);
        if (t != null) {
            camp(t[0], t[1], 0).toilets(0, 0, 6);
        }
        // when the barracks were full: tents, wherever there was room
        tentGrid(q, "tarp_green", 20 + rng.nextInt(20));
    }

    private void motorPool(Quad q) {
        for (int i = 0; i < 2; i++) {
            int[] s = room(q, 36, 9, 3, false);
            if (s != null) {
                camp(s[0], s[1], 0).shed(0, 0, 36);
            }
        }
        int r = 9, len = 28;
        int[] h = room(q, 2 * r + 1, len, 3, false);
        if (h != null) {
            camp(h[0], h[1], 2 * r + 1, len, q.facing()).quonset(0, 0, r, len, any("corrugated_grey", "corrugated_green"));
        }
        boolean wrecked = false;
        for (int i = 0; i < 2; i++) {
            int[] pad = room(q, 15, 17, 3, true);
            if (pad == null) {
                break;
            }
            Camp c = camp(pad[0], pad[1], 0);
            c.helipad(0, 1);
            if (chance() < 0.75) {
                boolean wreck = !wrecked && chance() < hit;
                wrecked |= wreck;
                c.helicopter(7, 2, wreck);
            }
        }
        int[] f = room(q, 29, 13, 3, true);
        if (f != null) {
            camp(f[0] + 1, f[1] + 3, 0).fuelTanks(0, 0, 3);
        }
        // the big hangar
        int[] bh = room(q, 27, 38, 3, true);
        if (bh != null) {
            camp(bh[0], bh[1], 27, 38, q.facing()).quonset(0, 0, 13, 38, any("corrugated_grey", "corrugated_green", "corrugated_rust"));
        }
        bays(q, 8 + rng.nextInt(5));
        trucks(q, 1 + rng.nextInt(2));
    }

    /** Rows of trucks and jeeps parked nose to tail on gravel, as many as were left. */
    private void trucks(Quad q, int rows) {
        List<int[]> spots = grid(q, 30, 8, 34, 12, 1, rows);
        for (int[] s : spots) {
            Placer t = new Placer(out, s[0], 0, s[1], 0);
            for (int i = 0; i < 6; i++) {
                if (chance() < 0.4) {
                    t.vehicle(2.5 + i * 5, 1, 4, chance() < 0.85 ? 0 : 180, "jeep,jeep_open,box_truck,swat_van,moving_truck,warthog", rng.nextLong());
                } else if (chance() < 0.4) {
                    new Camp(t, rng, hit).litter(1 + i * 5, 3);
                }
            }
        }
        yardAround(spots, 30, 8, 2);
    }

    /** Hesco vehicle bays in rows on a gravel yard. */
    private void bays(Quad q, int max) {
        List<int[]> spots = grid(q, 7, 11, 8, 16, 1, max);
        for (int[] s : spots) {
            camp(s[0], s[1], 7, 11, q.facing()).bay(0, 0, "jeep,jeep,jeep_open,box_truck,swat_van,ambulance,tow_truck,fire_truck");
        }
        yardAround(spots, 7, 11, 3);
    }

    /** Container stacks in rows, back to back, aisles between. */
    private void containerYard(Quad q, int max) {
        List<int[]> spots = grid(q, 12, 6, 14, 10, 1, max);
        for (int[] s : spots) {
            Camp c = camp(s[0], s[1], 0);
            c.containerWall(0, 1, 11, 1, 1 + rng.nextInt(3), 0.1);
            if (chance() < 0.85) {
                c.containerWall(0, 4, 11, 4, 1 + rng.nextInt(3), 0.1);
            }
        }
        yardAround(spots, 12, 6, 2);
    }

    /** Pallets of crates under their nets in rows. */
    private void crateYard(Quad q, int max) {
        List<int[]> spots = grid(q, 14, 9, 17, 12, 1, max);
        for (int[] s : spots) {
            camp(s[0] + 1, s[1] + 1, 0).crates(0, 0, 12, 7);
        }
        yardAround(spots, 14, 9, 2);
    }

    /**
     * Streets of tents: rows 6 x 10 two apart, a dirt street four wide in front of each row,
     * every tent's door on the street.
     */
    private void tentGrid(Quad q, String canvas, int max) {
        List<int[]> spots = grid(q, 6, 10, 8, 14, 1, max);
        for (int[] s : spots) {
            camp(s[0], s[1], 6, 10, q.facing()).tent(0, 0, 10, canvas, true);
            int sz0 = q.north ? s[1] + 10 : s[1] - 4;
            yardOver(s[0] - 1, sz0, s[0] + 7, sz0 + 3);
        }
    }

    /** Supply yard: a warehouse, containers stacked, crates under camo; with the dead, the pit and the trench at the back. */
    private void supplyYard(Quad q, boolean dead) {
        int[] wh = room(q, 40, 26, 3, false);
        if (wh != null) {
            warehouse(lot(wh[0], wh[1] + (q.north ? 0 : 6), 40, 20, q.facing()), 40, 20);
        }
        if (dead) {
            graves(q);
        }
        crateYard(q, 3 + rng.nextInt(4));
        containerYard(q, 14 + rng.nextInt(12));
    }

    /** The far corner of a quarter: the burn pit, the trench of body bags, a few proper graves. */
    private void graves(Quad q) {
        int[] bp = room(q, 13, 13, 3, true);
        if (bp != null) {
            camp(bp[0] + 6, bp[1] + 6, 0).burnPit(0, 0, 4);
        }
        int[] tr = room(q, 34, 7, 3, true);
        if (tr != null) {
            camp(tr[0], tr[1] + 3, 0).trench(0, 0, 22);
        }
        int[] gv = room(q, 14, 10, 2, true);
        if (gv != null) {
            Camp c = camp(gv[0], gv[1], 0);
            for (int x = 1; x < 13; x += 3) {
                for (int z = 2; z < 9; z += 4) {
                    if (chance() < 0.8) {
                        c.grave(x, z);
                    }
                }
            }
        }
    }

    /** Floodlight masts down the cross roads. */
    private void masts() {
        Camp c = camp(0, 0, 0);
        int off = gateHalf + 1;
        for (int x = fx0 + 20; x < fx1 - 20; x += 32) {
            if (Math.abs(x - gx) > 12 && kit.use(x, gz - off) != Kit.ROAD) {
                c.floodMast(x, gz - off, 10);
            }
        }
        for (int z = fz0 + 20; z < fz1 - 20; z += 32) {
            if (Math.abs(z - gz) > 12 && kit.use(gx + off, z) != Kit.ROAD) {
                c.floodMast(gx + off, z, 10);
            }
        }
    }

    /** A warehouse bw x bd in its frame: corrugated walls 8 high, dock doors on the front, racks inside. */
    private void warehouse(Placer s, int bw, int bd) {
        Bldg b = new Bldg(s, rng);
        b.damage = 0.4 + hit * 0.5;
        String clad = any("corrugated_grey", "corrugated_green", "corrugated_rust");
        b.shell(0, 0, bw - 1, bd - 1, 1, 8, clad, "floor_concrete", "floor_concrete");
        for (int i = 0; i * 7 + 8 < bw; i++) {
            int x = 3 + i * 7;
            for (int k = 0; k < 4; k++) {
                for (int y = 1; y <= 4; y++) {
                    s.set(x + k, y, 0, i % 2 == 1 && chance() < 0.6 ? "air" : "garage_door[facing=north]");
                }
            }
            s.set(x + 1, 6, -1, Camp.flood(rng, 0.5));
            if (chance() < 0.5) {
                s.vehicle(x + 2, 1, -7, 0, "box_truck,moving_truck", rng.nextLong());
            }
        }
        b.flat(0, 0, bw - 1, bd - 1, 8, clad, 1, "minecraft:smooth_stone_slab[type=bottom]");
        for (int x = 3; x < bw - 3; x += 4) {
            for (int z = 4; z < bd - 2; z++) {
                for (int y = 1; y <= 3; y++) {
                    double r = chance();
                    s.set(x, y, z, r < 0.4 ? "crate" : r < 0.6 ? "cardboard_box" : r < 0.7 ? "fm_drum[loot=storage]" : "air");
                }
                s.set(x, 4, z, "steel_olive");
            }
        }
        b.lights(1, 1, bw - 2, bd - 2, 7, 0);
    }

    // ------------------------------------------------------------------ the quarantine zone

    private void quarantine(List<Quad> quads) {
        perimeter("containers", 60, "quarantine");
        // two quarters of tents, the hospital, the supplies and the dead
        Quad tentsA = quads.get(0), tentsB = quads.get(1), med = quads.get(2), back = quads.get(3);
        tentCity(tentsA);
        tentCity(tentsB);
        // the processing line off the road, then the hospital
        int[] pl = room(med, 10, 30, 2, false);
        if (pl != null) {
            camp(pl[0], pl[1], 10, 30, med.facing()).processing(0, 2, 24);
        }
        int[] h = room(med, 18, 34, 3, false);
        if (h != null) {
            camp(h[0], h[1], 18, 34, med.facing()).hospital(0, 0, 18, 34);
        }
        for (int i = 0; i < 2; i++) {
            int[] t = room(med, 10, 12, 2, false);
            if (t != null) {
                camp(t[0], t[1], 10, 12, med.facing()).hospital(0, 0, 10, 12);
            }
        }
        int[] pad = room(med, 15, 17, 3, true);
        if (pad != null) {
            Camp c = camp(pad[0], pad[1], 0);
            c.helipad(0, 1);
            c.helicopter(7, 2, hit > 0.55);
        }
        for (int i = 0; i < 4; i++) {
            int[] a = room(med, 6, 9, 1, false);
            if (a != null) {
                lot(a[0], a[1], 6, 9, med.facing()).vehicle(3, 1, 4.5, 0, "ambulance,ambulance_van", rng.nextLong());
            }
        }
        // the overflow: white tents for the sick
        tentGrid(med, "minecraft:white_wool", 6 + rng.nextInt(10));
        int[] f = room(back, 20, 13, 3, false);
        if (f != null) {
            camp(f[0] + 1, f[1] + 3, 0).fuelTanks(0, 0, 2);
        }
        graves(back);
        crateYard(back, 2 + rng.nextInt(3));
        containerYard(back, 4 + rng.nextInt(6));
        for (Quad q : quads) {
            litter(q, 12 + rng.nextInt(10));
        }
        masts();
    }

    /** A quarter of tents in streets, latrine rows, water tanks, washing lines. */
    private void tentCity(Quad q) {
        for (int i = 0; i < 2; i++) {
            int[] t = room(q, 12, 2, 2, i == 1);
            if (t != null) {
                camp(t[0], t[1], 0).toilets(0, 0, 6);
            }
        }
        for (int i = 0; i < 2; i++) {
            int[] wt = room(q, 3, 3, 1, i == 1);
            if (wt != null) {
                for (int y = 1; y <= 2; y++) {
                    fill(wt[0], y, wt[1], wt[0] + 2, y, wt[1] + 2, y == 2 ? "water_tank_top" : "water_tank");
                }
            }
        }
        tentGrid(q, chance() < 0.8 ? "tarp_green" : "minecraft:white_wool", 24 + rng.nextInt(24));
    }

    // ------------------------------------------------------------------ the depot

    private void depot(List<Quad> quads) {
        perimeter("hesco", 55, "armydepot");
        for (int k = 0; k < 2; k++) {
            Quad q = quads.get(k);
            for (int i = 0; i < 2; i++) {
                int[] wh = room(q, 36, 26, 3, false);
                if (wh != null) {
                    warehouse(lot(wh[0], wh[1] + (q.north ? 0 : 6), 36, 20, q.facing()), 36, 20);
                } else if ((wh = room(q, 24, 20, 3, false)) != null) {
                    warehouse(lot(wh[0], wh[1] + (q.north ? 0 : 6), 24, 14, q.facing()), 24, 14);  // a small depot's shed
                } else {
                    break;
                }
            }
            supplyYard(q, false);
        }
        Quad yard = quads.get(2);
        crateYard(yard, 2 + rng.nextInt(3));
        containerYard(yard, 20);
        Quad pool = quads.get(3);
        for (int i = 0; i < 2; i++) {
            int[] s = room(pool, 36, 9, 3, false);
            if (s != null) {
                camp(s[0], s[1], 0).shed(0, 0, 36);
            }
        }
        int[] f = room(pool, 29, 13, 3, false);
        if (f != null) {
            camp(f[0] + 1, f[1] + 3, 0).fuelTanks(0, 0, 3);
        }
        int[] o = room(pool, 24, 10, 3, false);
        if (o != null) {
            camp(o[0], o[1], 24, 10, pool.facing()).barracks(24, 10, "concrete_panel", "minecraft:cobbled_deepslate");
        }
        int[] ar = room(pool, 18, 13, 3, false);
        if (ar != null) {
            camp(ar[0], ar[1] + (pool.north ? 0 : 3), 18, 10, pool.facing()).armoury(18, 10);
        }
        graves(pool);
        bays(pool, 4 + rng.nextInt(4));
        for (Quad q : quads) {
            litter(q, 8 + rng.nextInt(8));
        }
        masts();
    }
}
