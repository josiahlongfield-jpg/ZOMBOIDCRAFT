package com.zomboidcraft.zombie.perf;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.ZombieTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * A walker standing still skips its movement (performance.restingWalkers). Standing on the ground with no push, no
 * walk input and no path, Minecraft still runs a full move every tick (gravity into the floor, block collision, the
 * step-up search, the blocks it's touching): a third of the server tick for a big horde. A rested tick leaves it where
 * it is, as the move would have. It runs the full move every tenth tick anyway (staggered), and at once when anything
 * gives it motion: a shove, a hit, a jump, an escalator, the floor going.
 */
public final class Resting {
    /** The walker that rested this tick, so the block effects that follow its move are skipped too (server thread). */
    private static LivingEntity rested;

    /** Called at the start of travel: true to skip it. */
    public static boolean rest(LivingEntity e, Vec3 input) {
        if (!(e instanceof Mob mob) || e.level().isClientSide() || !ZombieTypes.isWalker(e)
                || !ZcConfig.get().performance.restingWalkers) {
            return false;
        }
        if (rested == e) {
            rested = null;
        }
        if ((e.tickCount + e.getId()) % 10 == 0 || !e.onGround() || input.lengthSqr() > 1e-6 || e.isPassenger()
                || e.isInWater() || e.isInLava() || mob.getNavigation().isInProgress()) {
            HordeStats.moved++;
            return false;
        }
        Vec3 v = e.getDeltaMovement();
        if (v.x * v.x + v.z * v.z > 1e-5 || v.y > 0 || v.y < -0.1 || e.level().getBlockState(e.getOnPos()).isAir()) {
            HordeStats.moved++;
            return false;
        }
        rested = e;
        HordeStats.rested++;
        return true;
    }

    /** Whether this entity's block effects belong to a move it skipped. */
    public static boolean skipEffects(Object e) {
        if (e != rested) {
            return false;
        }
        rested = null;
        return true;
    }

    private Resting() {
    }
}
