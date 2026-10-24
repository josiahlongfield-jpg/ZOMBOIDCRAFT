package com.zomboidcraft.mixin;

import com.zomboidcraft.zombie.InvestigateNoiseGoal;
import com.zomboidcraft.zombie.NoiseListener;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Zombie.class)
public abstract class ZombieMixin implements NoiseListener {
    @Unique
    private @Nullable Vec3 zomboidcraft$heardAt;

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void zomboidcraft$addNoiseGoal(CallbackInfo ci) {
        Zombie self = (Zombie) (Object) this;
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(3, new InvestigateNoiseGoal(self));
    }

    /** The walking dead don't burn in daylight. */
    @Inject(method = "isSunSensitive", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$noSunburn(CallbackInfoReturnable<Boolean> cir) {
        if (ZombieTypes.isWalker((Zombie) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public void zomboidcraft$hear(Vec3 pos) {
        zomboidcraft$heardAt = pos;
    }

    @Override
    public @Nullable Vec3 zomboidcraft$heardAt() {
        return zomboidcraft$heardAt;
    }

    @Override
    public void zomboidcraft$forget() {
        zomboidcraft$heardAt = null;
    }
}
