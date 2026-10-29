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
    /**
     * Each ZomboidCraft sound group's own slider scales its sounds, as they start and as looping ones update; and the
     * animals hush near a Harvestman.
     */
    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$groupVolume(SoundInstance sound, CallbackInfoReturnable<Float> cir) {
        // near a Harvestman the animals and the night's insects fall quiet
        float factor = SoundVolumes.factor(sound.getIdentifier()) * com.zomboidcraft.client.creature.HarvestmanPresence.hush(sound);
        if (factor != 1f) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }
}
