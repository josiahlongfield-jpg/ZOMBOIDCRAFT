package com.zomboidcraft.client.ferry;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.world.ferry.Ferries;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The ferry bells on your side: the usable dot on them, and the interact key rings one. */
public final class FerryClient {
    private FerryClient() {
    }

    public static void register() {
        Interactables.blocks(state -> state.is(Ferries.BELL));
    }

    /** Rings the bell you're looking at, if it is one; whether it was. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(block.getBlockPos()).is(Ferries.BELL)) {
            ClientPlayNetworking.send(new Ferries.Ring(block.getBlockPos()));
            return true;
        }
        return false;
    }
}
