package com.zomboidcraft.zombie;

import com.zomboidcraft.ModBlocks;
import com.zomboidcraft.ZcConfig;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A zombie that can't get to what it's after (you're shut in a house, or it heard you in there) goes round to the
 * nearest way in: a shut door, a window, boards over either. There BashGoal beats it in and WindowClimbGoal climbs
 * through, as Zomboid's zombies make for the doors and windows of the house you're in rather than pawing at a wall.
 */
public class WayInGoal extends Goal {
    /** How far round what it's after it looks for a way in, and how often a stuck zombie looks again (ticks). */
    private static final int RADIUS = 10, LOOK_EVERY = 40;
    /** Paths planned to ways in each server tick, by every zombie together: planning is the costly part. */
    private static final int PATHS_PER_TICK = 4;
    /** Ways in round each place, found once and kept a few seconds for every zombie after the same thing. */
    private static final int KEPT = 100;
    private static final Map<Level, Map<Long, Ways>> CACHE = new java.util.WeakHashMap<>();
    private static long budgetTick;
    private static int budget;

    private record Ways(long at, List<BlockPos> list) {
    }

    private final Zombie zombie;
    private BlockPos way;
    private Path path;
    private int ticks;
    private Vec3 lastPos = Vec3.ZERO;
    private int still;

    public WayInGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
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
        if ((zombie.tickCount + zombie.getId()) % LOOK_EVERY != 0 || !ZcConfig.get().zombies.seekWayIn
                || zombie.isPassenger() || !ZcConfig.get().zombies.bashDoors && !ZcConfig.get().zombies.climbWindows) {
            return false;
        }
        Vec3 goal = goal();
        if (goal == null || goal.distanceToSqr(zombie.position()) > 24 * 24 || goal.distanceToSqr(zombie.position()) < 2.5 * 2.5) {
            return false;
        }
        // only when it can't get there: its path falls short, or it has stood at the end of one
        Path current = zombie.getNavigation().getPath();
        Vec3 here = zombie.position();
        still = here.distanceToSqr(lastPos) < 0.25 ? still + 1 : 0;
        lastPos = here;
        boolean stuck = current != null && !current.canReach() || (current == null || current.isDone()) && still >= 2;
        return stuck && find(goal);
    }

    /** The nearest way in it can walk to, through the fewest steps both to it and from it to what it's after. */
    private boolean find(Vec3 goal) {
        Level level = zombie.level();
        long now = level.getGameTime();
        if (budgetTick != now) {
            budgetTick = now;
            budget = PATHS_PER_TICK;
        }
        if (budget <= 0) {
            return false;
        }
        BlockPos centre = BlockPos.containing(goal);
        Map<Long, Ways> cache = CACHE.computeIfAbsent(level, k -> new HashMap<>());
        long key = BlockPos.asLong(centre.getX() >> 2, centre.getY() >> 1, centre.getZ() >> 2);
        Ways ways = cache.get(key);
        if (ways == null || now - ways.at > KEPT) {
            if (cache.size() > 256) {
                cache.values().removeIf(w -> now - w.at > KEPT);
            }
            ways = new Ways(now, scan(level, centre));
            cache.put(key, ways);
        }
        Vec3 here = zombie.position();
        List<BlockPos> sorted = new ArrayList<>(ways.list);
        sorted.sort(java.util.Comparator.comparingDouble(p -> Math.sqrt(p.distToCenterSqr(here)) + Math.sqrt(p.distToCenterSqr(goal))));
        int tries = 0;
        for (BlockPos p : sorted) {
            if (tries >= 3 || budget <= 0) {
                break;
            }
            BlockPos stand = stand(level, p, goal);
            if (stand == null) {
                continue;
            }
            tries++;
            budget--;
            Path to = zombie.getNavigation().createPath(stand, 0);
            if (to != null && to.canReach()) {
                way = p;
                path = to;
                return true;
            }
        }
        return false;
    }

    /** Shut doors, windows (glass or an open frame) and boards round centre. */
    private static List<BlockPos> scan(Level level, BlockPos centre) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-RADIUS, -3, -RADIUS), centre.offset(RADIUS, 3, RADIUS))) {
            BlockState s = level.getBlockState(p);
            if (s.isAir()) {
                continue;
            }
            boolean door = s.getBlock() instanceof DoorBlock && !s.getValue(DoorBlock.OPEN)
                    && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
            boolean window = isWindow(s) && !isWindow(level.getBlockState(p.below()));
            if (door || window || s.is(ModBlocks.BARRICADE) && !level.getBlockState(p.below()).is(ModBlocks.BARRICADE)) {
                out.add(p.immutable());
            }
        }
        return out;
    }

    private static boolean isWindow(BlockState s) {
        var id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
        return id.getNamespace().equals("zomboidcraft") && id.getPath().contains("win")
                && (BashGoal.broken(s) != null || id.getPath().contains("_empty"));
    }

    /**
     * Where to stand to break in at p: in front of it on the side away from what it's after (the way it faces, or
     * whichever side is open), on the floor at its foot or a block below (a window's sill).
     */
    private @Nullable BlockPos stand(Level level, BlockPos p, Vec3 goal) {
        BlockState s = level.getBlockState(p);
        Direction.Axis axis = s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? s.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis()
                : s.hasProperty(BlockStateProperties.HORIZONTAL_AXIS) ? s.getValue(BlockStateProperties.HORIZONTAL_AXIS) : null;
        BlockPos best = null;
        double far = -1;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (axis != null && d.getAxis() != axis) {
                continue;
            }
            for (int down = 0; down <= 1; down++) {
                BlockPos at = p.relative(d).below(down);
                if (!walkable(level, at)) {
                    continue;
                }
                double dist = at.distToCenterSqr(goal);
                if (dist > far && dist > p.distToCenterSqr(goal)) {
                    far = dist;
                    best = at;
                }
                break;
            }
        }
        return best;
    }

    private static boolean walkable(Level level, BlockPos at) {
        return level.getBlockState(at).getCollisionShape(level, at).isEmpty()
                && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()
                && !level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty();
    }

    @Override
    public void start() {
        ticks = 0;
        zombie.getNavigation().moveTo(path, 1.0);
    }

    @Override
    public boolean canContinueToUse() {
        return way != null && ticks < 400 && !zombie.getNavigation().isDone() && goal() != null;
    }

    @Override
    public void tick() {
        ticks++;
    }

    @Override
    public void stop() {
        way = null;
        path = null;
        still = 0;
    }
}
