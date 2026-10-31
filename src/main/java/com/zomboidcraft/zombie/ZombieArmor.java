package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.survival.ModDamageTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * What a zombie's clothes stop, and what a fall does to it: Zomboid's Zombie Armor Factor, Maximum Zombie Armor
 * Defense and Zombie Fall Damage Multiplier (zombieLore). A soldier's helmet and flak vest, a firefighter's turnout
 * gear, a police or guard vest and a hard hat each turn part of a blow or a body shot, as their gear does in Zomboid.
 * A bullet in the head is never stopped (Josiah's rule: any shot to the head kills), and fire, blasts and falls
 * aren't either.
 */
public final class ZombieArmor {
    /** Base defense of each outfit's gear, percent, before the armour factor: Zomboid's clothing values, roughly. */
    private static double defense(String outfit) {
        return switch (outfit) {
            case "soldier" -> 25;        // helmet and flak vest
            case "firefighter" -> 20;    // turnout coat and helmet
            case "police" -> 10;         // the vest under the shirt
            case "security" -> 8;
            case "construction" -> 5;    // hard hat and work jacket
            default -> 0;
        };
    }

    /** The share of a blow or body shot this zombie's gear stops, 0 to maxDefense. */
    public static double share(Zombie zombie) {
        ZcConfig.ZombieLore lore = ZcConfig.get().zombieLore;
        double pct = Math.min(lore.maxDefense, defense(ZombieLook.outfit(zombie)) * lore.armorFactor);
        return Math.max(0, pct) / 100.0;
    }

    public static float apply(LivingEntity target, DamageSource source, float amount) {
        if (!(target instanceof Zombie zombie) || !ZombieTypes.isWalker(zombie) || zombie.level().isClientSide()) {
            return amount;
        }
        if (source.is(DamageTypeTags.IS_FALL)) {
            return (float) (amount * ZcConfig.get().zombieLore.fallDamage);
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(ModDamageTypes.BULLET)) {
            return amount;
        }
        if (source.is(ModDamageTypes.BULLET) && Dismember.headStruckNow(zombie)) {
            return amount;
        }
        if (source.getEntity() == null) {
            return amount;      // only blows and shots from someone, not cactus and suffocation
        }
        return (float) (amount * (1 - share(zombie)));
    }

    private ZombieArmor() {
    }
}
