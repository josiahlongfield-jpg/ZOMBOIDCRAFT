package com.zomboidcraft.client.survival;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.survival.Downs;
import com.zomboidcraft.survival.Treatment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** The interact key on a friend who's on their feet: see to their worst wound with the bandage or splint you carry. */
public final class TreatClient {
    private static final double REACH = 3.0;

    public static void register() {
        Interactables.finder(TreatClient::dot);
    }

    public static boolean interact(Minecraft client, HitResult hit) {
        Player target = lookedAt(client);
        if (target == null) return false;
        ClientPlayNetworking.send(new Treatment.Other(target.getId()));
        return true;
    }

    private static Player lookedAt(Minecraft client) {
        if (client.player == null || !(client.hitResult instanceof EntityHitResult e) || !(e.getEntity() instanceof Player p)
                || p == client.player || Downs.downed(p) || p.isSpectator()) {
            return null;
        }
        return e.getLocation().distanceTo(client.player.getEyePosition()) <= REACH ? p : null;
    }

    private static Interactables.Spot dot(Minecraft client, LocalPlayer player) {
        Player target = lookedAt(client);
        return target == null ? null : new Interactables.Spot(target.position().add(0, 1.0, 0), Interactables.SIZE);
    }

    private TreatClient() {
    }
}
