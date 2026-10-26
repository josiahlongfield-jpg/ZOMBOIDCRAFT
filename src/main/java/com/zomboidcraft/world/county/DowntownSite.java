package com.zomboidcraft.world.county;

import com.zomboidcraft.world.city.BlockSink;
import com.zomboidcraft.world.city.Decay;
import com.zomboidcraft.world.city.PackTower;
import com.zomboidcraft.world.city.Placer;
import com.zomboidcraft.world.city.ShopLights;
import com.zomboidcraft.world.city.Tower;
import com.zomboidcraft.world.city.Walkup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A city's downtown on a grid of avenues and cross streets, in three kinds:
 * <ul>
 * <li>metro: glass and concrete towers of twenty to fifty floors on every core block, a plaza
 * with its fountain and the aid station that was set up in it, a tower going up under its
 * crane when it all stopped, a parking garage, the general hospital, police headquarters and
 * a subway line under the main avenue, walk-ups thinning out toward the edges;</li>
 * <li>city: mid-rise towers and old brick, a park in the middle, a cathedral, the hospital, a
 * precinct house and a subway stop;</li>
 * <li>old_city: brick walk-ups shoulder to shoulder, stone and brick towers of the twenties,
 * the courthouse square in the middle, the elevated railway on its piers.</li>
 * </ul>
 * Every tower can be walked into and climbed: lobby, office floors, stairwells to the roof.
 * Lights are the city's purple: street lamps, lobby chandeliers, entrance canopies, the crowns
 * that still glow, with the odd green or amber among them. The streets are where the city
 * gave up: cars left in the lanes, buses and police cars, barricades at the crossings, trees
 * pushing up through the asphalt, and every building ruined by the Decay pass.
 */
final class DowntownSite extends Site {
    /** One city block: the buildable box inside the sidewalks, and which sides face a street. */
    private record Blk(int x0, int z0, int x1, int z1, boolean[] street, double t) {
        int w() {
            return x1 - x0 + 1;
        }

        int d() {
            return z1 - z0 + 1;
        }

        int cx() {
            return (x0 + x1) / 2;
        }

        int cz() {
            return (z0 + z1) / 2;
        }

        /** The side's length: along x for north and south, along z for east and west. */
        int len(int side) {
            return (side & 1) == 0 ? w() : d();
        }

        /** The block's depth back from a side. */
        int depth(int side) {
            return (side & 1) == 0 ? d() : w();
        }
    }

    private enum Role { TOWERS, MIXED, WALKUPS, PLAZA, HOSPITAL, POLICE, PARKING, CONSTRUCTION, CHURCH, VACANT, HALL }

    private Kit kit;
    private CityCivic civic;
    /** Street centre lines: x of the streets running north-south, z of those running east-west. */
    private final List<Integer> xs = new ArrayList<>(), zs = new ArrayList<>();
    private final List<Kit.Style> xStyle = new ArrayList<>(), zStyle = new ArrayList<>();
    private final List<Kit.Lane> lanes = new ArrayList<>();
    /** Boxes {x0, z0, x1, z1, height} for the Decay pass: each building with a block of margin. */
    private final List<int[]> ruins = new ArrayList<>();
    /** The parking levels under the big towers, one of which hides a bunker. */
    private final List<Garage> garages = new ArrayList<>();
    private int pitch, minFloors, maxFloors, walkMin, walkMax, carBudget;
    private double glass;
    private double hit;

    DowntownSite(Place p, BlockSink out) {
        super(p, out);
    }

    @Override
    void build() {
        kit = new Kit(out, rng, w, d);
        hit = 0.4 + chance() * 0.6;
        switch (p.variant) {
            case "old_city" -> {
                pitch = 64;
                minFloors = 7;
                maxFloors = 20;
                walkMin = 3;
                walkMax = 8;
                glass = 0.05;
                carBudget = 26;
            }
            case "city" -> {
                pitch = 74;
                minFloors = 10;
                maxFloors = 32;
                walkMin = 3;
                walkMax = 8;
                glass = 0.3;
                carBudget = 32;
            }
            default -> {
                pitch = 82;
                minFloors = 16;
                maxFloors = 50;
                walkMin = 4;
                walkMax = 10;
                glass = 0.5;
                carBudget = 40;
            }
        }
        // the tallest crown still has to fit under the build limit
        maxFloors = Math.min(maxFloors, (300 - p.level - Tower.LOBBY - 24) / Tower.FLOOR);
        minFloors = Math.min(minFloors, maxFloors - 4);
        civic = new CityCivic(kit, hit);
        civic.old = variant("old_city");
        civic.carCap = carBudget / 3;
        grid();
        kit.pave();
        crosswalks();
        List<Blk> blocks = blocks();
        Role[] roles = roles(blocks);
        for (int i = 0; i < blocks.size(); i++) {
            fill(blocks.get(i), roles[i]);
        }
        subway();
        hideout();
        if (variant("old_city") || variant("metro") && chance() < 0.5) {
            elevated();
        }
        streetLife();
        streetLamps();
        for (int[] r : ruins) {
            new Decay(out, rng.nextLong(), Math.max(0, r[0]), Math.min(w - 1, r[2]), Math.max(0, r[1]), Math.min(d - 1, r[3]),
                    r[4]).run();
        }
        kit.overgrow(0.55);
        kit.roadWeeds(0.35);
        kit.tidyPlants();
        shelter();
    }

    /**
     * Most old cities dug a fallout shelter under the courthouse, deep enough to pass under the
     * subway and the towers' bunkers; the plate by its door still says so. Its own dice again.
     */
    private void shelter() {
        SplittableRandom g = new SplittableRandom(Noise.hash(p.seed, 0x5E17, 0xC17E));
        if (civic.courthouse == null || g.nextDouble() > 0.75) {
            return;
        }
        double r = g.nextDouble();
        Bunker.State state = r < 0.45 ? Bunker.State.SEALED : r < 0.7 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int[] in = civic.courtInterior;
        if (Shelter.dig(civic.courthouse, g, state, in, civic.courtFloor, -28 + civic.courtFloor, new int[] {2, 2, w - 3, d - 3})) {
            Shelter.plate(civic.courthouse, in[0], in[2], 6);
        }
    }

    // ------------------------------------------------------------------ the grid

    private void grid() {
        int cx = p.gateX(), cz = p.gateZ();
        int margin = 30;
        for (int k = -10; k <= 10; k++) {
            int x = cx + k * pitch;
            if (x >= margin && x <= w - 1 - margin) {
                xs.add(x);
                xStyle.add(k == 0 || variant("metro") && k % 2 == 0 ? Kit.Style.MAIN : Kit.Style.CITY);
            }
            int z = cz + k * pitch;
            if (z >= margin && z <= d - 1 - margin) {
                zs.add(z);
                zStyle.add(k == 0 || variant("metro") && k % 2 == 0 ? Kit.Style.MAIN : Kit.Style.CITY);
            }
        }
        // the streets run edge to edge; the gate lines carry on out to the county roads
        for (int i = 0; i < xs.size(); i++) {
            int x = xs.get(i);
            boolean gate = x == cx;
            lanes.add(kit.lane(xStyle.get(i), x + 0.5, gate ? -3 : zs.get(0) - 0.5, x + 0.5,
                    gate ? d + 2 : zs.get(zs.size() - 1) + 0.5));
        }
        for (int i = 0; i < zs.size(); i++) {
            int z = zs.get(i);
            boolean gate = z == cz;
            lanes.add(kit.lane(zStyle.get(i), gate ? -3 : xs.get(0) - 0.5, z + 0.5, gate ? w + 2 : xs.get(xs.size() - 1) + 0.5,
                    z + 0.5));
        }
    }

    private static int reach(Kit.Style s) {
        return (int) Math.ceil(s.reach());
    }

    /** Zebra stripes across every street just outside each crossing. */
    private void crosswalks() {
        for (int i = 0; i < xs.size(); i++) {
            for (int j = 0; j < zs.size(); j++) {
                int x = xs.get(i), z = zs.get(j);
                int hx = (int) xStyle.get(i).half, hz = (int) zStyle.get(j).half;
                int rx = reach(xStyle.get(i)), rz = reach(zStyle.get(j));
                // across the north-south street, north and south of the crossing
                for (int sgn = -1; sgn <= 1; sgn += 2) {
                    int z0 = z + sgn * (rz + 1);
                    for (int k = 0; k < 3; k++) {
                        for (int dx = -hx; dx <= hx; dx++) {
                            if (Math.floorMod(dx, 2) == 0 && kit.use(x + dx, z0 + sgn * k) == Kit.ROAD && chance() > 0.15) {
                                out.set(x + dx, 0, z0 + sgn * k, "crosswalk");
                            }
                        }
                    }
                    int x0 = x + sgn * (rx + 1);
                    for (int k = 0; k < 3; k++) {
                        for (int dz = -hz; dz <= hz; dz++) {
                            if (Math.floorMod(dz, 2) == 0 && kit.use(x0 + sgn * k, z + dz) == Kit.ROAD && chance() > 0.15) {
                                out.set(x0 + sgn * k, 0, z + dz, "crosswalk");
                            }
                        }
                    }
                }
            }
        }
    }

    /** The blocks between the streets, and the fringe between the outer streets and the edge. */
    private List<Blk> blocks() {
        List<Blk> made = new ArrayList<>();
        int nx = xs.size(), nz = zs.size();
        double half = Math.max(w, d) / 2.0;
        for (int i = -1; i < nx; i++) {
            int x0 = i < 0 ? 3 : xs.get(i) + reach(xStyle.get(i)) + 1;
            int x1 = i + 1 >= nx ? w - 4 : xs.get(i + 1) - reach(xStyle.get(i + 1)) - 1;
            for (int j = -1; j < nz; j++) {
                int z0 = j < 0 ? 3 : zs.get(j) + reach(zStyle.get(j)) + 1;
                int z1 = j + 1 >= nz ? d - 4 : zs.get(j + 1) - reach(zStyle.get(j + 1)) - 1;
                if (x1 - x0 < 14 || z1 - z0 < 14) {
                    continue;
                }
                boolean[] street = {j >= 0, i + 1 < nx, j + 1 < nz, i >= 0};
                double t = Math.max(Math.abs((x0 + x1) / 2.0 - p.gateX()), Math.abs((z0 + z1) / 2.0 - p.gateZ())) / half;
                made.add(new Blk(x0, z0, x1, z1, street, t));
            }
        }
        return made;
    }

    /** What each block becomes: the core towers, the civic blocks, the fringe. */
    private Role[] roles(List<Blk> blocks) {
        Role[] roles = new Role[blocks.size()];
        List<Integer> inner = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            Blk b = blocks.get(i);
            boolean full = b.street[0] && b.street[1] && b.street[2] && b.street[3];
            if (!full) {
                roles[i] = chance() < 0.18 ? Role.VACANT : Role.WALKUPS;
                continue;
            }
            inner.add(i);
            roles[i] = b.t < 0.32 ? Role.TOWERS : b.t < 0.62 ? (chance() < 0.55 ? Role.TOWERS : Role.MIXED) : Role.MIXED;
            if (variant("old_city") && chance() < 0.55) {
                roles[i] = b.t < 0.3 ? Role.MIXED : Role.WALKUPS;
            }
        }
        inner.sort(Comparator.comparingDouble(i -> blocks.get(i).t));
        // the middle block is the square; the rest of the civic blocks go to their rings
        List<Role> wanted = new ArrayList<>();
        wanted.add(variant("old_city") ? Role.HALL : Role.PLAZA);
        wanted.add(Role.HOSPITAL);
        wanted.add(Role.POLICE);
        if (!variant("old_city")) {
            wanted.add(Role.CONSTRUCTION);
        }
        wanted.add(Role.PARKING);
        if (!variant("metro")) {
            wanted.add(Role.CHURCH);
        }
        if (variant("metro")) {
            wanted.add(Role.PARKING);
        }
        wanted.add(Role.VACANT);
        if (inner.isEmpty()) {
            return roles;
        }
        roles[inner.get(0)] = wanted.remove(0);
        List<Integer> rest = new ArrayList<>(inner.subList(1, inner.size()));
        // shuffle the others, then hand out in turn: the civic buildings end up all over the grid
        for (int i = rest.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = rest.get(i);
            rest.set(i, rest.get(j));
            rest.set(j, t);
        }
        for (int k = 0; k < rest.size() && !wanted.isEmpty(); k++) {
            Blk b = blocks.get(rest.get(k));
            Role r = wanted.get(0);
            // the hospital and the police want a block that isn't right in the middle
            if ((r == Role.HOSPITAL || r == Role.POLICE) && b.t < 0.2) {
                continue;
            }
            roles[rest.get(k)] = wanted.remove(0);
        }
        return roles;
    }

    // ------------------------------------------------------------------ filling a block

    private void fill(Blk b, Role role) {
        switch (role) {
            case TOWERS -> towers(b);
            case MIXED -> mixed(b);
            case PLAZA -> plaza(b);
            case HALL -> hall(b);
            case HOSPITAL -> hospital(b);
            case POLICE -> police(b);
            case PARKING -> parking(b);
            case CONSTRUCTION -> construction(b);
            case CHURCH -> church(b);
            case VACANT -> vacant(b);
            default -> walkups(b);
        }
    }

    /**
     * A lot on a block's side, along metres from the corner the side starts at (west end for
     * north and south, north end for east and west), lw along the street and ld back from it;
     * claimed, and its Placer faces the street.
     */
    private Placer lotOn(Blk b, int side, int along, int lw, int ld) {
        int minX, minZ, sx, sz;
        switch (side & 3) {
            case 0 -> { minX = b.x0 + along; minZ = b.z0; sx = lw; sz = ld; }
            case 2 -> { minX = b.x0 + along; minZ = b.z1 - ld + 1; sx = lw; sz = ld; }
            case 1 -> { minX = b.x1 - ld + 1; minZ = b.z0 + along; sx = ld; sz = lw; }
            default -> { minX = b.x0; minZ = b.z0 + along; sx = ld; sz = lw; }
        }
        kit.claim(minX, minZ, minX + sx - 1, minZ + sz - 1, Kit.BUILT);
        return Placer.forLot(out, minX, 0, minZ, lw, ld, side);
    }

    /** The world box of a lot on a side, as {x0, z0, x1, z1}. */
    private static int[] boxOn(Blk b, int side, int along, int lw, int ld) {
        return switch (side & 3) {
            case 0 -> new int[] {b.x0 + along, b.z0, b.x0 + along + lw - 1, b.z0 + ld - 1};
            case 2 -> new int[] {b.x0 + along, b.z1 - ld + 1, b.x0 + along + lw - 1, b.z1};
            case 1 -> new int[] {b.x1 - ld + 1, b.z0 + along, b.x1, b.z0 + along + lw - 1};
            default -> new int[] {b.x0, b.z0 + along, b.x0 + ld - 1, b.z0 + along + lw - 1};
        };
    }

    private void ruin(int[] box, int height) {
        ruins.add(new int[] {box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1, height});
    }

    private int towerStyle() {
        double r = chance();
        if (r < glass) {
            return Tower.GLASS;
        }
        r = chance();
        return variant("old_city") ? (r < 0.55 ? Tower.BRICK : Tower.DECO) : r < 0.45 ? Tower.GRID : r < 0.75 ? Tower.DECO : Tower.BRICK;
    }

    private int floors(double t) {
        // taller toward the middle
        double k = Math.max(0, 1 - t * 1.3);
        int lo = minFloors + (int) ((maxFloors - minFloors) * 0.35 * k);
        int hi = minFloors + (int) ((maxFloors - minFloors) * (0.45 + 0.55 * k));
        return lo + rng.nextInt(Math.max(1, hi - lo + 1));
    }

    /** A tower on a side of the block; its lobby lit purple and its decay box noted. */
    private void tower(Blk b, int side, int along, int tw, int td, int floors) {
        tw = Math.max(20, tw);
        td = Math.max(16, td);
        if (chance() < 0.55 && packTower(b, side, along, tw, td, 0, Tower.height(floors), true)) {
            return;
        }
        Placer pl = lotOn(b, side, along, tw, td);
        Tower t = new Tower(pl, rng, tw, td, floors, chance() < 0.85, towerStyle(), rng.nextInt(4));
        t.build();
        garage(pl, t, tw, td, boxOn(b, side, along, tw, td));
        civic.chandeliers(pl, tw, td, Tower.LOBBY);
        ruin(boxOn(b, side, along, tw, td), Tower.height(floors) + 12);
    }

    /**
     * One of the buildings from the pack Josiah bought ({@link PackTower}) in a lot tw along the
     * street and td deep (set back from the street by set), as near height blocks tall as one
     * fits; centred along the lot. False when none fits.
     */
    private boolean packTower(Blk b, int side, int along, int tw, int td, int set, int height, boolean office) {
        PackTower.Template t = PackTower.pick(rng, tw, td, height, office);
        if (t == null || p.level + t.height() > 316) {
            return false;
        }
        int at = along + (tw - t.w()) / 2;
        int[] box = boxOn(b, side, at, t.w(), t.d() + set);
        if (!kit.free(box[0], box[1], box[2], box[3])) {
            return false;
        }
        Placer pl = set > 0 ? lotOnSet(b, side, at, t.w(), t.d(), set) : lotOn(b, side, at, t.w(), t.d());
        new PackTower(pl, rng, t, chance() < 0.85).build();
        ruin(box, t.height() + 8);
        return true;
    }

    /** Walk-ups shoulder to shoulder along a side from along a0 to a1, depth back from the street. */
    private void frontage(Blk b, int side, int a0, int a1, int depth) {
        int a = a0;
        // the pack's apartment blocks and corner shops can go deeper than a walk-up, up to half the block
        int deep = Math.min(42, b.depth(side) / 2 - 1);
        while (a1 - a + 1 >= 9) {
            int space = a1 - a + 1;
            if (chance() < 0.4) {
                PackTower.Template t = PackTower.pick(rng, space, deep, (walkMin + walkMax) / 2 * Walkup.FLOOR + 6, false);
                if (t != null) {
                    int[] box = boxOn(b, side, a, t.w(), t.d());
                    if (kit.free(box[0], box[1], box[2], box[3])) {
                        new PackTower(lotOn(b, side, a, t.w(), t.d()), rng, t, true).build();
                        ruin(box, t.height() + 6);
                        a += t.w();
                        continue;
                    }
                }
            }
            int lw = space <= 18 ? space : 9 + rng.nextInt(9);
            if (space - lw < 9 && space - lw > 0) {
                lw = space;  // no sliver left at the corner
            }
            double r = chance();
            if (r < 0.07) {
                // a gap where one came down: rubble behind a chain-link fence
                rubbleLot(boxOn(b, side, a, lw, depth), side);
            } else {
                int dd = Math.min(depth, 16 + rng.nextInt(10));
                int floors = walkMin + rng.nextInt(walkMax - walkMin + 1);
                Placer pl = lotOn(b, side, a, lw, dd);
                new Walkup(pl, rng, lw, dd, floors, true).build();
                ruin(boxOn(b, side, a, lw, dd), floors * Walkup.FLOOR + 6);
            }
            a += lw;
            if (chance() < 0.12 && a1 - a > 14) {
                a += 2 + rng.nextInt(2);  // an alley between
            }
        }
    }

    /** The back of a block: an alley of cracked asphalt, bins, pallets and what was dumped there. */
    private void backyard(Blk b) {
        for (int x = b.x0; x <= b.x1; x++) {
            for (int z = b.z0; z <= b.z1; z++) {
                if (kit.use(x, z) == Kit.FREE) {
                    double r = chance();
                    out.set(x, 0, z, r < 0.55 ? kit.asphalt(0.5) : r < 0.8 ? "floor_concrete_crack" : r < 0.9 ? "minecraft:gravel"
                            : "minecraft:grass_block");
                    if (chance() < 0.02) {
                        out.set(x, 1, z, kit.pick("trash_bag[facing=north]", "crate", "cardboard_box", "debris_chunk", "paper",
                                "minecraft:barrel[facing=up,loot=storage]", "tire[axis=x]"));
                    }
                }
            }
        }
    }

    private void walkups(Blk b) {
        int depth = Math.min(22, Math.min(b.w(), b.d()) / 2);
        for (int side = 0; side < 4; side++) {
            if (!b.street[side]) {
                continue;
            }
            // north and south sides run the full length; east and west fit between them
            boolean ns = (side & 1) == 0;
            int a0 = ns ? 0 : (b.street[0] ? depth : 0), a1 = ns ? b.len(side) - 1 : b.len(side) - 1 - (b.street[2] ? depth : 0);
            frontage(b, side, a0, a1, depth);
        }
        backyard(b);
    }

    /**
     * Parking levels under a tower with the room for them, clear of the subway under the main
     * avenue. They roll their own dice, so the streets above come out as they always did.
     */
    private void garage(Placer pl, Tower t, int tw, int td, int[] box) {
        int cz = p.gateZ();
        if (!Garage.fits(tw, td) || box[3] >= cz - 16 && box[1] <= cz + 16) {
            return;
        }
        SplittableRandom g = new SplittableRandom(Noise.hash(p.seed, box[0], box[1]));
        Garage garage = new Garage(pl, g, tw, td, 2 + g.nextInt(2), t.coreX(), t.coreZ());
        garage.build();
        garages.add(garage);
    }

    /**
     * One tower in the city hides a bunker under its lowest level, down a ladder from the
     * electrical room; the other garages keep junk in that cage.
     */
    private void hideout() {
        if (garages.isEmpty()) {
            return;
        }
        SplittableRandom g = new SplittableRandom(Noise.hash(p.seed, 0x6B75, 0x6E6B));
        double r = g.nextDouble();
        Bunker.State state = r < 0.45 ? Bunker.State.SEALED : r < 0.7 ? Bunker.State.LOOTED : Bunker.State.BREACHED;
        int first = g.nextInt(garages.size());
        boolean done = false;
        for (int i = 0; i < garages.size(); i++) {
            Garage garage = garages.get((first + i) % garages.size());
            if (done) {
                garage.cageJunk();
            } else {
                done = garage.bunker(state, new int[] {2, 2, w - 3, d - 3}) != null;
            }
        }
    }

    /** Towers on the front street, walk-ups round the back. */
    private void mixed(Blk b) {
        int front = bigSide(b), back = (front + 2) & 3;
        int len = b.len(front), dep = b.depth(front);
        int td = Math.min(dep - 18, 30);
        if (td >= 16 && len >= 22) {
            if (len >= 46 && chance() < 0.6) {
                int tw = (len - 3) / 2;
                tower(b, front, 0, tw, td, floors(b.t + 0.15));
                tower(b, front, len - tw, tw, td, floors(b.t + 0.15));
            } else {
                int tw = Math.min(len, 22 + rng.nextInt(Math.max(1, len - 21)));
                tower(b, front, (len - tw) / 2, tw, td, floors(b.t + 0.15));
                frontage(b, front, 0, (len - tw) / 2 - 1, Math.min(td, 20));
                frontage(b, front, (len - tw) / 2 + tw, len - 1, Math.min(td, 20));
            }
            frontage(b, back, 0, len - 1, Math.min(dep - td - 2, 20));
        } else {
            walkups(b);
            return;
        }
        backyard(b);
    }

    /** The side on the bigger street (the avenue), else any street side. */
    private int bigSide(Blk b) {
        int best = -1;
        double bestHalf = -1;
        for (int s = 0; s < 4; s++) {
            if (!b.street[s]) {
                continue;
            }
            double half = styleOn(b, s).half + chance() * 0.5;
            if (half > bestHalf) {
                bestHalf = half;
                best = s;
            }
        }
        return Math.max(0, best);
    }

    /** The style of the street along a block's side. */
    private Kit.Style styleOn(Blk b, int side) {
        int target = switch (side) {
            case 0 -> b.z0 - 3;
            case 2 -> b.z1 + 3;
            case 1 -> b.x1 + 3;
            default -> b.x0 - 3;
        };
        List<Integer> lines = (side & 1) == 0 ? zs : xs;
        List<Kit.Style> styles = (side & 1) == 0 ? zStyle : xStyle;
        int best = 0;
        for (int i = 1; i < lines.size(); i++) {
            if (Math.abs(lines.get(i) - target) < Math.abs(lines.get(best) - target)) {
                best = i;
            }
        }
        return styles.isEmpty() ? Kit.Style.CITY : styles.get(best);
    }

    private void towers(Blk b) {
        int front = bigSide(b), back = (front + 2) & 3;
        int len = b.len(front), dep = b.depth(front);
        double r = chance();
        if (r < 0.3 && len >= 30 && dep >= 34) {
            // one big tower set back behind its own plaza: the pack's landmarks when one fits
            PackTower.Template big = PackTower.pick(rng, len, dep, maxFloors * Tower.FLOOR + 40, true);
            if (big != null && chance() < 0.7) {
                int set = Math.min(12, dep - big.d());
                forecourt(boxOn(b, front, 0, len, set), front);
                if (packTower(b, front, 0, len, big.d(), set, big.height(), true)) {
                    backyard(b);
                    return;
                }
            }
            int tw = Math.min(len - 6, 34 + rng.nextInt(12)), td = Math.min(dep - 12, 28 + rng.nextInt(12));
            int set = Math.min(12, dep - td);
            forecourt(boxOn(b, front, 0, len, set), front);
            Placer pl = lotOnSet(b, front, (len - tw) / 2, tw, td, set);
            int floors = Math.min(maxFloors, floors(b.t) + 6);
            Tower t = new Tower(pl, rng, tw, td, floors, chance() < 0.85, towerStyle(), rng.nextInt(4));
            t.build();
            garage(pl, t, tw, td, boxOn(b, front, (len - tw) / 2, tw, td + set));
            civic.chandeliers(pl, tw, td, Tower.LOBBY);
            int[] box = boxOn(b, front, (len - tw) / 2, tw, td + set);
            ruin(box, Tower.height(floors) + 12);
            backyard(b);
            return;
        }
        if (r < 0.65 && len >= 40 && packTower(b, front, 0, len, Math.min(dep, 62), 0, Tower.height(floors(b.t)), true)) {
            // one of the pack's office towers across the front of the block
            backyard(b);
            return;
        }
        int tw = (len - 3) / 2;
        int td = r < 0.65 ? Math.min(dep, 40) : (dep - 3) / 2;
        if (tw < 20) {
            tower(b, front, 0, len, Math.min(dep, 36), floors(b.t));
        } else if (td >= dep - 2) {
            // two towers through the block
            tower(b, front, 0, tw, td, floors(b.t));
            tower(b, front, len - tw, tw, td, floors(b.t));
        } else {
            // four, two on each street
            tower(b, front, 0, tw, td, floors(b.t));
            tower(b, front, len - tw, tw, td, floors(b.t));
            tower(b, back, 0, tw, td, floors(b.t));
            tower(b, back, len - tw, tw, td, floors(b.t));
        }
        backyard(b);
    }

    /** A lot set back set metres from the street, its Placer facing the street. */
    private Placer lotOnSet(Blk b, int side, int along, int lw, int ld, int set) {
        int[] box = boxOn(b, side, along, lw, ld + set);
        int minX = box[0], minZ = box[1];
        switch (side & 3) {
            case 0 -> minZ += set;
            case 3 -> minX += set;
            default -> { }
        }
        int sx = (side & 1) == 0 ? lw : ld, sz = (side & 1) == 0 ? ld : lw;
        kit.claim(minX, minZ, minX + sx - 1, minZ + sz - 1, Kit.BUILT);
        return Placer.forLot(out, minX, 0, minZ, lw, ld, side);
    }

    /** A tower's forecourt: granite paving, planters with what grew wild in them, benches, a lamp or two. */
    private void forecourt(int[] box, int side) {
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                if (kit.use(x, z) != Kit.FREE) {
                    continue;
                }
                double r = chance();
                out.set(x, 0, z, r < 0.5 ? "minecraft:polished_andesite" : r < 0.8 ? "minecraft:stone_bricks"
                        : r < 0.92 ? "minecraft:cracked_stone_bricks" : "minecraft:moss_block");
                kit.use[z * w + x] = Kit.LOT;
            }
        }
        for (int x = box[0] + 3; x <= box[2] - 3; x += 7) {
            for (int z = box[1] + 3; z <= box[3] - 3; z += 7) {
                if (chance() < 0.55) {
                    civic.planter(x, z);
                } else if (chance() < 0.5) {
                    civic.bench(x, z, (side & 1) == 0 ? "x" : "z");
                } else {
                    civic.lampPost(x, z, chance() < 0.7);
                }
            }
        }
    }

    private void plaza(Blk b) {
        int[] box = {b.x0, b.z0, b.x1, b.z1};
        kit.claim(b.x0, b.z0, b.x1, b.z1, Kit.LOT);
        civic.plaza(box, kit);
        ruin(box, 14);
    }

    private void hall(Blk b) {
        // the old courthouse square: the courthouse on a green in the middle, lamps, the memorial
        int[] box = {b.x0, b.z0, b.x1, b.z1};
        kit.claim(b.x0, b.z0, b.x1, b.z1, Kit.LOT);
        civic.square(box, kit);
        ruin(box, 40);
    }

    private void hospital(Blk b) {
        int front = bigSide(b);
        int len = b.len(front), dep = b.depth(front);
        Placer pl = lotOn(b, front, 0, len, dep);
        civic.hospital(pl, len, dep, maxFloors >= 30 ? 7 + rng.nextInt(3) : 5 + rng.nextInt(3));
        ruin(new int[] {b.x0, b.z0, b.x1, b.z1}, 60);
    }

    private void police(Blk b) {
        int front = bigSide(b), back = (front + 2) & 3;
        int len = b.len(front), dep = b.depth(front);
        int pw = Math.min(len, 44), pd = Math.min(dep - 2, 40);
        Placer pl = lotOn(b, front, (len - pw) / 2, pw, pd);
        civic.police(pl, pw, pd);
        ruin(boxOn(b, front, (len - pw) / 2, pw, pd), 24);
        if (dep - pd >= 12) {
            frontage(b, back, 0, len - 1, Math.min(dep - pd - 1, 20));
        }
        // walk-ups either side of the station
        int side = (len - pw) / 2;
        if (side >= 9) {
            frontage(b, front, 0, side - 1, Math.min(pd, 20));
            frontage(b, front, side + pw, len - 1, Math.min(pd, 20));
        }
        backyard(b);
    }

    private void parking(Blk b) {
        int front = bigSide(b);
        int len = b.len(front), dep = b.depth(front);
        if (chance() < 0.6 && len >= 36 && dep >= 36) {
            int gw = Math.min(len, 48), gd = Math.min(dep, 44);
            Placer pl = lotOn(b, front, (len - gw) / 2, gw, gd);
            civic.garage(pl, gw, gd, 3 + rng.nextInt(3));
            ruin(boxOn(b, front, (len - gw) / 2, gw, gd), 22);
            backyard(b);
        } else {
            Placer pl = lotOn(b, front, 0, len, dep);
            civic.carLot(pl, len, dep);
        }
    }

    private void construction(Blk b) {
        int front = bigSide(b);
        int len = b.len(front), dep = b.depth(front);
        Placer pl = lotOn(b, front, 0, len, dep);
        civic.construction(pl, len, dep, Math.max(14, floors(b.t)));
        ruin(new int[] {b.x0, b.z0, b.x1, b.z1}, 30);
    }

    private void church(Blk b) {
        int front = bigSide(b);
        int len = b.len(front), dep = b.depth(front);
        int[] size = Civic.churchSize(Civic.Church.STONE);
        if (len < size[0] + 4 || dep < size[1]) {
            walkups(b);
            return;
        }
        int along = (len - size[0]) / 2;
        Placer pl = lotOn(b, front, along, size[0], size[1]);
        new Civic(pl, rng).church(Civic.Church.STONE, size[0], size[1], "stmary");
        ruin(boxOn(b, front, along, size[0], size[1]), 50);
        if (along >= 9) {
            frontage(b, front, 0, along - 2, Math.min(size[1], 20));
            frontage(b, front, along + size[0] + 1, len - 1, Math.min(size[1], 20));
        }
        int back = (front + 2) & 3;
        if (dep - size[1] >= 12) {
            frontage(b, back, 0, len - 1, Math.min(dep - size[1] - 1, 20));
        }
        backyard(b);
    }

    private void vacant(Blk b) {
        int[] box = {b.x0, b.z0, b.x1, b.z1};
        rubbleLot(box, bigSide(b));
    }

    /** Where a building came down: heaps of rubble and rebar, weeds, a fence round it with gaps. */
    private void rubbleLot(int[] box, int side) {
        kit.claim(box[0], box[1], box[2], box[3], Kit.FREE);
        for (int x = box[0]; x <= box[2]; x++) {
            for (int z = box[1]; z <= box[3]; z++) {
                double r = chance();
                out.set(x, 0, z, r < 0.35 ? "minecraft:gravel" : r < 0.55 ? "minecraft:coarse_dirt" : r < 0.65 ? "floor_concrete_crack"
                        : "minecraft:grass_block");
            }
        }
        int heaps = 1 + (box[2] - box[0]) * (box[3] - box[1]) / 300;
        for (int i = 0; i < heaps; i++) {
            int x = box[0] + 2 + rng.nextInt(Math.max(1, box[2] - box[0] - 4)), z = box[1] + 2 + rng.nextInt(Math.max(1, box[3] - box[1] - 4));
            int r = 2 + rng.nextInt(4), h = 1 + rng.nextInt(4);
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    int top = (int) Math.round(h * (1 - Math.hypot(dx, dz) / (r + 0.5)) + chance() * 0.6);
                    for (int y = 1; y <= top; y++) {
                        if (kit.inside(x + dx, z + dz)) {
                            out.set(x + dx, y, z + dz, chance() < 0.8 ? "rubble" : chance() < 0.5 ? "minecraft:iron_bars" : "debris_chunk");
                        }
                    }
                }
            }
        }
        // the fence along the street
        int[] e = switch (side & 3) {
            case 0 -> new int[] {box[0], box[1], box[2], box[1]};
            case 2 -> new int[] {box[0], box[3], box[2], box[3]};
            case 1 -> new int[] {box[2], box[1], box[2], box[3]};
            default -> new int[] {box[0], box[1], box[0], box[3]};
        };
        Kit.chainlink(new Placer(out, 0, 0, 0, 0), rng, e[0], e[1], e[2], e[3], 3);
    }

    // ------------------------------------------------------------------ under and over the streets

    /** The subway under the main east-west avenue, its station under the block west of the middle. */
    private void subway() {
        int cz = p.gateZ();
        int xa = -1, xb = -1;
        for (int i = 0; i + 1 < xs.size(); i++) {
            if (xs.get(i + 1) > p.gateX() - 5) {
                xa = xs.get(i);
                xb = xs.get(i + 1);
                break;
            }
        }
        if (xa < 0) {
            return;
        }
        int x0 = xa + reach(xStyle.get(xs.indexOf(xa))) + 2, x1 = xb - reach(xStyle.get(xs.indexOf(xb))) - 2;
        civic.subway(kit, x0, x1, cz, (int) zStyle.get(zs.indexOf(cz)).half, 2, w - 3);
    }

    /** The elevated railway down one cross street, on piers, a train stopped on it. */
    private void elevated() {
        if (zs.size() < 2) {
            return;
        }
        int j = rng.nextInt(zs.size());
        if (zs.get(j) == p.gateZ()) {
            j = (j + 1) % zs.size();
        }
        int cz = zs.get(j);
        civic.elevated(kit, 1, w - 2, cz);
    }

    // ------------------------------------------------------------------ the streets

    private void streetLife() {
        // the civic blocks have had their share of the downtown's cars; the streets get the rest
        int specials = 4 + rng.nextInt(4);
        int left = Math.max(6, carBudget - civic.cars - specials);
        double per = left * 100.0 / lanes.stream().mapToDouble(Kit.Lane::length).sum();
        for (Kit.Lane l : lanes) {
            kit.abandonedCars(l, per);
        }
        // buses and taxis left in the lanes, police cars across the crossings
        for (int i = 0; i < specials; i++) {
            Kit.Lane l = lanes.get(rng.nextInt(lanes.size()));
            double s = 20 + chance() * Math.max(1, l.length() - 40);
            double[] q = l.point(s);
            int side = chance() < 0.5 ? 1 : -1;
            double o = l.style.half * 0.5;
            double x = q[0] - q[3] * side * o, z = q[1] + q[2] * side * o;
            if (kit.use((int) x, (int) z) != Kit.ROAD) {
                continue;
            }
            float yaw = Kit.yaw(q[2] * -side, q[3] * -side) + (float) (chance() * 16 - 8);
            out.vehicle(x, 1, z, yaw, kit.pick("city_bus", "city_bus", "taxi", "taxi", "police_sedan", "police_suv", "garbage_truck",
                    "fire_truck", "ambulance", "swat_van"), rng.nextLong());
        }
        // barricades: jersey barriers and sandbags across some crossings, a checkpoint at one
        for (int i = 0; i < xs.size(); i++) {
            for (int j = 0; j < zs.size(); j++) {
                if (chance() > 0.18) {
                    continue;
                }
                int x = xs.get(i), z = zs.get(j);
                int hx = (int) xStyle.get(i).half, hz = (int) zStyle.get(j).half;
                boolean acrossX = chance() < 0.5;
                int at = (acrossX ? reach(zStyle.get(j)) : reach(xStyle.get(i))) + 2;
                for (int k = acrossX ? -hx : -hz; k <= (acrossX ? hx : hz); k++) {
                    if (chance() < 0.18) {
                        continue;
                    }
                    int bx = acrossX ? x + k : x + at, bz = acrossX ? z + at : z + k;
                    if (kit.use(bx, bz) == Kit.ROAD && out.get(bx, 1, bz) == null) {
                        out.set(bx, 1, bz, chance() < 0.7 ? "jersey_barrier[facing=" + (acrossX ? "north" : "east") + "]" : "sandbags");
                    }
                }
            }
        }
        // the street going back to nature and what was dropped on it
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                byte u = kit.use(x, z);
                if ((u != Kit.ROAD && u != Kit.WALK) || out.get(x, 1, z) != null) {
                    continue;
                }
                double r = chance();
                if (r < 0.006) {
                    out.set(x, 1, z, kit.pick("debris_chunk", "rubble_pile", "trash_bag[facing=north]", "cardboard_box", "tire[axis=x]"));
                } else if (r < 0.014) {
                    out.set(x, 1, z, kit.pick("paper", "glass_shards", "blood_splat", "leaf_litter"));
                } else if (r < 0.0155 && u == Kit.ROAD) {
                    // a tree that came up through a crack in the road
                    kit.tree(pick(new Trees.Kind[] {Trees.Kind.YOUNG, Trees.Kind.YOUNG, Trees.Kind.SICK, Trees.Kind.BUSH, Trees.Kind.MAPLE}), x, z);
                }
            }
        }
        for (Kit.Lane l : lanes) {
            if (chance() < 0.7) {
                sidewalkTrees(l);
            }
        }
    }

    /** Street trees in pits along the sidewalks, some dead, some gone wild. */
    private void sidewalkTrees(Kit.Lane l) {
        for (int side = -1; side <= 1; side += 2) {
            for (double s = 6 + rng.nextInt(6); s < l.length() - 6; s += 13 + rng.nextInt(6)) {
                double[] q = l.point(s);
                double o = l.style.half + 2.5;
                int x = (int) Math.floor(q[0] - q[3] * side * o), z = (int) Math.floor(q[1] + q[2] * side * o);
                if (kit.use(x, z) != Kit.WALK || out.get(x, 1, z) != null || chance() < 0.35) {
                    continue;
                }
                out.set(x, 0, z, "minecraft:coarse_dirt");
                kit.tree(pick(new Trees.Kind[] {Trees.Kind.MAPLE, Trees.Kind.SYCAMORE, Trees.Kind.YOUNG, Trees.Kind.SICK, Trees.Kind.DEAD}), x, z);
            }
        }
    }

    /**
     * The street lamps: tall poles with an arm, and on the end a purple globe (green or amber now
     * and then, a few dead). Every other one at the crossings.
     */
    private void streetLamps() {
        for (Kit.Lane l : lanes) {
            int side = 1;
            int spacing = l.style == Kit.Style.MAIN ? 26 : 30;
            for (double s = spacing / 2.0; s < l.length() - 4; s += spacing) {
                double[] q = l.point(s);
                double nx = -q[3] * side, nz = q[2] * side;
                double o = l.style.half + 1.6;
                int x = (int) Math.floor(q[0] + nx * o), z = (int) Math.floor(q[1] + nz * o);
                side = -side;
                if (kit.use(x, z) != Kit.WALK || out.get(x, 1, z) != null) {
                    continue;
                }
                int toward = Math.abs(nx) > Math.abs(nz) ? (nx > 0 ? 3 : 1) : (nz > 0 ? 0 : 2);
                int[][] dir = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
                int hgt = 8;
                if (chance() < 0.08 * hit) {
                    // knocked flat across the sidewalk
                    for (int k = 0; k < 5; k++) {
                        out.set(x - dir[toward][0] * k, 1, z - dir[toward][1] * k, "streetlight_pole");
                    }
                    continue;
                }
                for (int y = 1; y <= hgt; y++) {
                    out.set(x, y, z, "streetlight_pole");
                }
                int ax = x + dir[toward][0], az = z + dir[toward][1];
                out.set(ax, hgt, az, "streetlight_arm[facing=" + ((toward & 1) == 1 ? "east" : "south") + "]");
                out.set(ax + dir[toward][0], hgt, az + dir[toward][1], civic.cityLamp(0.22));
            }
        }
    }
}
