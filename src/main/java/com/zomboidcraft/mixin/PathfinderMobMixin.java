package com.zomboidcraft.mixin;

import com.zomboidcraft.zombie.ZombieSpawner;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PathfinderMob.class)
public abstract class PathfinderMobMixin {
    /**
     * The night's own zombies don't turn up on roofs or in treetops either (ZombieSpawner.perched). Zombies take
     * their spawn rule from here, not from Mob, which this overrides without calling.
     */
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$notPerched(LevelAccessor level, EntitySpawnReason reason, CallbackInfoReturnable<Boolean> cir) {
        Mob self = (Mob) (Object) this;
        // the world's population places the dead now (population); the night adds none of its own
        if (reason == EntitySpawnReason.NATURAL && ZombieTypes.isWalker(self)
                && (com.zomboidcraft.ZcConfig.get().population.enabled
                        || level instanceof ServerLevel server && ZombieSpawner.perched(server, self.blockPosition()))) {
            cir.setReturnValue(false);
        }
    }
}
