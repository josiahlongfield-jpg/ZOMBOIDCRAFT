package com.zomboidcraft.client.cars;

import com.zomboidcraft.client.camera.CameraRig;
import com.zomboidcraft.client.hud.Hud;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
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
    /** Where the jet's pilot wants the nose (world space, unit length). */
    private static final Vector3f aim = new Vector3f(0, 0, 1);
    /** Where you look in the cockpit, relative to the airframe, degrees: right and up positive. */
    private static float lookYaw, lookPitch;
    /** Mouse turn waiting to be fed to the helicopter's pedals, degrees. */
    private static float turnBuffer;
    private static CarProp riding;

    /** How close (eye to seat) and how straight at it you have to be looking for the cockpit's dot. */
    private static final double COCKPIT_REACH = 3.5, COCKPIT_CONE = 0.94;

    public static void register() {
        CarProp.flightInput = FlightClient::controls;
        CarProp.pilotReport = state -> ClientPlayNetworking.send(state);
        CarProp.airSound = AirSound::keep;
        CarProp.boarded = FlightClient::boarded;
        CameraRig.register(10, FlightClient::camera);
        ClientTickEvents.END_CLIENT_TICK.register(FlightClient::tick);
        FlightHud.register();
        FarFlybys.register();     // before the lights, which light the far ones it draws
        AirLights.register();
        AirStrikeFx.register();
        com.zomboidcraft.client.interact.Interactables.finder(FlightClient::cockpitDot);
    }

    /**
     * The cockpit's green dot: standing by an aircraft, close to the seat the interact key would put you in
     * and looking at it (aircraft have no door handles for CarClient's dots). It sits on the cockpit's side
     * toward you, so the canopy doesn't hide it.
     */
    private static com.zomboidcraft.client.interact.Interactables.Spot cockpitDot(Minecraft client, LocalPlayer player) {
        if (player.isPassenger() || player.isSpectator()) {
            return null;
        }
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        for (CarProp car : client.level.getEntitiesOfClass(CarProp.class, player.getBoundingBox().inflate(12), CarProp::aircraft)) {
            // only while the crosshair's on it, so the interact key gets you in (CarClient)
            if ("burnt".equals(car.variant()) || car.isRemoved() || CarClient.lookedAt(client, client.hitResult) != car) {
                continue;
            }
            int seat = car.nearestFreeSeat(player);
            if (seat < 0) {
                continue;
            }
            Vec3 at = car.seatEye(seat), to = at.subtract(eye);
            double d = to.length();
            if (d > COCKPIT_REACH || d > 1e-3 && to.dot(look) / d < COCKPIT_CONE) {
                continue;
            }
            // out to the cockpit's side toward you
            Vec3 back = eye.subtract(at);
            back = new Vec3(back.x, 0, back.z);
            double side = back.length();
            return new com.zomboidcraft.client.interact.Interactables.Spot(
                    side < 1e-3 ? at : at.add(back.scale(Math.min(0.75, side * 0.5) / side)),
                    com.zomboidcraft.client.interact.Interactables.SIZE);
        }
        return null;
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
        Flight.Controls c = input(car);
        AirControls.input(car, c);
        return c;
    }

    private static Flight.Controls input(CarProp car) {
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
        // mouse aim, flown the way a pilot would: roll until the aim point is overhead, then pull to it;
        // close to the nose, fine pitch and rudder put it on the aim with the wings near level
        Vector3f local = new Quaternionf(f.q).conjugate().transform(new Vector3f(aim));
        float off = (float) Math.toDegrees(Math.acos(Mth.clamp(local.z, -1, 1)));
        float bank = f.bank();
        float pitch, roll, yaw;
        if (off < 8) {
            pitch = Mth.clamp(local.y * 14, -1, 1);
            yaw = Mth.clamp(-local.x * 14, -1, 1);
            float wantBank = Mth.clamp(-local.x * 300, -25, 25);
            roll = Mth.clamp((wantBank - bank) / 45, -1, 1);
        } else {
            // the roll that puts the aim straight above the canopy (right positive)
            float over = (float) Math.toDegrees(Math.atan2(-local.x, local.y));
            // (only part of the way for an aim just off the nose, so small turns stay shallow)
            roll = Mth.clamp(over * Math.min(1, (off - 4) / 18) / 45, -1, 1);
            // pull as it comes overhead, harder the further off it is
            float lined = (float) Math.max(0, Math.cos(Math.toRadians(over)));
            pitch = Mth.clamp((0.25f + off / 40f) * lined * lined, 0, 1);
            if (local.y < 0 && Math.abs(over) > 150 && off < 25) {
                pitch = Mth.clamp(local.y * 6, -0.5f, 0);   // a little below: push rather than roll over
                roll = Mth.clamp(-bank / 45, -1, 1);
            }
            yaw = Mth.clamp(-local.x * 3, -1, 1) * 0.5f;
        }
        if (side != 0) {
            roll = side;    // A/D roll by hand
        }
        f.assist = assist ? new Vector3f(aim) : null;
        return new Flight.Controls(pitch, roll, yaw, fwd, in.jump(), 0, 0, 0, 0);
    }

    private static void tick(Minecraft mc) {
        LocalPlayer player = mc.player;
        CarProp car = aircraft(player);
        riding = car;
        if (car == null) {
            return;
        }
        boolean pilot = pilot(player, car);
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
        Flight f = car.flight();
        if (!camera.isDetached()) {
            shake(f, body);
            CameraRig.fromBody(body, out.rotation);
            return true;
        }
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

    /** Whether jets fly with the assist (on until the pilot turns it off). */
    private static boolean assist = true;

    /** The horn key in the pilot's seat: the helicopter's level lock, or a jet's flight assist, on or off. */
    static void levelLock(CarProp car) {
        Flight f = car.flight();
        if (f.kind != Flight.Kind.HELI) {
            assist = !assist;
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(assist
                        ? "Flight assist on: the jet flies where you look and banks itself"
                        : "Flight assist off: full physics, you roll and pull yourself"));
            }
            return;
        }
        f.levelLock = !f.levelLock;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(f.levelLock
                    ? "Level lock on: holds this height, and hovers with the cyclic centred"
                    : "Level lock off"));
        }
    }

    /**
     * The seat's shake, in degrees as felt by the head: the airframe buffeting under hard g or on the edge
     * of a stall, the bumps of the runway at speed, and a helicopter's constant rotor beat.
     */
    private static void shake(Flight f, Quaternionf body) {
        float speed = (float) f.vel.length();
        float shake;
        if (f.kind == Flight.Kind.HELI) {
            shake = f.rpm * (0.06f + Math.min(speed, 60) / 60 * 0.18f);
        } else {
            shake = Math.max(0, f.load - 4.5f) * 0.22f + (f.stalled ? 0.9f : 0) + (f.aoa > Math.toRadians(13) ? 0.4f : 0)
                    + (f.grounded ? Math.min(speed, 80) / 80 * 0.35f : 0) + (f.throttle > 0.85f && f.rpm > 0.9f ? 0.08f : 0);
        }
        if (shake < 0.01f) {
            return;
        }
        double t = System.nanoTime() / 1e9;
        double beat = f.kind == Flight.Kind.HELI ? 6 : 1;      // the rotor's blade pass is a quick, even beat
        float x = (float) (Math.sin(t * 23 * beat / 2) * 0.6 + Math.sin(t * 37.3) * 0.4) * shake;
        float y = (float) (Math.sin(t * 29.7 + 1.1) * 0.6 + Math.sin(t * 17.1 * beat / 2) * 0.4) * shake;
        body.rotateX((float) Math.toRadians(x)).rotateY((float) Math.toRadians(y * 0.6f));
    }

    private FlightClient() {
    }
}
