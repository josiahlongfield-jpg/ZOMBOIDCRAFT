package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.vertex.PoseStack;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import com.zomboidcraft.world.events.WorldEvents;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Flybys seen from far off. The server only puts a flyby's aircraft in the world near a player, and the game only
 * draws things inside the render distance, so on their own they popped in overhead and vanished again. Every game
 * is told each flyby's line ({@link WorldEvents.Event}), so here each one is flown along it on this side alone,
 * the whole way: up close where it is, and further off drawn closer in on the line to it, scaled down to look
 * exactly as big as it would (as the far lights are, {@link AirLights}), unless something solid is in the way.
 * The real one the server flies near players is hidden while this one's drawn (it trails a little behind on its
 * way to each game, so the two never quite meet), and its lights come with this one.
 */
public final class FarFlybys {
    /** Further than this it's too small to see anyway. */
    private static final double MAX = 1400;
    /** Further off than this it's drawn this far in front of the camera instead, scaled down to match. */
    private static final double PROXY = 96;

    private static final class Far {
        final WorldEvents.Event event;
        CarProp car;
        /** Drawn this frame, and whether the line to it was clear of anything solid at the last tick. */
        boolean shown, clear = true;

        Far(WorldEvents.Event event) {
            this.event = event;
        }
    }

    private static final List<Far> FLYING = new ArrayList<>();
    private static ClientLevel in;
    /** Ids for the stand-ins, counting down from far below anything the server hands out. */
    private static int nextId = -1_000_000;

    private FarFlybys() {
    }

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FarFlybys::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(FarFlybys::draw);
    }

    /** A flyby has started somewhere (WorldEventSound passes on every event it's told of). */
    public static void add(WorldEvents.Event e) {
        if (e.flyby()) {
            FLYING.add(new Far(e));
        }
    }

    /** The far aircraft drawn this frame, at where they really are, for their lights. */
    static List<CarProp> shown() {
        List<CarProp> out = new ArrayList<>();
        for (Far f : FLYING) {
            if (f.shown && f.car != null) {
                out.add(f.car);
            }
        }
        return out;
    }

    /** Whether one of the far ones is drawn where this real aircraft is (so its lights aren't drawn twice). */
    static boolean covers(CarProp real) {
        for (Far f : FLYING) {
            if (f.shown && f.car != null && f.car != real && f.car.position().distanceToSqr(real.position()) < 40 * 40) {
                return true;
            }
        }
        return false;
    }

    private static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level != in || level == null || level.dimension() != Level.OVERWORLD) {
            FLYING.clear();
            in = level;
            return;
        }
        long now = level.getGameTime();
        if (mc.player == null) {
            return;
        }
        Vec3 eye = mc.player.getEyePosition();
        for (var it = FLYING.iterator(); it.hasNext(); ) {
            Far f = it.next();
            WorldEvents.Event e = f.event;
            if (now - e.start() > e.ticks()) {
                it.remove();
                continue;
            }
            Vec3 at = e.at((now - e.start()) / 20.0);
            double d = at.distanceTo(eye);
            if (d > MAX) {
                f.car = null;
                continue;
            }
            if (f.car == null) {
                f.car = Cars.CAR.create(level, EntitySpawnReason.EVENT);
                if (f.car == null) {
                    continue;
                }
                // never added to the level, so it has no id; the per-aircraft maps (cockpit lights, surfaces...) hash by it
                f.car.setId(nextId--);
                f.car.setLook(e.model(), "usable");
            }
            CarProp car = f.car;
            // keep last tick's attitude to turn smoothly from (an air strike banks and dives)
            org.joml.Quaternionf was = new org.joml.Quaternionf(car.flight().q);
            e.place(car, (now - e.start()) / 20.0);
            car.flight().qO.set(was);
            car.rotorO = car.rotor;
            car.rotor += car.flight().rotorTurns() * Math.PI * 2 * 0.05;
            // past PROXY anything solid in the loaded world between here and there hides it (closer, the depth test does)
            f.clear = d < PROXY || level.clip(new ClipContext(eye, at, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, car)).getType() == HitResult.Type.MISS;
        }
    }

    private static void draw(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        CameraRenderState cam = context.levelState().cameraRenderState;
        for (Far f : FLYING) {
            f.shown = false;
        }
        if (level == null || cam == null || cam.pos == null || FLYING.isEmpty()) {
            return;
        }
        float partial = cam.cameraEntityPartialTicks;
        double time = level.getGameTime() + partial;
        for (Far f : FLYING) {
            CarProp car = f.car;
            if (car == null || !f.clear || !(mc.getEntityRenderDispatcher().getRenderer(car) instanceof CarRenderer renderer)) {
                continue;
            }
            WorldEvents.Event e = f.event;
            Vec3 at = e.at((time - e.start()) / 20.0);
            double d = at.distanceTo(cam.pos);
            if (d > MAX) {
                continue;
            }
            // where it really is this frame, for its lights and how much detail it's drawn with
            car.setPos(at.x, at.y, at.z);
            car.setOldPosAndRot();
            f.shown = true;
            CarRenderState state = renderer.createRenderState();
            renderer.extractRenderState(car, state, partial);
            if (!level.isLoaded(car.blockPosition())) {
                state.lightCoords = 0x00F00000;     // the open sky: the far world isn't loaded to light it
            }
            double k = Math.min(1, PROXY / Math.max(d, 1e-3));
            Vec3 near = cam.pos.add(at.subtract(cam.pos).scale(k));
            PoseStack pose = context.poseStack();
            pose.pushPose();
            pose.translate(near.x - cam.pos.x, near.y - cam.pos.y, near.z - cam.pos.z);
            pose.scale((float) k, (float) k, (float) k);
            renderer.submit(state, pose, context.submitNodeCollector(), cam);
            pose.popPose();
        }
    }
}
