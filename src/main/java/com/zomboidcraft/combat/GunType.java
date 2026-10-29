package com.zomboidcraft.combat;

import com.zomboidcraft.ModItems;
import com.zomboidcraft.ZcConfig;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The guns and what tells them apart: their item, magazine and rounds, their own numbers in settings, their
 * sounds, and their reload timings in ticks (first person animates on the same numbers, see RifleView).
 * A tube gun (the pump shotgun, the lever gun) has no magazine: its shells go in one at a time, and it's pumped (or its
 * lever worked) after each shot.
 * A bolt gun (the hunting rifle) takes a magazine, and its bolt is worked by hand after each shot.
 * A gun that opens to load (the double-barrel broken open, the revolver's cylinder swung out) is loaded by hand like a
 * tube gun, then closed: its spent shells or cases come out as it opens.
 */
public enum GunType {
    ASSAULT_RIFLE("assault_rifle", "5.56mm", 16, 22, 25, 34, 18),
    PISTOL("pistol", "9mm", 12, 16, 18, 24, 12),
    SHOTGUN("shotgun", "12 gauge", 6, 10, 8, 4),
    // its bolt is worked at the pace of its recording (lift, back, home, down over 16 ticks), so an empty reload runs long
    HUNTING_RIFLE("hunting_rifle", ".308", 16, 22, 25, 41, 18, 16, 7),
    // the lever pushed over and the barrels dropped, the empties plucked out, a shell into each chamber, snapped shut
    DOUBLE_BARREL("double_barrel", "12 gauge", 8, 10, 6, true),
    // the latch pushed and the cylinder swung out, the ejector rod pushed to clear the empties, a round thumbed into
    // each chamber, the cylinder snapped shut
    REVOLVER("revolver", ".38 Special", 10, 10, 5, true),
    // the flush rotary magazine thumbed out and a fresh one rocked in; the little handle pulled on an empty gun
    RIMFIRE("rimfire", ".22 LR", 16, 22, 24, 32, 16),
    // the curved magazine rocked in; on an empty gun the cocking handle pulled back and slapped down
    SMG("smg", "9mm", 16, 24, 25, 34, 18),
    // the double's loading, a little quicker in the hand
    SAWN_OFF("sawn_off", "12 gauge", 8, 10, 6, true),
    // the service revolver's loading, with bigger rounds
    MAGNUM("magnum", ".44 Magnum", 10, 10, 5, true),
    // rolled right side up, a round at a time thumbed in through the gate; the lever swung down and up after each shot
    // (heard as it goes down, the empty thrown up and out)
    LEVER_ACTION("lever_action", ".30-30", 6, 11, 6, 3),
    // tipped down and the string drawn back to the latch (the raise, timed to the cock recording: see COCK_AT), then a
    // bolt laid on the rail and slid back into it
    CROSSBOW("crossbow", "crossbow", 40, 10, 0, 0),
    // the assault rifle's reload with a heavier twenty-round .308 magazine
    BATTLE_RIFLE("battle_rifle", ".308", 16, 22, 25, 34, 18),
    // the pistol's reload, smaller
    POCKET_PISTOL("pocket_pistol", ".380 ACP", 12, 16, 18, 24, 12),
    // the pistol's reload with a longer magazine
    MACHINE_PISTOL("machine_pistol", "9mm", 12, 16, 18, 24, 12),
    // the assault rifle's reload; the can is built in
    SUPPRESSED_RIFLE("suppressed_rifle", "5.56mm", 16, 22, 25, 34, 18),
    // the hunting rifle's magazine and bolt
    PRECISION_RIFLE("precision_rifle", ".308", 16, 22, 25, 41, 18, 16, 7),
    // the same, with a heavier magazine seated more slowly
    HEAVY_SNIPER("heavy_sniper", ".50 BMG", 18, 24, 28, 44, 20, 16, 7),
    // six 40mm rounds pushed in one at a time; it fires a grenade round (Thrown) rather than a bullet
    GRENADE_LAUNCHER("grenade_launcher", "40mm", 10, 14, 0, 0);

    private final String id;
    private final String caliber;
    /** The new magazine seats at magIn; on an empty gun the handle (a pistol's slide) is worked at charge. */
    public final int magIn, charge;
    /** A reload onto a round still chambered skips the handle and ends at tacticalDone; one that works it ends at chargeDone. */
    public final int tacticalDone, chargeDone;
    /** Clearing a jam starts here, so the hand has time to reach the handle before it's worked at charge. */
    public final int jamClear;
    /** Loaded a shell at a time: it's raised for raise ticks, then a shell goes in every perShell ticks. */
    public final boolean tube;
    public final int raise, perShell;
    /**
     * Worked by hand after each shot (a pump, or a bolt): how long it takes, and how long after a shot it's heard (a
     * pump as it's worked; a bolt as it's lifted, the empty case coming out as it's run back, CASE_OUT later). 0 for a
     * self-loader.
     */
    public final int cycle, cycleAfterShot;
    /** Ticks from a bolt's lift to the empty case hitting the ground as it's run back (in step with its recording). */
    public static final int CASE_OUT = 6;
    /**
     * A crossbow's draw, in step with its cock recording (one take, the whole draw): ticks into the reload the hand
     * takes the string and the recording starts; then ticks into the recording the creak starts, the stretch is all
     * but done, and the string seats in the latch (its loudest hit, 1.66 s in).
     */
    public static final int COCK_AT = 5;
    public static final float COCK_CREAK = 2f, COCK_STRETCHED = 30f, COCK_SEAT = 33.2f;
    /** The handle is a bolt the shooting hand works: after each shot, and to chamber a round after a fresh magazine. */
    public final boolean bolt;
    /** Opens to load (broken open, or its cylinder swung out), and takes close ticks to shut again after. */
    public final boolean opens;
    public final int close;

    GunType(String id, String caliber, int magIn, int charge, int tacticalDone, int chargeDone, int jamClear) {
        this(id, caliber, magIn, charge, tacticalDone, chargeDone, jamClear, 0, 0);
    }

    /** A magazine gun; with a cycle, a bolt gun. */
    GunType(String id, String caliber, int magIn, int charge, int tacticalDone, int chargeDone, int jamClear,
            int cycle, int cycleAfterShot) {
        this.id = id;
        this.caliber = caliber;
        this.magIn = magIn;
        this.charge = charge;
        this.tacticalDone = tacticalDone;
        this.chargeDone = chargeDone;
        this.jamClear = jamClear;
        this.tube = false;
        this.raise = this.perShell = 0;
        this.cycle = cycle;
        this.cycleAfterShot = cycleAfterShot;
        this.bolt = cycle > 0;
        this.opens = false;
        this.close = 0;
    }

    GunType(String id, String caliber, int raise, int perShell, int pump, int pumpAfterShot) {
        this.id = id;
        this.caliber = caliber;
        this.tube = true;
        this.raise = raise;
        this.perShell = perShell;
        this.cycle = pump;
        this.cycleAfterShot = pumpAfterShot;
        this.bolt = false;
        this.opens = false;
        this.close = 0;
        this.magIn = this.charge = this.tacticalDone = this.chargeDone = this.jamClear = 0;
    }

    /** A gun that opens to load: opened over raise ticks, a round in every perShell, then shut over close. */
    GunType(String id, String caliber, int raise, int perShell, int close, boolean opens) {
        this.id = id;
        this.caliber = caliber;
        this.tube = true;
        this.raise = raise;
        this.perShell = perShell;
        this.cycle = this.cycleAfterShot = 0;
        this.bolt = false;
        this.opens = opens;
        this.close = close;
        this.magIn = this.charge = this.tacticalDone = this.chargeDone = this.jamClear = 0;
    }

    /** Worked by hand after each shot (a pump or a bolt), rather than cycled by the shot itself. */
    public boolean manual() {
        return cycle > 0;
    }

    /** A worn gun can jam: one the shot itself cycles from a magazine (a pump, bolt, double or revolver can't). */
    public boolean canJam() {
        return !manual() && !tube;
    }

    /** Ticks between shots: the setting, but never before a pump or bolt has been worked. */
    public int fireDelay() {
        return Math.max(stats().fireDelayTicks, cycleAfterShot + cycle);
    }

    /** The gun's item id within the mod, which its item models and first-person rig go by. */
    public String id() {
        return id;
    }

    public String caliber() {
        return caliber;
    }

    public Item item() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.ASSAULT_RIFLE;
            case PISTOL -> ModItems.PISTOL;
            case SHOTGUN -> ModItems.SHOTGUN;
            case HUNTING_RIFLE -> ModItems.HUNTING_RIFLE;
            case DOUBLE_BARREL -> ModItems.DOUBLE_BARREL;
            case REVOLVER -> ModItems.REVOLVER;
            case RIMFIRE -> ModItems.RIMFIRE;
            case SMG -> ModItems.SMG;
            case SAWN_OFF -> ModItems.SAWN_OFF;
            case MAGNUM -> ModItems.MAGNUM;
            case LEVER_ACTION -> ModItems.LEVER_ACTION;
            case CROSSBOW -> ModItems.CROSSBOW;
            case BATTLE_RIFLE -> ModItems.BATTLE_RIFLE;
            case POCKET_PISTOL -> ModItems.POCKET_PISTOL;
            case MACHINE_PISTOL -> ModItems.MACHINE_PISTOL;
            case SUPPRESSED_RIFLE -> ModItems.SUPPRESSED_RIFLE;
            case PRECISION_RIFLE -> ModItems.PRECISION_RIFLE;
            case HEAVY_SNIPER -> ModItems.HEAVY_SNIPER;
            case GRENADE_LAUNCHER -> ModItems.GRENADE_LAUNCHER;
        };
    }

    /** The gun's magazine, or null for a tube gun. */
    public @Nullable Item magazine() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.RIFLE_MAGAZINE;
            case PISTOL -> ModItems.PISTOL_MAGAZINE;
            case SHOTGUN, DOUBLE_BARREL, REVOLVER, SAWN_OFF, MAGNUM, LEVER_ACTION, CROSSBOW -> null;
            case HUNTING_RIFLE -> ModItems.HUNTING_RIFLE_MAGAZINE;
            case RIMFIRE -> ModItems.RIMFIRE_MAGAZINE;
            case SMG -> ModItems.SMG_MAGAZINE;
            case BATTLE_RIFLE -> ModItems.BATTLE_RIFLE_MAGAZINE;
            case POCKET_PISTOL -> ModItems.POCKET_PISTOL_MAGAZINE;
            case MACHINE_PISTOL -> ModItems.MACHINE_PISTOL_MAGAZINE;
            case SUPPRESSED_RIFLE -> ModItems.RIFLE_MAGAZINE;
            case PRECISION_RIFLE -> ModItems.PRECISION_RIFLE_MAGAZINE;
            case HEAVY_SNIPER -> ModItems.HEAVY_SNIPER_MAGAZINE;
            case GRENADE_LAUNCHER -> null;
        };
    }

    public Item ammo() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.RIFLE_AMMO;
            case PISTOL, SMG -> ModItems.PISTOL_AMMO;
            case SHOTGUN, DOUBLE_BARREL, SAWN_OFF -> ModItems.SHOTGUN_SHELL;
            case REVOLVER -> ModItems.REVOLVER_AMMO;
            case HUNTING_RIFLE -> ModItems.HUNTING_RIFLE_AMMO;
            case RIMFIRE -> ModItems.RIMFIRE_AMMO;
            case MAGNUM -> ModItems.MAGNUM_AMMO;
            case LEVER_ACTION -> ModItems.LEVER_AMMO;
            case CROSSBOW -> ModItems.CROSSBOW_BOLT;
            case BATTLE_RIFLE -> ModItems.HUNTING_RIFLE_AMMO;
            case POCKET_PISTOL -> ModItems.POCKET_AMMO;
            case MACHINE_PISTOL -> ModItems.PISTOL_AMMO;
            case SUPPRESSED_RIFLE -> ModItems.RIFLE_AMMO;
            case PRECISION_RIFLE -> ModItems.HUNTING_RIFLE_AMMO;
            case HEAVY_SNIPER -> ModItems.HEAVY_AMMO;
            case GRENADE_LAUNCHER -> ModItems.GRENADE_ROUND;
        };
    }

    /**
     * Damage, spread, kick, noise, zoom, range, rate and magazine size: the rifle's in guns, the pistol's in pistol,
     * the shotgun's in shotgun (with its pellets), the hunting rifle's in huntingRifle, the double's in doubleBarrel,
     * the revolver's in revolver, and the rest under their own names.
     */
    public ZcConfig.GunStats stats() {
        return switch (this) {
            case ASSAULT_RIFLE -> ZcConfig.get().guns;
            case PISTOL -> ZcConfig.get().pistol;
            case SHOTGUN -> ZcConfig.get().shotgun;
            case HUNTING_RIFLE -> ZcConfig.get().huntingRifle;
            case DOUBLE_BARREL -> ZcConfig.get().doubleBarrel;
            case REVOLVER -> ZcConfig.get().revolver;
            case RIMFIRE -> ZcConfig.get().rimfire;
            case SMG -> ZcConfig.get().smg;
            case SAWN_OFF -> ZcConfig.get().sawnOff;
            case MAGNUM -> ZcConfig.get().magnum;
            case LEVER_ACTION -> ZcConfig.get().leverAction;
            case CROSSBOW -> ZcConfig.get().crossbow;
            case BATTLE_RIFLE -> ZcConfig.get().battleRifle;
            case POCKET_PISTOL -> ZcConfig.get().pocketPistol;
            case MACHINE_PISTOL -> ZcConfig.get().machinePistol;
            case SUPPRESSED_RIFLE -> ZcConfig.get().suppressedRifle;
            case PRECISION_RIFLE -> ZcConfig.get().precisionRifle;
            case HEAVY_SNIPER -> ZcConfig.get().heavySniper;
            case GRENADE_LAUNCHER -> ZcConfig.get().grenadeLauncher;
        };
    }

    /** Pellets per shot: a shotgun's buckshot, or one bullet. */
    public int pellets() {
        return stats() instanceof ZcConfig.ShotgunStats s ? Math.max(1, s.pellets) : 1;
    }

    public int capacity() {
        return Math.max(1, stats().magazineSize);
    }

    /** The shooter's own shot, or what everyone else hears of it. */
    public SoundEvent shot(boolean suppressed, boolean distant) {
        return switch (this) {
            case ASSAULT_RIFLE -> suppressed ? (distant ? ModSounds.RIFLE_SHOT_SUPPRESSED_DISTANT : ModSounds.RIFLE_SHOT_SUPPRESSED)
                    : distant ? ModSounds.RIFLE_SHOT_DISTANT : ModSounds.RIFLE_SHOT;
            case PISTOL -> suppressed ? (distant ? ModSounds.PISTOL_SHOT_SUPPRESSED_DISTANT : ModSounds.PISTOL_SHOT_SUPPRESSED)
                    : distant ? ModSounds.PISTOL_SHOT_DISTANT : ModSounds.PISTOL_SHOT;
            case SHOTGUN -> distant ? ModSounds.SHOTGUN_SHOT_DISTANT : ModSounds.SHOTGUN_SHOT;
            case HUNTING_RIFLE -> suppressed ? (distant ? ModSounds.HUNTING_RIFLE_SHOT_SUPPRESSED_DISTANT : ModSounds.HUNTING_RIFLE_SHOT_SUPPRESSED)
                    : distant ? ModSounds.HUNTING_RIFLE_SHOT_DISTANT : ModSounds.HUNTING_RIFLE_SHOT;
            case DOUBLE_BARREL -> distant ? ModSounds.DOUBLE_BARREL_SHOT_DISTANT : ModSounds.DOUBLE_BARREL_SHOT;
            case REVOLVER -> distant ? ModSounds.REVOLVER_SHOT_DISTANT : ModSounds.REVOLVER_SHOT;
            case RIMFIRE -> suppressed ? ModSounds.RIMFIRE_SHOT_SUPPRESSED : distant ? ModSounds.RIMFIRE_SHOT_DISTANT : ModSounds.RIMFIRE_SHOT;
            case SMG -> suppressed ? ModSounds.SMG_SHOT_SUPPRESSED : distant ? ModSounds.SMG_SHOT_DISTANT : ModSounds.SMG_SHOT;
            case SAWN_OFF -> distant ? ModSounds.SAWN_OFF_SHOT_DISTANT : ModSounds.SAWN_OFF_SHOT;
            case MAGNUM -> distant ? ModSounds.MAGNUM_SHOT_DISTANT : ModSounds.MAGNUM_SHOT;
            case LEVER_ACTION -> distant ? ModSounds.LEVER_ACTION_SHOT_DISTANT : ModSounds.LEVER_ACTION_SHOT;
            case CROSSBOW -> ModSounds.CROSSBOW_SHOT;
            // wave two: each on its own events (stand-ins until the audio thread's takes land)
            case BATTLE_RIFLE -> suppressed ? (distant ? ModSounds.BATTLE_RIFLE_SHOT_SUPPRESSED_DISTANT : ModSounds.BATTLE_RIFLE_SHOT_SUPPRESSED)
                    : distant ? ModSounds.BATTLE_RIFLE_SHOT_DISTANT : ModSounds.BATTLE_RIFLE_SHOT;
            case POCKET_PISTOL -> suppressed ? (distant ? ModSounds.PISTOL_SHOT_SUPPRESSED_DISTANT : ModSounds.PISTOL_SHOT_SUPPRESSED)
                    : distant ? ModSounds.POCKET_PISTOL_SHOT_DISTANT : ModSounds.POCKET_PISTOL_SHOT;
            case MACHINE_PISTOL -> suppressed ? (distant ? ModSounds.MACHINE_PISTOL_SHOT_SUPPRESSED_DISTANT : ModSounds.MACHINE_PISTOL_SHOT_SUPPRESSED)
                    : distant ? ModSounds.MACHINE_PISTOL_SHOT_DISTANT : ModSounds.MACHINE_PISTOL_SHOT;
            case SUPPRESSED_RIFLE -> distant ? ModSounds.SUPPRESSED_RIFLE_SHOT_DISTANT : ModSounds.SUPPRESSED_RIFLE_SHOT;
            case PRECISION_RIFLE -> suppressed ? (distant ? ModSounds.PRECISION_RIFLE_SHOT_SUPPRESSED_DISTANT : ModSounds.PRECISION_RIFLE_SHOT_SUPPRESSED)
                    : distant ? ModSounds.PRECISION_RIFLE_SHOT_DISTANT : ModSounds.PRECISION_RIFLE_SHOT;
            case HEAVY_SNIPER -> suppressed ? (distant ? ModSounds.HEAVY_SNIPER_SHOT_SUPPRESSED_DISTANT : ModSounds.HEAVY_SNIPER_SHOT_SUPPRESSED)
                    : distant ? ModSounds.HEAVY_SNIPER_SHOT_DISTANT : ModSounds.HEAVY_SNIPER_SHOT;
            case GRENADE_LAUNCHER -> distant ? ModSounds.GRENADE_LAUNCHER_SHOT_DISTANT : ModSounds.GRENADE_LAUNCHER_SHOT;
        };
    }

    public SoundEvent dry() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModSounds.RIFLE_DRY;
            case PISTOL -> ModSounds.PISTOL_DRY;
            case SHOTGUN -> ModSounds.SHOTGUN_DRY;
            case HUNTING_RIFLE -> ModSounds.HUNTING_RIFLE_DRY;
            case DOUBLE_BARREL -> ModSounds.DOUBLE_BARREL_DRY;
            case REVOLVER -> ModSounds.REVOLVER_DRY;
            case RIMFIRE -> ModSounds.RIMFIRE_DRY;
            case SMG -> ModSounds.SMG_DRY;
            case SAWN_OFF -> ModSounds.SAWN_OFF_DRY;
            case MAGNUM -> ModSounds.MAGNUM_DRY;
            case LEVER_ACTION -> ModSounds.LEVER_ACTION_DRY;
            case CROSSBOW -> ModSounds.CROSSBOW_DRY;        // the trigger of an uncocked bow, just a click
            case BATTLE_RIFLE -> ModSounds.RIFLE_DRY;
            case POCKET_PISTOL, MACHINE_PISTOL -> ModSounds.PISTOL_DRY;
            case SUPPRESSED_RIFLE -> ModSounds.RIFLE_DRY;
            case PRECISION_RIFLE, HEAVY_SNIPER -> ModSounds.HUNTING_RIFLE_DRY;
            case GRENADE_LAUNCHER -> ModSounds.SHOTGUN_DRY;
        };
    }

    /** The magazine out, or a gun opened to load. */
    public SoundEvent magOut() {
        return switch (this) {
            case PISTOL -> ModSounds.PISTOL_MAG_OUT;
            case POCKET_PISTOL, MACHINE_PISTOL -> ModSounds.PISTOL_MAG_OUT;
            case PRECISION_RIFLE, HEAVY_SNIPER -> ModSounds.HUNTING_RIFLE_MAG_OUT;
            case HUNTING_RIFLE -> ModSounds.HUNTING_RIFLE_MAG_OUT;
            case DOUBLE_BARREL -> ModSounds.DOUBLE_BARREL_OPEN;
            case REVOLVER -> ModSounds.REVOLVER_OPEN;
            case RIMFIRE -> ModSounds.RIMFIRE_MAG_OUT;
            case SMG -> ModSounds.SMG_MAG_OUT;
            case SAWN_OFF -> ModSounds.SAWN_OFF_OPEN;
            case MAGNUM -> ModSounds.MAGNUM_OPEN;
            case CROSSBOW -> ModSounds.CROSSBOW_COCK;
            default -> ModSounds.RIFLE_MAG_OUT;
        };
    }

    /** The magazine seated, or a shell pushed into the tube. */
    public SoundEvent magIn() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModSounds.RIFLE_MAG_IN;
            case PISTOL -> ModSounds.PISTOL_MAG_IN;
            case SHOTGUN -> ModSounds.SHOTGUN_SHELL_IN;
            case HUNTING_RIFLE -> ModSounds.HUNTING_RIFLE_MAG_IN;
            case DOUBLE_BARREL -> ModSounds.DOUBLE_BARREL_SHELL_IN;
            case REVOLVER -> ModSounds.REVOLVER_ROUND_IN;
            case RIMFIRE -> ModSounds.RIMFIRE_MAG_IN;
            case SMG -> ModSounds.SMG_MAG_IN;
            case SAWN_OFF -> ModSounds.SAWN_OFF_SHELL_IN;
            case MAGNUM -> ModSounds.MAGNUM_ROUND_IN;
            case LEVER_ACTION -> ModSounds.LEVER_ACTION_ROUND_IN;
            case CROSSBOW -> ModSounds.CROSSBOW_LOAD;        // the bolt slid back into the latch
            case BATTLE_RIFLE -> ModSounds.RIFLE_MAG_IN;
            case POCKET_PISTOL, MACHINE_PISTOL -> ModSounds.PISTOL_MAG_IN;
            case SUPPRESSED_RIFLE -> ModSounds.RIFLE_MAG_IN;
            case PRECISION_RIFLE, HEAVY_SNIPER -> ModSounds.HUNTING_RIFLE_MAG_IN;
            case GRENADE_LAUNCHER -> ModSounds.SHOTGUN_SHELL_IN;
        };
    }

    /**
     * The charging handle worked, the slide racked, the pump worked, the bolt worked (lifted, back, home, down), or a
     * gun that opened to load shut again.
     */
    public SoundEvent charged() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModSounds.RIFLE_BOLT;
            case PISTOL -> ModSounds.PISTOL_SLIDE;
            case SHOTGUN -> ModSounds.SHOTGUN_PUMP;
            case HUNTING_RIFLE -> ModSounds.HUNTING_RIFLE_BOLT;
            case DOUBLE_BARREL -> ModSounds.DOUBLE_BARREL_CLOSE;
            case REVOLVER -> ModSounds.REVOLVER_CLOSE;
            case RIMFIRE -> ModSounds.RIMFIRE_COCK;
            case SMG -> ModSounds.SMG_COCK;
            case SAWN_OFF -> ModSounds.SAWN_OFF_CLOSE;
            case MAGNUM -> ModSounds.MAGNUM_CLOSE;
            case LEVER_ACTION -> ModSounds.LEVER_ACTION_LEVER;
            case CROSSBOW -> ModSounds.CROSSBOW_COCK;
            case BATTLE_RIFLE -> ModSounds.RIFLE_BOLT;
            case POCKET_PISTOL, MACHINE_PISTOL -> ModSounds.PISTOL_SLIDE;
            case SUPPRESSED_RIFLE -> ModSounds.RIFLE_BOLT;
            case PRECISION_RIFLE, HEAVY_SNIPER -> ModSounds.HUNTING_RIFLE_BOLT;
            case GRENADE_LAUNCHER -> ModSounds.SHOTGUN_PUMP;
        };
    }

    /** The empties pushed out as it opens (the ejector rod), or null where they just fall out with the opening. */
    public @Nullable SoundEvent eject() {
        return this == REVOLVER ? ModSounds.REVOLVER_EJECT : this == MAGNUM ? ModSounds.MAGNUM_EJECT : null;
    }

    /** The hammer thumbed back as it's brought up onto the sights, or null for a gun with no hammer to cock. */
    public @Nullable SoundEvent hammer() {
        return this == REVOLVER ? ModSounds.REVOLVER_COCK : this == MAGNUM ? ModSounds.MAGNUM_COCK : null;
    }

    /** What it fires, said loosely: shells for a shotgun, rounds for the rest. */
    public String rounds() {
        return ammo() == ModItems.SHOTGUN_SHELL ? "shells" : this == CROSSBOW ? "bolts" : this == GRENADE_LAUNCHER ? "grenades" : "rounds";
    }

    /** Carried in a holster rather than slung: the pistols and revolvers. */
    public boolean handgun() {
        return this == PISTOL || this == REVOLVER || this == MAGNUM || this == POCKET_PISTOL || this == MACHINE_PISTOL;
    }

    /** The gun a stack is, or null if it isn't one. */
    public static @Nullable GunType of(ItemStack stack) {
        return stack.getItem() instanceof RifleItem gun ? gun.type() : null;
    }

    /** The gun a magazine feeds, or null if it isn't one. */
    public static @Nullable GunType ofMagazine(ItemStack stack) {
        return stack.getItem() instanceof MagazineItem mag ? mag.type() : null;
    }

    public static boolean isGun(ItemStack stack) {
        return of(stack) != null;
    }
}
