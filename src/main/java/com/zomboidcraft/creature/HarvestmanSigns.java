package com.zomboidcraft.creature;

import com.zomboidcraft.world.blocks.CityBlock;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a Harvestman gives away about itself, before it's ever seen. It's heard first: a dry chittering from somewhere
 * through the walls, and the boards creaking overhead as it crosses a ceiling or settles above someone. And it leaves
 * signs where it's been: the lights in a building it hunts smashed one by one so the place stays dark, fresh gouges in
 * a doorframe it squeezed through, a drag trail behind whatever it carries, and zombies quietly gone from an area it
 * fed in while nobody was near. It also makes the noises it uses to fool its prey (HarvestmanMind's feint), and leaves
 * a door they shut standing open behind them.
 */
final class HarvestmanSigns {
    /** Names of the blocks it counts as lights it can smash (it only goes for ones that shine). */
    private static final String[] LIGHTS = {"lamp", "lantern", "light", "bulb", "torch", "glowstone", "froglight", "sconce",
            "chandelier", "fixture", "fluoro", "neon"};

    private final HarvestmanEntity h;
    private int ticks, nextLight = 200, nextSnack = 1200, marks, knockIn = -1;
    private boolean wasSlow;
    private Vec3 lastMark, knockAt, lastScore;
    /**
     * Scoring a trail as it goes (HarvestmanMind sets it: on its way home to its den, or to a ceiling it means to wait
     * on): gouges in the walls along its way every few blocks, that lead whoever follows them there.
     */
    boolean trail;

    HarvestmanSigns(HarvestmanEntity h) {
        this.h = h;
    }

    void tick(ServerLevel level) {
        ticks++;
        HarvestmanMind m = h.mind;
        // the second knock of a feint
        if (knockIn > 0 && --knockIn == 0 && knockAt != null) {
            level.playSound(null, knockAt.x, knockAt.y, knockAt.z, HarvestmanSounds.TAP, SoundSource.HOSTILE, 1.1f, 0.7f);
        }
        dragMarks(level);
        if (trail && ticks % 5 == 0) {
            trailMark(level);
        } else if (!trail) {
            lastScore = null;
        }
        boolean slow = h.route.slow;
        if (slow && !wasSlow && h.getRandom().nextFloat() < 0.5f) {
            gouge(level);
        }
        wasSlow = slow;
        if (ticks % 10 == 0) {
            creak(level);
        }
        if (ticks % 20 == 0) {
            chitter(level);
        }
        if (--nextLight <= 0) {
            nextLight = 20;
            if (smashLight(level)) {
                nextLight = 20 * (8 + h.getRandom().nextInt(10));
            }
        }
        if (ticks % 100 == 50) {
            pocket(level);
        }
        if (--nextSnack <= 0) {
            nextSnack = 20 * 60;
            snack(level, m);
        }
    }

    private static boolean roaming(int plan) {
        return plan == HarvestmanMind.STALK || plan == HarvestmanMind.AMBUSH || plan == HarvestmanMind.HIDE
                || plan == HarvestmanMind.SHADOW || plan == HarvestmanMind.SPLIT || plan == HarvestmanMind.HORDE
                || plan == HarvestmanMind.PERCH || plan == HarvestmanMind.CEILING || plan == HarvestmanMind.WANDER
                || plan == HarvestmanMind.INVESTIGATE || plan == HarvestmanMind.LISTEN || plan == HarvestmanMind.IDLE
                || plan == HarvestmanMind.PATROL || plan == HarvestmanMind.FORAGE || plan == HarvestmanMind.OBSERVE
                || plan == HarvestmanMind.LIE_IN_WAIT || plan == HarvestmanMind.SHELTER;
    }

    // ------------------------------------------------------------------------------------------------ heard first

    /**
     * A dry chittering now and then, while it waits or roams with someone in earshot: more often through a wall than in
     * plain sight, never while it's being looked at.
     */
    private void chitter(ServerLevel level) {
        if (!roaming(h.mind.plan) || h.mind.watched()) {
            return;
        }
        Player p = level.getNearestPlayer(h, 40);
        if (p == null || p.isSpectator() || p.distanceToSqr(h) < 12 * 12) {
            return;
        }
        boolean through = !h.canSee(p.getEyePosition());
        if (h.getRandom().nextFloat() < (through ? 0.12f : 0.05f)) {
            Vec3 c = h.centre();
            level.playSound(null, c.x, c.y, c.z, HarvestmanSounds.CHITTER, SoundSource.HOSTILE, 0.9f,
                    0.85f + h.getRandom().nextFloat() * 0.25f);
        }
    }

    /** The boards over someone's head creaking: as it crosses a ceiling, or when it settles on a floor above them. */
    private void creak(ServerLevel level) {
        if (!h.isAlive()) {
            return;
        }
        Vec3 c = h.centre(), up = h.up();
        boolean ceiling = up.y < -0.5;
        boolean over = false;
        for (Player p : level.players()) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (!p.isSpectator() && dx * dx + dz * dz < 9 * 9 && c.y - h.lift() > p.getEyeY() + 0.5 && c.y - p.getY() < 14) {
                over = true;
                break;
            }
        }
        if (!ceiling && !over) {
            return;
        }
        boolean moving = h.legs.speed() > 0.2;
        if (h.getRandom().nextFloat() < (moving ? 0.14f : 0.025f)) {
            Vec3 at = c.subtract(up.scale(h.lift()));
            level.playSound(null, at.x, at.y, at.z, HarvestmanSounds.CREAK, SoundSource.HOSTILE, moving ? 0.8f : 0.55f,
                    0.8f + h.getRandom().nextFloat() * 0.35f);
        }
    }

    // ------------------------------------------------------------------------------------------------ signs left

    /**
     * Smashes a light near it in a building where someone is (within 48), so the place stays dark: one at a time, and
     * only lights it can reach and see, indoors.
     */
    private boolean smashLight(ServerLevel level) {
        if (!roaming(h.mind.plan) || h.held() != null) {
            return false;
        }
        Player p = level.getNearestPlayer(h, 48);
        if (p == null || p.isSpectator()) {
            return false;
        }
        Vec3 c = h.centre();
        BlockPos at = BlockPos.containing(c);
        if (level.canSeeSky(at)) {
            return false;
        }
        BlockPos best = null;
        double bd = 1e9;
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-4, -3, -4), at.offset(4, 3, 4))) {
            BlockState s = level.getBlockState(q);
            if (s.getLightEmission() < 8 || !s.getFluidState().isEmpty() || s.getDestroySpeed(level, q) < 0 || !isLight(s)
                    || level.canSeeSky(q.above())) {
                continue;
            }
            double d = Vec3.atCenterOf(q).distanceToSqr(c);
            if (d < bd && d < 4.5 * 4.5) {
                var hit = level.clip(new ClipContext(c, Vec3.atCenterOf(q), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, h));
                if (hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(q)) {
                    bd = d;
                    best = q.immutable();
                }
            }
        }
        if (best == null) {
            return false;
        }
        Vec3 l = Vec3.atCenterOf(best);
        level.playSound(null, l.x, l.y, l.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1f, 0.8f + h.getRandom().nextFloat() * 0.2f);
        level.destroyBlock(best, false, h);
        h.look = l;
        return true;
    }

    private static boolean isLight(BlockState s) {
        String id = BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
        if (id.contains("redstone") || id.contains("campfire") || id.contains("furnace") || id.contains("smoker")) {
            return false;
        }
        for (String n : LIGHTS) {
            if (id.contains(n)) {
                return true;
            }
        }
        return false;
    }

    /** Scores in a doorframe it squeezes through: a plate of gouges on the wall beside its way. */
    /** Scores its mark on a wall where it stands (the edge of its ground: HarvestmanGoals). */
    void mark(ServerLevel level) {
        gouge(level);
    }

    private void gouge(ServerLevel level) {
        if (h.up().y < 0.5) {
            return;
        }
        Vec3 c = h.centre();
        BlockPos floor = BlockPos.containing(c.x, c.y - h.lift() + 0.3, c.z);
        for (int k = 0; k < 10; k++) {
            BlockPos q = floor.offset(h.getRandom().nextInt(5) - 2, h.getRandom().nextInt(3), h.getRandom().nextInt(5) - 2);
            if (!level.isEmptyBlock(q)) {
                continue;
            }
            Direction wall = wallBeside(level, q);
            BlockState s = CityBlocks.resolve("hd_gouges_" + h.getRandom().nextInt(3));
            if (wall != null && s != null) {
                level.setBlock(q, s.setValue(CityBlock.Facing.FACING, wall.getOpposite()), 3);
                level.playSound(null, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, HarvestmanSounds.SCRAPE, SoundSource.HOSTILE, 0.3f, 1.4f);
                return;
            }
        }
    }

    /** One mark of a trail: gouges in a wall beside it, every five or six blocks of its way. */
    private void trailMark(ServerLevel level) {
        if (h.up().y < 0.5 || h.held() != null) {
            return;
        }
        Vec3 c = h.centre();
        Vec3 here = new Vec3(c.x, c.y - h.lift(), c.z);
        if (lastScore != null && lastScore.distanceToSqr(here) < 5.5 * 5.5) {
            return;
        }
        BlockPos floor = BlockPos.containing(here.x, here.y + 0.3, here.z);
        for (int k = 0; k < 14; k++) {
            BlockPos q = floor.offset(h.getRandom().nextInt(5) - 2, h.getRandom().nextInt(2), h.getRandom().nextInt(5) - 2);
            if (!level.isEmptyBlock(q)) {
                continue;
            }
            Direction wall = wallBeside(level, q);
            BlockState s = CityBlocks.resolve("hd_gouges_" + h.getRandom().nextInt(3));
            if (wall != null && s != null) {
                level.setBlock(q, s.setValue(CityBlock.Facing.FACING, wall.getOpposite()), 3);
                lastScore = here;
                return;
            }
        }
    }

    private static Direction wallBeside(ServerLevel level, BlockPos c) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos w = c.relative(d);
            BlockState s = level.getBlockState(w);
            if (s.isCollisionShapeFullBlock(level, w) && !(s.getBlock() instanceof DoorBlock)) {
                return d;
            }
        }
        return null;
    }

    /** The trail behind what it drags or carries along a floor. */
    private void dragMarks(ServerLevel level) {
        if (h.held() == null && !h.mind.carrying()) {
            lastMark = null;
            marks = 0;
            return;
        }
        if (h.up().y < 0.5 || marks > 60) {
            return;
        }
        Vec3 c = h.centre();
        Vec3 here = new Vec3(c.x, c.y - h.lift(), c.z);
        if (lastMark != null && lastMark.distanceToSqr(here) < 1.4 * 1.4) {
            return;
        }
        Vec3 way = lastMark == null ? h.forward() : here.subtract(lastMark);
        lastMark = here;
        mark(level, BlockPos.containing(here.x, here.y + 0.1, here.z), way);
    }

    private boolean mark(ServerLevel level, BlockPos cell, Vec3 way) {
        BlockState s = CityBlocks.resolve("hd_drag");
        if (s == null || !level.isEmptyBlock(cell) || !level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(), Direction.UP)) {
            return false;
        }
        Direction.Axis axis = Math.abs(way.x) > Math.abs(way.z) ? Direction.Axis.X : Direction.Axis.Z;
        level.setBlock(cell, s.setValue(CityBlock.Axis.AXIS, axis), 3);
        marks++;
        return true;
    }

    /**
     * With nobody near (none within 40), now and then it takes a zombie from round about to feed on: the zombie is
     * simply gone, and all that's left is a short drag trail where it was taken.
     */
    private void snack(ServerLevel level, HarvestmanMind m) {
        int plan = m.plan;
        if (!roaming(plan) || h.held() != null || level.getNearestPlayer(h, 40) != null || m.hunger < 0.3
                || h.getRandom().nextFloat() > 0.4f) {
            return;
        }
        Zombie z = null;
        for (Zombie e : level.getEntitiesOfClass(Zombie.class, h.getBoundingBox().inflate(24), w -> ZombieTypes.isWalker(w) && w.isAlive())) {
            z = e;
            break;
        }
        if (z == null) {
            return;
        }
        Vec3 from = z.position(), to = h.position().subtract(from).multiply(1, 0, 1);
        to = to.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : to.normalize();
        for (int k = 0; k < 4; k++) {
            mark(level, BlockPos.containing(from.add(to.scale(k * 1.2)).add(0, 0.1, 0)), to);
        }
        z.discard();
        m.hunger = Math.max(0, m.hunger - 0.06);
    }

    /**
     * Something left lying about near the one it hunts (dropped, or put down and forgotten), picked up while nobody's
     * looking: it'll turn up on their doorstep some night (HarvestmanNights).
     */
    private void pocket(ServerLevel level) {
        HarvestmanMind m = h.mind;
        if (h.held() != null || m.watched() || !(m.quarry instanceof ServerPlayer t) || t != m.target(level)
                || t.distanceToSqr(h) > 40 * 40 || h.getRandom().nextFloat() > 0.5f) {
            return;
        }
        Vec3 c = h.centre();
        Vec3 under = c.subtract(h.up().scale(h.lift()));
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(under, under).inflate(3.5))) {
            if (!e.isAlive() || HarvestmanNights.leftIt(e) || e.getItem().isEmpty()) {
                continue;
            }
            boolean seen = false;
            for (Player p : level.players()) {
                if (!p.isSpectator() && HarvestmanMind.lookingAt(p, e.position().add(0, 0.2, 0))) {
                    seen = true;
                    break;
                }
            }
            if (!seen && HarvestmanNights.pocket(t, e.getItem())) {
                e.discard();
                level.playSound(null, e.getX(), e.getY(), e.getZ(), HarvestmanSounds.TAP, SoundSource.HOSTILE, 0.3f, 1.3f);
                return;
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ its tricks

    /** A noise made somewhere to turn its prey that way: a knock, the clatter of what's there, a second knock after. */
    void decoy(ServerLevel level, Vec3 at) {
        BlockState s = level.getBlockState(BlockPos.containing(at));
        SoundType type = s.isAir() ? SoundType.WOOD : s.getSoundType();
        level.playSound(null, at.x, at.y, at.z, type.getHitSound(), SoundSource.BLOCKS, 1.4f, type.getPitch() * 0.7f);
        level.playSound(null, at.x, at.y, at.z, HarvestmanSounds.TAP, SoundSource.HOSTILE, 1.2f, 0.75f);
        if (h.getRandom().nextBoolean()) {
            level.playSound(null, at.x, at.y, at.z, type.getBreakSound(), SoundSource.BLOCKS, 0.9f, type.getPitch() * 0.8f);
        }
        knockAt = at;
        knockIn = 6 + h.getRandom().nextInt(6);
    }

    static void decoyNoise(ServerLevel level, HarvestmanEntity h, Vec3 at) {
        h.signs.decoy(level, at);
    }

    /**
     * Opens a door they shut somewhere behind them, out of their sight: when they turn back, it's standing open. True
     * if it found one to open.
     */
    static boolean openDoorBehind(ServerLevel level, HarvestmanEntity h, ServerPlayer t) {
        BlockPos at = t.blockPosition();
        Vec3 eye = t.getEyePosition(), look = t.getLookAngle();
        for (BlockPos q : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 3, 12))) {
            BlockState s = level.getBlockState(q);
            if (!(s.getBlock() instanceof DoorBlock door) || s.getValue(DoorBlock.OPEN)
                    || s.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || !door.type().canOpenByHand()) {
                continue;
            }
            Vec3 d = Vec3.atCenterOf(q.above()).subtract(eye);
            double dist = d.length();
            if (dist < 5 || look.dot(d.scale(1 / dist)) > -0.1) {
                continue;   // too close, or where they'd see it go
            }
            door.setOpen(h, level, s, q.immutable(), true);
            return true;
        }
        return false;
    }
}
