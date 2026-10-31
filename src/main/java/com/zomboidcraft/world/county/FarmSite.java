package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;

/**
 * A farm out on the county road, in five kinds:
 * <ul>
 * <li>crop: the big red gambrel barn with its silos, a row of grain bins and the leg that fills
 * them, the machine shed, the farmhouse, a windmill, and corn, wheat and beans to the horizon;</li>
 * <li>dairy: the long free-stall barn and its milk house, blue silos, the bunker silo under its
 * tarp and tyres, calf hutches, the old barn, and pasture behind white fences;</li>
 * <li>horse_ranch: a log gate with the ranch's name, the ranch house, the horse barn with its
 * cupolas, a hay barn, the riding arena and round pen, paddocks of white board fence;</li>
 * <li>orchard: rows of apple trees heavy with fruit nobody picked, the packing shed full of
 * crates, the stand by the road, a pumpkin patch, the hives;</li>
 * <li>homestead: a log cabin, a small barn, the coop, the well and the outhouse, a kitchen
 * garden and a scarecrow, a pond, split rail fences.</li>
 * </ul>
 * The county road runs through east to west, two lanes with their lines; gravel roads go
 * north and south from it, jogging where they cross. Every farm has its outbreak story: graves
 * dug in the yard, a pyre where they burnt the dead, SOS cut into the corn, the barn barricaded.
 */
final class FarmSite extends Site {
    enum Crop { CORN, CORN_GREEN, WHEAT, BEANS, PUMPKINS, HAY, PASTURE, FALLOW, ORCHARD, PADDOCK, ARENA, GARDEN }

    /** A field: its box in place coordinates (fence on the edge), its crop, and which way its rows run. */
    private record Field(int x0, int z0, int x1, int z1, Crop crop, boolean alongX) {
        int w() {
            return x1 - x0 + 1;
        }

        int d() {
            return z1 - z0 + 1;
        }
    }

    private Kit kit;
    private Homes homes;
    private Kit.Lane main, north, south;
    private final List<Field> fields = new ArrayList<>();
    private final List<int[]> hedges = new ArrayList<>();
    /** How hard the farm was hit, 0 to 1. */
    private double hit;
    // the farmyard: its frame (z = 0 toward the main road), size, facing, the drive's x, what's taken
    private Placer yp;
    private int yw, yd, yFacing, ydx, yardEnd;
    private int[] yBox;
    private boolean[] taken;
    private boolean houseLeft;

    FarmSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        homes = new Homes(kit);
        hit = chance() * 0.7 + (chance() < 0.2 ? 0.3 : 0);
        boolean small = variant("homestead");
        roads(small ? Kit.Style.LANE : Kit.Style.COUNTY);
        kit.pave();
        plan();
        kit.pave();
        switch (p.variant) {
            case "dairy" -> dairy();
            case "horse_ranch" -> ranch();
            case "orchard" -> orchardYard();
            case "homestead" -> homestead();
            default -> cropFarm();
        }
        yardFinish();
        for (Field f : fields) {
            field(f);
        }
        hedgerows();
        roadside();
        kit.woods(0.5);
        kit.overgrow(0.85);
        kit.roadWeeds(0.3);
        homes.finish();
    }

    private double jitter(double amount) {
        return (chance() * 2 - 1) * amount;
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    // ------------------------------------------------------------------ roads and the plan

    private void roads(Kit.Style ms) {
        double[] W = kit.beyond(3), E = kit.beyond(1), N = kit.beyond(0), S = kit.beyond(2);
        double a = d * 0.05;
        main = kit.curve(ms, W[0], W[1], 8, W[1], w * 0.32, W[1] + jitter(a), w * 0.68, W[1] + jitter(a), w - 9, E[1], E[0], E[1]);
        // the gravel road crosses it, its two halves meeting the county road a little apart
        double jn = w / 2.0 + jitter(w * 0.1), js = w / 2.0 + jitter(w * 0.1);
        double zn = zAt(main, jn), zs = zAt(main, js);
        north = kit.curve(Kit.Style.GRAVEL, N[0], N[1], N[0], 8, jn, zn * 0.55, jn, zn);
        south = kit.curve(Kit.Style.GRAVEL, js, zs, js, zs + (d - zs) * 0.45, S[0], d - 9, S[0], S[1]);
    }

    /** The z of a lane where it passes x. */
    private static double zAt(Kit.Lane l, double x) {
        double best = 1e9, z = 0;
        for (double s = 0; s <= l.length(); s += 1) {
            double[] q = l.point(s);
            if (Math.abs(q[0] - x) < best) {
                best = Math.abs(q[0] - x);
                z = q[1];
            }
        }
        return z;
    }

    /** The x of a lane where it passes z. */
    private static double xAt(Kit.Lane l, double z) {
        double best = 1e9, x = 0;
        for (double s = 0; s <= l.length(); s += 1) {
            double[] q = l.point(s);
            if (Math.abs(q[1] - z) < best) {
                best = Math.abs(q[1] - z);
                x = q[0];
            }
        }
        return x;
    }

    /** True when the box is inside (off the very edge) and clear of roads by pad blocks. */
    private boolean clear(int x0, int z0, int x1, int z1, int pad) {
        if (x0 < 2 || z0 < 2 || x1 > w - 3 || z1 > d - 3) {
            return false;
        }
        return kit.free(Math.max(0, x0 - pad), Math.max(0, z0 - pad), Math.min(w - 1, x1 + pad), Math.min(d - 1, z1 + pad));
    }

    /** The biggest clear box grown out from (sx, sz), kept pad blocks off the roads; null when the seed isn't clear. */
    private int[] grow(int sx, int sz, int pad) {
        for (int t = 0; t < 12 && !clear(sx, sz, sx, sz, pad); t++) {
            sx += (int) jitter(8);
            sz += (int) jitter(8);
        }
        if (!clear(sx, sz, sx, sz, pad)) {
            return null;
        }
        int x0 = sx, z0 = sz, x1 = sx, z1 = sz;
        boolean moved = true;
        while (moved) {
            moved = false;
            if (clear(x0 - 1, z0, x0 - 1, z1, pad)) {
                x0--;
                moved = true;
            }
            if (clear(x1 + 1, z0, x1 + 1, z1, pad)) {
                x1++;
                moved = true;
            }
            if (clear(x0, z0 - 1, x1, z0 - 1, pad)) {
                z0--;
                moved = true;
            }
            if (clear(x0, z1 + 1, x1, z1 + 1, pad)) {
                z1++;
                moved = true;
            }
        }
        return new int[] {x0, z0, x1, z1};
    }

    private int[] yardSize() {
        return switch (p.variant) {
            case "dairy" -> new int[] {92, 74};
            case "horse_ranch" -> new int[] {86, 66};
            case "orchard" -> new int[] {72, 58};
            case "homestead" -> new int[] {52, 46};
            default -> new int[] {98, 74};
        };
    }

    /**
     * Lays out the farm on the land the roads leave: the yard in one corner against the county
     * road, its drive, then fields over the rest, split by farm tracks and hedgerows.
     */
    private void plan() {
        int[][] seeds = {{w / 4, d / 4}, {w * 3 / 4, d / 4}, {w / 4, d * 3 / 4}, {w * 3 / 4, d * 3 / 4}};
        int[][] quads = new int[4][];
        for (int q = 0; q < 4; q++) {
            quads[q] = grow(seeds[q][0], seeds[q][1], 3);
        }
        int yq = rng.nextInt(4);
        for (int t = 0; t < 4 && (quads[yq] == null || quads[yq][2] - quads[yq][0] < 36 || quads[yq][3] - quads[yq][1] < 30); t++) {
            yq = (yq + 1) & 3;
        }
        int[] Q = quads[yq];
        List<int[]> plots = new ArrayList<>();
        if (Q != null) {
            boolean northSide = yq < 2, westQuad = (yq & 1) == 0;
            int[] want = yardSize();
            yw = Math.min(want[0], Q[2] - Q[0] + 1);
            yd = Math.min(want[1], Q[3] - Q[1] + 1);
            boolean inner = chance() < 0.6;
            int bx0 = westQuad == inner ? Q[2] - yw + 1 : Q[0];
            int bz0 = northSide ? Q[3] - yd + 1 : Q[1];
            yBox = new int[] {bx0, bz0, bx0 + yw - 1, bz0 + yd - 1};
            yFacing = northSide ? 2 : 0;
            kit.claim(yBox[0], yBox[1], yBox[2], yBox[3], Kit.LOT);
            yp = Placer.forLot(out, bx0, 0, bz0, yw, yd, yFacing);
            taken = new boolean[yw * yd];
            // the drive in from the county road to the barnyard
            ydx = (int) (yw * (0.36 + chance() * 0.28));
            int[] gate = yp.world(ydx, 0), in = yp.world(ydx, 10);
            double zr = zAt(main, gate[0] + 0.5);
            kit.lane(Kit.Style.GRAVEL, gate[0] + 0.5, zr, in[0] + 0.5, in[1] + 0.5);
            // what's left of the yard's corner: beside it and behind it
            if (westQuad == inner) {
                plots.add(new int[] {Q[0], yBox[1], yBox[0] - 4, yBox[3]});
            } else {
                plots.add(new int[] {yBox[2] + 4, yBox[1], Q[2], yBox[3]});
            }
            plots.add(northSide ? new int[] {Q[0], Q[1], Q[2], yBox[1] - 4} : new int[] {Q[0], yBox[3] + 4, Q[2], Q[3]});
        }
        for (int q = 0; q < 4; q++) {
            if (q != yq && quads[q] != null) {
                plots.add(quads[q]);
            }
        }
        // split big plots into fields, with a farm track or a hedgerow between them
        List<int[]> boxes = new ArrayList<>();
        for (int[] r : plots) {
            split(r, boxes, 0);
        }
        Crop[] mix = crops();
        int arenaAt = -1;
        if (variant("horse_ranch")) {
            double best = 1e9;
            for (int i = 0; i < boxes.size(); i++) {
                int[] r = boxes.get(i);
                if (r[2] - r[0] >= 40 && r[3] - r[1] >= 28 && yBox != null) {
                    double dd = Math.hypot((r[0] + r[2]) / 2.0 - (yBox[0] + yBox[2]) / 2.0, (r[1] + r[3]) / 2.0 - (yBox[1] + yBox[3]) / 2.0);
                    if (dd < best) {
                        best = dd;
                        arenaAt = i;
                    }
                }
            }
        }
        for (int i = 0; i < boxes.size(); i++) {
            int[] r = boxes.get(i);
            if (r[2] - r[0] < 12 || r[3] - r[1] < 12) {
                continue;
            }
            Crop c = i == arenaAt ? Crop.ARENA : mix[rng.nextInt(mix.length)];
            fields.add(new Field(r[0], r[1], r[2], r[3], c, r[2] - r[0] >= r[3] - r[1]));
            kit.claim(r[0], r[1], r[2], r[3], Kit.LOT);
        }
    }

    /** The crops a kind of farm sows, as a bag to draw from. */
    private Crop[] crops() {
        return switch (p.variant) {
            case "dairy" -> new Crop[] {Crop.PASTURE, Crop.PASTURE, Crop.PASTURE, Crop.HAY, Crop.HAY, Crop.CORN_GREEN, Crop.CORN, Crop.FALLOW};
            case "horse_ranch" -> new Crop[] {Crop.PADDOCK, Crop.PADDOCK, Crop.PADDOCK, Crop.PASTURE, Crop.HAY, Crop.HAY};
            case "orchard" -> new Crop[] {Crop.ORCHARD, Crop.ORCHARD, Crop.ORCHARD, Crop.ORCHARD, Crop.PUMPKINS, Crop.PASTURE, Crop.HAY};
            case "homestead" -> new Crop[] {Crop.GARDEN, Crop.CORN, Crop.PASTURE, Crop.PASTURE, Crop.HAY, Crop.FALLOW, Crop.PUMPKINS};
            default -> new Crop[] {Crop.CORN, Crop.CORN, Crop.CORN, Crop.CORN_GREEN, Crop.WHEAT, Crop.WHEAT, Crop.BEANS, Crop.BEANS,
                    Crop.HAY, Crop.FALLOW, Crop.PUMPKINS};
        };
    }

    /** Cuts a plot into fields no longer than a farm's fields run, leaving a 5-wide strip between. */
    private void split(int[] r, List<int[]> outBoxes, int depth) {
        int fw = r[2] - r[0] + 1, fd = r[3] - r[1] + 1;
        if (fw < 12 || fd < 12) {
            return;
        }
        int max = variant("homestead") ? 44 : variant("horse_ranch") ? 60 : 84;
        if (fw <= max && fd <= max || depth > 3) {
            outBoxes.add(r);
            return;
        }
        boolean track = chance() < 0.55;
        if (fw >= fd) {
            int cut = r[0] + fw / 2 + (int) jitter(fw * 0.12);
            split(new int[] {r[0], r[1], cut - 3, r[3]}, outBoxes, depth + 1);
            split(new int[] {cut + 3, r[1], r[2], r[3]}, outBoxes, depth + 1);
            boolean roadSouth = zAt(main, cut) > r[3];
            if (track) {
                double z0 = roadSouth ? zAt(main, cut + 0.5) : r[1], z1 = roadSouth ? r[1] : zAt(main, cut + 0.5);
                kit.lane(Kit.Style.TRACK, cut + 0.5, z0, cut + 0.5, z1);
            } else {
                hedges.add(new int[] {cut, r[1], cut, r[3]});
            }
        } else {
            int cut = r[1] + fd / 2 + (int) jitter(fd * 0.12);
            split(new int[] {r[0], r[1], r[2], cut - 3}, outBoxes, depth + 1);
            split(new int[] {r[0], cut + 3, r[2], r[3]}, outBoxes, depth + 1);
            Kit.Lane cross = cut < zAt(main, (r[0] + r[2]) / 2.0) ? north : south;
            double xc = xAt(cross, cut + 0.5);
            boolean roadEast = xc > r[2];
            if (track) {
                kit.lane(Kit.Style.TRACK, roadEast ? xc : r[0], cut + 0.5, roadEast ? r[0] : xc, cut + 0.5);
            } else {
                hedges.add(new int[] {r[0], cut, r[2], cut});
            }
        }
    }

    // ------------------------------------------------------------------ the yard: placing things in it

    private boolean yardFree(int x0, int z0, int x1, int z1) {
        if (x0 < 1 || z0 < 1 || x1 > yw - 2 || z1 > yd - 2) {
            return false;
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                if (taken[z * yw + x]) {
                    return false;
                }
            }
        }
        return true;
    }

    private void take(int x0, int z0, int x1, int z1) {
        for (int x = Math.max(0, x0); x <= Math.min(yw - 1, x1); x++) {
            for (int z = Math.max(0, z0); z <= Math.min(yd - 1, z1); z++) {
                taken[z * yw + x] = true;
            }
        }
    }

    /**
     * A placer for something bw wide and bd deep (its own frame) facing f in the yard (0 the
     * road, 1 the yard's +x, 2 the back, 3 its -x), as near the yard spot (px, pz) as there's room
     * with pad blocks round it; the room is taken. Null when nothing fits.
     */
    private Placer put(int bw, int bd, int f, int px, int pz, int pad) {
        int fx = (f & 1) == 0 ? bw : bd, fz = (f & 1) == 0 ? bd : bw;
        int reach = Math.max(yw, yd);
        for (int r = 0; r < reach; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = px + dx, z = pz + dz;
                    if (yardFree(x - pad, z - pad, x + fx - 1 + pad, z + fz - 1 + pad)) {
                        take(x - pad, z - pad, x + fx - 1 + pad, z + fz - 1 + pad);
                        return frame(x, z, bw, bd, f);
                    }
                }
            }
        }
        return null;
    }

    /** The frame of something bw x bd facing f whose footprint starts at yard spot (x, z). */
    private Placer frame(int x, int z, int bw, int bd, int f) {
        return switch (f & 3) {
            case 0 -> yp.turned(x, 0, z, 0);
            case 1 -> yp.turned(x + bd - 1, 0, z, 1);
            case 2 -> yp.turned(x + bw - 1, 0, z + bd - 1, 2);
            default -> yp.turned(x, 0, z + bw - 1, 3);
        };
    }

    /** A lot for the farmhouse at yard spot (x, z), facing the road. */
    private Kit.Lot houseLot(int x, int z, int lw, int ld) {
        int[] a = yp.world(x, z), b = yp.world(x + lw - 1, z + ld - 1);
        return new Kit.Lot(Math.min(a[0], b[0]), Math.min(a[1], b[1]), lw, ld, yFacing);
    }

    /** Starts the yard: the drive and the barnyard kept open, the farmhouse on one side of the drive. */
    private void yardStart(House.Style[] styles, double[] weights, int lotW, int lotD) {
        yardEnd = Math.min(yd - 20, Math.max(16, yd / 2));
        take(ydx - 3, 0, ydx + 3, yardEnd);
        take(ydx - 10, yardEnd - 4, ydx + 10, yardEnd + 8);
        houseLeft = ydx > yw / 2 ? chance() < 0.8 : chance() < 0.2;
        int lw = Math.min(lotW, houseLeft ? ydx - 6 : yw - ydx - 7);
        if (lw >= 14) {
            int x = houseLeft ? ydx - 5 - lw : ydx + 5;
            int ld = Math.min(lotD, Math.min(yd - 8, yardEnd - 5));  // clear of the barnyard
            if (yardFree(x, 1, x + lw - 1, ld)) {
                take(x, 1, x + lw - 1, ld);
                Homes.Mix mix = new Homes.Mix(weights, 8, 0.45, 0.1, 0, 0.05, 0.6, 0.3, 0.85, false, false);
                homes.home(houseLot(x, 1, lw, ld), styles[rng.nextInt(styles.length)], mix);
            }
        }
    }

    /** The farmhouse's styles: weights in House.Style order (RANCH ... MANSION). */
    private static double[] weights(double... wts) {
        return wts;
    }

    /** The big barn with its lean-to and silos beside it, its doors toward the barnyard. */
    private int[] barnComplex(int bw, int bd, String wall, String roof, boolean gambrel, Farmstead.Use use, int silos,
                              Farmstead.Silo kind, boolean leanTo, int px, int pz) {
        int sr = 3;
        int left = silos > 0 ? 2 * sr + 6 : 0, right = leanTo ? 6 : 0;
        int tw = left + bw + right, td = Math.max(bd, silos * (2 * sr + 6)) + 4;
        Placer f = put(tw, td, 0, px, pz, 2);
        if (f == null) {
            if (silos == 0 && !leanTo && bd <= 12) {
                return null;
            }
            // no room: a shorter barn on its own still beats an empty barnyard
            return barnComplex(Math.min(bw, 14), Math.max(12, bd * 3 / 4), wall, roof, gambrel, use, 0, kind, false, px, pz);
        }
        Placer bp = f.shifted(left, 0, 4);
        Farmstead fs = new Farmstead(bp, rng, hit);
        fs.barn(bw, bd, wall, roof, gambrel, use);
        if (leanTo) {
            fs.leanTo(bw - 1, 1, 3, bd - 4, 5, bw >= 16 ? 5 : 4, any(Farmstead.TIN));
        }
        for (int i = 0; i < silos; i++) {
            int h = 18 + rng.nextInt(9);
            fs.silo(-sr - 3, bd - sr - 2 - i * (2 * sr + 6), sr, h, kind);
        }
        // the barnyard apron in front of the doors
        for (int x = bw / 2 - 4; x <= bw / 2 + 3; x++) {
            for (int z = -4; z < 0; z++) {
                bp.set(x, 0, z, any("minecraft:gravel", "minecraft:coarse_dirt", "minecraft:mud", "minecraft:packed_mud"));
            }
        }
        return new int[] {tw, td};
    }

    /** A yard light: a tall pole over the barnyard, its lamp still burning or not. */
    private void yardLight(int x, int z) {
        for (int y = 1; y <= 7; y++) {
            yp.set(x, y, z, "pole_wood");
        }
        yp.set(x, 7, z + 1, "streetlight_arm[facing=south]");
        chance();  // the old dead-bulb roll, kept so everything after it lands where it did: every lamp works now
        yp.set(x, 7, z + 2, "streetlight_head[facing=south]");
        take(x, z, x, z);
    }

    /** Hay stacked in a stepped pile under its tarp (the tp_ kit), the tarp blown half off. */
    private void hayStack(Placer q, int sw, int sd) {
        for (int y = 1; y <= 3; y++) {
            for (int x = y - 1; x < sw - y + 1; x++) {
                for (int z = 0; z < sd; z++) {
                    if (y < 3) {
                        q.set(x, y, z, "fm_bales[axis=z]");
                        continue;
                    }
                    // the top course under the tarp, roped and weighed with tyres, blown half off in places
                    double r = chance();
                    q.set(x, y, z, r < 0.2 ? "fm_bales_low[axis=z]" : r < 0.35 ? "tp_bales_tyre[axis=z]"
                            : r < 0.5 ? "tp_bales_blown[facing=north]" : "tp_bales[axis=z]");
                }
            }
        }
    }

    /** A row of pines along the back of the yard: the windbreak. */
    private void windbreak() {
        for (int x = 2; x < yw - 2; x += 5 + rng.nextInt(2)) {
            int z = yd - 3 - rng.nextInt(2);
            if (yardFree(x - 1, z - 1, x + 1, Math.min(yd - 2, z + 1))) {
                int[] at = yp.world(x, z);
                kit.tree(chance() < 0.8 ? Trees.Kind.PINE : Trees.Kind.OAK, at[0], at[1]);
                take(x - 1, z - 1, x + 1, z + 1);
            }
        }
    }

    /** The outbreak's marks in the yard: graves, a pyre, a burnt-out car. */
    private void yardStory() {
        if (chance() < 0.55) {
            Placer g = put(9, 4, 0, houseLeft ? 4 : yw - 14, yardEnd + 10, 1);
            if (g != null) {
                new Farmstead(g, rng, hit).graves(1, 0, 2 + rng.nextInt(3));
            }
        }
        if (chance() < 0.35 + hit * 0.3) {
            Placer g = put(9, 9, 0, ydx + 8, yardEnd + 4, 1);
            if (g != null) {
                new Farmstead(g, rng, hit).pyre(4, 4, 3);
            }
        }
        if (chance() < 0.5) {
            yp.vehicle(ydx + (chance() < 0.5 ? -6 : 6), 1, yardEnd + 2, (float) (chance() * 360), "pickup,car", rng.nextLong());
        }
    }

    /** Ends the yard: gravel over the barnyard, lawn over the rest, the drive's surface. */
    private void yardFinish() {
        if (yp == null) {
            return;
        }
        for (int x = Math.max(0, ydx - 12); x <= Math.min(yw - 1, ydx + 12); x++) {
            for (int z = 8; z <= Math.min(yd - 1, yardEnd + 10); z++) {
                if (yp.get(x, 0, z) == null && Math.abs(x - ydx) + Math.max(0, z - yardEnd - 6) * 2 < 14) {
                    yp.set(x, 0, z, any("minecraft:gravel", "minecraft:gravel", "minecraft:coarse_dirt", "minecraft:packed_mud", "minecraft:mud"));
                    if (chance() < 0.04) {
                        yp.set(x, 1, z, any("straw_floor", "minecraft:short_grass", "tire[axis=x]", "blood_splat"));
                    }
                }
            }
        }
        for (int z = 0; z < yardEnd; z++) {
            for (int x = ydx - 1; x <= ydx + 1; x++) {
                if (yp.get(x, 0, z) == null) {
                    yp.set(x, 0, z, chance() < 0.8 ? "minecraft:gravel" : "minecraft:coarse_dirt");
                }
            }
        }
        Kit.lawn(yp, rng, 0, 0, yw - 1, yd - 1, 0.9);
    }

    // ------------------------------------------------------------------ the five kinds of yard

    private void cropFarm() {
        if (yp == null) {
            return;
        }
        yardStart(new House.Style[] {House.Style.FARMHOUSE, House.Style.FARMHOUSE, House.Style.COLONIAL, House.Style.RANCH},
                weights(0.2, 0.15, 0, 0.65, 0, 0, 0, 0), 26, 28);
        // the big barn at the back of the barnyard, facing the drive
        int bw = chance() < 0.5 ? 16 : 18;
        String wall = chance() < 0.75 ? "barn_red" : "barn_grey";
        String roof = any("minecraft:deepslate_tile", "minecraft:dark_oak", Farmstead.TIN[2], "minecraft:cobbled_deepslate");
        int bx = houseLeft ? ydx + 2 : ydx - bw - 12;
        barnComplex(bw, 30 + rng.nextInt(6), wall, roof, true, Farmstead.Use.STALLS, 2, Farmstead.Silo.STAVE, true, bx, yardEnd + 6);
        // grain bins in a row with their leg, on the far side of the barnyard
        int n = 3 + rng.nextInt(2), r = 4 + rng.nextInt(2), h = 8 + rng.nextInt(3);
        int gw = n * (2 * r + 3) + 2, gd = 2 * r + 9;
        Placer g = put(gw, gd, 0, houseLeft ? yw - gw - 2 : 2, yardEnd + 4, 2);
        if (g != null) {
            Farmstead fs = new Farmstead(g, rng, hit);
            int[][] tops = new int[n][];
            for (int i = 0; i < n; i++) {
                int cx = r + 2 + i * (2 * r + 3), cz = r + 2;
                int top = fs.grainBin(cx, cz, r, h);
                tops[i] = new int[] {cx, top, cz};
            }
            fs.grainLeg(r + 2 + r + 1 + 1, 2 * r + 5, h + 10, tops);
        }
        // the machine shed by the road side of the barnyard
        Placer m = put(26, 14, houseLeft ? 3 : 1, houseLeft ? ydx + 8 : ydx - 24, 4, 1);
        if (m != null) {
            new Farmstead(m, rng, hit).machineShed(26, 14, any("corrugated_grey", "corrugated_green", "corrugated_red", "corrugated_rust"),
                    any(Farmstead.TIN));
        }
        Placer t = put(3, 4, 0, ydx + 6, yardEnd - 8, 1);
        if (t != null) {
            new Farmstead(t, rng, hit).fuelTank(0, 0);
        }
        Placer wm = put(12, 10, 0, houseLeft ? 2 : yw - 14, yd / 2 + 6, 1);
        if (wm != null) {
            new Farmstead(wm, rng, hit).windmill(3, 3, 14 + rng.nextInt(4), true);
        }
        Placer crib = put(7, 7, 0, ydx - 12, yardEnd + 12, 1);
        if (crib != null) {
            new Farmstead(crib, rng, hit).cornCrib(3, 3);
        }
        Placer hs = put(8, 5, 0, ydx + 10, yardEnd + 14, 1);
        if (hs != null) {
            hayStack(hs, 8, 5);
        }
        // most farms kept a few hens
        if (chance() < 0.6) {
            coop(houseLeft ? yw - 12 : 3, yardEnd - 10);
        }
        if (chance() < 0.35) {
            smokehouse(houseLeft ? ydx - 14 : ydx + 10, yardEnd + 18);
        }
        Placer tr = put(3, 12, 0, ydx + 4, yardEnd, 0);
        if (tr != null) {
            Farmstead fs = new Farmstead(tr, rng, hit);
            fs.tractor(0, 0, any("red", "green", "green", "orange"));
            if (chance() < 0.6) {
                fs.hayWagon(0, 6, 2);
            }
        }
        yardLight(ydx + 4, yardEnd - 2);
        yardStory();
        windbreak();
    }

    private void dairy() {
        if (yp == null) {
            return;
        }
        yardStart(new House.Style[] {House.Style.FARMHOUSE, House.Style.COLONIAL, House.Style.BUNGALOW},
                weights(0.1, 0.25, 0.2, 0.45, 0, 0, 0, 0), 24, 26);
        // the long free-stall barn behind the barnyard, its milk house on the end
        int dw = 18, dd = Math.min(56, yd - 12);
        Placer db = put(dw + 8, dd, 0, houseLeft ? ydx + 2 : ydx - dw - 10, yardEnd - 4, 1);
        if (db != null) {
            Farmstead fs = new Farmstead(db.shifted(8, 0, 0), rng, hit);
            fs.dairyBarn(dw, dd, any(Farmstead.TIN));
            new Farmstead(db, rng, hit).milkHouse(7, 7);
            Boards.fascia(db.shifted(8, 0, 0), rng, "dairy", 0, 6, -1, dw);
        }
        // the old barn, two blue silos and a stave one
        String wall = chance() < 0.6 ? "barn_red" : "siding_white";
        barnComplex(14, 26, wall, any("minecraft:deepslate_tile", Farmstead.TIN[1]), true, Farmstead.Use.STALLS, 1,
                Farmstead.Silo.STAVE, false, houseLeft ? 2 : yw - 30, yardEnd + 8);
        Placer s = put(26, 12, 0, ydx - 13, yd - 16, 1);
        if (s != null) {
            Farmstead fs = new Farmstead(s, rng, hit);
            fs.silo(4, 6, 4, 26 + rng.nextInt(6), Farmstead.Silo.BLUE);
            fs.silo(16, 6, 4, 24 + rng.nextInt(6), Farmstead.Silo.BLUE);
        }
        // the bunker silo: concrete walls, silage under black plastic held down with tyres
        Placer bk = put(12, 26, 0, houseLeft ? yw - 16 : 4, yardEnd, 1);
        if (bk != null) {
            for (int z = 0; z < 26; z++) {
                for (int x = 0; x < 12; x++) {
                    boolean wall2 = x == 0 || x == 11;
                    bk.set(x, 0, z, "floor_concrete");
                    for (int y = 1; y <= 3; y++) {
                        bk.set(x, y, z, wall2 ? "concrete_dark" : z > 3 && y <= 2 ? "minecraft:packed_mud" : "air");
                    }
                    if (!wall2 && z > 3) {
                        bk.set(x, 3, z, z > 6 || chance() < 0.4 ? "fm_silage" : "air");
                        if (z > 6 && (x + z) % 3 == 0 && chance() < 0.7) {
                            bk.set(x, 3, z, "tire[axis=x]");
                        }
                    }
                }
            }
        }
        // calf hutches in a row, each with its little wire pen
        Placer ch = put(22, 6, 0, ydx + (houseLeft ? 12 : -34), 4, 1);
        if (ch != null) {
            for (int i = 0; i < 5; i++) {
                int x = i * 4 + 1;
                ch.set(x, 1, 3, "fm_hutch_front[facing=north]");
                ch.set(x, 1, 4, "fm_hutch_back[facing=north]");
                ch.fill(x, 0, 3, x + 1, 0, 5, "straw_floor");
                Kit.chainlink(ch, rng, x - 1, 0, x + 2, 0, 1);
                if (chance() < 0.3) {
                    ch.set(x, 1, 1, "blood_splat");
                }
            }
        }
        Placer t = put(3, 4, 0, ydx + 6, yardEnd - 8, 1);
        if (t != null) {
            new Farmstead(t, rng, hit).fuelTank(0, 0);
        }
        Placer tr = put(3, 6, 0, ydx - 6, yardEnd, 0);
        if (tr != null) {
            new Farmstead(tr, rng, hit).tractor(0, 0, any("green", "red"));
        }
        // the holding pen by the barn, and the hens
        Placer cr = put(14, 12, 0, houseLeft ? ydx + 2 : ydx - 16, yardEnd + 6, 1);
        if (cr != null) {
            new Farmstead(cr, rng, hit).corral(14, 12, 2 + rng.nextInt(3));
        }
        if (chance() < 0.5) {
            coop(houseLeft ? yw - 12 : 3, 6);
        }
        // the manure pit
        Placer mp = put(12, 10, 0, houseLeft ? yw - 14 : 2, yd - 14, 1);
        if (mp != null) {
            for (int x = 0; x < 12; x++) {
                for (int z = 0; z < 10; z++) {
                    boolean edge = x == 0 || z == 0 || x == 11 || z == 9;
                    mp.set(x, 0, z, edge ? "minecraft:mud" : chance() < 0.7 ? "minecraft:water" : "minecraft:mud");
                    if (!edge) {
                        mp.set(x, -1, z, "minecraft:mud");
                    }
                }
            }
            Kit.chainlink(mp, rng, 0, -1, 11, -1, 1);
        }
        yardLight(ydx + 4, yardEnd - 2);
        yardStory();
        windbreak();
    }

    private void ranch() {
        if (yp == null) {
            return;
        }
        yardStart(new House.Style[] {House.Style.RANCH, House.Style.RANCH, House.Style.FARMHOUSE},
                weights(0.7, 0, 0, 0.3, 0, 0, 0, 0), 28, 26);
        // the log gate over the drive with the ranch's name
        for (int y = 1; y <= 6; y++) {
            yp.set(ydx - 4, y, 3, "tree_spruce_log");
            yp.set(ydx + 4, y, 3, "tree_spruce_log");
        }
        for (int x = ydx - 5; x <= ydx + 5; x++) {
            yp.set(x, 7, 3, "fm_beam[axis=x]");
        }
        Placer sign = yp.turned(ydx - 2, 0, 3, 0);
        for (int i = 0; i < 4; i++) {
            sign.set(3 - i, 5, -1, "sign_ranch_" + i + "[facing=south]");
            sign.set(3 - i, 6, -1, "fm_chain");
        }
        // the horse barn: a long gable with cupolas, the stalls down both sides
        String wall = chance() < 0.5 ? "barn_grey" : "siding_white";
        barnComplex(14, 36, wall, any("minecraft:dark_oak", Farmstead.TIN[2]), false, Farmstead.Use.HORSES, 0, Farmstead.Silo.STAVE,
                false, houseLeft ? ydx + 3 : ydx - 18, yardEnd + 4);
        // the hay barn, full to the loft
        barnComplex(12, 18, chance() < 0.5 ? "barn_red" : "corrugated_green", any(Farmstead.TIN), true, Farmstead.Use.HAY, 0,
                Farmstead.Silo.STAVE, true, houseLeft ? yw - 22 : 3, yd - 26);
        // the round pen
        Placer rp = put(17, 17, 0, houseLeft ? 3 : yw - 20, yardEnd + 2, 1);
        if (rp != null) {
            for (int x = 0; x < 17; x++) {
                for (int z = 0; z < 17; z++) {
                    double dd = Math.hypot(x - 8, z - 8);
                    if (dd < 8.5) {
                        rp.set(x, 0, z, chance() < 0.7 ? "minecraft:sand" : "minecraft:coarse_dirt");
                    }
                    if (dd >= 7.5 && dd < 8.5) {
                        rp.set(x, 1, z, "air");
                    }
                }
            }
        }
        if (rp != null) {
            // the round pen's white boards, smooth round on their posts, the gate at the south
            Round.wall(rp, 8.5, 8.5, 8.3, 1, 1, "paddock", 0);
            Doors.place(rp, 8, 1, 16, "fm_gate", "south", "left", true);
        }
        // a horse trailer and its truck
        Placer ht = put(4, 9, 0, ydx + 7, yardEnd - 12, 1);
        if (ht != null) {
            ht.vehicle(1.5, 1, 4.5, 180, "=horse_trailer", rng.nextLong());
            yp.vehicle(ydx + 8.5, 1, yardEnd - 18, 0, "pickup", rng.nextLong());
        }
        // the ranch runs cattle now (Zomboid has no horses): the working corral by the barns
        Placer cr = put(16, 14, 0, ydx - 18, yd - 16, 1);
        if (cr != null) {
            new Farmstead(cr, rng, hit).corral(16, 14, 3 + rng.nextInt(3));
        }
        Placer m = put(8, 6, 0, ydx - 14, yd - 10, 1);
        if (m != null) {
            for (int x = 0; x < 8; x++) {
                for (int z = 0; z < 6; z++) {
                    m.set(x, 0, z, "minecraft:mud");
                    if (chance() < 0.6) {
                        m.set(x, 1, z, chance() < 0.5 ? "straw_floor" : "minecraft:packed_mud");
                    }
                }
            }
        }
        yardLight(ydx + 4, yardEnd - 2);
        yardStory();
        windbreak();
    }

    private void orchardYard() {
        if (yp == null) {
            return;
        }
        yardStart(new House.Style[] {House.Style.FARMHOUSE, House.Style.BUNGALOW, House.Style.COLONIAL},
                weights(0, 0.25, 0.25, 0.5, 0, 0, 0, 0), 24, 26);
        // the stand by the road where they sold the apples
        Placer st = put(7, 5, 0, houseLeft ? ydx + 6 : ydx - 13, 5, 1);
        if (st != null) {
            new Farmstead(st, rng, hit).produceStand(1, 1, chance() < 0.6 ? "apples" : "produce");
        }
        // the packing shed, open-fronted, full of crates
        Placer ps = put(22, 14, 0, houseLeft ? ydx + 3 : ydx - 25, yardEnd + 4, 1);
        if (ps != null) {
            Farmstead fs = new Farmstead(ps, rng, hit);
            fs.machineShed(22, 14, any("corrugated_green", "corrugated_red", "barn_red"), any(Farmstead.TIN));
            for (int x = 1; x < 21; x++) {
                for (int z = 2; z < 12; z++) {
                    if ((x % 4 != 0) && (z % 3 != 0) && chance() < 0.55) {
                        ps.set(x, 1, z, "apple_crate");
                        if (chance() < 0.35) {
                            ps.set(x, 2, z, chance() < 0.7 ? "apple_crate" : "crate");
                        }
                    }
                }
            }
            for (int x = 2; x < 20; x++) {
                ps.set(x, 1, 1, x == 2 || x == 19 ? "fm_drum" : "rail[axis=x]");
            }
        }
        barnComplex(12, 20, chance() < 0.7 ? "barn_red" : "barn_grey", any("minecraft:deepslate_tile", Farmstead.TIN[0]), true,
                Farmstead.Use.HAY, 0, Farmstead.Silo.STAVE, false, houseLeft ? yw - 18 : 3, yd - 28);
        Placer hv = put(10, 3, 0, houseLeft ? 3 : yw - 14, yd - 8, 1);
        if (hv != null) {
            new Farmstead(hv, rng, hit).hives(0, 1, 5);
        }
        Placer hw = put(3, 6, 0, ydx + 5, yardEnd + 2, 0);
        if (hw != null) {
            Farmstead fs = new Farmstead(hw, rng, hit);
            fs.hayWagon(0, 0, 1);
        }
        // a few trees in the yard itself, the oldest of the orchard
        for (int i = 0; i < 3; i++) {
            Placer at = put(5, 5, 0, rng.nextInt(Math.max(1, yw - 6)), yardEnd + rng.nextInt(Math.max(1, yd - yardEnd - 6)), 1);
            if (at != null) {
                int[] c = framePoint(at, 2, 2);
                appleTree(c[0], c[1]);
            }
        }
        yardLight(ydx + 4, yardEnd - 2);
        yardStory();
        windbreak();
    }

    private void homestead() {
        if (yp == null) {
            return;
        }
        yardStart(new House.Style[] {House.Style.CABIN, House.Style.CABIN, House.Style.FARMHOUSE},
                weights(0, 0, 0, 0.3, 0.7, 0, 0, 0), 20, 22);
        barnComplex(12, 16, chance() < 0.75 ? "barn_red" : "barn_grey", any("minecraft:dark_oak", Farmstead.TIN[1], "minecraft:deepslate_tile"),
                true, Farmstead.Use.STALLS, chance() < 0.4 ? 1 : 0, Farmstead.Silo.BRICK, chance() < 0.5, houseLeft ? ydx + 3 : ydx - 22,
                yardEnd + 2);
        coop(houseLeft ? yw - 11 : 2, 4);
        // the family cow in her little pen by the barn
        Placer cr = put(9, 9, 0, houseLeft ? ydx + 17 : ydx - 32, yardEnd + 4, 1);
        if (cr != null) {
            new Farmstead(cr, rng, hit).corral(9, 9, 1 + rng.nextInt(2));
        }
        Placer wl = put(3, 3, 0, houseLeft ? ydx - 8 : ydx + 6, yardEnd - 4, 1);
        if (wl != null) {
            new Farmstead(wl, rng, hit).well(1, 1);
        }
        smokehouse(houseLeft ? ydx - 12 : ydx + 8, yardEnd + 4);
        Placer oh = put(2, 3, 0, houseLeft ? 2 : yw - 4, yd - 8, 1);
        if (oh != null) {
            new Farmstead(oh, rng, hit).outhouse(0, 1);
        }
        Placer wp = put(8, 3, 0, ydx - 4, yardEnd + 10, 1);
        if (wp != null) {
            new Farmstead(wp, rng, hit).woodpile(1, 0, 5);
        }
        Placer wm = put(12, 10, 0, houseLeft ? yw - 14 : 2, yd - 14, 1);
        if (wm != null) {
            new Farmstead(wm, rng, hit).windmill(3, 3, 11 + rng.nextInt(3), true);
        }
        // the kitchen garden behind the cabin, fenced against deer, its scarecrow
        Placer kg = put(14, 10, 0, houseLeft ? 2 : yw - 16, yardEnd + 2, 1);
        if (kg != null) {
            for (int x = 0; x < 14; x++) {
                for (int z = 0; z < 10; z++) {
                    boolean edge = x == 0 || z == 0 || x == 13 || z == 9;
                    if (edge) {
                        boolean corner = (x == 0 || x == 13) && (z == 0 || z == 9);
                        if (x == 6 && z == 0) {
                            Doors.place(kg, x, 1, z, "fm_gate", "north", "left", true);
                        } else {
                            kg.set(x, 1, z, corner ? "fm_post" : "chainlink[axis=" + (z == 0 || z == 9 ? "x" : "z") + "]");
                            kg.set(x, 2, z, corner ? "fm_post" : "chainlink[axis=" + (z == 0 || z == 9 ? "x" : "z") + "]");
                        }
                        continue;
                    }
                    if (z % 3 == 0) {
                        kg.set(x, 0, z, "minecraft:dirt_path");
                        continue;
                    }
                    kg.set(x, 0, z, "minecraft:farmland[moisture=0]");
                    int row = z / 3;
                    String plant = switch ((row + x / 7) % 4) {
                        case 0 -> "minecraft:carrots[age=" + (5 + rng.nextInt(3)) + "]";
                        case 1 -> "minecraft:potatoes[age=" + (5 + rng.nextInt(3)) + "]";
                        case 2 -> "minecraft:beetroots[age=3]";
                        default -> "minecraft:wheat[age=7]";
                    };
                    if (chance() < 0.82) {
                        kg.set(x, 1, z, plant);
                    }
                }
            }
            new Farmstead(kg, rng, hit).scarecrow(7, 4, "north");
        }
        Placer hv = put(6, 3, 0, ydx + 8, yd - 10, 1);
        if (hv != null) {
            new Farmstead(hv, rng, hit).hives(0, 1, 3);
        }
        Placer pd = put(13, 11, 0, houseLeft ? ydx + 4 : ydx - 17, yd - 14, 1);
        if (pd != null) {
            int[] at = framePoint(pd, 6, 5);
            kit.pond(at[0], at[1], 4);
        }
        // the split rail fence round the yard
        Kit.railFence(yp, rng, 0, 0, ydx - 3, 0, false, 0.08);
        Kit.railFence(yp, rng, ydx + 3, 0, yw - 1, 0, false, 0.08);
        Kit.railFence(yp, rng, 0, 1, 0, yd - 1, false, 0.1);
        Kit.railFence(yp, rng, yw - 1, 1, yw - 1, yd - 1, false, 0.1);
        yp.vehicle(ydx + (houseLeft ? 4 : -4), 1, yardEnd - 6, (float) (chance() * 360), "pickup", rng.nextLong());
        yardStory();
    }

    /** The smokehouse at the nearest free yard spot to (x, z), its door to the yard. */
    private void smokehouse(int x, int z) {
        Placer sm = put(5, 5, 0, x, z, 1);
        if (sm != null) {
            new Farmstead(sm, rng, hit).smokehouse(any("barn_grey", "barn_red", "brick_red"));
        }
    }

    /** A chicken coop and its run at the nearest free yard spot to (x, z), with its hens. */
    private void coop(int x, int z) {
        Placer c = put(9, 12, 0, x, z, 1);
        if (c != null) {
            new Farmstead(c.shifted(1, 0, 7), rng, hit).coop(7, 5, any("barn_red", "siding_white", "barn_grey"));
        }
    }

    /** The place coordinates of a point in some frame. */
    private static int[] framePoint(Placer q, int x, int z) {
        return q.world(x, z);
    }

    // ------------------------------------------------------------------ fields

    private void field(Field f) {
        int x0 = f.x0() + 1, z0 = f.z0() + 1, x1 = f.x1() - 1, z1 = f.z1() - 1;
        switch (f.crop()) {
            case CORN, CORN_GREEN -> corn(f, x0, z0, x1, z1);
            case WHEAT -> rows(f, x0, z0, x1, z1, "wheat");
            case BEANS -> rows(f, x0, z0, x1, z1, "beans");
            case GARDEN -> rows(f, x0, z0, x1, z1, "garden");
            case PUMPKINS -> pumpkins(x0, z0, x1, z1);
            case HAY -> hay(f, x0, z0, x1, z1);
            case PASTURE, PADDOCK -> pasture(f, x0, z0, x1, z1);
            case FALLOW -> fallow(x0, z0, x1, z1);
            case ORCHARD -> orchard(f, x0, z0, x1, z1);
            case ARENA -> arena(x0, z0, x1, z1);
        }
        fence(f);
    }

    /** Which side of a field the county road (or the nearest road) is on: 0 north, 2 south. */
    private int roadSide(Field f) {
        double zc = zAt(main, (f.x0() + f.x1()) / 2.0);
        return zc < f.z0() ? 0 : 2;
    }

    private void fence(Field f) {
        String post, rail;
        switch (f.crop()) {
            case PASTURE -> {
                post = "fm_post";
                rail = "barbed_wire";
            }
            case PADDOCK, ARENA -> {
                post = "fm_rail_post_w";
                rail = "fm_rail_w";
            }
            case GARDEN -> {
                post = "fm_post";
                rail = "chainlink";
            }
            default -> {
                post = "fm_rail_post";
                rail = "fm_rail";
            }
        }
        int gateSide = roadSide(f);
        int gx = (f.x0() + f.x1()) / 2;
        for (int x = f.x0(); x <= f.x1(); x++) {
            fencePost(x, f.z0(), post, rail, "x", (x - f.x0()) % 4 == 0, gateSide == 0 && Math.abs(x - gx) <= 1, "north");
            fencePost(x, f.z1(), post, rail, "x", (x - f.x0()) % 4 == 0, gateSide == 2 && Math.abs(x - gx) <= 1, "south");
        }
        for (int z = f.z0() + 1; z < f.z1(); z++) {
            fencePost(f.x0(), z, post, rail, "z", (z - f.z0()) % 4 == 0, false, "west");
            fencePost(f.x1(), z, post, rail, "z", (z - f.z0()) % 4 == 0, false, "east");
        }
    }

    private void fencePost(int x, int z, String post, String rail, String axis, boolean isPost, boolean gate, String side) {
        if (kit.use(x, z) == Kit.ROAD || out.get(x, 1, z) != null && !out.get(x, 1, z).contains("grass")) {
            return;
        }
        if (gate) {
            Doors.place(new Placer(out, 0, 0, 0, 0), x, 1, z, "fm_gate", side, "left", chance() < 0.5);
            return;
        }
        double r = chance();
        if (r < 0.05) {
            return;  // gone
        }
        String a = "[axis=" + axis + "]";
        if (isPost || r < 0.08) {
            out.set(x, 1, z, post.startsWith("fm_rail") ? post + a : post);
        } else if (rail.equals("barbed_wire")) {
            // a steel T-post between the wooden ones
            out.set(x, 1, z, ((axis.equals("x") ? x : z) % 2 == 0 ? "fm_tpost" : rail) + a);
        } else if (rail.equals("fm_rail") && r < 0.14) {
            out.set(x, 1, z, "fm_rail_broken" + a);
        } else {
            out.set(x, 1, z, rail + a);
        }
    }

    /** Corn, dry on the stalk past harvest (or still green), a wheel rut every fifth row; maybe SOS cut into it. */
    private void corn(Field f, int x0, int z0, int x1, int z1) {
        boolean dry = f.crop() == Crop.CORN;
        String lo = dry ? "corn_dry_lower" : "corn_lower", hi = dry ? "corn_dry_upper" : "corn_upper";
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int row = f.alongX() ? z - z0 : x - x0;
                out.set(x, 0, z, chance() < 0.75 ? "minecraft:dirt" : "minecraft:coarse_dirt");
                if (row % 5 == 4) {
                    out.set(x, 0, z, chance() < 0.6 ? "minecraft:coarse_dirt" : "minecraft:dirt_path");
                    if (chance() < 0.15) {
                        out.set(x, 1, z, "minecraft:short_dry_grass");
                    }
                    continue;
                }
                double r = chance();
                if (r < 0.86) {
                    out.set(x, 1, z, lo);
                    out.set(x, 2, z, hi);
                } else if (r < 0.93) {
                    out.set(x, 1, z, lo);
                } else if (r < 0.96) {
                    out.set(x, 1, z, "minecraft:short_dry_grass");
                }
            }
        }
        if (x1 - x0 > 40 && z1 - z0 > 22 && chance() < 0.4) {
            sos(x0 + (x1 - x0) / 2 - 17, z0 + (z1 - z0) / 2 - 7);
        }
    }

    /** SOS trampled into the corn in letters fifteen blocks high, for anyone flying over. */
    private void sos(int x, int z) {
        String[] s = {"###", "#..", "###", "..#", "###"}, o = {"###", "#.#", "#.#", "#.#", "###"};
        String[][] letters = {s, o, s};
        for (int k = 0; k < 3; k++) {
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    if (letters[k][row].charAt(col) != '#') {
                        continue;
                    }
                    for (int i = 0; i < 3; i++) {
                        for (int j = 0; j < 3; j++) {
                            int bx = x + k * 12 + col * 3 + i, bz = z + row * 3 + j;
                            out.set(bx, 1, bz, chance() < 0.2 ? "corn_dry_lower" : "air");
                            out.set(bx, 2, bz, "air");
                            out.set(bx, 0, bz, "minecraft:coarse_dirt");
                        }
                    }
                }
            }
        }
    }

    /** Row crops on dry farmland: wheat gone over, or beans and roots, or a kitchen garden's mix. */
    private void rows(Field f, int x0, int z0, int x1, int z1, String kind) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int row = f.alongX() ? z - z0 : x - x0;
                if (!kind.equals("wheat") && row % 3 == 2) {
                    out.set(x, 0, z, "minecraft:coarse_dirt");
                    if (chance() < 0.1) {
                        out.set(x, 1, z, any("minecraft:short_grass", "weeds"));
                    }
                    continue;
                }
                out.set(x, 0, z, "minecraft:farmland[moisture=0]");
                double r = chance();
                if (r > 0.9) {
                    out.set(x, 0, z, "minecraft:coarse_dirt");
                    out.set(x, 1, z, any("minecraft:short_dry_grass", "weeds", "minecraft:dead_bush"));
                    continue;
                }
                String plant = switch (kind) {
                    case "wheat" -> "minecraft:wheat[age=" + (r < 0.7 ? 7 : 5 + rng.nextInt(3)) + "]";
                    case "beans" -> row / 3 % 2 == 0 ? "minecraft:potatoes[age=7]" : "minecraft:carrots[age=7]";
                    default -> switch (row / 3 % 4) {
                        case 0 -> "minecraft:carrots[age=7]";
                        case 1 -> "minecraft:beetroots[age=3]";
                        case 2 -> "minecraft:potatoes[age=6]";
                        default -> "minecraft:wheat[age=7]";
                    };
                };
                out.set(x, 1, z, plant);
            }
        }
        if (kind.equals("garden") || chance() < 0.3) {
            new Farmstead(new Placer(out, 0, 0, 0, 0), rng, hit).scarecrow((x0 + x1) / 2, (z0 + z1) / 2, any(Farmstead.DIRS));
        }
    }

    private void pumpkins(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out.set(x, 0, z, chance() < 0.6 ? "minecraft:grass_block" : "minecraft:coarse_dirt");
                double r = chance();
                if (r < 0.14) {
                    out.set(x, 1, z, "fm_pumpkin[facing=" + any(Farmstead.DIRS) + "]");
                } else if (r < 0.15) {
                    out.set(x, 1, z, "fm_pumpkin_carved[facing=" + any(Farmstead.DIRS) + "]");
                } else if (r < 0.45) {
                    out.set(x, 1, z, "minecraft:short_grass");
                } else if (r < 0.55) {
                    out.set(x, 1, z, "minecraft:fern");
                }
            }
        }
        new Farmstead(new Placer(out, 0, 0, 0, 0), rng, hit).scarecrow((x0 + x1) / 2, (z0 + z1) / 2, any(Farmstead.DIRS));
    }

    /** A hay field mown before it all happened: stubble, round bales left lying in rows, a stack in the corner. */
    private void hay(Field f, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out.set(x, 0, z, "minecraft:grass_block");
                double r = chance();
                if (r < 0.45) {
                    out.set(x, 1, z, "minecraft:short_grass");
                } else if (r < 0.55) {
                    out.set(x, 1, z, "minecraft:short_dry_grass");
                }
                int row = f.alongX() ? z - z0 : x - x0, along = f.alongX() ? x - x0 : z - z0;
                if (row % 9 == 4 && along % 7 == 3 && chance() < 0.75) {
                    String ax = "[axis=" + (f.alongX() ? "x" : "z") + "]";
                    out.set(x, 1, z, chance() < 0.3 ? "fm_wrap" + ax : chance() < 0.15 ? "fm_bale_up" : (chance() < 0.4 ? "fm_bale_grey" : "fm_bale") + ax);
                }
            }
        }
        // the stack, in the corner by the gate
        for (int y = 1; y <= 3; y++) {
            for (int i = y - 1; i < 7 - y; i++) {
                for (int j = 0; j < 3; j++) {
                    out.set(x0 + 1 + i, y, z0 + 1 + j, y == 3 || i == y - 1 || i == 6 - y ? "fm_bales_low[axis=x]" : "fm_bales[axis=x]");
                }
            }
        }
    }

    /** Pasture gone shaggy: tall grass, a trough by the gate, mud round it, a lone tree, bones. */
    private void pasture(Field f, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out.set(x, 0, z, chance() < 0.05 ? "minecraft:coarse_dirt" : "minecraft:grass_block");
                double r = chance();
                if (r < 0.18) {
                    out.set(x, 1, z, "minecraft:tall_grass[half=lower]");
                    out.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                } else if (r < 0.6) {
                    out.set(x, 1, z, "minecraft:short_grass");
                } else if (r < 0.63) {
                    out.set(x, 1, z, any("minecraft:dandelion", "minecraft:oxeye_daisy", "weeds"));
                }
            }
        }
        int gz = roadSide(f) == 0 ? z0 + 2 : z1 - 2, gx = (x0 + x1) / 2 + 3;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.set(gx + dx, 0, gz + dz, "minecraft:mud");
                out.set(gx + dx, 1, gz + dz, "air");
                out.set(gx + dx, 2, gz + dz, "air");
            }
        }
        if (chance() < 0.6) {
            // the round stock tank in its mud (refs/farm/cattle-water-trough)
            out.set(gx, 1, gz - 1, "fm_stock_tank[facing=north]");
            out.set(gx + 1, 1, gz - 1, "fm_stock_tank[facing=east]");
            out.set(gx + 1, 1, gz, "fm_stock_tank[facing=south]");
            out.set(gx, 1, gz, "fm_stock_tank[facing=west]");
        } else {
            out.set(gx, 1, gz, "fm_water_trough[axis=x]");
            out.set(gx + 1, 1, gz, "fm_water_trough[axis=x]");
        }
        boolean stock = p.variant.equals("dairy") || p.variant.equals("horse_ranch") || p.variant.equals("homestead");
        if (stock && chance() < 0.6 && x1 - x0 > 12 && z1 - z0 > 12) {
            // a bale ring feeder out in the field, the grass worn to mud round it
            int fx = x0 + 4 + rng.nextInt(x1 - x0 - 9), fz = z0 + 4 + rng.nextInt(z1 - z0 - 9);
            for (int dx = -1; dx <= 2; dx++) {
                for (int dz = -1; dz <= 2; dz++) {
                    out.set(fx + dx, 0, fz + dz, chance() < 0.6 ? "minecraft:mud" : "minecraft:coarse_dirt");
                    out.set(fx + dx, 1, fz + dz, "air");
                    out.set(fx + dx, 2, fz + dz, "air");
                }
            }
            out.set(fx, 1, fz, "fm_ring_feeder[facing=north]");
            out.set(fx + 1, 1, fz, "fm_ring_feeder[facing=east]");
            out.set(fx + 1, 1, fz + 1, "fm_ring_feeder[facing=south]");
            out.set(fx, 1, fz + 1, "fm_ring_feeder[facing=west]");
        }
        if (stock) {
            // the herd out at grass (FarmHerds: SURVIVAL's cows)
            int n = Math.min(6, 2 + (x1 - x0) * (z1 - z0) / 400);
            out.herd("cow", n, x0, z0, x1, z1, 1, rng.nextLong());
        }
        if (f.w() > 24 && f.d() > 24) {
            kit.tree(chance() < 0.6 ? Trees.Kind.OAK : Trees.Kind.MAPLE, (x0 + x1) / 2 - 6, (z0 + z1) / 2);
        }
        if (chance() < 0.4) {
            int bx = x0 + 3 + rng.nextInt(Math.max(1, x1 - x0 - 6)), bz = z0 + 3 + rng.nextInt(Math.max(1, z1 - z0 - 6));
            out.set(bx, 1, bz, "fm_bones[facing=east]");
            out.set(bx + 1, 1, bz, "fm_bones[facing=north]");
            out.set(bx + 1, 1, bz + 1, "blood_splat");
        }
        if (f.crop() == Crop.PADDOCK) {
            // a run-in shed against the back fence
            int sx = x0 + 2, sz = roadSide(f) == 0 ? z1 - 5 : z0 + 1;
            for (int x = sx; x < sx + 6; x++) {
                for (int z = sz; z < sz + 4; z++) {
                    boolean back = roadSide(f) == 0 ? z == sz + 3 : z == sz;
                    boolean sides = x == sx || x == sx + 5;
                    out.set(x, 0, z, "straw_floor".equals(out.get(x, 1, z)) ? "minecraft:dirt" : "minecraft:coarse_dirt");
                    for (int y = 1; y <= 3; y++) {
                        out.set(x, y, z, back || sides ? (y == 3 ? "barn_grey" : "barn_grey_b") : "air");
                    }
                    boolean front = roadSide(f) == 0 ? z == sz : z == sz + 3;
                    out.set(x, 4, z, "ext_porch_roof_tin_red" + (front ? "_edge" : "") + "[facing=" + (roadSide(f) == 0 ? "north" : "south") + "]");
                    if (!back && !sides && chance() < 0.5) {
                        out.set(x, 1, z, "straw_floor");
                    }
                }
            }
        }
    }

    private void fallow(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                double r = chance();
                out.set(x, 0, z, r < 0.4 ? "minecraft:coarse_dirt" : r < 0.7 ? "minecraft:dirt" : "minecraft:grass_block");
                double q = chance();
                if (q < 0.1) {
                    out.set(x, 1, z, "minecraft:short_dry_grass");
                } else if (q < 0.16) {
                    out.set(x, 1, z, any("weeds", "weeds_tall", "minecraft:dead_bush"));
                } else if (q < 0.2) {
                    out.set(x, 1, z, "minecraft:tall_dry_grass");
                }
            }
        }
    }

    /** An apple tree: a short trunk, a round crown, fruit on it and under it. */
    private void appleTree(int x, int z) {
        if (!inside(x, z)) {
            return;
        }
        int trunk = 2 + rng.nextInt(2);
        for (int y = 1; y <= trunk; y++) {
            out.set(x, y, z, "tree_oak_log");
        }
        double r = 2.2 + chance() * 0.6;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    double dd = Math.sqrt(dx * dx + dz * dz + (dy - 1.2) * (dy - 1.2) * 1.6);
                    if (dd <= r && inside(x + dx, z + dz) && out.get(x + dx, trunk + dy, z + dz) == null) {
                        out.set(x + dx, trunk + dy, z + dz, chance() < 0.45 ? "apple_leaves" : "minecraft:oak_leaves[persistent=true]");
                    }
                }
                if (dx * dx + dz * dz <= 6 && inside(x + dx, z + dz) && out.get(x + dx, 1, z + dz) == null && chance() < 0.4) {
                    out.set(x + dx, 1, z + dz, "windfall");
                }
            }
        }
    }

    private void orchard(Field f, int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                out.set(x, 0, z, "minecraft:grass_block");
                if (chance() < 0.35) {
                    out.set(x, 1, z, "minecraft:short_grass");
                }
            }
        }
        int rowGap = 7, treeGap = 6;
        for (int a = 3; (f.alongX() ? z0 + a : x0 + a) <= (f.alongX() ? z1 - 3 : x1 - 3); a += rowGap) {
            for (int b2 = 3; (f.alongX() ? x0 + b2 : z0 + b2) <= (f.alongX() ? x1 - 3 : z1 - 3); b2 += treeGap) {
                int x = f.alongX() ? x0 + b2 : x0 + a, z = f.alongX() ? z0 + a : z0 + b2;
                double r = chance();
                if (r < 0.06) {
                    continue;  // a gap where one died
                }
                for (int y = 1; y <= 4; y++) {
                    out.set(x, y, z, "air");
                }
                appleTree(x, z);
                if (r > 0.92) {
                    // a picker's ladder still leaning in it, a crate half full beneath
                    out.set(x + 1, 1, z, "fm_ladder[facing=east]");
                    out.set(x + 1, 2, z, "fm_ladder[facing=east]");
                    out.set(x + 2, 1, z, "apple_crate");
                }
            }
        }
        if (chance() < 0.5) {
            // a stack of empty bins at the end of a row
            for (int i = 0; i < 6; i++) {
                out.set(x0 + 1 + i % 3, 1 + i / 3, z0 + 1, i % 2 == 0 ? "crate" : "apple_crate");
            }
        }
    }

    /** The riding arena: raked sand inside a white rail, jumps set up, lights on poles at the corners. */
    private void arena(int x0, int z0, int x1, int z1) {
        int ax0 = x0 + 2, az0 = z0 + 2, ax1 = Math.min(x1 - 2, ax0 + 44), az1 = Math.min(z1 - 2, az0 + 26);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean in = x >= ax0 && x <= ax1 && z >= az0 && z <= az1;
                out.set(x, 0, z, in ? (chance() < 0.75 ? "minecraft:sand" : "minecraft:coarse_dirt") : "minecraft:grass_block");
                if (!in && chance() < 0.4) {
                    out.set(x, 1, z, "minecraft:short_grass");
                }
                boolean rail = in && (x == ax0 || x == ax1 || z == az0 || z == az1);
                if (rail) {
                    String a = "[axis=" + (z == az0 || z == az1 ? "x" : "z") + "]";
                    boolean corner = (x == ax0 || x == ax1) && (z == az0 || z == az1);
                    out.set(x, 1, z, corner ? "fm_post_w" : (x + z) % 4 == 0 ? "fm_rail_post_w" + a : "fm_rail_w" + a);
                    out.set(x, 2, z, "air");
                }
            }
        }
        Doors.place(new Placer(out, 0, 0, 0, 0), (ax0 + ax1) / 2, 1, az0, "fm_gate", "north", "left", true);
        // the jumps: rails between white standards, a barrel jump
        for (int k = 0; k < 4; k++) {
            int jx = ax0 + 6 + rng.nextInt(Math.max(1, ax1 - ax0 - 12)), jz = az0 + 4 + rng.nextInt(Math.max(1, az1 - az0 - 8));
            boolean alongX = chance() < 0.5;
            for (int i = 0; i < 4; i++) {
                int x = jx + (alongX ? i : 0), z = jz + (alongX ? 0 : i);
                boolean end = i == 0 || i == 3;
                String a = "[axis=" + (alongX ? "x" : "z") + "]";
                out.set(x, 1, z, k == 3 && !end ? "fm_drum" : end ? "fm_post_w" : "fm_rail_w" + a);
                if (end) {
                    out.set(x, 2, z, "fm_post_w");
                }
            }
        }
        for (int[] c : new int[][] {{ax0 - 1, az0 - 1}, {ax1 + 1, az0 - 1}, {ax0 - 1, az1 + 1}, {ax1 + 1, az1 + 1}}) {
            for (int y = 1; y <= 7; y++) {
                out.set(c[0], y, c[1], "pole_wood");
            }
            chance();  // the old dead-bulb roll, kept so everything after it lands where it did: every lamp works now
            out.set(c[0], 8, c[1], "streetlight_head[facing=north]");
        }
    }

    /** Trees and brush along the field edges where no track runs. */
    private void hedgerows() {
        for (int[] h : hedges) {
            boolean alongX = h[1] == h[3];
            int len = alongX ? h[2] - h[0] : h[3] - h[1];
            for (int i = 0; i <= len; i++) {
                int x = alongX ? h[0] + i : h[0], z = alongX ? h[1] : h[1] + i;
                if (kit.use(x, z) != Kit.FREE) {
                    continue;
                }
                out.set(x, 0, z, "minecraft:grass_block");
                double r = chance();
                if (i % 9 == 4 && r < 0.6) {
                    double t = chance();
                    kit.tree(t < 0.4 ? Trees.Kind.OAK : t < 0.7 ? Trees.Kind.MAPLE : t < 0.85 ? Trees.Kind.YOUNG : Trees.Kind.DEAD, x, z);
                } else if (r < 0.5) {
                    out.set(x, 1, z, "minecraft:oak_leaves[persistent=true]");
                    if (chance() < 0.4) {
                        out.set(x, 2, z, "minecraft:oak_leaves[persistent=true]");
                    }
                } else if (r < 0.7) {
                    out.set(x, 1, z, any("minecraft:sweet_berry_bush[age=2]", "minecraft:bush", "minecraft:tall_grass[half=lower]"));
                }
                kit.claim(x, z, x, z, Kit.PARK);
            }
        }
    }

    // ------------------------------------------------------------------ along the roads

    /**
     * The county road's things: power poles on the far side from the yard, the mailbox and sign at
     * the drive, lamps either side of it, NO TRESPASSING on a fence, cars left where they stopped.
     */
    private void roadside() {
        int side = yp != null && yFacing == 2 ? 1 : -1;  // poles on the side away from the yard (+1 is south of an eastward road)
        int[] last = null;
        for (double s = 6; s < main.length() - 4; s += 26) {
            double[] q = main.point(s);
            double nx = -q[3] * side, nz = q[2] * side;
            double o = main.style.half + 3;
            int x = (int) Math.floor(q[0] + nx * o), z = (int) Math.floor(q[1] + nz * o);
            if (!inside(x, z) || kit.use(x, z) == Kit.ROAD || out.get(x, 1, z) != null) {
                continue;
            }
            for (int y = 1; y < 8; y++) {
                out.set(x, y, z, "pole_wood");
            }
            out.set(x, 8, z, "pole_top[axis=z]");
            if (last != null) {
                wire(last[0], last[1], x, z, 8);
            }
            last = new int[] {x, z};
        }
        if (yp != null) {
            int[] g = yp.world(ydx, 0);
            int fx = yFacing == 2 ? 1 : -1;  // toward the road from the yard front, in place z
            int mx = g[0] + 3, mz = g[1] + fx * 2;
            if (inside(mx, mz) && kit.use(mx, mz) != Kit.ROAD) {
                out.set(mx, 0, mz, "minecraft:gravel");
                out.set(mx, 1, mz, "mailbox[facing=" + (fx == 1 ? "south" : "north") + "]");
            }
            // lamps on poles either side of the drive's mouth
            for (int sx = -1; sx <= 1; sx += 2) {
                int lx = g[0] + sx * 4, lz = g[1] + fx;
                if (inside(lx, lz) && kit.use(lx, lz) != Kit.ROAD) {
                    for (int y = 1; y <= 4; y++) {
                        out.set(lx, y, lz, "pole_wood");
                    }
                    chance();  // the old dead-bulb roll, kept so everything after it lands where it did: every lamp works now
                    out.set(lx, 5, lz, "streetlight_head[facing=" + (fx == 1 ? "south" : "north") + "]");
                }
            }
            String sign = switch (p.variant) {
                case "dairy" -> "dairy";
                case "orchard" -> "orchard";
                case "horse_ranch" -> null;
                case "homestead" -> chance() < 0.5 ? "eggs" : "trespass";
                default -> "hollowcreek";
            };
            if (sign != null) {
                // a board on posts by the drive, facing the road
                Placer bp = new Placer(out, g[0] - 9, 0, g[1] + fx * 3, yFacing == 2 ? 2 : 0);
                Boards.board(bp, rng, sign, 0, 2, 0);
            }
        }
        // field signs and NO TRESPASSING on the fences along the road
        for (Field f : fields) {
            if ((f.crop() == Crop.CORN || f.crop() == Crop.CORN_GREEN) && chance() < 0.5) {
                int z = roadSide(f) == 0 ? f.z0() - 2 : f.z1() + 2;
                if (inside(f.x0() + 3, z) && kit.use(f.x0() + 3, z) != Kit.ROAD) {
                    Placer bp = new Placer(out, f.x0() + 3, 0, z, roadSide(f) == 0 ? 0 : 2);
                    Boards.board(bp, rng, "seedcorn", 0, 2, 0);
                }
            } else if (chance() < 0.15) {
                int z = roadSide(f) == 0 ? f.z0() : f.z1();
                out.set(f.x1() - 4, 1, z, "fm_post");
            }
        }
        kit.abandonedCars(main, 1.2);
        kit.abandonedCars(north, 0.6);
        kit.abandonedCars(south, 0.6);
        // a tractor run off into the ditch, or a burnt wreck across the lanes
        if (chance() < 0.5) {
            double[] q = main.point(main.length() * (0.2 + chance() * 0.6));
            int x = (int) (q[0] + q[3] * (main.style.half + 2)), z = (int) (q[1] - q[2] * (main.style.half + 2));
            if (inside(x, z) && inside(x + 3, z + 5) && kit.free(x, z, x + 2, z + 4)) {
                new Farmstead(new Placer(out, x, 0, z, rng.nextInt(4)), rng, hit).tractor(0, 0, any("red", "green"), any("@abandoned", "@wrecked", "@burnt"));
            }
        }
    }

    /** A wire strung between two pole tops. */
    private void wire(int x0, int z0, int x1, int z1, int y) {
        int n = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
        String axis = Math.abs(x1 - x0) >= Math.abs(z1 - z0) ? "x" : "z";
        for (int i = 1; i < n; i++) {
            int x = x0 + (int) Math.round((x1 - x0) * (double) i / n), z = z0 + (int) Math.round((z1 - z0) * (double) i / n);
            if (inside(x, z) && out.get(x, y, z) == null) {
                out.set(x, y, z, "power_line[axis=" + axis + "]");
            }
        }
    }
}
