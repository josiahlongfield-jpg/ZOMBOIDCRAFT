package com.zomboidcraft.mixin;

import com.zomboidcraft.world.trains.Trains;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Standing in a train a block above the track isn't flying: no "flying is not enabled" kick for riders. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerFloatingMixin {
    @Inject(method = "noBlocksAround", at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$onTrain(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof ServerPlayer player && Trains.riding(player)) {
            cir.setReturnValue(false);
        }
    }
}
