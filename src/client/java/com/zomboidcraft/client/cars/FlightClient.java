package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.client.camera.CameraRig;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.world.cars.AirNet;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The player's side of flying: the mouse and keys turned into {@link Flight.Controls}, the cockpit
 * view (it pitches and rolls with the airframe; hold the peek key to look round it), the chase view in
 * third person, and the pilot's state sent to the server each tick.
 *
 * <p>Jets fly by mouse aim: the mouse moves an aim point and the jet banks and pulls to put its nose
 * on it, like War Thunder's mouse aim; W/S set the throttle, A/D roll by hand, Space brakes. The
 * helicopter's nose follows the mouse sideways; the mouse up and down tilts your head.
 */
public final class FlightClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("aircraft"));
    /** Starts and shuts down the engines (until the cars' engine key, which aircraft will share, lands). */
    public static final KeyMapping ENGINE = new KeyMapping("key.zomboidcraft.air_engine", InputConstants.KEY_C, CATEGORY);

    /** Where the jet's pilot wants the nose (world space, unit length). */
    private static final Vector3f aim = new Vector3f(0, 0, 1);
    /** Where you look in the cockpit, relative to the airframe, degrees: right and up positive. */
    private static float lookYaw, lookPitch;
    /** Mouse turn waiting to be fed to the helicopter's pedals, degrees. */
    private static float turnBuffer;
    private static CarProp riding;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(ENGINE);
        CarProp.flightInput = FlightClient::controls;
        CarProp.pilotReport = state -> ClientPlayNetworking.send(state);
        CarProp.airSound = AirSound::keep;
        CarProp.boarded = FlightClient::boarded;
        CameraRig.register(10, FlightClient::camera);
        ClientTickEvents.END_CLIENT_TICK.register(FlightClient::tick);
        FlightHud.register();
    }

    /** The aircraft the local player sits in, or null. */
    public static CarProp aircraft(Entity player) {
        return player != null && player.getVehicle() instanceof CarProp car && car.aircraft() ? car : null;
    }

    public static boolean pilot(LocalPlayer player, CarProp car) {
        return car != null && car.getControllingPassenger() == player;
    }

    public static Vector3f aim() {
        return aim;
    }

    private static void boarded(CarProp car) {
        Vec3 f = car.flight().forward();
        aim.set((float) f.x, (float) f.y, (float) f.z);
        lookYaw = 0;
        lookPitch = car.helicopter() ? -8 : -4;
        turnBuffer = 0;
    }

    private static boolean freeLook() {
        return Hud.PEEK.isDown();
    }

    /** The mouse, in degrees (Minecraft's: yaw right, pitch down). Returns whether it was used here. */
    public static boolean mouse(LocalPlayer player, double yaw, double pitch) {
        CarProp car = aircraft(player);
        if (car == null) {
            return false;
        }
        Flight f = car.flight();
        boolean pilot = pilot(player, car);
        if (pilot && !freeLook() && f.kind == Flight.Kind.JET) {
            Vec3 up = f.up(), left = f.left();
            Quaternionf turn = new Quaternionf().rotateAxis((float) Math.toRadians(-yaw), (float) up.x, (float) up.y, (float) up.z)
                    .rotateAxis((float) Math.toRadians(pitch), (float) left.x, (float) left.y, (float) left.z);
            turn.transform(aim).normalize();
            clampAim(f, Minecraft.getInstance().options.getCameraType().isFirstPerson() ? 35 : 80);
        } else if (pilot && !freeLook()) {
            turnBuffer += (float) yaw;
            lookPitch = Mth.clamp(lookPitch - (float) pitch, -70, 40);
        } else {
            lookYaw = Mth.clamp(lookYaw + (float) yaw, -150, 150);
            lookPitch = Mth.clamp(lookPitch - (float) pitch, -80, 60);
        }
        return true;
    }

    /** Keeps the aim point within degrees of the nose, so it stays on the screen. */
    private static void clampAim(Flight f, float degrees) {
        Vec3 fw = f.forward();
        Vector3f nose = new Vector3f((float) fw.x, (float) fw.y, (float) fw.z);
        float angle = nose.angle(aim);
        float most = (float) Math.toRadians(degrees);
        if (angle > most) {
            Vector3f axis = nose.cross(aim, new Vector3f());
            if (axis.lengthSquared() < 1e-8f) {
                return;
            }
            axis.normalize();
            aim.set(nose).rotateAxis(most, axis.x, axis.y, axis.z).normalize();
        }
    }

    /** The pilot's controls this tick. */
    private static Flight.Controls controls(CarProp car) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.screen() != null) {
            return Flight.Controls.NONE;
        }
        Input in = player.input.keyPresses;
        Flight f = car.flight();
        f.trainee = !com.zomboidcraft.world.cars.AirReadiness.knows(player, com.zomboidcraft.world.cars.AirReadiness.manualFor(f.kind));
        f.skill = com.zomboidcraft.world.cars.AirReadiness.skill(player);
        float fwd = (in.forward() ? 1 : 0) - (in.backward() ? 1 : 0);
        float side = (in.right() ? 1 : 0) - (in.left() ? 1 : 0);
        if (f.kind == Flight.Kind.HELI) {
            float collective = in.jump() ? 1 : in.sprint() || in.shift() || mc.options.keySprint.isDown() ? -1 : 0;
            float turn = Mth.clamp(turnBuffer, -3, 3);
            turnBuffer -= turn;
            turnBuffer = Mth.clamp(turnBuffer, -45, 45);
            return new Flight.Controls(0, 0, 0, 0, false, fwd, side, collective, turn);
        }
        // mouse aim: pull and bank toward the aim point, rolling wings level once the nose is near it
        Vector3f local = new Quaternionf(f.q).conjugate().transform(new Vector3f(aim));
        float off = (float) Math.toDegrees(Math.acos(Mth.clamp(local.z, -1, 1)));
        float pitch = Mth.clamp(local.y * 4, -1, 1);
        float yaw = Mth.clamp(-local.x * 4, -1, 1);
        float hard = Mth.clamp(-local.x * 4, -1, 1);
        float level = (float) -f.left().y * 2;
        float roll = Mth.lerp(Mth.clamp(off / 10f, 0, 1), Mth.clamp(level, -1, 1), hard);
        if (local.z < 0) {
            pitch = 1;      // the aim's behind: pull round
        }
        if (side != 0) {
            roll = side;    // A/D roll by hand
        }
        return new Flight.Controls(pitch, roll, yaw, fwd, in.jump(), 0, 0, 0, 0);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        CarProp car = aircraft(player);
        riding = car;
        if (car == null) {
            while (ENGINE.consumeClick()) {
                // nothing to start
            }
            return;
        }
        boolean pilot = pilot(player, car);
        while (ENGINE.consumeClick()) {
            if (pilot) {
                ClientPlayNetworking.send(new AirNet.Control(AirNet.ENGINE));
            }
        }
        Flight f = car.flight();
        if (pilot && !freeLook() && f.kind == Flight.Kind.JET) {
            // looking round lets go of the view; it settles back on the nose
            lookYaw *= 0.7f;
            lookPitch += (-4 - lookPitch) * 0.3f;
        }
        if (pilot && f.kind == Flight.Kind.HELI && !freeLook()) {
            lookYaw *= 0.7f;
        }
        // your body faces where the camera does, so the crosshair and anything you use point the right way
        Vector3f dir = view(car, 1).transform(new Vector3f(0, 0, 1));
        float yRot = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        float xRot = (float) Math.toDegrees(-Math.asin(Mth.clamp(dir.y, -1, 1)));
        player.setYRot(yRot);
        player.setXRot(Mth.clamp(xRot, -90, 90));
        player.yRotO = yRot;
        player.xRotO = player.getXRot();
        player.setYHeadRot(yRot);
        player.yHeadRotO = yRot;
    }

    /** The cockpit view's turn in the airframe's own frame (nose +z), as of partialTick. */
    private static Quaternionf view(CarProp car, float partialTick) {
        Flight f = car.flight();
        Quaternionf q = f.qO.slerp(f.q, partialTick, new Quaternionf());
        return q.rotateY((float) Math.toRadians(-lookYaw)).rotateX((float) Math.toRadians(-lookPitch));
    }

    /** The aircraft camera: the cockpit view in first person, a chase camera behind it otherwise. */
    private static boolean camera(Camera camera, Entity entity, float partialTick, CameraRig.Out out) {
        CarProp car = aircraft(entity);
        if (car == null) {
            return false;
        }
        Quaternionf body = view(car, partialTick);
        if (!camera.isDetached()) {
            CameraRig.fromBody(body, out.rotation);
            return true;
        }
        Flight f = car.flight();
        Vector3f dir;
        if (f.kind == Flight.Kind.JET && pilot(Minecraft.getInstance().player, car) && !freeLook()) {
            dir = new Vector3f(aim);
        } else {
            // the chase camera follows the heading, never the roll
            float yaw = f.kind == Flight.Kind.HELI ? f.heading : car.getYRot(partialTick);
            dir = new Quaternionf().rotationY((float) Math.toRadians(-yaw - lookYaw)).rotateX((float) Math.toRadians(-lookPitch))
                    .transform(new Vector3f(0, 0, 1));
        }
        float yaw = (float) Math.atan2(-dir.x, dir.z), pitch = (float) -Math.asin(Mth.clamp(dir.y, -1, 1));
        out.rotation.rotationYXZ((float) Math.PI - yaw, -pitch, 0);
        float dist = f.kind == Flight.Kind.JET ? 26 : 22;
        Vec3 centre = car.getPosition(partialTick).add(0, car.pivot(), 0);
        out.position = centre.add(-dir.x * dist, -dir.y * dist + 3.5, -dir.z * dist);
        return true;
    }

    private FlightClient() {
    }
}
