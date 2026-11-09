package com.zomboidcraft.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.zomboidcraft.client.reach.ReachMeshes;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Reach world's Halo levels are drawn at the end of the game's solid pass, in the same pass as terrain and mobs. */
@Mixin(LevelRenderer.class)
public abstract class ReachSolidPassMixin {
    @Inject(method = "executeSolid", at = @At("RETURN"), require = 0)
    private void zomboidcraft$drawReach(CallbackInfo ci, @Local(argsOnly = true) RenderPass pass) {
        ReachMeshes.drawSolid(pass);
    }
}
