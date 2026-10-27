package com.zomboidcraft.client.lifts;

import com.zomboidcraft.world.lifts.LiftCar;
import com.zomboidcraft.world.lifts.LiftPhysics;
import com.zomboidcraft.world.lifts.Lifts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * The lifts on your side: each car run on smoothly between the server's updates (before anything
 * else moves each tick, so you ride in step), drawn by {@link LiftRenderer}; the interact key on a
 * landing's call button calls the car, and inside a car opens its floor panel ({@link LiftScreen}).
 */
public final class LiftClient {
    public static void register() {
        EntityRendererRegistry.register(Lifts.CAR, LiftRenderer::new);
        ClientTickEvents.START_CLIENT_TICK.register(LiftClient::start);
        ClientTickEvents.END_CLIENT_TICK.register(LiftClient::end);
    }

    /** Before the world ticks: every car runs on, so whoever rides it moves with it this same tick. */
    private static void start(Minecraft client) {
        if (client.level == null || client.isPaused()) {
            return;
        }
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof LiftCar car) {
                car.clientAdvance();
            }
        }
    }

    /** Stepped out of the car with its panel up: put the panel away. */
    private static void end(Minecraft client) {
        if (client.player != null && client.gui.screen() instanceof LiftScreen screen && !screen.stillInside(client.player)) {
            screen.onClose();
        }
    }

    /** The interact key pressed: on a lift's call button, call it; inside a car, its floor panel. Returns whether it was either. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(block.getBlockPos()).is(Lifts.BUTTON)) {
            ClientPlayNetworking.send(new Lifts.Call(block.getBlockPos()));
            return true;
        }
        LiftCar car = client.player == null ? null : LiftPhysics.inside(client.player);
        if (car != null && car.shaft() != null) {
            client.gui.setScreen(new LiftScreen(car));
            return true;
        }
        return false;
    }

    private LiftClient() {
    }
}
