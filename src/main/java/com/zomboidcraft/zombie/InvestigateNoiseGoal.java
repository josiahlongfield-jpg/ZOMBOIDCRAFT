package com.zomboidcraft.zombie;

import java.util.EnumSet;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;

/**
 * Shamble toward the last thing we heard (or where we lost you), then search round it until we find a target or lose
 * interest. How long, and how far out the search goes, is the zombie's own memory (zombieLore memory, {@link Lore}):
 * a long memory spirals out 16 blocks for a minute and a half, a short one stands at the spot for ten seconds.
 */
public class InvestigateNoiseGoal extends Goal {
    private final Zombie zombie;
    private final NoiseListener ears;
    /** A new noise this close to where it's already heading doesn't need a new path; nor one within a second of the last. */
    private static final double MOVED = 3;
    private static final int REPATH_TICKS = 20;
    /** Each turn of the search spiral goes this much further out, in this many steps round. */
    private static final double SPIRAL_STEP = 2.5;
    private static final int SPIRAL_POINTS = 6;

    private Vec3 destination, spot;
    /** Ticks since it set off, when it got to the spot (-1 not yet), when it last pathed, search points walked. */
    private int ticks, arrivedAt, pathedAt, searched;

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
        if (zombie.getTarget() != null || destination == null || gaveUp()) {
            return false;
        }
        if (zombie.position().distanceToSqr(destination) > 4.0) {
            return true;
        }
        if (arrivedAt < 0) {
            arrivedAt = ticks;
        }
        // arrived: search on round the spot while memory lasts
        return !gaveUp() && nextSearchPoint();
    }

    @Override
    public void start() {
        destination = ears.zomboidcraft$heardAt();
        spot = destination;
        ticks = 0;
        arrivedAt = -1;
        pathedAt = 0;
        searched = 0;
        zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
    }

    @Override
    public void tick() {
        ticks++;
        Vec3 latest = ears.zomboidcraft$heardAt();
        if (latest != null && !latest.equals(spot) && ticks - pathedAt >= REPATH_TICKS
                && (latest.distanceToSqr(spot) > MOVED * MOVED || zombie.getNavigation().isDone())) {
            // a fresh path only when the noise is somewhere new: footsteps and banging come twice a second, and a
            // crowd planning a path to each one is what slows a big horde down
            destination = latest;
            spot = latest;
            searched = 0;
            arrivedAt = -1;
            pathedAt = ticks;
            zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
        } else if (ticks % 40 == 0 && zombie.getNavigation().isDone()) {
            if (searched > 0) {
                nextSearchPoint();          // that one's out of reach: on to the next
            } else {
                zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0);
            }
        }
    }

    /**
     * The next point of a widening spiral round where it heard or lost you, looking along the way (its head turns
     * as it walks). False when its memory reaches no further, so it gives up and forgets.
     */
    private boolean nextSearchPoint() {
        double reach = Lore.searchReach(zombie);
        double r = SPIRAL_STEP * (1 + searched / (double) SPIRAL_POINTS);
        if (reach <= 0 || r > reach) {
            return false;
        }
        double angle = searched * (Math.PI * 2 / SPIRAL_POINTS) + Lore.roll(zombie, 9) * Math.PI * 2;
        searched++;
        destination = spot.add(Math.cos(angle) * r, 0, Math.sin(angle) * r);
        // slower than the walk in: it's looking, not going somewhere
        zombie.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.8);
        return true;
    }

    /**
     * Out of patience: the walk there gets the usual search time (senses.searchSeconds) whatever its memory, so even
     * one with none comes to a noise; once there it searches for as long as its memory lets it.
     */
    private boolean gaveUp() {
        if (arrivedAt < 0) {
            return ticks >= com.zomboidcraft.ZcConfig.get().senses.searchSeconds * 20;
        }
        return ticks - arrivedAt >= Lore.searchSeconds(zombie) * 20;
    }

    @Override
    public void stop() {
        // Only lose interest after arriving or giving up, not when another goal (like bashing) takes over.
        if (gaveUp() || destination == null || zombie.position().distanceToSqr(destination) <= 4.0) {
            ears.zomboidcraft$forget();
        }
        destination = null;
        spot = null;
        zombie.getNavigation().stop();
    }
}
