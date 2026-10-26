package com.zomboidcraft.client.sound;

import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The world's own sound (sounds.json amb.*): rain on the street outside and on the roof once you're under one,
 * rain on the glass beside a window in a blow, drips, wind that rises with the weather, the night, and now and
 * then a creak in the house or something far off. The game's rain loops are silenced in sounds.json and
 * replaced by these; indoors is worked out here by looking up for a roof, not from the heightmap. No animals:
 * Josiah found the crows and owl annoying, so they're gone.
 */
public final class Ambience {
    private static final RandomSource RANDOM = RandomSource.create();

    private static final Bed RAIN = new Bed("amb.rain", SoundSource.WEATHER);
    private static final Bed ROOF = new Bed("amb.roof", SoundSource.WEATHER);
    private static final Bed WINDOW = new Bed("amb.window", SoundSource.WEATHER);
    private static final Bed DRIP = new Bed("amb.drip", SoundSource.WEATHER);
    private static final Bed WIND = new Bed("amb.wind", SoundSource.WEATHER);
    private static final Bed NIGHT = new Bed("amb.night", SoundSource.AMBIENT);
    private static final Bed[] BEDS = {RAIN, ROOF, WINDOW, DRIP, WIND, NIGHT};

    private static final SoundEvent CREAK = event("amb.creak");
    private static final SoundEvent DISTANT = event("amb.distant");

    /** How far up a roof is looked for, and how far round a window. */
    private static final int ROOF_HEIGHT = 24, WINDOW_REACH = 4;

    private static float indoors, nearWindow;
    private static int nextCreak = 400, nextDistant = 6000;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(Ambience::tick);
    }

    private static SoundEvent event(String name) {
        return SoundEvent.createVariableRangeEvent(ZomboidCraft.id(name));
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (player == null || level == null) {
            for (Bed bed : BEDS) {
                bed.tick(client, 0);
            }
            return;
        }
        if (player.tickCount % 10 == 0) {
            BlockPos eye = BlockPos.containing(player.getEyePosition());
            indoors += (roofed(level, eye) - indoors) * 0.5f;
            nearWindow += (window(level, eye) - nearWindow) * 0.5f;
        }
        Weather weather = WeatherView.client();
        float rain = weather != null ? weather.rain() : level.getRainLevel(1f);
        float wind = weather != null ? weather.wind() : 0.2f + 0.3f * rain;
        float night = (float) WeatherView.nightness(level);
        float out = 1 - indoors;

        RAIN.tick(client, rain * out);
        ROOF.tick(client, rain * indoors);
        WINDOW.tick(client, rain * indoors * nearWindow * Mth.clamp(wind * 1.6f - 0.3f, 0, 1));
        DRIP.tick(client, rain * indoors * 0.7f);
        WIND.tick(client, Mth.clamp(wind, 0, 1) * (1 - 0.6f * indoors));
        NIGHT.tick(client, night * (1 - Mth.clamp(rain * 1.5f, 0, 1)) * (1 - 0.7f * indoors));

        if (--nextCreak <= 0) {
            nextCreak = between(20, 60);
            if (indoors > 0.6f) {
                shot(client, CREAK, around(player.getEyePosition(), 3, 7, -2, 3), 0.6f + RANDOM.nextFloat() * 0.4f);
            }
        }
        if (--nextDistant <= 0) {
            nextDistant = between(360, 720);
            shot(client, DISTANT, around(player.getEyePosition(), 60, 90, 0, 10), 0.8f + RANDOM.nextFloat() * 0.2f);
        }
    }

    /** How much of a roof is over you: five columns round your head, each looked up until something solid. */
    private static float roofed(ClientLevel level, BlockPos eye) {
        int covered = 0;
        int[][] columns = {{0, 0}, {2, 0}, {-2, 0}, {0, 2}, {0, -2}};
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] column : columns) {
            for (int dy = 1; dy <= ROOF_HEIGHT; dy++) {
                pos.set(eye.getX() + column[0], eye.getY() + dy, eye.getZ() + column[1]);
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && (state.canOcclude() || !state.getCollisionShape(level, pos).isEmpty())) {
                    covered++;
                    break;
                }
            }
        }
        return covered / (float) columns.length;
    }

    /** 1 with a window right beside you, fading to 0 at WINDOW_REACH blocks. */
    private static float window(ClientLevel level, BlockPos eye) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double best = Double.MAX_VALUE;
        for (int dx = -WINDOW_REACH; dx <= WINDOW_REACH; dx++) {
            for (int dz = -WINDOW_REACH; dz <= WINDOW_REACH; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    pos.set(eye.getX() + dx, eye.getY() + dy, eye.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
                    if (name.contains("glass") || name.contains("window")) {
                        best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    }
                }
            }
        }
        return best == Double.MAX_VALUE ? 0 : (float) Mth.clamp(1 - (best - 1) / WINDOW_REACH, 0, 1);
    }

    private static int between(int fromSeconds, int toSeconds) {
        return (fromSeconds + RANDOM.nextInt(toSeconds - fromSeconds + 1)) * 20;
    }

    /** A point `near` to `far` blocks away across the ground, `low` to `high` blocks up. */
    private static Vec3 around(Vec3 from, double near, double far, double low, double high) {
        double a = RANDOM.nextDouble() * Math.PI * 2, d = near + RANDOM.nextDouble() * (far - near);
        return from.add(Math.cos(a) * d, low + RANDOM.nextDouble() * (high - low), Math.sin(a) * d);
    }

    private static void shot(Minecraft client, SoundEvent event, Vec3 at, float volume) {
        client.getSoundManager().play(new Shot(event, at, volume));
    }

    /** One looping bed, eased toward a level each tick; started when it's wanted, stopped once it's silent. */
    /**
     * A bed loops one of its recordings, and every 24 to 72 seconds crossfades over three seconds into a fresh
     * random one, so a bed that never goes quiet (wind, mostly) doesn't sit on the same take forever.
     */
    private static final class Bed {
        private static final int FADE = 60, MIN_RUN = 480, MAX_RUN = 1440;
        private final SoundEvent event;
        private final SoundSource source;
        private float level;
        private Loop playing, fading;
        private int silent, run;

        Bed(String name, SoundSource source) {
            this.event = event(name);
            this.source = source;
        }

        void tick(Minecraft client, float target) {
            level += (target - level) * 0.03f;
            if (level < 0.003f && target <= 0) {
                level = 0;
            }
            boolean active = playing != null && !playing.isStopped() && client.getSoundManager().isActive(playing);
            if (level > 0.01f && !active) {
                start(client, 1);
            } else if (active && --run <= 0) {
                if (fading != null) {
                    client.getSoundManager().stop(fading);
                }
                fading = playing;
                fading.fade = -1f / FADE;
                start(client, 0);
            }
            if (fading != null && (fading.gain <= 0 || fading.isStopped())) {
                client.getSoundManager().stop(fading);
                fading = null;
            }
            silent = level <= 0 ? silent + 1 : 0;
            if (playing != null && silent > 40) {
                client.getSoundManager().stop(playing);
                playing = null;
                if (fading != null) {
                    client.getSoundManager().stop(fading);
                    fading = null;
                }
            }
        }

        private void start(Minecraft client, float gain) {
            playing = new Loop(this, gain);
            if (gain < 1) {
                playing.fade = 1f / FADE;
            }
            run = MIN_RUN + RANDOM.nextInt(MAX_RUN - MIN_RUN);
            client.getSoundManager().play(playing);
        }
    }

    /** A bed's loop, played at your ears rather than from a place in the world. */
    private static final class Loop extends AbstractTickableSoundInstance {
        private final Bed bed;
        /** This take's share of the bed while crossfading, and how much it changes each tick. */
        private float gain, fade;

        Loop(Bed bed, float gain) {
            super(bed.event, bed.source, SoundInstance.createUnseededRandom());
            this.bed = bed;
            this.gain = gain;
            this.looping = true;
            this.delay = 0;
            this.volume = bed.level * gain;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            gain = Mth.clamp(gain + fade, 0, 1);
            volume = bed.level * gain;
        }
    }

    /** One sparse sound from somewhere round you. */
    private static final class Shot extends AbstractSoundInstance {
        Shot(SoundEvent event, Vec3 at, float volume) {
            super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.volume = volume;
            this.pitch = 0.92f + RANDOM.nextFloat() * 0.16f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }
    }

    private Ambience() {
    }
}
