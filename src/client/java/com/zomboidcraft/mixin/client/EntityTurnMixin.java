package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.combat.AimSensitivity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    /** On the sights (and through the scope) the mouse turns you at the aim sensitivity instead of the normal one. */
    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double zomboidcraft$aimYaw(double yaw) {
        return (Object) this instanceof LocalPlayer ? yaw * AimSensitivity.factor() : yaw;
    }

    @ModifyVariable(method = "turn", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double zomboidcraft$aimPitch(double pitch) {
        return (Object) this instanceof LocalPlayer ? pitch * AimSensitivity.factor() : pitch;
    }
}
