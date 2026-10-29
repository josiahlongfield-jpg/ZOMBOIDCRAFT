package com.zomboidcraft.world.county;

import static com.zomboidcraft.world.county.Waterfront.BED;
import static com.zomboidcraft.world.county.Waterfront.BZ0;
import static com.zomboidcraft.world.county.Waterfront.BZ1;
import static com.zomboidcraft.world.county.Waterfront.PX1;
import static com.zomboidcraft.world.county.Waterfront.PZ0;
import static com.zomboidcraft.world.county.Waterfront.PZ1;
import static com.zomboidcraft.world.county.Waterfront.SEAWALL;

/**
 * Pier 9, the Waterfront's timber pier: out from the promenade on its piles, under the arch with
 * the pier's board, a row of little shingled shops either side of the boardwalk (the clam shack,
 * saltwater taffy, harbor gifts, the Sea Dog arcade and the T-shirt shop to the north; ice cream,
 * the surf shop and the chowder house to the south), the carousel under its striped tent past them,
 * and at the end the Ferris wheel, white on two A-frame legs, its gondolas hanging still; the
 * benches and telescopes at the rail beyond it.
 *
 * <p>Photos: refs/city/waterfront-pier-ferris-wheel (Navy Pier, Scheveningen, Blackpool's Central
 * Pier). The wheel stands across the boardwalk, its face north and south, so it reads from the
 * terminal and the plaza, from the marina and from the water.
 */
final class WaterfrontPier {
    /** The wheel: its hub (x, y), its radius, the plane its gondolas hang in (the rims either side, the legs three out). */
    static final int WX = 102, WY = 17, WR = 15, WZ = 106;
    /** The carousel's centre. */
    static final int CX = 81, CZ = 114;
    /** The shops: the party walls along each row and what each one sold. */
    private static final int[] NORTH = {41, 49, 57, 67, 79, 88}, SOUTH = {41, 49, 57, 73};
    private static final String[] NORTH_KEYS = {"clams", "taffy", "gifts", "arcade", "tees"}, SOUTH_KEYS = {"scoops", "surf", "chowder"};

    private final Waterfront c;

    WaterfrontPier(Waterfront c) {
        this.c = c;
    }

    private void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    void build() {
        deck();
        for (int i = 0; i < NORTH_KEYS.length; i++) {
            shop(NORTH[i], NORTH[i + 1], true, NORTH_KEYS[i]);
        }
        for (int i = 0; i < SOUTH_KEYS.length; i++) {
            shop(SOUTH[i], SOUTH[i + 1], false, SOUTH_KEYS[i]);
        }
        arch();
        carousel();
        wheel();
        end();
        story();
    }

    // ------------------------------------------------------------------ the deck and the piles

    /** The decking from the seawall out, on stringers and pile caps, the piles down to the bed; the rail round the open edges. */
    private void deck() {
        for (int x = SEAWALL + 1; x <= PX1; x++) {
            for (int z = PZ0; z <= PZ1; z++) {
                set(x, 0, z, c.chance() < 0.08 ? "wf_deck_b" : "wf_deck");
            }
        }
        int[] rows = {PZ0, PZ0 + 7, PZ0 + 14, PZ0 + 21, PZ1};
        for (int x = SEAWALL + 1; x <= PX1; x++) {
            for (int z : rows) {
                set(x, -1, z, "wf_stringer[axis=x]");
            }
        }
        for (int x = SEAWALL + 3; x <= PX1; x += 5) {
            for (int z = PZ0; z <= PZ1; z++) {
                set(x, -2, z, "wf_stringer[axis=z]");
            }
            for (int z : rows) {
                for (int y = BED; y < -2; y++) {
                    set(x, y, z, "wf_pile");
                }
            }
        }
        // the rail where no shop backs onto the edge, a post standing up through it every ten
        for (int x = SEAWALL + 1; x <= PX1; x++) {
            boolean post = x % 10 == 0;
            if (x < NORTH[0] || x > NORTH[NORTH.length - 1]) {
                set(x, 1, PZ0, post ? "wf_pile_top" : "wf_rail[facing=north]");
            }
            if (x < SOUTH[0] || x > SOUTH[SOUTH.length - 1]) {
                set(x, 1, PZ1, post ? "wf_pile_top" : "wf_rail[facing=south]");
            }
        }
        for (int z = PZ0 + 1; z < PZ1; z++) {
            set(PX1, 1, z, z % 10 == 0 ? "wf_pile_top" : "wf_rail[facing=east]");
        }
    }

    // ------------------------------------------------------------------ the shops

    /**
     * A shop between the party walls xa and xb, its back on the pier's edge, its front on the
     * boardwalk: shingle or clapboard, a false front over the door carrying its sign, shop windows
     * either side of the door; fitted out for what it sold.
     */
    private void shop(int xa, int xb, boolean north, String key) {
        int back = north ? PZ0 : PZ1, front = north ? PZ0 + 8 : PZ1 - 8;
        int z0 = Math.min(back, front), z1 = Math.max(back, front);
        String ff = north ? "south" : "north";
        String wall = c.any("wf_shingle", "wf_shingle", "wf_shingle_b", "wf_clap");
        for (int x = xa; x <= xb; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == xa || x == xb || z == z0 || z == z1;
                set(x, 0, z, edge ? "wf_deck_b" : "floor_boards");
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z, edge ? (y == 4 ? "wf_trim" : wall) : "air");
                }
                set(x, 5, z, edge ? "wf_trim" : "un_roof");
            }
            // the false front up past the roof, its cap
            set(x, 5, front, wall);
            set(x, 6, front, "wf_trim");
        }
        int dx = (xa + xb) / 2;
        for (int x = xa + 1; x < xb; x++) {
            if (Math.abs(x - dx) >= 2) {
                set(x, 2, front, c.chance() < c.b.damage * 0.6 ? "window_broken[axis=x]" : "wf_window[facing=" + ff + "]");
                set(x, 3, front, "wf_window[facing=" + ff + "]");
            }
        }
        set(dx, 3, front, wall);
        c.door(dx, 0, front, "shop_door", ff);
        c.row("wf_shop_" + key + "_", 4, dx - 2, 5, north ? front + 1 : front - 1, ff, 0.08);
        fitOut(key, xa, xb, north, ff);
        c.ceilingLights(xa + 1, z0 + 1, xb - 1, z1 - 1, 4);
        c.mess(xa + 1, z0 + 1, xb - 1, z1 - 1, 0, 0.06);
    }

    /** The row inside a shop k back from... k = 0 against the back wall, 6 just inside the door. */
    private static int zk(boolean north, int k) {
        return north ? PZ0 + 1 + k : PZ1 - 1 - k;
    }

    private void fitOut(String key, int xa, int xb, boolean north, String ff) {
        int dx = (xa + xb) / 2;
        String f = "[facing=" + ff + "]";
        switch (key) {
            case "clams", "chowder" -> {
                // the fry line on the back wall, the menu over it, the counter across with a flap at the end
                String[] line = {"dn_fryer", "dn_griddle", "dn_prep", "dn_fryer", "store_oven", "dn_prep", "dn_lowboy"};
                for (int x = xa + 1; x < xb; x++) {
                    set(x, 1, zk(north, 0), line[(x - xa - 1) % line.length] + f);
                }
                c.row("wf_menu_", 2, xa + 2, 3, zk(north, 0), ff, 0);
                int ck = key.equals("clams") ? 3 : 2;
                for (int x = xa + 1; x <= xb - 2 - (key.equals("chowder") ? 1 : 0); x++) {
                    set(x, 1, zk(north, ck), (x == xa + 2 ? "store_register" : "store_counter") + f);
                }
                if (key.equals("chowder")) {
                    // the dining room: tables down the front with a chair either side
                    for (int x = xa + 2; x + 1 < xb; x += 4) {
                        set(x, 1, zk(north, 4), c.any("dn_table_set", "dn_table_mess", "dn_table", "dn_table_blood") + f);
                        set(x - 1, 1, zk(north, 4), (c.chance() < 0.2 ? "home_chair_tipped" : "home_chair") + "[facing=east]");
                        set(x + 1, 1, zk(north, 4), (c.chance() < 0.2 ? "home_chair_tipped" : "home_chair") + "[facing=west]");
                    }
                }
            }
            case "taffy" -> {
                for (int k = 1; k <= 5; k++) {
                    set(xa + 1, 1, zk(north, k), (c.chance() < 0.6 ? "tr_candy_empty" : "tr_candy") + "[facing=east]");
                    set(xb - 1, 1, zk(north, k), (c.chance() < 0.6 ? "tr_candy_empty" : "tr_candy") + "[facing=west]");
                }
                set(xa + 3, 1, zk(north, 0), "wf_taffy" + f);
                set(xb - 3, 1, zk(north, 0), "wf_taffy" + f);
                for (int x = xa + 3; x <= xb - 3; x++) {
                    set(x, 1, zk(north, 2), (x == dx ? "store_register" : "store_counter") + f);
                }
            }
            case "gifts" -> {
                for (int x = xa + 1; x < xb; x++) {
                    set(x, 1, zk(north, 0), "wf_gifts" + f);
                }
                for (int k = 1; k <= 4; k++) {
                    set(xa + 1, 1, zk(north, k), "wf_gifts[facing=east]");
                    set(xb - 1, 1, zk(north, k), "wf_gifts[facing=west]");
                }
                for (int k = 2; k <= 3; k++) {
                    set(dx - 2, 1, zk(north, k), "store_table");
                    set(dx + 2, 1, zk(north, k), "store_table");
                }
                set(xb - 1, 1, zk(north, 5), "store_register[facing=west]");
            }
            case "arcade" -> {
                for (int x = xa + 1; x < xb; x++) {
                    cabinet(x, zk(north, 0), ff);
                }
                for (int k = 2; k <= 5; k++) {
                    cabinet(xa + 1, zk(north, k), "east");
                    cabinet(xb - 1, zk(north, k), "west");
                }
                set(xa + 3, 1, zk(north, 3), "store_counter" + f);
                set(xa + 4, 1, zk(north, 3), "store_register" + f);
            }
            case "tees", "surf" -> {
                for (int x = xa + 1; x < xb; x++) {
                    for (int y = 1; y <= 3; y++) {
                        set(x, y, zk(north, 0), "wf_tees" + f);
                    }
                }
                for (int k = 1; k <= 4; k++) {
                    set(xa + 3, 1, zk(north, k), "store_clothes_rack[facing=east]");
                    if (xb - 3 > xa + 4) {
                        set(xb - 3, 1, zk(north, k), "store_clothes_rack[facing=west]");
                    }
                }
                if (key.equals("surf")) {
                    for (int k = 1; k <= 4; k++) {
                        set(xa + 1, 1, zk(north, k), "store_shelf_hardware[facing=east]");
                    }
                }
                set(xb - 1, 1, zk(north, 5), "store_register[facing=west]");
            }
            case "scoops" -> {
                for (int x = xa + 1; x < xb; x++) {
                    set(x, 1, zk(north, 0), "dn_prep" + f);
                }
                c.row("wf_menu_", 2, xa + 3, 3, zk(north, 0), ff, 0);
                for (int x = xa + 1; x <= xb - 2; x++) {
                    set(x, 1, zk(north, 2), (x == xa + 1 ? "store_register" : "wf_freezer") + f);
                }
                set(xb - 2, 1, zk(north, 5), "store_table");
                set(xb - 3, 1, zk(north, 5), c.any("dn_stool_r", "dn_stool_b", "dn_stool_torn"));
                set(xb - 1, 1, zk(north, 5), c.any("dn_stool_r", "dn_stool_b", "dn_stool_stub"));
            }
            default -> {
            }
        }
    }

    /** An arcade cabinet, its screen lit if the pier still has power. */
    private void cabinet(int x, int z, String facing) {
        set(x, 1, z, "un_arcade_lo[facing=" + facing + "]");
        set(x, 2, z, "un_arcade_hi" + (c.power && c.chance() < 0.5 ? "_on" : "") + "[facing=" + facing + "]");
    }

    // ------------------------------------------------------------------ the arch

    /** At the pier's root, over the boardwalk: white posts and a beam, the pier's board on it, globes on the posts. */
    private void arch() {
        int x = SEAWALL + 3;
        for (int z : new int[] {BZ0, BZ1}) {
            for (int y = 1; y <= 6; y++) {
                set(x, y, z, "wf_trim");
            }
            set(x, 8, z, c.power && c.chance() < 0.6 ? "cv_lamp_globe_lit" : "cv_lamp_globe");
        }
        for (int z = BZ0; z <= BZ1; z++) {
            set(x, 7, z, "wf_trim");
        }
        c.picture("wf_pier_", 3, 2, x, 8, (BZ0 + BZ1) / 2 - 1, "west");
    }

    // ------------------------------------------------------------------ the carousel

    /**
     * The carousel at the south side past the shops: its platform flush with the deck, the
     * mirrored drum in the middle, twelve horses on their brass poles, the striped tent stepping up
     * to the crest.
     */
    private void carousel() {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                int x = CX + dx, z = CZ + dz;
                if (z < PZ0 || z > PZ1) {
                    continue;
                }
                if (r <= 5.5) {
                    set(x, 0, z, "wf_ride_floor");
                    for (int y = 1; y <= 4; y++) {
                        set(x, y, z, "air");
                    }
                }
                int sector = (int) Math.floor((Math.atan2(dz, dx) + Math.PI) / (Math.PI / 6));
                String canvas = sector % 2 == 0 ? "wf_canopy_a" : "wf_canopy_b";
                double[] steps = {6.5, 4.6, 2.6, 1.2};
                for (int i = 0; i < steps.length; i++) {
                    if (r <= steps[i]) {
                        set(x, 5 + i, z, canvas);
                    }
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = 1; y <= 4; y++) {
                    set(CX + dx, y, CZ + dz, "wf_drum");
                }
            }
        }
        String[] coats = {"a", "b", "c"};
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = CX + (int) Math.round(Math.cos(a) * 3.8), z = CZ + (int) Math.round(Math.sin(a) * 3.8);
            // nose along the ride's way round (counter-clockwise from above: toward -sin, +cos)
            double tx = -Math.sin(a), tz = Math.cos(a);
            String facing = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? "east" : "west") : (tz > 0 ? "south" : "north");
            set(x, 1, z, "wf_horse_" + coats[i % 3] + "[facing=" + facing + "]");
            for (int y = 2; y <= 4; y++) {
                set(x, y, z, "wf_brass");
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + Math.PI / 8;
            int x = CX + (int) Math.round(Math.cos(a) * 6), z = CZ + (int) Math.round(Math.sin(a) * 6);
            if (z > PZ0 && z < PZ1) {
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z, "wf_brass");
                }
            }
        }
        set(CX, 9, CZ, "wf_crest");
    }

    // ------------------------------------------------------------------ the wheel

    /**
     * The Ferris wheel standing across the boardwalk, its face up and down the shore: two rims of
     * lit members on sixteen spokes and an inner ring, the hub on its axle between the A-frame legs,
     * sixteen gondolas hung between the rims; a fence round its foot.
     */
    private void wheel() {
        boolean lit = c.power;
        for (int z : new int[] {WZ - 1, WZ + 1}) {
            ring(z, WR, lit ? "wf_rim_lit_" : "wf_rim_");
            ring(z, 8, "wf_spoke_");
            for (int k = 0; k < 16; k++) {
                double a = k * Math.PI / 8;
                line(z, WX + Math.cos(a) * 1.2, WY + Math.sin(a) * 1.2, WX + Math.cos(a) * (WR - 1), WY + Math.sin(a) * (WR - 1), "wf_spoke_", false);
            }
            set(WX, WY, z, "wf_hub");
        }
        // the axle through to the legs
        for (int z = WZ - 3; z <= WZ + 3; z++) {
            if (Math.abs(z - WZ) != 1) {
                set(WX, WY, z, Math.abs(z - WZ) == 3 ? "wf_truss" : "wf_steel");
            }
        }
        // the A-frames: a leg a side from a footing on the deck up to the axle at 67.5 degrees, a tie across low down
        for (int z : new int[] {WZ - 3, WZ + 3}) {
            for (int side = -1; side <= 1; side += 2) {
                int fx = WX + side * 6;
                set(fx, 0, z, "wf_cap");
                line(z, fx, 1, WX, WY - 1, "wf_leg_", true);
            }
            line(z, WX - 3, 7, WX + 3, 7, "wf_leg_", false);
        }
        // the gondolas, one at the end of each spoke; one or two gone
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = WX + (int) Math.round(Math.cos(a) * WR), y = WY + (int) Math.round(Math.sin(a) * WR);
            if (c.chance() < 0.1) {
                continue;
            }
            set(x, y, WZ, "wf_gondola_" + (i % 2 == 0 ? "a" : "b"));
        }
        // one came down onto the deck by the fence
        if (c.chance() < 0.6) {
            set(WX - 4, 1, BZ1 + 2, "wf_gondola_b");
        }
        // the fence round the foot
        for (int x = WX - 7; x <= WX + 7; x++) {
            set(x, 1, WZ - 4, "wf_rail[facing=north]");
            set(x, 1, WZ + 4, "wf_rail[facing=south]");
        }
        for (int z = WZ - 4; z <= WZ + 4; z++) {
            set(WX - 8, 1, z, "wf_rail[facing=west]");
            set(WX + 8, 1, z, "wf_rail[facing=east]");
        }
    }

    /**
     * A straight member from (x0, y0) to (x1, y1) in the plane z, a cell per step along its long
     * way, each turned to its direction and set off its cell's centre to stay on the true line; over
     * what is already there only if over.
     */
    private void line(int z, double x0, double y0, double x1, double y1, String prefix, boolean over) {
        double dx = x1 - x0, dy = y1 - y0;
        int k = Math.floorMod((int) Math.round(Math.atan2(dy, dx) / (Math.PI / 8)), 8);
        double n = k * Math.PI / 8 + Math.PI / 2, nx = Math.cos(n), ny = Math.sin(n);
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        for (int i = 0; i <= steps; i++) {
            double px = x0 + dx * i / steps, py = y0 + dy * i / steps;
            int cx = (int) Math.round(px), cy = (int) Math.round(py);
            if (!over && !c.empty(cx, cy, z)) {
                continue;
            }
            double o = ((px - cx) * nx + (py - cy) * ny) * 16;
            set(cx, cy, z, prefix + k + (o > 3 ? "_p" : o < -3 ? "_m" : ""));
        }
    }

    /**
     * A ring of members radius r round the hub in the plane z, each turned along the ring: the
     * cells a midpoint circle (one cell per step, never a corner cell between two diagonal ones,
     * which would stand a second member beside the first).
     */
    private void ring(int z, int r, String prefix) {
        java.util.Set<Long> cells = new java.util.LinkedHashSet<>();
        int a = 0, b = r, e = 1 - r;
        while (a <= b) {
            for (int[] q : new int[][] {{a, b}, {b, a}, {-a, b}, {-b, a}, {a, -b}, {b, -a}, {-a, -b}, {-b, -a}}) {
                cells.add(((long) q[0] << 32) | (q[1] & 0xffffffffL));
            }
            a++;
            if (e < 0) {
                e += 2 * a + 1;
            } else {
                b--;
                e += 2 * (a - b) + 1;
            }
        }
        for (long cell : cells) {
            int dx = (int) (cell >> 32), dy = (int) cell;
            double dist = Math.hypot(dx, dy);
            // the member's direction: square to the radius at the cell's own angle, in 22.5 degree steps
            int k = Math.floorMod((int) Math.round((Math.atan2(dy, dx) + Math.PI / 2) / (Math.PI / 8)), 8);
            // how far the true circle passes off the cell's centre, along the member's left normal
            double n = k * Math.PI / 8 + Math.PI / 2;
            double o = (r - dist) * (dx * Math.cos(n) + dy * Math.sin(n)) / dist * 16;
            set(WX + dx, WY + dy, z, prefix + k + (o > 3 ? "_p" : o < -3 ? "_m" : ""));
        }
    }

    // ------------------------------------------------------------------ the end and the sides

    /** Benches along the north rail looking out, the lookout past the wheel: telescopes and a life ring at the rail. */
    private void end() {
        for (int x = NORTH[NORTH.length - 1] + 4; x < PX1 - 3; x += 8) {
            set(x, 1, PZ0 + 1, "st_bench[facing=south]");
            set(x + 1, 1, PZ0 + 1, "st_bench[facing=south]");
        }
        for (int x = CX + 9; x < PX1 - 3; x += 8) {
            set(x, 1, PZ1 - 1, "st_bench[facing=north]");
            set(x + 1, 1, PZ1 - 1, "st_bench[facing=north]");
        }
        for (int z = PZ0 + 3; z <= PZ1 - 3; z += 6) {
            if (Math.abs(z - WZ) > 4) {
                set(PX1 - 1, 1, z, c.chance() < 0.7 ? "wf_telescope[facing=east]" : "wf_ring_post[facing=east]");
            }
        }
        set(PX1 - 1, 1, WZ, "wf_ring_post[facing=east]");
        for (int z = PZ0 + 2; z < PZ1 - 1; z += 5) {
            if (c.chance() < 0.5 && c.empty(PX1 - 2, 1, z)) {
                set(PX1 - 2, 1, z, c.chance() < 0.6 ? "wf_pots[facing=" + c.dir() + "]" : "wf_coil");
            }
        }
        // the signs and the words on the shop ends
        int ne = NORTH[NORTH.length - 1], se = SOUTH[SOUTH.length - 1];
        c.sign("nofish", ne + 1, 3, PZ0 + 5, "east");
        c.row("wf_spray_help_", 2, ne + 1, 2, PZ0 + 2, "east", 0);
        c.sign("nodive", se + 1, 3, PZ1 - 5, "east");
        // lamps down the boardwalk, against the party walls so they stand clear of the doors
        for (int i = 1; i < NORTH.length - 1; i++) {
            c.lampPost(NORTH[i], BZ0);
        }
        for (int i = 1; i < SOUTH.length - 1; i++) {
            c.lampPost(SOUTH[i], BZ1);
        }
        c.lampPost(WX - 10, BZ0);
        c.lampPost(WX + 10, BZ0);
        c.lampPost(WX + 10, BZ1);
    }

    /** The crowd came out here too: litter and luggage down the boardwalk. */
    private void story() {
        for (int x = SEAWALL + 1; x < PX1; x++) {
            for (int z = BZ0; z <= BZ1; z++) {
                if (c.empty(x, 1, z) && c.chance() < 0.03) {
                    // nothing solid in the rows along the shop fronts, where it would stand in a door
                    boolean flat = z < BZ0 + 2 || z > BZ1 - 2;
                    double r = c.chance();
                    set(x, 1, z, r < 0.4 || flat && r < 0.7 ? "paper" : r < 0.6 ? "wf_bags_" + c.any("a", "b", "c") : r < 0.75 ? "blood_splat"
                            : r < 0.85 || flat ? "glass_shards" : "trash_bag[facing=" + c.dir() + "]");
                }
            }
        }
    }
}
