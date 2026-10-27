package com.zomboidcraft.world.cars;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Aircraft sounds, listed in assets/zomboidcraft/sounds.json. The loops are mixed on the client by
 * engine speed, throttle, airspeed and whether you're in the cockpit ({@code AirSound}).
 */
public final class AirSounds {
    public static final SoundEvent JET_START = register("aircraft.jet.start", 64f);
    public static final SoundEvent JET_SHUTDOWN = register("aircraft.jet.shutdown", 64f);
    /** The turbine's whine as heard from the cockpit, pitched by engine speed. */
    public static final SoundEvent JET_IDLE = register("aircraft.jet.idle", 48f);
    /** The thrust roar outside, louder with the throttle. */
    public static final SoundEvent JET_ROAR = register("aircraft.jet.roar", 160f);
    public static final SoundEvent JET_AFTERBURNER = register("aircraft.jet.afterburner", 192f);
    /** A far-off rumble that carries much further than the roar. */
    public static final SoundEvent JET_DISTANT = register("aircraft.jet.distant", 400f);
    public static final SoundEvent HELI_START = register("aircraft.heli.start", 96f);
    public static final SoundEvent HELI_SHUTDOWN = register("aircraft.heli.shutdown", 96f);
    /** Blade slap at one steady rate, pitched by rotor speed. */
    public static final SoundEvent HELI_ROTOR = register("aircraft.heli.rotor", 160f);
    public static final SoundEvent HELI_TURBINE = register("aircraft.heli.turbine", 48f);
    public static final SoundEvent HELI_DISTANT = register("aircraft.heli.distant", 300f);
    /** Air rushing past, louder and higher with airspeed. */
    public static final SoundEvent WIND = register("aircraft.wind", 24f);
    public static final SoundEvent WARN_STALL = register("aircraft.warn.stall", 8f);
    public static final SoundEvent WARN_CAUTION = register("aircraft.warn.caution", 8f);
    public static final SoundEvent TOUCHDOWN = register("aircraft.touchdown", 48f);
    public static final SoundEvent CATAPULT = register("aircraft.catapult", 96f);
    public static final SoundEvent HOOK = register("aircraft.hook", 64f);
    public static final SoundEvent CRASH = register("aircraft.crash", 192f);
    public static final SoundEvent CANOPY = register("aircraft.canopy", 16f);

    public static void register() {
        // Loading the class registers the sounds.
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    private AirSounds() {
    }
}
