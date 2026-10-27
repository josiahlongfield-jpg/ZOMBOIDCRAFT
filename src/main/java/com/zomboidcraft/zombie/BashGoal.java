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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A zombie that wants to get somewhere and is standing next to a barricade, or a shut door on
 * its way, pounds on it until the boards come off or the door gives way. Zombies never open
 * doors; they break them. The banging is loud and draws more of them in.
 */
public class BashGoal extends Goal {
    private static final int HIT_INTERVAL = 20;
    private static final double BANG_NOISE = 12;

    private final Zombie zombie;
    private BlockPos block;
    private boolean door;
    private int ticks, hits;

    public BashGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Where the zombie is trying to get: the player it's after, or the last noise it heard. */
    private @Nullable Vec3 goal() {
        if (zombie.getTarget() != null) {
            return zombie.getTarget().position();
        }
        return ((NoiseListener) zombie).zomboidcraft$heardAt();
    }

    private boolean blocking(BlockState state) {
        return door ? isShutDoor(state) : bashable(state);
    }

    /** Boards, a metal sheet or a spiked log wall: built things it beats down. */
    private static boolean bashable(BlockState state) {
        return state.is(ModBlocks.BARRICADE) || com.zomboidcraft.building.BuildBlocks.bashDamage(state) != null;
    }

    private static boolean isShutDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN);
    }

    @Override
    public boolean canUse() {
        ZcConfig.Zombies config = ZcConfig.get().zombies;
        if (zombie.tickCount % 10 != 0 || !config.bashBarricades && !config.bashDoors || goal() == null) {
            return false;
        }
        return find(config);
    }

    @Override
    public boolean canContinueToUse() {
        return goal() != null && blocking(zombie.level().getBlockState(block))
                && zombie.position().distanceToSqr(Vec3.atCenterOf(block)) < 6.25;
    }

    @Override
    public void start() {
        ticks = 0;
        hits = 0;
        zombie.getNavigation().stop();
        zombie.setAggressive(true);
    }

    @Override
    public void stop() {
        zombie.setAggressive(false);
        zombie.level().destroyBlockProgress(zombie.getId(), block, -1);
        block = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        Vec3 center = Vec3.atCenterOf(block);
        zombie.getLookControl().setLookAt(center.x, center.y, center.z);
        if (++ticks % HIT_INTERVAL != 0 || !(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = level.getBlockState(block);
        boolean steel = state.getSoundType() != SoundType.WOOD;
        level.playSound(null, block, steel ? SoundEvents.ZOMBIE_ATTACK_IRON_DOOR : SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR,
                SoundSource.HOSTILE, 1f, 0.9f + zombie.getRandom().nextFloat() * 0.2f);
        Noise.emit(level, center, BANG_NOISE);
        hits++;
        if (door) {
            hitDoor(level);
        } else {
            hitBarricade(level, state);
        }
    }

    private void hitDoor(ServerLevel level) {
        int needed = Math.max(1, ZcConfig.get().zombies.doorHits);
        if (zombie.getRandom().nextFloat() < 1f / needed) {
            level.destroyBlock(block, false);
            level.playSound(null, block, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1f, 1f);
            return;
        }
        level.destroyBlockProgress(zombie.getId(), block, Math.min(9, hits * 10 / needed));
    }

    /**
     * A blow on boards, a sheet or a spiked wall: each board, and the sheet or wall, stands so many blows (ZcConfig building), kept on the
     * block in eighths so a few stragglers wear it down and a crowd gets through.
     */
    private void hitBarricade(ServerLevel level, BlockState state) {
        double scale = ZcConfig.get().zombies.barricadeDamage;
        if (scale <= 0 || zombie.getRandom().nextDouble() > scale) {
            return;
        }
        var piece = com.zomboidcraft.building.BuildBlocks.bashDamage(state);
        boolean sheet = piece != null;
        var damage = sheet ? piece : BarricadeBlock.DAMAGE;
        int hits = sheet ? com.zomboidcraft.building.BuildBlocks.bashHits(state) : BarricadeBlock.boardHits(state);
        if (state.is(com.zomboidcraft.building.BuildBlocks.SPIKED_WALL)) {
            com.zomboidcraft.building.BuildBlocks.SpikedWall.spike(level, zombie);
        }
        // eighths of the board per blow, carried over by chance so a 6-blow board takes about 6 blows
        double eighths = 8.0 / hits;
        int add = (int) eighths + (zombie.getRandom().nextDouble() < eighths - (int) eighths ? 1 : 0);
        int now = state.getValue(damage) + add;
        if (now >= 8) {
            int planks = sheet ? 1 : state.getValue(BarricadeBlock.PLANKS);
            if (planks <= 1) {
                level.destroyBlock(block, false);
                level.playSound(null, block, sheet ? SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR : SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                        SoundSource.HOSTILE, 1f, sheet ? 0.7f : 1f);
                return;
            }
            level.setBlockAndUpdate(block, state.setValue(BarricadeBlock.PLANKS, planks - 1).setValue(damage, 0));
            level.playSound(null, block, SoundEvents.WOOD_BREAK, SoundSource.HOSTILE, 1f, 0.8f);
            now = 0;
        } else {
            level.setBlockAndUpdate(block, state.setValue(damage, now));
        }
        level.destroyBlockProgress(zombie.getId(), block, Math.min(9, now * 10 / 8));
    }

    /**
     * The nearest barricade around the zombie, or failing that the nearest shut door that stands
     * between it and where it's going.
     */
    private boolean find(ZcConfig.Zombies config) {
        BlockPos feet = zombie.blockPosition();
        Vec3 here = zombie.position();
        Vec3 goal = goal();
        BlockPos best = null;
        boolean bestDoor = false;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 1, 1))) {
            BlockState state = zombie.level().getBlockState(pos);
            boolean isDoor;
            if (config.bashBarricades && bashable(state)) {
                isDoor = false;
            } else if (config.bashDoors && isShutDoor(state)) {
                Vec3 toDoor = Vec3.atCenterOf(pos).subtract(here);
                Vec3 toGoal = goal.subtract(here);
                if (toDoor.dot(toGoal) <= 0 || toGoal.lengthSqr() < toDoor.lengthSqr()) {
                    continue;  // behind it, or it's already this side of the door
                }
                isDoor = true;
            } else {
                continue;
            }
            // barricades first: they're what's actually in the way of a window or doorway
            double dist = here.distanceToSqr(Vec3.atCenterOf(pos)) + (isDoor ? 100 : 0);
            if (dist < bestDist) {
                bestDist = dist;
                best = pos.immutable();
                bestDoor = isDoor;
            }
        }
        block = best;
        door = bestDoor;
        return best != null;
    }
}
