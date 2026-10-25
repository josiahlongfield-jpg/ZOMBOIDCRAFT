package com.zomboidcraft.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/**
 * A sound played at your own ears, for your own gun and swings. A sound placed at the spot you fired from stays
 * behind as you move, so strafing while shooting made your own shots seem to come from behind you.
 */
public final class EarSound {
    public static void play(SoundEvent event, SoundSource source, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(new Instance(event, source, volume, pitch));
    }

    private static final class Instance extends AbstractSoundInstance {
        Instance(SoundEvent event, SoundSource source, float volume, float pitch) {
            super(event, source, SoundInstance.createUnseededRandom());
            this.volume = volume;
            this.pitch = pitch;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }
    }

    private EarSound() {
    }
}
