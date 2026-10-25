package com.zomboidcraft.loot;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;

/**
 * The sound a container makes when you open it and again when you're done (the sound thread's loot.* events in
 * sounds.json). Shelves only rustle quietly on opening.
 */
final class LootSounds {
    private static final String[] SHELVES = {"store_shelf_", "gun_rack", "pawn_wall", "pegboard", "clothes_rack",
            "produce", "lumber", "paint_cans", "coffee"};

    /** The loot.* event for a block. */
    static String event(Block block) {
        String name = BuiltInRegistries.BLOCK.getKey(block).getPath();
        for (String shelf : SHELVES) {
            if (name.startsWith(shelf)) {
                return "loot.shelf";
            }
        }
        if (name.contains("filing_cabinet") || name.contains("counter")) return "loot.drawer";
        if (name.contains("oven") || name.contains("washer") || name.contains("dryer") || name.contains("grill")
                || name.contains("vending") || name.startsWith("case_")) return "loot.cupboard";
        if (name.contains("cooler")) return "loot.fridge";
        if (name.contains("register")) return "loot.register";
        if (name.contains("safe")) return "loot.safe";
        return "loot.box"; // boxes, crates, bins, chests, barrels and anything else
    }

    static boolean openOnly(String event) {
        return "loot.shelf".equals(event);
    }

    static void play(ServerLevel level, BlockPos pos, String event, boolean closing) {
        if (closing && openOnly(event)) {
            return;
        }
        float volume = openOnly(event) ? 0.35f : 1.0f;
        float pitch = closing ? 0.9f : 0.9f + level.getRandom().nextFloat() * 0.2f;
        level.playSound(null, pos, SoundEvent.createVariableRangeEvent(ZomboidCraft.id(event)), SoundSource.BLOCKS, volume, pitch);
    }

    private LootSounds() {
    }
}
