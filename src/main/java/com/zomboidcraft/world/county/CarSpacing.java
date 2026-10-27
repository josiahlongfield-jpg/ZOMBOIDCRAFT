package com.zomboidcraft.world.county;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps generated vehicles apart. Each one claims the ground under its body on a quarter-metre
 * grid, and one whose body would touch a vehicle already standing there is left out, so no two
 * are ever generated inside each other, however crooked a crash scene leaves them. Road vehicles
 * keep a quarter metre clear of each other; aircraft only need not overlap (decks park them
 * wingtip to wingtip).
 */
public final class CarSpacing {
    /** What the game knows about the models. Null outside the game (the previews), where nothing is checked. */
    public interface Bodies {
        /** The model a builder's car resolves to, picked from its seed exactly as the chunk writer will, or null. */
        String pick(double minLength, double maxLength, String models, long seed);

        /** Whether that model's body stands over (lx, lz) in model space: metres, centred, nose toward +z. */
        boolean covers(String model, double lx, double lz);

        /** Half the model's footprint diagonal, in metres. */
        double reach(String model);

        /** Aircraft: allowed to stand right up against their neighbours. */
        boolean tight(String model);
    }

    public static volatile Bodies bodies;

    private static final double CELL = 0.25;
    private final Set<Long> taken = new HashSet<>();

    /**
     * Claims the ground under a model standing at (x, y, z) turned to yaw (Minecraft's: 0 faces
     * south), or returns false, claiming nothing, if it would touch a vehicle already claimed
     * within a block above or below. Always true outside the game.
     */
    public boolean claim(String model, double x, int y, double z, float yaw) {
        Bodies b = bodies;
        if (b == null || model == null) {
            return true;
        }
        double t = Math.toRadians(-yaw), cos = Math.cos(t), sin = Math.sin(t);
        double reach = b.reach(model) + CELL;
        int gap = b.tight(model) ? 0 : 1;
        Set<Long> cells = new HashSet<>();
        for (int gx = (int) Math.floor((x - reach) / CELL); gx <= (int) Math.floor((x + reach) / CELL); gx++) {
            for (int gz = (int) Math.floor((z - reach) / CELL); gz <= (int) Math.floor((z + reach) / CELL); gz++) {
                double dx = (gx + 0.5) * CELL - x, dz = (gz + 0.5) * CELL - z;
                if (!b.covers(model, dx * cos - dz * sin, dx * sin + dz * cos)) {
                    continue;
                }
                for (int dy = -1; dy <= 1; dy++) {
                    for (int ix = -gap; ix <= gap; ix++) {
                        for (int iz = -gap; iz <= gap; iz++) {
                            if (taken.contains(key(gx + ix, y + dy, gz + iz))) {
                                return false;
                            }
                        }
                    }
                }
                cells.add(key(gx, y, gz));
            }
        }
        taken.addAll(cells);
        return true;
    }

    private static long key(int gx, int y, int gz) {
        return ((long) gx & 0xFFFFFF) << 40 | ((long) gz & 0xFFFFFF) << 16 | ((long) y & 0xFFFF);
    }
}
