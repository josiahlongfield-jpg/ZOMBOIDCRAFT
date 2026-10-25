package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.sound.SoundVolumes;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SoundEngine.class)
public abstract class SoundEngineMixin {
    /** Each ZomboidCraft sound group's own slider scales its sounds, as they start and as looping ones update. */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$groupVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        float factor = SoundVolumes.factor(sound.getIdentifier());
        if (factor != 1f) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }
}
