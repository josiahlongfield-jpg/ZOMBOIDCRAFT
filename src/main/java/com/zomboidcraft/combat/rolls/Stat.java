package com.zomboidcraft.combat.rolls;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.combat.Weapons;
import java.util.List;
import java.util.function.ToDoubleFunction;
import net.minecraft.world.item.ItemStack;

/**
 * A stat that rolls inside each model's range. A roll keeps where this copy landed (0 to 1); {@link Rolls#multiplier}
 * turns that into what it does to the underlying number. For the four where a smaller number is better (spread,
 * recoil, aim sway, jam chance) a good roll gives a multiplier under 1.
 */
public enum Stat {
    DAMAGE("Damage", true, c -> c.damageRange),
    /** Bullet spread. */
    ACCURACY("Accuracy", false, c -> c.accuracyRange),
    /** Recoil. */
    STABILITY("Stability", false, c -> c.stabilityRange),
    /** Aim sway on the sights. */
    HANDLING("Handling", false, c -> c.handlingRange),
    /** Jam chance; only for guns that can jam. */
    RELIABILITY("Reliability", false, c -> c.reliabilityRange),
    /** How long it lasts: its condition's full count. */
    DURABILITY("Durability", true, c -> c.durabilityRange),
    /** Swings a second. */
    SPEED("Swing speed", true, c -> c.speedRange),
    REACH("Reach", true, c -> c.reachRange),
    KNOCKDOWN("Knockdown", true, c -> c.knockdownRange);

    private static final List<Stat> GUN = List.of(DAMAGE, ACCURACY, STABILITY, HANDLING, RELIABILITY, DURABILITY);
    private static final List<Stat> GUN_NO_JAMS = List.of(DAMAGE, ACCURACY, STABILITY, HANDLING, DURABILITY);
    private static final List<Stat> MELEE = List.of(DAMAGE, SPEED, REACH, KNOCKDOWN, DURABILITY);

    private final String label;
    private final boolean higherIsBetter;
    private final ToDoubleFunction<ZcConfig.WeaponRolls> range;

    Stat(String label, boolean higherIsBetter, ToDoubleFunction<ZcConfig.WeaponRolls> range) {
        this.label = label;
        this.higherIsBetter = higherIsBetter;
        this.range = range;
    }

    public String label() {
        return label;
    }

    /** Whether a bigger multiplier is the better one (damage), or a smaller (spread, recoil, sway, jams). */
    public boolean higherIsBetter() {
        return higherIsBetter;
    }

    /** How far a model's stat can sit from its average, each way, as a fraction (in /zc config under rolls). */
    public double range() {
        return Math.max(0, range.applyAsDouble(ZcConfig.get().rolls));
    }

    /** The stats that roll on this item, in the order the tooltip and inspect page list them; none if it isn't a weapon. */
    public static List<Stat> of(ItemStack stack) {
        GunType gun = GunType.of(stack);
        if (gun != null) {
            return gun.canJam() ? GUN : GUN_NO_JAMS;
        }
        return Weapons.spec(stack) != null ? MELEE : List.of();
    }
}
