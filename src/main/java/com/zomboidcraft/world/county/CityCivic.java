package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Placer;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The big city's public places, and what runs under and over its streets, for
 * {@link DowntownSite}:
 * <ul>
 * <li>the plaza: granite paving, the fountain gone green or dry, a bronze statue or an obelisk,
 * trees in their pits, benches and purple lamps, and the FEMA aid station put up in it;</li>
 * <li>the old city's courthouse square;</li>
 * <li>the general hospital: EMERGENCY over the ambulance bay, a red cross that still glows on
 * the ward tower, wards of beds, the pharmacy, triage tents in the side yard, the helipad on
 * the roof;</li>
 * <li>police headquarters: the blue panel and gold star over the doors, POLICE, the soul-blue
 * lamps, the cruisers nosed in, the front desk, offices, the armoury and the cells;</li>
 * <li>a parking garage of open concrete decks and ramps, or a surface lot;</li>
 * <li>the tower that was going up: steel frame over poured floors, scaffolding, hoarding, and
 * the tower crane with its red lights;</li>
 * <li>the subway station under the main avenue: stairs down from the sidewalk between green
 * globes, tiled walls, a train standing at the platform, tunnels running off to cave-ins and
 * the place where the street fell in;</li>
 * <li>the elevated railway on its steel piers, a station up on it, a span down in the street.</li>
 * </ul>
 * Lights are the city's: mostly purple, now and then green or amber, a lot of them dead.
 * Buildings are drawn through a {@link Placer} in their own frame (front along local z = 0
 * facing the street); the plaza, the square and the railways in the place's own coordinates.
 */
final class CityCivic {
    private static final String[] DIRS = {"north", "east", "south", "west"};

    private final Kit kit;
    private final BlockSink out;
    private final SplittableRandom rng;
    private final Placer world;
    private final double hit;
    /** The old city builds in brick and stone where the others build in concrete and glass. */
    boolean old;
    /** Vehicles put down here, so the streets can keep the downtown's total in hand. */
    int cars;
    /** The most vehicles the civic blocks may put down between them. */
    int carCap = 14;
    /** The old city's courthouse, once the square is built: its frame and the room inside its walls. */
    Placer courthouse;
    int[] courtInterior;
    /** The floor of the courthouse's basement, where a shelter's stair comes up. */
    int courtFloor;

    CityCivic(Kit kit, double hit) {
        this.kit = kit;
        this.out = kit.out;
        this.rng = kit.rng;
        this.world = new Placer(out, 0, 0, 0, 0);
        this.hit = hit;
    }

    private double chance() {
        return rng.nextDouble();
    }

    private String any(String... options) {
        return options[rng.nextInt(options.length)];
    }

    private void vehicle(Placer p, double x, int y, double z, float yaw, String models) {
        if (cars < carCap) {
            cars++;
            p.vehicle(x, y, z, yaw, models, rng.nextLong());
        }
    }

    private void car(Placer p, double x, int y, double z, float yaw) {
        if (cars < carCap) {
            cars++;
            p.car(x, y, z, yaw, 0, 5.6, rng.nextLong());
        }
    }

    private Bldg bldg(Placer p) {
        Bldg b = new Bldg(p, rng);
        b.damage = 0.35 + hit * 0.55;
        b.boarded = 0.06 + chance() * 0.2;
        b.lit = 0.03;
        return b;
    }

    private String dry() {
        double r = chance();
        return r < 0.6 ? "drywall" : r < 0.72 ? "drywall_stain" : r < 0.84 ? "drywall_peel" : r < 0.92 ? "drywall_b" : "drywall_soot";
    }

    // ------------------------------------------------------------------ lamps and furniture

    /** A city lamp head: the purple most of them are, now and then green or amber, some dead. */
    String cityLamp(double dead) {
        if (chance() < dead) {
            return "minecraft:smooth_quartz";
        }
        double r = chance();
        return r < 0.7 ? "minecraft:pearlescent_froglight" : r < 0.85 ? "minecraft:verdant_froglight" : "minecraft:ochre_froglight";
    }

    /** A cast-iron lamp post with a glowing head, for plazas, squares and forecourts. */
    void lampPost(int x, int z, boolean lit) {
        lampPost(world, x, z, lit);
    }

    private void lampPost(Placer p, int x, int z, boolean lit) {
        if (chance() < 0.06 * hit) {
            // knocked down
            p.set(x, 1, z, "lamp_base");
            p.set(x + 1, 1, z, "lamp_post");
            p.set(x + 2, 1, z, "glass_shards");
            return;
        }
        p.set(x, 1, z, "lamp_base");
        p.set(x, 2, z, "lamp_post");
        p.set(x, 3, z, "lamp_post");
        p.set(x, 4, z, lit ? cityLamp(0) : "minecraft:smooth_quartz");
        p.set(x, 5, z, "minecraft:polished_blackstone_slab[type=bottom]");
    }

    /** A raised stone bed three blocks square with what grew wild in it. */
    void planter(int x, int z) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean rim = dx != 0 || dz != 0;
                out.set(x + dx, 1, z + dz, rim ? any("minecraft:stone_bricks", "minecraft:stone_bricks", "minecraft:cracked_stone_bricks",
                        "minecraft:mossy_stone_bricks") : "minecraft:rooted_dirt");
                if (rim && chance() < 0.2) {
                    out.set(x + dx, 2, z + dz, "minecraft:moss_carpet");
                }
            }
        }
        double r = chance();
        if (r < 0.45) {
            Trees.grow(kit.canopy(), new Trees.Kind[] {Trees.Kind.YOUNG, Trees.Kind.BUSH, Trees.Kind.SICK, Trees.Kind.DEAD}[rng.nextInt(4)],
                    x, 2, z, rng);
        } else if (r < 0.85) {
            out.set(x, 2, z, chance() < 0.5 ? "weeds_tall" : "weeds");
        }
    }

    /** A bench three long along an axis, its back to one side; a slat or two gone. */
    void bench(int x, int z, String axis) {
        boolean ax = axis.equals("x");
        String f = ax ? (chance() < 0.5 ? "north" : "south") : (chance() < 0.5 ? "east" : "west");
        for (int k = -1; k <= 1; k++) {
            int bx = ax ? x + k : x, bz = ax ? z : z + k;
            out.set(bx, 1, bz, chance() < 0.05 + 0.12 * hit ? "minecraft:dark_oak_slab[type=bottom]" : "minecraft:dark_oak_stairs[facing=" + f + "]");
        }
    }

    /** A street tree in its pit: a grate round the trunk, mostly maples and planes, some dead. */
    private void treePit(int x, int z) {
        out.set(x, 0, z, "minecraft:coarse_dirt");
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if ((dx != 0 || dz != 0) && chance() < 0.7) {
                    out.set(x + dx, 1, z + dz, "grate");
                }
            }
        }
        double r = chance();
        kit.tree(r < 0.4 ? Trees.Kind.MAPLE : r < 0.7 ? Trees.Kind.SYCAMORE : r < 0.82 ? Trees.Kind.OAK : r < 0.92 ? Trees.Kind.SICK
                : Trees.Kind.DEAD, x, z);
    }

    /**
     * Chandeliers in a tower's lobby (its own frame, lobby h high): purple glass on iron arms
     * hung on chains between the doors and the lift core, one in the hall behind it; one or two
     * come down and lie smashed on the marble.
     */
    void chandeliers(Placer pl, int tw, int td, int h) {
        int cx0 = (tw - 14) / 2, cz0 = (td - 10) / 2;
        if (cz0 >= 4) {
            int n = tw >= 34 ? 3 : 2;
            int z = Math.max(2, cz0 / 2);
            for (int i = 1; i <= n; i++) {
                chandelier(pl, tw * i / (n + 1), z, h);
            }
        }
        int back = cz0 + 10;
        if (td - 1 - back >= 5) {
            chandelier(pl, tw / 2, back + (td - 1 - back) / 2, h);
        }
        if (cx0 >= 5) {
            chandelier(pl, cx0 / 2 + 1, td / 2, h);
            chandelier(pl, tw - 2 - cx0 / 2, td / 2, h);
        }
    }

    private void chandelier(Placer p, int x, int z, int h) {
        if (chance() < 0.15 * hit) {
            p.set(x, 1, z, "glass_shards");
            p.setIfAir(x + 1, 1, z, "minecraft:iron_chain[axis=x]");
            p.setIfAir(x, 1, z + 1, "glass_shards");
            p.set(x, h - 1, z, "minecraft:iron_chain[axis=y]");
            return;
        }
        p.set(x, h - 1, z, "minecraft:iron_chain[axis=y]");
        p.set(x, h - 2, z, "minecraft:iron_chain[axis=y]");
        p.set(x, h - 3, z, cityLamp(0.2));
        for (int[] o : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            p.set(x + o[0], h - 3, z + o[1], "minecraft:iron_bars");
        }
    }

    // ------------------------------------------------------------------ the plaza

    /**
     * The civic plaza filling a block (box {x0, z0, x1, z1}): granite paving on a grid, the
     * fountain in the middle with its statue, a ring of lamps and benches, trees round the
     * edges, and along one side the aid station: field hospital, tents, crates, fence and wire.
     */
    void plaza(int[] box, Kit kit) {
        int x0 = box[0], z0 = box[1], x1 = box[2], z1 = box[3];
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int half = Math.min(x1 - x0, z1 - z0) / 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean band = Math.floorMod(x - cx, 8) == 0 || Math.floorMod(z - cz, 8) == 0;
                double r = chance();
                out.set(x, 0, z, band ? (r < 0.85 ? "minecraft:deepslate_tiles" : "minecraft:cracked_deepslate_tiles")
                        : r < 0.45 ? "minecraft:stone_bricks" : r < 0.7 ? "minecraft:polished_andesite" : r < 0.86 ? "minecraft:cracked_stone_bricks"
                        : r < 0.94 ? "minecraft:moss_block" : "minecraft:coarse_dirt");
                if (chance() < 0.035) {
                    out.set(x, 1, z, any("weeds", "grass_tuft", "leaf_litter", "paper", "minecraft:short_grass"));
                }
            }
        }
        int r = Math.max(5, Math.min(7, half / 4));
        fountain(cx, cz, r);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            int lx = cx + (int) Math.round(Math.cos(a) * (r + 4)), lz = cz + (int) Math.round(Math.sin(a) * (r + 4));
            if (i % 2 == 0) {
                lampPost(lx, lz, chance() < 0.75);
            } else {
                bench(lx, lz, Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? "z" : "x");
            }
        }
        int side = rng.nextInt(4);
        // trees round the edges, lamps between them; the aid station's side is left open
        for (int x = x0 + 3; x <= x1 - 3; x += 9) {
            if (side != 0) {
                treePit(x, z0 + 3);
            }
            if (side != 2) {
                treePit(x, z1 - 3);
            }
        }
        for (int z = z0 + 12; z <= z1 - 12; z += 9) {
            if (side != 3) {
                treePit(x0 + 3, z);
            }
            if (side != 1) {
                treePit(x1 - 3, z);
            }
        }
        for (int x = x0 + 7; x <= x1 - 7; x += 18) {
            lampPost(x, z0 + 1, chance() < 0.6);
            lampPost(x, z1 - 1, chance() < 0.6);
        }
        aidStation(x0, z0, x1, z1, cx, cz, half, r + 7, side);
        // what was left lying about
        for (int i = 0; i < (x1 - x0) * (z1 - z0) / 70; i++) {
            int x = x0 + rng.nextInt(x1 - x0 + 1), z = z0 + rng.nextInt(z1 - z0 + 1);
            if (out.get(x, 1, z) == null) {
                out.set(x, 1, z, any("paper", "paper", "blood_splat", "trash_bag[facing=north]", "cardboard_box", "glass_shards", "leaf_litter",
                        "minecraft:skeleton_skull[rotation=" + rng.nextInt(16) + "]"));
            }
        }
    }

    /** The fountain: a round stone basin, green water with lily pads or dried to mud, the statue in it. */
    private void fountain(int cx, int cz, int r) {
        boolean dry = chance() < 0.35;
        for (int dx = -r - 1; dx <= r + 1; dx++) {
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                double dd = Math.hypot(dx, dz);
                int x = cx + dx, z = cz + dz;
                if (dd > r + 0.5) {
                    continue;
                }
                if (dd > r - 0.6) {
                    out.set(x, -1, z, "minecraft:stone_bricks");
                    out.set(x, 0, z, chance() < 0.8 ? "minecraft:stone_bricks" : "minecraft:mossy_stone_bricks");
                    out.set(x, 1, z, chance() < 0.08 ? "air" : chance() < 0.7 ? "minecraft:smooth_stone_slab[type=bottom]"
                            : "minecraft:mossy_stone_brick_slab[type=bottom]");
                } else {
                    out.set(x, -1, z, "minecraft:stone_bricks");
                    if (dry) {
                        out.set(x, 0, z, any("minecraft:mud", "minecraft:coarse_dirt", "minecraft:moss_block", "minecraft:packed_mud"));
                        if (chance() < 0.2) {
                            out.set(x, 1, z, any("leaf_litter", "weeds", "paper", "trash_bag[facing=north]", "minecraft:short_grass"));
                        }
                    } else {
                        out.set(x, 0, z, "minecraft:water");
                        if (chance() < 0.14) {
                            out.set(x, 1, z, "minecraft:lily_pad");
                        }
                    }
                }
            }
        }
        // the pedestal and on it a figure in bronze gone green, or an obelisk
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                out.set(cx + dx, -1, cz + dz, "minecraft:stone_bricks");
                out.set(cx + dx, 0, cz + dz, "minecraft:stone_bricks");
                out.set(cx + dx, 1, cz + dz, dx == 0 && dz == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks");
                out.set(cx + dx, 2, cz + dz, "minecraft:polished_andesite");
            }
        }
        int s = 3;
        if (chance() < 0.6) {
            out.set(cx, s, cz, "minecraft:oxidized_copper");
            out.set(cx, s + 1, cz, "minecraft:oxidized_copper");
            out.set(cx, s + 2, cz, "minecraft:oxidized_cut_copper");
            out.set(cx + 1, s + 1, cz, "minecraft:oxidized_cut_copper_slab[type=top]");
            out.set(cx - 1, s + 2, cz, "minecraft:oxidized_cut_copper_slab[type=bottom]");
            out.set(cx - 1, s + 3, cz, "minecraft:lightning_rod");
            if (chance() < 0.3 * hit) {
                // the head knocked off, lying in the basin
                out.set(cx, s + 2, cz, "air");
                out.set(cx + 2, 1, cz + 1, "minecraft:oxidized_cut_copper");
            }
        } else {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    for (int y = s; y < s + 4; y++) {
                        out.set(cx + dx, y, cz + dz, "minecraft:smooth_stone");
                    }
                }
            }
            for (int y = s + 4; y < s + 12; y++) {
                out.set(cx, y, cz, y == s + 11 ? "minecraft:polished_andesite" : "minecraft:smooth_stone");
            }
            out.set(cx, s + 12, cz, "minecraft:lightning_rod");
        }
        // a lamp at each corner of the pedestal
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            if (chance() < 0.6) {
                out.set(cx + c[0], 3, cz + c[1], cityLamp(0.3));
            }
        }
    }

    /**
     * The aid station along one side of the plaza: the strip between the edge and the fountain's
     * ring, facing the fountain. A field hospital, tents in a row, crates under camo, a FEMA board,
     * chain-link and wire round it with gaps, the trucks that brought it.
     */
    private void aidStation(int x0, int z0, int x1, int z1, int cx, int cz, int half, int inner, int side) {
        int outer = half - 3;
        int ld = outer - inner + 1;
        if (ld < 8) {
            return;
        }
        int lw = ((side & 1) == 0 ? x1 - x0 + 1 : z1 - z0 + 1) - 8;
        int minX, minZ;
        switch (side) {
            case 0 -> { minX = x0 + 4; minZ = cz - outer; }
            case 2 -> { minX = x0 + 4; minZ = cz + inner; }
            case 1 -> { minX = cx + inner; minZ = z0 + 4; }
            default -> { minX = cx - outer; minZ = z0 + 4; }
        }
        // the front faces the fountain: the opposite way to the side it sits on
        Placer q = Placer.forLot(out, minX, 0, minZ, lw, ld, (side + 2) & 3);
        Camp camp = new Camp(q, rng, hit);
        int len = Math.min(14, ld - 2);
        int x = 1;
        camp.hospital(x, 1, 9, len);
        x += 11;
        String[] canvas = {"minecraft:white_wool", "minecraft:white_wool", "minecraft:light_gray_wool", "minecraft:light_blue_wool"};
        while (x + 6 <= lw - 10) {
            camp.tent(x, 1, Math.min(10, len), canvas[rng.nextInt(canvas.length)], true);
            x += 8;
        }
        camp.crates(lw - 9, 2, 6, Math.min(5, ld - 3));
        Boards.board(q, rng, "aid", 2, 3, -2);
        // jersey barriers across the front, a gap at the middle; wire round the back
        for (int i = 0; i < lw; i++) {
            if (Math.abs(i - lw / 2) > 2 && chance() < 0.85) {
                q.set(i, 1, -1, chance() < 0.75 ? "jersey_barrier[facing=north]" : "sandbags[facing=north]");
            }
        }
        camp.fence(0, ld - 1, lw - 1, ld - 1, 0.3);
        if (chance() < 0.7) {
            vehicle(q, lw - 4.5, 1, -4, 90 + (float) (chance() * 20 - 10), "jeep,jeep_open,box_truck");
        }
    }

    // ------------------------------------------------------------------ the courthouse square

    /** The old city's square (box {x0, z0, x1, z1}): the courthouse on its lawn, paths, the memorial, lamps and old trees. */
    void square(int[] box, Kit kit) {
        int x0 = box[0], z0 = box[1], x1 = box[2], z1 = box[3];
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x - x0 < 2 || x1 - x < 2 || z - z0 < 2 || z1 - z < 2;
                boolean path = edge || Math.abs(x - cx) <= 1 || Math.abs(z - cz) <= 1;
                out.set(x, 0, z, path ? (chance() < 0.12 ? "sidewalk_cracked" : "sidewalk") : "minecraft:grass_block");
                if (!path) {
                    double r = chance();
                    if (r < 0.1) {
                        out.set(x, 1, z, "minecraft:tall_grass[half=lower]");
                        out.set(x, 2, z, "minecraft:tall_grass[half=upper]");
                    } else if (r < 0.4) {
                        out.set(x, 1, z, "minecraft:short_grass");
                    } else if (r < 0.43) {
                        out.set(x, 1, z, "weeds");
                    }
                }
            }
        }
        int cw = Math.min(34, x1 - x0 - 14), cd = Math.min(30, z1 - z0 - 20);
        if (cw >= 16 && cd >= 20) {
            int hx = cx - cw / 2, hz = cz - cd / 2 + 2;
            Placer hp = new Placer(out, hx, 0, hz - 4, 0);
            hp.fill(-1, 1, -1, cw, 8, cd + 4, "air");
            new Civic(hp, rng).courthouse(cw, cd + 4);
            courthouse = hp;
            courtInterior = Courthouse.cellar(cw, cd + 4);
            courtFloor = Courthouse.BASE;
        }
        new Landmarks(world, rng).memorial(cx + 8, z1 - 5);
        for (int[] c : new int[][] {{x0 + 4, z0 + 4}, {x1 - 4, z0 + 4}, {x0 + 4, z1 - 4}, {x1 - 4, z1 - 4}}) {
            kit.tree(chance() < 0.6 ? Trees.Kind.OAK : Trees.Kind.MAPLE, c[0], c[1]);
        }
        for (int x = x0 + 8; x <= x1 - 8; x += 12) {
            lampPost(x, z0 + 1, chance() < 0.6);
            lampPost(x, z1 - 1, chance() < 0.6);
        }
        for (int z = z0 + 8; z <= z1 - 8; z += 12) {
            lampPost(x0 + 1, z, chance() < 0.6);
            lampPost(x1 - 1, z, chance() < 0.6);
        }
        // the tents of the aid station that didn't last, on the lawn behind the courthouse
        Camp camp = new Camp(world, rng, hit);
        for (int i = 0; i < 2; i++) {
            int tx = x0 + 4 + i * 8;
            if (tx + 6 < cx - 2) {
                camp.tent(tx, z1 - 13, 8, i == 0 ? "minecraft:white_wool" : "minecraft:light_gray_wool", true);
            }
        }
    }

    // ------------------------------------------------------------------ the hospital

    /**
     * The general hospital on a whole block (its own frame, len along the street, dep deep):
     * the ambulance drive and the EMERGENCY canopy in front, a two-storey podium (the emergency
     * department, the pharmacy, wards above) with the ward tower rising from its back half to
     * `floors` storeys, a red cross on the tower and the hospital's name along its top, the
     * helipad on the roof. Triage tents and body bags in the side yard, a loading drive on the
     * other side.
     */
    void hospital(Placer p, int len, int dep, int floors) {
        int S = 5;
        int x0 = 9, x1 = len - 10, z0 = 12, z1 = dep - 3;
        if (x1 - x0 < 22) {
            x0 = 3;
            x1 = len - 4;
        }
        String wall = old ? any("brick_buff", "brick_red") : any("concrete_panel", "concrete_panel", "brick_buff");
        Bldg b = bldg(p);
        // the ground: the drive and drop-off, the yards
        for (int x = 0; x < len; x++) {
            for (int z = 0; z < dep; z++) {
                boolean drive = z < z0 || x > x1 && x0 > 3;
                double r = chance();
                p.set(x, 0, z, drive ? (r < 0.6 ? "asphalt" : r < 0.8 ? "asphalt_crack" : r < 0.92 ? "asphalt_patch" : "asphalt_moss")
                        : r < 0.6 ? "floor_concrete" : r < 0.85 ? "floor_concrete_crack" : "minecraft:gravel");
            }
        }
        b.shell(x0, z0, x1, z1, 2, S, wall, "floor_lino", "floor_lino");
        b.flat(x0, z0, x1, z1, 2 * S, wall, 1, "stone_trim");
        int tx0 = x0 + 3, tx1 = x1 - 3, tz0 = z0 + Math.max(4, (z1 - z0) / 3), tz1 = z1 - 2;
        int up = Math.max(1, floors - 2);
        Placer tp = p.shifted(0, 2 * S, 0);
        Bldg t = bldg(tp);
        t.shell(tx0, tz0, tx1, tz1, up, S, wall, "floor_lino", "floor_lino");
        int top = (2 + up) * S;
        t.flat(tx0, tz0, tx1, tz1, up * S, wall, 1, "stone_trim");
        int ex = (x0 + x1) / 2;
        // windows: rows on the podium, ribbons round the tower
        for (int s = 0; s < 2; s++) {
            int y = s * S + 2;
            for (int x = x0 + 2; x <= x1 - 2; x += 2) {
                if (s > 0 || Math.abs(x - ex) > 4) {
                    b.window(x, y, z0, "north", 2, null);
                }
                b.window(x, y, z1, "south", 2, null);
            }
            for (int z = z0 + 2; z <= z1 - 2; z += 2) {
                b.window(x0, y, z, "west", 2, null);
                b.window(x1, y, z, "east", 2, null);
            }
        }
        for (int s = 0; s < up; s++) {
            int y = s * S + 2;
            t.windowRow(tx0 + 1, tx1 - 1, 1, tz0, y, "north", 2, null);
            t.windowRow(tx0 + 1, tx1 - 1, 1, tz1, y, "south", 2, null);
            t.windowRow(tz0 + 1, tz1 - 1, 1, tx0, y, "west", 2, null);
            t.windowRow(tz0 + 1, tz1 - 1, 1, tx1, y, "east", 2, null);
        }
        // inside: the emergency department behind the doors, wards everywhere else
        int sx = tx1 - 3, sa = tz0 + S + 1;
        emergency(b, p, x0, z0, x1, z1, ex, S);
        wards(p, x0, z0 + 9, x1, z1, 0, S, true);
        wards(p, x0, z0, x1, z1, S, S, false);
        for (int s = 0; s < up; s++) {
            wards(tp, tx0, tz0, tx1, tz1, s * S, S, false);
        }
        for (int k = 0; k < 2 + up; k++) {
            flight(b, p, sx, sa, k * S, S, k);
        }
        b.doors(ex, 1, z0, "shop_door", "north");
        b.door(x0, 1, (z0 + z1) / 2, "fire_door", "west");
        b.door(x1, 1, (z0 + z1) / 2, "fire_door", "east");
        b.door(ex + 4, 1, z1, "fire_door", "south");
        // the canopy over the ambulance bay, EMERGENCY on its front edge
        int c0 = ex - 6, c1 = ex + 7;
        for (int x = c0; x <= c1; x++) {
            for (int z = 3; z < z0; z++) {
                p.set(x, 5, z, chance() < 0.9 ? "concrete_dark" : "concrete_dark_crack");
                if ((x - c0) % 4 == 2 && (z - 3) % 4 == 1) {
                    p.set(x, 4, z, chance() < 0.5 ? "ceiling_light" : "ceiling_light_off");
                }
            }
        }
        for (int y = 1; y <= 4; y++) {
            p.set(c0, y, 3, wall);
            p.set(c1, y, 3, wall);
        }
        if (!Boards.fascia(p, rng, "emergency", c0, 5, 2, c1 - c0 + 1)) {
            for (int x = c0; x <= c1; x++) {
                p.set(x, 5, 2, "minecraft:red_concrete");
            }
        }
        // the red cross, lit, high on the tower's front, and the name along its top
        int crossY = top - 5;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                boolean cross = dx == 0 || dy == 0;
                if (cross) {
                    p.set(ex + dx, crossY + dy, tz0, "minecraft:glowstone");
                    p.set(ex + dx, crossY + dy, tz0 - 1, "minecraft:red_stained_glass");
                } else {
                    p.set(ex + dx, crossY + dy, tz0, "minecraft:white_concrete");
                }
            }
        }
        Boards.fascia(p, rng, "mercy", tx0, top + 1, tz0 - 1, tx1 - tx0 + 1);
        Boards.fascia(p, rng, "mercy", x0, 2 * S - 1, z0 - 1, x1 - x0 + 1);
        // the helipad on the tower roof, and maybe what came down on it
        int hx = (tx0 + tx1) / 2 - 7, hz = (tz0 + tz1) / 2 - 7;
        if (tx1 - tx0 >= 17 && tz1 - tz0 >= 17) {
            Placer roof = p.shifted(0, top, 0);
            Camp camp = new Camp(roof, rng, hit);
            camp.helipad(hx, hz);
            if (chance() < 0.4) {
                camp.helicopter(hx + 7, hz + 3, chance() < 0.6);
            }
        } else {
            for (int i = 0; i < 3; i++) {
                p.set(tx0 + 3 + i * 4, top + 1, tz1 - 3, "hvac");
                p.set(tx0 + 3 + i * 4, top + 2, tz1 - 3, "hvac");
            }
        }
        for (int i = 0; i < (x1 - x0) / 8; i++) {
            p.set(x0 + 2 + i * 8, 2 * S + 1, z0 + 2, "hvac");
        }
        // ambulances in the bay and on the drive, one slewed across it
        vehicle(p, ex - 2.5, 1, 7.5, 90 + (float) (chance() * 10 - 5), "ambulance,ambulance_van");
        if (chance() < 0.7) {
            vehicle(p, ex + 4.5, 1, 6.5, 270 + (float) (chance() * 50 - 25), "ambulance,ambulance_van,police_sedan");
        }
        // sandbags and barriers across the front where they tried to hold the doors
        for (int x = 2; x < len - 2; x++) {
            if (Math.abs(x - ex) > 3 && chance() < 0.55) {
                p.set(x, 1, 1, chance() < 0.6 ? "jersey_barrier[facing=north]" : "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 6; i++) {
            p.setIfAir(ex - 3 + rng.nextInt(7), 1, z0 - 1 - rng.nextInt(4), any("blood_splat", "glass_shards", "paper", "trash_bag[facing=east]"));
        }
        if (x0 > 3) {
            // triage tents and the body bags in the side yard; dumpsters and gas on the other side
            Camp side = new Camp(p, rng, hit);
            for (int z = z0 + 2; z + 10 <= z1 - 8; z += 12) {
                side.tent(1, z, 10, "minecraft:white_wool", true);
            }
            for (int z = z1 - 6; z < z1; z++) {
                for (int x = 1; x < x0 - 1; x++) {
                    if (chance() < 0.6) {
                        p.set(x, 1, z, "trash_bag[facing=" + (chance() < 0.5 ? "east" : "west") + "]");
                    }
                }
            }
            for (int z = z0 + 4; z < z1 - 4; z += 7) {
                p.set(x1 + 2, 1, z, chance() < 0.6 ? "dumpster[facing=west]" : "propane_tank");
            }
        }
        lampPost(p, 2, 4, chance() < 0.7);
        lampPost(p, len - 3, 4, chance() < 0.7);
    }

    /** The emergency department's front: waiting room chairs, the reception desk behind its glass. */
    private void emergency(Bldg b, Placer p, int x0, int z0, int x1, int z1, int ex, int S) {
        int dz = z0 + 7;
        for (int x = ex - 5; x <= ex + 5; x++) {
            p.set(x, 1, dz, x == ex - 5 || x == ex + 5 ? "reception_desk" : "store_counter[facing=north]");
            if (x > ex - 5 && x < ex + 5 && chance() < 0.7) {
                p.set(x, 2, dz, "store_teller_glass[axis=x]");
            }
        }
        for (int x = x0 + 1; x < x1; x++) {
            if (Math.abs(x - ex) > 5) {
                for (int y = 1; y < S; y++) {
                    p.set(x, y, dz + 1, dry());
                }
            }
        }
        // rows of chairs facing the desk
        for (int z = z0 + 3; z <= z0 + 5; z += 2) {
            for (int x = x0 + 3; x <= x1 - 3; x++) {
                if (Math.abs(x - ex) > 1 && chance() < 0.75) {
                    p.set(x, 1, z, chance() < 0.12 ? "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]" : "minecraft:blue_terracotta");
                    if (chance() < 0.85) {
                        p.set(x, 1, z, "minecraft:dark_oak_stairs[facing=north]");
                    }
                }
            }
        }
        p.set(x0 + 1, 1, z0 + 1, "store_vending[facing=east]");
        p.set(x0 + 1, 2, z0 + 1, "store_vending_top[facing=east]");
        b.lights(x0, z0, x1, dz, S - 1, 0.1);
        b.mess(x0 + 1, z0 + 1, x1 - 1, dz - 1, 1, 0.05);
    }

    /**
     * A floor of rooms off a corridor down the middle, from y: two beds to a room behind their
     * curtain, a cabinet, a drip stand. The pharmacy takes the first room on the ground floor.
     */
    private void wards(Placer p, int x0, int z0, int x1, int z1, int y, int S, boolean pharmacy) {
        Bldg b = bldg(p);
        int cz = (z0 + z1) / 2;
        boolean north = cz - 2 - z0 >= 4, south = z1 - cz - 2 >= 4;
        for (int x = x0 + 1; x < x1; x++) {
            for (int yy = y + 1; yy < y + S; yy++) {
                if (north) {
                    p.set(x, yy, cz - 2, dry());
                }
                if (south) {
                    p.set(x, yy, cz + 2, dry());
                }
            }
        }
        boolean first = true;
        for (int rx = x0; rx + 4 < x1; rx += 6) {
            int rx1 = Math.min(rx + 6, x1);
            if (rx1 < x1) {
                for (int yy = y + 1; yy < y + S; yy++) {
                    if (north) {
                        for (int z = z0 + 1; z < cz - 2; z++) {
                            p.set(rx1, yy, z, dry());
                        }
                    }
                    if (south) {
                        for (int z = cz + 3; z < z1; z++) {
                            p.set(rx1, yy, z, dry());
                        }
                    }
                }
            }
            int dx = rx + 3;
            if (north) {
                b.door(dx, y + 1, cz - 2, "fire_door", "south");
                room(p, rx + 1, rx1 - 1, z0 + 1, cz - 3, y, true, false);
            }
            if (south) {
                b.door(dx, y + 1, cz + 2, "fire_door", "north");
                room(p, rx + 1, rx1 - 1, cz + 3, z1 - 1, y, false, pharmacy && first);
                first = false;
            }
        }
        b.lights(x0, z0, x1, z1, y + S - 1, 0.06);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1, 0.025);
    }

    /** One room from a0 to a1 (x) and r0 to r1 (z); its outer wall is north (r0) or south (r1). */
    private void room(Placer p, int a0, int a1, int r0, int r1, int y, boolean northSide, boolean pharmacy) {
        int wallZ = northSide ? r0 : r1, in = northSide ? 1 : -1;
        String face = northSide ? "south" : "north", head = northSide ? "north" : "south";
        if (pharmacy) {
            for (int x = a0; x <= a1; x++) {
                p.set(x, y + 1, wallZ, "store_shelf_pharmacy[facing=" + face + "]");
                p.set(x, y + 2, wallZ, "store_shelf_pharmacy_top[facing=" + face + "]");
            }
            for (int z = Math.min(r0, r1) + 1; z < Math.max(r0, r1); z++) {
                p.set(a0, y + 1, z, "store_shelf_pharmacy[facing=east]");
                p.set(a1, y + 1, z, "store_shelf_pharmacy[facing=west]");
            }
            p.set(a0 + 2, y + 1, wallZ + in * 2, "store_counter[facing=" + face + "]");
            return;
        }
        if (a1 - a0 < 3 || Math.abs(r1 - r0) < 3) {
            return;
        }
        for (int bx : new int[] {a0, a0 + 3}) {
            if (bx > a1 || chance() < 0.12) {
                continue;
            }
            p.set(bx, y + 1, wallZ, "minecraft:white_bed[facing=" + head + ",part=head]");
            p.set(bx, y + 1, wallZ + in, "minecraft:white_bed[facing=" + head + ",part=foot]");
            if (chance() < 0.35 * hit + 0.1) {
                p.set(bx, y + 1, wallZ + in * 2, "blood_splat");
            }
        }
        if (chance() < 0.7) {
            p.set(a0 + 1, y + 1, wallZ, "minecraft:white_stained_glass_pane");
            p.set(a0 + 1, y + 2, wallZ, "minecraft:white_stained_glass_pane");
        }
        if (chance() < 0.3) {
            p.set(a1, y + 1, wallZ, "store_shelf_pharmacy[facing=" + face + "]");
        }
        if (chance() < 0.5) {
            p.set(a0 + 2, y + 1, wallZ + in, "minecraft:lightning_rod");
        }
    }

    /**
     * A switchback stair, two flights wide: flight k climbs one floor from y, north up the
     * column sx for even k from z = a, south up the column sx + 1 for odd k. The stair hall is
     * cleared of any partition first.
     */
    private void flight(Bldg b, Placer p, int sx, int a, int y, int S, int k) {
        for (int yy = y + 1; yy < y + S; yy++) {
            for (int x = sx - 1; x <= sx + 2; x++) {
                for (int z = a - S; z <= a + 1; z++) {
                    p.set(x, yy, z, "air");
                }
            }
        }
        if ((k & 1) == 0) {
            b.stair(sx, a, y + 1, S, "north", "minecraft:smooth_stone");
        } else {
            b.stair(sx + 1, a - S + 1, y + 1, S, "south", "minecraft:smooth_stone");
        }
        for (int z = a - S + 1; z <= a; z++) {
            if (chance() < 0.85) {
                p.set(sx - 1, y + 1, z, "railing[facing=west]");
            }
        }
    }

    // ------------------------------------------------------------------ police headquarters

    /**
     * Police headquarters on pw x pd (its own frame): an apron with cruisers nosed in and the
     * sandbags of a last stand; three or four storeys with a blue band, the panel with its gold
     * star over the doors and POLICE under it, soul lamps either side, flag poles. Inside the
     * front desk behind glass, the armoury, the cells along the back, offices upstairs, a radio
     * mast on the roof.
     */
    void police(Placer p, int pw, int pd) {
        int S = 5;
        int floors = old ? 3 : 3 + rng.nextInt(2);
        int x0 = 1, x1 = pw - 2, z0 = 7, z1 = pd - 1;
        String wall = old ? any("brick_red", "brick_dark") : any("concrete_panel", "brick_dark", "concrete_panel");
        Bldg b = bldg(p);
        b.boarded = 0.3;
        for (int x = 0; x < pw; x++) {
            for (int z = 0; z < z0; z++) {
                p.set(x, 0, z, chance() < 0.75 ? "floor_concrete" : "floor_concrete_crack");
            }
        }
        b.shell(x0, z0, x1, z1, floors, S, wall, "floor_lino", "office_floor");
        int top = floors * S;
        b.flat(x0, z0, x1, z1, top, wall, 1, "stone_trim");
        int ex = (x0 + x1) / 2;
        // the blue band round it above the ground floor
        for (int x = x0; x <= x1; x++) {
            p.set(x, S - 1, z0, "minecraft:blue_terracotta");
            p.set(x, S - 1, z1, "minecraft:blue_terracotta");
        }
        for (int z = z0; z <= z1; z++) {
            p.set(x0, S - 1, z, "minecraft:blue_terracotta");
            p.set(x1, S - 1, z, "minecraft:blue_terracotta");
        }
        for (int s = 0; s < floors; s++) {
            int y = s * S + 2;
            String sill = old ? "sill" : null;
            for (int x = x0 + 2; x <= x1 - 2; x += 2) {
                if (s > 0 || Math.abs(x - ex) > 3) {
                    b.window(x, y, z0, "north", 2, sill);
                }
                if (s > 0) {
                    b.window(x, y, z1, "south", 2, sill);
                }
            }
            for (int z = z0 + 2; z <= z1 - 2; z += 3) {
                b.window(x0, y, z, "west", 2, sill);
                b.window(x1, y, z, "east", 2, sill);
            }
        }
        b.doors(ex, 1, z0, "shop_door", "north");
        b.door(x1 - 3, 1, z1, "fire_door", "south");
        // the panel with the star over the doors, the sign under it
        String[] star = {"..G..", "GGGGG", ".GGG.", ".G.G.", "G...G"};
        for (int row = 0; row < 5; row++) {
            for (int i = 0; i < 5; i++) {
                p.set(ex - 2 + i, S + 5 - row, z0 - 1, star[row].charAt(i) == 'G' ? "minecraft:gold_block" : "minecraft:blue_concrete");
            }
        }
        if (!Boards.fascia(p, rng, "police", ex - 3, 3, z0 - 1, 8)) {
            p.fill(ex - 2, 3, z0 - 1, ex + 2, 3, z0 - 1, "minecraft:blue_concrete");
        }
        Boards.fascia(p, rng, "precinct", x0, top - 1, z0 - 1, x1 - x0 + 1);
        for (int lx : new int[] {ex - 3, ex + 4}) {
            p.set(lx, 1, z0 - 1, "minecraft:polished_blackstone_wall");
            p.set(lx, 2, z0 - 1, "minecraft:polished_blackstone_wall");
            p.set(lx, 3, z0 - 1, chance() < 0.8 ? "minecraft:soul_lantern" : "minecraft:lantern");
        }
        Civic civic = new Civic(p, rng);
        civic.flagpole(2, 1, 10);
        civic.flagpole(pw - 3, 1, 10);
        // cruisers nosed in, the sandbags across the doors
        for (int i = 0; i < 2; i++) {
            if (chance() < 0.75) {
                vehicle(p, (i == 0 ? ex - 8 : ex + 9) + 0.5, 1, 3.5, (float) (chance() * 30 - 15), "police_sedan,police_suv,police_sedan,swat_van");
            }
        }
        for (int x = ex - 3; x <= ex + 4; x++) {
            if (chance() < 0.8) {
                p.set(x, 1, z0 - 3, "sandbags[facing=north]");
            }
        }
        for (int i = 0; i < 8; i++) {
            p.setIfAir(ex - 6 + rng.nextInt(13), 1, z0 - 4 - rng.nextInt(3), any("blood_splat", "blood_splat", "glass_shards", "paper", "debris_chunk"));
        }
        // inside: the front desk, the corridor, the cells and the armoury behind
        int dz = z0 + 6;
        for (int x = ex - 5; x <= ex + 5; x++) {
            p.set(x, 1, dz, "store_counter[facing=north]");
            p.set(x, 2, dz, chance() < 0.7 ? "store_teller_glass[axis=x]" : "glass_shards");
        }
        for (int x = x0 + 1; x < x1; x++) {
            if (Math.abs(x - ex) > 5) {
                for (int y = 1; y < S; y++) {
                    p.set(x, y, dz, dry());
                }
            }
        }
        int cz = dz + 4;
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = 1; y < S; y++) {
                p.set(x, y, cz, dry());
            }
        }
        for (int x = x0 + 4; x < x1; x += 8) {
            b.door(x, 1, cz, "fire_door", "north");
        }
        int armoury = x1 - 9;
        // the cells: bars on the corridor side, a bunk and a toilet in each
        for (int x = x0 + 5; x + 3 < armoury; x += 4) {
            for (int z = cz + 1; z < z1; z++) {
                for (int y = 1; y < S; y++) {
                    p.set(x, y, z, "concrete_panel");
                }
            }
            for (int i = 1; i <= 3; i++) {
                for (int y = 1; y < S; y++) {
                    p.set(x + i, y, cz + 4, y <= 2 && i == 2 ? "air" : "minecraft:iron_bars");
                }
            }
            p.set(x + 2, 1, cz + 4, "minecraft:iron_door[facing=north,half=lower,hinge=left,open=" + (chance() < 0.4 + hit * 0.3) + "]");
            p.set(x + 2, 2, cz + 4, "minecraft:iron_door[facing=north,half=upper,hinge=left,open=false]");
            if (z1 - cz > 7) {
                p.set(x + 1, 1, z1 - 1, "minecraft:gray_bed[facing=south,part=head]");
                p.set(x + 1, 1, z1 - 2, "minecraft:gray_bed[facing=south,part=foot]");
                p.set(x + 3, 1, z1 - 1, "minecraft:cauldron");
            }
            if (chance() < 0.3 + hit * 0.3) {
                p.setIfAir(x + 2, 1, z1 - 3, chance() < 0.5 ? "blood_splat" : "minecraft:skeleton_skull[rotation=" + rng.nextInt(16) + "]");
            }
        }
        for (int z = cz + 1; z < z1; z++) {
            for (int y = 1; y < S; y++) {
                p.set(armoury, y, z, "concrete_panel");
            }
            p.set(x1 - 1, 1, z, "store_gun_rack[facing=west]");
            p.set(x1 - 1, 2, z, "store_gun_rack[facing=west]");
        }
        p.set(armoury, 1, cz + 2, "minecraft:iron_door[facing=west,half=lower,hinge=right,open=" + (chance() < 0.5) + "]");
        p.set(armoury, 2, cz + 2, "minecraft:iron_door[facing=west,half=upper,hinge=right,open=false]");
        for (int x = armoury + 2; x < x1 - 2; x++) {
            p.set(x, 1, z1 - 1, "store_shelf_ammo[facing=north]");
        }
        b.lights(x0, z0, x1, z1, S - 1, 0.05);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, 1, 0.03);
        // offices upstairs
        for (int s = 1; s < floors; s++) {
            offices(p, x0, z0, x1, z1, s * S, S);
        }
        for (int k = 0; k < floors; k++) {
            flight(b, p, x0 + 2, z0 + 1 + S + 1, k * S, S, k);
        }
        new Camp(p.shifted(0, top, 0), rng, hit).mast(x1 - 4, z1 - 4, 12);
        if (chance() < 0.5) {
            new Camp(p.shifted(0, top, 0), rng, hit).nest(ex, z0 + 3);
        }
    }

    /** An office floor from y: desks in pairs with their chairs, filing cabinets along the walls. */
    private void offices(Placer p, int x0, int z0, int x1, int z1, int y, int S) {
        Bldg b = bldg(p);
        for (int x = x0 + 4; x < x1 - 3; x += 5) {
            for (int z = z0 + 3; z < z1 - 3; z += 5) {
                if (chance() < 0.2) {
                    continue;
                }
                p.set(x, y + 1, z, "desk[facing=north]");
                p.set(x + 1, y + 1, z, "desk[facing=north]");
                p.set(x, y + 1, z + 1, chance() < 0.25 ? "office_chair_fallen[facing=" + DIRS[rng.nextInt(4)] + "]" : "office_chair[facing=north]");
                if (chance() < 0.5) {
                    p.set(x + 2, y + 1, z, "cubicle_wall[facing=east]");
                }
            }
        }
        for (int x = x0 + 2; x < x1 - 1; x += 3) {
            if (chance() < 0.5) {
                p.set(x, y + 1, z1 - 1, "filing_cabinet[facing=north]");
            }
        }
        b.lights(x0, z0, x1, z1, y + S - 1, 0.06);
        b.mess(x0 + 1, z0 + 1, x1 - 1, z1 - 1, y + 1, 0.04);
    }

    // ------------------------------------------------------------------ parking

    /**
     * A parking garage gw x gd (its own frame): open decks four blocks apart on a grid of
     * columns, a low wall round each, ramps up one side in two lanes, a stair in the corner,
     * a few cars still in their bays, purple lamps on the roof deck.
     */
    void garage(Placer p, int gw, int gd, int levels) {
        int H = 4;
        int laneA = gw - 8, laneB = gw - 4, za = gd / 2 - H;
        for (int k = 0; k <= levels; k++) {
            int y = k * H;
            for (int x = 0; x < gw; x++) {
                for (int z = 0; z < gd; z++) {
                    double r = chance();
                    p.set(x, y, z, k == 0 ? (r < 0.7 ? "asphalt" : r < 0.9 ? "asphalt_crack" : "asphalt_patch")
                            : r < 0.75 ? "floor_concrete" : r < 0.93 ? "floor_concrete_crack" : "floor_concrete_moss");
                    if (k < levels) {
                        for (int yy = y + 1; yy < y + H; yy++) {
                            p.set(x, yy, z, "air");
                        }
                    }
                    boolean edge = x == 0 || z == 0 || x == gw - 1 || z == gd - 1;
                    boolean column = (x % 8 == 0 || x == gw - 1) && (z % 8 == 0 || z == gd - 1);
                    if (column && k < levels) {
                        for (int yy = y + 1; yy < y + H; yy++) {
                            p.set(x, yy, z, chance() < 0.85 ? "concrete_panel" : "concrete_panel_stain");
                        }
                    } else if (edge && k > 0) {
                        p.set(x, y + 1, z, chance() < 0.08 * hit ? "air" : chance() < 0.85 ? "concrete_panel" : "concrete_panel_crack");
                    }
                }
            }
            // bays painted along the front and back rows
            for (int x = 1; x < laneA - 1; x += 3) {
                for (int z = 1; z < gd - 1; z++) {
                    boolean row = z <= 5 || z >= gd - 6 || Math.abs(z - gd / 2) <= 2;
                    if (row && chance() < 0.7) {
                        p.set(x, y, z, k == 0 ? "road_line_white[axis=z]" : "minecraft:white_concrete");
                    }
                }
            }
            // cars left in the bays
            int n = rng.nextInt(3);
            for (int i = 0; i < n; i++) {
                int bx = 2 + 3 * rng.nextInt(Math.max(1, (laneA - 4) / 3));
                boolean front = chance() < 0.5;
                car(p, bx + 1, y + 1, front ? 3 : gd - 4, front ? 0 : 180);
            }
            if (k < levels) {
                for (int x = 4; x < gw - 1; x += 8) {
                    for (int z = 4; z < gd - 1; z += 8) {
                        p.set(x, y + H - 1, z, chance() < 0.15 ? "ceiling_light" : chance() < 0.2 ? cityLamp(0) : "ceiling_light_off");
                    }
                }
            }
        }
        // ramps: lane A for the even levels, lane B for the odd, each rising a level over 2H
        for (int k = 0; k < levels; k++) {
            int lx = (k & 1) == 0 ? laneA : laneB;
            for (int t = 0; t < 2 * H; t++) {
                int z = (k & 1) == 0 ? za + t : za + 2 * H - 1 - t;
                int full = (t + 1) / 2, half = (t + 1) % 2;
                for (int x = lx; x < lx + 3; x++) {
                    for (int yy = k * H + 1; yy <= k * H + full; yy++) {
                        p.set(x, yy, z, "floor_concrete");
                    }
                    int surf = k * H + full + 1;
                    if (half == 1) {
                        p.set(x, surf, z, "minecraft:smooth_stone_slab[type=bottom]");
                        surf++;
                    }
                    for (int yy = surf; yy < surf + 3 && yy <= (k + 1) * H + (t == 2 * H - 1 ? 0 : 3); yy++) {
                        if (yy != (k + 1) * H || t < 2 * H - 1) {
                            p.set(x, yy, z, "air");
                        }
                    }
                }
            }
        }
        Bldg b = bldg(p);
        for (int k = 0; k < levels; k++) {
            flight(b, p, 2, gd - 3, k * H, H, k);
        }
        // the roof: lamps at the corners, the last stand someone made up here
        int roof = levels * H;
        for (int[] c : new int[][] {{3, 3}, {gw - 4, 3}, {3, gd - 4}, {gw - 4, gd - 4}}) {
            lampPost(p.shifted(0, roof, 0), c[0], c[1], chance() < 0.7);
        }
        if (chance() < 0.4) {
            new Camp(p.shifted(0, roof, 0), rng, hit).nest(gw / 2, gd / 2);
        }
        // the entrance: barrier arm, the sign over it
        new Camp(p, rng, hit).boom(laneA - 4, 2, 5, chance() < 0.5);
        if (!Boards.fascia(p, rng, "parking", laneA - 6, H - 1, -1, 12)) {
            p.set(laneA - 2, H - 1, -1, "minecraft:blue_concrete");
        }
    }

    /** A surface lot len x dep: cracked asphalt and faded bays, a booth, chain-link with gaps, a few cars. */
    void carLot(Placer p, int len, int dep) {
        for (int x = 0; x < len; x++) {
            for (int z = 0; z < dep; z++) {
                double r = chance();
                int row = Math.floorMod(z - 2, 16);
                boolean line = x % 3 == 0 && (row < 5 || row >= 11) && x > 1 && x < len - 2;
                p.set(x, 0, z, line && chance() < 0.7 ? "road_line_white[axis=z]"
                        : r < 0.6 ? "asphalt" : r < 0.8 ? "asphalt_crack" : r < 0.9 ? "asphalt_patch" : r < 0.96 ? "asphalt_moss"
                        : "minecraft:grass_block");
                if (chance() < 0.02) {
                    p.set(x, 1, z, any("weeds", "weeds_tall", "paper", "trash_bag[facing=north]", "minecraft:short_grass"));
                }
            }
        }
        for (int x = 0; x < len; x++) {
            if (x < 3 || x > 9) {
                for (int h = 1; h <= 2; h++) {
                    if (chance() > 0.08) {
                        p.set(x, h, 0, x % 4 == 0 ? "fence_post" : "chainlink[axis=x]");
                    }
                }
            }
        }
        p.set(4, 1, 2, "store_booth[facing=north]");
        p.fill(10, 1, 1, 11, 2, 2, "concrete_panel");
        p.set(10, 2, 1, "window_broken[axis=x]");
        Boards.board(p, rng, "parking", 12, 3, 1);
        int n = 2 + rng.nextInt(4);
        for (int i = 0; i < n; i++) {
            int bx = 3 + 3 * rng.nextInt(Math.max(1, (len - 6) / 3));
            int row = rng.nextInt(Math.max(1, (dep - 4) / 16 + 1));
            car(p, bx + 1, 1, 2 + row * 16 + (chance() < 0.5 ? 2 : 13), chance() < 0.5 ? 0 : 180);
        }
        for (int x = 6; x < len - 3; x += 16) {
            for (int z = 8; z < dep - 3; z += 16) {
                lampPost(p, x, z, chance() < 0.6);
            }
        }
    }

    // ------------------------------------------------------------------ the construction site

    /**
     * The tower that was going up when it all stopped, on a whole block (its own frame): a
     * concrete core ahead of the floors, floors poured part way up and bare steel above them,
     * glass on the lowest floors, scaffolding up the front, plywood hoarding round the site,
     * the tower crane with its jib over the frame and its red lights, cabins, stacked steel and
     * what was left on the ground.
     */
    void construction(Placer p, int len, int dep, int floors) {
        int S = 4;
        int x0 = 6, x1 = len - 8, z0 = 8, z1 = dep - 7;
        int done = Math.max(2, (int) (floors * (0.35 + chance() * 0.3)));
        for (int x = 0; x < len; x++) {
            for (int z = 0; z < dep; z++) {
                double r = chance();
                p.set(x, 0, z, r < 0.35 ? "minecraft:coarse_dirt" : r < 0.6 ? "minecraft:gravel" : r < 0.75 ? "minecraft:mud"
                        : r < 0.88 ? "minecraft:dirt" : "floor_concrete_crack");
            }
        }
        // the hoarding along the street, chain-link round the rest
        int gate = len / 2;
        for (int x = 0; x < len; x++) {
            if (Math.abs(x - gate) <= 3) {
                continue;
            }
            for (int h = 1; h <= 3; h++) {
                p.set(x, h, 0, x % 4 == 0 ? "fence_post" : chance() < 0.05 * hit ? "air" : "fence_boards[axis=x]");
            }
        }
        Boards.fascia(p, rng, "hardhat", 2, 2, -1, gate - 6);
        Kit.chainlink(p, rng, 0, dep - 1, len - 1, dep - 1, 3);
        Kit.chainlink(p, rng, 0, 1, 0, dep - 2, 3);
        Kit.chainlink(p, rng, len - 1, 1, len - 1, dep - 2, 3);
        // the core, a few floors ahead
        int kx = (x0 + x1) / 2 - 4, kz = (z0 + z1) / 2 - 4;
        int coreTop = Math.min(floors, done + 3) * S;
        for (int y = 1; y <= coreTop; y++) {
            for (int i = 0; i < 9; i++) {
                for (int j = 0; j < 9; j++) {
                    boolean wall = i == 0 || j == 0 || i == 8 || j == 8;
                    boolean door = j == 0 && i >= 3 && i <= 5 && (y - 1) % S < 2 && (y - 1) / S <= done;
                    p.set(kx + i, y, kz + j, wall && !door ? (chance() < 0.9 ? "concrete_dark" : "concrete_dark_stain") : "air");
                }
            }
        }
        // columns, beams and slabs
        int frameTop = floors * S;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean colX = (x - x0) % 6 == 0 || x == x1, colZ = (z - z0) % 6 == 0 || z == z1;
                boolean inCore = x >= kx && x <= kx + 8 && z >= kz && z <= kz + 8;
                if (inCore) {
                    continue;
                }
                if (colX && colZ) {
                    int h = frameTop - (chance() < 0.3 ? S * (1 + rng.nextInt(2)) : 0);
                    for (int y = 1; y <= h; y++) {
                        p.set(x, y, z, "steel_girder");
                    }
                }
                for (int k = 1; k <= floors; k++) {
                    int y = k * S;
                    boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                    if (k <= done) {
                        p.set(x, y, z, chance() < 0.85 ? "floor_concrete" : "floor_concrete_crack");
                        if (k == done && chance() < 0.04) {
                            p.set(x, y + 1, z, "rebar");
                        }
                    } else if (colX || colZ) {
                        if (edge || chance() < 0.9) {
                            p.set(x, y, z, "steel_girder");
                        }
                    }
                }
            }
        }
        // glass on the lowest floors, safety chain-link along the edges of the ones above
        int glassed = Math.min(done, 1 + rng.nextInt(3));
        for (int k = 0; k < done; k++) {
            for (int y = k * S + 1; y < k * S + S; y++) {
                for (int x = x0 + 1; x < x1; x++) {
                    if (k < glassed) {
                        p.set(x, y, z0, (x - x0) % 6 == 0 ? "steel_mullion" : chance() < 0.15 * hit + 0.05 ? "window_broken[axis=x]" : "window[axis=x]");
                    } else if (y == k * S + 1 && chance() < 0.7) {
                        p.set(x, y, z0, "chainlink[axis=x]");
                    }
                }
            }
        }
        // scaffolding up the front face, walkways every floor
        for (int x = x0; x <= x1; x++) {
            boolean pole = (x - x0) % 3 == 0;
            for (int y = 1; y <= done * S; y++) {
                if (pole) {
                    p.set(x, y, z0 - 2, "minecraft:scaffolding[bottom=false,distance=0]");
                } else if (y % S == 0) {
                    p.set(x, y, z0 - 2, chance() < 0.9 ? "minecraft:spruce_slab[type=top]" : "air");
                }
            }
        }
        crane(p, x1 + 3, (z0 + z1) / 2, frameTop + 14, x1 - x0 + 4);
        // the site: cabins stacked by the gate, steel and rebar, the toilets, a truck
        Harbour h = new Harbour(p, rng, hit);
        h.box(1, 1, 2, "container_white", true);
        h.box(1, 4, 2, chance() < 0.5 ? "container_blue" : "container_white", true);
        new Camp(p, rng, hit).toilets(len - 8, 2, 3);
        for (int i = 0; i < 3; i++) {
            int sx = x0 + 2 + rng.nextInt(Math.max(1, x1 - x0 - 10)), sz = z1 + 2 + rng.nextInt(2);
            for (int k = 0; k < 6; k++) {
                for (int y = 1; y <= 1 + rng.nextInt(2); y++) {
                    p.setIfAir(sx + k, y, sz, chance() < 0.7 ? "steel_girder" : "store_lumber[facing=north]");
                }
            }
        }
        for (int i = 0; i < 10; i++) {
            p.setIfAir(1 + rng.nextInt(len - 2), 1, 3 + rng.nextInt(dep - 5), any("rebar", "crate", "cardboard_box", "tire[axis=x]",
                    "minecraft:light_gray_concrete_powder", "rubble_pile", "debris_chunk"));
        }
        if (chance() < 0.7) {
            vehicle(p, gate + 0.5, 1, 4.5, (float) (chance() * 40 - 20), "box_truck,pickup,tow_truck,step_van");
        }
        for (int[] c : new int[][] {{3, dep - 4}, {len - 4, dep - 4}}) {
            for (int y = 1; y <= 6; y++) {
                p.set(c[0], y, c[1], "steel_girder");
            }
            p.set(c[0], 7, c[1], chance() < 0.6 ? "minecraft:ochre_froglight" : Camp.flood(rng, 0.4));
        }
    }

    /**
     * A tower crane: a 2 x 2 yellow lattice mast to h, the slewing ring and cab, the jib reaching
     * jib blocks out over -x with its trolley and hook hanging a load, the counter-jib and its
     * weights, the A-frame on top with the ties, red lights at the top and the tip.
     */
    private void crane(Placer p, int mx, int mz, int h, int jib) {
        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 2; j++) {
                p.set(mx + i, 0, mz + j, "concrete_dark");
            }
        }
        for (int y = 1; y <= h; y++) {
            for (int i = 0; i <= 1; i++) {
                for (int j = 0; j <= 1; j++) {
                    p.set(mx + i, y, mz + j, "truss_yellow");
                }
            }
        }
        p.fill(mx - 1, h + 1, mz - 1, mx + 2, h + 1, mz + 2, "steel_yellow");
        p.fill(mx - 1, h + 2, mz + 2, mx, h + 3, mz + 2, "steel_yellow");
        p.set(mx - 1, h + 3, mz + 2, chance() < 0.5 ? "window_broken[axis=x]" : "window[axis=x]");
        int jy = h + 2;
        for (int x = mx - jib; x <= mx + 1; x++) {
            p.set(x, jy, mz, "truss_yellow");
            p.set(x, jy, mz + 1, "truss_yellow");
        }
        int counter = Math.max(8, jib / 3);
        for (int x = mx + 2; x <= mx + counter; x++) {
            p.set(x, jy, mz, "truss_yellow");
            p.set(x, jy, mz + 1, "truss_yellow");
        }
        p.fill(mx + counter - 3, jy - 2, mz, mx + counter - 1, jy - 1, mz + 1, "concrete_dark");
        for (int y = jy + 1; y <= jy + 5; y++) {
            p.set(mx, y, mz, "truss_yellow");
        }
        p.set(mx, jy + 6, mz, "aviation_light");
        p.set(mx - jib, jy + 1, mz, "aviation_light");
        line(p, mx, jy + 5, mz, mx - jib * 2 / 3, jy + 1, mz, "minecraft:iron_chain[axis=x]");
        line(p, mx, jy + 5, mz, mx + counter - 1, jy + 1, mz, "minecraft:iron_chain[axis=x]");
        // the trolley and hook with its load hanging
        int tx = mx - jib / 2 - rng.nextInt(Math.max(1, jib / 3));
        int hookY = Math.max(6, jy - 8 - rng.nextInt(Math.max(1, jy / 2)));
        p.set(tx, jy - 1, mz, "steel_yellow");
        for (int y = hookY + 1; y < jy - 1; y++) {
            p.set(tx, y, mz, "minecraft:iron_chain[axis=y]");
        }
        if (chance() < 0.5) {
            p.fill(tx - 2, hookY, mz, tx + 2, hookY, mz, "steel_girder");
        } else {
            p.set(tx, hookY, mz, "crate");
        }
    }

    private static void line(Placer p, int x0, int y0, int z0, int x1, int y1, int z1, String block) {
        int n = Math.max(Math.abs(x1 - x0), Math.max(Math.abs(y1 - y0), Math.abs(z1 - z0)));
        for (int i = 0; i <= n; i++) {
            double t = n == 0 ? 0 : i / (double) n;
            p.set((int) Math.round(x0 + (x1 - x0) * t), (int) Math.round(y0 + (y1 - y0) * t), (int) Math.round(z0 + (z1 - z0) * t), block);
        }
    }

    // ------------------------------------------------------------------ the subway

    /**
     * The subway under the avenue whose centre line is z = cz (half its road's half width): the
     * station from x0 to x1 and the tunnels from tx0 to tx1, in the place's own coordinates.
     * Two tracks in the middle with the columns between them, a platform each side with its
     * yellow edge, tiled walls with the station's name, green and purple lamps, a train standing
     * at one platform with its doors open. Stairs come down from both sidewalks under green
     * globes. The tunnels end in cave-ins, and in one place the street has fallen into them.
     */
    void subway(Kit kit, int x0, int x1, int cz, int half, int tx0, int tx1) {
        final int roof = -2, top = -3, plat = -11, bed = -13;
        for (int x = x0; x <= x1; x++) {
            for (int dz = -13; dz <= 13; dz++) {
                int z = cz + dz;
                int a = Math.abs(dz);
                out.set(x, -1, z, "concrete_dark");
                out.set(x, roof, z, "concrete_dark");
                boolean wall = a == 13 || x == x0 || x == x1;
                for (int y = bed - 1; y <= top; y++) {
                    String b;
                    if (wall && !(a <= 5 && (x == x0 || x == x1) && y >= bed + 1 && y <= -7)) {
                        b = y == -8 ? "minecraft:green_terracotta" : y == bed - 1 || y < plat ? "concrete_dark"
                                : chance() < 0.85 ? "minecraft:quartz_bricks" : "minecraft:calcite";
                    } else if (a >= 6) {
                        b = y < plat ? "concrete_dark" : y == plat ? (a == 6 ? "minecraft:yellow_concrete" : chance() < 0.85 ? "floor_concrete"
                                : "floor_concrete_crack") : "air";
                    } else {
                        b = y == bed - 1 ? "concrete_dark" : y == bed ? (chance() < 0.7 ? "minecraft:gravel" : "minecraft:tuff") : "air";
                    }
                    out.set(x, y, z, b);
                }
                if (a == 2 || a == 4) {
                    out.set(x, bed + 1, z, "rail[axis=x]");
                }
            }
            // the columns down the middle, between the tracks
            if ((x - x0) % 6 == 3) {
                for (int y = bed; y <= top; y++) {
                    out.set(x, y, cz, "steel_girder");
                }
            }
            // lamps over the platforms, a strip over each track
            if ((x - x0) % 6 == 0) {
                out.set(x, top, cz - 9, subwayLamp());
                out.set(x, top, cz + 9, subwayLamp());
                out.set(x, top, cz - 3, chance() < 0.2 ? "ceiling_light" : "ceiling_light_off");
                out.set(x, top, cz + 3, chance() < 0.2 ? "ceiling_light" : "ceiling_light_off");
            }
        }
        // the name along both walls, benches and bins along the platforms
        for (int x = x0 + 4; x + 6 <= x1 - 2; x += 14) {
            Boards.fascia(new Placer(out, x + 5, 0, cz - 13, 2), rng, "central", 0, -7, -1, 6);
            Boards.fascia(new Placer(out, x, 0, cz + 13, 0), rng, "central", 0, -7, -1, 6);
        }
        for (int x = x0 + 7; x < x1 - 3; x += 9) {
            for (int s = -1; s <= 1; s += 2) {
                int z = cz + s * 12;
                double r = chance();
                out.set(x, plat + 1, z, r < 0.45 ? "minecraft:dark_oak_stairs[facing=" + (s < 0 ? "north" : "south") + "]"
                        : r < 0.6 ? "bin_ribbed" : r < 0.75 ? "store_vending[facing=" + (s < 0 ? "south" : "north") + "]" : "air");
                if (r >= 0.6 && r < 0.75) {
                    out.set(x, plat + 2, z, "store_vending_top[facing=" + (s < 0 ? "south" : "north") + "]");
                }
            }
        }
        for (int i = 0; i < (x1 - x0) / 2; i++) {
            int x = x0 + 1 + rng.nextInt(x1 - x0 - 1), z = cz + (chance() < 0.5 ? -1 : 1) * (7 + rng.nextInt(5));
            if (out.get(x, plat + 1, z) == null) {
                out.set(x, plat + 1, z, any("paper", "paper", "blood_splat", "trash_bag[facing=east]", "cardboard_box", "glass_shards",
                        "minecraft:skeleton_skull[rotation=" + rng.nextInt(16) + "]"));
            }
        }
        train(x0 + 3, x1 - 3, cz + 1, cz + 5, cz + 5, plat + 1);
        if (chance() < 0.4) {
            train(x0 + 3, x1 - 3, cz - 5, cz - 1, cz - 5, plat + 1);
        }
        // stairs down from each sidewalk, at opposite ends of the station
        int[] walk = walkRows(kit, (x0 + x1) / 2, cz, 1);
        if (walk != null) {
            entrance(kit, x0 + 2, 1, walk, plat);
        }
        walk = walkRows(kit, (x0 + x1) / 2, cz, -1);
        if (walk != null) {
            entrance(kit, x1 - 2, -1, walk, plat);
        }
        tunnel(kit, tx0, x0 - 1, cz, true);
        tunnel(kit, x1 + 1, tx1, cz, false);
    }

    private String subwayLamp() {
        double r = chance();
        return r < 0.3 ? "minecraft:smooth_quartz" : r < 0.65 ? "minecraft:verdant_froglight" : r < 0.92 ? "minecraft:pearlescent_froglight"
                : "minecraft:ochre_froglight";
    }

    /** The sidewalk's rows on one side (s = 1 south, -1 north) of the street at cz, as their z, or null. */
    private int[] walkRows(Kit kit, int x, int cz, int s) {
        List<Integer> rows = new ArrayList<>();
        for (int dz = 1; dz <= 13; dz++) {
            if (kit.use(x, cz + s * dz) == Kit.WALK) {
                rows.add(cz + s * dz);
            }
        }
        if (rows.size() < 4) {
            return null;
        }
        int[] r = new int[rows.size()];
        for (int i = 0; i < r.length; i++) {
            r[i] = rows.get(i);
        }
        return r;
    }

    /**
     * Stairs two wide from the sidewalk down to the platform, descending along x in the
     * direction dir from x = ex, railings either side, the green globes at the top and the
     * SUBWAY sign facing the way in.
     */
    private void entrance(Kit kit, int ex, int dir, int[] walk, int plat) {
        int inner = walk[0], outer = walk[walk.length - 1];
        int s = outer > inner ? 1 : -1;
        int a = inner + s, b = inner + 2 * s;  // the two stair rows
        int depth = -plat - 1;
        int lo = Math.min(ex - dir, ex + dir * (depth + 1)), hi = Math.max(ex - dir, ex + dir * (depth + 1));
        kit.claim(lo, Math.min(inner, outer), hi, Math.max(inner, outer), Kit.BUILT);
        for (int i = 0; i <= depth; i++) {
            int x = ex + dir * i, y = -i;
            for (int z : new int[] {a, b}) {
                out.set(x, y, z, "stairs_concrete[facing=" + (dir > 0 ? "west" : "east") + "]");
                for (int yy = y + 1; yy <= Math.min(1, y + 3); yy++) {
                    out.set(x, yy, z, "air");
                }
                for (int yy = y - 1; yy >= plat; yy--) {
                    if (out.get(x, yy, z) == null) {
                        out.set(x, yy, z, "concrete_dark");
                    }
                }
            }
            if (i > 0) {
                out.set(x, 1, inner, "minecraft:iron_bars");
                out.set(x, 1, outer, "minecraft:iron_bars");
            }
        }
        int end = ex + dir * (depth + 1);
        out.set(end, 1, a, "minecraft:iron_bars");
        out.set(end, 1, b, "minecraft:iron_bars");
        for (int z : new int[] {inner, outer}) {
            out.set(ex - dir, 1, z, "lamp_base");
            out.set(ex - dir, 2, z, "lamp_post");
            out.set(ex - dir, 3, z, chance() < 0.75 ? "minecraft:verdant_froglight" : "minecraft:smooth_quartz");
        }
        // the sign on its posts at the far end, facing back toward the way in
        // turned 3 the sign runs toward -z from its origin, turned 1 toward +z: cover the outer three rows
        int rot = dir > 0 ? 3 : 1;
        int oz = rot == 3 ? (s > 0 ? outer : outer + 2) : (s > 0 ? outer - 2 : outer);
        Placer sp = new Placer(out, end + dir, 0, oz, rot);
        Boards.board(sp, rng, "subway", 0, 2, 0);
    }

    /**
     * A tunnel along x from xa to xb under cz, two tracks, a wall of columns between, lamps
     * long dead; it ends in a cave-in at the far end (west when west is true). Somewhere along
     * it the street above has fallen in.
     */
    private void tunnel(Kit kit, int xa, int xb, int cz, boolean west) {
        if (xb - xa < 8) {
            return;
        }
        for (int x = xa; x <= xb; x++) {
            for (int dz = -6; dz <= 6; dz++) {
                int z = cz + dz, a = Math.abs(dz);
                for (int y = -14; y <= -6; y++) {
                    String b;
                    if (a == 6 || y == -14 || y == -6) {
                        b = chance() < 0.9 ? "concrete_dark" : "concrete_dark_stain";
                    } else if (y == -13) {
                        b = chance() < 0.7 ? "minecraft:gravel" : "minecraft:tuff";
                    } else if (dz == 0 && x % 3 != 0) {
                        b = "concrete_dark";
                    } else {
                        b = "air";
                    }
                    out.set(x, y, z, b);
                }
                if (a == 2 || a == 4) {
                    out.set(x, -12, z, "rail[axis=x]");
                }
            }
            if (x % 24 == 0) {
                out.set(x, -7, cz - 5, chance() < 0.2 ? "minecraft:verdant_froglight" : "ceiling_light_off");
            }
            if (chance() < 0.01) {
                out.set(x, -12, cz + (chance() < 0.5 ? -3 : 3), any("debris_chunk", "trash_bag[facing=east]", "rubble_pile", "blood_splat"));
            }
        }
        // the cave-in at the end
        int end = west ? xa : xb, step = west ? 1 : -1;
        for (int i = 0; i < 9; i++) {
            int x = end + step * i;
            int hgt = 7 - i * 7 / 9;
            for (int dz = -5; dz <= 5; dz++) {
                for (int y = -12; y < -12 + hgt + (chance() < 0.4 ? 1 : 0); y++) {
                    out.set(x, y, cz + dz, chance() < 0.85 ? "rubble" : "minecraft:cobblestone");
                }
            }
        }
        // where the street fell in
        if (xb - xa > 60 && chance() < 0.55) {
            int sx = xa + 20 + rng.nextInt(xb - xa - 40);
            boolean crossing = kit.use(sx, cz - 9) == Kit.ROAD || kit.use(sx, cz + 9) == Kit.ROAD;
            if (!crossing) {
                sinkhole(kit, sx, cz);
            }
        }
    }

    private void sinkhole(Kit kit, int sx, int cz) {
        kit.claim(sx - 5, cz - 5, sx + 5, cz + 5, Kit.BUILT);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double dd = Math.hypot(dx / 1.2, dz);
                if (dd > 4.3 + chance() * 0.6) {
                    continue;
                }
                int x = sx + dx, z = cz + dz;
                for (int y = -5; y <= 1; y++) {
                    out.set(x, y, z, "air");
                }
                int heap = -12 + (int) Math.round((4.5 - dd) * 0.9 + chance());
                for (int y = -12; y <= heap; y++) {
                    out.set(x, y, z, chance() < 0.6 ? "rubble" : chance() < 0.5 ? "asphalt_crack" : "minecraft:cobblestone");
                }
            }
        }
        // slabs of road tipped in round the rim, barriers round it
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz += 10) {
                if (chance() < 0.6) {
                    out.set(sx + dx, 1, cz + dz, "jersey_barrier[facing=north]");
                }
            }
        }
        if (cars < carCap) {
            cars++;
            out.vehicle(sx + 0.5, -9, cz + 0.5, (float) (chance() * 360), "sedan,taxi,hatchback,city_bus", rng.nextLong());
        }
    }

    /**
     * A train standing in the station from xa to xb on the track whose rows run from za to zb,
     * its doors open on the side door: three or four cars of steel with windows down both
     * sides, seats along them, a light or two still on, what the passengers left.
     */
    private void train(int xa, int xb, int za, int zb, int door, int floor) {
        int z0 = Math.min(za, zb), z1 = Math.max(za, zb);
        int cars = 3;
        int cl = (xb - xa + 1 - (cars - 1)) / cars;
        if (cl < 8) {
            return;
        }
        for (int c = 0; c < cars; c++) {
            int c0 = xa + c * (cl + 1), c1 = c0 + cl - 1;
            for (int x = c0; x <= c1; x++) {
                for (int z = z0; z <= z1; z++) {
                    boolean side = z == z0 || z == z1, end = x == c0 || x == c1;
                    for (int y = floor - 1; y <= floor + 3; y++) {
                        String b;
                        if (y == floor - 1 || y == floor + 3) {
                            b = "train_body";
                        } else if (side) {
                            boolean doorway = z == door && Math.floorMod(x - c0 - 2, 6) < 2 && y <= floor + 1;
                            boolean window = y == floor + 1 && Math.floorMod(x - c0, 6) >= 3;
                            b = doorway ? "air" : window ? (chance() < 0.3 + hit * 0.3 ? "window_broken[axis=x]" : "window[axis=x]") : "train_body";
                        } else if (end) {
                            b = z == (z0 + z1) / 2 && y <= floor + 1 ? "air" : "train_body";
                        } else {
                            b = "air";
                        }
                        out.set(x, y, z, b);
                    }
                }
                // seats along both sides, poles and a light down the middle
                if (x > c0 + 1 && x < c1 - 1 && Math.floorMod(x - c0 - 2, 6) >= 2) {
                    out.set(x, floor, z0 + 1, "minecraft:blue_terracotta");
                    out.set(x, floor, z1 - 1, "minecraft:blue_terracotta");
                }
                if (Math.floorMod(x - c0, 4) == 1) {
                    out.set(x, floor + 2, (z0 + z1) / 2, chance() < 0.15 ? "ceiling_light" : "ceiling_light_off");
                }
                if (chance() < 0.08) {
                    out.set(x, floor, (z0 + z1) / 2, any("paper", "blood_splat", "trash_bag[facing=east]", "cardboard_box", "glass_shards"));
                }
            }
        }
    }

    // ------------------------------------------------------------------ the elevated railway

    /**
     * The elevated railway down the street at cz from x0 to x1: a concrete deck on steel girders
     * at 13, its piers at the curbs (none in the crossings), railings and two tracks; a station
     * up on it with its platforms, canopy and purple lamps and a stair down; a train stopped on
     * it; and a span or two that came down in the street.
     */
    void elevated(Kit kit, int x0, int x1, int cz) {
        int deck = 13;
        List<int[]> gaps = new ArrayList<>();
        int ng = rng.nextInt(3);
        for (int i = 0; i < ng; i++) {
            int g = x0 + 30 + rng.nextInt(Math.max(1, x1 - x0 - 60));
            if (!crossing(kit, g, cz)) {
                gaps.add(new int[] {g, g + 9 + rng.nextInt(6)});
            }
        }
        for (int x = x0; x <= x1; x++) {
            int[] gap = in(gaps, x);
            if (gap != null) {
                continue;
            }
            for (int dz = -3; dz <= 3; dz++) {
                int z = cz + dz;
                out.set(x, deck, z, chance() < 0.9 ? "concrete_dark" : "concrete_dark_crack");
                if (Math.abs(dz) == 3) {
                    out.set(x, deck - 1, z, "steel_girder");
                    out.set(x, deck + 1, z, "minecraft:iron_bars");
                } else if (x % 4 == 0) {
                    out.set(x, deck - 1, z, "steel_girder");
                }
                if (Math.abs(dz) == 2 || Math.abs(dz) == 1) {
                    out.set(x, deck + 1, z, "rail[axis=x]");
                }
            }
            if (x % 16 == 8 && !crossing(kit, x, cz)) {
                for (int s = -1; s <= 1; s += 2) {
                    int z = cz + s * 6;
                    for (int y = 1; y < deck; y++) {
                        out.set(x, y, z, "steel_girder");
                    }
                    kit.claim(x, z, x, z, Kit.BUILT);
                }
                for (int dz = -6; dz <= 6; dz++) {
                    out.set(x, deck - 1, cz + dz, "steel_girder");
                }
            }
            if (chance() < 0.04) {
                out.set(x, deck - 1 - (chance() < 0.5 ? 0 : 1), cz + rng.nextInt(7) - 3, "vines_hanging");
            }
        }
        // the spans that came down: rubble and girders across the street, a girder still hanging
        for (int[] g : gaps) {
            kit.claim(g[0], cz - 5, g[1], cz + 5, Kit.BUILT);
            for (int x = g[0]; x <= g[1]; x++) {
                for (int dz = -4; dz <= 4; dz++) {
                    int top = chance() < 0.5 ? 1 : chance() < 0.6 ? 2 : 3;
                    for (int y = 1; y <= top; y++) {
                        if (chance() < 0.75) {
                            out.set(x, y, cz + dz, chance() < 0.6 ? "rubble" : chance() < 0.5 ? "concrete_dark_crack" : "steel_girder");
                        }
                    }
                }
                if (chance() < 0.3) {
                    out.set(x, 4, cz + rng.nextInt(5) - 2, "rail[axis=x]");
                }
            }
            line(world, g[0], deck - 1, cz - 3, g[0] + 6, 1, cz - 2, "steel_girder");
            line(world, g[1], deck - 1, cz + 3, g[1] - 5, 2, cz + 3, "steel_girder");
        }
        // a station: platforms either side, a canopy on posts, purple lamps, a stair down
        int st = -1;
        for (int tries = 0; tries < 20 && st < 0; tries++) {
            int c = x0 + 20 + rng.nextInt(Math.max(1, x1 - x0 - 50));
            boolean ok = true;
            for (int x = c - 2; x <= c + 26 && ok; x++) {
                ok = in(gaps, x) == null && !crossing(kit, x, cz);
            }
            if (ok) {
                st = c;
            }
        }
        if (st >= 0) {
            for (int x = st; x < st + 24; x++) {
                for (int s = -1; s <= 1; s += 2) {
                    for (int a = 4; a <= 6; a++) {
                        int z = cz + s * a;
                        out.set(x, deck, z, a == 4 ? "minecraft:yellow_concrete" : chance() < 0.85 ? "floor_concrete" : "floor_concrete_crack");
                        out.set(x, deck - 1, z, "steel_girder");
                    }
                    out.set(x, deck + 1, cz + s * 3, "air");
                    out.set(x, deck + 1, cz + s * 7, "minecraft:iron_bars");
                    out.set(x, deck + 5, cz + s * 5, "corrugated_green");
                    out.set(x, deck + 5, cz + s * 6, "corrugated_green");
                    out.set(x, deck + 5, cz + s * 4, "corrugated_green");
                    if ((x - st) % 6 == 0) {
                        for (int y = deck + 1; y <= deck + 4; y++) {
                            out.set(x, y, cz + s * 6, "steel_girder");
                        }
                        out.set(x, deck + 4, cz + s * 5, cityLamp(0.3));
                    }
                    if ((x - st) % 8 == 3) {
                        out.set(x, deck + 1, cz + s * 6, "minecraft:dark_oak_stairs[facing=" + (s < 0 ? "north" : "south") + "]");
                    }
                }
            }
            // the stair down to the south sidewalk
            int sz = cz + 8;
            for (int i = 0; i < deck; i++) {
                int x = st + 1 + i;
                out.set(x, deck - i, sz, "stairs_concrete[facing=west]");
                out.set(x, deck - i, sz + 1, "stairs_concrete[facing=west]");
                for (int y = deck - i + 1; y <= deck - i + 3; y++) {
                    out.set(x, y, sz, "air");
                    out.set(x, y, sz + 1, "air");
                }
                out.set(x, deck - i + 1, sz + 2, "minecraft:iron_bars");
            }
            for (int x = st - 1; x <= st + deck + 1; x++) {
                for (int z = sz; z <= sz + 2; z++) {
                    kit.claim(x, z, x, z, Kit.BUILT);
                }
            }
            out.set(st, deck, sz, "floor_concrete");
            out.set(st, deck, sz + 1, "floor_concrete");
            out.set(st, deck, cz + 7, "air");
        }
        // a train stopped on the line, three cars
        int tx = x0 + 10 + rng.nextInt(Math.max(1, x1 - x0 - 80));
        boolean clear = true;
        for (int x = tx; x <= tx + 50 && clear; x++) {
            clear = in(gaps, x) == null;
        }
        if (clear && x1 - x0 > 80) {
            train(tx, tx + 50, cz + 2, cz - 2, chance() < 0.5 ? cz + 2 : cz - 2, deck + 2);
        }
    }

    private static int[] in(List<int[]> gaps, int x) {
        for (int[] g : gaps) {
            if (x >= g[0] && x <= g[1]) {
                return g;
            }
        }
        return null;
    }

    /** True where another street crosses the one at cz (its road shows beyond the sidewalks). */
    private static boolean crossing(Kit kit, int x, int cz) {
        return kit.use(x, cz - 9) == Kit.ROAD || kit.use(x, cz + 9) == Kit.ROAD;
    }
}
