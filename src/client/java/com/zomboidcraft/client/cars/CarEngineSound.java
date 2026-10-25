package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.CarSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** A running car's engine: the steady idle loop, following the car, its pitch and loudness climbing with speed. */
final class CarEngineSound extends AbstractTickableSoundInstance {
    private static final Map<CarProp, CarEngineSound> PLAYING = new WeakHashMap<>();

    private final CarProp car;
    private float pitchNow = 0.8f;

    private CarEngineSound(CarProp car) {
        super(CarSounds.IDLE, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.car = car;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.8f;
        this.pitch = pitchNow;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        follow();
    }

    /** Keeps a car's engine sounding; called each tick it runs. */
    static void keep(CarProp car) {
        CarEngineSound sound = PLAYING.get(car);
        Minecraft client = Minecraft.getInstance();
        if (sound == null || sound.isStopped()) {
            sound = new CarEngineSound(car);
            PLAYING.put(car, sound);
            client.getSoundManager().play(sound);
        }
    }

    private void follow() {
        x = car.getX();
        y = car.getY() + 0.6;
        z = car.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (car.isRemoved() || !car.engineOn()) {
            stop();
            return;
        }
        follow();
        // a steady idle pitched up: 0.8 ticking over, 2.0 (the most the game allows) flat out, and
        // louder as it goes; eased so a change of speed sweeps rather than jumps
        double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
        float fast = (float) Mth.clamp(speed / 1.1, 0, 1);
        pitchNow += (0.8f + 1.2f * fast - pitchNow) * 0.15f;
        pitch = pitchNow;
        volume = 0.6f + 0.6f * fast;
    }
}
