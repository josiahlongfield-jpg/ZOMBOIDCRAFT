package com.zomboidcraft.mixin;

import com.zomboidcraft.world.lifts.LiftPhysics;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every entity can ride a lift car (carried up and down with it, stood on its floor) and an escalator's steps. */
@Mixin(Entity.class)
public abstract class EntityLiftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void zomboidcraft$carryInLift(CallbackInfo ci) {
        LiftPhysics.carry((Entity) (Object) this);
    }

    @ModifyVariable(method = "collide", at = @At("HEAD"), argsOnly = true)
    private Vec3 zomboidcraft$liftFloor(Vec3 movement) {
        return LiftPhysics.clip((Entity) (Object) this, movement);
    }
}
