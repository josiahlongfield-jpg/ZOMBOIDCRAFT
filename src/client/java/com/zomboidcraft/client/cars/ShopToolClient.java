package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.ShopTool;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** The shop tools' working spots (the jack's handle, the crane's push bar, the machines' fronts), lit green close up. */
public final class ShopToolClient {
    private static final double REACH = 2.6, CONE = 0.94;

    public static void register() {
        com.zomboidcraft.client.interact.Interactables.finder((client, player) -> {
            ShopTool t = spot(client, player);
            return t == null ? null : new com.zomboidcraft.client.interact.Interactables.Spot(t.handle(),
                    com.zomboidcraft.client.interact.Interactables.SIZE);
        });
    }

    private static ShopTool spot(Minecraft client, LocalPlayer player) {
        if (client.level == null || player.isPassenger() || client.gui.screen() != null) {
            return null;
        }
        Vec3 eye = player.getEyePosition(), look = player.getViewVector(1f);
        ShopTool best = null;
        double bestCos = CONE;
        for (ShopTool t : client.level.getEntitiesOfClass(ShopTool.class, player.getBoundingBox().inflate(4))) {
            Vec3 to = t.handle().subtract(eye);
            double d = to.length();
            double cos = d < 1e-3 ? 1 : to.dot(look) / d;
            if (d < REACH && cos > bestCos) {
                bestCos = cos;
                best = t;
            }
        }
        return best;
    }

    /** The interact key, pressed: works the tool you're looking at. Returns whether it did. */
    public static boolean interact(Minecraft client, LocalPlayer player) {
        ShopTool t = spot(client, player);
        if (t == null) {
            return false;
        }
        ClientPlayNetworking.send(new ShopTool.Press(t.getId()));
        return true;
    }

    private ShopToolClient() {
    }
}
