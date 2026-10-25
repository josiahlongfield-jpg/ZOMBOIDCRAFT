package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.combat.AimSensitivity;
import com.zomboidcraft.client.hud.QuickWheel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    /** While the quick-use wheel is up the mouse points at it and you don't turn. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$wheel(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && QuickWheel.open()) {
            QuickWheel.point(yaw, pitch);
            ci.cancel();
        }
    }

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
