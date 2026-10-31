package com.zomboidcraft.mixin;

import com.zomboidcraft.survival.Downs;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerDownedMixin {
    /** A downed survivor lies flat, crawling, on every client and the server (see survival/Downs). */
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$lieDown(CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (Downs.downed(self) && !self.isPassenger()) {
            self.setPose(Pose.SWIMMING);
            ci.cancel();
        }
    }
}
