package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;

/**
 * The shipyard's waterside ({@link Shipyard}): No. 1 Dry Dock and the tanker in it, the goliath
 * crane over it, No. 2 Berth and hull 1047 on its ways with the tower cranes either side, the
 * fitting-out jetty and the ship alongside it. Drawn in the yard's frame: the quay's face on
 * z = 0, the basin north of it, y 0 the yard's ground.
 */
final class ShipyardDocks {
    /** The dock's top (the altars' outer edge), its floor between the altars, the floor's level. */
    static final int DX0 = -276, DX1 = -217, FX0 = -268, FX1 = -225, DF = -14;
    /** Where the altars start behind the entrance, where the floor ends at the head, the head's edge. */
    static final int FZ0 = 7, FZ1 = 242, HEAD = 251;
    /** The dock's centre line (the ship's keel is on its west half). */
    static final int DCX = -246;
    /** The goliath's rails, its girder's middle along them. */
    static final int GW = -287, GE = -206, GZ = 128;
    /** The berth: the slip's sides (its opening in the quay), its middle, the head, how far the ways run under the water. */
    static final int SLX0 = -176, SLX1 = -124, SCX = -150, SLZ = 224, SLW = 40;
    /** Hull 1047: her length, where her stern is, her keel's height on the blocks, her deck over it, how far she's built. */
    static final int HLEN = 150, HZ = 30, HKEEL = 3, HDECK = 18, HBUILT = 104;
    /** The jetty: its sides, how far out it runs. */
    static final int JX0 = 150, JX1 = 161, JZ = -172;

    private final Shipyard.Yard y;
    private final Placer p;

    ShipyardDocks(Shipyard.Yard y) {
        this.y = y;
        this.p = y.p;
    }

    private void set(int x, int yy, int z, String b) {
        y.set(x, yy, z, b);
    }

    private double chance() {
        return y.chance();
    }

    void build() {
        dock();
        caisson();
        tanker();
        dockFloor();
        dockEdges();
        goliath();
        slip();
        hull();
        towerCrane(SLX0 - 10, 70, 46, 34, 1);
        towerCrane(SLX1 + 8, 150, 42, 30, 3);
        jetty();
        outfitShip();
    }

    // ------------------------------------------------------------------ No. 1 Dry Dock

    /** The altar a column of the dock's side is in from the floor out (0 the floor, 1..4 the steps, 5 the yard). */
    private static int altar(int d) {
        return d <= 0 ? 0 : Math.min(5, (d + 1) / 2);
    }

    /** The top of a dock column: the floor, a step's tread (each three higher), or the yard's ground. */
    private static int top(int x, int z) {
        int dx = x < FX0 ? FX0 - x : x > FX1 ? x - FX1 : 0;
        int dz = z > FZ1 ? z - FZ1 : 0;
        if (z < FZ0) {
            // the entrance: straight walls the caisson seats against
            return dx > 0 ? 0 : DF;
        }
        int k = Math.max(altar(dx), altar(dz));
        return k >= 5 ? 0 : DF + 3 * k;
    }

    /** The dock cut into the yard: the floor, the granite altars stepping up its sides and head, the straight entrance walls. */
    private void dock() {
        for (int x = DX0 - 1; x <= DX1 + 1; x++) {
            for (int z = 0; z <= HEAD; z++) {
                int t = top(x, z);
                for (int yy = DF - 2; yy <= 1; yy++) {
                    String b;
                    if (yy > t) {
                        b = "air";
                    } else if (t == 0 && z >= FZ0 && (x < DX0 || x > DX1 || z >= HEAD)) {
                        continue;  // the yard round it
                    } else if (yy == t && t == DF) {
                        b = "sy_dock";
                    } else if (t == 0 && z < FZ0) {
                        b = yy == 0 ? "concrete_panel" : yy < SEA_MOSS ? "sy_altar_wet" : "sy_altar";
                    } else {
                        b = yy < DF + 4 ? "sy_altar_wet" : "sy_altar";
                    }
                    set(x, yy, z, b);
                }
            }
        }
        // the coping round the top in dressed stone, the drain channels at the floor's sides
        for (int z = FZ0; z <= HEAD; z++) {
            set(DX0 - 1, 0, z, "om_granite");
            set(DX1 + 1, 0, z, "om_granite");
        }
        for (int x = DX0 - 1; x <= DX1 + 1; x++) {
            set(x, 0, HEAD, "om_granite");
        }
        for (int z = FZ0; z <= FZ1; z++) {
            set(FX0, DF, z, "dm_conc_wet");
            set(FX1, DF, z, "dm_conc_wet");
        }
        // the ladders down the entrance walls, steel stairs down the head
        for (int yy = DF + 1; yy <= 0; yy++) {
            set(FX0, yy, 4, "cr_cage_ladder[facing=east]");
            set(FX1, yy, 4, "cr_cage_ladder[facing=west]");
        }
        for (int k = 1; k <= 4; k++) {
            int ty = DF + 3 * k;
            for (int x = DCX - 1; x <= DCX + 1; x++) {
                set(x, ty - 2, FZ1 + 2 * k - 1, "steel_stairs[facing=south]");
                set(x, ty - 1, FZ1 + 2 * k, "steel_stairs[facing=south]");
                set(x, ty, FZ1 + 2 * k, "air");
                set(x, ty - 1, FZ1 + 2 * k - 1, "air");
            }
        }
        y.sign("dock", DX1 + 2, 2, HEAD + 2, "south");
        y.sign("deep", DCX - 8, 1, HEAD + 1, "south");
    }

    /** The water's mark on the entrance walls: wet and weedy under it. */
    private static final int SEA_MOSS = Shipyard.SEA + 1;

    /**
     * The caisson across the mouth: a steel box the width of the entrance seated on its sill, its
     * black face to the sea weeded where the water stands on it, its deck a walkway with rails.
     */
    private void caisson() {
        for (int x = FX0; x <= FX1; x++) {
            set(x, DF - 1, 0, "dm_conc");
            for (int yy = DF; yy <= 0; yy++) {
                set(x, yy, 0, yy <= Shipyard.SEA ? "sy_caisson_weed" : "sy_caisson");
                set(x, yy, 1, "sy_caisson");
                set(x, yy, 2, "sy_caisson");
                set(x, yy, 3, yy == 0 ? "sy_caisson" : "sy_caisson");
            }
            set(x, 0, 1, "deck_steel");
            set(x, 0, 2, "deck_steel");
            set(x, 1, 0, "cr_rail[facing=north]");
            set(x, 1, 3, "cr_rail[facing=south]");
        }
        set(DCX, 2, 3, "sy_sgn_caisson_0[facing=south]");
        set(DCX + 1, 2, 3, "sy_sgn_caisson_1[facing=south]");
        // the fenders on the caisson's sea face, the valve wheels on its deck
        for (int x = FX0 + 3; x < FX1; x += 6) {
            set(x, SEA + 1, -1, "wf_fender[facing=north]");
            set(x + 2, 1, 2, "dm_valve[axis=x]");
        }
    }

    private static final int SEA = Shipyard.SEA;

    /**
     * The tanker in the dock on her keel blocks, her bow to the head, her red bottom out of the
     * water: the bilge blocks under her turn, the timber shores from the altars to her sides, her
     * propeller and rudder bare at the stern.
     */
    private void tanker() {
        // keel on the blocks two over the floor: her frame's keel (BigShip.KEEL) at DF + 3
        int lift = DF + 3 - BigShip.KEEL;
        BigShip ship = new BigShip(p.turned(DCX, lift, FZ0 + 13, 1), y.rng, y.hit, 0);
        Ships.Hull h = ship.bulk(196, 32, true);
        int keel = DF + 3;
        // the blocks: a pair down her keel every other block, the bilge blocks either side under her flat
        for (int z = FZ0 + 14; z < FZ0 + 13 + h.len() - 8; z += 2) {
            for (int x = DCX; x <= DCX + 1; x++) {
                if (!y.empty(x, keel, z)) {
                    set(x, DF + 1, z, "sy_keel[axis=z]");
                    set(x, DF + 2, z, "sy_keel_cap[axis=z]");
                }
            }
            if (Math.floorMod(z, 6) == 0) {
                for (int x : new int[] {DCX - 6, DCX + 7}) {
                    if (!y.empty(x, keel, z)) {
                        set(x, DF + 1, z, "sy_keel[axis=x]");
                        set(x, DF + 2, z, "sy_keel_cap[axis=x]");
                    }
                }
            }
        }
        // the shores off the altars' treads to her sides, every ten blocks, at two heights
        for (int z = FZ0 + 22; z < FZ0 + h.len(); z += 10) {
            for (int k : new int[] {1, 2}) {
                int yy = DF + 3 * k + 1;
                shoreLine(FX0 - 2 * k + 1, yy, z, 1);
                shoreLine(FX1 + 2 * k - 1, yy, z, -1);
            }
        }
        // the propeller and the rudder aft of her stern, on her centre line
        propeller(DCX, keel + 5, FZ0 + 11, 2);
        for (int yy = keel + 1; yy <= keel + 11; yy++) {
            set(DCX, yy, FZ0 + 8, "sy_rudder[axis=z]");
            set(DCX, yy, FZ0 + 7, "sy_rudder[axis=z]");
        }
        set(DCX, keel + 12, FZ0 + 8, "sy_primer");
    }

    /** A shore from x going dx (+1 east, -1 west) at height yy until it meets her, wedged against her plating. */
    private void shoreLine(int x, int yy, int z, int dx) {
        for (int i = 0; i < 20; i++) {
            int cx = x + dx * i;
            if (!y.empty(cx + dx, yy, z)) {
                if (i > 0) {
                    set(cx, yy, z, "sy_shore_end[facing=" + (dx > 0 ? "west" : "east") + "]");
                }
                return;
            }
            set(cx, yy, z, "sy_shore[axis=x]");
        }
        // she's too far off here: take it back out
        for (int i = 0; i < 20; i++) {
            set(x + dx * i, yy, z, "air");
        }
    }

    /** A propeller in the plane across her at (cx, cy, z), r blades' blocks out on the diagonals. */
    void propeller(int cx, int cy, int z, int r) {
        set(cx, cy, z, "sy_prop_hub[facing=south]");
        for (int i = 1; i <= r; i++) {
            set(cx - i, cy + i, z, "sy_prop_a[facing=south]");
            set(cx + i, cy - i, z, "sy_prop_a[facing=south]");
            set(cx + i, cy + i, z, "sy_prop_b[facing=south]");
            set(cx - i, cy - i, z, "sy_prop_b[facing=south]");
        }
        set(cx, cy, z + 1, "dm_penstock[axis=z]");
    }

    /**
     * The floor under her: the silt, the scrap and paint drums left at the blocks, and the dead,
     * everything that came over the edge after the crew on her pulled their ladders up.
     */
    private void dockFloor() {
        for (int z = FZ0; z <= FZ1; z++) {
            for (int x = FX0 + 1; x < FX1; x++) {
                if (!y.empty(x, DF + 1, z)) {
                    continue;
                }
                double r = chance();
                int far = Math.min(Math.abs(x - FX0), Math.abs(x - FX1));
                if (r < 0.03 + (far < 6 ? 0.05 : 0)) {
                    set(x, DF + 1, z, chance() < 0.55 ? "home_corpse_head[facing=" + y.dir() + "]" : "home_blood_pool");
                } else if (r < 0.09) {
                    set(x, DF + 1, z, y.any("blood_splat", "sy_hose", "glass_shards", "trash_bag[facing=north]", "paper"));
                } else if (r < 0.1) {
                    set(x, DF + 1, z, y.any("pw_drum[axis=z]", "fm_drum", "crate"));
                }
            }
        }
        // the ladder that's left hanging off her side, too short now
        for (int yy = DF + 9; yy <= 17; yy++) {
            set(FX1 - 5, yy, 120, "vessel_pilot_ladder[facing=east]");
        }
        // her crew's camp on the tank deck: the tarps, the drums of water, the words on her side
        for (int i = 0; i < 6; i++) {
            int z = FZ0 + 60 + i * 18, x = DCX - 6 + (i % 3) * 5, dy = DF + 3 - BigShip.KEEL + 13;
            set(x, dy, z, y.any("st_tarp_green", "st_tarp_red", "st_tarp_black") + "[facing=" + y.dir() + "]");
            set(x + 1, dy, z, y.any("bunk_drum_water", "crate", "fm_bedroll[facing=north]", "air"));
        }
        y.row("sy_spray_still_", 4, DCX + 17, 9, 140, "east", 0.1);
        y.row("sy_spray_pulled_", 4, DCX - 17, 7, 96, "west", 0.1);
    }

    /** The dock's edge: the chain guard, bollards and capstans, the pump house at the head. */
    private void dockEdges() {
        for (int z = FZ0; z < HEAD; z++) {
            boolean post = z % 3 == 0;
            set(DX0 - 1, 1, z, post ? "sy_chain[facing=east]" : "air");
            set(DX1 + 1, 1, z, post ? "sy_chain[facing=west]" : "air");
            if (z % 24 == 12) {
                set(DX0 - 2, 1, z, z % 48 == 12 ? "vessel_bollard" : "sy_capstan");
                set(DX1 + 2, 1, z, z % 48 == 12 ? "sy_capstan" : "vessel_bollard");
            }
        }
        for (int x = DX0; x <= DX1; x += 3) {
            set(x, 1, HEAD, "sy_chain[facing=north]");
        }
        // the pump house: brick, its roller door, the culvert's grating out to the head
        int x0 = DX1 + 14, z0 = HEAD - 22;
        Bldg ph = new Bldg(p, y.rng);
        ph.damage = 0.4;
        ph.shell(x0, z0, x0 + 14, z0 + 12, 1, 7, "brick_red", "floor_concrete", "floor_concrete");
        ph.windowRow(z0 + 2, z0 + 10, 3, x0, 3, "west", 2, "stone_trim");
        ph.gable(x0, x0 + 14, z0, z0 + 12, 7, "minecraft:andesite", false, "brick_red", 1);
        y.fill(x0 + 5, 1, z0 + 12, x0 + 8, 4, z0 + 12, "pt_shutter_steel");
        for (int x = x0 + 2; x < x0 + 13; x += 5) {
            set(x, 1, z0 + 5, "pw_drum[axis=z]");
            set(x, 1, z0 + 6, "pw_drum_end[facing=south]");
            set(x, 2, z0 + 5, "dm_valve[axis=x]");
        }
        y.sign("pump", x0 + 5, 5, z0 + 13, "south");
    }

    // ------------------------------------------------------------------ the goliath

    /**
     * The goliath crane across the dock, after Belfast's: an A-frame leg either side on its bogies on
     * the rails, the box girder over the dock with the yard's name down both faces, the walkways and
     * rails along its top, the trolley and its machinery house, the hook block hung on its falls,
     * the cab under the girder's east end, the lights on its corners.
     */
    private void goliath() {
        // the rails the length of the dock and past its head, KEEP CLEAR on the ends
        for (int z = 4; z <= HEAD + 40; z++) {
            for (int x : new int[] {GW, GW + 1, GE, GE + 1}) {
                set(x, 0, z, "ah_track[axis=z]");
            }
        }
        y.sign("keep", GW - 1, 1, HEAD + 41, "south");
        int g0 = 54, g1 = 61, gz0 = GZ - 4, gz1 = GZ + 4;
        // the legs: box sections 4 across and 3 deep, splayed in z, each pair on a sill beam on its bogies
        for (int gx : new int[] {GW - 1, GE - 1}) {
            for (int s : new int[] {-1, 1}) {
                double zb = GZ + s * 13, zt = GZ + s * 2.5;
                for (int yy = 3; yy < g0; yy++) {
                    double t = (yy - 3) / (double) (g0 - 3);
                    int zc = (int) Math.round(zb + (zt - zb) * t);
                    for (int dx = 0; dx < 4; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            boolean shell = dx == 0 || dx == 3 || dz != 0;
                            set(gx + dx, yy, zc + dz, shell ? (yy % 6 == 0 ? "cr_plate_yellow_seam" : "cr_plate_yellow") : "cr_cage_ladder[facing=north]");
                        }
                    }
                }
            }
            // the sill along the rail between the leg feet, the bogies under it
            for (int z = GZ - 16; z <= GZ + 16; z++) {
                for (int dx = 0; dx < 4; dx++) {
                    set(gx + dx, 2, z, Math.abs(z - GZ) <= 14 ? "cr_plate_yellow" : "hazard_stripes");
                }
                set(gx + 1, 1, z, "cr_bogie[axis=z]");
                set(gx + 2, 1, z, "cr_bogie[axis=z]");
            }
            // the cross brace between the legs, a third of the way up
            for (int z = GZ - 9; z <= GZ + 9; z++) {
                set(gx + 1, 20, z, "cr_plate_yellow");
                set(gx + 2, 20, z, "cr_plate_yellow");
            }
        }
        // the girder: a box over the span and past the legs, hollow, plated, the name on both faces
        int x0 = GW - 8, x1 = GE + 9;
        for (int x = x0; x <= x1; x++) {
            for (int yy = g0; yy <= g1; yy++) {
                for (int z = gz0; z <= gz1; z++) {
                    boolean shell = yy == g0 || yy == g1 || z == gz0 || z == gz1 || x == x0 || x == x1;
                    set(x, yy, z, shell ? (x % 8 == 0 ? "cr_plate_yellow_seam" : "cr_plate_yellow") : "air");
                }
            }
            set(x, g1 + 1, gz0, "cr_rail[facing=north]");
            set(x, g1 + 1, gz1, "cr_rail[facing=south]");
            set(x, g0 + 1, GZ, "deck_steel");
        }
        int nx = (x0 + x1) / 2 - 10;
        y.picture("sy_gname_", 20, 3, nx, g0 + 2, gz0 - 1, "north");
        y.picture("sy_gname_", 20, 3, nx, g0 + 2, gz1 + 1, "south");
        // the trolley on its rails along the top, its machinery house, the falls down to the hook block
        int tx = DCX - 8 + y.rng.nextInt(16);
        for (int x = tx - 4; x <= tx + 4; x++) {
            for (int z = gz0; z <= gz1; z++) {
                set(x, g1 + 1, z, "cr_plate_grey");
                for (int yy = g1 + 2; yy <= g1 + 5; yy++) {
                    boolean wall = x == tx - 4 || x == tx + 4 || z == gz0 || z == gz1;
                    set(x, yy, z, wall ? (yy == g1 + 3 && !(x == tx - 4 || x == tx + 4) && x % 2 == 0 ? "wf_glass[facing=" + (z == gz0 ? "north" : "south") + "]" : "cr_plate_yellow") : "air");
                }
                set(x, g1 + 6, z, "cr_plate_grey");
            }
        }
        int hookY = 20 + y.rng.nextInt(16);
        for (int yy = hookY + 1; yy < g0; yy++) {
            set(tx - 1, yy, GZ, "vessel_chain_v");
            set(tx + 1, yy, GZ, "vessel_chain_v");
        }
        for (int x = tx - 1; x <= tx + 1; x++) {
            set(x, hookY, GZ, "cr_headblock[axis=x]");
        }
        set(tx, hookY - 1, GZ, "pw_hook");
        set(tx - 2, g1 + 2, gz0 - 1, "sy_sgn_load_0[facing=north]");
        set(tx - 1, g1 + 2, gz0 - 1, "sy_sgn_load_1[facing=north]");
        // the cab under the east end, its windows on three sides, the ladder down a leg
        for (int x = GE - 8; x <= GE - 3; x++) {
            for (int z = GZ - 2; z <= GZ + 2; z++) {
                for (int yy = g0 - 4; yy < g0; yy++) {
                    boolean wall = x == GE - 8 || x == GE - 3 || z == GZ - 2 || z == GZ + 2;
                    String f = x == GE - 8 ? "west" : z == GZ - 2 ? "north" : z == GZ + 2 ? "south" : "east";
                    set(x, yy, z, !wall ? "air" : yy == g0 - 4 ? "cr_plate_grey" : yy >= g0 - 3 && yy <= g0 - 2 && x != GE - 3
                            ? (chance() < 0.2 ? "wf_glass_broken" : "wf_glass") + "[facing=" + f + "]" : "cr_plate_white");
                }
                set(x, g0 - 5, z, "cr_plate_grey");
            }
        }
        set(GE - 6, g0 - 3, GZ, "pw_crane_cab[facing=west]");
        set(GE - 5, g0 - 3, GZ + 1, "fm_bedroll[facing=north]");
        // the aviation lights on the girder's ends
        for (int x : new int[] {x0, x1}) {
            set(x, g1 + 1, gz0, "aviation_light");
            set(x, g1 + 1, gz1, "aviation_light");
        }
    }

    // ------------------------------------------------------------------ No. 2 Berth

    /**
     * The slip: its concrete floor from the head down to the quay and on under the water as a ramp,
     * the two standing ways along it with their ribbands, the winch house at the head.
     */
    private void slip() {
        for (int x = SLX0; x <= SLX1; x++) {
            for (int z = -SLW; z <= SLZ; z++) {
                int t = z >= 0 ? 0 : (int) Math.round(-1 + z * 11.0 / SLW);
                for (int yy = Math.min(t, BED); yy <= Math.max(0, t); yy++) {
                    if (yy < t) {
                        set(x, yy, z, "dm_conc");
                    } else if (yy == t) {
                        set(x, yy, z, z < 0 ? (t < SEA ? "dm_conc_wet" : "dm_conc_moss") : chance() < 0.06 ? "dm_conc_stain" : "dm_conc");
                    } else if (z >= 0) {
                        set(x, yy, z, "air");
                    }
                }
                if (z >= 0) {
                    set(x, 1, z, "air");
                }
            }
        }
        // the slip's sides at the quay: the face down to the bed
        for (int z = -SLW; z < 0; z++) {
            for (int x : new int[] {SLX0 - 1, SLX1 + 1}) {
                for (int yy = BED; yy <= 0; yy++) {
                    set(x, yy, z, yy == 0 ? "concrete_panel" : yy > SEA ? "concrete_panel_stain" : "concrete_dark");
                }
            }
        }
        // the standing ways and their ribbands, down the slip and under the water
        for (int z = -SLW; z <= SLZ - 4; z++) {
            int t = z >= 0 ? 0 : (int) Math.round(-1 + z * 11.0 / SLW);
            for (int wx : new int[] {SCX - 7, SCX + 8}) {
                for (int x = wx - 1; x <= wx + 1; x++) {
                    set(x, t + 1, z, "sy_ways[axis=z]");
                }
                set(wx - 2, t + 1, z, "sy_ribband[axis=z]");
                set(wx + 2, t + 1, z, "sy_ribband[axis=z]");
            }
        }
        // the winch house at the head
        Bldg wh = new Bldg(p, y.rng);
        wh.damage = 0.45;
        wh.shell(SCX - 8, SLZ + 2, SCX + 8, SLZ + 11, 1, 5, "corrugated_rust", "floor_concrete", "floor_concrete");
        wh.flat(SCX - 8, SLZ + 2, SCX + 8, SLZ + 11, 5, "corrugated_rust", 0, null);
        y.fill(SCX - 2, 1, SLZ + 2, SCX + 1, 3, SLZ + 2, "air");
        set(SCX - 3, 1, SLZ + 6, "pw_drum[axis=x]");
        set(SCX + 2, 1, SLZ + 6, "pw_drum[axis=x]");
        y.sign("berth", SCX - 1, 4, SLZ + 1, "north");
    }

    /**
     * Hull 1047 on the ways: on her keel blocks down the middle and the cradle on the ways under her
     * ends, the staging up both sides, her propeller already in; at her bow the launch platform
     * with the bunting along it and the bottle hung on its ribbon, the date on the board.
     */
    private void hull() {
        BigShip ship = new BigShip(p.turned(SCX, HKEEL, HZ, 1), y.rng, y.hit, 0);
        Ships.Hull h = ship.onSlip(HLEN, 28, HDECK, HBUILT);
        int keel = HKEEL;
        for (int z = HZ + 2; z < HZ + HLEN - 6; z += 2) {
            for (int x = SCX; x <= SCX + 1; x++) {
                if (!y.empty(x, keel, z)) {
                    set(x, 1, z, "sy_keel[axis=z]");
                    set(x, 2, z, "sy_keel_cap[axis=z]");
                }
            }
        }
        // the cradle: packing up from the ways to her bottom wherever she's off them
        for (int z = HZ; z < HZ + HLEN; z++) {
            for (int wx : new int[] {SCX - 7, SCX + 8}) {
                for (int x = wx - 1; x <= wx + 1; x++) {
                    int top = -1;
                    for (int yy = 2; yy <= keel + 8; yy++) {
                        if (!y.empty(x, yy, z)) {
                            top = yy;
                            break;
                        }
                    }
                    if (top > 2) {
                        for (int yy = 2; yy < top; yy++) {
                            set(x, yy, z, yy == top - 1 ? "sy_keel_cap[axis=z]" : "sy_poppet[axis=z]");
                        }
                    }
                }
            }
        }
        propeller(SCX, keel + 4, HZ - 2, 2);
        for (int yy = keel + 1; yy <= keel + 9; yy++) {
            set(SCX, yy, HZ - 4, "sy_rudder[axis=z]");
        }
        // staging up both sides of her built length
        int sx0 = SCX - 16, sx1 = SCX + 17;
        for (int z = HZ + 4; z < HZ + HBUILT; z++) {
            boolean pole = (z - HZ) % 3 == 0;
            for (int yy = 1; yy <= HDECK + keel + 1; yy++) {
                boolean lift = yy % 4 == 0;
                String piece = lift ? (pole ? "sf_deck_std" : "sf_deck") : pole ? "sf_std" : (z - HZ) % 6 == 1 && yy % 4 == 2 ? "sf_brace" : "sf_bay";
                if (chance() < 0.02 + y.hit * 0.03) {
                    continue;
                }
                set(sx0, yy, z, piece + "[facing=west]");
                set(sx1, yy, z, piece + "[facing=east]");
            }
        }
        // the launch platform under her bow block's place: the stage, its steps, the bunting, the bottle
        int pz = HZ + HBUILT + 6;
        for (int x = SCX - 6; x <= SCX + 6; x++) {
            for (int z = pz; z <= pz + 4; z++) {
                for (int yy = 1; yy <= 3; yy++) {
                    set(x, yy, z, yy == 3 ? "wf_deck" : (x == SCX - 6 || x == SCX + 6) && (z == pz || z == pz + 4) ? "sy_timber" : "air");
                }
            }
            set(x, 4, pz, "cr_rail[facing=north]");
            set(x, 6, pz + 2, x % 2 == 0 ? "sy_bunting[axis=x]" : "sy_bunting[axis=x]");
        }
        for (int z = pz + 5; z <= pz + 7; z++) {
            set(SCX, z - pz - 4, z, "steel_stairs[facing=north]");
        }
        set(SCX, 5, pz - 1, "sy_bottle");
        y.sign("launch", SCX - 1, 5, pz + 4, "south");
        // the bow block waiting by the head for the crane that never came, on its stools
        BigShip bow = new BigShip(p.turned(SCX + 46, 3, SLZ - 40, 1), y.rng, y.hit, 0);
        bow.block(HLEN, 28, HDECK, HLEN - 30, HLEN - 1);
        for (int z = SLZ - 38; z < SLZ - 12; z += 4) {
            for (int x : new int[] {SCX + 42, SCX + 46, SCX + 50}) {
                if (!y.empty(x, 3, z)) {
                    set(x, 1, z, "sy_keel[axis=z]");
                    set(x, 2, z, "sy_keel_cap[axis=z]");
                }
            }
        }
        y.row("sy_spray_launch_", 4, SCX - 2, 10, HZ + 40, "west", 0.2);
    }

    /**
     * A tower crane on a portal over the slip's side: the four legs on their bogies on the rail,
     * the lattice mast, the slewing deck and cab, the jib out over the berth and the counter-jib
     * with its weights, the ties up to the A-frame, the trolley and the hook. facing: the way the jib
     * points, 1 east or 3 west.
     */
    private void towerCrane(int mx, int mz, int h, int jib, int facing) {
        int sgn = facing == 1 ? 1 : -1;
        for (int z = mz - 30; z <= mz + 30; z++) {
            set(mx - 2, 0, z, "ah_track[axis=z]");
            set(mx + 3, 0, z, "ah_track[axis=z]");
        }
        for (int[] c : new int[][] {{mx - 2, mz - 2}, {mx + 3, mz - 2}, {mx - 2, mz + 3}, {mx + 3, mz + 3}}) {
            for (int yy = 1; yy <= 6; yy++) {
                set(c[0], yy, c[1], yy == 1 ? "cr_bogie[axis=z]" : "steel_yellow");
            }
        }
        y.fill(mx - 2, 7, mz - 2, mx + 3, 7, mz + 3, "steel_yellow");
        for (int yy = 8; yy <= h; yy++) {
            for (int i = 0; i <= 1; i++) {
                for (int j = 0; j <= 1; j++) {
                    set(mx + i, yy, mz + j, "truss_yellow");
                }
            }
        }
        y.fill(mx - 1, h + 1, mz - 1, mx + 2, h + 1, mz + 2, "steel_yellow");
        // the cab on the jib's side
        int cx = facing == 1 ? mx + 2 : mx - 1;
        y.fill(cx, h + 2, mz + 2, cx, h + 3, mz + 2, "steel_yellow");
        set(cx, h + 3, mz + 2, chance() < 0.4 ? "window_broken[axis=x]" : "window[axis=x]");
        int jy = h + 2;
        for (int k = -jib; k <= jib / 3; k++) {
            int x = mx + sgn * -k;
            if (k < -1 || k > 2) {
                set(x, jy, mz, "truss_yellow");
                set(x, jy, mz + 1, "truss_yellow");
            }
        }
        // the counter-jib's weights, the A-frame and its ties
        int back = mx - sgn * (jib / 3);
        y.fill(back - 1, jy - 2, mz, back + 1, jy - 1, mz + 1, "concrete_dark");
        for (int yy = jy + 1; yy <= jy + 6; yy++) {
            set(mx, yy, mz, "truss_yellow");
        }
        set(mx, jy + 7, mz, "aviation_light");
        set(mx + sgn * jib, jy + 1, mz, "aviation_light");
        Harbour ties = new Harbour(p.turned(0, 0, mz, 3), y.rng, y.hit);
        ties.member(0, jy + 6, mx, jy + 1, mx + sgn * jib * 2 / 3, "cr_stay");
        ties.member(0, jy + 6, mx, jy + 1, back, "cr_stay");
        // the trolley out over her, the hook down to the staging with a plate hung on it
        int tx = mx + sgn * (jib / 2 + y.rng.nextInt(Math.max(1, jib / 3)));
        int hookY = HDECK + 6 + y.rng.nextInt(10);
        set(tx, jy - 1, mz, "steel_yellow");
        for (int yy = hookY + 1; yy < jy - 1; yy++) {
            set(tx, yy, mz, "vessel_chain_v");
        }
        set(tx, hookY, mz, "pw_hook");
        set(tx, hookY - 1, mz, "sy_plates_low[axis=x]");
    }

    // ------------------------------------------------------------------ the fitting-out jetty

    /**
     * The jetty out into the basin on its piles: the deck, the rails along it, the bollards and
     * fenders on its west face, the mobile crane on it, the gangway up to the ship.
     */
    private void jetty() {
        for (int z = JZ; z < 0; z++) {
            for (int x = JX0; x <= JX1; x++) {
                set(x, 0, z, (x == JX0 || x == JX1) ? "concrete_dark" : chance() < 0.1 ? "concrete_dark_crack" : "concrete_panel");
                set(x, -1, z, "concrete_dark");
                boolean pile = Math.floorMod(z, 8) == 0 && (x == JX0 || x == JX1 || x == (JX0 + JX1) / 2);
                if (pile) {
                    for (int yy = BED; yy < -1; yy++) {
                        set(x, yy, z, yy >= SEA - 1 && yy <= SEA ? "concrete_panel_moss" : "concrete_dark");
                    }
                }
            }
            if (Math.floorMod(z, 12) == 0) {
                set(JX0, 1, z, "vessel_bollard");
                set(JX1, 1, z, "vessel_bollard");
            }
            if (Math.floorMod(z, 6) == 3) {
                set(JX0 - 1, -1, z, "wf_fender[facing=west]");
            }
            set(JX1, 1, z, Math.floorMod(z, 12) == 0 ? "vessel_bollard" : "cr_rail[facing=east]");
        }
        for (int x = JX0; x <= JX1; x++) {
            set(x, 1, JZ, "cr_rail[facing=north]");
        }
        hb(JX0 + 6, 0, -60).harbourCrane(28, "steel_yellow");
        y.sign("outfit", JX0 + 2, 2, -1, "south");
    }

    private Harbour hb(int x, int yy, int z) {
        return new Harbour(p.turned(x, yy, z, 0), y.rng, y.hit);
    }

    private static final int BED = Shipyard.BED;

    /** The ship alongside the jetty being finished: a container feeder, her bow to the sea, made fast to it. */
    private void outfitShip() {
        // her frame: bow north (-z), her starboard side (+z in her frame) to the jetty on the east
        int beam = 30, ox = JX0 - 2 - beam / 2;
        BigShip ship = new BigShip(p.turned(ox, 0, -8, 3), y.rng, y.hit, JX0 - ox);
        ship.container(176, beam, null);
        // the gangway from the jetty up to her deck
        for (int i = 0; i < 6; i++) {
            set(JX0 - 1 - i / 3, 1 + i, -40, "steel_stairs[facing=east]");
        }
    }
}
