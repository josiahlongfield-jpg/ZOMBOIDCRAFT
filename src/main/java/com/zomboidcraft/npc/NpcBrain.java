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
import net.minecraft.world.item.ItemStack;
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
    /** Closer than this she sprints; further she jogs. How near she runs to her player from, and how far behind him she stands. */
    private static final double SPRINT_FROM = 4.0, REFUGE = 24.0, BEHIND = 1.8;
    /** No zombie within this, and she'll hold still while you talk to her. */
    private static final double ATTEND_SAFE = 5.0;
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

    /** Holds still for whoever is talking to her or has her page open, facing them, while no zombie is close. */
    static final class Attend extends Goal {
        private final NpcEntity npc;
        private Player by;

        Attend(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            by = npc.work.asleep ? null : npc.attendant();
            return by != null && nearestZombie(npc, ATTEND_SAFE, false) == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            npc.getNavigation().stop();
        }

        @Override
        public void tick() {
            npc.getLookControl().setLookAt(by, 30f, 30f);
        }

        @Override
        public void stop() {
            by = null;
        }
    }

    /**
     * Gets away from a zombie that's too close. Armed, she stands and fights (Shoot) and only steps back from one in arm's
     * reach. Unarmed, she makes for the spot just behind the player she follows, on his side away from the zombie, and
     * stays there; on her own she picks one way out (under a roof or toward home if she can) and keeps to it rather than
     * darting about. She jogs, and sprints only with a zombie right on her. In a panic, once she's as far as she can get,
     * she crouches with her arms over her head for a few seconds (calming her down ends it).
     */
    static final class Flee extends Goal {
        private final NpcEntity npc;
        private Zombie from;
        private Vec3 to;
        private boolean behind, armed;
        private int repick, follow;

        Flee(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (npc.work.asleep) {
                return false;
            }
            armed = NpcCare.armed(npc);
            from = nearestZombie(npc, armed ? FLEE_ARMED : FLEE_UNARMED * npc.mind.flightScale(), false);
            if (from == null) {
                return false;
            }
            double d = from.distanceToSqr(npc);
            if (npc.attendant() != null && d > ATTEND_SAFE * ATTEND_SAFE) {
                return false;
            }
            if (armed) {
                to = DefaultRandomPos.getPosAway(npc, 5, 3, from.position());
                behind = false;
                return to != null && npc.getNavigation().moveTo(to.x, to.y, to.z, 1.0);
            }
            if (!refuge() && !escape()) {
                // nowhere better to go: if she's frightened enough, she freezes where she is
                if (npc.mind.panicking() && d > SPRINT_FROM * SPRINT_FROM) {
                    cower();
                    return true;
                }
                return false;
            }
            npc.mind.jolt(12f);
            npc.say(npc.mind.panicking() ? NpcLines.Kind.PANIC : NpcLines.Kind.FLEE);
            return true;
        }

        /** The player she follows, if he's near enough to run to. */
        private Player leader() {
            Player p = npc.following ? npc.leader() : null;
            return p != null && p.distanceToSqr(npc) < REFUGE * REFUGE ? p : null;
        }

        /** Just behind her player, on his side away from the zombie. */
        private boolean refuge() {
            Player p = leader();
            if (p == null) {
                return false;
            }
            Vec3 away = p.position().subtract(from.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1e-4) {
                away = p.position().subtract(npc.position()).multiply(-1, 0, -1);
            }
            if (away.lengthSqr() < 1e-4) {
                return false;
            }
            Vec3 spot = p.position().add(away.normalize().scale(BEHIND));
            if (!npc.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0)) {
                return false;
            }
            to = spot;
            behind = true;
            return true;
        }

        /** One way out: the best of a few spots away from the zombie, a roof over it or home close scoring higher. */
        private boolean escape() {
            Vec3 best = null;
            double bestScore = -Double.MAX_VALUE, now = from.distanceToSqr(npc);
            for (int i = 0; i < 6; i++) {
                Vec3 c = DefaultRandomPos.getPosAway(npc, 14, 6, from.position());
                if (c == null || c.distanceToSqr(from.position()) <= now) {
                    continue;
                }
                double score = Math.sqrt(c.distanceToSqr(from.position()));
                if (!npc.level().canSeeSky(net.minecraft.core.BlockPos.containing(c).above())) {
                    score += 6;
                }
                if (npc.work.home != null) {
                    score -= 0.3 * Math.sqrt(c.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(npc.work.home)));
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = c;
                }
            }
            if (best == null || !npc.getNavigation().moveTo(best.x, best.y, best.z, 1.0)) {
                return false;
            }
            to = best;
            behind = false;
            repick = 40;
            return true;
        }

        private void cower() {
            npc.getNavigation().stop();
            npc.cowerTicks = 60 + npc.getRandom().nextInt(41);
            npc.say(NpcLines.Kind.PANIC);
        }

        @Override
        public boolean canContinueToUse() {
            if (from == null || !from.isAlive() || npc.work.asleep) {
                return false;
            }
            double d = from.distanceToSqr(npc);
            if (armed) {
                return !npc.getNavigation().isDone() && d < 6 * 6;
            }
            return npc.cowerTicks > 0 || d < (FLEE_UNARMED + 5) * (FLEE_UNARMED + 5) * npc.mind.flightScale() * npc.mind.flightScale();
        }

        @Override
        public void tick() {
            double d = from.distanceToSqr(npc);
            if (npc.cowerTicks > 0) {
                npc.cowerTicks--;
                npc.getNavigation().stop();
                if (d < SPRINT_FROM * SPRINT_FROM * 0.5) {
                    // it's on her: she bolts after all
                    npc.cowerTicks = 0;
                    if (!refuge()) escape();
                }
                return;
            }
            // a jog, unless it's right on her
            npc.getNavigation().setSpeedModifier((d < SPRINT_FROM * SPRINT_FROM ? 1.6 : 1.0) * npc.mind.pace());
            if (armed) {
                return;
            }
            repick--;
            if (behind) {
                // stay behind him as he moves
                if (++follow % 10 == 0 && !refuge() && !escape()) {
                    behind = false;
                }
                if (npc.getNavigation().isDone()) {
                    npc.getLookControl().setLookAt(from, 30f, 30f);
                }
                return;
            }
            if (leader() != null && follow++ % 20 == 0 && refuge()) {
                return;
            }
            if (npc.getNavigation().isDone()) {
                npc.getLookControl().setLookAt(from, 30f, 30f);
                if (d < SPRINT_FROM * SPRINT_FROM && repick <= 0) {
                    if (!escape() && npc.mind.panicking()) cower();
                } else if (npc.mind.panicking() && d > SPRINT_FROM * SPRINT_FROM) {
                    cower();
                }
            }
        }

        @Override
        public void stop() {
            from = null;
            to = null;
            behind = false;
            npc.cowerTicks = 0;
        }
    }

    /** Stands, raises her gun and shoots the nearest zombie she can see; reloads when the magazine's out. */
    static final class Shoot extends Goal {
        private final NpcEntity npc;
        private Zombie target;
        private int raised, cooldown;
        private ItemStack lastGun;

        Shoot(NpcEntity npc) {
            this.npc = npc;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // asleep she sees nothing; a zombie close by or her own alarm wakes her first
            if (!NpcCare.armed(npc) || npc.work.asleep) {
                return false;
            }
            target = nearestZombie(npc, SHOOT_RANGE, true);
            return target != null;
        }

        @Override
        public boolean canContinueToUse() {
            return NpcCare.armed(npc) && target != null && target.isAlive() && target.distanceToSqr(npc) < SHOOT_RANGE * SHOOT_RANGE
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
            boolean endless = ZcConfig.get().companions.endlessAmmo;
            ItemStack held = npc.getMainHandItem();
            if (held != lastGun) {
                // a different gun in her hands (a swap, or one you handed her): count what's in this one
                lastGun = held;
                npc.rounds = -1;
                npc.reloadLeft = 0;
            }
            if (npc.rounds < 0) {
                // endless, a full magazine; else what's really in the gun
                npc.rounds = endless ? stats.magazineSize : NpcCare.loaded(held);
            }
            if (npc.reloadLeft > 0) {
                if (--npc.reloadLeft == 0) {
                    npc.rounds = endless ? stats.magazineSize : npc.rounds + NpcCare.draw(npc, type, type.capacity() - npc.rounds);
                    if (!endless) NpcCare.setLoaded(held, npc.rounds);
                }
                return;
            }
            if (npc.rounds <= 0) {
                if (endless || NpcCare.reserve(npc, type) > 0) {
                    npc.reloadLeft = RELOAD;
                    npc.say(NpcLines.Kind.RELOAD);
                }
                return;
            }
            if (--cooldown > 0 || raised < RAISE) {
                return;
            }
            fire(type, stats);
            // a person's pace, not the gun's top rate: short bursts from automatics, aimed shots otherwise
            cooldown = Math.max(type.fireDelay(), 4) + npc.getRandom().nextInt(6);
            npc.rounds--;
            if (!endless) NpcCare.setLoaded(held, npc.rounds);
            if (npc.rounds <= 0) {
                if (endless || NpcCare.reserve(npc, type) > 0) {
                    npc.reloadLeft = RELOAD;
                    npc.say(NpcLines.Kind.RELOAD);
                } else {
                    npc.lines.sayLine(npc, NpcLines.Kind.RELOAD, NpcWork.pick(npc, "I'm out! I've got nothing left!",
                            "Empty! I need rounds!", "That's my last one!"));
                }
            }
        }

        private void fire(GunType type, ZcConfig.GunStats stats) {
            ServerLevel level = (ServerLevel) npc.level();
            Vec3 eye = npc.getEyePosition();
            boolean head = npc.getRandom().nextFloat() < 0.12f + 0.025f * npc.skills.level(NpcSkills.Skill.AIM);
            Vec3 aimAt = head ? target.getEyePosition() : target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 dir = aimAt.subtract(eye).normalize();
            // about two degrees of wobble when she's steady; pain, tiredness and fear shake it (NpcMind.wobble)
            double wobble = 0.02 * npc.mind.wobble() * (1.25 - 0.06 * npc.skills.level(NpcSkills.Skill.AIM));
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
            npc.work.practise(npc, NpcSkills.Skill.AIM, 1f);
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
                    npc.work.practise(npc, NpcSkills.Skill.AIM, 3f);
                }
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
            if (hunger < FORAGE_FROM && thirst < FORAGE_FROM || npc.work.asleep || npc.getRandom().nextInt(10) != 0) {
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
            if (leader == null) return false;
            // hand in hand she keeps right at your side
            return leader.distanceToSqr(npc) > (npc.bond.holding ? 1.3 * 1.3 : 5 * 5);
        }

        @Override
        public boolean canContinueToUse() {
            return npc.following && leader != null && leader.isAlive() && leader.distanceToSqr(npc) > (npc.bond.holding ? 0.9 * 0.9 : 2.5 * 2.5);
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
            if (npc.bond.holding) {
                if (--repath <= 0) {
                    repath = 4;
                    // a step to your right, matching your pace
                    double yaw = Math.toRadians(leader.getYRot());
                    double x = leader.getX() - Math.cos(yaw) * 0.7, z = leader.getZ() - Math.sin(yaw) * 0.7;
                    npc.getNavigation().moveTo(x, leader.getY(), z, leader.isSprinting() ? 1.6 : 1.1);
                }
                return;
            }
            if (--repath <= 0) {
                repath = 10;
                npc.getNavigation().moveTo(leader, (leader.distanceToSqr(npc) > 10 * 10 ? 1.6 : 1.0) * npc.mind.pace());
            }
        }
    }

    private NpcBrain() {
    }
}
