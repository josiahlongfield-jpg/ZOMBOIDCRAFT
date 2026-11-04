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
 * (sports, wagon, offroad or van, from the local PZ car sounds pack). The note is the settled idle cut from
 * the end of the engine's own start recording (pzsounds.py NOTE_CUT), so there's no cut from starting to
 * ticking over to pulling away: pitched from 1 at rest up to 1.9 flat out and back as it slows.
 */
final class PzEngineSound {
    private static final double FLAT_OUT = 1.1;
    private static final Map<CarProp, Engine> ENGINES = new WeakHashMap<>();

    /** Whether this car has Zomboid's engine and the pack that holds it. */
    static boolean has(CarProp car) {
        CarModels.Model m = CarModels.get(car.model());
        String set = m == null || m.sounds == null ? null : m.sounds.get("engine");
        return set != null
                && Minecraft.getInstance().getSoundManager().getSoundEvent(CarSounds.pz("engine." + set + ".note")) != null;
    }

    /** Called each tick the car's engine runs. */
    static void keep(CarProp car) {
        ENGINES.computeIfAbsent(car, Engine::new).tick();
    }

    private static final class Engine {
        final CarProp car;
        final String set;
        final Map<String, Layer> layers = new HashMap<>();
        float pitchNow = 1;
        double speedNow;
        int lastTick = -100, running;

        Engine(CarProp car) {
            this.car = car;
            this.set = CarModels.get(car.model()).sounds.get("engine");
        }

        void tick() {
            if (car.tickCount == lastTick) {
                return;
            }
            running = car.tickCount - lastTick > 2 ? 0 : running + 1;      // a fresh start after a gap
            lastTick = car.tickCount;
            // one steady note (Josiah): the settled idle cut from the end of this engine's own start recording
            // (pz.engine.<set>.note), so the start runs straight into it; its pitch rises smoothly with speed
            // and falls as the car slows. It fades in under the start's own tail as the engine catches.
            // the speed is smoothed over about half a second before it sets the pitch, so the tick-to-tick wobble in
            // the car's movement doesn't make the note hunt up and down (Josiah 2026-11-04)
            double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
            speedNow += (speed - speedNow) * 0.1;
            float share = (float) Mth.clamp(speedNow / FLAT_OUT, 0, 1);
            pitchNow += (1 + 0.9f * share - pitchNow) * 0.08f;
            Layer l = layers.get("note");
            if (l == null || l.isStopped()) {
                l = new Layer(this, "note");
                layers.put("note", l);
                Minecraft.getInstance().getSoundManager().play(l);
            }
            // louder as it pulls away (Josiah 2026-11-04): ticking over at 0.6, full volume from about half speed
            l.weight = Math.min(1, running / 40f) * (0.6f + 0.4f * Math.min(1, share * 2));
            layers.values().removeIf(Layer::isStopped);
        }

        SoundEvent event(String what, float range) {
            return SoundEvent.createFixedRangeEvent(CarSounds.pz(what), range);
        }
    }

    /** One recorded loop: its loudness is its share of the mix, its pitch the revs over its own. */
    private static final class Layer extends AbstractTickableSoundInstance {
        final Engine engine;
        float weight;
        int silent;

        Layer(Engine engine, String key) {
            super(engine.event("engine." + engine.set + "." + key, 40), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            this.engine = engine;
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
            pitch = Mth.clamp(engine.pitchNow, 0.5f, 2f);
        }
    }

    private PzEngineSound() {
    }
}
