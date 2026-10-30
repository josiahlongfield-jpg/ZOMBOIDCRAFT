package com.zomboidcraft.client.kit;

import com.zomboidcraft.world.files.LabFiles;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Reading a file in the world (LabFiles): the interact key on one, or a click, tells the server
 * and opens the kit on the Files page with it showing. Files get the green dot like every other
 * thing you can use.
 */
public final class FilesClient {
    public static void register() {
        com.zomboidcraft.client.interact.Interactables.blocks(state -> LabFiles.id(state) != null);
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide()) return InteractionResult.PASS;
            String id = LabFiles.id(level.getBlockState(hit.getBlockPos()));
            if (id == null) return InteractionResult.PASS;
            Minecraft mc = Minecraft.getInstance();
            mc.execute(() -> KitScreen.openFile(mc, id));
            return InteractionResult.SUCCESS;
        });
    }

    /** The interact key: true if it was on a file. */
    public static boolean interact(Minecraft client, HitResult hit) {
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK && client.level != null) {
            String id = LabFiles.id(client.level.getBlockState(block.getBlockPos()));
            if (id != null) {
                ClientPlayNetworking.send(new LabFiles.Read(block.getBlockPos()));
                KitScreen.openFile(client, id);
                return true;
            }
        }
        return false;
    }

    private FilesClient() {
    }
}
