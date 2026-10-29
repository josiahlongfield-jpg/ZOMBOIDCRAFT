package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Market.CX;
import static com.zomboidcraft.world.county.Market.CZ;
import static com.zomboidcraft.world.county.Market.HX0;
import static com.zomboidcraft.world.county.Market.HX1;
import static com.zomboidcraft.world.county.Market.HZ0;
import static com.zomboidcraft.world.county.Market.HZ1;
import static com.zomboidcraft.world.county.Market.PORCH;

/**
 * The market house: granite, two storeys, a Doric portico at each end with the name in the frieze
 * and the city's panel in the pediment, a dome on a drum over the middle with the clock in it.
 *
 * <p>The ground floor is the market: an aisle down the middle under the pendant lamps, the stalls
 * either side between the piers, six blocks to a stall, each with its counter to the aisle, its
 * board hung over the counter and its trade behind (the fish man's ice, the butcher's case and
 * hooks, the cheese, the bread racks, the fruit stood up in tilted crates); cross aisles to the
 * doors in the long walls. Under the dome the stall keepers' barter post, a ring of counters with
 * chicken wire to the ceiling and the rates chalked up over the north doors. Upstairs the long
 * room: the market master's office, the tables the city used for its suppers, the keepers' cots,
 * their stores; a gallery round the well under the dome. Stairs at the far corners up to the roof,
 * its balustrade and the drum.
 *
 * <p>Piers stand where (x - CX) % 6 == 3 along the long walls and (z - CZ) % 6 == 3 along the ends,
 * so the middle of every bay lines up with the dome.
 */
final class MarketHall {
    /** The upper floor's slab and the roof's. */
    static final int UP = 8, ROOF = 15;
    /** The stairs: the west one on the south side, the east one on the north side (5 by 10 shafts). */
    static final int WST_X = CX - 68, WST_Z = HZ1 - 9, EST_X = CX + 64, EST_Z = HZ0;
    /** The bays with doors in the long walls (by their middles). */
    static final int[] DOOR_BAYS = {CX - 54, CX - 30, CX, CX + 30, CX + 54};
    /** The drum's half width (its octagon: |dx|, |dz| <= DR, |dx| + |dz| <= DD) and top. */
    static final int DR = 9, DD = 13, DRUM_TOP = ROOF + 7;

    /** What each stall sold, in the order they're handed out down the hall. */
    private static final String[] TRADES = {"fish", "produce", "meat", "cheese", "bread", "greens", "spice", "deli", "oyster",
            "poultry", "candy", "coffee", "flowers", "nuts", "pickles", "donuts", "produce", "fish", "meat", "bread"};

    private final Market c;
    private int trade;

    MarketHall(Market c) {
        this.c = c;
    }

    void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    void fill(int x0, int y0, int z0, int x1, int y1, int z1, String b) {
        c.fill(x0, y0, z0, x1, y1, z1, b);
    }

    boolean empty(int x, int y, int z) {
        return c.empty(x, y, z);
    }

    void build() {
        shell();
        porticoes();
        stalls();
        barter();
        upstairs();
        roof();
        drum();
        c.stair(WST_X, WST_Z, false, new int[] {0, UP, ROOF}, "om_granite", "un_roof");
        c.stair(EST_X, EST_Z, true, new int[] {0, UP, ROOF}, "om_granite", "un_roof");
        c.sign("up", WST_X + 3, 4, WST_Z - 1, "north");
        c.sign("up", EST_X + 3, 4, EST_Z + 10, "south");
    }

    // ------------------------------------------------------------------ helpers

    /** Inside a stair shaft (its walls and its well). */
    static boolean shaft(int x, int z) {
        return x >= WST_X && x <= WST_X + 4 && z >= WST_Z && z <= WST_Z + 9 || x >= EST_X && x <= EST_X + 4 && z >= EST_Z && z <= EST_Z + 9;
    }

    /** Within the octagon a by b about the dome's middle. */
    static boolean oct(int x, int z, int a, int b) {
        int dx = Math.abs(x - CX), dz = Math.abs(z - CZ);
        return dx <= a && dz <= b && dx + dz <= (Math.max(a, b) * 13 + 4) / 9;
    }

    /** The way out from the dome's middle at (x, z): the side its biggest offset is on. */
    static String out(int x, int z) {
        int dx = x - CX, dz = z - CZ;
        return Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? "east" : "west") : (dz > 0 ? "south" : "north");
    }

    private String granite(int x, int y, int z) {
        double r = c.chance();
        if (y <= 2 && r < 0.25) {
            return "om_granite_moss";
        }
        return r < 0.18 ? "om_granite_b" : "om_granite";
    }

    private boolean pierX(int x) {
        return Math.floorMod(x - CX, 6) == 3;
    }

    private boolean pierZ(int z) {
        return Math.floorMod(z - CZ, 6) == 3;
    }

    private String sash(String out) {
        double r = c.chance();
        String s = r < 0.22 ? "om_sash_broken" : c.power && r < 0.27 ? "om_sash_lit" : r < 0.3 ? "boarded_window" : "om_sash";
        return s.equals("boarded_window")
                ? s + "[axis=" + (out.equals("north") || out.equals("south") ? "x" : "z") + "]" : s + "[facing=" + out + "]";
    }

    // ------------------------------------------------------------------ the shell

    /**
     * The walls: a granite pier every six, between them the plinth, the tall ground-floor window
     * with its lintel, the band at the floor, the upper window, the cornice; the floors, the doors.
     */
    private void shell() {
        for (int x = HX0; x <= HX1; x++) {
            for (int z = HZ0; z <= HZ1; z++) {
                set(x, 0, z, c.chance() < 0.12 ? "om_flags_b" : "om_flags");
                boolean wall = x == HX0 || x == HX1 || z == HZ0 || z == HZ1;
                if (!wall) {
                    for (int y = 1; y < ROOF; y++) {
                        set(x, y, z, "air");
                    }
                    if (!shaft(x, z)) {
                        set(x, UP, z, "floor_boards");
                        set(x, ROOF, z, "un_roof");
                    }
                    continue;
                }
                boolean alongX = z == HZ0 || z == HZ1;
                boolean corner = (x == HX0 || x == HX1) && (z == HZ0 || z == HZ1);
                boolean pier = corner || (alongX ? pierX(x) : pierZ(z));
                String out = z == HZ0 ? "north" : z == HZ1 ? "south" : x == HX0 ? "west" : "east";
                for (int y = 1; y <= ROOF; y++) {
                    String b;
                    if (y == ROOF) {
                        b = "cv_cornice_stone[facing=" + out + "]";
                    } else if (pier) {
                        b = granite(x, y, z);
                    } else if (y == 1 || y == 7 || y == 9 || y == 14) {
                        b = y == 1 ? "om_granite_b" : "stone_trim";
                    } else if (y == UP) {
                        b = "stone_trim";
                    } else if (y >= 2 && y <= 6 || y >= 10 && y <= 13) {
                        b = sash(out);
                    } else {
                        b = granite(x, y, z);
                    }
                    set(x, y, z, b);
                }
            }
        }
        // the doors in the long walls: three leaves in the bay, the fanlight over them
        for (int bx : DOOR_BAYS) {
            for (int z : new int[] {HZ0, HZ1}) {
                String f = z == HZ0 ? "north" : "south";
                for (int x = bx - 1; x <= bx + 1; x++) {
                    c.door(x, 0, z, "shop_door", f);
                    set(x, 3, z, "stone_trim");
                    for (int y = 4; y <= 6; y++) {
                        set(x, y, z, sash(f));
                    }
                }
                c.sign("hall", bx, 7, z == HZ0 ? z - 1 : z + 1, f);
            }
        }
        // the ends: the doors in the middle bay under the fanlight, sidelights either side
        for (int x : new int[] {HX0, HX1}) {
            String f = x == HX0 ? "west" : "east";
            for (int z = CZ - 1; z <= CZ + 1; z++) {
                c.door(x, 0, z, "shop_door", f);
            }
            for (int z : new int[] {CZ - 2, CZ + 2}) {
                set(x, 1, z, "om_granite_b");
                set(x, 2, z, sash(f));
                set(x, 3, z, sash(f));
            }
            for (int z = CZ - 2; z <= CZ + 2; z++) {
                set(x, 4, z, "om_granite");
                set(x, 5, z, "om_granite");
                set(x, 6, z, "om_granite");
            }
            c.picture("om_fan_", 5, 2, x, 4, CZ - 2, f);
        }
        // the floors' lamps: pendants down the aisle, lamps under the long room's ceiling
        for (int x = HX0 + 4; x < HX1 - 2; x += 6) {
            if (!oct(x, CZ, DR, DR)) {
                set(x, UP - 1, CZ, c.power && c.chance() < 0.4 ? "tx_pendant" : "tx_pendant_off");
            }
            if (x % 12 == 4 && !oct(x, CZ, DR, DR)) {
                set(x, ROOF - 1, CZ, c.power && c.chance() < 0.3 ? "ch_chandelier_lit" : "ch_chandelier");
            }
        }
    }

    // ------------------------------------------------------------------ the porticoes

    /**
     * Four Doric columns and an anta at each corner, the architrave with the name over them, the
     * granite pediment one in two with the city's panel, its raking cornice run back to the hall
     * as the porch's roof.
     */
    private void porticoes() {
        for (int end = 0; end < 2; end++) {
            boolean west = end == 0;
            String f = west ? "west" : "east";
            int front = west ? HX0 - PORCH : HX1 + PORCH;
            int xa = Math.min(front, west ? HX0 - 1 : HX1 + 1), xb = Math.max(front, west ? HX0 - 1 : HX1 + 1);
            for (int x = xa; x <= xb; x++) {
                for (int z = HZ0; z <= HZ1; z++) {
                    set(x, 0, z, c.chance() < 0.1 ? "om_flags_b" : "om_flags");
                    set(x, ROOF, z, "om_granite");
                    set(x, ROOF - 1, z, z == HZ0 || z == HZ1 || x == front ? "om_granite" : "air");
                }
                set(x, ROOF, HZ0, "cv_cornice_stone[facing=north]");
                set(x, ROOF, HZ1, "cv_cornice_stone[facing=south]");
            }
            for (int z = HZ0; z <= HZ1; z++) {
                set(front, ROOF, z, "cv_cornice_stone[facing=" + f + "]");
            }
            // the antae at the corners, full height; the columns between
            for (int z : new int[] {HZ0, HZ1}) {
                for (int y = 1; y < ROOF - 1; y++) {
                    set(front, y, z, granite(front, y, z));
                }
            }
            for (int z : new int[] {CZ - 9, CZ - 3, CZ + 3, CZ + 9}) {
                set(front, 1, z, "cv_column_base_stone");
                for (int y = 2; y < ROOF - 2; y++) {
                    set(front, y, z, "cv_column_stone");
                }
                set(front, ROOF - 2, z, "cv_column_cap_stone[facing=" + f + "]");
            }
            // the name in the frieze, the cornice over it
            c.row("om_name_", 22, west ? front - 1 : front + 1, ROOF - 1, CZ - 11, f, 0.03);
            // the pediment: row i spans z HZ0 + 2i .. HZ1 - 2i, run back over the porch
            int span = HZ1 - HZ0;
            for (int i = 0; 2 * i + 1 < span - 2 * i; i++) {
                int y = ROOF + 1 + i, z0 = HZ0 + 2 * i, z1 = HZ1 - 2 * i;
                for (int x = xa; x <= xb; x++) {
                    for (int z = z0; z <= z1; z++) {
                        // west: the left end (rake_l) at the high z; east: at the low z
                        int kl = west ? z1 - z : z - z0, kr = west ? z - z0 : z1 - z;
                        String b = kl < 2 ? "om_rake_l_" + kl + "[facing=" + f + "]"
                                : kr < 2 ? "om_rake_r_" + kr + "[facing=" + f + "]" : "om_granite";
                        set(x, y, z, b);
                    }
                }
            }
            c.picture("om_date_", 4, 2, front, ROOF + 2, CZ - 2, f);
        }
    }

    // ------------------------------------------------------------------ the stalls

    /** Each bay along each side either a stall, a cross aisle to a door, a stair or the rotunda. */
    private void stalls() {
        for (int bc = CX - 66; bc <= CX + 66; bc += 6) {
            boolean door = false;
            for (int b : DOOR_BAYS) {
                door |= b == bc;
            }
            if (Math.abs(bc - CX) <= 6) {
                continue;
            }
            for (int side = 0; side < 2; side++) {
                boolean north = side == 0;
                if (shaft(bc, north ? HZ0 + 3 : HZ1 - 3)) {
                    continue;
                }
                if (door) {
                    aisle(bc, north);
                } else {
                    stall(bc, north, TRADES[trade++ % TRADES.length]);
                }
            }
        }
        c.mess(HX0 + 1, HZ0 + 1, HX1 - 1, HZ1 - 1, 0, 0.025);
    }

    /** A cross aisle from a door to the main aisle: a bench, the market's notices on the piers. */
    private void aisle(int bc, boolean north) {
        int zc = north ? HZ0 + 1 : HZ1 - 1;
        set(bc - 3, 2, zc, "un_poster_" + c.rng.nextInt(6) + "[facing=" + (north ? "east" : "west") + "]");
        if (c.chance() < 0.5) {
            set(bc + 2, 1, north ? HZ0 + 4 : HZ1 - 4, "st_bench[facing=west]");
        }
    }

    /**
     * One stall in the bay about bc: counters along the aisle with a gap at the east end to get
     * behind, posts at the piers carrying the beam the board hangs from, a low divider to the next
     * stall, and what the stall sold behind the counter.
     */
    private void stall(int bc, boolean north, String kind) {
        int cz = north ? CZ - 3 : CZ + 3;                 // the counters' row
        int back = north ? HZ0 + 1 : HZ1 - 1;              // against the wall
        int dz = north ? 1 : -1;                           // from the wall toward the counter
        String toAisle = north ? "south" : "north", toWall = north ? "north" : "south";
        // the posts and the beam over the counter line, shared with the stalls either side
        for (int x : new int[] {bc - 3, bc + 3}) {
            for (int y = 1; y <= 4; y++) {
                set(x, y, cz, "om_post");
            }
        }
        for (int x = bc - 3; x <= bc + 3; x++) {
            set(x, 5, cz, "om_beam[axis=x]");
        }
        c.row("om_board_" + kind + "_", 3, bc - 1, 4, cz, toAisle, 0.0);
        // the dividers to the next stalls: a counter-high partition
        for (int z = back; z != cz; z += dz) {
            set(bc - 3, 1, z, "om_counter[facing=east]");
        }
        // the counters, the gap at the east end
        for (int x = bc - 2; x <= bc + 1; x++) {
            set(x, 1, cz, counter(kind, x - bc) + "[facing=" + toAisle + "]");
        }
        // behind: the trade's stock along the wall, the block and the hooks for the butchers
        for (int x = bc - 2; x <= bc + 2; x++) {
            String s = stock(kind, x - bc);
            if (s != null) {
                set(x, 1, back, s.contains("[") || s.startsWith("om_pickles") || s.startsWith("om_flowers") || s.startsWith("om_block")
                        ? s : s + "[facing=" + toAisle + "]");
            }
        }
        if (kind.equals("meat") || kind.equals("poultry") || kind.equals("deli")) {
            for (int x = bc - 2; x <= bc + 1; x++) {
                set(x, 3, back + dz, c.chance() < 0.6 ? "om_hooks[facing=" + toAisle + "]" : "om_hooks_empty[facing=" + toAisle + "]");
            }
            set(bc, 1, back + 2 * dz, "om_block");
        }
        if (kind.equals("fish") || kind.equals("produce") || kind.equals("greens")) {
            set(bc + 2, 4, cz, "om_scale");
        }
        // the prices chalked on the wall behind
        String[] chalk = {"fish", "meat", "produce", "bread"};
        for (int i = 0; i < chalk.length; i++) {
            if (kind.equals(chalk[i]) || kind.equals("greens") && i == 2) {
                set(bc + 1, 2, back, "om_chalk_" + i + "[facing=" + toAisle + "]");
            }
        }
        // the register and a stool
        if (c.chance() < 0.7) {
            set(bc + 1, 1, cz - dz, c.chance() < 0.5 ? "store_register[facing=" + toWall + "]" : "trade_folding_chair[facing=" + toAisle + "]");
        }
        if (kind.equals("oyster")) {
            for (int x = bc - 2; x <= bc + 1; x += 2) {
                set(x, 1, cz + dz, "pk_bar_chair");
            }
        }
    }

    private String counter(String kind, int i) {
        return switch (kind) {
            case "fish" -> c.chance() < 0.45 ? "om_fish_rot" : "om_fish";
            case "oyster" -> "om_oyster";
            case "meat", "poultry", "deli" -> i == 1 ? "om_counter" : "om_meat_case";
            case "cheese" -> "om_cheese";
            case "produce" -> c.chance() < 0.4 ? "om_produce_rot" : "om_produce_a";
            case "greens" -> c.chance() < 0.5 ? "om_produce_rot" : "om_produce_b";
            case "spice", "nuts" -> i == 0 ? "om_counter" : "om_spice";
            case "candy" -> "om_candy";
            case "coffee" -> "om_coffee";
            case "flowers" -> "om_counter";
            default -> "om_counter";
        };
    }

    private String stock(String kind, int i) {
        return switch (kind) {
            case "fish", "oyster" -> i % 2 == 0 ? "pk_fridge" : "om_block";
            case "meat", "poultry", "deli", "cheese" -> i % 2 == 0 ? "pk_fridge" : "om_crates";
            case "bread", "donuts" -> "om_bread";
            case "produce", "greens" -> i == 0 ? "om_sacks" : "om_crates";
            case "spice", "nuts", "coffee" -> "om_sacks";
            case "candy" -> "pk_shelf_grocery_" + (1 + Math.floorMod(i, 5));
            case "flowers" -> "om_flowers";
            case "pickles" -> "om_pickles";
            default -> c.chance() < 0.5 ? "om_crates" : null;
        };
    }

    // ------------------------------------------------------------------ the barter post

    /**
     * Under the dome: a square of counters with chicken wire to the ceiling, the slot under it, a
     * gate on the east side; inside the stock they traded from, water, the scales, the keepers'
     * table; the rates chalked over the north doors and their banner off the portico.
     */
    private void barter() {
        int r = 4;
        for (int x = CX - r; x <= CX + r; x++) {
            for (int z = CZ - r; z <= CZ + r; z++) {
                boolean edge = Math.abs(x - CX) == r || Math.abs(z - CZ) == r;
                if (!edge) {
                    continue;
                }
                if (x == CX + r && z == CZ) {
                    c.door(x, 0, z, "flat_door", "east");
                    continue;
                }
                String f = out(x, z);
                set(x, 1, z, "om_barter[facing=" + f + "]");
                for (int y = 2; y <= 3; y++) {
                    set(x, y, z, "om_wire_wall[axis=" + (Math.abs(z - CZ) == r ? "x" : "z") + "]");
                }
            }
        }
        int[][] stock = {{-3, -3}, {-3, -2}, {-2, -3}, {3, -3}, {3, 3}, {-3, 3}, {-2, 3}, {-3, 2}};
        String[] what = {"st_pallet_water", "om_sacks", "om_crates", "st_pallet_water_half", "om_sacks", "om_crates", "st_pallet_meals",
                "om_pickles"};
        for (int i = 0; i < stock.length; i++) {
            String s = what[i];
            set(CX + stock[i][0], 1, CZ + stock[i][1], s.startsWith("st_") || s.equals("om_pickles") ? s : s + "[facing=" + c.dir() + "]");
        }
        set(CX, 1, CZ, "trade_banquet_table");
        set(CX - 1, 1, CZ, "trade_folding_chair[facing=east]");
        set(CX + 1, 1, CZ, "trade_folding_chair[facing=west]");
        set(CX, 3, CZ - 1, "om_scale");
        c.picture("om_prices_", 3, 2, CX - 1, 5, HZ0 + 1, "south");
        c.row("om_banner_", 6, CX - 2, 9, HZ0 - 1, "north", 0.0);
        c.row("om_spray_keep_", 4, CX - 1, 2, CZ - r - 1, "north", 0.0);
        c.sign("noguns", CX + r + 1, 4, CZ - 1, "east");
    }

    // ------------------------------------------------------------------ upstairs

    /**
     * The long room: the gallery round the well under the dome, the market master's office in the
     * west end, the supper tables, the keepers' cots; the east end their stores and their kitchen.
     */
    private void upstairs() {
        int y = UP;
        // the well under the dome, its railing
        for (int x = CX - DR; x <= CX + DR; x++) {
            for (int z = HZ0 + 1; z < HZ1; z++) {
                if (oct(x, z, 5, 5)) {
                    set(x, y, z, "air");
                } else if (oct(x, z, 6, 6)) {
                    set(x, y + 1, z, "railing[facing=" + out(x, z) + "]");
                }
            }
        }
        // the market master's office in the north-west corner
        int ox1 = HX0 + 12, oz1 = HZ0 + 8;
        c.partX(HX0 + 1, ox1, oz1, y, ROOF - y, "cv_wainscot", "plaster_cream");
        c.partZ(ox1, HZ0 + 1, oz1, y, ROOF - y, "cv_wainscot", "plaster_cream");
        c.door(ox1, y, HZ0 + 5, "flat_door", "east");
        c.sign("master", ox1 + 1, y + 3, HZ0 + 3, "east");
        set(HX0 + 4, y + 1, HZ0 + 3, "pw_desk[facing=south]");
        set(HX0 + 4, y + 1, HZ0 + 4, "office_chair[facing=north]");
        set(HX0 + 1, y + 1, HZ0 + 1, "filing_cabinet[facing=east]");
        set(HX0 + 1, y + 1, HZ0 + 2, "filing_cabinet[facing=east]");
        set(HX0 + 8, y + 1, HZ0 + 1, "store_safe[facing=south]");
        set(HX0 + 10, y + 1, HZ0 + 1, "pk_bookshelf_worn[facing=south]");
        // the supper tables down the west long room
        for (int x = HX0 + 16; x <= CX - 14; x += 5) {
            for (int z : new int[] {CZ - 4, CZ + 4}) {
                for (int k = 0; k < 3; k++) {
                    set(x + k, y + 1, z, "trade_banquet_table");
                    if (c.chance() < 0.7) {
                        set(x + k, y + 1, z - 1, c.chance() < 0.15 ? "trade_folding_chair_tipped[facing=" + c.dir() + "]" : "trade_folding_chair[facing=south]");
                    }
                    if (c.chance() < 0.7) {
                        set(x + k, y + 1, z + 1, "trade_folding_chair[facing=north]");
                    }
                }
            }
        }
        // the keepers' cots along the south wall of the west room, a curtain of boards between
        for (int x = HX0 + 8; x <= CX - 14; x += 3) {
            if (shaft(x, HZ1 - 2) || shaft(x + 1, HZ1 - 2)) {
                continue;
            }
            set(x, y + 1, HZ1 - 1, "sc_cot_head[facing=east]");
            set(x + 1, y + 1, HZ1 - 1, "sc_cot_foot[facing=east]");
        }
        // the east room: the keepers' stores in racks, the kitchen at the end
        for (int x = CX + 14; x <= EST_X - 6; x += 4) {
            for (int z = HZ0 + 2; z <= HZ1 - 2; z++) {
                if (Math.abs(z - CZ) <= 1) {
                    continue;
                }
                double r = c.chance();
                String s = r < 0.3 ? "pk_metal_rack[facing=" + (z < CZ ? "south" : "north") + "]" : r < 0.5 ? "om_sacks[facing=east]"
                        : r < 0.7 ? "om_crates[facing=east]" : r < 0.8 ? "st_pallet_water" : r < 0.85 ? "om_pickles" : null;
                if (s != null) {
                    set(x, y + 1, z, s);
                }
            }
        }
        for (int z = CZ + 3; z <= HZ1 - 1; z++) {
            set(HX1 - 1, y + 1, z, z % 3 == 0 ? "pk_cooker[facing=west]" : "home_counter_white[facing=west]");
        }
        set(HX1 - 3, y + 1, HZ1 - 1, "pk_fridge[facing=north]");
        set(HX1 - 4, y + 1, HZ1 - 1, "bunk_drum_water");
        c.mess(HX0 + 1, HZ0 + 1, HX1 - 1, HZ1 - 1, y, 0.02);
    }

    // ------------------------------------------------------------------ the roof

    /** The balustrade round the roof's edge, piers at the corners and over the piers; the laylights. */
    private void roof() {
        for (int x = HX0; x <= HX1; x++) {
            for (int z : new int[] {HZ0, HZ1}) {
                set(x, ROOF + 1, z, pierX(x) || x == HX0 || x == HX1 ? "cv_pier_stone" : "cv_balustrade_stone[axis=x]");
            }
        }
        for (int z = HZ0 + 1; z < HZ1; z++) {
            for (int x : new int[] {HX0, HX1}) {
                set(x, ROOF + 1, z, pierZ(z) ? "cv_pier_stone" : "cv_balustrade_stone[axis=z]");
            }
        }
        for (int x = HX0 + 6; x < HX1 - 4; x += 6) {
            if (oct(x, CZ, DR, DR) || shaft(x, CZ)) {
                continue;
            }
            for (int z = CZ - 1; z <= CZ + 1; z++) {
                set(x, ROOF, z, "cv_skylight");
            }
        }
    }

    // ------------------------------------------------------------------ the dome

    /**
     * The drum: an octagon of granite with a window in each face, the clock in the north and south
     * faces, the cornice round its top; the copper dome stepping in over it, the lantern on top.
     * Open inside from the market floor to the dome.
     */
    private void drum() {
        // open the roof inside the drum
        for (int x = CX - DR; x <= CX + DR; x++) {
            for (int z = CZ - DR; z <= CZ + DR; z++) {
                if (oct(x, z, DR - 1, DR - 1)) {
                    set(x, ROOF, z, "air");
                }
            }
        }
        for (int y = ROOF + 1; y <= DRUM_TOP; y++) {
            for (int x = CX - DR; x <= CX + DR; x++) {
                for (int z = CZ - DR; z <= CZ + DR; z++) {
                    if (oct(x, z, DR, DR) && !oct(x, z, DR - 1, DR - 1)) {
                        set(x, y, z, y == ROOF + 1 || y == DRUM_TOP ? "stone_trim" : granite(x, y, z));
                    }
                }
            }
        }
        // windows in the east and west faces and the stepped diagonal ones, the clocks north and south
        for (int y = ROOF + 3; y <= ROOF + 5; y++) {
            for (int dz = -1; dz <= 1; dz++) {
                set(CX - DR, y, CZ + dz, "om_sash[facing=west]");
                set(CX + DR, y, CZ + dz, "om_sash[facing=east]");
            }
            for (int sx : new int[] {-1, 1}) {
                for (int sz : new int[] {-1, 1}) {
                    set(CX + sx * 6, y, CZ + sz * 7, "om_sash[facing=" + (sz < 0 ? "north" : "south") + "]");
                    set(CX + sx * 7, y, CZ + sz * 6, "om_sash[facing=" + (sx < 0 ? "west" : "east") + "]");
                }
            }
        }
        boolean lit = c.power && c.chance() < 0.5;
        String dial = lit ? "cv_dial_lit_" : "cv_dial_";
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 4; i++) {
                int y = ROOF + 5 - j;
                String tile = dial + (j * 4 + i);
                set(CX + 2 - i, y, CZ - DR - 1, tile + "[facing=north]");
                set(CX - 1 + i, y, CZ + DR + 1, tile + "[facing=south]");
            }
        }
        // the cornice round the drum's top
        for (int x = CX - DR - 1; x <= CX + DR + 1; x++) {
            for (int z = CZ - DR - 1; z <= CZ + DR + 1; z++) {
                if (oct(x, z, DR + 1, DR + 1) && !oct(x, z, DR, DR)) {
                    set(x, DRUM_TOP, z, "cv_cornice_stone[facing=" + out(x, z) + "]");
                }
            }
        }
        // the dome: rings stepping in, each ring the cells of its octagon not inside the next one in
        int[] a = new int[DR + 1];
        for (int k = 0; k <= DR; k++) {
            a[k] = (int) Math.round(DR * Math.sqrt(Math.max(0, 1 - Math.pow(k / (double) DR, 2))));
        }
        int top = DRUM_TOP;
        for (int k = 0; k <= DR && a[k] > 0; k++) {
            int y = DRUM_TOP + 1 + k;
            int inner = Math.min(a[k] - 1, k + 1 <= DR ? a[k + 1] : 0);
            for (int x = CX - a[k]; x <= CX + a[k]; x++) {
                for (int z = CZ - a[k]; z <= CZ + a[k]; z++) {
                    if (oct(x, z, a[k], a[k]) && (inner < 0 || !oct(x, z, inner, inner))) {
                        set(x, y, z, "cv_copper");
                    }
                }
            }
            top = y;
        }
        // the lantern: four posts and the glass between them, the copper cap and the finial
        for (int y = top + 1; y <= top + 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean corner = dx != 0 && dz != 0;
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    set(CX + dx, y, CZ + dz, corner ? "cv_column_stone" : "om_sash[facing=" + out(CX + dx, CZ + dz) + "]");
                }
            }
        }
        fill(CX - 1, top + 3, CZ - 1, CX + 1, top + 3, CZ + 1, "cv_copper_slab");
        set(CX, top + 4, CZ, "cv_finial");
    }
}
