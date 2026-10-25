package com.zomboidcraft.mixin.client;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Attachments;
import com.zomboidcraft.combat.GunType;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    /** Aiming down a gun's sights narrows the view a little, in first person; a 4x scope a lot. */
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        GunType type = firstPerson && self.isUsingItem() ? GunType.of(self.getUseItem()) : null;
        if (type != null) {
            double zoom = Attachments.scoped(self.getUseItem()) ? ZcConfig.get().guns.scopeZoom : type.stats().aimZoom;
            cir.setReturnValue(cir.getReturnValueF() * (float) zoom);
        }
    }
}
