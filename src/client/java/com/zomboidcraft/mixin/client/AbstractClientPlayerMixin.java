package com.zomboidcraft.mixin.client;

import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunType;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    /** Aiming down a magnified scope narrows the view by its magnification, in first person. Iron sights, red dots
     * and every other unmagnified sight stay at true 1x, the world unchanged (Josiah 2026-11-02). */
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        // at a mounted gun, right-click narrows the view down the barrels
        if (self.getVehicle() instanceof com.zomboidcraft.world.cars.CarProp car && com.zomboidcraft.world.cars.Turret.isGunner(self)) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            com.zomboidcraft.world.cars.CarModels.Model m = com.zomboidcraft.world.cars.CarModels.get(car.model());
            if (self == mc.player && mc.options.keyUse.isDown() && mc.gui.screen() == null && m != null && m.turret != null) {
                cir.setReturnValue(cir.getReturnValueF() * m.turret.zoom);
            }
            return;
        }
        GunType type = firstPerson && self.isUsingItem() ? GunType.of(self.getUseItem()) : null;
        if (type != null) {
            double optic = Attachments.aimZoom(self.getUseItem());
            if (optic > 0) {
                cir.setReturnValue(cir.getReturnValueF() * (float) optic);
            }
        }
    }
}
