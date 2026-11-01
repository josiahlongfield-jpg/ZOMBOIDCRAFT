package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Car sounds: recorded samples, listed in assets/zomboidcraft/sounds.json and credited in CREDITS.md. */
public final class CarSounds {
    /** Key, crank, and the engine catching. */
    public static final SoundEvent START = register("car.start", 32f);
    /** Cranking that never catches: a car that won't start. */
    public static final SoundEvent STALL = register("car.stall", 32f);
    /** The running engine, looped by the client with its pitch rising with speed. */
    public static final SoundEvent IDLE = register("car.idle", 32f);
    public static final SoundEvent HORN = register("car.horn", 96f);
    public static final SoundEvent CRASH = register("car.crash", 48f);
    /** An emergency vehicle's wail, a 6 s loop, and a car alarm's whoop-and-chirp, a 4 s loop (AUDIO, Josiah's picks). */
    public static final SoundEvent SIREN = register("car.siren", 160f);
    public static final SoundEvent ALARM = register("car.alarm", 160f);
    public static final SoundEvent DOOR_OPEN = register("car.door.open", 16f);
    public static final SoundEvent DOOR_CLOSE = register("car.door.close", 16f);

    /**
     * Zomboid's own recording of a sound for this model, from the local "ZomboidCraft PZ Car Sounds" pack: the
     * pz.&lt;what&gt; event with % replaced by the model's set of that kind (models.json "sounds"), carrying our
     * sound's range. Models without that kind of set, and our own cars, get our sound.
     */
    public static Holder<SoundEvent> of(CarModels.Model m, String kind, String what, SoundEvent ours) {
        String set = m == null || m.sounds == null ? null : m.sounds.get(kind);
        if (set == null) {
            return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(ours);
        }
        return Holder.direct(SoundEvent.createFixedRangeEvent(pz(what.replace("%", set)), ours.fixedRange().orElse(16f)));
    }

    /** The id of a pz.* event in the local pack. */
    public static Identifier pz(String what) {
        return ZomboidCraft.id("pz." + what);
    }

    public static void register() {
        // Loading the class registers the sounds.
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    private CarSounds() {
    }
}
