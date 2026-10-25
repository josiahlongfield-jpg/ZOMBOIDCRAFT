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
 * A tube gun (the pump shotgun) has no magazine: its shells go in one at a time, and it's pumped after each shot.
 */
public enum GunType {
    ASSAULT_RIFLE("assault_rifle", "5.56mm", 16, 22, 25, 34, 18),
    PISTOL("pistol", "9mm", 12, 16, 18, 24, 12),
    SHOTGUN("shotgun", "12 gauge", 6, 10, 8, 4);

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
    /** A tube gun's pump: how long it takes to work, and how long after a shot it starts. */
    public final int pump, pumpAfterShot;

    GunType(String id, String caliber, int magIn, int charge, int tacticalDone, int chargeDone, int jamClear) {
        this.id = id;
        this.caliber = caliber;
        this.magIn = magIn;
        this.charge = charge;
        this.tacticalDone = tacticalDone;
        this.chargeDone = chargeDone;
        this.jamClear = jamClear;
        this.tube = false;
        this.raise = this.perShell = this.pump = this.pumpAfterShot = 0;
    }

    GunType(String id, String caliber, int raise, int perShell, int pump, int pumpAfterShot) {
        this.id = id;
        this.caliber = caliber;
        this.tube = true;
        this.raise = raise;
        this.perShell = perShell;
        this.pump = pump;
        this.pumpAfterShot = pumpAfterShot;
        this.magIn = this.charge = this.tacticalDone = this.chargeDone = this.jamClear = 0;
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
        };
    }

    /** The gun's magazine, or null for a tube gun. */
    public @Nullable Item magazine() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.RIFLE_MAGAZINE;
            case PISTOL -> ModItems.PISTOL_MAGAZINE;
            case SHOTGUN -> null;
        };
    }

    public Item ammo() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.RIFLE_AMMO;
            case PISTOL -> ModItems.PISTOL_AMMO;
            case SHOTGUN -> ModItems.SHOTGUN_SHELL;
        };
    }

    /**
     * Damage, spread, kick, noise, zoom, range, rate and magazine size: the rifle's in guns, the pistol's in pistol,
     * the shotgun's in shotgun (with its pellets).
     */
    public ZcConfig.GunStats stats() {
        return switch (this) {
            case ASSAULT_RIFLE -> ZcConfig.get().guns;
            case PISTOL -> ZcConfig.get().pistol;
            case SHOTGUN -> ZcConfig.get().shotgun;
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
        };
    }

    public SoundEvent dry() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModSounds.RIFLE_DRY;
            case PISTOL -> ModSounds.PISTOL_DRY;
            case SHOTGUN -> ModSounds.SHOTGUN_DRY;
        };
    }

    public SoundEvent magOut() {
        return this == PISTOL ? ModSounds.PISTOL_MAG_OUT : ModSounds.RIFLE_MAG_OUT;
    }

    /** The magazine seated, or a shell pushed into the tube. */
    public SoundEvent magIn() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModSounds.RIFLE_MAG_IN;
            case PISTOL -> ModSounds.PISTOL_MAG_IN;
            case SHOTGUN -> ModSounds.SHOTGUN_SHELL_IN;
        };
    }

    /** The charging handle worked, the slide racked, or the pump worked. */
    public SoundEvent charged() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModSounds.RIFLE_BOLT;
            case PISTOL -> ModSounds.PISTOL_SLIDE;
            case SHOTGUN -> ModSounds.SHOTGUN_PUMP;
        };
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
