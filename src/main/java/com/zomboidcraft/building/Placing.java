package com.zomboidcraft.building;

import com.zomboidcraft.ZcConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * No dirt huts. In survival the vanilla game's blocks don't go down as building material: every wall comes from the
 * Build page, so the skill ladders mean something and nothing plain and blocky sits beside the town. Earth is soil
 * only: it fills natural ground back up to where the land first lay, never higher. Seeds and saplings still plant.
 * The town's own furniture places freely, since lifting it was the hard part.
 */
public final class Placing {
    /** Whether this vanilla block may go down here; false refuses it (BlockItemPlaceMixin, both sides). */
    public static boolean allowed(BlockPlaceContext context) {
        Player player = context.getPlayer();
        ItemStack held = context.getItemInHand();
        if (player == null || !active(player)) {
            return true;
        }
        String id = BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
        if (!id.startsWith("minecraft:")) {
            return true;
        }
        ZcConfig.Building b = ZcConfig.get().building;
        if (b.plants.contains(id)) {
            return true;
        }
        Level level = context.getLevel();
        if (b.soil.contains(id)) {
            if (!(level instanceof ServerLevel server)) {
                return true;  // the server says whether it's a hole in the ground
            }
            BlockPos at = context.getClickedPos();
            if (server.dimension() == Level.OVERWORLD && at.getY() <= DigRules.ground(server, at.getX(), at.getZ())
                    && DigRules.natural(level.getBlockState(at.below()))) {
                return true;
            }
            say(player, "Earth only fills the ground back in. Build walls from the Build page.");
            return false;
        }
        say(player, "Not a building material. Build walls from the Build page (E).");
        return false;
    }

    private static boolean active(Player player) {
        return !ZcConfig.get().building.vanillaBlocks && ZcConfig.get().digging.rules && !player.hasInfiniteMaterials() && !player.isSpectator();
    }

    private static void say(Player player, String text) {
        if (player instanceof ServerPlayer server) {
            server.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
            // the client already took one off the stack for the block it guessed would go down: put it back
            server.inventoryMenu.sendAllDataToRemote();
        }
    }

    private Placing() {
    }
}
