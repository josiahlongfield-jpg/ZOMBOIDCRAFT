package com.zomboidcraft.world.trains;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.zombie.Noise;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * What a call button costs: above ground one red flare goes up over the station and hangs there,
 * drifting down till it burns out; in the subway the station's buzzer sounds once. Either way, once,
 * as the button's pressed, every zombie in earshot comes for the station.
 */
final class Beacons {
    private static final DustParticleOptions RED = new DustParticleOptions(0xFF2A1A, 2.5f);
    private static final DustParticleOptions GLOW = new DustParticleOptions(0xFF8A5A, 1.4f);

    private static final class Beacon {
        final Line line;
        final int stop;
        final Vec3 at;
        final long started;
        long lit;

        Beacon(Line line, int stop, Vec3 at, long now) {
            this.line = line;
            this.stop = stop;
            this.at = at;
            this.started = now;
            this.lit = now;
        }
    }

    private static final List<Beacon> ON = new ArrayList<>();

    static void register() {
    }

    static void start(ServerLevel level, Line line, int stop, BlockPos button) {
        Beacon b = new Beacon(line, stop, Vec3.atCenterOf(button), level.getGameTime());
        ON.add(b);
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        if (line.kind == Line.Kind.RAIL) {
            level.playSound(null, b.at.x, b.at.y, b.at.z, TrainSounds.FLARE, SoundSource.NEUTRAL, 3f, 0.7f);
            Noise.emit(level, b.at, cfg.flareNoise);
        } else {
            level.playSound(null, b.at.x, b.at.y, b.at.z, TrainSounds.BUZZER, SoundSource.NEUTRAL, 2f, 1f);
            Noise.emit(level, b.at, cfg.sirenNoise);
            ON.remove(b);   // nothing more to it
        }
    }

    /** A train's come to the stop (a flare already up burns itself out). */
    static void answered(Line line, int stop) {
        ON.removeIf(b -> b.line == line && b.stop == stop);
    }

    static void clear(Line line) {
        ON.removeIf(b -> b.line == line);
    }

    static void tick(ServerLevel level) {
        if (ON.isEmpty()) {
            return;
        }
        ZcConfig.Trains cfg = ZcConfig.get().trains;
        long now = level.getGameTime();
        // one flare a press: gone once it's burnt out
        ON.removeIf(b -> !Trains.lines(false).containsValue(b.line) || now - b.started > Math.max(10, cfg.flareSeconds) * 20L);
        for (Beacon b : ON) {
            flare(level, b, now, cfg);
        }
    }

    /** The flare: two seconds climbing, then hanging and drifting down while it burns. */
    private static void flare(ServerLevel level, Beacon b, long now, ZcConfig.Trains cfg) {
        long t = now - b.lit;
        double top = 42;
        double y = t < 40 ? top * Math.sin(t / 40.0 * Math.PI / 2) : top - (t - 40) * 0.02;
        Vec3 p = b.at.add(Math.sin(t * 0.013) * 2, y, Math.cos(t * 0.011) * 2);
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(p) > 400 * 400) {
                continue;
            }
            level.sendParticles(player, RED, true, true, p.x, p.y, p.z, 4, 0.15, 0.15, 0.15, 0);
            level.sendParticles(player, GLOW, true, true, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
            if (t % 2 == 0) {
                level.sendParticles(player, t < 40 ? ParticleTypes.FLAME : ParticleTypes.SMOKE, true, false,
                        p.x, p.y - 0.3, p.z, 1, 0.05, 0.1, 0.05, 0.01);
            }
        }
    }

    private Beacons() {
    }
}
