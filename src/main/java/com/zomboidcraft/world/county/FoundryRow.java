package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Placer;

/**
 * Foundry Row itself, in {@link Foundry}'s frame: the strip between the works' fence and the street
 * on the lot's south edge, everything facing the street (refs/industry: the Braddock and Homestead
 * mill towns, company rows, union halls, wash houses):
 * <ul>
 * <li>the company's row houses at the west end, the same narrow brick house ten times over, their
 * back yards down to the alley along the fence: the privy, the coal bin, the washing line, the
 * garden gone to seed;</li>
 * <li>the Calder Mercantile, the company store, its loading dock on the alley;</li>
 * <li>the union hall, which fed the Row from the trestle tables behind it until the food ran out,
 * and says so on its walls; the Cast House Tavern next door;</li>
 * <li>over the gate road the time office by the gate, its clocks and card racks behind the
 * turnstiles; the general office, three storeys with the company's name along its parapet; the
 * wash house with the men's clothes still hauled up to the roof on their chains; the employment
 * office; the car park at the east end.</li>
 * </ul>
 */
final class FoundryRow {
    /** The building line on the street, the walk in front of it. */
    static final int FRONT = 366;
    /** The alley behind the houses and the shops, and the back fences on it. */
    static final int ALLEY0 = 319, ALLEY1 = 323, BACK = 325;
    /** The row houses: ten of them from x 4, eight wide on a nine block pitch, 24 deep. */
    static final int HX0 = 4, HOUSES = 10, HW = 8, HPITCH = 9, HD = 24;

    private final Foundry c;
    private final Placer p;

    FoundryRow(Foundry c) {
        this.c = c;
        this.p = c.p;
    }

    private void set(int x, int y, int z, String block) {
        c.set(x, y, z, block);
    }

    void build() {
        alley();
        houses();
        vacant(HX0 + HOUSES * HPITCH, 101);
        mercantile();
        unionHall();
        tavern();
        timeOffice();
        office();
        washHouse();
        employment();
        carPark();
        walk();
        story();
    }

    // ------------------------------------------------------------------ the alley, the walk

    private void alley() {
        for (int x = 3; x <= Foundry.GATE - 6; x++) {
            for (int z = ALLEY0; z <= ALLEY1; z++) {
                double r = c.chance();
                set(x, 0, z, r < 0.55 ? "mu_gravel" : r < 0.75 ? "mu_gravel_b" : r < 0.9 ? "minecraft:coarse_dirt" : "minecraft:dirt_path");
                set(x, 1, z, r > 0.97 ? "gc_grass_" + (1 + c.rng.nextInt(12)) : "air");
            }
        }
        // the poles carrying the line along the alley
        for (int x = 8; x < Foundry.GATE - 8; x += 22) {
            for (int y = 1; y <= 7; y++) {
                set(x, y, ALLEY0 - 1, "sy_timber");
            }
            set(x, 8, ALLEY0 - 1, "power_line[axis=x]");
        }
    }

    /** The walk along the street in front of everything, its lamps, hydrants, the box, the cans. */
    private void walk() {
        for (int x = 0; x < c.w; x++) {
            for (int z = FRONT + 1; z <= FRONT + 2; z++) {
                if (c.get(x, 0, z) == null || !c.get(x, 0, z).startsWith("asphalt")) {
                    set(x, 0, z, c.chance() < 0.15 ? "sidewalk_cracked" : c.chance() < 0.1 ? "sidewalk_stain" : "sidewalk");
                }
            }
        }
        for (int x = 12; x < c.w - 4; x += 26) {
            if (Math.abs(x - Foundry.GATE) > 7) {
                c.lampPost(x, c.d - 2);
            }
        }
        int[][] stuff = {{40, 0}, {118, 1}, {150, 2}, {176, 3}, {240, 1}, {300, 0}, {358, 2}, {420, 3}};
        for (int[] s : stuff) {
            int x = s[0], z = c.d - 2;
            set(x + 2, 1, z, switch (s[1]) {
                case 0 -> "fs_hydrant[facing=north]";
                case 1 -> "mailbox[facing=north]";
                case 2 -> c.chance() < 0.4 ? "ext_trash_can_tipped" : "ext_trash_can";
                default -> "th_bench[facing=south]";
            });
        }
        // the Row's own street sign on its two posts at the corner by the vacant lot
        int sx = HX0 + HOUSES * HPITCH + 2;
        for (int y = 1; y <= 3; y++) {
            set(sx, y, FRONT + 1, "fence_post");
            set(sx + 1, y, FRONT + 1, "fence_post");
        }
        c.sign("row", sx, 3, FRONT + 2, "south");
    }

    // ------------------------------------------------------------------ the row houses

    private void houses() {
        String[] doors = {"red", "green", "navy", "black", "oak", "white"};
        for (int i = 0; i < HOUSES; i++) {
            int x0 = HX0 + i * HPITCH, x1 = x0 + HW - 1;
            double r = c.chance();
            House.Condition cond = r < 0.38 ? House.Condition.LOOTED : r < 0.66 ? House.Condition.BOARDED
                    : r < 0.78 ? House.Condition.BURNT : House.Condition.SHUT;
            // the company built them all alike; a few have been painted or roofed since
            String wall = c.chance() < 0.8 ? "brick_red" : c.chance() < 0.5 ? "brick_dark" : "siding_white";
            House.Palette pal = new House.Palette(wall, "front_trim_white", c.chance() < 0.75 ? "charcoal" : "tin_red",
                    c.chance() < 0.5 ? "floor_boards" : "home_floor_oak", "stone_trim", c.chance() < 0.7 ? "white" : "brown",
                    null, doors[c.rng.nextInt(doors.length)]);
            House h = new House(p.turned(x1, 0, FRONT, 2), c.rng, House.Style.SHOTGUN, cond, pal, HW, HD, 1);
            if (c.power && c.chance() < 0.3) {
                h.lit(0.4);
            }
            h.build();
            // the walk up to the porch steps
            int door = x1 - h.doorX();
            set(door, 0, FRONT + 1, "sidewalk");
            backYard(x0, x1, i);
        }
    }

    /** One house's yard from its back wall to the alley: picket sides, the privy, the coal, the line, the patch. */
    private void backYard(int x0, int x1, int i) {
        int zb = FRONT - HD;   // the back wall's z
        for (int x = x0; x <= x1; x++) {
            for (int z = BACK + 1; z <= zb; z++) {
                double r = c.chance();
                set(x, 0, z, r < 0.6 ? "minecraft:grass_block" : r < 0.85 ? "minecraft:coarse_dirt" : "minecraft:dirt");
                set(x, 1, z, r < 0.08 ? "gc_grass_" + (1 + c.rng.nextInt(12)) : "air");
            }
        }
        // the pickets down the side between this yard and the next, and along the alley with the gate in them
        for (int z = BACK + 1; z <= zb; z++) {
            set(x1 + 1, 1, z, c.chance() < 0.15 ? "yd_picket_bare_broken[axis=z]" : c.chance() < 0.05 ? "air" : "yd_picket_bare[axis=z]");
        }
        for (int x = x0; x <= x1 + 1; x++) {
            String pk = x == x0 + 3 ? (c.chance() < 0.6 ? "yd_picket_gate_bare_open" : "yd_picket_gate_bare")
                    : c.chance() < 0.12 ? "yd_picket_bare_broken" : "yd_picket_bare";
            set(x, 1, BACK, pk + "[axis=x]");
        }
        // the privy in the back corner, its door to the house; the coal bin of old boards beside the gate
        set(x1 - 1, 1, BACK + 2, "fm_outhouse_lo[facing=south]");
        set(x1 - 1, 2, BACK + 2, "fm_outhouse_hi[facing=south]");
        for (int x = x0; x <= x0 + 2; x++) {
            set(x, 1, BACK + 1, "fence_boards[axis=x]");
        }
        set(x0, 1, BACK + 2, "fence_boards[axis=z]");
        set(x0 + 1, 1, BACK + 2, "fr_coke_" + (1 + c.rng.nextInt(3)));
        set(x0 + 2, 1, BACK + 2, c.chance() < 0.5 ? "fr_coke_1" : "sc_bucket[facing=" + c.dir() + "]");
        // the washing line across the yard, still pegged out on some
        int lz = BACK + 6 + c.rng.nextInt(3);
        set(x0, 1, lz, "yd_line_post[axis=x]");
        set(x1, 1, lz, "yd_line_post[axis=x]");
        for (int x = x0 + 1; x < x1; x++) {
            double r = c.chance();
            set(x, 1, lz, (r < 0.2 ? "yd_line_sheet" : r < 0.4 ? "yd_line_shirts" : "yd_line") + "[axis=x]");
        }
        // a garden by the house, gone to seed, or a dog's corner, or the rubbish
        int gz = lz + 3;
        switch (i % 3) {
            case 0 -> {
                String[] crops = {"minecraft:potatoes[age=7]", "minecraft:carrots[age=7]", "minecraft:beetroots[age=3]"};
                for (int x = x0 + 1; x <= x1 - 1; x++) {
                    for (int z = gz; z <= Math.min(zb - 2, gz + 4); z++) {
                        set(x, 0, z, "minecraft:farmland[moisture=0]");
                        double r = c.chance();
                        set(x, 1, z, r < 0.45 ? crops[c.rng.nextInt(crops.length)] : r < 0.75 ? "minecraft:dead_bush" : "minecraft:short_grass");
                    }
                }
            }
            case 1 -> {
                set(x0 + 1, 1, gz + 1, "yd_doghouse_" + (c.chance() < 0.5 ? "red" : "white") + "[facing=south]");
                set(x0 + 1, 1, gz + 2, "yd_dog_stake[facing=south]");
                set(x0 + 3, 1, gz + 2, "yd_dog_bowl");
                set(x1 - 1, 1, gz, "barrel");
            }
            default -> {
                set(x0 + 1, 1, gz, "ext_trash_can");
                set(x0 + 2, 1, gz, c.chance() < 0.5 ? "ext_trash_can_tipped" : "trash_bag[facing=" + c.dir() + "]");
                set(x1 - 2, 1, gz + 2, "yd_firering");
                set(x1 - 1, 1, gz + 1, "trade_folding_chair[facing=west]");
            }
        }
        set(x0 + 4, 1, zb - 1, c.chance() < 0.5 ? "home_trash_can" : "planter");
    }

    /** Where the eleventh house was: its footings, the rubble, the weeds through it. */
    private void vacant(int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = BACK; z <= FRONT; z++) {
                double r = c.chance();
                set(x, 0, z, r < 0.5 ? "minecraft:coarse_dirt" : r < 0.8 ? "minecraft:grass_block" : "brick_red_crack");
                boolean footing = (x == x0 + 1 || x == x1 - 1) && z > FRONT - HD + 2 || z == FRONT - HD + 3 && x > x0 && x < x1;
                set(x, 1, z, footing && c.chance() < 0.7 ? "brick_red_crack"
                        : r < 0.1 ? "rubble_pile" : r < 0.16 ? "debris_chunk[facing=" + c.dir() + "]"
                        : r < 0.3 ? "gc_grass_" + (1 + c.rng.nextInt(12)) : r < 0.34 ? "minecraft:dead_bush" : "air");
            }
        }
    }

    // ------------------------------------------------------------------ the shops and the hall

    /** A storefront facing the street from x0 to x1, d deep (turned: its local x runs west from x1). */
    private Storefront front(int x0, int x1, int d, int floors, Storefront.Style style, Storefront.Trade trade) {
        Storefront s = new Storefront(p.turned(x1, 0, FRONT, 2), c.rng, x1 - x0 + 1, d, floors, style, trade);
        s.openWest = true;
        s.openEast = true;
        s.build();
        return s;
    }

    private void mercantile() {
        int x0 = 104, x1 = 131, d = 24;
        front(x0, x1, d, 2, Storefront.Style.VICTORIAN, Storefront.Trade.COMPANY);
        c.sign("store", x1 + 1, 6, FRONT - 6, "east");
        // the loading dock on the alley: the platform, the crates and pallets left on it, the truck backed up
        int zb = FRONT - d - 4;
        for (int x = x0 + 4; x <= x1 - 4; x++) {
            for (int z = zb - 5; z <= zb; z++) {
                set(x, 0, z, "concrete_dark");
                set(x, 1, z, z == zb - 5 ? "concrete_dark_crack" : "concrete_dark");
                double r = c.chance();
                set(x, 2, z, z == zb - 5 ? "air" : r < 0.15 ? "crate" : r < 0.25 ? "st_pallet" : r < 0.3 ? "apple_crate"
                        : r < 0.35 ? "cardboard_box[facing=" + c.dir() + "]" : "air");
            }
        }
        for (int x = x0 + 4; x <= x1 - 4; x++) {
            set(x, 1, zb - 6, "steel_stairs[facing=south]");
        }
        p.vehicle(x0 + 14.5, 1, ALLEY0 + 2.5, 270, "box_truck", c.rng.nextLong());
    }

    /**
     * The union hall, Local 1219: the hall in front, and behind it in the yard the trestle tables
     * the Row ate at, the pots, the pallets with nothing left on them, the barricade they held.
     */
    private void unionHall() {
        int x0 = 136, x1 = 165, d = 26;
        front(x0, x1, d, 2, Storefront.Style.VICTORIAN, Storefront.Trade.VFW);
        c.sign("union", x0 - 1, 7, FRONT - 6, "west");
        c.row("fr_spray_food_", 5, x0 - 1, 3, FRONT - 20, "west", 0.0);
        c.row("fr_spray_local_", 4, x1 + 1, 3, FRONT - 18, "east", 0.0);
        int z1 = FRONT - d - 4, z0 = BACK + 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, c.chance() < 0.7 ? "concrete_dark" : "concrete_dark_crack");
            }
        }
        // the tarp over the serving line on its timbers
        for (int x = x0 + 2; x <= x1 - 2; x++) {
            for (int z = z0 + 2; z <= z0 + 6; z++) {
                boolean post = (x - x0) % 6 == 2 && (z == z0 + 2 || z == z0 + 6);
                if (post) {
                    for (int y = 1; y <= 3; y++) {
                        set(x, y, z, "sy_timber");
                    }
                }
                if (c.chance() > 0.12) {
                    set(x, 4, z, (x + z) % 7 == 0 ? "st_tarp_black" : "st_tarp_green");
                }
            }
        }
        // the serving line: tables end to end with the pots on them, the pallets behind, all empty
        for (int x = x0 + 3; x <= x1 - 3; x++) {
            set(x, 1, z0 + 4, "trade_banquet_table");
            if ((x - x0) % 3 == 0) {
                set(x, 2, z0 + 4, c.chance() < 0.8 ? "hk_pot" : "air");
            }
            if ((x - x0) % 4 == 1) {
                set(x, 1, z0 + 2, c.chance() < 0.3 ? "st_pallet_water_half" : "st_pallet");
            }
        }
        set(x1 - 3, 1, z0 + 2, "st_pallet_meals[facing=south]");
        // the tables they ate at, the chairs pushed back or over
        for (int row = 0; row < 3; row++) {
            int z = z0 + 9 + row * 3;
            if (z > z1 - 1) {
                break;
            }
            for (int x = x0 + 3; x <= x1 - 3; x++) {
                set(x, 1, z, "trade_banquet_table");
                if (c.chance() < 0.5) {
                    set(x, 1, z - 1, c.chance() < 0.75 ? "trade_folding_chair[facing=south]" : "trade_folding_chair_tipped[facing=" + c.dir() + "]");
                }
                if (c.chance() < 0.5) {
                    set(x, 1, z + 1, c.chance() < 0.75 ? "trade_folding_chair[facing=north]" : "trade_folding_chair_tipped[facing=" + c.dir() + "]");
                }
                if (c.chance() < 0.08) {
                    set(x, 2, z, c.any("paper", "fm_lantern", "trash_bag[facing=north]"));
                }
            }
        }
        // the barrel fires, the sandbags across the yard's open side, the last of them
        set(x0 + 1, 1, z0 + 8, "rg_barrel_burnt[facing=north]");
        set(x1 - 1, 1, z0 + 12, "rg_barrel_burnt[facing=south]");
        for (int z = z0; z <= z1; z++) {
            if (c.chance() < 0.8) {
                set(x0 - 1, 1, z, "sandbags");
                if (c.chance() < 0.5) {
                    set(x0 - 1, 2, z, "sandbags");
                }
            }
        }
        for (int k = 0; k < 4; k++) {
            set(x0 + 4 + c.rng.nextInt(x1 - x0 - 8), 1, z0 + 7, "home_corpse_head[facing=" + c.dir() + "]");
        }
        set(x0 + 8, 1, z0 + 1, "cv_flag_us[facing=south]");
    }

    private void tavern() {
        int x0 = 170, x1 = 190;
        front(x0, x1, 22, 2, Storefront.Style.ITALIANATE, Storefront.Trade.TAVERN);
        c.sign("tavern", x1 + 1, 6, FRONT - 4, "east");
        // the yard behind: the empties, the kegs, the night turn's last cars
        int z1 = FRONT - 26, z0 = BACK + 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, c.chance() < 0.6 ? "mu_gravel" : "minecraft:coarse_dirt");
                if (z <= z0 + 2 && x <= x0 + 6 && c.chance() < 0.6) {
                    set(x, 1, z, c.any("pk_barrel_1[facing=north]", "cellar_barrel[facing=east]", "crate", "trash_bag[facing=north]"));
                }
            }
        }
        p.vehicle(x0 + 13.5, 1, z0 + 5.5, 4, "pickup,sedan", c.rng.nextLong());
    }

    // ------------------------------------------------------------------ by the gate

    /**
     * The time office inside the gate: a brick hut the men filed through, the turnstiles across it,
     * the clocks on the back wall between the card racks, in and out.
     */
    private void timeOffice() {
        int x0 = 214, x1 = 230, z0 = Foundry.FENCE + 4, z1 = z0 + 13;
        Bldg b = c.b;
        b.shell(x0, z0, x1, z1, 1, 5, "brick_dark", "floor_concrete", "floor_concrete");
        b.flat(x0, z0, x1, z1, 5, "brick_dark", 1, "stone_trim");
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            b.window(x, 2, z1, "south", 2, "stone_trim");
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 4) {
            b.window(x1, 2, z, "east", 2, "stone_trim");
        }
        // in from the gate road at the west end, out the north end into the works
        int dz = (z0 + z1) / 2;
        c.doors(x0, 0, dz, "fire_door", "west");
        c.sign("time", x0 - 1, 4, dz, "west");
        c.door(x0 + 3, 0, z0, "fire_door", "north");
        c.door(x1 - 3, 0, z0, "fire_door", "north");
        // the turnstiles in a row across the room
        for (int z = z0 + 1; z < z1; z++) {
            set(x0 + 5, 1, z, z % 2 == 0 ? "st_turnstile[facing=west]" : "ext_rail[axis=z]");
        }
        // the clocks on the north and south walls, a rack of cards either side of each
        for (int x = x0 + 7; x <= x1 - 2; x += 3) {
            set(x, 2, z0 + 1, "pw_timeclock[facing=south]");
            set(x - 1, 2, z0 + 1, "fr_cardrack[facing=south]");
            set(x + 1, 2, z0 + 1, "fr_cardrack[facing=south]");
            set(x - 1, 3, z0 + 1, "fr_cardrack[facing=south]");
            set(x + 1, 3, z0 + 1, "fr_cardrack[facing=south]");
            set(x, 2, z1 - 1, "pw_timeclock[facing=north]");
            set(x - 1, 2, z1 - 1, "fr_cardrack[facing=north]");
            set(x + 1, 2, z1 - 1, "fr_cardrack[facing=north]");
        }
        // the timekeeper's window, his desk, the board
        set(x0 + 2, 1, z0 + 2, "store_counter[facing=east]");
        set(x0 + 2, 1, z0 + 3, "store_counter[facing=east]");
        set(x0 + 1, 1, z0 + 2, "office_chair[facing=east]");
        set(x0 + 1, 2, z1 - 2, "bk_notice[facing=east]");
        set(x0 + 1, 1, z1 - 1, "filing_cabinet[facing=east]");
        c.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 0, 0.18);
        set(x0 + 8, 1, dz, "home_corpse_head[facing=east]");
        c.b.lights(x0, z0, x1, z1, 4, 0.1);
    }

    /**
     * The general office, three storeys of brick facing the street with the company's name along the
     * parapet: the counter downstairs, the clerks' floors over it, the stair at the back.
     */
    private void office() {
        int x0 = 234, x1 = 290, z0 = FRONT - 32, z1 = FRONT;
        Bldg b = c.b;
        b.shell(x0, z0, x1, z1, 3, 4, "brick_red", "floor_lino", "floor_boards");
        b.flat(x0, z0, x1, z1, 12, "brick_red", 2, "stone_trim");
        for (int k = 0; k < 3; k++) {
            int y = k * 4 + 2;
            for (int x = x0 + 2; x <= x1 - 2; x += 3) {
                if (k == 0 && Math.abs(x - (x0 + x1) / 2) < 3) {
                    continue;
                }
                b.window(x, y, z1, "south", 2, "stone_trim");
                b.window(x, y, z0, "north", 2, "stone_trim");
            }
            for (int z = z0 + 3; z <= z1 - 3; z += 4) {
                b.window(x0, y, z, "west", 2, "stone_trim");
                b.window(x1, y, z, "east", 2, "stone_trim");
            }
            // the stone band at each floor
            for (int x = x0; x <= x1; x++) {
                set(x, k * 4 + 4, z1 + 1, "bd_lintel[facing=south]");
            }
        }
        int mx = (x0 + x1) / 2;
        c.doors(mx, 0, z1, "shop_door", "south");
        for (int x = mx - 2; x <= mx + 3; x++) {
            set(x, 0, z1 + 1, "concrete_dark");
        }
        c.sign("office", mx, 4, z1 + 1, "south");
        c.picture("fr_name_", 12, 2, mx - 5, 13, z1 + 1, "south");
        // the stair up the back corner, into a hut on the roof
        c.stair(x1 - 6, z0 + 1, true, new int[] {0, 4, 8, 12}, "brick_red", "roof");
        // downstairs: the counter across the room, the bench for the men waiting on their pay
        for (int x = x0 + 4; x <= x1 - 10; x++) {
            set(x, 1, z1 - 8, x == mx ? "air" : "store_counter[facing=south]");
            if (x % 6 == 0) {
                set(x, 2, z1 - 8, "store_register[facing=south]");
            }
        }
        for (int x = x0 + 4; x <= x1 - 10; x += 2) {
            set(x, 1, z1 - 2, "th_bench[facing=north]");
        }
        clerks(x0, z0, x1, z1 - 10, 0);
        clerks(x0, z0, x1, z1, 4);
        clerks(x0, z0, x1, z1, 8);
        for (int k = 0; k < 3; k++) {
            b.lights(x0, z0, x1, z1, k * 4 + 3, 0.08);
            c.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, k * 4, 0.05);
        }
    }

    /** A clerks' floor at y: rows of desks facing the windows, files down the walls. */
    private void clerks(int x0, int z0, int x1, int z1, int y) {
        for (int z = z0 + 3; z <= z1 - 3; z += 4) {
            for (int x = x0 + 3; x <= x1 - 10; x += 3) {
                if (!c.empty(x, y + 1, z) || !c.empty(x, y + 1, z + 1)) {
                    continue;
                }
                set(x, y + 1, z, "desk[facing=north]");
                set(x, y + 1, z + 1, c.chance() < 0.8 ? "office_chair[facing=north]" : "office_chair_fallen[facing=" + c.dir() + "]");
                if (c.chance() < 0.25) {
                    set(x, y + 2, z, c.any("paper", "sh_typewriter[facing=north]", "home_books_stack_low"));
                }
            }
        }
        for (int x = x0 + 2; x <= x1 - 10; x += 2) {
            if (c.empty(x, y + 1, z0 + 1)) {
                set(x, y + 1, z0 + 1, "filing_cabinet[facing=south]");
            }
        }
    }

    // ------------------------------------------------------------------ the wash house

    /**
     * The wash house: one tall brick hall, the men's street clothes hauled up to the roof in their
     * baskets on chains, the lockers, the showers along the south wall, the sinks, the benches.
     */
    private void washHouse() {
        int x0 = 296, x1 = 346, z0 = FRONT - 32, z1 = FRONT, top = 9;
        Bldg b = c.b;
        b.shell(x0, z0, x1, z1, 1, top, "brick_red", "floor_concrete", "floor_concrete");
        b.gable(x0 - 1, x1 + 1, z0 - 1, z1 + 1, top + 1, "minecraft:polished_andesite", false, "brick_red", 1);
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            set(x, 6, z0, "pw_sash" + (c.chance() < 0.4 ? "_broken" : "") + "[facing=north]");
            set(x, 7, z0, "pw_sash" + (c.chance() < 0.4 ? "_broken" : "") + "[facing=north]");
            set(x, 6, z1, "pw_sash" + (c.chance() < 0.4 ? "_broken" : "") + "[facing=south]");
            set(x, 7, z1, "pw_sash" + (c.chance() < 0.4 ? "_broken" : "") + "[facing=south]");
        }
        int mx = (x0 + x1) / 2;
        c.doors(mx, 0, z1, "fire_door", "south");
        c.doors(mx, 0, z0, "fire_door", "north");
        c.sign("wash", mx, 4, z1 + 1, "south");
        // the shower stalls along the south wall: tiled, a stall every three
        for (int x = x0 + 1; x <= x1 - 1; x++) {
            if (Math.abs(x - mx) <= 2) {
                continue;
            }
            for (int z = z1 - 4; z <= z1 - 1; z++) {
                set(x, 0, z, "store_tile");
            }
            for (int y = 1; y <= 3; y++) {
                set(x, y, z1 - 1, c.chance() < 0.25 ? "home_wall_tile_white_worn" : "home_wall_tile_white");
            }
            if ((x - x0) % 3 == 0) {
                for (int y = 1; y <= 3; y++) {
                    for (int z = z1 - 3; z <= z1 - 2; z++) {
                        set(x, y, z, "home_wall_tile_white");
                    }
                }
            } else if ((x - x0) % 3 == 1) {
                set(x, 3, z1 - 2, "bunk_shower[facing=north]");
            }
        }
        // the sinks down the east wall, the lockers down the west, benches between
        for (int z = z0 + 3; z <= z1 - 7; z++) {
            set(x1 - 1, 1, z, z % 2 == 0 ? "home_sink_white[facing=west]" : "fit_sink_cabin[facing=west]");
            set(x0 + 1, 1, z, c.chance() < 0.15 ? "air" : "fit_locker_lo[facing=east]");
            set(x0 + 1, 2, z, c.chance() < 0.15 ? "air" : "fit_locker_hi[facing=east]");
        }
        // the baskets: a grid of them up under the roof on their chains, the chains run down to the cleats
        for (int x = x0 + 5; x <= x1 - 5; x += 2) {
            for (int z = z0 + 3; z <= z1 - 8; z += 2) {
                boolean down = c.chance() < 0.07;
                if (down) {
                    set(x, 1, z, "fr_basket");
                    continue;
                }
                set(x, 6, z, "fr_basket");
                set(x, 7, z, "fm_chain");
                set(x, 8, z, "fm_chain");
            }
            if (c.empty(x, 1, z0 + 1)) {
                set(x, 1, z0 + 1, "sy_chain[facing=south]");
            }
        }
        for (int z = z0 + 4; z <= z1 - 8; z += 3) {
            for (int x = x0 + 4; x <= x1 - 4; x++) {
                if (c.empty(x, 1, z) && c.chance() < 0.85) {
                    set(x, 1, z, "th_bench[facing=" + (z % 2 == 0 ? "north" : "south") + "]");
                }
            }
        }
        for (int z = z0 + 2; z <= z1 - 2; z += 3) {
            set(x0 + 1, top - 1, z, "bunk_lamp_hang" + (c.power && c.chance() < 0.3 ? "_lit" : ""));
        }
        c.mess(x0 + 2, z0 + 1, x1 - 2, z1 - 5, 0, 0.06);
        for (int k = 0; k < 6; k++) {
            int x = x0 + 4 + c.rng.nextInt(x1 - x0 - 8), z = z0 + 3 + c.rng.nextInt(z1 - z0 - 12);
            if (c.empty(x, 1, z)) {
                set(x, 1, z, c.any("home_clothes_pile", "pw_hardhats[facing=north]", "home_corpse_head[facing=" + c.dir() + "]"));
            }
        }
    }

    // ------------------------------------------------------------------ the east end

    private void employment() {
        int x0 = 350, x1 = 366;
        front(x0, x1, 17, 1, Storefront.Style.VICTORIAN, Storefront.Trade.REALTY);
        c.sign("employ", x1 + 1, 3, FRONT - 3, "east");
        // the line that formed when the works shut: chairs along the wall, what they left
        for (int z = FRONT - 14; z <= FRONT - 2; z += 2) {
            set(x1 + 1, 1, z, c.chance() < 0.7 ? "trade_folding_chair[facing=east]" : "trade_folding_chair_tipped[facing=east]");
        }
        set(x1 + 2, 1, FRONT - 6, "home_corpse_head[facing=south]");
    }

    /** The car park: asphalt to the street, two rows of stalls, the cars left in them, its lamps. */
    private void carPark() {
        int x0 = 372, x1 = c.w - 4, z0 = BACK + 1, z1 = FRONT;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean stall = ((z >= z0 + 4 && z <= z0 + 9) || (z >= z1 - 9 && z <= z1 - 4)) && (x - x0) % 3 == 0;
                set(x, 0, z, stall ? "park_line[axis=z]" : c.chance() < 0.25 ? "asphalt_crack" : "asphalt");
                set(x, 1, z, c.chance() < 0.01 ? "debris_chunk[facing=" + c.dir() + "]" : c.chance() < 0.01 ? "paper" : "air");
            }
        }
        for (int x = x0 + 6; x <= x1 - 4; x += 18) {
            c.lampPost(x, (z0 + z1) / 2);
        }
        for (int x = x0 + 1; x + 2 <= x1; x += 3) {
            if (c.chance() < 0.4) {
                p.vehicle(x + 1, 1, z0 + 7.0, 0, "sedan,pickup,hatchback,suv", c.rng.nextLong());
            }
            if (c.chance() < 0.35) {
                p.vehicle(x + 1, 1, z1 - 6.0, 180, "sedan,pickup,hatchback,suv", c.rng.nextLong());
            }
        }
    }

    // ------------------------------------------------------------------ what happened here

    private void story() {
        // the ones in the street and the yards
        for (int i = 0; i < 46; i++) {
            int x = 4 + c.rng.nextInt(c.w - 8), z = BACK + c.rng.nextInt(c.d - BACK - 1);
            if (!c.empty(x, 1, z) || c.get(x, 0, z) == null) {
                continue;
            }
            double r = c.chance();
            set(x, 1, z, r < 0.3 ? "home_corpse_head[facing=" + c.dir() + "]" : r < 0.55 ? "blood_splat"
                    : r < 0.75 ? "paper" : r < 0.9 ? "trash_bag[facing=" + c.dir() + "]" : "glass_shards");
        }
        // a car up on the walk outside the tavern, another across the gate road
        p.vehicle(181.5, 1, FRONT + 4.5, 75, "sedan,suv", c.rng.nextLong());
        p.vehicle(Foundry.GATE + 0.5, 1, FRONT - 10.5, 30, "pickup,police", c.rng.nextLong());
    }
}
