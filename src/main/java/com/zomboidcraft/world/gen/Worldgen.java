package com.zomboidcraft.world.gen;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Registers the ZomboidCraft world type's generator and biome source ("zomboidcraft:county"). */
public final class Worldgen {
    public static void register() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, ZomboidCraft.id("county"), CountyChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, ZomboidCraft.id("county"), CountyBiomeSource.CODEC);
    }

    private Worldgen() {
    }
}
