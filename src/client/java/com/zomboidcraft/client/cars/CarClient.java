package com.zomboidcraft.client.cars;

import com.mojang.blaze3d.platform.InputConstants;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarNet;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Cars;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The player's side of cars. The interact key (G unless rebound in Controls) gets in: standing right at
 * a door and facing its handle lights the handle green and gets you into that door's seat; otherwise it's
 * the nearest free seat, or the boot from the back end. Inside, it moves you to a free seat you look at
 * (lit green), opens the glovebox from the front, or gets you out. At the wheel, C turns the key (or
 * hotwires a car with none), the rail-light key works the headlights, and H sounds the horn. What the key
 * would use is lit with a small green dot ({@link com.zomboidcraft.client.interact.Interactables}), the boot
 * too; the driving keys are handed to the car you drive; running engines are heard; and headlights light the road.
 */
public final class CarClient {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(ZomboidCraft.id("vehicles"));
    public static final KeyMapping INTERACT = new KeyMapping("key.zomboidcraft.interact", InputConstants.KEY_G, CATEGORY);
    public static final KeyMapping HORN = new KeyMapping("key.zomboidcraft.horn", InputConstants.KEY_H, CATEGORY);
    public static final KeyMapping ENGINE = new KeyMapping("key.zomboidcraft.engine", InputConstants.KEY_C, CATEGORY);

    /** How squarely you have to face a door handle (degrees off), and how close you have to stand. */
    private static final double DOOR_CONE = Math.cos(Math.toRadians(26)), DOOR_REACH = 2.1;
    /** How squarely you have to look at a seat or the glovebox from inside. */
    private static final double SEAT_CONE = Math.cos(Math.toRadians(13)), GLOVE_CONE = Math.cos(Math.toRadians(14));

    /**
     * What the interact key does to a car right now, lit green on it: a door's seat from outside, or from inside
     * another seat or the glovebox. at is the point lit, in the car's model space; seat is -1 for the glovebox.
     */
    public record Target(CarProp car, int seat, float[] at, float size, String label) {
    }

    private static Target target;

    public static void register() {
        KeyMappingHelper.registerKeyMapping(INTERACT);
        KeyMappingHelper.registerKeyMapping(HORN);
        KeyMappingHelper.registerKeyMapping(ENGINE);
        CarProp.engineSound = CarEngineSound::keep;
        // a car you drive moves on your side, from your movement keys
        CarProp.clientInput = player -> player instanceof LocalPlayer local ? local.input.keyPresses : Input.EMPTY;
        ClientTickEvents.END_CLIENT_TICK.register(CarClient::tick);
        com.zomboidcraft.client.interact.Interactables.drawsOwn(() -> target != null);
        HoistClient.register();
        com.zomboidcraft.client.interact.Interactables.finder(CarClient::boot);
        com.zomboidcraft.client.interact.Interactables.finder(CarClient::bonnet);
        com.zomboidcraft.client.interact.InteractMenu.provider(CarClient::menu);
        CarLights.register();
    }

    /** What's lit green on this car for the local player, or null. */
    public static Target target(CarProp car) {
        return target != null && target.car() == car ? target : null;
    }

    /** Whether the local player sits at the wheel of a car (the rail-light key works its headlights then). */
    public static boolean driving(LocalPlayer player) {
        return player.getVehicle() instanceof CarProp car && car.seatOf(player) == 0;
    }

    /** The headlights switched from the wheel (the rail-light key's other job). */
    public static void headlights() {
        ClientPlayNetworking.send(new CarNet.Use(CarNet.LIGHTS, 0, 0, 0));
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            com.zomboidcraft.world.cars.CarShelter.update(null, null);
            target = null;
            return;
        }
        CarProp inside = player.getVehicle() instanceof CarProp c ? c : null;
        com.zomboidcraft.world.cars.CarShelter.update(client.level, player.position());
        target = client.gui.screen() != null ? null : inside != null ? insideTarget(player, inside) : doorTarget(client, player);
        HitResult hit = client.hitResult;
        CarProp car = inside != null || target != null ? null : lookedAt(client, hit);
        // G: a tap does the obvious thing; held, it opens Zomboid's menu of everything you can do here
        boolean pressed = false;
        while (INTERACT.consumeClick()) pressed = true;
        if (pressed && held < 0) held = 0;
        if (held >= 0) {
            if (INTERACT.isDown() && client.gui.screen() == null) {
                if (++held == HOLD) com.zomboidcraft.client.interact.InteractMenu.open(client);
            } else {
                if (held < HOLD && client.gui.screen() == null) interact(client, player, inside, car, hit);
                held = -1;
            }
        }
        while (HORN.consumeClick()) {
            if (inside != null && inside.aircraft() && inside.seatOf(player) == 0) {
                FlightClient.levelLock(inside);     // in a helicopter the same key holds height and hover
            } else if (inside != null) {
                ClientPlayNetworking.send(new CarNet.Use(CarNet.HORN, 0, 0, 0));
            }
        }
        while (ENGINE.consumeClick()) {
            if (inside != null && inside.seatOf(player) == 0) {
                ClientPlayNetworking.send(new CarNet.Use(CarNet.ENGINE, 0, 0, 0));
            }
        }
        // a hand on the wheel: a long gun comes down off the shoulder once the car's moving
        if (player.isUsingItem() && !CarProp.mayUse(player, player.getUseItem()) && client.gameMode != null) {
            client.gameMode.releaseUsingItem(player);
        }
    }

    /** How long G is held (ticks) before the menu opens instead of the tap's action; -1 when it isn't. */
    private static final int HOLD = 6;
    private static int held = -1;

    /** A tap of G: the one obvious thing for what you're looking at, as before the menu. */
    private static void interact(Minecraft client, LocalPlayer player, CarProp inside, CarProp car, HitResult hit) {
        if (target != null) {
            ClientPlayNetworking.send(new CarNet.Seat(target.car().getId(), target.seat()));
        } else if (inside != null) {
            ClientPlayNetworking.send(new CarNet.Use(CarNet.GET_OUT, 0, 0, 0));
        } else if (HoistClient.interact(client, player)) {
            // a car lift's control box or its pump lever
        } else if (car != null && atBonnet(car, hit, player)) {
            client.gui.setScreen(new com.zomboidcraft.client.kit.MechanicsScreen(car));
        } else if (car != null) {
            Vec3 at = hit.getLocation().subtract(car.position());
            ClientPlayNetworking.send(new CarNet.Use(car.getId(), (float) at.x, (float) at.y, (float) at.z));
        } else if (com.zomboidcraft.client.lifts.LiftClient.interact(client, hit)) {
            // a lift's call button, or the floor panel inside a car
        } else if (com.zomboidcraft.client.trains.TrainClient.interact(client, hit)) {
            // a station's call button: the train's on its way
        } else if (com.zomboidcraft.client.survival.DownedClient.interact(client, hit)) {
            // a downed friend: revive them, or lift them onto a stretcher
        } else if (com.zomboidcraft.client.loot.TowerStashClient.interact(client, hit)) {
            // the water tower: the key under the pump, the padlocked ladder
        } else if (com.zomboidcraft.client.npc.NpcClient.interact(client, hit)) {
            // a companion: follow me, or wait here
        } else if (com.zomboidcraft.client.kit.FilesClient.interact(client, hit)) {
            // a file lying in the world: read it
        } else if (com.zomboidcraft.client.ferry.FerryClient.interact(client, hit)) {
            // a landing's bell: the ferry's coming in
        } else if (com.zomboidcraft.client.lights.LightClient.interact(client, hit)) {
            // a room's light switch
        } else if (com.zomboidcraft.client.survival.SleepClient.interact(client, hit)) {
            // a bed, sofa or mattress (or the floor, crouching): lie down and sleep
        } else if (player.getMainHandItem().is(com.zomboidcraft.ModItems.CAR_KEY)) {
            // not at a car, key in hand: its panic button
            ClientPlayNetworking.send(new CarNet.Use(CarNet.FOB, 0, 0, 0));
        }
    }

    /** Held G's menu, for cars: the seat you're at, the boot, the mechanics, getting out and moving over from inside. */
    private static void menu(Minecraft client, LocalPlayer player, HitResult hit, java.util.List<com.zomboidcraft.client.interact.InteractMenu.Option> out) {
        CarProp inside = player.getVehicle() instanceof CarProp c ? c : null;
        if (inside != null) {
            CarModels.Model m = CarModels.get(inside.model());
            int mine = inside.seatOf(player);
            java.util.List<com.zomboidcraft.client.interact.InteractMenu.Option> seats = new java.util.ArrayList<>();
            if (m != null && m.seats != null) {
                for (int s = 0; s < m.seats.length; s++) {
                    if (s == mine) continue;
                    int seat = s;
                    String name = seatName(m, s);
                    seats.add(inside.seatTaken(s) ? com.zomboidcraft.client.interact.InteractMenu.Option.no(name, "Someone's in it")
                            : com.zomboidcraft.client.interact.InteractMenu.Option.of(name,
                            () -> ClientPlayNetworking.send(new CarNet.Seat(inside.getId(), seat))));
                }
            }
            if (!seats.isEmpty()) out.add(com.zomboidcraft.client.interact.InteractMenu.Option.menu("Switch seat", seats));
            if (m != null && m.glovebox != null && mine >= 0 && mine <= 1) {
                out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Open glovebox",
                        () -> ClientPlayNetworking.send(new CarNet.Seat(inside.getId(), CarNet.Seat.GLOVEBOX))));
            }
            if (mine == 0) {
                out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Start or stop the engine",
                        () -> ClientPlayNetworking.send(new CarNet.Use(CarNet.ENGINE, 0, 0, 0))));
                out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Headlights", CarClient::headlights));
                out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Horn",
                        () -> ClientPlayNetworking.send(new CarNet.Use(CarNet.HORN, 0, 0, 0))));
            }
            out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Get out",
                    () -> ClientPlayNetworking.send(new CarNet.Use(CarNet.GET_OUT, 0, 0, 0))));
            return;
        }
        Target t = doorTarget(client, player);
        CarProp car = t != null ? t.car() : lookedAt(client, hit);
        if (car == null) return;
        if (hit.getLocation().distanceTo(player.getEyePosition()) > 4) return;
        if (t != null) {
            Target door = t;
            out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of(door.label(),
                    () -> ClientPlayNetworking.send(new CarNet.Seat(door.car().getId(), door.seat()))));
        }
        Vec3 at = hit.getLocation().subtract(car.position());
        if (car.atBoot(at)) {
            out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Open the trunk",
                    () -> ClientPlayNetworking.send(new CarNet.Use(car.getId(), (float) at.x, (float) at.y, (float) at.z))));
        } else if (t == null) {
            out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of("Search it",
                    () -> ClientPlayNetworking.send(new CarNet.Use(car.getId(), (float) at.x, (float) at.y, (float) at.z))));
        }
        out.add(car.parts().isEmpty()
                ? com.zomboidcraft.client.interact.InteractMenu.Option.no("Vehicle mechanics", "Nothing on this one to work on")
                : com.zomboidcraft.client.interact.InteractMenu.Option.of("Vehicle mechanics",
                () -> client.gui.setScreen(new com.zomboidcraft.client.kit.MechanicsScreen(car))));
    }

    /**
     * The boot's dot: you're at the back of a car, close, looking at it, so the interact key would search it.
     * At the middle of the back, a little under the top of the body.
     */
    private static com.zomboidcraft.client.interact.Interactables.Spot boot(Minecraft client, LocalPlayer player) {
        if (target != null || player.isPassenger()) {
            return null;
        }
        HitResult hit = client.hitResult;
        CarProp car = lookedAt(client, hit);
        CarModels.Model m = car == null ? null : CarModels.get(car.model());
        if (m == null || m.jet() || hit.getLocation().distanceTo(player.getEyePosition()) > DOOR_REACH
                || !car.atBoot(hit.getLocation().subtract(car.position()))) {
            return null;
        }
        float z = -m.size[2] / 2;
        float y = 0.8f * m.heightAt(0, z + 0.3f);
        return new com.zomboidcraft.client.interact.Interactables.Spot(car.toWorld(new float[]{0, y, z}),
                com.zomboidcraft.client.interact.Interactables.SIZE);
    }

    /** Whether the interact key at this car, looked at here, opens its mechanics screen: at the front end of a car with parts. */
    private static boolean atBonnet(CarProp car, HitResult hit, LocalPlayer player) {
        return !car.parts().isEmpty() && hit.getLocation().distanceTo(player.getEyePosition()) <= DOOR_REACH
                && car.atBonnet(hit.getLocation().subtract(car.position()));
    }

    /** The bonnet's dot: you're at the front of a car with parts, close, looking at it; the interact key opens the mechanics screen. */
    private static com.zomboidcraft.client.interact.Interactables.Spot bonnet(Minecraft client, LocalPlayer player) {
        if (target != null || player.isPassenger()) {
            return null;
        }
        HitResult hit = client.hitResult;
        CarProp car = lookedAt(client, hit);
        CarModels.Model m = car == null ? null : CarModels.get(car.model());
        if (m == null || !atBonnet(car, hit, player)) {
            return null;
        }
        float z = m.size[2] / 2 - 0.5f;
        float y = m.heightAt(0, z) + 0.05f;
        return new com.zomboidcraft.client.interact.Interactables.Spot(car.toWorld(new float[]{0, y, z}),
                com.zomboidcraft.client.interact.Interactables.SIZE);
    }

    /**
     * The door you're standing right at and facing the handle of, and the free seat it lets you into: only
     * close up, on that side, level with that door, looking at it; running past a car lights nothing.
     */
    private static Target doorTarget(Minecraft client, LocalPlayer player) {
        if (player.isSpectator()) {
            return null;
        }
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        Target best = null;
        double bestCos = DOOR_CONE;
        for (CarProp car : client.level.getEntitiesOfClass(CarProp.class, player.getBoundingBox().inflate(5))) {
            CarModels.Model m = CarModels.get(car.model());
            if (m == null || m.handles == null || m.seats == null || "burnt".equals(car.variant())) {
                continue;
            }
            Vec3 at = car.toLocal(player.position());
            for (int s = 0; s < Math.min(m.handles.length, m.seats.length); s++) {
                float[] h = m.handles[s];
                if (h == null || car.seatTaken(s)) {
                    continue;
                }
                // outside the car on the handle's side, within arm's reach of the door, level with it; a handle
                // on the middle line (the Warthog's gun, lit on its grips) is climbed to from behind the car
                boolean back = Math.abs(h[0]) < 0.05f;
                double out = back ? -m.size[2] / 2 - at.z : Math.signum(h[0]) * at.x - Math.abs(h[0]);
                if (out < -0.15 || out > (back ? 1.6 : 1.3) || (back ? Math.abs(at.x) > 1.3 : Math.abs(at.z - h[2]) > 1.1)) {
                    continue;
                }
                Vec3 to = car.toWorld(h).subtract(eye);
                double d = to.length();
                double cos = d < 1e-3 ? 1 : to.dot(look) / d;
                if (d < (back ? 3.2 : DOOR_REACH) && cos > bestCos) {
                    bestCos = cos;
                    best = new Target(car, s, h, com.zomboidcraft.client.interact.Interactables.SIZE, seatName(m, s));
                }
            }
        }
        return best;
    }

    /** From inside: a free seat you look at to move over to, or the glovebox in front of a front seat. */
    private static Target insideTarget(LocalPlayer player, CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        if (m == null || m.seats == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        int mine = car.seatOf(player);
        Target best = null;
        double bestCos = SEAT_CONE;
        for (int s = 0; s < m.seats.length; s++) {
            if (s == mine || car.seatTaken(s)) {
                continue;
            }
            // the middle of the seat's backrest, a little below where its occupant's head would be
            float[] p = {m.seats[s][0], m.seats[s][1] - 0.3f, m.seats[s][2] - 0.1f};
            Vec3 to = car.toWorld(p).subtract(eye);
            double d = to.length(), cos = to.dot(look) / Math.max(1e-3, d);
            if (d < 3.5 && cos > bestCos) {
                bestCos = cos;
                best = new Target(car, s, p, 0.045f, s == 0 ? "Take the wheel" : "Move to the " + seatName(m, s).toLowerCase());
            }
        }
        if (m.glovebox != null && mine >= 0 && mine <= 1) {
            Vec3 to = car.toWorld(m.glovebox).subtract(eye);
            double d = to.length(), cos = to.dot(look) / Math.max(1e-3, d);
            if (d < 1.6 && cos > Math.max(bestCos, GLOVE_CONE)) {
                best = new Target(car, CarNet.Seat.GLOVEBOX, m.glovebox, com.zomboidcraft.client.interact.Interactables.SIZE, "Glovebox");
            }
        }
        return best;
    }

    /** "Drive", "Front seat" or "Back seat". */
    private static String seatName(CarModels.Model m, int s) {
        if (s == 0) {
            return "Drive";
        }
        if (s == m.gunner) {
            return "Gun";
        }
        return m.seats[s][2] < m.seats[0][2] - 0.5f ? "Back seat" : "Front seat";
    }

    /** The car under the crosshair: picked itself, or one of its invisible hull blocks. */
    static CarProp lookedAt(Minecraft client, HitResult hit) {
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
