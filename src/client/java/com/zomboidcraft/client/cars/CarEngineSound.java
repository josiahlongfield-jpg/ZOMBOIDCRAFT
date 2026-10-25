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

/** A running car's engine: the idle loop, following the car, its pitch climbing with speed. */
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
        // about 0.8 ticking over, up to 1.7 flat out; eased so gear changes don't jump
        double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
        float target = 0.8f + 0.9f * (float) Mth.clamp(speed / 1.1, 0, 1);
        pitchNow += (target - pitchNow) * 0.15f;
        pitch = pitchNow;
        volume = 0.7f + 0.3f * (float) Mth.clamp(speed / 1.1, 0, 1);
    }
}
