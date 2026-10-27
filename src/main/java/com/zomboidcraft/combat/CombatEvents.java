package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.rolls.Rolls;
import com.zomboidcraft.combat.rolls.Stat;
import com.zomboidcraft.skills.Skills;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.zombie.ZombieTypes;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Melee hits on the walking dead: the right impact sound for the weapon, the endurance each
 * swing costs, the chance a blow puts the zombie on the ground (heavy weapons can cripple it),
 * and the extra damage of a blow or a stomp on a zombie already down. Blunt blows knock a zombie
 * back; a blade's cut doesn't.
 */
public final class CombatEvents {
    /** The walker a blade last cut, and the game tick it did: a cut doesn't knock it back (LivingEntityMixin). */
    private static LivingEntity cut;
    private static long cutAt = -1;

    public static void register() {
        AttackEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
            if (player instanceof ServerPlayer serverPlayer && ZombieTypes.isWalker(target)) {
                Stamina.onSwing(serverPlayer);
            }
            return InteractionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damage, blocked) -> {
            if (!blocked && damage > 0 && ZombieTypes.isWalker(entity) && meleeBy(source) instanceof ServerPlayer player) {
                onMeleeHit(player, entity);
            }
        });
    }

    /** The player behind a hand-to-hand blow, or null for anything else (gunshots included). */
    private static ServerPlayer meleeBy(DamageSource source) {
        if (source.getDirectEntity() instanceof ServerPlayer player && source.getEntity() == player
                && !source.is(ModDamageTypes.BULLET)) {
            return player;
        }
        return null;
    }

    /**
     * Damage for a hand-to-hand blow: on a zombie, zombieMelee of it; on one on the ground, a stomp barehanded or a
     * heavier hit with a weapon. Called from the damage mixin before skills and armour.
     */
    public static float meleeDamage(LivingEntity target, DamageSource source, float amount) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        ServerPlayer player = meleeBy(source);
        if (player == null) {
            return amount;
        }
        float tough = ZombieTypes.isWalker(target) ? (float) config.zombieMelee : 1f;
        WeaponSpecs.WeaponSpec spec = Weapons.spec(player.getMainHandItem());
        if (ZombieTypes.isWalker(target) && spec != null && cuts(spec)) {
            cut = target;
            cutAt = target.level().getGameTime();
        }
        if (!config.groundAttacks || !Downed.isLow(target)) {
            return amount * tough;
        }
        Downed.keepDown(target, 8);
        if (player.getMainHandItem().isEmpty()) {
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(), ModSounds.STOMP, SoundSource.PLAYERS,
                    1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
            return (float) Math.max(amount, config.stompDamage) * tough;
        }
        return amount * (float) config.groundAttackMultiplier * tough;
    }

    private static void onMeleeHit(ServerPlayer player, LivingEntity target) {
        ZcConfig.Combat config = ZcConfig.get().combat;
        ItemStack weapon = player.getMainHandItem();
        WeaponSpecs.WeaponSpec spec = Weapons.spec(weapon);
        SoundEvent impact = spec == null ? null : switch (spec.sound()) {
            case BLADE -> ModSounds.HIT_BLADE;
            case PAN -> ModSounds.HIT_PAN;
            case BLUNT -> ModSounds.HIT_BLUNT;
        };
        if (impact != null) {
            target.level().playSound(null, target.getX(), target.getEyeY() - 0.3, target.getZ(), impact, SoundSource.PLAYERS,
                    1f, 0.88f + player.getRandom().nextFloat() * 0.24f);
        }
        if (!target.isAlive() || Downed.isLow(target)) {
            return;
        }
        float chance = spec != null ? spec.knockdown() * (float) Rolls.multiplier(weapon, Stat.KNOCKDOWN) : 0.05f;
        // a blow from behind, or with a run-up, takes them off their feet more often
        Vec3 facing = Vec3.directionFromRotation(0, target.getYRot());
        Vec3 swing = target.position().subtract(player.position()).multiply(1, 0, 1);
        if (swing.lengthSqr() > 1e-6 && facing.dot(swing.normalize()) > 0.4) {
            chance *= 1.6f;
        }
        if (player.isSprinting()) {
            chance *= 1.3f;
        }
        if (spec != null) {
            chance *= 1 + 0.04f * Skills.level(player, spec.skill());
        }
        chance *= (float) config.knockdownChance * Stamina.vigour(player);
        if (player.getRandom().nextFloat() < chance) {
            Downed.knockDown(target);
            boolean heavy = spec != null && (spec.kind() == WeaponSpecs.Kind.HEAVY_BLUNT || spec.kind() == WeaponSpecs.Kind.AXE);
            if (heavy) {
                Downed.crippleChance(target, 1.0);
            }
        } else {
            // the hit still rocks it back for a moment
            boolean blade = spec != null && spec.sound() == WeaponSpecs.Sound.BLADE;
            Downed.stagger(target, blade ? 4 : 8);
        }
    }

    /** A knife, machete, sword or axe: it cuts rather than batters, so the blow doesn't knock a zombie back. */
    private static boolean cuts(WeaponSpecs.WeaponSpec spec) {
        return spec.kind() == WeaponSpecs.Kind.SHORT_BLADE || spec.kind() == WeaponSpecs.Kind.LONG_BLADE
                || spec.kind() == WeaponSpecs.Kind.AXE;
    }

    /**
     * Whether a blade cut this walker this tick, so the knockback from the blow (the hit's own, and the weapon's and a
     * sprint's after it) is left off. Bats, pipes, fists and shoves still knock one back.
     */
    public static boolean cutJustNow(LivingEntity entity) {
        return entity == cut && entity.level().getGameTime() == cutAt;
    }

    private CombatEvents() {
    }
}
