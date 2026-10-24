package com.zomboidcraft.mixin;

import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class MobMixin {
    /** The walking dead don't burn in daylight. In 26.3 the sunburn check lives here, not in Zombie. */
    @Inject(method = "burnUndead", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$noSunburn(CallbackInfo ci) {
        if (ZombieTypes.isWalker((Mob) (Object) this)) {
            ci.cancel();
        }
    }
}
