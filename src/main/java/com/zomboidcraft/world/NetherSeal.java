package com.zomboidcraft.world;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * The Nether is sealed: the only way in will be the breach under Ridgeview Labs (Josiah,
 * 2026-10-29), so a vanilla Nether portal never lights. Flint and steel or a fire charge on
 * obsidian does nothing, and a portal that lit some other way (lava, an old world's portal)
 * collapses the moment a player steps into it.
 */
public final class NetherSeal {
    private NetherSeal() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            ItemStack held = player.getItemInHand(hand);
            if ((held.is(Items.FLINT_AND_STEEL) || held.is(Items.FIRE_CHARGE))
                    && level.getBlockState(hit.getBlockPos()).is(Blocks.OBSIDIAN)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(NetherSeal::tick);
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % 5 != 0) {
            return;
        }
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            ServerLevel level = sp.level();
            if (level.dimension() != Level.OVERWORLD) {
                continue;
            }
            BlockPos feet = sp.blockPosition();
            for (BlockPos at : new BlockPos[] {feet, feet.above()}) {
                if (level.getBlockState(at).is(Blocks.NETHER_PORTAL)) {
                    // one block gone and the rest of the portal falls apart with it
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }
}
