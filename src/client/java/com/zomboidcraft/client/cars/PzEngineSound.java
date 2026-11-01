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
 * A converted Workshop car's engine: one steady note (Josiah, 2026-11-01), the model's recorded engine
 * (sports, wagon, offroad or van, from the local PZ car sounds pack) as the on-load take at the revs that
 * suit it, engine-bay and exhaust layers together, pitched smoothly up with speed and down as it slows.
 */
final class PzEngineSound {
    /** The revs each band was recorded at (pzsounds.py ENGINES), above the idle loop at IDLE. */
    private static final Map<String, float[]> BANDS = Map.of(
            "sports", new float[] {1000, 2000, 3000, 4000, 5000, 6000},
            "wagon", new float[] {1000, 2000, 3000, 4000},
            "offroad", new float[] {1000, 1600, 2200, 2800, 3400, 4000, 4600, 5200},
            "van", new float[] {1000, 1750, 2500, 3250, 4000});
    private static final float IDLE = 800;
    /** Each engine's note: the band whose on-load take carries it best (3000 rpm sports, 2800 offroad, 2000 wagon, 2500 van). */
    private static final Map<String, Integer> NOTE = Map.of("sports", 3, "offroad", 4, "wagon", 2, "van", 3);
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
        /** Which recorded band is the car's note: the model's "note" if set, else the take that suits its engine. */
        final int note;
        float rpm;
        int lastTick = -1;

        Engine(CarProp car) {
            this.car = car;
            this.set = CarModels.get(car.model()).sounds.get("engine");
            float[] bands = BANDS.get(set);
            at = new float[bands.length + 1];
            at[0] = IDLE;
            System.arraycopy(bands, 0, at, 1, bands.length);
            String n = CarModels.get(car.model()).sounds.get("note");
            note = Mth.clamp(n != null ? Integer.parseInt(n) : NOTE.getOrDefault(set, 2), 1, bands.length);
            rpm = at[note] * 0.62f;
        }

        void tick() {
            if (car.tickCount == lastTick) {
                return;
            }
            lastTick = car.tickCount;
            // one steady note, no gears (Josiah): the engine's own on-load take at the revs that suit it, its
            // pitch rising smoothly from ticking over to flat out and falling again as the car slows
            double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
            float share = (float) Mth.clamp(speed / FLAT_OUT, 0, 1);
            float target = 0.62f + 0.98f * share;
            rpm += (target * at[note] - rpm) * 0.12f;
            for (String layer : new String[] {"engine", "exhaust"}) {
                String key = note + ".on." + layer;
                Layer l = layers.get(key);
                if (l == null || l.isStopped()) {
                    l = new Layer(this, key, note);
                    layers.put(key, l);
                    Minecraft.getInstance().getSoundManager().play(l);
                }
                l.weight = 0.7f + 0.3f * share;
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
