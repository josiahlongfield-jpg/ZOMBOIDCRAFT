package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Weapon sounds: recorded CC0 samples, listed in assets/zomboidcraft/sounds.json and credited in CREDITS.md. */
public final class ModSounds {
    public static final SoundEvent RIFLE_SHOT = register("weapon.rifle.shot");
    public static final SoundEvent RIFLE_DRY = register("weapon.rifle.dry");
    public static final SoundEvent RIFLE_MAG_OUT = register("weapon.rifle.mag_out");
    public static final SoundEvent RIFLE_MAG_IN = register("weapon.rifle.mag_in");
    public static final SoundEvent RIFLE_BOLT = register("weapon.rifle.bolt");
    public static final SoundEvent RIFLE_LOAD_ROUND = register("weapon.rifle.load_round");
    public static final SoundEvent CASING = register("weapon.casing");
    public static final SoundEvent BULLET_FLESH = register("weapon.bullet.flesh");
    public static final SoundEvent BULLET_RICOCHET = register("weapon.bullet.ricochet");
    public static final SoundEvent SWING = register("weapon.melee.swing");
    public static final SoundEvent SWING_HEAVY = register("weapon.melee.swing_heavy");
    public static final SoundEvent HIT_BLUNT = register("weapon.melee.hit_blunt");
    public static final SoundEvent HIT_BLADE = register("weapon.melee.hit_blade");
    public static final SoundEvent HIT_PAN = register("weapon.melee.hit_pan");
    public static final SoundEvent STOMP = register("weapon.melee.stomp");
    public static final SoundEvent SHOVE = register("weapon.melee.shove");
    public static final SoundEvent GRAB = register("zombie.grab");

    private static SoundEvent register(String name) {
        Identifier id = ZomboidCraft.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void register() {
        // loading the class registers the sounds
    }

    private ModSounds() {
    }
}
