package com.zomboidcraft.client.decor;

import com.zomboidcraft.client.interact.InteractMenu;
import com.zomboidcraft.client.interact.Interactables;
import com.zomboidcraft.decor.DecorBlock;
import com.zomboidcraft.decor.DecorNet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Decor you can use: the green dot, a tap of G switches it on or off (or plays it), and the G menu has both. Picking
 * it up is the moving menu's (building/Moving).
 */
public final class DecorClient {
    private DecorClient() {
    }

    private static boolean usable(BlockState s) {
        return s.getBlock() instanceof DecorBlock d && (d.spec.toggle() || d.spec.play() > 0);
    }

    public static void register() {
        Interactables.blocks(DecorClient::usable);
        InteractMenu.provider((client, player, hit, out) -> {
            BlockPos pos = at(client, hit);
            if (pos == null) {
                return;
            }
            BlockState s = client.level.getBlockState(pos);
            DecorBlock d = (DecorBlock) s.getBlock();
            if (d.spec.toggle()) {
                boolean on = s.getValue(DecorBlock.ON);
                out.add(InteractMenu.Option.of(on ? "Switch it off" : "Switch it on", () -> send(pos, DecorNet.Action.TOGGLE)));
            }
            if (d.spec.play() > 0) {
                out.add(InteractMenu.Option.of("Play", () -> send(pos, DecorNet.Action.PLAY)));
            }
        });
    }

    private static void send(BlockPos pos, DecorNet.Action action) {
        ClientPlayNetworking.send(new DecorNet.Use(pos, action.ordinal()));
    }

    private static BlockPos at(Minecraft client, HitResult hit) {
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK
                && usable(client.level.getBlockState(block.getBlockPos())) ? block.getBlockPos() : null;
    }

    /** A tap of G: switch it, or play it when it doesn't switch. */
    public static boolean interact(Minecraft client, HitResult hit) {
        BlockPos pos = at(client, hit);
        if (pos == null) {
            return false;
        }
        DecorBlock d = (DecorBlock) client.level.getBlockState(pos).getBlock();
        send(pos, d.spec.toggle() ? DecorNet.Action.TOGGLE : DecorNet.Action.PLAY);
        return true;
    }
}
