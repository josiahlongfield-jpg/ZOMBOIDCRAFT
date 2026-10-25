package com.zomboidcraft.client.zombie;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.combat.ModSounds;
import com.zomboidcraft.zombie.Hordes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Crowd beds under the single zombie voices: a murmur that swells with the walkers round you and a roar
 * with the ones coming after you (the server sends both counts). Each bed is a run of takes with their own
 * fades, the next started before the last ends so they crossfade into one sound, and never the same take
 * twice running. It comes from where the crowd is, kept close so it stays heard.
 */
public final class HordeSounds {
    /** The murmur's takes run 16 s and the roar's 12 s; each next one starts 2 s early. */
    private static final Bed MURMUR = new Bed(ModSounds.ZOMBIE_HORDE_DORMANT, 14 * 20);
    private static final Bed ROAR = new Bed(ModSounds.ZOMBIE_HORDE_ANGRY, 10 * 20);
    private static final double PULL_IN = 6;
    private static Vec3 centre = Vec3.ZERO;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(HordeSounds::tick);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ZcConfig.Zombies cfg = ZcConfig.get().zombies;
        float murmur = 0, roar = 0;
        if (player != null && client.level != null && cfg.hordeSounds) {
            int packed = player.getAttachedOrElse(Hordes.COUNT, 0);
            murmur = (float) (swell(Hordes.near(packed), cfg.hordeMurmurFrom, cfg.hordeMurmurFull) * cfg.hordeMurmurVolume);
            roar = (float) (swell(Hordes.after(packed), cfg.hordeRoarFrom, cfg.hordeRoarFull) * cfg.hordeRoarVolume);
            if (player.tickCount % 10 == 0) {
                centre = crowd(player);
            }
        }
        Vec3 eye = player == null ? Vec3.ZERO : player.getEyePosition();
        Vec3 to = centre.subtract(eye);
        Vec3 at = to.lengthSqr() < 1e-4 ? eye : eye.add(to.normalize().scale(Math.min(to.length(), PULL_IN)));
        MURMUR.tick(client, murmur, at);
        ROAR.tick(client, roar, at);
    }

    /** 0 below `from`, then a quarter rising to full at `full`. */
    private static double swell(int count, int from, int full) {
        if (count < from) {
            return 0;
        }
        return 0.25 + 0.75 * Mth.clamp((count - from) / (double) Math.max(1, full - from), 0, 1);
    }

    /** Where the zombies round the player are, on average. */
    private static Vec3 crowd(LocalPlayer player) {
        double x = 0, y = 0, z = 0;
        int n = 0;
        for (Zombie zombie : player.level().getEntitiesOfClass(Zombie.class, new AABB(player.blockPosition()).inflate(Hordes.RANGE),
                Zombie::isAlive)) {
            x += zombie.getX();
            y += zombie.getEyeY();
            z += zombie.getZ();
            n++;
        }
        return n == 0 ? player.getEyePosition() : new Vec3(x / n, y / n, z / n);
    }

    /** One layer: the level it's easing toward, and the takes playing now. */
    private static final class Bed {
        private final SoundEvent event;
        private final int period;
        private float level;
        private Vec3 at = Vec3.ZERO;
        private Identifier lastTake;
        private Take latest;
        private int sinceStart;

        Bed(SoundEvent event, int period) {
            this.event = event;
            this.period = period;
        }

        void tick(Minecraft client, float target, Vec3 at) {
            this.at = at;
            level += (target - level) * 0.04f;
            if (level < 0.002f && target == 0) {
                level = 0;
            }
            sinceStart++;
            boolean playing = latest != null && !latest.isStopped() && client.getSoundManager().isActive(latest);
            if (level > 0.01f && (!playing || sinceStart >= period)) {
                latest = new Take(this);
                latest.moveTo(at);
                client.getSoundManager().play(latest);
                sinceStart = 0;
            }
        }
    }

    private static final class Take extends AbstractTickableSoundInstance {
        private final Bed bed;
        private int age;

        Take(Bed bed) {
            super(bed.event, SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.bed = bed;
            this.looping = false;
            this.delay = 0;
            this.volume = bed.level;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        void moveTo(Vec3 at) {
            x = at.x;
            y = at.y;
            z = at.z;
        }

        /** Picks a take other than the one the bed played last. */
        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            WeighedSoundEvents events = super.resolve(manager);
            for (int i = 0; i < 8 && sound != null && sound.getLocation().equals(bed.lastTake); i++) {
                events = super.resolve(manager);
            }
            if (sound != null) {
                bed.lastTake = sound.getLocation();
            }
            return events;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            age++;
            volume = bed.level;
            moveTo(bed.at);
            if (bed.level <= 0 && age > 20) {
                stop();
            }
        }
    }

    private HordeSounds() {
    }
}
