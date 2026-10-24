package com.zomboidcraft.skills;

import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Weapon skills: each level adds 6% melee damage against the dead. */
public final class Combat {
    private static final float DAMAGE_PER_LEVEL = 0.06f;

    public static float modifyDamage(LivingEntity target, DamageSource source, float amount) {
        if (!ZombieTypes.isWalker(target) || !(source.getDirectEntity() instanceof ServerPlayer player)
                || source.getEntity() != player) {
            return amount;
        }
        Skill skill = WeaponTypes.skillFor(player.getMainHandItem());
        if (skill == null) {
            return amount;
        }
        Skills.addXp(player, skill, 1);
        return amount * (1f + DAMAGE_PER_LEVEL * Skills.level(player, skill));
    }

    public static void onKill(LivingEntity victim, DamageSource source) {
        if (ZombieTypes.isWalker(victim) && source.getEntity() instanceof ServerPlayer player) {
            Skill skill = WeaponTypes.skillFor(player.getMainHandItem());
            if (skill != null) {
                Skills.addXp(player, skill, 8);
            }
        }
    }

    private Combat() {
    }
}
