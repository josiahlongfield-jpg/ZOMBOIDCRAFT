package com.zomboidcraft.mixin;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.BashGoal;
import com.zomboidcraft.zombie.InvestigateNoiseGoal;
import com.zomboidcraft.zombie.NoiseListener;
import com.zomboidcraft.zombie.ZombieSightGoal;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
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

@Mixin(Zombie.class)
public abstract class ZombieMixin implements NoiseListener {
    @Unique
    private @Nullable Vec3 zomboidcraft$heardAt;

    @Inject(method = "registerGoals", at = @At("TAIL"))
    private void zomboidcraft$addGoals(CallbackInfo ci) {
        Zombie self = (Zombie) (Object) this;
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(1, new BashGoal(self));
        ((MobAccessor) self).zomboidcraft$goalSelector().addGoal(3, new InvestigateNoiseGoal(self));
        if (ZombieTypes.isWalkerType(self.getType()) && ZcConfig.get().senses.sightEnabled) {
            GoalSelector targets = ((MobAccessor) self).zomboidcraft$targetSelector();
            targets.removeAllGoals(goal -> goal instanceof NearestAttackableTargetGoal<?> nearest
                    && ((NearestAttackableTargetGoalAccessor) nearest).zomboidcraft$targetType() == Player.class);
            targets.addGoal(2, new ZombieSightGoal(self));
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
