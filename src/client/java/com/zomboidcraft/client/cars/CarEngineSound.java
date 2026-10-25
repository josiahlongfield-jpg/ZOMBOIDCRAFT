package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.CarSounds;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A running car's engine, following the car: a steady idle loop that fades into a steady cruising
 * loop as the car picks up speed, both climbing in pitch with it. Without a cruising recording
 * the idle carries the whole range on its own.
 */
final class CarEngineSound extends AbstractTickableSoundInstance {
    private static final Map<CarProp, CarEngineSound[]> PLAYING = new WeakHashMap<>();
    /** Full cruising sound from about 36 km/h. */
    private static final double CRUISE = 0.5;

    private final CarProp car;
    private final boolean cruise, alone;
    private float pitchNow = 0.8f, gainNow;

    private CarEngineSound(CarProp car, SoundEvent event, boolean cruise, boolean alone) {
        super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.car = car;
        this.cruise = cruise;
        this.alone = alone;
        this.looping = true;
        this.delay = 0;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        gainNow = cruise ? 0 : 0.8f;
        volume = Math.max(0.001f, gainNow);
        pitch = pitchNow;
        follow();
    }

    /** Keeps a car's engine sounding; called each tick it runs. */
    static void keep(CarProp car) {
        Minecraft client = Minecraft.getInstance();
        CarEngineSound[] layers = PLAYING.get(car);
        if (layers == null || layers[0].isStopped()) {
            boolean hasCruise = client.getSoundManager().getSoundEvent(CarSounds.DRIVE.location()) != null;
            layers = hasCruise
                    ? new CarEngineSound[] {new CarEngineSound(car, CarSounds.IDLE, false, false), new CarEngineSound(car, CarSounds.DRIVE, true, false)}
                    : new CarEngineSound[] {new CarEngineSound(car, CarSounds.IDLE, false, true)};
            PLAYING.put(car, layers);
            for (CarEngineSound layer : layers) {
                client.getSoundManager().play(layer);
            }
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
        double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
        float fast = (float) Mth.clamp(speed / 1.1, 0, 1);
        float blend = (float) Mth.clamp(speed / CRUISE, 0, 1);
        float targetPitch, targetGain;
        if (alone) {
            // about 0.8 ticking over, up to 1.7 flat out
            targetPitch = 0.8f + 0.9f * fast;
            targetGain = 0.7f + 0.3f * fast;
        } else if (cruise) {
            targetPitch = 0.75f + 0.75f * fast;
            targetGain = 0.9f * blend;
        } else {
            targetPitch = 0.85f + 0.45f * blend;
            targetGain = 0.8f * (1 - blend * 0.85f);
        }
        // eased, so a change of speed sweeps rather than jumps
        pitchNow += (targetPitch - pitchNow) * 0.15f;
        gainNow += (targetGain - gainNow) * 0.2f;
        pitch = pitchNow;
        volume = Math.max(0.001f, gainNow);
    }
}
