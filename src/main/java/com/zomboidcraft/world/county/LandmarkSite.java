package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;

/**
 * A city landmark on a lot of its own:
 * <ul>
 * <li>stadium: Grayhaven Stadium ({@link Stadium}) in the middle of its plaza, an avenue in from
 * each side to its gates, the parking lots round it in rows under their light poles, still half
 * full of the cars people drove to the shelter, the ones that got no further left across the
 * avenues.</li>
 * <li>union_station: Grayhaven Union Station, the railway's terminal and the change to the subway:
 * the head house ({@link UnionStation}) with its front to the street the subway runs under (the
 * passage to the subway's platform, RailGen's, coming in to it), the train hall ({@link UnionHall})
 * beside it over the el's old street, the el running through its top floor, and the bus and taxi
 * terminal past that.</li>
 * <li>mall: Grayhaven Mall ({@link Mall}), the island's galleria between its two department
 * stores, with its car park and parking decks.</li>
 * <li>mercy: Mercy General Hospital ({@link Hospital}), the brick podium with the ER and its
 * ambulance bay, the white ward tower over it, the service wing and the staff lot behind.</li>
 * <li>university: Grayhaven State University ({@link University}), its halls round the quad
 * behind the gate, Old Main's clock tower closing the south end, the dorm tower by the gate.</li>
 * <li>civic: the Grayhaven Civic Center ({@link CivicCentre}), City Hall's tower behind its plaza
 * and reflecting pool, the council chamber's drum beside it, the relief station's tents.</li>
 * </ul>
 */
final class LandmarkSite extends Site {
    private Kit kit;
    private int[] camp;

    LandmarkSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        if (variant("union_station")) {
            // the front to the south: the head house from x = 0, the train hall over the el, the terminal to the lot's far side
            Placer q = lot(0, 0, w, d, 2);
            UnionStation head = new UnionStation(q, rng);
            head.build();
            new UnionHall(q, rng, w, d, head.power()).build();
            return;
        }
        if (variant("mall")) {
            mall();
            return;
        }
        if (variant("mercy")) {
            new Hospital(lot(0, 0, w, d, 0), rng, w, d).build();
            return;
        }
        if (variant("university")) {
            new University(lot(0, 0, w, d, 0), rng, w, d).build();
            return;
        }
        if (variant("civic")) {
            new CivicCentre(lot(0, 0, w, d, 0), rng, w, d).build();
            return;
        }
        kit = new Kit(out, rng, w, d);
        stadium();
    }

    /** Grayhaven Mall ({@link Mall}) on its lot, the ground round it paved. */
    private void mall() {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                boolean walk = x < 4 || z < 4 || x >= w - 4 || z >= d - 4;
                out.set(x, 0, z, walk ? (chance() < 0.1 ? "sidewalk_cracked" : "sidewalk") : chance() < 0.2 ? "asphalt_crack" : "asphalt");
            }
        }
        new Mall(lot(0, 0, w, d, 0), rng, w, d).build();
    }

    /** The parking rows: twenty from aisle to aisle, a band of stalls six deep each side of it, three wide. */
    private static final int ROW = 20, STALL = 6, BAY = 3;

    private void stadium() {
        int cx = w / 2, cz = d / 2;
        // the avenues in to the four gates, stopping at the plaza
        int px = Stadium.EX + 10, pz = Stadium.EZ + 10;
        Kit.Lane[] avenues = {
                kit.lane(Kit.Style.MAIN, kit.beyond(0)[0], kit.beyond(0)[1], cx + 0.5, cz - pz),
                kit.lane(Kit.Style.MAIN, kit.beyond(2)[0], kit.beyond(2)[1], cx + 0.5, cz + pz),
                kit.lane(Kit.Style.MAIN, kit.beyond(1)[0], kit.beyond(1)[1], cx + px, cz + 0.5),
                kit.lane(Kit.Style.MAIN, kit.beyond(3)[0], kit.beyond(3)[1], cx - px, cz + 0.5)};
        kit.pave();
        camp = Stadium.camp(cx, cz, w, d);
        // the plaza round the stadium and under the ramp towers, the lots beyond in their rows
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                if (kit.use(x, z) != Kit.FREE) {
                    continue;
                }
                double e = Stadium.edge(x - cx + 0.5, z - cz + 0.5);
                if (e <= Stadium.FACADE + 1) {
                    continue;
                }
                if (e < Stadium.FACADE + 14 || Stadium.nearRamp(x - cx + 0.5, z - cz + 0.5, 14) || Stadium.inCamp(camp, x - cx, z - cz)) {
                    out.set(x, 0, z, chance() < 0.1 ? "sidewalk_cracked" : "sidewalk");
                    continue;
                }
                int zz = Math.floorMod(z, ROW);
                boolean stalls = zz < STALL || zz >= ROW - STALL;
                out.set(x, 0, z, stalls && x % BAY == 0 && lot(x, z, cx, cz) ? "road_line_white[axis=z]" : kit.asphalt(0.35));
            }
        }
        new Stadium(out, rng, cx, cz).withCamp(camp).build();
        // the cars in their stalls, nose in from the aisle: one stall in sixteen still taken (every
        // car is an entity, and a lot this size full of them would cost frames)
        for (int x = BAY; x + BAY < w; x += BAY) {
            for (int z = 0; z < d; z++) {
                int zz = Math.floorMod(z, ROW);
                if (zz != 0 && zz != ROW - STALL || chance() > 0.06) {
                    continue;
                }
                boolean fits = true;
                for (int i = 1; i < BAY && fits; i++) {
                    for (int j = 0; j < STALL; j++) {
                        fits &= lot(x + i, z + j, cx, cz);
                    }
                }
                if (fits) {
                    out.vehicle(x + BAY / 2.0 + 0.5, 1, z + STALL / 2.0, (zz == 0 ? 180 : 0) + (float) (rng.nextDouble() * 10 - 5),
                            "sedan,suv,pickup,hatchback,minivan,wagon,sedan,van", rng.nextLong());
                }
            }
        }
        // the light poles down the aisles, a few still lit
        for (int x = 16; x < w; x += 33) {
            for (int z = 0; z < d; z++) {
                if (Math.floorMod(z, ROW) != ROW / 2 || !lot(x, z, cx, cz) || !lot(x + 1, z, cx, cz) || !lot(x - 1, z, cx, cz)) {
                    continue;
                }
                out.set(x, 1, z, "st_lot_base");
                for (int y = 2; y <= 14; y++) {
                    out.set(x, y, z, "st_lot_pole");
                }
                out.set(x, 15, z, (chance() < 0.35 ? "st_lot_head_on" : "st_lot_head") + "[facing=" + (chance() < 0.5 ? "north" : "east") + "]");
            }
        }
        // the cars that got no further, nosed every way across the avenues
        for (Kit.Lane l : avenues) {
            for (double s = 6 + rng.nextDouble() * 6; s < l.length() - 8; s += 7 + rng.nextDouble() * 6) {
                double[] q = l.point(s);
                double side = (rng.nextDouble() - 0.5) * 2 * (l.style.half - 2);
                double x = q[0] - q[3] * side, z = q[1] + q[2] * side;
                float yaw = (float) Math.toDegrees(Math.atan2(-q[2], q[3])) + (float) (rng.nextDouble() * 50 - 25);
                if (Stadium.edge(x - cx, z - cz) > Stadium.FACADE + 30) {
                    out.car(x, 1, z, yaw, 4, 6.5, rng.nextLong());
                }
            }
        }
    }

    /** A lot cell: free ground well clear of the plaza, the ramp towers, the camp and the lot's edge. */
    private boolean lot(int x, int z, int cx, int cz) {
        if (x < 2 || z < 2 || x >= w - 2 || z >= d - 2 || kit.use(x, z) != Kit.FREE) {
            return false;
        }
        double px = x - cx + 0.5, pz = z - cz + 0.5;
        return Stadium.edge(px, pz) >= Stadium.FACADE + 16 && !Stadium.nearRamp(px, pz, 16) && !Stadium.inCamp(camp, x - cx, z - cz);
    }
}
