package com.zomboidcraft.zombie.perf;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

/**
 * A horde heading for one spot plans one path (performance.sharedPaths). When a walker plans a path, it's kept for a
 * second under where it started (to the block's height, within a two-block square) and where it goes; another
 * walker starting from the same square for the same place walks a copy instead of planning its own. A path that
 * found no way is shared too, so fifty zombies don't each search a whole region for a way into a sealed room.
 */
public final class SharedPaths {
    private static final int KEEP_TICKS = 20;

    private record Key(Level level, int x, int y, int z, long target, int accuracy, int range, int depth) {
    }

    private record Kept(Path path, long made) {
    }

    private static final Map<Key, Kept> KEPT = new HashMap<>();
    private static long sweptAt;

    /** In place of PathFinder.findPath inside PathNavigation.createPath. */
    public static Path find(PathFinder finder, PathNavigationRegion region, Mob mob, Set<BlockPos> targets, float range,
                            int accuracy, float depth) {
        if (targets.size() != 1 || mob.level().isClientSide() || !ZombieTypes.isWalker(mob)
                || !ZcConfig.get().performance.sharedPaths) {
            return finder.findPath(region, mob, targets, range, accuracy, depth);
        }
        long now = mob.level().getGameTime();
        if (now - sweptAt > 100 || KEPT.size() > 8192) {
            KEPT.values().removeIf(k -> now - k.made > KEEP_TICKS);
            sweptAt = now;
        }
        BlockPos at = mob.blockPosition();
        Key key = new Key(mob.level(), at.getX() >> 1, at.getY(), at.getZ() >> 1, targets.iterator().next().asLong(),
                accuracy, (int) range, Float.floatToIntBits(depth));
        Kept kept = KEPT.get(key);
        if (kept != null && now - kept.made <= KEEP_TICKS) {
            HordeStats.shared++;
            return copy(kept.path);
        }
        Path path = finder.findPath(region, mob, targets, range, accuracy, depth);
        HordeStats.planned++;
        KEPT.put(key, new Kept(path == null ? null : copy(path), now));
        return path;
    }

    private static Path copy(Path path) {
        if (path == null) {
            return null;
        }
        List<Node> nodes = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) {
            nodes.add(path.getNode(i));
        }
        return new Path(nodes, path.getTarget(), path.canReach());
    }

    /**
     * How far a noise has to move before a zombie heading for it plans again: three blocks close by, a fifth of the
     * way when it's further (a zombie 60 blocks off doesn't need a new path for footsteps 4 blocks over).
     */
    public static double replanAfter(double distance, double near) {
        return ZcConfig.get().performance.sharedPaths ? Math.max(near, distance * 0.2) : near;
    }

    private SharedPaths() {
    }
}
