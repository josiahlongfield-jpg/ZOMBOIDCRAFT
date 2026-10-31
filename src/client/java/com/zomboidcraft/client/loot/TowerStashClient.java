package com.zomboidcraft.client.loot;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.loot.TowerStash;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The interact key on the water tower's pump (feel under it) or its padlocked chain (unlock it, or cut it). */
public final class TowerStashClient {
    private static final double REACH = 3.5;

    public static void register() {
        Interactables.finder(TowerStashClient::dot);
    }

    public static boolean interact(Minecraft client, HitResult hit) {
        BlockPos pos = lookedAt(client);
        if (pos == null) return false;
        ClientPlayNetworking.send(new TowerStash.Use(pos));
        return true;
    }

    private static BlockPos lookedAt(Minecraft client) {
        if (client.player == null || client.level == null || !(client.hitResult instanceof BlockHitResult b)
                || b.getType() != HitResult.Type.BLOCK
                || b.getLocation().distanceTo(client.player.getEyePosition()) > REACH) {
            return null;
        }
        return TowerStash.kind(client.level, b.getBlockPos()) == null ? null : b.getBlockPos();
    }

    private static Interactables.Spot dot(Minecraft client, LocalPlayer player) {
        BlockPos pos = lookedAt(client);
        return pos == null ? null : new Interactables.Spot(net.minecraft.world.phys.Vec3.atCenterOf(pos), Interactables.SIZE);
    }

    private TowerStashClient() {
    }
}
