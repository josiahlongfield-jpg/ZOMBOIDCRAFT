package com.zomboidcraft.skills;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZcConfig;
import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Skill XP per player, keyed by skill name. Like Zomboid, it's lost on death.
 */
public final class Skills {
    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, Integer>> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final AttachmentType<Map<String, Integer>> TYPE = AttachmentRegistry.create(
            ZomboidCraft.id("skills"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(Map::of)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    public static int xp(Player player, Skill skill) {
        return player.getAttachedOrCreate(TYPE).getOrDefault(skill.name(), skill.passive() ? Skill.xpForLevel(5) : 0);
    }

    public static int level(Player player, Skill skill) {
        return Skill.levelForXp(xp(player, skill));
    }

    public static void addXp(ServerPlayer player, Skill skill, int amount) {
        // companions working beside you pick some of it up (before your books: hers are her own)
        com.zomboidcraft.npc.NpcSkills.watched(player, skill, amount);
        double multiplier = ZcConfig.get().progression.xpMultiplier;
        amount = multiplier <= 0 ? 0 : Math.max(1, (int) Math.round(amount * multiplier * SkillBooks.boost(player, skill)
                * com.zomboidcraft.survival.Mood.learning(player)));
        if (amount <= 0 || player.isCreative()) {
            return;
        }
        int before = xp(player, skill);
        int after = before + amount;
        Map<String, Integer> updated = new HashMap<>(player.getAttachedOrCreate(TYPE));
        updated.put(skill.name(), after);
        player.setAttached(TYPE, updated);
        // the client's skill tape shows a level going up
    }

    /** Takes XP away (a passive skill wasting from starving or idleness); never below level 0. */
    public static void loseXp(ServerPlayer player, Skill skill, int amount) {
        if (amount <= 0 || player.isCreative()) {
            return;
        }
        Map<String, Integer> updated = new HashMap<>(player.getAttachedOrCreate(TYPE));
        updated.put(skill.name(), Math.max(0, xp(player, skill) - amount));
        player.setAttached(TYPE, updated);
    }

    private Skills() {
    }
}
