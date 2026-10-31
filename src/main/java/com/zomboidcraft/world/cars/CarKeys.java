package com.zomboidcraft.world.cars;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

/**
 * Car keys, like Zomboid's: each car has one, made for it alone. It may be left in the ignition, in
 * the glovebox, on one of the dead walking about nearby, or lost; without it the car has to be
 * hotwired. A key knows its car by the car's id.
 */
public final class CarKeys {
    /** Where a car's key is. */
    public static final int UNROLLED = 0, IGNITION = 1, GLOVEBOX = 2, ZOMBIE = 3, LOST = 4, TAKEN = 5;
    /** A customer car at an auto shop: its key hangs on the shop's key board until someone opens it. */
    public static final int BOARD = 6;
    /** How far from its board a shop's customer cars may be parked. */
    public static final double BOARD_REACH = 48;

    /** A fresh key to car. */
    public static ItemStack make(CarProp car) {
        ItemStack key = new ItemStack(ModItems.CAR_KEY);
        CompoundTag tag = new CompoundTag();
        tag.putString("car", car.getStringUUID());
        key.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        key.set(DataComponents.ITEM_NAME, Component.literal("Car Key (" + name(car.model()) + ")"));
        return key;
    }

    /** Whether a stack is this car's key. */
    public static boolean fits(ItemStack stack, CarProp car) {
        if (!stack.is(ModItems.CAR_KEY)) {
            return false;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && car.getStringUUID().equals(data.copyTag().getStringOr("car", ""));
    }

    /** Whether someone has this car's key on them. */
    public static boolean carries(Player player, CarProp car) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (fits(player.getInventory().getItem(i), car)) {
                return true;
            }
        }
        return false;
    }

    /** Which of the places a new car's key is in, from the config's shares. */
    static int roll(java.util.Random rng) {
        ZcConfig.World w = ZcConfig.get().world;
        double r = rng.nextDouble();
        if ((r -= w.carKeyIgnition) < 0) {
            return IGNITION;
        }
        if ((r -= w.carKeyGlovebox) < 0) {
            return GLOVEBOX;
        }
        return r - w.carKeyZombie < 0 ? ZOMBIE : LOST;
    }

    /**
     * An auto shop's key board, opened the first time: hangs the key of every customer car parked within
     * {@link #BOARD_REACH} whose key is still on a board, in the first empty slots. Returns how many it hung.
     * The board's block calls this once and remembers it has.
     */
    public static int board(ServerLevel level, net.minecraft.core.BlockPos at, net.minecraft.world.Container into) {
        int hung = 0;
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(at).inflate(BOARD_REACH, 16, BOARD_REACH);
        for (CarProp car : level.getEntitiesOfClass(CarProp.class, around, CarProp::onBoard)) {
            for (int i = 0; i < into.getContainerSize(); i++) {
                if (into.getItem(i).isEmpty()) {
                    into.setItem(i, make(car));
                    car.keyFound();
                    hung++;
                    break;
                }
            }
        }
        return hung;
    }

    /** "police_suv" as "Police Suv". */
    static String name(String model) {
        StringBuilder out = new StringBuilder();
        for (String word : model.split("_")) {
            if (!word.isEmpty()) {
                out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return out.toString();
    }

    /** A zombie that dies near a car whose key went walking may have had it in its pocket. */
    public static void register() {
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof Zombie && entity.level() instanceof ServerLevel server) {
                dropNear(server, entity);
            }
        });
    }

    private static void dropNear(ServerLevel server, LivingEntity dead) {
        CarProp best = null;
        for (CarProp car : server.getEntitiesOfClass(CarProp.class, dead.getBoundingBox().inflate(24))) {
            if (car.keyOnZombie() && (best == null || car.distanceToSqr(dead) < best.distanceToSqr(dead))) {
                best = car;
            }
        }
        if (best != null && dead.getRandom().nextDouble() < ZcConfig.get().world.carKeyDrop) {
            best.keyFound();
            dead.spawnAtLocation(server, make(best));
        }
    }

    private CarKeys() {
    }
}
