package com.zomboidcraft;

import com.zomboidcraft.barricade.BarricadeBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

public final class ModBlocks {
    public static final Block BARRICADE = register("barricade", BlockBehaviour.Properties.of()
            .strength(2.0f, 3.0f).sound(SoundType.WOOD).noOcclusion().pushReaction(PushReaction.DESTROY));

    private static Block register(String name, BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, ZomboidCraft.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, new BarricadeBlock(properties.setId(key)));
    }

    public static void register() {
        // Loading the class registers the blocks.
    }

    private ModBlocks() {
    }
}
