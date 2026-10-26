package com.zomboidcraft.client.sound;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.zombie.Zombie;

/**
 * The game's music, which replaces Minecraft's entirely (its own tracks are stopped as they start). Josiah wants
 * ambient noise under the world, not songs (sounds.json music.*, built by tools/audio/build_music.py): a very soft
 * low rumble that's always there, an eerie bed that comes in at night, and a heavy bass bed while something's chasing
 * you, which plays its own ending when the chase is over. All on the Music slider.
 */
public final class Score {
    private static final SoundEvent BED = event("music.bed"), NIGHT = event("music.night"),
            CHASE = event("music.chase"), CHASE_END = event("music.chase_end");
    /** Josiah: the rumble is "a constant noise but very soft". */
    private static final float BED_LEVEL = 0.3f;
    /** A walker this close that's after someone counts as a chase; it's over once none has been for this long. */
    private static final double CHASE_RANGE = 24;
    private static final int CHASE_HOLD = 80;

    private static final Layer bed = new Layer(BED), night = new Layer(NIGHT), chase = new Layer(CHASE);
    private static int sinceChaser = CHASE_HOLD;
    private static boolean chasing;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Score::tick);
    }

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(ZomboidCraft.id(name));
    }

    private static void tick(Minecraft client) {
        client.getMusicManager().stopPlaying();
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            chasing = false;
            sinceChaser = CHASE_HOLD;
            bed.tick(client, 0, 0.05f);
            night.tick(client, 0, 0.05f);
            chase.tick(client, 0, 0.05f);
            return;
        }
        if (player.tickCount % 10 == 0) {
            boolean near = !client.level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(CHASE_RANGE),
                    z -> z.isAlive() && z.isAggressive()).isEmpty();
            sinceChaser = near ? 0 : sinceChaser + 10;
        }
        boolean chased = sinceChaser < CHASE_HOLD;
        if (chasing && !chased) {
            // the chase is over: the loop lets go over a second while its own ending plays out
            client.getSoundManager().play(new Loop(CHASE_END, null));
            chase.snap(client, 1f / 20);
        }
        chasing = chased;
        float nightness = (float) WeatherView.nightness(client.level);
        bed.tick(client, BED_LEVEL, 0.01f);
        night.tick(client, chased ? 0 : nightness, chased ? 0.05f : 0.005f);
        chase.tick(client, chased ? 1 : 0, chased ? 0.05f : 0);
    }

    /** One looping piece, eased toward a level; started when it's wanted, stopped once it's gone quiet. */
    private static final class Layer {
        private final SoundEvent event;
        private float level;
        private Loop playing;

        Layer(SoundEvent event) {
            this.event = event;
        }

        void tick(Minecraft client, float target, float rate) {
            level = target > level ? Math.min(target, level + rate) : Math.max(target, level - rate);
            boolean active = playing != null && !playing.isStopped() && client.getSoundManager().isActive(playing);
            if (level > 0.005f && !active) {
                playing = new Loop(event, this);
                client.getSoundManager().play(playing);
            } else if (level <= 0 && playing != null) {
                client.getSoundManager().stop(playing);
                playing = null;
            }
        }

        /** Fade to nothing at this rate a tick, whatever it's asked for meanwhile, then stop. */
        void snap(Minecraft client, float rate) {
            if (playing != null) {
                playing.release = rate;
                playing = null;
            }
            level = 0;
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final Layer layer;
        private float release;

        /** Loops with its layer's level; with no layer, plays once through at full volume. */
        Loop(SoundEvent event, Layer layer) {
            super(event, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.layer = layer;
            this.looping = layer != null;
            this.delay = 0;
            this.volume = layer != null ? layer.level : 1f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (release > 0) {
                volume = Mth.clamp(volume - release, 0, 1);
                if (volume <= 0) {
                    stop();
                }
            } else if (layer != null) {
                volume = layer.level;
            }
        }
    }

    private Score() {
    }
}
