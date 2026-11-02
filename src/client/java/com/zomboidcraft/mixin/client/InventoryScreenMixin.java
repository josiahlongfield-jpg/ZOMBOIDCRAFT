package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.hud.InjuryGlowLayer;
import com.zomboidcraft.client.kit.SpinFigure;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Notes the render state made for the HUD's figure, so the injury glow layer draws on that figure and nowhere else
 * (not the inventory mirror, not you in third person). While a kit figure is drawn ({@link SpinFigure}) it also
 * stands it still in its idle pose and turns it by however far it has been dragged.
 */
@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
            at = @At("RETURN"), require = 1)
    private static void zomboidcraft$noteFigure(LivingEntity entity, CallbackInfoReturnable<EntityRenderState> cir) {
        InjuryGlowLayer.noted(cir.getReturnValue());
        // the appearance page's figure: the survivor layer turns her as far as you've dragged her
        com.zomboidcraft.client.kit.AppearanceScreen.noteFigure(cir.getReturnValue());
        if (SpinFigure.drawing && cir.getReturnValue() instanceof LivingEntityRenderState living) {
            // idle: no walking stride carried over from the moment the kit was opened
            living.walkAnimationPos = 0;
            living.walkAnimationSpeed = 0;
        }
    }

    @ModifyArg(method = "extractEntityInInventoryFollowsMouse", require = 1, index = 3,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;entity(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FLorg/joml/Vector3fc;Lorg/joml/Quaternionfc;Lorg/joml/Quaternionfc;IIII)V"))
    private static Quaternionfc zomboidcraft$spin(Quaternionfc pose) {
        if (!SpinFigure.drawing || SpinFigure.yaw == 0) return pose;
        return new Quaternionf(pose).rotateY(SpinFigure.yaw * ((float) Math.PI / 180f));
    }
}
