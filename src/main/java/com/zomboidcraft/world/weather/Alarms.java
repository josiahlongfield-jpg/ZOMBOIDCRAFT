package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Storms set the city's alarms off. Thunder and gusts trip a car alarm down the street (its horn
 * whooping, its lamps flashing) or a shop's alarm bell, and they ring on for half a minute or
 * so: everything dead within earshot comes to see. Getting into the car shuts it up.
 */
public final class Alarms {
    private static final SplittableRandom RNG = new SplittableRandom();
    private static final int NEAR = 10, FAR = 48, SHOP_REACH = 32;
    private static final List<Bell> BELLS = new ArrayList<>();
    private static ServerLevel bellsIn;

    /** A shop alarm ringing at pos, ticks left. */
    private static final class Bell {
        final BlockPos pos;
        int left;

        Bell(BlockPos pos, int left) {
            this.pos = pos;
            this.left = left;
        }
    }

    /** Once a second: maybe a storm sets an alarm off near someone. */
    static void second(ServerLevel level, Weather w) {
        if (!ZcConfig.get().weather.stormAlarms) {
            return;
        }
        // thunder does it most; a hard gale now and then
        double chance = w.thunder() > 0.5f ? 1 / 110.0 : w.wind() > 0.7f ? 1 / 260.0 : 0;
        if (chance == 0) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || RNG.nextDouble() >= chance) {
                continue;
            }
            if (RNG.nextDouble() < 0.6 ? !car(level, player.position()) && !shop(level, player.blockPosition())
                    : !shop(level, player.blockPosition())) {
                car(level, player.position());
            }
        }
    }

    private static boolean car(ServerLevel level, Vec3 near) {
        List<CarProp> cars = new ArrayList<>(level.getEntitiesOfClass(CarProp.class, new AABB(near, near).inflate(FAR),
                car -> car.position().distanceTo(near) >= NEAR));
        Collections.shuffle(cars);
        for (CarProp car : cars) {
            if (car.tripAlarm(500 + RNG.nextInt(500))) {
                return true;
            }
        }
        return false;
    }

    /** A shop's alarm bell: rung from behind one of the shop signs round you. */
    private static boolean shop(ServerLevel level, BlockPos center) {
        List<BlockPos> signs = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = -SHOP_REACH; x <= SHOP_REACH; x += 1) {
            for (int z = -SHOP_REACH; z <= SHOP_REACH; z += 1) {
                if (x * x + z * z < NEAR * NEAR) {
                    continue;
                }
                for (int y = -6; y <= 12; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (!level.isLoaded(pos)) {
                        break;
                    }
                    String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
                    if (path.startsWith("blade_") || path.startsWith("neon_open")) {
                        signs.add(pos.immutable());
                    }
                }
            }
        }
        if (signs.isEmpty()) {
            return false;
        }
        if (bellsIn != level) {
            BELLS.clear();
            bellsIn = level;
        }
        BELLS.add(new Bell(signs.get(RNG.nextInt(signs.size())), 500 + RNG.nextInt(500)));
        return true;
    }

    /** Every tick: the bells ring on. */
    static void tick(ServerLevel level) {
        if (bellsIn != level || BELLS.isEmpty()) {
            return;
        }
        BELLS.removeIf(bell -> {
            if (--bell.left <= 0) {
                return true;
            }
            // an electric bell's hammer: the struck bell over and over, high and hard
            if (bell.left % 4 == 0) {
                level.playSound(null, bell.pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2f, 1.7f + RNG.nextFloat() * 0.1f);
            }
            if (bell.left % 40 == 0) {
                Noise.emit(level, Vec3.atCenterOf(bell.pos), 56);
            }
            return false;
        });
    }

    private Alarms() {
    }
}
