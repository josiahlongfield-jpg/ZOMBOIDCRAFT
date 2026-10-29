package com.zomboidcraft.creature;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The Harvestman's sounds (made by tools/creatures/harvestman/sounds.py, listed in assets/zomboidcraft/sounds.json):
 * its points tapping down on hard floors, a metal scrape, a hiss when it's disturbed, the screech it strikes with,
 * feeding, bullets ringing off its plates, its shrieks when hurt or a leg is shot off, its death, the far-off call
 * only the one it hunts hears, and (tools/audio/build_harvestman_presence.py) the chittering, creaks and hum that give it
 * away before it's seen.
 */
public final class HarvestmanSounds {
    public static final SoundEvent TAP = register("creature.harvestman.tap");
    public static final SoundEvent SCRAPE = register("creature.harvestman.scrape");
    public static final SoundEvent CLICK = register("creature.harvestman.click");
    public static final SoundEvent HISS = register("creature.harvestman.hiss");
    public static final SoundEvent SCREECH = register("creature.harvestman.screech", 80f);
    public static final SoundEvent FEED = register("creature.harvestman.feed");
    public static final SoundEvent CLANG = register("creature.harvestman.clang");
    public static final SoundEvent HURT = register("creature.harvestman.hurt", 48f);
    public static final SoundEvent LEG_OFF = register("creature.harvestman.leg_off", 48f);
    public static final SoundEvent DEATH = register("creature.harvestman.death", 96f);
    public static final SoundEvent CALL = register("creature.harvestman.call", 160f);
    /** A dry chittering heard through walls; the boards creaking under its weight overhead; the low hum it gives off. */
    public static final SoundEvent CHITTER = register("creature.harvestman.chitter");
    public static final SoundEvent CREAK = register("creature.harvestman.creak");
    public static final SoundEvent HUM = register("creature.harvestman.hum");
    /** Its slow rasping breath, through the door it waits behind. */
    public static final SoundEvent BREATHE = register("creature.harvestman.breathe");

    private static SoundEvent register(String name) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    private static SoundEvent register(String name, float range) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
    }

    public static void register() {
        // loading the class registers the sounds
    }

    private HarvestmanSounds() {
    }
}
