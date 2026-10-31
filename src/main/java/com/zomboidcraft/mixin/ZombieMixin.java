package com.zomboidcraft.mixin;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Downed;
import com.zomboidcraft.zombie.BashGoal;
import com.zomboidcraft.zombie.InvestigateNoiseGoal;
import com.zomboidcraft.zombie.NoiseListener;
import com.zomboidcraft.zombie.ZombieSightGoal;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
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
    private void zomboidcraft$addGoals(CallbackInfo ci) {
        Zombie self = (Zombie) (Object) this;
        if (ZombieTypes.isWalkerType(self.getType())) {
            // no turtles in the county: their egg hunt scans thousands of blocks round every zombie, a big cost in a horde
            ((MobAccessor) self).zomboidcraft$goalSelector().removeAllGoals(goal -> goal instanceof RemoveBlockGoal);
            ((MobAccessor) self).zomboidcraft$targetSelector().removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?> nearest
                    && ((NearestAttackableTargetGoalAccessor) nearest).zomboidcraft$targetType().getSimpleName().equals("Turtle"));
        }
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(0, new com.zomboidcraft.zombie.WindowClimbGoal(self));
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(1, new BashGoal(self));
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(1, new com.zomboidcraft.zombie.WayInGoal(self));
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(3, new InvestigateNoiseGoal(self));
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(6, new com.zomboidcraft.zombie.RallyGoal(self));
        if (ZombieTypes.isWalkerType(self.getType()) && ZcConfig.get().senses.sightEnabled) {
            GoalSelector targets = ((MobAccessor) self).zomboidcraft$targetSelector();
            targets.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?> nearest
                    && ((NearestAttackableTargetGoalAccessor) nearest).zomboidcraft$targetType() == Player.class);
            targets.addGoal(2, new ZombieSightGoal(self));
        }
    }

    /** Lying or crawling walkers have a low hitbox: you look down to hit them. */
    @Inject(method = "getDefaultDimensions", at = @At("HEAD"), cancellable = true)
    private void zomboidcraft$lieLow(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (pose == Pose.SWIMMING && ZombieTypes.isWalkerType(((Zombie) (Object) this).getType())) {
            cir.setReturnValue(Downed.LOW);
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
