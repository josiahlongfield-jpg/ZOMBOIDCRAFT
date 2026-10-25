package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/** The car prop entity and its hull block, and how a car is laid into the world. */
public final class Cars {
    private static final ResourceKey<EntityType<?>> CAR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("car"));
    public static final EntityType<CarProp> CAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, CAR_KEY,
            EntityType.Builder.<CarProp>of(CarProp::new, MobCategory.MISC)
                    .sized(2.0f, 1.5f).clientTrackingRange(10).updateInterval(200).fireImmune().noLootTable()
                    .build(CAR_KEY));

    private static final ResourceKey<Block> HULL_KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("car_hull"));
    public static final Block HULL = Registry.register(BuiltInRegistries.BLOCK, HULL_KEY, new CarHull(BlockBehaviour.Properties.of()
            .strength(-1f, 3600000f).noOcclusion().noLootTable().noTerrainParticles()
            .isSuffocating((state, level, pos) -> false).setId(HULL_KEY)));

    public static void register() {
        // Loading the class registers the entity and the block.
    }

    /** A hull block as tall as the given number of sixteenths, 1 to 16. */
    public static BlockState hull(int sixteenths) {
        return HULL.defaultBlockState().setValue(CarHull.HEIGHT, Mth.clamp(sixteenths, 1, 16));
    }

    /**
     * The columns under a car standing at (x, z) and turned to yaw (Minecraft's: 0 faces south):
     * {block x, block z, body height in centimetres} for every column it covers.
     */
    public static List<int[]> hullColumns(CarModels.Model model, double x, double z, float yaw) {
        List<int[]> out = new ArrayList<>();
        double t = Math.toRadians(-yaw), cos = Math.cos(t), sin = Math.sin(t);
        double reach = Math.hypot(model.size[0], model.size[2]) / 2 + 0.5;
        for (int bx = Mth.floor(x - reach); bx <= Mth.floor(x + reach); bx++) {
            for (int bz = Mth.floor(z - reach); bz <= Mth.floor(z + reach); bz++) {
                float top = 0;
                for (int i = 0; i < 4; i++) {
                    for (int k = 0; k < 4; k++) {
                        double dx = bx + 0.125 + i * 0.25 - x, dz = bz + 0.125 + k * 0.25 - z;
                        top = Math.max(top, model.heightAt(dx * cos - dz * sin, dx * sin + dz * cos));
                    }
                }
                if (top > 0.15f) {
                    out.add(new int[] {bx, bz, Math.round(top * 100)});
                }
            }
        }
        return out;
    }

    /** One column's hull blocks from the ground up: sixteenths for each layer, 16 for full ones. */
    public static int[] layers(int centimetres) {
        int n = (centimetres + 99) / 100;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = Mth.clamp((int) Math.ceil(Math.min(100, centimetres - i * 100) * 16 / 100.0), 1, 16);
        }
        return out;
    }

    /** Which state a new car is left in, from the world.car* mix settings. */
    public static String pickVariant(RandomGenerator rng) {
        ZcConfig.World w = ZcConfig.get().world;
        double[] mix = {w.carUsable, w.carAbandoned, w.carWrecked, w.carBurnt};
        double total = 0;
        for (double m : mix) {
            total += Math.max(0, m);
        }
        double r = rng.nextDouble() * total;
        List<String> names = CarModels.variants();
        for (int i = 0; i < mix.length && i < names.size(); i++) {
            if ((r -= Math.max(0, mix[i])) < 0) {
                return names.get(i);
            }
        }
        return names.get(0);
    }

    /** Puts a car in the world, standing on the block below y, with its hull where there's room. */
    public static CarProp spawn(ServerLevel level, double x, double y, double z, float yaw, String model, String variant,
            boolean hull) {
        CarProp car = CAR.create(level, EntitySpawnReason.STRUCTURE);
        if (car == null) {
            return null;
        }
        car.setLook(model, variant);
        car.snapTo(x, y, z, yaw, 0f);
        level.addFreshEntity(car);
        CarModels.Model m = CarModels.get(model);
        if (hull && m != null) {
            int base = Mth.floor(y + 0.01);
            for (int[] col : hullColumns(m, x, z, yaw)) {
                int[] layers = layers(col[2]);
                for (int i = 0; i < layers.length; i++) {
                    BlockPos pos = new BlockPos(col[0], base + i, col[1]);
                    if (level.getBlockState(pos).canBeReplaced()) {
                        level.setBlock(pos, hull(layers[i]), 2);
                    }
                }
            }
        }
        return car;
    }

    /** Takes away the hull blocks under a car that's being removed. */
    static void clearHull(ServerLevel level, CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null) {
            return;
        }
        int base = Mth.floor(car.getY() + 0.01);
        for (int[] col : hullColumns(m, car.getX(), car.getZ(), car.getYRot())) {
            for (int i = 0; i < layers(col[2]).length; i++) {
                BlockPos pos = new BlockPos(col[0], base + i, col[1]);
                if (level.getBlockState(pos).is(HULL)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private Cars() {
    }
}
