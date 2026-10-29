package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.Doors;
import com.zomboidcraft.world.city.Placer;
import java.util.SplittableRandom;

/**
 * A home on its lot, in the lot's own frame (the street along z = 0, x along the front): the
 * house set back behind its front yard, a walk to the door and a driveway, a mailbox at the
 * curb, fences or hedges, and a back yard with whatever the family had (a shed, a swing set,
 * a sagging above-ground pool, a vegetable patch gone to seed). Then the outbreak's marks: the
 * car still in the drive or gone, litter where the looters went through, sandbags where someone
 * tried to hold out, and the lawn grown knee high.
 */
final class Homes {
    /** How a neighbourhood's homes are picked and dressed. */
    record Mix(double[] styles, int setback, double picket, double hedge, double privacy, double chainlink,
               double cars, double lit, double wild, boolean alleyGarage, boolean grand) {
        /** Weights in House.Style order: RANCH, COLONIAL, BUNGALOW, FARMHOUSE, CABIN, TRAILER, SHOTGUN, MANSION. */
        static double[] weights(double... w) {
            return w;
        }
    }

    private static final String[] WALKS = {"sidewalk", "sidewalk", "pt_stone_flags", "pt_brick", "minecraft:packed_mud"};
    private static final String[] SHEDS = {"barn_red", "barn_grey", "siding_white", "siding_sage", "corrugated_grey",
            "corrugated_rust", "barn_red"};

    private final Kit kit;
    private final SplittableRandom rng;

    Homes(Kit kit) {
        this.kit = kit;
        this.rng = kit.rng;
    }

    House.Style pick(double[] weights) {
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        double r = rng.nextDouble() * total;
        House.Style[] all = House.Style.values();
        for (int i = 0; i < weights.length; i++) {
            r -= weights[i];
            if (r < 0) {
                return all[i];
            }
        }
        return House.Style.RANCH;
    }

    /** Width, depth (porch included) and floors for a style, before fitting the lot. */
    private int[] size(House.Style s) {
        return switch (s) {
            // roomy enough for a hall, rooms off it and walls two layers thick (see Interior)
            case RANCH -> new int[] {22 + rng.nextInt(6), 16 + rng.nextInt(3), 1};
            case COLONIAL -> new int[] {17 + rng.nextInt(5), 15 + rng.nextInt(3), 2};
            case BUNGALOW -> new int[] {12 + rng.nextInt(4), 16 + rng.nextInt(4), 1};
            case FARMHOUSE -> new int[] {16 + rng.nextInt(4), 17 + rng.nextInt(3), 2};
            case CABIN -> new int[] {9 + rng.nextInt(4), 12 + rng.nextInt(3), 1};
            case TRAILER -> new int[] {18 + rng.nextInt(4), 7 + rng.nextInt(2), 1};
            case SHOTGUN -> new int[] {7 + rng.nextInt(2), 21 + rng.nextInt(5), 1};
            case MANSION -> new int[] {24 + rng.nextInt(7), 19 + rng.nextInt(5), 2};
        };
    }

    /** Builds a home of the mix on the lot. */
    void home(Kit.Lot lot, Mix mix) {
        home(lot, pick(mix.styles()), mix);
    }

    void home(Kit.Lot lot, House.Style style, Mix mix) {
        Placer p = lot.placer(kit.out);
        int lw = lot.w(), ld = lot.d();
        int[] sz = size(style);
        int hw = sz[0], hd = sz[1], floors = sz[2];
        // fit the lot: narrow the house, or drop to a smaller style
        if (hw > lw - 5) {
            hw = lw - 5;
            if (style == House.Style.RANCH && hw < 20 || style == House.Style.MANSION && hw < 20) {
                style = hw >= 11 ? House.Style.BUNGALOW : House.Style.SHOTGUN;
                int[] alt = size(style);
                hd = alt[1];
                floors = alt[2];
                hw = Math.min(alt[0], lw - 4);
            }
        }
        int back = mix.alleyGarage() ? 11 : 6;
        if (hd > ld - back - 3) {
            hd = Math.max(8, ld - back - 3);
        }
        if (floors > 1 && hw < 14) {
            floors = 1;  // too narrow for a hall with the stairs beside it
        }
        if (hw < 7 || hd < (style == House.Style.TRAILER ? 7 : 9)) {
            yardOnly(p, lw, ld, mix);
            return;
        }
        int sb = Math.max(3, Math.min(mix.setback() + rng.nextInt(3) - 1, ld - hd - back));
        boolean garage = style == House.Style.RANCH;
        // leave a side yard for the drive when there's no garage in the house
        int sideRoom = lw - hw;
        boolean driveLeft = rng.nextBoolean();
        int sx;
        if (!garage && !mix.alleyGarage() && sideRoom >= 7) {
            sx = driveLeft ? sideRoom - 2 - rng.nextInt(2) : 1 + rng.nextInt(2);
        } else {
            sx = sideRoom / 2 + (sideRoom > 4 ? rng.nextInt(3) - 1 : 0);
        }
        House.Condition cond = House.condition(rng);
        House.Palette pal = House.palette(style, rng);
        House h = new House(p.shifted(sx, 0, sb), rng, style, cond, pal, hw, hd, floors);
        if (rng.nextDouble() < mix.lit() && cond != House.Condition.BURNT && cond != House.Condition.RUIN) {
            h.lit(0.35 + rng.nextDouble() * 0.4);
        }
        h.build();
        int bodyFront = sb + h.frontZ();
        int doorX = sx + h.doorX();

        // the drive: to the garage door, or down the side yard to a pad (or the alley garage)
        int dx0, dx1, dz1;
        String drive = pick("floor_concrete", "floor_concrete", "asphalt", "minecraft:gravel");
        if (garage && h.garageX0() >= 0) {
            dx0 = sx + h.garageX0() + 1;
            dx1 = sx + h.garageX1() - 1;
            dz1 = sb - 1;
        } else if (!mix.alleyGarage() && sideRoom >= 7) {
            dx0 = driveLeft ? 1 : sx + hw + 1;
            dx1 = driveLeft ? sx - 2 : lw - 2;
            dx0 = Math.max(dx0, dx1 - 3);
            dz1 = Math.min(ld - 4, bodyFront + 4 + rng.nextInt(4));
        } else {
            dx0 = dx1 = -1;
            dz1 = -1;
        }
        if (dx0 >= 0) {
            for (int x = dx0; x <= dx1; x++) {
                for (int z = 0; z <= dz1; z++) {
                    if (p.get(x, 0, z) == null) {
                        p.set(x, 0, z, material(drive));
                    }
                }
            }
            if (rng.nextDouble() < mix.cars()) {
                double cz = garage ? Math.max(3.0, sb - 3.2) : Math.max(3.0, dz1 - 3.5);
                boolean noseIn = rng.nextDouble() < 0.7;
                p.car((dx0 + dx1 + 1) / 2.0, 1, cz, noseIn ? 0 : 180, 0, rng.nextDouble() < 0.25 ? 6.5 : 5.6, rng.nextLong());
            }
        }
        if (mix.alleyGarage()) {
            alleyGarage(p, lw, ld, pal);
        }

        // the front walk, and maybe a branch to the drive
        String walk = WALKS[rng.nextInt(WALKS.length)];
        int walkTo = h.frontZ() > 0 ? sb - 1 : bodyFront - 1;
        for (int z = 0; z <= walkTo; z++) {
            if (p.get(doorX, 0, z) == null) {
                p.set(doorX, 0, z, rng.nextDouble() < 0.1 ? "minecraft:grass_block" : walk);
            }
        }
        // mailbox on its post at the curb, by the drive or the walk
        if (rng.nextDouble() < 0.9) {
            int mx = dx0 >= 0 ? (dx0 > doorX ? dx0 - 1 : dx1 + 1) : doorX + 2;
            if (mx >= 0 && mx < lw && p.get(mx, 1, 0) == null) {
                p.set(mx, 0, 0, "minecraft:grass_block");
                p.set(mx, 1, 0, rng.nextDouble() < 0.12 ? "yd_deck_post" : "mailbox[facing=north]");
            }
        }
        // bins left out for a collection that never came
        if (dx0 >= 0 && rng.nextDouble() < 0.6) {
            int bx = dx1 + 1 < lw - 1 && dx1 + 1 != doorX ? dx1 + 1 : dx0 - 1;
            if (bx >= 0 && bx < lw) {
                p.setIfAir(bx, 1, 1, rng.nextDouble() < 0.2 ? "trash_bag[facing=north]" : "ext_trash_can");
                double r = rng.nextDouble();
                p.setIfAir(bx, 1, 2, r < 0.4 ? "trash_bag[facing=east]" : r < 0.6 ? "ext_trash_can_tipped[facing=north]" : "ext_trash_can");
            }
        }

        if (style == House.Style.TRAILER) {
            deck(p, doorX, sb);
        }
        if (mix.grand()) {
            grandFront(p, lw, sb, sx, hw, doorX, dx0, dx1, style);
            grandBack(p, lw, ld, sb + hd, pal);
        } else {
            frontEdge(p, lw, sb, doorX, dx0, dx1, mix);
            backYard(p, lw, ld, sx, hw, sb + hd, mix, pal);
        }
        sides(p, lw, sx, hw, sb, hd);
        aftermath(p, cond, lw, sb, doorX, bodyFront);
        // trees: one out front, one or two behind, clear of the house
        if (rng.nextDouble() < 0.55) {
            int tx = doorX < lw / 2 ? lw - 3 - rng.nextInt(3) : 2 + rng.nextInt(3);
            if (tx < dx0 - 1 || tx > dx1 + 1) {
                tree(p, tx, Math.max(2, sb / 2), sb >= 9 ? kit.yardTree() : Trees.Kind.YOUNG);
            }
        }
        int behind = rng.nextInt(3);
        for (int i = 0; i < behind && ld - (sb + hd) > 9; i++) {
            int tz = sb + hd + 5 + rng.nextInt(Math.max(1, ld - sb - hd - 7));
            tree(p, 2 + rng.nextInt(Math.max(1, lw - 4)), Math.min(ld - 3, tz), kit.yardTree());
        }
        Kit.lawn(p, rng, 0, 0, lw - 1, ld - 1, mix.wild());
    }

    private String material(String drive) {
        if (drive.equals("asphalt")) {
            return kit.asphalt(0.4);
        }
        if (drive.equals("floor_concrete")) {
            double r = rng.nextDouble();
            return r < 0.08 ? "sidewalk_cracked" : r < 0.12 ? "minecraft:moss_block" : "floor_concrete";
        }
        return rng.nextDouble() < 0.2 ? "minecraft:coarse_dirt" : drive;
    }

    /** A yard tree on its bed of soil, if it fits clear of the house, the drive and the car. */
    private void tree(Placer p, int x, int z, Trees.Kind kind) {
        Kit.plant(new Kit.Spot() {
            @Override
            public String get(int x, int y, int z) {
                return p.get(x, y, z);
            }

            @Override
            public void set(int x, int y, int z, String block) {
                p.set(x, y, z, block);
            }

            @Override
            public boolean hard(int x, int z) {
                return !Kit.soil(p.get(x, 0, z));
            }
        }, kind, x, z, true, rng);
    }

    /** An empty lot: the house is long gone, or was never built. */
    private void yardOnly(Placer p, int lw, int ld, Mix mix) {
        Kit.litter(p, rng, 0, 0, lw - 1, ld - 1, 0.03);
        if (rng.nextBoolean()) {
            tree(p, lw / 2, ld / 2, kit.yardTree());
        }
        Kit.lawn(p, rng, 0, 0, lw - 1, ld - 1, Math.min(1, mix.wild() + 0.3));
    }

    /** Picket fence, hedge or nothing along the street, with gaps for the walk and the drive. */
    private void frontEdge(Placer p, int lw, int sb, int doorX, int dx0, int dx1, Mix mix) {
        double r = rng.nextDouble();
        boolean picket = r < mix.picket(), hedge = !picket && r < mix.picket() + mix.hedge();
        if (!picket && !hedge) {
            return;
        }
        String leaves = rng.nextBoolean() ? "minecraft:oak_leaves" : rng.nextBoolean() ? "minecraft:azalea_leaves" : "minecraft:dark_oak_leaves";
        String paint = rng.nextDouble() < 0.75 ? "white" : "bare";
        for (int x = 0; x < lw; x++) {
            boolean gap = Math.abs(x - doorX) <= (picket ? 0 : 1) || (dx0 >= 0 && x >= dx0 - 1 && x <= dx1 + 1);
            if (gap || p.get(x, 1, 0) != null) {
                continue;
            }
            if (picket) {
                picket(p, x, 0, "x", paint, x % 4 == 0 || x == lw - 1 || x == doorX - 1 || x == doorX + 1 || x == dx0 - 2 || x == dx1 + 2);
            } else {
                Kit.hedge(p, rng, x, 0, x, 0, leaves);
            }
        }
        if (picket && rng.nextDouble() < 0.6) {
            // the sides of the front yard too
            for (int z = 1; z < sb; z++) {
                if (p.get(0, 1, z) == null) {
                    picket(p, 0, z, "z", paint, z % 4 == 0 || z == sb - 1);
                }
                if (p.get(lw - 1, 1, z) == null && !(dx0 >= 0 && dx1 >= lw - 2)) {
                    picket(p, lw - 1, z, "z", paint, z % 4 == 0 || z == sb - 1);
                }
            }
            if (rng.nextBoolean()) {
                p.set(doorX, 1, 0, "yd_picket_gate_" + paint + (rng.nextBoolean() ? "_open" : "") + "[axis=x]");
            }
        }
    }

    /** One cell of picket fence along an axis: a post, a run of pickets, or a run with pickets kicked out. */
    private void picket(Placer p, int x, int z, String axis, String paint, boolean post) {
        double r = rng.nextDouble();
        if (r < 0.05) {
            return;
        }
        String kind = post ? "yd_picket_post_" : r < 0.15 ? "yd_picket_" + paint + "_broken" : "yd_picket_";
        p.set(x, 1, z, kind + (kind.endsWith("_") ? paint : "") + "[axis=" + axis + "]");
    }

    /** AC unit, gas meter, the side gate. */
    private void sides(Placer p, int lw, int sx, int hw, int sb, int hd) {
        if (rng.nextDouble() < 0.6) {
            int x = rng.nextBoolean() ? sx - 1 : sx + hw;
            int z = sb + hd - 3;
            String there = x >= 0 && x < lw ? p.get(x, 1, z) : "out";
            if (there == null || there.startsWith("ext_plinth")) {
                p.set(x, 0, z, "floor_concrete");
                p.set(x, 1, z, "ext_ac");
            }
        }
        if (rng.nextDouble() < 0.25) {
            int x = rng.nextBoolean() ? sx - 1 : sx + hw;
            int z = sb + hd - 6;
            if (x >= 0 && x < lw && p.get(x, 1, z) == null) {
                p.set(x, 1, z, "propane_tank[axis=z]");
            }
        }
    }

    /** What happened here: litter, blood, sandbags, a fire. */
    private void aftermath(Placer p, House.Condition cond, int lw, int sb, int doorX, int bodyFront) {
        switch (cond) {
            case LOOTED -> {
                Kit.litter(p, rng, 0, 0, lw - 1, sb - 1, 0.05);
                if (rng.nextDouble() < 0.4) {
                    // a suitcase and a mattress dragged onto the lawn
                    p.setIfAir(doorX + 2, 1, sb - 2, "cardboard_box[facing=east]");
                    p.setIfAir(doorX - 2, 1, sb - 3, "home_mattress_floor[facing=" + (rng.nextBoolean() ? "east" : "north") + "]");
                }
            }
            case BOARDED -> {
                // the last stand at the front door
                if (rng.nextDouble() < 0.6) {
                    for (int x = doorX - 2; x <= doorX + 2; x++) {
                        if (x != doorX) {
                            p.setIfAir(x, 1, bodyFront - 2, "sandbags[facing=north]");
                        }
                    }
                }
                Kit.litter(p, rng, 0, 0, lw - 1, sb - 1, 0.02);
            }
            case BURNT -> {
                for (int i = 0; i < 6; i++) {
                    p.setIfAir(rng.nextInt(lw), 1, rng.nextInt(Math.max(1, sb)), "scorch");
                }
            }
            case RUIN -> Kit.litter(p, rng, 0, 0, lw - 1, sb - 1, 0.06);
            default -> {
            }
        }
        if (rng.nextDouble() < 0.18) {
            // a trail of blood down the walk
            for (int z = sb - 1; z >= 0; z -= 1 + rng.nextInt(2)) {
                if (p.get(doorX, 1, z) == null) {
                    p.set(doorX, 1, z, "blood_splat");
                }
            }
        }
    }

    /** A trailer's wooden deck and steps at the door, a rail round it. */
    private void deck(Placer p, int doorX, int sb) {
        for (int x = doorX - 2; x <= doorX + 2; x++) {
            for (int z = sb - 3; z < sb; z++) {
                if (z < 0) {
                    continue;
                }
                p.set(x, 0, z, "ext_porch_boards");
                boolean side = x == doorX - 2 || x == doorX + 2, front = z == sb - 3;
                if (side && front) {
                    p.set(x, 1, z, "yd_deck_post");
                } else if ((side || front) && !(x == doorX && front) && rng.nextDouble() < 0.9) {
                    p.set(x, 1, z, "yd_deck_rail[axis=" + (front ? "x" : "z") + "]");
                }
            }
        }
        if (sb - 4 >= 0) {
            p.set(doorX, 0, sb - 4, "ext_stairs_wood[facing=south]");
        }
        if (rng.nextBoolean()) {
            p.setIfAir(doorX + 1, 1, sb - 2, "store_chair[facing=north]");
        }
    }

    // ------------------------------------------------------------------ the big houses

    /** Iron railings between brick piers along the street, and a turning circle with a fountain. */
    private void grandFront(Placer p, int lw, int sb, int sx, int hw, int doorX, int dx0, int dx1, House.Style style) {
        String pier = pick("minecraft:bricks", "minecraft:stone_bricks", "brick_buff");
        for (int x = 0; x < lw; x++) {
            boolean gate = dx0 >= 0 && x >= dx0 - 1 && x <= dx1 + 1 || x == doorX;
            if (gate) {
                continue;
            }
            if (x % 6 == 0 || x == lw - 1 || x == dx0 - 2 || x == dx1 + 2) {
                p.set(x, 1, 0, pier);
                p.set(x, 2, 0, pier);
                p.set(x, 3, 0, rng.nextDouble() < 0.25 ? "minecraft:lantern" : "minecraft:stone_brick_slab[type=bottom]");
            } else if (rng.nextDouble() > 0.06) {
                p.set(x, 1, 0, "minecraft:iron_bars");
                if (rng.nextDouble() > 0.1) {
                    p.set(x, 2, 0, "minecraft:iron_bars");
                }
            }
        }
        // a hedge down each side of the front lawn
        Kit.hedge(p, rng, 1, 1, 1, sb - 2, "minecraft:dark_oak_leaves");
        Kit.hedge(p, rng, lw - 2, 1, lw - 2, sb - 2, "minecraft:dark_oak_leaves");
        if (style == House.Style.MANSION && sb >= 12) {
            // a gravel turning circle round a dry fountain at the front steps
            int cx = sx + hw / 2, cz = sb / 2;
            for (int x = cx - 6; x <= cx + 6; x++) {
                for (int z = cz - 5; z <= cz + 5; z++) {
                    double dist = Math.hypot((x - cx) / 6.0, (z - cz) / 5.0);
                    if (dist <= 1 && dist >= 0.55 && z >= 1 && z < sb) {
                        p.set(x, 0, z, rng.nextDouble() < 0.15 ? "minecraft:coarse_dirt" : "minecraft:gravel");
                    }
                }
            }
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    boolean rim = Math.abs(i) == 2 || Math.abs(j) == 2;
                    p.set(cx + i, 0, cz + j, "minecraft:stone_bricks");
                    p.set(cx + i, 1, cz + j, rim ? "minecraft:stone_brick_slab[type=bottom]" : "minecraft:water");
                }
            }
            p.set(cx, 1, cz, "minecraft:stone_brick_wall");
            p.set(cx, 2, cz, "minecraft:stone_brick_wall");
            p.set(cx, 3, cz, "minecraft:chiseled_stone_bricks");
            p.set(cx + 1, 2, cz, "minecraft:lily_pad");
            p.set(cx - 1, 2, cz + 1, "minecraft:lily_pad");
            for (int z = cz + 5; z < sb; z++) {
                p.set(doorX, 0, z, "minecraft:gravel");
            }
        }
    }

    /** A pool, a tennis court or a formal garden, and a pool house. */
    private void grandBack(Placer p, int lw, int ld, int houseBack, House.Palette pal) {
        int z0 = houseBack + 2, z1 = ld - 3;
        if (z1 - z0 < 10) {
            return;
        }
        Kit.chainlink(p, rng, 0, houseBack - 2, 0, ld - 1, 2);
        Kit.chainlink(p, rng, lw - 1, houseBack - 2, lw - 1, ld - 1, 2);
        Kit.chainlink(p, rng, 0, ld - 1, lw - 1, ld - 1, 2);
        // the pool, green with algae, its deck and a diving board
        int pw = Math.min(7, lw - 10), pd = Math.min(12, z1 - z0 - 4);
        int px = 3, pz = z0 + 1;
        if (pw >= 4 && pd >= 6) {
            for (int x = px - 1; x <= px + pw; x++) {
                for (int z = pz - 1; z <= pz + pd; z++) {
                    boolean rim = x == px - 1 || x == px + pw || z == pz - 1 || z == pz + pd;
                    if (rim) {
                        p.set(x, 0, z, "lobby_floor_white");
                        continue;
                    }
                    boolean deep = z > pz + pd / 2;
                    p.set(x, 0, z, "minecraft:water");
                    p.set(x, -1, z, "minecraft:water");
                    if (deep) {
                        p.set(x, -2, z, "minecraft:water");
                    }
                    p.set(x, deep ? -3 : -2, z, "minecraft:prismarine_bricks");
                    if (rng.nextDouble() < 0.08) {
                        p.set(x, 1, z, "minecraft:lily_pad");
                    }
                }
            }
            for (int x = px - 1; x <= px + pw; x++) {
                for (int y = -3; y <= -1; y++) {
                    p.set(x, y, pz - 1, "minecraft:prismarine_bricks");
                    p.set(x, y, pz + pd, "minecraft:prismarine_bricks");
                }
            }
            for (int z = pz - 1; z <= pz + pd; z++) {
                for (int y = -3; y <= -1; y++) {
                    p.set(px - 1, y, z, "minecraft:prismarine_bricks");
                    p.set(px + pw, y, z, "minecraft:prismarine_bricks");
                }
            }
            p.set(px + pw / 2, 1, pz + pd, "minecraft:smooth_quartz_slab[type=bottom]");
            p.set(px + pw / 2, 1, pz + pd - 1, "minecraft:smooth_quartz_slab[type=bottom]");
            for (int z = pz; z < pz + pd; z += 3) {
                p.setIfAir(px + pw + 1, 1, z, "store_chair[facing=west]");
            }
            // the pool house
            int hx = px + pw + 3;
            if (hx + 6 < lw - 1) {
                for (int x = hx; x < hx + 6; x++) {
                    for (int z = pz; z < pz + 5; z++) {
                        boolean edge = x == hx || x == hx + 5 || z == pz || z == pz + 4;
                        p.set(x, 0, z, "floor_checker");
                        for (int y = 1; y <= 3; y++) {
                            p.set(x, y, z, edge ? (y == 2 && x == hx && z == pz + 2 ? "window[axis=z]" : pal.wall()) : "air");
                        }
                        p.set(x, 4, z, "minecraft:deepslate_tile_slab[type=bottom]");
                    }
                }
                p.set(hx, 1, pz + 2, "minecraft:air");
                p.set(hx + 4, 1, pz + 3, "minecraft:barrel[facing=up,loot=storage]");
            }
        }
        // a tennis court further back if there's room, otherwise a formal garden
        int tz = pz + pd + 3;
        if (ld - 3 - tz >= 22 && lw >= 16) {
            int tx = (lw - 13) / 2;
            for (int x = tx; x < tx + 13; x++) {
                for (int z = tz; z < tz + 22; z++) {
                    boolean line = x == tx + 1 || x == tx + 11 || z == tz + 1 || z == tz + 20 || (x == tx + 6 && z > tz + 5 && z < tz + 16);
                    p.set(x, 0, z, line ? "minecraft:white_concrete" : rng.nextDouble() < 0.06 ? "minecraft:moss_block" : "minecraft:green_terracotta");
                }
                p.set(x, 1, tz + 11, rng.nextDouble() < 0.15 ? "air" : "chainlink[axis=x]");
            }
            Kit.chainlink(p, rng, tx - 1, tz - 1, tx + 13, tz - 1, 3);
            Kit.chainlink(p, rng, tx - 1, tz + 22, tx + 13, tz + 22, 3);
            Kit.chainlink(p, rng, tx - 1, tz - 1, tx - 1, tz + 22, 3);
            Kit.chainlink(p, rng, tx + 13, tz - 1, tx + 13, tz + 22, 3);
        } else if (ld - 3 - tz >= 9) {
            int gx = lw / 2 - 4;
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    boolean border = i == 0 || j == 0 || i == 8 || j == 8 || i == 4 || j == 4;
                    p.set(gx + i, 0, tz + j, border ? "minecraft:gravel" : "minecraft:grass_block");
                    if (!border && rng.nextDouble() < 0.4) {
                        p.set(gx + i, 1, tz + j, "minecraft:rose_bush[half=lower]");
                        p.set(gx + i, 2, tz + j, "minecraft:rose_bush[half=upper]");
                    } else if (!border) {
                        p.set(gx + i, 1, tz + j, "minecraft:azalea_leaves[persistent=true]");
                    }
                }
            }
            p.set(gx + 4, 1, tz + 4, "minecraft:stone_brick_wall");
            p.set(gx + 4, 2, tz + 4, "minecraft:cauldron");
        }
    }

    // ------------------------------------------------------------------ back yard

    /** A detached garage at the back of the lot with its door on the alley. */
    private void alleyGarage(Placer p, int lw, int ld, House.Palette pal) {
        int gw = Math.min(7, lw - 2), gd = 7;
        int x0 = lw - gw - 1, z0 = ld - gd;
        String wall = rng.nextBoolean() ? pal.wall() : pick("siding_white", "barn_grey", "corrugated_grey", "barn_red");
        boolean open = rng.nextDouble() < 0.4;
        for (int x = x0; x < x0 + gw; x++) {
            for (int z = z0; z < z0 + gd; z++) {
                boolean edge = x == x0 || x == x0 + gw - 1 || z == z0 || z == z0 + gd - 1;
                p.set(x, 0, z, "floor_concrete");
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z, edge ? wall : "air");
                }
            }
        }
        tinRoof(p, x0, z0, x0 + gw - 1, z0 + gd - 1, 4);
        for (int x = x0 + 1; x < x0 + gw - 1; x++) {
            for (int y = 1; y <= 3; y++) {
                p.set(x, y, z0 + gd - 1, open && y < 3 ? "air" : "garage_door[facing=south]");
            }
            // the apron out to the alley
            p.set(x, 0, ld - 1, "floor_concrete");
        }
        Doors.abandoned(p, rng, x0 + 1, 1, z0, "yd_shed_door", "north", "left");
        p.set(x0 + gw - 2, 1, z0 + 1, "home_garage_shelf[facing=west,loot=hardware]");
        p.set(x0 + gw - 2, 1, z0 + 2, rng.nextBoolean() ? "store_paint_cans" : "fm_drum[loot=storage]");
        p.set(x0 + 1, 1, z0 + 2, "home_workbench[facing=east]");
        if (rng.nextDouble() < 0.4) {
            p.car(x0 + gw / 2.0, 1, z0 + gd / 2.0, 0, 0, 5.2, rng.nextLong());
        }
    }

    private void backYard(Placer p, int lw, int ld, int sx, int hw, int houseBack, Mix mix, House.Palette pal) {
        int z0 = houseBack + 1, z1 = ld - 2 - (mix.alleyGarage() ? 8 : 0);
        if (z1 - z0 < 4) {
            return;
        }
        // the fence round the back
        double r = rng.nextDouble();
        int fz = Math.max(1, houseBack - 3);
        if (r < mix.privacy()) {
            String wood = pick("cedar", "grey", "grey", "brown");
            Kit.privacyFence(p, rng, 0, fz, 0, ld - 1, wood);
            Kit.privacyFence(p, rng, lw - 1, fz, lw - 1, ld - 1, wood);
            if (!mix.alleyGarage()) {
                Kit.privacyFence(p, rng, 0, ld - 1, lw - 1, ld - 1, wood);
            }
        } else if (r < mix.privacy() + mix.chainlink()) {
            Kit.chainlink(p, rng, 0, fz, 0, ld - 1, 1);
            Kit.chainlink(p, rng, lw - 1, fz, lw - 1, ld - 1, 1);
            if (!mix.alleyGarage()) {
                Kit.chainlink(p, rng, 0, ld - 1, lw - 1, ld - 1, 1);
            }
        }
        boolean[][] used = new boolean[lw][ld];
        int features = 1 + rng.nextInt(3);
        for (int i = 0; i < features; i++) {
            feature(p, used, 1, z0, lw - 2, z1, pal);
        }
    }

    /** One back-yard thing, wherever it fits. */
    private void feature(Placer p, boolean[][] used, int x0, int z0, int x1, int z1, House.Palette pal) {
        int kind = rng.nextInt(11);
        int[] dims = switch (kind) {
            case 0 -> new int[] {5, 4};   // shed
            case 1 -> new int[] {5, 3};   // swing set
            case 2 -> new int[] {5, 5};   // above-ground pool
            case 3 -> new int[] {3, 3};   // trampoline
            case 4 -> new int[] {4, 4};   // patio and grill
            case 5 -> new int[] {4, 6};   // vegetable patch
            case 6 -> new int[] {7, 1};   // washing line
            case 7 -> new int[] {2, 3};   // dog house
            case 8 -> new int[] {3, 3};   // sandpit
            case 9 -> new int[] {3, 3};   // fire pit
            default -> new int[] {4, 7};  // a car or a boat left to rot
        };
        for (int tries = 0; tries < 8; tries++) {
            int fw = dims[0], fd = dims[1];
            if (x1 - x0 + 1 < fw || z1 - z0 + 1 < fd) {
                return;
            }
            int fx = x0 + rng.nextInt(x1 - x0 - fw + 2), fz = z0 + rng.nextInt(z1 - z0 - fd + 2);
            boolean clear = true;
            for (int x = fx - 1; x <= fx + fw && clear; x++) {
                for (int z = fz - 1; z <= fz + fd && clear; z++) {
                    if (x >= 0 && z >= 0 && x < used.length && z < used[0].length && used[x][z]) {
                        clear = false;
                    }
                    if (x >= fx && x < fx + fw && z >= fz && z < fz + fd && p.get(x, 1, z) != null) {
                        clear = false;
                    }
                }
            }
            if (!clear) {
                continue;
            }
            for (int x = fx; x < fx + fw; x++) {
                for (int z = fz; z < fz + fd; z++) {
                    used[x][z] = true;
                }
            }
            build(kind, p, fx, fz, fw, fd, pal);
            return;
        }
    }

    private void build(int kind, Placer p, int x, int z, int fw, int fd, House.Palette pal) {
        switch (kind) {
            case 0 -> shed(p, x, z, fw, fd);
            case 1 -> {
                // the swing set: the A-frames at each end, three bays under the bar, a swing snapped
                for (int y = 1; y <= 3; y++) {
                    p.set(x, y, z + 1, "sc_swing_leg_" + (y - 1) + "[axis=x]");
                    p.set(x + 4, y, z + 1, "sc_swing_leg_" + (y - 1) + "[axis=x]");
                }
                for (int i = 1; i <= 3; i++) {
                    double r = rng.nextDouble();
                    if (r < 0.75) {
                        p.set(x + i, 1, z + 1, (r < 0.6 ? "sc_swing_seat" : "sc_swing_snapped") + "[axis=x]");
                        p.set(x + i, 2, z + 1, "sc_swing_chain[axis=x]");
                        p.set(x + i, 3, z + 1, "sc_swing_top[axis=x]");
                    } else {
                        p.set(x + i, 3, z + 1, (r < 0.9 ? "sc_swing_wrapped" : "sc_swing_bar") + "[axis=x]");
                    }
                    p.set(x + i, 0, z + 1, "minecraft:coarse_dirt");
                }
            }
            case 2 -> {
                // an above-ground pool, its walls printed and faded, green and full of leaves
                String c = rng.nextBoolean() ? "blue" : "white";
                int ladder = rng.nextInt(3);
                for (int i = 0; i < 5; i++) {
                    for (int j = 0; j < 5; j++) {
                        boolean we = i == 0 || i == 4, ns = j == 0 || j == 4;
                        p.set(x + i, 0, z + j, "minecraft:sand");
                        String b;
                        if (we && ns) {
                            b = "yd_pool_corner_" + c + "[facing=" + (i == 0 ? (j == 0 ? "north" : "west") : (j == 0 ? "east" : "south")) + "]";
                        } else if (we || ns) {
                            String f = j == 0 ? "north" : j == 4 ? "south" : i == 0 ? "west" : "east";
                            b = (j == 4 && i == 1 + ladder ? "yd_pool_ladder_" : "yd_pool_edge_") + c + "[facing=" + f + "]";
                        } else {
                            b = rng.nextDouble() < 0.25 ? "yd_pool_water_b" : "yd_pool_water";
                        }
                        p.set(x + i, 1, z + j, b);
                    }
                }
            }
            case 3 -> {
                // a trampoline: the frame and the pad reach over the eight cells round the middle
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        p.set(x + i, 1, z + j, "air");
                    }
                }
                p.set(x + 1, 1, z + 1, rng.nextDouble() < 0.4 ? "yd_tramp_torn" : "yd_tramp");
            }
            case 4 -> {
                String paving = pick("pt_stone_flags", "pt_brick", "sidewalk", "floor_concrete");
                p.fill(x, 0, z, x + 3, 0, z + 3, paving);
                p.set(x, 1, z, "store_grill[facing=south]");
                p.set(x + 2, 1, z + 2, "store_table");
                p.set(x + 1, 1, z + 2, rng.nextBoolean() ? "store_chair[facing=east]" : "air");
                p.set(x + 3, 1, z + 2, "store_chair[facing=west]");
                p.set(x + 2, 1, z + 3, rng.nextDouble() < 0.3 ? "minecraft:air" : "store_chair[facing=north]");
                if (rng.nextBoolean()) {
                    p.set(x + 3, 1, z, "propane_tank[axis=z]");
                }
            }
            case 5 -> {
                // a vegetable patch gone to seed, a scarecrow keeping watch
                String[] crops = {"minecraft:wheat[age=7]", "minecraft:carrots[age=7]", "minecraft:potatoes[age=7]",
                        "minecraft:beetroots[age=3]", "minecraft:wheat[age=4]"};
                for (int i = 0; i < 4; i++) {
                    for (int j = 0; j < 6; j++) {
                        boolean edge = i == 0 || i == 3 || j == 0 || j == 5;
                        if (edge) {
                            p.set(x + i, 0, z + j, "minecraft:coarse_dirt");
                            continue;
                        }
                        p.set(x + i, 0, z + j, "minecraft:farmland[moisture=0]");
                        double r = rng.nextDouble();
                        p.set(x + i, 1, z + j, r < 0.6 ? crops[rng.nextInt(crops.length)] : r < 0.8 ? "minecraft:short_grass" : "minecraft:dead_bush");
                    }
                }
                if (rng.nextBoolean()) {
                    p.set(x, 1, z, "fm_scarecrow_lo[facing=south]");
                    p.set(x, 2, z, "fm_scarecrow_hi[facing=south]");
                }
            }
            case 6 -> {
                // the washing line between its T-posts, a sheet or the shirts still pegged out
                p.set(x, 1, z, "yd_line_post[axis=x]");
                p.set(x + 6, 1, z, "yd_line_post[axis=x]");
                for (int i = 1; i < 6; i++) {
                    double r = rng.nextDouble();
                    p.set(x + i, 1, z, (r < 0.15 ? "yd_line_sheet" : r < 0.3 ? "yd_line_shirts" : "yd_line") + "[axis=x]");
                }
            }
            case 7 -> {
                // a dog house, its chain still staked out front, the bowl
                p.set(x, 1, z + 1, "yd_doghouse_" + (rng.nextBoolean() ? "red" : "white") + "[facing=north]");
                p.set(x, 0, z, "minecraft:coarse_dirt");
                p.set(x + 1, 1, z, rng.nextBoolean() ? "yd_dog_stake[facing=" + pick("north", "east", "south", "west") + "]" : "yd_dog_bowl");
            }
            case 8 -> {
                // a sandbox, its timber edge round it, a bucket left in it
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        boolean n = j == 0, s = j == 2, w = i == 0, e = i == 2;
                        String b;
                        if ((n || s) && (w || e)) {
                            b = "sc_sand_corner[facing=" + (n ? (w ? "north" : "east") : (e ? "south" : "west")) + "]";
                        } else if (n || s || w || e) {
                            b = "sc_sand_edge[facing=" + (n ? "north" : s ? "south" : w ? "west" : "east") + "]";
                        } else {
                            b = "sc_sand";
                        }
                        p.set(x + i, 0, z + j, b);
                        p.set(x + i, 1, z + j, "air");
                    }
                }
                p.set(x + 1, 1, z + 1, "sc_bucket[facing=" + pick("north", "east", "south", "west") + "]");
            }
            case 9 -> {
                // a fire pit: the ring of stones, the last fire's logs, a log and a stump to sit on
                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        p.set(x + i, 0, z + j, (i == 1 && j == 1) || rng.nextDouble() < 0.5 ? "minecraft:coarse_dirt" : "minecraft:gravel");
                    }
                }
                p.set(x + 1, 1, z + 1, "yd_firering");
                p.set(x + 1, 2, z + 1, "air");
                p.set(x, 1, z + 2, "cp_log[facing=north]");
                p.set(x + 2, 1, z, "fm_chop_block");
            }
            default -> {
                if (rng.nextDouble() < 0.6) {
                    p.fill(x, 0, z, x + 3, 0, z + 6, "minecraft:coarse_dirt");
                    p.car(x + 2.0, 1, z + 3.5, rng.nextBoolean() ? 0 : 180, 0, 5.8, rng.nextLong());
                } else {
                    // a boat dragged up the yard for a winter that never ended, the grass grown up round her
                    p.fill(x, 0, z, x + 3, 0, z + 6, "minecraft:coarse_dirt");
                    for (int j = 0; j <= 6; j += 2) {
                        p.setIfAir(x, 1, z + j, "minecraft:short_grass");
                        p.setIfAir(x + 3, 1, z + j + 1, "minecraft:short_grass");
                    }
                    p.vehicle(x + 2.0, 1, z + 3.5, rng.nextBoolean() ? 0 : 180,
                            "=" + pick("boat_skiff", "boat_bowrider", "boat_console") + "@" + pick("abandoned", "abandoned", "wrecked"), rng.nextLong());
                }
            }
        }
        if (rng.nextDouble() < 0.1 && pal != null) {
            p.setIfAir(x + fw, 1, z, "yd_compost[facing=" + pick("north", "east", "south", "west") + "]");
        }
    }

    /** A garden shed: plank floor, a door, a slab roof, tools and junk inside. */
    private void shed(Placer p, int x, int z, int sw, int sd) {
        String wall = SHEDS[rng.nextInt(SHEDS.length)];
        for (int i = 0; i < sw; i++) {
            for (int j = 0; j < sd; j++) {
                boolean edge = i == 0 || j == 0 || i == sw - 1 || j == sd - 1;
                p.set(x + i, 0, z + j, "floor_boards");
                for (int y = 1; y <= 2; y++) {
                    p.set(x + i, y, z + j, edge ? wall : "air");
                }
            }
        }
        tinRoof(p, x, z, x + sw - 1, z + sd - 1, 3);
        Doors.abandoned(p, rng, x + 1, 1, z, "yd_shed_door", "south", "left");
        p.set(x + sw - 2, 1, z + sd - 2, "home_garage_shelf[facing=north,loot=hardware]");
        p.set(x + sw - 2, 1, z + 1, rng.nextBoolean() ? "store_paint_cans" : "home_mower[facing=west]");
        if (rng.nextBoolean()) {
            p.set(x + 1, 1, z + sd - 2, "tire[axis=x]");
        }
    }

    /** A tin roof from (x0, z0) to (x1, z1) at y: the sheets on their purlins, the edges reaching over the walls. */
    private static void tinRoof(Placer p, int x0, int z0, int x1, int z1, int y) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                String sides = (z == z0 ? "n" : z == z1 ? "s" : "") + (x == x0 ? "w" : x == x1 ? "e" : "");
                p.set(x, y, z, "cp_tw_roof" + (sides.isEmpty() ? "" : "_" + sides));
            }
        }
    }

    private String pick(String... options) {
        return options[rng.nextInt(options.length)];
    }
}
