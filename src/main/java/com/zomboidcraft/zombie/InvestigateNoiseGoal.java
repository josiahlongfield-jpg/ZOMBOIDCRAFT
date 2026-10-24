package com.zomboidcraft.zombie;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Shamble toward the last thing we heard until we arrive, find a target, or lose interest. */
public class InvestigateNoiseGoal extends Goal {
    private static final int GIVE_UP_TICKS = 20 * 30;

    private final Zombie zombie;
    private final NoiseListener ears;
    private Vec3 destination;
    private int ticks;

    public InvestigateNoiseGoal(Zombie zombie) {
        this.zombie = zombie;
        this.ears = (NoiseListener) zombie;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return zombie.getTarget() == null && ears.zomboidcraft$heardAt() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return zombie.getTarget() == null && destination != null && ticks < GIVE_UP_TICKS
                && zombie.position().distanceToSqr(destination) > 4.0;
    }

    @Override
    public void start() {
        destination = ears.zomboidcraft$heardAt();
        ticks = 0;
        zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 latest = ears.zomboidcraft$heardAt();
        if (latest != null && !latest.equals(destination)) {
            destination = latest;
            zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        } else if (ticks % 40 == 0 && zombie.getNavigation().isDone()) {
            zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        }
    }

    @Override
    public void stop() {
        ears.zomboidcraft$forget();
        destination = null;
        zombie.getNavigation().stop();
    }
}
