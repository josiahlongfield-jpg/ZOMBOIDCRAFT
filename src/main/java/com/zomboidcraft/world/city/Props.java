package com.zomboidcraft.world.city;

import java.util.SplittableRandom;

/**
 * Street dressing that tells the story of the collapse: abandoned and burnt-out cars, police
 * barriers, fallen masonry, weeds pushing through the asphalt, chain-link around empty lots.
 */
public final class Props {
    private static final String[] COLOURS = {"red", "white", "blue", "white", "green", "grey"};

    private final BlockSink sink;
    private final SplittableRandom rng;

    public Props(BlockSink sink, SplittableRandom rng) {
        this.sink = sink;
        this.rng = rng;
    }

    /**
     * A car three wide and six long, its nose toward `facing`, with its rear-left corner block
     * at (x, y, z) in world space. Wrecks lose glass and rust through; a few are burnt out.
     */
    public void car(int x, int y, int z, int facing) {
        Placer p = Placer.forLot(sink, x, y, z, 3, 6, facing);
        boolean burnt = rng.nextDouble() < 0.2;
        String c = burnt ? "burnt" : COLOURS[rng.nextInt(COLOURS.length)];
        double rot = burnt ? 1 : rng.nextDouble() * 0.5;
        for (int dz = 0; dz < 6; dz++) {
            for (int dx = 0; dx < 3; dx++) {
                boolean wheel = (dz == 1 || dz == 4) && dx != 1;
                p.set(dx, 0, dz, wheel ? "tire[axis=x]" : body(c, rot));
                String top;
                if (dz <= 1) {
                    top = "car_hood_" + c;
                } else if (dz == 5) {
                    top = "car_hood_" + c;
                } else {
                    boolean smashed = burnt || rng.nextDouble() < 0.3;
                    top = "car_cabin_" + c + (smashed ? "_broken" : "");
                }
                p.set(dx, 1, dz, top);
            }
        }
        if (!burnt && rng.nextDouble() < 0.3) {
            // a door hanging open
            p.set(rng.nextBoolean() ? -1 : 3, 0, 2 + rng.nextInt(2), "car_door_" + c + "[facing=" + (rng.nextBoolean() ? "east" : "west") + "]");
        }
    }

    private String body(String c, double rust) {
        return rng.nextDouble() < rust * 0.6 ? "rust" : "car_" + c;
    }

    /** A row of concrete barriers across a road, with a gap someone pushed open. */
    public void barrierLine(int x0, int z0, int x1, int z1, int y) {
        boolean alongX = z0 == z1;
        int gap = rng.nextInt(Math.max(1, Math.abs(alongX ? x1 - x0 : z1 - z0)));
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                if (Math.abs(n++ - gap) < 2) {
                    continue;
                }
                sink.set(x, y, z, "jersey_barrier[facing=" + (alongX ? "north" : "east") + "]");
            }
        }
    }

    /** Broken masonry and litter scattered over an area, plus weeds through the cracks. */
    public void debris(int x0, int z0, int x1, int z1, int y, double density) {
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                String under = sink.get(x, y - 1, z);
                if (under == null || sink.get(x, y, z) != null) {
                    continue;
                }
                double r = rng.nextDouble();
                if (r < density * 0.25) {
                    sink.set(x, y, z, "rubble_pile");
                } else if (r < density * 0.4) {
                    sink.set(x, y, z, "debris_chunk[facing=" + dir() + "]");
                } else if (r < density * 0.75) {
                    sink.set(x, y, z, "weeds");
                } else if (r < density) {
                    sink.set(x, y, z, "moss_carpet");
                }
            }
        }
    }

    /** A chain-link fence with barbed wire on top, from (x0, z0) to (x1, z1) along one axis. */
    public void fence(int x0, int z0, int x1, int z1, int y, int height) {
        boolean alongX = z0 == z1;
        int n = 0;
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); x++) {
            for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); z++) {
                boolean post = n++ % 4 == 0;
                for (int h = 0; h < height; h++) {
                    sink.set(x, y + h, z, post ? "fence_post" : "chainlink[axis=" + (alongX ? "x" : "z") + "]");
                }
                sink.set(x, y + height, z, "barbed_wire[axis=" + (alongX ? "x" : "z") + "]");
            }
        }
    }

    private String dir() {
        return new String[] {"north", "east", "south", "west"}[rng.nextInt(4)];
    }
}
