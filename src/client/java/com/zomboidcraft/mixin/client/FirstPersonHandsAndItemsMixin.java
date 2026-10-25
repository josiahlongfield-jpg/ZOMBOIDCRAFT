package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.combat.RifleView;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItems.class)
public abstract class FirstPersonHandsAndItemsMixin {
    /** On the sights, the held gun barely lags behind the look: the hand sway is scaled down like the bob. */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void zomboidcraft$steadyAim(LocalPlayer player, float partialTick, FirstPersonHandsAndItemsRenderState state,
                                        CallbackInfo ci) {
        float keep = RifleView.bobScale();
        if (keep < 1f) {
            state.xBob = state.viewXRot - (state.viewXRot - state.xBob) * keep;
            state.yBob = state.viewYRot - (state.viewYRot - state.yBob) * keep;
        }
        // the hand renderer turns the hands by a tenth of these: the gun's rail lights follow it
        RifleView.sway((state.viewXRot - state.xBob) * 0.1f, (state.viewYRot - state.yBob) * 0.1f);
    }
}
