package com.zomboidcraft.power;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Mains power, Zomboid style: on at the start, cut district by district after the shutoff delay (sandbox), unless
 * the always-on toggle is set; a repaired substation brings its district back. Generators power what's near them.
 * <p>
 * Stub for now: always on. Callers (pumps, fridges, lights) can rely on the signature.
 */
public final class PowerGrid {
    /** Is there power at this spot, from the grid or a running generator nearby? */
    public static boolean hasPower(ServerLevel level, BlockPos pos) {
        return true;
    }

    private PowerGrid() {
    }
}
