package com.zomboidcraft.survival;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import com.zomboidcraft.zombie.ZombieTypes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;

/**
 * Zomboid's three moods, 0-100 each. Boredom builds while nothing happens (worst sat indoors), and high boredom
 * turns into unhappiness. Unhappiness also comes from pain, being soaked, being infected and bad food; it slows
 * learning and, deep down, saps your strength. Stress comes from the dead close by, wounds and the bite; it shakes
 * your aim and keeps you awake. Reading, games, music, photos, cigarettes, drink and antidepressants push back
 * (LeisureItem, LootFood, MedSpecs); base decor will too. Panic is separate (the survival thread's).
 * <p>
 * Kept in tenths so slow drifts add up; synced to the player's own client for the moodle icons.
 * Other systems move it with {@link #add} and read it with {@link #get}.
 */
public final class Mood {
    public enum Stat {
        BOREDOM, UNHAPPINESS, STRESS;

        final String key = name().toLowerCase(Locale.ROOT);
    }

    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, Integer>> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final AttachmentType<Map<String, Integer>> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("mood"),
            builder -> builder.persistent(CODEC).initializer(Map::of)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // loading the class registers the attachment
    }

    /** 0-100. */
    public static int get(Player player, Stat stat) {
        return player.getAttachedOrElse(TYPE, Map.of()).getOrDefault(stat.key, 0) / 10;
    }

    /** Moves a mood by whole points (negative to ease it). */
    public static void add(ServerPlayer player, Stat stat, int points) {
        Map<String, Integer> m = new HashMap<>(player.getAttachedOrElse(TYPE, Map.of()));
        change(m, stat, points * 10);
        save(player, m);
    }

    private static void change(Map<String, Integer> m, Stat stat, int tenths) {
        m.put(stat.key, Math.max(0, Math.min(1000, m.getOrDefault(stat.key, 0) + tenths)));
    }

    private static void save(Player player, Map<String, Integer> m) {
        m.values().removeIf(v -> v == 0);
        if (!m.equals(player.getAttachedOrElse(TYPE, Map.of()))) player.setAttached(TYPE, Map.copyOf(m));
    }

    /** Once a second (SurvivalEvents). */
    static void tick(ServerPlayer player, int second) {
        ServerLevel level = (ServerLevel) player.level();
        Map<String, Integer> m = new HashMap<>(player.getAttachedOrElse(TYPE, Map.of()));
        int near = level.getEntitiesOfClass(Zombie.class, player.getBoundingBox().inflate(12), ZombieTypes::isWalker).size();
        boolean busy = near > 0 || player.hurtTime > 0 || TimedActions.busy(player) || player.isPassenger()
                || player.getDeltaMovement().horizontalDistanceSqr() > 0.004;
        boolean indoors = !level.canSeeSky(player.blockPosition().above());
        int pain = Medicine.pain(player);
        boolean infected = SurvivalStats.get(player).infected();
        int boredom = m.getOrDefault(Stat.BOREDOM.key, 0);

        // boredom: idle builds it, worst inside; trouble and moving about wear it off
        if (player.isSleeping()) {
            // nothing
        } else if (near > 0) {
            change(m, Stat.BOREDOM, -6);
        } else if (!busy) {
            change(m, Stat.BOREDOM, indoors ? 3 : 2);
        } else {
            change(m, Stat.BOREDOM, -1);
        }

        // unhappiness
        int sad = 0;
        if (boredom > 500) sad += boredom > 800 ? 3 : 1;
        if (pain >= 40) sad += 1;
        if (infected) sad += 2;
        if (level.isRainingAt(player.blockPosition().above())) sad += 1;
        if (Ailments.get(player, "antidep") > 0) sad -= 4;
        if (sad == 0 && second % 3 == 0) sad = -1;
        change(m, Stat.UNHAPPINESS, sad);

        // stress
        int stress = Math.min(8, near * 2);
        if (pain >= 50) stress += 1;
        if (infected) stress += m.getOrDefault(Stat.STRESS.key, 0) < 500 ? 3 : 0;
        if (stress == 0) stress = -2;
        change(m, Stat.STRESS, stress);

        save(player, m);
        int unhappy = get(player, Stat.UNHAPPINESS), stressed = get(player, Stat.STRESS);
        if (unhappy >= 80) Medicine.effect(player, MobEffects.WEAKNESS, 0);
        if (stressed >= 75 && player.isSleeping() && Ailments.get(player, "sleepy") == 0) {
            player.stopSleeping();
            player.sendOverlayMessage(Component.literal("You're too on edge to sleep.").withStyle(ChatFormatting.GRAY));
        }
        if (second % 300 == 0) {
            if (boredom > 700) player.sendOverlayMessage(Component.literal("You're bored out of your mind.").withStyle(ChatFormatting.GRAY));
            else if (unhappy > 70) player.sendOverlayMessage(Component.literal("Everything feels hopeless.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** Stress shakes your aim: added to Injuries.armShake. */
    public static float shake(Player player) {
        int stress = get(player, Stat.STRESS);
        return stress >= 75 ? 0.25f : stress >= 50 ? 0.1f : 0f;
    }

    /** Unhappy survivors learn slower: Skills.addXp multiplies by this. */
    public static double learning(Player player) {
        int unhappy = get(player, Stat.UNHAPPINESS);
        return unhappy >= 80 ? 0.5 : unhappy >= 60 ? 0.75 : 1.0;
    }

    private Mood() {
    }
}
