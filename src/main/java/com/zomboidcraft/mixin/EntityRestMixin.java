package com.zomboidcraft.mixin;

import com.zomboidcraft.zombie.perf.QuietCollisions;
import com.zomboidcraft.zombie.perf.Resting;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The horde savings on Entity: no block effects for a walker that rested (Resting), no empty collision search (QuietCollisions). */
@Mixin(Entity.class)
public abstract class EntityRestMixin {
    @Inject(method = "applyEffectsFromBlocks()V", at = @At("HEAD"), cancellable = true, require = 0)
    private void zomboidcraft$restedNoEffects(CallbackInfo ci) {
        if (Resting.skipEffects(this)) {
            ci.cancel();
        }
    }

    @Redirect(method = "collide", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;getEntityCollisions(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            require = 0)
    private List<VoxelShape> zomboidcraft$quietCollisions(Level level, Entity self, AABB box) {
        return QuietCollisions.collisions(level, self, box);
    }
}
