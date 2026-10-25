package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
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
    /** The engine at cruising revs, looped by the client and faded in over the idle as the car speeds up. */
    public static final SoundEvent DRIVE = register("car.drive", 32f);
    public static final SoundEvent HORN = register("car.horn", 96f);
    public static final SoundEvent CRASH = register("car.crash", 48f);
    public static final SoundEvent DOOR_OPEN = register("car.door.open", 16f);
    public static final SoundEvent DOOR_CLOSE = register("car.door.close", 16f);

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
