package com.zomboidcraft.world.weather;

import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.survival.ModDamageTypes;
import com.zomboidcraft.world.blocks.CityBlock;
import com.zomboidcraft.world.blocks.CityBlocks;
import com.zomboidcraft.zombie.Noise;
import java.util.SplittableRandom;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Runs the overworld's weather in place of vanilla's: the sky never clears, and one kind of
 * weather follows another (drizzle most of the time, then rain, downpours, fog, gales and
 * storms), easing from one to the next over a minute or so. Dangerous storms get a warning
 * first: the wind picks up and thunder rolls in the distance.
 *
 * Every player in the overworld gets the current weather through {@link Weather#TYPE}; the
 * sky and light follow it on their side ({@link WeatherLayers}).
 */
public final class WeatherDirector {
    private static final int SECOND = 20;
    /** Full swing of a value in about a minute. */
    private static final float EASE = 1f / 1200f;
    private static final int WARNING_TICKS = 70 * SECOND;

    private static final SplittableRandom RNG = new SplittableRandom();
    private static Weather current = Weather.CALM;
    private static WeatherKind kind = WeatherKind.RAIN;
    private static WeatherKind queued;
    private static int ticksLeft = 4 * 60 * SECOND;
    private static int warning;
    private static float windYaw = 45;
    private static int hailLeft;
    /** A fog bank rolling in over ordinary weather, 0 when there is none. */
    private static float bank;
    private static long ticks;
    private static boolean running;

    public static void register() {
        Weather.register();
        WeatherParticles.register();
        ServerTickEvents.END_SERVER_TICK.register(WeatherDirector::tick);
    }

    /** The weather in the overworld right now, or null when the mod isn't running it. */
    public static Weather current() {
        return running ? current : null;
    }

    /** The weather a level sees: the synced copy on clients, the director's on the server. */
    public static Weather of(Level level) {
        if (level.isClientSide()) {
            return WeatherView.client();
        }
        return level.dimension() == Level.OVERWORLD ? current() : null;
    }

    /** Scales how far zombies hear: rain, wind and thunder cover noise. */
    public static double hearing(Level level) {
        Weather w = of(level);
        if (w == null || !ZcConfig.get().weather.affectsZombies) {
            return 1;
        }
        return Mth.clamp(1.15 - 0.55 * w.rain() - 0.35 * w.wind() - 0.2 * w.thunder(), 0.35, 1);
    }

    /** Scales how far zombies see: fog most of all, heavy rain a little. */
    public static double sight(Level level) {
        Weather w = of(level);
        if (w == null || !ZcConfig.get().weather.affectsZombies) {
            return 1;
        }
        return Mth.clamp(1 - 0.65 * w.fog() - 0.3 * Math.max(0, w.rain() - 0.4) - 0.1 * w.thunder(), 0.3, 1);
    }

    /** Starts a kind of weather now, skipping the build-up (for /zc weather). */
    public static void force(WeatherKind next) {
        start(next);
        bank = 0;  // what you asked for, without a fog bank rolling in over it
        warning = 0;
        current = target(null);
    }

    public static WeatherKind kind() {
        return kind;
    }

    public static int secondsLeft() {
        return (ticksLeft + warning) / SECOND;
    }

    private static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ZcConfig.Weather config = ZcConfig.get().weather;
        if (!config.enabled) {
            if (running) {
                running = false;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    player.removeAttached(Weather.TYPE);
                }
            }
            return;
        }
        running = true;
        ticks++;
        if (warning > 0) {
            if (--warning == 0) {
                start(queued);
            }
        } else if (--ticksLeft <= 0) {
            WeatherKind next = pick(level);
            if (next.isDangerous()) {
                queued = next;
                warning = WARNING_TICKS;
            } else {
                start(next);
            }
        }
        // the wind wanders, and swings round harder in storms
        windYaw = Mth.wrapDegrees(windYaw + (float) RNG.nextGaussian() * (kind.isStorm() ? 0.6f : 0.15f));
        if (hailLeft > 0) {
            hailLeft--;
        } else if (kind == WeatherKind.SEVERE && warning == 0 && config.hail && RNG.nextInt(30 * SECOND) == 0) {
            hailLeft = (30 + RNG.nextInt(50)) * SECOND;
        }
        current = current.toward(target(level), kind.isDangerous() ? EASE * 1.5f : EASE);
        holdVanillaRain(level);

        if (ticks % 5 == 0 && current.wind() > 0.85f && config.windDamage) {
            gusts(level);
        }
        if (ticks % SECOND == 0) {
            sync(server, level);
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator()) {
                    effects(level, player, config);
                }
            }
        }
    }

    private static void start(WeatherKind next) {
        kind = next;
        queued = null;
        ticksLeft = (next.minMinutes * 60 + RNG.nextInt((next.maxMinutes - next.minMinutes) * 60 + 1)) * SECOND;
        hailLeft = 0;
        // now and then thick fog rolls in over ordinary rain, day or night
        ZcConfig.Weather config = ZcConfig.get().weather;
        boolean plain = !next.isStorm() && next != WeatherKind.FOG;
        bank = config.fog && plain && RNG.nextDouble() < 0.15 * config.eventFrequency ? 0.55f + 0.4f * RNG.nextFloat() : 0;
    }

    private static WeatherKind pick(Level level) {
        ZcConfig.Weather config = ZcConfig.get().weather;
        double night = WeatherView.nightness(level);
        double[] weights = new double[WeatherKind.values().length];
        double total = 0;
        for (WeatherKind k : WeatherKind.values()) {
            double w = k.weight;
            if (k != WeatherKind.RAIN) {
                w *= config.eventFrequency;
            }
            if (k.isStorm()) {
                w *= config.stormFrequency * (k.isDangerous() ? config.stormFrequency : 1);
            }
            if (k == WeatherKind.FOG) {
                w = config.fog ? w * (0.6 + 2.4 * night) : 0;  // fog gathers at night and lingers into the morning
            }
            if (k == kind && k != WeatherKind.RAIN) {
                w *= 0.3;  // rarely the same thing twice running
            }
            weights[k.ordinal()] = w;
            total += w;
        }
        double r = RNG.nextDouble() * total;
        for (WeatherKind k : WeatherKind.values()) {
            r -= weights[k.ordinal()];
            if (r <= 0) {
                return k;
            }
        }
        return WeatherKind.RAIN;
    }

    /** Where the weather is heading: the current kind, or the build-up to a storm. */
    private static Weather target(Level level) {
        ZcConfig.Weather config = ZcConfig.get().weather;
        WeatherKind k = kind;
        // the rain never eases below moderate, whatever else the weather is doing
        float rain = Math.max(k.rain, (float) config.minRain), thunder = k.thunder, wind = k.wind;
        float fog = config.fog ? Math.max(k.fog, Math.max(bank, rolling(config))) : 0;
        if (warning > 0 && queued != null) {
            // the calm before: wind rising, darker, rain thickening
            float t = 1 - warning / (float) WARNING_TICKS;
            wind = Math.max(wind, 0.35f + 0.4f * t);
            thunder = Math.max(thunder, 0.35f * t);
            rain = Math.max(rain, 0.5f * t);
        }
        float moon = 0;
        if (level != null && config.moonBreaks && !k.isStorm() && k != WeatherKind.FOG && k != WeatherKind.DOWNPOUR) {
            // clouds drift apart now and then: two slow waves, open when both line up
            double t = ticks / 2400.0;
            double gap = Math.sin(t * 1.7) * 0.6 + Math.sin(t * 0.63 + 1.3) * 0.4;
            moon = (float) (Mth.clamp((gap - 0.35) * 3, 0, 1) * WeatherView.nightness(level));
        }
        float hail = hailLeft > 0 ? 1 : 0;
        return new Weather(k.ordinal(), rain, thunder, fog, wind, windYaw, moon, hail);
    }

    /**
     * Fog that comes and goes on its own over any weather: three slow waves of different lengths
     * (roughly 7, 17 and 41 minutes), so some hours stay clear, some get a thin haze, and now and
     * then they line up into a thick bank that rolls in and lifts again.
     */
    private static float rolling(ZcConfig.Weather config) {
        if (config.rollingFog <= 0) {
            return 0;
        }
        double t = ticks / 20.0 / 60.0;  // minutes
        double wave = 0.45 * Math.sin(t * Mth.TWO_PI / 7 + 0.7) + 0.35 * Math.sin(t * Mth.TWO_PI / 17 + 2.1)
                + 0.2 * Math.sin(t * Mth.TWO_PI / 41 + 4.4);
        return (float) (Mth.clamp((wave + 0.15) * 0.95, 0, 0.9) * config.rollingFog);
    }

    /** Keeps vanilla raining (wet streets, fires put out) and thundering in storms. */
    private static void holdVanillaRain(ServerLevel level) {
        WeatherData data = level.getWeatherData();
        boolean thunder = current.thunder() > 0.5f;
        if (!data.isRaining() || data.isThundering() != thunder || data.getRainTime() < 1000) {
            data.setRaining(true);
            data.setRainTime(12000);
            data.setThundering(thunder);
            data.setThunderTime(12000);
            data.setClearWeatherTime(0);
            data.setDirty();
        }
    }

    private static void sync(MinecraftServer server, ServerLevel level) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != level) {
                player.removeAttached(Weather.TYPE);
                continue;
            }
            Weather sent = player.getAttached(Weather.TYPE);
            if (sent == null || !sent.near(current)) {
                player.setAttached(Weather.TYPE, current);
            }
        }
    }

    // ------------------------------------------------------------------ what the weather does

    private static void effects(ServerLevel level, ServerPlayer player, ZcConfig.Weather config) {
        Vec3 pos = player.position();
        boolean outside = level.canSeeSky(BlockPos.containing(player.getEyePosition()));
        // distant lightning and the thunder rolling after it, before a storm and through it, are
        // each client's own (client/weather/WeatherFx), so the flash and its clap go together
        if (warning == 0 && kind.isDangerous() && config.lightningStrikes
                && RNG.nextInt(kind == WeatherKind.SEVERE ? 10 : 22) == 0) {
            strike(level, pos);
        }
        if (current.hail() > 0.3f) {
            hail(level, player, outside, config);
        }
        if (current.wind() > 0.55f) {
            // the wind itself is the client's ambience bed (client/sound/Ambience), which rises with current.wind()
            // something loose bangs in the wind around the block, and the dead come to look
            if (RNG.nextInt(25) == 0) {
                BlockPos bang = player.blockPosition().offset(RNG.nextInt(41) - 20, RNG.nextInt(9) - 2, RNG.nextInt(41) - 20);
                level.playSound(null, bang, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.2f, 0.7f + RNG.nextFloat() * 0.3f);
                Noise.emit(level, Vec3.atCenterOf(bang), 16);
            }
        }
        if (current.wind() > 0.9f && config.windDamage && RNG.nextInt(3) == 0) {
            blowInWindow(level, player.blockPosition());
        }
    }

    /** Lightning somewhere in the streets around a player, never closer than 16 blocks. */
    private static void strike(ServerLevel level, Vec3 near) {
        double a = RNG.nextDouble() * Math.PI * 2;
        double d = 16 + RNG.nextDouble() * 56;
        int x = Mth.floor(near.x + Math.cos(a) * d), z = Mth.floor(near.z + Math.sin(a) * d);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        EntityTypes.LIGHTNING_BOLT.spawn(level, new BlockPos(x, y, z), EntitySpawnReason.EVENT);
    }

    private static void hail(ServerLevel level, ServerPlayer player, boolean outside, ZcConfig.Weather config) {
        Vec3 pos = player.position();
        level.sendParticles(player, ParticleTypes.ITEM_SNOWBALL, true, false, pos.x, pos.y + 9, pos.z,
                (int) (60 * current.hail()), 9, 1.5, 9, 0.1);
        if (RNG.nextInt(2) == 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.GLASS_HIT, SoundSource.WEATHER, 0.6f, 1.6f + RNG.nextFloat() * 0.4f);
        }
        if (config.hail && outside && !player.isCreative() && ticks % (2 * SECOND) == 0
                && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.HAIL), 1f);
        }
    }

    /** Cyclone winds shove everything out in the open downwind. */
    private static void gusts(ServerLevel level) {
        float strength = 0.05f * current.wind() * (0.6f + RNG.nextFloat() * 0.8f);
        double rad = Math.toRadians(current.windYaw());
        double dx = -Math.sin(rad) * strength, dz = Math.cos(rad) * strength;
        for (ServerPlayer player : level.players()) {
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(player.blockPosition()).inflate(40))) {
                if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) {
                    continue;
                }
                if (!level.canSeeSky(BlockPos.containing(entity.getEyePosition()))) {
                    continue;
                }
                double push = entity.onGround() ? 1 : 0.4;
                entity.push(dx * push, 0, dz * push);
                if (entity instanceof ServerPlayer p2) {
                    p2.connection.send(new ClientboundSetEntityMotionPacket(p2));
                }
            }
        }
    }

    /** A city window near the player gives way: glass rains down and the noise carries. */
    private static void blowInWindow(ServerLevel level, BlockPos around) {
        for (int i = 0; i < 24; i++) {
            BlockPos pos = around.offset(RNG.nextInt(49) - 24, RNG.nextInt(21) - 6, RNG.nextInt(49) - 24);
            BlockState state = level.getBlockState(pos);
            String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (!(name.equals("window") || name.equals("window_lit")) || !state.hasProperty(CityBlock.Axis.AXIS)) {
                continue;
            }
            BlockState broken = CityBlocks.resolve("window_broken[axis=" + state.getValue(CityBlock.Axis.AXIS).getSerializedName() + "]");
            if (broken == null) {
                return;
            }
            level.setBlockAndUpdate(pos, broken);
            level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.2f, 0.9f);
            Noise.emit(level, Vec3.atCenterOf(pos), 14);
            return;
        }
    }

    private WeatherDirector() {
    }
}
