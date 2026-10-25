package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Replaces vanilla's "see a player within 35 blocks through any angle" targeting. Zombies
 * notice players with {@link Sight}, chase while they can see them, keep chasing for a
 * while after losing sight, then go and search the spot they lost them. They scream when they first
 * spot someone and snarl on the chase ({@link ZombieVoice}).
 */
public class ZombieSightGoal extends TargetGoal {
    private final Zombie zombie;
    private Player spotted;
    private long nextSnarl;

    public ZombieSightGoal(Zombie zombie) {
        super(zombie, true, false);
        this.zombie = zombie;
        setFlags(EnumSet.of(Flag.TARGET));
        setUnseenMemoryTicks(ZcConfig.get().senses.memorySeconds * 20);
    }

    @Override
    public boolean canUse() {
        if ((zombie.tickCount + zombie.getId()) % 5 != 0) {
            return false;
        }
        spotted = null;
        double best = Double.MAX_VALUE;
        for (Player player : zombie.level().players()) {
            double distance = zombie.distanceToSqr(player);
            if (distance < best && distance < 64 * 64 && Sight.canSee(zombie, player)) {
                best = distance;
                spotted = player;
            }
        }
        return spotted != null;
    }

    @Override
    public void start() {
        boolean fresh = zombie.getTarget() == null;
        zombie.setTarget(spotted);
        super.start();
        if (fresh) {
            ZombieVoice.spotted(zombie);
        }
        nextSnarl = zombie.level().getGameTime() + ZombieVoice.nextSnarl(zombie);
    }

    @Override
    public void tick() {
        super.tick();
        long now = zombie.level().getGameTime();
        if (zombie.getTarget() != null && now >= nextSnarl) {
            ZombieVoice.snarl(zombie);
            nextSnarl = now + ZombieVoice.nextSnarl(zombie);
        }
    }

    @Override
    public void stop() {
        LivingEntity lost = zombie.getTarget();
        if (lost != null && lost.isAlive()) {
            // Head for where we lost them and look around.
            ((NoiseListener) zombie).zomboidcraft$hear(lost.position());
        }
        super.stop();
        spotted = null;
    }
}
