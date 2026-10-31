package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The car prop entity and its hull block, and how a car is laid into the world. */
public final class Cars {
    private static final ResourceKey<EntityType<?>> CAR_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("car"));
    public static final EntityType<CarProp> CAR = Registry.register(BuiltInRegistries.ENTITY_TYPE, CAR_KEY,
            EntityType.Builder.<CarProp>of(CarProp::new, MobCategory.MISC)
                    .sized(2.0f, 1.5f).clientTrackingRange(10).updateInterval(3).fireImmune().noLootTable()
                    .build(CAR_KEY));

    private static final ResourceKey<EntityType<?>> DEBRIS_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("aircraft_debris"));
    public static final EntityType<AirDebris> DEBRIS = Registry.register(BuiltInRegistries.ENTITY_TYPE, DEBRIS_KEY,
            EntityType.Builder.<AirDebris>of(AirDebris::new, MobCategory.MISC)
                    .sized(1.2f, 0.8f).clientTrackingRange(10).updateInterval(2).fireImmune().noLootTable()
                    .build(DEBRIS_KEY));

    private static final ResourceKey<EntityType<?>> HOIST_KEY = ResourceKey.create(Registries.ENTITY_TYPE, ZomboidCraft.id("car_hoist"));
    public static final EntityType<CarHoist> HOIST = Registry.register(BuiltInRegistries.ENTITY_TYPE, HOIST_KEY,
            EntityType.Builder.<CarHoist>of(CarHoist::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).fireImmune().noLootTable()
                    .build(HOIST_KEY));

    private static final ResourceKey<Block> HULL_KEY = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id("car_hull"));
    public static final Block HULL = Registry.register(BuiltInRegistries.BLOCK, HULL_KEY, new CarHull(BlockBehaviour.Properties.of()
            .strength(-1f, 3600000f).noOcclusion().noLootTable().noTerrainParticles()
            .isSuffocating((state, level, pos) -> false).setId(HULL_KEY)));

    public static void register() {
        // a mob's blow at someone in a car lands on its windows until they give
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                !(entity.getVehicle() instanceof CarProp car && source.getEntity() instanceof Mob attacker
                        && !attacker.isPassengerOfSameVehicle(entity)) || car.blowAtRider(entity, attacker, amount));
    }

    /** A hull block from a code packing its four quarter heights (0-4 each) in base 5, north-west first. */
    public static BlockState hull(int code) {
        BlockState state = HULL.defaultBlockState();
        for (int q = 0; q < 4; q++) {
            state = state.setValue(CarHull.QUARTERS.get(q), CarHull.level(code, q));
        }
        return state;
    }

    /**
     * The columns under a car standing at (x, z) and turned to yaw (Minecraft's: 0 faces south):
     * {block x, block z, then the body height in centimetres over each quarter of the block,
     * north-west, north-east, south-west, south-east, then the underside's height over each
     * quarter the same way} for every column it covers. A quarter counts the car's height over at
     * least two of its four sample points, so the hull stays inside the body's outline rather than
     * spilling past its corners; its underside is the lowest one under the body there.
     */
    public static List<int[]> hullColumns(CarModels.Model model, double x, double z, float yaw) {
        List<int[]> out = new ArrayList<>();
        double t = Math.toRadians(-yaw), cos = Math.cos(t), sin = Math.sin(t);
        double reach = Math.hypot(model.size[0], model.size[2]) / 2 + 0.5;
        float[] samples = new float[4], unders = new float[4];
        for (int bx = Mth.floor(x - reach); bx <= Mth.floor(x + reach); bx++) {
            for (int bz = Mth.floor(z - reach); bz <= Mth.floor(z + reach); bz++) {
                int[] col = {bx, bz, 0, 0, 0, 0, 0, 0, 0, 0};
                boolean any = false;
                for (int q = 0; q < 4; q++) {
                    for (int s = 0; s < 4; s++) {
                        double dx = bx + (q & 1) * 0.5 + 0.125 + (s & 1) * 0.25 - x;
                        double dz = bz + (q >> 1) * 0.5 + 0.125 + (s >> 1) * 0.25 - z;
                        samples[s] = model.heightAt(dx * cos - dz * sin, dx * sin + dz * cos);
                        unders[s] = samples[s] > 0.15f ? model.floorAt(dx * cos - dz * sin, dx * sin + dz * cos) : 99f;
                    }
                    java.util.Arrays.sort(samples);
                    if (samples[2] > 0.15f) {
                        col[2 + q] = Math.round(samples[2] * 100);
                        col[6 + q] = Math.round(Math.min(Math.min(unders[0], unders[1]), Math.min(unders[2], unders[3])) * 100);
                        any = true;
                    }
                }
                if (any) {
                    out.add(col);
                }
            }
        }
        return out;
    }

    /** The top of the first solid thing at or below y (hull blocks aside), up to four blocks down. */
    public static double groundY(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y + 0.5), Mth.floor(z));
        for (int i = 0; i < 5; i++, pos.move(Direction.DOWN)) {
            BlockState state = level.getBlockState(pos);
            if (state.is(HULL)) {
                continue;
            }
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                return pos.getY() + shape.max(Direction.Axis.Y);
            }
        }
        return y;
    }

    /**
     * One column's hull blocks from the ground up, as hull() codes: each quarter's height above
     * the block's floor, less lift centimetres (the gap between the ground and the first block).
     * A quarter is left empty in every whole block under its underside (0 codes are no block).
     */
    public static int[] layers(int[] column, int lift) {
        int top = 0;
        for (int q = 0; q < 4; q++) {
            top = Math.max(top, column[2 + q] - lift);
        }
        int n = Math.max(0, (top + 99) / 100);
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            int code = 0;
            for (int q = 3; q >= 0; q--) {
                boolean under = column.length > 6 && column[6 + q] - lift >= (i + 1) * 100;
                int cm = under ? 0 : Mth.clamp(column[2 + q] - lift - i * 100, 0, 100);
                code = code * 5 + (cm + 12) / 25;
            }
            out[i] = code;
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

    /**
     * Puts a car in the world on the ground under (x, y, z), with its hull where there's room. The
     * ground is found rather than assumed, since roads are only 14/16 of a block tall.
     */
    public static CarProp spawn(ServerLevel level, double x, double y, double z, float yaw, String model, String variant,
            boolean hull) {
        CarProp car = CAR.create(level, EntitySpawnReason.STRUCTURE);
        if (car == null) {
            return null;
        }
        y = groundY(level, x, y, z);
        car.setLook(model, variant);
        car.snapTo(x, y, z, yaw, 0f);
        level.addFreshEntity(car);
        if (hull) {
            layHull(level, car);
        }
        return car;
    }

    /** Lays the hull blocks under a car where it stands, wherever there's room for them. */
    static void layHull(ServerLevel level, CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null) {
            return;
        }
        // hull layers start at the first whole block above the ground, so take off the gap
        double y = car.getY();
        int base = Mth.ceil(y - 0.05);
        int lift = (int) Math.round((base - y) * 100);
        for (int[] col : hullColumns(m, car.getX(), car.getZ(), car.getYRot())) {
            int[] layers = layers(col, lift);
            for (int i = 0; i < layers.length; i++) {
                if (layers[i] == 0) {
                    continue;
                }
                BlockPos pos = new BlockPos(col[0], base + i, col[1]);
                BlockState there = level.getBlockState(pos);
                if (there.canBeReplaced() && (i == 0 || !m.boat() || there.getFluidState().isEmpty())) {
                    level.setBlock(pos, hull(layers[i]), 2);
                }
            }
        }
    }

    /** Takes away the hull blocks under a car that's being removed or driven off. */
    static void clearHull(ServerLevel level, CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null) {
            return;
        }
        int base = Mth.floor(car.getY() + 0.01);
        for (int[] col : hullColumns(m, car.getX(), car.getZ(), car.getYRot())) {
            for (int i = 0; i <= 7; i++) {
                BlockPos pos = new BlockPos(col[0], base + i, col[1]);
                if (level.getBlockState(pos).is(HULL)) {
                    // a boat's bottom row stood in the sea: give the water back
                    boolean sea = m.boat() && i == 0 && level.getFluidState(pos.below()).is(net.minecraft.tags.FluidTags.WATER);
                    level.setBlock(pos, (sea ? Blocks.WATER : Blocks.AIR).defaultBlockState(), 2);
                }
            }
        }
    }

    private Cars() {
    }
}
