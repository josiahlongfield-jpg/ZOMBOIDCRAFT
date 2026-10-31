package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Per-player survival state. Saved with the player and synced to that player's
 * client so the HUD can show moodles.
 *
 * @param thirst    100 = fully hydrated, 0 = dying of thirst
 * @param fatigue   0 = rested, 100 = collapsing
 * @param wounds    number of open, bleeding wounds
 * @param infection seconds since infection, or -1 when not infected
 */
public record SurvivalStats(float thirst, float fatigue, int wounds, int infection) {
    /** Seconds from infection to death: 40 minutes (two Minecraft days) by default. */
    /**
     * Seconds from bite to death, by Zomboid's Infection Mortality (zombieLore.mortality), in game time: a game day is
     * 20 minutes, so 2-3 days is about 50 minutes (the middle of the range, the same on server and HUD).
     */
    public static int infectionDuration() {
        return switch (ZcConfig.get().zombieLore.mortality) {
            case INSTANT -> 1;
            case SECONDS_0_30, MINUTES_0_1 -> 2;
            case HOURS_0_12 -> 6 * GAME_HOUR;
            case DAYS_2_3 -> 60 * GAME_HOUR;
            case WEEKS_1_2 -> 252 * GAME_HOUR;
            case NEVER -> Integer.MAX_VALUE / 4;
        };
    }

    /** One game hour in real seconds (a 20-minute day). */
    public static final int GAME_HOUR = 50;

    public static final SurvivalStats FRESH = new SurvivalStats(100f, 0f, 0, -1);

    public static final Codec<SurvivalStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("thirst").forGetter(SurvivalStats::thirst),
            Codec.FLOAT.fieldOf("fatigue").forGetter(SurvivalStats::fatigue),
            Codec.INT.fieldOf("wounds").forGetter(SurvivalStats::wounds),
            Codec.INT.fieldOf("infection").forGetter(SurvivalStats::infection)
    ).apply(i, SurvivalStats::new));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SurvivalStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, SurvivalStats::thirst,
            ByteBufCodecs.FLOAT, SurvivalStats::fatigue,
            ByteBufCodecs.VAR_INT, SurvivalStats::wounds,
            ByteBufCodecs.VAR_INT, SurvivalStats::infection,
            SurvivalStats::new);

    public static final AttachmentType<SurvivalStats> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("survival"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(() -> FRESH)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    public static SurvivalStats get(Player player) {
        return player.getAttachedOrCreate(TYPE);
    }

    public static void set(Player player, SurvivalStats stats) {
        if (!stats.equals(player.getAttached(TYPE))) {
            player.setAttached(TYPE, stats);
        }
    }

    public boolean infected() {
        return infection >= 0;
    }

    /** 0..1 progress of the infection, or 0 when healthy. */
    public float infectionProgress() {
        return infected() ? Mth.clamp(infection / (float) infectionDuration(), 0f, 1f) : 0f;
    }

    public SurvivalStats withThirst(float value) {
        return new SurvivalStats(Mth.clamp(value, 0f, 100f), fatigue, wounds, infection);
    }

    public SurvivalStats withFatigue(float value) {
        return new SurvivalStats(thirst, Mth.clamp(value, 0f, 100f), wounds, infection);
    }

    public SurvivalStats withWounds(int value) {
        return new SurvivalStats(thirst, fatigue, Math.max(0, value), infection);
    }

    public SurvivalStats withInfection(int value) {
        return new SurvivalStats(thirst, fatigue, wounds, value);
    }
}
