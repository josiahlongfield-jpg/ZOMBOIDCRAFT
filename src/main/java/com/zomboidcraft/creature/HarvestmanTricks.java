package com.zomboidcraft.creature;

import com.zomboidcraft.zombie.Noise;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The games a Harvestman plays with the one it hunts, beyond stalking and striking (HarvestmanMind weighs them in with
 * the rest):
 *
 * <ul>
 * <li>DOOR: they've shut themselves in a room; it goes to the far side of the door and settles there, breathing, the
 * plates ticking. Opened on it with a light, it's gone; opened on it in the dark, it may not be.</li>
 * <li>HERD: indoors, with two ways out, it shows itself at the nearer one and waits over the other.</li>
 * <li>RETREAT: hit, it runs like it's badly hurt, and the way it runs leads under where it waits for whoever follows.</li>
 * <li>HORDE: it makes a din near them to bring the dead down on them, and comes in while they're busy.</li>
 * <li>SHADOW: out in the open, a shape on the rooftops keeping pace with them, never closer.</li>
 * <li>SPLIT: with others round them, it makes noises out past their friends to draw them off.</li>
 * <li>DISPLAY: on its patient nights, it stands out in plain sight of them with its red lit, just to let them know it's
 * watching. Come for it and the red goes out and it's gone.</li>
 * </ul>
 *
 * Each learns from how it goes (herding and fake retreats have a bias of their own, saved with the mind).
 */
final class HarvestmanTricks {
    private final HarvestmanMind m;
    private final HarvestmanEntity h;
    /** How well herding and fake retreats have worked for it (0..1). */
    double herdBias = 0.3, retreatBias = 0.3;
    /** When each was last tried (game time). */
    private long doorAt = -100_000, herdAt = -100_000, retreatAt = -100_000, hordeAt = -100_000, shadowAt = -100_000,
            splitAt = -100_000, displayAt = -100_000;
    /** DISPLAY: how far off they were when it lit up. */
    private double shownD;
    /** The door it waits behind (lower half), and the open state it found it in. */
    private BlockPos door;
    /** Herding: where it shows itself, and the way out it waits over. */
    private Vec3 showAt, waitOver;
    /** Splitting a group: the friend it's drawing off. */
    private Player friend;
    private int noises;
    /** Its own din (HORDE), so it doesn't start at it. */
    private Vec3 madeAt;
    private long madeWhen = -100;
    /** Where its target was last time it looked, and how they're moving (blocks a tick, flat). */
    private Vec3 trackAt;
    private Vec3 trackVel = Vec3.ZERO;

    HarvestmanTricks(HarvestmanMind m) {
        this.m = m;
        this.h = m.h;
    }

    static boolean hunting(int plan) {
        return plan == HarvestmanMind.DOOR || plan == HarvestmanMind.HERD || plan == HarvestmanMind.RETREAT
                || plan == HarvestmanMind.HORDE || plan == HarvestmanMind.SHADOW || plan == HarvestmanMind.SPLIT
                || plan == HarvestmanMind.DISPLAY;
    }

    boolean sneaking() {
        int p = m.plan;
        return p == HarvestmanMind.DOOR || p == HarvestmanMind.SHADOW || p == HarvestmanMind.SPLIT || p == HarvestmanMind.HORDE
                || p == HarvestmanMind.HERD && m.phase >= 2 || p == HarvestmanMind.RETREAT && m.phase >= 1
                || p == HarvestmanMind.DISPLAY && m.phase == 0;
    }

    /** A noise it made itself (it doesn't turn to its own din). */
    boolean madeIt(ServerLevel level, Vec3 pos) {
        return madeAt != null && level.getGameTime() - madeWhen < 4 && madeAt.distanceToSqr(pos) < 1;
    }

    /** Keeps up with how its target is moving (the server doesn't know a player's velocity): every few ticks. */
    void track(ServerPlayer t) {
        if (t == null) {
            trackAt = null;
            trackVel = Vec3.ZERO;
            return;
        }
        Vec3 p = t.position();
        if (trackAt != null && trackAt.distanceToSqr(p) < 64) {
            trackVel = trackVel.lerp(p.subtract(trackAt).multiply(1, 0, 1).scale(1 / 5.0), 0.5);
        }
        trackAt = p;
    }

    // ------------------------------------------------------------------------------------------------ weighing

    /** Adds its tricks' weights for a target d blocks off (alone: nobody else within 12 of them). */
    void weigh(ServerLevel level, ServerPlayer t, double d, boolean alone, double[] w) {
        long now = level.getGameTime();
        boolean indoors = !level.canSeeSky(BlockPos.containing(t.getEyePosition()));
        if (indoors && d < 32 && now - doorAt > 20 * 60 * 4 && shutDoor(level, t) != null) {
            w[HarvestmanMind.DOOR] = 0.45;
        }
        if (indoors && d < 30 && now - herdAt > 20 * 60 * 5) {
            w[HarvestmanMind.HERD] = 0.15 + 0.35 * herdBias;
        }
        if (d < 40 && now - hordeAt > 20 * 60 * 10 && walkers(level, t.position(), 48) >= 5) {
            w[HarvestmanMind.HORDE] = 0.2;
        }
        if (!indoors && d < 48 && now - shadowAt > 20 * 60 * 4 && trackVel.lengthSqr() > 0.002) {
            w[HarvestmanMind.SHADOW] = 0.35;
        }
        if (HarvestmanNights.patient(level) && d < 64 && now - displayAt > 20 * 45) {
            w[HarvestmanMind.DISPLAY] = 2.5;
        }
        if (!alone && d < 32 && now - splitAt > 20 * 60 * 3) {
            w[HarvestmanMind.SPLIT] = 0.45;
        }
    }

    /** Starts a trick on its target (it stays idle if the trick can't be played from here). */
    void begin(ServerLevel level, int plan, ServerPlayer t) {
        if (t == null) {
            return;
        }
        switch (plan) {
            case HarvestmanMind.DOOR -> beginDoor(level, t);
            case HarvestmanMind.HERD -> beginHerd(level, t);
            case HarvestmanMind.RETREAT -> beginRetreat(level, t);
            case HarvestmanMind.HORDE -> beginHorde(level, t);
            case HarvestmanMind.SHADOW -> beginShadow(level, t);
            case HarvestmanMind.SPLIT -> beginSplit(level, t);
            case HarvestmanMind.DISPLAY -> beginDisplay(level, t);
            default -> {
            }
        }
        if (m.plan == plan) {
            m.quarry = t;
        }
    }

    void act(ServerLevel level) {
        if (!(m.quarry instanceof ServerPlayer t) || !t.isAlive() || t.level() != level || t.isSpectator()) {
            m.end();
            return;
        }
        switch (m.plan) {
            case HarvestmanMind.DOOR -> door(level, t);
            case HarvestmanMind.HERD -> herd(level, t);
            case HarvestmanMind.RETREAT -> retreat(level, t);
            case HarvestmanMind.HORDE -> horde(level, t);
            case HarvestmanMind.SHADOW -> shadow(level, t);
            case HarvestmanMind.SPLIT -> split(level, t);
            case HarvestmanMind.DISPLAY -> display(level, t);
            default -> m.end();
        }
    }

    // ------------------------------------------------------------------------------------------------ DOOR

    /** A shut door within 7 blocks of them, between them and the way out (lower half), or null. */
    private BlockPos shutDoor(ServerLevel level, ServerPlayer t) {
        BlockPos at = t.blockPosition(), best = null;
        double bd = 1e9;
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-7, -2, -7), at.offset(7, 2, 7))) {
            BlockState s = level.getBlockState(q);
            if (!(s.getBlock() instanceof DoorBlock) || s.getValue(DoorBlock.OPEN) || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            double d = Vec3.atCenterOf(q).distanceToSqr(t.position());
            if (d < bd && outside(level, q, t) != null) {
                bd = d;
                best = q.immutable();
            }
        }
        return best;
    }

    /** The cell on the far side of a door from them (one clear for it to wait in), or null. */
    private static BlockPos outside(ServerLevel level, BlockPos door, Player t) {
        Direction f = level.getBlockState(door).getValue(DoorBlock.FACING);
        BlockPos a = door.relative(f), b = door.relative(f.getOpposite());
        BlockPos far = Vec3.atCenterOf(a).distanceToSqr(t.position()) > Vec3.atCenterOf(b).distanceToSqr(t.position()) ? a : b;
        return level.isEmptyBlock(far) && level.isEmptyBlock(far.above()) ? far : null;
    }

    private void beginDoor(ServerLevel level, ServerPlayer t) {
        BlockPos d = shutDoor(level, t);
        BlockPos out = d != null ? outside(level, d, t) : null;
        if (out == null) {
            return;
        }
        door = d;
        doorAt = level.getGameTime();
        Vec3 away = Vec3.atBottomCenterOf(out).subtract(Vec3.atBottomCenterOf(d)).normalize();
        // a body's length back from the door, on the floor, facing it
        Vec3 wait = Vec3.atBottomCenterOf(out).add(away.scale(0.6)).add(0, HarvestmanEntity.STAND, 0);
        m.start(HarvestmanMind.DOOR, wait, 1.7);
        m.h.wantFloor = true;
    }

    private void door(ServerLevel level, ServerPlayer t) {
        BlockState s = level.getBlockState(door);
        if (!(s.getBlock() instanceof DoorBlock)) {
            m.end();
            return;
        }
        Vec3 at = Vec3.atCenterOf(door);
        h.wantFloor = true;
        if (m.phase == 0) {
            if (h.goal == null || m.near(h.goal, 1.2)) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
            } else if (m.planTicks > 20 * 45) {
                m.end();
            }
            return;
        }
        h.goal = null;
        h.look = at;
        if (s.getValue(DoorBlock.OPEN)) {
            // opened on it: with a light, it's gone; in the dark, it may come through
            boolean lit = level.getBrightness(LightLayer.BLOCK, door) >= 11 || holdsLight(t);
            if (!lit && t.distanceToSqr(h) < 7 * 7 && h.getHealth() > h.getMaxHealth() * 0.6 && h.getRandom().nextFloat() < 0.6f) {
                m.startStrike(level, t);
                if (m.plan == HarvestmanMind.STRIKE) {
                    return;
                }
            }
            level.playSound(null, at.x, at.y, at.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 1.2f, 1.25f);
            m.end();
            m.start(HarvestmanMind.HIDE, m.hideFrom(level, t), HarvestmanMind.BURST);
            return;
        }
        // settled there: its breath, slow, and now and then its plates against the door
        if (m.phaseTicks % 70 == 10) {
            Vec3 c = h.centre();
            level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.BREATHE, SoundSource.HOSTILE, 0.7f, 0.9f + h.getRandom().nextFloat() * 0.15f);
        }
        if (m.phaseTicks % 230 == 120 && h.getRandom().nextFloat() < 0.6f) {
            level.playSound(null, at.x, at.y, at.z, h.getRandom().nextBoolean() ? HarvestmanSounds.SCRAPE : HarvestmanSounds.TAP,
                    SoundSource.HOSTILE, 0.35f, 0.8f);
        }
        // they've gone off elsewhere, or it has waited long enough: it leaves as quietly as it came
        if (t.position().distanceTo(at) > 14 || m.phaseTicks > 20 * (60 + 60 * m.patience)) {
            m.end();
            m.start(HarvestmanMind.HIDE, m.spot(HarvestmanMind.HIDE, 14), 1.6);
        }
    }

    /** Are they carrying a light (a torch, a lantern, a flashlight) in either hand? */
    private static boolean holdsLight(Player t) {
        return isLight(t.getMainHandItem()) || isLight(t.getOffhandItem());
    }

    private static boolean isLight(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        return id.contains("torch") || id.contains("lantern") || id.contains("flashlight") || id.contains("flare");
    }

    // ------------------------------------------------------------------------------------------------ HERD

    private void beginHerd(ServerLevel level, ServerPlayer t) {
        List<BlockPos> doors = new ArrayList<>();
        BlockPos at = t.blockPosition();
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-16, -2, -16), at.offset(16, 2, 16))) {
            BlockState s = level.getBlockState(q);
            if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                doors.add(q.immutable());
            }
        }
        if (doors.size() < 2) {
            return;
        }
        doors.sort((a, b) -> Double.compare(Vec3.atCenterOf(a).distanceToSqr(t.position()), Vec3.atCenterOf(b).distanceToSqr(t.position())));
        BlockPos near = doors.get(0);
        for (int i = 1; i < doors.size(); i++) {
            BlockPos far = doors.get(i);
            if (far.distSqr(near) < 6 * 6) {
                continue;
            }
            // a ceiling over the far way out, inside, to wait on
            Vec3 under = Vec3.atBottomCenterOf(far).add(towards(far, t).scale(1.5)).add(0, 1, 0);
            BlockHitResult up = m.cast(level, under, under.add(0, 10, 0));
            if (up.getType() == HitResult.Type.MISS || up.getLocation().y - under.y < 2) {
                continue;
            }
            // where it shows itself: just inside the near door, in their sight
            showAt = Vec3.atBottomCenterOf(near).add(towards(near, t).scale(1.5)).add(0, HarvestmanEntity.STAND, 0);
            waitOver = up.getLocation().add(0, -1.2, 0);
            herdAt = level.getGameTime();
            m.start(HarvestmanMind.HERD, showAt, HarvestmanMind.BURST * 0.6);
            return;
        }
    }

    /** From a door toward them, flat. */
    private static Vec3 towards(BlockPos door, Player t) {
        Vec3 d = t.position().subtract(Vec3.atBottomCenterOf(door)).multiply(1, 0, 1);
        return d.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : d.normalize();
    }

    private void herd(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        switch (m.phase) {
            case 0 -> {
                if (m.near(showAt, 1.5) || m.planTicks > 20 * 20) {
                    m.phase = 1;
                    m.phaseTicks = 0;
                    h.goal = null;
                    h.rearWant = 0.6;
                    level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.HISS, SoundSource.HOSTILE, 1.6f, 0.85f);
                }
            }
            case 1 -> {
                // standing in the way, reared, for long enough that they've seen it
                h.look = t.getEyePosition();
                if (m.watchedTicks > 10 || m.phaseTicks > 20 * 5) {
                    h.rearWant = 0;
                    m.phase = 2;
                    m.phaseTicks = 0;
                    h.goal = waitOver;
                    h.goalSpeed = HarvestmanMind.BURST * 0.7;
                }
            }
            case 2 -> {
                if (m.near(waitOver, 1.0) || m.phaseTicks > 20 * 20) {
                    m.phase = 3;
                    m.phaseTicks = 0;
                    h.goal = null;
                }
            }
            default -> {
                h.look = t.getEyePosition();
                Vec3 flat = t.position().subtract(c);
                if (Math.hypot(flat.x, flat.z) < 3.5 && c.y > t.getEyeY() && !HarvestmanMind.lookingAt(t, c)) {
                    herdBias = Math.min(1, herdBias + 0.1);
                    m.startStrike(level, t);
                    return;
                }
                if (m.phaseTicks > 20 * 45) {
                    herdBias = Math.max(0.05, herdBias - 0.05);
                    m.end();
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ RETREAT

    /**
     * Hit, but not badly: now and then it runs like it's badly hurt (the same shriek, the same speed) and the way it runs
     * leads in under a ceiling, where it turns and waits over whoever follows. True if it took this up.
     */
    boolean fakeRetreat(ServerLevel level, ServerPlayer by) {
        int p = m.plan;
        if (h.held() != null || p == HarvestmanMind.FLEE || p == HarvestmanMind.REST || p == HarvestmanMind.RETREAT
                || h.getHealth() < h.getMaxHealth() * 0.5 || level.getGameTime() - retreatAt < 20 * 60 * 3
                || by != m.target(level) || HarvestmanNights.patient(level)
                || h.getRandom().nextDouble() > 0.25 + 0.5 * retreatBias) {
            return false;
        }
        beginRetreat(level, by);
        if (m.plan != HarvestmanMind.RETREAT) {
            return false;
        }
        m.quarry = by;
        level.playSound(null, h.getX(), h.getY(), h.getZ(), HarvestmanSounds.HURT, SoundSource.HOSTILE, 1.4f, 1f);
        return true;
    }

    private void beginRetreat(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        Vec3 away = c.subtract(t.position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
        Vec3 best = null;
        for (int k = 0; k < 20 && best == null; k++) {
            double a = (h.getRandom().nextDouble() - 0.5) * 1.6, r = 10 + h.getRandom().nextDouble() * 12;
            Vec3 d = new Vec3(away.x * Math.cos(a) - away.z * Math.sin(a), 0, away.x * Math.sin(a) + away.z * Math.cos(a));
            Vec3 p = c.add(d.scale(r));
            BlockHitResult floor = m.cast(level, p.add(0, 3, 0), p.add(0, -8, 0));
            if (floor.getType() == HitResult.Type.MISS) {
                continue;
            }
            Vec3 f = floor.getLocation().add(0, 1, 0);
            BlockHitResult roof = m.cast(level, f, f.add(0, 9, 0));
            if (roof.getType() != HitResult.Type.MISS && roof.getLocation().y - f.y > 2) {
                best = roof.getLocation().add(0, -1.2, 0);
            }
        }
        if (best == null) {
            return;
        }
        retreatAt = level.getGameTime();
        m.start(HarvestmanMind.RETREAT, best, HarvestmanMind.SPRINT);
    }

    private void retreat(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        if (m.phase == 0) {
            if (h.goal == null || m.near(h.goal, 1.0) || m.planTicks > 20 * 12) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
            }
            return;
        }
        h.look = t.getEyePosition();
        Vec3 flat = t.position().subtract(c);
        if (Math.hypot(flat.x, flat.z) < 4 && c.y > t.getEyeY() && !HarvestmanMind.lookingAt(t, c)) {
            retreatBias = Math.min(1, retreatBias + 0.1);
            m.startStrike(level, t);
            return;
        }
        if (m.phaseTicks > 20 * 25) {
            // they didn't follow
            retreatBias = Math.max(0.05, retreatBias - 0.04);
            m.end();
        }
    }

    // ------------------------------------------------------------------------------------------------ HORDE

    private static int walkers(ServerLevel level, Vec3 at, double r) {
        return level.getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(r), z -> ZombieTypes.isWalker(z) && z.isAlive()).size();
    }

    private void beginHorde(ServerLevel level, ServerPlayer t) {
        // a din a few blocks from them: something knocked over, metal on metal, loud enough to carry down the street
        Vec3 eye = t.getEyePosition(), best = null;
        for (int k = 0; k < 16 && best == null; k++) {
            Vec3 d = m.randomDir().multiply(1, 0.3, 1).normalize();
            BlockHitResult hit = m.cast(level, eye, eye.add(d.scale(9)));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(eye) > 3) {
                best = hit.getLocation().add(HarvestmanMind.normal(hit).scale(0.3));
            }
        }
        if (best == null) {
            return;
        }
        hordeAt = level.getGameTime();
        m.start(HarvestmanMind.HORDE, m.stalkSpot(level, t), 1.6);
        din(level, best);
        noises = 1;
    }

    private void din(ServerLevel level, Vec3 at) {
        HarvestmanSigns.decoyNoise(level, h, at);
        level.playSound(null, at.x, at.y, at.z, HarvestmanSounds.CLANG, SoundSource.HOSTILE, 2.2f, 0.6f);
        madeAt = at;
        madeWhen = level.getGameTime();
        Noise.emit(level, at, 40);
    }

    private void horde(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        double d = t.position().distanceTo(c);
        h.look = t.getEyePosition();
        if (m.watched) {
            h.goal = null;
        } else if (m.planTicks % 30 == 0) {
            h.goal = m.stalkSpot(level, t);
        }
        // once more, if the first didn't carry
        if (noises == 1 && m.planTicks == 20 * 12) {
            noises = 2;
            din(level, t.getEyePosition().add(m.randomDir().multiply(5, 0.5, 5)));
        }
        // the dead on them, and their eyes on the dead: it comes in
        int round = walkers(level, t.position(), 6);
        if (round >= 2 && d < 12 && m.planTicks % 10 == 0 && !HarvestmanMind.lookingAt(t, c)) {
            m.startStrike(level, t);
            return;
        }
        if (m.planTicks > 20 * 45) {
            m.end();
        }
    }

    // ------------------------------------------------------------------------------------------------ SHADOW

    private void beginShadow(ServerLevel level, ServerPlayer t) {
        Vec3 p = roofSpot(level, t);
        if (p == null) {
            return;
        }
        shadowAt = level.getGameTime();
        m.start(HarvestmanMind.SHADOW, p, HarvestmanMind.BURST);
    }

    /**
     * A rooftop off to their side and a little ahead of where they're going, 14-24 blocks off, under the open sky and in
     * their sight (so it stands against the sky), or null.
     */
    private Vec3 roofSpot(ServerLevel level, ServerPlayer t) {
        Vec3 eye = t.getEyePosition(), go = trackVel;
        Vec3 ahead = go.lengthSqr() > 1e-4 ? go.normalize() : t.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 24; k++) {
            double side = h.getRandom().nextBoolean() ? 1 : -1, a = side * (0.6 + h.getRandom().nextDouble() * 0.9);
            double r = 14 + h.getRandom().nextDouble() * 10;
            Vec3 d = new Vec3(ahead.x * Math.cos(a) - ahead.z * Math.sin(a), 0, ahead.x * Math.sin(a) + ahead.z * Math.cos(a));
            Vec3 col = t.position().add(d.scale(r)).add(ahead.scale(4));
            BlockHitResult top = m.cast(level, new Vec3(col.x, eye.y + 30, col.z), new Vec3(col.x, t.getY() - 2, col.z));
            if (top.getType() == HitResult.Type.MISS || top.getDirection() != Direction.UP) {
                continue;
            }
            Vec3 roof = top.getLocation();
            if (roof.y < t.getY() + 4 || !level.canSeeSky(BlockPos.containing(roof.add(0, 0.5, 0)))) {
                continue;
            }
            Vec3 stand = roof.add(0, HarvestmanEntity.STAND, 0);
            if (m.cast(level, eye, stand).getType() != HitResult.Type.MISS) {
                continue;
            }
            double score = -Math.abs(r - 18) * 0.2 - stand.distanceTo(h.centre()) * 0.02 + h.getRandom().nextDouble();
            if (score > bestScore) {
                bestScore = score;
                best = stand;
            }
        }
        return best;
    }

    private void shadow(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        h.look = t.getEyePosition();
        boolean indoors = !level.canSeeSky(BlockPos.containing(t.getEyePosition()));
        if (m.watched) {
            // looked at: dead still against the sky; looked at for long, it drops back behind the roof's edge
            h.goal = null;
            if (m.watchedTicks > 20 * 6) {
                m.end();
                m.start(HarvestmanMind.HIDE, m.hideFrom(level, t), HarvestmanMind.BURST);
            }
            return;
        }
        // keeping pace: a new roof a little ahead of them, never closer than 14
        if (m.planTicks % 30 == 0 || h.goal == null || t.position().distanceTo(c) < 14) {
            Vec3 p = roofSpot(level, t);
            if (p != null) {
                h.goal = p;
            }
        }
        if (indoors && m.phaseTicks > 20 * 5 || m.planTicks > 20 * 90 || t.position().distanceTo(c) > 60) {
            m.end();
        }
        if (!indoors) {
            m.phaseTicks = 0;
        }
    }

    // ------------------------------------------------------------------------------------------------ DISPLAY

    /** Standing lit up for them to see (its red on). */
    boolean showing() {
        return m.plan == HarvestmanMind.DISPLAY && m.phase == 1;
    }

    private void beginDisplay(ServerLevel level, ServerPlayer t) {
        Vec3 p = showSpot(level, t);
        if (p == null) {
            return;
        }
        displayAt = level.getGameTime();
        m.start(HarvestmanMind.DISPLAY, p, HarvestmanMind.BURST);
    }

    /**
     * Somewhere 22-40 blocks off, in front of where they're facing and in plain sight of them, high up for choice (a
     * rooftop, a ledge, a rise), or null.
     */
    private Vec3 showSpot(ServerLevel level, ServerPlayer t) {
        Vec3 eye = t.getEyePosition(), look = t.getLookAngle().multiply(1, 0, 1);
        look = look.lengthSqr() > 1e-4 ? look.normalize() : new Vec3(1, 0, 0);
        Vec3 best = null;
        double bestScore = -1e9;
        for (int k = 0; k < 32; k++) {
            double a = (h.getRandom().nextDouble() - 0.5) * Math.toRadians(100), r = 22 + h.getRandom().nextDouble() * 18;
            Vec3 d = new Vec3(look.x * Math.cos(a) - look.z * Math.sin(a), 0, look.x * Math.sin(a) + look.z * Math.cos(a));
            Vec3 col = t.position().add(d.scale(r));
            BlockHitResult top = m.cast(level, new Vec3(col.x, eye.y + 30, col.z), new Vec3(col.x, t.getY() - 8, col.z));
            if (top.getType() == HitResult.Type.MISS || top.getDirection() != Direction.UP) {
                continue;
            }
            Vec3 stand = top.getLocation().add(0, HarvestmanEntity.STAND, 0);
            if (m.cast(level, eye, stand).getType() != HitResult.Type.MISS) {
                continue;
            }
            double score = Math.min(12, stand.y - t.getY()) * 0.25 - Math.abs(a) * 0.8 - stand.distanceTo(h.centre()) * 0.01
                    + h.getRandom().nextDouble() * 0.5;
            if (score > bestScore) {
                bestScore = score;
                best = stand;
            }
        }
        return best;
    }

    private void display(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        double d = t.position().distanceTo(c);
        h.look = t.getEyePosition();
        if (m.phase == 0) {
            // getting there dark; once it's there (or near enough, and they can see it), the red comes up
            boolean seen = m.cast(level, t.getEyePosition(), c).getType() == HitResult.Type.MISS;
            if (h.goal == null || m.near(h.goal, 2) || m.planTicks > 20 * 25 && seen) {
                m.phase = 1;
                m.phaseTicks = 0;
                h.goal = null;
                h.rearWant = 0.6;
                shownD = d;
                level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.CALL, SoundSource.HOSTILE, 1.2f, 0.8f);
            } else if (m.planTicks > 20 * 40 || d > 72) {
                m.end();
            }
            return;
        }
        // lit up and still, facing them. Come for it and the red goes out and it's gone, fast
        Vec3 to = c.subtract(t.position()).multiply(1, 0, 1);
        double closing = to.lengthSqr() > 1e-4 ? trackVel.dot(to.normalize()) : 0;
        boolean chased = d < 14 || d < shownD - 6 || closing > 0.12 && d < shownD - 2;
        if (chased || m.phaseTicks > 20 * 75 || d > 80) {
            vanish(level, t, chased);
        }
    }

    /** A gunshot (from) while it's out to be seen: true if it went (lights out, scurrying off out of sight). */
    boolean shotAt(ServerLevel level, Vec3 from) {
        if (m.plan != HarvestmanMind.DISPLAY || !(m.quarry instanceof ServerPlayer t) || from.distanceToSqr(h.centre()) > 80 * 80) {
            return false;
        }
        vanish(level, t, true);
        return true;
    }

    private void vanish(ServerLevel level, ServerPlayer t, boolean chased) {
        displayAt = level.getGameTime() + (chased ? 20 * 75 : 0);
        Vec3 away = m.hideFrom(level, t);
        m.end();
        if (away != null) {
            m.start(HarvestmanMind.HIDE, away, HarvestmanMind.SPRINT);
        }
    }

    // ------------------------------------------------------------------------------------------------ SPLIT

    private void beginSplit(ServerLevel level, ServerPlayer t) {
        Player best = null;
        double bd = 16 * 16;
        for (Player p : level.players()) {
            if (p != t && !p.isSpectator() && p.distanceToSqr(t) < bd) {
                bd = p.distanceToSqr(t);
                best = p;
            }
        }
        if (best == null) {
            return;
        }
        friend = best;
        splitAt = level.getGameTime();
        noises = 0;
        m.start(HarvestmanMind.SPLIT, m.stalkSpot(level, t), 1.5);
        lure(level, t);
    }

    /** A noise out past their friend, on the far side from them: the friend goes to look, away from them. */
    private boolean lure(ServerLevel level, ServerPlayer t) {
        Vec3 fe = friend.getEyePosition();
        Vec3 out = fe.subtract(t.getEyePosition()).multiply(1, 0, 1);
        out = out.lengthSqr() < 1e-4 ? m.randomDir().multiply(1, 0, 1).normalize() : out.normalize();
        for (int k = 0; k < 12; k++) {
            Vec3 d = out.add(m.randomDir().multiply(0.5, 0.15, 0.5)).normalize();
            BlockHitResult hit = m.cast(level, fe, fe.add(d.scale(6 + h.getRandom().nextDouble() * 6)));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceTo(fe) > 3) {
                HarvestmanSigns.decoyNoise(level, h, hit.getLocation().add(HarvestmanMind.normal(hit).scale(0.3)));
                noises++;
                return true;
            }
        }
        return false;
    }

    private void split(ServerLevel level, ServerPlayer t) {
        Vec3 c = h.centre();
        h.look = t.getEyePosition();
        if (friend == null || !friend.isAlive() || friend.level() != level) {
            m.end();
            return;
        }
        if (m.watched) {
            h.goal = null;
        } else if (m.planTicks % 30 == 0) {
            h.goal = m.stalkSpot(level, t);
        }
        // another noise every few seconds, further out each time
        if (noises < 3 && m.planTicks % (20 * 5) == 0 && m.planTicks > 0) {
            lure(level, t);
        }
        // apart now, and them not looking: it comes in
        boolean apart = friend.distanceToSqr(t) > 12 * 12;
        if (apart && t.position().distanceTo(c) < 12 && m.unseenTicks > 30 && m.planTicks % 10 == 0) {
            m.startStrike(level, t);
            return;
        }
        if (m.planTicks > 20 * 40) {
            m.end();
        }
    }
}
