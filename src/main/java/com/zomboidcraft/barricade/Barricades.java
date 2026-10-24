package com.zomboidcraft.barricade;

import com.zomboidcraft.ModBlocks;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

public final class Barricades {
    /** Breaking a barricade by hand gives the planks back (the nails are bent and lost). */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (state.is(ModBlocks.BARRICADE) && !player.hasInfiniteMaterials()) {
                Block.popResource(level, pos, new ItemStack(Items.OAK_PLANKS, state.getValue(BarricadeBlock.PLANKS)));
            }
        });
    }

    private Barricades() {
    }
}
