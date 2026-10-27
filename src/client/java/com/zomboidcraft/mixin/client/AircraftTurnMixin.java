package com.zomboidcraft.mixin.client;

import com.zomboidcraft.client.cars.FlightClient;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class AircraftTurnMixin {
    /** In an aircraft the mouse flies it, or looks round the cockpit, rather than turning you. */
    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$fly(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player && FlightClient.mouse(player, yaw * 0.15, pitch * 0.15)) {
            ci.cancel();
        }
    }
}
