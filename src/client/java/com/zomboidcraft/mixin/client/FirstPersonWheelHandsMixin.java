package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.cars.CarHands;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonWheelHandsMixin {
    /** Both hands on the steering wheel (the car draws them there): no hand, and nothing in it, in front of the eyes. */
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$handsOnWheel(CallbackInfo ci) {
        if (CarHands.hidesFirstPerson()) {
            ci.cancel();
        }
    }
}
