package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.hud.InjuryGlowLayer;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes the render state made for the HUD's figure, so the injury glow layer draws on that figure and nowhere else
 * (not the inventory mirror, not you in third person).
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 1)
    private static void zomboidcraft$noteFigure(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> cir) {
        InjuryGlowLayer.noted(cir.getReturnValue());
    }
}
