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
    public static final SoundEvent PISTOL_SHOT_SUPPRESSED_DISTANT = register("weapon.pistol.shot_suppressed_distant", 40f);
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
    /**
     * The double-barrel: both barrels' boom like the pump's, the top lever thrown and the barrels dropping open, a shell
     * thumbed into a chamber, the action snapped shut.
     */
    public static final SoundEvent DOUBLE_BARREL_SHOT = register("weapon.double_barrel.shot");
    public static final SoundEvent DOUBLE_BARREL_SHOT_DISTANT = register("weapon.double_barrel.shot_distant", 160f);
    public static final SoundEvent DOUBLE_BARREL_DRY = register("weapon.double_barrel.dry");
    public static final SoundEvent DOUBLE_BARREL_OPEN = register("weapon.double_barrel.open");
    public static final SoundEvent DOUBLE_BARREL_CLOSE = register("weapon.double_barrel.close");
    public static final SoundEvent DOUBLE_BARREL_SHELL_IN = register("weapon.double_barrel.shell_in");
    /**
     * The revolver: a .38's bark heard out to 100 blocks, the hammer falling on an empty chamber, the latch and the
     * cylinder swinging out, the ejector rod pushing the empties out, a round dropped into a chamber, the cylinder
     * snapped shut. The hammer cocked is there for single action.
     */
    public static final SoundEvent REVOLVER_SHOT = register("weapon.revolver.shot");
    public static final SoundEvent REVOLVER_SHOT_DISTANT = register("weapon.revolver.shot_distant", 100f);
    public static final SoundEvent REVOLVER_DRY = register("weapon.revolver.dry");
    public static final SoundEvent REVOLVER_COCK = register("weapon.revolver.cock");
    public static final SoundEvent REVOLVER_OPEN = register("weapon.revolver.open");
    public static final SoundEvent REVOLVER_EJECT = register("weapon.revolver.eject");
    public static final SoundEvent REVOLVER_ROUND_IN = register("weapon.revolver.round_in");
    public static final SoundEvent REVOLVER_CLOSE = register("weapon.revolver.close");
    /**
     * The hunting rifle: a .308's hard crack heard out to 176 blocks, and its bolt worked through, lift to lock (about
     * a third of a second).
     */
    public static final SoundEvent HUNTING_RIFLE_SHOT = register("weapon.hunting_rifle.shot");
    public static final SoundEvent HUNTING_RIFLE_SHOT_DISTANT = register("weapon.hunting_rifle.shot_distant", 176f);
    /** Through a suppressor: a .308 is still a heavy thump, heard out to 48 blocks. */
    public static final SoundEvent HUNTING_RIFLE_SHOT_SUPPRESSED = register("weapon.hunting_rifle.shot_suppressed");
    public static final SoundEvent HUNTING_RIFLE_SHOT_SUPPRESSED_DISTANT = register("weapon.hunting_rifle.shot_suppressed_distant", 48f);
    public static final SoundEvent HUNTING_RIFLE_DRY = register("weapon.hunting_rifle.dry");
    public static final SoundEvent HUNTING_RIFLE_MAG_OUT = register("weapon.hunting_rifle.mag_out");
    public static final SoundEvent HUNTING_RIFLE_MAG_IN = register("weapon.hunting_rifle.mag_in");
    public static final SoundEvent HUNTING_RIFLE_BOLT = register("weapon.hunting_rifle.bolt");
    /**
     * Wave one, on stand-ins until their own recordings land (the audio thread swaps the sounds.json entries): the .22
     * is a light crack, the sawn-off and the magnum heavy, the lever gun a .30-30's bark, the crossbow just its string.
     */
    public static final SoundEvent RIMFIRE_SHOT = register("weapon.rimfire.shot");
    public static final SoundEvent RIMFIRE_SHOT_DISTANT = register("weapon.rimfire.shot_distant", 64f);
    public static final SoundEvent RIMFIRE_SHOT_SUPPRESSED = register("weapon.rimfire.shot_suppressed", 24f);
    public static final SoundEvent RIMFIRE_DRY = register("weapon.rimfire.dry");
    public static final SoundEvent RIMFIRE_MAG_OUT = register("weapon.rimfire.mag_out");
    public static final SoundEvent RIMFIRE_MAG_IN = register("weapon.rimfire.mag_in");
    public static final SoundEvent RIMFIRE_COCK = register("weapon.rimfire.cock");
    public static final SoundEvent SMG_SHOT = register("weapon.smg.shot");
    public static final SoundEvent SMG_SHOT_DISTANT = register("weapon.smg.shot_distant", 96f);
    public static final SoundEvent SMG_SHOT_SUPPRESSED = register("weapon.smg.shot_suppressed", 40f);
    public static final SoundEvent SMG_DRY = register("weapon.smg.dry");
    public static final SoundEvent SMG_MAG_OUT = register("weapon.smg.mag_out");
    public static final SoundEvent SMG_MAG_IN = register("weapon.smg.mag_in");
    public static final SoundEvent SMG_COCK = register("weapon.smg.cock");
    public static final SoundEvent SAWN_OFF_SHOT = register("weapon.sawn_off.shot");
    public static final SoundEvent SAWN_OFF_SHOT_DISTANT = register("weapon.sawn_off.shot_distant", 160f);
    public static final SoundEvent SAWN_OFF_DRY = register("weapon.sawn_off.dry");
    public static final SoundEvent SAWN_OFF_OPEN = register("weapon.sawn_off.open");
    public static final SoundEvent SAWN_OFF_SHELL_IN = register("weapon.sawn_off.shell_in");
    public static final SoundEvent SAWN_OFF_CLOSE = register("weapon.sawn_off.close");
    public static final SoundEvent MAGNUM_SHOT = register("weapon.magnum.shot");
    public static final SoundEvent MAGNUM_SHOT_DISTANT = register("weapon.magnum.shot_distant", 128f);
    public static final SoundEvent MAGNUM_DRY = register("weapon.magnum.dry");
    public static final SoundEvent MAGNUM_COCK = register("weapon.magnum.cock");
    public static final SoundEvent MAGNUM_OPEN = register("weapon.magnum.open");
    public static final SoundEvent MAGNUM_EJECT = register("weapon.magnum.eject");
    public static final SoundEvent MAGNUM_ROUND_IN = register("weapon.magnum.round_in");
    public static final SoundEvent MAGNUM_CLOSE = register("weapon.magnum.close");
    public static final SoundEvent LEVER_ACTION_SHOT = register("weapon.lever_action.shot");
    public static final SoundEvent LEVER_ACTION_SHOT_DISTANT = register("weapon.lever_action.shot_distant", 160f);
    public static final SoundEvent LEVER_ACTION_DRY = register("weapon.lever_action.dry");
    public static final SoundEvent LEVER_ACTION_LEVER = register("weapon.lever_action.lever");
    public static final SoundEvent LEVER_ACTION_ROUND_IN = register("weapon.lever_action.round_in");
    public static final SoundEvent CROSSBOW_SHOT = register("weapon.crossbow.shot", 16f);
    public static final SoundEvent CROSSBOW_COCK = register("weapon.crossbow.cock");
    public static final SoundEvent CROSSBOW_DRY = register("weapon.crossbow.dry");
    public static final SoundEvent CROSSBOW_LOAD = register("weapon.crossbow.load");
    public static final SoundEvent CROSSBOW_HIT = register("weapon.crossbow.hit", 24f);
    public static final SoundEvent CROSSBOW_HIT_BLOCK = register("weapon.crossbow.hit_block", 24f);
    public static final SoundEvent CASING = register("weapon.casing");
    public static final SoundEvent BULLET_FLESH = register("weapon.bullet.flesh", 64f);  // Josiah: hear your hits from across the street
    public static final SoundEvent BULLET_RICOCHET = register("weapon.bullet.ricochet");
    /** A bullet striking each kind of surface (Surface): heard a street or so away. */
    public static final SoundEvent IMPACT_GROUND = register("weapon.bullet.impact.ground", 40f);
    public static final SoundEvent IMPACT_CONCRETE = register("weapon.bullet.impact.concrete", 40f);
    public static final SoundEvent IMPACT_BRICK = register("weapon.bullet.impact.brick", 40f);
    public static final SoundEvent IMPACT_WOOD = register("weapon.bullet.impact.wood", 40f);
    public static final SoundEvent IMPACT_METAL = register("weapon.bullet.impact.metal", 48f);
    public static final SoundEvent IMPACT_CAR = register("weapon.bullet.impact.car", 48f);
    public static final SoundEvent IMPACT_GLASS = register("weapon.bullet.impact.glass", 48f);
    public static final SoundEvent IMPACT_SOFT = register("weapon.bullet.impact.soft", 24f);
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
    /**
     * Radios (made in tools/audio/build_radio_sounds.py): the click and short hiss on everyone else's radio when someone
     * keys up, the burst of static when they let go, the talk button's own click, the channel knob's click and beep,
     * and switching one on and off.
     */
    public static final SoundEvent RADIO_KEY_UP = register("item.radio.key_up");
    public static final SoundEvent RADIO_SQUELCH = register("item.radio.squelch");
    public static final SoundEvent RADIO_PTT = register("item.radio.ptt");
    public static final SoundEvent RADIO_CHANNEL = register("item.radio.channel");
    public static final SoundEvent RADIO_POWER_ON = register("item.radio.power_on");
    public static final SoundEvent RADIO_POWER_OFF = register("item.radio.power_off");

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
