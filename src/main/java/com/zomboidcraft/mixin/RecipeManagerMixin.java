package com.zomboidcraft.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * No vanilla item, ever (Josiah 2026-11-02): none of Minecraft's own recipes load, so nothing vanilla (a chest, a
 * crafting table, a torch) can be crafted, smelted or cut. Only ours (data/zomboidcraft/recipe) are left.
 */
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @ModifyVariable(method = "apply", at = @At("HEAD"), argsOnly = true)
    private RecipeMap zomboidcraft$oursOnly(RecipeMap map) {
        List<RecipeHolder<?>> ours = new ArrayList<>();
        for (RecipeHolder<?> holder : map.values()) {
            if (!holder.id().identifier().getNamespace().equals("minecraft")) {
                ours.add(holder);
            }
        }
        return RecipeMap.create(ours);
    }
}
