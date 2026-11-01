package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.combat.MeshGuns;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes when a picture-in-picture (the HUD's figure, the inventory mirror) is being drawn, so the mesh guns there
 * skip their ink rim: a gun a few dozen pixels long shows no rim, and the profile (2026-11-01) had the held gun
 * drawn again in the HUD figure costing a sixth of the frame.
 */
@Mixin(PictureInPictureRenderer.class)
public abstract class PictureInPictureMixin {
    @Inject(method = "prepare", at = @At("HEAD"), require = 0)
    private void zomboidcraft$enter(CallbackInfo ci) {
        MeshGuns.inPicture = true;
    }

    @Inject(method = "prepare", at = @At("RETURN"), require = 0)
    private void zomboidcraft$leave(CallbackInfo ci) {
        MeshGuns.inPicture = false;
    }
}
