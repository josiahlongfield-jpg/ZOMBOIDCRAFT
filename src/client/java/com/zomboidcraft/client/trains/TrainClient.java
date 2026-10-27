package com.zomboidcraft.client.trains;

import com.zomboidcraft.world.trains.Line;
import com.zomboidcraft.world.trains.TrainCar;
import com.zomboidcraft.world.trains.TrainEntity;
import com.zomboidcraft.world.trains.TrainNet;
import com.zomboidcraft.world.trains.TrainRider;
import com.zomboidcraft.world.trains.Trains;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The trains on your side: the lines and their rails, running each train smoothly between the
 * server's updates (before anything else moves each tick, so you ride in step), telling the server
 * where you stand in your car, placing everyone else aboard where they stand in theirs, the rolling
 * sound, and the call buttons (the interact key).
 */
public final class TrainClient {
    /** Where the server last said each rider (by entity id) stands: train id, car, x, y, z, yaw, and the tick it said so. */
    private static final Map<Integer, float[]> RIDERS = new HashMap<>();
    private static boolean wasRiding;
    private static long tick;

    public static void register() {
        EntityRendererRegistry.register(Trains.TRAIN, TrainRenderer::new);
        TrackRenderer.register();
        ClientPlayNetworking.registerGlobalReceiver(TrainNet.Lines.TYPE, (payload, context) -> context.client().execute(() -> Trains.received(payload)));
        ClientPlayNetworking.registerGlobalReceiver(TrainNet.Riders.TYPE, (payload, context) -> context.client().execute(() -> riders(payload)));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            Trains.lines(true).clear();
            RIDERS.clear();
        }));
        ClientTickEvents.START_CLIENT_TICK.register(TrainClient::start);
        ClientTickEvents.END_CLIENT_TICK.register(TrainClient::end);
    }

    /** Before the world ticks: every train runs on, so whoever rides it moves with it this same tick. */
    private static void start(Minecraft client) {
        if (client.level == null || client.isPaused()) {
            return;
        }
        tick++;
        for (Entity e : client.level.entitiesForRendering()) {
            if (e instanceof TrainEntity train) {
                train.clientAdvance();
                TrainSound.keep(train);
            }
        }
    }

    /** After the world ticks: tell the server where we stand, and put other riders where they stand. */
    private static void end(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            return;
        }
        TrainRider me = (TrainRider) player;
        TrainEntity train = me.zomboidcraft$train();
        if (train != null && train.line() != null) {
            TrainCar.Pose pose = TrainCar.pose(train.line().route, train.s(), me.zomboidcraft$car());
            Vec3 local = pose.toLocal(player.position());
            ClientPlayNetworking.send(new TrainNet.Ride(train.getId(), me.zomboidcraft$car(), (float) local.x, (float) local.y,
                    (float) local.z, Mth.wrapDegrees(player.getYRot() - pose.yawDegrees())));
            wasRiding = true;
        } else if (wasRiding) {
            ClientPlayNetworking.send(new TrainNet.Ride(-1, 0, 0, 0, 0, 0));
            wasRiding = false;
        }
        placeRiders(client);
        hint(client, player);
    }

    private static void riders(TrainNet.Riders p) {
        RIDERS.entrySet().removeIf(e -> e.getValue()[0] == p.train());
        for (int i = 0; i < p.ids().length; i++) {
            RIDERS.put(p.ids()[i], new float[] {p.train(), p.cars()[i], p.at()[i * 4], p.at()[i * 4 + 1], p.at()[i * 4 + 2],
                    p.at()[i * 4 + 3], tick});
        }
    }

    /** Everyone else aboard stands where the server says they stand in their car, as this game runs that car. */
    private static void placeRiders(Minecraft client) {
        for (var it = RIDERS.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            float[] r = entry.getValue();
            if (tick - (long) r[6] > 10) {
                it.remove();
                continue;
            }
            Entity e = client.level.getEntity(entry.getKey());
            if (e == null || e == client.player || !(client.level.getEntity((int) r[0]) instanceof TrainEntity train) || train.line() == null) {
                continue;
            }
            Line line = train.line();
            int car = (int) r[1];
            if (car < 0 || car >= line.cars) {
                continue;
            }
            TrainCar.Pose pose = TrainCar.pose(line.route, train.s(), car);
            Vec3 at = pose.toWorld(r[2], r[3], r[4]);
            e.setPos(at);
            float yaw = pose.yawDegrees() + r[5];
            e.setYRot(yaw);
            if (e instanceof LivingEntity living) {
                living.yHeadRot = yaw;
                living.yBodyRot = Mth.approachDegrees(living.yBodyRot, yaw, 20);
            }
        }
    }

    /** The interact key pressed: on a call button, call the train. Returns whether it was one. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(block.getBlockPos()).is(Trains.CALL_BUTTON)) {
            ClientPlayNetworking.send(new TrainNet.Call(block.getBlockPos()));
            return true;
        }
        return false;
    }

    private static void hint(Minecraft client, LocalPlayer player) {
        HitResult hit = client.hitResult;
        if (tick % 20 == 0 && client.gui.screen() == null && hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(block.getBlockPos()).is(Trains.CALL_BUTTON)) {
            player.sendOverlayMessage(Component.literal("[").append(com.zomboidcraft.client.cars.CarClient.INTERACT.getTranslatedKeyMessage())
                    .append("] Call the train (it will be heard)"));
        }
    }

    private TrainClient() {
    }
}
