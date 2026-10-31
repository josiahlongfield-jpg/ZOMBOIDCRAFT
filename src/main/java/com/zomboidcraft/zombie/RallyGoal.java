package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Zomboid's rallies (zombiePopulation rally settings): idle dead drift together into groups and stand as a crowd, the
 * clumps you see down Zomboid's streets. A few of them, rolled from the UUID (about one per group's worth), are where
 * groups form; one wins over any other within Rally Group Separation. An idle zombie within Rally Travel Distance of
 * one whose group isn't full yet walks over and stands within Rally Group Radius of it. Anything heard or seen comes
 * first: this is only what they do with nothing else to do.
 */
public class RallyGoal extends Goal {
    /** How often an idle zombie looks round for a group (ticks). */
    private static final int LOOK_EVERY = 100;
    private static final double SPEED = 0.7;

    private final Zombie zombie;
    private Zombie leader;
    private Vec3 spot;
    private int ticks;

    public RallyGoal(Zombie zombie) {
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private static ZcConfig.ZombiePopulation cfg() {
        return ZcConfig.get().zombiePopulation;
    }

    /** Whether this zombie is one groups form round: about one in Rally Group Size. */
    private static boolean candidate(Zombie z) {
        int size = Math.max(1, cfg().rallyGroupSize);
        return size > 1 && Lore.roll(z, 20) < 1.0 / size;
    }

    /** How many it gathers: Rally Group Size, give or take the variance, rolled for it. */
    private static int groupSize(Zombie z) {
        ZcConfig.ZombiePopulation c = cfg();
        double v = c.rallyGroupSizeVariance / 100 * (Lore.roll(z, 21) * 2 - 1);
        return Math.max(2, (int) Math.round(c.rallyGroupSize * (1 + v)));
    }

    private static boolean idle(Zombie z) {
        return z.isAlive() && z.getTarget() == null && ((NoiseListener) z).zomboidcraft$heardAt() == null && ZombieTypes.isWalker(z);
    }

    /** A candidate that isn't outranked by another within the separation: a group really forms round it. */
    private boolean leads(Zombie c) {
        double sep = cfg().rallyGroupSeparation;
        double mine = Lore.roll(c, 20);
        for (Zombie o : zombie.level().getEntitiesOfClass(Zombie.class, c.getBoundingBox().inflate(sep), RallyGoal::idle)) {
            if (o != c && candidate(o) && Lore.roll(o, 20) < mine && o.distanceToSqr(c) <= sep * sep) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean canUse() {
        if ((zombie.tickCount + zombie.getId()) % LOOK_EVERY != 0 || cfg().rallyGroupSize <= 1 || !idle(zombie)
                || candidate(zombie) && leads(zombie)) {
            return false;
        }
        double travel = cfg().rallyTravelDistance, radius = Math.max(1, cfg().rallyGroupRadius);
        Zombie best = null;
        double bestD = Double.MAX_VALUE;
        for (Zombie c : zombie.level().getEntitiesOfClass(Zombie.class, zombie.getBoundingBox().inflate(travel), RallyGoal::idle)) {
            if (c == zombie || !candidate(c)) {
                continue;
            }
            double d = c.distanceToSqr(zombie);
            if (d > travel * travel || d >= bestD || !leads(c)) {
                continue;
            }
            if (d <= radius * radius) {
                return false;   // already in its group
            }
            List<Zombie> group = zombie.level().getEntitiesOfClass(Zombie.class, c.getBoundingBox().inflate(radius), RallyGoal::idle);
            if (group.size() - 1 >= groupSize(c)) {
                continue;       // full
            }
            best = c;
            bestD = d;
        }
        if (best == null) {
            return false;
        }
        leader = best;
        double a = zombie.getRandom().nextDouble() * Math.PI * 2, r = Math.sqrt(zombie.getRandom().nextDouble()) * radius;
        spot = best.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
        return true;
    }

    @Override
    public void start() {
        ticks = 0;
        zombie.getNavigation().moveTo(spot.x, spot.y, spot.z, SPEED);
    }

    @Override
    public boolean canContinueToUse() {
        return ++ticks < 600 && idle(zombie) && leader.isAlive() && !zombie.getNavigation().isDone();
    }

    @Override
    public void stop() {
        leader = null;
        spot = null;
    }
}
