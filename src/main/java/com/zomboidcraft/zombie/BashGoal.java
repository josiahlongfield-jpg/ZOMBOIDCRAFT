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

    /** Untouched this long (ticks), a dented door or cracked window is forgotten. */
    private static final int DENT_KEPT = 6000;
    /** Blows on each door and window, shared by everyone pounding on it: blow count and the tick of the last. */
    private static final java.util.Map<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
            java.util.Map<Long, long[]>> DENTS = new java.util.HashMap<>();

    private final Zombie zombie;
    private BlockPos block;
    private boolean door, glass;
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
        return glass ? isWindow(state) : door ? isShutDoor(state) : bashable(state);
    }

    /** Glass in a window or a shop front (not a lamp), or a broken one with shards still in: it gives under enough blows. */
    private static boolean isWindow(BlockState state) {
        return broken(state) != null;
    }

    private static final java.util.Map<net.minecraft.world.level.block.Block, java.util.Optional<net.minecraft.world.level.block.Block>>
            NEXT = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * What a window becomes when it gives: glass (or lit) to its _broken block, broken to its _empty one (an open frame
     * a zombie can climb through), plain glass to nothing. Null for anything that isn't a window, or one already open.
     */
    static @Nullable BlockState broken(BlockState state) {
        net.minecraft.world.level.block.Block next = NEXT.computeIfAbsent(state.getBlock(), BashGoal::nextBlock).orElse(null);
        if (next == null) {
            return null;
        }
        BlockState out = next.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> property : state.getProperties()) {
            out = copy(state, out, property);
        }
        return out;
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to,
            net.minecraft.world.level.block.state.properties.Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    private static java.util.Optional<net.minecraft.world.level.block.Block> nextBlock(net.minecraft.world.level.block.Block block) {
        net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        String n = id.getPath();
        if (!id.getNamespace().equals("zomboidcraft")) {
            BlockState s = block.defaultBlockState();
            // vanilla glass and panes break clean away
            return s.getSoundType() == SoundType.GLASS && s.getLightEmission() == 0
                    ? java.util.Optional.of(net.minecraft.world.level.block.Blocks.AIR) : java.util.Optional.empty();
        }
        String to;
        if (n.contains("_glass_") || n.contains("_lit_")) {
            to = n.replace("_glass_", "_broken_").replace("_lit_", "_broken_");      // ext_win_*_glass_hi
        } else if (n.contains("_broken")) {
            to = n.replace("_broken", "_empty");                                    // window_broken, ext_win_*_broken_hi
        } else if (n.endsWith("_lit")) {
            to = n.substring(0, n.length() - 4) + "_broken";                         // window_lit, ch_win_*_lit
        } else {
            to = n + "_broken";                                                      // window, navy_window, dn_win_*, ch_win_*
        }
        if (!n.contains("win") || to.equals(n)) {
            return java.util.Optional.empty();
        }
        net.minecraft.resources.Identifier next = net.minecraft.resources.Identifier.fromNamespaceAndPath("zomboidcraft", to);
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.containsKey(next)
                ? java.util.Optional.of(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getValue(next)) : java.util.Optional.empty();
    }

    /** Boards, a metal sheet or a spiked log wall: built things it beats down. */
    private static boolean bashable(BlockState state) {
        return state.is(ModBlocks.BARRICADE) || com.zomboidcraft.building.BuildBlocks.bashDamage(state) != null;
    }

    private static String blockPath(BlockState state) {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
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
            hitDoor(level, state);
        } else {
            hitBarricade(level, state);
        }
    }

    /**
     * A blow on a shut door or a window. The blows add up on the door itself, whoever lands them, so one zombie takes
     * a couple of minutes on a door and a crowd is through in seconds (ZcConfig zombies.doorHits, windowHits).
     */
    private void hitDoor(ServerLevel level, BlockState state) {
        ZcConfig.Zombies config = ZcConfig.get().zombies;
        // a broken window only has its shards to knock out: a third of the blows
        int needed = Math.max(1, glass ? (blockPath(state).contains("_broken") ? config.windowHits / 3 : config.windowHits)
                : config.doorHits);
        // a door is two blocks: its blows are kept on the lower half
        BlockPos at = !glass && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                ? block.below() : block;
        java.util.Map<Long, long[]> dents = DENTS.computeIfAbsent(level.dimension(), k -> new java.util.HashMap<>());
        long now = level.getGameTime();
        if (dents.size() > 4096) {
            dents.values().removeIf(d -> now - d[1] > DENT_KEPT);
        }
        long[] dent = dents.computeIfAbsent(at.asLong(), k -> new long[2]);
        if (now - dent[1] > DENT_KEPT) {
            dent[0] = 0;
        }
        dent[0]++;
        dent[1] = now;
        if (dent[0] >= needed) {
            dents.remove(at.asLong());
            BlockState next = glass ? broken(state) : null;
            if (next != null && !next.isAir()) {
                level.setBlockAndUpdate(block, next);
                level.playSound(null, block, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 1f, 1f);
                return;
            }
            level.destroyBlock(block, false);
            level.playSound(null, block, glass ? SoundEvents.GLASS_BREAK : SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR,
                    SoundSource.HOSTILE, 1f, 1f);
            return;
        }
        level.destroyBlockProgress(at.hashCode(), block, (int) Math.min(9, dent[0] * 10 / needed));
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
        boolean bestDoor = false, bestGlass = false;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 1, 1))) {
            BlockState state = zombie.level().getBlockState(pos);
            boolean isDoor, isGlass = false;
            if (config.bashBarricades && bashable(state)) {
                isDoor = false;
            } else if (config.bashDoors && (isShutDoor(state) || (isGlass = isWindow(state)))) {
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
                bestGlass = isGlass;
            }
        }
        block = best;
        door = bestDoor;
        glass = bestGlass;
        return best != null;
    }
}
