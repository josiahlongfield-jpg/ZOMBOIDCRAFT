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
    private static final Map<CarProp, Engine> ENGINES = new WeakHashMap<>();
    /** How long each start-up recording runs before the idle loop (cut from the same recording) takes over. */
    private static final int JET_START_TICKS = 50 * 20, HELI_START_TICKS = 35 * 20;

    /** Called each client tick for every aircraft in view. */
    static void keep(CarProp car) {
        if (CarProp.quiet.test(car)) {
            List<Layer> playing = PLAYING.remove(car);     // a flyby heard as a whole recording (WorldEventSound)
            if (playing != null) {
                playing.forEach(Layer::end);
            }
            return;
        }
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
            // the spoken warning is three calls over four and a half seconds; let each one finish
            if (car.tickCount - last > 90) {
                WARNED.put(car, car.tickCount);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(AirSounds.WARN_STALL, 1f, 0.7f));
            }
        }
        starting(car);
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
            // the idle loop is the real idle: pitch 1 at idle, rising only as the throttle opens
            layers.add(new Layer(car, AirSounds.JET_IDLE, c -> (inside(c) ? 0.9 : 0.45) * spool(c) * running(c),
                    c -> 1 + 0.45 * Mth.clamp((c.flight().rpm - 0.6) / 0.4, 0, 1)));
            layers.add(new Layer(car, AirSounds.JET_ROAR, c -> (inside(c) ? 0.35 : 1.0) * Mth.clamp(c.flight().thrustShare() * 1.3 + 0.04 * spool(c), 0, 1) * running(c),
                    c -> (0.75 + 0.35 * c.flight().power) * doppler(c)));
            layers.add(new Layer(car, AirSounds.JET_AFTERBURNER, c -> c.flight().afterburner() ? (inside(c) ? 0.5 : 1.2) * running(c) : 0, c -> 1));
            layers.add(new Layer(car, AirSounds.JET_DISTANT, c -> far(c) * (0.3 + c.flight().thrustShare()), AirSound::doppler));
        } else {
            // in the cabin: the idle recording at its own pitch once the start has played
            layers.add(new Layer(car, AirSounds.HELI_IDLE, c -> inside(c) ? 0.9 * c.flight().rpm * running(c) : 0,
                    c -> 1 + 0.08 * c.flight().power));
            // outside: the rotor loop, louder with lift, shifted by the Doppler effect as it passes
            layers.add(new Layer(car, AirSounds.HELI_ROTOR, c -> (inside(c) ? 0.08 : 1.1) * Mth.clamp(c.flight().rpm * 1.4f, 0, 1)
                    * (0.75 + 0.35 * c.flight().power) * running(c), c -> (0.55 + 0.45 * c.flight().rpm) * doppler(c)));
            layers.add(new Layer(car, AirSounds.HELI_TURBINE, c -> (inside(c) ? 0.2 : 0.18) * spool(c) * running(c), c -> 0.7 + 0.6 * c.flight().rpm));
            layers.add(new Layer(car, AirSounds.HELI_DISTANT, c -> far(c) * c.flight().rpm, AirSound::doppler));
        }
        layers.add(new Layer(car, AirSounds.WIND, c -> inside(c) ? Mth.clamp((c.flight().speed() - 10) / 60, 0, 1) * 0.8 : 0,
                c -> 0.7 + 0.8 * Mth.clamp(c.flight().speed() / c.flight().top, 0, 1)));
        layers.forEach(l -> mc.getSoundManager().play(l));
        PLAYING.put(car, layers);
    }

    /**
     * The start-up recording, played when the engine is switched on in sight: for its fifty seconds (the
     * helicopter's thirty-five) the engine layers stay quiet so the recording isn't doubled by a pitched
     * copy of itself, then the idle loop comes in. Opening the throttle before then fades the start out and hands over early;
     * switching off stops it. An aircraft first seen already running never plays it.
     */
    private static void starting(CarProp car) {
        Engine e = ENGINES.computeIfAbsent(car, c -> new Engine(c.engineOn()));
        boolean on = car.engineOn(), jet = car.flight().kind == Flight.Kind.JET;
        if (on && !e.on) {
            e.start = new Start(car, jet ? AirSounds.JET_START : AirSounds.HELI_START);
            e.startedAt = car.tickCount;
            Minecraft.getInstance().getSoundManager().play(e.start);
        }
        e.on = on;
        boolean early = car.flight().power > (jet ? 0.05f : 0.25f);
        boolean playing = e.start != null && on && car.tickCount - e.startedAt < (jet ? JET_START_TICKS : HELI_START_TICKS) && !early;
        if (e.start != null && !playing) {
            e.start.fadeOut(on ? (early ? 20 : 6) : 2);
            e.start = null;
        }
        e.running = playing ? 0 : 1;
    }

    /** 0 while the start-up recording has the engine to itself, 1 once the loops take over. */
    private static double running(CarProp c) {
        Engine e = ENGINES.get(c);
        return e == null ? 1 : e.running;
    }

    private static final class Engine {
        boolean on;
        Start start;
        int startedAt;
        float running = 1;

        Engine(boolean on) {
            this.on = on;
        }
    }

    /** A start-up recording, following the aircraft, stopped if the engine's switched off part way. */
    private static final class Start extends AbstractTickableSoundInstance {
        private final CarProp car;
        private int fade = -1, fadeTicks;

        Start(CarProp car, SoundEvent event) {
            super(event, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.car = car;
            this.volume = 1.5f;
            this.pitch = 1;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            tick();
        }

        void fadeOut(int ticks) {
            if (fade < 0) {
                fade = fadeTicks = Math.max(1, ticks);
            }
        }

        @Override
        public void tick() {
            if (car.isRemoved()) {
                stop();
                return;
            }
            x = car.getX();
            y = car.getY() + 2;
            z = car.getZ();
            if (fade >= 0) {
                volume = 1.5f * fade / fadeTicks;
                if (fade-- == 0) {
                    stop();
                }
            }
        }
    }

    /**
     * The Doppler shift for the listener: higher while the aircraft closes on them, lower as it goes away
     * (speed of sound 343 m/s; kept within 0.6 to 1.8 so a jet passing close doesn't squeal).
     */
    private static double doppler(CarProp c) {
        var p = Minecraft.getInstance().player;
        if (p == null || inside(c)) {
            return 1;
        }
        double dx = p.getX() - c.getX(), dy = p.getEyeY() - c.getY() - 2, dz = p.getZ() - c.getZ();
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1) {
            return 1;
        }
        var v = c.flight().vel;
        var mine = p.getDeltaMovement().scale(20);
        double closing = ((v.x - mine.x) * dx + (v.y - mine.y) * dy + (v.z - mine.z) * dz) / d;
        return Mth.clamp(343 / (343 - Mth.clamp(closing, -300, 300)), 0.6, 1.8);
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
