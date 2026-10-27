package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;

/**
 * A country depot behind the platform at one of the railway's bigger country stops (Layout puts
 * them at Pinecrest, Millbrook, Fairview, Kingsbridge and Seabright): the combination depot North
 * American lines built by the thousand, drawn from what is known of them (no photos of one were in
 * the library when it was made). One long storey of the railway's cream board and batten over
 * green lap siding, on a brick foundation, under a steep roof whose eaves stand out on brackets,
 * the platform canopy carried on from the front.
 *
 * <p>Three rooms in a row along the line: the waiting room round its stove, benches on every wall
 * and the ticket window into the agent's office; the office itself, its bay window out onto the
 * platform so the agent could see down the line both ways, the telegraph table in the bay and the
 * train-order signal on its mast in front; the freight room, sliding doors onto the platform and
 * onto the dock at the back where the wagons pulled up, a scale, crates, trunks, milk cans and mail
 * nobody came for. The yard behind: a gravel drive along the back from every way in, the section
 * crew's tool house, a car or two left where they stopped.
 *
 * <p>Drawn in its lot's frame: x along the platform (0 .. {@link #L} - 1, the stop's middle
 * halfway), z = 0 the strip of platform against the platform's back edge, z growing away from the
 * line; y = 0 the yard, a block under the platform, y = 1 the platform's and the floor's level.
 */
final class DepotSite extends Site {
    private static final int L = Layout.DEPOT_LEN, D = Layout.DEPOT_DEPTH;
    /** The building: x X0 .. X1, the front wall at ZF and the back at ZB; floor at y = 1, walls 2 .. 5, the ceiling at 6. */
    private static final int X0 = 4, X1 = 39, ZF = 4, ZB = 13, FLOOR = 1, CEIL = 6;
    /** The partitions: the waiting room up to P1, the agent's office between, the freight room past P2. */
    private static final int P1 = 17, P2 = 24;
    /** The bay: x BX - 1 .. BX + 1 out in front of the office; the doors. */
    private static final int BX = 20, WAIT_DOOR = 11, OFFICE_DOOR = 23, FREIGHT_FRONT = 29, FREIGHT_BACK = 30;
    /** The yard's drive runs along behind the depot here. */
    private static final int LANE_Z = 17;
    private static final String[] ROOFS = {"slate", "charcoal", "tin_green", "red"};

    private final County county;
    private Placer q;
    private Bldg b;
    private Kit kit;
    private int facing;
    /** The stop's name-board key (tools/city/railway.py DEPOTS), or null. */
    private String key;
    private String roof;

    DepotSite(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    @Override
    void build() {
        facing = p.waterSide;
        key = stopKey();
        roof = ROOFS[rng.nextInt(ROOFS.length)];
        q = lot(0, 0, L, D, facing);
        b = new Bldg(q, rng);
        b.damage = 0.3 + chance() * 0.4;
        b.boarded = 0.08 + chance() * 0.2;
        kit = new Kit(out, rng, w, d);
        yard();
        strip();
        shell();
        front();
        back();
        ends();
        waitingRoom();
        office();
        freightRoom();
        outside();
        toolHouse();
        kit.woods(0.3);
        kit.overgrow(0.9);
        kit.tidyPlants();
    }

    private void set(int x, int y, int z, String block) {
        q.set(x, y, z, block);
    }

    private String get(int x, int y, int z) {
        return q.get(x, y, z);
    }

    /** The place's own column for (lx, lz) of the lot's frame (as Placer.forLot turns it). */
    private int[] at(double lx, double lz) {
        return switch (facing & 3) {
            case 0 -> new int[] {(int) Math.floor(lx), (int) Math.floor(lz)};
            case 1 -> new int[] {(int) Math.floor(D - 1 - lz), (int) Math.floor(lx)};
            case 2 -> new int[] {(int) Math.floor(L - 1 - lx), (int) Math.floor(D - 1 - lz)};
            default -> new int[] {(int) Math.floor(lz), (int) Math.floor(L - 1 - lx)};
        };
    }

    /** Marks a box of the lot's frame used, so no tree or meadow grows over it. */
    private void claim(int lx0, int lz0, int lx1, int lz1, byte kind) {
        int[] a = at(lx0, lz0), c = at(lx1, lz1);
        kit.claim(a[0], a[1], c[0], c[1], kind);
    }

    /** The compass way of the lot's own north, east, south or west, turned as the lot is. */
    private static final String[] DIRS = Site.FACINGS;

    /** Which stop the depot is behind: the name-board key of the railway's stop nearest its middle. */
    private String stopKey() {
        if (county == null) {
            return null;
        }
        String best = null;
        double bestD = 60;
        for (Track t : county.tracks()) {
            if (t.kind != Track.Kind.RAIL) {
                continue;
            }
            for (Track.Stop st : t.stops) {
                int m = st.middle();
                double dd = Math.hypot(t.xs[m] - (p.x0 + p.x1) / 2.0, t.zs[m] - (p.z0 + p.z1) / 2.0);
                if (dd < bestD) {
                    bestD = dd;
                    best = RailGen.key(st);
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ the ground

    /** The gravel drive: in from each way a road can come (the back and both ends), along behind the depot. */
    private void yard() {
        int back = (facing + 2) & 3, endA = (facing + 1) & 3, endB = (facing + 3) & 3;
        double[] g = kit.beyond(back), ea = kit.beyond(endA), eb = kit.beyond(endB);
        int[] mid = at(L / 2.0, LANE_Z + 0.5), lo = at(5.5, LANE_Z + 0.5), hi = at(L - 6.5, LANE_Z + 0.5);
        kit.lane(Kit.Style.GRAVEL, g[0], g[1], mid[0] + 0.5, mid[1] + 0.5);
        // the ends' gates are halfway back, level with the building: the drive comes round its corners
        double[] a = end(ea), c = end(eb);
        double[] la = closer(a, lo, hi), lc = closer(c, lo, hi);
        kit.curve(Kit.Style.GRAVEL, ea[0], ea[1], a[0], a[1], la[0], la[1]);
        kit.curve(Kit.Style.GRAVEL, eb[0], eb[1], c[0], c[1], lc[0], lc[1]);
        kit.lane(Kit.Style.GRAVEL, lo[0] + 0.5, lo[1] + 0.5, hi[0] + 0.5, hi[1] + 0.5);
        kit.pave();
        // the apron behind the depot, gravel and cinders, the dock's wagons' ground
        for (int x = X0 - 2; x <= X1 + 2; x++) {
            for (int z = ZB + 1; z <= LANE_Z; z++) {
                int[] c0 = at(x, z);
                if (inside(c0[0], c0[1]) && kit.use(c0[0], c0[1]) == Kit.FREE) {
                    // in patches a couple of blocks across, not block by block
                    double r = (Noise.hash(p.seed, Math.floorDiv(x, 4), Math.floorDiv(z, 3)) & 0xFFFF) / 65536.0 * 0.85 + chance() * 0.15;
                    out.set(c0[0], 0, c0[1], r < 0.6 ? "minecraft:gravel" : r < 0.88 ? "rw_cinder" : "minecraft:coarse_dirt");
                    kit.claim(c0[0], c0[1], c0[0], c0[1], Kit.LOT);
                }
            }
        }
    }

    /** Where the drive from an end gate turns in: two in from the gate, toward the lot's back. */
    private double[] end(double[] beyond) {
        double cx = w / 2.0, cz = d / 2.0;
        double dx = cx - beyond[0], dz = cz - beyond[1], len = Math.max(1e-9, Math.hypot(dx, dz));
        double[] pt = {beyond[0] + dx / len * 5, beyond[1] + dz / len * 5};
        // and over toward the back, so it comes round behind the building
        int[] back = at(L / 2.0, D - 1);
        double bx = back[0] - cx, bz = back[1] - cz, bl = Math.max(1e-9, Math.hypot(bx, bz));
        return new double[] {pt[0] + bx / bl * 4, pt[1] + bz / bl * 4};
    }

    private static double[] closer(double[] from, int[] a, int[] c) {
        double da = Math.hypot(a[0] - from[0], a[1] - from[1]), dc = Math.hypot(c[0] - from[0], c[1] - from[1]);
        int[] pick = da < dc ? a : c;
        return new double[] {pick[0] + 0.5, pick[1] + 0.5};
    }

    /** The strip of platform against the platform's back, its canopy, the half steps down at its ends. */
    private void strip() {
        for (int x = 0; x < L; x++) {
            for (int z = 0; z < ZF; z++) {
                set(x, 0, z, "rw_platform_base");
                double r = chance();
                set(x, FLOOR, z, x == 0 || x == L - 1 ? "rw_platform_half" : r < 0.55 ? "rw_platform" : r < 0.8 ? "rw_platform_2" : "rw_platform_3");
                for (int y = FLOOR + 1; y <= CEIL + 3; y++) {
                    set(x, y, z, "air");
                }
                if (z < ZF - 1 && x >= 2 && x <= L - 3) {
                    set(x, CEIL, z, "rw_canopy");
                }
            }
        }
        claim(0, 0, L - 1, ZF - 1, Kit.BUILT);
    }

    // ------------------------------------------------------------------ the building

    /** A wall block facing out: lap siding on the bottom course, board and batten over it, the years on both. */
    private String wall(int y, String out, boolean back) {
        if (y == FLOOR + 1) {
            return (back && chance() < 0.35 ? "rw_dp_wall_low_moss" : "rw_dp_wall_low") + "[facing=" + out + "]";
        }
        return (chance() < (back ? 0.4 : 0.2) ? "rw_dp_wall_b" : "rw_dp_wall") + "[facing=" + out + "]";
    }

    private void shell() {
        claim(X0 - 1, ZF, X1 + 1, ZB + 3, Kit.BUILT);
        for (int x = X0; x <= X1; x++) {
            for (int z = ZF; z <= ZB; z++) {
                boolean edge = x == X0 || x == X1 || z == ZF || z == ZB;
                set(x, 0, z, "rw_platform_base");
                // the brick foundation shows a block over the yard round the back and ends
                set(x, FLOOR, z, edge ? b.wall("brick_red", 1) : "rw_dp_floor");
                for (int y = FLOOR + 1; y < CEIL; y++) {
                    if (edge) {
                        String out = z == ZF ? "north" : z == ZB ? "south" : x == X0 ? "west" : "east";
                        set(x, y, z, wall(y, out, z == ZB || x == X0 || x == X1));
                    } else {
                        set(x, y, z, "air");
                    }
                }
                if (!edge) {
                    // the beadboard ceiling, a board down here and there
                    set(x, CEIL, z, chance() < b.damage * 0.06 ? "air" : "rw_dp_ceiling");
                }
            }
        }
        // the partitions: beadboard to the chair rail, painted boards over
        for (int px : new int[] {P1, P2}) {
            for (int z = ZF + 1; z < ZB; z++) {
                for (int y = FLOOR + 1; y < CEIL; y++) {
                    set(px, y, z, y == FLOOR + 1 ? "rw_dp_inner_wall_low" : "rw_dp_inner_wall");
                }
            }
        }
        b.door(P1, FLOOR + 1, ZB - 2, "home_door_oak", "west");
        b.door(P2, FLOOR + 1, ZF + 2, "home_door_oak", "east");
        // the roof, its eaves out over the wall on every side; the gable ends in board and batten
        b.roofGable(X0, X1, ZF, ZB, CEIL, roof, false, "rw_dp_wall", 1);
        // the brackets under the eaves, front and back
        for (int x : new int[] {X0 + 1, 9, 14, 17, 26, 34, X1 - 1}) {
            set(x, CEIL - 1, ZF - 1, "rw_dp_bracket[facing=north]");
            set(x, CEIL - 1, ZB + 1, "rw_dp_bracket[facing=south]");
        }
        // the waiting room's stove pipe up into its brick chimney, out through the roof's back slope
        int sx = 10, sz = ZB - 3;
        for (int y = CEIL; y <= CEIL + 6; y++) {
            set(sx, y, sz, b.wall("brick_red", y));
        }
        set(sx, CEIL + 7, sz, "ext_flue_cap");
    }

    /** A window two blocks tall in a wall facing out: whole, broken, empty or boarded. */
    private void window(int x, int z, String out) {
        String state;
        double r = chance();
        if (r < b.boarded) {
            state = "boarded";
        } else if ((r = chance()) < b.damage * 0.5) {
            state = r < b.damage * 0.3 ? "broken" : "empty";
        } else {
            state = "glass";
        }
        set(x, FLOOR + 2, z, "rw_dp_win_" + state + "_lo[facing=" + out + "]");
        set(x, FLOOR + 3, z, "rw_dp_win_" + (state.equals("broken") && chance() < 0.5 ? "glass" : state) + "_hi[facing=" + out + "]");
    }

    /** A door into a wall facing out, a glazed transom over it, steps down to the yard where it opens onto one. */
    private void door(int x, int z, String out, boolean steps) {
        b.door(x, FLOOR + 1, z, "ext_door_green", out);
        set(x, FLOOR + 3, z, (chance() < b.damage * 0.4 ? "rw_dp_win_broken_hi" : "rw_dp_win_glass_hi") + "[facing=" + out + "]");
        if (steps) {
            int[] o = Bldg.step(out);
            set(x + o[0], FLOOR, z + o[1], "rw_stair[facing=" + Bldg.opposite(out) + "]");
            set(x + o[0], FLOOR + 1, z + o[1], "air");
            set(x + o[0], FLOOR + 2, z + o[1], "air");
        }
    }

    /**
     * The sliding freight door: an opening two wide and three high in a wall along x facing out
     * (north or south), the door hung in front on its rail, shut over the opening or run aside.
     */
    private void freightDoor(int x, int z, String out) {
        int o = out.equals("north") ? -1 : 1;
        boolean open = chance() < 0.45;
        int at = open ? x + 2 : x;
        for (int y = FLOOR + 1; y <= FLOOR + 3; y++) {
            set(x, y, z, "air");
            set(x + 1, y, z, "air");
            for (int k = 0; k < 2; k++) {
                // tile 0 the viewer's left: facing north the left is the high x, facing south the low
                int col = out.equals("north") ? 1 - k : k, row = FLOOR + 3 - y;
                set(at + k, y, z + o, "rw_dp_freight_" + (row * 2 + col) + "[facing=" + out + "]");
            }
        }
        for (int k = 0; k < 4; k++) {
            set(x + k, FLOOR + 4, z + o, "rw_dp_door_rail[facing=" + out + "]");
        }
    }

    private void front() {
        for (int x : new int[] {6, 8, 14, 16, 35}) {
            window(x, ZF, "north");
        }
        door(WAIT_DOOR, ZF, "north", false);
        door(OFFICE_DOOR, ZF, "north", false);
        freightDoor(FREIGHT_FRONT, ZF, "north");
        // the bay: the office's front wall opened into it, windows on its three sides
        for (int x = BX - 1; x <= BX + 1; x++) {
            set(x, FLOOR, ZF - 1, "rw_dp_floor");
            set(x, FLOOR + 1, ZF - 1, "rw_dp_wall_low[facing=" + (x == BX ? "north" : x < BX ? "west" : "east") + "]");
            set(x, FLOOR + 4, ZF - 1, "rw_dp_wall[facing=north]");
            for (int y = FLOOR + 1; y < CEIL; y++) {
                set(x, y, ZF, "air");
            }
            set(x, FLOOR, ZF, "rw_dp_floor");
        }
        window(BX - 1, ZF - 1, "west");
        window(BX, ZF - 1, "north");
        window(BX + 1, ZF - 1, "east");
        // the name over the waiting room's door
        board(WAIT_DOOR - 1, FLOOR + 4, ZF - 1, "north");
    }

    private void back() {
        for (int x : new int[] {7, 15, 21, 36}) {
            window(x, ZB, "south");
        }
        door(WAIT_DOOR, ZB, "south", true);
        // the freight dock along the freight room's back, its steps down at the end
        for (int x = P2; x <= X1; x++) {
            for (int z = ZB + 1; z <= ZB + 3; z++) {
                set(x, FLOOR, z, x == P2 && z > ZB + 1 ? "rw_stair[facing=east]" : "rw_dp_floor");
                set(x, 0, z, "rw_platform_base");
            }
        }
        freightDoor(FREIGHT_BACK, ZB, "south");
    }

    private void ends() {
        for (int z : new int[] {ZF + 2, ZB - 2}) {
            window(X0, z, "west");
        }
        window(X1, ZF + 4, "east");
        // the stop's name in the gable at each end, for the trains coming in
        board(X0 - 1, CEIL + 1, ZF + 3, "west");
        board(X1 + 1, CEIL + 1, ZF + 3, "east");
    }

    /**
     * The stop's name board, four long, on a wall: along x on a front or back (facing north or
     * south), along z on an end, from (x, z) up the axis; tile 0 at the viewer's left.
     */
    private void board(int x, int y, int z, String facingOut) {
        if (key == null) {
            return;
        }
        boolean alongX = facingOut.equals("north") || facingOut.equals("south");
        for (int k = 0; k < 4; k++) {
            // facing north the viewer's left is the high x; south the low x; west the low z; east the high z
            int tile = switch (facingOut) {
                case "north", "east" -> 3 - k;
                default -> k;
            };
            set(alongX ? x + k : x, y, alongX ? z : z + k, "rw_dp_name_" + key + "_" + tile + "[facing=" + facingOut + "]");
        }
    }

    // ------------------------------------------------------------------ the rooms

    private void waitingRoom() {
        int y = FLOOR + 1;
        // benches round the walls, a pair back to back down the middle
        for (int x = X0 + 2; x <= P1 - 3; x++) {
            if (x != WAIT_DOOR && x != WAIT_DOOR - 1 && x != WAIT_DOOR + 1) {
                set(x, y, ZB - 1, chance() < 0.12 ? "air" : "rw_bench[facing=north]");
                set(x, y, ZF + 1, chance() < 0.12 ? "air" : "rw_bench[facing=south]");
            }
        }
        for (int z = ZF + 3; z <= ZB - 3; z++) {
            set(X0 + 1, y, z, "rw_bench[facing=east]");
        }
        // the stove under its pipe
        set(10, y, ZB - 3, "home_wood_stove[facing=north]");
        for (int yy = y + 1; yy < CEIL; yy++) {
            set(10, yy, ZB - 3, "home_stove_pipe[facing=north]");
        }
        set(9, y, ZB - 2, "barrel");
        // the ticket window in the office's wall, the clock over it, the notices
        set(P1, y + 1, ZF + 4, "rw_dp_ticket[facing=west]");
        set(P1 - 1, CEIL - 1, ZF + 4, "bk_wall_clock[facing=west]");
        set(P1 - 1, y + 1, ZF + 2, "us_notice_" + rng.nextInt(3) + "[facing=west]");
        set(X0 + 1, y + 2, ZF + 4, "us_notice_" + rng.nextInt(3) + "[facing=east]");
        set(13, CEIL - 1, ZF + 4, "rw_lamp_hang");
        set(7, CEIL - 1, ZF + 4, "rw_lamp_hang");
        // what people left: a trunk, a mail sack, cases, the mess
        set(P1 - 2, y, ZB - 1, "rw_dp_trunk[facing=west]");
        set(P1 - 1, y, ZB - 3, chance() < 0.5 ? "trade_luggage_cart[facing=west]" : "cardboard_box[facing=north]");
        b.mess(X0 + 1, ZF + 1, P1 - 1, ZB - 1, y, 0.06);
    }

    private void office() {
        int y = FLOOR + 1;
        // the telegraph table in the bay, the agent's chair behind it
        set(BX, y, ZF, "rw_dp_desk[facing=south]");
        set(BX, y, ZF + 1, chance() < 0.4 ? "office_chair_fallen[facing=south]" : "office_chair[facing=north]");
        // the safe, the files, the typewriter's desk, the counter behind the ticket window
        set(P1 + 1, y, ZB - 1, "store_safe[facing=east]");
        set(P2 - 1, y, ZB - 1, "filing_cabinet[facing=west]");
        set(P2 - 1, y, ZB - 2, "filing_cabinet[facing=west]");
        set(P2 - 1, y, ZF + 4, "home_desk[facing=west]");
        set(P2 - 1, y + 1, ZF + 4, "sh_typewriter[facing=west]");
        set(P1 + 1, y, ZF + 4, "trade_check_desk[facing=east]");
        set(P2 - 1, y + 2, ZB - 3, "sh_map_" + rng.nextInt(4) + "[facing=west]");
        set(P1 + 1, CEIL - 1, ZF + 6, "bk_wall_clock[facing=east]");
        set(BX, CEIL - 1, ZF + 4, "rw_lamp_hang");
        b.mess(P1 + 1, ZF + 1, P2 - 1, ZB - 1, y, 0.1);
    }

    private void freightRoom() {
        int y = FLOOR + 1;
        // the scale by the platform door, wagons, the freight nobody came for
        set(FREIGHT_FRONT - 2, y, ZF + 2, "rw_dp_scale[facing=east]");
        set(FREIGHT_FRONT + 1, y, ZF + 3, "rw_dp_wagon[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
        for (int x = P2 + 1; x <= X1 - 1; x++) {
            for (int z = ZF + 1; z <= ZB - 1; z++) {
                boolean aisle = x >= FREIGHT_FRONT - 2 && x <= FREIGHT_BACK + 3 || z >= ZF + 4 && z <= ZF + 5 || x == P2 + 1 && z == ZF + 2;
                if (aisle || get(x, y, z) != null && !"air".equals(get(x, y, z))) {
                    continue;
                }
                double r = chance();
                if (r < 0.2) {
                    set(x, y, z, "crate");
                    if (chance() < 0.3) {
                        set(x, y + 1, z, "crate");
                    }
                } else if (r < 0.32) {
                    set(x, y, z, "pk_barrel_" + (1 + rng.nextInt(3)) + "[facing=" + DIRS[rng.nextInt(4)] + "]");
                } else if (r < 0.42) {
                    set(x, y, z, "rw_dp_milk_can");
                } else if (r < 0.5) {
                    set(x, y, z, "rw_dp_trunk[facing=" + DIRS[rng.nextInt(4)] + "]");
                } else if (r < 0.58) {
                    set(x, y, z, "cv_mail_sack[facing=" + DIRS[rng.nextInt(4)] + "]");
                } else if (r < 0.66) {
                    set(x, y, z, "cardboard_box[facing=" + DIRS[rng.nextInt(4)] + "]");
                }
            }
        }
        set(33, CEIL - 1, ZF + 4, "rw_lamp_hang");
        b.mess(P2 + 1, ZF + 1, X1 - 1, ZB - 1, y, 0.04);
    }

    // ------------------------------------------------------------------ outside

    private void outside() {
        int y = FLOOR + 1;
        // under the canopy: benches between the windows, the bulletin, the payphone, lamps, wagons
        for (int x : new int[] {7, 15}) {
            set(x, y, ZF - 1, "rw_bench[facing=north]");
        }
        set(WAIT_DOOR + 1, y + 1, ZF - 1, "rw_dp_bulletin[facing=north]");
        set(WAIT_DOOR - 2, y + 1, ZF - 1, "gs_payphone[facing=north]");
        set(OFFICE_DOOR + 1, y + 1, ZF - 1, "zomboidcraft:train_call_button[facing=north]");
        for (int x : new int[] {5, 26, 38}) {
            set(x, CEIL - 2, ZF - 1, "rw_dp_gooseneck[facing=north]");
        }
        for (int x : new int[] {6, 14, 30, 37}) {
            set(x, CEIL - 1, 1, "rw_lamp_hang");
        }
        set(33, y, 1, "rw_dp_wagon[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
        if (chance() < 0.6) {
            set(2, y, 2, "rw_dp_wagon[facing=north]");
        }
        for (int k = 0; k < 3 + rng.nextInt(4); k++) {
            int x = FREIGHT_FRONT + 2 + rng.nextInt(4), z = 1 + rng.nextInt(2);
            if (get(x, y, z) == null || get(x, y, z).equals("air")) {
                set(x, y, z, "rw_dp_milk_can");
            }
        }
        // the train-order signal on its mast before the bay, through the canopy, both blades at stop
        for (int yy = y; yy <= CEIL + 3; yy++) {
            set(BX, yy, 1, "rw_dp_mast");
        }
        set(BX, CEIL + 4, 1, "rw_dp_semaphore[facing=north]");
        // a lamp at each gable end and one by the back door
        set(X0 - 1, CEIL - 2, ZF + 7, "rw_dp_gooseneck[facing=west]");
        set(X1 + 1, CEIL - 2, ZF + 7, "rw_dp_gooseneck[facing=east]");
        set(WAIT_DOOR + 1, CEIL - 2, ZB + 1, "rw_dp_gooseneck[facing=south]");
        // weeds through the strip's cracks, ivy up the back and the ends, a tag on the freight room
        for (int x = 0; x < L; x++) {
            for (int z = 0; z < ZF; z++) {
                if (chance() < 0.05 && get(x, y, z) == null) {
                    set(x, y, z, chance() < 0.6 ? "weeds" : "minecraft:short_grass");
                }
            }
        }
        for (int i = 0; i < 4; i++) {
            int x = X0 + 1 + rng.nextInt(P2 - X0 - 1), h = 2 + rng.nextInt(3);
            if (get(x, FLOOR + 1, ZB + 1) == null) {
                for (int yy = FLOOR + 1; yy < FLOOR + 1 + h; yy++) {
                    set(x, yy, ZB + 1, "vines[facing=north]");
                }
            }
        }
        for (int z = ZF + 1; z < ZB; z += 1 + rng.nextInt(4)) {
            if (chance() < 0.5) {
                for (int yy = FLOOR + 1; yy < FLOOR + 2 + rng.nextInt(3); yy++) {
                    set(X0 - 1, yy, z, "vines[facing=east]");
                }
            }
        }
        if (chance() < 0.7) {
            set(X1 - 5, FLOOR + 2, ZB + 1, "graffiti_" + rng.nextInt(8) + "[facing=north]");
        }
        // a car left behind the depot
        if (chance() < 0.6) {
            int[] c = at(12.5, LANE_Z + 0.5);
            int[] e = at(13.5, LANE_Z + 0.5);
            kit.car(c[0] + 0.5, c[1] + 0.5, Kit.yaw(e[0] - c[0], e[1] - c[1]), 4.5, 5.5);
        }
    }

    /** The section crew's tool house across the drive: corrugated iron, a tin roof, its tools still in it. */
    private void toolHouse() {
        int x0 = 31, x1 = 37, z0 = LANE_Z + 3, z1 = D - 1;
        if (z1 - z0 < 2) {
            return;
        }
        claim(x0 - 1, z0 - 1, x1 + 1, z1 + 1, Kit.BUILT);
        String mat = chance() < 0.5 ? "corrugated_rust" : "corrugated_grey";
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                set(x, 0, z, "floor_concrete");
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                for (int y = 1; y <= 3; y++) {
                    set(x, y, z, edge ? b.wall(mat, y) : "air");
                }
            }
        }
        b.roofGable(x0, x1, z0, z1, 4, "tin_galv", false, mat, 0);
        b.door(x0 + 2, 1, z0, "ext_door_oak", "north");
        b.window(x1 - 1, 2, z0, "north", 1, null);
        set(x0 + 1, 1, z1 - 1, "home_workbench[facing=north]");
        set(x1 - 1, 1, z1 - 1, "home_tool_chest[facing=north]");
        set(x1 - 1, 1, z0 + 1, "rw_dp_milk_can");
        set(x0 + 3, 1, z1 - 1, "crate");
    }
}
