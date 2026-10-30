package com.zomboidcraft.world.county;

/**
 * The rest of Foundry Row's works in {@link Foundry}'s frame, round the furnaces:
 * <ul>
 * <li>the coke ovens along the north fence, a battery of narrow ovens between their buckstays and
 * doors on both faces, the larry car on top, the pusher on the north side, the quench car and the
 * quench tower at the west end, the coal tower at the east, the battery's stack
 * (refs/industry/coke-ovens, railway-coke-quench);</li>
 * <li>the blowing engine house, brick with its tall windows, the engines inside, the boiler house
 * and its four stacks; the gas holder in its frame (blowing-engine-house, gas-holder);</li>
 * <li>the ore yard with its heaps and the ore bridge over them on its rails; the slag dump
 * (ore-yard-bridge, slag-heap);</li>
 * <li>the open hearth shop, its charging floor and furnaces, the pit side with the ladles and the
 * ingot moulds on their cars, the cranes (open-hearth-shop, ingot-moulds-yard);</li>
 * <li>the 28 inch bar mill, the reheat furnace, the roller table down the hall through the stands,
 * the pulpit, and beside it the cooling bed hall, the company's name down its east side for the
 * trains (rolling-mill, mill-hall-crane, mill-pulpit-control); the scrap yard; the machine shop; the
 * water tower; the pipe racks along the spine.</li>
 * </ul>
 */
final class FoundryWorks {
    /** The coke battery. */
    static final int KX0 = 116, KX1 = 286, KZ0 = 14, KZ1 = 28, KH = 10;
    /** The open hearth shop. */
    static final int OX0 = 150, OX1 = 330, OZ0 = 196, OZ1 = 284, OH = 30, CHARGE = 7;
    /** The bar mill and its cooling bed hall beside it. */
    static final int MX0 = 344, MX1 = 384, CX1 = 446, MZ0 = 96, MZ1 = 288, MH = 24;

    private final Foundry c;

    FoundryWorks(Foundry c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String block) {
        c.set(x, y, z, block);
    }

    void build() {
        coke();
        blowing();
        gasHolder();
        oreYard();
        slagDump();
        openHearth();
        mill();
        scrap();
        machineShop();
        waterTower();
        c.hb.pipeRack(150, 440, Foundry.SPINE0 - 4, 7);
    }

    // ------------------------------------------------------------------ the coke works

    private void coke() {
        for (int x = KX0; x <= KX1; x++) {
            for (int z = KZ0; z <= KZ1; z++) {
                boolean face = z == KZ0 || z == KZ1;
                for (int y = 1; y <= KH; y++) {
                    String b;
                    if (face && y >= 2 && y <= 8 && x % 2 == 0 && x > KX0 + 1 && x < KX1 - 1) {
                        b = c.chance() < 0.06 ? "air" : "fr_oven_door[facing=" + (z == KZ0 ? "north" : "south") + "]";
                    } else if (face || x == KX0 || x == KX1 || y == KH) {
                        b = "fr_ovenface";
                    } else {
                        b = "fr_firebrick";
                    }
                    set(x, y, z, b);
                }
                // the top: the charging holes and their lids, the larry rails
                set(x, KH + 1, z, z == KZ0 + 2 || z == KZ1 - 2 ? "tb_rail[axis=x]" : x % 2 == 0 && (z == KZ0 + 5 || z == KZ0 + 7 || z == KZ0 + 9) ? "hull_black" : "air");
            }
            if (x % 2 == 1) {
                for (int y = 1; y <= KH + 1; y++) {
                    set(x, y, KZ0 - 1, "fr_rust");
                    set(x, y, KZ1 + 1, "fr_rust");
                }
            }
        }
        // the larry car up top
        for (int x = 190; x <= 196; x++) {
            for (int z = KZ0 + 2; z <= KZ1 - 2; z++) {
                for (int y = KH + 2; y <= KH + 5; y++) {
                    boolean edge = x == 190 || x == 196 || z == KZ0 + 2 || z == KZ1 - 2;
                    set(x, y, z, !edge && y < KH + 5 ? "fr_coke" : y == KH + 4 && edge && x == 193 ? "window_broken[axis=z]" : "fr_blue");
                }
            }
        }
        // the pusher machine on its rails on the north side, its ram run into an oven
        c.hb.railSpur(KX0, KX1, KZ0 - 6);
        for (int x = 160; x <= 166; x++) {
            for (int z = KZ0 - 8; z <= KZ0 - 4; z++) {
                for (int y = 2; y <= 9; y++) {
                    boolean edge = x == 160 || x == 166 || z == KZ0 - 8 || z == KZ0 - 4;
                    set(x, y, z, edge ? (y == 7 && z == KZ0 - 8 ? "window_broken[axis=x]" : "fr_blue") : y == 2 ? "deck_steel" : "air");
                }
            }
        }
        for (int z = KZ0 - 3; z <= KZ0 - 2; z++) {
            set(162, 5, z, "pw_drum[axis=z]");
        }
        // the coke side: the bench, the quench car's track, the car itself full of coke
        c.hb.railSpur(100, KX1 + 4, KZ1 + 4);
        for (int x = 220; x <= 232; x++) {
            for (int z = KZ1 + 3; z <= KZ1 + 5; z++) {
                set(x, 2, z, "fr_rust");
                set(x, 3, z, z == KZ1 + 4 && x > 220 && x < 232 ? "fr_coke_3" : "fr_rust");
                set(x, 4, z, z == KZ1 + 4 && x > 220 && x < 232 ? "air" : "fr_rust");
            }
        }
        // the quench tower at the west end, its louvres, steam still off the wet coke
        int qx0 = 100, qx1 = 110, qz0 = KZ1 + 1, qz1 = KZ1 + 7;
        for (int x = qx0; x <= qx1; x++) {
            for (int z = qz0; z <= qz1; z++) {
                boolean edge = x == qx0 || x == qx1 || z == qz0 || z == qz1;
                boolean track = z >= KZ1 + 3 && z <= KZ1 + 5 && (x == qx0 || x == qx1);
                for (int y = 1; y <= 24; y++) {
                    if (!edge || track && y <= 6) {
                        continue;
                    }
                    set(x, y, z, y > 14 && (x + y) % 2 == 0 ? "sy_timber" : y > 14 ? "air" : "concrete_panel_stain");
                }
                set(x, 25, z, "concrete_panel");
            }
        }
        set(105, 26, KZ1 + 4, "pw_steam_v");
        set(105, 27, KZ1 + 4, "pw_steam_v");
        // the battery stack and the coal tower at the east end with its heap of coal beside
        c.drum(110, KZ0 - 2, 3, 1, 64, "soot", 0);
        set(110, 65, KZ0 - 2, "aviation_light");
        c.pipe(KX0, 6, KZ0 + 7, 113, 6, KZ0 - 2, 1.0, "fr_rust");
        int tx0 = KX1 + 2, tx1 = KX1 + 12;
        for (int x = tx0; x <= tx1; x++) {
            for (int z = KZ0 - 4; z <= KZ1 + 2; z++) {
                boolean edge = x == tx0 || x == tx1 || z == KZ0 - 4 || z == KZ1 + 2;
                for (int y = 1; y <= 30; y++) {
                    boolean leg = (x == tx0 || x == tx1) && (z == KZ0 - 4 || z == KZ1 + 2 || z == (KZ0 + KZ1) / 2);
                    if (y <= 10 && !leg) {
                        continue;
                    }
                    set(x, y, z, leg ? "concrete_panel" : edge ? (y > 24 ? "corrugated_rust" : c.chance() < 0.12 ? "concrete_panel_stain" : "concrete_panel") : "air");
                }
                set(x, 31, z, "corrugated_grey");
                set(x, 11, z, "concrete_panel");
            }
        }
        c.hb.heap(tx1 + 12, KZ0 + 8, 9, 8, "coal");
        c.sign("coke", KX0 + 6, 4, KZ1 + 2, "south");
    }

    // ------------------------------------------------------------------ the blowing engines, the gas holder

    private void blowing() {
        int x0 = 340, x1 = 392, z0 = 8, z1 = 44;
        c.b.shell(x0, z0, x1, z1, 1, 16, "brick_dark", "pw_tile", "pw_tile");
        for (int x = x0 + 3; x <= x1 - 3; x += 4) {
            c.b.window(x, 4, z1, "south", 9, "stone_trim");
            c.b.window(x, 4, z0, "north", 9, "stone_trim");
        }
        for (int z = z0 + 4; z <= z1 - 4; z += 4) {
            c.b.window(x1, 4, z, "east", 9, "stone_trim");
        }
        c.b.gable(x0, x1, z0, z1, 17, "minecraft:deepslate_tile", false, "brick_dark", 1);
        c.b.doors((x0 + x1) / 2, 1, z1, "fire_door", "south");
        c.b.doors(x0, 1, (z0 + z1) / 2, "fire_door", "west");
        c.sign("blowing", (x0 + x1) / 2, 7, z1 + 1, "south");
        // the three engines: the flywheel pit, the cylinders on their beds, the gauge board, a crane over them
        for (int k = 0; k < 3; k++) {
            int ex = x0 + 8 + k * 15, ez = z0 + 8;
            for (int z = ez; z <= ez + 20; z++) {
                set(ex, 1, z, "concrete_panel");
                set(ex + 4, 1, z, "concrete_panel");
            }
            for (int z = ez + 2; z <= ez + 8; z++) {
                set(ex + 2, 2, z, "pw_tg_round[facing=north]");
                set(ex + 2, 3, z, "pw_tg_round[facing=north]");
            }
            for (int y = 2; y <= 7; y++) {
                set(ex + 2, y, ez + 12, "pw_rotor[axis=x]");
            }
            set(ex + 2, 2, ez + 16, "pw_gen");
            set(ex + 3, 2, ez + 18, "pw_drum[axis=z]");
            set(ex + 1, 2, ez + 18, "pw_drum[axis=z]");
            set(ex + 2, 1, ez + 22, "pw_bench[facing=north]");
        }
        for (int x = x0 + 4; x <= x1 - 4; x += 2) {
            set(x, 4, z1 - 1, "pw_gauge[facing=north]");
        }
        for (int x = x0 + 1; x < x1; x++) {
            set(x, 13, z0 + 1, "pw_crane_rail[facing=south]");
            set(x, 13, z1 - 1, "pw_crane_rail[facing=north]");
        }
        for (int z = z0 + 1; z < z1; z++) {
            set(x0 + 20, 14, z, "pw_crane[axis=z]");
        }
        // the boiler house behind and its four stacks
        int bz0 = z1 + 2, bz1 = z1 + 18;
        c.b.shell(x0, bz0, x1, bz1, 1, 12, "brick_red", "floor_concrete", "floor_concrete");
        c.b.flat(x0, bz0, x1, bz1, 12, "brick_red", 1, "stone_trim");
        for (int k = 0; k < 4; k++) {
            int bx = x0 + 6 + k * 13;
            for (int z = bz0 + 3; z <= bz0 + 10; z++) {
                set(bx, 1, z, "pw_boiler[facing=south]");
                set(bx + 1, 1, z, "pw_boiler[facing=south]");
            }
            c.drum(bx, bz1 + 4, 2.5, 1, 58, "soot", 0);
            c.pipe(bx, 8, bz1 - 1, bx, 8, bz1 + 2, 0.8, "fr_rust");
        }
        c.ladder(x0 + 6 - 3, bz1 + 4, 1, 58, "west");
        // the cold blast main away to the stoves
        c.pipe(x0 - 1, 12, z0 + 30, 310, 12, z0 + 30, 1.3, "fr_rust_seam");
        c.pipe(310, 12, z0 + 30, 310, 12, 66, 1.3, "fr_rust_seam");
        c.pipe(310, 12, 66, 160, 12, 66, 1.0, "fr_rust");
    }

    private void gasHolder() {
        int gx = 420, gz = 40;
        c.drum(gx, gz, 12, 1, 18, "oilrust", 3);
        Round.roof(c.p, gx + 0.5, gz + 0.5, 12, "llll", 19, "oil");
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int x = gx + (int) Math.round(Math.cos(a) * 15), z = gz + (int) Math.round(Math.sin(a) * 15);
            for (int y = 1; y <= 34; y++) {
                set(x, y, z, "steel_girder");
            }
        }
        for (int y : new int[] {18, 34}) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    double r = Math.hypot(dx, dz);
                    if (r > 14.4 && r < 15.6 && c.empty(gx + dx, y, gz + dz)) {
                        set(gx + dx, y, gz + dz, y == 18 ? "deck_steel" : "steel_girder");
                    }
                }
            }
        }
        c.ladder(gx + 16, gz, 1, 34, "east");
        c.sign("gas", gx - 1, 3, gz + 13, "south");
    }

    // ------------------------------------------------------------------ the ore yard, the slag dump

    private void oreYard() {
        int x0 = 8, x1 = 134, z0 = 106, z1 = 194;
        c.hb.railSpur(x0, x1, z0);
        c.hb.railSpur(x0, x1, z1);
        c.heap(30, 128, 16, 14, 9, "ore");
        c.heap(70, 136, 18, 16, 11, "ore");
        c.heap(108, 130, 14, 14, 8, "ore");
        c.heap(40, 172, 16, 12, 7, "coke");
        c.heap(96, 174, 22, 12, 9, "ore");
        // the ore bridge: its pier leg and shear leg on the rails, the girder across, the trolley and grab
        int bx = 60, top = 24;
        for (int z : new int[] {z0, z1}) {
            for (int x : new int[] {bx - 3, bx + 3}) {
                for (int y = 2; y <= top; y++) {
                    set(x, y, z, "steel_girder");
                }
                set(x, 1, z, "pw_drum[axis=x]");
            }
            for (int y = 4; y <= top; y += 6) {
                for (int x = bx - 2; x <= bx + 2; x++) {
                    set(x, y, z, "steel_girder");
                }
            }
        }
        for (int x : new int[] {bx - 2, bx + 2}) {
            for (int z = z0 - 8; z <= z1 + 6; z++) {
                set(x, top, z, "steel_girder");
                set(x, top + 6, z, "steel_girder");
            }
            for (int z = z0 - 8; z + 6 <= z1 + 6; z += 6) {
                c.hb.member(x, top + 1, z, top + 5, z + 6, "cr_strut_red");
            }
        }
        for (int z = z0 - 8; z <= z1 + 6; z++) {
            for (int x = bx - 1; x <= bx + 1; x++) {
                set(x, top, z, "deck_steel");
            }
            set(bx, top + 1, z, "rail[axis=z]");
        }
        // the machinery house on the shear leg, the trolley out over the heaps, the grab let down
        for (int x = bx - 3; x <= bx + 3; x++) {
            for (int z = z1 - 4; z <= z1 + 4; z++) {
                for (int y = top + 7; y <= top + 11; y++) {
                    boolean edge = x == bx - 3 || x == bx + 3 || z == z1 - 4 || z == z1 + 4;
                    set(x, y, z, edge ? (y == top + 9 && z == z1 - 4 ? "window_broken[axis=x]" : "corrugated_rust") : "air");
                }
                set(x, top + 12, z, "corrugated_grey");
            }
        }
        int tz = 150;
        for (int x = bx - 1; x <= bx + 1; x++) {
            for (int z = tz - 1; z <= tz + 1; z++) {
                set(x, top + 1, z, "pw_trolley[facing=north]");
            }
        }
        for (int y = 14; y <= top - 1; y++) {
            set(bx, y, tz, "pw_rope");
        }
        set(bx, 13, tz, "fr_skip[facing=north]");
        set(bx + 1, top - 2, tz + 2, "pw_crane_cab[facing=east]");
        c.sign("ore", x1 - 2, 3, z0 - 2, "north");
    }

    private void slagDump() {
        c.heap(50, 48, 40, 36, 13, "cinder");
        // the crust where the last pots were tipped down its face, still black
        for (int x = 20; x <= 90; x++) {
            for (int z = 80; z <= 92; z++) {
                if (c.chance() < 0.4 && !c.empty(x, 1, z)) {
                    int y = 1;
                    while (!c.empty(x, y + 1, z) && y < 20) {
                        y++;
                    }
                    set(x, y, z, "fr_slag");
                }
            }
        }
        // a pot tipped over at the end of the line
        set(9, 1, Foundry.SLAG, "fw_truck_mid[facing=east]");
        set(8, 2, Foundry.SLAG - 2, "fr_slagpot");
        c.sign("slag", 14, 2, Foundry.SLAG + 3, "south");
    }

    // ------------------------------------------------------------------ the open hearth shop

    private void openHearth() {
        int x0 = OX0, x1 = OX1, z0 = OZ0, z1 = OZ1;
        int split = z0 + 34;
        // the steel frame and its cladding, a brick base, the roof and its monitor
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                set(x, 0, z, z < split ? "floor_concrete" : c.chance() < 0.2 ? "fr_slag" : "fr_castsand");
                if (!edge) {
                    continue;
                }
                boolean col = (x - x0) % 10 == 0 || (z - z0) % 11 == 0 || x == x1 || z == z1;
                for (int y = 1; y <= OH; y++) {
                    String b = col ? "steel_girder" : y <= 4 ? "brick_dark" : y >= 20 && y <= 24 && (x + z) % 3 != 0
                            ? "pw_sash" + (c.chance() < 0.4 ? "_broken" : "") + "[facing=" + (z == z0 ? "north" : z == z1 ? "south" : x == x0 ? "west" : "east") + "]"
                            : c.chance() < 0.04 + c.b.damage * 0.1 ? "air" : c.chance() < 0.6 ? "corrugated_rust" : "corrugated_grey";
                    set(x, y, z, b);
                }
            }
        }
        c.b.gable(x0, x1, z0, z1, OH + 1, "minecraft:polished_tuff", false, "corrugated_rust", 1);
        int rz = (z0 + z1) / 2;
        for (int x = x0 + 4; x <= x1 - 4; x++) {
            for (int dz = -3; dz <= 3; dz++) {
                set(x, OH + 44, rz + dz, Math.abs(dz) == 3 ? "corrugated_rust" : "air");
                set(x, OH + 45, rz + dz, "corrugated_grey");
            }
        }
        // the ends open for the tracks, the doors on the spine
        for (int z = split + 20; z <= split + 26; z++) {
            for (int y = 1; y <= 8; y++) {
                set(x0, y, z, "air");
                set(x1, y, z, "air");
            }
        }
        c.b.doors((x0 + x1) / 2, 0, z1, "garage_door", "south");
        c.hb.railSpur(x0 - 8, MX0 + 10, split + 23);
        // the charging floor along the north half, up on its girders
        for (int x = x0 + 1; x < x1; x++) {
            for (int z = z0 + 1; z < split; z++) {
                set(x, CHARGE, z, "deck_steel");
                if ((x - x0) % 10 == 0 && (z - z0) % 8 == 0) {
                    for (int y = 1; y < CHARGE; y++) {
                        set(x, y, z, "steel_girder");
                    }
                }
            }
            set(x, CHARGE + 1, split, "ext_rail[axis=x]");
        }
        c.steelStair(x0 + 3, split + 7, 0, CHARGE, "north");
        c.steelStair(x1 - 3, split + 7, 0, CHARGE, "north");
        // the five furnaces on the charging floor: firebrick boxes, their doors to the floor, the stacks up through the roof
        for (int k = 0; k < 5; k++) {
            int fx0 = x0 + 8 + k * 34, fx1 = fx0 + 22, fz0 = z0 + 10, fz1 = split - 2;
            for (int x = fx0; x <= fx1; x++) {
                for (int z = fz0; z <= fz1; z++) {
                    for (int y = CHARGE + 1; y <= CHARGE + 8; y++) {
                        boolean edge = x == fx0 || x == fx1 || z == fz0 || z == fz1 || y == CHARGE + 8;
                        if (!edge) {
                            set(x, y, z, "air");
                            continue;
                        }
                        set(x, y, z, z == fz0 && y <= CHARGE + 4 && (x - fx0) % 5 == 2 ? "fr_oven_door[facing=north]"
                                : (x - fx0) % 5 == 0 ? "fr_rust" : "fr_firebrick");
                    }
                }
                for (int z = fz0; z <= fz1; z++) {
                    for (int y = 1; y <= CHARGE; y++) {
                        if (c.empty(x, y, z)) {
                            set(x, y, z, "brick_dark");
                        }
                    }
                }
            }
            // the tap hole out the back over the pit, a ladle under it
            set((fx0 + fx1) / 2, CHARGE + 1, fz1 + 1, "fr_runner[axis=z]");
            set((fx0 + fx1) / 2, CHARGE, fz1 + 1, "deck_steel");
            ladle((fx0 + fx1) / 2, split + 6, k % 2 == 0);
            c.drum(fx0 + 3, fz0 + 3, 1.5, CHARGE + 9, OH + 22, "soot", 0);
            c.drum(fx1 - 3, fz0 + 3, 1.5, CHARGE + 9, OH + 22, "soot", 0);
            c.sign("hearth", fx0 + 9, CHARGE + 6, fz0 - 1, "north");
        }
        // the charging machine on its rails along the floor, a charging box on its peel
        for (int x = x0 + 2; x < x1 - 1; x++) {
            set(x, CHARGE + 1, z0 + 3, "tb_rail[axis=x]");
            set(x, CHARGE + 1, z0 + 7, "tb_rail[axis=x]");
        }
        for (int x = x0 + 60; x <= x0 + 66; x++) {
            for (int z = z0 + 2; z <= z0 + 8; z++) {
                for (int y = CHARGE + 2; y <= CHARGE + 6; y++) {
                    boolean edge = x == x0 + 60 || x == x0 + 66 || z == z0 + 2 || z == z0 + 8;
                    set(x, y, z, edge ? (y == CHARGE + 5 && z == z0 + 8 ? "window_broken[axis=x]" : "fr_blue") : "air");
                }
            }
        }
        for (int z = z0 + 9; z <= z0 + 9; z++) {
            set(x0 + 63, CHARGE + 3, z, "fr_rust");
        }
        // the pit side: the ingot moulds on their cars along the track, more stacked, the cranes over it all
        int tz = split + 23;
        for (int x = x0 + 10; x < x1 - 10; x += 3) {
            if (c.chance() < 0.75) {
                set(x, 2, tz, "fr_mould");
            }
            set(x, 1, tz, "fw_truck_mid[facing=east]");
        }
        for (int x = x0 + 6; x < x1 - 6; x++) {
            for (int z = tz + 6; z <= z1 - 4; z++) {
                if ((x / 4 + z / 4) % 3 == 0 && c.chance() < 0.7) {
                    set(x, 1, z, z % 2 == 0 ? "fr_ingot" : "fr_mould");
                }
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            set(x, OH - 6, z0 + 1, "pw_crane_rail[facing=south]");
            set(x, OH - 6, z1 - 1, "pw_crane_rail[facing=north]");
        }
        for (int kx : new int[] {x0 + 40, x0 + 120}) {
            for (int z = z0 + 1; z < z1; z++) {
                set(kx, OH - 5, z, "pw_crane[axis=z]");
                set(kx + 2, OH - 5, z, "pw_crane[axis=z]");
            }
            set(kx + 2, OH - 6, z1 - 4, "pw_crane_cab[facing=west]");
            for (int y = 6; y < OH - 5; y++) {
                set(kx + 1, y, split + 12, "pw_rope");
            }
            set(kx + 1, 5, split + 12, "pw_hook");
        }
        c.b.lights(x0, z0, x1, z1, OH - 7, 0.02);
        c.sign("crane", x0 + 2, 6, z1 - 1, "north");
        c.row("fr_spray_lastcast_", 4, x0 + 30, 12, z1 + 1, "south", 0.0);
    }

    /** A teeming ladle on the pit floor: a riveted drum, its trunnions, steel gone solid in it. */
    private void ladle(int x, int z, boolean full) {
        c.drum(x, z, 2.5, 1, 5, "oilrust", 2);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(x + dx, full ? 5 : 2, z + dz, "fr_skull");
            }
        }
        set(x - 3, 4, z, "fr_rust");
        set(x + 3, 4, z, "fr_rust");
    }

    // ------------------------------------------------------------------ the bar mill

    private void mill() {
        // the two halls side by side: brick to the sills, sheeting over, the roofs, the shared wall opened
        for (int[] h : new int[][] {{MX0, MX1}, {MX1, CX1}}) {
            int x0 = h[0], x1 = h[1];
            for (int x = x0; x <= x1; x++) {
                for (int z = MZ0; z <= MZ1; z++) {
                    set(x, 0, z, "floor_concrete");
                    boolean edge = x == x0 || x == x1 || z == MZ0 || z == MZ1;
                    if (!edge) {
                        continue;
                    }
                    boolean shared = x == MX1;
                    boolean col = (z - MZ0) % 8 == 0 || z == MZ1 || (x - x0) % 8 == 0 || x == x1;
                    for (int y = 1; y <= MH; y++) {
                        if (shared && y <= 12 && !col) {
                            set(x, y, z, "air");
                            continue;
                        }
                        String out = z == MZ0 ? "north" : z == MZ1 ? "south" : x == x0 ? "west" : "east";
                        set(x, y, z, col ? "steel_girder" : y <= 5 ? "brick_red" : y >= 14 && y <= 18
                                ? "pw_sash" + (c.chance() < 0.35 ? "_broken" : "") + "[facing=" + out + "]"
                                : c.chance() < 0.03 + c.b.damage * 0.08 ? "air" : c.chance() < 0.7 ? "corrugated_grey" : "corrugated_rust");
                    }
                }
            }
            c.b.gable(x0, x1, MZ0, MZ1, MH + 1, "minecraft:polished_andesite", true, "corrugated_grey", 1);
            for (int x = x0 + 1; x < x1; x++) {
                set(x, MH - 5, MZ0 + 1, "air");
            }
            for (int z = MZ0 + 1; z < MZ1; z++) {
                set(x0 + 1, MH - 5, z, "pw_crane_rail[facing=east]");
                set(x1 - 1, MH - 5, z, "pw_crane_rail[facing=west]");
            }
            int kz = MZ0 + 60 + (x0 == MX0 ? 0 : 50);
            for (int x = x0 + 1; x < x1; x++) {
                set(x, MH - 4, kz, "pw_crane[axis=x]");
            }
            set(x0 + 4, MH - 5, kz, "pw_crane_cab[facing=south]");
            c.b.lights(x0, MZ0, x1, MZ1, MH - 6, 0.0);
        }
        // the company's name down the cooling hall's east side, where the trains go by
        c.picture("fr_name_", 12, 2, CX1 + 1, 19, MZ0 + 70, "east");
        // the doors on the spine and the rail in at the north end
        c.b.doors((MX0 + MX1) / 2, 0, MZ1, "garage_door", "south");
        c.b.doors((MX1 + CX1) / 2, 0, MZ1, "garage_door", "south");
        // the reheat furnace at the north end, its stack
        int rx = (MX0 + MX1) / 2;
        for (int x = rx - 8; x <= rx + 8; x++) {
            for (int z = MZ0 + 4; z <= MZ0 + 16; z++) {
                for (int y = 1; y <= 7; y++) {
                    boolean edge = x == rx - 8 || x == rx + 8 || z == MZ0 + 4 || z == MZ0 + 16 || y == 7;
                    set(x, y, z, edge ? (z == MZ0 + 16 && y <= 3 && Math.abs(x - rx) <= 1 ? "air" : (x - rx) % 4 == 0 ? "fr_rust" : "fr_firebrick") : "air");
                }
            }
        }
        for (int x = rx - 1; x <= rx + 1; x++) {
            for (int z = MZ0 + 6; z <= MZ0 + 14; z++) {
                set(x, 1, z, "fr_rollers[axis=z]");
                if (z % 3 == 0) {
                    set(x, 2, z, "fr_billets[axis=z]");
                }
            }
        }
        c.drum(rx - 4, MZ0 + 10, 1.5, 8, MH + 14, "soot", 0);
        // the roller table down the hall through the stands, the shear, the pulpit over it
        for (int z = MZ0 + 17; z <= MZ1 - 6; z++) {
            set(rx, 1, z, "fr_rollers[axis=z]");
            set(rx - 1, 1, z, "fr_rollers[axis=z]");
        }
        for (int k = 0; k < 6; k++) {
            int sz = MZ0 + 40 + k * 14;
            set(rx - 2, 1, sz, "fr_rollstand[facing=east]");
            set(rx + 1, 1, sz, "fr_rollstand[facing=west]");
            set(rx - 2, 2, sz, "fr_blue");
            set(rx + 1, 2, sz, "fr_blue");
            set(rx - 3, 1, sz, "pw_gen");
        }
        set(rx + 2, 1, MZ0 + 30, "fr_billets[axis=z]");
        set(rx + 2, 1, MZ0 + 140, "pw_mill");
        int px0 = rx + 6, px1 = rx + 14, pz0 = MZ0 + 100, pz1 = MZ0 + 110;
        for (int x = px0; x <= px1; x++) {
            for (int z = pz0; z <= pz1; z++) {
                set(x, 6, z, "deck_steel");
                boolean edge = x == px0 || x == px1 || z == pz0 || z == pz1;
                for (int y = 7; y <= 10; y++) {
                    set(x, y, z, !edge ? "air" : y >= 8 && y <= 9 && x == px0 ? "window[axis=z]" : "fr_blue");
                }
                set(x, 11, z, "fr_rust");
                if ((x == px0 || x == px1) && (z == pz0 || z == pz1)) {
                    for (int y = 1; y < 6; y++) {
                        set(x, y, z, "steel_girder");
                    }
                }
            }
        }
        for (int z = pz0 + 2; z <= pz1 - 2; z++) {
            set(px0 + 1, 7, z, "pw_bench[facing=west]");
        }
        set(px1 - 1, 7, pz0 + 2, "pw_annun[facing=west]");
        set(px1 - 2, 7, pz1 - 2, "office_chair_fallen");
        c.b.door(px1, 7, pz0 + 5, "fire_door", "east");
        c.steelStair(px1 + 6, pz0 + 5, 0, 6, "west");
        // the cooling bed hall: the bed's racks, the bundles waiting, coils, a magnet crane's load
        for (int x = MX1 + 6; x <= CX1 - 6; x++) {
            for (int z = MZ0 + 20; z <= MZ0 + 90; z++) {
                set(x, 1, z, (z - MZ0) % 2 == 0 ? "fr_rollers[axis=x]" : "deck_steel");
            }
        }
        for (int x = MX1 + 6; x <= CX1 - 6; x += 3) {
            for (int z = MZ0 + 100; z <= MZ1 - 8; z += 2) {
                if (c.chance() < 0.6) {
                    set(x, 1, z, c.chance() < 0.6 ? "fr_billets[axis=x]" : "fr_slabs[axis=x]");
                    if (c.chance() < 0.4) {
                        set(x, 2, z, "fr_billets[axis=x]");
                    }
                } else if (c.chance() < 0.3) {
                    set(x, 1, z, "fr_coil[axis=x]");
                }
            }
        }
        c.sign("mill", (MX0 + MX1) / 2 - 1, 8, MZ1 + 1, "south");
        c.sign("crane", MX1 + 4, 6, MZ1 - 1, "north");
        c.sign("safety", rx + 4, 3, MZ0 + 17, "south");
    }

    // ------------------------------------------------------------------ the scrap yard, the shop, the water tower

    private void scrap() {
        int x0 = MX0, x1 = CX1, z0 = 70, z1 = 92;
        for (int k = 0; k < 6; k++) {
            int cx = x0 + 10 + k * 17, cz = (z0 + z1) / 2 + (k % 2 == 0 ? -3 : 3);
            for (int dx = -7; dx <= 7; dx++) {
                for (int dz = -6; dz <= 6; dz++) {
                    double e = Math.hypot(dx / 7.0, dz / 6.0);
                    if (e >= 1) {
                        continue;
                    }
                    int h = (int) Math.round((1 - e) * 5 + c.chance());
                    for (int y = 1; y <= h; y++) {
                        set(cx + dx, y, cz + dz, c.any("rust", "fr_rust", "debris_chunk[facing=north]", "pw_parts[facing=" + c.dir() + "]", "rust", "steel_girder"));
                    }
                }
            }
        }
        // the magnet crane on its crawler, the magnet down on a heap
        int mx = x0 + 60, mz = z0 + 2;
        for (int x = mx; x <= mx + 4; x++) {
            for (int z = mz; z <= mz + 5; z++) {
                set(x, 1, z, "pw_dozer_body[facing=east]");
                set(x, 2, z, x > mx + 2 && z < mz + 3 ? "fr_blue" : "air");
            }
        }
        c.hb.member(mx + 2, 3, mz + 2.5, 14, mz - 3.5, "cr_strut_yellow");
        set(mx + 2, 8, mz - 4, "sy_magnet");
        c.sign("crane", x0 + 2, 2, z1 + 2, "south");
    }

    private void machineShop() {
        new Harbour(c.p.shifted(8, 0, 210), c.rng, 0.5).factory(120, 76);
        c.sign("machine", 8 + 58, 5, 209, "north");
    }

    private void waterTower() {
        int x = 133, z = 202;
        for (int[] q : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int y = 1; y <= 26; y++) {
                set(x + q[0], y, z + q[1], "steel_girder");
            }
        }
        c.drum(x, z, 4.5, 27, 34, "wtw", 0);
        Round.roof(c.p, x + 0.5, z + 0.5, 4.5, Round.cone(4.5), 35, "wtw");
        c.pipe(x, 1, z, x, 26, z, 0.6, "fr_rust");
        c.ladder(x + 5, z, 1, 34, "east");
        c.platform(x - 5, z - 5, x + 5, z + 5, 26, false);
    }
}
