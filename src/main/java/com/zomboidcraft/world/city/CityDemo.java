package com.zomboidcraft.world.city;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * The first hand-laid downtown, for judging the look before the full city generator: a main
 * avenue running north through six blocks, with brick walk-ups close by, a 30-floor office
 * tower one block up, more towers beyond, an elevated railway across the avenue, and a skyline
 * of towers on the blocks behind. Roads, sidewalks and ground floors are all at y = 0 (the road
 * surface sits two pixels lower, which makes the curb); props stand on y = 1.
 */
public final class CityDemo {
    public static final int MIN_X = -260, MAX_X = 260, MIN_Z = -420, MAX_Z = 80;
    private static final int[] CROSS = {0, -80, -160, -240, -320, -400};
    private static final int FRONT = Streets.HALF_WIDTH + 1;  // first block past the sidewalk

    private final BlockSink sink;
    private final SplittableRandom rng;
    private final Props props;
    private final long seed;
    private List<int[]> lamps = new ArrayList<>();

    public CityDemo(BlockSink sink, long seed) {
        this.sink = sink;
        this.seed = seed;
        this.rng = new SplittableRandom(seed);
        this.props = new Props(sink, rng);
    }

    public List<int[]> lamps() {
        return lamps;
    }

    public void build() {
        // a floor of packed earth and old concrete under everything
        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int z = MIN_Z; z <= MAX_Z; z++) {
                sink.set(x, 0, z, "concrete_dark");
            }
        }
        Streets streets = new Streets(sink, rng, 0);
        streets.alongZ(0, MIN_Z + 12, MAX_Z - 8).alongZ(-130, MIN_Z + 12, MAX_Z - 8).alongZ(130, MIN_Z + 12, MAX_Z - 8);
        for (int cz : CROSS) {
            streets.alongX(cz, MIN_X + 12, MAX_X - 12);
        }
        streets.build();
        lamps = streets.lamps();

        // blocks between the cross streets, from the camera's block northward
        int[][] blocks = {{FRONT, MAX_Z - 20}, {-80 + FRONT, -FRONT}, {-160 + FRONT, -80 - FRONT},
                {-240 + FRONT, -160 - FRONT}, {-320 + FRONT, -240 - FRONT}, {-400 + FRONT, -320 - FRONT}};
        for (int b = 0; b < blocks.length; b++) {
            int z0 = blocks[b][0], z1 = blocks[b][1];
            frontage(z0, z1, b, false);
            frontage(z0, z1, b, true);
            skyline(z0, z1, b);
        }
        railway(-160);
        avenueLife();
        new Decay(sink, seed, MIN_X - 8, MAX_X + 8, MIN_Z - 8, MAX_Z + 8, 250).run();
    }

    /** Buildings along one side of the main avenue within one block. */
    private void frontage(int z0, int z1, int block, boolean east) {
        int z = z1;
        while (z - z0 >= 8) {
            int space = z - z0 + 1;
            int w, d;
            boolean hero = east && block == 1 && z < -15 && space >= 28;
            boolean tower = hero || (block >= 2 && space >= 24 && rng.nextDouble() < 0.45);
            if (tower) {
                w = 28 - (hero ? 0 : rng.nextInt(2) * 4);
                d = 28;
                int floors = hero ? 30 : 14 + rng.nextInt(26);
                place(east, z - w + 1, w, d, b -> (hero
                        ? new Tower(b, rng, w, d, floors, true, Tower.DECO, 1)
                        : new Tower(b, rng, w, d, floors, block <= 2)).build());
            } else if (east && block == 0 && z < 40 && z > 25) {
                // an empty lot behind a fence, where a building came down
                w = 14;
                vacantLot(z - w + 1, w);
            } else {
                w = Math.min(space, 9 + rng.nextInt(9));
                d = 16 + rng.nextInt(10);
                int floors = 3 + rng.nextInt(block == 0 ? 6 : 8);
                int ww = w, dd = d;
                place(east, z - w + 1, w, d, b -> new Walkup(b, rng, ww, dd, floors, block <= 1).build());
            }
            z -= w;
            if (rng.nextDouble() < 0.15) {
                z -= 2 + rng.nextInt(3);  // an alley
            }
        }
    }

    private interface Builder {
        void build(Placer p);
    }

    /** Places a building whose front faces the main avenue, spanning world z from minZ. */
    private void place(boolean east, int minZ, int w, int d, Builder b) {
        int minX = east ? FRONT : -FRONT - d + 1;
        b.build(Placer.forLot(sink, minX, 0, minZ, w, d, east ? 3 : 1));
    }

    private void vacantLot(int minZ, int w) {
        int x0 = FRONT, x1 = FRONT + 22;
        props.fence(x0, minZ, x0, minZ + w - 1, 1, 3);
        props.debris(x0 + 1, minZ, x1, minZ + w - 1, 1, 0.5);
        for (int i = 0; i < 6; i++) {
            int x = x0 + 3 + rng.nextInt(16), z = minZ + rng.nextInt(w);
            int h = 1 + rng.nextInt(3);
            sink.set(x, h, z, "rubble");
            for (int y = 1; y < h; y++) {
                sink.set(x, y, z, "rubble");
            }
        }
    }

    /** The towers behind the avenue: plain floors, but they make the skyline. */
    private void skyline(int z0, int z1, int block) {
        for (int side = -1; side <= 1; side += 2) {
            for (int x = 46; x < 250; x += 30) {
                if (Math.abs(x - 130) < 26) {
                    continue;  // the side avenue
                }
                for (int z = z1 - 27; z >= z0; z -= 30) {
                    if (rng.nextDouble() < 0.12) {
                        continue;
                    }
                    int w = 20 + rng.nextInt(3) * 4, d = 20 + rng.nextInt(3) * 4;
                    int floors = 12 + rng.nextInt(40);
                    int minX = side > 0 ? x : -x - d;
                    Placer p = Placer.forLot(sink, minX, 0, z, w, d, side > 0 ? 3 : 1);
                    if (rng.nextDouble() < 0.3) {
                        new Walkup(p, rng, Math.min(w, 18), d, 4 + rng.nextInt(6), false).build();
                    } else {
                        new Tower(p, rng, w, d, floors, false).build();
                    }
                }
            }
        }
    }

    /** An elevated railway on piers down the middle of a cross street. */
    private void railway(int cz) {
        int deck = 13;
        for (int x = MIN_X + 12; x <= MAX_X - 12; x++) {
            for (int dz = -3; dz <= 3; dz++) {
                int z = cz + dz;
                sink.set(x, deck, z, "concrete_dark");
                if (Math.abs(dz) == 3) {
                    sink.set(x, deck - 1, z, "steel_girder[axis=x]");
                    sink.set(x, deck + 1, z, "railing[facing=" + (dz < 0 ? "north" : "south") + "]");
                }
            }
            sink.set(x, deck + 1, cz - 2, "rail[axis=x]");
            sink.set(x, deck + 1, cz - 1, "rail[axis=x]");
            sink.set(x, deck + 1, cz + 1, "rail[axis=x]");
            sink.set(x, deck + 1, cz + 2, "rail[axis=x]");
            if (Math.floorMod(x, 40) == 20 && Math.abs(x) > 10) {
                for (int y = 1; y < deck; y++) {
                    sink.set(x, y, cz, "concrete_dark");
                    sink.set(x + 1, y, cz, "concrete_dark");
                }
            }
            if (rng.nextDouble() < 0.08) {
                sink.set(x, deck - 1, cz + rng.nextInt(5) - 2, "vines_hanging");
            }
        }
        // a derailed carriage left on the line
        for (int x = 30; x < 52; x++) {
            for (int y = deck + 1; y <= deck + 4; y++) {
                for (int dz = -2; dz <= 1; dz++) {
                    boolean side = dz == -2 || dz == 1;
                    boolean window = side && y == deck + 3 && x % 3 != 0;
                    sink.set(x, y, cz + dz, side || y == deck + 1 || y == deck + 4 ? (window ? "window" : "train_body") : "air");
                }
            }
        }
    }

    /** Abandoned traffic, barricades and the street going back to nature. */
    private void avenueLife() {
        // parked along the curbs and left in the lanes
        for (int side = -1; side <= 1; side += 2) {
            for (int z = MAX_Z - 12; z > MIN_Z + 20; z -= 7 + rng.nextInt(4)) {
                if (Math.floorMod(z + 13, 80) < 28 || rng.nextDouble() < 0.5) {
                    continue;
                }
                props.car(side < 0 ? -7 : 5, 1, z, side < 0 ? 2 : 0);
            }
        }
        for (int z = 30; z > -400; z -= 30 + rng.nextInt(40)) {
            if (Math.floorMod(z + 7, 80) < 22) {
                continue;  // keep junctions mostly clear
            }
            boolean northbound = rng.nextBoolean();
            int lane = northbound ? 1 + rng.nextInt(3) : -4 - rng.nextInt(3);
            int facing = rng.nextDouble() < 0.15 ? (rng.nextBoolean() ? 1 : 3) : northbound ? 0 : 2;
            if (facing == (northbound ? 0 : 2) && rng.nextDouble() < 0.2) {
                // a bus, truck or fire engine abandoned mid-lane
                props.vehicle(lane + 1.5, 1, z, Props.yaw(facing) + (float) (rng.nextDouble() * 10 - 5), 5.5, 12);
            } else {
                props.car(lane, 1, z, facing);
            }
        }
        props.barrierLine(-7, -88, 7, -88, 1);
        props.barrierLine(-7, -232, 7, -232, 1);
        props.debris(-7, MIN_Z + 20, 7, MAX_Z - 10, 1, 0.07);
        for (int side = -1; side <= 1; side += 2) {
            int x0 = side * (Streets.HALF_ROAD + 1), x1 = side * Streets.HALF_WIDTH;
            props.debris(Math.min(x0, x1), MIN_Z + 20, Math.max(x0, x1), MAX_Z - 10, 1, 0.05);
        }
    }
}
