package com.zomboidcraft.mixin;

import com.zomboidcraft.world.trains.TrainPhysics;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Legs on a moving train only walk for the steps their owner takes, not the ride. */
@Mixin(LivingEntity.class)
public abstract class LivingTrainMixin {
    @ModifyVariable(method = "updateWalkAnimation", at = @At("HEAD"), argsOnly = true)
    private float zomboidcraft$trainWalk(float moved) {
        return TrainPhysics.walkDistance((LivingEntity) (Object) this, moved);
    }
}
