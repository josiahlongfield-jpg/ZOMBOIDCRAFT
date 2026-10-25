package com.zomboidcraft.mixin;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.CombatEvents;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.skills.Combat;
import com.zomboidcraft.survival.Drinks;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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

    /** Blows on a zombie on the ground land harder; then the weapon skill bonus against zombies. */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float zomboidcraft$weaponSkill(float amount, ServerLevel level, DamageSource source) {
        LivingEntity self = (LivingEntity) (Object) this;
        return Combat.modifyDamage(self, source, CombatEvents.groundAttack(self, source, amount));
    }

    /** A zombie knocked flat or reeling from a blow doesn't move or attack. */
    @Inject(method = "isImmobile", at = @At("RETURN"), cancellable = true)
    private void zomboidcraft$downed(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && (Object) this instanceof Mob mob && Downed.isHeld(mob)) {
            cir.setReturnValue(true);
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
