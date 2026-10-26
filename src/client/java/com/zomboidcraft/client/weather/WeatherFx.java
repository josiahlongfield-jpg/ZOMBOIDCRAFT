package com.zomboidcraft.client.weather;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.world.weather.Weather;
import com.zomboidcraft.world.weather.WeatherParticles;
import com.zomboidcraft.world.weather.WeatherView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The weather you can see around you, made of particles so shader packs draw it too: mist banks
 * drifting with the wind when the fog is in, a low ground mist hanging in the streets at night,
 * and litter blowing along when the wind gets up.
 *
 * Particles are only kept near you, and spawned in the open (by the heightmap) near your own
 * height, so the street fills with mist but rooms and rooftops far above don't.
 */
public final class WeatherFx {
    private static final RandomSource RANDOM = RandomSource.create();
    /** Puffs kept round you in full fog: enough to wall off the street, few enough to stay cheap. */
    private static final int MIST_MAX = 170;
    /** A puff's mean lifetime in ticks (MistParticle), to turn a head count into a spawn rate. */
    private static final float MIST_LIFE = 370;
    private static float mistDue, debrisDue;

    public static void register() {
        ParticleProviderRegistry.getInstance().register(WeatherParticles.MIST, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new MistParticle(level, x, y, z, dx, dz, (float) dy, sprites.get(random)));
        ParticleProviderRegistry.getInstance().register(WeatherParticles.DEBRIS, sprites ->
                (type, level, x, y, z, dx, dy, dz, random) -> new DebrisParticle(level, x, y, z, dx, dz, sprites.get(random)));
        ClientTickEvents.END_CLIENT_TICK.register(WeatherFx::tick);
    }

    private static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level = client.level;
        Weather w = WeatherView.client();
        if (player == null || level == null || w == null || client.isPaused() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        ZcConfig.Weather c = ZcConfig.get().weather;
        float night = (float) WeatherView.nightness(level);
        double yaw = Math.toRadians(w.windYaw());
        double windX = -Math.sin(yaw), windZ = Math.cos(yaw);
        if (c.mist && c.mistAmount > 0) {
            mist(level, player, w, night, windX, windZ, (float) c.mistAmount);
        }
        if (c.windDebris && w.wind() > 0.3f) {
            debris(level, player, w, windX, windZ);
        }
    }

    private static void mist(ClientLevel level, LocalPlayer player, Weather w, float night, double windX, double windZ, float amount) {
        // fog banks, plus a thin mist that always lies in the streets, more of it at night
        float ground = 0.10f + 0.12f * night + 0.08f * w.rain();
        float thick = Math.max(w.fog(), ground);
        mistDue += MIST_MAX * thick * amount / MIST_LIFE;
        float drift = 0.008f + 0.05f * w.wind();
        for (; mistDue >= 1; mistDue--) {
            double angle = RANDOM.nextDouble() * Mth.TWO_PI;
            double r = 3 + Math.sqrt(RANDOM.nextDouble()) * 30;
            double x = player.getX() + Math.cos(angle) * r, z = player.getZ() + Math.sin(angle) * r;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z));
            if (Math.abs(top - player.getY()) > 10) {
                continue;  // a roof well above you, or a drop: keep the mist where you are
            }
            // ground mist hugs the street; a fog bank stands taller
            double y = top + 0.2 + RANDOM.nextDouble() * (1.2 + 5 * w.fog());
            float strength = (0.05f + 0.22f * w.fog() + 0.05f * night) * (0.6f + 0.4f * RANDOM.nextFloat());
            double speed = drift * (0.6 + 0.8 * RANDOM.nextDouble());
            level.addParticle(WeatherParticles.MIST, x, y, z, windX * speed, strength, windZ * speed);
        }
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
