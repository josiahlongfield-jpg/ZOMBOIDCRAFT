package com.zomboidcraft.mixin;

import com.zomboidcraft.world.cars.CarProp;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerRidingMixin {
    /** Sneak doesn't throw you out of an aircraft (Ctrl sinks the helicopter); the interact key gets you out. */
    @Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$stayInAircraft(CallbackInfoReturnable<Boolean> cir) {
        if (((Player) (Object) this).getVehicle() instanceof CarProp car && car.aircraft()) {
            cir.setReturnValue(false);
        }
    }
}
