package com.zomboidcraft.mixin;

import com.zomboidcraft.skills.Combat;
import com.zomboidcraft.survival.Drinks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /** Weapon skill damage bonus against zombies. */
    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true)
    private float zomboidcraft$weaponSkill(float amount, ServerLevel level, DamageSource source) {
        return Combat.modifyDamage((LivingEntity) (Object) this, source, amount);
    }
}
