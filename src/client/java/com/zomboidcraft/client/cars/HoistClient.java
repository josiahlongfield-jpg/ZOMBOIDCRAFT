package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarHoist;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * The car lift's two working spots, lit green when you're close and looking at one: the control box (the
 * interact key takes the car up a notch, or swings the arms in; sneaking takes it down, or swings them out)
 * and the power unit's lever (a stroke of the hand pump; sneaking opens the valve to let it down).
 */
public final class HoistClient {
    private static final double REACH = 2.4, CONE = 0.97;

    private record Spot(CarHoist hoist, boolean lever, Vec3 at) {
    }

    public static void register() {
        com.zomboidcraft.client.interact.Interactables.finder((client, player) -> {
            Spot s = spot(client, player);
            return s == null ? null : new com.zomboidcraft.client.interact.Interactables.Spot(s.at,
                    com.zomboidcraft.client.interact.Interactables.SIZE);
        });        // held G: both ways the box or the lever works, rather than Shift for down
        com.zomboidcraft.client.interact.InteractMenu.provider((client, player, hit, out) -> {
            Spot s = spot(client, player);
            if (s == null) return;
            CarHoist.Use up = s.lever ? CarHoist.Use.PUMP : CarHoist.Use.CONTROL, down = s.lever ? CarHoist.Use.VALVE : CarHoist.Use.CONTROL_DOWN;
            out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of(s.lever ? "Pump it up" : "Raise the lift",
                    () -> ClientPlayNetworking.send(new CarHoist.Press(s.hoist.getId(), up.ordinal()))));
            out.add(com.zomboidcraft.client.interact.InteractMenu.Option.of(s.lever ? "Open the valve" : "Lower the lift",
                    () -> ClientPlayNetworking.send(new CarHoist.Press(s.hoist.getId(), down.ordinal()))));
        });
    }

    private static Spot spot(Minecraft client, LocalPlayer player) {
        if (client.level == null || player.isPassenger() || client.gui.screen() != null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        Spot best = null;
        double bestCos = CONE;
        for (CarHoist h : client.level.getEntitiesOfClass(CarHoist.class, player.getBoundingBox().inflate(5))) {
            for (int i = 0; i < 2; i++) {
                Vec3 at = h.toWorld(i == 0 ? CarHoist.CONTROL.add(0.07, 0, 0) : CarHoist.LEVER.add(0.02, 0, 0.06));
                Vec3 to = at.subtract(eye);
                double d = to.length();
                double cos = d < 1e-3 ? 1 : to.dot(look) / d;
                if (d < REACH && cos > bestCos) {
                    bestCos = cos;
                    best = new Spot(h, i == 1, at);
                }
            }
        }
        return best;
    }

    /** The interact key, pressed: works the lift you're looking at. Returns whether it did. */
    public static boolean interact(Minecraft client, LocalPlayer player) {
        Spot s = spot(client, player);
        if (s == null) {
            return false;
        }
        boolean down = player.isShiftKeyDown();
        CarHoist.Use use = s.lever ? (down ? CarHoist.Use.VALVE : CarHoist.Use.PUMP) : (down ? CarHoist.Use.CONTROL_DOWN : CarHoist.Use.CONTROL);
        ClientPlayNetworking.send(new CarHoist.Press(s.hoist.getId(), use.ordinal()));
        return true;
    }

    private HoistClient() {
    }
}
