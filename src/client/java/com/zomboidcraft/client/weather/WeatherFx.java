package com.zomboidcraft.client.weather;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherParticles;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.ArrayList;
import java.util.List;

/**
 * The weather you can see around you, drawn so shader packs show it too: the mist lying over the
 * ground ({@link GroundFog}), litter blowing along when the wind gets up, and storms flickering
 * over the skyline with the thunder rolling in after.
 *
 * Particles are only kept near you, and spawned in the open (by the heightmap) near your own
 * height, so the street gets them but rooms and rooftops far above don't.
 */
public final class WeatherFx {
    private static final RandomSource RANDOM = RandomSource.create();
    private static float debrisDue;
    /** Ticks to the next flash; and a second flicker of the same stroke, when there is one. */
    private static int nextFlash = 200, flicker;
    private static float flickerStrength;
    /**
     * Client-only lightning gets ids from near the top of the range, far above anything the server
     * hands out, counting down (26.3 throws on any negative id; aircraft stand-ins count down from
     * the very top). The bolt being added right now, so the load event knows it's ours.
     */
    private static int boltId = Integer.MAX_VALUE - 2_000_000;
    private static LightningBolt adding;
    private static final List<Thunder> THUNDER = new ArrayList<>();

    /** A clap on its way: sound is slower than light, so the far ones arrive seconds later. */
    private record Thunder(int at, double x, double y, double z, float volume, float pitch) {
    }

    private static int clock;
    private static ClientLevel lastLevel;

    public static void register() {
        ParticleProviderRegistry.getInstance().register(WeatherParticles.MIST, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new MistParticle(level, x, y, z, dx, dz, (float) dy, sprites.get(random)));
        ParticleProviderRegistry.getInstance().register(WeatherParticles.DEBRIS, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new DebrisParticle(level, x, y, z, dx, dz, sprites.get(random)));
        ParticleProviderRegistry.getInstance().register(WeatherParticles.PLUME, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new PlumeParticle(level, x, y, z, dx, dz, (float) dy, sprites.get(random)));
        ParticleProviderRegistry.getInstance().register(WeatherParticles.EMBER, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new EmberParticle(level, x, y, z, (float) dy, sprites.get(random)));
        // a glint's colour travels in the velocity slots
        ParticleProviderRegistry.getInstance().register(WeatherParticles.GLINT, sprites -> (type, level, x, y, z, dx, dy, dz, random) -> {
            GlintParticle glint = new GlintParticle(level, x, y, z, (float) dx, (float) dy, (float) dz, sprites.get(random));
            glint.lean(Glints.leanX, Glints.leanZ);
            return glint;
        });
        ClientTickEvents.END_CLIENT_TICK.register(WeatherFx::tick);
        LevelRenderEvents.COLLECT_SUBMITS.register(GroundFog::draw);
        // every bolt that lands near you, the server's real strikes too, lights up the street
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof LightningBolt && entity != adding) {  // our own flashes set their own strength
                strike(level, entity.getX(), entity.getZ());
                Skyline.struck(entity.getX(), entity.getZ());  // real strikes start real fires: let them smoulder
            }
        });
    }

    /**
     * A bolt has come down at x, z: a full flash, the street lit round the strike, and if it's
     * close the lamps round you stutter.
     */
    private static void strike(ClientLevel level, double x, double z) {
        flash(1);
        FlashLights.bolt(level, x, z);
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && ZcConfig.get().weather.stormSurges
                && Math.hypot(player.getX() - x, player.getZ() - z) < 64) {
            StormLights.surge(level, player.blockPosition());
        }
    }

    private static void add(ClientLevel level, LightningBolt bolt) {
        adding = bolt;
        try {
            level.addEntity(bolt);
        } finally {
            adding = null;
        }
    }

    /** Lights the world for a moment; strength 1 for a bolt close by, less for one far off in the clouds. */
    private static void flash(float strength) {
        WeatherView.setFlash(Math.max(WeatherView.flash(), strength));
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        if (level != lastLevel) {
            // a new world or dimension: what we swapped or lit belonged to the old one
            StormLights.forget();
            FlashLights.forget();
            Water.forget();
            Glints.forget();
            GroundFog.forget();
            GhostPower.forget();
            Skyline.forget();
            THUNDER.clear();
            lastLevel = level;
        }
        if (level != null && !client.isPaused()) {
            StormLights.tick(level);
            FlashLights.tick(level);
        }
        Weather w = WeatherView.client();
        if (player == null || level == null || w == null || client.isPaused() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ZcConfig.Weather c = ZcConfig.get().weather;
        float night = (float) WeatherView.nightness(level);
        double yaw = Math.toRadians(w.windYaw());
        double windX = -Math.sin(yaw), windZ = Math.cos(yaw);
        if (c.mist && c.mistAmount > 0) {
            // the mist is sheets of fog over the ground now (drawn every frame), not puffs
            GroundFog.tick(level, player, w, night);
        }
        if (c.windDebris && w.wind() > 0.3f) {
            debris(level, player, w, windX, windZ);
        }
        if (c.drips) {
            Water.tick(level, player, w);
        }
        if (c.skylineFires) {
            Skyline.tick(level, player, w);
        }
        if (c.wetGlints) {
            Glints.tick(level, player, w, windX, windZ);
        }
        if (c.ghostPower) {
            GhostPower.tick(level, player.blockPosition(), (float) c.ghostPowerEvery);
        }
        clock++;
        // a flash lasts a few ticks, dying away fast
        WeatherView.setFlash(WeatherView.flash() < 0.02f ? 0 : WeatherView.flash() * 0.62f);
        if (c.stormFlashes) {
            storm(level, player, w);
        }
        thunder(level);
    }

    /**
     * Lightning out over the city in any storm, and in the build-up to a bad one: mostly sheet
     * lightning lighting the clouds, now and then a bolt you can see coming down between the
     * towers (harmless, only drawn on your side; real strikes come from the server in the
     * dangerous storms). Each flash brings its thunder a moment later.
     */
    private static void storm(ClientLevel level, LocalPlayer player, Weather w) {
        if (flicker > 0 && --flicker == 0) {
            level.setSkyFlashTime(2);
            flash(flickerStrength);
            FlashLights.sky(level, player.blockPosition(), flickerStrength * 0.8f);
        }
        if (w.thunder() < 0.15f || --nextFlash > 0) {
            return;
        }
        float t = w.thunder();
        nextFlash = (int) ((80 + RANDOM.nextInt(320)) * (1.3f - t));
        double angle = RANDOM.nextDouble() * Mth.TWO_PI;
        boolean bolt = t > 0.4f && RANDOM.nextFloat() < 0.35f;
        // a visible bolt has to fall inside the distance the game draws lightning at
        double distance = bolt ? 34 + RANDOM.nextDouble() * 26 : 250 + RANDOM.nextDouble() * 1200;
        double x = player.getX() + Math.cos(angle) * distance, z = player.getZ() + Math.sin(angle) * distance;
        if (bolt) {
            LightningBolt strike = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
            strike.setVisualOnly(true);
            strike.setSilent(true);  // its thunder is ours, below
            strike.setId(boltId--);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            strike.snapTo(x, y, z);
            add(level, strike);
            strike(level, x, z);
        } else {
            // sheet lightning: the clouds light up and the street with them, less the further off it is.
            // A bolt high up and far out of sight is spawned too, so shader packs that flash on
            // lightning (Photon reads where the last bolt was) flash with it
            level.setSkyFlashTime(2);
            float strength = (float) Mth.clamp(1.1 - distance / 1500, 0.35, 0.85);
            flash(strength);
            FlashLights.sky(level, player.blockPosition(), strength);
            LightningBolt sheet = new LightningBolt(EntityTypes.LIGHTNING_BOLT, level);
            sheet.setVisualOnly(true);
            sheet.setSilent(true);
            sheet.setId(boltId--);
            sheet.snapTo(player.getX() + Math.cos(angle) * 160, player.getY() + 90, player.getZ() + Math.sin(angle) * 160);
            add(level, sheet);
        }
        if (RANDOM.nextFloat() < 0.5f) {
            flicker = 3 + RANDOM.nextInt(4);
            flickerStrength = bolt ? 0.8f : 0.5f;
        }
        // heard from its direction, a few blocks out; near ones crack, far ones rumble low
        double ear = 24;
        float volume = bolt ? 1.4f + RANDOM.nextFloat() * 0.6f : 0.35f + 0.5f * t * RANDOM.nextFloat();
        float pitch = bolt ? 0.8f + RANDOM.nextFloat() * 0.2f : 0.5f + RANDOM.nextFloat() * 0.25f;
        int delay = (int) (distance / 17);  // sound covers about 17 blocks a tick
        THUNDER.add(new Thunder(clock + delay, player.getX() + Math.cos(angle) * ear, player.getY() + 8,
                player.getZ() + Math.sin(angle) * ear, volume, pitch));
    }

    private static void thunder(ClientLevel level) {
        THUNDER.removeIf(clap -> {
            if (clock < clap.at()) {
                return false;
            }
            level.playLocalSound(clap.x(), clap.y(), clap.z(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                    clap.volume(), clap.pitch(), false);
            return true;
        });
    }

    private static void debris(ClientLevel level, LocalPlayer player, Weather w, double windX, double windZ) {
        debrisDue += (w.wind() - 0.3f) * 1.4f;
        double push = 0.06 + 0.22 * w.wind();
        for (; debrisDue >= 1; debrisDue--) {
            // start upwind of you so it blows past
            double side = (RANDOM.nextDouble() - 0.5) * 36;
            double back = 4 + RANDOM.nextDouble() * 18;
            double x = player.getX() - windX * back - windZ * side, z = player.getZ() - windZ * back + windX * side;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            if (Math.abs(top - player.getY()) > 6) {
                continue;
            }
            level.addParticle(WeatherParticles.DEBRIS, x, top + 0.1 + RANDOM.nextDouble() * 1.5, z, windX * push, 0, windZ * push);
        }
    }

    private WeatherFx() {
    }
}
