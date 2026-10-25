package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Weapon and zombie sounds: recorded CC0 samples, listed in assets/zomboidcraft/sounds.json and credited in CREDITS.md. */
public final class ModSounds {
    /** The shooter's own shot, played on their client. */
    public static final SoundEvent RIFLE_SHOT = register("weapon.rifle.shot");
    /** What everyone else hears of a shot, out to 128 blocks. */
    public static final SoundEvent RIFLE_SHOT_DISTANT = register("weapon.rifle.shot_distant", 128f);
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
    /** A walker's scream when it first spots someone. */
    public static final SoundEvent ZOMBIE_ALERT = register("zombie.alert", 32f);
    /** Snarls while it chases. */
    public static final SoundEvent ZOMBIE_CHASE = register("zombie.chase", 24f);

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

    private ModSounds() {
    }
}
