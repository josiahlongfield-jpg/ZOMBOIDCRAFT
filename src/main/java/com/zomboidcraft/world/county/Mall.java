package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Grayhaven Mall, the island's covered shopping centre: a downtown galleria of the late seventies
 * on a lot of four blocks by three. A two-storey bar of shops runs between two department stores
 * (the anchors, three floors each), the concourse down its middle open to a glass roof, walkways
 * along each side upstairs with bridges across the well. In the middle the centre court: the
 * fountain, the escalators crossing over it and the glass crown over all. Off the court to the
 * north the cinema wing (the arcade under it, four screens upstairs), to the south the food
 * court under its skylight; behind each row of shops a service corridor with the fire stairs.
 * Outside, a car park to the north under the mall's sign, a parking deck either side of the food
 * court to the south.
 *
 * <p>The city fell around it, and people made their stand inside: the doors barricaded with
 * shopping carts and benches, the grilles pulled down on the shops, panes out of the roof and the
 * rain in on the planters, which grew.
 *
 * <p>Drawn in the lot's frame: x along the front (west to east), z from the front (north) back.
 * Every storey's floor block is at its y (ground 0, upstairs 6, roof 12); the anchors' floors at
 * 0, 6 and 12, their roofs at 18.
 */
final class Mall {
    /** The building's west and east walls (the anchors' outer walls). */
    static final int X0 = 10, X1 = 282;
    /** The bar's walls: north outer, the north shops' backs, the storefronts, the south shops' backs, south outer. */
    static final int Z0 = 36, BN = 40, FN = 64, FS = 82, BS = 106, Z1 = 110;
    /** The anchors' inner walls (to the bar), and how far they stand out of it north and south. */
    static final int AW = 66, AE = 226, AZ0 = 24, AZ1 = 122;
    /** Floors: upstairs, the bar's roof; the anchors' third floor and roof. */
    static final int UP = 6, ROOF = 12, A3 = 12, AROOF = 18;
    /** The concourse's well upstairs (the walkways either side of it). */
    static final int WELL0 = 69, WELL1 = 77;
    /** The centre court, and its well upstairs. */
    static final int CX0 = 128, CX1 = 164, CZ0 = 50, CZ1 = 96, KX0 = 134, KX1 = 158, KZ0 = 56, KZ1 = 90;
    /** The glass roof: its eaves over the clerestory and its ridge. */
    static final int EAVE = 15, RIDGE = 23;
    /** The two mall entrances through each row, eight wide from these x. */
    static final int[] ENTRY = {92, 193};
    static final int EW = 8;
    /** The bridges across the concourse's well upstairs, five wide from these x. */
    private static final int[] BRIDGES = {78, 110, 178, 210};
    /** The cinema wing (north of the court) and the food court (south of it). */
    static final int NX0 = 124, NX1 = 168, NZ0 = 6, FX0 = 118, FX1 = 174, FZ1 = 142;
    /** The parking decks: west and east of the food court. */
    static final int PZ0 = 150, PZ1 = 206;

    private final Placer p;
    private final SplittableRandom rng;
    private final Bldg b;
    private final int w, d;
    /** The emergency generator still runs a few of the lights (and the escalators, like everywhere). */
    private final boolean power;

    Mall(Placer p, SplittableRandom rng, int w, int d) {
        this.p = p;
        this.rng = rng;
        this.w = w;
        this.d = d;
        this.b = new Bldg(p, rng);
        b.damage = 0.55;
        b.boarded = 0.2;
        this.power = rng.nextDouble() < 0.6;
    }

    void build() {
        new MallAnchor(p.turned(X0, 0, AZ0, 0), rng, true, power).build();
        new MallAnchor(p.turned(X1, 0, AZ1, 2), rng, false, power).build();
        bar();
        concourse();
        court();
        entrances();
        new MallWings(p, rng, b, power).build();
        new MallParking(p, rng, w, d, power).build();
        rails();
        glassRoof();
        serviceStairs();
        roofTop();
    }

    private void set(int x, int y, int z, String block) {
        p.set(x, y, z, block);
    }

    private String get(int x, int y, int z) {
        return p.get(x, y, z);
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String lamp(double share) {
        return power && chance() < share ? "ceiling_light" : "ceiling_light_off";
    }

    /** The outside walls: precast panels on a brick base, a brick band at each floor. */
    private String outer(int y) {
        return y <= 1 ? (chance() < 0.25 ? "ml_brick_b" : "ml_brick") : y == UP || y == ROOF + 1 ? "ml_precast_band"
                : chance() < 0.3 ? "ml_precast_b" : "ml_precast";
    }

    /** Inside, behind the shops and in the corridors: painted block. */
    private String block(int y) {
        return chance() < 0.12 ? "drywall_stain" : chance() < 0.05 ? "drywall_peel" : "drywall";
    }

    /** The concourse's floor: terrazzo, cracked and grimed, green where the roof lets the rain in. */
    private String terrazzo(int x, int z) {
        // the court in rings round the fountain; down the concourse a slate band along each row of
        // shops and the rose panels between the planters, each in its slate border
        if (x >= CX0 && x <= CX1 && z >= CZ0 && z <= CZ1) {
            double d = Math.hypot(x - (KX0 + KX1) / 2.0, z - (KZ0 + KZ1) / 2.0);
            if (d >= 7.5 && d < 8.5 || d >= 14 && d < 15) {
                return "ml_terrazzo_slate";
            }
            if (d >= 10 && d < 11.5) {
                return "ml_terrazzo_rose";
            }
        } else if (z > FN && z < FS) {
            if (z == FN + 2 || z == FS - 2) {
                return "ml_terrazzo_slate";
            }
            int u = Math.floorMod(x - AW, 16);
            if (u >= 2 && u <= 13 && z >= WELL0 && z <= WELL1) {
                return u == 2 || u == 13 || z == WELL0 || z == WELL1 ? "ml_terrazzo_slate" : "ml_terrazzo_rose";
            }
        }
        double r = chance();
        return r < 0.08 ? "tx_terrazzo_d" : r < 0.3 ? "tx_terrazzo_b" : "tx_terrazzo";
    }

    // ------------------------------------------------------------------ the bar of shops

    private void bar() {
        int xa = AW + 1, xb = AE - 1;
        // the outer walls, north and south, up to the parapet
        for (int x = xa; x <= xb; x++) {
            for (int y = 0; y <= ROOF + 2; y++) {
                set(x, y, Z0, y == ROOF + 2 ? "stone_trim" : outer(y));
                set(x, y, Z1, y == ROOF + 2 ? "stone_trim" : outer(y));
            }
        }
        // floors and the roof over the rows, the corridors and the concourse
        for (int x = xa; x <= xb; x++) {
            for (int z = Z0 + 1; z < Z1; z++) {
                boolean hall = z > FN && z < FS;
                set(x, 0, z, hall ? terrazzo(x, z) : z < BN || z > BS ? b.wall("floor_concrete", 3) : "ml_vct");
                for (int y = 1; y < ROOF; y++) {
                    if (y != UP) {
                        set(x, y, z, "air");
                    }
                }
                // the walkways upstairs either side of the well; the rows' and corridors' upper floors
                boolean well = z >= WELL0 && z <= WELL1;
                if (!well) {
                    set(x, UP, z, hall ? "ml_walk" : z < BN || z > BS ? b.wall("floor_concrete", 3) : chance() < 0.15 ? "ml_slab_b" : "ml_slab");
                } else {
                    set(x, UP, z, "air");
                }
                if (!hall) {
                    set(x, ROOF, z, b.wall("roof", 2));
                }
            }
        }
        // the shop rows' backs and fronts, the corridors' walls
        for (int x = xa; x <= xb; x++) {
            for (int y = 1; y < ROOF; y++) {
                if (y == UP) {
                    continue;
                }
                set(x, y, BN, block(y));
                set(x, y, BS, block(y));
            }
            // the storefront lines carry up past the shops' roofs as the clerestory under the glass
            for (int y = ROOF; y < EAVE; y++) {
                set(x, y, FN, y == ROOF ? "stone_trim" : clerestory(x, y));
                set(x, y, FS, y == ROOF ? "stone_trim" : clerestory(x, y));
            }
            set(x, EAVE, FN, "stone_trim");
            set(x, EAVE, FS, "stone_trim");
        }
        rows();
        corridors();
    }

    /** The clerestory over the shops' roofs: glass between steel posts every fourth block. */
    private String clerestory(int x, int y) {
        return clerestory(x, y, "x");
    }

    /** The same along a wall running along axis (x, or z at the court's ends), its posts every fourth block along it. */
    private String clerestory(int along, int y, String axis) {
        if (Math.floorMod(along - AW, 4) == 0) {
            return "tx_column";
        }
        double r = chance();
        return (r < 0.12 ? "tx_glass_broken" : r < 0.25 ? "tx_glass_cracked" : "tx_glass") + "[axis=" + axis + "]";
    }

    /** A run of shop units along one row, cut between the anchors, the entrances and the court. */
    private void rows() {
        List<int[]> runs = new ArrayList<>();
        int[] cuts = {AW, ENTRY[0] - 1, ENTRY[0] + EW, CX0 - 1, CX1 + 1, ENTRY[1] - 1, ENTRY[1] + EW, AE};
        for (int i = 0; i + 1 < cuts.length; i += 2) {
            runs.add(new int[] {cuts[i], cuts[i + 1]});
        }
        for (boolean north : new boolean[] {true, false}) {
            for (int[] run : runs) {
                units(run[0], run[1], north);
            }
        }
    }

    /**
     * The units between the party walls at x0 and x1 on one row: widths of eight to sixteen, a
     * double-width one now and then, the walls between them up through both storeys.
     */
    private void units(int x0, int x1, boolean north) {
        List<Integer> walls = new ArrayList<>();
        walls.add(x0);
        int x = x0;
        while (x1 - x > 20) {
            int width = 8 + 4 * rng.nextInt(3);
            x += width;
            walls.add(x);
        }
        if (x1 - x > 13) {
            x += (x1 - x) / 2;
            walls.add(x);
        }
        walls.add(x1);
        int back = north ? BN : BS, front = north ? FN : FS;
        for (int i = 0; i < walls.size(); i++) {
            int wx = walls.get(i);
            for (int z = Math.min(back, front); z <= Math.max(back, front); z++) {
                for (int y = 1; y < ROOF; y++) {
                    if (y != UP) {
                        set(wx, y, z, block(y));
                    }
                }
            }
        }
        for (int i = 0; i + 1 < walls.size(); i++) {
            int ux0 = walls.get(i) + 1, ux1 = walls.get(i + 1) - 1;
            for (int storey = 0; storey < 2; storey++) {
                int y = storey * UP;
                MallShop unit = new MallShop(p, rng, b, ux0, ux1, back, front, y, UP, power);
                unit.build();
            }
        }
    }

    /** The service corridors behind each row: their back doors, lamps, the stores' junk. */
    private void corridors() {
        for (int z : new int[] {Z0 + 1, Z0 + 2, Z0 + 3, Z1 - 3, Z1 - 2, Z1 - 1}) {
            for (int x = AW + 1; x < AE; x++) {
                for (int y : new int[] {UP - 1, ROOF - 1}) {
                    if (Math.floorMod(x, 6) == 0 && (z == Z0 + 2 || z == Z1 - 2) && get(x, y, z) == null) {
                        set(x, y, z, "tx_ceiling_lamp[axis=x]");
                    }
                }
            }
        }
        for (int x = AW + 1; x < AE; x++) {
            for (int y : new int[] {1, UP + 1}) {
                for (int z : new int[] {Z0 + 1, Z1 - 1}) {
                    if (get(x, y, z) == null && chance() < 0.06) {
                        double r = chance();
                        set(x, y, z, r < 0.4 ? "cardboard_box[facing=north]" : r < 0.7 ? "trash_bag[facing=east]"
                                : r < 0.85 ? "store_cart[facing=" + (z < BN ? "east" : "west") + "]" : "crate");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ the concourse

    private void concourse() {
        // the bridges across the well upstairs
        for (int bx : BRIDGES) {
            for (int x = bx; x < bx + 5; x++) {
                for (int z = WELL0; z <= WELL1; z++) {
                    set(x, UP, z, "ml_walk");
                }
            }
        }
        // the downlights in the walkways' soffits, a few still on; the lamps along the shops upstairs
        for (int x = AW + 2; x < AE - 1; x += 3) {
            if (x >= CX0 - 1 && x <= CX1 + 1) {
                continue;
            }
            for (int z : new int[] {FN + 2, FS - 2}) {
                if (power && chance() < 0.3) {
                    set(x, UP, z, "ml_walk_on");
                }
            }
        }
        for (int x = AW + 2; x < AE - 1; x += 4) {
            if (x >= CX0 - 1 && x <= CX1 + 1) {
                continue;
            }
            set(x, ROOF - 1, FN + 2, lamp(0.2));
            set(x, ROOF - 1, FS - 2, lamp(0.2));
        }
        // the directories facing each entrance, the banners hung down the middle from the roof
        for (int ex : ENTRY) {
            MallSigns.directory(p, rng, ex + 3, WELL0 + 1, "north", power);
            MallSigns.directory(p, rng, ex + 3, WELL1 - 1, "south", power);
        }
        for (int x = AW + 8; x < AE - 8; x += 16) {
            if (x >= CX0 - 4 && x <= CX1 + 4) {
                continue;
            }
            int zc = (WELL0 + WELL1) / 2;
            MallSigns.banner(p, rng, x, UP + 7, zc, hallHeight(zc) - 1, (x / 16) % 2 == 0 ? "north" : "east");
        }
        // planters, benches and the kiosks down the middle of the ground floor
        for (int x = AW + 8; x < AE - 8; x += 16) {
            if (x >= CX0 - 8 && x <= CX1 + 8 || inEntry(x, 3)) {
                continue;
            }
            planter(x, (WELL0 + WELL1) / 2);
            if (chance() < 0.7) {
                set(x - 2, 1, WELL0 + 1, "tx_bench[facing=north]");
                set(x + 2, 1, WELL1 - 1, "tx_bench[facing=south]");
            }
        }
        for (int x = AW + 16; x < AE - 16; x += 16) {
            if (x >= CX0 - 8 && x <= CX1 + 8 || inEntry(x, 4) || chance() < 0.3) {
                continue;
            }
            kiosk(x, (WELL0 + WELL1) / 2);
        }
    }

    /** Whether x is within m of an entrance passage. */
    private static boolean inEntry(int x, int m) {
        for (int ex : ENTRY) {
            if (x >= ex - m && x < ex + EW + m) {
                return true;
            }
        }
        return false;
    }

    /**
     * Glass balustrade round every edge of the floor upstairs where it stops at the well: the
     * walkways, the bridges, the court's ring (not across an escalator's head).
     */
    private void rails() {
        int[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int x = AW + 1; x < AE; x++) {
            for (int z = CZ0; z <= CZ1; z++) {
                if (!open(x, z)) {
                    continue;
                }
                String f = get(x, UP, z);
                if (f == null || f.startsWith("tx_esc") || f.startsWith("zomboidcraft:") || get(x, UP + 1, z) != null) {
                    continue;
                }
                for (int[] dv : dirs) {
                    int nx = x + dv[0], nz = z + dv[1];
                    if (open(nx, nz) && get(nx, UP, nz) == null) {
                        set(x, UP + 1, z, "tx_glass_rail[axis=" + (dv[0] == 0 ? "x" : "z") + "]");
                        break;
                    }
                }
            }
        }
    }

    /** Inside the concourse or the court (the space the well is cut from). */
    private static boolean open(int x, int z) {
        return x > AW && x < AE && z > FN && z < FS || x >= CX0 && x <= CX1 && z >= CZ0 && z <= CZ1;
    }

    /**
     * A raised planter three by three: a stone kerb round the soil and the ficus that was in it,
     * grown wild since the roof let the rain in.
     */
    private void planter(int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                set(x, 1, z, x != cx || z != cz ? "stone_trim" : "planter");
            }
        }
        plant(p, rng, cx, cz, false);
    }

    /**
     * What grows in a planter at (cx, cz) with its soil at y 1: a fig, a palm (tall ones in the
     * court), or ferns; dead in some, and the ferns gone over the kerb where the rain gets in.
     */
    static void plant(Placer p, SplittableRandom rng, int cx, int cz, boolean tall) {
        double r = rng.nextDouble();
        if (tall || r < 0.3) {
            int h = tall ? 4 + rng.nextInt(2) : 2 + rng.nextInt(2);
            for (int y = 2; y < 2 + h; y++) {
                p.set(cx, y, cz, "mu_palm_trunk");
            }
            p.set(cx, 2 + h, cz, "mu_palm_crown");
        } else if (r < 0.8) {
            p.set(cx, 2, cz, "ml_ficus_lo");
            p.set(cx, 3, cz, rng.nextDouble() < 0.85 ? "ml_ficus_hi" : "air");
        } else if (r < 0.92) {
            p.set(cx, 2, cz, "home_plant_dead");
        } else {
            p.set(cx, 2, cz, "mu_fern");
        }
        for (int[] o : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {0, -1}, {0, 1}, {-1, 0}, {1, 0}}) {
            if (rng.nextDouble() < 0.3) {
                p.set(cx + o[0], 2, cz + o[1], rng.nextDouble() < 0.7 ? "mu_fern" : "home_plant");
            }
        }
    }

    /** A kiosk in the concourse: a counter round a booth under its canopy. */
    private void kiosk(int cx, int cz) {
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 1; z <= cz + 1; z++) {
                boolean edge = x == cx - 2 || x == cx + 2 || z == cz - 1 || z == cz + 1;
                if (edge && !(x == cx + 2 && z == cz)) {
                    set(x, 1, z, "store_counter[facing=" + (z == cz - 1 ? "north" : z == cz + 1 ? "south" : x < cx ? "west" : "east") + "]");
                }
                set(x, 4, z, "stone_trim");
            }
        }
        for (int[] c : new int[][] {{cx - 2, cz - 1}, {cx + 2, cz + 1}}) {
            set(c[0], 2, c[1], "tx_column");
            set(c[0], 3, c[1], "tx_column");
        }
    }

    // ------------------------------------------------------------------ the centre court

    private void court() {
        // the court's floor through both rows, open to the crown; the ring upstairs round its well
        for (int x = CX0; x <= CX1; x++) {
            for (int z = CZ0; z <= CZ1; z++) {
                set(x, 0, z, terrazzo(x, z));
                for (int y = 1; y < RIDGE; y++) {
                    set(x, y, z, "air");
                }
                boolean well = x >= KX0 && x <= KX1 && z >= KZ0 && z <= KZ1 || z >= WELL0 && z <= WELL1;
                set(x, UP, z, well ? "air" : "ml_walk");
            }
        }
        // the court's walls behind the rows (to the cinema's way in and the food court's passage)
        for (int x = CX0 - 1; x <= CX1 + 1; x++) {
            for (int y = 1; y < ROOF; y++) {
                if (y == UP) {
                    continue;
                }
                set(x, y, CZ0 - 1, block(y));
                set(x, y, CZ1 + 1, block(y));
            }
        }
        for (int x = CX0; x <= CX1; x += 4) {
            set(x, UP - 1, CZ0 + 2, lamp(0.3));
            set(x, UP - 1, CZ1 - 2, lamp(0.3));
        }
        // the cinema's name high on the wall to the north, the food court's over the way through to it
        MallSigns.name(p, rng, "cinema", 6, 2, (CX0 + CX1) / 2 - 3, ROOF - 1, CZ0, "south", power && chance() < 0.8);
        MallSigns.name(p, rng, "food", 6, 1, (CX0 + CX1) / 2 - 3, UP + 3, CZ1, "north", power && chance() < 0.8);
        fountain((KX0 + KX1) / 2, (KZ0 + KZ1) / 2);
        // the escalators crossing the well: a pair up toward the east north of the fountain, one toward the west south of it
        escalators(KX1 + 1 - (UP + 1), KZ0 + 2, 1);
        escalators(KX0 - 1 + (UP + 1), KZ1 - 3, -1);
        for (int[] c : new int[][] {{KX0 + 2, KZ0 + 6}, {KX1 - 2, KZ0 + 6}, {KX0 + 2, KZ1 - 6}, {KX1 - 2, KZ1 - 6}}) {
            for (int x = c[0] - 1; x <= c[0] + 1; x++) {
                for (int z = c[1] - 1; z <= c[1] + 1; z++) {
                    set(x, 1, z, x != c[0] || z != c[1] ? "stone_trim" : "planter");
                }
            }
            plant(p, rng, c[0], c[1], true);
        }
    }

    /**
     * The fountain: a round basin eleven across with a wide stone rim to sit on, a column in the
     * middle with its bowl, the water gone green and still.
     */
    private void fountain(int cx, int cz) {
        // the basin: a smooth concrete drum two high round the water, moss on it where the pumps stopped
        double ox = cx + 0.5, oz = cz + 0.5;
        java.util.Set<Long> rim = new java.util.HashSet<>();
        for (Round.Cell c : Round.ring(ox, oz, 5.4)) {
            rim.add(((long) c.x() << 32) ^ (c.z() & 0xffffffffL));
        }
        for (int x = cx - 6; x <= cx + 6; x++) {
            for (int z = cz - 6; z <= cz + 6; z++) {
                if (Math.hypot(x + 0.5 - ox, z + 0.5 - oz) < 5.2 && !rim.contains(((long) x << 32) ^ (z & 0xffffffffL))) {
                    set(x, -1, z, "stone_trim");
                    set(x, 0, z, "minecraft:water");
                    if (chance() < 0.12) {
                        set(x, 1, z, "moss_carpet");
                    }
                }
            }
        }
        Round.wall(p, ox, oz, 5.4, 0, 1, "twr", 0);
        // the centre: a pedestal, a wide round bowl holding water, a slimmer stem, the small bowl, the finial
        for (int y = 0; y <= 2; y++) {
            set(cx, y, cz, "stone_trim");
        }
        set(cx, 3, cz, "stone_trim");
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int z = cz - 2; z <= cz + 2; z++) {
                if (Math.hypot(x - cx, z - cz) < 1.5) {
                    set(x, 3, z, "stone_trim");
                    set(x, 4, z, x == cx && z == cz ? "stone_trim" : "minecraft:water");
                }
            }
        }
        Round.wall(p, ox, oz, 2.4, 3, 4, "twr", 0);
        for (int y = 5; y <= 6; y++) {
            set(cx, y, cz, "om_bollard");
        }
        Round.wall(p, ox, oz, 1.2, 7, 7, "twr", 0);
        set(cx, 8, cz, "om_bollard");
    }

    /**
     * A pair of escalators rising UP blocks toward +x (s = 1) or -x from the comb plate at x0, in
     * lanes z0 (up) and z0 + 1 (down): the balustrades over the steps, the plates at each end,
     * the stringer clad under them, four clear over every step.
     */
    private void escalators(int x0, int z0, int s) {
        String up = s > 0 ? "east" : "west", down = s > 0 ? "west" : "east";
        for (int k = 1; k <= UP; k++) {
            int x = x0 + s * k;
            for (int z = z0; z <= z0 + 1; z++) {
                boolean upLane = z == z0;
                for (int y = 1; y < k; y++) {
                    set(x, y, z, "stone_trim");
                }
                set(x, k, z, "zomboidcraft:escalator[facing=" + up + ",run=" + (upLane ? "up" : "down") + "]");
                set(x, k + 1, z, "tx_esc_side_" + (upLane == s > 0 ? "r" : "l") + "[facing=" + up + "]");
                for (int y = k + 2; y <= k + Builders.HEADROOM; y++) {
                    set(x, y, z, "air");
                }
            }
        }
        for (int z = z0; z <= z0 + 1; z++) {
            String hand = (z == z0) == s > 0 ? "r" : "l";
            set(x0, 0, z, "tx_esc_plate[facing=" + up + "]");
            set(x0, 1, z, "tx_esc_newel_" + hand + "[facing=" + down + "]");
            set(x0 + s * (UP + 1), UP, z, "tx_esc_plate[facing=" + down + "]");
            set(x0 + s * (UP + 1), UP + 1, z, "tx_esc_newel_" + hand + "[facing=" + up + "]");
        }
    }

    // ------------------------------------------------------------------ the entrances

    /**
     * The four mall entrances, two through each row: a passage two storeys high from the
     * concourse out through the corridor to the glass doors in the outer wall, the corridor
     * upstairs crossing it on a bridge, the doors barricaded from inside with carts and benches.
     */
    private void entrances() {
        for (int ex : ENTRY) {
            for (boolean north : new boolean[] {true, false}) {
                int zo = north ? Z0 : Z1, zf = north ? FN : FS, s = north ? -1 : 1;
                int za = Math.min(zo, zf) + (north ? 1 : 0), zb = Math.max(zo, zf) - (north ? 0 : 1);
                for (int x = ex; x < ex + EW; x++) {
                    for (int z = za; z <= zb; z++) {
                        set(x, 0, z, terrazzo(x, z));
                        for (int y = 1; y < ROOF; y++) {
                            set(x, y, z, "air");
                        }
                        set(x, ROOF, z, b.wall("roof", 2));
                    }
                }
                // the passage's sides: the end walls of the units either side, and the corridors' doors
                for (int z = za; z <= zb; z++) {
                    if (z == zf) {
                        continue;
                    }
                    for (int y = 1; y < ROOF; y++) {
                        set(ex - 1, y, z, block(y));
                        set(ex + EW, y, z, block(y));
                    }
                }
                int zc = north ? Z0 + 2 : Z1 - 2;
                for (int x = ex; x < ex + EW; x++) {
                    for (int z = zc - 1; z <= zc + 1; z++) {
                        set(x, UP, z, b.wall("floor_concrete", 3));
                    }
                    set(x, UP + 1, zc - s, "tx_glass_rail[axis=x]");
                }
                for (int y : new int[] {1, UP + 1}) {
                    Doors.abandoned(p, rng, ex - 1, y, zc, Doors.STEEL, "west", Doors.hinge(rng));
                    Doors.abandoned(p, rng, ex + EW, y, zc, Doors.STEEL, "east", Doors.hinge(rng));
                }
                // the doors in the outer wall: three pairs of glass under a transom, the glass over them
                for (int x = ex; x < ex + EW; x++) {
                    for (int y = 1; y <= UP - 1; y++) {
                        set(x, y, zo, y <= 2 ? "air" : y == 3 ? "tx_glass_t[axis=x]" : "tx_glass[axis=x]");
                    }
                }
                for (int y = 1; y <= UP - 1; y++) {
                    set(ex, y, zo, "tx_column");
                    set(ex + EW - 1, y, zo, "tx_column");
                }
                for (int x = ex + 1; x < ex + EW - 1; x += 2) {
                    Doors.pair(p, rng, x, 1, zo, Doors.SHOP, north ? "south" : "north");
                }
                // the entrance's front raised over the roof with the mall's name on it, the glass canopy over the doors
                for (int x = ex - 3; x <= ex + EW + 2; x++) {
                    for (int k = 0; k < 3; k++) {
                        for (int y = ROOF + 1; y <= ROOF + 6; y++) {
                            set(x, y, zo - s * k, k == 0 ? "ml_precast_band" : "ml_precast");
                        }
                        set(x, ROOF + 7, zo - s * k, "stone_trim");
                    }
                    for (int k = 1; k <= 6; k++) {
                        set(x, UP, zo + s * k, k == 6 ? "tx_canopy_edge[facing=" + (north ? "north" : "south") + "]" : "tx_canopy");
                    }
                }
                // the canopy out over the drop-off on red steel posts, the way the photos have it, and its lights under it
                Harbour hb = new Harbour(p, rng, 0.5);
                for (int x : new int[] {ex - 3, ex + EW + 2}) {
                    hb.member(x, 1, zo + s * 5, UP - 1, zo + s * 5, "cr_strut_red");
                    set(x, 0, zo + s * 5, "stone_trim");
                }
                for (int x = ex - 2; x <= ex + EW + 1; x += 3) {
                    set(x, UP - 1, zo + s * 3, power && chance() < 0.4 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
                }
                // the bollards along the kerb so nobody drove into the doors, and the carts that got as far as the drop-off
                for (int x = ex - 4; x <= ex + EW + 3; x += 2) {
                    set(x, 1, zo + s * 7, "om_bollard");
                }
                for (int i = 0; i < 4; i++) {
                    int x = ex - 3 + rng.nextInt(EW + 6), z = zo + s * (2 + rng.nextInt(4));
                    if (get(x, 1, z) == null) {
                        set(x, 1, z, rng.nextInt(3) == 0 ? "trash_bag[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]" : "store_cart[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
                    }
                }
                MallSigns.name(p, rng, "mall", 12, 2, ex - 2, ROOF + 3, zo + s, north ? "north" : "south", power && chance() < 0.75);
                // the carts and benches piled against the doors from inside
                for (int i = 0; i < 10; i++) {
                    int x = ex + rng.nextInt(EW), z = zo - s * (1 + rng.nextInt(3));
                    if (get(x, 1, z) == null && z != zc) {
                        set(x, 1, z, i % 3 == 0 ? "tx_bench[facing=" + (north ? "south" : "north") + "]"
                                : "store_cart[facing=" + Bldg.DIRS[rng.nextInt(4)] + "]");
                    }
                }
                for (int x = ex + 1; x < ex + EW; x += 3) {
                    set(x, ROOF - 1, (zo + zf) / 2 - s * 4, lamp(0.2));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the glass roof

    /** The roof's height over the concourse at z: up a block a block from the eaves to the ridge. */
    private static int hallHeight(int z) {
        if (z <= FN || z >= FS) {
            return -1;
        }
        return Math.min(RIDGE, EAVE + Math.min(z - FN - 1, FS - 1 - z));
    }

    /** The crown's height over the court at x, z. */
    private static int crownHeight(int x, int z) {
        if (x < CX0 || x > CX1 || z < CZ0 || z > CZ1) {
            return -1;
        }
        return Math.min(RIDGE, EAVE + Math.min(Math.min(x - CX0, CX1 - x), Math.min(z - CZ0, CZ1 - z)));
    }

    private static int roofHeight(int x, int z) {
        int h = x > AW && x < AE ? hallHeight(z) : -1;
        return Math.max(h, crownHeight(x, z));
    }

    /**
     * The glass over the concourse and the court as one: each column's pane at its height, stepped
     * glass facing up the slope, flat lights along the ridge and over the crown's top, the
     * clerestory under its edges; a pane in eight broken and the rain in under it.
     */
    private void glassRoof() {
        for (int x = AW + 1; x < AE; x++) {
            for (int z = CZ0; z <= CZ1; z++) {
                int h = roofHeight(x, z);
                if (h < 0) {
                    continue;
                }
                int e = roofHeight(x + 1, z), wv = roofHeight(x - 1, z), so = roofHeight(x, z + 1), no = roofHeight(x, z - 1);
                String up = null;
                int rise = 0;
                for (Object[] o : new Object[][] {{"east", e}, {"west", wv}, {"south", so}, {"north", no}}) {
                    int nh = (Integer) o[1];
                    if (nh > h && nh - h > rise) {
                        rise = nh - h;
                        up = (String) o[0];
                    }
                }
                boolean broken = chance() < 0.12;
                if (up == null || h == RIDGE) {
                    set(x, h, z, broken ? "us_skylight_broken" : "us_skylight");
                } else if (rise == 1) {
                    set(x, h, z, (broken ? "ml_roof_slope_broken" : "ml_roof_slope") + "[facing=" + up + "]");
                } else {
                    set(x, h, z, broken ? "tx_glass_broken" : "tx_roof_glass");
                }
                for (int y = ROOF + 1; y < h; y++) {
                    set(x, y, z, "air");
                }
                // the clerestory where the glass comes down to an edge of the court (the cinema wing stands to the north)
                boolean edge = e < 0 || wv < 0 || so < 0 || no < 0;
                if (edge && h == EAVE && crownHeight(x, z) >= 0 && hallHeight(z) < 0 && z != CZ0) {
                    for (int y = ROOF; y < EAVE; y++) {
                        boolean alongX = so < 0 || no < 0;
                        set(x, y, z, y == ROOF ? "stone_trim" : clerestory(alongX ? x : z, y, alongX ? "x" : "z"));
                    }
                }
                if (broken && chance() < 0.3) {
                    // the rain's been in: a wet stain and moss under it, a sapling in a planter's soil
                    set(x, 1, z, get(x, 1, z) == null ? (chance() < 0.5 ? "moss_carpet" : "weeds") : get(x, 1, z));
                }
            }
        }
        // the steel trusses under the glass, dark red like the frames: a rafter up each slope, the tie
        // across, the king post and the struts, a lamp hung from the tie
        Harbour hb = new Harbour(p, rng, 0.5);
        int mid = (FN + FS) / 2;
        for (int x = AW + 4; x < AE - 2; x += 8) {
            if (x >= CX0 - 1 && x <= CX1 + 1) {
                continue;
            }
            double lo = EAVE - 0.5, top = RIDGE - 0.5;
            hb.member(x, lo, FN + 0.5, top, mid + 0.5, "cr_strut_red");
            hb.member(x, top, mid + 0.5, lo, FS + 0.5, "cr_strut_red");
            hb.member(x, lo, FN + 1.5, lo, FS - 0.5, "cr_strut_red");
            hb.member(x, lo, mid + 0.5, top - 1, mid + 0.5, "cr_strut_red");
            hb.member(x, lo, FN + 4.5, top - 3, mid + 0.5, "cr_strut_red");
            hb.member(x, lo, FS - 3.5, top - 3, mid + 0.5, "cr_strut_red");
            set(x, EAVE - 2, mid, power && chance() < 0.3 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
        }
        // the gable ends against the anchors
        for (int z = FN + 1; z < FS; z++) {
            int h = hallHeight(z);
            for (int y = ROOF; y <= h; y++) {
                set(AW, y, z, outer(y));
                set(AE, y, z, outer(y));
            }
        }
    }

    // ------------------------------------------------------------------ the fire stairs

    /**
     * The fire stairs in the corridors, two each side: a flight up from the ground floor in the
     * corridor's two outer lanes (the inner one stays a way past), a landing, and the next flight
     * on up to a hut on the roof.
     */
    private void serviceStairs() {
        for (int sx : new int[] {AW + 6, AE - 24}) {
            stair(sx, Z0 + 1, 1);
            stair(sx, Z1 - 2, -1);
        }
    }

    /** A fire stair's two flights climbing east from x0 in lanes z0 and z0 + 1 (lane s side is the corridor's open lane). */
    private void stair(int x0, int z0, int s) {
        for (int k = 1; k <= UP; k++) {
            for (int z = z0; z <= z0 + 1; z++) {
                set(x0 + k, k, z, "stairs_concrete[facing=east]");
                for (int y = 1; y < k; y++) {
                    set(x0 + k, y, z, "concrete_panel");
                }
                for (int r = 1; r <= Builders.HEADROOM; r++) {
                    set(x0 + k, k + r, z, "air");
                }
            }
            if (k + Builders.HEADROOM >= UP) {
                int zr = s > 0 ? z0 + 2 : z0 - 1;
                set(x0 + k, UP + 1, zr, "ext_rail_edge_dark[facing=" + (s > 0 ? "north" : "south") + "]");
            }
        }
        // the landing at the top, then the flight to the roof
        for (int z = z0; z <= z0 + 1; z++) {
            set(x0 + UP + 1, UP, z, b.wall("floor_concrete", 3));
            set(x0 + UP + 2, UP, z, b.wall("floor_concrete", 3));
        }
        RoofHut.flight(p, rng, x0 + UP + 2, z0, UP, ROOF, "ml_precast", "stairs_concrete", s > 0 ? "south" : "north", null);
    }

    // ------------------------------------------------------------------ the roof

    /** The plant on the roof: air handlers over the rows, their ducts, the vents. */
    private void roofTop() {
        for (int x = AW + 10; x < AE - 10; x += 22) {
            for (int z : new int[] {BN + 8, BS - 10}) {
                if (x >= CX0 - 4 && x <= CX1 + 4) {
                    continue;
                }
                for (int dx = 0; dx < 4; dx++) {
                    for (int dz = 0; dz < 3; dz++) {
                        set(x + dx, ROOF + 1, z + dz, "hvac");
                        if (dz == 1 && dx == 1) {
                            set(x + dx, ROOF + 2, z + dz, "pk_aircon[facing=south]");
                        }
                    }
                }
            }
        }
    }
}
