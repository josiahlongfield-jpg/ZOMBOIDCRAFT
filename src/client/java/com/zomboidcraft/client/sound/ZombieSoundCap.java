package com.zomboidcraft.client.sound;

import com.zomboidcraft.ZcConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.world.entity.player.Player;

/**
 * Keeps zombie groans, steps and door blows to a share of the sound channels, nearest first. Minecraft has about 247
 * and drops a sound without a word when they're all taken: with 800 zombies about, the gun went silent (Josiah,
 * 2026-11-06). A new zombie sound nearer than the furthest one playing takes its place; one further off isn't played.
 */
public final class ZombieSoundCap {
    private static final List<SoundInstance> PLAYING = new ArrayList<>();

    private ZombieSoundCap() {
    }

    /** True when the sound may play; false when the zombies already have their share and this one is no nearer. */
    public static boolean allow(SoundEngine engine, SoundInstance sound) {
        int cap = ZcConfig.get().performance.zombieSoundCap;
        if (cap <= 0 || sound.isRelative()) {
            return true;
        }
        SoundVolumes.Group group = SoundVolumes.groupOf(sound.getIdentifier());
        if (group != SoundVolumes.Group.ZOMBIE_VOICES && group != SoundVolumes.Group.ZOMBIE_STEPS
                && group != SoundVolumes.Group.DOOR_BASHING) {
            return true;
        }
        PLAYING.removeIf(s -> !engine.isActive(s));
        Player player = Minecraft.getInstance().player;
        if (PLAYING.size() >= cap && player != null) {
            double d = dist(sound, player);
            SoundInstance furthest = null;
            double most = -1;
            for (SoundInstance s : PLAYING) {
                double e = dist(s, player);
                if (e > most) {
                    most = e;
                    furthest = s;
                }
            }
            if (furthest == null || most <= d) {
                return false;
            }
            PLAYING.remove(furthest);
            engine.stop(furthest);
        }
        PLAYING.add(sound);
        return true;
    }

    private static double dist(SoundInstance s, Player p) {
        double dx = s.getX() - p.getX(), dy = s.getY() - p.getY(), dz = s.getZ() - p.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
