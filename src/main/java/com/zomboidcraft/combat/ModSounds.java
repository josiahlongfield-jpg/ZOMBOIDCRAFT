package com.zomboidcraft.combat;

import com.zomboidcraft.ZomboidCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/** Weapon and zombie sounds: recorded samples (CC0 or royalty-free), listed in assets/zomboidcraft/sounds.json and credited in CREDITS.md. */
public final class ModSounds {
    /** The shooter's own shot, played on their client. */
    public static final SoundEvent RIFLE_SHOT = register("weapon.rifle.shot");
    /** What everyone else hears of a shot, out to 128 blocks. */
    public static final SoundEvent RIFLE_SHOT_DISTANT = register("weapon.rifle.shot_distant", 128f);
    /** The same pair through a suppressor (Sonniss recordings), heard out to 40 blocks. */
    public static final SoundEvent RIFLE_SHOT_SUPPRESSED = register("weapon.rifle.shot_suppressed");
    public static final SoundEvent RIFLE_SHOT_SUPPRESSED_DISTANT = register("weapon.rifle.shot_suppressed_distant", 40f);
    /** The pistol's shots: sharper and a little quieter than the rifle's, heard out to 96 blocks. */
    public static final SoundEvent PISTOL_SHOT = register("weapon.pistol.shot");
    public static final SoundEvent PISTOL_SHOT_DISTANT = register("weapon.pistol.shot_distant", 96f);
    public static final SoundEvent PISTOL_SHOT_SUPPRESSED = register("weapon.pistol.shot_suppressed");
    public static final SoundEvent PISTOL_SHOT_SUPPRESSED_DISTANT = register("weapon.pistol.shot_suppressed_distant", 24f);
    public static final SoundEvent RIFLE_DRY = register("weapon.rifle.dry");
    public static final SoundEvent RIFLE_MAG_OUT = register("weapon.rifle.mag_out");
    public static final SoundEvent RIFLE_MAG_IN = register("weapon.rifle.mag_in");
    public static final SoundEvent RIFLE_BOLT = register("weapon.rifle.bolt");
    public static final SoundEvent RIFLE_LOAD_ROUND = register("weapon.rifle.load_round");
    public static final SoundEvent PISTOL_DRY = register("weapon.pistol.dry");
    public static final SoundEvent PISTOL_MAG_OUT = register("weapon.pistol.mag_out");
    public static final SoundEvent PISTOL_MAG_IN = register("weapon.pistol.mag_in");
    /** The slide racked to chamber a round or clear a jam. */
    public static final SoundEvent PISTOL_SLIDE = register("weapon.pistol.slide");
    /** The pump shotgun: a deep, heavy boom heard out to 160 blocks, the pump worked, a shell pushed into the tube. */
    public static final SoundEvent SHOTGUN_SHOT = register("weapon.shotgun.shot");
    public static final SoundEvent SHOTGUN_SHOT_DISTANT = register("weapon.shotgun.shot_distant", 160f);
    public static final SoundEvent SHOTGUN_DRY = register("weapon.shotgun.dry");
    public static final SoundEvent SHOTGUN_PUMP = register("weapon.shotgun.pump");
    public static final SoundEvent SHOTGUN_SHELL_IN = register("weapon.shotgun.shell_in");
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
    /** Crowd beds that swell with the horde round you: a murmur of nearby walkers, a roar of the ones after you. */
    public static final SoundEvent ZOMBIE_HORDE_DORMANT = register("zombie.horde_dormant");
    public static final SoundEvent ZOMBIE_HORDE_ANGRY = register("zombie.horde_angry");

    /** Nailing a board up; heard a fair way off, like the noise zombies follow. */
    public static final SoundEvent HAMMER_NAIL = register("item.hammer.nail", 32f);
    public static final SoundEvent BANDAGE = register("item.bandage");

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
