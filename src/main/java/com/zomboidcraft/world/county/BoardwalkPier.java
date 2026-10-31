package com.zomboidcraft.world.county;

/**
 * Seabright's amusement pier ({@link Boardwalk}), out from the boardwalk at the plaza: its deck
 * on rows of piles, the white rail and the lamps along its edges; the Ferris wheel and the
 * carousel at its root, the swing ride and the row of game booths past them, the Nor'easter (the
 * wooden coaster) filling the far end with its station, its lift hill and the drop back; then
 * the narrow fishing pier on out to the bait shop on its T (boardwalk-amusements 01-03, beach-pier
 * 01-04).
 *
 * <p>Drawn in the front's frame, the deck at y = 0; the coaster's track and bents are members in
 * the plane of their run (x, y), like the wheel's (waterfront.py, boardwalk.py).
 */
final class BoardwalkPier {
    private final Boardwalk.Front c;
    private static final int X0 = Boardwalk.BW1 + 1, X1 = Boardwalk.PX1, Z0 = Boardwalk.PZ0, Z1 = Boardwalk.PZ1;
    /** The wheel: its hub at (WX, WY) in the planes WZ +- 1, its rim's radius. */
    private static final int WX = 100, WY = 15, WZ = -17, WR = 12;
    /** The carousel's middle; the swing ride's. */
    private static final int CX = 100, CZ = 15, SX = 140, SZ = -12;
    /** The coaster: its two runs' planes (out and back), the ends where it turns, the station. */
    private static final int OUT = -22, BACK = -6, TW = 166, TE = 244, ST0 = 168, ST1 = 186;
    /** The track's height per x in each run, once laid (Integer.MIN_VALUE where there is none). */
    private final int[] outY = new int[400], backY = new int[400];

    BoardwalkPier(Boardwalk.Front c) {
        this.c = c;
        java.util.Arrays.fill(outY, Integer.MIN_VALUE);
        java.util.Arrays.fill(backY, Integer.MIN_VALUE);
    }

    private void set(int x, int y, int z, String b) {
        c.set(x, y, z, b);
    }

    void build() {
        deck();
        wheel();
        carousel();
        swings();
        booths();
        coaster();
        fishing();
        story();
    }

    // ------------------------------------------------------------------ the deck

    private void deck() {
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                set(x, 0, z, c.chance() < 0.025 ? "wf_deck_b" : c.chance() < 0.004 ? "air" : "sb_deck_z");
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, "air");
                }
            }
            set(x, 1, Z0, "sb_rail[facing=north]");
            set(x, 1, Z1, "sb_rail[facing=south]");
            if (Math.floorMod(x, 12) == 0) {
                c.lampPost(x, Z0 + 1);
                c.lampPost(x, Z1 - 1);
            }
        }
        for (int z = Z0; z <= Z1; z++) {
            if (z < -6 || z > 6) {
                set(X1, 1, z, "sb_rail[facing=east]");
            }
        }
        c.piles(X0 + 2, Z0, X1, Z1, 6);
    }

    // ------------------------------------------------------------------ the wheel

    /**
     * The Ferris wheel, its face along the pier: two rims of lit members on sixteen spokes, the hub
     * on its axle between the A-frame legs, the gondolas hung between the rims; a fence round its
     * foot (the wf_ kit, as on Pier 9).
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
        for (int z = WZ - 3; z <= WZ + 3; z++) {
            if (Math.abs(z - WZ) != 1) {
                set(WX, WY, z, Math.abs(z - WZ) == 3 ? "wf_truss" : "wf_steel");
            }
        }
        for (int z : new int[] {WZ - 3, WZ + 3}) {
            for (int side = -1; side <= 1; side += 2) {
                int fx = WX + side * 6;
                set(fx, 0, z, "wf_cap");
                line(z, fx, 1, WX, WY - 1, "wf_leg_", true);
            }
            line(z, WX - 3, 7, WX + 3, 7, "wf_leg_", false);
        }
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI / 8;
            int x = WX + (int) Math.round(Math.cos(a) * WR), y = WY + (int) Math.round(Math.sin(a) * WR);
            if (c.chance() < 0.1) {
                continue;
            }
            set(x, y, WZ, "wf_gondola_" + (i % 2 == 0 ? "a" : "b"));
        }
        for (int x = WX - 9; x <= WX + 9; x++) {
            set(x, 1, WZ - 5, "wf_rail[facing=north]");
            set(x, 1, WZ + 5, x == WX ? "air" : "wf_rail[facing=south]");
        }
        for (int z = WZ - 5; z <= WZ + 5; z++) {
            set(WX - 10, 1, z, "wf_rail[facing=west]");
            set(WX + 10, 1, z, "wf_rail[facing=east]");
        }
        c.sign("ride", WX + 2, 1, WZ + 6, "south");
    }

    // ------------------------------------------------------------------ the carousel

    private void carousel() {
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                double r = Math.hypot(dx, dz);
                int x = CX + dx, z = CZ + dz;
                if (r <= 5.5) {
                    set(x, 0, z, "wf_ride_floor");
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
            for (int y = 1; y <= 4; y++) {
                set(x, y, z, "wf_brass");
            }
        }
        set(CX, 9, CZ, "wf_crest");
    }

    // ------------------------------------------------------------------ the swing ride

    /**
     * The swing ride: its red tower on the round platform, the domed crown over it, twelve seats
     * hung on their chains round the crown's edge; a fence round it.
     */
    private void swings() {
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double r = Math.hypot(dx, dz);
                int x = SX + dx, z = SZ + dz;
                if (r <= 3.5) {
                    set(x, 0, z, "wf_ride_floor");
                }
                if (r > 8.6 && r <= 9.6) {
                    set(x, 1, z, Math.abs(dx) >= Math.abs(dz) ? "railing[facing=" + (dx > 0 ? "east" : "west") + "]"
                            : "railing[facing=" + (dz > 0 ? "south" : "north") + "]");
                }
            }
        }
        // the tower a red drum, the crown over it a low red cone the chains hang from
        Round.wall(c.p, SX + 0.5, SZ + 0.5, 1.3, 1, 10, "lhr", 0);
        int crown = Round.roof(c.p, SX + 0.5, SZ + 0.5, 7.6, "smm", 11, "tinred");
        set(SX, crown, SZ, "wf_crest");
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            int x = SX + (int) Math.round(Math.cos(a) * 6.6), z = SZ + (int) Math.round(Math.sin(a) * 6.6);
            double tx = -Math.sin(a), tz = Math.cos(a);
            String facing = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? "east" : "west") : (tz > 0 ? "south" : "north");
            for (int y = 5; y <= 10; y++) {
                set(x, y, z, "sb_chain[facing=" + facing + "]");
            }
            set(x, 4, z, c.chance() < 0.12 ? "air" : "sb_swing[facing=" + facing + "]");
        }
    }

    // ------------------------------------------------------------------ the game booths

    /** A row of booths along the south rail: counter to the walk, the prize walls, the striped canopies. */
    private void booths() {
        String[] canvas = {"sb_stripe_a", "sb_stripe_b", "sb_stripe_c"};
        int i = 0;
        for (int x0 = 122; x0 + 5 <= 162; x0 += 6, i++) {
            int z0 = 19, z1 = Z1 - 2;
            for (int x = x0; x <= x0 + 5; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = x == x0 || x == x0 + 5;
                    if (side && (z == z0 || z == z1)) {
                        for (int y = 1; y <= 3; y++) {
                            set(x, y, z, "wf_trim");
                        }
                    } else if (side) {
                        set(x, 1, z, "sb_white");
                        set(x, 2, z, "sb_prize[facing=" + (x == x0 ? "east" : "west") + "]");
                    }
                    set(x, 4, z, canvas[i % 3]);
                }
                if (x > x0 && x < x0 + 5) {
                    set(x, 1, z0, "store_counter[facing=north]");
                    set(x, 1, z1, "sb_prize[facing=north]");
                    set(x, 2, z1, "sb_prize[facing=north]");
                }
            }
            set(x0 + 1, 5, z0, "wf_trim");
            if (i % 2 == 0) {
                c.neon("games", 3, 1, x0 + 1, 5, z0 - 1, "north");
            }
        }
    }

    // ------------------------------------------------------------------ the coaster

    /**
     * The Nor'easter: out along the north of the pier's end from the station, up the lift hill,
     * round the turn at the top and back down the first drop, over the camelback and home round
     * the low turn; the white bents under every fourth foot of it braced across in X, the train
     * in the station, the sign over its door.
     */
    private void coaster() {
        // the station's platform either side of the track, the roof over it on posts
        for (int x = ST0 - 1; x <= ST1; x++) {
            for (int z = OUT - 4; z <= OUT + 4; z++) {
                if (z == OUT) {
                    continue;
                }
                set(x, 1, z, "sb_white");
                set(x, 2, z, x == ST0 - 1 ? "air" : "sb_deck");
                if (x == ST0 - 1) {
                    set(x, 1, z, "ext_stairs_wood[facing=east]");
                }
                set(x, 8, z, x % 2 == 0 ? "sb_stripe_a" : "sb_red");
            }
            for (int z : new int[] {OUT - 4, OUT + 4}) {
                if (Math.floorMod(x - ST0, 6) == 0) {
                    for (int y = 3; y <= 7; y++) {
                        set(x, y, z, "wf_trim");
                    }
                } else {
                    set(x, 3, z, "railing[facing=" + (z < OUT ? "north" : "south") + "]");
                }
            }
        }
        c.neon("noreaster", 6, 1, ST0 - 2, 8, OUT + 3, "west");
        c.sign("ride", ST0 - 2, 3, OUT + 5, "west");
        // the outbound run
        lay(OUT, TW, 2, ST1, 2, outY);
        lay(OUT, ST1, 2, ST1 + 53, 24, outY);
        lay(OUT, ST1 + 53, 24, TE, 24, outY);
        // the back run
        lay(BACK, TE, 24, TE - 4, 24, backY);
        lay(BACK, TE - 4, 24, TE - 26, 2, backY);
        lay(BACK, TE - 26, 2, TE - 50, 12, backY);
        lay(BACK, TE - 50, 12, TW + 4, 2, backY);
        lay(BACK, TW + 4, 2, TW, 2, backY);
        // the turns across between the runs
        for (int z = OUT; z <= BACK; z++) {
            set(TE, 24, z, "sb_track_z");
            set(TW, 2, z, "sb_track_z");
        }
        bents(OUT, outY);
        bents(BACK, backY);
        for (int z = OUT; z <= BACK; z += 4) {
            bentsAcross(TE, 24, z);
        }
        // the train in the station, its front car east
        for (int x = ST1 - 8; x <= ST1 - 5; x++) {
            set(x, 3, OUT, (x == ST1 - 5 ? "sb_car_front" : "sb_car") + "[facing=east]");
        }
        // the fence round the ride's foot
        for (int x = TW - 2; x <= TE + 2; x++) {
            if (x > ST1) {
                set(x, 1, OUT - 4, "railing[facing=north]");
            }
            set(x, 1, BACK + 3, x > ST0 && x < ST1 ? "air" : "railing[facing=south]");
        }
        for (int z = OUT - 4; z <= BACK + 3; z++) {
            set(TE + 3, 1, z, "railing[facing=east]");
        }
    }

    /** Track from (x0, y0) to (x1, y1) in the run's plane z, its height kept per x. */
    private void lay(int z, int x0, int y0, int x1, int y1, int[] ys) {
        line(z, x0, y0, x1, y1, "sb_track_", true);
        int n = Math.abs(x1 - x0);
        for (int i = 0; i <= n; i++) {
            int x = x0 + Integer.signum(x1 - x0) * i;
            int y = (int) Math.round(y0 + (y1 - y0) * (double) i / Math.max(1, n));
            if (x >= 0 && x < ys.length) {
                ys[x] = Math.max(ys[x], y);
            }
        }
    }

    /**
     * Under a run: a bent every fourth x, its two legs either side of the track up to a cap beam
     * across under it, the bays between braced in X and tied every eight up.
     */
    private void bents(int z, int[] ys) {
        for (int x = TW; x <= TE; x += 4) {
            int top = ys[x] - 1;
            if (top < 1) {
                continue;
            }
            for (int zz : new int[] {z - 1, z + 1}) {
                for (int y = 1; y <= top; y++) {
                    set(x, y, zz, "sb_bent_4");
                }
                set(x, 0, zz, "wf_cap");
            }
            for (int zz = z - 1; zz <= z + 1; zz++) {
                set(x, top, zz, "sb_beam[axis=z]");
            }
            int next = x + 4 <= TE ? ys[x + 4] - 1 : -1;
            int h = Math.min(top, next);
            for (int zz : new int[] {z - 1, z + 1}) {
                for (int y = 1; y + 4 <= h; y += 4) {
                    line(zz, x, y, x + 4, y + 4, "sb_bent_", false);
                    line(zz, x, y + 4, x + 4, y, "sb_bent_", false);
                }
                for (int y = 8; y < h; y += 8) {
                    line(zz, x, y, x + 4, y, "sb_bent_", false);
                }
            }
        }
    }

    /** At the high turn: a pair of legs under the track across at z, capped. */
    private void bentsAcross(int x, int ytrack, int z) {
        for (int xx : new int[] {x - 1, x + 1}) {
            for (int y = 1; y < ytrack; y++) {
                if (c.empty(xx, y, z)) {
                    set(xx, y, z, "sb_bent_4");
                }
            }
        }
    }

    // ------------------------------------------------------------------ the fishing pier

    /** The fishing pier on out past the rides, its rail and benches, the bait shop on the T at its end. */
    private void fishing() {
        int fz0 = -5, fz1 = 5, tx0 = Boardwalk.FPX1 - 16, tz0 = -16, tz1 = 16;
        for (int x = X1 + 1; x <= Boardwalk.FPX1; x++) {
            boolean t = x >= tx0;
            int z0 = t ? tz0 : fz0, z1 = t ? tz1 : fz1;
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, c.chance() < 0.05 ? "wf_deck_b" : c.chance() < 0.005 ? "air" : "sb_deck_z");
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, "air");
                }
            }
            if (!t || x == tx0) {
                set(x, 1, t ? tz0 : fz0, "sb_rail[facing=north]");
                set(x, 1, t ? tz1 : fz1, "sb_rail[facing=south]");
            }
            if (t) {
                set(x, 1, tz0, "sb_rail[facing=north]");
                set(x, 1, tz1, "sb_rail[facing=south]");
            }
            if (Math.floorMod(x, 16) == 0 && !t) {
                c.lampPost(x, fz0 + 1);
                c.set(x + 3, 1, fz1 - 1, "st_bench[facing=north]");
                c.set(x + 4, 1, fz1 - 1, "st_bench[facing=north]");
            } else if (!t && c.chance() < 0.04) {
                // what the fishermen left: a cooler, a bucket of bait gone bad, a line still over the rail
                c.set(x, 1, c.chance() < 0.5 ? fz0 + 1 : fz1 - 1, c.any("sb_cooler[facing=" + c.dir() + "]", "sc_bucket", "crate", "pk_trash_bag"));
            }
        }
        for (int z = tz0; z <= tz1; z++) {
            set(Boardwalk.FPX1, 1, z, "sb_rail[facing=east]");
        }
        c.piles(X1 + 3, fz0, tx0 - 1, fz1, 5);
        c.piles(tx0, tz0, Boardwalk.FPX1, tz1, 5);
        // the bait shop
        int bx0 = tx0 + 3, bx1 = Boardwalk.FPX1 - 3, bz0 = -6, bz1 = 6;
        for (int x = bx0; x <= bx1; x++) {
            for (int z = bz0; z <= bz1; z++) {
                boolean edge = x == bx0 || x == bx1 || z == bz0 || z == bz1;
                for (int y = 1; y <= 4; y++) {
                    set(x, y, z, edge ? "wf_shingle_b" : "air");
                }
                set(x, 5, z, edge ? "wf_trim" : "un_roof");
            }
        }
        c.door(bx0, 0, 0, "shop_door", "west");
        set(bx0, 2, -3, "wf_window[facing=west]");
        set(bx0, 2, 3, "wf_window[facing=west]");
        c.sign("bait", bx0 - 1, 4, -1, "west");
        // the Hollis ferry's bell at the T's south-east corner, the board over it on the rail
        set(Boardwalk.BELL[0], Boardwalk.BELL[1], Boardwalk.BELL[2], "zomboidcraft:ferry_bell[facing=west]");
        set(Boardwalk.FPX1 - 1, 1, 15, "pn_post");
        set(Boardwalk.FPX1 - 2, 1, 15, "pn_post");
        c.sign("ferry", Boardwalk.FPX1 - 2, 2, 15, "north");
        for (int z = bz0 + 1; z < bz1; z++) {
            set(bx1 - 1, 1, z, "store_shelf_hardware[facing=west]");
        }
        set(bx0 + 3, 1, -2, "store_counter[facing=west]");
        set(bx0 + 3, 1, -1, "store_register[facing=west]");
        set(bx0 + 3, 1, 2, "store_cooler[facing=west]");
    }

    // ------------------------------------------------------------------ what happened

    /** The pier the night they shut it: a car off the coaster's track, the dead at the booths, the litter. */
    private void story() {
        set(TE - 30, 1, BACK + 2, "sb_car[facing=" + c.dir() + "]");
        set(TE - 31, 1, BACK + 2, "home_blood_pool");
        set(TE - 29, 1, BACK + 1, "home_corpse_head[facing=west]");
        c.mess(X0, Z0 + 1, X1, Z1 - 1, 0, 0.03);
        for (int x = 122; x <= 160; x += 3) {
            if (c.chance() < 0.3) {
                set(x, 1, 17, c.any("home_corpse_head[facing=" + c.dir() + "]", "blood_splat", "pk_trash_bag"));
            }
        }
    }

    // ------------------------------------------------------------------ members

    /**
     * A straight member from (x0, y0) to (x1, y1) in the plane z, a cell per step along its long
     * way, each turned to its direction and set off its cell's centre to stay on the true line;
     * over what is already there only if over (WaterfrontPier's wheel, the same way).
     */
    private void line(int z, double x0, double y0, double x1, double y1, String prefix, boolean over) {
        double dx = x1 - x0, dy = y1 - y0;
        int k = Math.floorMod((int) Math.round(Math.atan2(dy, dx) / (Math.PI / 8)), 8);
        double n = k * Math.PI / 8 + Math.PI / 2, nx = Math.cos(n), ny = Math.sin(n);
        int steps = (int) Math.ceil(Math.max(Math.abs(dx), Math.abs(dy)));
        for (int i = 0; i <= steps; i++) {
            double px = x0 + dx * i / Math.max(1, steps), py = y0 + dy * i / Math.max(1, steps);
            int cx = (int) Math.round(px), cy = (int) Math.round(py);
            if (!over && !c.empty(cx, cy, z)) {
                continue;
            }
            double o = ((px - cx) * nx + (py - cy) * ny) * 16;
            set(cx, cy, z, prefix + k + (o > 3 ? "_p" : o < -3 ? "_m" : ""));
        }
    }

    /** A ring of members radius r round the wheel's hub in the plane z, each turned along the ring. */
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
            int k = Math.floorMod((int) Math.round((Math.atan2(dy, dx) + Math.PI / 2) / (Math.PI / 8)), 8);
            double n = k * Math.PI / 8 + Math.PI / 2;
            double o = (r - dist) * (dx * Math.cos(n) + dy * Math.sin(n)) / dist * 16;
            set(WX + dx, WY + dy, z, prefix + k + (o > 3 ? "_p" : o < -3 ? "_m" : ""));
        }
    }
}
