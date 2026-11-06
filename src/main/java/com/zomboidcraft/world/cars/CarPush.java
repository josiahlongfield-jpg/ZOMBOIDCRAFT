package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.combat.Stamina;
import com.zomboidcraft.survival.TimedActions;
import com.zomboidcraft.zombie.Noise;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Pushing a car out of the way (held G at it, "Push it"): a few hard seconds shoving it a block along, away from
 * you, its tyres scraping on the road. That's how the way gets cleared past a wreck that blocks it. It costs
 * endurance and it's loud. A car only rolls on its wheels: one on its roof or its side won't go, a burnt shell
 * on bare rims takes twice as long, and the big trucks and buses are too heavy to shift by hand. It won't go
 * through anything solid, another car, or off a drop.
 */
public final class CarPush {
    /** How far one push moves it, and how long one takes, in ticks. */
    static final double STEP = 1.0;
    static final int TICKS = 40;
    /** Endurance one push takes. */
    static final double EFFORT = 8;

    public record Push(int car) implements CustomPacketPayload {
        public static final Type<Push> TYPE = new Type<>(ZomboidCraft.id("car_push"));
        public static final StreamCodec<FriendlyByteBuf, Push> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Push::car, Push::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private CarPush() {
    }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Push.TYPE, Push.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Push.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            player.level().getServer().execute(() -> push(player, payload.car()));
        });
    }

    /** Why this car can't be pushed at all, or null if it can (the menu greys the option with it). */
    public static String why(CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null || car.aircraft() || m.jet() || m.boat()) {
            return "It won't move";
        }
        if (car.isVehicle()) {
            return "Someone's in it";
        }
        if (car.pose() != 0) {
            return "It's not on its wheels";
        }
        if (car.hoisted() || car.jackCorner() >= 0) {
            return "It's up off the ground";
        }
        if (heavy(m)) {
            return "Too heavy to push";
        }
        return null;
    }

    /** The trucks and buses: more than about a big van's footprint. */
    static boolean heavy(CarModels.Model m) {
        return m.size[0] * m.size[2] > 17.5;
    }

    private static void push(ServerPlayer player, int id) {
        if (!(player.level() instanceof ServerLevel level) || !(level.getEntity(id) instanceof CarProp car)
                || car.distanceTo(player) > 6) {
            return;
        }
        String no = why(car);
        if (no != null) {
            player.sendOverlayMessage(Component.literal(no));
            return;
        }
        int ticks = "burnt".equals(car.variant()) ? TICKS * 2 : TICKS;
        level.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.8f, 0.5f);
        TimedActions.start(player, "car_push", "Pushing the car", ticks, p -> {
            if (car.isRemoved() || car.distanceTo(p) > 6 || why(car) != null) {
                return;
            }
            // away from you, along the ground
            Vec3 way = new Vec3(car.getX() - p.getX(), 0, car.getZ() - p.getZ());
            if (way.lengthSqr() < 1e-4) {
                return;
            }
            way = way.normalize().scale(STEP);
            Stamina.use(p, EFFORT);
            if (!move(level, car, car.getX() + way.x, car.getZ() + way.z)) {
                p.sendOverlayMessage(Component.literal("It won't go any further that way"));
                return;
            }
            level.playSound(null, car.getX(), car.getY(), car.getZ(), SoundEvents.GRINDSTONE_USE, SoundSource.NEUTRAL, 0.9f, 0.45f);
            Noise.emitEngine(level, car.position(), 24);
        });
    }

    /** Moves a parked car to (x, z) at the same height if its hull fits there on the same ground; whether it did. */
    static boolean move(ServerLevel level, CarProp car, double x, double z) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null) {
            return false;
        }
        int base = Mth.ceil(car.getY() - 0.05);
        Set<Long> mine = new HashSet<>();
        for (int[] col : Cars.hullColumns(m, car.getX(), car.getZ(), car.getYRot(), car.pose())) {
            mine.add(BlockPos.asLong(col[0], 0, col[1]));
        }
        List<int[]> next = Cars.hullColumns(m, x, z, car.getYRot(), car.pose());
        int height = Math.max(1, Mth.ceil(m.size[1]));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int floor = 0;
        for (int[] col : next) {
            boolean ours = mine.contains(BlockPos.asLong(col[0], 0, col[1]));
            for (int i = 0; i < height; i++) {
                BlockState there = level.getBlockState(pos.set(col[0], base + i, col[1]));
                if (!(there.canBeReplaced() || ours && there.is(Cars.HULL))) {
                    return false;   // a wall, a kerb's step, a post, another car
                }
            }
            if (!level.getBlockState(pos.set(col[0], base - 1, col[1])).getCollisionShape(level, pos).isEmpty()) {
                floor++;
            }
        }
        if (floor < next.size() * 0.6) {
            return false;           // off the edge: a ditch, a drop, the water
        }
        Cars.clearHull(level, car);
        car.setPos(x, car.getY(), z);
        Cars.layHull(level, car);
        return true;
    }
}
