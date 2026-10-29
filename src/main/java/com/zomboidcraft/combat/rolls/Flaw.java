package com.zomboidcraft.combat.rolls;

import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.WeaponSpecs;
import com.zomboidcraft.combat.Weapons;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/**
 * What makes a find "not so great": a fault a real gun or tool picks up from hard use and neglect. Each one worsens one
 * stat by rolls.flawPenalty; the gunsmith bench will fix them with the right part. Stored as bits by ordinal, so new
 * flaws go on the end.
 */
public enum Flaw {
    PITTED_BORE("Pitted bore", "Wider spread", Stat.ACCURACY, Flaw::gun),
    CRACKED_STOCK("Cracked stock", "More recoil", Stat.STABILITY, s -> gun(s) && !handgun(s)),
    CRACKED_GRIP("Cracked grip", "More recoil", Stat.STABILITY, Flaw::handgun),
    WEAK_SPRING("Weak magazine spring", "Jams more often", Stat.RELIABILITY, s -> gun(s) && GunType.of(s).canJam()),
    WORN_SEAR("Worn sear", "Heavy trigger, shakier aim", Stat.HANDLING, Flaw::gun),
    CHIPPED_EDGE("Chipped edge", "Hits softer", Stat.DAMAGE, s -> melee(s, true)),
    DENTED_HEAD("Dented head", "Hits softer", Stat.DAMAGE, s -> melee(s, false)),
    LOOSE_HANDLE("Loose handle", "Wears out faster", Stat.DURABILITY, s -> Weapons.spec(s) != null);

    private final String label;
    private final String effect;
    private final Stat stat;
    private final Predicate<ItemStack> fits;

    Flaw(String label, String effect, Stat stat, Predicate<ItemStack> fits) {
        this.label = label;
        this.effect = effect;
        this.stat = stat;
        this.fits = fits;
    }

    public String label() {
        return label;
    }

    /** What it does, in a few words. */
    public String effect() {
        return effect;
    }

    public Stat stat() {
        return stat;
    }

    public boolean fits(ItemStack stack) {
        return fits.test(stack);
    }

    public int bit() {
        return 1 << ordinal();
    }

    private static boolean gun(ItemStack stack) {
        return GunType.of(stack) != null;
    }

    private static boolean handgun(ItemStack stack) {
        GunType type = GunType.of(stack);
        return type != null && type.handgun();
    }

    private static boolean melee(ItemStack stack, boolean blade) {
        WeaponSpecs.WeaponSpec spec = Weapons.spec(stack);
        return spec != null && (spec.sound() == WeaponSpecs.Sound.BLADE) == blade;
    }
}
