package com.zomboidcraft.mixin;

import com.zomboidcraft.zombie.ZedSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A walker's own moans, hurt cries, death and footsteps (vanilla zombie, husk and zombie villager sounds, all played
 * through Entity.playSound) become Project Zomboid's: its voice, and its feet on what it walks on (ZedSounds).
 */
@Mixin(Entity.class)
public abstract class EntitySoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$zomboidVoice(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        if (sound != null && ZedSounds.swap((Entity) (Object) this, sound, volume)) {
            ci.cancel();
        }
    }
}
