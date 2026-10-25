package com.zomboidcraft.mixin.client;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Attachments;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    /** Aiming down the rifle's sights narrows the view a little, in first person; a 4x scope a lot. */
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$aimZoom(boolean firstPerson, float effectScale, CallbackInfoReturnable<Float> cir) {
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        if (firstPerson && self.isUsingItem() && self.getUseItem().is(ModItems.ASSAULT_RIFLE)) {
            ZcConfig.Guns guns = ZcConfig.get().guns;
            cir.setReturnValue(cir.getReturnValueF() * (float) (Attachments.scoped(self.getUseItem()) ? guns.scopeZoom : guns.aimZoom));
        }
    }
}
