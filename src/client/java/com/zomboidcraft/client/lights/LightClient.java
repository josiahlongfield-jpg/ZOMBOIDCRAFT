package com.zomboidcraft.client.lights;

import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.world.blocks.LightSwitch;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** The light switches: the usable dot on them, and the interact key flips one. */
public final class LightClient {
    private LightClient() {
    }

    public static void register() {
        Interactables.blocks(state -> state.getBlock() instanceof LightSwitch.Switch);
    }

    /** Flips the switch you're looking at, if it is one; whether it was. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && client.level.getBlockState(block.getBlockPos()).getBlock() instanceof LightSwitch.Switch) {
            ClientPlayNetworking.send(new LightSwitch.Flip(block.getBlockPos()));
            return true;
        }
        return false;
    }
}
