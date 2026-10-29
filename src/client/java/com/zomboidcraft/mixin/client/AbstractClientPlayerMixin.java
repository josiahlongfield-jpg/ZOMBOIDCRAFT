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
    /** Aiming down a gun's sights narrows the view a little, in first person; a scope by its magnification. */
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        GunType type = firstPerson && self.isUsingItem() ? GunType.of(self.getUseItem()) : null;
        if (type != null) {
            double optic = Attachments.aimZoom(self.getUseItem());
            double zoom = optic > 0 ? optic : type.stats().aimZoom;
            cir.setReturnValue(cir.getReturnValueF() * (float) zoom);
        }
    }
}
