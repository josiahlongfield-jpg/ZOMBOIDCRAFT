package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarNet;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The player's side of cars: the interact key (G unless rebound in Controls) gets in, searches
 * the boot from the back end, or gets out again; H sounds the horn; a hint shows while you look
 * at one; the driving keys are handed to the car you drive; running engines are heard; and
 * running cars' headlights light the road.
 */
public final class CarClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("vehicles"));
    public static final KeyMapping INTERACT = new KeyMapping("key.zomboidcraft.interact", InputConstants.KEY_G, CATEGORY);
    public static final KeyMapping HORN = new KeyMapping("key.zomboidcraft.horn", InputConstants.KEY_H, CATEGORY);

    private static CarProp hinted;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(INTERACT);
        KeyMappingHelper.registerKeyMapping(HORN);
        CarProp.engineSound = CarEngineSound::keep;
        // a car you drive moves on your side, from your movement keys
        CarProp.clientInput = player -> player instanceof LocalPlayer local ? local.input.keyPresses : Input.EMPTY;
        ClientTickEvents.END_CLIENT_TICK.register(CarClient::tick);
        CarLights.register();
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        boolean inCar = player.getVehicle() instanceof CarProp;
        HitResult hit = client.hitResult;
        CarProp car = inCar ? null : lookedAt(client, hit);
        while (INTERACT.consumeClick()) {
            if (inCar) {
                ClientPlayNetworking.send(new CarNet.Use(CarNet.GET_OUT, 0, 0, 0));
            } else if (car != null) {
                Vec3 at = hit.getLocation().subtract(car.position());
                ClientPlayNetworking.send(new CarNet.Use(car.getId(), (float) at.x, (float) at.y, (float) at.z));
            }
        }
        while (HORN.consumeClick()) {
            if (inCar) {
                ClientPlayNetworking.send(new CarNet.Use(CarNet.HORN, 0, 0, 0));
            }
        }
        if (car != null && (car != hinted || player.tickCount % 20 == 0)) {
            boolean boot = player.isShiftKeyDown() || car.atBoot(hit.getLocation().subtract(car.position()));
            player.sendOverlayMessage(Component.literal("[").append(INTERACT.getTranslatedKeyMessage())
                    .append(boot ? "] Search" : "] Get in"));
        }
        hinted = car;
    }

    /** The car under the crosshair: picked itself, or one of its invisible hull blocks. */
    private static CarProp lookedAt(Minecraft client, HitResult hit) {
        if (hit instanceof EntityHitResult e) {
            return e.getEntity() instanceof CarProp car ? car : null;
        }
        if (hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(b.getBlockPos()).is(Cars.HULL)) {
            Vec3 at = hit.getLocation();
            CarProp best = null;
            for (CarProp car : client.level.getEntitiesOfClass(CarProp.class, new AABB(at, at).inflate(6))) {
                if (best == null || car.distanceToSqr(at) < best.distanceToSqr(at)) {
                    best = car;
                }
            }
            return best;
        }
        return null;
    }

    private CarClient() {
    }
}
