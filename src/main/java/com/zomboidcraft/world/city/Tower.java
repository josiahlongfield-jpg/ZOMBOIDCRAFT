package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * A fully enterable office tower: a double-height lobby, stacked office floors around a core
 * holding one stair and one working lift, an optional setback terrace, and a crown.
 *
 * Local layout (front at z = 0): the facade is a ring one block thick; each floor is 4 blocks
 * (slab plus 3 of room). The 8 x 10 core sits in the middle of the footprint: the stairwell,
 * the lift beside it with a janitor's closet behind. The stair switches back inside its 3-wide
 * well, so every floor connects, up to a plant room on the roof; the rest is floor space.
 *
 * Styles change the skin, not the plan: a concrete grid, a glass curtain wall, stone piers in
 * the 1930s manner, or old brick with stone bands. Crowns: a radio mast, a stepped stone crown
 * with a spire, a glowing band, or a rooftop water tank.
 */
public final class Tower {
    public static final int FLOOR = 4;
    public static final int LOBBY = 8;
    public static final int GRID = 0, GLASS = 1, DECO = 2, BRICK = 3;
    /** The core's frame (the garages under a tower line up with it) and the slim core inside it. */
    private static final int CORE_W = 14, CORE_D = 10, SLIM_W = 8;

    private final Placer p;
    private final SplittableRandom rng;
    private final int w, d, floors;
    private final boolean furnished;
    private final boolean[] powered;
    private final int style, crown, setbackFloor;
    private final int cx0, cz0;  // core corner
    /** What the tower is for, and so each floor above the lobby ({@link Floors}). */
    private final Floors.Type[] program;
    private final Floors.Purpose purpose;

    /** furnished = false leaves the office floors empty (distant towers nobody will reach soon). */
    public Tower(Placer p, SplittableRandom rng, int w, int d, int floors, boolean furnished) {
        this(p, rng, w, d, floors, furnished, rng.nextInt(4), rng.nextInt(4));
    }

    public Tower(Placer p, SplittableRandom rng, int w, int d, int floors, boolean furnished, int style, int crown) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.floors = floors;
        this.furnished = furnished;
        this.style = style;
        this.crown = crown;
        this.cx0 = (w - CORE_W) / 2;
        this.cz0 = (d - CORE_D) / 2;
        // tall towers wide enough to keep the core step in by 4 blocks part way up
        boolean canStep = floors >= 16 && cx0 >= 6 && cz0 >= 6;
        this.setbackFloor = canStep && rng.nextDouble() < 0.7 ? floors / 2 + rng.nextInt(floors / 4) : Integer.MAX_VALUE;
        // a few floors still have power from a generator somewhere: their lights and windows glow
        this.powered = new boolean[floors + 2];
        for (int k = 1; k <= floors; k++) {
            powered[k] = rng.nextDouble() < 0.12;
        }
        this.purpose = Floors.officePurpose(rng, floors);
        this.program = Floors.program(purpose, floors, rng);
    }

    public boolean hotel() {
        return purpose == Floors.Purpose.HOTEL;
    }

    /** A floor that looks out: the hotel's sky club and penthouse have glass floor to ceiling. */
    private boolean scenic(int floor) {
        return floor >= 1 && floor <= floors && (program[floor] == Floors.Type.NIGHTCLUB || program[floor] == Floors.Type.PENTHOUSE);
    }

    /** The core's north-west corner, which everything below the lobby lines up with. */
    public int coreX() {
        return cx0;
    }

    public int coreZ() {
        return cz0;
    }

    public static int height(int floors) {
        return LOBBY + floors * FLOOR + 3;
    }

    public void build() {
        lobby();
        for (int k = 1; k <= floors; k++) {
            officeFloor(k, floorY(k));
        }
        int roofY = floorY(floors + 1);
        facade(roofY);
        core(roofY);
        roof(roofY);
    }

    private static int floorY(int k) {
        return k == 0 ? 0 : LOBBY + (k - 1) * FLOOR;
    }

    /** Which floor a height belongs to: 0 for the lobby. */
    private static int floorOf(int y) {
        return y < LOBBY ? 0 : (y - LOBBY) / FLOOR + 1;
    }

    private int inset(int floor) {
        return floor >= setbackFloor ? 4 : 0;
    }

    /** The slim core's west wall: one stair and one lift, centred in the frame. */
    public int coreX0() {
        return cx0 + (CORE_W - SLIM_W) / 2;
    }

    private boolean inCore(int x, int z) {
        return x >= coreX0() && x < coreX0() + SLIM_W && z >= cz0 && z < cz0 + CORE_D;
    }

    // ------------------------------------------------------------------ outside

    private void facade(int roofY) {
        for (int y = 0; y <= roofY; y++) {
            int k = floorOf(y);
            int in = inset(Math.min(k, floors));
            boolean slab = y >= LOBBY && (y - LOBBY) % FLOOR == 0;
            for (int x = in; x < w - in; x++) {
                for (int z = in; z < d - in; z++) {
                    boolean edge = x == in || z == in || x == w - 1 - in || z == d - 1 - in;
                    if (!edge) {
                        continue;
                    }
                    boolean frontBack = z == in || z == d - 1 - in;
                    int along = frontBack ? x - in : z - in;
                    int length = (frontBack ? w : d) - 2 * in;
                    String skin = Placer.inWall(skin(y, k, slab, along, length), frontBack);
                    p.set(x, y, z, skin);
                    if (y >= LOBBY && (y - LOBBY) % FLOOR == 2 && skin.startsWith("window")) {
                        roomGlow(x, y, z, x == in ? 1 : x == w - 1 - in ? -1 : 0, z == in ? 1 : z == d - 1 - in ? -1 : 0);
                    }
                }
            }
            // the terrace where the tower steps in: roof and parapet on the lower part
            if (k == setbackFloor && y == floorY(k)) {
                for (int x = 0; x < w; x++) {
                    for (int z = 0; z < d; z++) {
                        boolean outer = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                        if (x < in || z < in || x >= w - in || z >= d - in) {
                            p.set(x, y, z, outer ? trim() : "roof");
                            if (outer) {
                                p.set(x, y + 1, z, trim());
                            }
                        }
                    }
                }
            }
        }
        int in = inset(floors);
        for (int x = in; x < w - in; x++) {
            for (int z = in; z < d - in; z++) {
                if (x == in || z == in || x == w - 1 - in || z == d - 1 - in) {
                    p.set(x, roofY + 1, z, wallMaterial());
                    p.set(x, roofY + 2, z, trim());
                }
            }
        }
        // the entrance: two pairs of glass doors under a transom, a steel canopy over the sidewalk
        int mid = w / 2;
        p.fill(mid - 2, 3, 0, mid + 1, 3, 0, "window[axis=x]");
        Doors.pair(p, rng, mid - 2, 1, 0, Doors.SHOP, "south");
        Doors.pair(p, rng, mid, 1, 0, Doors.SHOP, "south");
        p.fill(mid - 3, 4, -3, mid + 2, 4, -1, "steel_mullion");
        p.set(mid - 3, 3, -3, "wall_lantern");
        p.set(mid + 2, 3, -3, "wall_lantern");
        // lamp boxes under the canopy, throwing their colour onto the wet sidewalk
        p.set(mid - 2, 3, -2, ShopLights.lamp(rng, 0.15));
        p.set(mid + 1, 3, -2, ShopLights.lamp(rng, 0.15));
    }

    /**
     * Now and then a room still glows: a purple lamp up under the ceiling just inside the glass,
     * a few to a tower, dotted up the dark face.
     */
    private void roomGlow(int x, int y, int z, int dx, int dz) {
        String lamp = ShopLights.window(ShopLights.spot(p, x, y, z), 0.0025);
        if (lamp != null) {
            p.setIfAir(x + dx, y + 1, z + dz, lamp);
        }
    }

    private String wallMaterial() {
        return switch (style) {
            case GLASS -> "steel_mullion";
            case DECO -> "stone_trim";
            case BRICK -> "brick_dark";
            default -> "concrete_dark";
        };
    }

    private String trim() {
        return style == GLASS ? "steel_mullion" : style == GRID ? "concrete_panel" : "stone_trim";
    }

    /** The facade block at one spot: columns, spandrels and windows depend on the style. */
    private String skin(int y, int floor, boolean slab, int along, int length) {
        boolean corner = along == 0 || along == length - 1;
        if (y < LOBBY) {
            // the lobby: tall glass between piers, a stone band over it
            if (corner || along % 4 == 0) {
                return style == GLASS ? "steel_mullion" : style == BRICK ? "stone_trim" : "concrete_dark";
            }
            return y == 0 ? "concrete_dark" : y == LOBBY - 1 ? "stone_trim" : "window";
        }
        if (corner) {
            return wallMaterial();
        }
        if (scenic(floor)) {
            // full-height glass between slim mullions, a thin steel band at the slab
            return along % 6 == 0 ? "steel_mullion" : slab ? "glass_spandrel" : windowFor(floor);
        }
        switch (style) {
            case GLASS:
                if (along % 4 == 0) {
                    return "steel_mullion";
                }
                return slab ? "glass_spandrel" : windowFor(floor);
            case DECO:
                // continuous stone piers every other block, dark brick spandrels between
                if (along % 2 == 0) {
                    return "stone_trim";
                }
                return slab ? "brick_dark" : windowFor(floor);
            case BRICK:
                if (slab) {
                    return floor % 5 == 0 ? "stone_trim" : "brick_dark";
                }
                return along % 3 == 0 ? "brick_dark" : windowFor(floor);
            default:
                if (along % 4 == 0) {
                    return "concrete_dark";
                }
                return slab ? "concrete_panel" : windowFor(floor);
        }
    }

    private String windowFor(int floor) {
        double r = rng.nextDouble();
        if (floor <= floors && (powered[floor] ? r < 0.75 : r < 0.035)) {
            return "window_lit";
        }
        return rng.nextDouble() < 0.07 ? "window_broken" : "window";
    }

    // ------------------------------------------------------------------ lobby

    private void lobby() {
        for (int x = 1; x < w - 1; x++) {
            for (int z = 1; z < d - 1; z++) {
                boolean dark = ((x / 2) + (z / 2)) % 2 == 0;
                p.set(x, 0, z, dark ? "lobby_floor_dark" : "lobby_floor_white");
                p.set(x, LOBBY, z, "office_floor");
                if (x % 4 == 2 && z % 4 == 2 && !inCore(x, z)) {
                    p.set(x, LOBBY - 1, z, rng.nextDouble() < 0.4 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        if (!furnished) {
            return;
        }
        if (hotel()) {
            hotelLobby();
            return;
        }
        // reception desk facing the doors, with a chair behind
        int mid = w / 2;
        int rz = Math.max(3, cz0 - 4);
        for (int x = mid - 4; x <= mid + 3; x++) {
            p.set(x, 1, rz, "reception_desk");
        }
        p.set(mid - 4, 1, rz + 1, "reception_desk");
        p.set(mid + 3, 1, rz + 1, "reception_desk");
        p.set(mid - 2, 1, rz + 1, "office_chair[facing=north]");
        p.set(mid + 1, 1, rz + 2, "office_chair[facing=west]");
        // planters in the corners
        for (int[] c : new int[][] {{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}}) {
            p.set(c[0], 1, c[1], "planter");
            p.set(c[0], 2, c[1], "weeds");
        }
        // security desk by the side wall
        if (cx0 > 4) {
            p.fill(2, 1, cz0, 2, 1, cz0 + 3, "reception_desk");
            p.set(3, 1, cz0 + 1, "office_chair[facing=west]");
        }
        scatter(1, 1, 1, w - 2, d - 2, 4);
    }

    // ------------------------------------------------------------------ office floors

    private void officeFloor(int k, int y) {
        int in = inset(k);
        boolean lit = powered[k];
        for (int x = 1 + in; x < w - 1 - in; x++) {
            for (int z = 1 + in; z < d - 1 - in; z++) {
                if (!inCore(x, z)) {
                    p.set(x, y, z, "office_floor");
                    if (x % 4 == 2 && z % 4 == 2) {
                        p.set(x, y + 3, z, lit ? "ceiling_light" : "ceiling_light_off");
                    }
                }
            }
        }
        if (!furnished) {
            return;
        }
        int kx = coreX0();
        Floorplan fp = new Floorplan(p, rng, w, d, y, FLOOR - 1, new Floorplan.Site() {
            @Override
            public boolean open(int x, int z) {
                return x >= 1 + in && z >= 1 + in && x < w - 1 - in && z < d - 1 - in && !inCore(x, z) && !keep(x, z);
            }

            @Override
            public boolean core(int x, int z) {
                return inCore(x, z);
            }

            @Override
            public boolean keep(int x, int z) {
                // the landing in front of the stair and the lift, and the way into the closet behind
                return z >= cz0 - 2 && z < cz0 && x >= kx && x < kx + SLIM_W || x == kx + 5 && z == cz0 + CORE_D;
            }
        }, lit ? 0.6 : 0.03);
        Floors.fit(fp, program[k], rng);
    }

    private void scatter(int y, int x0, int z0, int x1, int z1, int n) {
        for (int i = 0; i < n; i++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1);
            int z = z0 + rng.nextInt(z1 - z0 + 1);
            if (!inCore(x, z)) {
                p.set(x, y, z, rng.nextBoolean() ? "rubble_pile" : "moss_carpet");
            }
        }
    }

    // ------------------------------------------------------------------ core

    private void core(int roofY) {
        int top = roofY + FLOOR + 1;  // the stair and the lift rise into a plant room on the roof, 4 clear over its steps
        int kx = coreX0(), st = kx + 4, lw = kx + 7, back = cz0 + 4;
        for (int y = 0; y <= top; y++) {
            boolean lobby = y < LOBBY;
            String wall = lobby ? "wood_panel" : "drywall";
            for (int x = kx; x < kx + SLIM_W; x++) {
                for (int z = cz0; z < cz0 + CORE_D; z++) {
                    boolean edge = x == kx || x == lw || z == cz0 || z == cz0 + CORE_D - 1 || x == st || x > st && z == back;
                    if (edge) {
                        p.set(x, y, z, y > roofY ? "concrete_dark" : x == st && !lobby ? "concrete_dark" : wall);
                    } else {
                        p.set(x, y, z, y == 0 ? "floor_concrete" : "air");
                    }
                }
            }
        }
        // the one working lift beside the stair, doors and a call button on every floor and its machine up in
        // the plant room; behind it a janitor's closet on every floor, its door onto the back of the floor
        int[] ys = new int[floors + 1];
        for (int level = 0; level <= floors; level++) {
            int y = floorY(level);
            ys[level] = y;
            p.fill(st + 1, y, back + 1, lw - 1, y, cz0 + CORE_D - 2, "floor_concrete");
            Doors.abandoned(p, rng, st + 1, y + 1, cz0 + CORE_D - 1, Doors.FLAT, "north", Doors.hinge(rng));
            if (level > 0 && furnished) {
                closet(st + 2, y + 1, back + 1);
            }
        }
        LiftBuild.shaft(p::set, st + 1, cz0 + 1, 2, back - cz0 - 1, ys, 0, 0, top - 1);
        stairwell(kx + 1, roofY);
        p.fill(kx, top, cz0, kx + SLIM_W - 1, top, cz0 + CORE_D - 1, "roof");
    }

    /** The janitor's closet behind the lift: a sink, shelves of cleaning stuff, a mop bucket. */
    private void closet(int x, int y, int z0) {
        p.set(x, y, z0, "bunk_sink[facing=south]");
        p.set(x, y, z0 + 1, "home_garage_shelf[facing=west]");
        p.set(x, y, z0 + 2, rng.nextBoolean() ? "dn_mop" : "cardboard_box[facing=west]");
        p.set(x, y + 1, z0 + 1, rng.nextBoolean() ? "home_bottles_low[facing=west]" : "air");
    }

    /** The fire stair ({@link Stairwell}), inner x0..x0+2, from the lobby to the roof. */
    private void stairwell(int x0, int roofY) {
        int[] ys = new int[floors + 2];
        for (int level = 0; level <= floors; level++) {
            ys[level] = floorY(level);
        }
        ys[floors + 1] = roofY;
        Stairwell.build(p, rng, x0, cz0, ys, false, 0, 0);
    }

    // ------------------------------------------------------------------ roof

    private void roof(int roofY) {
        int in = inset(floors);
        for (int x = 1 + in; x < w - 1 - in; x++) {
            for (int z = 1 + in; z < d - 1 - in; z++) {
                if (!inCore(x, z)) {
                    p.set(x, roofY, z, "roof");
                }
            }
        }
        int y = roofY + 1;
        for (int i = 0; i < 3; i++) {
            int x = 2 + in + rng.nextInt(Math.max(1, w - 8 - 2 * in));
            int z = rng.nextBoolean() ? 2 + in + rng.nextInt(3) : d - 5 - in - rng.nextInt(3);
            if (!inCore(x, z) && !inCore(x + 2, z + 1)) {
                p.fill(x, y, z, x + 2, y + 1, z + 1, "hvac");
            }
        }
        if (hotel() && w >= 22 && d >= 18) {
            poolDeck(roofY, in);
            if (crown == 3) {
                waterTank(y, w - 7 - in, d - 7 - in);
            } else {
                mast(y, 3 + in, d - 4 - in);
            }
            return;
        }
        switch (crown) {
            case 1 -> steppedCrown(roofY, in);
            case 2 -> glowingBand(roofY, in);
            case 3 -> waterTank(y, w - 7 - in, d - 7 - in);
            default -> mast(y, 3 + in, d - 4 - in);
        }
    }

    /**
     * The hotel's lobby, two storeys high: the long reception desk facing the doors with the
     * clerks' chairs and the key cabinets behind, the queue ropes, the bell captain's podium and
     * luggage carts by the doors, sitting groups on rugs at either side, the lobby bar, planters.
     * The way from the doors to the lifts stays clear.
     */
    private void hotelLobby() {
        int mid = w / 2, kx = coreX0();
        // the desk down the west side facing across the lobby, the key cabinets behind the clerks
        int dz0 = 4, dz1 = Math.min(d - 7, dz0 + 9), dx = Math.max(4, Math.min(5, kx - 6));
        for (int z = dz0; z <= dz1; z++) {
            p.set(dx, 1, z, "cv_circ_desk[facing=east]");
            p.set(dx - 2, 1, z, rng.nextBoolean() ? "pk_cabinet_oak[facing=east]" : "filing_cabinet[facing=east]");
            if (z % 3 == 1) {
                p.set(dx - 1, 1, z, rng.nextDouble() < 0.2 ? "office_chair_fallen[facing=north]" : "office_chair[facing=east]");
            }
        }
        p.set(dx - 1, 1, dz0 - 1, "home_desk_pc[facing=east]");
        for (int z = dz0 + 1; z < dz1; z += 3) {
            p.set(dx + 2, 1, z, "trade_rope_post[axis=z]");
        }
        int rz = dz0;
        // the bell captain by the doors, the carts left where they stood
        p.set(mid + 4, 1, 3, "tr_podium[facing=north]");
        p.set(mid - 5, 1, 2, "trade_luggage_cart[facing=" + (rng.nextBoolean() ? "east" : "south") + "]");
        if (rng.nextBoolean()) {
            p.set(mid + 6, 1, 4, "trade_luggage_cart[facing=west]");
        }
        // sitting groups on rugs either side, between the windows and the desk
        String sofa = rng.nextBoolean() ? "brown" : "floral";
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? dx + 4 : w - 8;
            if (x0 < 2 || x0 + 4 >= w - 1) {
                continue;
            }
            int z0 = Math.min(rz + 4, d - 8);
            for (int[] g : new int[][] {{x0, 3}, {x0, z0}}) {
                int gx = g[0], gz = g[1];
                if (gz + 3 >= cz0 - 2 && gx + 4 >= kx && gx <= kx + SLIM_W) {
                    continue;
                }
                p.set(gx + 1, 0, gz + 1, "lobby_floor_white");
                p.set(gx, 1, gz, "home_sofa_" + sofa + "_r[facing=south]");
                p.set(gx + 1, 1, gz, "home_sofa_" + sofa + "_m[facing=south]");
                p.set(gx + 2, 1, gz, "home_sofa_" + sofa + "_l[facing=south]");
                p.set(gx + 1, 1, gz + 1, "pk_coffee_table[facing=north]");
                p.set(gx, 1, gz + 2, "home_armchair_" + sofa + "[facing=north]");
                p.set(gx + 2, 1, gz + 2, "home_armchair_" + sofa + "[facing=north]");
                p.set(gx + 3, 1, gz, "home_lamp_table[facing=south]");
                p.set(gx + 3, 2, gz, "home_lamp_table_low[facing=south]");
            }
        }
        // the lobby bar along the back wall, behind the core, if there's room
        int bz = cz0 + CORE_D + 2;
        if (bz + 2 < d - 1) {
            for (int x = 3; x < w - 3; x++) {
                if (x > mid - 3 && x < mid + 2) {
                    continue;
                }
                p.set(x, 1, d - 2, (x % 4 == 0 ? "trade_back_bar" : "pk_shelf_liquor_" + (1 + Math.floorMod(x, 3))) + "[facing=north]");
                p.set(x, 1, d - 4, (x == mid + 4 ? "trade_bar_taps" : "trade_bar") + "[facing=north]");
                if (rng.nextDouble() < 0.7) {
                    p.set(x, 1, d - 5, "pk_bar_chair[facing=south]");
                }
            }
        }
        for (int[] c : new int[][] {{2, 2}, {w - 3, 2}, {2, d - 3}, {w - 3, d - 3}, {mid - 4, 1}, {mid + 3, 1}}) {
            p.set(c[0], 1, c[1], "planter");
            p.set(c[0], 2, c[1], rng.nextBoolean() ? "weeds" : "pk_houseplant[facing=north]");
        }
        scatter(1, 1, 1, w - 2, d - 2, 6);
    }

    /**
     * The hotel's roof: a pool deck, and the pool itself run out over the street past the front
     * of the tower, its far end hanging in the air with a glass floor and glass sides, held on
     * two steel beams. Loungers along the deck, a little bar by the stair hut, a glass rail round
     * the roof's edge.
     */
    private void poolDeck(int roofY, int in) {
        int y = roofY;
        int mid = w / 2, px0 = mid - 4, px1 = mid + 3;
        int out = 6, pz0 = in - out, pz1 = Math.min(in + 9, cz0 - 3);
        // the deck: boards on the roof, the glass rail on the parapet
        for (int x = 1 + in; x < w - 1 - in; x++) {
            for (int z = 1 + in; z < d - 1 - in; z++) {
                if (!inCore(x, z)) {
                    p.set(x, y, z, "floor_boards");
                }
            }
        }
        for (int x = in; x < w - in; x++) {
            for (int z = in; z < d - in; z++) {
                boolean frontBack = z == in || z == d - 1 - in;
                if (x == in || z == in || x == w - 1 - in || z == d - 1 - in) {
                    if (x >= px0 && x <= px1 && z == in) {
                        continue;  // the pool runs out through here
                    }
                    p.set(x, y + 3, z, "tx_glass_rail[axis=" + (frontBack ? "x" : "z") + "]");
                }
            }
        }
        if (pz1 < in + 3) {
            return;  // no room in front of the core: a deck without the pool
        }
        // the pool: two deep, its rim the coping in on the roof and glass out over the street
        for (int x = px0; x <= px1; x++) {
            for (int z = pz0; z <= pz1; z++) {
                boolean outside = z < in;
                boolean rim = x == px0 || x == px1 || z == pz0 || z == pz1;
                p.set(x, y, z, outside ? "of_pool_glass" : "of_pool_tile");
                p.set(x, y + 1, z, rim ? (outside ? "of_pool_glass" : "of_pool_tile") : "minecraft:water");
                p.set(x, y + 2, z, rim ? (outside ? "of_pool_glass" : "of_coping") : "minecraft:water");
                if (outside) {
                    p.set(x, y + 3, z, "air");
                }
            }
        }
        // the parapet cut where the pool passes through it
        for (int x = px0 + 1; x < px1; x++) {
            p.set(x, y + 1, in, "minecraft:water");
            p.set(x, y + 2, in, "minecraft:water");
        }
        // two beams under the hanging end, back into the roof slab
        for (int z = pz0; z < in + 2; z++) {
            p.set(px0, y - 1, z, "fit_girder[axis=z]");
            p.set(px1, y - 1, z, "fit_girder[axis=z]");
        }
        p.set(px0 + 1, y + 1, pz1 + 1, "of_pool_ladder[facing=south]");
        // loungers either side of the pool, facing the view
        for (int z = in + 1; z <= pz1; z += 2) {
            p.set(px0 - 2, y + 1, z, "of_lounger[facing=north]");
            p.set(px1 + 2, y + 1, z, "of_lounger[facing=north]");
        }
        // the pool bar by the stair hut
        int bx = coreX0() - 4, bz = cz0 + 2;
        if (bx > in + 2) {
            for (int z = bz; z < bz + 4; z++) {
                p.set(bx, y + 1, z, "trade_bar[facing=west]");
                p.set(bx - 1, y + 1, z, rng.nextDouble() < 0.3 ? "home_chair_tipped[facing=east]" : "pk_bar_chair[facing=east]");
            }
        }
    }

    private void mast(int y, int x, int z) {
        int h = 14 + rng.nextInt(10);
        p.fill(x, y, z, x, y + h, z, "steel_mullion");
        p.set(x, y + h + 1, z, "aviation_light");
    }

    private void waterTank(int y, int tx, int tz) {
        for (int[] leg : new int[][] {{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            p.fill(tx + leg[0], y, tz + leg[1], tx + leg[0], y + 1, tz + leg[1], "steel_mullion");
        }
        for (int yy = y + 2; yy < y + 6; yy++) {
            for (int dx = 0; dx < 4; dx++) {
                for (int dz = 0; dz < 4; dz++) {
                    boolean corner = (dx == 0 || dx == 3) && (dz == 0 || dz == 3);
                    if (!corner) {
                        p.set(tx + dx, yy, tz + dz, "water_tank");
                    }
                }
            }
        }
        p.set(tx + 1, y + 6, tz + 1, "water_tank_top");
        p.set(tx + 2, y + 6, tz + 2, "water_tank_top");
    }

    /** Stone tiers stepping in above the roof, with a spire and a warning light on top. */
    private void steppedCrown(int roofY, int in) {
        int y = roofY + 3;
        int x0 = in + 2, z0 = in + 2, x1 = w - 3 - in, z1 = d - 3 - in;
        while (x1 - x0 >= 4 && z1 - z0 >= 4) {
            int tier = 3 + rng.nextInt(3);
            for (int yy = y; yy < y + tier; yy++) {
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        boolean edge = x == x0 || z == z0 || x == x1 || z == z1;
                        boolean corner = (x == x0 || x == x1) && (z == z0 || z == z1);
                        boolean slit = edge && !corner && (x + z) % 2 == 1 && yy == y + 1;
                        p.set(x, yy, z, slit ? Placer.inWall("window_lit", z == z0 || z == z1) : edge ? "stone_trim" : "concrete_dark");
                    }
                }
            }
            y += tier;
            x0 += 2;
            z0 += 2;
            x1 -= 2;
            z1 -= 2;
        }
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int h = 10 + rng.nextInt(14);
        p.fill(cx, y, cz, cx, y + h, cz, "steel_mullion");
        p.set(cx, y + h + 1, cz, "aviation_light");
    }

    /** A ring of light around the top floor, the last thing still powered. */
    private void glowingBand(int roofY, int in) {
        for (int x = in; x < w - in; x++) {
            for (int z = in; z < d - in; z++) {
                boolean alongX = z == in || z == d - 1 - in, alongZ = x == in || x == w - 1 - in;
                if (alongX || alongZ) {
                    // stone at the corners, where a pane would show its edge to one street
                    p.set(x, roofY + 1, z, alongX && alongZ ? trim() : Placer.inWall("window_lit", alongX));
                }
            }
        }
        mast(roofY + 1, w - 4 - in, 3 + in);
    }
}
