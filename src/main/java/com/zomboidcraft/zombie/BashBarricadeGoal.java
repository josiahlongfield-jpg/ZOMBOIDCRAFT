package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.barricade.BarricadeBlock;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A zombie that wants to get somewhere and is standing next to a barricade pounds on it
 * until the boards come off. The banging is loud and draws more of them in.
 */
public class BashBarricadeGoal extends Goal {
    private static final int HIT_INTERVAL = 20;
    private static final double BANG_NOISE = 12;

    private final Zombie zombie;
    private BlockPos barricade;
    private int ticks;

    public BashBarricadeGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    private boolean motivated() {
        return zombie.getTarget() != null || ((NoiseListener) zombie).zomboidcraft$heardAt() != null;
    }

    @Override
    public boolean canUse() {
        if (zombie.tickCount % 10 != 0 || !ZcConfig.get().zombies.bashBarricades || !motivated()) {
            return false;
        }
        barricade = findBarricade();
        return barricade != null;
    }

    @Override
    public boolean canContinueToUse() {
        return motivated() && zombie.level().getBlockState(barricade).is(ModBlocks.BARRICADE)
                && zombie.position().distanceToSqr(Vec3.atCenterOf(barricade)) < 6.25;
    }

    @Override
    public void start() {
        ticks = 0;
        zombie.getNavigation().stop();
        zombie.setAggressive(true);
    }

    @Override
    public void stop() {
        zombie.setAggressive(false);
        zombie.level().destroyBlockProgress(zombie.getId(), barricade, -1);
        barricade = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 center = Vec3.atCenterOf(barricade);
        zombie.getLookControl().setLookAt(center.x, center.y, center.z);
        if (++ticks % HIT_INTERVAL != 0 || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(barricade);
        level.playSound(null, barricade, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1f,
                0.9f + zombie.getRandom().nextFloat() * 0.2f);
        Noise.emit(level, center, BANG_NOISE);

        if (zombie.getRandom().nextFloat() < BarricadeBlock.breakChance(state) * ZcConfig.get().zombies.barricadeDamage) {
            int planks = state.getValue(BarricadeBlock.PLANKS);
            if (planks <= 1) {
                level.destroyBlock(barricade, false);
                level.playSound(null, barricade, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1f, 1f);
                return;
            }
            level.setBlockAndUpdate(barricade, state.setValue(BarricadeBlock.PLANKS, planks - 1));
            state = level.getBlockState(barricade);
        }
        int planks = state.getValue(BarricadeBlock.PLANKS);
        level.destroyBlockProgress(zombie.getId(), barricade, (BarricadeBlock.MAX_PLANKS - planks) * 2 + 1);
    }

    private BlockPos findBarricade() {
        BlockPos feet = zombie.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 1, 1))) {
            if (zombie.level().getBlockState(pos).is(ModBlocks.BARRICADE)) {
                double dist = zombie.position().distanceToSqr(Vec3.atCenterOf(pos));
                if (dist < bestDist) {
                    bestDist = dist;
                    best = pos.immutable();
                }
            }
        }
        return best;
    }
}
