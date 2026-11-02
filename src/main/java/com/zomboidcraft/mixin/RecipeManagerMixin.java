package com.zomboidcraft.mixin;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * No vanilla item, ever (Josiah 2026-11-02): none of Minecraft's own recipes are used, so nothing vanilla (a chest, a
 * crafting table, a torch) can be crafted, smelted or cut. Only ours (data/zomboidcraft/recipe) are left.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @ModifyExpressionValue(method = "*", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeMap;create(Lnet/minecraft/core/HolderLookup;)Lnet/minecraft/world/item/crafting/RecipeMap;"))
    private RecipeMap zomboidcraft$oursOnly(RecipeMap map) {
        ImmutableMultimap.Builder<net.minecraft.world.item.crafting.RecipeType<?>, RecipeHolder<?>> byType = ImmutableMultimap.builder();
        ImmutableMap.Builder<net.minecraft.resources.ResourceKey<net.minecraft.world.item.crafting.Recipe<?>>, RecipeHolder<?>> byKey = ImmutableMap.builder();
        for (RecipeHolder<?> holder : map.values()) {
            if (!holder.id().identifier().getNamespace().equals("minecraft")) {
                byType.put(holder.value().getType(), holder);
                byKey.put(holder.id(), holder);
            }
        }
        return RecipeMapInvoker.zomboidcraft$make(byType.build(), byKey.build());
    }
}
