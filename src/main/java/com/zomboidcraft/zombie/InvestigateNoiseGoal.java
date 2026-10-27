package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/** Shamble toward the last thing we heard until we arrive, find a target, or lose interest. */
public class InvestigateNoiseGoal extends Goal {
    private final Zombie zombie;
    private final NoiseListener ears;
    /** A new noise this close to where it's already heading doesn't need a new path; nor one within a second of the last. */
    private static final double MOVED = 3;
    private static final int REPATH_TICKS = 20;

    private Vec3 destination;
    private int ticks, pathedAt;

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
        return zombie.getTarget() == null && destination != null && ticks < giveUpTicks()
                && zombie.position().distanceToSqr(destination) > 4.0;
    }

    @Override
    public void start() {
        destination = ears.zomboidcraft$heardAt();
        ticks = 0;
        pathedAt = 0;
        zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 latest = ears.zomboidcraft$heardAt();
        if (latest != null && !latest.equals(destination) && ticks - pathedAt >= REPATH_TICKS
                && (latest.distanceToSqr(destination) > MOVED * MOVED || zombie.getNavigation().isDone())) {
            // a fresh path only when the noise is somewhere new: footsteps and banging come twice a second, and a
            // crowd planning a path to each one is what slows a big horde down
            destination = latest;
            pathedAt = ticks;
            zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        } else if (ticks % 40 == 0 && zombie.getNavigation().isDone()) {
            zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        }
    }

    private static int giveUpTicks() {
        return ZcConfig.get().senses.searchSeconds * 20;
    }

    @Override
    public void stop() {
        // Only lose interest after arriving or giving up, not when another goal (like bashing) takes over.
        if (ticks >= giveUpTicks() || destination == null || zombie.position().distanceToSqr(destination) <= 4.0) {
            ears.zomboidcraft$forget();
        }
        destination = null;
        zombie.getNavigation().stop();
    }
}
