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
 */
public enum GunType {
    ASSAULT_RIFLE("assault_rifle", "5.56mm", 16, 22, 25, 34, 18),
    PISTOL("pistol", "9mm", 12, 16, 18, 24, 12);

    private final String id;
    private final String caliber;
    /** The new magazine seats at magIn; on an empty gun the handle (a pistol's slide) is worked at charge. */
    public final int magIn, charge;
    /** A reload onto a round still chambered skips the handle and ends at tacticalDone; one that works it ends at chargeDone. */
    public final int tacticalDone, chargeDone;
    /** Clearing a jam starts here, so the hand has time to reach the handle before it's worked at charge. */
    public final int jamClear;

    GunType(String id, String caliber, int magIn, int charge, int tacticalDone, int chargeDone, int jamClear) {
        this.id = id;
        this.caliber = caliber;
        this.magIn = magIn;
        this.charge = charge;
        this.tacticalDone = tacticalDone;
        this.chargeDone = chargeDone;
        this.jamClear = jamClear;
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
        };
    }

    public Item magazine() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.RIFLE_MAGAZINE;
            case PISTOL -> ModItems.PISTOL_MAGAZINE;
        };
    }

    public Item ammo() {
        return switch (this) {
            case ASSAULT_RIFLE -> ModItems.RIFLE_AMMO;
            case PISTOL -> ModItems.PISTOL_AMMO;
        };
    }

    /** Damage, spread, kick, noise, zoom, range, rate and magazine size: the rifle's in guns, the pistol's in pistol. */
    public ZcConfig.GunStats stats() {
        return switch (this) {
            case ASSAULT_RIFLE -> ZcConfig.get().guns;
            case PISTOL -> ZcConfig.get().pistol;
        };
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
        };
    }

    public SoundEvent dry() {
        return this == PISTOL ? ModSounds.PISTOL_DRY : ModSounds.RIFLE_DRY;
    }

    public SoundEvent magOut() {
        return this == PISTOL ? ModSounds.PISTOL_MAG_OUT : ModSounds.RIFLE_MAG_OUT;
    }

    public SoundEvent magIn() {
        return this == PISTOL ? ModSounds.PISTOL_MAG_IN : ModSounds.RIFLE_MAG_IN;
    }

    /** The charging handle worked, or the slide racked. */
    public SoundEvent charged() {
        return this == PISTOL ? ModSounds.PISTOL_SLIDE : ModSounds.RIFLE_BOLT;
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
