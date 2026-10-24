package com.zomboidcraft.zombie;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Implemented on zombies by a mixin: the last noise they heard. */
public interface NoiseListener {
    void zomboidcraft$hear(Vec3 pos);

    @Nullable
    Vec3 zomboidcraft$heardAt();

    void zomboidcraft$forget();
}
