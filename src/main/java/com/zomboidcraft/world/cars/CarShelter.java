package com.zomboidcraft.world.cars;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * On your game, a car keeps the weather out the way a train car does: its roof counts as the top of
 * the world over it (rain stops there, and nothing that asks "is it raining here" reaches inside) and
 * inside the cabin the sky's light is shut out like indoors, which tells shader packs you're out of
 * the rain and fog. Re-read every tick round the player, so a moving car takes its shelter with it.
 */
public final class CarShelter {
    /** How far round the player cars are sheltering, and how much of a body counts as roof over a column. */
    private static final double RANGE = 64, MIN_ROOF = 0.9;

    /** Per block column (x, z packed): { the car's floor (its y), the top of its roof }. */
    private static volatile Map<Long, double[]> columns = Map.of();

    /** Client tick: which columns round you have a car's roof over them now. */
    public static void update(Level level, Vec3 around) {
        if (level == null || around == null) {
            columns = Map.of();
            return;
        }
        Map<Long, double[]> found = new HashMap<>();
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, new AABB(around, around).inflate(RANGE))) {
            CarModels.Model m = CarModels.get(car.model());
            if (m == null || car.aircraft()) {
                continue;
            }
            double reach = Math.hypot(m.size[0], m.size[2]) / 2 + 1, cx = car.getX(), cz = car.getZ();
            double t = Math.toRadians(car.getYRot()), cos = Math.cos(t), sin = Math.sin(t);
            for (int x = (int) Math.floor(cx - reach); x <= (int) Math.floor(cx + reach); x++) {
                for (int z = (int) Math.floor(cz - reach); z <= (int) Math.floor(cz + reach); z++) {
                    // the column's middle in the car's own space (as Vec3.yRot turns it)
                    double dx = x + 0.5 - cx, dz = z + 0.5 - cz;
                    double lx = dx * cos + dz * sin, lz = dz * cos - dx * sin;
                    float h = m.heightAt(lx, lz);
                    if (h < MIN_ROOF) {
                        continue;
                    }
                    double[] was = found.get(key(x, z)), now = {car.getY(), car.getY() + h + 0.05};
                    if (was == null || now[1] > was[1]) {
                        found.put(key(x, z), now);
                    }
                }
            }
        }
        columns = found;
    }

    /** The top of a car's roof over this column, on your game; NaN where there's none. */
    public static double roofOver(Level level, int x, int z) {
        if (!level.isClientSide()) {
            return Double.NaN;
        }
        double[] c = columns.get(key(x, z));
        return c == null ? Double.NaN : c[1];
    }

    /** Whether a point is inside a car's cabin (under its roof, above its floor), on your game. */
    public static boolean inside(Level level, double x, double y, double z) {
        if (!level.isClientSide()) {
            return false;
        }
        double[] c = columns.get(key((int) Math.floor(x), (int) Math.floor(z)));
        return c != null && y >= c[0] && y <= c[1];
    }

    private static long key(int x, int z) {
        return (long) x << 32 ^ z & 0xFFFFFFFFL;
    }

    private CarShelter() {
    }
}
