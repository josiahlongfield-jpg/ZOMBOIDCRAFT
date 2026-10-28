package com.zomboidcraft.creature;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * How a Harvestman finds its way round walls: through doorways, windows and gaps, round corners, up walls, over
 * roofs and off ledges. It maps the cells its body can be in (air beside a floor, a wall or a ceiling it can hold, with
 * room off it) and searches them for the cheapest way (walking a floor is cheapest, clinging to a wall dearer, hanging
 * from a ceiling dearer still; dropping off a floor's edge is a step of its own). It follows the route by making for
 * the farthest point of it it can see with room for its body. A gap (a doorway, a window, a corridor) it comes at
 * square on, slowly: a point lined up a few cells before it, then along the line through its middle until its whole
 * body is through. It plans again when its quarry moves off the end of the route or it gets stuck. Worked out in a
 * Python port against voxel walls, doors and houses first (the thread's scratchpad crawlsim/walls.py).
 */
final class HarvestmanRoute {
    private static final int MAX_NODES = 12000, RANGE = 48, DROP = 16;
    /** Within this of its goal with a clear way to it, it needs no route. */
    private static final double DIRECT = 6;
    private static final int[][] MOVES;

    static {
        List<int[]> m = new ArrayList<>();
        for (int[] d : new int[][] {{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
            m.add(d);
        }
        for (int a = 0; a < 3; a++) {
            for (int b = a + 1; b < 3; b++) {
                for (int sa : new int[] {1, -1}) {
                    for (int sb : new int[] {1, -1}) {
                        int[] d = new int[3];
                        d[a] = sa;
                        d[b] = sb;
                        m.add(d);
                    }
                }
            }
        }
        MOVES = m.toArray(new int[0][]);
    }

    private List<Vec3> points;
    /** Gates (a point lined up before a gap) and the gaps themselves, each with the way through. */
    private final Map<Integer, Vec3> through = new HashMap<>();
    private final java.util.Set<Integer> gaps = new java.util.HashSet<>();
    private int idx;
    private Vec3 target;
    private long madeAt = -1000;
    private ServerLevel level;
    private final Long2ByteOpenHashMap solidCache = new Long2ByteOpenHashMap();
    private final Long2ByteOpenHashMap holdCache = new Long2ByteOpenHashMap();
    /** Its route goes on along the face it holds (so it doesn't climb what it meets), and it's coming up to a gap. */
    boolean stay, slow;

    void clear() {
        points = null;
        target = null;
        stay = slow = false;
    }

    /**
     * Where to make for now on the way to goal: the goal itself when it's close and in the clear, otherwise a point
     * along the route. foot is the cell-level point of its body (its centre, back toward the face it holds).
     */
    Vec3 aim(ServerLevel level, Vec3 foot, Vec3 surf, Vec3 goal, int stuck) {
        stay = slow = false;
        this.level = level;
        if (goal == null) {
            clear();
            return null;
        }
        if (foot.distanceTo(goal) < DIRECT && clear(foot, goal, surf)) {
            points = null;
            target = null;
            return goal;
        }
        long now = level.getGameTime();
        boolean moved = target == null || target.distanceToSqr(goal) > 2.5 * 2.5;
        if ((moved || stuck > 10) && now - madeAt >= 10) {
            plan(level, foot, goal);
            target = goal;
            madeAt = now;
        }
        if (points == null || points.isEmpty()) {
            return goal;    // no way found: straight at it, as before
        }
        while (idx < points.size() - 1 && passed(foot, surf)) {
            idx++;
        }
        // slow coming up to a gap and going through it, so it can line up and squeeze its body through
        for (int k = Math.max(0, idx - 1); k < Math.min(points.size(), idx + 4); k++) {
            if (through.containsKey(k) && points.get(k).distanceTo(foot) < 4) {
                slow = true;
            }
        }
        if (gaps.contains(idx)) {
            // through the gap: a point on the line through its middle, a little ahead, so it closes on that line
            Vec3 p = points.get(idx), ax = through.get(idx);
            return pick(foot, surf, p.add(ax.scale(foot.subtract(p).dot(ax) + 0.8)));
        }
        int last = Math.min(points.size() - 1, idx + 6);
        for (int k = idx + 1; k <= last; k++) {
            if (through.containsKey(k)) {
                last = k;
                break;
            }
        }
        for (int j = last; j > idx; j--) {
            Vec3 p = points.get(j);
            if (p.distanceTo(foot) < 7 && clear(foot, p, surf)) {
                return pick(foot, surf, j == points.size() - 1 ? goal : p);
            }
        }
        return pick(foot, surf, points.get(idx));
    }

    private Vec3 pick(Vec3 foot, Vec3 surf, Vec3 p) {
        stay = Math.abs(p.subtract(foot).dot(surf)) < 1.2;
        return p;
    }

    /** On to the next point: once there (across the face), or close with a clear way on; a gap once its body is through. */
    private boolean passed(Vec3 foot, Vec3 surf) {
        Vec3 p = points.get(idx), v = foot.subtract(p);
        if (gaps.contains(idx)) {
            return v.dot(through.get(idx)) > 2.1;
        }
        if (through.containsKey(idx)) {
            return v.length() < 0.7;
        }
        double flat = v.subtract(surf.scale(v.dot(surf))).length();
        return flat < 0.6 || v.length() < 1.6 && clear(foot, points.get(idx + 1), surf);
    }

    /** In sight with room for its body: down the middle and to either side. */
    private boolean clear(Vec3 a, Vec3 b, Vec3 surf) {
        Vec3 d = b.subtract(a), lat = d.cross(surf);
        lat = lat.lengthSqr() < 1e-8 ? Vec3.ZERO : lat.normalize().scale(0.7);
        for (Vec3 o : new Vec3[] {Vec3.ZERO, lat, lat.scale(-1)}) {
            if (level.clip(new ClipContext(a.add(o), b.add(o), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty())).getType() != HitResult.Type.MISS) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ the map

    private boolean solid(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte b = solidCache.getOrDefault(key, (byte) -1);
        if (b < 0) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = level.getBlockState(p);
            b = (byte) (s.getCollisionShape(level, p).isEmpty() ? 0 : 1);
            solidCache.put(key, b);
        }
        return b == 1;
    }

    /** 0 where its body can't be; otherwise the cost of being there: 10 on a floor, 14 on a wall, 20 on a ceiling. */
    private int hold(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        byte b = holdCache.getOrDefault(key, (byte) -1);
        if (b < 0) {
            b = 0;
            if (!solid(x, y, z)) {
                if (solid(x, y - 1, z) && !solid(x, y + 1, z)) {
                    b = 10;
                } else if (solid(x + 1, y, z) && !solid(x - 1, y, z) || solid(x - 1, y, z) && !solid(x + 1, y, z)
                        || solid(x, y, z + 1) && !solid(x, y, z - 1) || solid(x, y, z - 1) && !solid(x, y, z + 1)) {
                    b = 14;
                } else if (solid(x, y + 1, z) && !solid(x, y - 1, z)) {
                    b = 20;
                }
            }
            holdCache.put(key, b);
        }
        return b;
    }

    private BlockPos near(BlockPos p, int r) {
        BlockPos best = null;
        double bd = 1e9;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (hold(p.getX() + dx, p.getY() + dy, p.getZ() + dz) > 0) {
                        double d = dx * dx + dy * dy * 1.2 + dz * dz;
                        if (d < bd) {
                            bd = d;
                            best = p.offset(dx, dy, dz);
                        }
                    }
                }
            }
        }
        return best;
    }

    private void plan(ServerLevel level, Vec3 foot, Vec3 goal) {
        solidCache.clear();
        holdCache.clear();
        points = null;
        through.clear();
        gaps.clear();
        idx = 0;
        BlockPos a = near(BlockPos.containing(foot), 2), b = near(BlockPos.containing(goal), 3);
        if (a == null || b == null) {
            return;
        }
        Long2DoubleOpenHashMap g = new Long2DoubleOpenHashMap();
        g.defaultReturnValue(Double.MAX_VALUE);
        Long2LongOpenHashMap came = new Long2LongOpenHashMap();
        PriorityQueue<double[]> open = new PriorityQueue<>((p, q) -> Double.compare(p[0], q[0]));
        long start = a.asLong(), end = b.asLong();
        g.put(start, 0);
        open.add(new double[] {0, start});
        int n = 0;
        while (!open.isEmpty()) {
            double[] top = open.poll();
            long key = (long) top[1];
            if (key == end) {
                List<BlockPos> path = new ArrayList<>();
                for (long k = key; ; k = came.get(k)) {
                    path.add(0, BlockPos.of(k));
                    if (!came.containsKey(k)) {
                        break;
                    }
                }
                points(path);
                return;
            }
            if (++n > MAX_NODES) {
                return;
            }
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            double gp = g.get(key);
            if (top[0] - dist(x, y, z, b) > gp + 1e-6) {
                continue;   // a stale entry
            }
            int hp = hold(x, y, z);
            for (int[] m : MOVES) {
                int qx = x + m[0], qy = y + m[1], qz = z + m[2];
                if (Math.abs(qx - a.getX()) > RANGE || Math.abs(qy - a.getY()) > RANGE || Math.abs(qz - a.getZ()) > RANGE) {
                    continue;
                }
                int hq = hold(qx, qy, qz);
                if (hq == 0) {
                    continue;
                }
                int axes = Math.abs(m[0]) + Math.abs(m[1]) + Math.abs(m[2]);
                if (axes == 2) {
                    // round an edge or across a corner: never squeezed between two solid blocks, and flat across a
                    // floor, wall or ceiling it can't cut a corner (over an edge, one side of the move is the edge)
                    boolean s1, s2;
                    if (m[0] != 0 && m[1] != 0) {
                        s1 = solid(x + m[0], y, z);
                        s2 = solid(x, y + m[1], z);
                    } else if (m[0] != 0) {
                        s1 = solid(x + m[0], y, z);
                        s2 = solid(x, y, z + m[2]);
                    } else {
                        s1 = solid(x, y + m[1], z);
                        s2 = solid(x, y, z + m[2]);
                    }
                    if (s1 && s2 || m[1] == 0 && (s1 || s2)) {
                        continue;
                    }
                }
                double cost = Math.sqrt(axes) * (hp + hq) / 20.0;
                relax(g, came, open, key, BlockPos.asLong(qx, qy, qz), gp + cost, qx, qy, qz, b);
            }
            // off a floor's edge, down to the floor below
            if (hp == 10) {
                for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int qx = x + d[0], qz = z + d[1];
                    if (solid(qx, y, qz) || solid(qx, y + 1, qz) || solid(qx, y - 1, qz)) {
                        continue;
                    }
                    for (int k = 2; k <= DROP; k++) {
                        if (solid(qx, y - k, qz)) {
                            break;
                        }
                        if (solid(qx, y - k - 1, qz)) {
                            relax(g, came, open, key, BlockPos.asLong(qx, y - k, qz), gp + 2 + 0.25 * k, qx, y - k, qz, b);
                            break;
                        }
                    }
                }
            }
        }
    }

    private static double dist(int x, int y, int z, BlockPos b) {
        double dx = x - b.getX(), dy = y - b.getY(), dz = z - b.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static void relax(Long2DoubleOpenHashMap g, Long2LongOpenHashMap came, PriorityQueue<double[]> open, long from,
                              long to, double cost, int x, int y, int z, BlockPos b) {
        if (cost < g.get(to)) {
            g.put(to, cost);
            came.put(to, from);
            open.add(new double[] {cost + dist(x, y, z, b), to});
        }
    }

    // ------------------------------------------------------------------------------------------------ the points

    /** A route point: the cell's middle, moved off a solid side toward open room, and out round an outside corner. */
    private Vec3 centre(BlockPos p) {
        int x = p.getX(), y = p.getY(), z = p.getZ();
        double[] c = {x + 0.5, y + 0.5, z + 0.5};
        int[][] axis = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        for (int i = 0; i < 3; i++) {
            int[] e = axis[i];
            boolean lo = solid(x - e[0], y - e[1], z - e[2]), hi = solid(x + e[0], y + e[1], z + e[2]);
            if (lo && !hi) {
                c[i] += 0.3;
            } else if (hi && !lo) {
                c[i] -= 0.3;
            }
        }
        for (int[] ij : new int[][] {{0, 2}, {0, 1}, {1, 2}}) {
            for (int si : new int[] {1, -1}) {
                for (int sj : new int[] {1, -1}) {
                    int[] q = {x, y, z}, qi = {x, y, z}, qj = {x, y, z};
                    q[ij[0]] += si;
                    q[ij[1]] += sj;
                    qi[ij[0]] += si;
                    qj[ij[1]] += sj;
                    if (solid(q[0], q[1], q[2]) && !solid(qi[0], qi[1], qi[2]) && !solid(qj[0], qj[1], qj[2])) {
                        c[ij[0]] -= si * 0.35;
                        c[ij[1]] -= sj * 0.35;
                    }
                }
            }
        }
        return new Vec3(c[0], c[1], c[2]);
    }

    /** A doorway, a window or a corridor: solid both sides across one way, open both ways along another (that axis), or -1. */
    private int narrow(BlockPos p) {
        int[][] axis = {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}};
        int[] index = {0, 2, 1};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == j) {
                    continue;
                }
                int[] a = axis[i], c = axis[j];
                if (solid(p.getX() - c[0], p.getY() - c[1], p.getZ() - c[2]) && solid(p.getX() + c[0], p.getY() + c[1], p.getZ() + c[2])
                        && !solid(p.getX() - a[0], p.getY() - a[1], p.getZ() - a[2]) && !solid(p.getX() + a[0], p.getY() + a[1], p.getZ() + a[2])) {
                    return index[i];
                }
            }
        }
        return -1;
    }

    /** The route as points to make for; a gap gets a gate lined up before it, so it comes at the gap square on. */
    private void points(List<BlockPos> path) {
        List<Vec3> out = new ArrayList<>();
        for (int k = 0; k < path.size(); k++) {
            BlockPos p = path.get(k);
            Vec3 c = centre(p);
            int ax = k > 0 ? narrow(p) : -1;
            if (ax >= 0 && !out.isEmpty() && narrow(path.get(k - 1)) < 0) {
                double along = get(c, ax) - get(out.get(out.size() - 1), ax);
                double sign = Math.signum(along);
                if (sign != 0) {
                    Vec3 dir = new Vec3(ax == 0 ? sign : 0, ax == 1 ? sign : 0, ax == 2 ? sign : 0);
                    Vec3 gate = c.subtract(dir.scale(2.6));
                    if (!solid(Mth.floor(gate.x), Mth.floor(gate.y), Mth.floor(gate.z))) {
                        // the points already closer in than the gate go: it lines up first
                        while (!out.isEmpty() && (get(c, ax) - get(out.get(out.size() - 1), ax)) * sign < 2.6
                                && !through.containsKey(out.size() - 1)) {
                            out.remove(out.size() - 1);
                        }
                        through.put(out.size(), dir);
                        out.add(gate);
                        through.put(out.size(), dir);
                        gaps.add(out.size());
                        out.add(c);
                        continue;
                    }
                }
            }
            out.add(c);
        }
        points = out;
    }

    private static double get(Vec3 v, int axis) {
        return axis == 0 ? v.x : axis == 1 ? v.y : v.z;
    }
}
