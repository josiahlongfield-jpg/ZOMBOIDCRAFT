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
        // your character's ears: Keen Hearing, Hard of Hearing, Deaf
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && sound.getSource() != net.minecraft.sounds.SoundSource.MUSIC
                && sound.getSource() != net.minecraft.sounds.SoundSource.MASTER && !sound.isRelative()) {
            SoundVolumes.Group group = SoundVolumes.groupOf(sound.getIdentifier());
            boolean dead = group == SoundVolumes.Group.ZOMBIE_VOICES || group == SoundVolumes.Group.HORDES
                    || group == SoundVolumes.Group.ZOMBIE_STEPS || group == SoundVolumes.Group.DOOR_BASHING;
            factor *= dead ? com.zomboidcraft.zombie.TraitSenses.zombieSounds(player) : com.zomboidcraft.zombie.TraitSenses.world(player);
        }
        if (factor != 1f) {
            cir.setReturnValue(cir.getReturnValueF() * factor);
        }
    }

    /** Zombie sounds keep to their share of the channels, so a gunshot always finds one (ZombieSoundCap). */
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$zombieShare(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (!com.zomboidcraft.client.sound.ZombieSoundCap.allow((SoundEngine) (Object) this, sound)) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
