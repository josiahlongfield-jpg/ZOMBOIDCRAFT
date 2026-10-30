package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * Grayhaven International's cargo shed and fuel farm, west of the fire station, in the airport's
 * frame ({@link Airport}), after refs/airport/cargo-terminal and refs/airport/fuel-farm.
 *
 * <ul>
 * <li>The cargo shed: a long low warehouse of grey ribbed steel with its blue band, the roller
 * doors along the airside face onto its own apron with the containers still on their dollies, the
 * truck docks along the landside face onto the yard with the trucks backed up to them; inside, the
 * containers, the pallets of freight in their rows, the cages; the office block at its east end,
 * two floors, the counter where the forwarders signed for their freight.</li>
 * <li>The fuel farm north of it: six white tanks in their bund, the caged ladders, the pipes out
 * of each to the manifold, the rack over to the pump house, the fill stand under its canopy where
 * the bowsers loaded, the fence round it all.</li>
 * </ul>
 */
final class AirportCargo extends Grounds {
    /** The cargo shed's walls (the airside face at CZ1, the docks at CZ0), its roof, the office block from OX. */
    static final int CX0 = -900, CX1 = -760, CZ0 = -232, CZ1 = -176, CROOF = 12, OX = CX1 - 20, OS2 = 6;
    /** The fuel farm's bund. */
    static final int FX0 = -780, FX1 = -700, FZ0 = -330, FZ1 = -275;
    /** Everything this builds lies within these (in the frame). */
    static final int BX0 = CX0 - 4, BX1 = FX1 + 24, BZ0 = FZ0 - 5, BZ1 = Airport.TWY_Z - Airport.TWY_SHOULDER - 1;

    AirportCargo(Placer p, SplittableRandom rng) {
        super(p, rng, BX1 - BX0 + 1, BZ1 - BZ0 + 1, 0.3, 0.0, 0.25);
    }

    void build() {
        grounds();
        shed();
        warehouse();
        office();
        fuelFarm();
    }

    private String clad(int y) {
        double r = chance();
        return y <= 2 ? "concrete_panel_b" : y >= CROOF - 2 ? "corrugated_blue_b"
                : r < 0.05 ? "corrugated_grey_stain" : r < 0.3 ? "corrugated_grey_b" : "corrugated_grey";
    }

    private void win(int x, int y, int z, String out) {
        set(x, y, z, (chance() < 0.15 ? "wf_glass_broken" : "wf_glass") + "[facing=" + out + "]");
    }

    // ------------------------------------------------------------------ the cargo shed

    /** The walls, the roof on its beams, the doors both sides, the names along the fascias. */
    private void shed() {
        for (int x = CX0; x <= CX1; x++) {
            for (int z = CZ0; z <= CZ1; z++) {
                boolean wall = x == CX0 || x == CX1 || z == CZ0 || z == CZ1 || x == OX;
                boolean office = x > OX;
                set(x, 0, z, wall ? "concrete_dark" : office ? "un_slab_vct" : chance() < 0.02 ? "navy_hangar_oil" : "navy_hangar_floor");
                for (int y = 1; y < CROOF; y++) {
                    set(x, y, z, wall ? clad(y) : "air");
                }
                if (office && !wall) {
                    set(x, OS2, z, "un_slab_vct");
                }
                set(x, CROOF, z, "un_roof");
            }
        }
        b.flat(CX0, CZ0, CX1, CZ1, CROOF, "corrugated_grey", 0, null);
        for (int x = CX0; x <= CX1; x++) {
            for (int z = CZ0; z <= CZ1; z++) {
                if (x == CX0 || x == CX1 || z == CZ0 || z == CZ1) {
                    set(x, CROOF + 1, z, "corrugated_grey");
                    String f = z == CZ0 ? "north" : z == CZ1 ? "south" : x == CX0 ? "west" : "east";
                    boolean corner = (x == CX0 || x == CX1) && (z == CZ0 || z == CZ1);
                    set(x, CROOF + 2, z, corner ? "bd_coping_pc_corner" : "bd_coping_pc[facing=" + f + "]");
                }
            }
        }
        // the airside doors: roller shutters six wide, some up, a container on its dolly before each
        for (int k = 0; k < 8; k++) {
            int dx = CX0 + 6 + k * 15;
            boolean up = chance() < 0.45;
            for (int x = dx; x < dx + 6; x++) {
                set(x, 0, CZ1, "navy_hangar_floor");
                for (int y = 1; y <= 5; y++) {
                    set(x, y, CZ1, up ? (y == 5 ? "pt_shutter_grey" : "air") : "pt_shutter_grey");
                }
                set(x, 6, CZ1, "ml_precast_band");
            }
            set(dx - 1, 7, CZ1 + 1, (power && chance() < 0.4 ? "cp_flood_lit" : "cp_flood") + "[facing=south]");
            if (chance() < 0.75) {
                int z = CZ1 + 4 + rng.nextInt(4);
                set(dx + 2, 1, z, "rp_dolly_front[facing=south]");
                set(dx + 2, 1, z + 1, "rp_dolly_rear[facing=south]");
                set(dx + 2, 2, z, chance() < 0.2 ? "rp_uld_open[facing=north]" : "rp_uld[facing=north]");
            }
        }
        // the truck docks on the landside: ten doors four wide, the bumpers, the trucks backed in
        for (int k = 0; k < 9; k++) {
            int dx = CX0 + 5 + k * 13;
            boolean up = chance() < 0.4;
            for (int x = dx; x < dx + 4; x++) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, CZ0, up ? (y == 4 ? "pt_shutter_steel" : "air") : "pt_shutter_steel");
                }
                set(x, 5, CZ0, "ml_precast_band");
            }
            set(dx - 1, 1, CZ0 - 1, "vessel_bollard");
            set(dx + 4, 1, CZ0 - 1, "vessel_bollard");
            set(dx + 1, 6, CZ0 - 1, (power && chance() < 0.4 ? "cp_flood_lit" : "cp_flood") + "[facing=north]");
            if (chance() < 0.55) {
                p.vehicle(dx + 2.0, 1, CZ0 - 6.0, 180f, "box_truck,moving_truck,step_van", rng.nextLong());
            }
        }
        // the names along both fascias
        int nx = (CX0 + OX) / 2 - 5;
        for (int i = 0; i < 10; i++) {
            set(nx + i, CROOF - 3, CZ1 + 1, "ah_cargo_" + i + "[facing=south]");
            set(nx + 9 - i, CROOF - 3, CZ0 - 1, "ah_cargo_" + i + "[facing=north]");
        }
        // the clerestory along the ends
        for (int z = CZ0 + 3; z < CZ1 - 2; z++) {
            if (Math.floorMod(z - CZ0, 5) < 3) {
                win(CX0, CROOF - 4, z, "west");
            }
        }
    }

    /** The warehouse floor: the beams and the lights, the containers, the pallets in their rows, the cages, the roller beds. */
    private void warehouse() {
        for (int x = CX0 + 5; x < OX; x += 10) {
            for (int z = CZ0 + 1; z < CZ1; z++) {
                set(x, CROOF - 1, z, "sf_beam[axis=z]");
            }
            for (int z = CZ0 + 6; z < CZ1 - 2; z += 9) {
                set(x + 5, CROOF - 1, z, power && chance() < 0.35 ? "fit_highbay_on" : "fit_highbay");
            }
        }
        // the containers by the airside doors, some stacked, one burst
        for (int k = 0; k < 8; k++) {
            int dx = CX0 + 6 + k * 15;
            for (int i = 0; i < 4; i++) {
                if (chance() < 0.65) {
                    int x = dx + i + (i > 1 ? 1 : 0), z = CZ1 - 3 - rng.nextInt(3);
                    set(x, 1, z, chance() < 0.12 ? "rp_uld_spill[facing=south]" : "rp_uld[facing=south]");
                    if (chance() < 0.3) {
                        set(x, 2, z, "rp_uld[facing=south]");
                    }
                }
            }
        }
        // the freight on its pallets in rows across the middle, the aisles between
        for (int z = CZ0 + 10; z < CZ1 - 10; z += 5) {
            for (int x = CX0 + 3; x < OX - 2; x++) {
                if (Math.floorMod(x - CX0, 15) >= 12 || chance() < 0.1) {
                    continue;
                }
                for (int dz = 0; dz <= 1; dz++) {
                    set(x, 1, z + dz, chance() < 0.3 ? "st_pallet[facing=north]" : any("cardboard_box[facing=north]", "pk_crate_1", "pk_crate_2", "crate"));
                    if (chance() < 0.6) {
                        set(x, 2, z + dz, any("cardboard_box[facing=north]", "pk_crate_1", "crate"));
                        if (chance() < 0.4) {
                            set(x, 3, z + dz, "cardboard_box[facing=north]");
                        }
                    }
                }
            }
        }
        // the cages along the dock wall, the forwarders' parcels
        for (int x = CX0 + 3; x < OX - 2; x++) {
            if (Math.floorMod(x - CX0, 13) >= 5 && Math.floorMod(x - CX0, 13) <= 11) {
                set(x, 1, CZ0 + 2, any("bunk_rack_crates", "cardboard_box[facing=south]", "pk_crate_2"));
            }
        }
        mess(CX0 + 1, CZ0 + 1, OX - 1, CZ1 - 1, 0, 0.008);
    }

    /** The office block at the east end: the counter and the waiting seats downstairs, the desks upstairs. */
    private void office() {
        int x0 = OX + 1, x1 = CX1 - 1;
        for (int y0 : new int[] {0, OS2}) {
            for (int z = CZ0 + 2; z < CZ1 - 1; z++) {
                if (Math.floorMod(z - CZ0, 4) != 0) {
                    win(CX1, y0 + 2, z, "east");
                    win(CX1, y0 + 3, z, "east");
                }
            }
            for (int x = x0 + 1; x < x1; x++) {
                if (Math.floorMod(x - x0, 4) != 0) {
                    win(x, y0 + 2, CZ1, "south");
                    win(x, y0 + 3, CZ1, "south");
                }
            }
        }
        // the front door on the landside, the door through to the warehouse
        for (int x = x0 + 8; x <= x0 + 9; x++) {
            for (int y = 1; y <= 3; y++) {
                set(x, y, CZ0, "air");
            }
        }
        doors(x0 + 8, 0, CZ0, Doors.SHOP, "north");
        for (int z = CZ0 + 20; z <= CZ0 + 21; z++) {
            for (int y = 1; y <= 3; y++) {
                set(OX, y, z, "air");
            }
        }
        doors(OX, 0, CZ0 + 20, Doors.STEEL, "west");
        // the counter across the lobby, the seats, the notices
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            set(x, 1, CZ0 + 8, x == x0 + 9 ? "store_register[facing=north]" : "ap_counter[facing=north]");
        }
        for (int x = x0 + 2; x <= x0 + 6; x++) {
            set(x, 1, CZ0 + 2, "ap_seats[facing=south]");
        }
        set(x0 + 12, 2, CZ0 + 1, "ap_notice_fema[facing=south]");
        set(x1, 1, CZ0 + 12, "filing_cabinet[facing=west]");
        set(x1, 1, CZ0 + 13, "filing_cabinet[facing=west]");
        // the stair up
        stair(x0 + 1, CZ1 - 10, false, new int[] {0, OS2, CROOF}, "ml_precast", "un_roof");
        // upstairs: the desks in an open office, the manager's glass room
        int y = OS2 + 1;
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            for (int z = CZ0 + 3; z <= CZ1 - 14; z += 4) {
                set(x, y, z, "home_desk_pc[facing=north]");
                set(x, y, z + 1, (chance() < 0.2 ? "trade_folding_chair_tipped" : "store_chair") + "[facing=north]");
            }
        }
        lamps(x0, CZ0, x1, CZ1, OS2, 0.3);
        lamps(x0, CZ0, x1, CZ1, CROOF, 0.3);
        mess(x0, CZ0 + 1, x1, CZ1 - 1, 0, 0.03);
        mess(x0, CZ0 + 1, x1, CZ1 - 1, OS2, 0.03);
    }

    // ------------------------------------------------------------------ the fuel farm

    /** The tanks in their bund, the manifold and the rack, the pump house, the fill stand, the fence. */
    private void fuelFarm() {
        Harbour h = new Harbour(p, rng, 0.2);
        h.bund(FX0, FZ0, FX1, FZ1);
        int[] cxs = {FX0 + 13, FX0 + 40, FX0 + 67}, czs = {FZ0 + 13, FZ0 + 41};
        for (int cz : czs) {
            for (int cx : cxs) {
                h.tank(cx, cz, 7, 12 + rng.nextInt(4), "white");
            }
        }
        // the manifold along the bund's east side, the rack over to the pump house
        for (int z = FZ0 + 13; z <= FZ0 + 41; z++) {
            set(FX1 - 3, 1, z, z == FZ0 + 13 || z == FZ0 + 41 ? "tk_valve[axis=z]" : "tk_pipe[axis=z]");
        }
        h.pipeRack(FX1 - 3, FX1 + 6, FZ0 + 26, 5);
        // the pump house: a precast box, the pumps and their valves inside, the name over its door
        int px0 = FX1 + 4, px1 = FX1 + 17, pz0 = FZ0 + 4, pz1 = FZ0 + 14;
        for (int x = px0; x <= px1; x++) {
            for (int z = pz0; z <= pz1; z++) {
                boolean wall = x == px0 || x == px1 || z == pz0 || z == pz1;
                set(x, 0, z, wall ? "concrete_dark" : "floor_concrete");
                for (int y = 1; y < 6; y++) {
                    set(x, y, z, wall ? (y == 5 ? "ml_precast_band" : "ml_precast") : "air");
                }
                set(x, 6, z, "un_roof");
            }
        }
        for (int x = px0 + 2; x < px1 - 1; x += 3) {
            set(x, 1, pz0 + 2, "gs_compressor_lo[facing=south]");
            set(x, 2, pz0 + 2, "gs_compressor_hi[facing=south]");
            set(x, 1, pz0 + 4, "fit_fuel_valves[facing=south]");
        }
        set(px1 - 1, 1, pz1 - 1, "pw_breaker[facing=west]");
        for (int y = 1; y <= 3; y++) {
            set(px0 + 6, y, pz1, "air");
        }
        door(px0 + 6, 0, pz1, Doors.STEEL, "south");
        for (int i = 0; i < 10; i++) {
            set(px0 + 2 + i, 4, pz1 + 1, "ah_fuel_" + i + "[facing=south]");
        }
        // the fill stand: the canopy on its posts over two lanes, the loading valves at each
        int sx0 = FX1 + 4, sx1 = FX1 + 17, sz0 = FZ0 + 22, sz1 = FZ0 + 36;
        for (int x = sx0; x <= sx1; x++) {
            for (int z = sz0; z <= sz1; z++) {
                set(x, 0, z, "floor_concrete");
                set(x, 6, z, "tx_canopy");
            }
        }
        for (int[] c : new int[][] {{sx0, sz0}, {sx1, sz0}, {sx0, sz1}, {sx1, sz1}, {(sx0 + sx1) / 2, sz0}, {(sx0 + sx1) / 2, sz1}}) {
            for (int y = 1; y < 6; y++) {
                set(c[0], y, c[1], "steel_girder");
            }
        }
        for (int z = sz0 + 3; z <= sz1 - 3; z += 4) {
            set((sx0 + sx1) / 2, 1, z, "fit_fuel_valves[facing=east]");
            set((sx0 + sx1) / 2, 2, z, "fit_valve[facing=east]");
        }
        // the fence round it all, the gate on the south side left open
        int fx0 = FX0 - 3, fx1 = sx1 + 4, fz0 = FZ0 - 3, fz1 = FZ1 + 3;
        for (int x = fx0; x <= fx1; x++) {
            boolean gate = x >= sx0 + 2 && x <= sx1 - 2;
            fence(x, fz0, true);
            if (!gate) {
                fence(x, fz1, true);
            }
        }
        for (int z = fz0; z <= fz1; z++) {
            fence(fx0, z, false);
            fence(fx1, z, false);
        }
    }

    private void fence(int x, int z, boolean alongX) {
        boolean post = Math.floorMod(alongX ? x : z, 3) == 0;
        String run = "[axis=" + (alongX ? "x" : "z") + "]";
        for (int y = 1; y <= 2; y++) {
            set(x, y, z, post ? "fence_post" : "chainlink" + run);
        }
        set(x, 3, z, post ? "fence_post" : "barbed_wire" + run);
    }

    // ------------------------------------------------------------------ the ground

    /** The apron from the airside doors to the taxiway, the truck yard behind, the lead-in lines. */
    private void grounds() {
        for (int x = CX0 - 3; x <= CX1 + 3; x++) {
            for (int z = CZ1 + 1; z <= BZ1; z++) {
                boolean jx = Math.floorMod(x, 5) == 0, jz = Math.floorMod(z, 5) == 0;
                set(x, 0, z, jx && jz ? "af_apron_jj[facing=north]" : jx ? "af_apron_j[facing=north]" : jz ? "af_apron_j[facing=east]" : "af_apron");
            }
            for (int z = CZ0 - 30; z < CZ0; z++) {
                double r = chance();
                set(x, 0, z, r < 0.1 ? "asphalt_crack" : r < 0.15 ? "asphalt_patch" : "asphalt");
            }
        }
        for (int x = CX0; x <= CX1; x++) {
            set(x, 0, CZ1 + 12, "af_paint_y");
        }
    }
}
