package com.zomboidcraft.skills;

import com.mojang.serialization.Codec;
import com.zomboidcraft.ZomboidCraft;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Skill books, Zomboid style. Each book holds five volumes, one for each band of two levels (0-2, 3-4, 5-6, 7-8,
 * 9-10). Reading the volume for the band you're in multiplies the XP you earn in that skill while you're in it: x3,
 * x5, x8, x12, x16. What you've read is lost on death, like the skills themselves.
 */
public final class SkillBooks {
    private static final int[] MULTIPLIER = {3, 5, 8, 12, 16};
    private static final Codec<Map<String, Integer>> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);
    private static final StreamCodec<io.netty.buffer.ByteBuf, Map<String, Integer>> STREAM_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    /** Volumes read, per skill name, as a bit for each volume. */
    public static final AttachmentType<Map<String, Integer>> READ = AttachmentRegistry.create(
            ZomboidCraft.id("books_read"),
            builder -> builder
                    .persistent(CODEC)
                    .initializer(Map::of)
                    .syncWith(STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

    public static void register() {
        // Loading the class registers the attachment.
    }

    /** The volume (1-5) that covers a level. */
    public static int volume(int level) {
        return level <= 2 ? 1 : Math.min(5, (level + 1) / 2);
    }

    public static int multiplier(int volume) {
        return MULTIPLIER[volume - 1];
    }

    /** The last level the volume covers. */
    public static int lastLevel(int volume) {
        return volume == 1 ? 2 : volume * 2;
    }

    public static boolean read(Player player, Skill skill, int volume) {
        return (player.getAttachedOrCreate(READ).getOrDefault(skill.name(), 0) & (1 << volume)) != 0;
    }

    public static void markRead(Player player, Skill skill, int volume) {
        Map<String, Integer> updated = new HashMap<>(player.getAttachedOrCreate(READ));
        updated.merge(skill.name(), 1 << volume, (a, b) -> a | b);
        player.setAttached(READ, updated);
    }

    /** How much the books you've read multiply XP in this skill right now (1 when you haven't read this volume). */
    public static int boost(Player player, Skill skill) {
        int level = Skills.level(player, skill);
        if (level >= Skill.MAX_LEVEL) {
            return 1;
        }
        int volume = volume(level);
        return read(player, skill, volume) ? multiplier(volume) : 1;
    }

    private SkillBooks() {
    }
}
