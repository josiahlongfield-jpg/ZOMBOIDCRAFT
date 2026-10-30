package com.zomboidcraft.npc;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.GunType;
import com.zomboidcraft.survival.Drinks;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.zombie.Noise;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A companion NPC's goals (NpcEntity.registerGoals), most urgent first. */
final class NpcBrain {
    /** How far she'll pick a zombie to shoot, and spot a gun lying about. */
    private static final double SHOOT_RANGE = 22, GUN_RANGE = 12;
    /** A zombie this close sends her running: closer when she's armed. */
    private static final double FLEE_ARMED = 3.0, FLEE_UNARMED = 9.0;
    /** Ticks to bring the gun up before the first shot, and to reload a magazine. */
    private static final int RAISE = 8, RELOAD = 50;

    static GunType gun(NpcEntity npc) {
        return GunType.of(npc.getMainHandItem());
    }

    /** The nearest living zombie within range, optionally only one she can see. */
    static Zombie nearestZombie(NpcEntity npc, double range, boolean seen) {
        List<Zombie> near = npc.level().getEntitiesOfClass(Zombie.class, npc.getBoundingBox().inflate(range),
                z -> z.isAlive() && z.distanceToSqr(npc) < range * range);
        Zombie best = null;
        double bestD = Double.MAX_VALUE;
        for (Zombie z : near) {
            double d = z.distanceToSqr(npc);
            if (d < bestD && (!seen || npc.getSensing().hasLineOfSight(z))) {
                best = z;
                bestD = d;
            }
        }
        return best;
    }

    /** Runs from a zombie that's too close. */
    static final class Flee extends Goal {
        private final NpcEntity npc;
        private Zombie from;

        Flee(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            // fear pushes the distance out; in a panic she runs even with a gun in her hands
            double range = (gun(npc) != null && !npc.mind.panicking() ? FLEE_ARMED : FLEE_UNARMED) * npc.mind.flightScale();
            from = nearestZombie(npc, range, false);
            if (from == null) {
                return false;
            }
            Vec3 away = DefaultRandomPos.getPosAway(npc, 14, 6, from.position());
            if (away == null || away.distanceToSqr(from.position()) < from.distanceToSqr(npc)) {
                return false;
            }
            npc.getNavigation().moveTo(away.x, away.y, away.z, 1.7 * npc.mind.pace());
            npc.mind.jolt(12f);
            npc.say(npc.mind.panicking() ? NpcLines.Kind.PANIC : NpcLines.Kind.FLEE);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !npc.getNavigation().isDone() && from.isAlive() && from.distanceToSqr(npc) < 14 * 14;
        }

        @Override
        public void stop() {
            from = null;
        }
    }

    /** Stands, raises her gun and shoots the nearest zombie she can see; reloads when the magazine's out. */
    static final class Shoot extends Goal {
        private final NpcEntity npc;
        private Zombie target;
        private int raised, cooldown;

        Shoot(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (gun(npc) == null) {
                return false;
            }
            target = nearestZombie(npc, SHOOT_RANGE, true);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return gun(npc) != null && target != null && target.isAlive() && target.distanceToSqr(npc) < SHOOT_RANGE * SHOOT_RANGE
                    && npc.getSensing().hasLineOfSight(target);
        }

        @Override
        public void start() {
            npc.getNavigation().stop();
            npc.setAiming(true);
            npc.say(NpcLines.Kind.SPOTTED);
            raised = 0;
        }

        @Override
        public void stop() {
            npc.setAiming(false);
            if (target != null && !target.isAlive() && nearestZombie(npc, SHOOT_RANGE, true) == null) {
                npc.say(NpcLines.Kind.CLEAR);
            }
            target = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            GunType type = gun(npc);
            if (type == null || target == null) {
                return;
            }
            npc.getLookControl().setLookAt(target, 60f, 60f);
            npc.setYBodyRot(npc.getYHeadRot());
            raised++;
            ZcConfig.GunStats stats = type.stats();
            if (npc.rounds < 0) {
                npc.rounds = stats.magazineSize;
            }
            if (npc.reloadLeft > 0) {
                if (--npc.reloadLeft == 0) {
                    npc.rounds = stats.magazineSize;
                }
                return;
            }
            if (--cooldown > 0 || raised < RAISE) {
                return;
            }
            fire(type, stats);
            // a person's pace, not the gun's top rate: short bursts from automatics, aimed shots otherwise
            cooldown = Math.max(type.fireDelay(), 4) + npc.getRandom().nextInt(6);
            if (--npc.rounds <= 0) {
                npc.reloadLeft = RELOAD;
                npc.say(NpcLines.Kind.RELOAD);
            }
        }

        private void fire(GunType type, ZcConfig.GunStats stats) {
            ServerLevel level = (ServerLevel) npc.level();
            Vec3 eye = npc.getEyePosition();
            boolean head = npc.getRandom().nextFloat() < 0.2f;
            Vec3 aimAt = head ? target.getEyePosition() : target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 dir = aimAt.subtract(eye).normalize();
            // about two degrees of wobble when she's steady; pain, tiredness and fear shake it (NpcMind.wobble)
            double wobble = 0.02 * npc.mind.wobble();
            dir = dir.add(npc.getRandom().nextGaussian() * wobble, npc.getRandom().nextGaussian() * wobble, npc.getRandom().nextGaussian() * wobble).normalize();
            Vec3 muzzle = eye.add(dir.scale(0.8)).add(0, -0.15, 0);
            // never through a friend
            for (Player p : level.players()) {
                if (!p.isSpectator() && p.getBoundingBox().inflate(0.4).clip(eye, aimAt).isPresent()) {
                    return;
                }
            }
            npc.shot();
            npc.mind.add(NpcMind.Need.ADRENALINE, 3f);
            level.playSound(null, muzzle.x, muzzle.y, muzzle.z, type.shot(false, false), SoundSource.NEUTRAL, 1.4f,
                    0.95f + npc.getRandom().nextFloat() * 0.1f);
            level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.03, 0.03, 0.03, 0.01);
            level.sendParticles(ParticleTypes.SMALL_FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0, 0, 0, 0);
            Noise.emit(level, npc.position(), stats.shotNoise);
            Vec3 end = eye.add(dir.scale(stats.range));
            var block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, npc));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            AABB box = target.getBoundingBox().inflate(0.1);
            var through = box.clip(eye, stop);
            if (through.isPresent()) {
                boolean headHit = through.get().y > target.getEyeY() - 0.25;
                float damage = (float) (stats.damage * (headHit ? stats.headshotMultiplier : 1));
                target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.BULLET, npc), damage);
                if (!target.isAlive()) {
                    npc.mind.killed(npc);
                }
            }
        }
    }

    /** Walks over to a gun lying on the ground and takes it, when her hands are empty. */
    static final class TakeGun extends Goal {
        private final NpcEntity npc;
        private ItemEntity item;

        TakeGun(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!npc.getMainHandItem().isEmpty()) {
                return false;
            }
            item = null;
            double best = Double.MAX_VALUE;
            for (ItemEntity e : npc.level().getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(GUN_RANGE),
                    e -> e.isAlive() && GunType.of(e.getItem()) != null)) {
                double d = e.distanceToSqr(npc);
                if (d < best) {
                    best = d;
                    item = e;
                }
            }
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive() && npc.getMainHandItem().isEmpty();
        }

        @Override
        public void start() {
            npc.getNavigation().moveTo(item, 1.2);
        }

        @Override
        public void stop() {
            item = null;
        }

        @Override
        public void tick() {
            if (item == null) {
                return;
            }
            if (item.distanceToSqr(npc) < 1.8 * 1.8) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, item.getItem().copy());
                npc.say(NpcLines.Kind.GUN);
                npc.rounds = -1;
                npc.reloadLeft = 0;
                item.discard();
                item = null;
            } else if (npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(item, 1.2);
            }
        }
    }

    /**
     * Goes for food or a drink she sees lying about when she needs it (hungry or thirsty past FORAGE_FROM), and has it
     * there and then. Drop her something and she'll pick it up.
     */
    static final class Forage extends Goal {
        private static final float FORAGE_FROM = 35f;
        private final NpcEntity npc;
        private ItemEntity item;

        Forage(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        /** What a stack does for her hunger and thirst (0, 0 if it's not food or drink). */
        static float[] value(net.minecraft.world.item.ItemStack stack) {
            var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
            return new float[] {food == null ? 0f : food.nutrition() * 6f, Drinks.thirstValue(stack)};
        }

        @Override
        public boolean canUse() {
            float hunger = npc.mind.get(NpcMind.Need.HUNGER), thirst = npc.mind.get(NpcMind.Need.THIRST);
            if (hunger < FORAGE_FROM && thirst < FORAGE_FROM || npc.getRandom().nextInt(10) != 0) {
                return false;
            }
            item = null;
            double best = Double.MAX_VALUE;
            for (ItemEntity e : npc.level().getEntitiesOfClass(ItemEntity.class, npc.getBoundingBox().inflate(GUN_RANGE), ItemEntity::isAlive)) {
                float[] v = value(e.getItem());
                boolean wanted = hunger >= FORAGE_FROM && v[0] > 0 || thirst >= FORAGE_FROM && v[1] > 0;
                double d = e.distanceToSqr(npc);
                if (wanted && d < best) {
                    best = d;
                    item = e;
                }
            }
            return item != null;
        }

        @Override
        public boolean canContinueToUse() {
            return item != null && item.isAlive();
        }

        @Override
        public void start() {
            npc.getNavigation().moveTo(item, 1.1 * npc.mind.pace());
        }

        @Override
        public void stop() {
            item = null;
        }

        @Override
        public void tick() {
            if (item == null) {
                return;
            }
            if (item.distanceToSqr(npc) < 1.8 * 1.8) {
                net.minecraft.world.item.ItemStack stack = item.getItem();
                float[] v = value(stack);
                npc.mind.ate(npc, stack.getHoverName().getString(), v[0], v[1]);
                npc.playSound(v[0] > 0 ? net.minecraft.sounds.SoundEvents.GENERIC_EAT.value() : net.minecraft.sounds.SoundEvents.GENERIC_DRINK.value(),
                        0.8f, 0.9f + npc.getRandom().nextFloat() * 0.2f);
                npc.say(NpcLines.Kind.EAT);
                stack.shrink(1);
                if (stack.isEmpty()) {
                    item.discard();
                } else {
                    item.setItem(stack);
                }
                item = null;
            } else if (npc.getNavigation().isDone()) {
                npc.getNavigation().moveTo(item, 1.1 * npc.mind.pace());
            }
        }
    }

    /** Keeps up with her leader: walks when they're a little way off, runs when they're far. */
    static final class Follow extends Goal {
        private final NpcEntity npc;
        private Player leader;
        private int repath;

        Follow(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!npc.following) {
                return false;
            }
            leader = npc.leader();
            return leader != null && leader.distanceToSqr(npc) > 5 * 5;
        }

        @Override
        public boolean canContinueToUse() {
            return npc.following && leader != null && leader.isAlive() && leader.distanceToSqr(npc) > 2.5 * 2.5;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            leader = null;
            npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            npc.getLookControl().setLookAt(leader, 10f, npc.getMaxHeadXRot());
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(leader, (leader.distanceToSqr(npc) > 10 * 10 ? 1.6 : 1.0) * npc.mind.pace());
            }
        }
    }

    private NpcBrain() {
    }
}
