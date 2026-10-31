package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.Dismember;
import com.zomboidcraft.combat.Downed;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A zombie after something on the far side of a window whose glass is gone climbs in through it, as in Zomboid: it rakes
 * the last shards out of the frame, hauls itself onto the sill, slides through on its belly, spills out head first onto
 * the floor, lies there a moment and gets up (ClimbPath, drawn by ZBreak). Either way through: zombies the population
 * puts in houses climb out after you as readily as the ones outside climb in.
 *
 * <p>A house window is a one-block sill under an opening 0.69 of a block wide and 1.75 tall, too small to walk
 * through, so the zombie is moved through it under its hips, passing through the frame, and can't be stopped once
 * it's started; it can be shot all the way.
 */
public class WindowClimbGoal extends Goal {
    /** Windows being climbed through, and by whom, so two don't go through one at once. Server thread only. */
    private static final Map<Level, Map<Long, Zombie>> CLAIMS = new java.util.WeakHashMap<>();

    private final Zombie zombie;
    private BlockPos window;
    private Direction way;
    private Acts.Act act;
    private boolean raked, hauled, landed;

    public WindowClimbGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    /** Where the zombie is trying to get: the player it's after, or the last noise it heard. */
    private @Nullable Vec3 goal() {
        if (zombie.getTarget() != null) {
            return zombie.getTarget().position();
        }
        return ((NoiseListener) zombie).zomboidcraft$heardAt();
    }

    @Override
    public boolean canUse() {
        if (zombie.tickCount % 5 != 0 || !ZcConfig.get().zombies.climbWindows || !zombie.onGround() || zombie.isPassenger()
                || zombie.isInWater() || Downed.isLow(zombie) || Downed.isCrawler(zombie)
                || Dismember.legsGone(Dismember.cuts(zombie)) > 0 || Downed.isHeld(zombie)) {
            return false;
        }
        Vec3 goal = goal();
        return goal != null && find(goal);
    }

    /** A window in the wall beside it, its glass gone, with what it's after somewhere beyond and room to land. */
    private boolean find(Vec3 goal) {
        Level level = zombie.level();
        BlockPos feet = zombie.blockPosition();
        Vec3 here = zombie.position();
        if (here.y - feet.getY() > 0.2) {
            return false;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos lo = feet.relative(d).above();
            Direction.Axis axis = d.getAxis(), side = axis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
            boolean positive = d.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            // up against the wall, in line with the opening
            double toWall = (lo.get(axis) + (positive ? 0 : 1) - (axis == Direction.Axis.X ? here.x : here.z)) * (positive ? 1 : -1);
            double off = (side == Direction.Axis.X ? here.x : here.z) - (lo.get(side) + 0.5);
            if (toWall < 0.2 || toWall > 0.95 || Math.abs(off) > 0.45) {
                continue;
            }
            Vec3 toGoal = goal.subtract(here);
            if (toGoal.x * d.getStepX() + toGoal.z * d.getStepZ() < 1.2 || claimed(level, lo) || !open(level, lo, d)
                    || !landing(level, lo, d)) {
                continue;
            }
            window = lo;
            way = d;
            return true;
        }
        return false;
    }

    /** An open frame (an _empty window) passed through along d, with the top half open too or only glass to rake out. */
    private static boolean open(Level level, BlockPos lo, Direction d) {
        BlockState state = level.getBlockState(lo);
        if (!frame(state, d)) {
            return false;
        }
        BlockState top = level.getBlockState(lo.above());
        return frame(top, d) || window(top) && BashGoal.broken(top) != null
                || top.getCollisionShape(level, lo.above()).isEmpty();
    }

    private static boolean frame(BlockState state, Direction d) {
        if (!window(state) || !path(state).contains("_empty")) {
            return false;
        }
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == d.getAxis();
        }
        return !state.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)
                || state.getValue(BlockStateProperties.HORIZONTAL_AXIS) == d.getAxis();
    }

    private static boolean window(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.getNamespace().equals("zomboidcraft") && id.getPath().contains("win");
    }

    private static String path(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    /** Inside: room to stand where it gets up, a floor under it, and no wall where its head lands. */
    private boolean landing(Level level, BlockPos lo, Direction d) {
        Vec3 at = Acts.anchor(lo).add(d.getStepX() * ClimbPath.hips(ClimbPath.END), 0, d.getStepZ() * ClimbPath.hips(ClimbPath.END));
        BlockPos floor = BlockPos.containing(at).below();
        BlockPos head = lo.relative(d, 2).below();
        return level.noCollision(zombie, zombie.getDimensions(Pose.STANDING).makeBoundingBox(at))
                && !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                && !level.getBlockState(head).isCollisionShapeFullBlock(level, head);
    }

    private boolean claimed(Level level, BlockPos lo) {
        Zombie by = CLAIMS.computeIfAbsent(level, k -> new HashMap<>()).get(lo.asLong());
        return by != null && by != zombie && by.isAlive() && !by.isRemoved() && Acts.climbing(by);
    }

    @Override
    public void start() {
        Level level = zombie.level();
        Vec3 rel = zombie.position().subtract(Acts.anchor(window));
        // how far past the glass and to its left it stands (its left, facing the way it climbs, is (z, -x))
        float along0 = (float) (rel.x * way.getStepX() + rel.z * way.getStepZ());
        float side0 = (float) (rel.x * way.getStepZ() - rel.z * way.getStepX());
        act = new Acts.Act(Acts.CLIMB, window, way.get2DDataValue(), level.getGameTime(), along0, side0);
        zombie.setAttached(Acts.ACT, act);
        CLAIMS.computeIfAbsent(level, k -> new HashMap<>()).put(window.asLong(), zombie);
        zombie.getNavigation().stop();
        zombie.noPhysics = true;
        zombie.setDeltaMovement(Vec3.ZERO);
        raked = hauled = landed = false;
    }

    private float time() {
        return Acts.climbTime(act, zombie.level().getGameTime());
    }

    @Override
    public boolean canContinueToUse() {
        return act != null && zombie.isAlive() && time() < ClimbPath.END;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        float u = time();
        zombie.getNavigation().stop();
        zombie.setPos(Acts.climbPlace(act, u));
        zombie.setDeltaMovement(Vec3.ZERO);
        float yaw = act.yaw();
        zombie.setYRot(yaw);
        zombie.setYBodyRot(yaw);
        zombie.setYHeadRot(yaw);
        zombie.setXRot(0f);
        if (!(zombie.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = zombie.position();
        if (!raked && u >= ClimbPath.RAKED) {
            raked = true;
            rake(level);
        }
        if (!hauled && u >= ClimbPath.HAUL) {
            hauled = true;
            level.playSound(null, at.x, at.y + 1, at.z, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 0.5f,
                    1.3f + zombie.getRandom().nextFloat() * 0.2f);
        }
        if (!landed && u >= ClimbPath.LANDED) {
            landed = true;
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_BIG_FALL, SoundSource.HOSTILE, 1f, 0.75f);
            Noise.emit(level, at, 8);
        }
    }

    /** The shards left in the top half of the window come out with its hands. */
    private void rake(ServerLevel level) {
        BlockPos top = window.above();
        BlockState was = level.getBlockState(top), now = was;
        for (int i = 0; i < 3 && window(now); i++) {
            BlockState next = BashGoal.broken(now);
            if (next == null) {
                break;
            }
            now = next;
        }
        if (now != was) {
            level.setBlockAndUpdate(top, now);
            level.playSound(null, top, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8f, 1.1f);
        }
    }

    @Override
    public void stop() {
        zombie.noPhysics = false;
        if (act != null) {
            if (zombie.isAlive()) {
                // through, or cut short: on its feet on whichever side it's nearer
                float u = Math.min(time(), ClimbPath.END);
                Vec3 end = Acts.climbPlace(act, u < 3.5f ? ClimbPath.START : ClimbPath.END);
                zombie.setPos(u >= ClimbPath.END ? Acts.climbPlace(act, ClimbPath.END) : end);
                if (u >= ClimbPath.END) {
                    com.zomboidcraft.combat.Grabs.lungeAfterClimb(zombie);     // in, and at you if you're close
                }
            }
            Map<Long, Zombie> claims = CLAIMS.get(zombie.level());
            if (claims != null) {
                claims.remove(window.asLong(), zombie);
            }
            if (Acts.climbing(zombie)) {
                zombie.removeAttached(Acts.ACT);
            }
        }
        act = null;
        window = null;
    }
}
