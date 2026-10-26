package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.AirSounds;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.Flight;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.ToDoubleFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * An aircraft's sound, mixed from layers that each follow something live: the turbine's whine with
 * engine speed, the roar and afterburner with thrust, blade slap with rotor speed, wind with airspeed,
 * a far-off rumble that carries for hundreds of blocks. From the cockpit the whine and the wind sit
 * up front and the roar is muffled; outside the roar dominates. The pilot also hears the stall
 * warning, and a touchdown chirp on landing.
 */
final class AirSound {
    private static final Map<CarProp, List<Layer>> PLAYING = new WeakHashMap<>();
    private static final Map<CarProp, Integer> WARNED = new WeakHashMap<>();

    /** Called each client tick for every aircraft in view. */
    static void keep(CarProp car) {
        Flight f = car.flight();
        Minecraft mc = Minecraft.getInstance();
        if (car.touchedDown > 2) {
            mc.getSoundManager().play(new SimpleSoundInstance(AirSounds.TOUCHDOWN, SoundSource.NEUTRAL,
                    Math.min(1.5f, car.touchedDown / 4), 0.9f + car.getRandom().nextFloat() * 0.2f, SoundInstance.createUnseededRandom(),
                    car.getX(), car.getY(), car.getZ()));
        }
        boolean pilot = FlightClient.aircraft(mc.player) == car;
        if (pilot && f.stalled) {
            int last = WARNED.getOrDefault(car, -100);
            if (car.tickCount - last > 20) {
                WARNED.put(car, car.tickCount);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(AirSounds.WARN_STALL, 1f, 0.7f));
            }
        }
        if (f.rpm < 0.02f && f.speed() < 3) {
            return;
        }
        List<Layer> layers = PLAYING.get(car);
        if (layers != null && layers.stream().noneMatch(Layer::isStopped)) {
            return;
        }
        if (layers != null) {
            layers.forEach(Layer::end);
        }
        layers = new ArrayList<>();
        if (f.kind == Flight.Kind.JET) {
            layers.add(new Layer(car, AirSounds.JET_IDLE, c -> inside(c) ? 0.9 * spool(c) : 0.45 * spool(c), c -> 0.7 + 0.9 * c.flight().rpm));
            layers.add(new Layer(car, AirSounds.JET_ROAR, c -> (inside(c) ? 0.35 : 1.0) * Mth.clamp(c.flight().thrustShare() * 1.3 + 0.08 * spool(c), 0, 1),
                    c -> 0.75 + 0.35 * c.flight().power));
            layers.add(new Layer(car, AirSounds.JET_AFTERBURNER, c -> c.flight().afterburner() ? (inside(c) ? 0.5 : 1.2) : 0, c -> 1));
            layers.add(new Layer(car, AirSounds.JET_DISTANT, c -> far(c) * (0.3 + c.flight().thrustShare()), c -> 1));
        } else {
            layers.add(new Layer(car, AirSounds.HELI_ROTOR, c -> (inside(c) ? 0.7 : 1.1) * Mth.clamp(c.flight().rpm * 1.4f, 0, 1) * (0.7 + 0.4 * c.flight().power),
                    c -> 0.45 + 0.6 * c.flight().rpm));
            layers.add(new Layer(car, AirSounds.HELI_TURBINE, c -> (inside(c) ? 0.6 : 0.35) * spool(c), c -> 0.7 + 0.6 * c.flight().rpm));
            layers.add(new Layer(car, AirSounds.HELI_DISTANT, c -> far(c) * c.flight().rpm, c -> 0.6 + 0.4 * c.flight().rpm));
        }
        layers.add(new Layer(car, AirSounds.WIND, c -> inside(c) ? Mth.clamp((c.flight().speed() - 10) / 60, 0, 1) * 0.8 : 0,
                c -> 0.7 + 0.8 * Mth.clamp(c.flight().speed() / c.flight().top, 0, 1)));
        layers.forEach(l -> mc.getSoundManager().play(l));
        PLAYING.put(car, layers);
    }

    private static double spool(CarProp c) {
        return Mth.clamp(c.flight().rpm / 0.6, 0, 1);
    }

    private static boolean inside(CarProp c) {
        return FlightClient.aircraft(Minecraft.getInstance().player) == c;
    }

    /** How much of the far-off rumble to add: none close by, all of it a hundred and fifty blocks off. */
    private static double far(CarProp c) {
        var p = Minecraft.getInstance().player;
        return p == null ? 0 : Mth.clamp((Math.sqrt(p.distanceToSqr(c)) - 50) / 100, 0, 1) * 1.5;
    }

    /** One looping layer on an aircraft, its loudness and pitch worked out every tick. */
    private static final class Layer extends AbstractTickableSoundInstance {
        private final CarProp car;
        private final ToDoubleFunction<CarProp> loud, high;
        private float volumeNow, pitchNow;

        Layer(CarProp car, SoundEvent event, ToDoubleFunction<CarProp> loud, ToDoubleFunction<CarProp> high) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.car = car;
            this.loud = loud;
            this.high = high;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.volumeNow = 0.001f;
            this.pitchNow = (float) Mth.clamp(high.applyAsDouble(car), 0.5, 2);
            this.volume = volumeNow;
            this.pitch = pitchNow;
            follow();
        }

        void end() {
            stop();
        }

        private void follow() {
            x = car.getX();
            y = car.getY() + 2;
            z = car.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (car.isRemoved() || car.flight().rpm < 0.01f && car.flight().speed() < 2) {
                stop();
                return;
            }
            follow();
            volumeNow += ((float) Math.max(0.001, loud.applyAsDouble(car)) - volumeNow) * 0.2f;
            pitchNow += ((float) Mth.clamp(high.applyAsDouble(car), 0.5, 2) - pitchNow) * 0.2f;
            volume = volumeNow;
            pitch = pitchNow;
        }
    }

    private AirSound() {
    }
}
