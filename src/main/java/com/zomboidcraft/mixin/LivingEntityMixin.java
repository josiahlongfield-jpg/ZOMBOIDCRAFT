package com.zomboidcraft.mixin;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.CombatEvents;
import com.zomboidcraft.combat.Deaths;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.skills.Combat;
import com.zomboidcraft.survival.Drinks;
import com.zomboidcraft.zombie.Corpses;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Shadow
    protected ItemStack useItem;

    /** Drinking and eating restore thirst. Runs before the item is used up. */
    @Inject(method = "completeUsingItem", at = @At("HEAD"))
    private void zomboidcraft$quenchThirst(CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && !useItem.isEmpty()) {
            Drinks.onConsumed(player, useItem.copy());
        }
    }

    /**
     * Blows on zombies are cut, and land harder on one on the ground; then the weapon skill bonus against zombies; and
     * only a shot to the head kills one (Dismember.onlyTheHead).
     */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float zomboidcraft$weaponSkill(float amount, ServerLevel level, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        return Dismember.onlyTheHead(self, source, Combat.modifyDamage(self, source, CombatEvents.meleeDamage(self, source, amount)));
    }

    /** A blade cuts a zombie without knocking it back; only blunt blows do (CombatEvents.cutJustNow). */
    @Inject(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$noKnockbackFromCuts(double strength, double x, double z, DamageSource source, float amount,
            boolean blocked, CallbackInfo ci) {
        if (CombatEvents.cutJustNow((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /** The same for the weapon's and a sprint's extra knockback, where it comes this way. */
    @Inject(method = "knockback(DDD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$noExtraKnockbackFromCuts(double strength, double x, double z, CallbackInfo ci) {
        if (CombatEvents.cutJustNow((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /** A zombie knocked flat or reeling from a blow doesn't move or attack. */
    @Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$downed(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (Object) this instanceof Mob mob && Downed.isHeld(mob)) {
            cir.setReturnValue(true);
        }
    }

    /** A walker's death: how it goes down, for every client to draw the same fall (combat.Deaths). */
    @Inject(method = "die", at = @At("HEAD"))
    private void zomboidcraft$died(DamageSource source, CallbackInfo ci) {
        if ((Object) this instanceof Zombie zombie && !zombie.level().isClientSide() && ZombieTypes.isWalker(zombie)) {
            Deaths.died(zombie, source);
        }
    }

    /** A dead walker falls, then its body stays where it lies (zombie.Corpses), instead of a tip-over and a puff of smoke. */
    @Inject(method = "tickDeath", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$fall(CallbackInfo ci) {
        if (Corpses.holds((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }

    /** Deeper, more human groans for the walking dead. */
    @Inject(method = "getVoicePitch", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$deeperGroans(CallbackInfoReturnable<Float> cir) {
        if (ZombieTypes.isWalker((LivingEntity) (Object) this)) {
            cir.setReturnValue(cir.getReturnValue() * (float) ZcConfig.get().look.voicePitch);
        }
    }
}
