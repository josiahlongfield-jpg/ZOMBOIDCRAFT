package com.zomboidcraft.client.cars;

import com.zomboidcraft.world.cars.CarModels;
import com.zomboidcraft.world.cars.CarProp;
import com.zomboidcraft.world.cars.CarSounds;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A converted Workshop car's engine, played the way Zomboid's FMOD engine events play it: the model's
 * recorded engine (sports, wagon, offroad or van, from the local PZ car sounds pack) as loops taken at
 * fixed revs, an engine-bay layer and an exhaust layer each, an on-load take with the throttle open and
 * an off-load take coasting. The two takes either side of the revs crossfade, each pitched by how far the
 * revs sit from where it was recorded.
 *
 * Josiah 2026-11-01 asked for no gear changes, and 2026-11-04 for each engine's own recorded acceleration
 * back (the single steady note he had meanwhile is gone), smooth and louder: so the revs rise in one smooth
 * sweep from idle with the car's speed, the speed and throttle are smoothed so the note never hunts, and the
 * engine is loudest pulling away.
 */
final class PzEngineSound {
    /** The revs each band was recorded at (pzsounds.py ENGINES), above the idle loop at IDLE. */
    private static final Map<String, float[]> BANDS = Map.of(
            "sports", new float[] {1000, 2000, 3000, 4000, 5000, 6000},
            "wagon", new float[] {1000, 2000, 3000, 4000},
            "offroad", new float[] {1000, 1600, 2200, 2800, 3400, 4000, 4600, 5200},
            "van", new float[] {1000, 1750, 2500, 3250, 4000});
    private static final float IDLE = 800;
    private static final double FLAT_OUT = 1.1;
    private static final Map<CarProp, Engine> ENGINES = new WeakHashMap<>();

    /** Whether this car has Zomboid's engine and the pack that holds it. */
    static boolean has(CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        String set = m == null || m.sounds == null ? null : m.sounds.get("engine");
        return set != null && BANDS.containsKey(set)
                && Minecraft.getInstance().getSoundManager().getSoundEvent(CarSounds.pz("engine." + set + ".idle.engine")) != null;
    }

    /** Called each tick the car's engine runs. */
    static void keep(CarProp car) {
        ENGINES.computeIfAbsent(car, Engine::new).tick();
    }

    private static final class Engine {
        final CarProp car;
        final String set;
        final float[] at;
        final Map<String, Layer> layers = new HashMap<>();
        float rpm = IDLE, load;
        int lastTick = -100, running;
        double speedNow, lastSpeed;

        Engine(CarProp car) {
            this.car = car;
            this.set = CarModels.get(car.model()).sounds.get("engine");
            float[] bands = BANDS.get(set);
            at = new float[bands.length + 1];
            at[0] = IDLE;
            System.arraycopy(bands, 0, at, 1, bands.length);
        }

        void tick() {
            if (car.tickCount == lastTick) {
                return;
            }
            running = car.tickCount - lastTick > 2 ? 0 : running + 1;      // a fresh start after a gap
            lastTick = car.tickCount;
            // the speed smoothed over about half a second, so the tick-to-tick wobble in the car's movement
            // doesn't make the revs or the throttle hunt
            double raw = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
            speedNow += (raw - speedNow) * 0.1;
            double speed = speedNow;
            float share = (float) Mth.clamp(speed / FLAT_OUT, 0, 1);
            float red = at[at.length - 1];
            float target = speed < 0.01 ? IDLE : IDLE + (red * 0.95f - IDLE) * share;
            rpm += (target - rpm) * 0.1f;
            // the throttle open while the car gathers speed, shut while it coasts or brakes; eased in and out
            float pull = speed > 0.01 && speed - lastSpeed > 0.0004 ? 1 : 0;
            load += (pull - load) * 0.06f;
            lastSpeed = speed;
            // loudest pulling away, a little under while ticking over or coasting; faded in as the engine catches
            float level = Math.min(1, running / 40f) * (0.7f + 0.3f * Math.max(load, Math.min(1, share * 2)));
            int i = 0;
            while (i < at.length - 2 && rpm > at[i + 1]) {
                i++;
            }
            float t = Mth.clamp((rpm - at[i]) / (at[i + 1] - at[i]), 0, 1);
            Map<String, Float> want = new HashMap<>();
            for (int k = i; k <= i + 1; k++) {
                float w = k == i ? 1 - t : t;
                if (k == 0) {
                    want.merge("idle", w, Float::sum);
                } else {
                    want.merge(k + ".on", w * load, Float::sum);
                    want.merge(k + ".off", w * (1 - load), Float::sum);
                }
            }
            for (String band : want.keySet()) {
                for (String layer : new String[] {"engine", "exhaust"}) {
                    String key = band + "." + layer;
                    Layer l = layers.get(key);
                    if ((l == null || l.isStopped()) && want.get(band) > 0.02f) {
                        l = new Layer(this, key, band.equals("idle") ? 0 : Integer.parseInt(band.substring(0, band.indexOf('.'))));
                        layers.put(key, l);
                        Minecraft.getInstance().getSoundManager().play(l);
                    }
                }
            }
            for (Layer l : layers.values()) {
                l.weight = want.getOrDefault(l.band, 0f) * level;
            }
            layers.values().removeIf(Layer::isStopped);
        }

        SoundEvent event(String what, float range) {
            return SoundEvent.createFixedRangeEvent(CarSounds.pz(what), range);
        }
    }

    /** One recorded loop: its loudness is its share of the mix, its pitch the revs over its own. */
    private static final class Layer extends AbstractTickableSoundInstance {
        final Engine engine;
        final String band;
        final int index;
        float weight;
        int silent;

        Layer(Engine engine, String key, int index) {
            super(engine.event("engine." + engine.set + "." + key, 40), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.engine = engine;
            this.band = key.substring(0, key.lastIndexOf('.'));
            this.index = index;
            this.looping = true;
            this.delay = 0;
            this.volume = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            follow();
        }

        private void follow() {
            x = engine.car.getX();
            y = engine.car.getY() + 0.6;
            z = engine.car.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (engine.car.isRemoved() || !engine.car.engineOn() || silent > 40) {
                stop();
                return;
            }
            silent = weight < 0.01f ? silent + 1 : 0;
            follow();
            volume = weight;
            pitch = Mth.clamp(engine.rpm / engine.at[index], 0.5f, 2f);
        }
    }

    private PzEngineSound() {
    }
}
