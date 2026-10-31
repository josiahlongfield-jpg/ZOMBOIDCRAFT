package com.zomboidcraft.combat;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.BashGoal;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Shots through window glass. A bullet goes on through the glass, a little spent, and leaves a cracked hole in it;
 * how many holes a pane takes before it gives depends on the glass (a church's leaded panes go at once, a shop's
 * plate front takes several, glass bricks barely mind). When it gives it shatters the way a zombie breaks it: to
 * its broken frame, or clean away. So shooting out of a building costs you its windows.
 */
public final class GlassShots {
    /** Untouched this long (ticks), the holes in a pane are forgotten (it's as good as it was). */
    private static final long KEPT = 24000;
    /** Holes per block and the tick of the last, per dimension. */
    private static final Map<ResourceKey<Level>, Map<Long, long[]>> HOLES = new HashMap<>();

    /** What's left of a bullet after it goes through a pane. */
    public static final float THROUGH = 0.85f;

    /** Whether a shot goes through this block, as glass: a window that breaks, or a plain pane or block of glass. */
    public static boolean isGlass(BlockState state) {
        if (state.isAir() || Surface.of(state) != Surface.GLASS) {
            return false;
        }
        if (BashGoal.broken(state) != null) {
            return true;
        }
        String path = path(state);
        return state.getSoundType() == SoundType.GLASS && !path.contains("shards") && !path.contains("frame") && !path.contains("boarded");
    }

    /** How many holes this glass takes before it gives (ZcConfig guns.glassHoles*). */
    public static int holes(BlockState state) {
        ZcConfig.Guns c = ZcConfig.get().guns;
        String path = path(state);
        if (path.contains("_broken")) {
            return 1;                       // only shards left in the frame
        }
        if (path.contains("glassblock")) {
            return c.glassHolesBlock;       // glass bricks
        }
        if (state.is(Blocks.GLASS_PANE) || path.contains("pane") || path.startsWith("ch_win") || path.contains("_six_")
                || path.contains("paintwin")) {
            return c.glassHolesSmall;       // small panes and leaded church glass
        }
        if (path.contains("store") || path.contains("shop") || path.contains("teller") || path.startsWith("tx_")
                || path.contains("pool") || path.contains("spandrel")) {
            return c.glassHolesShop;        // plate glass: shop fronts, counters, station and office glazing
        }
        if (path.contains("vessel") || path.contains("roof") || path.contains("rail")) {
            return c.glassHolesLarge;
        }
        return c.glassHolesWindow;          // a house or flat's window, a plain glass block
    }

    /**
     * A bullet went through glass at this hit: a hole in it (drawn by each client), the tinkle of it, and the pane
     * shattered if that was one too many. Returns whether it shattered.
     */
    public static boolean shot(ServerLevel level, Player player, BlockHitResult hit, Vec3 dir, BlockState state, boolean sound) {
        BlockPos pos = hit.getBlockPos();
        Impacts.block(level, hit, dir, state, 0.6f);
        if (sound) {
            Vec3 p = hit.getLocation();
            level.playSound(null, p.x, p.y, p.z, Surface.GLASS.impact(), SoundSource.PLAYERS, 0.9f,
                    0.92f + player.getRandom().nextFloat() * 0.16f);
        }
        Map<Long, long[]> holes = HOLES.computeIfAbsent(level.dimension(), k -> new HashMap<>());
        long now = level.getGameTime();
        if (holes.size() > 4096) {
            holes.values().removeIf(h -> now - h[1] > KEPT);
        }
        long[] h = holes.computeIfAbsent(pos.asLong(), k -> new long[2]);
        if (now - h[1] > KEPT) {
            h[0] = 0;
        }
        h[0]++;
        h[1] = now;
        if (h[0] < holes(state)) {
            return false;
        }
        holes.remove(pos.asLong());
        BlockState next = BashGoal.broken(state);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 0.9f + player.getRandom().nextFloat() * 0.2f);
        if (next != null && !next.isAir()) {
            level.setBlockAndUpdate(pos, next);
        } else {
            level.destroyBlock(pos, false, player);
        }
        return true;
    }

    /** Where a ray along dir leaves the block at pos, a hair beyond: where to carry on looking from. */
    public static Vec3 past(BlockPos pos, Vec3 from, Vec3 dir) {
        double best = Double.MAX_VALUE;
        double[] o = {from.x, from.y, from.z}, d = {dir.x, dir.y, dir.z}, lo = {pos.getX(), pos.getY(), pos.getZ()};
        for (int a = 0; a < 3; a++) {
            if (Math.abs(d[a]) < 1e-9) {
                continue;
            }
            double wall = d[a] > 0 ? lo[a] + 1 : lo[a];
            double t = (wall - o[a]) / d[a];
            if (t >= 0 && t < best) {
                best = t;
            }
        }
        return best == Double.MAX_VALUE ? from.add(dir.scale(0.05)) : from.add(dir.scale(best + 1e-3));
    }

    private static String path(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }

    private GlassShots() {
    }
}
