package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Pelican Point Shipyard (Josiah, 2026-10-30: the coast and industry places, this one first), on
 * the shore north of the highway's bend east of Grayhaven, after refs/shipyard:
 * <ul>
 * <li>No. 1 Dry Dock, a graving dock cut into the yard with its stepped granite altars, a tanker
 * in it on her keel blocks with the shores to her sides, the caisson across its mouth holding the
 * sea out, and the goliath crane with the yard's name down its girder straddling it
 * ({@link ShipyardDocks});</li>
 * <li>No. 2 Berth, the slipway beside it, hull 1047 on the ways in her primer, the bunting and the
 * bottle hung for the launch that was due in a week; the tower cranes either side;</li>
 * <li>the fitting-out jetty out into the basin with a ship alongside being finished, its
 * crane;</li>
 * <li>the panel line and the block assembly hall, their cranes and the work left in them, the
 * blocks waiting on their stools between the halls and the berths, the plate yard and its magnet
 * crane, the blast and prime shop ({@link ShipyardHalls});</li>
 * <li>the main office with the company's name across it, the canteen, the car park, the gas
 * tanks, the substation, the gate on the road in.</li>
 * </ul>
 * When it went the crew of the tanker in the dock pulled the gangways up and lived on her, the
 * dock their moat; what came after them is down on its floor. Nobody else got out by sea.
 *
 * <p>Drawn whole in its own frame ({@link FrameSite}) with its origin on the quay's line, the yard
 * to the south (z 0 to Z1) levelled a little over the sea, the basin to the north of it (BZ0 to
 * -1) dredged and left as water in tiles of its own.
 */
final class Shipyard extends FrameSite {
    /** The frame's origin, the plan's spot moved to the quay's line north of it. */
    static final int RX = 5880, RZ = 1512;
    /** The yard, the basin north of it, the tiles. */
    static final int X0 = -300, X1 = 299, Z0 = 0, Z1 = 447, BZ0 = -200, TILE_W = 200;
    /** The sea's top block and the dredged bed, under the quay's top (the yard's ground, y 0). */
    static final int SEA = Ships.SEA, BED = Ships.BED;

    Shipyard(Place p, BlockSink out) {
        super(p, out, 0x5A1BDL);
    }

    static List<Place> places(Plan.Spot s, County county, Plan plan, long h) {
        Plan.Spot o = new Plan.Spot(s.id(), s.kind(), PlaceType.LANDMARK, "shipyard", RX, RZ, s.flags(), s.name());
        List<Place> land = FrameSite.tiles("shipyard", o, county, plan, h, X0, Z0, X1, Z1, TILE_W);
        List<Place> out = new ArrayList<>();
        if (land.isEmpty()) {
            return out;
        }
        // the yard a little over the sea, whatever the land round it does, so the quay stands right
        int level = Terrain.WATER + 3;
        String v = land.get(0).variant;
        for (Place t : land) {
            out.add(new Place(PlaceType.LANDMARK, v, t.x0, t.z0, t.x1, t.z1, level, h, -1, -1));
        }
        for (int a = X0; a <= X1; a += TILE_W) {
            out.add(new Place(PlaceType.LANDMARK, v, RX + a, RZ + BZ0, RX + a + TILE_W - 1, RZ - 1, level, h, 0, -BZ0));
        }
        return out;
    }

    @Override
    void build() {
        List<int[]> g = gates;
        copy(piece("shipyard", 0x5A1B1L, (q, r) -> new Yard(q, r, g, seed).build()));
    }

    /** The shipyard, drawn whole in the frame. */
    static final class Yard extends Grounds {
        @Override
        String signs() {
            return "sy_sgn_";
        }

        final List<int[]> gates;
        final Noise noise;
        final Camp camp;
        final Harbour hb;
        final double hit = 0.5;
        /** The gate's x on the south fence, and the main road's half width. */
        final int gx;
        static final int ROAD = 4;
        /** The spine road across the yard (its middle z), the road along the plate yard's south. */
        static final int SPINE = 262, BACK = 352;

        Yard(Placer p, SplittableRandom rng, List<int[]> gates, long seed) {
            super(p, rng, X1 - X0 + 1, Z1 - BZ0 + 1, 0.45, 0.2, 0.1);
            this.gates = gates;
            this.noise = new Noise(seed);
            this.camp = new Camp(p, rng, hit);
            this.hb = new Harbour(p, rng, hit);
            int x = 0;
            for (int[] g : gates) {
                if (g[2] == 2) {
                    x = Math.max(X0 + 20, Math.min(X1 - 20, g[0]));
                }
            }
            this.gx = x;
        }

        /** A camp helper drawing in a building's frame: its front along local z = 0 facing that way (0 north .. 3 west). */
        Camp at(int x0, int z0, int bw, int bd, int facing) {
            return new Camp(frame(x0, z0, bw, bd, facing), rng, hit);
        }

        Placer frame(int x0, int z0, int bw, int bd, int facing) {
            return switch (facing & 3) {
                case 0 -> p.turned(x0, 0, z0, 0);
                case 1 -> p.turned(x0 + bd - 1, 0, z0, 1);
                case 2 -> p.turned(x0 + bw - 1, 0, z0 + bd - 1, 2);
                default -> p.turned(x0, 0, z0 + bw - 1, 3);
            };
        }

        void build() {
            ground();
            roads();
            quay();
            ShipyardDocks docks = new ShipyardDocks(this);
            docks.build();
            new ShipyardHalls(this).build();
            office();
            canteen();
            carPark();
            services();
            forecourt(docks);
            fence();
            gate();
            story();
        }

        // ------------------------------------------------------------------ the ground

        boolean inBox(int x, int z, int x0, int z0, int x1, int z1) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }

        /** Concrete hardstanding over the whole yard, its slabs' joints, the weeds up them, oil and rust on it. */
        private void ground() {
            for (int x = X0; x <= X1; x++) {
                for (int z = Z0; z <= Z1; z++) {
                    double n = noise.fbm(x, z, 30, 3) * 0.5 + 0.5, r = chance();
                    boolean joint = Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0;
                    String g = n > 0.78 && r < 0.5 ? "minecraft:moss_block" : joint && r < 0.25 ? "floor_concrete_moss"
                            : r < 0.04 ? "floor_concrete_crack" : r < 0.07 ? "concrete_dark_stain" : n < 0.25 && r < 0.3 ? "floor_concrete_b"
                            : "floor_concrete";
                    set(x, 0, z, g);
                    if (joint && r < 0.04) {
                        set(x, 1, z, r < 0.015 ? "weeds_tall" : "grass_tuft");
                    }
                }
            }
        }

        /** Asphalt from x0, z0 to x1, z1, its centre line along the long way. */
        void road(int x0, int z0, int x1, int z1) {
            boolean alongX = Math.abs(x1 - x0) > Math.abs(z1 - z0);
            int mid = alongX ? (z0 + z1) / 2 : (x0 + x1) / 2;
            for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                    double r = chance();
                    boolean line = (alongX ? z == mid : x == mid) && Math.floorMod(alongX ? x : z, 6) < 3;
                    set(x, 0, z, line && r > 0.2 ? "af_paint_y" : r < 0.07 ? "asphalt_crack" : r < 0.1 ? "asphalt_patch" : r < 0.13 ? "asphalt_moss" : "asphalt");
                    set(x, 1, z, "air");
                }
            }
        }

        /** The main road in from the gate, the spine across the yard, the back road, the quay road. */
        private void roads() {
            road(gx - ROAD, SPINE - ROAD, gx + ROAD, Z1);
            road(X0 + 6, SPINE - ROAD, X1 - 6, SPINE + ROAD);
            road(-190, BACK - ROAD, X1 - 6, BACK + ROAD);
            road(-100, 8, X1 - 6, 15);
            road(-110, 8, -102, SPINE);
            road(92, 8, 98, SPINE);
            road(238, 8, 244, SPINE);
        }

        // ------------------------------------------------------------------ the quay

        /** The quay's face along z = 0 wherever the yard meets the basin: the dock's mouth, the berth's and the jetty's roots aside. */
        private void quay() {
            int[][] runs = {{X0, ShipyardDocks.DX0 - 1}, {ShipyardDocks.DX1 + 1, ShipyardDocks.SLX0 - 1}, {ShipyardDocks.SLX1 + 1, X1}};
            for (int[] r : runs) {
                hb.quay(r[0], r[1], 0);
            }
            // the cut ends of the yard's ground down to the bed at the basin's sides
            for (int z = BZ0; z < 0; z++) {
                for (int x : new int[] {X0, X1}) {
                    for (int y = BED; y <= 0; y++) {
                        set(x, y, z, y == 0 ? "concrete_panel" : y > SEA ? "concrete_panel_stain" : y >= SEA - 1 ? "concrete_panel_moss" : "concrete_dark");
                    }
                }
            }
        }

        // ------------------------------------------------------------------ the office, the canteen, the car park

        /** The main office: three storeys of brick facing the main road, the name along its front, the flags, the boardroom's models. */
        private void office() {
            int x0 = gx - 70, x1 = gx - 14, z0 = 372, z1 = 398, st = 4;
            Bldg ob = new Bldg(p, rng);
            ob.damage = 0.25;
            ob.boarded = 0.15;
            ob.shell(x0, z0, x1, z1, 3, st, "brick_dark", "un_slab_vct", "un_slab_vct");
            for (int k = 0; k < 3; k++) {
                int y = 2 + k * st;
                ob.windowRow(x0 + 2, x1 - 2, 3, z0, y, "north", 2, "stone_trim");
                ob.windowRow(x0 + 2, x1 - 2, 3, z1, y, "south", 2, "stone_trim");
                ob.windowRow(z0 + 2, z1 - 2, 3, x1, y, "east", 2, "stone_trim");
                ob.windowRow(z0 + 2, z1 - 2, 3, x0, y, "west", 2, "stone_trim");
            }
            ob.flat(x0, z0, x1, z1, 3 * st, "brick_dark", 1, "stone_trim");
            // the entrance on the road: the doors, the canopy, the name across the parapet
            int ez = (z0 + z1) / 2;
            fill(x1, 1, ez - 2, x1, 3, ez + 1, "air");
            doors(x1, 0, ez - 1, Doors.SHOP, "east");
            for (int z = ez - 3; z <= ez + 2; z++) {
                set(x1 + 1, 4, z, "ml_precast_band");
                set(x1 + 2, 4, z, "ml_precast_band");
                set(x1 + 1, 0, z, "floor_concrete");
            }
            row("sy_name_", 12, x1 + 1, 3 * st, ez - 6, "east", 0.04);
            set(x1 + 3, 1, ez - 5, "cv_flagpole_base");
            set(x1 + 3, 1, ez + 4, "cv_flagpole_base");
            for (int y = 2; y <= 9; y++) {
                set(x1 + 3, y, ez - 5, "cv_flagpole");
                set(x1 + 3, y, ez + 4, "cv_flagpole");
            }
            set(x1 + 3, 10, ez - 5, "cv_flag_us_top");
            set(x1 + 3, 10, ez + 4, chance() < 0.5 ? "cv_flag_state_top" : "cv_flagpole_top");
            sign("office", x1 + 1, 2, ez + 4, "east");
            // inside: the reception, the drawing office upstairs with its tables and the plans, the boardroom on top
            for (int k = 0; k < 3; k++) {
                int y = k * st;
                partZ(x0 + 18, z0 + 1, z1 - 1, y, st - 1, "drywall", "drywall");
                set(x0 + 18, y + 1, ez, "air");
                set(x0 + 18, y + 2, ez, "air");
                for (int x = x0 + 21; x < x1 - 3; x += 4) {
                    for (int z = z0 + 3; z < z1 - 2; z += 5) {
                        if (k == 0 && Math.abs(z - ez) < 3) {
                            continue;
                        }
                        set(x, y + 1, z, k == 1 ? "home_desk[facing=north]" : any("home_desk[facing=north]", "pk_desk_metal[facing=north]"));
                        set(x + 1, y + 1, z, k == 1 ? "paper" : any("filing_cabinet[facing=west]", "air", "cardboard_box"));
                    }
                }
                for (int z = z0 + 2; z < z1 - 1; z += 2) {
                    set(x0 + 1, y + 1, z, any("filing_cabinet[facing=east]", "filing_cabinet[facing=east]", "cardboard_box", "air"));
                }
                b.lights(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + st - 1, 0.0);
                mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y, 0.06);
            }
            set(x1 - 4, 1, ez - 3, "reception_desk[facing=east]");
            set(x1 - 4, 1, ez - 2, "reception_desk[facing=east]");
            // the boardroom's table and the ship models in their cases on it
            for (int x = x0 + 4; x <= x0 + 13; x++) {
                set(x, 2 * st + 1, ez, "home_desk[facing=north]");
                set(x, 2 * st + 1, ez + 1, "home_desk[facing=north]");
            }
            stair(x0 + 1, z1 - 10, false, new int[] {0, st, 2 * st, 3 * st}, "brick_dark", "un_roof");
        }

        /** The canteen and the locker rooms: one storey of block, its long windows, the tables inside. */
        private void canteen() {
            int x0 = gx + 14, x1 = gx + 62, z0 = 376, z1 = 396;
            Bldg cb = new Bldg(p, rng);
            cb.damage = 0.55;
            cb.shell(x0, z0, x1, z1, 1, 5, "ml_precast", "floor_concrete", "floor_concrete");
            cb.windowRow(x0 + 2, x1 - 2, 3, z0, 2, "north", 2, null);
            cb.windowRow(x0 + 2, x1 - 2, 3, z1, 2, "south", 2, null);
            cb.flat(x0, z0, x1, z1, 5, "ml_precast", 0, "ml_precast");
            fill(x0, 1, z0 + 9, x0, 2, z0 + 10, "air");
            doors(x0, 0, z0 + 9, Doors.FLAT, "west");
            sign("canteen", x0 - 1, 4, z0 + 7, "west");
            partZ(x0 + 32, z0 + 1, z1 - 1, 0, 4, "drywall", "drywall");
            set(x0 + 32, 1, z0 + 10, "air");
            set(x0 + 32, 2, z0 + 10, "air");
            for (int x = x0 + 3; x < x0 + 30; x += 4) {
                for (int z = z0 + 3; z < z1 - 2; z += 4) {
                    set(x, 1, z, chance() < 0.2 ? "air" : "store_table");
                    set(x + 1, 1, z, chance() < 0.4 ? "air" : "home_chair[facing=" + dir() + "]");
                }
            }
            for (int z = z0 + 2; z < z1 - 1; z++) {
                set(x0 + 30, 1, z, "fit_counter_steel[facing=west]");
            }
            // the lockers in rows past the wall
            for (int x = x0 + 35; x < x1 - 1; x += 3) {
                for (int z = z0 + 2; z < z1 - 1; z++) {
                    if (z != z0 + 10) {
                        set(x, 1, z, "sh_locker[facing=east]");
                        set(x, 2, z, "sh_locker_top[facing=east]");
                    }
                }
            }
            b.lights(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 4, 0.0);
            mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.08);
        }

        /** The car park west of the office: its bays, the workers' cars left in them, the lamp standards. */
        private void carPark() {
            int x0 = -280, x1 = gx - 82, z0 = 366, z1 = 434;
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double r = chance();
                    boolean bay = Math.floorMod(z - z0, 20) < 6 || Math.floorMod(z - z0, 20) >= 14;
                    boolean lineX = bay && Math.floorMod(x - x0, 3) == 0 && Math.floorMod(z - z0, 20) != 5 && Math.floorMod(z - z0, 20) != 14;
                    set(x, 0, z, lineX && r > 0.15 ? "road_line_white[axis=z]" : r < 0.06 ? "asphalt_crack" : r < 0.1 ? "asphalt_moss" : "asphalt_b");
                    set(x, 1, z, "air");
                }
            }
            for (int j = 0; j * 20 + z0 < z1; j++) {
                for (int x = x0 + 1; x < x1 - 2; x += 3) {
                    for (int side = 0; side < 2; side++) {
                        if (chance() < 0.45) {
                            int z = z0 + j * 20 + (side == 0 ? 2 : 17);
                            p.car(x + 0.5, 1, z + 0.5, side == 0 ? 0f : 180f, 4, 5.5, rng.nextLong());
                        }
                    }
                }
            }
            for (int x = x0 + 12; x < x1; x += 30) {
                for (int z = z0 + 10; z < z1; z += 40) {
                    lampPost(x, z);
                }
            }
        }

        /** The gas tanks behind their fence, the substation, the compressor house. */
        private void services() {
            // the bulk gases: oxygen, argon, nitrogen in their tall white tanks, the vaporizers by them
            for (int i = 0; i < 3; i++) {
                int cx = 150 + i * 14, cz = 380;
                Round.wall(p, cx + 0.5, cz + 0.5, 2.6, 1, 16, "oil", 0);
                Round.roof(p, cx + 0.5, cz + 0.5, 2.6, "ssm", 17, "oil");
                for (int x = cx - 3; x <= cx + 3; x++) {
                    for (int z = cz - 3; z <= cz + 3; z++) {
                        set(x, 0, z, "concrete_dark");
                    }
                }
                for (int y = 1; y <= 8; y++) {
                    set(cx + 5, y, cz - 2, "sy_vapor");
                    set(cx + 5, y, cz + 2, "sy_vapor");
                }
                set(cx, 6, cz - 3, "sy_sgn_gas_0[facing=north]");
            }
            ring(144, 372, 196, 390, 0.1, 168);
            // the substation: its transformers in their bays behind the wire
            for (int i = 0; i < 3; i++) {
                int x = 110 + i * 9;
                for (int y = 1; y <= 4; y++) {
                    for (int dx = 0; dx < 4; dx++) {
                        for (int dz = 0; dz < 3; dz++) {
                            set(x + dx, y, 376 + dz, y == 4 ? "pw_tg_plate" : "steel_olive");
                        }
                    }
                }
                set(x + 1, 5, 377, "pl_transformer");
                set(x + 2, 5, 377, "pole_top");
            }
            ring(106, 372, 138, 384, 0.0, 120);
            // the compressor house
            Bldg ch = new Bldg(p, rng);
            ch.damage = 0.4;
            ch.shell(108, 392, 136, 402, 1, 6, "brick_dark", "floor_concrete", "floor_concrete");
            ch.flat(108, 392, 136, 402, 6, "brick_dark", 0, "stone_trim");
            fill(122, 1, 392, 123, 3, 392, "air");
            for (int x = 111; x < 134; x += 6) {
                set(x, 1, 397, "pw_drum[axis=x]");
                set(x + 1, 1, 397, "pw_drum_end[facing=east]");
            }
        }

        // ------------------------------------------------------------------ the fence and the gate

        /**
         * The way in from the gate: the lamp standards down the main road, the old propeller on its
         * plinth that every yard sets by its gate with the hull count under it, the benches and
         * planters round it, and a transporter left parked with its last load of plate.
         */
        private void forecourt(ShipyardDocks docks) {
            for (int z = SPINE + 8; z < Z1 - 10; z += 14) {
                lampPost(gx - ROAD - 2, z);
                lampPost(gx + ROAD + 2, z + 7);
            }
            int mx = gx - ROAD - 22, mz = Z1 - 26;
            for (int x = mx - 5; x <= mx + 5; x++) {
                for (int z = mz - 2; z <= mz + 2; z++) {
                    set(x, 0, z, "floor_concrete");
                    boolean plinth = Math.abs(x - mx) <= 4 && Math.abs(z - mz) <= 1;
                    set(x, 1, z, plinth ? "om_granite" : Math.abs(x - mx) == 5 || Math.abs(z - mz) == 2 ? "planter" : "air");
                    set(x, 2, z, plinth && Math.abs(x - mx) <= 3 ? "om_granite" : "air");
                }
            }
            docks.propeller(mx, 6, mz, 3);
            set(mx, 3, mz, "om_granite");
            sign("proud", mx - 1, 2, mz + 2, "south");
            for (int dx : new int[] {-3, 3}) {
                set(mx + dx, 1, mz + 4, "st_bench[facing=north]");
            }
            // the transporter where it stopped, the plates still chained on it
            int tx = gx + ROAD + 10, tz = Z1 - 40;
            for (int x = tx; x < tx + 3; x++) {
                for (int z = tz; z < tz + 8; z++) {
                    set(x, 1, z, "sy_spmt[facing=south]");
                    if (z > tz && z < tz + 7) {
                        set(x, 2, z, z % 3 == 0 ? "sy_plates_low[axis=z]" : "sy_plates[axis=z]");
                    }
                }
            }
        }

        /** The chain-link and wire round the yard's land sides, opened at the gate. */
        private void fence() {
            camp.fence(X0 + 1, 2, X0 + 1, Z1 - 1, 0.04);
            camp.fence(X1 - 1, 2, X1 - 1, Z1 - 1, 0.04);
            camp.fence(X0 + 1, Z1 - 1, X1 - 1, Z1 - 1, 0.04);
            fill(gx - ROAD - 1, 1, Z1 - 2, gx + ROAD + 1, 4, Z1, "air");
        }

        /** Four straight runs of wire round a box (Camp.fence draws one run), with a
         *  4-wide gap in the north side at gateX. */
        private void ring(int x0, int z0, int x1, int z1, double breach, int gateX) {
            camp.fence(x0, z0, x1, z0, breach);
            camp.fence(x0, z1, x1, z1, breach);
            camp.fence(x0, z0, x0, z1, breach);
            camp.fence(x1, z0, x1, z1, breach);
            fill(gateX - 2, 1, z0, gateX + 1, 4, z0, "air");
        }

        /** The gatehouse, the barriers and the turnstiles; the gate barricaded with steel plate and a forklift's worth of junk. */
        private void gate() {
            int z = Z1 - 8;
            Camp g = at(gx + ROAD + 2, z - 4, 6, 5, 3);
            g.booth(0, 0);
            camp.boom(gx - ROAD, z, ROAD * 2 + 1, chance() < 0.6);
            for (int x = gx - ROAD - 6; x < gx - ROAD - 1; x++) {
                set(x, 0, z, "floor_concrete");
                set(x, 1, z, x % 2 == 0 ? "st_turnstile[facing=south]" : "chainlink");
                set(x, 2, z, "chainlink");
            }
            sign("gate", gx + ROAD + 2, 3, Z1 - 1, "south");
            sign("closed", gx - 1, 2, Z1 - 3, "south");
            sign("hats", gx - ROAD - 6, 3, Z1 - 1, "south");
            // the barricade: plates on edge, a skip, drums, across the road inside the gate
            for (int x = gx - ROAD; x <= gx + ROAD; x++) {
                if (chance() < 0.75) {
                    set(x, 1, z - 6, "sy_plates[axis=x]");
                    if (chance() < 0.5) {
                        set(x, 2, z - 6, "sy_plates_low[axis=x]");
                    }
                }
            }
            set(gx + 2, 1, z - 7, "pw_drum[axis=z]");
            set(gx - 3, 1, z - 7, "jersey_barrier[facing=south]");
            camp.floodMast(gx + ROAD + 9, z - 2, 9);
        }

        // ------------------------------------------------------------------ the story

        /** The bodies by the gate and in the yard, the litter, the cars left at the halls. */
        private void story() {
            for (int i = 0; i < 26; i++) {
                int x = X0 + 10 + rng.nextInt(X1 - X0 - 20), z = 20 + rng.nextInt(Z1 - 40);
                if (empty(x, 1, z) && get(x, 0, z) != null && !get(x, 0, z).startsWith("sy_")) {
                    set(x, 1, z, chance() < 0.4 ? "home_corpse_head[facing=" + dir() + "]" : chance() < 0.5 ? "blood_splat" : "trash_bag[facing=" + dir() + "]");
                }
            }
            for (int i = 0; i < 6; i++) {
                int x = gx - ROAD + rng.nextInt(ROAD * 2), z = Z1 - 20 - rng.nextInt(20);
                p.car(x + 0.5, 1, z + 0.5, rng.nextInt(4) * 90f, 4, 6, rng.nextLong());
            }
        }
    }
}
