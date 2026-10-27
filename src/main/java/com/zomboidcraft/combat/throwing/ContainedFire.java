package com.zomboidcraft.combat.throwing;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Fires that can't get away (Josiah 2026-10-27: blasts start fires, left alone they destroy things, but a stray fire
 * must never burn down the world). Each fire remembers where it started: its flames only spread to spots within its
 * reach of there, it holds at most so many flames, and it burns out after its life. While it burns it eats wooden
 * and soft blocks next to its flames. Rain puts flames out; so does water (they can't stand in it).
 */
public final class ContainedFire {
    private static final int STEP = 10;
    private static final List<Fire> FIRES = new ArrayList<>();
    private static int ticks;

    private ContainedFire() {
    }

    private static final class Fire {
        final ServerLevel level;
        final BlockPos origin;
        final double reach;
        final long endAt;
        /** Flames, and for spilled fuel on bare ground the game time it runs out. */
        final Map<BlockPos, Long> flames = new HashMap<>();

        Fire(ServerLevel level, BlockPos origin, double reach, long endAt) {
            this.level = level;
            this.origin = origin;
            this.reach = reach;
            this.endAt = endAt;
        }

        boolean inReach(BlockPos p) {
            return p.distSqr(origin) <= reach * reach;
        }
    }

    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(ContainedFire::tick);
    }

    /** Wood, cloth, leaves, paper and the like: whatever Minecraft lets lava light, and anything you'd cut with an axe. */
    public static boolean flammable(BlockState state) {
        return !state.isAir() && (state.ignitedByLava() || state.is(BlockTags.MINEABLE_WITH_AXE) || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.WOOL) || state.is(BlockTags.WOOL_CARPETS));
    }

    static boolean tracked(ServerLevel level, BlockPos pos) {
        for (Fire f : FIRES) {
            if (f.level == level && f.flames.containsKey(pos)) {
                return true;
            }
        }
        return false;
    }

    /** A molotov's spilled fuel: flames on every spot of ground within radius, burning burnTicks even on stone. */
    public static void pool(ServerLevel level, Vec3 centre, double radius, int burnTicks) {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        long now = level.getGameTime();
        Fire fire = new Fire(level, BlockPos.containing(centre), radius + c.fireReach, now + Math.max(burnTicks, c.fireLifeTicks));
        int r = (int) Math.ceil(radius);
        BlockPos mid = BlockPos.containing(centre);
        for (BlockPos p : BlockPos.betweenClosed(mid.offset(-r, -2, -r), mid.offset(r, 2, r))) {
            if (Vec3.atBottomCenterOf(p).distanceTo(centre) <= radius + 0.3 && level.random.nextFloat() < 0.85f) {
                light(fire, p.immutable(), now + burnTicks - level.random.nextInt(Math.max(1, burnTicks / 3)));
            }
        }
        start(fire);
    }

    /** A blast: a share of the flammable spots within its radius catch, and a few bare ones burn briefly. */
    public static void blast(ServerLevel level, Vec3 centre, double radius) {
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        long now = level.getGameTime();
        Fire fire = new Fire(level, BlockPos.containing(centre), radius + c.fireReach, now + c.fireLifeTicks);
        int r = (int) Math.ceil(radius);
        BlockPos mid = BlockPos.containing(centre);
        for (BlockPos p : BlockPos.betweenClosed(mid.offset(-r, -r, -r), mid.offset(r, r, r))) {
            if (Vec3.atCenterOf(p).distanceTo(centre) > radius || !level.getBlockState(p).isAir()) {
                continue;
            }
            boolean nearFuel = touchesFlammable(level, p);
            float chance = (float) c.blastFireChance * (nearFuel ? 1f : 0.15f);
            if (level.random.nextFloat() < chance) {
                light(fire, p.immutable(), nearFuel ? -1 : now + 60 + level.random.nextInt(80));
            }
        }
        start(fire);
    }

    /** A burning flare's spot: it lights what it lies on, if that burns. */
    public static void spot(ServerLevel level, BlockPos pos) {
        if (!touchesFlammable(level, pos) || tracked(level, pos)) {
            return;
        }
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        Fire fire = new Fire(level, pos, 1 + c.fireReach, level.getGameTime() + c.fireLifeTicks / 2);
        light(fire, pos, -1);
        start(fire);
    }

    private static void start(Fire fire) {
        if (!fire.flames.isEmpty()) {
            FIRES.add(fire);
            fire.level.playSound(null, fire.origin, ModSounds.FIRE_BURN, SoundSource.BLOCKS, 1f, 1f);
        }
    }

    private static boolean light(Fire fire, BlockPos p, long fuelUntil) {
        ServerLevel level = fire.level;
        if (fire.flames.size() >= ZcConfig.get().throwing.fireMaxBlocks || !level.getBlockState(p).isAir()
                || !level.getFluidState(p).isEmpty()) {
            return false;
        }
        BlockState flame = Throwing.FIRE.defaultBlockState();
        if (!flame.canSurvive(level, p)) {
            return false;
        }
        level.setBlock(p, flame, 3);
        fire.flames.put(p, fuelUntil);
        return true;
    }

    private static boolean touchesFlammable(ServerLevel level, BlockPos p) {
        for (Direction d : Direction.values()) {
            if (flammable(level.getBlockState(p.relative(d)))) {
                return true;
            }
        }
        return false;
    }

    private static void tick(MinecraftServer server) {
        if (FIRES.isEmpty() || ++ticks % STEP != 0) {
            return;
        }
        ZcConfig.Throwing c = ZcConfig.get().throwing;
        for (Iterator<Fire> it = FIRES.iterator(); it.hasNext(); ) {
            Fire fire = it.next();
            if (!step(fire, c)) {
                it.remove();
            }
        }
    }

    /** One check of a fire: flames go out, eat what they touch, and creep on. False once it's out. */
    private static boolean step(Fire fire, ZcConfig.Throwing c) {
        ServerLevel level = fire.level;
        RandomSource random = level.random;
        long now = level.getGameTime();
        boolean dying = now >= fire.endAt;
        Set<BlockPos> spread = new HashSet<>();
        for (Iterator<Map.Entry<BlockPos, Long>> it = fire.flames.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Long> e = it.next();
            BlockPos p = e.getKey();
            if (!level.isLoaded(p) || !level.getBlockState(p).is(Throwing.FIRE)) {
                it.remove();        // put out: water, or someone stamped on it
                continue;
            }
            boolean fuel = e.getValue() >= 0 && now < e.getValue();
            boolean rained = level.isRainingAt(p) && random.nextFloat() < c.fireRainChance;
            boolean starved = !fuel && !touchesFlammable(level, p);
            if (rained || starved || dying && random.nextFloat() < 0.35f) {
                level.removeBlock(p, false);
                it.remove();
                continue;
            }
            if (!c.fireSpreads) {
                continue;
            }
            // eat what it touches
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (fire.inReach(q) && flammable(level.getBlockState(q)) && random.nextFloat() < c.fireBurnChance) {
                    level.removeBlock(q, false);
                    spread.add(q.immutable());     // the flames take its place
                }
            }
            // and creep to the spots round it
            if (!dying && random.nextFloat() < c.fireSpreadChance) {
                BlockPos q = p.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
                if (fire.inReach(q) && touchesFlammable(level, q)) {
                    spread.add(q.immutable());
                }
            }
        }
        if (!dying) {
            for (BlockPos q : spread) {
                light(fire, q, -1);
            }
        }
        return !fire.flames.isEmpty();
    }

    /** Puts out every flame within radius of a spot: an extinguisher, or a splash of water. */
    public static int douse(ServerLevel level, Vec3 centre, double radius) {
        int out = 0;
        for (Fire fire : FIRES) {
            if (fire.level != level) {
                continue;
            }
            for (Iterator<BlockPos> it = fire.flames.keySet().iterator(); it.hasNext(); ) {
                BlockPos p = it.next();
                if (Vec3.atCenterOf(p).distanceTo(centre) <= radius) {
                    if (level.getBlockState(p).is(Throwing.FIRE)) {
                        level.removeBlock(p, false);
                    }
                    it.remove();
                    out++;
                }
            }
        }
        BlockPos mid = BlockPos.containing(centre);
        int r = (int) Math.ceil(radius);
        for (BlockPos p : BlockPos.betweenClosed(mid.offset(-r, -r, -r), mid.offset(r, r, r))) {
            if (level.getBlockState(p).is(Throwing.FIRE) || level.getBlockState(p).is(Blocks.FIRE)) {
                level.removeBlock(p, false);
                out++;
            }
        }
        return out;
    }
}
