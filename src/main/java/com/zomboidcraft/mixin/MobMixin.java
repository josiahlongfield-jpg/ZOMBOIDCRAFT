package com.zomboidcraft.mixin;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.NoiseListener;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {
    /** The walking dead don't burn in daylight. In 26.3 the sunburn check lives here, not in Zombie. */
    @Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$noSunburn(CallbackInfo ci) {
        if (ZombieTypes.isWalker((Mob) (Object) this) && !ZcConfig.get().zombies.burnInSunlight) {
            ci.cancel();
        }
    }

    /**
     * A zombie standing about with nothing to chase or look into, far from every player, thinks every fourth tick: a
     * horde spread over a town costs a quarter as much, and one that hears something still turns up the same.
     */
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$idleFarOff(CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if ((self.tickCount + self.getId()) % 4 == 0 || !ZombieTypes.isWalker(self) || self.getTarget() != null
                || !self.getNavigation().isDone() || ((NoiseListener) self).zomboidcraft$heardAt() != null) {
            return;
        }
        double far = ZcConfig.get().performance.idleThinkDistance;
        if (far <= 0) {
            return;
        }
        for (Player player : self.level().players()) {
            if (!player.isSpectator() && player.distanceToSqr(self) < far * far) {
                return;
            }
        }
        ci.cancel();
    }

    /**
     * Minecraft's own despawning never takes a walker. Vanilla deleted any zombie 128 blocks off and idle ones past 32
     * at random, for good: walk away from a 600 horde and come back to a handful (Josiah, 2026-11-04). Zomboid never
     * deletes its dead; idle ones far off are folded into their chunk's records by Population and woken on return.
     */
    @Inject(method = "removeWhenFarAway", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$walkersStay(double distance, CallbackInfoReturnable<Boolean> cir) {
        if (ZombieTypes.isWalker((Mob) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
