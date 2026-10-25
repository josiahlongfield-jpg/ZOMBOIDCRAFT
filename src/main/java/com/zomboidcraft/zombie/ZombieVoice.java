package com.zomboidcraft.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.ModSounds;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A walker's scream when it spots someone and its snarls on the chase. Screams are rationed: each
 * zombie waits a while before screaming again, and only the first few in a crowd scream together.
 */
public final class ZombieVoice {
    private static final double TOGETHER_RANGE = 16;
    private static final int TOGETHER_TICKS = 40;
    private static final Map<Zombie, Long> LAST_SCREAM = new WeakHashMap<>();
    private static final Map<Level, ArrayDeque<Scream>> RECENT = new WeakHashMap<>();

    private record Scream(Vec3 at, long time) {
    }

    private ZombieVoice() {
    }

    /** Called when a zombie first takes a target. */
    public static void spotted(Zombie zombie) {
        ZcConfig.Zombies cfg = ZcConfig.get().zombies;
        if (!cfg.alertScreams || !(zombie.level() instanceof ServerLevel level) || zombie.isSilent()) {
            return;
        }
        long now = level.getGameTime();
        Long last = LAST_SCREAM.get(zombie);
        if (last != null && now - last < cfg.alertCooldownSeconds * 20L) {
            return;
        }
        ArrayDeque<Scream> recent = RECENT.computeIfAbsent(level, l -> new ArrayDeque<>());
        while (!recent.isEmpty() && now - recent.peekFirst().time() > TOGETHER_TICKS) {
            recent.pollFirst();
        }
        Vec3 at = zombie.position();
        int near = 0;
        for (Scream s : recent) {
            if (s.at().closerThan(at, TOGETHER_RANGE)) {
                near++;
            }
        }
        if (near >= cfg.alertMaxTogether) {
            return;
        }
        recent.addLast(new Scream(at, now));
        LAST_SCREAM.put(zombie, now);
        level.playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), ModSounds.ZOMBIE_ALERT, SoundSource.HOSTILE,
                1f, 0.9f + zombie.getRandom().nextFloat() * 0.2f);
    }

    /** Ticks until the next snarl, picked fresh after each one. */
    public static int nextSnarl(Zombie zombie) {
        ZcConfig.Zombies cfg = ZcConfig.get().zombies;
        double lo = Math.max(0.5, cfg.chaseSnarlMinSeconds), hi = Math.max(lo, cfg.chaseSnarlMaxSeconds);
        return (int) Math.round((lo + zombie.getRandom().nextDouble() * (hi - lo)) * 20);
    }

    public static void snarl(Zombie zombie) {
        if (!ZcConfig.get().zombies.chaseSnarls || zombie.isSilent()) {
            return;
        }
        zombie.level().playSound(null, zombie.getX(), zombie.getEyeY(), zombie.getZ(), ModSounds.ZOMBIE_CHASE,
                SoundSource.HOSTILE, 0.9f, 0.9f + zombie.getRandom().nextFloat() * 0.2f);
    }
}
