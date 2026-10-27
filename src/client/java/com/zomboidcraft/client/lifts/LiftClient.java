package com.zomboidcraft.client.lifts;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.world.lifts.LiftCar;
import com.zomboidcraft.world.lifts.LiftPhysics;
import com.zomboidcraft.world.lifts.LiftShaft;
import com.zomboidcraft.world.lifts.Lifts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The lifts on your side: each car run on smoothly between the server's updates (before anything
 * else moves each tick, so you ride in step), drawn by {@link LiftRenderer}; the interact key on a
 * landing's call button calls the car, and looking at the floor panel inside a car opens it ({@link LiftScreen}).
 */
public final class LiftClient {
    public static void register() {
        EntityRendererRegistry.register(Lifts.CAR, LiftRenderer::new);
        ClientTickEvents.START_CLIENT_TICK.register(LiftClient::start);
        ClientTickEvents.END_CLIENT_TICK.register(LiftClient::end);
        // the usable dot on call buttons, and on the floor panel inside a car
        Interactables.blocks(state -> state.is(Lifts.BUTTON));
        Interactables.finder((client, player) -> {
            LiftCar car = LiftPhysics.inside(player);
            Vec3 at = car == null ? null : panel(car, player.getEyePosition(), player.getViewVector(1f));
            return at == null ? null : new Interactables.Spot(at, 0f);
        });
    }

    /**
     * Where a look from the eye meets the car's floor panel (the right-hand wall by the doors, as
     * {@link LiftRenderer} draws it), if it does within reach; null otherwise.
     */
    static Vec3 panel(LiftCar car, Vec3 eye, Vec3 look) {
        LiftShaft s = car.shaft();
        if (s == null) {
            return null;
        }
        double rx = s.r.getStepX(), rz = s.r.getStepZ(), bx = s.b.getStepX(), bz = s.b.getStepZ();
        double eu = (eye.x - s.ox) * rx + (eye.z - s.oz) * rz, lu = look.x * rx + look.z * rz;
        double pu = s.w - 0.11;
        if (lu <= 1e-4) {
            return null;
        }
        double t = (pu - eu) / lu;
        if (t < 0 || t > Interactables.REACH) {
            return null;
        }
        Vec3 hit = eye.add(look.scale(t));
        double v = (hit.x - s.ox) * bx + (hit.z - s.oz) * bz, y = hit.y - car.carY();
        return v > 0.25 && v < 0.75 && y > 0.8 && y < 1.8 ? hit : null;
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
        if (car != null && panel(car, client.player.getEyePosition(), client.player.getViewVector(1f)) != null) {
            client.gui.setScreen(new LiftScreen(car));
            return true;
        }
        return false;
    }

    private LiftClient() {
    }
}
