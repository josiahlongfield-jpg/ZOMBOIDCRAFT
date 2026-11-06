package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * The harbour fort (refs/coast: sea-fort-exterior, gun-battery-casemate, gun-port-embrasure,
 * coastal-battery-concrete): an old round sea fort on a shoal in the bay, a drum of grey granite
 * coursed up out of the water, dark and weeded in its splash zone, and over it the tier of iron
 * armour round its gun rooms, a port to each, white paint round the parapet and a small red and
 * white light on the roof. The rust has run down the plates from every port, and from the shore
 * that is all you can see of it: the streaked ports facing the sea.
 *
 * Inside, the casemates ring a round light well open to the sky, a gallery round the well at the
 * gun deck's level, the stair up to it from the well's floor and a ladder on up to the roof. The
 * people who sheltered here made the gun rooms their home (cots, a kitchen, a sick bay, the watch
 * room with its radio over the landing) and left two of the old guns run out at their ports. The
 * magazines under them are their stores. Under the furthest magazine, the hatch the War Department
 * left: the shaft down through the fort's footing and the rock under it to an {@link Bunker} dressed
 * as {@link Bunker.Kind#ARMY}, its galleries vaulted like the fort's.
 *
 * You reach it by boat or a long swim: the steel landing stage on its shore side has a ladder down
 * into the water, and the sally port behind it.
 *
 * A hideout of its own, one in the county (like the ship at anchor, {@link OffshoreSite}): drawn
 * round the place's middle in a frame turned so local +x faces the nearest shore, heights from a
 * quay's top (the sea's top block is four under it).
 */
final class FortSite extends Site {
    static final int LEVEL = Terrain.WATER + 3;
    /** The drum's radius to its outside face; the light well's to the inside of its wall. */
    private static final int R = 22;
    private static final double WELL = 6;
    /** The lower (magazine) deck's floor, the gun deck's and the roof's, from the quay's top. */
    private static final int LOW = -3, GUN = 2, ROOF = 8;
    /** The place reaches this far each way from the fort's middle, to hold the bunker under it. */
    private static final int HALF = 58;
    private static final String[] NAMES = {"Gull Bank Fort", "Middle Shoal Fort", "Kettle Rock Fort", "Whelk Bank Fort",
        "Spar Sands Fort", "Horn Shoal Fort"};

    private final County county;
    private Placer f;
    private final Map<Long, Round.Cell> ring = new HashMap<>();
    private Bunker.State state;
    private SplittableRandom r;

    FortSite(Place p, County county, BlockSink out) {
        super(p, out);
        this.county = county;
    }

    /** The fort's name, as the marked maps give it. */
    static String name(Place p) {
        return NAMES[(int) Math.floorMod(p.seed ^ 0xF047L, (long) NAMES.length)];
    }

    /**
     * The place round the fort, in the planned county; null in the endless one or if the coast has
     * no fit. The spot is out on the sea in a bay: 160 to 420 blocks off the shore, over water 7
     * to 22 deep that runs clear round the fort and its landing, clear of the islands, the ports and
     * every other place, the roads and rails (the bridges) and the ship at anchor; of the spots that
     * fit, the one with the most land round it, the seed breaking ties.
     */
    static Place place(County county, long seed) {
        if (!county.planned()) {
            return null;
        }
        Plan plan = Plan.get();
        SplittableRandom r = new SplittableRandom(seed);
        double best = -1;
        int bx = 0, bz = 0;
        int step = 40;
        // the sea's own edges are the map's: keep well in from them, and count only the county's land
        double x0 = plan.sea.minX, x1 = plan.sea.maxX - 1500, z0 = plan.sea.minZ + 1500, z1 = plan.sea.maxZ - 1500;
        for (int x = (int) x0 + step; x < x1; x += step) {
            for (int z = (int) z0; z < z1; z += step) {
                double tie = r.nextDouble();
                if (!plan.inSea(x, z)) {
                    continue;
                }
                double h = county.terrain.height(x, z);
                if (h > Terrain.WATER - 7 || h < Terrain.WATER - 22) {
                    continue;
                }
                double c = plan.coast(x, z);
                if (c > -160 || c < -420) {
                    continue;
                }
                if (!clear(county, plan, x, z)) {
                    continue;
                }
                int land = 0;
                for (int k = 0; k < 16; k++) {
                    double a = Math.PI * 2 * k / 16;
                    for (int dist = 150; dist <= 900; dist += 150) {
                        double sx = x + Math.cos(a) * dist, sz = z + Math.sin(a) * dist;
                        if (sx < x1 + 1000 && sz > z0 - 1000 && sz < z1 + 1000 && !plan.inSea(sx, sz)) {
                            land++;
                            break;
                        }
                    }
                }
                double score = land + tie * 0.9;
                if (score > best) {
                    best = score;
                    bx = x;
                    bz = z;
                }
            }
        }
        if (best < 0) {
            return null;
        }
        return new Place(PlaceType.HIDEOUT, "harbour_fort", bx - HALF, bz - HALF, bx + HALF, bz + HALF, LEVEL, seed, -1, -1);
    }

    /** True if the fort and its landing can stand at (x, z): water all round, nothing else near. */
    private static boolean clear(County county, Plan plan, int x, int z) {
        for (int k = 0; k < 12; k++) {
            double a = Math.PI * 2 * k / 12;
            double sx = x + Math.cos(a) * (R + 10), sz = z + Math.sin(a) * (R + 10);
            if (!plan.inSea(sx, sz) || county.terrain.height(sx, sz) > Terrain.WATER - 4) {
                return false;
            }
        }
        for (Plan.Oval o : plan.islands) {
            if (Math.hypot(o.x() - x, o.z() - z) < Math.max(o.rx(), o.rz()) + 120) {
                return false;
            }
        }
        for (Plan.Ship s : plan.ships) {
            if (Math.hypot(s.x() - x, s.z() - z) < 360) {
                return false;
            }
        }
        for (Plan.Line l : plan.roads) {
            if (l.shape.distance(x, z, 200) < 160) {
                return false;
            }
        }
        for (Plan.Line l : plan.rails) {
            if (l.shape.distance(x, z, 200) < 160) {
                return false;
            }
        }
        return county.placesNear(x - 150, z - 150, x + 150, z + 150, 0).isEmpty();
    }

    @Override
    void build() {
        r = new SplittableRandom(p.seed ^ 0xF0F7L);
        double roll = r.nextDouble();
        state = roll < 0.45 ? Bunker.State.SEALED : roll < 0.72 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        f = new Placer(out, w / 2, 0, d / 2, shore());
        for (Round.Cell c : Round.ring(0, 0, R)) {
            ring.put(key(c.x(), c.z()), c);
        }
        drum();
        walls();
        well();
        roof();
        landing();
        casemates();
        magazines();
        bunker();
        people();
    }

    // ------------------------------------------------------------------------------------- frame

    /** The quarter turn that points local +x at the nearest shore (the landing's side). */
    private int shore() {
        Plan plan = Plan.get();
        double cx = p.centreX(), cz = p.centreZ();
        int[][] dirs = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
        for (int dist = 60; dist <= 3000; dist += 60) {
            for (int q = 0; q < 4; q++) {
                if (!plan.inSea(cx + dirs[q][0] * dist, cz + dirs[q][1] * dist)) {
                    return q;
                }
            }
        }
        return 0;
    }

    /** The sea bed under local (x, z), from the quay's top. */
    private int seabed(int x, int z) {
        int[] at = f.world(x, z);
        return (int) Math.floor(county.terrain.height(p.x0 + at[0] + 0.5, p.z0 + at[1] + 0.5)) - p.level;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static double radius(int x, int z) {
        return Math.hypot(x + 0.5, z + 0.5);
    }

    private static double bearing(int x, int z) {
        return Math.toDegrees(Math.atan2(z + 0.5, x + 0.5));
    }

    /** How far round (in blocks at radius k) (x, z) is from the middle of the nearest casemate. */
    private static double offBay(int x, int z) {
        double b = bearing(x, z);
        double off = b - 30 * Math.round(b / 30);
        return Math.abs(Math.toRadians(off)) * radius(x, z);
    }

    /** A gun port's opening runs this far either side of its casemate's middle. */
    private static boolean port(int x, int z) {
        return offBay(x, z) < 1.3;
    }

    private enum Col { OUT, RING, WALL, ROOM, WELLWALL, GALLERY, COURT }

    private Col col(int x, int z) {
        if (ring.containsKey(key(x, z))) {
            return Col.RING;
        }
        double k = radius(x, z);
        return k > R ? Col.OUT : k >= R - 2.0 ? Col.WALL : k >= WELL + 1 ? Col.ROOM : k >= WELL ? Col.WELLWALL
                : k >= WELL - 2 ? Col.GALLERY : Col.COURT;
    }

    /** True for a column in a casemate's radial wall. */
    private static boolean radial(int x, int z) {
        double px = x + 0.5, pz = z + 0.5;
        for (int j = 0; j < 12; j++) {
            double a = Math.toRadians(30 * j + 15);
            double along = px * Math.cos(a) + pz * Math.sin(a), across = -px * Math.sin(a) + pz * Math.cos(a);
            if (along > 0 && Math.abs(across) < 0.6) {
                return true;
            }
        }
        return false;
    }

    private static int[] at(double deg, double rad) {
        double a = Math.toRadians(deg);
        return new int[] {(int) Math.floor(rad * Math.cos(a) + 1e-6), (int) Math.floor(rad * Math.sin(a) + 1e-6)};
    }

    private static String face(double deg) {
        double a = Math.floorMod(Math.round(deg), 360);
        return a >= 315 || a < 45 ? "east" : a < 135 ? "south" : a < 225 ? "west" : "north";
    }

    private static int[] dir(String facing) {
        return switch (facing) {
            case "east" -> new int[] {1, 0};
            case "south" -> new int[] {0, 1};
            case "west" -> new int[] {-1, 0};
            default -> new int[] {0, -1};
        };
    }

    private boolean free(int x, int y, int z) {
        String b = f.get(x, y, z);
        return b == null || b.equals("air");
    }

    // ------------------------------------------------------------------------------------- shell

    /**
     * The drum: its footing of rubble from the sea bed, the lower deck's floor, the outer wall's
     * panels course by course (weeded granite in the splash zone, granite, the armour tier with its
     * ports and the rust run down under them, the white parapet), and the rip-rap round its foot.
     */
    private void drum() {
        for (int x = -R - 4; x <= R + 4; x++) {
            for (int z = -R - 4; z <= R + 4; z++) {
                Col c = col(x, z);
                int bed = seabed(x, z);
                if (c == Col.OUT) {
                    // the rip-rap the fort stands in, boulders heaped round its foot under the water
                    double k = radius(x, z);
                    if (k < R + 4 && r.nextDouble() < 0.85) {
                        int top = Math.min(Ships.SEA - 2, bed + (k < R + 2 ? 2 : 1));
                        for (int y = bed - 1; y <= top; y++) {
                            f.set(x, y, z, r.nextDouble() < 0.3 ? "om_granite_moss" : "pt_stone_rough");
                        }
                    }
                    continue;
                }
                for (int y = Math.min(bed - 2, LOW - 2); y < LOW; y++) {
                    f.set(x, y, z, c == Col.RING ? panel(x, y, z) : "pt_stone_rough");
                }
                if (c == Col.RING) {
                    for (int y = LOW; y <= ROOF + 1; y++) {
                        f.set(x, y, z, panel(x, y, z));
                    }
                } else {
                    f.set(x, LOW, z, c == Col.COURT || c == Col.GALLERY ? (r.nextDouble() < 0.3 ? "pt_stone_rough" : "pt_stone_flags")
                            : r.nextDouble() < 0.35 ? "bunk_floor_dirty" : "om_granite_b");
                }
            }
        }
    }

    /** The outer wall's panel at (x, y, z), a ring cell. */
    private String panel(int x, int y, int z) {
        Round.Cell c = ring.get(key(x, z));
        String mat;
        if (y <= Ships.SEA + 1) {
            mat = "hfgw";
        } else if (y <= 0) {
            mat = "hfg";
        } else if (y < ROOF) {
            boolean pt = port(x, z);
            mat = !pt ? "hfa" : y == 4 ? "hfpl" : y == 5 ? "hfpu" : y < 4 ? "hfas" : "hfa";
        } else {
            mat = "lhw";
        }
        return "rd_" + mat + "_" + c.suffix() + "[facing=" + c.facing() + "]";
    }

    /** The walls and floors inside the drum, deck by deck. */
    private void walls() {
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                Col c = col(x, z);
                switch (c) {
                    case WALL -> {
                        for (int y = LOW + 1; y <= ROOF; y++) {
                            boolean embrasure = port(x, z) && (y == 4 || y == 5);
                            f.set(x, y, z, embrasure ? "air" : y == ROOF ? "bunk_concrete" : "hf_granite");
                        }
                    }
                    case ROOM -> {
                        boolean wall = radial(x, z);
                        double k = radius(x, z);
                        boolean door = wall && k >= 12.6 && k < 14.6;
                        for (int y = LOW + 1; y <= ROOF; y++) {
                            String b = y == GUN ? (r.nextDouble() < 0.3 ? "om_granite_b" : "om_granite")
                                    : y == ROOF ? (r.nextDouble() < 0.25 ? "bunk_concrete_damp" : "bunk_concrete") : "air";
                            if (wall && y != GUN && y != ROOF) {
                                boolean gap = door && (y == LOW + 1 || y == LOW + 2 || y == GUN + 1 || y == GUN + 2);
                                b = gap ? "air" : "hf_granite";
                            }
                            f.set(x, y, z, b);
                        }
                    }
                    case WELLWALL -> {
                        boolean door = offBay(x, z) < 0.6;
                        for (int y = LOW + 1; y <= ROOF; y++) {
                            boolean gap = door && (y == LOW + 1 || y == LOW + 2 || y == GUN + 1 || y == GUN + 2);
                            f.set(x, y, z, gap ? "air" : "hf_granite");
                        }
                    }
                    case GALLERY, COURT -> {
                        for (int y = LOW + 1; y <= ROOF + 3; y++) {
                            f.set(x, y, z, c == Col.GALLERY && y == GUN ? "deck_steel" : "air");
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /**
     * The light well: the gallery's rail, the steel stair from the well's floor up to the gallery
     * (two wide, a short bridge out to it), the ladder on up the well's wall to the roof, the drain
     * in the middle and the puddles round it.
     */
    private void well() {
        for (Round.Cell c : Round.ring(0, 0, WELL - 2.1)) {
            if (c.x() >= -4 && c.x() <= 3 && (c.z() == 0 || c.z() == -1)) {
                continue;  // the bridge from the stair
            }
            f.set(c.x(), GUN, c.z(), "deck_steel");
            f.set(c.x(), GUN + 1, c.z(), "rd_rail_" + c.suffix() + "[facing=" + c.facing() + "]");
        }
        for (int s = 0; s <= 4; s++) {
            for (int z = -1; z <= 0; z++) {
                f.set(-3 + s, LOW + 1 + s, z, "steel_stairs[facing=east]");
            }
        }
        for (int x = 2; x <= 3; x++) {
            for (int z = -1; z <= 0; z++) {
                f.set(x, GUN, z, "deck_steel");
            }
        }
        for (int y = GUN + 1; y <= ROOF + 1; y++) {
            f.set(-6, y, -1, "bunk_ladder[facing=east]");
        }
        f.set(-1, LOW, 2, "bunk_floor_drain");
        for (int i = 0; i < 4; i++) {
            int[] c = at(r.nextDouble() * 360, 1 + r.nextDouble() * 2.5);
            if (free(c[0], LOW + 1, c[1])) {
                f.set(c[0], LOW + 1, c[1], "bunk_puddle");
            }
        }
    }

    /**
     * The roof: the rail round the well's opening (open at the ladder), the small light on its
     * tower at the seaward rim (red and white, the lantern still turning on its solar cell), the
     * water butts they caught the rain in, the vents and a sandbagged lookout.
     */
    private void roof() {
        for (Round.Cell c : Round.ring(0, 0, WELL + 0.4)) {
            if (Math.abs(c.x() + 6.5) < 1.6 && Math.abs(c.z() + 0.5) < 1.6) {
                continue;
            }
            f.set(c.x(), ROOF + 1, c.z(), "rd_rail_" + c.suffix() + "[facing=" + c.facing() + "]");
        }
        // the light
        double lx = -16.5, lz = 0.5;
        for (int y = ROOF + 1; y <= ROOF + 5; y++) {
            Round.wall(f, lx, lz, 1.4, y, y, (y - ROOF - 1) / 2 % 2 == 0 ? "lhr" : "lhw", 0);
            f.set(-17, y, 0, "air");
        }
        Round.wall(f, lx, lz, 1.4, ROOF + 6, ROOF + 6, "glazex", 0);
        f.set(-17, ROOF + 6, 0, "rd_lens_lit");
        int top = Round.roof(f, lx, lz, 1.9, "sm", ROOF + 7, "lhcap");
        f.set(-17, top, 0, "rd_finial");

        // the water butts, the vents, the lookout over the landing
        String[] butts = {"bunk_drum_water", "bunk_drum_water", "rg_barrel_rust"};
        for (int i = 0; i < 3; i++) {
            int[] c = at(120 + i * 8, 15);
            f.set(c[0], ROOF + 1, c[1], butts[i]);
        }
        for (double a : new double[] {60, 210, 300}) {
            int[] c = at(a, 11);
            f.set(c[0], ROOF + 1, c[1], "bunk_vent_stack");
            f.set(c[0], ROOF + 2, c[1], "bunk_vent_cap");
        }
        for (double a = -14; a <= 14; a += 4) {
            int[] c = at(a, R - 3);
            f.set(c[0], ROOF + 1, c[1], "sandbags[facing=" + face(a) + "]");
        }
        int[] seat = at(0, R - 5);
        f.set(seat[0], ROOF + 1, seat[1], "bunk_chair[facing=" + face(180) + "]");
    }

    /**
     * The landing on the shore side: the sally port through the wall (its steel doors shut on a
     * sealed fort), the steel stage on its piles, its rails, the ladder down into the water at its
     * end, the cleats and a fender.
     */
    private void landing() {
        for (int x = 19; x <= R + 1; x++) {
            for (int z = -1; z <= 0; z++) {
                Col c = col(x, z);
                if (c == Col.RING || c == Col.WALL) {
                    f.set(x, LOW, z, "pt_stone_flags");
                    f.set(x, LOW + 1, z, "air");
                    f.set(x, LOW + 2, z, "air");
                }
            }
        }
        int door = 20;
        while (col(door, 0) != Col.WALL && door < R) {
            door++;
        }
        boolean open = state != Bunker.State.SEALED;
        for (int z = -1; z <= 0; z++) {
            String hinge = z == -1 ? "left" : "right";
            f.set(door, LOW + 1, z, "bunk_door_steel[facing=west,half=lower,hinge=" + hinge + ",open=" + open + "]");
            f.set(door, LOW + 2, z, "bunk_door_steel[facing=west,half=upper,hinge=" + hinge + ",open=" + open + "]");
        }

        int x0 = R, x1 = R + 6;
        for (int x = x0; x <= x1; x++) {
            for (int z = -3; z <= 2; z++) {
                if (col(x, z) == Col.OUT) {
                    f.set(x, LOW, z, "deck_steel");
                }
            }
        }
        for (int x : new int[] {x0, x0 + 3, x1}) {
            for (int z : new int[] {-3, 2}) {
                for (int y = seabed(x, z) - 1; y < LOW; y++) {
                    f.set(x, y, z, "wf_pile");
                }
            }
        }
        for (int x = x0 + 1; x <= x1; x++) {
            f.set(x, LOW + 1, -3, "wf_rail_iron[facing=north]");
            f.set(x, LOW + 1, 2, "wf_rail_iron[facing=south]");
        }
        f.set(x1, LOW + 1, -2, "wf_rail_iron[facing=east]");
        f.set(x1, LOW + 1, 1, "wf_rail_iron[facing=east]");
        for (int y = Ships.SEA - 2; y <= LOW + 1; y++) {
            f.set(x1 + 1, y, 0, "bunk_ladder[facing=east]");
        }
        f.set(x0 + 2, LOW + 1, -2, "wf_cleat[axis=x]");
        f.set(x0 + 2, LOW + 1, 1, "wf_cleat[axis=x]");
        f.set(x1 + 1, LOW, -2, "wf_fender[facing=east]");
        if (state == Bunker.State.BREACHED) {
            f.set(x0 + 1, LOW + 1, 0, "blood_splat");
        }
    }

    // ------------------------------------------------------------------------------------- inside

    /** Puts a block at a bearing and radius in a casemate, if the cell is clear and floored. */
    private boolean put(int bay, double off, double rad, int y, String block, char turn) {
        double th = bay * 30 + off;
        int[] c = at(th, rad);
        if (!free(c[0], y, c[1]) || free(c[0], y - 1, c[1]) || radial(c[0], c[1])) {
            return false;
        }
        String b = block;
        if (turn != '-' && !b.equals("paper") && !b.equals("bunk_grit")) {
            b += "[facing=" + face(th + switch (turn) {
                case 'o' -> 0;
                case 'i' -> 180;
                case 'l' -> 90;
                default -> -90;
            }) + "]";
        }
        f.set(c[0], y, c[1], b);
        return true;
    }

    /** A two-block piece (a cot, a bed) laid out along its facing, head first. */
    private void pair(int bay, double off, double rad, int y, String head, String foot, char turn) {
        double th = bay * 30 + off;
        int[] c = at(th, rad);
        String fc = face(th + (turn == 'o' ? 0 : turn == 'i' ? 180 : turn == 'l' ? 90 : -90));
        int[] dv = dir(fc);
        int fx = c[0] + dv[0], fz = c[1] + dv[1];
        if (!free(c[0], y, c[1]) || !free(fx, y, fz) || free(c[0], y - 1, c[1]) || free(fx, y - 1, fz)
                || radial(c[0], c[1]) || radial(fx, fz)) {
            return;
        }
        f.set(c[0], y, c[1], head + "[facing=" + fc + "]");
        f.set(fx, y, fz, foot + "[facing=" + fc + "]");
    }

    /** A stocked container, or on a picked-over fort often only its litter. */
    private String stock(String block) {
        if (state != Bunker.State.SEALED && r.nextDouble() < 0.45) {
            return r.nextBoolean() ? "paper" : "bunk_grit";
        }
        return block;
    }

    /**
     * The gun deck, made their home: the watch room over the landing (the radio, the chart, the
     * view of the shore), the sick bay, the kitchen across the well from it, two old guns still run
     * out at their ports with their shot stacked by them, and cots in every other room; sandbags
     * under the ports, a lamp in each room, and the mess of people living close.
     */
    private void casemates() {
        int y = GUN + 1;
        boolean lit = state == Bunker.State.SEALED;
        for (int bay = 0; bay < 12; bay++) {
            switch (bay) {
                case 0 -> {
                    put(bay, 0, 18.4, y, "bunk_radio_desk", 'i');
                    put(bay, 4, 17.2, y, "bunk_chair", 'o');
                    put(bay, 0, 12.5, y, "bunk_map_table", '-');
                    put(bay, -9, 15, y, "bunk_footlocker", 'l');
                    pair(bay, 9, 9.5, y, "sc_cot_head", "sc_cot_foot", 'o');
                }
                case 3, 9 -> {
                    // the old gun on its slide, raised on its platform so the barrel lies in the port
                    for (double rad : new double[] {18, 19}) {
                        int[] c = at(bay * 30, rad);
                        f.set(c[0], y, c[1], "bunk_plate");
                    }
                    int[] b = at(bay * 30, 18), m = at(bay * 30, 19);
                    f.set(b[0], y + 1, b[1], "hf_gun[facing=" + face(bay * 30) + "]");
                    f.set(m[0], y + 1, m[1], "hf_gun_muzzle[facing=" + face(bay * 30) + "]");
                    put(bay, 0, 16.4, y, "hf_racer", 'o');
                    put(bay, -6, 17.2, y, "hf_racer", 'o');
                    put(bay, 6, 17.2, y, "hf_racer", 'o');
                    put(bay, -9, 14, y, "hf_shot", '-');
                    put(bay, 9, 14, y, "hf_shot", '-');
                    pair(bay, -9, 9, y, "sc_cot_head", "sc_cot_foot", 'o');
                }
                case 5 -> {
                    pair(bay, -9, 10, y, "bunk_medbed_head", "bunk_medbed_foot", 'o');
                    pair(bay, 9, 10, y, "bunk_medbed_head", "bunk_medbed_foot", 'o');
                    put(bay, -9, 15.5, y, stock("bunk_medcab"), 'l');
                    put(bay, 0, 13, y, "bunk_iv_stand", '-');
                    put(bay, 9, 15.5, y, stock("bunk_first_aid"), 'r');
                    if (state != Bunker.State.SEALED) {
                        put(bay, 0, 17, y, "bunk_bodybag", 'o');
                    }
                }
                case 6 -> {
                    put(bay, 0, 18.4, y, "home_wood_stove", 'i');
                    put(bay, -9, 16, y, "bunk_counter", 'l');
                    put(bay, -9, 14.5, y, "bunk_sink", 'l');
                    put(bay, 9, 16, y, stock("bunk_tins"), 'r');
                    put(bay, 9, 14.5, y, stock("bunk_crate_ration"), 'r');
                    put(bay, 0, 11.5, y, "bunk_mess_table[axis=" + (Math.abs(Math.cos(Math.toRadians(bay * 30))) > 0.7 ? "z" : "x") + "]", '-');
                    put(bay, -5, 11.5, y, "bunk_chair", 'l');
                    put(bay, 5, 11.5, y, "bunk_chair", 'r');
                }
                default -> {
                    pair(bay, -9, 9, y, "sc_cot_head", "sc_cot_foot", 'o');
                    pair(bay, 9, 9, y, "sc_cot_head", "sc_cot_foot", 'o');
                    pair(bay, -8, 13.5, y, "sc_cot_head", "sc_cot_foot", 'o');
                    put(bay, 8, 14.5, y, stock(r.nextBoolean() ? "sc_cases_water" : "bunk_crate_ration"), 'r');
                    put(bay, 4, 17.5, y, stock("bunk_footlocker"), 'i');
                    put(bay, -4, 17.5, y, r.nextBoolean() ? "bunk_jerry_cans" : "cellar_barrel", 'i');
                }
            }
            if (bay != 3 && bay != 9) {
                put(bay, 0, 19, y, "sandbags", 'o');
            }
            if (bay != 5 && bay != 6) {
                living(bay, y);
            }
            int[] lamp = at(bay * 30, 14);
            if (!radial(lamp[0], lamp[1]) && free(lamp[0], ROOF - 1, lamp[1])) {
                f.set(lamp[0], ROOF - 1, lamp[1], lit && bay % 2 == 0 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
            }
            mess(bay, y);
        }
    }

    /**
     * What people living in a room leave about it: a mattress on the floor, their clothes and
     * bags, a bucket, candles, books, the bottles; a radio in the watch room.
     */
    private void living(int bay, int y) {
        String[][] things = {{"home_mattress_floor", "o"}, {"home_clothes_pile", "-"}, {"fit_laundry_bag", "i"},
            {"sc_bucket", "l"}, {"home_candles", "-"}, {"home_books_stack_low", "r"}, {"home_bottles_low", "l"},
            {"trash_bag", "i"}, {"home_clothes_pile", "-"}};
        int n = 4 + r.nextInt(3);
        for (int i = 0; i < n; i++) {
            String[] t = things[r.nextInt(things.length)];
            put(bay, (r.nextDouble() - 0.5) * 20, 8.5 + r.nextDouble() * 9.5, y, t[0], t[1].charAt(0));
        }
        if (bay == 0) {
            put(bay, -4, 17.2, y, "home_radio", 'o');
        }
    }

    /** Litter on a deck's floor in a casemate: paper, grit, the cans; blood where the dead got in. */
    private void mess(int bay, int y) {
        for (int i = 0; i < 4; i++) {
            double off = (r.nextDouble() - 0.5) * 22, rad = 9 + r.nextDouble() * 9;
            double v = r.nextDouble();
            String b = v < 0.35 ? "paper" : v < 0.6 ? "bunk_grit" : v < 0.75 ? "zs_cans" : state == Bunker.State.BREACHED ? "blood_splat" : "bunk_bootprints";
            put(bay, off, rad, y, b, b.equals("bunk_bootprints") ? 'o' : '-');
        }
    }

    /**
     * The lower deck, the old magazines and shell rooms: the guard post inside the sally port, their
     * stores racked round the rooms, the wash room, the generator, the water and the coal, cots for
     * those who came last. Under the furthest one (bay 6, across the well from the landing) the
     * War Department's hatch is in the floor among the old shot.
     */
    private void magazines() {
        int y = LOW + 1;
        for (int bay = 0; bay < 12; bay++) {
            switch (bay) {
                case 0 -> {
                    put(bay, -10, 17, y, "sandbags", 'o');
                    put(bay, 10, 17, y, "sandbags", 'o');
                    put(bay, -8, 13, y, "bunk_chair", 'r');
                    put(bay, 9, 12, y, "bunk_extinguisher", 'r');
                }
                case 1, 11 -> {
                    put(bay, -9, 16, y, stock("bunk_rack_boxes"), 'l');
                    put(bay, -9, 13, y, stock("bunk_rack_crates"), 'l');
                    put(bay, 9, 16, y, stock("cellar_shelf_cans"), 'r');
                    put(bay, 9, 13, y, stock("bunk_rack_drums"), 'r');
                    put(bay, 0, 18, y, stock("bunk_crate_ration"), 'i');
                }
                case 2 -> {
                    put(bay, -9, 15, y, "bunk_sink", 'l');
                    put(bay, -9, 12.5, y, "bunk_sink", 'l');
                    put(bay, 9, 15, y, "bunk_shower", 'r');
                    put(bay, 0, 18, y, "bunk_drum_water", '-');
                }
                case 4 -> {
                    put(bay, -6, 17, y, "bunk_gen_engine", 'l');
                    put(bay, 0, 17, y, "bunk_gen_alternator", 'l');
                    put(bay, 9, 14, y, stock("bunk_jerry_cans"), 'r');
                    put(bay, 9, 16, y, stock("bunk_jerry_cans"), 'r');
                }
                case 5 -> {
                    put(bay, -9, 15, y, "bunk_logpile", 'l');
                    put(bay, 9, 15, y, "cellar_barrel", 'r');
                    put(bay, 0, 18, y, "cellar_barrel_side", 'o');
                }
                case 6 -> {
                    put(bay, -9, 16, y, "hf_shot", '-');
                    put(bay, 9, 16, y, "hf_shot", '-');
                    put(bay, -9, 12.5, y, stock("bunk_crate_ammo"), 'l');
                    put(bay, 0, 18.5, y, "hf_shot", '-');
                }
                case 10 -> {
                    for (double off : new double[] {-9, -4, 4, 9}) {
                        put(bay, off, 17.5, y, "bunk_drum_water", '-');
                    }
                    put(bay, 0, 13, y, "bunk_tank", '-');
                }
                default -> {
                    pair(bay, -9, 10, y, "sc_cot_head", "sc_cot_foot", 'o');
                    pair(bay, 9, 10, y, "sc_cot_head", "sc_cot_foot", 'o');
                    put(bay, 0, 17.5, y, stock("bunk_footlocker"), 'i');
                }
            }
            int[] lamp = at(bay * 30, 14);
            if (!radial(lamp[0], lamp[1]) && free(lamp[0], GUN - 1, lamp[1])) {
                f.set(lamp[0], GUN - 1, lamp[1], state == Bunker.State.SEALED && bay % 3 == 0 ? "bunk_lamp_hang_lit" : "bunk_lamp_hang");
            }
            mess(bay, y);
        }
    }

    // ------------------------------------------------------------------------------------- bunker

    /** The antechamber's shaft in the bunker's frame (as HatchSite's and SiloBunker's). */
    private static final int SX = -1, SZ = -5;

    /**
     * The bunker under the far magazine: its floor well under the lowest sea bed round it, turned
     * the first of the four ways it stays inside the place. The hatch is in the magazine's floor.
     */
    private void bunker() {
        int[] h = at(180, 15);
        int hx = h[0], hz = h[1];
        int low = Integer.MAX_VALUE;
        for (int x = -HALF + 2; x <= HALF - 2; x += 3) {
            for (int z = -HALF + 2; z <= HALF - 2; z += 3) {
                low = Math.min(low, seabed(x, z));
            }
        }
        int floor = Math.min(low, LOW - 12) - 9;
        long core = r.nextLong();
        int[] order = {2, 1, 3, 0};
        for (int q : order) {
            int[] s = turn(q, SX, SZ);
            Placer probe = f.turned(hx - s[0], 0, hz - s[1], q);
            int[] box = new Bunker(probe, new SplittableRandom(core), Bunker.Kind.ARMY, state).bounds();
            if (!inside(probe, box[0] - 1, SZ - 2, box[2] + 1, box[3] + 1)) {
                continue;
            }
            Placer bp = f.turned(hx - s[0], floor, hz - s[1], q);
            Bunker bunker = new Bunker(bp, new SplittableRandom(core), Bunker.Kind.ARMY, state);
            HatchSite.antechamber(bp, r, LOW - floor, state);
            bunker.build();
            f.mark("bunker " + state.name().toLowerCase(Locale.ROOT) + " fort", hx, LOW + 1, hz);
            bp.mark("bunker door", 0, 1, -2);
            if (state != Bunker.State.SEALED) {
                f.set(hx + 1, LOW + 1, hz, "bunk_bootprints[facing=" + face(0) + "]");
            }
            return;
        }
    }

    private static int[] turn(int q, int x, int z) {
        return switch (q & 3) {
            case 0 -> new int[] {x, z};
            case 1 -> new int[] {-z, x};
            case 2 -> new int[] {-x, -z};
            default -> new int[] {z, -x};
        };
    }

    private boolean inside(Placer bp, int x0, int z0, int x1, int z1) {
        for (int[] k : new int[][] {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) {
            int[] at = bp.world(k[0], k[1]);
            if (at[0] < 1 || at[1] < 1 || at[0] > w - 2 || at[1] > d - 2) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------- people

    /**
     * The people who sheltered here: most of them still about the fort, a couple up on the roof,
     * the rest in the casemates and the magazines; and the ones who went down and stayed down.
     */
    private void people() {
        int dead = state == Bunker.State.BREACHED ? 4 : state == Bunker.State.LOOTED ? 2 : 1;
        int[] roofAt = at(140, 12);
        f.vehicle(roofAt[0] + 0.5, ROOF + 1, roofAt[1] + 0.5, 0, "!walkers:civilian;2;4", r.nextLong());
        for (int bay : new int[] {2, 7, 10}) {
            int[] c = at(bay * 30, 12);
            f.vehicle(c[0] + 0.5, GUN + 1, c[1] + 0.5, 0, "!walkers:civilian;" + (1 + r.nextInt(2)) + ";-3", r.nextLong());
        }
        for (int bay : new int[] {4, 8}) {
            int[] c = at(bay * 30, 12);
            f.vehicle(c[0] + 0.5, LOW + 1, c[1] + 0.5, 0, "!walkers:civilian;1;-3", r.nextLong());
        }
        for (int i = 0; i < dead; i++) {
            int bay = r.nextInt(12);
            boolean upper = r.nextBoolean();
            int[] c = at(bay * 30 + (r.nextDouble() - 0.5) * 16, 10 + r.nextDouble() * 6);
            int y = upper ? GUN + 1 : LOW + 1;
            if (free(c[0], y, c[1]) && !radial(c[0], c[1])) {
                f.vehicle(c[0] + 0.5, y, c[1] + 0.5, (float) (r.nextDouble() * 360), "!body:civilian;0;0", r.nextLong());
                if (state == Bunker.State.BREACHED) {
                    f.set(c[0], y, c[1], "blood_splat");
                }
            }
        }
    }
}
